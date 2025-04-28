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
package de.uka.ipd.idaho.im.analysis;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.ImageIcon;
import javax.swing.JOptionPane;

import de.uka.ipd.idaho.gamta.Gamta;
import de.uka.ipd.idaho.gamta.util.CountingSet;
import de.uka.ipd.idaho.gamta.util.ParallelJobRunner;
import de.uka.ipd.idaho.gamta.util.ParallelJobRunner.ParallelFor;
import de.uka.ipd.idaho.gamta.util.ProgressMonitor;
import de.uka.ipd.idaho.gamta.util.ProgressMonitor.SynchronizedProgressMonitor;
import de.uka.ipd.idaho.gamta.util.imaging.BoundingBox;
import de.uka.ipd.idaho.gamta.util.imaging.PageImage;
import de.uka.ipd.idaho.im.ImDocument;
import de.uka.ipd.idaho.im.ImLayoutObject;
import de.uka.ipd.idaho.im.ImPage;
import de.uka.ipd.idaho.im.ImRegion;
import de.uka.ipd.idaho.im.ImWord;
import de.uka.ipd.idaho.im.analysis.Imaging.AnalysisImage;
import de.uka.ipd.idaho.im.analysis.Imaging.ImagePartRectangle;
import de.uka.ipd.idaho.im.analysis.PageImageAnalysis.PageRegionColoring;
import de.uka.ipd.idaho.im.util.ImFontUtils;
import de.uka.ipd.idaho.im.util.ImUtils;

/**
 * Function library for word image analysis.
 * 
 * @author sautter
 */
public class WordImageAnalysis {
	
	/* make sure we have the fonts we need */
	static {
		ImFontUtils.loadFreeFonts();
	}
	
	/**
	 * Analyze font metrics in an Image Markup document. This method relies on
	 * OCR results for comparison.
	 * @param doc the document whose font metrics to analyze
	 * @param pm a progress monitor to observe the processing
	 */
	public static void analyzeFontMetrics(ImDocument doc, ProgressMonitor pm) {
		analyzeFontMetrics(doc, true, pm);
	}
	
	/**
	 * Analyze font metrics in an Image Markup document. This method relies on
	 * OCR results for comparison.
	 * @param doc the document whose font metrics to analyze
	 * @param useMultipleCores use multiple CPU cores for word image analysis
	 *            if possible (i.e., if multiple cores available)?
	 * @param pm a progress monitor to observe the processing
	 */
	public static void analyzeFontMetrics(ImDocument doc, boolean useMultipleCores, ProgressMonitor pm) {
		analyzeFontMetrics(doc, useMultipleCores, pm, true, true, true);
	}
	
	/**
	 * Analyze font metrics in an Image Markup document. This method relies on
	 * OCR results for comparison.
	 * @param doc the document whose font metrics to analyze
	 * @param useMultipleCores use multiple CPU cores for word image analysis
	 *            if possible (i.e., if multiple cores available)?
	 * @param pm a progress monitor to observe the processing
	 * @param assessBold find and mark words in bold (using width of vertical
	 *            stems in characters in comparison to document average)
	 * @param assessItalics find and mark words in italics (using contrast
	 *            of histograms over number of pixels in columns)
	 * @param measureFontSize approximate the font sizes of words (based upon
	 *            line height and scan resolution)
	 */
	public static void analyzeFontMetrics(ImDocument doc, boolean useMultipleCores, ProgressMonitor pm, final boolean assessBold, final boolean assessItalics, final boolean measureFontSize) {
		final SynchronizedProgressMonitor spm = ((pm instanceof SynchronizedProgressMonitor) ? ((SynchronizedProgressMonitor) pm) : new SynchronizedProgressMonitor(pm));
		
		//	check if we're actually supposed to do anything
		if (measureFontSize) {}
		else if (assessBold) {}
		else if (assessItalics) {}
		else return; // no measurement selected at all
		
		//	collect word images for later collective assessment
		final ArrayList wordImages = new ArrayList();
		
		//	get word images, and assess italics
		spm.setStep("Extracting word images");
		spm.setBaseProgress(0);
		spm.setMaxProgress(50);
		final ImPage[] pages = doc.getPages();
		final Map wordImageCache = Collections.synchronizedMap(new HashMap());
		final Map renderedMatchesByWords = Collections.synchronizedMap(new HashMap());
		ParallelJobRunner.runParallelFor(new ParallelFor() {
			public void doFor(int p) throws Exception {
				
				//	update progress
				spm.setInfo("Extracting word images from page " + p);
				spm.setProgress((p * 100) / pages.length);
				
				//	get word images for current page
				WordImage[] pageWordImages = getWordImages(pages[p]);
				spm.setInfo(" ==> got " + pageWordImages.length + " word images");
				
				//	match word images against rendered OCR result for italics detection
				assessItalicsAndFontSize(pageWordImages, renderedMatchesByWords, wordImageCache, assessItalics, measureFontSize);
				
				//	add word images to list (order is not really relevant for our analyses)
				synchronized (wordImages) {
					wordImages.addAll(Arrays.asList(pageWordImages));
				}
			}
		}, pages.length, (useMultipleCores ? -1 : 1));
		
		//	average out font sizes inside paragraphs (should be rather consistent)
		if (measureFontSize) {
			spm.setStep("Computing paragraph font sizes");
			spm.setBaseProgress(50);
			spm.setMaxProgress(75);
			ParallelJobRunner.runParallelFor(new ParallelFor() {
				public void doFor(int pg) throws Exception {
					
					//	update progress
					spm.setInfo("Computing font sizes in page " + pg);
					spm.setProgress((pg * 100) / pages.length);
					
					//	work on individual paragraphs
					ImRegion[] pageParagraphs = pages[pg].getRegions(ImRegion.PARAGRAPH_TYPE);
					Arrays.sort(pageParagraphs, ImUtils.topDownOrder);
					for (int p = 0; p < pageParagraphs.length; p++) {
						if (DEBUG_COMPUTE_FONT_METRICS) System.out.println("Assessing font size in paragraph " + pageParagraphs[p].bounds + " on page " + pages[pg].pageId);
						ImWord[] paragraphWords = pageParagraphs[p].getWords();
						if (paragraphWords.length != 0)
							assessFontSize(paragraphWords);
					}
				}
			}, pages.length, (useMultipleCores ? -1 : 1));
		}
		
		//	anything left to do?
		if (!assessBold)
			return;
		
		/* Average out weight differences between font sizes
		 * - relative weight generally differs between words (ones with no
		 *   ascenders and descenders are relatively heavier due to smaller box)
		 * - relation of relative weight across font sizes should be about the
		 *   same, however
		 * ==> DOESN'T SEEM TO BE THE CASE, THEN ...
		 * ==> AND THEN, IT ACTUALLY IS THE CASE FOR FONT SIZES WITH MANY
		 *     REPRESENTATIVES (at least, say, 3% of document words)
		 */
		spm.setStep("Computing average font weights");
		spm.setBaseProgress(75);
		spm.setMaxProgress(85);
		TreeMap fontSizesToWeightRelations = new TreeMap();
		int weightRelationWordCount = 0;
		int noFontSizeWordCount = 0;
		int minFontSize = Integer.MAX_VALUE;
		int maxFontSize = 0;
		for (int w = 0; w < wordImages.size(); w++) {
			WordImage wi = ((WordImage) wordImages.get(w));
			
			//	update progress
			spm.setProgress((w * 100) / wordImages.size());
			
			//	ignore punctuation marks (for now, at least)
			if ((wi.box.getWidth() < (wi.pageImageDpi / 30)) || (wi.box.getHeight() < (wi.pageImageDpi / 20)))
				continue;
			
			//	ignore punctuation marks, at least for now
			if (Gamta.isPunctuation(wi.str))
				continue;
			
			//	little we can do without font size
			if (!wi.word.hasAttribute(ImWord.FONT_SIZE_ATTRIBUTE)) {
				noFontSizeWordCount++;
				continue;
			}
			
			//	get match to non-bold rendering (might be italics or not)
			WordImageMatch wim = ((WordImageMatch) renderedMatchesByWords.get(wi.word.getLocalID()));
			if (wim == null)
				continue;
			
			//	get word font size
			int wordFontSize = wi.word.getFontSize();
			minFontSize = Math.min(minFontSize, wordFontSize);
			maxFontSize = Math.max(maxFontSize, wordFontSize);
			
			//	record image word weight
			FontSizeWeightRelation fswr = ((FontSizeWeightRelation) fontSizesToWeightRelations.get(wordFontSize));
			if (fswr == null) {
				fswr = new FontSizeWeightRelation(wordFontSize);
				fontSizesToWeightRelations.put(Integer.valueOf(wordFontSize), fswr);
			}
			fswr.addWord(wim);
			weightRelationWordCount++;
		}
		if (DEBUG_COMPUTE_FONT_METRICS) System.out.println("Fon sizes range from " + minFontSize + " to " + maxFontSize + ", font size lacking in " + noFontSizeWordCount + " words");
		
		//	anything to work with?
		if (maxFontSize < minFontSize)
			return;
		
		//	average out image font size weights and stem widths
		float[] fontSizeWeightRelations = new float[maxFontSize - minFontSize + 1];
		Arrays.fill(fontSizeWeightRelations, -1);
		float[] fontSizeStemWidths = new float[maxFontSize - minFontSize + 1];
		Arrays.fill(fontSizeStemWidths, -1);
		
		//	assign weight relations to font sizes with sufficient number of representatives
		if (DEBUG_COMPUTE_FONT_METRICS) System.out.println("Relative image word weights and stem widths by font sizes:");
		for (Iterator fsit = fontSizesToWeightRelations.keySet().iterator(); fsit.hasNext();) {
			FontSizeWeightRelation fswr = ((FontSizeWeightRelation) fontSizesToWeightRelations.get(fsit.next()));
			if ((fswr.wordCount * 33) >= weightRelationWordCount) {
				if (DEBUG_COMPUTE_FONT_METRICS) System.out.println(" - " + fswr.fontSize + ": " + fswr.getWordWeightRelation() + ", " + fswr.getWordStemWidth() + " based on " + fswr.wordCount + " words");
				fontSizeWeightRelations[fswr.fontSize - minFontSize] = fswr.getWordWeightRelation();
				fontSizeStemWidths[fswr.fontSize - minFontSize] = fswr.getWordStemWidth();
			}
		}
		
		//	extrapolate weight relations for font sizes with few representatives
		extrapolateFontSizeWeightRelations(fontSizeWeightRelations, minFontSize);
		
		//	extrapolate stem widths
		extrapolateFontSizeStemWidths(fontSizeStemWidths, minFontSize);
		
		//	try and find bold words based on being considerably heavier in comparison to plain renderings than average
		spm.setStep("Detecting bold words");
		spm.setBaseProgress(85);
		spm.setMaxProgress(95);
		for (int w = 0; w < wordImages.size(); w++) {
			WordImage wi = ((WordImage) wordImages.get(w));
			if (DEBUG_COMPUTE_FONT_METRICS) System.out.println("Assessing word '" + wi.str + "' for bold face");
			
			//	update progress
			spm.setProgress((w * 100) / wordImages.size());
			
			//	do actual assessment
			assessBoldFace(wi, ((WordImageMatch) renderedMatchesByWords.get(wi.word.getLocalID())), fontSizeWeightRelations, fontSizeStemWidths, minFontSize, wordImageCache, spm);
		}
		
		//	extrapolate bold words
		pm.setStep("Extrapolating bold words");
		pm.setBaseProgress(95);
		pm.setMaxProgress(100);
		ParallelJobRunner.runParallelFor(new ParallelFor() {
			public void doFor(int pg) throws Exception {
				
				//	update progress
				spm.setInfo("Extrapolating bold words on page " + pg);
				spm.setProgress((pg * 100) / pages.length);
				
				//	work on individual paragraphs
				ImRegion[] pageParagraphs = pages[pg].getRegions(ImRegion.PARAGRAPH_TYPE);
				Arrays.sort(pageParagraphs, ImUtils.topDownOrder);
				for (int p = 0; p < pageParagraphs.length; p++) {
					ImWord[] paragraphWords = pageParagraphs[p].getWords();
					ImUtils.sortLeftRightTopDown(paragraphWords);
					
					//	run through words
					for (int w = 0; w < paragraphWords.length; w++) {
						
						//	we've already handled this one
						if (!Gamta.isPunctuation(paragraphWords[w].getString()))
							continue;
						
						//	check if we have a bold predecessor
						boolean boldBefore = true;
						for (int l = (w-1); l >= 0; l--)
							if (!Gamta.isPunctuation(paragraphWords[l].getString())) {
								boldBefore = paragraphWords[l].hasAttribute(ImWord.BOLD_ATTRIBUTE);
								break;
							}
						
						//	check if we have a bold successor
						boolean boldAfter = true;
						for (int l = (w+1); l < paragraphWords.length; l++)
							if (!Gamta.isPunctuation(paragraphWords[l].getString())) {
								boldAfter = paragraphWords[l].hasAttribute(ImWord.BOLD_ATTRIBUTE);
								break;
							}
						
						//	this one looks good
						if ((paragraphWords.length != 1) && boldBefore && boldAfter) {
							paragraphWords[w].setAttribute(ImWord.BOLD_ATTRIBUTE);
							spm.setStep("Extrapolated bold face to word '" + paragraphWords[w].getString() + "' on page " + paragraphWords[w].pageId + " at " + paragraphWords[w].bounds);
						}
					}
				}
			}
		}, pages.length,  (useMultipleCores ? -1 : 1));
	}
	
	/**
	 * Analyze font metrics in a single region of an Image Markup document.
	 * This method relies on OCR results for comparison. It assumes that the
	 * argument region represents a single block of text, in a single font
	 * size.
	 * @param region the region whose font metrics to analyze
	 * @param pm a progress monitor to observe the processing
	 */
	public static void analyzeFontMetrics(ImRegion region, ProgressMonitor pm) {
		analyzeFontMetrics(region, pm, true, true, true);
	}
	
