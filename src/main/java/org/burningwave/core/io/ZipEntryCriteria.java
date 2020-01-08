/*
 * This file is part of Burningwave Core.
 *
 * Author: Roberto Gentli
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
package org.burningwave.core.io;

import java.util.function.Predicate;

import org.burningwave.core.Criteria;

public class ZipEntryCriteria extends Criteria<ZipInputStream.Entry, ZipEntryCriteria, Criteria.TestContext<ZipInputStream.Entry, ZipEntryCriteria>>{
	
	private ZipEntryCriteria() {}
	
	public static ZipEntryCriteria create() {
		return new ZipEntryCriteria();
	}
	
	public ZipEntryCriteria absolutePath(final Predicate<String> predicate) {
		this.predicate = concat(
			this.predicate,
			(context, zipEntry) -> predicate.test(zipEntry.getAbsolutePath())
		);
		return this;
	}
	
	
	public ZipEntryCriteria name(final Predicate<String> predicate) {
		this.predicate = concat(
			this.predicate,
			(context, zipEntry) -> predicate.test(zipEntry.getName())
		);
		return this;
	}
	
}
