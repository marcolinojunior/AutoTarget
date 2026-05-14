/*
 * ============================================================================
 * Arquivo: SensorThread.java
 * Pacote:  com.autotarget.service
 * ============================================================================
 *
 * ESCALONAMENTO RMA (Rate Monotonic Analysis):
 *   Tarefa: T7 — SensorThread.coletar
 *   Período P₇ = 1000ms (1s), Execução C₇ = 5-10ms, Deadline D₇ = 1000ms
 *   Prioridade RM: 6 (Fundo)
 *
 * DESCRIÇÃO TÉCNICA:
 *   Thread dedicada para coleta simulada de dados de sensores. Opera a cada
 *   1s (para acumular ≥10 leituras em 10s conforme §6.2.2-c), coleta posições
 *   e velocidades com ruído gaussiano PROPORCIONAL (5% do valor real),
 *   e alimenta buffer histórico de distâncias para reconciliação.
 *
 * RUÍDO (AV2 §6.2.2-c):
 *   Desvio padrão proporcional ao valor real (5%):
 *   posX_ruidosa = posX_real × (1 + N(0, 0.05))
 *
 * BUFFER (AV2 §6.2.2-c):
 *   ≥10 leituras por alvo. Com intervalo 1s e janela 10s → 10 amostras.
 *
 * LOCK ORDERING: collisionLock → sensorLock (nunca na ordem inversa)
 *
 * ============================================================================
 */
package com.autotarget.service;

import android.util.Log;
import com.autotarget.model.Alvo;
import com.autotarget.model.Canhao;
import com.autotarget.model.Lado;
import com.autotarget.util.RMAAnalysis;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Thread dedicada para coleta simulada de dados de sensores.
 * Produz leituras ruidosas (ruído 5% proporcional) e alimenta
 * buffer histórico de distâncias para reconciliação EJML.
 */
public class SensorThread extends Thread {

    private static final String TAG = "SensorThread";

    /** Referência ao motor do jogo para obter listas dinâmicas. */
    private final com.autotarget.engine.Jogo jogo;

    /** Dados por lado para atender coleta/otimização independentes por território. */
    private final EnumMap<Lado, SideSensorData> dadosPorLado;

    /** Dados agregados da última coleta (compatibilidade com API existente). */
    private volatile float[] leiturasPosX;
    private volatile float[] leiturasPosY;
    private volatile float[] leiturasVelocidade;
    private volatile float[] leiturasVelocidadeX;
    private volatile float[] leiturasVelocidadeY;
    private volatile float[] verdadeiroPosX;
    private volatile float[] verdadeiroPosY;
    private volatile int quantidadeAlvosColetados;
    private static final int TAMANHO_HISTORICO = 10;

    /** Flag de controle. */
    private volatile boolean ativo;

    /** Lock para sincronizar com thread de reconciliação. */
    private final Object sensorLock;

    /** Lock para proteger iteração da lista de alvos. */
    private final Object collisionLock;

    /** Intervalo entre coletas (ms) — 1s para ≥10 leituras em 10s. */
    private static final int INTERVALO_COLETA = 1000;

    /** Proporção de ruído (5% do valor real). */
    private static final double PROPORCAO_RUIDO = 0.05;

    /** Ruído simulado nos sensores. */
    private final Random ruido = new Random();

    private static class SideSensorData {
        float[] leiturasPosX = new float[0];
        float[] leiturasPosY = new float[0];
        float[] leiturasVelocidade = new float[0];
        float[] leiturasVelocidadeX = new float[0];
        float[] leiturasVelocidadeY = new float[0];
        float[] verdadeiroPosX = new float[0];
        float[] verdadeiroPosY = new float[0];
        int quantidadeAlvosColetados = 0;
        final LinkedList<float[][]> historicoDistancias = new LinkedList<>();
    }

    /**
     * Cria a thread de sensores.
     *
     * @param alvos         lista compartilhada de alvos
     * @param canhoes       lista de canhões (para distâncias)
     * @param sensorLock    lock compartilhado com thread de reconciliação
     * @param collisionLock lock global para proteger iteração de alvos
     */
    public SensorThread(com.autotarget.engine.Jogo jogo,
                        Object sensorLock, Object collisionLock) {
        super("SensorThread");
        this.jogo = jogo;
        this.sensorLock = sensorLock;
        this.collisionLock = collisionLock;
        this.ativo = true;
        this.leiturasPosX = new float[0];
        this.leiturasPosY = new float[0];
        this.leiturasVelocidade = new float[0];
        this.quantidadeAlvosColetados = 0;
        this.dadosPorLado = new EnumMap<>(Lado.class);
        this.dadosPorLado.put(Lado.ESQUERDO, new SideSensorData());
        this.dadosPorLado.put(Lado.DIREITO, new SideSensorData());
        setDaemon(true);
    }

