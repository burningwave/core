package org.burningwave.core.classes.source;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;

public interface Generator {
	
	public String make();
	
	public default <F> String _toString() {
		return make();
	}
	
	public static abstract class Abst implements Generator {
		static final String EMPTY_SPACE = " ";
		static final String COMMA = ",";
		
		@Override
		public String toString() {
			return make();
		}
		
		String getOrEmpty(Generator value) {
			return Optional.ofNullable(value.make()).orElseGet(() -> "");
		}
		
		String getOrEmpty(String value) {
			return Optional.ofNullable(value).orElseGet(() -> "");
		}
		
		String getOrEmpty(Object... values) {
			return getOrEmpty(Arrays.asList(values));
		}
		
		String getOrEmpty(Collection<?> objects) {
			return getOrEmpty(objects, EMPTY_SPACE);
		}
		
		String getOrEmpty(Collection<?> objects, String separator) {
			String value = "";
			objects = Optional.ofNullable(objects).map(objs -> new ArrayList<>(objs)).orElseGet(ArrayList::new);
			objects.removeAll(Collections.singleton(null));
			objects.removeAll(Collections.singleton(""));
			Iterator<?> objectsItr = objects.iterator();
			while (objectsItr.hasNext()) {
				Object object = objectsItr.next();
				if (object instanceof Generator) {
					value += getOrEmpty((Generator)object);
				} else if (object instanceof String) {
					value += getOrEmpty((String)object);
				} else if (object instanceof Collection) {
					value += getOrEmpty((Collection<?>)object, separator);
				}
				if (objectsItr.hasNext() && !value.endsWith("\t") && !value.endsWith("\n")) {
					value += separator;
				}
			}
			return value;
		}		
	}
}
