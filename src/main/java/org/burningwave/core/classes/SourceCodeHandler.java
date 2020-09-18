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
import static org.burningwave.core.assembler.StaticComponentContainer.Strings;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.burningwave.core.Component;
import org.burningwave.core.Executable;
import org.burningwave.core.function.MultiParamsConsumer;
import org.burningwave.core.function.MultiParamsFunction;
import org.burningwave.core.function.MultiParamsPredicate;


public class SourceCodeHandler implements Component {
	
	private SourceCodeHandler() {}
	
	public static SourceCodeHandler create() {
		return new SourceCodeHandler();
	}
	
	public String extractClassName(String classCode) {
		return
			Optional.ofNullable(
				Strings.extractAllGroups(
					Pattern.compile("(package)\\s*([[a-zA-Z0-9\\s]*\\.?]*)"), classCode
				).get(2).get(0)
			).map(
				value -> value + "."
			).orElse("") +
			Strings.extractAllGroups(
				Pattern.compile("(?<=\\n|\\A)(?:public\\s*)?(class|interface|enum)\\s*([^\\n\\s<]*)"), classCode
			).get(2).get(0);
	}
	
	public Collection<String> extractImports(String classCode) {
		Collection<String> imports = Strings.extractAllGroups(
			Pattern.compile("import\\s+(.*?)\\s*;"), classCode
		).get(1);
		Collection<String> finalImports = new HashSet<>();
		for (String className : imports) {
			if (className.startsWith("static")) {
				className = className.replaceAll("static\\s+", "");
				className = className.substring(0, className.lastIndexOf("."));
			}
			finalImports.add(className);
		}
		
		return finalImports;
	}

	public UnitSourceGenerator generateExecutor(String className, BodySourceGenerator body) {
		if (className.contains("$")) {
			throw Throwables.toRuntimeException(className + " CodeExecutor could not be a inner class");
		}
		String packageName = Classes.retrievePackageName(className);
		String classSimpleName = Classes.retrieveSimpleName(className);
		
		FunctionSourceGenerator executeMethod = FunctionSourceGenerator.create("execute").setReturnType(
			Object.class
		).addModifier(
			Modifier.PUBLIC
		).addParameter(
			VariableSourceGenerator.create(
				TypeDeclarationSourceGenerator.create("Object... "), "parameter"
			)
		).addOuterCodeLine("@Override").addBodyElement(body);
		ClassSourceGenerator cls = ClassSourceGenerator.create(
			TypeDeclarationSourceGenerator.create(classSimpleName)
		).addModifier(
			Modifier.PUBLIC
		).addConcretizedType(
			Executable.class
		).addMethod(
			executeMethod
		);
		return UnitSourceGenerator.create(packageName).addClass(cls);
	};
	
	public UnitSourceGenerator generateConsumer(String className, int parametersLength) {
		String packageName = Classes.retrievePackageName(className);
		String classSimpleName = Classes.retrieveSimpleName(className);
		if (className.contains("$")) {
			throw Throwables.toRuntimeException(className + " Consumer could not be a inner class");
		}
		TypeDeclarationSourceGenerator typeDeclaration = TypeDeclarationSourceGenerator.create(classSimpleName);
		FunctionSourceGenerator acceptMethod = FunctionSourceGenerator.create("accept").setReturnType(
			void.class
		).addModifier(Modifier.PUBLIC | Modifier.ABSTRACT);
		FunctionSourceGenerator varArgsAcceptMethod = FunctionSourceGenerator.create("accept").setReturnType(
			void.class
		).addModifier(Modifier.PUBLIC).setDefault().addParameter(
			VariableSourceGenerator.create(TypeDeclarationSourceGenerator.create("Object..."), "params")
		).addOuterCodeLine("@Override");
		varArgsAcceptMethod.addBodyCodeLine("accept(");
		BodySourceGenerator applyMethodCodeOne = BodySourceGenerator.createSimple().setBodyElementSeparator(", ");
		for (int i = 0; i < parametersLength; i++) {
			typeDeclaration.addGeneric(GenericSourceGenerator.create("P" + i));
			acceptMethod.addParameter(VariableSourceGenerator.create(TypeDeclarationSourceGenerator.create("P" + i), "p" + i));
			applyMethodCodeOne.addCode("(P" + i + ")params["+i+"]");
		}
		varArgsAcceptMethod.addBodyElement(applyMethodCodeOne);
		varArgsAcceptMethod.addBodyCode(");");
		ClassSourceGenerator cls = ClassSourceGenerator.createInterface(
			typeDeclaration
		).addModifier(
			Modifier.PUBLIC
		).expands(
			TypeDeclarationSourceGenerator.create(MultiParamsConsumer.class)
		).addMethod(
			acceptMethod
		).addMethod(
			varArgsAcceptMethod
		).addOuterCodeLine("@FunctionalInterface");
		return UnitSourceGenerator.create(packageName).addClass(cls);
	};
	
