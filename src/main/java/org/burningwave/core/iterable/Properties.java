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
 * Copyright (c) 2019-2021 Roberto Gentili
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


import static org.burningwave.core.assembler.StaticComponentContainer.Driver;
import static org.burningwave.core.assembler.StaticComponentContainer.IterableObjectHelper;
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggersRepository;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.burningwave.core.ManagedLogger;
import org.burningwave.core.iterable.IterableObjectHelper.ResolveConfig;


@SuppressWarnings("unchecked")
public class Properties extends ConcurrentHashMap<Object, Object> implements ManagedLogger {
	private static final long serialVersionUID = -350748766178421942L;

	public static enum Event {
		PUT, REMOVE
	}
	
	private Set<Listener> listeners;
	private String defaultValuesSeparator;
	
	public Properties() {
		super();
	}
	
	public Properties(Map<?, ?> defaults, String defaultValuesSeparator) {
		super(defaults);
		this.defaultValuesSeparator = defaultValuesSeparator;
	}

	private Set<Listener> getListeners() {
		if (listeners == null) {
			synchronized (this) {
				if (listeners == null) {
					listeners = newKeySet();
				}
			}
		}
		return listeners;
	}
	
	public Properties load(InputStream resourceAsStream) {
		try {
			java.util.Properties properties = new java.util.Properties();
			properties.load(resourceAsStream);
			putAll(properties);
			return this;
		} catch (IOException exc) {
			return Driver.throwException(exc);
		}
	}
	
	public String getDefaultValuesSeparator() {
		return this.defaultValuesSeparator != null ? this.defaultValuesSeparator : IterableObjectHelper.getDefaultValuesSeparator();
	}
	
    public Object setProperty(String key, String value) {
        return put(key, value);
    }
    
    public String getProperty(String key) {
        return (String)get(key);
    }

////////////////////

	public <T> T resolveValue(String key) {
		return IterableObjectHelper.resolveValue(
			ResolveConfig.forNamedKey(key)
			.on(this)
			.withDefaultValueSeparator(defaultValuesSeparator)
		);
	}

	public <T> Collection<T> resolveValues(String key) {
		return IterableObjectHelper.resolveValues(
			ResolveConfig.forNamedKey(key)
			.on(this)
			.withDefaultValueSeparator(defaultValuesSeparator)
		);
	}

	public String resolveStringValue(String key) {
		return IterableObjectHelper.resolveStringValue(
			ResolveConfig.forNamedKey(key)
			.on(this)
			.withDefaultValueSeparator(defaultValuesSeparator)
		);
	}

	public Collection<String> resolveStringValues(String key) {
		return IterableObjectHelper.resolveStringValues(
			ResolveConfig.forNamedKey(key)
			.on(this)
			.withDefaultValueSeparator(defaultValuesSeparator)
		);
	}

////////////////////

	public <T> T resolveValue(String key, Map<?, ?> defaultValues) {
		return IterableObjectHelper.resolveValue(
			ResolveConfig.forNamedKey(key)
			.on(this)
			.withDefaultValueSeparator(defaultValuesSeparator)
			.withDefaultValues(defaultValues)
		);
	}

	public <T> Collection<T> resolveValues(String key, Map<?, ?> defaultValues) {
		return IterableObjectHelper.resolveValues(
			ResolveConfig.forNamedKey(key)
			.on(this)
			.withDefaultValueSeparator(defaultValuesSeparator)
			.withDefaultValues(defaultValues)
		);
	}

	public String resolveStringValue(String key, Map<?, ?> defaultValues) {
		return IterableObjectHelper.resolveStringValue(
			ResolveConfig.forNamedKey(key)
			.on(this)
			.withDefaultValueSeparator(defaultValuesSeparator)
			.withDefaultValues(defaultValues)
		);
	}

	public Collection<String> resolveStringValues(String key, Map<?, ?> defaultValues) {
		return IterableObjectHelper.resolveStringValues(
			ResolveConfig.forNamedKey(key)
			.on(this)
			.withDefaultValueSeparator(defaultValuesSeparator)
			.withDefaultValues(defaultValues)
		);
	}

////////////////////

