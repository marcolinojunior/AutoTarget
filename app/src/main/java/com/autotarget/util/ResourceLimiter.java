package com.autotarget.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gerenciador thread-safe de limites de recursos (canhões, alvos, etc).
 * Evita race conditions tipo TOCTOU (Time-of-Check, Time-of-Use).
 *
 * CRÍTICO: Impede que o sistema exceda os limites por condições de corrida.
 */
public class ResourceLimiter {
    
    private final ConcurrentHashMap<String, AtomicInteger> counts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> limits = new ConcurrentHashMap<>();
    
    public ResourceLimiter() {
        // Inicializar contadores
        counts.put("canhoesEsquerdo", new AtomicInteger(0));
        counts.put("canhoesDireito", new AtomicInteger(0));
        counts.put("alvosTotal", new AtomicInteger(0));
        
        // Inicializar limites
        limits.put("canhoesEsquerdo", 10);
        limits.put("canhoesDireito", 10);
        limits.put("alvosTotal", 50);
    }
    
    /**
     * Tenta incrementar contador atomicamente.
     * Retorna true se conseguiu, false se atingiu limite.
     *
     * ATOMICAMENTE garante que não há race condition entre check e uso.
     */
    public boolean tryIncrement(String resourceName) {
        AtomicInteger counter = counts.computeIfAbsent(resourceName, k -> new AtomicInteger(0));
        Integer limit = limits.get(resourceName);
        if (limit == null) return true; // Sem limite definido
        
        int current;
        do {
            current = counter.get();
            if (current >= limit) {
                return false; // Atingiu limite, operação negada
            }
        } while (!counter.compareAndSet(current, current + 1));
        
        return true;
    }
    
    /**
     * Decrementa contador (liberação de recurso).
     */
    public void decrement(String resourceName) {
        AtomicInteger counter = counts.get(resourceName);
        if (counter != null) {
            counter.decrementAndGet();
        }
    }
    
    /**
     * Retorna contador atual sem modificar.
     */
    public int getCount(String resourceName) {
        AtomicInteger counter = counts.get(resourceName);
        return counter != null ? counter.get() : 0;
    }
    
    /**
     * Reinicia contador (útil para testes ou reset).
     */
    public void reset(String resourceName) {
        AtomicInteger counter = counts.get(resourceName);
        if (counter != null) {
            counter.set(0);
        }
    }
}
