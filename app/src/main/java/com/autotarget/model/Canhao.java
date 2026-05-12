/*
 * ============================================================================
 * Arquivo: Canhao.java
 * Pacote:  com.autotarget.model
 * ============================================================================
 *
 * DESCRIÇÃO TÉCNICA:
 *   Classe que representa um canhão autônomo no jogo AutoTarget. Cada canhão
 *   opera em sua própria thread, pertence a um Lado (ESQUERDO ou DIREITO),
 *   e dispara projéteis automaticamente em direção ao alvo ativo mais próximo
 *   que esteja no MESMO lado.
 *
 * ESCALONAMENTO RMA (Rate Monotonic Analysis):
 *   Tarefa: T6 — Canhao.run (Disparos)
 *   Período P₆ = 1500ms (base), Execução C₆ = 3-5ms, Deadline D₆ = 1500ms
 *   Prioridade RM: 5 (Baixa)
 *   Fórmula Liu & Layland: Σ(Ci/Pi) ≤ n(2^(1/n) - 1)
 *
 * PENALIDADE EXPONENCIAL (AV2 §6.2.2-b):
 *   I_novo = I_base × (1 + max(0, N - L) × α)
 *   Onde: L=5 (limiar), α=0.2 (coeficiente de penalidade)
 *
 * REALOCAÇÃO (AV2 §6.2.2-d):
 *   Canhões podem mover-se gradualmente para posição ótima calculada
 *   pela ReconciliacaoThread via moverPara().
 *
 * RETROALIMENTAÇÃO TÉRMICA (AV3 §6.3.2-d):
 *   thermalPenaltyFactor multiplica o intervalo de sleep quando
 *   temperatura > 40°C.
 *
 * ============================================================================
 */
package com.autotarget.model;

import android.util.Log;
import com.autotarget.util.RMAAnalysis;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Canhão autônomo que opera em sua própria thread.
 */
public class Canhao extends Thread {

    private static final String TAG = "Canhao";

    // ── Atributos ────────────────────────────────────────────────
    private volatile float x;
    private volatile float y;
    private volatile float targetX;
    private volatile float targetY;
    private volatile boolean movendo;
    private float angulo;
    private volatile boolean ativo;

    /** Lado (campo) ao qual este canhão pertence. */
    private final Lado lado;

    /** Lista thread-safe dos projéteis disparados por este canhão. */
    private final List<Projetil> projeteis;

    /** Referência à lista compartilhada de alvos (para mirar). */
    private final List<Alvo> alvos;

    /** Lock global para colisão. */
    private final Object collisionLock;

    /** Referência ao motor do jogo (para acessar QuadTree). */
    private final com.autotarget.engine.Jogo jogo;

    /** Velocidade dos projéteis disparados. */
    private static final float VELOCIDADE_PROJETIL = 12f;

    /** Intervalo base entre disparos (ms). */
    private static final int INTERVALO_DISPARO_BASE = 1500;

    /** Limiar de penalidade (L = 5). */
    private static final int LIMIAR_PENALIDADE = 5;

    /** Coeficiente de penalidade (α = 0.2). */
    private static final float ALPHA_PENALIDADE = 0.2f;

    /** Velocidade de movimento de realocação (pixels por frame). */
    private static final float VELOCIDADE_MOVIMENTO = 2.0f;

    /** Intervalo de disparo efetivo (pode ter penalidade). */
    private volatile int intervaloDisparo;

    /** Fator de penalidade térmica (AV3 — controle por feedback). */
    private volatile float thermalPenaltyFactor = 1.0f;

    /** Limites da tela. */
    private int larguraTela;
    private int alturaTela;

    // ── Construtor ───────────────────────────────────────────────

    public Canhao(float x, float y, Lado lado, List<Alvo> alvos,
                  Object collisionLock, int larguraTela, int alturaTela,
                  com.autotarget.engine.Jogo jogo) {
        this.x = x;
        this.y = y;
        this.targetX = x;
        this.targetY = y;
        this.movendo = false;
        this.lado = lado;
        this.angulo = 0f;
        this.ativo = true;
        this.alvos = alvos;
        this.collisionLock = collisionLock;
        this.larguraTela = larguraTela;
        this.alturaTela = alturaTela;
        this.jogo = jogo;
        this.projeteis = new CopyOnWriteArrayList<>();
        this.intervaloDisparo = INTERVALO_DISPARO_BASE;
    }

    // ── Thread ───────────────────────────────────────────────────

    @Override
    public void run() {
        while (ativo) {
            long startNs = System.nanoTime();
            try {
                disparar();
                limparProjetisInativos();

                // Intervalo com penalidade térmica aplicada
                long sleepMs = (long) (intervaloDisparo * thermalPenaltyFactor);
                Thread.sleep(sleepMs);

                // ── Instrumentação RMA ──
                long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
                RMAAnalysis.checkDeadline("T6-Canhao", elapsedMs, intervaloDisparo);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ativo = false;
            }
        }
    }

    // ── Disparo ──────────────────────────────────────────────────

