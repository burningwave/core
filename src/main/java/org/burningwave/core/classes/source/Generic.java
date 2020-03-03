package org.burningwave.core.classes.source;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

public class Generic extends Generator.Abst {
	private Collection<String> outerCode;
	private String name;
	private String hirearchyOperator;
	private TypeDeclaration hirearchyElement;
	
	private Generic(String name) {
		this.name = name;
	}
	
	public static Generic create(String name) {
		return new Generic(name);		
	}
	
	public Generic addOuterCode(String code) {
		this.outerCode = Optional.ofNullable(this.outerCode).orElseGet(ArrayList::new);
		this.outerCode.add(code);
		return this;
	}
	
	public Generic expands(TypeDeclaration hirearchyElement) {
		hirearchyOperator = "extends";
		this.hirearchyElement = hirearchyElement;
		return this;
	}
	
	public Generic parentOf(TypeDeclaration hirearchyElement) {
		hirearchyOperator = "super";
		this.hirearchyElement = hirearchyElement;
		return this;
	}
	
	Collection<TypeDeclaration> getTypesDeclarations() {
		Collection<TypeDeclaration> types = new ArrayList<>();
		Optional.ofNullable(hirearchyElement).ifPresent(hirearchyElement -> {
			types.addAll(hirearchyElement.getTypeDeclarations());
		});
		return types;
	}
	
	@Override
	public String make() {
		return getOrEmpty(outerCode, name, hirearchyOperator, hirearchyElement);
	}	
	
	
}
