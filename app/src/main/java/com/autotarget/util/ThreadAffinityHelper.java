/*
 * ============================================================================
 * Arquivo: ThreadAffinityHelper.java
 * Pacote:  com.autotarget.util
 * ============================================================================
 *
 * DESCRIÇÃO TÉCNICA:
 *   Wrapper Java para afinidade de CPU com seleção dinâmica de máscara.
 *   O helper tenta aplicar Process.setThreadAffinityMask() via reflexão e,
 *   se a API não existir, faz fallback para JNI. O comportamento pode ser
 *   parametrizado em 1 núcleo, 2 núcleos ou todos os núcleos disponíveis.
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

    public enum AffinityMode {
        ONE_CORE,
        TWO_CORES,
        ALL_CORES
    }

    private static final String TAG = "ThreadAffinity";
    private static boolean loaded = false;
    private static volatile AffinityMode affinityMode = AffinityMode.ALL_CORES;
    private static volatile boolean lastAffinitySuccess = false;
    private static volatile String lastAffinityMethod = "none";
    private static volatile String lastAffinityError = "";

    /** Máscaras de afinidade para big.LITTLE ARM SoC */
    public static final int BIG_CORES = 0xF0;     // Cores 4-7 (alto desempenho)
    public static final int LITTLE_CORES = 0x0F;   // Cores 0-3 (eficiência)
    public static final int ALL_CORES = 0xFF;       // Todos os cores
    public static final int SINGLE_CORE = 0x01;     // Core 0 apenas

    public static void setAffinityMode(AffinityMode mode) {
        affinityMode = (mode == null) ? AffinityMode.ALL_CORES : mode;
        Log.i(TAG, "Modo de afinidade atualizado para " + affinityMode);
    }

    public static AffinityMode getAffinityMode() {
        return affinityMode;
    }

    private static int buildDynamicMask(int coresDesejados) {
        int available = Math.max(1, Runtime.getRuntime().availableProcessors());
        int effective = Math.max(1, Math.min(coresDesejados, available));
        if (effective >= Integer.SIZE - 1) {
            return -1;
        }
        return (1 << effective) - 1;
    }

    private static int buildLowerHalfMask(int available) {
        int effective = Math.max(1, Math.min(available, 16));
        int half = Math.max(1, effective / 2);
        return (1 << half) - 1;
    }

    private static int buildUpperHalfMask(int available) {
        int effective = Math.max(1, Math.min(available, 16));
        int half = Math.max(1, effective / 2);
        int base = (1 << effective) - 1;
        int low = (1 << half) - 1;
        int upper = base & ~low;
        return upper == 0 ? buildDynamicMask(effective) : upper;
    }

    private static int resolveMaskForMode(boolean preferHighPerformance) {
        int available = Math.max(1, Runtime.getRuntime().availableProcessors());
        switch (affinityMode) {
            case ONE_CORE:
                return buildDynamicMask(1);
            case TWO_CORES:
                return buildDynamicMask(2);
            case ALL_CORES:
            default:
                return preferHighPerformance ? buildUpperHalfMask(available) : buildLowerHalfMask(available);
        }
    }

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
    public static boolean trySetAffinity(int threadId, int mask) {
        if (loaded) {
            try {
                setThreadAffinityMask(threadId, mask);
                lastAffinitySuccess = true;
                lastAffinityMethod = "jni";
                lastAffinityError = "";
                return true;
            } catch (Throwable e) {
                Log.w(TAG, "Erro ao definir afinidade para thread " + threadId, e);
                lastAffinitySuccess = false;
                lastAffinityMethod = "jni";
                lastAffinityError = e.getMessage() == null ? "" : e.getMessage();
            }
        }
        return false;
    }

    /**
     * Tenta usar Process.setThreadAffinityMask() via reflexão e faz fallback para JNI.
     * Alguns dispositivos não expõem a API; nesses casos, usa o wrapper nativo.
     */
    public static boolean trySetAffinityPreferProcessApi(int threadId, int mask) {
        try {
            java.lang.reflect.Method method = android.os.Process.class
                    .getDeclaredMethod("setThreadAffinityMask", int.class, int.class);
            method.setAccessible(true);
            method.invoke(null, threadId, mask);
            Log.i(TAG, "Afinidade aplicada via Process.setThreadAffinityMask para thread " + threadId + " com máscara " + mask);
            lastAffinitySuccess = true;
            lastAffinityMethod = "process-api";
            lastAffinityError = "";
            return true;
        } catch (Throwable e) {
            Log.w(TAG, "Process.setThreadAffinityMask não disponível via reflexão (fallback para JNI). Motivo: " + e.getMessage());
            lastAffinitySuccess = false;
            lastAffinityMethod = "process-api";
            lastAffinityError = e.getMessage() == null ? "" : e.getMessage();
        }
        return trySetAffinity(threadId, mask);
    }

    /**
     * Define a afinidade para tarefas críticas de alto desempenho (ex: Motor de Física).
     * @param threadId ID da thread (obtido via android.os.Process.myTid())
     */
    public static void setAffinityForCriticalTask(int threadId) {
        trySetAffinityPreferProcessApi(threadId, resolveMaskForMode(true));
    }

    /**
     * Define a afinidade para tarefas de prioridade média (ex: Renderização de UI).
     * @param threadId ID da thread (obtido via android.os.Process.myTid())
     */
    public static void setAffinityForMediumTask(int threadId) {
        int available = Math.max(1, Runtime.getRuntime().availableProcessors());
        int mask;
        if (affinityMode == AffinityMode.ONE_CORE) {
            mask = buildDynamicMask(1);
        } else if (affinityMode == AffinityMode.TWO_CORES) {
            mask = buildDynamicMask(2);
        } else {
            mask = buildDynamicMask(available);
        }
        trySetAffinityPreferProcessApi(threadId, mask);
    }

    /**
     * Define a afinidade para tarefas de processamento de fundo (ex: Sensores, Reconciliação).
     * @param threadId ID da thread (obtido via android.os.Process.myTid())
     */
    public static void setAffinityForBackgroundTask(int threadId) {
        trySetAffinityPreferProcessApi(threadId, resolveMaskForMode(false));
    }

    public static boolean wasLastAffinitySuccessful() {
        return lastAffinitySuccess;
    }

    public static String getLastAffinityMethod() {
        return lastAffinityMethod;
    }

    public static String getLastAffinityError() {
        return lastAffinityError;
    }
}
