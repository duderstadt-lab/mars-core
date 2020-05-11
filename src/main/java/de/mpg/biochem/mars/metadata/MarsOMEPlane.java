package de.mpg.biochem.mars.metadata;

import de.mpg.biochem.mars.fx.molecule.metadataTab.ImageModel;
import loci.formats.ome.OMEXMLMetadata;
import ome.xml.model.primitives.NonNegativeInteger;

public class MarsOMEPlane {

	private final int imageID;
	private final int tiffDataID;
	private final NonNegativeInteger c;
	private final NonNegativeInteger z;
	private final NonNegativeInteger t;
	private final NonNegativeInteger ifd;
	private final String filename;
	private final String uuid;

	private float dt;
	private float exposureTime;
	private float posX;
	private float posY;
	private float posZ;

	public MarsOMEPlane(int imageID, int tiffDataID, OMEXMLMetadata md) {
		this.imageID = imageID;
		this.tiffDataID = tiffDataID;
		this.c = md.getTiffDataFirstC(imageID, tiffDataID);
		this.z = md.getTiffDataFirstZ(imageID, tiffDataID);
		this.t = md.getTiffDataFirstT(imageID, tiffDataID);
		this.ifd = md.getTiffDataIFD(imageID, tiffDataID);
		this.filename = md.getUUIDFileName(imageID, tiffDataID);
		this.uuid = md.getUUIDValue(imageID, tiffDataID);

		this.dt = -1;
		this.exposureTime = -1;
		this.posX = -1;
		this.posY = -1;
		this.posZ = -1;
		}
	
	double getDeltaT(int imageIndex, int z, int c, int t);
	
	double getDeltaT(int imageIndex, int planeIndex);
	
	double getXDrift(int imageIndex, int z, int c, int t);
	double getYDrift(int imageIndex, int z, int c, int t);
	double getZDrift(int imageIndex, int z, int c, int t);
	
	void setXDrift(int imageIndex, int z, int c, int t, double xDrift);
	void setYDrift(int imageIndex, int z, int c, int t, double yDrift);
	void setZDrift(int imageIndex, int z, int c, int t, double zDrift);
	
}
