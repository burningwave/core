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

import static org.burningwave.core.assembler.StaticComponentContainer.BackgroundExecutor;
import static org.burningwave.core.assembler.StaticComponentContainer.Paths;
import static org.burningwave.core.assembler.StaticComponentContainer.Streams;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.burningwave.core.Component;
import org.burningwave.core.assembler.StaticComponentContainer;
import org.burningwave.core.concurrent.QueuedTasksExecutor;
import org.burningwave.core.function.ThrowingRunnable;
import org.burningwave.core.function.ThrowingSupplier;


public class FileSystemHelper implements Component {
	private File baseTemporaryFolder;
	private Set<File> temporaryFolders;
	private String id;
	
	private FileSystemHelper() {
		id = UUID.randomUUID().toString();
		temporaryFolders = ConcurrentHashMap.newKeySet();	
	}
	
	public static FileSystemHelper create() {
		return new FileSystemHelper();
	}
	
	public void clearMainTemporaryFolder() {
		delete(Arrays.asList(getOrCreateMainTemporaryFolder().listFiles()), false);
	}
	
	public File getOrCreateMainTemporaryFolder() {
		if (baseTemporaryFolder != null) {
			return baseTemporaryFolder;
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
	
	public File getOrCreateFilesToBeDeletedFile() {
		File filesToBeDeleted = new File(Paths.clean(getOrCreateMainTemporaryFolder().getAbsolutePath() + "/" + id + "_files.to-be-deleted"));
		if (!filesToBeDeleted.exists()) {
			ThrowingRunnable.run(() -> filesToBeDeleted.createNewFile());
		}
		return filesToBeDeleted;
	}
	
	public File createTemporaryFolder(String folderName) {
		return ThrowingSupplier.get(() -> {
			File tempFolder = new File(getOrCreateMainTemporaryFolder().getAbsolutePath() + "/" + folderName);
			if (tempFolder.exists()) {
				tempFolder.delete();
			}
			tempFolder.mkdirs();
			temporaryFolders.add(tempFolder);
			return tempFolder;
		});
	}
	
	@Override
	public File getOrCreateTemporaryFolder(String folderName) {
		return ThrowingSupplier.get(() -> {
			File tempFolder = new File(getOrCreateMainTemporaryFolder().getAbsolutePath() + "/" + folderName);
			if (!tempFolder.exists()) {
				tempFolder.mkdirs();
				temporaryFolders.add(tempFolder);
			}
			return tempFolder;
		});
	}
	
	public QueuedTasksExecutor.Task delete(Collection<File> files, boolean markToBeDeletedOnNextExecution) {
		if (files != null) {
			Set<File> markedToBeDeletedOnNextExecution = new HashSet<>();
			Iterator<File> itr = files.iterator();
			while(itr.hasNext()) {
				File file = itr.next();
				FileSystemItem fileSystemItem = FileSystemItem.ofPath(file.getAbsolutePath());
				if (fileSystemItem.exists()) {
					if (!delete(file) && markToBeDeletedOnNextExecution) {
						markedToBeDeletedOnNextExecution.add(file);
					}
				};
			}
			markToBeDeletedOnNextExecution(markedToBeDeletedOnNextExecution);
		}
		return null;
	}
	
	public boolean delete(File file) {
		if (file.isDirectory()) {
		    File[] files = file.listFiles();
		    if(files != null) { //some JVMs return null for empty dirs
		        for(File fsItem: files) {
		            delete(fsItem);
		        }
		    }
		}
		if (!file.delete()) {
    		file.deleteOnExit();
    		return false;
    	}
		return true;
	}
	
	public void deleteOnExit(File file) {
		if (file.isDirectory()) {
		    File[] files = file.listFiles();
		    if(files != null) { //some JVMs return null for empty dirs
		        for(File fsItem: files) {
		        	deleteOnExit(fsItem);
		        }
		    }
		}
		file.deleteOnExit();
	}
	
	public QueuedTasksExecutor.Task markToBeDeletedOnNextExecution(Collection<File> files) {
		return BackgroundExecutor.createTask(() -> {
			for (File file : files) {
				Files.write(
					java.nio.file.Paths.get(Paths.clean(getOrCreateFilesToBeDeletedFile().getAbsolutePath())),
					(Paths.clean(file.getAbsolutePath() + "\n").getBytes()), 
					StandardOpenOption.APPEND
				);
			}
		}, Thread.MIN_PRIORITY).submit();
	}
	
	public boolean delete(String absolutePath) {
		return delete(new File(absolutePath));	
	}
	
	public void deleteOnExit(String absolutePath) {
		deleteOnExit(new File(absolutePath));	
	}
	
	public QueuedTasksExecutor.Task deleteTemporaryFolders(boolean markToBeDeletedOnNextExecution) {
		QueuedTasksExecutor.Task deleteFoldersTask = delete(temporaryFolders, markToBeDeletedOnNextExecution);
		return BackgroundExecutor.createTask(() -> {
			if (deleteFoldersTask != null) {
				deleteFoldersTask.join();
			}
			temporaryFolders.clear();
		}, Thread.MIN_PRIORITY).submit();
	}

	public void deleteUndeletedFoldersOfPreviousExecution() {
		BackgroundExecutor.createTask(() -> {
			Set<String> absolutePathsToBeDeleted = new HashSet<>();
			for (FileSystemItem child : FileSystemItem.ofPath(getOrCreateMainTemporaryFolder().getAbsolutePath()).getChildren()) {
				if ("to-be-deleted".equals(child.getExtension())) {
					String filesToBeDeleted = Streams.getAsStringBuffer(
						child.toInputStream()
					).toString();
					for (String fileSystemItemAbsolutePath : filesToBeDeleted.split("\n")) {
						absolutePathsToBeDeleted.add(fileSystemItemAbsolutePath);
					}
					absolutePathsToBeDeleted.add(child.getAbsolutePath());
				}
			};
			Set<File> markedToBeDeletedOnNextExecution = new HashSet<>();
			for (String absolutePathToBeDeleted : absolutePathsToBeDeleted) {
				if (!delete(absolutePathToBeDeleted)) {
					markedToBeDeletedOnNextExecution.add(new File(absolutePathToBeDeleted));
				}
			}
			markToBeDeletedOnNextExecution(markedToBeDeletedOnNextExecution);
		}, Thread.MIN_PRIORITY).submit();
	}
	
	@Override
	public void close() {
		if (this != StaticComponentContainer.FileSystemHelper || 
			Thread.currentThread().getStackTrace()[2].getClassName().equals(StaticComponentContainer.class.getName())
		) {	
			List<QueuedTasksExecutor.Task> closingTasks = new ArrayList<>();
			closingTasks.add(closeResources(() -> id == null, () -> {
				closingTasks.add(deleteTemporaryFolders(true));
				id = null;
			}));
			if (closingTasks.get(0) != null) {
				closingTasks.get(0).join();
				closingTasks.get(1).join();
				baseTemporaryFolder = null;
				temporaryFolders = null;
			}
		} else {
			throw Throwables.toRuntimeException("Could not close singleton instance " + this);
		}
	}
	
}
