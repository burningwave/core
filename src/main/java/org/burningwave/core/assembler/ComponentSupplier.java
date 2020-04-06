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
package org.burningwave.core.assembler;

import java.util.function.Supplier;

import org.burningwave.core.Component;
import org.burningwave.core.classes.ByteCodeHunter;
import org.burningwave.core.classes.ClassFactory;
import org.burningwave.core.classes.ClassHunter;
import org.burningwave.core.classes.ClassPathHunter;
import org.burningwave.core.classes.FunctionalInterfaceFactory;
import org.burningwave.core.classes.JavaMemoryCompiler;
import org.burningwave.core.classes.SourceCodeHandler;
import org.burningwave.core.concurrent.ConcurrentHelper;
import org.burningwave.core.io.FileSystemScanner;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.iterable.IterableObjectHelper;
import org.burningwave.core.reflection.PropertyAccessor;

public interface ComponentSupplier extends Component {
	
	public static ComponentSupplier getInstance() {
		return ComponentContainer.getInstance();
	}

	public ComponentSupplier clear();

	public<T extends Component> T getOrCreate(Class<T> componentType, Supplier<T> componentSupplier);
	
	public PropertyAccessor.ByFieldOrByMethod getByFieldOrByMethodPropertyAccessor();

	public PropertyAccessor.ByMethodOrByField getByMethodOrByFieldPropertyAccessor();

	public ByteCodeHunter getByteCodeHunter();

	public ClassFactory getClassFactory();

	public SourceCodeHandler getSourceCodeHandler();
	
	public ClassHunter getClassHunter();

	public ClassPathHunter getClassPathHunter();
	
	public ConcurrentHelper getConcurrentHelper();
	
	public FileSystemScanner getFileSystemScanner();

	public FunctionalInterfaceFactory getFunctionalInterfaceFactory();

	public IterableObjectHelper getIterableObjectHelper();

	public JavaMemoryCompiler getJavaMemoryCompiler();
		
	public PathHelper getPathHelper();
	
}