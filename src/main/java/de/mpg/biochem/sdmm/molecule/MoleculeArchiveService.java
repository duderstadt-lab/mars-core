package de.mpg.biochem.sdmm.molecule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import de.mpg.biochem.sdmm.table.SDMMResultsTable;
import io.scif.services.FormatService;

import org.scijava.app.StatusService;
import org.scijava.log.LogService;
import org.scijava.plugin.AbstractPTService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.script.ScriptService;
import org.scijava.service.Service;
import org.scijava.ui.UIService;

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
    
    @Parameter
    private ScriptService scriptService;
    
    //@Parameter
    //private ObjectService objectService;
    
	private Map<String, MoleculeArchive> archives;
	
	@Override
	public void initialize() {
		// This Service method is called when the service is first created.
		archives = new LinkedHashMap<>();
		
		scriptService.addAlias(MoleculeArchive.class);
		scriptService.addAlias(MoleculeArchiveService.class);
	}
	
	public void addArchive(MoleculeArchive archive) {
		archives.put(archive.getName(), archive);
		//objectService.addObject(archive);
	}
	
	public void removeArchive(String title) {
		if (archives.containsKey(title)) {
			//objectService.removeObject(archives.get(title));
			archives.get(title).destroy();
			archives.remove(title);		
		}
	}
	
	public void removeArchive(MoleculeArchive archive) {
		if (archives.containsKey(archive.getName())) {
			removeArchive(archive.getName());
		}
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
				archives.get(name).getWindow().updateAll();
			} else {
				MoleculeArchiveWindow win = new MoleculeArchiveWindow(archive, this);
				archives.get(name).setWindow(win);
			}
		}
	}
	
	public ArrayList<String> getColumnNames() {
		ArrayList<String> columns = new ArrayList<String>();
	
		for (MoleculeArchive archive: archives.values()) {
			//We assume all the molecules have the same columns
			//I think this should be strictly enforced
			SDMMResultsTable datatable = archive.get(0).getDataTable();
			
			for (int i=0;i<datatable.getColumnCount();i++) {
				if(!columns.contains(datatable.getColumnHeader(i)))
					columns.add(datatable.getColumnHeader(i));
			}
		}
		
		return columns;
	}
	
	public ArrayList<String> getSegmentTableNames() {
		ArrayList<String> segTableNames = new ArrayList<String>();
	
		for (MoleculeArchive archive: archives.values()) {
			//We assume all the molecules have the same segment tables
			//I think this should be strictly enforced
			for (String segTableName : archive.get(0).getSegmentTableNames()) {
				if(!segTableNames.contains(segTableName))
					segTableNames.add(segTableName);
			}
		}
		
		return segTableNames;
	}
	
	
	public ArrayList<String> getArchiveNames() {
		return new ArrayList<String>(archives.keySet());
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
