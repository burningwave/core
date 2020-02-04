/*
 * This file is part of Burningwave Core.
 *
 * Author: Roberto Gentili
 *
 * Hosted at: https://github.com/burningwave/core
 *
 * --
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Roberto Gentili
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without
 * limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.burningwave.core.extension.concurrent;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.burningwave.core.Component;
import org.burningwave.core.concurrent.ConcurrentHelper;
import org.burningwave.core.extension.concurrent.Cycler.Runnable;

public class CollectionManager<T> implements Component {
	
	private ConcurrentHelper concurrentHelper;
	private Map<Collection<T>, CollectionWrapper<T>> collectionWrapper;
	private List<Cycler.Thread> threadList;
	private String threadsGroupName;
	private int threadNumber;
	private int threadPriority;
	private long waitInterval;
	
	
	private CollectionManager(ConcurrentHelper concurrentHelper, String threadsGroupName, int threadsNumber, int threadPriority, long waitInterval) {
		this.concurrentHelper = concurrentHelper;
		this.threadsGroupName = threadsGroupName;
		this.threadNumber = threadsNumber;
		this.threadPriority = threadPriority;
		this.waitInterval = waitInterval;
	}
	
	
	public static <T> CollectionManager<T> create(ConcurrentHelper concurrentHelper, String threadsGroupName, int threadsNumber, int threadPriority, long waitInterval) {
		return new CollectionManager<T>(concurrentHelper, threadsGroupName, threadsNumber, threadPriority, waitInterval);
	}

	public void start() {
		this.collectionWrapper = new ConcurrentHashMap<>();
		this.threadList = new CopyOnWriteArrayList<>();
		for (int i = 0; i < threadNumber; i++) {
			Cycler.Thread thrWrp = new Cycler.Thread(
				threadList,
				new Runnable() {
					@SuppressWarnings("unchecked")
					@Override
					public void run() {
						if (!collectionWrapper.isEmpty()) {
							collectionWrapper.forEach(
								(cL, cLW) -> {
									if (cLW.isUseless()) {
										cL.clear();
										collectionWrapper.remove(cL);
										logDebug("cleaned");
									} else if (!concurrentHelper.removeAllTerminated((Collection<CompletableFuture<?>>) cLW.getCollection())){
										concurrentHelper.waitFor(waitInterval);
									}
								}
							);
						} else {
							concurrentHelper.waitFor(waitInterval);
						}
					}
				},
				getThreadNewName(i), 
				threadPriority
			);
			thrWrp.start();
		}
	}
	
	private String getThreadNewName() {
		return getThreadNewName(threadList.size() + 1);
	}
	
	private String getThreadNewName(int idx) {
		return threadsGroupName + "[" + idx + "]";
	}
	
	public void add(Collection<T> coll) {
		Optional.ofNullable(collectionWrapper).ifPresent((collectionWrapper) -> {
			if (!collectionWrapper.containsKey(coll)) {
				synchronized(this) {
					if (!collectionWrapper.containsKey(coll)) {
						collectionWrapper.put(coll, new CollectionWrapper<T>(coll));
					}
				}
			}
		});
	}	

	public CollectionWrapper<T> get(Collection<CompletableFuture<?>> coll) {
		return collectionWrapper.get(coll);
	}
	
	public void markAsUseless(Collection<CompletableFuture<?>> coll) {
		Optional.ofNullable(threadList).ifPresent((threadList) -> {
			Cycler.Thread thread = new Cycler.Thread(
				threadList,
				new Runnable() {
					@Override
					public void run() {
						CollectionWrapper<T> wRP = get(coll);
						if (wRP != null) {
							wRP.setUseless(true);
							thread.terminate();
						} else if (collectionWrapper.isEmpty()) {
							thread.terminate();
						} else {
							concurrentHelper.waitFor(waitInterval);
						}
					}
				},
				getThreadNewName(),
				this.threadPriority
			);
			thread.start();
		});
	}
	
	public void stop() {
		try {
			finalize();
		} catch (Throwable exc) {
			logError("Exception occurred", exc);
		}
	}
	
	@Override
	protected void finalize() throws Throwable {
		Optional.ofNullable(collectionWrapper).ifPresent((collectionWrapper) -> {
			collectionWrapper.clear();
		});
		Optional.ofNullable(threadList).ifPresent((threadList) -> {
			threadList.forEach((thr) -> {
				try {
					thr.terminate();
					thr.join();
				} catch (InterruptedException exc) {
					logError("Exception occurred", exc);
				}
			});
			threadList.clear();
		});
	}
	
	public static class Cycler {
		
		public static class Thread extends org.burningwave.core.extension.concurrent.Cycler.Thread {
			private Collection<Thread> threadCollection;
			
			public Thread(
				Collection<Thread> threadCollection, 
				org.burningwave.core.extension.concurrent.Cycler.Runnable function,
				String name, int priority) {
				super(function, name, priority);
				this.threadCollection = threadCollection;
			}
			
			@Override
			public void run() {
				threadCollection.add(this);
				super.run();
				threadCollection.remove(this);
			}
		}
	}
}

class CollectionWrapper<T> {
	private Collection<T> collection;
	private boolean useless;
	
	CollectionWrapper(Collection<T> collection) {
		this.collection = collection;
	}
	
	void setUseless(boolean value) {
		this.useless = value;
	}
	
	boolean isUseless() {
		return this.useless;
	}
	
	Collection<T> getCollection() {
		return this.collection;
	}
}
