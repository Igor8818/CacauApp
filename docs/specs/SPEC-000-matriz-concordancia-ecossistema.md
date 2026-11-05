# Especificação Mestre: Matriz de Concordância Cross-Stack CacauApp ↔ CacauSync (SPEC-000)

## 1. Objetivo
Estabelecer o **Contrato Mestre de Concordância Semântica, Sintática e Geométrica** entre o aplicativo mobile Android (`app/`, Kotlin) e o servidor central (`CacauSync/`, Java 21 + Spring Boot + Web UI). Qualquer alteração em um dos lados **DEVE** preservar 100% das invariantes desta matriz.

---

## 2. Matriz de Concordância de Dados (Mobile ↔ API REST ↔ Banco ↔ Web)

| Conceito | Mobile (`app/` Kotlin) | Backend (`CacauSync/` Java) | Invariante de Concordância |
| :--- | :--- | :--- | :--- |
| **Código do Ensaio** | `ensaioManual` / `codigoEnsaio` | `SyncJsonRequest.codigoEnsaio` / `Ensaio.codigo` | Trim + Uppercase (ex: `"TC-1801"`). Não vazio. |
| **Identificador da Planta** | `RegistroCacau.plantId` (`"${clone}_P${parcela}_Pl${planta}"`) | `ItemSync.plantId` / `Planta.plantCode` | Único por planta no ensaio (`VARCHAR(120)`). Usado como chave no mapa de retorno `leituras[{plantId, leituraId}]`. |
| **Chave JSON do Clone** | `put("clone", cloneCode)` (`CacauSyncClient.kt`) | `@JsonProperty("clone") String cloneCodigo` (`SyncJsonRequest.java`) | No JSON trafega como `"clone"`; se omitido/vazio, extrai prefixo antes de `"_"` em `plantId`. |
| **Resultado / Contagem** | `put("resultado", if (naoColetavel) "Não Coletável" else totalFrutos.toString())` | `@Pattern(regexp = "^(\\d+\|Não Coletável)$") String resultado` | **Estritamente** dígitos inteiros (`"0"`, `"12"`) ou a literal exata `"Não Coletável"` (com acento). |
| **Data/Hora da Coleta** | `SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.ROOT)` | `ItemSync.data` / `LeituraColeta.dataColetaStr` (`VARCHAR(40)`) | Formato canônico com ano e segundos (`19` caracteres) para evitar colisão em `chaveIdempotencia`; aceita `"dd/MM HH:mm"` legado. |
| **Chave de Idempotência** | Implícita por `(codigoEnsaio, plantId, campanha, data)` | `ColetaSyncService.gerarChaveIdempotencia` (`VARCHAR(200)`) | Formato `"ENSAIO\|PLANT_ID\|CAMPANHA\|DATA"`. Reenvio do mesmo pacote não duplica leitura e retorna o `leituraId` existente para upload de foto. |
| **Classes Fitossanitárias (Rótulos)** | `EstadoFruto.rotulo`: `"Sadio"`, `"Vassoura de Bruxa"`, `"Podridão"`, `"Não Avaliável"` | `DeteccaoDto.classe` Regex: `^(Sadio\|Vassoura de Bruxa\|Podridão\|Não Avaliável\|cacau\|outro)$` | Os 4 rótulos de `EstadoFruto` em `TelaRevisaoCaptura.kt` **DEVEM** casar caractere por caractere com o regex de `DeteccaoDto.java` e com `CORES_CLASSES` do `index.html`. |
| **Concordância de Soma de Classes** | `RegistroCacau.isConcordante()`: `totalFrutos == sadios + vassoura + podridao + naoAvaliavel` | `LeituraService.validarConsistenciaDasClasses`: `soma == qtdFrutosValidada` (em `AJUSTADA`) | Em planta coletável, a soma das 4 classes é igual ao total de frutos e a `caixas.size`. Em planta `"Não Coletável"`, todas as contagens são `0` e `caixas` é vazia. |
| **Geometria das Caixas YOLO** | `x = normLeft`, `y = normTop`, `largura = normRight - normLeft`, `altura = normBottom - normTop` | `DeteccaoDto`: `x, y, largura, altura` em `[0.0, 1.0]`, `x + largura <= 1.0001`, `y + altura <= 1.0001` | Coordenadas normalizadas relativas à foto **já rotacionada conforme EXIF**. |

---

## 3. Concordância de Planilhas CSV (3 Formatos Suportados)

1. **Rota Pré-Campo (`GET /api/v1/ensaios/{codigo}/rota-csv` ➔ `TelaModoFieldBook`):**
   - Cabeçalho exato: `plant_id,Ensaio,Bloco,Parcela,Planta,Clone,Ordem,SitPla2023c,SitPla2023L`
   - O parser mobile (`MainActivity.kt` `lerApenasCabecalho` + `processarCSVComIndices`) reconhece automaticamente as colunas `Clone`, `Parcela` e `Planta`.
2. **Exportação Customizada do App (`exportarCSVCEPEC_Personalizado` ➔ `CsvColetaParser.mapearFormatoPersonalizado`):**
   - 15 colunas: `Campanha,ID_Gerado,Clone,Parcela,Planta,Resultado,Data,Lat,Lng,Total_Frutos,Sadios,Vassoura,Podridao,Nao_Avaliavel,Deteccoes_YOLO`
   - Escapamento RFC 4180 (`csvField`) obrigatório para o JSON de `Deteccoes_YOLO` na coluna 14.
3. **Exportação Analítica Pós-Auditoria (`GET /api/v1/ensaios/{codigo}/analise-csv`):**
   - 21 colunas: `Ensaio,Campanha,Plant_ID,Clone,Parcela,Planta,Qtd_IA,Qtd_Validada,Qtd_Efetiva,Sadios,Vassoura,Podridao,Nao_Avaliavel,Status_Validacao,Validado_Por,Validado_Em,Observacao,Data_Coleta,Latitude,Longitude,Tem_Foto`

---

## 4. Gates Automatizados de Verificação (Obrigatórios)

Qualquer subagente que modifique contratos de dados ou cálculos deve validar os dois lados:
- **Gate Mobile (Kotlin/JUnit 4):** `.\gradlew testDebugUnitTest` (especialmente `DomainConcordanceIntegrationTest`, `CsvParsingUnitTest`, `LetterboxAndGeometryUnitTest`, `PersistenceConcurrencyUnitTest`).
- **Gate Backend (Java 21/JUnit 5):** `mvn test` em `CacauSync/` (especialmente `MobileContractTest`, `ColetaSyncServiceTest`, `CsvColetaParserTest`, `EnsaioServiceTest`, `LeituraServiceTest`, `SecurityRulesIntegrationTest`).
