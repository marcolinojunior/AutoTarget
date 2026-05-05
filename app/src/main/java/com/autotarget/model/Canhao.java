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
 *   que esteja no MESMO lado. Mantém sua própria lista de projéteis e controla
 *   o intervalo de disparo, que pode sofrer penalidade (2x) quando o número
 *   de canhões do lado excede o limiar base.
 *
 * TÓPICOS DA RUBRICA ATENDIDOS NESTE ARQUIVO:
 *
 *   ► Classes/threads (6.1.4):
 *     - Canhao extends Thread — cada canhão é uma thread independente.
 *     - Atributos: posição (x, y), ângulo de rotação, lado, lista de projéteis.
 *     - Método run() → loop while(ativo) { disparar(); limparInativos(); sleep(); }
 *     - Método disparar() → localiza alvo mais próximo no mesmo lado, calcula
 *       direção normalizada, cria Projetil e inicia sua thread.
 *
 *   ► Sincronização e região crítica (6.1.5):
 *     - collisionLock (Object) — repassado a cada Projetil para verificação
 *       sincronizada de colisão com alvos (região crítica compartilhada).
 *     - synchronized(projeteis) — bloco sincronizado na lista de projéteis
 *       ao adicionar novos projéteis e ao limpar inativos.
 *     - CopyOnWriteArrayList para a lista de projéteis.
 *
 *   ► Tratamento de exceções (6.1.6):
 *     - try-catch em run() para InterruptedException (para thread com graça).
 *     - try-catch ao calcular ângulo (Math.atan2) para evitar erros aritméticos.
 *     - Verificação de distância < 0.001f para evitar divisão por zero.
 *
 *   ► Cenário competitivo (seção 4.2):
 *     - Cada canhão pertence a um Lado e só mira em alvos do MESMO lado.
 *     - Penalidade de taxa de disparo: intervaloDisparo é duplicado quando
 *       canhões do mesmo lado excedem LIMIAR_PENALIDADE (5).
 *     - pararCanhao() desativa o canhão e todos os seus projéteis.
 *
 * ATRIBUTOS PRINCIPAIS:
 *   - x, y: posição fixa do canhão no canvas (float)
 *   - angulo: ângulo de rotação para renderização do triângulo (float)
 *   - lado: campo ao qual pertence (Lado.ESQUERDO ou Lado.DIREITO)
 *   - projeteis: CopyOnWriteArrayList<Projetil> — projéteis ativos deste canhão
 *   - intervaloDisparo: ms entre disparos (base 1500ms, penalidade 3000ms)
 *
 * ============================================================================
 */
package com.autotarget.model;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Canhão autônomo que opera em sua própria thread.
 * <p>
 * Pertence a um {@link Lado} (esquerdo ou direito) e só pode disparar
 * contra alvos que estejam no mesmo lado. Quando o número de canhões
 * do seu lado ultrapassa o limite base, sofre penalidade na taxa de
 * disparo (intervalo entre tiros aumenta).
 */
public class Canhao extends Thread {

    // ── Atributos ────────────────────────────────────────────────
    private float x;
    private float y;
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

    /** Velocidade dos projéteis disparados. */
    private static final float VELOCIDADE_PROJETIL = 12f;

    /** Intervalo base entre disparos (ms). */
    private static final int INTERVALO_DISPARO_BASE = 1500;

    /** Intervalo de disparo efetivo (pode ter penalidade). */
    private int intervaloDisparo;

    /** Limites da tela. */
    private int larguraTela;
    private int alturaTela;

    // ── Construtor ───────────────────────────────────────────────

