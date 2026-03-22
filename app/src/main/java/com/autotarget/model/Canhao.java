package com.autotarget.model;

import java.util.List;
import java.util.ArrayList; // CORREÇÃO 1: Uso de ArrayList padrão

/**
 * Canhão autônomo que opera em sua própria thread.
 */
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
                Thread.sleep(intervaloDisparo);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ativo = false;
            }
        }
    }

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
        synchronized (projeteis) { // CORREÇÃO 1: Sincronização manual correta na remoção
            projeteis.removeIf(p -> !p.isAtivo());
        }
    }

    public void aplicarPenalidade(boolean penalidade) {
        if (penalidade) {
            this.intervaloDisparo = INTERVALO_DISPARO_BASE * 2;
        } else {
            this.intervaloDisparo = INTERVALO_DISPARO_BASE;
        }
    }

    public void pararCanhao() {
        this.ativo = false;
        synchronized (projeteis) { // CORREÇÃO 1: Sincronização manual na iteração de parada
            for (Projetil p : projeteis) {
                p.setAtivo(false);
                p.interrupt();
            }
        }
    }

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
