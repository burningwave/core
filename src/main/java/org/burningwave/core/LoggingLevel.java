package org.burningwave.core;

public class LoggingLevel {
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
	
	Integer flags;
	
	public LoggingLevel(int flags){
		this.flags = flags;
	}
	
	public boolean matchPartialy(Integer flags) {
		return this.flags == 0 && flags == 0 || (this.flags & flags) != 0;
	}
	
	public boolean matchPartialy(LoggingLevel level) {
		return matchPartialy(level.flags);
	}	
	
	public static class Mutable extends LoggingLevel{

		public Mutable(int flags) {
			super(flags);
		}
		
		public void add(Integer level) {
			this.flags |= flags;
		}
		
		public void remove(Integer flags) {
			this.flags = (this.flags | flags) ^ flags;
		}
		
		public void set(Integer flags) {
			this.flags = flags;
		}
	}
}