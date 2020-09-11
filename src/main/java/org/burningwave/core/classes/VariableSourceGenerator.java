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
import java.util.Collection;
import java.util.Optional;

public class VariableSourceGenerator extends SourceGenerator.Abst {
	private Collection<String> outerCode;
	private Collection<AnnotationSourceGenerator> annotations;
	private String assignmentOperator;
	private String delimiter;
	private Integer modifier;
	private TypeDeclarationSourceGenerator type;
	private String name;
	private BodySourceGenerator valueBody;
	private Collection<TypeDeclarationSourceGenerator> usedTypes;
	
	private VariableSourceGenerator(TypeDeclarationSourceGenerator type, String name) {
		this.type = type;
		this.name = name;
		this.assignmentOperator = "= ";
		this.delimiter = ";";
	}
	
	public static VariableSourceGenerator create(java.lang.Class<?> type, String name) {
		return new VariableSourceGenerator(TypeDeclarationSourceGenerator.create(type), name);
	}
	
	public static VariableSourceGenerator create(TypeDeclarationSourceGenerator type, String name) {
		return new VariableSourceGenerator(type, name);
	}
	
	public static VariableSourceGenerator create(String name) {
		return new VariableSourceGenerator(null, name);
	}
	
	public VariableSourceGenerator addModifier(Integer modifier) {
		if (this.modifier == null) {
			this.modifier = modifier;
		} else {
			this.modifier |= modifier; 
		}
		return this;
	}
	
	public VariableSourceGenerator addOuterCode(String code) {
		this.outerCode = Optional.ofNullable(this.outerCode).orElseGet(ArrayList::new);
		this.outerCode.add(code);
		return this;
	}
	
	public VariableSourceGenerator addOuterCodeLine(String code) {
		this.outerCode = Optional.ofNullable(this.outerCode).orElseGet(ArrayList::new);
		if (!this.outerCode.isEmpty()) {
			this.outerCode.add("\n" + code);
		} else {
			this.outerCode.add(code);
		}
		return this;
	}
	
	public VariableSourceGenerator addAnnotation(AnnotationSourceGenerator annotation) {
		this.annotations = Optional.ofNullable(this.annotations).orElseGet(ArrayList::new);
		this.annotations.add(annotation);
		return this;
	}
	
	Collection<TypeDeclarationSourceGenerator> getTypeDeclarations() {
		Collection<TypeDeclarationSourceGenerator> types = new ArrayList<>();
		Optional.ofNullable(annotations).ifPresent(annotations -> {
			for (AnnotationSourceGenerator annotation : annotations) {
				types.addAll(annotation.getTypeDeclarations());
			}
		});	
		Optional.ofNullable(type).ifPresent(type -> {
			types.addAll(type.getTypeDeclarations());
		});
		Optional.ofNullable(valueBody).ifPresent(valueBody -> {
			types.addAll(valueBody.getTypeDeclarations());
		});
		Optional.ofNullable(usedTypes).ifPresent(usedTypes ->
			types.addAll(usedTypes)
		);
		return types;
	}
	
	public VariableSourceGenerator setValue(String value) {
		return setValue(BodySourceGenerator.createSimple().addCode(value));
	}
	
	public VariableSourceGenerator setValue(BodySourceGenerator valueGenerator) {
		this.valueBody = valueGenerator;
		return this;
	}
	
	VariableSourceGenerator setAssignementOperator(String operator) {
		this.assignmentOperator = operator;
		return this;
	}
	
	VariableSourceGenerator setDelimiter(String separator) {
		this.delimiter = separator;
		return this;
	}
	
	public VariableSourceGenerator useType(java.lang.Class<?>... classes) {
		this.usedTypes = Optional.ofNullable(this.usedTypes).orElseGet(ArrayList::new);
		for (java.lang.Class<?> cls : classes) {			
			this.usedTypes.add(TypeDeclarationSourceGenerator.create(cls));
		}
		return this;		
	}
	
	private String getAnnotations() {
		return Optional.ofNullable(annotations).map(annts -> getOrEmpty(annts, "\n") +"\n").orElseGet(() -> null);
	}
	
	@Override
	public String make() {
		return getOrEmpty(
			Optional.ofNullable(outerCode).map(oc -> 
				getOrEmpty(outerCode) + "\n"
			).orElseGet(() -> null),
			getAnnotations(),
			Optional.ofNullable(modifier).map(mod -> Modifier.toString(this.modifier)).orElseGet(() -> null),
			type,
			name,
			Optional.ofNullable(valueBody).map(value -> assignmentOperator + value).orElseGet(() -> null)
		) + Optional.ofNullable(delimiter).orElseGet(() -> "");
	}
}
