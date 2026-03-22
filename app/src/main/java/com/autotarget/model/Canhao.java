package com.autotarget.model;

import java.util.List;
import java.util.ArrayList; // CORREÇÃO 1: Uso de ArrayList padrão

/**
 * Canhão autônomo que opera em sua própria thread.
 */
// Concorrência: A classe estende Thread pois cada Canhão funciona como uma "torreta autônoma".
// Ele toma suas próprias decisões de disparo em paralelo, sem bloquear o restante do jogo.
public class Canhao extends Thread {

    private float x;
    private float y;
    private float angulo;
    private volatile boolean ativo;

    private final Lado lado;

    /** Lista de projéteis disparados (uso de ArrayList com sincronização manual). */
    private final List<Projetil> projeteis;

    private final List<Alvo> alvos;
    private final Object collisionLock;

    private static final float VELOCIDADE_PROJETIL = 12f;
    private static final int INTERVALO_DISPARO_BASE = 1500;
    private int intervaloDisparo;

    private int larguraTela;
    private int alturaTela;

    /**
     * Instancia um Canhão, que será uma Thread independente.
     *
     * @param x Coordenada X
     * @param y Coordenada Y
     * @param lado Lado ao qual pertence
     * @param alvos Lista de alvos do jogo
     * @param collisionLock Lock de colisão usado pelo Jogo
     * @param larguraTela Largura total da tela
     * @param alturaTela Altura total da tela
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
        this.projeteis = new ArrayList<>(); // CORREÇÃO 1: Instanciando ArrayList padrão
        this.intervaloDisparo = INTERVALO_DISPARO_BASE;
    }

    @Override
    public void run() {
        while (ativo) {
            try {
                disparar();
                limparProjetisInativos();
                // Concorrência: Pausa a Thread de acordo com o cooldown (intervaloDisparo) do canhão.
                // É a forma natural de controlar a cadência de tiro através de concorrência.
                Thread.sleep(intervaloDisparo);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ativo = false;
            }
        }
    }

    /**
     * Calcula a direção e cria um projétil apontado para o alvo mais próximo
     * do seu respectivo Lado da tela.
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
                VELOCIDADE_PROJETIL, larguraTela, alturaTela
        );
        
        // Concorrência: A lista de projéteis é modificada aqui pela Thread do Canhão,
        // mas também é iterada no Game Loop (para renderização e colisões).
        // Usamos synchronized(projeteis) para garantir acesso exclusivo e evitar Race Conditions.
        synchronized (projeteis) { // CORREÇÃO 1: Adição protegida na lista de projéteis
            projeteis.add(projetil);
        }
        projetil.start();
    }

    private Alvo encontrarAlvoMaisProximoNoMesmoLado() {
        Alvo maisProximo = null;
        float menorDistancia = Float.MAX_VALUE;

        // CORREÇÃO 4: Região Crítica (Proteção na leitura da lista de alvos)
        // Previne ConcurrentModificationException pois a lista agora é um ArrayList
        // Concorrência: Esta é uma Região Crítica protegida por collisionLock. O Canhão precisa iterar a lista de alvos para mirar.
        // Sem essa trava, uma Thread do Jogo poderia deletar um alvo enquanto este canhão estivesse lendo a lista, causando erro (Race Condition).
        synchronized (collisionLock) { 
            for (Alvo alvo : alvos) {
                if (!alvo.isAtivo()) continue;
                Lado ladoAlvo = Lado.determinar(alvo.getX(), larguraTela);
                if (ladoAlvo != this.lado) continue;

                float dist = Alvo.calcularDistancia(this.x, this.y,
                        alvo.getX(), alvo.getY());
                if (dist < menorDistancia) {
                    menorDistancia = dist;
                    maisProximo = alvo;
                }
            }
        }
        return maisProximo;
    }

    private void limparProjetisInativos() {
        // Concorrência: Como a remoção altera a estrutura da lista, travamos a lista toda
        // para que ninguém tente desenhar projéteis que estão sendo deletados.
        synchronized (projeteis) { // CORREÇÃO 1: Sincronização manual correta na remoção
            projeteis.removeIf(p -> !p.isAtivo());
        }
    }

    /**
     * Aplica uma penalidade ao intervalo de disparo do canhão.
     *
     * @param penalidade true se deve sofrer penalidade, false caso contrário
     */
    public void aplicarPenalidade(boolean penalidade) {
        if (penalidade) {
            this.intervaloDisparo = INTERVALO_DISPARO_BASE * 2;
        } else {
            this.intervaloDisparo = INTERVALO_DISPARO_BASE;
        }
    }

    /**
     * Encerra a execução do canhão e remove e destrói todos os projéteis pertencentes.
     */
    public void pararCanhao() {
        this.ativo = false;
        synchronized (projeteis) { // CORREÇÃO 1: Sincronização manual na iteração de parada
            for (Projetil p : projeteis) {
                p.setAtivo(false);
                p.interrupt();
            }
        }
    }

    /**
     * Recupera a posição X atual.
     *
     * @return Posição X
     */
    public float getX() { return x; }

    /**
     * Recupera a posição Y atual.
     *
     * @return Posição Y
     */
    public float getY() { return y; }

    /**
     * Recupera o ângulo do canhão.
     *
     * @return Ângulo
     */
    public float getAngulo() { return angulo; }

    /**
     * Verifica se está ativo.
     *
     * @return true se ativo, false caso contrário
     */
    public boolean isAtivo() { return ativo; }

    /**
     * Recupera o Lado.
     *
     * @return Lado do canhão
     */
    public Lado getLado() { return lado; }

    /**
     * Retorna a lista de projéteis ativos.
     *
     * @return lista de Projétil
     */
    public List<Projetil> getProjeteis() { return projeteis; }

    /**
     * Retorna o intervalo atual de disparo.
     *
     * @return intervalo em ms
     */
    public int getIntervaloDisparo() { return intervaloDisparo; }

    /**
     * Atualiza o estado ativo.
     *
     * @param ativo boolean indicando novo estado
     */
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    /**
     * Atualiza a largura da tela.
     *
     * @param larguraTela Nova largura
     */
    public void setLarguraTela(int larguraTela) { this.larguraTela = larguraTela; }

    /**
     * Atualiza a altura da tela.
     *
     * @param alturaTela Nova altura
     */
    public void setAlturaTela(int alturaTela) { this.alturaTela = alturaTela; }

    /**
     * Recupera o intervalo de disparo base.
     *
     * @return o intervalo padrão
     */
    public static int getIntervaloDisparoBase() { return INTERVALO_DISPARO_BASE; }
}
