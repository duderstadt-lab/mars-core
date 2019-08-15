package de.mpg.biochem.mars.molecule;

import java.util.Collection;

public interface MarsImageMetadata extends JsonConvertibleRecord, MarsRecord {
	void setMicroscopeName(String Microscope);
	
	String getMicroscopeName();
	
	void setCollectionDate(String str);
	
	String getCollectionDate();
	
	String getSourceDirectory();
	
	void addLogMessage(String str);
	
	String getLog();
	
	BdvSource getBdvSource(String name);
	
	void putBdvSource(BdvSource source);
	
	void removeBdvSource(String name);
	
	Collection<BdvSource> getBdvSources();
}
