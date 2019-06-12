/*******************************************************************************
 * Copyright (C) 2019, Karl Duderstadt
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/
package de.mpg.biochem.mars.molecule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import io.scif.services.FormatService;

import org.scijava.app.StatusService;
import org.scijava.display.DisplayService;
import org.scijava.log.LogService;
import org.scijava.plugin.AbstractPTService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;
import org.scijava.script.ScriptService;
import org.scijava.service.Service;
import org.scijava.ui.UIService;

import ij.plugin.frame.RoiManager;
import net.imagej.ImageJService;

@Plugin(type = Service.class)
public class MoleculeArchiveService extends AbstractPTService<MoleculeArchiveService> implements ImageJService {
		
    @Parameter
    private UIService uiService;
    
    @Parameter
    private LogService logService;
    
    @Parameter
    private PrefService prefService;
    
    @Parameter
	private FormatService formatService;
    
    @Parameter
    private StatusService statusService;
    
    @Parameter
    private ScriptService scriptService;
    
    @Parameter
    private DisplayService displayService;
    
	private Map<String, MoleculeArchive> archives;
	
	@Override
	public void initialize() {
		// This Service method is called when the service is first created.
		archives = new LinkedHashMap<>();
		
		scriptService.addAlias(MoleculeArchive.class);
		scriptService.addAlias(MoleculeArchiveService.class);
	}
	
	public void addArchive(MoleculeArchive archive) {
		String name = archive.getName();
		int num = 1;	    
	    while (archives.containsKey(name)) {
	    	if (name.endsWith(".yama"))
	    		name = name.substring(0, name.length() - 5);
	    	if (num == 1) {
	    		name = name + num + ".yama";
	    	} else  {
	    		name = name.substring(0, name.length() - String.valueOf(num-1).length()) + num + ".yama";
	    	}
	    	num++;
	    }
	    
	    archive.setName(name);
		archives.put(archive.getName(), archive);
	}
	
	public void removeArchive(String title) {
		if (archives.containsKey(title)) {
			archives.remove(title);		
			displayService.getDisplay(title).close();
		}
	}
	
	public void removeArchive(MoleculeArchive archive) {
		if (archives.containsKey(archive.getName())) {
			removeArchive(archive.getName());
			displayService.getDisplay(archive.getName()).close();
		}
	}
	
	public boolean rename(String oldName, String newName) {
		if (archives.containsKey(newName)) {
			logService.error("A MoleculeArchive is already open with that name. Choose another name.");
			return false;
		} else {
			archives.get(oldName).setName(newName);
			MoleculeArchive arch = archives.remove(oldName);
			archives.put(newName, arch);
			displayService.getDisplay(oldName).setName(newName);
			return true;
		}
	}

	public ArrayList<String> getColumnNames() {
		Set<String> columnSet = new LinkedHashSet<String>();
		for (MoleculeArchive archive: archives.values()) {
			columnSet.addAll(archive.getProperties().getColumnSet());
		}
		
		ArrayList<String> columns = new ArrayList<String>();
		columns.addAll(columnSet);
		
		return columns;
	}
	
	public Set<ArrayList<String>> getSegmentTableNames() {
		Set<ArrayList<String>> segTableNames = new LinkedHashSet<ArrayList<String>>();
	
		for (MoleculeArchive archive: archives.values()) {
			for (ArrayList<String> segTableName : archive.getProperties().getSegmnetTableNames()) {
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
	
	public UIService getUIService() {
		return uiService; 
	}
	
	public LogService getLogService() {
		return logService;
	}
	
	public StatusService getStatusService() {
		return statusService;
	}
	
	public PrefService getPrefService() {
		return prefService;
	}
	
	public FormatService getFormatService() {
		return formatService;
	}
	
	@Override
	public Class<MoleculeArchiveService> getPluginType() {
		return MoleculeArchiveService.class;
	}
}
