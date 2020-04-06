package org.burningwave.core.io;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Optional;

import org.burningwave.core.function.ThrowingSupplier;

public class Resources {
	
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

	public URL get(ClassLoader resourceClassLoader, String fileName) {
		return Optional.ofNullable(
			resourceClassLoader
		).orElseGet(() -> ClassLoader.getSystemClassLoader()).getResource(
			fileName
		);
	}
	
}
