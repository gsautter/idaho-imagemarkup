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
 *     * Neither the name of the Universitaet Karlsruhe (TH) nor the
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
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import de.uka.ipd.idaho.gamta.util.ProgressMonitor;
import de.uka.ipd.idaho.gamta.util.constants.TableConstants;
import de.uka.ipd.idaho.gamta.util.imaging.BoundingBox;
import de.uka.ipd.idaho.gamta.util.imaging.ImagingConstants;
import de.uka.ipd.idaho.gamta.util.imaging.PageImage;
import de.uka.ipd.idaho.im.ImDocument;
import de.uka.ipd.idaho.im.ImLayoutObject;
import de.uka.ipd.idaho.im.ImPage;
import de.uka.ipd.idaho.im.ImRegion;
import de.uka.ipd.idaho.im.ImWord;
import de.uka.ipd.idaho.im.analysis.Imaging.AnalysisImage;
import de.uka.ipd.idaho.im.analysis.Imaging.ImagePartRectangle;
import de.uka.ipd.idaho.im.analysis.PageImageAnalysis.Block;
import de.uka.ipd.idaho.im.analysis.PageImageAnalysis.Line;
import de.uka.ipd.idaho.im.analysis.PageImageAnalysis.Region;
import de.uka.ipd.idaho.im.analysis.PageImageAnalysis.TableCell;
import de.uka.ipd.idaho.im.analysis.PageImageAnalysis.TableRow;
import de.uka.ipd.idaho.im.analysis.PageImageAnalysis.Word;
import de.uka.ipd.idaho.stringUtils.StringUtils;

/**
 * Function library converting the results of page image analyzes into GAMTA
 * documents.
 * 
 * @author sautter
 */
public class PageImageConverter implements ImagingConstants, TableConstants {
	
	private static final int maxAnalysisDpi = 300; // higher resolutions are too computationally expensive and do not improve accuracy to any noticable degree
	
	/**
	 * Analyze the structure of pages, i.e., columns and blocks. The document
	 * structure is stored in the page annotations of the argument GAMTA
	 * document, replacing their original content.
	 * @param doc the document to store the structural information in
	 * @param psm a monitor object for reporting progress, e.g. to a UI
	 * @return the argument GAMTA document
	 * @throws IOException
	 */
	public static ImDocument fillInPageRegions(ImDocument doc, ProgressMonitor psm) throws IOException {
		if (psm == null)
			psm = ProgressMonitor.dummy;
		
		//	analyze pages
		ImPage[] pages = doc.getPages();
		for (int p = 0; p < pages.length; p++) {
			psm.setStep("Analyzing page " + (p+1) + " of " + pages.length);
			psm.setProgress((100 * p) / pages.length);
			
			//	clean up from previous round (references are void only now)
			if (p != 0)
				System.gc();
//			
//			//	check if page yet to analyze
//			if ((pages[p].size() > 1) || !"P".equals(pages[p].firstValue())) {
//				psm.setInfo(" - page analyzed before");
//				continue;
//			}
			
			//	get page image
			String pageImageName = ((String) pages[p].getAttribute(IMAGE_NAME_ATTRIBUTE));
			PageImage pageImage = ((pageImageName == null) ? null : PageImage.getPageImage(pageImageName));
			if (pageImage == null) {
				psm.setInfo(" --> page image not found");
				continue;
			}
			
			//	analyze page structure
			int wordCount = fillInPageRegions(pages[p], pageImage.image, pageImage.currentDpi, psm);
			psm.setInfo(" --> got " + wordCount + " words in total");
			
			//	... finally
			psm.setInfo(" - page done");
		}
		
		//	clean up one last time
		System.gc();
		
		//	finally ...
		psm.setProgress(100);
		return doc;
	}
	
	/**
	 * Analyze the region structure of a single page, i.e., columns and blocks.
	 * The structure is stored in the argument page annotation, replacing its
	 * original content.
	 * @param page the page to analyze
	 * @param psm a monitor object for reporting progress, e.g. to a UI
	 * @return the number of individual text blocks found in the page
	 * @throws IOException
	 */
	public static int fillInPageRegions(ImPage page, ProgressMonitor psm) throws IOException {
		String pageImageName = ((String) page.getAttribute(IMAGE_NAME_ATTRIBUTE));
		PageImage pageImage = ((pageImageName == null) ? null : PageImage.getPageImage(pageImageName));
		if (pageImage == null)
			throw new IOException("Could not find page image");
		
		return fillInPageRegions(page, pageImage.image, pageImage.currentDpi, psm);
	}
	
	/**
	 * Analyze the region structure of a single page, i.e., columns and blocks.
	 * The structure is stored in the argument page annotation, replacing its
	 * original content.
	 * @param page the page to analyze
	 * @param pageImage the image of the page
	 * @param psm a monitor object for reporting progress, e.g. to a UI
	 * @return the number of individual text blocks found in the page
	 * @throws IOException
	 */
	public static int fillInPageRegions(ImPage page, PageImage pageImage, ProgressMonitor psm) throws IOException {
		return fillInPageRegions(page, pageImage.image, pageImage.currentDpi, psm);
	}
	
	/**
	 * Analyze the region structure of a single page, i.e., columns and blocks.
	 * The structure is stored in the argument page annotation, replacing its
	 * original content. All images wrapped for analysis via the
	 * Imaging.wrapImage() method are cached as with the key
	 * '&lt;imageHashCode&gt;-&lt;imageDpi&gt;'.
	 * @param page the page to analyze
	 * @param pageImage the image of the page
	 * @param imageDpi the resolution of the page image
	 * @param psm a monitor object for reporting progress, e.g. to a UI
	 * @return the number of individual text blocks found in the page
	 * @throws IOException
	 */
	public static int fillInPageRegions(ImPage page, BufferedImage pageImage, int imageDpi, ProgressMonitor psm) throws IOException {
		if (psm == null)
			psm = ProgressMonitor.dummy;
		
		//	scale down image for structure analysis if necessary
		AnalysisImage ai = Imaging.wrapImage(pageImage, (pageImage.hashCode() + "-" + imageDpi));
		int analysisDpi = imageDpi;
		while (analysisDpi > maxAnalysisDpi)
			analysisDpi /= 2;
		if (analysisDpi != imageDpi) {
			BufferedImage bi = ai.getImage();
			BufferedImage sbi = new BufferedImage(((bi.getWidth() * analysisDpi) / imageDpi), ((bi.getHeight() * analysisDpi) / imageDpi), ((bi.getType() == BufferedImage.TYPE_CUSTOM) ? BufferedImage.TYPE_BYTE_GRAY : bi.getType()));
			sbi.getGraphics().setColor(Color.WHITE);
			sbi.getGraphics().fillRect(0, 0, sbi.getWidth(), sbi.getHeight());
			sbi.getGraphics().drawImage(bi, 0, 0, sbi.getWidth(), sbi.getHeight(), null);
			ai = Imaging.wrapImage(sbi, (pageImage.hashCode() + "-" + analysisDpi));
			Imaging.whitenWhite(ai);
			psm.setInfo(" - image scaled to " + analysisDpi + " for structural analysis");
		}
		
		//	find text blocks
		return fillInPageRegions(page, ai, analysisDpi, imageDpi, psm);
	}
	
