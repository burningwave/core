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

import static org.burningwave.core.assembler.StaticComponentContainer.BackgroundExecutor;
import static org.burningwave.core.assembler.StaticComponentContainer.Classes;
import static org.burningwave.core.assembler.StaticComponentContainer.Constructors;
import static org.burningwave.core.assembler.StaticComponentContainer.Fields;
import static org.burningwave.core.assembler.StaticComponentContainer.JVMInfo;
import static org.burningwave.core.assembler.StaticComponentContainer.LowLevelObjectsHandler;
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggersRepository;
import static org.burningwave.core.assembler.StaticComponentContainer.Members;
import static org.burningwave.core.assembler.StaticComponentContainer.Methods;
import static org.burningwave.core.assembler.StaticComponentContainer.Resources;
import static org.burningwave.core.assembler.StaticComponentContainer.Streams;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.burningwave.core.Closeable;
import org.burningwave.core.Component;
import org.burningwave.core.ManagedLogger;
import org.burningwave.core.assembler.StaticComponentContainer;
import org.burningwave.core.classes.MembersRetriever;
import org.burningwave.core.classes.MemoryClassLoader;
import org.burningwave.core.classes.MethodCriteria;
import org.burningwave.core.function.Executor;
import org.burningwave.core.function.TriFunction;
import org.burningwave.core.io.ByteBufferOutputStream;

import sun.misc.Unsafe;

@SuppressWarnings({"all"})
public class LowLevelObjectsHandler implements Closeable, ManagedLogger, MembersRetriever {
	
	Driver driver;
	
	Field[] emtpyFieldsArray;
	Method[] emptyMethodsArray;
	Constructor<?>[] emptyConstructorsArray;

	private LowLevelObjectsHandler() {
		emtpyFieldsArray = new Field[]{};
		emptyMethodsArray = new Method[]{};
		emptyConstructorsArray = new Constructor<?>[]{};
		driver = Driver.create();
	}
	
	public static LowLevelObjectsHandler create() {
		return new LowLevelObjectsHandler();
	}
	
	public Class<?> defineAnonymousClass(Class<?> outerClass, byte[] byteCode, Object[] var3) {
		return driver.defineAnonymousClass(outerClass, byteCode, var3);
	}
	
	public Package retrieveLoadedPackage(ClassLoader classLoader, Object packageToFind, String packageName) throws Throwable {
		return driver.retrieveLoadedPackage(classLoader, packageToFind, packageName);
	}
		
	public Collection<Class<?>> retrieveLoadedClasses(ClassLoader classLoader) {
		return driver.retrieveLoadedClasses(classLoader);
	}
	
	public Map<String, ?> retrieveLoadedPackages(ClassLoader classLoader) {
		return driver.retrieveLoadedPackages(classLoader);
	}
	
	public boolean isBuiltinClassLoader(ClassLoader classLoader) {
		return driver.isBuiltinClassLoader(classLoader);
	}
	
	public boolean isClassLoaderDelegate(ClassLoader classLoader) {
		return driver.isClassLoaderDelegate(classLoader);
	}
	
	public ClassLoader getParent(ClassLoader classLoader) {
		if (isClassLoaderDelegate(classLoader)) {
			return getParent(Fields.getDirect(classLoader, "classLoader"));
		} else if (isBuiltinClassLoader(classLoader)) {
			Field builtinClassLoaderClassParentField = Fields.findFirstAndMakeItAccessible(driver.getBuiltinClassLoaderClass(), "parent", classLoader.getClass());
			return Executor.get(() ->(ClassLoader) builtinClassLoaderClassParentField.get(classLoader));
		} else {
			return classLoader.getParent();
		}
	}
	
	public synchronized Function<Boolean, ClassLoader> setAsParent(ClassLoader target, ClassLoader originalFutureParent) {
		if (isClassLoaderDelegate(target)) {
			return setAsParent(Fields.getDirect(target, "classLoader"), originalFutureParent);
		}
		ClassLoader futureParentTemp = originalFutureParent;
		if (isBuiltinClassLoader(target)) {
			futureParentTemp = checkAndConvertBuiltinClassLoader(futureParentTemp);
		}
		ClassLoader targetExParent = Fields.get(target, "parent");
		ClassLoader futureParent = futureParentTemp;
		checkAndRegisterOrUnregisterMemoryClassLoaders(target, targetExParent, originalFutureParent);		
		Fields.setDirect(target, "parent", futureParent);
		return (reset) -> {
			if (reset) {
				checkAndRegisterOrUnregisterMemoryClassLoaders(target, originalFutureParent, targetExParent);
				Fields.setDirect(target, "parent", targetExParent);
			}
			return targetExParent;
		};
	}

