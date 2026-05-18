package com.autotarget.util;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Testes unitários para {@link RMAAnalysis}.
 * Valida cálculos de escalonabilidade Rate Monotonic, WCRT, e métricas de runtime.
 */
public class RMAAnalysisTest {

    @Before
    public void setUp() {
        RMAAnalysis.resetRuntimeStats();
    }

    // ── Testes de Liu & Layland Bound ───────────────────────────

    @Test
    public void testLiuLaylandBound_N1() {
        double bound = RMAAnalysis.liuLaylandBound(1);
        assertEquals("LL bound para n=1 deve ser 1.0", 1.0, bound, 1e-10);
    }

    @Test
    public void testLiuLaylandBound_N2() {
        double bound = RMAAnalysis.liuLaylandBound(2);
        // U_bound(2) = 2 * (2^(1/2) - 1) ≈ 0.8284
        assertEquals("LL bound para n=2", 2.0 * (Math.sqrt(2.0) - 1.0), bound, 1e-10);
    }

    @Test
    public void testLiuLaylandBound_N8() {
        double bound = RMAAnalysis.liuLaylandBound(8);
        // U_bound(8) ≈ 0.724
        assertTrue("LL bound para n=8 deve ser ≈ 0.724", bound > 0.7 && bound < 0.8);
    }

    @Test
    public void testLiuLaylandBound_Convergence() {
        // O limite de LL converge para ln(2) ≈ 0.693 quando n → ∞
        double bound = RMAAnalysis.liuLaylandBound(100);
        assertEquals("LL bound para n→∞ deve convergir para ln(2)",
                Math.log(2.0), bound, 0.01);
    }

    // ── Testes de utilização total ──────────────────────────────

    @Test
    public void testTotalUtilization_Positive() {
        double u = RMAAnalysis.totalUtilization();
        assertTrue("Utilização total deve ser positiva", u > 0);
    }

    @Test
    public void testTotalUtilization_LessThanOne() {
        double u = RMAAnalysis.totalUtilization();
        // O sistema deve ter utilização < 1 para ser escalonável
        assertTrue("Utilização total deve ser < 1 para escalonabilidade", u < 1.0);
    }

    // ── Testes de WCRT (Worst Case Response Time) ──────────────

    @Test
    public void testWCRT_Task0_NoInterference() {
        // Tarefa de maior prioridade (índice 0) não tem interferência
        // R0 = C0
        double r0 = RMAAnalysis.worstCaseResponseTime(0);
        assertTrue("WCRT de T0 deve ser positivo", r0 > 0);
        // T0 é PhysicsTimer com C=4ms, então R0 = 4ms
        assertEquals("WCRT de T0 deve ser igual a C0 (4ms)", 4.0, r0, 0.5);
    }

    @Test
    public void testWCRT_Monotonicity() {
        // WCRT deve ser crescente com o índice (menor prioridade = maior R)
        double r0 = RMAAnalysis.worstCaseResponseTime(0);
        double r1 = RMAAnalysis.worstCaseResponseTime(1);
        double r2 = RMAAnalysis.worstCaseResponseTime(2);

        assertTrue("R0 <= R1 (monotonicidade)", r0 <= r1 + 0.01);
        assertTrue("R1 <= R2 (monotonicidade)", r1 <= r2 + 0.01);
    }

    @Test
    public void testWCRT_AllTasksWithinDeadline() {
        // Todas as 8 tarefas devem ter WCRT <= deadline
        double[] deadlines = {16, 16, 30, 33, 1000, 1500, 1000, 10000};
        for (int i = 0; i < 8; i++) {
            double r = RMAAnalysis.worstCaseResponseTime(i);
            assertTrue("WCRT de T" + i + " deve ser positivo, foi: " + r, r > 0);
            assertTrue("WCRT de T" + i + " (" + r + ") deve ser <= deadline (" + deadlines[i] + ")",
                    r <= deadlines[i] + 1.0);
        }
    }

    @Test
    public void testWCRT_InvalidIndex() {
        assertEquals("Índice negativo deve retornar -1", -1.0,
                RMAAnalysis.worstCaseResponseTime(-1), 1e-10);
        assertEquals("Índice fora de faixa deve retornar -1", -1.0,
                RMAAnalysis.worstCaseResponseTime(100), 1e-10);
    }

    // ── Testes de métricas de runtime ───────────────────────────