	private static int fillInPageRegions(ImPage page, AnalysisImage ai, int analysisDpi, int imageDpi, ProgressMonitor psm) throws IOException {
		
		//	find text blocks
		Region thePage = PageImageAnalysis.getPageRegion(ai, analysisDpi, true, psm);
		
		//	assemble document from structure
		fillInSubRegions(page, thePage, imageDpi, analysisDpi);
		
		//	finally ...
		return page.getRegions().length;
	}
	
	private static void fillInSubRegions(ImPage page, Region theRegion, int imageDpi, int analysisDpi) {
		
		//	classify region
		String regionType;
		
		//	atomic region ==> annotate as block
		if (theRegion.isAtomic)
			regionType = BLOCK_ANNOTATION_TYPE;
		
		//	never annotate whole page if it's not atomic
		else if (theRegion.superRegion == null)
			regionType = null;
		
		//	non-atomic column with atomic blocks ==> annotate as column
		else if (theRegion.isColumn && theRegion.areChildrenAtomic())
			regionType = COLUMN_ANNOTATION_TYPE;
		
		//	higher level region ==> annotate as such (might turn out helpful for structure corrections after OCR, so retain it)
		else regionType = REGION_ANNOTATION_TYPE;
		
		//	mark region
		new ImRegion(page, theRegion.getBoundingBox(imageDpi / analysisDpi), regionType);
		
		//	fill in child regions
		if (!theRegion.isAtomic) {
			for (int s = 0; s < theRegion.getSubRegionCount(); s++)
				fillInSubRegions(page, theRegion.getSubRegion(s), imageDpi, analysisDpi);
		}
	}
	
	/**
	 * Analyze the inner structure of text blocks in a document, i.e., lines and
	 * words. The structure is stored in the block annotations of the argument
	 * GAMTA document, replacing their original content.
	 * @param doc the document to store the structural information in
	 * @param psm a monitor object for reporting progress, e.g. to a UI
	 * @return the number of individual words found in the blocks of the
	 *         argument document
	 * @throws IOException
	 */
	public static int fillInTextBlockStructure(ImDocument doc, ProgressMonitor psm) throws IOException {
		if (psm == null)
			psm = ProgressMonitor.dummy;
		
		//	analyze pages
		ImPage[] pages = doc.getPages();
		int wordCount = 0;
		for (int p = 0; p < pages.length; p++) {
			psm.setStep("Analyzing page " + (p+1) + " of " + pages.length);
			psm.setProgress((100 * p) / pages.length);
			
			//	clean up from previous round (references are void only now)
			if (p != 0)
				System.gc();
			
			//	analyze page structure
			int pageWordCount = fillInTextBlockStructure(pages[p], null, psm);
			psm.setInfo(" --> got " + pageWordCount + " words in total");
			wordCount += pageWordCount;
			
			//	finally ...
			psm.setInfo(" - page done");
		}
		
		//	clean up one last time
		System.gc();
		
		//	finally ...
		psm.setProgress(100);
		return wordCount;
	}
	
	/**
	 * Split the text blocks in a document page into lines. The lines are stored
	 * in the block annotations of the argument page, replacing its original
	 * content.
	 * @param page the page whose blocks to analyze
	 * @param psm a monitor object for reporting progress, e.g. to a UI
	 * @return the number of lines found in the blocks of the page
	 * @throws IOException
	 */
	public static int fillInTextBlockLines(ImPage page, ProgressMonitor psm) throws IOException {
		String pageImageName = ((String) page.getAttribute(IMAGE_NAME_ATTRIBUTE));
		PageImage pageImage = ((pageImageName == null) ? null : PageImage.getPageImage(pageImageName));
		if (pageImage == null) {
			((psm == null) ? ProgressMonitor.dummy : psm).setInfo(" --> page image not found");
			return 0;
		}
		return fillInTextBlockLines(page, psm, pageImage);
	}
	
	/**
	 * Split the text blocks in a document page into lines. The lines are stored
	 * in the block annotations of the argument page, replacing its original
	 * content.
	 * @param page the page whose blocks to analyze
	 * @param psm a monitor object for reporting progress, e.g. to a UI
	 * @param pageImage the image of the page
	 * @return the number of lines found in the blocks of the page
	 * @throws IOException
	 */
	public static int fillInTextBlockLines(ImPage page, ProgressMonitor psm, PageImage pageImage) throws IOException {
		return fillInTextBlockLines(page, psm, pageImage.image, pageImage.currentDpi);
	}
	
