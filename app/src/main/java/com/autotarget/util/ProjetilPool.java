package com.autotarget.util;

import com.autotarget.model.Projetil;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Padrão Object Pool para minimizar a sobrecarga de GC (Garbage Collection)
 * em ambientes de Sistemas de Tempo Real (STR) ao disparar canhões repetidamente.
 */
public class ProjetilPool {

    private static final ConcurrentLinkedQueue<Projetil> pool = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger size = new AtomicInteger(0);
    private static final int MAX_POOL_SIZE = 100;

    /**
     * Obtém uma instância reutilizável de Projetil do pool.
     * Se a pool estiver vazia, retorna null.
     */
    public static Projetil obter() {
        Projetil p = pool.poll();
        if (p != null) {
            size.decrementAndGet();
            return p;
        }
        return new Projetil();
    }

    /**
     * Devolve o Projetil inativo para a pool.
     */
    public static void liberar(Projetil p) {
        if (p != null && size.get() < MAX_POOL_SIZE) {
            // Verifica se não estamos reinserindo o mesmo objeto acidentalmente
            if (!pool.contains(p)) {
                pool.offer(p);
                size.incrementAndGet();
            }
        }
    }
}