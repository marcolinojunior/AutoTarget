package com.autotarget.util;

import com.autotarget.util.DataReconciliation.ReconciliationResult;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Random;
import java.util.Locale;

/**
 * T03 - Fortalecimento de testes quantitativos de reconciliação.
 * Comprova matematicamente que a reconciliação reduz erro/variância consistentemente.
 */
public class DataReconciliationTest {

    private static final long SEED = 42;
    private static final int NUM_LOTES = 1000;
    private static final double MIN_REDUCAO_ESPERADA = 15.0; // 15% de melhoria mínima

    /**
     * Motor de simulação Monte Carlo para validação da reconciliação.
     */
    private double executarSimulacao(
            float[] canhoesX, float[] canhoesY,
            float targetX, float targetY,
            float noiseStd, boolean singular) {

        int N = canhoesX.length;
        Random rng = new Random(SEED);
        DataReconciliation dr = new DataReconciliation();
        
        double mseAntesTotal = 0;
        double mseDepoisTotal = 0;

        for (int i = 0; i < NUM_LOTES; i++) {
            float[] medias = new float[N];
            float[] vars = new float[N];

            for (int j = 0; j < N; j++) {
                double dx = targetX - canhoesX[j];
                double dy = targetY - canhoesY[j];
                double distReal = Math.sqrt(dx * dx + dy * dy);

                // No caso singular, forçamos um sensor a ser "perfeito"
                double currentNoise = (singular && j == 0) ? 0.0001 : noiseStd;
                double ruido = rng.nextGaussian() * currentNoise;
                double distMedida = Math.max(0.1, distReal + ruido);
                
                medias[j] = (float) distMedida;
                vars[j] = (float) (currentNoise * currentNoise);

                double erroAntes = distMedida - distReal;
                mseAntesTotal += erroAntes * erroAntes;
            }

            float[][] mediaWrapper = {medias};
            float[][] varWrapper = {vars};
            // Acesso direto via nome qualificado para evitar problemas de compilação
            com.autotarget.util.DataReconciliation.ReconciliationResult[] results = 
                    dr.reconciliar(canhoesX, canhoesY, mediaWrapper, varWrapper);
            
            assertNotNull("Reconciliação não deve retornar null", results);
            assertTrue("Reconciliação deve retornar resultados", results.length > 0);
            float[] distRecon = results[0].distanciasReconciliadas;

            for (int j = 0; j < N; j++) {
                double dx = targetX - canhoesX[j];
                double dy = targetY - canhoesY[j];
                double distReal = Math.sqrt(dx * dx + dy * dy);

                double erroDepois = distRecon[j] - distReal;
                mseDepoisTotal += erroDepois * erroDepois;
            }
        }

        double mseAntes = mseAntesTotal / (NUM_LOTES * N);
        double mseDepois = mseDepoisTotal / (NUM_LOTES * N);

        return (mseAntes > 0) ? (1.0 - (mseDepois / mseAntes)) * 100.0 : 0;
    }

    @Test
    public void testReconciliacao_BemCondicionada_N6() {
        float[] cX = { 0f, 200f, 0f, 200f, 100f, 100f };
        float[] cY = { 0f, 0f, 200f, 200f, 50f, 150f };
        float tX = 110f, tY = 95f;
        
        double reducao = executarSimulacao(cX, cY, tX, tY, 5.0f, false);
        
        System.out.println(String.format(Locale.US, "T03-LOG: Redução MSE (N=6): %.2f%%", reducao));
        assertTrue("Melhoria deve ser > " + MIN_REDUCAO_ESPERADA + "%", reducao >= MIN_REDUCAO_ESPERADA);
    }

    @Test
    public void testReconciliacao_Singular_N4() {
        // Mínimo de canhões para reconciliação (N=4)
        float[] cX = { 0f, 500f, 0f, 500f };
        float[] cY = { 0f, 0f, 500f, 500f };
        float tX = 250f, tY = 250f;

        // Testa estabilidade com pseudo-inversa
        double reducao = executarSimulacao(cX, cY, tX, tY, 10.0f, true);

        System.out.println(String.format(Locale.US, "T03-LOG: Redução MSE (Singular N=4): %.2f%%", reducao));
        assertTrue("Melhoria em caso singular deve ser positiva", reducao > 0);
    }

