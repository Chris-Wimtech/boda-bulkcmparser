package com.bodastage.boda_bulkcmparser;

import java.util.LinkedHashMap;
import java.util.function.Supplier;

public class GrowingHashMap<K, V> extends LinkedHashMap<K, V> {
	private static final long serialVersionUID = -3059441745048497827L;

	private final Supplier<V> producer;
	
	public GrowingHashMap(Supplier<V> producer) {
		this.producer = producer;
	}
	
	public V grow(K key) {
		if (!containsKey(key)) {
        	put(key, producer.get());
        }
		
		return get(key);
	}
}
