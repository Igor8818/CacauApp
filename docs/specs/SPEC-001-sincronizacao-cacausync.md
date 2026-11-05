# Especificação Técnica: Sincronização CacauApp ➔ CacauSync (SPEC-001)

## 1. Visão Geral e Objetivo
Garantir a sincronização determinística, resiliente e idempotente das coletas de campo (contagem de frutos, diagnóstico fitossanitário, caixas delimitadoras do YOLO auditadas e fotos) do aplicativo Android (`CacauApp`) para o servidor Spring Boot (`CacauSync`).

## 2. Contratos e Invariantes (Regras Imutáveis)

### 2.1. Autenticação
- Endpoint: `POST /api/v1/auth/login`
- Payload: `{"email": "<string>", "senha": "<string>"}`
- Resposta esperada: HTTP 200 com `{"token": "<JWT>"}`.
- **Invariante:** Credenciais nunca devem ser hardcoded no código-fonte; devem residir em `SharedPreferences` local.

### 2.2. Sincronização de Lote JSON (`POST /api/v1/coletas/sync-json`)
- Header obrigatório: `Authorization: Bearer <token>`
- Payload (`SyncJsonRequest`):
  - `codigoEnsaio`: String em maiúsculas e sem espaços nas bordas (ex: `"TC-1801"`).
  - `leituras[]`: Lista de objetos contendo:
    - `campanha`, `plantId`, `clone`, `parcela`, `planta`, `data` (formato canônico `"dd/MM/yyyy HH:mm:ss"` com ano e segundos para evitar colisão de `chaveIdempotencia` em re-coletas no mesmo minuto; aceita `"dd/MM HH:mm"` por retrocompatibilidade), `lat`, `lng`.
    - `resultado`: **DEVE** ser estritamente um número inteiro em formato String (ex: `"12"`) ou a literal exata `"Não Coletável"`. Qualquer outro texto deve ser rejeitado.
    - `deteccoes[]`: Lista de caixas auditadas com:
      - `classe`: `"Sadio"`, `"Vassoura de Bruxa"`, `"Podridão"` ou `"Não Avaliável"`.
      - `confianca`: Float entre `0.0` e `1.0`.
      - `x`, `y`, `largura`, `altura`: Coordenadas normalizadas estritamente no intervalo `[0.0, 1.0]`.
- Resposta (`ResultadoImportacaoCsvResponse`):
  - Deve retornar contadores (`totalLinhasProcessadas`, `leiturasNovasInseridas`, `leiturasDuplicadasIgnoradas`) e a lista `leituras: [{plantId, leituraId}]` — **incluindo** o `leituraId` de leituras duplicadas ignoradas via `chaveIdempotencia`, para permitir reenvio de fotos pendentes.

### 2.3. Upload de Fotos (`POST /api/v1/leituras/{id}/foto`)
- Multipart field: `foto` (`image/jpeg`).
- **Invariantes no Cliente Android:**
  1. Corrigir rotação física via metadados EXIF (`ORIENTATION_ROTATE_90`, `180`, `270`) antes de enviar, garantindo que a foto salva no servidor case geometricamente com as caixas normalizadas do YOLO.
  2. Downsampling seguro: calcular `inSampleSize` verificando se largura **OU** altura ultrapassam `1600px` e usar `Bitmap.Config.RGB_565` na decodificação para evitar `OutOfMemoryError` em aparelhos de entrada.
  3. Reciclar (`recycle()`) todos os `Bitmap` intermediários na memória nativa.
  4. Leituras com diagnóstico `"Não Coletável"` ou sem URI de foto válida devem ser marcadas como `SINCRONIZADO` após o `sync-json` sem falhar no passo de foto.

### 2.4. Thread Safety e UI (Android Jetpack Compose)
- Toda chamada de rede (`OkHttp`) deve rodar fora da Main Thread (`Dispatchers.IO`).
- Toda mutação de estado observável pelo Compose (`SnapshotStateList`, `mutableStateOf`) disparada por callbacks de progresso deve ser despachada para a Main Thread (`Looper.getMainLooper()`).
