package entity_locker;

import java.util.*;

class Lock<T> {

    private final T id;
    private Thread locker;
    private int locksCount;

    private final LinkedList<Thread> lockersQueue;
    private final HashSet<Thread> expiredThreads;
    private final List<TimerTask> expirationTasks;

    public Lock(T id, Thread lockThread){
        this.id = id;
        this.locker = lockThread;
        this.locksCount = 1;
        this.lockersQueue = new LinkedList<>();
        this.expiredThreads = new HashSet<>();
        this.expirationTasks = new LinkedList<>();
    }

    public Lock(T id){
        this(id, Thread.currentThread());
    }

    public Thread getLocker(){
        return locker;
    }

    public synchronized int incrementLocksCount(){
        return ++locksCount;
    }

    public synchronized int decrementLocksCount(){
        return --locksCount;
    }

    public synchronized void enqueue() {
        enqueue(Thread.currentThread());
    }
    public synchronized void enqueue(Thread thread) {
        lockersQueue.add(thread);
    }

    public synchronized int queueSize(){
        return lockersQueue.size();
    }

    public synchronized void updateLocker() {
        this.locker = lockersQueue.removeFirst();
        locksCount = 1;
    }

    public void reset() {
        this.locker = null;
    }

    public T getId() {
        return id;
    }

    public synchronized void lockExpired(Thread thread){
        expirationTasks.remove(0);
        expiredThreads.add(thread);
    }

    public synchronized void addExpirationTask(TimerTask task){
        expirationTasks.add(task);
    }

    public synchronized boolean expired(Thread thread){
        return expiredThreads.contains(thread);
    }

    public synchronized boolean cancelExpireTask(){
        if(!expirationTasks.isEmpty()) {
            expirationTasks.remove(0).cancel();
            return true;
        }
        return false;
    }

    public synchronized void removeFromQueue(Thread thread) {
        lockersQueue.remove(thread);
    }

    public void syncWait(){
        synchronized (this){
            try {
                this.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void syncNotify(){
        synchronized (this){
            this.notify();
        }
    }

    public void syncEnqueueAndWait() {
        synchronized (this) {
            try {
                this.enqueue();
                this.wait();
            } catch (InterruptedException e) {
                this.removeFromQueue(Thread.currentThread());
                e.printStackTrace();
            }
        }
    }
}
