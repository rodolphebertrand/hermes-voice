package com.hermesandroid.relay.wake

import kotlinx.serialization.Serializable

/**
 * Transformation appliquée aux trames mel avant l'embedding (openWakeWord v0.5.1 :
 * `spec = spec / 10 + 2`).
 */
@Serializable
data class MelspecTransform(
    val divide: Float = 10f,
    val add: Float = 2f,
)

/**
 * Géométrie de la pipeline openWakeWord pour la famille de modèles 96-dim
 * (pré-v0.6, input (16, 96)). Provenance : release v0.5.1, Apache-2.0.
 */
@Serializable
data class WakeWordPipeline(
    val melspectrogramModel: String = "melspectrogram.onnx",
    val embeddingModel: String = "embedding_model.onnx",
    val melspecTransform: MelspecTransform = MelspecTransform(),
    val sampleRate: Int = 16_000,
    /** Échantillons entre deux pas de streaming (1280 = 8 trames mel). */
    val stepSamples: Int = 1_280,
    /** Échantillons de contexte (lookbehind) utilisés par le melspectrogramme. */
    val melspecLookbehindSamples: Int = 480,
    val frameHopSamples: Int = 160,
    val melBins: Int = 32,
    /** Trames mel entrant dans une fenêtre d'embedding. */
    val embeddingWindowFrames: Int = 76,
    /** Trames mel produites par pas de 1280 échantillons. */
    val embeddingStepFrames: Int = 8,
    val featureDim: Int = 96,
    /** Embeddings entrant dans l'input (16, 96) des modèles de wake word. */
    val wakeWindowFrames: Int = 16,
)

/**
 * Une wake word routée : phrase → fichier .onnx → profil Hermes cible.
 * La table est 100 % pilotée par wakewords.json (règle 14 AGENTS.md).
 */
@Serializable
data class WakeWordAssetEntry(
    val id: String,
    val phrase: String,
    /** Fichier .onnx relatif au dossier wakewords/. */
    val model: String,
    /** Profil Hermes cible : "pi", "alfred", ou "default" = profil actif. */
    val targetProfile: String = "default",
    /** Seuil de détection (score sigmoidé 0..1). Convention de formation : 0.5. */
    val threshold: Float = 0.5f,
    val enabled: Boolean = true,
)

@Serializable
data class WakeWordAssetConfig(
    val version: Int = 1,
    val pipeline: WakeWordPipeline = WakeWordPipeline(),
    val wakeWords: List<WakeWordAssetEntry> = emptyList(),
) {
    /** Jette une [IllegalArgumentException] si la table est incohérente. */
    fun validate() {
        require(wakeWords.none { it.id.isBlank() }) { "wake word id vide dans wakewords.json" }
        require(wakeWords.none { it.phrase.isBlank() }) { "wake word phrase vide dans wakewords.json" }
        require(wakeWords.none { it.model.isBlank() }) { "wake word model vide dans wakewords.json" }
        val ids = wakeWords.map { it.id }
        require(ids.toSet().size == ids.size) { "ids de wake words dupliqués : $ids" }
        require(pipeline.melBins in 1..512)
        require(pipeline.featureDim in 1..1024)
        require(pipeline.embeddingWindowFrames in 1..512)
        require(pipeline.wakeWindowFrames in 1..128)
        require(pipeline.stepSamples > 0)
        require(pipeline.melspectrogramModel.isNotBlank())
        require(pipeline.embeddingModel.isNotBlank())
    }
}
