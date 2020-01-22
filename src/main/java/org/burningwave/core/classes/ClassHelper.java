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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.burningwave.Throwables;
import org.burningwave.core.Component;
import org.burningwave.core.Virtual;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.hunter.ByteCodeHunter;
import org.burningwave.core.classes.hunter.ByteCodeHunter.SearchResult;
import org.burningwave.core.classes.hunter.SearchConfig;
import org.burningwave.core.common.Strings;
import org.burningwave.core.function.ThrowingSupplier;
import org.burningwave.core.io.ByteBufferInputStream;
import org.burningwave.core.io.ByteBufferOutputStream;
import org.burningwave.core.io.FileOutputStream;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.io.Streams;
import org.burningwave.core.jvm.LowLevelObjectsHandler;
import org.objectweb.asm.ClassReader;


public class ClassHelper implements Component {
	private LowLevelObjectsHandler lowLevelObjectsHandler;
	private ClassFactory classFactory;
	private Supplier<ClassFactory> classFactorySupplier;
	private ByteCodeHunter byteCodeHunter;
	private Supplier<ByteCodeHunter> byteCodeHunterSupplier;
	private PathHelper pathHelper;

	private ClassHelper(
		Supplier<ClassFactory> classFactorySupplier,
		Supplier<ByteCodeHunter> byteCodeHunterSupplier,
		LowLevelObjectsHandler lowLevelObjectsHandler,
		PathHelper pathHelper
	) {
		this.classFactorySupplier = classFactorySupplier;
		this.byteCodeHunterSupplier = byteCodeHunterSupplier;
		this.lowLevelObjectsHandler = lowLevelObjectsHandler;
		this.pathHelper = pathHelper;
	}
	
	public static ClassHelper create(
		Supplier<ClassFactory> classFactorySupplier,
		Supplier<ByteCodeHunter> byteCodeHunterSupplier,
		LowLevelObjectsHandler objectRetriever,
		PathHelper pathHelper
	) {
		return new ClassHelper(classFactorySupplier, byteCodeHunterSupplier, objectRetriever, pathHelper);
	}
	
	private ClassFactory getClassFactory() {
		return classFactory != null? classFactory : (classFactory = classFactorySupplier.get());
	}
	
