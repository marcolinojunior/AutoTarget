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
}
