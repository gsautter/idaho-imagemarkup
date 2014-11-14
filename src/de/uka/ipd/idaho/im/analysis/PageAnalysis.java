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
 *     * Neither the name of the Universität Karlsruhe (TH) nor the
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
package de.uka.ipd.idaho.im.analysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.TreeSet;

import de.uka.ipd.idaho.gamta.MutableAnnotation;
import de.uka.ipd.idaho.gamta.util.ProgressMonitor;
import de.uka.ipd.idaho.gamta.util.constants.TableConstants;
import de.uka.ipd.idaho.gamta.util.imaging.BoundingBox;
import de.uka.ipd.idaho.gamta.util.imaging.ImagingConstants;
import de.uka.ipd.idaho.im.ImLayoutObject;
import de.uka.ipd.idaho.im.ImRegion;

/**
 * Function library for analyzing the structure of text blocks in pages,
 * primarily paragraphs.
 * 
 * @author sautter
 */
public class PageAnalysis implements ImagingConstants, TableConstants {
	
	/**
	 * Group the lines inside a the blocks of a page into paragraphs. This
	 * method is for convenience, it loops through to the splitIntoPargaraphs()
	 * method that takes a single mutable annotations as an argument.
	 * @param blocks the blocks to split
	 * @param dpi the resolution of the page image the bounding boxes of the
	 *            argument annotations refer to
	 * @param psm an observer object monitoring progress
	 */
	public static void splitIntoParagraphs(ImRegion[] blocks, int dpi, ProgressMonitor psm) {
		for(int b = 0; b < blocks.length; b++)
			splitIntoParagraphs(blocks[b], dpi, psm);
	}
	
	/**
	 * Group the lines inside a the blocks of a page into paragraphs. This
	 * convenience method first calls splitIntoPargarphsShortLines(), then
	 * splitIntoParagraphsLineStart(), and finally
	 * splitIntoParagraphsLineDistance().
	 * @param block the block to split
	 * @param dpi the resolution of the page image the bounding boxes of the
	 *            argument annotation and its children refer to
	 * @param psm an observer object monitoring progress
	 */
	public static void splitIntoParagraphs(ImRegion block, int dpi, ProgressMonitor psm) {
		if (psm == null)
			psm = ProgressMonitor.dummy;
		
		//	test if we are dealing with a table
		if (TABLE_ANNOTATION_TYPE.equals(block.getType())) {
			psm.setInfo(" ==> the block is a table");
			return;
		}
		
		//	get lines
		ImRegion[] lines = getParagraphLayoutLines(block);
		psm.setInfo(" - found " + lines.length + " relevant lines in block");
		
		//	perform split
		splitIntoParagraphsShortLine(block, dpi, psm, lines);
		splitIntoParagraphsLineStart(block, dpi, psm, lines, computeAverageHeight(lines));
		//	TODOne figure out which method is better
		//	==> line distance seems to be more reliable
//		splitIntoParagraphsLineMargin(block, dpi, psm, lines);
		splitIntoParagraphsLineDistance(block, dpi, psm, lines);
		
		//	compute paragraph bounding boxes and compute indentation
		wrapAroundChildren(block, MutableAnnotation.PARAGRAPH_TYPE, LINE_ANNOTATION_TYPE);
		computeBlockLayout(block, dpi);
		
		//	compute font size of paragraphs
		computeParagraphFontSize(block);
	}
	
	/**
	 * Compute the layout of entire text columns. This method is a convenience
	 * method invoking computeColumnLayout() for every single element of the
	 * argument array.
	 * @param columns the columns to analyze
	 * @param dpi the resolution of the page image the bounding boxes of the
	 *            argument annotations and their children refer to
	 */
	public static final void computeColumnLayout(ImRegion[] columns, int dpi) {
		for (int c = 0; c < columns.length; c++)
			computeColumnLayout(columns[c], dpi);
	}
	
