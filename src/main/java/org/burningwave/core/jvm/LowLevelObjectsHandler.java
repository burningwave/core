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
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.Buffer;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiPredicate;
import java.util.function.Function;

import org.burningwave.ManagedLogger;
import org.burningwave.Throwables;
import org.burningwave.core.Component;
import org.burningwave.core.classes.Classes;
import org.burningwave.core.classes.FieldCriteria;
import org.burningwave.core.classes.MemberFinder;
import org.burningwave.core.classes.MethodCriteria;
import org.burningwave.core.function.ThrowingBiConsumer;
import org.burningwave.core.function.ThrowingTriFunction;
import org.burningwave.core.io.ByteBufferOutputStream;
import org.burningwave.core.io.Streams;

import sun.misc.Unsafe;

@SuppressWarnings("restriction")
public class LowLevelObjectsHandler implements Component {
	private static LowLevelObjectsHandler INSTANCE;
	
	private Field[] emtpyFieldsArray;
	private Method[] emptyMethodsArray;
	private Constructor<?>[] emptyConstructorsArray;
	
	private Method getDeclaredFieldsRetriever;
	private Method getDeclaredMethodsRetriever;
	private Method getDeclaredConstructorsRetriever;
	private Method methodInvoker;
	private ThrowingBiConsumer<AccessibleObject, Boolean> accessibleSetter;
	
	private Map<Class<?>, Field> parentClassLoaderFields;
	private Class<?> classLoaderDelegateClass;
	private Class<?> builtinClassLoaderClass;

	private Unsafe unsafe;	
	private Long loadedPackagesMapMemoryOffset;
	private Long loadedClassesVectorMemoryOffset;	
	private JVMChecker jvmChecker;
	private ThrowingTriFunction<ClassLoader, Object, String, Package> packageRetriever;	

	public LowLevelObjectsHandler(JVMChecker jvmChecker) {
		Field theUnsafeField;
		try {
			theUnsafeField = Unsafe.class.getDeclaredField("theUnsafe");
			theUnsafeField.setAccessible(true);
			unsafe = (Unsafe)theUnsafeField.get(null);
		} catch (Throwable exc) {
			ManagedLogger.Repository.getInstance().logInfo(LowLevelObjectsHandler.class, "Exception while retrieving unsafe");
			throw Throwables.toRuntimeException(exc);
		}
		this.jvmChecker = jvmChecker;
		try {
			builtinClassLoaderClass = Class.forName("jdk.internal.loader.BuiltinClassLoader");
			init1();
		} catch (ClassNotFoundException exc) {
			ManagedLogger.Repository.getInstance().logInfo(LowLevelObjectsHandler.class, "jdk.internal.loader.BuiltinClassLoader class not detected");
			init0();
		}
	}

	protected void initEmptyMembersArrays() {
		emtpyFieldsArray = new Field[]{};
		emptyMethodsArray = new Method[]{};
		emptyConstructorsArray = new Constructor<?>[]{};
	}
	
	//For Java 8
	private void init0() {
		initEmptyMembersArrays();
		try {
			final Method accessibleSetterMethod = AccessibleObject.class.getDeclaredMethod("setAccessible0", AccessibleObject.class, boolean.class);
			accessibleSetterMethod.setAccessible(true);
			accessibleSetter = (accessibleObject, flag) ->
				accessibleSetterMethod.invoke(null, accessibleObject, flag);
		} catch (Throwable exc) {
			ManagedLogger.Repository.getInstance().logInfo(LowLevelObjectsHandler.class, "method setAccessible0 class not detected on " + AccessibleObject.class.getName());
			throw Throwables.toRuntimeException(exc);
		}
		initMembersRetrievers();
		packageRetriever = (classLoader, object, packageName) -> (Package)object;
		try {
			methodInvoker = Class.forName("sun.reflect.NativeMethodAccessorImpl").getDeclaredMethod("invoke0", Method.class, Object.class, Object[].class);
			setAccessible(methodInvoker, true);
		} catch (Throwable exc2) {
			ManagedLogger.Repository.getInstance().logInfo(LowLevelObjectsHandler.class, "method invoke0 of class jdk.internal.reflect.NativeMethodAccessorImpl not detected");
			throw Throwables.toRuntimeException(exc2);
		}
	}

	protected void initMembersRetrievers() {
		try {
			getDeclaredFieldsRetriever = Class.class.getDeclaredMethod("getDeclaredFields0", boolean.class);
			setAccessible(getDeclaredFieldsRetriever, true);
			getDeclaredMethodsRetriever = Class.class.getDeclaredMethod("getDeclaredMethods0", boolean.class);
			setAccessible(getDeclaredMethodsRetriever, true);
			getDeclaredConstructorsRetriever = Class.class.getDeclaredMethod("getDeclaredConstructors0", boolean.class);
			setAccessible(getDeclaredConstructorsRetriever, true);
			parentClassLoaderFields = new ConcurrentHashMap<>();
		} catch (Throwable exc) {
			throw Throwables.toRuntimeException(exc);
		}
	}
	
