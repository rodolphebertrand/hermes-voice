package com.hermesandroid.relay.wake

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

/**
 * Registre des wake words ONNX (assets remplaçables, règle 14 AGENTS.md).
 *
 * Sources, par ordre de priorité :
 * 1. `filesDir/wakewords/` — dossier de l'app, éditable sans recompiler
 *    (ajout/suppression de .onnx + édition de wakewords.json).
 * 2. `assets/wakewords/` — embarqués dans le build, copiés vers (1) au premier
 *    démarrage si le manifeste n'existe pas encore.
 *
 * Le toggle par wake word est stocké dans le DataStore partagé (clé
 * `wake_word_asset_enabled_<id>`), indépendamment du champ `enabled` du JSON
 * (valeur par défaut).
 */
object WakeWordAssetRegistry {
    const val ASSET_DIR = "wakewords"
    const val MANIFEST_NAME = "wakewords.json"
    const val DEFAULT_PROFILE = "default"

    private val assetJson = Json {
        ignoreUnknownKeys = true
        namingStrategy = JsonNamingStrategy.SnakeCase
        coerceInputValues = true
    }

    /** Parsing pur (testable sans Android) : jette si la table est incohérente. */
    fun parse(jsonText: String): WakeWordAssetConfig {
        val config = assetJson.decodeFromString(WakeWordAssetConfig.serializer(), jsonText)
        config.validate()
        return config
    }

    fun dir(context: Context): File = File(context.applicationContext.filesDir, ASSET_DIR)

    fun manifest(context: Context): File = File(dir(context), MANIFEST_NAME)

    fun modelFile(context: Context, entry: WakeWordAssetEntry): File =
        File(dir(context), entry.model)

    fun entryKey(id: String): Preferences.Key<Boolean> =
        booleanPreferencesKey("wake_word_asset_enabled_$id")

    /**
     * Charge la configuration depuis `filesDir/wakewords/`.
     * Jette si le manifeste ou les modèles de pipeline manquent.
     */
    fun load(context: Context): WakeWordAssetConfig {
        val appContext = context.applicationContext
        val manifest = manifest(appContext)
        require(manifest.isFile) {
            "Manifeste wake words manquant ($MANIFEST_NAME) — lancer ensureWakeWordReady() d'abord"
        }
        val config = parse(manifest.readText())
        val dir = dir(appContext)
        require(File(dir, config.pipeline.melspectrogramModel).isFile) {
            "Modèle de pipeline manquant : ${config.pipeline.melspectrogramModel}"
        }
        require(File(dir, config.pipeline.embeddingModel).isFile) {
            "Modèle de pipeline manquant : ${config.pipeline.embeddingModel}"
        }
        return config
    }

    /** true si au moins une wake word est active (toggle ON + fichier .onnx présent). */
    suspend fun hasActiveEntry(context: Context): Boolean {
        val appContext = context.applicationContext
        val manifest = manifest(appContext)
        if (!manifest.isFile) return false
        val config = runCatching { parse(manifest.readText()) }.getOrNull() ?: return false
        return config.wakeWords.any { entry ->
            isEntryEnabled(appContext, entry.id, entry.enabled) && modelFile(appContext, entry).isFile
        }
    }

    suspend fun isEntryEnabled(context: Context, id: String, jsonDefault: Boolean): Boolean {
        val stored = runCatching {
            WakeWordPreferencesRepository(context.applicationContext).entryEnabled(id).first()
        }.getOrNull()
        return stored ?: jsonDefault
    }

    suspend fun setEntryEnabled(context: Context, id: String, enabled: Boolean) {
        WakeWordPreferencesRepository(context.applicationContext).setEntryEnabled(id, enabled)
    }

    /**
     * Liste des wake words avec leur état effectif (pour l'écran de réglage).
     * Vide si aucun manifeste.
     */
    suspend fun entries(context: Context): List<WakeWordEntryUi> {
        val appContext = context.applicationContext
        val manifest = manifest(appContext)
        if (!manifest.isFile) return emptyList()
        val config = runCatching { parse(manifest.readText()) }.getOrNull() ?: return emptyList()
        return config.wakeWords.map { entry ->
            WakeWordEntryUi(
                id = entry.id,
                phrase = entry.phrase,
                profile = entry.targetProfile.ifBlank { DEFAULT_PROFILE },
                modelFile = modelFile(appContext, entry).name,
                enabled = isEntryEnabled(appContext, entry.id, entry.enabled),
            )
        }
    }

    enum class WakeWordEngineKind { Assets, Sherpa }

    /**
     * Rend un modèle de wake word disponible sur l'appareil, par ordre :
     * 1. entrée active déjà dans `filesDir/wakewords/` ;
     * 2. assets embarqués (copie vers filesDir au premier démarrage) ;
     * 3. fallback sherpa (téléchargement) si le build n'embarque pas d'assets.
     *
     * Jette [IllegalStateException] si aucune source ne fournit de wake word.
     */
    suspend fun ensureWakeWordReady(context: Context): WakeWordEngineKind {
        val appContext = context.applicationContext
        val manifest = manifest(appContext)
        if (manifest.isFile) {
            check(hasActiveEntry(appContext)) {
                "Aucune wake word active (toutes désactivées ou fichiers .onnx manquants)"
            }
            return WakeWordEngineKind.Assets
        }
        val assetNames = runCatching {
            appContext.assets.list(ASSET_DIR) ?: emptyArray()
        }.getOrDefault(emptyArray())
        if (assetNames.any { it == MANIFEST_NAME }) {
            copyAssets(appContext)
            if (manifest.isFile && hasActiveEntry(appContext)) {
                return WakeWordEngineKind.Assets
            }
        }
        // Build sans assets wake words → fallback sherpa (téléchargement).
        WakeWordModelInstaller(appContext).ensureInstalled()
        return WakeWordEngineKind.Sherpa
    }

    /**
     * true si un trigger wake word peut être créé sans téléchargement
     * (manifeste présent dans filesDir OU modèles sherpa installés).
     */
    fun isReady(context: Context): Boolean {
        val appContext = context.applicationContext
        return manifest(appContext).isFile ||
            WakeWordModelInstaller(appContext).installedFiles() != null
    }

    private fun copyAssets(context: Context) {
        val appContext = context.applicationContext
        val targetDir = dir(appContext)
        if (!targetDir.isDirectory && !targetDir.mkdir()) {
            throw IllegalStateException("Impossible de créer le dossier $ASSET_DIR")
        }
        for (name in appContext.assets.list(ASSET_DIR).orEmpty()) {
            val target = File(targetDir, name)
            appContext.assets.open("$ASSET_DIR/$name").use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }
}

/** État d'une wake word pour l'écran de réglage (UI neutre). */
data class WakeWordEntryUi(
    val id: String,
    val phrase: String,
    val profile: String,
    val modelFile: String,
    val enabled: Boolean,
)
