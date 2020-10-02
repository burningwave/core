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

import static org.burningwave.core.assembler.StaticComponentContainer.Methods;
import static org.burningwave.core.assembler.StaticComponentContainer.Paths;
import static org.burningwave.core.assembler.StaticComponentContainer.Streams;
import static org.burningwave.core.assembler.StaticComponentContainer.ThreadPool;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.UUID;

import org.burningwave.core.Closeable;
import org.burningwave.core.Component;
import org.burningwave.core.ManagedLogger;
import org.burningwave.core.assembler.StaticComponentContainer;
import org.burningwave.core.function.ThrowingRunnable;
import org.burningwave.core.function.ThrowingSupplier;


public class FileSystemHelper implements Component {
	private File mainTemporaryFolder;
	private String id;
	private Scavenger scavenger;
	
	private FileSystemHelper() {
		id = UUID.randomUUID().toString() + "_" + System.currentTimeMillis();
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
		if (mainTemporaryFolder != null && mainTemporaryFolder.exists()) {
			return mainTemporaryFolder;
		}
		synchronized (this) {
			if (mainTemporaryFolder != null && mainTemporaryFolder.exists()) {
				return mainTemporaryFolder;
			}			
			return mainTemporaryFolder = ThrowingSupplier.get(() -> {
				File toDelete = File.createTempFile("_BW_TEMP_", "_temp");
				File tempFolder = toDelete.getParentFile();
				File folder = new File(tempFolder.getAbsolutePath() + "/" + "Burningwave" +"/"+id);
				if (!folder.exists()) {
					folder.mkdirs();
					folder.deleteOnExit();
				}
				toDelete.delete();
				return folder;
			});
		}
	}
	
	public File getOrCreatePingFile() {
		File pingFile = new File(Paths.clean(getOrCreateBurningwaveTemporaryFolder() .getAbsolutePath() + "/" + id + ".ping"));
		if (!pingFile.exists()) {
			ThrowingRunnable.run(() -> pingFile.createNewFile());
			pingFile.deleteOnExit();
		}
		return pingFile;
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
				if (file.exists()) {
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
			scavenger = new Scavenger(this, 3600000, 30000);
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
			Methods.retrieveExternalCallerInfo().getClassName().equals(StaticComponentContainer.class.getName())
		) {	
			Scavenger scavenger = this.scavenger;
			if (scavenger != null) {
				scavenger.close();
			}
			closeResources(() -> id == null, () -> {
				clearMainTemporaryFolder();
				this.scavenger = null;
				id = null;
				mainTemporaryFolder = null;
			});
		} else {
			throw Throwables.toRuntimeException("Could not close singleton instance {}", this);
		}
	}
	
	public static class Scavenger implements ManagedLogger, Closeable {
		private boolean isAlive;
		private FileSystemHelper fileSystemHelper;
		private long deletingInterval;
		private long waitInterval;
		private Thread executor;
		private File burningwaveTemporaryFolder;
		
		private Scavenger(FileSystemHelper fileSystemHelper, long deletingInterval, long waitInterval) {
			this.fileSystemHelper = fileSystemHelper;
			this.deletingInterval = deletingInterval;
			this.waitInterval = waitInterval;
			this.burningwaveTemporaryFolder = fileSystemHelper.getOrCreateBurningwaveTemporaryFolder();
			executor = ThreadPool.getOrCreate().setExecutable(() -> {
				pingAndDelete();				
			});
			executor.setName("Temporary file scavenger");
			executor.setPriority(Thread.MIN_PRIORITY);
		}

