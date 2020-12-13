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

import static org.burningwave.core.assembler.StaticComponentContainer.ByteBufferHandler;
import static org.burningwave.core.assembler.StaticComponentContainer.Classes;
import static org.burningwave.core.assembler.StaticComponentContainer.Streams;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Consumer;

import org.burningwave.core.Closeable;
import org.burningwave.core.function.ThrowingFunction;
import org.burningwave.core.io.FileSystemItem;

public class JavaClass implements Closeable {
	private ByteBuffer byteCode;
	private String classNameSlashed;
	private String className;
	
	private JavaClass(String className, ByteBuffer byteCode) {
		this.classNameSlashed = className;
		this.byteCode = byteCode;
	}
	
	JavaClass(Class<?> cls) {
		this(cls.getName(), Classes.getByteCode(cls));
	}
	
	JavaClass(ByteBuffer byteCode) {
		this(Classes.retrieveName(byteCode), Streams.shareContent(byteCode));
	}
	
	public static JavaClass create(Class<?> cls) {
		return new JavaClass(cls); 
	}
	
	public static JavaClass create(ByteBuffer byteCode) {
		return new JavaClass(byteCode);
	}
	
	public static void use(ByteBuffer byteCode, Consumer<JavaClass> javaClassConsumer) {
		try(JavaClass javaClass = JavaClass.create(byteCode)) {
			javaClassConsumer.accept(javaClass);
		}
	}
	
	public static <T, E extends Throwable> T extractByUsing(ByteBuffer byteCode, ThrowingFunction<JavaClass, T, E> javaClassConsumer) throws E {
		try(JavaClass javaClass = JavaClass.create(byteCode)) {
			return javaClassConsumer.apply(javaClass);
		}
	}
	
	private  String _getPackageName() {
		return classNameSlashed.contains("/") ?
			classNameSlashed.substring(0, classNameSlashed.lastIndexOf("/")) :
			null;
	}

	private String _getSimpleName() {
		return classNameSlashed.contains("/") ?
			classNameSlashed.substring(classNameSlashed.lastIndexOf("/") + 1) :
			classNameSlashed;
	}	
	
	public String getPackageName() {
		return Optional.ofNullable(_getPackageName()).map(value -> value.replace("/", ".")).orElse(null);
	}
	
	public String getSimpleName() {
		return Optional.ofNullable(_getSimpleName()).orElse(null);
	}
	
	public String getPackagePath() {
		String packageName = getPackageName();
		return packageName != null? packageName.replace(".", "/") + "/" : null;
	}
	
	public String getClassFileName() {
		String classFileName = getSimpleName();
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
		if (className == null) {
			String packageName = getPackageName();
			String classSimpleName = getSimpleName();
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
			className = name;
		}		
		return className;
	}
	
	public ByteBuffer getByteCode() {
		return ByteBufferHandler.duplicate(byteCode);
	}
	
	public byte[] toByteArray() {
		return Streams.toByteArray(getByteCode());
	}
	
	public FileSystemItem storeToClassPath(String classPathFolder) {
		return Streams.store(classPathFolder + "/" + getPath(), getByteCode());
	}
	
	public JavaClass duplicate() {
		return new JavaClass(classNameSlashed, byteCode);
	}
	
	@Override
	public String toString() {
		return getName();
	}
	
	protected static class Criteria extends org.burningwave.core.Criteria<JavaClass, Criteria, org.burningwave.core.Criteria.TestContext<JavaClass, Criteria>>{
		
		public static Criteria create() {
			return new Criteria();
		}
		
	}

	@Override
	public void close() {
		classNameSlashed = null;
		byteCode = null;		
	}
}