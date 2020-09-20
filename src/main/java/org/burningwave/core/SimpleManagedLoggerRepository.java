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

import static org.burningwave.core.assembler.StaticComponentContainer.Synchronizer;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.burningwave.core.ManagedLogger.Repository;

public class SimpleManagedLoggerRepository extends Repository.Abst {
	private Map<String, LoggingLevel.Mutable> loggers;
	
	public SimpleManagedLoggerRepository(Properties properties) {
		super(properties);
	}
	
	
	@Override
	void initSpecificElements(Properties properties) {
		loggers = new HashMap<>();
	}
	
	@Override
	void resetSpecificElements() {
		loggers.clear();		
	}
	
	private LoggingLevel.Mutable getLoggerEnabledFlag(String clientName) {
		LoggingLevel.Mutable loggerEnabledFlag = loggers.get(clientName);
		if (loggerEnabledFlag == null) {
			loggerEnabledFlag = Synchronizer.execute(instanceId + "_" + clientName, () -> {
				LoggingLevel.Mutable loggerEnabledFlagTemp = loggers.get(clientName);
				if (loggerEnabledFlagTemp == null) {
					loggers.put(clientName, loggerEnabledFlagTemp = new LoggingLevel.Mutable(LoggingLevel.ALL_LEVEL_ENABLED));
				}
				return loggerEnabledFlagTemp;
			});
		}
		return loggerEnabledFlag;
	}
	
	@Override
	public void setLoggingLevelFlags(Class<?> cls, Integer flags) {
		getLoggerEnabledFlag(cls.getName()).set(flags);
	}

	@Override
	public Integer getLoggingLevelFlags(Class<?> cls) {
		return getLoggerEnabledFlag(cls.getName()).flags;
	}
	
	@Override
	public void addLoggingLevelFor(LoggingLevel logLevel, String... classNames) {
		for (String className : classNames) {
			getLoggerEnabledFlag(className).add(logLevel.flags);
		}		
	}

	@Override
	public void removeLoggingLevelFor(LoggingLevel logLevel, String... classNames) {
		for (String className : classNames) {
			getLoggerEnabledFlag(className).remove(logLevel.flags);
		}	
	}
	
	@Override
	public void setLoggingLevelFor(LoggingLevel logLevel, String... classNames) {
		for (String className : classNames) {
			getLoggerEnabledFlag(className).set(logLevel.flags);
		}
	}
	
	private void setLoggerEnabledFlag(String client, LoggingLevel level) {
		loggers.put(client, new LoggingLevel.Mutable(level.flags));
	}

	private void log(LoggingLevel level, PrintStream printStream, String text, Throwable exception) {
		if (!isEnabled) {
			return;
		}
		StackTraceElement stackTraceElement = Thread.currentThread().getStackTrace()[4];
		String clientName = stackTraceElement.getClassName();
		if (getLoggerEnabledFlag(clientName).partialyMatch(level)) {
			if (exception == null) {
				printStream.println("[" + Thread.currentThread().getName() + "] - " + clientName + " - " + addDetailsToMessage(text, stackTraceElement));
			} else {
				printStream.println("[" + Thread.currentThread().getName() + "] - " + clientName + " - " + addDetailsToMessage(text, stackTraceElement));
				exception.printStackTrace(printStream);
			}
		}
	}
	
	@Override
	public void disableLogging(String clientName) {
		setLoggerEnabledFlag(clientName, new LoggingLevel.Mutable(LoggingLevel.ALL_LEVEL_DISABLED));
	}
	@Override
	public void enableLogging(String clientName) {
		setLoggerEnabledFlag(clientName, new LoggingLevel.Mutable(LoggingLevel.ALL_LEVEL_ENABLED));
	}
	
	@Override
	public void logError(String message, Throwable exc) {
		log(LoggingLevel.ERROR, System.err, message, exc);
	}
	
	@Override
	public void logError(String message) {
		log(LoggingLevel.ERROR, System.err, message, null);
	}
	
	@Override
	public void logDebug(String message) {
		log(LoggingLevel.DEBUG, System.out, message, null);
	}
	
	@Override
	public void logDebug(String message, Object... arguments) {
		log(LoggingLevel.DEBUG, System.out, replacePlaceHolder(message, arguments), null);
	}
	
	@Override
	public void logInfo(String message) {
		log(LoggingLevel.INFO, System.out, message, null);
	}
	
	@Override
	public void logInfo(String message, Object... arguments) {
		log(LoggingLevel.INFO, System.out, replacePlaceHolder(message, arguments), null);
	}
	
	@Override
	public void logWarn(String message) {
		log(LoggingLevel.WARN, System.out, message, null);
	}
	
	@Override
	public void logWarn(String message, Object... arguments) {
		log(LoggingLevel.WARN, System.out, replacePlaceHolder(message, arguments), null);
	}
	
	@Override
	public void logTrace(String message) {
		log(LoggingLevel.TRACE, System.out, message, null);
	}

	@Override
	public void logTrace(String message, Object... arguments) {
		log(LoggingLevel.TRACE, System.out, replacePlaceHolder(message, arguments), null);
	}
	
	private String replacePlaceHolder(String message, Object... arguments) {
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
	
	@Override
	public void close() {
		this.loggers.clear();
		super.close();
	}
}
