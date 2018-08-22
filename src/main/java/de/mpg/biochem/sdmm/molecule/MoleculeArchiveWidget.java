package de.mpg.biochem.sdmm.molecule;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JComboBox;
import javax.swing.JPanel;

import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.*;
import org.scijava.widget.WidgetModel;

import org.scijava.ui.swing.widget.SwingInputWidget;

/**
 * Swing implementation of multiple choice selector widget for MoleculeArchive selection.
 * 
 * @author Karl Duderstadt
 */
@Plugin(type = InputWidget.class)
public class MoleculeArchiveWidget extends SwingInputWidget<MoleculeArchive> implements InputWidget<MoleculeArchive, JPanel>, ActionListener {

	@Parameter
    private MoleculeArchiveService moleculeArchiveService;
	
	@Parameter
	private LogService logService;	
		
	private JComboBox<Object> comboBox;

	@Override
	public void actionPerformed(final ActionEvent e) {
		updateModel();
	}

	// -- InputWidget methods --

	@Override
	public MoleculeArchive getValue() {
		return moleculeArchiveService.getArchive((String)comboBox.getSelectedItem());
	}

	// -- WrapperPlugin methods --

	@Override
	public void set(final WidgetModel model) {
		super.set(model);
		
		String[] items = new String[moleculeArchiveService.getArchiveNames().size()];
		moleculeArchiveService.getArchiveNames().toArray(items);

		comboBox = new JComboBox<>(items);
		setToolTip(comboBox);
		getComponent().add(comboBox);
		comboBox.addActionListener(this);

		updateModel();
		
		refreshWidget();
	}

	// -- Typed methods --

	@Override
	public boolean supports(final WidgetModel model) {
		return super.supports(model) && model.isType(MoleculeArchive.class) && moleculeArchiveService.getArchiveNames().size() > 0;
	}

	// -- AbstractUIInputWidget methods ---

	@Override
	public void doRefresh() {
	//	final String value = get().getValue().toString();
	//	if (value.equals(comboBox.getSelectedItem())) return; // no change
	//	comboBox.setSelectedItem(value);
	}
}
