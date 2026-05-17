# RELATÓRIO DE AUDITORIA DE QUALIDADE DE SOFTWARE (AI QA)
## GAT108 - Automação Avançada - Projeto "AutoTarget" (AV2)

Este relatório consubstancia a avaliação da qualidade estrita (AI Quality Assurance) em aderência ao modelo matemático, otimização de sistemas de tempo real, e controle físico do projeto AutoTarget, conforme a rubrica oficial de avaliação.

---

### TÓPICO 1: ANÁLISE INDIVIDUAL DOS ARQUIVOS JAVA

#### === CLASSE: Jogo.java ===
**1. Desvios Matemáticos e Lógicos:**
- *Penalidade Exponencial:* O cálculo do fator exato (`Fator = 1 + (n - L) * 0.2`) está corretamente implementado através do método `aplicarPenalidades()` e a penalidade no disparo obedece a equação. O decaimento de energia (1 unidade/s/canhão) está fiel ao modelo.

**2. Riscos de Concorrência e Ciclo de Vida:**
- *Transferência Atômica Inadequada:* A rubrica exige transferência ESTRITAMENTE ATÔMICA ao cruzar a linha. No método `transferirAlvosCruzados()`, o uso de `removeAll()` e `addAll()` não está protegido por um bloco atômico único para ambas as metades dentro do processamento principal que iterou os alvos. As interações atômicas não impedem que um alvo seja contabilizado duplicado ou perdido temporariamente entre as duas listas de `CopyOnWriteArrayList` num ciclo de concorrência massiva. O lock de transação deveria cobrir a operação em conjunto.

**3. Gargalos de Tempo Real (STR):**
- *Inversões na UI/Main Thread:* A thread `PhysicsTask` (T1) a 16ms roda num `ScheduledThreadPoolExecutor`. O método `transferirAlvosCruzados()` cria `transferBufferDireita` e `transferBufferEsquerda` gerando alto GC overhead na verificação a cada 16ms (~60Hz).

#### === CLASSE: DataReconciliation.java ===
**1. Desvios Matemáticos e Lógicos (CRÍTICO):**
- *Modelagem Incompatível com Gabarito:* O modelo da disciplina exige a Matriz M de dimensões `N x 3` composta por `[1, -2*x_j, -2*y_j]`.
- A implementação está utilizando uma abordagem via canhão 0 como âncora, gerando uma Matriz M com dimensões `(N-1) x 2`.
- *Matriz de Covariância V:* A rubrica obriga que V seja diagonal, preenchida com o Delta Method `(2 * d_medio_ij)^2 * s_ij^2`. No código atual, `V_arr` é construída como uma Matriz Densa, espalhando a variância induzida pelo Canhão 0 para elementos extra-diagonais.

**2. Riscos de Concorrência e Ciclo de Vida:**
- *Overshoot de Memória e GC Churn:* O método `reconciliarAlvo` cria instâncias nativas do tipo `SimpleMatrix` repetidamente sem reaproveitamento e blocos de `try-catch` capturando exceções matriciais. Em um STR, isso causa Stop-The-World considerável.

**3. Gargalos de Tempo Real (STR):**
- As operações repetidas de `computeLeftNullSpace` (Espaço nulo esquerdo) realizam decomposição `svd()` intensa. O cache mitigou o problema, porém falhará se qualquer canhão se mover gradualmente durante o frame.

#### === CLASSE: Canhao.java ===
**1. Desvios Matemáticos e Lógicos:**
- A aplicação matemática da penalidade exponencial por excedente de canhões (`aplicarPenalidade(int totalCanhoesNoLado)`) atende satisfatoriamente a rubrica.

**2. Riscos de Concorrência e Ciclo de Vida:**
- *Overhead Massivo de GC e OS Threads:* No método `disparar()`, o código instancia uma nova Thread `new Projetil(...)` e executa `.start()` para CADA disparo. Isso é extremamente crítico em sistemas de tempo real, uma vez que o OS Thread Scheduler pode ser sobrecarregado, causando `OutOfMemoryError` ou Context Switching contínuo, violando o princípio do isolamento.

**3. Gargalos de Tempo Real (STR):**
- A thread (T6) é declarada sem qualquer limitação de núcleo ou preempção específica. Instanciar threads de vida curtíssima dentro de outra thread (Canhão) aumenta o jitter assustadoramente no barramento do sistema operacional.

