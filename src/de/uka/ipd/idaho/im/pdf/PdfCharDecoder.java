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
package de.uka.ipd.idaho.im.pdf;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JOptionPane;

import de.uka.ipd.idaho.im.analysis.Imaging;
import de.uka.ipd.idaho.im.analysis.Imaging.AnalysisImage;
import de.uka.ipd.idaho.im.analysis.Imaging.ImagePartRectangle;
import de.uka.ipd.idaho.im.pdf.PdfFontDecoder.FontDecoderCharset;
import de.uka.ipd.idaho.im.pdf.PdfParser.PStream;
import de.uka.ipd.idaho.im.pdf.PdfUtils.PdfLineInputStream;
import de.uka.ipd.idaho.im.util.ImFontUtils;
import de.uka.ipd.idaho.im.utilities.ImageDisplayDialog;
import de.uka.ipd.idaho.stringUtils.StringUtils;

/**
 * @author sautter
 *
 */
public class PdfCharDecoder {
	
	/* make sure we have the fonts we need */
	static {
		ImFontUtils.loadFreeFonts();
	}
	
	//	TODO try and cluster characters by similarity
	//	==> good match yields suggestions for further match attempts
	
	//	TODO also try using Tesseract on char images
	
	/* TODO use glyph outlines in char signatures:
	 * - record number and direction of Bezier curves and lines during rendering
	 * - also record number of jumps after rendering first path part
	 * - learn numbers for individual characters across fonts from many PDFs
	 * - include in char signatures 
	 */
	
	static final int SERIF = 4;
	static final int MONOSPACED = 8;
	private static final boolean SERIF_IS_STYLE = false;
	
	private static final String[] styleNames4 = {"Plain", "Bold", "Italics", "BoldItalics"};
	private static final String[] styleNames8 = {"Sans-Plain", "Sans-Bold", "Sans-Italics", "Sans-BoldItalics", "Serif-Plain", "Serif-Bold", "Serif-Italics", "Serif-BoldItalics"};
	private static final String[] styleNames = (SERIF_IS_STYLE ? styleNames8 : styleNames4);
	
	//	!!! TEST ONLY !!!
	public static void main(String[] args) {
		ImageDisplayDialog unMatchedCharImages = null;
		for (int ufs = 0; ufs <=1; ufs++)
			for (int s = 0; s <= ((Font.BOLD | Font.ITALIC) | (SERIF_IS_STYLE ? SERIF : 0)); s++) {
//				if (s < SERIF)
//					continue;
				unMatchedCharImages = testCharMatchPos(Integer.MAX_VALUE/*0x03FF*/, s, (ufs == 1), unMatchedCharImages);
//				break;
			}
//		testCharMatchDiffs(Integer.MAX_VALUE/*0x03FF*/);
		if (unMatchedCharImages != null) {
			unMatchedCharImages.setSize(600, 400);
			unMatchedCharImages.setVisible(true);
		}
	}
	
	private static void testCharMatchDiffs(int charLimit) {
		for (int s = 0; s <= ((Font.BOLD | Font.ITALIC) | (SERIF_IS_STYLE ? SERIF : 0)); s++) {
//			Font font = new Font((((s & SERIF) != 0) ? "Serif" : "Sans"), (s & (Font.BOLD | Font.ITALIC)), 96);
			Font font = new Font((((s & SERIF) != 0) ? "FreeSerif" : "FreeSans"), (s & (Font.BOLD | Font.ITALIC)), 96);
			Graphics2D gr = (new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY)).createGraphics();
			gr.setFont(font);
			Rectangle2D fontBox = font.getMaxCharBounds(gr.getFontRenderContext());
			float sMaxMatchDiff = 0;
			float sMatchDiffSum = 0;
			float nsMaxMatchDiff = 0;
			float nsMatchDiffSum = 0;
			int testCharCount = 0;
//			int nonMatchCharCount = 0;
			TreeSet sCharMatches = new TreeSet();
			TreeSet nsCharMatches = new TreeSet();
			for (int t = 0; t < Math.min(charLimit, charSignatures.length); t++) {
				if (charSignatures[t].ch > charLimit)
					break;
				char ch = charSignatures[t].ch;
				if (!font.canDisplay(charSignatures[t].ch)) {
					System.out.println(((ch == '"') ? "=\"\"\"\"" : ("=\"" + ch + "\"")) + "\t=\"" + Integer.toString(((int) ch), 16) + "\"");
					continue;
				}
				BufferedImage cbi = new BufferedImage(((int) (Math.round(fontBox.getWidth()) + 2)), ((int) (Math.round(fontBox.getHeight()) + 2)), BufferedImage.TYPE_BYTE_GRAY);
				Graphics2D cgr = cbi.createGraphics();
				cgr.setColor(Color.WHITE);
				cgr.fillRect(0, 0, cbi.getWidth(), cbi.getHeight());
				cgr.setFont(font);
				cgr.setColor(Color.BLACK);
				TextLayout tl = new TextLayout(("" + ch), font, gr.getFontRenderContext());
				int cbl = (Math.round(tl.getAscent()) + 1);
				cgr.drawString(("" + ch), ((int) ((Math.round(fontBox.getWidth() - tl.getBounds().getWidth()) + 2) / 2)), cbl);
				cgr.dispose();
				
				//	compute char signature and difference
				CharMetrics cm = getCharMetrics(cbi, 1, fontBox, cbl);
				if (cm == null)
					continue;
				LinkedHashMap sDiffDetails = new LinkedHashMap();
				float sDiff = charSignatures[t].getDifference(s, (cm.disjointParts > 1), (cm.loops > 0), cm.fontBoxSignature, cm.relCharBoxTop, cm.relCharBoxBottom, cm.charBoxProportion, cm.charBoxSignature, false, ((char) 0), sDiffDetails);
				LinkedHashMap nsDiffDetails = new LinkedHashMap();
				float nsDiff = charSignatures[t].getDifference(-1, (cm.disjointParts > 1), (cm.loops > 0), cm.fontBoxSignature, cm.relCharBoxTop, cm.relCharBoxBottom, cm.charBoxProportion, cm.charBoxSignature, false, ((char) 0), nsDiffDetails);
				
				if (testCharCount == 0) {
					System.out.println(styleNames[s]);
					System.out.print("char\thex");
					for (Iterator dkit = nsDiffDetails.keySet().iterator(); dkit.hasNext();) {
						String diffKey = ((String) dkit.next());
						if ("fbd;cbp;cbd".indexOf(diffKey) == -1)
							System.out.print("\tsa." + diffKey);
					}
					System.out.print("\t\tchar\thex");
					for (Iterator dkit = sDiffDetails.keySet().iterator(); dkit.hasNext();) {
						String diffKey = ((String) dkit.next());
						if ("fbd;cbp;cbd".indexOf(diffKey) == -1)
							System.out.print("\tsa." + diffKey);
					}
					System.out.println();
				}
				System.out.print(((ch == '"') ? "=\"\"\"\"" : ("=\"" + ch + "\"")) + "\t=\"" + Integer.toString(((int) ch), 16) + "\"");
				for (Iterator dkit = nsDiffDetails.keySet().iterator(); dkit.hasNext();) {
					String diffKey = ((String) dkit.next());
					if ("fbd;cbp;cbd".indexOf(diffKey) == -1)
						System.out.print("\t" + nsDiffDetails.get(diffKey).toString().replaceAll("\\.", ","));
				}
				System.out.print("\t\t" + ((ch == '"') ? "=\"\"\"\"" : ("=\"" + ch + "\"")) + "\t=\"" + Integer.toString(((int) ch), 16) + "\"");
				for (Iterator dkit = sDiffDetails.keySet().iterator(); dkit.hasNext();) {
					String diffKey = ((String) dkit.next());
					if ("fbd;cbp;cbd".indexOf(diffKey) == -1)
						System.out.print("\t" + sDiffDetails.get(diffKey).toString().replaceAll("\\.", ","));
				}
				System.out.println();
				
				//	keep statistics
				testCharCount++;
				sMaxMatchDiff = Math.max(sMaxMatchDiff, sDiff);
				sMatchDiffSum += sDiff;
				sCharMatches.add(new CharMatch(ch, -1, sDiff, null));
				nsMaxMatchDiff = Math.max(nsMaxMatchDiff, nsDiff);
				nsMatchDiffSum += nsDiff;
				nsCharMatches.add(new CharMatch(ch, -1, nsDiff, null));
			}
			System.out.println();
			System.out.println();
//			System.out.println("Result for " + font.getName() + "-" + font.getStyle() + ", " + testCharCount + " chars:");
//			System.out.println(" - style aware: " + (sMatchDiffSum / testCharCount) + " on average, " + sMaxMatchDiff + " at worst");
//			System.out.println(" - style aware matches with extreme difference");
//			for (Iterator cmit = sCharMatches.iterator(); cmit.hasNext();) {
//				CharMatch cm = ((CharMatch) cmit.next());
//				if (cm.matchDiff < (sMatchDiffSum / testCharCount))
//					continue;
//				System.out.println("   - " + cm.toString());
//			}
//			System.out.println(" - style agnostic: " + (nsMatchDiffSum / testCharCount) + " on average, " + nsMaxMatchDiff + " at worst");
//			System.out.println(" - style agnostic matches with extreme difference");
//			for (Iterator cmit = nsCharMatches.iterator(); cmit.hasNext();) {
//				CharMatch cm = ((CharMatch) cmit.next());
//				if (cm.matchDiff < (nsMatchDiffSum / testCharCount))
//					continue;
//				System.out.println("   - " + cm.toString());
//			}
		}
	}
	
	//	!!! TEST ONLY !!!
	private static ImageDisplayDialog testCharMatchPos(int charLimit, int fontStyle, boolean useFontStyle, ImageDisplayDialog unMatchedCharImages) {
		float matchQuotientThreshold = 3;
//		Font font = new Font((((fontStyle & SERIF) != 0) ? "Serif" : "Sans"), (fontStyle & (Font.BOLD | Font.ITALIC)), 96);
		Font font = new Font((((fontStyle & SERIF) != 0) ? "FreeSerif" : "FreeSans"), (fontStyle & (Font.BOLD | Font.ITALIC)), 96);
		Graphics2D gr = (new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY)).createGraphics();
		gr.setFont(font);
		Rectangle2D fontBox = font.getMaxCharBounds(gr.getFontRenderContext());
		int testCharCount = 0;
		int misMatchCharCountSum = 0;
		int maxMatchPos = 0;
		int matchPosSum = 0;
		float maxMatchDiff = 0;
		float matchDiffSum = 0;
		int[] efforts = {0, 0, 0, 0, 0, 0, 0, 0};
//		int nonMatchCharCount = 0;
		TreeSet charMatches = new TreeSet();
		TreeSet unMatchedChars = new TreeSet();
		for (int t = 0; t < Math.min(charLimit, charSignatures.length); t++) {
			if (charSignatures[t].ch > charLimit)
				break;
			if (!font.canDisplay(charSignatures[t].ch))
				continue;
			
			char ch = charSignatures[t].ch;
			BufferedImage cbi = new BufferedImage(((int) (Math.round(fontBox.getWidth()) + 2)), ((int) (Math.round(fontBox.getHeight()) + 2)), BufferedImage.TYPE_BYTE_GRAY);
			Graphics2D cgr = cbi.createGraphics();
			cgr.setColor(Color.WHITE);
			cgr.fillRect(0, 0, cbi.getWidth(), cbi.getHeight());
			cgr.setFont(font);
			cgr.setColor(Color.BLACK);
			TextLayout tl = new TextLayout(("" + ch), font, cgr.getFontRenderContext());
			int cbl = (Math.round(tl.getAscent()) + 1);
			cgr.drawString(("" + ch), ((int) ((Math.round(fontBox.getWidth() - tl.getBounds().getWidth()) + 2) / 2)), cbl);
			cgr.dispose();
			
			//	compute char signature
			CharMetrics cm = getCharMetrics(cbi, 1, fontBox, cbl);
			if (cm == null)
				continue;
			
			//	search match and check how fast it is
			System.out.println("Actual char is '" + ch + "' (" + Integer.toString(((int) ch), 16) + "):");
			SortedSet scss = getScoredCharSignatures(cm, (useFontStyle ? fontStyle : -1), null, true, ((char) 0), efforts);
			int nonMatchChars = 0;
			int afterMatchChars = 0;
			TreeSet nonMatches = new TreeSet(scoredCharSignatureOrder);
			float bestMatchDiff = -1; 
			for (Iterator cit = scss.iterator(); cit.hasNext();) {
				ScoredCharSignature scs = ((ScoredCharSignature) cit.next());
				if (bestMatchDiff < 0)
					bestMatchDiff = scs.difference;
//				System.out.println(" - " + nonMatchChars + ": '" + scs.cs.ch + "' (" + Integer.toString(((int) scs.cs.ch), 16) + ") " + scs.difference);
//				scs.cs.getDifference((useFontStyle ? fontStyle : -1), (cm.disjointParts > 1), (cm.loops > 0), cm.fontBoxSignature, cm.charBoxProportion, cm.charBoxSignature, true, null);
				System.out.println(" - " + nonMatchChars + ": " + scs.toString());
				if (scs.cs.ch == ch) {
					maxMatchDiff = Math.max(maxMatchDiff, scs.difference);
					matchDiffSum += scs.difference;
					maxMatchPos = Math.max(maxMatchPos, nonMatchChars);
					matchPosSum += nonMatchChars;
					CharMatch cmc = new CharMatch(ch, nonMatchChars, scs.difference, nonMatches);
					charMatches.add(cmc);
					while (cit.hasNext()) {
						ScoredCharSignature amScs = ((ScoredCharSignature) cit.next());
						if (amScs.difference < (Math.max(bestMatchDiff, 1) * matchQuotientThreshold))
							afterMatchChars++;
						else break;
					}
					System.out.println(" --> best match quotient is " + (scs.difference / Math.max(bestMatchDiff, 1)));
					System.out.println("     after match in range are " + afterMatchChars);
					break;
				}
				nonMatchChars++;
				nonMatches.add(scs);
				if (scs.difference > 1000) {
//					nonMatchCharCount++;
					unMatchedChars.add(new Character(ch));
					while (cit.hasNext()) {
						scs = ((ScoredCharSignature) cit.next());
						if (scs.cs.ch == ch) {
							System.out.println(" --> " + nonMatchChars + ": " + scs.toString());
							System.out.println("     " + (cm.disjointParts > 1) + ", " + (cm.loops > 0) + ", " + cm.charBoxProportion + ", " + Arrays.toString(cm.fontBoxSignature) + ", " + Arrays.toString(cm.charBoxSignature));
							System.out.println("     " + ((useFontStyle && (scs.cs.sData[fontStyle] != null)) ? scs.cs.sData[fontStyle] : scs.cs.data));
							if (unMatchedCharImages == null)
								unMatchedCharImages = new ImageDisplayDialog("Un-Matched Chars");
							unMatchedCharImages.addImage(cbi, ("'" + ch + "' (" + Integer.toString(((int) ch), 16) + ") in " + styleNames[fontStyle] + " " + (useFontStyle ? "SS" : "SA")));
							break;
						}
						else {
//							System.out.println(" -- " + nonMatchChars + ": " + scs.toString());
							nonMatchChars++;
						}
					}
					break;
				}
			}
			testCharCount++;
			misMatchCharCountSum += (nonMatchChars + afterMatchChars);
			scss.clear();
		}
		System.out.println("Result from " + testCharCount + " chars in " + styleNames[fontStyle] + " " + (useFontStyle ? "SS" : "SA") + ":");
		System.out.println(" - " + (misMatchCharCountSum / testCharCount) + " wrong match attempts on average");