	/**
	 * Split the text blocks in a document page into lines. The lines are stored
	 * in the block annotations of the argument page, replacing its original
	 * content. All images wrapped for analysis via the Imaging.wrapImage()
	 * method are cached as with the key
	 * '&lt;imageHashCode&gt;-&lt;imageDpi&gt;'.
	 * @param page the page whose blocks to analyze
	 * @param psm a monitor object for reporting progress, e.g. to a UI
	 * @param pageImage the image of the page
	 * @param imageDpi the resolution of the page image
	 * @return the number of lines found in the blocks of the page
	 * @throws IOException
	 */
	public static int fillInTextBlockLines(ImPage page, ProgressMonitor psm, BufferedImage pageImage, int imageDpi) throws IOException {
		if (psm == null)
			psm = ProgressMonitor.dummy;
		
		//	scale down image for structure analysis if neccessary
		AnalysisImage ai = Imaging.wrapImage(pageImage, (pageImage.hashCode() + "-" + imageDpi));
		int analysisDpi = imageDpi;
		while (analysisDpi > maxAnalysisDpi)
			analysisDpi /= 2;
		if (analysisDpi != imageDpi) {
			BufferedImage bi = ai.getImage();
			BufferedImage sbi = new BufferedImage(((bi.getWidth() * analysisDpi) / imageDpi), ((bi.getHeight() * analysisDpi) / imageDpi), ((bi.getType() == BufferedImage.TYPE_CUSTOM) ? BufferedImage.TYPE_BYTE_GRAY : bi.getType()));
			sbi.getGraphics().setColor(Color.WHITE);
			sbi.getGraphics().fillRect(0, 0, sbi.getWidth(), sbi.getHeight());
			sbi.getGraphics().drawImage(bi, 0, 0, sbi.getWidth(), sbi.getHeight(), null);
			ai = Imaging.wrapImage(sbi, (pageImage.hashCode() + "-" + analysisDpi));
			Imaging.whitenWhite(ai);
			psm.setInfo(" - image scaled to " + analysisDpi + " for structural analysis");
		}
		
		//	count lines over all blocks
		int lineCount = 0;
		
		//	get blocks
		ImRegion[] blocks = page.getRegions();
		
		//	no blocks marked so far, analyze page structure first
		if (blocks.length == 0) {
			psm.setInfo(" - no blocks found, analyzing page structure");
			fillInPageRegions(page, ai, analysisDpi, imageDpi, psm);
			blocks = page.getRegions(BLOCK_ANNOTATION_TYPE);
		}
		
		//	analyze structure of individual blocks
		for (int b = 0; b < blocks.length; b++) {
			psm.setInfo(" - got " + blocks.length + " blocks");
			psm.setInfo(" - doing block " + (b+1));
			lineCount += fillInTextBlockLines(blocks[b], ai, analysisDpi, imageDpi, psm);
		}
		
		psm.setInfo(" --> got " + lineCount + " lines in total");
		
		//	... finally
		psm.setInfo(" - page done");
		
		//	clean up
		System.gc();
		
		//	finally ...
		psm.setProgress(100);
		return lineCount;
	}
	
	//	called only if block already existed in input document page
	private static int fillInTextBlockLines(ImRegion block, AnalysisImage analysisImage, int analysisDpi, int imageDpi, ProgressMonitor psm) {
		
		//	catch blocks filled in before
		if (block.getRegions(WORD_ANNOTATION_TYPE).length != 0)
			return 0;
		
		//	prepare block
		ImagePartRectangle blockBounds = getBounds(block, analysisImage, imageDpi, analysisDpi);
		if (blockBounds == null)
			return 0;
		Block theBlock = new Block(blockBounds);
		
		//	find text lines and words
		PageImageAnalysis.getBlockLines(theBlock, analysisDpi, psm);
		
		//	append block content
		int lineCount = appendTextBlockLines(block, theBlock, analysisDpi, imageDpi, psm);
		
		//	adjust type
		if (theBlock.isTable())
			block.setType(TABLE_ANNOTATION_TYPE);
		
		//	finally ...
		return lineCount;
	}
	
	//	called only if block structure not yet in input document page or block
	private static int appendTextBlockLines(ImRegion block, Block theBlock, int analysisDpi, int imageDpi, ProgressMonitor psm) {
		
		//	catch empty blocks
		if (theBlock.isEmpty())
			return 0;
		
		//	add lines
		Line[] lines = theBlock.getLines();
		for (int l = 0; l < lines.length; l++)
			new ImRegion(block.getPage(), lines[l].getBoundingBox(imageDpi / analysisDpi), LINE_ANNOTATION_TYPE);
		
		//	finally ...
		return lines.length;
	}
	
	/**
	 * Analyze the inner structure of text blocks in a document page, i.e.,
	 * lines and words. The structure is stored in the block annotations of the
	 * argument page, replacing its original content. If bounding boxes of words
	 * are specified, the analysis is vastly simplified.
	 * @param page the page whose blocks to analyze
	 * @param existingWords bounding boxes of existing words
	 * @param psm a monitor object for reporting progress, e.g. to a UI
	 * @return the number of individual words found in the blocks of the page
	 * @throws IOException
	 */
	public static int fillInTextBlockStructure(ImPage page, BoundingBox[] existingWords, ProgressMonitor psm) throws IOException {
		String pageImageName = ((String) page.getAttribute(IMAGE_NAME_ATTRIBUTE));
		PageImage pageImage = ((pageImageName == null) ? null : PageImage.getPageImage(pageImageName));
		if (pageImage == null) {
			((psm == null) ? ProgressMonitor.dummy : psm).setInfo(" --> page image not found");
			return 0;
		}
		return fillInTextBlockStructure(page, existingWords, psm, pageImage);
	}
	
	/**
	 * Analyze the inner structure of text blocks in a document page, i.e.,
	 * lines and words. The structure is stored in the block annotations of the
	 * argument page, replacing its original content. If bounding boxes of words
	 * are specified, the analysis is vastly simplified.
	 * @param page the page whose blocks to analyze
	 * @param existingWords bounding boxes of existing words
	 * @param psm a monitor object for reporting progress, e.g. to a UI
	 * @param pageImage the image of the page
	 * @return the number of individual words found in the blocks of the page
	 * @throws IOException
	 */
	public static int fillInTextBlockStructure(ImPage page, BoundingBox[] existingWords, ProgressMonitor psm, PageImage pageImage) throws IOException {
		return fillInTextBlockStructure(page, existingWords, psm, pageImage.image, pageImage.currentDpi);
	}
	
