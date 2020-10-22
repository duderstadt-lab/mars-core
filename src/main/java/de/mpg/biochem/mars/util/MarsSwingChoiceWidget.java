package de.mpg.biochem.mars.util;


import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;

import javax.swing.JComboBox;
import javax.swing.JPanel;

import org.scijava.Priority;
import org.scijava.plugin.Plugin;
import org.scijava.widget.ChoiceWidget;
import org.scijava.widget.InputWidget;
import org.scijava.widget.WidgetModel;
import org.scijava.ui.swing.widget.*;

/**
 * Swing implementation of multiple choice selector widget using a
 * {@link JComboBox}.
 * 
 * @author Curtis Rueden
 * 
 * Small edits to ensure list changes are recognized when doRefresh() is called.
 * 
 * @author Karl Duderstadt
 */
@Plugin(type = InputWidget.class, priority = SwingChoiceWidget.PRIORITY + 0.1)
public class MarsSwingChoiceWidget extends SwingInputWidget<String> implements
	ActionListener, ChoiceWidget<JPanel>
{

	public static final double PRIORITY = Priority.NORMAL + 0.1;

	private JComboBox<String> comboBox;

	// -- ActionListener methods --

	@Override
	public void actionPerformed(final ActionEvent e) {
		updateModel();
	}

	// -- InputWidget methods --

	@Override
	public String getValue() {
		if (comboBox.getItemCount() > 0)
			return comboBox.getSelectedItem().toString();
		else
			return null;
	}

	// -- WrapperPlugin methods --

	@Override
	public void set(final WidgetModel model) {
		super.set(model);

		final String[] items = model.getChoices();

		comboBox = new JComboBox<>(items);
		setToolTip(comboBox);
		getComponent().add(comboBox);
		comboBox.addActionListener(this);

		refreshWidget();
	}

	// -- Typed methods --

	@Override
	public boolean supports(final WidgetModel model) {
		return super.supports(model) && model.isText() && model.isMultipleChoice();
	}

	// -- AbstractUIInputWidget methods ---

	@Override
	public void doRefresh() {
		final String[] choices = get().getChoices();
		
		if (!Arrays.equals(choices, comboBoxItems())) {
			comboBox.removeAllItems();	
			for (int i=0; i<choices.length; i++)
				comboBox.addItem(choices[i]);
		} else {
			final Object value = get().getValue();
			if (value.equals(comboBox.getSelectedItem())) return;
			comboBox.setSelectedItem(value);
		}
	}
	
	private String[] comboBoxItems() {
		String[] comboItems = new String[comboBox.getItemCount()];
		for (int i=0; i <comboBox.getItemCount(); i++)
			comboItems[i] = comboBox.getItemAt(i);
		
		return comboItems;
	}
}
