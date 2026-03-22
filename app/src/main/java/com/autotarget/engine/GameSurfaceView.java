package com.autotarget.engine;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.autotarget.model.Alvo;
import com.autotarget.model.AlvoRapido;
import com.autotarget.model.Canhao;
import com.autotarget.model.Lado;
import com.autotarget.model.Projetil;

/**
 * SurfaceView customizada para renderizar o campo de jogo AutoTarget.
 * <p>
 * Desenha duas áreas (esquerda/direita) separadas por uma linha divisória,
 * alvos, canhões, projéteis e HUD com energia/tempo por lado a ~30 FPS.
 */
public class GameSurfaceView extends SurfaceView implements SurfaceHolder.Callback {

    private RenderThread renderThread;
    private Jogo jogo;

    private final Paint paintAlvoComum;
    private final Paint paintAlvoRapido;
    private final Paint paintCanhaoEsq;
    private final Paint paintCanhaoDir;
    private final Paint paintProjetil;
    private final Paint paintTexto;
    private final Paint paintTextoGrande;
    private final Paint paintGrid;
    private final Paint paintHudBg;
    private final Paint paintDivisoria;
    private final Paint paintEnergiaBarraEsq;
    private final Paint paintEnergiaBarraDir;
    private final Paint paintEnergiaFundo;
    private final Paint paintTempoBarra;

    private static final int TARGET_FPS = 30;

    // ── Construtores ─────────────────────────────────────────────

    public GameSurfaceView(Context context) {
        super(context);
        paintAlvoComum = new Paint();
        paintAlvoRapido = new Paint();
        paintCanhaoEsq = new Paint();
        paintCanhaoDir = new Paint();
        paintProjetil = new Paint();
        paintTexto = new Paint();
        paintTextoGrande = new Paint();
        paintGrid = new Paint();
        paintHudBg = new Paint();
        paintDivisoria = new Paint();
        paintEnergiaBarraEsq = new Paint();
        paintEnergiaBarraDir = new Paint();
        paintEnergiaFundo = new Paint();
        paintTempoBarra = new Paint();
        init();
    }