#### === CLASSE: Projetil.java ===
**1. Desvios Matemáticos e Lógicos:**
- A formulação de colisão espacial `dist(projetil, alvo) <= RAIO_PROJETIL + raioAlvo` e a lógica de verificação ponto a ponto estão em conformidade com o cenário estipulado.

**2. Riscos de Concorrência e Ciclo de Vida:**
- A região crítica em `verificarColisoes()` usando `synchronized (collisionLock)` trava todas as colisões de todos os projéteis a cada 16ms globalmente, limitando a escalabilidade com aumento de número de alvos.

**3. Gargalos de Tempo Real (STR):**
- É um Gargalo RM (Priority 1). O travamento no monitor global afeta diretamente o fluxo de processamento de quadros rápidos.

#### === CLASSE: Alvo.java ===
**1. Desvios Matemáticos e Lógicos:**
- O cálculo euclidiano está de acordo. O polimorfismo é aplicado para as subclasses gerenciar suas velocidades e cores independentemente.

**2. Riscos de Concorrência e Ciclo de Vida:**
- Assim como `Projetil`, `Alvo` também herda `Thread` e consome recursos massivos do SO ao não ser suportado num ThreadPool.

#### === CLASSE: SensorThread.java ===
**1. Desvios Matemáticos e Lógicos:**
- *Ruído Gaussiano:* A aplicação via `ThreadLocalRandom.current().nextGaussian() * PROPORCAO_RUIDO * escala` implementa corretamente a média zero e o desvio padrão de 5%.
- *Buffer:* Mantido as 10 amostras como exigido usando o `TAMANHO_HISTORICO`.

**2. Riscos de Concorrência e Ciclo de Vida:**
- *Ordem de Lock:* A Thread atinge dois Locks em sequência: `synchronized (jogo.getCollisionLock())` e em seguida `synchronized (sensorLock)`. Este comportamento engessa a leitura de colisão para a cópia do estado no snapshot, gerando alto risco de latência induzida nas demais Threads RM prioritárias (Inversão de prioridade).

**3. Gargalos de Tempo Real (STR):**
- Na criação iterativa dos snapshots, múltiplos arrays são clonados `new float[count]`. Isso destrói o L1 Cache Hits.

#### === CLASSE: ReconciliacaoThread.java ===
**1. Desvios Matemáticos e Lógicos:**
- *Ponderação Equivocada (w_ij):* O requisito estipula que a ponderação da movimentação gradual deve obedecer o peso explícito `w_ij = 1 / d_reconciliado_ij`. No entanto, na implementação do método `realocarCanhoes` o peso utilizado foi `float w = (float) Math.exp(-BETA * Math.max(dist, 1f));`. Este desvio subverte o modelo do projeto.
- *Process Affinity Inexistente:* Não consta a utilização do método mandatário `Process.setThreadAffinityMask()`, sendo a Thread designada via API nativa duvidosa sem especificação da bitmask exigida (1, 2 e todos os núcleos).

**2. Riscos de Concorrência e Ciclo de Vida:**
- Acoplamento à Thread Principal (Jogo) pela instância não isolada da interface.

**3. Gargalos de Tempo Real (STR):**
- O agrupamento (clustering) de instâncias a cada 10 segundos pode impactar o Little Core escalonado se exceder a janela RM.

---

### TÓPICO 2: VEREDITO DAS RUBRICAS (AVALIAÇÃO DE NOTA)

| Critério | Classificação | Justificativa Impeditiva de "Excelente" |
| :--- | :--- | :--- |
| **A) Divisão de tela, pertencimento e placar** | Bom | Falha na transação atômica em nível mais profundo de abstração ao transferir entre listas, arriscando instabilidades visuais de piscar elementos na troca. |
| **B) Modelo de energia e penalidade** | Excelente | Implementa corretamente as curvas logísticas de penalidade exponencial e custos por canhão. |
| **C) Sensores ruidosos + buffers** | Bom | Embora a matemática esteja impecável, a alta alocação de memória (criação frequente de arrays locais) reduz o escore qualitativo de engenharia do software. |
| **D) Reconciliação de dados (implementação + evidência)** | Insuficiente | O pilar de cálculo não segue a equação ditada pela diretriz. Modificou a Dimensão e Arquitetura da Matriz M (para Nx2 usando âncora) e utilizou uma Matriz V com espalhamento de erro denso invés do Delta Method Diagonal exigido. |
| **E) Otimização (mover/adicionar/remover)** | Adequado | Utilizou uma função Exponencial (Math.exp) no cálculo do peso invés do inverso da distância exigida. Além da sobrecarga do GC com threads autônomas de projéteis. |
| **F) Tempo real: tarefas e análise** | Insuficiente | Não validou a variação do hardware isoladamente com a ferramenta exigida no projeto `Process.setThreadAffinityMask()`. |

