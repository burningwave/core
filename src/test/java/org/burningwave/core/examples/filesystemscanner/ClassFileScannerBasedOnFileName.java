package org.burningwave.core.examples.filesystemscanner;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.function.Predicate;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.JavaClass;
import org.burningwave.core.io.FileScanConfig;
import org.burningwave.core.io.FileScanConfigAbst;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.IterableZipContainer.Entry;

public class ClassFileScannerBasedOnFileName {
    
    @SuppressWarnings("resource")
	public static void execute() {
        ComponentSupplier componentSupplier = ComponentContainer.getInstance();
        Collection<FileSystemItem> files = new HashSet<>();
        componentSupplier.getFileSystemScanner().scan(
            new FileScanConfig() {
                @Override
                protected Predicate<File> getFileNameCheckerForFileSystemEntry() {
                    return entry -> {
                        String name = entry.getName();
                        return name.endsWith(".class") && 
                            !name.endsWith("module-info.class") &&
                            !name.endsWith("package-info.class");
                    };
                }
                
                @Override
                protected Predicate<Entry> getFileNameCheckerForZipEntry() {
                    return entry -> {
                        String name = entry.getName();
                        return name.endsWith(".class") && 
                            !name.endsWith("module-info.class") &&
                            !name.endsWith("package-info.class");
                    };
                }
                
            }.checkFileOptions(
                FileScanConfigAbst.CHECK_FILE_NAME
            ).addPaths(
                componentSupplier.getPathHelper().getMainClassPaths()
            ).toScanConfiguration(
                (wrapper) -> {
                    FileSystemItem fIS = wrapper.getScannedItem().toFileSystemItem();
                    System.out.println(fIS.getAbsolutePath());
                    files.add(fIS);
                    JavaClass.create(
                        wrapper.getScannedItem().toByteBuffer()
                    ).storeToClassPath(
                        System.getProperty("user.home") + "/Desktop/bw-tests"
                    );
                }
            )
        );
        System.out.println("Files found: " + files.size());
    }    
    
    public static void main(String[] args) throws Throwable {
        execute();
    }
    
}
