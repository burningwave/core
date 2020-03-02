package org.burningwave.core.classes.source;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

public class Generic extends Generator.Abst {
	private String name;
	private String hirearchyOperator;
	private TypeDeclaration hirearchyElement;
	
	private Generic(String name) {
		this.name = name;
	}
	
	public static Generic create(String name) {
		return new Generic(name);		
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
	
	Collection<TypeDeclaration> getAllTypes() {
		Collection<TypeDeclaration> types = new ArrayList<>();
		Optional.ofNullable(hirearchyElement).ifPresent(hirearchyElement -> {
			types.addAll(hirearchyElement.getAllTypes());
		});
		return types;
	}
	
	@Override
	public String make() {
		return getOrEmpty(name, hirearchyOperator, hirearchyElement);
	}	
	
	
}