	private ClassLoader checkAndConvertBuiltinClassLoader(ClassLoader classLoader) {
		if (!isBuiltinClassLoader(classLoader)) {
			try {
				Collection<Method> methods = Members.findAll(
					MethodCriteria.byScanUpTo(
						cls -> cls.getName().equals(ClassLoader.class.getName())
					).name(
						"loadClass"::equals
					).and().parameterTypesAreAssignableFrom(
						String.class, boolean.class
					), classLoader.getClass()
				);					
				classLoader = (ClassLoader)Constructors.newInstanceOf(driver.getClassLoaderDelegateClass(), null, classLoader, Methods.findDirectHandle(
					methods.stream().skip(methods.size() - 1).findFirst().get()
				));
			} catch (Throwable exc) {
				Throwables.throwException(exc);
			}
		}
		return classLoader;
	}

	private void checkAndRegisterOrUnregisterMemoryClassLoaders(ClassLoader target, ClassLoader exParent, ClassLoader futureParent) {
		if (isClassLoaderDelegate(target)) {
			target = Fields.getDirect(target, "classLoader");
		}
		if (exParent != null && isClassLoaderDelegate(exParent)) {
			exParent = Fields.getDirect(exParent, "classLoader");
		}
		if (futureParent != null && isClassLoaderDelegate(futureParent)) {
			futureParent = Fields.getDirect(futureParent, "classLoader");
		}
		MemoryClassLoader exParentMC = exParent instanceof MemoryClassLoader? (MemoryClassLoader)exParent : null;
		MemoryClassLoader futureParentMC = futureParent instanceof MemoryClassLoader? (MemoryClassLoader)futureParent : null;
		MemoryClassLoader targetMemoryClassLoader = target instanceof MemoryClassLoader? (MemoryClassLoader)target : null;
		if (targetMemoryClassLoader != null) {
			if (futureParentMC != null) {
				futureParentMC.register(targetMemoryClassLoader);
			}
			if (exParentMC != null) {
				exParentMC.unregister(targetMemoryClassLoader, false);
			}
		}
	}
	
	public void setAccessible(AccessibleObject object, boolean flag) {
		try {
			object.setAccessible(true);
		} catch (Throwable exc) {
			driver.setAccessible(object, flag);
		}
	}
	
	public Object invoke(Object target, Method method, Object... params) {
		if (params == null) {
			params = new Object[] {null};
		}
		try {
			return method.invoke(target, params);
		} catch (Throwable exc) {
			return driver.invoke(method, target, params);
		}
	}
	
	public <T> T newInstance(Constructor<T> ctor, Object... params) {
		if (params == null) {
			params = new Object[] {null};
		}
		try {
			return ctor.newInstance(params);
		} catch (Throwable exc) {
			return driver.newInstance(ctor, params);
		}
	}
	
	public <T> T getFieldValue(Object target, Field field) {
		return driver.getFieldValue(target, field);
	}
	
	public void setFieldValue(Object target, Field field, Object value) {
		driver.setFieldValue(target, field, value);
	}
	
	@Override
	public Field[] getDeclaredFields(Class<?> cls)  {
		try {
			return driver.getDeclaredFields(cls);
		} catch (Throwable exc) {
			ManagedLoggersRepository.logWarn(getClass()::getName, "Could not retrieve fields of class {}. Cause: {}", cls.getName(), exc.getMessage());
			return emtpyFieldsArray;
		}		
	}
	
	@Override
	public <T> Constructor<T>[] getDeclaredConstructors(Class<T> cls) {
		try {
			return driver.getDeclaredConstructors(cls);
		} catch (Throwable exc) {
			ManagedLoggersRepository.logWarn(getClass()::getName, "Could not retrieve constructors of class {}. Cause: {}", cls.getName(), exc.getMessage());
			return (Constructor<T>[])emptyConstructorsArray;
		}
	}
	
	@Override
	public Method[] getDeclaredMethods(Class<?> cls)  {
		try {
			return driver.getDeclaredMethods(cls);
		} catch (Throwable exc) {
			ManagedLoggersRepository.logWarn(getClass()::getName, "Could not retrieve methods of class {}. Cause: {}", cls.getName(), exc.getMessage());
			return emptyMethodsArray;
		}
	}
	
	public MethodHandles.Lookup getConsulter(Class<?> cls) {
		return driver.getConsulter(cls);
	}
	
	@Override
	public void close() {
		if (this != StaticComponentContainer.LowLevelObjectsHandler) {
			emtpyFieldsArray = null;
			emptyMethodsArray = null;
			emptyConstructorsArray = null;
			driver.close();
		} else {
			Throwables.throwException("Could not close singleton instance " + this);
		}
	}

	public static class ByteBufferHandler implements Component {
		private Field directAllocatedByteBufferAddressField;
		
		public ByteBufferHandler() {
			BackgroundExecutor.createTask(() -> {
				init();
				synchronized (this) {
					this.notifyAll();
				}
			}).setName("ByteBufferHandler initializer").submit();
		}

		void init() {
			try {
				if (LowLevelObjectsHandler == null) {
					synchronized (LowLevelObjectsHandler.class) {
						if (LowLevelObjectsHandler == null) {							
							LowLevelObjectsHandler.class.wait();
						}
					}
				}
				Class directByteBufferClass = ByteBuffer.allocateDirect(1).getClass();
				while (directByteBufferClass != null && directAllocatedByteBufferAddressField == null) {
					directAllocatedByteBufferAddressField = LowLevelObjectsHandler.getDeclaredField(directByteBufferClass, field -> "address".equals(field.getName()));
					directByteBufferClass = directByteBufferClass.getSuperclass();
				}
			} catch (InterruptedException exc) {
				Throwables.throwException(exc);
			}
		}
		
