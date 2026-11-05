# Regras de Negócio e Requisitos Operacionais — Ecossistema CacauApp & CacauSync (CEPEC / CEPLAC)

Este documento é a **Fonte Primária de Verdade de Negócio** do projeto. Todo agente construtor ou subagente auditor deve validar se o aplicativo Android (`CacauApp`) e o sistema web/servidor (`CacauSync`) atendem a estas regras reais de operação no campo e no laboratório.

---

## 1. Visão Geral do Negócio
O ecossistema automatiza a **avaliação fenotípica, fitossanitária e de produtividade de clones de cacaueiro** em quadras experimentais (ex.: ensaio competitivo `TC-1801`), substituindo pranchetas de papel por **visão computacional embarcada no celular (offline)** integrada a uma **plataforma central de auditoria científica e ranking genético no laboratório**.

---

## 2. Regras de Negócio do Aplicativo Mobile (`CacauApp`)

### RN-MOB-01 — Papel do Modelo YOLO (Enxergar os Frutos na Árvore)
1. O aplicativo utiliza um modelo de inteligência artificial **YOLO (`.tflite`) rodando 100% offline no aparelho** para **enxergar, localizar e contar os frutos de cacau** tanto na câmera ao vivo quanto na foto capturada.
2. O usuário deve poder configurar o modelo no app (selecionar modelo embutido ou importar um novo `.tflite` treinado pelo CEPEC, ajustar o limiar de confiança e o limite de detecções).
3. Se o pesquisador tremer a mão na hora do clique na câmera, o app deve garantir que os frutos presentes na foto estática continuem sendo detectados na tela seguinte.

### RN-MOB-02 — Revisão Humana em Campo (Classificação Fitossanitária)
1. Como o modelo base enxerga onde estão os frutos, cabe ao técnico na **Tela de Revisão Pós-Captura** validar o estado fitossanitário de cada fruto tocando sobre a caixa:
   - 🟢 **Sadio** (padrão inicial de todo fruto detectado)
   - 🟠 **Vassoura de Bruxa**
   - 🔴 **Podridão** (Podridão Parda / Monília)
   - ⚪ **Não Avaliável**
2. **Correção de Erros da IA no Campo:**
   - **Falso Negativo (IA não viu um fruto):** Tocar em qualquer espaço vazio da foto deve criar uma nova caixa de fruto manualmente.
   - **Falso Positivo (IA marcou folha/tronco como fruto):** O usuário deve poder selecionar a caixa e excluí-la na lixeira.
3. A interface da tela de revisão não pode cobrir os frutos no centro da foto com menus grandes.

### RN-MOB-03 — Desempenho e Compatibilidade Multi-Dispositivo Android
1. O aplicativo **deve funcionar com bom desempenho e estabilidade em múltiplos aparelhos Android** (desde celulares intermediários/antigos de campo, como Samsung Galaxy A50 / Android 7+, até aparelhos recentes), sem travar a câmera, sem *OutOfMemoryError* ao processar fotos de alta resolução e sem *UnsatisfiedLinkError* nativo.
2. As caixas desenhadas sobre os frutos **não podem sofrer distorção ou deslocamento** entre aparelhos com telas de proporções diferentes ou quando a câmera salva a foto rotacionada (metadados EXIF).

### RN-MOB-04 — Modos de Trabalho no Campo (Coleta Livre e Modo Rota)
1. **Modo Coleta Livre:** O técnico define o ensaio/campanha e informa Clone, Parcela e Planta. Ao salvar uma planta, o contador numérico da planta avança automaticamente para agilizar a caminhada.
2. **Modo Rota Guiada (FieldBook):** O técnico carrega uma planilha CSV de planejamento (exportada pelo laboratório) e o app guia a coleta planta a planta na sequência exata da quadra, mostrando progresso, permitindo avançar/voltar e refazer a coleta de uma planta.
3. **Planta Morta / Seca / Ausente (`Não Coletável`):** Em qualquer modo, se a árvore morreu ou não pode ser avaliada, o técnico deve poder registrá-la com 1 toque como **"Não Coletável"**, gravando `0` frutos sem precisar tirar foto.

### RN-MOB-05 — Segurança Offline dos Dados e Sincronização
1. **Zero Perda de Dados em Campo:** Toda coleta confirmada deve ser salva imediatamente no armazenamento local do celular. Se o app fechar ou o celular reiniciar no meio da mata, todas as coletas devem continuar lá ao reabrir.
2. **Dupla Saída no Pós-Campo:** Quando houver conexão ou cabo, o técnico pode:
   - **Sincronizar direto via Wi-Fi/Rede** com o servidor `CacauSync` (enviando contagens, doenças, caixas, GPS e fotos comprimidas); ou
   - **Exportar planilha CSV** padronizada na pasta `Download/CEPEC` do celular.

---

## 3. Regras de Negócio do Back-end e Painel Web (`CacauSync`)

### RN-BACK-01 — Organização Hierárquica do Ensaio Genético
1. Todo registro obedece à hierarquia agronômica do CEPEC:
   **Ensaio** (ex.: `TC-1801`) $\rightarrow$ **Parcela / Bloco** $\rightarrow$ **Clone (Variedade Genética)** $\rightarrow$ **Planta (Cova/Indivíduo)** $\rightarrow$ **Leitura de Coleta**.
