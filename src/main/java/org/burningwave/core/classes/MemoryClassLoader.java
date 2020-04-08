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
import static org.burningwave.core.assembler.StaticComponentContainer.ClassLoaders;
import static org.burningwave.core.assembler.StaticComponentContainer.Cache;
import static org.burningwave.core.assembler.StaticComponentContainer.Classes;
import static org.burningwave.core.assembler.StaticComponentContainer.Strings;

import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.security.ProtectionDomain;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.burningwave.core.Component;
import org.burningwave.core.io.ByteBufferInputStream;


public class MemoryClassLoader extends ClassLoader implements Component {

	protected Map<String, ByteBuffer> notLoadedCompiledClasses;
	protected Map<String, ByteBuffer> loadedCompiledClasses;
	
	static {
        ClassLoader.registerAsParallelCapable();
    }
	
	protected MemoryClassLoader(
		ClassLoader parentClassLoader
	) {
		super(parentClassLoader);
		this.notLoadedCompiledClasses = new HashMap<>();
		this.loadedCompiledClasses = new HashMap<>();
	}
	
	public static MemoryClassLoader create(ClassLoader parentClassLoader) {
		return new MemoryClassLoader(parentClassLoader);
	}

	public void addCompiledClass(String className, ByteBuffer byteCode) {
    	if (ClassLoaders.retrieveLoadedClass(this, className) == null) {
    		synchronized (Classes.getId(notLoadedCompiledClasses, className)) {
    			notLoadedCompiledClasses.put(className, byteCode);
    		}
		} else {
			logDebug("Could not add compiled class {} cause it's already defined", className);
		}
    }
    
    public Map.Entry<String, ByteBuffer> getNotLoadedCompiledClass(String name) {
    	for (Map.Entry<String, ByteBuffer> entry : notLoadedCompiledClasses.entrySet()){
    	    if (entry.getKey().equals(name)) {
    	    	return entry;
    	    }
    	}
    	return null;
    }
    
    public ByteBuffer getByteCodeOf(String name) {
    	return Optional.ofNullable(getNotLoadedCompiledClass(name)).map(entry -> entry.getValue()).orElse(null);
    }
    
    void addCompiledClasses(Map<String, ByteBuffer> classes) {
		Set<String> clsNames = classes.keySet();
		Iterator<String> clsNamesItr = clsNames.iterator();
		while (clsNamesItr.hasNext()) {
			String clsName = clsNamesItr.next();
			addCompiledClass(clsName, classes.get(clsName));
		}
    }
    
    public void addCompiledClasses(Collection<Entry<String, ByteBuffer>> classes) {
    	classes.forEach(classObject -> addCompiledClass(classObject.getKey(), classObject.getValue()));	
	} 

    
    @SuppressWarnings("unchecked")
	public void addCompiledClasses(Entry<String, ByteBuffer>... classes) {
    	Stream.of(classes).forEach(classObject -> addCompiledClass(classObject.getKey(), classObject.getValue()));	
	} 
    
	public boolean hasPackageBeenDefined(String packageName) {
		if (Strings.isNotEmpty(packageName)) {
			return ClassLoaders.retrieveLoadedPackage(this, packageName) != null;
		} else {
			return true;
		}
	}
    
    @Override
    protected Package definePackage(String packageName, String specTitle,
		String specVersion, String specVendor, String implTitle,
		String implVersion, String implVendor, URL sealBase
	) throws IllegalArgumentException {
    	Package pkg = null;
    	if (Strings.isNotEmpty(packageName)) {
    		pkg = ClassLoaders.retrieveLoadedPackage(this, packageName);
    		if (pkg == null) {
    			try {
    				pkg = super.definePackage(packageName, specTitle, specVersion, specVendor, implTitle,
    		    			implVersion, implVendor, sealBase);
    			} catch (IllegalArgumentException exc) {
    				logWarn("Package " + packageName + " already defined");
    				pkg = ClassLoaders.retrieveLoadedPackage(this, packageName);
    			}
    		}
    	}
    	return pkg;
    }
    
	protected void definePackageOf(Class<?> cls) {
		if (cls.getName().contains(".")) {
			String pckgName = cls.getName().substring(
		    	0, cls.getName().lastIndexOf(".")
		    );
		    if (ClassLoaders.retrieveLoadedPackage(this, pckgName) == null) {
		    	definePackage(pckgName, null, null, null, null, null, null, null);
			}	
		}
	}
    
    @Override
    protected Class<?> loadClass(String className, boolean resolve) throws ClassNotFoundException {
    	Class<?> cls = null;
    	try {
			cls = super.loadClass(className, resolve);
		} catch (SecurityException exc) {
			cls = Class.forName(className);
		}
    	removeNotLoadedCompiledClass(className);
    	return cls;
    }
    
    
    public Class<?> loadOrUploadClass(Class<?> toLoad) throws ClassNotFoundException {
    	return ClassLoaders.loadOrUploadClass(toLoad, this);
    }
    
    public Class<?> loadOrUploadClass(JavaClass toLoad) throws ClassNotFoundException {
    	return ClassLoaders.loadOrUploadClass(toLoad, this);
    }
    