		void pingAndDelete() {
			long lastDeletionStartTime = -1;
			while (isAlive) {
				try {
					setPingTime(fileSystemHelper.getOrCreatePingFile().getAbsolutePath());
				} catch (Throwable exc) {
					logError("Exception occurred while setting ping time on file " + fileSystemHelper.getOrCreatePingFile().getAbsolutePath());
					logError(exc.getMessage());
					logInfo("Current execution id: {}", fileSystemHelper.id);
				}
				if (System.currentTimeMillis() - lastDeletionStartTime > deletingInterval) {
					lastDeletionStartTime = System.currentTimeMillis();
					for (File fileSystemItem : burningwaveTemporaryFolder.listFiles()) {
						if (!fileSystemItem.getName().equals(fileSystemHelper.getOrCreateMainTemporaryFolder().getName()) &&
							!fileSystemItem.getName().equals(fileSystemHelper.getOrCreatePingFile().getName()) 
						) {
							try {
								try {
									if (fileSystemItem.isDirectory()) {
										File pingFile = new File(
											burningwaveTemporaryFolder.getAbsolutePath() + "/" + fileSystemItem.getName() + ".ping"
										);
										long pingTime = getCreationTime(fileSystemItem.getName());
										if (pingFile.exists()) {
											pingTime = getOrSetPingTime(pingFile);
										}
										if (System.currentTimeMillis() - pingTime >= deletingInterval) {
											delete(fileSystemItem);
										}							
									} else if (fileSystemItem.getName().endsWith("ping")) {
										long pingTime = getOrSetPingTime(fileSystemItem);
										if (System.currentTimeMillis() - pingTime >= deletingInterval) {
											delete(fileSystemItem);
										}
									}
								} catch (Throwable exc) {
									logError("Exception occurred while cleaning temporary file system item " + fileSystemItem.getAbsolutePath(), exc);
									if (fileSystemItem.getName().contains("null")) {
										logInfo("Trying to force deleting of {}", fileSystemItem.getAbsolutePath());
										delete(fileSystemItem);
									} else {
										throw exc;
									}
								}
							} catch (Throwable exc) {
								logInfo("To avoid this error remove {} manually", fileSystemItem.getAbsolutePath());
								logInfo("Current execution id: {}", fileSystemHelper.id);
							}
						}
					}
				}
				try {
					Thread.sleep(waitInterval);
				} catch (Throwable exc) {
					logError("Exception occurred: " + exc.getMessage());
				}
			}
		}
		
		public void start() {
			isAlive = true;
			executor.start();
		}

		long getOrSetPingTime(File pingFile) throws IOException {
			long pingTime = -1;
			try {
				pingTime = getPingTime(pingFile);
			} catch (Throwable exc) {
				logError("Exception occurred while getting ping time on file " + pingFile.getAbsolutePath());
				logError(exc.getMessage());
				logInfo("Current execution id: {}", fileSystemHelper.id);
				pingTime = setPingTime(pingFile.getAbsolutePath());
				logInfo("Ping time reset to {} for file {}", pingTime, pingFile.getAbsolutePath());
			}
			return pingTime;
		}
		
		long setPingTime(String absolutePath) throws IOException {
			long pingTime = System.currentTimeMillis();
			Files.write(
				java.nio.file.Paths.get(absolutePath),
				(String.valueOf(pingTime) + ";").getBytes(), 
				StandardOpenOption.TRUNCATE_EXISTING
			);
			return getPingTime(new File(absolutePath));
		}

		Long getCreationTime(String resourceName) {
			return Long.valueOf(resourceName.split("_")[1]);
		}

		void delete(File resource) {
			fileSystemHelper.delete(resource.getAbsolutePath());
		}

		long getPingTime(File pingFile) throws IOException {
			long pingTime;
			try (InputStream pingFileAsInputStream = new FileInputStream(pingFile)) {
				StringBuffer content = Streams.getAsStringBuffer(pingFileAsInputStream);
				pingTime = Long.valueOf(content.toString().split(";")[0]);
			}
			return pingTime;
		}
		
		public void stop() {
			isAlive = false;
		}
		
		@Override
		public void close() {
			closeResources(() -> 
					executor == null, 
				() -> {
					stop();
					executor = null;
					burningwaveTemporaryFolder = null;
					fileSystemHelper = null;
				}
			);
		}	
		
	}
	
}
