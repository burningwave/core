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

import static org.burningwave.core.assembler.StaticComponentContainer.Strings;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.burningwave.core.Component;


public class SourceCodeHandler implements Component {
	
	private SourceCodeHandler() {}
	
	public static SourceCodeHandler create() {
		return new SourceCodeHandler();
	}
	
	public String extractClassName(String classCode) {
		return
			Optional.ofNullable(
				Strings.extractAllGroups(
					Pattern.compile("(package)\\s*([[a-zA-Z0-9\\s]*\\.?]*)"), classCode
				).get(2).get(0)
			).map(
				value -> value + "."
			).orElse("") +
			Strings.extractAllGroups(
				Pattern.compile("(?<=\\n|\\A)(?:public\\s*)?(class|interface|enum)\\s*([^\\n\\s<]*)"), classCode
			).get(2).get(0);
	}
	
	public Collection<String> extractImports(String classCode) {
		Collection<String> imports = Strings.extractAllGroups(
			Pattern.compile("import\\s+(.*?)\\s*;"), classCode
		).get(1);
		Collection<String> finalImports = new HashSet<>();
		for (String className : imports) {
			if (className.startsWith("static")) {
				className = className.replaceAll("static\\s+", "");
				className = className.substring(0, className.lastIndexOf("."));
			}
			finalImports.add(className);
		}
		
		return finalImports;
	}
	
	public Collection<String> addLineCounter(Collection<String> sources) {
		return sources.stream().map(source -> addLineCounter(source)).collect(Collectors.toList());
	}
	
	public String addLineCounter(String source) {
		StringBuffer newSource = new StringBuffer();
		String[] lines = source.split("\n");
		int maxDigitCount = 0;
		int temp = lines.length;
		while(temp > 0) {
			temp = temp / 10;
			++maxDigitCount; 
		}
		for (int lineCounter = 1; lineCounter <= lines.length; lineCounter++) {
			newSource.append(String.format(" %0" + maxDigitCount + "d", lineCounter) + " | \t" + lines[lineCounter - 1] + "\n");
		}		
		return newSource.substring(0, newSource.length() -1);
	}
	
	
	@Override
	public void close() {}
}