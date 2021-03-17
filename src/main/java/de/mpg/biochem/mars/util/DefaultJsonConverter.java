package de.mpg.biochem.mars.util;

import de.mpg.biochem.mars.molecule.*;

public class DefaultJsonConverter extends AbstractJsonConvertibleRecord {
	
	public DefaultJsonConverter() {
		super();
	}

	@Override
	protected void createIOMaps() {
		//No implementation needed
		//This reader will be populated with fields
		//and run.
	}

}
