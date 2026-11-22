package br.gov.ceplac.cepec.cacauapp.userinterface

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.media.ExifInterface
import br.gov.ceplac.cepec.cacauapp.data.ConfigRendimentoCacau
import br.gov.ceplac.cepec.cacauapp.data.ModelConfig
import br.gov.ceplac.cepec.cacauapp.data.ModoIACacau
import br.gov.ceplac.cepec.cacauapp.data.calcularEstimativaPlantaCacau
import br.gov.ceplac.cepec.cacauapp.data.formatarEstimativaCurta
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauPerigo
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauVerdeBotanico
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauVerdeEscuro
import br.gov.ceplac.cepec.cacauapp.userinterface.util.ObjectDetectorHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

enum class EstadoFruto(
    val rotulo: String,
    val rotuloCurto: String,
    val cor: Color
) {
    SADIO("Sadio", "Sadio", Color(0xFF2E7D32)),
    VASSOURA("Vassoura de Bruxa", "Vassoura", Color(0xFF8E24AA)),
    PODRIDAO("Podridão", "Podridão", Color(0xFFD32F2F)),
    NAO_AVALIAVEL("Não Avaliável", "Não Aval.", Color(0xFFEF6C00));

    fun proximo(): EstadoFruto = when (this) {
        SADIO -> VASSOURA
        VASSOURA -> PODRIDAO
        PODRIDAO -> NAO_AVALIAVEL
        NAO_AVALIAVEL -> SADIO
    }
}

enum class AlcaCaixa {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT
}

enum class ModoVisualizacaoOverlay(val label: String) {
    COMPLETO("Completo"),
    SUTIL("Sutil"),
    LIMPO("Limpo");

    fun proximo(): ModoVisualizacaoOverlay = when (this) {
        COMPLETO -> SUTIL
        SUTIL -> LIMPO
        LIMPO -> COMPLETO
    }
}

internal data class SnapshotRevisao(
    val caixas: List<CaixaAuditavel>,
    val selecionadoIdx: Int?
)

data class CaixaAuditavel(
    val normLeft: Float,
    val normTop: Float,
    val normRight: Float,
    val normBottom: Float,
    val estado: EstadoFruto = EstadoFruto.SADIO,
    val manual: Boolean = false,
    val confianca: Float = 0.90f,
    val label: String = "Sadio"
) {
    val centroX: Float get() = (normLeft + normRight) / 2f
    val centroY: Float get() = (normTop + normBottom) / 2f
    val largura: Float get() = (normRight - normLeft).coerceAtLeast(0.01f)
    val altura: Float get() = (normBottom - normTop).coerceAtLeast(0.01f)
}

data class DetEstilizada(
    val normLeft: Float,
    val normTop: Float,
    val normRight: Float,
    val normBottom: Float,
    val confianca: Float,
    val estado: EstadoFruto = EstadoFruto.SADIO,
    val label: String = "Sadio"
)

fun mapearLabelParaEstadoFruto(label: String?): EstadoFruto {
    if (label.isNullOrBlank()) return EstadoFruto.SADIO
    val low = label.trim().lowercase(Locale.ROOT)
    return when {
        low.contains("vassoura") || low.contains("witch") -> EstadoFruto.VASSOURA
        low.contains("podrid") || low.contains("monili") || low.contains("black_pod") ||
            low.contains("frosty") || low.contains("doen") -> EstadoFruto.PODRIDAO
        low.contains("nao_avali") || low.contains("não aval") || low.contains("nao aval") ||
            low.contains("avali") -> EstadoFruto.NAO_AVALIAVEL
        else -> EstadoFruto.SADIO
    }
}

fun rotuloCurtoDoenca(estado: EstadoFruto, labelOriginal: String? = null): String {
    val raw = labelOriginal?.trim().orEmpty()
    val low = raw.lowercase(Locale.ROOT)
    return when {
        low.contains("monili") -> "Monilíase"
        low.contains("podrid") -> "Podridão"
        low.contains("vassoura") -> "Vassoura"
        low.contains("nao_avali") || low.contains("não aval") || low.contains("nao aval") -> "Não Aval."
        estado == EstadoFruto.SADIO && (raw.isBlank() || low == "cacau" || low == "fruto" || low == "sadio") -> "Sadio"
        else -> estado.rotuloCurto
    }
}

fun iouNorm(
    aLeft: Float,
    aTop: Float,
    aRight: Float,
    aBottom: Float,
    bLeft: Float,
    bTop: Float,
    bRight: Float,
    bBottom: Float
): Float {
    val interL = max(aLeft, bLeft)
    val interT = max(aTop, bTop)
    val interR = min(aRight, bRight)
    val interB = min(aBottom, bBottom)
    val interArea = max(0f, interR - interL) * max(0f, interB - interT)
    val areaA = max(0f, aRight - aLeft) * max(0f, aBottom - aTop)
    val areaB = max(0f, bRight - bLeft) * max(0f, bBottom - bTop)
    val unionArea = areaA + areaB - interArea
    return if (unionArea <= 1e-6f) 0f else (interArea / unionArea).coerceIn(0f, 1f)
}

fun iouNorm(
    a: CaixaAuditavel,
    bLeft: Float,
    bTop: Float,
    bRight: Float,
    bBottom: Float
): Float = iouNorm(a.normLeft, a.normTop, a.normRight, a.normBottom, bLeft, bTop, bRight, bBottom)

fun iouCaixasNorm(a: CaixaAuditavel, b: CaixaAuditavel): Float =
    iouNorm(a.normLeft, a.normTop, a.normRight, a.normBottom, b.normLeft, b.normTop, b.normRight, b.normBottom)

/**
 * Seleciona a bounding box tocada considerando clusters adensados:
 * prefere caixas que contêm o ponto de toque (desempate por menor distância ao centro / menor área),
 * e só usa margem expandida quando o toque fica levemente fora de qualquer caixa.
 */
