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

import java.lang.invoke.MethodHandle;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.Buffer;
import java.util.Map;
import java.util.Vector;
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
import org.burningwave.core.io.Streams;
import org.burningwave.core.reflection.ConsulterRetriever;

import sun.misc.Unsafe;

@SuppressWarnings("restriction")
public class LowLevelObjectsHandler implements Component {
	ConsulterRetriever consulterRetriever;
	JVMInfo jVMInfo;
	MemberFinder memberFinder;
	Unsafe unsafe;
	Runnable illegalAccessLoggerEnabler;
	Runnable illegalAccessLoggerDisabler;
	
	Field[] emtpyFieldsArray;
	Method[] emptyMethodsArray;
	Constructor<?>[] emptyConstructorsArray;
	
	MethodHandle getDeclaredFieldsRetriever;
	MethodHandle getDeclaredMethodsRetriever;
	MethodHandle getDeclaredConstructorsRetriever;
	ThrowingTriFunction<ClassLoader, Object, String, Package, Throwable> packageRetriever;	
	Method methodInvoker;
	ThrowingBiConsumer<AccessibleObject, Boolean, Throwable> accessibleSetter;
		
	Map<Class<?>, Field> parentClassLoaderFields;
	Class<?> classLoaderDelegateClass;
	Class<?> builtinClassLoaderClass;
	
	Long loadedPackagesMapMemoryOffset;
	Long loadedClassesVectorMemoryOffset;	

	private LowLevelObjectsHandler(ConsulterRetriever consulterRetriever, JVMInfo jVMInfo, MemberFinder memberFinder) {
		this.consulterRetriever = consulterRetriever;
		this.jVMInfo = jVMInfo;
		this.memberFinder = memberFinder;
		LowLevelObjectsHandlerSpecificElementsInitializer.build(this);
	}


	public static LowLevelObjectsHandler getInstance() {
		return LazyHolder.getLowLevelObjectsHandlerInstance();
	}
	
	public static LowLevelObjectsHandler create(ConsulterRetriever consulterRetriever, JVMInfo jvmChecker, MemberFinder memberFinder) {
		return new LowLevelObjectsHandler(consulterRetriever, jvmChecker, memberFinder);
	}
	
	public Unsafe getUnsafe() {
		return unsafe;
	}
	
	public void disableIllegalAccessLogger() {
	    if (illegalAccessLoggerDisabler != null) {
	    	illegalAccessLoggerDisabler.run();
	    }
	}
	
	public void enableIllegalAccessLogger() {
	    if (illegalAccessLoggerEnabler != null) {
	    	illegalAccessLoggerEnabler.run();
	    }
	}
	private void initLoadedClassesVectorMemoryOffset() {
		AtomicReference<Class<?>> definedClass = new AtomicReference<>();
		ClassLoader temporaryClassLoader = new ClassLoader() {
			@Override
			public String toString() {
				definedClass.set(super.defineClass(
					LowLevelObjectsHandler.class.getName(),
					Streams.toByteBuffer(Classes.getInstance().getClassLoader(this.getClass()).getResourceAsStream(LowLevelObjectsHandler.class.getName().replace(".", "/")+ ".class")),	
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
		if (jVMInfo.is32Bit()) {
			logInfo("JVM is 32 bit");
			offset = 8;
			step = 4;
		} else if (!jVMInfo.isCompressedOopsOffOn64BitHotspot()) {
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
					field = memberFinder.findOne(
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
		if (this != LowLevelObjectsHandler.getInstance()) {
			loadedPackagesMapMemoryOffset = null;
			loadedClassesVectorMemoryOffset = null;
			jVMInfo = null;
			memberFinder = null;
			unsafe = null;
			illegalAccessLoggerEnabler = null;
			illegalAccessLoggerDisabler = null;
			emtpyFieldsArray = null;
			emptyMethodsArray = null;
			emptyConstructorsArray = null;
			getDeclaredFieldsRetriever = null;
			getDeclaredMethodsRetriever = null;
			getDeclaredConstructorsRetriever = null;
			packageRetriever = null;	
			methodInvoker = null;
			accessibleSetter = null;	
			parentClassLoaderFields = null;
			classLoaderDelegateClass = null;
			builtinClassLoaderClass = null;
		}
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
	
	private static class LazyHolder {
		private static final LowLevelObjectsHandler LOW_LEVEL_OBJECTS_HANDLER_INSTANCE = LowLevelObjectsHandler.create(
			ConsulterRetriever.getInstance(), JVMInfo.getInstance(), MemberFinder.getInstance()
		);
		
		private static LowLevelObjectsHandler getLowLevelObjectsHandlerInstance() {
			return LOW_LEVEL_OBJECTS_HANDLER_INSTANCE;
		}
	}
}
