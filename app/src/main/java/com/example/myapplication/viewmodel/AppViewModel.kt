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
import com.example.myapplication.userinterface.util.cloneDoPlantId
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

    // ── Histórico para Desfazer Exclusão / Salvo Rápido (Undo) ───────────────
    private var ultimoRegistroExcluido: Pair<Int, RegistroCacau>? = null
    private var backupAntesDeSalvarLivre: Triple<String, RegistroCacau?, Int>? = null

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

            val (v2, v3) = repository.carregarValoresNumericos()
            _valorNum2.value = v2
            _valorNum3.value = v3

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

    fun salvarRegistro(novo: RegistroCacau, incrementarPlantaManual: Boolean = true) {
        val listaAtual = _registros.value.toMutableList()
        val idx = listaAtual.indexOfFirst { it.plantId == novo.plantId }
        val regAnterior = if (idx >= 0) listaAtual[idx] else null
        val plantaAnterior = _valorNum3.value

        if (incrementarPlantaManual) {
            backupAntesDeSalvarLivre = Triple(novo.plantId, regAnterior, plantaAnterior)
        }

        if (idx >= 0) {
            listaAtual[idx] = novo
        } else {
            listaAtual.add(novo)
        }
        _registros.value = listaAtual

        if (incrementarPlantaManual && _isNum3.value) {
            _valorNum3.value = _valorNum3.value + 1
            salvarEstadoSessao()
        }

        val cloneNome = novo.cloneExplicito.ifBlank { cloneDoPlantId(novo.plantId) }
        if (cloneNome.isNotBlank() && !_historicoClones.value.contains(cloneNome)) {
            val novoHist = _historicoClones.value + cloneNome
            _historicoClones.value = novoHist
            salvarEstadoSessao()
        }

        viewModelScope.launch(Dispatchers.IO) {
            repository.salvarRegistros(listaAtual)
        }
    }

    fun desfazerUltimoSalvoLivre(): Boolean {
        val backup = backupAntesDeSalvarLivre ?: return false
        val (plantIdSalvo, regAnterior, plantaAnterior) = backup
        val listaAtual = _registros.value.toMutableList()
        val idx = listaAtual.indexOfFirst { it.plantId == plantIdSalvo }
        if (regAnterior != null) {
            if (idx >= 0) listaAtual[idx] = regAnterior else listaAtual.add(regAnterior)
        } else if (idx >= 0) {
            listaAtual.removeAt(idx)
        }
        _registros.value = listaAtual
        if (_isNum3.value) {
            _valorNum3.value = plantaAnterior.coerceAtLeast(1)
            salvarEstadoSessao()
        }
        backupAntesDeSalvarLivre = null
        viewModelScope.launch(Dispatchers.IO) {
            repository.salvarRegistros(listaAtual)
        }
        return true
    }

    fun editarContagemRegistro(plantId: String, novaContagem: String) {
        val listaAtual = _registros.value.toMutableList()
        val idx = listaAtual.indexOfFirst { it.plantId == plantId }
        if (idx >= 0) {
            val reg = listaAtual[idx]
            val inativa = SituacaoPlanta.ehInativa(novaContagem)
            val novoInt = if (inativa) 0 else (novaContagem.toIntOrNull() ?: reg.contagemIa)
            val novoSitPla = if (inativa) novaContagem else "Normal"
            val atualizado = reg.copy(
                diagnostico = novaContagem,
                contagemFinal = novoInt,
                sitPla = novoSitPla
            )
            listaAtual[idx] = atualizado
            _registros.value = listaAtual
            viewModelScope.launch(Dispatchers.IO) {
                repository.salvarRegistros(listaAtual)
            }
        }
    }

    fun atualizarRegistroCompleto(atualizado: RegistroCacau) {
        val listaAtual = _registros.value.toMutableList()
        val idx = listaAtual.indexOfFirst { it.plantId == atualizado.plantId }
        if (idx >= 0) {
            listaAtual[idx] = atualizado
            _registros.value = listaAtual
            viewModelScope.launch(Dispatchers.IO) {
                repository.salvarRegistros(listaAtual)
            }
        }
        // Sincroniza também na rota caso faça parte dela
        val rotaAtual = _rota.value.toMutableList()
        val idxRota = rotaAtual.indexOfFirst { it.idEfetivo == atualizado.plantId }
        if (idxRota >= 0) {
            val p = rotaAtual[idxRota]
            rotaAtual[idxRota] = p.copy(
                coletado = true,
                qtdFrutos = atualizado.diagnostico,
                observacao = atualizado.observacao,
                sitPla = atualizado.sitPla,
                nfPequenos = atualizado.nfPequenos,
                nfMedios = atualizado.nfMedios,
                nfAdultos = atualizado.nfAdultos,
                nfMaduros = atualizado.nfMaduros,
                nfVassoura = atualizado.nfVassoura,
                nfPodridao = atualizado.nfPodridao,
                nfOutrasPerdas = atualizado.nfOutrasPerdas,
                nvTotal = atualizado.nvTotal,
                corFruto = atualizado.corFruto,
                notaVirus = atualizado.notaVirus
            )
            _rota.value = rotaAtual
            viewModelScope.launch(Dispatchers.IO) {
                repository.salvarRota(rotaAtual)
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
        // Se a planilha carregada já traz o nome do Ensaio (ex: TC-1801), sincroniza caso necessário
        val ensaioPlanilha = novaLista.firstOrNull { it.ensaio.isNotBlank() }?.ensaio
        if (!ensaioPlanilha.isNullOrBlank() && _ensaioNome.value.isBlank()) {
            _ensaioNome.value = ensaioPlanilha
            salvarEstadoSessao()
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.salvarRota(novaLista)
        }
    }

    fun marcarPontoRota(
        pontoOriginal: RotaColeta,
        coletado: Boolean,
        qtdFrutos: String,
        observacao: String = pontoOriginal.observacao,
        sitPla: String = if (SituacaoPlanta.ehInativa(qtdFrutos)) qtdFrutos else "Normal",
        nfPequenos: Int = pontoOriginal.nfPequenos,
        nfMedios: Int = pontoOriginal.nfMedios,
        nfAdultos: Int = pontoOriginal.nfAdultos,
        nfMaduros: Int = pontoOriginal.nfMaduros,
        nfVassoura: Int = pontoOriginal.nfVassoura,
        nfPodridao: Int = pontoOriginal.nfPodridao,
        nfOutrasPerdas: Int = pontoOriginal.nfOutrasPerdas,
        nvTotal: Int = pontoOriginal.nvTotal,
        corFruto: String = pontoOriginal.corFruto,
        notaVirus: Int = pontoOriginal.notaVirus
    ) {
        val listaAtual = _rota.value.toMutableList()
        val idx = listaAtual.indexOfFirst {
            (it.plantIdOriginal.isNotBlank() && it.plantIdOriginal == pontoOriginal.plantIdOriginal) ||
                (it.clone == pontoOriginal.clone && it.parcela == pontoOriginal.parcela && it.planta == pontoOriginal.planta && it.bloco == pontoOriginal.bloco)
        }
        if (idx >= 0) {
            listaAtual[idx] = pontoOriginal.copy(
                coletado = coletado,
                qtdFrutos = qtdFrutos,
                observacao = observacao,
                sitPla = sitPla,
                nfPequenos = nfPequenos,
                nfMedios = nfMedios,
                nfAdultos = nfAdultos,
                nfMaduros = nfMaduros,
                nfVassoura = nfVassoura,
                nfPodridao = nfPodridao,
                nfOutrasPerdas = nfOutrasPerdas,
                nvTotal = nvTotal,
                corFruto = corFruto,
                notaVirus = notaVirus
            )
            _rota.value = listaAtual
            viewModelScope.launch(Dispatchers.IO) {
                repository.salvarRota(listaAtual)
            }
        }
    }

    fun restaurarPontoRota(pontoAnterior: RotaColeta, removerRegistroSeInedito: Boolean) {
        val listaRota = _rota.value.toMutableList()
        val idx = listaRota.indexOfFirst { it.idEfetivo == pontoAnterior.idEfetivo }
        if (idx >= 0) {
            listaRota[idx] = pontoAnterior
            _rota.value = listaRota
            viewModelScope.launch(Dispatchers.IO) {
                repository.salvarRota(listaRota)
            }
        }
        if (removerRegistroSeInedito) {
            val regs = _registros.value.toMutableList()
            regs.removeAll { it.plantId == pontoAnterior.idEfetivo }
            _registros.value = regs
            viewModelScope.launch(Dispatchers.IO) {
                repository.salvarRegistros(regs)
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

    fun iniciarNovoEnsaio(nome: String, operador: String = _operador.value, fazenda: String = _fazenda.value) {
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
                fazenda = _fazenda.value,
                valorNum2 = _valorNum2.value,
                valorNum3 = _valorNum3.value
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
    fun setValorNum2(v: Int) {
        _valorNum2.value = v.coerceAtLeast(1)
        salvarEstadoSessao()
    }
    fun setValorNum3(v: Int) {
        _valorNum3.value = v.coerceAtLeast(1)
        salvarEstadoSessao()
    }
    fun resetarPlanta() {
        _valorNum3.value = 1
        salvarEstadoSessao()
    }
    fun resetarParcela() {
        _valorNum2.value = 1
        salvarEstadoSessao()
    }
    fun setValorTxt2(v: String) { _valorTxt2.value = v }
    fun setValorTxt3(v: String) { _valorTxt3.value = v }
    fun setConfigConcluida(v: Boolean) { _configConcluida.value = v; salvarEstadoSessao() }
    fun setEnsaioNome(v: String) { _ensaioNome.value = v }
    fun setOperador(v: String) { _operador.value = v }
    fun setFazenda(v: String) { _fazenda.value = v }

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
