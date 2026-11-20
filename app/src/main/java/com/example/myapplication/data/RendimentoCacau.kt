package com.example.myapplication.data

import com.example.myapplication.userinterface.CaixaAuditavel
import com.example.myapplication.userinterface.EstadoFruto
import java.util.Locale

/**
 * Parâmetros agronômicos oficiais para estimativa de rendimento de cacau (SPEC-003).
 * Padrão CEPEC/CEPLAC: Índice de Fruto (IF) = 22 frutos sadios por kg de amêndoas secas,
 * densidade = 1111 plantas/ha (espaçamento 3x3m) e 1 arroba (@) = 15 kg.
 */
data class ConfigRendimentoCacau(
    val indiceFruto: Double = INDICE_FRUTO_PADRAO,
    val kgPorArroba: Double = KG_POR_ARROBA,
    val plantasPorHa: Int = PLANTAS_POR_HA_PADRAO,
    val descontarDoencas: Boolean = true
) {
    companion object {
        const val INDICE_FRUTO_PADRAO = 22.0
        const val KG_POR_ARROBA = 15.0
        const val PLANTAS_POR_HA_PADRAO = 1111
    }
}

data class EstimativaPlantaCacau(
    val frutosConsiderados: Int,
    val frutosPerdidosDoenca: Int,
    val kgAmendoasSecas: Double,
    val arrobas: Double,
    val perdaKg: Double,
    val perdaArrobas: Double
)

data class ResumoRendimentoEnsaioCacau(
    val totalRegistros: Int,
    val plantasColetaveis: Int,
    val plantasNaoColetaveis: Int,
    val taxaSobrevivenciaPercentual: Double,
    val totalFrutos: Int,
    val totalSadios: Int,
    val totalVassoura: Int,
    val totalPodridao: Int,
    val totalNaoAvaliavel: Int,
    val frutosPerdidosDoenca: Int,
    val taxaInfeccaoPercentual: Double,
    val mediaFrutosPorPlanta: Double,
    val mediaSadiosPorPlanta: Double,
    val kgAcumulado: Double,
    val arrobasAcumuladas: Double,
    val perdaKgAcumulada: Double,
    val perdaArrobasAcumulada: Double,
    val potencialKgHa: Double,
    val realKgHa: Double,
    val realArrobasHa: Double,
    val perdaKgHa: Double,
    val perdaArrobasHa: Double
)

data class RankingCloneCacau(
    val codigoClone: String,
    val totalRegistros: Int,
    val plantasColetaveis: Int,
    val taxaSobrevivenciaPercentual: Double,
    val totalFrutos: Int,
    val sadios: Int,
    val vassoura: Int,
    val podridao: Int,
    val naoAvaliavel: Int,
    val mediaFrutosPlanta: Double,
    val mediaSadiosPlanta: Double,
    val taxaInfeccaoPercentual: Double,
    val kgAcumulado: Double,
    val arrobasAcumuladas: Double,
    val kgHa: Double,
    val arrobasHa: Double,
    val perdaArrobasHa: Double
)

internal fun isRegistroNaoColetavelCacau(reg: RegistroCacau): Boolean {
    val norm = reg.diagnostico.trim().lowercase(Locale.ROOT)
    return norm == "não coletável" || norm == "nao coletavel" ||
        norm.contains("não coletável") || norm.contains("nao coletavel") ||
        norm.contains("ausente") || norm.contains("morta") || norm.contains("seca")
}

internal fun basePlantIdSemFoto(plantId: String): String =
    plantId.trim().replace(Regex("_Foto\\d+$", RegexOption.IGNORE_CASE), "")

internal fun extrairCloneRegistroCacau(reg: RegistroCacau): String {
    val base = basePlantIdSemFoto(reg.plantId)
    val prefixo = base.substringBefore("_P").substringBefore("_").trim()
    return prefixo.ifBlank { "Sem Clone" }
}

/**
 * Estima rendimento em kg de amêndoas secas e arrobas (@ = 15 kg) para UMA planta ou foto.
 * Por padrão (SPEC-003), desconta frutos com Vassoura de Bruxa e Podridão/Monilíase.
 */