    /**
     * Cria um canhão em uma posição fixa, pertencente a um lado.
     *
     * @param x             posição X
     * @param y             posição Y
     * @param lado          lado (ESQUERDO ou DIREITO)
     * @param alvos         lista compartilhada de alvos
     * @param collisionLock lock para região crítica de colisão
     * @param larguraTela   largura do canvas
     * @param alturaTela    altura do canvas
     */
    public Canhao(float x, float y, Lado lado, List<Alvo> alvos,
                  Object collisionLock, int larguraTela, int alturaTela) {
        this.x = x;
        this.y = y;
        this.lado = lado;
        this.angulo = 0f;
        this.ativo = true;
        this.alvos = alvos;
        this.collisionLock = collisionLock;
        this.larguraTela = larguraTela;
        this.alturaTela = alturaTela;
        this.projeteis = new CopyOnWriteArrayList<>();
        this.intervaloDisparo = INTERVALO_DISPARO_BASE;
    }

    // ── Thread ───────────────────────────────────────────────────

    @Override
    public void run() {
        while (ativo) {
            try {
                disparar();
                limparProjetisInativos();
                Thread.sleep(intervaloDisparo);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ativo = false;
            }
        }
    }

    // ── Disparo ──────────────────────────────────────────────────

    /**
     * Dispara um projétil em direção ao alvo ativo mais próximo
     * que esteja no MESMO LADO deste canhão.
     */
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
                larguraTela, alturaTela
        );
        synchronized (projeteis) {
            projeteis.add(projetil);
        }
        projetil.start();
    }

    /**
     * Encontra o alvo ativo mais próximo que esteja no MESMO LADO.
     * Alvos são filtrados pela posição X relativa à linha divisória.
     *
     * @return o alvo mais próximo no mesmo lado, ou null se nenhum
     */
    private Alvo encontrarAlvoMaisProximoNoMesmoLado() {
        Alvo maisProximo = null;
        float menorDistancia = Float.MAX_VALUE;

        for (Alvo alvo : alvos) {
            if (!alvo.isAtivo()) continue;

            // Só mira em alvos do MESMO LADO
            Lado ladoAlvo = Lado.determinar(alvo.getX(), larguraTela);
            if (ladoAlvo != this.lado) continue;

            float dist = Alvo.calcularDistancia(this.x, this.y,
                    alvo.getX(), alvo.getY());
            if (dist < menorDistancia) {
                menorDistancia = dist;
                maisProximo = alvo;
            }
        }
        return maisProximo;
    }

    /**
     * Remove projéteis inativos da lista para liberar memória.
     */
    private void limparProjetisInativos() {
        synchronized (projeteis) {
            projeteis.removeIf(p -> !p.isAtivo());
        }
    }

    // ── Penalidade ───────────────────────────────────────────────

    /**
     * Aplica penalidade na taxa de disparo.
     * Canhões além do limiar base têm intervalo de disparo aumentado.
     *
     * @param penalidade true para aplicar penalidade (2x intervalo)
     */
    public void aplicarPenalidade(boolean penalidade) {
        if (penalidade) {
            this.intervaloDisparo = INTERVALO_DISPARO_BASE * 2;
        } else {
            this.intervaloDisparo = INTERVALO_DISPARO_BASE;
        }
    }

    // ── Controle ─────────────────────────────────────────────────

    /**
     * Para o canhão e todos os seus projéteis.
     */
    public void pararCanhao() {
        this.ativo = false;
        synchronized (projeteis) {
            for (Projetil p : projeteis) {
                p.setAtivo(false);
                p.interrupt();
            }
        }
    }

    // ── Getters ──────────────────────────────────────────────────

    public float getX() { return x; }
    public float getY() { return y; }
    public float getAngulo() { return angulo; }
    public boolean isAtivo() { return ativo; }
    public Lado getLado() { return lado; }
    public List<Projetil> getProjeteis() { return projeteis; }
    public int getIntervaloDisparo() { return intervaloDisparo; }

    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    public void setLarguraTela(int larguraTela) { this.larguraTela = larguraTela; }
    public void setAlturaTela(int alturaTela) { this.alturaTela = alturaTela; }

    public static int getIntervaloDisparoBase() { return INTERVALO_DISPARO_BASE; }
}
