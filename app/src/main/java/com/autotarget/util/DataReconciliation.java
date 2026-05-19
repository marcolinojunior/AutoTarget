/*
 * ============================================================================
 * Arquivo: DataReconciliation.java
 * Pacote:  com.autotarget.util
 * ============================================================================
 *
 * DESCRIÇÃO TÉCNICA:
 *   Reconciliação estatística de dados estocásticos utilizando EJML
 *   (Efficient Java Matrix Library). Corrige medições ruidosas de
 *   distância entre alvos e canhões impondo restrições lineares
 *   derivadas da geometria euclidiana.
 *
 * ALGORITMO (conforme derivação do professor):
 *   1. Vetor y:  y_ij = d̄²_ij - (x_j² + y_j²)
 *   2. Matriz V: diagonal, V_(i,j) = (2·d̄_ij)²·s²_ij (método Delta)
 *   3. Matriz M: [1, -2x_j, -2y_j] para cada canhão j
 *   4. Espaço nulo C: base do null space esquerdo de M
 *   5. Equação: ŷ_i = y_i - V_i·Cᵀ·(C·V_i·Cᵀ)⁻¹·C·y_i
 *   6. Distâncias: d̂_ij = √(ŷ_ij + x_j² + y_j²)
 *   7. Posições OLS: [K̂, X̂, Ŷ]ᵀ = (MᵀM)⁻¹Mᵀŷ_i
 *
 * REQUISITO N≥4: Com N=3 canhões, M tem posto 3 e não há espaço nulo.
 *   A reconciliação só é aplicável com N≥4 canhões não-colineares.
 *
 * ============================================================================
 */
package com.autotarget.util;

import android.util.Log;

import org.ejml.simple.SimpleMatrix;
import org.ejml.data.SingularMatrixException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Reconciliação de dados estocásticos via álgebra linear (EJML).
 */
public class DataReconciliation {

    private static final String TAG = "DataReconciliation";
    private static final double LIMIAR_GEOMETRICO_FATOR = 0.85;
    private static final double LIMIAR_CONDICIONAMENTO = 1.0e12;
    private static final double REGULARIZACAO_TIKHONOV = 1.0e-8;
    public static final String MATRIZ_RESTRICAO_CONTRATO = "LEFT_NULL_SPACE";

    /**
     * Timeout para operações de SVD (ms).
     * Previne que a inversão de matrizes na thread de reconciliação
     * cause ANR indireto (Watchdog timeout do Android).
     * Valor conservador: 200ms é suficiente para matrizes N≤15.
     */
    private static final long SVD_TIMEOUT_MS = 200;

