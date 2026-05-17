# RELATÓRIO DE AUDITORIA DE QUALIDADE DE SOFTWARE (AI QA)
## GAT108 - Automação Avançada - Projeto "AutoTarget" (AV2)

Este relatório consubstancia a avaliação da qualidade estrita (AI Quality Assurance) em aderência ao modelo matemático, otimização de sistemas de tempo real, e controle físico do projeto AutoTarget, conforme a rubrica oficial de avaliação.

---

### TÓPICO 1: ANÁLISE INDIVIDUAL DOS ARQUIVOS JAVA

#### === CLASSE: Jogo.java ===
**1. Desvios Matemáticos e Lógicos:**
- *Penalidade Exponencial:* O cálculo do fator exato (`Fator = 1 + (n - L) * 0.2`) está corretamente implementado através do método `aplicarPenalidades()` e a penalidade no disparo obedece a equação. O decaimento de energia (1 unidade/s/canhão) está fiel ao modelo.

**2. Riscos de Concorrência e Ciclo de Vida:**
- *Transferência Atômica Inadequada:* A rubrica exige transferência ESTRITAMENTE ATÔMICA ao cruzar a linha. No método `transferirAlvosCruzados()`, o uso de `removeAll()` e `addAll()` é iterativo sem a proteção de um bloco `synchronized` atômico que encapsule a operação como transação única nas duas instâncias de `CopyOnWriteArrayList`. Dessa forma, threads que acessam a lista de alvos podem enxergar o alvo temporariamente duplicado ou inexistente em ciclos de clock intercalados.

**3. Gargalos de Tempo Real (STR):**
- *Iteração Crítica Limitante:* O loop da `PhysicsTask` (T1) a 16ms está encapsulando blocos de alocação temporal considerável e não delega totalmente a carga massiva de GC para threads auxiliares. Isso pode invadir e colapsar a UI-Thread se não controlado.

#### === CLASSE: DataReconciliation.java ===
**1. Desvios Matemáticos e Lógicos (CRÍTICO):**
- *Modelagem Incompatível com Gabarito:* O modelo da disciplina exige: `y_ij = (d_medido_ij)^2 - (x_j^2 + y_j^2)`, possuindo a Matriz M `N x 3` composta por `[1, -2*x_j, -2*y_j]`.
- Contudo, a implementação utiliza um método das diferenças baseada em canhão-âncora de dimensão `(N-1)`. Matriz M é construída como `(N-1)x2`. E a Matriz `V` é construída como matriz *densa* de covariância baseada na propagação do erro do canhão 0, violando frontalmente a instrução: "Matriz de Covariância V (diagonal) preenchida via aproximação pelo Delta Method".

**2. Riscos de Concorrência e Ciclo de Vida:**
- *Overshoot de Memória:* A criação desenfreada de matrizes nativas EJML (como arrays bidimensionais) para cada reconciliação sem poolers ou reúso aumenta substancialmente a pressão do Garbage Collector (GC Churn). Em STR, isso pode acarretar STW (Stop The World).

**3. Gargalos de Tempo Real (STR):**
- As operações SVD para extrair o Espaço Nulo são altamente dispendiosas. O cache adotado ameniza o problema, mas a verificação do `cachedMatMHash` poderia ser melhor otimizada.

#### === CLASSE: Canhao.java ===
**1. Desvios Matemáticos e Lógicos:**
- Atende as formulações logísticas de penalidade de maneira satisfatória, respeitando o escalonamento RMA T6.

**2. Riscos de Concorrência e Ciclo de Vida:**
- *Geração Contínua de Objetos (GC Overhead):* A classe aloca um novo `Projetil` e uma nova Thread via `new Projetil(...)` a cada disparo sem utilizar o padrão *Object Pool*. Essa proliferação de threads instanciadas sob demanda causará thrashing no OS Scheduler.

**3. Gargalos de Tempo Real (STR):**
- *Vazamento de CPU:* Criação de threads em massa num ambiente de tempo real (STR) pode prejudicar as threads prioritárias de RM.

#### === CLASSE: SensorThread.java ===
**1. Desvios Matemáticos e Lógicos:**
- *Conformidade Gaussiana:* Aplicação do ruído obedece as especificações `~N(0, 0.05)`, gerando variabilidade de 5% de maneira coerente e alimentando o buffer.
- *Histórico de Buffer:* O limiar de no mínimo 10 leituras está respeitado na topologia de arrays e LinkedList.

**2. Riscos de Concorrência e Ciclo de Vida:**
- *Ordem de Lock Perigosa:* A classe atinge o monitor `jogo.getCollisionLock()` e logo a seguir o `sensorLock`. Em STR, essa sobreposição sem controle de timeouts (tryLock) é um vetor clássico para Inversão de Prioridade e Deadlocks caso `ReconciliacaoThread` inverta a ordem.

**3. Gargalos de Tempo Real (STR):**
- Reconstrução iterativa de floats via `snap.leiturasPosX = new float[count]` gasta tempo excessivo em alocações ao invés de atualizar posições de arrays fixos.

#### === CLASSE: ReconciliacaoThread.java ===
**1. Desvios Matemáticos e Lógicos:**
- *Ponderação Equivocada (w_ij):* O requisito exige a movimentação gradual na UI adotando o peso explícito `w_ij = 1 / d_reconciliado_ij`. No entanto, a implementação utiliza uma atenuação exponencial através de `float w = (float) Math.exp(-BETA * Math.max(dist, 1f));`. Este é um desvio que afeta o controle e precisão de migração do centroide.
- *Afinidade Inexistente:* Não foi localizada implementação direta do Process Affinity via `Process.setThreadAffinityMask()`, deixando as threads "soltas" para migrar de núcleos de CPU indiscriminadamente, ferindo o critério do isolamento em STR.