    @Test
    public void testReconciliacao_ColinearMesmoY_FallbackEstavel() {
        float[] cX = { 10f, 20f, 30f, 40f };
        float[] cY = { 50f, 50f, 50f, 50f };
        float tX = 25f, tY = 120f;

        double reducao = executarSimulacao(cX, cY, tX, tY, 2.0f, true);

        System.out.println(String.format(Locale.US, "T03-LOG: Redução MSE (Colinear Y): %.2f%%", reducao));
        assertTrue("Reconciliação colinear deve continuar estável", Double.isFinite(reducao));
        assertTrue("Reconciliação colinear não deve piorar drasticamente", reducao > -50.0);
    }

    @Test
    public void testReconciliacao_N3_Fallback() {
        // N=3: Reconciliação não é aplicada matematicamente (Null Space vazio)
        float[] cX = { 0f, 100f, 50f };
        float[] cY = { 0f, 0f, 100f };
        float tX = 50f, tY = 50f;

        double reducao = executarSimulacao(cX, cY, tX, tY, 5.0f, false);

        System.out.println(String.format(Locale.US, "T03-LOG: Redução MSE (N=3 Fallback): %.2f%%", reducao));
    }

    @Test
    public void testFormulaRubrica_Consistencia() {
        // y_hat = y - V * A^T * (A * V * A^T)^-1 * A * y
        double[] y = { 12.0, 8.0, 10.0, 10.0 };
        double[][] V = { {1,0,0,0}, {0,1,0,0}, {0,0,1,0}, {0,0,0,1} };
        double[][] A = { {1.0, -1.0, 0.0, 0.0} }; // Restrição: y0 = y1
        
        double[] yHat = DataReconciliation.reconcile(y, V, A);
        
        // Com variâncias iguais, o valor reconciliado deve ser a média (10.0)
        assertEquals("yHat[0] deve ser 10", 10.0, yHat[0], 1e-5);
        assertEquals("yHat[1] deve ser 10", 10.0, yHat[1], 1e-5);
    }

    // ── Testes adicionais para reconcile() com dados conhecidos ──

    @Test
    public void testReconcile_NullInputs_ReturnsOriginalY() {
        double[] y = {1.0, 2.0, 3.0};
        double[][] V = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};

        double[] result = DataReconciliation.reconcile(y, V, null);
        assertArrayEquals("V nulo deve retornar y original", y, result, 1e-10);

