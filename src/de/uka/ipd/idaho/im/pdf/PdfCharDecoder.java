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
 *     * Neither the name of the Universität Karlsruhe (TH) / KIT nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY UNIVERSITÄT KARLSRUHE (TH) / KIT AND CONTRIBUTORS 
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.swing.ImageIcon;
import javax.swing.JOptionPane;

import de.uka.ipd.idaho.im.analysis.Imaging;
import de.uka.ipd.idaho.im.analysis.Imaging.AnalysisImage;
import de.uka.ipd.idaho.im.analysis.Imaging.ImagePartRectangle;
import de.uka.ipd.idaho.im.pdf.PdfParser.PStream;
import de.uka.ipd.idaho.im.pdf.PdfUtils.PdfLineInputStream;
import de.uka.ipd.idaho.im.utilities.ImageDisplayDialog;
import de.uka.ipd.idaho.stringUtils.StringUtils;

/**
 * @author sautter
 *
 */
public class PdfCharDecoder {
	
	//	TODO try and cluster characters by similarity
	//	==> good match yields suggestions for further match attempts
	
	//	TODO also try using Tesseract on char images
	
	private static final int SERIF = 4;
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
			Font font = new Font((((s & SERIF) != 0) ? "Serif" : "Sans"), (s & (Font.BOLD | Font.ITALIC)), 96);
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
		Font font = new Font((((fontStyle & SERIF) != 0) ? "Serif" : "Sans"), (fontStyle & (Font.BOLD | Font.ITALIC)), 96);
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
			SortedSet scss = getScoredCharSignatures(cm, (useFontStyle ? fontStyle : -1), true, ((char) 0), efforts);
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
	
