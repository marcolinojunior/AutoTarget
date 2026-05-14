# Transcricao das imagens - AutoTarget (AV2)

## 1. APRESENTACAO

Este documento descreve projeto que consiste na implementacao de um jogo para dispositivos Android chamado AutoTarget que servira como plataforma para aplicacao dos conceitos teoricos abordados: programacao concorrente, sistemas de tempo real, reconciliacao de dados, seguranca cibernetica, entre outros

**Objetivo geral do jogo e descricao do cenario:**

O jogo simula um sistema de defesa automatizado. Alvos (representados por circulos) surgem e se movem aleatoriamente em uma tela. O jogador pode posicionar canhoes automaticos que, uma vez ativados, disparam projeteis para abater os alvos. Cada canhao opera em uma thread independente, e os alvos tambem sao threads. O sistema deve garantir sincronizacao no acesso a recursos compartilhados (por exemplo, area de colisao), registrar estatisticas em banco de dados, criptografar dados sensiveis e aplicar tecnicas de reconciliacao de dados para corrigir medicoes simuladas de sensores (distancia, velocidade).

## 4. INFORMACOES COMPLEMENTARES

### 4.1. OBJETIVO PRATICO DAS AVALIACOES

Implementar uma versao competitiva do jogo AutoTarget, onde dois sistemas independentes (esquerda e direita) disputam o controle de alvos, utilizando reconciliacao de dados para posicionar canhoes de forma otimizada, considerando penalidades por uso excessivo de recursos. A avaliacao integra conceitos de programacao concorrente, sincronizacao, sistemas de tempo real, reconciliacao de dados e otimizacao.

### 4.2. DESCRICAO DO CENARIO

A tela do aplicativo e dividida verticalmente em duas areas de jogo (campo esquerdo e campo direito). Cada area possui seu proprio conjunto de canhoes, controlados por threads independentes. Alvos (circulos) surgem em posicoes aleatorias em toda a tela, mas so podem ser abatidos pelos canhoes da area em que se encontram no momento do disparo. Quando um alvo cruza a linha divisoria, ele passa a pertencer a outra area.

Cada sistema (esquerda/direita) tem um orcamento de "energia" que e consumido por cada canhao ativo por segundo. O numero maximo de canhoes e limitado (por exemplo, 10), e canhoes adicionais alem de um limite base (ex.: 5) sofrem penalidade na taxa de disparo (aumento do intervalo entre disparos). O objetivo e maximizar o numero de alvos abatidos ao final de um tempo pre-definido (ex.: 60 segundos). O sistema vencedor e aquele com mais abates.

A reconciliacao de dados e usada para, periodicamente, corrigir as leituras dos sensores (posicao e velocidade dos alvos) e, com base nessas informacoes, decidir se e vantajoso realocar canhoes existentes ou adicionar/remover canhoes, considerando a penalidade.

## 5. VISAO GERAL DO PROJETO AUTOTARGET, TELAS E ARQUITETURA

O jogo simula um sistema de defesa automatizado. Alvos (circulos) surgem e se movem de forma pseudoaleatoria em um canvas. O jogador pode posicionar canhoes automaticos (triangulos) que, uma vez ativados, disparam projeteis para abater os alvos. Cada canhao opera em uma thread independente e os alvos tambem podem ser modelados como threads, exigindo sincronizacao no acesso a recursos compartilhados (listas e regioes criticas).

## 6.2 AV2 - COMPETICAO, RECONCILIACAO DE DADOS E OTIMIZACAO

### 6.2.1. OBJETIVO

Implementar uma versao competitiva do jogo AutoTarget, onde dois sistemas independentes (esquerda e direita) disputam o controle de alvos, utilizando reconciliacao de dados para posicionar canhoes de forma otimizada, considerando penalidades por uso excessivo de recursos. A avaliacao integra conceitos de programacao concorrente, sincronizacao, sistemas de tempo real, reconciliacao de dados e otimizacao.

### 6.2.2 REQUISITOS E TECNICOS

#### a) Divisao de Tela e Controle de Alvos:

- A tela deve ser dividida por uma linha vertical no centro.
- Cada lado possui uma lista de canhoes (threads) que so podem atirar em alvos cujo centro esteja na sua metade.
- Implementar um mecanismo para detectar quando um alvo cruza a linha: nesse momento, ele e transferido da lista do lado antigo para o novo. A transferencia deve ser atomica (usar synchronized ou semaforo).
- Ao final do tempo, exibir o placar (numero de abates de cada lado) e declarar o vencedor.

#### b) Modelo de Recursos e Penalidades:

