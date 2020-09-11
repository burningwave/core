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
	private Collection<TypeDeclarationSourceGenerator> usedTypes;
	private String startingDelimiter;
	private String endingDelimiter;
	private String elementPrefix;
	private String elementSeparator;
	private Collection<SourceGenerator> bodyGenerators;
		
	BodySourceGenerator() {}
	
	public static BodySourceGenerator create() {
		return new BodySourceGenerator().setDelimiters("{", "\n}").setElementPrefix("\t");
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
	
	public BodySourceGenerator addElement(SourceGenerator... generators) {
		this.bodyGenerators = Optional.ofNullable(this.bodyGenerators).orElseGet(ArrayList::new);
		for (SourceGenerator generator : generators) {
			this.bodyGenerators.add(generator);
		}
		return this;		
	}
	
	public BodySourceGenerator addCode(String... elements) {
		this.bodyGenerators = Optional.ofNullable(this.bodyGenerators).orElseGet(ArrayList::new);
		for (String element : elements) {
			this.bodyGenerators.add(new SourceGenerator() {
				@Override
				public String make() {
					return element;
				}
			});
		}
		return this;
	}

	public BodySourceGenerator addCodeLine(String... codes) {
		for (String code : codes) {
			addCode("\n" + Optional.ofNullable(elementPrefix).orElseGet(() -> "") + code);	
		}
		return this;	
	}
	
	public BodySourceGenerator addAllElements(Collection<? extends SourceGenerator> generators) {
		this.bodyGenerators = Optional.ofNullable(this.bodyGenerators).orElseGet(ArrayList::new);
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
				if (generator instanceof BodySourceGenerator) {
					types.addAll(((BodySourceGenerator)generator).getTypeDeclarations());
				}
				if (generator instanceof VariableSourceGenerator) {
					types.addAll(((VariableSourceGenerator)generator).getTypeDeclarations());
				}
				if (generator instanceof AnnotationSourceGenerator) {
					types.addAll(((AnnotationSourceGenerator)generator).getTypeDeclarations());
				}
			}
		});
		return types;
	}
	
	public BodySourceGenerator useType(java.lang.Class<?>... classes) {
		this.usedTypes = Optional.ofNullable(this.usedTypes).orElseGet(ArrayList::new);
		for (java.lang.Class<?> cls : classes) {			
			this.usedTypes.add(TypeDeclarationSourceGenerator.create(cls));
		}
		return this;		
	}
	
	public BodySourceGenerator useType(String... classes) {
		this.usedTypes = Optional.ofNullable(this.usedTypes).orElseGet(ArrayList::new);
		for (String cls : classes) {			
			this.usedTypes.add(TypeDeclarationSourceGenerator.create(cls, null));
		}
		return this;		
	}
	
	@Override
	public String make() {
		return getOrEmpty(startingDelimiter, getOrEmpty((Collection<?>)bodyGenerators, (String)Optional.ofNullable(elementSeparator).orElse(EMPTY_SPACE)), endingDelimiter);
	}
	
}
