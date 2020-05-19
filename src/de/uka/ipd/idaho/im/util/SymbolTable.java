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
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Iterator;
import java.util.LinkedList;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import de.uka.ipd.idaho.gamta.util.swing.DialogFactory;
import de.uka.ipd.idaho.stringUtils.StringUtils;

/**
 * A singleton shared dialog for selecting printable symbols from the entire
 * Unicode range. Client code interacts with the shared instance via the owner
 * interface, which controls positioning, and also receives notification of
 * symbols clicked for use and closing of the dialog. The most recently used
 * symbols are shown in a favorite list to save scrolling and searching symbols
 * that are frequently used.
 * 
 * @author sautter
 */
public class SymbolTable extends JPanel {
	
	/**
	 * Interface to implement by client code using the shared symbol table
	 * instance.
	 * 
	 * @author sautter
	 */
	public static interface Owner {
		
		/**
		 * Use a symbol in client code that was clicked in the symbol table.
		 * @param symbol the symbol to use
		 */
		public abstract void useSymbol(char symbol);
		
		/**
		 * Retrieve the color to use for borders.
		 * @return the color for borders
		 */
		public abstract Color getColor();
		
		/**
		 * Retrieve the location of the owner on the screen. This method,
		 * together with <code>getSize()</code>, determines where the symbol
		 * table appears relative to its owner.
		 * @return the on-screen location of the owner
		 */
		public abstract Point getLocation();
		
		
		/**
		 * Retrieve the size of the owner on the screen. This method, together
		 * with <code>getLocation()</code>, determines where the symbol table
		 * appears relative to its owner.
		 * @return the size of the owner
		 */
		public abstract Dimension getSize();
		
		/**
		 * Receive notification that the symbol table was closed using its own
		 * 'Close' button.
		 */
		public abstract void symbolTableClosed();
	}
	
	private static final int mostRecentlyUsedSymbolCount = 12;
	private static final int symbolTableColumnCount = 16;
	private static final int symbolLabelWidth = 20;
	private static final Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
	
	private LinkedList mruChars = new LinkedList();
	private SymbolLabel[] mruLabels = new SymbolLabel[mostRecentlyUsedSymbolCount];
	
	private Font symbolLabelFont = null;
	
	private Color defaultWordColor = Color.MAGENTA;
	
	private Owner owner = null;
	private JDialog dialog = null;
	private JButton close;
	
