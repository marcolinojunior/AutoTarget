# AV2 - Correções Implementadas no AutoTarget

## 1) Sensores, buffers e coleta por lado

- A `SensorThread` foi refeita para coletar dados **separadamente por território** (`ESQUERDO` e `DIREITO`).
- O histórico de distâncias agora usa **janela deslizante real de 10 segundos** (1 amostra/segundo, máximo de 10 snapshots por lado).
- As leituras agora incluem:
  - posição ruidosa `x/y`,
  - velocidade escalar ruidosa,
  - **componentes `vx/vy` ruidosos**.
- Foram adicionados getters por lado para uso na reconciliação e otimização.

## 2) Reconciliação de dados (matriz A por geometria de limiar)

- A classe `DataReconciliation` passou a construir `A` por conectividade geométrica:
  - `construirMatrizIncidenciaPorLimiar(float[] mediaDistancias, double limiar)`.
- O limiar é calculado dinamicamente a partir das distâncias médias do alvo.
- Se não houver conectividade válida, o sistema usa fallback para a estratégia anterior por espaço nulo (evita falha numérica).

## 3) Otimização e decisão de canhões por lado

- A `ReconciliacaoThread` foi reestruturada para operar em **ambos os lados**:
  - pressão tática por lado,
  - decisão de adicionar/remover por utilidade (`U(N)`, `U(N+1)`, `U(N-1)`),
  - realocação por clusters (tipo K-Means) por lado.
- A reconciliação agora roda por território com dados do respectivo buffer.

## 4) Tempo real e escalabilidade (RMA + benchmark)

- `RMAAnalysis` foi expandida com:
  - tabela de tarefas com `Pi, Ci, Di, Ji`,
  - dependências entre tarefas,
  - cálculo de WCRT e métricas de execução observadas,
  - relatório CSV textual com tempos de resposta e misses.
- `BenchmarkActivity` foi modernizada com:
  - gráfico comparativo embutido (speedup x tempo relativo),
  - uso das métricas de resposta coletadas,
  - resumo com tabela de tarefas + grafo de dependências.

## 5) Afinidade de CPU

- `ThreadAffinityHelper` agora tenta usar `Process.setThreadAffinityMask()` por reflexão e mantém fallback JNI nativo.
- O benchmark foi ajustado para usar a nova estratégia preferencial.

## 6) Correções estruturais de projeto

- Classe adicionada: `ReconciliationLog` (auditoria de reconciliação, IA, tiros e relatório final).
- Classe adicionada: `ReconciliationReportActivity` (tela para exibir o relatório textual ao final da partida).
- Essas classes removem referências quebradas de compilação existentes no fluxo principal.

## 7) Testes adicionados/atualizados

- `DataReconciliationTest`:
  - validação da construção de matriz de incidência por limiar,
  - caso sem conectividade.
- Novo `RMAAnalysisTest`:
  - validação de coleta de métricas de runtime,
  - validação de resumo de tarefas/dependências.

## 8) Execução de testes no ambiente

Foi tentada a execução de:

```bash
gradlew.bat test --no-daemon --console=plain
```

No ambiente atual, a execução foi bloqueada por ausência de `pwsh.exe` (dependência do runtime de shell), impedindo a validação automatizada local nesta sessão.
