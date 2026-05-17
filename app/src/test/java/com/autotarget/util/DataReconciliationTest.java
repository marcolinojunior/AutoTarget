package com.autotarget.util;

import org.junit.Test;
import static org.junit.Assert.*;

import org.ejml.simple.SimpleMatrix;
import java.util.Random;

public class DataReconciliationTest {

    /**
     * Executa uma simulação quantitativa sintética da projeção puramente matricial.
     * Isola o Left Null Space e o método Delta para provar a redução do erro MSE
     * desconsiderando heurísticas de restrição do domínio WLS.
     */
    private double executarSimulacaoProjecaoPura(
            float[] canhoesX, float[] canhoesY,
            float targetX, float targetY,
            float[] desvioPadraoRuidos, float[] varianciaDeclarada) {

        int N = canhoesX.length;
        int numLotes = 100;
        Random rng = new Random(42);

        DataReconciliation dr = new DataReconciliation();
        SimpleMatrix matM = new SimpleMatrix(N, 3);
        for (int j = 0; j < N; j++) {
            matM.set(j, 0, 1.0);
            matM.set(j, 1, -2.0 * canhoesX[j]);
            matM.set(j, 2, -2.0 * canhoesY[j]);
        }
        SimpleMatrix C = dr.computeLeftNullSpace(matM, N);

        double mseAntesTotal = 0;
        double mseDepoisTotal = 0;

        for (int i = 0; i < numLotes; i++) {
            float[] medias = new float[N];
            float[] vars = new float[N];

            for (int j = 0; j < N; j++) {
                double dx = targetX - canhoesX[j];
                double dy = targetY - canhoesY[j];
                double distReal = Math.sqrt(dx * dx + dy * dy);

                double ruido = rng.nextGaussian() * desvioPadraoRuidos[j];
                double distMedida = distReal + ruido;
                if (distMedida < 0) distMedida = 0;

                medias[j] = (float) distMedida;
                vars[j] = varianciaDeclarada[j];

                double erroAntes = distMedida - distReal;
                mseAntesTotal += erroAntes * erroAntes;
            }

            float[] distReconciliadas = dr.calcularProjecaoMatricial(N, canhoesX, canhoesY, medias, vars, C);

            for (int j = 0; j < N; j++) {
                double dx = targetX - canhoesX[j];
                double dy = targetY - canhoesY[j];
                double distReal = Math.sqrt(dx * dx + dy * dy);

                double erroDepois = distReconciliadas[j] - distReal;
                mseDepoisTotal += erroDepois * erroDepois;
            }
        }

        double mseAntes = mseAntesTotal / (numLotes * N);
        double mseDepois = mseDepoisTotal / (numLotes * N);

        if (mseAntes == 0) return 0;
        return (1.0 - (mseDepois / mseAntes)) * 100.0;
    }

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

    @Test
    public void testQuantitativoMatrizBemCondicionada_NMaiorIgual4() {
        // Arranjo de 6 canhões distribuídos circularmente
        float[] canhoesX = { 0f, 100f, 50f, -50f, -100f, -50f };
        float[] canhoesY = { 100f, 50f, -50f, -100f, -50f, 50f };
        float targetX = 10f;
        float targetY = 20f;

        float[] desvioPadrao = { 2.0f, 2.0f, 2.0f, 2.0f, 2.0f, 2.0f };
        float[] varianciaDecl = { 4.0f, 4.0f, 4.0f, 4.0f, 4.0f, 4.0f };

        double reducaoMse = executarSimulacaoProjecaoPura(canhoesX, canhoesY, targetX, targetY, desvioPadrao, varianciaDecl);

        System.out.println("Redução MSE (Matriz Bem Condicionada - L2 Puramente Matricial): " + reducaoMse + "%");

        // A matemática da projeção em C deve entregar uma melhoria consistente no MSE das distâncias L2.
        assertTrue("A redução de MSE puramente matricial deve ser > 15%. Redução foi: " + reducaoMse + "%", reducaoMse > 15.0);
    }

