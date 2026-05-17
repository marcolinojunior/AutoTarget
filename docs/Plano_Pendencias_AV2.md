# Plano de Pendencias AV2 - AutoTarget

Este documento lista, em ordem sequencial, o que falta para elevar o projeto ao nivel maximo da rubrica AV2, com tarefas executaveis uma apos a outra.

## Como usar este plano

- Execute as tarefas na ordem apresentada (T01 -> T12).
- Nao pule criterio de aceite.
- Marque cada tarefa como concluida somente apos validacao tecnica e evidencias.

---

## T01 - Parada imediata da frota quando energia do lado zerar

### Objetivo
Garantir que, quando a energia de um lado chegar a zero, todos os canhoes ativos desse lado parem imediatamente (nao apenas um por ciclo).

### Arquivos alvo
- app/src/main/java/com/autotarget/engine/Jogo.java
- app/src/main/java/com/autotarget/model/Canhao.java

### O que implementar
1. Criar metodo no motor para desativar todos os canhoes ativos de um lado de forma atomica.
2. Substituir chamadas atuais de desativacao unitarias no fluxo de energia por desativacao total.
3. Garantir que o estado de energia fique clampado em 0 apos disparo do mecanismo.
4. Garantir que o canhao em loop de disparo respeite o estado de inatividade sem atraso residual.

### Criterios de aceite
- Com energia de um lado em 0, nenhum canhao desse lado continua disparando.
- A parada ocorre no mesmo ciclo de atualizacao de energia.
- Nao ha ConcurrentModificationException nem thread orfa.

### Evidencias esperadas
- Teste unitario novo comprovando parada total da frota no lado sem energia.
- Log com contagem de canhoes ativos antes/depois da parada.

---

## T02 - Unificar politica de afinidade de CPU

### Objetivo
Remover duplicidade de abordagem (reflexao direta em android.os.Process e helper JNI) e adotar um unico caminho padrao de afinidade.

### Arquivos alvo
- app/src/main/java/com/autotarget/util/ThreadAffinityHelper.java
- app/src/main/java/com/autotarget/service/SensorThread.java
- app/src/main/java/com/autotarget/service/ReconciliacaoThread.java
- app/src/main/java/com/autotarget/engine/Jogo.java
- app/src/main/java/com/autotarget/engine/GameSurfaceView.java

### O que implementar
1. Definir o helper como ponto unico de entrada para afinidade.
2. Remover trechos de reflexao duplicados nas threads.
3. Padronizar mascaras por classe de tarefa (critica, media, fundo).
4. Adicionar fallback explicito e log padrao para dispositivos sem suporte.

### Criterios de aceite
- Nenhum arquivo chama Process.setThreadAffinityMask diretamente fora do helper.
- Todas as tarefas com afinidade usam o mesmo helper.
- Em dispositivo sem suporte, execucao continua sem crash.

### Evidencias esperadas
- Busca no projeto sem ocorrencias de chamada direta indevida.
- Logs padronizados indicando sucesso/fallback por tarefa.

---

## T03 - Fortalecer testes quantitativos de reconciliacao

### Objetivo
Comprovar matematicamente que a reconciliacao reduz erro/variancia de forma consistente.

### Arquivos alvo
- app/src/test/java/com/autotarget/util/DataReconciliationTest.java
- app/src/main/java/com/autotarget/util/DataReconciliation.java

### O que implementar
1. Criar cenario sintetico reprodutivel com ruido conhecido.
2. Medir MSE/erro antes e depois para multiplos lotes.
3. Definir limiar minimo de melhoria esperada (exemplo: reducao media > X%).
4. Cobrir caso singular e caso bem condicionado com asserts quantitativos.

### Criterios de aceite
- Testes falham se nao houver melhoria minima definida.
- Testes cobrem ao menos: N<4, N>=4, singularidade, matriz bem condicionada.

### Evidencias esperadas
- Saida de teste com metricas medias de erro antes/depois.
- Assert claro da melhoria quantitativa.

---

