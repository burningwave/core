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

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.burningwave.Throwables;
import org.burningwave.core.Component;
import org.burningwave.core.Virtual;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.JavaMemoryCompiler.MemoryFileObject;
import org.burningwave.core.common.Streams;
import org.burningwave.core.common.Strings;
import org.burningwave.core.function.ThrowingSupplier;
import org.burningwave.core.io.ByteBufferInputStream;
import org.burningwave.core.io.StreamHelper;
import org.burningwave.core.reflection.ObjectRetriever;
import org.objectweb.asm.ClassReader;

@SuppressWarnings("restriction")
public class ClassHelper implements Component {
	private ObjectRetriever objectRetriever;
	private ClassFactory classFactory;
	private Supplier<ClassFactory> classFactorySupplier;
	private StreamHelper streamHelper;
	private Map<String, ClassLoaderDelegate> classLoaderDelegates;
	private ClassHelper(
		Supplier<ClassFactory> classFactorySupplier,
		StreamHelper streamHelper,
		ObjectRetriever objectRetriever
	) {
		this.classFactorySupplier = classFactorySupplier;
		this.objectRetriever = objectRetriever;
		this.streamHelper = streamHelper;
		this.classLoaderDelegates = new ConcurrentHashMap<>();
	}
	
	public static ClassHelper create(Supplier<ClassFactory> classFactorySupplier, StreamHelper streamHelper, ObjectRetriever objectRetriever) {
		return new ClassHelper(classFactorySupplier, streamHelper, objectRetriever);
	}
	
	private ClassFactory getClassFactory() {
		if (classFactory == null) {
			classFactory = classFactorySupplier.get();
		}
		return classFactory;
	}
	
