/*******************************************************************************
 * MARS - MoleculeArchive Suite - A collection of ImageJ2 commands for single-molecule analysis.
 * 
 * Copyright (C) 2018 - 2019 Karl Duderstadt
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package de.mpg.biochem.sdmm.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;

import java.util.Properties;

public class LogBuilder {
	private ArrayList<String[]> parameters;
	
    private Properties properties;
    
    private String version, artifactId, gitBuild;
	
	public LogBuilder() {
		properties = new Properties();
		try {
			properties.load(this.getClass().getResourceAsStream("/project.properties"));
			version = properties.getProperty("version");
			artifactId = properties.getProperty("artifactId");
			gitBuild = properties.getProperty("buildNumber");
		} catch (IOException e) {
			// TODO Auto-generated catch block
		}
		
		parameters = new ArrayList<String[]>();
		addRunParameters();
	}
	
	public String buildTitleBlock(String pluginName) {
		String titleBlock = "";
		int charLength = 75;
		int eachHalf = (charLength - pluginName.length() - 2)/2;
		
		char[] chars = new char[eachHalf];
		Arrays.fill(chars, '*');
		String stars = new String(chars);
		
		titleBlock += stars;
		titleBlock += " " + pluginName + " ";
		titleBlock += stars;
		titleBlock += "\n";
		
		return titleBlock;
	}
	
	public void newParameterList() {
		parameters.clear();
		addRunParameters();
	}
	
	public void clearParameterList() {
		parameters.clear();
	}
	
	private void addRunParameters() {
		addParameter("Time", new java.util.Date() + "");
		addParameter("Version", getVersion());
		addParameter("Git Build", getBuild());
	}
	
	public void addParameter(String parameter, String value) {
		if (parameters == null) {
			parameters = new ArrayList<String[]>();
		}
		String[] param = new String[2];
		param[0] = parameter;
		param[1] = value;
		parameters.add(param);
	}
	
	public String buildParameterList() {
		//First we find the longest parameter string and put the colon at the position + 1
		int paramLength = 0;
		for (String[] str: parameters) {
			if (str[0].length() > paramLength)
				paramLength = str[0].length();
		}
		paramLength += 1;
		
		char[] chars = new char[paramLength];
		Arrays.fill(chars, ' ');
		String spaces = new String(chars);
		
		String output = "";
		//Now we build the parameter list text...
		for (String[] str: parameters) {
			output += str[0] + spaces.substring(str[0].length()) + ": " + str[1] + "\n";
		}
		
		return output;
	}
	
	public String endBlock(boolean success) {
		if (success)
			return "********************************* Success *********************************";
		else
			return "********************************* Failure *********************************";
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
}
