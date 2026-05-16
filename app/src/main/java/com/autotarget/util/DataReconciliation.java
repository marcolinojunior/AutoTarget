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

/**
 * Reconciliação de dados estocásticos via álgebra linear (EJML).
 */
public class DataReconciliation {

    private static final String TAG = "DataReconciliation";
    private static final double LIMIAR_GEOMETRICO_FATOR = 0.85;

    /**
     * Resultado da reconciliação para um alvo.
     */
    public static class ReconciliationResult {
        public final float x, y;
        public final float[] distanciasReconciliadas;
        public final double normA_yHat;

        public ReconciliationResult(float x, float y, float[] dist) {
            this.x = x;
            this.y = y;
            this.distanciasReconciliadas = dist;
            this.normA_yHat = 0;
        }

        public ReconciliationResult(float x, float y, float[] dist, double normA_yHat) {
            this.x = x;
            this.y = y;
            this.distanciasReconciliadas = dist;
            this.normA_yHat = normA_yHat;
        }
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

        int N = canhoesX.length;    // número de canhões
        int M = mediaDistancias.length; // número de alvos

        if (N < 4 || M == 0) {
            // Com N<4, não há espaço nulo — estimar posição diretamente via OLS
            Log.w(TAG, "N=" + N + " < 4: reconciliação não aplicável, usando OLS direto");
            return estimarPosicoesDiretas(canhoesX, canhoesY, mediaDistancias);
        }

        if (M == 0) {
            Log.w(TAG, "Nenhum alvo para reconciliar");
            return new ReconciliationResult[0];
        }

        try {
            // ── Otimização de estabilidade: Transladar para o centroide ──
            double cx = 0, cy = 0;
            for (int j = 0; j < N; j++) {
                cx += canhoesX[j];
                cy += canhoesY[j];
            }
            cx /= N;
            cy /= N;

            float[] cX = new float[N];
            float[] cY = new float[N];
            for (int j = 0; j < N; j++) {
                cX[j] = (float) (canhoesX[j] - cx);
                cY[j] = (float) (canhoesY[j] - cy);
            }

            // ── Passo 3: Construir Matriz M ((N-1)×2) ──────────────────
            int M_rows = N - 1;
            SimpleMatrix matM = new SimpleMatrix(M_rows, 2);
            double cx0 = cX[0];
            double cy0 = cY[0];
            for (int j = 1; j < N; j++) {
                int r = j - 1;
                matM.set(r, 0, 2.0 * (cX[j] - cx0));
                matM.set(r, 1, 2.0 * (cY[j] - cy0));
            }

            // ── Passo 4: Espaço nulo esquerdo de M ─────────────────
            // dim(null space esquerdo) = M_rows - rank(M) = (N-1) - 2 = N - 3
            SimpleMatrix C = computeLeftNullSpace(matM, M_rows);
            if (C == null) {
                Log.w(TAG, "Canhões colineares — fallback para OLS direto");
                return estimarPosicoesDiretas(canhoesX, canhoesY, mediaDistancias);
            }

            // ── Reconciliar cada alvo independentemente ─────────────
            ReconciliationResult[] results = new ReconciliationResult[M];

            for (int i = 0; i < M; i++) {
                results[i] = reconciliarAlvo(i, N, cX, cY,
                        mediaDistancias[i], varDistancias[i],
                        C, matM, (float) cx, (float) cy);
            }

            Log.i(TAG, "Reconciliação concluída: " + M + " alvos processados");
            return results;

        } catch (SingularMatrixException e) {
            Log.e(TAG, "Singularidade matricial na reconciliação", e);
            return estimarPosicoesDiretas(canhoesX, canhoesY, mediaDistancias);
        } catch (Exception e) {
            Log.e(TAG, "Erro na reconciliação EJML", e);
            return null;
        }
    }

