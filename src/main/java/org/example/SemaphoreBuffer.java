package org.example;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;


public class SemaphoreBuffer extends BufferWorker{
    volatile String buffer = null;
    volatile boolean finish = false;

    //семафоры:
    //semEmpty = 1 (первоначально буфер пуст)
    //semFull = 0 (данных пока нет)
    final Semaphore semEmpty = new Semaphore(1);
    final Semaphore semFull = new Semaphore(0);

    //параметры
    
    
    public SemaphoreBuffer (int writersCount, int readersCount, int allMessages, int messageVolume, boolean printStat){
        super(writersCount, readersCount, allMessages, messageVolume,printStat);
    }
    public SemaphoreBuffer (int writersCount, int readersCount, int allMessages, int messageVolume){
        super(writersCount, readersCount, allMessages, messageVolume);
    }


    public void run() throws InterruptedException {
        Thread[] writers = new Thread[writersCount];
        Thread[] readers = new Thread[readersCount];
        int base = allMessages / writersCount;
        int rem = allMessages % writersCount;
        Map<String, Integer> seen = new ConcurrentHashMap<>();

        long timeStart = System.currentTimeMillis();

        for (int w = 0; w < writersCount; w++){
            final int id = w;
            int thStart = w * base + Math.min(w, rem);
            int thEnd = thStart + base + (w < rem ? 1 : 0);
            writers[w] = new Thread(() -> {
                for (int i = thStart; i < thEnd; i++) {
                    try {
                        //ждем пока буфер освободится
                        semEmpty.acquire();
                        buffer = "W" + id + "-" + i + "-" + message;
                        semFull.release();

                    }catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }, "Writer-" + w);
            writers[w].start();
        }

        for (int r = 0; r < readersCount; r++) {
            readers[r] = new Thread(() -> {
                while (true){
                    try {
                        semFull.acquire();
                        if (finish && buffer == null)
                            break;

                        String msg = buffer;
                        buffer = null;

                        semEmpty.release();

                        if (msg != null) {
                            seen.merge(msg, 1, Integer::sum);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }, "Readers-" + r);
            readers[r].start();
        }

        for (Thread t : writers) t.join();
        finish = true;

        for (int i = 0; i < readersCount; i++){
            semFull.release();
        }
        for (Thread t: readers) t.join();
        long end = System.currentTimeMillis();
        printStat("SemaphoreBuffer", seen, timeStart, end);
    }
}
