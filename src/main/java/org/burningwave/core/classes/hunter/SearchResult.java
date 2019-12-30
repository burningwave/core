package org.burningwave.core.classes.hunter;

import java.util.Collection;
import java.util.Map;

import org.burningwave.core.Component;

public class SearchResult<K, T> implements Component {
	protected SearchContext<K, T> context;
	
	SearchResult(SearchContext<K, T> context) {
		this.context = context;
	}
	
	public Collection<T> getItemsFound() {
		return context.getItemsFound();
	}
	
	public Map<K, T> getItemsFoundFlatMap() {
		return context.getItemsFoundFlatMap();
	}
	
	@SuppressWarnings("unchecked")
	<C extends ClassLoader> C getUsedClassLoader() {
		return (C) context.pathMemoryClassLoader;
	}
	
	public void waitForSearchEnding() {
		context.waitForSearchEnding();
	}
	
	@Override
	public void close() {
		context.close();
		context = null;
	}
}