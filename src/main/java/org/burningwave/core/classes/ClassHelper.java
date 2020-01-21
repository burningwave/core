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
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Vector;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

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
	
	public Map<String, JavaClass> findDependenciesFastMode(Class<?> simulatorClass) {
		SearchResult searchResult = getByteCodeHunter().findBy(
			SearchConfig.forPaths(
				pathHelper.getMainClassPaths()
			)
		);
		try (MemoryClassLoader memoryClassLoader = MemoryClassLoader.create(null, this)) {
			for (Entry<String, JavaClass> entry : searchResult.getClassesFlatMap().entrySet()) {
				JavaClass javaClass = entry.getValue();
				memoryClassLoader.addCompiledClass(javaClass.getName(), javaClass.getByteCode());
			}
			Class<?> cls;
			try {
				cls = loadOrUploadClass(simulatorClass, memoryClassLoader);
				cls.getMethod("main", String[].class).invoke(null, (Object)null);
				Collection<String> loadedCompiledClasses = memoryClassLoader.getLoadedCompiledClasses().keySet();
				return searchResult.getClasses(
					JavaClass.Criteria.create().allThat(javaClass -> 
						loadedCompiledClasses.contains(javaClass.getName()) && !javaClass.getName().equals(simulatorClass.getName())
					)
				);
			} catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException exc) {
				throw Throwables.toRuntimeException(exc);
			}
		}
	}
	
	public Map<String, JavaClass> findDependenciesSlowMode(Class<?> simulatorClass) {
		Map<String, JavaClass> dependencies = new LinkedHashMap<>();
		SearchResult searchResult = getByteCodeHunter().findBy(
			SearchConfig.forPaths(
				pathHelper.getMainClassPaths()
			)
		);
		boolean end = false;
		Collection<Map.Entry<String, ByteBuffer>> dependenciesMapForClassLoader = new LinkedHashSet<>();
		while (!end) {
			try (MemoryClassLoader memoryClassLoader = MemoryClassLoader.create(null, this)) {
				memoryClassLoader.addCompiledClasses(dependenciesMapForClassLoader);
				Class<?> cls = loadOrUploadClass(simulatorClass, memoryClassLoader);
				cls.getMethod("main", String[].class).invoke(null, (Object)null);
				end = true;
			} catch (ClassNotFoundException | NoClassDefFoundError | NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException outerExc) {
				String newNotFoundClassName = outerExc.getMessage().replace("/", ".");
				Map.Entry<String, JavaClass> foundClass = searchResult.getClasses(
					JavaClass.Criteria.create().allThat(javaClass -> 
						javaClass.getName().equals(newNotFoundClassName)
					)
				).entrySet().stream().findFirst().orElseGet(() -> null);
				logDebug("Found dependency: " + Optional.ofNullable(foundClass).map(fndCls -> fndCls.getValue().getName()).orElseGet(() -> null));
				dependencies.put(foundClass.getKey(), foundClass.getValue());
				dependenciesMapForClassLoader.add(
					new AbstractMap.SimpleEntry<>(foundClass.getValue().getName(), foundClass.getValue().getByteCode())
				);
			} catch (InvocationTargetException iTE) {
				Throwable targetException = iTE.getTargetException();
				if (targetException instanceof NoClassDefFoundError ||
					targetException instanceof ClassNotFoundException
				) {
					String newNotFoundClassName = iTE.getTargetException().getMessage().replace("/", ".");
					Map.Entry<String, JavaClass> foundClass = searchResult.getClasses(
						JavaClass.Criteria.create().allThat(javaClass -> 
							javaClass.getName().equals(newNotFoundClassName)
						)
					).entrySet().stream().findFirst().orElseGet(() -> null);
					logDebug("Found dependency: " + Optional.ofNullable(foundClass).map(fndCls -> fndCls.getValue().getName()).orElseGet(() -> null));
					dependencies.put(foundClass.getKey(), foundClass.getValue());
					dependenciesMapForClassLoader.add(
						new AbstractMap.SimpleEntry<>(foundClass.getValue().getName(), foundClass.getValue().getByteCode())
					);
				} else {
					throw Throwables.toRuntimeException(iTE);
				}
			}
		}
		return dependencies;
	}
	
	public FileSystemItem storeDependencies(Class<?> classSimulator, String destinationPath) {
		for (JavaClass javaClass : findDependenciesFastMode(classSimulator).values()) {
			javaClass.storeToClassPath(destinationPath);
		}
		return FileSystemItem.ofPath(destinationPath);
	}
}
