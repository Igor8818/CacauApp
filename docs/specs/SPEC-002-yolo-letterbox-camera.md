# Especificação Técnica: Letterbox Geométrico YOLO & Estabilização Universal de Câmera (SPEC-002)

## 1. Problema e Objetivo
No pipeline atual de Visão Computacional (`ObjectDetectorHelper.kt`), o frame da câmera (proporção `4:3`, ex: `1200x1600` ou `960x1280`) é redimensionado diretamente para um quadrado `640x640` via `Bitmap.createScaledBitmap(image, 640, 640, true)`.
- **Consequência:** O fruto de cacau sofre **achatamento geométrico de 25%** em um dos eixos, reduzindo a pontuação de confiança (*score*) do modelo YOLOv8 (que foi treinado com *Letterbox* mantendo proporção natural e bordas neutras `RGB(114, 114, 114)`).
- **Objetivo:** Implementar pré-processamento **Letterbox Isomórfico** (mantendo proporção exata `scale = min(inputW / imgW, inputH / imgH)` com preenchimento cinza `0xFF727272`) e desprojeção matemática inversa exata das caixas para as coordenadas reais da foto/frame original em qualquer dispositivo Android.

## 2. Invariantes Matemáticas do Letterbox

Dado um `Bitmap` de entrada com dimensões `(imgW, imgH)` e um tensor de entrada do modelo `(inputW, inputH)` (tipicamente `640 x 640`):

1. **Escala Isomórfica e Padding:**
   - $\text{scale} = \min\left(\frac{\text{inputW}}{\text{imgW}}, \frac{\text{inputH}}{\text{imgH}}\right)$
   - $\text{newW} = \text{round}(\text{imgW} \times \text{scale})$
   - $\text{newH} = \text{round}(\text{imgH} \times \text{scale})$
   - $\text{padX} = \frac{\text{inputW} - \text{newW}}{2.0}$
   - $\text{padY} = \frac{\text{inputH} - \text{newH}}{2.0}$
   - O canvas `inputW x inputH` deve ser preenchido com a cor padrão do Ultralytics YOLO: `Color.rgb(114, 114, 114)` (`0xFF727272`) e o frame redimensionado `(newW, newH)` desenhado em `(padX, padY)`.

2. **Desprojeção Inversa das Caixas (Tensor ➔ Imagem Original):**
   - Para cada caixa detectada no espaço normalizado `[0.0, 1.0]` do tensor `(normCx, normCy, normW, normH)`:
     - Coordenadas em pixels no tensor `640x640`:
       - $x_1 = (\text{normCx} - \text{normW}/2) \times \text{inputW}$
       - $y_1 = (\text{normCy} - \text{normH}/2) \times \text{inputH}$
       - $x_2 = (\text{normCx} + \text{normW}/2) \times \text{inputW}$
       - $y_2 = (\text{normCy} + \text{normH}/2) \times \text{inputH}$
     - Remoção do Padding e Escala para o `Bitmap` original `(imgW, imgH)`:
       - $\text{left} = \text{coerceIn}\left(\frac{x_1 - \text{padX}}{\text{scale}}, 0, \text{imgW}\right)$
       - $\text{top} = \text{coerceIn}\left(\frac{y_1 - \text{padY}}{\text{scale}}, 0, \text{imgH}\right)$
       - $\text{right} = \text{coerceIn}\left(\frac{x_2 - \text{padX}}{\text{scale}}, 0, \text{imgW}\right)$
       - $\text{bottom} = \text{coerceIn}\left(\frac{y_2 - \text{padY}}{\text{scale}}, 0, \text{imgH}\right)$

3. **Gerenciamento de Memória (Reuso de Buffers):**
   - Reutilizar `Bitmap` do canvas Letterbox (`640x640`), `IntArray` de pixels e `ByteBuffer` direto quando `inputWidth` e `inputHeight` permanecerem constantes, zerando alocações por frame no Garbage Collector.
