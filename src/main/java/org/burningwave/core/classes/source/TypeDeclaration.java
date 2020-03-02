package org.burningwave.core.classes.source;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

public class TypeDeclaration extends Generator.Abst {
	private String name;
	private String simpleName;
	private Collection<Generic> generics;
	
	private TypeDeclaration(String name, String simpleName) {
		this.name = name;
		this.simpleName = simpleName;
	}
	
	private TypeDeclaration() {}
	
	public static TypeDeclaration create(String name) {
		return new TypeDeclaration(null, name);
	}
	
	public static TypeDeclaration create(java.lang.Class<?> cls) {
		return new TypeDeclaration(cls.getName(), cls.getSimpleName());
	}
	
	public static TypeDeclaration create(Generic... generics) {
		return new TypeDeclaration().addGeneric(generics);
	}
	
	public TypeDeclaration addGeneric(Generic... generics) {
		this.generics = Optional.ofNullable(this.generics).orElseGet(ArrayList::new);
		this.generics.addAll(Arrays.asList(generics));
		return this;
	}
	
	Collection<TypeDeclaration> getAllTypes() {
		Collection<TypeDeclaration> types = new ArrayList<>();
		types.add(this);
		Optional.ofNullable(generics).ifPresent(generics -> {
			generics.forEach(generic -> {
				types.addAll(generic.getAllTypes());
			});
		});
		return types;
	}
	
	@Override
	public String make() {
		return getOrEmpty(simpleName)  + Optional.ofNullable(generics).map(generics -> 
		"<" + getOrEmpty(generics, COMMA + EMPTY_SPACE) + ">").orElseGet(() -> "");
	}

	public String getName() {
		return name;
	}

	public String getSimpleName() {
		return simpleName;
	}
	
}