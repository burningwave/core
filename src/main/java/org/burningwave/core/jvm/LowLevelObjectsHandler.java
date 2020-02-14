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

import java.io.InputStream;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;

import org.burningwave.ManagedLogger;
import org.burningwave.Throwables;
import org.burningwave.core.Component;
import org.burningwave.core.Strings;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.ClassFactory;
import org.burningwave.core.classes.ClassHelper;
import org.burningwave.core.classes.FieldCriteria;
import org.burningwave.core.classes.MemberFinder;
import org.burningwave.core.classes.MemoryClassLoader;
import org.burningwave.core.classes.MethodCriteria;
import org.burningwave.core.function.TriFunction;
import org.burningwave.core.io.ByteBufferInputStream;
import org.burningwave.core.io.ByteBufferOutputStream;
import org.burningwave.core.io.StreamHelper;
import org.burningwave.core.io.Streams;
import org.burningwave.core.iterable.IterableObjectHelper;
import org.burningwave.core.iterable.Properties;

import sun.misc.Unsafe;

@SuppressWarnings("restriction")
public class LowLevelObjectsHandler implements Component {
	public final static String SUPPLIER_IMPORTS_KEY_SUFFIX = ".supplier.imports";
	
	public static final Method GET_DECLARED_FIELDS_RETRIEVER;
	public static final Method GET_DECLARED_METHODS_RETRIEVER;
	public static final Method GET_DECLARED_CONSTRUCTORS_RETRIEVER;
	private static Method METHOD_INVOKER;
	private static Method ACCESSIBLE_SETTER;
	private static final Map<Class<?>, Field> PARENT_CLASS_LOADER_FIELDS;
	private static Class<?> CLASS_LOADER_DELEGATE_CLASS;
	private static Class<?> BUILTIN_CLASS_LOADER_CLASS;
	
	protected Long LOADED_PACKAGES_MAP_MEMORY_OFFSET;
	protected Long LOADED_CLASSES_VECTOR_MEMORY_OFFSET;
	
	protected JVMChecker jVMChecker;
	protected IterableObjectHelper iterableObjectHelper;
	protected ClassFactory classFactory;
	protected Supplier<ClassFactory> classFactorySupplier;
	protected Supplier<ClassHelper> classHelperSupplier;
	protected StreamHelper streamHelper;
	protected ClassHelper classHelper;
	protected MemberFinder memberFinder;
	protected TriFunction<ClassLoader, Object, String, Package> packageRetriever;
	protected static Unsafe unsafe;
	
	protected Map<ClassLoader, Vector<Class<?>>> classLoadersClasses;
	protected Map<ClassLoader, Map<String, ?>> classLoadersPackages;
	protected Map<String, Method> classLoadersMethods;
	protected Map<String, ClassLoaderDelegate> classLoaderDelegates;
	
	static {
		try {
			Field theUnsafeField = Unsafe.class.getDeclaredField("theUnsafe");
			theUnsafeField.setAccessible(true);
			unsafe = (Unsafe)theUnsafeField.get(null);
			GET_DECLARED_FIELDS_RETRIEVER = Class.class.getDeclaredMethod("getDeclaredFields0", boolean.class);
			GET_DECLARED_FIELDS_RETRIEVER.setAccessible(true);
			GET_DECLARED_METHODS_RETRIEVER = Class.class.getDeclaredMethod("getDeclaredMethods0", boolean.class);
			GET_DECLARED_METHODS_RETRIEVER.setAccessible(true);
			GET_DECLARED_CONSTRUCTORS_RETRIEVER = Class.class.getDeclaredMethod("getDeclaredConstructors0", boolean.class);
			GET_DECLARED_CONSTRUCTORS_RETRIEVER.setAccessible(true);
			PARENT_CLASS_LOADER_FIELDS = new ConcurrentHashMap<>();
		} catch (Throwable exc) {
			throw Throwables.toRuntimeException(exc);
		}
		try {
			BUILTIN_CLASS_LOADER_CLASS = Class.forName("jdk.internal.loader.BuiltinClassLoader");
			try {
				ACCESSIBLE_SETTER = AccessibleObject.class.getDeclaredMethod("setAccessible0", boolean.class);
				ACCESSIBLE_SETTER.setAccessible(true);
			} catch (Throwable exc) {
				ManagedLogger.Repository.getInstance().logInfo(LowLevelObjectsHandler.class, "method setAccessible0 class not detected on " + AccessibleObject.class.getName());
				throw Throwables.toRuntimeException(exc);
			}
			try {
				METHOD_INVOKER = Class.forName("jdk.internal.reflect.NativeMethodAccessorImpl").getDeclaredMethod("invoke0", Method.class, Object.class, Object[].class);
				setAccessible(METHOD_INVOKER, true);
			} catch (Throwable exc) {
				ManagedLogger.Repository.getInstance().logInfo(LowLevelObjectsHandler.class, "method invoke0 of class jdk.internal.reflect.NativeMethodAccessorImpl not detected");
				throw Throwables.toRuntimeException(exc);
			}
			try (
				InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("jdk/internal/loader/ClassLoaderDelegate.bwclass");
				ByteBufferOutputStream bBOS = new ByteBufferOutputStream()
			) {
				Streams.copy(inputStream, bBOS);
				CLASS_LOADER_DELEGATE_CLASS = unsafe.defineAnonymousClass(BUILTIN_CLASS_LOADER_CLASS, bBOS.toByteArray(), null);
			} catch (Throwable exc) {
				throw Throwables.toRuntimeException(exc);
			}
		} catch (Throwable exc) {
			ManagedLogger.Repository.getInstance().logInfo(LowLevelObjectsHandler.class, "jdk.internal.loader.BuiltinClassLoader class not detected");
			BUILTIN_CLASS_LOADER_CLASS = null;
		}
	}
	
