package de.mpg.biochem.sdmm.molecule;

import org.scijava.plugin.Parameter;
import org.scijava.ui.UIService;

import org.scijava.display.Display;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.ui.UserInterface;
import org.scijava.ui.viewer.AbstractDisplayViewer;
import org.scijava.ui.viewer.DisplayViewer;

@Plugin(type = DisplayViewer.class)
public class MoleculeArchiveView extends AbstractDisplayViewer<MoleculeArchive> implements DisplayViewer<MoleculeArchive> {
	
	@Parameter
    private MoleculeArchiveService moleculeArchiveService;
	
    @Parameter
    private UIService uiService;
	
	//This method is called to create and display a window
	//here we override it to make sure that calls like uiService.show( .. for MoleculeArchive 
	//will use this method automatically..
	@Override
	public void view(final UserInterface ui, final Display<?> d) {
		MoleculeArchive archive = (MoleculeArchive)d.get(0);
		archive.setName(d.getName());

		//We add it to the MoleculeArchive Service if it hasn't been added already
		if (moleculeArchiveService.getArchive(archive.getName()) == null) {
			moleculeArchiveService.addArchive(archive);
			
			//We also create a new window since we assume it is a new MoleculeArchive...
			new MoleculeArchiveWindow(archive.getName(), archive, moleculeArchiveService);
		} else {
			//We update the table if it is already open
			//This is really ugly at the moment and in the future should be implemented through display
			moleculeArchiveService.getArchive(archive.getName()).getWindow().update();
		}
	}

	@Override
	public boolean canView(final Display<?> d) {
		if (d instanceof MoleculeArchiveDisplay) {
			return true;
		} else {
			return false;
		}
	}
	
	@Override
	public MoleculeArchiveDisplay getDisplay() {
		return (MoleculeArchiveDisplay) super.getDisplay();
	}

	@Override
	public boolean isCompatible(UserInterface arg0) {
		//Needs to be updated if all contexts are to be enabled beyond ImageJ
		return true;
	}
}