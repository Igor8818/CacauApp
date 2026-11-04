package com.example.myapplication

import android.net.Uri
import com.example.myapplication.data.RegistroCacau
import com.example.myapplication.network.CacauSyncClient
import com.example.myapplication.userinterface.CaixaAuditavel
import com.example.myapplication.userinterface.EstadoFruto
import com.example.myapplication.userinterface.util.PersistenciaLocalRegistros
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito

/**
 * Teste automatizado de integração de ponta a ponta (End-to-End Domain Concordance).
 * Simula a jornada completa de coleta de dados fitossanitários no campo:
 * 1. Detecção inicial pelo modelo YOLO (3 frutos);
 * 2. Auditoria e reclassificação na Tela de Revisão (1 Sadio, 1 Vassoura de Bruxa, 1 Podridão);
 * 3. Criação de RegistroCacau e validação de concordância agronômica;
 * 4. Serialização e Desserialização JSON local (PersistenciaLocalRegistros);
 * 5. Geração e validação de payload JSON para CacauSync (SPEC-001);
 * 6. Exportação e verificação das colunas da planilha CSV CEPEC;
 * 7. Tratamento e invariantes de plantas não coletáveis / ausentes.
 */
class DomainConcordanceIntegrationTest {

    private val mockUri = Mockito.mock(Uri::class.java)

