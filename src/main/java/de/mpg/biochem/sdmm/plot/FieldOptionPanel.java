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
package de.mpg.biochem.sdmm.plot;

import java.awt.*;
import java.util.*;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.decimal4j.util.DoubleRounder;

public class FieldOptionPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	
	@SuppressWarnings("rawtypes")
	protected Vector stringField, numberField, defaultValues,defaultText;
	private GridBagLayout grid;
    private GridBagConstraints c;
    private int x = 0;
    private int y = 0;
    private int nfIndex = 0;
    private int sfIndex = 0;
    private int field_columns;
	
	public FieldOptionPanel(int cols) {
		field_columns = cols;
		grid = new GridBagLayout();
        c = new GridBagConstraints();
        setLayout(grid);
	}
	
	public void addNumericField(String label, double defaultValue, int digits) {
        addNumericField(label, defaultValue, digits, 6);
    }
	
	public void addStringField(String label, String defaultText) {
        addStringField(label, defaultText, 8);
    }
	
	@SuppressWarnings("unchecked")
	public void addStringField(String label, String defaultText, int columns) {
        String label2 = label;
        if (label2.indexOf('_')!=-1)
            label2 = label2.replace('_', ' ');
        JLabel theLabel = makeLabel(label2);
        c.gridx = x; 
        c.gridy = y;
        c.anchor = GridBagConstraints.EAST;
        c.gridwidth = 1;
        c.insets = new Insets(0, 0, 3, 0);
        grid.setConstraints(theLabel, c);
        add(theLabel);
        if (stringField==null) {
        	stringField = new Vector(4);
        }
        
        JTextField tf = new JTextField(defaultText, columns);
        c.gridx = x+1; 
        c.gridy = y;
        c.anchor = GridBagConstraints.WEST;
        grid.setConstraints(tf, c);
        tf.setEditable(true);
        add(tf);
        stringField.addElement(tf);
        x += 2;
        if (x == field_columns*2) {
        	y++;
        	x=0;
        }
    }
	
	public void advanceRows() {
		y++;
    	x=0;
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void addNumericField(String label, double defaultValue, int digits, int columns) {
        String label2 = label;
        if (label2.indexOf('_')!=-1)
            label2 = label2.replace('_', ' ');
        JLabel theLabel = makeLabel(label2);
        c.gridx = x; 
        c.gridy = y;
        c.anchor = GridBagConstraints.EAST;
        c.gridwidth = 1;
        c.insets = new Insets(0, 0, 3, 0);
        grid.setConstraints(theLabel, c);
        add(theLabel);
        if (numberField==null) {
            numberField = new Vector(5);
            defaultValues = new Vector(5);
            defaultText = new Vector(5);
        }
        
        JTextField tf = new JTextField(String.valueOf(DoubleRounder.round(defaultValue, digits)), columns);
        numberField.addElement(tf);
        defaultValues.addElement(new Double(defaultValue));
        defaultText.addElement(tf.getText());
        c.gridx = x+1; 
        c.gridy = y;
        c.anchor = GridBagConstraints.WEST;
        tf.setEditable(true);
        grid.setConstraints(tf, c);
        add(tf);
        x += 2;
        if (x == field_columns*2) {
        	y++;
        	x=0;
        }
    }
	
	private JLabel makeLabel(String label) {
        //if (IJ.isMacintosh())
         //   label += " ";
        return new JLabel(label);
    }
	
	public double getNextNumber() {
        if (numberField==null)
            return -1.0;
        JTextField tf = (JTextField)numberField.elementAt(nfIndex);
        String theText = tf.getText();
  
        String originalText = (String)defaultText.elementAt(nfIndex);
        double defaultValue = ((Double)(defaultValues.elementAt(nfIndex))).doubleValue();
        double value;
        if (theText.equals(originalText))
            value = defaultValue;
        else {
            Double d = getValue(theText);
            value = d.doubleValue();
        }
        nfIndex++;
        if (nfIndex == numberField.size())
        	nfIndex=0;
        
        return value;
    }
	
	
	public void setEnabled(boolean state) {
		for (int i=0; i < this.getComponentCount() ; i++) {
			this.getComponent(i).setEnabled(state);
		}
	}
	
	 public String getNextString() {
        String theText;
        if (stringField==null)
            return "";
        JTextField tf = (JTextField)(stringField.elementAt(sfIndex));
        theText = tf.getText();
        sfIndex++;
        if (sfIndex == stringField.size())
        	sfIndex=0;
        
        return theText;
    }
	 
	 
	protected Double getValue(String text) {
        Double d;
        try {d = new Double(text);}
        catch (NumberFormatException e){
            d = null;
        }
        return d;
    }
}