fun calcularEstimativaPlantaCacau(
    caixas: List<CaixaAuditavel>,
    totalFrutos: Int = caixas.size,
    config: ConfigRendimentoCacau = ConfigRendimentoCacau()
): EstimativaPlantaCacau {
    val indice = config.indiceFruto.coerceAtLeast(0.01)
    val kgPorArroba = config.kgPorArroba.coerceAtLeast(0.01)

    val qtdDoentes = if (caixas.isNotEmpty()) {
        caixas.count { it.estado == EstadoFruto.VASSOURA || it.estado == EstadoFruto.PODRIDAO }
    } else {
        0
    }
    val qtdSadios = if (caixas.isNotEmpty()) {
        caixas.count { it.estado == EstadoFruto.SADIO }
    } else {
        totalFrutos.coerceAtLeast(0)
    }

    val considerados = if (config.descontarDoencas) {
        qtdSadios
    } else {
        maxOf(totalFrutos, caixas.size).coerceAtLeast(0)
    }

    val kg = considerados.toDouble() / indice
    val arrobas = kg / kgPorArroba
    val perdaKg = qtdDoentes.toDouble() / indice
    val perdaArrobas = perdaKg / kgPorArroba

    return EstimativaPlantaCacau(
        frutosConsiderados = considerados,
        frutosPerdidosDoenca = qtdDoentes,
        kgAmendoasSecas = kg,
        arrobas = arrobas,
        perdaKg = perdaKg,
        perdaArrobas = perdaArrobas
    )
}

fun calcularEstimativaPorContagensCacau(
    sadios: Int,
    doentes: Int,
    totalFrutos: Int,
    config: ConfigRendimentoCacau = ConfigRendimentoCacau()
): EstimativaPlantaCacau {
    val indice = config.indiceFruto.coerceAtLeast(0.01)
    val kgPorArroba = config.kgPorArroba.coerceAtLeast(0.01)
    val considerados = if (config.descontarDoencas) sadios.coerceAtLeast(0) else totalFrutos.coerceAtLeast(0)
    val perdas = doentes.coerceAtLeast(0)
    val kg = considerados.toDouble() / indice
    val arrobas = kg / kgPorArroba
    val perdaKg = perdas.toDouble() / indice
    val perdaArrobas = perdaKg / kgPorArroba
    return EstimativaPlantaCacau(
        frutosConsiderados = considerados,
        frutosPerdidosDoenca = perdas,
        kgAmendoasSecas = kg,
        arrobas = arrobas,
        perdaKg = perdaKg,
        perdaArrobas = perdaArrobas
    )
}

/**
 * Calcula produtividade acumulada e projeção indicativa em kg/ha e @/ha conforme SPEC-003 §2.3:
 * - Usa apenas os registros únicos mais recentes por plantId caso existam duplicatas locais? Em Campo Livre/Rota já há remoção por plantId antes de salvar.
 * - Média considera plantas coletáveis avaliadas.
 * - Pondera pela taxa de sobrevivência do grupo/ensaio.
 */
