# Verificacao de requisitos AV2 - AutoTarget

## Escopo
- Codigo analisado: app/src/main/java e activity_main.xml.
- Foco: requisitos do documento AV2 (telas, energia, sensores, reconciliacao, otimizacao, tempo real).

## Resumo geral
- Atende bem os itens de divisao de tela, energia/penalidade e controle de canhoes por lado.
- Reconcilia dados e faz otimizacao, mas ha pontos de aderencia parcial (buffers por 10s reais, matriz A por geometria de limiar, sensores por lado, vx/vy).
- Analise de tempo real existe (RMA), mas faltam jitter, grafo de dependencias, medicao de tempos de resposta por tarefa e graficos comparativos.

## Checklist por requisito

### a) Divisao de tela e controle de alvos
- Linha vertical central e campos separados: ATENDE. Evidencia: [app/src/main/java/com/autotarget/engine/GameSurfaceView.java](app/src/main/java/com/autotarget/engine/GameSurfaceView.java#L357)
- Listas de canhoes por lado + canhoes atiram apenas em alvos do mesmo lado: ATENDE. Evidencia: [app/src/main/java/com/autotarget/engine/Jogo.java](app/src/main/java/com/autotarget/engine/Jogo.java#L146), [app/src/main/java/com/autotarget/engine/Jogo.java](app/src/main/java/com/autotarget/engine/Jogo.java#L533)
- Transferencia atomica ao cruzar linha: ATENDE (lock dedicado). Evidencia: [app/src/main/java/com/autotarget/engine/Jogo.java](app/src/main/java/com/autotarget/engine/Jogo.java#L712), [app/src/main/java/com/autotarget/engine/Jogo.java](app/src/main/java/com/autotarget/engine/Jogo.java#L713)
- Placar e vencedor no fim da partida: ATENDE (HUD + dialogo). Evidencia: [app/src/main/java/com/autotarget/engine/GameSurfaceView.java](app/src/main/java/com/autotarget/engine/GameSurfaceView.java#L409), [app/src/main/java/com/autotarget/engine/GameSurfaceView.java](app/src/main/java/com/autotarget/engine/GameSurfaceView.java#L463), [app/src/main/java/com/autotarget/MainActivity.java](app/src/main/java/com/autotarget/MainActivity.java#L231), [app/src/main/java/com/autotarget/engine/Jogo.java](app/src/main/java/com/autotarget/engine/Jogo.java#L387)
- Melhorar para excelente: documentar testes de borda (alvo exatamente na linha, troca simultanea, alvo cruzando durante tiro).

### b) Modelo de recursos e penalidades
- Energia inicial por lado e consumo por canhao por segundo: ATENDE. Evidencia: [app/src/main/java/com/autotarget/engine/Jogo.java](app/src/main/java/com/autotarget/engine/Jogo.java#L256), [app/src/main/java/com/autotarget/engine/Jogo.java](app/src/main/java/com/autotarget/engine/Jogo.java#L442)
- Energia em tempo real e canhoes param ao zerar energia: ATENDE. Evidencia: [app/src/main/java/com/autotarget/engine/Jogo.java](app/src/main/java/com/autotarget/engine/Jogo.java#L442), [app/src/main/java/com/autotarget/engine/Jogo.java](app/src/main/java/com/autotarget/engine/Jogo.java#L447)
- Penalidade por excesso de canhoes: ATENDE (I = I_base * (1 + max(0, N-L) * 0.2)). Evidencia: [app/src/main/java/com/autotarget/engine/Jogo.java](app/src/main/java/com/autotarget/engine/Jogo.java#L595), [app/src/main/java/com/autotarget/model/Canhao.java](app/src/main/java/com/autotarget/model/Canhao.java#L261)
- Melhorar para excelente: adicionar logs/metricas de impacto da penalidade (antes/depois) para evidenciar ganho/perda.

### c) Sensores simulados e coleta de dados
- Ruido gaussiano proporcional (5%): ATENDE. Evidencia: [app/src/main/java/com/autotarget/service/SensorThread.java](app/src/main/java/com/autotarget/service/SensorThread.java#L82), [app/src/main/java/com/autotarget/service/SensorThread.java](app/src/main/java/com/autotarget/service/SensorThread.java#L172)
- Buffer com pelo menos 10 leituras por alvo: PARCIAL. Evidencia: [app/src/main/java/com/autotarget/service/SensorThread.java](app/src/main/java/com/autotarget/service/SensorThread.java#L67), [app/src/main/java/com/autotarget/service/SensorThread.java](app/src/main/java/com/autotarget/service/SensorThread.java#L200)
  - Existe burst de 10 amostras, mas nao e um buffer de 10s reais; o historico e recriado em cada coleta.
- Leituras de velocidade (vx, vy): NAO ATENDE (usa apenas velocidade escalar). Evidencia: [app/src/main/java/com/autotarget/service/SensorThread.java](app/src/main/java/com/autotarget/service/SensorThread.java#L170), [app/src/main/java/com/autotarget/service/SensorThread.java](app/src/main/java/com/autotarget/service/SensorThread.java#L174)
- Coleta por lado (somente alvos do proprio territorio): PARCIAL (coleta global de todos os alvos). Evidencia: [app/src/main/java/com/autotarget/service/SensorThread.java](app/src/main/java/com/autotarget/service/SensorThread.java#L145)
- Melhorar para excelente:
  - Manter janela deslizante real de 10s (1 leitura/segundo) por alvo.
  - Armazenar vx e vy (componentes) alem de velocidade escalar.
  - Separar buffers por lado (esquerdo/direito).

### d) Reconciliacao de dados para otimizacao de posicionamento
- Metodo reconcile(double[] y, double[][] V, double[][] A) com a formula exigida: ATENDE. Evidencia: [app/src/main/java/com/autotarget/util/DataReconciliation.java](app/src/main/java/com/autotarget/util/DataReconciliation.java#L254)
- Media e variancia das distancias para V: ATENDE (com ressalva do buffer). Evidencia: [app/src/main/java/com/autotarget/service/SensorThread.java](app/src/main/java/com/autotarget/service/SensorThread.java#L228), [app/src/main/java/com/autotarget/service/SensorThread.java](app/src/main/java/com/autotarget/service/SensorThread.java#L261)
- Matriz A baseada em geometria por limiar (incidencia): PARCIAL. Evidencia: [app/src/main/java/com/autotarget/util/DataReconciliation.java](app/src/main/java/com/autotarget/util/DataReconciliation.java#L285)
  - Implementa A pelo espaco nulo de M (geometria dos canhoes), nao por limiar de distancia.
- Reconcilia a cada 10s: ATENDE. Evidencia: [app/src/main/java/com/autotarget/service/ReconciliacaoThread.java](app/src/main/java/com/autotarget/service/ReconciliacaoThread.java#L141), [app/src/main/java/com/autotarget/service/ReconciliacaoThread.java](app/src/main/java/com/autotarget/service/ReconciliacaoThread.java#L167)
- Posicao otima e realocacao gradual: ATENDE (K-Means + moverPara). Evidencia: [app/src/main/java/com/autotarget/service/ReconciliacaoThread.java](app/src/main/java/com/autotarget/service/ReconciliacaoThread.java#L551), [app/src/main/java/com/autotarget/model/Canhao.java](app/src/main/java/com/autotarget/model/Canhao.java#L287)
- Melhorar para excelente:
  - Se o professor exigir A por limiar, construir A por conectividade (distancia media < limiar) e comparar com A via espaco nulo.
  - Incluir comparacao antes/depois (distancias brutas x reconciliadas) em tabela ou grafico.

### e) Decisao adicionar/remover canhoes (custo-beneficio)
- Funcao utilidade U(N) e decisao greedy: ATENDE. Evidencia: [app/src/main/java/com/autotarget/util/DataReconciliation.java](app/src/main/java/com/autotarget/util/DataReconciliation.java#L377), [app/src/main/java/com/autotarget/service/ReconciliacaoThread.java](app/src/main/java/com/autotarget/service/ReconciliacaoThread.java#L421)
- Adicao/remocao com controle de threads e locks: ATENDE. Evidencia: [app/src/main/java/com/autotarget/service/ReconciliacaoThread.java](app/src/main/java/com/autotarget/service/ReconciliacaoThread.java#L472), [app/src/main/java/com/autotarget/engine/Jogo.java](app/src/main/java/com/autotarget/engine/Jogo.java#L491)
- Avaliacao por lado: PARCIAL (aplica-se ao lado direito/IA; lado esquerdo e manual). Evidencia: [app/src/main/java/com/autotarget/service/ReconciliacaoThread.java](app/src/main/java/com/autotarget/service/ReconciliacaoThread.java#L382), [app/src/main/java/com/autotarget/engine/GameSurfaceView.java](app/src/main/java/com/autotarget/engine/GameSurfaceView.java#L284)
- Melhorar para excelente:
  - Explicitar no relatorio que o lado esquerdo e manual (se isso for aceito), ou adicionar logica opcional automatica.
  - Evidenciar ganho esperado de abates (comparacao U(N+1), U(N), U(N-1)) no relatorio final.

### f) Escalonamento de tempo real e escalabilidade
- Identificacao de tarefas (>= 8): ATENDE. Evidencia: [app/src/main/java/com/autotarget/util/RMAAnalysis.java](app/src/main/java/com/autotarget/util/RMAAnalysis.java#L43)
- Calculo de WCRT (tempo de resposta): ATENDE (metodo existe e e usado no log). Evidencia: [app/src/main/java/com/autotarget/util/RMAAnalysis.java](app/src/main/java/com/autotarget/util/RMAAnalysis.java#L86)
- Jitter, grafo de dependencias e tabela completa: PARCIAL (nao ha jitter nem grafo formal). Evidencia: [app/src/main/java/com/autotarget/util/RMAAnalysis.java](app/src/main/java/com/autotarget/util/RMAAnalysis.java#L43)
- Variacao de processadores com afinidade: PARCIAL. Evidencia: [app/src/main/java/com/autotarget/util/ThreadAffinityHelper.java](app/src/main/java/com/autotarget/util/ThreadAffinityHelper.java#L68), [app/src/main/java/com/autotarget/ui/BenchmarkActivity.java](app/src/main/java/com/autotarget/ui/BenchmarkActivity.java#L184)
  - Usa JNI para afinidade; nao usa explicitamente Process.setThreadAffinityMask().
  - Mede tempo total de carga, nao tempos de resposta por tarefa.
- Graficos comparativos e conclusoes: NAO ATENDE (apenas texto). Evidencia: [app/src/main/java/com/autotarget/ui/BenchmarkActivity.java](app/src/main/java/com/autotarget/ui/BenchmarkActivity.java#L277)
- Melhorar para excelente:
  - Adicionar tabela formal com Pi, Ci, Di, Ji e dependencias.
  - Medir Ri real por tarefa (timestamp por ciclo) e indicar misses.
  - Usar ou documentar equivalencia a Process.setThreadAffinityMask().
  - Gerar graficos (ex: tempo de resposta por tarefa e speedup vs cores).

## Itens extras que ajudam a subir de nivel
- Mostrar relatorio final com metricas (RMSE, reducao de MSE, norma do residuo) em grafico simples.
- Exportar dados do benchmark para CSV e plotar automaticamente em uma tela de relatorio.
- Incluir testes automatizados de fronteira (alvo cruzando linha no exato instante da colisao).
