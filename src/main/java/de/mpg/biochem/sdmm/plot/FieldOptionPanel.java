package de.mpg.biochem.sdmm.plot;

import java.awt.*;
import java.util.*;
import ij.IJ;

public class FieldOptionPanel extends Panel {
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
        Label theLabel = makeLabel(label2);
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
        
        TextField tf = new TextField(defaultText, columns);
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
        Label theLabel = makeLabel(label2);
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
    
        TextField tf = new TextField(IJ.d2s(defaultValue, digits), columns);
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
	
	private Label makeLabel(String label) {
        if (IJ.isMacintosh())
            label += " ";
        return new Label(label);
    }
	
	public double getNextNumber() {
        if (numberField==null)
            return -1.0;
        TextField tf = (TextField)numberField.elementAt(nfIndex);
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
        TextField tf = (TextField)(stringField.elementAt(sfIndex));
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

