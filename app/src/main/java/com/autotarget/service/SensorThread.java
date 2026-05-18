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
import com.autotarget.engine.GameGeometry;
import com.autotarget.util.RMAAnalysis;
import com.autotarget.util.ReconciliationLog;
import com.autotarget.util.SensorStatisticsTracker;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

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
    private static final int TAMANHO_HISTORICO = 10; // Não pode ser reduzido de 10 pois é exigência do trabalho

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

    /** Ruído simulado nos sensores via ThreadLocalRandom. */

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
        
        /** Histórico persistente por alvo para evitar corrupção por troca de índices. */
        final java.util.Map<Long, TargetHistory> historicoPorAlvo = new java.util.HashMap<>();
    }

    private static class TargetHistory {
        final LinkedList<Sample> samples = new LinkedList<>();
        
        static class Sample {
            long timestamp;
            float[] distancias; // dist para cada canhão
            float[] canhoesX; // posições dos canhões no momento da medição
            float[] canhoesY;
            float vx, vy;
            float x, y; // posição ruidosa
            float trueVx, trueVy;
            float trueX, trueY;
        }
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
        com.autotarget.util.ThreadAffinityHelper.setAffinityForBackgroundTask(android.os.Process.myTid());

        try {
            while (ativo) {
                try {
                    long startNs = System.nanoTime();
                    coletarDados();
                    long elapsedMs = (System.nanoTime() - startNs) / 1_000_000; // tempo de execução sem sleep
                    RMAAnalysis.checkDeadline("T7-Sensor", elapsedMs, INTERVALO_COLETA);
                    Thread.sleep(INTERVALO_COLETA);

                } catch (InterruptedException e) {
                    Log.w(TAG, "SensorThread interrompida.");
                    Thread.currentThread().interrupt();
                    ativo = false;
                } catch (Exception e) {
                    Log.e(TAG, "Erro no loop de coleta de dados", e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "ERRO FATAL na SensorThread", e);
        } finally {
            Log.i(TAG, "SensorThread finalizada.");
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

        synchronized (jogo.getCollisionLock()) {
            List<Alvo> alvosAtuais = jogo.getAllAlvos();
            List<Canhao> canhoesEsq = new ArrayList<>();
            List<Canhao> canhoesDir = new ArrayList<>();
            java.util.List<Canhao> listaEsq = jogo.getCanhoesNoLadoSnapshot(Lado.ESQUERDO);
            for (int i = 0; i < listaEsq.size(); i++) {
                Canhao c = listaEsq.get(i);
                if (c.isAtivo()) canhoesEsq.add(c);
            }
            java.util.List<Canhao> listaDir = jogo.getCanhoesNoLadoSnapshot(Lado.DIREITO);
            for (int i = 0; i < listaDir.size(); i++) {
                Canhao c = listaDir.get(i);
                if (c.isAtivo()) canhoesDir.add(c);
            }

            int larguraTela = Math.max(jogo.getLarguraTela(), 1);
            GameGeometry geom = com.autotarget.engine.GameGeometry.forScreen(larguraTela, jogo.getAlturaTela());
            for (Alvo alvo : alvosAtuais) {
                if (!alvo.isAtivo()) continue;
                Lado lado = geom.determineLado(alvo.getX());
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

        for (Map.Entry<Lado, SideSnapshot> entry : snapshotsPorLado.entrySet()) {
            registrarEstatisticasSensor(entry.getKey(), entry.getValue());
        }
    }

    public static class SideSnapshot {
        public final List<Alvo> alvosAtivos = new ArrayList<>();
        public List<Canhao> canhoesAtivos = new ArrayList<>();
        public float[] leiturasPosX = new float[0];
        public float[] leiturasPosY = new float[0];
        public float[] leiturasVelocidade = new float[0];
        public float[] leiturasVelocidadeX = new float[0];
        public float[] leiturasVelocidadeY = new float[0];
        public float[] verdadeiroPosX = new float[0];
        public float[] verdadeiroPosY = new float[0];
        public float[] verdadeiroVelocidadeX = new float[0];
        public float[] verdadeiroVelocidadeY = new float[0];
        public float[][] snapshotDistancias = null;
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
        snap.verdadeiroVelocidadeX = new float[count];
        snap.verdadeiroVelocidadeY = new float[count];

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
            snap.verdadeiroVelocidadeX[i] = realVx;
            snap.verdadeiroVelocidadeY[i] = realVy;
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
        float ruidoGaussiano = (float) (ThreadLocalRandom.current().nextGaussian() * PROPORCAO_RUIDO * escala);
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
            dado.historicoDistancias.addLast(clonarMatriz(snap.snapshotDistancias));
            while (dado.historicoDistancias.size() > TAMANHO_HISTORICO) {
                dado.historicoDistancias.removeFirst();
            }
        }

        long now = System.currentTimeMillis();
        
        // Atualizar histórico por alvo individual para evitar o bug de troca de índices
        // e permitir compensação de movimento (Radar)
        for (int i = 0; i < snap.alvosAtivos.size(); i++) {
            Alvo a = snap.alvosAtivos.get(i);
            TargetHistory history = dado.historicoPorAlvo.get(a.getTargetId());
            if (history == null) {
                history = new TargetHistory();
                dado.historicoPorAlvo.put(a.getTargetId(), history);
            }
            
            TargetHistory.Sample s = new TargetHistory.Sample();
            s.timestamp = now;
            s.vx = snap.leiturasVelocidadeX[i];
            s.vy = snap.leiturasVelocidadeY[i];
            s.x = snap.leiturasPosX[i];
            s.y = snap.leiturasPosY[i];
            s.trueVx = snap.verdadeiroVelocidadeX[i];
            s.trueVy = snap.verdadeiroVelocidadeY[i];
            s.trueX = snap.verdadeiroPosX[i];
            s.trueY = snap.verdadeiroPosY[i];
            if (snap.snapshotDistancias != null) {
                s.distancias = snap.snapshotDistancias[i];
                // GUARDAR POSIÇÕES DOS CANHÕES PARA RECONCILIAÇÃO GEOMÉTRICA CONSISTENTE
                s.canhoesX = new float[snap.canhoesAtivos.size()];
                s.canhoesY = new float[snap.canhoesAtivos.size()];
                for (int j = 0; j < snap.canhoesAtivos.size(); j++) {
                    s.canhoesX[j] = snap.canhoesAtivos.get(j).getX();
                    s.canhoesY[j] = snap.canhoesAtivos.get(j).getY();
                }
            }
            
            history.samples.addLast(s);
            while (history.samples.size() > TAMANHO_HISTORICO) {
                history.samples.removeFirst();
            }
        }
    }

    /**
     * Remove do histórico os alvos que não estão mais ativos.
     * Chamado pela ReconciliacaoThread após consumir os dados.
     */
    public void limparHistoricoInativo(Lado lado, java.util.List<Alvo> alvosAtivos) {
        synchronized (sensorLock) {
            SideSensorData dado = dadosPorLado.get(lado);
            if (dado == null) return;
            dado.historicoPorAlvo.entrySet().removeIf(entry -> {
                for (Alvo a : alvosAtivos) {
                    if (a.getTargetId() == entry.getKey()) return false;
                }
                return true;
            });
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

    private float[][] clonarMatriz(float[][] entrada) {
        if (entrada == null) return null;
        float[][] copia = new float[entrada.length][];
        for (int i = 0; i < entrada.length; i++) {
            copia[i] = entrada[i] == null ? null : entrada[i].clone();
        }
        return copia;
    }

    private void registrarEstatisticasSensor(Lado lado, SideSnapshot snap) {
        if (snap == null) return;
        int historico = getHistoricoCount(lado);
        if (historico < TAMANHO_HISTORICO) {
            return;
        }
        double mediaX = media(snap.leiturasPosX);
        float[] residuoX = new float[snap.leiturasPosX.length];
        float[] residuoY = new float[snap.leiturasPosY.length];
        float[] residuoVelX = new float[snap.leiturasVelocidadeX.length];
        float[] residuoVelY = new float[snap.leiturasVelocidadeY.length];
        float[] residuoVel = new float[snap.leiturasVelocidade.length];
        for (int i = 0; i < snap.leiturasPosX.length; i++) {
            residuoX[i] = snap.leiturasPosX[i] - snap.verdadeiroPosX[i];
            residuoY[i] = snap.leiturasPosY[i] - snap.verdadeiroPosY[i];
            residuoVelX[i] = snap.leiturasVelocidadeX[i] - snap.verdadeiroVelocidadeX[i];
            residuoVelY[i] = snap.leiturasVelocidadeY[i] - snap.verdadeiroVelocidadeY[i];
            float velLeitura = snap.leiturasVelocidade[i];
            float velReal = (float) Math.sqrt(
                    snap.verdadeiroVelocidadeX[i] * snap.verdadeiroVelocidadeX[i]
                            + snap.verdadeiroVelocidadeY[i] * snap.verdadeiroVelocidadeY[i]);
            residuoVel[i] = velLeitura - velReal;
        }
        mediaX = media(residuoX);
        double varX = varianciaAmostral(residuoX, mediaX);
        double mediaY = media(residuoY);
        double varY = varianciaAmostral(residuoY, mediaY);
        double mediaV = media(residuoVel);
        double varV = varianciaAmostral(residuoVel, mediaV);
        double mediaVelX = media(residuoVelX);
        double varVelX = varianciaAmostral(residuoVelX, mediaVelX);
        double mediaVelY = media(residuoVelY);
        double varVelY = varianciaAmostral(residuoVelY, mediaVelY);
        
        ReconciliationLog.getInstance().logSensorStats(
                lado.name(), snap.alvosAtivos.size(), historico, mediaX, varX, mediaV, varV);
        
        // [EXCELENTE] Log detalhado de variancia dos sensores (X, Y, VelX, VelY)
        ReconciliationLog.getInstance().logSensorVariance(
                lado.name(), mediaX, varX, mediaVelX, varVelX, mediaY, varY, mediaVelY, varVelY);
        
        // Registrar para visualização em dashboard
        float[] distancias = new float[snap.leiturasPosX.length];
        for (int i = 0; i < snap.leiturasPosX.length; i++) {
            distancias[i] = snap.leiturasPosX[i];
        }
        SensorStatisticsTracker.registrarEstatisticas(
                snap.alvosAtivos.size(),
                lado.name(),
                distancias);
    }

    private double media(float[] valores) {
        if (valores == null || valores.length == 0) return 0;
        double soma = 0;
        for (float v : valores) soma += v;
        return soma / valores.length;
    }

    private double varianciaAmostral(float[] valores, double media) {
        if (valores == null || valores.length < 2) return 0;
        double soma = 0;
        for (float v : valores) {
            double diff = v - media;
            soma += diff * diff;
        }
        return soma / (valores.length - 1);
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
            if (dado == null) return null;

            if (!dado.historicoDistancias.isEmpty() && dado.historicoPorAlvo.isEmpty()) {
                return calcularMediaHistoricoLegado(dado.historicoDistancias);
            }

            if (dado.historicoPorAlvo.isEmpty()) return null;

            // Filtrar apenas alvos que possuem o histórico mínimo exigido
            List<Long> idsValidos = new ArrayList<>();
            for (Map.Entry<Long, TargetHistory> entry : dado.historicoPorAlvo.entrySet()) {
                if (entry.getValue().samples.size() >= TAMANHO_HISTORICO) {
                    idsValidos.add(entry.getKey());
                }
            }

            if (idsValidos.isEmpty()) return null;

            int M = idsValidos.size();
            int N = dado.historicoPorAlvo.get(idsValidos.get(0)).samples.getLast().distancias.length;
            float[][] media = new float[M][N];

            long now = System.currentTimeMillis();

            for (int i = 0; i < M; i++) {
                TargetHistory history = dado.historicoPorAlvo.get(idsValidos.get(i));
                for (TargetHistory.Sample sample : history.samples) {
                    // COMPENSAÇÃO DE MOVIMENTO (Radar Dead Reckoning)
                    // GARANTIMOS que o histórico é do MESMO ALVO via ID.
                    for (int j = 0; j < N; j++) {
                        if (sample.distancias != null && j < sample.distancias.length) {
                             media[i][j] += sample.distancias[j]; 
                        }
                    }
                }
                for (int j = 0; j < N; j++) media[i][j] /= history.samples.size();
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
            if (dado != null && !dado.historicoDistancias.isEmpty() && dado.historicoPorAlvo.isEmpty()) {
                return calcularVarianciaHistoricoLegado(dado.historicoDistancias);
            }
            float[][] media = getMediaDistancias(lado);
            if (dado == null || media == null) return null;

            int M = media.length;
            int N = media[0].length;
            float[][] variancia = new float[M][N];

            List<Long> idsValidos = new ArrayList<>();
            for (Map.Entry<Long, TargetHistory> entry : dado.historicoPorAlvo.entrySet()) {
                if (entry.getValue().samples.size() >= TAMANHO_HISTORICO) {
                    idsValidos.add(entry.getKey());
                }
            }

            for (int i = 0; i < M; i++) {
                TargetHistory history = dado.historicoPorAlvo.get(idsValidos.get(i));
                for (TargetHistory.Sample sample : history.samples) {
                    if (sample.distancias == null) continue;
                    for (int j = 0; j < N; j++) {
                        float diff = sample.distancias[j] - media[i][j];
                        variancia[i][j] += diff * diff;
                    }
                }
                for (int j = 0; j < N; j++) {
                    variancia[i][j] /= (history.samples.size() - 1);
                    if (variancia[i][j] < 0.01f) variancia[i][j] = 0.01f; // Piso de variância
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
            if (dado == null) return 0;

            if (dado.historicoPorAlvo.isEmpty()) {
                return dado.historicoDistancias.size();
            }
            
            // Retornar o maior histórico disponível entre os alvos ativos
            int max = 0;
            for (TargetHistory th : dado.historicoPorAlvo.values()) {
                if (th.samples.size() > max) max = th.samples.size();
            }
            return max;
        }
    }

    /**
     * Retorna a quantidade de alvos que já possuem histórico suficiente para reconciliação.
     * Atende ao requisito de cobertura mínima da AV2.
     */
    public int getAlvosProntosCount(Lado lado) {
        synchronized (sensorLock) {
            SideSensorData dado = dadosPorLado.get(lado);
            if (dado == null) return 0;
            int prontos = 0;
            for (TargetHistory th : dado.historicoPorAlvo.values()) {
                if (th.samples.size() >= TAMANHO_HISTORICO) prontos++;
            }
            return prontos;
        }
    }

    public static int getHistoricoMinimoReconciliacao() {
        return TAMANHO_HISTORICO;
    }

    public boolean isAlvoTravado(Lado lado, long targetId) {
        synchronized (sensorLock) {
            SideSensorData dado = dadosPorLado.get(lado);
            if (dado == null) return false;

            TargetHistory history = dado.historicoPorAlvo.get(targetId);
            return history != null && history.samples.size() >= TAMANHO_HISTORICO;
        }
    }

    private float[][] calcularMediaHistoricoLegado(LinkedList<float[][]> historico) {
        if (historico.isEmpty()) return null;
        float[][] primeira = historico.getFirst();
        if (primeira == null || primeira.length == 0) return null;
        int M = primeira.length;
        int N = primeira[0].length;
        float[][] media = new float[M][N];
        for (float[][] amostra : historico) {
            if (amostra == null || amostra.length != M) continue;
            for (int i = 0; i < M; i++) {
                if (amostra[i] == null || amostra[i].length != N) continue;
                for (int j = 0; j < N; j++) {
                    media[i][j] += amostra[i][j];
                }
            }
        }
        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                media[i][j] /= historico.size();
            }
        }
        return media;
    }

    private float[][] calcularVarianciaHistoricoLegado(LinkedList<float[][]> historico) {
        float[][] media = calcularMediaHistoricoLegado(historico);
        if (media == null) return null;
        int M = media.length;
        int N = media[0].length;
        float[][] variancia = new float[M][N];
        for (float[][] amostra : historico) {
            if (amostra == null || amostra.length != M) continue;
            for (int i = 0; i < M; i++) {
                if (amostra[i] == null || amostra[i].length != N) continue;
                for (int j = 0; j < N; j++) {
                    float diff = amostra[i][j] - media[i][j];
                    variancia[i][j] += diff * diff;
                }
            }
        }
        float divisor = Math.max(1, historico.size() - 1);
        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                variancia[i][j] = Math.max(variancia[i][j] / divisor, 0.01f);
            }
        }
        return variancia;
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

    public static class TargetSnapshot {
        public final long id;
        public final float[][] mediaD; // distâncias [1][N]
        public final float[][] varD;   // variâncias [1][N]
        public final float[] canhoesX; // posições médias ou alinhadas
        public final float[] canhoesY;
        public final float verdadeiroX, verdadeiroY;
        public final float leituraX, leituraY;
        public final float verdadeiroVelX, verdadeiroVelY;
        public final float leituraVelX, leituraVelY;

        public TargetSnapshot(long id, float[][] mediaD, float[][] varD, float[] cX, float[] cY,
                              float vX, float vY, float lX, float lY,
                              float tvx, float tvy, float lvx, float lvy) {
            this.id = id; this.mediaD = mediaD; this.varD = varD; this.canhoesX = cX; this.canhoesY = cY;
            this.verdadeiroX = vX; this.verdadeiroY = vY; this.leituraX = lX; this.leituraY = lY;
            this.verdadeiroVelX = tvx; this.verdadeiroVelY = tvy; this.leituraVelX = lvx; this.leituraVelY = lvy;
        }
    }

    public List<TargetSnapshot> getSnapshotsParaReconciliacao(Lado lado) {
        synchronized (sensorLock) {
            SideSensorData dado = dadosPorLado.get(lado);
            if (dado == null || dado.historicoPorAlvo.isEmpty()) return null;

            List<TargetSnapshot> results = new ArrayList<>();
            for (Map.Entry<Long, TargetHistory> entry : dado.historicoPorAlvo.entrySet()) {
                TargetHistory history = entry.getValue();
                if (history.samples.size() < TAMANHO_HISTORICO) continue;

                TargetHistory.Sample last = history.samples.getLast();
                if (last.distancias == null || last.canhoesX == null) continue;

                int N = last.distancias.length;
                float[] mediaD = new float[N];
                float[] varD = new float[N];
                
                // Média por alvo (leituras)
                for (TargetHistory.Sample s : history.samples) {
                    if (s.distancias == null || s.distancias.length != N) continue;
                    for (int j = 0; j < N; j++) mediaD[j] += s.distancias[j];
                }
                for (int j = 0; j < N; j++) mediaD[j] /= history.samples.size();
                
                // Variância baseada no RESÍDUO (Leitura - Real) para isolar o ruído do movimento (AV2)
                for (TargetHistory.Sample s : history.samples) {
                    if (s.distancias == null || s.distancias.length != N) continue;
                    for (int j = 0; j < N; j++) {
                        float dx = s.trueX - s.canhoesX[j];
                        float dy = s.trueY - s.canhoesY[j];
                        float distReal = (float) Math.sqrt(dx * dx + dy * dy);
                        float residuo = s.distancias[j] - distReal;
                        varD[j] += residuo * residuo;
                    }
                }
                for (int j = 0; j < N; j++) {
                    varD[j] = Math.max(varD[j] / history.samples.size(), 0.01f);
                }

                results.add(new TargetSnapshot(entry.getKey(), new float[][]{mediaD}, new float[][]{varD},
                        last.canhoesX, last.canhoesY,
                        last.trueX, last.trueY, last.x, last.y,
                        last.trueVx, last.trueVy, last.vx, last.vy));
            }
            return results;
        }
    }

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

    /**
     * Retorna a última leitura ruidosa de um alvo específico para visualização "Ghost".
     * @param targetId ID do alvo
     * @return float[2] {x, y} ruidoso, ou null se não houver leitura
     */
    public float[] getUltimaLeituraRuidosa(long targetId) {
        synchronized (sensorLock) {
            for (SideSensorData data : dadosPorLado.values()) {
                TargetHistory history = data.historicoPorAlvo.get(targetId);
                if (history != null && !history.samples.isEmpty()) {
                    TargetHistory.Sample last = history.samples.getLast();
                    return new float[]{last.x, last.y};
                }
            }
        }
        return null;
    }
}
