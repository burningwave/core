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
import java.util.Collection;
import java.util.Optional;

public class Variable extends Generator.Abst {
	private Collection<String> outerCode;
	private String assignmentOperator;
	private String delimiter;
	private Integer modifier;
	private TypeDeclaration type;
	private String name;
	private Statement valueBody;
	
	private Variable(TypeDeclaration type, String name) {
		this.type = type;
		this.name = name;
		this.assignmentOperator = "= ";
		this.delimiter = ";";
	}
	
	public static Variable create(TypeDeclaration type, String name) {
		return new Variable(type, name);
	}
	
	public Variable addModifier(Integer modifier) {
		if (this.modifier == null) {
			this.modifier = modifier;
		} else {
			this.modifier |= modifier; 
		}
		return this;
	}
	
	public Variable addOuterCode(String code) {
		this.outerCode = Optional.ofNullable(this.outerCode).orElseGet(ArrayList::new);
		this.outerCode.add(code);
		return this;
	}
	
	public Variable addOuterCodeRow(String code) {
		this.outerCode = Optional.ofNullable(this.outerCode).orElseGet(ArrayList::new);
		if (!this.outerCode.isEmpty()) {
			this.outerCode.add("\n" + code);
		} else {
			this.outerCode.add(code);
		}
		return this;
	}
	
	Collection<TypeDeclaration> getTypeDeclarations() {
		Collection<TypeDeclaration> types = new ArrayList<>();
		Optional.ofNullable(type).ifPresent(type -> {
			types.addAll(type.getTypeDeclarations());
		});
		Optional.ofNullable(valueBody).ifPresent(valueBody -> {
			types.addAll(valueBody.getTypeDeclarations());
		});
		return types;
	}
	
	public Variable setValue(String value) {
		return setValue(Statement.createSimple().addCode(value));
	}
	
	public Variable setValue(Statement valueGenerator) {
		this.valueBody = valueGenerator;
		return this;
	}
	
	Variable setAssignementOperator(String operator) {
		this.assignmentOperator = operator;
		return this;
	}
	
	Variable setDelimiter(String separator) {
		this.delimiter = separator;
		return this;
	}
	
	@Override
	public String make() {
		return getOrEmpty(
			Optional.ofNullable(outerCode).map(oc -> 
				getOrEmpty(outerCode) + "\n"
			).orElseGet(() -> null),
			Optional.ofNullable(modifier).map(mod -> Modifier.toString(this.modifier)).orElseGet(() -> null),
			type,
			name,
			Optional.ofNullable(valueBody).map(value -> assignmentOperator + value).orElseGet(() -> null)
		) + Optional.ofNullable(delimiter).orElseGet(() -> "");
	}
}
