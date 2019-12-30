package org.burningwave.core.io;

public enum BufferSize {
	BYTE(1),
	BYTE_256(256),
	BYTE_512(512),
	BYTE_768(768),
    KILO_BYTE(1024),
    MEGA_BYTE(KILO_BYTE.value * KILO_BYTE.value);
    
	private long value;

    private BufferSize(long value) {
    	this.value = value;
    }        
    public long getValue() {
    	return value;
    }
}