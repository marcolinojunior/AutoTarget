package com.autotarget.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class DataReconciliationTest {

    @Test
    public void testReconcileExactSignature() {
        // Arrange
        // N = 4 canhões
        double[] y = { 10.0, 12.0, 9.0, 11.0 };
        double[][] V = {
            { 0.1, 0.0, 0.0, 0.0 },
            { 0.0, 0.1, 0.0, 0.0 },
            { 0.0, 0.0, 0.1, 0.0 },
            { 0.0, 0.0, 0.0, 0.1 }
        };
        // Para N=4, o espaço nulo de M (N x 3) tem dimensão 1.
        // A é de dimensão 1 x 4
        double[][] A = {
            { 0.5, -0.5, -0.5, 0.5 }
        };

        // Act
        double[] yHat = DataReconciliation.reconcile(y, V, A);

        // Assert
        assertNotNull(yHat);
        assertEquals(4, yHat.length);

        // Verificar a restrição Ay_hat = 0
        double sum = 0;
        for (int i = 0; i < 4; i++) {
            sum += A[0][i] * yHat[i];
        }
        assertEquals(0.0, sum, 1e-6);
    }

    @Test
    public void testConstruirMatrizIncidenciaPorLimiar() {
        float[] medias = {40f, 20f, 22f, 35f, 21f};
        double limiar = 25.0;

        double[][] a = DataReconciliation.construirMatrizIncidenciaPorLimiar(medias, limiar);

        assertNotNull(a);
        assertEquals(2, a.length);
        assertEquals(4, a[0].length);

        // conectados esperados (sem canhão 0): 1,2,4 -> índices internos 0,1,3
        // linhas: [1,-1,0,0] e [0,1,0,-1]
        assertEquals(1.0, a[0][0], 1e-9);
        assertEquals(-1.0, a[0][1], 1e-9);
        assertEquals(1.0, a[1][1], 1e-9);
        assertEquals(-1.0, a[1][3], 1e-9);
    }

    @Test
    public void testConstruirMatrizIncidenciaPorLimiarSemConectividade() {
        float[] medias = {40f, 30f, 31f, 32f};
        double[][] a = DataReconciliation.construirMatrizIncidenciaPorLimiar(medias, 10.0);
        assertNull(a);
    }
}
