package org.burningwave.core.iterable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.burningwave.core.Component;
import org.burningwave.core.common.Strings;
import org.burningwave.core.reflection.PropertyAccessor;

public class IterableObjectHelper implements Component {
	private PropertyAccessor propertyAccessor;
	
	private Pattern PLACE_HOLDER_FOR_PROPERTIES_PATTERN = Pattern.compile("\\$\\{([\\w\\d\\.]*)\\}");
	
	
	private IterableObjectHelper(PropertyAccessor propertyAccessor) {
		this.propertyAccessor = propertyAccessor;
	}
	
	public static IterableObjectHelper create(PropertyAccessor propertyAccessor) {
		return new IterableObjectHelper(propertyAccessor);
	}
	
	public <T> Stream<T> retrieveStream(Object object, String propertyPath) {
		return retrieveStream(propertyAccessor.get(object, propertyPath));
	}
	
	@SuppressWarnings("unchecked")
	public <T> Stream<T> retrieveStream(Object object) {
		Stream<T> stream = null;
		if (object != null) {
			if (object instanceof Collection) {
				return ((Collection<T>)object).stream();
			} else if (object.getClass().isArray()) {
				return Stream.of((T[])object);
			} else if (object instanceof Map) {
				return ((Map<T, ?>)object).keySet().stream();
			}
		}
		return stream;
	}

	public long getSize(Object object) {
		return retrieveStream(object).count();
	}
	
	
	public String get(Properties properties, String propertyName, Map<String, String> defaultValues) {
		String propertyValue = (String)properties.get(propertyName);
		if (Strings.isEmpty(propertyValue) && defaultValues != null) {
			propertyValue = defaultValues.get(propertyName);
		}
		if (!Strings.isEmpty(propertyValue)) {
			Map<Integer, List<String>> subProperties = Strings.extractAllGroups(PLACE_HOLDER_FOR_PROPERTIES_PATTERN, propertyValue);		
			if (!subProperties.isEmpty()) {
				AtomicReference<String> propertyValueWrapper = new AtomicReference<String>(propertyValue);
				subProperties.forEach((group, propertiesNames) -> {
					propertiesNames.forEach((propName) -> {
						propertyValueWrapper.set(propertyValueWrapper.get().replace("${" + propName + "}", get(properties, propName, defaultValues)));
					});				
				});
				propertyValue = propertyValueWrapper.get();
			}
			
		}
		return propertyValue;
	}
}