	public UnitSourceGenerator generatePredicate(String className, int parametersLength) {
		String packageName = Classes.retrievePackageName(className);
		String classSimpleName = Classes.retrieveSimpleName(className);
		if (className.contains("$")) {
			throw Throwables.toRuntimeException(className + " Predicate could not be a inner class");
		}
		TypeDeclarationSourceGenerator typeDeclaration = TypeDeclarationSourceGenerator.create(classSimpleName);
		FunctionSourceGenerator testMethod = FunctionSourceGenerator.create("test").setReturnType(
			boolean.class
		).addModifier(Modifier.PUBLIC | Modifier.ABSTRACT);
		FunctionSourceGenerator varArgsTestMethod = FunctionSourceGenerator.create("test").setReturnType(
			boolean.class
		).addModifier(Modifier.PUBLIC).setDefault().addParameter(
			VariableSourceGenerator.create(TypeDeclarationSourceGenerator.create("Object..."), "params")
		).addOuterCodeLine("@Override");
		varArgsTestMethod.addBodyCodeLine("return test(");
		BodySourceGenerator applyMethodCodeOne = BodySourceGenerator.createSimple().setBodyElementSeparator(", ");
		for (int i = 0; i < parametersLength; i++) {
			typeDeclaration.addGeneric(GenericSourceGenerator.create("P" + i));
			testMethod.addParameter(VariableSourceGenerator.create(TypeDeclarationSourceGenerator.create("P" + i), "p" + i));
			applyMethodCodeOne.addCode("(P" + i + ")params["+i+"]");
		}
		varArgsTestMethod.addBodyElement(applyMethodCodeOne);
		varArgsTestMethod.addBodyCode(");");
		ClassSourceGenerator cls = ClassSourceGenerator.createInterface(
			typeDeclaration
		).addModifier(
			Modifier.PUBLIC
		).expands(
			TypeDeclarationSourceGenerator.create(MultiParamsPredicate.class)
		).addMethod(
			testMethod
		).addMethod(
			varArgsTestMethod
		).addOuterCodeLine("@FunctionalInterface");
		return UnitSourceGenerator.create(packageName).addClass(cls);
	};
	
	public UnitSourceGenerator generateFunction(String className, int parametersLength) {
		String packageName = Classes.retrievePackageName(className);
		String classSimpleName = Classes.retrieveSimpleName(className);
		if (className.contains("$")) {
			throw Throwables.toRuntimeException(className + " Function could not be a inner class");
		}
		TypeDeclarationSourceGenerator typeDeclaration = TypeDeclarationSourceGenerator.create(classSimpleName);
		GenericSourceGenerator returnType = GenericSourceGenerator.create("R");
		FunctionSourceGenerator applyMethod = FunctionSourceGenerator.create("apply").setReturnType(
			returnType
		).addModifier(Modifier.PUBLIC | Modifier.ABSTRACT);
		FunctionSourceGenerator varArgsApplyMethod = FunctionSourceGenerator.create("apply").setReturnType(
			returnType
		).addModifier(Modifier.PUBLIC).setDefault().addParameter(
			VariableSourceGenerator.create(TypeDeclarationSourceGenerator.create("Object..."), "params")
		).addOuterCodeLine("@Override");
		varArgsApplyMethod.addBodyCodeLine("return apply(");
		BodySourceGenerator applyMethodCodeOne = BodySourceGenerator.createSimple().setBodyElementSeparator(", ");
		for (int i = 0; i < parametersLength; i++) {
			typeDeclaration.addGeneric(GenericSourceGenerator.create("P" + i));
			applyMethod.addParameter(VariableSourceGenerator.create(TypeDeclarationSourceGenerator.create("P" + i), "p" + i));
			applyMethodCodeOne.addCode("(P" + i + ")params["+i+"]");
		}
		varArgsApplyMethod.addBodyElement(applyMethodCodeOne);
		varArgsApplyMethod.addBodyCode(");");
		typeDeclaration.addGeneric(returnType);		
		ClassSourceGenerator cls = ClassSourceGenerator.createInterface(
			typeDeclaration
		).addModifier(
			Modifier.PUBLIC
		).expands(
			TypeDeclarationSourceGenerator.create(MultiParamsFunction.class).addGeneric(returnType)
		).addMethod(
			applyMethod
		).addMethod(
			varArgsApplyMethod
		).addOuterCodeLine("@FunctionalInterface");
		return UnitSourceGenerator.create(packageName).addClass(cls);
	};
	
	@Override
	public void close() {}
	
	public Collection<String> addLineCounter(Collection<String> sources) {
		return sources.stream().map(source -> addLineCounter(source)).collect(Collectors.toList());
	}
	
	public String addLineCounter(String source) {
		StringBuffer newSource = new StringBuffer();
		String[] lines = source.split("\n");
		int maxDigitCount = 0;
		int temp = lines.length;
		while(temp > 0) {
			temp = temp / 10;
			++maxDigitCount; 
		}
		for (int lineCounter = 1; lineCounter < lines.length; lineCounter++) {
			newSource.append(String.format("%0" + maxDigitCount + "d", lineCounter) + " | \t" + lines[lineCounter - 1] + "\n");
		}		
		return newSource.toString();
	}
}