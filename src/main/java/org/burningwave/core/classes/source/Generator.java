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
package org.burningwave.core.classes.source;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;

public interface Generator {
	
	public String make();
	
	public default <F> String _toString() {
		return make();
	}
	
	public static abstract class Abst implements Generator {
		static final String EMPTY_SPACE = " ";
		static final String COMMA = ",";
		static final String SEMICOLON = ";";
		
		@Override
		public String toString() {
			return make();
		}
		
		String getOrEmpty(Generator value) {
			return Optional.ofNullable(value.make()).orElseGet(() -> "");
		}
		
		String getOrEmpty(String value) {
			return Optional.ofNullable(value).orElseGet(() -> "");
		}
		
		String getOrEmpty(Object... values) {
			return getOrEmpty(Arrays.asList(values));
		}
		
		String getOrEmpty(Collection<?> objects) {
			return getOrEmpty(objects, EMPTY_SPACE);
		}
		
		String getOrEmpty(Collection<?> objects, String separator) {
			String value = "";
			objects = Optional.ofNullable(objects).map(objs -> new ArrayList<>(objs)).orElseGet(ArrayList::new);
			objects.removeAll(Collections.singleton(null));
			objects.removeAll(Collections.singleton(""));
			Iterator<?> objectsItr = objects.iterator();
			while (objectsItr.hasNext()) {
				Object object = objectsItr.next();
				if (object instanceof Generator) {
					value += getOrEmpty((Generator)object);
				} else if (object instanceof String) {
					value += getOrEmpty((String)object);
				} else if (object instanceof Collection) {
					value += getOrEmpty((Collection<?>)object, separator);
				}
				if (objectsItr.hasNext() && !value.endsWith("\t") && !value.endsWith("\n")) {
					value += separator;
				}
			}
			return value;
		}		
	}
}