	public <T> T resolveValue(String key, String valuesSeparator) {
		return IterableObjectHelper.resolveValue(
			ResolveConfig.forNamedKey(key)
			.on(this)
			.withValuesSeparator(valuesSeparator)
			.withDefaultValueSeparator(defaultValuesSeparator)
		);
	}

	public <T> Collection<T> resolveValues(String key, String valuesSeparator) {
		return IterableObjectHelper.resolveValues(
			ResolveConfig.forNamedKey(key)
			.on(this)
			.withValuesSeparator(valuesSeparator)
			.withDefaultValueSeparator(defaultValuesSeparator)
		);
	}

	public String resolveStringValue(String key, String valuesSeparator) {
		return IterableObjectHelper.resolveStringValue(
			ResolveConfig.forNamedKey(key)
			.on(this)
			.withValuesSeparator(valuesSeparator)
			.withDefaultValueSeparator(defaultValuesSeparator)
		);
	}

	public Collection<String> resolveStringValues(String key, String valuesSeparator) {
		return IterableObjectHelper.resolveStringValues(
			ResolveConfig.forNamedKey(key)
			.on(this)
			.withValuesSeparator(valuesSeparator)
			.withDefaultValueSeparator(defaultValuesSeparator)
		);
	}

////////////////////

	public <T> T resolveValue(String key, String valuesSeparator, boolean deleteUnresolvedPlaceHolder) {
		return IterableObjectHelper.resolveValue(
			ResolveConfig.forNamedKey(key)
			.on(this)
			.withValuesSeparator(valuesSeparator)
			.withDefaultValueSeparator(defaultValuesSeparator)
			.deleteUnresolvedPlaceHolder(deleteUnresolvedPlaceHolder)
		);
	}

	public <T> Collection<T> resolveValues(String key, String valuesSeparator, boolean deleteUnresolvedPlaceHolder) {
		return IterableObjectHelper.resolveValues(
			ResolveConfig.forNamedKey(key)
			.on(this)
			.withValuesSeparator(valuesSeparator)
			.withDefaultValueSeparator(defaultValuesSeparator)
			.deleteUnresolvedPlaceHolder(deleteUnresolvedPlaceHolder)
		);
	}

	public String resolveStringValue(String key, String valuesSeparator, boolean deleteUnresolvedPlaceHolder) {
		return IterableObjectHelper.resolveStringValue(
			ResolveConfig.forNamedKey(key)
			.on(this)
			.withValuesSeparator(valuesSeparator)
			.withDefaultValueSeparator(defaultValuesSeparator)
			.deleteUnresolvedPlaceHolder(deleteUnresolvedPlaceHolder)
		);
	}

	public Collection<String> resolveStringValues(String key, String valuesSeparator, boolean deleteUnresolvedPlaceHolder) {
		return IterableObjectHelper.resolveStringValues(
			ResolveConfig.forNamedKey(key)
			.on(this)
			.withValuesSeparator(valuesSeparator)
			.withDefaultValueSeparator(defaultValuesSeparator)
			.deleteUnresolvedPlaceHolder(deleteUnresolvedPlaceHolder)
		);
	}

////////////////////


	public Collection<String> getAllPlaceHolders(String propertyName) {
		return IterableObjectHelper.getAllPlaceHolders(this, propertyName);
	}

	@Override
	public synchronized Object put(Object key, Object value) {
		Object oldValue = null;
		if (value != null) {
			oldValue = super.put(key, value);
		} else {
			oldValue = super.remove(key);
		}
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
		for (Listener listener : getListeners()) {
			try  {
				listener.processChangeNotification(this, event, key, newValue, oldValue);
			} catch (Throwable exc){
				ManagedLoggersRepository.logError(getClass()::getName, "Exception occurred while notifying: " + event.name() + " -> (" + key + " - " + newValue + ") to " + listener, exc);
			}
		}
	}

	public static interface Listener {


		public default <T extends Listener> T listenTo(Properties properties) {
			properties.getListeners().add(this);
			return (T)this;
		}

		public default <T extends Listener> T unregister(Properties properties) {
			properties.getListeners().remove(this);
			return (T)this;
		}

		public default <K, V>void processChangeNotification(Properties properties, Event event, K key, V newValue, V previousValue) {

		}

	}

}