//		System.out.println(" - " + nonMatchCharCount + " failed to match at all");
		System.out.println(" - filtering effort: " + efforts[0] + " MBD (" + ((efforts[0] * 100) / (testCharCount * charSignatures.length)) + "%), " + efforts[1] + " LOD (" + ((efforts[1] * 100) / (testCharCount * charSignatures.length)) + "%), " + efforts[2] + " CBT (" + ((efforts[2] * 100) / (testCharCount * charSignatures.length)) + "%), " + efforts[3] + " CBB (" + ((efforts[3] * 100) / (testCharCount * charSignatures.length)) + "%), " + efforts[4] + " CBP (" + ((efforts[4] * 100) / (testCharCount * charSignatures.length)) + "%), " + efforts[5] + " FBD (" + ((efforts[5] * 100) / (testCharCount * charSignatures.length)) + "%), " + efforts[6] + " CBD (" + ((efforts[6] * 100) / (testCharCount * charSignatures.length)) + "%)");
		System.out.println(" - chars to compare: " + efforts[5] + " (" + ((efforts[5] * 100) / (testCharCount * charSignatures.length)) + "%)");
		System.out.println(" - maximum match difference is " + maxMatchDiff + ", average is " + (matchDiffSum / testCharCount));
		System.out.println(" - maximum match position is " + maxMatchPos + ", average is " + (matchPosSum / testCharCount));
		System.out.println(" - " + unMatchedChars.size() + " failed to match at all");
		for (Iterator nmcit = unMatchedChars.iterator(); nmcit.hasNext();) {
			Character nmc = ((Character) nmcit.next());
			System.out.println("   - '" + nmc + "' (" + Integer.toString(((int) nmc.charValue()), 16) + ")");
		}
		System.out.println(" - matches in order of difference");
		float maxBestMatchQuotient = 0;
		for (Iterator cmit = charMatches.iterator(); cmit.hasNext();) {
			CharMatch cm = ((CharMatch) cmit.next());
			maxBestMatchQuotient = Math.max(maxBestMatchQuotient, cm.getBestMatchQuotient());
//			if (cm.matchDiff < (matchDiffSum / testCharCount))
//				continue;
//			System.out.println("   - " + cm.toString());
		}
		System.out.println(" - maximum best match quotient is " + maxBestMatchQuotient);
		System.out.println();
		System.out.println();
		System.out.println();
		System.out.println();
		System.out.println();
		return unMatchedCharImages;
	}
	
	static class CharMatch implements Comparable {
		char ch;
		int matchPos;
		float matchDiff;
		TreeSet misMatches;
		CharMatch(char ch, int matchPos, float matchDiff, TreeSet misMatches) {
			this.ch = ch;
			this.matchPos = matchPos;
			this.matchDiff = matchDiff;
			this.misMatches = misMatches;
		}
		public int compareTo(Object obj) {
			CharMatch cm = ((CharMatch) obj);
			int c = Float.compare(this.matchDiff, cm.matchDiff);
			if (c != 0)
				return c;
			c = (this.matchPos - cm.matchPos);
			if (c != 0)
				return c;
			return (this.ch - cm.ch);
		}
		public String toString() {
			StringBuffer sb = new StringBuffer("'" + this.ch + "' (" + Integer.toString(((int) this.ch), 16) + ") at " + this.matchPos + " with " + this.matchDiff);
			if ((this.misMatches != null) && (this.misMatches.size() != 0)) {
				ScoredCharSignature bestScs = ((ScoredCharSignature) this.misMatches.first());
				sb.append(", quotient " + (this.matchDiff / bestScs.difference));
				sb.append(", after " + this.misMatches.toString());
			}
			else sb.append(", quotient 1.0");
			return sb.toString();
		}
		float getBestMatchQuotient() {
			if ((this.misMatches == null) || this.misMatches.isEmpty())
				return 1.0f;
			else {
				ScoredCharSignature bestScs = ((ScoredCharSignature) this.misMatches.first());
//				return (this.matchDiff / bestScs.difference);
				return (this.matchDiff / Math.max(bestScs.difference, 1));
			}
		}
	}
	
	static class CharMetrics {
		int disjointParts;
		int loops;
		byte[][] fontBoxSignature;
		float relCharBoxTop;
		float relCharBoxBottom;
		float charBoxProportion;
		byte[][] charBoxSignature;
		CharMetrics(int disjointParts, int loops, byte[][] fontBoxSignature, float relCharBoxTop, float relCharBoxBottom, float charBoxProportion, byte[][] charBoxSignature) {
			this.disjointParts = disjointParts;
			this.loops = loops;
			this.fontBoxSignature = fontBoxSignature;
			this.relCharBoxTop = relCharBoxTop;
			this.relCharBoxBottom = relCharBoxBottom;
			this.charBoxProportion = charBoxProportion;
			this.charBoxSignature = charBoxSignature;
		}
	}
	
	static CharMetrics getCharMetrics(BufferedImage cbi, int cim) {
		return getCharMetrics(cbi, cim, null, -1, cbi.getWidth(), cbi.getHeight());
	}
	
	static CharMetrics getCharMetrics(BufferedImage cbi, int cim, Rectangle2D fontBox, int cbl) {
		return getCharMetrics(cbi, cim, fontBox, cbl, ((int) Math.round(fontBox.getWidth())), ((int) Math.round(fontBox.getHeight())));
	}
	
	private static CharMetrics getCharMetrics(BufferedImage cbi, int cim, Rectangle2D fontBox, int cbl, int maxHeight, int maxWidth) {
		
		//	measure char
		AnalysisImage cai = Imaging.wrapImage(cbi, null);
		byte[][] cBrightness = cai.getBrightness();
		ImagePartRectangle cbb = Imaging.getContentBox(cai);
		
		//	compute number of disjoint parts by coloring black regins
		int[][] brcs = Imaging.getRegionColoring(cai, ((byte) 80), true);
		int bMaxRegionColor = getMaxRegionColor(brcs);
		int[] brccs = getRegionColorCounts(brcs, bMaxRegionColor);
		for (int r = 1; r < brccs.length; r++) {
			if (brccs[r] < (cbi.getHeight() / 10))
				bMaxRegionColor--;
		}
		if (bMaxRegionColor == 0)
			return null;
		int disjointParts = bMaxRegionColor;
//		System.out.println(" - disjoint parts: " + disjointParts);
		
		//	compute number of loops by coloring white regions
		int[][] wrcs = Imaging.getRegionColoring(cai, ((byte) -80), true);
		int wMaxRegionColor = getMaxRegionColor(wrcs);
		int[] wrccs = getRegionColorCounts(wrcs, wMaxRegionColor);
		for (int r = 1; r < wrccs.length; r++) {
			if (wrccs[r] < (cbi.getHeight() / 10))
				wMaxRegionColor--;
		}
		int loops = (wMaxRegionColor - 1); // subtract one for outside
//		System.out.println(" - loops: " + loops);
//		System.out.println(" - rendered bounds: " + cbb.getId());
//		System.out.println(" - height above baseline: " + (cbl - cbb.getTopRow()));
//		if (cbb.getBottomRow() < cbl)
//			System.out.println(" - elevation above baseline: " + (cbl - cbb.getBottomRow()));
//		else System.out.println(" - extent below baseline: " + (cbb.getBottomRow() - cbl));
//		System.out.println(" - relative width: " + (((float) cbb.getWidth()) / maxWidth));
		
//		charMetrics.add(new CharMetrics(((char) c), font.getName(), font.getStyle(), disjointParts, loops, ((float) (((float) (cbl - cbb.getTopRow())) / fontBox.getHeight())), ((float) (((float) (cbb.getBottomRow() - cbl)) / fontBox.getHeight())), ((float) (((float) cbb.getWidth()) / maxWidth)), ((float) (((float) cbb.getHeight()) / maxHeight))));
		
		
		//	fill font box signature (if we have a font box ...)
		byte[][] fbSignature = null;
		if (fontBox != null) {
			int[] fbSignatureColBounds = new int[charSignatureWidth+1];
			fbSignatureColBounds[0] = cim;
			for (int c = 1; c < charSignatureWidth; c++)
				fbSignatureColBounds[c] = (Math.round(((float) (c * (cbi.getWidth() - (cim * 2)))) / charSignatureWidth) + cim);
			fbSignatureColBounds[charSignatureWidth] = (cbi.getWidth() - cim);
			int[] fbSignatureRowBounds = new int[charSignatureHeight+1];
			fbSignatureRowBounds[0] = cim;
			for (int r = 1; r < charSignatureHeight; r++)
				fbSignatureRowBounds[r] = (Math.round(((float) (r * (cbi.getHeight() - (cim * 2)))) / charSignatureHeight) + cim);
			fbSignatureRowBounds[charSignatureHeight] = (cbi.getHeight() - cim);
			fbSignature = new byte[charSignatureWidth][charSignatureHeight];
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
		}
		
		//	compute relative top char boundary over baseline
		float rCbTop = (((float) ((cbl - cim) - (cbb.getTopRow() - cim))) / (cbl - cim));
		
		//	compute relative bottom char boundary over baseline
		float rCbBottom = (((float) ((cbl - cim) - (cbb.getBottomRow() - cim))) / (cbl - cim));
		
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
		
		return new CharMetrics(disjointParts, loops, fbSignature, rCbTop, rCbBottom, cbPropotion, cbSignature);
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
	
	static SortedSet getScoredCharSignatures(CharMetrics cm, int style, FontDecoderCharset charSet, boolean debug, char debugChar, int[] efforts) {
		TreeSet scss = new TreeSet(scoredCharSignatureOrder);
		if (cm == null)
			return scss;
		int mpdComputed = 0;
		int lodComputed = 0;
		int fbdComputed = 0;
		int cbtComputed = 0;
		int cbbComputed = 0;
		int cbpComputed = 0;
		int cbdComputed = 0;
		int probCharCount = 0;
		for (int s = 0; s < charSignatures.length; s++) {
			if ((charSet != null) && !charSet.containsChar(charSignatures[s].ch))
				continue;
			Map diffData = ((efforts != null) ? new HashMap() : null);
//			float diff = charSignatures[s].getDifference(style, (cm.disjointParts > 1), (cm.loops > 0), cm.fontBoxSignature, cm.charBoxProportion, cm.charBoxSignature, false, diffData);
			float diff = charSignatures[s].getDifference(style,
					(cm.disjointParts > 1), 3, false,
					(cm.loops > 0), 3, false,
					4,
					cm.fontBoxSignature, 0, 4.5f, true,
					cm.relCharBoxTop, 0.2f, false,
					cm.relCharBoxBottom, 0.2f, false,
					0.3f,
					cm.charBoxProportion, 3.5f, true,
					cm.charBoxSignature, 0, ((style < 0) ? 6.5f : 4.5f), true,
					false, debugChar, diffData);
			//	TODO play with what is counted in, and with limits (adjust cutoffs accordingly from Excel sheet)
			float[] diffDetails = null;
			if (efforts != null) {
				diffDetails = new float[7];
				Arrays.fill(diffDetails, -1);
				if (diffData.containsKey("mpd")) {
					diffDetails[0] = ((Number) diffData.get("mpd")).floatValue();
					mpdComputed++;
				}
				if (diffData.containsKey("lod")) {
					diffDetails[1] = ((Number) diffData.get("lod")).floatValue();
					lodComputed++;
				}
				if (diffData.containsKey("cbt")) {
					diffDetails[2] = ((Number) diffData.get("cbt")).floatValue();
					cbtComputed++;
				}
				if (diffData.containsKey("cbb")) {
					diffDetails[3] = ((Number) diffData.get("cbb")).floatValue();
					cbbComputed++;
				}
				if (diffData.containsKey("cbp")) {
					diffDetails[4] = ((Number) diffData.get("cbp")).floatValue();
					cbpComputed++;
				}
				if (diffData.containsKey("fbd")) {
					diffDetails[5] = ((Number) diffData.get("fbd")).floatValue();
					fbdComputed++;
				}
				if (diffData.containsKey("cbd")) {
					diffDetails[6] = ((Number) diffData.get("cbd")).floatValue();
					cbdComputed++;
				}
				if (diff < 1000)
					probCharCount++;
			}
			scss.add(new ScoredCharSignature(diff, diffDetails, charSignatures[s]));
		}
		if (efforts != null)
			System.out.println(" - effort: " + mpdComputed + " MBD, " + lodComputed + " LOD, " + cbtComputed + " CBT, " + cbbComputed + " CBB, " + cbpComputed + " CBP, " + fbdComputed + " FBD, " + cbdComputed + " CBD");
		if (efforts != null) {
			efforts[0] += mpdComputed;
			efforts[1] += lodComputed;
			efforts[2] += cbtComputed;
			efforts[3] += cbbComputed;
			efforts[4] += cbpComputed;
			efforts[5] += fbdComputed;
			efforts[6] += cbdComputed;
			efforts[7] += probCharCount;
		}
		return scss;
	}
	
	static class ScoredCharSignature {
		float difference;
		float[] diffDetails;
		CharSignature cs;
		ScoredCharSignature(float diff, float[] diffDetails, CharSignature cs) {
			this.difference = diff;
			this.diffDetails = diffDetails;
			this.cs = cs;
		}
		public String toString() {
			return ("'" + this.cs.ch + "' (" + Integer.toString(((int) this.cs.ch), 16) + ") with " + this.difference + ((this.diffDetails == null) ? "" : (" " + Arrays.toString(this.diffDetails))));
		}
	}
	
	private static Comparator scoredCharSignatureOrder = new Comparator() {
		public int compare(Object obj1, Object obj2) {
			ScoredCharSignature scs1 = ((ScoredCharSignature) obj1);
			ScoredCharSignature scs2 = ((ScoredCharSignature) obj2);
			int c = Float.compare(scs1.difference, scs2.difference);
			return ((c == 0) ? (scs1.cs.ch - scs2.cs.ch) : c);
		}
	};
	
	private static final boolean DEBUG_CHAR_PROG_DECODING = false;
	private static final boolean DEBUG_DISPLAY_CHAR_PROG_IMAGES = false;
	
	static char getChar(PdfFont pFont, PStream charProg, int charCode, String charName, FontDecoderCharset charSet, Map objects, Font[] serifFonts, Font[] sansFonts, Font[] monoFonts, HashMap cache, boolean debug) throws IOException {
		byte[] cpBytes;
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			PdfParser.decode(charProg.params.get("Filter"), charProg.bytes, charProg.params, baos, objects);
			cpBytes = baos.toByteArray();
		}
		catch (IOException ioe) {
			System.out.println("Could not read char prog:");
			ioe.printStackTrace(System.out);
			char fbch = StringUtils.getCharForName(charName); // fail gracefully
			System.out.println("Falling back to named char: '" + fbch + "' (" + ((int) fbch) + ")");
			return fbch;
		}
		if (DEBUG_CHAR_PROG_DECODING) {
			System.out.write(cpBytes);
			System.out.println();
		}
		int imgWidth = -1;
		int imgHeight = -1;
		int imgMinY = -1;
		int imgMaxY = -1;
		int bpc = -1;
		boolean isMaskImage = false;
		String imgFilter = null;
		ByteArrayOutputStream imgBuffer = null;
		byte[] imgData = null;
		PdfLineInputStream lis = new PdfLineInputStream(new ByteArrayInputStream(cpBytes));
		byte[] line;
		while ((line = lis.readLine()) != null) {
			if (PdfUtils.startsWith(line, "BI", 0))
				break;
			if (PdfUtils.endsWith(line, " d1")) {
				String[] glyphParams = (new String(line)).split("\\s+");
				if (DEBUG_CHAR_PROG_DECODING) System.out.println("Glyph dimension params: " + Arrays.toString(glyphParams));
				if (glyphParams.length >= 6) {
					if (DEBUG_CHAR_PROG_DECODING) System.out.println(" - lower left Y: " + Integer.parseInt(glyphParams[3]));
					imgMinY = Integer.parseInt(glyphParams[3]);
					if (DEBUG_CHAR_PROG_DECODING) System.out.println(" - upper right Y: " + Integer.parseInt(glyphParams[5]));
					imgMaxY = Integer.parseInt(glyphParams[5]);
				}
			}
			else if (PdfUtils.endsWith(line, " cm")) {
				String[] glyphTransMatrix = (new String(line)).split("\\s+");
				if (DEBUG_CHAR_PROG_DECODING) System.out.println("Glyph transformation matrix: " + Arrays.toString(glyphTransMatrix));
				if (glyphTransMatrix.length >= 6) {
					float[][] gtm = new float[3][3];
					gtm[2][2] = 1;
					gtm[1][2] = Float.parseFloat(glyphTransMatrix[5]);
					gtm[0][2] = Float.parseFloat(glyphTransMatrix[4]);
					gtm[2][1] = 0;
					gtm[1][1] = Float.parseFloat(glyphTransMatrix[3]);
					gtm[0][1] = Float.parseFloat(glyphTransMatrix[2]);
					gtm[2][0] = 0;
					gtm[1][0] = Float.parseFloat(glyphTransMatrix[1]);
					gtm[0][0] = Float.parseFloat(glyphTransMatrix[0]);
					float[] translate = transform(0, 0, 1, gtm);
					if (DEBUG_CHAR_PROG_DECODING) System.out.println(" - glyph rendering origin " + Arrays.toString(translate));
					float[] scaleRotate1 = transform(1, 0, 0, gtm);
					if (DEBUG_CHAR_PROG_DECODING) System.out.println(" - glyph rotation and scaling 1 " + Arrays.toString(scaleRotate1));
					float[] scaleRotate2 = transform(0, 1, 0, gtm);
					if (DEBUG_CHAR_PROG_DECODING) System.out.println(" - glyph rotation and scaling 2 " + Arrays.toString(scaleRotate2));
					//	if image is upside-down (scaleRotate2[1] < 0), invert min and max Y
					if (scaleRotate2[1] < 0) {
						int minY = Math.min(-imgMinY, -imgMaxY);
						int maxY = Math.max(-imgMinY, -imgMaxY);
						imgMinY = minY;
						imgMaxY = maxY;
					}
				}
			}
			else if (DEBUG_CHAR_PROG_DECODING) System.out.println("IGNORING: " + new String(line));
		}
		Map imgParams = new HashMap();
		while ((line = lis.readLine()) != null) {
			if (PdfUtils.startsWith(line, "EI", 0)) {
				if (imgBuffer != null)
					imgData = imgBuffer.toByteArray();
				break;
			}
			if (PdfUtils.startsWith(line, "ID", 0)) {
				imgBuffer = new ByteArrayOutputStream();
				if (line.length > 3)
					imgBuffer.write(line, 3, (line.length-3));
			}
			else if (imgBuffer != null)
				imgBuffer.write(line);
			else if (PdfUtils.startsWith(line, "/", 0)) {
				String keyValuePair = new String(line, 1, (line.length-1));
				String[] keyValue = keyValuePair.split("\\s+");
				if (keyValue.length != 2) {
					if (DEBUG_CHAR_PROG_DECODING) System.out.println("BROKEN PARAMETER LINE: " + new String(line));
					continue;
				}
				imgParams.put(keyValue[0], keyValue[1]);
				if ("W".equals(keyValue[0]) || "Width".equals(keyValue[0]))
					imgWidth = Integer.parseInt(keyValue[1]);
				else if ("H".equals(keyValue[0]) || "Height".equals(keyValue[0]))
					imgHeight = Integer.parseInt(keyValue[1]);
				else if ("BPC".equals(keyValue[0]) || "BitsPerComponent".equals(keyValue[0]))
					bpc = Integer.parseInt(keyValue[1]);
				else if ("IM".equals(keyValue[0]) || "ImageMask".equals(keyValue[0]))
					isMaskImage = "true".equals(keyValue[1]);
				else if ("F".equals(keyValue[0]) || "Filter".equals(keyValue[0])) {
					imgFilter = keyValue[1];
					if (imgFilter.startsWith("/"))
						imgFilter = imgFilter.substring(1);
				}
			}
		}
		
		//	TODO implement other types of type 3 fonts (as examples become available)
		
		if ((imgWidth == -1) || (imgHeight == -1) || (imgData == null)) {
			if (DEBUG_CHAR_PROG_DECODING) System.out.println("Invalid char prog");
			return StringUtils.getCharForName(charName); // fail gracefully
		}
		if (imgFilter != null) {
			imgBuffer = new ByteArrayOutputStream();
			PdfParser.decode(imgFilter, imgData, imgParams, imgBuffer, objects);
			imgData = imgBuffer.toByteArray();
		}
		if (DEBUG_CHAR_PROG_DECODING) {
			System.out.println("GOT DATA FOR CHAR IMAGE (" + imgWidth + " x " + imgHeight + "): " + imgData.length);
			System.out.println("MIN Y: " + imgMinY);
			System.out.println("MAX Y: " + imgMaxY);
		}
		BufferedImage cpImg = null;
		if (isMaskImage) {
			cpImg = createImageMask(imgWidth, imgHeight, imgData);
			if (DEBUG_DISPLAY_CHAR_PROG_IMAGES) {
				BufferedImage cpImgBi = new BufferedImage(cpImg.getWidth(), cpImg.getHeight(), BufferedImage.TYPE_INT_ARGB);
				Graphics gr = cpImgBi.createGraphics();
				gr.drawImage(cpImg, 0, 0, null);
				gr.setColor(Color.RED);
				gr.drawLine(0, imgMaxY, imgWidth, imgMaxY);
				JOptionPane.showMessageDialog(null, new JLabel(new ImageIcon(cpImgBi)));
			}
		}
		else {
			//	TODO implement color spaces, and read them as bitmaps nonetheless
			//	TODO to achieve this, create a BufferedImage in respective color space and read back brightness
		}
		
		//	do we have anything to work with?
		if (cpImg == null)
			return 0;
		pFont.setCharImage(charCode, charName, cpImg);
		
		//	wrap and measure char
		CharImage chImage = new CharImage(cpImg, imgMaxY);
		CharMetrics chMetrics = getCharMetrics(cpImg, 1);
		
		//	little we can do about this one
		if (chMetrics == null) {
//			JOptionPane.showMessageDialog(null, "Char match problem", "Char Match Problem", JOptionPane.INFORMATION_MESSAGE, new ImageIcon(cpImg));
			return 0;
		}
		
		//	set up statistics
		CharImageMatch bestCim = null;
		float bestCimSigDiff = -1;
		
		//	get ranked list of probably matches
		SortedSet matchChars = getScoredCharSignatures(chMetrics, -1, charSet, true, ((char) 0), null);
		
		//	evaluate probable matches
		for (Iterator mcit = matchChars.iterator(); mcit.hasNext();) {
			ScoredCharSignature scs = ((ScoredCharSignature) mcit.next());
			if (scs.difference > 500)
				break;
			System.out.println(" testing '" + scs.cs.ch + "' (" + ((int) scs.cs.ch) + "), signature difference is " + scs.difference);
			CharMatchResult matchResult = matchChar(chImage, scs.cs.ch, true, serifFonts, sansFonts, monoFonts, cache, false, false);
			CharImageMatch cim = null;
			for (int s = 0; s < matchResult.serifStyleCims.length; s++) {
				if ((matchResult.serifStyleCims[s] != null) && ((cim == null) || (cim.sim < matchResult.serifStyleCims[s].sim)))
					cim = matchResult.serifStyleCims[s];
			}
			for (int s = 0; s < matchResult.sansStyleCims.length; s++) {
				if ((matchResult.sansStyleCims[s] != null) && ((cim == null) || (cim.sim < matchResult.sansStyleCims[s].sim)))
					cim = matchResult.sansStyleCims[s];
			}
			for (int s = 0; s < matchResult.monoStyleCims.length; s++) {
				if ((matchResult.monoStyleCims[s] != null) && ((cim == null) || (cim.sim < matchResult.monoStyleCims[s].sim)))
					cim = matchResult.monoStyleCims[s];
			}
			
			if (cim == null) {
//				System.out.println("   --> could not render image");
				continue;
			}
			System.out.println("   --> similarity is " + cim.sim);
			if ((bestCim == null) || (cim.sim > bestCim.sim)) {
				System.out.println("   ==> new best match '" + scs.cs.ch + "' (" + ((int) scs.cs.ch) + ", " + StringUtils.getCharName((char) scs.cs.ch) + "), similarity is " + cim.sim + ", scale logs are " + cim.scaleLogX + "/" + cim.scaleLogY);
				if (bestCim != null) {
					System.out.println("    - improvement is " + (cim.sim - bestCim.sim));
					System.out.println("    - sig diff factor is " + (scs.difference / bestCimSigDiff));
					System.out.println("    - sig diff malus is " + (scs.difference / (bestCimSigDiff * 100)));
					if ((bestCim.sim + (scs.difference / (bestCimSigDiff * 100))) > cim.sim) {
						System.out.println("    ==> rejected for signature difference");
						if (debug || DEBUG_DISPLAY_CHAR_PROG_IMAGES)
							PdfCharDecoder.displayCharMatch(cim, "New best match rejected for char signature difference");
						continue;
					}
				}
				if (debug || DEBUG_DISPLAY_CHAR_PROG_IMAGES)
					displayCharMatch(cim, "New best match");
				bestCim = cim;
				bestCimSigDiff = scs.difference;
			}
			else if (DEBUG_DISPLAY_CHAR_PROG_IMAGES && ((bestCim == null) || (cim.sim > (bestCim.sim - 0.01))))
				displayCharMatch(cim, "New almost best match");
		}
		
		//	finally ...
		return ((bestCim == null) ? 0 : bestCim.match.ch);
	}
	
	private static float[] transform(float x, float y, float z, float[][] matrix) {
		float[] res = new float[3];
		for (int c = 0; c < matrix.length; c++)
			res[c] = ((matrix[c][0] * x) + (matrix[c][1] * y) + (matrix[c][2] * z));
		return res;
	}
	
	private static final int blackRgb = Color.BLACK.getRGB();
	private static BufferedImage createImageMask(int width, int height, byte[] bits) {
		BufferedImage bi = new BufferedImage((width + 2), (height + 2), BufferedImage.TYPE_BYTE_GRAY);
		Graphics g = bi.getGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, bi.getWidth(), bi.getHeight());
		int rw = (((width % 8) == 0) ? width : (((width / 8) + 1) * 8));
		for (int r = 0; r < height; r++)
			for (int c = 0; c < width; c++) {
				int bitOffset = (rw * r) + c;
				int byteIndex = (bitOffset / 8);
				if (byteIndex < bits.length) {
					int byteOffset = (bitOffset % 8);
					int bt = bits[byteIndex];
					int btMask = 1 << (7 - byteOffset);
					boolean bit = ((bt & btMask) == 0);
					if (bit)
						bi.setRGB((c+1), (r+1), blackRgb);
				}
			}
		return bi;
	}
	
	static class CharImageMatch {
		CharImage charImage;
		CharImage match;
		int matched;
		int spurious;
		int missed;
		float sim;
		float vCenterShift;
		float scaleLogX;
		float scaleLogY;
		boolean isCharBoxMatch;
		float relAscenderShift;
		float relDescenderShift;
		int leftShift;
		int rightShift;
		int topShift;
		int bottomShift;
		float xHistSim;
		float yHistSim;
		CharImageMatch(CharImage charImage, CharImage match, int matched, int spurious, int missed, boolean isCharBoxMatch) {
			this.charImage = charImage;
			this.match = match;
			this.matched = matched;
			this.spurious = spurious;
			this.missed = missed;
			if (this.matched == 0)
				this.sim = 0;
			else {
				float precision = (((float) this.matched) / (this.spurious + this.matched));
				float recall = (((float) this.matched) / (this.missed + this.matched));
				this.sim = ((precision * recall * 2) / (precision + recall));
			}
			this.isCharBoxMatch = isCharBoxMatch;
		}
	}
