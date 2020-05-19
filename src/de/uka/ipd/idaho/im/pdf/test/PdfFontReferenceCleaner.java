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
package de.uka.ipd.idaho.im.pdf.test;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TreeMap;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import de.uka.ipd.idaho.gamta.util.CountingSet;
import de.uka.ipd.idaho.stringUtils.StringUtils;

/**
 * @author sautter
 */
public class PdfFontReferenceCleaner {
	public static void main(String[] args) throws Exception {
//		BufferedReader br = new BufferedReader(new InputStreamReader(new BufferedInputStream(new FileInputStream(new File("E:/Temp/glyphs.raw.txt"))), "UTF-8"));
		BufferedReader br = new BufferedReader(new InputStreamReader(new BufferedInputStream(new FileInputStream(new File("E:/Temp/glyphs.reconciled.txt"))), "UTF-8"));
		ArrayList glyphs = new ArrayList();
		for (String line; (line = br.readLine()) != null;) {
			String[] glyphData = line.split("\\t");
			if ((glyphData.length == 2) && "DISCARD".equals(glyphData[1]))
				continue;
			CharImage chi = new CharImage(glyphData[0]);
			for (int c = 1; c < glyphData.length; c++) {
				String[] strData = glyphData[c].split("\\s");
				chi.strs.add(strData[0], Integer.parseInt(strData[1]));
			}
			glyphs.add(chi);
		}
		br.close();
		
		ArrayList ambiguousGlyphs = new ArrayList();
		ArrayList unassignedGlyphs = new ArrayList();
		for (int g = 0; g < glyphs.size(); g++) {
			CharImage chi = ((CharImage) glyphs.get(g));
			if (chi.strs.elementCount() == 0) {
				unassignedGlyphs.add(chi);
				continue;
			}
			char min = ((String) chi.strs.first()).charAt(0);
			char max = ((String) chi.strs.last()).charAt(0);
			if ((min >= 0x3200) && (max < 0xA500)) {
				glyphs.remove(g--);
				continue; // ignore Chinese for now, cannot read it anyway
			}
			if (chi.strs.elementCount() > 1)
				ambiguousGlyphs.add(chi);
		}
		System.out.println(glyphs.size() + " glyphs remaining, " + ambiguousGlyphs.size() + " ambiguous ones, "+ unassignedGlyphs.size() + " unassigned ones");
		
//		CountingSet ambiguities = new CountingSet(new TreeMap());
//		for (int g = 0; g < ambiguousGlyphs.size(); g++) {
//			CharImage chi = ((CharImage) ambiguousGlyphs.get(g));
//			ambiguities.add(new Integer(chi.strs.elementCount()));
//		}
//		System.out.println("Ambiguity distribution:");
//		for (Iterator ait = ambiguities.iterator(); ait.hasNext();) {
//			Integer ambiguity = ((Integer) ait.next());
//			System.out.println(" - " + ambiguity.intValue() + " distinct strings: " + ambiguities.getCount(ambiguity));
//		}
//		
//		for (int g = 0; g < ambiguousGlyphs.size(); g++) {
//			CharImage chi = ((CharImage) ambiguousGlyphs.get(g));
//			if (chi.strs.elementCount() >= 10)
//				continue;
//			if (chi.strs.elementCount() < 5)
//				continue;
//			System.out.println("COLLISION ON IMAGE: " + chi.imageHex);
//			for (Iterator sit = chi.strs.iterator(); sit.hasNext();) {
//				String str = ((String) sit.next());
//				int strCount = chi.strs.getCount(str);
//				int strFract = ((strCount * 100) / chi.strs.size());
//				System.out.println("  " + str + ((str.length() == 1) ? (" (0x" + Integer.toString(str.charAt(0), 16) + ")") : "") + ": " + strCount + " of " + chi.strs.size() + ", " + strFract + "%");
//			}
//			JOptionPane.showMessageDialog(null, new JLabel(("IMAGE COLLISION " + chi.strs.elementCount()), new ImageIcon(decodeCharacterImage(chi.imageHex)), JLabel.LEFT));
//		}
		
		CharMap chars = new CharMap();
		for (int g = 0; g < glyphs.size(); g++) {
			CharImage chi = ((CharImage) glyphs.get(g));
			for (Iterator sit = chi.strs.iterator(); sit.hasNext();)
				chars.putChar(((String) sit.next()), chi);
		}
		System.out.println("Got " + chars.charCount() + " chars");
		
		ArrayList strings = chars.strings();
//		CountingSet charImageCounts = new CountingSet(new TreeMap());
//		for (int s = 0; s < strings.size(); s++) {
//			String str = ((String) strings.get(s));
//			Char ch = chars.getChar(str);
//			charImageCounts.add(new Integer(ch.images.size()));
//		}
//		System.out.println("Image count distribution:");
//		for (Iterator cicit = charImageCounts.iterator(); cicit.hasNext();) {
//			Integer cic = ((Integer) cicit.next());
//			System.out.println(" - " + charImageCounts.getCount(cic) + " chars with " + cic.intValue() + " images");
//		}
		
		//	reload data from previous runs
		HashMap stringImageRels = new HashMap();
		HashSet discardStrings = new HashSet();
		File stringImageRelsFile = new File("E:/Temp/glyphs.pruning.txt");
		if (stringImageRelsFile.exists()) try {
			HashMap imageRels = null;
			BufferedReader sirBr = new BufferedReader(new InputStreamReader(new BufferedInputStream(new FileInputStream(stringImageRelsFile)), "UTF-8"));
			for (String sir; (sir = sirBr.readLine()) != null;) {
				if (sir.startsWith("+ ")) {
					if (imageRels == null)
						continue;
					imageRels.put(sir.substring("+ ".length()), "+");
				}
				else if (sir.startsWith("- ")) {
					if (imageRels == null)
						continue;
					imageRels.put(sir.substring("- ".length()), "-");
				}
				else if (sir.startsWith("DISCARD ")) {
					discardStrings.add(sir.substring("DISCARD ".length()));
				}
				else {
					imageRels = new HashMap();
					stringImageRels.put(sir, imageRels);
				}
			}
			sirBr.close();
			stringImageRelsFile.renameTo(new File("E:/Temp/glyphs.pruning.old." + System.currentTimeMillis() + ".txt"));
			stringImageRelsFile = new File("E:/Temp/glyphs.pruning.txt");
//			stringImageRelsFile = new File("E:/Temp/glyphs.pruning.new.txt");
		}
		catch (IOException ioe) {
			ioe.printStackTrace(System.out);
		}
		
		BufferedWriter sirBw = new BufferedWriter(new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(stringImageRelsFile)), "UTF-8"));
		for (int s = 0; s < strings.size(); s++) {
			String str = ((String) strings.get(s));
//			if (str.charAt(0) <= 0x1000)
//				continue;
			Char ch = chars.getChar(str);
			
			if (discardStrings.contains(str)) {
				for (int i = 0; i < ch.images.size(); i++)
					((CharImage) ch.images.get(i)).strs.removeAll(ch.str);
				sirBw.write("DISCARD " + ch.str);
				sirBw.newLine();
				sirBw.flush();
				continue;
			}
			
			HashMap imageRels = ((HashMap) stringImageRels.get(ch.str));
			if (imageRels == null) {
				imageRels = new HashMap();
				stringImageRels.put(ch.str, imageRels);
			}
			
			int exImageRels = 0;
			ArrayList glyphTrays = new ArrayList();
			Collections.sort(ch.images, charImageMarginOrder);
			JPanel glyphPanel = new JPanel(new GridLayout(0, 4, 10, 3), true);
			glyphPanel.setBackground(Color.DARK_GRAY);
			glyphPanel.setBorder(BorderFactory.createMatteBorder(3, 7, 3, 7, Color.DARK_GRAY));
			for (int i = 0; i < ch.images.size(); i++) {
				CharImage chi = ((CharImage) ch.images.get(i));
				boolean defUseGlyph;
				if (imageRels.containsKey(chi.imageHex)) {
					defUseGlyph = "+".equals(imageRels.get(chi.imageHex));
					exImageRels++;
				}
				else if (chi.strs.elementCount() == 1)
					defUseGlyph = true;
				else {
					int chiAllStrCount = chi.strs.size();
					int chiStrCount = chi.strs.getCount(ch.str);
					if (chi.strs.elementCount() == 2)
						defUseGlyph = ((chiStrCount * 3) > (chiAllStrCount * 2));
					else {
						int chiAvgStrCount = (chiAllStrCount / chi.strs.elementCount());
						defUseGlyph = (chiStrCount > (chiAvgStrCount * 2));
					}
				}
				GlyphTray gt = new GlyphTray(ch.str, chi, defUseGlyph);
				glyphTrays.add(gt);
				glyphPanel.add(gt);
			}
			JCheckBox discardString = new JCheckBox("Discard string altogether?");
			
			if (exImageRels < ch.images.size()) {
				JPanel message = new JPanel(new BorderLayout(), true);
				if (ch.images.size() <= 16)
					message.add(glyphPanel, BorderLayout.CENTER);
				else {
					JScrollPane glyphBox = new JScrollPane(glyphPanel);
					glyphBox.getVerticalScrollBar().setUnitIncrement(50);
					glyphBox.getVerticalScrollBar().setBlockIncrement(50);
					glyphBox.setPreferredSize(new Dimension((glyphBox.getPreferredSize().width + glyphBox.getVerticalScrollBar().getPreferredSize().width), Math.min(glyphBox.getPreferredSize().height, 600)));
					message.add(glyphBox, BorderLayout.CENTER);
				}
				message.add(discardString, BorderLayout.SOUTH);
				JOptionPane.showMessageDialog(null, message, (str + ((ch.hex == null) ? "" : (" (" + ch.hex + ")")) + ((ch.name == null) ? "" : (" " + ch.name)) + " - " + ch.occurrences + " total occurrences with " + ch.images.size() + " distinct glyphs"), JOptionPane.PLAIN_MESSAGE);
				
				if (discardString.isSelected()) {
					for (int i = 0; i < ch.images.size(); i++)
						((CharImage) ch.images.get(i)).strs.removeAll(ch.str);
					stringImageRels.remove(ch.str);
					discardStrings.add(ch.str);
				}
				else for (int t = 0; t < glyphTrays.size(); t++) {
					GlyphTray gt = ((GlyphTray) glyphTrays.get(t));
					if (!gt.useGlyph.isSelected())
						gt.chi.strs.removeAll(ch.str);
				}
			}
			else for (int t = 0; t < glyphTrays.size(); t++) {
				GlyphTray gt = ((GlyphTray) glyphTrays.get(t));
				if (!gt.useGlyph.isSelected())
					gt.chi.strs.removeAll(ch.str);
			}
			
			if (discardStrings.contains(ch.str)) {
				sirBw.write("DISCARD " + ch.str);
				sirBw.newLine();
			}
			else {
				sirBw.write(ch.str);
				sirBw.newLine();
				for (int i = 0; i < ch.images.size(); i++) {
					CharImage chi = ((CharImage) ch.images.get(i));
					if (chi.strs.contains(ch.str))
						sirBw.write("+ " + chi.imageHex);
					else sirBw.write("- " + chi.imageHex);
					sirBw.newLine();
				}
			}
			sirBw.flush();
		}
		sirBw.close();
		
		//	TODOne store whole thing in "glyphs.pruned.txt"
		BufferedWriter gBw = new BufferedWriter(new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(new File("E:/Temp/glyphs.pruned.txt"))), "UTF-8"));
		for (int g = 0; g < glyphs.size(); g++) {
			CharImage chi = ((CharImage) glyphs.get(g));
			gBw.write(chi.imageHex);
			for (Iterator sit = chi.strs.iterator(); sit.hasNext();) {
				String str = ((String) sit.next());
				gBw.write('\t');
				gBw.write(str);
				gBw.write(' ');
				gBw.write("" + chi.strs.getCount(str));
			}
			gBw.newLine();
		}
		gBw.flush();
		gBw.close();
	}
	
	private static class GlyphTray extends JPanel {
		final CharImage chi;
		final JCheckBox useGlyph;
		GlyphTray(String str, CharImage chi, boolean defUseGlyph) {
			super(new BorderLayout(), true);
			this.chi = chi;
			this.useGlyph = new JCheckBox((chi.strs.getCount(str) + "/" + chi.strs.size() + " times"), defUseGlyph);
			this.useGlyph.setOpaque(true);
			this.useGlyph.setBackground(defUseGlyph ? Color.GREEN : Color.WHITE);
			this.useGlyph.addItemListener(new ItemListener() {
				public void itemStateChanged(ItemEvent ie) {
					useGlyph.setBackground(useGlyph.isSelected() ? Color.GREEN : Color.WHITE);
				}
			});
			this.setBorder(BorderFactory.createLineBorder(Color.WHITE, 2));
			this.add(new JLabel(new ImageIcon(decodeCharacterImage(chi.imageHex))), BorderLayout.WEST);
			this.add(this.useGlyph, BorderLayout.CENTER);
		}
	}
	
	private static class CharImage implements Comparable {
		final String imageHex;
		final int blankTop;
		final int blankLeft;
		final CountingSet strs = new CountingSet(new TreeMap());
		CharImage(String imageHex) {
			this.imageHex = imageHex;
			int leadHexZeros = 0;
			for (int c = 0; c < this.imageHex.length(); c++) {
				char hch = this.imageHex.charAt(c);
				if (hch == '0')
					leadHexZeros += 4;
				else {
					if (hch < '2')
						leadHexZeros += 3;
					else if (hch < '4')
						leadHexZeros += 2;
					else if (hch < '8')
						leadHexZeros += 1;
					break;
				}
			}
			int width = (this.imageHex.length() / 8);
			this.blankTop = (leadHexZeros / width);
			this.blankLeft = ((width <= 32) ? ((32 - width) / 2) : 0);
		}
		public int compareTo(Object obj) {
			return ((String) this.strs.first()).compareTo((String) ((CharImage) obj).strs.first());
		}
	}
	
	private static final Comparator charImageMarginOrder = new Comparator() {
		public int compare(Object o1, Object o2) {
			CharImage chi1 = ((CharImage) o1);
			CharImage chi2 = ((CharImage) o2);
			return ((chi1.blankTop == chi2.blankTop) ? (chi2.blankLeft - chi1.blankLeft) : (chi2.blankTop - chi1.blankTop));
		}
	};
	
	private static class Char {
		final String str;
		final String hex;
		final String name;
		final ArrayList images = new ArrayList();
		int occurrences = 0;
		Char(String str) {
			this.str = str;
			this.hex = ((this.str.length() == 1) ? ("0x" + Integer.toString(this.str.charAt(0), 16)) : null);
			this.name = ((this.str.length() == 1) ? StringUtils.getCharName(this.str.charAt(0)) : null);
		}
		void addImage(CharImage chi) {
			this.images.add(chi);
			this.occurrences += chi.strs.getCount(this.str);
		}
	}
	
	private static class CharMap {
		private HashMap chars = new HashMap();
		Char getChar(String str) {
			return ((Char) this.chars.get(str));
		}
		void putChar(String str, CharImage chi) {
			Char ch = ((Char) this.chars.get(str));
			if (ch == null) {
				ch = new Char(str);
				this.chars.put(str, ch);
			}
			ch.addImage(chi);
		}
		int charCount() {
			return this.chars.size();
		}
		ArrayList strings() {
			ArrayList strings = new ArrayList(this.chars.keySet());
			Collections.sort(strings);
			return strings;
		}
	}
	
	private static final int whiteRgb = Color.WHITE.getRGB();
	private static final int blackRgb = Color.BLACK.getRGB();
	private static BufferedImage decodeCharacterImage(String charImageHex) {
		if (charImageHex == null)
			return null;
		int charImageWidth = (charImageHex.length() / 8);
		if (charImageWidth == 0)
			return null;
		int charImagePadding = ((charImageWidth <= 32) ? ((32 - charImageWidth) / 2) : 0);
		
		BufferedImage charImage = new BufferedImage(Math.max(charImageWidth, 32), 32, BufferedImage.TYPE_BYTE_BINARY);
		int x = 0;
		int y = 0;
		int hex;
		int hexMask;
		for (int h = 0; h < charImageHex.length(); h++) {
			hex = Integer.parseInt(charImageHex.substring(h, (h+1)), 16);
			hexMask = 8;
			while (hexMask != 0) {
				charImage.setRGB((charImagePadding + x++), y, (((hex & hexMask) == 0) ? whiteRgb : blackRgb));
				hexMask >>= 1;
				if (x == charImageWidth) {
					x = 0;
					y++;
				}
			}
		}
		
		for (x = 0; x < charImage.getWidth(); x++) {
			if ((charImagePadding <= x) && (x < (charImagePadding + charImageWidth)))
				continue;
			for (y = 0; y < charImage.getHeight(); y++)
				charImage.setRGB(x, y, whiteRgb);
		}
		
		return charImage;
	}
}
