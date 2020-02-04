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
package org.burningwave.core.extension.concurrent;

import java.util.concurrent.atomic.AtomicBoolean;

import org.burningwave.core.Component;

public class Cycler implements Component {
	
	public abstract static class Runnable implements java.lang.Runnable, Component{
		Cycler.Thread thread;
		
		void setThread(Cycler.Thread thread) {
			this.thread = thread;
		}
		
		public Cycler.Thread getThread() {
			return this.thread;
		}
	}
	
	public static class Thread extends java.lang.Thread {
		protected final AtomicBoolean continueLoop = new AtomicBoolean();			
		protected Runnable function;
		
		public Thread(Runnable function, String name, int priority) {
			this.function = function;
			function.setThread(this);
			setName(name);
			setPriority(priority);
		}
		
		public void terminate() {
			continueLoop.set(false);
		}
		
		@Override
		public synchronized void start() {
			continueLoop.set(true);
			super.start();
		}
		
		@Override
		public void run() {
			while (continueLoop.get()) {
				function.run();
			}
		}
	}
}