//	
//	static CharImageMatch matchChar(CharImage charImage, char ch, Font font, boolean isSerifFont, HashMap cache, boolean isVerificationMatch, boolean debug) {
//		CharImage matchImage = createCharImage(ch, font, isSerifFont, cache, debug);
//		if (matchImage == null)
//			return null;
//		if ((charImage.box.getBottomRow() <= charImage.baseline) && (matchImage.baseline <= matchImage.box.getTopRow()))
//			return null;
//		if ((0 < charImage.baseline) && (charImage.baseline <= charImage.box.getTopRow()) && (matchImage.box.getBottomRow() <= matchImage.baseline))
//			return null;
//		return matchCharImage(charImage, matchImage, font.getName(), (charImage.baseline < 1), isVerificationMatch, debug);
//	}
	
	static class CharMatchResult {
		boolean rendered = false;
		CharImageMatch[] serifStyleCims = new CharImageMatch[4];
		CharImageMatch[] sansStyleCims = new CharImageMatch[4];
		CharImageMatch[] monoStyleCims = new CharImageMatch[4];
		CharMatchResult() {}
		float getAverageSimilarity() {
			float simSum = 0;
			int simCount = 0;
			for (int s = Font.PLAIN; s <= (Font.BOLD | Font.ITALIC); s++) {
				if (this.serifStyleCims[s] != null) {
					simSum += this.serifStyleCims[s].sim;
					simCount++;
				}
				if (this.sansStyleCims[s] != null) {
					simSum += this.sansStyleCims[s].sim;
					simCount++;
				}
				if (this.monoStyleCims[s] != null) {
					simSum += this.monoStyleCims[s].sim;
					simCount++;
				}
			}
			return ((simCount == 0) ? 0 : (simSum / simCount));
		}
	}
	
	static CharMatchResult matchChar(CharImage charImage, char ch, boolean allowCaps, Font[] serifFonts, Font[] sansFonts, Font[] monoFonts, HashMap cache, boolean isVerificationMatch, boolean debug) {
		CharMatchResult matchResult = new CharMatchResult();
		
		//	anything to match against?
		if (ch <= ' ')
			return matchResult;
		
		//	assess char position and extent
		boolean charAboveBaseline = (charImage.box.getBottomRow() <= charImage.baseline);
		boolean charBelowBaseline = ((0 < charImage.baseline) && (charImage.baseline <= charImage.box.getTopRow()));
		
		/* char box match or font box match? (cut some slack for mathematical
		 * symbols, especially '=', whose distance tends to vary widely) */
		boolean useCharBoxMatch;
		if (charImage.baseline < 1)
			useCharBoxMatch = true;
		else if (((charImage.box.getWidth() * 2) > charImage.img.getWidth()) && ((charImage.box.getHeight() * 2) > charImage.img.getHeight()))
			useCharBoxMatch = true;
		else if ("=#+×<>\u00B1\u00F7\u2260\u2264\u2265\u2266\u2267\u2268\u2269".indexOf(ch) != -1)
			useCharBoxMatch = true;
		else useCharBoxMatch = false;
		
		//	match argument char in different serif fonts
		for (int s = 0; s < serifFonts.length; s++) {
			CharImage matchImage = createCharImage(ch, serifFonts[s], true, false, cache, debug);
			if (matchImage == null)
				continue;
			if (charAboveBaseline && (matchImage.baseline <= matchImage.box.getTopRow()))
				continue;
			if (charBelowBaseline && (matchImage.box.getBottomRow() <= matchImage.baseline))
				continue;
			boolean doUseCharBoxMatch = (charImage.baseline < 1);
			if (useCharBoxMatch != doUseCharBoxMatch) {
				double charBoxProportionDistance = computeCharBoxProportionDistance(charImage, matchImage);
				if (charBoxProportionDistance < 1)
					doUseCharBoxMatch = true;
			}
			matchResult.serifStyleCims[s] = matchCharImage(charImage, matchImage, serifFonts[s].getName(), doUseCharBoxMatch, isVerificationMatch, debug);
			if (allowCaps && (ch != Character.toUpperCase(ch))) {
				CharImage capMatchImage = createCharImage(Character.toUpperCase(ch), serifFonts[s], true, false, cache, debug);
				if (capMatchImage != null) {
					doUseCharBoxMatch = (charImage.baseline < 1);
					if (useCharBoxMatch != doUseCharBoxMatch) {
						double charBoxProportionDistance = computeCharBoxProportionDistance(charImage, capMatchImage);
						if (charBoxProportionDistance < 1)
							doUseCharBoxMatch = true;
					}
					CharImageMatch capCim = matchCharImage(charImage, capMatchImage, serifFonts[s].getName(), doUseCharBoxMatch, isVerificationMatch, debug);
					if ((capCim != null) && ((matchResult.serifStyleCims[s] == null) || (matchResult.serifStyleCims[s].sim < capCim.sim)))
						matchResult.serifStyleCims[s] = capCim;
				}
			}
			if (matchResult.serifStyleCims[s] != null)
				matchResult.rendered = true;
		}
		
		//	match argument char in different sans-serif fonts
		for (int s = 0; s < sansFonts.length; s++) {
			CharImage matchImage = createCharImage(ch, sansFonts[s], false, false, cache, debug);
			if (matchImage == null)
				continue;
			if (charAboveBaseline && (matchImage.baseline <= matchImage.box.getTopRow()))
				continue;
			if (charBelowBaseline && (matchImage.box.getBottomRow() <= matchImage.baseline))
				continue;
			boolean doUseCharBoxMatch = (charImage.baseline < 1);
			if (useCharBoxMatch != doUseCharBoxMatch) {
				double charBoxProportionDistance = computeCharBoxProportionDistance(charImage, matchImage);
				if (charBoxProportionDistance < 1)
					doUseCharBoxMatch = true;
			}
			matchResult.sansStyleCims[s] = matchCharImage(charImage, matchImage, sansFonts[s].getName(), doUseCharBoxMatch, isVerificationMatch, debug);
			if (allowCaps && (ch != Character.toUpperCase(ch))) {
				CharImage capMatchImage = createCharImage(Character.toUpperCase(ch), sansFonts[s], false, false, cache, debug);
				if (capMatchImage != null) {
					doUseCharBoxMatch = (charImage.baseline < 1);
					if (useCharBoxMatch != doUseCharBoxMatch) {
						double charBoxProportionDistance = computeCharBoxProportionDistance(charImage, capMatchImage);
						if (charBoxProportionDistance < 1)
							doUseCharBoxMatch = true;
					}
					CharImageMatch capCim = matchCharImage(charImage, capMatchImage, sansFonts[s].getName(), useCharBoxMatch, isVerificationMatch, debug);
					if ((capCim != null) && ((matchResult.sansStyleCims[s] == null) || (matchResult.sansStyleCims[s].sim < capCim.sim)))
						matchResult.sansStyleCims[s] = capCim;
				}
			}
			if (matchResult.sansStyleCims[s] != null)
				matchResult.rendered = true;
		}
		
		//	match argument char in different monospaced fonts
		for (int s = 0; s < monoFonts.length; s++) {
			CharImage matchImage = createCharImage(ch, monoFonts[s], false, true, cache, debug);
			if (matchImage == null)
				continue;
			if (charAboveBaseline && (matchImage.baseline <= matchImage.box.getTopRow()))
				continue;
			if (charBelowBaseline && (matchImage.box.getBottomRow() <= matchImage.baseline))
				continue;
			boolean doUseCharBoxMatch = (charImage.baseline < 1);
			if (useCharBoxMatch != doUseCharBoxMatch) {
				double charBoxProportionDistance = computeCharBoxProportionDistance(charImage, matchImage);
				if (charBoxProportionDistance < 1)
					doUseCharBoxMatch = true;
			}
			matchResult.monoStyleCims[s] = matchCharImage(charImage, matchImage, monoFonts[s].getName(), doUseCharBoxMatch, isVerificationMatch, debug);
			if (allowCaps && (ch != Character.toUpperCase(ch))) {
				CharImage capMatchImage = createCharImage(Character.toUpperCase(ch), monoFonts[s], false, true, cache, debug);
				if (capMatchImage != null) {
					doUseCharBoxMatch = (charImage.baseline < 1);
					if (useCharBoxMatch != doUseCharBoxMatch) {
						double charBoxProportionDistance = computeCharBoxProportionDistance(charImage, capMatchImage);
						if (charBoxProportionDistance < 1)
							doUseCharBoxMatch = true;
					}
					CharImageMatch capCim = matchCharImage(charImage, capMatchImage, monoFonts[s].getName(), useCharBoxMatch, isVerificationMatch, debug);
					if ((capCim != null) && ((matchResult.monoStyleCims[s] == null) || (matchResult.monoStyleCims[s].sim < capCim.sim)))
						matchResult.monoStyleCims[s] = capCim;
				}
			}
			if (matchResult.monoStyleCims[s] != null)
				matchResult.rendered = true;
		}
		
		//	finally
		return matchResult;
	}
	
	private static double computeCharBoxProportionDistance(CharImage ci1, CharImage ci2) {
		double charBoxProportion1 = Math.log(((double) ci1.box.getWidth()) / ci1.box.getHeight());
		double charBoxProportion2 = Math.log(((double) ci2.box.getWidth()) / ci2.box.getHeight());
		return Math.abs(charBoxProportion1 - charBoxProportion2);
	}
	
	private static final char DEBUG_MATCH_TARGET_CHAR = ((char) 0);
	
	private static CharImageMatch matchCharImage(CharImage charImage, CharImage match, String fontName, boolean charBoxMatch, boolean isVerificationMatch, boolean debug) {
//		if ((charImage == null) || (match == null))
//			return null;
//		
		if (!isVerificationMatch) {
			double charBoxProportionDistance = computeCharBoxProportionDistance(charImage, match);
			if (charBoxProportionDistance > 1) // TODO verify this cutoff
				return null;
		}
		
		//	align histograms to compute image match offsets
		int[] colShifts = getHistogramAlignmentShifts(charImage.xHistogram, charImage.xHistogramMax, match.xHistogram, match.xHistogramMax, 10);
		int leftShift = colShifts[0];
		int rightShift = colShifts[1];
		int[] rowShifts = getHistogramAlignmentShifts(charImage.yHistogram, charImage.yHistogramMax, match.yHistogram, match.yHistogramMax, 10);
		int topShift = rowShifts[0];
		int bottomShift = rowShifts[1];
		
		/* create match with and without horizontal and vertical shifts (either
		 * one can be advantageous, depending on glyph shape and resulting
		 * histogram shape); actually matching glyphs will benefit from
		 * - not shifting if histograms are rather smooth and flat
		 * - shifting if histograms exhibit distinctive peaks (corresponding)
		 *   to stems
		 */
		CharImageMatch ffCim = matchCharImage(charImage, match, 0, 0, 0, 0, fontName, charBoxMatch, debug);
		CharImageMatch fsCim = matchCharImage(charImage, match, 0, 0, topShift, bottomShift, fontName, charBoxMatch, debug);
		CharImageMatch sfCim = matchCharImage(charImage, match, leftShift, rightShift, 0, 0, fontName, charBoxMatch, debug);
		CharImageMatch ssCim = matchCharImage(charImage, match, leftShift, rightShift, topShift, bottomShift, fontName, charBoxMatch, debug);
		
		//	find and return best match
		CharImageMatch cim1 = ((ffCim.sim < fsCim.sim) ? fsCim : ffCim);
		CharImageMatch cim2 = ((sfCim.sim < ssCim.sim) ? ssCim : sfCim);
		return ((cim1.sim < cim2.sim) ? cim2 : cim1);
	}
	
	private static int[] getHistogramAlignmentShifts(short[] ciHist, int ciHistMax, short[] mHist, int mHistMax, int maxShift) {
		int ciStart = 0;
		while ((ciStart < ciHist.length) && (ciHist[ciStart] == 0))
			ciStart++;
		int ciEnd = ciHist.length;
		while ((ciEnd != 0) && (ciHist[ciEnd-1] == 0))
			ciEnd--;
		int mStart = 0;
		while ((mStart < mHist.length) && (mHist[mStart] == 0))
			mStart++;
		int mEnd = mHist.length;
		while ((mEnd != 0) && (mHist[mEnd-1] == 0))
			mEnd--;
		
		if ((ciEnd <= ciStart) || (mEnd <= mStart)) {
			int[] shifts = {0, 0};
			return shifts;
		}
		
		int ciLength = (ciEnd - ciStart);
		int mLength = (mEnd - mStart);
		
		int ciMaxShift = maxShift;
		int mMaxShift = maxShift;
		if (ciHist.length > mHist.length) {
			ciMaxShift = maxShift;
			mMaxShift = ((maxShift * mHist.length) / ciHist.length);
		}
		else if (ciHist.length < mHist.length) {
			ciMaxShift = ((maxShift * ciHist.length) / mHist.length);
			mMaxShift = maxShift;
		}
		int startShift = 0;
		int endShift = 0;
		int bestShiftAlignLength = 1;
		long bestShiftDistSquareSum = Long.MAX_VALUE;
		for (int ss = -ciMaxShift; ss <= mMaxShift; ss++)
			for (int es = -ciMaxShift; es <= mMaxShift; es++) {
				long distSquareSum = 0;
				int alignLength = Math.max((ciLength + Math.max(-ss, 0) + Math.max(-es, 0)), (mLength + Math.max(ss, 0) + Math.max(es, 0)));
				for (int c = 0; c < alignLength; c++) {
//					//	THIS IS NOT PUSHING INWARD, THIS IS JUMPING INWARD, AKA PULING OUTWARD !!!
//					int sCol = ((sLeft + Math.max(-ls, 0)) + ((c * (sWidth - Math.max(-ls, 0) - Math.max(-rs, 0))) / (mWidth - Math.max(-ls, 0) - Math.max(-rs, 0))));
//					int rCol = (rLeft + Math.max(ls, 0) + ((c * (rWidth - Math.max(ls, 0) - Math.max(rs, 0))) / (mWidth - Math.max(ls, 0) - Math.max(rs, 0))));
					//	PUSH INWARD
//					int sCol = (0 - Math.max(-ls, 0) + ((c * (sHist.length + Math.max(-ls, 0) + Math.max(-rs, 0))) / mWidth));
//					int rCol = (0 - Math.max(ls, 0) + ((c * (rHist.length + Math.max(ls, 0) + Math.max(rs, 0))) / mWidth));
					int ciHistPos = (ciStart - Math.max(-ss, 0) + ((c * (ciLength + Math.max(-ss, 0) + Math.max(-es, 0))) / alignLength));
					int mHistPos = (mStart - Math.max(ss, 0) + ((c * (mLength + Math.max(ss, 0) + Math.max(es, 0))) / alignLength));
					
					int ciHistVal = (mHistMax * (((0 <= ciHistPos) && (ciHistPos < ciHist.length)) ? ciHist[ciHistPos] : 0));
					int mHistVal = (ciHistMax * (((0 <= mHistPos) && (mHistPos < mHist.length)) ? mHist[mHistPos] : 0));
					distSquareSum += ((ciHistVal - mHistVal) * (ciHistVal - mHistVal));
					if (distSquareSum < 0) {
						distSquareSum = Long.MAX_VALUE;
						break;
					}
				}
				if ((distSquareSum != Long.MAX_VALUE) && ((distSquareSum / alignLength) < (bestShiftDistSquareSum / bestShiftAlignLength))) {
					bestShiftAlignLength = alignLength;
					bestShiftDistSquareSum = distSquareSum;
					startShift = ss;
					endShift = es;
				}
			}
		int[] shifts = {startShift, endShift};
		return shifts;
	}
	
	private static CharImageMatch matchCharImage(CharImage charImage, CharImage match, int leftShift, int rightShift, int topShift, int bottomShift, String fontName, boolean charBoxMatch, boolean debug) {
		
		int ciLeft = charImage.box.getLeftCol();
		int ciRight = charImage.box.getRightCol();
		int ciWidth = (ciRight - ciLeft);
		int ciTop = (charBoxMatch ? charImage.box.getTopRow() : Math.min(charImage.baseline, charImage.box.getTopRow()));
		int ciBottom = (charBoxMatch ? charImage.box.getBottomRow() : Math.max(charImage.baseline, charImage.box.getBottomRow()));
		int ciHeight = (ciBottom - ciTop);
		int mLeft = match.box.getLeftCol();
		int mRight = match.box.getRightCol();
		int mWidth = (mRight - mLeft);
		int mTop = (charBoxMatch ? match.box.getTopRow() : Math.min(match.baseline, match.box.getTopRow()));
		int mBottom = (charBoxMatch ? match.box.getBottomRow() : Math.max(match.baseline, match.box.getBottomRow()));
		int mHeight = (mBottom - mTop);
		
		int cimWidth = Math.max(ciWidth, mWidth);
		int cimHeight = Math.max(ciHeight, mHeight);
		
		//	align histograms to compute image match offsets
		if ((leftShift != 0) || (rightShift != 0)) {
			ciLeft = (ciLeft - Math.max(-leftShift, 0));
			ciRight = (ciRight + Math.max(-rightShift, 0));
			ciWidth = (ciRight - ciLeft);
			mLeft = (mLeft - Math.max(leftShift, 0));
			mRight = (mRight + Math.max(rightShift, 0));
			mWidth = (mRight - mLeft);
			cimWidth = Math.max(ciWidth, mWidth);
		}
		if ((topShift != 0) || (bottomShift != 0)) {
			ciTop = (ciTop - Math.max(-topShift, 0));
			ciBottom = (ciBottom + Math.max(-bottomShift, 0));
			ciHeight = (ciBottom - ciTop);
			mTop = (mTop - Math.max(topShift, 0));
			mBottom = (mBottom + Math.max(bottomShift, 0));
			mHeight = (mBottom - mTop);
			cimHeight = Math.max(ciHeight, mHeight);
		}
		
		int matched = 0;
		int spurious = 0;
		int missed = 0;
		byte[][] cimData = new byte[cimWidth][cimHeight];
		byte[][] ciDistData = new byte[cimWidth][cimHeight];
		byte[][] mDistData = new byte[cimWidth][cimHeight];
		for (int cimCol = 0; cimCol < cimWidth; cimCol++) {
			int ciCol = (ciLeft + ((cimCol * ciWidth) / cimWidth));
			if (ciCol < 0)
				continue;
			if (ciRight <= ciCol)
				break;
			int mCol = (mLeft + ((cimCol * mWidth) / cimWidth));
			if (mCol < 0)
				continue;
			if (mRight <= mCol)
				break;
			
			for (int cimRow = 0; cimRow < cimHeight; cimRow++) {
				int ciRow = (ciTop + ((cimRow * ciHeight) / cimHeight));
				if (ciRow < 0)
					continue;
				if (ciBottom <= ciRow)
					break;
				int mRow = (mTop + ((cimRow * mHeight) / cimHeight));
				if (mRow < 0)
					continue;
				if (mBottom <= mRow)
					break;
				
				byte cib = (((0 <= ciCol) && (ciCol < charImage.brightness.length) && (0 <= ciRow) && (ciRow < charImage.brightness[ciCol].length)) ? charImage.brightness[ciCol][ciRow] : ((byte) 127));
				byte mb = (((0 <= mCol) && (mCol < match.brightness.length) && (0 <= mRow) && (mRow < match.brightness[mCol].length)) ? match.brightness[mCol][mRow] : ((byte) 127));
				if ((cib < 80) && (mb < 80)) {
					matched++;
					cimData[cimCol][cimRow] = CIM_MATCHED;
					ciDistData[cimCol][cimRow] = 1;
					mDistData[cimCol][cimRow] = 1;
				}
				else if (cib < 80) {
					spurious++;
					cimData[cimCol][cimRow] = CIM_SPURIOUS;
					ciDistData[cimCol][cimRow] = 1;
					mDistData[cimCol][cimRow] = 0;
				}
				else if (mb < 80) {
					missed++;
					cimData[cimCol][cimRow] = CIM_MISSED;
					ciDistData[cimCol][cimRow] = 0;
					mDistData[cimCol][cimRow] = 1;
				}
				else {
					cimData[cimCol][cimRow] = CIM_NONE;
					ciDistData[cimCol][cimRow] = 0;
					mDistData[cimCol][cimRow] = 0;
				}
			}
		}
		
		//	TODO try and use char box match for verification
		
		//	TODO try and use average distances to penalize missed branches (maybe use to weight spurious and missed pixels)
		
		//	TODO consider using average distances that include direct matches as ultimate similarity
		//	TODO first figure out how to normalize average distances, however
		
		//	TODO figure out if surface of any use
		
		CharImageMatch cim = new CharImageMatch(charImage, match, matched, spurious, missed, charBoxMatch);
		cim.leftShift = leftShift;
		cim.rightShift = rightShift;
		cim.topShift = topShift;
		cim.bottomShift = bottomShift;
		cim.xHistSim = getHistogramSimilarity(charImage.xHistogram, charImage.xHistogramMax, match.xHistogram, match.xHistogramMax, leftShift, rightShift);
		cim.yHistSim = getHistogramSimilarity(charImage.yHistogram, charImage.yHistogramMax, match.yHistogram, match.yHistogramMax, topShift, bottomShift);
		
		//	test for similarity computation
//		cim.xHistSim = getHistogramSimilarity(charImage.xHistogram, charImage.xHistogramMax, charImage.xHistogram, charImage.xHistogramMax, 0, 0);
//		cim.yHistSim = getHistogramSimilarity(match.yHistogram, match.yHistogramMax, match.yHistogram, match.yHistogramMax, 0, 0);
		
		/* Measure glyph distortion, and also displacement relative to baseline
		 * - measure baseline relative ascender and descender shift
		 * - measure relative stretch in each dimension
		 * - both should be relatively constant for each font
		 * ==> penalize deviations
		 * ==> should help preventing case mix-ups for "c", "j", "o", "p", "s", "v", "w", "x", "y", and "z"
		 */
		float ciRelAscender = (((float) (charImage.baseline - charImage.box.getTopRow())) / charImage.img.getHeight());
		float mRelAscender = (((float) (match.baseline - match.box.getTopRow())) / match.img.getHeight());
		float ciRelDescender = (((float) (charImage.baseline - charImage.box.getBottomRow())) / charImage.img.getHeight());
		float mRelDescender = (((float) (match.baseline - match.box.getBottomRow())) / match.img.getHeight());
		cim.relAscenderShift = (ciRelAscender - mRelAscender);
		cim.relDescenderShift = (ciRelDescender - mRelDescender);
		
		float ciRelVerticalCenter = (((float) (charImage.box.getTopRow() + charImage.box.getBottomRow())) / (2 * charImage.img.getHeight()));
		float mRelVerticalCenter = (((float) (match.box.getTopRow() + match.box.getBottomRow())) / (2 * match.img.getHeight()));
		cim.vCenterShift = (ciRelVerticalCenter - mRelVerticalCenter);
		
		cim.scaleLogX = ((float) Math.log(((double) charImage.box.getWidth()) / match.box.getWidth()));
		cim.scaleLogY = ((float) Math.log(((double) charImage.box.getHeight()) / match.box.getHeight()));
		
		if (debug) {
			fillDistData(ciDistData);
			fillDistData(mDistData);
			System.out.println((charBoxMatch ? "Char" : "Font") + " box match stats for " + fontName + ":");
			double ciAreaProportion = Math.log(((double) charImage.box.getWidth()) / charImage.box.getHeight());
			double mAreaProportion = Math.log(((double) match.box.getWidth()) / match.box.getHeight());
			System.out.println(" - area proportion distance is " + Math.abs(ciAreaProportion - mAreaProportion));
			System.out.println(" - matched " + matched + ", surface " + getSurface(cimData, CIM_MATCHED));
			System.out.println(" - spurious " + spurious + ", surface " + getSurface(cimData, CIM_SPURIOUS) + ", avg distance " + getAvgDist(cimData, CIM_SPURIOUS, mDistData));
			System.out.println(" - missed " + missed + ", surface " + getSurface(cimData, CIM_MISSED) + ", avg distance " + getAvgDist(cimData, CIM_MISSED, ciDistData));
	//		if (cim.sim > 0.4)
				displayCharMatch(cim, ((charBoxMatch ? "Char" : "Font") + " box match"));
		}
		return cim;
	}
	
	private static float getHistogramSimilarity(short[] ciHist, int ciHistMax, short[] mHist, int mHistMax, int startShift, int endShift) {
		int ciStart = 0;
		while ((ciStart < ciHist.length) && (ciHist[ciStart] == 0))
			ciStart++;
		int ciEnd = ciHist.length;
		while ((ciEnd != 0) && (ciHist[ciEnd-1] == 0))
			ciEnd--;
		int mStart = 0;
		while ((mStart < mHist.length) && (mHist[mStart] == 0))
			mStart++;
		int mEnd = mHist.length;
		while ((mEnd != 0) && (mHist[mEnd-1] == 0))
			mEnd--;
		
		int ciLength = (ciEnd - ciStart);
		int mLength = (mEnd - mStart);
		
		double distSquareSum = 0;
		int alignLength = Math.max((ciLength + Math.max(-startShift, 0) + Math.max(-endShift, 0)), (mLength + Math.max(startShift, 0) + Math.max(endShift, 0)));
		for (int c = 0; c < alignLength; c++) {
//			//	THIS IS NOT PUSHING INWARD, THIS IS JUMPING INWARD, AKA PULING OUTWARD !!!
//			int sCol = ((sLeft + Math.max(-ls, 0)) + ((c * (sWidth - Math.max(-ls, 0) - Math.max(-rs, 0))) / (mWidth - Math.max(-ls, 0) - Math.max(-rs, 0))));
//			int rCol = (rLeft + Math.max(ls, 0) + ((c * (rWidth - Math.max(ls, 0) - Math.max(rs, 0))) / (mWidth - Math.max(ls, 0) - Math.max(rs, 0))));
			//	PUSH INWARD
//			int sCol = (0 - Math.max(-ls, 0) + ((c * (sHist.length + Math.max(-ls, 0) + Math.max(-rs, 0))) / mWidth));
//			int rCol = (0 - Math.max(ls, 0) + ((c * (rHist.length + Math.max(ls, 0) + Math.max(rs, 0))) / mWidth));
			int ciHistPos = (ciStart - Math.max(-startShift, 0) + ((c * (ciLength + Math.max(-startShift, 0) + Math.max(-endShift, 0))) / alignLength));
			int mHistPos = (mStart - Math.max(startShift, 0) + ((c * (mLength + Math.max(startShift, 0) + Math.max(endShift, 0))) / alignLength));
			
			float ciHistVal = (((float) (((0 <= ciHistPos) && (ciHistPos < ciHist.length)) ? ciHist[ciHistPos] : 0)) / ciHistMax);
			float mHistVal = (((float) (((0 <= mHistPos) && (mHistPos < mHist.length)) ? mHist[mHistPos] : 0)) / mHistMax);
			distSquareSum += ((ciHistVal - mHistVal) * (ciHistVal - mHistVal));
		}
		return (1.0f - ((float) (distSquareSum / alignLength)));
	}
	
	private static void fillDistData(byte[][] distData) {
		for (boolean unmatchedRemains = true; unmatchedRemains;) {
			unmatchedRemains = false;
			boolean newMatch = false;
			for (int x = 0; x < distData.length; x++)
				for (int y = 0; y < distData[x].length; y++) {
					if (distData[x][y] != 0)
						continue;
					byte dist = Byte.MAX_VALUE;
					if ((x != 0) && (distData[x-1][y] != 0))
						dist = ((byte) Math.min((distData[x-1][y] + 1), dist));
					if ((y != 0) && (distData[x][y-1] != 0))
						dist = ((byte) Math.min((distData[x][y-1] + 1), dist));
					if (((x+1) < distData.length) && (distData[x+1][y] != 0))
						dist = ((byte) Math.min((distData[x+1][y] + 1), dist));
					if (((y+1) < distData[x].length) && (distData[x][y+1] != 0))
						dist = ((byte) Math.min((distData[x][y+1] + 1), dist));
					if (dist < Byte.MAX_VALUE) {
						distData[x][y] = dist;
						newMatch = true;
					}
					else unmatchedRemains = true;
				}
			
			if (newMatch)
				continue;
			
			for (int x = 0; x < distData.length; x++)
				for (int y = 0; y < distData[x].length; y++) {
					if (distData[x][y] == 0)
						distData[x][y] = Byte.MAX_VALUE;
				}
			break;
		}
	}
	
	private static double getAvgDist(byte[][] cimData, byte t, byte[][] distData) {
		int tCount = 0;
		double distSum = 0;
		for (int x = 0; x < cimData.length; x++)
			for (int y = 0; y < cimData[x].length; y++) {
				if (cimData[x][y] != t)
					continue;
//				if (distData[x][y] == 1)
//					continue;
				tCount++;
				distSum += (distData[x][y]-1);
			}
		return ((tCount == 0) ? 0 : (distSum / tCount));
	}
	
	private static final byte CIM_NONE = 0;
	private static final byte CIM_MATCHED = 1;
	private static final byte CIM_SPURIOUS = 2;
	private static final byte CIM_MISSED = 3;
	
	private static void resetCimData(byte[][] cimData) {
		for (int x = 0; x < cimData.length; x++)
			for (int y = 0; y < cimData[x].length; y++) {
				if (cimData[x][y] < 0)
					cimData[x][y] = ((byte) -cimData[x][y]);
			}
	}
	
	private static int getSurface(byte[][] cimData, byte t) {
		int tSurface = 0;
		for (int x = 0; x < cimData.length; x++)
			for (int y = 0; y < cimData[x].length; y++) {
				if (cimData[x][y] == t)
					continue;
				if ((x != 0) && (cimData[x-1][y] == t))
					tSurface++;
				else if ((y != 0) && (cimData[x][y-1] == t))
					tSurface++;
				else if (((x+1) < cimData.length) && (cimData[x+1][y] == t))
					tSurface++;
				else if (((y+1) < cimData[x].length) && (cimData[x][y+1] == t))
					tSurface++;
			}
		return tSurface;
	}
	
	static BufferedImage getCharMatchImage(CharImageMatch cim) {
		BufferedImage bi = new BufferedImage(
				(cim.charImage.img.getWidth() + 1 + cim.match.img.getWidth() + 1 + Math.max(cim.charImage.img.getWidth(), cim.match.img.getWidth()) + 10),
				(Math.max(cim.charImage.img.getHeight(), cim.match.img.getHeight()) + 10),
				BufferedImage.TYPE_INT_ARGB
			);
		Graphics g = bi.getGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, bi.getWidth(), bi.getHeight());
		
		g.drawImage(cim.charImage.img, 0, 0, null);
		g.setColor(new Color((0x80FFFFFF & Color.MAGENTA.getRGB()), true));
		for (int x = 0; x < cim.charImage.xHistogram.length; x++)
			g.drawLine(x, (bi.getHeight()-cim.charImage.xHistogram[x]), x, bi.getHeight());
		g.setColor(new Color((0x80FFFFFF & Color.BLUE.getRGB()), true));
		for (int y = 0; y < cim.charImage.yHistogram.length; y++)
			g.drawLine(0, y, cim.charImage.yHistogram[y], y);
		
		g.drawImage(cim.match.img, (cim.charImage.img.getWidth() + 1), 0, null);
		g.setColor(new Color((0x80FFFFFF & Color.MAGENTA.getRGB()), true));
		for (int x = 0; x < cim.match.xHistogram.length; x++)
			g.drawLine((x + cim.charImage.img.getWidth() + 1), (bi.getHeight()-cim.match.xHistogram[x]), (x + cim.charImage.img.getWidth() + 1), bi.getHeight());
		g.setColor(new Color((0x80FFFFFF & Color.BLUE.getRGB()), true));
		for (int y = 0; y < cim.match.yHistogram.length; y++)
			g.drawLine((cim.charImage.img.getWidth() + 1), y, (cim.charImage.img.getWidth() + 1 + cim.match.yHistogram[y]), y);
		
		g.setColor(Color.BLACK);
		if (0 < cim.charImage.baseline)
			g.drawLine(0, cim.charImage.baseline, cim.charImage.img.getWidth(), cim.charImage.baseline);
		if (0 < cim.match.baseline)
			g.drawLine((cim.charImage.img.getWidth() + 1), cim.match.baseline, ((cim.charImage.img.getWidth() + 1) + cim.match.img.getWidth()), cim.match.baseline);
		
		int ciLeft = cim.charImage.box.getLeftCol();
		int ciRight = cim.charImage.box.getRightCol();
		int ciTop = (cim.isCharBoxMatch ? cim.charImage.box.getTopRow() : Math.min(cim.charImage.baseline, cim.charImage.box.getTopRow()));
		int ciBottom = (cim.isCharBoxMatch ? cim.charImage.box.getBottomRow() : Math.max(cim.charImage.baseline, cim.charImage.box.getBottomRow()));
		int mLeft = cim.match.box.getLeftCol();
		int mRight = cim.match.box.getRightCol();
		int mTop = (cim.isCharBoxMatch ? cim.match.box.getTopRow() : Math.min(cim.match.baseline, cim.match.box.getTopRow()));
		int mBottom = (cim.isCharBoxMatch ? cim.match.box.getBottomRow() : Math.max(cim.match.baseline, cim.match.box.getBottomRow()));
		
		//	observe shifts
		ciLeft = (ciLeft - Math.max(-cim.leftShift, 0));
		ciRight = (ciRight + Math.max(-cim.rightShift, 0));
		int ciWidth = (ciRight - ciLeft);
		mLeft = (mLeft - Math.max(cim.leftShift, 0));
		mRight = (mRight + Math.max(cim.rightShift, 0));
		int mWidth = (mRight - mLeft);
		int cimWidth = Math.max(ciWidth, mWidth);
		
		ciTop = (ciTop - Math.max(-cim.topShift, 0));
		ciBottom = (ciBottom + Math.max(-cim.bottomShift, 0));
		int ciHeight = (ciBottom - ciTop);
		mTop = (mTop - Math.max(cim.topShift, 0));
		mBottom = (mBottom + Math.max(cim.bottomShift, 0));
		int mHeight = (mBottom - mTop);
		int cimHeight = Math.max(ciHeight, mHeight);
		
		//	render overlay image
		for (int x = 0;; x++) {
			int ciCol = (ciLeft + ((x * (ciRight - ciLeft)) / cimWidth));
			if (ciCol < 0)
				continue;
			if (ciRight <= ciCol)
				break;
			int mCol = (mLeft + ((x * (mRight - mLeft)) / cimWidth));
			if (mCol < 0)
				continue;
			if (mRight <= mCol)
				break;
			
			for (int y = 0;; y++) {
				int ciRow = (ciTop + ((y * (ciBottom - ciTop)) / cimHeight));
				if (ciRow < 0)
					continue;
				if (ciBottom <= ciRow)
					break;
				int mRow = (mTop + ((y * (mBottom - mTop)) / cimHeight));
				if (mRow < 0)
					continue;
				if (mBottom <= mRow)
					break;
				
//				byte cib = cim.charImage.brightness[ciCol][ciRow];
//				byte mb = cim.match.brightness[mCol][mRow];
				byte cib = (((0 <= ciCol) && (ciCol < cim.charImage.brightness.length) && (0 <= ciRow) && (ciRow < cim.charImage.brightness[ciCol].length)) ? cim.charImage.brightness[ciCol][ciRow] : ((byte) 127));
				byte mb = (((0 <= mCol) && (mCol < cim.match.brightness.length) && (0 <= mRow) && (mRow < cim.match.brightness[mCol].length)) ? cim.match.brightness[mCol][mRow] : ((byte) 127));
				Color c = null;
				if ((cib < 80) && (mb < 80))
					c = Color.BLACK;
				else if (cib < 80)
					c = Color.GREEN;
				else if (mb < 80)
					c = Color.RED;
				if (c != null)
					bi.setRGB(
							(cim.charImage.img.getWidth() + 1 + cim.match.img.getWidth() + 1 + Math.min(ciLeft, mLeft) + Math.max((ciCol - ciLeft), (mCol - mLeft))),
							(Math.min(ciTop, mTop) + Math.max((ciRow - ciTop), (mRow - mTop))),
							c.getRGB()
						);
			}
		}
		
		return bi;
	}
	
	static int displayCharMatch(CharImageMatch cim, String message) {
		return JOptionPane.showConfirmDialog(null, (message + ": '" + cim.match.ch + "', similarity is " + cim.sim + "" +
				"\nmatched " + cim.matched + ", spurious " + cim.spurious + ", missed " + cim.missed +
				"\nx-histogram similarity " + cim.xHistSim + ", y-histogram similarity " + cim.yHistSim +
				"\nascender shift is " + cim.relAscenderShift + ", descender shift is " + cim.relDescenderShift +
				"\n[" + cim.leftShift + ", " + cim.rightShift + ", " + cim.topShift + ", " + cim.bottomShift + "]" +
				""), "Comparison Image", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, new ImageIcon(getCharMatchImage(cim)));
	}
	
	private static final boolean useNonPostscriptChars = false;
	private static final CharSignature[] charSignatures;
	
	private static final byte NEVER = 0;
	private static final byte SOMETIMES = 1;
	private static final byte USUALLY = 2;
	private static final byte ALWAYS = 3;
	
	private static final int charSignatureWidth = 12;
	private static final int charSignatureHeight = 12;
	private static final byte charSignatureMaxDensity = ((byte) 0xF); // this way, we can use one hex digit per signature area
	
	static class CharSignature {
		String data;
		String[] sData = {null, null, null, null, null, null, null, null};
		char ch;
		byte isMultiPart;
		byte[] sIsMultiPart = {-1, -1, -1, -1, -1, -1, -1, -1};
		byte hasLoops;
		byte[] sHasLoops = {-1, -1, -1, -1, -1, -1, -1, -1};
		byte[][] fontBoxSignature;
		byte[][][] sFontBoxSignatures = {null, null, null, null, null, null, null, null};
		float relCharBoxTop;
		float[] sRelCharBoxTops = {-1, -1, -1, -1, -1, -1, -1, -1};
		float relCharBoxBottom;
		float[] sRelCharBoxBottoms = {-1, -1, -1, -1, -1, -1, -1, -1};
		float charBoxProportion;
		float[] sCharBoxProportions = {-1, -1, -1, -1, -1, -1, -1, -1};
		byte[][] charBoxSignature;
		byte[][][] sCharBoxSignatures = {null, null, null, null, null, null, null, null};
		CharSignature(String data, char ch, byte isMultiPart, byte hasLoops, byte[][] fontBoxSignature, float relCharBoxTop, float relCharBoxBottom, float charBoxPropotion, byte[][] charBoxSignature) {
			this.data = data;
			this.ch = ch;
			this.isMultiPart = isMultiPart;
			this.hasLoops = hasLoops;
			this.fontBoxSignature = fontBoxSignature;
			this.relCharBoxTop = relCharBoxTop;
			this.relCharBoxBottom = relCharBoxBottom;
			this.charBoxProportion = charBoxPropotion;
			this.charBoxSignature = charBoxSignature;
		}
		void addStyle(int style, String data, byte isMultiPart, byte hasLoops, byte[][] fontBoxSignature, float relCharBoxTop, float relCharBoxBottom, float charBoxPropotion, byte[][] charBoxSignature) {
			this.sData[style] = data;
			this.sIsMultiPart[style] = isMultiPart;
			this.sHasLoops[style] = hasLoops;
			this.sFontBoxSignatures[style] = fontBoxSignature;
			this.sRelCharBoxTops[style] = relCharBoxTop;
			this.sRelCharBoxBottoms[style] = relCharBoxBottom;
			this.sCharBoxProportions[style] = charBoxPropotion;
			this.sCharBoxSignatures[style] = charBoxSignature;
		}
		
		float getDifference(int style, boolean isMultiPart, boolean hasLoops, byte[][] fontBoxSignature, float relCharBoxTop, float relCharBoxBottom, float charBoxProportion, byte[][] charBoxSignature, boolean debugOutput, char debugChar, Map debugData) {
			return this.getDifference(style,
					isMultiPart, Integer.MAX_VALUE, false,
					hasLoops, Integer.MAX_VALUE, false,
					Integer.MAX_VALUE,
					fontBoxSignature, 0, Float.MAX_VALUE, true,
					relCharBoxTop, Float.MAX_VALUE, false,
					relCharBoxBottom, Float.MAX_VALUE, false,
					Float.MAX_VALUE,
					charBoxProportion, Float.MAX_VALUE, true,
					charBoxSignature, 0, Float.MAX_VALUE, true,
					debugOutput, debugChar, debugData);
		}
		
		float getDifference(int style,
				boolean isMultiPart, int multiPartDiffCutoff, boolean addMultiPartDiff,
				boolean hasLoops, int loopDiffCutoff, boolean addLoopDiff,
				int multiPartLoopDiffCutoff,
				byte[][] fontBoxSignature, int fontBoxDiffLimit, float fontBoxDiffCutoff, boolean addFontBoxDiff,
				float relCharBoxTop, float charBoxTopDiffCutoff, boolean addCharBoxTopDiff,
				float relCharBoxBottom, float charBoxBottomDiffCutoff, boolean addCharBoxBottomDiff,
				float relCharBoxTopBottomDiffCutoff,
				float charBoxProportion, float charBoxPropDiffCutoff, boolean addCharBoxPropDiff,
				byte[][] charBoxSignature, int charBoxDiffLimit, float charBoxDiffCutoff, boolean addCharBoxDiff,
				boolean debugOutput, char debugChar, Map debugData) {
			float difference = 0;
			if (debugOutput || (this.ch == debugChar))
				System.out.println("'" + this.ch + "' (" + Integer.toString(((int) this.ch), 16) + ") signature computing diff:");
			
//			float multiPartDiff = ((float) Math.abs((((style < 0) || (this.sIsMultiPart[style] == -1)) ? this.isMultiPart : this.sIsMultiPart[style]) - (isMultiPart ? ALWAYS : NEVER)));
//			multiPartDiff = ((multiPartDiff * multiPartDiff) / Math.abs(ALWAYS - NEVER));
//			if (debugDiff)
//				System.out.println(" - multi part diff is " + multiPartDiff);
//			difference += multiPartDiff;
			int multiPartDiff = Math.abs((((style < 0) || (this.sIsMultiPart[style] == -1)) ? this.isMultiPart : this.sIsMultiPart[style]) - (isMultiPart ? ALWAYS : NEVER));
			if (debugOutput || (this.ch == debugChar))
				System.out.println(" - multi part diff is " + multiPartDiff + " (|" + (((style < 0) || (this.sIsMultiPart[style] == -1)) ? this.isMultiPart : this.sIsMultiPart[style]) + "-" + (isMultiPart ? ALWAYS : NEVER) + "|)");
			if (debugData != null)
				debugData.put("mpd", new Integer(multiPartDiff));
			if (multiPartDiff >= multiPartDiffCutoff)
				return 8000f;
			if (addMultiPartDiff)
				difference += multiPartDiff;
			
//			float loopDiff = ((float) Math.abs((((style < 0) || (this.sHasLoops[style] == -1)) ? this.hasLoops : this.sHasLoops[style]) - (hasLoops ? ALWAYS : NEVER)));
//			loopDiff = ((loopDiff * loopDiff) / Math.abs(ALWAYS - NEVER));
//			if (debugDiff)
//				System.out.println(" - loop diff is " + loopDiff);
//			difference += loopDiff;
			int loopDiff = Math.abs((((style < 0) || (this.sHasLoops[style] == -1)) ? this.hasLoops : this.sHasLoops[style]) - (hasLoops ? ALWAYS : NEVER));
			if (debugOutput || (this.ch == debugChar))
				System.out.println(" - loop diff is " + loopDiff + " (|" + (((style < 0) || (this.sHasLoops[style] == -1)) ? this.hasLoops : this.sHasLoops[style]) + "-" + (hasLoops ? ALWAYS : NEVER) + "|)");
			if (debugData != null)
				debugData.put("lod", new Integer(loopDiff));
			if (loopDiff >= loopDiffCutoff)
				return 7000f;
			if (addLoopDiff)
				difference += loopDiff;
			if ((multiPartDiff + loopDiff) >= multiPartLoopDiffCutoff)
				return 6000f;
			
			if (fontBoxSignature != null) {// we might not have a font box, and then there measures make no sense, either ...
				float charBoxTopDiff = Math.abs((((style < 0) || (this.sRelCharBoxTops[style] == -1)) ? this.relCharBoxTop : this.sRelCharBoxTops[style]) - relCharBoxTop);
				if (debugData != null)
					debugData.put("cbt", new Float(charBoxTopDiff));
				if (debugOutput || (this.ch == debugChar))
					System.out.println(" - char box top diff is " + charBoxTopDiff);
				if (charBoxTopDiff > charBoxTopDiffCutoff)
					return 5700f;
				if (addCharBoxTopDiff)
					difference += charBoxTopDiff;
				
				float charBoxBottomDiff = Math.abs((((style < 0) || (this.sRelCharBoxBottoms[style] == -1)) ? this.relCharBoxBottom : this.sRelCharBoxBottoms[style]) - relCharBoxBottom);
				if (debugData != null)
					debugData.put("cbb", new Float(charBoxBottomDiff));
				if (debugOutput || (this.ch == debugChar))
					System.out.println(" - char box bottom diff is " + charBoxBottomDiff);
				if (charBoxBottomDiff > charBoxBottomDiffCutoff)
					return 5300f;
				if (addCharBoxBottomDiff)
					difference += charBoxBottomDiff;
			}
			
//			difference += Math.abs(this.charBoxProportion - charBoxPropotion);
			float charBoxPropDiff = (((((style < 0) || (this.sCharBoxProportions[style] == -1)) ? this.charBoxProportion : this.sCharBoxProportions[style]) - charBoxProportion) * ((((style < 0) || (this.sCharBoxProportions[style] == -1)) ? this.charBoxProportion : this.sCharBoxProportions[style]) - charBoxProportion));
			if (debugData != null)
				debugData.put("cbp", new Float(charBoxPropDiff));
			if (debugOutput || (this.ch == debugChar))
				System.out.println(" - char box proportion diff is " + charBoxPropDiff);
			if (debugData != null) {
				debugData.put("cbp-1", new Float(Math.abs(this.charBoxProportion - charBoxProportion)));
				debugData.put("cbp-2", new Float(charBoxPropDiff));
			}
			if (charBoxPropDiff > charBoxPropDiffCutoff)
				return 5000f;
			if (addCharBoxPropDiff)
				difference += charBoxPropDiff;
			
			if (fontBoxSignature != null) {// we might not have a font box ...
				float fontBoxDiff = this.getDifference((((style < 0) || (this.sFontBoxSignatures[style] == null)) ? this.fontBoxSignature : this.sFontBoxSignatures[style]), fontBoxSignature, fontBoxDiffLimit);
				if (debugData != null)
					debugData.put("fbd", new Float(fontBoxDiff));
				if (debugOutput || (this.ch == debugChar))
					System.out.println(" - font box sig diff is " + fontBoxDiff);
				if (debugData != null) {
					for (int l = 0; l <= 8; l++)
						debugData.put(("fbd-" + l), new Float(this.getDifference((((style < 0) || (this.sFontBoxSignatures[style] == null)) ? this.fontBoxSignature : this.sFontBoxSignatures[style]), fontBoxSignature, l)));
				}
				if (fontBoxDiff >= fontBoxDiffCutoff)
					return 4000f;
				if (addFontBoxDiff)
					difference += fontBoxDiff;
			}
			
			float charBoxDiff = this.getDifference((((style < 0) || (this.sCharBoxSignatures[style] == null)) ? this.charBoxSignature : this.sCharBoxSignatures[style]), charBoxSignature, charBoxDiffLimit);
			if (debugData != null)
				debugData.put("cbd", new Float(charBoxDiff));
			if (debugOutput || (this.ch == debugChar))
				System.out.println(" - char box sig diff is " + charBoxDiff);
			if (debugData != null) {
				for (int l = 0; l <= 8; l++)
					debugData.put(("cbd-" + l), new Float(this.getDifference((((style < 0) || (this.sCharBoxSignatures[style] == null)) ? this.charBoxSignature : this.sCharBoxSignatures[style]), charBoxSignature, l)));
			}
			if (charBoxDiff >= charBoxDiffCutoff)
				return 3000f;
			if (addCharBoxDiff)
				difference += charBoxDiff;
			if (debugOutput || (this.ch == debugChar))
				System.out.println(" ==> diff is " + difference);
			return difference;
		}
		
		private float getDifference(byte[][] charSignature1, byte[][] charSignature2, int threshold) {
			int columnOffset1 = ((charSignature1.length < charSignature2.length) ? ((charSignature2.length - charSignature1.length) / 2) : 0);
			int columnOffset2 = ((charSignature2.length < charSignature1.length) ? ((charSignature1.length - charSignature2.length) / 2) : 0);
			int cellDifferenceSum = 0;
			for (int c = 0; c < Math.max(charSignature1.length, charSignature2.length); c++) {
				byte[] column1 = (((c < columnOffset1) || ((c - columnOffset1) >= charSignature1.length)) ? null : charSignature1[c - columnOffset1]);
				byte[] column2 = (((c < columnOffset2) || ((c - columnOffset2) >= charSignature2.length)) ? null : charSignature2[c - columnOffset2]);
				for (int r = 0; r < Math.min(charSignature1[0].length, charSignature2[0].length); r++) {
					int cellDifference = (((column1 == null) ? 0 : column1[r]) - ((column2 == null) ? 0 : column2[r]));
					if (Math.abs(cellDifference) > threshold)
						cellDifferenceSum += (cellDifference * cellDifference); // we square this here to penalize large differences harder
				}
			}
			return (((float) cellDifferenceSum) / (Math.min(charSignature1.length, charSignature2.length) * Math.min((charSignature1[0].length - this.countEmptyRows(charSignature1)), (charSignature2[0].length - this.countEmptyRows(charSignature2))) * (charSignatureMaxDensity + 1)));
		}
		private float countEmptyRows(byte[][] charSignature) {
			int emptyRows = 0;
			for (int r = 0; r < charSignature[0].length; r++) {
				boolean rowEmpty = true;
				for (int c = 0; c < charSignature.length; c++)
					if (charSignature[c][r] != 0) {
						rowEmpty = false;
						break;
					}
				if (rowEmpty)
					emptyRows++;
			}
			return emptyRows;
		}
	}
	
	static class CharImage {
		final char ch;
		final String fontName;
		final int fontStyle;
		final BufferedImage img;
		final byte[][] brightness;
		final ImagePartRectangle box;
		final int baseline;
		final short[] xHistogram;
		final short xHistogramMax;
		final byte xHistogramPeaks25;
		final byte xHistogramPeaks33;
		final byte xHistogramPeaks50;
		final byte xHistogramPeaks67;
		final byte xHistogramPeaks75;
		final short[] yHistogram;
		final short yHistogramMax;
		final byte yHistogramPeaks25;
		final byte yHistogramPeaks33;
		final byte yHistogramPeaks50;
		final byte yHistogramPeaks67;
		final byte yHistogramPeaks75;
		CharImage(BufferedImage img, int baseline) {
			this(((char) 0), "", -1, Imaging.wrapImage(img, null), baseline);
		}
		CharImage(char ch, String fontName, int fontStyle, BufferedImage img, int baseline) {
			this(ch, fontName, fontStyle, Imaging.wrapImage(img, (ch + "-" + fontName + "-" + fontStyle)), baseline);
		}
		CharImage(char ch, String fontName, int fontStyle, AnalysisImage ai, int baseline) {
			this.ch = ch;
			this.fontName = fontName;
			this.fontStyle = fontStyle;
			this.img = ai.getImage();
			this.brightness = ai.getBrightness();
			this.box = Imaging.getContentBox(ai);
			this.baseline = baseline;
			
			//	compute histograms
			byte[][] imgBrightness = ai.getBrightness();
			this.xHistogram = new short[this.img.getWidth()];
			Arrays.fill(this.xHistogram, ((short) 0));
			this.yHistogram = new short[this.img.getHeight()];
			Arrays.fill(this.yHistogram, ((short) 0));
			for (int c = 0; c < imgBrightness.length; c++) {
				for (int r = 0; r < imgBrightness[c].length; r++)
					if (imgBrightness[c][r] < 80) {
						this.xHistogram[c]++;
						this.yHistogram[r]++;
					}
			}
			
			//	compute histogram maximums (for normalization)
			int xHistMax = 0;
			for (int c = 0; c < this.xHistogram.length; c++)
				xHistMax = Math.max(xHistMax, this.xHistogram[c]);
			this.xHistogramMax = ((short) xHistMax);
			int yHistMax = 0;
			for (int r = 0; r < this.yHistogram.length; r++)
				yHistMax = Math.max(yHistMax, this.yHistogram[r]);
			this.yHistogramMax = ((short) yHistMax);
			
			//	count histogram peaks (passes through 25%, 33%, 50%, 67%, and 75% of box height/width)
			int yp25 = 0;
			int yp33 = 0;
			int yp50 = 0;
			int yp67 = 0;
			int yp75 = 0;
			for (int c = 0; c <= this.xHistogram.length; c++) {
				int py = ((c == 0) ? 0 : this.xHistogram[c-1]);
				int y = ((c == this.xHistogram.length) ? 0 : this.xHistogram[c]);
				if ((py < ((1 * this.box.getHeight()) / 4)) != (y < ((1 * this.box.getHeight()) / 4)))
					yp25++;
				if ((py < ((1 * this.box.getHeight()) / 3)) != (y < ((1 * this.box.getHeight()) / 3)))
					yp33++;
				if ((py < ((1 * this.box.getHeight()) / 2)) != (y < ((1 * this.box.getHeight()) / 2)))
					yp50++;
				if ((py < ((2 * this.box.getHeight()) / 3)) != (y < ((2 * this.box.getHeight()) / 3)))
					yp67++;
				if ((py < ((3 * this.box.getHeight()) / 4)) != (y < ((3 * this.box.getHeight()) / 4)))
					yp75++;
			}
			this.xHistogramPeaks25 = ((byte) (yp25 / 2));
			this.xHistogramPeaks33 = ((byte) (yp33 / 2));
			this.xHistogramPeaks50 = ((byte) (yp50 / 2));
			this.xHistogramPeaks67 = ((byte) (yp67 / 2));
			this.xHistogramPeaks75 = ((byte) (yp75 / 2));
			int xp25 = 0;
			int xp33 = 0;
			int xp50 = 0;
			int xp67 = 0;
			int xp75 = 0;
			for (int r = 0; r <= this.yHistogram.length; r++) {
				int px = ((r == 0) ? 0 : this.yHistogram[r-1]);
				int x = ((r == this.yHistogram.length) ? 0 : this.yHistogram[r]);
				if ((px < ((1 * this.box.getWidth()) / 4)) != (x < ((1 * this.box.getWidth()) / 4)))
					xp25++;
				if ((px < ((1 * this.box.getWidth()) / 3)) != (x < ((1 * this.box.getWidth()) / 3)))
					xp33++;
				if ((px < ((1 * this.box.getWidth()) / 2)) != (x < ((1 * this.box.getWidth()) / 2)))
					xp50++;
				if ((px < ((2 * this.box.getWidth()) / 3)) != (x < ((2 * this.box.getWidth()) / 3)))
					xp67++;
				if ((px < ((3 * this.box.getWidth()) / 4)) != (x < ((3 * this.box.getWidth()) / 4)))
					xp75++;
			}
			this.yHistogramPeaks25 = ((byte) (xp25 / 2));
			this.yHistogramPeaks33 = ((byte) (xp33 / 2));
			this.yHistogramPeaks50 = ((byte) (xp50 / 2));
			this.yHistogramPeaks67 = ((byte) (xp67 / 2));
			this.yHistogramPeaks75 = ((byte) (xp75 / 2));
		}
	}
	
	private static float charImageRenderingFontSize = 96.0f;
	private static HashSet unrenderableChars = new HashSet();
	
	static CharImage createCharImage(char ch, Font font, boolean isSerifFont, boolean isMonospacedFont, HashMap cache, boolean debug) {
		if (debug) System.out.println("Rendering char '" + ch + "' in " + font.getName() + "-" + font.getStyle());
		if (!font.canDisplay(ch)) {
			if (debug) System.out.println(" ==> font cannot display char");
			return null;
		}
		if (ch == 160) {
			if (debug) System.out.println(" ==> space, ignored");
			return null;
		}
		String charKey = (ch + "-in-" + font.getName() + "-" + font.getStyle());
		if (debug) System.out.println(" - char key is " + charKey);
		if (unrenderableChars.contains(charKey)) {
			if (debug) System.out.println(" ==> known to be unrenderable");
			return null;
		}
		
		//	try cache lookup
		String cacheKey = null;
		if (cache != null) {
			cacheKey = (ch + "-in-" + font.getName() + "-" + font.getStyle());
			if (debug) System.out.println(" - cache key is " + cacheKey);
			CharImage ci = ((CharImage) cache.get(cacheKey));
			if (ci != null) {
//				if (DEBUG_LOAD_FONTS) System.out.println(" - cache hit for " + cacheKey);
				if (debug) System.out.println(" ==> cache hit");
				return ci;
			}
		}
		
		//	normalize font size and get maximum char bounds
		if (font.getSize() != charImageRenderingFontSize)
			font = font.deriveFont(charImageRenderingFontSize);
		Rectangle2D fontBox = getFontBox(font);
		if (debug) System.out.println(" - font box is " + fontBox);
		
		//	create char image
		BufferedImage cbi = new BufferedImage(((int) Math.round(fontBox.getWidth() + 2)), ((int) Math.round(fontBox.getHeight() + 2)), BufferedImage.TYPE_BYTE_BINARY);
		if (debug) System.out.println(" - image size is " + cbi.getWidth() + "x" + cbi.getHeight());
		Graphics2D cgr = cbi.createGraphics();
		cgr.setColor(Color.WHITE);
		cgr.fillRect(0, 0, cbi.getWidth(), cbi.getHeight());
		cgr.setFont(font);
		cgr.setColor(Color.BLACK);
//		System.out.println("Drawing '" + ch + "' in " + font.getName() + "-" + font.getStyle());
		TextLayout tl = new TextLayout(("" + ch), font, cgr.getFontRenderContext());
		int cbl = Math.round(tl.getAscent() + 1);
		if (debug) System.out.println(" - baseline is " + cbl);
		cgr.drawString(("" + ch), ((int) (Math.round(fontBox.getWidth() - tl.getBounds().getWidth() + 1) / 2)), cbl);
		cgr.dispose();
		if (debug) System.out.println(" - char image rendered");
		CharImage ci = new CharImage(ch, font.getName(), (font.getStyle() | (isSerifFont ? SERIF : 0) | (isMonospacedFont ? MONOSPACED : 0)), cbi, cbl);
		if (debug) JOptionPane.showMessageDialog(null, "", ("'" + ch + "' in " + font.getName() + "-" + font.getStyle()), JOptionPane.PLAIN_MESSAGE, new ImageIcon(cbi));
		
		//	cache image if possible and return it
		if (cache != null)
			cache.put(cacheKey, ci);
		return ci;
	}
	
	private static Rectangle2D getFontBox(Font font) {
		Graphics2D gr = (new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY)).createGraphics();
		gr.setFont(font);
		return font.getMaxCharBounds(gr.getFontRenderContext());
	}
	
	static {
		LinkedList csList = new LinkedList();
		
		try {
			String csResourceName = PdfCharDecoder.class.getName();
			csResourceName = csResourceName.substring(0, csResourceName.lastIndexOf('.'));
			csResourceName = (csResourceName.replaceAll("\\.", "/") + "/PdfCharSignatures." + (SERIF_IS_STYLE ? 8 : 4) + ".txt");
			System.out.println(csResourceName);
			BufferedReader csBr = new BufferedReader(new InputStreamReader(PdfCharDecoder.class.getClassLoader().getResourceAsStream(csResourceName), "UTF-8"));
			String chHex = null;
			CharSignature chSig = null;
			for (String csLine; (csLine = csBr.readLine()) != null;) {
				if (csLine.startsWith("//"))
					continue;
				String[] csData = csLine.split("\\t");
				if (csData[0].matches("FE[56][0-9A-Fa-f]")) // small forms, let's ignore those for now
					continue;
				if (";;;;;;;;;;;".equals(csData[4]) || ";;;;;;;;;;;".equals(csData[6]))
					continue;
				if ("-1".equals(csData[1])) {
					char ch = ((char) Integer.parseInt(csData[0], 16));
					if (useNonPostscriptChars || (StringUtils.getCharName(ch) != null)) {
						chHex = csData[0];
						chSig = new CharSignature(csLine, ch, Byte.parseByte(csData[2]), Byte.parseByte(csData[3]), parseSignature(csData[4]), Float.parseFloat(csData[5]), Float.parseFloat(csData[6]), Float.parseFloat(csData[7]), parseSignature(csData[8]));
						csList.add(chSig);
					}
					else {
						chHex = null;
						chSig = null;
					}
				}
				else if ((chHex != null) && chHex.equals(csData[0]) && (chSig != null))
					chSig.addStyle(Integer.parseInt(csData[1]), csLine, Byte.parseByte(csData[2]), Byte.parseByte(csData[3]), parseSignature(csData[4]), Float.parseFloat(csData[5]), Float.parseFloat(csData[6]), Float.parseFloat(csData[7]), parseSignature(csData[8]));
			}
			csBr.close();
			System.out.println("PdfCharDecoder loaded " + csList.size() + " char signatures");
		}
		catch (IOException ioe) {
			System.out.println("PdfCharDecoder could not load char signatures: " + ioe.getMessage());
			ioe.printStackTrace(System.out);
		}
		
		charSignatures = ((CharSignature[]) csList.toArray(new CharSignature[csList.size()]));
	}
	
	private static byte[][] parseSignature(String sigData) {
		String[] sigRows = sigData.split("\\;");
		byte[][] sig = new byte[sigRows[0].length()][sigRows.length];
		for (int c = 0; c < sig.length; c++) {
			for (int r = 0; r < sigRows.length; r++)
				sig[c][r] = ((byte) Integer.parseInt(sigRows[r].substring(c, (c+1)), 16));
		}
		return sig;
	}
	
	//	TODO treat superscript digits as such
	
	private static final String[] classifiedUnicodeBlocks = {
//		"0000;007F;Latin, Common", // Basic Latin[g] (cut control characters)
//			"0020;007E;Latin, Common", // Basic Latin[g] (split up to distinguish letters from numbers from punctuation marks)
			"0021;002F;P", // Basic Latin[g] 
			"0030;0039;D", // Basic Latin[g] 
			"003A;0040;P", // Basic Latin[g] 
			"0041;005A;L", // Basic Latin[g] 
			"005B;0060;P", // Basic Latin[g] 
			"0061;007A;L", // Basic Latin[g] 
			"007B;007E;P", // Basic Latin[g] 
//		"0080;00FF;Latin, Common", // Latin-1 Supplement[h] (cut control block, which renders on Windows machines)
//			"00A0;00FF;Latin, Common", // Latin-1 Supplement[h] (split up to distinguish letters from numbers from punctuation marks, and cut soft hyphen (u00AD) as indistinguishable from hyphen proper)
			"00A1;00AC;P", // Latin-1 Supplement[h] 
			"00AE;00BF;P", // Latin-1 Supplement[h] 
			"00C0;00FF;L", // Latin-1 Supplement[h] 
//		"0100;017F;L", // Latin Extended-A (cut Latin small letter long s (u017f), which is extremely rare and very similar to 'f', as well as dot-less i (ui0131))
			"0100;017E;L", // Latin Extended-A
//		"0180;024F;L", // Latin Extended-B (split up to cut out tone six letters, which are very rare in Latin scripts, and very similar to 'b', cut florin / f with hook (u0192) as indistinguishable from italics f in serif fonts, cut dental click (u01C0) as indistinguishable from l in sans-serif fonts; cut dental, lateral, alveolar, and retroflex clicks (u01C0, u01C1, u01C2, and u01C3) as virtually indistinguishable from 1, I, l, exclamation mark, etc.)
			"0180;0183;L", // Latin Extended-B
			"0186;0191;L", // Latin Extended-B
			"0193;01BA;L", // Latin Extended-B
			"01BB;01BB;D", // Latin Extended-B ("letter" two with stroke)
			"01BC;01BF;L", // Latin Extended-B
			"01C4;024F;L", // Latin Extended-B
//		"0250;02AF;Latin", // IPA Extensions (no need to cover phonetic characters)
//		"02B0;02FF;Latin, Common", // Spacing Modifier Letters 
//		"0300;036F;L", // Combining Diacritical Marks (split up to remove combining diacritic strokes, which are extremely rare and virtually indistinguishable from dashes, short and long combining solidus overlay (u0337 and u0338) as indistinguishable from slash, grave and acute tone marks (u0340 and u0341), which are indistinguishable from respective accents, and legacy Greet perispomeni and other accents (u342, u343, u344, u345), which is indistinguishable from other accents still in use in Latin based alphabets)
			"0300;0334;L", // Combining Diacritical Marks 
			"0339;033F;L", // Combining Diacritical Marks 
			"0346;036F;L", // Combining Diacritical Marks 
//		"0370;03FF;G", // Greek and Coptic (split up to remove heta, sampi, Pamphylian digamma, lunate sigma, which are historical and closely resemble 'I', 'T', reversed 'N', and 'c', respectively; cut blank codes and tonos diacritics; cut further archaic Greek symbols; cut Greek question mark, which looks just like ';')
			"0391;03D7;G", // Greek and Coptic 
			"03E2;03EF;G", // Greek and Coptic 
			"03FC;03FC;G", // Greek and Coptic 
		"0400;04FF;C", // Cyrillic 
		"0500;052F;C", // Cyrillic Supplement 
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
		"1E00;1EFF;L", // Latin Extended Additional 
		"1F00;1FFF;G", // Greek Extended 
//		"2000;206F;Common, Inherited", // General Punctuation (cut special spaces, split to exclude per-tenthousand character (u2031), cut off various special spaces (u206x), cut out hyphend (u2010 and u2011) as indistinguishable from ASCII counterpart, cut out low left single quotation mark (u201A) as indistinguishable from comma, cut out one dot enleader (u2024) as indistinguishable from dot/period), cut out fraction slash (u2044) as indistinguishable from slash)
			"2012;2019;P", // General Punctuation 
			"201B;2023;P", // General Punctuation 
			"2025;2027;P", // General Punctuation 
			"2030;2030;P", // General Punctuation 
			"2032;2043;P", // General Punctuation 
			"2045;205E;P", // General Punctuation 
//		"2070;209F;Latin, Common", // Superscripts and Subscripts 
//		"20A0;20CF;Common", // Currency Symbols 
//		"20D0;20FF;Latin", // Combining Diacritical Marks for Symbols 
//		"2100;214F;Latin, Greek, Common", // Letterlike Symbols
//		"2150;218F;Latin, Common", // Number Forms (split to exclude Roman numerals (u2160-u217F))
//			"2150;215F;Latin, Common", // Number Forms 
//			"2180;218F;Latin, Common", // Number Forms 
//		"2190;21FF;Common", // Arrows (split and cut to exclude extremely exotic arrows)
//			"2190;2199;P", // Arrows (removed for now as very unlikely to occur in publication)
//			"21D0;21D9;P", // Arrows (removed for now as very unlikely to occur in publication)
//		"2200;22FF;P", // Mathematical Operators (removed for now as very unlikely to occur in publication)
//		"2300;23FF;P", // Miscellaneous Technical (removed for now as very unlikely to occur in publication)
//		"2400;243F;Common", // Control Pictures 
//		"2440;245F;Common", // Optical Character Recognition 
//		"2460;24FF;Common", // Enclosed Alphanumerics 
//		"2500;257F;Common", // Box Drawing 
//		"2580;259F;Common", // Block Elements 
//		"25A0;25FF;Common", // Geometric Shapes 
//		"2600;26FF;P", // Miscellaneous Symbols (cut down to gender and planetary symbols for now)
			"263F;2647;P", // Miscellaneous Symbols
//		"2700;27BF;Common", // Dingbats 
//		"27C0;27EF;P", // Miscellaneous Mathematical Symbols-A (removed for now as very unlikely to occur in publication)
//		"27F0;27FF;Common", // Supplemental Arrows-A 
//		"2800;28FF;Braille", // Braille Patterns 
//		"2900;297F;P", // Supplemental Arrows-B (removed for now as very unlikely to occur in publication)
//		"2980;29FF;P", // Miscellaneous Mathematical Symbols-B (removed for now as very unlikely to occur in publication)
//		"2A00;2AFF;P", // Supplemental Mathematical Operators (removed for now as very unlikely to occur in publication)
//		"2B00;2BFF;P", // Miscellaneous Symbols and Arrows (removed for now as very unlikely to occur in publication)
//		"2C00;2C5F;Glagolitic", // Glagolitic 
		"2C60;2C7F;L", // Latin Extended-C 
//		"2C80;2CFF;Coptic", // Coptic 
//		"2D00;2D2F;Georgian", // Georgian Supplement 
//		"2D30;2D7F;Tifinagh", // Tifinagh 
//		"2D80;2DDF;Ethiopic", // Ethiopic Extended 
		"2DE0;2DFF;C", // Cyrillic Extended-A 
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
		"A640;A69F;C", // Cyrillic Extended-B 
//		"A6A0;A6FF;Bamum", // Bamum 
//		"A700;A71F;Common", // Modifier Tone Letters 
		"A720;A7FF;L", // Latin Extended-D 
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
		"AB30;AB6F;L", // Latin Extended-E 
//		"AB70;ABBF;Cherokee", // Cherokee 
//		"ABC0;ABFF;Meetei Mayek", // Meetei Mayek 
//		"AC00;D7AF;Hangul", // Hangul Syllables 
//		"D7B0;D7FF;Hangul", // Hangul Jamo Extended-B 
//		"D800;DB7F;", // High Surrogates 
//		"DB80;DBFF;", // High Private Use Surrogates 
//		"DC00;DFFF;", // Low Surrogates 
//		"E000;F8FF;", // Private Use Area 
//		"F900;FAFF;Han", // CJK Compatibility Ideographs 
//		"FB00;FB4F;Latin, Hebrew, Armenian", // Alphabetic Presentation Forms (cut down to Latin)
			"FB00;FB0F;L", // Alphabetic Presentation Forms 
//		"FB50;FDFF;Arabic, Common", // Arabic Presentation Forms-A 
//		"FE00;FE0F;Inherited", // Variation Selectors 
//		"FE10;FE1F;Common", // Vertical Forms 
//		"FE20;FE2F;Inherited", // Combining Half Marks 
//		"FE30;FE4F;Common", // CJK Compatibility Forms 
//		"FE50;FE6F;Common", // Small Form Variants 
//		"FE70;FEFF;Arabic, Common", // Arabic Presentation Forms-B 
//		"FF00;FFEF;Latin, Katakana, Hangul, Common", // Halfwidth and fullwidth forms 
//		"FFF0;FFFF;Common", // Specials 
	};
	
	private static HashMap charsToScripts = new HashMap();
	static {
		
		//	set up cache for character ranges in each script
		for (int b = 0; b < classifiedUnicodeBlocks.length; b++) {
			String[] blockData = classifiedUnicodeBlocks[b].split("\\;");
			int low = Integer.parseInt(blockData[0], 16);
			int high = Integer.parseInt(blockData[1], 16);
			String charClass = blockData[2];
			for (int c = low; c <= high; c++)
				charsToScripts.put(new Integer(c), charClass);
		}
	}
	static String getCharClass(char ch) {
		String charClass = ((String) charsToScripts.get(new Integer((int) ch)));
		return ((charClass == null) ? "U" : charClass);
	}
	
	/* Sub classes for 'P':
	 * - 'g' for general (period, comma, etc.), the default
	 * - 'l' for letters (quotation marks, u0022, u0027, u00AB, u00BB, u2018-u201F)
	 * - 'd' for digits (angle brackets, plus/minus, degree, currency symbols)
	 */
	
	private static final String[] punctuationBlocks = {
//		"0000;007F;Latin, Common", // Basic Latin[g] (cut control characters)
//			"0020;007E;Latin, Common", // Basic Latin[g] (split up to distinguish letters from numbers from punctuation marks)
			"0021;0022;l", // Basic Latin[g] 
			"0024;0025;d", // Basic Latin[g] 
			"003C;003E;d", // Basic Latin[g] 
			"005E;005E;l", // Basic Latin[g] 
			"0060;0060;l", // Basic Latin[g] 
//		"0080;00FF;Latin, Common", // Latin-1 Supplement[h] (cut control block, which renders on Windows machines)
//			"00A0;00FF;Latin, Common", // Latin-1 Supplement[h] (split up to distinguish letters from numbers from punctuation marks)
			"00A1;00A1;q", // Latin-1 Supplement[h] (inverted exclamation mark)
			"00A2;00A5;d", // Latin-1 Supplement[h] 
			"00A8;00A8;l", // Latin-1 Supplement[h] 
			"00AA;00AA;d", // Latin-1 Supplement[h] 
			"00AB;00AB;q", // Latin-1 Supplement[h] (double left-pointing (opening) angle bracket quoter)
			"00AF;00AF;l", // Latin-1 Supplement[h] 
			"00B0;00B1;d", // Latin-1 Supplement[h] 
			"00B4;00B4;l", // Latin-1 Supplement[h] 
			"00B8;00B8;l", // Latin-1 Supplement[h] 
			"00BA;00BA;d", // Latin-1 Supplement[h] 
			"00BB;00BB;Q", // Latin-1 Supplement[h] (double right-pointing angle (closing) bracket quoter)
			"00BF;00BF;q", // Latin-1 Supplement[h] (inverted question mark)
//		"2000;206F;Common, Inherited", // General Punctuation (split to exclude per-tenthousand character (u2031), and cut off various special spaces (u206x))
//			"2018;201F;l", // General Punctuation 
			"2018;2018;q", // General Punctuation (single high left (opening) quoter)
			"2019;2019;Q", // General Punctuation (single high right (closing) quoter)
			"201A;201A;q", // General Punctuation (single low left (opening) quoter)
			"201B;201B;q", // General Punctuation (single high reversed left (opening) quoter)
			"201C;201C;q", // General Punctuation (double high left (opening) quoter)
			"201D;201D;Q", // General Punctuation (double high right (closing) quoter)
			"201E;201E;q", // General Punctuation (double low left (opening) quoter) 
			"201F;201F;q", // General Punctuation (double high reversed left (opening) quoter)
			"2030;2030;d", // General Punctuation 
			"2032;2037;d", // General Punctuation 
//			"2039;203A;l", // General Punctuation 
			"2039;2039;q", // General Punctuation (single left-pointing (opening) angle bracket quoter)
			"203A;203A;Q", // General Punctuation (single right-pointing angle (closing) bracket quoter)
			"203C;2051;l", // General Punctuation 
			"2052;2053;d", // General Punctuation 
			"2054;205E;l", // General Punctuation 
//		"2070;209F;Latin, Common", // Superscripts and Subscripts 
//		"20A0;20CF;Common", // Currency Symbols 
//		"20D0;20FF;Latin", // Combining Diacritical Marks for Symbols 
//		"2100;214F;Latin, Greek, Common", // Letterlike Symbols
//		"2150;218F;Latin, Common", // Number Forms (split to exclude Roman numerals (u2160-u217F))
//			"2150;215F;Latin, Common", // Number Forms 
//			"2180;218F;Latin, Common", // Number Forms 
//		"2190;21FF;Common", // Arrows (split and cut to exclude extremely exotic arrows)
//			"2190;2199;P", // Arrows (removed for now as very unlikely to occur in publication)
//			"21D0;21D9;P", // Arrows (removed for now as very unlikely to occur in publication)
//		"2200;22FF;P", // Mathematical Operators (removed for now as very unlikely to occur in publication)
//		"2300;23FF;P", // Miscellaneous Technical (removed for now as very unlikely to occur in publication)
//		"27C0;27EF;P", // Miscellaneous Mathematical Symbols-A (removed for now as very unlikely to occur in publication)
//		"2900;297F;P", // Supplemental Arrows-B (removed for now as very unlikely to occur in publication)
//		"2980;29FF;P", // Miscellaneous Mathematical Symbols-B (removed for now as very unlikely to occur in publication)
//		"2A00;2AFF;P", // Supplemental Mathematical Operators (removed for now as very unlikely to occur in publication)
//		"2B00;2BFF;P", // Miscellaneous Symbols and Arrows (removed for now as very unlikely to occur in publication)
	};
	
	private static HashMap punctToClass = new HashMap();
	static {
		
		//	set up cache for character ranges in each script
		for (int b = 0; b < punctuationBlocks.length; b++) {
			String[] blockData = punctuationBlocks[b].split("\\;");
			int low = Integer.parseInt(blockData[0], 16);
			int high = Integer.parseInt(blockData[1], 16);
			String punctClass = blockData[2];
			for (int c = low; c <= high; c++)
				punctToClass.put(new Integer(c), punctClass);
		}
	}
	static String getPunctuationClass(char ch) {
		String punctClass = ((String) punctToClass.get(new Integer((int) ch)));
		return ((punctClass == null) ? "g" : punctClass);
	}
	
	//	TODO think of more such pairs
	private static String[] specialPunctuationPairs = {
		":/", // for URLs
	};
	private static Set specialPunctuationPairSet = new HashSet(Arrays.asList(specialPunctuationPairs));
	
	static boolean isSpecialPunctuationPair(char char1, char char2) {
		return specialPunctuationPairSet.contains(char1 + "" + char2);
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
	
	static final String COMBINABLE_ACCENTS;
	static final HashMap COMBINABLE_ACCENT_MAPPINGS = new HashMap();
	static {
		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u005E'), "circumflex");
		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u0060'), "grave");
		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u00A8'), "dieresis");
		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u00AF'), "macron");
		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u00B4'), "acute");
		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u00B8'), "cedilla");
		
		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u02C6'), "circumflex"); // 0x88 in Windows-1252 encoding
		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u02C7'), "caron");
		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u02D8'), "breve");
		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u02D9'), "dot");
		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u02DA'), "ring");
		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u02DB'), "ogonek");
		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u02DC'), "tilde"); // 0x98 in Windows-1252 encoding
		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u02DD'), "dblacute");
		
		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u0300'), "grave");
		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u0301'), "acute");
		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u0302'), "circumflex");
		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u0303'), "tilde");
		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u0304'), "macron");
		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u0306'), "breve");
		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u0307'), "dot");
		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u0308'), "dieresis");
		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u0309'), "hook");
		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u030A'), "ring");
		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u030B'), "dblacute");
		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u030F'), "dblgrave");
		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u030C'), "caron");
		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u0323'), "dotbelow");
		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u0327'), "cedilla");
		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u0328'), "ogonek");
		
		StringBuffer combinableAccentCollector = new StringBuffer();
		ArrayList combinableAccents = new ArrayList(COMBINABLE_ACCENT_MAPPINGS.keySet());
		for (int c = 0; c < combinableAccents.size(); c++) {
			Character combiningChar = ((Character) combinableAccents.get(c));
			combinableAccentCollector.append(combiningChar.charValue());
			String charName = ((String) COMBINABLE_ACCENT_MAPPINGS.get(combiningChar));
			char baseChar = StringUtils.getCharForName(charName);
			if ((baseChar > 0) && (baseChar != combiningChar.charValue())) {
				combinableAccentCollector.append(baseChar);
				COMBINABLE_ACCENT_MAPPINGS.put(new Character(baseChar), charName);
			}
		}
		COMBINABLE_ACCENTS = combinableAccentCollector.toString();
	}
	
	static boolean isCombiningAccent(char ch) {
		return (COMBINABLE_ACCENTS.indexOf(ch) != -1);
	}
	
	static String getCombinedCharName(char ch, char cmbAccCh) {
		return (StringUtils.getBaseChar(ch) + "" + COMBINABLE_ACCENT_MAPPINGS.get(new Character(cmbAccCh)));
	}
	
	static char getCombinedChar(char ch, char cmbAccCh) {
		return StringUtils.getCharForName(getCombinedCharName(ch, cmbAccCh));
	}
	
	static char getNonCombiningChar(char ch) {
		String charName = StringUtils.getCharName(ch);
		if (charName == null)
			return ch;
		if (charName.endsWith("cmb")) {
			char ncCh = StringUtils.getCharForName(charName.substring(0, (charName.length() - "cmb".length())));
			return ((ncCh == 0) ? ch : ncCh);
		}
		else if (charName.endsWith("comb")) {
			char ncCh = StringUtils.getCharForName(charName.substring(0, (charName.length() - "comb".length())));
			return ((ncCh == 0) ? ch : ncCh);
		}
		else return ch;
	}
	
	//	extrated from http://en.wikipedia.org/wiki/Unicode_block
	private static final String[] allUnicodeBlocks = {
//		"0000;007F;Basic Latin",
		"0020;007E;Basic Latin", // removed control characters
//		"0080;00FF;Latin-1 Supplement",
		"00A0;00FF;Latin-1 Supplement", // removed control characters
		"0100;017F;Latin Extended-A",
		"0180;024F;Latin Extended-B",
		"0250;02AF;IPA Extensions",
		"02B0;02FF;Spacing Modifier Letters",
		"0300;036F;Combining Diacritical Marks",
		"0370;03FF;Greek and Coptic",
		"0400;04FF;Cyrillic",
		"0500;052F;Cyrillic Supplement",
		"0530;058F;Armenian",
		"0590;05FF;Hebrew",
		"0600;06FF;Arabic",
		"0700;074F;Syriac",
		"0750;077F;Arabic Supplement",
		"0780;07BF;Thaana",
		"07C0;07FF;NKo",
		"0800;083F;Samaritan",
		"0840;085F;Mandaic",
		"08A0;08FF;Arabic Extended-A",
		"0900;097F;Devanagari",
		"0980;09FF;Bengali",
		"0A00;0A7F;Gurmukhi",
		"0A80;0AFF;Gujarati",
		"0B00;0B7F;Oriya",
		"0B80;0BFF;Tamil",
		"0C00;0C7F;Telugu",
		"0C80;0CFF;Kannada",
		"0D00;0D7F;Malayalam",
		"0D80;0DFF;Sinhala",
		"0E00;0E7F;Thai",
		"0E80;0EFF;Lao",
		"0F00;0FFF;Tibetan",
		"1000;109F;Myanmar",
		"10A0;10FF;Georgian",
		"1100;11FF;Hangul Jamo",
		"1200;137F;Ethiopic",
		"1380;139F;Ethiopic Supplement",
		"13A0;13FF;Cherokee",
		"1400;167F;Unified Canadian Aboriginal Syllabics",
		"1680;169F;Ogham",
		"16A0;16FF;Runic",
		"1700;171F;Tagalog",
		"1720;173F;Hanunoo",
		"1740;175F;Buhid",
		"1760;177F;Tagbanwa",
		"1780;17FF;Khmer",
		"1800;18AF;Mongolian",
		"18B0;18FF;Unified Canadian Aboriginal Syllabics Extended",
		"1900;194F;Limbu",
		"1950;197F;Tai Le",
		"1980;19DF;New Tai Lue",
		"19E0;19FF;Khmer Symbols",
		"1A00;1A1F;Buginese",
		"1A20;1AAF;Tai Tham",
		"1AB0;1AFF;Combining Diacritical Marks Extended",
		"1B00;1B7F;Balinese",
		"1B80;1BBF;Sundanese",
		"1BC0;1BFF;Batak",
		"1C00;1C4F;Lepcha",
		"1C50;1C7F;Ol Chiki",
		"1C80;1C8F;Cyrillic Extended-C",
		"1CC0;1CCF;Sundanese Supplement",
		"1CD0;1CFF;Vedic Extensions",
		"1D00;1D7F;Phonetic Extensions",
		"1D80;1DBF;Phonetic Extensions Supplement",
		"1DC0;1DFF;Combining Diacritical Marks Supplement",
		"1E00;1EFF;Latin Extended Additional",
		"1F00;1FFF;Greek Extended",
		"2000;206F;General Punctuation",
		"2070;209F;Superscripts and Subscripts",
		"20A0;20CF;Currency Symbols",
		"20D0;20FF;Combining Diacritical Marks for Symbols",
		"2100;214F;Letterlike Symbols",
		"2150;218F;Number Forms",
		"2190;21FF;Arrows",
		"2200;22FF;Mathematical Operators",
		"2300;23FF;Miscellaneous Technical",
		"2400;243F;Control Pictures",
		"2440;245F;Optical Character Recognition",
		"2460;24FF;Enclosed Alphanumerics",
		"2500;257F;Box Drawing",
		"2580;259F;Block Elements",
		"25A0;25FF;Geometric Shapes",
		"2600;26FF;Miscellaneous Symbols",
		"2700;27BF;Dingbats",
		"27C0;27EF;Miscellaneous Mathematical Symbols-A",
		"27F0;27FF;Supplemental Arrows-A",
		"2800;28FF;Braille Patterns",
		"2900;297F;Supplemental Arrows-B",
		"2980;29FF;Miscellaneous Mathematical Symbols-B",
		"2A00;2AFF;Supplemental Mathematical Operators",
		"2B00;2BFF;Miscellaneous Symbols and Arrows",
		"2C00;2C5F;Glagolitic",
		"2C60;2C7F;Latin Extended-C",
		"2C80;2CFF;Coptic",
		"2D00;2D2F;Georgian Supplement",
		"2D30;2D7F;Tifinagh",
		"2D80;2DDF;Ethiopic Extended",
		"2DE0;2DFF;Cyrillic Extended-A",
		"2E00;2E7F;Supplemental Punctuation",
		"2E80;2EFF;CJK Radicals Supplement",
		"2F00;2FDF;Kangxi Radicals",
		"2FF0;2FFF;Ideographic Description Characters",
		"3000;303F;CJK Symbols and Punctuation",
		"3040;309F;Hiragana",
		"30A0;30FF;Katakana",
		"3100;312F;Bopomofo",
		"3130;318F;Hangul Compatibility Jamo",
		"3190;319F;Kanbun",
		"31A0;31BF;Bopomofo Extended",
		"31C0;31EF;CJK Strokes",
		"31F0;31FF;Katakana Phonetic Extensions",
		"3200;32FF;Enclosed CJK Letters and Months",
		"3300;33FF;CJK Compatibility",
		"3400;4DBF;CJK Unified Ideographs Extension A",
		"4DC0;4DFF;Yijing Hexagram Symbols",
		"4E00;9FFF;CJK Unified Ideographs",
		"A000;A48F;Yi Syllables",
		"A490;A4CF;Yi Radicals",
		"A4D0;A4FF;Lisu",
		"A500;A63F;Vai",
		"A640;A69F;Cyrillic Extended-B",
		"A6A0;A6FF;Bamum",
		"A700;A71F;Modifier Tone Letters",
		"A720;A7FF;Latin Extended-D",
		"A800;A82F;Syloti Nagri",
		"A830;A83F;Common Indic Number Forms",
		"A840;A87F;Phags-pa",
		"A880;A8DF;Saurashtra",
		"A8E0;A8FF;Devanagari Extended",
		"A900;A92F;Kayah Li",
		"A930;A95F;Rejang",
		"A960;A97F;Hangul Jamo Extended-A",
		"A980;A9DF;Javanese",
		"A9E0;A9FF;Myanmar Extended-B",
		"AA00;AA5F;Cham",
		"AA60;AA7F;Myanmar Extended-A",
		"AA80;AADF;Tai Viet",
		"AAE0;AAFF;Meetei Mayek Extensions",
		"AB00;AB2F;Ethiopic Extended-A",
		"AB30;AB6F;Latin Extended-E",
		"AB70;ABBF;Cherokee Supplement",
		"ABC0;ABFF;Meetei Mayek",
		"AC00;D7AF;Hangul Syllables",
		"D7B0;D7FF;Hangul Jamo Extended-B",
		"D800;DB7F;High Surrogates",
		"DB80;DBFF;High Private Use Surrogates",
		"DC00;DFFF;Low Surrogates",
		"E000;F8FF;Private Use Area",
		"F900;FAFF;CJK Compatibility Ideographs",
		"FB00;FB4F;Alphabetic Presentation Forms",
		"FB50;FDFF;Arabic Presentation Forms-A",
		"FE00;FE0F;Variation Selectors",
		"FE10;FE1F;Vertical Forms",
		"FE20;FE2F;Combining Half Marks",
		"FE30;FE4F;CJK Compatibility Forms",
		"FE50;FE6F;Small Form Variants",
		"FE70;FEFF;Arabic Presentation Forms-B",
		"FF00;FFEF;Halfwidth and Fullwidth Forms",
		"FFF0;FFFF;Specials"
	};
	
	private static TreeMap unicodeBlocksByName = new TreeMap(String.CASE_INSENSITIVE_ORDER);
	static {
		for (int b = 0; b < allUnicodeBlocks.length; b++) {
			String[] blockData = allUnicodeBlocks[b].split("\\;");
			int minChar = Integer.parseInt(blockData[0], 16);
			int maxChar = Integer.parseInt(blockData[1], 16);
			String name = blockData[2];
			unicodeBlocksByName.put(name, new UnicodeBlock(name, minChar, maxChar));
		}
	}
	
	static class UnicodeBlock {
		final String name;
		final int minChar;
		final int maxChar;
		UnicodeBlock(String name, int minChar, int maxChar) {
			this.name = name;
			this.minChar = minChar;
			this.maxChar = maxChar;
		}
	}
	
	static UnicodeBlock getUnicodeBlock(String name) {
		return ((UnicodeBlock) unicodeBlocksByName.get(name));
	}
}