package org.burningwave.core.concurrent;

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
