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
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
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
import javax.swing.JPanel;

import de.uka.ipd.idaho.gamta.util.CountingSet;
import de.uka.ipd.idaho.stringUtils.StringUtils;

/**
 * @author sautter
 */
public class PdfFontReferenceAnalyzer {
	public static void main(String[] args) throws Exception {
		BufferedReader br = new BufferedReader(new InputStreamReader(new BufferedInputStream(new FileInputStream(new File("E:/Temp/glyphs.disambiguated.txt"))), "UTF-8"));
//		BufferedReader br = new BufferedReader(new InputStreamReader(new BufferedInputStream(new FileInputStream(new File("E:/Temp/glyphs.pruned.txt"))), "UTF-8"));
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
		
		CharMap chars = new CharMap();
		ArrayList ambiguousGlyphs = new ArrayList();
		ArrayList unassignedGlyphs = new ArrayList();
		for (int g = 0; g < glyphs.size(); g++) {
			CharImage chi = ((CharImage) glyphs.get(g));
			for (Iterator sit = chi.strs.iterator(); sit.hasNext();)
				chars.putChar(((String) sit.next()), chi);
			if (chi.strs.isEmpty())
				unassignedGlyphs.add(chi);
			else if (chi.strs.elementCount() > 1)
				ambiguousGlyphs.add(chi);
		}
		System.out.println("Got " + glyphs.size() + " glyphs, " + ambiguousGlyphs.size() + " ambiguous ones, " + unassignedGlyphs.size() + " unassigned ones");
		System.out.println("Got " + chars.charCount() + " chars");
		
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
		HashMap stringImageRels = new HashMap();
		HashSet discardStrings = new HashSet();
		File stringImageRelsFile = new File("E:/Temp/glyphs.analyzing.txt");
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
			stringImageRelsFile.renameTo(new File("E:/Temp/glyphs.analyzing.old." + System.currentTimeMillis() + ".txt"));
			stringImageRelsFile = new File("E:/Temp/glyphs.pruning.txt");
//			stringImageRelsFile = new File("E:/Temp/glyphs.pruning.new.txt");
		}
		catch (IOException ioe) {
			ioe.printStackTrace(System.out);
		}
		
//		HashMap reconciled = new HashMap();
//		HashSet discarded = new HashSet();
//		File reconciledFile = new File("E:/Temp/glyphs.analyzed.txt");
//		if (reconciledFile.exists()) try {
//			BufferedReader dBr = new BufferedReader(new InputStreamReader(new BufferedInputStream(new FileInputStream(reconciledFile)), "UTF-8"));
//			for (String line; (line = dBr.readLine()) != null;) {
//				String[] glyphData = line.split("\\t");
//				if ((glyphData.length == 2) && "DISCARD".equals(glyphData[1])) {
//					discarded.add(glyphData[0]);
//					continue;
//				}
//				CountingSet strs = new CountingSet(new TreeMap());
//				for (int c = 1; c < glyphData.length; c++) {
//					String[] strData = glyphData[c].split("\\s");
//					strs.add(strData[0], Integer.parseInt(strData[1]));
//				}
//				if (strs.size() != 0)
//					reconciled.put(glyphData[0], strs);
//			}
//			dBr.close();
//			reconciledFile.renameTo(new File("E:/Temp/glyphs.analyzed.old." + System.currentTimeMillis() + ".txt"));
//			reconciledFile = new File("E:/Temp/glyphs.analyzed.txt");
////			stringImageRelsFile = new File("E:/Temp/glyphs.analyzed.new.txt");
//		}
//		catch (IOException ioe) {
//			ioe.printStackTrace(System.out);
//		}
		
