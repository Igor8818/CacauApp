---
name: peixinho-auditor
description: >-
  Executa auditoria cega (Contexto Zero) invocando subagentes para validar se o código cumpre as
  Regras de Negócio reais (docs/REGRAS_DE_NEGOCIO.md) e a concordância técnica Mobile/Backend (docs/specs/).
  Use sempre que o usuário pedir para validar o projeto, rodar subagentes, checar regras de negócio ou evitar erros de concordância.
---

# Skill: Validação de Regras de Negócio & Concordância por Subagentes

Esta skill despacha subagentes `research` com **contexto zero** (sem viés da conversa) para auditar o projeto contra **[`docs/REGRAS_DE_NEGOCIO.md`](../../docs/REGRAS_DE_NEGOCIO.md)** e os contratos em `docs/specs/`.

## Como Executar a Validação Completa (3 Subagentes em Paralelo)

Dispare simultaneamente 3 chamadas a `invoke_subagent` (`TypeName: "research"`):

1. **Subagente 1 — `Auditor Regras de Negócio Mobile (CacauApp)`:**
   - **Documento base:** `docs/REGRAS_DE_NEGOCIO.md` (Seção 2: `RN-MOB-01` a `RN-MOB-05`) e `docs/specs/SPEC-002-yolo-letterbox-camera.md`.
   - **Foco:** Verificar no código do `app/` se o modelo YOLO enxerga os frutos sem distorção geométrica, se a revisão pós-captura permite classificar doenças e adicionar/remover caixas, se o gerenciamento de memória (`Bitmap.recycle`, `inSampleSize`, buffers reutilizáveis, threads) garante bom desempenho em vários dispositivos Android sem *OutOfMemoryError*, e se os modos Livre/Rota e persistência offline estão íntegros.
2. **Subagente 2 — `Auditor Regras de Negócio Backend & Web (CacauSync)`:**
   - **Documento base:** `docs/REGRAS_DE_NEGOCIO.md` (Seção 3: `RN-BACK-01` a `RN-BACK-05`) e `docs/specs/SPEC-003-produtividade-e-analytics-cacausync.md`.
   - **Foco:** Verificar no código de `CacauSync/` se a hierarquia agronômica, a auditoria do pesquisador (Confirmar, Ajustar, Rejeitar, Desfazer, reativar planta Não Coletável), a deduplicação por planta, o ranking por frutos sadios e a estimativa em kg/ha e @/ha cumprem todas as regras de negócio do CEPEC.
3. **Subagente 3 — `Auditor de Concordância Cross-Stack (App ↔ Servidor)`:**
   - **Documento base:** `docs/REGRAS_DE_NEGOCIO.md` e `docs/specs/SPEC-000-matriz-concordancia-ecossistema.md`.
   - **Foco:** Confrontar linha a linha o que o Android envia/lê (`CacauSyncClient.kt`, `MainActivity.kt`, `RegistroCacau.kt`) com o que o Backend recebe/exporta (`SyncJsonRequest.java`, `DeteccaoDto.java`, `ColetaSyncService.java`, `CsvColetaParser.java`, `EnsaioService.java`).
4. **Subagente 4 — `Auditor de Detecção YOLO e Campo (Prioridade Máxima)`:**
   - **Skill dedicada:** [`.agents/skills/auditor-deteccao-campo/SKILL.md`](../auditor-deteccao-campo/SKILL.md).
   - **Foco:** Auditar a detecção do `detector.tflite`, o alinhamento exato do quadradinho verde sobre o fruto na foto estática (*Coordinate Snapping* pós-captura + Letterbox `640x640` + EXIF), a seleção por distância ao centro em frutos agrupados, a classificação pós-foto nas 4 classes fitossanitárias e a adição/redimensionamento manual de frutos que o modelo não pegar.
