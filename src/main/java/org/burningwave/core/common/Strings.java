package org.burningwave.core.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Strings {
	
	public static boolean isBlank(String str) {
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
	
	
	public static boolean isNotBlank(String str) {
		return (!(isBlank(str)));
	}
	
	
	public static boolean isEmpty(String str) {
		return ((str == null) || (str.length() == 0));
	}


	public static boolean isNotEmpty(String str) {
		return (!(isEmpty(str)));
	}	 
	
	
	public static boolean contains(String str, char searchChar) {
		if (isEmpty(str)) {
			return false;
		}
		return (str.indexOf(searchChar) >= 0);
	}
	
	
	public static String strip(String str, String stripChars) {
		if (isEmpty(str)) {
			return str;
		}
		str = stripStart(str, stripChars);
		return stripEnd(str, stripChars);
	}

	
	public static String stripStart(String str, String stripChars) {
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

	public static String stripEnd(String str, String stripChars) {
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
	public static String lowerCaseFirstCharacter(String string) {
		return Character.toLowerCase(string.charAt(0)) + string.substring(1);
	}	
	
	public static String replace(String text, Map<String, String> params) {
		AtomicReference<String> template = new AtomicReference<String>(text);
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
	
	
	public static Map<Integer, List<String>> extractAllGroups(Pattern pattern, String target) {
		Matcher matcher = pattern.matcher(target);
		Map<Integer, List<String>> found = new LinkedHashMap<>();
		while (matcher.find()) {
			for (int i = 1; i <= matcher.groupCount(); i++) {
				try {
					List<String> foundString = null;
					if ((foundString = found.get(i)) == null) {
						foundString = new ArrayList<String>();
						found.put(i, foundString);
					}					
					foundString.add(matcher.group(i));
				} catch (IndexOutOfBoundsException exc) {
					LoggersRepository.logDebug(Strings.class, "group " + i + " not found on string \"" + target + "\" using pattern " + pattern.pattern());
				}
			}
		}
		return found;
	}
	
	
	public static class Paths {
		
		public static String clean(String path) {
			path = Strings.strip(path, " ");
			path = path.replace("\\", "/");
			// ON LINUX UNIX FIRST SLASH CHARACTER MUST NOT BE REMOVED.
			if (System.getProperty("os.name").toLowerCase().contains("windows") && path.startsWith("/")) {
				path = path.substring(1);
			}
			// ... AND FINAL TOO
			if (System.getProperty("os.name").toLowerCase().contains("windows") && path.endsWith("/")) {
				path = path.substring(0, path.length() - 1);
			}	
			return path.replaceAll("\\/{2,}", "/");
		}
		
		
		public static String uniform(String path) {
			path = removeInitialPathElements(path, 
					"file:", 
					//Patch for tomcat 7
					"!");
			if (path.contains("\\")) {
				path = path.replace("\\", "/");
			}		
			if (!path.startsWith("/")) {
				path = "/" + path;
			}
			if (!path.endsWith("/")) {
				path = path + "/";
			}
			if (path.contains("%20")) {
				path = path.replace("%20", " ");
			}
			return path;
		}
		
		public static String removeInitialPathElements(String path, String... toRemove) {
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
}
