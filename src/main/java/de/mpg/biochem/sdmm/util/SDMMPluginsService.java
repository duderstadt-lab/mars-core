package de.mpg.biochem.sdmm.util;

import java.awt.Frame;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.File;
import java.nio.file.*;
import java.nio.charset.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.service.*;
import org.scijava.plugin.Parameter;
import org.scijava.ui.UIService;

import net.imagej.table.DoubleColumn;
import net.imglib2.type.numeric.RealType;
import net.imagej.ImageJService;

import org.scijava.plugin.AbstractPTService;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.PluginInfo;
import org.scijava.service.Service;

@Plugin(type = Service.class)
public class SDMMPluginsService extends AbstractPTService<SDMMPluginsService> implements ImageJService {
	
    @Parameter
    private UIService uiService;
    
    @Parameter
    private LogService logService;
	
    private Properties properties;
    
    private String version, artifactId, gitBuild;
    
	@Override
	public void initialize() {
		// This Service method is called when the service is first created.
		properties = new Properties();
		try {
			properties.load(this.getClass().getResourceAsStream("/project.properties"));
			version = properties.getProperty("version");
			artifactId = properties.getProperty("artifactId");
			gitBuild = properties.getProperty("buildNumber");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			logService.error("Can't load project.properties or some properties absent, no plugin version information will be provided.");
		}
	}
	
	public String getVersion() {
		return version;
	}

	public String getArtifactId() {
		return artifactId;
	}
	
	public String getBuild() {
		return gitBuild;
	}
	
	@Override
	public Class<SDMMPluginsService> getPluginType() {
		return SDMMPluginsService.class;
	}
}
