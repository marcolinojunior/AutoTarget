/*
 * ============================================================================
 * Arquivo: GameSurfaceView.java
 * Pacote:  com.autotarget.engine
 * ============================================================================
 *
 * DESCRIÇÃO TÉCNICA:
 *   SurfaceView customizada que funciona como o canvas de renderização do jogo
 *   AutoTarget. Utiliza uma thread de renderização interna (RenderThread) que
 *   desenha o campo de jogo a ~30 FPS, incluindo alvos (círculos), canhões
 *   (triângulos), projéteis, HUD (placar, energia, tempo) e a tela de fim.
 *
 * TÓPICOS DA RUBRICA ATENDIDOS NESTE ARQUIVO:
 *
 *   ► Tela principal e interação (6.1.2/6.1.3):
 *     - Canvas onde alvos (círculos verdes/laranjas) se movem aleatoriamente.
 *     - Canhões representados por triângulos (cores por lado: ciano=ESQ, rosa=DIR).
 *     - Projéteis renderizados como círculos dourados.
 *     - Linha divisória central tracejada separa os dois campos.
 *     - HUD em tempo real: barra de tempo, barras de energia por lado,
 *       pontuação por lado, labels "ESQUERDO"/"DIREITO".
 *     - Tela de fim de jogo com overlay escuro, box de resultado e vencedor.
 *     - Feedback visual: estado/placar/logs visuais e organização.
 *
 *   ► Classes/threads (6.1.4):
 *     - RenderThread (classe interna) — thread dedicada de renderização que
 *       faz lock do Canvas via SurfaceHolder, invoca desenhar() e controla
 *       o framerate (sleep para atingir TARGET_FPS = 30).
 *     - APENAS RENDERIZA — não processa colisões (responsabilidade do PhysicsTimer).
 *     - Implementa SurfaceHolder.Callback para gerenciar ciclo de vida da surface.
 *
 *   ► Sincronização (6.1.5):
 *     - synchronized(holder) dentro da RenderThread para acesso seguro ao Canvas.
 *
 *   ► Herança e polimorfismo (6.1.8):
 *     - Renderização polimórfica de alvos via alvo.getCorId() — cada subclasse
 *       define sua cor sem necessidade de instanceof na View.
 *     - Canhões diferenciados por Lado (ESQUERDO/DIREITO) com cores distintas.
 *
 * FLUXO DE RENDERIZAÇÃO (a cada frame):
 *   1. Preenche fundo escuro (#1A1A2E) + grid decorativo
 *   2. Desenha linha divisória central tracejada
 *   3. Itera alvos: glow + círculo (cor polimórfica via getCorId())
 *   4. Itera canhões: triângulo rotacionado pelo ângulo + projéteis
 *   5. Desenha HUD (tempo, energia, pontuação)
 *   6. Se estado == ENCERRADO → overlay + box de fim de jogo
 *
 * ESCALONAMENTO RMA (Rate Monotonic Analysis):
 *   Tarefa: T4 — RenderThread.run (Canvas)
 *   Período P₄ = 33ms (~30FPS), Execução C₄ = 5-12ms, Deadline D₄ = 33ms
 *   Prioridade RM: 3 (Média)
 *
 * ============================================================================
 */
package com.autotarget.engine;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

import com.autotarget.exception.JogoException;
import com.autotarget.model.Alvo;
import com.autotarget.model.Canhao;
import com.autotarget.model.Lado;
import com.autotarget.model.Projetil;
import com.autotarget.util.ReconciliationVisualizer;
import com.autotarget.util.SensorStatisticsTracker;

/**
 * SurfaceView customizada para renderizar o campo de jogo AutoTarget.
 * <p>
 * Desenha duas áreas (esquerda/direita) separadas por uma linha divisória,
 * alvos, canhões, projéteis e HUD com energia/tempo por lado a ~30 FPS.
 */
public class GameSurfaceView extends SurfaceView implements SurfaceHolder.Callback {

    private RenderThread renderThread;
    private Jogo jogo;

