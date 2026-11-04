package com.example.myapplication

import org.junit.Assert.*
import org.junit.Test

/**
 * Testes unitários para sanitização e parsing de planilhas CSV da CEPLAC/CacauSync.
 */
class CsvParsingUnitTest {

    @Test
    fun testLimparSimbologiaPura_RemovesBOMAndArtifacts() {
        val sujo = "\uFEFFClone,Parcela,Planta\r\n"
        val limpo = limparSimbologiaPura(sujo)
        assertEquals("Clone,Parcela,Planta", limpo)

        val comMojibake = "ï»¿Ensaio_TC-1801"
        val limpoMojibake = limparSimbologiaPura(comMojibake)
        assertEquals("Ensaio_TC-1801", limpoMojibake)
    }

    @Test
    fun testFatiarColunasCampos_CommaSeparated() {
        val linha = "CCN-51,1,10,Sadio"
        val colunas = fatiarColunasCampos(linha)
        assertEquals(4, colunas.size)
        assertEquals("CCN-51", colunas[0])
        assertEquals("1", colunas[1])
        assertEquals("10", colunas[2])
        assertEquals("Sadio", colunas[3])
    }

    @Test
    fun testFatiarColunasCampos_SemicolonSeparated() {
        val linha = "CEPEC-2002;3;15;Lote B"
        val colunas = fatiarColunasCampos(linha)
        assertEquals(4, colunas.size)
        assertEquals("CEPEC-2002", colunas[0])
        assertEquals("3", colunas[1])
        assertEquals("15", colunas[2])
        assertEquals("Lote B", colunas[3])
    }

    @Test
    fun testLimparSimbologiaPura_EmptyString() {
        assertEquals("", limparSimbologiaPura(""))
        assertTrue(fatiarColunasCampos("").isEmpty())
    }
}
