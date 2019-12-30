package org.burningwave.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Context implements Component {
	protected Map<Enum<?>, Object> context;

	protected Context() {
		context = new ConcurrentHashMap<>();
	}
	
	public static Context create() {
		return new Context();
	}
	
	public Context put(Enum<?> name, Object parameter) {
		if (parameter != null) {
			context.put(name, parameter);
		} else {
			context.remove(name);
		}
		return this;
	}
	
	@SuppressWarnings("unchecked")
	public <T> T get(Enum<?> name) {
		return ((T)context.get(name));
	}
	
	@Override
	public void close() {
		context.clear();
		context = null;
	}
}