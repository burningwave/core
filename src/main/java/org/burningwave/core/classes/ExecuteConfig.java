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
package org.burningwave.core.classes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.burningwave.core.iterable.Properties;

public abstract class ExecuteConfig<C extends ExecuteConfig<C>> {
	ClassLoader parentClassLoader;
	boolean useDefaultClassLoaderAsParentIfParentClassLoaderIsNull;
	List<Object> params;
	
	ExecuteConfig() {
		useDefaultClassLoaderAsParentIfParentClassLoaderIsNull = true;
	}
	
	@SuppressWarnings("unchecked")
	public C useAsParentClassLoader(ClassLoader parentClassLoader) {
		this.parentClassLoader = parentClassLoader;
		return (C)this;
	}
	
	@SuppressWarnings("unchecked")
	public C useDefaultClassLoaderAsParent(boolean flag) {
		this.useDefaultClassLoaderAsParentIfParentClassLoaderIsNull = flag;
		return (C)this; 
	}
	
	@SuppressWarnings("unchecked")
	public C withParameter(Object... parameters) {
		if (params == null) {
			params = new ArrayList<>();
		}
		for (Object param : parameters) {
			params.add(param);
		}
		return (C)this;
	}		
	
	ClassLoader getParentClassLoader() {
		return parentClassLoader;
	}

	boolean isUseDefaultClassLoaderAsParentIfParentClassLoaderIsNull() {
		return useDefaultClassLoaderAsParentIfParentClassLoaderIsNull;
	}

	Object[] getParams() {
		return params != null ?
			params.toArray(new Object[params.size()]) : 
			null;
	}
	
	
	
	public static ExecuteConfig.ForProperties fromDefaultProperties() {
		return new ForProperties();
	}
	
	public static ExecuteConfig.ForProperties forProperties(Properties properties) {
		ExecuteConfig.ForProperties fromProperties = new ForProperties();
		fromProperties.properties = properties;
		return fromProperties;
	}
	
	public static ExecuteConfig.ForProperties forProperty(String propertyName) {
		ExecuteConfig.ForProperties fromProperties = new ForProperties();
		fromProperties.propertyName = propertyName;
		return fromProperties;
	}
	
	public static ExecuteConfig.ForProperties forPropertiesFile(String filePath) {
		ExecuteConfig.ForProperties fromProperties = new ForProperties();
		fromProperties.filePath = filePath;
		return fromProperties;
	}
	
	public static ExecuteConfig.ForBodySourceGenerator forBodySourceGenerator() {
		return new ForBodySourceGenerator(BodySourceGenerator.createSimple());
	}
	
	public static ExecuteConfig.ForBodySourceGenerator forBodySourceGenerator(BodySourceGenerator body) {
		return new ForBodySourceGenerator(body);
	}
	
	
	public static class ForProperties extends ExecuteConfig<ExecuteConfig.ForProperties> {
		private Properties properties;
		private String propertyName;
		private String filePath;
		private boolean isAbsoluteFilePath;
		private Map<String, String> defaultValues;
		    		
		private ForProperties() {
			isAbsoluteFilePath = false;
		}
		
		
		public ExecuteConfig.ForProperties setPropertyName(String propertyName) {
			this.propertyName = propertyName;
			return this;
		}
		
		public ExecuteConfig.ForProperties setFilePathAsAbsolute(boolean flag) {
			this.isAbsoluteFilePath = flag;
			return this;
		}
		
		public ExecuteConfig.ForProperties withDefaultPropertyValue(String key, String value) {
			if (defaultValues == null) {
				defaultValues = new HashMap<>();
			}
			defaultValues.put(key, value);
			return this;
		}
		
		public ExecuteConfig.ForProperties withDefaultPropertyValues(Map<String, String> defaultValues) {
			if (defaultValues == null) {
				defaultValues = new HashMap<>();
			}
			defaultValues.putAll(defaultValues);
			return this;
		}

		Properties getProperties() {
			return properties;
		}


		String getPropertyName() {
			return propertyName;
		}


		String getFilePath() {
			return filePath;
		}


		boolean isAbsoluteFilePath() {
			return isAbsoluteFilePath;
		}


		Map<String, String> getDefaultValues() {
			return defaultValues;
		}   		
		
	}
	
	
	public static class ForBodySourceGenerator extends ExecuteConfig<ExecuteConfig.ForBodySourceGenerator> {
		BodySourceGenerator body;
		
		private ForBodySourceGenerator(BodySourceGenerator body) {
			this.body = body.setElementPrefix("\t");
		}

		BodySourceGenerator getBody() {
			return body;
		}
		
		public ExecuteConfig.ForBodySourceGenerator addCodeRow(String... codeRow) {
			body.addCodeRow(codeRow);
			return this;
		}
		
		public ExecuteConfig.ForBodySourceGenerator addCode(String... code) {
			body.addCode(code);
			return this;
		}
		
		public ExecuteConfig.ForBodySourceGenerator addCode(SourceGenerator... generators) {
			body.addElement(generators);
			return this;
		}

		public ExecuteConfig.ForBodySourceGenerator useType(Class<?>... classes) {
			body.useType(classes);
			return this;
		}
	}
}