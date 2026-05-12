/*
 * ============================================================================
 * Arquivo: QuadTree.java
 * Pacote:  com.autotarget.util
 * ============================================================================
 *
 * DESCRIÇÃO TÉCNICA:
 *   Implementação de árvore quaternária (Quadtree) para particionamento
 *   espacial 2D do campo de jogo. Substitui a varredura O(N×M) por
 *   consultas O(N·log M) na detecção de colisões, reduzindo a fração
 *   serial (1-P) da Lei de Amdahl.
 *
 * OTIMIZAÇÃO (AV4 §6.4.2-c):
 *   - Cada nó folha possui seu próprio lock para colisão por quadrante
 *   - Reconstruída a cada frame do PhysicsTimer
 *   - Capacidade: 4 entidades por nó, profundidade máxima: 6
 *
 * ============================================================================
 */
package com.autotarget.util;

import android.graphics.RectF;
import com.autotarget.model.Alvo;

import java.util.ArrayList;
import java.util.List;

/**
 * Quadtree para particionamento espacial eficiente.
 */
public class QuadTree {

    private static final int MAX_OBJECTS = 4;
    private static final int MAX_LEVELS = 6;

    private final int level;
    private final List<Alvo> objects;
    private final RectF bounds;
    private final QuadTree[] nodes;

    /** Lock para colisão neste quadrante. */
    public final Object quadrantLock = new Object();

    /**
     * Cria um QuadTree com os limites especificados.
     *
     * @param level  nível de profundidade (0 = raiz)
     * @param bounds retângulo delimitador deste nó
     */
    public QuadTree(int level, RectF bounds) {
        this.level = level;
        this.objects = new ArrayList<>();
        this.bounds = bounds;
        this.nodes = new QuadTree[4];
    }

    /**
     * Limpa todos os objetos e subnós do quadtree.
     */
    public void clear() {
        objects.clear();
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i] != null) {
                nodes[i].clear();
                nodes[i] = null;
            }
        }
    }

    /**
     * Subdivide este nó em 4 quadrantes filhos.
     */
    private void split() {
        float midX = bounds.centerX();
        float midY = bounds.centerY();

        // NE (0), NW (1), SW (2), SE (3)
        nodes[0] = new QuadTree(level + 1, new RectF(midX, bounds.top, bounds.right, midY));
        nodes[1] = new QuadTree(level + 1, new RectF(bounds.left, bounds.top, midX, midY));
        nodes[2] = new QuadTree(level + 1, new RectF(bounds.left, midY, midX, bounds.bottom));
        nodes[3] = new QuadTree(level + 1, new RectF(midX, midY, bounds.right, bounds.bottom));
    }

    /**
     * Determina em qual quadrante um ponto se encaixa.
     *
     * @return índice do quadrante (0-3) ou -1 se não couber em nenhum
     */
    private int getIndex(float x, float y, float radius) {
        float midX = bounds.centerX();
        float midY = bounds.centerY();

        boolean topQuad = (y - radius >= bounds.top && y + radius <= midY);
        boolean bottomQuad = (y - radius >= midY && y + radius <= bounds.bottom);
        boolean leftQuad = (x - radius >= bounds.left && x + radius <= midX);
        boolean rightQuad = (x - radius >= midX && x + radius <= bounds.right);

        if (topQuad) {
            if (rightQuad) return 0;
            if (leftQuad) return 1;
        } else if (bottomQuad) {
            if (leftQuad) return 2;
            if (rightQuad) return 3;
        }
        return -1; // Não cabe inteiramente em nenhum quadrante
    }

    /**
     * Insere um alvo na quadtree.
     */
    public void insert(Alvo alvo) {
        if (!alvo.isAtivo()) return;

        if (nodes[0] != null) {
            int index = getIndex(alvo.getX(), alvo.getY(), alvo.getRaio());
            if (index != -1) {
                nodes[index].insert(alvo);
                return;
            }
        }

        objects.add(alvo);

        if (objects.size() > MAX_OBJECTS && level < MAX_LEVELS) {
            if (nodes[0] == null) {
                split();
            }
            int i = 0;
            while (i < objects.size()) {
                Alvo obj = objects.get(i);
                int index = getIndex(obj.getX(), obj.getY(), obj.getRaio());
                if (index != -1) {
                    objects.remove(i);
                    nodes[index].insert(obj);
                } else {
                    i++;
                }
            }
        }
    }

    /**
     * Retorna todos os alvos que poderiam colidir com um objeto na posição dada.
     *
     * @param x      posição X do objeto de consulta
     * @param y      posição Y do objeto de consulta
     * @param radius raio do objeto de consulta
     * @return lista de alvos candidatos a colisão
     */
    public List<Alvo> query(float x, float y, float radius) {
        List<Alvo> result = new ArrayList<>();
        query(x, y, radius, result);
        return result;
    }

    private void query(float x, float y, float radius, List<Alvo> result) {
        int index = getIndex(x, y, radius);

        if (index != -1 && nodes[0] != null) {
            nodes[index].query(x, y, radius, result);
        } else if (nodes[0] != null) {
            // Objeto cruza limites — verificar todos os quadrantes
            for (QuadTree node : nodes) {
                if (node != null) {
                    node.query(x, y, radius, result);
                }
            }
        }

        result.addAll(objects);
    }

    /**
     * Retorna todos os alvos contidos em um retângulo.
     */
    public List<Alvo> queryRange(RectF range) {
        List<Alvo> found = new ArrayList<>();
        if (!RectF.intersects(bounds, range)) {
            return found;
        }

        for (Alvo obj : objects) {
            if (range.contains(obj.getX(), obj.getY())) {
                found.add(obj);
            }
        }

        if (nodes[0] != null) {
            for (QuadTree node : nodes) {
                found.addAll(node.queryRange(range));
            }
        }

        return found;
    }
}