	/**
	 * Analyze font metrics in a single region of an Image Markup document.
	 * This method relies on OCR results for comparison. It assumes that the
	 * argument region represents a single block of text, in a single font
	 * size.
	 * @param region the region whose font metrics to analyze
	 * @param pm a progress monitor to observe the processing
	 * @param assessBold find and mark words in bold (using width of vertical
	 *            stems in characters in comparison to document average)
	 * @param assessItalics find and mark words in italics (using contrast
	 *            of histograms over number of pixels in columns)
	 * @param measureFontSize approximate the font sizes of words (based upon
	 *            line height and scan resolution)
	 */
	public static void analyzeFontMetrics(ImRegion region, ProgressMonitor pm, boolean assessBold, boolean assessItalics, boolean measureFontSize) {
		
		//	check if we're actually supposed to do anything
		if (measureFontSize) {}
		else if (assessBold) {}
		else if (assessItalics) {}
		else return; // no measurement selected at all
		
		//	get words
		ImWord[] regionWords = region.getWords();
		if (regionWords.length == 0)
			return;
		
		//	get word images, and assess italics
		pm.setStep("Extracting word images");
		pm.setBaseProgress(0);
		pm.setMaxProgress(50);
		final Map wordImageCache = Collections.synchronizedMap(new HashMap());
		final Map renderedMatchesByWords = Collections.synchronizedMap(new HashMap());
		
		//	get word images for current page
		WordImage[] regionWordImages = getWordImages(region);
		pm.setInfo(" ==> got " + regionWordImages.length + " word images");
		
		//	match word images against rendered OCR result for italics detection
		assessItalicsAndFontSize(regionWordImages, renderedMatchesByWords, wordImageCache, assessItalics, measureFontSize);
		
		//	average out font sizes inside paragraphs (should be rather consistent)
		if (measureFontSize) {
			pm.setStep("Computing paragraph font size");
			pm.setBaseProgress(50);
			pm.setMaxProgress(75);
			if (regionWords.length != 0) {
				if (DEBUG_COMPUTE_FONT_METRICS) System.out.println("Assessing font size");
				assessFontSize(regionWords);
			}
		}
		
		//	anything left to do?
		if (!assessBold)
			return;
		
		/* Average out weight differences between font sizes
		 * - relative weight generally differs between words (ones with no
		 *   ascenders and descenders are relatively heavier due to smaller box)
		 * - relation of relative weight across font sizes should be about the
		 *   same, however
		 * ==> DOESN'T SEEM TO BE THE CASE, THEN ...
		 * ==> AND THEN, IT ACTUALLY IS THE CASE FOR FONT SIZES WITH MANY
		 *     REPRESENTATIVES (at least, say, 3% of document words)
		 */
		pm.setStep("Computing average font weights");
		pm.setBaseProgress(75);
		pm.setMaxProgress(85);
		TreeMap fontSizesToWeightRelations = new TreeMap();
		int weightRelationWordCount = 0;
		int minFontSize = Integer.MAX_VALUE;
		int maxFontSize = 0;
		for (int w = 0; w < regionWordImages.length; w++) {
			WordImage wi = regionWordImages[w];
			
			//	update progress
			pm.setProgress((w * 100) / regionWordImages.length);
			
			//	ignore punctuation marks (for now, at least)
			if ((wi.box.getWidth() < (wi.pageImageDpi / 30)) || (wi.box.getHeight() < (wi.pageImageDpi / 20)))
				continue;
			
			//	ignore punctuation marks, at least for now
			if (Gamta.isPunctuation(wi.str))
				continue;
			
			//	little we can do without font size
			if (!wi.word.hasAttribute(ImWord.FONT_SIZE_ATTRIBUTE))
				continue;
			
			//	get match to non-bold rendering (might be italics or not)
			WordImageMatch wim = ((WordImageMatch) renderedMatchesByWords.get(wi.word.getLocalID()));
			if (wim == null)
				continue;
			
			//	get word font size
			int wordFontSize = wi.word.getFontSize();
			minFontSize = Math.min(minFontSize, wordFontSize);
			maxFontSize = Math.max(maxFontSize, wordFontSize);
			
			//	record image word weight
			FontSizeWeightRelation fswr = ((FontSizeWeightRelation) fontSizesToWeightRelations.get(wordFontSize));
			if (fswr == null) {
				fswr = new FontSizeWeightRelation(wordFontSize);
				fontSizesToWeightRelations.put(Integer.valueOf(wordFontSize), fswr);
			}
			fswr.addWord(wim);
			weightRelationWordCount++;
		}
		
		//	any font sizes to work with?
		if (maxFontSize < minFontSize)
			return;
		
		//	average out image font size weights and stem widths
		float[] fontSizeWeightRelations = new float[maxFontSize - minFontSize + 1];
		Arrays.fill(fontSizeWeightRelations, -1);
		float[] fontSizeStemWidths = new float[maxFontSize - minFontSize + 1];
		Arrays.fill(fontSizeStemWidths, -1);
		
		//	assign weight relations to font sizes with sufficient number of representatives
		if (DEBUG_COMPUTE_FONT_METRICS) System.out.println("Relative image word weights and stem widths by font sizes:");
		for (Iterator fsit = fontSizesToWeightRelations.keySet().iterator(); fsit.hasNext();) {
			FontSizeWeightRelation fswr = ((FontSizeWeightRelation) fontSizesToWeightRelations.get(fsit.next()));
			if ((fswr.wordCount * 33) >= weightRelationWordCount) {
				if (DEBUG_COMPUTE_FONT_METRICS) System.out.println(" - " + fswr.fontSize + ": " + fswr.getWordWeightRelation() + ", " + fswr.getWordStemWidth() + " based on " + fswr.wordCount + " words");
				fontSizeWeightRelations[fswr.fontSize - minFontSize] = fswr.getWordWeightRelation();
				fontSizeStemWidths[fswr.fontSize - minFontSize] = fswr.getWordStemWidth();
			}
		}
		
		//	extrapolate weight relations for font sizes with few representatives
		extrapolateFontSizeWeightRelations(fontSizeWeightRelations, minFontSize);
		
		//	extrapolate stem widths
		extrapolateFontSizeStemWidths(fontSizeStemWidths, minFontSize);
		
		//	try and find bold words based on being considerably heavier in comparison to plain renderings than average
		pm.setStep("Detecting bold words");
		pm.setBaseProgress(85);
		pm.setMaxProgress(95);
		for (int w = 0; w < regionWordImages.length; w++) {
			WordImage wi = regionWordImages[w];
			if (DEBUG_COMPUTE_FONT_METRICS) System.out.println("Assessing word '" + wi.str + "' for bold face");
			
			//	update progress
			pm.setProgress((w * 100) / regionWordImages.length);
			
			//	do actual assessment
			assessBoldFace(wi, ((WordImageMatch) renderedMatchesByWords.get(wi.word.getLocalID())), fontSizeWeightRelations, fontSizeStemWidths, minFontSize, wordImageCache, pm);
		}
		
		//	extrapolate bold face to punctuation marks that lie between two bold words or numbers
		pm.setStep("Extrapolating bold words");
		pm.setBaseProgress(95);
		pm.setMaxProgress(100);
		ImUtils.sortLeftRightTopDown(regionWords);
		for (int w = 0; w < regionWords.length; w++) {
			
			//	update progress
			pm.setProgress((w * 100) / regionWordImages.length);
			
			//	we've already handled this one
			if (!Gamta.isPunctuation(regionWords[w].getString()))
				continue;
			
			//	check if we have a bold predecessor
			boolean boldBefore = true;
			for (int l = (w-1); l >= 0; l--)
				if (!Gamta.isPunctuation(regionWords[l].getString())) {
					boldBefore = regionWords[l].hasAttribute(ImWord.BOLD_ATTRIBUTE);
					break;
				}
			
			//	check if we have a bold successor
			boolean boldAfter = true;
			for (int l = (w+1); l < regionWords.length; l++)
				if (!Gamta.isPunctuation(regionWords[l].getString())) {
					boldAfter = regionWords[l].hasAttribute(ImWord.BOLD_ATTRIBUTE);
					break;
				}
			
			//	this one looks good
			if ((regionWords.length != 1) && boldBefore && boldAfter) {
				regionWords[w].setAttribute(ImWord.BOLD_ATTRIBUTE);
				pm.setStep("Extrapolated bold face to word '" + regionWords[w].getString() + "' on page " + regionWords[w].pageId + " at " + regionWords[w].bounds);
			}
		}
	}
	
	/*
Analyze sequence of block line heights (as now printed for logging):
- identify jumps of line height by DPI/72 pixels (one full Cicero) or more:
  - for individual lines
  - for average of two adjacent lines on either side
    - fall back to single line height at block edges
  - for average of three adjacent lines on either side
    - fall back to two line average at block edges
- actual jumps should show in all three computations
  ==> test it !!!
- group lines based upon identified jumps ...
- ... and compute font size(s) from average height of each group ...
- ... for entirety of group
==> fewer font sizes
==> better basis for bold face detection (hopefully)

Also use x-height for line height grouping:
- varies less with ascenders, descenders, overshoots, and even line skew
- measures quite accurately with region coloring
  ==> should make for decent additional safety net for line height grouping
==> store x-height in line attribute on OCR adjustment
- simply extrapolate to short lines from line above it if differing ...
- ... unless difference significant (heading vs. sub heading) ...
- ... or line distance way above average (might be narrowly spaced heading) ...
  ==> should help smooth out inaccuracies owed to small sample size
  ==> should be rather safe, as font sizes hardly ever change on line wrap
==> not a reliable basis for font size computation (varies between fonts) ...
==> ... but good for backing up line grouping (fonts don't change between lines with semantic coherence between them, let along in mid sentence ...)
  ==> base font size computation on distance between cap height and lower line boundary in block line extraction
  ==> use difference between cap height and x-height for extrapolating descender (as ascender and descender symmetric in very most fonts)
==> also store region coloring measured cap height as line attribute ...
==> ... and extrapolate from line above just like x-height (with same blocker criteria as for latter)
	 */
	
	private static void assessItalicsAndFontSize(WordImage[] wordImages, Map renderedMatchesByWords, Map wordImageCache, boolean assessItalics, boolean measureFontSize) {
		
		//	TODO assess line height distribution
		ImPage page = wordImages[0].word.getPage();
		ImRegion[] pageBlocks = page.getRegions(ImRegion.BLOCK_ANNOTATION_TYPE);
		CountingSet pageWordHeights = new CountingSet(new TreeMap());
		for (int b = 0; b < pageBlocks.length; b++) {
			ImWord[] blockWords = pageBlocks[b].getWords();
			CountingSet blockWordHeights = new CountingSet(new TreeMap());
			CountingSet blockWordCapHeights = new CountingSet(new TreeMap());
			CountingSet blockWordXHeights = new CountingSet(new TreeMap());
			for (int w = 0; w < blockWords.length; w++) {
//				WordImage wi = wordImages[w];
//				wordHeights.add(new Integer(wi.word.bounds.getHeight()));
				blockWordHeights.add(new Integer(blockWords[w].bounds.getHeight()));
				if (blockWords[w].hasAttribute("capHeight"))
					blockWordCapHeights.add(new Integer((String) blockWords[w].getAttribute("capHeight")));
				if (blockWords[w].hasAttribute("xHeight"))
					blockWordXHeights.add(new Integer((String) blockWords[w].getAttribute("xHeight")));
				pageWordHeights.add(new Integer(blockWords[w].bounds.getHeight()));
			}
			ImRegion[] blockLines = page.getRegionsInside(ImRegion.LINE_ANNOTATION_TYPE, pageBlocks[b].bounds, true);
			Arrays.sort(blockLines, ImUtils.topDownOrder);
			int[] blockLineHeights = new int[blockLines.length];
			for (int l = 0; l < blockLines.length; l++)
				blockLineHeights[l] = blockLines[l].bounds.getHeight();
			int[] blockLineHeightsS2u = new int[blockLines.length];
			int[] blockLineHeightsS2l = new int[blockLines.length];
			int[] blockLineHeightsS3u = new int[blockLines.length];
			int[] blockLineHeightsS3l = new int[blockLines.length];
			for (int l = 0; l < blockLines.length; l++) {
				if (l == 0)
					blockLineHeightsS2u[l] = blockLineHeights[l];
				else blockLineHeightsS2u[l] = ((blockLineHeights[l] + blockLineHeights[l-1]) / 2);
				if ((l+1) == blockLines.length)
					blockLineHeightsS2l[l] = blockLineHeights[l];
				else blockLineHeightsS2l[l] = ((blockLineHeights[l] + blockLineHeights[l+1]) / 2);
				
				if (l == 0)
					blockLineHeightsS3u[l] = blockLineHeights[l];
				else if (l == 1)
					blockLineHeightsS3u[l] = ((blockLineHeights[l] + blockLineHeights[l-1]) / 2);
				else blockLineHeightsS3u[l] = ((blockLineHeights[l] + blockLineHeights[l-1] + blockLineHeights[l-2] + 1) / 3);
				if ((l+1) == blockLines.length)
					blockLineHeightsS3l[l] = blockLineHeights[l];
				else if ((l+2) == blockLines.length)
					blockLineHeightsS3l[l] = ((blockLineHeights[l] + blockLineHeights[l+1]) / 2);
				else blockLineHeightsS3l[l] = ((blockLineHeights[l] + blockLineHeights[l+1] + blockLineHeights[l+2] + 1) / 3);
			}
			if (DEBUG_COMPUTE_FONT_METRICS) {
				System.out.println("Got " + blockWordHeights.size() + " word heights in " + pageBlocks[b].bounds + ", " + blockWordHeights.elementCount() + " distinct ones:");
				System.out.println("  word heights: " + blockWordHeights);
				System.out.println("  cap heights: " + blockWordCapHeights);
				System.out.println("  x-heights: " + blockWordXHeights);
				System.out.println("  line heights:             " + Arrays.toString(blockLineHeights));
				System.out.println("  2u-smoothed line heights: " + Arrays.toString(blockLineHeightsS2u));
				System.out.println("  2l-smoothed line heights: " + Arrays.toString(blockLineHeightsS2l));
				System.out.println("  3u-smoothed line heights: " + Arrays.toString(blockLineHeightsS3u));
				System.out.println("  3l-smoothed line heights: " + Arrays.toString(blockLineHeightsS3l));
			}
		}
//		int minPageWordHeight = ((Integer) pageWordHeights.first()).intValue();
//		int maxPageWordHeight = ((Integer) pageWordHeights.last()).intValue();
//		BufferedImage whbi = new BufferedImage(page.bounds.getWidth(), page.bounds.getHeight(), BufferedImage.TYPE_INT_ARGB);
//		Graphics whg = whbi.getGraphics();
//		whg.setColor(Color.WHITE);
//		whg.fillRect(0, 0, whbi.getWidth(), whbi.getHeight());
//		for (int w = 0; w < wordImages.length; w++) {
//			BoundingBox wb = wordImages[w].word.bounds;
//			Color whc = new Color(Color.HSBtoRGB(((float) (wb.getHeight() - minPageWordHeight) / (maxPageWordHeight - minPageWordHeight + 1)), 1, 1));
//			whg.setColor(whc);
//			whg.fillRect(wb.left, wb.top, wb.getWidth(), wb.getHeight());
//		}
//		ImageDisplayDialog idd = new ImageDisplayDialog("Word Height Distribution");
//		idd.addImage(whbi, "");
//		idd.setSize(1000, 1000);
//		idd.setLocationRelativeTo(null);
//		idd.setVisible(true);
		
		//	match word images against rendered OCR result for italics detection
		for (int w = 0; w < wordImages.length; w++) {
			WordImage wi = wordImages[w];
			if (DEBUG_COMPUTE_FONT_METRICS) {
				System.out.println("Assessing font sytle of word '" + wi.word.getString() + "' at " + wi.word.bounds + " with baseline " + wi.word.getAttribute(ImWord.BASELINE_ATTRIBUTE));
				System.out.println(" - word image is " + wi.box);
			}
			
			//	ignore punctuation marks (for now, at least) TODO make sure not to ignore small-print letters
			if ((wi.box.getWidth() < (wi.pageImageDpi / 50)) || (wi.box.getHeight() < (wi.pageImageDpi / 25))) {
				if (DEBUG_COMPUTE_FONT_METRICS) System.out.println(" ==> punctuation mark (size), ignored");
				continue;
			}
			
			//	ignore punctuation marks TODO use normalized check !!!
			if (Gamta.isPunctuation(Gamta.normalize(wi.str))) {
				if (DEBUG_COMPUTE_FONT_METRICS) System.out.println(" ==> punctuation mark (OCR), ignored");
				continue;
			}
			
			//	get and check word string (no use rendering whitespace)
			String wordString = wi.word.getString();
			if (wordString == null)
				continue;
			wordString = wordString.trim();
			if (wordString.length() == 0)
				continue;
//			
//			//	get plain or italics super-rendering
//			WordImageMatch wim = getPlainOrItalicsMatch(wi, wordImageCache);
//			if (wim == null)
//				continue;
//			
//			//	store rendering and adjust word attributes if required
//			renderedMatchesByWords.put(wi.word.getLocalID(), wim);
//			if (!Gamta.isNumber(wi.str) && (wim.rendered.isItalics != wi.word.hasAttribute(ImWord.ITALICS_ATTRIBUTE))) {
//				if (wim.rendered.isItalics)
//					wi.word.setAttribute(ImWord.ITALICS_ATTRIBUTE);
//				else wi.word.removeAttribute(ImWord.ITALICS_ATTRIBUTE);
//				wordImages[w] = new WordImage(wi.img, wi.word, wi.baseline, wi.pageImageDpi);
//				if (DEBUG_COMPUTE_FONT_METRICS) System.out.println(" ==> italics property adjusted");
//			}
//			wi.word.setFontSize(wim.rendered.fontSize);
//			if (DEBUG_COMPUTE_FONT_METRICS) System.out.println(" ==> font size set to " + wim.rendered.fontSize);
			
			//	get plain or italics super-rendering (need that in every case, as it does the match rendering)
			float italicsScore = getItalicsScore(wi, wordImageCache, renderedMatchesByWords);
			if (italicsScore < 0)
				continue; // something's wrong
			
			//	store rendering and adjust word attributes if required
			if (assessItalics) {
				wi.word.setAttribute((ImWord.ITALICS_ATTRIBUTE + "Score"), ("" + italicsScore));
				if (!Gamta.isNumber(wi.str) && ((0.5 <= italicsScore) != wi.word.hasAttribute(ImWord.ITALICS_ATTRIBUTE))) {
					if (italicsScore < 0.5)
						wi.word.removeAttribute(ImWord.ITALICS_ATTRIBUTE);
					else wi.word.setAttribute(ImWord.ITALICS_ATTRIBUTE);
					wordImages[w] = new WordImage(wi.img, wi.word, wi.baseline, wi.pageImageDpi);
					if (DEBUG_COMPUTE_FONT_METRICS) System.out.println(" ==> italics property adjusted");
				}
			}
			
			//	compute font size as function of line height instead
			if (measureFontSize && !wi.word.hasAttribute(ImWord.FONT_SIZE_ATTRIBUTE)) {
				WordImageMatch wim = ((WordImageMatch) renderedMatchesByWords.get(wi.word.getLocalID()));
				wi.word.setFontSize(wim.rendered.fontSize);
				float lineHeightFontSizeRaw = ((wi.word.bounds.getHeight() * 72.0f) / wi.pageImageDpi);
				int lineHeightFontSize = Math.round(lineHeightFontSizeRaw);
				wi.word.setFontSize(lineHeightFontSize);
				if (DEBUG_COMPUTE_FONT_METRICS) {
					System.out.println(" ==> rendered font size is " + ((wim == null) ? -1 : wim.rendered.fontSize));
					System.out.println(" ==> line height font size is " + lineHeightFontSize + " (" + lineHeightFontSizeRaw + ")");
				}
			}
		}
	}
	
