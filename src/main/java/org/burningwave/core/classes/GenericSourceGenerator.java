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
	private Collection<String> outerCode;
	private String name;
	private String hirearchyOperator;
	private TypeDeclarationSourceGenerator hirearchyElement;
	
	private GenericSourceGenerator(String name) {
		this.name = name;
	}
	
	public static GenericSourceGenerator create(String name) {
		return new GenericSourceGenerator(name);		
	}
	
	public String getName() {
		return name;
	}	
	
	public GenericSourceGenerator addOuterCode(String code) {
		this.outerCode = Optional.ofNullable(this.outerCode).orElseGet(ArrayList::new);
		this.outerCode.add(code);
		return this;
	}
	
	public GenericSourceGenerator expands(TypeDeclarationSourceGenerator hirearchyElement) {
		hirearchyOperator = "extends";
		this.hirearchyElement = hirearchyElement;
		return this;
	}
	
	public GenericSourceGenerator parentOf(TypeDeclarationSourceGenerator hirearchyElement) {
		hirearchyOperator = "super";
		this.hirearchyElement = hirearchyElement;
		return this;
	}
	
	Collection<TypeDeclarationSourceGenerator> getTypesDeclarations() {
		Collection<TypeDeclarationSourceGenerator> types = new ArrayList<>();
		Optional.ofNullable(hirearchyElement).ifPresent(hirearchyElement -> {
			types.addAll(hirearchyElement.getTypeDeclarations());
		});
		return types;
	}
	
	@Override
	public String make() {
		return getOrEmpty(outerCode, name, hirearchyOperator, hirearchyElement);
	}

}
