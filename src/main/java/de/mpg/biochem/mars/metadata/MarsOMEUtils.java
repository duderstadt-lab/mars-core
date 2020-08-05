package de.mpg.biochem.mars.metadata;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.util.LogBuilder;
import io.scif.ome.services.OMEXMLService;
import loci.common.services.ServiceException;
import net.imagej.Dataset;
import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.units.quantity.Time;
import ome.xml.meta.OMEXMLMetadata;
import ome.xml.model.enums.Binning;
import ome.xml.model.enums.DimensionOrder;
import ome.xml.model.enums.EnumerationException;
import ome.xml.model.enums.handlers.BinningEnumHandler;
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
		image.setImageIndex(0);
		image.setPixelsPhysicalSizeX(new Length(1.0d, UNITS.PIXEL));
		image.setPixelsPhysicalSizeY(new Length(1.0d, UNITS.PIXEL));
		image.setSizeZ(new PositiveInteger(1));
		//image.setAquisitionDate(new Timestamp(oldMetadata.getCollectionDate()));
		
		image.setName(oldMetadata.getSourceDirectory());
		image.setDimensionOrder(DimensionOrder.valueOf("XYZCT"));
		
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
		
		String format = "Unknown";
		
		//Check format
		if (table.hasColumn("DateTime")) {
			format = "Norpix";
			//Must be Norpix data...
			image.setSizeC(new PositiveInteger(1));
			image.setSizeT(new PositiveInteger((int)table.getValue("slice", table.getRowCount() - 1))); 
			
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
		} else if (table.hasColumn("ChannelIndex")) {
			format = "Micromanager";
			//Must be Micromanager data.
			Map<Integer, String> channelNames = new LinkedHashMap<Integer, String>();
			Map<Integer, String> channelBinning = new LinkedHashMap<Integer, String>();
			
			table.rows().forEach(row -> {
				int channelIndex = Integer.valueOf(row.getStringValue("ChannelIndex"));
				channelBinning.put(channelIndex, row.getStringValue("Binning"));
				channelNames.put(channelIndex, row.getStringValue("Channel"));
			});
			image.setSizeC(new PositiveInteger(channelNames.size()));
			image.setSizeT(new PositiveInteger((int)table.getValue("Frame", table.getRowCount() - 1))); 
			if (table.hasColumn("Width"))
				image.setSizeX(new PositiveInteger(Integer.valueOf(table.getStringValue("Width", 0))));
			if (table.hasColumn("Height"))
				image.setSizeY(new PositiveInteger(Integer.valueOf(table.getStringValue("Height", 0))));
			
			BinningEnumHandler handler = new BinningEnumHandler();
			
			for (int channelIndex : channelNames.keySet()) {
				MarsOMEChannel channel = new MarsOMEChannel();
				channel.setChannelIndex(channelIndex);
				channel.setName(channelNames.get(channelIndex));
				try {
					String binKey = channelBinning.get(channelIndex) + "x" + channelBinning.get(channelIndex);
					channel.setBinning((Binning) handler.getEnumeration(binKey));
				} catch (EnumerationException e) {
					e.printStackTrace();
				}
				image.setChannel(channel, channelIndex);
			}
			
			for (int rowIndex=0; rowIndex < table.getRowCount(); rowIndex++) {
				MarsOMEPlane plane = new MarsOMEPlane();
				plane.setImage(image);
				plane.setImageIndex(0);
				
				int c = Integer.valueOf(table.getStringValue("ChannelIndex", rowIndex));
				int t = Integer.valueOf(table.getStringValue("Frame", rowIndex));
				
				plane.setPlaneIndex((int) image.getPlaneIndex(0, c, t)); 
				plane.setZ(new NonNegativeInteger(0));
				plane.setC(new NonNegativeInteger(c));
				plane.setT(new NonNegativeInteger(t));
				
				for (String heading : table.getColumnHeadingList()) {
					if (xDriftColumnName.equals(heading) || yDriftColumnName.equals(heading))
						continue;
					else if (heading.equals("FileName")) {
						plane.setFilename(table.getStringValue(heading, rowIndex));
						continue;
					} else if (heading.equals("Time (s)")) {
						plane.setDeltaT(new Time(table.getValue("Time (s)", rowIndex), UNITS.SECOND));
						continue;
					}
					
					//Add all unknown columns as StringFields
					plane.setStringField(heading, table.getStringValue(heading, rowIndex));
				}
				
				if (!xDriftColumnName.equals(""))
					plane.setXDrift(table.getValue(xDriftColumnName, rowIndex));
				if (!yDriftColumnName.equals(""))
					plane.setYDrift(table.getValue(yDriftColumnName, rowIndex));
				
				image.setPlane(plane, 0, c, t);
			}
		} else {
			//Unknown. We should at least have a slice column..
			//Have to guess the rest.
			image.setSizeC(new PositiveInteger(1));
			image.setSizeT(new PositiveInteger((int)table.getValue("slice", table.getRowCount() - 1))); 
			
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
				
				image.setPlane(plane, 0, 0, t);
			}
		}

		marsOME.setImage(image, 0);
		
		//Build log message
		LogBuilder builder = new LogBuilder();
		String log = LogBuilder.buildTitleBlock("Migrated to OME format");
		builder.addParameter("From format", format);
		log += builder.buildParameterList() + "\n";
		log += LogBuilder.endBlock();
		
		marsOME.logln(log);
		
		return marsOME;
	}
	
	//Translator from DNA Archive
	public static MarsOMEMetadata translateDNAMetadataToMarsOMEMetadata(OLDMarsMetadata oldMetadata) {
		MarsOMEMetadata marsOME = new MarsOMEMetadata(oldMetadata.getUID());
	
		//BUILD NEW MarsOMEMetadata from OLDMarsMetadata..
		
		return marsOME;
	}
}