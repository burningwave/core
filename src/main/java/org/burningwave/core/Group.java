package org.burningwave.core;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.function.Predicate;

public class Group<T> extends Item {
	protected Collection<T> elements = new LinkedHashSet<>();
	

	public Group<T> add(T element) {
		elements.add(element);
		if (element instanceof Item) {
			((Item)element).setParent(this);
		}
		return this;
	}
	
	public <I extends Item> I get(String name) {
		return get((item) -> (item instanceof Item && ((Item)item).getName().matches(name)));
		
	}
	
	@SuppressWarnings("unchecked")
	public <I> I get(Predicate<I> predicate) {
		return (I)elements.stream().filter(item -> 
			(predicate.test((I)item))
		).findFirst().orElse(null);
	}
	

	@Override
	public void close() {
		if (elements != null) {
			elements.clear();
			elements = null;
		}
		super.close();
	}
}