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

import static org.burningwave.core.assembler.StaticComponentContainer.Classes;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

import org.burningwave.core.Criteria;
import org.burningwave.core.function.TriPredicate;


@SuppressWarnings("resource")
public class FieldCriteria extends MemberCriteria<
	Field, FieldCriteria, Criteria.TestContext<Field, FieldCriteria>
> {
	private FieldCriteria() {
		super();
	}
	
	public static FieldCriteria withoutConsideringParentClasses() {
		return byScanUpTo((lastClassInHierarchy, currentScannedClass) -> {
            return lastClassInHierarchy.equals(currentScannedClass);
        });
	}
	
	public static FieldCriteria forEntireClassHierarchy() {
		return new FieldCriteria();
	}
	
	@Override
	Function<Class<?>, Field[]> getMembersSupplierFunction() {
		return Classes::getDeclaredFields;
	}

	public static FieldCriteria byScanUpTo(BiPredicate<Class<?>, Class<?>> predicate) {
		return new FieldCriteria().scanUpTo(predicate);
	}
	
	public static FieldCriteria byScanUpTo(TriPredicate<Map<Class<?>, Class<?>>, Class<?>, Class<?>> predicate) {
		return new FieldCriteria().scanUpTo(predicate);
	}
	
	
	public FieldCriteria type(final Predicate<Class<?>> predicate) {
		this.predicate = concat(
			this.predicate,
			(context, member) -> predicate.test(member.getType())
		);
		return this;
	}
}