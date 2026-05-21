# Relatorio de Analise de Telemetria - AutoTarget

Gerado em: 2026-05-19 20:37:54

## Resumo Executivo
- Runs analisadas: 3 (dados2, dados3, dados4)
- Reconcilicao: 15/31 sucesso (48.39%)
- Reducao media do erro (apenas sucesso): 69.66%
- Norma media ||A*yHat|| (apenas sucesso): 0.059153
- Deadline misses totais (ALL_CORES): 4815

## Cobertura dos Dados
| Run | reconciliation | deadline_misses | rma_runtime | energy_penalty | energy_restoration | sensor_variance | utility |
| --- | --- | --- | --- | --- | --- | --- | --- |
| dados2 | 10 | 8 | 8 | 60 | 152 | 32 | 2 |
| dados3 | 13 | 8 | 8 | 60 | 150 | 39 | 3 |
| dados4 | 8 | 8 | 8 | 60 | 148 | 49 | 2 |

## Reconcilicao (telemetry_reconciliation.csv)
| Run | Total | Sucesso | Sucesso% | Reducao media (%) | Reducao mediana (%) | Norma media |
| --- | --- | --- | --- | --- | --- | --- |
| dados2 | 10 | 6 | 60.00% | 84.79 | 89.12 | 0.037476 |
| dados3 | 13 | 6 | 46.15% | 55.63 | 68.72 | 0.086718 |
| dados4 | 8 | 3 | 37.50% | 67.45 | 62.41 | 0.047379 |
| TOTAL | 31 | 15 | 48.39% | 69.66 | 72.21 | 0.059153 |

## Escalonamento e Deadline Misses (deadline_misses_ALL_CORES.csv)
| Tarefa | Execucoes | Misses | Miss rate |
| --- | --- | --- | --- |
| T4-Render | 21519 | 4593 | 21.34% |
| T1-Physics | 44421 | 144 | 0.32% |
| T2-Projetil | 28870 | 63 | 0.22% |
| T3-Alvo | 215505 | 15 | 0.01% |
| T8-Reconciliacao | 240 | 0 | 0.00% |
| T7-Sensor | 720 | 0 | 0.00% |
| T6-Canhao | 3485 | 0 | 0.00% |
| T5-GameTimer | 720 | 0 | 0.00% |

## RMA Runtime (telemetry_rma_runtime.csv)
| Tarefa | Execucoes | Max ms | Avg ms | Stddev ms | Misses |
| --- | --- | --- | --- | --- | --- |
| T4-Render | 21519 | 931.000 | 31.371 | 34.213 | 4593 |
| T8-Reconciliacao | 240 | 915.000 | 15.921 | 101.540 | 0 |
| T3-Alvo | 215505 | 112.000 | 0.032 | 0.747 | 15 |
| T6-Canhao | 3485 | 85.000 | 1.178 | 5.413 | 0 |
| T2-Projetil | 28870 | 77.000 | 0.233 | 1.937 | 63 |
| T5-GameTimer | 720 | 77.000 | 2.982 | 7.836 | 0 |
| T1-Physics | 44421 | 75.000 | 0.285 | 2.080 | 144 |
| T7-Sensor | 720 | 60.000 | 3.278 | 7.234 | 0 |

## Energia e Penalidade (telemetry_energy_penalty.csv)
- Energia minima: esquerda 13.20 | direita 38.00
- Energia media: esquerda 65.16 | direita 74.43
- Intervalo medio com N<=5: 1500.0 ms | N>5: 1852.9 ms
| Canhoes | Intervalo medio ms | Amostras |
| --- | --- | --- |
| 1 | 1500.0 | 18 |
| 2 | 1500.0 | 18 |
| 3 | 1500.0 | 18 |
| 4 | 1500.0 | 21 |
| 5 | 1500.0 | 177 |
| 6 | 1800.0 | 42 |
| 7 | 2100.0 | 9 |

## Energia Restoration (telemetry_energy_restoration.csv)
| Lado | Eventos | Energia restaurada media | Energia apos media | Abates max |
| --- | --- | --- | --- | --- |
| ESQUERDO | 216 | 2.47 | 57.03 | 73 |
| DIREITO | 234 | 2.47 | 71.69 | 81 |

## Variancia de Sensores (telemetry_sensor_variance.csv)
| Lado | Amostras | Var_X media | Var_Y media | Var_VelX media | Var_VelY media |
| --- | --- | --- | --- | --- | --- |
| ESQUERDO | 52 | 266.157 | 3168.388 | 0.033 | 0.033 |
| DIREITO | 68 | 1440.735 | 2438.381 | 0.025 | 0.030 |

## Funcao de Utilidade (telemetry_utility.csv)
| Lado | Amostras | U_Atual media | Sinal adicionar | Sinal remover |
| --- | --- | --- | --- | --- |
| ESQUERDO | 4 | 4.754 | 3 | 1 |
| DIREITO | 3 | 9.565 | 0 | 0 |
