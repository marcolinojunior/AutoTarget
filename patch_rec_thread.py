with open('app/src/main/java/com/autotarget/service/ReconciliacaoThread.java', 'r') as f:
    content = f.read()

search_1 = """    private Canhao obterUltimoCanhaoAtivo(Lado lado) {
        List<Canhao> lista = lado == Lado.ESQUERDO ? jogo.getCanhoesEsquerdo() : jogo.getCanhoesDireito();
        for (int i = lista.size() - 1; i >= 0; i--) {
            Canhao c = lista.get(i);
            if (c.isAtivo()) return c;
        }
        return null;
    }"""

replace_1 = """    private Canhao obterUltimoCanhaoAtivo(Lado lado) {
        synchronized(jogo.getCanhoesLock()) {
            List<Canhao> lista = lado == Lado.ESQUERDO ? jogo.getCanhoesEsquerdo() : jogo.getCanhoesDireito();
            for (int i = lista.size() - 1; i >= 0; i--) {
                Canhao c = lista.get(i);
                if (c.isAtivo()) return c;
            }
        }
        return null;
    }"""

content = content.replace(search_1, replace_1)


search_2 = """    private List<Canhao> coletarCanhoesAtivos(Lado lado) {
        java.util.List<Canhao> origem = lado == Lado.ESQUERDO ? jogo.getCanhoesEsquerdo() : jogo.getCanhoesDireito();
        List<Canhao> canhoes = new ArrayList<>(Math.max(4, origem.size()));
        for (Canhao c : origem) {
            if (c != null && c.isAtivo()) canhoes.add(c);
        }
        return canhoes;
    }"""

replace_2 = """    private List<Canhao> coletarCanhoesAtivos(Lado lado) {
        List<Canhao> canhoes;
        synchronized(jogo.getCanhoesLock()) {
            java.util.List<Canhao> origem = lado == Lado.ESQUERDO ? jogo.getCanhoesEsquerdo() : jogo.getCanhoesDireito();
            canhoes = new ArrayList<>(Math.max(4, origem.size()));
            for (int i = 0; i < origem.size(); i++) {
                Canhao c = origem.get(i);
                if (c != null && c.isAtivo()) canhoes.add(c);
            }
        }
        return canhoes;
    }"""

content = content.replace(search_2, replace_2)

with open('app/src/main/java/com/autotarget/service/ReconciliacaoThread.java', 'w') as f:
    f.write(content)
