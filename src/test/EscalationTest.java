package test;

import entity_locker.EntityLocker;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Thread.sleep;
import static org.junit.Assert.assertEquals;

public class EscalationTest {

    @Test
    public void escalationTest() throws InterruptedException {
        EntityLocker<Integer> l = new EntityLocker<>(true);
        for (int i = 0; i< 10; i++){
            l.lock(i);
        }

        sleep(100);

        for (int i = 0; i< 10; i++){
            l.unlock(i);
        }
    }

    @Test
    public void escalationTest2() throws InterruptedException {
        EntityLocker<Integer> l = new EntityLocker<>(true);
        l.setLocksUntilEscalation(5);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger count = new AtomicInteger(0);

        for (int i = 0; i< 10; i++){
            l.lock(i);
        }

        new Thread(()->{
            latch.countDown();
            l.lock(11);
            count.incrementAndGet();
            l.unlock(11);

        }).start();
        latch.await();
        sleep(100);
        assertEquals(0, count.intValue());
        for (int i = 0; i< 10; i++){
            l.unlock(i);
        }
        l.lock(11);
        l.unlock(11);
        assertEquals(1, count.intValue());
    }

    @Test
    public void escalationTest3() throws InterruptedException {
        EntityLocker<Integer> l = new EntityLocker<>(true);
        l.setLocksUntilEscalation(5);
        AtomicInteger count = new AtomicInteger(0);
        for (int i = 0; i< 10; i++){
            l.lock(i);
        }
        new Thread(()->{
            l.globalLock();
            count.incrementAndGet();
            try {
                sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            l.globalUnlock();

        }).start();
        sleep(100);
        new Thread(()->{
            l.lock(11);

            count.incrementAndGet();

            l.unlock(11);
        }).start();
        sleep(100);
        assertEquals(0, count.intValue());
        for (int i = 0; i< 10; i++){
            l.unlock(i);
        }
        sleep(100);
        assertEquals(1, count.intValue());
        sleep(2000);
        assertEquals(2, count.intValue());
    }
}
