package de.mpg.biochem.mars.table;

import java.io.File;
import java.io.IOException;

import org.scijava.Priority;
import org.scijava.io.AbstractIOPlugin;
import org.scijava.io.IOPlugin;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

@Plugin(type = IOPlugin.class, priority = Priority.LOW)
public class MarsTableIOPlugin extends AbstractIOPlugin<MarsTable> {

	@Parameter
	private UIService uiService;
	
	@Override
	public Class<MarsTable> getDataType() {
		return MarsTable.class;
	}

	@Override
	public boolean supportsOpen(final String source) {
		return source.endsWith(".yamt");
	}

	@Override
	public boolean supportsSave(final String source) {
		return supportsOpen(source);
	}
	
	@Override
	public MarsTable open(final String source) throws IOException {

		MarsTable table = new MarsTable(new File(source));
		
		uiService.show(table);
		
		return table;
	}
	
	@Override
	public void save(final MarsTable table, final String destination) throws IOException {
		table.saveAsYAMT(destination);
	}
}