## T04 - Fechar lacunas de borda na intersecao fronteira x disparo

### Objetivo
Garantir comportamento deterministico quando alvo cruza fronteira no mesmo instante de disparo/colisao.

### Arquivos alvo
- app/src/main/java/com/autotarget/engine/Jogo.java
- app/src/main/java/com/autotarget/model/Projetil.java
- app/src/test/java/com/autotarget/engine/JogoFronteiraTest.java

### O que implementar
1. Definir regra unica de precedencia (transferencia vs abatimento) e documentar.
2. Garantir que pontuacao siga a regra sem dupla contagem.
3. Cobrir timing race com testes repetitivos (stress de fronteira).

### Criterios de aceite
- Sem duplicacao de alvo em listas esquerda/direita.
- Sem perda de evento de abatimento.
- Pontuacao total consistente com numero de alvos destruidos.

### Evidencias esperadas
- Teste de stress de fronteira executado em repeticoes sem falha.

---

## T05 - Instrumentar justificativa formal do modelo de penalidade

### Objetivo
Transformar a penalidade implementada em modelo justificavel no relatorio (nao so no codigo).

### Arquivos alvo
- docs/Especificacao_Projeto_AutoTarget_AV2.md
- app/src/main/java/com/autotarget/model/Canhao.java
- app/src/main/java/com/autotarget/util/ReconciliationLog.java

### O que implementar
1. Documentar formula utilizada, dominio e impacto esperado.
2. Registrar metricas comparativas de taxa de disparo por quantidade de canhoes.
3. Gerar tabela para anexar ao relatorio final.

### Criterios de aceite
- Formula e parametros documentados em linguagem tecnica.
- Existe evidencia numerica de impacto da penalidade.

### Evidencias esperadas
- Secao no documento com tabela de N canhoes vs intervalo efetivo.

---

## T06 - Concluir pacote de evidencias de sensores (media/variancia)

### Objetivo
Deixar rastreavel a analise estatistica exigida pela rubrica para sensores ruidosos.

### Arquivos alvo
- app/src/main/java/com/autotarget/service/SensorThread.java
- app/src/main/java/com/autotarget/util/SensorStatisticsTracker.java
- app/src/main/java/com/autotarget/ui/ReconciliationReportActivity.java

### O que implementar
1. Exibir resumo de media/variancia por lado no relatorio.
2. Permitir exportacao do historico para anexo tecnico.
3. Garantir consistencia do tamanho de amostra apresentado.

### Criterios de aceite
- Relatorio mostra media, variancia e desvio por janela.
- Dados apresentados batem com buffer real (10 leituras minimas).

### Evidencias esperadas
- Captura de relatorio com estatisticas por lado.

---

## T07 - Formalizar matriz A e V no relatorio tecnico

### Objetivo
Explicar claramente como A (incidencia) e V (covariancia) sao construidas no projeto.

### Arquivos alvo
- docs/Especificacao_Projeto_AutoTarget_AV2.md
- app/src/main/java/com/autotarget/util/DataReconciliation.java

### O que implementar
1. Descrever construcao de V a partir de variancias amostrais.
2. Descrever construcao de A por limiar geometrico.
3. Inserir exemplo numerico pequeno (didatico) com 1 alvo e N canhoes.

### Criterios de aceite
- Leitor externo entende reproduzir A e V sem abrir o codigo.
- Equacao de reconciliacao esta conectada ao exemplo numerico.

### Evidencias esperadas
- Secao matematica dedicada no documento.

---

## T08 - Consolidar benchmark de 1, 2 e todos os nucleos

### Objetivo
Atender integralmente a exigencia de comparacao de afinidade/processadores com conclusao de escalonabilidade.

### Arquivos alvo
- app/src/main/java/com/autotarget/ui/BenchmarkActivity.java
- docs/Especificacao_Projeto_AutoTarget_AV2.md

### O que implementar
1. Rodar benchmark em 1 core, 2 cores e todos os cores disponiveis.
2. Exportar CSV final com speedup e metricas de deadline.
3. Gerar graficos comparativos para anexar ao relatorio.
4. Escrever conclusao objetiva sobre gargalos e perdas de deadline.

