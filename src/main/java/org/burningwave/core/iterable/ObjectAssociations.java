package org.burningwave.core.iterable;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

import org.burningwave.core.Component;


public class ObjectAssociations<A, B, C> implements Component, Serializable {

	private static final long serialVersionUID = -8746196679726732982L;

	private java.util.Map<A, Map<B, C>> associations;

	public ObjectAssociations() {
		associations = new LinkedHashMap<A, Map<B, C>>();
	}

	public void clear() {
		associations.clear();
	}

	public C getRightAssociationFor(A a, B b) {
		java.util.Map<B, C> rightAssociations = getRightAssociationsFor(a);
		return rightAssociations.get(b);
	}

	public java.util.Map<B, C> getRightAssociationsFor(A a) {
		java.util.Map<B, C> rightAssociations = associations.get(a);
		if (rightAssociations == null) {
			synchronized (this) {
				rightAssociations = associations.get(a);
				if (rightAssociations == null) {
					rightAssociations = new LinkedHashMap<B, C>();
					associations.put(a, rightAssociations);
				}
			}
		}
		return rightAssociations;
	}

	public void addRightAssociation(A a, B b, C c) {
		java.util.Map<B, C> rightAssociations = getRightAssociationsFor(a);
		C associatedObj = rightAssociations.get(b);
		if (associatedObj == null) {
			synchronized (this) {
				associatedObj = rightAssociations.get(b);
				if (associatedObj == null) {
					rightAssociations.put(b, c);
				}
			}
		}
	}

}