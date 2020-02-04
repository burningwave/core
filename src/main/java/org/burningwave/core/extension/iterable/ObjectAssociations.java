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
package org.burningwave.core.extension.iterable;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

import org.burningwave.core.Component;


public class ObjectAssociations<A, B, C> implements Component, Serializable {

	private static final long serialVersionUID = -8746196679726732982L;

	private java.util.Map<A, Map<B, C>> associations;

	public ObjectAssociations() {
		associations = new LinkedHashMap<A, Map<B, C>>();
	}

	public void clear() {
		associations.clear();
	}

	public C getRightAssociationFor(A a, B b) {
		java.util.Map<B, C> rightAssociations = getRightAssociationsFor(a);
		return rightAssociations.get(b);
	}

	public java.util.Map<B, C> getRightAssociationsFor(A a) {
		java.util.Map<B, C> rightAssociations = associations.get(a);
		if (rightAssociations == null) {
			synchronized (this) {
				rightAssociations = associations.get(a);
				if (rightAssociations == null) {
					rightAssociations = new LinkedHashMap<B, C>();
					associations.put(a, rightAssociations);
				}
			}
		}
		return rightAssociations;
	}

	public void addRightAssociation(A a, B b, C c) {
		java.util.Map<B, C> rightAssociations = getRightAssociationsFor(a);
		C associatedObj = rightAssociations.get(b);
		if (associatedObj == null) {
			synchronized (this) {
				associatedObj = rightAssociations.get(b);
				if (associatedObj == null) {
					rightAssociations.put(b, c);
				}
			}
		}
	}

}