package org.example;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AutoResetEventBuffer extends BufferWorker {

    // одноэлементный буфер
    volatile String buffer = null;
    volatile boolean finish = false;

    // события: одно для "буфер пуст", другое для "буфер полон"
    final AutoResetEvent evEmpty = new AutoResetEvent(true);  // сначала буфер пуст
    final AutoResetEvent evFull  = new AutoResetEvent(false);

    public AutoResetEventBuffer(int writersCount, int readersCount, int allMessages, int messageVolume, boolean printStat){
        super(writersCount, readersCount, allMessages, messageVolume,printStat);
    }
    public AutoResetEventBuffer(int writersCount, int readersCount, int allMessages, int messageVolume){
        super(writersCount, readersCount, allMessages, messageVolume);
    }

    @Override
    public void run() throws InterruptedException {
        Thread[] writers = new Thread[writersCount];
        Thread[] readers = new Thread[readersCount];

        Map<String, Integer> seen = new ConcurrentHashMap<>();

        // распределяем allMessages по писателям так же, как в SemaphoreBuffer
        int base = allMessages / writersCount;
        int rem  = allMessages % writersCount;

        long timeStart = System.currentTimeMillis();

        // --- писатели ---
        for (int w = 0; w < writersCount; w++) {
            final int id = w;
            final int thStart = w * base + Math.min(w, rem);
            final int thEnd   = thStart + base + (w < rem ? 1 : 0);

            writers[w] = new Thread(() -> {
                for (int i = thStart; i < thEnd; i++) {
                    String msg = "W" + id + "-" + i + "-" + message;
                    // ждем, пока освободят буфер
                    evEmpty.waitOne();
                    // кладем сообщение
                    buffer = msg;
                    // сигнал "буфер полон" одному читателю
                    evFull.set();
                }
            }, "Writer-" + w);
            writers[w].start();
        }

        // --- читатели ---
        for (int r = 0; r < readersCount; r++) {
            readers[r] = new Thread(() -> {
                while (true) {
                    // перед тем как ждать: если все кончено и буфер пуст - выходим
                    if (finish && buffer == null) {
                        break;
                    }

                    // ждем, пока появится сообщение или сигнал завершения
                    evFull.waitOne();

                    String msg = buffer;

                    if (msg != null) {
                        buffer = null;
                        // после чтения буфер пуст — будим одного писателя
                        evEmpty.set();
                        seen.merge(msg, 1, Integer::sum);
                    } else {
                        // сюда попадаем, когда main/внешний код шлет "пустой" сигнал для остановки
                        if (finish) {
                            break;
                        }
                    }
                }
            }, "Reader-" + r);
            readers[r].start();
        }

        // ждем завершения писателей
        for (Thread t : writers) t.join();

        // сигнал о завершении работы
        finish = true;

        // разбудим всех читателей, которые могут висеть на evFull
        for (int i = 0; i < readersCount; i++) {
            evFull.set();
        }

        // ждем завершения читателей
        for (Thread t : readers) t.join();

        long end = System.currentTimeMillis();

        // статистика AutoResetEventBuffer
        printStat("AutoResetEventBuffer", seen, timeStart, end);
    }



    static class AutoResetEvent {
        // вместо простого флага — счётчик «сигналов», чтобы они не терялись
        private int permits; // 0 = нет сигналов; >0 = есть накопленные сигналы

        public AutoResetEvent(boolean initialState) {
            this.permits = initialState ? 1 : 0;
        }

        public synchronized void waitOne() {
            while (permits == 0) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            // auto-reset: «потребляем» один сигнал
            permits--;
        }

        public synchronized void set() {
            // накапливаем сигнал и будим один ожидающий поток
            permits++;
            notify();
        }
    }



}
