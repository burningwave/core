package org.burningwave.core.io;

import java.io.InputStream;
import java.io.Serializable;

public class ClosedInputStream extends InputStream implements Serializable {

	private static final long serialVersionUID = -6104755286005190458L;
	
	public static final ClosedInputStream CLOSED_INPUT_STREAM = new ClosedInputStream();

    @Override
    public int read() {
        return -1;
    }

}