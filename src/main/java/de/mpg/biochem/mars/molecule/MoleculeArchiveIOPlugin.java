package de.mpg.biochem.mars.molecule;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;

import org.scijava.Priority;
import org.scijava.event.EventService;
import org.scijava.io.AbstractIOPlugin;
import org.scijava.io.IOPlugin;
import org.scijava.io.event.DataOpenedEvent;
import org.scijava.log.LogService;
import org.scijava.object.ObjectService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.ui.DialogPrompt.MessageType;
import org.scijava.ui.DialogPrompt.OptionType;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.format.DataFormatDetector;
import com.fasterxml.jackson.core.format.DataFormatMatcher;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

import de.mpg.biochem.mars.util.LogBuilder;

@SuppressWarnings("rawtypes")
@Plugin(type = IOPlugin.class, priority = Priority.LOW)
public class MoleculeArchiveIOPlugin extends AbstractIOPlugin<MoleculeArchive> {
	
	@Parameter
    private LogService logService;
	
	@Parameter
    private MoleculeArchiveService moleculeArchiveService;
	
	@Parameter
    private EventService eventService;
	
	@Parameter
    private ObjectService objectService;
	
	@Parameter
    private UIService uiService;
	
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
		File file = new File(source);
		if (!file.exists())
			System.out.println("File not found.");
		String archiveType;
		
		if (file.isDirectory())
			archiveType = getArchiveType(new File(file.getAbsolutePath() + "/MoleculeArchiveProperties.json"));
		else 
			archiveType = getArchiveType(file);
		
		MoleculeArchive archive = null;
		
		try {
			Class<?> clazz = Class.forName(archiveType);
			Constructor<?> constructor = clazz.getConstructor(File.class);
			archive = (MoleculeArchive)constructor.newInstance(file);
		} catch (ClassNotFoundException e) {
			System.err.println("MoleculeArchive class not found.");
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		String name = file.getName();
		
		if (moleculeArchiveService.contains(name)) {
			uiService.showDialog("The MoleculeArchive " + name + " is already open.", MessageType.ERROR_MESSAGE, OptionType.DEFAULT_OPTION);
			return null;
		}
		
		//Seems like this should be called by DefaultIOService, but it isn't.
		eventService.publish(new DataOpenedEvent(source, archive));
		
		objectService.addObject(archive);
		
		//Why doesn't this happen somewhere else. How if ij.io().open is used in a script. It will also open the archive window.
		uiService.show(archive.getName(), archive);

		LogBuilder builder = new LogBuilder();
		
		String log = builder.buildTitleBlock("Opening MoleculeArchive");
		builder.addParameter("Loading File", file.getAbsolutePath());
		builder.addParameter("Archive Name", name);
		
		log += builder.buildParameterList();
		logService.info(log);
		logService.info(LogBuilder.endBlock(true));
		
		return archive;
	}
	
	@Override
	public void save(final MoleculeArchive archive, final String destination) throws IOException {
		File file = new File(destination);
		if (file.isDirectory())
			archive.saveAsVirtualStore(file);
		else
			archive.saveAs(file);
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