    @Test
    public void testQuantitativoSingular_VarianciaZero() {
        // Arranjo de 6 canhões
        float[] canhoesX = { 0f, 100f, 50f, -50f, -100f, -50f };
        float[] canhoesY = { 100f, 50f, -50f, -100f, -50f, 50f };
        float targetX = 10f;
        float targetY = 20f;

        // Canhões 0 e 1 têm leituras "perfeitas" (ruído zero, variância zero)
        // Força a pseudo-inversa
        float[] desvioPadrao = { 0.0f, 0.0f, 3.0f, 3.0f, 3.0f, 3.0f };
        float[] varianciaDecl = { 0.0f, 0.0f, 9.0f, 9.0f, 9.0f, 9.0f };

        double reducaoMse = executarSimulacaoProjecaoPura(canhoesX, canhoesY, targetX, targetY, desvioPadrao, varianciaDecl);

        System.out.println("Redução MSE (V Singular - L2 Puramente Matricial): " + reducaoMse + "%");

        assertTrue("A redução de MSE usando pseudo-inversa puramente matricial deve ser > 15%. Redução foi: " + reducaoMse + "%", reducaoMse > 15.0);
    }

    @Test
    public void testQuantitativoNMenorQue4() {
        // Arranjo de 3 canhões (sem null space)
        float[] canhoesX = { 0f, 100f, 50f };
        float[] canhoesY = { 100f, 0f, -100f };
        float targetX = 10f;
        float targetY = 20f;

        float[] desvioPadrao = { 2.0f, 2.0f, 2.0f };
        float[] varianciaDecl = { 4.0f, 4.0f, 4.0f };

        DataReconciliation dr = new DataReconciliation();
        SimpleMatrix matM = new SimpleMatrix(3, 3);
        for (int j = 0; j < 3; j++) {
            matM.set(j, 0, 1.0);
            matM.set(j, 1, -2.0 * canhoesX[j]);
            matM.set(j, 2, -2.0 * canhoesY[j]);
        }
        SimpleMatrix C = dr.computeLeftNullSpace(matM, 3);

        // N < 4 não possui espaço nulo esquerdo válido (M tem rank 3). Logo C será nulo.
        assertNull("O espaço nulo C deve ser nulo para N=3.", C);
    }

    @Test
    public void testHeuristicaWLS_RespeitaLimitesFisicos() {
        // Testa a rota principal de produção (reconciliar) que incorpora
        // as heurísticas de restrição de domínio via WLS.
        // O MSE estatístico pode aumentar comparado com a minimização pura L2,
        // mas as restrições físicas (e.g. distâncias não-negativas e coordenadas razoáveis)
        // devem ser estritamente preservadas.
        float[] canhoesX = { 0f, 100f, 50f, -50f, -100f, -50f };
        float[] canhoesY = { 100f, 50f, -50f, -100f, -50f, 50f };

        float[][] media = { { 30f, 80f, 60f, 110f, 150f, 90f } };
        float[][] variancia = { { 4.0f, 4.0f, 4.0f, 4.0f, 4.0f, 4.0f } };

        DataReconciliation dr = new DataReconciliation();
        DataReconciliation.ReconciliationResult[] out = dr.reconciliar(canhoesX, canhoesY, media, variancia);

        assertNotNull(out);
        assertEquals(1, out.length);

        DataReconciliation.ReconciliationResult res = out[0];

        // As coordenadas estimadas WLS não podem ser NaN ou Infinitas
        assertTrue("X estimado deve ser finito", Double.isFinite(res.x));
        assertTrue("Y estimado deve ser finito", Double.isFinite(res.y));

        // Todas as distâncias backward-recalculadas devem ser estritamente >= 0
        for (float d : res.distanciasReconciliadas) {
            assertTrue("A distância heurística após projeção WLS deve ser não-negativa", d >= 0f);
        }
    }
}
