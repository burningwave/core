package org.burningwave.core.classes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.burningwave.core.assembler.ComponentSupplier;

public interface CodeExecutor {
	
    public <T> T execute(ComponentSupplier componentSupplier, Object... parameters) throws Throwable;
	
    public static abstract class Config<C extends Config<C>> {
    	ClassLoader parentClassLoader;
    	boolean useDefaultClassLoaderAsParentIfParentClassLoaderIsNull;
    	List<Object> params;
    	
    	Config() {
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
		
		
		
		public static ForProperties forDefaultProperties() {
			return new ForProperties();
		}
		
		public static ForProperties forProperties(Properties properties) {
    		ForProperties fromProperties = new ForProperties();
    		fromProperties.properties = properties;
    		return fromProperties;
    	}
    	
    	public static ForProperties forProperty(String propertyName) {
    		ForProperties fromProperties = new ForProperties();
    		fromProperties.propertyName = propertyName;
    		return fromProperties;
    	}
    	
    	public static ForProperties forPropertiesFile(String filePath) {
    		ForProperties fromProperties = new ForProperties();
    		fromProperties.filePath = filePath;
    		return fromProperties;
    	}
    	
    	public static ForStatementSourceGenerator forStatementSourceGenerator(StatementSourceGenerator statement) {
    		return new ForStatementSourceGenerator(statement);
    	}
    	
    	
    	public static class ForProperties extends Config<ForProperties> {
    		private Properties properties;
    		private String propertyName;
    		private String filePath;
    		private boolean isAbsoluteFilePath;
    		private Map<String, String> defaultValues;
    		    		
    		private ForProperties() {
    			isAbsoluteFilePath = false;
    		}
    		
    		
    		public ForProperties setPropertyName(String propertyName) {
    			this.propertyName = propertyName;
    			return this;
    		}
    		
    		public ForProperties setFilePathAsAbsolute() {
    			this.isAbsoluteFilePath = true;
    			return this;
    		}
    		
    		public ForProperties withDefaultPropertyValue(String key, String value) {
    			if (defaultValues == null) {
    				defaultValues = new HashMap<>();
    			}
    			defaultValues.put(key, value);
    			return this;
    		}
    		
    		public ForProperties withDefaultPropertyValues(Map<String, String> defaultValues) {
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
    	
    	
    	public static class ForStatementSourceGenerator extends Config<ForStatementSourceGenerator> {
    		StatementSourceGenerator statement;
    		
    		private ForStatementSourceGenerator(StatementSourceGenerator statement) {
    			this.statement = statement;
    		}

			StatementSourceGenerator getStatement() {
				return statement;
			}    		
    		
    	}
    }
}