	//For Java 9 and later
	private void init1() {
		initEmptyMembersArrays();
		try {
			final Method accessibleSetterMethod = AccessibleObject.class.getDeclaredMethod("setAccessible0", boolean.class);
			accessibleSetterMethod.setAccessible(true);
			accessibleSetter = (accessibleObject, flag) ->
				accessibleSetterMethod.invoke(accessibleObject, flag);
		} catch (Throwable exc) {
			ManagedLogger.Repository.getInstance().logInfo(LowLevelObjectsHandler.class, "method setAccessible0 class not detected on " + AccessibleObject.class.getName());
			throw Throwables.toRuntimeException(exc);
		}
		initMembersRetrievers();
		try {
			final Method getDefinePackageMethod = ClassLoader.class.getDeclaredMethod("getDefinedPackage", String.class);
			packageRetriever = (classLoader, object, packageName) -> 
				(Package)getDefinePackageMethod.invoke(classLoader, packageName);
		} catch (NoSuchMethodException | SecurityException exc) {
			throw Throwables.toRuntimeException(exc);
		}
		try {
			builtinClassLoaderClass = Class.forName("jdk.internal.loader.BuiltinClassLoader");
			try {
				methodInvoker = Class.forName("jdk.internal.reflect.NativeMethodAccessorImpl").getDeclaredMethod("invoke0", Method.class, Object.class, Object[].class);
				setAccessible(methodInvoker, true);
			} catch (Throwable exc) {
				ManagedLogger.Repository.getInstance().logInfo(LowLevelObjectsHandler.class, "method invoke0 of class jdk.internal.reflect.NativeMethodAccessorImpl not detected");
				throw Throwables.toRuntimeException(exc);
			}
			try (
				InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("jdk/internal/loader/ClassLoaderDelegate.bwclass");
				ByteBufferOutputStream bBOS = new ByteBufferOutputStream()
			) {
				Streams.copy(inputStream, bBOS);
				classLoaderDelegateClass = unsafe.defineAnonymousClass(builtinClassLoaderClass, bBOS.toByteArray(), null);
			} catch (Throwable exc) {
				throw Throwables.toRuntimeException(exc);
			}
		} catch (Throwable exc) {
			ManagedLogger.Repository.getInstance().logInfo(LowLevelObjectsHandler.class, "jdk.internal.loader.BuiltinClassLoader class not detected");
			throw Throwables.toRuntimeException(exc);
		}
	}
	
	public static LowLevelObjectsHandler getInstance() {
		if (INSTANCE == null) {
			synchronized(LowLevelObjectsHandler.class) {
				if (INSTANCE == null) {
					INSTANCE = new LowLevelObjectsHandler(JVMChecker.create());
				}
			}
		}
		return INSTANCE;
	}
	
	public Unsafe getUnsafe() {
		return unsafe;
	}
	
