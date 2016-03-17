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
package de.uka.ipd.idaho.im.pdf.test;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import de.uka.ipd.idaho.im.analysis.Imaging;
import de.uka.ipd.idaho.im.analysis.Imaging.AnalysisImage;
import de.uka.ipd.idaho.im.analysis.Imaging.ImagePartRectangle;
import de.uka.ipd.idaho.im.util.ImFontUtils;
import de.uka.ipd.idaho.stringUtils.StringUtils;

/**
 * @author sautter
 *
 */
public class PdfCharSimilarityTest {
	
	private static final int SERIF = 4;
	private static final boolean SERIF_IS_STYLE = false;
	
	private static final String[] styleNames4 = {"Plain", "Bold", "Italics", "BoldItalics"};
	private static final String[] styleNames8 = {"Sans-Plain", "Sans-Bold", "Sans-Italics", "Sans-BoldItalics", "Serif-Plain", "Serif-Bold", "Serif-Italics", "Serif-BoldItalics"};
	private static final String[] styleNames = (SERIF_IS_STYLE ? styleNames8 : styleNames4);
	
	//	TODO generate images for all chars observed by decoder (copy arrays, etc)
	
	//	TODO measure image similarity (with overlap algorithm from PdfFont)
	//	TODOne - left char bounds aligned
	//	TODOne - vertical char centerlines aligned
	//	TODOne - right char bound aligned
	//	TODOne - both chars scaled to same width
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		TreeMap closeCharSets = new TreeMap() {
			public Object get(Object key) {
				Object ts = super.get(key);
				if (ts == null) {
					ts = new TreeSet(charImageSimilarityOrder);
					this.put(key, ts);
				}
				return ts;
			}
		};
		
		int charLimit = 0xFFFF;//Integer.MAX_VALUE;
		
		ImFontUtils.loadFreeFonts();
		
		for (int fs = 0; fs <= (SERIF | (Font.BOLD | Font.ITALIC)); fs++) {
//			Font font = new Font((((fs & SERIF) != 0) ? "Serif" : "Sans"), (fs & (Font.BOLD | Font.ITALIC)), 48);
			Font font = new Font((((fs & SERIF) != 0) ? "FreeSerif" : "FreeSans"), (fs & (Font.BOLD | Font.ITALIC)), 48);
			
			Graphics2D gr = (new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY)).createGraphics();
			gr.setFont(font);
			Rectangle2D fontBox = font.getMaxCharBounds(gr.getFontRenderContext());
			
			LinkedList charImageList = new LinkedList();
			for (int t = 0; t < Math.min(charLimit, charSignatures.length); t++) {
				if (charSignatures[t].ch > charLimit)
					break;
				if (font.canDisplay(charSignatures[t].ch))
					charImageList.add(getCharImage(charSignatures[t].ch, fs, font, fontBox));
			}
			CharImage[] charImages = ((CharImage[]) charImageList.toArray(new CharImage[charImageList.size()]));
			
