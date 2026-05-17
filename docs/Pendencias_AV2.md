# Pendências AV2 — AutoTarget

Este documento lista, organiza e descreve detalhadamente todas as pendências identificadas na auditoria AV2.
Cada item contém: descrição do problema, motivo (impacto na rubrica), arquivos afetados, prioridade e passos recomendados para correção.

---

## Sumário rápido
- Corrigir histórico de sensores para o mínimo de 10 leituras
- Implementar avaliação de custo-benefício (função utilidade) na reconciliação
- Adicionar testes unitários que cubram casos de singularidade e fallback na reconciliação
- Documentar e parametrizar ThreadAffinityHelper / fallback JNI
- Ajustes menores: comentários, remoção de métodos dummy e cobertura de logs

---

## 1) Restaurar `TAMANHO_HISTORICO` para 10

- Arquivo: `app/src/main/java/com/autotarget/service/SensorThread.java`
- Descrição: Atualmente `TAMANHO_HISTORICO` está configurado como 5. A especificação AV2 exige que haja pelo menos 10 leituras por alvo antes da reconciliação.
- Impacto: Redução da robustez estatística, resultados de reconciliação menos confiáveis, penaliza nota em "Sensores ruidosos + buffers".
- Prioridade: Alta
- Passos de correção:
  1. Alterar `private static final int TAMANHO_HISTORICO = 5;` para `10` ou tornar parametrizável via configuração (recommended).
  2. Garantir que `ReconciliacaoThread` só use alvos com histórico >= 10 (já existe filtragem, mas validar). Verificar `getMediaDistancias()` e `getVarianciaDistancias()`.
  3. Atualizar testes/falsos (mocks) que esperam 5 para usar 10.
  4. Rodar testes e revisar `ReconciliationLog` para confirmar aumento do número de alvos processados após 10s de coleta.

---

## 2) Implementar `avaliarCustoBeneficio(...)` em `ReconciliacaoThread`

- Arquivo: `app/src/main/java/com/autotarget/service/ReconciliacaoThread.java`
- Descrição: Método atualmente vazio; responsável por decidir adicionar/remover canhões com base em utilidade esperada U(N).
- Impacto: Sem essa implementação, decisões de alocação não são justificadas matematicamente — afeta nota em "Otimização".
- Prioridade: Alta
- Requisitos e proposta de implementação:
  - Usar `DataReconciliation.calcularUtilidade(...)` com distâncias reconciliadas para estimar `U(N)`.
  - Calcular `U(N)`, `U(N+1)` e `U(N-1)` quando aplicável. Considerar energia disponível (`jogo.getEnergia(lado)`) e limiar de ganho `LIMIAR_GANHO` já definido.
  - Registar comparação via `ReconciliationLog.logUtilityComparison(...)` antes de aplicar alteração.
  - Só executar `listener.onSugestaoAdicionarCanhao` se `U(N+1) - U(N) > LIMIAR_GANHO && energia > ENERGIA_SEGURA_MINIMA + custoEstimado`.
  - Para remoção: se `U(N) - U(N-1) < LIMIAR_NEGATIVO` (ou ganho negativo) e energia suficiente, sugerir remoção.

- Passos de correção (detalhados):
  1. Implementar método para reconstruir `float[][] distancias` com base em `DataReconciliation.ReconciliationResult[] resultados` (formatar como [M][N]).
  2. Calcular `uAtual = DataReconciliation.calcularUtilidade(distancias, N, LIMIAR_PENALIDADE, ALPHA, intervaloBase)`.
  3. Para `uMais1`: simular adição de um canhão hipotético — por exemplo posicionar no `calcularPosicaoEstrategica(lado, N)` e estimar distâncias euclidianas para cada alvo reconciliado; recomputar utilidade com N+1 e energia descontada.
  4. Para `uMenos1`: simular remoção do canhão marginal (ex.: o com menor contribuição) e recomputar utilidade.
  5. Se decisão de adicionar/remover for tomada, chamar `ReconciliationLog.logAIDecision` e `listener.onSugestaoAdicionarCanhao` / `onSugestaoRemoverCanhao`.
  6. Escrever testes unitários para cenários: ganho positivo grande, ganho marginal pequeno, e energia insuficiente.

---

## 3) Testes para singularidade numérica / fallback EJML

