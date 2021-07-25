/*
 * #%L
 * SciJava UI components for Java Swing.
 * %%
 * Copyright (C) 2010 - 2020 SciJava developers.
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

import org.scijava.object.ObjectService;
import org.scijava.convert.ConvertService;
import org.scijava.log.LogService;
import org.scijava.module.Module;
import org.scijava.module.ModuleException;
import org.scijava.module.ModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.AbstractInputHarvesterPlugin;
import org.scijava.ui.UIService;
import org.scijava.ui.swing.SwingDialog;
import org.scijava.ui.swing.SwingUI;
import org.scijava.ui.swing.widget.SwingInputHarvester;
import org.scijava.ui.swing.widget.SwingInputPanel;
import org.scijava.widget.*;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import java.awt.event.MouseAdapter;

import org.scijava.module.process.PreprocessorPlugin;

@Plugin(type = PreprocessorPlugin.class, priority = SwingInputHarvester.PRIORITY + 1)
public class MarsSwingGroupingInputHarvester extends AbstractInputHarvesterPlugin<JPanel, JPanel>
{

    @Parameter
    private LogService logService;

    @Parameter
    private WidgetService widgetService;

    @Parameter
    private ObjectService objectService;

    @Parameter
    private ConvertService convertService;

    @Parameter
    private UIService uiService;

    @Override
	public void process(final Module module) {
    	if (uiService == null) return; // no UI service means no input harvesting!

		// do not harvest if the UI is inactive!
		//if (!uiService.isVisible(getUI())) return;
    	//Always use this, even in legacy...

		// proceed with input harvesting
		try {
			harvest(module);
		}
		catch (final ModuleException e) {
			cancel(e.getMessage());
		}
	}

    // -- InputHarvester methods --
    @Override
	public void buildPanel(final InputPanel<JPanel, JPanel> inputPanel, final Module module) throws ModuleException
    {
    	final Iterable<ModuleItem<?>> inputs = module.getInfo().inputs();
		final ArrayList<WidgetModel> models = new ArrayList<>();

		Map<String, List<ModuleItem<?>>> groups = StreamSupport.stream(inputs.spliterator(), false).collect(Collectors.groupingBy( input -> {
        	String style = input.getWidgetStyle();
        	if (!style.contains("group:"))
        		return "";

        	String groupName = style.substring(style.indexOf("group:") + 6);
        	if (groupName.contains(","))
        		groupName = groupName.substring(0, groupName.indexOf(","));
        	return groupName;
        }));

        for (String groupName : groups.keySet()) {
            // no empty groups, and skip resolved inputs, aka services
            if (groupName.equals("")) {
            	for (ModuleItem<?> input : groups.get(groupName)) {
                    WidgetModel model = addInput(inputPanel, module, input);
                    if (model != null) models.add(model);
                }
            	continue;
            }

        	if (!groups.get(groupName).stream().filter(input -> !module.isInputResolved(input.getName())).findAny().isPresent())
        		continue;

            SwingInputPanel panel = new SwingInputPanel();
            SwingInputPanel labelPanel = new SwingInputPanel();
            JLabel label = new JLabel("<html><strong>▼ " + groupName + "</strong></html>");

            label.addMouseListener(new MouseAdapter() {
                /**
                 * Invoked when the mouse button has been clicked (pressed
                 * and released) on a component.
                 * @param e the event to be processed
                 */
            	@Override
                public void mouseClicked(MouseEvent e) {
                    if(e.getClickCount() == 1) {
                    	panel.getComponent().setVisible(!panel.getComponent().isVisible());

                        if(panel.getComponent().isVisible()) {
                            label.setText("<html><strong>▼ " + groupName + "</strong></html>");
                        } else {
                            label.setText("<html><strong>▶ " + groupName + "</strong></html>");
                        }
                        inputPanel.getComponent().revalidate();
                    }
                }

                /**
                 * Invoked when a mouse button has been pressed on a component.
                 * @param e the event to be processed
                 */
            	@Override
                public void mousePressed(MouseEvent e) {
                }

                /**
                 * Invoked when a mouse button has been released on a component.
                 * @param e the event to be processed
                 */
            	@Override
                public void mouseReleased(MouseEvent e) {
                }

                /**
                 * Invoked when the mouse enters a component.
                 * @param e the event to be processed
                 */
            	@Override
                public void mouseEntered(MouseEvent e) {
                }

                /**
                 * Invoked when the mouse exits a component.
                 * @param e the event to be processed
                 */
            	@Override
                public void mouseExited(MouseEvent e) {
                }

            });

            labelPanel.getComponent().add(label);
            inputPanel.getComponent().add(labelPanel.getComponent(), "wrap");
            // hidemode 3 ignores the space taken up by components when rendered
            inputPanel.getComponent().add(panel.getComponent(), "wrap,hidemode 3");

            for (ModuleItem<?> input : groups.get(groupName)) {
                WidgetModel model = addInput(panel, module, input);
                if (model != null) models.add(model);
            }
        }

        // mark all models as initialized
        for (WidgetModel model : models) 
          model.setInitialized(true);

        // compute initial preview
        module.preview();
    }

    // -- Helper methods --

 	private <T> WidgetModel addInput(final InputPanel<JPanel, JPanel> inputPanel,
 		final Module module, final ModuleItem<T> item) throws ModuleException
 	{
 		final String name = item.getName();
 		final boolean resolved = module.isInputResolved(name);
 		if (resolved) return null; // skip resolved inputs

 		final Class<T> type = item.getType();
 		final WidgetModel model =
 			widgetService.createModel(inputPanel, module, item, getObjects(type));

 		final Class<JPanel> widgetType = inputPanel.getWidgetComponentType();
 		final InputWidget<?, ?> widget = widgetService.create(model);
 		if (widget == null) {
 			logService.debug("No widget found for input: " + model.getItem().getName());
 		}
 		if (widget != null && widget.getComponentType() == widgetType) {
 			@SuppressWarnings("unchecked")
 			final InputWidget<?, JPanel> typedWidget = (InputWidget<?, JPanel>) widget;
 			inputPanel.addWidget(typedWidget);
 			return model;
 		}

 		if (item.isRequired()) {
 			throw new ModuleException("A " + type.getSimpleName() +
 				" is required but none exist.");
 		}

 		// item is not required; we can skip it
 		return null;
 	}

    /** Asks the object service and convert service for valid choices */
	@SuppressWarnings("unchecked")
	private List<?> getObjects(final Class<?> type) {
		@SuppressWarnings("rawtypes")
		Set compatibleInputs =
				new HashSet(convertService.getCompatibleInputs(type));
		compatibleInputs.addAll(objectService.getObjects(type));
		return new ArrayList<>(compatibleInputs);
	}

    @Override
	public boolean harvestInputs(final InputPanel<JPanel, JPanel> inputPanel,
		final Module module)
	{
		final JPanel pane = inputPanel.getComponent();

		// display input panel in a dialog
		final String title = module.getInfo().getTitle();
		final boolean modal = !module.getInfo().isInteractive();
		final boolean allowCancel = module.getInfo().canCancel();
		final int optionType, messageType;
		if (allowCancel) optionType = JOptionPane.OK_CANCEL_OPTION;
		else optionType = JOptionPane.DEFAULT_OPTION;
		if (inputPanel.isMessageOnly()) {
			if (allowCancel) messageType = JOptionPane.QUESTION_MESSAGE;
			else messageType = JOptionPane.INFORMATION_MESSAGE;
		}
		else messageType = JOptionPane.PLAIN_MESSAGE;
		final boolean doScrollBars = messageType == JOptionPane.PLAIN_MESSAGE;
		final SwingDialog dialog =
			new SwingDialog(pane, optionType, messageType, doScrollBars);
		dialog.setTitle(title);
		dialog.setModal(modal);
		final int rval = dialog.show();

		// verify return value of dialog
		return rval == JOptionPane.OK_OPTION;
	}

	// -- Internal methods --

	@Override
	protected String getUI() {
		return SwingUI.NAME;//"legacy";
	}

	@Override
	public InputPanel<JPanel, JPanel> createInputPanel() {
		return new SwingInputPanel();
	}

}