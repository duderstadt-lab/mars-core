/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2024 Karl Duderstadt
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package de.mpg.biochem.mars.molecule;

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
		for (String key : module.getOutputs().keySet()) {
			Object obj = module.getOutputs().get(key);
			if (obj instanceof MoleculeArchive) {
				moleculeArchiveService.addArchive((MoleculeArchive<?, ?, ?, ?>) obj);
				scriptService.addAlias(((MoleculeArchive<?, ?, ?, ?>) obj).getClass());
			}
		}

		for (String key : module.getInputs().keySet()) {
			Object obj = module.getInputs().get(key);
			if (obj instanceof MoleculeArchive) {
				if (((MoleculeArchive<?, ?, ?, ?>) obj).getWindow() != null)
					((MoleculeArchive<?, ?, ?, ?>) obj).getWindow().unlock();
			}
		}
	}
}
