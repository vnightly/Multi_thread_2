package org.example;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LockBuffer extends BufferWorker{
    //общий одноэлементный буфер
    String buffer = null;
    volatile boolean empty = true;
    volatile boolean finish = false;

    //один общий замок для согласования доступа
    final Object lock = new Object();

    //параметры
    boolean doubleCheck;

    public LockBuffer(int writersCount, int readersCount, int allMessages, int messageVolume,boolean printStat, boolean doubleCheck){
        super(writersCount, readersCount, allMessages, messageVolume, printStat);
        this.doubleCheck = doubleCheck;

    }
    public LockBuffer(int writersCount, int readersCount, int allMessages, int messageVolume, boolean doubleCheck){
        super(writersCount, readersCount, allMessages, messageVolume);
        this.doubleCheck = doubleCheck;

    }

    //переключатель:false = одинарная, true = двойная проверка


    public void run () throws InterruptedException{
        Thread[] writers = new Thread[writersCount];
        Thread[] readers = new Thread[readersCount];

        int base = allMessages / writersCount;
        int rem = allMessages % writersCount;

        Map<String, Integer> seen = new ConcurrentHashMap<>();

        long timeStart = System.currentTimeMillis();
        // писатели
        for (int w = 0; w < writersCount; w++){
            final int id = w;
            int thStart = w * base + Math.min(w, rem);
            int thEnd = thStart + base + (w < rem ? 1 : 0);
            writers[w] = new Thread(() -> {
                for (int i = thStart; i < thEnd;){
                    String msg = "W" + id + "-" + i + "-" + message;
                    if (!doubleCheck){
                        // Вариант 1: одинарная проверка
                        synchronized (lock){
                            if (empty) {
                                buffer = msg;
                                empty = false;
                                i++;
                            }
                        }
                    } else {
                        // Вариант 2: двойная проверка
                        if (empty){ // дешево проверили без захода в
                            synchronized (lock){
                                if (empty){
                                    buffer = msg;
                                    empty = false;
                                    i++;
                                }

                            }
                        }else {
                            Thread.yield();
                        }
                    }
                }
            }, "Writer-" +w);
            writers[w].start();
        }

        //читатели
        for (int r = 0; r < readersCount; r++){
            readers[r] = new Thread(() -> {
                while (true){
                    String msg = null;

                    if(!doubleCheck){
                        //Вариант 1 : одинарная проверка
                        synchronized (lock){
                           if (!empty){
                               msg = buffer;
                               buffer = null;
                               empty = true;
                           } else if (finish) {
                               break;
                           }
                        }
                    } else {
                      // Вариант 2: двойная проверка
                        if (!empty) {
                            synchronized (lock) {
                                if (!empty) {
                                    msg = buffer;
                                    buffer = null;
                                    empty = true;
                                } else if (finish) {
                                    break;
                                }
                            }
                        } else if (finish){
                            break;
                        }
                    }

                    if (msg != null){
                        seen.merge(msg, 1, Integer::sum);
                    } else {
                        Thread.yield();
                    }
                }
            }, "Reader-"+r);
            readers[r].start();
        }
        //ждем писателей
        for (Thread t :writers) {
            t.join();
        }
        synchronized (lock){
            finish = true;
        }
        // ждем читателей
        for(Thread t : readers){
            t.join();
        }
        long end = System.currentTimeMillis();
        //статистика

        printStat("LockBuffer (" + (doubleCheck ? "double-check" : "single-check") + ")", seen, timeStart, end);
    }

}
