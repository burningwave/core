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
import static org.burningwave.core.assembler.StaticComponentContainer.Cache;
import static org.burningwave.core.assembler.StaticComponentContainer.ClassLoaders;
import static org.burningwave.core.assembler.StaticComponentContainer.Classes;
import static org.burningwave.core.assembler.StaticComponentContainer.Strings;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.security.ProtectionDomain;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
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
import org.burningwave.core.function.ThrowingSupplier;
import org.burningwave.core.io.ByteBufferInputStream;
import org.burningwave.core.io.FileSystemItem;


public class MemoryClassLoader extends ClassLoader implements Component {

	protected Map<String, ByteBuffer> notLoadedByteCodes;
	protected Map<String, ByteBuffer> loadedByteCodes;
	
	static {
        ClassLoader.registerAsParallelCapable();
    }
	
	protected MemoryClassLoader(
		ClassLoader parentClassLoader
	) {
		super(parentClassLoader);
		this.notLoadedByteCodes = new HashMap<>();
		this.loadedByteCodes = new HashMap<>();
	}
	
	public static MemoryClassLoader create(ClassLoader parentClassLoader) {
		return new MemoryClassLoader(parentClassLoader);
	}

	public void addByteCode(String className, ByteBuffer byteCode) {
    	if (ClassLoaders.retrieveLoadedClass(this, className) == null) {
    		synchronized (Classes.getId(notLoadedByteCodes, className)) {
    			notLoadedByteCodes.put(className, byteCode);
    		}
		} else {
			logDebug("Could not add compiled class {} cause it's already defined", className);
		}
    }
    
    public Map.Entry<String, ByteBuffer> getNotLoadedByteCodes(String name) {
    	for (Map.Entry<String, ByteBuffer> entry : notLoadedByteCodes.entrySet()){
    	    if (entry.getKey().equals(name)) {
    	    	return entry;
    	    }
    	}
    	return null;
    }
    
    public ByteBuffer getByteCodeOf(String name) {
    	return Optional.ofNullable(getNotLoadedByteCodes(name)).map(entry -> entry.getValue()).orElse(null);
    }
    
    void addByteCodes(Map<String, ByteBuffer> classes) {
		Set<String> clsNames = classes.keySet();
		Iterator<String> clsNamesItr = clsNames.iterator();
		while (clsNamesItr.hasNext()) {
			String clsName = clsNamesItr.next();
			addByteCode(clsName, classes.get(clsName));
		}
    }
    
    public void addByteCodes(Collection<Entry<String, ByteBuffer>> classes) {
    	classes.forEach(classObject -> addByteCode(classObject.getKey(), classObject.getValue()));	
	} 

    
    @SuppressWarnings("unchecked")
	public void addByteCodes(Entry<String, ByteBuffer>... classes) {
    	Stream.of(classes).forEach(classObject -> addByteCode(classObject.getKey(), classObject.getValue()));	
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
    	return ClassLoaders.loadOrUploadJavaClass(toLoad, this);
    }
    
    public Class<?> loadOrUploadClass(ByteBuffer byteCode) throws ClassNotFoundException {
    	return ClassLoaders.loadOrUploadByteCode(byteCode, this);
    }
    
    
    @Override
    public InputStream getResourceAsStream(String name) {
    	InputStream inputStream = super.getResourceAsStream(name);
    	if (inputStream == null && name.endsWith(".class")) {
    		inputStream = getByteCodeAsInputStream(name);
    	}
    	return inputStream;
    }
    
	@Override
	public URL getResource(String resourceName) {
		Enumeration<URL> urls = getResources(resourceName, true);
		if (urls.hasMoreElements()) {
			return urls.nextElement();
		}
		return null;
	}
	
	@Override
	public Enumeration<URL> getResources(String resourceName) throws IOException {
		return getResources(resourceName, false);
	}
    
	private Enumeration<URL> getResources(String resourceName, boolean findFirst) {
		Collection<URL> resources = new LinkedHashSet<>();
		Enumeration<URL> resourceEnum = ThrowingSupplier.get(() -> super.getResources(resourceName));
		while (resourceEnum.hasMoreElements()) {
			resources.add(resourceEnum.nextElement());
		}
		if (resourceName.endsWith(".class")) {
			File temporaryFolder = getOrCreateTemporaryFolder();
			File compiledClass = new File(temporaryFolder.getAbsolutePath() + "/" + resourceName);
			if (compiledClass.exists()) {
				resources.add(FileSystemItem.ofPath(compiledClass.getAbsolutePath()).getURL());
			} else {
				Optional.ofNullable(getByteCode(resourceName)).ifPresent(byteCode -> 
					resources.add(
						JavaClass.create(
							byteCode
						).storeToClassPath(
							temporaryFolder.getAbsolutePath()
						).getURL()
					)
				);
			}			
		}
		return Collections.enumeration(resources);
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
		String className = classRelativePath.substring(0, classRelativePath.lastIndexOf(".class")).replace("/", ".");
		ByteBuffer byteCode = loadedByteCodes.get(className);
		if (byteCode == null) {
			byteCode = notLoadedByteCodes.get(className);
		}
		return byteCode;
	}
    
    
    protected void addLoadedByteCode(String className, ByteBuffer byteCode) {
    	synchronized (Classes.getId(loadedByteCodes, className)) {
    		loadedByteCodes.put(className, byteCode);
		}
    }
    
    
	@Override
    protected Class<?> findClass(String className) throws ClassNotFoundException {
		Class<?> cls = null;
		ByteBuffer byteCode = notLoadedByteCodes.get(className);
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
		addLoadedByteCode(className, byteCode);
		removeNotLoadedCompiledClass(className);
		return cls;
	}

	public void removeNotLoadedCompiledClass(String className) {
		synchronized (Classes.getId(notLoadedByteCodes, className)) {
			notLoadedByteCodes.remove(className);
		}
	}
	
	
	public Set<Class<?>> getLoadedClassesForPackage(Predicate<Package> packagePredicate	) {
		return ClassLoaders.retrieveLoadedClassesForPackage(this, packagePredicate);
	}
	
	Map<String, ByteBuffer> getLoadedCompiledClasses() {
		return loadedByteCodes;
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
		for (Map.Entry<String, ByteBuffer> entry : notLoadedByteCodes.entrySet()){
			try {
				loadClass(entry.getKey());
			} catch (Throwable exc) {
				logWarn("Could not load class " + entry.getKey(), exc.getMessage());
			}
		}
		return notLoadedByteCodes.keySet();
	}
	
	public void clear () {
		notLoadedByteCodes.clear();
		loadedByteCodes.clear();
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
		notLoadedByteCodes = null;
		loadedByteCodes = null;
	}
}