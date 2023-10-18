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
 * Copyright (c) 2019-2023 Roberto Gentili
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
package org.burningwave.core.iterable;


import static org.burningwave.core.assembler.StaticComponentContainer.Classes;
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository;
import static org.burningwave.core.assembler.StaticComponentContainer.ThreadSupplier;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.burningwave.core.concurrent.Thread;
import org.burningwave.core.function.ThrowingConsumer;

@SuppressWarnings("unchecked")
class ThreadBasedIterator extends IterableObjectHelperImpl.Iterator {

	ThreadBasedIterator(IterableObjectHelperImpl iterableObjectHelper) {
		super(iterableObjectHelper);
	}

	@Override
	<I, IC, OC> OC iterate(
		IC items,
		Predicate<IC> predicateForParallelIteration,
		OC output,
		BiConsumer<I, Consumer<Consumer<OC>>> action,
		Integer priority
	) {
		if (items == IterableObjectHelperImpl.Iterator.NO_ITEMS) {
			return output;
		}
		java.lang.Thread currentThread = Thread.currentThread();
		int initialThreadPriority = currentThread.getPriority();
		if (priority == null) {
			priority = initialThreadPriority;
		} else if (initialThreadPriority != priority) {
			currentThread.setPriority(priority);
		}
		try {
			if (predicateForParallelIteration == null) {
				predicateForParallelIteration = collectionOrArray -> iterableObjectHelper.defaultMinimumCollectionSizeForParallelIterationPredicate.test(collectionOrArray);
			}
			int taskCountThatCanBeCreated = iterableObjectHelper.getCountOfTasksThatCanBeCreated(items, predicateForParallelIteration);
			if (taskCountThatCanBeCreated > 1) {
				Consumer<Consumer<OC>> outputItemsHandler = buildOutputCollectionHandler(output);
				// Used for break the iteration
				AtomicReference<IterableObjectHelper.TerminateIteration> terminateIterationNotification = new AtomicReference<>();
				Map<Thread, Thread> threads = new ConcurrentHashMap<>();
				/* Iterate List */
				if (items instanceof List) {
					List<I> itemList = (List<I>)items;
					final int splittedIteratorSize = itemList.size() / taskCountThatCanBeCreated;
					for (
						int currentIndex = 0, splittedIteratorIndex = 0;
						currentIndex < taskCountThatCanBeCreated && terminateIterationNotification.get() == null;
						++currentIndex, splittedIteratorIndex+=splittedIteratorSize
					) {
						Iterator<I> itemIterator = itemList.listIterator(splittedIteratorIndex);
						final int itemsCount = currentIndex != taskCountThatCanBeCreated -1 ?
							splittedIteratorSize :
							(itemList.size() - (splittedIteratorSize * currentIndex));
						ThrowingConsumer<Thread, ? extends Throwable> iterator = thread -> {
							try {
								for (
									int remainedItems = itemsCount;
									terminateIterationNotification.get() == null && remainedItems > 0;
									--remainedItems
								) {
									action.accept(itemIterator.next(), outputItemsHandler);
								}
							} catch (IterableObjectHelper.TerminateIteration exc) {
								checkAndNotifyTerminationOfIteration(terminateIterationNotification, exc);
							} catch (Throwable exc) {
								terminateIterationNotification.set(IterableObjectHelper.TerminateIteration.NOTIFICATION);
								throw exc;
							} finally {
								removeThread(threads, thread);
							}
						};
						if (currentIndex < (taskCountThatCanBeCreated - 1)) {
							createAndStartThread(threads, iterator, priority);
						} else {
							consume(iterator);
						}
					}
				/* Iterate any Collection except List */
				} else if (items instanceof Collection) {
					Iterator<I> itemIterator = ((Collection<I>)items).iterator();
					ThrowingConsumer<Thread, ? extends Throwable> iterator = thread -> {
						I item = null;
						try {
							while (terminateIterationNotification.get() == null) {
								try {
									synchronized (itemIterator) {
										item = itemIterator.next();
									}
								} catch (NoSuchElementException exc) {
									terminateIterationNotification.set(IterableObjectHelper.TerminateIteration.NOTIFICATION);
									break;
								}
								action.accept(item, outputItemsHandler);
							}
						} catch (IterableObjectHelper.TerminateIteration exc) {
							checkAndNotifyTerminationOfIteration(terminateIterationNotification, exc);
						} catch (Throwable exc) {
							terminateIterationNotification.set(IterableObjectHelper.TerminateIteration.NOTIFICATION);
							throw exc;
						} finally {
							removeThread(threads, thread);
						}
					};
					for (int taskIndex = 0; taskIndex < taskCountThatCanBeCreated && terminateIterationNotification.get() == null; taskIndex++) {
						if (taskIndex < (taskCountThatCanBeCreated - 1)) {
							createAndStartThread(threads, iterator, priority);
						} else {
							consume(iterator);
						}
					}
				} else {
					int arrayLength = Array.getLength(items);
					final int splittedIteratorSize = arrayLength / taskCountThatCanBeCreated;
					Class<?> componentType = items.getClass().getComponentType();
					/* Iterate primitive array */
					if (componentType.isPrimitive()) {
						final Function<Integer, ?> itemRetriever = Classes.buildArrayValueRetriever(items);
						for (
							int taskIndex = 0, currentSplittedIteratorIndex = 0;
							taskIndex < taskCountThatCanBeCreated && terminateIterationNotification.get() == null;
							++taskIndex, currentSplittedIteratorIndex+=splittedIteratorSize
						) {
							final int itemsCount = taskIndex != taskCountThatCanBeCreated -1 ?
								splittedIteratorSize :
								arrayLength - (splittedIteratorSize * taskIndex);
							final int splittedIteratorIndex = currentSplittedIteratorIndex;
							ThrowingConsumer<Thread, ? extends Throwable> iterator = thread -> {
								try {
									int remainedItems = itemsCount;
									for (
										int itemIndex = splittedIteratorIndex;
										terminateIterationNotification.get() == null && remainedItems > 0;
										--remainedItems
									) {
										action.accept((I)itemRetriever.apply(itemIndex++), outputItemsHandler);
									}
								} catch (IterableObjectHelper.TerminateIteration exc) {
									checkAndNotifyTerminationOfIteration(terminateIterationNotification, exc);
								} catch (Throwable exc) {
									terminateIterationNotification.set(IterableObjectHelper.TerminateIteration.NOTIFICATION);
									throw exc;
								} finally {
									removeThread(threads, thread);
								}
							};
							if (taskIndex < (taskCountThatCanBeCreated - 1)) {
								createAndStartThread(threads, iterator, priority);
							} else {
								consume(iterator);
							}
						}
					/* Iterate array of objects */
					} else {
						for (
							int taskIndex = 0, currentSplittedIteratorIndex = 0;
							taskIndex < taskCountThatCanBeCreated && terminateIterationNotification.get() == null;
							++taskIndex, currentSplittedIteratorIndex+=splittedIteratorSize
						) {
							final int itemsCount = taskIndex != taskCountThatCanBeCreated -1 ?
								splittedIteratorSize :
								arrayLength - (splittedIteratorSize * taskIndex);
							final int splittedIteratorIndex = currentSplittedIteratorIndex;
							I[] itemArray = (I[])items;
							ThrowingConsumer<Thread, ? extends Throwable> iterator = thread -> {
								try {
									int remainedItems = itemsCount;
									for (
										int itemIndex = splittedIteratorIndex;
										terminateIterationNotification.get() == null && remainedItems > 0;
										--remainedItems
									) {
										action.accept(itemArray[itemIndex++], outputItemsHandler);
									}
								} catch (IterableObjectHelper.TerminateIteration exc) {
									checkAndNotifyTerminationOfIteration(terminateIterationNotification, exc);
								} catch (Throwable exc) {
									terminateIterationNotification.set(IterableObjectHelper.TerminateIteration.NOTIFICATION);
									throw exc;
								} finally {
									removeThread(threads, thread);
								}
							};
							if (taskIndex < (taskCountThatCanBeCreated - 1)) {
								createAndStartThread(threads, iterator, priority);
							} else {
								consume(iterator);
							}
						}
					}
				}
				if (!threads.isEmpty()) {
					synchronized(threads) {
						if (!threads.isEmpty()) {
							try {
								threads.wait();
							} catch (InterruptedException exc) {
								org.burningwave.core.assembler.StaticComponentContainer.Driver.throwException(exc);
							}
						}
					}
				}
				return output;
			}
			Consumer<Consumer<OC>> outputItemsHandler =
				output != null ?
					(outputCollectionConsumer) -> {
						outputCollectionConsumer.accept(output);
					}
				: null;
			try {
				if (items instanceof Collection) {
					for (I item : (Collection<I>)items) {
						action.accept(item, outputItemsHandler);
					}
				} else if (!items.getClass().getComponentType().isPrimitive()) {
					I[] itemArray = (I[])items;
					for (I item : itemArray) {
						action.accept(item, outputItemsHandler);
					}
				} else {
					Function<Integer, ?> itemRetriever = Classes.buildArrayValueRetriever(items);
					int arrayLength = Array.getLength(items);
					for (int i = 0; i < arrayLength; i++) {
						action.accept((I)itemRetriever.apply(i), outputItemsHandler);
					}
				}
			} catch (IterableObjectHelper.TerminateIteration t) {

			}
		} finally {
			if (initialThreadPriority != priority) {
				currentThread.setPriority(initialThreadPriority);
			}
		}
		return output;
	}

	private Thread createAndStartThread(Map<Thread, Thread> threads, ThrowingConsumer<Thread, ? extends Throwable> iterator, int priority) {
		Thread thread = ThreadSupplier.getOrCreateThread().setExecutable(iterator);
		thread.setPriority(priority);
		threads.put(thread, thread);
		thread.start();
		return thread;
	}

	private void removeThread(Map<Thread, Thread> threads, Thread thread) {
		if (thread != null) {
			threads.remove(thread);
			if (threads.isEmpty()) {
				synchronized(threads) {
					threads.notify();
				}
			}
		}
	}

	private void consume(ThrowingConsumer<Thread, ? extends Throwable> iterator) {
		try {
			iterator.accept(null);
		} catch (Throwable exc) {
			ManagedLoggerRepository.logError(getClass()::getName, exc);
		}
	}

}
