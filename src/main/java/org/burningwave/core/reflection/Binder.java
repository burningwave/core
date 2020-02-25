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

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import org.burningwave.core.Component;
import org.burningwave.core.classes.MemberFinder;

public interface Binder extends Component {
	
	public <F> F bindTo(Class<?> targetClass, String methodName, Class<?>... types) throws Throwable;
	
	public <F> F bindTo(Method method) throws Throwable;
	
	static abstract class Abst implements Binder {
		MemberFinder memberFinder;
		ConsulterRetriever consulterRetriever;
		
		Abst(MemberFinder memberFinder, ConsulterRetriever consulterRetriever) {
			this.memberFinder = memberFinder;
			this.consulterRetriever = consulterRetriever;
		}
	}
	
	public interface Multi extends Binder {
		
		public <F, I> Map<I, F> bindToMany(Class<?> targetClass, String methodName) throws Throwable;
		
		static abstract class Abst extends Binder.Abst implements Multi {
			BiFunction<ClassLoader, Integer, Class<?>> classRetriever;
			MemberFinder memberFinder;

			Abst(
				MemberFinder memberFinder,
				ConsulterRetriever consulterRetriever,
				BiFunction<ClassLoader, Integer, Class<?>> classRetriever
			) {
				super(memberFinder, consulterRetriever);
				this.classRetriever = classRetriever;
			}

			Class<?> retrieveClass(Class<?> cls, ClassLoader classLoader, int parametersCount) throws ClassNotFoundException {
				if (parametersCount < 3) {
					return Class.forName(retrieveClassName(cls, parametersCount));	
				} else {
					return classRetriever.apply(classLoader, parametersCount);
				}
			}
			
			String retrieveClassName(Class<?>cls, int parametersCount) {
				switch (parametersCount) {
		        	case 2:
		        		return Optional.ofNullable(cls.getPackage()).map(pkg -> pkg.getName()).orElse(null) + ".Bi" + cls.getSimpleName();
		        	default : 
		        		return cls.getName();
				}
			}
			
			<F, I> Map<I, F> createResultMap() {
				return new LinkedHashMap<I, F>() {
					private static final long serialVersionUID = -5808566429515776779L;
	
					@Override
					public F get(Object key) {
						if (key instanceof Class[] || key instanceof Class) {
							final Class<?>[] params = key.getClass().isArray()?
								(Class<?>[] )key :
									new Class<?>[] {(Class<?>)key};
							
							key = this.keySet().stream().filter((method) -> {
								Class<?>[] mthParams = ((Method)method).getParameterTypes();
								if (mthParams.length != params.length) {
									return false;
								}
								for (int i = 0; i < mthParams.length; i++) {
									if (mthParams[i] != params[i]) {
										return false;
									}
								}
	
								return true;
							}).findFirst().orElse(null);
						}
						return super.get(key);
					}
				};
			}
		}
	}
}