    private final Object dragLock = new Object();
    /** Canhão atualmente sendo arrastado pelo jogador (Drag-and-Drop). */
    private Canhao draggedCanhao;
    /** Flag: true enquanto o dedo está arrastando um canhão. */
    private boolean isDragging;
    /** Raio de detecção de toque sobre um canhão existente. */
    private static final float RAIO_TOQUE = 50f;
    /** Altura da zona de lixeira na base da tela. */
    private static final float ALTURA_LIXEIRA = 100f;
    /** Paint para zona de lixeira. */
    private final Paint paintLixeira = new Paint();
    /** Paint para labels dos campos. */
    private final Paint paintLabel = new Paint();
    /** Paint para texto da lixeira durante drag. */
    private final Paint paintLixeiraTexto = new Paint();
    /** Paint para texto alinhado à direita no HUD. */
    private final Paint paintTextoDireita = new Paint();
    /** Paints reutilizáveis para a tela de fim de jogo. */
    private final Paint paintOverlayFim = new Paint();
    private final Paint paintBoxFim = new Paint();
    private final Paint paintBordaFim = new Paint();
    private final Paint paintTextoFim = new Paint();

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
    
    private final RectF hudRect = new RectF();
    private final RectF hudRectAux = new RectF();
    private final RectF fimBoxRect = new RectF();
    private final Path pathCanhao = new Path();

    private static final int TARGET_FPS = 30;
    private final StringBuilder sbHUD = new StringBuilder(64);

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

        // Lixeira (zona vermelha semi-transparente)
        paintLixeira.setColor(Color.parseColor("#66E94560"));
        paintLixeira.setStyle(Paint.Style.FILL);

        // Labels dos campos
        paintLabel.setColor(Color.parseColor("#55FFFFFF"));
        paintLabel.setTextSize(18f);
        paintLabel.setAntiAlias(true);
        paintLabel.setTextAlign(Paint.Align.CENTER);

        paintLixeiraTexto.setColor(Color.parseColor("#CCFFFFFF"));
        paintLixeiraTexto.setTextSize(22f);
        paintLixeiraTexto.setAntiAlias(true);
        paintLixeiraTexto.setTextAlign(Paint.Align.CENTER);

        paintTextoDireita.setAntiAlias(true);
        paintTextoDireita.setTextAlign(Paint.Align.RIGHT);

        paintOverlayFim.setColor(Color.parseColor("#CC1A1A2E"));
        paintOverlayFim.setStyle(Paint.Style.FILL);

        paintBoxFim.setColor(Color.parseColor("#16213E"));
        paintBoxFim.setStyle(Paint.Style.FILL);

        paintBordaFim.setColor(Color.parseColor("#E94560"));
        paintBordaFim.setStyle(Paint.Style.STROKE);
        paintBordaFim.setStrokeWidth(3f);

        paintTextoFim.setColor(Color.WHITE);
        paintTextoFim.setAntiAlias(true);
        paintTextoFim.setTextAlign(Paint.Align.CENTER);

