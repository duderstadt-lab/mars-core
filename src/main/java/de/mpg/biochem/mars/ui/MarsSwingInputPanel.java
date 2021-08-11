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

package de.mpg.biochem.mars.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import net.miginfocom.swing.MigLayout;

import org.scijava.ItemVisibility;
import org.scijava.widget.AbstractInputPanel;
import org.scijava.widget.InputPanel;
import org.scijava.widget.InputWidget;
import org.scijava.widget.WidgetModel;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Swing implementation of {@link InputPanel}.
 * 
 * @author Curtis Rueden
 * @author Karl Duderstadt
 */
public class MarsSwingInputPanel extends AbstractInputPanel<JPanel, JPanel> {

	private JPanel uiComponent;
	
	private JTabbedPane tabbedPane;
	private Map<String, JPanel> tabPanels;

	// -- InputPanel methods --

	@Override
	public void addWidget(final InputWidget<?, JPanel> widget) {
		super.addWidget(widget);
		final JPanel widgetPane = widget.getComponent();
		final WidgetModel model = widget.get();
		final String style = (model.getItem().getWidgetStyle() == null) ? "" : model.getItem().getWidgetStyle();
		
		String groupString = "";
		if (model.getItem().getVisibility() == ItemVisibility.MESSAGE && style.contains("groupLabel")) 
			groupString = (String) model.getValue();
		else if (style.contains("group:")) {
			groupString = style.substring(style.indexOf("group:") + 6);
        	if (groupString.contains(","))
        		groupString = groupString.substring(0, groupString.indexOf(","));
		}
		final String group = groupString;
		
		// add widget to panel
		if (model.getItem().getVisibility() == ItemVisibility.MESSAGE && style.contains("groupLabel")) {
			addTab(group);
			
			//dialogSize:[200 200]
			
			if (style.contains("tabbedPaneWidth:")) {
				String widthString = style.substring(style.indexOf("tabbedPaneWidth:") + 16);
	        	if (widthString.contains(","))
	        		widthString = widthString.substring(0, widthString.indexOf(","));

	        	int width = Integer.valueOf(widthString);
	        	
	        	tabbedPane.setPreferredSize(new Dimension(width, tabbedPane.getPreferredSize().height));
			}
			
		} else if (widget.isLabeled()) {
			// widget is prefixed by a label
			final JLabel l = new JLabel(model.getWidgetLabel());
			final String desc = model.getItem().getDescription();
			if (desc != null && !desc.isEmpty()) l.setToolTipText(desc);
			if (tabbedPane != null && !group.equals("")) {
				if (!tabPanels.containsKey(group))
					addTab(group); 
				
				getTabPanel(group).add(l);
				getTabPanel(group).add(widgetPane);
			} else {
				getComponent().add(l);
				getComponent().add(widgetPane);
			}
		}
		else {
			if (tabbedPane != null && !group.equals("")) {
				if (!tabPanels.containsKey(group))
					addTab(group); 
				
				// widget occupies entire row
				getTabPanel(group).add(widgetPane, "span");
			} else {
				// widget occupies entire row
				getComponent().add(widgetPane, "span");
			}
		}
	}
	
	private void addTab(String name) {
		if (tabbedPane == null) {
			tabbedPane = new JTabbedPane();
			getComponent().add(tabbedPane, "growx, growy, span 2");
		}
		
		if (tabPanels == null)
			tabPanels = new HashMap<String, JPanel>();
		
		JPanel panel = new JPanel();
		panel.setLayout(new MigLayout("fillx,wrap 2", "[right]10[fill,grow]"));
		tabPanels.put(name, panel);
		tabbedPane.add(name, panel);
	}
	
	private JPanel getTabPanel(String name) {
		if (tabPanels != null && tabPanels.containsKey(name))
			return tabPanels.get(name);
		else 
			return null;
	}

	@Override
	public Class<JPanel> getWidgetComponentType() {
		return JPanel.class;
	}

	// -- UIComponent methods --

	@Override
	public JPanel getComponent() {
		if (uiComponent == null) {
			uiComponent = new JPanel();
			final MigLayout layout =
					new MigLayout("fillx,wrap 2", "[right]10[fill,grow]");
			uiComponent.setLayout(layout);
		}
		return uiComponent;
	}

	@Override
	public Class<JPanel> getComponentType() {
		return JPanel.class;
	}

}

