---
name: auditor-deteccao-campo
description: >-
  Subagente especialista de contexto zero para auditar a prioridade máxima do CacauApp:
  detecção de frutos de cacau pelo modelo YOLO (detector.tflite), alinhamento geométrico exato
  dos quadradinhos verdes sobre a foto estática, classificação fitossanitária pós-foto
  (Sadio, Vassoura de Bruxa, Podridão, Não Avaliável) e marcação manual de frutos que o modelo não pegar.
---

# Skill: Subagente Auditor de Detecção YOLO, Alinhamento do Quadradinho Verde & Operação de Campo

Esta skill define o subagente revisor que deve ser invocado (via `invoke_subagent` com `TypeName: "research"`) sempre que o pipeline de câmera, inferência TFLite (`ObjectDetectorHelper.kt`) ou a tela de revisão pós-foto (`TelaRevisaoCaptura.kt`, `MainActivity.kt`, `TelaConfiguracaoModelo.kt`) forem modificados.

## Prioridade Máxima de Negócio (Visão de Campo)
No campo cacaueiro (sombra sob cabruca, almofadas florais densas, mão do técnico em movimento ao clicar no obturador):
1. **Os frutos de cacau precisam ser identificados pelo modelo (`detector.tflite`)** tanto no preview ao vivo quanto na foto estática capturada ou importada da galeria.
2. **O quadradinho verde (`#43A047` para Sadio) deve ficar geometricamente cravado em cima do fruto na foto**, sem deslocamento causado pelo delay entre o último frame do preview e o disparo real do obturador JPEG, e sem distorção de aspect ratio (Letterbox `640x640` + rotação EXIF completa).
3. **A classificação pós-foto deve ser imediata e precisa**: tocar em um quadradinho seleciona o fruto exato (mesmo quando há frutos agrupados no tronco) e permite classificá-lo como **Sadio**, **Vassoura de Bruxa**, **Podridão** ou **Não Avaliável**, além de redimensionar ou excluir falsos positivos sem que eles reapareçam.
4. **O que o modelo não pegar deve poder ser marcado com 1 toque**: tocar em qualquer fruto não detectado cria imediatamente um quadradinho manual alinhado ao ponto tocado e já abre a barra de classificação fitossanitária.

---

## Como Invocar o Subagente Revisor

Use a ferramenta `invoke_subagent` com:
- `TypeName`: `"research"`
- `Role`: `"Auditor de Detecção YOLO e Campo"`
- `Model`: `"inherit"`
- `Prompt`: Copie o prompt de auditoria cega abaixo.

### Prompt Padrão para `invoke_subagent`

