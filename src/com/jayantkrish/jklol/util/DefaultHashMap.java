package com.jayantkrish.jklol.util;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.Collection;


/**
 * DefaultHashMap is a HashMap with a default value for
 * keys not found in it. Mostly useful for accumulation.
 */
public class DefaultHashMap<K, V> {

    private V defaultValue;
    private Map<K, V> map;

    public DefaultHashMap(V defaultValue) {
	this.defaultValue = defaultValue;
	map = new HashMap<K, V>();
    }

    public void clear() {
	map.clear();
    }

    public boolean containsKey(Object key) {
	return map.containsKey(key);
    }

    public boolean containsValue(Object value) {
	return map.containsValue(value);
    }

    public Set<Map.Entry<K,V>> entrySet() {
	return map.entrySet();
    }

    public V get(Object key) {
	if (map.containsKey(key)) {
	    return map.get(key);
	}
	return defaultValue;
    }
    

    public boolean isEmpty() {
	return map.isEmpty();
    }

    
    public Set<K> keySet() {
	return map.keySet();
    }

    public V put(K key, V value) {
	return map.put(key, value);
    }
    public void putAll(Map<? extends K,? extends V> m) {
	map.putAll(m);
    }

    public V remove(Object key) {
	return map.remove(key);
    }

    public int size() {
	return map.size();
    }

    /**
     * Convert this object into a Map with no default value.
     */ 
    public Map getBaseMap() {
	return map;
    }

    public Collection<V> values() {
	return map.values();
    }

    public boolean equals(Object o) {
	if (o instanceof DefaultHashMap) {
	    DefaultHashMap d = (DefaultHashMap) o;
	    return defaultValue.equals(d.defaultValue) && map.equals(d.map);
	}
	return false;
    }

    public int hashCode() {
	return map.hashCode() * 739821 + defaultValue.hashCode() * 723;
    }
}