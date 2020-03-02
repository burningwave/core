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
	private TypeDeclaration typeDeclaration;
	private String expands;
	private TypeDeclaration expandedType;
	private String concretize;
	private Collection<TypeDeclaration> concretizedTypes;
	private Collection<Variable> fields;
	private Collection<Function> constructors;
	private Collection<Function> methods;
	private Collection<Class> innerClasses;
	
	private Class(String classType, TypeDeclaration typeDeclaration) {
		this.classType = classType;
		this.typeDeclaration = typeDeclaration;
	}
	
	public static Class create(TypeDeclaration type) {
		return new Class("class", type);
	}
	
	public static Class createInterface(TypeDeclaration type) {
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
		return expands(TypeDeclaration.create(extendedClass));
	}
	
	public Class expands(TypeDeclaration expandedType) {
		expands = "extends";
		this.expandedType = expandedType;
		return this;
	}
	
	public Class addConcretizedType(TypeDeclaration... concretizedTypes) {
		concretize = "implements";
		this.concretizedTypes = Optional.ofNullable(this.concretizedTypes).orElseGet(ArrayList::new);
		this.concretizedTypes.addAll(Arrays.asList(concretizedTypes));
		return this;		
	}
	
	public Class addOuterCodeRow(String code) {
		this.outerCode = Optional.ofNullable(this.outerCode).orElseGet(ArrayList::new);
		if (!this.outerCode.isEmpty()) {
			this.outerCode.add("\n" + code);
		} else {
			this.outerCode.add(code);
		}
		return this;
	}
	
	public Class addField(Variable field) {
		this.fields = Optional.ofNullable(this.fields).orElseGet(ArrayList::new);
		this.fields.add(field.setSeparator(";"));
		return this;
	}
	
	public Class addConstructor(Function constructor) {
		this.constructors = Optional.ofNullable(this.constructors).orElseGet(ArrayList::new);
		this.constructors.add(constructor);
		constructor.setName(this.typeDeclaration.getSimpleName());
		constructor.setReturnType(null);
		return this;
	}
	
	public Class addMethod(Function method) {
		this.methods = Optional.ofNullable(this.methods).orElseGet(ArrayList::new);
		this.methods.add(method);
		return this;
	}
	
	public Class addInnerClass(Class cls) {
		this.innerClasses = Optional.ofNullable(this.innerClasses).orElseGet(ArrayList::new);
		this.innerClasses.add(cls);
		return this;
	}
	
	private String getFieldsCode() {
		return Optional.ofNullable(fields).map(flds -> "\t" + getOrEmpty(flds, "\n").replace("\n", "\n\t")).orElseGet(() -> null);
	}
	
	private String getFunctionCode(Collection<Function> functions) {
		return Optional.ofNullable(functions).map(mths -> "\t" + getOrEmpty(mths, "\n\n").replace("\n", "\n\t")).orElseGet(() -> null);
	}
	
	private String getInnerClassesCode() {
		String innerClassesAsString = null;
		if (innerClasses != null) {
			innerClassesAsString = "\t";
			for (Class cls : innerClasses) {
				innerClassesAsString += (cls.make()).replaceAll("\n(.)", "\n\t$1");
			}
		}
		return innerClassesAsString;
	}
	
	@Override
	public String make() {
		String fieldsCode = getFieldsCode();
		String constructorsCode = getFunctionCode(constructors);
		String methodsCode = getFunctionCode(methods);
		String innerClassesCode = getInnerClassesCode();
		return
			getOrEmpty(
				getOuterCode(),
				Optional.ofNullable(modifier).map(mod -> Modifier.toString(this.modifier)).orElseGet(() -> null),
				classType,
				typeDeclaration,
				expands,
				expandedType,
				concretize,
				concretizedTypes, 
				"{",
				fieldsCode != null? "\n\n" + fieldsCode : null,
				constructorsCode != null? "\n\n" + constructorsCode : null,
				methodsCode != null? "\n\n" + methodsCode : null,
				innerClassesCode != null? "\n\n" + innerClassesCode : null,
				"\n\n}"
			);
	}

	protected String getOuterCode() {
		return Optional.ofNullable(outerCode).map(outerCode ->
			getOrEmpty(outerCode) +"\n"
		).orElseGet(() -> null);
	}
	
	public Collection<TypeDeclaration> getAllTypes() {
		Collection<TypeDeclaration> types = typeDeclaration.getAllTypes();
		Optional.ofNullable(expandedType).ifPresent(expandedType -> {
			types.addAll(expandedType.getAllTypes());
		});
		Optional.ofNullable(concretizedTypes).ifPresent(concretizedTypes -> {
			types.addAll(concretizedTypes);
			for (TypeDeclaration type : concretizedTypes) {
				types.addAll(type.getAllTypes());
			}
		});
		Optional.ofNullable(fields).ifPresent(fields -> {
			for (Variable field : fields) {
				types.addAll(field.getAllTypes());
			}
		});
		Optional.ofNullable(innerClasses).ifPresent(innerClasses -> {
			for (Class cls : innerClasses) {
				types.addAll(cls.getAllTypes());
			}
		});	
		return types;
	}
	
	public static void main(String[] args) {
		Function method = Function.create("find").addModifier(Modifier.PUBLIC)
		.setTypeDeclaration(TypeDeclaration.create(Generic.create("F"), Generic.create("G")))
		.setReturnType(TypeDeclaration.create(Long.class)).addParameter(Variable.create(TypeDeclaration.create(Long.class), "parameter1").addOuterCodeRow("@Parameter"))
		.addParameter(Variable.create(TypeDeclaration.create(String.class), "parameter2"))
				.addParameter(Variable.create(TypeDeclaration.create(Long.class), "parameter3"))
				.addInnerCodeRow("System.out.println(\"Hello world!\");")
				.addInnerCodeRow("System.out.println(\"How are you!\");")
				.addInnerCodeRow("return new Long(1);")
				.addOuterCodeRow("@MethodAnnotation");
		
		Function method2 = Function.create("find2").addModifier(Modifier.PUBLIC)
				.setTypeDeclaration(TypeDeclaration.create(Generic.create("F"), Generic.create("G")))
				.setReturnType(TypeDeclaration.create(Long.class)).addParameter(Variable.create(TypeDeclaration.create(Long.class), "parameter1"))
				.addParameter(Variable.create(TypeDeclaration.create(String.class), "parameter2"))
						.addParameter(Variable.create(TypeDeclaration.create(Long.class), "parameter3"))
						.addInnerCodeRow("System.out.println(\"Hello world!\");")
						.addInnerCodeRow("System.out.println(\"How are you!\");")
						.addInnerCodeRow("return new Long(1);");
		
		Function constructor = Function.create().addModifier(Modifier.PUBLIC).addInnerCodeRow("this.index1 = 1;");
		
		Class cls = Class
				.create(TypeDeclaration.create("Generated")
						.addGeneric(Generic.create("T")
								.expands(TypeDeclaration.create("Class")
										.addGeneric(Generic.create("F").expands(
												TypeDeclaration.create("ClassTwo").addGeneric(Generic.create("H"))))))
						.addGeneric(Generic.create("?")
								.parentOf(TypeDeclaration.create("Free").addGeneric(Generic.create("S"))
										.addGeneric(Generic.create("Y")))))
				.addModifier(Modifier.PUBLIC).expands(Object.class)
				.addField(Variable.create(TypeDeclaration.create(Integer.class), "index1").addModifier(Modifier.PRIVATE)
						.addOuterCodeRow("@Field").addOuterCodeRow("@Annotation2")
				)
				.addField(
						Variable.create(TypeDeclaration.create(Integer.class), "index2").addModifier(Modifier.PRIVATE))
				.addConstructor(constructor)
				.addMethod(method).addMethod(method2);
		cls.addInnerClass(Class
				.create(TypeDeclaration.create("Generated")
						.addGeneric(Generic.create("T")
								.expands(TypeDeclaration.create("Class")
										.addGeneric(Generic.create("F").expands(
												TypeDeclaration.create("ClassTwo").addGeneric(Generic.create("H"))))))
						.addGeneric(Generic.create("?").parentOf(TypeDeclaration.create("Free")
								.addGeneric(Generic.create("S")).addGeneric(Generic.create("Y")))))
				.addModifier(Modifier.PUBLIC).expands(Object.class)
				.addField(Variable.create(TypeDeclaration.create(Integer.class), "index1").addModifier(Modifier.PRIVATE)
						.addOuterCodeRow("@Field"))
				.addField(
						Variable.create(TypeDeclaration.create(Integer.class), "index2").addModifier(Modifier.PRIVATE))
				.addOuterCodeRow("@Annotation").addOuterCodeRow("@Annotation2")
				.addInnerClass(Class
						.create(TypeDeclaration.create("Generated")
								.addGeneric(Generic.create("T").expands(TypeDeclaration.create("Class")
										.addGeneric(Generic.create("F").expands(
												TypeDeclaration.create("ClassTwo").addGeneric(Generic.create("H"))))))
								.addGeneric(Generic.create("?")
										.parentOf(TypeDeclaration.create("Free").addGeneric(Generic.create("S"))
												.addGeneric(Generic.create("Y")))))
						.addModifier(Modifier.PUBLIC).expands(Object.class)
						.addField(Variable.create(TypeDeclaration.create(Integer.class), "index1")
								.addModifier(Modifier.PRIVATE).addOuterCodeRow("@Field"))
						.addField(Variable.create(TypeDeclaration.create(Integer.class), "index2")
								.addModifier(Modifier.PRIVATE))
						.addOuterCodeRow("@Annotation").addOuterCodeRow("@Annotation2")).addMethod(method))
				.addOuterCodeRow("@Annotation").addOuterCodeRow("@Annotation2");
		System.out.println(cls.make());
		// cls.getAllTypes().forEach(type -> System.out.println(type.getSimpleName()));
	}
}