	private static float getItalicsScore(WordImage wi, Map wordImageCache, Map renderedMatchesByWords) {
		
		//	render word string to word bounds (always try plain and italics)
		WordImage prwi = renderWordImage(wi.word, wi.baseline, Font.PLAIN, 10 /* start with highly usual font size */, wi.pageImageDpi, wordImageCache);
		WordImage irwi = renderWordImage(wi.word, wi.baseline, Font.ITALIC, 10 /* start with highly usual font size */, wi.pageImageDpi, wordImageCache);
		
		//	also shear word image to left by about 14.5° (to get potential italics upright)
		BufferedImage swbi = new BufferedImage(
				(wi.img.getWidth() + (wi.img.getHeight() / 3)),
				wi.img.getHeight(),
				wi.img.getType()
			);
		Graphics2D swg = swbi.createGraphics();
		swg.setColor(Color.WHITE);
		swg.fillRect(0, 0, swbi.getWidth(), swbi.getHeight());
		swg.shear(Math.sin((Math.PI * 14.5) / 180), 0);
		swg.drawImage(wi.img, 0, 0, null);
		WordImage swi = new WordImage(swbi, wi.word, wi.baseline, wi.pageImageDpi);
		float pcs = getColOccupationContrastScore(wi);
		float scs = getColOccupationContrastScore(swi);
		float ccs = (scs / (pcs + scs)); // combined and normalized score
		if (DEBUG_COMPUTE_FONT_METRICS) {
			System.out.println(" - column occupation contrast score is " + pcs + " plain, " + scs + " sheared --> " + ccs);
			System.out.println("   aggregate column occupation contrast score is  " + ccs);
		}
		
		//	create overlay (code copied from char decoder, for starters)
		try {
			WordImageMatch pwim = matchWordImages(wi, prwi, true, true, true);
			if (DEBUG_COMPUTE_FONT_METRICS)
				System.out.println(" - plain simiparity is " + pwim.sim + ", " + pwim.matched + " matched, " + pwim.scannedOnly + " only in scan, " + pwim.renderedOnly + " only in rendered");
			WordImageMatch iwim = matchWordImages(wi, irwi, true, true, true);
			if (DEBUG_COMPUTE_FONT_METRICS)
				System.out.println(" - italic simiparity is " + iwim.sim + ", " + iwim.matched + " matched, " + iwim.scannedOnly + " only in scan, " + iwim.renderedOnly + " only in rendered");
			WordImageMatch swim = matchWordImages(swi, prwi, true, true, true);
			if (DEBUG_COMPUTE_FONT_METRICS)
				System.out.println(" - sheared simiparity is " + swim.sim + ", " + swim.matched + " matched, " + swim.scannedOnly + " only in scan, " + swim.renderedOnly + " only in rendered");
			float pirs = (iwim.sim / (pwim.sim + iwim.sim)); // combined and normalized score
			float psrs = (swim.sim / (pwim.sim + swim.sim)); // combined and normalized score
			if (DEBUG_COMPUTE_FONT_METRICS)
				System.out.println("   aggregate rendering similarity score is " + pirs + " for italics, " + psrs + " for shearing");
			float pre = (1 - pwim.sim); // remaining error
			float ire = (1 - iwim.sim); // remaining error
			float sre = (1 - swim.sim); // remaining error
			float pire = (ire / (pre + ire)); // combined and normalized remaining error
			float psre = (sre / (pre + sre)); // combined and normalized remaining error
			float pies = (1 - pire); // combined and normalized score
			float pses = (1 - psre); // combined and normalized score
			if (DEBUG_COMPUTE_FONT_METRICS)
				System.out.println("   aggregate rendering error score is " + pies + " for italics, " + pses + " for shearing");
			float pirw = ((pwim.sim + iwim.sim) / 2);
			float psrw = ((pwim.sim + swim.sim) / 2);
			float pias = ((pirs * pirw) + (ccs * (1 - pirw)));
			float psas = ((psrs * psrw) + (ccs * (1 - psrw)));
			if (DEBUG_COMPUTE_FONT_METRICS)
				System.out.println("   aggregate score is " + pias + " with italics, " + psas + " with shearing");
//			if (DEBUG_COMPUTE_FONT_METRICS && (((ccs < 0.5) != (pirs < 0.5)) || ((ccs < 0.5) != (psrs < 0.5))))
//				displayWordMatches(pwim, iwim, swim, "Ambiguous italics check");
//			return ((pwim.sim < iwim.sim) ? iwim : pwim);
			float as = ((pias + psas) / 2);
			renderedMatchesByWords.put(wi.word.getLocalID(), ((as < 0.5) ? pwim : iwim));
			return as;
		}
		catch (Exception e) {
			e.printStackTrace(System.out);
			return -1;
		}
	}
	
	private static float getColOccupationContrastScore(WordImage wi) {
		float diffSquareSum = 0;
		for (int c = 0; c < wi.colBrightnessHist.length; c++) {
			int diff = ((c == 0) ? wi.colBrightnessHist[c] : (wi.colBrightnessHist[c] - wi.colBrightnessHist[c-1]));
			diffSquareSum += (diff * diff);
		}
		return (diffSquareSum / wi.colBrightnessHist.length);
	}
	
	private static void displayWordMatches(WordImageMatch pwim, WordImageMatch iwim, WordImageMatch swim, String message) {
		BufferedImage pmi = getWordMatchImage(pwim);
		BufferedImage imi = getWordMatchImage(iwim);
		BufferedImage smi = getWordMatchImage(swim);
		BufferedImage mi = new BufferedImage(Math.max(pmi.getWidth(), Math.max(imi.getWidth(), smi.getWidth())), (pmi.getHeight() + 2 + imi.getHeight() + 2 + smi.getHeight()), BufferedImage.TYPE_INT_ARGB);
		Graphics mig = mi.createGraphics();
		mig.drawImage(pmi, 0, 0, null);
		mig.drawImage(imi, 0, (pmi.getHeight() + 2), null);
		mig.drawImage(smi, 0, (pmi.getHeight() + 2 + imi.getHeight() + 2), null);
		JOptionPane.showMessageDialog(null, (message + ": '" + pwim.rendered.str + "'\nplain similarity is " + pwim.sim + "\n" + pwim.matched + "-" + pwim.scannedOnly + "-" + pwim.renderedOnly + "\nitalics similarity is " + iwim.sim + "\n" + iwim.matched + "-" + iwim.scannedOnly + "-" + iwim.renderedOnly), "Comparison Image", JOptionPane.PLAIN_MESSAGE, new ImageIcon(mi));
	}
	private static final int wordImageMargin = 10;
	private static BufferedImage getWordMatchImage(WordImageMatch wim) {
		BufferedImage bi = new BufferedImage(
				(wim.scanned.img.getWidth() + wordImageMargin + wim.rendered.img.getWidth() + wordImageMargin + wim.matchData.length),
				(Math.max(wim.scanned.img.getHeight(), wim.rendered.img.getHeight()) + wordImageMargin + Math.max(wim.scanned.img.getHeight(), wim.rendered.img.getHeight())),
				BufferedImage.TYPE_INT_RGB
			);
		Graphics g = bi.getGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, bi.getWidth(), bi.getHeight());
		g.drawImage(wim.scanned.img, 0, 0, null);
		g.drawImage(wim.rendered.img, (wim.scanned.img.getWidth() + wordImageMargin), 0, null);
		g.setColor(Color.MAGENTA);
		if (0 < wim.scanned.baseline)
			g.drawLine(0, (wim.scanned.baseline - wim.scanned.word.bounds.top), wim.scanned.img.getWidth(), (wim.scanned.baseline - wim.scanned.word.bounds.top));
		if (0 < wim.rendered.baseline)
			g.drawLine((wim.scanned.img.getWidth() + wordImageMargin), wim.rendered.baseline, ((wim.scanned.img.getWidth() + wordImageMargin) + wim.rendered.img.getWidth()), wim.rendered.baseline);
		
		for (int x = 0; x < wim.matchData.length; x++) {
			for (int y = 0; y < wim.matchData[x].length; y++) {
				Color c = null;
				if (wim.matchData[x][y] == WordImageAnalysis.WORD_IMAGE_MATCH_MATCHED)
					c = Color.BLACK;
				else if (wim.matchData[x][y] == WordImageAnalysis.WORD_IMAGE_MATCH_SCANNED_ONLY)
					c = Color.GREEN;
				else if (wim.matchData[x][y] == WordImageAnalysis.WORD_IMAGE_MATCH_RENDERED_ONLY)
					c = Color.RED;
				if (c != null)
					bi.setRGB(
							(wim.scanned.img.getWidth() + wordImageMargin + wim.rendered.img.getWidth() + wordImageMargin + x),
							y,
							c.getRGB()
						);
			}
			
			for (int c = 0; c < wim.scanned.colBrightnessHist.length; c++) {
				int y = bi.getHeight();
				for (int h = 0; h < wim.scanned.colBrightnessHist[c]; h++)
					bi.setRGB(
							c,
							(--y),
							Color.BLACK.getRGB()
						);
			}
			
			for (int c = 0; c < wim.rendered.colBrightnessHist.length; c++) {
				int y = bi.getHeight();
				for (int r = 0; r < wim.rendered.colBrightnessHist[c]; r++)
					bi.setRGB(
							(wim.scanned.img.getWidth() + wordImageMargin + c),
							(--y),
							Color.BLACK.getRGB()
						);
			}
//			
//			for (int c = 0; c < wim.matchedHist.length; c++) {
//				int y = bi.getHeight();
//				for (int r = 0; r < wim.renderedOnlyHist[c]; r++)
//					bi.setRGB(
//							(wim.base.scanned.img.getWidth() + wordImageMargin + wim.base.rendered.img.getWidth() + wordImageMargin + c),
//							(--y),
//							Color.RED.getRGB()
//						);
//				for (int s = 0; s < wim.scannedOnlyHist[c]; s++)
//					bi.setRGB(
//							(wim.base.scanned.img.getWidth() + wordImageMargin + wim.base.rendered.img.getWidth() + wordImageMargin + c),
//							(--y),
//							Color.GREEN.getRGB()
//						);
//				for (int m = 0; m < wim.matchedHist[c]; m++)
//					bi.setRGB(
//							(wim.base.scanned.img.getWidth() + wordImageMargin + wim.base.rendered.img.getWidth() + wordImageMargin + c),
//							(--y),
//							Color.BLACK.getRGB()
//						);
//			}
		}
		
