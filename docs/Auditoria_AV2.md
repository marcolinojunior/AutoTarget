# Auditoria Completa — Implementação AV2 do AutoTarget

> **Data:** 18/05/2026  
> **Escopo:** Todos os arquivos-fonte da AV2 (engine, model, service, util, ui)  
> **Referência:** `Especificacao_Projeto_AutoTarget_AV2.md` e `Checklist_Agente_IA_AutoTarget.md`

---

## Legenda de Severidade

| Nível | Significado |
|---|---|
| 🔴 **CRÍTICO** | Bug que causa crash, deadlock, perda de dados ou violação de requisito rubrica peso ≥15% |
| 🟠 **ALTO** | Lógica incorreta que mascara resultados, produz métricas erradas ou pode falhar em edge cases |
| 🟡 **MÉDIO** | Feature parcialmente implementada, código frágil, ou violação de boas práticas |
| 🔵 **BAIXO** | Melhoria de qualidade, legibilidade, ou robustez não-essencial |

---

## 🔴 CRÍTICO — 6 Problemas

### C1. `logShot` conta disparos com `hit=N` em vez de `hit=Y` (invertido desde a origem)

**Arquivo:** `ReconciliationLog.java`  
**Linhas:** 237-245  
**Código atual:**
```java
if (!hit) {  // hit=false significa ERRO
    totalShots++;
    ...
} else {     // hit=true significa ACERTO
    totalHits++;
    ...
}
```

**Teoria:** O parâmetro `boolean hit` é `true` quando o projétil acertou o alvo (ver `Projetil.java:249` → `logShot(..., true, ...)`). O código atual incrementa `totalShots` quando `!hit` (erro) e `totalHits` quando `hit` (acerto). Isso significa que `totalShots` na verdade conta **apenas os erros**, e `totalHits` conta **apenas os acertos**. O "Disparos" do relatório é `totalShots + totalHits`, que até soma corretamente o total, mas os campos individuais estão semanticamente invertidos. Qualquer análise que use `totalShots` isoladamente (ex: "tiros que erraram") estará errada.

**Impacto na rubrica:** Peso 15% — "Divisão de tela, pertencimento e placar". O placar de disparos por lado está incorreto se usado isoladamente.

**Status:** ✅ Corrigido na refatoração anterior (agora `totalShots` conta todos os disparos, `totalHits` conta apenas acertos).

---

### C2. `reconciliarAlvo` e `computeLeftNullSpace` não recebiam `sufixoLado` — gráfico de condicionamento vazio

**Arquivo:** `DataReconciliation.java`  
**Linhas:** 293, 527  
**Problema:** Após a refatoração que adicionou `sufixoLado` ao método `reconciliar(..., String lado)`, os métodos internos `reconciliarAlvo` e `computeLeftNullSpace` não recebiam esse parâmetro. O `logConditioning` dentro deles usava `"RECON_ALVO_INSTAVEL" + sufixoLado` e `"SVD_TIMEOUT" + sufixoLado`, mas `sufixoLado` não existia no escopo desses métodos.

**Teoria:** Java é estaticamente tipado — variáveis locais não "vazam" entre métodos. O `sufixoLado` era calculado no método `reconciliar` mas nunca passado para `reconciliarAlvo` nem para `computeLeftNullSpace`. Isso causava erro de compilação (`cannot find symbol: variable sufixoLado`).

**Impacto na rubrica:** Peso 25% — "Reconciliação de dados". Sem o sufixo, todos os `logConditioning` caíam no contexto `_GLOBAL`, impossibilitando separar métricas de condicionamento por lado no gráfico.

**Status:** ✅ Corrigido — adicionado parâmetro `sufixoLado` em `reconciliarAlvo` e `computeLeftNullSpace`.

---

### C3. `buildAdaptiveSummary` soma energias dos dois lados — mascara starvation

**Arquivo:** `ReconciliationReportActivity.java`  
**Linhas:** ~340-350 (versão anterior)  
**Problema:** O diagnóstico calculava `energiaInicial = energiaEsq + energiaDir` (100+100=200) e `energiaFinal = energiaEsq + energiaDir` (ex: 4.3+0=4.3). Isso produzia "Energia inicial/final: 200.0 -> 4.3", mascarando que o lado direito morreu completamente (E=0, canhões=0).

