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

import static org.burningwave.core.assembler.StaticComponentContainer.BackgroundExecutor;
import static org.burningwave.core.assembler.StaticComponentContainer.Cache;
import static org.burningwave.core.assembler.StaticComponentContainer.ClassLoaders;
import static org.burningwave.core.assembler.StaticComponentContainer.Classes;
import static org.burningwave.core.assembler.StaticComponentContainer.IterableObjectHelper;
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggersRepository;
import static org.burningwave.core.assembler.StaticComponentContainer.Objects;
import static org.burningwave.core.assembler.StaticComponentContainer.Strings;
import static org.burningwave.core.assembler.StaticComponentContainer.Synchronizer;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.security.ProtectionDomain;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.burningwave.core.Component;
import org.burningwave.core.concurrent.QueuedTasksExecutor.Task;
import org.burningwave.core.io.ByteBufferInputStream;


@SuppressWarnings("unchecked")
public class MemoryClassLoader extends ClassLoader implements Component {
	Map<String, ByteBuffer> notLoadedByteCodes;
	Map<String, ByteBuffer> loadedByteCodes;
	Collection<Object> clients;
	protected boolean isClosed;
	String instanceId;
	
	
	static {
        ClassLoader.registerAsParallelCapable();
    }
	
	protected MemoryClassLoader(
		ClassLoader parentClassLoader
	) {
		super(parentClassLoader);
		instanceId = Objects.getCurrentId(this);
		if (parentClassLoader instanceof MemoryClassLoader) {
			((MemoryClassLoader)parentClassLoader).register(this);
		}
		this.notLoadedByteCodes = new HashMap<>();
		this.loadedByteCodes = new HashMap<>();
		this.clients = new HashSet<>();
	}
	
	public static MemoryClassLoader create(ClassLoader parentClassLoader) {
		return new MemoryClassLoader(parentClassLoader);
	}

	public void addByteCode(String className, ByteBuffer byteCode) {
    	try {
			addByteCode0(className, byteCode);
    	} catch (Throwable exc) {
    		if (!isClosed) {
    			throw exc;
    		} else {
    			ManagedLoggersRepository.logWarn(getClass()::getName, "Could not execute addByteCode on class named {} because {} has been closed", className, this.toString());
    		}
    	}
    }

	void addByteCode0(String className, ByteBuffer byteCode) {
		if (ClassLoaders.retrieveLoadedClass(this, className) == null) {
			notLoadedByteCodes.put(className, byteCode);
		} else {
			ManagedLoggersRepository.logWarn(getClass()::getName, "Could not add bytecode for class {} cause it's already defined", className);
		}
	}
    
    public Map.Entry<String, ByteBuffer> getNotLoadedByteCode(String className) {
    	try {
        	for (Map.Entry<String, ByteBuffer> entry : notLoadedByteCodes.entrySet()){
        	    if (entry.getKey().equals(className)) {
        	    	return entry;
        	    }
        	}
    	} catch (Throwable exc) {
    		if (!isClosed) {
    			throw exc;
    		} else {
    			ManagedLoggersRepository.logWarn(getClass()::getName, "Could not execute getNotLoadedByteCode on class named {} because {} has been closed", className, this.toString());
    		}
    	}
    	return null;
    }
    
    public ByteBuffer getByteCodeOf(String className) {
    	try {
    		return Optional.ofNullable(notLoadedByteCodes.get(className)).orElseGet(() -> Optional.ofNullable(loadedByteCodes.get(className)).orElseGet(() -> null));
    	} catch (Throwable exc) {
    		if (!isClosed) {
    			throw exc;
    		} else {
    			ManagedLoggersRepository.logWarn(getClass()::getName, "Could not execute getByteCodeOf on class named {} because {} has been closed", className, this.toString());
    		}
    	}
    	return null;
    }
    
    void addByteCodes(Map<String, ByteBuffer> byteCodes) {
    	try {
    		for (Map.Entry<String, ByteBuffer> clazz : byteCodes.entrySet()) {
    			addByteCode0(
    				clazz.getKey(), clazz.getValue()
    			);
    		}
    	} catch (Throwable exc) {
    		if (!isClosed) {
    			throw exc;
    		} else {
    			ManagedLoggersRepository.logWarn(getClass()::getName, "Could not execute addByteCodes on {} because {} has been closed", byteCodes.toString(), this.toString());
    		}
    	}
		
    }
    
    public void addByteCodes(Collection<Entry<String, ByteBuffer>> classes) {
    	try {
    		for (Map.Entry<String, ByteBuffer> clazz : classes) {
    			addByteCode0(
    				clazz.getKey(), clazz.getValue()
    			);
    		}
    	} catch (Throwable exc) {
    		if (!isClosed) {
    			throw exc;
    		} else {
    			ManagedLoggersRepository.logWarn(getClass()::getName, "Could not execute addByteCodes on {} because {} has been closed", classes.toString(), this.toString());
    		}
    	}
	} 