fun calcularRendimentoEnsaioCacau(
    registros: List<RegistroCacau>,
    config: ConfigRendimentoCacau = ConfigRendimentoCacau()
): ResumoRendimentoEnsaioCacau {
    // Deduplica multi-fotos (_Foto2, _Foto3...) por planta única (P1-07)
    val gruposPorPlanta = registros.groupBy { reg ->
        basePlantIdSemFoto(reg.plantId).lowercase(Locale.ROOT)
    }

    val plantasConsolidadas = gruposPorPlanta.values.map { fotosPlanta ->
        val coletaveisPlanta = fotosPlanta.filter { !isRegistroNaoColetavelCacau(it) }
        if (coletaveisPlanta.isEmpty()) {
            fotosPlanta.last()
        } else {
            val base = coletaveisPlanta.last()
            base.copy(
                totalFrutos = coletaveisPlanta.sumOf { it.totalFrutos.coerceAtLeast(0) },
                sadios = coletaveisPlanta.sumOf { it.sadios.coerceAtLeast(0) },
                vassoura = coletaveisPlanta.sumOf { it.vassoura.coerceAtLeast(0) },
                podridao = coletaveisPlanta.sumOf { it.podridao.coerceAtLeast(0) },
                naoAvaliavel = coletaveisPlanta.sumOf { it.naoAvaliavel.coerceAtLeast(0) }
            )
        }
    }

    val totalRegs = plantasConsolidadas.size
    val coletaveis = plantasConsolidadas.filter { !isRegistroNaoColetavelCacau(it) }
    val qtdColetaveis = coletaveis.size
    val qtdNaoColetaveis = totalRegs - qtdColetaveis
    val sobrevivencia = if (totalRegs > 0) ((qtdColetaveis * 100.0) / totalRegs).coerceIn(0.0, 100.0) else 100.0

    var totFrutos = 0
    var totSadios = 0
    var totVassoura = 0
    var totPodridao = 0
    var totNaoAval = 0

    for (reg in coletaveis) {
        totFrutos += reg.totalFrutos.coerceAtLeast(0)
        totSadios += reg.sadios.coerceAtLeast(0)
        totVassoura += reg.vassoura.coerceAtLeast(0)
        totPodridao += reg.podridao.coerceAtLeast(0)
        totNaoAval += reg.naoAvaliavel.coerceAtLeast(0)
    }

    val doentes = totVassoura + totPodridao
    val taxaInfeccao = if (totFrutos > 0) ((doentes * 100.0) / totFrutos).coerceIn(0.0, 100.0) else 0.0
    val frutosComerciais = if (config.descontarDoencas) totSadios else totFrutos

    val indice = config.indiceFruto.coerceAtLeast(0.01)
    val kgPorArroba = config.kgPorArroba.coerceAtLeast(0.01)
    val plHa = config.plantasPorHa.coerceAtLeast(0)

    val kgAcum = frutosComerciais.toDouble() / indice
    val arbAcum = kgAcum / kgPorArroba
    val perdaKgAcum = doentes.toDouble() / indice
    val perdaArbAcum = perdaKgAcum / kgPorArroba

    val mediaFrutos = if (qtdColetaveis > 0) totFrutos.toDouble() / qtdColetaveis else 0.0
    val mediaComercial = if (qtdColetaveis > 0) frutosComerciais.toDouble() / qtdColetaveis else 0.0

    val potencialKgHa = if (qtdColetaveis > 0 && plHa > 0) (mediaComercial / indice) * plHa else 0.0
    val realKgHa = potencialKgHa * (sobrevivencia / 100.0)
    val realArbHa = realKgHa / kgPorArroba
    val perdaKgHa = if (qtdColetaveis > 0 && plHa > 0) {
        ((doentes.toDouble() / qtdColetaveis) / indice) * plHa * (sobrevivencia / 100.0)
    } else 0.0
    val perdaArbHa = perdaKgHa / kgPorArroba

    return ResumoRendimentoEnsaioCacau(
        totalRegistros = totalRegs,
        plantasColetaveis = qtdColetaveis,
        plantasNaoColetaveis = qtdNaoColetaveis,
        taxaSobrevivenciaPercentual = sobrevivencia,
        totalFrutos = totFrutos,
        totalSadios = totSadios,
        totalVassoura = totVassoura,
        totalPodridao = totPodridao,
        totalNaoAvaliavel = totNaoAval,
        frutosPerdidosDoenca = doentes,
        taxaInfeccaoPercentual = taxaInfeccao,
        mediaFrutosPorPlanta = mediaFrutos,
        mediaSadiosPorPlanta = if (qtdColetaveis > 0) totSadios.toDouble() / qtdColetaveis else 0.0,
        kgAcumulado = kgAcum,
        arrobasAcumuladas = arbAcum,
        perdaKgAcumulada = perdaKgAcum,
        perdaArrobasAcumulada = perdaArbAcum,
        potencialKgHa = potencialKgHa,
        realKgHa = realKgHa,
        realArrobasHa = realArbHa,
        perdaKgHa = perdaKgHa,
        perdaArrobasHa = perdaArbHa
    )
}

