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
 *     * Neither the name of the Universitaet Karlsruhe (TH) / KIT nor the
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
package de.uka.ipd.idaho.im.util;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.UIManager;

import de.uka.ipd.idaho.gamta.util.imaging.BoundingBox;
import de.uka.ipd.idaho.gamta.util.imaging.PageImage;
import de.uka.ipd.idaho.gamta.util.imaging.PageImageInputStream;
import de.uka.ipd.idaho.gamta.util.swing.DialogFactory;
import de.uka.ipd.idaho.im.ImDocument;
import de.uka.ipd.idaho.im.ImPage;
import de.uka.ipd.idaho.im.ImWord;
import de.uka.ipd.idaho.im.analysis.Imaging;

/**
 * Page image editor widget. The image editing functionality is extensible by
 * means of adding <code>ImImageEditTool</code> instances.
 * 
 * @author sautter
 */
public class ImImageEditorPanel extends JPanel {
	
	private JPanel toolPanel;
	private JPanel imagePanel;
	
	private DirectImageEditToolButton activeToolButton = null;
	private DirectImageEditTool activeTool = null;
	
	private ImPage page;
	private PageImage pageImage;
	private BufferedImage image;
	private int imageModCount = 0;
	private ArrayList pageWords = new ArrayList();
	private HashSet pageWordIDs = new HashSet();
	
	private Color wordBoxColor;
	
	private JButton undoButton = new JButton("UNDO");
	private LinkedList undoActions = new LinkedList();
	
