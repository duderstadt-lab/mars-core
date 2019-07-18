package de.mpg.biochem.mars.molecule;

import de.mpg.biochem.mars.util.JsonConvertable;

public interface MarsImageMetadata extends JsonConvertable, MarsRecord {
	void setMicroscopeName(String Microscope);
	
	String getMicroscopeName();
	
	void setCollectionDate(String str);
	
	String getCollectionDate();
	
	String getSourceDirectory();
	
	void addLogMessage(String str);
	
	String getLog();
}