	/**
	 * Compute the layout of an entire text column. This method first computes
	 * the layout of each single block contained in the column, and then
	 * extrapolates this layout to other blocks and paragraphs whose layout is
	 * ambiguous when looked at in isolation.
	 * @param column the column to analyze
	 * @param dpi the resolution of the page image the bounding boxes of the
	 *            argument annotation and its children refer to
	 */
	public static final void computeColumnLayout(ImRegion column, int dpi) {
		ImRegion[] blocks = column.getRegions(BLOCK_ANNOTATION_TYPE);
		if (blocks.length == 0)
			return;
		
		//	compute layout of individual blocks, and index paragraph-to-block relations
		Properties paragraphsToBlocks = new Properties();
		for (int b = 0; b < blocks.length; b++) {
			computeBlockLayout(blocks[b], dpi);
			ImRegion[] paragraphs = blocks[b].getRegions(MutableAnnotation.PARAGRAPH_TYPE);
			for (int p = 0; p < paragraphs.length; p++)
				paragraphsToBlocks.setProperty(paragraphs[p].bounds.toString(), blocks[b].bounds.toString());
		}
		
		//	compute average line height
		int avgLineHeigth = computeAverageLineHeight(column);
		if (avgLineHeigth != -1)
			column.setAttribute(LINE_HEIGHT_ATTRIBUTE, ("" + avgLineHeigth));
		
		//	get paragraphs
		ImRegion[] paragraphs = column.getRegions(MutableAnnotation.PARAGRAPH_TYPE);
		if (paragraphs.length < 2)
			return;
		
		//	get indentation (number of pixels) for each left-oriented or justified paragraph, measured against column boundary, and collect paragraphs with yet unknown layout
		int exdentCount = 0;
		int plainCount = 0;
		int plainDistSum = 0;
		int indentCount = 0;
		int indentDistSum = 0;
		
		int textOrientationCount = 0;
		int leftCount = 0;
		int justifiedCount = 0;
		HashSet withoutLayout = new HashSet();
		for (int p = 0; p < paragraphs.length; p++) {
			if (TEXT_ORIENTATION_JUSTIFIED.equals(paragraphs[p].getAttribute(TEXT_ORIENTATION_ATTRIBUTE))) {
				textOrientationCount++;
				justifiedCount++;
			}
			else if (TEXT_ORIENTATION_LEFT.equals(paragraphs[p].getAttribute(TEXT_ORIENTATION_ATTRIBUTE))) {
				textOrientationCount++;
				leftCount++;
			}
			else {
				if (paragraphs[p].hasAttribute(TEXT_ORIENTATION_ATTRIBUTE))
					textOrientationCount++;
				else withoutLayout.add(paragraphs[p].bounds.toString());
				continue;
			}
			if (!paragraphs[p].hasAttribute(INDENTATION_ATTRIBUTE))
				continue;
			
			ImRegion[] lines = getParagraphLayoutLines(paragraphs[p]);
			if ((lines == null) || (lines.length == 0))
				continue;
			int fLineDist = (lines[0].bounds.left - column.bounds.left);
			if (INDENTATION_EXDENT.equals(paragraphs[p].getAttribute(INDENTATION_ATTRIBUTE))) {
				exdentCount++;
				plainDistSum += fLineDist;
			}
			else if (INDENTATION_INDENT.equals(paragraphs[p].getAttribute(INDENTATION_ATTRIBUTE))) {
				indentCount++;
				indentDistSum += fLineDist;
			}
			else if (INDENTATION_NONE.equals(paragraphs[p].getAttribute(INDENTATION_ATTRIBUTE)) && !TEXT_ORIENTATION_CENTERED.equals(paragraphs[p].getAttribute(TEXT_ORIENTATION_ATTRIBUTE))) {
				plainCount++;
				plainDistSum += fLineDist;
			}
		}
		
		//	anything to take care of?
		if (withoutLayout.isEmpty())
			return;
		
		//	compute averages and predominant text orientation
		int minSignificantDifference = (dpi / 20); // little more than one millimeter
		int avgPlainDist = (((plainCount + exdentCount) == 0) ? Integer.MAX_VALUE : ((plainDistSum + ((plainCount + exdentCount) / 2)) / (plainCount + exdentCount)));
		int avgIndentDist = ((indentCount == 0) ? Integer.MAX_VALUE : ((indentDistSum + (indentCount / 2)) / indentCount));
		String predominantTextOrientation = null;
		if ((leftCount * 2) > textOrientationCount)
			predominantTextOrientation = TEXT_ORIENTATION_LEFT;
		else if ((justifiedCount * 2) > textOrientationCount)
			predominantTextOrientation = TEXT_ORIENTATION_JUSTIFIED;
		
		//	measure single line paragraphs against column and draw conclusion 
		for (int p = 0; p < paragraphs.length; p++) {
			if (!withoutLayout.contains(paragraphs[p].bounds.toString()))
				continue;
			ImRegion[] lines = getParagraphLayoutLines(paragraphs[p]);
			if ((lines == null) || (lines.length == 0))
				continue;
			int fLineDist = (lines[0].bounds.left - column.bounds.left);
			
			//	plain at left edge
			if (Math.abs(fLineDist - avgPlainDist) < minSignificantDifference) {
				
				//	figure out indentation looking above and below
				String indentationAbove = ((p == 0) ? null : ((String) paragraphs[p-1].getAttribute(INDENTATION_ATTRIBUTE)));
				String indentationBelow = (((p+1) == paragraphs.length) ? null : ((String) paragraphs[p+1].getAttribute(INDENTATION_ATTRIBUTE)));
				String indentation;
				if ((indentationAbove == null) && (indentationBelow == null))
					indentation = ((exdentCount < plainCount) ? INDENTATION_NONE : INDENTATION_EXDENT);
				else if (indentationAbove == null)
					indentation = indentationBelow;
				else if (indentationBelow == null)
					indentation = indentationAbove;
				else if (indentationAbove.equals(indentationBelow))
					indentation = indentationAbove;
				else {
					String blockIdAbove = paragraphsToBlocks.getProperty(paragraphs[p-1].bounds.toString());
					String blockId = paragraphsToBlocks.getProperty(paragraphs[p].bounds.toString());
					String blockIdBelow = paragraphsToBlocks.getProperty(paragraphs[p+1].bounds.toString());
					if (blockIdAbove.equals(blockIdBelow))
						indentation = ((exdentCount < plainCount) ? INDENTATION_NONE : INDENTATION_EXDENT);
					else if (blockId.equals(blockIdAbove))
						indentation = indentationAbove;
					else if (blockId.equals(blockIdBelow))
						indentation = indentationBelow;
					else indentation = ((exdentCount < plainCount) ? INDENTATION_NONE : INDENTATION_EXDENT);
				}
				paragraphs[p].setAttribute(INDENTATION_ATTRIBUTE, indentation);
				
				//	figure out text orientation looking above and below
				String textOrientationAbove = ((p == 0) ? null : ((String) paragraphs[p-1].getAttribute(TEXT_ORIENTATION_ATTRIBUTE)));
				String textOrientationBelow = (((p+1) == paragraphs.length) ? null : ((String) paragraphs[p+1].getAttribute(TEXT_ORIENTATION_ATTRIBUTE)));
				String textOrientation;
				if ((textOrientationAbove == null) && (textOrientationBelow == null))
					textOrientation = predominantTextOrientation;
				else if (textOrientationAbove == null)
					textOrientation = textOrientationBelow;
				else if (textOrientationBelow == null)
					textOrientation = textOrientationAbove;
				else if (textOrientationAbove.equals(textOrientationBelow))
					textOrientation = textOrientationAbove;
				else {
					String blockIdAbove = paragraphsToBlocks.getProperty(paragraphs[p-1].bounds.toString());
					String blockId = paragraphsToBlocks.getProperty(paragraphs[p].bounds.toString());
					String blockIdBelow = paragraphsToBlocks.getProperty(paragraphs[p+1].bounds.toString());
					if (blockIdAbove.equals(blockIdBelow))
						textOrientation = predominantTextOrientation;
					else if (blockId.equals(blockIdAbove))
						textOrientation = textOrientationAbove;
					else if (blockId.equals(blockIdBelow))
						textOrientation = textOrientationBelow;
					else textOrientation = predominantTextOrientation;
				}
				if (textOrientation != null)
					paragraphs[p].setAttribute(TEXT_ORIENTATION_ATTRIBUTE, textOrientation);
			}
			
			//	regular indent
			else if (Math.abs(fLineDist - avgIndentDist) < minSignificantDifference) {
				paragraphs[p].setAttribute(INDENTATION_ATTRIBUTE, INDENTATION_INDENT);
				if (predominantTextOrientation != null)
					paragraphs[p].setAttribute(TEXT_ORIENTATION_ATTRIBUTE, predominantTextOrientation);
			}
			
			//	some other orientation, resort to centered
			else {
				paragraphs[p].setAttribute(INDENTATION_ATTRIBUTE, INDENTATION_NONE);
				paragraphs[p].setAttribute(TEXT_ORIENTATION_ATTRIBUTE, TEXT_ORIENTATION_CENTERED);
			}
			
			//	mark paragraph for debugging
			paragraphs[p].setAttribute("_layoutExtrapolated", "true");
		}
	}
	
