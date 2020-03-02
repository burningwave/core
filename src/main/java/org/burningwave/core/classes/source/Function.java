package org.burningwave.core.classes.source;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Optional;

public class Function extends Generator.Abst {
	private Collection<String> outerCode;
	private Integer modifier;
	private TypeDeclaration typesDeclaration;
	private TypeDeclaration returnType;
	private String name;
	private Collection<Variable> parameters;
	private Collection<String> innerCode;
	
	private Function(String name) {
		this.name = name;
	}
	
	public static Function create(String name) {
		return new Function(name);
	}
	
	public Function addModifier(Integer modifier) {
		if (this.modifier == null) {
			this.modifier = modifier;
		} else {
			this.modifier |= modifier; 
		}
		return this;
	}
	
	public Function setTypeDeclaration(TypeDeclaration typesDeclaration) {
		this.typesDeclaration = typesDeclaration;
		return this;
	}
	
	public Function setReturnType(TypeDeclaration returnType) {
		this.returnType = returnType;
		return this;
	}
	
	public Function addParameter(Variable parameter) {
		this.parameters = Optional.ofNullable(this.parameters).orElseGet(ArrayList::new);
		this.parameters.add(parameter);
		return this;
	}
	
	public Function addOuterCodeRow(String code) {
		this.outerCode = Optional.ofNullable(this.outerCode).orElseGet(ArrayList::new);
		this.outerCode.add(code + "\n");
		return this;
	}
	
	public Function addInnerCodeRow(String code) {
		this.innerCode = Optional.ofNullable(this.innerCode).orElseGet(ArrayList::new);
		this.innerCode.add("\t" + code + "\n");
		return this;
	}
	
	@Override
	public String make() {
		return "\n" + 
			getOrEmpty(
				Optional.ofNullable(outerCode).map(outerCode ->
					getOrEmpty(outerCode)
				).orElseGet(() -> null),
				Optional.ofNullable(modifier).map(mod -> Modifier.toString(this.modifier)).orElseGet(() -> null),
				typesDeclaration,
				returnType,
				name,
				getParametersCode(),
				"{"
			);
	}
	
	private String getParametersCode() {
		String paramsCode = "(";
		if (parameters != null) {
			Iterator<Variable> paramsIterator =  parameters.iterator();
			while (paramsIterator.hasNext()) {
				Variable param = paramsIterator.next();
				paramsCode += param.make().replace("\n", "\n\t");
				if (paramsIterator.hasNext()) {
					paramsCode += ",";
				} else {
					paramsCode += "\n";
				}
			}
		}
		return paramsCode + ")";
	}
	
	public <T> T  get() {
		return null;
	}
	
}
