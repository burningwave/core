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

import static org.burningwave.core.assembler.StaticComponentContainer.Classes;
import static org.burningwave.core.assembler.StaticComponentContainer.JVMInfo;
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggersRepository;
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
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.burningwave.core.Closeable;
import org.burningwave.core.Component;
import org.burningwave.core.function.TriFunction;
import org.burningwave.core.io.ByteBufferOutputStream;

import sun.misc.Unsafe;

/**
 * @since 8.12.4
 */
@SuppressWarnings("all")
public class Driver implements Closeable {
	
	Unsafe unsafe;
	Runnable illegalAccessLoggerEnabler;
	Runnable illegalAccessLoggerDisabler;
	
	MethodHandle getDeclaredFieldsRetriever;
	MethodHandle getDeclaredMethodsRetriever;
	MethodHandle getDeclaredConstructorsRetriever;
	MethodHandle methodInvoker;
	MethodHandle constructorInvoker;
	BiConsumer<AccessibleObject, Boolean> accessibleSetter;
	Function<Class<?>, MethodHandles.Lookup> consulterRetriever;
	TriFunction<ClassLoader, Object, String, Package> packageRetriever;
	
	Long loadedPackagesMapMemoryOffset;
	Long loadedClassesVectorMemoryOffset;	
	
	Class<?> classLoaderDelegateClass;
	Class<?> builtinClassLoaderClass;
	
	private Driver() {
		Initializer.build(this);
	}
	