	private static final void computeBlockLayout(ImRegion block, int dpi) {
		ImRegion[] paragraphs = block.getRegions(MutableAnnotation.PARAGRAPH_TYPE);
		if (paragraphs.length == 0)
			return;
		
		//	compute indentation of individual paragraphs
		for (int p = 0; p < paragraphs.length; p++)
			computeParagraphLayout(paragraphs[p], dpi);
		
		//	compute average line height
		int avgLineHeigth = computeAverageLineHeight(block);
		if (avgLineHeigth != -1)
			block.setAttribute(LINE_HEIGHT_ATTRIBUTE, ("" + avgLineHeigth));
		
		//	at most one paragaraph, no comparison or transfer required
		if (paragraphs.length == 1) {
			if (!INDENTATION_NONE.equals(paragraphs[0].getAttribute(INDENTATION_ATTRIBUTE, INDENTATION_NONE)))
				block.setAttribute(INDENTATION_ATTRIBUTE, paragraphs[0].getAttribute(INDENTATION_ATTRIBUTE));
			return;
		}
		
		//	find paragraph indentation of block (skip first paragraph, might be continuing from earlier block, and thus misleading)
		TreeSet blockIndentations = new TreeSet();
		String indentation = null;
		boolean indentationComplete = true;
		for (int p = 1; p < paragraphs.length; p++) {
			indentation = ((String) paragraphs[p].getAttribute(INDENTATION_ATTRIBUTE));
			if (indentation == null)
				indentationComplete = false;
			else blockIndentations.add(indentation);
		}
		
		//	indentation ambiguous throughout block
		if (blockIndentations.size() != 1) {
			if (!blockIndentations.isEmpty())
				block.setAttribute(INDENTATION_ATTRIBUTE, INDENTATION_MIXED);
			return;
		}
		
		//	mark block indentation
		block.setAttribute(INDENTATION_ATTRIBUTE, blockIndentations.first());
		
		//	indentation complete
		if (indentationComplete)
			return;
		
		//	mark paragraph indentation of block (skip first paragraph, might be continuing from earlier block and thus behave differently on purpose)
		for (int p = 1; p < paragraphs.length; p++) {
			if (!paragraphs[p].hasAttribute(INDENTATION_ATTRIBUTE))
				paragraphs[p].setAttribute(INDENTATION_ATTRIBUTE, blockIndentations.first());
		}
	}
	
	private static void computeParagraphFontSize(ImRegion block) {
		ImRegion[] paragraphs = block.getRegions(MutableAnnotation.PARAGRAPH_TYPE);
		for (int p = 0; p < paragraphs.length; p++) {
			ImRegion[] lines = getParagraphLayoutLines(paragraphs[p]);
			int fontSizeSum = 0;
			int fontSizeCount = 0;
			for (int l = 0; l < lines.length; l++) {
				String lineFontSizeString = ((String) lines[l].getAttribute(FONT_SIZE_ATTRIBUTE));
				if (lineFontSizeString == null)
					continue;
				fontSizeSum += Integer.parseInt(lineFontSizeString);
				fontSizeCount++;
			}
			if (fontSizeCount == 0)
				continue;
			int fontSize = ((fontSizeSum + (fontSizeCount / 2)) / fontSizeCount);
			paragraphs[p].setAttribute(FONT_SIZE_ATTRIBUTE, ("" + fontSize));
			for (int l = 0; l < lines.length; l++) {
				if (!lines[l].hasAttribute(FONT_SIZE_ATTRIBUTE))
					lines[l].setAttribute(FONT_SIZE_ATTRIBUTE, ("" + fontSize));
			}
		}
	}
	
	private static final void computeParagraphLayout(ImRegion paragraph, int dpi) {
		ImRegion[] lines = getParagraphLayoutLines(paragraph);
		
		//	got no basis for decisions
		if (lines.length == 0)
			return;
		
		//	compute metrics, excluding lines to the right of other lines
		int minLineStart = paragraph.bounds.right;
		int maxLineStart = 0;
		int nfLineStartSum = 0;
		int minLineEnd = paragraph.bounds.right;
		int maxLineEnd = 0;
		int nlLineEndSum = 0;
		for (int l = 0; l < lines.length; l++) {
			minLineStart = Math.min(minLineStart, (lines[l].bounds.left - paragraph.bounds.left));
			maxLineStart = Math.max(maxLineStart, (lines[l].bounds.left - paragraph.bounds.left));
			if (l != 0)
				nfLineStartSum += (lines[l].bounds.left - paragraph.bounds.left);
			minLineEnd = Math.min(minLineEnd, (paragraph.bounds.right - lines[l].bounds.right));
			maxLineEnd = Math.max(maxLineEnd, (paragraph.bounds.right - lines[l].bounds.right));
			if (l != 0)
				nlLineEndSum += (paragraph.bounds.right - lines[l-1].bounds.right);
		}
		
		//	store line heigth
		int avgLineHeigth = computeAverageHeight(lines);
		if (avgLineHeigth != -1)
			paragraph.setAttribute(LINE_HEIGHT_ATTRIBUTE, ("" + avgLineHeigth));
		
		//	not enough data for local decision on indentation
		if (lines.length < 2)
			return;
		
		//	compute significance threshold
		int minSignificantDifference = (dpi / 20); // little more than one millimeter
		int fLineStart = (lines[0].bounds.left - paragraph.bounds.left);
		
		//	line is centered, no indentation
		if (
				(maxLineStart != fLineStart)
				&&
				(minSignificantDifference < (maxLineStart - minLineStart))
				&&
				(minSignificantDifference < (maxLineEnd - minLineEnd))
				&&
				(Math.abs(minLineStart - minLineEnd) < minSignificantDifference)
				&&
				(Math.abs(maxLineStart - maxLineEnd) < minSignificantDifference)
			) {
			paragraph.setAttribute(INDENTATION_ATTRIBUTE, INDENTATION_NONE);
			paragraph.setAttribute(TEXT_ORIENTATION_ATTRIBUTE, TEXT_ORIENTATION_CENTERED);
			return;
		}
		
		//	compute text orientation
		int normLeft;
		
		//	no significant indent or exdent
		if ((maxLineStart - minLineStart) < minSignificantDifference) {
			paragraph.setAttribute(INDENTATION_ATTRIBUTE, INDENTATION_NONE);
			normLeft = 0;
		}
		
		//	indent or exdent
		else {
			
			//	compute line start averages
			int avgNfLineStart = (nfLineStartSum / (lines.length-1));
			
			//	set attribute
			paragraph.setAttribute(INDENTATION_ATTRIBUTE, ((fLineStart < avgNfLineStart) ? INDENTATION_EXDENT : INDENTATION_INDENT));
			normLeft = ((fLineStart < avgNfLineStart) ? maxLineStart : minLineStart);
		}
		
		//	compute line start averages
		int avgNfLineStart = (nfLineStartSum / (lines.length-1));
		int avgNlLineEnd = (nlLineEndSum / (lines.length-1));
		
		//	compute left and right justification
		boolean leftJustified = ((avgNfLineStart - normLeft) < minSignificantDifference);
		boolean rigthJustified = (avgNlLineEnd < minSignificantDifference);
		
		//	finally, set text orientation
		if (leftJustified && rigthJustified)
			paragraph.setAttribute(TEXT_ORIENTATION_ATTRIBUTE, TEXT_ORIENTATION_JUSTIFIED);
		else if (leftJustified)
			paragraph.setAttribute(TEXT_ORIENTATION_ATTRIBUTE, TEXT_ORIENTATION_LEFT);
		else if (rigthJustified)
			paragraph.setAttribute(TEXT_ORIENTATION_ATTRIBUTE, TEXT_ORIENTATION_RIGHT);
	}
	
