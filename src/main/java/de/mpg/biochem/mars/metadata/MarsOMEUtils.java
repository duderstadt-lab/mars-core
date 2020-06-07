package de.mpg.biochem.mars.metadata;

import de.mpg.biochem.mars.table.MarsTable;
import io.scif.ome.services.OMEXMLService;
import loci.common.services.ServiceException;
import net.imagej.Dataset;
import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.units.quantity.Time;
import ome.xml.meta.OMEXMLMetadata;
import ome.xml.model.primitives.NonNegativeInteger;
import ome.xml.model.primitives.PositiveInteger;
import ome.xml.model.primitives.Timestamp;
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
		
		MarsTable table = oldMetadata.getDataTable();
		
		String xDriftColumnName = "";
		String yDriftColumnName = "";
		
		//Check for drift Columns
		for (String heading : table.getColumnHeadingList()) {
			String lower = heading.toLowerCase();
			if (lower.contains("x") && lower.contains("drift"))
				xDriftColumnName = heading;
			if (lower.contains("y") && lower.contains("drift"))
				yDriftColumnName = heading;
		}
		
		//Check format
		if (table.hasColumn("DateTime")) {
			//Must be Norpix data...
			image.setImageIndex(0);
			image.setPixelsPhysicalSizeX(new Length(1.0d, UNITS.PIXEL));
			image.setPixelsPhysicalSizeY(new Length(1.0d, UNITS.PIXEL));
			image.setSizeC(new PositiveInteger(1));
			image.setSizeZ(new PositiveInteger(1));
			image.setSizeT(new PositiveInteger((int)table.getValue("slice", table.getRowCount() - 1))); 
			image.setAquisitionDate(new Timestamp(oldMetadata.getCollectionDate()));
			
			MarsOMEChannel channel = new MarsOMEChannel();
			channel.setChannelIndex(0);
			image.setChannel(channel, 0);
			
			for (int rowIndex=0; rowIndex < table.getRowCount(); rowIndex++) {
				int slice = (int) table.getValue("slice", rowIndex);
				int t = slice - 1;
				MarsOMEPlane plane = new MarsOMEPlane(image, 0, rowIndex, new NonNegativeInteger(0), new NonNegativeInteger(0), new NonNegativeInteger(t));
				
				if (!xDriftColumnName.equals(""))
					plane.setXDrift(table.getValue(xDriftColumnName, rowIndex));
				if (!yDriftColumnName.equals(""))
					plane.setYDrift(table.getValue(yDriftColumnName, rowIndex));
				if (table.hasColumn("Time (s)"))
					plane.setDeltaT(new Time(table.getValue("Time (s)", rowIndex), UNITS.SECOND));
				if (table.hasColumn("DateTime"))
					plane.setStringField("DateTime", table.getStringValue("DateTime", rowIndex));
				
				image.setPlane(plane, 0, 0, t);
			}
		} else {
			//Build planes..
			//oldMetadata.getDataTable().rows().forEach(row -> {
				//Create Plane from row
			//});
		}

		marsOME.setImage(image, 0);
		
		return marsOME;
	}
	
	//Translator from DNA Archive
	public static MarsOMEMetadata translateDNAMetadataToMarsOMEMetadata(OLDMarsMetadata oldMetadata) {
		MarsOMEMetadata marsOME = new MarsOMEMetadata(oldMetadata.getUID());
	
		//BUILD NEW MarsOMEMetadata from OLDMarsMetadata..
		
		return marsOME;
	}
}