	protected LowLevelObjectsHandler(
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
		return classFactory != null ?
			classFactory :
			(classFactory = classFactorySupplier.get());
	}
	
	protected ClassHelper getClassHelper() {
		return classHelper != null ?
			classHelper :
			(classHelper = classHelperSupplier.get());
	}
	
	public static Unsafe getUnsafe() {
		return unsafe;
	}
	
	private void initLoadedClassesVectorMemoryOffset() {
		AtomicReference<Class<?>> definedClass = new AtomicReference<>();
		ClassLoader temporaryClassLoader = new ClassLoader() {
			@Override
			public String toString() {
				ByteBufferInputStream inputStream = (ByteBufferInputStream)streamHelper.getResourceAsStream(LowLevelObjectsHandler.class.getName().replace(".", "/")+ ".class");
				definedClass.set(super.defineClass(LowLevelObjectsHandler.class.getName(), inputStream.toByteBuffer(), null));
				return "lowlevelobjectshandler.initializator";
			}							
		};
		temporaryClassLoader.toString();
		iterateClassLoaderFields(
			temporaryClassLoader, 
			getLoadedClassesVectorMemoryOffsetInitializator(definedClass.get())
		);
	}

	private void initLoadedPackageMapOffset() {
		AtomicReference<Object> definedPackage = new AtomicReference<>();
		ClassLoader temporaryClassLoader = new ClassLoader() {
			@Override
			public String toString() {
				definedPackage.set(super.definePackage("lowlevelobjectshandler.loadedpackagemapoffset.initializator.packagefortesting", 
					null, null, null, null, null, null, null));
				return "lowlevelobjectshandler.initializator";
			}							
		};
		temporaryClassLoader.toString();
		iterateClassLoaderFields(
			temporaryClassLoader, 
			getLoadedPackageMapMemoryOffsetInitializator(definedPackage.get())
		);
	}
	
	private BiPredicate<Object, Long> getLoadedClassesVectorMemoryOffsetInitializator(Class<?> definedClass) {
		return (object, offset) -> {
			if (object != null && object instanceof Vector) {
				Vector<?> vector = (Vector<?>)object;
				if (vector.contains(definedClass)) {
					LOADED_CLASSES_VECTOR_MEMORY_OFFSET = offset;
					return true;
				}
			}
			return false;
		};
	}
	
	private BiPredicate<Object, Long> getLoadedPackageMapMemoryOffsetInitializator(Object pckg) {
		return (object, offset) -> {
			if (object != null && object instanceof Map) {
				Map<?, ?> map = (Map<?, ?>)object;
				if (map.containsValue(pckg)) {
					LOADED_PACKAGES_MAP_MEMORY_OFFSET = offset;
					return true;
				}
			}
			return false;
		};
	}
	
