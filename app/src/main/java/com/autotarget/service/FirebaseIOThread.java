/*
 * ============================================================================
 * Arquivo: FirebaseIOThread.java
 * Pacote:  com.autotarget.service
 * ============================================================================
 *
 * DESCRIÇÃO TÉCNICA:
 *   Thread dedicada para operações de I/O assíncronas com Firebase.
 *   Utiliza ExecutorService (pool dinâmico) para isolar chamadas de rede
 *   da UI Thread. Criptografa dados via Cryptography antes de persistir.
 *
 * ============================================================================
 */
package com.autotarget.service;

import android.util.Log;

import com.autotarget.network.FirestoreRepository;
import com.autotarget.util.Cryptography;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Thread dedicada para operações de I/O com Firebase.
 * Usa ExecutorService e BlockingQueue para processamento assíncrono.
 */
public class FirebaseIOThread extends Thread {

    private static final String TAG = "FirebaseIOThread";

    /** Repositório Firestore. */
    private final FirestoreRepository firestoreRepository;

    /** Serviço de criptografia AES/GCM. */
    private final Cryptography cryptography;

    /** Fila de tarefas de persistência (thread-safe). */
    private final BlockingQueue<FirebaseTask> taskQueue;

    /** Pool de threads para execução paralela. */
    private final ExecutorService executorService;

    /** Flag de controle. */
    private volatile boolean ativo;

    /**
     * Representa uma tarefa de persistência no Firebase.
     */
    public static class FirebaseTask {
        public final int pontuacaoEsq;
        public final int pontuacaoDir;
        public final long tempo;
        public final int reconciliacoes;
        public final String vencedor;
        public final String dadosExtras;

        public FirebaseTask(int pontuacaoEsq, int pontuacaoDir,
                            long tempo, int reconciliacoes,
                            String vencedor, String dadosExtras) {
            this.pontuacaoEsq = pontuacaoEsq;
            this.pontuacaoDir = pontuacaoDir;
            this.tempo = tempo;
            this.reconciliacoes = reconciliacoes;
            this.vencedor = vencedor;
            this.dadosExtras = dadosExtras;
        }

        /** Construtor de compatibilidade. */
        public FirebaseTask(int pontuacao, long tempo, String dadosExtras) {
            this(pontuacao, 0, tempo, 0, "N/A", dadosExtras);
        }
    }

    public FirebaseIOThread(FirestoreRepository firestoreRepository,
                            Cryptography cryptography) {
        super("FirebaseIOThread");
        this.firestoreRepository = firestoreRepository;
        this.cryptography = cryptography;
        this.taskQueue = new LinkedBlockingQueue<>();
        this.executorService = Executors.newCachedThreadPool();
        this.ativo = true;
        setDaemon(true);
    }

    @Override
    public void run() {
        while (ativo) {
            try {
                FirebaseTask task = taskQueue.take();

                // Executar no pool de threads (off UI thread)
                executorService.submit(() -> {
                    try {
                        // 1. Criptografar dados sensíveis
                        String dadosCriptografados = cryptography.encrypt(task.dadosExtras);

                        // 2. Salvar no Firestore
                        firestoreRepository.salvarPartida(
                                task.pontuacaoEsq,
                                task.pontuacaoDir,
                                task.tempo,
                                task.reconciliacoes,
                                task.vencedor,
                                dadosCriptografados);

                        Log.i(TAG, "Partida persistida no Firestore (criptografada)");

                    } catch (Exception e) {
                        Log.e(TAG, "Erro ao processar tarefa Firebase", e);
                    }
                });

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ativo = false;
            }
        }
        executorService.shutdown();
    }

    /**
     * Enfileira tarefa completa de persistência.
     */
    public void salvarAsync(int pontEsq, int pontDir, long tempo,
                             int reconciliacoes, String vencedor, String dados) {
        try {
            taskQueue.put(new FirebaseTask(pontEsq, pontDir, tempo,
                    reconciliacoes, vencedor, dados));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Sobrecarga de compatibilidade.
     */
    public void salvarAsync(int pontuacao, long tempo, String dados) {
        salvarAsync(pontuacao, 0, tempo, 0, "N/A", dados);
    }

    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    public boolean isAtivo() { return ativo; }
}
