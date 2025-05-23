/*
 * Copyright (c) 2006-, IPD Boehm, Universitaet Karlsruhe (TH) / KIT, by Guido Sautter
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the Universitaet Karlsruhe (TH) nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY UNIVERSITAET KARLSRUHE (TH) / KIT AND CONTRIBUTORS 
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package de.uka.ipd.idaho.im.utilities;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.image.BufferedImage;

import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;

import de.uka.ipd.idaho.gamta.util.swing.DialogFactory;

/**
 * Utility for showing images
 * 
 * @author sautter
 */
public class ImageDisplayDialog extends JPanel {
	private JDialog dialog;
	private JTabbedPane tabs = new JTabbedPane();
	private Color background;
	private boolean unshown = true;
	public ImageDisplayDialog(String title) {
		this(title, null);
	}
	public ImageDisplayDialog(String title, Color background) {
		this.dialog = DialogFactory.produceDialog(title, true);
		this.dialog.getContentPane().setLayout(new BorderLayout());
		this.dialog.getContentPane().add(this.tabs, BorderLayout.CENTER);
		this.tabs.setTabPlacement(JTabbedPane.LEFT);
		this.tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
		this.background = background;
	}
	public void addImage(BufferedImage image, String title) {
		this.addImage(image, title, this.background);
	}
	public void addImage(final BufferedImage image, String title, final Color background) {
		JPanel imagePanel = new JPanel() {
			public void paintComponent(Graphics graphics) {
				super.paintComponent(graphics);
				if (background != null) {
					graphics.setColor(background);
					graphics.fillRect(0, 0, this.getWidth(), this.getHeight());
				}
				graphics.drawImage(image, 0, 0, this.getWidth(), this.getHeight(), this);
			}
		};
		imagePanel.setPreferredSize(new Dimension(image.getWidth(), image.getHeight()));
		JPanel imageTray = new JPanel(new GridBagLayout());
		imageTray.add(imagePanel, new GridBagConstraints());
		JScrollPane imageBox = new JScrollPane(imageTray);
		imageBox.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
		imageBox.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		if (title == null)
			title = "<null>";
		else if (128 < title.length())
			title = (title.substring(0, 124) + " ...");
		this.tabs.addTab(title, imageBox);
		this.tabs.setSelectedComponent(imageBox);
	}
	public void setVisible(boolean visible) {
		super.setVisible(visible);
		if (this.unshown && (this.tabs.getTabCount() != 0)) /* jump to first only before opening _first_ */ {
			this.tabs.setSelectedIndex(0);
			this.unshown = false;
		}
		this.dialog.setVisible(visible);
	}
	public void setSize(int width, int height) {
		this.dialog.setSize(width, height);
	}
	public void setLocationRelativeTo(Component comp) {
		this.dialog.setLocationRelativeTo(comp);
	}
	public int getImageCount() {
		return this.tabs.getTabCount();
	}
}
