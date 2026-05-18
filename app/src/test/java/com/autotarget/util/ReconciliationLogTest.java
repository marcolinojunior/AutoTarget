package com.autotarget.util;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReconciliationLogTest {

    private ReconciliationLog log;

    @Before
    public void setUp() {
        log = ReconciliationLog.getInstance();
        log.reset();
    }

    @Test
    public void testHitRateCalculation() {
        // Simular 10 disparos, 6 acertos
        // 4 disparos errados
        for (int i = 0; i < 4; i++) {
            log.logShot(0, 0, 10, 10, 10, 10, false, "ESQUERDO");
        }
        // 6 disparos certeiros
        for (int i = 0; i < 6; i++) {
            log.logShot(0, 0, 10, 10, 10, 10, true, "ESQUERDO");
        }

        String relatorio = log.gerarRelatorio();
        
        // Esperado: 10 disparos totais, 6 acertos, 60% de taxa
        assertTrue(relatorio.contains("Disparos: 10"));
        assertTrue(relatorio.contains("Acertos: 6"));
        assertTrue(relatorio.contains("Taxa de acerto: 60.00%"));
    }

    @Test
    public void testPerSideHitRate() {
        // ESQUERDO: 5 disparos, 2 acertos (40%)
        log.logShot(0, 0, 10, 10, 10, 10, true, "ESQUERDO");
        log.logShot(0, 0, 10, 10, 10, 10, true, "ESQUERDO");
        log.logShot(0, 0, 10, 10, 10, 10, false, "ESQUERDO");
        log.logShot(0, 0, 10, 10, 10, 10, false, "ESQUERDO");
        log.logShot(0, 0, 10, 10, 10, 10, false, "ESQUERDO");

        // DIREITO: 10 disparos, 8 acertos (80%)
        for (int i = 0; i < 8; i++) log.logShot(0, 0, 10, 10, 10, 10, true, "DIREITO");
        for (int i = 0; i < 2; i++) log.logShot(0, 0, 10, 10, 10, 10, false, "DIREITO");

        // Adicionar uma amostra de performance para ativar a seção no relatório
        log.logPerformanceMetrics("ESQUERDO", 5, 2, 40.0, 10.0, 1);
        log.logPerformanceMetrics("DIREITO", 10, 8, 80.0, 20.0, 1);

        String relatorio = log.gerarRelatorio();

        assertTrue(relatorio.contains("[ESQUERDO] Taxa real: 40.00%"));
        assertTrue(relatorio.contains("[DIREITO]  Taxa real: 80.00%"));
    }
}