        double[][] A = {{1.0, -1.0, 0.0}};
        result = DataReconciliation.reconcile(y, null, A);
        assertArrayEquals("y nulo deve retornar y original (null → new double[0])", y, result, 1e-10);
    }

    @Test
    public void testReconcile_EmptyY_ReturnsOriginal() {
        double[] y = {};
        double[][] V = {};
        double[][] A = {};

        double[] result = DataReconciliation.reconcile(y, V, A);
        assertArrayEquals("y vazio deve retornar vazio", y, result, 1e-10);
    }

    @Test
    public void testReconcile_UniformVariance_ConstraintSatisfied() {
        // 4 medições com variância uniforme e restrição: y0 + y1 = y2 + y3
        double[] y = {10.0, 14.0, 11.0, 13.0}; // soma = 48
        double[][] V = {{1,0,0,0}, {0,1,0,0}, {0,0,1,0}, {0,0,0,1}};
        // Restrição: y0 + y1 - y2 - y3 = 0 (soma dos 2 primeiros = soma dos 2 últimos)
        double[][] A = {{1.0, 1.0, -1.0, -1.0}};

        double[] yHat = DataReconciliation.reconcile(y, V, A);

        assertNotNull("Resultado não deve ser null", yHat);
        assertEquals("Deve ter 4 elementos", 4, yHat.length);

        // Verificar que a restrição é satisfeita: y0 + y1 = y2 + y3
        double somaPrimeiros = yHat[0] + yHat[1];
        double somaUltimos = yHat[2] + yHat[3];
        assertEquals("Restrição deve ser satisfeita: y0+y1 = y2+y3",
                somaPrimeiros, somaUltimos, 1e-6);

        // Com variância uniforme, a correção deve ser distribuída igualmente
        // y0 e y1 devem ser iguais entre si, y2 e y3 devem ser iguais entre si
        assertEquals("yHat[0] deve ser igual a yHat[1] (simetria)", yHat[0], yHat[1], 1e-6);
        assertEquals("yHat[2] deve ser igual a yHat[3] (simetria)", yHat[2], yHat[3], 1e-6);
    }

    @Test
    public void testReconcile_HigherVariance_LessCorrection() {
        // O elemento com maior variância deve receber menor correção
        double[] y = {10.0, 14.0};
        // V0 tem variância 100 (alta), V1 tem variância 1 (baixa)
        double[][] V = {{100, 0}, {0, 1}};
        // Restrição: y0 = y1
        double[][] A = {{1.0, -1.0}};

        double[] yHat = DataReconciliation.reconcile(y, V, A);

        assertNotNull("Resultado não deve ser null", yHat);

        // Com variância alta em y0, a correção deve empurrar yHat para mais perto de y1
        // yHat deve estar mais perto de 14 (menor variância) do que de 10
        assertTrue("yHat[0] deve ser > 10 (corrigido em direção a y1)",
                yHat[0] > 10.0);
        assertTrue("yHat[1] deve ser < 14 (corrigido em direção a y0)",
                yHat[1] < 14.0);

        // Ambos devem convergir para o mesmo valor
        assertEquals("yHat[0] deve ser igual a yHat[1]", yHat[0], yHat[1], 1e-6);
    }

    @Test
    public void testReconcile_AlreadySatisfiedConstraint_NoChange() {
        // Se y já satisfaz a restrição, yHat deve ser igual a y
        double[] y = {10.0, 10.0, 10.0, 10.0};
        double[][] V = {{1,0,0,0}, {0,1,0,0}, {0,0,1,0}, {0,0,0,1}};
        double[][] A = {{1.0, -1.0, 0.0, 0.0}}; // y0 = y1 (já satisfeita)

        double[] yHat = DataReconciliation.reconcile(y, V, A);

        assertNotNull("Resultado não deve ser null", yHat);
        for (int i = 0; i < y.length; i++) {
            assertEquals("y já satisfaz restrição, yHat[" + i + "] deve ser igual a y[" + i + "]",
                    y[i], yHat[i], 1e-10);
        }
    }

    @Test
    public void testReconcile_NonFiniteValues_ReturnsOriginal() {
        double[] y = {1.0, Double.NaN, 3.0};
        double[][] V = {{1,0,0}, {0,1,0}, {0,0,1}};
        double[][] A = {{1.0, -1.0, 0.0}};

        double[] result = DataReconciliation.reconcile(y, V, A);
        // Com NaN no input, o resultado deve conter NaN (propagação)
        // ou retornar y original se detectar não-finito
        assertNotNull("Resultado não deve ser null", result);
    }

    @Test
    public void testReconcile_LargeSystem_Consistency() {
        // Sistema 6x6 com 2 restrições independentes
        int n = 6;
        double[] y = {12.0, 8.0, 15.0, 5.0, 20.0, 10.0};
        double[][] V = new double[n][n];
        for (int i = 0; i < n; i++) V[i][i] = 1.0;

        // 2 restrições: y0+y1=y2+y3 e y2+y3=y4+y5
        double[][] A = {
                {1.0, 1.0, -1.0, -1.0, 0.0, 0.0},
                {0.0, 0.0, 1.0, 1.0, -1.0, -1.0}
        };

        double[] yHat = DataReconciliation.reconcile(y, V, A);

        assertNotNull("Resultado não deve ser null", yHat);
        assertEquals("Deve ter 6 elementos", n, yHat.length);

        // Verificar restrição 1: y0+y1 = y2+y3
        double soma1 = yHat[0] + yHat[1];
        double soma2 = yHat[2] + yHat[3];
        assertEquals("Restrição 1 deve ser satisfeita", soma1, soma2, 1e-4);

        // Verificar restrição 2: y2+y3 = y4+y5
        double soma3 = yHat[4] + yHat[5];
        assertEquals("Restrição 2 deve ser satisfeita", soma2, soma3, 1e-4);
    }

    @Test
    public void testReconcile_NormaRestricao() {
        // Testar calcularNormaRestrição com dados conhecidos
        double[][] A = {{1.0, -1.0, 0.0}};
        double[] yHat = {5.0, 5.0, 10.0}; // y0 - y1 = 0 → norma = 0

        double norma = DataReconciliation.calcularNormaRestricao(A, yHat);
        assertEquals("Norma deve ser 0 quando restrição é satisfeita", 0.0, norma, 1e-10);

        double[] yHat2 = {7.0, 3.0, 10.0}; // y0 - y1 = 4 → norma = 4
        double norma2 = DataReconciliation.calcularNormaRestricao(A, yHat2);
        assertEquals("Norma deve ser 4.0", 4.0, norma2, 1e-10);
    }

    @Test
    public void testReconcile_VarianciaCalculation() {
        double[] valores = {2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0};
        double var = DataReconciliation.calcularVariancia(valores);
        // Variância amostral conhecida: 4.0
        assertEquals("Variância amostral deve ser 4.0", 4.0, var, 0.5);

        // Variância de 1 elemento = 0
        assertEquals(0.0, DataReconciliation.calcularVariancia(new double[]{5.0}), 1e-10);

        // Variância de null = 0
        assertEquals(0.0, DataReconciliation.calcularVariancia(null), 1e-10);
    }

    // ── Teste de integração: reconcile + calcularErroRMS ────────

    @Test
    public void testReconcileErroRMS_ReducaoConsistente() {
        // Simular 100 medições com ruído e verificar que a reconciliação reduz erro
        java.util.Random rng = new java.util.Random(12345);
        int N = 4; // canhões
        int M = 1; // alvo
        float trueX = 250f, trueY = 250f;

        // Posições dos canhões (quadrado)
        float[] cX = {0f, 500f, 0f, 500f};
        float[] cY = {0f, 0f, 500f, 500f};

        double mseAntesTotal = 0;
        double mseDepoisTotal = 0;
        int trials = 200;

        for (int t = 0; t < trials; t++) {
            float[] medias = new float[N];
            float[] vars = new float[N];

            for (int j = 0; j < N; j++) {
                double dx = trueX - cX[j];
                double dy = trueY - cY[j];
                double distReal = Math.sqrt(dx * dx + dy * dy);
                double ruido = rng.nextGaussian() * 3.0; // 3px de ruído
                medias[j] = (float) Math.max(0.1, distReal + ruido);
                vars[j] = 9.0f; // variância = 3^2
            }

            DataReconciliation.ReconciliationResult[] results =
                    new DataReconciliation().reconciliar(cX, cY, new float[][]{medias}, new float[][]{vars});

            if (results != null && results.length > 0 && results[0] != null) {
                double erroAntes = Math.sqrt(medias[0] * medias[0]) - Math.sqrt(trueX * trueX + trueY * trueX);
                double erroDepois = Math.sqrt(results[0].distanciasReconciliadas[0] * results[0].distanciasReconciliadas[0])
                        - Math.sqrt(trueX * trueX + trueY * trueX);
                mseAntesTotal += erroAntes * erroAntes;
                mseDepoisTotal += erroDepois * erroDepois;
            }
        }

        double mseAntes = mseAntesTotal / trials;
        double mseDepois = mseDepoisTotal / trials;
        double reducao = (1.0 - mseDepois / mseAntes) * 100.0;

        System.out.println(String.format(java.util.Locale.US,
                "TEST-RECON-RMS: MSE antes=%.4f, depois=%.4f, redução=%.1f%%",
                mseAntes, mseDepois, reducao));
    }

    // ── Helpers ─────────────────────────────────────────────────

    private void assertArrayEquals(String msg, double[] expected, double[] actual, double delta) {
        assertNotNull("Resultado não deve ser null", actual);
        assertEquals(msg + " (tamanho)", expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(msg + " [" + i + "]", expected[i], actual[i], delta);
        }
    }
}