    @Test
    public void testRecordExecutionAndRuntimeReport() {
        RMAAnalysis.recordExecution("T1-Physics", 5);
        RMAAnalysis.recordExecution("T1-Physics", 9);
        RMAAnalysis.recordExecution("T7-Sensor", 2);

        String report = RMAAnalysis.getRuntimeMetricsReport();
        assertNotNull(report);
        assertTrue(report.contains("task,executions"));
        assertTrue(report.contains("T1-Physics"));
        assertTrue(report.contains("T7-Sensor"));
    }

    @Test
    public void testRecordExecution_DeadlineMiss() {
        // T1-Physics tem deadline de 16ms
        RMAAnalysis.checkDeadline("T1-Physics", 20, 16);

        String report = RMAAnalysis.getRuntimeMetricsReport();
        assertTrue("Deve registrar deadline miss", report.contains("T1-Physics"));
    }

    @Test
    public void testRuntimeReport_Statistics() {
        RMAAnalysis.recordExecution("T1-Physics", 5);
        RMAAnalysis.recordExecution("T1-Physics", 15);

        String report = RMAAnalysis.getRuntimeMetricsReport();
        assertTrue("Deve conter T1-Physics", report.contains("T1-Physics"));
    }

    @Test
    public void testResetRuntimeStats() {
        RMAAnalysis.recordExecution("T1-Physics", 5);
        RMAAnalysis.resetRuntimeStats();

        String report = RMAAnalysis.getRuntimeMetricsReport();
        assertFalse("Após reset, não deve conter T1-Physics", report.contains("T1-Physics"));
    }

    // ── Testes de tabela de tarefas ─────────────────────────────

    @Test
    public void testTaskTableSummaryContainsJitterAndDeps() {
        String summary = RMAAnalysis.getTaskTableSummary();
        assertNotNull(summary);
        assertTrue(summary.contains("Ji"));
        assertTrue(summary.contains("deps"));
        assertTrue(summary.contains("T8-Reconciliacao"));
    }

    @Test
    public void testTaskTableSummary_AllTasksPresent() {
        String summary = RMAAnalysis.getTaskTableSummary();
        String[] taskIds = {
                "T1-Physics", "T2-Projetil", "T3-Alvo", "T4-Render",
                "T5-GameTimer", "T6-Canhao", "T7-Sensor", "T8-Reconciliacao"
        };
        for (String id : taskIds) {
            assertTrue("Tabela deve conter " + id, summary.contains(id));
        }
    }

    @Test
    public void testTaskTableSummary_DependencyGraph() {
        String summary = RMAAnalysis.getTaskTableSummary();
        assertTrue("Deve conter grafo de dependências",
                summary.contains("GRAFO_DEPENDENCIAS"));
        assertTrue("T3 -> T1 deve estar no grafo", summary.contains("T3 -> T1-Physics"));
        assertTrue("T7 -> T8 deve estar no grafo", summary.contains("T7 -> T8-Reconciliacao"));
    }

    // ── Testes de escalonabilidade do sistema ───────────────────

    @Test
    public void testSystemScalability_LiuLayland() {
        double u = RMAAnalysis.totalUtilization();
        double bound = RMAAnalysis.liuLaylandBound(8);

        System.out.println(String.format(java.util.Locale.US,
                "RMA SCALABILITY: U=%.4f, U_bound=%.4f, Escalonável=%s",
                u, bound, u <= bound ? "SIM (LL)" : "INCONCLUSIVO (verificar WCRT)"));

        boolean llSufficient = u <= bound;
        boolean wcrSufficient = true;
        double[] deadlines = {16, 16, 30, 33, 1000, 1500, 1000, 10000};
        for (int i = 0; i < 8; i++) {
            double r = RMAAnalysis.worstCaseResponseTime(i);
            if (r > deadlines[i]) {
                wcrSufficient = false;
                break;
            }
        }
        assertTrue("Sistema deve ser escalonável (LL ou WCRT)", llSufficient || wcrSufficient);
    }

    // ── Testes de checkDeadline ─────────────────────────────────

    @Test
    public void testCheckDeadline_OnTime() {
        RMAAnalysis.checkDeadline("T1-Physics", 10, 16);
    }

    @Test
    public void testCheckDeadline_Miss() {
        RMAAnalysis.resetRuntimeStats();
        RMAAnalysis.checkDeadline("T1-Physics", 25, 16);

        String report = RMAAnalysis.getRuntimeMetricsReport();
        assertTrue("Deve conter T1-Physics após miss", report.contains("T1-Physics"));
    }
}
