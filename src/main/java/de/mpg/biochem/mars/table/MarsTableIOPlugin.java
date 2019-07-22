package de.mpg.biochem.mars.table;

import java.net.URISyntaxException;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.scijava.Priority;
import org.scijava.io.AbstractIOPlugin;
import org.scijava.io.IOPlugin;
import org.scijava.io.handle.DataHandle;
import org.scijava.io.handle.DataHandleService;
import org.scijava.io.location.Location;
import org.scijava.io.location.LocationService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.table.GenericTable;
import org.scijava.table.Table;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.format.DataFormatDetector;
import com.fasterxml.jackson.core.format.DataFormatMatcher;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

@SuppressWarnings("rawtypes")
@Plugin(type = IOPlugin.class, priority = Priority.LOW)
public class MarsTableIOPlugin extends AbstractIOPlugin<MarsTable> {

	@Parameter
	private LocationService locationService;

	@Parameter
	private DataHandleService dataHandleService;
	
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
	
	//This needs cleaning up but lets see if it is working first...
	@Override
	public MarsTable open(final String source) throws IOException {
		final Location sourceLocation;
		try {
			sourceLocation = locationService.resolve(source);
		} catch (final URISyntaxException exc) {
			throw new IOException("Unresolvable source: " + source, exc);
		}
		
		MarsTable table = new MarsTable(new File(source));
		
		return table;
	}
	
	@Override
	public void save(final MarsTable table, final String destination) throws IOException {
		final Location dstLocation;
		try {
			dstLocation = locationService.resolve(destination);
		}
		catch (final URISyntaxException exc) {
			throw new IOException("Unresolvable destination: " + destination, exc);
		}

		try (final DataHandle<Location> handle = dataHandleService.create(dstLocation)) {

		}
	}
}