		return bi;
	}
	
	private static void assessFontSize(ImWord[] words) {
		
		//	sum up word font sizes
		int wordFontSizeSum = 0;
		int fontSizeWordCount = 0;
		for (int w = 0; w < words.length; w++) {
			
			//	skip over artifacts
			if (ImWord.TEXT_STREAM_TYPE_ARTIFACT.equals(words[w].getTextStreamType()) || ImWord.TEXT_STREAM_TYPE_DELETED.equals(words[w].getTextStreamType()))
				continue;
			
			//	count in font size
			if (words[w].hasAttribute(ImWord.FONT_SIZE_ATTRIBUTE)) {
				wordFontSizeSum += words[w].getFontSize();
				fontSizeWordCount++;
			}
		}
		
		//	anything to work with?
		if (fontSizeWordCount == 0)
			return;
		
		//	compute and set average font size
		int paragraphFontSize = ((wordFontSizeSum + (fontSizeWordCount / 2)) / fontSizeWordCount);
		for (int w = 0; w < words.length; w++)
			words[w].setFontSize(paragraphFontSize);
		if (DEBUG_COMPUTE_FONT_METRICS) System.out.println(" ==> font size set to " + paragraphFontSize);
	}
	
	private static void assessBoldFace(WordImage wi, WordImageMatch wim, float[] fontSizeWeightRelations, float[] fontSizeStemWidths, int minFontSize, Map wordImageCache, ProgressMonitor pm) {
		
		//	little we can do about this one
		if (wim == null)
			return;
		
		//	ignore punctuation marks (for now, at least)
		if ((wi.box.getWidth() < (wi.pageImageDpi / 30)) || (wi.box.getHeight() < (wi.pageImageDpi / 20)))
			return;
		
		//	ignore punctuation marks, at least for now
		if (Gamta.isPunctuation(wi.str))
			return;
		
		//	little we can do without font size
		if (!wi.word.hasAttribute(ImWord.FONT_SIZE_ATTRIBUTE))
			return;
		int wordFontSize = wi.word.getFontSize();
		
		//	compute relative weights against plain rendering
		float avgFontSizeWeightRelation = fontSizeWeightRelations[wordFontSize - minFontSize];
		if (DEBUG_COMPUTE_FONT_METRICS) System.out.println(" - average image to rendered weight relation for font size " + wordFontSize + " is " + avgFontSizeWeightRelation);
		float plainWordWeightRelation = getWeightRelation(wim);
		if (DEBUG_COMPUTE_FONT_METRICS) System.out.println(" - word image to rendered weight relation is " + plainWordWeightRelation);
		if (DEBUG_COMPUTE_FONT_METRICS) System.out.println(" - word weight relation is " + (plainWordWeightRelation / avgFontSizeWeightRelation));
		
		/* Observations from 5 test documents:
		 * - 10% appears to be a suitable cutoff, if one that incurs a few
		 *   false negatives.
		 * - a 5% cutoff incurs no false negatives, but many false positives
		 * - false positives go up to 80% heavier, so no way of preventing
		 *   them altogether based on a threshold alone
		 * ==> use super-rendering for verification
		 */
		
		//	this one's less than 5% heavier than average, no way it's bold
		if ((avgFontSizeWeightRelation * 20) > (plainWordWeightRelation * 19)) {
			wi.word.removeAttribute(ImWord.BOLD_ATTRIBUTE);
			if (DEBUG_COMPUTE_FONT_METRICS) System.out.println(" ==> not bold");
			return;
		}
		
		//	compare word stem width to font size average
		float avgFontSizeStemWidth = fontSizeStemWidths[wordFontSize - minFontSize];
		if (DEBUG_COMPUTE_FONT_METRICS) System.out.println(" - average stem width for font size " + wordFontSize + " is " + avgFontSizeStemWidth);
		float wordStemWidth = getStemWidth(wi);
		if (DEBUG_COMPUTE_FONT_METRICS) System.out.println(" - word stem widht is " + wordStemWidth);
		if (DEBUG_COMPUTE_FONT_METRICS) System.out.println(" - word stem width relation is " + (wordStemWidth / avgFontSizeStemWidth));
		
		//	stem width less than 20% above average, no way this one's bold
		if ((avgFontSizeStemWidth * 5) > (wordStemWidth * 4)) {
			wi.word.removeAttribute(ImWord.BOLD_ATTRIBUTE);
			if (DEBUG_COMPUTE_FONT_METRICS) System.out.println(" ==> not bold");
			return;
		}
		
		//	render word in bold for comparison
		WordImage brwi = renderWordImage(wi.word, wi.baseline, (wim.rendered.fontStyle | Font.BOLD), wim.rendered.fontSize, wi.pageImageDpi, wordImageCache);
		WordImageMatch bwim = matchWordImages(wi, brwi, true, true, true);
		if (DEBUG_COMPUTE_FONT_METRICS) System.out.println(" - non-bold similarity is plain " + wim.sim + ", weight adjuted " + wim.waSim);
		if (DEBUG_COMPUTE_FONT_METRICS) System.out.println(" - bold similarity is plain " + bwim.sim + ", weight adjusted " + bwim.waSim);
		
		//	this one's less than 5% closer to bold than non-bold, doesn't look like bold (we require some significant distance, as bold has more pixels to match directly than plain, especially in the face of OCR errors)
		if ((wim.sim * 20) > (bwim.sim * 19)) {
			wi.word.removeAttribute(ImWord.BOLD_ATTRIBUTE);
			if (DEBUG_COMPUTE_FONT_METRICS) System.out.println(" ==> not bold");
			return;
		}
		
		//	this one's closer to non-bold than bold if adjusted for weight, doesn't look like bold
		if (wim.waSim > bwim.waSim) {
			wi.word.removeAttribute(ImWord.BOLD_ATTRIBUTE);
			if (DEBUG_COMPUTE_FONT_METRICS) System.out.println(" ==> not bold");
			return;
		}
		
		//	compute relative weights against bold rendering
		float boldWordWeightRelation = getWeightRelation(bwim);
		if (DEBUG_COMPUTE_FONT_METRICS) System.out.println(" - word image to bold rendered weight relation is " + boldWordWeightRelation);
		if (DEBUG_COMPUTE_FONT_METRICS) System.out.println(" - bold word weight relation is " + (boldWordWeightRelation / avgFontSizeWeightRelation));
		
		//	this one's lighter than 75% of weight relation for bold
		if ((avgFontSizeWeightRelation * 3) > (boldWordWeightRelation * 4)) {
			wi.word.removeAttribute(ImWord.BOLD_ATTRIBUTE);
			if (DEBUG_COMPUTE_FONT_METRICS) System.out.println(" ==> not bold");
			return;
		}
		
		//	this one does look like bold
		wi.word.setAttribute(ImWord.BOLD_ATTRIBUTE);
		if (DEBUG_COMPUTE_FONT_METRICS) System.out.println(" ==> bold");
		pm.setStep("Found bold word '" + wi.str + "' on page " + wi.word.pageId + " at " + wi.word.bounds);
	}
	
	private static final boolean DEBUG_COMPUTE_FONT_METRICS = true;
	
	private static class FontSizeWeightRelation {
		int fontSize;
		int wordCount = 0;
		
		float scannedPixelSum = 0;
		int scannedAreaSum = 0;
		float renderedPixelSum = 0;
		int renderedAreaSum = 0;
		
		float stemChunkPixelSum = 0;
		int stemChunkCount = 0;
		
		FontSizeWeightRelation(int fontSize) {
			this.fontSize = fontSize;
		}
		
		float getWordWeightRelation() {
			if (this.wordCount == 0)
				return 1;
			float scannedWeight = (this.scannedPixelSum / this.scannedAreaSum);
			float renderedWeight = (this.renderedPixelSum / this.renderedAreaSum);
			return (scannedWeight / renderedWeight);
		}
		float getWordStemWidth() {
			return ((this.stemChunkCount == 0) ? 0 : (this.stemChunkPixelSum / this.stemChunkCount));
		}
		
		void addWord(WordImageMatch wim) {
			this.wordCount++;
			
			//	measure scanned and rendered pixel counts
			this.scannedPixelSum += (wim.matched + wim.scannedOnly);
			this.scannedAreaSum += ((wim.matchData.length - Math.max(0, -wim.leftShift) - Math.max(0, -wim.leftShift)) * (wim.matchData[0].length - Math.max(0, -wim.topShift) - Math.max(0, -wim.bottomShift)));
			this.renderedPixelSum += (wim.matched + wim.renderedOnly);
			this.renderedAreaSum += ((wim.matchData.length - Math.max(0, wim.leftShift) - Math.max(0, wim.leftShift)) * (wim.matchData[0].length - Math.max(0, wim.topShift) - Math.max(0, wim.bottomShift)));
			
			//	count out lengths of horizontal pixel rows to measure stem width
			CountingSet stemWidths = new CountingSet(new TreeMap());
			int stemWidth = 0;
			for (int r = wim.scanned.box.getTopRow(); r < wim.scanned.box.getBottomRow(); r++) {
				for (int c = wim.scanned.box.getLeftCol(); c < wim.scanned.box.getRightCol(); c++) {
					if (wim.scanned.brightness[c][r] < 96)
						stemWidth++;
					else {
						if (stemWidth != 0)
							stemWidths.add(new Integer(stemWidth));
						stemWidth = 0;
					}
				}
				if (stemWidth != 0)
					stemWidths.add(new Integer(stemWidth));
				stemWidth = 0;
			}
			
			//	compute stem width from middle 60% of measured pixel rows
			int stemPixelCount = wim.scanned.pixelCount;
			
			//	remove top and bottom 20%, which likely result from horizontal parts of letters, or from tip
			int toRemoveTop = (stemWidths.size() / 5);
			int toRemoveBottom = (stemWidths.size() / 5);
			while (toRemoveTop != 0) {
				Integer removeStemWidth = ((Integer) stemWidths.last());
				stemWidths.remove(removeStemWidth);
				stemPixelCount -= removeStemWidth.intValue();
				toRemoveTop--;
			}
			while (toRemoveBottom != 0) {
				Integer removeStemWidth = ((Integer) stemWidths.first());
				stemWidths.remove(removeStemWidth);
				stemPixelCount -= removeStemWidth.intValue();
				toRemoveBottom--;
			}
			
			//	count remaining (middle) 60% into totals
			this.stemChunkPixelSum += stemPixelCount;
			this.stemChunkCount += stemWidths.size();
		}
	}
	
	private static float getWeightRelation(WordImageMatch wim) {
		float scannedWeight = (((float) (wim.matched + wim.scannedOnly)) / ((wim.matchData.length - Math.max(0, -wim.leftShift) - Math.max(0, -wim.leftShift)) * (wim.matchData[0].length - Math.max(0, -wim.topShift) - Math.max(0, -wim.bottomShift))));
		float renderedWeight = (((float) (wim.matched + wim.renderedOnly)) / ((wim.matchData.length - Math.max(0, wim.leftShift) - Math.max(0, wim.leftShift)) * (wim.matchData[0].length - Math.max(0, wim.topShift) - Math.max(0, wim.bottomShift))));
		return (scannedWeight / renderedWeight);
	}
	
	private static void extrapolateFontSizeWeightRelations(float[] fontSizeWeights, int minFontSize) {
		for (int s = 0; s < fontSizeWeights.length; s++) {
			if (0 < fontSizeWeights[s])
				continue;
			
			//	find next explicit weight below
			int downwardDist = 1;
			float downwardWeight = -1;
			while (downwardDist <= s) {
				if (0 < fontSizeWeights[s - downwardDist]) {
					downwardWeight = fontSizeWeights[s - downwardDist];
					break;
				}
				else downwardDist++;
			}
			
			//	find next explicit weight above
			int upwardDist = 1;
			float upwardWeight = -1;
			while (upwardDist < (fontSizeWeights.length - s)) {
				if (0 < fontSizeWeights[s + upwardDist]) {
					upwardWeight = fontSizeWeights[s + upwardDist];
					break;
				}
				else upwardDist++;
			}
			
			//	nothing found at all (unlikely, but better be sure)
			if ((downwardWeight < 0) && (upwardWeight < 0))
				continue;
			
			//	no explicit weight below, use weight above
			if (downwardWeight < 0)
				fontSizeWeights[s] = -upwardWeight;
			
			//	no explicit weight above, use weight below
			else if (upwardWeight < 0)
				fontSizeWeights[s] = -downwardWeight;
			
			//	explicit weights above and below, use distance weighted average (multiply weights with _opposing_ distance to give higher weight to closer weight)
			else fontSizeWeights[s] = -(((upwardWeight * downwardDist) + (downwardWeight * upwardDist)) / (downwardDist + upwardDist));
		}
		
		//	invert extrapolated font size weights (have to store them inverted on generation to keep them marked)
		for (int s = 0; s < fontSizeWeights.length; s++) {
			if (0 < fontSizeWeights[s]) {
				if (DEBUG_COMPUTE_FONT_METRICS) System.out.println(" --> " + (s + minFontSize) + " computed as " + fontSizeWeights[s]);
			}
			else {
				fontSizeWeights[s] = -fontSizeWeights[s];
				if (DEBUG_COMPUTE_FONT_METRICS) System.out.println(" --> " + (s + minFontSize) + " extrapolated to " + fontSizeWeights[s]);
			}
		}
	}
	
	private static float getStemWidth(WordImage wi) {
		
		//	count out lengths of horizontal pixel rows to measure stem width
		CountingSet stemWidths = new CountingSet(new TreeMap());
		int stemWidth = 0;
		for (int r = wi.box.getTopRow(); r < wi.box.getBottomRow(); r++) {
			for (int c = wi.box.getLeftCol(); c < wi.box.getRightCol(); c++) {
				if (wi.brightness[c][r] < 96)
					stemWidth++;
				else {
					if (stemWidth != 0)
						stemWidths.add(new Integer(stemWidth));
					stemWidth = 0;
				}
			}
			if (stemWidth != 0)
				stemWidths.add(new Integer(stemWidth));
			stemWidth = 0;
		}
		
		//	compute stem width from middle 60% of measured pixel rows
		int stemPixelCount = wi.pixelCount;
		
		//	remove top and bottom 20%, which likely result from horizontal parts of letters, or from tips
		int toRemoveTop = (stemWidths.size() / 5);
		int toRemoveBottom = (stemWidths.size() / 5);
		while (toRemoveTop != 0) {
			Integer removeStemWidth = ((Integer) stemWidths.last());
			stemWidths.remove(removeStemWidth);
			stemPixelCount -= removeStemWidth.intValue();
			toRemoveTop--;
		}
		while (toRemoveBottom != 0) {
			Integer removeStemWidth = ((Integer) stemWidths.first());
			stemWidths.remove(removeStemWidth);
			stemPixelCount -= removeStemWidth.intValue();
			toRemoveBottom--;
		}
		
		//	return remaining (middle) 60%
		return (stemWidths.isEmpty() ? 0 : (((float) stemPixelCount) / stemWidths.size()));
	}
	
	private static void extrapolateFontSizeStemWidths(float[] fontSizeStemWidths, int minFontSize) {
		/* for this extrapolation, we assume that the relation of stem width to
		 * font size is relatively constant, even though it tends to be somewhat
		 * larger for smaller font sizes */
		for (int s = 0; s < fontSizeStemWidths.length; s++) {
			if (0 < fontSizeStemWidths[s])
				continue;
			
			//	find next explicit stem width below
			int downwardDist = 1;
			float downwardStemWidth = -1;
			while (downwardDist <= s) {
				if (0 < fontSizeStemWidths[s - downwardDist]) {
					downwardStemWidth = fontSizeStemWidths[s - downwardDist];
					break;
				}
				else downwardDist++;
			}
			
			//	find next explicit stem width above
			int upwardDist = 1;
			float upwardStemWidth = -1;
			while (upwardDist < (fontSizeStemWidths.length - s)) {
				if (0 < fontSizeStemWidths[s + upwardDist]) {
					upwardStemWidth = fontSizeStemWidths[s + upwardDist];
					break;
				}
				else upwardDist++;
			}
			
			//	nothing found at all (unlikely, but better be sure)
			if ((downwardStemWidth < 0) && (upwardStemWidth < 0))
				continue;
			
			//	no explicit weight below, use weight above
			if (downwardStemWidth < 0) {
				int upwardFontSize = (s + upwardDist + minFontSize);
				int fontSize = (s + minFontSize);
				fontSizeStemWidths[s] = -((upwardStemWidth * fontSize) / upwardFontSize);
			}
			
			//	no explicit weight above, use weight below
			else if (upwardStemWidth < 0) {
				int downwardFontSize = (s - downwardDist + minFontSize);
				int fontSize = (s + minFontSize);
				fontSizeStemWidths[s] = -((downwardStemWidth * fontSize) / downwardFontSize);
			}
			
			//	explicit weights above and below, use distance weighted average (multiply weights with _opposing_ distance to give higher weight to closer weight)
			else {
				int upwardFontSize = (s + upwardDist + minFontSize);
				int downwardFontSize = (s - downwardDist + minFontSize);
				int fontSize = (s + minFontSize);
				fontSizeStemWidths[s] = -(((((upwardStemWidth * fontSize) / upwardFontSize) * downwardDist) + (((downwardStemWidth * fontSize) / downwardFontSize) * upwardDist)) / (downwardDist + upwardDist));
			}
		}
		
		//	invert extrapolated stem widths (have to store them inverted on generation to keep them marked)
		for (int s = 0; s < fontSizeStemWidths.length; s++) {
			if (0 < fontSizeStemWidths[s]) {
				if (DEBUG_COMPUTE_FONT_METRICS) System.out.println(" --> " + (s + minFontSize) + " computed as " + fontSizeStemWidths[s]);
			}
			else {
				fontSizeStemWidths[s] = -fontSizeStemWidths[s];
				if (DEBUG_COMPUTE_FONT_METRICS) System.out.println(" --> " + (s + minFontSize) + " extrapolated to " + fontSizeStemWidths[s]);
			}
		}
	}
	
	/**
	 * The image of an individual word, either cut out from a scanned page
	 * image, or rendered from an OCR result string. This class adds several
	 * image metrics, like the brightness array as well as aggregations over
	 * it.
	 * 
	 * @author sautter
	 */
	public static class WordImage {
		
		/** the word the image belongs to (null for rendered word images) */
		public final ImWord word;
		/** the resolution of the underlying page image (-1 for rendered word images) */
		public final int pageImageDpi;
		
		/** the word string */
		public final String str;
		/** is the word in italics */
		public final boolean isItalics;
		/** the name of the font the word was rendered in (null in non-rendered word images) */
		public final String fontName;
		/** the style of the font the word was rendered in (-1 in non-rendered word images) */
		public final int fontStyle;
		/** the size of the font the word was rendered in (-1 in non-rendered word images) */
		public final int fontSize;
		
		/** the word image */
		public final BufferedImage img;
		/** the bounding rectangle of the actual word, inside the image proper */
		public final ImagePartRectangle box; // TODO consider using BoundingBox instead
		/** the word baseline */
		public final int baseline;
		/** the logarithm of the width/height relation of the bounding box */
		public final double boxProportion;
		/** the brightness array of the word image */
		public final byte[][] brightness;
		/** a histogram aggregating the number of non-white pixels in columns */
		public final int[] colBrightnessHist;
		/** a histogram aggregating the number of non-white pixels in rows */
		public final int[] rowBrightnessHist;
		/** the number of non-white pixels in the word image */
		public final int pixelCount;
		
		/**
		 * Constructor
		 * @param img the word image
		 * @param word the word the image belongs to
		 * @param baseline the word baseline
		 * @param pageImageDpi the resolution of the word image
		 */
		//	used for page image cut-outs
		public WordImage(BufferedImage img, ImWord word, int baseline, int pageImageDpi) {
			this(word, pageImageDpi, word.getString(), word.hasAttribute(ImWord.ITALICS_ATTRIBUTE), "", -1, -1, Imaging.wrapImage(img, null), baseline);
		}
		
		/**
		 * Constructor
		 * @param img the word image
		 * @param str the word string
		 * @param isItalics is the word in italics?
		 * @param pageImageDpi the resolution of the word image
		 */
		//	used for cluster representatives
		public WordImage(BufferedImage img, String str, boolean isItalics, int pageImageDpi) {
			this(null, pageImageDpi, str, isItalics, "", -1, -1, Imaging.wrapImage(img, null), -1);
		}
		
		//	used for rendered words
		WordImage(String str, boolean isItalics, String fontName, int fontStyle, int fontSize, BufferedImage img, int baseline) {
			this(null, -1, str, isItalics, fontName, fontStyle, fontSize, Imaging.wrapImage(img, (str + "-" + fontName + "-" + fontSize + "-" + fontStyle)), baseline);
		}
		
		//	the one that ends up doing the actual work
		private WordImage(ImWord word, int pageImageDpi, String str, boolean isItalics, String fontName, int fontStyle, int fontSize, AnalysisImage ai, int baseline) {
			this.word = word;
			this.pageImageDpi = pageImageDpi;
			
			this.str = str;
			this.isItalics = isItalics;
			this.fontName = fontName;
			this.fontStyle = fontStyle;
			this.fontSize = fontSize;
			
			this.img = ai.getImage();
			this.box = Imaging.getContentBox(ai);
			this.boxProportion = (((this.box.getWidth() * this.box.getHeight()) == 0) ? Double.NaN : Math.log(((double) this.box.getWidth()) / this.box.getHeight()));
			this.baseline = baseline;
			
			int pixelCount = 0;
			this.brightness = ai.getBrightness();
			this.colBrightnessHist = new int[this.brightness.length];
			Arrays.fill(this.colBrightnessHist, 0);
			this.rowBrightnessHist = new int[(this.brightness.length == 0) ? 0 : this.brightness[0].length];
			Arrays.fill(this.rowBrightnessHist, 0);
			for (int c = 0; c < this.brightness.length; c++) {
				for (int r = 0; r < this.brightness[c].length; r++)
					if (this.brightness[c][r] < 80) {
						this.colBrightnessHist[c]++;
						this.rowBrightnessHist[r]++;
						pixelCount++;
					}
			}
			this.pixelCount = pixelCount;
		}
	}
	
	/**
	 * Extract the images for the words in a document page or region.
	 * @param region the page to extract the word images for
	 * @return an array holding the word images
	 */
	public static WordImage[] getWordImages(ImRegion region) {
		return getWordImages(region, false, false);
	}
	
	/**
	 * Extract the images for the words in a document page or region.
	 * @param region the page to extract the word images for
	 * @param expandToGlyphs expand word images to include the full characters?
	 * @return an array holding the word images
	 */
	public static WordImage[] getWordImages(ImRegion region, boolean expandToGlyphs) {
		return getWordImages(region, expandToGlyphs, false);
	}
	
	/**
	 * Extract the images for the words in a document page or region. Expanding
	 * to adjacent regions only words if expanding to glyphs is activated.
	 * @param region the page to extract the word images for
	 * @param expandToGlyphs expand word images to include the full (printed)
	 *            characters from the scans?
	 * @param expandToAdjacent expand word images to include adjacent regions
	 *            (printed characters) of the same line that are not part of
	 *            any words at all?
	 * @return an array holding the word images
	 */
	public static WordImage[] getWordImages(ImRegion region, boolean expandToGlyphs, boolean expandToAdjacent) {
		
		//	get words
		ImWord[] regionWords = region.getWords();
		if (regionWords.length == 0)
			return new WordImage[0];
		ArrayList wordImages = new ArrayList();
		
		//	get and wrap page image only once
		PageImage pageImage = region.getPageImage();
		AnalysisImage pageAi = Imaging.wrapImage(pageImage.image, null);
		
		//	get region coloring of whole page
		int[][] pageRegionCodes = Imaging.getRegionColoring(pageAi, ((byte) 96), false);
		int regionCodeCount = 0;
		for (int c = 0; c < pageRegionCodes.length; c++) {
			for (int r = 0; r < pageRegionCodes[c].length; r++)
				regionCodeCount = Math.max(regionCodeCount, pageRegionCodes[c][r]);
		}
		regionCodeCount++; // account for 0
		int[] pageRegionSizes = new int[regionCodeCount];
		Arrays.fill(pageRegionSizes, 0);
		int[] pageRegionMinX = new int[regionCodeCount];
		Arrays.fill(pageRegionMinX, pageImage.image.getWidth());
		int[] pageRegionMaxX = new int[regionCodeCount];
		Arrays.fill(pageRegionMaxX, 0);
		int[] pageRegionMinY = new int[regionCodeCount];
		Arrays.fill(pageRegionMinY, pageImage.image.getHeight());
		int[] pageRegionMaxY = new int[regionCodeCount];
		Arrays.fill(pageRegionMaxY, 0);
		for (int c = 0; c < pageRegionCodes.length; c++)
			for (int r = 0; r < pageRegionCodes[c].length; r++) {
				if (pageRegionCodes[c][r] == 0)
					continue;
				pageRegionSizes[pageRegionCodes[c][r]]++;
				pageRegionMinX[pageRegionCodes[c][r]] = Math.min(c, pageRegionMinX[pageRegionCodes[c][r]]);
				pageRegionMaxX[pageRegionCodes[c][r]] = Math.max(c, pageRegionMaxX[pageRegionCodes[c][r]]);
				pageRegionMinY[pageRegionCodes[c][r]] = Math.min(r, pageRegionMinY[pageRegionCodes[c][r]]);
				pageRegionMaxY[pageRegionCodes[c][r]] = Math.max(r, pageRegionMaxY[pageRegionCodes[c][r]]);
			}
		System.out.println("Got " + regionCodeCount + " disjoint regions");
		
		//	assess how many words each region color occurs in (to prevent expanding words along characters mingled across words)
		int[] pageRegionWordCounts = new int[regionCodeCount];
		Arrays.fill(pageRegionWordCounts, 0);
		for (int w = 0; w < regionWords.length; w++) {
			
			//	skip over artifacts
			if (ImWord.TEXT_STREAM_TYPE_ARTIFACT.equals(regionWords[w].getTextStreamType()) || ImWord.TEXT_STREAM_TYPE_DELETED.equals(regionWords[w].getTextStreamType()))
				continue;
			
			//	get original scanned word image for comparison
			System.out.println("Assessing word '" + regionWords[w].getString() + "' at " + regionWords[w].bounds);
			int wiMinX = Math.max(0, (regionWords[w].bounds.left - pageImage.leftEdge - 1));
			int wiMinY = Math.max(0, (regionWords[w].bounds.top - pageImage.topEdge - 1));
			int wiMaxX = Math.min(pageImage.image.getWidth(), (regionWords[w].bounds.right - pageImage.leftEdge + 1));
			int wiMaxY = Math.min(pageImage.image.getHeight(), (regionWords[w].bounds.bottom - pageImage.topEdge + 1));
			
			//	count out region colors
			CountingSet wordRegionColors = new CountingSet(new TreeMap());
			for (int c = wiMinX; c < wiMaxX; c++)
				for (int r = wiMinY; r < wiMaxY; r++) {
					if (pageRegionCodes[c][r] != 0)
						wordRegionColors.add(new Integer(pageRegionCodes[c][r]));
				}
			System.out.println(" ==> got " + wordRegionColors.elementCount() + " region colors occupying " + wordRegionColors.size() + " pixels");
			System.out.println("     " + wordRegionColors);
			
			//	count word for region colors
			for (Iterator wrcit = wordRegionColors.iterator(); wrcit.hasNext();) {
				Integer wrc = ((Integer) wrcit.next());
				pageRegionWordCounts[wrc.intValue()]++;
			}
		}
		
		//	keep font site estimate to speed up adjustment
		ImRegion[] regionLines = region.getRegions(ImRegion.LINE_ANNOTATION_TYPE);
		ImRegion regionLine = null;
		
		//	assess how many lines each region color occurs in (to prevent expanding words along characters mingled across lines)
		int[] pageRegionLineCounts = new int[regionCodeCount];
		Arrays.fill(pageRegionLineCounts, 0);
		for (int l = 0; l < regionLines.length; l++) {
			
			//	get original scanned word image for comparison
			System.out.println("Assessing line at " + regionLines[l].bounds);
			int lMinX = Math.max(0, (regionLines[l].bounds.left - pageImage.leftEdge - 1));
			int lMinY = Math.max(0, (regionLines[l].bounds.top - pageImage.topEdge - 1));
			int lMaxX = Math.min(pageImage.image.getWidth(), (regionLines[l].bounds.right - pageImage.leftEdge + 1));
			int lMaxY = Math.min(pageImage.image.getHeight(), (regionLines[l].bounds.bottom - pageImage.topEdge + 1));
			
			//	count out region colors
			CountingSet lineRegionColors = new CountingSet(new TreeMap());
			for (int c = lMinX; c < lMaxX; c++)
				for (int r = lMinY; r < lMaxY; r++) {
					if (pageRegionCodes[c][r] != 0)
						lineRegionColors.add(new Integer(pageRegionCodes[c][r]));
				}
			System.out.println(" ==> got " + lineRegionColors.elementCount() + " region colors occupying " + lineRegionColors.size() + " pixels");
			System.out.println("     " + lineRegionColors);
			
			//	count line for region colors
			for (Iterator lrcit = lineRegionColors.iterator(); lrcit.hasNext();) {
				Integer lrc = ((Integer) lrcit.next());
				pageRegionLineCounts[lrc.intValue()]++;
			}
		}
		
		//	check individual words
		Arrays.sort(regionWords, ImUtils.topDownOrder);
		for (int w = 0; w < regionWords.length; w++) {
			
			//	skip over artifacts
			if (ImWord.TEXT_STREAM_TYPE_ARTIFACT.equals(regionWords[w].getTextStreamType()) || ImWord.TEXT_STREAM_TYPE_DELETED.equals(regionWords[w].getTextStreamType()))
				continue;
			
			//	get and check word string (no use rendering whitespace)
			String wordString = regionWords[w].getString();
			if (wordString == null)
				continue;
			wordString = wordString.trim();
			if (wordString.length() == 0)
				continue;
			
			//	check if we are still in line
			if ((regionLine != null) && !regionLine.bounds.includes(regionWords[w].bounds, true))
				regionLine = null;
			
			//	find current line
			if (regionLine == null) {
				for (int l = 0; l < regionLines.length; l++)
					if (regionLines[l].bounds.includes(regionWords[w].bounds, true)) {
						regionLine = regionLines[l];
						break;
					}
			}
			
			//	get original scanned word image for comparison
			System.out.println("Doing word '" + wordString + "' at " + regionWords[w].bounds);
			int wiMinX = Math.max(0, (regionWords[w].bounds.left - pageImage.leftEdge - 1));
			int wiMinY = Math.max(0, (regionWords[w].bounds.top - pageImage.topEdge - 1));
			int wiMaxX = Math.min(pageImage.image.getWidth(), (regionWords[w].bounds.right - pageImage.leftEdge + 1));
			int wiMaxY = Math.min(pageImage.image.getHeight(), (regionWords[w].bounds.bottom - pageImage.topEdge + 1));
			
			//	use region coloring to assess which regions are mainly inside the word, and which are not
			CountingSet wordRegionColors = new CountingSet(new TreeMap());
			for (int c = wiMinX; c < wiMaxX; c++)
				for (int r = wiMinY; r < wiMaxY; r++) {
					if (pageRegionCodes[c][r] != 0)
						wordRegionColors.add(new Integer(pageRegionCodes[c][r]));
				}
			
			//	sort out regions mainly (over 60%) inside word bounds (for keeping), but only if size is sufficiently large
			HashSet wordMainRegionColors = new HashSet();
			int wordRegionCount = wordRegionColors.elementCount();
			for (Iterator wrcit = wordRegionColors.iterator(); wrcit.hasNext();) {
				Integer wrc = ((Integer) wrcit.next());
				if (((wordRegionColors.getCount(wrc) * 50) > pageImage.currentDpi) && ((wordRegionColors.getCount(wrc) * 5) > (pageRegionSizes[wrc.intValue()] * 3))) {
					wordMainRegionColors.add(wrc);
					wrcit.remove();
				}
			}
			
			//	expand word boundaries to cover all the regions we want to retain
			if (expandToGlyphs) {
//				int oWiMinX = wiMinX;
//				int oWiMaxX = wiMaxX;
//				int oWiMinY = wiMinY;
//				int oWiMaxY = wiMaxY;
				for (Iterator wrcit = wordMainRegionColors.iterator(); wrcit.hasNext();) {
					Integer wrc = ((Integer) wrcit.next());
					if (pageRegionLineCounts[wrc.intValue()] > 1)
						continue; // prevent expanding for characters mingled across lines
					if (pageRegionWordCounts[wrc.intValue()] > 1)
						continue; // prevent expanding for characters mingled across words in same line
					if (pageRegionMinX[wrc.intValue()] < wiMinX)
						wiMinX = pageRegionMinX[wrc.intValue()];
					if (wiMaxX <= pageRegionMaxX[wrc.intValue()])
						wiMaxX = (pageRegionMaxX[wrc.intValue()] + 1);
					if (pageRegionMinY[wrc.intValue()] < wiMinY)
						wiMinY = pageRegionMinY[wrc.intValue()];
					if (wiMaxY <= pageRegionMaxY[wrc.intValue()])
						wiMaxY = (pageRegionMaxY[wrc.intValue()] + 1);
				}
//				if ((((wiMaxX - wiMinX) * 2) >= ((oWiMaxX - oWiMinX) * 3)) || (((wiMaxY - wiMinY) * 2) >= ((oWiMaxY - oWiMinY) * 3))) {
//					BufferedImage pi = new BufferedImage(pageImage.image.getWidth(), pageImage.image.getHeight(), BufferedImage.TYPE_4BYTE_ABGR);
//					Graphics2D pig = pi.createGraphics();
//					pig.drawImage(pageImage.image, 0, 0, null);
//					pig.setColor(Color.GREEN);
//					pig.drawRect(oWiMinX, oWiMinY, (oWiMaxX - oWiMinX), (oWiMaxY - oWiMinY));
//					pig.setColor(Color.RED);
//					pig.drawRect(wiMinX, wiMinY, (wiMaxX - wiMinX), (wiMaxY - wiMinY));
//					ImageDisplayDialog idd = new ImageDisplayDialog("Word Image out of Bounds");
//					idd.addImage(pi, regionWords[w].getString());
//					idd.setSize(800, 1000);
//					idd.setLocationRelativeTo(null);
//					idd.setVisible(true);
//				}
			}
			
			//	render word image TODO use binary black & white index color model
//			BufferedImage wordImage = pageImage.image.getSubimage(wiMinX, wiMinY, (wiMaxX - wiMinX), (wiMaxY - wiMinY));
			BufferedImage wordImage = new BufferedImage((wiMaxX - wiMinX), (wiMaxY - wiMinY), pageImage.image.getType());
			Graphics wordImageGraphics = wordImage.createGraphics();
			wordImageGraphics.drawImage(pageImage.image.getSubimage(wiMinX, wiMinY, (wiMaxX - wiMinX), (wiMaxY - wiMinY)), 0, 0, null);
			
			//	truncate regions whose majority of pixels lies outside the word bounds (only if we have at least one region surviving, though)
			if (wordRegionColors.elementCount() < wordRegionCount) {
				for (int c = wiMinX; c < wiMaxX; c++)
					for (int r = wiMinY; r < wiMaxY; r++) {
						if (pageRegionCodes[c][r] == 0) // make sure 'inter-region' area is really white
							wordImage.setRGB((c - wiMinX), (r - wiMinY), whiteRgb);
						else if (wordRegionColors.contains(new Integer(pageRegionCodes[c][r])))
							wordImage.setRGB((c - wiMinX), (r - wiMinY), whiteRgb);
					}
			}
			
			//	compute baseline if possible (and adjust relative to scan cut-out)
//			int wordBaseline = ((regionLine == null) ? -1 : Integer.parseInt((String) regionLine.getAttribute(ImWord.BASELINE_ATTRIBUTE, "-1")));
			int wordBaseline = -1;
			if ((wordBaseline == -1) && (regionLine != null))
				wordBaseline = Integer.parseInt((String) regionLine.getAttribute(ImWord.BASELINE_ATTRIBUTE, "-1"));
			if (wordBaseline == -1)
				wordBaseline = Integer.parseInt((String) regionWords[w].getAttribute(ImWord.BASELINE_ATTRIBUTE, "-1"));
			if (wordBaseline < regionWords[w].centerY)
				wordBaseline = -1;
			//else wordBaseline -= wiMinY;
			
			//	wrap and store word image
			wordImages.add(new WordImage(wordImage, regionWords[w], wordBaseline, pageImage.currentDpi));
		}
		
		//	finally ...
		return ((WordImage[]) wordImages.toArray(new WordImage[wordImages.size()]));
	}
	private static final int whiteRgb = Color.WHITE.getRGB();
	
	private static void addWordImages(ImRegion line, ImWord[] lineWords, PageRegionColoring regionColors, ArrayList worrImages) {
		
	}
	
	/**
	 * A group of words that share a non-white area in a scanned page image,
	 * including extensive metadata of the conflict.
	 * 
	 * @author sautter
	 */
	public static class WordConflict {
		
		/** the word conflict ID (space-separated concatenation of the local IDs of the conflicting words) */
		public final String wordConflictId;
		/** the conflicting words */
		public final ImWord[] words;
		/** the underlying page image */
		public final PageImage pageImage;
		/** a region coloring of the page image the conflicting words lie in */
		public final int[][] pageRegionColors;
		/** the minimum (left-most) X coordinate of every region in the page, indexed by region color */
		public final int[] pageRegionMinX;
		/** the maximum (right-most) X coordinate of every region in the page, indexed by region color */
		public final int[] pageRegionMaxX;
		/** the minimum (top-most) Y coordinate of every region in the page, indexed by region color */
		public final int[] pageRegionMinY;
		/** the maximum (bottom-most) Y coordinate of every region in the page, indexed by region color */
		public final int[] pageRegionMaxY;
		/** the convex hull of the conflicting words, expanded to fully contain non-contested regions */
		public final BoundingBox wordBounds;
		/** the region colors of the page image areas the words are conflicting over */
		public final int[] conflictRegionColors;
		/** the number of pixels from each contested region contained in the individual words */
		public final int[][] conflictRegionPixelCounts;
		/** the convex hull of the conflicting colored regions */
		public final BoundingBox conflictBounds;
		
		/**
		 * @param words the conflicting words
		 * @param pageDate the word conflict to copy the gape image and region coloring data from
		 * @param wordBounds the convex hull of the conflicting words, expanded to fully contain non-contested regions
		 * @param conflictRegionColors the region colors of the page image areas the words are conflicting over
		 * @param conflictCounds the convex hull of the conflicting colored regions
		 */
		public WordConflict(ImWord[] words, WordConflict pageData, BoundingBox wordBounds, int[] conflictRegionColors, BoundingBox conflictBounds) {
			this(concatenateIDs(words), words, pageData.pageImage, pageData.pageRegionColors, pageData.pageRegionMinX, pageData.pageRegionMaxX, pageData.pageRegionMinY, pageData.pageRegionMaxY, wordBounds, conflictRegionColors, conflictBounds);
		}
		
		WordConflict(String wordConflictId, ImWord[] words, PageImage pageImage, int[][] pageRegionColors, int[] pageRegionMinX, int[] pageRegionMaxX, int[] pageRegionMinY, int[] pageRegionMaxY, BoundingBox wordBounds, int[] conflictRegionColors, BoundingBox conflictBounds) {
			this.wordConflictId = wordConflictId;
			this.words = words;
			this.pageImage = pageImage;
			this.pageRegionColors = pageRegionColors;
			this.pageRegionMinX = pageRegionMinX;
			this.pageRegionMaxX = pageRegionMaxX;
			this.pageRegionMinY = pageRegionMinY;
			this.pageRegionMaxY = pageRegionMaxY;
			this.wordBounds = wordBounds;
			this.conflictRegionColors = conflictRegionColors;
			this.conflictRegionPixelCounts = new int[this.words.length][this.conflictRegionColors.length];
			for (int w = 0; w < this.words.length; w++) /* better to re-count here in case words get adjusted via public constructor */ {
				CountingSet wordRegionColors = new CountingSet();
				for (int c = this.words[w].bounds.left; c < this.words[w].bounds.right; c++)
					for (int r = this.words[w].bounds.top; r < this.words[w].bounds.bottom; r++) {
						if (this.pageRegionColors[c][r] != 0)
							wordRegionColors.add(new Integer(this.pageRegionColors[c][r]));
					}
				for (int c = 0; c < this.conflictRegionColors.length; c++)
					this.conflictRegionPixelCounts[w][c] = wordRegionColors.getCount(new Integer(this.conflictRegionColors[c]));
			}
			this.conflictBounds = conflictBounds;
		}
	}
	
	private static String concatenateIDs(ImWord[] words) {
		StringBuffer wordIDs = new StringBuffer();
		for (int w = 0; w < words.length; w++) {
			if (w != 0)
				wordIDs.append(" ");
			wordIDs.append(words[w].getLocalID());
		}
		return wordIDs.toString();
	}
	
	/**
	 * Extract groups of words from a document page or region that share a
	 * non-white area in the underlying page image. Such conflicts can indicate
	 * highly likely OCR errors, as well as problems in the scan proper.
	 * @param region the page to extract the word conflicts for
	 * @return an array holding the word conflicts
	 */
	public static WordConflict[] getWordConflicts(ImRegion region) {
		ImWord[] words = region.getWords();
		ImRegion[] lines = region.getRegions(ImRegion.LINE_ANNOTATION_TYPE);
		return getWordConflicts(words, lines, null, null);
	}
	
	/**
	 * Extract groups of words from a document page or region that share a
	 * non-white area in the underlying page image. Such conflicts can indicate
	 * highly likely OCR errors, as well as problems in the scan proper. If the
	 * argument region coloring of the underlying page is null, this method
	 * creates one. However, when searching for conflicts in multiple regions of
	 * a single page, client code is well advised to create and reuse a region
	 * coloring to reduce computational effort.
	 * @param region the page to extract the word conflicts for
	 * @param regionColoring a region coloring of the underlying page
	 * @return an array holding the word conflicts
	 */
	public static WordConflict[] getWordConflicts(ImRegion region, PageRegionColoring regionColoring) {
		ImWord[] words = region.getWords();
		ImRegion[] lines = region.getRegions(ImRegion.LINE_ANNOTATION_TYPE);
		return getWordConflicts(words, lines, null, regionColoring);
	}
	
	/**
	 * Extract groups of words from a document page or region that share a
	 * non-white area in the underlying page image. Such conflicts can indicate
	 * highly likely OCR errors, as well as problems in the scan proper. If the
	 * words in the argument array are detached, the argument page must not be
	 * null.
	 * @param words the words to detect conflicts in
	 * @param page the page the words belong to
	 * @return an array holding the word conflicts
	 */
	public static WordConflict[] getWordConflicts(ImWord[] words, ImPage page) {
		return getWordConflicts(words, null, page, null);
	}
	
	/**
	 * Extract groups of words from a document page or region that share a
	 * non-white area in the underlying page image. Such conflicts can indicate
	 * highly likely OCR errors, as well as problems in the scan proper. If the
	 * words in the argument array are detached, the argument page must not be
	 * null.
	 * @param words the words to detect conflicts in
	 * @param page the page the words belong to
	 * @param regionColoring a region coloring of the page
	 * @return an array holding the word conflicts
	 */
	public static WordConflict[] getWordConflicts(ImWord[] words, ImPage page, PageRegionColoring regionColoring) {
		return getWordConflicts(words, null, page, regionColoring);
	}
	
	private static WordConflict[] getWordConflicts(ImWord[] words, ImRegion[] lines, ImPage page, PageRegionColoring regionColoring) {
		if (words == null)
			return new WordConflict[0];
		if (words.length == 0)
			return new WordConflict[0];
		if (page == null)
			page = words[0].getPage();
		if (page == null)
			page = words[0].getDocument().getPage(words[0].pageId);
		if (page == null)
			return new WordConflict[0];
		if (regionColoring == null)
			regionColoring = PageImageAnalysis.getRegionColoring(page, ((byte) 96));
		if (lines == null) {
			BoundingBox wordArea = ImLayoutObject.getAggregateBox(words);
			lines = page.getRegionsInside(ImRegion.LINE_ANNOTATION_TYPE, wordArea, true);
		}
		return getWordConflicts(words, lines, page, page.getImage(), regionColoring.pageRegionColors, regionColoring.pageRegionSizes, regionColoring.pageRegionMinX, regionColoring.pageRegionMaxX, regionColoring.pageRegionMinY, regionColoring.pageRegionMaxY);
	}
	private static WordConflict[] getWordConflicts(ImWord[] words, ImRegion[] lines, ImPage page, PageImage pageImage, int[][] pageRegionColors, int[] pageRegionSizes, int[] pageRegionMinX, int[] pageRegionMaxX, int[] pageRegionMinY, int[] pageRegionMaxY) {
		
		//	sort words
		ImUtils.sortLeftRightTopDown(words);
		
		//	freeze number of region colors
		int regionColorCount = pageRegionSizes.length;
		
		//	assess how many words each region color occurs in (to prevent expanding words along characters mingled across words)
		Object[] pageRegionWords = new Object[regionColorCount];
		for (int w = 0; w < words.length; w++) {
			
			//	skip over artifacts
			if (ImWord.TEXT_STREAM_TYPE_ARTIFACT.equals(words[w].getTextStreamType()) || ImWord.TEXT_STREAM_TYPE_DELETED.equals(words[w].getTextStreamType()))
				continue;
			
			//	get original scanned word image for comparison
//			System.out.println("Assessing word '" + regionWords[w].getString() + "' at " + regionWords[w].bounds);
//			int wiMinX = Math.max(0, (regionWords[w].bounds.left - pageImage.leftEdge - 1));
//			int wiMinY = Math.max(0, (regionWords[w].bounds.top - pageImage.topEdge - 1));
//			int wiMaxX = Math.min(pageImage.image.getWidth(), (regionWords[w].bounds.right - pageImage.leftEdge + 1));
//			int wiMaxY = Math.min(pageImage.image.getHeight(), (regionWords[w].bounds.bottom - pageImage.topEdge + 1));
			int wiMinX = Math.max(0, (words[w].bounds.left - pageImage.leftEdge));
			int wiMinY = Math.max(0, (words[w].bounds.top - pageImage.topEdge));
			int wiMaxX = Math.min(pageImage.image.getWidth(), (words[w].bounds.right - pageImage.leftEdge));
			int wiMaxY = Math.min(pageImage.image.getHeight(), (words[w].bounds.bottom - pageImage.topEdge));
			
			//	count out region colors
			CountingSet wordRegionColors = new CountingSet(new TreeMap());
			for (int c = wiMinX; c < wiMaxX; c++)
				for (int r = wiMinY; r < wiMaxY; r++) {
					if (pageRegionColors[c][r] != 0)
						wordRegionColors.add(new Integer(pageRegionColors[c][r]));
				}
//			System.out.println(" ==> got " + wordRegionColors.elementCount() + " region colors occupying " + wordRegionColors.size() + " pixels");
//			System.out.println("     " + wordRegionColors);
			
			//	count word for region colors
			for (Iterator wrcit = wordRegionColors.iterator(); wrcit.hasNext();) {
				Integer wrc = ((Integer) wrcit.next());
				if (wrc.intValue() == 38)
					System.out.println();
				if (pageRegionWords[wrc.intValue()] == null)
					pageRegionWords[wrc.intValue()] = words[w];
				else if (pageRegionWords[wrc.intValue()] instanceof LinkedHashSet)
					((LinkedHashSet) pageRegionWords[wrc.intValue()]).add(words[w]);
				else {
					LinkedHashSet prws = new LinkedHashSet(4); // actual size will mostly be 2, but this way we save but any resizing
					prws.add(pageRegionWords[wrc.intValue()]);
					prws.add(words[w]);
					pageRegionWords[wrc.intValue()] = prws;
				}
			}
		}
		
		//	assess how many lines each region color occurs in (to prevent expanding words along characters mingled across lines)
		int[] pageRegionLineCounts = new int[regionColorCount];
		Arrays.fill(pageRegionLineCounts, 0);
		for (int l = 0; l < lines.length; l++) {
			
			//	get original scanned word image for comparison
//			System.out.println("Assessing line at " + regionLines[l].bounds);
			int lMinX = Math.max(0, (lines[l].bounds.left - pageImage.leftEdge - 1));
			int lMinY = Math.max(0, (lines[l].bounds.top - pageImage.topEdge - 1));
			int lMaxX = Math.min(pageImage.image.getWidth(), (lines[l].bounds.right - pageImage.leftEdge + 1));
			int lMaxY = Math.min(pageImage.image.getHeight(), (lines[l].bounds.bottom - pageImage.topEdge + 1));
			
			//	count out region colors
			CountingSet lineRegionColors = new CountingSet(new TreeMap());
			for (int c = lMinX; c < lMaxX; c++)
				for (int r = lMinY; r < lMaxY; r++) {
					if (pageRegionColors[c][r] != 0)
						lineRegionColors.add(new Integer(pageRegionColors[c][r]));
				}
//			System.out.println(" ==> got " + lineRegionColors.elementCount() + " region colors occupying " + lineRegionColors.size() + " pixels");
//			System.out.println("     " + lineRegionColors);
			
			//	count line for region colors
			for (Iterator lrcit = lineRegionColors.iterator(); lrcit.hasNext();) {
				Integer lrc = ((Integer) lrcit.next());
				pageRegionLineCounts[lrc.intValue()]++;
			}
		}
		
		//	check individual colored regions
		LinkedHashMap conflictWordIDsToRegionColors = new LinkedHashMap();
		for (int r = 0; r < pageRegionWords.length; r++) {
			
			//	any conflicting words to deal with here?
			if (!(pageRegionWords[r] instanceof LinkedHashSet))
				continue;
			
			//	leave line conflicts alone for now
			if (pageRegionLineCounts[r] > 1)
				continue;
			
			//	get conflicting words and set up conflict group ID (might concern more than one colored region, e.g. with an 'equals' sign)
			LinkedHashSet prws = ((LinkedHashSet) pageRegionWords[r]);
			ImWord[] cWords = ((ImWord[]) prws.toArray(new ImWord[prws.size()]));
			String cWordIDs = concatenateIDs(cWords);
			
			//	index region as conflicting
			Object cWordColors = conflictWordIDsToRegionColors.get(cWordIDs);
			if (cWordColors == null)
				conflictWordIDsToRegionColors.put(cWordIDs, new Integer(r));
			else if (cWordColors instanceof LinkedHashSet)
				((LinkedHashSet) cWordColors).add(new Integer(r));
			else {
				LinkedHashSet cwcs = new LinkedHashSet(4); // actual size will mostly be 2, but this way we save but any resizing
				cwcs.add(cWordColors);
				cwcs.add(new Integer(r));
				conflictWordIDsToRegionColors.put(cWordIDs, cwcs);
			}
		}
		
		//	extract conflicting word groups
		ArrayList wordConflicts = new ArrayList();
		for (Iterator cwidit = conflictWordIDsToRegionColors.keySet().iterator(); cwidit.hasNext();) {
			String cwId = ((String) cwidit.next());
			
			//	tray up colors of regions words conflict over (_can_ be more than one)
			Object cwprcs = conflictWordIDsToRegionColors.get(cwId);
			int[] cWordRegionColors;
			if (cwprcs instanceof Integer) {
				cWordRegionColors = new int[1];
				cWordRegionColors[0] = ((Integer) cwprcs).intValue();
			}
			else {
				LinkedHashSet cwprcSet = ((LinkedHashSet) cwprcs);
				cWordRegionColors = new int[cwprcSet.size()];
				int cwrcIndex = 0;
				for (Iterator prcit = cwprcSet.iterator(); prcit.hasNext();)
					cWordRegionColors[cwrcIndex++] = ((Integer) prcit.next());
			}
			
			//	get conflicting words proper, and compute convex boundary
			LinkedHashSet prws = ((LinkedHashSet) pageRegionWords[cWordRegionColors[0]]);
			ImWord[] cWords = ((ImWord[]) prws.toArray(new ImWord[prws.size()]));
			BoundingBox cWordArea = ImLayoutObject.getAggregateBox(cWords);
			
			//	get word string (for logging)
			StringBuffer cWordString = new StringBuffer();
			for (int w = 0; w < cWords.length; w++)
				cWordString.append(cWords[w].getString());
			
			//	get original scanned word image for comparison
			System.out.println("Doing conflicting words '" + cWordString.toString() + "' at " + cwId);
			int cwMinX = Math.max(0, (cWordArea.left - pageImage.leftEdge - 1));
			int cwMinY = Math.max(0, (cWordArea.top - pageImage.topEdge - 1));
			int cwMaxX = Math.min(pageImage.image.getWidth(), (cWordArea.right - pageImage.leftEdge + 1));
			int cwMaxY = Math.min(pageImage.image.getHeight(), (cWordArea.bottom - pageImage.topEdge + 1));
			
			//	use region coloring to assess which regions are mainly inside the conflicting words, and which are not
			CountingSet cWordAreaRegionColors = new CountingSet(new TreeMap());
			for (int c = cwMinX; c < cwMaxX; c++)
				for (int r = cwMinY; r < cwMaxY; r++) {
					if (pageRegionColors[c][r] != 0)
						cWordAreaRegionColors.add(new Integer(pageRegionColors[c][r]));
				}
			
			//	sort out regions mainly (over 60%) inside word bounds (for keeping), but only if size is sufficiently large
			HashSet cWordAreaMainRegionColors = new HashSet();
			for (Iterator cwrcit = cWordAreaRegionColors.iterator(); cwrcit.hasNext();) {
				Integer wrc = ((Integer) cwrcit.next());
				if (((cWordAreaRegionColors.getCount(wrc) * 50) > pageImage.currentDpi) && ((cWordAreaRegionColors.getCount(wrc) * 5) > (pageRegionSizes[wrc.intValue()] * 3))) {
					cWordAreaMainRegionColors.add(wrc);
					cwrcit.remove();
				}
			}
			
			//	expand word boundaries to cover all the regions we want to retain
//			int oCwMinX = cwMinX;
//			int oCwMaxX = cwMaxX;
//			int oCwMinY = cwMinY;
//			int oCwMaxY = cwMaxY;
			for (Iterator wrcit = cWordAreaMainRegionColors.iterator(); wrcit.hasNext();) {
				Integer wrc = ((Integer) wrcit.next());
				if (pageRegionLineCounts[wrc.intValue()] > 1)
					continue; // prevent expanding for characters mingled across lines
				if (pageRegionWords[wrc.intValue()] == null) { /* whatever */ }
				else if (pageRegionWords[wrc.intValue()] instanceof ImWord) { /* no conflicts here */ }
				else if (prws.equals(pageRegionWords[wrc.intValue()])) { /* same conflict set */ }
				else continue; // prevent expanding for characters mingled with words outside current conflict set
				if (pageRegionMinX[wrc.intValue()] < cwMinX)
					cwMinX = pageRegionMinX[wrc.intValue()];
				if (cwMaxX <= pageRegionMaxX[wrc.intValue()])
					cwMaxX = (pageRegionMaxX[wrc.intValue()] + 1);
				if (pageRegionMinY[wrc.intValue()] < cwMinY)
					cwMinY = pageRegionMinY[wrc.intValue()];
				if (cwMaxY <= pageRegionMaxY[wrc.intValue()])
					cwMaxY = (pageRegionMaxY[wrc.intValue()] + 1);
			}
//			if ((((wiMaxX - wiMinX) * 2) >= ((oWiMaxX - oWiMinX) * 3)) || (((wiMaxY - wiMinY) * 2) >= ((oWiMaxY - oWiMinY) * 3))) {
//				BufferedImage pi = new BufferedImage(pageImage.image.getWidth(), pageImage.image.getHeight(), BufferedImage.TYPE_4BYTE_ABGR);
//				Graphics2D pig = pi.createGraphics();
//				pig.drawImage(pageImage.image, 0, 0, null);
//				pig.setColor(Color.GREEN);
//				pig.drawRect(oWiMinX, oWiMinY, (oWiMaxX - oWiMinX), (oWiMaxY - oWiMinY));
//				pig.setColor(Color.RED);
//				pig.drawRect(wiMinX, wiMinY, (wiMaxX - wiMinX), (wiMaxY - wiMinY));
//				ImageDisplayDialog idd = new ImageDisplayDialog("Word Image out of Bounds");
//				idd.addImage(pi, regionWords[w].getString());
//				idd.setSize(800, 1000);
//				idd.setLocationRelativeTo(null);
//				idd.setVisible(true);
//			}
			
			//	enlarge conflict area bounds
			cWordArea = new BoundingBox(cwMinX, cwMaxX, cwMinY, cwMaxY);
			
			//	compute bounds of contested regions
			int crMinX = pageRegionMinX[cWordRegionColors[0]];
			int crMaxX = pageRegionMaxX[cWordRegionColors[0]];
			int crMinY = pageRegionMinY[cWordRegionColors[0]];
			int crMaxY = pageRegionMaxY[cWordRegionColors[0]];
			for (int c = 1; c < cWordRegionColors.length; c++) {
				crMinX = Math.min(crMinX, pageRegionMinX[cWordRegionColors[c]]);
				crMaxX = Math.max(crMaxX, pageRegionMaxX[cWordRegionColors[c]]);
				crMinY = Math.min(crMinY, pageRegionMinY[cWordRegionColors[c]]);
				crMaxY = Math.max(crMaxY, pageRegionMaxY[cWordRegionColors[c]]);
			}
			
			//	wrap and store word conflict
			wordConflicts.add(new WordConflict(cwId, cWords, pageImage, pageRegionColors, pageRegionMinX, pageRegionMaxX, pageRegionMinY, pageRegionMaxY, cWordArea, cWordRegionColors, new BoundingBox(crMinX, (crMaxX+1), crMinY, (crMaxY+1))));
		}
		
		//	finally ...
		return ((WordConflict[]) wordConflicts.toArray(new WordConflict[wordConflicts.size()]));
	}
	
	/**
	 * Mark an OCR conflict on a group of words from a document page. Such
	 * conflicts can indicate highly likely OCR errors, as well as problems in
	 * the scan proper, or other problems identified by client code.
	 * @param words the group of words to mark the conflict on
	 * @return the word conflicts
	 */
	public static WordConflict getWordConflict(ImWord[] words) {
		return getWordConflict(words, null, null, null, null, null, null);
	}
	
	/**
	 * Mark an OCR conflict on a group of words from a document page. Such
	 * conflicts can indicate highly likely OCR errors, as well as problems in
	 * the scan proper, or other problems identified by client code. If the
	 * argument region coloring of the underlying page is null, this method
	 * creates one. However, when marking multiple conflicts in a single page,
	 * client code is well advised to create and reuse a region coloring to
	 * reduce computational effort.
	 * @param words the group of words to mark the conflict on
	 * @param regionColoring a region coloring of the underlying page
	 * @return the word conflicts
	 */
	public static WordConflict getWordConflict(ImWord[] words, PageRegionColoring regionColoring) {
		return getWordConflict(words,
				((regionColoring == null) ? null : regionColoring.pageImage),
				((regionColoring == null) ? null : regionColoring.pageRegionColors),
				((regionColoring == null) ? null : regionColoring.pageRegionMinX),
				((regionColoring == null) ? null : regionColoring.pageRegionMaxX),
				((regionColoring == null) ? null : regionColoring.pageRegionMinY),
				((regionColoring == null) ? null : regionColoring.pageRegionMaxY)
			);
	}
	
	/**
	 * Mark an OCR conflict on a group of words from a document page. Such
	 * conflicts can indicate highly likely OCR errors, as well as problems in
	 * the scan proper, or other problems identified by client code. If the
	 * argument word conflict is not null, its region coloring data will be
	 * reused. This enables client code to save computational effort when
	 * marking multiple conflicts in a single page.
	 * @param words the group of words to mark the conflict on
	 * @param pageData a word conflict from the same page to reuse data from
	 * @return the word conflicts
	 */
	public static WordConflict getWordConflict(ImWord[] words, WordConflict pageData) {
		return getWordConflict(words,
				((pageData == null) ? null : pageData.pageImage),
				((pageData == null) ? null : pageData.pageRegionColors),
				((pageData == null) ? null : pageData.pageRegionMinX),
				((pageData == null) ? null : pageData.pageRegionMaxX),
				((pageData == null) ? null : pageData.pageRegionMinY),
				((pageData == null) ? null : pageData.pageRegionMaxY)
			);
	}
	
	private static WordConflict getWordConflict(ImWord[] words, PageImage pageImage, int[][] pageRegionColors, int[] pageRegionMinX, int[] pageRegionMaxX, int[] pageRegionMinY, int[] pageRegionMaxY) {
		
		//	check arguments
		if (words == null)
			return null;
		if (words.length == 0)
			return null;
		
		//	make sure we have a page image and region coloring data
		if (pageImage == null) {
			ImDocument doc = words[0].getDocument();
			ImPage page = doc.getPage(words[0].pageId);
			pageImage = page.getImage();
			PageRegionColoring regionColoring = PageImageAnalysis.getRegionColoring(page, ((byte) 96));
			pageRegionColors = regionColoring.pageRegionColors;
			pageRegionMinX = regionColoring.pageRegionMinX;
			pageRegionMaxX = regionColoring.pageRegionMaxX;
			pageRegionMinY = regionColoring.pageRegionMinY;
			pageRegionMaxY = regionColoring.pageRegionMaxY;
		}
		
		//	TODO compute conflict boundaries and all region colors
		BoundingBox wordArea = ImLayoutObject.getAggregateBox(words);
		HashSet areaRegionColors = new HashSet();
		for (int c = wordArea.left; c < wordArea.right; c++)
			for (int r = wordArea.top; r < wordArea.bottom; r++) {
				if (pageRegionColors[c][r] != 0)
					areaRegionColors.add(new Integer(pageRegionColors[c][r]));
			}
		
		//	collect region colors of each word
		HashSet[] wordRegionColors = new HashSet[words.length];
		for (int w = 0; w < words.length; w++) {
			wordRegionColors[w] = new HashSet();
			for (int c = words[w].bounds.left; c < words[w].bounds.right; c++)
				for (int r = wordArea.top; r < wordArea.bottom; r++) {
					if (pageRegionColors[c][r] != 0)
						wordRegionColors[w].add(new Integer(pageRegionColors[c][r]));
				}
		}
		
		//	collect regions colors not belonging to exactly one word
		HashSet conflictRegionColorSet = new HashSet();
		for (Iterator rcit = areaRegionColors.iterator(); rcit.hasNext();) {
			Integer rc = ((Integer) rcit.next());
			int rcWordCount = 0;
			for (int w = 0; w < words.length; w++) {
				if (wordRegionColors[w].contains(rc))
					rcWordCount++;
			}
			if (rcWordCount != 1)
				conflictRegionColorSet.add(rc);
		}
		int[] conflictRegionColors = new int[conflictRegionColorSet.size()];
		int crcIndex = 0;
		for (Iterator prcit = conflictRegionColorSet.iterator(); prcit.hasNext();)
			conflictRegionColors[crcIndex++] = ((Integer) prcit.next());
		Arrays.sort(conflictRegionColors);
		
		//	finally ...
		return new WordConflict(concatenateIDs(words), words, pageImage, pageRegionColors, pageRegionMinX, pageRegionMaxX, pageRegionMinY, pageRegionMaxY, wordArea, conflictRegionColors, wordArea);
	}
	
	/**
	 * Render the string value of a word to fit inside the word's bounds.
	 * @param word the word whose string to render
	 * @param wordBaseline the word baseline
	 * @param fontStyle the font style to use for rendering
	 * @param estimatedFontSize the font size to use (will be adjusted if
	 *            rendered string doesn't fit the word's bounding box)
	 * @param pageImageDpi the resolution of the page image the word lies upon
	 * @param cache a map for caching rendered word images
	 * @return the rendered word image
	 */
	public static WordImage renderWordImage(ImWord word, int wordBaseline, int fontStyle, int estimatedFontSize, int pageImageDpi, Map cache) {
		return renderWordImage(word.getString(), word.bounds, wordBaseline, fontStyle, estimatedFontSize, pageImageDpi, cache);
	}
	
	/**
	 * Render the string value of a word to fit inside the word's bounds.
	 * @param word the word whose string to render
	 * @param wordBaseline the word baseline
	 * @param fontName the name of the font to use for rendering
	 * @param fontStyle the font style to use for rendering
	 * @param estimatedFontSize the font size to use (will be adjusted if
	 *            rendered string doesn't fit the word's bounding box)
	 * @param pageImageDpi the resolution of the page image the word lies upon
	 * @param cache a map for caching rendered word images
	 * @return the rendered word image
	 */
	public static WordImage renderWordImage(ImWord word, int wordBaseline, String fontName, int fontStyle, int estimatedFontSize, int pageImageDpi, Map cache) {
		return renderWordImage(word.getString(), word.bounds, wordBaseline, fontName, fontStyle, estimatedFontSize, pageImageDpi, cache);
	}
	
	/**
	 * Render the string value of a word to fit inside the word's bounds.
	 * @param wordString the word string to render
	 * @param wordBounds the bounding box of the word whose string to render
	 * @param wordBaseline the word baseline
	 * @param fontStyle the font style to use for rendering
	 * @param estimatedFontSize the font size to use (will be adjusted if
	 *            rendered string doesn't fit the word's bounding box)
	 * @param pageImageDpi the resolution of the page image the word lies upon
	 * @param cache a map for caching rendered word images
	 * @return the rendered word image
	 */
	public static WordImage renderWordImage(String wordString, BoundingBox wordBounds, int wordBaseline, int fontStyle, int estimatedFontSize, int pageImageDpi, Map cache) {
//		return renderWordImage(wordString, wordBounds, wordBaseline, "Serif", fontStyle, estimatedFontSize, pageImageDpi, cache);
		return renderWordImage(wordString, wordBounds, wordBaseline, "FreeSerif", fontStyle, estimatedFontSize, pageImageDpi, cache);
	}
	
	/**
	 * Render the string value of a word to fit inside the word's bounds.
	 * @param wordString the word string to render
	 * @param wordBounds the bounding box of the word whose string to render
	 * @param wordBaseline the word baseline
	 * @param fontName the name of the font to use for rendering
	 * @param fontStyle the font style to use for rendering
	 * @param estimatedFontSize the font size to use (will be adjusted if
	 *            rendered string doesn't fit the word's bounding box)
	 * @param pageImageDpi the resolution of the page image the word lies upon
	 * @param cache a map for caching rendered word images
	 * @return the rendered word image
	 */
	public static WordImage renderWordImage(String wordString, BoundingBox wordBounds, int wordBaseline, String fontName, int fontStyle, int estimatedFontSize, int pageImageDpi, Map cache) {
		
		//	test if word string has descent
		boolean wordHasDescent;
		
		//	TODO remove italics flag (seems to cause problems in FreeSerif)
		boolean isItalics = false; //((fontStyle & Font.ITALIC) != 0);
//		fontName = "Serif";
		
		//	adjust font size
		int fontSize;
		Font renderingFont;
		if (isFlatString(wordString)) {
			fontSize = estimatedFontSize;
//			renderingFont = new Font(fontName, fontStyle, Math.round(((float) (fontSize * pageImageDpi)) / 72));
			renderingFont = new Font(fontName, (fontStyle - (isItalics ? Font.ITALIC : 0)), Math.round(((float) (fontSize * pageImageDpi)) / 72));
			wordHasDescent = false;
		}
		else {
			fontSize = estimatedFontSize;
//			renderingFont = new Font(fontName, fontStyle, Math.round(((float) (fontSize * pageImageDpi)) / 72));
			renderingFont = new Font(fontName, (fontStyle - (isItalics ? Font.ITALIC : 0)), Math.round(((float) (fontSize * pageImageDpi)) / 72));
			TextLayout wtl = new TextLayout(wordString, renderingFont, fontRenderContext);
			wordHasDescent = ((Math.abs(wtl.getBounds().getY()) * 10) < (Math.abs(wtl.getBounds().getHeight()) * 9));
			while ((wtl.getBounds().getHeight() < (((wordHasDescent || (wordBaseline < 1)) ? wordBounds.bottom : (wordBaseline + 1)) - wordBounds.top)) || ((0 < wordBaseline) && (Math.abs(wtl.getBounds().getY()) < ((wordBaseline + 1) - wordBounds.top)))) {
				fontSize++;
//				renderingFont = new Font(fontName, fontStyle, Math.round(((float) (fontSize * pageImageDpi)) / 72));
				renderingFont = new Font(fontName, (fontStyle - (isItalics ? Font.ITALIC : 0)), Math.round(((float) (fontSize * pageImageDpi)) / 72));
				wtl = new TextLayout(wordString, renderingFont, fontRenderContext);
			}
			while (((((wordHasDescent || (wordBaseline < 1)) ? wordBounds.bottom : (wordBaseline + 1)) - wordBounds.top) < wtl.getBounds().getHeight()) || ((0 < wordBaseline) && (((wordBaseline + 1) - wordBounds.top) < Math.abs(wtl.getBounds().getY()))))  {
				fontSize--;
//				renderingFont = new Font(fontName, fontStyle, Math.round(((float) (fontSize * pageImageDpi)) / 72));
				renderingFont = new Font(fontName, (fontStyle - (isItalics ? Font.ITALIC : 0)), Math.round(((float) (fontSize * pageImageDpi)) / 72));
				wtl = new TextLayout(wordString, renderingFont, fontRenderContext);
			}
			estimatedFontSize = fontSize;
		}
		
		//	check cache
		String cacheKey = null;
		if (cache != null) {
			cacheKey = (wordString + "[" + fontStyle + "-" + fontSize + "]");
			WordImage wi = ((WordImage) cache.get(cacheKey));
			if (wi != null)
				return wi;
		}
		
		//	render word string TODO use binary black & white index color model
		BufferedImage renderedWordImage = new BufferedImage((wordBounds.right - wordBounds.left + (renderingSafetyMarginX * 2)), (wordBounds.bottom - wordBounds.top + (renderingSafetyMarginY * 2)), BufferedImage.TYPE_BYTE_GRAY);
		Graphics2D graphics = renderedWordImage.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setColor(Color.WHITE);
		graphics.fillRect(0, 0, renderedWordImage.getWidth(), renderedWordImage.getHeight());
		
		//	adjust word size and vertical position
		graphics.setFont(renderingFont);
		LineMetrics wlm = renderingFont.getLineMetrics(wordString, graphics.getFontRenderContext());
		TextLayout wtl = new TextLayout(wordString, renderingFont, graphics.getFontRenderContext());
		double hScale = (((double) (wordBounds.right - wordBounds.left)) / wtl.getBounds().getWidth());
		
		//	draw word
		graphics.setColor(Color.BLACK);
		graphics.translate(renderingSafetyMarginX, renderingSafetyMarginY);
		graphics.translate(-wtl.getBounds().getMinX(), 0);
		if (hScale != 1)
			graphics.scale(hScale, 1);
		if (isItalics) /* simulates italics by shearing output to italic angle, to avoid FreeSerif-Italic problems */ {
			float italicAngle = renderingFont.deriveFont(Font.ITALIC).getItalicAngle();
			System.out.println("Italics angle is " + italicAngle);
			graphics.shear(-italicAngle, 0);
			graphics.translate((italicAngle * wordBounds.getHeight()), 0);
		}
		graphics.drawString(wordString, 0, ((wordBaseline < 1) ? ((wordBounds.bottom - wordBounds.top) - (wordHasDescent ? Math.round(wlm.getDescent()) : 0)) : (wordBaseline - wordBounds.top + 1)));
		graphics.dispose();
		
		//	wrap up rendered image
		WordImage wi = new WordImage(wordString, ((fontStyle & Font.ITALIC) != 0), renderingFont.getName(), fontStyle, fontSize, renderedWordImage, wordBaseline);
		
		//	cache word image if possible
		if (cache != null)
			cache.put(cacheKey, wi);
		
		//	finally ...
		return wi;
	}
	
	private static final int renderingSafetyMarginX = 16;
	private static final int renderingSafetyMarginY = 8;
	
	private static boolean isFlatString(String str) {
		for (int c = 0; c < str.length(); c++) {
			if (".,:;°_-~*+'´`\"\u2012\u2013\u2014\u2015\u2212".indexOf(str.charAt(c)) == -1)
				return false;
		}
		return true;
	}
	
	private static final FontRenderContext fontRenderContext;
	static { //  TODO use binary black & white index color model
		BufferedImage renderedWordImage = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY);
		Graphics2D graphics = renderedWordImage.createGraphics();
		fontRenderContext = graphics.getFontRenderContext();
	}
	
	/**
	 * The result of matching two word images, with various metrics. The
	 * natural sort order of objects of this class is by descending similarity.
	 * 
	 * @author sautter
	 */
	public static class WordImageMatch implements Comparable {
		
		/** the scanned word image */
		public final WordImage scanned;
		/** the rendered word image */
		public final WordImage rendered;
		
		/** the shift of the left edge (negative: scan shifted inward, positive: rendering shifted inward) */
		public final int leftShift;
		/** the shift of the right edge (negative: scan shifted inward, positive: rendering shifted inward) */
		public final int rightShift;
		/** the shift of the top edge (negative: scan shifted inward, positive: rendering shifted inward) */
		public final int topShift;
		/** the shift of the bottom edge (negative: scan shifted inward, positive: rendering shifted inward) */
		public final int bottomShift;
		
		/** the detailed match data */
		public final byte[][] matchData;
		
		/** the number of matched pixels */
		public final int matched;
		/** the number of pixels only present in the scan image */
		public final int scannedOnly;
		/** the number of pixels only present in the rendered image */
		public final int renderedOnly;
		
		/** matching precision */
		public final float precision;
		/** matching recall */
		public final float recall;
		/** image similarity (f-score computed from precision and recall) */
		public final float sim;
		
		/** matching precision, adjusted for weight (number of non-white pixels) of matched images */
		public final float waPrecision;
		/** matching recall, adjusted for weight (number of non-white pixels) of matched images */
		public final float waRecall;
		/** matching similarity, adjusted for weight (number of non-white pixels) of matched images */
		public final float waSim;
		
		WordImageMatch(WordImage scanned, WordImage rendered, int leftShift, int rightShift, int topShift, int bottomShift, byte[][] matchData, int matched, int scannedOnly, int renderedOnly) {
			this.scanned = scanned;
			this.rendered = rendered;
			this.leftShift = leftShift;
			this.rightShift = rightShift;
			this.topShift = topShift;
			this.bottomShift = bottomShift;
			this.matchData = matchData;
			this.matched = matched;
			this.scannedOnly = scannedOnly;
			this.renderedOnly = renderedOnly;
			if (this.matched == 0) {
				this.sim = 0;
				this.precision = 0;
				this.recall = 0;
				this.waSim = 0;
				this.waPrecision = 0;
				this.waRecall = 0;
			}
			else {
				this.precision = (((float) this.matched) / (this.scannedOnly + this.matched));
				this.recall = (((float) this.matched) / (this.renderedOnly + this.matched));
				this.sim = ((this.precision * this.recall * 2) / (this.precision + this.recall));
				int scannedWeight = (this.scannedOnly + this.matched);
				int renderedWeight = (this.renderedOnly + this.matched);
				int waScannedOnly = ((this.scannedOnly * renderedWeight) / scannedWeight);
				int waRenderedOnly = ((this.renderedOnly * scannedWeight) / renderedWeight);
				this.waPrecision = (((float) this.matched) / (waScannedOnly + this.matched));
				this.waRecall = (((float) this.matched) / (waRenderedOnly + this.matched));
				this.waSim = ((this.waPrecision * this.waRecall * 2) / (this.waPrecision + this.waRecall));
			}
		}
		
		/* (non-Javadoc)
		 * @see java.lang.Comparable#compareTo(java.lang.Object)
		 */
		public int compareTo(Object obj) {
			return Float.compare(((WordImageMatch) obj).sim, this.sim);
		}
	}
	
	/** word image match value indicating that the respective pixel is set in neither image */
	public static final byte WORD_IMAGE_MATCH_NONE = 0;
	/** word image match value indicating that the respective pixel is set in both images */
	public static final byte WORD_IMAGE_MATCH_MATCHED = 1;
	/** word image match value indicating that the respective pixel is set only on the scanned image */
	public static final byte WORD_IMAGE_MATCH_SCANNED_ONLY = 2;
	/** word image match value indicating that the respective pixel is set only in the rendered image */
	public static final byte WORD_IMAGE_MATCH_RENDERED_ONLY = 4;
	
	/**
	 * Match two word images against one another, e.g. an original word image
	 * extracted from a scan against a rendering of an OCR result. However,
	 * this method can compare any word images to one another. This method
	 * computes matches with and without alignment shift, returning the better
	 * matching one.
	 * @param scanned the original scanned word image
	 * @param rendered the rendered word image
	 * @param wordBoxMatch stretch word images to exactly match in size?
	 * @param isVerificationMatch are we verifying an OCR result, or comparing
	 *            otherwise?
	 * @return the match result
	 */
	public static WordImageMatch matchWordImages(WordImage scanned, WordImage rendered, boolean wordBoxMatch, boolean isVerificationMatch) {
		WordImageMatch nswim = matchWordImages(scanned, rendered, true, true, false);
		WordImageMatch swim = matchWordImages(scanned, rendered, true, true, true);
		return ((nswim.sim > swim.sim) ? nswim : swim);
	}
	
	/**
	 * Match two word images against one another, e.g. an original word image
	 * extracted from a scan against a rendering of an OCR result. However,
	 * this method can compare any word images to one another.
	 * @param scanned the original scanned word image
	 * @param rendered the rendered word image
	 * @param wordBoxMatch stretch word images to exactly match in size?
	 * @param isVerificationMatch are we verifying an OCR result, or comparing
	 *            otherwise?
	 * @param allowShift allowShifting boundaries of word images against one
	 *            another to better align stems?
	 * @return the match result
	 */
	public static WordImageMatch matchWordImages(WordImage scanned, WordImage rendered, boolean wordBoxMatch, boolean isVerificationMatch, boolean allowShift) {
		
		//	compute bounds of to-match areas
		int sLeft = scanned.box.getLeftCol();
		int sRight = scanned.box.getRightCol();
		int sWidth = (sRight - sLeft);
		int sTop = (wordBoxMatch ? scanned.box.getTopRow() : Math.min(scanned.baseline, scanned.box.getTopRow()));
		int sBottom = (wordBoxMatch ? scanned.box.getBottomRow() : Math.max(scanned.baseline, scanned.box.getBottomRow()));
		int sHeight = (sBottom - sTop);
		int rLeft = rendered.box.getLeftCol();
		int rRight = rendered.box.getRightCol();
		int rWidth = (rRight - rLeft);
		int rTop = (wordBoxMatch ? rendered.box.getTopRow() : Math.min(rendered.baseline, rendered.box.getTopRow()));
		int rBottom = (wordBoxMatch ? rendered.box.getBottomRow() : Math.max(rendered.baseline, rendered.box.getBottomRow()));
		int rHeight = (rBottom - rTop);
		
		int mWidth = Math.max(sWidth, rWidth);
		int mHeight = Math.max(sHeight, rHeight);
		
		//	align columns if allowed to
		int leftShift = 0;
		int rightShift = 0;
		if (allowShift) {
			int[] colShifts = getHistogramAlignmentShifts(scanned.colBrightnessHist, rendered.colBrightnessHist, Math.max((Math.max(scanned.pageImageDpi, rendered.pageImageDpi) / 75), (mWidth / (isVerificationMatch ? 10 : 20))) /* 5% of match width, 10% for verification matches */);
			leftShift = colShifts[0];
			rightShift = colShifts[1];
			if ((leftShift != 0) || (rightShift != 0)) {
				sLeft = (sLeft - Math.max(-leftShift, 0));
				sRight = (sRight + Math.max(-rightShift, 0));
				sWidth = (sRight - sLeft);
				rLeft = (rLeft - Math.max(leftShift, 0));
				rRight = (rRight + Math.max(rightShift, 0));
				rWidth = (rRight - rLeft);
				mWidth = Math.max(sWidth, rWidth);
			}
		}
		
		//	align rows
		int topShift = 0;
		int bottomShift = 0;
		if (allowShift) {
			int[] rowShifts = getHistogramAlignmentShifts(scanned.rowBrightnessHist, rendered.rowBrightnessHist, (mHeight / 10) /* 10% of match height, to account for variations in descender length */);
			topShift = rowShifts[0];
			bottomShift = rowShifts[1];
			if ((topShift != 0) || (bottomShift != 0)) {
				sTop = (sTop - Math.max(-topShift, 0));
				sBottom = (sBottom + Math.max(-bottomShift, 0));
				sHeight = (sBottom - sTop);
				rTop = (rTop - Math.max(topShift, 0));
				rBottom = (rBottom + Math.max(bottomShift, 0));
				rHeight = (rBottom - rTop);
				mHeight = Math.max(sHeight, rHeight);
			}
		}
		
		//	compute match
		byte[][] mData = new byte[mWidth][mHeight];
		int matched = 0;
		int scannedOnly = 0;
		int renderedOnly = 0;
		for (int mCol = 0; mCol < mWidth; mCol++) {
			int sCol = (sLeft + ((mCol * sWidth) / mWidth));
			if (sCol < sLeft)
				continue;
			if (sRight <= sCol)
				break;
			int rCol = (rLeft + ((mCol * rWidth) / mWidth));
			if (rCol < rLeft)
				continue;
			if (rRight <= rCol)
				break;
			
			for (int mRow = 0; mRow < mHeight; mRow++) {
				int sRow = (sTop + ((mRow * sHeight) / mHeight));
				if (sRow < sTop)
					continue;
				if (sBottom <= sRow)
					break;
				int rRow = (rTop + ((mRow * rHeight) / mHeight));
				if (rRow < rTop)
					continue;
				if (rBottom <= rRow)
					break;
				
				byte sb = (((0 <= sCol) && (sCol < scanned.brightness.length) && (0 <= sRow) && (sRow < scanned.brightness[sCol].length)) ? scanned.brightness[sCol][sRow] : ((byte) 127));
				byte rb = (((0 <= rCol) && (rCol < rendered.brightness.length) && (0 <= rRow) && (rRow < rendered.brightness[rCol].length)) ? rendered.brightness[rCol][rRow] : ((byte) 127));
				if ((sb < 80) && (rb < 80)) {
					matched++;
					mData[mCol][mRow] = WORD_IMAGE_MATCH_MATCHED;
				}
				else if (sb < 80) {
					scannedOnly++;
					mData[mCol][mRow] = WORD_IMAGE_MATCH_SCANNED_ONLY;
				}
				else if (rb < 80) {
					renderedOnly++;
					mData[mCol][mRow] = WORD_IMAGE_MATCH_RENDERED_ONLY;
				}
				else mData[mCol][mRow] = WORD_IMAGE_MATCH_NONE;
			}
		}
		
		//	finally ...
		return new WordImageMatch(scanned, rendered, leftShift, rightShift, topShift, bottomShift, mData, matched, scannedOnly, renderedOnly);
	}
	/**
	 * Compute the best alignment shifts between two histograms. This method
	 * minimizes the sum of the square differences between aligned histogram
	 * buckets. Which histogram to shift inward is expressed by the sign of the
	 * returned shifts:<br>
	 * - negative shift: shift first argument inward<br>
	 * - positive shift: shift second argument inward<br>
	 * @param sHist the first histogram
	 * @param rHist the second histogram
	 * @param maxShift the maximum shift, either way
	 * @returns an array holding the two shifts, <code>[leftOrTopShift, rightOrBottomShift]</code>
	 */
	public static int[] getHistogramAlignmentShifts(int[] sHist, int[] rHist, int maxShift) {
//		return getHistogramAlignmentShifts(sHist, rHist, maxShift, true);
//	}
//	
//	/**
//	 * Compute the best alignment shifts between two histograms. This method
//	 * minimizes the sum of the square differences between aligned histogram
//	 * buckets. Which histogram to shift inward is expressed by the sign of the
//	 * returned shifts:<br>
//	 * - negative shift: shift first argument inward<br>
//	 * - positive shift: shift second argument inward<br>
//	 * @param sHist the first histogram
//	 * @param rHist the second histogram
//	 * @param maxShift the maximum shift, either way
//	 * @param ignoreEmptyEdges ignore leading and tailing 0s in shifts?
//	 * @returns an array holding the two shifts, <code>[leftOrTopShift, rightOrBottomShift]</code>
//	 */
//	public static int[] getHistogramAlignmentShifts(int[] sHist, int[] rHist, int maxShift, boolean ignoreEmptyEdges) {
		int sLeft = 0;
		while ((sLeft < sHist.length) && (sHist[sLeft] == 0))
			sLeft++;
		int sRight = sHist.length;
		while ((sRight != 0) && (sHist[sRight-1] == 0))
			sRight--;
		int rLeft = 0;
		while ((rLeft < rHist.length) && (rHist[rLeft] == 0))
			rLeft++;
		int rRight = rHist.length;
		while ((rRight != 0) && (rHist[rRight-1] == 0))
			rRight--;
		
		if ((sRight <= sLeft) || (rRight <= rLeft)) {
			int[] shifts = {0, 0};
			return shifts;
		}
		
		int sWidth = (sRight - sLeft);
		int rWidth = (rRight - rLeft);
		
		int leftShift = 0;
		int rightShift = 0;
		int bestShiftDistSquareSum = Integer.MAX_VALUE;
		for (int ls = -maxShift; ls <= maxShift; ls++)
			for (int rs = -maxShift; rs <= maxShift; rs++) {
				int shiftDistSquareSum = 0;
				int mWidth = Math.max((sWidth + Math.max(-ls, 0) + Math.max(-rs, 0)), (rWidth + Math.max(ls, 0) + Math.max(rs, 0)));
				for (int c = 0; c < mWidth; c++) {
//					//	THIS IS NOT PUSHING INWARD, THIS IS JUMPING INWARD, AKA PULLING OUTWARD !!!
//					int sCol = ((sLeft + Math.max(-ls, 0)) + ((c * (sWidth - Math.max(-ls, 0) - Math.max(-rs, 0))) / (mWidth - Math.max(-ls, 0) - Math.max(-rs, 0))));
//					int rCol = (rLeft + Math.max(ls, 0) + ((c * (rWidth - Math.max(ls, 0) - Math.max(rs, 0))) / (mWidth - Math.max(ls, 0) - Math.max(rs, 0))));
					//	PUSH INWARD
//					int sCol = (0 - Math.max(-ls, 0) + ((c * (sHist.length + Math.max(-ls, 0) + Math.max(-rs, 0))) / mWidth));
//					int rCol = (0 - Math.max(ls, 0) + ((c * (rHist.length + Math.max(ls, 0) + Math.max(rs, 0))) / mWidth));
					int sCol = (sLeft - Math.max(-ls, 0) + ((c * (sWidth + Math.max(-ls, 0) + Math.max(-rs, 0))) / mWidth));
					int rCol = (rLeft - Math.max(ls, 0) + ((c * (rWidth + Math.max(ls, 0) + Math.max(rs, 0))) / mWidth));
					
					int sps = (((0 <= sCol) && (sCol < sHist.length)) ? sHist[sCol] : 0);
					int rps = (((0 <= rCol) && (rCol < rHist.length)) ? rHist[rCol] : 0);
					shiftDistSquareSum += ((sps - rps) * (sps - rps));
				}
				if (shiftDistSquareSum < bestShiftDistSquareSum) {
					bestShiftDistSquareSum = shiftDistSquareSum;
					leftShift = ls;
					rightShift = rs;
				}
			}
		int[] shifts = {leftShift, rightShift};
		return shifts;
	}
}