    @Override
    public void run() {
        while (ativo) {
            long startNs = System.nanoTime();
            try {
                coletarDados();
                Thread.sleep(INTERVALO_COLETA);

                // Instrumentação RMA
                long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
                RMAAnalysis.checkDeadline("T7-Sensor", elapsedMs, INTERVALO_COLETA);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ativo = false;
            }
        }
    }

    /**
     * Coleta dados simulados com ruído proporcional (5% do valor real).
     * Lock ordering: collisionLock (externo) → sensorLock (interno).
     */
    private void coletarDados() {
        final EnumMap<Lado, SideSnapshot> snapshotsPorLado = new EnumMap<>(Lado.class);
        snapshotsPorLado.put(Lado.ESQUERDO, new SideSnapshot());
        snapshotsPorLado.put(Lado.DIREITO, new SideSnapshot());

        synchronized (collisionLock) {
            List<Alvo> alvosAtuais = jogo.getAllAlvos();
            List<Canhao> canhoesEsq = new ArrayList<>();
            List<Canhao> canhoesDir = new ArrayList<>();
            for (Canhao c : jogo.getCanhoesEsquerdo()) {
                if (c.isAtivo()) canhoesEsq.add(c);
            }
            for (Canhao c : jogo.getCanhoesDireito()) {
                if (c.isAtivo()) canhoesDir.add(c);
            }

            int larguraTela = Math.max(jogo.getLarguraTela(), 1);
            for (Alvo alvo : alvosAtuais) {
                if (!alvo.isAtivo()) continue;
                Lado lado = Lado.determinar(alvo.getX(), larguraTela);
                snapshotsPorLado.get(lado).alvosAtivos.add(alvo);
            }

            snapshotsPorLado.get(Lado.ESQUERDO).canhoesAtivos = canhoesEsq;
            snapshotsPorLado.get(Lado.DIREITO).canhoesAtivos = canhoesDir;
            preencherLeiturasLado(snapshotsPorLado.get(Lado.ESQUERDO));
            preencherLeiturasLado(snapshotsPorLado.get(Lado.DIREITO));
        }

        synchronized (sensorLock) {
            for (Map.Entry<Lado, SideSnapshot> entry : snapshotsPorLado.entrySet()) {
                publicarSnapshotLado(entry.getKey(), entry.getValue());
            }
            publicarAgregadoGlobal();
            sensorLock.notifyAll();
        }
    }

    private static class SideSnapshot {
        final List<Alvo> alvosAtivos = new ArrayList<>();
        List<Canhao> canhoesAtivos = new ArrayList<>();
        float[] leiturasPosX = new float[0];
        float[] leiturasPosY = new float[0];
        float[] leiturasVelocidade = new float[0];
        float[] leiturasVelocidadeX = new float[0];
        float[] leiturasVelocidadeY = new float[0];
        float[] verdadeiroPosX = new float[0];
        float[] verdadeiroPosY = new float[0];
        float[][] snapshotDistancias = null;
    }

    private void preencherLeiturasLado(SideSnapshot snap) {
        int count = snap.alvosAtivos.size();
        snap.leiturasPosX = new float[count];
        snap.leiturasPosY = new float[count];
        snap.leiturasVelocidade = new float[count];
        snap.leiturasVelocidadeX = new float[count];
        snap.leiturasVelocidadeY = new float[count];
        snap.verdadeiroPosX = new float[count];
        snap.verdadeiroPosY = new float[count];

        for (int i = 0; i < count; i++) {
            Alvo alvo = snap.alvosAtivos.get(i);
            float realX = alvo.getX();
            float realY = alvo.getY();
            float realV = alvo.getVelocidade();
            float realVx = alvo.getDirecaoX() * realV;
            float realVy = alvo.getDirecaoY() * realV;

            snap.leiturasPosX[i] = aplicarRuidoProporcional(realX);
            snap.leiturasPosY[i] = aplicarRuidoProporcional(realY);
            snap.leiturasVelocidade[i] = aplicarRuidoProporcional(realV);
            snap.leiturasVelocidadeX[i] = aplicarRuidoProporcional(realVx);
            snap.leiturasVelocidadeY[i] = aplicarRuidoProporcional(realVy);
            snap.verdadeiroPosX[i] = realX;
            snap.verdadeiroPosY[i] = realY;
        }

        int numCanhoes = snap.canhoesAtivos.size();
        if (count == 0 || numCanhoes == 0) {
            snap.snapshotDistancias = null;
            return;
        }
        snap.snapshotDistancias = new float[count][numCanhoes];
        for (int ai = 0; ai < count; ai++) {
            float ax = snap.verdadeiroPosX[ai];
            float ay = snap.verdadeiroPosY[ai];
            for (int cj = 0; cj < numCanhoes; cj++) {
                Canhao canhao = snap.canhoesAtivos.get(cj);
                float dx = ax - canhao.getX();
                float dy = ay - canhao.getY();
                float distReal = (float) Math.sqrt(dx * dx + dy * dy);
                snap.snapshotDistancias[ai][cj] = aplicarRuidoProporcional(distReal);
            }
        }
    }

