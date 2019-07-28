package de.mpg.biochem.mars.molecule;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;

import de.mpg.biochem.mars.table.MarsTable;

public class DefaultMarsImageMetadata extends AbstractMarsImageMetadata {
    public DefaultMarsImageMetadata() {
    	super();
    }
    
    public DefaultMarsImageMetadata(String UID) {
    	super(UID);
    }
    
    public DefaultMarsImageMetadata(String UID, MarsTable dataTable) {
    	super(UID, dataTable);
    }
	
	public DefaultMarsImageMetadata(JsonParser jParser) throws IOException {
		super(jParser);
	}
}
