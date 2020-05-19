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
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;

import de.uka.ipd.idaho.gamta.util.swing.DialogFactory;
import de.uka.ipd.idaho.im.analysis.Imaging;
import de.uka.ipd.idaho.im.analysis.Imaging.AnalysisImage;
import de.uka.ipd.idaho.im.analysis.Imaging.ImagePartRectangle;
import de.uka.ipd.idaho.im.util.ImFontUtils;

/**
 * This class renders characters and computes a signature, consisting of
 * - the size relative to the font box
 * - the number of disjoint parts
 * - the number of loops (enclosed patches of whitespace)
 * 
 * @author sautter
 */
public class PdfCharSignatureGenerator {
	
	private static final int SERIF = 4;
	private static final boolean SERIF_IS_STYLE = true;
	
	private static final String[] styleNames4 = {"Plain", "Bold", "Italics", "BoldItalics"};
	private static final String[] styleNames8 = {"Sans-Plain", "Sans-Bold", "Sans-Italics", "Sans-BoldItalics", "Serif-Plain", "Serif-Bold", "Serif-Italics", "Serif-BoldItalics"};
	private static final String[] styleNames = (SERIF_IS_STYLE ? styleNames8 : styleNames4);
	
	//	this should be sufficiently large to capture all details ...
	private static int measurementFontSize = 96;
	private static boolean outputCode = true;
	private static char debugChar = ((char) 0);
	
	public static void main(String[] args) throws Exception {
//		
//		//	get font families
//		String[] fontFamilies = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
//		if (true) {
//			for (int f = 0; f < fontFamilies.length; f++)
//				System.out.println(fontFamilies[f]);
//			return;
//		}
		
		//	set up char metrics collection
		TreeSet charMetrics = new TreeSet(new Comparator() {
			public int compare(Object obj1, Object obj2) {
				return ((CharMetrics) obj1).getId().compareTo(((CharMetrics) obj2).getId());
			}
		});
		
		//	make sure we have the fonts we need
		ImFontUtils.loadFreeFonts();
		
		//	collect char metrics for various font faces and styles
		for (int s = Font.PLAIN; s <= (Font.BOLD | Font.ITALIC); s++) {
//			Font sans = new Font("Sans", s, measurementFontSize);
			Font sans = new Font("FreeSans", s, measurementFontSize);
			getCharMetrics(sans, s, charMetrics);
//			Font serif = new Font("Serif", s, measurementFontSize);
			Font serif = new Font("FreeSerif", s, measurementFontSize);
			getCharMetrics(serif, ((SERIF_IS_STYLE ? SERIF : 0) | s), charMetrics);
			for (int f = 0; f < fontNames.length; f++) {
				Font font = new Font(fontNames[f], s, measurementFontSize);
				getCharMetrics(font, (((SERIF_IS_STYLE && serifFonts.contains(fontNames[f])) ? SERIF : 0) | s), charMetrics);
			}
		}
		
		//	find x-height, cap height, and descender height
		//	TODO_not we better do this for individual fonts
		//	width and height already _are_ font specific, and for the rest, we do _need_ the averages
//		float xHeightSum = 0;
//		int xHeightCharCount = 0;
//		float capHeightSum = 0;
//		int capHeightCharCount = 0;
//		float descHeightSum = 0;
//		int descHeightCharCount = 0;
//		for (Iterator cmit = charMetrics.iterator(); cmit.hasNext();) {
//			CharMetrics cm = ((CharMetrics) cmit.next());
//			if ("acegmnopqrsuvwxyz".indexOf(cm.ch) != -1) {
//				xHeightSum += cm.rAscent;
//				xHeightCharCount++;
//			}
//			if ("ABCDEFGHIJKLMNOPQRSTUVWXYZbdhk0123456789".indexOf(cm.ch) != -1) {
//				capHeightSum += cm.rAscent;
//				capHeightCharCount++;
//			}
//			if ("gjpqy".indexOf(cm.ch) != -1) {
//				descHeightSum += cm.rDescent;
//				descHeightCharCount++;
//			}
//		}
//		float xHeight = (xHeightSum / xHeightCharCount);
//		System.out.println("XHeight is " + xHeight);
//		float capHeight = (capHeightSum / capHeightCharCount);
//		System.out.println("CapHeight is " + capHeight);
//		float descHeight = (descHeightSum / descHeightCharCount);
//		System.out.println("DescHeight is " + descHeight);
		
		//	evaluate measurements
		char ch = ((char) 0);
		LinkedList chMetrics = new LinkedList();
		for (Iterator cmit = charMetrics.iterator(); cmit.hasNext();) {
			CharMetrics cm = ((CharMetrics) cmit.next());
			if (cm.ch != ch) {
//				outputCharSignature(ch, chMetrics, xHeight, capHeight, descHeight);
				outputCharSignature(ch, chMetrics);
				if (!outputCode && (ch == debugChar))
					for (Iterator dcmit = chMetrics.iterator(); dcmit.hasNext();) {
						CharMetrics dcm = ((CharMetrics) dcmit.next());
						System.out.println("For font " + dcm.fontName + "-" + dcm.fontStyle);
						int[][] dFbSignature = new int[charSignatureWidth][charSignatureHeight];
						for (int c = 0; c < charSignatureWidth; c++) {
							for (int r = 0; r < charSignatureHeight; r++)
								dFbSignature[c][r] = dcm.fontBoxSignature[c][r];
						}
						int[][] dCbSignature = new int[charSignatureWidth][charSignatureHeight];
						for (int c = 0; c < charSignatureWidth; c++) {
							for (int r = 0; r < charSignatureHeight; r++)
								dCbSignature[c][r] = dcm.charBoxSignature[c][r];
						}
						outputCharSignature(ch, dcm.fontStyle, 1, dcm.disjointParts, dcm.disjointParts, dcm.disjointParts, dcm.loops, dcm.loops, dcm.loops, dFbSignature, dcm.relCharBoxTop, dcm.relCharBoxBottom, dcm.charBoxProportion, dCbSignature);
					}
				ch = cm.ch;
				chMetrics.clear();
				if (!outputCode)
					System.out.println("Measurements for '" + ch + "' (" + Integer.toString(((int) ch), 16).toUpperCase() + ")");
			}
			chMetrics.add(cm);
		}
//		outputCharSignature(ch, chMetrics, xHeight, capHeight, descHeight);
		outputCharSignature(ch, chMetrics);
		if (!outputCode && (ch == debugChar))
			for (Iterator dcmit = chMetrics.iterator(); dcmit.hasNext();) {
				CharMetrics dcm = ((CharMetrics) dcmit.next());
				System.out.println("For font " + dcm.fontName + "-" + dcm.fontStyle);
				int[][] dFbSignature = new int[charSignatureWidth][charSignatureHeight];
				for (int c = 0; c < charSignatureWidth; c++) {
					for (int r = 0; r < charSignatureHeight; r++)
						dFbSignature[c][r] = dcm.fontBoxSignature[c][r];
				}
				int[][] dCbSignature = new int[charSignatureWidth][charSignatureHeight];
				for (int c = 0; c < charSignatureWidth; c++) {
					for (int r = 0; r < charSignatureHeight; r++)
						dCbSignature[c][r] = dcm.charBoxSignature[c][r];
				}
				outputCharSignature(ch, dcm.fontStyle, 1, dcm.disjointParts, dcm.disjointParts, dcm.disjointParts, dcm.loops, dcm.loops, dcm.loops, dFbSignature, dcm.relCharBoxTop, dcm.relCharBoxBottom, dcm.charBoxProportion, dCbSignature);
			}
		chMetrics.clear();
	}
	
