package com.example.myapplication.userinterface.util

/** Disposição do tensor de saída do detector YOLO. */
enum class LayoutSaida {
    /** [1, 4+C, anchors] — YOLOv8 / YOLOv11 exportado sem NMS. */
    TRANSPOSTO,

    /** [1, anchors, 4+C] */
    PADRAO,

    /** [1, N, 6] com (x1, y1, x2, y2, score, classe) — NMS já embutido no modelo. */
    END_TO_END
}

/** Descrição validada de um modelo TFLite de detecção suportado pelo app. */
data class FormatoModelo(
    val inputSize: Int,
    val entradaNchw: Boolean,
    val entradaFloat: Boolean,
    val layout: LayoutSaida,
    val numAnchors: Int,
    val numClasses: Int
) {
    fun descricao(): String {
        val ent = if (entradaNchw) "NCHW" else "NHWC"
        val tipo = if (entradaFloat) "float32" else "uint8"
        val saida = when (layout) {
            LayoutSaida.TRANSPOSTO -> "YOLOv8 [4+C × anchors]"
            LayoutSaida.PADRAO -> "[anchors × 4+C]"
            LayoutSaida.END_TO_END -> "NMS embutido [N × 6]"
        }
        return "${inputSize}x$inputSize $ent $tipo · saída $saida · $numAnchors âncoras · $numClasses classe(s)"
    }
}

/**
 * Valida os shapes/tipos de um modelo e determina como interpretá-lo.
 * Função pura (sem dependência de Android/TFLite) para permitir teste unitário.
 *
 * @throws IllegalArgumentException com mensagem em português quando o formato não é suportado.
 */
fun analisarFormatoModelo(
    inShape: IntArray,
    entradaFloat32: Boolean,
    entradaUint8: Boolean,
    outShape: IntArray,
    saidaFloat32: Boolean
): FormatoModelo {
    require(entradaFloat32 || entradaUint8) {
        "Tipo de entrada não suportado (use float32 ou uint8)."
    }
    require(saidaFloat32) { "Saída quantizada não suportada (a saída deve ser float32)." }
    require(inShape.size == 4 && inShape[0] == 1) {
        "Entrada inesperada ${inShape.toList()}; esperado [1, H, W, 3] ou [1, 3, H, W]."
    }
    require(inShape.all { it > 0 }) { "Modelo com entrada de tamanho dinâmico não é suportado." }

    val nchw: Boolean
    val tamanho: Int
    when {
        inShape[3] == 3 -> {
            require(inShape[1] == inShape[2]) { "Entrada não quadrada ${inShape.toList()} não suportada." }
            nchw = false
            tamanho = inShape[1]
        }
        inShape[1] == 3 -> {
            require(inShape[2] == inShape[3]) { "Entrada não quadrada ${inShape.toList()} não suportada." }
            nchw = true
            tamanho = inShape[2]
        }
        else -> throw IllegalArgumentException(
            "Entrada ${inShape.toList()} sem 3 canais RGB; não é um detector de imagens colorido."
        )
    }

    require(outShape.size == 3 && outShape[0] == 1) {
        "Saída inesperada ${outShape.toList()}; esperado um detector YOLO [1, 4+C, N], [1, N, 4+C] ou [1, N, 6]."
    }

    val layout: LayoutSaida
    val anchors: Int
    val classes: Int
    when {
        outShape[2] == 6 && outShape[1] in 1..1000 -> {
            layout = LayoutSaida.END_TO_END
            anchors = outShape[1]
            classes = 1
        }
        outShape[1] in 5..300 && outShape[2] > outShape[1] -> {
            layout = LayoutSaida.TRANSPOSTO
            anchors = outShape[2]
            classes = outShape[1] - 4
        }
        outShape[2] in 5..300 && outShape[1] > outShape[2] -> {
            layout = LayoutSaida.PADRAO
            anchors = outShape[1]
            classes = outShape[2] - 4
        }
        else -> throw IllegalArgumentException(
            "Formato de saída ${outShape.toList()} não reconhecido como detector YOLO."
        )
    }

    return FormatoModelo(tamanho, nchw, entradaFloat32, layout, anchors, classes)
}
