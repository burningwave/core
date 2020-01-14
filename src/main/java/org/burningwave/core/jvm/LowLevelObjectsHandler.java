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
package org.burningwave.core.jvm;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.burningwave.Throwables;
import org.burningwave.core.Component;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.ClassFactory;
import org.burningwave.core.classes.ClassHelper;
import org.burningwave.core.classes.MemberFinder;
import org.burningwave.core.classes.MemoryClassLoader;
import org.burningwave.core.classes.MethodCriteria;
import org.burningwave.core.common.Streams;
import org.burningwave.core.common.Strings;
import org.burningwave.core.function.TriFunction;
import org.burningwave.core.io.StreamHelper;
import org.burningwave.core.iterable.IterableObjectHelper;
import org.burningwave.core.iterable.Properties;

import sun.misc.Unsafe;

@SuppressWarnings("restriction")
public class LowLevelObjectsHandler implements Component {
	private JVMChecker jVMChecker;
	private IterableObjectHelper iterableObjectHelper;
	private ClassFactory classFactory;
	private Supplier<ClassFactory> classFactorySupplier;
	private Supplier<ClassHelper> classHelperSupplier;
	private StreamHelper streamHelper;
	private ClassHelper classHelper;
	private MemberFinder memberFinder;
	private Map<ClassLoader, Vector<Class<?>>> classLoadersClasses;
	private Map<ClassLoader, Map<String, ?>> classLoadersPackages;
	private Predicate<Object> packageMapTester;
	private TriFunction<ClassLoader, Object, String, Package> packageRetriever;
	private static Unsafe unsafe;
	private Map<String, Method> classLoadersMethods;
	private Map<String, ClassLoaderDelegate> classLoaderDelegates;
	
	static {
		try {
			Field theUnsafeField = Unsafe.class.getDeclaredField("theUnsafe");
			theUnsafeField.setAccessible(true);
			unsafe = (Unsafe)theUnsafeField.get(null);
		} catch (Throwable exc) {
			Throwables.toRuntimeException(exc);
		}
	}
	
	private LowLevelObjectsHandler(
		JVMChecker jVMChecker,
		StreamHelper streamHelper,
		Supplier<ClassFactory> classFactorySupplier,
		Supplier<ClassHelper> classHelperSupplier,
		MemberFinder memberFinder,
		IterableObjectHelper iterableObjectHelper
	) {	
		this.jVMChecker = jVMChecker;
		this.streamHelper = streamHelper;
		this.classFactorySupplier = classFactorySupplier;
		this.classHelperSupplier = classHelperSupplier;
		this.memberFinder = memberFinder;
		this.iterableObjectHelper = iterableObjectHelper;
		this.classLoadersClasses = new ConcurrentHashMap<>();
		this.classLoadersPackages = new ConcurrentHashMap<>();
		this.classLoadersMethods = new ConcurrentHashMap<>();
		this.classLoaderDelegates = new ConcurrentHashMap<>();
		try {
			Class.forName("java.lang.NamedPackage");
			packageMapTester = (object) -> object != null && object instanceof ConcurrentHashMap;
		} catch (ClassNotFoundException e) {
			packageMapTester = (object) -> object != null && object instanceof HashMap;
		}
		if (findGetDefinedPackageMethod() == null) {
			packageRetriever = (classLoader, object, packageName) -> (Package)object;
		} else {
			packageRetriever = (classLoader, object, packageName) -> getClassLoaderDelegate("ForJDKVersionLaterThan8").getPackage(classLoader, packageName);
		}
	}
	
	public static LowLevelObjectsHandler create(
		JVMChecker jVMChecker,
		StreamHelper streamHelper,
		Supplier<ClassFactory> classFactorySupplier,
		Supplier<ClassHelper> classHelperSupplier,
		MemberFinder memberFinder,
		IterableObjectHelper iterableObjectHelper
	) {
		return new LowLevelObjectsHandler(jVMChecker, streamHelper, classFactorySupplier, classHelperSupplier, memberFinder, iterableObjectHelper);
	}
	
	private ClassFactory getClassFactory() {
		if (classFactory == null) {
			classFactory = classFactorySupplier.get();
		}
		return classFactory;
	}
	
	private ClassHelper getClassHelper() {
		return classHelper != null ?
			classHelper :
			(classHelper = classHelperSupplier.get());
	}
	
	public static Unsafe getUnsafe() {
		return unsafe;
	}
	
	public Class<?> defineAnonymousClass(Class<?> outerClass, byte[] byteCode, Object[] var3) {
		return unsafe.defineAnonymousClass(outerClass, byteCode, var3);
	}
	
	public Method getDefinePackageMethod(ClassLoader classLoader) {
		return getMethod(
			classLoader,
			classLoader.getClass().getName() + "_" + "definePackage",
			() -> findDefinePackageMethodAndMakeItAccesible(classLoader)
		);
	}
	