	/**
	 * Analyze the inner structure of text blocks in a document page, i.e.,
	 * lines and words. The structure is stored in the block annotations of the
	 * argument page, replacing its original content. If bounding boxes of words
	 * are specified, the analysis is vastly simplified. All images wrapped for
	 * analysis via the Imaging.wrapImage() method are cached as with the key
	 * '&lt;imageHashCode&gt;-&lt;imageDpi&gt;'.
	 * @param page the page whose blocks to analyze
	 * @param existingWords bounding boxes of existing words
	 * @param psm a monitor object for reporting progress, e.g. to a UI
	 * @param pageImage the image of the page
	 * @param imageDpi the resolution of the page image
	 * @return the number of individual words found in the blocks of the page
	 * @throws IOException
	 */
	public static int fillInTextBlockStructure(ImPage page, BoundingBox[] existingWords, ProgressMonitor psm, BufferedImage pageImage, int imageDpi) throws IOException {
		if (psm == null)
			psm = ProgressMonitor.dummy;
		
		//	scale down image for structure analysis if necessary (only if we don't have pre-existing words, though)
		AnalysisImage ai = Imaging.wrapImage(pageImage, (pageImage.hashCode() + "-" + imageDpi));
		int analysisDpi = imageDpi;
		while ((existingWords == null) && (analysisDpi > maxAnalysisDpi))
			analysisDpi /= 2;
		if (analysisDpi != imageDpi) {
			BufferedImage bi = ai.getImage();
			BufferedImage sbi = new BufferedImage(((bi.getWidth() * analysisDpi) / imageDpi), ((bi.getHeight() * analysisDpi) / imageDpi), ((bi.getType() == BufferedImage.TYPE_CUSTOM) ? BufferedImage.TYPE_BYTE_GRAY : bi.getType()));
			sbi.getGraphics().setColor(Color.WHITE);
			sbi.getGraphics().fillRect(0, 0, sbi.getWidth(), sbi.getHeight());
			sbi.getGraphics().drawImage(bi, 0, 0, sbi.getWidth(), sbi.getHeight(), null);
			ai = Imaging.wrapImage(sbi, (pageImage.hashCode() + "-" + analysisDpi));
			Imaging.whitenWhite(ai);
			psm.setInfo(" - image scaled to " + analysisDpi + " for structural analysis");
		}
		
		//	count words over all blocks
		int wordCount = 0;
		
		//	get blocks
		ImRegion[] blocks = page.getRegions(BLOCK_ANNOTATION_TYPE);
		ImRegion[] tables = page.getRegions(TABLE_ANNOTATION_TYPE);
		
		//	no blocks marked so far, analyze page structure first
		if ((blocks.length == 0) && (tables.length == 0)) {
			psm.setInfo(" - no blocks found, analyzing page structure");
			Region thePage = PageImageAnalysis.getPageRegion(ai, analysisDpi, true, psm);
			wordCount = appendRegionStructure(page, thePage, analysisDpi, imageDpi, psm, existingWords);
		}
		
		//	analyze structure of individual blocks
		else for (int b = 0; b < blocks.length; b++) {
			psm.setInfo(" - got " + blocks.length + " blocks");
			psm.setInfo(" - doing block " + (b+1));
			wordCount += fillInTextBlockStructure(blocks[b], ai, analysisDpi, imageDpi, psm, existingWords);
		}
		
		psm.setInfo(" --> got " + wordCount + " words in total");
		
		//	... finally
		psm.setInfo(" - page done");
		
		//	clean up
		System.gc();
		
		//	finally ...
		psm.setProgress(100);
		return wordCount;
	}
	
	private static int appendRegionStructure(ImRegion region, Region theRegion, int analysisDpi, int imageDpi, ProgressMonitor psm, BoundingBox[] existingWords) {
		
		//	mark block
		ImRegion block = new ImRegion(region.getPage(), theRegion.getBoundingBox(imageDpi / analysisDpi));
		
		//	atomic region ==> block, do further analysis
		if (theRegion.isAtomic) {
			
			//	analyze block structure
			Block theBlock = new Block(theRegion.bounds);
			PageImageAnalysis.getBlockStructure(theBlock, analysisDpi, existingWords, psm);
			
			//	set block type
			block.setType(theBlock.isTable() ? TABLE_ANNOTATION_TYPE : BLOCK_ANNOTATION_TYPE);
			
			//	mark block content
			return addTextBlockStructure(region, theBlock, analysisDpi, imageDpi, psm);
		}
		
		//	set block type
		block.setType((theRegion.isColumn && theRegion.areChildrenAtomic()) ? COLUMN_ANNOTATION_TYPE : REGION_ANNOTATION_TYPE);
		
		//	recurse to child regions
		int wordCount = 0;
		for (int s = 0; s < theRegion.getSubRegionCount(); s++)
			wordCount += appendRegionStructure(region, theRegion.getSubRegion(s), analysisDpi, imageDpi, psm, existingWords);
		
		//	finally ...
		return wordCount;
	}
	
	/**
	 * Analyze the inner structure of a text block, i.e., lines and words. The
	 * structure is stored in the argument block annotation, replacing its
	 * original content. The block is first split into the specified number of
	 * lines, and then the lines are split into words.
	 * @param block the block to analyze
	 * @param pageImage the image of the page the block is on
	 * @param blockTextLines the lines making up the block text
	 * @param psm a monitor object for reporting progress, e.g. to a UI
	 * @return the number of individual words found in the block
	 * @throws IOException
	 */
	public static int fillInTextBlockStructure(ImRegion block, PageImage pageImage, String[] blockTextLines, ProgressMonitor psm) throws IOException {
		return fillInTextBlockStructure(block, pageImage.image, pageImage.currentDpi, blockTextLines, psm);
	}
	
	/**
	 * Analyze the inner structure of a text block, i.e., lines and words. The
	 * structure is stored in the argument block annotation, replacing its
	 * original content. The block is first split into the specified number of
	 * lines, and then the lines are split into words. All images wrapped for
	 * analysis via the Imaging.wrapImage() method are cached as with the key
	 * '&lt;imageHashCode&gt;-&lt;imageDpi&gt;'.
	 * @param block the block to analyze
	 * @param pageImage the image of the page the block is on
	 * @param imageDpi the resolution of the page image
	 * @param blockTextLines the lines making up the block text
	 * @param psm a monitor object for reporting progress, e.g. to a UI
	 * @return the number of individual words found in the block
	 * @throws IOException
	 */
	public static int fillInTextBlockStructure(ImRegion block, BufferedImage pageImage, int imageDpi, String[] blockTextLines, ProgressMonitor psm) throws IOException {
		if (psm == null)
			psm = ProgressMonitor.dummy;
		
		//	scale down image for structure analysis if necessary
		AnalysisImage ai = Imaging.wrapImage(pageImage, (pageImage.hashCode() + "-" + imageDpi));
		int analysisDpi = imageDpi;
		while (analysisDpi > maxAnalysisDpi)
			analysisDpi /= 2;
		if (analysisDpi != imageDpi) {
			BufferedImage bi = ai.getImage();
			BufferedImage sbi = new BufferedImage(((bi.getWidth() * analysisDpi) / imageDpi), ((bi.getHeight() * analysisDpi) / imageDpi), ((bi.getType() == BufferedImage.TYPE_CUSTOM) ? BufferedImage.TYPE_BYTE_GRAY : bi.getType()));
			sbi.getGraphics().setColor(Color.WHITE);
			sbi.getGraphics().fillRect(0, 0, sbi.getWidth(), sbi.getHeight());
			sbi.getGraphics().drawImage(bi, 0, 0, sbi.getWidth(), sbi.getHeight(), null);
			ai = Imaging.wrapImage(sbi, (pageImage.hashCode() + "-" + analysisDpi));
			Imaging.whitenWhite(ai);
			psm.setInfo(" - image scaled to " + analysisDpi + " for structural analysis");
		}
		
		//	find text lines and words
		return fillInTextBlockStructure(block, ai, analysisDpi, imageDpi, blockTextLines, psm);
	}
	