**2. Riscos de Concorrência e Ciclo de Vida:**
- Iteração iterativa sob canhões sem lock-clipping robusto causa sobrecarga de travamento em T8.

**3. Gargalos de Tempo Real (STR):**
- A manipulação e iteradores de agrupamentos (clusters) acarreta sobrecarga no barramento de memória da CPU devido ao alocamento de dados temporários.

---

### TÓPICO 2: VEREDITO DAS RUBRICAS (AVALIAÇÃO DE NOTA)

| Requisito | Classificação | Justificativa Impeditiva de "Excelente" |
|---|---|---|
| **A) Divisão de Tela e Concorrência Atômica** | Bom | Não atinge a nota máxima pois a transição das instâncias através da linha não é empacotada atômicamente (usa sequências non-atomic entre Listas seguras, arriscando sumiço de instâncias e race conditions visuais). |
| **B) Modelo de Energia e Penalidades** | Excelente | As funções matemáticas e de redução atendem ao gabarito fielmente. |
| **C) Sensores e Buffer de Dados** | Bom | Gera o ruído de 5% e aplica o buffer corretamente de 10 leituras. Contudo, as repetidas construções de array a cada ciclo violam os princípios de gestão enxuta de heap STR. |
| **D) DataReconciliation (Matemática)** | Insuficiente | O pilar algébrico mais avançado (Reconciliação) sofreu alteração severa. Construíram a formulação via TDOA (Canhão âncora N-1) construindo M com N-1 linhas e criando Matriz V densa. A rubrica pedia Matriz M de dimensões `N x 3` e Covariância Diagonal exata. |
| **E) Otimização e Frota** | Adequado | Utilizou a exponencial `Math.exp` ao invés da interpolação inversa de distância `1 / d_reconciliado`. A realocação ocorre, mas o peso é infiel ao modelo e as instâncias em background geram threads ilimitadas no Canhão. |
| **F) Process Affinity e Jitter** | Insuficiente | `Process.setThreadAffinityMask()` não é utilizado nas threads apresentadas, prejudicando o isolamento de hardware requerido pela rubrica de 20%. |

---

### TÓPICO 3: PLANO DE AÇÃO E REFATORAÇÃO DE CÓDIGO

#### PRIORIDADE CRÍTICA

**1. Correção Algébrica na Reconciliação (Matriz M e V)**
- *Problema:* Desvio do modelo Mínimos Quadrados.
- *Classe:* `DataReconciliation.java`
- *Sugestão de Código:*
```java
// Remover a lógica N-1 iterativa e adotar a criação direta da Matriz Nx3
SimpleMatrix matM = new SimpleMatrix(N, 3);
double[][] V_arr = new double[N][N]; // Mantida diagonal

for (int j = 0; j < N; j++) {
    // Matriz M
    matM.set(j, 0, 1.0);
    matM.set(j, 1, -2.0 * canhoesX[j]);
    matM.set(j, 2, -2.0 * canhoesY[j]);

    // Matriz V (Diagonal)
    double dj = mediaDist[j];
    double var_j = varDist[j];
    V_arr[j][j] = Math.max(4.0 * (dj * dj) * var_j, 1e-6);
}
```

**2. Transição Estritamente Atômica de Alvos**
- *Problema:* Falta de garantia atômica durante a travessia de metade do ecrã.
- *Classe:* `Jogo.java`
- *Sugestão de Código:*
```java
// No método transferirAlvosCruzados(), criar um Synchronized block único
synchronized (listLock) { // listLock deve proteger as duas listas juntas
    if (!transferBufferDireita.isEmpty()) {
        alvosEsquerdo.removeAll(transferBufferDireita);
        alvosDireito.addAll(transferBufferDireita);
    }
    if (!transferBufferEsquerda.isEmpty()) {
        alvosDireito.removeAll(transferBufferEsquerda);
        alvosEsquerdo.addAll(transferBufferEsquerda);
    }
}
```

#### PRIORIDADE IMPORTANTE

**3. Ajuste do Peso Geométrico na Realocação (w_ij)**
- *Problema:* Centroide está usando decaimento beta exponencial ao invés do inverso da distância.
- *Classe:* `ReconciliacaoThread.java`
- *Sugestão de Código:*
```java
// Alterar a ponderação dentro de realocarCanhoes()
float dist = Alvo.calcularDistancia(canhao.getX(), canhao.getY(), r.x, r.y);
// Substituir a expressão exponencial pela descrita no modelo:
float w = 1f / Math.max(dist, 1f);
```

**4. Isolamento de Threads em Hardware (Process Affinity)**
- *Problema:* Jitter por escalonamento aleatório no Kernel.
- *Classe:* `SensorThread.java` / `ReconciliacaoThread.java`
- *Sugestão de Código:*
```java
// No início do run() de cada Thread de tempo real crítica (como T1, T7, T8):
try {
    // Exemplo para limitar em núcleo específico
    android.os.Process.setThreadAffinityMask(
        android.os.Process.myTid(), 0b0011 // 2 Núcleos
    );
} catch (Exception e) {
    Log.w("RMA", "Process Affinity indisponível ou negado.", e);
}
```

#### MELHORIA TÉCNICA

**5. Object Pooling em Projéteis para mitigar GC**
- *Problema:* Alocação excessiva de Threads instanciáveis (new Thread/Projetil).
- *Classe:* `Canhao.java`
- *Sugestão de Código:*
```java
// Ao invés de instanciar novos projéteis toda vez em disparar(),
// Deve-se adotar um pool pré-alocado (Ex: Executors.newFixedThreadPool).
Projetil projetil = ProjetilPool.obterOuInstanciar();
projetil.reutilizar(this.x, this.y, dirX, dirY, alvoReservado);
```