	private static int computeAverageLineHeight(ImRegion region) {
		ImRegion[] lines = getParagraphLayoutLines(region);
		if (lines == null)
			return -1;
		return computeAverageHeight(lines);
	}
	
	private static ImRegion[] getParagraphLayoutLines(ImRegion blockOrParagraph) {
		
		//	get lines (have to use extra array, as getAnnotations() actually returns QueriableAnnotation[], which incurs exceptions on merge)
		ImRegion[] lines = blockOrParagraph.getRegions(LINE_ANNOTATION_TYPE);
		if (lines.length == 0)
			return lines;
		
		//	merge side-by-side lines for now
		ArrayList lineList = new ArrayList();
		for (int l = 1; l < lines.length; l++) {
			if ((lines[l].bounds.top < lines[l-1].bounds.bottom) && (lines[l-1].bounds.top < lines[l].bounds.bottom) && (lines[l-1].bounds.right < lines[l].bounds.left)) {
				BoundingBox[] lineBoxesToMerge = {lines[l-1].bounds, lines[l].bounds};
				BoundingBox mergeLineBox = BoundingBox.aggregate(lineBoxesToMerge);
				ImRegion mergeLine = new ImRegion(blockOrParagraph.getPage(), mergeLineBox, LINE_ANNOTATION_TYPE);
				try {
					int leftBaseline = Integer.parseInt((String) lines[l-1].getAttribute(BASELINE_ATTRIBUTE, "-1"));
					int rightBaseline = Integer.parseInt((String) lines[l].getAttribute(BASELINE_ATTRIBUTE, "-1"));
					if ((0 < leftBaseline) && (0 < rightBaseline))
						mergeLine.setAttribute(BASELINE_ATTRIBUTE, ("" + ((leftBaseline + rightBaseline) / 2)));
				} catch (Exception e) {}
				try {
					int leftFontSize = Integer.parseInt((String) lines[l-1].getAttribute(FONT_SIZE_ATTRIBUTE, "-1"));
					int rightFontSize = Integer.parseInt((String) lines[l].getAttribute(FONT_SIZE_ATTRIBUTE, "-1"));
					if ((0 < leftFontSize) && (0 < rightFontSize))
						mergeLine.setAttribute(FONT_SIZE_ATTRIBUTE, ("" + ((leftFontSize + rightFontSize) / 2)));
				} catch (Exception e) {}
				blockOrParagraph.getPage().removeRegion(lines[l-1]);
				blockOrParagraph.getPage().removeRegion(lines[l]);
				lines[l-1] = null;
				lines[l] = mergeLine;
			}
			else lineList.add(lines[l-1]);
		}
		lineList.add(lines[lines.length-1]);
		if (lineList.size() < lines.length)
			lines = ((ImRegion[]) lineList.toArray(new ImRegion[lineList.size()]));
		lineList.clear();
		
		//	compute average line height
		int avgLineHeight = computeAverageHeight(lines);
		
		//	sort out lines lower than half the block average (misguided accents, etc. of upper case diacritics)
		lineList.add(lines[0]);
		for (int l = 1; l < lines.length; l++) {
			if ((lines[l].bounds.bottom - lines[l].bounds.top) < (avgLineHeight / 2))
				blockOrParagraph.getPage().removeRegion(lines[l]);
			else lineList.add(lines[l]);
		}
		if (lineList.size() < lines.length)
			lines = ((ImRegion[]) lineList.toArray(new ImRegion[lineList.size()]));
		
		return lines;
	}
	
//	private static BoundingBox[] getBoundingBoxes(Annotation[] annots) {
//		BoundingBox[] boxes = new BoundingBox[annots.length];
//		for (int a = 0; a < annots.length; a++) {
//			boxes[a] = BoundingBox.getBoundingBox(annots[a]);
//			if (boxes[a] == null)
//				return null;
//		}
//		return boxes;
//	}
//	
	private static int computeAverageHeight(ImRegion[] regions) {
		if (regions.length == 0)
			return -1;
		int heightSum = 0;
		for (int r = 0; r < regions.length; r++)
			heightSum += (regions[r].bounds.bottom - regions[r].bounds.top);
		return (heightSum / regions.length);
	}
	
