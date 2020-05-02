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

import static org.burningwave.core.assembler.StaticComponentContainer.Paths;
import static org.burningwave.core.assembler.StaticComponentContainer.Streams;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.burningwave.core.io.FileSystemItem;

public class UnitSourceGenerator extends SourceGenerator.Abst {
	private String packageName;
	private Collection<String> imports;
	private Collection<ClassSourceGenerator> classes;	
	
	private UnitSourceGenerator(String packageName) {
		this.packageName = packageName;
	}
	
	public static UnitSourceGenerator create(String packageName) {
		return new UnitSourceGenerator(packageName);
	}
	
	public UnitSourceGenerator addImport(String... imports) {
		this.imports = Optional.ofNullable(this.imports).orElseGet(ArrayList::new);
		for (String imprt : imports) {
			this.imports.add(imprt);
		}
		return this;
	}
	
	public UnitSourceGenerator addImport(java.lang.Class<?>... classes) {
		for (java.lang.Class<?> cls : classes) {
			this.addImport(cls.getName());
		}
		return this;
	}
	
	public UnitSourceGenerator addClass(ClassSourceGenerator... clazzes) {
		this.classes = Optional.ofNullable(this.classes).orElseGet(ArrayList::new);
		for (ClassSourceGenerator cls : clazzes) {
			classes.add(cls);
		}
		return this;
	}

	private Set<String> getImports() {
		List<String> imports = new ArrayList<>();
		Optional.ofNullable(this.imports).ifPresent(imprts -> {
			imprts.forEach(imprt -> {
				imports.add("import " + imprt.replace("$", ".") + ";");
			});
		});
		
		getTypeDeclarations().forEach(typeDeclaration -> {
			Optional.ofNullable(typeDeclaration.getName()).ifPresent(className -> {
				imports.add("import " + className.replace("$", ".") + ";");
			});
		});
		Collections.sort(imports);
		return new LinkedHashSet<>(imports);
	}
	
	Collection<TypeDeclarationSourceGenerator> getTypeDeclarations() {
		Collection<TypeDeclarationSourceGenerator> types = new ArrayList<>();
		Optional.ofNullable(classes).ifPresent(clazzes -> {
			clazzes.forEach(cls -> {
				types.addAll(cls.getTypeDeclarations());
			});
		});
		return types;
	}
	
	public Map<String, ClassSourceGenerator> getAllClasses() {
		Map<String, ClassSourceGenerator> allClasses = new HashMap<>();
		Optional.ofNullable(classes).ifPresent(classes -> {
			classes.forEach(cls -> {
				allClasses.put(packageName + "." + cls.getTypeDeclaration().getSimpleName(), cls);
				cls.getAllInnerClasses().entrySet().forEach(innerClass -> {
					allClasses.put(packageName + "." + innerClass.getKey(), innerClass.getValue());
				});
			});
		});
		return allClasses;
	}
	
	public ClassSourceGenerator getClass(String className) {
		return getAllClasses().get(className);
	}
	
	@Override
	public String make() {
		return getOrEmpty(
			Arrays.asList("package " + packageName + ";", "\n", getImports(), "\n", getOrEmpty(classes, "\n\n")), "\n"	
		);
	}
	
	public FileSystemItem storeToClassPath(String classPathFolder) {
		classPathFolder = Paths.clean(classPathFolder);
		String classRelativePath = packageName != null? packageName.replace(".", "/") : "";
		for (ClassSourceGenerator cSG : classes) {
			if (cSG.getModifier() != null && Modifier.isPublic(cSG.getModifier())) {
				classRelativePath += "/" + cSG.getSimpleName() + ".java";
				break;
			}
		}
		return Streams.store(classPathFolder + "/" + classRelativePath, make().getBytes());
	}
}
