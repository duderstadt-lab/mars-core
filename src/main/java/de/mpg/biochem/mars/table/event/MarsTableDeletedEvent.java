package de.mpg.biochem.mars.table.event;

import org.scijava.object.event.ObjectDeletedEvent;

public class MarsTableDeletedEvent extends ObjectDeletedEvent {
	
	public MarsTableDeletedEvent(Object obj) {
		super(obj);
	}
	
}
