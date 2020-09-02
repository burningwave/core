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

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;

import org.burningwave.core.ManagedLogger.Repository;
import org.burningwave.core.concurrent.Mutex;

public class SimpleManagedLoggerRepository extends Repository.Abst {
	private Map<String, LoggingLevel.Mutable> loggers;
	private Mutex.Manager mutexManager;
	public SimpleManagedLoggerRepository(Properties properties) {
		super(properties);
	}
	
	
	@Override
	void initSpecificElements(Properties properties) {
		loggers = new HashMap<>();
		mutexManager = Mutex.Manager.create();
	}
	
	@Override
	void resetSpecificElements() {
		loggers.clear();		
	}
	
	private LoggingLevel.Mutable getLoggerEnabledFlag(String clientName) {
		LoggingLevel.Mutable loggerEnabledFlag = loggers.get(clientName);
		if (loggerEnabledFlag == null) {
			synchronized (mutexManager.getMutex(clientName)) {
				loggerEnabledFlag = loggers.get(clientName);
				if (loggerEnabledFlag == null) {
					loggers.put(clientName, loggerEnabledFlag = new LoggingLevel.Mutable(LoggingLevel.ALL_LEVEL_ENABLED));
				}
			}
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

	private void log(Supplier<String> clientNameSupplier, LoggingLevel level, PrintStream printStream, Supplier<String> textSupplier, Throwable exception) {
		if (!isEnabled) {
			return;
		}
		String clientName = clientNameSupplier.get();
		if (getLoggerEnabledFlag(clientName).partialyMatch(level)) {
			if (exception == null) {
				printStream.println("[" + Thread.currentThread().getName() + "] - " + clientName + " - " + textSupplier.get());
			} else {
				printStream.println("[" + Thread.currentThread().getName() + "] - " + clientName + " - " + textSupplier.get());
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
	void logError(Supplier<String> clientNameSupplier, Supplier<String> messageSupplier, Throwable exc) {
		log(clientNameSupplier,LoggingLevel.ERROR, System.err, messageSupplier, exc);
	}
	
	@Override
	void logError(Supplier<String> clientNameSupplier, Supplier<String> messageSupplier) {
		log(clientNameSupplier,LoggingLevel.ERROR, System.err, messageSupplier, null);
	}
	
	@Override
	void logDebug(Supplier<String> clientNameSupplier, Supplier<String> messageSupplier) {
		log(clientNameSupplier,LoggingLevel.DEBUG, System.out, messageSupplier, null);
	}
	
	@Override
	void logDebug(Supplier<String> clientNameSupplier, Supplier<String> messageSupplier, Object... arguments) {
		log(clientNameSupplier,LoggingLevel.DEBUG, System.out, () -> replacePlaceHolder(messageSupplier.get(), arguments), null);
	}
	
	@Override
	void logInfo(Supplier<String> clientNameSupplier, Supplier<String> messageSupplier) {
		log(clientNameSupplier,LoggingLevel.INFO, System.out, messageSupplier, null);
	}
	
	@Override
	void logInfo(Supplier<String> clientNameSupplier, Supplier<String> messageSupplier, Object... arguments) {
		log(clientNameSupplier,LoggingLevel.INFO, System.out, () -> replacePlaceHolder(messageSupplier.get(), arguments), null);
	}
	
	@Override
	void logWarn(Supplier<String> clientNameSupplier, Supplier<String> messageSupplier) {
		log(clientNameSupplier,LoggingLevel.WARN, System.out, messageSupplier, null);
	}
	
	@Override
	void logWarn(Supplier<String> clientNameSupplier, Supplier<String> messageSupplier, Object... arguments) {
		log(clientNameSupplier,LoggingLevel.WARN, System.out, () -> replacePlaceHolder(messageSupplier.get(), arguments), null);
	}
	
	@Override
	void logTrace(Supplier<String> clientNameSupplier, Supplier<String> messageSupplier) {
		log(clientNameSupplier,LoggingLevel.TRACE, System.out, messageSupplier, null);
	}

	@Override
	void logTrace(Supplier<String> clientNameSupplier, Supplier<String> messageSupplier, Object... arguments) {
		log(clientNameSupplier,LoggingLevel.TRACE, System.out, () -> replacePlaceHolder(messageSupplier.get(), arguments), null);
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
	
}