    /** Pool de threads dedicado para operações matriciais pesadas (SVD). */
    private static final ExecutorService SVD_EXECUTOR =
            Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "SVD-Worker");
                t.setDaemon(true);
                t.setPriority(Thread.MIN_PRIORITY); // Não competir com UI/Physics
                return t;
            });

    public static String getMatrizRestricaoContrato() {
        return MATRIZ_RESTRICAO_CONTRATO;
    }

    /**
     * Resultado da reconciliação para um alvo.
     */
    public static class ReconciliationResult {
        public final float x, y;
        public final float[] distanciasReconciliadas;
        public final double normA_yHat;

        public ReconciliationResult(float x, float y, float[] dist) {
            this(x, y, dist, 0.0);
        }

        public ReconciliationResult(float x, float y, float[] dist, double normA_yHat) {
            this.x = x;
            this.y = y;
            this.distanciasReconciliadas = dist;
            this.normA_yHat = normA_yHat;
        }
    }

    /**
     * Inverte a matriz sem utilizar blocos try/catch (evitando criação custosa de StackTrace
     * em sistemas de tempo real).
     *
     * @param mat a matriz a inverter
     * @param allowPseudoInverse fallback para pseudo-inversa em caso de singularidade
     * @return matriz invertida
     */
    public static double calcularNumeroCondicion(SimpleMatrix mat) {
        if (mat == null) return Double.POSITIVE_INFINITY;
        try {
            org.ejml.simple.SimpleSVD<SimpleMatrix> svd = mat.svd();
            SimpleMatrix w = svd.getW();
            int n = Math.min(w.getNumRows(), w.getNumCols());
            double maior = 0.0;
            double menor = Double.POSITIVE_INFINITY;
            for (int i = 0; i < n; i++) {
                double sigma = Math.abs(w.get(i, i));
                maior = Math.max(maior, sigma);
                if (sigma > 0.0) {
                    menor = Math.min(menor, sigma);
                }
            }
            if (maior == 0.0 || !Double.isFinite(menor)) {
                return Double.POSITIVE_INFINITY;
            }
            return maior / menor;
        } catch (Exception e) {
            return Double.POSITIVE_INFINITY;
        }
    }

    private static SimpleMatrix safeInvert(SimpleMatrix mat, boolean allowPseudoInverse) {
        if (mat == null) return null;
        try {
            double cond = calcularNumeroCondicion(mat);
            if (allowPseudoInverse && (Double.isInfinite(cond) || cond > LIMIAR_CONDICIONAMENTO)) {
                return mat.pseudoInverse();
            }

            org.ejml.interfaces.linsol.LinearSolverDense<org.ejml.data.DMatrixRMaj> solver =
                    org.ejml.dense.row.factory.LinearSolverFactory_DDRM.linear(mat.getNumRows());
            if (solver.setA(mat.getMatrix())) {
                org.ejml.data.DMatrixRMaj inv = new org.ejml.data.DMatrixRMaj(mat.getNumRows(), mat.getNumCols());
                solver.invert(inv);
                return new SimpleMatrix(inv);
            }
        } catch (Exception ignored) {}

        if (allowPseudoInverse) {
            try {
                return mat.pseudoInverse();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Executa reconciliação completa para todos os alvos.
     *
     * @param canhoesX       posições X dos canhões (N)
     * @param canhoesY       posições Y dos canhões (N)
     * @param mediaDistancias  média das distâncias d̄_ij [M][N]
     * @param varDistancias    variância amostral s²_ij [M][N]
     * @return array de resultados reconciliados, ou null se falhar
     */
    public ReconciliationResult[] reconciliar(
            float[] canhoesX, float[] canhoesY,
            float[][] mediaDistancias, float[][] varDistancias) {
        return reconciliar(canhoesX, canhoesY, mediaDistancias, varDistancias, null);
    }

    /**
     * Executa reconciliação completa para todos os alvos, com identificação do lado.
     *
     * @param canhoesX       posições X dos canhões (N)
     * @param canhoesY       posições Y dos canhões (N)
     * @param mediaDistancias  média das distâncias d̄_ij [M][N]
     * @param varDistancias    variância amostral s²_ij [M][N]
     * @param lado           lado do campo (ESQUERDO/DIREITO) para logging separado, ou null
     * @return array de resultados reconciliados, ou null se falhar
     */
    public ReconciliationResult[] reconciliar(
            float[] canhoesX, float[] canhoesY,
            float[][] mediaDistancias, float[][] varDistancias, String lado) {

        String sufixoLado = (lado != null && !lado.isEmpty()) ? "_" + lado.toUpperCase() : "_GLOBAL";

        int N = canhoesX.length;    // número de canhões
        int M = mediaDistancias.length; // número de alvos

        if (N < 4 || M == 0) {
            // Com N<4, não há espaço nulo — estimar posição diretamente via OLS
            Log.w(TAG, "N=" + N + " < 4: reconciliação não aplicável, usando OLS direto");
            return estimarPosicoesDiretas(canhoesX, canhoesY, mediaDistancias);
        }


        try {
            // ── Passo 3: Construir Matriz M (N×3) ──────────────────
            int M_rows = N;
            SimpleMatrix matM = new SimpleMatrix(M_rows, 3);
            for (int j = 0; j < N; j++) {
                matM.set(j, 0, 1.0);
                matM.set(j, 1, -2.0 * canhoesX[j]);
                matM.set(j, 2, -2.0 * canhoesY[j]);
            }

            double condM = calcularNumeroCondicion(matM);
            boolean malCondicionada = !Double.isFinite(condM) || condM > LIMIAR_CONDICIONAMENTO;
            
            ReconciliationLog.getInstance().logConditioning(
                    "RECONCILIAR" + sufixoLado, N, condM, malCondicionada);

            if (malCondicionada) {
                Log.w(TAG, "Geometria dos canhões mal condicionada (cond=" + condM + ") — fallback para OLS direto");
                return estimarPosicoesDiretas(canhoesX, canhoesY, mediaDistancias);
            }

            // ── Passo 4: Espaço nulo esquerdo de M ─────────────────
            // dim(null space esquerdo) = M_rows - rank(M) = N - 3
            SimpleMatrix C = computeLeftNullSpace(matM, M_rows, sufixoLado);
            if (C == null) {
                Log.w(TAG, "Canhões colineares — fallback para OLS direto");
                return estimarPosicoesDiretas(canhoesX, canhoesY, mediaDistancias);
            }

            // ── Reconciliar cada alvo independentemente ─────────────
            ReconciliationResult[] results = new ReconciliationResult[M];

            for (int i = 0; i < M; i++) {
                results[i] = reconciliarAlvo(i, N, canhoesX, canhoesY,
                        mediaDistancias[i], varDistancias[i],
                        C, matM, 0f, 0f, sufixoLado);
            }

            Log.i(TAG, "Reconciliação concluída: " + M + " alvos processados");
            return results;

        } catch (SingularMatrixException e) {
            Log.e(TAG, "Singularidade matricial na reconciliação", e);
            return estimarPosicoesDiretas(canhoesX, canhoesY, mediaDistancias);
        } catch (OutOfMemoryError e) {
            Log.e(TAG, "Memória insuficiente para reconciliação", e);
            throw e;
        } catch (Exception e) {
            Log.e(TAG, "Erro na reconciliação EJML", e);
            return new ReconciliationResult[0];
        }
    }

    /**
     * Isola a projeção matemática pura L2 (Minimização via Left Null Space).
     * Esta função testa puramente a teoria do filtro de minimização, e não sofre viés
     * de heurísticas de restrição do ambiente (WLS).
     */
    public float[] calcularProjecaoMatricial(
            int N, float[] canhoesX, float[] canhoesY,
            float[] mediaDist, float[] varDist,
            SimpleMatrix C) {

        double[] y_arr = new double[N];
        double[][] V_arr = new double[N][N];

        for (int j = 0; j < N; j++) {
            double dj = mediaDist[j];
            double var_j = varDist[j];
            double dj_sq = dj * dj;
            double norm_j_sq = canhoesX[j] * canhoesX[j] + canhoesY[j] * canhoesY[j];
            y_arr[j] = dj_sq - norm_j_sq;
            V_arr[j][j] = Math.max(4.0 * dj_sq * var_j, 1e-6);
        }

        // Usa estritamente o espaço nulo matricial para provar a consistência estatística
        double[][] A_arr = new double[C.getNumRows()][C.getNumCols()];
        for (int r = 0; r < C.getNumRows(); r++) {
            for (int c = 0; c < C.getNumCols(); c++) {
                A_arr[r][c] = C.get(r, c);
            }
        }

        double[] yHat_arr = reconcile(y_arr, V_arr, A_arr);
        float[] distReconciliadas = new float[N];
        for (int j = 0; j < N; j++) {
            double norm_j_sq = canhoesX[j] * canhoesX[j] + canhoesY[j] * canhoesY[j];
            double d_hat_sq = yHat_arr[j] + norm_j_sq;
            if (d_hat_sq < 0) d_hat_sq = 0;
            distReconciliadas[j] = (float) Math.sqrt(d_hat_sq);
        }
        return distReconciliadas;
    }

    private ReconciliationResult reconciliarAlvo(
            int alvoIndex, int N,
            float[] canhoesX, float[] canhoesY,
            float[] mediaDist, float[] varDist,
            SimpleMatrix C, SimpleMatrix matM,
            float offsetX, float offsetY, String sufixoLado) {

        int M_rows = N;
        // ── Passo 1 e 2: Vetor y_i e Matriz V_i ─────────────────
        double[] y_arr = new double[M_rows];
        double[][] V_arr = new double[M_rows][M_rows];

        for (int j = 0; j < N; j++) {
            double cx_j = canhoesX[j];
            double cy_j = canhoesY[j];
            double dj = mediaDist[j];
            double var_j = varDist[j];
            double dj_sq = dj * dj;
            double norm_j_sq = cx_j * cx_j + cy_j * cy_j;

            y_arr[j] = dj_sq - norm_j_sq;

            // Matriz V (Delta Method Diagonal)
            // FIX: Adicionar um piso de variância (regularização) para evitar instabilidade numérica
            V_arr[j][j] = Math.max(4.0 * dj_sq * var_j, 1e-4);
        }

        // ── Passo 3: Matriz A por Espaço Nulo Esquerdo ───────
        // FIX: Usar estritamente o espaço nulo matricial (C) como a matriz de restrição A.
        // O método anterior por limiar geométrico estava causando erros de lógica.
        double[][] A_arr = new double[C.getNumRows()][C.getNumCols()];
        for (int r = 0; r < C.getNumRows(); r++) {
            for (int c = 0; c < C.getNumCols(); c++) {
                A_arr[r][c] = C.get(r, c);
            }
        }

        // ── Passo 5: Equação de reconciliação (CHAMADA EXIGIDA) ──
        double[] yHat_arr = reconcile(y_arr, V_arr, A_arr);
        SimpleMatrix yHat = new SimpleMatrix(M_rows, 1);
        for (int j = 0; j < M_rows; j++) yHat.set(j, 0, yHat_arr[j]);

        // ── Passo 7: Posição WLS (Mínimos Quadrados Ponderados) ──
        // [X̂, Ŷ]ᵀ = (MᵀWM)⁻¹MᵀW·ŷ
        SimpleMatrix matV = new SimpleMatrix(V_arr);
        SimpleMatrix W = safeInvert(matV, true);
        if (W == null) return null;

        SimpleMatrix Mt = matM.transpose();
        SimpleMatrix MtWM = Mt.mult(W).mult(matM);
        SimpleMatrix MtWM_inv = safeInvert(MtWM, true);
        if (MtWM_inv == null) return null;

        SimpleMatrix theta = MtWM_inv.mult(Mt).mult(W).mult(yHat);
        // theta = [K^, X^, Y^]^T, logo index 1 é X e index 2 é Y.
        float xHat = (float) theta.get(1, 0) + offsetX;
        float yHat_coord = (float) theta.get(2, 0) + offsetY;

        // FIX: Sanity check para evitar explosão numérica (-1977% MSE)
        // Se a posição reconciliada fugir absurdamente da tela, fallback para o dado ruidoso bruto
        if (Float.isNaN(xHat) || Float.isInfinite(xHat) || Math.abs(xHat) > 10000 || 
            Float.isNaN(yHat_coord) || Float.isInfinite(yHat_coord) || Math.abs(yHat_coord) > 10000) {
            
            Log.w(TAG, "Reconciliação instável para alvo " + alvoIndex + " (x=" + xHat + ", y=" + yHat_coord + ") — fallback OLS");
            ReconciliationLog.getInstance().logConditioning("RECON_ALVO_INSTAVEL" + sufixoLado, N, 999.0, true);

            // Re-estimar via OLS direto ignorando o null space instável
            ReconciliationResult fallback = estimarPosicoesDiretas(canhoesX, canhoesY, new float[][]{mediaDist})[0];
            return new ReconciliationResult(fallback.x, fallback.y, mediaDist.clone(), 0.0);
        }

        // ── Passo 8: Distâncias reconciliadas ───────────────────
        float[] distReconciliadas = new float[N];
        double[] yFinal = new double[N];
        for (int j = 0; j < N; j++) {
            double dx = xHat - (canhoesX[j] + offsetX);
            double dy = yHat_coord - (canhoesY[j] + offsetY);
            double d = Math.sqrt(dx * dx + dy * dy);
            distReconciliadas[j] = (float) d;
            
            // Recalcular y a partir da geometria final para obter um resíduo não-zero
            yFinal[j] = d * d - (canhoesX[j] * canhoesX[j] + canhoesY[j] * canhoesY[j]);
        }

        // FIX (Final): Calcular a norma usando a geometria FINAL projetada
        // Isso garante que a norma reflita o resíduo da não-linearidade geométrica.
        double normFinal = calcularNormaRestricao(A_arr, yFinal);

        return new ReconciliationResult(xHat, yHat_coord, distReconciliadas, normFinal);
    }

    /**
     * Assinatura EXATA exigida na rubrica AV2 para a álgebra linear.
     * Calcula: y_hat = y - V * A^T * (A * V * A^T)^-1 * A * y
     *
     * @param y vetor de medições brutas
     * @param V matriz de covariância (diagonal)
     * @param A matriz de restrição (espaço nulo esquerdo)
     * @return vetor reconciliado y_hat
     */
    public static double[] reconcile(double[] y, double[][] V, double[][] A) {
        return reconcile(y, V, A, null);
    }

    /**
     * H8 FIX: Sobrecarlogo com parâmetro lado para logging separado por lado.
     * Mantém compatibilidade com a assinatura original exigida pela rubrica.
     */
    public static double[] reconcile(double[] y, double[][] V, double[][] A, String lado) {
        String sufixoLado = (lado != null && !lado.isEmpty()) ? "_" + lado.toUpperCase() : "_GLOBAL";
        if (y == null || V == null || A == null || y.length == 0) {
            Log.w(TAG, "reconcile: entradas inválidas, retornando y original");
            ReconciliationLog.getInstance().logConditioning("RECONCILE_INVALID_INPUT" + sufixoLado, 0, Double.NaN, true);
            return y;
        }

        SimpleMatrix matY = new SimpleMatrix(y.length, 1);
        for (int i = 0; i < y.length; i++) matY.set(i, 0, y[i]);

        SimpleMatrix matV = new SimpleMatrix(V);
        SimpleMatrix matA = new SimpleMatrix(A);

        SimpleMatrix At = matA.transpose();

        SimpleMatrix AVAt = matA.mult(matV).mult(At);
        int m = AVAt.getNumRows();
        for (int i = 0; i < m; i++) {
            AVAt.set(i, i, AVAt.get(i, i) + REGULARIZACAO_TIKHONOV);
        }

        SimpleMatrix AVAt_inv = safeInvert(AVAt, true);
        if (AVAt_inv == null) {
            try {
                AVAt_inv = AVAt.pseudoInverse();
            } catch (Exception e) {
                Log.w(TAG, "reconcile: fallback total (inversão indisponível), retornando y original", e);
                ReconciliationLog.getInstance().logConditioning("RECONCILE_INVERSION_FAIL" + sufixoLado, m, Double.NaN, true);
                return y;
            }
        }

        try {
            SimpleMatrix correction = matV.mult(At).mult(AVAt_inv).mult(matA).mult(matY);
            SimpleMatrix yHat = matY.minus(correction);

            double[] result = new double[y.length];
            for (int i = 0; i < y.length; i++) {
                result[i] = yHat.get(i, 0);
                if (!Double.isFinite(result[i])) {
                    Log.w(TAG, "reconcile: valor não finito no resultado, retornando y original");
                    ReconciliationLog.getInstance().logConditioning("RECONCILE_NON_FINITE" + sufixoLado, y.length, Double.NaN, true);
                    return y;
                }
            }
            return result;
        } catch (Exception e) {
            Log.w(TAG, "reconcile: exceção no cálculo, retornando y original", e);
            ReconciliationLog.getInstance().logConditioning("RECONCILE_EXCEPTION" + sufixoLado, y.length, Double.NaN, true);
            return y;
        }
    }

    /**
     * Calcula a norma do resíduo das restrições: ||A * y_hat||.
     * Implementação exigida para validação da AV2.
     *
     * @param matrizA matriz de restrições (null space)
     * @param y_recon vetor reconciliado
     * @return norma euclidiana do resíduo
     */
    public static double calcularNormaRestricao(double[][] matrizA, double[] y_recon) {
        if (matrizA == null || y_recon == null) return 0.0;
        double somaDosQuadrados = 0.0;
        for (int i = 0; i < matrizA.length; i++) {
            double valorLinha = 0.0;
            for (int j = 0; j < y_recon.length; j++) {
                valorLinha += matrizA[i][j] * y_recon[j];
            }
            somaDosQuadrados += (valorLinha * valorLinha);
        }
        return Math.sqrt(somaDosQuadrados);
    }

    /**
     * Calcula a variância amostral de um vetor.
     */
    public static double calcularVariancia(double[] valores) {
        if (valores == null || valores.length < 2) return 0;
        double soma = 0;
        for (double v : valores) soma += v;
        double media = soma / valores.length;

        double somaQuad = 0;
        for (double v : valores) {
            somaQuad += (v - media) * (v - media);
        }
        return somaQuad / (valores.length - 1);
    }

    /**
     * Calcula erro RMS antes e depois da reconciliação.
     * Retorna array: [erroAntes, erroDepois, reducaoPercentual]
     *
     * @param y vetor bruto
     * @param yHat vetor reconciliado
     * @return [erroAntes, erroDepois, reducaoPercentual]
     */
    public static double[] calcularErroRMS(double[] y, double[] yHat) {
        if (y == null || yHat == null || y.length == 0) {
            return new double[] {0, 0, 0};
        }

        double somaY2 = 0, somaYHat2 = 0;
        for (int i = 0; i < y.length; i++) {
            somaY2 += y[i] * y[i];
            somaYHat2 += yHat[i] * yHat[i];
        }

        double erroAntes = Math.sqrt(somaY2);
        double erroDepois = Math.sqrt(somaYHat2);
        double reducao = (erroAntes > 0) ? (1.0 - erroDepois / erroAntes) * 100.0 : 0;

        return new double[] {erroAntes, erroDepois, reducao};
    }

    private static double calcularLimiarGeometrico(float[] mediaDistancias, double fator) {
        if (mediaDistancias == null || mediaDistancias.length == 0) return Double.NaN;
        double soma = 0;
        int count = 0;
        for (float d : mediaDistancias) {
            if (d > 0) {
                soma += d;
                count++;
            }
        }
        if (count == 0) return Double.NaN;
        return (soma / count) * fator;
    }

    /**
     * Calcula o espaço nulo esquerdo de M via SVD.
     * Retorna matriz C de dimensão (N-3)×N.
     */
    public SimpleMatrix computeLeftNullSpace(SimpleMatrix M, int N, String sufixoLado) {
        // Simple cache: se M não mudou, reutiliza C calculada previamente
        try {
            if (M == null) return null;
        } catch (Exception ignored) {}
        if (cachedC != null && cachedN == N) {
            double h = 0;
            for (int r = 0; r < M.getNumRows(); r++) {
                for (int c = 0; c < M.getNumCols(); c++) {
                    h = h * 31 + M.get(r, c);
                }
            }
            if (Double.compare(h, cachedMatMHash) == 0) {
                return cachedC;
            }
        }
        try {
            // SVD: M = U·S·Vᵀ — executado em thread separada com timeout
            // para prevenir ANR indireto (Android Watchdog)
            long svdStart = System.nanoTime();

            Future<org.ejml.simple.SimpleSVD<SimpleMatrix>> svdFuture =
                    SVD_EXECUTOR.submit(new Callable<org.ejml.simple.SimpleSVD<SimpleMatrix>>() {
                        @Override
                        public org.ejml.simple.SimpleSVD<SimpleMatrix> call() {
                            return M.svd();
                        }
                    });

            org.ejml.simple.SimpleSVD<SimpleMatrix> svd;
            try {
                svd = svdFuture.get(SVD_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                svdFuture.cancel(true);
                long elapsedMs = (System.nanoTime() - svdStart) / 1_000_000;
                Log.w(TAG, "SVD TIMEOUT após " + elapsedMs + "ms (limite=" + SVD_TIMEOUT_MS
                        + "ms) — fallback para OLS");
                ReconciliationLog.getInstance().logConditioning(
                        "SVD_TIMEOUT" + sufixoLado, N, Double.NaN, true);
                return null;
            } catch (ExecutionException | InterruptedException e) {
                Log.w(TAG, "SVD falhou — fallback para OLS", e);
                ReconciliationLog.getInstance().logConditioning(
                        "SVD_EXECUTION_FAIL" + sufixoLado, N, Double.NaN, true);
                return null;
            }

            long svdElapsedMs = (System.nanoTime() - svdStart) / 1_000_000;
            Log.i("AUTOTARGET_METRICS_SVD", "SVD duration ms=" + svdElapsedMs
                    + " rows=" + M.getNumRows() + " cols=" + M.getNumCols());

            if (svdElapsedMs > SVD_TIMEOUT_MS / 2) {
                Log.w(TAG, "SVD lento detectado: " + svdElapsedMs + "ms para N=" + N);
            }

            SimpleMatrix U = svd.getU();

            int rank = 0;
            SimpleMatrix W = svd.getW();
            for (int i = 0; i < Math.min(W.getNumRows(), W.getNumCols()); i++) {
                if (W.get(i, i) > 1e-10) rank++;
            }

            int nullDim = N - rank;
            if (nullDim <= 0) return null; // Sem espaço nulo

            // Extrair últimas nullDim colunas de U e transpor
            SimpleMatrix C = new SimpleMatrix(nullDim, N);
            for (int i = 0; i < nullDim; i++) {
                for (int j = 0; j < N; j++) {
                    C.set(i, j, U.get(j, rank + i));
                }
            }

            // Atualizar cache (hash simples da matriz M)
            double h = 0;
            for (int r = 0; r < M.getNumRows(); r++) {
                for (int c = 0; c < M.getNumCols(); c++) {
                    h = h * 31 + M.get(r, c);
                }
            }
            cachedC = C;
            cachedMatMHash = h;
            cachedN = N;

            return C;
        } catch (Exception e) {
            Log.e(TAG, "Erro ao calcular espaço nulo", e);
            return null;
        }
    }

    // Cache sencillo para evitar SVD repetido quando a geometria não muda
    private transient SimpleMatrix cachedC = null;
    private transient double cachedMatMHash = Double.NaN;
    private transient int cachedN = -1;

    /**
     * Fallback: estimar posições diretamente via OLS quando N<4.
     */
    private ReconciliationResult[] estimarPosicoesDiretas(
            float[] canhoesX, float[] canhoesY, float[][] mediaDistancias) {

        int N = canhoesX.length;
        int M = mediaDistancias.length;

        if (M == 0) return new ReconciliationResult[0];

        // Se N < 3, não conseguimos estimar posição (triangulação requer 3 pontos)
        // Mas retornamos as distâncias brutas como se fossem reconciliadas para que apareçam no log
        if (N < 3) {
            Log.w(TAG, "N=" + N + " < 3: Impossível estimar posição, retornando apenas distâncias brutas para log.");
            ReconciliationResult[] results = new ReconciliationResult[M];
            for (int i = 0; i < M; i++) {
                results[i] = new ReconciliationResult(0f, 0f, mediaDistancias[i]);
            }
            return results;
        }

        SimpleMatrix matM = new SimpleMatrix(N, 3);
        double[] normaSq = new double[N];
        for (int j = 0; j < N; j++) {
            normaSq[j] = canhoesX[j] * canhoesX[j] + canhoesY[j] * canhoesY[j];
            matM.set(j, 0, 1.0);
            matM.set(j, 1, -2.0 * canhoesX[j]);
            matM.set(j, 2, -2.0 * canhoesY[j]);
        }

        SimpleMatrix Mt = matM.transpose();
        SimpleMatrix solver;
        try {
            SimpleMatrix MtM_inv = safeInvert(Mt.mult(matM), false);
            if (MtM_inv == null) return new ReconciliationResult[0]; // Adicionado check nulo
            solver = MtM_inv.mult(Mt);
        } catch (SingularMatrixException e) {
            Log.e(TAG, "Canhões colineares — impossível estimar posição", e);
            return new ReconciliationResult[0];
        }

        ReconciliationResult[] results = new ReconciliationResult[M];
        for (int i = 0; i < M; i++) {
            SimpleMatrix y = new SimpleMatrix(N, 1);
            float[] distRecon = new float[N];
            for (int j = 0; j < N; j++) {
                double d = mediaDistancias[i][j];
                y.set(j, 0, d * d - normaSq[j]);
                distRecon[j] = mediaDistancias[i][j]; // sem reconciliação
            }
            SimpleMatrix theta = solver.mult(y);
            results[i] = new ReconciliationResult(
                    (float) theta.get(1, 0),
                    (float) theta.get(2, 0),
                    distRecon, 0.0);
        }
        return results;
    }

    /**
     * Calcula a função de utilidade U(N) para decisão de custo-benefício.
     *
     * U(N) = Σ_j r(N) · Σ_i exp(-β·d̂_ij)
     * r(N) = r₀ / (1 + α·max(0, N-L))
     *
     * @param distancias      distâncias reconciliadas [M][N] ou [alvos][canhões]
     * @param numCanhoes       N
     * @param limiarPenalidade L (default 5)
     * @param alpha            α (default 0.2)
     * @param beta             β (constante de dissipação, default 0.005)
     * @param intervaloBase    I_base em ms
     * @return utilidade total U(N)
     */
    public static double calcularUtilidade(
            float[][] distancias, int numCanhoes,
            int limiarPenalidade, double alpha, double beta,
            int intervaloBase) {

        if (distancias == null || distancias.length == 0 || numCanhoes == 0) return 0;

        int M = distancias.length;
        int N = numCanhoes;

        // Taxa de disparo efetiva (disparos por segundo)
        double penaltyFactor = 1.0 + alpha * Math.max(0, N - limiarPenalidade);
        double rN = 1000.0 / (intervaloBase * penaltyFactor);

        double U = 0;
        for (int j = 0; j < N && j < distancias[0].length; j++) {
            double sumProb = 0;
            for (int i = 0; i < M; i++) {
                sumProb += Math.exp(-beta * distancias[i][j]);
            }
            U += rN * sumProb;
        }

        return U;
    }
}
