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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

import org.burningwave.core.Component;

public class Mutex<K, V> implements Component {
	private K key;
	private Predicate<V> predicate;
	
	
	private Mutex(K key, Predicate<V> predicate) {
		this.key = key;
		this.predicate = predicate;
	}
	
	static <K, V> Mutex<K, V> create(K key, Predicate<V> predicate) {
		return new Mutex<>(key, predicate);
	}


	public K getKey() {
		return key;
	}
	
	public Predicate<V> getPredicate() {
		return predicate;
	}
	
	@Override
	public void close() {
		key = null;
		predicate = null;		
	}

	
	public static abstract class Manager implements Component {
		
		public static class ForMap<O, K, V> extends Manager  {
			
			private Map<O, Set<Mutex<K, V>>> mutexes;
			private Function<K, V> valueRetriever;
			
			private ForMap(Function<K, V> valueRetriever) {
				mutexes = new LinkedHashMap<>();
				this.valueRetriever = valueRetriever;
			}
			
			public static <O, K, V> ForMap<O, K, V> create(Function<K, V> valueRetriever) {
				return new ForMap<>(valueRetriever);
			}
			
			public V waitFor(O operation, K key, Predicate<V> predicate, int... timeout) throws InterruptedException {
				V toRet = valueRetriever.apply(key);
				if (predicate.test(toRet)) {
					return toRet;
				}
				
				Mutex<K, V> mutex = addMutexFor(operation, key, predicate);
				synchronized (mutex) {
					toRet = valueRetriever.apply(key);
					if (predicate.test(toRet)) {
						return toRet;
					}
					if (timeout != null && timeout.length > 0) {
						mutex.wait(timeout[0]);
					} else {
						mutex.wait();
					}
					return valueRetriever.apply(key);
				}
			}
			

			public Mutex<K, V> addMutexFor(O operation, K key, Predicate<V> predicate) {
				Set<Mutex<K, V>> mutexes = getMutexes(operation);
				return (Mutex<K, V>) mutexes.stream().filter(mutex -> 
					mutex.getKey().equals(key) && mutex.getPredicate() == predicate
				).findFirst().orElseGet(() -> {
					Mutex<K, V> mutex = Mutex.create(key, predicate);
					mutexes.add(mutex);
					return mutex;
				});
			}

			public Set<Mutex<K, V>> getMutexes(O operation) {
				return Optional.ofNullable(mutexes.get(operation)).orElseGet(() -> {
					Set<Mutex<K, V>> mutexes = ConcurrentHashMap.newKeySet();
					this.mutexes.put(operation, mutexes);
					return mutexes;
				});
			}
			

			public void unlockMutexes(O operation, K key, V value) {
				Set<Mutex<K, V>> mutexesForPut = getMutexes(operation);
				if (!mutexesForPut.isEmpty()) {
					for (Mutex<K, V> mutex : mutexesForPut) {
						if (mutex.getKey().equals(key) && mutex.getPredicate().test(value)) {
							synchronized (mutex) {
								mutex.notifyAll();
							}
							break;
						}
					}
				}
			}
			
			void removeMutexFor(O operation, Mutex<K, V> mutex) throws Exception {
				removeMutex(getMutexes(operation), mutex);			
			}
			
			void removeMutex(Set<Mutex<K, V>> mutexSet, Mutex<K, V> mutex) {
				synchronized (mutex) {
					mutex.notifyAll();
					mutexSet.remove(mutex);
					mutex.close();
				}
			}
			
			public void clearMutexes() {
				for (O key : mutexes.keySet()) {
					Set<Mutex<K, V>> mutexSet = mutexes.get(key);
					for(Mutex<K, V> mutex : mutexSet) {
						removeMutex(mutexSet, mutex);
					}
				}		
			}
			
			public void close() {
				if (mutexes != null) {
					clearMutexes();
					mutexes = null;
				}
				valueRetriever = null;
			}
		}
	}
}