	/**
	 * Split a block into paragraphs at short (far-left ending) inner lines.
	 * @param block the block to split
	 * @param dpi the resolution of the page image the bounding boxes of the
	 *            argument annotation and its children refer to
	 * @param psm an observer object monitoring progress
	 */
	public static void splitIntoParagraphsShortLine(ImRegion block, int dpi, ProgressMonitor psm) {
		
		//	get lines
		ImRegion[] lines = getParagraphLayoutLines(block);
		psm.setInfo(" - found " + lines.length + " relevant lines in block");
		
		//	nothing to do at all
		if (lines.length == 0) {
			psm.setInfo(" ==> no lines in block, nothing to split");
			return;
		}
		
		//	catch single-line paragraphs
		if (lines.length == 1) {
			ImRegion paragraph = new ImRegion(block.getPage(), lines[0].bounds, MutableAnnotation.PARAGRAPH_TYPE);
			psm.setInfo(" ==> only one line in block, nothing to split");
			psm.setInfo(" ==> created single line paragraph at " + paragraph.bounds.toString());
			return;
		}
		
		//	perform split
		splitIntoParagraphsShortLine(block, dpi, psm, lines);
	}
	
	//	if we get here, we have sufficient lines, all with bounding boxes, and a non-null status monitor
	private static void splitIntoParagraphsShortLine(ImRegion block, int dpi, ProgressMonitor psm, ImRegion[] lines) {
		
		//	compute average line height
		int avgLineHeight = computeAverageHeight(lines);
		if (avgLineHeight < 1)
			return;
		
		//	compute minimum, maximum, and average line end
		int minLineEnd = block.bounds.right;
		int maxLineEnd = 0;
		int lineEndSum = 0;
		for (int l = 0; l < lines.length; l++) {
			minLineEnd = Math.min(minLineEnd, (lines[l].bounds.right - block.bounds.left));
			maxLineEnd = Math.max(maxLineEnd, (lines[l].bounds.right - block.bounds.left));
			lineEndSum += (lines[l].bounds.right - block.bounds.left);
		}
		int avgLineEnd = (lineEndSum / lines.length);
		psm.setInfo(" - minimum line end is at " + minLineEnd + ", maximum at " + maxLineEnd + ", average at " + avgLineEnd + ", average line height is " + avgLineHeight);
		
		//	no significant difference
		int minSignificantDifference = (dpi / 20); // a little more than one millimeter
		if ((maxLineEnd - minLineEnd) < minSignificantDifference) {
			ImRegion paragraph = new ImRegion(block.getPage(), block.bounds, MutableAnnotation.PARAGRAPH_TYPE);
			psm.setInfo(" ==> line ends vary only by " + (maxLineEnd - minLineEnd) + ", short line split too unreliable");
			psm.setInfo(" ==> created single paragraph at " + paragraph.bounds.toString());
			return;
		}
		
		//	perform short (far-left ending) line split (highly reliable)
		int pStartLineIndex = 0;
		for (int l = 1; l < lines.length; l++) {
			
			//	skip over lines lower than half the block average (some wider ones might be left)
			if ((lines[l-1].bounds.bottom - lines[l-1].bounds.top) < (avgLineHeight / 2))
				continue;
			
			//	skip over empty lines
			if (lines[l-1].getWords().length == 0)
				continue;
			
			//	if above line ends far to the left, start new paragraph
			psm.setInfo(" - previous line end is " + (lines[l-1].bounds.right - block.bounds.left));
			if (minSignificantDifference < (avgLineEnd - (lines[l-1].bounds.right - block.bounds.left))) {
				ImRegion paragraph = new ImRegion(block.getPage(), ImLayoutObject.getAggregateBox(lines, pStartLineIndex, l), MutableAnnotation.PARAGRAPH_TYPE);
				pStartLineIndex = l;
				psm.setInfo(" - created paragraph at " + paragraph.bounds.toString());
			}
		}
		if (pStartLineIndex < lines.length) {
			ImRegion paragraph = new ImRegion(block.getPage(), ImLayoutObject.getAggregateBox(lines, pStartLineIndex, lines.length), MutableAnnotation.PARAGRAPH_TYPE);
			psm.setInfo(" - created final paragraph at " + paragraph.bounds.toString());
		}
		
		//	compute paragraph bounding boxes and compute indentation
		wrapAroundChildren(block, MutableAnnotation.PARAGRAPH_TYPE, LINE_ANNOTATION_TYPE);
		computeBlockLayout(block, dpi);
		
		//	compute font size of paragraphs
		computeParagraphFontSize(block);
	}
	
	private static ImRegion[][] getParagraphLines(ImRegion[] paragraphs, ImRegion[] lines) {
		ArrayList[] paragraphLineLists = new ArrayList[paragraphs.length];
		for (int p = 0; p < paragraphs.length; p++) {
			paragraphLineLists[p] = new ArrayList();
			for (int l = 0; l < lines.length; l++) {
				if (paragraphs[p].bounds.includes(lines[l].bounds, false))
					paragraphLineLists[p].add(lines[l]);
			}
		}
		ImRegion[][] paragraphLines = new ImRegion[paragraphs.length][];
		for (int p = 0; p < paragraphs.length; p++)
			paragraphLines[p] = ((ImRegion[]) paragraphLineLists[p].toArray(new ImRegion[paragraphLineLists[p].size()]));
		return paragraphLines;
	}
	
	/**
	 * Split the paragraphs in a block into smaller paragraphs at indented or
	 * outdented inner lines, whatever is predominant at the start of existing
	 * paragraphs.
	 * @param block the block to split
	 * @param dpi the resolution of the page image the bounding boxes of the
	 *            argument annotation and its children refer to
	 * @param psm an observer object monitoring progress
	 */
	public static void splitIntoParagraphsLineStart(ImRegion block, int dpi, ProgressMonitor psm) {
		if (psm == null)
			psm = ProgressMonitor.dummy;
		
		//	get lines
		ImRegion[] lines = getParagraphLayoutLines(block);
		psm.setInfo(" - found " + lines.length + " relevant lines in block");
		
		//	perform split
		splitIntoParagraphsLineStart(block, dpi, psm, lines, computeAverageLineHeight(block));
	}
	