- Definir um orcamento inicial de energia para cada lado (ex.: 100 unidades).
- Cada canhao ativo consome 1 unidade de energia por segundo (ou por ciclo de disparo). A energia e decrementada em tempo real.
- Se a energia chegar a zero, os canhoes desse lado param de disparar.
- Penalidade por excesso de canhoes: se o numero de canhoes em um lado for maior que um limite L (ex.: 5), o intervalo de disparo de cada canhao nesse lado e multiplicado por um fator (1 + (n - L) * 0,2), ou seja, aumenta 20% por canhao extra. O aluno pode escolher outros modelos de penalidade, desde que justificados no relatorio.

#### c) Sensores Simulados e Coleta de Dados:

- Cada alvo possui um sensor virtual que fornece leituras ruidosas de sua posicao (x, y) e velocidade (vx, vy). O ruido e gaussiano com media zero e desvio padrao proporcional ao valor real (ex.: 5%).
- A cada segundo, cada lado coleta as leituras dos alvos que estao em seu territorio e armazena em um buffer (pelo menos 10 leituras por alvo para analise).

#### d) Reconciliacao de Dados para Otimizacao de Posicionamento:

- Implementar a classe DataReconciliation com o metodo reconcile(double[] y, double[][] V, double[][] A) que retorna o vetor reconciliado y_hat, conforme:

$$
\hat{y} = y - V A^{T} (A V A^{T})^{-1} A y
$$

- Modelar o problema de posicionamento como um sistema de fluxos: cada alvo i tem uma "demanda" (probabilidade de ser abatido) que depende da distancia aos canhoes. Os canhoes tem uma "capacidade" (taxa de disparo). A reconciliacao e usada para estimar a matriz de incidencia A que relaciona alvos e canhoes, a partir das leituras ruidosas de distancia.
- A cada 10 segundos, cada lado deve reunir todas as leituras dos ultimos 10 segundos para cada alvo.
- A cada 10 segundos, cada lado deve calcular a media e a variancia das distancias entre cada canhao e cada alvo (construindo a matriz de covariancia V).
- A cada 10 segundos, cada lado deve definir a matriz de incidencia A baseada na geometria (ex.: se a distancia media for menor que um limiar, ha conexao).
- A cada 10 segundos, cada lado deve aplicar a reconciliacao para obter distancias corrigidas.
- Com base nas distancias reconciliadas, calcular uma posicao "otima" para cada canhao (por exemplo, o centroide dos alvos que estao mais proximos). Se a nova posicao for significativamente diferente da atual, o canhao deve se mover gradualmente (simulando realocacao) - isso pode ser feito atualizando sua posicao na tela.

#### e) Decisao de Adicionar/Remover Canhoes com Base em Custo-Beneficio:

- A cada ciclo de reconciliacao, cada lado avalia se deve aumentar ou diminuir o numero de canhoes.
- Deve-se estimar o ganho esperado em abates com a adicao de um novo canhao (baseado nas distancias reconciliadas e na taxa de disparo) e comparar com o custo adicional de energia e a penalidade por excesso.
- Implementar uma funcao de utilidade que retorne o numero esperado de abates por segundo dada uma configuracao de canhoes.
- A decisao pode ser tomada por um algoritmo guloso ou por otimizacao simples.
- A adicao/remocao de canhoes deve ser feita criando ou interrompendo threads, com cuidado para evitar condicoes de corrida.

#### f) Escalonamento de Tempo Real e Analise de Escalabilidade:

- Identificar as tarefas (threads) do sistema competitivo, incluindo:
- T1: Movimentacao dos alvos (varias threads, uma por alvo)
- T2: Disparo dos canhoes de cada lado (varias threads)
- T3: Verificacao de colisoes
- T4: Atualizacao da UI
- T5: Coleta de dados dos sensores
- T6: Execucao da reconciliacao e otimizacao (periodica)
- T7: Gerenciamento de energia e penalidades
- (Pelo menos 8 tarefas no total)
- Definir para cada tarefa: prioridade, periodo (Pi), tempo de execucao (Ci), deadline (Di) e jitter (Ji) - valores coerentes com a simulacao. Construir o grafo de dependencias (se houver) e preencher a tabela de tarefas.
- Calcular o tempo de resposta maximo Ri para cada tarefa usando a equacao de resposta com prioridades fixas (Rate Monotonic) e verificar se o conjunto e escalonavel em um unico processador.
- Variacao de processadores: Usar Process.setThreadAffinityMask() para executar as threads em 1, 2 e todos os nucleos disponiveis. Medir os tempos de resposta e verificar em quais configuracoes ocorre perda de escalonabilidade.
- Apresentar graficos comparativos e conclusoes.

### 6.2.3. RUBRICA DE AVALIACAO (AV2)

