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
package org.burningwave.core;

public class Objects {
	
	public static Objects create() {
		return new Objects();
	}
	
	public String getId(Object target) {
		return target.getClass().getName() + "@" +  System.identityHashCode(target);
	}	
	
	public String getStandardId(Object target) {
		return target.getClass().getName() + "@" +  Integer.toHexString(System.identityHashCode(target));
	}

	public String getCurrentId(Object target) {
		return target.getClass().getName() + "@" +  System.identityHashCode(target) + "_" + System.currentTimeMillis();
	}
	
	public String getClassId(Class<?> targetClass) {
		return targetClass.getName() + "@" + System.identityHashCode(targetClass.getClass());
	}
	
	public int toInt(Object object) {
		return object instanceof Integer ?
			(int)object
			: Integer.valueOf(
				object.toString()
			);
	}
	
	public long toLong(Object object) {
		return object instanceof Long ?
			(Long)object
			: Long.valueOf(
				object.toString()
			);
	}
	
	public boolean toBoolean(Object object) {
		return object instanceof Boolean ?
			(Boolean)object
			: Boolean.valueOf(
				object.toString()
			);
	}
}
