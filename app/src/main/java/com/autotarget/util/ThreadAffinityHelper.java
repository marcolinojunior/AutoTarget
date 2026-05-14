/*
 * ============================================================================
 * Arquivo: ThreadAffinityHelper.java
 * Pacote:  com.autotarget.util
 * ============================================================================
 *
 * DESCRIÇÃO TÉCNICA:
 *   Wrapper Java para o módulo JNI de afinidade de núcleo de CPU.
 *   Permite ancorar threads em núcleos específicos do SoC ARM,
 *   eliminando cache misses causados pela migração dinâmica de threads
 *   pelo kernel Linux do Android.
 *
 * ESCALONAMENTO RMA:
 *   Usado para garantir que tarefas de alta prioridade (T1 PhysicsTimer,
 *   T4 RenderThread) executem em big cores, e tarefas de baixa prioridade
 *   (T8 ReconciliacaoThread) executem em LITTLE cores.
 *
 * ============================================================================
 */
package com.autotarget.util;

import android.util.Log;

/**
 * Helper para afinidade de CPU via JNI.
 * Fallback gracioso se NDK não estiver disponível.
 */
public class ThreadAffinityHelper {

    private static final String TAG = "ThreadAffinity";
    private static boolean loaded = false;

    /** Máscaras de afinidade para big.LITTLE ARM SoC */
    public static final int BIG_CORES = 0xF0;     // Cores 4-7 (alto desempenho)
    public static final int LITTLE_CORES = 0x0F;   // Cores 0-3 (eficiência)
    public static final int ALL_CORES = 0xFF;       // Todos os cores
    public static final int SINGLE_CORE = 0x01;     // Core 0 apenas

    static {
        try {
            System.loadLibrary("threadaffinity");
            loaded = true;
            Log.i(TAG, "Biblioteca JNI threadaffinity carregada com sucesso");
        } catch (UnsatisfiedLinkError e) {
            loaded = false;
            Log.w(TAG, "NDK não disponível — afinidade de CPU desabilitada", e);
        }
    }

    /**
     * Define a máscara de afinidade de CPU para uma thread.
     *
     * @param threadId ID da thread (obtido via android.os.Process.myTid())
     * @param mask     máscara de bits indicando quais cores podem executar a thread
     */
    public static native void setThreadAffinityMask(int threadId, int mask);

    /**
     * Verifica se a biblioteca JNI foi carregada com sucesso.
     */
    public static boolean isAvailable() {
        return loaded;
    }

    /**
     * Tenta definir a afinidade de CPU, ignorando silenciosamente se JNI não disponível.
     */
    public static void trySetAffinity(int threadId, int mask) {
        if (loaded) {
            try {
                setThreadAffinityMask(threadId, mask);
            } catch (Exception e) {
                Log.w(TAG, "Erro ao definir afinidade para thread " + threadId, e);
            }
        }
    }

    /**
     * Tenta usar Process.setThreadAffinityMask() via reflexão e faz fallback para JNI.
     * Alguns dispositivos não expõem a API; nesses casos, usa o wrapper nativo.
     */
    public static void trySetAffinityPreferProcessApi(int threadId, int mask) {
        try {
            java.lang.reflect.Method method = android.os.Process.class
                    .getDeclaredMethod("setThreadAffinityMask", int.class, int.class);
            method.setAccessible(true);
            method.invoke(null, threadId, mask);
            Log.i(TAG, "Afinidade aplicada via Process.setThreadAffinityMask");
            return;
        } catch (Throwable ignored) {
            // Fallback abaixo.
        }
        trySetAffinity(threadId, mask);
    }
}
