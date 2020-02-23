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

import java.io.InputStream;
import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;

import org.burningwave.core.classes.Classes;

public class Properties extends java.util.Properties {
	private static final long serialVersionUID = -350748766178421942L;
	private static Properties FOR_STATIC_CLASSES;
	
	static {
		try {
			InputStream propertiesFileIS = Classes.getInstance().getClassLoader(Properties.class).getResourceAsStream("burningwave.static.properties");
			if (propertiesFileIS != null) {
				FOR_STATIC_CLASSES = new Properties();
				FOR_STATIC_CLASSES.load(propertiesFileIS);
			}
		} catch (Throwable exc){
			
		}
	}
	
	public static Object getGlobalProperty(Object propertyKey) {
		if (FOR_STATIC_CLASSES != null) {
			return FOR_STATIC_CLASSES.get(propertyKey);
		}
		return null;
	}
	
	public static enum Event {
		PUT, REMOVE
	}
	
	private Collection<Listener> listeners;
	
	
	public Properties() {
		super();
		listeners = new CopyOnWriteArrayList<>();
	}
	
	public Properties(Properties defaults) {
		super();
		listeners = new CopyOnWriteArrayList<>();
	}
	
	
	@Override
	public synchronized Object put(Object key, Object value) {
		Object put = super.put(key, value);
		notifyChange(Event.PUT, key, put);
		return put;
	}

	@Override
	public synchronized Object remove(Object key) {
		Object removed = super.remove(key);
		notifyChange(Event.REMOVE, key, removed);
		return removed;
	}
	
	private void notifyChange(Event event, Object key, Object value) {
		listeners.forEach((listener) -> 
			listener.receiveNotification(this, event, key, value)
		);
	}
	
	
	public static interface Listener {
		
		public default void listenTo(Properties properties) {
			properties.listeners.add(this);
		}
		
		public default void receiveNotification(Properties properties, Event event, Object key, Object value) {
			
		}
		
	}
	
}
