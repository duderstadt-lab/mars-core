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
package de.mpg.biochem.sdmm.ImageProcessing;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import de.mpg.biochem.sdmm.molecule.ImageMetaData;
import ij.util.StringSorter;

// Has some small parts of imagej1 FolderOpener.java
//We keep it general for the moment, but add a tif filter since all our current files are tifs..

public class ImageList {
	private static String[] excludedTypes = {".txt", ".lut", ".roi", ".pty", ".hdr", ".java", ".ijm", ".py", ".js", ".bsh", ".xml"};
	private static String filter = ".tif";
	
	public static String[] getImageList(String directory) {
		String[] list = (new File(directory)).list();
		if (list==null) return null;
		list = tifFileList(list);
		if (list==null) return null;
		
		/** Sorts file names containing numerical components.
	     * @see ij.util.StringSorter#sortNumerically
	     * Author: Norbert Vischer
	     */
		list = StringSorter.sortNumerically(list);
		
		return list;
	}
	
	public static ConcurrentMap<Integer, String> getImageMap(String directory) {
		String[] list = getImageList(directory);
		
		ConcurrentMap<Integer, String> imageMap = new ConcurrentHashMap<>();
		
		//For the moment we take the first frame as the first slice and number purely based on numerical ordering of file names...
		for (int i=0;i<list.length;i++) 
			imageMap.put(i+1, list[i]);
		
		return imageMap;
	}
	
	public static String[] tifFileList(String[] rawlist) {
		int count = 0;
        for (int i=0; i< rawlist.length; i++) {
            String name = rawlist[i];
            if (!name.endsWith(filter))
                rawlist[i] = null;
            else
                count++;
        }
        if (count==0) return null;
        String[] list = rawlist;
        if (count<rawlist.length) {
            list = new String[count];
            int index = 0;
            for (int i=0; i< rawlist.length; i++) {
                if (rawlist[i]!=null)
                    list[index++] = rawlist[i];
            }
        }
        return list;
	}
	
	/** Removes names that start with "." or end with ".db", ".txt", ".lut", "roi", ".pty", ".hdr", ".py", etc. */
    public static String[] trimFileList(String[] rawlist) {
        int count = 0;
        for (int i=0; i< rawlist.length; i++) {
            String name = rawlist[i];
            if (name.startsWith(".")||name.equals("Thumbs.db")||excludedFileType(name))
                rawlist[i] = null;
            else
                count++;
        }
        if (count==0) return null;
        String[] list = rawlist;
        if (count<rawlist.length) {
            list = new String[count];
            int index = 0;
            for (int i=0; i< rawlist.length; i++) {
                if (rawlist[i]!=null)
                    list[index++] = rawlist[i];
            }
        }
        return list;
    }
    /* Returns true if 'name' ends with ".txt", ".lut", ".roi", ".pty", ".hdr", ".java", ".ijm", ".py", ".js" or ".bsh. */
    public static boolean excludedFileType(String name) {
        if (name==null) return true;
        for (int i=0; i<excludedTypes.length; i++) {
            if (name.endsWith(excludedTypes[i]))
                return true;
        }
        return false;
    }
}
