package de.mpg.biochem.mars.metadata;

import java.util.List;

public interface GenericModel {
	
	public String toString();

	public Iterable<List<String>> getInformationsRow();
	
}
