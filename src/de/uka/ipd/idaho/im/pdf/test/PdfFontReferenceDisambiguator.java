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
public class PdfFontReferenceDisambiguator {
	public static void main(String[] args) throws Exception {
		BufferedReader br = new BufferedReader(new InputStreamReader(new BufferedInputStream(new FileInputStream(new File("E:/Temp/glyphs.pruned.txt"))), "UTF-8"));
		ArrayList glyphs = new ArrayList();
		for (String line; (line = br.readLine()) != null;) {
			String[] glyphData = line.split("\\t");
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
			if (chi.strs.isEmpty())
				unassignedGlyphs.add(chi);
			else if (chi.strs.elementCount() > 1)
				ambiguousGlyphs.add(chi);
		}
		System.out.println("Got " + glyphs.size() + " glyphs, " + ambiguousGlyphs.size() + " ambiguous ones, " + unassignedGlyphs.size() + " unassigned ones");
		
		CountingSet ambiguities = new CountingSet(new TreeMap());
		for (int g = 0; g < ambiguousGlyphs.size(); g++) {
			CharImage chi = ((CharImage) ambiguousGlyphs.get(g));
			ambiguities.add(new Integer(chi.strs.elementCount()));
		}
		System.out.println("Ambiguity distribution:");
		for (Iterator ait = ambiguities.iterator(); ait.hasNext();) {
			Integer ambiguity = ((Integer) ait.next());
			System.out.println(" - " + ambiguity.intValue() + " distinct strings: " + ambiguities.getCount(ambiguity));
		}
		
//		for (int g = 0; g < ambiguousGlyphs.size(); g++) {
//			CharImage chi = ((CharImage) ambiguousGlyphs.get(g));
////			if (chi.strs.elementCount() >= 10)
////				continue;
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
//		
//		CharMap chars = new CharMap();
//		for (int g = 0; g < glyphs.size(); g++) {
//			CharImage chi = ((CharImage) glyphs.get(g));
//			for (Iterator sit = chi.strs.iterator(); sit.hasNext();)
//				chars.putChar(((String) sit.next()), chi);
//		}
//		System.out.println("Got " + chars.charCount() + " chars");
//		
//		CountingSet charImageCounts = new CountingSet(new TreeMap());
//		ArrayList strings = chars.strings();
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
		HashMap disambiguated = new HashMap();
		HashSet discarded = new HashSet();
		File disambiguatedFile = new File("E:/Temp/glyphs.disambiguated.txt");
		if (disambiguatedFile.exists()) try {
			BufferedReader dBr = new BufferedReader(new InputStreamReader(new BufferedInputStream(new FileInputStream(disambiguatedFile)), "UTF-8"));
			for (String line; (line = dBr.readLine()) != null;) {
				String[] glyphData = line.split("\\t");
				if ((glyphData.length == 2) && "DISCARD".equals(glyphData[1])) {
					discarded.add(glyphData[0]);
					continue;
				}
				CountingSet strs = new CountingSet(new TreeMap());
				for (int c = 1; c < glyphData.length; c++) {
					String[] strData = glyphData[c].split("\\s");
					strs.add(strData[0], Integer.parseInt(strData[1]));
				}
				disambiguated.put(glyphData[0], strs);
			}
			dBr.close();
			disambiguatedFile.renameTo(new File("E:/Temp/glyphs.disambiguated.old." + System.currentTimeMillis() + ".txt"));
			disambiguatedFile = new File("E:/Temp/glyphs.disambiguated.txt");
//			stringImageRelsFile = new File("E:/Temp/glyphs.disambiguated.new.txt");
		}
		catch (IOException ioe) {
			ioe.printStackTrace(System.out);
		}
		
		//	disambiguate ambiguous glyphs
		BufferedWriter dBw = new BufferedWriter(new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(disambiguatedFile)), "UTF-8"));
		for (int g = 0; g < glyphs.size(); g++) {
			CharImage chi = ((CharImage) glyphs.get(g));
			if ((chi.strs.elementCount() < 2) || discarded.contains(chi.imageHex) || disambiguated.containsKey(chi.imageHex)) {
				dBw.write(chi.imageHex);
				if (discarded.contains(chi.imageHex)) {
					dBw.write('\t');
					dBw.write("DISCARD");
				}
				else {
					if (disambiguated.containsKey(chi.imageHex)) {
						CountingSet strs = ((CountingSet) disambiguated.get(chi.imageHex));
						chi.strs.clear();
						chi.strs.addAll(strs);
					}
					for (Iterator sit = chi.strs.iterator(); sit.hasNext();) {
						String str = ((String) sit.next());
						dBw.write('\t');
						dBw.write(str);
						dBw.write(' ');
						dBw.write("" + chi.strs.getCount(str));
					}
				}
				dBw.newLine();
				dBw.flush();
				ambiguousGlyphs.remove(chi);
				continue;
			}
			
			ArrayList useStrings = new ArrayList();
			JPanel stringPanel = new JPanel(new GridLayout(0, 4, 10, 3), true);
			stringPanel.setBackground(Color.DARK_GRAY);
			stringPanel.setBorder(BorderFactory.createMatteBorder(3, 7, 3, 7, Color.DARK_GRAY));
			for (Iterator sit = chi.strs.iterator(); sit.hasNext();) {
				String str = ((String) sit.next());
				String hex = ((str.length() == 1) ? ("0x" + Integer.toString(str.charAt(0), 16)) : null);
				String name = ((str.length() == 1) ? StringUtils.getCharName(str.charAt(0)) : null);
				String text = (str + ((hex == null) ? "" : (" (" + hex + ")")) + ((name == null) ? "" : (" " + name)) + " - " + chi.strs.getCount(str) + " times of " + chi.strs.size());
				boolean defUseString;
				if (chi.strs.elementCount() == 1)
					defUseString = true;
				else {
					int chiAllStrCount = chi.strs.size();
					int chiStrCount = chi.strs.getCount(str);
					if (chi.strs.elementCount() == 2)
						defUseString = ((chiStrCount * 3) > (chiAllStrCount * 2));
					else {
						int chiAvgStrCount = (chiAllStrCount / chi.strs.elementCount());
						defUseString = (chiStrCount > (chiAvgStrCount * 2));
					}
				}
				UseString us = new UseString(text, defUseString, str);
				stringPanel.add(us);
				useStrings.add(us);
			}
			JLabel glyph = new JLabel(new ImageIcon(decodeCharacterImage(chi.imageHex)));
			glyph.setBorder(BorderFactory.createMatteBorder(3, 7, 3, 7, Color.DARK_GRAY));
			JCheckBox discardGlyph = new JCheckBox("Discard glyph altogether?");
			
			JPanel message = new JPanel(new BorderLayout(), true);
			if (chi.strs.elementCount() <= 16)
				message.add(stringPanel, BorderLayout.CENTER);
			else {
				JScrollPane stringBox = new JScrollPane(stringPanel);
				stringBox.getVerticalScrollBar().setUnitIncrement(50);
				stringBox.getVerticalScrollBar().setBlockIncrement(50);
				stringBox.setPreferredSize(new Dimension((stringBox.getPreferredSize().width + stringBox.getVerticalScrollBar().getPreferredSize().width), Math.min(stringBox.getPreferredSize().height, 600)));
				message.add(stringBox, BorderLayout.CENTER);
			}
			message.add(glyph, BorderLayout.NORTH);
			message.add(discardGlyph, BorderLayout.SOUTH);
			JOptionPane.showMessageDialog(null, message);
			
			if (discardGlyph.isSelected()) {
				dBw.write(chi.imageHex);
				dBw.write('\t');
				dBw.write("DISCARD");
				dBw.newLine();
				dBw.flush();
				ambiguousGlyphs.remove(chi);
				System.out.println(ambiguousGlyphs.size() + " ambiguous glyhs remaining");
				continue;
			}
			
			for (int s = 0; s < useStrings.size(); s++) {
				UseString us = ((UseString) useStrings.get(s));
				if (!us.isSelected())
					chi.strs.removeAll(us.str);
			}
			
			dBw.write(chi.imageHex);
			for (Iterator sit = chi.strs.iterator(); sit.hasNext();) {
				String str = ((String) sit.next());
				dBw.write('\t');
				dBw.write(str);
				dBw.write(' ');
				dBw.write("" + chi.strs.getCount(str));
			}
			dBw.newLine();
			dBw.flush();
			
			ambiguousGlyphs.remove(chi);
			System.out.println(ambiguousGlyphs.size() + " ambiguous glyhs remaining");
		}
		dBw.close();
	}
	
	private static class UseString extends JCheckBox {
		final String str;
		UseString(String text, boolean selected, String str) {
			super(text, selected);
			this.str = str;
			this.setOpaque(true);
			this.setBackground(selected ? Color.YELLOW : Color.WHITE);
			this.addItemListener(new ItemListener() {
				public void itemStateChanged(ItemEvent ie) {
					setBackground(isSelected() ? Color.YELLOW : Color.WHITE);
				}
			});
			this.setBorder(BorderFactory.createLineBorder(Color.WHITE, 2));
		}
	}
	
	private static class CharImage implements Comparable {
		final String imageHex;
		final CountingSet strs = new CountingSet(new TreeMap());
		CharImage(String imageHex) {
			this.imageHex = imageHex;
		}
		public int compareTo(Object obj) {
			return ((String) this.strs.first()).compareTo((String) ((CharImage) obj).strs.first());
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
