package org.burningwave.core.examples.classfactory;

import java.util.Arrays;
import java.util.List;

public class MyClass {
	
	
	private List<String> words;
	
	public MyClass(String... words) {
		this.words = Arrays.asList(words);
	}
	
	public void print() {
		for (String word : words) {
			System.out.print(word);
			System.out.print(" ");
		}
	}
	
	public static void main(String[] args) {
		new MyClass("Hello", "world!").print();
	}
}
