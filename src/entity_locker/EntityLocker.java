package entity_locker;

import java.util.*;

public class EntityLocker<T> {

    public static final int NO_TIMEOUT = -1;

    private final boolean allowReentrantLock;
    private final Map<T, Lock<T>> locks;
    private final Object accessLock = new Object();
    private Timer expirationTimer;

    private Lock<T> globalLock;

    private int locksUntilEscalation = 5;
    private int escalatedLocksCount = 0;
    private int expirationTasksCount = 0;

    public EntityLocker(boolean allowReentrantLock) {
        this.locks = new HashMap<>();
        this.allowReentrantLock = allowReentrantLock;
    }

    public EntityLocker(){
        this(false);
    }

    public void setLocksUntilEscalation(int limit){
        locksUntilEscalation = limit;
    }

    public int locksNumber(){
        return locks.size()+(globalLock==null? 0 : 1);
    }

    private boolean escalationNeeded(){
        return locksUntilEscalation>0 && globalLock == null && locks.values().stream()
                .filter(l->l.getLocker()==Thread.currentThread()).count()>locksUntilEscalation;
    }

    public void lock(T id, int timeout){
        if(id == null){
            throw new NullPointerException("Id cannot be null");
        }
        boolean lockAcquired = false;
        Lock<T> lock;
        Thread thread = Thread.currentThread();
        synchronized (accessLock){

            lock = locks.get(id);
            if(lock != null){
                if(lock.getLocker() == thread){
                    if(allowReentrantLock) {
                        lock.incrementLocksCount();
                        lockAcquired = true;
                    } else {
                        throw new RuntimeException("Cannot lock second time from a single thread if reentrant is forbidden");
                    }
                }
            } else {
                if(globalLock == null) {
                    lock = new Lock<>(id);
                    lockAcquired = true;
                } else if(globalLock.getLocker() == thread){
                    lock = new Lock<>(id);
                    lock.incrementLocksCount();
                    lockAcquired = true;
                } else {
                    lock = new Lock<>(id, globalLock.getLocker());
                    for (Thread t: globalLock.queue()){
                        lock.enqueue(t);
                    }
                }
                locks.put(id, lock);
            }
            if(lockAcquired && timeout!=NO_TIMEOUT){
                if(expirationTimer == null){
                    this.expirationTimer = new Timer();
                }
                scheduleExpiration(lock, timeout);
            }

            if(escalatedLocksCount>0 && thread == globalLock.getLocker()){
                escalatedLocksCount++;
            }

            if(lockAcquired && escalationNeeded()){
                globalLock = new Lock<>(null);
                escalatedLocksCount = locks.size();
            }

        }
        if(!lockAcquired ) {
            lock.syncEnqueueAndWait();
        }
    }


    public void lock(T id){
        lock(id, NO_TIMEOUT);
    }

    public void unlock(T id){
        synchronized (accessLock){
            Thread thread = Thread.currentThread();

            Lock<T> lock = locks.get(id);
            if(lock == null ){
                System.err.println("Lock not found or expired. Id: " + id);
                return;
            }
            if(lock.getLocker() != thread){
                if(lock.expired(thread)){
                    System.err.println("Lock expired. Id: " + id);
                    return;
                } else {
                    throw new RuntimeException("Cannot unlock entity from not owning thread");
                }
            }
            if(lock.cancelExpireTask()){
                expirationTasksCount--;
            }
            unlockInternal(lock);
            if(escalatedLocksCount>0 && globalLock.getLocker() == thread){
                if(--escalatedLocksCount==0){
                    globalUnlockInternal(thread);
                }
            }
        }

    }

    public void globalLock(){
        globalLock(NO_TIMEOUT);
    }

    public void globalLock(int timeout)  {
        boolean globalLockAcquired = false;
        boolean needEnqueue = false;
        synchronized (accessLock){
            if(globalLock != null){
                needEnqueue = true;
            } else {
                globalLock = new Lock<>(null);
                if(locks.isEmpty()){
                    globalLockAcquired = true;
                }
            }
            if(!globalLockAcquired){
                // TODO potential deadlock
                locks.values().forEach(Lock::enqueue);
            }
        }
        if(!globalLockAcquired){
            if(needEnqueue){
                globalLock.syncEnqueueAndWait();
            } else {
                globalLock.syncWait();
            }
        } else if( timeout!=NO_TIMEOUT){
            if(expirationTimer == null){
                this.expirationTimer = new Timer();
            }
            scheduleExpirationGlobal(globalLock, timeout);
        }
    }

