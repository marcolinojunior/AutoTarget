package com.autotarget.service;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Thread dedicada para operações de I/O com Firebase (Firestore).
 * <p>
 * Conforme a arquitetura do AutoTarget, esta thread opera de forma
 * assíncrona, recebendo dados da região crítica, criptografando-os
 * via {@link Cryptography} e persistindo no Firestore.
 * <p>
 * Fluxo: Lista (Região Crítica) → [esta thread] → Criptografia → Firebase ☁️
 */
public class FirebaseIOThread extends Thread {

    /** Repositório Firestore. */
    private final FirestoreRepository firestoreRepository;

    /** Serviço de criptografia. */
    private final Cryptography cryptography;

    /** Fila de tarefas de persistência (thread-safe). */
    private final BlockingQueue<FirebaseTask> taskQueue;

    /** Flag de controle. */
    private volatile boolean ativo;

    /**
     * Representa uma tarefa de persistência no Firebase.
     */
    public static class FirebaseTask {
        public final int pontuacao;
        public final long tempo;
        public final String dadosExtras;

        public FirebaseTask(int pontuacao, long tempo, String dadosExtras) {
            this.pontuacao = pontuacao;
            this.tempo = tempo;
            this.dadosExtras = dadosExtras;
        }
    }

    /**
     * Cria a thread de Firebase I/O.
     *
     * @param firestoreRepository repositório Firestore
     * @param cryptography        serviço de criptografia
     */
    public FirebaseIOThread(FirestoreRepository firestoreRepository,
                            Cryptography cryptography) {
        super("FirebaseIOThread");
        this.firestoreRepository = firestoreRepository;
        this.cryptography = cryptography;
        this.taskQueue = new LinkedBlockingQueue<>();
        this.ativo = true;
        setDaemon(true);
    }

    @Override
    public void run() {
        while (ativo) {
            try {
                // Bloqueia até receber uma tarefa
                FirebaseTask task = taskQueue.take();

                // 1. Criptografar dados sensíveis antes de enviar
                String dadosCriptografados = cryptography.encrypt(task.dadosExtras);

                // 2. Salvar no Firestore (stub por enquanto)
                firestoreRepository.salvarPartida(task.pontuacao, task.tempo);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ativo = false;
            } catch (Exception e) {
                // Erro no Firebase I/O, continua processando a fila
            }
        }
    }

    /**
     * Enfileira uma tarefa de persistência para execução assíncrona.
     *
     * @param pontuacao pontuação da partida
     * @param tempo     tempo total da partida
     * @param dados     dados extras a criptografar
     */
    public void salvarAsync(int pontuacao, long tempo, String dados) {
        try {
            taskQueue.put(new FirebaseTask(pontuacao, tempo, dados));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    public boolean isAtivo() { return ativo; }
}
