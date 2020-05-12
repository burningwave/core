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

import static org.burningwave.core.assembler.StaticComponentContainer.JVMInfo;
import static org.burningwave.core.assembler.StaticComponentContainer.Members;
import static org.burningwave.core.assembler.StaticComponentContainer.Methods;
import static org.burningwave.core.assembler.StaticComponentContainer.Resources;
import static org.burningwave.core.assembler.StaticComponentContainer.Streams;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.Buffer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.function.Function;

import org.burningwave.core.Component;
import org.burningwave.core.classes.FieldCriteria;
import org.burningwave.core.classes.MembersRetriever;
import org.burningwave.core.classes.MethodCriteria;
import org.burningwave.core.function.ThrowingBiConsumer;
import org.burningwave.core.function.ThrowingFunction;
import org.burningwave.core.function.ThrowingSupplier;
import org.burningwave.core.function.ThrowingTriFunction;
import org.burningwave.core.io.ByteBufferOutputStream;

import sun.misc.Unsafe;

@SuppressWarnings("restriction")
public class LowLevelObjectsHandler implements Component, MembersRetriever {

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
	ThrowingFunction<Class<?>, Lookup, Throwable> consulterRetriever;
	
	Map<Class<?>, Field> parentClassLoaderFields;
	Long loadedPackagesMapMemoryOffset;
	Long loadedClassesVectorMemoryOffset;	
	
	Class<?> classLoaderDelegateClass;
	Class<?> builtinClassLoaderClass;

	private LowLevelObjectsHandler() {
		Initializer.build(this);
	}
	
