package com.hermesandroid.relay.wake

import android.content.Context
import android.util.Log
import com.microsoft.onnxruntime.OnnxTensor
import com.microsoft.onnxruntime.OrtEnvironment
import com.microsoft.onnxruntime.OrtSession
import java.io.File

/** Résultat d'une détection de wake word (quelle que soit l'implémentation). */
data class WakeDetection(
    /** Id de l'entrée assets (ex. "he_pixel"), ou null pour le fallback sherpa. */
    val entryId: String?,
    val phrase: String,
    /** Score brut (0..1) au moment du déclenchement. */
    val score: Float,
    /** Profil Hermes cible ("pi", "alfred"), ou "default"/null = profil actif. */
    val targetProfile: String?,
)

/**
 * Déclencheur de wake word à implémentations interchangeables
 * (architecture AGENTS.md : micro continu, assistant, tuile, raccourci…).
 *
 * Contrat : `accept()` reçoit des trames PCM16 16 kHz mono et renvoie au plus
 * un [WakeDetection] par appel. `close()` libère les ressources natives
 * idempotemment.
 */
interface WakeTrigger : AutoCloseable {
    val id: String

    fun accept(samples: ShortArray, count: Int): WakeDetection?

    override fun close() {}
}

/** Scoring d'un modèle de wake word ONNX (fenêtre (16, 96) → score 0..1). */
interface WakeWordModelScorer : AutoCloseable {
    fun score(wakeWindow: FloatArray): Float

    override fun close()
}

class OnnxWakeWordModelScorer(
    private val environment: OrtEnvironment,
    private val session: OrtSession,
    private val wakeWindowFrames: Int,
    private val featureDim: Int,
) : WakeWordModelScorer {
    private val inputName: String = session.inputNames.first()

    override fun score(wakeWindow: FloatArray): Float {
        val tensor = OnnxTensor.createTensor(
            environment,
            wakeWindow,
            longArrayOf(1L, wakeWindowFrames.toLong(), featureDim.toLong()),
        )
        try {
            val output = session.run(mapOf<String, Any>(inputName to tensor)).first() as OnnxTensor
            val total = output.shape.fold(1L) { a, b -> a * b }.toInt()
            require(total > 0) { "Sortie wake word vide" }
            val flat = FloatArray(total)
            output.asFloatBuffer().let { buffer -> buffer.rewind(); buffer.get(flat) }
            return flat[flat.size - 1]
        } finally {
            tensor.close()
        }
    }

    override fun close() {
        session.close()
    }
}

/** Une wake word active : entrée de la table + scorer ONNX. */
data class AssetWakeWord(
    val entry: WakeWordAssetEntry,
    val scorer: WakeWordModelScorer,
) {
    val threshold: Float = entry.threshold.coerceIn(0.05f, 0.95f)
}

/**
 * Déclencheur primaire : modèles openWakeWord ONNX chargés dynamiquement
 * depuis `assets/wakewords/` (règle 14 AGENTS.md — aucun modèle en dur).
 *
 * La pipeline (melspectrogram + embedding) est partagée entre les wake words ;
 * chaque entrée possède son propre scorer. Une détection requiert
 * [confirmationFrames] trames consécutives au-dessus du seuil.
 */
