package test;

import entity_locker.EntityLocker;
import org.junit.Test;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class ConcurrentTest {

    @Test
    public void multiThreadLock() throws InterruptedException {
        EntityLocker<Integer> l = new EntityLocker();
        int numberOfThreads = 10;

        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        CountDownLatch latch2 = new CountDownLatch(numberOfThreads);

        ExecutorService service = Executors.newFixedThreadPool(numberOfThreads);
        Object lock = new Object();
        synchronized (lock){
            for (int i = 0; i < numberOfThreads; i++) {
                int id = i;
                service.execute(() -> {
                    l.lock(id);
                    latch.countDown();
                    synchronized (lock){
                        l.unlock(id);
                        latch2.countDown();
                    }
                });
            }
            latch.await();
            assertEquals(numberOfThreads, l.locksNumber());
        }
        latch2.await();
        assertEquals(0, l.locksNumber());
    }

    @Test
    public void concurrentTest() throws InterruptedException {
        EntityLocker<Integer> l = new EntityLocker<>();
        int id = 1;
        AtomicInteger count = new AtomicInteger(0);
        l.lock(id);

        ExecutorService service = Executors.newFixedThreadPool(1);
        service.execute(()->{
            l.lock(id);
            count.incrementAndGet();
            l.unlock(id);
        });
        Thread.sleep(100);
        assertEquals(0, count.intValue());
        l.unlock(id);

        l.lock(id);
        assertEquals(1, count.intValue());
        l.unlock(id);
        assertEquals(0, l.locksNumber());
    }

    @Test
    public void differentIdsTest() throws InterruptedException {
        EntityLocker<Integer> l = new EntityLocker<>();
        int id = 1;
        int id2 = 2;
        AtomicInteger count = new AtomicInteger(0);
        l.lock(id);

        ExecutorService service = Executors.newFixedThreadPool(1);
        service.execute(()->{
            l.lock(id2);
            count.incrementAndGet();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            l.unlock(id2);
        });
        Thread.sleep(100);
        assertEquals(1, count.intValue());
        l.unlock(id);
        l.lock(id2);
        l.unlock(id2);
        assertEquals(0, l.locksNumber());
    }

    @Test
    public void loadTest(){
        EntityLocker<Integer> entityLocker = new EntityLocker<>();
        for (int i =0; i<10000; i++) {
            new Thread(() -> {
                int id = new Random().nextInt(100);
                entityLocker.lock(id);

                try {
                    Thread.sleep(0);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                entityLocker.unlock(id);
            }).start();
        }
    }

}
