with open('app/src/main/java/com/autotarget/engine/GameSurfaceView.java', 'r') as f:
    content = f.read()

search = """        // ── Canhões (cor diferente por lado) ──
        for (Canhao canhao : jogo.getCanhoes()) {
            if (!canhao.isAtivo()) continue;
            Paint paintCanhao = (canhao.getLado() == Lado.ESQUERDO) ? paintCanhaoEsq : paintCanhaoDir;
            desenharCanhao(canvas, canhao, paintCanhao);
        }"""
replace = """        // ── Canhões (cor diferente por lado) ──
        synchronized(jogo.getCanhoesLock()) {
            java.util.List<Canhao> canhoes = jogo.getCanhoes();
            for (int i = 0; i < canhoes.size(); i++) {
                Canhao canhao = canhoes.get(i);
                if (!canhao.isAtivo()) continue;
                Paint paintCanhao = (canhao.getLado() == Lado.ESQUERDO) ? paintCanhaoEsq : paintCanhaoDir;
                desenharCanhao(canvas, canhao, paintCanhao);
            }
        }"""
content = content.replace(search, replace)

with open('app/src/main/java/com/autotarget/engine/GameSurfaceView.java', 'w') as f:
    f.write(content)
