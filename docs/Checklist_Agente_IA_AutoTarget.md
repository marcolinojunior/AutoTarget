# AutoTarget AV2: Checklist de Verificação de Agente (AI Quality Assurance)

> **Contexto para o Agente:** Utilize este documento como um guia passo a passo para validar a implementação da Avaliação 2 (AV2) do projeto Android "AutoTarget". Cada item deve ser verificado em código e, onde aplicável, testado em tempo de execução para garantir conformidade com as especificações (concorrência, reconciliação de dados, otimização e sistemas de tempo real).

---

## FASE 1: Arquitetura Base e Cenário Competitivo (a, b)

### 1.1 Divisão de Tela e Controle de Alvos (Peso: 15%)
- [ ] **Validação Visual/UI:** A tela está dividida verticalmente no centro de forma clara?
- [ ] **Restrição de Disparo:** Os canhões do lado esquerdo *apenas* atiram em alvos com o centro no lado esquerdo? Os do lado direito respeitam a mesma regra para o lado direito? (Verificar a lógica de colisão/mira).
- [ ] **Transferência Atômica:** Quando um alvo cruza a linha divisória, ele é removido da lista de uma área e adicionado à outra?
- [ ] **Thread Safety (Crucial):** A operação de transferência usa `synchronized`, semáforos ou `ConcurrentHashMap`/estruturas seguras para evitar `ConcurrentModificationException` ou perda de alvos?
- [ ] **Placar:** O sistema contabiliza abates independentes para cada lado e declara o vencedor ao final do tempo limite (ex: 60s)?
- [ ] **Testes de Borda:** O comportamento é estável se o alvo cruzar a linha exatamente no momento do disparo ou da verificação?

### 1.2 Modelo de Recursos e Penalidades (Peso: 15%)
- [ ] **Orçamento Inicial:** Cada lado inicia com um orçamento de energia configurável (ex: 100)?
- [ ] **Consumo em Tempo Real:** Cada canhão ativo consome 1 unidade de energia por segundo ou ciclo de disparo? A redução de energia ocorre corretamente em tempo de execução?
- [ ] **Condição de Parada (Energia = 0):** Se a energia de um lado chegar a zero, as threads dos canhões desse lado são pausadas/interrompidas e param de atirar?
- [ ] **Cálculo de Penalidade:** O sistema monitora o número de canhões? Se `N > Limite (ex: 5)`, o intervalo de disparo é recalculado usando o fator `(1 + (N - L) * 0.2)`? (Exigir justificativa caso outro modelo seja usado).
- [ ] **Evidência Gráfica/Log:** Existem logs claros ou elementos na UI mostrando o consumo de energia e a aplicação da penalidade?

---

## FASE 2: Coleta de Dados e Matemática (c, d)

### 2.1 Sensores Ruidosos e Buffers (Peso: 10%)
- [ ] **Simulação de Ruído:** O sensor de cada alvo aplica ruído gaussiano (média zero, desvio padrão proporcional a ~5%) nas posições reais (x,y) e velocidades (vx, vy)?
- [ ] **Rotina de Coleta:** Existe uma thread/rotina que coleta as leituras a cada 1 segundo?
- [ ] **Buffer de Dados:** O buffer armazena corretamente o histórico? O requisito mínimo de 10 leituras por alvo antes da análise está sendo respeitado?
- [ ] **Estatísticas Básicas:** O sistema calcula a média e a variância dos dados lidos antes da reconciliação?

### 2.2 Reconciliação de Dados (Peso: 25%)
- [ ] **Classe e Método:** A classe `DataReconciliation` existe com a assinatura correta `reconcile(double[] y, double[][] V, double[][] A)`?
- [ ] **Implementação Matemática:** A fórmula $\hat{y} = y - V A^T (A V A^T)^{-1} A y$ foi implementada corretamente? (Verificar as operações de multiplicação de matriz, transposição e inversão. Dependência de bibliotecas de álgebra linear é comum aqui).
- [ ] **Definição das Matrizes (A e V):**
    - A matriz de covariância (V) foi construída com as variâncias calculadas das leituras?
    - A matriz de incidência (A) relaciona alvos e canhões baseando-se em uma geometria/limiar de distância média?
- [ ] **Execução Periódica:** A rotina de reconciliação executa a cada 10 segundos, puxando dados do buffer?
- [ ] **Evidência de Reconciliação:** O sistema produz logs ou saída em interface demonstrando a distância ruidosa (antes) e a distância corrigida (depois)? A variância/erro diminuiu?

---

## FASE 3: Inteligência e Otimização (d, e)

### 3.1 Realocação de Canhões (Parte de d)
- [ ] **Cálculo de Posição Ótima:** O sistema calcula o "centroide" ou posição ideal para um canhão focar nos alvos mais próximos, usando as *distâncias reconciliadas*?
- [ ] **Movimentação Gradual:** Se a posição ideal diferir da atual, o canhão é movido progressivamente na UI (sem teletransporte)?

### 3.2 Otimização: Adicionar/Remover Canhões (Peso: 15%)
- [ ] **Análise de Custo-Benefício:** A cada ciclo de reconciliação (10s), o sistema avalia matematicamente a necessidade de alterar a frota de canhões?
- [ ] **Função de Utilidade:** Existe uma função que calcula o ganho esperado de abates vs. o custo de energia + penalidade de intervalo de disparo?
- [ ] **Gerenciamento de Threads Seguro:** A decisão de adicionar ou remover canhões cria ou finaliza threads de forma segura (sem concorrência destrutiva, `ConcurrentModificationException` ou threads órfãs)?

---

## FASE 4: Sistemas de Tempo Real (STR) (f)

### 4.1 Identificação e Especificação de Tarefas (Peso: 20%)
- [ ] **Mapeamento T1-T7:** As threads foram corretamente identificadas (Alvos, Canhões, Colisão, UI, Sensores, Reconciliação, Energia)? Existem pelo menos 8 tarefas bem definidas?
- [ ] **Parâmetros STR Definidos:** Para cada tarefa, foram especificados no código ou relatório: Prioridade, Período (Pi), Tempo de Execução (Ci), Deadline (Di) e Jitter (Ji)?

### 4.2 Análise de Escalonabilidade
- [ ] **Cálculo de Rate Monotonic:** O tempo de resposta máximo (Ri) de cada tarefa foi calculado usando equações de prioridade fixa? O sistema é teoricamente escalonável em um processador?
- [ ] **Tabela/Grafo:** Existe um relatório documentando o grafo de dependências e a tabela de tarefas?

### 4.3 Variação de Processadores (Process Affinity)
- [ ] **Implementação do Affinity:** O método `Process.setThreadAffinityMask()` (ou similar via JNI/Android) foi utilizado para amarrar as threads a núcleos específicos?
- [ ] **Testes de Cenário:** Foram feitos testes rodando o sistema com 1 núcleo, 2 núcleos e todos os núcleos?
- [ ] **Análise de Resultados:** Foram fornecidos gráficos comparando os tempos de resposta e identificando em quais configurações ocorre perda de escalonabilidade (deadlines perdidos)?

---

## FASE 5: Entrega e Qualidade Geral
- [ ] **Relatório/Documentação:** O aluno entregou um relatório justificando modelos de penalidade, mostrando cálculos de Rate Monotonic, gráficos de afinidade e matrizes de reconciliação?
- [ ] **Crash/ANR:** O app Android roda liso? Ocorre *Application Not Responding* (ANR) devido ao peso da inversão de matrizes na Thread de UI? (O cálculo `reconcile` *deve* estar em background).
