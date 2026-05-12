/*
 * ============================================================================
 * Arquivo: Projetil.java
 * Pacote:  com.autotarget.model
 * ============================================================================
 *
 * DESCRIÇÃO TÉCNICA:
 *   Classe que representa um projétil disparado por um Canhao no jogo AutoTarget.
 *   Cada projétil opera em sua própria thread, movendo-se em linha reta na
 *   direção definida no momento do disparo. A cada frame, verifica colisão
 *   contra a lista compartilhada de alvos dentro de uma região crítica
 *   (synchronized com collisionLock).
 *
 * TÓPICOS DA RUBRICA ATENDIDOS NESTE ARQUIVO:
 *
 *   ► Classes/threads (6.1.4):
 *     - Projetil extends Thread — cada projétil é uma thread independente.
 *     - Atributos: posição (x, y), direção normalizada (direcaoX/Y), velocidade.
 *     - Método run() → loop while(ativo) { mover(); foraDosTela?; colisões; sleep; }
 *     - Move-se ponto a ponto em linha reta (16ms entre atualizações ≈ 60 FPS).
 *     - Auto-desativa ao sair dos limites da tela ou ao colidir com alvo.
 *
 *   ► Sincronização e região crítica (6.1.5):
 *     - verificarColisoes() usa synchronized(collisionLock) — REGIÃO CRÍTICA.
 *     - Garante que apenas um projétil pode verificar colisão com um alvo por vez,
 *       evitando condições de corrida (dois projéteis "matando" o mesmo alvo).
 *     - O lock é compartilhado entre todos os projéteis e o Jogo.
 *
 *   ► Tratamento de exceções (6.1.6):
 *     - InterruptedException capturada em run() — seta interrupt flag e desativa.
 *
 *   ► Testes unitários (6.1.7):
 *     - collide() e calcularDistancia() testados em ProjetilTest:
 *       colisão detectada, colisão não detectada, fronteira exata,
 *       movimento retilíneo e diagonal.
 *
 * DETECÇÃO DE COLISÃO:
 *   - Usa distância euclidiana: dist(projetil, alvo) <= RAIO_PROJETIL + raioAlvo
 *   - Ao colidir: alvo.setAtivo(false) + projetil.ativo = false
 *   - RAIO do projétil = 5f (constante)
 *
 * ESCALONAMENTO RMA (Rate Monotonic Analysis):
 *   Tarefa: T2 — Projetil.run (Balística)
 *   Período P₂ = 16ms, Execução C₂ = 1-2ms, Deadline D₂ = 16ms
 *   Prioridade RM: 1 (Máxima)
 *
 * ============================================================================
 */
package com.autotarget.model;

import java.util.List;

/**
 * Projétil disparado por um canhão.
 * <p>
 * Cada projétil opera em sua própria thread, movendo-se em linha reta
 * na direção definida no momento do disparo. Verifica colisão contra
 * a lista compartilhada de alvos usando sincronização explícita.
 */
public class Projetil extends Thread {

    // ── Atributos ────────────────────────────────────────────────
    private float x;
    private float y;
    private float direcaoX;
    private float direcaoY;
    private float velocidade;
    private volatile boolean ativo;

    /** Raio visual do projétil para detecção de colisão. */
    private static final float RAIO = 5f;

    /** Intervalo entre atualizações de posição (ms). */
    private static final int INTERVALO_ATUALIZACAO = 16;

    /** Referência à lista compartilhada de alvos (região crítica). */
    private final List<Alvo> alvos;

    /** Lock para região crítica de colisão. */
    private final Object collisionLock;

    /** Limites da tela. */
    private final int larguraTela;
    private final int alturaTela;

    /** Referência ao motor do jogo para QuadTree. */
    private final com.autotarget.engine.Jogo jogo;
    
    /** Lado ao qual este projétil pertence. */
    private final com.autotarget.model.Lado lado;

    // ── Construtor ───────────────────────────────────────────────

    /**
     * Cria um projétil.
     *
     * @param x             posição X de origem (posição do canhão)
     * @param y             posição Y de origem
     * @param direcaoX      componente X da direção normalizada
     * @param direcaoY      componente Y da direção normalizada
     * @param velocidade    velocidade do projétil (px/atualização)
     * @param alvos         lista compartilhada de alvos
     * @param collisionLock objeto de lock para região crítica
     * @param larguraTela   largura do canvas
     * @param alturaTela    altura do canvas
     */
    public Projetil(float x, float y, float direcaoX, float direcaoY,
                    float velocidade, List<Alvo> alvos, Object collisionLock,
                    int larguraTela, int alturaTela, com.autotarget.engine.Jogo jogo,
                    com.autotarget.model.Lado lado) {
        this.x = x;
        this.y = y;
        this.direcaoX = direcaoX;
        this.direcaoY = direcaoY;
        this.velocidade = velocidade;
        this.alvos = alvos;
        this.collisionLock = collisionLock;
        this.larguraTela = larguraTela;
        this.alturaTela = alturaTela;
        this.jogo = jogo;
        this.lado = lado;
        this.ativo = true;
    }

    // ── Thread ───────────────────────────────────────────────────

    @Override
    public void run() {
        while (ativo) {
            try {
                mover();
                if (foraDosTela()) {
                    ativo = false;
                    break;
                }
                verificarColisoes();
                Thread.sleep(INTERVALO_ATUALIZACAO);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ativo = false;
            }
        }
    }

    // ── Movimento ────────────────────────────────────────────────

    /**
     * Desloca o projétil na direção definida.
     */
    public void mover() {
        this.x += direcaoX * velocidade;
        this.y += direcaoY * velocidade;
    }

    /**
     * Verifica se o projétil saiu dos limites da tela.
     */
    private boolean foraDosTela() {
        return x < -RAIO || x > larguraTela + RAIO
            || y < -RAIO || y > alturaTela + RAIO;
    }

    // ── Colisão ──────────────────────────────────────────────────

    /**
     * Verifica colisão com cada alvo ativo na lista compartilhada.
     * <p>
     * Região crítica: apenas um projétil pode verificar colisão
     * com um alvo por vez, evitando condições de corrida.
     */
    private void verificarColisoes() {
        synchronized (collisionLock) {
            com.autotarget.util.QuadTree qt = jogo.getQuadTree(this.lado);
            List<Alvo> candidatos;
            
            if (qt != null) {
                candidatos = qt.query(this.x, this.y, RAIO);
            } else {
                candidatos = alvos;
            }
            
            for (Alvo alvo : candidatos) {
                if (alvo.isAtivo() && collide(alvo)) {
                    alvo.setAtivo(false);
                    this.ativo = false;
                    break;
                }
            }
        }
    }

    /**
     * Verifica se este projétil colidiu com o dado alvo
     * usando a distância euclidiana.
     *
     * @param alvo o alvo a verificar
     * @return true se houve colisão
     */
    public boolean collide(Alvo alvo) {
        float distancia = Alvo.calcularDistancia(this.x, this.y, alvo.getX(), alvo.getY());
        return distancia <= (RAIO + alvo.getRaio());
    }

    /**
     * Calcula distância euclidiana — método público estático para testes.
     */
    public static float calcularDistancia(float x1, float y1, float x2, float y2) {
        return Alvo.calcularDistancia(x1, y1, x2, y2);
    }

    // ── Getters ──────────────────────────────────────────────────

    public float getX() { return x; }
    public float getY() { return y; }
    public float getVelocidade() { return velocidade; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    public static float getRaio() { return RAIO; }
}
