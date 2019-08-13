package de.mpg.biochem.mars.molecule;

import java.util.Set;

public interface MarsImageMetadata extends JsonConvertibleRecord, MarsRecord {
	void setMicroscopeName(String Microscope);
	
	String getMicroscopeName();
	
	void setCollectionDate(String str);
	
	String getCollectionDate();
	
	String getSourceDirectory();
	
	void addLogMessage(String str);
	
	String getLog();
	
	String getBdvView(String viewName);
	
	void putBdvView(String viewName, String filePath);
	
	Set<String> getBdvViewList();
	
	void removeBdvView(String viewName);
}