	private static int fillInTextBlockStructure(ImRegion block, AnalysisImage analysisImage, int analysisDpi, int imageDpi, String[] blockTextLines, ProgressMonitor psm) {
		
		//	get block bounds
		ImagePartRectangle blockBounds = getBounds(block, analysisImage, imageDpi, analysisDpi);
		if (blockBounds == null)
			return 0;
		
		//	compute pixel row brightness, and count non-white pixel rows to estimate line height
		byte[][] brightness = analysisImage.getBrightness();
		byte[] blockRowBrightnesses = new byte[blockBounds.bottomRow - blockBounds.topRow];
		int nonWhiteRowCount = 0;
		for (int r = blockBounds.topRow; r < blockBounds.bottomRow; r++) {
			int brightnessSum = 0;
			for (int c = blockBounds.leftCol; c < blockBounds.rightCol; c++)
				brightnessSum += brightness[c][r];
			blockRowBrightnesses[r - blockBounds.topRow] = ((byte) (brightnessSum / (blockBounds.rightCol - blockBounds.leftCol)));
			if (blockRowBrightnesses[r-blockBounds.topRow] < 127)
				nonWhiteRowCount++;
		}
		int nwrAvgLineHeight = (nonWhiteRowCount / blockTextLines.length);
		System.out.println("Average line height from non-white rows: " + nwrAvgLineHeight);
		
		//	chop block into line groups
//		int minLineGroupMargin = (analysisDpi / 25); // a little less than a millimeter
//		int minLineGroupMargin = 1; // FOR TEST ==> seems OK, actually ... let's wait for that cut-off i dot
		int minLineGroupMargin = (analysisDpi / 50); // a little less than half a millimeter, should leave the i dots attached, at least at usual main text font sizes
		ImagePartRectangle[] lineGroups = Imaging.splitIntoRows(blockBounds, minLineGroupMargin);
		
		//	sum up height of line groups and estimate line height
		int lineGroupHeightSum = 0;
		for (int g = 0; g < lineGroups.length; g++)
			lineGroupHeightSum += (lineGroups[g].bottomRow - lineGroups[g].topRow);
		int lgAvgLineHeight = (lineGroupHeightSum / blockTextLines.length);
		System.out.println("Average line height from " + lineGroups.length + " line groups: " + lgAvgLineHeight);
		
		//	split line groups into individual lines
		ArrayList lineList = new ArrayList();
		ArrayList lineSplitClean = new ArrayList();
		for (int g = 0; g < lineGroups.length; g++) {
			int groupLineCount = (((lineGroups[g].bottomRow - lineGroups[g].topRow) + (lgAvgLineHeight / 3)) / lgAvgLineHeight);
			System.out.println("Got " + groupLineCount + " lines in group " + g + " (height " + (lineGroups[g].bottomRow - lineGroups[g].topRow) + ")");
			
			//	catch empty line groups
			if (groupLineCount == 0)
				continue;
			
			//	handle single-line groups right away
			if (groupLineCount == 1) {
				lineGroups[g] = Imaging.narrowLeftAndRight(lineGroups[g]);
				lineList.add(lineGroups[g]);
				lineSplitClean.add(null);
				continue;
			}
			
			//	chop up line group into individual lines equidistantly
			int[] lineSplits = new int[groupLineCount+1];
			boolean[] lineSplitCleanAbove = new boolean[groupLineCount];
			lineSplits[0] = lineGroups[g].topRow;
			lineSplitCleanAbove[0] = true;
			lineSplits[groupLineCount] = lineGroups[g].bottomRow;
			for (int l = 1; l < groupLineCount; l++)
				lineSplits[l] = (lineSplits[0] + ((l * (lineSplits[groupLineCount] - lineSplits[0])) / groupLineCount));
			
			//	refine line splits searching up and down by a few pixels and use brightest split, starting from bottom, as if there are larger font lines (heading), they are attached at the top
			for (int l = groupLineCount-1; l > 0; l--) {
				
				//	compute min and max clean (pixel row brightness 127) split a little less than a millimeter up and down from equidistant split
				//	TODO move this interval up or down depending on relation of starting split (re-adjusted after each line) and initial equidistant split
				int minCleanLineSplit = lineSplits[l]-(analysisDpi / 25);
				if (minCleanLineSplit < blockBounds.topRow)
					minCleanLineSplit = blockBounds.topRow;
				int maxCleanLineSplit = lineSplits[l]+(analysisDpi / 25);
				if (maxCleanLineSplit > blockBounds.bottomRow)
					maxCleanLineSplit = blockBounds.bottomRow;
				
				//	compute clean split interval
				int cleanLineSplitTop = -1;
				for (int r = lineSplits[l]; r >= minCleanLineSplit; r--) {
					if (blockRowBrightnesses[r - blockBounds.topRow] == 127)
						cleanLineSplitTop = r;
					else break;
				}
				if (cleanLineSplitTop == -1) {
					for (int r = lineSplits[l]; r < maxCleanLineSplit; r++)
						if (blockRowBrightnesses[r - blockBounds.topRow] == 127) {
							cleanLineSplitTop = r;
							break;
						}
				}
				int cleanLineSplitBottom = -1;
				for (int r = lineSplits[l]; r < maxCleanLineSplit; r++) {
					if (blockRowBrightnesses[r - blockBounds.topRow] == 127)
						cleanLineSplitBottom = r;
					else break;
				}
				if (cleanLineSplitBottom == -1) {
					for (int r = lineSplits[l]; r >= minCleanLineSplit; r--)
						if (blockRowBrightnesses[r - blockBounds.topRow] == 127) {
							cleanLineSplitBottom = r;
							break;
						}
				}
				
				//	place line split in middle of clean interval (if we found any)
				int lineSplit = (((cleanLineSplitTop == -1) || (cleanLineSplitBottom == -1)) ? -1 : ((cleanLineSplitTop + cleanLineSplitBottom) / 2));
				
				lineSplitCleanAbove[l] = (lineSplit != -1);
				
				//	clean line split not found, try and find best gray split
				if (lineSplit == -1) {
					byte bestLineSplitBrightness = 0;
					
					//	compute min and max split a little less than half a millimeter up and down from equidistant split
					//	TODO move this interval up or down depending on relation of starting split (re-adjusted after each line) and initial equidistant split
					int minLineSplit = lineSplits[l]-(analysisDpi / 50);
					int maxLineSplit = lineSplits[l]+(analysisDpi / 50);
					
					//	search outward from middle of interval
					int lineSplitCenter = ((minLineSplit + maxLineSplit) / 2);
					int lineSplitRadius = ((maxLineSplit - minLineSplit) / 2);
					for (int r = 0; r < lineSplitRadius; r++) {
						if (blockRowBrightnesses[lineSplitCenter - r - blockBounds.topRow] > bestLineSplitBrightness) {
							bestLineSplitBrightness = blockRowBrightnesses[lineSplitCenter - r - blockBounds.topRow];
							lineSplit = (lineSplitCenter - r);
						}
						if (blockRowBrightnesses[lineSplitCenter + r - blockBounds.topRow] > bestLineSplitBrightness) {
							bestLineSplitBrightness = blockRowBrightnesses[lineSplitCenter + r - blockBounds.topRow];
							lineSplit = (lineSplitCenter + r);
						}
					}
				}
				
				//	after refining one split, recompute the others equidistantly in the remaining block
				if (lineSplits[l] != lineSplit) {
					lineSplits[l] = lineSplit;
					for (int la = 1; la < l; la++)
						lineSplits[la] = (lineSplits[0] + ((la * (lineSplits[l] - lineSplits[0])) / l));
				}
			}
			
			//	store lines
			for (int l = 0; l < groupLineCount; l++) {
				ImagePartRectangle lineBounds = new ImagePartRectangle(analysisImage);
				lineBounds.leftCol = lineGroups[g].leftCol;
				lineBounds.rightCol = lineGroups[g].rightCol;
				lineBounds.topRow = lineSplits[l];
				lineBounds.bottomRow = lineSplits[l+1];
				lineBounds.rightCol = lineGroups[g].rightCol;
				lineBounds = Imaging.narrowLeftAndRight(lineBounds);
				lineBounds = Imaging.narrowTopAndBottom(lineBounds);
				if (lineBounds.isEmpty())
					continue;
				lineList.add(lineBounds);
				lineSplitClean.add(lineSplitCleanAbove[l] ? null : "");
			}
		}
		
		//	mark last line split as clean
		lineSplitClean.add(null);
		
		//	prepare layout analysis
		Block theBlock = new Block(blockBounds);
		int wordCount = 0;
		
		//	build line bounding boxes
		for (int l = 0; l < lineList.size(); l++) {
			ImagePartRectangle lineBounds = ((ImagePartRectangle) lineList.get(l));
			
			//	check if line boundaries clean
			boolean cleanSplitAbove = (lineSplitClean.get(l) == null);
			boolean cleanSplitBelow = (lineSplitClean.get(l+1) == null);
			
			//	find line start and end, taking middle two quarters of line box and narrowing left and right
			int lineTop = lineBounds.topRow;
			int lineBottom = lineBounds.bottomRow;
			int lineHeight = (lineBottom - lineTop);
			lineBounds.topRow = (lineTop + (cleanSplitAbove ? 0 : (lineHeight / 8)));
			lineBounds.bottomRow = (lineBottom - (cleanSplitBelow ? 0 : (lineHeight / 8)));
			lineBounds = Imaging.narrowLeftAndRight(lineBounds);
			if (lineBounds.isEmpty())
				continue;
			
			//	check if line has ascent and/or descent
			boolean hasAscent = false;
			boolean hasDescent = false;
			if (l < blockTextLines.length)
				for (int c = 0; c < blockTextLines[l].length(); c++) {
					char ch = blockTextLines[l].charAt(c);
					if (Character.isDigit(ch))
						hasAscent = true;
					else if (Character.isLetter(ch)) {
						if (!hasAscent && (ch != Character.toLowerCase(ch)))
							hasAscent = true;
						char bch = StringUtils.getBaseChar(ch);
						if (!hasAscent && ("bdfhkl".indexOf(bch) != -1))
							hasAscent = true;
						if (!hasDescent && ("jpqy".indexOf(bch) != -1))
							hasDescent = true;
					}
					else hasAscent = (hasAscent || ("+~,;.:-_<>=".indexOf(ch) == -1));
					if (hasAscent && hasDescent)
						break;
				}
			
			//	split line with margin 1, then assess cumulation points of distances
			ImagePartRectangle[] lineChops = Imaging.splitIntoColumns(lineBounds, 1);
			System.out.println("Got " + lineChops.length + " chops in line " + l + " (" + ((l < blockTextLines.length) ? blockTextLines[l] : "") + ")");
			int maxChopDistance = 0;
			for (int c = 1; c < lineChops.length; c++) {
				int cd = (lineChops[c].leftCol - lineChops[c-1].rightCol);
				maxChopDistance = Math.max(maxChopDistance, cd);
			}
			System.out.println(" - max chop distance is " + maxChopDistance);
			int[] chopDistanceCounts = new int[maxChopDistance + 1];
			Arrays.fill(chopDistanceCounts, 0);
			for (int c = 1; c < lineChops.length; c++) {
				int cd = (lineChops[c].leftCol - lineChops[c-1].rightCol);
				chopDistanceCounts[cd]++;
			}
			System.out.println(" - chop distance distribution");
			for (int d = 0; d < chopDistanceCounts.length; d++)
				System.out.println("   - " + d + ": " + chopDistanceCounts[d]);
			
			//	compute minimum word margin
			int minWordMargin;
			
			//	compute number of words from OCR result and use chop distances to measure margin
			if (l < blockTextLines.length) {
				int spacesRemaining = blockTextLines[l].trim().split("\\s+").length - 1;
				minWordMargin = maxChopDistance+1;
				for (int d = maxChopDistance; d >= 0; d--) {
					if (spacesRemaining >= chopDistanceCounts[d]) {
						spacesRemaining -= chopDistanceCounts[d];
						minWordMargin = d;
					}
					else break;
				}
				System.out.println(" ==> minimum word margin counted out from spaces as " + minWordMargin);
			}
			
			//	compute word margin (see comment below method for derivation of formula)
			else {
				int minWordMarginDenominator;
				if (hasAscent && hasDescent)
					minWordMarginDenominator = 15;
				else if (hasAscent)
					minWordMarginDenominator = 12;
				else if (hasDescent)
					minWordMarginDenominator = 11;
				else minWordMarginDenominator = 8;
				minWordMargin = (((lineBottom - lineTop) * 5) / minWordMarginDenominator); // TODO find out if this works, finally
				System.out.println(" ==> minimum word margin computed as " + minWordMargin);
			}
			
//			this does not work with skewed images, as skew makes lines higher than they actually are, incurring too large margin threshold ==> good fallback, but space counting seems to work better
//			int minWordMarginDenominator;
//			if (hasAscent && hasDescent)
//				minWordMarginDenominator = 15;
//			else if (hasAscent)
//				minWordMarginDenominator = 12; 
//			else if (hasDescent)
//				minWordMarginDenominator = 11;
//			else minWordMarginDenominator = 8;
//			int minWordMargin = (((lineBottom - lineTop) * 5) / minWordMarginDenominator); // TODO_above find out if this works, finally
			
			//	chop lines into words at spaces
//			int minWordMargin = (analysisDpi / 30); // TODO_ne ==> too low threshold, splits between 1's, etc.
//			int minWordMargin = (analysisDpi / 23); // 1 mm ... TODO_ne find out if this makes sense (will turn out in the long haul only, though)
//			int minWordMargin = (analysisDpi / 15); // TODO_ne ==> too high threshold
			ImagePartRectangle[] words = Imaging.splitIntoColumns(lineBounds, minWordMargin);
			wordCount += words.length;
			
			//	restore actual line top and bottom
			lineBounds.topRow = lineTop;
			lineBounds.bottomRow = lineBottom;
			lineBounds = Imaging.narrowTopAndBottom(lineBounds);
			if (lineBounds.isEmpty())
				continue;
			
			//	create line
			Line line = new Line(lineBounds);
			
			//	add words
			for (int w = 0; w < words.length; w++) {
				
				//	expand to actual line top and bottom
				words[w].topRow = lineTop;
				words[w].bottomRow = lineBottom;
				words[w] = Imaging.narrowTopAndBottom(words[w]);
				
				//	create word
				Word word = new Word(words[w]);
				
				//	store word
				line.addWord(word);
			}
			
			//	store line
			theBlock.addLine(line);
		}
		
		//	compute line baselines
		PageImageAnalysis.computeLineBaselines(theBlock, analysisDpi);
		
		//	correct words that extend below baseline more than average descending words (might have caught a stain)
		PageImageAnalysis.checkWordDescents(theBlock, analysisDpi);
//		
//		//	compute remaining line metrics, omitting font style though
//		PageImageAnalysis.computeFontMetrics(theBlock, analysisDpi, true);
		
		//	annotate lines and add bounding box attributes
		addTextBlockStructure(block, theBlock, analysisDpi, imageDpi, psm);
		
		//	assign line text
		ImRegion[] lines = block.getRegions(LINE_ANNOTATION_TYPE);
		for (int l = 0; l < Math.min(lines.length, blockTextLines.length); l++)
			lines[l].setAttribute(STRING_ATTRIBUTE, blockTextLines[l]);
		
		//	finally ...
		return wordCount;
	}
/*
DERIVATION OF WORD MARGIN FORMULA (independent of resolution, as all measures scale the same)

Times New Roman at 400 DPI (417%):
24 pt: line height 153, cap height 90, non-cap height 62, descent 26, space 36-42, 1-1 distance 32 ==> 35
18 pt: line height 115, cap height 67, non-cap height 46, descent 20, space 31-39, 1-1 distance 24 ==> 29
12 pt: line height  77, cap height 45, non-cap height 32, descent 13, space 20-27, 1-1 distance 15 ==> 19
10 pt: line height  64, cap height 37, non-cap height 25, descent 11, space 16-22, 1-1 distance 13 ==> 16
 8 pt: line height  52, cap height 30, non-cap height 20, descent  8, space 14-16, 1-1 distance 10 ==> 13
 6 pt: line height  38, cap height 22, non-cap height 15, descent  7, space 10-11, 1-1 distance 8 ==> 9

==> line text height with descent: space width = line text height / 3
==> line text height without descent: space width = line text height / 2.4 = (line text height * 5) / 12
==> line text height without caps: space width = line text height / 2.4 = (line text height * 5) / 11
==> line text height without descent and caps: space width = line text height / 1.6 = (line text height * 5) / 8
 */
	/**
	 * Analyze the inner structure of a text block, i.e., lines and words. The
	 * structure is stored in the argument block annotation, replacing its
	 * original content. If bounding boxes of words are specified, the analysis
	 * is vastly simplified.
	 * @param block the block to analyze
	 * @param pageImage the image of the page the block is on
	 * @param existingWords bounding boxes of existing words
	 * @param psm a monitor object for reporting progress, e.g. to a UI
	 * @return the number of individual words found in the block
	 * @throws IOException
	 */
	public static int fillInTextBlockStructure(ImRegion block, PageImage pageImage, BoundingBox[] existingWords, ProgressMonitor psm) throws IOException {
		return fillInTextBlockStructure(block, pageImage.image, pageImage.currentDpi, existingWords, psm);
	}
	
