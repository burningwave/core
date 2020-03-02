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
		return Optional.ofNullable(fields).map(flds -> "\n\t" + getOrEmpty(flds).replace("\n", "\n\t")).orElseGet(() ->"");
	}
	
	private String getMethodsCode() {
		return Optional.ofNullable(methods).map(mths -> "\n\t" + getOrEmpty(mths).replace("\n", "\n\t")).orElseGet(() ->"");
	}
	
	@Override
	public String make() {
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
				"{\n",
				getFieldsCode(),
				getMethodsCode(),
				getInnerClassesCode(),
				"\n\n}"
			);
	}

	protected String getInnerClassesCode() {
		String innerClassesAsString = "";
		if (innerClasses != null) {
			for (Class cls : innerClasses) {
				innerClassesAsString += ("\n\n" + cls.make()).replaceAll("\n(.)", "\n\t$1");
			}
		}
		return innerClassesAsString;
	}

	protected String getOuterCode() {
		return Optional.ofNullable(outerCode).map(outerCode ->
			getOrEmpty(outerCode)
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
						.addOuterCodeRow("@Field").addOuterCodeRow("@Annotation2"))
				.addField(
						Variable.create(TypeDeclaration.create(Integer.class), "index2").addModifier(Modifier.PRIVATE))
				.addMethod(Function.create("find").addModifier(Modifier.PUBLIC)
						.setTypeDeclaration(TypeDeclaration.create(Generic.create("F"), Generic.create("G")))
						.setReturnType(TypeDeclaration.create(void.class)).addParameter(Variable.create(TypeDeclaration.create(Long.class), "parameter1").addOuterCodeRow("@Parameter"))
						.addParameter(Variable.create(TypeDeclaration.create(String.class), "parameter2"))
								.addParameter(Variable.create(TypeDeclaration.create(Long.class), "parameter3"))
				);
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
				.addOuterCodeRow("@Annotation").addOuterCodeRow("@Annotation 2")
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
						.addOuterCodeRow("@Annotation").addOuterCodeRow("@Annotation 2")))
				.addOuterCodeRow("@Annotation").addOuterCodeRow("@Annotation 2");
		System.out.println(cls.make());
		// cls.getAllTypes().forEach(type -> System.out.println(type.getSimpleName()));
	}
}
