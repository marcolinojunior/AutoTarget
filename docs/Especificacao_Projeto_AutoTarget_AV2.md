# Especificação do Projeto: AutoTarget (AV2)

> **Nota para a próxima IA:** Este documento contém a transcrição completa das diretrizes para a avaliação 2 (AV2) do projeto "AutoTarget". Trata-se de um jogo desenvolvido para Android que serve como base para testar conceitos de Sistemas de Tempo Real, Programação Concorrente, Reconciliação de Dados e Otimização. O documento está dividido logicamente em: Apresentação Geral, Regras do Cenário Competitivo, Requisitos Técnicos Específicos (com fórmulas matemáticas e definições de threads) e a Rubrica de Avaliação. Use essas informações como contexto absoluto para responder dúvidas, sugerir implementações ou gerar código relacionado ao projeto.

---

## 1. APRESENTAÇÃO

Este documento descreve o projeto que consiste na implementação de um jogo para dispositivos Android chamado **AutoTarget** que servirá como plataforma para aplicação dos conceitos teóricos abordados: programação concorrente, sistemas de tempo real, reconciliação de dados, segurança cibernética, entre outros.

**Objetivo geral do jogo e descrição do cenário:**

O jogo simula um sistema de defesa automatizado. Alvos (representados por círculos) surgem e se movem aleatoriamente em uma tela. O jogador pode posicionar canhões automáticos que, uma vez ativados, disparam projéteis para abater os alvos. Cada canhão opera em uma thread independente, e os alvos também são threads. O sistema deve garantir sincronização no acesso a recursos compartilhados (por exemplo, área de colisão), registrar estatísticas em banco de dados, criptografar dados sensíveis e aplicar técnicas de reconciliação de dados para corrigir medições simuladas de sensores (distância, velocidade).

---

## 4. INFORMAÇÕES COMPLEMENTARES

### 4.1. OBJETIVO PRÁTICO DAS AVALIAÇÕES
Implementar uma versão competitiva do jogo AutoTarget, onde dois sistemas independentes (esquerda e direita) disputam o controle de alvos, utilizando reconciliação de dados para posicionar canhões de forma otimizada, considerando penalidades por uso excessivo de recursos. A avaliação integra conceitos de programação concorrente, sincronização, sistemas de tempo real, reconciliação de dados e otimização.

### 4.2. DESCRIÇÃO DO CENÁRIO
A tela do aplicativo é dividida verticalmente em duas áreas de jogo (campo esquerdo e campo direito). Cada área possui seu próprio conjunto de canhões, controlados por threads independentes. Alvos (círculos) surgem em posições aleatórias em toda a tela, mas só podem ser abatidos pelos canhões da área em que se encontram no momento do disparo. Quando um alvo cruza a linha divisória, ele passa a pertencer à outra área.

Cada sistema (esquerda/direita) tem um orçamento de “energia” que é consumido por cada canhão ativo por segundo. O número máximo de canhões é limitado (por exemplo, 10), e canhões adicionais além de um limite base (ex.: 5) sofrem penalidade na taxa de disparo (aumento do intervalo entre disparos). O objetivo é maximizar o número de alvos abatidos ao final de um tempo pré-definido (ex.: 60 segundos). O sistema vencedor é aquele com mais abates.

A reconciliação de dados é usada para, periodicamente, corrigir as leituras dos sensores (posição e velocidade dos alvos) e, com base nessas informações, decidir se é vantajoso realocar canhões existentes ou adicionar/remover canhões, considerando a penalidade.

---

## 5. VISÃO GERAL DO PROJETO AUTOTARGET, TELAS E ARQUITETURA

O jogo simula um sistema de defesa automatizado. Alvos (círculos) surgem e se movem de forma pseudoaleatória em um canvas. O jogador pode posicionar canhões automáticos (triângulos) que, uma vez ativados, disparam projéteis para abater os alvos. Cada canhão opera em uma thread independente e os alvos também podem ser modelados como threads, exigindo sincronização no acesso a recursos compartilhados (listas e regiões críticas).

---

## 6.2 AV2 – COMPETIÇÃO, RECONCILIAÇÃO DE DADOS E OTIMIZAÇÃO

### 6.2.1. OBJETIVO
Implementar uma versão competitiva do jogo AutoTarget, onde dois sistemas independentes (esquerda e direita) disputam o controle de alvos, utilizando reconciliação de dados para posicionar canhões de forma otimizada, considerando penalidades por uso excessivo de recursos. A avaliação integra conceitos de programação concorrente, sincronização, sistemas de tempo real, reconciliação de dados e otimização.

### 6.2.2 REQUISITOS E TÉCNICOS

**a) Divisão de Tela e Controle de Alvos:**
* A tela deve ser dividida por uma linha vertical no centro.
* Cada lado possui uma lista de canhões (threads) que só podem atirar em alvos cujo centro esteja na sua metade.
* Implementar um mecanismo para detectar quando um alvo cruza a linha: nesse momento, ele é transferido da lista do lado antigo para o novo. A transferência deve ser atômica (usar `synchronized` ou semáforo).
* Ao final do tempo, exibir o placar (número de abates de cada lado) e declarar o vencedor.

**b) Modelo de Recursos e Penalidades:**
* Definir um orçamento inicial de energia para cada lado (ex.: 100 unidades).
* Cada canhão ativo consome 1 unidade de energia por segundo (ou por ciclo de disparo). A energia é decrementada em tempo real.
* Se a energia chegar a zero, os canhões desse lado param de disparar.
* Penalidade por excesso de canhões: se o número de canhões em um lado for maior que um limite L (ex.: 5), o intervalo de disparo de cada canhão nesse lado é multiplicado por um fator `(1 + (n - L) * 0.2)`, ou seja, aumenta 20% por canhão extra.
* O aluno pode escolher outros modelos de penalidade, desde que justificados no relatório.

