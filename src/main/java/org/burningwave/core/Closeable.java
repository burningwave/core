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

import static org.burningwave.core.assembler.StaticComponentContainer.BackgroundExecutor;
import static org.burningwave.core.assembler.StaticComponentContainer.Objects;

import java.util.Optional;
import java.util.function.Supplier;

import org.burningwave.core.concurrent.QueuedTasksExecutor.Task;
import org.burningwave.core.function.ThrowingRunnable;

public interface Closeable extends AutoCloseable {
	
	@Override
	default public void close() {
			
	}
	
	default public Task createCloseResoucesResources(Supplier<Boolean> isClosedPredicate, ThrowingRunnable<?> closingFunction) {
		return BackgroundExecutor.createTask(closingFunction, Thread.MIN_PRIORITY).runOnlyOnce(Objects.getId(this) + "->" + "closeResources", isClosedPredicate);
	}
	
	default public Task closeResources(Supplier<Boolean> isClosedPredicate, ThrowingRunnable<?> closingFunction) {
		return Optional.ofNullable(createCloseResoucesResources(isClosedPredicate, closingFunction).submit()).orElseGet(() -> null);
	}
	
}
