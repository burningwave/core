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
package org.burningwave.core.extension;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.function.Predicate;

public class Group<T> extends Item {
	protected Collection<T> elements = new LinkedHashSet<>();
	

	public Group<T> add(T element) {
		elements.add(element);
		if (element instanceof Item) {
			((Item)element).setParent(this);
		}
		return this;
	}
	
	public <I extends Item> I get(String name) {
		return get((item) -> (item instanceof Item && ((Item)item).getName().matches(name)));
		
	}
	
	@SuppressWarnings("unchecked")
	public <I> I get(Predicate<I> predicate) {
		return (I)elements.stream().filter(item -> 
			(predicate.test((I)item))
		).findFirst().orElse(null);
	}
	

	@Override
	public void close() {
		if (elements != null) {
			elements.clear();
			elements = null;
		}
		super.close();
	}
}