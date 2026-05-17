package com.autotarget.util;

import com.autotarget.model.Lado;
import com.autotarget.service.SensorThread;

/**
 * Controlador de feedback para evitar inanição de dados.
 */
public class DataStarvationController {

    private static final int HISTORICO_MINIMO_SEGURO = 10;
    private static final double EVASAO_NORMAL = 0.0;
    private static final double EVASAO_JAMMING = 1.0;

    private final SensorThread sensorThread;
    private volatile double evasaoEsquerda = EVASAO_NORMAL;
    private volatile double evasaoDireita = EVASAO_NORMAL;

    public DataStarvationController(SensorThread sensorThread) {
        this.sensorThread = sensorThread;
    }

    public void monitorarEAtuar() {
        if (sensorThread == null) return;

        int histEsq = sensorThread.getHistoricoCount(Lado.ESQUERDO);
        int histDir = sensorThread.getHistoricoCount(Lado.DIREITO);

        atualizarEvasao(Lado.ESQUERDO, histEsq);
        atualizarEvasao(Lado.DIREITO, histDir);

        ReconciliationLog.getInstance().logStarvationState(histEsq, histDir, evasaoEsquerda, evasaoDireita);
    }

    private void atualizarEvasao(Lado lado, int historico) {
        double novoValor = (historico > 0 && historico < HISTORICO_MINIMO_SEGURO)
                ? EVASAO_JAMMING
                : EVASAO_NORMAL;

        if (lado == Lado.ESQUERDO) {
            if (evasaoEsquerda != novoValor) {
                evasaoEsquerda = novoValor;
                ReconciliationLog.getInstance().logEvasion(lado,
                        novoValor == EVASAO_JAMMING ? "Contramedida ATIVADA (Starvation)" : "Contramedida DESATIVADA");
            }
        } else {
            if (evasaoDireita != novoValor) {
                evasaoDireita = novoValor;
                ReconciliationLog.getInstance().logEvasion(lado,
                        novoValor == EVASAO_JAMMING ? "Contramedida ATIVADA (Starvation)" : "Contramedida DESATIVADA");
            }
        }
    }

    public boolean alvoEsquivou(Lado lado) {
        double chanceEvasao = lado == Lado.ESQUERDO ? evasaoEsquerda : evasaoDireita;
        return Math.random() < chanceEvasao;
    }

    public double getEvasaoEsquerda() {
        return evasaoEsquerda;
    }

    public double getEvasaoDireita() {
        return evasaoDireita;
    }
}