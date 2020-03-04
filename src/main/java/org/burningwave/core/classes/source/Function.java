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
import java.util.Iterator;
import java.util.Optional;

public class Function extends Generator.Abst {
	private Collection<String> outerCode;
	private Integer modifier;
	private boolean defaultFunction;
	private TypeDeclaration typesDeclaration;
	private TypeDeclaration returnType;
	private String name;
	private Collection<Variable> parameters;
	private Statement body;
	
	private Function(String name) {
		this.name = name;
	}
	
	public static Function create(String name) {
		return new Function(name);
	}
	
	public static Function create() {
		return new Function(null);
	}
	
	Function setName(String name) {
		this.name = name;
		return this;
	}
	
	public Function addModifier(Integer modifier) {
		if (this.modifier == null) {
			this.modifier = modifier;
		} else {
			this.modifier |= modifier; 
		}
		return this;
	}
	
	public Function setDefault() {
		this.defaultFunction = true;
		return this;
	}
	
	public Function setTypeDeclaration(TypeDeclaration typesDeclaration) {
		this.typesDeclaration = typesDeclaration;
		return this;
	}
	
	public Function setReturnType(TypeDeclaration returnType) {
		this.returnType = returnType;
		return this;
	}
	
	public Function addParameter(Variable parameter) {
		this.parameters = Optional.ofNullable(this.parameters).orElseGet(ArrayList::new);
		this.parameters.add(parameter.setDelimiter(null));
		return this;
	}
	
	public Function addOuterCodeRow(String code) {
		this.outerCode = Optional.ofNullable(this.outerCode).orElseGet(ArrayList::new);
		if (!this.outerCode.isEmpty()) {
			this.outerCode.add("\n" + code);
		} else {
			this.outerCode.add(code);
		}
		return this;
	}
	
	public Function addBodyCode(String code) {
		this.body = Optional.ofNullable(this.body).orElseGet(Statement::create);
		this.body.addCode(code);
		return this;
	}
	
	public Function addBodyCodeRow(String code) {
		this.body = Optional.ofNullable(this.body).orElseGet(Statement::create);
		this.body.addCodeRow(code);
		return this;
	}
	
	public Function addBodyElement(Generator generator) {
		this.body = Optional.ofNullable(this.body).orElseGet(Statement::create);
		this.body.addElement(generator);
		return this;
	}

	private String getParametersCode() {
		String paramsCode = "(";
		if (parameters != null) {
			paramsCode += "\n";
			Iterator<Variable> paramsIterator =  parameters.iterator();
			while (paramsIterator.hasNext()) {
				Variable param = paramsIterator.next();
				paramsCode += "\t" + param.make().replace("\n", "\n\t");
				if (paramsIterator.hasNext()) {
					paramsCode += COMMA + "\n";
				} else {
					paramsCode += "\n";
				}
			}
		}
		return paramsCode + ")";
	}
	
	Collection<TypeDeclaration> getTypeDeclarations() {
		Collection<TypeDeclaration> types = new ArrayList<>();
		Optional.ofNullable(typesDeclaration).ifPresent(typesDeclaration -> {
			types.addAll(typesDeclaration.getTypeDeclarations());
		});
		Optional.ofNullable(returnType).ifPresent(returnType -> {
			types.addAll(returnType.getTypeDeclarations());
		});
		Optional.ofNullable(parameters).ifPresent(parameters -> {
			parameters.forEach(parameter -> {
				types.addAll(parameter.getTypeDeclarations());
			});
		});;
		return types;
	}
	
	@Override
	public String make() {
		return getOrEmpty(
			Optional.ofNullable(outerCode).map(outerCode ->
				getOrEmpty(outerCode) + "\n"
			).orElseGet(() -> null),
			Optional.ofNullable(modifier).map(mod -> Modifier.toString(this.modifier)).orElseGet(() -> null),
			defaultFunction ? "default" : null,
			typesDeclaration,
			returnType,
			name + getParametersCode(),
			body,
			Optional.ofNullable(modifier).map(mod -> Modifier.isAbstract(mod)? ";" : null).orElseGet(() -> null)
		);
	}
		
}