	//	if we get here, we have sufficient lines, all with bounding boxes, and a non-null status monitor
	private static void splitIntoParagraphsLineStart(ImRegion block, int dpi, ProgressMonitor psm, ImRegion[] lines, int avgLineHeight) {
		
		//	try to further split first generation of paragraphs at indented/exdented lines
		ImRegion[] paragraphs = block.getRegions(MutableAnnotation.PARAGRAPH_TYPE);
//		
//		//	too few paragraphs for start analysis, however
//		//	even for single paragraphs, we can detect indent or exdent based on frequency
//		if (paragraphs.length < 3) {
//			psm.setInfo(" ==> got " + paragraphs.length + " paragraphs, indentation splitting too unreliable");
//			return;
//		}
		
		//	group lines by paragraphs
		ImRegion[][] paragraphLines = getParagraphLines(paragraphs, lines);
		
		//	compute minimum, maximum, and average start of all lines, and lines at paragraph starts so far
		int minLineStart = block.bounds.right;
		int maxLineStart = 0;
		int lineStartCount = 0;
		int lineStartSum = 0;
		int fLineStartCount = 0;
		int fLineStartSum = 0;
		for (int p = 0; p < paragraphLines.length; p++) {
			for (int l = 0; l < paragraphLines[p].length; l++) {
				
				//	skip over lines lower than half the block average (some wider ones might be left)
				if ((paragraphLines[p][l].bounds.bottom - paragraphLines[p][l].bounds.top) < (avgLineHeight / 2))
					continue;
				
				//	skip over lines that start are indented more than one third of block width (likely artifacts)
				if (((paragraphLines[p][l].bounds.left - block.bounds.left) * 3) > (block.bounds.right - block.bounds.left))
					continue;
				
				//	count line
				lineStartCount++;
				lineStartSum += (paragraphLines[p][l].bounds.left - block.bounds.left);
				minLineStart = Math.min(minLineStart, (paragraphLines[p][l].bounds.left - block.bounds.left));
				maxLineStart = Math.max(maxLineStart, (paragraphLines[p][l].bounds.left - block.bounds.left));
				if (l == 0) {
					fLineStartCount++;
					fLineStartSum += (paragraphLines[p][l].bounds.left - block.bounds.left);
				}
			}
		}
		
		//	no significant indent or exdent
		int minSignificantDifference = (dpi / 20); // little more than one millimeter
		if ((maxLineStart - minLineStart) < minSignificantDifference) {
			psm.setInfo(" ==> line starts vary only by " + (maxLineStart - minLineStart) + ", line start split too unreliable");
			return;
		}
		
		//	compute averages
		int avgLineStart = ((lineStartCount == 0) ? 0 : (lineStartSum / lineStartCount));
		int avgFLineStart = ((fLineStartCount == 0) ? 0 : (fLineStartSum / fLineStartCount));
		psm.setInfo(" - average line start is at " + avgLineStart + ", average paragraph first line start is " + avgFLineStart);
		
		//	group line start
		int minLineStartCount = 0;
		int minLineStartCounts[] = new int[paragraphs.length];
		Arrays.fill(minLineStartCounts, 0);
		int maxLineStartCount = 0;
		int maxLineStartCounts[] = new int[paragraphs.length];
		Arrays.fill(maxLineStartCounts, 0);
		for (int p = 0; p < paragraphLines.length; p++) {
			for (int l = 0; l < paragraphLines[p].length; l++) {
				
				//	skip over lines lower than half the block average (some wider ones might be left)
				if ((paragraphLines[p][l].bounds.bottom - paragraphLines[p][l].bounds.top) < (avgLineHeight / 2))
					continue;
				
				//	sort into group
				int minLsd = Math.abs((paragraphLines[p][l].bounds.left - block.bounds.left) - minLineStart);
				int maxLsd = Math.abs((paragraphLines[p][l].bounds.left - block.bounds.left) - maxLineStart);
				if (maxLsd < minLsd) {
					maxLineStartCount++;
					maxLineStartCounts[p]++;
				}
				else {
					minLineStartCount++;
					minLineStartCounts[p]++;
				}
			}
		}
		psm.setInfo(" - got " + minLineStartCount + " lefter line starts, " + maxLineStartCount + " righter line starts");
		
		//	evaluate frequencies TODO figure out if line count thresholds make sense
		boolean[] splitExdent = new boolean[paragraphs.length];
		boolean[] splitIndent = new boolean[paragraphs.length];
		for (int p = 0; p < paragraphs.length; p++) {
			
			//	three or fewer lines in paragraph, too insecure
			if (paragraphLines[p].length < 4) {
				splitExdent[p] = false;
				splitIndent[p] = false;
			}
			
			//	nine or fewer lines in paragraph, use conservative estimates
			else if (paragraphLines[p].length < 10) {
				splitExdent[p] = ((minLineStartCount * 2) <= maxLineStartCount);
				splitIndent[p] = ((maxLineStartCount * 2) <= minLineStartCount);
			}
			
			//	ten or more lines paragraph, use more aggressive estimates
			else {
				splitExdent[p] = ((minLineStartCount * 3) <= (maxLineStartCount * 2));
				splitIndent[p] = ((maxLineStartCount * 3) <= (minLineStartCount * 2));
			}
		}
		
		//	split preliminary paragraphs at indented or exdented lines
		for (int p = 0; p < paragraphs.length; p++) {
			int spStartLineIndex = 0;
			psm.setInfo(" - investigating preliminary paragraph " + p + " with " + paragraphLines[p].length + " lines, paragraph is " + paragraphs[p].bounds.toString());
			for (int l = 1; l < paragraphLines[p].length; l++) {
				
				//	investigate line start
				psm.setInfo(" - paragraph line start is " + (paragraphLines[p][l].bounds.left - block.bounds.left));
				
				//	evaluate line start based on whatever we have
				boolean split = false;
				
				//	even for single paragraphs, we can detect indent or exdent based on frequency
				if (paragraphs.length < 3) {
					int minLsd = Math.abs((paragraphLines[p][l].bounds.left - block.bounds.left) - minLineStart);
					int maxLsd = Math.abs((paragraphLines[p][l].bounds.left - block.bounds.left) - maxLineStart);
					if (maxLsd < minLsd) {
						split = splitIndent[p];
						if (split)
							psm.setInfo(" - splitting for indent with min distance " + minLsd + ", max distance " + maxLsd);
					}
					else {
						split = splitExdent[p];
						if (split)
							psm.setInfo(" - splitting for exdent with min distance " + minLsd + ", max distance " + maxLsd);
					}
				}
				
				//	do paragraph start analysis
				else {
					int flsd = Math.abs((paragraphLines[p][l].bounds.left - block.bounds.left) - avgFLineStart);
					int lsd = Math.abs((paragraphLines[p][l].bounds.left - block.bounds.left) - avgLineStart);
					split = (lsd > flsd);
					if (split)
						psm.setInfo(" - splitting for first line start local distance " + lsd + ", global distance " + flsd);
				}
				
				//	this one looks like a paragraph start, perform split
				if (split) {
					ImRegion splitParagraph = new ImRegion(block.getPage(), ImLayoutObject.getAggregateBox(paragraphLines[p], spStartLineIndex, l), MutableAnnotation.PARAGRAPH_TYPE);
					spStartLineIndex = l;
					psm.setInfo(" - created sub paragraph at " + splitParagraph.bounds.toString());
				}
			}
			
			//	we've split something, mark last part, and remove original paragraph
			if (spStartLineIndex != 0) {
				ImRegion splitParagraph = new ImRegion(block.getPage(), ImLayoutObject.getAggregateBox(paragraphLines[p], spStartLineIndex, paragraphLines[p].length), MutableAnnotation.PARAGRAPH_TYPE);
				psm.setInfo(" - created final sub paragraph at " + splitParagraph.bounds.toString());
				block.getPage().removeRegion(paragraphs[p]);
			}
		}
		
		//	compute paragraph bounding boxes and compute indentation
		wrapAroundChildren(block, MutableAnnotation.PARAGRAPH_TYPE, LINE_ANNOTATION_TYPE);
		computeBlockLayout(block, dpi);
		
		//	compute font size of paragraphs
		computeParagraphFontSize(block);
	}
	