	/**
	 * Analyze the inner structure of a text block, i.e., lines and words. The
	 * structure is stored in the argument block annotation, replacing its
	 * original content. If bounding boxes of words are specified, the analysis
	 * is vastly simplified. All images wrapped for analysis via the
	 * Imaging.wrapImage() method are cached as with the key
	 * '&lt;imageHashCode&gt;-&lt;imageDpi&gt;'.
	 * @param block the block to analyze
	 * @param pageImage the image of the page the block is on
	 * @param imageDpi the resolution of the page image
	 * @param existingWords bounding boxes of existing words
	 * @param psm a monitor object for reporting progress, e.g. to a UI
	 * @return the number of individual words found in the block
	 * @throws IOException
	 */
	public static int fillInTextBlockStructure(ImRegion block, BufferedImage pageImage, int imageDpi, BoundingBox[] existingWords, ProgressMonitor psm) throws IOException {
		if (psm == null)
			psm = ProgressMonitor.dummy;
		
		//	scale down image for structure analysis if necessary (only if we don't have pre-existing words, though)
		AnalysisImage ai = Imaging.wrapImage(pageImage, (pageImage.hashCode() + "-" + imageDpi));
		int analysisDpi = imageDpi;
		while ((existingWords == null) && (analysisDpi > maxAnalysisDpi))
			analysisDpi /= 2;
		if (analysisDpi != imageDpi) {
			BufferedImage bi = ai.getImage();
			BufferedImage sbi = new BufferedImage(((bi.getWidth() * analysisDpi) / imageDpi), ((bi.getHeight() * analysisDpi) / imageDpi), ((bi.getType() == BufferedImage.TYPE_CUSTOM) ? BufferedImage.TYPE_BYTE_GRAY : bi.getType()));
			sbi.getGraphics().setColor(Color.WHITE);
			sbi.getGraphics().fillRect(0, 0, sbi.getWidth(), sbi.getHeight());
			sbi.getGraphics().drawImage(bi, 0, 0, sbi.getWidth(), sbi.getHeight(), null);
			ai = Imaging.wrapImage(sbi, (pageImage.hashCode() + "-" + analysisDpi));
			Imaging.whitenWhite(ai);
			psm.setInfo(" - image scaled to " + analysisDpi + " for structural analysis");
		}
		
		//	find text lines and words
		return fillInTextBlockStructure(block, ai, analysisDpi, imageDpi, psm, existingWords);
	}
	
