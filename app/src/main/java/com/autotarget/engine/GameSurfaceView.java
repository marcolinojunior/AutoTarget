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
import androidx.core.content.ContextCompat;

import com.autotarget.R;
import com.autotarget.model.Alvo;
import com.autotarget.model.AlvoRapido;
import com.autotarget.model.Canhao;
import com.autotarget.model.Lado;
import com.autotarget.model.Projetil;

/**
 * SurfaceView customizada para renderizar o campo de jogo AutoTarget.
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

    /**
     * Construtor da GameSurfaceView, usado para instanciar via código.
     *
     * @param context O contexto atual
     */
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
        init(context);
    }

    /**
     * Construtor da GameSurfaceView, usado para inflar via XML.
     *
     * @param context O contexto atual
     * @param attrs Os atributos da view
     */
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
        init(context);
    }

    private void init(Context context) {
        getHolder().addCallback(this);
        setFocusable(true);

        paintAlvoComum.setColor(ContextCompat.getColor(context, R.color.alvo_comum));
        paintAlvoComum.setAntiAlias(true);
        paintAlvoComum.setStyle(Paint.Style.FILL);

        paintAlvoRapido.setColor(ContextCompat.getColor(context, R.color.alvo_rapido));
        paintAlvoRapido.setAntiAlias(true);
        paintAlvoRapido.setStyle(Paint.Style.FILL);

        paintCanhaoEsq.setColor(ContextCompat.getColor(context, R.color.accent));
        paintCanhaoEsq.setAntiAlias(true);
        paintCanhaoEsq.setStyle(Paint.Style.FILL);

        paintCanhaoDir.setColor(ContextCompat.getColor(context, R.color.primary));
        paintCanhaoDir.setAntiAlias(true);
        paintCanhaoDir.setStyle(Paint.Style.FILL);

        paintProjetil.setColor(ContextCompat.getColor(context, R.color.projetil));
        paintProjetil.setAntiAlias(true);
        paintProjetil.setStyle(Paint.Style.FILL);

        paintTexto.setColor(ContextCompat.getColor(context, R.color.white));
        paintTexto.setTextSize(24f);
        paintTexto.setAntiAlias(true);

        paintTextoGrande.setColor(ContextCompat.getColor(context, R.color.white));
        paintTextoGrande.setTextSize(36f);
        paintTextoGrande.setAntiAlias(true);
        paintTextoGrande.setTextAlign(Paint.Align.CENTER);

        paintGrid.setColor(ContextCompat.getColor(context, R.color.grid));
        paintGrid.setStyle(Paint.Style.STROKE);
        paintGrid.setStrokeWidth(0.5f);

        paintHudBg.setColor(ContextCompat.getColor(context, R.color.hud_bg));
        paintHudBg.setStyle(Paint.Style.FILL);

        paintDivisoria.setColor(ContextCompat.getColor(context, R.color.divisoria));
        paintDivisoria.setStyle(Paint.Style.STROKE);
        paintDivisoria.setStrokeWidth(2f);
        paintDivisoria.setPathEffect(new DashPathEffect(new float[]{15f, 10f}, 0f));

        paintEnergiaBarraEsq.setColor(ContextCompat.getColor(context, R.color.accent));
        paintEnergiaBarraEsq.setAntiAlias(true);
        paintEnergiaBarraEsq.setStyle(Paint.Style.FILL);

        paintEnergiaBarraDir.setColor(ContextCompat.getColor(context, R.color.primary));
        paintEnergiaBarraDir.setAntiAlias(true);
        paintEnergiaBarraDir.setStyle(Paint.Style.FILL);

        paintEnergiaFundo.setColor(ContextCompat.getColor(context, R.color.energia_fundo));
        paintEnergiaFundo.setAntiAlias(true);
        paintEnergiaFundo.setStyle(Paint.Style.FILL);

        paintTempoBarra.setColor(ContextCompat.getColor(context, R.color.white));
        paintTempoBarra.setAntiAlias(true);
        paintTempoBarra.setStyle(Paint.Style.FILL);
    }

    /**
     * Chamado quando o Surface é criado.
     * Atualiza as dimensões do jogo e inicia a Thread de renderização.
     *
     * @param holder O SurfaceHolder
     */
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (jogo != null) jogo.setDimensoesTela(getWidth(), getHeight());
        renderThread = new RenderThread(holder);
        renderThread.setRunning(true);
        renderThread.start();
    }

    /**
     * Chamado quando as características físicas (como tamanho) do Surface mudam.
     *
     * @param holder O SurfaceHolder
     * @param format O novo PixelFormat
     * @param w A nova largura
     * @param h A nova altura
     */
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        if (jogo != null) jogo.setDimensoesTela(w, h);
    }

    /**
     * Chamado quando o Surface vai ser destruído.
     * Encerra a thread de renderização com segurança.
     *
     * @param holder O SurfaceHolder
     */
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

    /**
     * Define o controlador do jogo para uso e leitura.
     *
     * @param jogo Instância do jogo
     */
    public void setJogo(Jogo jogo) { this.jogo = jogo; }

    private void desenhar(Canvas canvas) {
        if (canvas == null) return;

        int w = canvas.getWidth();
        int h = canvas.getHeight();
        float meioX = w / 2f;

        canvas.drawColor(ContextCompat.getColor(getContext(), R.color.background_dark));
        desenharGrid(canvas);

        if (jogo == null) return;

        canvas.drawLine(meioX, 0, meioX, h, paintDivisoria);

        Paint labelPaint = new Paint(paintTexto);
        labelPaint.setAlpha(80);
        labelPaint.setTextSize(14f);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(getContext().getString(R.string.lado_esquerdo), meioX / 2f, h - 10, labelPaint);
        canvas.drawText(getContext().getString(R.string.lado_direito), meioX + meioX / 2f, h - 10, labelPaint);

        // CORREÇÃO 1: Proteção na iteração dos alvos para renderização
        synchronized (jogo.getCollisionLock()) {
            for (Alvo alvo : jogo.getAlvos()) {
                if (!alvo.isAtivo()) continue;
                Paint paint = (alvo instanceof AlvoRapido) ? paintAlvoRapido : paintAlvoComum;

                canvas.drawCircle(alvo.getX(), alvo.getY(), alvo.getRaio() + 4, createGlowPaint(paint));
                canvas.drawCircle(alvo.getX(), alvo.getY(), alvo.getRaio(), paint);
            }
        }

        // CORREÇÃO 1: Proteção na iteração dos canhoes e seus projeteis
        synchronized (jogo.getCanhoes()) {
            for (Canhao canhao : jogo.getCanhoes()) {
                if (!canhao.isAtivo()) continue;
                Paint paintCanhao = (canhao.getLado() == Lado.ESQUERDO) ? paintCanhaoEsq : paintCanhaoDir;
                desenharCanhao(canvas, canhao, paintCanhao);

                // Proteção na iteração dos projéteis de cada canhão
                synchronized (canhao.getProjeteis()) {
                    for (Projetil projetil : canhao.getProjeteis()) {
                        if (projetil.isAtivo()) {
                            canvas.drawCircle(projetil.getX(), projetil.getY(),
                                    Projetil.getRaio(), paintProjetil);
                        }
                    }
                }
            }
        }

        desenharHUD(canvas);

        // CORREÇÃO 3: Lógica de negócio (física) movida para timer dedicado no Jogo.java
        // REMOVIDA A CHAMADA jogo.verificarColisoes() DESTE MÉTODO DE DESENHO (RENDER THREAD)

        if (jogo.getEstado() == Jogo.Estado.ENCERRADO) {
            desenharTelaFim(canvas);
        }
    }

    private void desenharHUD(Canvas canvas) {
        if (jogo.getEstado() != Jogo.Estado.RODANDO) return;

        int w = canvas.getWidth();
        float pad = 12f;
        float meioX = w / 2f;

        canvas.drawRect(0, 0, w, 80, paintHudBg);

        int tempo = jogo.getTempoRestante();
        String tempoStr = String.format("%02d:%02d", tempo / 60, tempo % 60);
        paintTextoGrande.setColor(tempo <= 10 ? ContextCompat.getColor(getContext(), R.color.primary) : ContextCompat.getColor(getContext(), R.color.white));
        canvas.drawText(tempoStr, meioX, 32f, paintTextoGrande);

        float tempoRatio = (float) tempo / Jogo.DURACAO_PARTIDA_SEGUNDOS;
        canvas.drawRoundRect(new RectF(pad, 40, w - pad, 46), 3, 3, paintEnergiaFundo);
        if (tempoRatio > 0) {
            canvas.drawRoundRect(new RectF(pad, 40, pad + (w - 2 * pad) * tempoRatio, 46),
                    3, 3, paintTempoBarra);
        }

        float eEsq = jogo.getEnergiaEsquerdo() / Jogo.getEnergiaMaxima();
        canvas.drawRoundRect(new RectF(pad, 52, meioX - 6, 58), 3, 3, paintEnergiaFundo);
        if (eEsq > 0) {
            canvas.drawRoundRect(new RectF(pad, 52, pad + (meioX - pad - 6) * eEsq, 58),
                    3, 3, paintEnergiaBarraEsq);
        }

        float eDir = jogo.getEnergiaDireito() / Jogo.getEnergiaMaxima();
        canvas.drawRoundRect(new RectF(meioX + 6, 52, w - pad, 58), 3, 3, paintEnergiaFundo);
        if (eDir > 0) {
            canvas.drawRoundRect(new RectF(meioX + 6, 52, meioX + 6 + (w - pad - meioX - 6) * eDir, 58),
                    3, 3, paintEnergiaBarraDir);
        }

        paintTexto.setTextSize(20f);
        paintTexto.setColor(ContextCompat.getColor(getContext(), R.color.accent));
        String textEsq = getContext().getString(R.string.hud_score_esq,
                (int) jogo.getEnergiaEsquerdo(), jogo.getPontuacaoEsquerdo());
        canvas.drawText(textEsq, pad, 75, paintTexto);

        paintTexto.setColor(ContextCompat.getColor(getContext(), R.color.primary));
        Paint rightPaint = new Paint(paintTexto);
        rightPaint.setTextAlign(Paint.Align.RIGHT);
        String textDir = getContext().getString(R.string.hud_score_dir,
                jogo.getPontuacaoDireito(), (int) jogo.getEnergiaDireito());
        canvas.drawText(textDir, w - pad, 75, rightPaint);
        paintTexto.setTextSize(24f);
    }

    private void desenharTelaFim(Canvas canvas) {
        int w = canvas.getWidth();
        int h = canvas.getHeight();

        Paint overlay = new Paint();
        overlay.setColor(ContextCompat.getColor(getContext(), R.color.overlay_fim));
        canvas.drawRect(0, 0, w, h, overlay);

        float boxL = w * 0.08f, boxR = w * 0.92f;
        float boxT = h * 0.25f, boxB = h * 0.65f;

        Paint boxPaint = new Paint();
        boxPaint.setColor(ContextCompat.getColor(getContext(), R.color.box_fim));
        boxPaint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(new RectF(boxL, boxT, boxR, boxB), 16, 16, boxPaint);

        Paint borderPaint = new Paint();
        borderPaint.setColor(ContextCompat.getColor(getContext(), R.color.primary));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(3f);
        canvas.drawRoundRect(new RectF(boxL, boxT, boxR, boxB), 16, 16, borderPaint);

        Paint text = new Paint();
        text.setColor(ContextCompat.getColor(getContext(), R.color.white));
        text.setAntiAlias(true);
        text.setTextAlign(Paint.Align.CENTER);

        text.setTextSize(42f);
        canvas.drawText(getContext().getString(R.string.fim_de_jogo), w / 2f, boxT + 50, text);

        int pE = jogo.getPontuacaoEsquerdo();
        int pD = jogo.getPontuacaoDireito();

        text.setTextSize(30f);
        text.setColor(ContextCompat.getColor(getContext(), R.color.accent));
        canvas.drawText(getContext().getString(R.string.resultado_esquerdo) + pE, w / 2f, boxT + 100, text);
        text.setColor(ContextCompat.getColor(getContext(), R.color.primary));
        canvas.drawText(getContext().getString(R.string.resultado_direito) + pD, w / 2f, boxT + 140, text);

        text.setTextSize(34f);
        text.setColor(ContextCompat.getColor(getContext(), R.color.projetil));
        String vencedor;
        if (pE > pD) vencedor = getContext().getString(R.string.vitoria_esquerdo);
        else if (pD > pE) vencedor = getContext().getString(R.string.vitoria_direito);
        else vencedor = getContext().getString(R.string.empate);
        canvas.drawText(vencedor, w / 2f, boxT + 190, text);

        text.setTextSize(22f);
        text.setColor(ContextCompat.getColor(getContext(), R.color.texto_inativo));
        canvas.drawText(getContext().getString(R.string.reiniciar_jogo), w / 2f, boxB - 20, text);
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