**Teoria:** Em um sistema competitivo com recursos independentes por lado, agregar métricas de recursos em um "pool" global destrói a capacidade de diagnóstico. Se um lado tem 90% de eficiência e o outro 10%, a média de 50% não diz nada útil. O mesmo aplica-se à energia: a soma esconde assimetrias críticas.

**Impacto na rubrica:** Peso 15% — "Modelo de energia e penalidade". O diagnóstico não detecta starvation de um lado.

**Status:** ✅ Corrigido — `buildAdaptiveSummary` reescrito para avaliar cada lado independentemente.

---

### C4. `logAIDecision` não recia o lado — decisões de IA sempre globais

**Arquivo:** `ReconciliationLog.java`  
**Linhas:** 251-254  
**Problema:** O método `logAIDecision(String acao, String motivo, ...)` não recebia o parâmetro `lado`. As chamadas em `ReconciliacaoThread.java` (linhas 201, 316, 336) passavam o lado apenas dentro da string `motivo` (ex: "Pressão tática ESQUERDO"). Sem parsing do motivo, `totalAdditionsEsq` e `totalAdditionsDir` eram sempre 0.

**Teoria:** A decisão de adicionar/remover canhões é feita por lado (a IA avalia cada lado independentemente). Se o log não separa por lado, o relatório "IA adicionou: ESQ=0 DIR=0" é sempre exibido, mesmo quando decisões foram tomadas. Isso viola o requisito de "evidência de otimização" da rubrica.

**Impacto na rubrica:** Peso 15% — "Otimização (mover/adicionar/remover)". Sem separação por lado, impossível evidenciar a função de utilidade.

**Status:** ✅ Corrigido — adicionado parsing via `extrairLadoDoMotivo()` e inclusão de `lado.name()` no motivo das chamadas.

---

### C5. `erroPos` é `double` mas `Entry` do MPAndroidChart espera `float`

**Arquivo:** `ReconciliationReportActivity.java`  
**Linhas:** 289, 291  
**Código com erro:**
```java
entriesEsq.add(new Entry(idxEsq++, s.erroPos));  // erroPos é double
```

**Teoria:** `ReconSample.erroPos` é declarado como `double` (linha 44 de `ReconciliationLog.java`). O construtor `Entry(float, float)` do MPAndroidChart espera `float`. Java não faz narrowing conversion implícita de `double` para `float` — isso é erro de compilação (`possible lossy conversion`).

**Impacto:** Impossibilita build do app. Gráfico de erro posicional nunca é renderizado.

**Status:** ✅ Corrigido — adicionado cast explícito `(float) s.erroPos`.

---

### C6. `computeLeftNullSpace` é `public` mas chamado com assinatura incompatível após refatoração

**Arquivo:** `DataReconciliation.java`  
**Linha:** 527  
**Problema:** O método `computeLeftNullSpace` era `public` (possivelmente usado em testes). Após adicionar o parâmetro `sufixoLado`, qualquer caller externo com a assinatura antiga (`computeLeftNullSpace(M, N)`) falharia em compilar.

**Teoria:** Mudar a assinatura de um método público é uma breaking change. Se houver testes unitários ou outros callers, eles precisam ser atualizados.

**Impacto:** Erro de compilação em qualquer caller com assinatura antiga.

**Status:** ✅ Corrigido — verificado que há apenas 1 caller (dentro do próprio `reconciliar`), que foi atualizado.

---

## 🟠 ALTO — 8 Problemas

### H1. `processarAlvosInativos` usa `getLadoAbate()` que pode ser `null` — alvo transferido no frame exato do abate não conta ponto

**Arquivo:** `Jogo.java`  
**Linhas:** 978-985  
**Código:**
```java
if (alvo.getLadoAbate() == ladoSendoProcessado) {
    restaurarEnergiaPorAbate(alvo, ladoSendoProcessado);
    abatesConfirmadosLado++;
}
```

