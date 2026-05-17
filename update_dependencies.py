import re

# Update GameSurfaceView.java
with open('app/src/main/java/com/autotarget/engine/GameSurfaceView.java', 'r') as f:
    content = f.read()

# Fix 1: desenhar canhões (for (Canhao canhao : jogo.getCanhoes())) -> synchronized
desenhar_search = """        // ── Canhões (cor diferente por lado) ──
        for (Canhao canhao : jogo.getCanhoes()) {
            if (!canhao.isAtivo()) continue;
            Paint paintCanhao = (canhao.getLado() == Lado.ESQUERDO) ? paintCanhaoEsq : paintCanhaoDir;
            desenharCanhao(canvas, canhao, paintCanhao);
        }"""
desenhar_replace = """        // ── Canhões (cor diferente por lado) ──
        synchronized(jogo.getCanhoesLock()) {
            java.util.List<Canhao> canhoes = jogo.getCanhoes();
            for (int i = 0; i < canhoes.size(); i++) {
                Canhao canhao = canhoes.get(i);
                if (!canhao.isAtivo()) continue;
                Paint paintCanhao = (canhao.getLado() == Lado.ESQUERDO) ? paintCanhaoEsq : paintCanhaoDir;
                desenharCanhao(canvas, canhao, paintCanhao);
            }
        }"""
content = content.replace(desenhar_search, desenhar_replace)

# Fix 2: onTouchEvent getCanhoesEsquerdo()
touch_search_esq = """                    for (Canhao c : jogo.getCanhoesEsquerdo()) {
                        if (!c.isAtivo()) continue;
                        float dist = Alvo.calcularDistancia(c.getX(), c.getY(), touchX, touchY);
                        if (dist < RAIO_TOQUE) {
                            draggedCanhao = c;
                            break;
                        }
                    }"""
touch_replace_esq = """                    synchronized(jogo.getCanhoesLock()) {
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
                    }"""
content = content.replace(touch_search_esq, touch_replace_esq)

# Fix 3: onTouchEvent getCanhoesDireito()
touch_search_dir = """                    for (Canhao c : jogo.getCanhoesDireito()) {
                        if (!c.isAtivo()) continue;
                        float dist = Alvo.calcularDistancia(c.getX(), c.getY(), touchX, touchY);
                        if (dist < RAIO_TOQUE) {
                            draggedCanhao = c;
                            break;
                        }
                    }"""
touch_replace_dir = """                    synchronized(jogo.getCanhoesLock()) {
                        java.util.List<Canhao> canhoesDir = jogo.getCanhoesDireito();
                        for (int i = 0; i < canhoesDir.size(); i++) {
                            Canhao c = canhoesDir.get(i);
                            if (!c.isAtivo()) continue;
                            float dist = Alvo.calcularDistancia(c.getX(), c.getY(), touchX, touchY);
                            if (dist < RAIO_TOQUE) {
                                draggedCanhao = c;
                                break;
                            }
                        }
                    }"""
content = content.replace(touch_search_dir, touch_replace_dir)

# Fix 4: metrics get size
metrics_search = """        int nCanhoesEsq = jogo.getCanhoesEsquerdo().size();
        int nCanhoeDir = jogo.getCanhoesDireito().size();"""
metrics_replace = """        int nCanhoesEsq = 0;
        int nCanhoeDir = 0;
        synchronized(jogo.getCanhoesLock()) {
            nCanhoesEsq = jogo.getCanhoesEsquerdo().size();
            nCanhoeDir = jogo.getCanhoesDireito().size();
        }"""
content = content.replace(metrics_search, metrics_replace)

# Fix 5: onTouchEvent add canhão getCanhoesEsquerdo
touch_add_search = """                        java.util.List<Canhao> lista = jogo.getCanhoesEsquerdo();
                        if (!lista.isEmpty()) {
                            draggedCanhao = lista.get(lista.size() - 1);
                        }"""
touch_add_replace = """                        synchronized(jogo.getCanhoesLock()) {
                            java.util.List<Canhao> lista = jogo.getCanhoesEsquerdo();
                            if (!lista.isEmpty()) {
                                draggedCanhao = lista.get(lista.size() - 1);
                            }
                        }"""
content = content.replace(touch_add_search, touch_add_replace)

# Fix 6: onTouchEvent add canhão getCanhoesDireito
touch_add_search_dir = """                        java.util.List<Canhao> lista = jogo.getCanhoesDireito();
                        if (!lista.isEmpty()) {
                            draggedCanhao = lista.get(lista.size() - 1);
                        }"""
touch_add_replace_dir = """                        synchronized(jogo.getCanhoesLock()) {
                            java.util.List<Canhao> lista = jogo.getCanhoesDireito();
                            if (!lista.isEmpty()) {
                                draggedCanhao = lista.get(lista.size() - 1);
                            }
                        }"""
content = content.replace(touch_add_search_dir, touch_add_replace_dir)


