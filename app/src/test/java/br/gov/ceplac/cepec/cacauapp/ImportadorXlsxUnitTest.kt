package br.gov.ceplac.cepec.cacauapp

import br.gov.ceplac.cepec.cacauapp.userinterface.util.ImportadorPlanilhaUniversal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ImportadorXlsxUnitTest {

    private fun criarXlsxEmMemoria(): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            fun addEntry(name: String, content: String) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()
            }

            addEntry(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/contentTypes">
                    <Default Extension="xml" ContentType="application/xml"/>
                </Types>""".trimIndent()
            )

            addEntry(
                "xl/sharedStrings.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="7" uniqueCount="7">
                    <si><t>Clone</t></si>
                    <si><t>Parcela</t></si>
                    <si><t>Planta</t></si>
                    <si><t>Ordem</t></si>
                    <si><t>CCN-51</t></si>
                    <si><t>CEPEC-2002</t></si>
                    <si><t>P-12</t></si>
                </sst>""".trimIndent()
            )

            addEntry(
                "xl/worksheets/sheet1.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                    <sheetData>
                        <row r="1">
                            <c r="A1" t="s"><v>0</v></c>
                            <c r="B1" t="s"><v>1</v></c>
                            <c r="C1" t="s"><v>2</v></c>
                            <c r="D1" t="s"><v>3</v></c>
                        </row>
                        <row r="2">
                            <c r="A2" t="s"><v>4</v></c>
                            <c r="B2"><v>5.0</v></c>
                            <c r="C2"><v>1.0</v></c>
                            <c r="D2"><v>10.0</v></c>
                        </row>
                        <row r="3">
                            <c r="A3" t="s"><v>5</v></c>
                            <c r="B3" t="s"><v>6</v></c>
                            <c r="C3"><v>2</v></c>
                            <c r="D3"><v>20</v></c>
                        </row>
                    </sheetData>
                </worksheet>""".trimIndent()
            )
        }
        return baos.toByteArray()
    }

    @Test
    fun testLerPlanilhaXlsxEMapeamentoRota() {
        val xlsxBytes = criarXlsxEmMemoria()
        val lida = ImportadorPlanilhaUniversal.lerPlanilhaDeBytes(xlsxBytes)

        assertEquals("XLSX", lida.formato)
        assertEquals(listOf("Clone", "Parcela", "Planta", "Ordem"), lida.cabecalho)
        assertEquals(2, lida.linhas.size)

        val map = ImportadorPlanilhaUniversal.sugerirMapeamento(lida.cabecalho)
        assertTrue(map.completo)

        val rota = ImportadorPlanilhaUniversal.extrairRotaCacau(lida, map.idxClone, map.idxParcela, map.idxPlanta, map.idxOrdem)
        assertEquals(2, rota.size)
        assertEquals("CCN-51", rota[0].clone)
        assertEquals("5", rota[0].parcela)
        assertEquals(1, rota[0].planta)
        assertEquals(10, rota[0].ordem)

        assertEquals("CEPEC-2002", rota[1].clone)
        assertEquals("P-12", rota[1].parcela)
        assertEquals(2, rota[1].planta)
        assertEquals(20, rota[1].ordem)
    }

    @Test
    fun testLerCsvPontoEVirgulaComBomEAcentos() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val csv = "Variedade;Bloco;Árvore\nTSH-1188;3;7\nPH-16;4;2\n".toByteArray(StandardCharsets.UTF_8)
        val lida = ImportadorPlanilhaUniversal.lerPlanilhaDeBytes(bom + csv)

        assertEquals("CSV", lida.formato)
        val map = ImportadorPlanilhaUniversal.sugerirMapeamento(lida.cabecalho)
        assertTrue(map.completo)

        val rota = ImportadorPlanilhaUniversal.extrairRotaCacau(lida, map.idxClone, map.idxParcela, map.idxPlanta)
        assertEquals(2, rota.size)
        assertEquals("TSH-1188", rota[0].clone)
        assertEquals("3", rota[0].parcela)
        assertEquals(7, rota[0].planta)
    }

    @Test
    fun testExtrairNumeroPlantaLimpo_ComPlantIdEDecimal() {
        val csv = "Plant_ID;Parcela;Planta\nCCN-51_P1_Pl5;2;1.0\nCEPEC-2002;3;CCN-51_P1_Pl5\n".toByteArray(StandardCharsets.UTF_8)
        val lida = ImportadorPlanilhaUniversal.lerPlanilhaDeBytes(csv)
        val rota = ImportadorPlanilhaUniversal.extrairRotaCacau(lida, 0, 1, 2)

        assertEquals(2, rota.size)
        assertEquals(1, rota[0].planta)
        assertEquals(5, rota[1].planta)
    }

    @Test
    fun testSegurancaZipPathTraversal() {
        assertFalse(ImportadorPlanilhaUniversal.isSafeZipEntryName("../evil.xml"))
        assertFalse(ImportadorPlanilhaUniversal.isSafeZipEntryName("C:\\temp\\a.tflite"))
        assertTrue(ImportadorPlanilhaUniversal.isSafeZipEntryName("xl/worksheets/sheet1.xml"))
    }
}
