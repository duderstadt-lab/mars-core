/*******************************************************************************
 * Copyright (C) 2019, Karl Duderstadt
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
package de.mpg.biochem.mars.plot;

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

