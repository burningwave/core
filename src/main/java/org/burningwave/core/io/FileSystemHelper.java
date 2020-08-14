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
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.UUID;

import org.burningwave.core.Component;
import org.burningwave.core.ManagedLogger;
import org.burningwave.core.assembler.StaticComponentContainer;
import org.burningwave.core.concurrent.QueuedTasksExecutor;
import org.burningwave.core.function.ThrowingRunnable;
import org.burningwave.core.function.ThrowingSupplier;


public class FileSystemHelper implements Component {
	private File baseTemporaryFolder;
	private String id;
	private Scavenger scavenger;
	
	private FileSystemHelper() {
		id = UUID.randomUUID().toString() + "_" + String.valueOf(System.currentTimeMillis());
	}
	
	public static FileSystemHelper create() {
		return new FileSystemHelper();
	}
	
	public void clearBurningwaveTemporaryFolder() {
		delete(Arrays.asList(getOrCreateBurningwaveTemporaryFolder().listFiles()));
	}
	
	public void clearMainTemporaryFolder() {
		delete(getOrCreateMainTemporaryFolder());
	}
	
	public File getOrCreateBurningwaveTemporaryFolder() {
		return getOrCreateMainTemporaryFolder().getParentFile();
	}
	
	public File getOrCreateMainTemporaryFolder() {
		if (baseTemporaryFolder != null) {
			return baseTemporaryFolder;
		}
		return ThrowingSupplier.get(() -> {
			File toDelete = File.createTempFile("_BW_TEMP_", "_temp");
			File tempFolder = toDelete.getParentFile();
			File folder = new File(tempFolder.getAbsolutePath() + "/" + "Burningwave" +"/"+id);
			if (!folder.exists()) {
				folder.mkdirs();
			}
			toDelete.delete();
			return folder;
		});
	}
	
	public File getOrCreatePingFile() {
		File filesToBeDeleted = new File(Paths.clean(getOrCreateBurningwaveTemporaryFolder() .getAbsolutePath() + "/" + id + ".ping"));
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
			return tempFolder;
		});
	}
	
	@Override
	public File getOrCreateTemporaryFolder(String folderName) {
		return ThrowingSupplier.get(() -> {
			File tempFolder = new File(getOrCreateMainTemporaryFolder().getAbsolutePath() + "/" + folderName);
			if (!tempFolder.exists()) {
				tempFolder.mkdirs();
			}
			return tempFolder;
		});
	}
	
	public void delete(Collection<File> files) {
		if (files != null) {
			Iterator<File> itr = files.iterator();
			while(itr.hasNext()) {
				File file = itr.next();
				FileSystemItem fileSystemItem = FileSystemItem.ofPath(file.getAbsolutePath());
				if (fileSystemItem.exists()) {
					delete(file);
				};
			}
		}
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
	
	public boolean delete(String absolutePath) {
		return delete(new File(absolutePath));	
	}
	
	public void deleteOnExit(String absolutePath) {
		deleteOnExit(new File(absolutePath));	
	}
	
	public void startScavenger() {
		if (scavenger == null) {
			scavenger = new Scavenger(this);
		}
		if (!scavenger.isAlive) {
			scavenger.start();
		}
	}
	
	public void stopScavenger() {
		if (scavenger != null) {
			scavenger.stop();
		}
	}
	
	@Override
	public void close() {
		if (this != StaticComponentContainer.FileSystemHelper || 
			Thread.currentThread().getStackTrace()[2].getClassName().equals(StaticComponentContainer.class.getName())
		) {	
			QueuedTasksExecutor.Task closingTask = closeResources(() -> id == null, () -> {
				clearMainTemporaryFolder();
				if (scavenger != null) {
					scavenger.stop();
					scavenger = null;
				}
				id = null;
			});
			if (closingTask != null) {
				closingTask.join();
				baseTemporaryFolder = null;
			}
		} else {
			throw Throwables.toRuntimeException("Could not close singleton instance " + this);
		}
	}
	
	public static class Scavenger implements ManagedLogger {
		private boolean isAlive;
		private FileSystemHelper fileSystemHelper;
		private long deletingInterval;
		private long waitInterval;
		
		private Scavenger(FileSystemHelper fileSystemHelper) {
			this.fileSystemHelper = fileSystemHelper;
			this.deletingInterval = 100000;
			this.waitInterval = 5000;
		}
		
		public void start() {
			isAlive = true;
			FileSystemItem burningwaveTemporaryFolder = 
				FileSystemItem.ofPath(fileSystemHelper.getOrCreateBurningwaveTemporaryFolder().getAbsolutePath());
			BackgroundExecutor.createTask(() -> {
				long lastDeletionStartTime = -1;
				while (isAlive) {
					setPingTime(fileSystemHelper.getOrCreatePingFile().getAbsolutePath());
					if (System.currentTimeMillis() - lastDeletionStartTime > deletingInterval) {
						lastDeletionStartTime = System.currentTimeMillis();
						for (FileSystemItem fileSystemItem : burningwaveTemporaryFolder.refresh().getChildren()) {
							if (!fileSystemItem.getName().equals(fileSystemHelper.getOrCreateMainTemporaryFolder().getName()) &&
								!fileSystemItem.getName().equals(fileSystemHelper.getOrCreatePingFile().getName()) 
							) {
								try {
									if (fileSystemItem.isFolder()) {
										FileSystemItem pingFile = FileSystemItem.ofPath(
											burningwaveTemporaryFolder.getAbsolutePath() + "/" + fileSystemItem.getName() + ".ping"
										);
										long pingTime = getCreationTime(fileSystemItem.getName());
										if (pingFile.exists()) {
											pingTime = getPingTime(pingFile);
										}
										if (System.currentTimeMillis() - pingTime >= deletingInterval) {
											delete(fileSystemItem);
										}							
									} else if ("ping".equals(fileSystemItem.getExtension())) {
										long pingTime = getPingTime(fileSystemItem);
										if (System.currentTimeMillis() - pingTime >= deletingInterval) {
											delete(fileSystemItem);
										}
									}
								} catch (Throwable exc) {
									logError("Exception occurred while cleaning temporary file system item " + fileSystemItem.getAbsolutePath());
									logError(exc.getMessage());
								}
							}
						}
					}
					Thread.sleep(waitInterval);
				}				
			},Thread.MIN_PRIORITY).async().submit();
		}

		long setPingTime(String absolutePath) throws IOException {
			long pingTime = System.currentTimeMillis();
			Files.write(
				java.nio.file.Paths.get(Paths.clean(fileSystemHelper.getOrCreatePingFile().getAbsolutePath())),
				(String.valueOf(pingTime) + ";").getBytes(), 
				StandardOpenOption.WRITE,
				StandardOpenOption.TRUNCATE_EXISTING
			);
			return pingTime;
		}

		Long getCreationTime(String resourceName) {
			return Long.valueOf(resourceName.split("_")[1]);
		}

		void delete(FileSystemItem resource) {
			fileSystemHelper.delete(resource.getAbsolutePath());
			resource.destroy();
		}

		long getPingTime(FileSystemItem pingFile) throws IOException {
			long pingTime;
			try (InputStream pingFileAsInputStream = pingFile.toInputStream()) {
				StringBuffer content = Streams.getAsStringBuffer(pingFileAsInputStream);
				pingTime = Long.valueOf(content.toString().split(";")[0]);
			}
			return pingTime;
		}
		
		public void stop() {
			isAlive = false;
		}
	}
	
}