    public Class<?> loadOrUploadClass(ByteBuffer byteCode) throws ClassNotFoundException {
    	return ClassLoaders.loadOrUploadClass(byteCode, this);
    }
    
    
    @Override
    public InputStream getResourceAsStream(String name) {
    	InputStream inputStream = super.getResourceAsStream(name);
    	if (inputStream == null && name.endsWith(".class")) {
    		inputStream = getCompiledClassAsInputStream(name);
    	}
    	return inputStream;
    }

	protected InputStream getCompiledClassAsInputStream(String classRelativePath) {
		if (classRelativePath.endsWith(".class")) {
			String className = classRelativePath.substring(0, classRelativePath.lastIndexOf(".class")).replace("/", ".");
    		ByteBuffer byteCode = loadedCompiledClasses.get(className);
    		if (byteCode == null) {
    			byteCode = notLoadedCompiledClasses.get(className);
    		}
    		if (byteCode == null) {
	    		return new ByteBufferInputStream(
	    			byteCode
	    		);
    		}
    	}
		return null;
	}
    
    
    protected void addLoadedCompiledClass(String className, ByteBuffer byteCode) {
    	synchronized (Classes.getId(loadedCompiledClasses, className)) {
    		loadedCompiledClasses.put(className, byteCode);
		}
    }
    
    
	@Override
    protected Class<?> findClass(String className) throws ClassNotFoundException {
		Class<?> cls = null;
		ByteBuffer byteCode = notLoadedCompiledClasses.get(className);
		if (byteCode != null) {
			try {
				cls = _defineClass(className, byteCode, null);
        		definePackageOf(cls);
        	} catch (NoClassDefFoundError noClassDefFoundError) {
        		String notFoundClassName = Classes.retrieveName(noClassDefFoundError);
        		while (!notFoundClassName.equals(className)) {
        			try {
        				//This search over all ClassLoader Parents
        				loadClass(notFoundClassName, false);
        				cls = loadClass(className, false);
        			} catch (ClassNotFoundException exc) {
        				String newNotFoundClass = Classes.retrieveName(noClassDefFoundError);
        				if (newNotFoundClass.equals(notFoundClassName)) {
        					break;
        				} else {
        					notFoundClassName = newNotFoundClass;
        				}
        			}
        		}
        		if (cls == null) {
        			removeNotLoadedCompiledClass(className);
    				logWarn("Could not load compiled class " + className + ", so it will be removed: " + noClassDefFoundError.toString());
        			throw noClassDefFoundError;
        		}
        	}
			if (cls == null){
        		removeNotLoadedCompiledClass(className);
        		logDebug("Could not load compiled class " + className + ", so it will be removed");
        	}
		} else {
			logDebug("Compiled class " + className + " not found");
		}
		if (cls != null) {
			return cls;
		} else {
			throw new ClassNotFoundException(className);
		}
	}

	protected Class<?> _defineClass(String className, java.nio.ByteBuffer byteCode, ProtectionDomain protectionDomain) {
		Class<?> cls = super.defineClass(className, byteCode, protectionDomain);
		addLoadedCompiledClass(className, byteCode);
		removeNotLoadedCompiledClass(className);
		return cls;
	}

	public void removeNotLoadedCompiledClass(String className) {
		synchronized (Classes.getId(notLoadedCompiledClasses, className)) {
			notLoadedCompiledClasses.remove(className);
		}
	}
	
	
	public Set<Class<?>> getLoadedClassesForPackage(Predicate<Package> packagePredicate	) {
		return ClassLoaders.retrieveLoadedClassesForPackage(this, packagePredicate);
	}
	
	Map<String, ByteBuffer> getLoadedCompiledClasses() {
		return loadedCompiledClasses;
	}
		
	public void forceCompiledClassesLoading() {
		Set<String> compiledClassesNotLoaded = new LinkedHashSet<>(loadCompiledClassesNotLoaded());
		if (!compiledClassesNotLoaded.isEmpty()) {	
			Set<String> compiledClassesNotLoaded2 = new LinkedHashSet<>(loadCompiledClassesNotLoaded());
			if (!compiledClassesNotLoaded2.isEmpty() && !compiledClassesNotLoaded2.containsAll(compiledClassesNotLoaded)) {
				forceCompiledClassesLoading();
			}
		}
	}
	
	public Set<String> loadCompiledClassesNotLoaded() {
		for (Map.Entry<String, ByteBuffer> entry : notLoadedCompiledClasses.entrySet()){
			try {
				loadClass(entry.getKey());
			} catch (Throwable exc) {
				logWarn("Could not load class " + entry.getKey(), exc.getMessage());
			}
		}
		return notLoadedCompiledClasses.keySet();
	}
	
	public void clear () {
		notLoadedCompiledClasses.clear();
		loadedCompiledClasses.clear();
	}
	
	protected void unregister() {
		ClassLoaders.unregister(this);
		Cache.classLoaderForConstructors.remove(this);
		Cache.classLoaderForFields.remove(this);
		Cache.classLoaderForMethods.remove(this);
	}
	
	@Override
	public void close() {
		clear();
		notLoadedCompiledClasses = null;
		loadedCompiledClasses = null;
	}
}