### Criterios de aceite
- Existem 3 cenarios completos com dados comparaveis.
- Relatorio conclui onde o sistema deixa de ser escalonavel (se ocorrer).

### Evidencias esperadas
- CSVs e graficos anexos ao documento tecnico.

---

## T09 - Revisao de concorrencia e lock ordering em documentacao de entrega

### Objetivo
Garantir que estrategia de sincronizacao esteja explicitada como parte da avaliacao.

### Arquivos alvo
- app/src/main/java/com/autotarget/engine/Jogo.java
- docs/Especificacao_Projeto_AutoTarget_AV2.md

### O que implementar
1. Extrair lock ordering oficial para documento.
2. Mapear regioes criticas por thread (fisica, sensores, reconciliacao).
3. Incluir riscos mitigados (race, deadlock, starvation) e por que nao ocorrem.

### Criterios de aceite
- Documento permite auditoria de concorrencia sem ambiguidade.

### Evidencias esperadas
- Tabela thread x lock x recurso protegido.

---

## T10 - Completar cobertura de testes de regressao

### Objetivo
Aumentar seguranca contra regressao ao fazer ajustes finais.

### Arquivos alvo
- app/src/test/java/com/autotarget/engine/JogoStressConcorrenciaTest.java
- app/src/test/java/com/autotarget/service/ReconciliacaoThreadTest.java
- app/src/test/java/com/autotarget/util/RMAAnalysisTest.java

### O que implementar
1. Adicionar cenarios de regressao para energia zero e parada total.
2. Adicionar cenarios para decisao de adicionar/remover canhoes pela utilidade.
3. Adicionar verificacao automatica de consistencia de metricas RMA.

### Criterios de aceite
- Suite de testes cobre os ajustes criticos T01-T04.
- Falhas de comportamento principal sao detectadas automaticamente.

### Evidencias esperadas
- Execucao de gradle test sem falhas apos inclusao dos novos testes.

---

## T11 - Pacote final de entrega AV2 (relatorio + anexos)

### Objetivo
Fechar a entrega com evidencias alinhadas 1:1 aos criterios da rubrica.

### Arquivos alvo
- docs/Especificacao_Projeto_AutoTarget_AV2.md
- docs/Checklist_Agente_IA_AutoTarget.md
- docs (anexos de graficos/tabelas)

### O que implementar
1. Preencher cada item da checklist com evidencia correspondente.
2. Anexar graficos de energia, reconciliacao e benchmark multicore.
3. Inserir secao de conclusoes e limitacoes conhecidas.

### Criterios de aceite
- Cada criterio da rubrica aponta para uma evidencia objetiva.
- Documento final esta pronto para avaliacao sem dependencia de explicacao oral.

### Evidencias esperadas
- Checklist integralmente marcada com referencias de evidencias.

---

## T12 - Dry run final de apresentacao

### Objetivo
Validar fluxo fim-a-fim em execucao para reduzir risco na avaliacao pratica.

### O que executar
1. Rodar partida completa com logs habilitados.
2. Verificar UI, placar, energia, reconciliacao, realocacao e encerramento.
3. Gerar relatorio de reconciliacao ao fim.
4. Rodar benchmark e salvar CSV.

### Criterios de aceite
- Sem crash, sem ANR, sem inconsistencias de placar/energia.
- Evidencias geradas e salvas para apresentacao.

---

## Ordem de execucao recomendada

1. T01
2. T02
3. T03
4. T04
5. T05
6. T06
7. T07
8. T08
9. T09
10. T10
11. T11
12. T12

---

## Definicao de pronto (DoD) da AV2

A AV2 esta pronta quando:
- Requisitos funcionais estao corretos e estaveis em borda.
- Requisitos matematicos estao implementados e comprovados quantitativamente.
- Requisitos de tempo real estao analisados com teoria + medicao.
- Evidencias de todos os criterios da rubrica estao documentadas e auditaveis.
