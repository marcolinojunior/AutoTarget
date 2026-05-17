with open('app/src/main/java/com/autotarget/engine/Jogo.java', 'r') as f:
    content = f.read()

# Fix 1: spawnarAlvo
search_spawn = """            com.autotarget.engine.GameGeometry geom = com.autotarget.engine.GameGeometry.forScreen(larguraTela, alturaTela);
            Lado lado = geom.determineLado(x);
            if (lado == Lado.ESQUERDO) {
                alvosEsquerdo.add(alvo);
            } else {
                alvosDireito.add(alvo);
            }"""
replace_spawn = """            com.autotarget.engine.GameGeometry geom = com.autotarget.engine.GameGeometry.forScreen(larguraTela, alturaTela);
            Lado lado = geom.determineLado(x);
            synchronized(listLock) {
                if (lado == Lado.ESQUERDO) {
                    alvosEsquerdo.add(alvo);
                } else {
                    alvosDireito.add(alvo);
                }
            }"""
content = content.replace(search_spawn, replace_spawn)

# Fix 2: reservarAlvo
search_reservar = """        List<Alvo> listaAlvos =
                (canhao.getLado() == Lado.ESQUERDO) ? alvosEsquerdo : alvosDireito;

        Alvo melhor = null;
        float menorDist = Float.MAX_VALUE;

        for (int i = 0; i < listaAlvos.size(); i++) {
            Alvo alvo = listaAlvos.get(i);
            if (!alvo.isAtivo()) continue;

            // Verificar se já está reservado por OUTRO canhão
            Canhao dono = reservasAlvos.get(alvo);
            if (dono != null && dono != canhao && dono.isAtivo()) {
                continue; // Alvo já comprometido por outro canhão
            }

            float dist = Alvo.calcularDistancia(canhao.getX(), canhao.getY(),
                    alvo.getX(), alvo.getY());
            if (dist < menorDist) {
                menorDist = dist;
                melhor = alvo;
            }
        }"""
replace_reservar = """        Alvo melhor = null;
        float menorDist = Float.MAX_VALUE;

        synchronized(listLock) {
            List<Alvo> listaAlvos =
                    (canhao.getLado() == Lado.ESQUERDO) ? alvosEsquerdo : alvosDireito;

            for (int i = 0; i < listaAlvos.size(); i++) {
                Alvo alvo = listaAlvos.get(i);
                if (!alvo.isAtivo()) continue;

                // Verificar se já está reservado por OUTRO canhão
                Canhao dono = reservasAlvos.get(alvo);
                if (dono != null && dono != canhao && dono.isAtivo()) {
                    continue; // Alvo já comprometido por outro canhão
                }

                float dist = Alvo.calcularDistancia(canhao.getX(), canhao.getY(),
                        alvo.getX(), alvo.getY());
                if (dist < menorDist) {
                    menorDist = dist;
                    melhor = alvo;
                }
            }
        }"""
content = content.replace(search_reservar, replace_reservar)

# Fix 3: getAllAlvos
search_getAllAlvos = """    public List<Alvo> getAllAlvos() {
        List<Alvo> list = new ArrayList<>();
        list.addAll(alvosEsquerdo);
        list.addAll(alvosDireito);
        return list;
    }"""
replace_getAllAlvos = """    public List<Alvo> getAllAlvos() {
        List<Alvo> list = new ArrayList<>();
        synchronized(listLock) {
            list.addAll(alvosEsquerdo);
            list.addAll(alvosDireito);
        }
        return list;
    }"""
content = content.replace(search_getAllAlvos, replace_getAllAlvos)

with open('app/src/main/java/com/autotarget/engine/Jogo.java', 'w') as f:
    f.write(content)
