with open('app/src/main/java/com/autotarget/engine/Jogo.java', 'r') as f:
    content = f.read()

# Fix 1: desativarUltimoCanhao
search_desativar = """    private void desativarUltimoCanhao(Lado lado) {
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
replace_desativar = """    private void desativarUltimoCanhao(Lado lado) {
        List<Canhao> lista = (lado == Lado.ESQUERDO) ? canhoesEsquerdo : canhoesDireito;
        Canhao cParaParar = null;
        synchronized (canhoesLock) {
            for (int i = lista.size() - 1; i >= 0; i--) {
                Canhao c = lista.get(i);
                if (c.isAtivo()) {
                    cParaParar = c;
                    break;
                }
            }
        }
        if (cParaParar != null) {
            pararCanhaoComJoin(cParaParar, 300);
        }
    }"""
content = content.replace(search_desativar, replace_desativar)

# Fix 2: pararTodasThreads (canhões part)
search_parar = """        synchronized (canhoesLock) {
            for (int i = 0; i < canhoesEsquerdo.size(); i++) {
                pararCanhaoComJoin(canhoesEsquerdo.get(i), 400);
            }
            for (int i = 0; i < canhoesDireito.size(); i++) {
                pararCanhaoComJoin(canhoesDireito.get(i), 400);
            }
        }"""
replace_parar = """        List<Canhao> canhoesParaParar = new ArrayList<>();
        synchronized (canhoesLock) {
            canhoesParaParar.addAll(canhoesEsquerdo);
            canhoesParaParar.addAll(canhoesDireito);
        }
        for (Canhao c : canhoesParaParar) {
            pararCanhaoComJoin(c, 400);
        }"""
content = content.replace(search_parar, replace_parar)

# Fix 3: pararTodasThreads (alvos part)
search_parar_alvos = """    private void pararTodasThreads() {
        for (int i = 0; i < alvosEsquerdo.size(); i++) {
            Alvo a = alvosEsquerdo.get(i);
            a.setAtivo(false);
            interromperEEsperar(a, 200);
        }
        for (int i = 0; i < alvosDireito.size(); i++) {
            Alvo a = alvosDireito.get(i);
            a.setAtivo(false);
            interromperEEsperar(a, 200);
        }"""
replace_parar_alvos = """    private void pararTodasThreads() {
        List<Alvo> alvosParaParar = new ArrayList<>();
        synchronized(listLock) {
            alvosParaParar.addAll(alvosEsquerdo);
            alvosParaParar.addAll(alvosDireito);
        }
        for (Alvo a : alvosParaParar) {
            a.setAtivo(false);
            interromperEEsperar(a, 200);
        }"""
content = content.replace(search_parar_alvos, replace_parar_alvos)

with open('app/src/main/java/com/autotarget/engine/Jogo.java', 'w') as f:
    f.write(content)
