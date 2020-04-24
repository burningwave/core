package org.burningwave.core.classes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

public class AnnotationSourceGenerator extends SourceGenerator.Abst {
	private TypeDeclarationSourceGenerator type;
	private String name;
	private StatementSourceGenerator statement;
	
	public AnnotationSourceGenerator(String simpleName) {
		this.name = simpleName;
	}


	public static AnnotationSourceGenerator create(String name) {
		return new AnnotationSourceGenerator(name);		
	}	

	public static AnnotationSourceGenerator create(Class<?> cls) {
		AnnotationSourceGenerator annotation = new AnnotationSourceGenerator(cls.getSimpleName());
		annotation.type = TypeDeclarationSourceGenerator.create(cls);
		return annotation;
	}
	
	Collection<TypeDeclarationSourceGenerator> getTypeDeclarations() {
		Collection<TypeDeclarationSourceGenerator> types = new ArrayList<>();
		Optional.ofNullable(statement).ifPresent(hirearchyElements -> {
			types.addAll(statement.getTypeDeclarations());
		});
		Optional.ofNullable(type).ifPresent(hirearchyElement -> {
			types.add(type);
		});
		return types;
	}
	
	public AnnotationSourceGenerator addParameters(VariableSourceGenerator... parameters) {
		return addParameters(null, parameters);
	}
	
	public AnnotationSourceGenerator addParameters(String name, VariableSourceGenerator... parameters) {
		this.statement = Optional.ofNullable(this.statement).orElseGet(() ->
			StatementSourceGenerator.createSimple().setDelimiters("(", ")").setBodyElementSeparator(", ")
		);
		StatementSourceGenerator innStatement = StatementSourceGenerator.createSimple().setBodyElementSeparator(", ");
		if (name != null) {
			innStatement.setDelimiters(name + " = {", "}");
		} else {
			innStatement.setDelimiters("{", "}");
		}
		for (VariableSourceGenerator parameter : parameters) {
			innStatement.addElement(parameter.setDelimiter(null));
		}
		this.statement.addElement(innStatement);
		return this;
	}
	
	public AnnotationSourceGenerator addParameter(VariableSourceGenerator parameter) {
		this.statement = Optional.ofNullable(this.statement).orElseGet(() -> 
			StatementSourceGenerator.createSimple().setDelimiters("(", ")").setBodyElementSeparator(", ")
		).addElement(parameter.setDelimiter(null));
		return this;
	}
	
	public AnnotationSourceGenerator addParameter(AnnotationSourceGenerator parameter) {
		this.statement = Optional.ofNullable(this.statement).orElseGet(() ->
			StatementSourceGenerator.createSimple().setDelimiters("(", ")").setBodyElementSeparator(", ")
		);
		this.statement.addElement(parameter);
		return this;
	}
	
	public AnnotationSourceGenerator addParameters(AnnotationSourceGenerator... parameters) {
		return addParameters(null, parameters);
	}
	
	public AnnotationSourceGenerator addParameters(String name, AnnotationSourceGenerator... parameters) {
		this.statement = Optional.ofNullable(this.statement).orElseGet(() ->
			StatementSourceGenerator.createSimple().setDelimiters("(", ")").setBodyElementSeparator(", ")
		);
		StatementSourceGenerator innStatement = StatementSourceGenerator.createSimple().setBodyElementSeparator(", ");
		if (name != null) {
			innStatement.setDelimiters(name + " = {", "}");
		} else {
			innStatement.setDelimiters("{", "}");
		}
		for (AnnotationSourceGenerator parameter : parameters) {
			innStatement.addElement(parameter);
		}
		this.statement.addElement(innStatement);
		return this;
	}
	
	public AnnotationSourceGenerator useType(java.lang.Class<?>... classes) {
		this.statement = Optional.ofNullable(this.statement).orElseGet(() ->
			StatementSourceGenerator.createSimple().setDelimiters("(", ")").setBodyElementSeparator(", ")
		);
		statement.useType(classes);
		return this;	
	}
	
	@Override
	public String make() {
		return getOrEmpty("@" + name, statement);
	}
}
