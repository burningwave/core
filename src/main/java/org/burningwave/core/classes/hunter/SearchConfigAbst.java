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
package org.burningwave.core.classes.hunter;

import java.lang.reflect.Constructor;

import org.burningwave.Throwables;
import org.burningwave.core.Component;
import org.burningwave.core.classes.ClassCriteria;
import org.burningwave.core.classes.ClassHelper;
import org.burningwave.core.classes.Classes;
import org.burningwave.core.classes.MemberFinder;
import org.burningwave.core.function.ThrowingSupplier;

@SuppressWarnings("unchecked")
abstract class SearchConfigAbst<S extends SearchConfigAbst<S>> implements Component {
	ClassCriteria classCriteria;
	ClassLoader parentClassLoaderForMainClassLoader;
	boolean useSharedClassLoaderAsMain;
	boolean deleteFoundItemsOnClose;
	boolean useSharedClassLoaderAsParent;
	boolean considerURLClassLoaderPathsAsScanned;
	boolean waitForSearchEnding;
	

	SearchConfigAbst() {
		useSharedClassLoaderAsMain(true);
		deleteFoundItemsOnClose = true;
		waitForSearchEnding = true;
		classCriteria = ClassCriteria.create();
	}
	
	void init(ClassHelper classHelper, PathMemoryClassLoader classSupplier, MemberFinder memberFinder) {
		classCriteria.init(classHelper, classSupplier, memberFinder);
	}
	
	public S by(ClassCriteria classCriteria) {
		this.classCriteria = classCriteria;
		return (S)this;
	}
	
	protected ClassCriteria getClassCriteria() {
		return classCriteria;
	}
	
	public S deleteFoundItemsOnClose(boolean flag) {
		this.deleteFoundItemsOnClose = flag;
		return (S)this;
	}	

	public S useSharedClassLoaderAsMain(boolean value) {
		useSharedClassLoaderAsMain = value;
		useSharedClassLoaderAsParent = !useSharedClassLoaderAsMain;
		parentClassLoaderForMainClassLoader = null;
		return (S)this;
	}
	
	public S useAsParentClassLoader(ClassLoader classLoader) {
		if (classLoader == null)  {
			throw Throwables.toRuntimeException("Parent class loader could not be null");
		}
		useSharedClassLoaderAsMain = false;
		useSharedClassLoaderAsParent = false;
		parentClassLoaderForMainClassLoader = classLoader;
		return (S)this;
	}
	
	public S useSharedClassLoaderAsParent(boolean value) {
		useSharedClassLoaderAsParent = value;
		useSharedClassLoaderAsMain = !useSharedClassLoaderAsParent;		
		parentClassLoaderForMainClassLoader = null;
		return (S)this;
	}
	
	public S isolateClassLoader() {
		useSharedClassLoaderAsParent = false;
		useSharedClassLoaderAsMain = false;		
		parentClassLoaderForMainClassLoader = null;
		return (S)this;
	}
	
	public S waitForSearchEnding(boolean waitForSearchEnding) {
		this.waitForSearchEnding = waitForSearchEnding;
		return (S)this;
	}

	public S considerURLClassLoaderPathsAsScanned(
		boolean value
	) {
		this.considerURLClassLoaderPathsAsScanned = value;
		return (S)this;
	}
	
	protected S newInstance() {
		return ThrowingSupplier.get(() -> {
			Constructor<?> constructor = getClass().getDeclaredConstructor();
			Classes.setAccessible(constructor, true);
			return (S)constructor.newInstance();
		});
	}
	
	public S createCopy() {
		S copy = newInstance();
		copy.classCriteria = this.classCriteria.createCopy();
		copy.useSharedClassLoaderAsMain = this.useSharedClassLoaderAsMain;
		copy.parentClassLoaderForMainClassLoader = this.parentClassLoaderForMainClassLoader;
		copy.useSharedClassLoaderAsParent = this.useSharedClassLoaderAsParent;
		copy.deleteFoundItemsOnClose = this.deleteFoundItemsOnClose;
		copy.considerURLClassLoaderPathsAsScanned = this.considerURLClassLoaderPathsAsScanned;
		copy.waitForSearchEnding = this.waitForSearchEnding;
		return copy;
	}
}
