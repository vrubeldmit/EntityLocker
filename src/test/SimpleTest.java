package test;

import entity_locker.EntityLocker;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class SimpleTest {
    @Test
    public void simpleTest(){
        EntityLocker<Integer> locker = new EntityLocker();
        assertEquals(0,locker.locksNumber());
    }

    @Test
    public void lockUnlockTest() throws InterruptedException {
        EntityLocker<Integer> locker = new EntityLocker();
        int id = 1;
        locker.lock(id);
        Thread.sleep(100);
        assertEquals(1,locker.locksNumber());
        locker.unlock(id);
        assertEquals(0,locker.locksNumber());
    }


}