- Arquivo(s): `app/src/main/java/com/autotarget/util/DataReconciliation.java` + testes em `app/src/test/java/...`
- Descrição: Garantir que `safeInvert`, `reconcile()` e `computeLeftNullSpace()` tem cobertura para casos singulares, colinearidade de canhões, cond numbers altos.
- Impacto: Robustez matemática e nota em "Reconciliação".
- Prioridade: Média-Alta
- Passos:
  1. Adicionar testes que criem matrizes M colineares e verifiquem retorno do fallback OLS.
  2. Testar `reconcile()` com V diagonal contendo zeros ou valores muito pequenos, confirmando uso de regularização e pseudo-inversa.
  3. Medir condNumber (SVD) e logar quando superior a um limiar (ex.: 1e10) para alertar sobre instabilidade.

---

## 4) Documentar `ThreadAffinityHelper` e parametrizar comportamento

- Arquivo: `app/src/main/java/com/autotarget/util/ThreadAffinityHelper.java`
- Descrição: Atualmente tenta JNI e reflexão; dependência NDK pode não existir. Falta configuração clara para habilitar/desabilitar tentativas em dispositivos de teste/CI.
- Impacto: Simplifica testes e evita logs de erro poluentes.
- Prioridade: Média
- Passos:
  1. Adicionar flag global (por ex. `BuildConfig.ENABLE_THREAD_AFFINITY`) para ativar tentativas de afinidade.
  2. Documentar fallback no README do projeto (ou `docs/DEPENDENCIES.md`) informando dependências NDK e instruções de build para `libthreadaffinity`.
  3. Adicionar checagem em tempo de execução e log claro quando a afinidade for ignorada.

---

## 5) Remover / documentar métodos dummy em `Projetil`

- Arquivo: `app/src/main/java/com/autotarget/model/Projetil.java`
- Descrição: Contém `start()` e `interrupt()` dummy. Pode confundir manutenção; preferir `Runnable` puro ou documentar pool/Executor usage.
- Impacto: Manutenibilidade
- Prioridade: Baixa
- Passos:
  1. Converter para `implements Runnable` (já está) e remover métodos `start()`/`interrupt()` vazios, ou explicitar com comentário que são compatibilidade com APIs antigas.

---

## 6) Testes de integração / stress e coleta de métricas RMA

- Arquivos/recursos: `app/src/test/java/...`, `ReconciliationLog`, `RMAAnalysis`
- Descrição: Rodar testes de estresse existentes (`JogoStressConcorrenciaTest`) com logging RMA ligado e coletar métricas. Validar que deadlines realistas não são violados frequentemente.
- Impacto: Nota em "Tempo real" e robustez geral.
- Prioridade: Média
- Passos:
  1. Executar os testes de stress localmente ou criar um runner que simule X partidas headless.
  2. Agregar `RMAAnalysis.getRuntimeMetricsReport()` e `ReconciliationLog.gerarRelatorio()` para análise.
  3. Ajustar `TASKS` em `RMAAnalysis` se estimativas de Ci não forem realistas.

---

## 7) Documentação, README e checklist final

- Arquivos: `README.md`, `docs/Pendencias_AV2.md` (este arquivo), `docs/Checklist_Agente_IA_AutoTarget.md` (já existente)
- Descrição: Consolidar instruções para executar testes, dependências EJML/NDK e passos para reproduzir os logs e relatórios.
- Prioridade: Baixa
- Passos:
  1. Atualizar `README.md` com comandos para rodar testes (`./gradlew test`), caminhos para visualizar relatórios e instruções NDK/EJML.
  2. Referenciar `docs/Pendencias_AV2.md` e `docs/Checklist_Agente_IA_AutoTarget.md`.

---

## Ordem sugerida de implementação (prioridade executável)
1. Corrigir `TAMANHO_HISTORICO` → 10 (rápido).  
2. Implementar `avaliarCustoBeneficio(...)` com `DataReconciliation.calcularUtilidade`.  
3. Adicionar testes unitários para reconciliação (singularidade/condição).  
4. Documentar `ThreadAffinityHelper` e adicionar flag de runtime.  
5. Ajustes menores (`Projetil` dummy methods).  
6. Rodar testes de stress + coletar `RMAAnalysis`/`ReconciliationLog`.  
7. Atualizar `README.md` e documentação.

---

## Apoio: comandos úteis

Para rodar os testes unitários localmente (Windows PowerShell):

```powershell
./gradlew test
./gradlew connectedAndroidTest
```

Gerar o relatório de reconciliação via app: jogar uma partida e abrir o menu "Relatório de Reconciliação".

---

Se desejar, posso aplicar automaticamente as alterações prioritárias (1 e 2). Indique se devo:  
- Aplicar 1) + executar testes rápidos,  
- Aplicar 1) e 2) (mais trabalho), ou  
- Apenas criar PR com mudanças sugeridas.
