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

import static org.burningwave.core.assembler.StaticComponentContainer.Classes;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.lang.reflect.Constructor;
import java.util.Optional;

import org.burningwave.core.classes.ConstructorCriteria;
import org.burningwave.core.function.ThrowingSupplier;


public class Constructors extends MemberHelper<Constructor<?>>  {

	private Constructors() {
		super();
	}
	
	public static Constructors create() {
		return new Constructors();
	}
	
	
	@SuppressWarnings("unchecked")
	public <T> T newInstanceOf(
			Object object,
			Object... arguments) {
		return ThrowingSupplier.get(() -> 
			(T)findOneAndMakeItAccessible(object, arguments).newInstance(arguments)
		);
	}

	@SuppressWarnings("unchecked")
	public <T> Constructor<T> findOneAndMakeItAccessible(Object object, Object... arguments) {
		ConstructorCriteria criteria = ConstructorCriteria.byScanUpTo(object).parameterTypesAreAssignableFrom(
			arguments
		);
		Constructor<T> member = (Constructor<T>)findOneAndApply(
			criteria, object, (mmb) ->	mmb.setAccessible(true)
		);
		Optional.ofNullable(member).orElseThrow(() ->
			Throwables.toRuntimeException("Constructor not found for class " + Classes.retrieveFrom(object))
		);
		return member;
	}

}