	private static void outputCharSignature(char ch, LinkedList chMetrics) {
		if (chMetrics.isEmpty())
			return;
		
		int[] sChMetricCounts = {0, 0, 0, 0, 0, 0, 0, 0};
		int minParts = Integer.MAX_VALUE;
		int[] sMinParts = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
		int maxParts = 0;
		int[] sMaxParts = {0, 0, 0, 0, 0, 0, 0, 0};
		int sumParts = 0;
		int[] sSumParts = {0, 0, 0, 0, 0, 0, 0, 0};
		int minLoops = Integer.MAX_VALUE;
		int[] sMinLoops = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
		int maxLoops = 0;
		int[] sMaxLoops = {0, 0, 0, 0, 0, 0, 0, 0};
		int sumLoops = 0;
		int[] sSumLoops = {0, 0, 0, 0, 0, 0, 0, 0};
		int[][] sumFbSignature = new int[charSignatureWidth][charSignatureHeight];
		int[][][] sSumFbSignatures = new int[(SERIF | (Font.BOLD | Font.ITALIC)) + 1][charSignatureWidth][charSignatureHeight];
		float sumRelCbTop = 0;
		float[] sSumRelCbTops = {0, 0, 0, 0, 0, 0, 0, 0};
		float sumRelCbBottom = 0;
		float[] sSumRelCbBottoms = {0, 0, 0, 0, 0, 0, 0, 0};
		float sumCbProportion = 0;
		float[] sSumCbProportions = {0, 0, 0, 0, 0, 0, 0, 0};
		int[][] sumCbSignature = new int[charSignatureWidth][charSignatureHeight];
		int[][][] sSumCbSignatures = new int[(SERIF | (Font.BOLD | Font.ITALIC)) + 1][charSignatureWidth][charSignatureHeight];
		
		for (Iterator cmit = chMetrics.iterator(); cmit.hasNext();) {
			CharMetrics cm = ((CharMetrics) cmit.next());
			sChMetricCounts[cm.fontStyle]++;
			
			minParts = Math.min(minParts, cm.disjointParts);
			sMinParts[cm.fontStyle] = Math.min(sMinParts[cm.fontStyle], cm.disjointParts);
			maxParts = Math.max(maxParts, cm.disjointParts);
			sMaxParts[cm.fontStyle] = Math.max(sMaxParts[cm.fontStyle], cm.disjointParts);
			sumParts += cm.disjointParts;
			sSumParts[cm.fontStyle] += cm.disjointParts;
			
			minLoops = Math.min(minLoops, cm.loops);
			sMinLoops[cm.fontStyle] = Math.min(sMinLoops[cm.fontStyle], cm.loops);
			maxLoops = Math.max(maxLoops, cm.loops);
			sMaxLoops[cm.fontStyle] = Math.max(sMaxLoops[cm.fontStyle], cm.loops);
			sumLoops += cm.loops;
			sSumLoops[cm.fontStyle] += cm.loops;
			
			for (int c = 0; c < charSignatureWidth; c++)
				for (int r = 0; r < charSignatureHeight; r++) {
					sumFbSignature[c][r] += cm.fontBoxSignature[c][r];
					sSumFbSignatures[cm.fontStyle][c][r] += cm.fontBoxSignature[c][r];
				}
			
			sumRelCbTop += cm.relCharBoxTop;
			sSumRelCbTops[cm.fontStyle] += cm.relCharBoxTop;
			sumRelCbBottom += cm.relCharBoxBottom;
			sSumRelCbBottoms[cm.fontStyle] += cm.relCharBoxBottom;
			sumCbProportion += cm.charBoxProportion;
			sSumCbProportions[cm.fontStyle] += cm.charBoxProportion;
			
			for (int c = 0; c < charSignatureWidth; c++)
				for (int r = 0; r < charSignatureHeight; r++) {
					sumCbSignature[c][r] += cm.charBoxSignature[c][r];
					sSumCbSignatures[cm.fontStyle][c][r] += cm.charBoxSignature[c][r];
				}
		}
		
		outputCharSignature(ch, -1, chMetrics.size(), minParts, maxParts, sumParts, minLoops, maxLoops, sumLoops, sumFbSignature, sumRelCbTop, sumRelCbBottom, sumCbProportion, sumCbSignature);
		for (int s = 0; s <= ((SERIF_IS_STYLE ? SERIF : 0) | (Font.BOLD | Font.ITALIC)); s++)
			outputCharSignature(ch, s, sChMetricCounts[s], sMinParts[s], sMaxParts[s], sSumParts[s], sMinLoops[s], sMaxLoops[s], sSumLoops[s], sSumFbSignatures[s], sSumRelCbTops[s], sSumRelCbBottoms[s], sSumCbProportions[s], sSumCbSignatures[s]);
	}
	