**Teoria:** O `ladoAbate` é setado no momento do impacto do projétil (`Alvo.tentarAbater(lado)` em `Projetil.java:249`). Porém, entre o impacto e a verificação de colisão pelo `PhysicsTimer`, o alvo pode ter cruzado a linha divisória (transferido para o outro lado pelo `transferirAlvosCruzados()`). Nesse caso, o `ladoAbate` registra o lado **do canhão que atirou**, mas o alvo agora pertence ao **outro lado**. A condição `getLadoAbate() == ladoSendoProcessado` falha, e o ponto não é contabilizado para ninguém — **o abate é perdido**.

**Impacto na rubrica:** Peso 15% — "Divisão de tela, pertencimento e placar". Abates podem ser perdidos em edge cases de transferência.

**Sugestão de correção:** Contabilizar o ponto para o lado registrado em `ladoAbate` independentemente de qual lado está processando, OU garantir que a transferência de alvos limpe o `ladoAbate` e reatribua.

---

### H2. `transferirAlvosCruzados` não atualiza `ladoAbate` — inconsistência após transferência

**Arquivo:** `Jogo.java`  
**Linhas:** 940-960  
**Código:**
```java
if (!transferBufferDireita.isEmpty()) {
    alvosEsquerdo.removeAll(transferBufferDireita);
    alvosDireito.addAll(transferBufferDireita);
    for (int i = 0; i < transferBufferDireita.size(); i++) liberarAlvo(transferBufferDireita.get(i));
}
```

**Teoria:** Quando um alvo cruza a linha, ele é removido de uma lista e adicionado à outra. Porém, se o alvo já tinha um `ladoAbate` setado (de um impacto que ocorreu antes da transferência ser processada), esse valor fica stale. Além disso, a reserva (`reservasAlvos`) é liberada, mas o alvo pode já ter sido destruído — a ordem de operações entre `transferirAlvosCruzados()` e `verificarColisões()` dentro do mesmo `physicsTask` pode causar race conditions.

**Impacto:** Pontos podem ser atribuídos ao lado errado ou perdidos quando alvos cruzam a linha no momento do abate.

---

### H3. `reservarAlvo` bloqueia alvos nos primeiros 10s — canhões não atiram no início

**Arquivo:** `Jogo.java`  
**Linhas:** 670-675  
**Código:**
```java
if (estado == Estado.RODANDO && tempoRestante > (DURACAO_PARTIDA_SEGUNDOS - 10)) {
    continue;  // Pula todos os alvos
}
```

**Teoria:** Nos primeiros 10s de partida, nenhum alvo pode ser reservado. Isso significa que todos os canhões ficam ociosos durante esse período, mesmo que hajam alvos na tela. O comentário diz "durante os primeiros 10s os canhões aguardam dados mínimos de reconciliação antes de gastar tiros", mas isso viola o requisito de que os canhões devem atirar assim que ativados. Além disso, `tempoRestante > 50` (60-10=50) — se o jogo já rodou 11s, `tempoRestante=49` e o bloqueio é liberado, mas os alvos que surgiram nos primeiros 10s já estão "velhos" e podem ter sido destruídos por outros meios.

**Impacto na rubrica:** Peso 15% — "Divisão de tela, pertencimento e placar". Canhões perdem tempo valioso de partida sem atirar, reduzindo artificialmente a pontuação.

---

### H4. `calcularPontosAbate` dá 0 pontos para abates >7s — penalidade excessiva

**Arquivo:** `Jogo.java`  
**Linhas:** 1007-1013  
**Código:**
```java
private int calcularPontosAbate(Alvo alvo) {
    long idadeMs = alvo.getIdadeMs();
    if (idadeMs < 2000) return 5;
    if (idadeMs < 4000) return 3;
    if (idadeMs < 7000) return 1;
    return 0; // Penalidade máxima: 0 pontos
}
```

**Teoria:** Com spawn a cada 3s e alvos que se movem pela tela, é comum um alvo levar mais de 7s para ser abatido (especialmente se cruza a linha, se o canhão erra o primeiro tiro, ou se o alvo é rápido). Dar 0 pontos efetivamente ignora o abate — o jogador gastou energia e munição para nada. Isso pode distorcer a análise de eficiência energética.

