package org.burningwave.core.classes.source;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

public class Variable extends Generator.Abst {
	private String indentationElementForOuterCode;
	private Collection<String> outerCode;
	private String separator;
	private Integer modifier;
	private Type type;
	private String name;
	private String value;
	
	private Variable(Type type, String name) {
		this.type = type;
		this.name = name;
	}
	
	public static Variable create(Type type, String name) {
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
		this.outerCode.add(code + "\n");
		return this;
	}
	
	Variable setIndentationElementForOuterCode(String indentationElement) {
		this.indentationElementForOuterCode = indentationElement;
		return this;
	}
	
	Variable setSeparator(String separator) {
		this.separator = separator;
		return this;
	}
	
	@Override
	public String make() {
		return getOrEmpty(
			Optional.ofNullable(outerCode).map(outerCode ->
				indentationElementForOuterCode + getOrEmpty(outerCode).replace("\n", indentationElementForOuterCode)
			).orElseGet(() -> null),
			Modifier.toString(this.modifier),
			type,
			name,
			Optional.ofNullable(value).map(value -> " = " + value).orElseGet(() -> null)
		) + Optional.ofNullable(separator).orElseGet(() -> "");
	}

}
