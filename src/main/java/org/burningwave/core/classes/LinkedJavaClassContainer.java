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

import static org.burningwave.core.assembler.StaticComponentContainer.Synchronizer;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.burningwave.core.Closeable;
import org.burningwave.core.Identifiable;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.PathHelper;

public class LinkedJavaClassContainer implements Closeable, Identifiable {
	private Collection<Collection<FileSystemItem>> classPathColls;
	private Map<String, LinkedJavaClass> javaClassesForName;
	
	public LinkedJavaClassContainer(PathHelper pathHelper) {
		classPathColls = new LinkedHashSet<>();
		javaClassesForName = new ConcurrentHashMap<>();
	}
	
	public void addClassPaths(Collection<FileSystemItem> classPathColl) {
		Collection<FileSystemItem> classPaths = ConcurrentHashMap.newKeySet();
		classPaths.addAll(classPathColl);
		classPathColls.add(classPaths);
	}
	
	
	@Override
	public void close() {
		javaClassesForName.clear();
	}

	public LinkedJavaClass find(String className) {
		LinkedJavaClass linkedJavaClass = javaClassesForName.get(className);
		if (linkedJavaClass != null) {
			return linkedJavaClass;
		}
		return Synchronizer.execute(getOperationId("findClass_" + className), (Supplier<LinkedJavaClass>)() -> {
			LinkedJavaClass linkedJavaClassInner = javaClassesForName.get(className);
			if (linkedJavaClassInner != null) {
				return linkedJavaClassInner;
			}
			FileSystemItem.Criteria classNameCriteria = FileSystemItem.Criteria.forAllFileThat(fileSystemItem -> {
				JavaClass javaClass = fileSystemItem.toJavaClass();
				return javaClass != null && javaClass.getName().equals(className);
			});
			for (Collection<FileSystemItem>classPaths : classPathColls) {
				for (FileSystemItem classPath : classPaths) {
					FileSystemItem fileSystemItem = 
						classPath.findFirstInAllChildren(classNameCriteria);
					if (fileSystemItem != null) {
						linkedJavaClassInner = new LinkedJavaClass(this, fileSystemItem);
						javaClassesForName.put(className, linkedJavaClassInner);
						return linkedJavaClassInner;
					}
				}
			}
			throw new LinkedJavaClass.NotFoundException(
				className, classPathColls
			);
		});
	}
	
	public Collection<LinkedJavaClass> findAll(String[] classNames) {
		Collection<LinkedJavaClass> linkedJavaClasses = new LinkedHashSet<>();
		if (classNames.length == 0) {
			return linkedJavaClasses;
		}
		Collection<String> notFoundClasses = new LinkedHashSet<>();		
		for (String className : classNames) {
			LinkedJavaClass linkedJavaClass = javaClassesForName.get(className);
			if (linkedJavaClass != null) {
				linkedJavaClasses.add(linkedJavaClass);
			} else {
				notFoundClasses.add(className);
			}
		}
		if (notFoundClasses.isEmpty()) {
			return linkedJavaClasses;
		}
		FileSystemItem.Criteria classNamesCriteria = FileSystemItem.Criteria.forAllFileThat(fileSystemItem -> {
			JavaClass javaClass = fileSystemItem.toJavaClass();
			if (javaClass != null) {
				String className = javaClass.getName();
				if (!notFoundClasses.contains(className)) {
					return false;
				}
				synchronized(linkedJavaClasses) {
					linkedJavaClasses.add(
						Synchronizer.execute(getOperationId("findClass_" + className), (Supplier<LinkedJavaClass>)() -> {
							LinkedJavaClass linkedJavaClass = javaClassesForName.get(className);
							if (linkedJavaClass != null) {
								return linkedJavaClass;
							}
							linkedJavaClass = new LinkedJavaClass(this, fileSystemItem);
							javaClassesForName.put(className, linkedJavaClass);
							return linkedJavaClass;
						})
					);
					notFoundClasses.remove(className);
				}
			}
			return notFoundClasses.isEmpty();
		});
		for (Collection<FileSystemItem>classPaths : classPathColls) {
			for (FileSystemItem classPath : classPaths) {
				classPath.findFirstInAllChildren(classNamesCriteria);
				if (notFoundClasses.isEmpty()) {
					return linkedJavaClasses;
				}
			}
		}
		if (!notFoundClasses.isEmpty()) {
			throw new LinkedJavaClass.NotFoundException(
				notFoundClasses.iterator().next(), classPathColls
			);
		}
		return linkedJavaClasses;
	}

	public LinkedJavaClass find(JavaClass javaClass) {
		try {
			return find(javaClass.getName());
		} catch (NullPointerException exc) {
			if (javaClass != null) {
				throw exc;
			}
			return null;
		}
	}
	
}
