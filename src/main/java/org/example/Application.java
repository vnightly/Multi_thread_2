package org.example;

import java.sql.SQLOutput;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Application {
    public static void main(String[] args) throws InterruptedException {
        List<Integer> WRITERS = Arrays.asList(1, 2, 4);
        List<Integer> Messages = Arrays.asList(1000, 10_000, 100_000);
        List<Integer> MessageVolumes = Arrays.asList(10, 100, 1000);

        String line =
                "+-----+-----+----------+--------+------------+------------+------------+--------------+------------+";

        // --- шапка ---
        System.out.println(line);
        System.out.printf(
                "| %-3s | %-3s | %-8s | %-6s | %-10s | %-10s | %-10s | %-12s | %-10s |%n",
                "W", "R", "Msgs", "Vol",
                "LockS", "LockD", "Semaphore", "AutoEvent", "Atomic"
        );
        System.out.println(line);

        for (int w : WRITERS) {
            int r = w; // читателей столько же, сколько писателей

            for (int m : Messages) {
                for (int v : MessageVolumes) {

                    // --- прогон всех реализаций ---
                    long tLockS  = runAndGet(new LockBuffer(w, r, m, v, false));  // single-check
                    long tLockD  = runAndGet(new LockBuffer(w, r, m, v, true));   // double-check
                    long tSem    = runAndGet(new SemaphoreBuffer(w, r, m, v));
                    long tAuto   = runAndGet(new AutoResetEventBuffer(w, r, m, v));
                    long tAtomic = runAndGet(new InterlockedBuffer(w, r, m, v));

                    // --- вывод строки ---
                    System.out.printf(
                            "| %-3d | %-3d | %-8d | %-6d | %-10d | %-10d | %-10d | %-12d | %-10d |%n",
                            w, r, m, v,
                            tLockS, tLockD, tSem, tAuto, tAtomic
                    );
                }
            }
        }

        System.out.println(line);


        /*Гонка данных, тут увелечение потоков помогает,так как работа идет асинхронно, но из-за асинхронное работы теряются
        данных
        */
        UnsyncBuffer unsync = new UnsyncBuffer(2,2,10_000, 1000, true);
        unsync.run();
        UnsyncBuffer unsync2 = new UnsyncBuffer(8,8,10_000, 1000, true);
        unsync2.run();

        //Синхронизация семафорами, по букве еще нужен был семфор слим, но в джава семафор и так слим
        SemaphoreBuffer semaphore = new SemaphoreBuffer(1,1,10_000,1000, true);
        semaphore.run();
        //Синхронизация блокировкой буффера одинарная и двойная
        LockBuffer lockOne = new LockBuffer(1,1,10_000,1000,true, false);
        lockOne.run();

        LockBuffer lockTwo = new LockBuffer(1,1,10_000,1000,true, true);
        lockTwo.run();

        AutoResetEventBuffer autoResetEvent = new AutoResetEventBuffer(1,1,10_000,1000, true);
        autoResetEvent.run();

        InterlockedBuffer interlocked = new InterlockedBuffer(1, 1, 10_000, 1000, true);
        interlocked.run();


    }
    private static long runAndGet(BufferWorker worker) throws InterruptedException {
        worker.run();
        return worker.getLastTime();
    }
}
