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

/**
 * Canhão do jogo AutoTarget. Cada canhão opera em sua própria thread,
 * disparando projéteis em direção aos alvos do seu lado.
 *
 * <p><b>Modelo de Penalidade (AV2 — Rubrica §6.2.2-b):</b></p>
 * <p>
 * Canhões além do limiar {@link #LIMIAR_PENALIDADE} sofrem penalidade
 * no intervalo de disparo, modelada pela fórmula:
 * </p>
 * <pre>
 *   I = I_base × (1 + max(0, N - L) × α)
 * </pre>
 * <ul>
 *   <li><b>I</b>: intervalo de disparo efetivo (ms)</li>
 *   <li><b>I_base</b>: intervalo base ({@link #INTERVALO_DISPARO_BASE} = 1500ms)</li>
 *   <li><b>N</b>: número de canhões ativos no mesmo lado</li>
 *   <li><b>L</b>: limiar de penalidade ({@link #LIMIAR_PENALIDADE} = 5)</li>
 *   <li><b>α</b>: fator de penalidade ({@link #ALPHA_PENALIDADE} = 0.2)</li>
 * </ul>
 * <p>
 * Exemplo: com N=7 canhões ativos no mesmo lado, L=5, α=0.2:
 * I = 1500 × (1 + max(0, 7-5) × 0.2) = 1500 × 1.4 = 2100ms.
 * </p>
 * <p>
 * Justificativa: o modelo penaliza a expansão excessiva da frota,
 * criando um trade-off entre poder de fogo e eficiência energética
 * (cada canhão adicional consome energia e reduz a taxa de disparo
 * de todos os canhões do mesmo lado).
 * </p>
 *
 * <p><b>Penalidade Térmica (AV3 — Sistema Ciberfísico):</b></p>
 * <p>
 * O fator {@link #thermalPenaltyFactor} é injetado pelo {@link com.autotarget.service.ThermalSensorService}
 * quando a temperatura ambiente excede 40°C:
 * </p>
 * <pre>
 *   sleepMs = intervaloDisparo × thermalPenaltyFactor
 *   onde thermalPenaltyFactor = 1.0 + (temp - 40) × 0.1
 * </pre>
 *
 * <p><b>Escalonamento RMA (Rate Monotonic Analysis):</b></p>
 * Tarefa: T6 — Canhao.run (Disparo)
 * Período P₆ = 1500ms, Execução C₆ = 5ms, Deadline D₆ = 1500ms
 * Prioridade RM: 6 (Baixa)
 */
public class Canhao extends Thread {
    private static final String TAG = "Canhao";
    // AV1: volatile garante visibilidade entre threads (render thread lê, physics thread escreve)
    private volatile float x, y;
    // AV2: volatile para safe publication entre ReconciliacaoThread (escrita) e PhysicsTimer (leitura)
    private volatile float targetX, targetY;
    private volatile boolean movendo;
    private float angulo;
    private volatile boolean ativo;
    private final Lado lado;
    private final List<Projetil> projeteis;
    private final List<Alvo> alvos;
    private final Object collisionLock;
    private final Jogo jogo;

    private static final float VELOCIDADE_PROJETIL = 20f;
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
            try {
                long startNs = System.nanoTime();
                disparar();
                limparProjetisInativos();
                long elapsedMs = (System.nanoTime() - startNs) / 1_000_000; // execução sem sleep
                RMAAnalysis.checkDeadline("T6-Canhao", elapsedMs, intervaloDisparo);
                long sleepMs = (long) (intervaloDisparo * thermalPenaltyFactor);
                Thread.sleep(Math.max(sleepMs, 100));
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
            // AJUSTE AV2: Precisão Decimal rigorosa para antecipação balística
            double vTargetX = (tDirX * tVel) / 30.0;
            double vTargetY = (tDirY * tVel) / 30.0;
            double vProj = VELOCIDADE_PROJETIL / 16.0;

            double dx = (double)tX - this.x;
            double dy = (double)tY - this.y;

            double a = vTargetX * vTargetX + vTargetY * vTargetY - vProj * vProj;
            double b = 2 * (dx * vTargetX + dy * vTargetY);
            double c = dx * dx + dy * dy;

            double t = 0;
            if (Math.abs(a) > 0.0001) {
                double delta = b * b - 4 * a * c;
                if (delta >= 0) {
                    double t1 = (-b + Math.sqrt(delta)) / (2 * a);
                    double t2 = (-b - Math.sqrt(delta)) / (2 * a);
                    t = (t1 > 0 && t2 > 0) ? Math.min(t1, t2) : Math.max(t1, t2);
                }
            }

            // 1. Probabilistic Confidence Filter for AlvoRapido (AV2 Optimization)
            if (alvoReservado instanceof AlvoRapido && t > 0) {
                // AlvoRapido changes direction every ~30ms. t is predicted impact time in ms.
                int ticksDeVoo = (int) (t / 30.0);
                double chanceDeAcerto = Math.pow(0.95, ticksDeVoo);
                
                // If confidence is low (<65%), hold fire to save energy
                if (chanceDeAcerto < 0.65) {
                    return; 
                }
            }

            // 2. Bouncing Reflection Logic (Kinematic Folding)
            double virtualX = tX + (t > 0 ? vTargetX * t : 0);
            double virtualY = tY + (t > 0 ? vTargetY * t : 0);

            double aimX = virtualX;
            double aimY = virtualY;

            // Reflect aim position back into screen bounds if it exceeds them
            if (larguraTela > 0) {
                aimX = Math.abs(aimX);
                int bouncesX = (int) (aimX / larguraTela);
                double offsetX = aimX % larguraTela;
                aimX = (bouncesX % 2 != 0) ? (larguraTela - offsetX) : offsetX;
            }
            if (alturaTela > 0) {
                aimY = Math.abs(aimY);
                int bouncesY = (int) (aimY / alturaTela);
                double offsetY = aimY % alturaTela;
                aimY = (bouncesY % 2 != 0) ? (alturaTela - offsetY) : offsetY;
            }

            double dxAim = aimX - this.x;
            double dyAim = aimY - this.y;
            double dist = Math.sqrt(dxAim * dxAim + dyAim * dyAim);
            if (dist < 0.1) return;

            this.angulo = (float) Math.toDegrees(Math.atan2(dyAim, dxAim));
            Projetil p = com.autotarget.util.ProjetilPool.obter();
            p.reutilizar(this.x, this.y, (float)(dxAim/dist), (float)(dyAim/dist), VELOCIDADE_PROJETIL, alvos, collisionLock, larguraTela, alturaTela, jogo, lado, alvoReservado, this);
            
            projeteis.add(p);
            projeteisPool.execute(p);
            disparoEfetivado = true;

            // FIX 1: Logar o 'aimX' e 'aimY' calculados corretamente, mesmo em caso de acerto futuro
            ReconciliationLog.getInstance().logShot(this.x, this.y, tX, tY, (float)aimX, (float)aimY, false, lado.name());
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

    public void pararCanhao() {
        this.ativo = false;
        for (Projetil p : projeteis) p.setAtivo(false);
        // AV2: Liberar ExecutorService para evitar leak de threads ao parar canhão
        projeteisPool.shutdownNow();
    }

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
    public int getLarguraTela() { return larguraTela; }
    public int getAlturaTela() { return alturaTela; }
    public static int getIntervaloDisparoBase() { return INTERVALO_DISPARO_BASE; }
    public static int getLimiarPenalidade() { return LIMIAR_PENALIDADE; }
    public static float getAlphaPenalidade() { return ALPHA_PENALIDADE; }
}