fun calcularRankingClonesCacau(
    registros: List<RegistroCacau>,
    config: ConfigRendimentoCacau = ConfigRendimentoCacau()
): List<RankingCloneCacau> {
    if (registros.isEmpty()) return emptyList()
    return registros
        .groupBy { extrairCloneRegistroCacau(it) }
        .map { (clone, grupo) ->
            val r = calcularRendimentoEnsaioCacau(grupo, config)
            RankingCloneCacau(
                codigoClone = clone,
                totalRegistros = r.totalRegistros,
                plantasColetaveis = r.plantasColetaveis,
                taxaSobrevivenciaPercentual = r.taxaSobrevivenciaPercentual,
                totalFrutos = r.totalFrutos,
                sadios = r.totalSadios,
                vassoura = r.totalVassoura,
                podridao = r.totalPodridao,
                naoAvaliavel = r.totalNaoAvaliavel,
                mediaFrutosPlanta = r.mediaFrutosPorPlanta,
                mediaSadiosPlanta = r.mediaSadiosPorPlanta,
                taxaInfeccaoPercentual = r.taxaInfeccaoPercentual,
                kgAcumulado = r.kgAcumulado,
                arrobasAcumuladas = r.arrobasAcumuladas,
                kgHa = r.realKgHa,
                arrobasHa = r.realArrobasHa,
                perdaArrobasHa = r.perdaArrobasHa
            )
        }
        .sortedWith(
            compareByDescending<RankingCloneCacau> { it.mediaSadiosPlanta }
                .thenByDescending { it.sadios }
                .thenByDescending { it.arrobasHa }
                .thenBy { it.codigoClone.lowercase(Locale.ROOT) }
        )
}

fun formatarEstimativaCurta(valor: Double, simbolo: String): String {
    val limpo = if (valor.isNaN() || valor.isInfinite()) 0.0 else valor
    val fmt = when {
        kotlin.math.abs(limpo) < 1.0 && limpo != 0.0 -> String.format(Locale.US, "%.3f", limpo)
        kotlin.math.abs(limpo) < 10.0 -> String.format(Locale.US, "%.2f", limpo)
        else -> String.format(Locale.US, "%.1f", limpo)
    }.replace('.', ',')
    val sym = simbolo.trim()
    return if (sym.isNotEmpty()) "$fmt $sym" else fmt
}

fun gerarResumoTextoCacau(
    ensaio: String,
    registros: List<RegistroCacau>,
    config: ConfigRendimentoCacau = ConfigRendimentoCacau()
): String = buildString {
    val res = calcularRendimentoEnsaioCacau(registros, config)
    val nomeEnsaio = ensaio.ifBlank { "TC-1801" }
    appendLine("*CacauApp CEPEC • Resumo de Campo*")
    appendLine("Ensaio: $nomeEnsaio")
    appendLine("Plantas: ${res.totalRegistros} (${res.plantasColetaveis} produtivas • Sobrev. ${res.taxaSobrevivenciaPercentual.toInt()}%)")
    appendLine("Frutos: ${res.totalFrutos} total • ${res.totalSadios} sadios")
    if (res.frutosPerdidosDoenca > 0) {
        appendLine("Fitossanidade: ${res.totalVassoura} Vassoura • ${res.totalPodridao} Podridão (${String.format(Locale.US, "%.1f", res.taxaInfeccaoPercentual).replace('.', ',')}%)")
    }
    appendLine("Produção acumulada (IF ${config.indiceFruto.toInt()}): ${formatarEstimativaCurta(res.arrobasAcumuladas, "@")} (${formatarEstimativaCurta(res.kgAcumulado, "kg")})")
    if (config.plantasPorHa > 0 && res.plantasColetaveis > 0) {
        appendLine("Projeção (${config.plantasPorHa} pl/ha): ${formatarEstimativaCurta(res.realArrobasHa, "@/ha")} (${formatarEstimativaCurta(res.realKgHa, "kg/ha")})")
        if (res.perdaArrobasHa > 0.0) {
            appendLine("Perda estimada p/ doenças: ${formatarEstimativaCurta(res.perdaArrobasHa, "@/ha")}")
        }
    }

    val ranking = calcularRankingClonesCacau(registros, config)
    if (ranking.isNotEmpty()) {
        appendLine()
        appendLine("*Top Clones (por frutos sadios):*")
        ranking.take(5).forEachIndexed { idx, item ->
            val mediaStr = String.format(Locale.US, "%.1f", item.mediaSadiosPlanta).replace('.', ',')
            val arbHa = if (config.plantasPorHa > 0) " • ${formatarEstimativaCurta(item.arrobasHa, "@/ha")}" else ""
            appendLine("${idx + 1}. ${item.codigoClone}: $mediaStr sadios/pl$arbHa (Infec. ${item.taxaInfeccaoPercentual.toInt()}%)")
        }
    }
}.trim()