	public void addByteCodes(Entry<String, ByteBuffer>... classes) {
		try {
    		for (Map.Entry<String, ByteBuffer> clazz : classes) {
    			addByteCode0(
    				clazz.getKey(), clazz.getValue()
    			);
    		}
    	} catch (Throwable exc) {
    		if (!isClosed) {
    			throw exc;
    		} else {
    			ManagedLoggersRepository.logWarn(getClass()::getName, "Could not execute addByteCodes on {} because {} has been closed", classes.toString(), this.toString());
    		}
    	}    	
	} 
    
	public boolean hasPackageBeenDefined(String packageName) {
		return Strings.isEmpty(packageName) || ClassLoaders.retrieveLoadedPackage(this, packageName) != null;
	}
    
    @Override
    protected Package definePackage(String packageName, String specTitle,
		String specVersion, String specVendor, String implTitle,
		String implVersion, String implVendor, URL sealBase
	) throws IllegalArgumentException {
    	Package pkg = null;
    	if (Strings.isNotEmpty(packageName)) {
    		try {
				pkg = super.definePackage(packageName, specTitle, specVersion, specVendor, implTitle,
		    			implVersion, implVendor, sealBase);
			} catch (IllegalArgumentException exc) {
				ManagedLoggersRepository.logWarn(getClass()::getName, "Package " + packageName + " already defined");
				pkg = ClassLoaders.retrieveLoadedPackage(this, packageName);
			}
    	}
    	return pkg;
    }
    
	void definePackageOf(Class<?> cls) {
		if (cls.getName().contains(".")) {
			String pckgName = cls.getName().substring(
		    	0, cls.getName().lastIndexOf(".")
		    );
		    if (ClassLoaders.retrieveLoadedPackage(this, pckgName) == null) {
		    	Synchronizer.execute(instanceId + "_" + pckgName, () -> {
		    		if (ClassLoaders.retrieveLoadedPackage(this, pckgName) == null) {
		    			definePackage(pckgName, null, null, null, null, null, null, null);
		    		}
		    	});
			}	
		}
	}
    
    @Override
    protected Class<?> loadClass(String className, boolean resolve) throws ClassNotFoundException {
    	Class<?> cls = null;
    	try {
			cls = super.loadClass(className, resolve);
		} catch (Throwable exc) {
			if (className.startsWith("java.")) {
				cls = Class.forName(className);
			} else {
				Throwables.throwException(exc);
			}			
		}
    	removeNotLoadedBytecode(className);
    	return cls;
    }
    
    
    public Class<?> loadOrDefineClass(Class<?> toLoad) throws ClassNotFoundException {
    	return ClassLoaders.loadOrDefine(toLoad, this);
    }
    
    public Class<?> loadOrDefineClass(JavaClass toLoad) throws ClassNotFoundException {
    	return ClassLoaders.loadOrDefineByJavaClass(toLoad, this);
    }
    
    public Class<?> loadOrDefineClass(ByteBuffer byteCode) throws ClassNotFoundException {
    	return ClassLoaders.loadOrDefineByByteCode(byteCode, this);
    }
    
    
    @Override
    public InputStream getResourceAsStream(String name) {
    	ClassLoader parentClassLoader = getParent();
    	InputStream inputStream = null;
    	if (parentClassLoader != null) {
    		inputStream = parentClassLoader.getResourceAsStream(name);
    	}
    	if (inputStream == null && name.endsWith(".class")) {
    		inputStream = getByteCodeAsInputStream(name);
    	}
    	return inputStream;
    }
    
	protected InputStream getByteCodeAsInputStream(String classRelativePath) {
		if (classRelativePath.endsWith(".class")) {
			ByteBuffer byteCode = getByteCode(classRelativePath);
    		if (byteCode != null) {
	    		return new ByteBufferInputStream(
	    			byteCode
	    		);
    		}
    	}
		return null;
	}
    
	ByteBuffer getByteCode(String classRelativePath) {
		try {
			String className = classRelativePath.substring(0, classRelativePath.lastIndexOf(".class")).replace("/", ".");
			ByteBuffer byteCode = loadedByteCodes.get(className);
			if (byteCode == null) {
				byteCode = notLoadedByteCodes.get(className);
			}
			return byteCode;
    	} catch (Throwable exc) {
    		if (!isClosed) {
    			throw exc;
    		} else {
    			ManagedLoggersRepository.logWarn(getClass()::getName, "Could not execute getByteCode on {} because {} has been closed", classRelativePath, this.toString());
    		}
    	}    
		return null;
	}
    
    
    protected void addLoadedByteCode(String className, ByteBuffer byteCode) {
    	try {
    		loadedByteCodes.put(className, byteCode);
    	} catch (Throwable exc) {
    		if (!isClosed) {
    			throw exc;
    		} else {
    			ManagedLoggersRepository.logWarn(getClass()::getName, "Could not execute addLoadedByteCode on {} because {} has been closed", className, this.toString());
    		}
    	}    
    }
    
    
	@Override
    protected Class<?> findClass(String className) throws ClassNotFoundException {
		Class<?> cls = null;
		try {
			ByteBuffer byteCode = notLoadedByteCodes.get(className);
			if (byteCode != null) {
				try {
					cls = _defineClass(className, byteCode, null);
	        		definePackageOf(cls);
	        	} catch (NoClassDefFoundError exc) {
	        		String notFoundClassName = Classes.retrieveName(exc);
	        		removeNotLoadedBytecode(className);
	        		ManagedLoggersRepository.logWarn(getClass()::getName, "Could not load class " + className + " because class " + notFoundClassName + 
						" could not be found, so it will be removed: " + exc.toString()
					);
	    			throw exc;
	        	}
			} else {
				ManagedLoggersRepository.logWarn(getClass()::getName, "Bytecode of class {} not found", className);
			}
		} catch (Throwable exc) {
			if (isClosed) {
				ManagedLoggersRepository.logWarn(getClass()::getName, "Could not load class {} because {} has been closed", className, this.toString());
			} else {
				throw exc;
			}
		}
		if (cls != null) {
			return cls;
		} else {
			throw new ClassNotFoundException(className);
		}
	}
	