	public ClassLoaderDelegate getClassLoaderDelegate(String name) {
		ClassLoaderDelegate classLoaderDelegate = classLoaderDelegates.get(name);
		if (classLoaderDelegate == null) {
			synchronized(classLoaderDelegates) {
				classLoaderDelegate = classLoaderDelegates.get(name);
				if (classLoaderDelegate == null) {
					try {
						Collection<MemoryFileObject> classLoaderDelegateByteCode = getClassFactory().build(
							this.streamHelper.getResourceAsStringBuffer(
								ClassLoaderDelegate.class.getPackage().getName().replaceAll("\\.", "/") + "/" + name + ".java"
							).toString()
						);
						byte[] byteCode = classLoaderDelegateByteCode.stream().findFirst().get().toByteArray();
						Class<?> cls = objectRetriever.getUnsafe().defineAnonymousClass(ClassLoaderDelegate.class, byteCode, null);
						classLoaderDelegate = (ClassLoaderDelegate) cls.getConstructor().newInstance();
						classLoaderDelegates.put(name, classLoaderDelegate);
					} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
							| InvocationTargetException | NoSuchMethodException | SecurityException exc) {
						throw Throwables.toRuntimeException(exc);
					}
				}
			}
		}
		return classLoaderDelegate;
	}
	
	public String extractClassName(String classCode) {
		return
			Optional.ofNullable(
				Strings.extractAllGroups(
					Pattern.compile("(package)\\s*([[a-zA-Z0-9\\s]*\\.?]*)"), classCode
				).get(2).get(0)
			).map(
				value -> value + "."
			).orElse("") +
			Strings.extractAllGroups(
				Pattern.compile("(?<=\\n|\\A)(?:public\\s*)?(class|interface|enum)\\s*([^\\n\\s<]*)"), classCode
			).get(2).get(0);
	}
	
	public String extractClassName(ByteBuffer byteCode) {
		return ThrowingSupplier.get(() -> {
			try (ByteBufferInputStream inputStream = new ByteBufferInputStream(byteCode)){
				return new ClassReader(inputStream).getClassName().replace("/", ".");
			}
		});
	}
	
	public ByteBuffer getByteCode(Class<?> cls) {
		ClassLoader clsLoader = cls.getClassLoader();
		if (clsLoader == null) {
			clsLoader = ClassLoader.getSystemClassLoader();
		}
		InputStream inputStream = clsLoader.getResourceAsStream(
			cls.getName().replace(".", "/") + ".class"
		);
		return Streams.toByteBuffer(
			Objects.requireNonNull(inputStream, "Could not acquire bytecode for class " + cls.getName())
		);
	}
	
	public String extractClassName(InputStream inputStream) {
		return ThrowingSupplier.get(() -> 
			new ClassReader(inputStream).getClassName().replace("/", ".")
		);
	}

	
	public Class<?> loadOrUploadClass(
		Class<?> toLoad, 
		ClassLoader classLoader
	) throws ClassNotFoundException {
		return loadOrUploadClass(
			toLoad, classLoader,
			objectRetriever.getDefineClassMethod(classLoader), 
			objectRetriever.retrieveLoadedClasses(classLoader),
			objectRetriever.getDefinePackageMethod(classLoader)
		);
	}
	
	private Class<?> loadOrUploadClass(
		Class<?> toLoad, 
		ClassLoader classLoader, 
		Method defineClassMethod, 
		Collection<Class<?>> definedClasses,
		Method definePackageMethod
	) throws ClassNotFoundException {
    	try {
    		return classLoader.loadClass(toLoad.getName());
    	} catch (ClassNotFoundException | NoClassDefFoundError outerEx) {
    		try {
    			Class<?> cls = defineClass(classLoader, defineClassMethod, toLoad.getName(), Streams.shareContent(getByteCode(toLoad)));
    			definePackageFor(cls, classLoader, definePackageMethod);
    			return cls;
			} catch (ClassNotFoundException | NoClassDefFoundError outerExc) {
				String newNotFoundClassName = outerExc.getMessage().replace("/", ".");
				loadOrUploadClass(
        			Class.forName(
        				newNotFoundClassName, false, toLoad.getClassLoader()
        			),
        			classLoader, defineClassMethod, definedClasses, definePackageMethod
        		);
				return loadOrUploadClass(
        			Class.forName(
        					toLoad.getName(), false, toLoad.getClassLoader()
        			),
        			classLoader, defineClassMethod, definedClasses, definePackageMethod
        		);
			}
    	}
    }

	private Class<?> defineClass(
		ClassLoader classLoader, 
		Method method, 
		String className,
		ByteBuffer byteCode
	) throws ClassNotFoundException {
		try {
			return (Class<?>)method.invoke(classLoader, className, byteCode, null);
		} catch (InvocationTargetException iTE) {
			Throwable targetExcption = iTE.getTargetException();
			if (targetExcption instanceof ClassNotFoundException) {
				throw (ClassNotFoundException)iTE.getTargetException();
			} else if (targetExcption instanceof NoClassDefFoundError) {
				throw (NoClassDefFoundError)iTE.getTargetException();
			}
			throw Throwables.toRuntimeException(iTE);
		} catch (Throwable exc) {
			throw Throwables.toRuntimeException(exc);
		}
	}
	
	
    private Package definePackage(
		String name, String specTitle,
		String specVersion, String specVendor, String implTitle,
		String implVersion, String implVendor, URL sealBase,
		ClassLoader classLoader,
		Method definePackageMethod
	) throws IllegalArgumentException {
    	return ThrowingSupplier.get(() -> {
    		try {
    			return (Package) definePackageMethod.invoke(classLoader, name, specTitle, specVersion, specVendor, implTitle,
    				implVersion, implVendor, sealBase);
    		} catch (IllegalArgumentException exc) {
    			logWarn("Package " + name + " already defined");
    			return retrievePackage(classLoader, name);
    		}
		});
    }
    
	private void definePackageFor(Class<?> cls, 
		ClassLoader classLoader,
		Method definePackageMethod
	) {
		if (cls.getName().contains(".")) {
			String pckgName = cls.getName().substring(
		    	0, cls.getName().lastIndexOf(".")
		    );
		    Package pkg = retrievePackage(classLoader, pckgName);
		    if (pkg == null) {
		    	pkg = definePackage(pckgName, null, null, null, null, null, null, null, classLoader, definePackageMethod);
			}	
		}
	}
	
	public Class<?> retrieveLoadedClass(ClassLoader classLoader, String className) {
		Vector<Class<?>> definedClasses = objectRetriever.retrieveLoadedClasses(classLoader);
		synchronized(definedClasses) {
			Iterator<?> itr = definedClasses.iterator();
			while(itr.hasNext()) {
				Class<?> cls = (Class<?>)itr.next();
				if (cls.getName().equals(className)) {
					return cls;
				}
			}
		}
		if (classLoader.getParent() != null) {
			return retrieveLoadedClass(classLoader.getParent(), className);
		}
		return null;
	}	
	
	public Set<Class<?>> retrieveLoadedClassesForPackage(ClassLoader classLoader, Predicate<Package> packagePredicate) {
		Set<Class<?>> classesFound = ConcurrentHashMap.newKeySet();
		Vector<Class<?>> definedClasses = objectRetriever.retrieveLoadedClasses(classLoader);
		synchronized(definedClasses) {
			Iterator<?> itr = definedClasses.iterator();
			while(itr.hasNext()) {
				Class<?> cls = (Class<?>)itr.next();
				Package classPackage = cls.getPackage();
				if (packagePredicate.test(classPackage)) {
					classesFound.add(cls);
				}
			}
		}
		if (classLoader.getParent() != null) {
			classesFound.addAll(retrieveLoadedClassesForPackage(classLoader.getParent(), packagePredicate));
		}
		return classesFound;
	}
	
	public Package retrievePackage(ClassLoader classLoader, String packageName) {
		Map<String, ?> packages = objectRetriever.retrievePackages(classLoader);
		Object packageToFind = packages.get(packageName);
		if (packageToFind != null) {
			return objectRetriever.retrievePackage(classLoader, packageToFind, packageName);
		} else if (classLoader.getParent() != null) {
			return retrievePackage(classLoader.getParent(), packageName);
		} else {
			return null;
		}
	}
	
	public <T> T executeCode(
		String imports, 
		String className, 
		String supplierCode, 
		Class<?> returnedClass,
		ComponentSupplier componentSupplier
	) {	
		return executeCode(imports, className, supplierCode, returnedClass, componentSupplier, Thread.currentThread().getContextClassLoader());
	}
	
	
	public <T> T executeCode(
		String imports, 
		String className, 
		String supplierCode, 
		Class<?> returnedClass,
		ComponentSupplier componentSupplier,
		ClassLoader classLoader
	) {	
		return ThrowingSupplier.get(() -> {
			try (MemoryClassLoader memoryClassLoader = 
				MemoryClassLoader.create(
					classLoader,
					this
				)
			) {
				Class<?> virtualClass = getClassFactory().getOrBuildCodeExecutorSubType(
					imports, className, supplierCode, returnedClass, componentSupplier, memoryClassLoader
				);
				Virtual virtualObject = ((Virtual)virtualClass.getDeclaredConstructor().newInstance());
				T retrievedElement = virtualObject.invokeWithoutCachingMethod("execute", componentSupplier);
				return retrievedElement;
			}
		});
	}
	
	public void unregister(ClassLoader classLoader) {
		objectRetriever.unregister(classLoader);
	}
	
	@Override
	public void close() {
		objectRetriever = null;
		classFactory = null;
		classFactorySupplier = null;
	}
	
	public static abstract class ClassLoaderDelegate {
		
		public abstract Package getPackage(ClassLoader classLoader, String packageName);
		
	}
}