		ArrayList strings = chars.strings();
//		BufferedWriter sirBw = new BufferedWriter(new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(stringImageRelsFile)), "UTF-8"));
		for (int s = 0; s < strings.size(); s++) {
			String str = ((String) strings.get(s));
//			if (str.charAt(0) <= 0x1000)
//				continue;
			Char ch = chars.getChar(str);
			
//			if (discardStrings.contains(str)) {
//				for (int i = 0; i < ch.images.size(); i++)
//					((CharImage) ch.images.get(i)).strs.removeAll(ch.str);
//				sirBw.write("DISCARD " + ch.str);
//				sirBw.newLine();
//				sirBw.flush();
//				continue;
//			}
//			
//			if (!"\u2642".equals(str))
//				continue;
			
			CharImageIndexRoot index = new CharImageIndexRoot();
			HashMap chiIndex = new HashMap();
			for (int g = 0; g < ch.images.size(); g++) {
				CharImage chi = ((CharImage) ch.images.get(g));
				index.addCharImage(chi);
				chiIndex.put(chi.imageHex, chi);
			}
			
			//	TODO find local pair-ups of glyphs by visual similarity
			HashMap maxSimChis = new HashMap();
			ArrayList chiClusters = new ArrayList();
			HashMap chiClusterIndex = new HashMap();
			HashMap chBiIndex = new HashMap();
			for (int g = 0; g < ch.images.size(); g++) {
				CharImage chi = ((CharImage) ch.images.get(g));
//				BufferedImage chBi = decodeCharacterImage(chi.imageHex);
				BufferedImage chBi = ((BufferedImage) chBiIndex.get(chi.imageHex));
				if (chBi == null) {
					chBi = decodeCharacterImage(chi.imageHex);
					chBiIndex.put(chi.imageHex, chBi);
				}
				float maxSim = 0;
				CharImage maxSimChi = null;
//				for (int rad = 0; rad < 10; rad++) {
				for (int rad = 2; rad <= 2; rad++) {
//					TreeMap simStringIndex = new TreeMap();
					CharImageList mainChil = new CharImageList();
					for (int l = Math.max(-chi.blankLeft, -rad); l <= rad; l++) {
						for (int r = Math.max(-chi.blankRight, -rad); r <= rad; r++) {
							for (int t = Math.max(-chi.blankTop, -rad); t <= rad; t++) {
								for (int b = Math.max(-chi.blankBottom, -rad); b <= rad; b++) {
									CharImageList chil = index.findCharImages((chi.blankLeft + l), (chi.blankRight + r), (chi.blankTop + t), (chi.blankBottom + b));
									if (chil == null)
										continue;
//									System.out.println("Got " + chil.size() + " glyphs with margins " + (chi.blankLeft + l) + ", " + (chi.blankRight + r) + ", " + (chi.blankTop + t) + ", " + (chi.blankBottom + b));
									for (int c = 0; c < chil.size(); c++) {
										CharImage cChi = chil.getCharImage(c);
										if (cChi != chi)
											mainChil.add(cChi);
									}
								}
							}
						}
					}
					CharImageTray[] cChis = new CharImageTray[mainChil.size()];
					for (int c = 0; c < mainChil.size(); c++)
						cChis[c] = new CharImageTray(mainChil.getCharImage(c), chi);
					Arrays.sort(cChis);
					for (int c = 0; c < cChis.length; c++) {
//						BufferedImage cChBi = decodeCharacterImage(cChi.imageHex);
						if (cChis[c].maxSim < maxSim)
							break; // save the hustle and dance if we can't make (current) best anyway
						BufferedImage cChBi = ((BufferedImage) chBiIndex.get(cChis[c].chi.imageHex));
						if (cChBi == null) {
							cChBi = decodeCharacterImage(cChis[c].chi.imageHex);
							chBiIndex.put(cChis[c].chi.imageHex, cChBi);
						}
						float sim = getImageSimilarity(chBi, cChBi);
						if (maxSim < sim) {
							maxSim = sim;
							maxSimChi = cChis[c].chi;
						}
					}
				}
				
				//	TODO add some absolute threshold (probably a pretty harsh one)
				if (maxSimChi != null) {
					maxSimChis.put(chi.imageHex, maxSimChi.imageHex);
					if (chi.imageHex.equals(maxSimChis.get(maxSimChi.imageHex))) {
//						System.out.println("Starting cluster with max-sim pair (" + maxSim + "): " + chi.imageHex + " / " + maxSimChi.imageHex);
						System.out.println("Starting cluster with max-sim pair (" + maxSim + ")");
						CharImageCluster chic = new CharImageCluster(maxSim);
						chiClusters.add(chic);
						chic.images.add(chi);
						chiIndex.remove(chi.imageHex);
						chiClusterIndex.put(chi.imageHex, chic);
						chic.images.add(maxSimChi);
						chiClusterIndex.put(maxSimChi.imageHex, chic);
						chiIndex.remove(maxSimChi.imageHex);
					}
					else if (chiClusterIndex.containsKey(maxSimChi.imageHex)) {
//						System.out.println("Extending cluster (1) at distance (" + maxSim + "): " + chi.imageHex + " / " + maxSimChi.imageHex);
						System.out.println("Extending cluster (1) at distance (" + maxSim + ")");
						CharImageCluster chic = ((CharImageCluster) chiClusterIndex.get(maxSimChi.imageHex));
						chic.images.add(chi);
						chiIndex.remove(chi.imageHex);
						chiClusterIndex.put(chi.imageHex, chic);
					}
				}
				else System.out.println("Unable to find closest match of " + chi.imageHex);
			}
			
			//	TODO add remaining glyphs to clusters
			for (boolean clusterExtended = true; clusterExtended;) {
				clusterExtended = false;
				ArrayList imageHexs = new ArrayList(chiIndex.keySet());
				for (int h = 0; h < imageHexs.size(); h++) {
					String imageHex = ((String) imageHexs.get(h));
					CharImageCluster chic = ((CharImageCluster) chiClusterIndex.get(maxSimChis.get(imageHex)));
					if (chic == null) {
						System.out.println("Unable to find cluster for closest match " + maxSimChis.get(imageHex));
						continue;
					}
					CharImage chi = ((CharImage) chiIndex.get(imageHex));
//					System.out.println("Extending cluster (2) with " + chi.imageHex);
					chic.images.add(chi);
					chiIndex.remove(chi.imageHex);
					chiClusterIndex.put(chi.imageHex, chic);
					clusterExtended = true;
				}
			}
			System.out.println("Got " + chiClusters.size() + " clusters and " + chiIndex.size() + " unassigned glyphs of " + ch.images.size() + " for " + str);
			
			if ("A".equals(str))
				break;
			
//			HashMap imageRels = ((HashMap) stringImageRels.get(ch.str));
//			if (imageRels == null) {
//				imageRels = new HashMap();
//				stringImageRels.put(ch.str, imageRels);
//			}
//			
//			int exImageRels = 0;
//			ArrayList glyphTrays = new ArrayList();
//			Collections.sort(ch.images, charImageMarginOrder);
//			JPanel glyphPanel = new JPanel(new GridLayout(0, 4, 10, 3), true);
//			glyphPanel.setBackground(Color.DARK_GRAY);
//			glyphPanel.setBorder(BorderFactory.createMatteBorder(3, 7, 3, 7, Color.DARK_GRAY));
//			for (int i = 0; i < ch.images.size(); i++) {
//				CharImage chi = ((CharImage) ch.images.get(i));
//				boolean defUseGlyph;
//				if (imageRels.containsKey(chi.imageHex)) {
//					defUseGlyph = "+".equals(imageRels.get(chi.imageHex));
//					exImageRels++;
//				}
//				else if (chi.strs.elementCount() == 1)
//					defUseGlyph = true;
//				else {
//					int chiAllStrCount = chi.strs.size();
//					int chiStrCount = chi.strs.getCount(ch.str);
//					if (chi.strs.elementCount() == 2)
//						defUseGlyph = ((chiStrCount * 3) > (chiAllStrCount * 2));
//					else {
//						int chiAvgStrCount = (chiAllStrCount / chi.strs.elementCount());
//						defUseGlyph = (chiStrCount > (chiAvgStrCount * 2));
//					}
//				}
//				GlyphTray gt = new GlyphTray(ch.str, chi, defUseGlyph);
//				glyphTrays.add(gt);
//				glyphPanel.add(gt);
//			}
//			JCheckBox discardString = new JCheckBox("Discard string altogether?");
//			
//			if (exImageRels < ch.images.size()) {
//				JPanel message = new JPanel(new BorderLayout(), true);
//				if (ch.images.size() <= 16)
//					message.add(glyphPanel, BorderLayout.CENTER);
//				else {
//					JScrollPane glyphBox = new JScrollPane(glyphPanel);
//					glyphBox.getVerticalScrollBar().setUnitIncrement(50);
//					glyphBox.getVerticalScrollBar().setBlockIncrement(50);
//					glyphBox.setPreferredSize(new Dimension((glyphBox.getPreferredSize().width + glyphBox.getVerticalScrollBar().getPreferredSize().width), Math.min(glyphBox.getPreferredSize().height, 600)));
//					message.add(glyphBox, BorderLayout.CENTER);
//				}
//				message.add(discardString, BorderLayout.SOUTH);
//				JOptionPane.showMessageDialog(null, message, (str + ((ch.hex == null) ? "" : (" (" + ch.hex + ")")) + ((ch.name == null) ? "" : (" " + ch.name)) + " - " + ch.occurrences + " total occurrences with " + ch.images.size() + " distinct glyphs"), JOptionPane.PLAIN_MESSAGE);
//				
//				if (discardString.isSelected()) {
//					for (int i = 0; i < ch.images.size(); i++)
//						((CharImage) ch.images.get(i)).strs.removeAll(ch.str);
//					stringImageRels.remove(ch.str);
//					discardStrings.add(ch.str);
//				}
//				else for (int t = 0; t < glyphTrays.size(); t++) {
//					GlyphTray gt = ((GlyphTray) glyphTrays.get(t));
//					if (!gt.useGlyph.isSelected())
//						gt.chi.strs.removeAll(ch.str);
//				}
//			}
//			else for (int t = 0; t < glyphTrays.size(); t++) {
//				GlyphTray gt = ((GlyphTray) glyphTrays.get(t));
//				if (!gt.useGlyph.isSelected())
//					gt.chi.strs.removeAll(ch.str);
//			}
//			
//			if (discardStrings.contains(ch.str)) {
//				sirBw.write("DISCARD " + ch.str);
//				sirBw.newLine();
//			}
//			else {
//				sirBw.write(ch.str);
//				sirBw.newLine();
//				for (int i = 0; i < ch.images.size(); i++) {
//					CharImage chi = ((CharImage) ch.images.get(i));
//					if (chi.strs.contains(ch.str))
//						sirBw.write("+ " + chi.imageHex);
//					else sirBw.write("- " + chi.imageHex);
//					sirBw.newLine();
//				}
//			}
//			sirBw.flush();
		}
//		sirBw.close();
//		
//		
//		//	assign unassigned glyphs
//		BufferedWriter dBw = new BufferedWriter(new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(reconciledFile)), "UTF-8"));
//		for (int g = 0; g < glyphs.size(); g++) {
//			CharImage chi = ((CharImage) glyphs.get(g));
//			if (reconciled.containsKey(chi.imageHex)) {
//				CountingSet strs = ((CountingSet) reconciled.get(chi.imageHex));
//				chi.strs.clear();
//				chi.strs.addAll(strs);
//			}
//			if ((chi.strs.elementCount() != 0) || discarded.contains(chi.imageHex)) {
//				dBw.write(chi.imageHex);
//				if (discarded.contains(chi.imageHex)) {
//					dBw.write('\t');
//					dBw.write("DISCARD");
//				}
//				else for (Iterator sit = chi.strs.iterator(); sit.hasNext();) {
//					String str = ((String) sit.next());
//					dBw.write('\t');
//					dBw.write(str);
//					dBw.write(' ');
//					dBw.write("" + chi.strs.getCount(str));
//				}
//				dBw.newLine();
//				dBw.flush();
//				unassignedGlyphs.remove(chi);
//				continue;
//			}
//			
//			//	find strings matching glyph based upon visual glyph similarity
//			BufferedImage chBi = decodeCharacterImage(chi.imageHex);
//			for (int rad = 0; rad < 10; rad++) {
//				TreeMap simStringIndex = new TreeMap();
//				for (int l = Math.max(-chi.blankLeft, -rad); l <= rad; l++) {
//					for (int r = Math.max(-chi.blankRight, -rad); r <= rad; r++) {
//						for (int t = Math.max(-chi.blankTop, -rad); t <= rad; t++) {
//							for (int b = Math.max(-chi.blankBottom, -rad); b <= rad; b++) {
//								CharImageList chil = index.findCharImages((chi.blankLeft + l), (chi.blankRight + r), (chi.blankTop + t), (chi.blankBottom + b));
//								if (chil == null)
//									continue;
//								System.out.println("Got " + chil.size() + " glyphs with margins " + (chi.blankLeft + l) + ", " + (chi.blankRight + r) + ", " + (chi.blankTop + t) + ", " + (chi.blankBottom + b));
//								for (int c = 0; c < chil.size(); c++) {
//									CharImage cChi = chil.getCharImage(c);
//									BufferedImage cChBi = decodeCharacterImage(cChi.imageHex);
//									float sim = getImageSimilarity(chBi, chi.pixelCount, cChBi, cChi.pixelCount, 0);
////									System.out.println(" - got similarity " + sim + " to image of " + cChi.strs);
//									//	TODO find some kind of cutoff
//									for (Iterator sit = cChi.strs.iterator(); sit.hasNext();) {
//										String str = ((String) sit.next());
//										SimString sStr = ((SimString) simStringIndex.get(str));
//										if (sStr == null) {
//											sStr = new SimString(str);
//											simStringIndex.put(str, sStr);
//										}
//										sStr.sims.add(new Float(sim));
//									}
//								}
//							}
//						}
//					}
//				}
//				
//				System.out.println("Got " + simStringIndex.size() + " strings with similar glyphs:");
//				ArrayList simStrings = new ArrayList();
//				for (Iterator sit = simStringIndex.keySet().iterator(); sit.hasNext();) {
//					String str = ((String) sit.next());
//					simStrings.add(simStringIndex.get(str));
//				}
//				Collections.sort(simStrings, topSimilaritySimStringOrder);
//				for (int s = 0; s < simStrings.size(); s++) {
//					SimString sStr = ((SimString) simStrings.get(s));
//					System.out.println(" - " + sStr.str + " with " + sStr.sims.size() + " similarities, best is " + sStr.getBestSimilarity());
//				}
//				
//				ArrayList useStrings = new ArrayList();
//				JPanel stringPanel = new JPanel(new GridLayout(0, 4, 10, 3), true);
//				stringPanel.setBackground(Color.DARK_GRAY);
//				stringPanel.setBorder(BorderFactory.createMatteBorder(3, 7, 3, 7, Color.DARK_GRAY));
//				for (int s = 0; s < simStrings.size(); s++) {
//					SimString sStr = ((SimString) simStrings.get(s));
//					String hex = ((sStr.str.length() == 1) ? ("0x" + Integer.toString(sStr.str.charAt(0), 16)) : null);
//					String name = ((sStr.str.length() == 1) ? StringUtils.getCharName(sStr.str.charAt(0)) : null);
//					String text = (sStr.str + ((hex == null) ? "" : (" (" + hex + ")")) + ((name == null) ? "" : (" " + name)) + " - " + sStr.sims.size() + " glyphs, best at " + ((int) Math.round(100 * sStr.getBestSimilarity())) + "%");
//					UseString us = new UseString(text, (s == 0), sStr.str);
//					stringPanel.add(us);
//					useStrings.add(us);
//				}
//				JLabel glyph = new JLabel(new ImageIcon(chBi));
//				glyph.setBorder(BorderFactory.createMatteBorder(3, 7, 3, 7, Color.DARK_GRAY));
//				JCheckBox discardGlyph = new JCheckBox("Discard glyph altogether?");
//				JTextField enterString = new JTextField("<enter HEX or char name>");
//				JPanel functionPanel = new JPanel(new GridLayout(0, 2));
//				functionPanel.add(discardGlyph);
//				functionPanel.add(enterString);
//				
//				JPanel message = new JPanel(new BorderLayout(), true);
//				if (simStrings.size() <= 16)
//					message.add(stringPanel, BorderLayout.CENTER);
//				else {
//					JScrollPane stringBox = new JScrollPane(stringPanel);
//					stringBox.getVerticalScrollBar().setUnitIncrement(50);
//					stringBox.getVerticalScrollBar().setBlockIncrement(50);
//					stringBox.setPreferredSize(new Dimension((stringBox.getPreferredSize().width + stringBox.getVerticalScrollBar().getPreferredSize().width), Math.min(stringBox.getPreferredSize().height, 600)));
//					message.add(stringBox, BorderLayout.CENTER);
//				}
//				message.add(glyph, BorderLayout.NORTH);
//				message.add(functionPanel, BorderLayout.SOUTH);
//				JOptionPane.showMessageDialog(null, message);
//				
//				if (discardGlyph.isSelected()) {
//					discarded.add(chi.imageHex);
//					break;
//				}
//				
//				for (int s = 0; s < useStrings.size(); s++) {
//					UseString us = ((UseString) useStrings.get(s));
//					if (us.isSelected())
//						chi.strs.add(us.str);
//				}
//				if (chi.strs.size() != 0)
//					break;
//				
//				String enteredStr = enterString.getText();
//				String str = null;
//				if (enteredStr.matches("[A-Za-z][a-z]*")) {
//					char ch = StringUtils.getCharForName(enteredStr);
//					if (ch != 0)
//						str = ("" + ch);
//				}
//				else if (enteredStr.matches("0x[0-9A-Fa-f]{2,4}")) {
//					int ch = Integer.parseInt(enteredStr.substring("0x".length()), 16);
//					if (ch > 0)
//						str = ("" + ((char) ch));
//				}
//				if (str != null) {
//					String hex = ((str.length() == 1) ? ("0x" + Integer.toString(str.charAt(0), 16)) : null);
//					String name = ((str.length() == 1) ? StringUtils.getCharName(str.charAt(0)) : null);
//					String text = (str + ((hex == null) ? "" : (" (" + hex + ")")) + ((name == null) ? "" : (" " + name)));
//					int choice = JOptionPane.showConfirmDialog(null, text, "", JOptionPane.YES_NO_OPTION);
//					if (choice == JOptionPane.YES_OPTION) {
//						chi.strs.add(str);
//						break;
//					}
//				}
//			}
//			
//			if (discarded.contains(chi.imageHex)) {
//				dBw.write(chi.imageHex);
//				dBw.write('\t');
//				dBw.write("DISCARD");
//				dBw.newLine();
//				dBw.flush();
//				unassignedGlyphs.remove(chi);
//				System.out.println(unassignedGlyphs.size() + " unassigned glyhs remaining");
//				continue;
//			}
//			
//			dBw.write(chi.imageHex);
//			for (Iterator sit = chi.strs.iterator(); sit.hasNext();) {
//				String str = ((String) sit.next());
//				dBw.write('\t');
//				dBw.write(str);
//				dBw.write(' ');
//				dBw.write("" + chi.strs.getCount(str));
//			}
//			dBw.newLine();
//			dBw.flush();
//			
//			unassignedGlyphs.remove(chi);
//			System.out.println(unassignedGlyphs.size() + " unassigned glyhs remaining");
//		}
//		dBw.close();
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
	
