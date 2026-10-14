package com.example.myapplication

import com.example.myapplication.userinterface.util.LayoutSaida
import com.example.myapplication.userinterface.util.analisarFormatoModelo
import com.example.myapplication.userinterface.util.cloneDoPlantId
import com.example.myapplication.userinterface.util.escaparECorrigirCsv
import org.junit.Assert.*
import org.junit.Test

/** Regressões da revisão de modelos externos e da exportação CSV. */
class ModelFormatAndExportUnitTest {

    // ── Formatos de modelo ────────────────────────────────────────────────────

    @Test
    fun modeloCepec_yoloV8Transposto_640() {
        val f = analisarFormatoModelo(
            inShape = intArrayOf(1, 640, 640, 3), entradaFloat32 = true, entradaUint8 = false,
            outShape = intArrayOf(1, 5, 8400), saidaFloat32 = true
        )
        assertEquals(640, f.inputSize)
        assertFalse(f.entradaNchw)
        assertEquals(LayoutSaida.TRANSPOSTO, f.layout)
        assertEquals(8400, f.numAnchors)
        assertEquals(1, f.numClasses)
    }

    @Test
    fun entradaNchw_naoVira_inputSize3() {
        val f = analisarFormatoModelo(
            inShape = intArrayOf(1, 3, 416, 416), entradaFloat32 = true, entradaUint8 = false,
            outShape = intArrayOf(1, 84, 3549), saidaFloat32 = true
        )
        assertEquals(416, f.inputSize)
        assertTrue(f.entradaNchw)
        assertEquals(80, f.numClasses)
    }

    @Test
    fun saidaComNmsEmbutido_reconhecida() {
        val f = analisarFormatoModelo(
            inShape = intArrayOf(1, 640, 640, 3), entradaFloat32 = true, entradaUint8 = false,
            outShape = intArrayOf(1, 300, 6), saidaFloat32 = true
        )
        assertEquals(LayoutSaida.END_TO_END, f.layout)
        assertEquals(300, f.numAnchors)
    }

    @Test
    fun saidaPadraoAnchorsPrimeiro() {
        val f = analisarFormatoModelo(
            inShape = intArrayOf(1, 320, 320, 3), entradaFloat32 = false, entradaUint8 = true,
            outShape = intArrayOf(1, 2100, 7), saidaFloat32 = true
        )
        assertEquals(LayoutSaida.PADRAO, f.layout)
        assertEquals(3, f.numClasses)
        assertFalse(f.entradaFloat)
    }

    @Test
    fun modeloInvalido_recusadoComMensagem() {
        // Classificador de imagens (saída [1,1000]) não é detector
        assertThrows(IllegalArgumentException::class.java) {
            analisarFormatoModelo(
                intArrayOf(1, 224, 224, 3), true, false, intArrayOf(1, 1000), true
            )
        }
        // Entrada de 1 canal
        assertThrows(IllegalArgumentException::class.java) {
            analisarFormatoModelo(
                intArrayOf(1, 640, 640, 1), true, false, intArrayOf(1, 5, 8400), true
            )
        }
        // Saída quantizada
        assertThrows(IllegalArgumentException::class.java) {
            analisarFormatoModelo(
                intArrayOf(1, 640, 640, 3), true, false, intArrayOf(1, 5, 8400), false
            )
        }
    }

    // ── Exportação CSV ────────────────────────────────────────────────────────

    @Test
    fun coordenadaNegativa_numerica_naoRecebeApostrofo() {
        assertEquals("-14,7891234", escaparECorrigirCsv("-14,7891234", ";", protegerFormula = false))
    }

    @Test
    fun textoComFormula_continuaProtegido() {
        assertEquals("'=SOMA(A1)", escaparECorrigirCsv("=SOMA(A1)"))
        assertEquals("'-cmd", escaparECorrigirCsv("-cmd"))
    }

    @Test
    fun escapeRfc4180_mantido() {
        assertEquals("\"a;b\"", escaparECorrigirCsv("a;b"))
        assertEquals("\"diz \"\"oi\"\"\"", escaparECorrigirCsv("diz \"oi\""))
    }

    @Test
    fun cloneComUnderscore_naoEhTruncado() {
        assertEquals("TC_01", cloneDoPlantId("TC_01_Parcela3_Planta7"))
        assertEquals("CCN51", cloneDoPlantId("CCN51_P2_Pl10"))
        assertEquals("SO", cloneDoPlantId("SO"))
    }
}