	public static LowLevelObjectsHandler create() {
		return new LowLevelObjectsHandler();
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
	
	public Class<?> defineAnonymousClass(Class<?> outerClass, byte[] byteCode, Object[] var3) {
		return unsafe.defineAnonymousClass(outerClass, byteCode, var3);
	}
	
	public Package retrieveLoadedPackage(ClassLoader classLoader, Object packageToFind, String packageName) throws Throwable {
		return packageRetriever.apply(classLoader, packageToFind, packageName);
	}
		
	@SuppressWarnings("unchecked")
	public Vector<Class<?>> retrieveLoadedClasses(ClassLoader classLoader) {
		return (Vector<Class<?>>)unsafe.getObject(classLoader, loadedClassesVectorMemoryOffset);
	}
	
	@SuppressWarnings("unchecked")
	public Map<String, ?> retrieveLoadedPackages(ClassLoader classLoader) {
		return (Map<String, ?>)unsafe.getObject(classLoader, loadedPackagesMapMemoryOffset);
	}
	
	private Field getParentClassLoaderField(Class<?> classLoaderClass) {
		Field field = parentClassLoaderFields.get(classLoaderClass);
		if (field == null) {
			synchronized (parentClassLoaderFields) {
				field = parentClassLoaderFields.get(classLoaderClass);
				if (field == null) {
					field = Members.findOne(
						FieldCriteria.on(classLoaderClass).name("parent"::equals), classLoaderClass
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
			return ThrowingSupplier.get(() ->(ClassLoader) builtinClassLoaderClassParentField.get(classLoader));
		} else {
			return classLoader.getParent();
		}
	}
	
	public Function<Boolean, ClassLoader> setAsParent(ClassLoader classLoader, ClassLoader futureParent, boolean mantainHierarchy) {
		Class<?> classLoaderBaseClass = builtinClassLoaderClass;
		if (builtinClassLoaderClass != null && builtinClassLoaderClass.isAssignableFrom(classLoader.getClass())) {
			try {
				Collection<Method> methods = Members.findAll(
					MethodCriteria.byScanUpTo(
						cls -> cls.getName().equals(ClassLoader.class.getName())
					).name(
						"loadClass"::equals
					).and().parameterTypesAreAssignableFrom(
						String.class, boolean.class
					), futureParent.getClass()
				);
				Object classLoaderDelegate = unsafe.allocateInstance(classLoaderDelegateClass);
				invoke(classLoaderDelegate,
					Members.findOne(
						MethodCriteria.on(classLoaderDelegateClass).name("init"::equals), classLoaderDelegateClass
					), futureParent,
					Methods.convertToMethodHandle(
						methods.stream().skip(methods.size() - 1).findFirst().get()
					)
				);
				futureParent = (ClassLoader)classLoaderDelegate;
			} catch (Throwable exc) {
				throw Throwables.toRuntimeException(exc);
			}
		} else {
			classLoaderBaseClass = ClassLoader.class;
		}
		Field parentClassLoaderField = getParentClassLoaderField(classLoaderBaseClass);
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
			logWarn("Could not retrieve fields of class {}. Cause: {}", cls.getName(), exc.getMessage());
			return emtpyFieldsArray;
		}		
	}
	
	public Constructor<?>[] getDeclaredConstructors(Class<?> cls) {
		try {
			return (Constructor<?>[])getDeclaredConstructorsRetriever.invoke(cls, false);
		} catch (Throwable exc) {
			logWarn("Could not retrieve constructors of class {}. Cause: {}", cls.getName(), exc.getMessage());
			return emptyConstructorsArray;
		}
	}
	
	public Method[] getDeclaredMethods(Class<?> cls)  {
		try {
			return (Method[])getDeclaredMethodsRetriever.invoke(cls, false);
		} catch (Throwable exc) {
			logWarn("Could not retrieve methods of class {}. Cause: {}", cls.getName(), exc.getMessage());
			return emptyMethodsArray;
		}
	}
	
	public Lookup getConsulter(Class<?> cls) {
		return ThrowingSupplier.get(() ->
			consulterRetriever.apply(cls)
		);
	}
	
	@Override
	public void close() {
		loadedPackagesMapMemoryOffset = null;
		loadedClassesVectorMemoryOffset = null;
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
		consulterRetriever = null;
		parentClassLoaderFields = null;
		classLoaderDelegateClass = null;
		builtinClassLoaderClass = null;
	}

	@SuppressWarnings("unchecked")
	public static class ByteBufferDelegate {
		
		private ByteBufferDelegate() {}
		
		public static ByteBufferDelegate create() {
			return new ByteBufferDelegate();
		}
		
		public <T extends Buffer> int limit(T buffer) {
			return ((Buffer)buffer).limit();
		}
		
		public <T extends Buffer> int position(T buffer) {
			return ((Buffer)buffer).position();
		}
		
		public <T extends Buffer> T limit(T buffer, int newLimit) {
			return (T)((Buffer)buffer).limit(newLimit);
		}
		
		public <T extends Buffer> T position(T buffer, int newPosition) {
			return (T)((Buffer)buffer).position(newPosition);
		}
		
		public <T extends Buffer> T flip(T buffer) {
			return (T)((Buffer)buffer).flip();
		}
		
		public <T extends Buffer> int capacity(T buffer) {
			return ((Buffer)buffer).capacity();
		}
		
		public <T extends Buffer> int remaining(T buffer) {
			return ((Buffer)buffer).remaining();
		}
		
	}
	
	private abstract static class Initializer implements Component {
		LowLevelObjectsHandler lowLevelObjectsHandler;
		
		private Initializer(LowLevelObjectsHandler lowLevelObjectsHandler) {
			this.lowLevelObjectsHandler = lowLevelObjectsHandler;
			try {
				Field theUnsafeField = Unsafe.class.getDeclaredField("theUnsafe");
				theUnsafeField.setAccessible(true);
				this.lowLevelObjectsHandler.unsafe = (Unsafe)theUnsafeField.get(null);
			} catch (Throwable exc) {
				logInfo("Exception while retrieving unsafe");
				throw Throwables.toRuntimeException(exc);
			}
		}	
		
		void init() {
			initEmptyMembersArrays();
			initMembersRetrievers();
			initSpecificElements();			
			initClassesVectorField();
			initPackagesMapField();
		}


		private void initPackagesMapField() {
			this.lowLevelObjectsHandler.loadedClassesVectorMemoryOffset = lowLevelObjectsHandler.unsafe.objectFieldOffset(
				lowLevelObjectsHandler.getDeclaredField(
					ClassLoader.class, (field) ->
					"classes".equals(field.getName())
				)
			);
		}

		private void initClassesVectorField() {
			this.lowLevelObjectsHandler.loadedPackagesMapMemoryOffset = lowLevelObjectsHandler.unsafe.objectFieldOffset(
				lowLevelObjectsHandler.getDeclaredField(
					ClassLoader.class, (field) ->
					"packages".equals(field.getName())
				)
			);
		}

		private void initEmptyMembersArrays() {
			lowLevelObjectsHandler.emtpyFieldsArray = new Field[]{};
			lowLevelObjectsHandler.emptyMethodsArray = new Method[]{};
			lowLevelObjectsHandler.emptyConstructorsArray = new Constructor<?>[]{};
		}
		
		private static void build(LowLevelObjectsHandler lowLevelObjectsHandler) {
			try (Initializer initializer =
					JVMInfo.getVersion() > 8 ?
					new ForJava9(lowLevelObjectsHandler):
					new ForJava8(lowLevelObjectsHandler)) {
				initializer.init();
			}
		}
		
		private void initMembersRetrievers() {
			try {
				Lookup consulter = lowLevelObjectsHandler.consulterRetriever.apply(Class.class);
				lowLevelObjectsHandler.getDeclaredFieldsRetriever = consulter.findSpecial(
					Class.class,
					"getDeclaredFields0",
					MethodType.methodType(Field[].class, boolean.class),
					Class.class
				);
				
				lowLevelObjectsHandler.getDeclaredMethodsRetriever = consulter.findSpecial(
					Class.class,
					"getDeclaredMethods0",
					MethodType.methodType(Method[].class, boolean.class),
					Class.class
				);

				lowLevelObjectsHandler.getDeclaredConstructorsRetriever = consulter.findSpecial(
					Class.class,
					"getDeclaredConstructors0",
					MethodType.methodType(Constructor[].class, boolean.class),
					Class.class
				);
				lowLevelObjectsHandler.parentClassLoaderFields = new HashMap<>();
			} catch (Throwable exc) {
				throw Throwables.toRuntimeException(exc);
			}
		}
		
		abstract void initSpecificElements();
		
		@Override
		public void close() {
			this.lowLevelObjectsHandler = null;
		}
		
		private static class ForJava8 extends Initializer {

			private ForJava8(LowLevelObjectsHandler lowLevelObjectsHandler) {
				super(lowLevelObjectsHandler);
				Field modes;
				try {
					modes = Lookup.class.getDeclaredField("allowedModes");
				} catch (NoSuchFieldException | SecurityException exc) {
					throw Throwables.toRuntimeException(exc);
				}
				modes.setAccessible(true);
				lowLevelObjectsHandler.consulterRetriever = (cls) -> {
					Lookup consulter = MethodHandles.lookup().in(cls);
					modes.setInt(consulter, -1);
					return consulter;
				};
			}

			@Override
			void initSpecificElements() {
				lowLevelObjectsHandler.packageRetriever = (classLoader, object, packageName) -> (Package)object;
				try {
					final Method accessibleSetterMethod = AccessibleObject.class.getDeclaredMethod("setAccessible0", AccessibleObject.class, boolean.class);
					accessibleSetterMethod.setAccessible(true);
					lowLevelObjectsHandler.accessibleSetter = (accessibleObject, flag) ->
						accessibleSetterMethod.invoke(null, accessibleObject, flag);
				} catch (Throwable exc) {
					logInfo("method setAccessible0 class not detected on " + AccessibleObject.class.getName());
					throw Throwables.toRuntimeException(exc);
				}
				try {
					lowLevelObjectsHandler.methodInvoker = Class.forName("sun.reflect.NativeMethodAccessorImpl").getDeclaredMethod("invoke0", Method.class, Object.class, Object[].class);
					lowLevelObjectsHandler.setAccessible(lowLevelObjectsHandler.methodInvoker, true);
				} catch (Throwable exc2) {
					logError("method invoke0 of class jdk.internal.reflect.NativeMethodAccessorImpl not detected");
					throw Throwables.toRuntimeException(exc2);
				}		
			}
		}
		
		private static class ForJava9 extends Initializer {
			
			private ForJava9(LowLevelObjectsHandler lowLevelObjectsHandler) {
				super(lowLevelObjectsHandler);
				try {
			        Class<?> cls = Class.forName("jdk.internal.module.IllegalAccessLogger");
			        Field logger = cls.getDeclaredField("logger");
			        final long loggerFieldOffset = lowLevelObjectsHandler.unsafe.staticFieldOffset(logger);
			        final Object illegalAccessLogger = lowLevelObjectsHandler.unsafe.getObjectVolatile(cls, loggerFieldOffset);
			        lowLevelObjectsHandler.illegalAccessLoggerDisabler = () ->
			        	lowLevelObjectsHandler.unsafe.putObjectVolatile(cls, loggerFieldOffset, null);
			        lowLevelObjectsHandler.illegalAccessLoggerEnabler = () ->
			        	lowLevelObjectsHandler.unsafe.putObjectVolatile(cls, loggerFieldOffset, illegalAccessLogger);
			    } catch (Throwable e) {
			    	
			    }
				lowLevelObjectsHandler.disableIllegalAccessLogger();
				try {
					MethodHandles.Lookup consulter = MethodHandles.lookup();
					MethodHandle consulterRetrieverMethod = consulter.findStatic(
						MethodHandles.class, "privateLookupIn",
						MethodType.methodType(Lookup.class, Class.class, Lookup.class)
					);
					lowLevelObjectsHandler.consulterRetriever = cls ->
						(Lookup)consulterRetrieverMethod.invoke(cls, MethodHandles.lookup());
				} catch (IllegalArgumentException | NoSuchMethodException
						| SecurityException | IllegalAccessException exc) {
					logError("Could not initialize consulter", exc);
					throw Throwables.toRuntimeException(exc);
				}
			}

			
			@Override
			void initSpecificElements() {
				try {
					final Method accessibleSetterMethod = AccessibleObject.class.getDeclaredMethod("setAccessible0", boolean.class);
					accessibleSetterMethod.setAccessible(true);
					lowLevelObjectsHandler.accessibleSetter = (accessibleObject, flag) ->
						accessibleSetterMethod.invoke(accessibleObject, flag);
				} catch (Throwable exc) {
					logInfo("method setAccessible0 class not detected on " + AccessibleObject.class.getName());
					throw Throwables.toRuntimeException(exc);
				}
				try {
					Lookup classLoaderConsulter = lowLevelObjectsHandler.consulterRetriever.apply(ClassLoader.class);
					MethodType methodType = MethodType.methodType(Package.class, String.class);
					MethodHandle methodHandle = classLoaderConsulter.findSpecial(ClassLoader.class, "getDefinedPackage", methodType, ClassLoader.class);
					lowLevelObjectsHandler.packageRetriever = (classLoader, object, packageName) ->
						(Package)methodHandle.invokeExact(classLoader, packageName);
				} catch (Throwable exc) {
					throw Throwables.toRuntimeException(exc);
				}
				try {
					lowLevelObjectsHandler.builtinClassLoaderClass = Class.forName("jdk.internal.loader.BuiltinClassLoader");
					try {
						lowLevelObjectsHandler.methodInvoker = Class.forName(
							"jdk.internal.reflect.NativeMethodAccessorImpl"
						).getDeclaredMethod(
							"invoke0", Method.class, Object.class, Object[].class
						);
						lowLevelObjectsHandler.setAccessible(lowLevelObjectsHandler.methodInvoker, true);
					} catch (Throwable exc) {
						logInfo("method invoke0 of class jdk.internal.reflect.NativeMethodAccessorImpl not detected");
						throw Throwables.toRuntimeException(exc);
					}
					try (
						InputStream inputStream =
							Resources.getAsInputStream(this.getClass().getClassLoader(), "org/burningwave/core/classes/ClassLoaderDelegate.bwc"
						);
						ByteBufferOutputStream bBOS = new ByteBufferOutputStream()
					) {
						Streams.copy(inputStream, bBOS);
						lowLevelObjectsHandler.classLoaderDelegateClass = lowLevelObjectsHandler.unsafe.defineAnonymousClass(
							lowLevelObjectsHandler.builtinClassLoaderClass, bBOS.toByteArray(), null
						);
					} catch (Throwable exc) {
						throw Throwables.toRuntimeException(exc);
					}
				} catch (Throwable exc) {
					logInfo("jdk.internal.loader.BuiltinClassLoader class not detected");
					throw Throwables.toRuntimeException(exc);
				}
			}
			
			@Override
			public void close() {
				super.close();
			}
		}

	}
}