**Impacto na rubrica:** Peso 15% — "Modelo de energia e penalidade". A métrica "energia por abate" fica artificialmente alta porque abates lentos não contam.

---

### H5. `calcularEnergiaRegenerada` com cap de 2.5f — modelo de energia pode criar "death spiral"

**Arquivo:** `Jogo.java`  
**Linhas:** 1023-1030  
**Código:**
```java
private float calcularEnergiaRegenerada(Alvo alvo) {
    if (idadeMs < 2000) return 2.5f;
    if (idadeMs < 4000) return 0.5f;
    if (idadeMs < 7000) return 0.2f;
    return 0.1f;
}
```

**Teoria:** O consumo é de 1f por canhão por segundo. Com 5 canhões, o consumo é 5f/s. Um abate ultra-rápido (<2s) regenera 2.5f, mas custou ~5f em energia durante esses 2s. O saldo líquido é negativo. Isso cria um "death spiral": quanto mais canhões, mais rápido a energia acaba, menos canhões ativos, menos abates, menos regeneração. O sistema converge para starvation inevitável. Embora isso possa ser intencional (escassez de recursos), o desequilíbrio é tão forte que o jogo se torna deterministicamente perdido para ambos os lados.

**Impacto na rubrica:** Peso 15% — "Modelo de energia e penalidade". O modelo pode ser considerado "incoerente" se leva a starvation inevitável independentemente da estratégia.

---

### H6. `ReconciliacaoThread` usa `Thread.sleep(INTERVALO_TATICO)` — deriva temporal acumulada

**Arquivo:** `ReconciliacaoThread.java`  
**Linhas:** 100-115  
**Código:**
```java
while (ativo) {
    Thread.sleep(INTERVALO_TATICO);  // 3000ms
    avaliarPressaoTatica(Lado.ESQUERDO);
    avaliarPressaoTatica(Lado.DIREITO);
    ciclosTaticos++;
    long agora = System.currentTimeMillis();
    if (agora - ultimoCicloReconciliacaoMs >= INTERVALO_RECONCILIACAO) {
        executarCiclo();
        ultimoCicloReconciliacaoMs = agora;
    }
}
```

**Teoria:** O `Thread.sleep(3000)` garante no mínimo 3s entre iterações, mas o tempo de execução de `avaliarPressaoTatica` (que acessa listas, calcula distâncias) é adicionado ao sleep. Se cada iteração leva 50ms, após 100 iterações a deriva é de 5s. Para STR (Sistemas de Tempo Real), isso é jitter não-controlado. O `RMAAnalysis.checkDeadline` mede o tempo de execução mas não compensa a deriva no próximo sleep.

**Impacto na rubrica:** Peso 20% — "Tempo real: tarefas e análise". O jitter acumulado invalida a análise de Rate Monotonic se os períodos reais divergirem significativamente dos especificados.

---

### H7. `RMAAnalysis.checkDeadline` é chamado mas o resultado não é usado para escalonamento

**Arquivo:** Múltiplos arquivos  
**Problema:** O `RMAAnalysis` mede tempos de execução e verifica deadlines, mas os resultados são apenas logados. Não há mecanismo de feedback que ajuste prioridades, suspenda tarefas, ou reporte violações de deadline ao usuário de forma estruturada.

**Teoria:** A análise de escalonabilidade (Rate Monotonic) requer não apenas medir, mas **documentar** os tempos de resposta máximos (Ri) e comparar com deadlines (Di). O checklist da AV2 exige "tabela coerente + análise" e "medições reais + gráficos + discussão de gargalos". Atualmente, os dados são logados mas não agregados em relatório.

**Impacto na rubrica:** Peso 20% — "Tempo real: tarefas e análise". Sem relatório estruturado de RMA, o critério "Excelente" não pode ser atingido.

---

### H8. `DataReconciliation.reconcile` (estático) não recebe `lado` — chamadas de teste sem contexto

**Arquivo:** `DataReconciliation.java`  
**Linhas:** 396-441  
**Problema:** O método `public static double[] reconcile(double[] y, double[][] V, double[][] A)` é a assinatura exigida pela rubrica, mas não recebe o parâmetro `lado`. Quando chamado diretamente (ex: testes unitários), o `logConditioning` usa contexto genérico sem identificação de lado.