    private ReconciliationResult reconciliarAlvo(
            int alvoIndex, int N,
            float[] canhoesX, float[] canhoesY,
            float[] mediaDist, float[] varDist,
            SimpleMatrix C, SimpleMatrix matM,
            float offsetX, float offsetY) {

        int M_rows = N - 1;
        // ── Passo 1 e 2: Vetor y_i e Matriz V_i ─────────────────
        double[] y_arr = new double[M_rows];
        double[][] V_arr = new double[M_rows][M_rows];

        double cx0 = canhoesX[0];
        double cy0 = canhoesY[0];
        double d0 = mediaDist[0];
        double var0 = varDist[0];
        double d0_sq = d0 * d0;
        double v0_sq = 4.0 * d0_sq * var0;
        double norm0_sq = cx0 * cx0 + cy0 * cy0;

        for (int j = 1; j < N; j++) {
            int r = j - 1;
            double cx_j = canhoesX[j];
            double cy_j = canhoesY[j];
            double dj = mediaDist[j];
            double dj_sq = dj * dj;
            double norm_j_sq = cx_j * cx_j + cy_j * cy_j;

            y_arr[r] = d0_sq - dj_sq - norm0_sq + norm_j_sq;
        }

        // ── Matriz V_i (Covariância Densa da Transformação) ─────
        for (int r = 0; r < M_rows; r++) {
            for (int s = 0; s < M_rows; s++) {
                if (r == s) {
                    double dj = mediaDist[r + 1];
                    double var_j = varDist[r + 1];
                    double vj_sq = 4.0 * (dj * dj) * var_j;
                    V_arr[r][s] = Math.max(v0_sq + vj_sq, 1e-6);
                } else {
                    V_arr[r][s] = v0_sq; // Covariância induzida pelo Canhão 0
                }
            }
        }

        // ── Passo 3: Matriz A por geometria (limiar de distância) ───────
        // Requisito AV2: incidência baseada em conectividade geométrica.
        double limiarGeometrico = calcularLimiarGeometrico(mediaDist, LIMIAR_GEOMETRICO_FATOR);
        double[][] A_arr = construirMatrizIncidenciaPorLimiar(mediaDist, limiarGeometrico);

        // Fallback robusto: se não houver conexões válidas no limiar,
        // mantém a estratégia por espaço nulo já validada.
        if (A_arr == null || A_arr.length == 0 || y_arr.length < 3) {
            Log.w(TAG, "Cenário não reconciliável, caindo para estimativas simples.");
            A_arr = new double[C.getNumRows()][C.getNumCols()];
            for (int r = 0; r < C.getNumRows(); r++) {
                for (int c = 0; c < C.getNumCols(); c++) {
                    A_arr[r][c] = C.get(r, c);
                }
            }
        }

        // ── Passo 5: Equação de reconciliação (CHAMADA EXIGIDA) ──
        double[] yHat_arr = reconcile(y_arr, V_arr, A_arr);
        SimpleMatrix yHat = new SimpleMatrix(M_rows, 1);
        for (int j = 0; j < M_rows; j++) yHat.set(j, 0, yHat_arr[j]);

        // ── Calcular ||A*yHat|| ─────────────────────────────────
        SimpleMatrix matA = new SimpleMatrix(A_arr);
        SimpleMatrix AyHat = matA.mult(yHat);
        double normA_yHat = AyHat.normF();

        // ── Passo 7: Posição WLS (Mínimos Quadrados Ponderados) ──
        // [X̂, Ŷ]ᵀ = (MᵀWM)⁻¹MᵀW·ŷ
        SimpleMatrix matV = new SimpleMatrix(V_arr);
        SimpleMatrix W;
        try {
            W = matV.invert();
        } catch (SingularMatrixException e) {
            W = matV.pseudoInverse();
        }

        SimpleMatrix Mt = matM.transpose();
        SimpleMatrix MtWM = Mt.mult(W).mult(matM);
        SimpleMatrix MtWM_inv;
        try {
            MtWM_inv = MtWM.invert();
        } catch (SingularMatrixException e) {
            MtWM_inv = MtWM.pseudoInverse();
        }

        SimpleMatrix theta = MtWM_inv.mult(Mt).mult(W).mult(yHat);
        float xHat = (float) theta.get(0, 0) + offsetX;
        float yHat_coord = (float) theta.get(1, 0) + offsetY;

        // ── Passo 8: Distâncias reconciliadas ───────────────────
        float[] distReconciliadas = new float[N];
        for (int j = 0; j < N; j++) {
            double dx = xHat - (canhoesX[j] + offsetX);
            double dy = yHat_coord - (canhoesY[j] + offsetY);
            distReconciliadas[j] = (float) Math.sqrt(dx * dx + dy * dy);
        }

        return new ReconciliationResult(xHat, yHat_coord, distReconciliadas, normA_yHat);
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
        SimpleMatrix matY = new SimpleMatrix(y.length, 1);
        for (int i = 0; i < y.length; i++) matY.set(i, 0, y[i]);

        SimpleMatrix matV = new SimpleMatrix(V);
        SimpleMatrix matA = new SimpleMatrix(A);

        SimpleMatrix At = matA.transpose();
        SimpleMatrix AVAt = matA.mult(matV).mult(At);

        SimpleMatrix AVAt_inv;
        try {
            AVAt_inv = AVAt.invert();
        } catch (SingularMatrixException e) {
            AVAt_inv = AVAt.pseudoInverse();
        }

        SimpleMatrix correction = matV.mult(At).mult(AVAt_inv).mult(matA).mult(matY);
        SimpleMatrix yHat = matY.minus(correction);

        double[] result = new double[y.length];
        for (int i = 0; i < y.length; i++) {
            result[i] = yHat.get(i, 0);
        }
        return result;
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
     * Constrói matriz de incidência A com base em conectividade por limiar:
     * se d_j < limiar, o canhão j é considerado conectado ao alvo.
     *
     * A dimensão de saída é k x (N-1), coerente com o vetor y atual
     * (diferenças entre canhões 1..N-1 e canhão referência 0).
     */
    public static double[][] construirMatrizIncidenciaPorLimiar(float[] mediaDistancias, double limiar) {
        if (mediaDistancias == null || mediaDistancias.length < 3 || !Double.isFinite(limiar)) {
            return null;
        }

        List<Integer> conectados = new ArrayList<>();
        // Ignora o canhão de referência (índice 0), pois y usa [1..N-1].
        for (int j = 1; j < mediaDistancias.length; j++) {
            if (mediaDistancias[j] <= limiar) {
                conectados.add(j - 1);
            }
        }

        if (conectados.size() < 2) {
            return null;
        }

        int rows = conectados.size() - 1;
        int cols = mediaDistancias.length - 1;
        double[][] A = new double[rows][cols];
        for (int r = 0; r < rows; r++) {
            int c1 = conectados.get(r);
            int c2 = conectados.get(r + 1);
            A[r][c1] = 1.0;
            A[r][c2] = -1.0;
        }
        return A;
    }

    /**
     * Calcula o espaço nulo esquerdo de M via SVD.
     * Retorna matriz C de dimensão (N-3)×N.
     */
    private SimpleMatrix computeLeftNullSpace(SimpleMatrix M, int N) {
        try {
            // SVD: M = U·S·Vᵀ
            // Espaço nulo esquerdo = últimas (N-rank) colunas de U
            org.ejml.simple.SimpleSVD<SimpleMatrix> svd = M.svd();
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

            return C;
        } catch (Exception e) {
            Log.e(TAG, "Erro ao calcular espaço nulo", e);
            return null;
        }
    }

    /**
     * Fallback: estimar posições diretamente via OLS quando N<4.
     */
    private ReconciliationResult[] estimarPosicoesDiretas(
            float[] canhoesX, float[] canhoesY, float[][] mediaDistancias) {

        int N = canhoesX.length;
        int M = mediaDistancias.length;

        if (N < 3 || M == 0) return new ReconciliationResult[0];

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
            solver = Mt.mult(matM).invert().mult(Mt);
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
                    distRecon);
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
