package com.autotarget.util;

import android.util.Log;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Análise de escalonabilidade Rate Monotonic com métricas teóricas e observadas.
 */
public class RMAAnalysis {

    private static final String TAG = "RMAAnalysis";

    private static final class TaskDef {
        final String id;
        final String name;
        final double periodMs;
        final double wcetMs;
        final double deadlineMs;
        final double jitterMs;
        final String dependencies;

        TaskDef(String id, String name, double periodMs, double wcetMs,
                double deadlineMs, double jitterMs, String dependencies) {
            this.id = id;
            this.name = name;
            this.periodMs = periodMs;
            this.wcetMs = wcetMs;
            this.deadlineMs = deadlineMs;
            this.jitterMs = jitterMs;
            this.dependencies = dependencies;
        }
    }

    private static final class RuntimeStats {
        long executions;
        long deadlineMisses;
        double maxResponseMs;
        double sumResponseMs;
        double sumSquaresResponseMs;
    }

    private static final TaskDef[] TASKS = {
            new TaskDef("T1-Physics", "PhysicsTimer.verificarColisoes", 16, 4, 16, 1, "T3"),
            new TaskDef("T2-Projetil", "Projetil.run", 16, 2, 16, 1, "T1"),
            new TaskDef("T3-Alvo", "Alvo.run", 30, 3, 30, 2, "-"),
            new TaskDef("T4-Render", "RenderThread.run", 33, 12, 33, 3, "T1,T2"),
            new TaskDef("T5-GameTimer", "GameTimer.run", 1000, 1, 1000, 10, "T1"),
            new TaskDef("T6-Canhao", "Canhao.run", 1500, 5, 1500, 20, "T5,T7"),
            new TaskDef("T7-Sensor", "SensorThread.coletar", 1000, 10, 1000, 8, "T3,T6"),
            new TaskDef("T8-Reconciliacao", "ReconciliacaoThread", 10000, 500, 10000, 30, "T7")
    };

    private static final Map<String, RuntimeStats> RUNTIME_STATS = new ConcurrentHashMap<>();
    private static final Map<String, Double> TASK_DEADLINES = new ConcurrentHashMap<>();

    static {
        for (TaskDef task : TASKS) {
            TASK_DEADLINES.put(task.id, task.deadlineMs);
        }
    }

    public static double liuLaylandBound(int n) {
        return n * (Math.pow(2.0, 1.0 / n) - 1.0);
    }

    public static double totalUtilization() {
        double u = 0;
        for (TaskDef task : TASKS) {
            u += task.wcetMs / task.periodMs;
        }
        return u;
    }

    public static double worstCaseResponseTime(int taskIndex) {
        if (taskIndex < 0 || taskIndex >= TASKS.length) return -1;
        double ci = TASKS[taskIndex].wcetMs;
        double di = TASKS[taskIndex].deadlineMs;
        double r = ci;

        for (int iter = 0; iter < 100; iter++) {
            double rNew = ci;
            for (int j = 0; j < taskIndex; j++) {
                rNew += Math.ceil(r / TASKS[j].periodMs) * TASKS[j].wcetMs;
            }
            if (Math.abs(rNew - r) < 0.001) return rNew;
            r = rNew;
            if (r > di) return r;
        }
        return -1;
    }

    public static void executarAnalise() {
        int n = TASKS.length;
        double u = totalUtilization();
        double bound = liuLaylandBound(n);

        Log.i(TAG, "═══════════════════════════════════════════════════");
        Log.i(TAG, "  ANÁLISE RMA — Rate Monotonic Analysis");
        Log.i(TAG, "═══════════════════════════════════════════════════");
        Log.i(TAG, String.format(Locale.US, "  Utilização total: U = %.4f", u));
        Log.i(TAG, String.format(Locale.US, "  Limite Liu & Layland: U_bound = %.4f (n=%d)", bound, n));
        Log.i(TAG, String.format(Locale.US, "  Resultado: %s",
                u <= bound ? "ESCALONÁVEL (U ≤ U_bound)" : "INCONCLUSIVO (U > U_bound, verificar WCRT)"));
        Log.i(TAG, "───────────────────────────────────────────────────");
        Log.i(TAG, "  Tabela: Pi | Ci | Di | Ji | Dependências | R_i");

        for (int i = 0; i < TASKS.length; i++) {
            TaskDef t = TASKS[i];
            double ri = worstCaseResponseTime(i);
            boolean ok = ri >= 0 && ri <= t.deadlineMs;
            Log.i(TAG, String.format(Locale.US,
                    "  %s: P=%.0fms C=%.0fms D=%.0fms J=%.0fms deps=[%s] R=%.1fms %s",
                    t.id, t.periodMs, t.wcetMs, t.deadlineMs, t.jitterMs, t.dependencies,
                    ri, ok ? "✓" : "✗ DEADLINE MISS"));
        }
        Log.i(TAG, "═══════════════════════════════════════════════════");
    }