    @Test
    fun testJornadaCompletaColeta_DeteccaoRevisaoPersistenciaSync() {
        // -------------------------------------------------------------------------
        // PASSO 1: Simulação das detecções iniciais da IA YOLO (3 frutos detectados)
        // -------------------------------------------------------------------------
        val deteccao1 = CaixaAuditavel(
            normLeft = 0.15f,
            normTop = 0.20f,
            normRight = 0.35f,
            normBottom = 0.50f,
            estado = EstadoFruto.SADIO,
            manual = false,
            confianca = 0.92f
        )
        val deteccao2 = CaixaAuditavel(
            normLeft = 0.40f,
            normTop = 0.30f,
            normRight = 0.60f,
            normBottom = 0.65f,
            estado = EstadoFruto.SADIO,
            manual = false,
            confianca = 0.88f
        )
        val deteccao3 = CaixaAuditavel(
            normLeft = 0.65f,
            normTop = 0.25f,
            normRight = 0.85f,
            normBottom = 0.55f,
            estado = EstadoFruto.SADIO,
            manual = false,
            confianca = 0.85f
        )
        val caixasIniciais = listOf(deteccao1, deteccao2, deteccao3)
        assertEquals(3, caixasIniciais.size)

        // -------------------------------------------------------------------------
        // PASSO 2: Auditoria pelo pesquisador na TelaRevisaoCaptura:
        // Fruto 1: Mantém SADIO
        // Fruto 2: Reclassifica para VASSOURA DE BRUXA
        // Fruto 3: Reclassifica para PODRIDÃO (Parda)
        // -------------------------------------------------------------------------
        val caixasAuditadas = listOf(
            deteccao1,
            deteccao2.copy(estado = EstadoFruto.VASSOURA),
            deteccao3.copy(estado = EstadoFruto.PODRIDAO)
        )

        val qtdSadios = caixasAuditadas.count { it.estado == EstadoFruto.SADIO }
        val qtdVassoura = caixasAuditadas.count { it.estado == EstadoFruto.VASSOURA }
        val qtdPodridao = caixasAuditadas.count { it.estado == EstadoFruto.PODRIDAO }
        val qtdNaoAvaliavel = caixasAuditadas.count { it.estado == EstadoFruto.NAO_AVALIAVEL }
        val totalFrutos = caixasAuditadas.size

        assertEquals(1, qtdSadios)
        assertEquals(1, qtdVassoura)
        assertEquals(1, qtdPodridao)
        assertEquals(0, qtdNaoAvaliavel)
        assertEquals(3, totalFrutos)
        // Invariante agronômica: Soma das categorias = Total
        assertEquals(totalFrutos, qtdSadios + qtdVassoura + qtdPodridao + qtdNaoAvaliavel)

        // -------------------------------------------------------------------------
        // PASSO 3: Confirmação e Criação do RegistroCacau (MainActivity.onSave)
        // -------------------------------------------------------------------------
        val ensaio = "TC-1801"
        val clone = "CCN-51"
        val parcela = "1"
        val numPlanta = 1
        val plantId = "${clone}_P${parcela}_Pl${numPlanta}"
        val diagnostico = totalFrutos.toString() // "3"

        val registro = RegistroCacau(
            uri = mockUri,
            plantId = plantId,
            ensaioManual = ensaio,
            parcelaManual = parcela,
            diagnostico = diagnostico,
            data = "04/11 14:30",
            numPlanta = numPlanta,
            lat = -14.793889,
            lng = -39.049444,
            totalFrutos = totalFrutos,
            sadios = qtdSadios,
            vassoura = qtdVassoura,
            podridao = qtdPodridao,
            naoAvaliavel = qtdNaoAvaliavel,
            caixas = caixasAuditadas,
            statusSync = "PENDENTE"
        )

        // Validação da concordância do modelo
        assertTrue("O registro deve ser perfeitamente concordante", registro.isConcordante())
        assertEquals("CCN-51_P1_Pl1", registro.plantId)
        assertEquals("3", registro.diagnostico)

        // -------------------------------------------------------------------------
        // PASSO 4: Persistência Local Offline (PersistenciaLocalRegistros)
        // -------------------------------------------------------------------------
        val jsonArrayLocal = PersistenciaLocalRegistros.serializarRegistrosParaJson(listOf(registro))
        assertEquals(1, jsonArrayLocal.length())

        val jsonStrLocal = jsonArrayLocal.toString()
        val carregados = PersistenciaLocalRegistros.deserializarRegistrosDeJson(jsonStrLocal)
        assertEquals(1, carregados.size)

        val regRecuperado = carregados[0]
        assertEquals(registro.plantId, regRecuperado.plantId)
        assertEquals(registro.ensaioManual, regRecuperado.ensaioManual)
        assertEquals(registro.parcelaManual, regRecuperado.parcelaManual)
        assertEquals(registro.diagnostico, regRecuperado.diagnostico)
        assertEquals(registro.totalFrutos, regRecuperado.totalFrutos)
        assertEquals(registro.sadios, regRecuperado.sadios)
        assertEquals(registro.vassoura, regRecuperado.vassoura)
        assertEquals(registro.podridao, regRecuperado.podridao)
        assertEquals(registro.naoAvaliavel, regRecuperado.naoAvaliavel)
        assertEquals(3, regRecuperado.caixas.size)
        assertEquals(EstadoFruto.SADIO, regRecuperado.caixas[0].estado)
        assertEquals(EstadoFruto.VASSOURA, regRecuperado.caixas[1].estado)
        assertEquals(EstadoFruto.PODRIDAO, regRecuperado.caixas[2].estado)
        assertTrue(regRecuperado.isConcordante())

        // -------------------------------------------------------------------------
        // PASSO 5: Formatação e Compatibilidade com Payload de Sincronização CacauSync
        // -------------------------------------------------------------------------
        val payloadSync = CacauSyncClient.criarPayloadSyncJson(ensaio, listOf(registro))
        assertEquals("TC-1801", payloadSync.getString("codigoEnsaio"))

        val leiturasJson = payloadSync.getJSONArray("leituras")
        assertEquals(1, leiturasJson.length())

        val itemLeitura = leiturasJson.getJSONObject(0)
        assertEquals("TC-1801", itemLeitura.getString("campanha"))
        assertEquals("CCN-51_P1_Pl1", itemLeitura.getString("plantId"))
        assertEquals("CCN-51", itemLeitura.getString("clone"))
        assertEquals("1", itemLeitura.getString("parcela"))
        assertEquals(1, itemLeitura.getInt("planta"))

        // O campo 'resultado' DEVE ser numérico string para planta coletável
        val resultadoStr = itemLeitura.getString("resultado")
        assertEquals("3", resultadoStr)
        assertTrue("Resultado deve satisfazer regex ^(\\d+|Não Coletável)$", resultadoStr.matches(Regex("^(\\d+|Não Coletável)$")))

        assertEquals(1, itemLeitura.getInt("sadios"))
        assertEquals(1, itemLeitura.getInt("vassoura"))
        assertEquals(1, itemLeitura.getInt("podridao"))
        assertEquals(0, itemLeitura.getInt("naoAvaliavel"))

        // Verificação das caixas YOLO normalizadas (DeteccaoDto)
        val deteccoesJson = itemLeitura.getJSONArray("deteccoes")
        assertEquals(3, deteccoesJson.length())

        for (i in 0 until deteccoesJson.length()) {
            val det = deteccoesJson.getJSONObject(i)
            val classe = det.getString("classe")
            assertTrue("Classe deve ser uma das classes suportadas", classe.matches(Regex("^(Sadio|Vassoura de Bruxa|Podridão|Não Avaliável|cacau|outro)$")))

            val confianca = det.getDouble("confianca")
            assertTrue("Confiança deve estar em [0.0, 1.0]", confianca in 0.0..1.0)

            val x = det.getDouble("x")
            val y = det.getDouble("y")
            val larg = det.getDouble("largura")
            val alt = det.getDouble("altura")

            assertTrue("x deve estar em [0.0, 1.0]", x in 0.0..1.0)
            assertTrue("y deve estar em [0.0, 1.0]", y in 0.0..1.0)
            assertTrue("largura deve ser positiva e <= 1.0", larg in 0.0..1.0)
            assertTrue("altura deve ser positiva e <= 1.0", alt in 0.0..1.0)
            assertTrue("Invariante DeteccaoDto.isGeometriaValida: x + larg <= 1.0001", x + larg <= 1.0001)
            assertTrue("Invariante DeteccaoDto.isGeometriaValida: y + alt <= 1.0001", y + alt <= 1.0001)
        }

        // -------------------------------------------------------------------------
        // PASSO 6: Exportação e Verificação da Planilha CSV CEPEC
        // -------------------------------------------------------------------------
        val itemPrincipal = "CCN-51"
        val linhaCsv = formatarLinhaCsvPersonalizado(registro, itemPrincipal)
        assertNotNull(linhaCsv)

        val colunas = fatiarColunasCampos(linhaCsv)
        assertTrue("A linha CSV personalizada deve ter pelo menos 14 colunas", colunas.size >= 14)
        assertEquals("TC-1801", colunas[0])         // Campanha
        assertEquals("CCN-51_P1_Pl1", colunas[1])    // ID_Gerado
        assertEquals("CCN-51", colunas[2])           // Clone
        assertEquals("1", colunas[3])                // Parcela
        assertEquals("1", colunas[4])                // Planta
        assertEquals("3", colunas[5])                // Resultado (numérico)
        assertEquals("04/11 14:30", colunas[6])      // Data
        assertEquals("-14.793889", colunas[7])       // Lat
        assertEquals("-39.049444", colunas[8])       // Lng
        assertEquals("3", colunas[9])                // Total_Frutos
        assertEquals("1", colunas[10])               // Sadios
        assertEquals("1", colunas[11])               // Vassoura
        assertEquals("1", colunas[12])               // Podridao
        assertEquals("0", colunas[13])               // Nao_Avaliavel
    }

