package org.burningwave.core.classes.source;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

public class Variable extends Generator.Abst {
	private Collection<String> outerCode;
	private String separator;
	private Integer modifier;
	private TypeDeclaration type;
	private String name;
	private String value;
	
	private Variable(TypeDeclaration type, String name) {
		this.type = type;
		this.name = name;
	}
	
	public static Variable create(TypeDeclaration type, String name) {
		return new Variable(type, name);
	}
	
	public Variable addModifier(Integer modifier) {
		if (this.modifier == null) {
			this.modifier = modifier;
		} else {
			this.modifier |= modifier; 
		}
		return this;
	}
	
	public Variable addOuterCode(String code) {
		this.outerCode = Optional.ofNullable(this.outerCode).orElseGet(ArrayList::new);
		this.outerCode.add(code);
		return this;
	}
	
	public Variable addOuterCodeRow(String code) {
		this.outerCode = Optional.ofNullable(this.outerCode).orElseGet(ArrayList::new);
		if (!this.outerCode.isEmpty()) {
			this.outerCode.add("\n" + code);
		} else {
			this.outerCode.add(code);
		}
		return this;
	}
	
	public Collection<TypeDeclaration> getAllTypes() {
		Collection<TypeDeclaration> types = new ArrayList<>();
		Optional.ofNullable(type).ifPresent(type -> {
			types.addAll(type.getAllTypes());
		});
		return types;
	}
	
	Variable setSeparator(String separator) {
		this.separator = separator;
		return this;
	}
	
	String getSeparator() {
		return this.separator;
	}
	
	@Override
	public String make() {
		return getOrEmpty(
			getOuterCode(),
			Optional.ofNullable(modifier).map(mod -> Modifier.toString(this.modifier)).orElseGet(() -> null),
			type,
			name,
			Optional.ofNullable(value).map(value -> " = " + value).orElseGet(() -> null)
		) + Optional.ofNullable(separator).orElseGet(() -> "");
	}

	protected String getOuterCode() {
		return Optional.ofNullable(outerCode).map(oc -> getOrEmpty(outerCode) + "\n").orElseGet(() -> null);
	}

}