**Teoria:** O método estático `reconcile` é a "assinatura contratual" da rubrica. Ele funciona corretamente para a álgebra linear, mas não integra com o sistema de logging por lado. Isso significa que chamadas diretas (testes, uso futuro) não produzem evidência separada por lado.

**Impacto:** Baixo em produção (o método principal usado é o `reconciliar` de instância), mas pode confundir a análise se testes forem executados durante a partida.

---

## 🟡 MÉDIO — 6 Problemas

### M1. `spawnarAlvo` spawna 4 alvos simultaneamente — pode sobrecarregar o sistema

**Arquivo:** `Jogo.java`  
**Linha:** 810  
**Código:** `int quantidadeSpawns = 4;`

**Teoria:** Spawns 4 alvos a cada 3s (inicialmente) significa ~1.33 alvos/segundo. Com 60s de partida, isso gera potencialmente ~80 alvos simultâneos. Cada alvo é uma `Thread` independente. O overhead de contexto-switching de 80+ threads pode degradar performance em dispositivos Android de baixo custo.

**Impacto:** Pode causar lag ou ANR em dispositivos lentos.

---

### M2. `aplicarPenalidade` usa `Math.max(0, total - LIMIAR) * 0.2f` — fator aditivo, não multiplicativo

**Arquivo:** `Canhao.java`  
**Linha:** 215  
**Código:**
```java
this.intervaloDisparo = (int) (INTERVALO_DISPARO_BASE * (1.0f + Math.max(0, total - LIMIAR_PENALIDADE) * ALPHA_PENALIDADE));
```

**Teoria:** A especificação diz "o intervalo de disparo é multiplicado por um fator `(1 + (n - L) * 0.2)`". O código implementa exatamente isso: `I = I_base * (1 + max(0, N-5) * 0.2)`. Para N=6: `I = I_base * 1.2`. Para N=10: `I = I_base * 2.0`. Isso está correto. Porém, o valor de `ALPHA_PENALIDADE` não foi verificado — se for diferente de 0.2, a implementação diverge da especificação.

**Impacto:** Depende do valor da constante. Se `ALPHA_PENALIDADE = 0.2`, está correto.

---

### M3. `semearCanhoesIniciais` não é chamado no início da partida se já houver canhões

**Arquivo:** `ReconciliacaoThread.java`  
**Linhas:** 190-205  
**Código:**
```java
if (nCanhoes == 0) {
    semearCanhoesIniciais(lado);
    return;
}
```

**Teoria:** Se o jogador adiciona canhões manualmente antes de iniciar a partida, `nCanhoes > 0` e `semearCanhoesIniciais` nunca é chamado. Isso é correto. Porém, se o jogador não adiciona nenhum canhão, a IA semeia canhões iniciais apenas no primeiro ciclo tático (após 3s de sleep). Há um "gap" de 3s onde o lado não tem canhões.

**Impacto:** Pequeno — 3s sem canhões no início da partida.

---

### M4. `buildAdaptiveSummary` não usa `performanceMetricsSamples`

**Arquivo:** `ReconciliationReportActivity.java`  
**Problema:** O `ReconciliationLog` coleta `PerformanceMetricsSample` (disparos, acertos, taxa, energia consumida, canhões ativos por lado), mas o `buildAdaptiveSummary` não os utiliza. O diagnóstico depende apenas de `energySamples`, `reconSamples`, `utilitySamples` e `conditioningSamples`.

**Impacto:** O diagnóstico não considera a taxa de acerto real por lado, que é uma métrica crítica para avaliar eficiência.

---

### M5. Gráfico de barras MSE usa janelas temporais — amostras de lados diferentes na mesma janela são médias juntas

**Arquivo:** `ReconciliationReportActivity.java`  
**Problema:** No `setupReconChart`, cada "slot" no eixo X é uma janela temporal. Se uma janela contém 3 amostras do lado esquerdo e 1 do direito, a barra do lado esquerdo mostra a média de 3 amostras e a do direito mostra 1 amostra. Isso pode criar barras com alturas muito diferentes que não refletem a evolução temporal real.

