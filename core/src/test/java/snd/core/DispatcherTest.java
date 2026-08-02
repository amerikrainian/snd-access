package snd.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DispatcherTest {
    @AfterEach
    void cleanUp() {
        Dispatcher.swap(null);
        Dispatcher.setFrameHook(null);
    }

    static class CountingModule implements ModModule {
        int ticks;

        @Override
        public void load(HostServices host) {
        }

        @Override
        public void tick() {
            ticks++;
        }

        @Override
        public void dispose() {
        }
    }

    @Test
    void jobsRunBeforeTickAndOnlyOnce() {
        final List<String> order = new ArrayList<>();
        ModModule m = new ModModule() {
            @Override
            public void load(HostServices host) {
            }

            @Override
            public void tick() {
                order.add("tick");
            }

            @Override
            public void dispose() {
            }
        };
        Dispatcher.swap(m);
        Dispatcher.post(new Runnable() {
            @Override
            public void run() {
                order.add("job");
            }
        });
        Dispatcher.frame();
        Dispatcher.frame();
        assertEquals(List.of("job", "tick", "tick"), order);
    }

    @Test
    void swapReplacesModuleAtomically() {
        CountingModule a = new CountingModule();
        CountingModule b = new CountingModule();
        Dispatcher.swap(a);
        Dispatcher.frame();
        Dispatcher.swap(b);
        Dispatcher.frame();
        assertEquals(1, a.ticks);
        assertEquals(1, b.ticks);
    }

    @Test
    void throwingTickDoesNotKillTheFrame() {
        ModModule bad = new ModModule() {
            @Override
            public void load(HostServices host) {
            }

            @Override
            public void tick() {
                throw new IllegalStateException("boom");
            }

            @Override
            public void dispose() {
            }
        };
        Dispatcher.swap(bad);
        final AtomicInteger hookRuns = new AtomicInteger();
        Dispatcher.setFrameHook(new Runnable() {
            @Override
            public void run() {
                hookRuns.incrementAndGet();
            }
        });
        Dispatcher.frame();
        Dispatcher.frame();
        assertEquals(2, hookRuns.get());
    }

    @Test
    void waitRemovedWhenSatisfied() {
        final AtomicInteger polls = new AtomicInteger();
        final AtomicInteger satisfied = new AtomicInteger();
        Dispatcher.addWait("w", new Dispatcher.FrameWait() {
            @Override
            public boolean poll() {
                return polls.incrementAndGet() >= 2;
            }

            @Override
            public void satisfied() {
                satisfied.incrementAndGet();
            }
        });
        Dispatcher.frame();
        assertEquals(0, satisfied.get());
        Dispatcher.frame();
        assertEquals(1, satisfied.get());
        Dispatcher.frame();
        assertEquals(2, polls.get());
        assertNull(null);
    }
}
