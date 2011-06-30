package com.jayantkrish.jklol.util;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.util.Collection;

/**
 * A multimap is like a map, except that every key maps to more than one 
 * value. 
 */ 
public class HashMultimap<K,V> {

	private Map<K, Set<V>> items;
	private Set<V> defaultValue;

	public HashMultimap() {
		items = new HashMap<K, Set<V>>();
		defaultValue = Collections.emptySet();
	}
	
	public HashMultimap(HashMultimap<K, V> other) {
		items = new HashMap<K, Set<V>>();
		for (K key : other.keySet()) {
			items.put(key, new HashSet<V>(other.get(key)));
		}
		defaultValue = new HashSet<V>(other.defaultValue);
	}

	public void clear() {
		items.clear();
	}

	public boolean containsKey(Object key) {
		return items.containsKey(key);
	}

	public boolean containsValue(Object value) {
		for (Set<V> valueSet : items.values()) {
			if (valueSet.equals(value)) {
				return true;
			}
		}
		return false;
	}

	public Set<Map.Entry<K,Set<V>>> entrySet() {
		return items.entrySet();
	}

	public boolean equals(Object o) {
		if (o instanceof HashMultimap<?,?>) {
			HashMultimap<?,?> h = (HashMultimap<?,?>) o;
			return this.items.equals(h.items) && (this.defaultValue == h.defaultValue || (this.defaultValue instanceof Object && this.defaultValue.equals(h.defaultValue)));
		}
		return false;
	}

	public Set<V> get(Object key) {
		if (items.containsKey(key)) {
			return items.get(key);
		}
		return defaultValue;
	}

	public int hashCode() {
		return items.hashCode() * 7423125;
	}

	public boolean isEmpty() {
		return items.isEmpty();
	}

	public Set<K> keySet() {
		return items.keySet();
	}

	public Set<V> values() {
		Set<V> values = new HashSet<V>();
		for (K key : keySet()) {
			values.addAll(get(key));
		}
		return values;
	}

	public Set<V> put(K key, V value) {
		if (!items.containsKey(key)) {
			items.put(key, new HashSet<V>());
		}
		items.get(key).add(value);
		return Collections.unmodifiableSet(items.get(key));
	}

	public void putAll(K key, Collection<V> values) {
		if (!items.containsKey(key)) {
			items.put(key, new HashSet<V>());
		}
		items.get(key).addAll(values);
	}

	public void putAll(Map<? extends K,? extends V> m) {
		for (K k : m.keySet()) {
			put(k, m.get(k));
		}
	}

	public void remove(Object key, V value) {
		if (items.containsKey(key)) {
			items.get(key).remove(value);
		}
	}

	public Set<V> removeAll(Object key) {
		if (items.containsKey(key)) {
			return items.remove(key);
		}
		return null;
	}

	/**
	 * Return the number of keys in the map.
	 */ 
	 public int size() {
		return items.size();
	 }

	 public String toString() {
		 return items.toString();
	 }
}