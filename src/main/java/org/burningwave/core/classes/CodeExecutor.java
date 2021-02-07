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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import org.burningwave.core.Executable;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.concurrent.QueuedTasksExecutor;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.iterable.Properties;


public interface CodeExecutor {
	
	public static class Configuration {
		
		public static class Key {
			public static final String COMMON_IMPORTS = "code-executor.common.imports";
			public static final String ADDITIONAL_COMMON_IMPORTS = "code-executor.common.additional-imports";
			public static final String PROPERTIES_FILE_IMPORTS_SUFFIX = ".imports";
			public static final String PROPERTIES_FILE_SUPPLIER_KEY = "supplier";
			public static final String PROPERTIES_FILE_EXECUTOR_KEY = "executor";
			public static final String PROPERTIES_FILE_SUPPLIER_IMPORTS_SUFFIX = "." + PROPERTIES_FILE_SUPPLIER_KEY + PROPERTIES_FILE_IMPORTS_SUFFIX;
			public static final String PROPERTIES_FILE_EXECUTOR_IMPORTS_SUFFIX = "." + PROPERTIES_FILE_EXECUTOR_KEY + PROPERTIES_FILE_IMPORTS_SUFFIX;
			public static final String PROPERTIES_FILE_CLASS_NAME_SUFFIX = ".name";
			public static final String PROPERTIES_FILE_SUPPLIER_NAME_SUFFIX = "." + PROPERTIES_FILE_SUPPLIER_KEY + PROPERTIES_FILE_CLASS_NAME_SUFFIX;
			public static final String PROPERTIES_FILE_EXECUTOR_NAME_SUFFIX = "." + PROPERTIES_FILE_EXECUTOR_KEY + PROPERTIES_FILE_CLASS_NAME_SUFFIX;
			public static final String PROPERTIES_FILE_CLASS_SIMPLE_NAME_SUFFIX = ".simple-name";
			public static final String PROPERTIES_FILE_SUPPLIER_SIMPLE_NAME_SUFFIX = "." + PROPERTIES_FILE_SUPPLIER_KEY + PROPERTIES_FILE_CLASS_SIMPLE_NAME_SUFFIX;
			public static final String PROPERTIES_FILE_EXECUTOR_SIMPLE_NAME_SUFFIX = "." + PROPERTIES_FILE_EXECUTOR_KEY + PROPERTIES_FILE_CLASS_SIMPLE_NAME_SUFFIX;
			
		}
		
		public static class Value {
			public static final String CODE_LINE_SEPARATOR = ";";
		}
		
		public final static Map<String, Object> DEFAULT_VALUES;
		
		static {
			Map<String, Object> defaultValues = new HashMap<>();

			defaultValues.put(Key.COMMON_IMPORTS,
				"static " + org.burningwave.core.assembler.StaticComponentContainer.class.getName() + ".BackgroundExecutor" + Value.CODE_LINE_SEPARATOR +
				"static " + org.burningwave.core.assembler.StaticComponentContainer.class.getName() + ".ManagedLoggersRepository" + Value.CODE_LINE_SEPARATOR +
				"${"+ Key.ADDITIONAL_COMMON_IMPORTS +  "}" + Value.CODE_LINE_SEPARATOR +
 				ComponentSupplier.class.getName() + Value.CODE_LINE_SEPARATOR +
				Function.class.getName() + Value.CODE_LINE_SEPARATOR +
				FileSystemItem.class.getName() + Value.CODE_LINE_SEPARATOR +
				PathHelper.class.getName() + Value.CODE_LINE_SEPARATOR +
				QueuedTasksExecutor.ProducerTask.class.getName() + Value.CODE_LINE_SEPARATOR +
				QueuedTasksExecutor.Task.class.getName() + Value.CODE_LINE_SEPARATOR +
				Supplier.class.getName() + Value.CODE_LINE_SEPARATOR
			);
			
			DEFAULT_VALUES = Collections.unmodifiableMap(defaultValues);
		}
		
	}
	
	public static CodeExecutor create(
		Supplier<ClassFactory> classFactorySupplier,
		PathHelper pathHelper,
		Properties config
	) {
		return new CodeExecutorImpl(
			classFactorySupplier,
			pathHelper,
			config
		);
	}
	
	public <T> T executeProperty(String propertyName, Object... params);

	public <E extends ExecuteConfig<E>, T> T execute(ExecuteConfig.ForProperties config);

	public <E extends ExecuteConfig<E>, T> T execute(BodySourceGenerator body);

	public <E extends ExecuteConfig<E>, T> T execute(E config);

	public <E extends LoadOrBuildAndDefineConfig.ForCodeExecutorAbst<E>, T extends Executable> Class<T> loadOrBuildAndDefineExecutorSubType(
			E config);
}
