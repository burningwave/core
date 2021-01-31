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

import java.lang.reflect.Executable;

public interface FunctionalInterfaceFactory {
	
	public static FunctionalInterfaceFactory create(ClassFactory classFactory) {
		return new FunctionalInterfaceFactoryImpl(classFactory);
	}
	
	public <T> T getOrCreate(Class<?> targetClass, Class<?>... argumentTypes);
	
	public <T> T getOrCreate(Class<?> targetClass, String methodName, Class<?>... argumentTypes);
	
	public <F> F getOrCreate(Executable executable);

	public <T> T getOrCreateFunction(Class<?> targetClass, String methodName, Class<?>... argumentTypes);

	public <T> T getOrCreatePredicate(Class<?> targetClass, String methodName, Class<?>... argumentTypes);
	
	public <T> T getOrCreateConsumer(Class<?> targetClass, String methodName, Class<?>... argumentTypes);
	
	public <T> T getOrCreateSupplier(Class<?> targetClass, String methodName);
	
	public <T> Class<T> loadOrBuildAndDefineFunctionSubType(int parametersCount);

	public <T> Class<T> loadOrBuildAndDefineFunctionSubType(ClassLoader classLoader, int parametersLength);

	public <T> Class<T> loadOrBuildAndDefineConsumerSubType(int parametersCount);

	public <T> Class<T> loadOrBuildAndDefineConsumerSubType(ClassLoader classLoader, int parametersLength);

	public <T> Class<T> loadOrBuildAndDefinePredicateSubType(int parametersLength);

	public <T> Class<T> loadOrBuildAndDefinePredicateSubType(ClassLoader classLoader, int parametersLength);

	
}
