/*******************************************************************************
 * Copyright (C) 2019, Duderstadt Lab
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/
package de.mpg.biochem.mars.ImageProcessing;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import de.mpg.biochem.mars.molecule.SdmmImageMetadata;
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
	
	/** Removes names that start with "." or end with ".db", ".txt", ".lut", "roi", ".pty", ".hdr", ".py", etc. 
	 * 
	 * @param rawlist Full list of files in the image directory.
	 * @return Image file list.
	 * */
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
