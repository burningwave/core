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

class LoggingLevel {
	public final static int ALL_LEVEL_ENABLED = 0b11111;
	public final static int ALL_LEVEL_DISABLED = 0b00000;
	public final static int TRACE_ENABLED = 0b00001;
	public final static int DEBUG_ENABLED = 0b00010;
	public final static int INFO_ENABLED = 0b00100;
	public final static int WARN_ENABLED = 0b01000;
	public final static int ERROR_ENABLED = 0b10000;
	
	public final static LoggingLevel TRACE = new LoggingLevel(TRACE_ENABLED);
	public final static LoggingLevel DEBUG = new LoggingLevel(DEBUG_ENABLED);
	public final static LoggingLevel INFO = new LoggingLevel(INFO_ENABLED);
	public final static LoggingLevel WARN = new LoggingLevel(WARN_ENABLED);
	public final static LoggingLevel ERROR = new LoggingLevel(ERROR_ENABLED);
	private final static LoggingLevel ALL_LEVEL = new LoggingLevel(ALL_LEVEL_ENABLED);
	
	public static enum Label {
		TRACE, DEBUG, INFO, WARN, ERROR
	}
	
	Integer flags;
	
	LoggingLevel(int flags){
		this.flags = flags;
	}
	
	boolean matchPartialy(Integer flags) {
		return (this.flags & flags) != 0;
	}
	
	boolean partialyMatch(LoggingLevel level) {
		return matchPartialy(level.flags);
	}
	
	static LoggingLevel fromLabel(String label) {
		if (label.toLowerCase().contains(Label.TRACE.name().toLowerCase())) {
			return TRACE;
		} else if (label.toLowerCase().contains(Label.DEBUG.name().toLowerCase())) {
			return DEBUG;
		} else if (label.toLowerCase().contains(Label.INFO.name().toLowerCase())) {
			return INFO;
		} else if (label.toLowerCase().contains(Label.WARN.name().toLowerCase())) {
			return WARN;
		} else if (label.toLowerCase().contains(Label.ERROR.name().toLowerCase())) {
			return ERROR;
		} else if (label.toLowerCase().contains("all-levels")) {
			return ALL_LEVEL;
		}
		return null;
	}
	
	static class Mutable extends LoggingLevel{

		Mutable(int flags) {
			super(flags);
		}
		
		void add(Integer flags) {
			this.flags |= flags;
		}
		
		void remove(Integer flags) {
			this.flags &= ALL_LEVEL_ENABLED ^ flags;
		}
		
		void set(Integer flags) {
			this.flags = flags;
		}
	}
	
	
}