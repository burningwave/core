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

import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggersRepository;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.burningwave.core.function.Executor;

public class Strings implements ManagedLogger {
	
	public final Pattern PLACE_HOLDER_NAME_EXTRACTOR_PATTERN = Pattern.compile("\\$\\{([\\w\\d\\.\\:\\-]*)\\}");
	public final Pattern PLACE_HOLDER_EXTRACTOR_PATTERN = Pattern.compile("(\\$\\{[\\w\\d\\.\\:\\-]*\\})");

	private Strings() {}
	
	public static Strings create() {
		return new Strings();
	}
	
	public String capitalizeFirstCharacter(String value) {
		return Character.valueOf(value.charAt(0)).toString().toUpperCase()
		+ value.substring(1, value.length());
	}
	
	public boolean isBlank(String str) {
		int strLen;
		if ((str == null) || ((strLen = str.length()) == 0)) {
			return true;
		}	
		for (int i = 0; i < strLen; ++i) {
			if (!(Character.isWhitespace(str.charAt(i)))) {
				return false;
			}
		}
		return true;
	}	
	
	public boolean isNotBlank(String str) {
		return (!(isBlank(str)));
	}
	
	
	public boolean isEmpty(String str) {
		return ((str == null) || (str.length() == 0));
	}

	public boolean isNotEmpty(String str) {
		return (!(isEmpty(str)));
	}	 
	
	
	public boolean contains(String str, char searchChar) {
		if (isEmpty(str)) {
			return false;
		}
		return (str.indexOf(searchChar) >= 0);
	}
	
	
	public String strip(String str, String stripChars) {
		if (isEmpty(str)) {
			return str;
		}
		str = stripStart(str, stripChars);
		return stripEnd(str, stripChars);
	}

	
	public String stripStart(String str, String stripChars) {
		int strLen;
		if (str == null || (strLen = str.length()) == 0) {
			return str;
		}
		int start = 0;
		if (stripChars == null) {
			while (start != strLen && Character.isWhitespace(str.charAt(start))) {
				start++;
			}
		} else if (stripChars.length() == 0) {
			return str;
		} else {
			while (start != strLen
					&& stripChars.indexOf(str.charAt(start)) != -1) {
				start++;
			}
		}
		return str.substring(start);
	}

	public String stripEnd(String str, String stripChars) {
		int end;
		if (str == null || (end = str.length()) == 0) {
			return str;
		}

		if (stripChars == null) {
			while (end != 0 && Character.isWhitespace(str.charAt(end - 1))) {
				end--;
			}
		} else if (stripChars.length() == 0) {
			return str;
		} else {
			while (end != 0 && stripChars.indexOf(str.charAt(end - 1)) != -1) {
				end--;
			}
		}
		return str.substring(0, end);
	}
	public String lowerCaseFirstCharacter(String string) {
		return Character.toLowerCase(string.charAt(0)) + string.substring(1);
	}	
	
	public String replace(String text, Map<String, String> params) {
		AtomicReference<String> template = new AtomicReference<>(text);
		params.forEach((key, value) -> 
			template.set(
				template.get().replaceAll(
					key.replaceAll("\\$", "\\\\\\$")
					.replaceAll("\\{", "\\\\\\{")
					.replaceAll("\\}", "\\\\\\}"),
					value
				)
			)
		);
		return template.get();
	}
	
	
	public Map<Integer, List<String>> extractAllGroups(Pattern pattern, String target) {
		Matcher matcher = pattern.matcher(target);
		Map<Integer, List<String>> found = new LinkedHashMap<>();
		while (matcher.find()) {
			for (int i = 1; i <= matcher.groupCount(); i++) {
				try {
					List<String> foundString = null;
					if ((foundString = found.get(i)) == null) {
						foundString = new ArrayList<>();
						found.put(i, foundString);
					}					
					foundString.add(matcher.group(i));
				} catch (IndexOutOfBoundsException exc) {
					ManagedLoggersRepository.logInfo(getClass()::getName, "group " + i + " not found on string \"" + target + "\" using pattern " + pattern.pattern());
				}
			}
		}
		return found;
	}
	
	public String compile(String message, Object... arguments) {
		for (Object obj : arguments) {
			message = message.replaceFirst("\\{\\}", Objects.isNull(obj) ? "null" : clear(obj.toString()));
		}
		return message;
	}
	
	private String clear(String text) {
		return text
		.replace("\\", "\\\\\\")
		.replace("{", "\\{")
		.replace("}", "\\}")
		.replace("(", "\\(")
		.replace(")", "\\)")
		.replace(".", "\\.")
		.replace("$", "\\$");
	}
	
    public String from(StackTraceElement[] stackTrace) {
        return from(stackTrace, '\t', 1);
    }
    
    public String from(StackTraceElement[] stackTrace, int marginCount) {
        return from(stackTrace, '\t', marginCount);
    }
    
    public String from(StackTraceElement[] stackTrace, char marginChar, int marginCount) {
    	return from(Arrays.asList(stackTrace), marginChar, marginCount);
    }
    
    public String from(List<StackTraceElement> stackTrace) {
    	return from(stackTrace, '\t', 1);
    }
    
    public String from(List<StackTraceElement> stackTrace, int marginCount) {
    	return from(stackTrace, '\t', marginCount);
    }
    