			for (int bc = 0; bc < charImages.length; bc++) {
				TreeSet bcCloseChars = ((TreeSet) closeCharSets.get("" + charImages[bc].ch));
				System.out.print("Comparing to '" + charImages[bc].ch + "' (" + Integer.toString(((int) charImages[bc].ch), 16) + ") in " + fs + " ");
				for (int cc = (bc+1); cc < charImages.length; cc++) {
					System.out.print("" + charImages[cc].ch);
					CharImageSimilarity cis = getSimilarity(charImages[bc], charImages[cc]);
//					System.out.println(cis);
					if (cis.getProductSimilarity("scaled") > 0.85f) {
						bcCloseChars.add(cis);
						((TreeSet) closeCharSets.get("" + charImages[cc].ch)).add(cis);
					}
				}
				System.out.println();
			}
		}
		
		for (Iterator cit = closeCharSets.keySet().iterator(); cit.hasNext();) {
			String ch = ((String) cit.next());
			TreeSet chCloseChars = ((TreeSet) closeCharSets.get(ch));
			HashSet ccCloseCharsPrinted = new HashSet();
			System.out.println("Chars close to '" + ch + "' (" + Integer.toString(((int) ch.charAt(0)), 16) + ") - " + chCloseChars.size() + ":");
			for (Iterator ccit = chCloseChars.iterator(); ccit.hasNext();) {
				CharImageSimilarity cis = ((CharImageSimilarity) ccit.next());
				char cch = ((ch.charAt(0) == cis.charImage1.ch) ? cis.charImage2.ch : cis.charImage1.ch);
				if (ccCloseCharsPrinted.add(new Character(cch)))
					System.out.println(" - '" + cch + "' (" + Integer.toString(((int) cch), 16) + "): " + cis.getProductSimilarity("scaled") + " P, " + cis.getFScoreSimilarity("scaled") + " F (in " + cis.charImage1.fontStyle + ")");
			}
		}
		
		//	TODO check into how many interconnected groups the characters fall
		while (closeCharSets.size() != 0) {
			TreeSet chCluster = new TreeSet();
			int chClusterDiameter = 0;
			TreeSet chClusterAdded = new TreeSet();
			chClusterAdded.add(closeCharSets.firstKey());
			while (chCluster.addAll(chClusterAdded)) {
				chClusterAdded.clear();
				for (Iterator chcit = chCluster.iterator(); chcit.hasNext();) {
					String ch = ((String) chcit.next());
					TreeSet chCloseChars = ((TreeSet) closeCharSets.remove(ch));
					if (chCloseChars == null)
						continue;
					for (Iterator ccit = chCloseChars.iterator(); ccit.hasNext();) {
						CharImageSimilarity cis = ((CharImageSimilarity) ccit.next());
						char cch = ((ch.charAt(0) == cis.charImage1.ch) ? cis.charImage2.ch : cis.charImage1.ch);
						chClusterAdded.add("" + cch);
					}
				}
				chClusterDiameter++;
			}
			System.out.print("Char cluster of " + chCluster.size() + " chars (diameter " + chClusterDiameter + "): ");
			for (Iterator chcit = chCluster.iterator(); chcit.hasNext();) {
				String ch = ((String) chcit.next());
				System.out.print("'" + ch + "' (" + Integer.toString(((int) ch.charAt(0)), 16) + ")");
				if (chcit.hasNext())
					System.out.print(", ");
			}
			System.out.println();
		}
	}
	
	private static Comparator charImageSimilarityOrder = new Comparator() {
		public int compare(Object obj1, Object obj2) {
			CharImageSimilarity cis1 = ((CharImageSimilarity) obj1);
			CharImageSimilarity cis2 = ((CharImageSimilarity) obj2);
			int c = Float.compare(cis2.getProductSimilarity("scaled"), cis1.getProductSimilarity("scaled"));
			return ((c == 0) ? cis1.key.compareTo(cis2.key) : c);
		}
	};
	
	private static CharImageSimilarity getSimilarity(CharImage charImage1, CharImage charImage2) {
		CharImageSimilarity cis = new CharImageSimilarity(charImage1, charImage2);
		int lo1;
		int lo2;
		float sim;
		
		lo1 = charImage1.cbb.getLeftCol();
		lo2 = charImage2.cbb.getLeftCol();
//		System.out.println("Bounds 1: " + charImage1.cbb.getId());
//		System.out.println("Bounds 2: " + charImage2.cbb.getId());
//		System.out.println("left lo1: " + lo1);
//		System.out.println("left lo2: " + lo2);
		sim = compareCharImages(charImage1, lo1, charImage2, lo2, false, cis, "left");
//		System.out.println("left aligned: " + sim);
		
		if (charImage1.cbb.getRightCol() < charImage2.cbb.getRightCol()) {
			lo1 = (Math.min(charImage1.cbb.getLeftCol(), charImage2.cbb.getLeftCol()) - (charImage2.cbb.getRightCol() - charImage1.cbb.getRightCol()));
			lo2 = Math.min(charImage1.cbb.getLeftCol(), charImage2.cbb.getLeftCol());
		}
		else {
			lo1 = Math.min(charImage1.cbb.getLeftCol(), charImage2.cbb.getLeftCol());
			lo2 = (Math.min(charImage1.cbb.getLeftCol(), charImage2.cbb.getLeftCol()) - (charImage1.cbb.getRightCol() - charImage2.cbb.getRightCol()));
		}
//		System.out.println("Bounds 1: " + charImage1.cbb.getId());
//		System.out.println("Bounds 2: " + charImage2.cbb.getId());
//		System.out.println("right lo1: " + lo1);
//		System.out.println("right lo2: " + lo2);
		sim = compareCharImages(charImage1, lo1, charImage2, lo2, false, cis, "right");
//		System.out.println("right aligned: " + sim);
		
		int cc1 = ((charImage1.cbb.getLeftCol() + charImage1.cbb.getRightCol()) / 2);
		int cc2 = ((charImage2.cbb.getLeftCol() + charImage2.cbb.getRightCol()) / 2);
		if (cc1 < cc2) {
			lo1 = (Math.min(charImage1.cbb.getLeftCol(), charImage2.cbb.getLeftCol()) - (cc2 - cc1));
			lo2 = Math.min(charImage1.cbb.getLeftCol(), charImage2.cbb.getLeftCol());
		}
		else {
			lo1 = Math.min(charImage1.cbb.getLeftCol(), charImage2.cbb.getLeftCol());
			lo2 = (Math.min(charImage1.cbb.getLeftCol(), charImage2.cbb.getLeftCol()) - (cc1 - cc2));
		}
//		System.out.println("Bounds 1: " + charImage1.cbb.getId());
//		System.out.println("Bounds 2: " + charImage2.cbb.getId());
//		System.out.println("center lo1: " + lo1);
//		System.out.println("center lo2: " + lo2);
		sim = compareCharImages(charImage1, lo1, charImage2, lo2, false, cis, "center");
//		System.out.println("center aligned: " + sim);
		
		lo1 = charImage1.cbb.getLeftCol();
		lo2 = charImage2.cbb.getLeftCol();
//		System.out.println("Bounds 1: " + charImage1.cbb.getId());
//		System.out.println("Bounds 2: " + charImage2.cbb.getId());
//		System.out.println("left lo1: " + lo1);
//		System.out.println("left lo2: " + lo2);
		sim = compareCharImages(charImage1, lo1, charImage2, lo2, true, cis, "scaled");
//		System.out.println("streteched: " + sim);
		
		return cis;
	}
	
	private static float compareCharImages(CharImage charImage1, int leftOffset1, CharImage charImage2, int leftOffset2, boolean stretch, CharImageSimilarity cis, String alignment) {
		int matchedBlackPixels = 0;
		int unmatchedBlackPixels1 = 0;
		int unmatchedBlackPixels2 = 0;
		for (int c = 0; c < Math.min(charImage1.cbi.getWidth(), charImage2.cbi.getWidth()); c++) {
			int col1 = (c + leftOffset1);
			if (stretch && (charImage1.cbb.getWidth() < charImage2.cbb.getWidth()))
				col1 = (((c * charImage1.cbb.getWidth()) / charImage2.cbb.getWidth()) + leftOffset1);
			if (charImage1.cbb.getRightCol() <= col1)
				col1 = Integer.MIN_VALUE;
			int col2 = (c + leftOffset2);
			if (stretch && (charImage2.cbb.getWidth() < charImage1.cbb.getWidth()))
				col2 = (((c * charImage2.cbb.getWidth()) / charImage1.cbb.getWidth()) + leftOffset2);
			if (charImage2.cbb.getRightCol() <= col2)
				col2 = Integer.MIN_VALUE;
			if ((col1 == Integer.MIN_VALUE) && (col2 == Integer.MIN_VALUE))
				break;
//			System.out.println("col1: " + col1);
//			System.out.println("col2: " + col2);
			for (int r = 0; r < Math.min(charImage1.cbi.getHeight(), charImage2.cbi.getHeight()); r++) {
				byte b1 = ((col1 < 0) ? 127 : charImage1.cib[col1][r]);
				byte b2 = ((col2 < 0) ? 127 : charImage2.cib[col2][r]);
				if ((b1 < 80) && (b2 < 80))
					matchedBlackPixels++;
				else if (b1 < 80)
					unmatchedBlackPixels1++;
				else if (b2 < 80)
					unmatchedBlackPixels2++;
			}
		}
		
		cis.addComparison(alignment, matchedBlackPixels, unmatchedBlackPixels1, unmatchedBlackPixels2);
		
		float covered1 = (((float) matchedBlackPixels) / (unmatchedBlackPixels1 + matchedBlackPixels));
		float covered2 = (((float) matchedBlackPixels) / (unmatchedBlackPixels2 + matchedBlackPixels));
		return (covered1 * covered2);
	}
	
	private static class CharImageSimilarity {
		CharImage charImage1;
		CharImage charImage2;
		String key;
		TreeMap comparisons = new TreeMap();
		CharImageSimilarity(CharImage charImage1, CharImage charImage2) {
			this.charImage1 = charImage1;
			this.charImage2 = charImage2;
			if (charImage1.ch < charImage2.ch)
				this.key = (charImage1.ch + "-" + charImage2.ch);
			else this.key = (charImage2.ch + "-" + charImage1.ch);
		}
		void addComparison(String alignment, int matchedPixels, int unmatchedPixels1, int unmatchedPixels2) {
			this.comparisons.put(alignment, new CharImageComparison(alignment, matchedPixels, unmatchedPixels1, unmatchedPixels2));
		}
		public String toString() {
			StringBuffer sb = new StringBuffer("Similarity between '" + this.charImage1.ch + "' (" + Integer.toString(((int) this.charImage1.ch), 16) + ") " + this.charImage1.cbb.getId() + " and '" + this.charImage2.ch + "' (" + Integer.toString(((int) this.charImage2.ch), 16) + ") " + this.charImage2.cbb.getId() + ":");
			for (Iterator cit = this.comparisons.keySet().iterator(); cit.hasNext();)
				sb.append("\r\n - " + ((CharImageComparison) this.comparisons.get(cit.next())).toString());
			return sb.toString();
		}
		float getProductSimilarity(String alignment) {
			return (this.comparisons.containsKey(alignment) ? ((CharImageComparison) this.comparisons.get(alignment)).getProductSimilarity() : -1);
		}
		float getFScoreSimilarity(String alignment) {
			return (this.comparisons.containsKey(alignment) ? ((CharImageComparison) this.comparisons.get(alignment)).getFScoreSimilarity() : -1);
		}
		static class CharImageComparison {
			final String alignment;
			int matchedPixels = 0;
			int unmatchedPixels1 = 0;
			int unmatchedPixels2 = 0;
			CharImageComparison(String alignment, int matchedPixels, int unmatchedPixels1, int unmatchedPixels2) {
				this.alignment = alignment;
				this.matchedPixels = matchedPixels;
				this.unmatchedPixels1 = unmatchedPixels1;
				this.unmatchedPixels2 = unmatchedPixels2;
			}
			float getProductSimilarity() {
				float covered1 = (((float) this.matchedPixels) / (this.unmatchedPixels1 + this.matchedPixels));
				float covered2 = (((float) this.matchedPixels) / (this.unmatchedPixels2 + this.matchedPixels));
				return (covered1 * covered2);
			}
			float getFScoreSimilarity() {
				float covered1 = (((float) this.matchedPixels) / (this.unmatchedPixels1 + this.matchedPixels));
				float covered2 = (((float) this.matchedPixels) / (this.unmatchedPixels2 + this.matchedPixels));
				return ((covered1 * covered2 * 2) / (covered1 + covered2));
			}
			public String toString() {
				return (this.alignment + ": " + this.getProductSimilarity() + " P, " + this.getFScoreSimilarity() + " F, details (" + this.unmatchedPixels1 + "-" + this.matchedPixels + "-" + this.unmatchedPixels2 + ")");
			}
		}
	}
	
	private static CharImage getCharImage(char ch, int fontStyle, Font font, Rectangle2D fontBox) {
		BufferedImage cbi = new BufferedImage(((int) Math.round(fontBox.getWidth())), ((int) Math.round(fontBox.getHeight())), BufferedImage.TYPE_BYTE_GRAY);
		Graphics2D cgr = cbi.createGraphics();
		cgr.setColor(Color.WHITE);
		cgr.fillRect(0, 0, cbi.getWidth(), cbi.getHeight());
		cgr.setFont(font);
		cgr.setColor(Color.BLACK);
		TextLayout tl = new TextLayout(("" + ch), font, cgr.getFontRenderContext());
		int cbl = Math.round(tl.getAscent());
		cgr.drawString(("" + ch), ((int) (Math.round(fontBox.getWidth() - tl.getBounds().getWidth()) / 2)), cbl);
		cgr.dispose();
		
		AnalysisImage cai = Imaging.wrapImage(cbi, null);
		
		return new CharImage(ch, fontStyle, cbi, cbl, cai.getBrightness(), Imaging.getContentBox(cai));
	}
	
	private static class CharImage {
		char ch;
		int fontStyle;
		BufferedImage cbi;
		int cbl;
		byte[][] cib;
		ImagePartRectangle cbb;
		CharImage(char ch, int fontStyle, BufferedImage cbi, int cbl, byte[][] cib, ImagePartRectangle cbb) {
			this.ch = ch;
			this.fontStyle = fontStyle;
			this.cbi = cbi;
			this.cbl = cbl;
			this.cib = cib;
			this.cbb = cbb;
		}
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
	
	private static class CharSignature {
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
				return 3000f;
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
				return 4000f;
			if (addLoopDiff)
				difference += loopDiff;
			if ((multiPartDiff + loopDiff) >= multiPartLoopDiffCutoff)
				return 5000f;
			
			float charBoxTopDiff = Math.abs((((style < 0) || (this.sRelCharBoxTops[style] == -1)) ? this.relCharBoxTop : this.sRelCharBoxTops[style]) - relCharBoxTop);
			if (debugData != null)
				debugData.put("cbt", new Float(charBoxTopDiff));
			if (debugOutput || (this.ch == debugChar))
				System.out.println(" - char box top diff is " + charBoxTopDiff);
			if (charBoxTopDiff > charBoxTopDiffCutoff)
				return 5800f;
			if (addCharBoxTopDiff)
				difference += charBoxTopDiff;
			
			float charBoxBottomDiff = Math.abs((((style < 0) || (this.sRelCharBoxBottoms[style] == -1)) ? this.relCharBoxBottom : this.sRelCharBoxBottoms[style]) - relCharBoxBottom);
			if (debugData != null)
				debugData.put("cbb", new Float(charBoxBottomDiff));
			if (debugOutput || (this.ch == debugChar))
				System.out.println(" - char box bottom diff is " + charBoxBottomDiff);
			if (charBoxBottomDiff > charBoxBottomDiffCutoff)
				return 5900f;
			if (addCharBoxBottomDiff)
				difference += charBoxBottomDiff;
			
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
				return 6000f;
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
					return 7000f;
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
				return 8000f;
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
	
	static {
		LinkedList csList = new LinkedList();
		
		try {
			String csResourceName = PdfCharSimilarityTest.class.getName();
			csResourceName = csResourceName.substring(0, csResourceName.lastIndexOf('.'));
			csResourceName = csResourceName.substring(0, csResourceName.lastIndexOf('.')); // have to cut '.test' as well from here
			csResourceName = (csResourceName.replaceAll("\\.", "/") + "/PdfCharSignatures." + (SERIF_IS_STYLE ? 8 : 4) + ".txt");
			System.out.println(csResourceName);
			BufferedReader csBr = new BufferedReader(new InputStreamReader(PdfCharSimilarityTest.class.getClassLoader().getResourceAsStream(csResourceName), "UTF-8"));
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
