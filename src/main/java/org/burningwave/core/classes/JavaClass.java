package org.burningwave.core.classes;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Optional;

import org.burningwave.Throwables;
import org.burningwave.core.common.Streams;
import org.burningwave.core.io.ByteBufferInputStream;
import org.objectweb.asm.ClassReader;

public class JavaClass {
	private final ByteBuffer byteCode;
	private final String className;
	
	private JavaClass(ByteBuffer byteCode) throws IOException {
		this.byteCode = byteCode;
		this.className = new ClassReader(new ByteBufferInputStream(byteCode)).getClassName();
	}
	
	public static JavaClass create(ByteBuffer byteCode) {
		try {
			return new JavaClass(byteCode);
		} catch (IOException exc) {
			throw Throwables.toRuntimeException(exc);
		}
	}
	
	private  String _getPackageName() {
		return className.contains("/") ?
			className.substring(0, className.lastIndexOf("/")) :
			null;
	}

	private String _getClassName() {
		return className.contains("/") ?
			className.substring(className.lastIndexOf("/") + 1) :
			className;
	}	
	
	public String getPackageName() {
		return Optional.ofNullable(_getPackageName()).map(value -> value.replace("/", ".")).orElse(null);
	}
	
	public String getClassName() {
		return Optional.ofNullable(_getClassName()).orElse(null);
	}
	
	public String getPackagePath() {
		String packageName = getPackageName();
		return packageName != null? packageName.replace(".", "/") + "/" : null;
	}
	
	public String getClassFileName() {
		String classFileName = getClassName();
		return classFileName != null? classFileName.replace(".", "$") + ".class" : null;
	}
	
	public String getPath() {
		String packagePath = getPackagePath();
		String classFileName = getClassFileName();
		String path = null;
		if (packagePath != null) {
			path = packagePath;
		}
		if (classFileName != null) {
			if (path == null) {
				path = "";
			}
			path += classFileName;
		}
		return path;
	}
	
	public String getName() {
		String packageName = getPackageName();
		String classSimpleName = getClassName();
		String name = null;
		if (packageName != null) {
			name = packageName;
		}
		if (classSimpleName != null) {
			if (packageName == null) {
				name = "";
			} else {
				name += ".";
			}
			name += classSimpleName;
		}
		return name;
	}
	
	public ByteBuffer getByteCode() {
		return Streams.shareContent(byteCode);
	}
	
	public byte[] getByteCodeBytes() {
		return Streams.toByteArray(getByteCode());
	}
}