	Class<?> _defineClass(String className, java.nio.ByteBuffer byteCode, ProtectionDomain protectionDomain) {
		synchronized(getClassLoadingLock(className)) {
			Class<?> cls = super.defineClass(className, byteCode, protectionDomain);
			addLoadedByteCode(className, byteCode);
			removeNotLoadedBytecode(className);
			return cls;
		}
	}

	public void removeNotLoadedBytecode(String className) {
		try {
			notLoadedByteCodes.remove(className);
    	} catch (Throwable exc) {
    		if (!isClosed) {
    			throw exc;
    		} else {
    			ManagedLoggersRepository.logWarn(getClass()::getName, "Could not execute removeNotLoadedBytecode on class named {} because {} has been closed", className, this.toString());
    		}
    	}    
	}
	
	
	public Set<Class<?>> getLoadedClassesForPackage(Predicate<Package> packagePredicate	) {
		return ClassLoaders.retrieveLoadedClassesForPackage(this, packagePredicate);
	}
	
	Map<String, ByteBuffer> getLoadedBytecodes() {
		return loadedByteCodes;
	}
		
	public Collection<Class<?>> forceBytecodesLoading() {
		Collection<Class<?>> loadedClasses = new HashSet<>();
		for (Map.Entry<String, ByteBuffer> entry : new HashMap<>(notLoadedByteCodes).entrySet()){
			try {
				loadedClasses.add(loadClass(entry.getKey()));
			} catch (Throwable exc) {
				ManagedLoggersRepository.logWarn(getClass()::getName, "Could not load class " + entry.getKey(), exc.getMessage());
			}
		}
		return loadedClasses;
	}
	
	@Override
	public MemoryClassLoader clear () {
		Map<String, ByteBuffer> notLoadedByteCodes = this.notLoadedByteCodes;
		Map<String, ByteBuffer> loadedByteCodes = this.loadedByteCodes;
		this.notLoadedByteCodes = new HashMap<>();
		this.loadedByteCodes = new HashMap<>();
		BackgroundExecutor.createTask(() -> {
			IterableObjectHelper.deepClear(notLoadedByteCodes);
			IterableObjectHelper.deepClear(loadedByteCodes);
		}, Thread.MIN_PRIORITY).submit();
		return this;
	}
	
	protected void unregister() {
		ClassLoaders.unregister(this);
		Cache.classLoaderForConstructors.remove(this, true);
		Cache.classLoaderForFields.remove(this, true);
		Cache.classLoaderForMethods.remove(this, true);
		Cache.uniqueKeyForFields.remove(this, true);
		Cache.uniqueKeyForConstructors.remove(this, true);
		Cache.uniqueKeyForMethods.remove(this, true);
		Cache.bindedFunctionalInterfaces.remove(this, true);
		Cache.uniqueKeyForExecutableAndMethodHandle.remove(this, true);
	}
	
	public synchronized boolean register(Object client) {
		Collection<Object> clients = this.clients;
		if (!isClosed) {
			clients.add(client);
			return true;
		}
		return false;
	}
	
	public synchronized boolean unregister(Object client, boolean close) {
		Collection<Object> clients = this.clients;
		if (!isClosed) {
			clients.remove(client);
			if (clients.isEmpty() && close) {
				close();
				return true;
			}
		}
		return false;
	}
	
	@Override
	public void close() {
		closeResources();
	}
	
	Task closeResources() {
		return closeResources(MemoryClassLoader.class.getName() + "@" + System.identityHashCode(this), () -> isClosed, () -> {
			Collection<Object> clients = this.clients;
			if (clients != null && !clients.isEmpty()) {
				Throwables.throwException("Could not close {} because there are {} registered clients", this, clients.size());
			}
			isClosed = true;
			ClassLoader parentClassLoader = getParent();
			if (parentClassLoader != null && parentClassLoader instanceof MemoryClassLoader) {
				((MemoryClassLoader)parentClassLoader).unregister(this,true);
			}
			clear();
			notLoadedByteCodes = null;
			loadedByteCodes = null;
			Collection<Class<?>> loadedClasses = ClassLoaders.retrieveLoadedClasses(this);
			loadedClasses.clear();
			unregister();
			this.clients.clear();
			this.clients = null;
		});
	}
}