	private static void outputCharSignature(char ch, int style, int chMetricsCount, int minParts, int maxParts, int sumParts, int minLoops, int maxLoops, int sumLoops, int[][] sumFbSignature, float sumRelCbTop, float sumRelCbBottom, float sumCbProportion, int[][] sumCbSignature) {
		if (chMetricsCount == 0) {
			if (!outputCode)
				System.out.println(" - " + ((style < 0) ? "Overall" : styleNames[style]) + ": no measurements to work with");
			return;
		}
		
		boolean[] fbIsAllZero = new boolean[charSignatureWidth];
		Arrays.fill(fbIsAllZero, false);
		for (int c = 0; c < charSignatureWidth; c++) {
			fbIsAllZero[c] = true;
			for (int r = 0; r < charSignatureHeight; r++)
				if (sumFbSignature[c][r] > (chMetricsCount / 2)) {
					fbIsAllZero[c] = false;
					break;
				}
			if (!fbIsAllZero[c])
				break;
		}
		for (int c = (charSignatureWidth - 1); c >= 0; c--) {
			fbIsAllZero[c] = true;
			for (int r = 0; r < charSignatureHeight; r++)
				if (sumFbSignature[c][r] > (chMetricsCount / 2)) {
					fbIsAllZero[c] = false;
					break;
				}
			if (!fbIsAllZero[c])
				break;
		}
		
		if (!outputCode) {
			System.out.println(" - " + ((style < 0) ? "Overall" : styleNames[style]) + ": average of " + chMetricsCount + " measurements:");
			System.out.println("   - parts: " + ((sumParts + (chMetricsCount / 2)) / chMetricsCount) + " [" + minParts + "," + maxParts + "]");
			System.out.println("   - loops: " + ((sumLoops + (chMetricsCount / 2)) / chMetricsCount) + " [" + minLoops + "," + maxLoops + "]");
			System.out.println("   - font box signature:");
			for (int r = 0; r < charSignatureHeight; r++) {
				System.out.print("     ");
				for (int c = 0; c < charSignatureWidth; c++) {
					if (fbIsAllZero[c])
						continue;
					System.out.print(Integer.toString(((sumFbSignature[c][r] + (chMetricsCount / 2)) / chMetricsCount), (charSignatureMaxDensity + 1)));
				}
				System.out.println();
			}
			System.out.println("   - char box signature (rel-top is " + (sumRelCbTop / chMetricsCount) + ", rel-bottom is " + (sumRelCbBottom / chMetricsCount) + ", proportion is " + (sumCbProportion / chMetricsCount) + "):");
			for (int r = 0; r < charSignatureHeight; r++) {
				System.out.print("     ");
				for (int c = 0; c < charSignatureWidth; c++)
					System.out.print(Integer.toString(((sumCbSignature[c][r] + (chMetricsCount / 2)) / chMetricsCount), (charSignatureMaxDensity + 1)));
				System.out.println();
			}
		}
		
		if (outputCode) {
			byte isMultiPart;
			if (minParts > 1)
				isMultiPart = ALWAYS;
			else if (((sumParts + (chMetricsCount / 2)) / chMetricsCount) > 1)
				isMultiPart = USUALLY;
			else if (maxParts > 1)
				isMultiPart = SOMETIMES;
			else isMultiPart = NEVER;
			
			byte hasLoops;
			if (minLoops > 0)
				hasLoops = ALWAYS;
			else if (((sumLoops + (chMetricsCount / 2)) / chMetricsCount) > 0)
				hasLoops = USUALLY;
			else if (maxLoops > 0)
				hasLoops = SOMETIMES;
			else hasLoops = NEVER;
			
			if (style < 0) {
				System.out.println("//\t\\u" + Integer.toString(((int) ch), 16) + " = '" + ch + "'");
				System.out.print(Integer.toString(((int) ch), 16) + "\t" + style + "\t" + isMultiPart + "\t" + hasLoops);
				for (int r = 0; r < charSignatureHeight; r++) {
					System.out.print((r == 0) ? "\t" : ";");
					for (int c = 0; c < charSignatureWidth; c++) {
						if (fbIsAllZero[c])
							continue;
						System.out.print(Integer.toString(((sumFbSignature[c][r] + (chMetricsCount / 2)) / chMetricsCount), (charSignatureMaxDensity + 1)));
					}
				}
				System.out.print("\t" + (sumRelCbTop / chMetricsCount));
				System.out.print("\t" + (sumRelCbBottom / chMetricsCount));
				System.out.print("\t" + (sumCbProportion / chMetricsCount));
				for (int r = 0; r < charSignatureHeight; r++) {
					System.out.print((r == 0) ? "\t" : ";");
					for (int c = 0; c < charSignatureWidth; c++)
						System.out.print(Integer.toString(((sumCbSignature[c][r] + (chMetricsCount / 2)) / chMetricsCount), (charSignatureMaxDensity + 1)));
				}
				System.out.println();
			}
			else {
				System.out.print(Integer.toString(((int) ch), 16) + "\t" + style + "\t" + isMultiPart + "\t" + hasLoops);
				for (int r = 0; r < charSignatureHeight; r++) {
					System.out.print((r == 0) ? "\t" : ";");
					for (int c = 0; c < charSignatureWidth; c++) {
						if (fbIsAllZero[c])
							continue;
						System.out.print(Integer.toString(((sumFbSignature[c][r] + (chMetricsCount / 2)) / chMetricsCount), (charSignatureMaxDensity + 1)));
					}
				}
				System.out.print("\t" + (sumRelCbTop / chMetricsCount));
				System.out.print("\t" + (sumRelCbBottom / chMetricsCount));
				System.out.print("\t" + (sumCbProportion / chMetricsCount));
				for (int r = 0; r < charSignatureHeight; r++) {
					System.out.print((r == 0) ? "\t" : ";");
					for (int c = 0; c < charSignatureWidth; c++)
						System.out.print(Integer.toString(((sumCbSignature[c][r] + (chMetricsCount / 2)) / chMetricsCount), (charSignatureMaxDensity + 1)));
				}
				System.out.println();
			}
		}
	}
//	
//	private static void outputCharSignature(char ch, LinkedList chMetrics, float xHeight, float capHeight, float descHeight) {
//		if (chMetrics.isEmpty())
//			return;
//		
//		int[] sChMetricCounts = {0, 0, 0, 0};
//		float minAscent = Integer.MAX_VALUE;
//		float[] sMinAscents = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
//		float maxAscent = Integer.MIN_VALUE;
//		float[] sMaxAscents = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
//		float sumAscent = 0;
//		float[] sSumAscents = {0, 0, 0, 0};
//		float minDescent = Integer.MAX_VALUE;
//		float[] sMinDescents = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
//		float maxDescent = Integer.MIN_VALUE;
//		float[] sMaxDescents = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
//		float sumDescent = 0;
//		float[] sSumDescents = {0, 0, 0, 0};
//		int minParts = Integer.MAX_VALUE;
//		int[] sMinParts = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
//		int maxParts = 0;
//		int[] sMaxParts = {0, 0, 0, 0};
//		int sumParts = 0;
//		int[] sSumParts = {0, 0, 0, 0};
//		int minLoops = Integer.MAX_VALUE;
//		int[] sMinLoops = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
//		int maxLoops = 0;
//		int[] sMaxLoops = {0, 0, 0, 0};
//		int sumLoops = 0;
//		int[] sSumLoops = {0, 0, 0, 0};
//		float minWidth = Integer.MAX_VALUE;
//		float[] sMinWidths = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
//		float maxWidth = 0;
//		float[] sMaxWidths = {0, 0, 0, 0};
//		float sumWidth = 0;
//		float[] sSumWidths = {0, 0, 0, 0};
//		float minHeight = Integer.MAX_VALUE;
//		float[] sMinHeights = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
//		float maxHeight = 0;
//		float[] sMaxHeights = {0, 0, 0, 0};
//		float sumHeight = 0;
//		float[] sSumHeights = {0, 0, 0, 0};
//		
//		for (Iterator cmit = chMetrics.iterator(); cmit.hasNext();) {
//			CharMetrics cm = ((CharMetrics) cmit.next());
//			sChMetricCounts[cm.fontStyle]++;
//			
//			minAscent = Math.min(minAscent, cm.rAscent);
//			sMinAscents[cm.fontStyle] = Math.min(sMinAscents[cm.fontStyle], cm.rAscent);
//			maxAscent = Math.max(maxAscent, cm.rAscent);
//			sMaxAscents[cm.fontStyle] = Math.max(sMaxAscents[cm.fontStyle], cm.rAscent);
//			sumAscent += cm.rAscent;
//			sSumAscents[cm.fontStyle] += cm.rAscent;
//			
//			minDescent = Math.min(minDescent, cm.rDescent);
//			sMinDescents[cm.fontStyle] = Math.min(sMinDescents[cm.fontStyle], cm.rDescent);
//			maxDescent = Math.max(maxDescent, cm.rDescent);
//			sMaxDescents[cm.fontStyle] = Math.max(sMaxDescents[cm.fontStyle], cm.rDescent);
//			sumDescent += cm.rDescent;
//			sSumDescents[cm.fontStyle] += cm.rDescent;
//			
//			minParts = Math.min(minParts, cm.disjointParts);
//			sMinParts[cm.fontStyle] = Math.min(sMinParts[cm.fontStyle], cm.disjointParts);
//			maxParts = Math.max(maxParts, cm.disjointParts);
//			sMaxParts[cm.fontStyle] = Math.max(sMaxParts[cm.fontStyle], cm.disjointParts);
//			sumParts += cm.disjointParts;
//			sSumParts[cm.fontStyle] += cm.disjointParts;
//			
//			minLoops = Math.min(minLoops, cm.loops);
//			sMinLoops[cm.fontStyle] = Math.min(sMinLoops[cm.fontStyle], cm.loops);
//			maxLoops = Math.max(maxLoops, cm.loops);
//			sMaxLoops[cm.fontStyle] = Math.max(sMaxLoops[cm.fontStyle], cm.loops);
//			sumLoops += cm.loops;
//			sSumLoops[cm.fontStyle] += cm.loops;
//			
//			minWidth = Math.min(minWidth, cm.rWidth);
//			sMinWidths[cm.fontStyle] = Math.min(sMinWidths[cm.fontStyle], cm.rWidth);
//			maxWidth = Math.max(maxWidth, cm.rWidth);
//			sMaxWidths[cm.fontStyle] = Math.max(sMaxWidths[cm.fontStyle], cm.rWidth);
//			sumWidth += cm.rWidth;
//			sSumWidths[cm.fontStyle] += cm.rWidth;
//			
//			minHeight = Math.min(minHeight, cm.rHeight);
//			sMinHeights[cm.fontStyle] = Math.min(sMinHeights[cm.fontStyle], cm.rHeight);
//			maxHeight = Math.max(maxHeight, cm.rHeight);
//			sMaxHeights[cm.fontStyle] = Math.max(sMaxHeights[cm.fontStyle], cm.rHeight);
//			sumHeight += cm.rHeight;
//			sSumHeights[cm.fontStyle] += cm.rHeight;
//		}
//		
//		outputCharSignature(ch, -1, chMetrics.size(), xHeight, capHeight, descHeight, minAscent, maxAscent, sumAscent, minDescent, maxDescent, sumDescent, minParts, maxParts, sumParts, minLoops, maxLoops, sumLoops, minWidth, maxWidth, sumWidth, minHeight, maxHeight, sumHeight);
//		for (int s = 0; s <= (Font.BOLD | Font.ITALIC); s++)
//			outputCharSignature(ch, s, sChMetricCounts[s], xHeight, capHeight, descHeight, sMinAscents[s], sMaxAscents[s], sSumAscents[s], sMinDescents[s], sMaxDescents[s], sSumDescents[s], sMinParts[s], sMaxParts[s], sSumParts[s], sMinLoops[s], sMaxLoops[s], sSumLoops[s], sMinWidths[s], sMaxWidths[s], sSumWidths[s], sMinHeights[s], sMaxHeights[s], sSumHeights[s]);
//	}
//	
//	private static void outputCharSignature(char ch, int style, int chMetricsCount, float xHeight, float capHeight, float descHeight, float minAscent, float maxAscent, float sumAscent, float minDescent, float maxDescent, float sumDescent, int minParts, int maxParts, int sumParts, int minLoops, int maxLoops, int sumLoops, float minWidth, float maxWidth, float sumWidth, float minHeight, float maxHeight, float sumHeight) {
//		if (chMetricsCount == 0) {
//			if (!outputCode)
//				System.out.println(" - " + ((style < 0) ? "Overall" : styleNames[style]) + ": no measurements to work with");
//			return;
//		}
//		
//		if (!outputCode) {
//			System.out.println(" - " + ((style < 0) ? "Overall" : styleNames[style]) + ": average of " + chMetricsCount + " measurements:");
//			System.out.println("   - ascent: " + (sumAscent / chMetricsCount) + " [" + minAscent + "," + maxAscent + "]");
//			System.out.println("   - descent: " + (sumDescent / chMetricsCount) + " [" + minDescent + "," + maxDescent + "]");
//			System.out.println("   - parts: " + ((sumParts + (chMetricsCount / 2)) / chMetricsCount) + " [" + minParts + "," + maxParts + "]");
//			System.out.println("   - loops: " + ((sumLoops + (chMetricsCount / 2)) / chMetricsCount) + " [" + minLoops + "," + maxLoops + "]");
//			System.out.println("   - relWidth: " + (sumWidth / chMetricsCount) + " [" + minWidth + "," + maxWidth + "]");
//			System.out.println("   - relHeight: " + (sumHeight / chMetricsCount) + " [" + minHeight + "," + maxHeight + "]");
//		}
//		
////		float midAscent = ((xHeight + capHeight) / 2);
////		float midDescent = (descHeight / 2);
////		float midXHeight = (xHeight / 2);
////		
////		byte inAscent;
////		if ((maxDescent < 0) && (midXHeight < -maxDescent))
////			inAscent = ALWAYS;
////		else if (minAscent > midAscent)
////			inAscent = ALWAYS;
////		else if ((sumAscent / chMetricsCount) > midAscent)
////			inAscent = USUALLY;
////		else if (maxAscent > midAscent)
////			inAscent = SOMETIMES;
////		else inAscent = NEVER;
////		
////		byte inDescent;
////		if (maxAscent <= 0)
////			inDescent = ALWAYS;
////		else if (minDescent > midDescent)
////			inDescent = ALWAYS;
////		else if ((sumDescent / chMetricsCount) > midDescent)
////			inDescent = USUALLY;
////		else if (maxDescent > midDescent)
////			inDescent = SOMETIMES;
////		else inDescent = NEVER;
////		
////		byte inXHeight;
////		if ((maxAscent < midXHeight) && (maxDescent < midDescent))
////			inXHeight = ALWAYS;
////		else if (((maxDescent < 0) && (midXHeight < -maxDescent)) || (maxAscent < midXHeight))
////			inXHeight = NEVER;
////		else if ((((sumDescent / chMetricsCount) < 0) && (midXHeight < -(sumDescent / chMetricsCount))) || ((sumAscent / chMetricsCount) < midXHeight))
////			inXHeight = SOMETIMES;
////		else if (((minDescent < 0) && (midXHeight < -minDescent)) || (minAscent < midXHeight))
////			inXHeight = USUALLY;
////		else inXHeight = ALWAYS;
//		
//		byte isMultiPart;
//		if (minParts > 1)
//			isMultiPart = ALWAYS;
//		else if (((sumParts + (chMetricsCount / 2)) / chMetricsCount) > 1)
//			isMultiPart = USUALLY;
//		else if (maxParts > 1)
//			isMultiPart = SOMETIMES;
//		else isMultiPart = NEVER;
//		
//		byte hasLoops;
//		if (minLoops > 0)
//			hasLoops = ALWAYS;
//		else if (((sumLoops + (chMetricsCount / 2)) / chMetricsCount) > 0)
//			hasLoops = USUALLY;
//		else if (maxLoops > 0)
//			hasLoops = SOMETIMES;
//		else hasLoops = NEVER;
////		
////		if (outputCode) {
////			if (style < 0) {
////				System.out.println("//\t\\u" + Integer.toString(((int) ch), 16) + " = '" + ch + "'");
////				System.out.println("cs = new CharSignature(((char) Integer.parseInt(\"" + Integer.toString(((int) ch), 16) + "\", 16)), " + indicatorByteNames[inAscent] + ", " + indicatorByteNames[inXHeight] + ", " + indicatorByteNames[inDescent] + ", " + indicatorByteNames[isMultiPart] + ", " + indicatorByteNames[hasLoops] + ", " + (sumWidth / chMetricsCount) + "f, 0f);");
////				System.out.println("csList.add(cs);");
////			}
////			else System.out.println("cs.addStyle(" + style + ", " + indicatorByteNames[inAscent] + ", " + indicatorByteNames[inXHeight] + ", " + indicatorByteNames[inDescent] + ", " + indicatorByteNames[isMultiPart] + ", " + indicatorByteNames[hasLoops] + ", " + (sumWidth / chMetricsCount) + "f, 0f);");
////		}
////		else System.out.println("   --> char signature: " + inAscent + "-" + inXHeight + "-" + inDescent + "/" + isMultiPart + "/" + hasLoops);
//		if (outputCode) {
//			if (style < 0) {
////				System.out.println("//\t\\u" + Integer.toString(((int) ch), 16) + " = '" + ch + "'");
////				System.out.println("cs = new CharSignature(((char) Integer.parseInt(\"" + Integer.toString(((int) ch), 16) + "\", 16)), " + indicatorByteNames[isMultiPart] + ", " + indicatorByteNames[hasLoops] + ", " + (sumAscent / chMetricsCount) + "f, " + (sumDescent / chMetricsCount) + "f, " + (sumWidth / chMetricsCount) + "f, " + (sumHeight / chMetricsCount) + "f);");
////				System.out.println("csList.add(cs);");
//				System.out.println("//\t\\u" + Integer.toString(((int) ch), 16) + " = '" + ch + "'");
//				System.out.println(Integer.toString(((int) ch), 16) + "\t" + style + "\t" + isMultiPart + "\t" + hasLoops + "\t" + (sumAscent / chMetricsCount) + "\t" + (sumDescent / chMetricsCount) + "\t" + (sumWidth / chMetricsCount) + "\t" + (sumHeight / chMetricsCount));
//			}
////			else System.out.println("cs.addStyle(" + style + ", " + ", " + indicatorByteNames[isMultiPart] + ", " + indicatorByteNames[hasLoops] + ", " + (sumAscent / chMetricsCount) + "f, " + (sumDescent / chMetricsCount) + "f, " + (sumWidth / chMetricsCount) + "f, " + (sumHeight / chMetricsCount) + "f);");
//			else System.out.println(Integer.toString(((int) ch), 16) + "\t" + style + "\t" + isMultiPart + "\t" + hasLoops + "\t" + (sumAscent / chMetricsCount) + "\t" + (sumDescent / chMetricsCount) + "\t" + (sumWidth / chMetricsCount) + "\t" + (sumHeight / chMetricsCount));
//		}
//		else System.out.println("   --> char signature: " + isMultiPart + "/" + hasLoops + "/" + (sumAscent / chMetricsCount) + "/" + (sumDescent / chMetricsCount) + "/" + (sumWidth / chMetricsCount) + "/" + (sumHeight / chMetricsCount));
//	}
	
