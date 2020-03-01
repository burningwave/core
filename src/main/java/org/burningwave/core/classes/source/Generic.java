package org.burningwave.core.classes.source;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

public class Generic extends Generator.Abst {
	private String name;
	private String hirearchyOperator;
	private Type hirearchyElement;
	
	private Generic(String name) {
		this.name = name;
	}
	
	public static Generic create(String name) {
		return new Generic(name);		
	}
	
	public Generic expands(Type hirearchyElement) {
		hirearchyOperator = "extends";
		this.hirearchyElement = hirearchyElement;
		return this;
	}
	
	public Generic parentOf(Type hirearchyElement) {
		hirearchyOperator = "super";
		this.hirearchyElement = hirearchyElement;
		return this;
	}
	
	public Collection<Type> getAllTypes() {
		Collection<Type> types = new ArrayList<>();
		Optional.ofNullable(hirearchyElement).ifPresent(hirearchyElement -> {
			types.add(hirearchyElement);
			types.addAll(hirearchyElement.getGenericTypes());
		});
		return types;
	}
	
	@Override
	public String make() {
		return getOrEmpty(name, hirearchyOperator, hirearchyElement);
	}	
	
	
}