	private Method findDefinePackageMethodAndMakeItAccesible(ClassLoader classLoader) {
		Method method = memberFinder.findAll(
			MethodCriteria.byScanUpTo((cls) -> 
				cls.getName().equals(ClassLoader.class.getName())
			).name(
				"definePackage"::equals
			).and().parameterTypesAreAssignableFrom(
				String.class, String.class, String.class, String.class,
				String.class, String.class, String.class, URL.class
			),
			classLoader
		).stream().findFirst().orElse(null);
		method.setAccessible(true);
		return method;
	}
	
	public Method getDefineClassMethod(ClassLoader classLoader) {
		return getMethod(
			classLoader,
			classLoader.getClass().getName() + "_" + "defineClass",
			() -> findDefineClassMethodAndMakeItAccesible(classLoader)
		);
	}
	
	private Method findDefineClassMethodAndMakeItAccesible(ClassLoader classLoader) {
		Method method = memberFinder.findAll(
			MethodCriteria.byScanUpTo((cls) -> cls.getName().equals(ClassLoader.class.getName())).name(
				(classLoader instanceof MemoryClassLoader? "_defineClass" : "defineClass")::equals
			).and().parameterTypes(params -> 
				params.length == 3
			).and().parameterTypesAreAssignableFrom(
				String.class, ByteBuffer.class, ProtectionDomain.class
			).and().returnType((cls) -> cls.getName().equals(Class.class.getName())),
			classLoader
		).stream().findFirst().orElse(null);
		method.setAccessible(true);
		return method;
	}
	
	private Method getMethod(ClassLoader classLoader, String key, Supplier<Method> methodSupplier) {
		Method method = classLoadersMethods.get(key);
		if (method == null) {
			synchronized (classLoadersMethods) {
				method = classLoadersMethods.get(key);
				if (method == null) {
					classLoadersMethods.put(key, method = methodSupplier.get());
				}
			}
		}
		return method;
	}
	
	public Method findGetDefinedPackageMethod() {
		Method method = memberFinder.findAll(
			MethodCriteria.byScanUpTo((cls) -> cls.getName().equals(ClassLoader.class.getName())).name(
				"getDefinedPackage"::equals
			).and().parameterTypes(params -> 
				params.length == 1
			).and().parameterTypesAreAssignableFrom(
				String.class
			).and().returnType((cls) -> cls.getName().equals(Package.class.getName())),
			ClassLoader.class
		).stream().findFirst().orElse(null);
		return method;
	}
	
	@SuppressWarnings({ "unchecked" })
	public Vector<Class<?>> retrieveLoadedClasses(ClassLoader classLoader) {
		Vector<Class<?>> classes = classLoadersClasses.get(classLoader);
		if (classes != null) {
			return classes;
		} else {
			classes = classLoadersClasses.get(classLoader);
			if (classes == null) {
				synchronized (classLoadersClasses) {
					classes = classLoadersClasses.get(classLoader);
					if (classes == null) {
						classes = (Vector<Class<?>>)iterateClassLoaderFields(classLoader, (object) -> object instanceof Vector);
						classLoadersClasses.put(classLoader, classes);
						return classes;
					}
				}
			}
		}
		throw Throwables.toRuntimeException("Could not find classes Vector on " + classLoader);
	}
	
	@SuppressWarnings({ "unchecked" })
	public Map<String, ?> retrievePackages(ClassLoader classLoader) {
		Map<String, ?> packages = classLoadersPackages.get(classLoader);
		if (packages != null) {
			return packages;
		} else {
			packages = classLoadersPackages.get(classLoader);
			if (packages == null) {
				synchronized (classLoadersPackages) {
					packages = classLoadersPackages.get(classLoader);
					if (packages == null) {
						packages = (Map<String, Package>)iterateClassLoaderFields(
							classLoader, packageMapTester
						);
						classLoadersPackages.put(classLoader, packages);
						return packages;
					}
				}
			}
		}
		throw Throwables.toRuntimeException("Could not find packages Map on " + classLoader);
	}
	

	protected Object iterateClassLoaderFields(ClassLoader classLoader, Predicate<Object> predicate) {
		long offset;
		long step;
		if (jVMChecker.is32Bit()) {
			offset = 8;
			step = 4;
		} else if (!jVMChecker.isCompressedOopsOffOn64Bit()) {
			offset = 12;
			step = 4;
		} else {
			offset = 16;
			step = 8;
		}
		while (true) {
			Object object = unsafe.getObject(classLoader, offset);
			//logDebug(offset + " " + object);
			if (predicate.test(object)) {
				return object;
			}
			offset+=step;
		}
	}
	
