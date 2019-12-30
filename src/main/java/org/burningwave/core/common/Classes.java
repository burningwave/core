package org.burningwave.core.common;

public class Classes {
	
	
	@SuppressWarnings({ "unchecked"})
	public static <T> Class<T> retrieveFrom(Object object) {
		return (Class<T>)(object instanceof Class? object : object.getClass());
	}

	public static Class<?>[] retrieveFrom(Object... objects) {
		Class<?>[] classes = null;
		if (objects != null) {
			classes = new Class[objects.length];
			for (int i = 0; i < objects.length; i++) {
				if (objects[i] != null) {
					classes[i] = retrieveFrom(objects[i]);
				}
			}
		}
		return classes;
	}
	
}
