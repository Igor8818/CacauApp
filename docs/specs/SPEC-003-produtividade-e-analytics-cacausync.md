# Especificação Técnica: Produtividade Agronômica e Auditoria do CacauSync (SPEC-003)

## 1. Problema e Objetivo
O relatório `GET /api/v1/ensaios/{codigo}/relatorio-clones` calculava `mediaFrutosPorPlantaViva` somando **todos** os frutos (inclusive Vassoura/Podridão) de **todas** as leituras e dividindo pelas plantas vivas **cadastradas**. Isso gerava quatro distorções:
1. Fruto doente inflava a "produtividade" (fruto com vassoura/podridão é perda, não colheita).
2. Plantas ainda não visitadas entravam no divisor como se tivessem 0 frutos.
3. Re-coletas da mesma planta (ex.: "REFAZER FOTO" no Modo Rota do app gera nova leitura com nova data) eram somadas.
4. Não havia conversão para unidade agronômica (kg/ha, @/ha).

Objetivo: separar **carga bruta** de **produtividade líquida (frutos sadios)**, contar cada planta uma única vez, informar a **cobertura** da coleta e estimar kg/ha e @/ha com parâmetros explícitos.

> A estimativa é **indicativa**: converte uma contagem pontual de frutos sadios em amêndoa seca. Não substitui pesagem de safra.

## 2. Contratos e Invariantes

### 2.1. Leitura válida de uma planta
- Entre as leituras da planta (opcionalmente filtradas por `campanha`, comparação case-insensitive) descartam-se as `REJEITADA`.
- A **leitura válida** é a mais recente restante (ordem `importadoEm DESC`). Cada planta contribui com **no máximo uma** leitura aos totais de frutos/classes.
- Se a leitura válida é `coletavel == false`, a planta contribui com 0 e **não** é "avaliada".

### 2.2. Definições (por clone e por ensaio)
- `plantasVivas` = plantas com `situacao == NORMAL`.
- `plantasAvaliadas` = plantas `NORMAL` cuja leitura válida é coletável.
- `coberturaPercentual = plantasAvaliadas / plantasVivas × 100` (0 se `plantasVivas == 0`).
- `taxaSobrevivencia = plantasVivas / totalPlantas × 100`.
- Totais de frutos/sadios/vassoura/podridão/não-avaliável = soma das leituras válidas (`qtdEfetiva`, `qtd*Efetiva`).
- `mediaFrutosPorPlantaViva = totalFrutos / plantasAvaliadas` (0 se nenhuma avaliada). *(nome do campo mantido por compatibilidade; o divisor agora é "viva e avaliada")*.
- `mediaSadiosPorPlantaAvaliada = totalSadios / plantasAvaliadas`.
- `taxaInfeccao = (vassoura + podridão) / totalFrutos × 100`.

### 2.3. Estimativa agronômica
Parâmetros: `indiceFruto` (frutos sadios por kg de amêndoa seca; padrão **22**, faixa `(0, 200]`) e `plantasPorHa` (padrão **1111**, faixa `[1, 10000]`). Fora da faixa → HTTP 422 (`RegraNegocioException`).
- `potencialKgHa = mediaSadiosPorPlantaAvaliada / indiceFruto × plantasPorHa`
- `produtividadeRealKgHa = potencialKgHa × taxaSobrevivencia / 100`
- `produtividadeRealArrobasHa = produtividadeRealKgHa / 15`
- `perdaDoencasKgHa = ((vassoura + podridão) / plantasAvaliadas) / indiceFruto × plantasPorHa × taxaSobrevivencia / 100`
- Todos arredondados a 2 casas (`HALF_UP`).

### 2.4. Ranking
Ordenado por `mediaSadiosPorPlantaAvaliada` DESC (desempenho fenotípico por planta viva), desempate por `totalFrutosSadios` DESC. Sobrevivência e kg/ha reais são colunas próprias (não entram na ordenação).

### 2.5. Contadores gerais (inalterados)
`totalLeiturasRegistradas`, `leiturasPendentesValidacao`, `leiturasValidadas`, `leiturasRejeitadas`, `leiturasComAlertaGps` continuam contando **todas** as leituras do ensaio.

### 2.6. Consistência da validação (`PUT /api/v1/leituras/{id}/validacao`)
Seja `S = sadios + vassoura + podridão + naoAvaliavel` (valores enviados, ou os já gravados quando omitidos).
- `AJUSTADA` com `S > 0` e `S != qtdFrutosValidada` → 422.
- `VALIDADA` com `S > 0` e `S != qtdFrutos` (contagem da IA) → 422, orientando o uso de "Salvar Validação".
- `S == 0` mantém o comportamento legado (todos contados como sadios).

### 2.7. Compatibilidade com o CacauApp (não pode quebrar)
- `POST /auth/login`, `POST /coletas/sync-json`, `POST /leituras/{id}/foto`, `GET /ensaios/{codigo}/rota-csv` mantêm contrato e campos (SPEC-001).
- O JSON gerado por `CacauSyncClient.criarPayloadSyncJson` (chaves `campanha, plantId, clone, parcela, planta, resultado, data, lat, lng, sadios, vassoura, podridao, naoAvaliavel, deteccoes[]`) deve ser aceito por `SyncJsonRequest` sem erro de validação.
- Re-coleta da mesma planta (nova `data`) cria nova leitura; o relatório usa apenas a mais recente (§2.1).
- Campos novos do relatório são **apenas aditivos**; o app não consome o relatório.

## 3. Adendos pós-auditoria (Peixinho)
- §2.1: o desempate de `importadoEm` é por `id DESC` (query explícita em `LeituraColetaRepository`).
- §2.5: o filtro de `campanha` afeta **somente** a produtividade; os contadores gerais sempre usam todas as leituras.
- §2.2: todo percentual é limitado a 100 (classes que somem mais que o total não geram taxa > 100%).
- §2.6: `REJEITADA` e `PENDENTE` **não** alteram classes nem caixas gravadas; só `VALIDADA`/`AJUSTADA` as atualizam.
- §2.3: parâmetro com tipo inválido (ex.: `indiceFruto=abc`) → HTTP 400; fora da faixa → HTTP 422.
- Campanhas são agrupadas sem diferenciar caixa/espaços nas pontas.
- Riscos conhecidos (fora de escopo): mapa limitado a 1000 leituras e sem filtro de campanha; sem `@Version` na validação concorrente; N+1 em `listarEnsaios`; ranking sem cobertura mínima.