fun selecionarIndiceCaixaNoToque(
    caixas: List<CaixaAuditavel>,
    tapXNorm: Float,
    tapYNorm: Float,
    margemXNorm: Float = 0.03f,
    margemYNorm: Float = 0.03f
): Int {
    if (caixas.isEmpty()) return -1

    var melhorExatoIdx: Int? = null
    var melhorExatoDist = Float.MAX_VALUE
    var melhorExatoArea = Float.MAX_VALUE

    var melhorMargemIdx: Int? = null
    var melhorMargemDist = Float.MAX_VALUE

    caixas.forEachIndexed { idx, c ->
        val dist = hypot((tapXNorm - c.centroX).toDouble(), (tapYNorm - c.centroY).toDouble()).toFloat()
        val dentroExato = tapXNorm in c.normLeft..c.normRight && tapYNorm in c.normTop..c.normBottom
        if (dentroExato) {
            val area = c.largura * c.altura
            if (dist < melhorExatoDist - 0.004f ||
                (abs(dist - melhorExatoDist) <= 0.004f && area < melhorExatoArea)
            ) {
                melhorExatoDist = dist
                melhorExatoArea = area
                melhorExatoIdx = idx
            }
        } else if (
            tapXNorm in (c.normLeft - margemXNorm)..(c.normRight + margemXNorm) &&
            tapYNorm in (c.normTop - margemYNorm)..(c.normBottom + margemYNorm)
        ) {
            if (dist < melhorMargemDist) {
                melhorMargemDist = dist
                melhorMargemIdx = idx
            }
        }
    }
    return melhorExatoIdx ?: melhorMargemIdx ?: -1
}

/**
 * Redimensiona proporcionalmente uma caixa ao redor do seu centro mantendo limites [0, 1].
 */
fun redimensionarCaixaAuditavel(
    caixa: CaixaAuditavel,
    fator: Float,
    minDimensao: Float = 0.04f,
    maxDimensao: Float = 0.90f
): CaixaAuditavel {
    val novaLarg = (caixa.largura * fator).coerceIn(minDimensao, maxDimensao)
    val novaAlt = (caixa.altura * fator).coerceIn(minDimensao, maxDimensao)
    val left = (caixa.centroX - novaLarg / 2f).coerceIn(0f, (1f - novaLarg).coerceAtLeast(0f))
    val top = (caixa.centroY - novaAlt / 2f).coerceIn(0f, (1f - novaAlt).coerceAtLeast(0f))
    val right = (left + novaLarg).coerceAtMost(1f)
    val bottom = (top + novaAlt).coerceAtMost(1f)
    return caixa.copy(
        normLeft = left,
        normTop = top,
        normRight = right,
        normBottom = bottom,
        manual = true
    )
}

/**
 * Move o corpo da caixa inteira preservando largura e altura, respeitando os limites [0, 1].
 */
fun moverCaixaAuditavel(
    caixa: CaixaAuditavel,
    dxNorm: Float,
    dyNorm: Float
): CaixaAuditavel {
    val w = (caixa.normRight - caixa.normLeft).coerceIn(0.02f, 1f)
    val h = (caixa.normBottom - caixa.normTop).coerceIn(0.02f, 1f)
    val newLeft = (caixa.normLeft + dxNorm).coerceIn(0f, (1f - w).coerceAtLeast(0f))
    val newTop = (caixa.normTop + dyNorm).coerceIn(0f, (1f - h).coerceAtLeast(0f))
    return caixa.copy(
        normLeft = newLeft,
        normTop = newTop,
        normRight = (newLeft + w).coerceAtMost(1f),
        normBottom = (newTop + h).coerceAtMost(1f),
        manual = true
    )
}

/**
 * Ajusta exatamente um dos 4 cantos da caixa auditável, preservando dimensão mínima e limites [0, 1].
 */
fun ajustarCantoCaixaAuditavel(
    caixa: CaixaAuditavel,
    alca: AlcaCaixa,
    xNorm: Float,
    yNorm: Float,
    minLargura: Float = 0.03f,
    minAltura: Float = 0.03f
): CaixaAuditavel {
    val cx = xNorm.coerceIn(0f, 1f)
    val cy = yNorm.coerceIn(0f, 1f)
    var l = caixa.normLeft
    var t = caixa.normTop
    var r = caixa.normRight
    var b = caixa.normBottom

    when (alca) {
        AlcaCaixa.TOP_LEFT -> {
            l = cx.coerceAtMost(r - minLargura).coerceAtLeast(0f)
            t = cy.coerceAtMost(b - minAltura).coerceAtLeast(0f)
        }
        AlcaCaixa.TOP_RIGHT -> {
            r = cx.coerceAtLeast(l + minLargura).coerceAtMost(1f)
            t = cy.coerceAtMost(b - minAltura).coerceAtLeast(0f)
        }
        AlcaCaixa.BOTTOM_LEFT -> {
            l = cx.coerceAtMost(r - minLargura).coerceAtLeast(0f)
            b = cy.coerceAtLeast(t + minAltura).coerceAtMost(1f)
        }
        AlcaCaixa.BOTTOM_RIGHT -> {
            r = cx.coerceAtLeast(l + minLargura).coerceAtMost(1f)
            b = cy.coerceAtLeast(t + minAltura).coerceAtMost(1f)
        }
    }

    return caixa.copy(
        normLeft = l,
        normTop = t,
        normRight = r,
        normBottom = b,
        manual = true
    )
}

/**
 * Verifica se o toque em coordenadas de tela atingiu uma das 4 alças circulares da caixa selecionada.
 */
fun detectarAlcaCaixaSelecionada(
    tapX: Float,
    tapY: Float,
    caixa: CaixaAuditavel,
    imgRect: Rect,
    raioPx: Float = 44f
): AlcaCaixa? {
    if (imgRect.width <= 1f || imgRect.height <= 1f) return null
    val l = imgRect.left + caixa.normLeft * imgRect.width
    val t = imgRect.top + caixa.normTop * imgRect.height
    val r = imgRect.left + caixa.normRight * imgRect.width
    val b = imgRect.top + caixa.normBottom * imgRect.height

    val cantos = listOf(
        AlcaCaixa.TOP_LEFT to Offset(l, t),
        AlcaCaixa.TOP_RIGHT to Offset(r, t),
        AlcaCaixa.BOTTOM_LEFT to Offset(l, b),
        AlcaCaixa.BOTTOM_RIGHT to Offset(r, b)
    )
    return cantos
        .map { (alca, pt) -> alca to hypot((tapX - pt.x).toDouble(), (tapY - pt.y).toDouble()).toFloat() }
        .filter { it.second <= raioPx }
        .minByOrNull { it.second }
        ?.first
}

