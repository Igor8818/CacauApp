# 📱 CacauApp — Visão Computacional Embarcada & Auditoria Fenotípica Offline

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-SDK%2024%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![TensorFlow Lite](https://img.shields.io/badge/TensorFlow%20Lite-YOLOv8-FF6F00?style=for-the-badge&logo=tensorflow&logoColor=white)](https://www.tensorflow.org/lite)
[![CEPEC / CEPLAC](https://img.shields.io/badge/CEPEC%20%2F%20CEPLAC-Pesquisa%20Agron%C3%B4mica-2E7D32?style=for-the-badge)](https://www.gov.br/agricultura/pt-br/assuntos/ceplac)

Aplicativo Android nativo de **Edge AI** (inteligência artificial on-device) desenvolvido para o **Centro de Pesquisas do Cacau (CEPEC / CEPLAC)** em Ilhéus/Itabuna - BA. 

O sistema opera **100% offline** em quadras experimentais de melhoramento genético, realizando a detecção, classificação fitossanitária e contagem de frutos de cacau diretamente pela câmera do smartphone com auxílio de modelos de deep learning (**YOLOv8**).

> 🔗 **Ecossistema Completo:** Este aplicativo se integra diretamente com a [**CacauSync API**](https://github.com/Igor8818/CacauSync), back-end em **Java 21 + Spring Boot 3 + PostgreSQL**, para ingestão em lote de coletas de campo, upload de fotos auditadas e ranqueamento de produtividade clonal.

---

## 🎯 Problema do Mundo Real & Solução de Campo

Pesquisadores e técnicos agrícolas do CEPEC avaliam rotineiramente milhares de plantas em ensaios como o **TC-1801**. No campo, não há conectividade com a internet, e a anotação manual em pranchetas de papel resulta em:
1. Erros de contagem e transcrição;
2. Falta de padronização visual no diagnóstico fitossanitário (*Sadio*, *Vassoura de Bruxa*, *Podridão Parda*);
3. Desalinhamento entre registros de mortalidade e produtividade real.

O **CacauApp** resolve este gargalo ao unificar **visão computacional local**, **tela de revisão auditável pelo pesquisador** e **georreferenciamento instantâneo via GPS**.

---

## ✨ Funcionalidades Principais

* **⚡ Inferência de Edge AI em Tempo Real (YOLOv8 Float32):**
  * Detecção on-device a 30+ FPS via **TensorFlow Lite Interpreter** com 4 threads de CPU e suporte a aceleração gráfica GPU Delegate.
  * Mapeamento matemático em aspecto fixo 4:3 (`Preview` e `ImageAnalysis`), garantindo caixas delimitadoras (*bounding boxes*) precisas sem distorções de lente em qualquer smartphone (testado em Xiaomi Redmi Note 13 Pro 5G e Samsung Galaxy A50).
* **🔍 Tela de Revisão Pós-Captura Interativa (Human-in-the-Loop):**
  * O pesquisador toca nas caixas verdes detectadas pela IA para classificar cada fruto individualmente:
    * 🟢 **Sadio**
    * 🟠 **Vassoura de Bruxa** (*Moniliophthora perniciosa*)
    * 🔴 **Podridão Parda** (*Phytophthora palmivora*)
    * ⚪ **Não Avaliável**
  * Toque em áreas vazias da foto para adicionar frutos não detectados manualmente com 100% de confiança;
  * Botão de lixeira para remoção instantânea de falsos positivos;
  * Resumo biométrico consolidado em tempo real por planta.
* **🗺️ Modo Rota Guiada (FieldBook):**
  * Importação de planilhas de planejamento experimental em CSV;
  * Navegação sequencial ordenada por Parcela, Bloco e Planta com status visual de coleta.
* **🌐 Sincronização Direta com o Laboratório (CacauSync API):**
  * Diálogo com autenticação institucional JWT Bearer (`POST /api/v1/auth/login`);
  * Envio em lote das coletas com caixas YOLO normalizadas (0.0 a 1.0) via `POST /api/v1/coletas/sync-json`;
  * Upload assíncrono de fotos com compressão inteligente (JPEG 85%, máx 1600px), correção automática de orientação EXIF e decodificação econômica de memória (`inSampleSize`) para evitar *OutOfMemoryError*.
* **📊 Exportação Offline em Planilha (RFC 4180):**
  * Geração instantânea de arquivo CSV padronizado no diretório `Download/CEPEC` com codificação UTF-8 e escape seguro de caracteres especiais.

---

## 🛠️ Stack Tecnológica

| Camada | Tecnologias Utilizadas |
|---|---|
| **Linguagem & Plataforma** | Kotlin 2.0, Android SDK (minSdk 24, targetSdk 36) |
| **Interface de Usuário** | Jetpack Compose, Material Design 3, Icons Extended, Coil Compose |
| **Câmera & Visão Computacional** | CameraX 1.5.3, TensorFlow Lite 2.16.1, YOLOv8 (640x640 Float32) |
| **Comunicação de Rede** | OkHttp 4.12.0, JSON nativo Android, Network Security Config |
| **Geolocalização & Hardware** | Google Play Services Location (FusedLocationProviderClient), ExifInterface |
| **Arquitetura** | MVVM, StateFlow, Coroutines assíncronas, SnapshotStateList |

---

## 🏗️ Como Compilar e Instalar

### Pré-requisitos
* Android Studio (versão Ladybug / Koala ou superior) com JDK 21 (JetBrains Runtime JBR);
* Aparelho físico Android com modo de depuração USB ativo.

### 1. Clonar o repositório
```bash
git clone https://github.com/Igor8818/CacauApp.git
cd CacauApp
```

### 2. Compilar o APK de Debug via Gradle
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew assembleDebug
```

### 3. Instalar no aparelho via ADB
```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 👨‍💻 Autor

**Igor Hagge**  
Desenvolvedor de Software | Android (Kotlin) & Soluções com Inteligência Artificial  
* **GitHub:** [@Igor8818](https://github.com/Igor8818)  
* **LinkedIn:** [linkedin.com/in/igorhagge](https://www.linkedin.com/in/igorhagge/)  
* **E-mail:** [igor.senna2@hotmail.com](mailto:igor.senna2@hotmail.com)
