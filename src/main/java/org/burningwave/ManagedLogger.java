
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
 * Copyright (c) 2020 Roberto Gentili
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
package org.burningwave;


public interface ManagedLogger {
		
	@SuppressWarnings("unchecked")
	public default <T extends ManagedLogger> T disableLogging() {
		return (T) this;
	}
	
	@SuppressWarnings("unchecked")
	public default <T extends ManagedLogger> T enableLogging() {
		return (T) this;
	}
	
	default void logError(String message, Throwable exc) {
		Repository.logError(this.getClass(), message, exc);
	}
	
	default void logError(String message) {
		Repository.logError(this.getClass(), message);
	}
	
	default void logDebug(String message) {
		Repository.logDebug(this.getClass(), message);
	}
	
	default void logDebug(String message, Object... arguments) {
		Repository.logDebug(this.getClass(), message, arguments);
	}
	
	default void logInfo(String message) {
		Repository.logInfo(this.getClass(), message);
	}
	
	default void logInfo(String message, Object... arguments) {
		Repository.logInfo(this.getClass(), message, arguments);
	}
	
	default void logWarn(String message) {
		Repository.logWarn(this.getClass(), message);
	}
	
	default void logWarn(String message, Object... arguments) {
		Repository.logWarn(this.getClass(), message, arguments);
	}
	
	
	public static class Repository {
		
		public static void disableLogging(Class<?> client) {}
		
		public static void enableLogging(Class<?> client) {}
		
		public static void logError(Class<?> client, String message, Throwable exc) {
			System.err.println(client.getName() + " - " + message + " - " + exc.getMessage());
		}
		
		public static void logError(Class<?> client, String message) {
			System.err.println(client.getName() + " - " + message);
		}
		
		public static void logDebug(Class<?> client, String message) {
			System.out.println(client.getName() + " - " + message);
		}
		
		public static void logDebug(Class<?> client, String message, Object... arguments) {
			message = replacePlaceHolder(message, arguments);
			System.out.println(client.getName() + " - " + message);
		}
		
		public static void logInfo(Class<?> client, String message) {
			System.out.println(client.getName() + " - " + message);
		}
		
		public static void logInfo(Class<?> client, String message, Object... arguments) {
			message = replacePlaceHolder(message, arguments);
			System.out.println(client.getName() + " - " + message);
		}
		
		public static void logWarn(Class<?> client, String message) {
			System.out.println(client.getName() + " - " + message);
		}
		
		public static void logWarn(Class<?> client, String message, Object... arguments) {
			message = replacePlaceHolder(message, arguments);
			System.out.println(client.getName() + " - " + message);
		}
		
		private static String replacePlaceHolder(String message, Object... arguments) {
			for (Object obj : arguments) {
				message = message.replaceFirst("\\{\\}", clear(obj.toString()));
			}
			return message;
		}
		
		private static String clear(String text) {
			return text
			.replace("\\", "\\")
			.replace("{", "\\{")
			.replace("}", "\\}")
			.replace("(", "\\(")
			.replace(")", "\\)")
			.replace(".", "\\.")
			.replace("$", "\\$");
		}
	}
}