	private ByteCodeHunter getByteCodeHunter() {
		return byteCodeHunter != null? byteCodeHunter : (byteCodeHunter = byteCodeHunterSupplier.get());
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
			lowLevelObjectsHandler.getDefineClassMethod(classLoader),
			lowLevelObjectsHandler.getDefinePackageMethod(classLoader)
		);
	}
	
	private Class<?> loadOrUploadClass(
		Class<?> toLoad, 
		ClassLoader classLoader, 
		Method defineClassMethod, 
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
        			classLoader, defineClassMethod, definePackageMethod
        		);
				return loadOrUploadClass(
        			Class.forName(
        					toLoad.getName(), false, toLoad.getClassLoader()
        			),
        			classLoader, defineClassMethod, definePackageMethod
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
		Vector<Class<?>> definedClasses = lowLevelObjectsHandler.retrieveLoadedClasses(classLoader);
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
		Vector<Class<?>> definedClasses = lowLevelObjectsHandler.retrieveLoadedClasses(classLoader);
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
		Map<String, ?> packages = lowLevelObjectsHandler.retrieveLoadedPackages(classLoader);
		Object packageToFind = packages.get(packageName);
		if (packageToFind != null) {
			return lowLevelObjectsHandler.retrieveLoadedPackage(classLoader, packageToFind, packageName);
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
		ComponentSupplier componentSupplier,
		Object... parameters
	) {	
		return executeCode(imports, className, supplierCode, componentSupplier, Thread.currentThread().getContextClassLoader(), parameters);
	}
	
	
	public <T> T executeCode(
		String imports, 
		String className, 
		String supplierCode, 
		ComponentSupplier componentSupplier,
		ClassLoader classLoader,
		Object... parameters
	) {	
		return ThrowingSupplier.get(() -> {
			try (MemoryClassLoader memoryClassLoader = 
				MemoryClassLoader.create(
					classLoader,
					this
				)
			) {
				Class<?> virtualClass = getClassFactory().getOrBuildCodeExecutorSubType(
					imports, className, supplierCode, componentSupplier, memoryClassLoader
				);
				Virtual virtualObject = ((Virtual)virtualClass.getDeclaredConstructor().newInstance());
				T retrievedElement = virtualObject.invokeWithoutCachingMethod("execute", componentSupplier, parameters);
				return retrievedElement;
			}
		});
	}
	
	public void unregister(ClassLoader classLoader) {
		lowLevelObjectsHandler.unregister(classLoader);
	}
	
	@Override
	public void close() {
		lowLevelObjectsHandler = null;
		classFactory = null;
		classFactorySupplier = null;
	}

	public static boolean isLoadedBy(Class<?> cls, ClassLoader classLoader) {
		if (cls.getClassLoader() == classLoader) {
			return true;
 		} else if (classLoader != null && classLoader.getParent() != null) {
 			return isLoadedBy(cls, classLoader.getParent());
 		} else {
 			return false;
 		}
	}
	
	public Dependencies findDependencies(
		Class<?> simulatorClass,
		Consumer<JavaClass> javaClassConsumer,
		BiConsumer<String, ByteBuffer> resourceConsumer
	) {
		return findDependencies(simulatorClass, pathHelper.getMainClassPaths(), javaClassConsumer, resourceConsumer);
	}
	
	public Dependencies findDependencies(
		Class<?> simulatorClass,
		Collection<String> baseClassPaths,
		Consumer<JavaClass> javaClassConsumer,
		BiConsumer<String, ByteBuffer> resourceConsumer
	) {
		final Dependencies result;
		try (SearchResult searchResult = getByteCodeHunter().findBy(
			SearchConfig.forPaths(
				baseClassPaths
			)
		)) {
			result = new Dependencies(searchResult.getClassesFlatMap(), javaClassConsumer, resourceConsumer);
		}
		Set<String> classesNameToBeExcluded = lowLevelObjectsHandler.retrieveAllLoadedClasses(
			this.getClass().getClassLoader()
		).stream().map(clsss -> 
			clsss.getName()).collect((Collectors.toSet())
		);
		result.findingTask = CompletableFuture.runAsync(() -> {
			Class<?> cls;
			try (MemoryClassLoader memoryClassLoader = new MemoryClassLoader(null, this) {
				@Override
				public void addLoadedCompiledClass(String name, ByteBuffer byteCode) {
					super.addLoadedCompiledClass(name, byteCode);
					if (!name.equals(simulatorClass.getName())) {
						result.put(name);
					}
					classesNameToBeExcluded.remove(name);
				};
				
				@Override
			    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
			    	Class<?> cls = super.loadClass(name, resolve);
			    	if (!name.equals(simulatorClass.getName())) {
						result.load(name);	
					}
			    	classesNameToBeExcluded.remove(name);
			    	return cls;
			    }
				
				@Override
			    public InputStream getResourceAsStream(String name) {
			    	InputStream inputStream = super.getResourceAsStream(name);
			    	ByteBufferOutputStream bBOS = new ByteBufferOutputStream();
			    	Streams.copy(inputStream, bBOS);
			    	result.putResource(name, bBOS.toByteBuffer());			    	
			    	return super.getResourceAsStream(name);
			    }
				
			}) {
				for (Entry<String, JavaClass> entry : result.classPathClasses.entrySet()) {
					JavaClass javaClass = entry.getValue();
					memoryClassLoader.addCompiledClass(javaClass.getName(), javaClass.getByteCode());
				}
				try {
					cls = loadOrUploadClass(simulatorClass, memoryClassLoader);
					cls.getMethod("main", String[].class).invoke(null, (Object)new String[]{});
				} catch (Throwable exc) {					
					Set<String> allLoadedClasses = lowLevelObjectsHandler.retrieveAllLoadedClasses(
						this.getClass().getClassLoader()
					).stream().map(clsss -> 
						clsss.getName()).collect((Collectors.toSet())
					);
					allLoadedClasses.removeAll(classesNameToBeExcluded);
					result.loadAll(allLoadedClasses);
					try {
						classesNameToBeExcluded.addAll(allLoadedClasses);
						simulatorClass.getMethod("main", String[].class).invoke(null, (Object)new String[]{});
						allLoadedClasses = lowLevelObjectsHandler.retrieveAllLoadedClasses(
							this.getClass().getClassLoader()
						).stream().map(clsss -> 
							clsss.getName()).collect((Collectors.toSet())
						);
						allLoadedClasses.removeAll(classesNameToBeExcluded);
						result.loadAll(allLoadedClasses);
					} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
							| NoSuchMethodException | SecurityException e) {
						throw Throwables.toRuntimeException(exc);
					}					
				}
			}
		});
		return result;
	}
	
	public Dependencies storeDependencies(
		Class<?> simulatorClass,
		String destinationPath
	) {
		return storeDependencies(simulatorClass, pathHelper.getMainClassPaths(), destinationPath);
	}
	
	public Dependencies storeDependencies(
		Class<?> simulatorClass,
		Collection<String> baseClassPaths,
		String destinationPath
	) {
		Dependencies dependencies = findDependencies(
			simulatorClass,
			baseClassPaths, (javaClass) -> 
				javaClass.storeToClassPath(destinationPath),
			(resourceName, resourceContent) -> {
				try (
					ByteBufferInputStream inputStream = new ByteBufferInputStream(resourceContent);
					FileOutputStream outputStream = FileOutputStream.create(new File(destinationPath + "/" + resourceName))
				) {
					Streams.copy(inputStream, outputStream);
				} catch (IOException exc) {
					logError("Could not persist resource resourceName", exc);
				}
			}					
		);
		dependencies.store = FileSystemItem.ofPath(destinationPath);
		return dependencies;
	}
	
	public static class Dependencies implements Component{
		private CompletableFuture<Void> findingTask;
		private final Map<String, JavaClass> classPathClasses;
		private Map<String, ByteBuffer> resources;
		private Map<String, JavaClass> result;
		private FileSystemItem store;
		private Consumer<JavaClass> javaClassConsumer;
		private BiConsumer<String, ByteBuffer> resourceConsumer;
		
		private Dependencies(Map<String, JavaClass> classPathClasses, Consumer<JavaClass> javaClassConsumer, BiConsumer<String, ByteBuffer> resourceConsumer) {
			this.result = new ConcurrentHashMap<>();
			this.resources = new ConcurrentHashMap<>();
			this.classPathClasses = new ConcurrentHashMap<>();
			this.classPathClasses.putAll(classPathClasses);
			this.javaClassConsumer = javaClassConsumer;
			this.resourceConsumer = resourceConsumer;
		}
		
		public JavaClass load(String className) {
			for (Map.Entry<String, JavaClass> entry : classPathClasses.entrySet()) {
				if (entry.getValue().getName().equals(className)) {
					result.put(entry.getKey(), entry.getValue());
					if (javaClassConsumer != null) {
						javaClassConsumer.accept(entry.getValue());
					}
					return entry.getValue();
				}
			}
			return null;
		}
		
		public Collection<JavaClass> loadAll(Collection<String> classesName) {
			Collection<JavaClass> javaClassAdded = new LinkedHashSet<>();
			for (Map.Entry<String, JavaClass> entry : classPathClasses.entrySet()) {
				if (classesName.contains(entry.getValue().getName())) {
					result.put(entry.getKey(), entry.getValue());
					javaClassAdded.add(entry.getValue());
					classesName.remove(entry.getValue().getName());
					if (javaClassConsumer != null) {
						javaClassConsumer.accept(entry.getValue());
					}
				}
			}
			return javaClassAdded;
		}

		public void putResource(String name, ByteBuffer bytes) {
			resources.put(name, bytes);
			if (resourceConsumer != null) {
	    		resourceConsumer.accept(name, Streams.shareContent(bytes));
	    	}
		}

		private JavaClass put(String className) {
			for (Map.Entry<String, JavaClass> entry : classPathClasses.entrySet()) {
				if (entry.getValue().getName().equals(className)) {
					result.put(entry.getKey(), entry.getValue());
					if (javaClassConsumer != null) {
						javaClassConsumer.accept(entry.getValue());
					}
					return entry.getValue();
				}
			}
			return null;
		}
		
		public Map<String, JavaClass> get() {
			return result;
		}
		
		public CompletableFuture<Void> getFindingTask() {
			return this.findingTask;
		}
		
		public void waitForTaskEnding() {
			findingTask.join();
		}
		
		public FileSystemItem getStore() {
			return store;
		}
		
		@Override
		public void close() {
			findingTask.cancel(true);
			findingTask = null;
			classPathClasses.clear();
			resources.clear();
			resources = null;
			result.clear();
			result = null;
			store = null;
		}
	}
}