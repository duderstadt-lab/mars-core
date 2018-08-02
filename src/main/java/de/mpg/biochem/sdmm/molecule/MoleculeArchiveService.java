package de.mpg.biochem.sdmm.molecule;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.chrylis.codec.base58.Base58Codec;
import com.chrylis.codec.base58.Base58UUID;

import io.scif.services.FormatService;

import org.scijava.app.StatusService;
import org.scijava.log.LogService;
import org.scijava.plugin.AbstractPTService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.service.Service;
import org.scijava.ui.UIService;

import java.math.BigInteger;

import net.imagej.ImageJService;

@Plugin(type = Service.class)
public class MoleculeArchiveService extends AbstractPTService<MoleculeArchiveService> implements ImageJService {
		
    @Parameter
    private UIService uiService;
    
    @Parameter
    private LogService logService;
    
    @Parameter
	private FormatService formatService;
    
    @Parameter
    private StatusService statusService;
    
	private Map<String, MoleculeArchive> archives;
	
	@Override
	public void initialize() {
		// This Service method is called when the service is first created.
		archives = new LinkedHashMap<>();
	}
	
	public void addArchive(MoleculeArchive archive) {
		archives.put(archive.getName(), archive);
	}
	
	public void removeArchive(String title) {
		if (archives.containsKey(title)) {
			archives.get(title).destroy();
			archives.remove(title);		
		}
	}
	
	public void removeResultsTable(MoleculeArchive archive) {
		if (archives.containsKey(archive.getName()))
			archives.remove(archive.getName());
	}
	
	public void rename(String oldName, String newName) {
		archives.get(oldName).setName(newName);
	}
	
	public void show(String name, MoleculeArchive archive) {
		//This will make sure we don't try to open archive windows if we are running in headless mode...
		//If this method is always used for showing archives it will seamlessly allow the same code to 
		//work in headless mode...
		if (!archives.containsKey(name))
			addArchive(archive);
		if (!uiService.isHeadless()) {
			if (archives.get(name).getWindow() != null) {
				archives.get(name).getWindow().update();
			} else {
				MoleculeArchiveWindow win = new MoleculeArchiveWindow(name, archive, this);
				archives.get(name).setWindow(win);
			}
		}
	}
	
	public ArrayList<String> getArchiveNames() {
		return new ArrayList(archives.keySet());
	}
	
	public boolean contains(String key) {
		return archives.containsKey(key);
	}
	
	public MoleculeArchive getArchive(String name) {
		return archives.get(name);
	}
	
	public MoleculeArchiveWindow getArchiveWindow(String name) {
		return archives.get(name).getWindow();
	}
	
	//Utility methods for creation of base58 encoded UUIDs used for ChronicleMap indexing of molecules.
	public static String getUUID58() {
		Base58UUID bu = new Base58UUID();
		String uuid58 = bu.encode(UUID.randomUUID());
		return uuid58;
	}
	
	//method to retrieve the UUID from a base64 encoded UID
	public static UUID getUUID(String uuid58) {
		Base58UUID bu = new Base58UUID();
		UUID uuid = bu.decode(uuid58);
		return uuid;
	}
	
	private static final BigInteger INIT64  = new BigInteger("cbf29ce484222325", 16);
	private static final BigInteger PRIME64 = new BigInteger("100000001b3",      16);
	private static final BigInteger MOD64   = new BigInteger("2").pow(64);
	
	public String getFNV1aBase58(String str) {
		Base58Codec codec = new Base58Codec();
		return codec.encode(fnv1a_64(str.getBytes()).toByteArray());
	}
	
	public BigInteger fnv1a_64(byte[] data) {
	    BigInteger hash = INIT64;

	    for (byte b : data) {
	      hash = hash.xor(BigInteger.valueOf((int) b & 0xff));
	      hash = hash.multiply(PRIME64).mod(MOD64);
	    }

	    return hash;
	  }
	
	public UIService getUIService() {
		return uiService; 
	}
	
	public LogService getLogService() {
		return logService;
	}
	
	public StatusService getStatusService() {
		return statusService;
	}
	
	public FormatService getFormatService() {
		return formatService;
	}
	
	@Override
	public Class<MoleculeArchiveService> getPluginType() {
		return MoleculeArchiveService.class;
	}
}
