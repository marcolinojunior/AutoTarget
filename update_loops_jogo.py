import re

with open('app/src/main/java/com/autotarget/engine/Jogo.java', 'r') as f:
    content = f.read()

# Fix 1: physicsTask
#   for (Canhao c : getAllCanhoes()) {
#       if (c.isAtivo() && c.isMovendo()) {
#           c.atualizarMovimento();
#       }
#   }
# Needs to wrap with synchronized(canhoesLock) and avoid getAllCanhoes() inside physicsTask
physics_loop_search = """                    // Atualiza a física de movimentação dos canhões (Deslize a 60Hz)
                    for (Canhao c : getAllCanhoes()) {
                        if (c.isAtivo() && c.isMovendo()) {
                            c.atualizarMovimento();
                        }
                    }"""

physics_loop_replace = """                    // Atualiza a física de movimentação dos canhões (Deslize a 60Hz)
                    synchronized (canhoesLock) {
                        for (int i = 0; i < canhoesEsquerdo.size(); i++) {
                            Canhao c = canhoesEsquerdo.get(i);
                            if (c.isAtivo() && c.isMovendo()) {
                                c.atualizarMovimento();
                            }
                        }
                        for (int i = 0; i < canhoesDireito.size(); i++) {
                            Canhao c = canhoesDireito.get(i);
                            if (c.isAtivo() && c.isMovendo()) {
                                c.atualizarMovimento();
                            }
                        }
                    }"""
content = content.replace(physics_loop_search, physics_loop_replace)

# Fix 2: atualizarEnergia
atualizarEnergia_search = """    private void atualizarEnergia() {
        int canhoesEsq = 0, canhoesDir = 0;
        for (Canhao c : canhoesEsquerdo) {
            if (c.isAtivo()) canhoesEsq++;
        }
        for (Canhao c : canhoesDireito) {
            if (c.isAtivo()) canhoesDir++;
        }"""
atualizarEnergia_replace = """    private void atualizarEnergia() {
        int canhoesEsq = 0, canhoesDir = 0;
        synchronized (canhoesLock) {
            for (int i = 0; i < canhoesEsquerdo.size(); i++) {
                if (canhoesEsquerdo.get(i).isAtivo()) canhoesEsq++;
            }
            for (int i = 0; i < canhoesDireito.size(); i++) {
                if (canhoesDireito.get(i).isAtivo()) canhoesDir++;
            }
        }"""
content = content.replace(atualizarEnergia_search, atualizarEnergia_replace)

# Fix 3: calcularIntervaloMedio
calcularIntervaloMedio_search = """        for (Canhao c : canhoes) {
            if (!c.isAtivo()) continue;
            soma += c.getIntervaloDisparo();
            ativos++;
        }"""
calcularIntervaloMedio_replace = """        synchronized (canhoesLock) {
            for (int i = 0; i < canhoes.size(); i++) {
                Canhao c = canhoes.get(i);
                if (!c.isAtivo()) continue;
                soma += c.getIntervaloDisparo();
                ativos++;
            }
        }"""
content = content.replace(calcularIntervaloMedio_search, calcularIntervaloMedio_replace)

# Fix 4: desativarUltimoCanhao
desativarUltimoCanhao_search = """    private void desativarUltimoCanhao(Lado lado) {
        List<Canhao> lista = (lado == Lado.ESQUERDO) ? canhoesEsquerdo : canhoesDireito;
        for (int i = lista.size() - 1; i >= 0; i--) {
            Canhao c = lista.get(i);
            if (c.isAtivo()) {
                pararCanhaoComJoin(c, 300);
                break;
            }
        }
    }"""
desativarUltimoCanhao_replace = """    private void desativarUltimoCanhao(Lado lado) {
        List<Canhao> lista = (lado == Lado.ESQUERDO) ? canhoesEsquerdo : canhoesDireito;
        synchronized (canhoesLock) {
            for (int i = lista.size() - 1; i >= 0; i--) {
                Canhao c = lista.get(i);
                if (c.isAtivo()) {
                    pararCanhaoComJoin(c, 300);
                    break;
                }
            }
        }
    }"""
content = content.replace(desativarUltimoCanhao_search, desativarUltimoCanhao_replace)

# Fix 5: contarCanhoesAtivos
contarCanhoesAtivos_search = """        int count = 0;
        for (Canhao c : lista) {
            if (c.isAtivo()) count++;
        }
        return count;"""
contarCanhoesAtivos_replace = """        int count = 0;
        synchronized (canhoesLock) {
            for (int i = 0; i < lista.size(); i++) {
                if (lista.get(i).isAtivo()) count++;
            }
        }
        return count;"""
