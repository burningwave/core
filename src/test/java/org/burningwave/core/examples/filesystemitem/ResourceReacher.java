package org.burningwave.core.examples.filesystemitem;

import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggersRepository;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.io.FileSystemItem;

@SuppressWarnings("null")
public class ResourceReacher {
    
    
	private static void execute() {
        //Obtaining FileSystemItem through absolute path
        FileSystemItem fSI = FileSystemItem.ofPath("C:/Program Files (x86)");
       
        
        FileSystemItem firstFolderFound = null;
        
        //Obtaining direct children
        for (FileSystemItem child : fSI.getChildren()) {
            System.out.println("child name:" + child.getAbsolutePath());
            if (firstFolderFound == null && child.isFolder()) {
            	 ManagedLoggersRepository.logInfo(ResourceReacher.class::getName, child.getAbsolutePath() + " is a folder: " + child.isFolder());
                 firstFolderFound = child;
            }
        }
        
        //Filtering all nested children for extension
        for (FileSystemItem child : firstFolderFound.findInAllChildren(
            FileSystemItem.Criteria.forAllFileThat(fSIC -> 
                "txt".equals(fSIC.getExtension()) || "exe".equals(fSIC.getExtension()))
            )
        ){
        	 ManagedLoggersRepository.logInfo(ResourceReacher.class::getName, "child name: " + child.getName() + " - child parent: " + child.getParent().getName());
            //copy the file to a folder
            child.copyTo(System.getProperty("user.home") + "/Desktop/copy");
        }
        
        //Obtaining a FileSystemItem through a relative path (in this case we are obtaining a reference to a jar
        //contained in an ear that is contained in a zip
        fSI = ComponentContainer.getInstance().getPathHelper().getResource(
            "/../../src/test/external-resources/libs-for-test.zip/ESC-Lib.ear/APP-INF/lib/jaxb-xjc-2.1.7.jar"
        );
        
        ManagedLoggersRepository.logInfo(ResourceReacher.class::getName, "is an archive:" + fSI.isArchive());
        
        //This method return true if the file or folder is located inside a compressed archive
        ManagedLoggersRepository.logInfo(ResourceReacher.class::getName, "is compressed:" + fSI.isCompressed());
        
        //this clear cache
        fSI.refresh(true);
        
        //Obtaining direct children
        for (FileSystemItem child : fSI.getChildren()) {
            System.out.println("child name:" + child.getAbsolutePath());
        }
        
        //Obtaining all nested children
        for (FileSystemItem child : fSI.getAllChildren()) {
        	 ManagedLoggersRepository.logInfo(ResourceReacher.class::getName, "child name:" + child.getAbsolutePath());
        }
        
        //Obtaining the content of the resource (once the content is loaded it will be cached)
        fSI.toByteBuffer();
    }
    
    public static void main(String[] args) {
        execute();
    }
    
}