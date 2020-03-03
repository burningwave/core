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

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

public class Class extends Generator.Abst {
	private Collection<String> outerCode;
	private Integer modifier;
	private String classType;
	private TypeDeclaration typeDeclaration;
	private String expands;
	private TypeDeclaration expandedType;
	private String concretize;
	private Collection<TypeDeclaration> concretizedTypes;
	private Collection<Variable> fields;
	private Collection<Function> constructors;
	private Collection<Function> methods;
	private Collection<Class> innerClasses;
	
	private Class(String classType, TypeDeclaration typeDeclaration) {
		this.classType = classType;
		this.typeDeclaration = typeDeclaration;
	}
	
	public static Class create(TypeDeclaration type) {
		return new Class("class", type);
	}
	
	public static Class createInterface(TypeDeclaration type) {
		return new Class("interface", type);
	}
	
	public Class addModifier(Integer modifier) {
		if (this.modifier == null) {
			this.modifier = modifier;
		} else {
			this.modifier |= modifier; 
		}
		return this;
	}
	
	public Class expands(java.lang.Class<?> extendedClass) {
		return expands(TypeDeclaration.create(extendedClass));
	}
	
	public Class expands(TypeDeclaration expandedType) {
		expands = "extends";
		this.expandedType = expandedType;
		return this;
	}
	
	public Class addConcretizedType(TypeDeclaration... concretizedTypes) {
		concretize = "implements";
		this.concretizedTypes = Optional.ofNullable(this.concretizedTypes).orElseGet(ArrayList::new);
		this.concretizedTypes.addAll(Arrays.asList(concretizedTypes));
		return this;		
	}
	
	public Class addOuterCodeRow(String code) {
		this.outerCode = Optional.ofNullable(this.outerCode).orElseGet(ArrayList::new);
		if (!this.outerCode.isEmpty()) {
			this.outerCode.add("\n" + code);
		} else {
			this.outerCode.add(code);
		}
		return this;
	}
	
	public Class addField(Variable field) {
		this.fields = Optional.ofNullable(this.fields).orElseGet(ArrayList::new);
		this.fields.add(field);
		return this;
	}
	
	public Class addConstructor(Function constructor) {
		this.constructors = Optional.ofNullable(this.constructors).orElseGet(ArrayList::new);
		this.constructors.add(constructor);
		constructor.setName(this.typeDeclaration.getSimpleName());
		constructor.setReturnType(null);
		return this;
	}
	
	public Class addMethod(Function method) {
		this.methods = Optional.ofNullable(this.methods).orElseGet(ArrayList::new);
		this.methods.add(method);
		return this;
	}
	
	public Class addInnerClass(Class cls) {
		this.innerClasses = Optional.ofNullable(this.innerClasses).orElseGet(ArrayList::new);
		this.innerClasses.add(cls);
		return this;
	}
	
	private String getFieldsCode() {
		return Optional.ofNullable(fields).map(flds -> "\t" + getOrEmpty(flds, "\n").replace("\n", "\n\t")).orElseGet(() -> null);
	}
	
	private String getFunctionCode(Collection<Function> functions) {
		return Optional.ofNullable(functions).map(mths -> "\t" + getOrEmpty(mths, "\n\n").replace("\n", "\n\t")).orElseGet(() -> null);
	}
	
	private String getInnerClassesCode() {
		String innerClassesAsString = null;
		if (innerClasses != null) {
			innerClassesAsString = "\t";
			for (Class cls : innerClasses) {
				innerClassesAsString += (cls.make()).replaceAll("\n(.)", "\n\t$1");
			}
		}
		return innerClassesAsString;
	}
	
	@Override
	public String make() {
		String fieldsCode = getFieldsCode();
		String constructorsCode = getFunctionCode(constructors);
		String methodsCode = getFunctionCode(methods);
		String innerClassesCode = getInnerClassesCode();
		return
			getOrEmpty(
				getOuterCode(),
				Optional.ofNullable(modifier).map(mod -> Modifier.toString(this.modifier)).orElseGet(() -> null),
				classType,
				typeDeclaration,
				expands,
				expandedType,
				concretize,
				concretizedTypes, 
				"{",
				fieldsCode != null? "\n\n" + fieldsCode : null,
				constructorsCode != null? "\n\n" + constructorsCode : null,
				methodsCode != null? "\n\n" + methodsCode : null,
				innerClassesCode != null? "\n\n" + innerClassesCode : null,
				"\n\n}"
			);
	}

	protected String getOuterCode() {
		return Optional.ofNullable(outerCode).map(outerCode ->
			getOrEmpty(outerCode) +"\n"
		).orElseGet(() -> null);
	}
	
	Collection<TypeDeclaration> getTypeDeclarations() {
		Collection<TypeDeclaration> types = typeDeclaration.getTypeDeclarations();
		Optional.ofNullable(expandedType).ifPresent(expandedType -> {
			types.addAll(expandedType.getTypeDeclarations());
		});
		Optional.ofNullable(concretizedTypes).ifPresent(concretizedTypes -> {
			types.addAll(concretizedTypes);
			for (TypeDeclaration type : concretizedTypes) {
				types.addAll(type.getTypeDeclarations());
			}
		});
		Optional.ofNullable(fields).ifPresent(fields -> {
			for (Variable field : fields) {
				types.addAll(field.getTypeDeclarations());
			}
		});
		Optional.ofNullable(constructors).ifPresent(constructors -> {
			for (Function constructor : constructors) {
				types.addAll(constructor.getTypeDeclarations());
			}
		});
		Optional.ofNullable(methods).ifPresent(methods -> {
			for (Function method : methods) {
				types.addAll(method.getTypeDeclarations());
			}
		});
		Optional.ofNullable(innerClasses).ifPresent(innerClasses -> {
			for (Class cls : innerClasses) {
				types.addAll(cls.getTypeDeclarations());
			}
		});	
		return types;
	}
}