**Impacto:** Visualmente confuso — a barra de um lado pode "pular" entre slots dependendo da distribuição de amostras.

---

### M6. `ReconciliationReportActivity` não trata rotação de tela — estado perdido

**Arquivo:** `ReconciliationReportActivity.java`  
**Problema:** A Activity recebe o relatório via `Intent.EXTRA_REPORT` e cria views dinamicamente. Se o usuário rotacionar a tela, a Activity é recriada, o Intent é reprocessado, e as views são recriadas. Porém, os gráficos do MPAndroidChart podem perder estado de zoom/scroll.

**Impacto:** UX — ao rotacionar, os gráficos voltam ao estado inicial.

---

## 🔵 BAIXO — 4 Problemas

### B1. `Jogo.java` tem import duplicado de `AtomicInteger`

**Arquivo:** `Jogo.java`  
**Linhas:** 51 e 53  
**Código:**
```java
import java.util.concurrent.atomic AtomicInteger;  // linha 51
...
import java.util.concurrent.atomic AtomicInteger;  // linha 53
```

**Impacto:** Nenhum (compila normalmente), mas indica falta de organização.

---

### B2. `ReconciliationLog` é singleton com estado mutável — não é thread-safe para acesso concorrente de UI

**Arquivo:** `ReconciliationLog.java`  
**Problema:** O singleton é acessado tanto pelas threads de jogo (sensor, reconciliação, physics) quanto pela UI thread (ao gerar relatório). Embora os métodos sejam `synchronized`, a iteração sobre listas durante `gerarRelatorio()` pode ser interrompida por modificações concorrentes se o jogo ainda estiver rodando.

**Impacto:** Possível `ConcurrentModificationException` se o relatório for gerado enquanto o jogo está ativo.

---

### B3. `setupReconChart` com `groupBars` pode ter barras sobrepostas se houver poucos dados

**Arquivo:** `ReconciliationReportActivity.java`  
**Problema:** Se houver apenas 1-2 slots de dados, o `groupBars(0f, 0.30f, 0.05f)` com `barWidth=0.30f` pode causar sobreposição visual.

**Impacto:** Visual — barras podem parecer sobrepostas com poucos dados.

---

### B4. `DataReconciliation.reconcile` estático não tem logging de lado

**Arquivo:** `DataReconciliation.java`  
**Linhas:** 396-441  
**Problema:** O método estático `reconcile(y, V, A)` é a assinatura exigida pela rubrica, mas não recebe `lado`. Chamadas de teste não produzem evidência separada por lado.

**Impacto:** Baixo — o método principal usado em produção é o `reconciliar` de instância.

---

## Resumo Quantitativo

| Severidade | Quantidade |
|---|---|
| 🔴 Crítico | 6 (todos corrigidos) |
| 🟠 Alto | 8 |
| 🟡 Médio | 6 |
| 🔵 Baixo | 4 |
| **Total** | **24** |

---

## Recomendações Prioritárias

1. **Corrigir H1+H2 (ladoAbate + transferência):** Garantir que abates de alvos transferidos sejam contabilizados corretamente. Isso requer coordenar a ordem de `transferirAlvosCruzados()` e `verificarColisões()` dentro do physics timer.

2. **Revisar H3 (bloqueio de 10s):** Avaliar se o bloqueio de disparo nos primeiros 10s é realmente necessário. Se for para aguardar dados de reconciliação, considerar um mecanismo mais granular (bloquear por alvo, não globalmente).

3. **Revisar H4+H5 (modelo de energia):** Simular o modelo matematicamente para verificar se o "death spiral" é intencional. Se não for, ajustar a curva de regeneração para que abates médios (0.5f) compensem parcialmente o consumo.

4. **Implementar relatório RMA estruturado (H7):** Agregar os dados de `RMAAnalysis` em um relatório formatado com tabela de tarefas, tempos de resposta, e verificação de escalonabilidade.

5. **Adicionar `PerformanceMetricsSample` ao diagnóstico (M4):** Incluir taxa de acerto por lado no `buildAdaptiveSummary`.
