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

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import org.burningwave.core.function.PentaConsumer;
import org.burningwave.core.function.TriConsumer;

public class PojoSourceGenerator {
	public static int ALL_OPTIONS_DISABLED = 0b00000000;
	public static int BUILDING_METHODS_CREATION_ENABLED = 0b00000001;
	public static int USE_OF_FULLY_QUALIFIED_CLASS_NAMES_ENABLED = 0b00000010;
	
	private TriConsumer<PojoSourceGenerator, ClassSourceGenerator, Map<String, VariableSourceGenerator>> fieldsBuilder;
	private PentaConsumer<PojoSourceGenerator, ClassSourceGenerator, FunctionSourceGenerator, Method, Integer> setterMethodsBodyBuilder;
	private PentaConsumer<PojoSourceGenerator, ClassSourceGenerator, FunctionSourceGenerator, Method, Integer> getterMethodsBodyBuilder;
	private PentaConsumer<PojoSourceGenerator, ClassSourceGenerator, Class<?>, Collection<Class<?>>, Integer> extraElementsBuilder;
	
	private PojoSourceGenerator(
		TriConsumer<PojoSourceGenerator, ClassSourceGenerator, Map<String, VariableSourceGenerator>> fieldsBuilder,
		PentaConsumer<PojoSourceGenerator, ClassSourceGenerator, FunctionSourceGenerator, Method, Integer> setterMethodsBodyBuilder,
		PentaConsumer<PojoSourceGenerator, ClassSourceGenerator, FunctionSourceGenerator, Method, Integer> getterMethodsBodyBuilder,
		PentaConsumer<PojoSourceGenerator, ClassSourceGenerator, Class<?>, Collection<Class<?>>, Integer> extraElementsBuilder
	) {
		this.fieldsBuilder = fieldsBuilder;
		this.setterMethodsBodyBuilder = setterMethodsBodyBuilder;
		this.getterMethodsBodyBuilder = getterMethodsBodyBuilder;
		this.extraElementsBuilder = extraElementsBuilder;
	}
	
	public static PojoSourceGenerator create() {
		return new PojoSourceGenerator(
			(pSG, cls, fieldsMap) -> {
				fieldsMap.entrySet().forEach(entry -> {
					cls.addField(entry.getValue().addModifier(Modifier.PRIVATE));
				});
			}, (pSG, cls, methodSG, method, options) -> {
				String fieldName = Strings.lowerCaseFirstCharacter(method.getName().replaceFirst("set", ""));
				methodSG.addBodyCodeLine("this." + fieldName + " = " + fieldName + ";");
			}, (pSG, cls, methodSG, method, options) -> {
				String prefix = method.getName().startsWith("get")? "get" : "is";
				String fieldName = Strings.lowerCaseFirstCharacter(method.getName().replaceFirst(prefix, ""));
				methodSG.addBodyCodeLine("return this." + fieldName + ";");
			}, null
		);
	}
	
	public PojoSourceGenerator setFieldsBuilder(TriConsumer<PojoSourceGenerator, ClassSourceGenerator, Map<String, VariableSourceGenerator>> fieldsBuilder) {
		this.fieldsBuilder = fieldsBuilder;
		return this;
	}

	public PojoSourceGenerator setSetterMethodsBodyBuilder(PentaConsumer<PojoSourceGenerator, ClassSourceGenerator, FunctionSourceGenerator, Method, Integer> setterMethodsBodyBuilder) {
		this.setterMethodsBodyBuilder = setterMethodsBodyBuilder;
		return this;
	}

	public PojoSourceGenerator setGetterMethodsBodyBuilder(PentaConsumer<PojoSourceGenerator, ClassSourceGenerator, FunctionSourceGenerator, Method, Integer> getterMethodsBodyBuilder) {
		this.getterMethodsBodyBuilder = getterMethodsBodyBuilder;
		return this;
	}

	public PojoSourceGenerator setExtraElementsBuilder(
		PentaConsumer<PojoSourceGenerator, ClassSourceGenerator, Class<?>, Collection<Class<?>>, Integer> extraElementsBuilder
	) {
		this.extraElementsBuilder = extraElementsBuilder;
		return this;
	}
	
	public ClassSourceGenerator generate(String className, Class<?>... superClasses) {
		return generate(className, ALL_OPTIONS_DISABLED, superClasses);
	}
	
