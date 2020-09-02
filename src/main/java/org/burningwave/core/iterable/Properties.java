/*
 * This file is part of Burningwave Core.
 *
 * Author: Roberto Gentili
 *
 * Hosted at: https://github.com/burningwave/core
 *
 * --
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Roberto Gentili
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without
 * limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.burningwave.core.iterable;

import static org.burningwave.core.assembler.StaticComponentContainer.IterableObjectHelper;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Properties extends java.util.Properties {
	private static final long serialVersionUID = -350748766178421942L;
	
	public static enum Event {
		PUT, REMOVE
	}
	
	private Collection<Listener> listeners;
	
	
	public Properties() {
		super();
		listeners = ConcurrentHashMap.newKeySet();
	}
	

////////////////////
	
	public <T> T resolveValue(String key) {
		return IterableObjectHelper.resolveValue(this, key);
	}
	
	public <T> Collection<T> resolveValues(String key) {
		return IterableObjectHelper.resolveValues(this, key);
	}
	
	public String resolveStringValue(String key) {
		return IterableObjectHelper.resolveStringValue(this, key);
	}
	
	public Collection<String> resolveStringValues(String key) {
		return IterableObjectHelper.resolveStringValues(this, key);
	}
	
////////////////////
	
	public <T> T resolveValue(String key, Map<String, ?> defaultValues) {
		return IterableObjectHelper.resolveValue(this, key, defaultValues);
	}
	
	public <T> Collection<T> resolveValues(String key, Map<String, ?> defaultValues) {
		return IterableObjectHelper.resolveValues(this, key, defaultValues);
	}
	
	public String resolveStringValue(String key, Map<String, ?> defaultValues) {
		return IterableObjectHelper.resolveStringValue(this, key, defaultValues);
	}
	
	public Collection<String> resolveStringValues(String key, Map<String, ?> defaultValues) {
		return IterableObjectHelper.resolveStringValues(this, key, defaultValues);
	}

////////////////////
	
	public <T> T resolveValue(String key, String valuesSeparator) {
		return IterableObjectHelper.resolveValue(this, key, valuesSeparator);
	}
	
	public <T> Collection<T> resolveValues(String key, String valuesSeparator) {
		return IterableObjectHelper.resolveValues(this, key, valuesSeparator);
	}
	
	public String resolveStringValue(String key, String valuesSeparator) {
		return IterableObjectHelper.resolveStringValue(this, key, valuesSeparator);
	}
	
	public Collection<String> resolveStringValues(String key, String valuesSeparator) {
		return IterableObjectHelper.resolveStringValues(this, key, valuesSeparator);
	}

////////////////////
	
	public <T> T resolveValue(String key, String valuesSeparator, boolean deleteUnresolvedPlaceHolder) {
		return IterableObjectHelper.resolveObjectValue(this, key, valuesSeparator, deleteUnresolvedPlaceHolder);
	}
	
	public <T> Collection<T> resolveValues(String key, String valuesSeparator, boolean deleteUnresolvedPlaceHolder) {
		return IterableObjectHelper.resolveObjectValues(this, key, valuesSeparator, deleteUnresolvedPlaceHolder);
	}
	
	public String resolveStringValue(String key, String valuesSeparator, boolean deleteUnresolvedPlaceHolder) {
		return IterableObjectHelper.resolveStringValue(this, key, valuesSeparator, deleteUnresolvedPlaceHolder);
	}
	
	public Collection<String> resolveStringValues(String key, String valuesSeparator, boolean deleteUnresolvedPlaceHolder) {
		return IterableObjectHelper.resolveStringValues(this, key, valuesSeparator, deleteUnresolvedPlaceHolder);
	}

////////////////////
	
	
	public Collection<String> getAllPlaceHolders(String propertyName) {
		return IterableObjectHelper.getAllPlaceHolders(this, propertyName);
	}
	
	@Override
	public synchronized Object put(Object key, Object value) {
		Object oldValue = super.put(key, value);
		notifyChange(Event.PUT, key, value, oldValue);
		return oldValue;
	}

	@Override
	public synchronized Object remove(Object key) {
		Object removed = super.remove(key);
		notifyChange(Event.REMOVE, key, null, removed);
		return removed;
	}
	
	private void notifyChange(Event event, Object key, Object newValue, Object oldValue) {
		listeners.forEach((listener) -> 
			listener.receiveNotification(this, event, key, newValue, oldValue)
		);
	}
	
	
	public static interface Listener {
		
		public default void listenTo(Properties properties) {
			properties.listeners.add(this);
		}
		
		public default void unregister(Properties properties) {
			properties.listeners.remove(this);
		} 
		
		public default <K, V>void receiveNotification(Properties properties, Event event, K key, V newValue, V previousValue) {
			
		}
		
	}
	
}
