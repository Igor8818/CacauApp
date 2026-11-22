package br.gov.ceplac.cepec.cacauapp.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.gov.ceplac.cepec.cacauapp.data.CLONES_CEPEC_PADRAO
import br.gov.ceplac.cepec.cacauapp.data.ConfigRendimentoCacau
import br.gov.ceplac.cepec.cacauapp.data.ModelConfig
import br.gov.ceplac.cepec.cacauapp.data.ModoIACacau
import br.gov.ceplac.cepec.cacauapp.data.RegistroCacau
import br.gov.ceplac.cepec.cacauapp.data.RotaColeta
import br.gov.ceplac.cepec.cacauapp.network.CacauSyncClient
import br.gov.ceplac.cepec.cacauapp.userinterface.CaixaAuditavel
import br.gov.ceplac.cepec.cacauapp.userinterface.util.PersistenciaLocalRegistros
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

enum class DestinoCaptura {
    LIVRE,
    ROTA,
    TESTE_IA,
    EDICAO_DADOS
}

data class AlvoRotaCaptura(
    val clone: String,
    val parcela: String,
    val planta: Int
)

internal fun gerarProximoPlantIdLivre(
    clone: String,
    parcela: String,
    numPlanta: Int,
    gerarNovoAngulo: Boolean,
    registros: List<RegistroCacau>
): String {
    val base = "${clone.trim()}_P${parcela.trim().ifBlank { "1" }}_Pl$numPlanta"
    if (!gerarNovoAngulo) return base
    var idx = 2
    while (registros.any { it.plantId.equals("${base}_Foto$idx", ignoreCase = true) }) {
        idx++
    }
    return "${base}_Foto$idx"
}

internal fun encontrarProximaPlantaPendente(
    rota: List<RotaColeta>,
    alvoAnterior: AlvoRotaCaptura?
): RotaColeta? {
    val idxAtual = if (alvoAnterior != null) {
        rota.indexOfFirst {
            it.clone.equals(alvoAnterior.clone, ignoreCase = true) &&
                it.parcela.equals(alvoAnterior.parcela, ignoreCase = true) &&
                it.planta == alvoAnterior.planta
        }
    } else {
        -1
    }
    val aposAtual = if (idxAtual >= 0) {
        rota.drop(idxAtual + 1).firstOrNull { !it.coletado }
    } else {
        null
    }
    return aposAtual ?: rota.firstOrNull { !it.coletado }
}

internal fun atualizarItemRotaColetado(
    rota: List<RotaColeta>,
    alvo: AlvoRotaCaptura,
    qtdStr: String
): List<RotaColeta> {
    var atualizado = false
    return rota.map { item ->
        if (!atualizado &&
            item.clone.equals(alvo.clone, ignoreCase = true) &&
            item.parcela.equals(alvo.parcela, ignoreCase = true) &&
            item.planta == alvo.planta
        ) {
            atualizado = true
            item.copy(coletado = true, qtdFrutos = qtdStr)
        } else {
            item
        }
    }
}

internal fun aplicarRegistroNaLista(
    registros: List<RegistroCacau>,
    novoRegistro: RegistroCacau,
    destino: DestinoCaptura,
    indiceEdicao: Int
): List<RegistroCacau> {
    if (destino == DestinoCaptura.EDICAO_DADOS && indiceEdicao in registros.indices) {
        val mut = registros.toMutableList()
        val orig = mut[indiceEdicao]
        mut[indiceEdicao] = orig.copy(
            diagnostico = novoRegistro.diagnostico,
            uri = novoRegistro.uri,
            totalFrutos = novoRegistro.totalFrutos,
            sadios = novoRegistro.sadios,
            vassoura = novoRegistro.vassoura,
            podridao = novoRegistro.podridao,
            naoAvaliavel = novoRegistro.naoAvaliavel,
            caixas = novoRegistro.caixas,
            statusSync = "PENDENTE"
        )
        return mut
    }
    return registros.filterNot { it.plantId.equals(novoRegistro.plantId, ignoreCase = true) } + novoRegistro
}

