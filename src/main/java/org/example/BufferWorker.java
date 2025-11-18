package org.example;

import java.util.Map;

public abstract class BufferWorker {
    final int writersCount;
    final int readersCount;
    final int allMessages;
    final String message;
    boolean printStat;

    private long lastTimeSpentMs;

    public BufferWorker (int writersCount, int readersCount, int allMessages, int messageVolume, boolean printStat){
        this.writersCount = writersCount;
        this.readersCount = readersCount;
        this.allMessages = allMessages;
        this.message = buildMessage(messageVolume);
        this.printStat = printStat;
    }
    public BufferWorker(int writersCount, int readersCount, int allMessages, int messageVolume) {
        this(writersCount, readersCount, allMessages, messageVolume, false);
    }
    private String buildMessage(int messageVolume){
        StringBuilder sb = new StringBuilder(messageVolume * 5);
        for (int i = 0; i < messageVolume; i++){
            sb.append("msg").append(i).append(" ");
        }
        return sb.toString();
    }
    public abstract void run() throws InterruptedException;

    public long getLastTime(){
        return this.lastTimeSpentMs;
    }
    protected void printStat(String name, Map<String, Integer> seen, long timeStart, long end){
        long timeSpent = end - timeStart;
        this.lastTimeSpentMs = timeSpent;
        if (!printStat) return;
        int expected = allMessages;
        long distinctRead = seen.size();
        long totalRead = seen.values().stream().mapToLong(Integer::longValue).sum();
        long duplicates = totalRead - distinctRead;
        long lost = expected - distinctRead;
        System.out.println("=== " + name + " ===");
        System.out.println("Ожидалось сообщений:       " + expected);
        System.out.println("Прочитано всего:           " + totalRead);
        System.out.println("Уникально  прочитано:      " + distinctRead);
        System.out.println("Дубликатов:                " + duplicates);
        System.out.println("Потерь:                    " + lost);
        System.out.println("Затраченное время:         " + (timeSpent));
    }
}