class OpenWakeWordAssetTrigger(
    val entries: List<AssetWakeWord>,
    private val window: WakeWordFeatureWindow,
    private val wakeWindowFrames: Int,
    confirmationFrames: Int,
) : WakeTrigger {
    override val id: String = "openwakeword-assets"
    private val neededFrames: Int = confirmationFrames.coerceIn(1, 5)
    private val confirmations = IntArray(entries.size)
    private var closed = false

    override fun accept(samples: ShortArray, count: Int): WakeDetection? {
        if (closed || count <= 0) return null
        window.step(samples, count)
        if (!window.hasWindow(wakeWindowFrames)) return null
        val wakeWindow = window.window(wakeWindowFrames)
        for (index in entries.indices) {
            val entry = entries[index]
            val score = entry.scorer.score(wakeWindow)
            if (score >= entry.threshold) {
                if (confirmations[index] >= neededFrames - 1) {
                    confirmations[index] = 0
                    return WakeDetection(
                        entryId = entry.entry.id,
                        phrase = entry.entry.phrase,
                        score = score,
                        targetProfile = entry.entry.targetProfile,
                    )
                }
                confirmations[index] += 1
            } else {
                confirmations[index] = 0
            }
        }
        return null
    }

    override fun close() {
        if (closed) return
        closed = true
        entries.forEach { runCatching { it.scorer.close() } }
        runCatching { window.close() }
    }

    companion object {
        suspend fun create(context: Context, preferences: WakeWordPreferences): OpenWakeWordAssetTrigger {
            val appContext = context.applicationContext
            val config = WakeWordAssetRegistry.load(appContext)
            val pipeline = config.pipeline
            val dir = WakeWordAssetRegistry.dir(appContext)
            val active = config.wakeWords.filter { entry ->
                runCatching {
                    WakeWordAssetRegistry.isEntryEnabled(appContext, entry.id, entry.enabled)
                }.getOrDefault(entry.enabled) && WakeWordAssetRegistry.modelFile(appContext, entry).isFile
            }
            require(active.isNotEmpty()) { "Aucune wake word active dans wakewords.json" }
            val environment = OrtEnvironment.getEnvironment()
            val melspectrogramSession = OrtSession.create(
                environment,
                File(dir, pipeline.melspectrogramModel).absolutePath,
            )
            val embeddingSession = OrtSession.create(
                environment,
                File(dir, pipeline.embeddingModel).absolutePath,
            )
            val window = OnnxWakeWordFeatureWindow(pipeline, environment, melspectrogramSession, embeddingSession)
            val entries = active.map { entry ->
                AssetWakeWord(
                    entry = entry,
                    scorer = OnnxWakeWordModelScorer(
                        environment = environment,
                        session = OrtSession.create(
                            environment,
                            File(dir, entry.model).absolutePath,
                        ),
                        wakeWindowFrames = pipeline.wakeWindowFrames,
                        featureDim = pipeline.featureDim,
                    ),
                )
            }
            return OpenWakeWordAssetTrigger(
                entries = entries,
                window = window,
                wakeWindowFrames = pipeline.wakeWindowFrames,
                confirmationFrames = preferences.confirmationFrames,
            )
        }
    }
}

/**
 * Fallback : détecteur sherpa-onnx KWS existant (modèle téléchargé).
 * La phrase reconnue est celle configurée (DEFAULT_WAKE_PHRASE) ; le routage
 * de profil reste celui des préférences.
 */
class SherpaWakeWordTrigger(
    private val detector: WakeWordDetector,
) : WakeTrigger {
    override val id: String = "sherpa-kws"
    private var closed = false

    override fun accept(samples: ShortArray, count: Int): WakeDetection? {
        if (closed || count <= 0) return null
        return if (detector.accept(samples, count)) {
            WakeDetection(
                entryId = null,
                phrase = DEFAULT_WAKE_PHRASE,
                score = 1f,
                targetProfile = null,
            )
        } else {
            null
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        detector.close()
    }
}

/**
 * Fabrique de [WakeTrigger] : primaire = assets openWakeWord ONNX ;
 * fallback = sherpa-onnx si aucun asset actif (règle mission Phase 0b).
 */
object WakeTriggerFactory {
    private const val TAG = "WakeTriggerFactory"

    suspend fun create(context: Context, preferences: WakeWordPreferences): WakeTrigger {
        val appContext = context.applicationContext
        if (WakeWordAssetRegistry.hasActiveEntry(appContext)) {
            return try {
                OpenWakeWordAssetTrigger.create(appContext, preferences)
            } catch (t: Throwable) {
                // ONNX indisponible (lib native, fichier corrompu) → fallback sherpa.
                Log.w(TAG, "Trigger openWakeWord assets indisponible ; fallback sherpa", t)
                sherpa(appContext, preferences)
            }
        }
        return sherpa(appContext, preferences)
    }

    private fun sherpa(context: Context, preferences: WakeWordPreferences): WakeTrigger {
        val files = WakeWordModelInstaller(context).installedFiles()
            ?: error(
                "Aucun modèle de wake word disponible (assets absents et fallback sherpa non installé)"
            )
        return SherpaWakeWordTrigger(
            SherpaWakeWordDetector(
                files = files,
                sensitivity = preferences.sensitivity,
                confirmationFrames = preferences.confirmationFrames,
            )
        )
    }
}