    public void globalUnlock(){
        synchronized (accessLock){
            if(globalLock == null ){
                System.err.println("Global lock not found or expired.");
                return;
            }
            Thread thread = Thread.currentThread();
            if(globalLock.getLocker() != thread){
                if(globalLock.expired(thread)){
                    System.err.println("Global lock not found or expired.");
                    return;
                } else {
                    throw new RuntimeException("Cannot unlock entity from not owning thread");
                }
            }

            if(globalLock.cancelExpireTask()){
                expirationTasksCount--;
            }
            globalUnlockInternal(thread);
        }
    }

    private void globalUnlockInternal(Thread thread) {
        if(globalLock.decrementLocksCount() == 0){
            if(globalLock.queueSize() == 0) {
                globalLock.reset();
                globalLock = null;
            } else {
                globalLock.updateLocker();
            }
            Iterator<Map.Entry<T, Lock<T>>> it = locks.entrySet().iterator();
            while (it.hasNext()){
                Lock<T> lock = it.next().getValue();
                if(lock.getLocker() == thread) {
                    if(lock.decrementLocksCount() == 0){
                        if(lock.queueSize() == 0){
                            it.remove();
                        } else {
                            lock.updateLocker();
                        }
                    }
                }
            }
            if(globalLock == null) {

                for (Lock<T> l : locks.values()) {
                    l.syncNotify();
                }
            } else if (!checkGlobalLock()){
                locks.values().stream().filter(l->l.getLocker() != globalLock.getLocker()).forEach(Lock::syncNotify);
            }
        }
        disableTimer();
    }

    private boolean checkGlobalLock() {
        synchronized (accessLock) {
            boolean haveAllLocks = locks.values().stream().allMatch(l -> l.getLocker() == globalLock.getLocker());
            if (haveAllLocks) {
                globalLock.syncNotify();
            }
            return haveAllLocks;
        }
    }

    private void unlockInternal(Lock<T> lock) {
        T id = lock.getId();
        if(lock.decrementLocksCount() == 0){
            if(lock.queueSize() == 0) {
                lock.reset();
                locks.remove(id);
            } else {
                lock.updateLocker();
            }
            if(globalLock == null || lock.getLocker() != globalLock.getLocker() || !checkGlobalLock()) {
                lock.syncNotify();
            }
        }

        disableTimer();
    }

    private void disableTimer() {
        if(expirationTimer!= null  && expirationTasksCount == 0){
            expirationTimer.cancel();
            expirationTimer = null;
        }
    }

    private void scheduleExpiration(Lock<T> lock, int timeout){
        LockExpireTask task = new LockExpireTask(Thread.currentThread(), lock);
        lock.addExpirationTask(task);
        expirationTimer.schedule(task, timeout);
    }

    private void scheduleExpirationGlobal(Lock<T> lock, int timeout){
        GlobalLockExpireTask task = new GlobalLockExpireTask(Thread.currentThread());
        lock.addExpirationTask(task);
        expirationTimer.schedule(task, timeout);
    }

    private class LockExpireTask extends TimerTask {
        private final Thread thread;
        private final Lock<T> lock;

        public LockExpireTask(Thread thread, Lock<T> lock) {
            expirationTasksCount++;
            this.thread = thread;
            this.lock = lock;
        }

        public void run(){
            synchronized (accessLock) {
                expirationTasksCount--;
                System.err.println("Lock expired for thread " + thread.getId() + " for lock id " + lock.getId());
                lock.lockExpired(thread);
                unlockInternal(lock);
            }
        }
    }

    private class GlobalLockExpireTask extends TimerTask {
        private final Thread thread;

        public GlobalLockExpireTask(Thread thread) {
            this.thread = thread;
            expirationTasksCount++;
        }

        public void run(){
            synchronized (accessLock) {
                expirationTasksCount--;
                System.err.println("Global lock expired for thread " + thread.getId());
                globalLock.lockExpired(thread);
                globalUnlockInternal(thread);
            }
        }
    }
}
