package org.burningwave.core.bean;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Complex {
	private Complex.Data data;
	
	public Complex() {
		setData(new Data());
	}
	
	
	public Complex.Data getData() {
		return data;
	}
	
	public void setData(Complex.Data data) {
		this.data = data;
	}


	public static class Data {
		private Data.Item[][] items;
		private List<Data.Item> itemsList;
		private Map<String, Data.Item[][]> itemsMap;
		
		public Data() {
			items = new Data.Item[][] {
				new Data.Item[] {
					new Item("Hello"),
					new Item("World!"),
					new Item("How do you do?")
				},
				new Data.Item[] {
					new Item("How do you do?"),
					new Item("Hello"),
					new Item("Bye")
				}
			};
			itemsMap = new LinkedHashMap<>();
			itemsMap.put("items", items);
		}
		
		public Data.Item[][] getItems() {
			return items;
		}
		public void setItems(Data.Item[][] items) {
			this.items = items;
		}
		
		public List<Data.Item> getItemsList() {
			return itemsList;
		}
		public void setItemsList(List<Data.Item> itemsList) {
			this.itemsList = itemsList;
		}
		
		public Map<String, Data.Item[][]> getItemsMap() {
			return itemsMap;
		}
		public void setItemsMap(Map<String, Data.Item[][]> itemsMap) {
			this.itemsMap = itemsMap;
		}
		
		public static class Item {
			private String name;
			
			public Item(String name) {
				this.name = name;
			}
			
			public String getName() {
				return name;
			}

			public void setName(String name) {
				this.name = name;
			}
		}
	}
}