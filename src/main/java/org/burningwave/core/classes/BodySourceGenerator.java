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

public class BodySourceGenerator extends SourceGenerator.Abst {

	private static final long serialVersionUID = 5923611245424078711L;
	
	private Collection<TypeDeclarationSourceGenerator> usedTypes;
	private String startingDelimiter;
	private String endingDelimiter;
	private String elementPrefix;
	private String elementSeparator;
	private Collection<SourceGenerator> bodyGenerators;
		
	BodySourceGenerator() {}
	
	public static BodySourceGenerator create() {
		return new BodySourceGenerator().setDelimiters("{\n", "\n}").setElementPrefix("\t");
	}
	
	public static BodySourceGenerator createSimple() {
		return new BodySourceGenerator().setDelimiters(null, null).setElementPrefix(null); 
	}
	
	public BodySourceGenerator setBodyElementSeparator(String elementSeparator) {
		this.elementSeparator = elementSeparator;
		return this;
	}
	
	public BodySourceGenerator setElementPrefix(String elementPrefix) {
		this.elementPrefix = elementPrefix;
		return this;
	}
	
	public BodySourceGenerator setDelimiters(String startingDelimiter, String endingDelimiter) {
		this.startingDelimiter = startingDelimiter;
		this.endingDelimiter = endingDelimiter;
		return this;
	}
	
	public BodySourceGenerator setStartingDelimiter(String startingDelimiter) {
		this.startingDelimiter = startingDelimiter;
		return this;
	}

	public BodySourceGenerator setEndingDelimiter(String endingDelimiter) {
		this.endingDelimiter = endingDelimiter;
		return this;
	}
	
	public BodySourceGenerator addElement(SourceGenerator... generators) {
		Optional.ofNullable(this.bodyGenerators).orElseGet(() -> this.bodyGenerators = new ArrayList<>());
		for (SourceGenerator generator : generators) {
			this.bodyGenerators.add(generator);
		}
		return this;		
	}
	
	public BodySourceGenerator addCode(String... elements) {
		Optional.ofNullable(this.bodyGenerators).orElseGet(() -> this.bodyGenerators = new ArrayList<>());
		for (String element : elements) {
			this.bodyGenerators.add(new SourceGenerator() {

				private static final long serialVersionUID = 5843006583153055991L;

				@Override
				public String make() {
					return element;
				}
			});
		}
		return this;
	}
	
	boolean isEmpty() {
		return bodyGenerators == null || bodyGenerators.isEmpty();
	}
	
	public BodySourceGenerator addCodeLine(String... codes) {
		if (codes.length > 0) {
			for (String code : codes) {
				addCode((bodyGenerators != null && !bodyGenerators.isEmpty()? "\n" : "") + code);	
			}
		} else {
			addCode((bodyGenerators != null && !bodyGenerators.isEmpty()? "\n" : ""));
		}
		return this;	
	}
	
	public BodySourceGenerator addAllElements(Collection<? extends SourceGenerator> generators) {
		Optional.ofNullable(this.bodyGenerators).orElseGet(() -> this.bodyGenerators = new ArrayList<>());
		generators.forEach(generator -> {
			addElement(generator);
		});
		return this;		
	}
	
	Collection<TypeDeclarationSourceGenerator> getTypeDeclarations() {
		Collection<TypeDeclarationSourceGenerator> types = new ArrayList<>();
		Optional.ofNullable(usedTypes).ifPresent(usedTypes -> types.addAll(usedTypes));
		Optional.ofNullable(bodyGenerators).ifPresent(bodyGenerators -> {
			for (SourceGenerator generator : bodyGenerators) {
				if (generator instanceof AnnotationSourceGenerator) {
					types.addAll(((AnnotationSourceGenerator)generator).getTypeDeclarations());
				}
				if (generator instanceof BodySourceGenerator) {
					types.addAll(((BodySourceGenerator)generator).getTypeDeclarations());
				}
				if (generator instanceof ClassSourceGenerator) {
					types.addAll(((ClassSourceGenerator)generator).getTypeDeclarations());
				}
				if (generator instanceof FunctionSourceGenerator) {
					types.addAll(((FunctionSourceGenerator)generator).getTypeDeclarations());
				}
				if (generator instanceof GenericSourceGenerator) {
					types.addAll(((GenericSourceGenerator)generator).getTypeDeclarations());
				}
				if (generator instanceof TypeDeclarationSourceGenerator) {
					types.addAll(((TypeDeclarationSourceGenerator)generator).getTypeDeclarations());
				}
				if (generator instanceof VariableSourceGenerator) {
					types.addAll(((VariableSourceGenerator)generator).getTypeDeclarations());
				}

			}
		});
		return types;
	}
	
	public BodySourceGenerator useType(java.lang.Class<?>... classes) {
		Optional.ofNullable(this.usedTypes).orElseGet(() -> this.usedTypes = new ArrayList<>());
		for (java.lang.Class<?> cls : classes) {			
			this.usedTypes.add(TypeDeclarationSourceGenerator.create(cls));
		}
		return this;		
	}
	
	String getStartingDelimiter() {
		return startingDelimiter;
	}
	
	String getEndingDelimiter() {
		return endingDelimiter;
	}
	
	String getBodyCode() {
		String elementPrefix = !isEmpty()? this.elementPrefix : null;
		String bodyCode =
			Optional.ofNullable(elementPrefix).orElseGet(() -> "") +
			getOrEmpty(bodyGenerators, Optional.ofNullable(elementSeparator).orElse(EMPTY_SPACE))
			.replaceAll("\n(.)", "\n" + Optional.ofNullable(elementPrefix).orElseGet(() -> "") + "$1");
		return bodyCode;
	}
	
	
	
	public BodySourceGenerator useType(String... classes) {
		Optional.ofNullable(this.usedTypes).orElseGet(() -> this.usedTypes = new ArrayList<>());
		for (String cls : classes) {			
			this.usedTypes.add(TypeDeclarationSourceGenerator.create(cls, null));
		}
		return this;		
	}
	
	@Override
	public String make() {
		return getOrEmpty(startingDelimiter, getBodyCode(), endingDelimiter);
	}
	
}