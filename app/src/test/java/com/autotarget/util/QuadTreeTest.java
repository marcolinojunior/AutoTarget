package com.autotarget.util;

import org.junit.Test;
import static org.junit.Assert.*;

import android.graphics.RectF;
import com.autotarget.model.Alvo;
import com.autotarget.model.AlvoComum;
import java.util.List;

public class QuadTreeTest {

    @Test
    public void testQuadTreeInsertionAndQuery() {
        // Arrange
        RectF bounds = new RectF(0, 0, 1000, 1000);
        QuadTree qt = new QuadTree(0, bounds);

        Alvo a1 = new AlvoComum(100, 100, 10, 5, 1000, 1000);
        Alvo a2 = new AlvoComum(800, 800, 10, 5, 1000, 1000);
        Alvo a3 = new AlvoComum(150, 150, 10, 5, 1000, 1000);

        // Act
        qt.insert(a1);
        qt.insert(a2);
        qt.insert(a3);

        List<Alvo> queryResult = qt.query(120, 120, 50);

        // Assert
        assertNotNull(queryResult);
        assertTrue(queryResult.contains(a1));
        assertTrue(queryResult.contains(a3));
        assertFalse(queryResult.contains(a2)); // a2 is far away
    }
}