    @Test
    fun testJornadaPlantaNaoColetavel_ConcordanciaEContratos() {
        val ensaio = "TC-1801"
        val clone = "CEPEC-2002"
        val parcela = "2"
        val numPlanta = 5
        val plantId = "${clone}_P${parcela}_Pl${numPlanta}"

        // Simula clique em "Marcar Planta como Não Coletável / Ausente" na TelaRevisaoCaptura
        val registroNaoColetavel = RegistroCacau(
            uri = mockUri,
            plantId = plantId,
            ensaioManual = ensaio,
            parcelaManual = parcela,
            diagnostico = "Não Coletável",
            data = "04/11 14:35",
            numPlanta = numPlanta,
            lat = -14.793889,
            lng = -39.049444,
            totalFrutos = 0,
            sadios = 0,
            vassoura = 0,
            podridao = 0,
            naoAvaliavel = 0,
            caixas = emptyList(),
            statusSync = "PENDENTE"
        )

        assertTrue(registroNaoColetavel.isConcordante())
        assertTrue(CacauSyncClient.isNaoColetavel(registroNaoColetavel.diagnostico))

        // Validação no payload JSON
        val payload = CacauSyncClient.criarPayloadSyncJson(ensaio, listOf(registroNaoColetavel))
        val leitura = payload.getJSONArray("leituras").getJSONObject(0)

        // DEVE ser exatamente a literal "Não Coletável"
        assertEquals("Não Coletável", leitura.getString("resultado"))
        assertTrue(leitura.getString("resultado").matches(Regex("^(\\d+|Não Coletável)$")))
        assertEquals(0, leitura.getInt("sadios"))
        assertEquals(0, leitura.getInt("vassoura"))
        assertEquals(0, leitura.getInt("podridao"))
        assertEquals(0, leitura.getInt("naoAvaliavel"))
        assertEquals(0, leitura.getJSONArray("deteccoes").length())

        // Validação da linha CSV
        val linhaCsv = formatarLinhaCsvPersonalizado(registroNaoColetavel, clone)
        val colunas = fatiarColunasCampos(linhaCsv)
        assertEquals("Não Coletável", colunas[5]) // Resultado
        assertEquals("0", colunas[9])             // Total_Frutos
        assertEquals("0", colunas[10])            // Sadios
        assertEquals("0", colunas[11])            // Vassoura
        assertEquals("0", colunas[12])            // Podridao
        assertEquals("0", colunas[13])            // Nao_Avaliavel
    }

