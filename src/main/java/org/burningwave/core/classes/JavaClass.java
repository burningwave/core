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
 * Copyright (c) 2019-2023 Roberto Gentili
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

import static org.burningwave.core.assembler.StaticComponentContainer.BufferHandler;
import static org.burningwave.core.assembler.StaticComponentContainer.Classes;
import static org.burningwave.core.assembler.StaticComponentContainer.Streams;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

import org.burningwave.core.Closeable;
import org.burningwave.core.function.ThrowingFunction;
import org.burningwave.core.io.FileSystemItem;

public class JavaClass extends io.github.toolfactory.jvm.util.JavaClass implements Closeable {
	private ByteBuffer byteCode;

	public JavaClass(Class<?> cls) {
		this(Classes.getByteCode(cls));
	}

	public JavaClass(ByteBuffer byteCode) {
		super(byteCode);
		setByteCode0(byteCode);
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

	protected ByteBuffer getByteCode0() {
		return this.byteCode;
	}

	protected void setByteCode0(ByteBuffer byteCode) {
		this.byteCode = BufferHandler.duplicate(byteCode);
	}

	public String getPackagePath() {
		return packageName != null? packageName.replace(".", "/") + "/" : null;
	}

	public String getClassFileName() {
		return simpleName.replace(".", "$") + ".class";
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

	public ByteBuffer getByteCode() {
		return BufferHandler.duplicate(getByteCode0());
	}

	public byte[] toByteArray() {
		return BufferHandler.toByteArray(getByteCode());
	}

	public FileSystemItem storeToClassPath(String classPathFolder) {
		return Streams.store(classPathFolder + "/" + getPath(), getByteCode());
	}

	public JavaClass duplicate() {
		return new JavaClass(getByteCode0());
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
		byteCode = null;
	}
}