	public ClassSourceGenerator generate(String className, int options, Class<?>... superClasses) {
		if (className.contains("$")) {
			Throwables.throwException("{} Pojo could not be a inner class", className);
		}
		String packageName = Classes.retrievePackageName(className);
		String classSimpleName = Classes.retrieveSimpleName(className);
		ClassSourceGenerator cls = ClassSourceGenerator.create(
			TypeDeclarationSourceGenerator.create(classSimpleName)
		).addModifier(
			Modifier.PUBLIC
		);
		Class<?> superClass = null;
		Collection<Class<?>> interfaces = new LinkedHashSet<>();
		for (Class<?> iteratedSuperClass : superClasses) {
			if (iteratedSuperClass.isInterface()) {
				cls.addConcretizedType(createTypeDeclaration((options & USE_OF_FULLY_QUALIFIED_CLASS_NAMES_ENABLED) != 0, iteratedSuperClass));
				interfaces.add(iteratedSuperClass);
			} else if (superClass == null) {
				cls.expands(createTypeDeclaration(isUseFullyQualifiedClassNamesEnabled(options), iteratedSuperClass));
				superClass = iteratedSuperClass;
			} else {
				Throwables.throwException("{} Pojo could not extends more than one class", className);
			}
		}
		if (superClass != null) {
			String superClassPackage = Optional.ofNullable(superClass.getPackage()).map(pckg -> pckg.getName()).orElseGet(() -> "");
			Predicate<Executable> modifierTester = 
				Strings.areEquals(packageName, superClassPackage) ?
					executable ->
						!Modifier.isPrivate(executable.getModifiers()) :
					executable ->
						Modifier.isPublic(executable.getModifiers()) ||
						Modifier.isProtected(executable.getModifiers());						
			for (Constructor<?> constructor : Classes.getDeclaredConstructors(superClass, constructor -> 
				modifierTester.test(constructor))
			) {
				Integer modifiers = constructor.getModifiers();
				if (isBuildingMethodsCreationEnabled(options)) {
					if (Modifier.isPublic(modifiers)) {
						modifiers ^= Modifier.PUBLIC;
					}
				}
				cls.addConstructor(
					create(
						classSimpleName, constructor, modifiers, (funct, params) ->
						funct.addBodyCodeLine("super(" + String.join(", ", params) + ");"),
						isUseFullyQualifiedClassNamesEnabled(options)
					)
				);
				if (isBuildingMethodsCreationEnabled(options)) {
					cls.addMethod(
						create(
							"create", constructor, modifiers, (funct, params) ->
								funct.addBodyCodeLine("return new " + classSimpleName + "(" + String.join(", ", params) + ");"),
							isUseFullyQualifiedClassNamesEnabled(options)
						).addModifier(Modifier.STATIC | Modifier.PUBLIC).setReturnType(classSimpleName)
					);
				}
			}
		}
		Map<String, VariableSourceGenerator> fieldsMap = new HashMap<>();
		for (Class<?> interf : interfaces) {
			for (Method method : Classes.getDeclaredMethods(interf, method -> 
				method.getName().startsWith("set") || method.getName().startsWith("get") || method.getName().startsWith("is")
			)) {
				Integer modifiers = method.getModifiers();
				if (Modifier.isAbstract(modifiers)) {
					modifiers ^= Modifier.ABSTRACT;
				}
				FunctionSourceGenerator methodSG = FunctionSourceGenerator.create(method.getName()).addModifier(modifiers);
				methodSG.setReturnType(createTypeDeclaration(isUseFullyQualifiedClassNamesEnabled(options), method.getReturnType()));
				if (method.getName().startsWith("set")) {
					String fieldName = Strings.lowerCaseFirstCharacter(method.getName().replaceFirst("set", ""));
					Class<?> paramType = method.getParameters()[0].getType();
					fieldsMap.put(fieldName, VariableSourceGenerator.create(createTypeDeclaration(isUseFullyQualifiedClassNamesEnabled(options), paramType), fieldName));
					methodSG.addParameter(VariableSourceGenerator.create(createTypeDeclaration(isUseFullyQualifiedClassNamesEnabled(options), paramType), fieldName));
					if (setterMethodsBodyBuilder != null) {
						setterMethodsBodyBuilder.accept(this, cls, methodSG, method, options);
					}
				} else if (method.getName().startsWith("get") || method.getName().startsWith("is")) {
					String prefix = method.getName().startsWith("get")? "get" : "is";
					String fieldName = Strings.lowerCaseFirstCharacter(method.getName().replaceFirst(prefix, ""));
					fieldsMap.put(fieldName, VariableSourceGenerator.create(createTypeDeclaration(isUseFullyQualifiedClassNamesEnabled(options), method.getReturnType()), fieldName));
					if (getterMethodsBodyBuilder != null) {
						getterMethodsBodyBuilder.accept(this, cls, methodSG, method, options);
					}
				}
				cls.addMethod(methodSG);
			}
			if (fieldsBuilder != null) {
				fieldsBuilder.accept(this, cls, fieldsMap);
			}
		}
		if (extraElementsBuilder != null) {
			extraElementsBuilder.accept(this, cls, superClass, interfaces, options);
		}
		return cls;
	}
	
	public boolean isUseFullyQualifiedClassNamesEnabled(int options) {
		return (options & USE_OF_FULLY_QUALIFIED_CLASS_NAMES_ENABLED) != 0;
	}
	
	public boolean isBuildingMethodsCreationEnabled(int options) {
		return (options & BUILDING_METHODS_CREATION_ENABLED) != 0;
	}
	
	TypeDeclarationSourceGenerator createTypeDeclaration(boolean useFullyQualifiedNames,
			Class<?> cls) {
		if (useFullyQualifiedNames) {
			return TypeDeclarationSourceGenerator.create(cls.getName().replace("$", "."));
		} else {
			return TypeDeclarationSourceGenerator.create(cls);
		}
	};
	
	private FunctionSourceGenerator create(
		String functionName,
		Executable executable,
		Integer modifiers,
		BiConsumer<FunctionSourceGenerator, Collection<String>> bodyBuilder,
		boolean useFullyQualifiedNames
	) {
		FunctionSourceGenerator function = FunctionSourceGenerator.create(functionName);
		Collection<String> params = new ArrayList<>();
		for (Parameter paramType : executable.getParameters()) {
			function.addParameter(
				VariableSourceGenerator.create(createTypeDeclaration(useFullyQualifiedNames, paramType.getType()), paramType.getName())
			);
			params.add(paramType.getName());
		}
		bodyBuilder.accept(function, params);
		return function;
	}
}