---

### TÓPICO 3: PLANO DE AÇÃO E REFATORAÇÃO DE CÓDIGO

#### PRIORIDADE CRÍTICA (Erros matemáticos ou falhas que causam crash/ANR/Race Conditions)

**1. Correção Algébrica na Reconciliação (Matrizes M e V)**
- *Problema:* Desvio severo do Modelo dos Mínimos Quadrados.
- *Classe:* `DataReconciliation.java`
- *Sugestão de Código:*
```java
// Remover a lógica N-1 iterativa de canhão 0 âncora
// Adotar a criação da Matriz Nx3
SimpleMatrix matM = new SimpleMatrix(N, 3);
double[][] V_arr = new double[N][N]; // Matriz estritamente diagonal

for (int j = 0; j < N; j++) {
    // Matriz M: [1, -2x_j, -2y_j]
    matM.set(j, 0, 1.0);
    matM.set(j, 1, -2.0 * canhoesX[j]);
    matM.set(j, 2, -2.0 * canhoesY[j]);

    // Matriz V (Delta Method Diagonal)
    double dj = mediaDist[j];
    double var_j = varDist[j];
    V_arr[j][j] = Math.max(4.0 * (dj * dj) * var_j, 1e-6);
}
```

**2. Transição Estritamente Atômica de Alvos**
- *Problema:* Operação iterativa dividida sem proteção no escopo unificado de transação.
- *Classe:* `Jogo.java` (método `transferirAlvosCruzados`)
- *Sugestão de Código:*
```java
// O loop inteiro de identificação e transferência deve ocorrer no Lock Atômico
synchronized (listLock) {
    for (Alvo alvo : alvosEsquerdo) {
        if (geom.determineLado(alvo.getX()) == Lado.DIREITO) {
            transferBufferDireita.add(alvo);
        }
    }
    for (Alvo alvo : alvosDireito) {
        if (geom.determineLado(alvo.getX()) == Lado.ESQUERDO) {
            transferBufferEsquerda.add(alvo);
        }
    }
    // ... realizar as remoções e adições de imediato
}
```

#### PRIORIDADE IMPORTANTE (Desvios de regras de negócio, perdas de pacotes do buffer ou movimentação não gradual)

**3. Ajuste da Ponderação Geométrica (w_ij)**
- *Problema:* Movimentação adotou decaimento exponencial incorreto.
- *Classe:* `ReconciliacaoThread.java`
- *Sugestão de Código:*
```java
// Dentro de realocarCanhoes() e calcularCentroidePonderado(), alterar o cálculo:
float dist = Alvo.calcularDistancia(canhao.getX(), canhao.getY(), r.x, r.y);
// float w = (float) Math.exp(-BETA * Math.max(dist, 1f)); // Incorreto
float w = 1f / Math.max(dist, 1f); // Exigido pela rubrica
```

**4. Isolamento Físico de Process Affinity (Hardware Isolations)**
- *Problema:* Isolamento mandatório ausente.
- *Classe:* `SensorThread.java` e `ReconciliacaoThread.java`
- *Sugestão de Código:*
```java
// Injetar diretamente no run() destas threads prioritárias:
try {
    // Definir afinidade via JNI Bitmask
    android.os.Process.setThreadAffinityMask(android.os.Process.myTid(), 0b0011); // 2 Núcleos
} catch (Exception e) {
    Log.w("RMA", "Process Affinity setup falhou", e);
}
```

#### MELHORIA TÉCNICA (Otimização de POO, Clean Code ou tratamento preventivo de exceções)

**5. Controle de Thread Pools (Gargalos de RMA em Disparo)**
- *Problema:* `Canhao.java` faz New em Thread a cada tiro.
- *Classe:* `Canhao.java`
- *Sugestão de Código:*
```java
// Em vez de herdar Thread, Projetil deveria ser um Runnable consumido
// por um ExecutorService estático de Background de vida contínua
private static final ExecutorService projeteisPool = Executors.newFixedThreadPool(20);

// Em disparar()
Projetil projetil = new Projetil(...); // Remover o extend Thread
projeteisPool.execute(projetil);
```