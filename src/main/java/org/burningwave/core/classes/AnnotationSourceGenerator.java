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

	private static final long serialVersionUID = -6466348844734237149L;
	
	
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
	
	public AnnotationSourceGenerator addParameter(VariableSourceGenerator... parameters) {
		return addParameter(null, parameters);
	}
	
	public AnnotationSourceGenerator addParameter(String name, VariableSourceGenerator... parameters) {
		return addParameter(name, false, parameters);
	}
	
	public AnnotationSourceGenerator addParameter(String name, boolean isArray, VariableSourceGenerator... parameters) {
		Optional.ofNullable(this.body).orElseGet(() ->
			this.body =BodySourceGenerator.createSimple().setDelimiters("(\n", "\n)").setBodyElementSeparator(",\n").setElementPrefix("\t")
		);
		BodySourceGenerator innBody = BodySourceGenerator.createSimple().setBodyElementSeparator(",\n").setElementPrefix("\t");
		if (name != null) {
			if (isArray) {
				innBody.setDelimiters(name + " = {\n", "\n}");
			} else {
				innBody.setDelimiters(name + " = ", null);
			}
		} else if (isArray) {
			innBody.setDelimiters("{", "}");
		} else {
			innBody.setElementPrefix(null);
		}
		for (VariableSourceGenerator parameter : parameters) {
			innBody.addElement(parameter.setDelimiter(null));
		}
		this.body.addElement(innBody);
		return this;
	}
	
	public AnnotationSourceGenerator addParameter(AnnotationSourceGenerator... parameters) {
		return addParameter(null, false, parameters);
	}
	
	public AnnotationSourceGenerator addParameter(boolean isArray, AnnotationSourceGenerator... parameters) {
		return addParameter(null, isArray, parameters);
	}
	
	public AnnotationSourceGenerator addParameter(String name, AnnotationSourceGenerator... parameters) {
		return addParameter(name, false, parameters);
	}
	
	public AnnotationSourceGenerator addParameter(String name, boolean isArray, AnnotationSourceGenerator... parameters) {
		Optional.ofNullable(this.body).orElseGet(() ->
			this.body =BodySourceGenerator.createSimple().setDelimiters("(\n", "\n)").setBodyElementSeparator(",\n").setElementPrefix("\t")
		);
		BodySourceGenerator innBody = BodySourceGenerator.createSimple().setBodyElementSeparator(",\n").setElementPrefix("\t");
		if (name != null) {
			if (isArray) {
				innBody.setDelimiters(name + " = {\n", "\n}");
			} else {
				innBody.setDelimiters(name + " = ", null);
			}
		} else if (isArray) {
			innBody.setDelimiters("{", "}");
		} else {
			innBody.setElementPrefix(null);
		}
		for (AnnotationSourceGenerator parameter : parameters) {
			innBody.addElement(parameter);
		}
		this.body.addElement(innBody);
		return this;
	}
	
	public AnnotationSourceGenerator useType(java.lang.Class<?>... classes) {
		Optional.ofNullable(this.body).orElseGet(() ->
			this.body =BodySourceGenerator.createSimple().setDelimiters("(\n", "\n)").setBodyElementSeparator(",\n").setElementPrefix("\t")
		);
		body.useType(classes);
		return this;	
	}
	
	@Override
	public String make() {
		return getOrEmpty("@" + name, body);
	}
}
