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

import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggersRepository;
import static org.burningwave.core.assembler.StaticComponentContainer.Synchronizer;

import java.util.function.Supplier;

import org.burningwave.core.Closeable;
import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.concurrent.Synchronizer.Mutex;

@SuppressWarnings("unchecked")
class ClassLoaderManager<C extends ClassLoader> implements Closeable {
	
	
	private Supplier<C> classLoaderSupplier;
	private Object classLoaderOrClassLoaderSupplier;
	private C classLoader;	
	
	ClassLoaderManager(
		Object classLoaderOrClassLoaderSupplier
	) {
		this.classLoaderOrClassLoaderSupplier = classLoaderOrClassLoaderSupplier;
	}
	
	
	C get() {
		return this.classLoader;
	}
	
	C get(Object client) {
		C classLoaderTemp = null;
		Supplier<C> defaultClassLoaderSupplier = this.classLoaderSupplier;
		if (defaultClassLoaderSupplier != null && (classLoaderTemp = defaultClassLoaderSupplier.get()) != classLoader) {
			Mutex mutex = Synchronizer.getMutex(getOperationId("getDefaultClassLoader"));
			try {
				synchronized(mutex) {
					defaultClassLoaderSupplier = this.classLoaderSupplier;
					if (defaultClassLoaderSupplier != null && (classLoaderTemp = defaultClassLoaderSupplier.get()) != classLoader) {
						ClassLoader oldClassLoader = this.classLoader;
						if (oldClassLoader != null && oldClassLoader instanceof MemoryClassLoader) {
							((MemoryClassLoader)oldClassLoader).unregister(this, true);
						}
						if (classLoaderTemp instanceof MemoryClassLoader) {
							if (!((MemoryClassLoader)classLoaderTemp).register(this)) {
								classLoaderTemp = get(client);
							} else {
								((MemoryClassLoader)classLoaderTemp).register(client);
							}
						}
						this.classLoader = classLoaderTemp;
					}
				}
			} finally {
				Synchronizer.removeIfUnused(mutex);
			}
			return classLoaderTemp;
		}
		if (classLoader == null) {
			Mutex mutex = Synchronizer.getMutex(getOperationId("getDefaultClassLoader"));
			try {
				synchronized(mutex) {
					if (classLoader == null) {
						Object defaultClassLoaderOrDefaultClassLoaderSupplier =
							((Supplier<?>)this.classLoaderOrClassLoaderSupplier).get();
						if (defaultClassLoaderOrDefaultClassLoaderSupplier instanceof PathScannerClassLoader) {
							this.classLoader = (C)defaultClassLoaderOrDefaultClassLoaderSupplier;
							((MemoryClassLoader)classLoader).register(this);
							((MemoryClassLoader)classLoader).register(client);
							return classLoader;
						} else if (defaultClassLoaderOrDefaultClassLoaderSupplier instanceof Supplier) {
							this.classLoaderSupplier = (Supplier<C>) defaultClassLoaderOrDefaultClassLoaderSupplier;
							return get(client);
						}
					} else {						
						return classLoader;
					}
				}
			} finally {
				Synchronizer.removeIfUnused(mutex);
			}
		}
		return classLoader;
	}	


	
	void reset() {
		Synchronizer.execute(getOperationId("getDefaultClassLoader"), () -> {
			C classLoader = this.classLoader;
			if (classLoader != null) {
				this.classLoaderSupplier = null;
				this.classLoader = null;
				if (classLoader instanceof MemoryClassLoader) {
					((MemoryClassLoader)classLoader).unregister(this, true);
				}
				try {
					if (classLoader instanceof ComponentContainer.PathScannerClassLoader) {
						((ComponentContainer.PathScannerClassLoader)classLoader).markAsCloseable();
					}
				} catch (Throwable exc) {
					ManagedLoggersRepository.logWarn(getClass()::getName, "Exception occurred while resetting default path scanner classloader: {}", exc.getMessage());
				}
			}
		});
	}
	
	@Override
	public void close() {
		this.classLoaderOrClassLoaderSupplier = null;
		reset();
		this.classLoaderSupplier = null;
		this.classLoader = null;
	}

}