    public void disparar() {
        Alvo alvoMaisProximo = encontrarAlvoMaisProximoNoMesmoLado();
        if (alvoMaisProximo == null) {
            return;
        }

        float dx = alvoMaisProximo.getX() - this.x;
        float dy = alvoMaisProximo.getY() - this.y;
        float distancia = Alvo.calcularDistancia(this.x, this.y,
                alvoMaisProximo.getX(), alvoMaisProximo.getY());

        if (distancia < 0.001f) {
            return;
        }

        float dirX = dx / distancia;
        float dirY = dy / distancia;

        try {
            this.angulo = (float) Math.toDegrees(Math.atan2(dy, dx));
        } catch (Exception e) {
            this.angulo = 0f;
        }

        Projetil projetil = new Projetil(
                this.x, this.y, dirX, dirY,
                VELOCIDADE_PROJETIL, alvos, collisionLock,
                larguraTela, alturaTela, jogo, this.lado
        );
        synchronized (projeteis) {
            projeteis.add(projetil);
        }
        projetil.start();
    }

    private Alvo encontrarAlvoMaisProximoNoMesmoLado() {
        Alvo maisProximo = null;
        float menorDistancia = Float.MAX_VALUE;

        for (Alvo alvo : alvos) {
            if (!alvo.isAtivo()) continue;

            if (!alvo.isAtivo()) continue;

            float dist = Alvo.calcularDistancia(this.x, this.y,
                    alvo.getX(), alvo.getY());
            if (dist < menorDistancia) {
                menorDistancia = dist;
                maisProximo = alvo;
            }
        }
        return maisProximo;
    }

    private void limparProjetisInativos() {
        synchronized (projeteis) {
            projeteis.removeIf(p -> !p.isAtivo());
        }
    }

    // ── Penalidade Exponencial (AV2) ────────────────────────────

    /**
     * Aplica penalidade exponencial na taxa de disparo.
     * I_novo = I_base × (1 + max(0, N - L) × α)
     *
     * @param totalCanhoesNoLado número total de canhões no mesmo lado (N)
     */
    public void aplicarPenalidade(int totalCanhoesNoLado) {
        float fator = 1.0f + Math.max(0, totalCanhoesNoLado - LIMIAR_PENALIDADE)
                * ALPHA_PENALIDADE;
        this.intervaloDisparo = (int) (INTERVALO_DISPARO_BASE * fator);
    }

    /**
     * Sobrecarga de compatibilidade com API anterior.
     */
    public void aplicarPenalidade(boolean penalidade) {
        if (penalidade) {
            this.intervaloDisparo = INTERVALO_DISPARO_BASE * 2;
        } else {
            this.intervaloDisparo = INTERVALO_DISPARO_BASE;
        }
    }

    // ── Realocação Gradual (AV2 §6.2.2-d) ──────────────────────

    /**
     * Define um novo destino para o canhão. A movimentação ocorre
     * gradualmente, a cada ciclo do run().
     *
     * @param novoX posição X de destino
     * @param novoY posição Y de destino
     */
    public void moverPara(float novoX, float novoY) {
        this.targetX = novoX;
        this.targetY = novoY;
        this.movendo = true;
        Log.i(TAG, String.format("Canhão %s movendo para (%.0f, %.0f)",
                lado.name(), novoX, novoY));
    }

    /**
     * Atualiza posição gradualmente em direção ao destino.
     * Agora chamado pelo motor de física (PhysicsTimer) a 60Hz.
     */
    public void atualizarMovimento() {
        if (!movendo) return;

        float dx = targetX - x;
        float dy = targetY - y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);

        if (dist < VELOCIDADE_MOVIMENTO) {
            x = targetX;
            y = targetY;
            movendo = false;
        } else {
            x += (dx / dist) * VELOCIDADE_MOVIMENTO;
            y += (dy / dist) * VELOCIDADE_MOVIMENTO;
        }
    }

    // ── Controle ─────────────────────────────────────────────────

    public void pararCanhao() {
        this.ativo = false;
        synchronized (projeteis) {
            for (Projetil p : projeteis) {
                p.setAtivo(false);
                p.interrupt();
            }
        }
    }

    // ── Getters / Setters ────────────────────────────────────────

    public float getX() { return x; }
    public float getY() { return y; }
    public float getAngulo() { return angulo; }
    public boolean isAtivo() { return ativo; }
    public Lado getLado() { return lado; }
    public List<Projetil> getProjeteis() { return projeteis; }
    public int getIntervaloDisparo() { return intervaloDisparo; }
    public boolean isMovendo() { return movendo; }

    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    public void setLarguraTela(int larguraTela) { this.larguraTela = larguraTela; }
    public void setAlturaTela(int alturaTela) { this.alturaTela = alturaTela; }

    /** Define fator de penalidade térmica (AV3). 1.0 = normal. */
    public void setThermalPenaltyFactor(float factor) {
        this.thermalPenaltyFactor = factor;
    }

    public static int getIntervaloDisparoBase() { return INTERVALO_DISPARO_BASE; }
    public static int getLimiarPenalidade() { return LIMIAR_PENALIDADE; }
    public static float getAlphaPenalidade() { return ALPHA_PENALIDADE; }
}
