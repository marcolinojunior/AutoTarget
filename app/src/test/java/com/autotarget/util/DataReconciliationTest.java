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
        assertEquals(5, a[0].length);

        // conectados esperados: 1,2,4 -> índices 1,2,4
        // linhas: [0, 1,-1,0,0] e [0, 0,1,0,-1]
        assertEquals(1.0, a[0][1], 1e-9);
        assertEquals(-1.0, a[0][2], 1e-9);
        assertEquals(1.0, a[1][2], 1e-9);
        assertEquals(-1.0, a[1][4], 1e-9);
    }

    @Test
    public void testConstruirMatrizIncidenciaPorLimiarSemConectividade() {
        float[] medias = {40f, 30f, 31f, 32f};
        double[][] a = DataReconciliation.construirMatrizIncidenciaPorLimiar(medias, 10.0);
        assertNull(a);
    }

    @Test
    public void testReconcileUsaPseudoInverseQuandoSingular() {
        double[] y = {2.0, 3.0, 4.0};
        double[][] V = {
                {1.0, 0.0, 0.0},
                {0.0, 1.0, 0.0},
                {0.0, 0.0, 1.0}
        };
        // Linhas dependentes -> A*V*A^T singular
        double[][] A = {
                {1.0, -1.0, 0.0},
                {2.0, -2.0, 0.0}
        };

        double[] yHat = DataReconciliation.reconcile(y, V, A);
        assertNotNull(yHat);
        assertEquals(3, yHat.length);
        for (double v : yHat) {
            assertTrue(Double.isFinite(v));
        }
    }

    @Test
    public void testReconciliarFallbackQuandoMenosDeQuatroCanhoes() {
        DataReconciliation dr = new DataReconciliation();
        float[] canhoesX = {0f, 100f, 0f};
        float[] canhoesY = {0f, 0f, 100f};
        float[][] media = {{70f, 50f, 50f}};
        float[][] variancia = {{1f, 1f, 1f}};

        DataReconciliation.ReconciliationResult[] out = dr.reconciliar(canhoesX, canhoesY, media, variancia);
        assertNotNull(out);
        assertEquals(1, out.length);
    }

    @Test
    public void testReconciliarComCanhoesColinearesNaoFalha() {
        DataReconciliation dr = new DataReconciliation();
        float[] canhoesX = {0f, 100f, 200f, 300f};
        float[] canhoesY = {0f, 0f, 0f, 0f};
        float[][] media = {{150f, 70f, 70f, 150f}};
        float[][] variancia = {{2f, 2f, 2f, 2f}};

        DataReconciliation.ReconciliationResult[] out = dr.reconciliar(canhoesX, canhoesY, media, variancia);
        assertNotNull(out);
    }
}