	public Package retrievePackage(ClassLoader classLoader, Object packageToFind, String packageName) {
		return packageRetriever.apply(classLoader, packageToFind, packageName);
	}
	
	public <T> T retrieveFromProperties(
		Properties config, 
		String supplierImportsKey, 
		String supplierCodeKey,
		Map<String, String> defaultValues,
		Class<?> returnedClass, 
		ComponentSupplier componentSupplier
	) {	
		String supplierCode = iterableObjectHelper.get(config, supplierCodeKey, defaultValues);
		supplierCode = supplierCode.contains("return")?
			supplierCode:
			"return " + supplierCode + ";";
		String importFromConfig = iterableObjectHelper.get(config, supplierImportsKey, defaultValues);
		if (Strings.isNotEmpty(importFromConfig)) {
			final StringBuffer stringBufferImports = new StringBuffer();
			Arrays.stream(importFromConfig.split(";")).forEach(imp -> {
				stringBufferImports.append("import ").append(imp).append(";\n");
			});
			importFromConfig = stringBufferImports.toString();
		}
		String imports =
			"import " + ComponentSupplier.class.getName() + ";\n" +
			"import " + componentSupplier.getClass().getName() + ";\n" + importFromConfig;
		String className = returnedClass.getSimpleName() + "Supplier";
		return getClassHelper().executeCode(imports, className, supplierCode, returnedClass, componentSupplier);
	}
	
	public void unregister(ClassLoader classLoader) {
		Vector<Class<?>> classes = classLoadersClasses.remove(classLoader);
		if (classes != null) {
			synchronized(classes) {
				classes.clear();
			}
		}
		Map<String, ?> packages = classLoadersPackages.remove(classLoader);
		if (packages != null) {
			synchronized(packages) {
				packages.clear();
			}
		}
	}
	
	public ClassLoaderDelegate getClassLoaderDelegate(String name) {
		ClassLoaderDelegate classLoaderDelegate = classLoaderDelegates.get(name);
		if (classLoaderDelegate == null) {
			synchronized(classLoaderDelegates) {
				classLoaderDelegate = classLoaderDelegates.get(name);
				if (classLoaderDelegate == null) {
					try {
						String sourceCode = this.streamHelper.getResourceAsStringBuffer(
							ClassLoaderDelegate.class.getPackage().getName().replaceAll("\\.", "/") + "/" + name + ".java"
						).toString();
						// In case of inner classes we have more than 1 compiled source
						Map<String, ByteBuffer> compiledSources = getClassFactory().build(sourceCode);
						Map<String, Class<?>> injectedClasses = new LinkedHashMap<>();
						compiledSources.forEach((className, byteCode) -> {
							byte[] byteCodeArray = Streams.toByteArray(compiledSources.get(className));
							injectedClasses.put(className, defineAnonymousClass(ClassLoaderDelegate.class, byteCodeArray, null));
						});
						classLoaderDelegate = (ClassLoaderDelegate)injectedClasses.get(getClassHelper().extractClassName(sourceCode)).getConstructor().newInstance();
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
	
	@Override
	public void close() {
		this.classLoadersClasses.clear();
		this.classLoadersClasses = null;
		this.classLoadersPackages.clear();
		this.classLoadersPackages = null;
		this.classLoadersMethods.clear();
		this.classLoaderDelegates.clear();
		this.classLoaderDelegates = null;
		this.classLoadersMethods = null;
		this.iterableObjectHelper = null;
		this.streamHelper = null;
		this.classFactorySupplier = null;
		this.classFactory = null;
		this.classHelperSupplier = null;
		this.classHelper = null;
		this.memberFinder = null;
		this.packageMapTester = null;
		this.packageRetriever = null;
	}
	
	@SuppressWarnings("unchecked")
	public static class ByteBufferDelegate {
		
		public static <T extends Buffer> int limit(T buffer) {
			return ((Buffer)buffer).limit();
		}
		
		public static <T extends Buffer> int position(T buffer) {
			return ((Buffer)buffer).position();
		}
		
		public static <T extends Buffer> T limit(T buffer, int newLimit) {
			return (T)((Buffer)buffer).limit(newLimit);
		}
		
		public static <T extends Buffer> T position(T buffer, int newPosition) {
			return (T)((Buffer)buffer).position(newPosition);
		}
		
		public static <T extends Buffer> T flip(T buffer) {
			return (T)((Buffer)buffer).flip();
		}
		
		public static <T extends Buffer> int capacity(T buffer) {
			return ((Buffer)buffer).capacity();
		}
		
		public static <T extends Buffer> int remaining(T buffer) {
			return ((Buffer)buffer).remaining();
		}
	}
	
	public static abstract class ClassLoaderDelegate {
		
		public abstract Package getPackage(ClassLoader classLoader, String packageName);
		
	}
}