	private static final byte NEVER = 0;
	private static final byte SOMETIMES = 1;
	private static final byte USUALLY = 2;
	private static final byte ALWAYS = 3;
//	private static String[] indicatorByteNames = {"NEVER", "SOMETIMES", "USUALLY", "ALWAYS"};
	
	private static final int charImageMargin = 5;
	
	private static void getCharMetrics(Font font, int fontStyle, Set charMetrics) throws Exception {
		System.out.println("Measuring font " + font.getName() + " (" + font.getFamily() + ") in " + fontStyle);
		
		//	create font and graphics
		Graphics2D gr = (new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY)).createGraphics();
		gr.setFont(font);
		
		//	measure font to determine maximum char image size
		Rectangle2D fontBox = font.getMaxCharBounds(gr.getFontRenderContext());
		System.out.println("Font box is " + fontBox);
		double maxWidth = 0;
		double maxHeight = 0;
		System.out.println("Measuring char bounds ...");
		for (int b = 0; b < unicodeCharBlocks.length; b++) {
			for (int ch = unicodeCharBlocks[b].lowChar; ch <= unicodeCharBlocks[b].highChar; ch++) {
				if (!font.canDisplay((char) ch))
					continue;
				if (ignoreCharSet.contains(new Integer(ch)))
					continue;
//				System.out.println("Measuring char '" + ((char) c) + "' (" + Integer.toString(c, 16) + ")");
				TextLayout tl = new TextLayout(("" + ((char) ch)), font, gr.getFontRenderContext());
//				System.out.println(" - baseline " + tl.getBaseline());
//				System.out.println(" - ascent " + tl.getAscent());
//				System.out.println(" - descent " + tl.getDescent());
				if (maxWidth < tl.getBounds().getWidth()) {
					maxWidth = tl.getBounds().getWidth();
					System.out.println(" ==> new max width is " + maxWidth + " for '" + ((char) ch) + "' (" + Integer.toString(ch, 16) + ")");
				}
				if (maxHeight < tl.getBounds().getHeight()) {
					maxHeight = tl.getBounds().getHeight();
					System.out.println(" ==> new max height is " + maxHeight + " for '" + ((char) ch) + "' (" + Integer.toString(ch, 16) + ")");
				}
			}
		}
		System.out.println("Max width is " + maxWidth);
		System.out.println("Max height is " + maxHeight);
		
