package de.mpg.biochem.mars.molecule;

import java.util.ArrayList;
import java.util.Set;

import de.mpg.biochem.mars.util.JsonConvertable;

public interface MoleculeArchiveProperties extends JsonConvertable {
	
	void addTag(String tag);
	
	void addAllTags(Set<String> tags);
	
	Set<String> getTagSet();
	
	void setTagSet(Set<String> tagSet);
	
	void addParameter(String parameterName);
	
	void addAllParameters(Set<String> parameters);
	
	void removeParameter(String parameter);
	
	Set<String> getParameterSet();
	
	void setParameterSet(Set<String> parameterSet);
	
	void setNumberOfMolecules(int numMolecules);
	
	int getNumberOfMolecules();
	
	void setNumImageMetaData(int numImageMetaData);
	
	int getNumImageMetaData();
	
	void addColumn(String column);
	
	void addAllColumns(Set<String> columns);
	
	void addAllColumns(ArrayList<String> columns);
	
	void setColumnSet(Set<String> moleculeDataTableColumnSet);
	
	Set<String> getColumnSet();
	
	void addSegmentTableNames(Set<ArrayList<String>> segmentTableNames);
	
	void setSegmentTableNames(Set<ArrayList<String>> moleculeSegmentTableNames);
	
	Set<ArrayList<String>> getSegmnetTableNames();
	
	String getComments();
	
	void addComment(String comment);
	
	void setComments(String comments);
}