	private void initLoadedClassesVectorMemoryOffset() {
		AtomicReference<Class<?>> definedClass = new AtomicReference<>();
		ClassLoader temporaryClassLoader = new ClassLoader() {
			@Override
			public String toString() {
				definedClass.set(super.defineClass(
					LowLevelObjectsHandler.class.getName(),
					Streams.toByteBuffer(ClassLoader.getSystemClassLoader().getResourceAsStream(LowLevelObjectsHandler.class.getName().replace(".", "/")+ ".class")),	
					null)
				);
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
					loadedClassesVectorMemoryOffset = offset;
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
					loadedPackagesMapMemoryOffset = offset;
					return true;
				}
			}
			return false;
		};
	}
	
	protected Object iterateClassLoaderFields(ClassLoader classLoader, BiPredicate<Object, Long> predicate) {
		long offset;
		long step;
		if (jvmChecker.is32Bit()) {
			logInfo("JVM is 32 bit");
			offset = 8;
			step = 4;
		} else if (!jvmChecker.isCompressedOopsOffOn64BitHotspot()) {
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
	
	public Package retrieveLoadedPackage(ClassLoader classLoader, Object packageToFind, String packageName) throws Throwable {
		return packageRetriever.apply(classLoader, packageToFind, packageName);
	}
	

	
	@SuppressWarnings("unchecked")
	public Vector<Class<?>> retrieveLoadedClasses(ClassLoader classLoader) {
		if (loadedClassesVectorMemoryOffset == null) {
			synchronized(this.getClass().getName() + "_" + this.hashCode() + "loadedClassesVectorMemoryOffset") {
				if (loadedClassesVectorMemoryOffset == null) {
					initLoadedClassesVectorMemoryOffset();
				}
			}
		}
		return (Vector<Class<?>>)unsafe.getObject(classLoader, loadedClassesVectorMemoryOffset);
	}
	
	@SuppressWarnings("unchecked")
	public Map<String, ?> retrieveLoadedPackages(ClassLoader classLoader) {
		if (loadedPackagesMapMemoryOffset == null) {
			synchronized(this.getClass().getName() + "_" + this.hashCode() + "loadedPackagesMapMemoryOffset") {
				if (loadedPackagesMapMemoryOffset == null) {
					initLoadedPackageMapOffset();
				}					

			}
		}
		return (Map<String, ?>)unsafe.getObject(classLoader, loadedPackagesMapMemoryOffset);
	}
	
	
	
	private Field getParentClassLoaderField(Class<?> classLoaderClass) {
		Field field = parentClassLoaderFields.get(classLoaderClass);
		if (field == null) {
			synchronized (parentClassLoaderFields) {
				field = parentClassLoaderFields.get(classLoaderClass);
				if (field == null) {
					field = MemberFinder.create().findOne(
						FieldCriteria.byScanUpTo(classLoaderClass).name("parent"::equals), classLoaderClass
					);
					setAccessible(field, true);
					parentClassLoaderFields.put(classLoaderClass, field);
				}
			}
		}
		return field;
	}
	
	public ClassLoader getParent(ClassLoader classLoader) {
		if (builtinClassLoaderClass != null && builtinClassLoaderClass.isAssignableFrom(classLoader.getClass())) {
			Field builtinClassLoaderClassParentField = getParentClassLoaderField(builtinClassLoaderClass);
			try {
				return (ClassLoader) builtinClassLoaderClassParentField.get(classLoader);
			} catch (IllegalArgumentException | IllegalAccessException exc) {
				throw Throwables.toRuntimeException(exc);
			}
		} else {
			return classLoader.getParent();
		}
	}
	
	public Function<Boolean, ClassLoader> setAsParent(ClassLoader classLoader, ClassLoader futureParent, boolean mantainHierarchy) {
		Class<?> classLoaderBaseClass = builtinClassLoaderClass;
		MemberFinder memberFinder = MemberFinder.create();
		if (builtinClassLoaderClass != null && builtinClassLoaderClass.isAssignableFrom(classLoader.getClass())) {
			try {
				Object classLoaderDelegate = unsafe.allocateInstance(classLoaderDelegateClass);
				Method initMethod = memberFinder.findOne(
					MethodCriteria.on(classLoaderDelegateClass).name("init"::equals), classLoaderDelegateClass
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
		Long offset = unsafe.objectFieldOffset(parentClassLoaderField);
		final ClassLoader exParent = (ClassLoader)unsafe.getObject(classLoader, offset);
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
	
	public void setAccessible(AccessibleObject object, boolean flag) {
		try {
			accessibleSetter.accept(object, flag);
		} catch (Throwable exc) {
			throw Throwables.toRuntimeException(exc);
		}
	}
	
	public Object invoke(Object target, Method method, Object... params) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		return methodInvoker.invoke(null, method, target, params);
	}
	
	public Field[] getDeclaredFields(Class<?> cls)  {
		try {
			return (Field[])getDeclaredFieldsRetriever.invoke(cls, false);
		} catch (Throwable exc) {
			ManagedLogger.Repository.getInstance().logWarn(Classes.class, "Could not retrieve fields of class {}. Cause: {}", cls.getName(), exc.getMessage());
			return emtpyFieldsArray;
		}		
	}
	
	public Method[] getDeclaredMethods(Class<?> cls)  {
		try {
			return (Method[]) getDeclaredMethodsRetriever.invoke(cls, false);
		} catch (Throwable exc) {
			ManagedLogger.Repository.getInstance().logWarn(Classes.class, "Could not retrieve methods of class {}. Cause: {}", cls.getName(), exc.getMessage());
			return emptyMethodsArray;
		}
	}
	
	public Constructor<?>[] getDeclaredConstructors(Class<?> cls)  {
		try {
			return (Constructor<?>[])getDeclaredConstructorsRetriever.invoke(cls, false);
		} catch (Throwable exc) {
			ManagedLogger.Repository.getInstance().logWarn(Classes.class, "Could not retrieve constructors of class {}. Cause: {}", cls.getName(), exc.getMessage());
			return emptyConstructorsArray;
		}
	}
	
	@Override
	public void close() {
		loadedPackagesMapMemoryOffset = null;
		loadedClassesVectorMemoryOffset = null;
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
}