    public String from(List<StackTraceElement> stackTrace, char marginChar, int marginCount) {
    	if (stackTrace.isEmpty()) {
    		return "";
    	}
    	String margin = new String(new char[marginCount]).replace('\0', marginChar);
    	return "\n" + margin + "at " + String.join("\n" + margin + "at ", stackTrace.stream().map(sTE -> sTE.toString()).collect(Collectors.toList()));
    }
	
	public String formatMessage(Throwable exc) {
		return exc.toString() + ": " + exc.getMessage();
	}
    
	public static class Paths {
		Function<String, String> pathCleaner;
		Function<String, String> uRLPathConverter;
		
		private Paths() {
			if (System.getProperty("os.name").toLowerCase().contains("windows")) {
				pathCleaner = (path) -> {
					path = path.replace("\\", "/");
					if (path.startsWith("/")) {
						path = path.substring(1);
					}
					if (path.endsWith("/")) {
						path = path.substring(0, path.length() - 1);
					}	
					return path.replaceAll("\\/{2,}", "/");
				};
				uRLPathConverter = this::convertURLPathToAbsolutePath0;
			} else {
				pathCleaner = (path) -> {
					path = path.replace("\\", "/").replaceAll("\\/{2,}", "/");
					if (path.endsWith("/") && path.length() > 1) {
						path = path.substring(0, path.length() - 1);
					}
					return path;
				};
				uRLPathConverter = this::convertURLPathToAbsolutePath1;
			}
		}
		
		public static Paths create() {
			return new Paths();
		}
		
		public String clean(String path) {
			return pathCleaner.apply(path);
		}
		
		public String toSquaredPath(String absolutePath, boolean isFolder) {
			String squaredPath = null;
			String root = absolutePath.indexOf("/") > 0?
				absolutePath.substring(0, absolutePath.indexOf(":")) :
				"root";
			if (absolutePath.chars().filter(ch -> ch == '/').count() > 1) {
				if (isFolder) {
					squaredPath = absolutePath.substring(absolutePath.indexOf("/")).replaceFirst("\\/", "\\[").replace("/", "][") + "]";
				} else {
					squaredPath = absolutePath.substring(absolutePath.indexOf("/")).replaceFirst("\\/", "\\[").replace("/", "][");
					squaredPath = squaredPath.substring(0, squaredPath.lastIndexOf("][") + 1) + squaredPath.substring(squaredPath.lastIndexOf("][") +2);
				}
			} else {
				squaredPath = absolutePath.substring(absolutePath.indexOf("/") + 1);
				if (isFolder) {
					squaredPath = "[" + squaredPath + "]";
				}
			}
			return "[" + root + "]" + squaredPath;
		}
		
		public String normalizeAndClean(String path) {
			if (path.contains("..") ||
				path.contains(".\\") ||
				path.contains(".//")
			) {
				path = java.nio.file.Paths.get(path).normalize().toString();
			}
			return clean(path);
		}
		
		public String getExtension(String path) {
			if (path.endsWith("/")) {
				return null;
			}
			if (path.contains("/")) {
				String name = path.substring(path.lastIndexOf("/") + 1);
				if (name.contains(".")) {
					return name.substring(name.lastIndexOf(".") + 1);
				}
				return null;
			}
			return null;
		}
		
		public String convertURLPathToAbsolutePath(String inputURLPath) {
			return uRLPathConverter.apply(inputURLPath);
		}
		
		private String convertURLPathToAbsolutePath0(String inputURLPath) {
			String absolutePath = Executor.get(() ->
				URLDecoder.decode(
					inputURLPath, StandardCharsets.UTF_8.name()
				)
			);
			absolutePath = removeInitialPathElements(absolutePath,
				"jar:",
				"zip:",
				"file:", 
				//Patch for tomcat 7
				"!"
			);
			
			if (absolutePath.startsWith("/")) {
				absolutePath = absolutePath.substring(1);
			}
			
			absolutePath = absolutePath.replaceAll("\\.(.*?)!\\/", "\\.$1\\/");
			
			if (absolutePath.endsWith("/")) {
				absolutePath = absolutePath.substring(0, absolutePath.length() - 1);
			}
			
			return absolutePath;
		}
		
		private String convertURLPathToAbsolutePath1(String inputURLPath) {
			String absolutePath = Executor.get(() ->
				URLDecoder.decode(
					inputURLPath, StandardCharsets.UTF_8.name()
				)
			);
			absolutePath = removeInitialPathElements(absolutePath,
				"jar:",
				"zip:",
				"file:", 
				//Patch for tomcat 7
				"!"
			);
			
			absolutePath = absolutePath.replaceAll("\\.(.*?)!\\/", "\\.$1\\/");
			return absolutePath;
		}
		
		public String removeInitialPathElements(String path, String... toRemove) {
			if (toRemove != null && toRemove.length > 0) {
				for (int i = 0; i < toRemove.length; i++) {
					if (path.startsWith(toRemove[i])) {
						path = path.substring(
							path.indexOf(toRemove[i]) + toRemove[i].length(), 
							path.length());
					}
				}
			}
			return path;
		}
	}


	public boolean areEquals(String string1, String string2) {
		return (isEmpty(string1) && isEmpty(string2)) || 
			(isNotEmpty(string1) && isNotEmpty(string2) && string1.equals(string2));
	}

	public String placeHolderToRegEx(String value) {
		return value.replace("$", "\\$").replace(".", "\\.").replace("{", "\\{").replace("}", "\\}");
	}

}
