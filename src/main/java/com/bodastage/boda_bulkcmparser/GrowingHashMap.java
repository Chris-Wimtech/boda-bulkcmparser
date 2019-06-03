package com.bodastage.boda_bulkcmparser;

import java.util.LinkedHashMap;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.NonNull;

public class GrowingHashMap<K, V> extends LinkedHashMap<K, V> {
	private static final long serialVersionUID = -3059441745048497827L;

	@NonNull
	private final Supplier<V> producer;
	
	public GrowingHashMap(@NonNull Supplier<V> producer) {
		this.producer = producer;
	}
	
	@NonNull
	public V grow(K key) {
		if (!containsKey(key)) {
        	put(key, producer.get());
        }
		
		return get(key);
	}
}
