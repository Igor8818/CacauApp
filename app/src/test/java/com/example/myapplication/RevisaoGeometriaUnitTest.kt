package com.example.myapplication

import androidx.compose.ui.geometry.Rect
import com.example.myapplication.data.MODELOS_EMBUTIDOS
import com.example.myapplication.data.ModelConfig
import com.example.myapplication.data.ModoIACacau
import com.example.myapplication.userinterface.AlcaCaixa
import com.example.myapplication.userinterface.CaixaAuditavel
import com.example.myapplication.userinterface.DetEstilizada
import com.example.myapplication.userinterface.EstadoFruto
import com.example.myapplication.userinterface.ajustarCantoCaixaAuditavel
import com.example.myapplication.userinterface.detectarAlcaCaixaSelecionada
import com.example.myapplication.userinterface.fundirDeteccoesComSnapping
import com.example.myapplication.userinterface.moverCaixaAuditavel
import com.example.myapplication.userinterface.util.ObjectDetectorHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RevisaoGeometriaUnitTest {

    @Test
    fun testMoverCaixaAuditavel_PreservaTamanhoERespeitaLimites() {
        val orig = CaixaAuditavel(0.20f, 0.20f, 0.40f, 0.50f, EstadoFruto.SADIO)
        val movida = moverCaixaAuditavel(orig, dxNorm = 0.70f, dyNorm = 0.65f)

        assertEquals(0.20f, movida.normRight - movida.normLeft, 0.001f)
        assertEquals(0.30f, movida.normBottom - movida.normTop, 0.001f)
        assertEquals(0.80f, movida.normLeft, 0.001f)
        assertEquals(1.00f, movida.normRight, 0.001f)
        assertEquals(0.70f, movida.normTop, 0.001f)
        assertEquals(1.00f, movida.normBottom, 0.001f)
        assertTrue(movida.manual)
    }

    @Test
    fun testAjustarCantoCaixa_TodosQuatroCantosEMinimo() {
        val base = CaixaAuditavel(0.30f, 0.30f, 0.60f, 0.60f, EstadoFruto.SADIO)

        val tl = ajustarCantoCaixaAuditavel(base, AlcaCaixa.TOP_LEFT, 0.10f, 0.15f)
        assertEquals(0.10f, tl.normLeft, 0.001f)
        assertEquals(0.15f, tl.normTop, 0.001f)
        assertEquals(0.60f, tl.normRight, 0.001f)

        val tr = ajustarCantoCaixaAuditavel(base, AlcaCaixa.TOP_RIGHT, 0.85f, 0.25f)
        assertEquals(0.85f, tr.normRight, 0.001f)
        assertEquals(0.25f, tr.normTop, 0.001f)

        val bl = ajustarCantoCaixaAuditavel(base, AlcaCaixa.BOTTOM_LEFT, 0.20f, 0.80f)
        assertEquals(0.20f, bl.normLeft, 0.001f)
        assertEquals(0.80f, bl.normBottom, 0.001f)

        // Tentativa de inverter BR para antes de TL respeita tamanho mínimo 0.03f
        val brClamp = ajustarCantoCaixaAuditavel(base, AlcaCaixa.BOTTOM_RIGHT, 0.10f, 0.10f, minLargura = 0.03f, minAltura = 0.03f)
        assertEquals(0.33f, brClamp.normRight, 0.001f)
        assertEquals(0.33f, brClamp.normBottom, 0.001f)
    }

    @Test
    fun testDetectarAlcaSelecionada_HitEMiss() {
        val caixa = CaixaAuditavel(0.20f, 0.20f, 0.60f, 0.60f)
        val imgRect = Rect(0f, 0f, 1000f, 1000f)

        assertEquals(AlcaCaixa.TOP_LEFT, detectarAlcaCaixaSelecionada(205f, 198f, caixa, imgRect, 40f))
        assertEquals(AlcaCaixa.TOP_RIGHT, detectarAlcaCaixaSelecionada(604f, 202f, caixa, imgRect, 40f))
        assertEquals(AlcaCaixa.BOTTOM_LEFT, detectarAlcaCaixaSelecionada(195f, 608f, caixa, imgRect, 40f))
        assertEquals(AlcaCaixa.BOTTOM_RIGHT, detectarAlcaCaixaSelecionada(598f, 605f, caixa, imgRect, 40f))
        assertNull(detectarAlcaCaixaSelecionada(400f, 400f, caixa, imgRect, 40f))
    }

    @Test
    fun testModelosExclusivosCacauEModoIA() {
        assertEquals(2, MODELOS_EMBUTIDOS.size)
        val nomes = MODELOS_EMBUTIDOS.map { it.nomeArquivo }.toSet()
        assertTrue(nomes.contains("detector.tflite"))
        assertTrue(nomes.contains("detector_doenca.tflite"))

        val base = ModelConfig()
        val soContagem = base.comModoIA(ModoIACacau.SOMENTE_CONTAGEM)
        assertEquals(ModoIACacau.SOMENTE_CONTAGEM, soContagem.modoEfetivo)
        assertEquals("detector.tflite", soContagem.arquivoPrincipalEfetivo)

        val soDoencas = base.comModoIA(ModoIACacau.SOMENTE_DOENCAS)
        assertEquals(ModoIACacau.SOMENTE_DOENCAS, soDoencas.modoEfetivo)
        assertEquals("detector_doenca.tflite", soDoencas.arquivoPrincipalEfetivo)

        val duasIas = soContagem.comModoIA(ModoIACacau.COMBINADO_2_IAS)
        assertEquals(ModoIACacau.COMBINADO_2_IAS, duasIas.modoEfetivo)
        assertTrue(duasIas.usarDiagnosticoSecundario)
    }

    @Test
    fun testNmsMultiClasse_SuprimeMesmaClasseEPermiteAdjacenteDiferente() {
        val b1 = floatArrayOf(0.50f, 0.50f, 0.20f, 0.20f, 0.90f, 2f) // Sadio
        val bMesmaClasse = floatArrayOf(0.52f, 0.50f, 0.20f, 0.20f, 0.80f, 2f) // Mesma classe, IoU alto (~0.81)
        val bOutraClasseAdjacente = floatArrayOf(0.56f, 0.50f, 0.20f, 0.20f, 0.82f, 3f) // Outra classe, IoU ~0.53 (<= 0.55)

        val res = ObjectDetectorHelper.nmsMultiClasse(
            boxes = listOf(b1, bMesmaClasse, bOutraClasseAdjacente),
            iouMesmaClasse = 0.45f,
            iouEntreClasses = 0.55f
        )

        assertEquals(2, res.size)
        assertEquals(0.90f, res[0][4], 0.001f)
        assertEquals(0.82f, res[1][4], 0.001f)
    }

    @Test
    fun testSnappingNaoDuplicaDoisParaUmEHonraExcluidas() {
        val excluida = CaixaAuditavel(0.20f, 0.20f, 0.40f, 0.50f, EstadoFruto.SADIO)
        val detStatic = DetEstilizada(0.21f, 0.21f, 0.41f, 0.51f, 0.88f, EstadoFruto.SADIO)

        // Com caixasAtuais vazia e excluida preenchida (caso de Re-IA após apagar tudo), não ressuscita!
        val resVazio = fundirDeteccoesComSnapping(
            caixasAtuais = emptyList(),
            deteccoesEstaticas = listOf(detStatic),
            caixasExcluidas = listOf(excluida)
        )
        assertTrue("Caixa excluída manualmente não pode ressuscitar", resVazio.isEmpty())

        // Duas caixas não manuais próximas a uma única detecção estática não podem fazer snap 2-para-1 para o mesmo alvo
        val c1 = CaixaAuditavel(0.20f, 0.20f, 0.40f, 0.50f, EstadoFruto.SADIO, manual = false, confianca = 0.70f)
        val c2 = CaixaAuditavel(0.22f, 0.22f, 0.42f, 0.52f, EstadoFruto.SADIO, manual = false, confianca = 0.65f)
        val unicaStatic = DetEstilizada(0.21f, 0.21f, 0.41f, 0.51f, 0.92f, EstadoFruto.VASSOURA, "VassouraBruxa")

        val fundidas = fundirDeteccoesComSnapping(
            caixasAtuais = listOf(c1, c2),
            deteccoesEstaticas = listOf(unicaStatic),
            caixasExcluidas = emptyList()
        )
        assertEquals(2, fundidas.size)
        assertEquals(EstadoFruto.VASSOURA, fundidas[0].estado)
        assertEquals(EstadoFruto.SADIO, fundidas[1].estado)
        assertEquals(0.22f, fundidas[1].normLeft, 0.001f)
    }
}
