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

/**
 * Reconciliação de dados estocásticos via álgebra linear (EJML).
 */
public class DataReconciliation {

    private static final String TAG = "DataReconciliation";

    /**
     * Resultado da reconciliação para um alvo.
     */
    public static class ReconciliationResult {
        /** Posição retificada X do alvo */
        public final float x;
        /** Posição retificada Y do alvo */
        public final float y;
        /** Distâncias reconciliadas a cada canhão */
        public final float[] distanciasReconciliadas;

        public ReconciliationResult(float x, float y, float[] distancias) {
            this.x = x;
            this.y = y;
            this.distanciasReconciliadas = distancias;
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

        if (N < 4) {
            // Com N<4, não há espaço nulo — estimar posição diretamente via OLS
            Log.w(TAG, "N=" + N + " < 4: reconciliação não aplicável, usando OLS direto");
            return estimarPosicoesDiretas(canhoesX, canhoesY, mediaDistancias);
        }

        if (M == 0) {
            Log.w(TAG, "Nenhum alvo para reconciliar");
            return new ReconciliationResult[0];
        }

        try {
            // ── Passo 3: Construir Matriz M (N×3) ──────────────────
            SimpleMatrix matM = new SimpleMatrix(N, 3);
            double[] canhaoNormaSq = new double[N];
            for (int j = 0; j < N; j++) {
                canhaoNormaSq[j] = canhoesX[j] * canhoesX[j] + canhoesY[j] * canhoesY[j];
                matM.set(j, 0, 1.0);
                matM.set(j, 1, -2.0 * canhoesX[j]);
                matM.set(j, 2, -2.0 * canhoesY[j]);
            }

            // ── Passo 4: Espaço nulo esquerdo de M ─────────────────
            // dim(null space esquerdo) = N - rank(M) = N - 3
            SimpleMatrix C = computeLeftNullSpace(matM, N);
            if (C == null) {
                Log.w(TAG, "Canhões colineares — fallback para OLS direto");
                return estimarPosicoesDiretas(canhoesX, canhoesY, mediaDistancias);
            }

            // ── Pré-calcular (MᵀM)⁻¹Mᵀ para OLS ──────────────────
            SimpleMatrix Mt = matM.transpose();
            SimpleMatrix MtM_inv_Mt = Mt.mult(matM).invert().mult(Mt);

            // ── Reconciliar cada alvo independentemente ─────────────
            ReconciliationResult[] results = new ReconciliationResult[M];

            for (int i = 0; i < M; i++) {
                results[i] = reconciliarAlvo(i, N, canhoesX, canhoesY,
                        canhaoNormaSq, mediaDistancias[i], varDistancias[i],
                        C, MtM_inv_Mt);
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

    /**
     * Reconcilia um alvo individual.
     *
     * Equação: ŷ_i = y_i - V_i·Cᵀ·(C·V_i·Cᵀ)⁻¹·C·y_i
     */
    private ReconciliationResult reconciliarAlvo(
            int alvoIndex, int N,
            float[] canhoesX, float[] canhoesY,
            double[] canhaoNormaSq,
            float[] mediaDist, float[] varDist,
            SimpleMatrix C, SimpleMatrix MtM_inv_Mt) {

        // ── Passo 1: Vetor y_i ──────────────────────────────────
        double[] y_arr = new double[N];
        for (int j = 0; j < N; j++) {
            double dBar = mediaDist[j];
            y_arr[j] = dBar * dBar - canhaoNormaSq[j];
        }

        // ── Passo 2: Matriz V_i (diagonal) ──────────────────────
        double[][] V_arr = new double[N][N];
        for (int j = 0; j < N; j++) {
            double dBar = mediaDist[j];
            double sij2 = varDist[j];
            double vij = (2.0 * dBar) * (2.0 * dBar) * sij2;
            V_arr[j][j] = Math.max(vij, 1e-6); // Evitar zeros
        }

        // ── Passo 3: Converter Matriz de Restrição C -> A ───────
        double[][] A_arr = new double[C.getNumRows()][C.getNumCols()];
        for (int r = 0; r < C.getNumRows(); r++) {
            for (int c = 0; c < C.getNumCols(); c++) {
                A_arr[r][c] = C.get(r, c);
            }
        }

        // ── Passo 5: Equação de reconciliação (CHAMADA EXIGIDA) ──
        double[] yHat_arr = reconcile(y_arr, V_arr, A_arr);
        SimpleMatrix yHat = new SimpleMatrix(N, 1);
        for (int j = 0; j < N; j++) yHat.set(j, 0, yHat_arr[j]);

        // ── Passo 6: Distâncias reconciliadas ───────────────────
        float[] distReconciliadas = new float[N];
        for (int j = 0; j < N; j++) {
            double val = yHat.get(j, 0) + canhaoNormaSq[j];
            distReconciliadas[j] = (float) Math.sqrt(Math.max(val, 0.0));
        }

        // ── Passo 7: Posição OLS ────────────────────────────────
        // [K̂, X̂, Ŷ]ᵀ = (MᵀM)⁻¹Mᵀ·ŷ
        SimpleMatrix theta = MtM_inv_Mt.mult(yHat);
        float xHat = (float) theta.get(1, 0);
        float yHat_coord = (float) theta.get(2, 0);

        return new ReconciliationResult(xHat, yHat_coord, distReconciliadas);
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
