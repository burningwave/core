package org.burningwave.core.classes.source;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

public class Class extends Generator.Abst {
	private Collection<String> outerCode;
	private Integer modifier;
	private String classType;
	private Type type;
	private String expands;
	private Type expandedType;
	private String concretize;
	private Collection<Type> concretizedTypes;
	private Collection<Generator> members;
	private Collection<Class> classes;
	
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
	
	public Class addOuterCode(String code) {
		this.outerCode = Optional.ofNullable(this.outerCode).orElseGet(ArrayList::new);
		this.outerCode.add(code + "\n");
		return this;
	}
	
	public Class addField(Variable field) {
		this.members = Optional.ofNullable(this.members).orElseGet(ArrayList::new);
		this.members.add(field.setIndentationElementForOuterCode("\n\t").setSeparator(";\n\t"));
		return this;
	}
	
	public Class addInnerClass(Class cls) {
		this.classes = Optional.ofNullable(this.classes).orElseGet(ArrayList::new);
		this.classes.add(cls);
		return this;
	}
	
	public String makeInnerClasses() {
		String innerClasses = "";
		if (classes != null) {
			for (Class cls : classes) {
				innerClasses += cls.makeInnerClasses();
			}
		}
		StringBuilder indentationElementOne = new StringBuilder();
		StringBuilder indentationElementTwo = new StringBuilder();
		indentationElementOne.append("\n\t\t");
		indentationElementTwo.append("\n\t");
		return 
			Optional.ofNullable(outerCode).map(outerCode ->
				"\n" + getOrEmpty(outerCode)
			).orElseGet(() -> null).replace("\n", indentationElementTwo)	+
			getOrEmpty(
				Modifier.toString(this.modifier),
				classType,
				type,
				expands,
				expandedType,
				concretize,
				concretizedTypes, 
				"{\n",
				getOrEmpty(members),
				innerClasses
			).replace("\n\t", indentationElementOne) + indentationElementTwo.toString() + "}";
	}
	
	@Override
	public String make() {
		String innerClasses = "";
		if (classes != null) {
			for (Class cls : classes) {
				innerClasses += cls.makeInnerClasses();
			}	
		}
		return getOrEmpty(
			Optional.ofNullable(outerCode).map(outerCode ->
				getOrEmpty(outerCode)
			).orElseGet(() -> null),
			Modifier.toString(this.modifier),
			classType,
			type,
			expands,
			expandedType,
			concretize,
			concretizedTypes, 
			"{\n",
			getOrEmpty(members),
			innerClasses,
			"\n}"
		);
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
		).addModifier(Modifier.PUBLIC).expands(Object.class)
		.addField(
			Variable.create(Type.create(Integer.class), "index1").addModifier(Modifier.PRIVATE).addOuterCode("@Field").addOuterCode("@Annotation2")
		)
		.addField(
			Variable.create(Type.create(Integer.class), "index2").addModifier(Modifier.PRIVATE)
		);
		cls.addInnerClass(Class.create(
			Type.create("Generated").addGeneric(
					Generic.create("T").expands(Type.create("Class").addGeneric(Generic.create("F").expands(Type.create("ClassTwo").addGeneric(Generic.create("H")))))
				).addGeneric(
					Generic.create("?").parentOf(Type.create("Free").addGeneric(Generic.create("S")).addGeneric(Generic.create("Y")))
				)
			).addModifier(Modifier.PUBLIC).expands(Object.class)
			.addField(
				Variable.create(Type.create(Integer.class), "index1").addModifier(Modifier.PRIVATE).addOuterCode("@Field")
			)
			.addField(
				Variable.create(Type.create(Integer.class), "index2").addModifier(Modifier.PRIVATE)
			).addOuterCode("@Annotation").addOuterCode("@Annotation 2").addInnerClass(Class.create(
			Type.create("Generated").addGeneric(
					Generic.create("T").expands(Type.create("Class").addGeneric(Generic.create("F").expands(Type.create("ClassTwo").addGeneric(Generic.create("H")))))
				).addGeneric(
					Generic.create("?").parentOf(Type.create("Free").addGeneric(Generic.create("S")).addGeneric(Generic.create("Y")))
				)
			).addModifier(Modifier.PUBLIC).expands(Object.class)
			.addField(
				Variable.create(Type.create(Integer.class), "index1").addModifier(Modifier.PRIVATE).addOuterCode("@Field")
			)
			.addField(
				Variable.create(Type.create(Integer.class), "index2").addModifier(Modifier.PRIVATE)
			).addOuterCode("@Annotation").addOuterCode("@Annotation 2"))
		).addOuterCode("@Annotation").addOuterCode("@Annotation 2");
		System.out.println(cls.make());
		cls.getAllTypes().forEach(type -> System.out.println(type.getSimpleName()));
	}
}
