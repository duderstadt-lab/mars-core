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

import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JLabel;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;

import org.scijava.ui.swing.widget.SwingInputPanel;
import org.scijava.widget.AbstractInputPanel;
import org.scijava.widget.InputPanel;
import org.scijava.widget.InputWidget;
import org.scijava.widget.WidgetModel;

/**
 * Swing implementation of {@link InputPanel}.
 * 
 * @author Curtis Rueden
 */
public class SwingGroupingInputPanel extends AbstractInputPanel<JPanel, JPanel> {

	private JPanel uiComponent;
	
	private Map<String, SwingInputPanel> uiGroupComponents;

	// -- InputPanel methods --

	@Override
	public void addWidget(final InputWidget<?, JPanel> widget) {
		addWidget(widget, "");
	}
	
	public void addWidget(final InputWidget<?, JPanel> widget, String group) {
		super.addWidget(widget);
		final JPanel widgetPane = widget.getComponent();
		final WidgetModel model = widget.get();

		// add widget to panel
		if (widget.isLabeled()) {
			// widget is prefixed by a label
			final JLabel l = new JLabel(model.getWidgetLabel());
			final String desc = model.getItem().getDescription();
			if (desc != null && !desc.isEmpty()) l.setToolTipText(desc);
			getComponent(group).getComponent().add(l);
			getComponent(group).getComponent().add(widgetPane);
		}
		else {
			// widget occupies entire row
			getComponent(group).getComponent().add(widgetPane, "span");
		}
	}

	@Override
	public Class<JPanel> getWidgetComponentType() {
		return JPanel.class;
	}

	// -- UIComponent methods --
	
	public SwingInputPanel getComponent(String group) {
		if (uiGroupComponents == null)
			uiGroupComponents = new HashMap<String, SwingInputPanel>();
			
		if (!uiGroupComponents.containsKey(group)) {
			 SwingInputPanel panel = new SwingInputPanel();
			 if (!group.equals("")) {
				 if (panel.getComponent().getLayout() instanceof MigLayout)
					 ((MigLayout) panel.getComponent().getLayout()).setLayoutConstraints("fillx, insets 0 15 0 15, gapy 0, wrap 2");
				 
				 JPanel labelPanel = new JPanel(new MigLayout("fillx,insets 10 15 5 15, gapy 0"));
		         JLabel label = new JLabel("<html><strong>▼ " + group + "</strong></html>");
		
		           label.addMouseListener(new MouseAdapter() {
		               /**
		                * Invoked when the mouse button has been clicked (pressed
		                * and released) on a component.
		                * @param e the event to be processed
		                */
		             @Override
		               public void mouseClicked(MouseEvent e) {
		               }
		
		               /**
		                * Invoked when a mouse button has been pressed on a component.
		                * @param e the event to be processed
		                */
		             @Override
		               public void mousePressed(MouseEvent e) {
		            	 panel.getComponent().setVisible(!panel.getComponent().isVisible());
		         		
	                       if(panel.getComponent().isVisible()) {
	                           label.setText("<html><strong>▼ " + group + "</strong></html>");
	                       } else {
	                           label.setText("<html><strong>▶ " + group + "</strong></html>");
	                       }
	                       getComponent().revalidate();
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
		
		          labelPanel.add(label);
		          getComponent().add(labelPanel, "align left, wrap");
			}
			 
			// hidemode 3 ignores the space taken up by components when rendered
			getComponent().add(panel.getComponent(), "wrap, hidemode 3");
			uiGroupComponents.put(group, panel);
		}
		
		return uiGroupComponents.get(group);
	}

	@Override
	public JPanel getComponent() {
		if (uiComponent == null) {
			uiComponent = new JPanel();
			final MigLayout layout =
					new MigLayout("align left, fillx, wrap 1, insets 0");
			uiComponent.setLayout(layout);
		}
		return uiComponent;
	}

	@Override
	public Class<JPanel> getComponentType() {
		return JPanel.class;
	}

}
