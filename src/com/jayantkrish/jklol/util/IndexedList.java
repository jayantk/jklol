package com.jayantkrish.jklol.util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * An indexed list is a way of maintaining an ordered set of elements
 * where each element has a unique numerical index and element lookups
 * can be performed in expected constant time.
 */ 
public class IndexedList<T> implements Iterable<T>, Serializable {

  private static final long serialVersionUID = -6977660130834159257L;
  
  private List<T> items;
	private Map<T, Integer> itemIndex;

	public IndexedList() {
		items = new ArrayList<T>();
		itemIndex = new HashMap<T, Integer>();
	}

	public IndexedList(Collection<? extends T> toInsert) {
	  int size = Iterables.size(toInsert);
		items = new ArrayList<T>(size);
		itemIndex = new HashMap<T, Integer>(size);

		for (T item : toInsert) {
			this.add(item);
		}
	}

	/**
	 * Copy constructor.
	 * @param other
	 */
	public IndexedList(IndexedList<T> other) {
		this.items = new ArrayList<T>(other.items);
		this.itemIndex = new HashMap<T, Integer>(other.itemIndex);
	}
	
	public static <T> IndexedList<T> create() {
	  return new IndexedList<T>();
	}
	
	public static <T> IndexedList<T> create(Iterable<? extends T> items) {
	  return new IndexedList<T>(Lists.newArrayList(items));
	}

	/**
	 * Add a new element to this set. Note that there can only be a single
	 * copy of any given item in the list.
	 */
	public void add(T item) {
		if (itemIndex.containsKey(item)) {
			// Items can only be added once
			return;
		}
		itemIndex.put(item, items.size());
		items.add(item);
	}

	/**
	 * Add each element of a set of elements to this list.
	 */
	public void addAll(Iterable<T> items) {
		for (T item : items) {
			add(item);
		}
	}

	/**
	 * Get the number of elements in the list.
	 */
	public int size() {
		return items.size();
	}

	/**
	 * True if the list contains the specified item.
	 */ 
	public boolean contains(Object item) {
		return itemIndex.containsKey(item);
	}

	/**
	 * Get the index in the list of the specified item.
	 */ 
	public int getIndex(Object item) {
		if (!itemIndex.containsKey(item)) {
			throw new NoSuchElementException();
		}
		return itemIndex.get(item);
	}

	/**
	 * Get the item with the specified index.
	 */ 
	public T get(int index) {
		if (index >= items.size() || index < 0) {
			throw new IndexOutOfBoundsException();
		}
		return items.get(index);
	}

	/**
	 * Get all of the items in the list
	 */
	public List<T> items() {
		return Collections.unmodifiableList(items);
	}

	public boolean equals(Object o) {
		return o instanceof IndexedList<?> && items.equals(((IndexedList<?>) o).items);   		
	}

	public String toString() {
		return items.toString();
	}

  @Override
  public Iterator<T> iterator() {
    return items.iterator();
  }
}