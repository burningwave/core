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
package org.burningwave.core.concurrent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Mutex {
	
	public static class Manager implements AutoCloseable {
		Map<String, Object> parallelLockMap;
		private Object defaultMutex;
		
		private Manager(Object defaultMutex) {
			this.parallelLockMap = new ConcurrentHashMap<>();
			this.defaultMutex = defaultMutex;
		}
		
		public static Manager create(Object client) {
			return new Manager(client);
		}
		
		public void enableLockForName() {
			Map<String, Object> parallelLockMap = this.parallelLockMap;
			if (parallelLockMap == null) {
				synchronized(this) {
					if ((parallelLockMap = this.parallelLockMap) == null) {
						this.parallelLockMap = new ConcurrentHashMap<>();
					}
				}
			}
		}
		
		public void disableLockForName() {
			this.parallelLockMap = null;
		}
		
		public Object getMutex(String id) {
			Object lock = defaultMutex;
			Map<String, Object> parallelLockMap = this.parallelLockMap;
			if (parallelLockMap != null) {
		    	Object newLock = new Mutex();
		    	lock = parallelLockMap.putIfAbsent(id, newLock);
		        if (lock == null) {
		            lock = newLock;
		        }
			}
	        return lock;
	    }

		public void clear() {
			if (parallelLockMap != null) {
				parallelLockMap.clear();
			}
		}
		
		@Override
		public void close() {
			clear();
			parallelLockMap = null;
			this.defaultMutex = null;
		}

		public void remove(String id) {
			parallelLockMap.remove(id);	
		}
	}
	
}