		public static ByteBufferHandler create() {
			return new ByteBufferHandler();
		}
		
		public ByteBuffer allocate(int capacity) {
			return ByteBuffer.allocate(capacity);
		}
		
		public ByteBuffer allocateDirect(int capacity) {
			return ByteBuffer.allocateDirect(capacity);
		}
		
		public ByteBuffer duplicate(ByteBuffer buffer) {
			return buffer.duplicate();
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
		
		public <T extends Buffer> long getAddress(T buffer) {
			try {
				return (long)LowLevelObjectsHandler.getFieldValue(buffer, directAllocatedByteBufferAddressField);
			} catch (NullPointerException exc) {
				return (long)LowLevelObjectsHandler.getFieldValue(buffer, getDirectAllocatedByteBufferAddressField());
			}
		}
		
		private Field getDirectAllocatedByteBufferAddressField() {
			if (directAllocatedByteBufferAddressField == null) {
				synchronized (this) {
					if (directAllocatedByteBufferAddressField == null) {
						try {
							this.wait();
						} catch (InterruptedException exc) {
							Throwables.throwException(exc);
						}
					}
				}
			}
			return directAllocatedByteBufferAddressField;
		}

		public <T extends Buffer> boolean destroy(T buffer, boolean force) {
			if (buffer.isDirect()) {
				Cleaner cleaner = getCleaner(buffer, force);
				if (cleaner != null) {
					return cleaner.clean();
				}
				return false;
			} else {
				return true;
			}
		}
		
		private <T extends Buffer> Object getInternalCleaner(T buffer, boolean findInAttachments) {
			if (buffer.isDirect()) {
				if (buffer != null) {
					Object cleaner;
					if ((cleaner = Fields.get(buffer, "cleaner")) != null) {
						return cleaner;
					} else if (findInAttachments){
						return getInternalCleaner(Fields.getDirect(buffer, "att"), findInAttachments);
					}
				}
			}
			return null;
		}
		
		private <T extends Buffer> Object getInternalDeallocator(T buffer, boolean findInAttachments) {
			if (buffer.isDirect()) {
				Object cleaner = getInternalCleaner(buffer, findInAttachments);
				if (cleaner != null) {
					return Fields.getDirect(cleaner, "thunk");
				}
			}
			return null;
		}
		
		private <T extends Buffer> Collection<T> getAllLinkedBuffers(T buffer) {
			Collection<T> allLinkedBuffers = new ArrayList<>();
			allLinkedBuffers.add(buffer);
			while((buffer = Fields.getDirect(buffer, "att")) != null) {
				allLinkedBuffers.add(buffer);
			}
			return allLinkedBuffers;
		}
		
		public  <T extends Buffer> Cleaner getCleaner(T buffer, boolean findInAttachments) {
			Object cleaner;
			if ((cleaner = getInternalCleaner(buffer, findInAttachments)) != null) {
				return new Cleaner () {
					
					@Override
					public boolean clean() {
						if (getAddress() != 0) {
							Methods.invokeDirect(cleaner, "clean");
							getAllLinkedBuffers(buffer).stream().forEach(linkedBuffer ->
								Fields.setDirect(linkedBuffer, "address", 0L)
							);							
							return true;
						}
						return false;
					}
					
					long getAddress() {
						return Long.valueOf((long)Fields.getDirect(Fields.getDirect(cleaner, "thunk"), "address"));
					}

					@Override
					public boolean cleaningHasBeenPerformed() {
						return getAddress() == 0;
					}
					
				};
			}
			return null;
		}
		
		public <T extends Buffer> Deallocator getDeallocator(T buffer, boolean findInAttachments) {
			if (buffer.isDirect()) {
				Object deallocator;
				if ((deallocator = getInternalDeallocator(buffer, findInAttachments)) != null) {
					return new Deallocator() {
						
						@Override
						public boolean freeMemory() {
							if (getAddress() != 0) {
								Methods.invokeDirect(deallocator, "run");
								getAllLinkedBuffers(buffer).stream().forEach(linkedBuffer ->
									Fields.setDirect(linkedBuffer, "address", 0L)
								);	
								return true;
							} else {
								return false;
							}
						}

						public long getAddress() {
							return Long.valueOf((long)Fields.getDirect(deallocator, "address"));
						}

						@Override
						public boolean memoryHasBeenReleased() {
							return getAddress() == 0;
						}
						
					};
				}
			}
			return null;
		}
		
		public static interface Deallocator {
			
			public boolean freeMemory();
			
			boolean memoryHasBeenReleased();
			
		}
		
		public static interface Cleaner {
			
			public boolean clean();
			
			public boolean cleaningHasBeenPerformed();
			
		}
	}
}
