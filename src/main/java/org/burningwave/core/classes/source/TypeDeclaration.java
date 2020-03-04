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
import java.util.Optional;

public class TypeDeclaration extends Generator.Abst {
	private String name;
	private String simpleName;
	private Collection<Generic> generics;
	
	private TypeDeclaration(String name, String simpleName) {
		this.name = name;
		this.simpleName = simpleName;
	}
	
	private TypeDeclaration() {}
	
	static TypeDeclaration create(String name, String simpleName) {
		return new TypeDeclaration(name, simpleName);
	}
	
	public static TypeDeclaration create(String simpleName) {
		return new TypeDeclaration(null, simpleName);
	}
	
	public static TypeDeclaration create(java.lang.Class<?> cls) {
		return new TypeDeclaration(cls.isPrimitive()? null : cls.getName(), cls.getSimpleName());
	}
	
	public static TypeDeclaration create(Generic... generics) {
		return new TypeDeclaration().addGeneric(generics);
	}
	
	public String getName() {
		return name;
	}

	public String getSimpleName() {
		return simpleName;
	}
	
	public TypeDeclaration addGeneric(Generic... generics) {
		this.generics = Optional.ofNullable(this.generics).orElseGet(ArrayList::new);
		this.generics.addAll(Arrays.asList(generics));
		return this;
	}
	
	Collection<TypeDeclaration> getTypeDeclarations() {
		Collection<TypeDeclaration> types = new ArrayList<>();
		types.add(this);
		Optional.ofNullable(generics).ifPresent(generics -> {
			generics.forEach(generic -> {
				types.addAll(generic.getTypesDeclarations());
			});
		});
		return types;
	}
	
	@Override
	public String make() {
		return getOrEmpty(simpleName)  + Optional.ofNullable(generics).map(generics -> 
		"<" + getOrEmpty(generics, COMMA + EMPTY_SPACE) + ">").orElseGet(() -> "");
	}	
}