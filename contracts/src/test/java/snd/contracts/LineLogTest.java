package snd.contracts;

import org.junit.jupiter.api.Test;
import snd.contracts.util.LineLog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LineLogTest {
    @Test
    void rendersWithCursorAndIndices() {
        LineLog log = new LineLog(10);
        log.add("one");
        log.add("two");
        assertEquals("cursor: 2\n0: one\n1: two\n", log.render(0));
        assertEquals("cursor: 2\n1: two\n", log.render(1));
        assertEquals("cursor: 2\n", log.render(2));
    }

    @Test
    void ringDropsOldestButKeepsIndices() {
        LineLog log = new LineLog(2);
        log.add("a");
        log.add("b");
        log.add("c");
        assertEquals("cursor: 3\n1: b\n2: c\n", log.render(0));
    }

    @Test
    void waitForNewReturnsImmediatelyWhenAvailable() {
        LineLog log = new LineLog(4);
        log.add("x");
        assertTrue(log.waitForNew(0, 1));
    }

    @Test
    void waitForNewTimesOut() {
        LineLog log = new LineLog(4);
        assertFalse(log.waitForNew(0, 30));
    }

    @Test
    void waitForNewWakesOnAdd() throws Exception {
        final LineLog log = new LineLog(4);
        Thread writer = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {
                }
                log.add("late");
            }
        });
        writer.start();
        assertTrue(log.waitForNew(0, 2000));
        writer.join();
    }
}