| Criterio | Peso | Insuficiente | Adequado | Bom | Excelente |
| --- | --- | --- | --- | --- | --- |
| Divisao de tela, pertencimento e placar | 15% | Regras incorretas; placar instavel. | Funciona com falhas em casos limite. | Correto e estavel. | Correto + excelente apresentacao e testes de borda. |
| Modelo de energia e penalidade | 15% | Ausente/incoerente. | Implementa parcialmente. | Implementa corretamente e justifica. | Alem do basico: logs/graficos mostrando impacto. |
| Sensores ruidosos + buffers | 10% | Sem sensores/buffer. | Sensores ou buffer incompletos. | Implementacao correta. | Inclui analise estatistica (media/variancia) bem apresentada. |
| Reconciliacao de dados (implementacao + evidencia) | 25% | Nao aplica ou nao evidencia. | Aplica sem contrato claro/sem comparacao. | Aplica com comparacao antes/depois. | Validacao quantitativa (erro/variancia) + explicacao didatica. |
| Otimizacao (mover/adicionar/remover) | 15% | Sem estrategia coerente. | Estrategia simples instavel. | Estrategia coerente com melhoria observavel. | Funcao de utilidade clara + comparacao de desempenho e justificativa. |
| Tempo real: tarefas e analise | 20% | Nao identifica tarefas/tempos. | Tabela incompleta. | Tabela coerente + analise. | Medicoes reais + graficos + discussao de gargalos. |

Soluçao Completa para Reconciliacao de Dados no Projeto AutoTarget (AV2)

---

## Mega TODO list (cobre todos os topicos)

- Mapear todas as telas e fluxo de jogo conforme o cenario competitivo.
- Implementar divisao da tela por linha vertical e logica de pertencimento por lado.
- Criar estruturas de dados separadas por lado para canhoes e alvos.
- Implementar detecçao de cruzamento da linha divisoria com transferencia atomica.
- Garantir sincronizaçao no acesso a listas e regioes criticas com locks/semaforos.
- Implementar threads de alvos com movimentacao pseudoaleatoria consistente.
- Implementar threads de canhoes com ciclos de disparo configuraveis.
- Implementar logica de projeteis e colisao com alvos.
- Atualizar placar por lado de forma thread-safe.
- Exibir placar final e declarar vencedor ao fim do tempo.
- Implementar orcamento de energia por lado com consumo em tempo real.
- Bloquear disparos quando a energia do lado chegar a zero.
- Implementar penalidade de disparo por excesso de canhoes acima de L.
- Parametrizar limites (tempo total, energia inicial, L, fator de penalidade).
- Implementar sensores virtuais com ruido gaussiano proporcional ao valor real.
- Criar buffer por alvo com minimo de 10 leituras por lado.
- Implementar coleta de sensores a cada segundo por lado.
- Implementar classe DataReconciliation e metodo reconcile conforme formula.
- Implementar matriz de covariancia V com media e variancia por canhao-alvo.
- Implementar matriz de incidencia A baseada em geometria/limiar de distancia.
- Agendar reconciliacao a cada 10 segundos com janela deslizante de leituras.
- Calcular distancias reconciliadas e registrar comparacao antes/depois.
- Calcular nova posicao otima dos canhoes (ex.: centroide) por lado.
- Implementar realocacao gradual dos canhoes quando houver diferenca significativa.
- Definir funcao de utilidade para estimar abates por segundo.
- Implementar decisao de adicionar/remover canhoes com custo-beneficio.
- Criar/remover threads de canhoes de forma segura e sem condicoes de corrida.
- Implementar logs/metricas para energia, penalidades, acertos e desempenho.
- Registrar estatisticas em banco de dados conforme requisito geral.
- Criptografar dados sensiveis persistidos.
- Definir conjunto de tarefas T1..T7 e incluir pelo menos 8 tarefas no total.
- Medir/estimar Pi, Ci, Di, Ji para cada tarefa e montar tabela.
- Construir grafo de dependencias entre tarefas e validar ordem de execucao.
- Calcular tempo de resposta maximo Ri com Rate Monotonic.
- Verificar escalonabilidade em um unico processador e documentar resultado.
- Implementar afinidade de threads com Process.setThreadAffinityMask().
- Medir tempos de resposta em 1, 2 e todos os nucleos disponiveis.
- Identificar perda de escalonabilidade e gargalos de desempenho.
- Produzir graficos comparativos de metricas e conclusoes.
- Validar todos os requisitos com testes de borda e cenarios limite.
- Gerar evidencias visuais e numericas para a rubrica da AV2.
- Preparar relatorio final com justificativas de modelos e escolhas.