	//	called only if block already existed in input document page
	private static int fillInTextBlockStructure(ImRegion block, AnalysisImage analysisImage, int analysisDpi, int imageDpi, ProgressMonitor psm, BoundingBox[] existingWords) {
		
		//	catch blocks filled in before
		if (block.getWords().length != 0)
			return 0;
		
		//	prepare block
		Block theBlock = new Block(getBounds(block, analysisImage, imageDpi, analysisDpi));
		
		//	find text lines and words
		PageImageAnalysis.getBlockStructure(theBlock, analysisDpi, existingWords, psm);
		
		//	adjust type
		if (theBlock.isTable())
			block.setType(TABLE_ANNOTATION_TYPE);
		
		//	append block content
		return addTextBlockStructure(block, theBlock, analysisDpi, imageDpi, psm);
	}
	
	//	called only if block structure not yet in input document page or block
	private static int addTextBlockStructure(ImRegion block, Block theBlock, int analysisDpi, int imageDpi, ProgressMonitor psm) {
		
		//	catch empty blocks
		if (theBlock.isEmpty())
			return 0;
		
		//	lines wrapped in table cells
		if (theBlock.isTable()) {
			
			//	add rows
			TableRow[] theRows = theBlock.getRows();
			for (int r = 0; r < theRows.length; r++) {
				
				//	mark row
				new ImRegion(block.getPage(), theRows[r].getBoundingBox(imageDpi / analysisDpi), TABLE_ROW_ANNOTATION_TYPE);
				
				//	add cells
				TableCell[] theCells = theRows[r].getCells();
				for (int t = 0; t < theCells.length; t++) {
					
					//	mark cell
					ImRegion cell = new ImRegion(block.getPage(), theCells[t].getBoundingBox(imageDpi / analysisDpi), TABLE_CELL_ANNOTATION_TYPE);
					
					//	set dimension attributes
					int colSpan = theCells[t].getColSpan();
					if (colSpan > 1)
						cell.setAttribute(COL_SPAN_ATTRIBUTE, ("" + colSpan));
					int rowSpan = theCells[t].getRowSpan();
					if (rowSpan > 1)
						cell.setAttribute(ROW_SPAN_ATTRIBUTE, ("" + rowSpan));
					
					//	add content
					Line[] lines = theCells[t].getLines();
					addLines(block, lines, analysisDpi, imageDpi, psm);
				}
			}
		}
		
		//	regular text lines
		else {
			Line[] theLines = theBlock.getLines();
			addLines(block, theLines, analysisDpi, imageDpi, psm);
		}
		
		//	finally ...
		return block.getWords().length;
	}
	
