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

import static org.burningwave.core.assembler.StaticComponentContainer.Fields;
import static org.burningwave.core.assembler.StaticComponentContainer.Methods;

import java.lang.reflect.Field;
import java.util.Map;

public interface Virtual {
	
	default <T> T getValueOf(String fieldName) {
		return Fields.get(this, fieldName);
	}
	
	default <T> T getDirectValueOf(String fieldName) {
		return Fields.getDirect(this, fieldName);
	}
	
	default <T> T invoke(String methodName, Object... parameters) {
		return Methods.invoke(this, methodName, parameters);
	}
	
	default <T> T invokeDirect(String methodName, Object... parameters) {
		return Methods.invokeDirect(this, methodName, parameters);
	}
	
	default Map<Field, ?> getAllValues() {
		return Fields.getAll(this);
	}
	
	default Map<Field, ?> getAllDirectValues() {
		return Fields.getAllDirect(this);
	}	
}