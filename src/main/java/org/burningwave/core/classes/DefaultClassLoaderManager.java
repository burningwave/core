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
package org.burningwave.core.classes;

import static org.burningwave.core.assembler.StaticComponentContainer.Synchronizer;

import java.util.function.Consumer;
import java.util.function.Supplier;

import org.burningwave.core.Closeable;
import org.burningwave.core.ManagedLogger;

@SuppressWarnings("unchecked")
public class DefaultClassLoaderManager<C extends ClassLoader> implements Closeable, ManagedLogger {
	
	
	private Consumer<ClassLoader> classLoaderResetter;
	private Supplier<C> defaultClassLoaderSupplier;
	private Object defaultClassLoaderOrDefaultClassLoaderSupplier;
	private C defaultClassLoader;	
	
	DefaultClassLoaderManager(
		Object defaultClassLoaderOrDefaultClassLoaderSupplier,
		Consumer<ClassLoader> classLoaderResetter
	) {
		this.defaultClassLoaderOrDefaultClassLoaderSupplier = defaultClassLoaderOrDefaultClassLoaderSupplier;
		this.classLoaderResetter = classLoaderResetter;
	}
	
	
	C get() {
		if (this.defaultClassLoader == null) {
			return get(this);
		}
		return this.defaultClassLoader;
	}
	
	C get(Object client) {
		C classLoader = null;
		Supplier<C> defaultClassLoaderSupplier = this.defaultClassLoaderSupplier;
		if (defaultClassLoaderSupplier != null && (classLoader = defaultClassLoaderSupplier.get()) != defaultClassLoader) {
			String mutexId = getOperationId(getOperationId("getDefaultClassLoader"));
			synchronized(Synchronizer.getMutex(mutexId)) {
				defaultClassLoaderSupplier = this.defaultClassLoaderSupplier;
				if (defaultClassLoaderSupplier != null && (classLoader = defaultClassLoaderSupplier.get()) != defaultClassLoader) {
					ClassLoader oldClassLoader = this.defaultClassLoader;
					if (oldClassLoader != null && oldClassLoader instanceof MemoryClassLoader) {
						((MemoryClassLoader)oldClassLoader).unregister(this, true);
					}
					if (classLoader instanceof MemoryClassLoader) {
						if (!((MemoryClassLoader)classLoader).register(this)) {
							classLoader = get(client);
						} else {
							((MemoryClassLoader)classLoader).register(client);
						}
					}
					this.defaultClassLoader = classLoader;
				}
				Synchronizer.removeMutex(mutexId);
			}
			return classLoader;
		}
		if (defaultClassLoader == null) {
			String mutexId = getOperationId("getDefaultClassLoader");
			synchronized(Synchronizer.getMutex(mutexId)) {
				if (defaultClassLoader == null) {
					Object defaultClassLoaderOrDefaultClassLoaderSupplier =
						((Supplier<?>)this.defaultClassLoaderOrDefaultClassLoaderSupplier).get();
					if (defaultClassLoaderOrDefaultClassLoaderSupplier instanceof PathScannerClassLoader) {
						this.defaultClassLoader = (C)defaultClassLoaderOrDefaultClassLoaderSupplier;
						((MemoryClassLoader)defaultClassLoader).register(this);
						((MemoryClassLoader)defaultClassLoader).register(client);
						return defaultClassLoader;
					} else if (defaultClassLoaderOrDefaultClassLoaderSupplier instanceof Supplier) {
						this.defaultClassLoaderSupplier = (Supplier<C>) defaultClassLoaderOrDefaultClassLoaderSupplier;
						return get(client);
					}
				} else { 
					return defaultClassLoader;
				}
				Synchronizer.removeMutex(mutexId);
			}
		}
		return defaultClassLoader;
	}	

	
	void reset() {
		Synchronizer.execute(getOperationId("getDefaultClassLoader"), () -> {
			C classLoader = this.defaultClassLoader;
			if (classLoader != null) {
				this.defaultClassLoaderSupplier = null;
				this.defaultClassLoader = null;
				if (classLoader instanceof MemoryClassLoader) {
					((MemoryClassLoader)classLoader).unregister(this, true);
				}
				try {
					classLoaderResetter.accept(classLoader);
				} catch (Throwable exc) {
					logWarn("Exception occurred while resetting default path scanner classloader {}", exc.getMessage());
				}
			}
		});
	}
	
	@Override
	public void close() {
		this.defaultClassLoaderOrDefaultClassLoaderSupplier = null;
		reset();
		this.defaultClassLoaderSupplier = null;
		this.classLoaderResetter = null;
		this.defaultClassLoader = null;
	}

}
