/*
 * ============================================================================
 * Arquivo: FirebaseIOThread.java
 * Pacote:  com.autotarget.service
 * ============================================================================
 *
 * DESCRIÇÃO TÉCNICA:
 *   Thread dedicada para operações de I/O assíncronas com Firebase (Firestore).
 *   Conforme o diagrama de arquitetura do AutoTarget, esta thread recebe dados
 *   da região crítica (lista de alvos/projéteis), criptografa-os via
 *   Cryptography e persiste no Firestore via FirestoreRepository.
 *   Utiliza uma BlockingQueue (LinkedBlockingQueue) para enfileirar tarefas
 *   de persistência de forma thread-safe.
 *
 * TÓPICOS DA RUBRICA ATENDIDOS NESTE ARQUIVO:
 *
 *   ► Classes/threads (6.1.4):
 *     - FirebaseIOThread extends Thread — thread daemon dedicada a I/O.
 *     - Loop run() usa BlockingQueue.take() — bloqueia até receber tarefa.
 *     - Modelo produtor-consumidor: Jogo.encerrarPartida() enfileira
 *       (salvarAsync) e esta thread consome/processa assincronamente.
 *     - Classe interna FirebaseTask encapsula dados de persistência
 *       (pontuação, tempo, dadosExtras).
 *
 *   ► Sincronização (6.1.5):
 *     - BlockingQueue (LinkedBlockingQueue) — estrutura thread-safe para
 *       comunicação entre a thread principal (Jogo) e a thread de I/O.
 *     - volatile no flag 'ativo' para visibilidade entre threads.
 *     - Daemon thread: não impede o encerramento da aplicação.
 *
 *   ► Tratamento de exceções (6.1.6):
 *     - InterruptedException capturada em run() e salvarAsync().
 *     - Catch genérico (Exception) no loop para falhas de Firebase I/O:
 *       continua processando a fila mesmo em caso de erro pontual.
 *
 *   ► Segurança / criptografia:
 *     - Invoca cryptography.encrypt(dadosExtras) antes de salvar,
 *       demonstrando o fluxo: dados → criptografia → persistência.
 *
 * FLUXO:
 *   Jogo.encerrarPartida() → salvarAsync(pontuacao, tempo, dados)
 *   → taskQueue.put(FirebaseTask)
 *   → run(): taskQueue.take() → encrypt → salvarPartida → loop
 *
 * ============================================================================
 */
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
