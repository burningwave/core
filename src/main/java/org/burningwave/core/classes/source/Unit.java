package org.burningwave.core.classes.source;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

public class Unit extends Generator.Abst {
	private String packageName;
	private Collection<String> imports;
	private Collection<Class> classes;	
	
	private Unit(String packageName) {
		this.packageName = packageName;
	}
	
	public static Unit create(String packageName) {
		return new Unit(packageName);
	}
	
	public Unit addImport(String imprt) {
		this.imports = Optional.ofNullable(this.imports).orElseGet(ArrayList::new);
		this.imports.add(imprt);
		return this;
	}
	
	public Unit addImport(java.lang.Class<?> cls) {
		return this.addImport(cls.getName());
	}
	
	public Unit addClass(Class... clazzes) {
		this.classes = Optional.ofNullable(this.classes).orElseGet(ArrayList::new);
		for (Class cls : clazzes) {
			classes.add(cls);
		}
		return this;
	}

	private Set<String> getImports() {
		Set<String> imports = new LinkedHashSet<>();
		Optional.ofNullable(imports).ifPresent(imprts -> {
			imprts.forEach(imprt -> {
				imports.add("import " + imprt + ";");
			});
		});
		
		getTypeDeclarations().forEach(typeDeclaration -> {
			Optional.ofNullable(typeDeclaration.getName()).ifPresent(className -> {
				imports.add("import " + className + ";");
			});
		});
		return imports;
	}
	
	Collection<TypeDeclaration> getTypeDeclarations() {
		Collection<TypeDeclaration> types = new ArrayList<>();
		Optional.ofNullable(classes).ifPresent(clazzes -> {
			clazzes.forEach(cls -> {
				types.addAll(cls.getTypeDeclarations());
			});
		});
		return types;
	}
	
	@Override
	public String make() {
		return getOrEmpty(
			Arrays.asList("package " + packageName + ";", "\n", getImports(), "\n", getOrEmpty(classes, "\n\n")), "\n"	
		);
	}
}