```text
Você é o Auditor Especialista em Detecção YOLO, Geometria de Caixas e Operação de Campo do CacauApp (Contexto Zero).
Sua missão é auditar rigorosamente o código-fonte do CacauApp pensando no técnico agrícola em campo e na prioridade máxima do projeto: identificar os frutos de cacau, alinhar perfeitamente o quadradinho verde sobre cada fruto na foto e permitir classificar ou adicionar manualmente frutos que o modelo não pegar.

Inspecione obrigatoriamente os arquivos:
1. `app/src/main/java/com/example/myapplication/userinterface/util/ObjectDetectorHelper.kt`
2. `app/src/main/java/com/example/myapplication/userinterface/util/CameraUtils.kt`
3. `app/src/main/java/com/example/myapplication/userinterface/TelaRevisaoCaptura.kt`
4. `app/src/main/java/com/example/myapplication/userinterface/TelaConfiguracaoModelo.kt`
5. `app/src/main/java/com/example/myapplication/MainActivity.kt`
6. `app/src/test/java/com/example/myapplication/YoloDetectionAndScaleUnitTest.kt`
7. `app/src/test/java/com/example/myapplication/LetterboxAndGeometryUnitTest.kt`

Verifique item por item e emita veredicto APROVADO ou REPROVADO (com arquivo e linha exata):

[CHECK 1 — DETECÇÃO YOLO E LETTERBOX SEM DISTORÇÃO]
- O `ObjectDetectorHelper.kt` aplica Letterbox isotrópico (`min(640/w, 640/h)`) com padding cinza `RGB(114,114,114)` e desfaz o offset (`padX`, `padY`) ao projetar as caixas de volta para o espaço da imagem original?
- O `detectarSincrono(bitmap)` funciona de forma isolada (`synchronized(lock)`) sem descartar a foto estática mesmo se o preview da câmera ainda estiver encerrando um frame?
- O NMS agnóstico de classe (`aplicarNMS`) evita caixas duplicadas sobre o mesmo fruto?

[CHECK 2 — ALINHAMENTO DO QUADRADINHO VERDE PÓS-CAPTURA ("COORDINATE SNAPPING")]
- Na `TelaRevisaoCaptura.kt`, quando `LaunchedEffect(fotoUri)` executa `helper.detectarSincrono(bmp)` sobre a foto estática recém-capturada:
  a) Se uma detecção da foto estática tiver sobreposição (`iouNorm > 0.25f`) com uma caixa automática vinda do preview (`!c.manual`), o código ATUALIZA (faz snap) das coordenadas (`normLeft`, `normTop`, `normRight`, `normBottom`) para a posição exata da foto estática (preservando o `estado` já classificado pelo usuário), em vez de descartar a caixa estática e manter a caixa deslocada do preview?
  b) Detecções novas encontradas na foto estática (que não saíram no preview) são adicionadas automaticamente?
  c) Caixas excluídas manualmente pelo usuário (`caixasExcluidasManualmente`) nunca ressuscitam após a re-inferência?

[CHECK 3 — ROTAÇÃO EXIF E PROPORÇÃO DO VIEWPORT]
- Tanto `imgAspect` quanto `carregarBitmapFoto` em `TelaRevisaoCaptura.kt` tratam todas as 4 orientações EXIF que trocam largura/altura (`ORIENTATION_ROTATE_90`, `ORIENTATION_ROTATE_270`, `ORIENTATION_TRANSPOSE`, `ORIENTATION_TRANSVERSE`)?
- O viewport de desenho do `Canvas` e o receptor de `pointerInput (detectTapGestures)` usam exatamente o mesmo `Modifier.aspectRatio(aspectoSeguro)`?

[CHECK 4 — CLASSIFICAÇÃO PÓS-FOTO E SELEÇÃO DE FRUTOS AGRUPADOS]
- Quando há dois ou mais frutos próximos (almofada floral no tronco) e o técnico toca em um quadradinho, a seleção escolhe a caixa cujo CENTRO `(cx, cy)` está mais próximo do toque `(normTapX, normTapY)` dentro da área/margem de toque, em vez de pegar cegamente `indexOfLast`?
- Tocar no fruto selecionado permite alternar imediatamente entre as 4 classes (`SADIO` verde `#43A047`, `VASSOURA_BRUXA` laranja `#FF6D00`, `PODRIDAO_PARDA` vermelho `#D50000`, `NAO_AVALIAVEL` cinza `#78909C`)?

[CHECK 5 — MARCAÇÃO MANUAL DO QUE O MODELO NÃO PEGAR + AJUSTE DE TAMANHO]
- Quando o modelo não identifica um fruto (ex.: cacau sombreado ou parcialmente encoberto), tocar fora das caixas existentes cria imediatamente um quadradinho manual (`manual = true`) centrado no ponto exato do toque (clampeado em `0f..1f`), seleciona essa nova caixa e já exibe os botões de classificação fitossanitária?
- O card inferior da caixa selecionada permite ajustar o tamanho do quadradinho (`-` / `+`) para adequar a frutos pequenos (bilros/frutos jovens) ou frutos grandes em primeiro plano?

[CHECK 6 — OPERAÇÃO EM CAMPO (GALERIA, RE-EDIÇÃO E PERSISTÊNCIA)]
- O usuário consegue testar/coletar tanto tirando foto na câmera quanto escolhendo uma foto da galeria do celular (`ActivityResultContracts.GetContent`)?
- O usuário consegue reabrir um registro salvo na aba Galeria para revisar/corrigir os quadradinhos antes de sincronizar?
```
