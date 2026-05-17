### TÓPICO 1: NOTAS E VEREDITO (DIAGNÓSTICO)

1.  **Sensores ruidosos + buffers (10%)**: **Bom**
    *Justificativa*: A aplicação de ruído estocástico na classe `SensorThread` está matematicamente correta (proporcional, via N(0, 0.05)), mas falhou no requisito absoluto de aplicar a janela restrita no buffer. O código anterior não bloqueava os cálculos se o buffer tivesse menos de 10 itens, contentando-se com `size() >= 2`. Para o nível excelente, foi exigido impor estritamente o limite `TAMANHO_HISTORICO`.
2.  **Reconciliação de dados (25%)**: **Adequado**
    *Justificativa*: O modelo EJML `matY.minus(matV.mult(At).mult(AVAt_inv).mult(matA).mult(matY))` na classe `DataReconciliation` foi perfeitamente transposto em relação à fórmula. A matriz A (Incidência) e V (Covariância) são montadas corretamente. Porém, para obter o nível Excelente, foi requerida a "validação quantitativa (erro/variância)", enquanto a implementação atual apenas extraía o Erro RMS no log.
3.  **Otimização (mover/adicionar/remover) (15%)**: **Excelente**
    *Justificativa*: Toda a estratégia competitiva está muito robusta. Os canhões estão sendo inseridos com "thread-safety" (uso de locks apropriados e arrays seguras como `CopyOnWriteArrayList` no `Jogo`). A formulação utilidade `calcularUtilidade()` respeita rigorosamente a penalidade marginal multiplicativa `(1 + (N - 5) * 0.2)` na taxa de disparo. O centroide e o `moverPara` ocorrem graduais como exigido.
4.  **Tempo real: tarefas e análise (20%)**: **Bom**
    *Justificativa*: A arquitetura STR usando threads periódicas (Timers ou Executors) é sólida, mas faltou o mapeamento expresso aos processadores físicos via `ThreadAffinityMask()`. Em STR modernos para dispositivos (como ARM big.LITTLE), deixar o escalonador Linux gerenciar T8 (Cálculo Pesado) e T4 (Render/Física) aleatoriamente vai gerar "cache misses" inter-clusters.

---

### TÓPICO 2: RESULTADO DO CHECKLIST E RED FLAGS

*   [x] O ruído gaussiano (~5%) está aplicado corretamente na geração de dados?
*   [ ] O buffer respeita as 10 leituras mínimas antes de liberar o cálculo de média/variância? (**RED FLAG: Não exigia 10 no buffer.**)
*   [x] A fórmula $\hat{y} = y - V A^T (A V A^T)^{-1} A y$ está montada corretamente?
*   [x] O cálculo do centroide usa as distâncias *reconciliadas* e a movimentação do canhão é progressiva?
*   [x] A criação/destruição de threads de canhões na Função de Utilidade é thread-safe?
*   [ ] A rotina pesada (Reconciliação) está adequadamente jogada para background, protegendo a UI Thread contra Application Not Responding (ANR)? (**RED FLAG: Falta `setThreadAffinityMask`.**)

---

### TÓPICO 3: PLANO DE AÇÃO E CÓDIGO (RUMO AO EXCELENTE)

Foram aplicadas todas as alterações e fixes de forma a adequar o projeto completamente à rubrica:

1. **Janela Restrita do Buffer:** Correção nos métodos de matriz da `SensorThread` forçando exigência das 10 coletas:
```java
if (dado == null || dado.historicoDistancias.size() < TAMANHO_HISTORICO) return null;
```
Além de atualizados os testes automatizados da suíte.

2. **Avaliação Quantitativa de Variância (Rumo ao Excelente):** Implementado o método de variância em `DataReconciliation.java`:
```java
public static double calcularVariancia(double[] valores) { ... }
```
E inserida a logométrica na `ReconciliacaoThread.java`:
```java
double varAntes = DataReconciliation.calcularVariancia(brutasDouble);
double varDepois = DataReconciliation.calcularVariancia(reconDouble);
Log.i(TAG, "varAntes=" + varAntes + ", varDepois=" + varDepois);
```

3. **Escalonamento Hard Real-Time (Evitando Gargalos e Preempção Injusta):**
A API do Android/JNI mapeou threads explicitamente:
- **T8 (Reconciliação)** jogada no cluster LITTLE_CORES (na Thread `run()` da `ReconciliacaoThread`).
- **T1 (Motor Físico)** e **T4 (Motor Render)** ancoradas no cluster BIG_CORES (na `GameSurfaceView` e no ExecutorService do `Jogo`).
```java
com.autotarget.util.ThreadAffinityHelper.trySetAffinityPreferProcessApi(
        android.os.Process.myTid(), com.autotarget.util.ThreadAffinityHelper.BIG_CORES);
```
