package org.burningwave.core.iterable;

import java.util.Collection;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

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
@SuppressWarnings("unchecked")
class IterationConfigImpl<I, D> implements IterableObjectHelper.IterationConfig<I, D, IterationConfigImpl<I, D>>{
	Object items;
	Object action;
	Object output;
	Predicate<D> predicateForParallelIteration;
	Integer priority;

	public IterationConfigImpl(
		Object items
	) {
		if (items == null) {
			throw new IllegalArgumentException("Input collection could not be null");
		}
		this.items = items;
	}

	public <O> IterationConfigImpl<I, D> withAction(BiConsumer<I, Consumer<Consumer<O>>> action) {
		this.action = action;
		return this;
	}

	@Override
	public IterationConfigImpl<I, D> withAction(Consumer<I> action) {
		BiConsumer<I, Consumer<Consumer<?>>> newAction = (item, outputItemCollector) -> action.accept(item);
		this.action = newAction;
		return this;
	}

	@Override
	public IterationConfigImpl<I, D> withPriority(Integer priority) {
		this.priority = priority;
		return this;
	}

	@Override
	public IterationConfigImpl<I, D> parallelIf(Predicate<D> predicate) {
		this.predicateForParallelIteration = predicate;
		return this;
	}

	IterationConfigImpl<I, D> setOutput(Object output) {
		if (this.output != null) {
			throw new IllegalArgumentException("Could not set output twice");
		}
		this.output = output;
		return this;
	}
	
	@Override
	public <O> WithOutputOfCollection<I, D, O> withOutput(Collection<O> output) {
		return new WithOutputOfCollection<>(setOutput(output));
	}

	@Override
	public <K, O> WithOutputOfMap<I, D, K, O> withOutput(Map<K, O> output) {
		return new WithOutputOfMap<>(setOutput(output));
	}

	static abstract class WithOutput<I, D, W extends WithOutput<I, D, W>> implements IterableObjectHelper.IterationConfig<I, D, W> {
		IterationConfigImpl<I, D> wrappedConfiguration;

		WithOutput(IterationConfigImpl<I, D> configuration) {
			this.wrappedConfiguration = configuration;
		}

		@Override
		public W withAction(Consumer<I> action) {
			wrappedConfiguration.withAction(action);
			return (W)this;
		}

		@Override
		public <O> WithOutputOfCollection<I, D, O> withOutput(Collection<O> output) {
			wrappedConfiguration.setOutput(output);
			return new WithOutputOfCollection<>(wrappedConfiguration);
		}

		@Override
		public <K, O> WithOutputOfMap<I, D, K, O> withOutput(Map<K, O> output) {
			wrappedConfiguration.setOutput(output);
			return new WithOutputOfMap<>(wrappedConfiguration);
		}

		@Override
		public W parallelIf(Predicate<D> predicate) {
			wrappedConfiguration.parallelIf(predicate);
			return (W)this;
		}

		@Override
		public W withPriority(Integer priority) {
			wrappedConfiguration.withPriority(priority);
			return (W)this;
		}

		IterationConfigImpl<I, D> getWrappedConfiguration() {
			return wrappedConfiguration;
		}
	}

}