    public static void checkDeadline(String taskName, long elapsedMs, long deadlineMs) {
        recordExecution(taskName, elapsedMs, deadlineMs);
        if (elapsedMs > deadlineMs) {
            Log.w(TAG, String.format(Locale.US,
                    "RMA DEADLINE MISS: %s executou em %dms (deadline=%dms)",
                    taskName, elapsedMs, deadlineMs));
        }
    }

    public static void recordExecution(String taskName, long elapsedMs) {
        double deadline = TASK_DEADLINES.getOrDefault(taskName, Double.MAX_VALUE);
        recordExecution(taskName, elapsedMs, deadline);
    }

    private static void recordExecution(String taskName, double elapsedMs, double deadlineMs) {
        RuntimeStats stats = RUNTIME_STATS.computeIfAbsent(taskName, k -> new RuntimeStats());
        synchronized (stats) {
            stats.executions++;
            stats.maxResponseMs = Math.max(stats.maxResponseMs, elapsedMs);
            stats.sumResponseMs += elapsedMs;
            stats.sumSquaresResponseMs += elapsedMs * elapsedMs;
            if (elapsedMs > deadlineMs) {
                stats.deadlineMisses++;
            }
        }
    }

    public static void resetRuntimeStats() {
        RUNTIME_STATS.clear();
        for (TaskDef t : TASKS) {
            RUNTIME_STATS.put(t.id, new RuntimeStats());
        }
    }

    /**
     * Exporta os deadline misses por tarefa para um arquivo CSV.
     * Atende ao item 35 da rubrica AV2.
     */
    public static void exportDeadlineMissesToCSV(android.content.Context context) {
        String mode = ThreadAffinityHelper.getAffinityMode().name();
        String filename = "deadline_misses_" + mode + ".csv";
        StringBuilder sb = new StringBuilder();
        sb.append("TaskID,Executions,DeadlineMisses,MaxResponseMs,AvgResponseMs,AffinityMode\n");
        
        for (Map.Entry<String, RuntimeStats> entry : RUNTIME_STATS.entrySet()) {
            RuntimeStats stats = entry.getValue();
            double avg = stats.executions > 0 ? stats.sumResponseMs / stats.executions : 0;
            sb.append(String.format(Locale.US, "%s,%d,%d,%.2f,%.2f,%s\n",
                    entry.getKey(), stats.executions, stats.deadlineMisses,
                    stats.maxResponseMs, avg, mode));
        }
        
        try (java.io.FileOutputStream fos = context.openFileOutput(filename, android.content.Context.MODE_PRIVATE)) {
            fos.write(sb.toString().getBytes());
            Log.i(TAG, "Métricas STR exportadas para: " + filename);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao exportar CSV de deadlines: " + e.getMessage());
        }
    }

    public static String getRuntimeMetricsReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("RUNTIME_METRICS\n");
        sb.append("task,executions,max_ms,avg_ms,stddev_ms,deadline_misses\n");
        for (TaskDef task : TASKS) {
            RuntimeStats stats = RUNTIME_STATS.get(task.id);
            if (stats == null || stats.executions == 0) continue;
            double avg;
            double std;
            synchronized (stats) {
                avg = stats.sumResponseMs / stats.executions;
                double variance = (stats.sumSquaresResponseMs / stats.executions) - (avg * avg);
                std = Math.sqrt(Math.max(variance, 0));
                sb.append(String.format(Locale.US, "%s,%d,%.3f,%.3f,%.3f,%d\n",
                        task.id, stats.executions, stats.maxResponseMs, avg, std, stats.deadlineMisses));
            }
        }
        return sb.toString();
    }

    public static String getTaskTableSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("TAREFAS (Pi,Ci,Di,Ji,deps)\n");
        for (TaskDef task : TASKS) {
            sb.append(String.format(Locale.US, "%s P=%.0f C=%.0f D=%.0f J=%.0f deps=%s\n",
                    task.id, task.periodMs, task.wcetMs, task.deadlineMs, task.jitterMs, task.dependencies));
        }
        sb.append("GRAFO_DEPENDENCIAS\n");
        for (TaskDef task : TASKS) {
            if (!"-".equals(task.dependencies)) {
                String[] deps = task.dependencies.split(",");
                for (String dep : deps) {
                    sb.append(dep).append(" -> ").append(task.id).append('\n');
                }
            }
        }
        return sb.toString();
    }
}