    private float aplicarRuidoProporcional(float valorReal) {
        float escala = Math.max(Math.abs(valorReal), 1f);
        float ruidoGaussiano = (float) (ruido.nextGaussian() * PROPORCAO_RUIDO * escala);
        return valorReal + ruidoGaussiano;
    }

    private void publicarSnapshotLado(Lado lado, SideSnapshot snap) {
        SideSensorData dado = dadosPorLado.get(lado);
        if (dado == null) return;

        dado.leiturasPosX = snap.leiturasPosX;
        dado.leiturasPosY = snap.leiturasPosY;
        dado.leiturasVelocidade = snap.leiturasVelocidade;
        dado.leiturasVelocidadeX = snap.leiturasVelocidadeX;
        dado.leiturasVelocidadeY = snap.leiturasVelocidadeY;
        dado.verdadeiroPosX = snap.verdadeiroPosX;
        dado.verdadeiroPosY = snap.verdadeiroPosY;
        dado.quantidadeAlvosColetados = snap.alvosAtivos.size();

        if (snap.snapshotDistancias != null) {
            dado.historicoDistancias.addLast(snap.snapshotDistancias);
            while (dado.historicoDistancias.size() > TAMANHO_HISTORICO) {
                dado.historicoDistancias.removeFirst();
            }
        }
    }

    private void publicarAgregadoGlobal() {
        SideSensorData esq = dadosPorLado.get(Lado.ESQUERDO);
        SideSensorData dir = dadosPorLado.get(Lado.DIREITO);
        if (esq == null || dir == null) return;

        leiturasPosX = concatenar(esq.leiturasPosX, dir.leiturasPosX);
        leiturasPosY = concatenar(esq.leiturasPosY, dir.leiturasPosY);
        leiturasVelocidade = concatenar(esq.leiturasVelocidade, dir.leiturasVelocidade);
        leiturasVelocidadeX = concatenar(esq.leiturasVelocidadeX, dir.leiturasVelocidadeX);
        leiturasVelocidadeY = concatenar(esq.leiturasVelocidadeY, dir.leiturasVelocidadeY);
        verdadeiroPosX = concatenar(esq.verdadeiroPosX, dir.verdadeiroPosX);
        verdadeiroPosY = concatenar(esq.verdadeiroPosY, dir.verdadeiroPosY);
        quantidadeAlvosColetados = esq.quantidadeAlvosColetados + dir.quantidadeAlvosColetados;
    }

