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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

public class TypeDeclarationSourceGenerator extends SourceGenerator.Abst {
	private String name;
	private String simpleName;
	private Collection<GenericSourceGenerator> generics;
	
	private TypeDeclarationSourceGenerator(String name, String simpleName) {
		this.name = name;
		this.simpleName = simpleName;
	}
	
	private TypeDeclarationSourceGenerator() {}
	
	static TypeDeclarationSourceGenerator create(String name, String simpleName) {
		return new TypeDeclarationSourceGenerator(name, simpleName);
	}
	
	public static TypeDeclarationSourceGenerator create(String simpleName) {
		return new TypeDeclarationSourceGenerator(null, simpleName);
	}
	
	public static TypeDeclarationSourceGenerator create(java.lang.Class<?> cls) {
		return new TypeDeclarationSourceGenerator(cls.isPrimitive()? null : cls.getName(), cls.getSimpleName());
	}
	
	public static TypeDeclarationSourceGenerator create(GenericSourceGenerator... generics) {
		return new TypeDeclarationSourceGenerator().addGeneric(generics);
	}
	
	public String getName() {
		return name;
	}

	public String getSimpleName() {
		return simpleName;
	}
	
	public TypeDeclarationSourceGenerator addGeneric(GenericSourceGenerator... generics) {
		this.generics = Optional.ofNullable(this.generics).orElseGet(ArrayList::new);
		this.generics.addAll(Arrays.asList(generics));
		return this;
	}
	
	Collection<TypeDeclarationSourceGenerator> getTypeDeclarations() {
		Collection<TypeDeclarationSourceGenerator> types = new ArrayList<>();
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