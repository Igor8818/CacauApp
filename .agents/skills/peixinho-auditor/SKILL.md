---
name: peixinho-auditor
description: >-
  Executa auditoria cega (Goldfish Review Pattern / SDD 2.0) invocando um subagente de contexto zero
  para confrontar o código atual contra uma especificação em docs/specs/ sem viés de confirmação.
  Use sempre que o usuário pedir para "rodar o peixinho", auditar uma spec ou validar brechas.
---

# Skill: Auditoria Peixinho Dourado (Contexto Zero)

Esta skill implementa o padrão **Elephant & Goldfish** de Spec-Driven Development 2.0 no Antigravity.

## Procedimento Passo a Passo

1. **Identificar a Especificação Alvo:**
   Localize o arquivo correspondente em `docs/specs/` (ex: `docs/specs/SPEC-001-sincronizacao-cacausync.md`).
2. **Invocar o Subagente de Contexto Zero (`research` ou `peixinho-auditor`):**
   Use a ferramenta `invoke_subagent` passando no `Prompt` **apenas**:
   - O caminho exato da especificação (`docs/specs/...`);
   - Os caminhos exatos dos arquivos de código-fonte que implementam a especificação;
   - A ordem expressa: *"Não elogie. Você tem memória zero da conversa. Confronte cada invariante da Spec com o código linha por linha e aponte brechas, bugs sutis, falhas de borda ou violações de contrato."*
3. **Consolidar e Corrigir:**
   Apresente o relatório imparcial do Peixinho ao usuário e corrija imediatamente qualquer falha crítica ou moderada encontrada.
