package com.autotarget.engine;

import android.graphics.Paint;
import android.util.SparseArray;

/**
 * Utilitário de renderização compartilhado: cache de Paints usados por views.
 * Extraído de GameSurfaceView para reduzir responsabilidades da View.
 */
public final class GameRenderer {

    private static final SparseArray<Paint> paintAlvoCache = new SparseArray<>();
    private static final SparseArray<Paint> paintGlowCache = new SparseArray<>();

    private GameRenderer() {}

    public static void registerDefaults() {
        registrarPaintAlvo(0xFF4CAF50);
        registrarPaintAlvo(0xFFFF9800);
    }

    public static void registrarPaintAlvo(int color) {
        if (paintAlvoCache.get(color) != null && paintGlowCache.get(color) != null) return;

        Paint base = new Paint();
        base.setColor(color);
        base.setAntiAlias(true);
        base.setStyle(Paint.Style.FILL);

        Paint glow = new Paint(base);
        glow.setAlpha(60);

        paintAlvoCache.put(color, base);
        paintGlowCache.put(color, glow);
    }

    public static Paint paintForColor(int color) {
        Paint paint = paintAlvoCache.get(color);
        if (paint == null) {
            registrarPaintAlvo(color);
            paint = paintAlvoCache.get(color);
        }
        return paint;
    }

    public static Paint glowForColor(int color) {
        Paint glow = paintGlowCache.get(color);
        if (glow == null) {
            registrarPaintAlvo(color);
            glow = paintGlowCache.get(color);
        }
        return glow;
    }
}
