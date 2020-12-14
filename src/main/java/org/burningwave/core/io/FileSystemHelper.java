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

import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggersRepository;
import static org.burningwave.core.assembler.StaticComponentContainer.Methods;
import static org.burningwave.core.assembler.StaticComponentContainer.Paths;
import static org.burningwave.core.assembler.StaticComponentContainer.Streams;
import static org.burningwave.core.assembler.StaticComponentContainer.ThreadHolder;
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
import java.util.Optional;
import java.util.UUID;

import org.burningwave.core.Closeable;
import org.burningwave.core.Component;
import org.burningwave.core.ManagedLogger;
import org.burningwave.core.assembler.StaticComponentContainer;
import org.burningwave.core.function.Executor;


public class FileSystemHelper implements Component {
	private String name;
	private File mainTemporaryFolder;
	private String id;
	private Scavenger scavenger;
	
	private FileSystemHelper(String name) {
		this.name = name;
		id = UUID.randomUUID().toString() + "_" + System.currentTimeMillis();
	}
	
	public static FileSystemHelper create(String name) {
		return new FileSystemHelper(name);
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
			return mainTemporaryFolder = Executor.get(() -> {
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
			Executor.run(() -> pingFile.createNewFile());
			pingFile.deleteOnExit();
		}
		return pingFile;
	}
	
	public File createTemporaryFolder(String folderName) {
		return Executor.get(() -> {
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
		return Executor.get(() -> {
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
	
	public void startSweeping() {
		if (scavenger == null) {
			synchronized(this) {
				if (scavenger == null) {
					scavenger = new Scavenger(this, getTemporaryFileScavengerThreadName(), 3600000, 30000);
				}
			}
		}
		scavenger.start();
	}
	
	public void stopSweeping() {
		if (scavenger != null) {
			scavenger.stop();
		}
	}
	
	private String getTemporaryFileScavengerThreadName() {
		return Optional.ofNullable(name).map(nm -> nm + " - ").orElseGet(() -> "") + "Temporary file scavenger";
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
			Throwables.throwException("Could not close singleton instance {}", this);
		}
	}
	
	public static class Scavenger implements ManagedLogger, Closeable {
		private String name;
		private FileSystemHelper fileSystemHelper;
		private long deletingInterval;
		private long waitInterval;
		private File burningwaveTemporaryFolder;
		long lastDeletionStartTime;
		
		private Scavenger(FileSystemHelper fileSystemHelper, String name, long deletingInterval, long waitInterval) {
			this.fileSystemHelper = fileSystemHelper;
			this.deletingInterval = deletingInterval;
			this.waitInterval = waitInterval;
			this.burningwaveTemporaryFolder = fileSystemHelper.getOrCreateBurningwaveTemporaryFolder();
			this.name = name;
		}

		public boolean isAlive() {
			return ThreadHolder.isAlive(name);
		}

		void pingAndDelete() {
			try {
				setPingTime(fileSystemHelper.getOrCreatePingFile().getAbsolutePath());
			} catch (Throwable exc) {
				ManagedLoggersRepository.logError(getClass()::getName, "Exception occurred while setting ping time on file " + fileSystemHelper.getOrCreatePingFile().getAbsolutePath());
				ManagedLoggersRepository.logError(getClass()::getName, exc.getMessage());
				ManagedLoggersRepository.logInfo(getClass()::getName, "Current execution id: {}", fileSystemHelper.id);
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
								ManagedLoggersRepository.logWarn(getClass()::getName, "Exception occurred while cleaning temporary file system item '{}'", fileSystemItem.getAbsolutePath());
								if (fileSystemItem.getName().contains("null")) {
									ManagedLoggersRepository.logInfo(getClass()::getName, "Trying to force deleting of '{}'", fileSystemItem.getAbsolutePath());
									delete(fileSystemItem);
								} else {
									throw exc;
								}
							}
						} catch (Throwable exc) {
							ManagedLoggersRepository.logError(getClass()::getName, "Could not delete '{}' automatically, To avoid this error remove it manually", fileSystemItem.getAbsolutePath());
							ManagedLoggersRepository.logInfo(getClass()::getName, "Current execution id: {}", fileSystemHelper.id);
						}
					}
				}
			}
		}
		
		public void start() {
			lastDeletionStartTime = -1;
			ThreadHolder.startLooping(name, true, Thread.MIN_PRIORITY, thread -> {
				pingAndDelete();
				thread.waitFor(waitInterval);
			});
		}

		long getOrSetPingTime(File pingFile) throws IOException {
			long pingTime = -1;
			try {
				pingTime = getPingTime(pingFile);
			} catch (Throwable exc) {
				ManagedLoggersRepository.logError(getClass()::getName, "Exception occurred while getting ping time on file " + pingFile.getAbsolutePath());
				ManagedLoggersRepository.logError(getClass()::getName, exc.getMessage());
				ManagedLoggersRepository.logInfo(getClass()::getName, "Current execution id: {}", fileSystemHelper.id);
				pingTime = setPingTime(pingFile.getAbsolutePath());
				ManagedLoggersRepository.logInfo(getClass()::getName, "Ping time reset to {} for file {}", pingTime, pingFile.getAbsolutePath());
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
			ThreadHolder.stop(name);
		}
		
		@Override
		public void close() {
			closeResources(() -> 
					burningwaveTemporaryFolder == null, 
				() -> {
					stop();
					burningwaveTemporaryFolder = null;
					fileSystemHelper = null;
				}
			);
		}	
		
	}
	
}
