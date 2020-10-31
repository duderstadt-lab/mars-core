
package de.mpg.biochem.mars.image;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.scijava.Context;
import org.scijava.options.OptionsService;

import de.mpg.biochem.mars.molecule.MoleculeArchive;
import de.mpg.biochem.mars.molecule.MoleculeArchiveService;

public class DNAFinderCommandTest {

	protected static Context context;

	protected static Context createContext() {
		return new Context(MoleculeArchiveService.class, OptionsService.class);
	}

	@BeforeAll
	public static void setup() {
		context = createContext();
	}

	@AfterAll
	public static synchronized void cleanUp() {
		if (context != null) {
			context.dispose();
			context = null;
		}
	}
}
