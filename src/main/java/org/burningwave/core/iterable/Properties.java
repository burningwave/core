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
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggersRepository;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.burningwave.core.ManagedLogger;

@SuppressWarnings("unchecked")
public class Properties extends java.util.Properties implements ManagedLogger {
	private static final long serialVersionUID = -350748766178421942L;
	
	public static enum Event {
		PUT, REMOVE
	}
	
	private Set<Listener> listeners;
	private String defaultValuesSeparator;
	
    public Properties(Properties defaults) {
    	this(defaults, null);
    }
	
	public Properties() {
		this(null, null);
	}
	
	public Properties(Properties defaults, String defaultValuesSeparator) {
		super(defaults);
		listeners = ConcurrentHashMap.newKeySet();
		this.defaultValuesSeparator = defaultValuesSeparator;
	}
	
	public String getDefaultValuesSeparator() {
		return this.defaultValuesSeparator != null ? this.defaultValuesSeparator : IterableObjectHelper.getDefaultValuesSeparator();
	}

////////////////////
	
	public <T> T resolveValue(String key) {
		return IterableObjectHelper.resolveValue(this, key, null, defaultValuesSeparator, false, this.defaults);
	}
	
	public <T> Collection<T> resolveValues(String key) {
		return IterableObjectHelper.resolveValues(this, key, null, defaultValuesSeparator, false, this.defaults);
	}
	
	public String resolveStringValue(String key) {
		return IterableObjectHelper.resolveStringValue(this, key, null, defaultValuesSeparator, false, this.defaults);
	}
	
	public Collection<String> resolveStringValues(String key) {
		return IterableObjectHelper.resolveStringValues(this, key, null, defaultValuesSeparator, false, this.defaults);
	}
	
////////////////////
	
	public <T> T resolveValue(String key, Map<?, ?> defaultValues) {
		return IterableObjectHelper.resolveValue(this, key, null, defaultValuesSeparator, false, defaultValues);
	}
	
	public <T> Collection<T> resolveValues(String key, Map<?, ?> defaultValues) {
		return IterableObjectHelper.resolveValues(this, key, null, defaultValuesSeparator, false, defaultValues);
	}
	
	public String resolveStringValue(String key, Map<?, ?> defaultValues) {
		return IterableObjectHelper.resolveStringValue(this, key, null, defaultValuesSeparator, false, defaultValues);
	}
	
	public Collection<String> resolveStringValues(String key, Map<?, ?> defaultValues) {
		return IterableObjectHelper.resolveStringValues(this, key, null, defaultValuesSeparator, false, defaultValues);
	}

////////////////////
	
	public <T> T resolveValue(String key, String valuesSeparator) {
		return IterableObjectHelper.resolveValue(this, key, valuesSeparator, defaultValuesSeparator, false, this.defaults);
	}
	
	public <T> Collection<T> resolveValues(String key, String valuesSeparator) {
		return IterableObjectHelper.resolveValues(this, key, valuesSeparator, defaultValuesSeparator, false, this.defaults);
	}
	
	public String resolveStringValue(String key, String valuesSeparator) {
		return IterableObjectHelper.resolveStringValue(this, key, valuesSeparator, defaultValuesSeparator, false, this.defaults);
	}
	
	public Collection<String> resolveStringValues(String key, String valuesSeparator) {
		return IterableObjectHelper.resolveStringValues(this, key, valuesSeparator, defaultValuesSeparator, false, this.defaults);
	}

////////////////////
	
	public <T> T resolveValue(String key, String valuesSeparator, boolean deleteUnresolvedPlaceHolder) {
		return IterableObjectHelper.resolveValue(this, key, valuesSeparator, defaultValuesSeparator, deleteUnresolvedPlaceHolder, this.defaults);
	}
	
	public <T> Collection<T> resolveValues(String key, String valuesSeparator, boolean deleteUnresolvedPlaceHolder) {
		return IterableObjectHelper.resolveValues(this, key, valuesSeparator, defaultValuesSeparator, deleteUnresolvedPlaceHolder, this.defaults);
	}
	
	public String resolveStringValue(String key, String valuesSeparator, boolean deleteUnresolvedPlaceHolder) {
		return IterableObjectHelper.resolveStringValue(this, key, valuesSeparator, defaultValuesSeparator, deleteUnresolvedPlaceHolder, this.defaults);
	}
	
	public Collection<String> resolveStringValues(String key, String valuesSeparator, boolean deleteUnresolvedPlaceHolder) {
		return IterableObjectHelper.resolveStringValues(this, key, valuesSeparator, defaultValuesSeparator, deleteUnresolvedPlaceHolder, this.defaults);
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
	
	public Map<Object, Object> toMap(Supplier<Map<Object, Object>> mapSupplier) {
		Map<Object, Object> allValues = mapSupplier.get();
		if (this.defaults != null) {
			allValues.putAll(this.defaults);
		}
		allValues.putAll(this);
		return allValues;
	}
	
	public String toSimplePrettyString() {
		return toSimplePrettyString(0);
	}
	
	public String toSimplePrettyString(int marginTabCount) {
		return IterableObjectHelper.toString(toMap(TreeMap::new), marginTabCount);
	}
	
	public String toPrettyString() {
		return toPrettyString(0);
	}
	
	public String toPrettyString(int marginTabCount) {
		return IterableObjectHelper.toPrettyString(toMap(TreeMap::new), getDefaultValuesSeparator(), marginTabCount);
	}	
	
	private void notifyChange(Event event, Object key, Object newValue, Object oldValue) {
		for (Listener listener : listeners) {
			try  {
				listener.processChangeNotification(this, event, key, newValue, oldValue);
			} catch (Throwable exc){
				ManagedLoggersRepository.logError(getClass()::getName, "Exception occurred while notifying: " + event.name() + " -> (" + key + " - " + newValue + ") to " + listener, exc);
				ManagedLoggersRepository.logWarn(getClass()::getName, "Resetting {} to previous value: {}", key, oldValue);
				put(key, oldValue);
			}
		}
	}
	
	public static interface Listener {
		
		
		public default <T extends Listener> T listenTo(Properties properties) {
			properties.listeners.add(this);
			return (T)this;
		}
		
		public default <T extends Listener> T unregister(Properties properties) {
			properties.listeners.remove(this);
			return (T)this;
		} 
		
		public default <K, V>void processChangeNotification(Properties properties, Event event, K key, V newValue, V previousValue) {
			
		}
		
	}
	
}