/**
 * Snapping após ajuste/exclusão:
 * - Nunca sobrescreve caixas manuais ou editadas manualmente (`manual == true`).
 * - Promove caixas SADIO da IA para doença quando houver nova detecção sobreposta (`iou >= iouSnap`).
 * - Bloqueia ressurreição de novas detecções que coincidam com `caixasExcluidas` (`iou >= iouExclusao`).
 */
fun fundirDeteccoesComSnapping(
    caixasAtuais: List<CaixaAuditavel>,
    deteccoesEstaticas: List<DetEstilizada>,
    caixasExcluidas: List<CaixaAuditavel> = emptyList(),
    iouSnap: Float = 0.40f,
    iouExclusao: Float = 0.45f
): List<CaixaAuditavel> {
    val resultado = caixasAtuais.toMutableList()
    val novasUsadas = BooleanArray(deteccoesEstaticas.size)

    for (i in resultado.indices) {
        val c = resultado[i]
        if (c.manual) continue

        var melhorIdx = -1
        var melhorIoU = iouSnap
        deteccoesEstaticas.forEachIndexed { nIdx, det ->
            if (novasUsadas[nIdx]) return@forEachIndexed
            val overlap = iouNorm(
                c.normLeft, c.normTop, c.normRight, c.normBottom,
                det.normLeft, det.normTop, det.normRight, det.normBottom
            )
            if (overlap >= melhorIoU) {
                melhorIoU = overlap
                melhorIdx = nIdx
            }
        }
        if (melhorIdx >= 0) {
            val det = deteccoesEstaticas[melhorIdx]
            novasUsadas[melhorIdx] = true
            val promoverParaDoenca = c.estado == EstadoFruto.SADIO && det.estado != EstadoFruto.SADIO
            resultado[i] = c.copy(
                normLeft = det.normLeft.coerceIn(0f, 1f),
                normTop = det.normTop.coerceIn(0f, 1f),
                normRight = det.normRight.coerceIn(0f, 1f),
                normBottom = det.normBottom.coerceIn(0f, 1f),
                confianca = det.confianca,
                estado = if (promoverParaDoenca) det.estado else c.estado,
                label = if (promoverParaDoenca) det.label else c.label
            )
        }
    }

    deteccoesEstaticas.forEachIndexed { idx, det ->
        if (novasUsadas[idx]) return@forEachIndexed
        val excluida = caixasExcluidas.any { exc ->
            iouNorm(exc.normLeft, exc.normTop, exc.normRight, exc.normBottom, det.normLeft, det.normTop, det.normRight, det.normBottom) >= iouExclusao
        }
        if (excluida) return@forEachIndexed
        val jaExiste = resultado.any { cur ->
            iouNorm(cur.normLeft, cur.normTop, cur.normRight, cur.normBottom, det.normLeft, det.normTop, det.normRight, det.normBottom) >= iouSnap
        }
        if (!jaExiste) {
            resultado.add(
                CaixaAuditavel(
                    normLeft = det.normLeft.coerceIn(0f, 1f),
                    normTop = det.normTop.coerceIn(0f, 1f),
                    normRight = det.normRight.coerceIn(0f, 1f),
                    normBottom = det.normBottom.coerceIn(0f, 1f),
                    estado = det.estado,
                    manual = false,
                    confianca = det.confianca,
                    label = det.label
                )
            )
        }
    }
    return resultado
}

/**
 * Fusão em dois estágios CEPEC:
 * 1. Mantém TODOS os frutos encontrados pelo modelo de contagem (`caixasContagem`).
 * 2. Quando `deteccoesDoenca` sobrepõe uma caixa (`IoU >= iouMinimo`), promove a doença.
 * 3. Doenças detectadas fora das caixas de contagem (`IoU < iouMinimo` e `confianca >= confiancaMinimaDoenca`)
 *    são adicionadas ao conjunto auditável sem descartar frutos sadios.
 */
fun fundirContagemEDiagnosticoIoU(
    caixasContagem: List<DetEstilizada>,
    deteccoesDoenca: List<DetEstilizada>,
    iouMinimo: Float = 0.20f,
    confiancaMinimaDoenca: Float = 0.22f
): List<DetEstilizada> {
    if (caixasContagem.isEmpty()) return deteccoesDoenca
    if (deteccoesDoenca.isEmpty()) return caixasContagem

    val doencaUsada = BooleanArray(deteccoesDoenca.size)
    val saida = caixasContagem.map { contagem ->
        var melhorIdx = -1
        var melhorIoU = iouMinimo
        deteccoesDoenca.forEachIndexed { dIdx, doenca ->
            if (doencaUsada[dIdx]) return@forEachIndexed
            val overlap = iouNorm(
                contagem.normLeft, contagem.normTop, contagem.normRight, contagem.normBottom,
                doenca.normLeft, doenca.normTop, doenca.normRight, doenca.normBottom
            )
            if (overlap >= melhorIoU) {
                melhorIoU = overlap
                melhorIdx = dIdx
            }
        }
        if (melhorIdx >= 0) {
            doencaUsada[melhorIdx] = true
            val match = deteccoesDoenca[melhorIdx]
            if (match.estado != EstadoFruto.SADIO || contagem.estado == EstadoFruto.SADIO) {
                contagem.copy(
                    estado = if (match.estado != EstadoFruto.SADIO) match.estado else contagem.estado,
                    confianca = max(contagem.confianca, match.confianca),
                    label = if (match.estado != EstadoFruto.SADIO) match.label else contagem.label
                )
            } else {
                contagem
            }
        } else {
            contagem
        }
    }.toMutableList()

    deteccoesDoenca.forEachIndexed { idx, doenca ->
        if (!doencaUsada[idx] && doenca.confianca >= confiancaMinimaDoenca) {
            val colideExistente = saida.any { ex ->
                iouNorm(ex.normLeft, ex.normTop, ex.normRight, ex.normBottom, doenca.normLeft, doenca.normTop, doenca.normRight, doenca.normBottom) >= iouMinimo
            }
            if (!colideExistente) {
                saida.add(doenca)
            }
        }
    }
    return saida
}

/**
 * Carrega bitmap corrigindo TODAS as 8 orientações EXIF e escalando com segurança de memória.
 */
