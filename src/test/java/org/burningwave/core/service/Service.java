package org.burningwave.core.service;

import java.util.Arrays;

import org.burningwave.core.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Service implements Component {
	private final static Logger LOGGER = LoggerFactory.getLogger(Service.class);
	
	private String name;
	
	public Service() {
		this("Default name");
	}
	
	public Service(String name) {
		this.name = name;
	}
	
	public String getName() {
		return this.name;
	}
	
	public void printName() {
		LOGGER.info("My name is {}", this.name);
	}
	
	public static Object retrieve() {
		LOGGER.info("static retrieve");
		return new Object();
	}
	
	@SuppressWarnings("unused")
	private Service supply() {
		LOGGER.info("supply");
		return new Service();
	}
	
	public static void consume(Integer obj) {
		LOGGER.info("Consumer Integer: " + obj.toString());
	}
	
	public void consume(String obj) {
		LOGGER.info("Consumer String: " + obj);
	}
	
	public String apply(String obj) {
		LOGGER.info("Function String execute String: " + obj);
		return obj;
	}
	
	public Long apply(Long obj) {
		LOGGER.info("Function Long execute String: " + obj);
		return obj;
	}
	
	public void run() {
		LOGGER.info("static run");
	}
	
	public void methodWithVarArgs(String... arg) {
		LOGGER.info("methodWithVarArgs");
	}
	
	public static void staticRun() {
		LOGGER.info("static run");
	}	
	
//	public String apply(String value_01, String value_02) {
//		LOGGER.info("BiFunction: " + value_01 + " " + value_02);
//		return "";
//	}
	
	public String apply(String value_01, String value_02, String... value_03) {
		LOGGER.info("TriFunction: " + value_01 + " " + value_02 + " " + value_03);
		return "";
	}
	
	public String apply(Object value_01, String value_02, String value_03) {
		LOGGER.info("TriFunction: " + value_01 + " " + value_02 + " " + value_03);
		return "";
	}
	
	public static String staticApply(Object value_01, String value_02, String value_03) {
		LOGGER.info("TriFunction: " + value_01 + " " + value_02 + " " + value_03);
		return "";
	}
	
	public static String staticApply(Object value_01, String value_02, String value_03, String... value_04) {
		LOGGER.info("TriFunction: " + value_01 + " " + value_02 + " " + value_03 + " " + value_04);
		return "";
	}
	
	public boolean test(Object value_01, String value_02, String value_03) {
		LOGGER.info("TriPredicate: " + value_01 + " " + value_02 + " " + value_03);
		return true;
	}
	
	public void accept(String value_01) {
		LOGGER.info("Consumer: " + value_01);
	}
	
	public void accept(String value_01, String value_02) {
		LOGGER.info("BiConsumer: " + value_01 + " " + value_02);
	}
	
	public void accept(String value_01, String value_02, String value_03) {
		LOGGER.info("TriConsumer: " + value_01 + " " + value_02 + " " + value_03);
	}
	
	public static void staticAccept(Service service, String value_01, String value_02, String value_03) {
		LOGGER.info("QuadConsumer: " + value_01 + " " + value_02 + " " + value_03);
	}
	
	public void withArray(String[] values) {
		LOGGER.info("withArray: " + String.join(", ", Arrays.asList(values)));
	}
	
}