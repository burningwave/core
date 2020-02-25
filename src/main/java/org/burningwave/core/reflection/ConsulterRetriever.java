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
package org.burningwave.core.reflection;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.burningwave.Throwables;
import org.burningwave.core.Component;
import org.burningwave.core.function.ThrowingFunction;
import org.burningwave.core.jvm.JVMInfo;

public class ConsulterRetriever implements Component {
	private ThrowingFunction<Class<?>, Lookup, Throwable> function;
	
	private ConsulterRetriever(JVMInfo jVMInfo) {
		if (jVMInfo.getVersion() > 8) {
			try {
				Method consulterRetrieverMethod = MethodHandles.class.getDeclaredMethod("privateLookupIn", Class.class, Lookup.class);
				function = cls -> (Lookup)consulterRetrieverMethod.invoke(null, cls, MethodHandles.lookup());
			} catch (IllegalArgumentException | NoSuchMethodException
					| SecurityException exc) {
				logError("Could not initialize consulter", exc);
				throw Throwables.toRuntimeException(exc);
			}
		} else {
			Field modes;
			try {
				modes = Lookup.class.getDeclaredField("allowedModes");
			} catch (NoSuchFieldException | SecurityException exc) {
				throw Throwables.toRuntimeException(exc);
			}
			modes.setAccessible(true);
			function = (cls) -> {
				Lookup consulter = MethodHandles.lookup().in(cls);
				modes.setInt(consulter, -1);
				return consulter;
			};
			
		}
	}
	

	public static ConsulterRetriever create(JVMInfo jVMInfo) {
		return new ConsulterRetriever(jVMInfo);
	}
	
	public static ConsulterRetriever getInstance() {
		return LazyHolder.getConsulterRetrieverInstance();
	}
	
	public MethodHandles.Lookup retrieve(Class<?> cls) {
		try {
			return function.apply(cls);
		} catch (Throwable exc) {
			throw Throwables.toRuntimeException(exc);
		}
	}
	
	private static class LazyHolder {
		private static final ConsulterRetriever CONSULTER_RETRIEVER_INSTANCE = ConsulterRetriever.create(JVMInfo.getInstance());
		
		private static ConsulterRetriever getConsulterRetrieverInstance() {
			return CONSULTER_RETRIEVER_INSTANCE;
		}
	}
}
