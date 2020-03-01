package org.burningwave.core.classes.source;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

public class Type extends Generator.Abst {
	private String name;
	private String simpleName;
	private Collection<Generic> generics;
	
	private Type(String name, String simpleName) {
		this.name = name;
		this.simpleName = simpleName;
	}
	
	private Type() {}
	
	public static Type create(String name) {
		return new Type(null, name);
	}
	
	public static Type create(java.lang.Class<?> cls) {
		return new Type(cls.getName(), cls.getSimpleName());
	}
	
	public static Type create(Generic... generics) {
		return new Type().addGeneric(generics);
	}
	
	public Type addGeneric(Generic... generics) {
		this.generics = Optional.ofNullable(this.generics).orElseGet(ArrayList::new);
		this.generics.addAll(Arrays.asList(generics));
		return this;
	}
	
	public Collection<Type> getGenericTypes() {
		Collection<Type> types = new ArrayList<>();
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