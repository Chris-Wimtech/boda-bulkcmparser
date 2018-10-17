package com.bodastage.boda_bulkcmparser;

import java.util.Collection;
import java.util.Stack;

public class DistinctStack<V> extends Stack<V> {
	private static final long serialVersionUID = -742034356634159813L;

	public DistinctStack() {
		super();
	}
	
	public DistinctStack(V[] values) {
		this();
		
		for (V i : values) {
            push(i);
        }
	}
	
	public void pushIfAbsent(V value) {
		if (!contains(value)) {
			push(value);
		}
	}
	
	public void pushAll(Collection<V> values) {
		for (V i : values) {
			pushIfAbsent(i);
		}
	}
}
