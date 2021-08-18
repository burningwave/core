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
 * Copyright (c) 2021 Roberto Gentili
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

import static org.burningwave.core.assembler.StaticComponentContainer.Constructors;
import static org.burningwave.core.assembler.StaticComponentContainer.Fields;
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggersRepository;
import static org.burningwave.core.assembler.StaticComponentContainer.Members;
import static org.burningwave.core.assembler.StaticComponentContainer.Methods;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

import org.burningwave.core.Closeable;
import org.burningwave.core.ManagedLogger;
import org.burningwave.core.assembler.StaticComponentContainer;
import org.burningwave.core.classes.MembersRetriever;
import org.burningwave.core.classes.MemoryClassLoader;
import org.burningwave.core.classes.MethodCriteria;
import org.burningwave.core.function.Executor;

@SuppressWarnings({"all"})
public class LowLevelObjectsHandler implements Closeable, ManagedLogger, MembersRetriever {
	
	Driver driver;
	
	Field[] emtpyFieldsArray;
	Method[] emptyMethodsArray;
	Constructor<?>[] emptyConstructorsArray;

	private LowLevelObjectsHandler(String driverClassName) {
		emtpyFieldsArray = new Field[]{};
		emptyMethodsArray = new Method[]{};
		emptyConstructorsArray = new Constructor<?>[]{};
		driver = Executor.get(() -> (Driver)this.getClass().getClassLoader().loadClass(driverClassName).getDeclaredConstructor().newInstance());
	}
	
	public static LowLevelObjectsHandler create(String driverClassName) {
		return new LowLevelObjectsHandler(driverClassName);
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
}