	private SymbolTable() {
		super(new BorderLayout(), true);
		
		Font defaultLabelFont = (new JLabel("")).getFont();
		this.symbolLabelFont = new Font("Times New Roman", defaultLabelFont.getStyle(), defaultLabelFont.getSize());
		
		JPanel mruPanel = new JPanel(new GridLayout(0, 1), true);
		mruPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.WHITE, 5), BorderFactory.createLoweredBevelBorder()));
		for (int u = 0; u < this.mruLabels.length; u++) {
			this.mruLabels[u] = new SymbolLabel((char) 0);
			mruPanel.add(this.mruLabels[u]);
		}
		for (int c = 0; c < Math.min(mostRecentlyUsedSymbols.length(), mostRecentlyUsedSymbolCount); c++) {
			Character mruChar = new Character(mostRecentlyUsedSymbols.charAt(c));
			if (!this.mruChars.contains(mruChar))
				this.mruChars.addLast(mruChar);
		}
		this.updateMruLabels();
		
		JPanel sHeaderPanel = new JPanel(new BorderLayout(), true);
		JPanel sHeaderSpacer = new JPanel(true);
		JPanel sLinePanel = new JPanel(new GridLayout(1, 0), true);
		JLabel sHexLabel = new JLabel("HEX", JLabel.CENTER);
		sHexLabel.setToolTipText("First 3 hex digits of symbols at line start, last one at column top");
		sHexLabel.setBorder(BorderFactory.createMatteBorder(2, 0, 2, 0, sHexLabel.getBackground()));
		sLinePanel.add(sHexLabel);
		for (int s = 0; s < 16; s++) {
			JLabel sColLabel = new JLabel("_" + Integer.toString(s, 16).toUpperCase(), JLabel.CENTER);
			sColLabel.setBorder(BorderFactory.createMatteBorder(2, 0, 2, 0, sColLabel.getBackground()));
			sLinePanel.add(sColLabel);
		}
		sHeaderPanel.add(sLinePanel, BorderLayout.CENTER);
		sHeaderPanel.add(sHeaderSpacer, BorderLayout.EAST);
		sLinePanel = null;
		JLabel sLineLabel = null;
		JPanel sPanel = new JPanel(new GridLayout(0, 1), true);
		for (int b = 0; b < unicodeBlocks.length; b++) {
			String[] blockData = unicodeBlocks[b].split("\\;");
			int lowChar = Integer.parseInt(blockData[0], 16);
			while ((lowChar % 16) != 0)
				lowChar--;
			int highChar = Integer.parseInt(blockData[1], 16);
			while (((highChar+1) % 16) != 0)
				highChar++;
			if (blockData[2].equals("Han") || (blockData[2].indexOf("Han,") != -1))
				continue;
			int sLineCharCount = 0;
			for (int c = lowChar; c <= highChar; c++) {
				char ch = ((char) c);
				if (sLinePanel == null) {
					sLinePanel = new JPanel(new GridLayout(1, 0), true);
					sLineLabel = new JLabel(((ch < 4096) ? "0" : "") + ((ch < 256) ? "0" : "") + Integer.toString((c / 16), 16).toUpperCase() + "_");
					sLineLabel.setToolTipText(blockData[2]);
					sLinePanel.add(sLineLabel);
					sLineCharCount = 0;
				}
				sLinePanel.add(new SymbolLabel(ch));
				if ((' ' < ch) && this.symbolLabelFont.canDisplay(ch))
					sLineCharCount++;
				if (sLinePanel.getComponentCount() == (symbolTableColumnCount + 1)) {
					if (sLineCharCount != 0)
						sPanel.add(sLinePanel);
					sLinePanel = null;
				}
			}
		}
		sPanel.setBorder(BorderFactory.createMatteBorder(0, 2, 0, 2, sLineLabel.getBackground()));
		
		JScrollPane sPanelBox = new JScrollPane(sPanel);
		sPanelBox.getVerticalScrollBar().setUnitIncrement(symbolLabelWidth * 4);
		sPanelBox.getVerticalScrollBar().setBlockIncrement(symbolLabelWidth * 4);
		sPanelBox.setViewportBorder(BorderFactory.createEmptyBorder());
		
		this.close = new JButton("Close");
		this.close.setBackground(Color.WHITE);
		this.close.setBorder(BorderFactory.createLineBorder(defaultWordColor));
		this.close.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent me) {
				close();
			}
		});
		JPanel bPanel = new JPanel(new FlowLayout(FlowLayout.CENTER), true);
		bPanel.setBackground(Color.WHITE);
		bPanel.add(this.close);
		
		sPanel.getLayout().layoutContainer(sPanel);
		sHexLabel.setPreferredSize(sLineLabel.getPreferredSize());
		sHeaderSpacer.setPreferredSize(new Dimension(sPanelBox.getVerticalScrollBar().getPreferredSize().width, sHexLabel.getPreferredSize().height));
		
		JPanel sPanelTray = new JPanel(new BorderLayout(), true);
		sPanelTray.add(sHeaderPanel, BorderLayout.NORTH);
		sPanelTray.add(sPanelBox, BorderLayout.CENTER);
		sPanelTray.setBorder(BorderFactory.createLineBorder(Color.WHITE, 5));
		
		this.setBackground(Color.WHITE);
		this.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.WHITE, 1), BorderFactory.createLineBorder(defaultWordColor, 1)));
		this.add(mruPanel, BorderLayout.WEST);
		this.add(sPanelTray, BorderLayout.CENTER);
		this.add(bPanel, BorderLayout.SOUTH);
	}
	
	private void useSymbol(char symbol) {
		Character sCh = new Character(symbol);
		this.mruChars.remove(sCh);
		this.mruChars.addFirst(sCh);
		while (this.mruChars.size() > mostRecentlyUsedSymbolCount)
			this.mruChars.removeLast();
		this.updateMruLabels();
		if (this.owner != null)
			this.owner.useSymbol(symbol);
	}
	
	private void updateMruLabels() {
		Iterator mruChIt = this.mruChars.iterator();
		for (int u = 0; u < this.mruLabels.length; u++)
			this.mruLabels[u].setSymbol(mruChIt.hasNext() ? ((Character) mruChIt.next()).charValue() : ((char) 0));
	}
	
	/**
	 * Make the symbol table know its current owner to interact with. This
	 * method must be called with a non-null argument before the symbol table
	 * can be opened via the <code>open()</code> method. Namely, it initializes
	 * the dialog the symbol table appears in and positions this dialog in
	 * relation to the owner. Positioning prefers right of over below over
	 * left of over atop the owner. Decision is based on which position pushes
	 * the least of the symbol table off screen.
	 * @param owner the owner
	 */
	public void setOwner(Owner owner) {
		if (this.dialog != null)
			throw new IllegalStateException("Cannot set owner when displaying");
		
		//	link up to parent word dialog
		this.owner = owner;
		
		//	create dialog, non-modal, with word dialog as parent
		this.dialog = DialogFactory.produceDialog("SymbolTable", false);
		this.dialog.getContentPane().setLayout(new BorderLayout());
		this.dialog.getContentPane().add(this, BorderLayout.CENTER);
		this.dialog.setUndecorated(true);
		this.dialog.setSize(new Dimension(514, 286)); // TODO how did we get this ???
		
		//	compute position here
		this.updateOwner();
	}
	
	/**
	 * Update the position of the dialog the symbol table appears in relative
	 * to the owner, e.g. after the position of the latter has changed.
	 * Positioning prefers right of over below over left of over atop the
	 * owner. Decision is based on which position pushes the least of the
	 * symbol table off screen.
	 */
	public void updateOwner() {
		if (this.owner == null)
			throw new IllegalStateException("Cannot update position without owner");
		
		//	update colors
		this.close.setBorder(BorderFactory.createLineBorder(this.owner.getColor()));
		this.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.WHITE, 1), BorderFactory.createLineBorder(this.owner.getColor(), 1)));
		
		//	compute position here
		Point oPos = this.owner.getLocation();
		Dimension oSize = this.owner.getSize();
		Dimension dSize = this.dialog.getSize();
		int leftLoss = (dSize.width - oPos.x);
		int rightLoss = (oPos.x + oSize.width + dSize.width - screenSize.width);
		int topLoss = (dSize.height - oPos.y);
		int bottomLoss = (oPos.y + oSize.height + dSize.height - screenSize.height);
		int minLoss = Math.min(Math.min(leftLoss, rightLoss), Math.min(topLoss, bottomLoss));
		if (rightLoss < 0) {
			int upLoss = (dSize.height - (oPos.y + oSize.height));
			int downLoss = (oPos.y + dSize.height - screenSize.height);
			if (upLoss < 0)
				this.dialog.setLocation((oPos.x + oSize.width), (oPos.y + oSize.height - dSize.height));
			else if (downLoss < 0)
				this.dialog.setLocation((oPos.x + oSize.width), oPos.y);
			else this.dialog.setLocation((oPos.x + oSize.width), 0);
		}
		else if (bottomLoss < 0) {
			int lwLoss = (dSize.width - (oPos.x + oSize.width));
			int rwLoss = (oPos.x + dSize.width - screenSize.width);
			if (rwLoss < 0)
				this.dialog.setLocation(oPos.x, (oPos.y + oSize.height));
			else if (lwLoss < 0)
				this.dialog.setLocation((oPos.x + oSize.width - dSize.width), (oPos.y + oSize.height));
			else this.dialog.setLocation(0, (oPos.y + oSize.height));
		}
		else if (topLoss < 0) {
			int lwLoss = (dSize.width - (oPos.x + oSize.width));
			int rwLoss = (oPos.x + dSize.width - screenSize.width);
			if (rwLoss < 0)
				this.dialog.setLocation(oPos.x, (oPos.y - dSize.height));
			else if (lwLoss < 0)
				this.dialog.setLocation((oPos.x + oSize.width - dSize.width), (oPos.y - dSize.height));
			else this.dialog.setLocation(0, (oPos.y - dSize.height));
		}
		else if (leftLoss < 0) {
			int upLoss = (dSize.height - (oPos.y + oSize.height));
			int downLoss = (oPos.y + dSize.height - screenSize.height);
			if (upLoss < 0)
				this.dialog.setLocation((oPos.x - dSize.width), (oPos.y + oSize.height - dSize.height));
			else if (downLoss < 0)
				this.dialog.setLocation((oPos.x - dSize.width), oPos.y);
			else this.dialog.setLocation((oPos.x - dSize.width), 0);
		}
		else if (rightLoss == minLoss)
			this.dialog.setLocation((oPos.x + oSize.width), (oPos.y + oSize.height - dSize.height));
		else if (bottomLoss == minLoss)
			this.dialog.setLocation(oPos.x, (oPos.y + oSize.height));
		else if (topLoss == minLoss)
			this.dialog.setLocation(oPos.x, (oPos.y - dSize.height));
		else /* if (leftLoss == minLoss) */
			this.dialog.setLocation((oPos.x - dSize.width), (oPos.y + oSize.height - dSize.height));
	}
	
	/**
	 * Open the symbol table in a dialog. If the owner has not been set, this
	 * method does nothing.
	 */
	public void open() {
		if (this.dialog == null)
			return;
		if (this.dialog.isVisible())
			this.dialog.toFront();
		else this.dialog.setVisible(true);
	}
	
	/**
	 * Close the symbol table.
	 */
	public void close() {
		if (this.dialog == null)
			return;
		this.dialog.dispose();
		this.dialog = null;
		if (this.owner != null) {
			this.owner.symbolTableClosed();
			this.owner = null;
		}
		StringBuffer mruChars = new StringBuffer();
		for (Iterator mruChIt = this.mruChars.iterator(); mruChIt.hasNext();)
			mruChars.append(((Character) mruChIt.next()).charValue());
		mostRecentlyUsedSymbols = mruChars.toString();
	}
	
	private class SymbolLabel extends JLabel {
		char symbol;
		SymbolLabel(char symbol) {
			super("", JLabel.CENTER);
			this.setFont(symbolLabelFont);
			this.setSymbol(symbol);
			this.setBorder(BorderFactory.createLineBorder(this.getBackground()));
			this.setBackground(Color.WHITE);
			this.setOpaque(true);
			StringBuffer tooltip = new StringBuffer("Use char " + ((symbol < 4096) ? "0" : "") + ((symbol < 256) ? "0" : "") + ((symbol < 16) ? "0" : "") + Integer.toString(symbol, 16).toUpperCase());
			String symbolNames[] = StringUtils.getCharNames(symbol);
			if (symbolNames != null)
				for (int n = 0; n < symbolNames.length; n++) {
					tooltip.append(" / ");
					tooltip.append(symbolNames[n]);
				}
			this.setToolTipText(tooltip.toString());
			this.addMouseListener(new MouseAdapter() {
				public void mouseClicked(MouseEvent me) {
					if ((' ' < SymbolLabel.this.symbol) && symbolLabelFont.canDisplay(SymbolLabel.this.symbol))
						useSymbol(SymbolLabel.this.symbol);
				}
			});
			this.setPreferredSize(new Dimension(symbolLabelWidth, symbolLabelWidth));
		}
		void setSymbol(char symbol) {
			this.symbol = symbol;
			this.setText((symbolLabelFont.canDisplay(symbol) ? ("" + symbol) : ""));
		}
	}
	
	/**
	 * Retrieve the singleton shared instance of this class.
	 * @return the shared instance
	 */
	public static SymbolTable getSharedSymbolTable() {
		if (sharedSymbolTable == null)
			sharedSymbolTable = new SymbolTable();
		return sharedSymbolTable;
	}
	private static SymbolTable sharedSymbolTable = null;
	
	/**
	 * Retrieve a string consisting of the most recently used symbols.
	 * @return the most recently used symbols
	 */
	public static String getMostRecentlyUsedSymbols()  {
		return mostRecentlyUsedSymbols;
	}
	
	/**
	 * Initialize the most recently used symbols.
	 * @param mruSymbols the initial most recently used symbols
	 */
	public static void setMostRecentlyUsedSymbols(String mruSymbols)  {
		if (mruSymbols == null)
			return;
		mostRecentlyUsedSymbols = mruSymbols;
		if (sharedSymbolTable == null)
			return;
		sharedSymbolTable.mruChars.clear();
		for (int c = 0; c < Math.min(mostRecentlyUsedSymbols.length(), mostRecentlyUsedSymbolCount); c++) {
			Character mruChar = new Character(mostRecentlyUsedSymbols.charAt(c));
			if (!sharedSymbolTable.mruChars.contains(mruChar))
				sharedSymbolTable.mruChars.addLast(mruChar);
		}
		sharedSymbolTable.updateMruLabels();
	}
	private static String mostRecentlyUsedSymbols = "";
	
	private static final String[] unicodeBlocks = {
		"0000;007F;Latin, Common", // Basic Latin[g] 
		"0080;00FF;Latin, Common", // Latin-1 Supplement[h] 
		"0100;017F;Latin", // Latin Extended-A 
		"0180;024F;Latin", // Latin Extended-B 
		"0250;02AF;Latin", // IPA Extensions 
		"02B0;02FF;Latin, Common", // Spacing Modifier Letters 
		"0300;036F;Inherited", // Combining Diacritical Marks 
		"0370;03FF;Greek, Coptic, Common", // Greek and Coptic 
		"0400;04FF;Cyrillic, Inherited", // Cyrillic 
		"0500;052F;Cyrillic", // Cyrillic Supplement 
		"0530;058F;Armenian, Common", // Armenian 
		"0590;05FF;Hebrew", // Hebrew 
		"0600;06FF;Arabic, Common, Inherited", // Arabic 
		"0700;074F;Syriac", // Syriac 
		"0750;077F;Arabic", // Arabic Supplement 
		"0780;07BF;Thaana", // Thaana 
		"07C0;07FF;Nko", // NKo 
		"0800;083F;Samaritan", // Samaritan 
		"0840;085F;Mandaic", // Mandaic 
		"08A0;08FF;Arabic", // Arabic Extended-A 
		"0900;097F;Devanagari, Common, Inherited", // Devanagari 
		"0980;09FF;Bengali", // Bengali 
		"0A00;0A7F;Gurmukhi", // Gurmukhi 
		"0A80;0AFF;Gujarati", // Gujarati 
		"0B00;0B7F;Oriya", // Oriya 
		"0B80;0BFF;Tamil", // Tamil 
		"0C00;0C7F;Telugu", // Telugu 
		"0C80;0CFF;Kannada", // Kannada 
		"0D00;0D7F;Malayalam", // Malayalam 
		"0D80;0DFF;Sinhala", // Sinhala 
		"0E00;0E7F;Thai, Common", // Thai 
		"0E80;0EFF;Lao", // Lao 
		"0F00;0FFF;Tibetan, Common", // Tibetan 
		"1000;109F;Myanmar", // Myanmar 
		"10A0;10FF;Georgian, Common", // Georgian 
		"1100;11FF;Hangul", // Hangul Jamo 
		"1200;137F;Ethiopic", // Ethiopic 
		"1380;139F;Ethiopic", // Ethiopic Supplement 
		"13A0;13FF;Cherokee", // Cherokee 
		"1400;167F;Canadian Aboriginal", // Unified Canadian Aboriginal Syllabics 
		"1680;169F;Ogham", // Ogham 
		"16A0;16FF;Runic, Common", // Runic 
		"1700;171F;Tagalog", // Tagalog 
		"1720;173F;Hanunoo, Common", // Hanunoo 
		"1740;175F;Buhid", // Buhid 
		"1760;177F;Tagbanwa", // Tagbanwa 
		"1780;17FF;Khmer", // Khmer 
		"1800;18AF;Mongolian, Common", // Mongolian 
		"18B0;18FF;Canadian Aboriginal", // Unified Canadian Aboriginal Syllabics Extended 
		"1900;194F;Limbu", // Limbu 
		"1950;197F;Tai Le", // Tai Le 
		"1980;19DF;New Tai Lue", // New Tai Lue 
		"19E0;19FF;Khmer", // Khmer Symbols 
		"1A00;1A1F;Buginese", // Buginese 
		"1A20;1AAF;Tai Tham", // Tai Tham 
		"1B00;1B7F;Balinese", // Balinese 
		"1B80;1BBF;Sundanese", // Sundanese 
		"1BC0;1BFF;Batak", // Batak 
		"1C00;1C4F;Lepcha", // Lepcha 
		"1C50;1C7F;Ol Chiki", // Ol Chiki 
		"1CC0;1CCF;Sundanese", // Sundanese Supplement 
		"1CD0;1CFF;Common, Inherited", // Vedic Extensions 
		"1D00;1D7F;Cyrillic, Greek, Latin", // Phonetic Extensions 
		"1D80;1DBF;Latin, Greek", // Phonetic Extensions Supplement 
		"1DC0;1DFF;Inherited", // Combining Diacritical Marks Supplement 
		"1E00;1EFF;Latin", // Latin Extended Additional 
		"1F00;1FFF;Greek", // Greek Extended 
		"2000;206F;Common, Inherited", // General Punctuation 
		"2070;209F;Latin, Common", // Superscripts and Subscripts 
		"20A0;20CF;Common", // Currency Symbols 
		"20D0;20FF;Inherited", // Combining Diacritical Marks for Symbols 
		"2100;214F;Latin, Greek, Common", // Letterlike Symbols 
		"2150;218F;Latin, Common", // Number Forms 
		"2190;21FF;Common", // Arrows 
		"2200;22FF;Common", // Mathematical Operators 
		"2300;23FF;Common", // Miscellaneous Technical 
		"2400;243F;Common", // Control Pictures 
		"2440;245F;Common", // Optical Character Recognition 
		"2460;24FF;Common", // Enclosed Alphanumerics 
		"2500;257F;Common", // Box Drawing 
		"2580;259F;Common", // Block Elements 
		"25A0;25FF;Common", // Geometric Shapes 
		"2600;26FF;Common", // Miscellaneous Symbols 
		"2700;27BF;Common", // Dingbats 
		"27C0;27EF;Common", // Miscellaneous Mathematical Symbols-A 
		"27F0;27FF;Common", // Supplemental Arrows-A 
		"2800;28FF;Braille", // Braille Patterns 
		"2900;297F;Common", // Supplemental Arrows-B 
		"2980;29FF;Common", // Miscellaneous Mathematical Symbols-B 
		"2A00;2AFF;Common", // Supplemental Mathematical Operators 
		"2B00;2BFF;Common", // Miscellaneous Symbols and Arrows 
		"2C00;2C5F;Glagolitic", // Glagolitic 
		"2C60;2C7F;Latin", // Latin Extended-C 
		"2C80;2CFF;Coptic", // Coptic 
		"2D00;2D2F;Georgian", // Georgian Supplement 
		"2D30;2D7F;Tifinagh", // Tifinagh 
		"2D80;2DDF;Ethiopic", // Ethiopic Extended 
		"2DE0;2DFF;Cyrillic", // Cyrillic Extended-A 
		"2E00;2E7F;Common", // Supplemental Punctuation 
		"2E80;2EFF;Han", // CJK Radicals Supplement 
		"2F00;2FDF;Han", // Kangxi Radicals 
		"2FF0;2FFF;Common", // Ideographic Description Characters 
		"3000;303F;Han, Hangul, Common, Inherited", // CJK Symbols and Punctuation 
		"3040;309F;Hiragana, Common, Inherited", // Hiragana 
		"30A0;30FF;Katakana, Common", // Katakana 
		"3100;312F;Bopomofo", // Bopomofo 
		"3130;318F;Hangul", // Hangul Compatibility Jamo 
		"3190;319F;Common", // Kanbun 
		"31A0;31BF;Bopomofo", // Bopomofo Extended 
		"31C0;31EF;Common", // CJK Strokes 
		"31F0;31FF;Katakana", // Katakana Phonetic Extensions 
		"3200;32FF;Katakana, Hangul, Common", // Enclosed CJK Letters and Months 
		"3300;33FF;Katakana, Common", // CJK Compatibility 
		"3400;4DBF;Han", // CJK Unified Ideographs Extension A 
		"4DC0;4DFF;Common", // Yijing Hexagram Symbols 
		"4E00;9FFF;Han", // CJK Unified Ideographs 
		"A000;A48F;Yi", // Yi Syllables 
		"A490;A4CF;Yi", // Yi Radicals 
		"A4D0;A4FF;Lisu", // Lisu 
		"A500;A63F;Vai", // Vai 
		"A640;A69F;Cyrillic", // Cyrillic Extended-B 
		"A6A0;A6FF;Bamum", // Bamum 
		"A700;A71F;Common", // Modifier Tone Letters 
		"A720;A7FF;Latin, Common", // Latin Extended-D 
		"A800;A82F;Syloti Nagri", // Syloti Nagri 
		"A830;A83F;Common", // Common Indic Number Forms 
		"A840;A87F;Phags Pa", // Phags-pa 
		"A880;A8DF;Saurashtra", // Saurashtra 
		"A8E0;A8FF;Devanagari", // Devanagari Extended 
		"A900;A92F;Kayah Li", // Kayah Li 
		"A930;A95F;Rejang", // Rejang 
		"A960;A97F;Hangul", // Hangul Jamo Extended-A 
		"A980;A9DF;Javanese", // Javanese 
		"AA00;AA5F;Cham", // Cham 
		"AA60;AA7F;Myanmar", // Myanmar Extended-A 
		"AA80;AADF;Tai Viet", // Tai Viet 
		"AAE0;AAFF;Meetei Mayek", // Meetei Mayek Extensions 
		"AB00;AB2F;Ethiopic", // Ethiopic Extended-A 
		"ABC0;ABFF;Meetei Mayek", // Meetei Mayek 
		"AC00;D7AF;Hangul", // Hangul Syllables 
		"D7B0;D7FF;Hangul", // Hangul Jamo Extended-B 
//		"D800;DB7F;", // High Surrogates 
//		"DB80;DBFF;", // High Private Use Surrogates 
//		"DC00;DFFF;", // Low Surrogates 
//		"E000;F8FF;", // Private Use Area 
		"F900;FAFF;Han", // CJK Compatibility Ideographs 
		"FB00;FB4F;Latin, Hebrew, Armenian", // Alphabetic Presentation Forms 
		"FB50;FDFF;Arabic, Common", // Arabic Presentation Forms-A 
		"FE00;FE0F;Inherited", // Variation Selectors 
		"FE10;FE1F;Common", // Vertical Forms 
		"FE20;FE2F;Inherited", // Combining Half Marks 
		"FE30;FE4F;Common", // CJK Compatibility Forms 
		"FE50;FE6F;Common", // Small Form Variants 
		"FE70;FEFF;Arabic, Common", // Arabic Presentation Forms-B 
		"FF00;FFEF;Latin, Katakana, Hangul, Common", // Halfwidth and fullwidth forms 
		"FFF0;FFFF;Common", // Specials 
	};
}
