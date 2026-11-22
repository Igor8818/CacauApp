package br.gov.ceplac.cepec.cacauapp

import android.net.Uri
import br.gov.ceplac.cepec.cacauapp.data.RegistroCacau
import br.gov.ceplac.cepec.cacauapp.data.RotaColeta
import br.gov.ceplac.cepec.cacauapp.userinterface.CaixaAuditavel
import br.gov.ceplac.cepec.cacauapp.userinterface.EstadoFruto
import br.gov.ceplac.cepec.cacauapp.userinterface.util.csvField
import br.gov.ceplac.cepec.cacauapp.userinterface.util.gerarCsvColetaCacauString
import br.gov.ceplac.cepec.cacauapp.viewmodel.AlvoRotaCaptura
import br.gov.ceplac.cepec.cacauapp.viewmodel.CacauUiState
import br.gov.ceplac.cepec.cacauapp.viewmodel.DestinoCaptura
import br.gov.ceplac.cepec.cacauapp.viewmodel.aplicarRegistroNaLista
import br.gov.ceplac.cepec.cacauapp.viewmodel.atualizarItemRotaColetado
import br.gov.ceplac.cepec.cacauapp.viewmodel.encontrarProximaPlantaPendente
import br.gov.ceplac.cepec.cacauapp.viewmodel.gerarProximoPlantIdLivre
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class CacauViewModelUnitTest {

    private fun mockUri(): Uri = Mockito.mock(Uri::class.java)

    private fun registroExemplo(
        plantId: String,
        parcela: String = "1",
        numPlanta: Int = 1,
        totalFrutos: Int = 5,
        diagnostico: String = "5",
        lat: Double = -14.79,
        lng: Double = -39.04,
        data: String = "22/11/2026 10:00:00",
        statusSync: String = "PENDENTE"
    ) = RegistroCacau(
        uri = mockUri(),
        plantId = plantId,
        parcelaManual = parcela,
        ensaioManual = "TC-1801",
        diagnostico = diagnostico,
        data = data,
        numPlanta = numPlanta,
        lat = lat,
        lng = lng,
        totalFrutos = totalFrutos,
        sadios = totalFrutos,
        vassoura = 0,
        podridao = 0,
        naoAvaliavel = 0,
        caixas = listOf(CaixaAuditavel(0.1f, 0.1f, 0.3f, 0.3f, estado = EstadoFruto.SADIO)),
        statusSync = statusSync
    )

    @Test
    fun `gerarProximoPlantIdLivre gera id padrao e incrementa angulos 360 sem colisoes`() {
        val existentes = listOf(
            registroExemplo("CCN-51_P2_Pl3"),
            registroExemplo("ccn-51_p2_pl3_foto2")
        )

        assertEquals(
            "CCN-51_P2_Pl3",
            gerarProximoPlantIdLivre("CCN-51", "2", 3, gerarNovoAngulo = false, registros = existentes)
        )
        assertEquals(
            "CCN-51_P2_Pl3_Foto3",
            gerarProximoPlantIdLivre("CCN-51", "2", 3, gerarNovoAngulo = true, registros = existentes)
        )
        assertEquals(
            "PS-1319_P1_Pl1",
            gerarProximoPlantIdLivre(" PS-1319 ", " ", 1, gerarNovoAngulo = false, registros = existentes)
        )
    }

    @Test
    fun `encontrarProximaPlantaPendente avanca sequencialmente e faz wrap quando necessario`() {
        val rota = listOf(
            RotaColeta(clone = "CCN-51", parcela = "1", planta = 1, coletado = false, ordem = 1),
            RotaColeta(clone = "CCN-51", parcela = "1", planta = 2, coletado = true, ordem = 2),
            RotaColeta(clone = "PS-1319", parcela = "2", planta = 1, coletado = false, ordem = 3)
        )

        val prox = encontrarProximaPlantaPendente(rota, AlvoRotaCaptura("CCN-51", "1", 2))
        assertEquals("PS-1319", prox?.clone)
        assertEquals(1, prox?.planta)

        val wrap = encontrarProximaPlantaPendente(rota, AlvoRotaCaptura("PS-1319", "2", 1))
        assertEquals("CCN-51", wrap?.clone)
        assertEquals(1, wrap?.planta)

        val rotaCompleta = rota.map { it.copy(coletado = true) }
        assertNull(encontrarProximaPlantaPendente(rotaCompleta, AlvoRotaCaptura("PS-1319", "2", 1)))
    }

    @Test
    fun `atualizarItemRotaColetado marca item correspondente com quantidade ou nao coletavel`() {
        val rota = listOf(
            RotaColeta(clone = "CCN-51", parcela = "1", planta = 1, coletado = false, ordem = 1),
            RotaColeta(clone = "BN-34", parcela = "2", planta = 4, coletado = false, ordem = 2)
        )

        val aposFrutos = atualizarItemRotaColetado(
            rota = rota,
            alvo = AlvoRotaCaptura("ccn-51", "1", 1),
            qtdStr = "14"
        )
        assertTrue(aposFrutos[0].coletado)
        assertEquals("14", aposFrutos[0].qtdFrutos)
        assertFalse(aposFrutos[1].coletado)

        val aposNaoCol = atualizarItemRotaColetado(
            rota = aposFrutos,
            alvo = AlvoRotaCaptura("BN-34", "2", 4),
            qtdStr = "Não Coletável"
        )
        assertTrue(aposNaoCol[1].coletado)
        assertEquals("Não Coletável", aposNaoCol[1].qtdFrutos)
    }

    @Test
    fun `aplicarRegistroNaLista substitui duplicata em coleta e preserva metadados em edicao`() {
        val orig = registroExemplo(
            plantId = "CCN-51_P1_Pl1",
            parcela = "1",
            numPlanta = 1,
            totalFrutos = 4,
            lat = -14.791,
            lng = -39.042,
            data = "21/11/2026 09:00:00",
            statusSync = "SINCRONIZADO"
        )
        val segundo = registroExemplo(plantId = "PH-16_P2_Pl2", totalFrutos = 9)

        val aposSubstituir = aplicarRegistroNaLista(
            registros = listOf(orig, segundo),
            novoRegistro = registroExemplo(plantId = "ccn-51_p1_pl1", totalFrutos = 8),
            destino = DestinoCaptura.LIVRE,
            indiceEdicao = -1
        )
        assertEquals(2, aposSubstituir.size)
        assertEquals(8, aposSubstituir.last().totalFrutos)

        val editado = registroExemplo(
            plantId = "OUTRO_ID",
            parcela = "99",
            numPlanta = 99,
            totalFrutos = 12,
            diagnostico = "12",
            lat = 0.0,
            lng = 0.0,
            data = "22/11/2026 12:00:00"
        )
        val aposEdicao = aplicarRegistroNaLista(
            registros = listOf(orig, segundo),
            novoRegistro = editado,
            destino = DestinoCaptura.EDICAO_DADOS,
            indiceEdicao = 0
        )
        assertEquals(2, aposEdicao.size)
        assertEquals("CCN-51_P1_Pl1", aposEdicao[0].plantId)
        assertEquals("1", aposEdicao[0].parcelaManual)
        assertEquals(-14.791, aposEdicao[0].lat, 0.0001)
        assertEquals("21/11/2026 09:00:00", aposEdicao[0].data)
        assertEquals(12, aposEdicao[0].totalFrutos)
        assertEquals("PENDENTE", aposEdicao[0].statusSync)
    }

    @Test
    fun `CacauUiState calcula propriedades derivadas e csvField escapa RFC 4180`() {
        val stBase = CacauUiState(
            rota = listOf(
                RotaColeta("CCN-51", "1", 1, coletado = true, ordem = 1),
                RotaColeta("CCN-51", "1", 2, coletado = false, ordem = 2),
                RotaColeta("PS-1319", "2", 1, coletado = false, ordem = 3)
            )
        )
        assertFalse(stBase.emTelaCheia)
        assertFalse(stBase.gpsAtivo)
        assertEquals(1, stBase.coletadasRotaCount)
        assertEquals(2, stBase.pendentesRotaCount)

        val stAtivo = stBase.copy(cameraAberta = true, latAtual = -14.79, lngAtual = -39.27)
        assertTrue(stAtivo.emTelaCheia)
        assertTrue(stAtivo.gpsAtivo)

        assertEquals("\"Clone \"\"A\"\", P1\"", csvField("Clone \"A\", P1"))
        val csv = gerarCsvColetaCacauString(listOf(registroExemplo("CCN-51_P1_Pl1", totalFrutos = 6)))
        assertTrue(csv.contains("CCN-51_P1_Pl1"))
        assertTrue(csv.contains(",6,"))
    }
}