		//	process chars
		for (int b = 0; b < unicodeCharBlocks.length; b++) {
			ImageDisplayDialog idd = null;
			System.out.print("Chars " + Integer.toString(unicodeCharBlocks[b].lowChar, 16) + "-" + Integer.toString(unicodeCharBlocks[b].highChar, 16) + " ");
			for (int ch = unicodeCharBlocks[b].lowChar; ch <= unicodeCharBlocks[b].highChar; ch++) {
				if (!font.canDisplay((char) ch))
					continue;
				if (ignoreCharSet.contains(new Integer(ch)))
					continue;
//				System.out.println("Measuring char '" + ((char) ch) + "' (" + Integer.toString(ch, 16) + ")");
				
				//	render char
//				BufferedImage cbi = new BufferedImage(((int) (Math.round(fontBox.getWidth()) + (charImageMargin * 2))), ((int) (Math.round(fontBox.getHeight()) + (charImageMargin * 2))), BufferedImage.TYPE_BYTE_GRAY);
				BufferedImage cbi = new BufferedImage(((int) (Math.round(maxWidth) + (charImageMargin * 2))), ((int) (Math.round(fontBox.getHeight()) + (charImageMargin * 2))), BufferedImage.TYPE_BYTE_GRAY);
				Graphics2D cgr = cbi.createGraphics();
				cgr.setColor(Color.WHITE);
				cgr.fillRect(0, 0, cbi.getWidth(), cbi.getHeight());
				cgr.setFont(font);
				cgr.setColor(Color.BLACK);
				TextLayout tl = new TextLayout(("" + ((char) ch)), font, gr.getFontRenderContext());
				int cbl = (Math.round(tl.getAscent()) + charImageMargin);
//				cgr.drawString(("" + ((char) ch)), ((int) ((Math.round(fontBox.getWidth() - tl.getBounds().getWidth()) + (charImageMargin * 2)) / 2)), cbl);
				cgr.drawString(("" + ((char) ch)), ((int) ((Math.round(maxWidth - tl.getBounds().getWidth()) + (charImageMargin * 2)) / 2)), cbl);
				
				//	measure char
				AnalysisImage cai = Imaging.wrapImage(cbi, null);
				byte[][] cBrightness = cai.getBrightness();
				ImagePartRectangle cbb = Imaging.getContentBox(cai);
				
				//	compute number of disjoint parts by coloring black regins
				int[][] brcs = Imaging.getRegionColoring(cai, ((byte) 80), true);
				int bMaxRegionColor = getMaxRegionColor(brcs);
				int[] brccs = getRegionColorCounts(brcs, bMaxRegionColor);
				for (int r = 1; r < brccs.length; r++) {
					if (brccs[r] < (measurementFontSize / 10))
						bMaxRegionColor--;
				}
				if (bMaxRegionColor == 0) {
//					System.out.println(" - nothing to work with");
					continue;
				}
				int disjointParts = bMaxRegionColor;
//				System.out.println(" - disjoint parts: " + disjointParts);
				
				//	compute number of loops by coloring white regions
				int[][] wrcs = Imaging.getRegionColoring(cai, ((byte) -80), true);
				int wMaxRegionColor = getMaxRegionColor(wrcs);
				int[] wrccs = getRegionColorCounts(wrcs, wMaxRegionColor);
				for (int r = 1; r < wrccs.length; r++) {
					if (wrccs[r] < (measurementFontSize / 10))
						wMaxRegionColor--;
				}
				int loops = (wMaxRegionColor - 1); // subtract one for outside
//				System.out.println(" - loops: " + loops);
//				System.out.println(" - rendered bounds: " + cbb.getId());
//				System.out.println(" - height above baseline: " + (cbl - cbb.getTopRow()));
//				if (cbb.getBottomRow() < cbl)
//					System.out.println(" - elevation above baseline: " + (cbl - cbb.getBottomRow()));
//				else System.out.println(" - extent below baseline: " + (cbb.getBottomRow() - cbl));
//				System.out.println(" - relative width: " + (((float) cbb.getWidth()) / maxWidth));
				
//				charMetrics.add(new CharMetrics(((char) c), font.getName(), font.getStyle(), disjointParts, loops, ((float) (((float) (cbl - cbb.getTopRow())) / fontBox.getHeight())), ((float) (((float) (cbb.getBottomRow() - cbl)) / fontBox.getHeight())), ((float) (((float) cbb.getWidth()) / maxWidth)), ((float) (((float) cbb.getHeight()) / maxHeight))));
				
				
				//	fill font box signature
				int[] fbSignatureColBounds = new int[charSignatureWidth+1];
				fbSignatureColBounds[0] = charImageMargin;
				for (int c = 1; c < charSignatureWidth; c++)
					fbSignatureColBounds[c] = (Math.round(((float) (c * (cbi.getWidth() - (charImageMargin * 2)))) / charSignatureWidth) + charImageMargin);
				fbSignatureColBounds[charSignatureWidth] = (cbi.getWidth() - charImageMargin);
				int[] fbSignatureRowBounds = new int[charSignatureHeight+1];
				fbSignatureRowBounds[0] = charImageMargin;
				for (int r = 1; r < charSignatureHeight; r++)
					fbSignatureRowBounds[r] = (Math.round(((float) (r * (cbi.getHeight() - (charImageMargin * 2)))) / charSignatureHeight) + charImageMargin);
				fbSignatureRowBounds[charSignatureHeight] = (cbi.getHeight() - charImageMargin);
				byte[][] fbSignature = new byte[charSignatureWidth][charSignatureHeight];
				for (int c = 0; c < charSignatureWidth; c++)
					for (int r = 0; r < charSignatureHeight; r++) {
						int pc = 0;
						int bpc = 0;
						for (int x = fbSignatureColBounds[c]; x < fbSignatureColBounds[c+1]; x++)
							for (int y = fbSignatureRowBounds[r]; y < fbSignatureRowBounds[r+1]; y++) {
								pc++;
								if (cBrightness[x][y] < 80)
									bpc++;
							}
						fbSignature[c][r] = ((byte) (((charSignatureMaxDensity * bpc) + (pc / 2)) / pc));
					}
				
				
				/* TODO re-add char box top and bottom relative to baseline:
				 * - compares faster than font box signature
				 * - cuts more slack than font box signature
				 * - should still filter quite an additional bit 
				 */
				
				//	compute relative top char boundary over baseline
				float rCbTop = (((float) ((cbl - charImageMargin) - (cbb.getTopRow() - charImageMargin))) / (cbl - charImageMargin));
				
				//	compute relative bottom char boundary over baseline
				float rCbBottom = (((float) ((cbl - charImageMargin) - (cbb.getBottomRow() - charImageMargin))) / (cbl - charImageMargin));
				
				//	compute char box proportion (2-log of height/width ==> positive corresponds to higher than wide, negative to wider than high)
				float cbPropotion = ((float) (Math.log(((double) cbb.getHeight()) / cbb.getWidth()) / Math.log(2)));
				
				//	fill char box signature
				int[] cbSignatureColBounds = new int[charSignatureWidth+1];
				cbSignatureColBounds[0] = cbb.getLeftCol();
				for (int c = 1; c < charSignatureWidth; c++)
					cbSignatureColBounds[c] = (Math.round(((float) (c * cbb.getWidth())) / charSignatureWidth) + cbb.getLeftCol());
				cbSignatureColBounds[charSignatureWidth] = cbb.getRightCol();
				int[] cbSignatureRowBounds = new int[charSignatureHeight+1];
				cbSignatureRowBounds[0] = cbb.getTopRow();
				for (int r = 1; r < charSignatureHeight; r++)
					cbSignatureRowBounds[r] = (Math.round(((float) (r * cbb.getHeight())) / charSignatureHeight) + cbb.getTopRow());
				cbSignatureRowBounds[charSignatureHeight] = cbb.getBottomRow();
				byte[][] cbSignature = new byte[charSignatureWidth][charSignatureHeight];
				for (int c = 0; c < charSignatureWidth; c++)
					for (int r = 0; r < charSignatureHeight; r++) {
						int pc = 0;
						int bpc = 0;
						for (int x = cbSignatureColBounds[c]; x < cbSignatureColBounds[c+1]; x++)
							for (int y = cbSignatureRowBounds[r]; y < cbSignatureRowBounds[r+1]; y++) {
								pc++;
								if (cBrightness[x][y] < 80)
									bpc++;
							}
						if (pc == 0)
							cbSignature[c][r] = -1;
						else cbSignature[c][r] = ((byte) (((charSignatureMaxDensity * bpc) + (pc / 2)) / pc));
					}
				
				//	for very small char boxes, we might have to interpolate a few columns or rows
				boolean cbCellLeftToFill;
				do {
					cbCellLeftToFill = false;
					for (int c = 0; c < charSignatureWidth; c++) {
						boolean cbColEmpty = true;
						for (int r = 0; r < charSignatureHeight; r++)
							if (cbSignature[c][r] != -1) {
								cbColEmpty = false;
								break;
							}
						if (cbColEmpty) {
							byte[] left = (((c == 0) || (cbSignature[c-1][0] == -1)) ? null : cbSignature[c-1]);
							byte[] right = ((((c+1) == cbSignature.length) || (cbSignature[c+1][0] == -1)) ? null : cbSignature[c+1]);
							if ((left == null) && (right == null))
								cbCellLeftToFill = true;
							else for (int r = 0; r < charSignatureHeight; r++) {
								if (left == null)
									cbSignature[c][r] = right[r];
								else if (right == null)
									cbSignature[c][r] = left[r];
								else cbSignature[c][r] = ((byte) ((left[r] + right[r] + 1) / 2));
							}
						}
						else for (int r = 0; r < charSignatureHeight; r++) {
							if (cbSignature[c][r] != -1)
								continue;
							byte above = ((r == 0) ? -1 : cbSignature[c][r-1]);
							byte below = (((r+1) == cbSignature[c].length) ? -1 : cbSignature[c][r+1]);
							if ((above == -1) && (below == -1))
								cbCellLeftToFill = true;
							else if (above == -1)
								cbSignature[c][r] = below;
							else if (below == -1)
								cbSignature[c][r] = above;
							else cbSignature[c][r] = ((byte) ((above + below + 1) / 2));
						}
					}
				} while (cbCellLeftToFill);
				
				charMetrics.add(new CharMetrics(((char) ch), font.getName(), fontStyle, disjointParts, loops, fbSignature, rCbTop, rCbBottom, cbPropotion, cbSignature));
				System.out.print(".");
				
				String ct;
				if (Character.isLetter((char) ch))
					ct = "Letter";
				else if (Character.isDigit((char) ch))
					ct = "Digit";
				else ct = "Other";
				
//				if (font.getStyle() != (Font.BOLD | Font.ITALIC))
//					continue;
//				
//				//	set up char for display
//				if (idd == null)
//					idd = new ImageDisplayDialog("Chars " + Integer.toString(unicodeCharBlocks[b].lowChar, 16) + "-" + Integer.toString(unicodeCharBlocks[b].highChar, 16));
//				idd.addImage(cbi, (("" + ((char) c)) + " (" + disjointParts + "/" + loops + ")"), (("" + ((char) c)) + " (" + ct + " " + Integer.toString(c, 16).toUpperCase() + " / " + disjointParts + " parts / " + loops + " loops / " + ((cbb.getWidth() * 100) / ((int) maxWidth)) + "% relative width / " + ((cbb.getHeight() * 100) / ((int) maxHeight)) + "% relative height / " + (cbl - cbb.getBottomRow()) + " shift off baseline )"));
			}
			System.out.println();
			if (idd != null) {
				idd.setSize(600, 400);
				idd.setVisible(true);
			}
		}
	}
	
	private static int getMaxRegionColor(int[][] rcs) {
		int maxRegionColor = 0;
		for (int c = 0; c < rcs.length; c++) {
			for (int r = 0; r < rcs[c].length; r++)
				maxRegionColor = Math.max(maxRegionColor, rcs[c][r]);
		}
		return maxRegionColor;
	}
	
	private static int[] getRegionColorCounts(int[][] rcs, int maxRegionColor) {
		int[] regionColorCounts = new int[maxRegionColor + 1];
		Arrays.fill(regionColorCounts, 0);
		for (int c = 0; c < rcs.length; c++) {
			for (int r = 0; r < rcs[c].length; r++)
				regionColorCounts[rcs[c][r]]++;
		}
		return regionColorCounts;
	}
	
	private static final int charSignatureWidth = 12;
	private static final int charSignatureHeight = 12;
	private static final byte charSignatureMaxDensity = ((byte) 0xF); // this way, we can use one hex digit per signature area
	private static class CharMetrics {
		char ch;
		String fontName;
		int fontStyle;
		int disjointParts;
		int loops;
		byte[][] fontBoxSignature;
		float relCharBoxTop;
		float relCharBoxBottom;
		float charBoxProportion;
		byte[][] charBoxSignature;
		CharMetrics(char ch, String fontName, int fontStyle, int disjointParts, int loops, byte[][] fontBoxSignature, float relCharBoxTop, float relCharBoxBottom, float charBoxProportion, byte[][] charBoxSignature) {
			this.ch = ch;
			this.fontName = fontName;
			this.fontStyle = fontStyle;
			this.disjointParts = disjointParts;
			this.loops = loops;
			this.fontBoxSignature = fontBoxSignature;
			this.relCharBoxTop = relCharBoxTop;
			this.relCharBoxBottom = relCharBoxBottom;
			this.charBoxProportion = charBoxProportion;
			this.charBoxSignature = charBoxSignature;
		}
		String getId() {
			return (this.ch + "-" + this.fontName + "-" + this.fontStyle);
		}
	}
