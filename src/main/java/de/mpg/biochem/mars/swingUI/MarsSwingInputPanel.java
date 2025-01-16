/*
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2025 Karl Duderstadt
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

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import org.scijava.ItemVisibility;
import org.scijava.widget.AbstractInputPanel;
import org.scijava.widget.InputPanel;
import org.scijava.widget.InputWidget;
import org.scijava.widget.WidgetModel;

import net.miginfocom.swing.MigLayout;

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
		final String style = (model.getItem().getWidgetStyle() == null) ? "" : model
			.getItem().getWidgetStyle();

		String groupString = "";
		if (model.getItem().getVisibility() == ItemVisibility.MESSAGE && style
			.contains("groupLabel")) groupString = (String) model.getValue();
		else if (style.contains("group:")) groupString = styleFieldValue(style,
			"group");
		final String group = groupString;

		// add widget to panel
		if (model.getItem().getVisibility() == ItemVisibility.MESSAGE && style
			.contains("groupLabel"))
		{
			addTab(group);

			if (style.contains("tabbedPaneWidth:")) {
				int width = Integer.parseInt(styleFieldValue(style, "tabbedPaneWidth"));
				tabbedPane.setPreferredSize(new Dimension(width, tabbedPane
					.getPreferredSize().height));
			}

		}
		else if (model.getItem().getVisibility() == ItemVisibility.MESSAGE && style
			.contains("image"))
		{
			try {
				BufferedImage wPic = (isRetina()) ? ImageIO.read(Objects.requireNonNull(getClass().getResource(
						"/2x/" + model.getValue()))) : ImageIO.read(Objects.requireNonNull(getClass()
						.getResource("/1x/" + model.getValue())));
				JLabel wIcon = new JLabel(new RetinaImageIcon(wPic));
				if (tabbedPane != null && !group.equals("")) {
					if (!tabPanels.containsKey(group)) addTab(group);

					// widget occupies entire row
					JPanel groupPanel = getTabPanel(group);
					if (groupPanel != null) groupPanel.add(wIcon, "center, span");
				}
				else {
					// widget occupies entire row
					getComponent().add(wIcon, "center, span");
				}
			}
			catch (IOException e) {
				// Fail silently...
			}
		}
		else if (widget.isLabeled()) {
			// widget is prefixed by a label
			final JLabel l = new JLabel(model.getWidgetLabel());
			final String desc = model.getItem().getDescription();
			if (desc != null && !desc.isEmpty()) l.setToolTipText(desc);
			if (tabbedPane != null && !group.equals("")) {
				if (!tabPanels.containsKey(group)) addTab(group);

				JPanel groupPanel = getTabPanel(group);
				if (groupPanel != null) {
					groupPanel.add(l);
					groupPanel.add(widgetPane);
				}
			}
			else {
				getComponent().add(l);
				getComponent().add(widgetPane);
			}
		}
		else {
			String alignment = (style.contains("align:")) ? this.styleFieldValue(style,
				"align") + ", " : "";
			if (tabbedPane != null && !group.equals("")) {
				if (!tabPanels.containsKey(group)) addTab(group);

				// widget occupies entire row
				JPanel groupPanel = getTabPanel(group);
				if (groupPanel != null) groupPanel.add(widgetPane, alignment + "span");
			}
			else {
				// widget occupies entire row
				getComponent().add(widgetPane, alignment + "span");
			}
		}
	}

	private void addTab(String name) {
		if (tabbedPane == null) {
			tabbedPane = new JTabbedPane();
			getComponent().add(tabbedPane, "growx, growy, span 2");
		}

		if (tabPanels == null) tabPanels = new HashMap<>();

		JPanel panel = new JPanel();
		panel.setLayout(new MigLayout("fillx,wrap 2", "[right]10[fill,grow]"));
		tabPanels.put(name, panel);
		tabbedPane.add(name, panel);
	}

	private JPanel getTabPanel(String name) {
		if (tabPanels != null && tabPanels.containsKey(name)) return tabPanels.get(
			name);
		else return null;
	}

	@Override
	public Class<JPanel> getWidgetComponentType() {
		return JPanel.class;
	}

	private String styleFieldValue(String style, String field) {
		String valueString = "";
		if (style.contains(field + ":")) {
			valueString = style.substring(style.indexOf(field + ":") + field
				.length() + 1);
			if (valueString.contains(",")) valueString = valueString.substring(0,
				valueString.indexOf(","));
		}
		return valueString;
	}

	// -- UIComponent methods --

	@Override
	public JPanel getComponent() {
		if (uiComponent == null) {
			uiComponent = new JPanel();
			final MigLayout layout = new MigLayout("fillx,wrap 2",
				"[right]10[fill,grow]");
			uiComponent.setLayout(layout);
		}
		return uiComponent;
	}

	@Override
	public Class<JPanel> getComponentType() {
		return JPanel.class;
	}

	private static boolean isRetina() {

		boolean isRetina = false;
		GraphicsDevice graphicsDevice = GraphicsEnvironment
			.getLocalGraphicsEnvironment().getDefaultScreenDevice();

		try {
			Field field = graphicsDevice.getClass().getDeclaredField("scale");
			field.setAccessible(true);
			Object scale = field.get(graphicsDevice);
			if (scale instanceof Integer && (Integer) scale == 2) {
				isRetina = true;
			}
		}
		catch (Exception ignored) {}
		return isRetina;
	}

	private static class RetinaImageIcon extends ImageIcon {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		RetinaImageIcon(BufferedImage image) {
			super(image);
		}

		@Override
		public int getIconWidth() {
			if (isRetina()) {
				return super.getIconWidth() / 2;
			}
			return super.getIconWidth();
		}

		@Override
		public int getIconHeight() {
			if (isRetina()) {
				return super.getIconHeight() / 2;
			}
			return super.getIconHeight();
		}

		@Override
		public synchronized void paintIcon(Component c, Graphics g, int x, int y) {
			ImageObserver observer = getImageObserver();

			if (observer == null) {
				observer = c;
			}

			Image image = getImage();
			int width = image.getWidth(observer);
			int height = image.getHeight(observer);
			final Graphics2D g2d = (Graphics2D) g.create(x, y, width, height);

			if (isRetina()) {
				g2d.scale(0.5, 0.5);
			}
			g2d.drawImage(image, 0, 0, observer);
			g2d.scale(1, 1);
			g2d.dispose();
		}

	}

}
