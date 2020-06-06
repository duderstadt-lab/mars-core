package de.mpg.biochem.mars.metadata;

import io.scif.ome.services.OMEXMLService;
import loci.common.services.ServiceException;
import ome.xml.meta.OMEXMLMetadata;

public class MarsOMEUtils {
	public static OMEXMLMetadata createOMEXMLMetadata(OMEXMLService omexmlService) throws ServiceException {
		OMEXMLMetadata meta = omexmlService.createOMEXMLMetadata();
		
		return meta;
	}
}
