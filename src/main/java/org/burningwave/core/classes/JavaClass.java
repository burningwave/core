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
package org.burningwave.core.classes;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Optional;

import org.burningwave.Throwables;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.Streams;

public class JavaClass {
	private final ByteBuffer byteCode;
	private final String className;
	
	JavaClass(ByteBuffer byteCode) throws IOException {
		this.byteCode = Streams.shareContent(byteCode);
		this.className = Classes.getInstance().retrieveName(byteCode);
	}
	
	public static JavaClass create(ByteBuffer byteCode) {
		try {
			return new JavaClass(byteCode);
		} catch (IOException exc) {
			throw Throwables.toRuntimeException(exc);
		}
	}
	
	private  String _getPackageName() {
		return className.contains("/") ?
			className.substring(0, className.lastIndexOf("/")) :
			null;
	}

	private String _getClassName() {
		return className.contains("/") ?
			className.substring(className.lastIndexOf("/") + 1) :
			className;
	}	
	
	public String getPackageName() {
		return Optional.ofNullable(_getPackageName()).map(value -> value.replace("/", ".")).orElse(null);
	}
	
	public String getClassName() {
		return Optional.ofNullable(_getClassName()).orElse(null);
	}
	
	public String getPackagePath() {
		String packageName = getPackageName();
		return packageName != null? packageName.replace(".", "/") + "/" : null;
	}
	
	public String getClassFileName() {
		String classFileName = getClassName();
		return classFileName != null? classFileName.replace(".", "$") + ".class" : null;
	}
	
	public String getPath() {
		String packagePath = getPackagePath();
		String classFileName = getClassFileName();
		String path = null;
		if (packagePath != null) {
			path = packagePath;
		}
		if (classFileName != null) {
			if (path == null) {
				path = "";
			}
			path += classFileName;
		}
		return path;
	}
	
	public String getName() {
		String packageName = getPackageName();
		String classSimpleName = getClassName();
		String name = null;
		if (packageName != null) {
			name = packageName;
		}
		if (classSimpleName != null) {
			if (packageName == null) {
				name = "";
			} else {
				name += ".";
			}
			name += classSimpleName;
		}
		return name;
	}
	
	public ByteBuffer getByteCode() {
		return byteCode.duplicate();
	}
	
	public byte[] toByteArray() {
		return Streams.toByteArray(getByteCode());
	}
	
	public FileSystemItem storeToClassPath(String classPathFolder) {
		return Streams.store(classPathFolder + "/" + getPath(), getByteCode());
	}
	
	@Override
	public String toString() {
		return getName();
	}
	
	public static class Criteria extends org.burningwave.core.Criteria<JavaClass, Criteria, org.burningwave.core.Criteria.TestContext<JavaClass, Criteria>>{
		
		public static Criteria create() {
			return new Criteria();
		}
	}
}