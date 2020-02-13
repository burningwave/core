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
package org.burningwave.core.io;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import org.burningwave.Throwables;
import org.burningwave.core.Component;
import org.burningwave.core.function.ThrowingSupplier;


public class FileSystemHelper implements Component {
	private static File BASE_TEMPORARY_FOLDER;
	private Supplier<PathHelper> pathHelperSupplier; 
	private PathHelper pathHelper;
	
	private FileSystemHelper(Supplier<PathHelper> pathHelperSupplier) {
		this.pathHelperSupplier = pathHelperSupplier;
	}
	
	public static void clearMainTemporaryFolder() {
		delete(Arrays.asList(getOrCreateMainTemporaryFolder().listFiles()));
	}
	
	private static File getOrCreateMainTemporaryFolder() {
		if (BASE_TEMPORARY_FOLDER != null) {
			return BASE_TEMPORARY_FOLDER;
		}
		return ThrowingSupplier.get(() -> {
			File toDelete = File.createTempFile("_BW_TEMP_", "_temp");
			File tempFolder = toDelete.getParentFile();
			File folder = new File(tempFolder.getAbsolutePath() + "/" + "Burningwave");
			if (!folder.exists()) {
				folder.mkdirs();
			}
			toDelete.delete();
			return folder;
		});
	}
	
	public static File getOrCreateTemporaryFolder(String folderName) {
		return ThrowingSupplier.get(() -> {
			File tempFolder = new File(getOrCreateMainTemporaryFolder().getAbsolutePath() + "/" + folderName);
			if (!tempFolder.exists()) {
				tempFolder.mkdirs();
			}
			return tempFolder;
		});
	}
	
	public static FileSystemHelper create(Supplier<PathHelper> pathHelperSupplier) {
		return new FileSystemHelper(pathHelperSupplier);
	}
	
	private PathHelper getPathHelper() {
		return pathHelper != null ?
			pathHelper :
			(pathHelper = pathHelperSupplier.get());
	}
	
	public Collection<FileSystemItem> getResources(String... resourcesRelativePaths) {
		return getResources((coll, file) -> coll.add(file), resourcesRelativePaths);
	}
	
	public FileSystemItem getResource(String resourceRelativePath) {
		return getResource(
				(coll, file) -> 
					coll.add(file), resourceRelativePath);
	}
	
	
	public <T> Collection<T> getResources(
		BiConsumer<Collection<T>, FileSystemItem> fileConsumer,
		String... resourcesRelativePaths
	) {
		Collection<T> files = new ArrayList<>();
		if (resourcesRelativePaths != null && resourcesRelativePaths.length > 0) {
			PathHelper pathHelper = getPathHelper();
			FileSystemItem.disableLog();
			for (String resourceRelativePath : resourcesRelativePaths) {
				pathHelper.getAllPaths().stream().forEach((path) -> {
					FileSystemItem fileSystemItem = FileSystemItem.ofPath(path + "/" + resourceRelativePath);
					if (fileSystemItem.exists()) {
						fileConsumer.accept(files, fileSystemItem);
					}
				});
			}
			FileSystemItem.enableLog();
		}
		return files;
	}
	
	
	public <T> T getResource(BiConsumer<Collection<T>, FileSystemItem> fileConsumer, String resourceRelativePath) {
		Collection<T> files = getResources(fileConsumer, resourceRelativePath);
		if (files.size() > 1) {
			throw Throwables.toRuntimeException("Found more than one resource under relative path " + resourceRelativePath);
		}
		return files.stream().findFirst().orElse(null);
	}
	

	public static void deleteTempraryFiles(Collection<File> temporaryFiles) {
		delete(temporaryFiles);
		temporaryFiles.removeAll(temporaryFiles);
	}
	
	public static void delete(Collection<File> files) {
		if (files != null) {
			Iterator<File> itr = files.iterator();
			while(itr.hasNext()) {
				File tempFile = (File)itr.next();
				if (tempFile.exists()) {
					delete(tempFile);
				}
			}
		}
	}
	
	public static boolean delete(File file) {
		if (file.isDirectory()) {
			return deleteFolder(file);
		} else {
			return file.delete();
		}
	}
	
	public static void delete(String absolutePath) {
		delete(new File(absolutePath));	
	}

	public static boolean deleteFolder(File folder) {
	    File[] files = folder.listFiles();
	    if(files!=null) { //some JVMs return null for empty dirs
	        for(File f: files) {
	            if(f.isDirectory()) {
	                deleteFolder(f);
	            } else {
	                f.delete();
	            }
	        }
	    }
	    return folder.delete();
	}


	
	
	@Override
	public void close() {
		
	}

}
