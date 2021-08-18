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

import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;

import org.burningwave.core.Closeable;

/**
 * @since 8.12.4
 */
public abstract class Driver implements Closeable {

	protected abstract void setFieldValue(Object target, Field field, Object value);

	protected abstract <T> T getFieldValue(Object target, Field field);

	protected abstract Method[] getDeclaredMethods(Class<?> cls);

	protected abstract <T> Constructor<T>[] getDeclaredConstructors(Class<T> cls);

	protected abstract Field[] getDeclaredFields(Class<?> cls);

	protected abstract Field getDeclaredField(Class<?> cls, String name);

	protected abstract <T> T newInstance(Constructor<T> ctor, Object[] params);

	protected abstract Object invoke(Method method, Object target, Object[] params);

	protected abstract Lookup getConsulter(Class<?> cls);

	protected abstract Class<?> getClassLoaderDelegateClass();

	protected abstract Class<?> getBuiltinClassLoaderClass();

	protected abstract boolean isClassLoaderDelegate(ClassLoader classLoader);

	protected abstract boolean isBuiltinClassLoader(ClassLoader classLoader);

	protected abstract Map<String, ?> retrieveLoadedPackages(ClassLoader classLoader);

	protected abstract Collection<Class<?>> retrieveLoadedClasses(ClassLoader classLoader);

	protected abstract Package retrieveLoadedPackage(ClassLoader classLoader, Object packageToFind, String packageName);

	protected abstract Class<?> defineHookClass(Class<?> clientClass, byte[] byteCode);

	protected abstract void setAccessible(AccessibleObject object, boolean flag);
	
	public abstract void close();

}