	public static Driver create() {
		return new Driver();
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
	
	public void setAccessible(AccessibleObject object, boolean flag) {
		try {
			accessibleSetter.accept(object, flag);
		} catch (Throwable exc) {
			Throwables.throwException(exc);
		}
	}
	
	public Class<?> defineAnonymousClass(Class<?> outerClass, byte[] byteCode, Object[] var3) {
		return unsafe.defineAnonymousClass(outerClass, byteCode, var3);
	}
	
	public Package retrieveLoadedPackage(ClassLoader classLoader, Object packageToFind, String packageName) throws Throwable {
		return packageRetriever.apply(classLoader, packageToFind, packageName);
	}
	
	public Collection<Class<?>> retrieveLoadedClasses(ClassLoader classLoader) {
		return (Collection<Class<?>>)unsafe.getObject(classLoader, loadedClassesVectorMemoryOffset);
	}
	
	public Map<String, ?> retrieveLoadedPackages(ClassLoader classLoader) {
		return (Map<String, ?>)unsafe.getObject(classLoader, loadedPackagesMapMemoryOffset);
	}
	
	public boolean isBuiltinClassLoader(ClassLoader classLoader) {
		return builtinClassLoaderClass != null && builtinClassLoaderClass.isAssignableFrom(classLoader.getClass());
	}
	
	public boolean isClassLoaderDelegate(ClassLoader classLoader) {
		return classLoaderDelegateClass != null && classLoaderDelegateClass.isAssignableFrom(classLoader.getClass());
	}
	
	public Class<?> getBuiltinClassLoaderClass() {
		return builtinClassLoaderClass;
	}
	
	public Class getClassLoaderDelegateClass() {
		return classLoaderDelegateClass;
	}
	
	public Lookup getConsulter(Class<?> cls) {
		return consulterRetriever.apply(cls);
	}
	
	public Object invoke(Method method, Object target, Object[] params) {
		try {
			return methodInvoker.invoke(method, target, params);
		} catch (Throwable exc) {
			return Throwables.throwException(exc);
		}			
	}
	
	public <T> T newInstance(Constructor<T> ctor, Object[] params) {
		try {
			return (T)constructorInvoker.invoke(ctor, params);
		} catch (Throwable exc) {
			return Throwables.throwException(exc);
		}			
	}
	
	public Field getDeclaredField(Class<?> cls, String name) {
		for (Field field : getDeclaredFields(cls)) {
			if (field.getName().equals(name)) {
				return field;
			}
		}
		return null;
	}
	
	public Field[] getDeclaredFields(Class<?> cls)  {
		try {
			return (Field[])getDeclaredFieldsRetriever.invoke(cls, false);
		} catch (Throwable exc) {
			return Throwables.throwException(exc);
		}		
	}
	
	public <T> Constructor<T>[] getDeclaredConstructors(Class<T> cls) {
		try {
			return (Constructor<T>[])getDeclaredConstructorsRetriever.invoke(cls, false);
		} catch (Throwable exc) {
			return Throwables.throwException(exc);
		}
	}
	
	public Method[] getDeclaredMethods(Class<?> cls) {
		try {
			return (Method[])getDeclaredMethodsRetriever.invoke(cls, false);
		} catch (Throwable exc) {
			return Throwables.throwException(exc);
		}
	}
	
	public <T> T getFieldValue(Object target, Field field) {
		target = Modifier.isStatic(field.getModifiers())?
			field.getDeclaringClass() :
			target;
		long fieldOffset = Modifier.isStatic(field.getModifiers())?
			unsafe.staticFieldOffset(field) :
			unsafe.objectFieldOffset(field);
		Class<?> cls = field.getType();
		if(!cls.isPrimitive()) {
			if (!Modifier.isVolatile(field.getModifiers())) {
				return (T)unsafe.getObject(target, fieldOffset);
			} else {
				return (T)unsafe.getObjectVolatile(target, fieldOffset);
			}
		} else if (cls == int.class) {
			if (!Modifier.isVolatile(field.getModifiers())) {
				return (T)Integer.valueOf(unsafe.getInt(target, fieldOffset));
			} else {
				return (T)Integer.valueOf(unsafe.getIntVolatile(target, fieldOffset));
			}
		} else if (cls == long.class) {
			if (!Modifier.isVolatile(field.getModifiers())) {
				return (T)Long.valueOf(unsafe.getLong(target, fieldOffset));
			} else {
				return (T)Long.valueOf(unsafe.getLongVolatile(target, fieldOffset));
			}
		} else if (cls == float.class) {
			if (!Modifier.isVolatile(field.getModifiers())) {
				return (T)Float.valueOf(unsafe.getFloat(target, fieldOffset));
			} else {
				return (T)Float.valueOf(unsafe.getFloatVolatile(target, fieldOffset));
			}
		} else if (cls == double.class) {
			if (!Modifier.isVolatile(field.getModifiers())) {
				return (T)Double.valueOf(unsafe.getDouble(target, fieldOffset));
			} else {
				return (T)Double.valueOf(unsafe.getDoubleVolatile(target, fieldOffset));
			}
		} else if (cls == boolean.class) {
			if (!Modifier.isVolatile(field.getModifiers())) {
				return (T)Boolean.valueOf(unsafe.getBoolean(target, fieldOffset));
			} else {
				return (T)Boolean.valueOf(unsafe.getBooleanVolatile(target, fieldOffset));
			}
		} else if (cls == byte.class) {
			if (!Modifier.isVolatile(field.getModifiers())) {
				return (T)Byte.valueOf(unsafe.getByte(target, fieldOffset));
			} else {
				return (T)Byte.valueOf(unsafe.getByteVolatile(target, fieldOffset));
			}
		} else {
			if (!Modifier.isVolatile(field.getModifiers())) {
				return (T)Character.valueOf(unsafe.getChar(target, fieldOffset));
			} else {
				return (T)Character.valueOf(unsafe.getCharVolatile(target, fieldOffset));
			}
		}
	}
	
	public void setFieldValue(Object target, Field field, Object value) {
		if(value != null && !Classes.isAssignableFrom(field.getType(), value.getClass())) {
			Throwables.throwException("Value {} is not assignable to {}", value , field.getName());
		}
		target = Modifier.isStatic(field.getModifiers())?
			field.getDeclaringClass() :
			target;
		long fieldOffset = Modifier.isStatic(field.getModifiers())?
			unsafe.staticFieldOffset(field) :
			unsafe.objectFieldOffset(field);
		Class<?> cls = field.getType();
		if(!cls.isPrimitive()) {
			if (!Modifier.isVolatile(field.getModifiers())) {
				unsafe.putObject(target, fieldOffset, value);
			} else {
				unsafe.putObjectVolatile(target, fieldOffset, value);
			}			
		} else if (cls == int.class) {
			if (!Modifier.isVolatile(field.getModifiers())) {
				unsafe.putInt(target, fieldOffset, ((Integer)value).intValue());
			} else {
				unsafe.putIntVolatile(target, fieldOffset, ((Integer)value).intValue());
			}
		} else if (cls == long.class) {
			if (!Modifier.isVolatile(field.getModifiers())) {
				unsafe.putLong(target, fieldOffset, ((Long)value).longValue());
			} else {
				unsafe.putLongVolatile(target, fieldOffset, ((Long)value).longValue());
			}
		} else if (cls == float.class) {
			if (!Modifier.isVolatile(field.getModifiers())) {
				unsafe.putFloat(target, fieldOffset, ((Float)value).floatValue());
			} else {
				unsafe.putFloatVolatile(target, fieldOffset, ((Float)value).floatValue());
			}
		} else if (cls == double.class) {
			if (!Modifier.isVolatile(field.getModifiers())) {
				unsafe.putDouble(target, fieldOffset, ((Double)value).doubleValue());
			} else {
				unsafe.putDoubleVolatile(target, fieldOffset, ((Double)value).doubleValue());
			}
		} else if (cls == boolean.class) {
			if (!Modifier.isVolatile(field.getModifiers())) {
				unsafe.putBoolean(target, fieldOffset, ((Boolean)value).booleanValue());
			} else {
				unsafe.putBooleanVolatile(target, fieldOffset, ((Boolean)value).booleanValue());
			}
		} else if (cls == byte.class) {
			if (!Modifier.isVolatile(field.getModifiers())) {
				unsafe.putByte(target, fieldOffset, ((Byte)value).byteValue());
			} else {
				unsafe.putByteVolatile(target, fieldOffset, ((Byte)value).byteValue());
			}
		} else if (cls == char.class) {
			if (!Modifier.isVolatile(field.getModifiers())) {
				unsafe.putChar(target, fieldOffset, ((Character)value).charValue());
			} else {
				unsafe.putCharVolatile(target, fieldOffset, ((Character)value).charValue());
			}
		}
	}
	
	@Override
	public void close() {
		loadedPackagesMapMemoryOffset = null;
		loadedClassesVectorMemoryOffset = null;
		unsafe = null;
		illegalAccessLoggerEnabler = null;
		illegalAccessLoggerDisabler = null;
		getDeclaredFieldsRetriever = null;
		getDeclaredMethodsRetriever = null;
		getDeclaredConstructorsRetriever = null;
		packageRetriever = null;	
		methodInvoker = null;
		constructorInvoker = null;
		accessibleSetter = null;	
		consulterRetriever = null;
		classLoaderDelegateClass = null;
		builtinClassLoaderClass = null;
	}
	
	private abstract static class Initializer implements Component {
		Driver driver;
		
		private Initializer(Driver driver) {
			this.driver = driver;
			try {
				Field theUnsafeField = Unsafe.class.getDeclaredField("theUnsafe");
				theUnsafeField.setAccessible(true);
				this.driver.unsafe = (Unsafe)theUnsafeField.get(null);
			} catch (Throwable exc) {
				ManagedLoggersRepository.logInfo(getClass()::getName, "Exception while retrieving unsafe");
				Throwables.throwException(exc);
			}
		}	

		void init() {
			initConsulterRetriever();
			initMembersRetrievers();
			initAccessibleSetter();
			initConstructorInvoker();
			initMethodInvoker();
			initSpecificElements();			
			initClassesVectorField();
			initPackagesMapField();
		}

		abstract void initConsulterRetriever();
		
		abstract void initAccessibleSetter();
		
		abstract void initSpecificElements();
		
		abstract void initConstructorInvoker();	
		
		abstract void initMethodInvoker();			

		private void initPackagesMapField() {
			try {
				this.driver.loadedPackagesMapMemoryOffset = driver.unsafe.objectFieldOffset(
					this.driver.getDeclaredField(ClassLoader.class, "packages")
				);
			} catch (Throwable exc) {
				ManagedLoggersRepository.logError(getClass()::getName, "Could not initialize field memory offset of loaded classes vector");
				Throwables.throwException(exc);
			}
		}

		private void initClassesVectorField() {
			try {
				this.driver.loadedClassesVectorMemoryOffset = driver.unsafe.objectFieldOffset(
					this.driver.getDeclaredField(ClassLoader.class, "classes")
				);
			} catch (Throwable exc) {
				ManagedLoggersRepository.logError(getClass()::getName, "Could not initialize field memory offset of packages map");
				Throwables.throwException(exc);
			}
		}

		
		private static void build(Driver driver) {
			try (Initializer initializer =
					JVMInfo.getVersion() > 8 ?
						JVMInfo.getVersion() > 13 ?
								new ForJava14(driver):
							new ForJava9(driver):
						new ForJava8(driver)) {
				initializer.init();
			}
		}
		
		private void initMembersRetrievers() {
			try {
				MethodHandles.Lookup consulter = driver.getConsulter(Class.class);
				driver.getDeclaredFieldsRetriever = consulter.findSpecial(
					Class.class,
					"getDeclaredFields0",
					MethodType.methodType(Field[].class, boolean.class),
					Class.class
				);
				
				driver.getDeclaredMethodsRetriever = consulter.findSpecial(
					Class.class,
					"getDeclaredMethods0",
					MethodType.methodType(Method[].class, boolean.class),
					Class.class
				);

				driver.getDeclaredConstructorsRetriever = consulter.findSpecial(
					Class.class,
					"getDeclaredConstructors0",
					MethodType.methodType(Constructor[].class, boolean.class),
					Class.class
				);
			} catch (Throwable exc) {
				Throwables.throwException(exc);
			}
		}
		
		@Override
		public void close() {
			this.driver = null;
		}
		
		private static class ForJava8 extends Initializer {

			private ForJava8(Driver driver) {
				super(driver);
			}

			void initConsulterRetriever() {
				try {
					Field modes = MethodHandles.Lookup.class.getDeclaredField("allowedModes");
					modes.setAccessible(true);
					driver.consulterRetriever = (cls) -> {
						MethodHandles.Lookup consulter = MethodHandles.lookup().in(cls);
						try {
							modes.setInt(consulter, -1);
						} catch (Throwable exc) {
							return Throwables.throwException(exc);
						}
						return consulter;
					};
				} catch (Throwable exc) {
					ManagedLoggersRepository.logError(getClass()::getName, "Could not initialize consulter retriever");
					Throwables.throwException(exc);
				}
			}
			
			void initAccessibleSetter() {
				try {
					final Method accessibleSetterMethod = AccessibleObject.class.getDeclaredMethod("setAccessible0", AccessibleObject.class, boolean.class);
					MethodHandle accessibleSetterMethodHandle = driver.getConsulter(AccessibleObject.class).unreflect(accessibleSetterMethod);
					driver.accessibleSetter = (accessibleObject, flag) -> {
						try {
							accessibleSetterMethodHandle.invoke(accessibleObject, flag);
						} catch (Throwable exc) {
							Throwables.throwException(exc);
						}
					};
				} catch (Throwable exc) {
					ManagedLoggersRepository.logError(getClass()::getName, "Could not initialize accessible setter");
					Throwables.throwException(exc);
				}
			}
			
			@Override
			void initConstructorInvoker() {
				try {
					Class<?> nativeAccessorImplClass = Class.forName("sun.reflect.NativeConstructorAccessorImpl");
					Method method = nativeAccessorImplClass.getDeclaredMethod("newInstance0", Constructor.class, Object[].class);
					MethodHandles.Lookup consulter = driver.getConsulter(nativeAccessorImplClass);
					driver.constructorInvoker = consulter.unreflect(method);
				} catch (Throwable exc) {
					ManagedLoggersRepository.logError(getClass()::getName, "Could not initialize constructor invoker");
					Throwables.throwException(exc);
				}				
			}			
			
			void initMethodInvoker() {
				try {
					Class<?> nativeAccessorImplClass = Class.forName("sun.reflect.NativeMethodAccessorImpl");
					Method method = nativeAccessorImplClass.getDeclaredMethod("invoke0", Method.class, Object.class, Object[].class);
					MethodHandles.Lookup consulter = driver.getConsulter(nativeAccessorImplClass);
					driver.methodInvoker = consulter.unreflect(method);
				} catch (Throwable exc) {
					ManagedLoggersRepository.logError(getClass()::getName, "Could not initialize method invoker");
					Throwables.throwException(exc);
				}
			}
			
			@Override
			void initSpecificElements() {
				driver.packageRetriever = (classLoader, object, packageName) -> (Package)object;	
			}
			
		}
		
		private static class ForJava9 extends Initializer {
			
			ForJava9(Driver driver) {
				super(driver);
				try {
			        Class<?> cls = Class.forName("jdk.internal.module.IllegalAccessLogger");
			        Field logger = cls.getDeclaredField("logger");
			        final long loggerFieldOffset = driver.unsafe.staticFieldOffset(logger);
			        final Object illegalAccessLogger = driver.unsafe.getObjectVolatile(cls, loggerFieldOffset);
			        driver.illegalAccessLoggerDisabler = () ->
			        	driver.unsafe.putObjectVolatile(cls, loggerFieldOffset, null);
			        driver.illegalAccessLoggerEnabler = () ->
			        	driver.unsafe.putObjectVolatile(cls, loggerFieldOffset, illegalAccessLogger);
			        driver.disableIllegalAccessLogger();
			    } catch (Throwable e) {
			    	
			    }
			}

			void initConsulterRetriever() {
				try (
					InputStream inputStream =
						Resources.getAsInputStream(this.getClass().getClassLoader(), this.getClass().getPackage().getName().replace(".", "/") + "/ConsulterRetrieverForJDK9.bwc"
					);
					ByteBufferOutputStream bBOS = new ByteBufferOutputStream()
				) {
					Streams.copy(inputStream, bBOS);
					Class<?> methodHandleWrapperClass = driver.defineAnonymousClass(
						Class.class, bBOS.toByteArray(), null
					);
					MethodHandles.Lookup consulter = MethodHandles.lookup();
					MethodHandle methodHandle = consulter.findStatic(
						MethodHandles.class, "privateLookupIn",
						MethodType.methodType(MethodHandles.Lookup.class, Class.class, MethodHandles.Lookup.class)
					);
					driver.unsafe.putObject(methodHandleWrapperClass,
						driver.unsafe.staticFieldOffset(methodHandleWrapperClass.getDeclaredField("consulterRetriever")),
						methodHandle
					);					
					driver.consulterRetriever =
						(Function<Class<?>, MethodHandles.Lookup>)driver.unsafe.allocateInstance(methodHandleWrapperClass);
				} catch (Throwable exc) {
					ManagedLoggersRepository.logError(getClass()::getName, "Could not initialize consulter retriever");
					Throwables.throwException(exc);
				}
				
			}
			
			void initAccessibleSetter() {
				try (
					InputStream inputStream =
						Resources.getAsInputStream(this.getClass().getClassLoader(), this.getClass().getPackage().getName().replace(".", "/") + "/AccessibleSetterInvokerForJDK9.bwc"
					);
					ByteBufferOutputStream bBOS = new ByteBufferOutputStream()
				) {
					Streams.copy(inputStream, bBOS);
					Class<?> methodHandleWrapperClass = driver.defineAnonymousClass(
						AccessibleObject.class, bBOS.toByteArray(), null
					);
					driver.unsafe.putObject(methodHandleWrapperClass,
						driver.unsafe.staticFieldOffset(methodHandleWrapperClass.getDeclaredField("methodHandleRetriever")),
						driver.getConsulter(methodHandleWrapperClass)
					);					
					driver.accessibleSetter =
						(BiConsumer<AccessibleObject, Boolean>)driver.unsafe.allocateInstance(methodHandleWrapperClass);
				} catch (Throwable exc) {
					ManagedLoggersRepository.logError(getClass()::getName, "Could not initialize accessible setter");
					Throwables.throwException(exc);
				}
			}
			

			@Override
			void initConstructorInvoker() {
				try {
					Class<?> nativeAccessorImplClass = Class.forName("jdk.internal.reflect.NativeConstructorAccessorImpl");
					Method method = nativeAccessorImplClass.getDeclaredMethod("newInstance0", Constructor.class, Object[].class);
					MethodHandles.Lookup consulter = driver.getConsulter(nativeAccessorImplClass);
					driver.constructorInvoker = consulter.unreflect(method);
				} catch (Throwable exc) {
					ManagedLoggersRepository.logError(getClass()::getName, "Could not initialize constructor invoker");
					Throwables.throwException(exc);
				}	
			}
			
			void initMethodInvoker() {
				try {
					Class<?> nativeMethodAccessorImplClass = Class.forName("jdk.internal.reflect.NativeMethodAccessorImpl");
					Method invoker = nativeMethodAccessorImplClass.getDeclaredMethod("invoke0", Method.class, Object.class, Object[].class);
					MethodHandles.Lookup consulter = driver.getConsulter(nativeMethodAccessorImplClass);
					driver.methodInvoker = consulter.unreflect(invoker);
				} catch (Throwable exc) {
					ManagedLoggersRepository.logError(getClass()::getName, "Could not initialize method invoker");
					Throwables.throwException(exc);
				}
			}

			
			@Override
			void initSpecificElements() {
				try {
					MethodHandles.Lookup classLoaderConsulter = driver.consulterRetriever.apply(ClassLoader.class);
					MethodType methodType = MethodType.methodType(Package.class, String.class);
					MethodHandle methodHandle = classLoaderConsulter.findSpecial(ClassLoader.class, "getDefinedPackage", methodType, ClassLoader.class);
					driver.packageRetriever = (classLoader, object, packageName) -> {
						try {
							return (Package)methodHandle.invokeExact(classLoader, packageName);
						} catch (Throwable exc) {
							return Throwables.throwException(exc);
						}
					};
				} catch (Throwable exc) {
					ManagedLoggersRepository.logError(getClass()::getName, "Could not initialize package retriever");
					Throwables.throwException(exc);
				}
				try {
					driver.builtinClassLoaderClass = Class.forName("jdk.internal.loader.BuiltinClassLoader");
				} catch (Throwable exc) {
					ManagedLoggersRepository.logError(getClass()::getName, "Could not initialize builtin class loader class");
					Throwables.throwException(exc);
				}
				try (
					InputStream inputStream =
						Resources.getAsInputStream(this.getClass().getClassLoader(), this.getClass().getPackage().getName().replace(".", "/") + "/ClassLoaderDelegateForJDK9.bwc"
					);
					ByteBufferOutputStream bBOS = new ByteBufferOutputStream()
				) {
					Streams.copy(inputStream, bBOS);
					driver.classLoaderDelegateClass = driver.defineAnonymousClass(
						driver.builtinClassLoaderClass, bBOS.toByteArray(), null
					);
				} catch (Throwable exc) {
					ManagedLoggersRepository.logError(getClass()::getName, "Could not initialize class loader delegate class");
					Throwables.throwException(exc);
				}
				try {
					initDeepConsulterRetriever();
				} catch (Throwable exc) {
					ManagedLoggersRepository.logInfo(getClass()::getName, "Could not initialize deep consulter retriever");
					Throwables.throwException(exc);
				}
			}

			void initDeepConsulterRetriever() throws Throwable {
				Constructor<MethodHandles.Lookup> lookupCtor = MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, int.class);
				driver.setAccessible(lookupCtor, true);
				Field fullPowerModeConstant = MethodHandles.Lookup.class.getDeclaredField("FULL_POWER_MODES");
				driver.setAccessible(fullPowerModeConstant, true);
				int fullPowerModeConstantValue = fullPowerModeConstant.getInt(null);
				MethodHandle methodHandle = lookupCtor.newInstance(MethodHandles.Lookup.class, fullPowerModeConstantValue).findConstructor(
					MethodHandles.Lookup.class, MethodType.methodType(void.class, Class.class, int.class)
				);
				driver.consulterRetriever = cls -> {
					try {
						return (MethodHandles.Lookup)methodHandle.invoke(cls, fullPowerModeConstantValue);
					} catch (Throwable exc) {
						return Throwables.throwException(exc);
					}
				};
			}
			
			@Override
			public void close() {
				super.close();
			}

		}
		
		private static class ForJava14 extends ForJava9 {
			
			ForJava14(Driver driver) {
				super(driver);
			}
			
			@Override
			void initDeepConsulterRetriever() throws Throwable {
				Constructor<?> lookupCtor = MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, Class.class, int.class);
				driver.setAccessible(lookupCtor, true);
				Field fullPowerModeConstant = MethodHandles.Lookup.class.getDeclaredField("FULL_POWER_MODES");
				driver.setAccessible(fullPowerModeConstant, true);
				int fullPowerModeConstantValue = fullPowerModeConstant.getInt(null);
				MethodHandle mthHandle = ((MethodHandles.Lookup)lookupCtor.newInstance(MethodHandles.Lookup.class, null, fullPowerModeConstantValue)).findConstructor(
					MethodHandles.Lookup.class, MethodType.methodType(void.class, Class.class, Class.class, int.class)
				);
				driver.consulterRetriever = cls -> {
					try {
						return (MethodHandles.Lookup)mthHandle.invoke(cls, null, fullPowerModeConstantValue);
					} catch (Throwable exc) {
						return Throwables.throwException(exc);
					}
				};
			}
		}		
		
	}

}
