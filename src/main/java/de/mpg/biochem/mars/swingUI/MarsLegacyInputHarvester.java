/*
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2022 Karl Duderstadt
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

package de.mpg.biochem.mars.swingUI;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.JOptionPane;
import javax.swing.JPanel;

import org.scijava.convert.ConvertService;
import org.scijava.log.LogService;
import org.scijava.module.Module;
import org.scijava.module.ModuleException;
import org.scijava.module.ModuleItem;
import org.scijava.module.process.PreprocessorPlugin;
import org.scijava.object.ObjectService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;
import org.scijava.ui.AbstractInputHarvesterPlugin;
import org.scijava.ui.UIService;
import org.scijava.ui.swing.SwingDialog;
import org.scijava.widget.InputHarvester;
import org.scijava.widget.InputPanel;
import org.scijava.widget.InputWidget;
import org.scijava.widget.WidgetModel;
import org.scijava.widget.WidgetService;

import net.imagej.legacy.ui.LegacyUI;

/**
 * Trivial Legacy extension of the {@link org.scijava.ui.swing.widget.SwingInputHarvester}. Just need to
 * link it to the {@link net.imagej.legacy.ui.LegacyUI}.
 */
@Plugin(type = PreprocessorPlugin.class, priority = InputHarvester.PRIORITY)
public class MarsLegacyInputHarvester extends AbstractInputHarvesterPlugin<JPanel, JPanel>
{
	
	@Parameter(required = false)
	private UIService uiService;
	
	@Parameter
	private LogService log;

	@Parameter
	private WidgetService widgetService;

	@Parameter
	private ObjectService objectService;

	@Parameter
	private ConvertService convertService;
	
	@Parameter
	private PrefService prefService;
	
	// -- ModuleProcessor methods --

	@Override
	public void process(final Module module) {
		if (uiService == null) return; // no UI service means no input harvesting!

		// do not harvest if the UI is inactive!
		if (!uiService.isVisible(getUI())) return;

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
	public MarsSwingInputPanel createInputPanel() {
		return new MarsSwingInputPanel();
	}
	
	@Override
	public void buildPanel(final InputPanel<JPanel, JPanel> inputPanel, final Module module)
		throws ModuleException
	{
		final Iterable<ModuleItem<?>> inputs = module.getInfo().inputs();

		final ArrayList<WidgetModel> models = new ArrayList<>();

		for (final ModuleItem<?> item : inputs) {
			final WidgetModel model = addInput(inputPanel, module, item);
			if (model != null) models.add(model);
		}

		// mark all models as initialized
		for (final WidgetModel model : models)
			model.setInitialized(true);

		// compute initial preview
		module.preview();
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
			log.debug("No widget found for input: " + model.getItem().getName());
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

	// -- Internal methods --

	@Override
	protected String getUI() {
		return LegacyUI.NAME;
	}

}