	private static void addLines(ImRegion region, Line[] lines, int analysisDpi, int imageDpi, ProgressMonitor psm) {
		for (int l = 0; l < lines.length; l++) {
			
			//	mark line
			ImRegion line = new ImRegion(region.getPage(), lines[l].getBoundingBox(imageDpi / analysisDpi), LINE_ANNOTATION_TYPE);
			
			Word[] words = lines[l].getWords();
			psm.setInfo(" - got " + words.length + " layout words");
			
			//	mark words, aggregating attributes along the way
			int fontSizeSum = 0;
			int fontSizeCount = 0;
			for (int w = 0; w < words.length; w++) {
				ImWord word = new ImWord(region.getPage(), words[w].getBoundingBox(imageDpi / analysisDpi));
				
				//	add baseline
				int baseline = words[w].getBaseline();
				if (0 < baseline)
//					word.setAttribute(BASELINE_ATTRIBUTE, ("" + (baseline * (imageDpi / analysisDpi))));
					word.setBaseline(baseline * (imageDpi / analysisDpi));
				
				//	set layout attributes
				if (words[w].isBold())
					word.setAttribute(BOLD_ATTRIBUTE, "true");
				if (words[w].isItalics())
					word.setAttribute(ITALICS_ATTRIBUTE, "true");
			}
			
			//	add aggregate attributes
			int baseline = lines[l].getBaseline();
			if (0 < baseline)
				line.setAttribute(BASELINE_ATTRIBUTE, ("" + (baseline * (imageDpi / analysisDpi))));
			if (0 < fontSizeSum) {
				int fontSize = ((fontSizeSum + (fontSizeCount / 2)) / fontSizeCount);
				if (0 < fontSize)
					line.setAttribute(FONT_SIZE_ATTRIBUTE, ("" + fontSize));
			}
			else {
				int fontSize = lines[l].getFontSize();
				if (0 < fontSize)
					line.setAttribute(FONT_SIZE_ATTRIBUTE, ("" + fontSize));
			}
		}
	}
	
	private static ImagePartRectangle getBounds(ImLayoutObject ilo, AnalysisImage ai, int imageDpi, int analysisDpi) {
		ImagePartRectangle bounds = new ImagePartRectangle(ai);
		bounds.leftCol = ((ilo.bounds.left * analysisDpi) / imageDpi);
		bounds.rightCol = ((ilo.bounds.right * analysisDpi) / imageDpi);
		bounds.topRow = ((ilo.bounds.top * analysisDpi) / imageDpi);
		bounds.bottomRow = ((ilo.bounds.bottom * analysisDpi) / imageDpi);
		return bounds;
	}
}
