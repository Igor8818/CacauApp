package com.example.myapplication

import com.example.myapplication.userinterface.util.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Testes unitários para utilitários de arquivos, sanitização e parser CSV RFC 4180.
 */
class FileUtilsUnitTest {

    @Test
    fun testLimparSimbologiaPura_preservaAcentosLatinos() {
        val original = "Fazenda São João — Parcela 12 (Clones Áçúçãr)"
        val sanitizado = limparSimbologiaPura(original)
        assertEquals("Fazenda_São_João_Parcela_12_Clones_Áçúçãr", sanitizado)
    }

    @Test
    fun testLimparSimbologiaPura_removeCaracteresPerigosos() {
        val entrada = "Campanha/2026\\Lote*1?Clone:CCN51<>|\""
        val resultado = limparSimbologiaPura(entrada)
        assertEquals("Campanha_2026_Lote_1_Clone_CCN51", resultado)
    }

    @Test
    fun testLimparBomECaracteresInvalidos() {
        val comBom = "\uFEFFClone;Parcela;Planta\r\n"
        val limpo = limparBomECaracteresInvalidos(comBom)
        assertEquals("Clone;Parcela;Planta", limpo)
    }

    @Test
    fun testParseLinhaCsvRfc4180_camposSimples() {
        val linha = "CCN-51;10;5;12;2026-10-05"
        val colunas = parseLinhaCsvRfc4180(linha, ';')
        assertEquals(listOf("CCN-51", "10", "5", "12", "2026-10-05"), colunas)
    }

    @Test
    fun testParseLinhaCsvRfc4180_camposComAspasESeparadorInterno() {
        val linha = "\"CCN-51; Especial\";\"Parcela 1; Bloco B\";12"
        val colunas = parseLinhaCsvRfc4180(linha, ';')
        assertEquals(3, colunas.size)
        assertEquals("CCN-51; Especial", colunas[0])
        assertEquals("Parcela 1; Bloco B", colunas[1])
        assertEquals("12", colunas[2])
    }

    @Test
    fun testParseLinhaCsvRfc4180_aspasEscapadas() {
        val linha = "\"Planta \"\"A\"\" da Rota\";Parcela 2"
        val colunas = parseLinhaCsvRfc4180(linha, ';')
        assertEquals(2, colunas.size)
        assertEquals("Planta \"A\" da Rota", colunas[0])
        assertEquals("Parcela 2", colunas[1])
    }

    @Test
    fun testDetectarSeparador_pontoEVirgula() {
        val cabecalho = "Clone;Parcela;Planta;Resultado"
        assertEquals(';', detectarSeparador(cabecalho))
    }

    @Test
    fun testDetectarSeparador_virgula() {
        val cabecalho = "Clone,Parcela,Planta,Resultado"
        assertEquals(',', detectarSeparador(cabecalho))
    }

    @Test
    fun testDetectarSeparador_tabulacao() {
        val cabecalho = "Clone\tParcela\tPlanta\tResultado"
        assertEquals('\t', detectarSeparador(cabecalho))
    }

    @Test
    fun testEscaparECorrigirCsv_protecaoFormulaInjection() {
        val formula1 = "=SUM(A1:A10)"
        val sanitizado1 = escaparECorrigirCsv(formula1, ";")
        assertTrue("Deve ser prefixado com apóstrofo", sanitizado1.startsWith("'="))

        val formula2 = "+cmd|' /C calc'!A0"
        val sanitizado2 = escaparECorrigirCsv(formula2, ";")
        assertTrue("Deve ser prefixado com apóstrofo", sanitizado2.startsWith("'+"))

        val formula3 = "@dangerous"
        val sanitizado3 = escaparECorrigirCsv(formula3, ";")
        assertTrue("Deve ser prefixado com apóstrofo", sanitizado3.startsWith("'@"))
    }

    @Test
    fun testEscaparECorrigirCsv_escapaAspasESeparador() {
        val texto = "Cacau \"Especial\"; Lote 1"
        val escapado = escaparECorrigirCsv(texto, ";")
        assertEquals("\"Cacau \"\"Especial\"\"; Lote 1\"", escapado)
    }
}
