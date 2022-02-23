package test;

import static org.junit.Assert.assertEquals;

import entity_locker.EntityLocker;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class TimeoutTest {

    @Test
    public void timeoutTestExpired() throws InterruptedException {
        EntityLocker<Integer> l = new EntityLocker<>();
        int id = 1;
        l.lock(id, 500);
        ExecutorService service = Executors.newFixedThreadPool(1);
        AtomicInteger count = new AtomicInteger(0);
        service.execute(()->{
            l.lock(id);
            count.incrementAndGet();
            l.unlock(id);
        });
        Thread.sleep(100);
        assertEquals(0, count.intValue());
        Thread.sleep(1000);
        assertEquals(1, count.intValue());
        l.unlock(id);
    }

    @Test
    public void timeoutTestNotExpired() throws InterruptedException {
        EntityLocker<Integer> l = new EntityLocker<>();
        int id = 1;
        l.lock(id, 1000);
        ExecutorService service = Executors.newFixedThreadPool(1);
        AtomicInteger count = new AtomicInteger(0);
        service.execute(()->{
            l.lock(id);
            count.incrementAndGet();
            l.unlock(id);
        });
        Thread.sleep(100);
        assertEquals(0, count.intValue());
        l.unlock(id);

        Thread.sleep(1000);
        assertEquals(1, count.intValue());
    }

    @Test
    public void globalLockTimeoutTest() throws InterruptedException {
        EntityLocker<Integer> l = new EntityLocker<>();
        int id = 1;
        l.globalLock(500);
        ExecutorService service = Executors.newFixedThreadPool(1);
        AtomicInteger count = new AtomicInteger(0);
        service.execute(()->{
            l.lock(id);
            count.incrementAndGet();
            l.unlock(id);
        });
        Thread.sleep(100);
        assertEquals(0, count.intValue());
        Thread.sleep(1000);
        assertEquals(1, count.intValue());
        l.globalUnlock();
    }
}
