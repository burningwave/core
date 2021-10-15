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
 * Copyright (c) 2019-2021 Roberto Gentili
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

import java.util.Collection;
import java.util.stream.Collectors;

import org.burningwave.core.io.FileSystemItem;

import io.github.toolfactory.jvm.util.Strings;

public class LinkedJavaClass {
	private LinkedJavaClassContainer linkedJavaClassContainer;
	private FileSystemItem fileSystemItem;
	private JavaClass javaClass;
	private LinkedJavaClass superClass;
	private Collection<LinkedJavaClass> interfaces;

	LinkedJavaClass(LinkedJavaClassContainer linkedJavaClassContainer, FileSystemItem fileSystemItem) {
		this.linkedJavaClassContainer = linkedJavaClassContainer;
		this.fileSystemItem = fileSystemItem;
		this.javaClass = fileSystemItem.toJavaClass();		
	}
	
	
	public LinkedJavaClass getSuperClass() {
		if (superClass != null) {
			return superClass;
		}
		return superClass = linkedJavaClassContainer.find(javaClass.getSuperClassName());
	}
	
	public Collection<LinkedJavaClass> getInterfaces() {
		if (interfaces != null) {
			return interfaces;
		}
		return interfaces = linkedJavaClassContainer.findAll(javaClass.getInterfaceNames());
	}
	
	@Override
	public String toString() {
		return javaClass.getName();
	}
	
	public static class NotFoundException extends RuntimeException {

		private static final long serialVersionUID = 6299238119268246910L;
		
		public NotFoundException(String className, Collection<Collection<FileSystemItem>> classPaths) {
			super(
				Strings.compile(
					"Class {} not found in class paths:\n\t{}",
					className,
					String.join("\n\t", classPaths.stream().flatMap(Collection::stream).collect(Collectors.toList()).stream()
						.map(FileSystemItem::getAbsolutePath).collect(Collectors.toSet()))
				)
			);
		}
		
	}
}