2. Perfis de acesso:
   - **Coordenador (`ROLE_COORDENADOR`):** Cria ensaios, cadastra usuários e audita dados.
   - **Pesquisador (`ROLE_PESQUISADOR`):** Sincroniza coletas de campo, audita/valida leituras e consulta relatórios.

### RN-BACK-02 — Recebimento Resiliente e Sem Duplicidade
1. O servidor deve receber coletas tanto pelo **botão de sincronização do app** (JSON + upload de fotos) quanto pelo **painel web** (upload de planilha CSV + lote de fotos avulsas, vinculando cada foto à sua planta automaticamente pelo nome do arquivo).
2. **Idempotência:** Sincronizar a mesma coleta duas vezes nunca pode duplicar a contagem de frutos no banco (apenas anexa a foto caso ela tenha falhado no primeiro envio).
3. **Tolerância a Falhas:** Uma foto corrompida ou inválida não pode cancelar a importação das demais leituras do pacote; o sistema salva os dados e emite um aviso.
4. **Alerta de GPS:** Se o celular coletou no meio da mata sem sinal de satélite (`latitude = 0` e `longitude = 0`), a leitura é aceita normalmente, mas marcada com **Alerta de GPS** para ciência do pesquisador.

### RN-BACK-03 — Auditoria Científica (A Palavra Final é do Pesquisador)
1. Toda leitura recém-chegada do campo entra como **Pendente de Validação (`PENDENTE`)**.
2. No painel web, o pesquisador inspeciona a foto com as caixas desenhadas e decide:
   - **Confirmar IA (`VALIDADA`):** A contagem original está correta.
   - **Ajustar (`AJUSTADA`):** O pesquisador corrige as caixas/doenças ou a quantidade total (`qtdFrutosValidada`). **O número ajustado pelo pesquisador passa a valer em todos os relatórios**, e a soma das classes (Sadios + Vassoura + Podridão + Não Avaliável) deve bater com o total validado.
   - **Rejeitar (`REJEITADA`):** Foto borrada, árvore errada ou teste inválido. **Sai completamente dos cálculos do ensaio** (vale zero e não conta como planta avaliada).
   - **Desfazer (`PENDENTE`):** Remove a validação anterior sem apagar os dados originais enviados pelo app.
3. **Reativação Automática de Planta:** Se uma planta estava marcada como `Não Coletável` (morta/ausente), mas chega uma leitura coletável com frutos (ou o pesquisador na auditoria ajusta a leitura para `> 0` frutos porque viu frutos na foto), a planta volta automaticamente ao status **Viva (`NORMAL`)**.

### RN-BACK-04 — Cálculo de Sobrevivência, Fitossanidade e Previsão de Produtividade por Clone
O objetivo central do `CacauSync` é responder ao melhorista genético **qual clone é superior em produtividade e resistência**:
1. **Taxa de Sobrevivência do Clone (%):** Proporção de plantas vivas (`NORMAL`) em relação ao total de plantas plantadas daquele clone no ensaio.
2. **Deduplicação por Planta e Cobertura:**
   - Se uma mesma planta foi fotografada mais de uma vez (re-coleta), **apenas a leitura válida mais recente** daquela planta entra na soma de frutos.
   - A média de frutos por planta divide apenas pelas **plantas vivas que já foram efetivamente avaliadas** (não penalizando plantas da rota que o técnico ainda não visitou), exibindo claramente a **Taxa de Cobertura da Coleta** (`plantas avaliadas / plantas vivas`).
3. **Produtividade Comercial Líquida (Apenas Frutos Sadios):**
   - Frutos com **Vassoura-de-Bruxa** ou **Podridão Parda** são **perda fitossanitária** e **NUNCA** devem inflar a produtividade do clone no ranking.
   - O **Ranking de Clones** ordena os clones pela **Média de Frutos Sadios por Planta Avaliada**.
4. **Previsão Agronômica ($\text{kg/ha}$ e $\text{Arrobas/ha}$):**
   - Converte a média de frutos sadios em **$\text{kg/ha}$** e **$\text{Arrobas (@)/ha}$** ($1\text{ @} = 15\text{ kg}$) de amêndoa seca usando o **Índice de Fruto** (quantos frutos sadios geram $1\text{ kg}$ de cacau seco, ajustável no painel) e a **Densidade de Plantio** ($\text{plantas/ha}$), ponderando pela taxa de sobrevivência do clone no campo e exibindo também a **Perda Estimada em $\text{@/ha}$ causada pelas doenças**.

### RN-BACK-05 — Visão Geoespacial e Exportação para Pesquisa
1. **Mapa de Focos da Quadra:** Exibir as plantas avaliadas com base no GPS, destacando visualmente onde estão as plantas sadias, os focos de Vassoura-de-Bruxa e os focos de Podridão Parda.
2. **Ciclo Completo de Planilhas:**
   - Exportar a **Rota Pré-Campo (`rota-csv`)** pronta para ser aberta no Modo Rota do `CacauApp`;
   - Exportar a **Análise Auditada Completa (`analise-csv`)** com todas as contagens da IA, ajustes do pesquisador, doenças, responsável pela validação e coordenadas GPS.