    public GameSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paintAlvoComum = new Paint();
        paintAlvoRapido = new Paint();
        paintCanhaoEsq = new Paint();
        paintCanhaoDir = new Paint();
        paintProjetil = new Paint();
        paintTexto = new Paint();
        paintTextoGrande = new Paint();
        paintGrid = new Paint();
        paintHudBg = new Paint();
        paintDivisoria = new Paint();
        paintEnergiaBarraEsq = new Paint();
        paintEnergiaBarraDir = new Paint();
        paintEnergiaFundo = new Paint();
        paintTempoBarra = new Paint();
        init();
    }

    private void init() {
        getHolder().addCallback(this);
        setFocusable(true);

        // Alvos comuns → verde
        paintAlvoComum.setColor(Color.parseColor("#4CAF50"));
        paintAlvoComum.setAntiAlias(true);
        paintAlvoComum.setStyle(Paint.Style.FILL);

        // Alvos rápidos → laranja
        paintAlvoRapido.setColor(Color.parseColor("#FF9800"));
        paintAlvoRapido.setAntiAlias(true);
        paintAlvoRapido.setStyle(Paint.Style.FILL);

        // Canhão esquerdo → ciano
        paintCanhaoEsq.setColor(Color.parseColor("#00B4D8"));
        paintCanhaoEsq.setAntiAlias(true);
        paintCanhaoEsq.setStyle(Paint.Style.FILL);

        // Canhão direito → rosa/vermelho
        paintCanhaoDir.setColor(Color.parseColor("#E94560"));
        paintCanhaoDir.setAntiAlias(true);
        paintCanhaoDir.setStyle(Paint.Style.FILL);

        // Projétil → dourado
        paintProjetil.setColor(Color.parseColor("#FFD700"));
        paintProjetil.setAntiAlias(true);
        paintProjetil.setStyle(Paint.Style.FILL);

        // Texto HUD
        paintTexto.setColor(Color.WHITE);
        paintTexto.setTextSize(24f);
        paintTexto.setAntiAlias(true);

        paintTextoGrande.setColor(Color.WHITE);
        paintTextoGrande.setTextSize(36f);
        paintTextoGrande.setAntiAlias(true);
        paintTextoGrande.setTextAlign(Paint.Align.CENTER);

        // Grid
        paintGrid.setColor(Color.parseColor("#1E2A4A"));
        paintGrid.setStyle(Paint.Style.STROKE);
        paintGrid.setStrokeWidth(0.5f);

        // HUD background
        paintHudBg.setColor(Color.parseColor("#AA16213E"));
        paintHudBg.setStyle(Paint.Style.FILL);

        // Linha divisória central
        paintDivisoria.setColor(Color.parseColor("#AAFFFFFF"));
        paintDivisoria.setStyle(Paint.Style.STROKE);
        paintDivisoria.setStrokeWidth(2f);
        paintDivisoria.setPathEffect(new DashPathEffect(new float[]{15f, 10f}, 0f));

        // Barras de energia
        paintEnergiaBarraEsq.setColor(Color.parseColor("#00B4D8"));
        paintEnergiaBarraEsq.setAntiAlias(true);
        paintEnergiaBarraEsq.setStyle(Paint.Style.FILL);

        paintEnergiaBarraDir.setColor(Color.parseColor("#E94560"));
        paintEnergiaBarraDir.setAntiAlias(true);
        paintEnergiaBarraDir.setStyle(Paint.Style.FILL);

        paintEnergiaFundo.setColor(Color.parseColor("#33FFFFFF"));
        paintEnergiaFundo.setAntiAlias(true);
        paintEnergiaFundo.setStyle(Paint.Style.FILL);

        paintTempoBarra.setColor(Color.parseColor("#FFFFFF"));
        paintTempoBarra.setAntiAlias(true);
        paintTempoBarra.setStyle(Paint.Style.FILL);
    }

    // ── SurfaceHolder.Callback ───────────────────────────────────

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (jogo != null) jogo.setDimensoesTela(getWidth(), getHeight());
        renderThread = new RenderThread(holder);
        renderThread.setRunning(true);
        renderThread.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        if (jogo != null) jogo.setDimensoesTela(w, h);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (renderThread != null) {
            renderThread.setRunning(false);
            boolean retry = true;
            while (retry) {
                try { renderThread.join(); retry = false; }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        }
    }

    public void setJogo(Jogo jogo) { this.jogo = jogo; }

    // ── Renderização ─────────────────────────────────────────────

    private void desenhar(Canvas canvas) {
        if (canvas == null) return;

        int w = canvas.getWidth();
        int h = canvas.getHeight();
        float meioX = w / 2f;

        // Fundo escuro
        canvas.drawColor(Color.parseColor("#1A1A2E"));
        desenharGrid(canvas);

        if (jogo == null) return;

        // ── Linha divisória central (tracejada) ──
        canvas.drawLine(meioX, 0, meioX, h, paintDivisoria);

        // ── Labels dos campos ──
        Paint labelPaint = new Paint(paintTexto);
        labelPaint.setAlpha(80);
        labelPaint.setTextSize(14f);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("ESQUERDO", meioX / 2f, h - 10, labelPaint);
        canvas.drawText("DIREITO", meioX + meioX / 2f, h - 10, labelPaint);

        // ── Alvos (polimorfismo visual) ──
        for (Alvo alvo : jogo.getAlvos()) {
            if (!alvo.isAtivo()) continue;
            Paint paint = (alvo instanceof AlvoRapido) ? paintAlvoRapido : paintAlvoComum;

            canvas.drawCircle(alvo.getX(), alvo.getY(), alvo.getRaio() + 4, createGlowPaint(paint));
            canvas.drawCircle(alvo.getX(), alvo.getY(), alvo.getRaio(), paint);
        }

        // ── Canhões (cor diferente por lado) ──
        for (Canhao canhao : jogo.getCanhoes()) {
            if (!canhao.isAtivo()) continue;
            Paint paintCanhao = (canhao.getLado() == Lado.ESQUERDO) ? paintCanhaoEsq : paintCanhaoDir;
            desenharCanhao(canvas, canhao, paintCanhao);

            for (Projetil projetil : canhao.getProjeteis()) {
                if (projetil.isAtivo()) {
                    canvas.drawCircle(projetil.getX(), projetil.getY(),
                            Projetil.getRaio(), paintProjetil);
                }
            }
        }

        // HUD
        desenharHUD(canvas);

        // Verificar colisões a cada frame
        if (jogo.getEstado() == Jogo.Estado.RODANDO) {
            jogo.verificarColisoes();
        }

        // Tela de fim de jogo
        if (jogo.getEstado() == Jogo.Estado.ENCERRADO) {
            desenharTelaFim(canvas);
        }
    }

    private void desenharHUD(Canvas canvas) {
        if (jogo.getEstado() != Jogo.Estado.RODANDO) return;

        int w = canvas.getWidth();
        float pad = 12f;
        float meioX = w / 2f;

        // Fundo HUD
        canvas.drawRect(0, 0, w, 80, paintHudBg);

        // ── Tempo restante (centro) ──
        int tempo = jogo.getTempoRestante();
        String tempoStr = String.format("%02d:%02d", tempo / 60, tempo % 60);
        paintTextoGrande.setColor(tempo <= 10 ? Color.parseColor("#E94560") : Color.WHITE);
        canvas.drawText(tempoStr, meioX, 32f, paintTextoGrande);

        // ── Barra de tempo (toda a largura) ──
        float tempoRatio = (float) tempo / Jogo.DURACAO_PARTIDA_SEGUNDOS;
        canvas.drawRoundRect(new RectF(pad, 40, w - pad, 46), 3, 3, paintEnergiaFundo);
        if (tempoRatio > 0) {
            canvas.drawRoundRect(new RectF(pad, 40, pad + (w - 2 * pad) * tempoRatio, 46),
                    3, 3, paintTempoBarra);
        }

        // ── Energia esquerda ──
        float eEsq = jogo.getEnergiaEsquerdo() / Jogo.getEnergiaMaxima();
        canvas.drawRoundRect(new RectF(pad, 52, meioX - 6, 58), 3, 3, paintEnergiaFundo);
        if (eEsq > 0) {
            canvas.drawRoundRect(new RectF(pad, 52, pad + (meioX - pad - 6) * eEsq, 58),
                    3, 3, paintEnergiaBarraEsq);
        }

        // ── Energia direita ──
        float eDir = jogo.getEnergiaDireito() / Jogo.getEnergiaMaxima();
        canvas.drawRoundRect(new RectF(meioX + 6, 52, w - pad, 58), 3, 3, paintEnergiaFundo);
        if (eDir > 0) {
            canvas.drawRoundRect(new RectF(meioX + 6, 52, meioX + 6 + (w - pad - meioX - 6) * eDir, 58),
                    3, 3, paintEnergiaBarraDir);
        }

        // ── Pontuações ──
        paintTexto.setTextSize(20f);
        paintTexto.setColor(Color.parseColor("#00B4D8"));
        canvas.drawText("⚡" + (int) jogo.getEnergiaEsquerdo() + "  Pts:" + jogo.getPontuacaoEsquerdo(),
                pad, 75, paintTexto);

        paintTexto.setColor(Color.parseColor("#E94560"));
        Paint rightPaint = new Paint(paintTexto);
        rightPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("Pts:" + jogo.getPontuacaoDireito() + "  ⚡" + (int) jogo.getEnergiaDireito(),
                w - pad, 75, rightPaint);
        paintTexto.setTextSize(24f);
    }

    private void desenharTelaFim(Canvas canvas) {
        int w = canvas.getWidth();
        int h = canvas.getHeight();

        // Overlay
        Paint overlay = new Paint();
        overlay.setColor(Color.parseColor("#CC1A1A2E"));
        canvas.drawRect(0, 0, w, h, overlay);

        // Box
        float boxL = w * 0.08f, boxR = w * 0.92f;
        float boxT = h * 0.25f, boxB = h * 0.65f;

        Paint boxPaint = new Paint();
        boxPaint.setColor(Color.parseColor("#16213E"));
        boxPaint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(new RectF(boxL, boxT, boxR, boxB), 16, 16, boxPaint);

        Paint borderPaint = new Paint();
        borderPaint.setColor(Color.parseColor("#E94560"));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(3f);
        canvas.drawRoundRect(new RectF(boxL, boxT, boxR, boxB), 16, 16, borderPaint);

        Paint text = new Paint();
        text.setColor(Color.WHITE);
        text.setAntiAlias(true);
        text.setTextAlign(Paint.Align.CENTER);

        text.setTextSize(42f);
        canvas.drawText("FIM DE JOGO", w / 2f, boxT + 50, text);

        // Scores
        int pE = jogo.getPontuacaoEsquerdo();
        int pD = jogo.getPontuacaoDireito();

        text.setTextSize(30f);
        text.setColor(Color.parseColor("#00B4D8"));
        canvas.drawText("Esquerdo: " + pE, w / 2f, boxT + 100, text);
        text.setColor(Color.parseColor("#E94560"));
        canvas.drawText("Direito: " + pD, w / 2f, boxT + 140, text);

        // Vencedor
        text.setTextSize(34f);
        text.setColor(Color.parseColor("#FFD700"));
        String vencedor;
        if (pE > pD) vencedor = "🏆 Esquerdo Vence!";
        else if (pD > pE) vencedor = "🏆 Direito Vence!";
        else vencedor = "🤝 Empate!";
        canvas.drawText(vencedor, w / 2f, boxT + 190, text);

        text.setTextSize(22f);
        text.setColor(Color.parseColor("#AAAAAA"));
        canvas.drawText("Toque em Iniciar para jogar novamente", w / 2f, boxB - 20, text);
    }

    private void desenharCanhao(Canvas canvas, Canhao canhao, Paint paint) {
        float tamanho = 25f;
        float angRad = (float) Math.toRadians(canhao.getAngulo());

        Path path = new Path();
        path.moveTo(
                canhao.getX() + (float) Math.cos(angRad) * tamanho * 1.5f,
                canhao.getY() + (float) Math.sin(angRad) * tamanho * 1.5f);
        path.lineTo(
                canhao.getX() + (float) Math.cos(angRad + 2.4f) * tamanho,
                canhao.getY() + (float) Math.sin(angRad + 2.4f) * tamanho);
        path.lineTo(
                canhao.getX() + (float) Math.cos(angRad - 2.4f) * tamanho,
                canhao.getY() + (float) Math.sin(angRad - 2.4f) * tamanho);
        path.close();
        canvas.drawPath(path, paint);
    }

    private void desenharGrid(Canvas canvas) {
        int spacing = 60;
        for (int x = 0; x < canvas.getWidth(); x += spacing)
            canvas.drawLine(x, 0, x, canvas.getHeight(), paintGrid);
        for (int y = 0; y < canvas.getHeight(); y += spacing)
            canvas.drawLine(0, y, canvas.getWidth(), y, paintGrid);
    }

    private Paint createGlowPaint(Paint base) {
        Paint glow = new Paint(base);
        glow.setAlpha(60);
        return glow;
    }

    // ── Thread de renderização ───────────────────────────────────

    private class RenderThread extends Thread {
        private final SurfaceHolder holder;
        private volatile boolean running;

        RenderThread(SurfaceHolder holder) {
            this.holder = holder;
            this.running = false;
            setName("RenderThread");
        }

        void setRunning(boolean r) { this.running = r; }

        @Override
        public void run() {
            long targetTime = 1000 / TARGET_FPS;
            while (running) {
                long start = System.currentTimeMillis();
                Canvas canvas = null;
                try {
                    canvas = holder.lockCanvas();
                    if (canvas != null) {
                        synchronized (holder) { desenhar(canvas); }
                    }
                } catch (Exception e) {
                } finally {
                    if (canvas != null) {
                        try { holder.unlockCanvasAndPost(canvas); } catch (Exception e) {}
                    }
                }
                long elapsed = System.currentTimeMillis() - start;
                long sleep = targetTime - elapsed;
                if (sleep > 0) {
                    try { Thread.sleep(sleep); } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }
}
