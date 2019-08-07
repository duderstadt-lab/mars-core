package de.mpg.biochem.mars.molecule;

public interface MarsImageMetadata extends JsonConvertibleRecord, MarsRecord {
	void setMicroscopeName(String Microscope);
	
	String getMicroscopeName();
	
	void setCollectionDate(String str);
	
	String getCollectionDate();
	
	String getSourceDirectory();
	
	void addLogMessage(String str);
	
	String getLog();
}
