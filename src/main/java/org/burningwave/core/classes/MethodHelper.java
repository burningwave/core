package org.burningwave.core.classes;


import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Predicate;

import org.burningwave.Throwables;
import org.burningwave.core.common.Classes;
import org.burningwave.core.function.ThrowingSupplier;


public class MethodHelper extends MemberHelper<Method> {
	
	private MethodHelper(MemberFinder memberFinder) {
		super(memberFinder);
	}
	
	public static MethodHelper create(MemberFinder memberFinder) {
		return new MethodHelper(memberFinder);
	}

	public String createGetterMethodNameByPropertyName(String property) {
		String methodName = 
				"get" + capitalizeFirstCharacter(property);
		return methodName;
	}

	public String createSetterMethodNameByPropertyName(String property) {
		String methodName = 
				"set" + capitalizeFirstCharacter(property);
		return methodName;
	}
	
	private String capitalizeFirstCharacter(String value) {
		return Character.valueOf(value.charAt(0)).toString().toUpperCase()
		+ value.substring(1, value.length());
	}
	
	public Method findOneAndMakeItAccessible(Object target, String methodName, Object... arguments) {
		return findOneAndMakeItAccessible(target, methodName, true, arguments);
	}
	
	public Method findOneAndMakeItAccessible(Object target, String methodName, boolean cacheMethod, Object... arguments) {
		String cacheKey = getCacheKey(target, "equals " + methodName, arguments);
		Collection<Method> members = cache.get(cacheKey);
		if (members == null) {	
			 members = findAllAndMakeThemAccessible(target, methodName::equals, arguments);
			 if (members.size() != 1) {
					Throwables.toRuntimeException("Method " + methodName
						+ " not found or found more than one methods in " + Classes.retrieveFrom(target).getName()
						+ " hierarchy");
			 }
			 if (cacheMethod) {
				 cache.put(cacheKey, members);
			 }
		}		
		return members.stream().findFirst().get();
	}

	@SuppressWarnings("unchecked")
	public Collection<Method> findAllAndMakeThemAccessible(Object target, Predicate<String> predicateForName, Object... arguments) {
		MethodCriteria criteria = MethodCriteria.create();
		if (predicateForName != null) {
			criteria.name(predicateForName
			).and();
		}
		criteria.parameterTypesAreAssignableFrom(arguments);
		Collection<Method> members = findAllAndApply(
			criteria, target, (member) -> member.setAccessible(true)
		);
		if (members.isEmpty()) {
			Throwables.toRuntimeException("Method not found in any class of " + Classes.retrieveFrom(target).getName()
				+ " hierarchy");
		}
		return members;
	}
	
	public <T> T invoke(Object target, String methodName, Object... arguments) {
		return invoke(target, methodName, true, arguments);
	}

	@SuppressWarnings("unchecked")
	public <T> T invoke(Object target, String methodName, boolean cacheMethod, Object... arguments) {
		return ThrowingSupplier.get(() -> 
			(T)findOneAndMakeItAccessible(target, methodName, cacheMethod, arguments).invoke(target, arguments)
		);
	}
	
	public <T> Collection<T> invokeAll(Object target, String methodNameRegEx, Object... arguments) {
		return invokeAll(target, methodNameRegEx, true, arguments);
	}
	
	@SuppressWarnings("unchecked")
	public <T> Collection<T> invokeAll(Object target, String methodNameRegEx, boolean cacheMember, Object... arguments) {
		return ThrowingSupplier.get(() -> {
			String cacheKey = getCacheKey(target, "matches " + methodNameRegEx, arguments);
			Collection<Method> members = cache.get(cacheKey);
			if (members == null) {	
				members = findAllAndMakeThemAccessible(target, (name) -> name.matches(methodNameRegEx), arguments);
				if (cacheMember) {
					 cache.put(cacheKey, members);
				}
			}			
			Collection<T> results = new ArrayList<>();
			for (Method member : members) {
				results.add((T)member.invoke(target, arguments));
			}			
			return results;
		});
	}
}