	static SortedSet getScoredCharSignatures(CharMetrics cm, int style, boolean debug, char debugChar, int[] efforts) {
		TreeSet scss = new TreeSet(scoredCharSignatureOrder);
		int mpdComputed = 0;
		int lodComputed = 0;
		int fbdComputed = 0;
		int cbtComputed = 0;
		int cbbComputed = 0;
		int cbpComputed = 0;
		int cbdComputed = 0;
		int probCharCount = 0;
		for (int s = 0; s < charSignatures.length; s++) {
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
	
	private static final boolean DEBUG_CHAR_PROG_DECODING = true;
	
	static char getChar(PdfFont pFont, PStream charProg, int charCode, String charName, Map objects, Font[] serifFonts, Font[] sansFonts, HashMap cache) throws IOException {
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
			if (DEBUG_CHAR_PROG_DECODING) System.out.println("IGNORING: " + new String(line));
		}
		Hashtable imgParams = new Hashtable();
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
		
		//	TODO implement other types of true type fonts
		
		if ((imgWidth == -1) || (imgHeight == -1) || (imgData == null)) {
			if (DEBUG_CHAR_PROG_DECODING) System.out.println("Invalid char prog");
			return StringUtils.getCharForName(charName); // fail gracefully
		}
		if (imgFilter != null) {
			imgBuffer = new ByteArrayOutputStream();
			PdfParser.decode(imgFilter, imgData, imgParams, imgBuffer, objects);
			imgData = imgBuffer.toByteArray();
		}
		if (DEBUG_CHAR_PROG_DECODING) System.out.println("GOT DATA FOR CHAR IMAGE (" + imgWidth + " x " + imgHeight + "): " + imgData.length);
		BufferedImage cpImg = null;
		if (isMaskImage)
			cpImg = createImageMask(imgWidth, imgHeight, imgData);
		else {
			//	TODO implement color spaces, and read them as bitmaps nonetheless
			//	TODO to achieve this, create a BufferedImage in respective color space and read back brightness
		}
		
		//	do we have anything to work with?
		if (cpImg == null)
			return 0;
		pFont.setCharImage(charCode, charName, cpImg);
		
		//	wrap and measure char
		CharImage chImage = new CharImage(cpImg, -1);
		CharMetrics chMetrics = getCharMetrics(cpImg, 1);
		
		//	little we can do about this one
		if (chMetrics == null) {
//			JOptionPane.showMessageDialog(null, "Char match problem", "Char Match Problem", JOptionPane.INFORMATION_MESSAGE, new ImageIcon(cpImg));
			return 0;
		}
		
		//	set up statistics
		CharImageMatch bestCim = null;
		
		//	get ranked list of probably matches
		SortedSet matchChars = getScoredCharSignatures(chMetrics, -1, true, ((char) 0), null);
		
		//	evaluate probable matches
		for (Iterator mcit = matchChars.iterator(); mcit.hasNext();) {
			ScoredCharSignature scs = ((ScoredCharSignature) mcit.next());
			if (scs.difference > 500)
				break;
			System.out.println(" testing '" + scs.cs.ch + "' (" + ((int) scs.cs.ch) + "), signature difference is " + scs.difference);
			CharMatchResult matchResult = matchChar(chImage, scs.cs.ch, true, serifFonts, sansFonts, cache, false, false);
			CharImageMatch cim = null;
			for (int s = 0; s < matchResult.serifStyleCims.length; s++) {
				if ((matchResult.serifStyleCims[s] != null) && ((cim == null) || (cim.sim < matchResult.serifStyleCims[s].sim)))
					cim = matchResult.serifStyleCims[s];
			}
			for (int s = 0; s < matchResult.sansStyleCims.length; s++) {
				if ((matchResult.sansStyleCims[s] != null) && ((cim == null) || (cim.sim < matchResult.sansStyleCims[s].sim)))
					cim = matchResult.sansStyleCims[s];
			}
			
			if (cim == null) {
//				System.out.println("   --> could not render image");
				continue;
			}
			System.out.println("   --> similarity is " + cim.sim);
			if ((bestCim == null) || (cim.sim > bestCim.sim)) {
				bestCim = cim;
				System.out.println("   ==> new best match '" + scs.cs.ch + "' (" + ((int) scs.cs.ch) + ", " + StringUtils.getCharName((char) scs.cs.ch) + "), similarity is " + cim.sim);
//				//	TODO remove this after tests
//				displayCharMatch(bestCim.match, bestCim, "New best punctuation match");
			}
		}
		
		//	finally ...
		return ((bestCim == null) ? 0 : bestCim.match.ch);
	}
	
	private static BufferedImage createImageMask(int width, int height, byte[] bits) {
		BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
		Graphics g = bi.getGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, bi.getWidth(), bi.getHeight());
		g.setColor(Color.BLACK);
		int rw = (((width % 8) == 0) ? width : (((width / 8) + 1) * 8));
		for (int r = 0; r < height; r++) {
			for (int c = 0; c < width; c++) {
				int bitOffset = (rw * r) + c;
				int byteIndex = (bitOffset / 8);
				if (byteIndex < bits.length) {
					int byteOffset = (bitOffset % 8);
					int bt = bits[byteIndex];
					int btMask = 1 << (7 - byteOffset);
					boolean bit = ((bt & btMask) == 0);
					if (bit)
						g.drawLine(c, r, c, r);
				}
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
		boolean isCharBoxMatch;
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
	
	static CharImageMatch matchChar(CharImage charImage, char ch, Font font, HashMap cache, boolean isVerificationMatch, boolean debug) {
		CharImage matchImage = createCharImage(ch, font, cache);
		if (matchImage == null)
			return null;
		if ((charImage.box.getBottomRow() <= charImage.baseline) && (matchImage.baseline <= matchImage.box.getTopRow()))
			return null;
		if ((0 < charImage.baseline) && (charImage.baseline <= charImage.box.getTopRow()) && (matchImage.box.getBottomRow() <= matchImage.baseline))
			return null;
		return matchCharImage(charImage, matchImage, (charImage.baseline < 1), isVerificationMatch, debug);
	}
	
	static class CharMatchResult {
		boolean rendered = false;
		CharImageMatch[] serifStyleCims = new CharImageMatch[4];
		CharImageMatch[] sansStyleCims = new CharImageMatch[4];
		CharMatchResult() {}
	}
	
	static CharMatchResult matchChar(CharImage charImage, char ch, boolean allowCaps, Font[] serifFonts, Font[] sansFonts, HashMap cache, boolean isVerificationMatch, boolean debug) {
		CharMatchResult matchResult = new CharMatchResult();
		boolean charAboveBaseline = (charImage.box.getBottomRow() <= charImage.baseline);
		boolean charBelowBaseline = ((0 < charImage.baseline) && (charImage.baseline <= charImage.box.getTopRow()));
		
		boolean useCharBoxMatch;// = (charImage.baseline < 1);
		if (charImage.baseline < 1)
			useCharBoxMatch = true;
		else if (((charImage.box.getWidth() * 2) > charImage.img.getWidth()) && ((charImage.box.getHeight() * 2) > charImage.img.getHeight()))
			useCharBoxMatch = true;
		else useCharBoxMatch = false;
		//	TODO use char box match also if
		//	TODO - char box proportion difference less than 1
		//	TODO - char box fills at least half of font box in both dimensions
		
		//	try named char match first (render known chars to fill whole image)
		for (int s = 0; s < serifFonts.length; s++) {
			CharImage matchImage = createCharImage(ch, serifFonts[s], cache);
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
			matchResult.serifStyleCims[s] = matchCharImage(charImage, matchImage, doUseCharBoxMatch, isVerificationMatch, debug);
			if (allowCaps && (ch != Character.toUpperCase(ch))) {
				CharImage capMatchImage = createCharImage(Character.toUpperCase(ch), serifFonts[s], cache);
				if (capMatchImage != null) {
					doUseCharBoxMatch = (charImage.baseline < 1);
					if (useCharBoxMatch != doUseCharBoxMatch) {
						double charBoxProportionDistance = computeCharBoxProportionDistance(charImage, capMatchImage);
						if (charBoxProportionDistance < 1)
							doUseCharBoxMatch = true;
					}
					CharImageMatch capCim = matchCharImage(charImage, capMatchImage, doUseCharBoxMatch, isVerificationMatch, debug);
					if ((capCim != null) && ((matchResult.serifStyleCims[s] == null) || (matchResult.serifStyleCims[s].sim < capCim.sim)))
						matchResult.serifStyleCims[s] = capCim;
				}
			}
			if (matchResult.serifStyleCims[s] != null)
				matchResult.rendered = true;
		}
		for (int s = 0; s < sansFonts.length; s++) {
			CharImage matchImage = createCharImage(ch, sansFonts[s], cache);
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
			matchResult.sansStyleCims[s] = matchCharImage(charImage, matchImage, doUseCharBoxMatch, isVerificationMatch, debug);
			if (allowCaps && (ch != Character.toUpperCase(ch))) {
				CharImage capMatchImage = createCharImage(Character.toUpperCase(ch), sansFonts[s], cache);
				if (capMatchImage != null) {
					doUseCharBoxMatch = (charImage.baseline < 1);
					if (useCharBoxMatch != doUseCharBoxMatch) {
						double charBoxProportionDistance = computeCharBoxProportionDistance(charImage, capMatchImage);
						if (charBoxProportionDistance < 1)
							doUseCharBoxMatch = true;
					}
					CharImageMatch capCim = matchCharImage(charImage, capMatchImage, useCharBoxMatch, isVerificationMatch, debug);
					if ((capCim != null) && ((matchResult.sansStyleCims[s] == null) || (matchResult.sansStyleCims[s].sim < capCim.sim)))
						matchResult.sansStyleCims[s] = capCim;
				}
			}
			if (matchResult.sansStyleCims[s] != null)
				matchResult.rendered = true;
		}
		
		return matchResult;
	}
	
	private static double computeCharBoxProportionDistance(CharImage ci1, CharImage ci2) {
		double charBoxProportion1 = Math.log(((double) ci1.box.getWidth()) / ci1.box.getHeight());
		double charBoxProportion2 = Math.log(((double) ci2.box.getWidth()) / ci2.box.getHeight());
		return Math.abs(charBoxProportion1 - charBoxProportion2);
	}
	
	private static final char DEBUG_MATCH_TARGET_CHAR = ((char) 0);
	
	private static CharImageMatch matchCharImage(CharImage charImage, CharImage match, boolean charBoxMatch, boolean isVerificationMatch, boolean debug) {
//		if ((charImage == null) || (match == null))
//			return null;
//		
		int ciLeft = charImage.box.getLeftCol();
		int ciRight = charImage.box.getRightCol();
		int ciTop = (charBoxMatch ? charImage.box.getTopRow() : Math.min(charImage.baseline, charImage.box.getTopRow()));
		int ciBottom = (charBoxMatch ? charImage.box.getBottomRow() : Math.max(charImage.baseline, charImage.box.getBottomRow()));
		int ciHeight = (ciBottom - ciTop);
		int mLeft = match.box.getLeftCol();
		int mRight = match.box.getRightCol();
		int mTop = (charBoxMatch ? match.box.getTopRow() : Math.min(match.baseline, match.box.getTopRow()));
		int mBottom = (charBoxMatch ? match.box.getBottomRow() : Math.max(match.baseline, match.box.getBottomRow()));
		int mHeight = (mBottom - mTop);
		
		if (!isVerificationMatch) {
			double charBoxProportionDistance = computeCharBoxProportionDistance(charImage, match);
			if (charBoxProportionDistance > 1) // TODO verify this cutoff
				return null;
		}
		
		int cimWidth = Math.max((ciRight - ciLeft), (mRight - mLeft));
		int cimHeight = Math.max(ciHeight, mHeight);
		byte[][] cimData = new byte[cimWidth][cimHeight];
		byte[][] ciDistData = new byte[cimWidth][cimHeight];
		byte[][] mDistData = new byte[cimWidth][cimHeight];
		
		int matched = 0;
		int spurious = 0;
		int missed = 0;
		for (int cimCol = 0; cimCol < cimWidth; cimCol++) {
			int ciCol = (ciLeft + ((cimCol * charImage.box.getWidth()) / cimWidth));
			if (ciCol < 0)
				continue;
			if (ciRight <= ciCol)
				break;
			int mCol = (mLeft + ((cimCol * match.box.getWidth()) / cimWidth));
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
				
				byte cib = charImage.brightness[ciCol][ciRow];
				byte mb = match.brightness[mCol][mRow];
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
		if (debug) {
			fillDistData(ciDistData);
			fillDistData(mDistData);
			System.out.println((charBoxMatch ? "Char" : "Font") + " box match stats:");
			double ciAreaProportion = Math.log(((double) charImage.box.getWidth()) / charImage.box.getHeight());
			double mAreaProportion = Math.log(((double) match.box.getWidth()) / match.box.getHeight());
			System.out.println(" - area proportion distance is " + Math.abs(ciAreaProportion - mAreaProportion));
			System.out.println(" - matched " + matched + ", surface " + getSurface(cimData, CIM_MATCHED));
			System.out.println(" - spurious " + spurious + ", surface " + getSurface(cimData, CIM_SPURIOUS) + ", avg distance " + getAvgDist(cimData, CIM_SPURIOUS, mDistData));
			System.out.println(" - missed " + missed + ", surface " + getSurface(cimData, CIM_MISSED) + ", avg distance " + getAvgDist(cimData, CIM_MISSED, ciDistData));
//			if (cim.sim > 0.4)
//				displayCharMatch(charImage, cim, ((charBoxMatch ? "Char" : "Font") + " box match"));
		}
		return cim;
	}
	
	private static void fillDistData(byte[][] distData) {
		for (boolean unmatchedRemains = true; unmatchedRemains;) {
			unmatchedRemains = false;
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
					if (dist < Byte.MAX_VALUE)
						distData[x][y] = dist;
					else unmatchedRemains = true;
				}
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
	
	static BufferedImage getCharMatchImage(CharImage charImage, CharImageMatch cim) {
		BufferedImage bi = new BufferedImage(
				(charImage.img.getWidth() + 1 + cim.match.img.getWidth() + 1 + Math.max(charImage.img.getWidth(), cim.match.img.getWidth())),
				Math.max(charImage.img.getHeight(), cim.match.img.getHeight()),
				BufferedImage.TYPE_INT_RGB
			);
		Graphics g = bi.getGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, bi.getWidth(), bi.getHeight());
		g.drawImage(charImage.img, 0, 0, null);
		g.drawImage(cim.match.img, (charImage.img.getWidth() + 1), 0, null);
		g.setColor(Color.BLACK);
		if (0 < charImage.baseline)
			g.drawLine(0, charImage.baseline, charImage.img.getWidth(), charImage.baseline);
		if (0 < cim.match.baseline)
			g.drawLine((charImage.img.getWidth() + 1), cim.match.baseline, ((charImage.img.getWidth() + 1) + cim.match.img.getWidth()), cim.match.baseline);
		
		int ciLeft = charImage.box.getLeftCol();
		int ciRight = charImage.box.getRightCol();
		int ciTop = (cim.isCharBoxMatch ? charImage.box.getTopRow() : Math.min(charImage.baseline, charImage.box.getTopRow()));
		int ciBottom = (cim.isCharBoxMatch ? charImage.box.getBottomRow() : Math.max(charImage.baseline, charImage.box.getBottomRow()));
		int mLeft = cim.match.box.getLeftCol();
		int mRight = cim.match.box.getRightCol();
		int mTop = (cim.isCharBoxMatch ? cim.match.box.getTopRow() : Math.min(cim.match.baseline, cim.match.box.getTopRow()));
		int mBottom = (cim.isCharBoxMatch ? cim.match.box.getBottomRow() : Math.max(cim.match.baseline, cim.match.box.getBottomRow()));
		
		for (int x = 0;; x++) {
			int ciCol = (ciLeft + ((x * (ciRight - ciLeft)) / Math.max((ciRight - ciLeft), (mRight - mLeft))));
			if (ciCol < 0)
				continue;
			if (ciRight <= ciCol)
				break;
			int mCol = (mLeft + ((x * (mRight - mLeft)) / Math.max((ciRight - ciLeft), (mRight - mLeft))));
			if (mCol < 0)
				continue;
			if (mRight <= mCol)
				break;
			
			for (int y = 0;; y++) {
				int ciRow = (ciTop + ((y * (ciBottom - ciTop)) / Math.max((ciBottom - ciTop), (mBottom - mTop))));
				if (ciRow < 0)
					continue;
				if (ciBottom <= ciRow)
					break;
				int mRow = (mTop + ((y * (mBottom - mTop)) / Math.max((ciBottom - ciTop), (mBottom - mTop))));
				if (mRow < 0)
					continue;
				if (mBottom <= mRow)
					break;
				
				byte cib = charImage.brightness[ciCol][ciRow];
				byte mb = cim.match.brightness[mCol][mRow];
				Color c = null;
				if ((cib < 80) && (mb < 80))
					c = Color.BLACK;
				else if (cib < 80)
					c = Color.GREEN;
				else if (mb < 80)
					c = Color.RED;
				if (c != null)
					bi.setRGB(
							(charImage.img.getWidth() + 1 + cim.match.img.getWidth() + 1 + Math.min(ciLeft, mLeft) + Math.max((ciCol - ciLeft), (mCol - mLeft))),
							(Math.min(ciTop, mTop) + Math.max((ciRow - ciTop), (mRow - mTop))),
							c.getRGB()
						);
			}
		}
		
		return bi;
	}
	
	static void displayCharMatch(CharImage charImage, CharImageMatch cim, String message) {
		JOptionPane.showMessageDialog(null, (message + ": '" + cim.match.ch + "', similarity is " + cim.sim + "\n" + cim.matched + "-" + cim.spurious + "-" + cim.missed), "Comparison Image", JOptionPane.PLAIN_MESSAGE, new ImageIcon(getCharMatchImage(charImage, cim)));
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
		final int fontStyle;
		final BufferedImage img;
		final byte[][] brightness;
		final ImagePartRectangle box;
		final int baseline;
		CharImage(char ch, int fontStyle, BufferedImage img, byte[][] brightness, ImagePartRectangle box, int baseline) {
			this.ch = ch;
			this.fontStyle = fontStyle;
			this.img = img;
			this.brightness = brightness;
			this.box = box;
			this.baseline = baseline;
		}
		CharImage(BufferedImage img, int baseline) {
			this(((char) 0), -1, Imaging.wrapImage(img, null), baseline);
		}
		CharImage(char ch, int fontStyle, BufferedImage img, int baseline) {
			this(ch, fontStyle, Imaging.wrapImage(img, (ch + "-" + fontStyle)), baseline);
		}
		CharImage(char ch, int fontStyle, AnalysisImage ai, int baseline) {
			this.ch = ch;
			this.fontStyle = fontStyle;
			this.img = ai.getImage();
			this.brightness = ai.getBrightness();
			this.box = Imaging.getContentBox(ai);
			this.baseline = baseline;
//			System.out.println("CharImage created" + ((this.ch == 0) ? "" : (" for " + this.ch)) + ", image is " + this.img.getWidth() + "x" + this.img.getHeight() + ", char box is " + this.box.getId() + ", baseline at " + this.baseline);
		}
	}
	
	private static HashSet unrenderableChars = new HashSet();
	
	static CharImage createCharImage(char ch, Font font, HashMap cache) {
		if (!font.canDisplay(ch))
			return null;
		if (ch == 160)
			return null;
		String charKey = (ch + "-in-" + font.getName() + "-" + font.getStyle());
		if (unrenderableChars.contains(charKey))
			return null;
		
		//	try cache lookup
		String cacheKey = null;
		if (cache != null) {
			cacheKey = (ch + "-in-" + font.getName() + "-" + font.getStyle());
			CharImage ci = ((CharImage) cache.get(cacheKey));
			if (ci != null) {
//				if (DEBUG_LOAD_FONTS) System.out.println(" - cache hit for " + cacheKey);
				return ci;
			}
		}
		
		//	normalize font size and get maximum char bounds
		if (font.getSize() != 48)
			font = font.deriveFont(48.0f);
		Rectangle2D fontBox = getFontBox(font);
		
		//	create char image
		BufferedImage cbi = new BufferedImage(((int) Math.round(fontBox.getWidth() + 2)), ((int) Math.round(fontBox.getHeight() + 2)), BufferedImage.TYPE_INT_RGB);
		Graphics2D cgr = cbi.createGraphics();
		cgr.setColor(Color.WHITE);
		cgr.fillRect(0, 0, cbi.getWidth(), cbi.getHeight());
		cgr.setFont(font);
		cgr.setColor(Color.BLACK);
		TextLayout tl = new TextLayout(("" + ch), font, cgr.getFontRenderContext());
		int cbl = Math.round(tl.getAscent() + 1);
		cgr.drawString(("" + ch), ((int) (Math.round(fontBox.getWidth() - tl.getBounds().getWidth() + 1) / 2)), cbl);
		cgr.dispose();
		CharImage ci = new CharImage(ch, font.getStyle(), cbi, cbl);
		
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
	
	private static final byte[][] parseSignature(String sigData) {
		String[] sigRows = sigData.split("\\;");
		byte[][] sig = new byte[sigRows[0].length()][sigRows.length];
		for (int c = 0; c < sig.length; c++) {
			for (int r = 0; r < sigRows.length; r++)
				sig[c][r] = ((byte) Integer.parseInt(sigRows[r].substring(c, (c+1)), 16));
		}
		return sig;
	}
}
