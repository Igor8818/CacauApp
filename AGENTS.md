# Diretrizes de Engenharia e Orquestração de Agentes (SDD 2.0)

Este repositório utiliza **Spec-Driven Development 2.0 (SDD)** combinado com o padrão **Elefante & Peixinho Dourado** e isolamento de subagentes nativo do **Google Antigravity**.

## 1. Arquitetura do Ecossistema
- **`app/` (CacauApp Mobile):** Aplicativo Android nativo em Kotlin + Jetpack Compose, CameraX e inferência Edge AI offline com TensorFlow Lite (YOLOv8).
- **`CacauSync/` (CacauSync API):** Back-end corporativo em Java 21 + Spring Boot 3.3, PostgreSQL, Flyway, autenticação JWT e suíte JUnit 5 / Mockito.
- **`docs/specs/` (Fonte Única da Verdade):** Especificações formais (`SPEC-XXX-*.md`) contendo contratos, invariantes e critérios de aceite.

## 2. Protocolo Elefante & Peixinho (Orquestração Multi-Agente)
1. **Congelar a Spec:** Nenhuma funcionalidade complexa deve ser implementada sem antes criar ou atualizar seu contrato em `docs/specs/`.
2. **O Elefante (Agente Construtor / Coordenador):** Implementa o código guiado pela especificação. Para tarefas paralelas entre Mobile (`app/`) e Backend (`CacauSync/`), pode despachar subagentes isolados com `Workspace: "share"` (Git Worktree nativo).
3. **O Peixinho Dourado (Subagente Auditor de Contexto Zero):**
   - Antes de dar uma entrega como finalizada, o Agente Construtor deve invocar um subagente com **contexto zerado** (sem acesso ao histórico do chat).
   - O Peixinho lê **apenas** o arquivo `docs/specs/SPEC-XXX.md` e os arquivos de código modificados, buscando desvios de contrato, condições de corrida, vazamentos de memória ou casos de borda não cobertos.
