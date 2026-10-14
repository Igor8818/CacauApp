package com.example.myapplication.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.*
import com.example.myapplication.userinterface.util.assetModeloExiste
import com.google.android.gms.location.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel central da aplicação.
 * Mantém o estado reativo da sessão, sobrevive a mudanças de configuração (rotação de tela)
 * e aciona o AppRepository para persistir imediatamente cada coleta realizada no campo.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application)
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(application)
    private var locationCallback: LocationCallback? = null

    // ── Estados de Dados Reativos ─────────────────────────────────────────────

    private val _registros = MutableStateFlow<List<RegistroCacau>>(emptyList())
    val registros: StateFlow<List<RegistroCacau>> = _registros.asStateFlow()

    private val _rota = MutableStateFlow<List<RotaColeta>>(emptyList())
    val rota: StateFlow<List<RotaColeta>> = _rota.asStateFlow()

    private val _modelConfig = MutableStateFlow(
        ModelConfig(
            modeloSelecionado = MODELOS_EMBUTIDOS.firstOrNull { assetModeloExiste(application, it.nomeArquivo) },
            labelsFiltro = listOf("cacau", "fruto"),
            threshold = 0.55f,
            maxResultados = 10
        )
    )
    val modelConfig: StateFlow<ModelConfig> = _modelConfig.asStateFlow()

    private val _ensaioNome = MutableStateFlow("")
    val ensaioNome: StateFlow<String> = _ensaioNome.asStateFlow()

    private val _configConcluida = MutableStateFlow(false)
    val configConcluida: StateFlow<Boolean> = _configConcluida.asStateFlow()

    /** Indica que os dados persistidos terminaram de ser lidos do disco (usado pela splash). */
    private val _dadosCarregados = MutableStateFlow(false)
    val dadosCarregados: StateFlow<Boolean> = _dadosCarregados.asStateFlow()

    private val _historicoClones = MutableStateFlow<List<String>>(emptyList())
    val historicoClones: StateFlow<List<String>> = _historicoClones.asStateFlow()

    private val _label1 = MutableStateFlow("Clone")
    val label1: StateFlow<String> = _label1.asStateFlow()

    private val _label2 = MutableStateFlow("Parcela")
    val label2: StateFlow<String> = _label2.asStateFlow()

    private val _label3 = MutableStateFlow("Planta")
    val label3: StateFlow<String> = _label3.asStateFlow()

    private val _isNum2 = MutableStateFlow(true)
    val isNum2: StateFlow<Boolean> = _isNum2.asStateFlow()

    private val _isNum3 = MutableStateFlow(true)
    val isNum3: StateFlow<Boolean> = _isNum3.asStateFlow()

    private val _valor1 = MutableStateFlow("")
    val valor1: StateFlow<String> = _valor1.asStateFlow()

    private val _valorNum2 = MutableStateFlow(1)
    val valorNum2: StateFlow<Int> = _valorNum2.asStateFlow()

    private val _valorNum3 = MutableStateFlow(1)
    val valorNum3: StateFlow<Int> = _valorNum3.asStateFlow()

    private val _valorTxt2 = MutableStateFlow("")
    val valorTxt2: StateFlow<String> = _valorTxt2.asStateFlow()

    private val _valorTxt3 = MutableStateFlow("")
    val valorTxt3: StateFlow<String> = _valorTxt3.asStateFlow()

    private val _operador = MutableStateFlow("")
    val operador: StateFlow<String> = _operador.asStateFlow()

    private val _fazenda = MutableStateFlow("")
    val fazenda: StateFlow<String> = _fazenda.asStateFlow()

    // ── Localização GPS & Precisão ───────────────────────────────────────────

    private val _latAtual = MutableStateFlow(0.0)
    val latAtual: StateFlow<Double> = _latAtual.asStateFlow()

    private val _lngAtual = MutableStateFlow(0.0)
    val lngAtual: StateFlow<Double> = _lngAtual.asStateFlow()

    private val _gpsPrecisaoM = MutableStateFlow(0f)
    val gpsPrecisaoM: StateFlow<Float> = _gpsPrecisaoM.asStateFlow()

    private val _altitude = MutableStateFlow(0.0)
    val altitude: StateFlow<Double> = _altitude.asStateFlow()

    private val _temFixGps = MutableStateFlow(false)
    val temFixGps: StateFlow<Boolean> = _temFixGps.asStateFlow()

    // ── Histórico para Desfazer Exclusão (Undo) ──────────────────────────────
    private var ultimoRegistroExcluido: Pair<Int, RegistroCacau>? = null

    init {
        carregarDadosPersistidos()
    }

    private fun carregarDadosPersistidos() {
        viewModelScope.launch(Dispatchers.IO) {
            _registros.value = repository.carregarRegistros()
            _rota.value = repository.carregarRota()
            _ensaioNome.value = repository.carregarEnsaioNome()
            _configConcluida.value = repository.carregarConfigConcluida()
            _historicoClones.value = repository.carregarHistoricoClones()
            _operador.value = repository.carregarOperador()
            _fazenda.value = repository.carregarFazenda()

            val (l1, l2, l3) = repository.carregarLabels()
            _label1.value = l1
            _label2.value = l2
            _label3.value = l3

            val (n2, n3) = repository.carregarTiposNumericos()
            _isNum2.value = n2
            _isNum3.value = n3

            // Carrega modelo salvo com verificação de arquivo existente
            val salvo = repository.carregarModelConfig()
            if (salvo.modeloSelecionado != null && !assetModeloExiste(getApplication(), salvo.modeloSelecionado.nomeArquivo)) {
                _modelConfig.value = salvo.copy(modeloSelecionado = null)
            } else {
                _modelConfig.value = salvo
            }
            _dadosCarregados.value = true
        }
    }

    // ── Operações de Registros ────────────────────────────────────────────────

    fun salvarRegistro(novo: RegistroCacau) {
        val listaAtual = _registros.value.toMutableList()
        // Substitui se já existir mesmo plantId na sessão (evita duplicidade inconsistente)
        val idx = listaAtual.indexOfFirst { it.plantId == novo.plantId }
        if (idx >= 0) {
            listaAtual[idx] = novo
        } else {
            listaAtual.add(novo)
        }
        _registros.value = listaAtual

        // Incrementa automaticamente o contador de planta se numérico
        if (_isNum3.value) {
            _valorNum3.value = _valorNum3.value + 1
        }

        // Adiciona clone ao histórico se inédito
        if (novo.plantId.isNotBlank()) {
            val cloneNome = novo.plantId.split("_").firstOrNull() ?: ""
            if (cloneNome.isNotBlank() && !_historicoClones.value.contains(cloneNome)) {
                val novoHist = _historicoClones.value + cloneNome
                _historicoClones.value = novoHist
                salvarEstadoSessao()
            }
        }

        // Autosave atômico em background
        viewModelScope.launch(Dispatchers.IO) {
            repository.salvarRegistros(listaAtual)
        }
    }

    fun editarContagemRegistro(plantId: String, novaContagem: String) {
        val listaAtual = _registros.value.toMutableList()
        val idx = listaAtual.indexOfFirst { it.plantId == plantId }
        if (idx >= 0) {
            val reg = listaAtual[idx]
            val novoInt = novaContagem.toIntOrNull() ?: reg.contagemIa
            listaAtual[idx] = reg.copy(diagnostico = novaContagem, contagemFinal = novoInt)
            _registros.value = listaAtual
            viewModelScope.launch(Dispatchers.IO) {
                repository.salvarRegistros(listaAtual)
            }
        }
    }

    fun excluirRegistro(registro: RegistroCacau): Boolean {
        val listaAtual = _registros.value.toMutableList()
        val idx = listaAtual.indexOfFirst { it.plantId == registro.plantId }
        if (idx >= 0) {
            ultimoRegistroExcluido = Pair(idx, listaAtual.removeAt(idx))
            _registros.value = listaAtual
            viewModelScope.launch(Dispatchers.IO) {
                repository.salvarRegistros(listaAtual)
            }
            return true
        }
        return false
    }

    fun desfazerExclusao(): Boolean {
        val excluido = ultimoRegistroExcluido ?: return false
        val listaAtual = _registros.value.toMutableList()
        val pos = excluido.first.coerceIn(0, listaAtual.size)
        listaAtual.add(pos, excluido.second)
        _registros.value = listaAtual
        ultimoRegistroExcluido = null
        viewModelScope.launch(Dispatchers.IO) {
            repository.salvarRegistros(listaAtual)
        }
        return true
    }

    fun existePlantId(plantId: String): Boolean {
        return _registros.value.any { it.plantId == plantId }
    }

    // ── Operações de Rota (FieldBook) ─────────────────────────────────────────

    fun carregarRota(novaLista: List<RotaColeta>) {
        _rota.value = novaLista
        viewModelScope.launch(Dispatchers.IO) {
            repository.salvarRota(novaLista)
        }
    }

    fun marcarPontoRota(
        pontoOriginal: RotaColeta,
        coletado: Boolean,
        qtdFrutos: String,
        observacao: String = pontoOriginal.observacao
    ) {
        val listaAtual = _rota.value.toMutableList()
        val idx = listaAtual.indexOfFirst {
            it.clone == pontoOriginal.clone && it.parcela == pontoOriginal.parcela && it.planta == pontoOriginal.planta
        }
        if (idx >= 0) {
            listaAtual[idx] = pontoOriginal.copy(coletado = coletado, qtdFrutos = qtdFrutos, observacao = observacao)
            _rota.value = listaAtual
            viewModelScope.launch(Dispatchers.IO) {
                repository.salvarRota(listaAtual)
            }
        }
    }

    fun limparRota() {
        _rota.value = emptyList()
        viewModelScope.launch(Dispatchers.IO) {
            repository.limparRota()
        }
    }

    // ── Configuração de Modelo IA ─────────────────────────────────────────────

    fun atualizarModelConfig(config: ModelConfig) {
        _modelConfig.value = config
        viewModelScope.launch(Dispatchers.IO) {
            repository.salvarModelConfig(config)
        }
    }

    // ── Gerenciamento de Sessão / Campanha ─────────────────────────────────────

    fun iniciarNovoEnsaio(nome: String, operador: String = "", fazenda: String = "") {
        _ensaioNome.value = nome
        _operador.value = operador
        _fazenda.value = fazenda
        _configConcluida.value = true
        _valorNum2.value = 1
        _valorNum3.value = 1
        _valorTxt2.value = ""
        _valorTxt3.value = ""
        salvarEstadoSessao()
    }

    fun limparRegistrosCampanha() {
        _registros.value = emptyList()
        viewModelScope.launch(Dispatchers.IO) {
            repository.limparRegistros()
        }
    }

    fun salvarEstadoSessao() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.salvarEstadoSessao(
                ensaioNome = _ensaioNome.value,
                configConcluida = _configConcluida.value,
                historicoClones = _historicoClones.value,
                label1 = _label1.value,
                label2 = _label2.value,
                label3 = _label3.value,
                isNum2 = _isNum2.value,
                isNum3 = _isNum3.value,
                operador = _operador.value,
                fazenda = _fazenda.value
            )
        }
    }

    fun atualizarLabels(l1: String, l2: String, l3: String, n2: Boolean, n3: Boolean) {
        _label1.value = l1
        _label2.value = l2
        _label3.value = l3
        _isNum2.value = n2
        _isNum3.value = n3
        salvarEstadoSessao()
    }

    fun setValor1(v: String) { _valor1.value = v }
    fun setValorNum2(v: Int) { _valorNum2.value = v }
    fun setValorNum3(v: Int) { _valorNum3.value = v }
    fun setValorTxt2(v: String) { _valorTxt2.value = v }
    fun setValorTxt3(v: String) { _valorTxt3.value = v }
    fun setConfigConcluida(v: Boolean) { _configConcluida.value = v; salvarEstadoSessao() }
    fun setEnsaioNome(v: String) { _ensaioNome.value = v }

    // ── Gerenciamento do Ciclo de Vida do GPS ─────────────────────────────────

    @SuppressLint("MissingPermission")
    fun iniciarGps() {
        if (locationCallback != null) return
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
            .setMinUpdateDistanceMeters(1f)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    atualizarLocalizacao(loc)
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
            // Tenta obter última localização imediatamente
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) atualizarLocalizacao(loc)
            }
        } catch (e: Exception) {
            Log.e("AppViewModel", "Erro ao iniciar GPS: ${e.message}")
        }
    }

    private fun atualizarLocalizacao(loc: Location) {
        _latAtual.value = loc.latitude
        _lngAtual.value = loc.longitude
        _gpsPrecisaoM.value = loc.accuracy
        _altitude.value = loc.altitude
        _temFixGps.value = loc.latitude != 0.0 && loc.longitude != 0.0
    }

    fun pararGps() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
            locationCallback = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        pararGps()
    }
}
