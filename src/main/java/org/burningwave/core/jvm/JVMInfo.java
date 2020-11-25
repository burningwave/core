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
package org.burningwave.core.jvm;

public class JVMInfo {

    private boolean is64Bit;
    private boolean is32Bit;
    private int version;
    
    public JVMInfo() {
    	init();
    }
    
    public static JVMInfo create() {
    	return new JVMInfo();
    }
    
    private void init() {
    	String version = System.getProperty("java.version");
        if(version.startsWith("1.")) {
        	version = version.substring(2, 3);
        } else {
        	int separatorIdx = version.indexOf(".");
        	if(separatorIdx != -1) {
        		version = version.substring(0, separatorIdx);
        	} else {
        		separatorIdx = version.indexOf("-");
        		if(separatorIdx != -1) {
            		version = version.substring(0, separatorIdx);
            	}
        	}
        }
        this.version = Integer.parseInt(version);
        final String arch = System.getProperty("sun.arch.data.model");
        String osArch = System.getProperty("os.arch");
        if (arch != null) {
        	is64Bit =  arch.contains("64");
            is32Bit = arch.contains("32");
        } else {
            if (osArch != null && osArch.contains("64")) {
                is64Bit = true;
            } else {
                is64Bit = false;
            }
        }
    }

    public boolean is32Bit() {
    	return is32Bit;
    }
    
    public boolean is64Bit() {
    	return is64Bit;
    }
    
    public int getVersion() {
    	return version;
    }
}