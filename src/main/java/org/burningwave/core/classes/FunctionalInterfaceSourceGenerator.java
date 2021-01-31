package org.burningwave.core.classes;

import static org.burningwave.core.assembler.StaticComponentContainer.Classes;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.lang.reflect.Modifier;

import org.burningwave.core.Executable;
import org.burningwave.core.function.MultiParamsConsumer;
import org.burningwave.core.function.MultiParamsFunction;
import org.burningwave.core.function.MultiParamsPredicate;

public class FunctionalInterfaceSourceGenerator {
	

	public static FunctionalInterfaceSourceGenerator create() {
		return new FunctionalInterfaceSourceGenerator();
	}	
	
	public ClassSourceGenerator generateExecutor(String className, BodySourceGenerator body) {
		if (className.contains("$")) {
			Throwables.throwException("{} code executor could not be a inner class", className);
		}
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
		return cls;
	};
	
	public ClassSourceGenerator generateConsumer(String className, int parametersLength) {
		String classSimpleName = Classes.retrieveSimpleName(className);
		if (className.contains("$")) {
			Throwables.throwException("{} consumer could not be a inner class", className);
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
		return cls;
	};
	
	public ClassSourceGenerator generatePredicate(String className, int parametersLength) {
		String classSimpleName = Classes.retrieveSimpleName(className);
		if (className.contains("$")) {
			Throwables.throwException("{} Predicate could not be a inner class", className);
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
		return cls;
	};
	
	public ClassSourceGenerator generateFunction(String className, int parametersLength) {
		String classSimpleName = Classes.retrieveSimpleName(className);
		if (className.contains("$")) {
			Throwables.throwException("{} function could not be a inner class", className);
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
		return cls;
	}
	
}