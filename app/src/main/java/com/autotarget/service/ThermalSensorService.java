/*
 * ============================================================================
 * Arquivo: ThermalSensorService.java
 * Pacote:  com.autotarget.service
 * ============================================================================
 *
 * DESCRIÇÃO TÉCNICA:
 *   Serviço de retroalimentação térmica (sistema ciberfísico).
 *   Registra Sensor.TYPE_AMBIENT_TEMPERATURE via SensorManager e aplica
 *   penalidade térmica nos canhões quando temperatura > 40°C.
 *
 * LOOP DE CONTROLE (AV3 §6.3.2-d):
 *   Se temp > 40°C: fator = 1.0 + (temp - 40) × 0.1
 *   O fator é injetado em todos os Canhao ativos, expandindo Thread.sleep().
 *
 * FALLBACK:
 *   Se sensor não disponível, gera dados sintéticos (30-38°C).
 *
 * ============================================================================
 */
package com.autotarget.service;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import com.autotarget.model.Canhao;
import com.autotarget.network.FirestoreRepository;

import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Serviço ciberfísico de retroalimentação térmica.
 */
public class ThermalSensorService implements SensorEventListener {

    private static final String TAG = "ThermalSensor";
    private static final float LIMIAR_CRITICO = 40.0f; // °C
    private static final long INTERVALO_COLETA = 10000; // 10s

    private SensorManager sensorManager;
    private Sensor temperatureSensor;
    private boolean sensorDisponivel;

    private volatile float temperaturaAtual;
    private final List<Canhao> canhoes;
    private final FirestoreRepository firestoreRepository;
    private final Random random = new Random();
    private Timer timer;

    /** Callback para informar temperatura ao Jogo. */
    private OnTemperaturaListener temperatureListener;

    public interface OnTemperaturaListener {
        void onTemperaturaAtualizada(float temperatura, float penaltyFactor);
    }

    public ThermalSensorService(Context context, List<Canhao> canhoes,
                                 FirestoreRepository firestoreRepository) {
        this.canhoes = canhoes;
        this.firestoreRepository = firestoreRepository;
        this.temperaturaAtual = 30.0f;

        try {
            sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
            temperatureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);

            if (temperatureSensor != null) {
                sensorDisponivel = true;
                sensorManager.registerListener(this, temperatureSensor,
                        SensorManager.SENSOR_DELAY_NORMAL);
                Log.i(TAG, "Sensor de temperatura ambiente registrado");
            } else {
                sensorDisponivel = false;
                Log.w(TAG, "Sensor de temperatura não disponível — usando dados sintéticos");
            }
        } catch (Exception e) {
            sensorDisponivel = false;
            Log.e(TAG, "Erro ao inicializar sensor", e);
        }
    }

    /**
     * Inicia o loop de coleta periódica.
     */
    public void iniciar() {
        timer = new Timer("ThermalTimer", true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                processarTemperatura();
            }
        }, INTERVALO_COLETA, INTERVALO_COLETA);
    }

    /**
     * Para o serviço e desregistra o sensor.
     */
    public void parar() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        if (sensorManager != null && sensorDisponivel) {
            sensorManager.unregisterListener(this);
        }
    }

    /**
     * Processa a temperatura e aplica feedback nos canhões.
     */
    private void processarTemperatura() {
        // Se sensor não disponível, gerar dado sintético
        if (!sensorDisponivel) {
            temperaturaAtual = 30.0f + random.nextFloat() * 8.0f; // 30-38°C
        }

        // Calcular fator de penalidade
        float fator = 1.0f;
        if (temperaturaAtual > LIMIAR_CRITICO) {
            fator = 1.0f + (temperaturaAtual - LIMIAR_CRITICO) * 0.1f;
            Log.w(TAG, String.format("THERMAL THROTTLING: %.1f°C > %.1f°C — fator=%.2f",
                    temperaturaAtual, LIMIAR_CRITICO, fator));
        }

        // Injetar fator nos canhões
        for (Canhao canhao : canhoes) {
            if (canhao.isAtivo()) {
                canhao.setThermalPenaltyFactor(fator);
            }
        }

        // Salvar telemetria no Firestore
        try {
            if (firestoreRepository != null) {
                firestoreRepository.salvarTelemetria(
                        temperaturaAtual, 0, 0, 0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao salvar telemetria", e);
        }

        // Notificar listener
        if (temperatureListener != null) {
            temperatureListener.onTemperaturaAtualizada(temperaturaAtual, fator);
        }
    }

    // ── SensorEventListener ─────────────────────────────────────

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_AMBIENT_TEMPERATURE) {
            temperaturaAtual = event.values[0];
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Não utilizado
    }

    // ── Getters ─────────────────────────────────────────────────

    public float getTemperaturaAtual() { return temperaturaAtual; }
    public boolean isSensorDisponivel() { return sensorDisponivel; }

    public void setTemperatureListener(OnTemperaturaListener listener) {
        this.temperatureListener = listener;
    }
}
