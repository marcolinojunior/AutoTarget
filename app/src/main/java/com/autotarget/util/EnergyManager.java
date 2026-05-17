package com.autotarget.util;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Wrapper thread-safe para gerenciar energia com atomicidade total.
 * Resolve problema de volatile sem atomicidade de operações compostas.
 *
 * CRÍTICO: Energia deve ser sempre consistente entre múltiplas threads.
 */
public class EnergyManager {
    
    private final AtomicReference<Float> value;
    private final float maxValue;
    private final Object updateLock = new Object();
    
    public EnergyManager(float initial, float max) {
        this.value = new AtomicReference<>(initial);
        this.maxValue = max;
    }
    
    /**
     * Adiciona energia de forma thread-safe.
     * Garante que nunca excede o máximo.
     */
    public void add(float amount) {
        if (amount <= 0) return;
        
        Float current;
        Float newValue;
        do {
            current = value.get();
            newValue = Math.min(current + amount, maxValue);
        } while (!value.compareAndSet(current, newValue));
    }
    
    /**
     * Remove energia de forma thread-safe.
     * Retorna se conseguiu (suficiente energia) ou não.
     */
    public boolean remove(float amount) {
        if (amount <= 0) return true;
        
        Float current;
        Float newValue;
        do {
            current = value.get();
            if (current < amount) {
                return false; // Energia insuficiente
            }
            newValue = current - amount;
        } while (!value.compareAndSet(current, newValue));
        
        return true;
    }
    
    /**
     * Obtém valor atual.
     */
    public float get() {
        return value.get();
    }
    
    /**
     * Define valor com sincronização total.
     */
    public void set(float newValue) {
        synchronized (updateLock) {
            value.set(Math.max(0, Math.min(newValue, maxValue)));
        }
    }
    
    /**
     * Operação atômica: remove se houver suficiente, senão falha.
     */
    public boolean tryRemove(float amount) {
        return remove(amount);
    }
    
    /**
     * Retorna se há energia suficiente (sem remover).
     */
    public boolean hasSufficient(float amount) {
        return value.get() >= amount;
    }
    
    /**
     * Retorna percentual de energia (0-100).
     */
    public float getPercentage() {
        return (value.get() / maxValue) * 100f;
    }
}
