package com.hermesandroid.relay.wake

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession

/**
 * Fenêtre sur l'historique d'embeddings produit par la pipeline streaming.
 *
 * L'implémentation ONNX est un port fidèle (pas à pas) de
 * `openwakeword/utils.py::AudioFeatures._streaming_features` (Apache-2.0)
 * pour la famille de modèles 96-dim, avec buffers démarrés vides :
 * le premier embedding apparaît après 76 trames mel (10 pas de 1280 samples).
 */
interface WakeWordFeatureWindow : AutoCloseable {
    /**
     * Pousse des échantillons PCM16 16 kHz mono et retourne le nombre de pas
     * d'embedding produits (0 ou plus).
     */
    fun step(samples: ShortArray, count: Int): Int

    fun hasWindow(frameCount: Int): Boolean

    /** Les [frameCount] derniers embeddings, aplatis (frameCount × featureDim, ligne par ligne). */
    fun window(frameCount: Int): FloatArray

    override fun close()
}

/**
 * Pipeline streaming openWakeWord v0.5.1 (modèles 96-dim) :
 * PCM16 → melspectrogram.onnx (1280+480 samples → 8 trames × 32 mel) →
 * `v / divide + add` → fenêtre de 76 trames → embedding_model.onnx → 96-dim.
 *
 * Thread-confine : à utiliser depuis un seul thread (boucle de lecture micro).
 */
