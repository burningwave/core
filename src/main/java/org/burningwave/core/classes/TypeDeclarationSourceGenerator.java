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

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

public class TypeDeclarationSourceGenerator extends SourceGenerator.Abst {
	
	private boolean isVarArgs;
	private boolean useFullyQualifiedName;
	private Boolean publicFlag;
	private String name;
	private String simpleName;
	private Collection<GenericSourceGenerator> generics;
	private BodySourceGenerator parameters;
	
	private TypeDeclarationSourceGenerator(String name, String simpleName) {
		this.name = name;
		this.simpleName = simpleName;
	}
	
	private TypeDeclarationSourceGenerator(Class<?> clazz) {
		if (!clazz.isPrimitive()) {
			this.name = clazz.getName();
		}
		publicFlag = Modifier.isPublic(clazz.getModifiers());
		this.simpleName = clazz.getSimpleName();
	}
	
	private TypeDeclarationSourceGenerator() {}
	
	public static TypeDeclarationSourceGenerator create(String name, String simpleName) {
		return new TypeDeclarationSourceGenerator(name, simpleName);
	}
	
	public static TypeDeclarationSourceGenerator create(String simpleName) {
		return new TypeDeclarationSourceGenerator(null, simpleName);
	}
	
	public static TypeDeclarationSourceGenerator create(java.lang.Class<?> cls) {
		return new TypeDeclarationSourceGenerator(cls);
	}
	
	public static TypeDeclarationSourceGenerator create(GenericSourceGenerator... generics) {
		return new TypeDeclarationSourceGenerator().addGeneric(generics);
	}
	
	public TypeDeclarationSourceGenerator setAsParameterizable(boolean flag) {
		if (flag) {
			if (parameters == null) {
				parameters = BodySourceGenerator.create().setDelimiters("(", ")").setElementPrefix("");
			}
		} else {
			parameters = null;
		}
		return this;
	}
	
	public TypeDeclarationSourceGenerator setAsVarArgs(boolean flag) {
		this.isVarArgs = flag;
		return this;
	}
	
	boolean isParameterizable() {
		return parameters != null;
	}
	
	public TypeDeclarationSourceGenerator addParameter(String... parameters) {
		if (this.parameters == null) {
			setAsParameterizable(true);
		}
		if (!this.parameters.make().equals("( )")) {
			this.parameters.addCode(",");
		}
		this.parameters.addCode(String.join(", ", parameters));		
		return this;
	}
	
	public TypeDeclarationSourceGenerator addParameter(SourceGenerator ... parameters) {
		if (this.parameters == null) {
			setAsParameterizable(true);
		}
		if (!this.parameters.make().equals("( )")) {
			this.parameters.addCode(",");
		}
		for (int i = 0; i < parameters.length - 1; i++) {
			this.parameters.addElement(parameters[i]);
			this.parameters.addCode(",");
		}
		this.parameters.addElement(parameters[parameters.length - 1]);	
		return this;
	}
	
	public TypeDeclarationSourceGenerator setSimpleName(String simpleName) {
		this.simpleName = simpleName;
		return this;
	}
	
	public TypeDeclarationSourceGenerator useFullyQualifiedName(boolean flag) {
		this.useFullyQualifiedName = flag;
		return this;
	}
	
	String getName() {
		return name;
	}

	String getSimpleName() {
		return simpleName;
	}
	
	public TypeDeclarationSourceGenerator addGeneric(GenericSourceGenerator... generics) {
		Optional.ofNullable(this.generics).orElseGet(() -> this.generics = new ArrayList<>());
		this.generics.addAll(Arrays.asList(generics));
		return this;
	}
	
	Collection<TypeDeclarationSourceGenerator> getTypeDeclarations() {
		Collection<TypeDeclarationSourceGenerator> types = new ArrayList<>();
		if (!(useFullyQualifiedName && name != null)) {
			types.add(this);
		}
		Optional.ofNullable(generics).ifPresent(generics -> {
			generics.forEach(generic -> {
				types.addAll(generic.getTypesDeclarations());
			});
		});
		Optional.ofNullable(this.parameters).ifPresent(parameters -> {
			types.addAll(parameters.getTypeDeclarations());
		});
		return types;
	}
	
	Boolean isPublic() {
		return publicFlag;
	}
	
	@Override
	public String make() {
		return 
			(useFullyQualifiedName && name != null ?
				getOrEmpty(name) :
				getOrEmpty(simpleName)
			)  + 
			Optional.ofNullable(generics).map(generics -> 
				"<" + getOrEmpty(generics, COMMA + EMPTY_SPACE) + ">"
			).orElseGet(() -> "") +
			Optional.ofNullable(parameters).map(BodySourceGenerator::make).orElseGet(() -> "") +
			(isVarArgs ? "..." : "");
	}	
}