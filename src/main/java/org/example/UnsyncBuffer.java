package org.example;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UnsyncBuffer {
    // --- общий одноэлементный буфер БЕЗ синхронизации ---
    static String buffer = null;     // что лежит в буфере
    static boolean empty = true;     // флаг пустоты буфера (намеренно не volatile)
    static volatile boolean finish = false; // чтобы корректно завершить читателей

    // параметры прогона
    static final int WRITERS = 4;
    static final int READERS = 4;
    static final int MESSAGES_PER_WRITER = 10_000;

    public static void main(String[] args) throws InterruptedException {
        Thread[] writers = new Thread[WRITERS];
        Thread[] readers = new Thread[READERS];

        // кто что прочитал (для статистики; это отдельная безопасная структура и
        // не участвует в доступе к буферу — нам важно НЕ синхронизировать сам буфер)
        Map<String, Integer> seen = new ConcurrentHashMap<>();

        // --- писатели ---
        for (int w = 0; w < WRITERS; w++) {
            final int id = w;
            writers[w] = new Thread(() -> {
                for (int i = 1; i <= MESSAGES_PER_WRITER; i++) {
                    // уникальный ID сообщения
                    String msg = "W" + id + "-" + i;

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
        for (int r = 0; r < READERS; r++) {
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

        // дождаться писателей
        for (Thread t : writers) t.join();

        // сообщить читателям, что писатели кончились
        finish = true;

        // дождаться читателей
        for (Thread t : readers) t.join();

        // --- статистика ---
        int expected = WRITERS * MESSAGES_PER_WRITER;
        long distinctRead = seen.size();
        long totalRead = seen.values().stream().mapToLong(Integer::longValue).sum();
        long duplicates = totalRead - distinctRead;    // сколько раз одно и то же считали несколькими читателями
        long lost = expected - distinctRead;           // сколько сообщений вообще не дошло до читателей

        System.out.println("=== Итоги без синхронизации ===");
        System.out.println("Ожидалось сообщений:     " + expected);
        System.out.println("Прочитано всего (включая повторы): " + totalRead);
        System.out.println("Уникально прочитано:     " + distinctRead);
        System.out.println("ДУБЛИКАТЫ:               " + duplicates);
        System.out.println("ПОТЕРИ:                  " + lost);
    }
}
