# 🍫 CacauApp — Monitoramento de Frutos com Visão Computacional

Aplicativo Android nativo desenvolvido para o **CEPEC (Centro de Pesquisa do Cacau) / CEPLAC** para contagem automatizada e monitoramento biométrico de frutos de cacaueiro (*Theobroma cacao L.*). O sistema opera **100% offline (Edge AI)** utilizando modelos de visão computacional **YOLO (TensorFlow Lite)**, renderização de caixas delimitadoras (*bounding boxes*), registro georreferenciado (GPS) e exportação em CSV para análise agronômica.

---

## 📲 Funcionalidades

- **Coleta Livre**: Câmera ao vivo com detecção contínua, contagem estabilizada por mediana, atalhos numéricos ampliados (80dp) e botão de lanterna/flash.
- **Revisão Científica de Fotos**: Ao fotografar, a imagem em resolução total é analisada pela IA e aberta em tela de auditoria. O pesquisador pode **tocar em caixas para removê-las** (falso positivo) ou **tocar em áreas livres para adicionar frutos** (falso negativo), registrando a contagem da IA e a contagem final auditada separadamente.
- **Modo Rota (FieldBook)**: Importação de planilhas CSV de planejamento agrícola com navegação sequencial planta a planta, avanço automático e salto para a próxima planta pendente.
- **Galeria com Edição & Desfazer**: Visualização com fotos, totalizadores de frutos e plantas, edição direta de contagens e exclusão com opção de **Desfazer (Snackbar)**.
- **Exportação CSV & Compartilhamento**: Gera arquivos no formato `Coleta_{Campanha}_{yyyy-MM-dd_HH-mm}.csv` compatíveis com Microsoft Excel pt-BR (delimitador `;` e UTF-8 com BOM), com proteção contra injeção de fórmulas e botão de **Compartilhar** via `FileProvider` (WhatsApp, E-mail, Google Drive).
- **Diagnóstico do Sistema**: Monitoramento em tempo real da latência da IA (ms), resolução de entrada, acelerador em uso (GPU Delegate / CPU), estado do sinal GPS e dados de hardware.
- **Persistência Atômica & Autosave**: Todos os registros, configurações e rotas são salvos atomicamente no armazenamento local do dispositivo, sobrevivendo a reinicializações, troca de aplicativos e rotação de tela.

---

## ⚠️ Política de Privacidade — Modelo de IA Privado (.tflite)

> **O modelo de visão computacional treinado pelo CEPEC é proprietário e privado. Por política de propriedade intelectual, ele NÃO é versionado neste repositório.**
> Arquivos `*.tflite`, a pasta `modeloia/`, `modelopronto/` e `app/src/main/assets/*.tflite` estão estritamente bloqueados no `.gitignore`.

Para utilizar a detecção no ambiente de desenvolvimento ou no campo:

**Opção A — Desenvolvimento Local:**
1. Coloque o arquivo em `modeloia/detector.tflite` ou `app/src/main/assets/detector.tflite`.
2. Compile o app pelo Android Studio ou `./gradlew assembleDebug`. O arquivo será empacotado no APK localmente sem ser rastreado pelo Git.

**Opção B — Importação em Tempo de Execução no Dispositivo:**
1. No menu inferior do aplicativo, acesse **"Modelo IA"**.
2. Toque em **"SELECIONAR ARQUIVO .tflite"** e escolha o arquivo no armazenamento do celular. O aplicativo valida a assinatura FlatBuffers `TFL3`, calcula o hash SHA-256 e copia o modelo para o armazenamento interno protegido.

> **Sem modelo, o app não trava:** a interface exibe um card orientativo direcionando o usuário para a seleção do modelo, mantendo todas as outras funcionalidades ativas.

---

## 🛠️ Stack Tecnológica

| Camada | Tecnologia |
|---|---|
| Linguagem | Kotlin 2.0 |
| Interface (UI) | Jetpack Compose + Material 3 |
| Arquitetura | MVVM com `AppViewModel` + `AppRepository` (gravação atômica) |
| Câmera | CameraX 1.5.3 (Pinch-to-zoom, Touch-to-focus, Flash) |
| Inferência de IA | TensorFlow Lite 2.16.1 + GPU Delegate + Letterbox Preprocessing |
| Georreferenciamento | Google Play Services Location (alta precisão e altitude) |
| Carregamento de Imagens | Coil Compose |
| Compartilhamento | AndroidX FileProvider |
| Min SDK / Compile SDK | Android 7.0 (API 24) / API 36 |

---

## 🗂️ Estrutura do Projeto

```
app/src/main/java/com/example/myapplication/
├── MainActivity.kt                            # Ponto de entrada, Scaffold e navegação
├── data/
│   ├── AppRepository.kt                       # Gravação atômica em disco (JSON / Prefs)
│   ├── ModelConfig.kt                         # Configuração e catálogo de modelos TFLite
│   ├── RegistroCacau.kt                       # Data class completa de registro de campo
│   └── RotaColeta.kt                          # Data class imutável de ponto da rota
├── viewmodel/
│   └── AppViewModel.kt                        # Estado reativo e ciclo de vida do GPS
└── userinterface/
    ├── TelaConfiguracaoModelo.kt              # Importação e ajuste de threshold/labels
    ├── components/
    │   └── CommonComponents.kt               # Cartões de métricas, avisos e dropdowns
    ├── screens/
    │   ├── CapturaLivreScreen.kt              # Coleta livre com câmera ao vivo
    │   ├── CheckInScreen.kt                   # Inicialização de campanha
    │   ├── DiagnosticoScreen.kt               # Métricas de desempenho, hardware e IA
    │   ├── GaleriaScreen.kt                   # Histórico, edição, exclusão e exportação
    │   ├── ModoRotaScreen.kt                  # Navegação sequencial de campo (FieldBook)
    │   └── RevisaoCapturaScreen.kt            # Auditoria de foto com caixas interativas
    └── util/
        ├── CameraUtils.kt                     # Integração CameraX e rotação de frame
        ├── FileUtils.kt                       # Parser RFC 4180, CSV pt-BR e FileProvider
        └── ObjectDetectorHelper.kt            # Letterbox, GPU delegate e inferência YOLO
```

---

## 🧪 Testes Unitários Automatizados

O projeto conta com suite de testes unitários em `app/src/test/`:
- **`FileUtilsUnitTest`**: Validação de regex Latin Extended, parser RFC 4180 com aspas duplas, autodetecção de delimitadores (`;`, `,`, `\t`) e sanitização de CSV formula injection (`=`, `+`, `-`, `@`).
- **`DetectorLogicUnitTest`**: Validação da função sigmoid, cálculo de IoU, supressão de não-máximos (NMS) e mapeamento reverso de coordenadas Letterbox.

Para rodar os testes:
```bash
./gradlew testDebugUnitTest
```

---

## 📄 Licença

Desenvolvido para uso do **CEPEC / CEPLAC**. Todos os direitos reservados.
