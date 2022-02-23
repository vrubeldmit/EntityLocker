package test;

import entity_locker.EntityLocker;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class GlobalLockTest {

    @Test
    public void globalLockSimpleLockTest() throws InterruptedException {
        EntityLocker<Integer> l = new EntityLocker<>();
        int id = 1;
        l.globalLock();

        ExecutorService service = Executors.newFixedThreadPool(1);
        AtomicInteger count = new AtomicInteger(0);
        service.execute(()->{
            l.lock(id);
            count.incrementAndGet();
            l.unlock(id);
        });
        Thread.sleep(100);
        assertEquals(0, count.intValue());
        l.globalUnlock();
        Thread.sleep(100);
        assertEquals(1, count.intValue());
    }

    @Test
    public void simpleLockGlobalLockTest() throws InterruptedException {
        EntityLocker<Integer> l = new EntityLocker<>();
        int id = 1;
        l.lock(id);

        ExecutorService service = Executors.newFixedThreadPool(1);
        AtomicInteger count = new AtomicInteger(0);
        service.execute(()->{
            l.globalLock();
            count.incrementAndGet();
            l.globalUnlock();
        });
        Thread.sleep(100);
        assertEquals(0, count.intValue());
        l.unlock(id);
        Thread.sleep(100);
        assertEquals(1, count.intValue());
    }


    @Test
    public void concurrentTest() throws InterruptedException {
        EntityLocker<Integer> l = new EntityLocker<>();
        AtomicInteger count = new AtomicInteger(0);
        l.globalLock();

        ExecutorService service = Executors.newFixedThreadPool(1);
        service.execute(()->{
            l.globalLock();
            count.incrementAndGet();
            l.globalUnlock();
        });
        Thread.sleep(100);
        assertEquals(0, count.intValue());
        l.globalUnlock();
        l.globalLock();
        assertEquals(1, count.intValue());
        l.globalUnlock();
        assertEquals(0, l.locksNumber());
    }

}
