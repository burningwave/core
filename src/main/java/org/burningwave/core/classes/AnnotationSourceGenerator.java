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

public class AnnotationSourceGenerator extends SourceGenerator.Abst {
	private TypeDeclarationSourceGenerator type;
	private String name;
	private BodySourceGenerator body;
	
	public AnnotationSourceGenerator(String simpleName) {
		this.name = simpleName;
	}


	public static AnnotationSourceGenerator create(String name) {
		return new AnnotationSourceGenerator(name);		
	}	

	public static AnnotationSourceGenerator create(Class<?> cls) {
		AnnotationSourceGenerator annotation = new AnnotationSourceGenerator(cls.getSimpleName());
		annotation.type = TypeDeclarationSourceGenerator.create(cls);
		return annotation;
	}
	
	Collection<TypeDeclarationSourceGenerator> getTypeDeclarations() {
		Collection<TypeDeclarationSourceGenerator> types = new ArrayList<>();
		Optional.ofNullable(body).ifPresent(hirearchyElements -> {
			types.addAll(body.getTypeDeclarations());
		});
		Optional.ofNullable(type).ifPresent(hirearchyElement -> {
			types.add(type);
		});
		return types;
	}
	
	public AnnotationSourceGenerator addParameters(VariableSourceGenerator... parameters) {
		return addParameters(null, parameters);
	}
	
	public AnnotationSourceGenerator addParameters(String name, VariableSourceGenerator... parameters) {
		this.body = Optional.ofNullable(this.body).orElseGet(() ->
			BodySourceGenerator.createSimple().setDelimiters("(", ")").setBodyElementSeparator(", ")
		);
		BodySourceGenerator innBody = BodySourceGenerator.createSimple().setBodyElementSeparator(", ");
		if (name != null) {
			innBody.setDelimiters(name + " = {", "}");
		} else {
			innBody.setDelimiters("{", "}");
		}
		for (VariableSourceGenerator parameter : parameters) {
			innBody.addElement(parameter.setDelimiter(null));
		}
		this.body.addElement(innBody);
		return this;
	}
	
	public AnnotationSourceGenerator addParameter(VariableSourceGenerator parameter) {
		this.body = Optional.ofNullable(this.body).orElseGet(() -> 
			BodySourceGenerator.createSimple().setDelimiters("(", ")").setBodyElementSeparator(", ")
		).addElement(parameter.setDelimiter(null));
		return this;
	}
	
	public AnnotationSourceGenerator addParameter(AnnotationSourceGenerator parameter) {
		this.body = Optional.ofNullable(this.body).orElseGet(() ->
			BodySourceGenerator.createSimple().setDelimiters("(", ")").setBodyElementSeparator(", ")
		);
		this.body.addElement(parameter);
		return this;
	}
	
	public AnnotationSourceGenerator addParameters(AnnotationSourceGenerator... parameters) {
		return addParameters(null, parameters);
	}
	
	public AnnotationSourceGenerator addParameters(String name, AnnotationSourceGenerator... parameters) {
		this.body = Optional.ofNullable(this.body).orElseGet(() ->
			BodySourceGenerator.createSimple().setDelimiters("(", ")").setBodyElementSeparator(", ")
		);
		BodySourceGenerator innBody = BodySourceGenerator.createSimple().setBodyElementSeparator(", ");
		if (name != null) {
			innBody.setDelimiters(name + " = {", "}");
		} else {
			innBody.setDelimiters("{", "}");
		}
		for (AnnotationSourceGenerator parameter : parameters) {
			innBody.addElement(parameter);
		}
		this.body.addElement(innBody);
		return this;
	}
	
	public AnnotationSourceGenerator useType(java.lang.Class<?>... classes) {
		this.body = Optional.ofNullable(this.body).orElseGet(() ->
			BodySourceGenerator.createSimple().setDelimiters("(", ")").setBodyElementSeparator(", ")
		);
		body.useType(classes);
		return this;	
	}
	
	@Override
	public String make() {
		return getOrEmpty("@" + name, body);
	}
}
