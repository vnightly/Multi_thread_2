package org.example;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UnsyncBuffer extends BufferWorker {
    // --- общий одноэлементный буфер БЕЗ синхронизации ---
    String buffer = null;     // что лежит в буфере
    boolean empty = true;     // флаг пустоты буфера (намеренно не volatile)
    volatile boolean finish = false; // чтобы корректно завершить читателей
    public UnsyncBuffer (int writersCount, int readersCount, int allMessages, int messageVolume, boolean printStat){
        super(writersCount, readersCount, allMessages, messageVolume, printStat);
    }

    public  void run() throws InterruptedException {
        Thread[] writers = new Thread[writersCount];
        Thread[] readers = new Thread[readersCount];
        int base = allMessages / writersCount;
        int rem = allMessages % writersCount;
        // кто что прочитал (для статистики; это отдельная безопасная структура и
        // не участвует в доступе к буферу — нам важно НЕ синхронизировать сам буфер)
        Map<String, Integer> seen = new ConcurrentHashMap<>();

        // --- писатели ---
        long timeStart = System.currentTimeMillis();

        for (int w = 0; w < writersCount; w++) {
            final int id = w;
            int thStart = w * base + Math.min(w, rem);
            int thEnd = thStart + base + (w < rem ? 1 : 0);
            writers[w] = new Thread(() -> {
                for (int i = thStart; i < thEnd; i++) {
                    // уникальный ID сообщения
                    String msg = "W" + id + "-" + i + "-" + message;

                    // БЕЗ синхронизации: гонка на empty/buffer
                    while (true) {
                        if (empty) {        // увидел пусто — "занимаю" буфер
                            buffer = msg;   // записал
                            empty = false;  // пометил как занятый
                            break;
                        } else {
                            Thread.yield(); // чуть уступим CPU
                        }
                    }
                }
            }, "Writer-" + w);
            writers[w].start();
        }

        // --- читатели ---
        for (int r = 0; r < readersCount; r++) {
            readers[r] = new Thread(() -> {
                while (!finish || !empty) { // пока не пришёл сигнал окончания ИЛИ буфер ещё не пуст
                    if (!empty) {           // увидел заполненный буфер
                        String msg = buffer; // прочитал
                        empty = true;        // освободил
                        if (msg != null) {
                            seen.merge(msg, 1, Integer::sum); // учли сколько раз прочитано
                        }
                    } else {
                        Thread.yield();
                    }
                }
            }, "Reader-" + r);
            readers[r].start();
        }

        for (Thread t : writers) t.join();

        // сообщить читателям, что писатели кончились
        finish = true;

        for (Thread t : readers) t.join();
        long end = System.currentTimeMillis();
        // --- статистика ---
        printStat("UsyncBuffer", seen, timeStart, end);
    }
}
