# Relatório de Gap Analysis - AutoTarget (AV2)

Abaixo consta a varredura exaustiva nos códigos do projeto sob a lente de um Arquiteto de Software avaliando a nota máxima (Excelente) da AV2.

## 1. Divisão de Tela e Controle de Alvos
- **Status:** Adequado (Risco de Inconsistência e Testes Ausentes).
- **Análise:** O método `transferirAlvosCruzados()` em `Jogo.java` (linhas 731-766) está envolto nos blocos `synchronized(collisionLock)` e `synchronized(listLock)`, porém ele itera ativamente iterando sobre `alvosEsquerdo` e `alvosDireito` extraindo uma lista de elementos e adicionando em outra coleção (linhas 751-760). Embora `CopyOnWriteArrayList` seja _thread-safe_ contra `ConcurrentModificationException` nas leituras ativas, não impede que haja _stale state_ se as threads dos alvos alterarem suas coordenadas enquanto esse bloqueio grande ocorre.
- **Código para Correção (Refatoração de `transferirAlvosCruzados()`):**
```java
    private void transferirAlvosCruzados() {
        if (larguraTela <= 0) return;
        List<Alvo> moverParaDireita = new ArrayList<>();
        List<Alvo> moverParaEsquerda = new ArrayList<>();

        // Remover bloqueios globais iterando sob uma view atomicamente segura:
        for (Alvo alvo : alvosEsquerdo) {
            if (Lado.determinar(alvo.getX(), larguraTela) == Lado.DIREITO) {
                moverParaDireita.add(alvo);
            }
        }
        for (Alvo alvo : alvosDireito) {
            if (Lado.determinar(alvo.getX(), larguraTela) == Lado.ESQUERDO) {
                moverParaEsquerda.add(alvo);
            }
        }

        if (!moverParaDireita.isEmpty()) {
            alvosEsquerdo.removeAll(moverParaDireita);
            alvosDireito.addAll(moverParaDireita);
            for (Alvo alvo : moverParaDireita) liberarAlvo(alvo);
        }
        if (!moverParaEsquerda.isEmpty()) {
            alvosDireito.removeAll(moverParaEsquerda);
            alvosEsquerdo.addAll(moverParaEsquerda);
            for (Alvo alvo : moverParaEsquerda) liberarAlvo(alvo);
        }
    }
```

## 2. Modelo de Energia e Penalidade
- **Status:** Bom (Falha no Critério Excelente: Falta de Visualização Clara em Log/Gráfico).
- **Análise:** A penalidade em `Canhao.java` é recomputada dinamicamente: `fator = 1.0f + Math.max(0, N - L) * ALPHA_PENALIDADE;`. Contudo, os impactos gerados estão escondidos na rotina de registro textual em `Jogo.java` (`registrarMetricasEnergiaPenalidade()`), sem geração de gráficos ou relatórios de uso em UI.
- **Código para Correção (Em `BenchmarkActivity.java` ou em LogCat Dedicado para Plotter):**
```java
// Exposição dos dados via broadcast para ferramentas de profiling:
private void registrarMetricasEnergiaPenalidade() {
    Log.i("Autotarget-Plotter", "EnergiaEsq:" + energiaEsquerdo + ",EnergiaDir:" + energiaDireito + ",Intervalo:" + intervaloEsq);
}
```

## 3. Sensores Ruidosos + Reconciliação de Dados
- **Status:** Adequado (Risco de Bloqueio por SVD com Inversão Completa de Matriz O(N³)).
- **Análise:** `DataReconciliation.reconcile()` implementa exatamente a equação matricial requerida na linha 265 (`SimpleMatrix yHat = matY.minus(correction);`). Contudo, o recálculo do SVD (Singular Value Decomposition) na formação do "espaço nulo esquerdo" no método `computeLeftNullSpace` ocorre a cada 10 segundos, o que para múltiplos canhões leva a `SingularMatrixException` com fallback instável em `OLS` se `N < 4`.
- **Código para Correção (Cache do Espaço Nulo ou Abortar se N pequeno em `DataReconciliation.java`):**
```java
    // Em DataReconciliation.java, antes de instanciar matrizes vazias:
    if (A_arr == null || A_arr.length == 0 || y_arr.length < 3) {
        Log.w(TAG, "Cenário não reconciliável, caindo para estimativas simples.");
        return estimarPosicoesDiretas(canhoesX, canhoesY, mediaDistancias);
    }
```

## 4. Otimização (mover/adicionar/remover)
- **Status:** Excelente.
- **Análise:** O método `calcularUtilidade` em `DataReconciliation.java` (linha 358) formula a utilidade de N canhões em cima das distâncias atenuadas `Math.exp(-beta * distancias[i][j])`. Em `ReconciliacaoThread.java`, o algoritmo estrito usa gradiente guloso decidindo atuar e enviando _callbacks_ ao motor principal de forma *Thread-Safe*. Funcionalidade extremamente alinhada com o modelo acadêmico.

## 5. Tempo real: Tarefas, RMA e Afinidade (Grafo de Threads)
- **Status:** Bom (Falha Gráfica).
- **Análise:** O projeto preenche a tabela no console (`RMAAnalysis.java`) e usa Affinity em `ThreadAffinityHelper.trySetAffinityPreferProcessApi`. No entanto, a rubrica dita explicitamente _"gráficos + discussão de gargalos"_. O `BenchmarkActivity.java` plota Speedup mas falha em renderizar gráficos de Jitter ou Misses de RMA em UI de forma visual.
- **Código para Correção (No Canvas do `BenchmarkChartView` em `BenchmarkActivity.java`):**
```java
        // Dentro de BenchmarkChartView.onDraw
        void setData(List<BenchmarkSample> data) {
            this.data = data;
            invalidate();
        }

        // Após plotar barras, pintar Deadline Misses como Alerta:
        paintTempo.setColor(s.deadlineMiss ? Color.RED : Color.parseColor("#E94560"));
        canvas.drawRect(tx, bottom - tempoH, tx + barW, bottom, paintTempo);
```