    @Test
    fun testResilienciaVariacoesSemanticasNaoColetavel() {
        // Variações que pesquisadores podem ter importado de planilhas antigas
        val variacoes = listOf(
            "Não Coletável",
            "Nao Coletavel",
            "não coletável",
            "nao coletavel",
            "Morta",
            "Planta Seca",
            "Ausente",
            "Não Coletável / Ausente"
        )

        for (v in variacoes) {
            assertTrue("A variação '$v' deve ser reconhecida como Não Coletável", CacauSyncClient.isNaoColetavel(v))

            val reg = RegistroCacau(
                uri = mockUri,
                plantId = "CCN-51_P1_Pl1",
                ensaioManual = "TC-1801",
                parcelaManual = "1",
                diagnostico = v,
                data = "04/11 14:00",
                numPlanta = 1,
                lat = 0.0,
                lng = 0.0,
                totalFrutos = 0,
                sadios = 0,
                vassoura = 0,
                podridao = 0,
                naoAvaliavel = 0
            )

            // No payload para o backend Spring Boot, deve ser normalizado para a literal exata
            val payload = CacauSyncClient.criarPayloadSyncJson("TC-1801", listOf(reg))
            val leitura = payload.getJSONArray("leituras").getJSONObject(0)
            assertEquals("Não Coletável", leitura.getString("resultado"))
        }
    }

    @Test
    fun testConcordanciaRotulosEnumsCaixasAuditaveis() {
        // Valida que todos os rótulos de EstadoFruto atendem ao regex do CacauSync DeteccaoDto
        val regexCacauSync = Regex("^(Sadio|Vassoura de Bruxa|Podridão|Não Avaliável|cacau|outro)$")

        for (estado in EstadoFruto.entries) {
            assertTrue(
                "O rótulo '${estado.rotulo}' do enum ${estado.name} deve concordar com a API CacauSync",
                estado.rotulo.matches(regexCacauSync)
            )
            assertFalse("Rótulo curto não deve ser vazio", estado.rotuloCurto.isBlank())
        }
    }
}
