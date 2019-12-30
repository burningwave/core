package org.burningwave.core.classes;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Optional;

import org.burningwave.Throwables;
import org.burningwave.core.common.Classes;


public class FieldHelper extends MemberHelper<Field> {

	private FieldHelper(MemberFinder memberFinder) {
		super(memberFinder);
	}
	
	public static FieldHelper create(MemberFinder memberFinder) {
		return new FieldHelper(memberFinder);
	}
	
	public Field findOneAndMakeItAccessible(Object target, String fieldName) {
		return findOneAndMakeItAccessible(target, fieldName, true);
	}
	
	
	public Field findOneAndMakeItAccessible(
		Object target,
		String fieldName,
		boolean cacheField
	) {
		String cacheKey = getCacheKey(target, "equals " + fieldName, (Object[])null);
		Collection<Field> members = cache.get(cacheKey);
		if (members == null) {
			members = memberFinder.findAll(
				FieldCriteria.forName(
					fieldName::equals
				),
				target
			);
			Optional.ofNullable(members.stream().findFirst().get()).orElseThrow(() ->
				Throwables.toRuntimeException("Field \"" + fieldName
					+ "\" not found in any class of " + Classes.retrieveFrom(target).getName()
					+ " hierarchy"
				)
			).setAccessible(true);
			if (cacheField) {
				cache.put(cacheKey, members);
			}
		}
		return members.stream().findFirst().get();
	}

}
