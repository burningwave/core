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
import java.util.Collection;
import java.util.Optional;

public class GenericSourceGenerator extends SourceGenerator.Abst {

	private static final long serialVersionUID = -7508101348682677540L;
	
	private Collection<String> outerCode;
	private Collection<AnnotationSourceGenerator> annotations;
	private TypeDeclarationSourceGenerator type;
	private String name;
	private String hirearchyOperator;
	private Collection<TypeDeclarationSourceGenerator> hirearchyElements;
	
	private GenericSourceGenerator(String name) {
		this.name = name;
	}
	
	public static GenericSourceGenerator create(String name) {
		return new GenericSourceGenerator(name);		
	}	

	public static GenericSourceGenerator create(Class<?> cls) {
		GenericSourceGenerator generic = new GenericSourceGenerator(cls.getSimpleName());
		generic.type = TypeDeclarationSourceGenerator.create(cls);
		return generic;
	}
	
	public String getName() {
		return name;
	}	
	
	public GenericSourceGenerator addOuterCode(String... codes) {
		Optional.ofNullable(this.outerCode).orElseGet(() -> this.outerCode = new ArrayList<>());
		for (String code : codes) {
			this.outerCode.add(code);
		}
		return this;
	}
	
	public GenericSourceGenerator addAnnotation(AnnotationSourceGenerator... annotations) {
		Optional.ofNullable(this.annotations).orElseGet(() -> this.annotations = new ArrayList<>());
		for (AnnotationSourceGenerator annotation : annotations) {
			this.annotations.add(annotation);
		}
		return this;
	}
	
	public GenericSourceGenerator expands(TypeDeclarationSourceGenerator... hirearchyElements) {
		hirearchyOperator = "extends";
		this.hirearchyElements = Optional.ofNullable(this.hirearchyElements).orElseGet(ArrayList::new);
		for (TypeDeclarationSourceGenerator hirearchyElement : hirearchyElements) {
			this.hirearchyElements.add(hirearchyElement);
		}
		return this;
	}
	
	public GenericSourceGenerator parentOf(TypeDeclarationSourceGenerator hirearchyElement) {
		hirearchyOperator = "super";
		this.hirearchyElements = Optional.ofNullable(this.hirearchyElements).orElseGet(ArrayList::new);
		this.hirearchyElements.add(hirearchyElement);
		return this;
	}
	
	Collection<TypeDeclarationSourceGenerator> getTypeDeclarations() {
		Collection<TypeDeclarationSourceGenerator> types = new ArrayList<>();
		Optional.ofNullable(annotations).ifPresent(annotations -> {
			for (AnnotationSourceGenerator annotation : annotations) {
				types.addAll(annotation.getTypeDeclarations());
			}
		});	
		Optional.ofNullable(hirearchyElements).ifPresent(hirearchyElements -> {
			hirearchyElements.forEach(hirearchyElement -> {
				types.addAll(hirearchyElement.getTypeDeclarations());
			});
		});
		Optional.ofNullable(type).ifPresent(hirearchyElement -> {
			types.add(type);
		});
		return types;
	}
	
	private String getAnnotations() {
		return Optional.ofNullable(annotations).map(annts -> getOrEmpty(annts)).orElseGet(() -> null);
	}
	
	@Override
	public String make() {
		return getOrEmpty(outerCode, getAnnotations(), name, hirearchyOperator, getOrEmpty(hirearchyElements, " & "));
	}

}