data class CacauUiState(
    val abaSelecionada: Int = 0,
    val configEnsaio: PersistenciaLocalRegistros.ConfigEnsaioLocal = PersistenciaLocalRegistros.ConfigEnsaioLocal(),
    val configRendimento: ConfigRendimentoCacau = ConfigRendimentoCacau(),
    val modelConfig: ModelConfig = ModelConfig(),
    val registros: List<RegistroCacau> = emptyList(),
    val rota: List<RotaColeta> = emptyList(),
    val timestampRota: Long = 0L,
    val latAtual: Double = 0.0,
    val lngAtual: Double = 0.0,
    val cameraAberta: Boolean = false,
    val destinoCaptura: DestinoCaptura = DestinoCaptura.LIVRE,
    val plantIdEmCaptura: String = "",
    val uriFotoEmRevisao: Uri? = null,
    val caixasIniciaisRevisao: List<CaixaAuditavel> = emptyList(),
    val indiceEditandoDados: Int = -1,
    val mostrarConfigIA: Boolean = false,
    val mostrarConfigRendimento: Boolean = false,
    val mostrarConfigServidor: Boolean = false,
    val sincronizandoPc: Boolean = false,
    val servidorConfigurado: Boolean = false,
    val cloneLivre: String = CLONES_CEPEC_PADRAO.first(),
    val parcelaLivre: String = "1",
    val numPlantaLivre: Int = 1,
    val gerarProximoAnguloLivre: Boolean = false,
    val alvoRotaAtual: AlvoRotaCaptura? = null
) {
    val emTelaCheia: Boolean
        get() = cameraAberta || uriFotoEmRevisao != null || mostrarConfigIA

    val gpsAtivo: Boolean
        get() = latAtual != 0.0 || lngAtual != 0.0

    val pendentesRotaCount: Int
        get() = rota.count { !it.coletado }

    val coletadasRotaCount: Int
        get() = rota.count { it.coletado }
}

class CacauViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext: Context get() = getApplication<Application>()

    private val _uiState = MutableStateFlow(carregarEstadoInicial())
    val uiState: StateFlow<CacauUiState> = _uiState.asStateFlow()

    private val _eventosToast = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val eventosToast: SharedFlow<String> = _eventosToast.asSharedFlow()

    init {
        iniciarObservacaoRotaUsb()
    }

    private fun carregarEstadoInicial(): CacauUiState {
        val configEnsaio = PersistenciaLocalRegistros.carregarConfigEnsaio(appContext)
        val configRendimento = PersistenciaLocalRegistros.carregarConfigRendimento(appContext)
        val modelConfig = PersistenciaLocalRegistros.carregarConfiguracaoModelo(appContext)
        val registros = PersistenciaLocalRegistros.carregarRegistros(appContext)
        val rota = PersistenciaLocalRegistros.carregarRotaPrecarregada(appContext).second
        val timestampRota = PersistenciaLocalRegistros.obterTimestampRotaPrecarregada(appContext)
        val prefsSync = appContext.getSharedPreferences("cacausync_prefs", Context.MODE_PRIVATE)
        val servidorConfigurado = !(prefsSync.getString("server_url", "") ?: "").isBlank()

        return CacauUiState(
            configEnsaio = configEnsaio,
            configRendimento = configRendimento,
            modelConfig = modelConfig,
            registros = registros,
            rota = rota,
            timestampRota = timestampRota,
            servidorConfigurado = servidorConfigurado
        )
    }

    private fun iniciarObservacaoRotaUsb() {
        viewModelScope.launch {
            while (true) {
                delay(2500L)
                val tsAtual = _uiState.value.timestampRota
                val novoTs = withContext(Dispatchers.IO) {
                    PersistenciaLocalRegistros.obterTimestampRotaPrecarregada(appContext)
                }
                if (novoTs > tsAtual) {
                    val (codEnsaioRota, itensRota) = withContext(Dispatchers.IO) {
                        PersistenciaLocalRegistros.carregarRotaPrecarregada(appContext)
                    }
                    if (itensRota.isNotEmpty()) {
                        _uiState.update { st ->
                            var cfgAtualizado = st.configEnsaio
                            if (codEnsaioRota.isNotBlank() && codEnsaioRota != cfgAtualizado.codigoEnsaio) {
                                cfgAtualizado = cfgAtualizado.copy(codigoEnsaio = codEnsaioRota)
                                PersistenciaLocalRegistros.salvarConfigEnsaio(appContext, cfgAtualizado)
                            }
                            st.copy(
                                timestampRota = novoTs,
                                rota = itensRota,
                                configEnsaio = cfgAtualizado
                            )
                        }
                        emitirToast("Nova rota recebida via USB (${itensRota.size} plantas)!")
                    } else {
                        _uiState.update { it.copy(timestampRota = novoTs) }
                    }
                }
            }
        }
    }

    private fun emitirToast(mensagem: String) {
        _eventosToast.tryEmit(mensagem)
    }

    fun selecionarAba(indice: Int) {
        _uiState.update { it.copy(abaSelecionada = indice.coerceIn(0, 3)) }
    }

    fun abrirConfigIA(abrir: Boolean) {
        _uiState.update { it.copy(mostrarConfigIA = abrir) }
    }

    fun abrirConfigRendimento(abrir: Boolean) {
        _uiState.update { it.copy(mostrarConfigRendimento = abrir) }
    }

    fun abrirConfigServidor(abrir: Boolean) {
        _uiState.update { it.copy(mostrarConfigServidor = abrir) }
    }

    fun atualizarLocalizacao(lat: Double, lng: Double) {
        _uiState.update { it.copy(latAtual = lat, lngAtual = lng) }
    }

    fun atualizarCodigoEnsaio(novoCodigo: String) {
        val atualizado = _uiState.value.configEnsaio.copy(
            codigoEnsaio = novoCodigo.trim().ifBlank { "TC-1801" }
        )
        PersistenciaLocalRegistros.salvarConfigEnsaio(appContext, atualizado)
        _uiState.update { it.copy(configEnsaio = atualizado) }
    }

    fun criarNovoEnsaio(codigo: String, manterRota: Boolean) {
        val novoCfg = PersistenciaLocalRegistros.criarNovoEnsaioIsolado(appContext, codigo, manterRota)
        val novosRegistros = PersistenciaLocalRegistros.carregarRegistros(appContext)
        val novaRota = PersistenciaLocalRegistros.carregarRotaPrecarregada(appContext).second
        val novoTs = PersistenciaLocalRegistros.obterTimestampRotaPrecarregada(appContext)
        _uiState.update {
            it.copy(
                configEnsaio = novoCfg,
                registros = novosRegistros,
                rota = novaRota,
                timestampRota = novoTs
            )
        }
    }

    fun alternarEnsaio(id: String) {
        val trocado = PersistenciaLocalRegistros.alternarEnsaioAtivo(appContext, id) ?: return
        val novosRegistros = PersistenciaLocalRegistros.carregarRegistros(appContext)
        val novaRota = PersistenciaLocalRegistros.carregarRotaPrecarregada(appContext).second
        val novoTs = PersistenciaLocalRegistros.obterTimestampRotaPrecarregada(appContext)
        _uiState.update {
            it.copy(
                configEnsaio = trocado,
                registros = novosRegistros,
                rota = novaRota,
                timestampRota = novoTs
            )
        }
    }

    fun atualizarConfigModelo(novo: ModelConfig) {
        PersistenciaLocalRegistros.salvarConfiguracaoModelo(appContext, novo)
        _uiState.update { it.copy(modelConfig = novo) }
    }

    fun trocarModoIA(novoModo: ModoIACacau) {
        atualizarConfigModelo(_uiState.value.modelConfig.comModoIA(novoModo))
    }

    fun atualizarThresholdPreview(novoThresh: Float) {
        val atual = _uiState.value.modelConfig
        val atualizado = if (atual.modoEfetivo == ModoIACacau.SOMENTE_DOENCAS) {
            atual.copy(thresholdDoenca = novoThresh)
        } else {
            atual.copy(threshold = novoThresh)
        }
        atualizarConfigModelo(atualizado)
    }

    fun atualizarConfigRendimento(novo: ConfigRendimentoCacau) {
        PersistenciaLocalRegistros.salvarConfigRendimento(appContext, novo)
        _uiState.update {
            it.copy(
                configRendimento = novo,
                mostrarConfigRendimento = false
            )
        }
    }

    fun salvarConfigServidor(url: String, email: String, pass: String, ensaio: String) {
        val codigoLimpo = ensaio.trim().ifBlank { "TC-1801" }
        appContext.getSharedPreferences("cacausync_prefs", Context.MODE_PRIVATE).edit()
            .putString("server_url", url.trim())
            .putString("user_email", email.trim())
            .putString("user_pass", pass)
            .putString("codigo_ensaio", codigoLimpo)
            .apply()

        val cfgAtualizado = _uiState.value.configEnsaio.copy(codigoEnsaio = codigoLimpo)
        PersistenciaLocalRegistros.salvarConfigEnsaio(appContext, cfgAtualizado)

        _uiState.update {
            it.copy(
                configEnsaio = cfgAtualizado,
                servidorConfigurado = url.isNotBlank(),
                mostrarConfigServidor = false
            )
        }
        emitirToast("Servidor CacauSync salvo!")
    }

    // ── Coleta Livre & Modo Rota ──

    fun alterarCloneLivre(novoClone: String, resetarPlanta: Boolean) {
        _uiState.update {
            it.copy(
                cloneLivre = novoClone,
                numPlantaLivre = if (resetarPlanta) 1 else it.numPlantaLivre
            )
        }
    }

    fun alterarParcelaLivre(novaParcela: String, resetarPlanta: Boolean) {
        _uiState.update {
            it.copy(
                parcelaLivre = novaParcela,
                numPlantaLivre = if (resetarPlanta) 1 else it.numPlantaLivre
            )
        }
    }

    fun alterarNumPlantaLivre(novoNum: Int) {
        _uiState.update { it.copy(numPlantaLivre = novoNum.coerceAtLeast(1)) }
    }

    fun proximoPlantIdLivreActual(forcarMesmaPlanta: Boolean = false): String {
        val st = _uiState.value
        return gerarProximoPlantIdLivre(
            clone = st.cloneLivre,
            parcela = st.parcelaLivre,
            numPlanta = st.numPlantaLivre,
            gerarNovoAngulo = forcarMesmaPlanta || st.gerarProximoAnguloLivre,
            registros = st.registros
        )
    }

    fun abrirCameraLivre(mesmaPlantaNovoAngulo: Boolean = false) {
        _uiState.update { st ->
            val pid = gerarProximoPlantIdLivre(
                clone = st.cloneLivre,
                parcela = st.parcelaLivre,
                numPlanta = st.numPlantaLivre,
                gerarNovoAngulo = mesmaPlantaNovoAngulo,
                registros = st.registros
            )
            st.copy(
                gerarProximoAnguloLivre = mesmaPlantaNovoAngulo,
                destinoCaptura = DestinoCaptura.LIVRE,
                plantIdEmCaptura = pid,
                uriFotoEmRevisao = null,
                caixasIniciaisRevisao = emptyList(),
                cameraAberta = true
            )
        }
    }

    fun prepararGaleriaLivre(mesmaPlantaNovoAngulo: Boolean) {
        _uiState.update { it.copy(gerarProximoAnguloLivre = mesmaPlantaNovoAngulo) }
    }

    fun abrirRevisaoGaleriaLivre(uriUsada: Uri) {
        _uiState.update { st ->
            val pid = gerarProximoPlantIdLivre(
                clone = st.cloneLivre,
                parcela = st.parcelaLivre,
                numPlanta = st.numPlantaLivre,
                gerarNovoAngulo = st.gerarProximoAnguloLivre,
                registros = st.registros
            )
            st.copy(
                destinoCaptura = DestinoCaptura.LIVRE,
                plantIdEmCaptura = pid,
                caixasIniciaisRevisao = emptyList(),
                uriFotoEmRevisao = uriUsada,
                cameraAberta = false
            )
        }
    }

    fun abrirCameraRota(item: RotaColeta) {
        _uiState.update {
            it.copy(
                destinoCaptura = DestinoCaptura.ROTA,
                alvoRotaAtual = AlvoRotaCaptura(item.clone, item.parcela, item.planta),
                plantIdEmCaptura = "${item.clone}_P${item.parcela}_Pl${item.planta}",
                uriFotoEmRevisao = null,
                caixasIniciaisRevisao = emptyList(),
                cameraAberta = true
            )
        }
    }

    fun abrirTesteCameraIA() {
        _uiState.update {
            it.copy(
                mostrarConfigIA = false,
                destinoCaptura = DestinoCaptura.TESTE_IA,
                plantIdEmCaptura = "TESTE_CAMPO",
                uriFotoEmRevisao = null,
                caixasIniciaisRevisao = emptyList(),
                cameraAberta = true
            )
        }
    }

    fun fecharCamera() {
        _uiState.update { it.copy(cameraAberta = false) }
    }

    fun aoFotoCapturada(uri: Uri) {
        _uiState.update {
            it.copy(
                cameraAberta = false,
                caixasIniciaisRevisao = emptyList(),
                uriFotoEmRevisao = uri
            )
        }
    }

    fun refazerFotoRevisao() {
        _uiState.update { st ->
            st.copy(
                uriFotoEmRevisao = null,
                caixasIniciaisRevisao = emptyList(),
                cameraAberta = st.destinoCaptura != DestinoCaptura.EDICAO_DADOS
            )
        }
    }

    fun atualizarRota(novaRota: List<RotaColeta>, codigoEnsaioPlanilha: String?) {
        val st = _uiState.value
        val cod = codigoEnsaioPlanilha?.takeIf { it.isNotBlank() } ?: st.configEnsaio.codigoEnsaio
        var cfgAtualizado = st.configEnsaio
        if (cod != cfgAtualizado.codigoEnsaio) {
            cfgAtualizado = cfgAtualizado.copy(codigoEnsaio = cod)
            PersistenciaLocalRegistros.salvarConfigEnsaio(appContext, cfgAtualizado)
        }
        PersistenciaLocalRegistros.salvarRotaPrecarregada(appContext, novaRota, cod)
        val novoTs = PersistenciaLocalRegistros.obterTimestampRotaPrecarregada(appContext)
        _uiState.update {
            it.copy(
                rota = novaRota,
                configEnsaio = cfgAtualizado,
                timestampRota = novoTs
            )
        }
    }

    fun recarregarRotaLocal() {
        val (_, rec) = PersistenciaLocalRegistros.carregarRotaPrecarregada(appContext)
        if (rec.isNotEmpty()) {
            atualizarRota(rec, null)
            emitirToast("Rota recarregada (${rec.size})")
        }
    }

    fun marcarNaoColetavelRota(item: RotaColeta) {
        val alvo = AlvoRotaCaptura(item.clone, item.parcela, item.planta)
        val pid = "${item.clone}_P${item.parcela}_Pl${item.planta}"
        registrarPlantaInterno(
            pidFinal = pid,
            fotoUri = Uri.EMPTY,
            caixas = emptyList(),
            total = 0,
            sadios = 0,
            vassoura = 0,
            podridao = 0,
            naoAval = 0,
            destino = DestinoCaptura.ROTA,
            alvoRota = alvo,
            diagnosticoCustom = "Não Coletável",
            naoColetavelFlag = true,
            parcelaRegistro = item.parcela,
            numPlantaRegistro = item.planta
        )
        emitirToast("$pid marcada como Não Coletável.")
    }

    fun marcarNaoColetavelLivre() {
        val st = _uiState.value
        val pid = "${st.cloneLivre.trim()}_P${st.parcelaLivre.trim().ifBlank { "1" }}_Pl${st.numPlantaLivre}"
        registrarPlantaInterno(
            pidFinal = pid,
            fotoUri = Uri.EMPTY,
            caixas = emptyList(),
            total = 0,
            sadios = 0,
            vassoura = 0,
            podridao = 0,
            naoAval = 0,
            destino = DestinoCaptura.LIVRE,
            alvoRota = st.alvoRotaAtual,
            diagnosticoCustom = "Não Coletável",
            naoColetavelFlag = true,
            parcelaRegistro = st.parcelaLivre,
            numPlantaRegistro = st.numPlantaLivre
        )
        _uiState.update { it.copy(numPlantaLivre = it.numPlantaLivre + 1) }
        emitirToast("$pid salva como Não Coletável.")
    }

    fun salvarRevisao(
        caixas: List<CaixaAuditavel>,
        tot: Int,
        sad: Int,
        vas: Int,
        pod: Int,
        nav: Int
    ) {
        val st = _uiState.value
        val uriRev = st.uriFotoEmRevisao ?: return
        if (st.destinoCaptura != DestinoCaptura.TESTE_IA) {
            registrarPlantaInterno(
                pidFinal = st.plantIdEmCaptura,
                fotoUri = uriRev,
                caixas = caixas,
                total = tot,
                sadios = sad,
                vassoura = vas,
                podridao = pod,
                naoAval = nav,
                destino = st.destinoCaptura,
                alvoRota = st.alvoRotaAtual
            )
            emitirToast("Salvo: ${st.plantIdEmCaptura} ($tot frutos)")
        } else {
            emitirToast("Teste concluído ($tot frutos)")
        }

        _uiState.update { cur ->
            val avancar = cur.destinoCaptura == DestinoCaptura.LIVRE &&
                cur.configEnsaio.autoAvancarPlanta &&
                !cur.gerarProximoAnguloLivre
            cur.copy(
                uriFotoEmRevisao = null,
                caixasIniciaisRevisao = emptyList(),
                indiceEditandoDados = -1,
                gerarProximoAnguloLivre = false,
                numPlantaLivre = if (avancar) cur.numPlantaLivre + 1 else cur.numPlantaLivre
            )
        }
    }

    fun salvarRevisaoEProxima(
        caixas: List<CaixaAuditavel>,
        tot: Int,
        sad: Int,
        vas: Int,
        pod: Int,
        nav: Int
    ) {
        val st = _uiState.value
        val uriRev = st.uriFotoEmRevisao ?: return
        val destinoAnterior = st.destinoCaptura
        val alvoAnterior = st.alvoRotaAtual

        registrarPlantaInterno(
            pidFinal = st.plantIdEmCaptura,
            fotoUri = uriRev,
            caixas = caixas,
            total = tot,
            sadios = sad,
            vassoura = vas,
            podridao = pod,
            naoAval = nav,
            destino = destinoAnterior,
            alvoRota = alvoAnterior
        )

        _uiState.update {
            it.copy(
                uriFotoEmRevisao = null,
                caixasIniciaisRevisao = emptyList(),
                gerarProximoAnguloLivre = false
            )
        }

        if (destinoAnterior == DestinoCaptura.ROTA) {
            val proxPendente = encontrarProximaPlantaPendente(_uiState.value.rota, alvoAnterior)
            if (proxPendente != null) {
                abrirCameraRota(proxPendente)
            } else {
                emitirToast("Rota concluída! Todas as plantas foram coletadas.")
            }
        } else if (destinoAnterior == DestinoCaptura.LIVRE) {
            _uiState.update { it.copy(numPlantaLivre = it.numPlantaLivre + 1) }
            abrirCameraLivre(mesmaPlantaNovoAngulo = false)
        }
    }

    fun editarRegistro(origIdx: Int, atualizado: RegistroCacau) {
        _uiState.update { st ->
            if (origIdx !in st.registros.indices) return@update st
            val novaLista = st.registros.toMutableList().apply { this[origIdx] = atualizado }
            PersistenciaLocalRegistros.salvarRegistros(appContext, novaLista)
            st.copy(registros = novaLista)
        }
    }

    fun abrirRevisaoRegistro(origIdx: Int, reg: RegistroCacau) {
        if (reg.uri == Uri.EMPTY) return
        _uiState.update {
            it.copy(
                indiceEditandoDados = origIdx,
                destinoCaptura = DestinoCaptura.EDICAO_DADOS,
                plantIdEmCaptura = reg.plantId,
                caixasIniciaisRevisao = reg.caixas,
                uriFotoEmRevisao = reg.uri,
                cameraAberta = false
            )
        }
    }

    fun excluirRegistro(origIdx: Int) {
        _uiState.update { st ->
            if (origIdx !in st.registros.indices) return@update st
            val novaLista = st.registros.toMutableList().apply { removeAt(origIdx) }
            PersistenciaLocalRegistros.salvarRegistros(appContext, novaLista)
            st.copy(registros = novaLista)
        }
    }

    fun sincronizarComPcAgora() {
        val st = _uiState.value
        if (st.sincronizandoPc || st.registros.isEmpty()) return

        _uiState.update { it.copy(sincronizandoPc = true) }
        val codigoEnsaio = st.configEnsaio.codigoEnsaio
        val snapshot = st.registros.toList()

        viewModelScope.launch {
            val statusAtualizados = ConcurrentHashMap<String, String>()
            val res = withContext(Dispatchers.IO) {
                CacauSyncClient.sincronizarAutomatico(
                    context = appContext,
                    ensaioPadrao = codigoEnsaio,
                    registros = snapshot,
                    onProgresso = {},
                    onStatusRegistro = { pid, status ->
                        statusAtualizados[pid] = status
                    }
                )
            }

            var listaPersistir: List<RegistroCacau>? = null
            _uiState.update { cur ->
                val registrosAtualizados = if (statusAtualizados.isNotEmpty()) {
                    cur.registros.map { reg ->
                        val novoSt = statusAtualizados[reg.plantId]
                        if (novoSt != null) reg.copy(statusSync = novoSt) else reg
                    }
                } else {
                    cur.registros
                }
                if (res.isSuccess) {
                    listaPersistir = registrosAtualizados
                }
                cur.copy(
                    registros = registrosAtualizados,
                    sincronizandoPc = false
                )
            }

            listaPersistir?.let { PersistenciaLocalRegistros.salvarRegistros(appContext, it) }

            res.onSuccess { syncRes ->
                emitirToast(
                    "Enviado! ${syncRes.novasInseridas} nova(s), ${syncRes.fotosEnviadas} foto(s) em ${syncRes.servidorUtilizado}"
                )
            }.onFailure { err ->
                emitirToast("PC não encontrado (${err.message ?: "verifique Wi-Fi ou cabo USB"})")
            }
        }
    }

    private fun registrarPlantaInterno(
        pidFinal: String,
        fotoUri: Uri,
        caixas: List<CaixaAuditavel>,
        total: Int,
        sadios: Int,
        vassoura: Int,
        podridao: Int,
        naoAval: Int,
        destino: DestinoCaptura,
        alvoRota: AlvoRotaCaptura?,
        diagnosticoCustom: String? = null,
        naoColetavelFlag: Boolean = false,
        parcelaRegistro: String = if (destino == DestinoCaptura.ROTA && alvoRota != null) alvoRota.parcela else _uiState.value.parcelaLivre,
        numPlantaRegistro: Int = if (destino == DestinoCaptura.ROTA && alvoRota != null) alvoRota.planta else _uiState.value.numPlantaLivre
    ) {
        val st = _uiState.value
        val dataStr = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.ROOT).format(Date())
        val ehNaoColetavel = naoColetavelFlag ||
            (diagnosticoCustom != null && CacauSyncClient.isNaoColetavel(diagnosticoCustom))
        val diag = diagnosticoCustom ?: if (ehNaoColetavel) "Não Coletável" else total.toString()

        val novoReg = RegistroCacau(
            plantId = pidFinal,
            parcelaManual = parcelaRegistro,
            ensaioManual = st.configEnsaio.codigoEnsaio,
            diagnostico = diag,
            data = dataStr,
            uri = fotoUri,
            lat = st.latAtual,
            lng = st.lngAtual,
            numPlanta = numPlantaRegistro,
            totalFrutos = total,
            sadios = sadios,
            vassoura = vassoura,
            podridao = podridao,
            naoAvaliavel = naoAval,
            caixas = caixas,
            statusSync = "PENDENTE"
        )

        _uiState.update { cur ->
            val novaLista = aplicarRegistroNaLista(
                registros = cur.registros,
                novoRegistro = novoReg,
                destino = destino,
                indiceEdicao = cur.indiceEditandoDados
            )
            PersistenciaLocalRegistros.salvarRegistros(appContext, novaLista)

            var novaRota = cur.rota
            var novoTsRota = cur.timestampRota
            if (destino == DestinoCaptura.ROTA && alvoRota != null) {
                val qtdStr = if (ehNaoColetavel) "Não Coletável" else total.toString()
                novaRota = atualizarItemRotaColetado(cur.rota, alvoRota, qtdStr)
                if (novaRota != cur.rota) {
                    PersistenciaLocalRegistros.salvarRotaPrecarregada(
                        appContext,
                        novaRota,
                        cur.configEnsaio.codigoEnsaio
                    )
                    novoTsRota = PersistenciaLocalRegistros.obterTimestampRotaPrecarregada(appContext)
                }
            }

            cur.copy(
                registros = novaLista,
                rota = novaRota,
                timestampRota = novoTsRota
            )
        }
    }
}
