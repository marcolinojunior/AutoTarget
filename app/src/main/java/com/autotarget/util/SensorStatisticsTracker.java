package com.autotarget.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Rastreia e exibe estatísticas de sensores (média, variância, ruído).
 * Útil para validação de implementação de sensores ruidosos (requisito AV2).
 */
public class SensorStatisticsTracker {
    
    public static class SensorStatistics {
        public long timestamp;
        public int nCanhoes;      // Número de canhões que contribuem dados
        public String lado;       // ESQUERDO ou DIREITO
        public float mediaDistancia;
        public float varianciaDistancia;
        public float desviopadrao;
        public float maximo;
        public float minimo;
        public int amostras;
        
        @Override
        public String toString() {
            return String.format(Locale.US,
                    "Sensor[%s] | N=%d | Média=%.2f ± %.2f | Amplitude=[%.2f,%.2f] | Amostras=%d",
                    lado, nCanhoes, mediaDistancia, desviopadrao, minimo, maximo, amostras);
        }
    }
    
    private static final int BUFFER_SIZE = 200;
    private static final List<SensorStatistics> historico = new ArrayList<>();
    
    /**
     * Registra estatísticas coletadas de um lado.
     */
    public static synchronized void registrarEstatisticas(
            int nCanhoes,
            String lado,
            float[] distancias) {
        
        if (distancias == null || distancias.length == 0) return;
        
        SensorStatistics stats = new SensorStatistics();
        stats.timestamp = System.currentTimeMillis();
        stats.nCanhoes = nCanhoes;
        stats.lado = lado;
        stats.amostras = distancias.length;
        
        // Calcular média
        float soma = 0;
        float max = distancias[0];
        float min = distancias[0];
        for (float d : distancias) {
            soma += d;
            max = Math.max(max, d);
            min = Math.min(min, d);
        }
        stats.mediaDistancia = soma / distancias.length;
        stats.maximo = max;
        stats.minimo = min;
        
        // Calcular variância e desvio padrão
        float somaVariancia = 0;
        for (float d : distancias) {
            somaVariancia += (d - stats.mediaDistancia) * (d - stats.mediaDistancia);
        }
        stats.varianciaDistancia = somaVariancia / distancias.length;
        stats.desviopadrao = (float) Math.sqrt(stats.varianciaDistancia);
        
        historico.add(stats);
        
        // Manter buffer sob controle
        if (historico.size() > BUFFER_SIZE) {
            historico.remove(0);
        }
    }
    
    /**
     * Retorna histórico de estatísticas.
     */
    public static synchronized List<SensorStatistics> obterHistorico() {
        return new ArrayList<>(historico);
    }
    
    /**
     * Retorna resumo agregado das últimas N coletas.
     */
    public static synchronized String obterResumoUltimas(int n) {
        if (historico.isEmpty()) {
            return "Sem dados de sensores";
        }
        
        List<SensorStatistics> ultimas = historico.subList(
                Math.max(0, historico.size() - n),
                historico.size());
        
        StringBuilder sb = new StringBuilder();
        for (SensorStatistics s : ultimas) {
            sb.append(s.toString()).append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Calcula ruído médio (como percentual da média de distância).
     */
    public static synchronized float calcularRuidoMedio() {
        if (historico.isEmpty()) return 0;
        
        float somaRuido = 0;
        for (SensorStatistics s : historico) {
            if (s.mediaDistancia > 0) {
                somaRuido += (s.desviopadrao / s.mediaDistancia) * 100.0f;
            }
        }
        
        return somaRuido / historico.size();
    }
    
    /**
     * Limpa histórico (útil ao encerrar partida).
     */
    public static synchronized void limpar() {
        historico.clear();
    }
    
    /**
     * Retorna tamanho do histórico.
     */
    public static synchronized int tamanhoHistorico() {
        return historico.size();
    }
}
