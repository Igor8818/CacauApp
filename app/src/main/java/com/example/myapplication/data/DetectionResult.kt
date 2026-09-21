package com.example.myapplication.data

import android.graphics.RectF

/**
 * Representa uma deteccao individual retornada pelo modelo YOLO.
 * Substitui org.tensorflow.lite.task.vision.detector.Detection
 * (removida para evitar conflito de AndroidManifest com AGP 9).
 */
data class DetectionResult(
    /** Caixa delimitadora em pixels relativos ao bitmap original. */
    val boundingBox: RectF,
    /** Lista de categorias detectadas (geralmente apenas 1 para YOLO). */
    val categories: List<DetectionCategory>
) {
    companion object {
        fun create(box: RectF, categories: List<DetectionCategory>): DetectionResult =
            DetectionResult(box, categories)
    }
}

/**
 * Representa um rotulo e sua pontuacao de confianca.
 * Substitui org.tensorflow.lite.support.label.Category.
 */
data class DetectionCategory(
    val label: String,
    val displayName: String,
    val score: Float
) {
    companion object {
        fun create(label: String, displayName: String, score: Float): DetectionCategory =
            DetectionCategory(label, displayName, score)
    }
}