package de.mpg.biochem.mars.molecule.event;

import org.scijava.object.event.ObjectDeletedEvent;

public class MoleculeArchiveDeletedEvent extends ObjectDeletedEvent {
	
	public MoleculeArchiveDeletedEvent(Object obj) {
		super(obj);
	}
	
}
