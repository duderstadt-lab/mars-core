package de.mpg.biochem.mars.table;

import java.io.File;
import java.io.IOException;

import org.scijava.Priority;
import org.scijava.event.EventService;
import org.scijava.io.AbstractIOPlugin;
import org.scijava.io.IOPlugin;
import org.scijava.io.event.DataOpenedEvent;
import org.scijava.object.ObjectService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.util.FileUtils;

@SuppressWarnings("rawtypes")
@Plugin(type = IOPlugin.class, priority = Priority.LOW)
public class MarsTableIOPlugin extends AbstractIOPlugin<MarsTable> {

	@Parameter
	private EventService eventService;
	
	@Parameter
	private UIService uiService;
	
	@Parameter
	private ObjectService objectService;
	
	@Override
	public Class<MarsTable> getDataType() {
		return MarsTable.class;
	}

	@Override
	public boolean supportsOpen(final String source) {
		final String ext = FileUtils.getExtension(source).toLowerCase();
		return ext.equals("yamt");
	}

	@Override
	public boolean supportsSave(final String source) {
		return supportsOpen(source);
	}
	
	@Override
	public MarsTable open(final String source) throws IOException {

		MarsTable table = new MarsTable(new File(source));
		
		//Seems like this should be called by DefaultIOService, but it isn't.
		eventService.publish(new DataOpenedEvent(source, table));
		
		objectService.addObject(table);
		
		uiService.show(table.getName(), table);
		
		return table;
	}
	
	@Override
	public void save(final MarsTable table, final String destination) throws IOException {
		table.saveAsYAMT(destination);
	}
}