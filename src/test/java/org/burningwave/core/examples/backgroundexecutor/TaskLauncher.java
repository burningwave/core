package org.burningwave.core.examples.backgroundexecutor;

import static org.burningwave.core.assembler.StaticComponentContainer.BackgroundExecutor;

import org.burningwave.core.ManagedLogger;
import org.burningwave.core.concurrent.QueuedTaskExecutor.ProducerTask;
import org.burningwave.core.concurrent.QueuedTaskExecutor.Task;


public class TaskLauncher implements ManagedLogger {

    public void launch() {
        ProducerTask<Long> taskOne = BackgroundExecutor.createProducerTask(task -> {
            Long startTime = System.currentTimeMillis();
            logInfo("task one started");
            synchronized (this) {
                wait(5000);
            }
            Task internalTask = BackgroundExecutor.createTask(tsk -> {
                logInfo("internal task started");
                synchronized (this) {
                    wait(5000);
                }
                logInfo("internal task finished");
            }, Thread.MAX_PRIORITY).submit();
            internalTask.waitForFinish();
            logInfo("task one finished");
            return startTime;
        }, Thread.MAX_PRIORITY);
        taskOne.submit();
        Task taskTwo = BackgroundExecutor.createTask(task -> {
            logInfo("task two started and wait for task one finishing");
            taskOne.waitForFinish();
            logInfo("task two finished");
        }, Thread.NORM_PRIORITY);
        taskTwo.submit();
        ProducerTask<Long> taskThree = BackgroundExecutor.createProducerTask(task -> {
            logInfo("task three started and wait for task two finishing");
            taskTwo.waitForFinish();
            logInfo("task two finished");
            return System.currentTimeMillis();
        }, Thread.MIN_PRIORITY);
        taskThree.submit();
        taskThree.waitForFinish();
        logInfo("Elapsed time: {}ms", taskThree.join() - taskOne.join());
    }

    public static void main(String[] args) {
        new TaskLauncher().launch();
    }

}
