/*
 * ============================================================================
 * Arquivo: RMAAnalysis.java
 * Pacote:  com.autotarget.util
 * ============================================================================
 *
 * DESCRIÇÃO TÉCNICA:
 *   Classe utilitária para Análise Monotônica de Taxa (Rate Monotonic
 *   Analysis - RMA). Implementa verificação de escalonabilidade via
 *   fórmula de Liu & Layland e cálculo recursivo do Tempo de Resposta
 *   de Pior Caso (WCRT).
 *
 * FÓRMULA DE LIU & LAYLAND:
 *   Σ(Ci/Pi) ≤ n(2^(1/n) - 1)
 *
 * TEMPO DE RESPOSTA WCRT:
 *   R_i^(k+1) = C_i + Σ_{j∈HP(i)} ⌈R_i^(k)/P_j⌉ · C_j
 *
 * TABELA DE TAREFAS DO AUTOTARGET:
 *   T1: PhysicsTimer.verificarColisoes — P=16ms, C=2-4ms, D=16ms, Prio=1
 *   T2: Projetil.run                   — P=16ms, C=1-2ms, D=16ms, Prio=1
 *   T3: Alvo.run (Trajetórias)         — P=30ms, C=1-3ms, D=30ms, Prio=2
 *   T4: RenderThread.run (Canvas)      — P=33ms, C=5-12ms, D=33ms, Prio=3
 *   T5: GameTimer.run (Energia)        — P=1000ms, C=<1ms, D=1000ms, Prio=4
 *   T6: Canhao.run (Disparos)          — P=1500ms, C=3-5ms, D=1500ms, Prio=5
 *   T7: SensorThread.coletar           — P=1000ms, C=5-10ms, D=1000ms, Prio=6
 *   T8: ReconciliacaoThread (EJML)     — P=10000ms, C=200-500ms, D=10000ms, Prio=7
 *
 * ============================================================================
 */
package com.autotarget.util;

import android.util.Log;

/**
 * Análise de escalonabilidade Rate Monotonic para as tarefas do AutoTarget.
 */
public class RMAAnalysis {

    private static final String TAG = "RMAAnalysis";

    /** Dados das tarefas: {período_ms, wcet_ms, deadline_ms} */
    private static final double[][] TASKS = {
        {16,   4,    16},    // T1 PhysicsTimer
        {16,   2,    16},    // T2 Projetil
        {30,   3,    30},    // T3 Alvo
        {33,   12,   33},    // T4 RenderThread
        {1000, 1,    1000},  // T5 GameTimer
        {1500, 5,    1500},  // T6 Canhao
        {1000, 10,   1000},  // T7 SensorThread
        {10000, 500, 10000}  // T8 Reconciliacao
    };

    private static final String[] TASK_NAMES = {
        "T1-Physics", "T2-Projetil", "T3-Alvo", "T4-Render",
        "T5-GameTimer", "T6-Canhao", "T7-Sensor", "T8-Reconciliacao"
    };

    /**
     * Calcula o limite de utilização de Liu & Layland.
     * U_bound = n * (2^(1/n) - 1)
     */
    public static double liuLaylandBound(int n) {
        return n * (Math.pow(2.0, 1.0 / n) - 1.0);
    }

    /**
     * Calcula a utilização total do sistema.
     * U = Σ(Ci/Pi)
     */
    public static double totalUtilization() {
        double u = 0;
        for (double[] task : TASKS) {
            u += task[1] / task[0];
        }
        return u;
    }

    /**
     * Calcula o Tempo de Resposta de Pior Caso (WCRT) para a tarefa i.
     * R_i^(k+1) = C_i + Σ_{j∈HP(i)} ⌈R_i^(k)/P_j⌉ · C_j
     *
     * @param taskIndex índice da tarefa (0-7)
     * @return WCRT em ms, ou -1 se não convergir (não escalonável)
     */
    public static double worstCaseResponseTime(int taskIndex) {
        double ci = TASKS[taskIndex][1];
        double di = TASKS[taskIndex][2];
        double r = ci;

        for (int iter = 0; iter < 100; iter++) {
            double rNew = ci;
            for (int j = 0; j < taskIndex; j++) {
                rNew += Math.ceil(r / TASKS[j][0]) * TASKS[j][1];
            }
            if (Math.abs(rNew - r) < 0.001) {
                return rNew;
            }
            r = rNew;
            if (r > di) return r; // Deadline miss
        }
        return -1; // Não convergiu
    }

    /**
     * Executa análise completa e loga os resultados.
     */
    public static void executarAnalise() {
        int n = TASKS.length;
        double u = totalUtilization();
        double bound = liuLaylandBound(n);

        Log.i(TAG, "═══════════════════════════════════════════════════");
        Log.i(TAG, "  ANÁLISE RMA — Rate Monotonic Analysis");
        Log.i(TAG, "═══════════════════════════════════════════════════");
        Log.i(TAG, String.format("  Utilização total: U = %.4f", u));
        Log.i(TAG, String.format("  Limite Liu & Layland: U_bound = %.4f (n=%d)", bound, n));
        Log.i(TAG, String.format("  Resultado: %s",
                u <= bound ? "ESCALONÁVEL (U ≤ U_bound)" : "INCONCLUSIVO (U > U_bound, verificar WCRT)"));
        Log.i(TAG, "───────────────────────────────────────────────────");

        for (int i = 0; i < n; i++) {
            double ri = worstCaseResponseTime(i);
            double di = TASKS[i][2];
            boolean ok = ri >= 0 && ri <= di;
            Log.i(TAG, String.format("  %s: P=%.0fms C=%.0fms D=%.0fms R=%.1fms %s",
                    TASK_NAMES[i], TASKS[i][0], TASKS[i][1], di, ri,
                    ok ? "✓" : "✗ DEADLINE MISS"));
        }
        Log.i(TAG, "═══════════════════════════════════════════════════");
    }

    /**
     * Verifica se o tempo de execução medido ultrapassou o deadline.
     *
     * @param taskName  nome da tarefa para log
     * @param elapsedMs tempo medido em ms
     * @param deadlineMs deadline em ms
     */
    public static void checkDeadline(String taskName, long elapsedMs, long deadlineMs) {
        if (elapsedMs > deadlineMs) {
            Log.w(TAG, String.format("RMA DEADLINE MISS: %s executou em %dms (deadline=%dms)",
                    taskName, elapsedMs, deadlineMs));
        }
    }
}
