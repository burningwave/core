package org.burningwave.core.io;

import java.util.function.Predicate;

import org.burningwave.core.Criteria;

public class ZipEntryCriteria extends Criteria<ZipInputStream.Entry, ZipEntryCriteria, Criteria.TestContext<ZipInputStream.Entry, ZipEntryCriteria>>{
	
	private ZipEntryCriteria() {}
	
	public static ZipEntryCriteria create() {
		return new ZipEntryCriteria();
	}
	
	public ZipEntryCriteria absolutePath(final Predicate<String> predicate) {
		this.predicate = concat(
			this.predicate,
			(context, zipEntry) -> predicate.test(zipEntry.getAbsolutePath())
		);
		return this;
	}
	
	
	public ZipEntryCriteria name(final Predicate<String> predicate) {
		this.predicate = concat(
			this.predicate,
			(context, zipEntry) -> predicate.test(zipEntry.getName())
		);
		return this;
	}
	
}
