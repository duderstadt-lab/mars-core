package de.mpg.biochem.mars.molecule;

import java.net.URISyntaxException;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.IntStream;

import org.scijava.Priority;
import org.scijava.io.AbstractIOPlugin;
import org.scijava.io.IOPlugin;
import org.scijava.io.handle.DataHandle;
import org.scijava.io.handle.DataHandleService;
import org.scijava.io.location.Location;
import org.scijava.io.location.LocationService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.DialogPrompt.MessageType;
import org.scijava.util.FileUtils;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.format.DataFormatDetector;
import com.fasterxml.jackson.core.format.DataFormatMatcher;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

@SuppressWarnings("rawtypes")
@Plugin(type = IOPlugin.class, priority = Priority.LOW)
public class MoleculeArchiveIOPlugin extends AbstractIOPlugin<MoleculeArchive> {

	@Parameter
	private LocationService locationService;

	@Parameter
	private DataHandleService dataHandleService;
	
	@Override
	public Class<MoleculeArchive> getDataType() {
		return MoleculeArchive.class;
	}

	@Override
	public boolean supportsOpen(final String source) {
		return source.endsWith(".yama") || source.endsWith(".yama.store");
	}

	@Override
	public boolean supportsSave(final String source) {
		return supportsOpen(source);
	}
	
	//This needs cleaning up but lets see if it is working first...
	@Override
	public MoleculeArchive open(final String source) throws IOException {
		final Location sourceLocation;
		try {
			sourceLocation = locationService.resolve(source);
		} catch (final URISyntaxException exc) {
			throw new IOException("Unresolvable source: " + source, exc);
		}
		
		File file = new File(source);
		String archiveType;
		
		if (file.isDirectory())
			archiveType = getArchiveType(new File(file.getAbsolutePath() + "/MoleculeArchiveProperties.json"));
		else 
			archiveType = getArchiveType(file);
		
		Object instance = new Object();
		
		try {
			Class<?> clazz = Class.forName(archiveType);
			Constructor<?> constructor = clazz.getConstructor(File.class);
			instance = constructor.newInstance(file);
		} catch (ClassNotFoundException e) {
			System.err.println("MoleculeArchive class not found.");
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InstantiationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		/*
		try (final DataHandle<? extends Location> handle = dataHandleService.create(sourceLocation)) {
			if (!handle.exists()) {
				throw new IOException("Cannot open source");
			}
			long length = handle.length();
		
		}
			*/
		
		System.out.println(" " + archiveType);
		
		return (MoleculeArchive)instance;
	}
	
	@Override
	public void save(final MoleculeArchive archive, final String destination) throws IOException {
		final Location dstLocation;
		try {
			dstLocation = locationService.resolve(destination);
		}
		catch (final URISyntaxException exc) {
			throw new IOException("Unresolvable destination: " + destination, exc);
		}

		try (final DataHandle<Location> handle = dataHandleService.create(dstLocation)) {

		}
	}

	private String getArchiveType(File file) throws JsonParseException, IOException {
		//The first object in the yama file has general information about the archive including
		//number of Molecules and their averageSize, which we can use to initialize the ChronicleMap
		//if we are working virtual. So we load that information first
		InputStream inputStream = new BufferedInputStream(new FileInputStream(file));
		
		//Here we automatically detect the format of the JSON file
		//Can be JSON text or Smile encoded binary file...
		JsonFactory jsonF = new JsonFactory();
		SmileFactory smileF = new SmileFactory(); 
		DataFormatDetector det = new DataFormatDetector(new JsonFactory[] { jsonF, smileF });
	    DataFormatMatcher match = det.findFormat(inputStream);
	    JsonParser jParser = match.createParserWithMatch();
	    
	    String archiveType = "de.mpg.biochem.mars.molecule.SingleMoleculeArchive";
	    
		jParser.nextToken();
		jParser.nextToken();
		if ("MoleculeArchiveProperties".equals(jParser.getCurrentName())) {
			jParser.nextToken();
			while (jParser.nextToken() != JsonToken.END_OBJECT) {
			    String fieldname = jParser.getCurrentName();
			    	
			    if ("ArchiveType".equals(fieldname)) {
			    	jParser.nextToken();
			    	archiveType = jParser.getText();
			    	break;
			    }
			}
		}
		
		jParser.close();
		inputStream.close();
		
		return archiveType;
	}
}
