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

import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.function.Predicate;

public interface MembersRetriever {
	
	public default Field getDeclaredField(Class<?> cls, Predicate<Field> predicate) {
		Collection<Field> members = getDeclaredFields(cls, predicate);
		if (members.size() > 1) {
			Throwables.throwException("More than one member found for class {}", cls.getName());
		}
		return members.stream().findFirst().orElse(null);
	}
	
	public default Method getDeclaredMethod(Class<?> cls, Predicate<Method> predicate) {
		Collection<Method> members = getDeclaredMethods(cls, predicate);
		if (members.size() > 1) {
			Throwables.throwException("More than one member found for class {}", cls.getName());
		}
		return members.stream().findFirst().orElse(null);
	}
	
	public default <T> Constructor<T> getDeclaredConstructor(Class<T> cls, Predicate<Constructor<T>> predicate) {
		Collection<Constructor<T>> members = getDeclaredConstructors(cls, predicate);
		if (members.size() > 1) {
			Throwables.throwException("More than one member found for class {}", cls.getName());
		}
		return members.stream().findFirst().orElse(null);
	}
	
	public default Collection<Field> getDeclaredFields(Class<?> cls, Predicate<Field> memberPredicate) {
		Collection<Field> members = new HashSet<>();
		for (Field member : getDeclaredFields(cls)) {
			if (memberPredicate.test(member)) {
				members.add(member);
			}
		}
		return members;
	}
	
	

	public default <T> Collection<Constructor<T>> getDeclaredConstructors(Class<T> cls, Predicate<Constructor<T>> predicate) {
		Collection<Constructor<T>> members = new HashSet<>();
		for (Constructor<T> member : getDeclaredConstructors(cls)) {
			if (predicate.test(member)) {
				members.add(member);
			}
		}
		return members;
	}
	
	

	public default Collection<Method> getDeclaredMethods(Class<?> cls, Predicate<Method> memberPredicate) {
		Collection<Method> members = new HashSet<>();
		for (Method member : getDeclaredMethods(cls)) {
			if (memberPredicate.test(member)) {
				members.add(member);
			}
		}
		return members;
	}
	
	public Field[] getDeclaredFields(Class<?> cls);
	
	public <T> Constructor<T>[] getDeclaredConstructors(Class<T> cls);
	
	public Method[] getDeclaredMethods(Class<?> cls);
	
}