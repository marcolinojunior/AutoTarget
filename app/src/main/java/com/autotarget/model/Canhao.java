package com.autotarget.model;

import android.util.Log;
import com.autotarget.engine.Jogo;
import com.autotarget.util.RMAAnalysis;
import com.autotarget.util.ReconciliationLog;
import com.autotarget.util.DataReconciliation;
import com.autotarget.service.ReconciliacaoThread;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Canhao extends Thread {
    private static final String TAG = "Canhao";
    private float x, y, targetX, targetY;
    private boolean movendo;
    private float angulo;
    private volatile boolean ativo;
    private final Lado lado;
    private final List<Projetil> projeteis;
    private final List<Alvo> alvos;
    private final Object collisionLock;
    private final Jogo jogo;

    private static final float VELOCIDADE_PROJETIL = 12f;
    private static final int INTERVALO_DISPARO_BASE = 1500;
    private static final int LIMIAR_PENALIDADE = 5;
    private static final float ALPHA_PENALIDADE = 0.2f;
    private static final float VELOCIDADE_MOVIMENTO = 2.0f;

    private int intervaloDisparo = INTERVALO_DISPARO_BASE;
    private float thermalPenaltyFactor = 1.0f;
    private int larguraTela, alturaTela;
    private final ExecutorService projeteisPool = Executors.newFixedThreadPool(5);

    public Canhao(float x, float y, Lado lado, List<Alvo> alvos, Object collisionLock, int largura, int altura, Jogo jogo) {
        this.x = x; this.y = y; this.lado = lado; this.alvos = alvos;
        this.collisionLock = collisionLock; this.larguraTela = largura; this.alturaTela = altura;
        this.jogo = jogo; this.ativo = true;
        this.projeteis = new java.util.concurrent.CopyOnWriteArrayList<>();
    }

    @Override
    public void run() {
        while (ativo) {
            long startNs = System.nanoTime();
            try {
                disparar();
                limparProjetisInativos();
                long sleepMs = (long) (intervaloDisparo * thermalPenaltyFactor);
                Thread.sleep(Math.max(sleepMs, 100));
                RMAAnalysis.checkDeadline("T6-Canhao", (System.nanoTime() - startNs) / 1_000_000, intervaloDisparo);
            } catch (InterruptedException e) {
                ativo = false;
            } catch (Exception e) {
                Log.e(TAG, "Erro no loop do canhão", e);
            }
        }
    }

    public void disparar() {
        Alvo alvoReservado = jogo.reservarAlvo(this);
        if (alvoReservado == null || !alvoReservado.isAtivo()) {
            if (alvoReservado != null) jogo.liberarAlvo(alvoReservado);
            return;
        }

        float tX = alvoReservado.getX();
        float tY = alvoReservado.getY();
        
        DataReconciliation.ReconciliationResult recon = 
                ReconciliacaoThread.getPosicaoReconciliada(alvoReservado.getTargetId());
        if (recon != null) {
            tX = recon.x; tY = recon.y;
        }

        float tVel = alvoReservado.getVelocidade();
        float tDirX = alvoReservado.getDirecaoX();
        float tDirY = alvoReservado.getDirecaoY();

        boolean disparoEfetivado = false;
        try {
            float vTargetX = (tDirX * tVel) / 30f;
            float vTargetY = (tDirY * tVel) / 30f;
            float vProj = VELOCIDADE_PROJETIL / 16f;

            float dx = tX - this.x;
            float dy = tY - this.y;

            float a = vTargetX * vTargetX + vTargetY * vTargetY - vProj * vProj;
            float b = 2 * (dx * vTargetX + dy * vTargetY);
            float c = dx * dx + dy * dy;

            float t = 0;
            if (Math.abs(a) > 0.0001f) {
                float delta = b * b - 4 * a * c;
                if (delta >= 0) {
                    float t1 = (-b + (float) Math.sqrt(delta)) / (2 * a);
                    float t2 = (-b - (float) Math.sqrt(delta)) / (2 * a);
                    t = (t1 > 0 && t2 > 0) ? Math.min(t1, t2) : Math.max(t1, t2);
                }
            }

            float aimX = tX + (t > 0 ? vTargetX * t : 0);
            float aimY = tY + (t > 0 ? vTargetY * t : 0);

            float dxAim = aimX - this.x;
            float dyAim = aimY - this.y;
            float dist = (float) Math.sqrt(dxAim * dxAim + dyAim * dyAim);
            if (dist < 0.1f) return;

            this.angulo = (float) Math.toDegrees(Math.atan2(dyAim, dxAim));
            Projetil p = com.autotarget.util.ProjetilPool.obter();
            p.reutilizar(this.x, this.y, dxAim/dist, dyAim/dist, VELOCIDADE_PROJETIL, alvos, collisionLock, larguraTela, alturaTela, jogo, lado, alvoReservado, this);
            
            projeteis.add(p);
            projeteisPool.execute(p);
            disparoEfetivado = true;

            // FIX 1: Logar o 'aimX' e 'aimY' calculados corretamente, mesmo em caso de acerto futuro
            ReconciliationLog.getInstance().logShot(this.x, this.y, tX, tY, aimX, aimY, false, lado.name());
        } finally {
            if (!disparoEfetivado) jogo.liberarAlvo(alvoReservado);
        }
    }

    private void limparProjetisInativos() {
        projeteis.removeIf(p -> {
            if (!p.isAtivo()) { com.autotarget.util.ProjetilPool.liberar(p); return true; }
            return false;
        });
    }

    public void onProjetilFinished(Projetil p) {
        if (p != null && projeteis.remove(p)) com.autotarget.util.ProjetilPool.liberar(p);
    }

    public void aplicarPenalidade(int total) {
        this.intervaloDisparo = (int) (INTERVALO_DISPARO_BASE * (1.0f + Math.max(0, total - LIMIAR_PENALIDADE) * ALPHA_PENALIDADE));
    }

    public void moverPara(float nx, float ny) {
        this.targetX = nx; this.targetY = ny; this.movendo = true;
    }

    public void atualizarMovimento() {
        if (!movendo) return;
        float dx = targetX - x, dy = targetY - y;
        float d = (float) Math.sqrt(dx*dx + dy*dy);
        if (d < VELOCIDADE_MOVIMENTO) { x = targetX; y = targetY; movendo = false; }
        else { x += (dx/d)*VELOCIDADE_MOVIMENTO; y += (dy/d)*VELOCIDADE_MOVIMENTO; }
    }

    public void pararCanhao() { this.ativo = false; for (Projetil p : projeteis) p.setAtivo(false); }

    public float getX() { return x; }
    public float getY() { return y; }
    public float getAngulo() { return angulo; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean a) { this.ativo = a; }
    public Lado getLado() { return lado; }
    public List<Projetil> getProjeteis() { return projeteis; }
    public int getIntervaloDisparo() { return intervaloDisparo; }
    public boolean isMovendo() { return movendo; }
    public void setPosicao(float nx, float ny) { this.x = nx; this.y = ny; }
    public void setLarguraTela(int l) { this.larguraTela = l; }
    public void setAlturaTela(int h) { this.alturaTela = h; }
    public void setThermalPenaltyFactor(float f) { this.thermalPenaltyFactor = f; }
    public static int getIntervaloDisparoBase() { return INTERVALO_DISPARO_BASE; }
    public static int getLimiarPenalidade() { return LIMIAR_PENALIDADE; }
    public static float getAlphaPenalidade() { return ALPHA_PENALIDADE; }
}
