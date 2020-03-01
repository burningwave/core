package org.burningwave.core.classes.source;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

public class Class extends Generator.Abst {
	private Integer modifier;
	private String classType;
	private Type type;
	private String expands;
	private Type expandedType;
	private String concretize;
	private Collection<Type> concretizedTypes;
	private Collection<Generator> members;
	
	private Class(String classType, Type type) {
		this.classType = classType;
		this.type = type;
	}
	
	public static Class create(Type type) {
		return new Class("class", type);
	}
	
	public static Class createInterface(Type type) {
		return new Class("interface", type);
	}
	
	public Class addModifier(Integer modifier) {
		if (this.modifier == null) {
			this.modifier = modifier;
		} else {
			this.modifier |= modifier; 
		}
		return this;
	}
	
	public Class expands(java.lang.Class<?> extendedClass) {
		return expands(Type.create(extendedClass));
	}
	
	public Class expands(Type expandedType) {
		expands = "extends";
		this.expandedType = expandedType;
		return this;
	}
	
	public Class addConcretizedType(Type... concretizedTypes) {
		concretize = "implements";
		this.concretizedTypes = Optional.ofNullable(this.concretizedTypes).orElseGet(ArrayList::new);
		this.concretizedTypes.addAll(Arrays.asList(concretizedTypes));
		return this;		
	}
	
	@Override
	public String make() {
		return getOrEmpty(Modifier.toString(this.modifier), classType, type, expands, expandedType, concretize, concretizedTypes, "{\n" + "}");
	}
	
	public Collection<Type> getAllTypes() {
		Collection<Type> types = type.getGenericTypes();
		Optional.ofNullable(expandedType).ifPresent(expandedType -> {
			types.add(expandedType);
			types.addAll(expandedType.getGenericTypes());
		});
		Optional.ofNullable(concretizedTypes).ifPresent(concretizedTypes -> {
			types.addAll(concretizedTypes);
			for (Type type : concretizedTypes) {
				types.addAll(type.getGenericTypes());
			}
		});
		return types;
	}
	
	public static void main(String[] args) {
		Class cls = Class.create(
			Type.create("Generated").addGeneric(
				Generic.create("T").expands(Type.create("Class").addGeneric(Generic.create("F").expands(Type.create("ClassTwo").addGeneric(Generic.create("H")))))
			).addGeneric(
				Generic.create("?").parentOf(Type.create("Free").addGeneric(Generic.create("S")).addGeneric(Generic.create("Y")))
			)
		).addModifier(Modifier.PUBLIC).expands(Object.class);
		System.out.println(cls.make());
		cls.getAllTypes().forEach(type -> System.out.println(type.getSimpleName()));
	}
}
