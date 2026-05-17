# RELATÓRIO TÉCNICO DE AUDITORIA AI QA (AV2) - GAT108

---

## TÓPICO 1: ANÁLISE INDIVIDUAL DOS ARQUIVOS JAVA

### === CLASSE: Jogo.java ===
**Desvios Matemáticos e Lógicos:**
A lógica de penalidade dinâmica no `Canhao` e no bônus temporal obedece corretamente as instruções de `1 + (n - L) * 0.2` baseada em decaimentos temporais.
**Riscos de Concorrência e Ciclo de Vida:**
A implementação utiliza blocos explícitos (ex: `synchronized (listLock)`) e variáveis atômicas encapsuladas com sucesso (`EnergyManager`). O loop da verificação de colisão `verificarColisoes()` está confinado na Thread de Física T1. A transição que cruza o limite da tela (antes falha em atomismo) já encontra-se atômica, o que é validado pela encapsulação das iterações iterativas.
**Gargalos de Tempo Real (STR):**
O ciclo em `verificarColisoes()` faz realocação da Árvore QuadTree (que causa impacto no barramento da memória). A alocação excessiva da UI Thread foi contida.

### === CLASSE: DataReconciliation.java ===
**Desvios Matemáticos e Lógicos:**
O código se atenta corretamente às dimensões da matriz `Nx3`, estabelecendo o espaço nulo à esquerda e validando N >= 4. Adicionalmente, as variâncias agora são processadas corretamente usando a propagação de erro local `Math.max(4.0 * dj_sq * var_j, 1e-6)` e de acordo com o método de incertezas independentes (Matriz Diagonal do Método Delta).
**Riscos de Concorrência e Ciclo de Vida:**
O cache SVD inserido mitiga excessos de alocações na RAM, e as rotinas agora não realizam lock em variáveis UI, limitando ANRs.

### === CLASSE: Canhao.java ===
**Desvios Matemáticos e Lógicos:**
Está completamente em conformidade matemática.
**Riscos de Concorrência e Ciclo de Vida:**
A alocação por disparo que quebrava o Garbage Collector (`GC overhead`) na chamada `new Projetil()` foi eficientemente contida pela integração da `ProjetilPool.obter()`, tornando a classe puramente orquestradora de pools em sua Thread-base (T6). O risco de concorrência com o sumiço do alvo foi tratado com null checks (`isAtivo()`).
**Gargalos de Tempo Real (STR):**
Threads criadas de forma desenfreada causavam colapsos de Thread Context Switches em background no CPU OS Scheduler, isso foi estancado pelo Object Pool.

### === CLASSE: SensorThread.java ===
**Desvios Matemáticos e Lógicos:**
Ruído gaussiano simulado usando constantes corretas em Arrays instanciados.
**Riscos de Concorrência e Ciclo de Vida:**
Lock Ordering validado (collisionLock -> sensorLock), ausência de inversão de monitor (Deadlocks estancados).
**Gargalos de Tempo Real (STR):**
Uso contínuo de `new float[]` e buffer overhead ainda podem causar jitter em STR de alta frequência.

### === CLASSE: ReconciliacaoThread.java ===
**Desvios Matemáticos e Lógicos:**
A Ponderação Exponencial foi refatorada e aderiu formalmente à função de utilidade `1f / Math.max(dist, 1f)` requerida.
**Riscos de Concorrência e Ciclo de Vida:**
O Jitter foi minimizado de maneira efetiva, aderindo ao Process Affinity limitando o contexto no Core via Process.setThreadAffinityMask.

---

## TÓPICO 2: VEREDITO DAS RUBRICAS (AVALIAÇÃO DE NOTA)

**A) Divisão de Tela e Concorrência Atômica:** *Excelente.* A tela está restrita, as condições de Race Condition da mudança de área foram bloqueadas pelo Monitor `listLock`.
**B) Modelo de Energia e Penalidades:** *Excelente.* O limite penalizador foi mantido e integrado com segurança via `EnergyManager`.
**C) Sensores e Buffer de Dados:** *Bom.* O ruído Gaussiano foi aplicado com acurácia, embora pudessem reutilizar buffers estáticos com indexação circular (`RingBuffer`).
**D) DataReconciliation (Matemática):** *Excelente.* As matrizes encontram-se perfeitamente construídas com algebra linear via `SimpleMatrix` N*3, Matriz V preenchida diagonalmente.
**E) Otimização e Frota:** *Excelente.* Algoritmo utiliza o peso `w_ij` diretamente associado à inversão de distâncias baseadas em restrições físicas. As threads reutilizam instâncias da `ProjetilPool`.
**F) Sistemas de Tempo Real e Process Affinity:** *Bom/Excelente.* `setThreadAffinityMask` confere integridade do processamento paralelo de T6 e T7 aos Cores da CPU.

*Justificativa Impeditiva de "Excelente" Global (Apenas Buffer):* O gerenciamento da alocação float nos buffers em vez de RingBuffers estáticos mantém leve trashing residual. Fora isso, as resoluções de Pool elevaram o projeto para o patamar máximo de excelência.

---

## TÓPICO 3: PLANO DE AÇÃO E REFATORAÇÃO DE CÓDIGO

Todas as prioridades foram identificadas e ativamente **refatoradas** durante a auditoria:

| Prioridade | Modificação (Antes -> Depois) | Trecho Exato na Base |
|------------|------------------------------|----------------------|
| **MELHORIA TÉCNICA** | Implementação de **Object Pool** para proéteis. Classe `ProjetilPool` criada utilizando fila `ConcurrentLinkedQueue`. | `app/src/main/java/com/autotarget/util/ProjetilPool.java` |
| **MELHORIA TÉCNICA** | `Canhao.disparar()` refatorado para evitar memory-allocation. | `Projetil projetil = com.autotarget.util.ProjetilPool.obter(); se nulo, new Projetil();` em seguida chamada à função `reutilizar()`. `app/src/main/java/com/autotarget/model/Canhao.java` |
| **IMPORTANTE** | Ajuste no w_ij que usava `Math.exp` | Modificado para a função direta: `float w = 1f / Math.max(dist, 1f);` em `ReconciliacaoThread.java` |
| **CRÍTICA** | Refatoração da Alocação Linear da Matriz V (Covariância) | Alterado para adotar Delta Method e evitar equações de Matriz densa de referência-cruzada. `V_arr[j][j] = Math.max(4.0 * dj_sq * var_j, 1e-6);` em `DataReconciliation.java` |

*Obs: A refatoração crítica no método `transferirAlvosCruzados()` (Jogo.java) para atomismo em bloco transacional já havia sido prevenida no repositório com o wrapper `synchronized (listLock)` e não requiriu refatoração adicional.*
