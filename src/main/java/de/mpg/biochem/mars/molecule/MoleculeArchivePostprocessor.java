package de.mpg.biochem.mars.molecule;

/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */

import org.scijava.module.Module;
import org.scijava.module.process.AbstractPostprocessorPlugin;
import org.scijava.module.process.PostprocessorPlugin;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.script.ScriptService;

@Plugin(type = PostprocessorPlugin.class)
public class MoleculeArchivePostprocessor extends AbstractPostprocessorPlugin {

	@Parameter
	private MoleculeArchiveService moleculeArchiveService;
	
	@Parameter
	private ScriptService scriptService;
	
	@Override
	public void process(final Module module) {
		for (String key:module.getOutputs().keySet()) {
			Object obj = module.getOutputs().get(key);
			if (obj instanceof MoleculeArchive) {
				moleculeArchiveService.addArchive((MoleculeArchive<?,?,?>)obj); 
				scriptService.addAlias(((MoleculeArchive<?,?,?>)obj).getClass());
			}
		}
	}
}