//	
//	relative measurements don't work all that well, are not as distinctive as hoped for
//	private static class CharMetrics {
//		char ch;
//		String fontName;
//		int fontStyle;
//		int disjointParts;
//		int loops;
//		float rAscent;
//		float rDescent;
//		float rWidth;
//		float rHeight;
//		CharMetrics(char ch, String fontName, int fontStyle, int disjointParts, int loops, float rAscent, float rDescent, float rWidth, float rHeight) {
//			this.ch = ch;
//			this.fontName = fontName;
//			this.fontStyle = fontStyle;
//			this.disjointParts = disjointParts;
//			this.loops = loops;
//			this.rAscent = rAscent;
//			this.rDescent = rDescent;
//			this.rWidth = rWidth;
//			this.rHeight = rHeight;
//		}
//		String getId() {
//			return (this.ch + "-" + this.fontName + "-" + this.fontStyle);
//		}
//	}
	
	private static final String[] unicodeBlocks = {
//		"0000;007F;Latin, Common", // Basic Latin[g] 
		"0020;007E;Latin, Common", // Basic Latin[g] 
//		"0080;00FF;Latin, Common", // Latin-1 Supplement[h] (cut control block, which renders on Windows machines)
			"00A0;00FF;Latin, Common", // Latin-1 Supplement[h] 
		"0100;017F;Latin", // Latin Extended-A 
		"0180;024F;Latin", // Latin Extended-B 
		"0250;02AF;Latin", // IPA Extensions 
		"02B0;02FF;Latin, Common", // Spacing Modifier Letters 
		"0300;036F;Inherited", // Combining Diacritical Marks 
		"0370;03FF;Greek, Coptic, Common", // Greek and Coptic 
//		"0400;04FF;Cyrillic, Inherited", // Cyrillic 
//		"0500;052F;Cyrillic", // Cyrillic Supplement 
//		"0530;058F;Armenian, Common", // Armenian 
//		"0590;05FF;Hebrew", // Hebrew 
//		"0600;06FF;Arabic, Common, Inherited", // Arabic 
//		"0700;074F;Syriac", // Syriac 
//		"0750;077F;Arabic", // Arabic Supplement 
//		"0780;07BF;Thaana", // Thaana 
//		"07C0;07FF;Nko", // NKo 
//		"0800;083F;Samaritan", // Samaritan 
//		"0840;085F;Mandaic", // Mandaic 
//		"08A0;08FF;Arabic", // Arabic Extended-A 
//		"0900;097F;Devanagari, Common, Inherited", // Devanagari 
//		"0980;09FF;Bengali", // Bengali 
//		"0A00;0A7F;Gurmukhi", // Gurmukhi 
//		"0A80;0AFF;Gujarati", // Gujarati 
//		"0B00;0B7F;Oriya", // Oriya 
//		"0B80;0BFF;Tamil", // Tamil 
//		"0C00;0C7F;Telugu", // Telugu 
//		"0C80;0CFF;Kannada", // Kannada 
//		"0D00;0D7F;Malayalam", // Malayalam 
//		"0D80;0DFF;Sinhala", // Sinhala 
//		"0E00;0E7F;Thai, Common", // Thai 
//		"0E80;0EFF;Lao", // Lao 
//		"0F00;0FFF;Tibetan, Common", // Tibetan 
//		"1000;109F;Myanmar", // Myanmar 
//		"10A0;10FF;Georgian, Common", // Georgian 
//		"1100;11FF;Hangul", // Hangul Jamo 
//		"1200;137F;Ethiopic", // Ethiopic 
//		"1380;139F;Ethiopic", // Ethiopic Supplement 
//		"13A0;13FF;Cherokee", // Cherokee 
//		"1400;167F;Canadian Aboriginal", // Unified Canadian Aboriginal Syllabics 
//		"1680;169F;Ogham", // Ogham 
//		"16A0;16FF;Runic, Common", // Runic 
//		"1700;171F;Tagalog", // Tagalog 
//		"1720;173F;Hanunoo, Common", // Hanunoo 
//		"1740;175F;Buhid", // Buhid 
//		"1760;177F;Tagbanwa", // Tagbanwa 
//		"1780;17FF;Khmer", // Khmer 
//		"1800;18AF;Mongolian, Common", // Mongolian 
//		"18B0;18FF;Canadian Aboriginal", // Unified Canadian Aboriginal Syllabics Extended 
//		"1900;194F;Limbu", // Limbu 
//		"1950;197F;Tai Le", // Tai Le 
//		"1980;19DF;New Tai Lue", // New Tai Lue 
//		"19E0;19FF;Khmer", // Khmer Symbols 
//		"1A00;1A1F;Buginese", // Buginese 
//		"1A20;1AAF;Tai Tham", // Tai Tham 
//		"1B00;1B7F;Balinese", // Balinese 
//		"1B80;1BBF;Sundanese", // Sundanese 
//		"1BC0;1BFF;Batak", // Batak 
//		"1C00;1C4F;Lepcha", // Lepcha 
//		"1C50;1C7F;Ol Chiki", // Ol Chiki 
//		"1CC0;1CCF;Sundanese", // Sundanese Supplement 
//		"1CD0;1CFF;Common, Inherited", // Vedic Extensions 
//		"1D00;1D7F;Cyrillic, Greek, Latin", // Phonetic Extensions 
//		"1D80;1DBF;Latin, Greek", // Phonetic Extensions Supplement 
//		"1DC0;1DFF;Inherited", // Combining Diacritical Marks Supplement 
		"1E00;1EFF;Latin", // Latin Extended Additional 
		"1F00;1FFF;Greek", // Greek Extended 
//		"2000;206F;Common, Inherited", // General Punctuation (split to exclude per-tenthousand character (u2031))
			"2000;2030;Common, Inherited", // General Punctuation 
			"2032;206F;Common, Inherited", // General Punctuation 
		"2070;209F;Latin, Common", // Superscripts and Subscripts 
//		"20A0;20CF;Common", // Currency Symbols 
		"20D0;20FF;Inherited", // Combining Diacritical Marks for Symbols 
		"2100;214F;Latin, Greek, Common", // Letterlike Symbols
//		"2150;218F;Latin, Common", // Number Forms (split to exclude Roman numerals (u2160-u216F))
			"2150;215F;Latin, Common", // Number Forms 
			"2170;218F;Latin, Common", // Number Forms 
//		"2190;21FF;Common", // Arrows (split and cut to exclude extremely exotic arrows)
			"2190;2199;Common", // Arrows 
			"21D0;21D9;Common", // Arrows 
		"2200;22FF;Common", // Mathematical Operators 
		"2300;23FF;Common", // Miscellaneous Technical 
		"2400;243F;Common", // Control Pictures 
//		"2440;245F;Common", // Optical Character Recognition 
//		"2460;24FF;Common", // Enclosed Alphanumerics 
//		"2500;257F;Common", // Box Drawing 
//		"2580;259F;Common", // Block Elements 
//		"25A0;25FF;Common", // Geometric Shapes 
		"2600;26FF;Common", // Miscellaneous Symbols 
//		"2700;27BF;Common", // Dingbats 
		"27C0;27EF;Common", // Miscellaneous Mathematical Symbols-A 
		"27F0;27FF;Common", // Supplemental Arrows-A 
//		"2800;28FF;Braille", // Braille Patterns 
		"2900;297F;Common", // Supplemental Arrows-B 
		"2980;29FF;Common", // Miscellaneous Mathematical Symbols-B 
		"2A00;2AFF;Common", // Supplemental Mathematical Operators 
		"2B00;2BFF;Common", // Miscellaneous Symbols and Arrows 
//		"2C00;2C5F;Glagolitic", // Glagolitic 
		"2C60;2C7F;Latin", // Latin Extended-C 
//		"2C80;2CFF;Coptic", // Coptic 
//		"2D00;2D2F;Georgian", // Georgian Supplement 
//		"2D30;2D7F;Tifinagh", // Tifinagh 
//		"2D80;2DDF;Ethiopic", // Ethiopic Extended 
		"2DE0;2DFF;Cyrillic", // Cyrillic Extended-A 
//		"2E00;2E7F;Common", // Supplemental Punctuation 
//		"2E80;2EFF;Han", // CJK Radicals Supplement 
//		"2F00;2FDF;Han", // Kangxi Radicals 
//		"2FF0;2FFF;Common", // Ideographic Description Characters 
//		"3000;303F;Han, Hangul, Common, Inherited", // CJK Symbols and Punctuation 
//		"3040;309F;Hiragana, Common, Inherited", // Hiragana 
//		"30A0;30FF;Katakana, Common", // Katakana 
//		"3100;312F;Bopomofo", // Bopomofo 
//		"3130;318F;Hangul", // Hangul Compatibility Jamo 
//		"3190;319F;Common", // Kanbun 
//		"31A0;31BF;Bopomofo", // Bopomofo Extended 
//		"31C0;31EF;Common", // CJK Strokes 
//		"31F0;31FF;Katakana", // Katakana Phonetic Extensions 
//		"3200;32FF;Katakana, Hangul, Common", // Enclosed CJK Letters and Months 
//		"3300;33FF;Katakana, Common", // CJK Compatibility 
//		"3400;4DBF;Han", // CJK Unified Ideographs Extension A 
//		"4DC0;4DFF;Common", // Yijing Hexagram Symbols 
//		"4E00;9FFF;Han", // CJK Unified Ideographs 
//		"A000;A48F;Yi", // Yi Syllables 
//		"A490;A4CF;Yi", // Yi Radicals 
//		"A4D0;A4FF;Lisu", // Lisu 
//		"A500;A63F;Vai", // Vai 
		"A640;A69F;Cyrillic", // Cyrillic Extended-B 
//		"A6A0;A6FF;Bamum", // Bamum 
//		"A700;A71F;Common", // Modifier Tone Letters 
		"A720;A7FF;Latin, Common", // Latin Extended-D 
//		"A800;A82F;Syloti Nagri", // Syloti Nagri 
//		"A830;A83F;Common", // Common Indic Number Forms 
//		"A840;A87F;Phags Pa", // Phags-pa 
//		"A880;A8DF;Saurashtra", // Saurashtra 
//		"A8E0;A8FF;Devanagari", // Devanagari Extended 
//		"A900;A92F;Kayah Li", // Kayah Li 
//		"A930;A95F;Rejang", // Rejang 
//		"A960;A97F;Hangul", // Hangul Jamo Extended-A 
//		"A980;A9DF;Javanese", // Javanese 
//		"AA00;AA5F;Cham", // Cham 
//		"AA60;AA7F;Myanmar", // Myanmar Extended-A 
//		"AA80;AADF;Tai Viet", // Tai Viet 
//		"AAE0;AAFF;Meetei Mayek", // Meetei Mayek Extensions 
//		"AB00;AB2F;Ethiopic", // Ethiopic Extended-A 
//		"ABC0;ABFF;Meetei Mayek", // Meetei Mayek 
//		"AC00;D7AF;Hangul", // Hangul Syllables 
//		"D7B0;D7FF;Hangul", // Hangul Jamo Extended-B 
//		"D800;DB7F;", // High Surrogates 
//		"DB80;DBFF;", // High Private Use Surrogates 
//		"DC00;DFFF;", // Low Surrogates 
//		"E000;F8FF;", // Private Use Area 
//		"F900;FAFF;Han", // CJK Compatibility Ideographs 
		"FB00;FB4F;Latin, Hebrew, Armenian", // Alphabetic Presentation Forms 
//		"FB50;FDFF;Arabic, Common", // Arabic Presentation Forms-A 
//		"FE00;FE0F;Inherited", // Variation Selectors 
//		"FE10;FE1F;Common", // Vertical Forms 
//		"FE20;FE2F;Inherited", // Combining Half Marks 
//		"FE30;FE4F;Common", // CJK Compatibility Forms 
//		"FE50;FE6F;Common", // Small Form Variants 
//		"FE70;FEFF;Arabic, Common", // Arabic Presentation Forms-B 
//		"FF00;FFEF;Latin, Katakana, Hangul, Common", // Halfwidth and fullwidth forms 
		"FFF0;FFFF;Common", // Specials 
	};
	
	private static CharBlock[] unicodeCharBlocks = null;
	static {
		
		//	set up cache for character ranges in each script
		LinkedList charBlocks = new LinkedList();
		for (int b = 0; b < unicodeBlocks.length; b++) {
			String[] blockData = unicodeBlocks[b].split("\\;");
			int l = Integer.parseInt(blockData[0], 16);
			int h = Integer.parseInt(blockData[1], 16);
			String[] scripts = blockData[2].split("\\s*\\,\\s*");
			charBlocks.add(new CharBlock(l, h, scripts));
		}
		unicodeCharBlocks = ((CharBlock[]) charBlocks.toArray(new CharBlock[charBlocks.size()]));
	}
	
	private static class CharBlock {
		int lowChar;
		int highChar;
		HashSet scripts = new HashSet();
		CharBlock(int lowChar, int highChar, String[] scripts) {
			this.lowChar = lowChar;
			this.highChar = highChar;
			this.scripts.addAll(Arrays.asList(scripts));
		}
	}
	
	private static Integer[] ignoreChars = {
		new Integer(Integer.parseInt("2100", 16)),
		new Integer(Integer.parseInt("2101", 16)),
		new Integer(Integer.parseInt("2105", 16)),
		new Integer(Integer.parseInt("2106", 16)),
		new Integer(Integer.parseInt("2120", 16)),
		new Integer(Integer.parseInt("2121", 16)),
		new Integer(Integer.parseInt("213a", 16)),
		new Integer(Integer.parseInt("213b", 16)),
		new Integer(Integer.parseInt("214d", 16)),
		new Integer(Integer.parseInt("22d8", 16)),
		new Integer(Integer.parseInt("22d9", 16)),
	};
	private static Set ignoreCharSet = new HashSet(Arrays.asList(ignoreChars));
	
	private static String[] fontNames = {
		"Agency FB",
		"Arial",
		"Arial Narrow",
		"Arial Rounded MT Bold",
//		"Baskerville Old Face",
		"Bell MT",
		"Berlin Sans FB",
		"Bernard MT Condensed",
		"Bodoni MT",
		"Bodoni MT Condensed",
		"Book Antiqua",
		"Bookman Old Style",
//		"Britannic Bold",
		"Broadway",
		"Calibri",
		"Californian FB",
		"Calisto MT",
		"Cambria",
		"Candara",
		"Centaur",
		"Century",
		"Century Gothic",
		"Century Schoolbook",
		"Consolas",
		"Constantia",
		"Cooper Black",
		"Corbel",
		"Dotum",
		"Elephant",
		"Eras Bold ITC",
		"Eras Demi ITC",
		"Eras Light ITC",
		"Eras Medium ITC",
		"Footlight MT Light",
		"Franklin Gothic Book",
		"Franklin Gothic Demi",
		"Franklin Gothic Heavy",
		"Franklin Gothic Medium",
		"Franklin Gothic Medium Cond",
		"Garamond",
		"Georgia",
		"Gill Sans MT",
		"Gill Sans MT Condensed",
		"Gloucester MT Extra Condensed",
		"Goudy Old Style",
		"Haettenschweiler",
		"High Tower Text",
		"Impact",
		"Latin Wide",
		"Lucida Bright",
		"Lucida Fax",
		"Lucida Sans",
		"Lucida Sans Unicode",
		"Maiandra GD",
		"Modern No. 20",
		"Niagara Solid",
		"Onyx",
		"Palatino Linotype",
		"Perpetua",
		"Rockwell",
		"Rockwell Condensed",
		"SansSerif",
		"Segoe UI",
		"Segoe UI Semibold",
		"Segoe UI Symbol",
		"Serif",
		"Sylfaen",
		"Tahoma",
		"Times New Roman",
		"Trebuchet MS",
		"Tw Cen MT",
		"Tw Cen MT Condensed",
		"Tw Cen MT Condensed Extra Bold",
		"Verdana",
	};
	private static final String[] serifFontNames = {
//		"Agency FB",
//		"Arial",
//		"Arial Narrow",
//		"Arial Rounded MT Bold",
//		"Baskerville Old Face",
		"Bell MT",
//		"Berlin Sans FB",
		"Bernard MT Condensed",
		"Bodoni MT",
		"Bodoni MT Condensed",
		"Book Antiqua",
		"Bookman Old Style",
//		"Britannic Bold",
//		"Broadway",
//		"Calibri",
		"Californian FB",
		"Calisto MT",
		"Cambria",
//		"Candara",
		"Centaur",
		"Century",
//		"Century Gothic",
		"Century Schoolbook",
//		"Consolas",
		"Constantia",
		"Cooper Black",
//		"Corbel",
//		"Dotum",
		"Elephant",
//		"Eras Bold ITC",
//		"Eras Demi ITC",
//		"Eras Light ITC",
//		"Eras Medium ITC",
		"Footlight MT Light",
//		"Franklin Gothic Book",
//		"Franklin Gothic Demi",
//		"Franklin Gothic Heavy",
//		"Franklin Gothic Medium",
//		"Franklin Gothic Medium Cond",
		"Garamond",
		"Georgia",
//		"Gill Sans MT",
//		"Gill Sans MT Condensed",
		"Gloucester MT Extra Condensed",
		"Goudy Old Style",
//		"Haettenschweiler",
		"High Tower Text",
//		"Impact",
		"Latin Wide",
		"Lucida Bright",
		"Lucida Fax",
//		"Lucida Sans",
//		"Lucida Sans Unicode",
//		"Maiandra GD",
		"Modern No. 20",
		"Niagara Solid",
		"Onyx",
		"Palatino Linotype",
		"Perpetua",
		"Rockwell",
		"Rockwell Condensed",
//		"SansSerif",
//		"Segoe UI",
//		"Segoe UI Semibold",
//		"Segoe UI Symbol",
		"Serif",
		"Sylfaen",
//		"Tahoma",
		"Times New Roman",
//		"Trebuchet MS",
//		"Tw Cen MT",
//		"Tw Cen MT Condensed",
//		"Tw Cen MT Condensed Extra Bold",
//		"Verdana",
	};
	private static final HashSet serifFonts = new HashSet(Arrays.asList(serifFontNames));
	
	private static class ImageDisplayDialog extends JPanel {
		private JDialog dialog;
		private JTabbedPane tabs = new JTabbedPane();
		public ImageDisplayDialog(String title) {
			this.dialog = DialogFactory.produceDialog(title, true);
			this.dialog.getContentPane().setLayout(new BorderLayout());
			this.dialog.getContentPane().add(this.tabs, BorderLayout.CENTER);
			this.tabs.setTabPlacement(JTabbedPane.LEFT);
		}
		public void addImage(final BufferedImage image, String title, String tooltip) {
			JPanel imagePanel = new JPanel() {
				public void paintComponent(Graphics graphics) {
					super.paintComponent(graphics);
					graphics.drawImage(image, 0, 0, this.getWidth(), this.getHeight(), this);
				}
			};
			imagePanel.setPreferredSize(new Dimension(image.getWidth(), image.getHeight()));
			JPanel imageTray = new JPanel(new GridBagLayout());
			imageTray.add(imagePanel, new GridBagConstraints());
			JScrollPane imageBox = new JScrollPane(imageTray);
			imageBox.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
			imageBox.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
			this.tabs.addTab(title, null, imageBox, tooltip);
			this.tabs.setSelectedComponent(imageBox);
		}
		public void setVisible(boolean visible) {
			super.setVisible(visible);
			this.dialog.setVisible(visible);
		}
		public void setSize(int width, int height) {
			this.dialog.setSize(width, height);
		}
		public void setLocationRelativeTo(Component comp) {
			this.dialog.setLocationRelativeTo(comp);
		}
	}
}