fun carregarBitmapOrientado(context: Context, uri: Uri, maxDim: Int = 1600): Bitmap? {
    return try {
        val orientation = runCatching {
            val stream = if (uri.scheme == "file" && uri.path != null) {
                File(uri.path!!).inputStream()
            } else {
                context.contentResolver.openInputStream(uri)
            }
            stream?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val streamBounds = if (uri.scheme == "file" && uri.path != null) {
            File(uri.path!!).inputStream()
        } else {
            context.contentResolver.openInputStream(uri)
        }
        streamBounds?.use { BitmapFactory.decodeStream(it, null, bounds) }

        var sampleSize = 1
        val maxOrig = max(bounds.outWidth, bounds.outHeight)
        if (maxOrig > maxDim) {
            while ((maxOrig / sampleSize) > maxDim * 2) {
                sampleSize *= 2
            }
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val streamBmp = if (uri.scheme == "file" && uri.path != null) {
            File(uri.path!!).inputStream()
        } else {
            context.contentResolver.openInputStream(uri)
        }
        val raw = streamBmp?.use { BitmapFactory.decodeStream(it, null, decodeOpts) } ?: return null

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return raw
        }

        val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        if (rotated !== raw) raw.recycle()
        rotated
    } catch (e: Exception) {
        Log.e("TelaRevisao", "Erro ao carregar bitmap orientado: ${e.message}", e)
        null
    }
}

private fun executarInferenciaRevisaoSincrona(
    context: Context,
    bmp: Bitmap,
    modelConfig: ModelConfig
): List<CaixaAuditavel> {
    val w = bmp.width.toFloat().coerceAtLeast(1f)
    val h = bmp.height.toFloat().coerceAtLeast(1f)
    val modo = modelConfig.modoEfetivo

    fun toDetEstilizada(
        helper: ObjectDetectorHelper,
        forcedDefaultLabel: String? = null
    ): List<DetEstilizada> {
        val res = helper.detectarSincrono(bmp, 0) ?: return emptyList()
        return res.deteccoes.map { d ->
            val cat = d.categories.firstOrNull()
            val lbl = cat?.label?.takeIf { it.isNotBlank() } ?: (forcedDefaultLabel ?: "cacau")
            val estado = mapearLabelParaEstadoFruto(lbl)
            DetEstilizada(
                normLeft = (d.boundingBox.left / w).coerceIn(0f, 1f),
                normTop = (d.boundingBox.top / h).coerceIn(0f, 1f),
                normRight = (d.boundingBox.right / w).coerceIn(0f, 1f),
                normBottom = (d.boundingBox.bottom / h).coerceIn(0f, 1f),
                confianca = cat?.score ?: 0.5f,
                estado = estado,
                label = lbl
            )
        }
    }

    if (modelConfig.usaModeloCustomizado) {
        val src = when {
            !modelConfig.modeloInternoPath.isNullOrBlank() ->
                ObjectDetectorHelper.ModelSource.InternalFile(
                    modelConfig.modeloInternoPath,
                    modelConfig.modeloExternoNome.ifBlank { "Modelo CEPEC" }
                )
            modelConfig.modeloExternoUri != null ->
                ObjectDetectorHelper.ModelSource.External(
                    modelConfig.modeloExternoUri,
                    modelConfig.modeloExternoNome.ifBlank { "Modelo CEPEC" }
                )
            else -> ObjectDetectorHelper.ModelSource.Asset("detector.tflite")
        }
        val helper = ObjectDetectorHelper(
            context = context,
            detectorListener = null,
            modelSource = src,
            scoreThreshold = modelConfig.threshold,
            maxResults = modelConfig.maxResultados,
            customLabels = modelConfig.labelsFiltro
        )
        return try {
            toDetEstilizada(helper).map {
                CaixaAuditavel(it.normLeft, it.normTop, it.normRight, it.normBottom, it.estado, false, it.confianca, it.label)
            }
        } finally {
            helper.fechar()
        }
    }

    return when (modo) {
        ModoIACacau.SOMENTE_CONTAGEM -> {
            val helperCount = ObjectDetectorHelper(
                context = context,
                detectorListener = null,
                modelSource = ObjectDetectorHelper.ModelSource.Asset("detector.tflite"),
                scoreThreshold = modelConfig.threshold,
                maxResults = modelConfig.maxResultados
            )
            try {
                toDetEstilizada(helperCount, "cacau").map {
                    CaixaAuditavel(it.normLeft, it.normTop, it.normRight, it.normBottom, EstadoFruto.SADIO, false, it.confianca, "Sadio")
                }
            } finally {
                helperCount.fechar()
            }
        }

        ModoIACacau.SOMENTE_DOENCAS -> {
            val helperDoenca = ObjectDetectorHelper(
                context = context,
                detectorListener = null,
                modelSource = ObjectDetectorHelper.ModelSource.Asset("detector_doenca.tflite"),
                scoreThreshold = modelConfig.thresholdDoenca,
                maxResults = modelConfig.maxResultados
            )
            try {
                toDetEstilizada(helperDoenca).map {
                    CaixaAuditavel(it.normLeft, it.normTop, it.normRight, it.normBottom, it.estado, false, it.confianca, it.label)
                }
            } finally {
                helperDoenca.fechar()
            }
        }

        ModoIACacau.COMBINADO_2_IAS -> {
            val helperCount = ObjectDetectorHelper(
                context = context,
                detectorListener = null,
                modelSource = ObjectDetectorHelper.ModelSource.Asset("detector.tflite"),
                scoreThreshold = modelConfig.threshold,
                maxResults = modelConfig.maxResultados
            )
            val detsContagem = try {
                toDetEstilizada(helperCount, "cacau")
            } finally {
                helperCount.fechar()
            }

            val helperDoenca = ObjectDetectorHelper(
                context = context,
                detectorListener = null,
                modelSource = ObjectDetectorHelper.ModelSource.Asset(
                    modelConfig.modeloSecundarioArquivo.ifBlank { "detector_doenca.tflite" }
                ),
                scoreThreshold = modelConfig.thresholdDoenca.coerceAtMost(0.28f),
                maxResults = modelConfig.maxResultados
            )
            val detsDoenca = try {
                toDetEstilizada(helperDoenca)
            } finally {
                helperDoenca.fechar()
            }

            val fundidas = fundirContagemEDiagnosticoIoU(
                caixasContagem = detsContagem,
                deteccoesDoenca = detsDoenca,
                iouMinimo = 0.20f,
                confiancaMinimaDoenca = modelConfig.thresholdDoenca
            )

            fundidas.map { det ->
                val casouComDoenca = detsDoenca.any { d ->
                    iouNorm(
                        det.normLeft, det.normTop, det.normRight, det.normBottom,
                        d.normLeft, d.normTop, d.normRight, d.normBottom
                    ) >= 0.20f
                }
                val estadoFinal: EstadoFruto
                val labelFinal: String
                if (det.estado == EstadoFruto.SADIO && !casouComDoenca) {
                    val cropLabel = ObjectDetectorHelper.classificarRecorteDoencaCacau(
                        bmp,
                        android.graphics.RectF(det.normLeft * w, det.normTop * h, det.normRight * w, det.normBottom * h)
                    )
                    val cropEstado = mapearLabelParaEstadoFruto(cropLabel)
                    if (cropEstado != EstadoFruto.SADIO) {
                        estadoFinal = cropEstado
                        labelFinal = cropLabel
                    } else {
                        estadoFinal = EstadoFruto.SADIO
                        labelFinal = det.label
                    }
                } else {
                    estadoFinal = det.estado
                    labelFinal = det.label
                }
                CaixaAuditavel(
                    normLeft = det.normLeft,
                    normTop = det.normTop,
                    normRight = det.normRight,
                    normBottom = det.normBottom,
                    estado = estadoFinal,
                    manual = false,
                    confianca = det.confianca,
                    label = labelFinal
                )
            }
        }
    }
}

@Composable
fun TelaRevisaoCaptura(
    fotoUri: Uri,
    plantId: String,
    modelConfig: ModelConfig,
    caixasIniciais: List<CaixaAuditavel> = emptyList(),
    pularInferenciaInicial: Boolean = false,
    configRendimento: ConfigRendimentoCacau = ConfigRendimentoCacau(),
    mostrarBotaoProximaFoto: Boolean = false,
    rotuloBotaoProximaFoto: String = "Salvar + Próx. →",
    onSalvarEProxima: ((List<CaixaAuditavel>, Int, Int, Int, Int, Int) -> Unit)? = null,
    onRefazer: () -> Unit,
    onSalvar: (List<CaixaAuditavel>, Int, Int, Int, Int, Int) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    var bitmap by remember(fotoUri) { mutableStateOf<Bitmap?>(null) }
    var processandoIA by remember(fotoUri) { mutableStateOf(true) }
    var forcarReanalise by remember { mutableStateOf(0) }

    val caixas = remember(fotoUri) { mutableStateListOf<CaixaAuditavel>() }
    val caixasExcluidas = remember(fotoUri) { mutableStateListOf<CaixaAuditavel>() }
    var selecionadoIdx by remember(fotoUri) { mutableStateOf<Int?>(null) }
    val undoStack = remember(fotoUri) { mutableStateListOf<SnapshotRevisao>() }

    var modoOverlay by remember { mutableStateOf(ModoVisualizacaoOverlay.COMPLETO) }
    var pinchModoFoto by remember { mutableStateOf(false) }
    var zoomFoto by remember(fotoUri) { mutableFloatStateOf(1f) }
    var panOffset by remember(fotoUri) { mutableStateOf(Offset.Zero) }

    fun pushUndo() {
        undoStack.add(SnapshotRevisao(caixas.toList(), selecionadoIdx))
        while (undoStack.size > 25) {
            undoStack.removeAt(0)
        }
    }

    fun desfazer() {
        if (undoStack.isEmpty()) return
        val snap = undoStack.removeAt(undoStack.lastIndex)
        caixas.clear()
        caixas.addAll(snap.caixas)
        selecionadoIdx = snap.selecionadoIdx?.takeIf { it in snap.caixas.indices }
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    val bmpLock = remember(fotoUri) { Any() }
    var telaDescartada by remember(fotoUri) { mutableStateOf(false) }
    var inferenciaEmCurso by remember(fotoUri) { mutableStateOf(false) }

    DisposableEffect(fotoUri) {
        onDispose {
            synchronized(bmpLock) {
                telaDescartada = true
                if (!inferenciaEmCurso) {
                    bitmap?.let { if (!it.isRecycled) it.recycle() }
                }
                bitmap = null
            }
        }
    }

    LaunchedEffect(fotoUri, caixasIniciais, pularInferenciaInicial, forcarReanalise) {
        processandoIA = true
        val bmp = bitmap ?: withContext(Dispatchers.IO) {
            carregarBitmapOrientado(context, fotoUri, 1600)
        }
        val deveSairSemBitmap = synchronized(bmpLock) {
            if (telaDescartada) {
                if (bmp != null && !bmp.isRecycled) bmp.recycle()
                true
            } else {
                bitmap = bmp
                false
            }
        }
        if (deveSairSemBitmap) return@LaunchedEffect

        if (bmp != null) {
            if ((pularInferenciaInicial || caixasIniciais.isNotEmpty()) && forcarReanalise == 0) {
                caixas.clear()
                caixas.addAll(caixasIniciais)
            } else {
                val podeIniciar = synchronized(bmpLock) {
                    if (telaDescartada || bmp.isRecycled) {
                        false
                    } else {
                        inferenciaEmCurso = true
                        true
                    }
                }
                if (!podeIniciar) return@LaunchedEffect

                val detectadas = withContext(Dispatchers.Default) {
                    try {
                        executarInferenciaRevisaoSincrona(context, bmp, modelConfig)
                    } finally {
                        synchronized(bmpLock) {
                            inferenciaEmCurso = false
                            if (telaDescartada && !bmp.isRecycled) {
                                bmp.recycle()
                            }
                        }
                    }
                }
                val abortado = synchronized(bmpLock) { telaDescartada }
                if (abortado) return@LaunchedEffect

                if (forcarReanalise > 0) {
                    val detEstilizadas = detectadas.map {
                        DetEstilizada(it.normLeft, it.normTop, it.normRight, it.normBottom, it.confianca, it.estado, it.label)
                    }
                    val fundidas = fundirDeteccoesComSnapping(caixas.toList(), detEstilizadas, caixasExcluidas.toList())
                    caixas.clear()
                    caixas.addAll(fundidas)
                } else {
                    caixas.clear()
                    caixas.addAll(detectadas)
                }
            }
        }
        processandoIA = false
    }

    val qtdSadios = caixas.count { it.estado == EstadoFruto.SADIO }
    val qtdVassoura = caixas.count { it.estado == EstadoFruto.VASSOURA }
    val qtdPodridao = caixas.count { it.estado == EstadoFruto.PODRIDAO }
    val qtdNaoAv = caixas.count { it.estado == EstadoFruto.NAO_AVALIAVEL }
    val estimativa = remember(caixas.toList(), configRendimento) {
        calcularEstimativaPlantaCacau(caixas.toList(), caixas.size, configRendimento)
    }

    val handleRaioPx = with(density) { 26.dp.toPx() }
    val touchSlopPx = with(density) { 8.dp.toPx() }
    val labelPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 26f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
    }
    val bgLabelPaint = remember {
        android.graphics.Paint().apply { isAntiAlias = true }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B120C))
    ) {
        // Barra superior escura em alto contraste
        Surface(
            color = Color(0xFF132215),
            contentColor = Color.White,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        IconButton(onClick = onRefazer, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Voltar", tint = Color.White)
                        }
                        Spacer(Modifier.width(4.dp))
                        Column {
                            Text(
                                text = plantId,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${modelConfig.modoEfetivo.rotuloCurto} • ${formatarEstimativaCurta(estimativa.arrobas, "@")} (${formatarEstimativaCurta(estimativa.kgAmendoasSecas, "kg")})",
                                color = Color(0xFFA5D6A7),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = { desfazer() },
                            enabled = undoStack.isNotEmpty(),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Desfazer",
                                tint = if (undoStack.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.35f)
                            )
                        }

                        Surface(
                            color = Color(0xFF203A24),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { modoOverlay = modoOverlay.proximo() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Layers, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(modoOverlay.label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Surface(
                            color = if (pinchModoFoto) Color(0xFFC98A00) else Color(0xFF203A24),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { pinchModoFoto = !pinchModoFoto }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ZoomIn, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    if (pinchModoFoto) "Zoom" else if (zoomFoto > 1.05f) "${String.format(Locale.US, "%.1f", zoomFoto)}x" else "Editar",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Área interativa da imagem + Bounding Boxes
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            val viewW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            val viewH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
            val bmp = bitmap

            if (bmp == null) {
                CircularProgressIndicator(color = Color(0xFFA5D6A7))
            } else {
                val bmpAspect = bmp.width.toFloat() / bmp.height.toFloat().coerceAtLeast(1f)
                val viewAspect = viewW / viewH
                val baseW: Float
                val baseH: Float
                if (viewAspect > bmpAspect) {
                    baseH = viewH
                    baseW = viewH * bmpAspect
                } else {
                    baseW = viewW
                    baseH = viewW / bmpAspect
                }
                val baseRect = Rect(
                    left = (viewW - baseW) / 2f,
                    top = (viewH - baseH) / 2f,
                    right = (viewW + baseW) / 2f,
                    bottom = (viewH + baseH) / 2f
                )

                val currentZoom by rememberUpdatedState(zoomFoto)
                val currentPan by rememberUpdatedState(panOffset)
                val currentPinchModo by rememberUpdatedState(pinchModoFoto)
                val currentSelecionado by rememberUpdatedState(selecionadoIdx)

                fun telaParaBase(pos: Offset): Offset {
                    val cx = viewW / 2f
                    val cy = viewH / 2f
                    val bx = (pos.x - cx - currentPan.x) / currentZoom + cx
                    val by = (pos.y - cy - currentPan.y) / currentZoom + cy
                    return Offset(bx, by)
                }

                fun aplicarTransform(zoomDelta: Float, panDelta: Offset) {
                    val novoZoom = (zoomFoto * zoomDelta).coerceIn(1f, 5f)
                    val maxPanX = (viewW * (novoZoom - 1f)) / 2f + baseW * 0.25f
                    val maxPanY = (viewH * (novoZoom - 1f)) / 2f + baseH * 0.25f
                    val novoPan = if (novoZoom <= 1.01f) {
                        Offset.Zero
                    } else {
                        Offset(
                            (panOffset.x + panDelta.x).coerceIn(-maxPanX, maxPanX),
                            (panOffset.y + panDelta.y).coerceIn(-maxPanY, maxPanY)
                        )
                    }
                    zoomFoto = novoZoom
                    panOffset = novoPan
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(fotoUri, baseRect, pinchModoFoto) {
                            if (pinchModoFoto) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    aplicarTransform(zoom, pan)
                                }
                            } else {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val downBase = telaParaBase(down.position)
                                    val selIdxAtDown = currentSelecionado
                                    val caixaSelAtDown = selIdxAtDown?.let { caixas.getOrNull(it) }
                                    val alcaAtDown = if (caixaSelAtDown != null) {
                                        detectarAlcaCaixaSelecionada(
                                            downBase.x,
                                            downBase.y,
                                            caixaSelAtDown,
                                            baseRect,
                                            raioPx = handleRaioPx / currentZoom
                                        )
                                    } else null
                                    val dentroSelAtDown = if (caixaSelAtDown != null) {
                                        val bL = baseRect.left + caixaSelAtDown.normLeft * baseRect.width
                                        val bT = baseRect.top + caixaSelAtDown.normTop * baseRect.height
                                        val bR = baseRect.left + caixaSelAtDown.normRight * baseRect.width
                                        val bB = baseRect.top + caixaSelAtDown.normBottom * baseRect.height
                                        downBase.x in bL..bR && downBase.y in bT..bB
                                    } else false

                                    var moved = false
                                    var snapshotPushed = false
                                    var lastPos = down.position
                                    var pinchUsed = false

                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val pressed = event.changes.filter { it.pressed }
                                        if (pressed.isEmpty()) break

                                        if (pressed.size >= 2) {
                                            pinchUsed = true
                                            val zoomChange = event.calculateZoom()
                                            val panChange = event.calculatePan()
                                            if (zoomChange != 1f || panChange != Offset.Zero) {
                                                aplicarTransform(zoomChange, panChange)
                                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                                            }
                                            continue
                                        }

                                        if (pinchUsed) continue
                                        val change = pressed.first()
                                        val distFromDown = (change.position - down.position).getDistance()
                                        if (!moved && distFromDown > touchSlopPx) {
                                            moved = true
                                        }
                                        if (moved && change.positionChanged()) {
                                            val curBase = telaParaBase(change.position)
                                            val prevBase = telaParaBase(lastPos)
                                            val dxNorm = (curBase.x - prevBase.x) / baseRect.width
                                            val dyNorm = (curBase.y - prevBase.y) / baseRect.height

                                            if (selIdxAtDown != null && selIdxAtDown in caixas.indices && (alcaAtDown != null || dentroSelAtDown)) {
                                                if (!snapshotPushed) {
                                                    pushUndo()
                                                    snapshotPushed = true
                                                }
                                                val target = caixas[selIdxAtDown]
                                                caixas[selIdxAtDown] = if (alcaAtDown != null) {
                                                    val xN = ((curBase.x - baseRect.left) / baseRect.width).coerceIn(0f, 1f)
                                                    val yN = ((curBase.y - baseRect.top) / baseRect.height).coerceIn(0f, 1f)
                                                    ajustarCantoCaixaAuditavel(target, alcaAtDown, xN, yN)
                                                } else {
                                                    moverCaixaAuditavel(target, dxNorm, dyNorm)
                                                }
                                                change.consume()
                                            } else if (currentZoom > 1.02f) {
                                                aplicarTransform(1f, change.position - lastPos)
                                                change.consume()
                                            }
                                        }
                                        lastPos = change.position
                                    }

                                    if (!moved && !pinchUsed) {
                                        val tapBase = telaParaBase(down.position)
                                        if (tapBase.x !in baseRect.left..baseRect.right || tapBase.y !in baseRect.top..baseRect.bottom) {
                                            selecionadoIdx = null
                                        } else {
                                            val nx = ((tapBase.x - baseRect.left) / baseRect.width).coerceIn(0f, 1f)
                                            val ny = ((tapBase.y - baseRect.top) / baseRect.height).coerceIn(0f, 1f)
                                            val hitIdx = selecionarIndiceCaixaNoToque(caixas.toList(), nx, ny)
                                            when {
                                                hitIdx < 0 -> {
                                                    pushUndo()
                                                    val halfW = 0.06f
                                                    val halfH = 0.075f
                                                    val l = (nx - halfW).coerceIn(0f, 1f - 2f * halfW)
                                                    val t = (ny - halfH).coerceIn(0f, 1f - 2f * halfH)
                                                    caixas.add(
                                                        CaixaAuditavel(
                                                            normLeft = l,
                                                            normTop = t,
                                                            normRight = (l + 2f * halfW).coerceAtMost(1f),
                                                            normBottom = (t + 2f * halfH).coerceAtMost(1f),
                                                            estado = EstadoFruto.SADIO,
                                                            manual = true,
                                                            confianca = 1f,
                                                            label = "Sadio"
                                                        )
                                                    )
                                                    selecionadoIdx = caixas.lastIndex
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                                hitIdx != selecionadoIdx -> {
                                                    // Select-before-cycle: 1º toque só seleciona
                                                    selecionadoIdx = hitIdx
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                                else -> {
                                                    // 2º toque na caixa já selecionada cicla o estado
                                                    pushUndo()
                                                    val cur = caixas[hitIdx]
                                                    val prox = cur.estado.proximo()
                                                    caixas[hitIdx] = cur.copy(
                                                        estado = prox,
                                                        manual = true,
                                                        label = prox.rotulo
                                                    )
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = zoomFoto
                                scaleY = zoomFoto
                                translationX = panOffset.x
                                translationY = panOffset.y
                            }
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Foto da Planta $plantId",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            caixas.forEachIndexed { idx, c ->
                                val selecionado = idx == selecionadoIdx
                                val l = baseRect.left + c.normLeft * baseRect.width
                                val t = baseRect.top + c.normTop * baseRect.height
                                val r = baseRect.left + c.normRight * baseRect.width
                                val b = baseRect.top + c.normBottom * baseRect.height
                                val cor = c.estado.cor

                                if (selecionado) {
                                    drawRect(
                                        color = cor.copy(alpha = 0.20f),
                                        topLeft = Offset(l, t),
                                        size = Size(r - l, b - t)
                                    )
                                }

                                val strokeWidth = when {
                                    selecionado -> 3.5.dp.toPx() / zoomFoto.coerceAtLeast(1f)
                                    modoOverlay == ModoVisualizacaoOverlay.SUTIL -> 1.8.dp.toPx() / zoomFoto.coerceAtLeast(1f)
                                    modoOverlay == ModoVisualizacaoOverlay.LIMPO -> 2.0.dp.toPx() / zoomFoto.coerceAtLeast(1f)
                                    else -> 2.6.dp.toPx() / zoomFoto.coerceAtLeast(1f)
                                }

                                drawRect(
                                    color = if (selecionado) Color.White else cor,
                                    topLeft = Offset(l, t),
                                    size = Size(r - l, b - t),
                                    style = Stroke(width = strokeWidth + (if (selecionado) 1.5f else 0f))
                                )
                                if (selecionado) {
                                    drawRect(
                                        color = cor,
                                        topLeft = Offset(l, t),
                                        size = Size(r - l, b - t),
                                        style = Stroke(width = strokeWidth)
                                    )
                                }

                                val mostrarLabel = selecionado || modoOverlay == ModoVisualizacaoOverlay.COMPLETO
                                if (mostrarLabel) {
                                    val texto = if (selecionado || modoOverlay == ModoVisualizacaoOverlay.COMPLETO) {
                                        rotuloCurtoDoenca(c.estado, c.label)
                                    } else ""
                                    if (texto.isNotEmpty()) {
                                        val tw = labelPaint.measureText(texto)
                                        val bh = 30f
                                        val bTop = (t - bh - 2f).coerceAtLeast(0f)
                                        bgLabelPaint.color = android.graphics.Color.argb(
                                            220,
                                            (cor.red * 255).toInt(),
                                            (cor.green * 255).toInt(),
                                            (cor.blue * 255).toInt()
                                        )
                                        drawContext.canvas.nativeCanvas.drawRoundRect(
                                            l,
                                            bTop,
                                            l + tw + 14f,
                                            bTop + bh,
                                            6f,
                                            6f,
                                            bgLabelPaint
                                        )
                                        drawContext.canvas.nativeCanvas.drawText(texto, l + 7f, bTop + bh - 8f, labelPaint)
                                    }
                                }

                                // 4 alças circulares nos cantos da caixa selecionada
                                if (selecionado) {
                                    val handleR = 8.dp.toPx() / zoomFoto.coerceAtLeast(1f)
                                    listOf(
                                        Offset(l, t),
                                        Offset(r, t),
                                        Offset(l, b),
                                        Offset(r, b)
                                    ).forEach { pt ->
                                        drawCircle(color = Color.White, radius = handleR + 1.5f, center = pt)
                                        drawCircle(color = cor, radius = handleR, center = pt)
                                    }
                                }
                            }
                        }
                    }
                }

                // Botão flutuante para resetar zoom ou re-analisar IA
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (zoomFoto > 1.05f || panOffset != Offset.Zero) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.72f),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.clickable {
                                zoomFoto = 1f
                                panOffset = Offset.Zero
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CenterFocusStrong, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("1x", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (!processandoIA) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.72f),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.clickable {
                                pushUndo()
                                forcarReanalise++
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFFFE082), modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Re-IA", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            if (processandoIA) {
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color(0xFFA5D6A7), strokeWidth = 2.5.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = if (modelConfig.modoEfetivo == ModoIACacau.COMBINADO_2_IAS) {
                                "Analisando frutos e doenças (2 IAs)..."
                            } else {
                                "Processando detecção IA..."
                            },
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Painel inferior solar de auditoria e ações
        Surface(
            color = Color.White,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Contadores resumidos + botão rápido + Fruto Manual
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ChipContagemEstado("Sadio", qtdSadios, EstadoFruto.SADIO.cor, Modifier.weight(1f))
                    ChipContagemEstado("Vass.", qtdVassoura, EstadoFruto.VASSOURA.cor, Modifier.weight(1f))
                    ChipContagemEstado("Podr.", qtdPodridao, EstadoFruto.PODRIDAO.cor, Modifier.weight(1f))
                    ChipContagemEstado("N.Av.", qtdNaoAv, EstadoFruto.NAO_AVALIAVEL.cor, Modifier.weight(1f))
                    Surface(
                        color = Color(0xFFE8F5E9),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, CacauVerdeBotanico.copy(alpha = 0.45f)),
                        modifier = Modifier.clickable {
                            pushUndo()
                            caixas.add(
                                CaixaAuditavel(
                                    normLeft = 0.42f,
                                    normTop = 0.40f,
                                    normRight = 0.58f,
                                    normBottom = 0.60f,
                                    estado = EstadoFruto.SADIO,
                                    manual = true,
                                    confianca = 1f,
                                    label = "Sadio"
                                )
                            )
                            selecionadoIdx = caixas.lastIndex
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = CacauVerdeEscuro, modifier = Modifier.size(16.dp))
                            Text("Fruto", color = CacauVerdeEscuro, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Barra contextual quando há caixa selecionada
                val selIdx = selecionadoIdx
                if (selIdx != null && selIdx in caixas.indices) {
                    val sel = caixas[selIdx]
                    Surface(
                        color = sel.estado.cor.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, sel.estado.cor.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                EstadoFruto.entries.forEach { est ->
                                    val ativo = sel.estado == est
                                    Surface(
                                        color = if (ativo) est.cor else Color.White,
                                        shape = RoundedCornerShape(7.dp),
                                        border = BorderStroke(1.dp, est.cor),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                pushUndo()
                                                caixas[selIdx] = sel.copy(estado = est, manual = true, label = est.rotulo)
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                    ) {
                                        Text(
                                            text = est.rotuloCurto,
                                            color = if (ativo) Color.White else est.cor,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        pushUndo()
                                        caixas[selIdx] = redimensionarCaixaAuditavel(sel, 0.85f)
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Diminuir", modifier = Modifier.size(15.dp))
                                }
                                OutlinedButton(
                                    onClick = {
                                        pushUndo()
                                        caixas[selIdx] = redimensionarCaixaAuditavel(sel, 1.18f)
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Ampliar", modifier = Modifier.size(15.dp))
                                }
                                OutlinedButton(
                                    onClick = {
                                        pushUndo()
                                        val removida = caixas.removeAt(selIdx)
                                        caixasExcluidas.add(removida)
                                        selecionadoIdx = null
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    border = BorderStroke(1.dp, CacauPerigo),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CacauPerigo),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remover", modifier = Modifier.size(15.dp))
                                }
                            }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Toque em um fruto para selecionar (arraste os cantos ou o corpo); toque fora para adicionar manual.",
                        fontSize = 11.sp,
                        color = Color(0xFF475B4B),
                        maxLines = 2
                    )
                }

                // Botões finais com contraste máximo sob luz solar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onRefazer,
                        border = BorderStroke(1.5.dp, CacauPerigo),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor = CacauPerigo
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = Modifier.weight(0.95f)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("REFAZER", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                    }

                    if (mostrarBotaoProximaFoto && onSalvarEProxima != null) {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSalvar(caixas.toList(), caixas.size, qtdSadios, qtdVassoura, qtdPodridao, qtdNaoAv)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CacauVerdeBotanico),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            modifier = Modifier.weight(0.95f)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Salvar (${caixas.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                        }

                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSalvarEProxima(caixas.toList(), caixas.size, qtdSadios, qtdVassoura, qtdPodridao, qtdNaoAv)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CacauVerdeEscuro),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            modifier = Modifier.weight(1.35f)
                        ) {
                            Text(rotuloBotaoProximaFoto, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(15.dp))
                        }
                    } else {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSalvar(caixas.toList(), caixas.size, qtdSadios, qtdVassoura, qtdPodridao, qtdNaoAv)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CacauVerdeEscuro),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier.weight(1.7f)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("SALVAR (${caixas.size} FRUTOS)", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChipContagemEstado(
    titulo: String,
    valor: Int,
    cor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = cor.copy(alpha = 0.12f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(valor.toString(), color = cor, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            Text(titulo, color = Color(0xFF1F2B20), fontSize = 10.sp, maxLines = 1)
        }
    }
}
