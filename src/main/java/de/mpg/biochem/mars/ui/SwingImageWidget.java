package de.mpg.biochem.mars.ui;

import java.io.IOException;

import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

import org.scijava.Priority;
import org.scijava.log.LogService;
import org.scijava.platform.PlatformService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.swing.widget.SwingInputWidget;
import org.scijava.widget.InputWidget;
import org.scijava.widget.MessageWidget;
import org.scijava.widget.WidgetModel;

/**
 * Swing implementation of image widget.
 * 
 * @author Karl Duderstadt
 */
@Plugin(type = InputWidget.class, priority = Priority.HIGH)
public class SwingImageWidget extends SwingInputWidget<Image> implements
	ImageWidget<JPanel>
{
	@Parameter
	private PlatformService platformService;

	private JPanel pane;

	// -- InputWidget methods --

	@Override
	public Image getValue() {
		return null;
	}

	@Override
	public boolean isLabeled() {
		final String l = get().getItem().getLabel();
		return l != null && !l.isEmpty();
	}

	@Override
	public boolean isMessage() {
		return true;
	}

	// -- WrapperPlugin methods --

	@Override
	public void set(final WidgetModel model) {
		super.set(model);

		final String text = model.getText();

		pane = new JPanel();

		// add the image here !!
		
		
		getComponent().add(pane);
	}

	// -- Typed methods --

	@Override
	public boolean supports(final WidgetModel model) {
		return super.supports(model) && model.isMessage();
	}

	// -- AbstractUIInputWidget methods ---

	@Override
	public void doRefresh() {
		// maybe dialog owner changed message content
		//pane.setText(get().getText());
		//I guess refresh the image here somehow from the value ??
	}
}