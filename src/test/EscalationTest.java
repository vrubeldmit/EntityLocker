package test;

import entity_locker.EntityLocker;
import org.junit.Test;

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
        AtomicInteger count = new AtomicInteger(0);

        for (int i = 0; i< 10; i++){
            l.lock(i);
        }
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
    }

    @Test
    public void escalationTest3() throws InterruptedException {
        EntityLocker<Integer> l = new EntityLocker<>(true);
        AtomicInteger count = new AtomicInteger(0);

        for (int i = 0; i< 10; i++){
            l.lock(i);
        }
        System.out.println("Escalated locks finished");

        new Thread(()->{
            l.globalLock();
            System.out.println("Global lock acquired");

            count.incrementAndGet();
            try {
                sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("Global unlock");
            l.globalUnlock();

        }).start();

        sleep(100);

        new Thread(()->{
            l.lock(11);
            System.out.println("lock 11 acquired");

            count.incrementAndGet();

            System.out.println("unlock 11");
            l.unlock(11);
        }).start();
        sleep(100);
        assertEquals(0, count.intValue());
        for (int i = 0; i< 10; i++){
            l.unlock(i);
        }
        System.out.println("escalated unlock");
        sleep(100);
        assertEquals(1, count.intValue());
        sleep(2000);
        assertEquals(2, count.intValue());

    }
}
