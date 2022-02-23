package test;

import entity_locker.EntityLocker;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class ReentrantTest {
    @Test
    public void reentrantLockTest() throws InterruptedException {
        EntityLocker<Integer> locker = new EntityLocker<>(true);
        int id = 1;
        locker.lock(id);
        Thread.sleep(100);
        locker.lock(id);
        Thread.sleep(100);
        assertEquals(1,locker.locksNumber());
        locker.unlock(id);
        assertEquals(1,locker.locksNumber());
        locker.unlock(id);
        assertEquals(0,locker.locksNumber());
    }

    @Test
    public void reentrantLockForbiddenTest() {
        EntityLocker<Integer> locker = new EntityLocker<>(false);
        int id = 1;
        locker.lock(id);

        assertThrows(RuntimeException.class, () -> {
            locker.lock(id);
        });
    }
}
