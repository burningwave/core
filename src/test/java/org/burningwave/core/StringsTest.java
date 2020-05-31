package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.Paths;
import static org.burningwave.core.assembler.StaticComponentContainer.Strings;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

public class StringsTest extends BaseTest {
	
	@Test
	public void extractTestOne() {
		testNotNull(() ->
			Strings.extractAllGroups(Pattern.compile("\\$\\{([\\w\\d\\.]*)\\}([\\w]*)"), "${${ciao.Asdf.1}prova${ciao}}").get(1).get(0)
		);
	}
	
	@Test
	public void convertURLTestOne() {
		testNotNull(() ->
			Paths.convertURLPathToAbsolutePath(this.getClass().getClassLoader().getResource(java.util.regex.Pattern.class
				.getName().replace(".", "/")+ ".class").toString())
		);
	}
	
	
	@Test
	public void stripTest() {
		assertTrue(!Strings.contains(Strings.strip(" Hello! ", " "), ' '));
	}
	
	
	@Test
	public void replaceTest() {
		Map<String, String> parameters = new HashMap<>();
		parameters.put("${firstParameter}", "firstParameter");
		parameters.put("${secondParameter}", "secondParameter");
		assertTrue(!Strings.contains(Strings.replace("${firstParameter},${secondParameter}", parameters), '$'));
	}
}
