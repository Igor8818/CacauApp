# Diretrizes de Negócio, Engenharia e Orquestração de Subagentes

Este repositório contém o ecossistema **CacauApp** (Android/Edge AI) e **CacauSync** (Backend Spring Boot & Painel Web) do **CEPEC / CEPLAC**.

## 1. Documentos que Guiam os Agentes (Ordem de Prioridade)
1. **[`docs/REGRAS_DE_NEGOCIO.md`](docs/REGRAS_DE_NEGOCIO.md) (FONTE PRIMÁRIA — Regras de Negócio Reais):**
   Define **o que o produto deve fazer** no campo (`RN-MOB-01` a `RN-MOB-05`) e no laboratório (`RN-BACK-01` a `RN-BACK-05`): papel do YOLO em enxergar os frutos, revisão humana de doenças, desempenho fluido em vários aparelhos Android, modos de coleta, sobrevivência clonal, produtividade líquida baseada em frutos sadios e auditoria científica.
2. **Contratos Técnicos (`docs/specs/`):**
   Detalham como o código cumpre as regras de negócio sem quebrar a comunicação entre o app e o servidor:
   - `SPEC-000-matriz-concordancia-ecossistema.md`: Contrato de integração Mobile ↔ API ↔ CSV ↔ Web.
   - `SPEC-001-sincronizacao-cacausync.md`: Sincronização, idempotência, EXIF e memória no Android.
   - `SPEC-002-yolo-letterbox-camera.md`: Pipeline do YOLO, Letterbox sem distorção e ergonomia da câmera.
   - `SPEC-003-produtividade-e-analytics-cacausync.md`: Fórmulas de produtividade líquida (sadios, kg/ha, @/ha) e validação.

## 2. Como Executar Subagentes para Validar Tudo (Sem Viés)
Para validar se o projeto cumpre tanto as **Regras de Negócio (`docs/REGRAS_DE_NEGOCIO.md`)** quanto a **Concordância Técnica (`docs/specs/`)**, invoque 3 subagentes `research` de contexto zero em paralelo:

1. **Subagente 1 — Auditor de Negócio & Desempenho Mobile (`RN-MOB-01` a `RN-MOB-05`):**
   - Verifica se o `CacauApp` (`ObjectDetectorHelper.kt`, `CameraUtils.kt`, `TelaRevisaoCaptura.kt`, `MainActivity.kt`, `PersistenciaLocalRegistros.kt`, `CacauSyncClient.kt`) enxerga os frutos via YOLO sem distorcer caixas, se permite classificar/adicionar/excluir frutos na revisão, se é leve em memória/threads para rodar bem em aparelhos Android variados (ex.: Galaxy A50 a Redmi Note 13) e se salva tudo offline com segurança.
2. **Subagente 2 — Auditor de Negócio & Agronomia Backend/Web (`RN-BACK-01` a `RN-BACK-05`):**
   - Verifica se o `CacauSync` (`EnsaioService.java`, `LeituraService.java`, `ColetaSyncService.java`, `LeituraColeta.java`, `index.html`) respeita a palavra final do pesquisador na auditoria, se não duplica plantas re-coletadas, se calcula a produtividade comercial apenas sobre **frutos sadios** (tratando vassoura/podridão como perda) e se a interface web reflete fielmente os dados.
3. **Subagente 3 — Auditor de Concordância Mobile ↔ Backend (`SPEC-000`):**
   - Verifica se tudo o que o app envia ou importa (JSON `sync-json`, upload de fotos EXIF, 3 formatos de CSV, rótulos de doenças e datas) casa 100% com o que o servidor espera receber e exportar.
4. **Subagente 4 (Prioridade Máxima em Campo) — Auditor de Detecção YOLO, Alinhamento do Quadradinho Verde & Revisão Pós-Foto (`.agents/skills/auditor-deteccao-campo/SKILL.md`):**
   - Focado exclusivamente na operação real em campo: garante que o `detector.tflite` identifique os cacaus no preview e na foto estática, que o quadradinho verde faça *snap* geométrico exato sobre a foto capturada (eliminando o desalinhamento de movimento entre preview e obturador), que o toque selecione o fruto mais próximo em cachos densos para classificar (`Sadio`, `Vassoura`, `Podridão`, `Não Avaliável`) e que qualquer fruto não detectado pelo modelo possa ser marcado e redimensionado manualmente com um toque.