	/**
	 * Split the paragraphs in a block into smaller paragraphs at wide inner
	 * horizontal margins.
	 * @param block the block to split
	 * @param dpi the resolution of the page image the bounding boxes of the
	 *            argument annotation and its children refer to
	 * @param psm an observer object monitoring progress
	 */
	public static void splitIntoParagraphsLineMargin(ImRegion block, int dpi, ProgressMonitor psm) {
		if (psm == null)
			psm = ProgressMonitor.dummy;
		
		//	get lines
		ImRegion[] lines = getParagraphLayoutLines(block);
		psm.setInfo(" - found " + lines.length + " relevant lines in block");
		
		//	perform split
		splitIntoParagraphsLineMargin(block, dpi, psm, lines);
	}
	
	//	if we get here, we have sufficient lines, all with bounding boxes, and a non-null status monitor
	private static void splitIntoParagraphsLineMargin(ImRegion block, int dpi, ProgressMonitor psm, ImRegion[] lines) {
		
		//	get paragraphs
		ImRegion[] paragraphs = block.getRegions(MutableAnnotation.PARAGRAPH_TYPE);
		if (paragraphs.length < 3) {
			psm.setInfo(" ==> got " + paragraphs.length + " paragraphs, margin splitting too unreliable");
			return;
		}
		
		//	compute average paragraph margin and line margin
		int paragraphMarginSum = 0;
		for (int p = 1; p < paragraphs.length; p++)
			paragraphMarginSum += (paragraphs[p].bounds.top - paragraphs[p-1].bounds.bottom);
		int avgParagraphMargin = (paragraphMarginSum / (paragraphs.length - 1));
		int lineMarginSum = 0;
		for (int l = 1; l < lines.length; l++)
			lineMarginSum += (lines[l].bounds.top - lines[l-1].bounds.bottom);
		int avgLineMargin = (lineMarginSum / (lines.length - 1));
		
		int minLineBlockMargin = (dpi / 20); // twice the regular line margin, but less than regular block margin
		psm.setInfo(" - average line margin is " + avgLineMargin + ", min line block margin at " + minLineBlockMargin + ", average paragraph margin is " + avgParagraphMargin);
		
		//	make sure not to split at average lines (margins can be wide in some documents)
		if (minLineBlockMargin < ((avgLineMargin * 3) / 2)) {
			minLineBlockMargin = ((avgLineMargin * 3) / 2);
			psm.setInfo(" - average line margin is " + avgLineMargin + ", increased min line block margin to " + minLineBlockMargin);
		}
		
		//	group lines
		ImRegion[][] paragraphLines = getParagraphLines(paragraphs, lines);
		
		//	split paragraphs at wide spaces
		for (int p = 0; p < paragraphs.length; p++) {
			int spStartLineIndex = 0;
			psm.setInfo(" - investigating preliminary paragraph " + p + " with " + paragraphLines[p].length + " lines, paragraph is " + paragraphs[p].bounds.toString());
			for (int l = 1; l < paragraphLines[p].length; l++) {
				psm.setInfo(" - paragraph line distance is " + (paragraphLines[p][l].bounds.top - paragraphLines[p][l-1].bounds.bottom));
				int apsd = Math.abs((paragraphLines[p][l].bounds.top - paragraphLines[p][l-1].bounds.bottom) - avgParagraphMargin);
				int alsd = Math.abs((paragraphLines[p][l].bounds.top - paragraphLines[p][l-1].bounds.bottom) - avgLineMargin);
				
				//	this margin is larger than usual, do split
				if ((alsd > apsd) && (minLineBlockMargin < (paragraphLines[p][l].bounds.top - paragraphLines[p][l-1].bounds.bottom))) {
					ImRegion splitParagraph = new ImRegion(block.getPage(), ImLayoutObject.getAggregateBox(paragraphLines[p], spStartLineIndex, l), MutableAnnotation.PARAGRAPH_TYPE);
					spStartLineIndex = l;
					psm.setInfo(" - created sub paragraph at " + splitParagraph.bounds.toString());
				}
			}
			
			//	we've split something, mark last part, and remove original paragraph
			if (spStartLineIndex != 0) {
				ImRegion splitParagraph = new ImRegion(block.getPage(), ImLayoutObject.getAggregateBox(paragraphLines[p], spStartLineIndex, paragraphLines[p].length), MutableAnnotation.PARAGRAPH_TYPE);
				psm.setInfo(" - created final sub paragraph at " + splitParagraph.bounds.toString());
				block.getPage().removeRegion(paragraphs[p]);
			}
		}
		
		//	compute paragraph bounding boxes and compute indentation
		wrapAroundChildren(block, MutableAnnotation.PARAGRAPH_TYPE, LINE_ANNOTATION_TYPE);
		computeBlockLayout(block, dpi);
		
		//	compute font size of paragraphs
		computeParagraphFontSize(block);
	}
	
