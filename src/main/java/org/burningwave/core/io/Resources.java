package org.burningwave.core.io;

import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Optional;

import org.burningwave.core.assembler.StaticComponentContainer;
import org.burningwave.core.function.ThrowingSupplier;
import org.burningwave.core.iterable.Properties;

public class Resources {
	
	public Map.Entry<Properties, URL> loadFirstOneFound(String... fileNames) {
		Properties properties = new Properties();
		Map.Entry<org.burningwave.core.iterable.Properties, URL> propertiesBag = new AbstractMap.SimpleEntry<>(properties, null);
		for (String fileName : fileNames) {
			ClassLoader classLoader = StaticComponentContainer.class.getClassLoader();
			InputStream propertiesFileIS = getAsInputStream(classLoader, fileName);
			if (propertiesFileIS != null) {				
				try {
					properties.load(propertiesFileIS);
					URL configFileURL = getURL(classLoader, fileName);
					propertiesBag.setValue(configFileURL);
					break;
				} catch (Throwable exc) {
					throw Throwables.toRuntimeException(exc);
				}
			}
		}
		return propertiesBag;
	}
	
	public InputStream getAsInputStream(ClassLoader resourceClassLoader, String resourceRelativePath) {
		return Optional.ofNullable(
			resourceClassLoader
		).orElseGet(() -> ClassLoader.getSystemClassLoader()).getResourceAsStream(
			resourceRelativePath
		);
	}
	
	public StringBuffer getAsStringBuffer(ClassLoader resourceClassLoader, String resourceRelativePath) {
		return ThrowingSupplier.get(() -> {
			ClassLoader classLoader = Optional.ofNullable(resourceClassLoader).orElseGet(() ->
				ClassLoader.getSystemClassLoader()
			);
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(
						classLoader.getResourceAsStream(resourceRelativePath)
					)
				)
			) {
				StringBuffer result = new StringBuffer();
				String sCurrentLine;
				while ((sCurrentLine = reader.readLine()) != null) {
					result.append(sCurrentLine + "\n");
				}
				return result;
			}
		});
	}

	public URL getURL(ClassLoader resourceClassLoader, String fileName) {
		return Optional.ofNullable(
			resourceClassLoader
		).orElseGet(() -> ClassLoader.getSystemClassLoader()).getResource(
			fileName
		);
	}
	
}
