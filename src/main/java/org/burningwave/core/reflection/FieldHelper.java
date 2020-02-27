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

import static org.burningwave.core.assembler.StaticComponentsContainer.Cache;
import static org.burningwave.core.assembler.StaticComponentsContainer.Classes;
import static org.burningwave.core.assembler.StaticComponentsContainer.MemberFinder;
import static org.burningwave.core.assembler.StaticComponentsContainer.Throwables;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Optional;

import org.burningwave.core.classes.FieldCriteria;


public class FieldHelper extends MemberHelper<Field> {

	private FieldHelper() {
		super();
	}
	
	public static FieldHelper create() {
		return new FieldHelper();
	}
	
	public Field findOneAndMakeItAccessible(Object target, String fieldName) {
		return findOneAndMakeItAccessible(target, fieldName, true);
	}
	
	
	public Field findOneAndMakeItAccessible(
		Object target,
		String fieldName,
		boolean cacheField
	) {
		String cacheKey = getCacheKey(target, "equals " + fieldName, (Object[])null);
		Collection<Field> members = Cache.uniqueKeyForFields.get(cacheKey);
		if (members == null) {
			members = MemberFinder.findAll(
				FieldCriteria.forName(
					fieldName::equals
				),
				target
			);
			Optional.ofNullable(members.stream().findFirst().get()).orElseThrow(() ->
				Throwables.toRuntimeException("Field \"" + fieldName
					+ "\" not found in any class of " + Classes.retrieveFrom(target).getName()
					+ " hierarchy"
				)
			).setAccessible(true);
			if (cacheField) {
				final Collection<Field> toUpload = members;
				Cache.uniqueKeyForFields.upload(cacheKey, () -> toUpload);
			}
		}
		return members.stream().findFirst().get();
	}

}
