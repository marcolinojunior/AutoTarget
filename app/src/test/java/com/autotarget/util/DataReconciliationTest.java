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
}