	protected Object iterateClassLoaderFields(ClassLoader classLoader, BiPredicate<Object, Long> predicate) {
		long offset;
		long step;
		if (jVMChecker.is32Bit()) {
			logInfo("JVM is 32 bit");
			offset = 8;
			step = 4;
		} else if (!jVMChecker.isCompressedOopsOffOn64BitHotspot()) {
			logInfo("JVM is 64 bit Hotspot and Compressed Oops is enabled");
			offset = 12;
			step = 4;
		} else {
			logInfo("JVM is 64 bit but is not Hotspot or Compressed Oops is disabled");
			offset = 16;
			step = 8;
		}
		logInfo("Iterating by unsafe fields of classLoader {}", classLoader.getClass().getName());
		while (true) {
			logInfo("Processing offset {}", offset);
			Object object = unsafe.getObject(classLoader, offset);
			//logDebug(offset + " " + object);
			if (predicate.test(object, offset)) {
				return object;
			}
			offset+=step;
		}
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
						if (LOADED_CLASSES_VECTOR_MEMORY_OFFSET == null) {
							initLoadedClassesVectorMemoryOffset();
						}					
						classes = (Vector<Class<?>>)unsafe.getObject(classLoader, LOADED_CLASSES_VECTOR_MEMORY_OFFSET);
						classLoadersClasses.put(classLoader, classes);
						return classes;
					}
				}
			}
		}
		throw Throwables.toRuntimeException("Could not find classes Vector on " + classLoader);
	}
	
	public Collection<Class<?>> retrieveAllLoadedClasses(ClassLoader classLoader) {
		Collection<Class<?>> allLoadedClasses = new LinkedHashSet<>();
		allLoadedClasses.addAll(retrieveLoadedClasses(classLoader));
		if (classLoader.getParent() != null) {
			allLoadedClasses.addAll(retrieveAllLoadedClasses(classLoader.getParent()));
		}
		return allLoadedClasses;
	}
	
	@SuppressWarnings({ "unchecked" })
	public Map<String, ?> retrieveLoadedPackages(ClassLoader classLoader) {
		Map<String, ?> packages = classLoadersPackages.get(classLoader);
		if (packages == null) {
			synchronized (classLoadersPackages) {
				packages = classLoadersPackages.get(classLoader);
				if (packages == null) {
					if (LOADED_PACKAGES_MAP_MEMORY_OFFSET == null) {
						initLoadedPackageMapOffset();
					}					
					packages = (Map<String, ?>)unsafe.getObject(classLoader, LOADED_PACKAGES_MAP_MEMORY_OFFSET);
					classLoadersPackages.put(classLoader, packages);
				}
			}
		
		}
		if (packages == null) {
			throw Throwables.toRuntimeException("Could not find packages Map on " + classLoader);
		}
		return packages;
		
	}
	
	public Package retrieveLoadedPackage(ClassLoader classLoader, Object packageToFind, String packageName) {
		return packageRetriever.apply(classLoader, packageToFind, packageName);
	}
	
	public <T> T retrieveFromProperties(
		Properties config, 
		String supplierCodeKey,
		Map<String, String> defaultValues,
		ComponentSupplier componentSupplier
	) {	
		String supplierCode = iterableObjectHelper.get(config, supplierCodeKey, defaultValues);
		supplierCode = supplierCode.contains("return")?
			supplierCode:
			"return (T)" + supplierCode + ";";
		String importFromConfig = iterableObjectHelper.get(config, supplierCodeKey + SUPPLIER_IMPORTS_KEY_SUFFIX, defaultValues);
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
		String className = "ObjectSupplier_" + UUID.randomUUID().toString().replaceAll("-", "");
		return getClassHelper().executeCode(
			imports, className, supplierCode, 
			componentSupplier, Thread.currentThread().getContextClassLoader()
		);
	}
	
	public void unregister(ClassLoader classLoader) {
		classLoadersClasses.remove(classLoader);
		classLoadersPackages.remove(classLoader);
	}
	
	public ClassLoaderDelegate getClassLoaderDelegate(String name) {
		ClassLoaderDelegate classLoaderDelegate = classLoaderDelegates.get(name);
		if (classLoaderDelegate == null) {
			synchronized(classLoaderDelegates) {
				classLoaderDelegate = classLoaderDelegates.get(name);
				if (classLoaderDelegate == null) {
					try {
						String sourceCode = this.streamHelper.getResourceAsStringBuffer(
							ClassLoaderDelegate.class.getPackage().getName().replaceAll("\\.", "/") + "/" + name + ".jt"
						).toString().replace("${packageName}", getClass().getPackage().getName());
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
	
	private static Field getParentClassLoaderField(Class<?> classLoaderClass) {
		Field field = PARENT_CLASS_LOADER_FIELDS.get(classLoaderClass);
		if (field == null) {
			synchronized (PARENT_CLASS_LOADER_FIELDS) {
				field = PARENT_CLASS_LOADER_FIELDS.get(classLoaderClass);
				if (field == null) {
					field = MemberFinder.create().findOne(
						FieldCriteria.byScanUpTo(classLoaderClass).name("parent"::equals), classLoaderClass
					);
					setAccessible(field, true);
					PARENT_CLASS_LOADER_FIELDS.put(classLoaderClass, field);
				}
			}
		}
		return field;
	}
	
	public static ClassLoader getParent(ClassLoader classLoader) {
		if (BUILTIN_CLASS_LOADER_CLASS != null && BUILTIN_CLASS_LOADER_CLASS.isAssignableFrom(classLoader.getClass())) {
			Field builtinClassLoaderClassParentField = LowLevelObjectsHandler.getParentClassLoaderField(BUILTIN_CLASS_LOADER_CLASS);
			try {
				return (ClassLoader) builtinClassLoaderClassParentField.get(classLoader);
			} catch (IllegalArgumentException | IllegalAccessException exc) {
				throw Throwables.toRuntimeException(exc);
			}
		} else {
			return classLoader.getParent();
		}
	}
	
	public static Function<Boolean, ClassLoader> setParent(ClassLoader classLoader, ClassLoader futureParent, boolean mantainHierarchy) {
		Class<?> classLoaderBaseClass = BUILTIN_CLASS_LOADER_CLASS;
		MemberFinder memberFinder = MemberFinder.create();
		if (BUILTIN_CLASS_LOADER_CLASS != null && BUILTIN_CLASS_LOADER_CLASS.isAssignableFrom(classLoader.getClass())) {
			try {
				Object classLoaderDelegate = unsafe.allocateInstance(CLASS_LOADER_DELEGATE_CLASS);
				Method initMethod = memberFinder.findOne(
					MethodCriteria.on(CLASS_LOADER_DELEGATE_CLASS).name("init"::equals), CLASS_LOADER_DELEGATE_CLASS
				);
				invoke(classLoaderDelegate, initMethod, futureParent);
				futureParent = (ClassLoader)classLoaderDelegate;
			} catch (Throwable exc) {
				throw Throwables.toRuntimeException(exc);
			}
		} else {
			classLoaderBaseClass = ClassLoader.class;
		}
		Field parentClassLoaderField = memberFinder.findOne(
			FieldCriteria.byScanUpTo(classLoaderBaseClass).name("parent"::equals), classLoaderBaseClass
		);
		Long offset = LowLevelObjectsHandler.getUnsafe().objectFieldOffset(parentClassLoaderField);
		final ClassLoader exParent = (ClassLoader)LowLevelObjectsHandler.getUnsafe().getObject(classLoader, offset);
		unsafe.putObject(classLoader, offset, futureParent);
		if (mantainHierarchy && exParent != null) {
			unsafe.putObject(futureParent, offset, exParent);
		}
		return (reset) -> {
			if (reset) {
				unsafe.putObject(classLoader, offset, exParent);
			}
			return exParent;
		};
	}
	
	public static void setAccessible(AccessibleObject object, boolean flag) {
		if (ACCESSIBLE_SETTER != null) {
			try {
				ACCESSIBLE_SETTER.invoke(object, flag);
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException exc) {
				throw Throwables.toRuntimeException(exc);
			}
		} else {
			object.setAccessible(flag);
		}
	}
	
	public static Object invoke(Object target, Method method, Object... params) {
		try {			
			if (METHOD_INVOKER != null) {
				return METHOD_INVOKER.invoke(null, method, target, params);
			} else {
				return method.invoke(target, params);
			}
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException exc) {
			throw Throwables.toRuntimeException(exc);
		}
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
		this.packageRetriever = null;
		LOADED_PACKAGES_MAP_MEMORY_OFFSET = null;
		LOADED_CLASSES_VECTOR_MEMORY_OFFSET = null;
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