	private static class SimString {
		final String str;
		final CountingSet sims = new CountingSet(new TreeMap());
		SimString(String str) {
			this.str = str;
		}
		float getBestSimilarity() {
			return (this.sims.isEmpty() ? 0 : ((Float) this.sims.last()).floatValue());
		}
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
		final int blankLeft;
		final int blankRight;
		final int blankTop;
		final int blankBottom;
		final int pixelCount;
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
			int tailHexZeros = 0;
			for (int c = (this.imageHex.length() - 1); c >= 0; c--) {
				char hch = this.imageHex.charAt(c);
				if (hch == '0')
					tailHexZeros += 4;
				else {
					if (hch == '8')
						tailHexZeros += 3;
					else if ((hch == 'C') || (hch == '4'))
						tailHexZeros += 2;
					else if ((hch == 'D') || (hch == 'A') || (hch == '6') || (hch == '2'))
						tailHexZeros += 1;
					break;
				}
			}
			int width = (this.imageHex.length() / 8);
			this.blankLeft = ((width <= 32) ? ((32 - width) / 2) : 0);
			this.blankRight = ((width <= 32) ? (32 - this.blankLeft - width) : 0);
			this.blankTop = (leadHexZeros / width);
			this.blankBottom = (tailHexZeros / width);
			int pixelCount = 0;
			for (int c = 0; c < this.imageHex.length(); c++) {
				char hch = this.imageHex.charAt(c);
				if (hch == 'F')
					pixelCount += 4;
				else if ((hch == 'E') || (hch == 'D') || (hch == 'B') || (hch == '7'))
					pixelCount += 3;
				else if ((hch == '8') || (hch == '4') || (hch == '2') || (hch == '1'))
					pixelCount += 1;
				else if (hch != '0')
					pixelCount += 2;
			}
			this.pixelCount = pixelCount;
		}
		public int compareTo(Object obj) {
			return ((String) this.strs.first()).compareTo((String) ((CharImage) obj).strs.first());
		}
	}
	
	private static class CharImageTray implements Comparable {
		final CharImage chi;
		final float maxSim;
		CharImageTray(CharImage chi, CharImage mainChi) {
			this.chi = chi;
			float maxPrecision = ((chi.pixelCount <= mainChi.pixelCount) ? 1 : (((float) mainChi.pixelCount) / chi.pixelCount));
			float maxRecall = ((mainChi.pixelCount <= chi.pixelCount) ? 1 : (((float) chi.pixelCount) / mainChi.pixelCount));
			this.maxSim = (maxPrecision * maxRecall);
		}
		public int compareTo(Object obj) {
			return Float.compare(((CharImageTray) obj).maxSim, this.maxSim);
		}
	}
	
	private static class CharImageCluster {
		final ArrayList images = new ArrayList();
		final float coreSim;
		CharImageCluster(float coreSim) {
			this.coreSim = coreSim;
		}
	}
	
	private static final Comparator charImageMarginOrder = new Comparator() {
		public int compare(Object o1, Object o2) {
			CharImage chi1 = ((CharImage) o1);
			CharImage chi2 = ((CharImage) o2);
			return ((chi1.blankTop == chi2.blankTop) ? (chi2.blankLeft - chi1.blankLeft) : (chi2.blankTop - chi1.blankTop));
		}
	};
	
	private static class CharImageList extends ArrayList {
		CharImage getCharImage(int index) {
			return ((CharImage) this.get(index));
		}
	}
	
	private static class CharImageIndexRoot extends TreeMap {
		void addCharImage(CharImage chi) {
			Integer key = getMarginKey(chi.blankTop, chi.blankBottom);
			CharImageIndexLeaf chil = ((CharImageIndexLeaf) this.get(key));
			if (chil == null) {
				chil = new CharImageIndexLeaf();
				this.put(key, chil);
			}
			chil.addCharImage(chi);
		}
		CharImageList findCharImages(int blankLeft, int blankRight, int blankTop, int blankBottom) {
			Integer key = getMarginKey(blankTop, blankBottom);
			CharImageIndexLeaf chil = ((CharImageIndexLeaf) this.get(key));
			return ((chil == null) ? null : chil.findCharImages(blankLeft, blankRight));
		}
	}
	
	private static class CharImageIndexLeaf extends TreeMap {
		void addCharImage(CharImage chi) {
			Integer key = getMarginKey(chi.blankLeft, chi.blankRight);
			CharImageList chil = ((CharImageList) this.get(key));
			if (chil == null) {
				chil = new CharImageList();
				this.put(key, chil);
			}
			chil.add(chi);
		}
		CharImageList findCharImages(int blankLeft, int blankRight) {
			Integer key = getMarginKey(blankLeft, blankRight);
			return ((CharImageList) this.get(key));
		}
	}
	
	private static Integer getMarginKey(int blankTopLeft, int blankBottomRight) {
		return new Integer((blankTopLeft * 32) + blankBottomRight);
	}
	
	private static final Comparator topSimilaritySimStringOrder = new Comparator() {
		public int compare(Object o1, Object o2) {
			SimString ss1 = ((SimString) o1);
			SimString ss2 = ((SimString) o2);
			return Float.compare(ss2.getBestSimilarity(), ss1.getBestSimilarity());
		}
	};
	
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
	
	private static float getImageSimilarity(BufferedImage bi, BufferedImage cbi) {
		float bestSim = 0;
		for (int xo = -2; xo <= 2; xo++) {
			for (int yo = -2; yo <= 2; yo++) {
				int matched = 0;
				int missed = 0;
				int spurious = 0;
				for (int x = 0; x < bi.getWidth(); x++) {
					for (int y = 0; y < bi.getHeight(); y++) {
						int rgb = bi.getRGB(x, y);
						int cRgb;
						if ((x + xo) < 0)
							cRgb = whiteRgb;
						else if (cbi.getWidth() <= (x + xo))
							cRgb = whiteRgb;
						else if ((y + yo) < 0)
							cRgb = whiteRgb;
						else if (cbi.getHeight() <= (y + yo))
							cRgb = whiteRgb;
						else cRgb = cbi.getRGB((x + xo), (y + yo));
						if ((rgb == whiteRgb) && (cRgb == whiteRgb))
							continue;
						if (rgb == cRgb)
							matched++;
						else if (rgb == blackRgb)
							spurious++;
						else if (cRgb == blackRgb)
							missed++;
					}
				}
				float precision = ((matched == 0) ? 0 : (((float) matched) / (matched + spurious)));
				float recall = ((matched == 0) ? 0 : (((float) matched) / (matched + missed)));
				float sim = (precision * recall);
				bestSim = Math.max(sim, bestSim);
					
			}
		}
		return bestSim;
	}
}
