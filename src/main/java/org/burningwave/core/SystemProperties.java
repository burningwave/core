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
package org.burningwave.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public class SystemProperties {
	Map<String, String> systemProperties;

	private SystemProperties() {
		systemProperties = new ConcurrentHashMap<String, String>();
		refresh();
	}

	public final static SystemProperties getInstance() {
		return Holder.getSystemPropertiesInstance();
	}

	public String get(String key) {
		return systemProperties.get(key);
	}

	public String put(String key, String value) {
		java.util.Properties properties;
		synchronized (properties = System.getProperties()) {
			properties.setProperty(key,  value);
		}
		return systemProperties.put(key, value);
	}

	public synchronized SystemProperties refresh() {
		systemProperties.clear();
		for (Map.Entry<Object, Object> entry : System.getProperties().entrySet()) {
			systemProperties.put((String)entry.getKey(), (String)entry.getValue());
		}
		return this;
	}

	public void forEachProperty(BiConsumer<String, String> consumer) {
		for (Map.Entry<String, String> entry : systemProperties.entrySet()) {
			consumer.accept(entry.getKey(), entry.getValue());
		}
	}

	private static class Holder {
		private static final SystemProperties INSTANCE = new SystemProperties();

		private static SystemProperties getSystemPropertiesInstance() {
			return INSTANCE;
		}
	}
}