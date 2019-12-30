package org.burningwave.core.concurrent;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.burningwave.core.Component;

public class ParallelTasksManager implements Component{

	protected Collection<CompletableFuture<?>> tasks;
	protected ExecutorService executorService;
	private int maxParallelTasks;

	private ParallelTasksManager(int maxParallelTasks) {
		tasks = new CopyOnWriteArrayList<>();
		this.maxParallelTasks = maxParallelTasks;
	}
	
	public static ParallelTasksManager create(int maxParallelTasks) {
		return new ParallelTasksManager(maxParallelTasks);
	}

	public void addTask(Runnable task) {
		if (executorService == null) {
			this.executorService = Executors.newFixedThreadPool(maxParallelTasks);
		}
		tasks.add(CompletableFuture.runAsync(task, executorService));
	}

	public void waitForTasksEnding() {
		tasks.forEach(task -> {
			task.join();
			//tasks.remove(task);
		});
	}
	
	@Override
	public void close() {
		waitForTasksEnding();
		tasks.clear();
		tasks = null;
		if (executorService != null) {
			executorService.shutdown();
			executorService = null;
		}
	}
}