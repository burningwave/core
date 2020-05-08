package org.burningwave.core.classes;

import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.function.Predicate;

public interface MembersRetriever {
	
	public default Field getDeclaredField(Class<?> cls, Predicate<Field> predicate) {
		Collection<Field> members = getDeclaredFields(cls, predicate);
		if (members.size() > 1) {
			throw Throwables.toRuntimeException("More than one member found for class " + cls.getName());
		}
		return members.stream().findFirst().orElse(null);
	}
	
	public default Method getDeclaredMethod(Class<?> cls, Predicate<Method> predicate) {
		Collection<Method> members = getDeclaredMethods(cls, predicate);
		if (members.size() > 1) {
			throw Throwables.toRuntimeException("More than one member found for class " + cls.getName());
		}
		return members.stream().findFirst().orElse(null);
	}
	
	public default Constructor<?> getDeclaredConstructor(Class<?> cls, Predicate<Constructor<?>> predicate) {
		Collection<Constructor<?>> members = getDeclaredConstructors(cls, predicate);
		if (members.size() > 1) {
			throw Throwables.toRuntimeException("More than one member found for class " + cls.getName());
		}
		return members.stream().findFirst().orElse(null);
	}
	
	public default Collection<Field> getDeclaredFields(Class<?> cls, Predicate<Field> memberPredicate) {
		Collection<Field> members = new HashSet<>();
		for (Field member : getDeclaredFields(cls)) {
			if (memberPredicate.test(member)) {
				members.add(member);
			}
		}
		return members;
	}
	
	

	public default Collection<Constructor<?>> getDeclaredConstructors(Class<?> cls, Predicate<Constructor<?>> predicate) {
		Collection<Constructor<?>> members = new HashSet<>();
		for (Constructor<?> member : getDeclaredConstructors(cls)) {
			if (predicate.test(member)) {
				members.add(member);
			}
		}
		return members;
	}
	
	

	public default Collection<Method> getDeclaredMethods(Class<?> cls, Predicate<Method> memberPredicate) {
		Collection<Method> members = new HashSet<>();
		for (Method member : getDeclaredMethods(cls)) {
			if (memberPredicate.test(member)) {
				members.add(member);
			}
		}
		return members;
	}
	
	public Field[] getDeclaredFields(Class<?> cls);
	
	public Constructor<?>[] getDeclaredConstructors(Class<?> cls);
	
	public Method[] getDeclaredMethods(Class<?> cls);
	
}