with open('app/src/main/java/com/autotarget/engine/GameSurfaceView.java', 'w') as f:
    f.write(content)


# Update SensorThread.java
with open('app/src/main/java/com/autotarget/service/SensorThread.java', 'r') as f:
    sensor_content = f.read()

sensor_search = """            for (Canhao c : jogo.getCanhoesEsquerdo()) {
                if (c.isAtivo()) canhoesPos.add(new float[]{c.getX(), c.getY()});
            }
            for (Canhao c : jogo.getCanhoesDireito()) {
                if (c.isAtivo()) canhoesPos.add(new float[]{c.getX(), c.getY()});
            }"""
sensor_replace = """            synchronized(jogo.getCanhoesLock()) {
                java.util.List<Canhao> canhoesEsq = jogo.getCanhoesEsquerdo();
                for (int i = 0; i < canhoesEsq.size(); i++) {
                    Canhao c = canhoesEsq.get(i);
                    if (c.isAtivo()) canhoesPos.add(new float[]{c.getX(), c.getY()});
                }
                java.util.List<Canhao> canhoesDir = jogo.getCanhoesDireito();
                for (int i = 0; i < canhoesDir.size(); i++) {
                    Canhao c = canhoesDir.get(i);
                    if (c.isAtivo()) canhoesPos.add(new float[]{c.getX(), c.getY()});
                }
            }"""
sensor_content = sensor_content.replace(sensor_search, sensor_replace)

with open('app/src/main/java/com/autotarget/service/SensorThread.java', 'w') as f:
    f.write(sensor_content)


# Update ReconciliacaoThread.java
with open('app/src/main/java/com/autotarget/service/ReconciliacaoThread.java', 'r') as f:
    rec_content = f.read()

rec_search_1 = """        List<Canhao> lista = lado == Lado.ESQUERDO ? jogo.getCanhoesEsquerdo() : jogo.getCanhoesDireito();
        if (lista.size() < 2) return null; // Sem base

        for (Canhao c : lista) {
            if (!c.isAtivo()) continue;
            for (int[] pos : regioesCalor) {
                float dist = Alvo.calcularDistancia(c.getX(), c.getY(), pos[0], pos[1]);
                if (dist > maxDist) {
                    maxDist = dist;
                    pior = c;
                }
            }
        }"""
rec_replace_1 = """        Canhao pior = null;
        float maxDist = -1f;
        synchronized(jogo.getCanhoesLock()) {
            List<Canhao> lista = lado == Lado.ESQUERDO ? jogo.getCanhoesEsquerdo() : jogo.getCanhoesDireito();
            if (lista.size() < 2) return null; // Sem base

            for (int i = 0; i < lista.size(); i++) {
                Canhao c = lista.get(i);
                if (!c.isAtivo()) continue;
                for (int[] pos : regioesCalor) {
                    float dist = Alvo.calcularDistancia(c.getX(), c.getY(), pos[0], pos[1]);
                    if (dist > maxDist) {
                        maxDist = dist;
                        pior = c;
                    }
                }
            }
        }"""
rec_content = rec_content.replace("""        Canhao pior = null;
        float maxDist = -1f;
""" + rec_search_1, rec_replace_1)

rec_search_2 = """        java.util.List<Canhao> origem = lado == Lado.ESQUERDO ? jogo.getCanhoesEsquerdo() : jogo.getCanhoesDireito();
        boolean mudei = false;
        for (Canhao c : origem) {
            if (!c.isAtivo() || c.isMovendo()) continue;
            // Procura calor desassistido
            for (int[] calor : regioesCalor) {
                float dx = c.getX() - calor[0];
                float dy = c.getY() - calor[1];
                if (dx*dx + dy*dy > 40000) { // mais de 200px
                    listener.onRealocarCanhao(c, calor[0], calor[1]);
                    mudei = true;
                    break;
                }
            }
            if (mudei) break;
        }"""
rec_replace_2 = """        boolean mudei = false;
        synchronized(jogo.getCanhoesLock()) {
            java.util.List<Canhao> origem = lado == Lado.ESQUERDO ? jogo.getCanhoesEsquerdo() : jogo.getCanhoesDireito();
            for (int i = 0; i < origem.size(); i++) {
                Canhao c = origem.get(i);
                if (!c.isAtivo() || c.isMovendo()) continue;
                // Procura calor desassistido
                for (int[] calor : regioesCalor) {
                    float dx = c.getX() - calor[0];
                    float dy = c.getY() - calor[1];
                    if (dx*dx + dy*dy > 40000) { // mais de 200px
                        listener.onRealocarCanhao(c, calor[0], calor[1]);
                        mudei = true;
                        break;
                    }
                }
                if (mudei) break;
            }
        }"""
rec_content = rec_content.replace(rec_search_2, rec_replace_2)

with open('app/src/main/java/com/autotarget/service/ReconciliacaoThread.java', 'w') as f:
    f.write(rec_content)