    private float[] concatenar(float[] a, float[] b) {
        if (a == null || a.length == 0) return b != null ? b.clone() : new float[0];
        if (b == null || b.length == 0) return a.clone();
        float[] out = new float[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    // ── Estatísticas para Reconciliação ──────────────────────────

    /**
     * Calcula a média das distâncias d̄_ij sobre o buffer histórico.
     * @return float[M][N] com médias, ou null se dados insuficientes
     */
    public float[][] getMediaDistancias() {
        float[][] dir = getMediaDistancias(Lado.DIREITO);
        if (dir != null) return dir;
        return getMediaDistancias(Lado.ESQUERDO);
    }

    public float[][] getMediaDistancias(Lado lado) {
        synchronized (sensorLock) {
            SideSensorData dado = dadosPorLado.get(lado);
            if (dado == null || dado.historicoDistancias.isEmpty()) return null;

            int M = dado.historicoDistancias.getLast().length;
            int N = dado.historicoDistancias.getLast()[0].length;
            float[][] media = new float[M][N];
            int count = 0;

            for (float[][] snapshot : dado.historicoDistancias) {
                if (snapshot.length != M || snapshot[0].length != N) continue;
                for (int i = 0; i < M; i++) {
                    for (int j = 0; j < N; j++) {
                        media[i][j] += snapshot[i][j];
                    }
                }
                count++;
            }

            if (count == 0) return null;
            for (int i = 0; i < M; i++) {
                for (int j = 0; j < N; j++) {
                    media[i][j] /= count;
                }
            }
            return media;
        }
    }

    /**
     * Calcula a variância amostral s²_ij das distâncias.
     * @return float[M][N] com variâncias, ou null se dados insuficientes
     */
    public float[][] getVarianciaDistancias() {
        float[][] dir = getVarianciaDistancias(Lado.DIREITO);
        if (dir != null) return dir;
        return getVarianciaDistancias(Lado.ESQUERDO);
    }

    public float[][] getVarianciaDistancias(Lado lado) {
        synchronized (sensorLock) {
            SideSensorData dado = dadosPorLado.get(lado);
            if (dado == null || dado.historicoDistancias.size() < 2) return null;
            float[][] media = getMediaDistancias(lado);
            if (media == null) return null;

            int M = media.length;
            int N = media[0].length;
            float[][] variancia = new float[M][N];
            int count = 0;

            for (float[][] snapshot : dado.historicoDistancias) {
                if (snapshot.length != M || snapshot[0].length != N) continue;
                for (int i = 0; i < M; i++) {
                    for (int j = 0; j < N; j++) {
                        float diff = snapshot[i][j] - media[i][j];
                        variancia[i][j] += diff * diff;
                    }
                }
                count++;
            }

            if (count <= 1) return null;
            for (int i = 0; i < M; i++) {
                for (int j = 0; j < N; j++) {
                    variancia[i][j] /= (count - 1);
                }
            }
            return variancia;
        }
    }

    /**
     * @return número de leituras no buffer histórico
     */
    public int getHistoricoCount() {
        int dir = getHistoricoCount(Lado.DIREITO);
        int esq = getHistoricoCount(Lado.ESQUERDO);
        return Math.max(dir, esq);
    }

    public int getHistoricoCount(Lado lado) {
        synchronized (sensorLock) {
            SideSensorData dado = dadosPorLado.get(lado);
            return dado == null ? 0 : dado.historicoDistancias.size();
        }
    }

    public float[] getVerdadeiroPosX() {
        float[] dir = getVerdadeiroPosX(Lado.DIREITO);
        if (dir.length > 0) return dir;
        return getVerdadeiroPosX(Lado.ESQUERDO);
    }

    public float[] getVerdadeiroPosX(Lado lado) {
        synchronized (sensorLock) {
            SideSensorData dado = dadosPorLado.get(lado);
            return dado == null ? new float[0] : dado.verdadeiroPosX.clone();
        }
    }

    public float[] getVerdadeiroPosY() {
        float[] dir = getVerdadeiroPosY(Lado.DIREITO);
        if (dir.length > 0) return dir;
        return getVerdadeiroPosY(Lado.ESQUERDO);
    }

    public float[] getVerdadeiroPosY(Lado lado) {
        synchronized (sensorLock) {
            SideSensorData dado = dadosPorLado.get(lado);
            return dado == null ? new float[0] : dado.verdadeiroPosY.clone();
        }
    }

    // ── Getters para Reconciliação ───────────────────────────────

    public float[] getLeiturasPosX() { return leiturasPosX; }
    public float[] getLeiturasPosY() { return leiturasPosY; }
    public float[] getLeiturasVelocidade() { return leiturasVelocidade; }
    public float[] getLeiturasVelocidadeX() { return leiturasVelocidadeX; }
    public float[] getLeiturasVelocidadeY() { return leiturasVelocidadeY; }
    public int getQuantidadeAlvosColetados() { return quantidadeAlvosColetados; }

    public float[] getLeiturasPosX(Lado lado) {
        synchronized (sensorLock) {
            SideSensorData dado = dadosPorLado.get(lado);
            return dado == null ? new float[0] : dado.leiturasPosX.clone();
        }
    }

    public float[] getLeiturasPosY(Lado lado) {
        synchronized (sensorLock) {
            SideSensorData dado = dadosPorLado.get(lado);
            return dado == null ? new float[0] : dado.leiturasPosY.clone();
        }
    }

    public float[] getLeiturasVelocidade(Lado lado) {
        synchronized (sensorLock) {
            SideSensorData dado = dadosPorLado.get(lado);
            return dado == null ? new float[0] : dado.leiturasVelocidade.clone();
        }
    }

    public float[] getLeiturasVelocidadeX(Lado lado) {
        synchronized (sensorLock) {
            SideSensorData dado = dadosPorLado.get(lado);
            return dado == null ? new float[0] : dado.leiturasVelocidadeX.clone();
        }
    }

    public float[] getLeiturasVelocidadeY(Lado lado) {
        synchronized (sensorLock) {
            SideSensorData dado = dadosPorLado.get(lado);
            return dado == null ? new float[0] : dado.leiturasVelocidadeY.clone();
        }
    }

    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    public boolean isAtivo() { return ativo; }
}
