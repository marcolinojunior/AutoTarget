package com.autotarget.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Armazena dados de reconciliação para visualização em relatórios.
 * Cada entrada representa a reconciliação de UM alvo em UMA rodada.
 */
public class ReconciliationVisualizer {
    
    public static class ReconciliationDataPoint {
        public int alvoId;
        public long timestamp;
        public float[] distanciasRutas;      // y (bruto)
        public float[] distanciasReconciliadas; // y_hat (reconciliado)
        public double erroAntes;
        public double erroDepois;
        public double reducaoPercentual;
        public int nCanhoes;
        public String lado;
        
        @Override
        public String toString() {
            return String.format(Locale.US,
                    "Alvo:%d | Tempo:%.1fs | Canhoes:%d | ErroANTES:%.2f → ErroDepois:%.2f (Reducao:%.1f%%)",
                    alvoId, timestamp / 1000.0, nCanhoes, erroAntes, erroDepois, reducaoPercentual);
        }
    }
    
    private static final int BUFFER_SIZE = 100;
    private static final List<ReconciliationDataPoint> historicoReconciliacao = new ArrayList<>();
    private static volatile long ultimaAtualizacao = 0;
    
    /**
     * Registra um ponto de dados de reconciliação.
     */
    public static synchronized void registrarPonto(
            int alvoId,
            float[] distanciasRutas,
            float[] distanciasReconciliadas,
            double erroAntes,
            double erroDepois,
            double reducaoPercentual,
            int nCanhoes,
            String lado) {
        
        ReconciliationDataPoint ponto = new ReconciliationDataPoint();
        ponto.alvoId = alvoId;
        ponto.timestamp = System.currentTimeMillis();
        ponto.distanciasRutas = distanciasRutas;
        ponto.distanciasReconciliadas = distanciasReconciliadas;
        ponto.erroAntes = erroAntes;
        ponto.erroDepois = erroDepois;
        ponto.reducaoPercentual = reducaoPercentual;
        ponto.nCanhoes = nCanhoes;
        ponto.lado = lado;
        
        historicoReconciliacao.add(ponto);
        
        // Manter buffer sob controle
        if (historicoReconciliacao.size() > BUFFER_SIZE) {
            historicoReconciliacao.remove(0);
        }
        
        ultimaAtualizacao = System.currentTimeMillis();
    }
    
    /**
     * Retorna todos os pontos de reconciliação registrados.
     */
    public static synchronized List<ReconciliationDataPoint> obterHistorico() {
        return new ArrayList<>(historicoReconciliacao);
    }
    
    /**
     * Retorna estatísticas agregadas de reconciliação.
     */
    public static synchronized String obterEstatisticasAgregadas() {
        if (historicoReconciliacao.isEmpty()) {
            return "Sem dados de reconciliação";
        }
        
        double mediaErroAntes = 0, mediaErroDepois = 0, mediaReducao = 0;
        int count = historicoReconciliacao.size();
        
        for (ReconciliationDataPoint p : historicoReconciliacao) {
            mediaErroAntes += p.erroAntes;
            mediaErroDepois += p.erroDepois;
            mediaReducao += p.reducaoPercentual;
        }
        
        mediaErroAntes /= count;
        mediaErroDepois /= count;
        mediaReducao /= count;
        
        return String.format(Locale.US,
                "Reconciliações: %d | ErroMédio: %.2f → %.2f (Redução média: %.1f%%)",
                count, mediaErroAntes, mediaErroDepois, mediaReducao);
    }
    
    /**
     * Limpa histórico (útil ao encerrar partida).
     */
    public static synchronized void limpar() {
        historicoReconciliacao.clear();
        ultimaAtualizacao = 0;
    }
    
    /**
     * Retorna timestamp da última atualização.
     */
    public static long obterUltimaAtualizacao() {
        return ultimaAtualizacao;
    }
}
