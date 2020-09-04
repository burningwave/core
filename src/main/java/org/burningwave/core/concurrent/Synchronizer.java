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
import java.util.function.Supplier;

public class Synchronizer implements AutoCloseable {
	Map<String, Object> parallelLockMap;
	
	private Synchronizer() {
		this.parallelLockMap = new ConcurrentHashMap<>();
	}
	
	public static Synchronizer create() {
		return new Synchronizer();
	}
	
	public Object getMutex(String id) {
		Object newLock = new Object();
    	Object lock = parallelLockMap.putIfAbsent(id, newLock);
        if (lock != null) {
        	return lock;
        }
        return newLock;
    }
	
	public void execute(String id, Runnable executable) {
		synchronized (getMutex(id)) {
			try {
				executable.run();
			} finally {
				parallelLockMap.remove(id);
			}
		}
	}
	
	public <T> T execute(String id, Supplier<T> executable) {
		T result = null;
		synchronized (getMutex(id)) {
			try {
				result = executable.get();
			} finally {
				parallelLockMap.remove(id);
			}
		}
		return result;
	}

	public void clear() {
		parallelLockMap.clear();
	}
	
	@Override
	public void close() {
		clear();
		parallelLockMap = null;
	}

	public void removeMutex(String id) {
		parallelLockMap.remove(id);	
	}
}
