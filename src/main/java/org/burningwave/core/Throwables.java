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
 * Copyright (c) 2019-2021 Roberto Gentili
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

import static org.burningwave.core.assembler.StaticComponentContainer.Strings;


@SuppressWarnings("unchecked")
public class Throwables {

	public static <T> T throwException(String message, Object... placeHolderReplacements) {
		Throwable exception = null;
		if (placeHolderReplacements == null || placeHolderReplacements.length == 0) {
			exception = new Exception(message);
		} else {
			exception = new Exception(Strings.compile(message, placeHolderReplacements));
		}
		StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
		StackTraceElement[] stackTraceOfException = new StackTraceElement[stackTrace.length - 2];
		System.arraycopy(stackTrace, 2, stackTraceOfException, 0, stackTraceOfException.length);
		org.burningwave.core.Throwables.throwException0(exception);
		return null;
	}

	public static <T> T throwException(Throwable exception) {
		StackTraceElement[] stackTrace = exception.getStackTrace();
		StackTraceElement[] stackTraceOfException = new StackTraceElement[stackTrace.length + 1];
		stackTraceOfException[0] = Thread.currentThread().getStackTrace()[2];
		System.arraycopy(stackTrace, 0, stackTraceOfException, 1, stackTrace.length);
		exception.setStackTrace(stackTraceOfException);
		org.burningwave.core.Throwables.throwException0(exception);
		return null;
	}

	private static <E extends Throwable> E throwException0(Throwable exc) throws E {
		throw (E)exc;
	}

}