	/**
	 * Split the paragraphs in a block into smaller paragraphs at wide inner
	 * line distances, as measured by the text base lines of the individual
	 * lines.
	 * @param block the block to split
	 * @param dpi the resolution of the page image the bounding boxes of the
	 *            argument annotation and its children refer to
	 * @param psm an observer object monitoring progress
	 */
	public static void splitIntoParagraphsLineDistance(ImRegion block, int dpi, ProgressMonitor psm) {
		if (psm == null)
			psm = ProgressMonitor.dummy;
		
		//	get lines
		ImRegion[] lines = getParagraphLayoutLines(block);
		psm.setInfo(" - found " + lines.length + " relevant lines in block");
		
		//	perform split
		splitIntoParagraphsLineDistance(block, dpi, psm, lines);
	}
	
	//	if we get here, we have sufficient lines, all with bounding boxes, and a non-null status monitor
	private static void splitIntoParagraphsLineDistance(ImRegion block, int dpi, ProgressMonitor psm, ImRegion[] lines) {
		
		//	get paragraphs
		ImRegion[] paragraphs = block.getRegions(MutableAnnotation.PARAGRAPH_TYPE);
		
		//	group lines
		ImRegion[][] paragraphLines = getParagraphLines(paragraphs, lines);
		
		//	split paragraphs at above-average line distances
		for (int p = 0; p < paragraphs.length; p++) {
			int spStartLineIndex = 0;
			psm.setInfo(" - investigating preliminary paragraph " + p + " with " + paragraphLines[p].length + " lines, paragraph is " + paragraphs[p].bounds.toString());
			
			//	too few lines, too unreliable
			if (paragraphLines[p].length < 5) {
				psm.setInfo(" --> too few lines");
				continue;
			}
			
			//	collect line baselines and average baseline distance
			boolean baselinesValid = true;
			int[] lineBaselines = new int[paragraphLines[p].length];
			int lineBaselineDistanceSum = 0;
			for (int l = 0; l < paragraphLines[p].length; l++) try {
				lineBaselines[l] = Integer.parseInt((String) paragraphLines[p][l].getAttribute(BASELINE_ATTRIBUTE, "-1"));
				if (lineBaselines[l] < 1) {
					baselinesValid = false;
					break;
				}
				else if (l != 0) lineBaselineDistanceSum += (lineBaselines[l] - lineBaselines[l-1]);
			} catch (Exception e) {}
			
			//	got no baselines to work with
			if (!baselinesValid) {
				psm.setInfo(" --> some lines lack their baseline attribute");
				continue;
			}
			
			//	compute average
			int avgLineBaselineDistance = (lineBaselineDistanceSum / (paragraphLines[p].length - 1));
			psm.setInfo("   - average line distance is " + avgLineBaselineDistance);
			
			for (int l = 1; l < paragraphLines[p].length; l++) {
				
				//	at least 25% above average, perform split
				if ((avgLineBaselineDistance * 5) <= ((lineBaselines[l] - lineBaselines[l-1]) * 4)) {
					ImRegion splitParagraph = new ImRegion(block.getPage(), ImLayoutObject.getAggregateBox(paragraphLines[p], spStartLineIndex, l), MutableAnnotation.PARAGRAPH_TYPE);
					spStartLineIndex = l;
					psm.setInfo(" - created sub paragraph at " + splitParagraph.bounds.toString());
				}
				else psm.setInfo("   - line distance " + (lineBaselines[l] - lineBaselines[l-1]) + " too small for split");
			}
			
			//	we've split something, mark last part, and remove original one
			if (spStartLineIndex != 0) {
				ImRegion splitParagraph = new ImRegion(block.getPage(), ImLayoutObject.getAggregateBox(paragraphLines[p], spStartLineIndex, paragraphLines[p].length), MutableAnnotation.PARAGRAPH_TYPE);
				psm.setInfo(" - created final sub paragraph at " + splitParagraph.bounds.toString());
				block.getPage().removeRegion(paragraphs[p]);
			}
		}
		
		//	compute paragraph bounding boxes and compute indentation
		wrapAroundChildren(block, MutableAnnotation.PARAGRAPH_TYPE, LINE_ANNOTATION_TYPE);
		computeBlockLayout(block, dpi);
		
		//	compute font size of paragraphs
		computeParagraphFontSize(block);
	}
	
	private static void wrapAroundChildren(ImRegion region, String pType, String cType) {
		ImRegion[] pRegions = region.getRegions(pType);
		for (int p = 0; p < pRegions.length; p++) {
			int pLeft = pRegions[p].bounds.right;
			int pRight = pRegions[p].bounds.left;
			int pTop = pRegions[p].bounds.bottom;
			int pBottom = pRegions[p].bounds.top;
			ImRegion[] cRegions = (WORD_ANNOTATION_TYPE.equals(cType) ? region.getPage().getWordsInside(pRegions[p].bounds) : region.getPage().getRegionsInside(pRegions[p].bounds, false));
			for (int c = 0; c < cRegions.length; c++) {
				if (!cType.equals(cRegions[c].getType()))
					continue;
				pLeft = Math.min(pLeft, cRegions[c].bounds.left);
				pRight = Math.max(pRight, cRegions[c].bounds.right);
				pTop = Math.min(pTop, cRegions[c].bounds.top);
				pBottom = Math.max(pBottom, cRegions[c].bounds.bottom);
			}
			if ((pLeft < pRight) && (pTop < pBottom) && ((pRegions[p].bounds.left < pLeft) || (pRight < pRegions[p].bounds.right) || (pRegions[p].bounds.top < pTop) || (pBottom < pRegions[p].bounds.bottom))) {
				BoundingBox pBox = new BoundingBox(pLeft, pRight, pTop, pBottom);
				ImRegion pRegion = new ImRegion(region.getPage(), pBox, pType);
				pRegion.copyAttributes(pRegions[p]);
				region.getPage().removeRegion(pRegions[p]);
				pRegions[p] = pRegion;
			}
		}
	}
}