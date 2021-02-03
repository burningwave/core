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

import static org.burningwave.core.assembler.StaticComponentContainer.Classes;

import java.util.Collection;

@SuppressWarnings("unchecked")
public class LoadOrBuildAndDefineConfig extends LoadOrBuildAndDefineConfigAbst<LoadOrBuildAndDefineConfig> {
	
	public LoadOrBuildAndDefineConfig(UnitSourceGenerator... unitsCode) {
		super(unitsCode);
	}

	public LoadOrBuildAndDefineConfig(Collection<UnitSourceGenerator>... unitsCodeCollections) {
		super(unitsCodeCollections);
	}

	@SafeVarargs
	public final static LoadOrBuildAndDefineConfig forUnitSourceGenerator(UnitSourceGenerator... unitsCode) {
		return new LoadOrBuildAndDefineConfig(unitsCode);
	}
	
	@SafeVarargs
	public final static LoadOrBuildAndDefineConfig forUnitSourceGenerator(Collection<UnitSourceGenerator>... unitsCode) {
		return new LoadOrBuildAndDefineConfig(unitsCode);
	}
	
	public static class ForCodeExecutorAbst<C extends ForCodeExecutorAbst<C>> extends LoadOrBuildAndDefineConfigAbst<C> {
		BodySourceGenerator body;
		
		ForCodeExecutorAbst(String executorName, BodySourceGenerator bodySG) {
			super(
				UnitSourceGenerator.create(Classes.retrievePackageName(executorName))
				.addClass(FunctionalInterfaceSourceGenerator.create().generateExecutor(executorName, bodySG))
			);
			this.body = bodySG;
			modifyCompilationConfig(compileConfig -> compileConfig.storeCompiledClasses(false));
		}
		
		public C setSimpleName(String simpleName) {
			UnitSourceGenerator uSG = unitSourceGenerators.iterator().next();
			ClassSourceGenerator cSG = uSG.getAllClasses().values().iterator().next();
			cSG.getTypeDeclaration().setSimpleName(simpleName);
			return (C)this;					
		}
		
		public C setName(String name) {
			UnitSourceGenerator uSG = unitSourceGenerators.iterator().next();
			uSG.setPackageName(Classes.retrievePackageName(name));
			ClassSourceGenerator cSG = uSG.getAllClasses().values().iterator().next();
			cSG.getTypeDeclaration().setSimpleName(Classes.retrieveSimpleName(name));
			return (C)this;					
		}
		
		String getExecutorName() {
			UnitSourceGenerator uSG = unitSourceGenerators.iterator().next();
			ClassSourceGenerator cSG = uSG.getAllClasses().values().iterator().next();
			return uSG.getPackageName() + "." + cSG.getTypeDeclaration().getSimpleName();
		}
		
		BodySourceGenerator getBody() {
			return body;
		}
		
	}
	
}