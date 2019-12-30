package org.burningwave.core.concurrent;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;

import org.burningwave.core.Component;

public class ConcurrentHelper implements Component {
	
	private ConcurrentHelper() {}
	
	public static ConcurrentHelper create() {
		return new ConcurrentHelper();
	}
	
	protected void joinAll(CompletableFuture<?>... completableFutures) {
		for (int i = 0; i < completableFutures.length; i++) {
			if (completableFutures[i] != null) {
				completableFutures[i].join();
			}
		}
	}

	public boolean removeAllTerminated(Collection<CompletableFuture<?>> completableFutureList) {
		Iterator<CompletableFuture<?>> itr = completableFutureList.iterator();
		boolean removed = false;
		while (itr.hasNext()) {
			CompletableFuture<?> cF = itr.next();
			if (cF.isDone() || cF.isCancelled() || cF.isCompletedExceptionally()) {
				completableFutureList.remove(cF);
				removed = true;
			}
		}
		return removed;
	}
	
	
	public void waitFor(long interval) {
		try {
			Thread.sleep(interval);
		} catch (InterruptedException exc) {
			logError("Exception occurred", exc);
		}
	}
	
}
