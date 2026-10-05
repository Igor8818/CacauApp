# 🍫 CacauApp — Monitoramento de Frutos com Visão Computacional

Aplicativo Android desenvolvido para o **CEPEC (Centro de Pesquisa do Cacau)** que utiliza modelos de visão computacional **YOLO (TFLite)** para contagem automática de frutos de cacaueiro, eliminando o processo manual de preenchimento de planilhas de campo.

---

## 📲 Funcionalidades

- **Coleta Livre**: Abre a câmera com detecção em tempo real, conta os frutos detectados pelo modelo de IA e salva foto + dados identificados (clone, parcela, planta, GPS)
- **Modo Rota (FieldBook)**: Importa uma planilha CSV de planejamento de campo e percorre cada planta sequencialmente, com a IA contando automaticamente
- **Galeria**: Visualiza todos os registros coletados na sessão com miniaturas das fotos
- **Exportação CSV**: Gera `Coleta_{Campanha}_{yyyy-MM-dd_HH-mm}.csv` compatível com Excel (UTF-8 com BOM, acentos preservados) com campanha, clone, parcela, planta, contagem IA, GPS e data/hora
- **Configuração de Modelo**: Permite selecionar o modelo embutido ou importar um arquivo `.tflite` externo com controle de threshold e rótulos

---

## ⚠️ Aviso Importante — Modelo de IA Privado (.tflite)

> **O modelo de visão computacional treinado pelo CEPEC é proprietário e privado. Por política de privacidade/propriedade intelectual, ele NÃO está incluído neste repositório.**
> Arquivos `*.tflite`, a pasta `modelopronto/` e `app/src/main/assets/*.tflite` estão no `.gitignore` e **nunca** devem ser commitados.

Para usar a detecção, você precisa fornecer um modelo `.tflite` por uma das opções:

**Opção A — Importar pelo app em tempo de execução (recomendado):**
1. Compile e instale o APK normalmente (sem modelo nos assets)
2. Ao iniciar a coleta sem modelo, o app abre automaticamente **"Configurar Modelo IA"** (também acessível pela aba **"Modelo IA"**)
3. Toque em **"Selecionar arquivo .tflite"** e escolha seu arquivo — ele é copiado para o armazenamento interno do app

**Opção B — Adicionar o modelo manualmente nos assets (build local):**
1. Coloque seu arquivo em `app/src/main/assets/detector.tflite`
2. Compile o projeto normalmente

**Sem modelo, o app não trava:** a câmera de detecção fica desabilitada e um aviso com o botão **"Configurar Modelo IA"** é exibido nas telas de Coleta Livre e Modo Rota.

O app é compatível com qualquer modelo **YOLOv8/v11 exportado para TFLite** (formato `[1, 4+C, anchors]` ou `[1, anchors, 4+C]`).

---

## 🛠️ Stack Tecnológica

| Camada | Tecnologia |
|---|---|
| Linguagem | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Câmera | CameraX 1.5.3 |
| Inferência IA | TensorFlow Lite 2.16.1 (Interpreter direto) |
| Localização | Google Play Services Location |
| Imagens | Coil Compose |
| Min SDK | Android 7.0 (API 24) |

---

## 🗂️ Estrutura de Pastas

```
app/src/main/java/com/example/myapplication/
├── MainActivity.kt                    # Composable principal, telas, galeria, CSV
├── data/
│   ├── ModelConfig.kt                 # Config do modelo (threshold, labels, fonte)
│   ├── RegistroCacau.kt               # Dados de cada planta coletada
│   └── RotaColeta.kt                  # Dados de cada ponto da rota
├── userinterface/
│   ├── TelaConfiguracaoModelo.kt      # Tela de seleção e ajuste do modelo
│   └── util/
│       ├── CameraUtils.kt             # Preview + análise em tempo real + captura
│       ├── FileUtils.kt               # Leitura de CSV e exportação de planilhas
│       └── ObjectDetectorHelper.kt    # Wrapper TFLite — inferência YOLO raw
```

---

## 🚀 Como rodar

1. Clone o repositório
2. Abra no Android Studio (Flamingo ou superior)
3. Adicione o modelo (ver **Nota de Build** acima)
4. Compile e instale no dispositivo (`Run > Run app`)

---

## 📄 Licença

Desenvolvido para uso interno do **CEPEC / CEPLAC**. Todos os direitos reservados.