        GameRenderer.registerDefaults();
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
            try {
                renderThread.join(5000); // aguarda até 5s
            } catch (InterruptedException e) {
                Log.w("GameSurface", "RenderThread não terminou em tempo", e);
                Thread.currentThread().interrupt();
            } finally {
                renderThread = null;
            }
        }
    }

    public void setJogo(Jogo jogo) { this.jogo = jogo; }

    // ── Drag-and-Drop (Fase 4) ─────────────────────────────────────

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (jogo == null || jogo.getEstado() != Jogo.Estado.RODANDO) {
            return super.onTouchEvent(event);
        }

        float touchX = event.getX();
        float touchY = event.getY();
        float meioX = getWidth() / 2f;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // Só permite interação no lado esquerdo (jogador)
                if (touchX >= meioX) break;

                // Verificar se tocou num canhão existente (proteção por lock)
                synchronized (dragLock) {
                    draggedCanhao = null;
                    synchronized(jogo.getCanhoesLock()) {
                        java.util.List<Canhao> canhoesEsq = jogo.getCanhoesEsquerdo();
                        for (int i = 0; i < canhoesEsq.size(); i++) {
                            Canhao c = canhoesEsq.get(i);
                            if (!c.isAtivo()) continue;
                            float dist = Alvo.calcularDistancia(c.getX(), c.getY(), touchX, touchY);
                            if (dist < RAIO_TOQUE) {
                                draggedCanhao = c;
                                break;
                            }
                        }
                    }
                }

                // Se não tocou em nenhum canhão, criar um novo
                if (draggedCanhao == null) {
                    try {
                        jogo.adicionarCanhao(touchX, touchY, Lado.ESQUERDO);
                        // Pegar o recém-criado
                        synchronized(jogo.getCanhoesLock()) {
                            java.util.List<Canhao> lista = jogo.getCanhoesEsquerdo();
                            if (!lista.isEmpty()) {
                                draggedCanhao = lista.get(lista.size() - 1);
                            }
                        }
                    } catch (JogoException e) {
                        Log.w("GameSurface", "Drag: " + e.getMessage());
                        post(() -> Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_SHORT).show());
                    }
                }

                synchronized (dragLock) { isDragging = (draggedCanhao != null); }
                break;

            case MotionEvent.ACTION_MOVE:
                synchronized (dragLock) {
                    if (isDragging && draggedCanhao != null && draggedCanhao.isAtivo()) {
                        // Limitar ao lado esquerdo
                        float clampX = Math.min(touchX, meioX - 30);
                        float clampY = Math.max(90, Math.min(touchY, getHeight() - 20));
                        draggedCanhao.setPosicao(clampX, clampY);
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                synchronized (dragLock) {
                    if (isDragging && draggedCanhao != null) {
                        // Se soltar na zona da lixeira (base da tela), remover
                        if (touchY > getHeight() - ALTURA_LIXEIRA) {
                            jogo.removerCanhao(draggedCanhao);
                            final String msg = "Canhão removido!";
                            post(() -> Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show());
                        }
                    }
                    draggedCanhao = null;
                    isDragging = false;
                }
                break;
        }
        return true;
    }

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
        canvas.drawText("JOGADOR", meioX / 2f, h - 10, paintLabel);
        canvas.drawText("IA \uD83E\uDD16", meioX + meioX / 2f, h - 10, paintLabel);

        // ── Zona de Lixeira (visível durante arrastar) ──
        boolean draggingSnapshot;
        synchronized (dragLock) { draggingSnapshot = isDragging; }
        if (draggingSnapshot) {
            canvas.drawRect(0, h - ALTURA_LIXEIRA, meioX, h, paintLixeira);
            canvas.drawText("\uD83D\uDDD1 SOLTE AQUI PARA REMOVER",
                meioX / 2f, h - ALTURA_LIXEIRA / 2f + 8, paintLixeiraTexto);
        }

        // ── Alvos (renderização polimórfica via getCorId()) ──
        for (Alvo alvo : jogo.getAlvos()) {
            if (!alvo.isAtivo()) continue;
            int corAlvo = alvo.getCorId();
            Paint paint = GameRenderer.paintForColor(corAlvo);
            canvas.drawCircle(alvo.getX(), alvo.getY(), alvo.getRaio() + 4, GameRenderer.glowForColor(corAlvo));
            canvas.drawCircle(alvo.getX(), alvo.getY(), alvo.getRaio(), paint);
        }

        // ── Canhões (cor diferente por lado) ──
        // jogo.getCanhoes() já retorna uma cópia de forma thread-safe devido ao synchronized interno.
        java.util.List<Canhao> canhoes = jogo.getCanhoes();
        for (int i = 0; i < canhoes.size(); i++) {
            Canhao canhao = canhoes.get(i);
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
        
        // Painel de status de reconciliação
        desenharPainelReconciliacao(canvas);

        // REMOVIDA A CHAMADA jogo.verificarColisoes() DESTE MÉTODO DE DESENHO.
        // A verificação de colisões agora é responsabilidade exclusiva do
        // PhysicsTimer (a cada 16ms) no Jogo.java, separando completamente
        // lógica de negócio de renderização.

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

        // Fundo HUD expandido
        canvas.drawRect(0, 0, w, 110, paintHudBg);

        // ── Tempo restante (centro) ──
        int tempo = jogo.getTempoRestante();
        String tempoStr = String.format("%02d:%02d", tempo / 60, tempo % 60);
        paintTextoGrande.setColor(tempo <= 10 ? Color.parseColor("#E94560") : Color.WHITE);
        canvas.drawText(tempoStr, meioX, 32f, paintTextoGrande);

        // ── Barra de tempo (toda a largura) ──
        float tempoRatio = (float) tempo / Jogo.DURACAO_PARTIDA_SEGUNDOS;
        hudRect.set(pad, 40, w - pad, 46);
        canvas.drawRoundRect(hudRect, 3, 3, paintEnergiaFundo);
        if (tempoRatio > 0) {
            hudRectAux.set(pad, 40, pad + (w - 2 * pad) * tempoRatio, 46);
            canvas.drawRoundRect(hudRectAux, 3, 3, paintTempoBarra);
        }

        // ── Energia esquerda ──
        float eEsq = jogo.getEnergiaEsquerdo() / Jogo.getEnergiaMaxima();
        hudRect.set(pad, 52, meioX - 6, 58);
        canvas.drawRoundRect(hudRect, 3, 3, paintEnergiaFundo);
        if (eEsq > 0) {
            hudRectAux.set(pad, 52, pad + (meioX - pad - 6) * eEsq, 58);
            canvas.drawRoundRect(hudRectAux, 3, 3, paintEnergiaBarraEsq);
        }

        // ── Energia direita ──
        float eDir = jogo.getEnergiaDireito() / Jogo.getEnergiaMaxima();
        hudRect.set(meioX + 6, 52, w - pad, 58);
        canvas.drawRoundRect(hudRect, 3, 3, paintEnergiaFundo);
        if (eDir > 0) {
            hudRectAux.set(meioX + 6, 52, meioX + 6 + (w - pad - meioX - 6) * eDir, 58);
            canvas.drawRoundRect(hudRectAux, 3, 3, paintEnergiaBarraDir);
        }

        // ── Linha de Dashboard: Canhões + Penalidades ──
        paintTexto.setTextSize(18f);
        paintTexto.setColor(Color.parseColor("#00B4D8"));
        
        int nCanhoesEsq = 0;
        int nCanhoeDir = 0;
        synchronized(jogo.getCanhoesLock()) {
            nCanhoesEsq = jogo.getCanhoesEsquerdo().size();
            nCanhoeDir = jogo.getCanhoesDireito().size();
        }
        float fatorEsq = 1.0f + Math.max(0, nCanhoesEsq - Jogo.getLimiarPenalidade()) * 0.2f;
        float fatorDir = 1.0f + Math.max(0, nCanhoeDir - Jogo.getLimiarPenalidade()) * 0.2f;

        sbHUD.setLength(0);
        sbHUD.append("⚡").append((int) jogo.getEnergiaEsquerdo())
            .append(" | Gun:").append(nCanhoesEsq)
            .append(" | Pena:").append(String.format(java.util.Locale.getDefault(), "%.1fx", fatorEsq));
        canvas.drawText(sbHUD.toString(), pad, 75, paintTexto);

        paintTexto.setColor(Color.parseColor("#E94560"));
        paintTextoDireita.setColor(paintTexto.getColor());
        paintTextoDireita.setTextSize(paintTexto.getTextSize());
        sbHUD.setLength(0);
        sbHUD.append("Pena:").append(String.format(java.util.Locale.getDefault(), "%.1fx", fatorDir))
            .append(" | Gun:").append(nCanhoeDir)
            .append(" | ⚡").append((int) jogo.getEnergiaDireito());
        canvas.drawText(sbHUD.toString(), w - pad, 75, paintTextoDireita);

        // ── Pontuações (linha 2 do dashboard) ──
        paintTexto.setTextSize(20f);
        paintTexto.setColor(Color.parseColor("#00B4D8"));
        canvas.drawText("Pts: " + jogo.getPontuacaoEsquerdo(),
                pad, 100, paintTexto);

        paintTexto.setColor(Color.parseColor("#E94560"));
        paintTextoDireita.setColor(paintTexto.getColor());
        paintTextoDireita.setTextSize(paintTexto.getTextSize());
        canvas.drawText("Pts: " + jogo.getPontuacaoDireito(),
                w - pad, 100, paintTextoDireita);
        paintTexto.setTextSize(24f);
    }

    private void desenharTelaFim(Canvas canvas) {
        int w = canvas.getWidth();
        int h = canvas.getHeight();

        // Overlay
        canvas.drawRect(0, 0, w, h, paintOverlayFim);

        // Box
        float boxL = w * 0.08f, boxR = w * 0.92f;
        float boxT = h * 0.25f, boxB = h * 0.65f;

        fimBoxRect.set(boxL, boxT, boxR, boxB);
        canvas.drawRoundRect(fimBoxRect, 16, 16, paintBoxFim);
        canvas.drawRoundRect(fimBoxRect, 16, 16, paintBordaFim);

        paintTextoFim.setTextSize(42f);
        paintTextoFim.setColor(Color.WHITE);
        canvas.drawText("FIM DE JOGO", w / 2f, boxT + 50, paintTextoFim);

        // Scores
        int pE = jogo.getPontuacaoEsquerdo();
        int pD = jogo.getPontuacaoDireito();

        paintTextoFim.setTextSize(30f);
        paintTextoFim.setColor(Color.parseColor("#00B4D8"));
        canvas.drawText("Esquerdo: " + pE, w / 2f, boxT + 100, paintTextoFim);
        paintTextoFim.setColor(Color.parseColor("#E94560"));
        canvas.drawText("Direito: " + pD, w / 2f, boxT + 140, paintTextoFim);

        // Vencedor
        paintTextoFim.setTextSize(34f);
        paintTextoFim.setColor(Color.parseColor("#FFD700"));
        String vencedor;
        if (pE > pD) vencedor = "🏆 Esquerdo Vence!";
        else if (pD > pE) vencedor = "🏆 Direito Vence!";
        else vencedor = "🤝 Empate!";
        canvas.drawText(vencedor, w / 2f, boxT + 190, paintTextoFim);

        paintTextoFim.setTextSize(22f);
        paintTextoFim.setColor(Color.parseColor("#AAAAAA"));
        canvas.drawText("Toque em Iniciar para jogar novamente", w / 2f, boxB - 20, paintTextoFim);
    }

    private void desenharCanhao(Canvas canvas, Canhao canhao, Paint paint) {
        float tamanho = 25f;
        float angRad = (float) Math.toRadians(canhao.getAngulo());

        pathCanhao.reset();
        pathCanhao.moveTo(
                canhao.getX() + (float) Math.cos(angRad) * tamanho * 1.5f,
                canhao.getY() + (float) Math.sin(angRad) * tamanho * 1.5f);
        pathCanhao.lineTo(
                canhao.getX() + (float) Math.cos(angRad + 2.4f) * tamanho,
                canhao.getY() + (float) Math.sin(angRad + 2.4f) * tamanho);
        pathCanhao.lineTo(
                canhao.getX() + (float) Math.cos(angRad - 2.4f) * tamanho,
                canhao.getY() + (float) Math.sin(angRad - 2.4f) * tamanho);
        pathCanhao.close();
        canvas.drawPath(pathCanhao, paint);
    }

    /**
     * Desenha painel com status de reconciliação e sensores.
     * Exibido na parte inferior da tela em tempo real.
     */
    private void desenharPainelReconciliacao(Canvas canvas) {
        if (jogo == null || jogo.getEstado() != Jogo.Estado.RODANDO) return;

        int h = canvas.getHeight();
        float y = h - 35f;
        float pad = 12f;
        
        // Status de reconciliação
        String statusReconc = ReconciliationVisualizer.obterEstatisticasAgregadas();
        paintTexto.setTextSize(14f);
        paintTexto.setColor(Color.parseColor("#FFB300"));
        canvas.drawText("Reconc: " + statusReconc, pad, y, paintTexto);
        
        // Status de sensores
        String statusSensores = "Ruído médio: " + String.format("%.1f%%", 
                SensorStatisticsTracker.calcularRuidoMedio());
        paintTexto.setColor(Color.parseColor("#00D4FF"));
        canvas.drawText(statusSensores, pad, y + 18, paintTexto);
        
        paintTexto.setTextSize(24f);
    }

    private void desenharGrid(Canvas canvas) {
        int spacing = 60;
        for (int x = 0; x < canvas.getWidth(); x += spacing)
            canvas.drawLine(x, 0, x, canvas.getHeight(), paintGrid);
        for (int y = 0; y < canvas.getHeight(); y += spacing)
            canvas.drawLine(0, y, canvas.getWidth(), y, paintGrid);
    }

    private void registrarPaintAlvo(int color) {
        // Moved to GameRenderer
    }

    private Paint paintForColor(int color) {
        // Moved to GameRenderer
        return GameRenderer.paintForColor(color);
    }

    private Paint glowForColor(int color) {
        // Moved to GameRenderer
        return GameRenderer.glowForColor(color);
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
            // T4 (Renderização): High Priority (UI), deve usar BIG cores para 30 FPS estável
            com.autotarget.util.ThreadAffinityHelper.trySetAffinityPreferProcessApi(
                    android.os.Process.myTid(), com.autotarget.util.ThreadAffinityHelper.BIG_CORES);

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
                    Log.e("RenderThread", "Erro no render loop", e);
                } finally {
                    if (canvas != null) {
                        try { holder.unlockCanvasAndPost(canvas); } catch (Exception e) {
                            Log.e("RenderThread", "Erro ao postar canvas", e);
                        }
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
