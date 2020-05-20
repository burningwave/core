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
import java.util.Optional;
import java.util.regex.Pattern;

import org.burningwave.core.Component;
import org.burningwave.core.assembler.ComponentSupplier;
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

	public UnitSourceGenerator generateExecutor(String className, StatementSourceGenerator statement) {
		if (className.contains("$")) {
			throw Throwables.toRuntimeException(className + " CodeExecutor could not be a inner class");
		}
		String packageName = Classes.retrievePackageName(className);
		String classSimpleName = Classes.retrieveSimpleName(className);
		TypeDeclarationSourceGenerator typeDeclaration = TypeDeclarationSourceGenerator.create(classSimpleName);
		GenericSourceGenerator returnType = GenericSourceGenerator.create("T");
		FunctionSourceGenerator executeMethod = FunctionSourceGenerator.create("execute").setReturnType(
			returnType
		).addModifier(
			Modifier.PUBLIC
		).addParameter(
			VariableSourceGenerator.create(
				TypeDeclarationSourceGenerator.create(ComponentSupplier.class), "componentSupplier"
			)
		).addParameter(
			VariableSourceGenerator.create(
				TypeDeclarationSourceGenerator.create("Object... "), "parameter"
			)
		).addOuterCodeRow("@Override").addBodyElement(statement);
		typeDeclaration.addGeneric(returnType);		
		ClassSourceGenerator cls = ClassSourceGenerator.create(
			typeDeclaration
		).addModifier(
			Modifier.PUBLIC
		).addConcretizedType(
			Executor.class
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
		).addOuterCodeRow("@Override");
		varArgsAcceptMethod.addBodyCodeRow("accept(");
		StatementSourceGenerator applyMethodCodeOne = StatementSourceGenerator.createSimple().setBodyElementSeparator(", ");
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
		).addOuterCodeRow("@FunctionalInterface");
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
		).addOuterCodeRow("@Override");
		varArgsTestMethod.addBodyCodeRow("return test(");
		StatementSourceGenerator applyMethodCodeOne = StatementSourceGenerator.createSimple().setBodyElementSeparator(", ");
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
		).addOuterCodeRow("@FunctionalInterface");
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
		).addOuterCodeRow("@Override");
		varArgsApplyMethod.addBodyCodeRow("return apply(");
		StatementSourceGenerator applyMethodCodeOne = StatementSourceGenerator.createSimple().setBodyElementSeparator(", ");
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
		).addOuterCodeRow("@FunctionalInterface");
		return UnitSourceGenerator.create(packageName).addClass(cls);
	};
	
	@Override
	public void close() {}
}