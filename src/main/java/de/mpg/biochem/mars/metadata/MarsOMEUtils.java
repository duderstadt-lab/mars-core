package de.mpg.biochem.mars.metadata;

import io.scif.ome.services.OMEXMLService;
import loci.common.services.ServiceException;
import net.imagej.Dataset;
import ome.xml.meta.OMEXMLMetadata;
import ome.xml.model.primitives.PositiveInteger;
import net.imagej.axis.Axes;

public class MarsOMEUtils {
	public static OMEXMLMetadata createOMEXMLMetadata(OMEXMLService omexmlService, Dataset dataset) throws ServiceException {
		OMEXMLMetadata meta = omexmlService.createOMEXMLMetadata();

		for (int d = 0; d < dataset.numDimensions(); d++) {
			if (dataset.axis(d).type().equals(Axes.X))
				meta.setPixelsSizeX(new PositiveInteger((int) dataset.dimension(d)) , 0);
			else if (dataset.axis(d).type().equals(Axes.Y))
				meta.setPixelsSizeY(new PositiveInteger((int) dataset.dimension(d)) , 0);
			else if (dataset.axis(d).type().equals(Axes.Z))
				meta.setPixelsSizeZ(new PositiveInteger((int) dataset.dimension(d)) , 0);
			else if (dataset.axis(d).type().equals(Axes.CHANNEL))
				meta.setPixelsSizeC(new PositiveInteger((int) dataset.dimension(d)) , 0);
			else if (dataset.axis(d).type().equals(Axes.TIME))
				meta.setPixelsSizeT(new PositiveInteger((int) dataset.dimension(d)) , 0);
		}
		
		if (meta.getPixelsSizeX(0) == null)
			meta.setPixelsSizeX(new PositiveInteger(1) , 0);
		if (meta.getPixelsSizeY(0) == null)
			meta.setPixelsSizeY(new PositiveInteger(1) , 0);
		if (meta.getPixelsSizeZ(0) == null)
			meta.setPixelsSizeZ(new PositiveInteger(1) , 0);
		if (meta.getPixelsSizeC(0) == null)
			meta.setPixelsSizeC(new PositiveInteger(1) , 0);
		if (meta.getPixelsSizeT(0) == null)
			meta.setPixelsSizeT(new PositiveInteger(1) , 0);
		
		return meta;
	}
	
	//Translator from Normal Archive (SingleMoleculeArchive, ArchMoleculeArchive)
	public static MarsOMEMetadata translateToMarsOMEMetadata(OLDMarsMetadata oldMetadata) {
		MarsOMEMetadata marsOME = new MarsOMEMetadata(oldMetadata.getUID());
	
		marsOME.setMicroscopeName(oldMetadata.getMicroscopeName());
		marsOME.setNotes(oldMetadata.getNotes());
		marsOME.log(oldMetadata.getLog());
		marsOME.setSourceDirectory(oldMetadata.getSourceDirectory());
		
		oldMetadata.getParameters().keySet().forEach(name -> 
			marsOME.setParameter(name, oldMetadata.getParameter(name)));
		
		oldMetadata.getTags().forEach(tag -> marsOME.addTag(tag));
		
		oldMetadata.getBdvSources().forEach(bdvSource -> marsOME.putBdvSource(bdvSource));
		
		oldMetadata.getRegionNames().forEach(name -> marsOME.putRegion(oldMetadata.getRegion(name)));
		
		oldMetadata.getPositionNames().forEach(name -> marsOME.putPosition(oldMetadata.getPosition(name)));
		
		//Create MarsOMEImage and fill in all the planes and then add it to the MarsOMEMetadata...
		MarsOMEImage image = new MarsOMEImage();
		
		
		
		//Build planes..
		oldMetadata.getDataTable().rows().forEach(row -> {
			//Create Plane from row
		});
		
		marsOME.setImage(image, 0);
		
		//BUILD NEW MarsOMEMetadata from OLDMarsMetadata..
		
		return marsOME;
	}
	
	//Translator from DNA Archive
	public static MarsOMEMetadata translateDNAMetadataToMarsOMEMetadata(OLDMarsMetadata oldMetadata) {
		MarsOMEMetadata marsOME = new MarsOMEMetadata(oldMetadata.getUID());
	
		//BUILD NEW MarsOMEMetadata from OLDMarsMetadata..
		
		return marsOME;
	}
}
