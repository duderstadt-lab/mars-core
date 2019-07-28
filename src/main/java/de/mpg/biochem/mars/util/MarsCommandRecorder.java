package de.mpg.biochem.mars.util;

import org.scijava.Priority;
import org.scijava.convert.ConvertService;
import org.scijava.module.Module;
import org.scijava.module.process.*;
import org.scijava.module.ModuleService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import de.mpg.biochem.mars.molecule.MoleculeArchiveCommandRecorder;

@Plugin(type = PostprocessorPlugin.class,
priority = Priority.LOW)
public class MarsCommandRecorder extends AbstractPostprocessorPlugin {
	
	@Parameter
	private ModuleService moduleService;
	
	@Parameter
	private ConvertService convertService;
	
	@Override
	public void process(final Module module) {
		if (MoleculeArchiveCommandRecorder.class.isAssignableFrom(module.getClass())) {
			module.getInfo().inputs().forEach(item -> {
				final String sValue = item.getValue(module) == null ? "" : convertService.convert(item.getValue(module), String.class);
	
				// do not persist if object cannot be converted back from a string
				if (!convertService.supports(sValue, item.getType())) return;
	
				try {
					System.out.println(item.getInfo().loadDelegateClass());
				} catch (ClassNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				System.out.println(item.getName());
				System.out.println(sValue);
			});
		}
		
		//NEED TO retreive the archive and add to commands for all MarsImageMetadata items.
	}
}
