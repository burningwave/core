package org.burningwave.core;

public abstract class Item implements Component {
	protected String name;
	protected Item parent;
	
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@SuppressWarnings("unchecked")
	public <T extends Item> T getParent() {
		return (T)this.parent;
	}
	
	void setParent(Item parent) {
		this.parent = parent;
	}

	@Override
	public void close() {
		parent = null;
		logDebug(name != null ? name : this + " finalized");
		name = null;
	}
	
}