class OnnxWakeWordFeatureWindow(
    private val pipeline: WakeWordPipeline,
    private val environment: OrtEnvironment,
    private val melspectrogramSession: OrtSession,
    private val embeddingSession: OrtSession,
) : WakeWordFeatureWindow {
    private val stepSamples: Int = pipeline.stepSamples
    private val lookbehind: Int = pipeline.melspecLookbehindSamples
    private val melBins: Int = pipeline.melBins
    private val windowFrames: Int = pipeline.embeddingWindowFrames
    private val featureDim: Int = pipeline.featureDim
    private val divide: Float = pipeline.melspecTransform.divide
    private val add: Float = pipeline.melspecTransform.add

    private val rawCapacity = 4_096
    private val raw = ShortArray(rawCapacity)
    private var rawSize = 0
    private var rawOldest = 0
    /** Échantillons PCM16 poussés au total (position absolue du plus récent). */
    private var totalPushed = 0L
    /** Étapes de melspectrogramme déjà calculées (fenêtre i se termine à
     *  lookbehind + stepSamples + i * stepSamples). */
    private var melspecsComputed = 0L

    private val melspecCapacity = 256
    private val melspec = Array(melspecCapacity) { FloatArray(melBins) }
    private var melspecSize = 0
    private var melspecOldest = 0

    private val featureCapacity = 120
    private val features = Array(featureCapacity) { FloatArray(featureDim) }
    private var featureSize = 0
    private var featureOldest = 0

    private val melspecInput = FloatArray(stepSamples + lookbehind)
    private val melspecInputName: String = melspectrogramSession.inputNames.first()
    private val embeddingInputName: String = embeddingSession.inputNames.first()

    override fun step(samples: ShortArray, count: Int): Int {
        require(count in 0..samples.size) { "count=$count hors bornes (size=${samples.size})" }
        for (index in 0 until count) {
            pushRaw(samples[index])
        }
        totalPushed += count
        val windowLen = stepSamples + lookbehind
        var steps = 0
        while (true) {
            // La fenêtre i se termine à la position absolue suivante.
            val nextEnd = lookbehind + stepSamples + melspecsComputed * stepSamples
            if (nextEnd > totalPushed) break
            val back = (totalPushed - nextEnd).toInt()
            val maxBack = rawSize - windowLen
            if (maxBack < 0) break // Pas encore assez d'historique (démarrage).
            if (back > maxBack) {
                // La fenêtre de cette étape est sortie du buffer ring (ex. micro
                // en pause) : on saute les étapes obsolètes au lieu de jeter.
                melspecsComputed += ((back - maxBack) + stepSamples - 1) / stepSamples
                continue
            }
            val frames = runMelspec(rawWindow(back, windowLen))
            for (frame in frames) appendMelspec(frame)
            if (melspecSize >= windowFrames) {
                appendFeature(runEmbedding(lastMelspec(windowFrames)))
            }
            melspecsComputed++
            steps++
        }
        return steps
    }

    override fun hasWindow(frameCount: Int): Boolean = featureSize >= frameCount

    override fun window(frameCount: Int): FloatArray {
        require(frameCount in 1..featureCapacity)
        require(featureSize >= frameCount) {
            "Pas encore assez d'embeddings ($featureSize < $frameCount)"
        }
        val out = FloatArray(frameCount * featureDim)
        for (i in 0 until frameCount) {
            val source = features[(featureOldest + featureSize - frameCount + i) % featureCapacity]
            System.arraycopy(source, 0, out, i * featureDim, featureDim)
        }
        return out
    }

    override fun close() {
        runCatching { melspectrogramSession.close() }
        runCatching { embeddingSession.close() }
    }

    private fun pushRaw(sample: Short) {
        if (rawSize == rawCapacity) {
            rawOldest = (rawOldest + 1) % rawCapacity
            rawSize = rawCapacity - 1
        }
        raw[(rawOldest + rawSize) % rawCapacity] = sample
        rawSize++
    }

    /**
     * Les [n] échantillons se terminant à [back] positions avant le plus récent
     * (back = 0 → les [n] derniers échantillons).
     */
    private fun rawWindow(back: Int, n: Int): ShortArray {
        require(back in 0..rawSize - n) {
            "Fenêtre brute indisponible (back=$back, n=$n, rawSize=$rawSize)"
        }
        val out = ShortArray(n)
        for (i in 0 until n) {
            out[i] = raw[(rawOldest + rawSize - back - n + i) % rawCapacity]
        }
        return out
    }

    private fun appendMelspec(row: FloatArray) {
        if (melspecSize == melspecCapacity) {
            melspecOldest = (melspecOldest + 1) % melspecCapacity
            melspecSize = melspecCapacity - 1
        }
        System.arraycopy(row, 0, melspec[(melspecOldest + melspecSize) % melspecCapacity], 0, melBins)
        melspecSize++
    }

    private fun lastMelspec(n: Int): Array<FloatArray> {
        require(melspecSize >= n) { "Trames mel insuffisantes ($melspecSize < $n)" }
        val out = Array(n) { FloatArray(melBins) }
        for (i in 0 until n) {
            System.arraycopy(
                melspec[(melspecOldest + melspecSize - n + i) % melspecCapacity],
                0,
                out[i],
                0,
                melBins,
            )
        }
        return out
    }

    private fun appendFeature(row: FloatArray) {
        if (featureSize == featureCapacity) {
            featureOldest = (featureOldest + 1) % featureCapacity
            featureSize = featureCapacity - 1
        }
        System.arraycopy(row, 0, features[(featureOldest + featureSize) % featureCapacity], 0, featureDim)
        featureSize++
    }

    private fun runMelspec(input: ShortArray): Array<FloatArray> {
        for (i in input.indices) melspecInput[i] = input[i].toInt().toFloat()
        val tensor = OnnxTensor.createTensor(
            environment,
            java.nio.FloatBuffer.wrap(melspecInput),
            longArrayOf(1L, input.size.toLong()),
        )
        try {
            val result = melspectrogramSession.run(mapOf(melspecInputName to tensor))
            try {
                val output = result.get(0) as OnnxTensor
                val buffer = output.floatBuffer
                val total = buffer.remaining()
                require(total % melBins == 0 && total > 0) {
                    "Sortie melspectrogram inattendue : $total éléments"
                }
                val frames = total / melBins
                val flat = FloatArray(total)
                buffer.rewind(); buffer.get(flat)
                return Array(frames) { f -> FloatArray(melBins) { b -> flat[f * melBins + b] } }
            } finally {
                result.close()
            }
        } finally {
            tensor.close()
        }
    }

    private fun runEmbedding(window: Array<FloatArray>): FloatArray {
        val total = windowFrames * melBins
        val flat = FloatArray(total)
        for (i in window.indices) {
            System.arraycopy(window[i], 0, flat, i * melBins, melBins)
        }
        val tensor = OnnxTensor.createTensor(
            environment,
            java.nio.FloatBuffer.wrap(flat),
            longArrayOf(1L, windowFrames.toLong(), melBins.toLong(), 1L),
        )
        try {
            val result = embeddingSession.run(mapOf(embeddingInputName to tensor))
            try {
                val output = result.get(0) as OnnxTensor
                val buffer = output.floatBuffer
                val size = buffer.remaining()
                require(size >= featureDim) {
                    "Sortie embedding inattendue : $size éléments"
                }
                val all = FloatArray(size)
                buffer.rewind(); buffer.get(all)
                val embedding = FloatArray(featureDim)
                System.arraycopy(all, size - featureDim, embedding, 0, featureDim)
                return embedding
            } finally {
                result.close()
            }
        } finally {
            tensor.close()
        }
    }
}
