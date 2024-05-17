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

package de.mpg.biochem.mars.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;

public class LogBuilder {

	private ArrayList<String[]> parameters;

	private String version, artifactId, gitBuild;

	public LogBuilder() {
		Properties properties = new Properties();
		try {
			properties.load(this.getClass().getResourceAsStream(
				"/project.properties"));
			version = properties.getProperty("version");
			artifactId = properties.getProperty("artifactId");
			gitBuild = properties.getProperty("buildNumber");
		}
		catch (IOException e) {
			e.printStackTrace();
		}

		parameters = new ArrayList<>();
		addRunParameters();
	}

	public static String buildTitleBlock(String pluginName) {
		String titleBlock = "";
		int charLength = 75;
		int eachHalf = (charLength - pluginName.length() - 2) / 2;

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

	public void addParameter(String parameter, boolean value) {
		addParameter(parameter, String.valueOf(value));
	}

	public void addParameter(String parameter, int value) {
		addParameter(parameter, String.valueOf(value));
	}

	public void addParameter(String parameter, Integer value) {
		addParameter(parameter, value.toString());
	}

	public void addParameter(String parameter, double value) {
		addParameter(parameter, String.valueOf(value));
	}

	public void addParameter(String parameter, Double value) {
		addParameter(parameter, value.toString());
	}

	public void addParameter(String parameter, String value) {
		if (parameters == null) {
			parameters = new ArrayList<>();
		}
		String[] param = new String[2];
		param[0] = parameter;
		param[1] = value;
		parameters.add(param);
	}

	public String buildParameterList() {
		// First we find the longest parameter string and put the colon at the
		// position + 1
		int paramLength = 0;
		for (String[] str : parameters) {
			if (str[0].length() > paramLength) paramLength = str[0].length();
		}
		paramLength += 1;

		char[] chars = new char[paramLength];
		Arrays.fill(chars, ' ');
		String spaces = new String(chars);

		StringBuilder output = new StringBuilder();
		// Now we build the parameter list text...
		for (int i = 0; i < parameters.size() - 1; i++) {
			String[] str = parameters.get(i);
			output.append(str[0]).append(spaces.substring(str[0].length())).append(": ").append(str[1]).append("\n");
		}
		if (parameters.size() > 0) {
			String[] str = parameters.get(parameters.size() - 1);
			output.append(str[0]).append(spaces.substring(str[0].length())).append(": ").append(str[1]);
		}

		return output.toString();
	}

	public static String endBlock(boolean success) {
		if (success)
			return "********************************* Success *********************************";
		else
			return "********************************* Failure *********************************";
	}

	@SuppressWarnings("SameReturnValue")
	public static String endBlock() {
		return "***************************************************************************";
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