	/** Constructor
	 * @param pi the page image to edit
	 * @param tools the editing tools
	 * @param words the words in the page
	 * @param wordBoxColor the color for word bounding boxes
	 */
	public ImImageEditorPanel(PageImage pi, ImImageEditTool[] tools, ImPage page, Color wordBoxColor) {
		super(new BorderLayout(), true);
		
		this.pageImage = pi;
		this.image = new BufferedImage(this.pageImage.image.getWidth(), this.pageImage.image.getHeight(), BufferedImage.TYPE_4BYTE_ABGR);
		this.image.createGraphics().drawImage(this.pageImage.image, 0, 0, this.image.getWidth(), this.image.getHeight(), null);
		
		if (page == null)
			this.page = new ImPage(new ImDocument("DUMMY"), -1, new BoundingBox(0, 0, this.pageImage.image.getWidth(), this.pageImage.image.getHeight()));
		else {
			this.page = page;
			ImWord[] words = this.page.getWords();
			for (int w = 0; w < words.length; w++) {
				this.pageWords.add(words[w]);
				this.pageWordIDs.add(words[w].getLocalID());
			}
			Collections.sort(this.pageWords, ImUtils.textStreamOrder);
		}
		
		this.wordBoxColor = wordBoxColor;
		
		this.imagePanel = new JPanel() {
			public void paint(Graphics gr) {
				super.paint(gr);
				gr.drawImage(image, 0, 0, image.getWidth(), image.getHeight(), null);
				if (pageWords != null) {
					BufferedImage wi = getWordImage();
					gr.drawImage(wi, 0, 0, wi.getWidth(), wi.getHeight(), null);
				}
				if (activeTool != null)
					activeTool.paint(gr);
			}
			public Dimension getPreferredSize() {
				if (this.imageSize == null)
					this.imageSize = new Dimension(image.getWidth(), image.getHeight());
				return this.imageSize;
			}
			private Dimension imageSize = null;
		};
		this.imagePanel.addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent me) {
				if (activeTool != null) {
					activeTool.mousePressed(ImImageEditorPanel.this, me.getX(), me.getY());
					imagePanel.repaint();
				}
			}
			public void mouseReleased(MouseEvent me) {
				if (activeTool != null) {
					ImImageEditUndoAction undoAction = activeTool.mouseReleased(ImImageEditorPanel.this, me.getX(), me.getY());
					imagePanel.repaint();
					undoActions.addFirst(undoAction);
					undoButton.setEnabled(true);
				}
			}
		});
		this.imagePanel.addMouseMotionListener(new MouseAdapter() {
			public void mouseDragged(MouseEvent me) {
				if (activeTool != null) {
					activeTool.mouseDragged(ImImageEditorPanel.this, me.getX(), me.getY());
					imagePanel.repaint();
				}
			}
		});
		
		this.undoButton.setBorder(BorderFactory.createRaisedBevelBorder());
		this.undoButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				if (undoActions.size() != 0) {
					ImImageEditUndoAction undoAction = ((ImImageEditUndoAction) undoActions.removeFirst());
					undoAction.doUndo(ImImageEditorPanel.this);
					wordImage = null;
					imagePanel.repaint();
				}
				undoButton.setEnabled(undoActions.size() != 0);
			}
		});
		this.undoButton.setEnabled(false);
		
		//	sort tools into ones that work on whole page and ones that work selection based
		ArrayList mietButtonList = new ArrayList();
		ArrayList dietButtonList = new ArrayList();
		for (int t = 0; t < tools.length; t++) {
			if (tools[t] instanceof MenuImageEditTool)
				mietButtonList.add(new MenuImageEditToolButton((MenuImageEditTool) tools[t]));
			else if (tools[t] instanceof DirectImageEditTool)
				dietButtonList.add(new DirectImageEditToolButton((DirectImageEditTool) tools[t]));
		}
		
		this.toolPanel = new JPanel(new GridLayout(0, 1, 2, 2));
		
		//	add word color button
		final JButton wordBoxColorButton = new JButton("Word Box Color");
		wordBoxColorButton.setToolTipText("Click to change color used for word bounding boxes");
		wordBoxColorButton.setBorder(BorderFactory.createRaisedBevelBorder());
		wordBoxColorButton.setOpaque(true);
		wordBoxColorButton.setBackground(this.wordBoxColor);
		wordBoxColorButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				Color wbc = JColorChooser.showDialog(DialogFactory.getTopWindow(), "Select Word Box Color", ImImageEditorPanel.this.wordBoxColor);
				if (wbc == null)
					return;
				ImImageEditorPanel.this.wordBoxColor = wbc;
				wordBoxColorButton.setBackground(ImImageEditorPanel.this.wordBoxColor);
				ImImageEditorPanel.this.wordImage = null;
				ImImageEditorPanel.this.imagePanel.validate();
				ImImageEditorPanel.this.imagePanel.repaint();
			}
		});
		this.toolPanel.add(wordBoxColorButton);
		this.toolPanel.add(new JLabel(" "));
		
		//	add whole-page edit tools and a label (with explanatory tooltip)
		if (mietButtonList.size() != 0) {
			JLabel mietLabel = new JLabel("Whole-Image Tools");
			mietLabel.setToolTipText("Below tools process the whole page (image and/or words) for a single click.");
			this.toolPanel.add(mietLabel);
			for (int b = 0; b < mietButtonList.size(); b++)
				this.toolPanel.add((JButton) mietButtonList.get(b));
			
			//	add separator
			this.toolPanel.add(new JLabel(" "));
		}
		
		//	add selection based edit tools and a label (with explanatory tooltip)
		if (dietButtonList.size() != 0) {
			JLabel dietLabel = new JLabel("Selection Edit Tools");
			dietLabel.setToolTipText("Below tools process a mouse-selected part of the page image and/or words (click to select).");
			this.toolPanel.add(dietLabel);
			for (int b = 0; b < dietButtonList.size(); b++)
				this.toolPanel.add((JButton) dietButtonList.get(b));
			
			//	add separator
			this.toolPanel.add(new JLabel(" "));
		}
		
		this.toolPanel.add(this.undoButton);
		
		JScrollPane imagePanelBox = new JScrollPane(this.imagePanel);
		
		JPanel toolPanelTray = new JPanel(new BorderLayout(), true);
		toolPanelTray.add(this.toolPanel, BorderLayout.NORTH);
		JScrollPane toolPanelBox = new JScrollPane(toolPanelTray);
		
		this.add(toolPanelBox, BorderLayout.WEST);
		this.add(imagePanelBox, BorderLayout.CENTER);
	}
	
	Color getWordBoxColor() {
		return this.wordBoxColor;
	}
	
	private BufferedImage getWordImage() {
		if (this.wordImage == null) {
			this.wordImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_4BYTE_ABGR);
			Graphics2D graphics = this.wordImage.createGraphics();
			graphics.setColor(this.wordBoxColor);
			for (int w = 0; w < pageWords.size(); w++) {
				ImWord imw = ((ImWord) pageWords.get(w));
				graphics.drawRect(imw.bounds.left, imw.bounds.top, (imw.bounds.right - imw.bounds.left), (imw.bounds.bottom - imw.bounds.top));
			}
		}
		return this.wordImage;
	}
	private BufferedImage wordImage = null;
	
	private class MenuImageEditToolButton extends JButton implements ActionListener {
		MenuImageEditTool tool;
		MenuImageEditToolButton(MenuImageEditTool tool) {
			this.tool = tool;
			
			this.setText(this.tool.getLabel());
			this.setToolTipText(this.tool.getTooltip());
			if (this.tool.getIcon() != null)
				this.setIcon(new ImageIcon(this.tool.getIcon()));
			this.setBorder(BorderFactory.createRaisedBevelBorder());
			
			this.addActionListener(this);
		}
		public void actionPerformed(ActionEvent ae) {
			if (activeToolButton != null)
				activeToolButton.setBorder(BorderFactory.createRaisedBevelBorder());
			activeToolButton = null;
			activeTool = null;
			//	TODO prepare for UNDO
			//	TODO call tool
			//	TODO store UNDO data if any changes occurred
			imagePanel.setCursor(Cursor.getDefaultCursor());
		}
	}
	
	private class DirectImageEditToolButton extends JButton implements ActionListener {
		DirectImageEditTool tool;
		DirectImageEditToolButton(DirectImageEditTool tool) {
			this.tool = tool;
			
			this.setText(this.tool.getLabel());
			this.setToolTipText(this.tool.getTooltip());
			if (this.tool.getIcon() != null)
				this.setIcon(new ImageIcon(this.tool.getIcon()));
			this.setBorder(BorderFactory.createRaisedBevelBorder());
			
			this.addActionListener(this);
		}
		public void actionPerformed(ActionEvent ae) {
			if (activeToolButton != null)
				activeToolButton.setBorder(BorderFactory.createRaisedBevelBorder());
			activeToolButton = this;
			activeTool = this.tool;
			this.setBorder(BorderFactory.createLoweredBevelBorder());
			Cursor cursor = this.tool.getCursor();
			imagePanel.setCursor((cursor == null) ? Cursor.getDefaultCursor() : cursor);
		}
	}
	
	/**
	 * Open a properties editor for a given word, to modify its string and font
	 * properties. The edit dialog is positioned relative to the argument
	 * <code>JPanel</code>, which has to be the one the word is painted on for
	 * the method to behave in a meaningful way.
	 * @param word the word to edit
	 * @return true if the dialog was committed, false otherwise
	 */
	public boolean editWord(ImWord word) {
		Component comp = this.imagePanel;
		Window w = DialogFactory.getTopWindow();
		if (w == null)
			return false;
		int xOff = 0;
		int yOff = 0;
		while (comp != null) {
			Point loc = comp.getLocation();
//			System.out.println("Component is " + comp.getClass().getName() + " at " + loc);
			if (comp == w)
				break;
			xOff += loc.x;
			yOff += loc.y;
			comp = comp.getParent();
			if (comp instanceof Window)
				break;
		}
		EditWordDialog ewd;
		if (w instanceof Frame)
			ewd = new EditWordDialog(((Frame) w), word, wordBoxColor, true);
		else if (w instanceof Dialog)
			ewd = new EditWordDialog(((Dialog) w), word, wordBoxColor, true);
		else return false;
		ewd.setLocation((word.bounds.left + xOff + w.getLocation().x), (word.bounds.top + yOff + w.getLocation().y));
//		System.out.println("Showing word edit dialog at " + ewd.getLocation());
		ewd.setVisible(true);
		if (ewd.isCommitted()) {
			String str = ewd.getString();
			if (!str.equals(word.getString()))
				word.setString(str);
			if (ewd.isBold() != word.hasAttribute(ImWord.BOLD_ATTRIBUTE)) {
				if (ewd.isBold())
					word.setAttribute(ImWord.BOLD_ATTRIBUTE);
				else word.removeAttribute(ImWord.BOLD_ATTRIBUTE);
			}
			if (ewd.isItalics() != word.hasAttribute(ImWord.ITALICS_ATTRIBUTE)) {
				if (ewd.isItalics())
					word.setAttribute(ImWord.ITALICS_ATTRIBUTE);
				else word.removeAttribute(ImWord.ITALICS_ATTRIBUTE);
			}
			return true;
		}
		else return false;
	}
	
	/**
	 * Retrieve the page whose image is edited in the panel
	 * @return the page
	 */
	public ImPage getPage() {
		return this.page;
	}
	
	/**
	 * Test if the actual image has been modified.
	 * @return true if the image has been modified, false otherwise
	 */
	public boolean isPageImageDirty() {
		return (this.imageModCount > 0);
	}
	
	/**
	 * Retrieve the page image in its current editing state.
	 * @return the page image
	 */
	public PageImage getPageImage() {
		return new PageImage(this.image, this.pageImage.originalWidth, this.pageImage.originalHeight, this.pageImage.originalDpi, this.pageImage.currentDpi, this.pageImage.leftEdge, this.pageImage.rightEdge, this.pageImage.topEdge, this.pageImage.bottomEdge, this.pageImage.source);
	}
	
	/**
	 * Retrieve the image to work on. This method returns the actual image, not
	 * a copy. It is intended for tools to access the image data.
	 * @return the image to edit
	 */
	public BufferedImage getImage() {
		return this.image;
	}
	
	/**
	 * Retrieve the words in the page.
	 * @return an array holding the words
	 */
	public ImWord[] getWords() {
		return ((this.pageWords == null) ? new ImWord[0] : ((ImWord[]) this.pageWords.toArray(new ImWord[this.pageWords.size()])));
	}
	
	/**
	 * Add a word to the page.
	 * @param imw the word to add
	 */
	public void addWord(ImWord imw) {
		if (this.pageWordIDs.add(imw.getLocalID())) {
			this.pageWords.add(imw);
//			Collections.sort(this.pageWords, wordOrder);
			Collections.sort(this.pageWords, ImUtils.textStreamOrder);
			this.wordImage = null;
			if (this.activeTool != null)
				this.activeTool.wordAdded(imw);
		}
	}
	
	/**
	 * Remove a word from the page. If the word is not associated with the
	 * page, this method has no effect.
	 * @param imw the word to remove
	 */
	public void removeWord(ImWord imw) {
		if (this.pageWordIDs.remove(imw.getLocalID()) && this.pageWords.remove(imw)) {
			this.wordImage = null;
			if (this.activeTool != null)
				this.activeTool.wordRemoved(imw);
		}
	}
	
	/**
	 * A tool that modifies a page image or words.
	 * 
	 * @author sautter
	 */
	public static abstract class ImImageEditTool {
		protected String label;
		protected String tooltip;
		protected BufferedImage icon;
		
		/** Constructor
		 * @param label the tool label
		 * @param tooltip the tooltip explaining the tool
		 * @param icon the icon for the tool
		 */
		protected ImImageEditTool(String label, String tooltip, BufferedImage icon) {
			this.label = label;
			this.tooltip = tooltip;
			this.icon = icon;
		}
		
		/**
		 * Retrieve the label of the tool, for display purposes.
		 * @return the tool label
		 */
		public String getLabel() {
			return this.label;
		}
		
		/**
		 * Retrieve the tooltip of the tool, for display purposes. If the
		 * tooltip is null, this method returns the label as a fallback.
		 * @return the tooltip
		 */
		public String getTooltip() {
			return ((this.tooltip == null) ? this.label : this.tooltip);
		}
		
		/**
		 * Retrieve the icon image of the tool, for display purposes. If this
		 * method returns null, the label is used for UI integration.
		 * @return the icon
		 */
		public BufferedImage getIcon() {
			return this.icon;
		}
	}
	
	/**
	 * An image edit tool that modifies an a whole page in response to a single
	 * mouse click. All that instances of this class have to do is modify the
	 * image and the page words.
	 * 
	 * @author sautter
	 */
	public static abstract class MenuImageEditTool extends ImImageEditTool {
		
		/** Constructor
		 * @param label the tool label
		 * @param tooltip the tooltip explaining the tool
		 * @param icon the icon for the tool
		 */
		public MenuImageEditTool(String label, String tooltip, BufferedImage icon) {
			super(label, tooltip, icon);
		}
		
		//	TODO fill this class with functionality
		
		//	TODO force indication of changes to page image (flipping page, white balance, speckle cleanup, etc.)
		
		//	TODO force indication of changes to words (flipping page (left or right), re-running OCR)
		
		//	TODO store UNDO data based on these indications, not via notification to active tool
		//	TODO on word addition/removal notification, check if active tool is null
		
		/* TODO
	Create MenuImageEditTool subclass of new ImImageEditTool:
	- edits whole page at once
	- move to menu in "Edit Page Image & Words"

	Create some MenuImageEditTools:
	- for rotating or flipping page image
	- for OCRing entire page image
		 */
	}
	
	/**
	 * An image edit tool that modifies an image in response to a mouse click
	 * or mouse dragged box selections. There is always at most one tool
	 * selected in an image editor panel, and the panel takes care of routing
	 * mouse clicks to the active tool. All that instances of this class have
	 * to do is visualize what they are doing and modify the image and the page
	 * words.
	 * 
	 * @author sautter
	 */
	public static abstract class DirectImageEditTool extends ImImageEditTool {
		
		/** Constructor
		 * @param label the tool label
		 * @param tooltip the tooltip explaining the tool
		 * @param icon the icon for the tool
		 */
		public DirectImageEditTool(String label, String tooltip, BufferedImage icon) {
			super(label, tooltip, icon);
		}
		
		/**
		 * Retrieve the mouse cursor to display when this tool is the active
		 * one. This default implementation returns the cursor produced by the
		 * <code>createCursor()</code> method.
		 * @return the cursor to indicate the tool is active
		 */
		public Cursor getCursor() {
			if (this.cursor == null)
				this.cursor = this.createCursor();
			return this.cursor;
		}
		private Cursor cursor = null;
		
		/**
		 * Produce the mouse cursor to display when this tool is the active
		 * one. This default implementation returns a cursor equal to the icon
		 * image if the latter is not null. The hot spot of the cursor is set
		 * to (0, 0), i.e., the upper left corner of the icon. Sub classes can
		 * change this by overwriting this method.
		 * @return the cursor to indicate the tool is active
		 */
		protected Cursor createCursor() {
			return ((this.icon == null) ? null : Toolkit.getDefaultToolkit().createCustomCursor(this.icon, new Point(0, 0), this.label));
		}
		
		private int undoMinX = -1;
		private int undoMinY = -1;
		private int undoMaxX = -1;
		private int undoMaxY = -1;
		private BufferedImage undoBeforeImage = null;
		private LinkedList undoWordEdits = null;
		
		void mousePressed(ImImageEditorPanel iiep, int x, int y) {
			this.undoBeforeImage = new BufferedImage(iiep.image.getWidth(), iiep.image.getHeight(), iiep.image.getType());
			this.undoBeforeImage.getGraphics().drawImage(iiep.image, 0, 0, iiep.image.getWidth(), iiep.image.getHeight(), null);
			this.undoWordEdits = new LinkedList();
			this.addUndoPoint(x, y);
			
			this.doMousePressed(iiep, x, y);
		}
		
		void mouseDragged(ImImageEditorPanel iiep, int x, int y) {
			if (this.doMouseDragged(iiep, x, y))
				this.addUndoPoint(x, y);
		}
		
		ImImageEditUndoAction mouseReleased(ImImageEditorPanel iiep, int x, int y) {
			this.addUndoPoint(x, y);
			
			this.doMouseReleased(iiep, x, y);
			
			//	compute actual image changes
			int icMinX = this.undoMaxX;
			int icMaxX = this.undoMinX;
			int icMinY = this.undoMaxY;
			int icMaxY = this.undoMinY;
			for (int icx = Math.max((this.undoMinX - 16), 0); icx < Math.min((this.undoMaxX + 16), this.undoBeforeImage.getWidth()); icx++)
				for (int icy = Math.max((this.undoMinY - 16), 0); icy < Math.min((this.undoMaxY + 16), this.undoBeforeImage.getHeight()); icy++) {
					if (iiep.image.getRGB(icx, icy) == this.undoBeforeImage.getRGB(icx, icy))
						continue;
					icMinX = Math.min(icMinX, icx);
					icMaxX = Math.max(icMaxX, icx);
					icMinY = Math.min(icMinY, icy);
					icMaxY = Math.max(icMaxY, icy);
				}
			
			//	did the image change?
			int ux = -1;
			int uy = -1;
			BufferedImage ubi = null;
			if ((icMinX <= icMaxX) && (icMinY <= icMaxY)) {
//				System.out.println("Image modified in " + icMinX + "-" + icMaxX + " x " + icMinY + "-" + icMaxY);
				ux = icMinX;
				uy = icMinY;
				int uw = (icMaxX - icMinX + 1);
				int uh = (icMaxY - icMinY + 1);
				ubi = this.undoBeforeImage;
				if ((uw * uh * 2) < (this.undoBeforeImage.getWidth() * this.undoBeforeImage.getHeight())) {
					ubi = new BufferedImage(uw, uh, this.undoBeforeImage.getType());
					ubi.getGraphics().drawImage(this.undoBeforeImage.getSubimage(ux, uy, uw, uh), 0, 0, uw, uh, null);
				}
				iiep.imageModCount++;
			}
//			else System.out.println("Image unmodified");
			
//			/* Compute dimensions of undo image, figuring in 16 pixels in each
//			 * direction to account for cursor dimensions. */
//			int ux = Math.max((this.undoMinX - 16), 0);
//			int uy = Math.max((this.undoMinY - 16), 0);
//			int uw = Math.min((this.undoMaxX - this.undoMinX + 16 + 16), (this.undoBeforeImage.getWidth() - ux));
//			int uh = Math.min((this.undoMaxY - this.undoMinY + 16 + 16), (this.undoBeforeImage.getHeight() - uy));
//			
//			/* If it is small (less than half of the main image) we copy the
//			 * relevant sub image to allow for whole image to be garbage
//			 * collected. */
//			BufferedImage ubi = this.undoBeforeImage;
//			if ((uw * uh * 2) < (this.undoBeforeImage.getWidth() * this.undoBeforeImage.getHeight())) {
//				ubi = new BufferedImage(uw, uh, this.undoBeforeImage.getType());
//				ubi.getGraphics().drawImage(this.undoBeforeImage.getSubimage(ux, uy, uw, uh), 0, 0, uw, uh, null);
//			}
//			
			//	line up word edits
			ImWordEditUndoAtom[] uwe = ((ImWordEditUndoAtom[]) this.undoWordEdits.toArray(new ImWordEditUndoAtom[this.undoWordEdits.size()]));
			
			//	clear registers
			this.undoMinX = -1;
			this.undoMinY = -1;
			this.undoMaxX = -1;
			this.undoMaxY = -1;
			this.undoBeforeImage = null;
			this.undoWordEdits = null;
			
			//	finally ...
			return new ImImageEditUndoAction(ux, uy, ubi, uwe);
		}
		
		void wordAdded(ImWord imw) {
			if (this.undoWordEdits != null)
				this.undoWordEdits.addFirst(new ImWordEditUndoAtom(imw, false));
		}
		
		void wordRemoved(ImWord imw) {
			if (this.undoWordEdits != null)
				this.undoWordEdits.addFirst(new ImWordEditUndoAtom(imw, true));
		}
		
		/**
		 * Mark a point in the image as edited by the tool. This point will be
		 * included in the undo action produced when the current tool invocation
		 * is finished. Implementations that perform wide range edits can use
		 * this method to notify the undo management of the range of their
		 * modifications to the image. This method should only be called from
		 * the <code>doMousePressed()</code>, <code>doMouseDragged()</code>,
		 * and <code>doMouseRelesed()</code> methods, or from methods called
		 * from any of these three.
		 * @param x the x coordinate of the point
		 * @param y the y coordinate of the point
		 */
		protected void addUndoPoint(int x, int y) {
			this.undoMinX = Math.min(this.undoMinX, x);
			this.undoMinY = Math.min(this.undoMinY, y);
			this.undoMaxX = Math.max(this.undoMaxX, x);
			this.undoMaxY = Math.max(this.undoMaxY, y);
		}
		
		/**
		 * Notify the action that the mouse has been pressed.
		 * @param iiep the image editor panel whose content to edit
		 * @param x the x coordinate of the click
		 * @param y the y coordinate of the click
		 */
		protected abstract void doMousePressed(ImImageEditorPanel iiep, int x, int y);
		
		/**
		 * Notify the tool that the mouse has been dragged. If the tool makes
		 * any changes to the argument image, it has to indicate so by returning
		 * <code>true</code>; otherwise, it should return <code>false</code>.
		 * @param iiep the image editor panel whose content to edit
		 * @param x the x coordinate of the mouse
		 * @param y the y coordinate of the mouse
		 * @return true if the argument image was modified
		 */
		protected abstract boolean doMouseDragged(ImImageEditorPanel iiep, int x, int y);
		
		/**
		 * Notify the action that the mouse has been released.
		 * @param iiep the image editor panel whose content to edit
		 * @param x the x coordinate of the click
		 * @param y the y coordinate of the click
		 */
		protected abstract void doMouseReleased(ImImageEditorPanel iiep, int x, int y);
		
		/**
		 * Visualize the action. This method should only do something if the
		 * <code>startEdit()</code> method has been called, but the edit has
		 * not yet been finished via the <code>finishEdit()</code> method; this
		 * basically means that the left mouse button is being held down. This
		 * default implementation does not paint anything. Sub classes whose
		 * application involves dragging the mouse should overwrite it to show
		 * what they are doing. 
		 * @param gr the graphics object to use for painting
		 */
		public void paint(Graphics gr) {}
	}
	
	/* Undo-action of an image edit tool application. The undo action simply
	 * consists of a before image containing the pixels before editing and the
	 * coordinates of the top left corner of the rectangle encircling all of
	 * the edit. */
	private static class ImImageEditUndoAction {
		private int x;
		private int y;
		private BufferedImage beforeImage;
		private ImWordEditUndoAtom[] wordEdits;
		ImImageEditUndoAction(int x, int y, BufferedImage before, ImWordEditUndoAtom[] wordEdits) {
			this.x = x;
			this.y = y;
			this.beforeImage = before;
			this.wordEdits = wordEdits;
		}
		void doUndo(ImImageEditorPanel iiep) {
			if (this.beforeImage != null) {
				iiep.getImage().getGraphics().drawImage(this.beforeImage, this.x, this.y, this.beforeImage.getWidth(), this.beforeImage.getHeight(), null);
				iiep.imageModCount--;
			}
//			iiep.getImage().getGraphics().drawImage(this.beforeImage, this.x, this.y, this.beforeImage.getWidth(), this.beforeImage.getHeight(), null);
			for (int w = 0; w < this.wordEdits.length; w++) {
				if (this.wordEdits[w].removed)
					iiep.addWord(this.wordEdits[w].imw);
				else iiep.removeWord(this.wordEdits[w].imw);
			}
		}
	}
	private static class ImWordEditUndoAtom {
		final ImWord imw;
		final boolean removed;
		ImWordEditUndoAtom(ImWord imw, boolean removed) {
			this.imw = imw;
			this.removed = removed;
		}
	}
	
	/**
	 * Image edit tool that sets a pattern of pixels around the cursor to a
	 * specific color when the mouse is pressed or dragged (moved while a
	 * button is being held down).
	 * 
	 * @author sautter
	 */
	public static class PatternOverpaintImageEditTool extends DirectImageEditTool {
		private boolean[][] pattern;
		private int colorRgb;
		
		/** Constructor
		 * @param label the label for the tool
		 * @param tooltip the tooltip text for the tool
		 * @param pattern the pattern to use for painting (at most 32 x 32)
		 * @param color the color to use for painting
		 */
		public PatternOverpaintImageEditTool(String label, String tooltip, String pattern, Color color) {
			this(label, tooltip, parsePattern(pattern), color);
		}
		
		/** Constructor
		 * @param label the label for the tool
		 * @param tooltip the tooltip text for the tool
		 * @param icon the icon for the tool
		 * @param pattern the pattern to use for painting (at most 32 x 32)
		 * @param color the color to use for painting
		 */
		public PatternOverpaintImageEditTool(String label, String tooltip, BufferedImage icon, String pattern, Color color) {
			this(label, tooltip, icon, parsePattern(pattern), color);
		}
		
		/** Constructor
		 * @param label the label for the tool
		 * @param tooltip the tooltip text for the tool
		 * @param icon the icon for the tool
		 * @param pattern the pattern to use for painting (at most 32 x 32)
		 * @param color the color to use for painting
		 */
		public PatternOverpaintImageEditTool(String label, String tooltip, boolean[][] pattern, Color color) {
			this(label, tooltip, createIcon(pattern, color), pattern, color);
		}
		
		/** Constructor
		 * @param label the label for the tool
		 * @param tooltip the tooltip text for the tool
		 * @param icon the icon for the tool
		 * @param pattern the pattern to use for painting (at most 32 x 32)
		 * @param color the color to use for painting
		 */
		public PatternOverpaintImageEditTool(String label, String tooltip, BufferedImage icon, boolean[][] pattern, Color color) {
			super(label, tooltip, icon);
			this.pattern = pattern;
			this.colorRgb = color.getRGB();
		}
		
		private static boolean[][] parsePattern(String patternStr) {
			
			//	get pattern rows
			String[] patternRows = patternStr.trim().split("\\s+");
			
			//	find maximum number of columns
			int patternCols = 0;
			for (int r = 0; r < patternRows.length; r++)
				patternCols = Math.max(patternCols, patternRows[r].length());
			
			//	create and fill pattern array
			boolean[][] pattern = new boolean[patternCols][patternRows.length];
			for (int c = 0; c < pattern.length; c++) {
				for (int r = 0; r < pattern[c].length; r++)
					pattern[c][r] = ((c < patternRows[r].length()) ? (patternRows[r].charAt(c) != '0') : false);
			}
			
			//	finally ...
			return pattern;
		}
		
		private static BufferedImage createIcon(boolean[][] pattern, Color color) {
			BufferedImage icon = new BufferedImage(pattern.length, pattern[0].length, BufferedImage.TYPE_INT_ARGB);
			int colorRgb = color.getRGB();
			int edgeRgb = -1;
			if (0.5 < Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null)[2])
				edgeRgb = iconEdgeAccent;
			for (int c = 0; c < icon.getWidth(); c++)
				for (int r = 0; r < icon.getHeight(); r++) {
					boolean accent;
					if (edgeRgb == -1)
						accent = false;
					else if (!pattern[c][r])
						accent = false;
					else if ((c == 0) || !pattern[c-1][r])
						accent = true;
					else if ((r == 0) || !pattern[c][r-1])
						accent = true;
					else if (((c+1) == pattern.length) || !pattern[c+1][r])
						accent = true;
					else if (((r+1) == pattern[c].length) || !pattern[c][r+1])
						accent = true;
					else accent = false;
					if (accent)
						icon.setRGB(c, r, edgeRgb);
					else if (pattern[c][r])
						icon.setRGB(c, r, colorRgb);
					else icon.setRGB(c, r, transparentWhite);
				}
			return icon;
		}
		private static final int transparentWhite = (new Color(255, 255, 255, 0)).getRGB();
		private static final int iconEdgeAccent = Color.BLACK.getRGB();
		
		private void doPatternOverpaint(BufferedImage bi, int x, int y) {
			for (int c = 0; c < this.pattern.length; c++) {
				int ix = (x - (this.pattern.length / 2) + c);
				if ((ix < 0) || (bi.getWidth() <= ix))
					continue;
				for (int r = 0; r < this.pattern[c].length; r++) {
					int iy = (y - (this.pattern[c].length / 2) + r);
					if ((iy < 0) || (bi.getHeight() <= iy))
						continue;
					if (this.pattern[c][r])
						bi.setRGB(ix, iy, this.colorRgb);
				}
			}
		}
		
		/** This implementation creates a cursor centered at the center of the
		 * pattern, and in the overpaint color.
		 * @see de.uka.ipd.idaho.im.util.ImImageEditorPanel.ImImageEditTool#createCursor()
		 */
		protected Cursor createCursor() {
			BufferedImage bi = this.getIcon();
			if (bi == null)
				return super.createCursor();
			if ((bi.getWidth() < 32) || (bi.getHeight() < 32)) {
				BufferedImage ci = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
				for (int c = 0; c < ci.getWidth(); c++) {
					for (int r = 0; r < ci.getHeight(); r++)
						ci.setRGB(c, r, transparentWhite);
				}
				for (int c = 0; c < bi.getWidth(); c++) {
					for (int r = 0; r < bi.getHeight(); r++)
						ci.setRGB((c + ((ci.getWidth() - bi.getWidth()) / 2)), (r + ((ci.getHeight() - bi.getHeight()) / 2)), bi.getRGB(c, r));
				}
				bi = ci;
			}
			return Toolkit.getDefaultToolkit().createCustomCursor(bi, new Point((bi.getWidth() / 2), (bi.getHeight() / 2)), this.getLabel());
		}
		
		/** This implementation draws the pattern around the mouse position.
		 * @see de.uka.ipd.idaho.im.util.ImImageEditorPanel.ImImageEditTool#mousePressed(java.awt.image.BufferedImage, int, int)
		 */
		protected void doMousePressed(ImImageEditorPanel iiep, int x, int y) {
			this.doPatternOverpaint(iiep.getImage(), x, y);
		}
		
		/** This implementation draws the pattern around the mouse position.
		 * @see de.uka.ipd.idaho.im.util.ImImageEditorPanel.ImImageEditTool#mouseDragged(java.awt.image.BufferedImage, int, int)
		 */
		protected boolean doMouseDragged(ImImageEditorPanel iiep, int x, int y) {
			this.doPatternOverpaint(iiep.getImage(), x, y);
			return true;
		}
		
		/** This implementation does nothing, as drawing happens in the
		 * <code>mousePressed()</code> and <code>mouseDragged()</code> methods.
		 * @see de.uka.ipd.idaho.im.util.ImImageEditorPanel.ImImageEditTool#mouseReleased(java.awt.image.BufferedImage, int, int)
		 */
		protected void doMouseReleased(ImImageEditorPanel iiep, int x, int y) {}
	}
	
	/**
	 * Image edit tool that lets users select two points by dragging from one
	 * to the other and modifies the image only when the mouse is released.
	 * 
	 * @author sautter
	 */
	public static abstract class SelectionImageEditTool extends DirectImageEditTool {
		boolean isBoxSelection;

		/** Constructor
		 * @param label the label for the tool
		 * @param tooltip the tooltip text for the tool
		 * @param icon the icon for the tool
		 * @param isBoxSelection use a box to visualize the selection?
		 */
		protected SelectionImageEditTool(String label, String tooltip, BufferedImage icon, boolean isBoxSelection) {
			super(label, tooltip, icon);
			this.isBoxSelection = isBoxSelection;
		}
		
		/** This implementation returns a system default crosshair cursor.
		 * @see de.uka.ipd.idaho.im.util.ImImageEditorPanel.ImImageEditTool#createCursor()
		 */
		protected Cursor createCursor() {
			return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
		}
		
		private int sx = -1;
		private int sy = -1;
		private int ex = -1;
		private int ey = -1;
		
		/** This implementation remembers the argument point as the staring
		 * point of a tool invocation. 
		 * @see de.uka.ipd.idaho.im.util.ImImageEditorPanel.ImImageEditTool#doMousePressed(java.awt.image.BufferedImage, int, int)
		 */
		protected void doMousePressed(ImImageEditorPanel iiep, int x, int y) {
			this.sx = x;
			this.sy = y;
		}
		
		/** This implementation remembers the argument point as the current
		 * ending point of the tool invocation, used for visualization in the
		 * <code>paint()</code> method. As it does not modify the argument
		 * image, this implementation returns <code>false</code>.
		 * @see de.uka.ipd.idaho.im.util.ImImageEditorPanel.ImImageEditTool#doMouseDragged(java.awt.image.BufferedImage, int, int)
		 */
		protected boolean doMouseDragged(ImImageEditorPanel iiep, int x, int y) {
			this.ex = x;
			this.ey = y;
			return false;
		}
		
		/** This implementation triggers the actual edit action, ranging from
		 * the point where the mouse button was first pressed to the point it
		 * was just released.
		 * @see de.uka.ipd.idaho.im.util.ImImageEditorPanel.ImImageEditTool#doMouseReleased(java.awt.image.BufferedImage, int, int)
		 */
		protected void doMouseReleased(ImImageEditorPanel iiep, int x, int y) {
			if ((this.sx != -1) && (this.sy != -1) && (this.ex != -1) && (this.ey != -1))
				this.doEdit(iiep, Math.max(0, Math.min(this.sx, iiep.image.getWidth())), Math.max(0, Math.min(this.sy, iiep.image.getHeight())), Math.max(0, Math.min(this.ex, iiep.image.getWidth())), Math.max(0, Math.min(this.ey, iiep.image.getHeight())));
			this.sx = -1;
			this.sy = -1;
			this.ex = -1;
			this.ey = -1;
		}
		
		/**
		 * Perform the actual editing on an image.
		 * @param iiep the image editor panel whose content to edit
		 * @param sx the x coordinate the selection started at
		 * @param sy the y coordinate the selection started at
		 * @param ex the x coordinate the selection ended at
		 * @param ey the y coordinate the selection ended at
		 */
		protected abstract void doEdit(ImImageEditorPanel iiep, int sx, int sy, int ex, int ey);
		
		/** This implementation draws a box or line between the point where the
		 * mouse button was pressed and the current location of the cursor.
		 * @see de.uka.ipd.idaho.im.util.ImImageEditorPanel.ImImageEditTool#paint(java.awt.Graphics)
		 */
		public void paint(Graphics gr) {
			if ((this.sx == -1) || (this.sy == -1) || (this.ex == -1) || (this.ey == -1))
				return;
			Color preBoxColor = gr.getColor();
			if (this.isBoxSelection) {
				gr.setColor(halfTransparentWhite);
				gr.fillRect(Math.min(this.sx, this.ex), Math.min(this.sy, this.ey), Math.abs(this.ex - this.sx), Math.abs(this.ey - this.sy));
				gr.setColor(Color.RED);
				gr.drawRect(Math.min(this.sx, this.ex), Math.min(this.sy, this.ey), Math.abs(this.ex - this.sx), Math.abs(this.ey - this.sy));
			}
			else {
				gr.setColor(Color.RED);
				gr.drawLine(this.sx, this.sy, this.ex, this.ey);
			}
			gr.setColor(preBoxColor);
		}
		private static Color halfTransparentWhite = new Color(Color.white.getRed(), Color.white.getGreen(), Color.white.getBlue(), 128);
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e) {}
		
		//	create a few tools
		ImImageEditTool[] tools = {
			new PatternOverpaintImageEditTool("Eraser SQ 5", null, "11111\n11111\n11111\n11111\n11111", Color.WHITE),
			new PatternOverpaintImageEditTool("Eraser C 5", null, "01110\n11111\n11111\n11111\n01110", Color.WHITE),
			new PatternOverpaintImageEditTool("Eraser RH 5", null, "00100\n01110\n11111\n01110\n00100", Color.WHITE),
			new PatternOverpaintImageEditTool("Eraser B 9/3", null, "111111111\n111111111\n111111111", Color.WHITE),
			new PatternOverpaintImageEditTool("Eraser B 3/9", null, "111\n111\n111\n111\n111\n111\n111\n111\n111", Color.WHITE),
			new PatternOverpaintImageEditTool("Eraser SQ 9", null, "111111111\n111111111\n111111111\n111111111\n111111111\n111111111\n111111111\n111111111\n111111111", Color.WHITE),
			new PatternOverpaintImageEditTool("Eraser C 9", null, "000111000\n011111110\n011111110\n111111111\n111111111\n111111111\n011111110\n011111110\n000111000", Color.WHITE),
			new PatternOverpaintImageEditTool("Eraser RH 9", null, "000010000\n000111000\n001111100\n011111110\n111111111\n011111110\n001111100\n000111000\n000010000", Color.WHITE),
			new PatternOverpaintImageEditTool("Eraser B 15/5", null, "111111111111111\n111111111111111\n111111111111111\n111111111111111\n111111111111111", Color.WHITE),
			new PatternOverpaintImageEditTool("Eraser B 5/15", null, "11111\n11111\n11111\n11111\n11111\n11111\n11111\n11111\n11111\n11111\n11111\n11111\n11111\n11111\n11111", Color.WHITE),
			new SelectionImageEditTool("Eraser Flex", null, null, true) {
				protected void doEdit(ImImageEditorPanel iiep, int sx, int sy, int ex, int ey) {
					Graphics gr = iiep.getImage().getGraphics();
					Color bc = gr.getColor();
					gr.setColor(Color.WHITE);
					gr.fillRect(Math.min(sx, ex), Math.min(sy, ey), Math.abs(ex - sx), Math.abs(ey - sy));
					gr.setColor(bc);
				}
			},
			new PatternOverpaintImageEditTool("Pen 1", null, "1", Color.BLACK),
			new PatternOverpaintImageEditTool("Pen 3", null, "111\n111\n111", Color.BLACK),
			new PatternOverpaintImageEditTool("Pen 5", null, "01110\n11111\n11111\n11111\n01110", Color.BLACK),
			new SelectionImageEditTool("Line", null, null, false) {
				protected void doEdit(ImImageEditorPanel iiep, int sx, int sy, int ex, int ey) {
					Graphics gr = iiep.getImage().getGraphics();
					Color bc = gr.getColor();
					gr.setColor(Color.ORANGE);
					gr.drawLine(sx, sy, ex, ey);
					gr.setColor(bc);
				}
			},
			new SelectionImageEditTool("Box", null, null, true) {
				protected void doEdit(ImImageEditorPanel iiep, int sx, int sy, int ex, int ey) {
					Graphics gr = iiep.getImage().getGraphics();
					Color bc = gr.getColor();
					gr.setColor(Color.ORANGE);
					gr.drawRect(Math.min(sx, ex), Math.min(sy, ey), Math.abs(ex - sx), Math.abs(ey - sy));
					gr.setColor(bc);
				}
			},
			new SelectionImageEditTool("Word Box Test", null, null, true) {
				protected void doEdit(ImImageEditorPanel iiep, int sx, int sy, int ex, int ey) {
					
					//	normalize coordinates
					int sMinX = Math.min(sx, ex);
					int sMinY = Math.min(sy, ey);
					int sMaxX = Math.max(sx, ex);
					int sMaxY = Math.max(sy, ey);
					
					//	start with original selection
					int eMinX = sMinX;
					int eMinY = sMinY;
					int eMaxX = sMaxX;
					int eMaxY = sMaxY;
					
					//	get region coloring of selected rectangle
					boolean changed;
					boolean[] originalSelectionRegionInCol;
					boolean[] originalSelectionRegionInRow;
					do {
						
						//	get region coloring for current rectangle
						int[][] regionColors = Imaging.getRegionColoring(Imaging.wrapImage(iiep.getImage().getSubimage(eMinX, eMinY, (eMaxX - eMinX), (eMaxY - eMinY)), null), ((byte) 120), true);
						
						//	check which region colors occur in original selection
						boolean[] regionColorInOriginalSelection = new boolean[this.getMaxRegionColor(regionColors)];
						Arrays.fill(regionColorInOriginalSelection, false);
						for (int c = Math.max((sMinX - eMinX), 0); c < Math.min((sMaxX - eMinX), regionColors.length); c++)
							for (int r = Math.max((sMinY - eMinY), 0); r < Math.min((sMaxY - eMinY), regionColors[c].length); r++) {
								if (regionColors[c][r] != 0)
									regionColorInOriginalSelection[regionColors[c][r]-1] = true;
							}
						
						//	assess which columns and rows contain regions that overlap original selection
						originalSelectionRegionInCol = new boolean[regionColors.length];
						Arrays.fill(originalSelectionRegionInCol, false);
						originalSelectionRegionInRow = new boolean[regionColors[0].length];
						Arrays.fill(originalSelectionRegionInRow, false);
						for (int c = 0; c < regionColors.length; c++) {
							for (int r = 0; r < regionColors[c].length; r++)
								if (regionColors[c][r] != 0) {
									originalSelectionRegionInCol[c] = (originalSelectionRegionInCol[c] || regionColorInOriginalSelection[regionColors[c][r]-1]);
									originalSelectionRegionInRow[r] = (originalSelectionRegionInRow[r] || regionColorInOriginalSelection[regionColors[c][r]-1]);
								}
						}
						
						//	adjust boundaries
						changed = false;
						if (originalSelectionRegionInCol[0] && (eMinX != 0)) {
							eMinX--;
							changed = true;
						}
						else for (int c = 0; (c+1) < originalSelectionRegionInCol.length; c++) {
							if (originalSelectionRegionInCol[c+1])
								break;
							else {
								eMinX++;
								changed = true;
							}
						}
						if (originalSelectionRegionInCol[originalSelectionRegionInCol.length-1] && (eMaxX != (iiep.getImage().getWidth()-1))) {
							eMaxX++;
							changed = true;
						}
						else for (int c = (originalSelectionRegionInCol.length-1); c != 0; c--) {
							if (originalSelectionRegionInCol[c-1])
								break;
							else {
								eMaxX--;
								changed = true;
							}
						}
						if (originalSelectionRegionInRow[0] && (eMinY != 0)) {
							eMinY--;
							changed = true;
						}
						else for (int r = 0; (r+1) < originalSelectionRegionInRow.length; r++) {
							if (originalSelectionRegionInRow[r+1])
								break;
							else {
								eMinY++;
								changed = true;
							}
						}
						if (originalSelectionRegionInRow[originalSelectionRegionInRow.length-1] && (eMaxY != (iiep.getImage().getHeight()-1))) {
							eMaxY++;
							changed = true;
						}
						else for (int r = (originalSelectionRegionInRow.length-1); r != 0; r--) {
							if (originalSelectionRegionInRow[r-1])
								break;
							else {
								eMaxY--;
								changed = true;
							}
						}
						
						//	check if we still have something to work with
						if ((eMaxX <= eMinX) || (eMaxY <= eMinY))
							return;
					}
					
					//	keep going while there is adjustments
					while (changed);
					
					//	cut white edge
					if (!originalSelectionRegionInCol[0])
						eMinX++;
					if (!originalSelectionRegionInCol[originalSelectionRegionInCol.length-1])
						eMaxX--;
					if (!originalSelectionRegionInRow[0])
						eMinY++;
					if (!originalSelectionRegionInRow[originalSelectionRegionInRow.length-1])
						eMaxY--;
					
					//	make undo management know eventual selection
					this.addUndoPoint(eMinX, eMinY);
					this.addUndoPoint(eMaxX, eMaxY);
					
					//	draw resulting box
					Graphics gr = iiep.getImage().getGraphics();
					Color bc = gr.getColor();
					gr.setColor(Color.ORANGE);
					gr.drawRect(eMinX, eMinY, (eMaxX - eMinX), (eMaxY - eMinY));
					gr.setColor(bc);
				}
				private int getMaxRegionColor(int[][] regionColors) {
					int maxRegionColor = 0;
					for (int c = 0; c < regionColors.length; c++) {
						for (int r = 0; r < regionColors[c].length; r++)
							maxRegionColor = Math.max(maxRegionColor, regionColors[c][r]);
					}
					return maxRegionColor;
				}
			},
		};
		
		//	load some page image
		PageImage pi = new PageImage(new PageImageInputStream(new FileInputStream(new File("E:/Testdaten/PdfExtract/3868.pdf.0000.png")), null));
		
		//	create editor panel
		ImImageEditorPanel iiep = new ImImageEditorPanel(pi, tools, null, Color.ORANGE);
		
		//	put editor panel in JFrame and open it
		JFrame f = new JFrame();
		f.setSize(600, 600);
		f.getContentPane().setLayout(new BorderLayout());
		f.getContentPane().add(iiep, BorderLayout.CENTER);
		f.setLocationRelativeTo(null);
		f.setVisible(true);
	}
}