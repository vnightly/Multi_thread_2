package org.example;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class InterlockedBuffer extends BufferWorker {

    // одноэлементный буфер на атомике
    final AtomicReference<String> buffer = new AtomicReference<>(null);
    volatile boolean finish = false;

    public InterlockedBuffer(int writersCount, int readersCount, int allMessages, int messageVolume, boolean printStat){
        super(writersCount, readersCount, allMessages, messageVolume,printStat);
    }
    public InterlockedBuffer(int writersCount, int readersCount, int allMessages, int messageVolume){
        super(writersCount, readersCount, allMessages, messageVolume);
    }

    @Override
    public void run() throws InterruptedException {
        Thread[] writers = new Thread[writersCount];
        Thread[] readers = new Thread[readersCount];
        Map<String, Integer> seen = new ConcurrentHashMap<>();

        // равномерно размазываем allMessages по писателям
        int base = allMessages / writersCount;
        int rem  = allMessages % writersCount;

        long timeStart = System.currentTimeMillis();

        // --- писатели ---
        for (int w = 0; w < writersCount; w++) {
            final int id = w;
            final int thStart = w * base + Math.min(w, rem);
            final int thEnd   = thStart + base + (w < rem ? 1 : 0);

            writers[w] = new Thread(() -> {
                for (int i = thStart; i < thEnd; ) {
                    String msg = "W" + id + "-" + i + "-" + message;

                    // атомарно занять буфер, если он пуст (null -> msg)
                    if (buffer.compareAndSet(null, msg)) {
                        i++; // продвигаемся только если удалось положить сообщение
                    } else {
                        Thread.yield(); // отдать квант другим потокам
                    }
                }
            }, "Writer-" + w);
            writers[w].start();
        }

        // --- читатели ---
        for (int r = 0; r < readersCount; r++) {
            readers[r] = new Thread(() -> {
                // крутимся, пока либо не пришёл сигнал finish,
                // либо в буфере ещё что-то лежит
                while (!finish || buffer.get() != null) {
                    // атомарно извлечь msg -> null
                    String msg = buffer.getAndSet(null);
                    if (msg != null) {
                        seen.merge(msg, 1, Integer::sum);
                    } else {
                        Thread.yield();
                    }
                }
            }, "Reader-" + r);
            readers[r].start();
        }

        // ждём завершения писателей
        for (Thread t : writers) t.join();
        // сигнал о завершении
        finish = true;
        // ждём читателей
        for (Thread t : readers) t.join();

        long end = System.currentTimeMillis();

        printStat("InterlockedBuffer", seen, timeStart, end);
    }


}
