package org.burningwave.core;

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
	void init(Properties properties) {
		loggers = new HashMap<>();		
	}
	
	private LoggingLevel.Mutable getLoggerEnabledFlag(String clientName) {
		LoggingLevel.Mutable loggerEnabledFlag = loggers.get(clientName);
		if (loggerEnabledFlag == null) {
			synchronized (getId(loggers, clientName)) {
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

	private void log(Class<?> client, LoggingLevel level, PrintStream printStream, String text, Throwable exception) {
		if (!isEnabled) {
			return;
		}
		if (getLoggerEnabledFlag(client.getName()).partialyMatch(level)) {
			if (exception == null) {
				printStream.println(client.getName() + " - " + text);
			} else {
				printStream.println(client.getName() + " - " + text);
				exception.printStackTrace(printStream);
			}
		}
	}
	
	public void disableLogging(Class<?> client) {
		setLoggerEnabledFlag(client.getName(), new LoggingLevel.Mutable(LoggingLevel.ALL_LEVEL_DISABLED));
	}
	
	public void enableLogging(Class<?> client) {
		setLoggerEnabledFlag(client.getName(), new LoggingLevel.Mutable(LoggingLevel.ALL_LEVEL_ENABLED));
	}
	
	public void logError(Class<?> client, String message, Throwable exc) {
		log(client, LoggingLevel.ERROR, System.err, message, exc);
	}

	public void logError(Class<?> client, String message) {
		log(client, LoggingLevel.ERROR, System.err, message, null);
	}
	
	public void logDebug(Class<?> client, String message) {
		log(client, LoggingLevel.DEBUG, System.out, message, null);
	}
	
	public void logDebug(Class<?> client, String message, Object... arguments) {
		message = replacePlaceHolder(message, arguments);
		log(client, LoggingLevel.DEBUG, System.out, message, null);
	}
	
	public void logInfo(Class<?> client, String message) {
		log(client, LoggingLevel.INFO, System.out, message, null);
	}
	
	public void logInfo(Class<?> client, String message, Object... arguments) {
		message = replacePlaceHolder(message, arguments);
		log(client, LoggingLevel.INFO, System.out, message, null);
	}
	
	public void logWarn(Class<?> client, String message) {
		log(client, LoggingLevel.WARN, System.out, message, null);
	}
	
	public void logWarn(Class<?> client, String message, Object... arguments) {
		message = replacePlaceHolder(message, arguments);
		log(client, LoggingLevel.WARN, System.out, message, null);
	}
	
	@Override
	public void logTrace(Class<?> client, String message) {
		log(client, LoggingLevel.TRACE, System.out, message, null);
	}

	@Override
	public void logTrace(Class<?> client, String message, Object... arguments) {
		message = replacePlaceHolder(message, arguments);
		log(client, LoggingLevel.TRACE, System.out, message, null);
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