**c) Sensores Simulados e Coleta de Dados:**
* Cada alvo possui um sensor virtual que fornece leituras ruidosas de sua posição (x, y) e velocidade (vx, vy). O ruído é gaussiano com média zero e desvio padrão proporcional ao valor real (ex.: 5%).
* A cada segundo, cada lado coleta as leituras dos alvos que estão em seu território e armazena em um buffer (pelo menos 10 leituras por alvo para análise).

**d) Reconciliação de Dados para Otimização de Posicionamento:**
* Implementar a classe `DataReconciliation` com o método `reconcile(double[] y, double[][] V, double[][] A)` que retorna o vetor reconciliado `y_hat`, conforme:
    $$\hat{y} = y - V A^T (A V A^T)^{-1} A y$$
* Modelar o problema de posicionamento como um sistema de fluxos: cada alvo $i$ tem uma "demanda" (probabilidade de ser abatido) que depende da distância aos canhões. Os canhões $j$ têm uma "capacidade" (taxa de disparo). A reconciliação é usada para estimar a matriz de incidência $A$ que relaciona alvos e canhões, a partir das leituras ruidosas de distância.
* A cada 10 segundos, cada lado deve:
    * Reunir todas as leituras dos últimos 10 segundos para cada alvo.
    * Calcular a média e a variância das distâncias entre cada canhão e cada alvo (construindo a matriz de covariância V).
    * Definir a matriz de incidência A baseada na geometria (ex.: se a distância média for menor que um limiar, há conexão).
    * Aplicar a reconciliação para obter distâncias corrigidas.
    * Com base nas distâncias reconciliadas, calcular uma posição "ótima" para cada canhão (por exemplo, o centroide dos alvos que estão mais próximos). Se a nova posição for significativamente diferente da atual, o canhão deve se mover gradualmente (simulando realocação) – isso pode ser feito atualizando sua posição na tela.

**e) Decisão de Adicionar/Remover Canhões com Base em Custo-Benefício:**
* A cada ciclo de reconciliação, cada lado avalia se deve aumentar ou diminuir o número de canhões.
* Para isso, deve-se estimar o ganho esperado em abates com a adição de um novo canhão (baseado nas distâncias reconciliadas e na taxa de disparo) e comparar com o custo adicional de energia e a penalidade por excesso.
* Implementar uma função de utilidade que retorne o número esperado de abates por segundo dada uma configuração de canhões. A decisão pode ser tomada por um algoritmo guloso ou por otimização simples.
* A adição/remoção de canhões deve ser feita criando ou interrompendo threads, com cuidado para evitar condições de corrida.

**f) Escalonamento de Tempo Real e Análise de Escalonabilidade:**
* Identificar as tarefas (threads) do sistema competitivo, incluindo:
    * **T1:** Movimentação dos alvos (várias threads, uma por alvo)
    * **T2:** Disparo dos canhões de cada lado (várias threads)
    * **T3:** Verificação de colisões
    * **T4:** Atualização da UI
    * **T5:** Coleta de dados dos sensores
    * **T6:** Execução da reconciliação e otimização (periódica)
    * **T7:** Gerenciamento de energia e penalidades
    * *(Pelo menos 8 tarefas no total)*
* Definir para cada tarefa: prioridade, período (Pi), tempo de execução (Ci), deadline (Di) e jitter (Ji) – valores coerentes com a simulação.
* Construir o grafo de dependências (se houver) e preencher a tabela de tarefas.
* Calcular o tempo de resposta máximo Ri para cada tarefa usando a equação de resposta com prioridades fixas (Rate Monotonic) e verificar se o conjunto é escalonável em um único processador.
* **Variação de processadores:** Usar `Process.setThreadAffinityMask()` para executar as threads em 1, 2 e todos os núcleos disponíveis. Medir os tempos de resposta e verificar em quais configurações ocorre perda de escalonabilidade.
* Apresentar gráficos comparativos e conclusões.

---

### 6.2.3. RUBRICA DE AVALIAÇÃO (AV2)

| Critério | Peso | Insuficiente | Adequado | Bom | Excelente |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Divisão de tela, pertencimento e placar** | 15% | Regras incorretas; placar instável. | Funciona com falhas em casos limite. | Correto e estável. | Correto + excelente apresentação e testes de borda. |
| **Modelo de energia e penalidade** | 15% | Ausente/incoerente. | Implementa parcialmente. | Implementa corretamente e justifica. | Além do básico: logs/gráficos mostrando impacto. |
| **Sensores ruidosos + buffers** | 10% | Sem sensores/buffer. | Sensores ou buffer incompletos. | Implementação correta. | Inclui análise estatística (média/variância) bem apresentada. |
| **Reconciliação de dados (implementação + evidência)** | 25% | Não aplica ou não evidencia. | Aplica sem contrato claro/sem comparação. | Aplica com comparação antes/depois. | Validação quantitativa (erro/variância) + explicação didática. |
| **Otimização (mover/adicionar/remover)** | 15% | Sem estratégia coerente. | Estratégia simples e instável. | Estratégia coerente com melhoria observável. | Função utilidade clara + comparação de desempenho e justificativa. |
| **Tempo real: tarefas e análise** | 20% | Não identifica tarefas/tempos. | Tabela incompleta. | Tabela coerente + análise. | Medições reais + gráficos + discussão de gargalos. |

---
**Solução Completa para Reconciliação de Dados no Projeto AutoTarget (AV2)**
