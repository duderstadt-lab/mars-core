package de.mpg.biochem.mars.molecule.event;

import org.scijava.object.event.ObjectCreatedEvent;

public class MoleculeArchiveCreatedEvent extends ObjectCreatedEvent {

	public MoleculeArchiveCreatedEvent(Object obj) {
		super(obj);
	}
	
}