content = content.replace(contarCanhoesAtivos_search, contarCanhoesAtivos_replace)

# Fix 6: removerCanhao
removerCanhao_search = """        synchronized (listLock) {
            if (canhao.getLado() == Lado.ESQUERDO) {
                canhoesEsquerdo.remove(canhao);
            } else {
                canhoesDireito.remove(canhao);
            }
        }"""
removerCanhao_replace = """        synchronized (canhoesLock) {
            if (canhao.getLado() == Lado.ESQUERDO) {
                canhoesEsquerdo.remove(canhao);
            } else {
                canhoesDireito.remove(canhao);
            }
        }"""
content = content.replace(removerCanhao_search, removerCanhao_replace)

# Fix 7: reservarAlvo loop
reservarAlvo_search = """        for (Alvo alvo : listaAlvos) {
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
reservarAlvo_replace = """        for (int i = 0; i < listaAlvos.size(); i++) {
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
content = content.replace(reservarAlvo_search, reservarAlvo_replace)

# Fix 8: aplicarPenalidades
aplicarPenalidades_search = """        int contEsq = 0, contDir = 0;
        for (Canhao c : canhoesEsquerdo) { if (c.isAtivo()) contEsq++; }
        for (Canhao c : canhoesDireito) { if (c.isAtivo()) contDir++; }

        for (Canhao c : canhoesEsquerdo) {
            if (c.isAtivo()) c.aplicarPenalidade(contEsq);
        }
        for (Canhao c : canhoesDireito) {
            if (c.isAtivo()) c.aplicarPenalidade(contDir);
        }"""
aplicarPenalidades_replace = """        int contEsq = 0, contDir = 0;
        synchronized (canhoesLock) {
            for (int i = 0; i < canhoesEsquerdo.size(); i++) { if (canhoesEsquerdo.get(i).isAtivo()) contEsq++; }
            for (int i = 0; i < canhoesDireito.size(); i++) { if (canhoesDireito.get(i).isAtivo()) contDir++; }

            for (int i = 0; i < canhoesEsquerdo.size(); i++) {
                Canhao c = canhoesEsquerdo.get(i);
                if (c.isAtivo()) c.aplicarPenalidade(contEsq);
            }
            for (int i = 0; i < canhoesDireito.size(); i++) {
                Canhao c = canhoesDireito.get(i);
                if (c.isAtivo()) c.aplicarPenalidade(contDir);
            }
        }"""
content = content.replace(aplicarPenalidades_search, aplicarPenalidades_replace)

# Fix 9: adicionarCanhao
adicionarCanhao_search = """        List<Canhao> listaCanhoes = (lado == Lado.ESQUERDO) ? canhoesEsquerdo : canhoesDireito;
        synchronized (listLock) {
            int countLado = listaCanhoes.size();

            if (countLado >= MAX_CANHOES_POR_LADO) {
                throw new JogoException(
                        "Máximo de canhões no lado " + lado + " atingido (" + MAX_CANHOES_POR_LADO + ")!");
            }

            // Verificar energia do lado
            float energiaLado = getEnergia(lado);
            if (estado == Estado.RODANDO && energiaLado < CUSTO_ENERGIA_POR_CANHAO) {
                throw new JogoException("Energia insuficiente no lado " + lado + "!");
            }

            List<Alvo> listaAlvos = (lado == Lado.ESQUERDO) ? alvosEsquerdo : alvosDireito;
            Canhao canhao = new Canhao(x, y, lado, listaAlvos, collisionLock,
                    larguraTela, alturaTela, this);

            // Penalty check
            canhao.aplicarPenalidade(countLado + 1);

            listaCanhoes.add(canhao);

            if (estado == Estado.RODANDO) {
                canhao.start();
            }
        }"""
adicionarCanhao_replace = """        List<Canhao> listaCanhoes = (lado == Lado.ESQUERDO) ? canhoesEsquerdo : canhoesDireito;
        synchronized (canhoesLock) {
            int countLado = listaCanhoes.size();

            if (countLado >= MAX_CANHOES_POR_LADO) {
                throw new JogoException(
                        "Máximo de canhões no lado " + lado + " atingido (" + MAX_CANHOES_POR_LADO + ")!");
            }

            // Verificar energia do lado
            float energiaLado = getEnergia(lado);
            if (estado == Estado.RODANDO && energiaLado < CUSTO_ENERGIA_POR_CANHAO) {
                throw new JogoException("Energia insuficiente no lado " + lado + "!");
            }

            List<Alvo> listaAlvos = (lado == Lado.ESQUERDO) ? alvosEsquerdo : alvosDireito;
            Canhao canhao = new Canhao(x, y, lado, listaAlvos, collisionLock,
                    larguraTela, alturaTela, this);

            // Penalty check
            canhao.aplicarPenalidade(countLado + 1);

            listaCanhoes.add(canhao);

            if (estado == Estado.RODANDO) {
                canhao.start();
            }
        }"""
content = content.replace(adicionarCanhao_search, adicionarCanhao_replace)

# Fix 10: transferirAlvosCruzados loops
transferirAlvosCruzados_search = """            for (Alvo alvo : alvosEsquerdo) {
                if (geom.determineLado(alvo.getX()) == Lado.DIREITO) {
                    transferBufferDireita.add(alvo);
                }
            }
            for (Alvo alvo : alvosDireito) {
                if (geom.determineLado(alvo.getX()) == Lado.ESQUERDO) {
                    transferBufferEsquerda.add(alvo);
                }
            }

            if (!transferBufferDireita.isEmpty()) {
                alvosEsquerdo.removeAll(transferBufferDireita);
                alvosDireito.addAll(transferBufferDireita);
                for (Alvo alvo : transferBufferDireita) liberarAlvo(alvo);
            }
            if (!transferBufferEsquerda.isEmpty()) {
                alvosDireito.removeAll(transferBufferEsquerda);
                alvosEsquerdo.addAll(transferBufferEsquerda);
                for (Alvo alvo : transferBufferEsquerda) liberarAlvo(alvo);
            }"""
transferirAlvosCruzados_replace = """            for (int i = 0; i < alvosEsquerdo.size(); i++) {
                Alvo alvo = alvosEsquerdo.get(i);
                if (geom.determineLado(alvo.getX()) == Lado.DIREITO) {
                    transferBufferDireita.add(alvo);
                }
            }
            for (int i = 0; i < alvosDireito.size(); i++) {
                Alvo alvo = alvosDireito.get(i);
                if (geom.determineLado(alvo.getX()) == Lado.ESQUERDO) {
                    transferBufferEsquerda.add(alvo);
                }
            }

            if (!transferBufferDireita.isEmpty()) {
                alvosEsquerdo.removeAll(transferBufferDireita);
                alvosDireito.addAll(transferBufferDireita);
                for (int i = 0; i < transferBufferDireita.size(); i++) liberarAlvo(transferBufferDireita.get(i));
            }
            if (!transferBufferEsquerda.isEmpty()) {
                alvosDireito.removeAll(transferBufferEsquerda);
                alvosEsquerdo.addAll(transferBufferEsquerda);
                for (int i = 0; i < transferBufferEsquerda.size(); i++) liberarAlvo(transferBufferEsquerda.get(i));
            }"""
content = content.replace(transferirAlvosCruzados_search, transferirAlvosCruzados_replace)

# Fix 11: verificarColisoes loops inside the method where quadtree is inserted
verificarColisoes_search = """                    for (Alvo alvo : alvosEsquerdo) {
                        if (alvo.isAtivo()) quadTreeEsquerdo.insert(alvo);
                    }
                    for (Alvo alvo : alvosDireito) {
                        if (alvo.isAtivo()) quadTreeDireito.insert(alvo);
                    }"""
verificarColisoes_replace = """                    for (int i = 0; i < alvosEsquerdo.size(); i++) {
                        Alvo alvo = alvosEsquerdo.get(i);
                        if (alvo.isAtivo()) quadTreeEsquerdo.insert(alvo);
                    }
                    for (int i = 0; i < alvosDireito.size(); i++) {
                        Alvo alvo = alvosDireito.get(i);
                        if (alvo.isAtivo()) quadTreeDireito.insert(alvo);
                    }"""
content = content.replace(verificarColisoes_search, verificarColisoes_replace)

# Fix 12: processarAlvosInativos loop
processarAlvosInativos_search = """        for (Alvo alvo : lista) {
            if (!alvo.isAtivo()) {
                pontos += calcularPontosAbate(alvo);
                energiaRegenerada += calcularEnergiaRegenerada(alvo);
                removidos.add(alvo);
                liberarAlvo(alvo);
            }
        }"""
processarAlvosInativos_replace = """        for (int i = 0; i < lista.size(); i++) {
            Alvo alvo = lista.get(i);
            if (!alvo.isAtivo()) {
                pontos += calcularPontosAbate(alvo);
                energiaRegenerada += calcularEnergiaRegenerada(alvo);
                removidos.add(alvo);
                liberarAlvo(alvo);
            }
        }"""
content = content.replace(processarAlvosInativos_search, processarAlvosInativos_replace)

# Fix 13: pararTodasThreads loops
pararTodasThreads_search = """    private void pararTodasThreads() {
        for (Alvo a : getAllAlvos()) {
            a.setAtivo(false);
            interromperEEsperar(a, 200);
        }
        for (Canhao c : getAllCanhoes()) {
            pararCanhaoComJoin(c, 400);
        }"""
pararTodasThreads_replace = """    private void pararTodasThreads() {
        for (int i = 0; i < alvosEsquerdo.size(); i++) {
            Alvo a = alvosEsquerdo.get(i);
            a.setAtivo(false);
            interromperEEsperar(a, 200);
        }
        for (int i = 0; i < alvosDireito.size(); i++) {
            Alvo a = alvosDireito.get(i);
            a.setAtivo(false);
            interromperEEsperar(a, 200);
        }
        synchronized (canhoesLock) {
            for (int i = 0; i < canhoesEsquerdo.size(); i++) {
                pararCanhaoComJoin(canhoesEsquerdo.get(i), 400);
            }
            for (int i = 0; i < canhoesDireito.size(); i++) {
                pararCanhaoComJoin(canhoesDireito.get(i), 400);
            }
        }"""
content = content.replace(pararTodasThreads_search, pararTodasThreads_replace)


# Fix 14: notificarAlvosAtivos
notificarAlvosAtivos_search = """    private void notificarAlvosAtivos() {
        if (listener != null) {
            int ativos = 0;
            for (Alvo a : getAllAlvos()) { if (a.isAtivo()) ativos++; }
            listener.onAlvosAtivosAtualizado(ativos);
        }
    }"""
notificarAlvosAtivos_replace = """    private void notificarAlvosAtivos() {
        if (listener != null) {
            int ativos = 0;
            for (int i = 0; i < alvosEsquerdo.size(); i++) { if (alvosEsquerdo.get(i).isAtivo()) ativos++; }
            for (int i = 0; i < alvosDireito.size(); i++) { if (alvosDireito.get(i).isAtivo()) ativos++; }
            listener.onAlvosAtivosAtualizado(ativos);
        }
    }"""
content = content.replace(notificarAlvosAtivos_search, notificarAlvosAtivos_replace)

# Fix 15: getAllCanhoes and getAllAlvos with synchronization
getAllCanhoes_search = """    public List<Canhao> getAllCanhoes() {
        List<Canhao> list = new ArrayList<>();
        list.addAll(canhoesEsquerdo);
        list.addAll(canhoesDireito);
        return list;
    }"""
getAllCanhoes_replace = """    public List<Canhao> getAllCanhoes() {
        List<Canhao> list = new ArrayList<>();
        synchronized (canhoesLock) {
            list.addAll(canhoesEsquerdo);
            list.addAll(canhoesDireito);
        }
        return list;
    }"""
content = content.replace(getAllCanhoes_search, getAllCanhoes_replace)

# Fix 16: Jogo() iniciar() starting existing cannons:
iniciar_canhoes_search = """        // Iniciar threads dos canhões existentes
        for (Canhao c : getAllCanhoes()) {
            if (!c.isAlive()) c.start();
        }"""
iniciar_canhoes_replace = """        // Iniciar threads dos canhões existentes
        synchronized (canhoesLock) {
            for (int i = 0; i < canhoesEsquerdo.size(); i++) {
                Canhao c = canhoesEsquerdo.get(i);
                if (!c.isAlive()) c.start();
            }
            for (int i = 0; i < canhoesDireito.size(); i++) {
                Canhao c = canhoesDireito.get(i);
                if (!c.isAlive()) c.start();
            }
        }"""
content = content.replace(iniciar_canhoes_search, iniciar_canhoes_replace)

# Fix 17: registrarMetricasEstruturadas:
registrarMetricas_search = """    public void registrarMetricasEstruturadas() {
        int nCanhoesEsq = canhoesEsquerdo.size();
        int nCanhoeDir = canhoesDireito.size();"""
registrarMetricas_replace = """    public void registrarMetricasEstruturadas() {
        int nCanhoesEsq;
        int nCanhoeDir;
        synchronized (canhoesLock) {
            nCanhoesEsq = canhoesEsquerdo.size();
            nCanhoeDir = canhoesDireito.size();
        }"""
content = content.replace(registrarMetricas_search, registrarMetricas_replace)

with open('app/src/main/java/com/autotarget/engine/Jogo.java', 'w') as f:
    f.write(content)
