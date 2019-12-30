package org.burningwave.core.io;

import java.io.File;
import java.util.function.Predicate;

import org.burningwave.core.Criteria;

public class FileCriteria extends Criteria<File, FileCriteria, Criteria.TestContext<File, FileCriteria>>{
	
	private FileCriteria() {}
	
	public static FileCriteria create() {
		return new FileCriteria();
	}
	
	public FileCriteria absolutePath(Predicate<String> predicate) {
		this.predicate = concat(
			this.predicate,
			(context, file) -> predicate.test(file.getAbsolutePath())
		);
		return this;
	}
	
	
	public FileCriteria name(Predicate<String> predicate) {
		this.predicate = concat(
			this.predicate,
			(context, file) -> predicate.test(file.getName())
		);
		return this;
	}
	
}
