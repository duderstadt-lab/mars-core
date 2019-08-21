package de.mpg.biochem.mars.molecule;

import java.util.Collection;
import java.util.Set;

public interface MarsImageMetadata extends JsonConvertibleRecord, MarsRecord {
	void setMicroscopeName(String Microscope);
	
	String getMicroscopeName();
	
	void setCollectionDate(String str);
	
	String getCollectionDate();
	
	String getSourceDirectory();
	
	void addLogMessage(String str);
	
	String getLog();
	
	MarsBdvSource getBdvSource(String name);
	
	void putBdvSource(MarsBdvSource source);
	
	void removeBdvSource(String name);
	
	Collection<MarsBdvSource> getBdvSources();
	
	Set<String> getBdvSourceNames();
}
