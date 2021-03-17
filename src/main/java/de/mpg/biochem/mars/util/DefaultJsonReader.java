package de.mpg.biochem.mars.util;

import de.mpg.biochem.mars.molecule.*;

public class DefaultJsonReader extends AbstractJsonConvertibleRecord {
	
	public DefaultJsonReader() {
		super();
	}

	@Override
	protected void createIOMaps() {
		//No implementation needed
		//This reader will be populated with fields
		//and run.
	}

}
