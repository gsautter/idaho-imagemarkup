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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;

import de.uka.ipd.idaho.gamta.Gamta;
import de.uka.ipd.idaho.gamta.util.CountingSet;
import de.uka.ipd.idaho.gamta.util.DocumentStyle;
import de.uka.ipd.idaho.gamta.util.imaging.BoundingBox;
import de.uka.ipd.idaho.gamta.util.imaging.ImagingConstants;
import de.uka.ipd.idaho.im.ImLayoutObject;
import de.uka.ipd.idaho.im.ImPage;
import de.uka.ipd.idaho.im.ImRegion;
import de.uka.ipd.idaho.im.ImWord;
import de.uka.ipd.idaho.im.util.ImDocumentStyle;
import de.uka.ipd.idaho.im.util.ImUtils;
import de.uka.ipd.idaho.im.util.LinePattern;

/**
 * Function library for analyzing the structure of text blocks in pages,
 * primarily paragraphs.
 * 
 * @author sautter
 */
public class PageAnalysis implements ImagingConstants {
	
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
			ImRegion[] paragraphs = blocks[b].getRegions(ImRegion.PARAGRAPH_TYPE);
			for (int p = 0; p < paragraphs.length; p++)
				paragraphsToBlocks.setProperty(paragraphs[p].bounds.toString(), blocks[b].bounds.toString());
		}
		
		//	compute average line height
		int avgLineHeigth = computeAverageLineHeight(column);
		if (avgLineHeigth != -1)
			column.setAttribute(LINE_HEIGHT_ATTRIBUTE, ("" + avgLineHeigth));
		
		//	get paragraphs
		ImRegion[] paragraphs = column.getRegions(ImRegion.PARAGRAPH_TYPE);
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
					String blockIdAbove = paragraphsToBlocks.getProperty(paragraphs[p-1].bounds.toString(), "ABOVE");
					String blockId = paragraphsToBlocks.getProperty(paragraphs[p].bounds.toString(), "CURRENT");
					String blockIdBelow = paragraphsToBlocks.getProperty(paragraphs[p+1].bounds.toString(), "BELOW");
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
					String blockIdAbove = paragraphsToBlocks.getProperty(paragraphs[p-1].bounds.toString(), "ABOVE");
					String blockId = paragraphsToBlocks.getProperty(paragraphs[p].bounds.toString(), "CURRENT");
					String blockIdBelow = paragraphsToBlocks.getProperty(paragraphs[p+1].bounds.toString(), "BELOW");
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
		ImRegion[] paragraphs = block.getRegions(ImRegion.PARAGRAPH_TYPE);
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
	
//	private static void computeParagraphFontSize(ImRegion block) {
//		ImRegion[] paragraphs = block.getRegions(ImRegion.PARAGRAPH_TYPE);
//		for (int p = 0; p < paragraphs.length; p++) {
//			ImRegion[] lines = getParagraphLayoutLines(paragraphs[p]);
//			int fontSizeSum = 0;
//			int fontSizeCount = 0;
//			for (int l = 0; l < lines.length; l++) {
//				String lineFontSizeString = ((String) lines[l].getAttribute(FONT_SIZE_ATTRIBUTE));
//				if (lineFontSizeString == null)
//					continue;
//				fontSizeSum += Integer.parseInt(lineFontSizeString);
//				fontSizeCount++;
//			}
//			if (fontSizeCount == 0)
//				continue;
//			int fontSize = ((fontSizeSum + (fontSizeCount / 2)) / fontSizeCount);
//			paragraphs[p].setAttribute(FONT_SIZE_ATTRIBUTE, ("" + fontSize));
//			for (int l = 0; l < lines.length; l++) {
//				if (!lines[l].hasAttribute(FONT_SIZE_ATTRIBUTE))
//					lines[l].setAttribute(FONT_SIZE_ATTRIBUTE, ("" + fontSize));
//			}
//		}
//	}
//	
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
		Arrays.sort(lines, ImUtils.topDownOrder);
		
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
	 * Test whether or not two text blocks could form a continuous piece of text
	 * in terms of style, i.e., font size, font face, fraction of words in bold
	 * or all-caps emphases, etc.
	 * @param firstBlockMetrics data for the first block to check
	 * @param secondBlockMetrics data for the second block to check
	 * @return true if the the two blocks are compatible
	 */
	public static boolean areContinuousStyle(BlockMetrics firstBlockMetrics, BlockMetrics secondBlockMetrics) {
		return areContinuousStyle(firstBlockMetrics, secondBlockMetrics, null);
	}
	
	/**
	 * Test whether or not two text blocks could form a continuous piece of text
	 * in terms of style, i.e., font size, font face, fraction of words in bold
	 * or all-caps emphases, etc.
	 * @param firstBlockMetrics data for the first block to check
	 * @param secondBlockMetrics data for the second block to check
	 * @param blockLayout a style template describing block layout
	 * @return true if the the two blocks are compatible
	 */
	public static boolean areContinuousStyle(BlockMetrics firstBlockMetrics, BlockMetrics secondBlockMetrics, ImDocumentStyle blockLayout) {
		
		//	compute block layouts
		BlockLayout firstBlockLayout = firstBlockMetrics.analyze(blockLayout);
		BlockLayout secondBlockLayout = secondBlockMetrics.analyze(blockLayout);
		
		//	compare block layouts
		return areContinuousStyle(firstBlockMetrics, firstBlockLayout, secondBlockMetrics, secondBlockLayout);
	}
	
	/**
	 * Test whether or not two text blocks could form a continuous piece of text
	 * in terms of style, i.e., font size, font face, fraction of words in bold
	 * or all-caps emphases, etc.
	 * @param firstBlockLayout data for the first block to check
	 * @param secondBlockLayout data for the second block to check
	 * @return true if the the two blocks are compatible
	 */
	public static boolean areContinuousStyle(BlockLayout firstBlockLayout, BlockLayout secondBlockLayout) {
		return areContinuousStyle(firstBlockLayout.blockMetrics, firstBlockLayout, secondBlockLayout.blockMetrics, secondBlockLayout);
	}
	
	private static final int EXACT_ALIGNMENT_MATCH_LINE_COUNT = 5;
	
	private static boolean areContinuousStyle(BlockMetrics firstBlockMetrics, BlockLayout firstBlockLayout, BlockMetrics secondBlockMetrics, BlockLayout secondBlockLayout) {
		
		//	require same font size for compatible style (cutting one Cicero of slack for super and sub script)
		if (1 < Math.abs(firstBlockMetrics.mainCharFontSize - secondBlockMetrics.mainCharFontSize)) {
			System.out.println("Style mismatch on main font size (" + firstBlockMetrics.mainCharFontSize + " vs. " + secondBlockMetrics.mainCharFontSize + ")");
			return false;
		}
		
		//	require fraction of bold words (TODO or characters ???) within 10 percentage points
		float firstBlockBoldFraction = (((float) firstBlockMetrics.boldCharCount) / firstBlockMetrics.charCount);
		float secondBlockBoldFraction = (((float) secondBlockMetrics.boldCharCount) / secondBlockMetrics.charCount);
		if (0.1 < Math.abs(firstBlockBoldFraction - secondBlockBoldFraction)) {
			System.out.println("Style mismatch on bold word fraction (" + firstBlockBoldFraction + " vs. " + secondBlockBoldFraction + ")");
			return false;
		}
		
		//	require fraction of all-caps words (TODO or characters ???) within 10 percentage points
		float firstBlockAllCapsFraction = (((float) firstBlockMetrics.allCapsCharCount) / firstBlockMetrics.charCount);
		float secondBlockAllCapsFraction = (((float) secondBlockMetrics.allCapsCharCount) / secondBlockMetrics.charCount);
		if (0.1 < Math.abs(firstBlockAllCapsFraction - secondBlockAllCapsFraction)) {
			System.out.println("Style mismatch on all-caps word fraction (" + firstBlockAllCapsFraction + " vs. " + secondBlockAllCapsFraction + ")");
			return false;
		}
		
		//	equal alignment and paragraph start line indent/outdent, we surely have a match
		if ((firstBlockLayout.alignment == secondBlockLayout.alignment) && (firstBlockLayout.paragraphStartLinePos == secondBlockLayout.paragraphStartLinePos))
			return true;
		
		//	both blocks have enough lines, so we should have matching layouts
		if ((EXACT_ALIGNMENT_MATCH_LINE_COUNT < firstBlockMetrics.lines.length) && (EXACT_ALIGNMENT_MATCH_LINE_COUNT < secondBlockMetrics.lines.length)) {
			
			//	alignment mis-match
			if (firstBlockLayout.alignment != secondBlockLayout.alignment) {
				System.out.println("Style mismatch on alignment (" + firstBlockLayout.alignment + " vs. " + secondBlockLayout.alignment + ")");
				return false;
			}
			
			//	mis-match on paragraph start line position, and neither block neutral
			if ((firstBlockLayout.paragraphStartLinePos != 'N') && (secondBlockLayout.paragraphStartLinePos != 'N')) {
				System.out.println("Style mismatch on paragraph start line position (" + firstBlockLayout.paragraphStartLinePos + " vs. " +  secondBlockLayout.paragraphStartLinePos + ")");
				return false;
			}
		}
		
		//	first block small, check if compatible with second one
		if (firstBlockMetrics.lines.length <= EXACT_ALIGNMENT_MATCH_LINE_COUNT) {
			if (secondBlockLayout.alignment == 'J') // justified layout can happen to be mistaken for left and right alignment judging from all too few lines
				return ("LJR".indexOf(firstBlockLayout.alignment) != -1);
			else return (firstBlockLayout.alignment == secondBlockLayout.alignment); // just mistaken indent/outdent, which easily happens with all too few lines
		}
		
		//	second block small, check if compatible with first one
		else if (firstBlockMetrics.lines.length <= EXACT_ALIGNMENT_MATCH_LINE_COUNT) {
			if (firstBlockLayout.alignment == 'J') // justified layout can happen to be mistaken for left and right alignment judging from all too few lines
				return ("LJR".indexOf(secondBlockLayout.alignment) != -1);
			else return (secondBlockLayout.alignment == firstBlockLayout.alignment); // just mistaken indent/outdent, which easily happens with all too few lines
		}
		
		//	both blocks small, can only filter for center alignment on both or neither
		else return ((firstBlockLayout.alignment == 'C') == (secondBlockLayout.alignment == 'C'));
	}
	
	/**
	 * Test whether or not two text blocks are in a relative position that
	 * implies a continuous flow of text across them. In particular, this
	 * method requres the argument blocks to be on the same page and inside the
	 * same column, and with no other regions lodged in the space between them.
	 * @param firstBlock the first block to check
	 * @param secondBlock the second block to check
	 * @return true if the the two blocks are spacially compatible
	 */
	public static boolean areContinuousLayout(ImRegion firstBlock, ImRegion secondBlock) {
		
		//	get page (layout can only be continuous if both blocks are on same page)
		ImPage page = firstBlock.getPage();
		if (page == null)
			page = firstBlock.getDocument().getPage(firstBlock.pageId);
		
		//	compare blocks
		return areContinuousLayout(page, firstBlock, secondBlock);
	}
	
	private static boolean areContinuousLayout(ImPage page, ImRegion firstBlock, ImRegion secondBlock) {
		
		//	different pages, we have a break
		if (firstBlock.pageId != secondBlock.pageId)
			return false;
		
		//	first block to left of second block, we have column break
		if (firstBlock.bounds.right <= secondBlock.bounds.left)
			return false;
		
		//	first block to right of second block, we still have column break (if one of the stranger kind)
		if (secondBlock.bounds.right <= firstBlock.bounds.left)
			return false;
		
		//	second block above first one, something stange
		if (secondBlock.bounds.top < firstBlock.bounds.bottom)
			return false;
		
		//	check for any other regions (table, image, graphics) in between our two blocks (there has to be something ...)
		ImRegion[] regions = page.getRegions();
		int minBlockLeft = Math.min(firstBlock.bounds.left, secondBlock.bounds.left);
		int maxBlockRight = Math.max(firstBlock.bounds.right, secondBlock.bounds.right);
		for (int r = 0; r < regions.length; r++) {
			if (regions[r].bounds.right <= minBlockLeft)
				continue; // off to the left
			if (maxBlockRight <= regions[r].bounds.left)
				continue; // off to the right
			if (regions[r].bounds.top <= firstBlock.bounds.bottom)
				continue; // too high up on page
			if (secondBlock.bounds.top <= regions[r].bounds.bottom)
				continue; // too far down on page
			if (ImRegion.IMAGE_TYPE.equals(regions[r].getType()))
				return false; // we're spanning over an image
			if (ImRegion.GRAPHICS_TYPE.equals(regions[r].getType()))
				return false; // we're spanning over some graphics
			if (ImRegion.TABLE_TYPE.equals(regions[r].getType()))
				return false; // we're spanning over a table
			if (ImRegion.BLOCK_ANNOTATION_TYPE.equals(regions[r].getType()))
				return false; // we're spanning over a text block (of whichever kind)
		}
		
//		//	first block less than one inch above second one, unlikely to have anything in between (figure, graphics, table) even if we missed it
//		//	NO USE IMPOSING A MINIMUM DISTANCE, IT'S STILL UNDISRUPTED, AND WE WANT TO FIND THE DISRUPTED CASES FOR CONTINUATION CHECKING
//		if ((secondBlock.bounds.top - firstBlock.bounds.bottom) < page.getImageDPI())
//			return true;
		
		//	nothing but empty white space between these tow blocks, so they could as well continue
		return true;
	}
	
	/**
	 * Compute layout metrics for a block of text.
	 * @param block the block to compute layout metrics for
	 * @return the block layout metrics
	 */
	public static BlockMetrics computeBlockMetrics(ImRegion block) {
		return computeBlockMetrics(block, null);
	}
	
	/**
	 * Compute layout metrics for a block of text.
	 * @param block the block to compute layout metrics for
	 * @param column the column the block lies in
	 * @return the block layout metrics
	 */
	public static BlockMetrics computeBlockMetrics(ImRegion block, ImRegion column) {
		
		//	get parent page
		ImPage page = block.getPage();
		if (page == null)
			page = block.getDocument().getPage(block.pageId);
		
		//	compute block layout metrics
		return computeBlockMetrics(page, page.getImageDPI(), block, column);
	}
	
	/**
	 * Compute layout metrics for a block of text.
	 * @param page the page the block lies in
	 * @param pageImageDpi the resolution of the underlying page image
	 * @param block the block to compute layout metrics for
	 * @return the block layout metrics
	 */
	public static BlockMetrics computeBlockMetrics(ImPage page, int pageImageDpi, ImRegion block) {
		return computeBlockMetrics(page, pageImageDpi, block, null);
	}
	
	/**
	 * Compute layout metrics for a block of text.
	 * @param page the page the block lies in
	 * @param pageImageDpi the resolution of the underlying page image
	 * @param block the block to compute layout metrics for
	 * @param column the column the block lies in
	 * @return the block layout metrics
	 */
	public static BlockMetrics computeBlockMetrics(ImPage page, int pageImageDpi, ImRegion block, ImRegion column) {
		if (DEBUG_LINE_METRICS) System.out.println("Computing block metrics on " + block.bounds + ((column == null) ? "" : (" in column " + column.bounds)));
		
		//	get lines
		ImRegion[] lines = block.getRegions(ImRegion.LINE_ANNOTATION_TYPE);
		//	TODO create lines if absent (e.g. in a table cell analyzed as a block against its column)
		//	TODO ALSO ensure that lines don't overlap ...
		//	TODO ... merging lines what are left-right adjacent (happens if blocks are shattered due to large word distances)
		//	TODO ALSO make sure OCR words have height of line (cut or expand) ...
		//	TODO ... and observe text direction in the process
		if (lines.length == 0)
			return null; // nothing to work with
		Arrays.sort(lines, ImUtils.topDownOrder);
		
		//	get context column (emulate as aggregate bounds of page blocks in assumed single-column layout if not found)
		if (column == null) {
			column = getParentColumn(page, block);
			if (DEBUG_LINE_METRICS && (column != null)) System.out.println(" - found parent column: " + column.bounds);
		}
		if (column == null) {
			column = createParentColumn(page, block);
			if (DEBUG_LINE_METRICS && (column != null)) System.out.println(" - computed parent column: " + column.bounds);
		}
		
		//	sort out lines without words
		ArrayList lineList = new ArrayList();
		for (int l = 0; l < lines.length; l++) {
			if (lines[l].getWords().length != 0)
				lineList.add(lines[l]);
		}
		if (lineList.size() == 0)
			return null; // nothing to work with
		
		//	measure left and right margins of lines both in column and in block
//		LineMetrics[] lineMetrics = new LineMetrics[lineList.size()];
//		for (int l = 0; l < lineList.size(); l++)
//			lineMetrics[l] = new LineMetrics(((ImRegion) lineList.get(l)), block, column);
		ArrayList lineMetrics = new ArrayList(lineList.size());
		for (int l = 0; l < lineList.size(); l++) try {
			lineMetrics.add(new LineMetrics(((ImRegion) lineList.get(l)), block, column));
		} catch (RuntimeException re) { /* ignore weird lines (almost exclusively occur in OCR of drawings) */ }
		if (lineMetrics.size() == 0)
			return null; // nothing to work with
		
		//	finally ...
		return new BlockMetrics(page, pageImageDpi, column, block, ((LineMetrics[]) lineMetrics.toArray(new LineMetrics[lineMetrics.size()])));
	}
	
	private static ImRegion getParentColumn(ImPage page, ImRegion block) {
		ImRegion[] pageColumns = page.getRegions(ImRegion.COLUMN_ANNOTATION_TYPE);
		for (int c = 0; c < pageColumns.length; c++) {
			if (pageColumns[c].bounds.includes(block.bounds, false))
				return pageColumns[c];
		}
		return null;
	}
	
	private static ImRegion createParentColumn(ImPage page, ImRegion block) {
		
		//	compute average page line height
		ImRegion[] pageLines = page.getRegions(ImRegion.LINE_ANNOTATION_TYPE);
		int pageLineHeightSum = 0;
		for (int l = 0; l < pageLines.length; l++)
			pageLineHeightSum += pageLines[l].bounds.getHeight();
		int avgPageLineHeight = ((pageLineHeightSum + (pageLines.length / 2)) / pageLines.length);
		
		//	get all columns in page
		ArrayList pageCols = new ArrayList(Arrays.asList(page.getRegions(ImRegion.COLUMN_ANNOTATION_TYPE)));
		
		//	exclude any column less than two lines high (should get us rid of any page header columns in single-column layouts)
		for (int c = 0; c < pageCols.size(); c++) {
			ImRegion pageCol = ((ImRegion) pageCols.get(c));
			if (pageCol.bounds.getHeight() < (avgPageLineHeight* 2))
				pageCols.remove(c--);
		}
		
		//	exclude any columns horizontally overlapping with block (any columns fully _containing_ block would have been chosen as parent above)
		for (int c = 0; c < pageCols.size(); c++) {
			ImRegion pageCol = ((ImRegion) pageCols.get(c));
			if (pageCol.bounds.right <= block.bounds.left)
				continue;
			if (block.bounds.right <= pageCol.bounds.left)
				continue;
			pageCols.remove(c--);
		}
		
		//	get all blocks in page
		ArrayList pageBlocks = new ArrayList(Arrays.asList(page.getRegions(ImRegion.BLOCK_ANNOTATION_TYPE)));
		
		//	exclude any blocks less than two lines high (should get us rid of any page header blocks in single-column layouts)
		for (int b = 0; b < pageBlocks.size(); b++) {
			ImRegion pageBlock = ((ImRegion) pageBlocks.get(b));
			if (pageBlock.bounds.getHeight() < (avgPageLineHeight* 2))
				pageBlocks.remove(b--);
		}
		
		//	exclude any blocks horizontally overlapping with any remaining column
		for (int c = 0; c < pageCols.size(); c++) {
			ImRegion pageCol = ((ImRegion) pageCols.get(c));
			for (int b = 0; b < pageBlocks.size(); b++) {
				ImRegion pageBlock = ((ImRegion) pageBlocks.get(b));
				if (pageCol.bounds.right <= pageBlock.bounds.left)
					continue;
				if (pageBlock.bounds.right <= pageCol.bounds.left)
					continue;
				pageBlocks.remove(b--);
			}
		}
		
		//	exclude and collect any blocks fully to left or right of argument block
		ArrayList leftBlocks = new ArrayList();
		ArrayList rightBlocks = new ArrayList();
		for (int b = 0; b < pageBlocks.size(); b++) {
			ImRegion pageBlock = ((ImRegion) pageBlocks.get(b));
			if (block.bounds.right <= pageBlock.bounds.left)
				rightBlocks.add(pageBlocks.remove(b--));
			else if (pageBlock.bounds.right <= block.bounds.left)
				leftBlocks.add(pageBlocks.remove(b--));
		}
		
		//	exclude any blocks overlapping with any block to left or right (gets us rid of column-spanning headings, captions, etc.)
		if (leftBlocks.size() != 0) {
			int leftBlockRight = 0;
			for (int b = 0; b < leftBlocks.size(); b++) {
				ImRegion leftBlock = ((ImRegion) leftBlocks.get(b));
				leftBlockRight = Math.max(leftBlockRight, leftBlock.bounds.right);
			}
			for (int b = 0; b < pageBlocks.size(); b++) {
				ImRegion pageBlock = ((ImRegion) pageBlocks.get(b));
				if (pageBlock.bounds.left < leftBlockRight)
					pageBlocks.remove(b--);
			}
		}
		if (rightBlocks.size() != 0) {
			int rightBlockLeft = page.bounds.right;
			for (int b = 0; b < rightBlocks.size(); b++) {
				ImRegion rightBlock = ((ImRegion) rightBlocks.get(b));
				rightBlockLeft = Math.min(rightBlockLeft, rightBlock.bounds.left);
			}
			for (int b = 0; b < pageBlocks.size(); b++) {
				ImRegion pageBlock = ((ImRegion) pageBlocks.get(b));
				if (rightBlockLeft < pageBlock.bounds.right)
					pageBlocks.remove(b--);
			}
		}
		
		//	resort to all page blocks if no blocks remaining
		if (pageBlocks.isEmpty())
			pageBlocks.addAll(Arrays.asList(page.getRegions(ImRegion.BLOCK_ANNOTATION_TYPE)));
		
		//	create parent column for argument block from remaining blocks
		ImRegion[] blockColBlocks = ((ImRegion[]) pageBlocks.toArray(new ImRegion[pageBlocks.size()]));
		BoundingBox blockColBounds = ImLayoutObject.getAggregateBox(blockColBlocks);
		return new ImRegion(page.getDocument(), page.pageId, blockColBounds, ImRegion.COLUMN_ANNOTATION_TYPE);
	}
	
	/**
	 * Split a text block into paragraphs. This method expects lines to be
	 * already marked. It further expects line words to come with text strings
	 * attached. The argument block has to be attached to its backing page, and
	 * the underlying page image has to be available.
	 * @param block the block to split into paragraphs
	 * @return true if any paragraphs were changed
	 */
	public static boolean splitIntoParagraphs(ImRegion block) {
		ImPage page = block.getPage();
		if (page == null)
			return false;
		if (page.getImageDPI() < 72)
			return false;
		return splitIntoParagraphs(page, page.getImageDPI(), block);
	}
	
	/**
	 * Split a text block into paragraphs. This method expects lines to be
	 * already marked. It further expects line words to come with text strings
	 * attached.
	 * @param page the page the block to split lies in
	 * @param pageImageDpi the resolution of the page
	 * @param block the block to split into paragraphs
	 * @return true if any paragraphs were changed
	 */
	public static boolean splitIntoParagraphs(ImPage page, int pageImageDpi, ImRegion block) {
		BlockMetrics blockMetrics = computeBlockMetrics(page, pageImageDpi, block);
		if (blockMetrics == null)
			return false; // happens if there are no lines at all
		BlockLayout blockLayout = blockMetrics.analyze();
		return blockLayout.writeParagraphStructure();
	}
	
	/**
	 * Split a text block into paragraphs. This method expects lines to be
	 * already marked. It further expects line words to come with text strings
	 * attached. The argument block has to be attached to its backing page, and
	 * the underlying page image has to be available.
	 * @param block the block to split into paragraphs
	 * @param layout a style template describing block layout
	 * @return true if any paragraphs were changed
	 */
	public static boolean splitIntoParagraphs(ImRegion block, ImDocumentStyle layout) {
		ImPage page = block.getPage();
		if (page == null)
			return false;
		if (page.getImageDPI() < 72)
			return false;
		return splitIntoParagraphs(page, page.getImageDPI(), block, layout);
	}
	
	/**
	 * Split a text block into paragraphs. This method expects lines to be
	 * already marked. It further expects line words to come with text strings
	 * attached.
	 * @param page the page the block to split lies in
	 * @param pageImageDpi the resolution of the page
	 * @param block the block to split into paragraphs
	 * @param layout a style template describing block layout
	 * @return true if any paragraphs were changed
	 */
	public static boolean splitIntoParagraphs(ImPage page, int pageImageDpi, ImRegion block, ImDocumentStyle layout) {
		BlockMetrics blockMetrics = computeBlockMetrics(page, pageImageDpi, block);
		if (blockMetrics == null)
			return false; // happens if there are no lines at all
		int maxAccPointSquashDistDenom = 25; // makes the tolerance about a millimeter
		int minSignificantHorizontalDist = layout.getIntProperty("minSignificantHorizontalDist", ((pageImageDpi + (maxAccPointSquashDistDenom / 2)) / maxAccPointSquashDistDenom), pageImageDpi);
		float minLeftAccPointSupport = layout.getFloatProperty("minLeftAccumulationPointSupport", 0.25f);
		float minCenterRightAccPointSupport = layout.getFloatProperty("minCenterRightAccumulationPointSupport", 0.25f);
		int minLineDistGapForDistSplitDenom = 50; // makes the tolerance about half a millimeter
		int minSignificantVerticalDist = layout.getIntProperty("minSignificantVerticalDist", ((pageImageDpi + (minLineDistGapForDistSplitDenom / 2)) / minLineDistGapForDistSplitDenom), pageImageDpi);
		float normSpaceWidth = layout.getFloatProperty("normSpaceWidth", 0.33f); // pretty conservative figure assuming wide spacing on short line check
		LinePattern[] splitLinePatterns = getSplitLinePatters(layout);
		BlockLayout blockLayout = blockMetrics.analyze(minSignificantHorizontalDist, minLeftAccPointSupport, minCenterRightAccPointSupport, minSignificantVerticalDist, normSpaceWidth, splitLinePatterns);
		return blockLayout.writeParagraphStructure();
	}
	
	private static LinePattern[] getSplitLinePatters(DocumentStyle layout) {
		String[] splitLinePatterStrs = layout.getStringListProperty("splitLinePatterns", null, " ");
		if ((splitLinePatterStrs == null) || (splitLinePatterStrs.length == 0))
			return new LinePattern[0];
		ArrayList splitLinePatters = new ArrayList(splitLinePatterStrs.length);
		for (int p = 0; p < splitLinePatterStrs.length; p++) try {
			splitLinePatters.add(LinePattern.parsePattern(splitLinePatterStrs[p]));
		}
		catch (IllegalArgumentException iae) {
			System.out.println("Error parsing split line pattern " + splitLinePatterStrs[p]);
			System.out.println(iae.getMessage());
			iae.printStackTrace(System.out);
		}
		return ((LinePattern[]) splitLinePatters.toArray(new LinePattern[splitLinePatters.size()]));
	}
	
	private static final boolean DEBUG_LINE_METRICS = true;
	
	/**
	 * Metrics of a single line of text inside a text block and a column.
	 * 
	 * @author sautter
	 */
	public static class LineMetrics {
		
		/** the line proper */
		public final ImRegion line;
		
		/** the words in the line, in left-to-right order */
		public final ImWord[] words;
		/** the average width of a space between two words within the line */
		public final int avgSpaceWidth;
		/** the bounding box of the leftmost space free (block of) word(s) in the line */
		public final BoundingBox startWordBlockBounds;
		/** the bounding box of the extended leftmost (block of) word(s) in the line, bridging the space after likely enumeration numbers, which mostly stick to their successor word(s) */
		public final BoundingBox extStartWordBlockBounds;
		
		/** does the line consist completely of a URI/URL/URN/DOI? */
		public final boolean isUri;
		/** does the line continue a URI/URL/URN/DOI, i.e., a query string? */
		public final boolean isUriContinuation;
		/** does the line start with words or punctuation indicating a sentence continuing rather than a new one starting? */
		public final boolean isSentenceContinuation;
		
		/** any in-line heading starting the line, including terminating punctuation (null if none) */
		public final String inLineHeading;
		
		/** the style of the in-line heading (if any), containing 'B' for bold, 'C' for all-caps, and 'I' for italics (null if none) */
		public final String inLineHeadingStyle;
		
		/** the text block containing the line */
		public final ImRegion block;
		/** the left margin of the line in the parent block, i.e., the space between the left block edge and the leftmost word in the line */
		public final int blockLeftMargin;
		/** the right margin of the line in the parent block, i.e., the space between the rightmost word in the line and the right block edge */
		public final int blockRightMargin;
		
		/** the text column containing the line */
		public final ImRegion column;
		/** the left margin of the line in the parent column, i.e., the space between the left column edge and the leftmost word in the line */
		public final int colLeftMargin;
		/** the right margin of the line in the parent column, i.e., the space between the rightmost word in the line and the right column edge */
		public final int colRightMargin;
		
		/** the number of bold words in the line */
		public final int boldWordCount;
		/** the number of italics words in the line */
		public final int italicsWordCount;
		/** the number of all-caps words in the line */
		public final int allCapsWordCount;
		
		/** the font sizes in the line, counting the number of words for each (populated only if words actually come with a 'fontSize' attribute) */
		public final CountingSet fontSizes = new CountingSet(new TreeMap());
		/** the predominant font size in the line (populated only if words actually come with a 'fontSize' attribute) */
		public final int mainFontSize;
		/** the font names in the line, counting the number of words for each (populated only if words actually come with a 'fontName' attribute) */
		public final CountingSet fontNames = new CountingSet(new TreeMap());
		/** the predominant font name in the line (populated only if words actually come with a 'fontName' attribute) */
		public final String mainFontName;
		
		/** the number of characters in the line */
		public final int charCount;
		/** the number of bold characters in the line */
		public final int boldCharCount;
		/** the number of italics characters in the line */
		public final int italicsCharCount;
		/** the number of all-caps characters in the line */
		public final int allCapsCharCount;
		
		/** the font sizes in the line, counting the number of characters for each (populated only if words actually come with a 'fontSize' attribute) */
		public final CountingSet charFontSizes = new CountingSet(new TreeMap());
		/** the predominant character-wise font size in the line (populated only if words actually come with a 'fontSize' attribute) */
		public final int mainCharFontSize;
		/** the font names in the line, counting the number of characters for each (populated only if words actually come with a 'fontName' attribute) */
		public final CountingSet charFontNames = new CountingSet(new TreeMap());
		/** the predominant character-wise font name in the line (populated only if words actually come with a 'fontName' attribute) */
		public final String mainCharFontName;
		
		//	TODO make this a factory method
		//	TODO use progress monitor instead of System.out
		
		LineMetrics(ImRegion line, ImRegion block, ImRegion column) {
			this.line = line;
			
			//	get words
			this.words = this.line.getWords();
			Arrays.sort(this.words, ImUtils.textStreamOrder);
			
			//	get line words, as well as dimensions of starting word(s) that would line wrap together
			int startWordBlockEnd = this.words.length;
			StringBuffer extStartWordBlockStr = new StringBuffer(this.words[0].getString());
			int extStartWordBlockEnd = this.words.length;
			int wordSpaceSum = 0;
			int wordSpaceCount = 0;
//			if (DEBUG_LINE_METRICS) System.out.println(" - start word: " + this.words[0].getString() + " at " + this.words[0].bounds);
			for (int w = 1; w < this.words.length; w++) {
				int wordMargin = (this.words[w].bounds.left - this.words[w-1].bounds.right);
//				if (DEBUG_LINE_METRICS) System.out.println(" - at " + wordMargin + ": " + this.words[w].getString() + " at " + this.words[w].bounds);
				if (2 < wordMargin) {
					if (startWordBlockEnd == this.words.length) {
//						if (DEBUG_LINE_METRICS) System.out.println(" ==> start word block ends before " + w);
						startWordBlockEnd = w;
					}
					if (extStartWordBlockEnd == this.words.length) {
						//	too large a space to span (over half line height) for an in-line enumeration
						if ((this.line.bounds.getHeight() / 2) < wordMargin) {
//							if (DEBUG_LINE_METRICS) System.out.println(" ==> extended start word block ends before " + w);
							extStartWordBlockEnd = w;
						}
						//	only continue with a word
						else if (!Gamta.isWord(this.words[w].getString()))
							extStartWordBlockEnd = w;
						//	span one reasonably-sized space if we have a line starting with '5. Test ...' or similar, as enumeration numbers often stick to their successor
						//	TEST captions in Amitus_vignus.pdf.imf
						else if (extStartWordBlockStr.toString().matches("[1-9][0-9]{0,2}\\.")) {}
						else if (extStartWordBlockStr.toString().matches("\\([1-9][0-9]{0,2}\\)")) {}
						else if (extStartWordBlockStr.toString().matches("[a-z]\\.")) {}
						else if (extStartWordBlockStr.toString().matches("[A-Z]\\.")) {}
						//	do not continue after anything else
						else extStartWordBlockEnd = w;
					}
					if (w < extStartWordBlockEnd)
						extStartWordBlockStr.append(' ');
					wordSpaceSum += wordMargin;
					wordSpaceCount++;
				}
				if (w < extStartWordBlockEnd)
					extStartWordBlockStr.append(this.words[w].getString());
			}
			this.avgSpaceWidth = ((wordSpaceCount == 0) ? -1 : ((wordSpaceSum + (wordSpaceCount / 2)) / wordSpaceCount));
//			if (DEBUG_LINE_METRICS) System.out.println(" ==> average space: " + this.avgSpaceWidth);
			this.startWordBlockBounds = ImLayoutObject.getAggregateBox(this.words, 0, startWordBlockEnd);
//			if (DEBUG_LINE_METRICS) System.out.println(" ==> start word block: " + this.startWordBlockBounds);
			this.extStartWordBlockBounds = ImLayoutObject.getAggregateBox(this.words, 0, extStartWordBlockEnd);
//			if (DEBUG_LINE_METRICS) System.out.println(" ==> extended start word block: " + this.extStartWordBlockBounds);
			
			//	compute positioning in block
			this.block = block;
			this.blockLeftMargin = Math.max(0, (this.line.bounds.left - this.block.bounds.left));
			this.blockRightMargin = Math.max(0, (this.block.bounds.right - this.line.bounds.right));
			
			//	compute positioning in column
			this.column = column;
			this.colLeftMargin = Math.max(0, (this.line.bounds.left - this.column.bounds.left));
			this.colRightMargin = Math.max(0, (this.column.bounds.right - this.line.bounds.right));
			
			//	test for URI first to prevent respective lower case start from being mistaken for sentence continuation
			ImWord[] startWords = this.line.getPage().getWordsInside(this.startWordBlockBounds);
			Arrays.sort(startWords, ImUtils.textStreamOrder);
			String startWordsStr = ImUtils.getString(startWords, true);
			//	TODO normalize start word string to handle accents, etc.
			String startWordStr = startWords[0].getString();
			if (startWordsStr.matches("(urn|uri|http|https|ftp)\\:.*") && this.startWordBlockBounds.equals(this.line.bounds)) {
				this.isUri = true;
				this.isUriContinuation = false;
				this.isSentenceContinuation = false;
				if (DEBUG_LINE_METRICS) System.out.println(" - found URI line: " + startWordsStr);
			}
			else if (false
				|| startWordsStr.matches(".*[a-zA-Z\\-\\_\\.]+\\=[a-zA-Z0-9\\-\\_\\.]+.*") // parameter name plus value
				|| startWordsStr.matches(".*\\?[a-zA-Z\\-\\_\\.]+.*") // start of query string (even if query string comes without '=')
				|| startWordsStr.matches(".*[a-zA-Z0-9\\-\\_\\.]+\\+[a-zA-Z0-9\\-\\_\\.]+.*") // escaped space between parameter value tokens
				|| startWordsStr.matches(".*[a-zA-Z0-9\\-\\_\\.]+\\/[a-zA-Z0-9\\-\\_\\.]+.*") // slash between path tokens
				|| startWordsStr.matches(".*[a-zA-Z0-9\\-\\_\\.]+\\&[a-zA-Z0-9\\-\\_\\.]+.*") // ampersand between parameters
				) {
				this.isUri = false;
				this.isUriContinuation = true;
				this.isSentenceContinuation = false;
				if (DEBUG_LINE_METRICS) System.out.println(" - found URI continuing line: " + startWordsStr);
			}
			else if (false
				|| "a".equals(startWordStr) || "y".equals(startWordStr) // single-letter stop words from English and Spanish
				|| startWordStr.matches("[a-z][a-z].*") // require at least two letters to avoid matching enumeration index letters
				|| startWordStr.matches("(\\.|\\u2024|\\u2025|\\u2026)+") // periods do not start a sentence, especially not sequences of them
				) {
				this.isUri = false;
				this.isUriContinuation = false;
				this.isSentenceContinuation = true;
				if (DEBUG_LINE_METRICS) System.out.println(" - found sentence continuing line: " + startWordsStr);
			}
			else {
				this.isUri = false;
				this.isUriContinuation = false;
				this.isSentenceContinuation = false;
			}
			
			//	assess font properties
			int boldWordCount = 0;
			int italicsWordCount = 0;
			int allCapsWordCount = 0;
			int charCount = 0;
			int boldCharCount = 0;
			int italicsCharCount = 0;
			int allCapsCharCount = 0;
			for (int w = 0; w < this.words.length; w++) {
				String wordString = this.words[w].getString();
				int wordCharCount = ((wordString == null) ? 1 : wordString.length());
				charCount += wordCharCount;
				if (this.words[w].hasAttribute(ImWord.BOLD_ATTRIBUTE)) {
					boldWordCount++;
					boldCharCount += wordCharCount;
				}
				if (this.words[w].hasAttribute(ImWord.ITALICS_ATTRIBUTE)) {
					italicsWordCount++;
					italicsCharCount += wordCharCount;
				}
				if (wordString.equals(wordString.toUpperCase()) && !wordString.equals(wordString.toLowerCase())) {
					allCapsWordCount++;
					allCapsCharCount += wordCharCount;
				}
				String fontName = ((String) this.words[w].getAttribute(ImWord.FONT_NAME_ATTRIBUTE));
				if (fontName != null) {
					this.fontNames.add(fontName);
					this.charFontNames.add(fontName, wordCharCount);
				}
				if (this.words[w].hasAttribute(ImWord.FONT_SIZE_ATTRIBUTE)) try {
					Integer fs = Integer.valueOf(this.words[w].getFontSize());
					this.fontSizes.add(fs);
					this.charFontSizes.add(fs, wordCharCount);
				} catch (NumberFormatException nfe) {}
			}
			this.boldWordCount = boldWordCount;
			this.italicsWordCount = italicsWordCount;
			this.allCapsWordCount = allCapsWordCount;
			this.mainFontName = (this.fontNames.isEmpty() ? null : ((String) getMostFrequentElement(this.fontNames)));
			this.mainFontSize = (this.fontSizes.isEmpty() ? -1 : ((Integer) getMostFrequentElement(this.fontSizes)).intValue());
			this.charCount = charCount;
			this.boldCharCount = boldCharCount;
			this.italicsCharCount = italicsCharCount;
			this.allCapsCharCount = allCapsCharCount;
			this.mainCharFontName = (this.charFontNames.isEmpty() ? null : ((String) getMostFrequentElement(this.charFontNames)));
			this.mainCharFontSize = (this.charFontSizes.isEmpty() ? -1 : ((Integer) getMostFrequentElement(this.charFontSizes)).intValue());
			
			//	find any in-line headings (title case bold or all-caps start with terminating punctuation of period or colon)
			boolean startIsBold = true;
			boolean startIsAllCaps = true;
			char startTerminatingPunctuation = ((char) 0);
			StringBuffer startHeading = new StringBuffer();
			for (int w = 0; w < this.words.length; w++) {
				String wordStr = this.words[w].getString();
				
				//	cannot work with empty string
				if ((wordStr == null) || (wordStr.length() == 0)) {
					startIsBold = false;
					startIsAllCaps = false;
					break;
				}
				
				//	word not even in title case
				if (wordStr.charAt(0) != Character.toUpperCase(wordStr.charAt(0))) {
					startIsBold = false;
					startIsAllCaps = false;
					break;
				}
				
				//	check continuation from predecessor
				if ((w == 0) && (this.words[w].getPreviousWord() != null)) {
					ImWord prevWord = this.words[w].getPreviousWord();
					if ((prevWord.getNextRelation() == ImWord.NEXT_RELATION_HYPHENATED) || (prevWord.getNextRelation() == ImWord.NEXT_RELATION_CONTINUE)) {
						startIsBold = false;
						startIsAllCaps = false;
						break;
					}
					if (prevWord.hasAttribute(ImWord.BOLD_ATTRIBUTE))
						startIsBold = false; // line is _continuing_ bold word sequence, not qualifying as bold emphasis in-line heading
				}
				
				//	stop at usual punctuation marks TODO extend punctuation marks as need arises
				if ((wordStr.length() == 1) && (".:-".indexOf(wordStr) != -1)) {
					startTerminatingPunctuation = wordStr.charAt(0);
					startHeading.append(wordStr);
					break;
				}
				
				//	we're out for words only
				if (wordStr.toUpperCase().equals(wordStr.toLowerCase())) {
					startIsBold = false;
					startIsAllCaps = false;
					break;
				}
				
				//	we're out for full words only (minimum length of 4 should cover very most nouns we're out for)
				if (wordStr.length() < 4) {
					startIsBold = false;
					startIsAllCaps = false;
					break;
				}
				
				//	check bold and all-caps
				if (!this.words[w].hasAttribute(ImWord.BOLD_ATTRIBUTE))
					startIsBold = false;
				if (!wordStr.equals(wordStr.toUpperCase()))
					startIsAllCaps = false;
				
				//	both emphases broken, no use looking any further
				if (!startIsBold && !startIsAllCaps)
					break;
				
				//	extend potential in-line heading
				if (w != 0)
					startHeading.append(' ');
				startHeading.append(wordStr);
			}
			
			//	no in-line heading found
			if (startTerminatingPunctuation == 0) {
				this.inLineHeading = null;
				this.inLineHeadingStyle = null;
			}
			
			//	we don't want punctuation-only headings (at least one four-letter word plus terminating punctuation mark)
			else if (startHeading.length() < 5) {
				this.inLineHeading = null;
				this.inLineHeadingStyle = null;
			}
			
			//	found in-line heading
			else {
				this.inLineHeading = startHeading.toString();
				this.inLineHeadingStyle = ((startIsBold ? "B" : "") + (startIsAllCaps ? "C" : ""));
				if (DEBUG_LINE_METRICS) System.out.println(" - found in-line heading (" + this.inLineHeadingStyle + "): " + this.inLineHeading);
			}
		}
	}
	
	private static Object getMostFrequentElement(CountingSet cs) {
		Object maxFreqObj = null;
		int maxFreqObjCount = 0;
		for (Iterator it = cs.iterator(); it.hasNext();) {
			Object obj = it.next();
			int objCount = cs.getCount(obj);
			if (maxFreqObjCount < objCount) {
				maxFreqObj = obj;
				maxFreqObjCount = objCount;
			}
		}
		return maxFreqObj;
	}
	
	private static final boolean DEBUG_BLOCK_METRICS = true;
	
	/**
	 * Metrics of a text block based upon its contained lines.
	 * 
	 * @author sautter
	 */
	public static class BlockMetrics {
		
		final ImPage page;
		final int pageImageDpi;
		
		/** the text column containing the block */
		public final ImRegion column;
		/** the block proper */
		public final ImRegion block;
		
		/** the lines contained in the block, in top-down order */
		public final LineMetrics[] lines;
		/** the gaps above the lines contained in the block, in synchronous order with the lines proper, and the first entry a filler */
		public final int[] aboveLineGaps;
		/** the the average gap above the lines contained in the block */
		public final int avgAboveLineGap;
		
		/** the total number of words in the block */
		public final int wordCount;
		/** the number of bold words in the block */
		public final int boldWordCount;
		/** the number of italics words in the block */
		public final int italicsWordCount;
		/** the number of all-caps words in the block */
		public final int allCapsWordCount;
		
		/** the font sizes in the block, counting the number of words for each (populated only if words actually come with a 'fontSize' attribute) */
		public final CountingSet fontSizes = new CountingSet(new TreeMap());
		/** the predominant font size in the block (populated only if words actually come with a 'fontSize' attribute) */
		public final int mainFontSize;
		/** the font names in the block, counting the number of words for each (populated only if words actually come with a 'fontName' attribute) */
		public final CountingSet fontNames = new CountingSet(new TreeMap());
		/** the predominant font name in the block (populated only if words actually come with a 'fontName' attribute) */
		public final String mainFontName;
		
		/** the number of characters in the block */
		public final int charCount;
		/** the number of bold characters in the block */
		public final int boldCharCount;
		/** the number of italics characters in the block */
		public final int italicsCharCount;
		/** the number of all-caps characters in the block */
		public final int allCapsCharCount;
		
		/** the font sizes in the block, counting the number of characters for each (populated only if words actually come with a 'fontSize' attribute) */
		public final CountingSet charFontSizes = new CountingSet(new TreeMap());
		/** the predominant character-wise font size in the block (populated only if words actually come with a 'fontSize' attribute) */
		public final int mainCharFontSize;
		/** the font names in the block, counting the number of characters for each (populated only if words actually come with a 'fontName' attribute) */
		public final CountingSet charFontNames = new CountingSet(new TreeMap());
		/** the predominant character-wise font name in the block (populated only if words actually come with a 'fontName' attribute) */
		public final String mainCharFontName;
		
		//	TODO make this a factory method
		//	TODO use progress monitor instead of System.out
		
		BlockMetrics(ImPage page, int pageImageDpi, ImRegion column, ImRegion block, LineMetrics[] lines) {
			this.page = page;
			this.pageImageDpi = pageImageDpi;
			this.column = column;
			this.block = block;
			this.lines = lines;
			
			//	measure line gaps
			this.aboveLineGaps = new int[this.lines.length];
			this.aboveLineGaps[0] = Short.MAX_VALUE;
			int lineGapSum = 0;
			for (int l = 1; l < this.lines.length; l++) {
				int lineGap = (this.lines[l].line.bounds.top - this.lines[l-1].line.bounds.bottom);
				this.aboveLineGaps[l] = lineGap;
				lineGapSum += lineGap;
			}
			this.avgAboveLineGap = ((this.lines.length < 2) ? -1 : ((lineGapSum + ((this.lines.length - 1) / 2))) / (this.lines.length - 1));
			
			//	aggregate font properties
			int wordCount = 0;
			int boldWordCount = 0;
			int italicsWordCount = 0;
			int allCapsWordCount = 0;
			int charCount = 0;
			int boldCharCount = 0;
			int italicsCharCount = 0;
			int allCapsCharCount = 0;
			for (int l = 0; l < this.lines.length; l++) {
				wordCount += this.lines[l].words.length;
				boldWordCount += this.lines[l].boldWordCount;
				italicsWordCount += this.lines[l].italicsWordCount;
				allCapsWordCount += this.lines[l].allCapsWordCount;
				this.fontNames.addAll(this.lines[l].fontNames);
				this.fontSizes.addAll(this.lines[l].fontSizes);
				charCount += this.lines[l].charCount;
				boldCharCount += this.lines[l].boldCharCount;
				italicsCharCount += this.lines[l].italicsCharCount;
				allCapsCharCount += this.lines[l].allCapsCharCount;
				this.charFontNames.addAll(this.lines[l].charFontNames);
				this.charFontSizes.addAll(this.lines[l].charFontSizes);
			}
			this.wordCount = wordCount;
			this.boldWordCount = boldWordCount;
			this.italicsWordCount = italicsWordCount;
			this.allCapsWordCount = allCapsWordCount;
			this.mainFontName = (this.fontNames.isEmpty() ? null : ((String) getMostFrequentElement(this.fontNames)));
			this.mainFontSize = (this.fontSizes.isEmpty() ? -1 : ((Integer) getMostFrequentElement(this.fontSizes)).intValue());
			this.charCount = charCount;
			this.boldCharCount = boldCharCount;
			this.italicsCharCount = italicsCharCount;
			this.allCapsCharCount = allCapsCharCount;
			this.mainCharFontName = (this.charFontNames.isEmpty() ? null : ((String) getMostFrequentElement(this.charFontNames)));
			this.mainCharFontSize = (this.charFontSizes.isEmpty() ? -1 : ((Integer) getMostFrequentElement(this.charFontSizes)).intValue());
		}
		
		/**
		 * Analyze the (paragraph) layout of the block. Use default parameter
		 * values for the analysis.
		 * @return the analysis result
		 */
		public BlockLayout analyze() {
			return this.doAnalyze(null, -1);
		}
		
		/**
		 * Analyze the (paragraph) layout of the block. Use default parameter
		 * values for the analysis.
		 * @param layout a style template describing block layout
		 * @return the analysis result
		 */
		public BlockLayout analyze(ImDocumentStyle layout) {
			return this.doAnalyze(null, -1, layout);
		}
		
		/**
		 * Analyze the (paragraph) layout of the block, virtually merging it up
		 * with a predecessor block from a preceding page or column. This method
		 * produces an analysis result as if the two blocks were one, and not
		 * split up due to page layout constraints. Use default parameter values
		 * for the analysis.
		 * @param predecessorBlockMetrics the block to continue from
		 * @param blockMargin the space to assume between the two blocks
		 * @return the analysis result
		 */
		public BlockLayout analyzeContinuingFrom(BlockMetrics predecessorBlockMetrics, int blockMargin) {
			return this.doAnalyze(predecessorBlockMetrics, blockMargin);
		}
		
		/**
		 * Analyze the (paragraph) layout of the block, virtually merging it up
		 * with a predecessor block from a preceding page or column. This method
		 * produces an analysis result as if the two blocks were one, and not
		 * split up due to page layout constraints. Use default parameter values
		 * for the analysis.
		 * @param predecessorBlockMetrics the block to continue from
		 * @param blockMargin the space to assume between the two blocks
		 * @param layout a style template describing block layout
		 * @return the analysis result
		 */
		public BlockLayout analyzeContinuingFrom(BlockMetrics predecessorBlockMetrics, int blockMargin, ImDocumentStyle layout) {
			return this.doAnalyze(predecessorBlockMetrics, blockMargin, layout);
		}
		
		/**
		 * Analyze the (paragraph) layout of the block. Use custom parameter
		 * values for the analysis.
		 * @param minSignificantHorizontalDist the minimum distance between two
		 *        accumulation points of some horizontal measure to consider them
		 *        actually intended different rather than noise
		 * @param minLeftAccPointSupport the minimum support for a left alignment
		 *        accumulation point; should be somewhat below the one for center
		 *        and right alignment due to differences for indent/outdent in
		 *        text aligned to left block edge
		 * @param minCenterRightAccPointSupport the minimum support for a center
		 *        or right alignment accumulation point
		 * @param minSignificantVerticalDist the minimum distance between two
		 *        accumulation points of some horizontal measure to consider them
		 *        actually intended different rather than noise
		 * @param normSpaceWidth width of a non-stretched space as a fraction of
		 *        the line height
		 * @return the resulting block layout
		 */
		public BlockLayout analyze(int minSignificantHorizontalDist, float minLeftAccPointSupport, float minCenterRightAccPointSupport, int minSignificantVerticalDist, float normSpaceWidth, LinePattern[] splitLinePatterns) {
			return this.doAnalyze(null, -1, minSignificantHorizontalDist, minLeftAccPointSupport, minCenterRightAccPointSupport, minSignificantVerticalDist, normSpaceWidth, splitLinePatterns);
		}
		
		/**
		 * Analyze the (paragraph) layout of the block, virtually merging it up
		 * with a predecessor block from a preceding page or column. This method
		 * produces an analysis result as if the two blocks were one, and not
		 * split up due to page layout constraints. Use custom parameter values
		 * for the analysis.
		 * @param predecessorBlockMetrics the block to continue from
		 * @param blockMargin the space to assume between the two blocks
		 * @param minSignificantHorizontalDist the minimum distance between two
		 *        accumulation points of some horizontal measure to consider them
		 *        actually intended different rather than noise
		 * @param minLeftAccPointSupport the minimum support for a left alignment
		 *        accumulation point; should be somewhat below the one for center
		 *        and right alignment due to differences for indent/outdent in
		 *        text aligned to left block edge
		 * @param minCenterRightAccPointSupport the minimum support for a center
		 *        or right alignment accumulation point
		 * @param minSignificantVerticalDist the minimum distance between two
		 *        accumulation points of some horizontal measure to consider them
		 *        actually intended different rather than noise
		 * @param normSpaceWidth width of a non-stretched space as a fraction of
		 *        the line height
		 * @return the analysis result
		 */
		public BlockLayout analyzeContinuingFrom(BlockMetrics predecessorBlockMetrics, int blockMargin, int minSignificantHorizontalDist, float minLeftAccPointSupport, float minCenterRightAccPointSupport, int minSignificantVerticalDist, float normSpaceWidth, LinePattern[] splitLinePatterns) {
			return this.doAnalyze(predecessorBlockMetrics, blockMargin, minSignificantHorizontalDist, minLeftAccPointSupport, minCenterRightAccPointSupport, minSignificantVerticalDist, normSpaceWidth, splitLinePatterns);
			
			//	TODO fix cross-page and cross-column paragraph continuation detection
			//	TODO TEST Zootaxa/zootaxa.4319.3.7.pdf.imf (page broken reference on Pages 9/10)
			
			//	TODO improve short line split in justified blocks (has to kick in earlier)
			//	TODO check line distance split with overall narrow line margins (maybe safety margins are too high)
			//	TODO TEST bibliography (pages 84-87) in 10.5281zenodo.1481114_vanTol_Guenther_2018.pdf
		}
		
		//	the tamplate based version (still falling back on the defaults if template is null, though)
		private BlockLayout doAnalyze(BlockMetrics predecessorBlockMetrics, int blockMargin, ImDocumentStyle layout) {
			if (layout == null)
				return this.doAnalyze(predecessorBlockMetrics, blockMargin);
			int maxAccPointSquashDistDenom = 25; // makes the tolerance about a millimeter
			int minSignificantHorizontalDist = layout.getIntProperty("minSignificantHorizontalDist", ((this.pageImageDpi + (maxAccPointSquashDistDenom / 2)) / maxAccPointSquashDistDenom), this.pageImageDpi);
			float minLeftAccPointSupport = layout.getFloatProperty("minLeftAccumulationPointSupport", 0.25f);
			float minCenterRightAccPointSupport = layout.getFloatProperty("minCenterRightAccumulationPointSupport", 0.25f);
			int minLineDistGapForDistSplitDenom = 50; // makes the tolerance about half a millimeter
			int minSignificantVerticalDist = layout.getIntProperty("minSignificantVerticalDist", ((this.pageImageDpi + (minLineDistGapForDistSplitDenom / 2)) / minLineDistGapForDistSplitDenom), this.pageImageDpi);
			float normSpaceWidth = layout.getFloatProperty("normSpaceWidth", 0.33f); // pretty conservative figure assuming wide spacing on short line check
			LinePattern[] splitLinePatterns = getSplitLinePatters(layout);
			return this.doAnalyze(predecessorBlockMetrics, blockMargin, minSignificantHorizontalDist, minLeftAccPointSupport, minCenterRightAccPointSupport, minSignificantVerticalDist, normSpaceWidth, splitLinePatterns);
		}
		
		//	the defaulting version
		private BlockLayout doAnalyze(BlockMetrics predecessorBlockMetrics, int blockMargin) {
			int maxAccPointSquashDistDenom = 25; // makes the tolerance about a millimeter
			int minSignificantHorizontalDist = ((this.pageImageDpi + (maxAccPointSquashDistDenom / 2)) / maxAccPointSquashDistDenom);
			float minLeftAccPointSupport = 0.25f;
			float minCenterRightAccPointSupport = 0.25f;
			int minLineDistGapForDistSplitDenom = 50; // makes the tolerance about half a millimeter
			int minSignificantVerticalDist = ((this.pageImageDpi + (minLineDistGapForDistSplitDenom / 2)) / minLineDistGapForDistSplitDenom);
			float normSpaceWidth = 0.33f; // pretty conservative figure assuming wide spacing on short line check
			return this.doAnalyze(predecessorBlockMetrics, blockMargin, minSignificantHorizontalDist, minLeftAccPointSupport, minCenterRightAccPointSupport, minSignificantVerticalDist, normSpaceWidth, new LinePattern[0]);
		}
		
		private BlockLayout doAnalyze(BlockMetrics predecessorBlockMetrics, int blockMargin, int minSignificantHorizontalDist, float minLeftAccPointSupport, float minCenterRightAccPointSupport, int minSignificantVerticalDist, float normSpaceWidth, LinePattern[] splitLinePatterns) {
			
			//	compute parameters
			LineMetrics[] lines;
			int[] aboveLineGaps;
			int colWidth;
			int blockWidth;
			
			//	for single block analysis
			if (predecessorBlockMetrics == null) {
				lines = this.lines;
				aboveLineGaps = this.aboveLineGaps;
				colWidth = this.column.bounds.getWidth();
				blockWidth = this.block.bounds.getWidth();
			}
			
			//	for block continuation analysis
			else {
				lines = new LineMetrics[predecessorBlockMetrics.lines.length + this.lines.length];
				System.arraycopy(predecessorBlockMetrics.lines, 0, lines, 0, predecessorBlockMetrics.lines.length);
				System.arraycopy(this.lines, 0, lines, predecessorBlockMetrics.lines.length, this.lines.length);
				
				aboveLineGaps = new int[predecessorBlockMetrics.lines.length + this.lines.length];
				System.arraycopy(predecessorBlockMetrics.aboveLineGaps, 0, aboveLineGaps, 0, predecessorBlockMetrics.aboveLineGaps.length);
				System.arraycopy(this.aboveLineGaps, 0, aboveLineGaps, predecessorBlockMetrics.lines.length, this.aboveLineGaps.length);
				aboveLineGaps[predecessorBlockMetrics.lines.length] = blockMargin;
				
				colWidth = Math.max(predecessorBlockMetrics.column.bounds.getWidth(), this.column.bounds.getWidth());
				blockWidth = Math.max(predecessorBlockMetrics.block.bounds.getWidth(), this.block.bounds.getWidth());
			}
			
			//	perform actual analysis
			BlockLayout blockLayout = doAnalyze(lines, aboveLineGaps, this.pageImageDpi, colWidth, blockWidth, minSignificantHorizontalDist, minLeftAccPointSupport, minCenterRightAccPointSupport, minSignificantVerticalDist, normSpaceWidth, splitLinePatterns);
			
			//	create final result
			if (predecessorBlockMetrics == null)
				return new BlockLayout(this, blockLayout.alignment, blockLayout.paragraphStartLinePos, blockLayout.shortLineEndsParagraph, blockLayout.uriLineSplitMode, blockLayout.inLineHeadingStyle, blockLayout.inLineHeadingTerminator, blockLayout.paragraphDistance, blockLayout.isParagraphStartLine, blockLayout.paragraphStartLineEvidence);
			
			else {
				boolean[] predecessorIsParagraphStartLine = new boolean[predecessorBlockMetrics.lines.length];
				System.arraycopy(blockLayout.isParagraphStartLine, 0, predecessorIsParagraphStartLine, 0, predecessorIsParagraphStartLine.length);
				boolean[] isParagraphStartLine = new boolean[this.lines.length];
				System.arraycopy(blockLayout.isParagraphStartLine, predecessorBlockMetrics.lines.length, isParagraphStartLine, 0, isParagraphStartLine.length);
				
				String[] predecessorParagraphStartLineEvidence = new String[predecessorBlockMetrics.lines.length];
				System.arraycopy(blockLayout.paragraphStartLineEvidence, 0, predecessorParagraphStartLineEvidence, 0, predecessorParagraphStartLineEvidence.length);
				String[] paragraphStartLineEvidence = new String[this.lines.length];
				System.arraycopy(blockLayout.paragraphStartLineEvidence, predecessorBlockMetrics.lines.length, paragraphStartLineEvidence, 0, paragraphStartLineEvidence.length);
				
				return new BlockLayout(predecessorBlockMetrics, this, blockLayout.alignment, blockLayout.paragraphStartLinePos, blockLayout.shortLineEndsParagraph, blockLayout.uriLineSplitMode, blockLayout.inLineHeadingStyle, blockLayout.inLineHeadingTerminator, blockLayout.paragraphDistance, predecessorIsParagraphStartLine, predecessorParagraphStartLineEvidence, isParagraphStartLine, paragraphStartLineEvidence);
			}
		}
		
		//	TODO use progress monitor instead of System.out
		
		private static BlockLayout doAnalyze(LineMetrics[] lines, int[] aboveLineGaps, int pageImageDpi, int colWidth, int blockWidth, int minSignificantHorizontalDist, float minLeftAccPointSupport, float minCenterRightAccPointSupport, int minSignificantVerticalDist, float normSpaceWidth, LinePattern[] splitLinePatterns) {
			
			//	analyze lines against column and block, as well as line distance
			CountingSet colLeftLineDists = new CountingSet(new TreeMap());
			int colLeftLineDistSum = 0;
			CountingSet colRightLineDists = new CountingSet(new TreeMap());
			int colRightLineDistSum = 0;
//			CountingSet colEdgeDistDiscrepancies = new CountingSet(new TreeMap());
//			int colEdgeDistDiscrepancySum = 0;
			CountingSet blockLeftLineDists = new CountingSet(new TreeMap());
//			int blockLeftLineDistSum = 0;
			CountingSet blockRightLineDists = new CountingSet(new TreeMap());
//			int blockRightLineDistSum = 0;
			CountingSet blockEdgeDistDiscrepancies = new CountingSet(new TreeMap());
//			int blockEdgeDistDiscrepancySum = 0;
			int lineHeightSum = 0;
			CountingSet lineGaps = new CountingSet(new TreeMap());
			int lineGapSum = 0;
			boolean[] isColShortLine = new boolean[lines.length];
			Arrays.fill(isColShortLine, false);
			int colShortLineCount = 0;
			boolean[] isBlockShortLine = new boolean[lines.length];
			Arrays.fill(isBlockShortLine, false);
			int blockShortLineCount = 0;
			int blockLeftWordWeight = 0;
			int blockRightWordWeight = 0;
			int colLeftWordWeight = 0;
			int colRightWordWeight = 0;
			int wBlockLeftWordWeight = 0;
			int wBlockRightWordWeight = 0;
			int wColLeftWordWeight = 0;
			int wColRightWordWeight = 0;
			for (int l = 0; l < lines.length; l++) {
				colLeftLineDists.add(new Integer(lines[l].colLeftMargin));
				colLeftLineDistSum += lines[l].colLeftMargin;
				colRightLineDists.add(new Integer(lines[l].colRightMargin));
				colRightLineDistSum += lines[l].colRightMargin;
//				colEdgeDistDiscrepancies.add(new Integer(Math.abs(lines[l].colLeftMargin - lines[l].colRightMargin)));
//				colEdgeDistDiscrepancySum += Math.abs(lines[l].colLeftMargin - lines[l].colRightMargin);
				blockLeftLineDists.add(new Integer(lines[l].blockLeftMargin));
//				blockLeftLineDistSum += lines[l].blockLeftMargin;
				blockRightLineDists.add(new Integer(lines[l].blockRightMargin));
//				blockRightLineDistSum += lines[l].blockRightMargin;
				blockEdgeDistDiscrepancies.add(new Integer(Math.abs(lines[l].blockLeftMargin - lines[l].blockRightMargin)));
//				blockEdgeDistDiscrepancySum += Math.abs(lines[l].blockLeftMargin - lines[l].blockRightMargin);
				if (DEBUG_BLOCK_LAYOUT) {
					System.out.println(" - '" + lines[l].words[0] + "': " + lines[l].colLeftMargin + "/" + lines[l].colRightMargin + " in column, " + lines[l].blockLeftMargin + "/" + lines[l].blockRightMargin + " in block");
					System.out.println("   start word(s) are " + lines[l].startWordBlockBounds.getWidth() + " long");
				}
				lineHeightSum += lines[l].line.bounds.getHeight();
				if (l != 0) {
					lineGapSum += aboveLineGaps[l];
					lineGaps.add(new Integer(aboveLineGaps[l]));
					if (((lines[l].line.bounds.getHeight() / 3) /* ballpark figure space */ + lines[l].startWordBlockBounds.getWidth()) < lines[l-1].colRightMargin) {
						isColShortLine[l-1] = true;
						colShortLineCount++;
						if (DEBUG_BLOCK_LAYOUT) System.out.println("   ==> previous line is short in column by " + (lines[l-1].colRightMargin - (lines[l].line.bounds.getHeight() / 3) - lines[l].startWordBlockBounds.getWidth()));
					}
					if (((lines[l].line.bounds.getHeight() / 3) /* ballpark figure space */ + lines[l].startWordBlockBounds.getWidth()) < lines[l-1].blockRightMargin) {
						isBlockShortLine[l-1] = true;
						blockShortLineCount++;
						if (DEBUG_BLOCK_LAYOUT) System.out.println("   ==> previous line is short in block by " + (lines[l-1].blockRightMargin - (lines[l].line.bounds.getHeight() / 3) - lines[l].startWordBlockBounds.getWidth()));
					}
				}
				int blockCenterX = ((lines[l].block.bounds.left + lines[l].block.bounds.right) / 2);
				int colCenterX = ((lines[l].column.bounds.left + lines[l].column.bounds.right) / 2);
				for (int w = 0; w < lines[l].words.length; w++) {
					if (lines[l].words[w].bounds.left < blockCenterX) {
						blockLeftWordWeight += ((Math.min(blockCenterX, lines[l].words[w].bounds.right) - lines[l].words[w].bounds.left) * lines[l].line.bounds.getHeight());
						for (int x = lines[l].words[w].bounds.left; x < Math.min(blockCenterX, lines[l].words[w].bounds.right); x++)
							wBlockLeftWordWeight += Math.round(Math.sqrt(blockCenterX - x));
					}
					if (blockCenterX < lines[l].words[w].bounds.right) {
						blockRightWordWeight += ((lines[l].words[w].bounds.right - Math.max(blockCenterX, lines[l].words[w].bounds.left)) * lines[l].line.bounds.getHeight());
						for (int x = Math.max((blockCenterX + 1), lines[l].words[w].bounds.left); x < lines[l].words[w].bounds.right; x++)
							wBlockRightWordWeight += Math.round(Math.sqrt(x - blockCenterX));
					}
					if (lines[l].words[w].bounds.left < colCenterX) {
						colLeftWordWeight += ((Math.min(colCenterX, lines[l].words[w].bounds.right) - lines[l].words[w].bounds.left) * lines[l].line.bounds.getHeight());
						for (int x = lines[l].words[w].bounds.left; x < Math.min(colCenterX, lines[l].words[w].bounds.right); x++)
							wColLeftWordWeight += Math.round(Math.sqrt(colCenterX - x));
					}
					if (colCenterX < lines[l].words[w].bounds.right) {
						colRightWordWeight += ((lines[l].words[w].bounds.right - Math.max(colCenterX, lines[l].words[w].bounds.left)) * lines[l].line.bounds.getHeight());
						for (int x = Math.max((colCenterX + 1), lines[l].words[w].bounds.left); x < lines[l].words[w].bounds.right; x++)
							wColRightWordWeight += Math.round(Math.sqrt(x - colCenterX));
					}
				}
			}
			int avgColLeftLineMargin = ((colLeftLineDistSum + (lines.length / 2)) / lines.length);
			int avgColRightLineMargin = ((colRightLineDistSum + (lines.length / 2)) / lines.length);
//			int avgBlockLeftLineMargin = ((blockLeftLineDistSum + (lines.length / 2)) / lines.length);
//			int avgBlockRightLineMargin = ((blockRightLineDistSum + (lines.length / 2)) / lines.length);
			int avgLineHeight = ((lineHeightSum + (lines.length / 2)) / lines.length);
			int avgLineGap = ((lines.length < 2) ? 0 : ((lineGapSum + (lines.length / 2)) / (lines.length - 1)));
			
			float blockWordWeightRatio = (((float) blockRightWordWeight) / (blockLeftWordWeight + blockRightWordWeight));
			float colWordWeightRatio = (((float) colRightWordWeight) / (colLeftWordWeight + colRightWordWeight));
			float wBlockWordWeightRatio = (((float) wBlockRightWordWeight) / (wBlockLeftWordWeight + wBlockRightWordWeight));
			float wColWordWeightRatio = (((float) wColRightWordWeight) / (wColLeftWordWeight + wColRightWordWeight));
			if (DEBUG_BLOCK_LAYOUT) {
				System.out.println("Left word weight in block is " + blockLeftWordWeight);
				System.out.println("Right word weight in block is " + blockRightWordWeight);
				System.out.println(" ==> word weight ratio in block is " + blockWordWeightRatio);
				System.out.println("Left word weight in column is " + colLeftWordWeight);
				System.out.println("Right word weight in column is " + colRightWordWeight);
				System.out.println(" ==> word weight ratio in column is " + colWordWeightRatio);
				
				System.out.println("Weighted left word weight in block is " + wBlockLeftWordWeight);
				System.out.println("Weighted right word weight in block is " + wBlockRightWordWeight);
				System.out.println(" ==> weighted word weight ratio in block is " + wBlockWordWeightRatio);
				System.out.println("Weighted left word weight in column is " + wColLeftWordWeight);
				System.out.println("Weighted right word weight in column is " + wColRightWordWeight);
				System.out.println(" ==> weighted word weight ratio in column is " + wColWordWeightRatio);
			}
			
			//	TODOne find good fraction of DPI to use as maximum gap in accumulation points
			int minAccPointMemberCount = ((lines.length < 3) ? 1 : 2);
			
			//	test line starts for sentence continuation, as well as for URIs
			if (DEBUG_BLOCK_LAYOUT) System.out.println("Assessing line starts");
			CountingSet sentenceContinuingColLineStarts = new CountingSet(new TreeMap());
			CountingSet sentenceContinuingBlockLineStarts = new CountingSet(new TreeMap());
			for (int l = 0; l < lines.length; l++) {
				if (lines[l].isUri) {
					if (DEBUG_BLOCK_LAYOUT) System.out.println(" - found URI line: " + ImUtils.getString(lines[l].words, true));
				}
				else if (lines[l].isUriContinuation) {
					sentenceContinuingColLineStarts.add(new Integer(lines[l].colLeftMargin));
					sentenceContinuingBlockLineStarts.add(new Integer(lines[l].blockLeftMargin));
					if (DEBUG_BLOCK_LAYOUT) System.out.println(" - found URI continuing line: " + ImUtils.getString(lines[l].words, true));
				}
				else if (lines[l].isSentenceContinuation) {
					sentenceContinuingColLineStarts.add(new Integer(lines[l].colLeftMargin));
					sentenceContinuingBlockLineStarts.add(new Integer(lines[l].blockLeftMargin));
					if (DEBUG_BLOCK_LAYOUT) System.out.println(" - found sentence continuing line: " + ImUtils.getString(lines[l].words, true));
				}
			}
			if (DEBUG_BLOCK_LAYOUT) System.out.println("Testing sentence continuation line column anchored left accumulation points");
			AccumulationPoint[] sentenceContinuingColLeftAccPoints = getAccumulationPoints(sentenceContinuingColLineStarts, (minSignificantHorizontalDist / 2), minLeftAccPointSupport, minAccPointMemberCount);
			if (1 < sentenceContinuingColLeftAccPoints.length)
				sentenceContinuingColLeftAccPoints = squashCloseAccumulationPoints(sentenceContinuingColLeftAccPoints, minSignificantHorizontalDist);
			if (DEBUG_BLOCK_LAYOUT) System.out.println("Testing sentence continuation line block anchored left accumulation points");
			AccumulationPoint[] sentenceContinuingBlockLeftAccPoints = getAccumulationPoints(sentenceContinuingBlockLineStarts, (minSignificantHorizontalDist / 2), minLeftAccPointSupport, minAccPointMemberCount);
			if (1 < sentenceContinuingBlockLeftAccPoints.length)
				sentenceContinuingBlockLeftAccPoints = squashCloseAccumulationPoints(sentenceContinuingBlockLeftAccPoints, minSignificantHorizontalDist);
			
			//	TODOne find accumulation points in left distances to detect left alignment in presence of indent and outdent
			if (DEBUG_BLOCK_LAYOUT) System.out.println("Testing column anchored left accumulation points");
			AccumulationPoint[] colLeftAccPoints = getAccumulationPoints(colLeftLineDists, (minSignificantHorizontalDist / 2), minLeftAccPointSupport, minAccPointMemberCount);
			//	if we end up with a single very high support accumulation point, check if remaining lines form another one
			//	TODOne TEST left column on page 5 in Gulec2007.imf (sentence continuation assessment thrown off by many references to 'de Bonis')
			if ((colLeftAccPoints.length == 1) && (colLeftAccPoints[0].count > (lines.length * (1 - minLeftAccPointSupport - minLeftAccPointSupport)))) {
				if (DEBUG_BLOCK_LAYOUT) System.out.println("Adding minority column anchored left accumulation points");
				float mMinLeftAccPointSupport = (((float) (lines.length - colLeftAccPoints[0].count)) / (lines.length * 2));
				colLeftAccPoints = getAccumulationPoints(colLeftLineDists, (minSignificantHorizontalDist / 2), mMinLeftAccPointSupport, minAccPointMemberCount);
			}
			if (colLeftAccPoints.length < 2) {
				if (DEBUG_BLOCK_LAYOUT) System.out.println("Adding sentence continuation line column anchored left accumulation points");
				colLeftAccPoints = mergeAccumulationPoints(colLeftAccPoints, sentenceContinuingColLeftAccPoints);
			}
			if (2 < colLeftAccPoints.length)
				colLeftAccPoints = squashCloseAccumulationPoints(colLeftAccPoints, minSignificantHorizontalDist);
			
			if (DEBUG_BLOCK_LAYOUT) System.out.println("Testing block anchored left accumulation points");
			AccumulationPoint[] blockLeftAccPoints = getAccumulationPoints(blockLeftLineDists, (minSignificantHorizontalDist / 2), minLeftAccPointSupport, minAccPointMemberCount);
			//	if we end up with a single very high support accumulation point, check if remaining lines form another one
			//	TODOne TEST left column on page 5 in Gulec2007.imf (sentence continuation assessment thrown off by many references to 'de Bonis')
			if ((blockLeftAccPoints.length == 1) && (blockLeftAccPoints[0].count > (lines.length * (1 - minLeftAccPointSupport - minLeftAccPointSupport)))) {
				if (DEBUG_BLOCK_LAYOUT) System.out.println("Adding minority block anchored left accumulation points");
				float mMinLeftAccPointSupport = (((float) (lines.length - blockLeftAccPoints[0].count)) / (lines.length * 2));
				blockLeftAccPoints = getAccumulationPoints(blockLeftLineDists, (minSignificantHorizontalDist / 2), mMinLeftAccPointSupport, minAccPointMemberCount);
			}
			if (blockLeftAccPoints.length < 2) {
				if (DEBUG_BLOCK_LAYOUT) System.out.println("Adding sentence continuation line block anchored left accumulation points");
				blockLeftAccPoints = mergeAccumulationPoints(blockLeftAccPoints, sentenceContinuingBlockLeftAccPoints);
			}
			if (2 < blockLeftAccPoints.length)
				blockLeftAccPoints = squashCloseAccumulationPoints(blockLeftAccPoints, minSignificantHorizontalDist);
			
			//	TODOne find accumulation points in right distances to detect justification
//			System.out.println("Testing column anchored right accumulation points");
//			AccumulationPoint[] colRightAccPoints = getAccumulationPoints(colRightLineDists, (minSignificantHorizontalDist / 2), minCenterRightAccPointSupport, minAccPointMemberCount);
//			if (1 < colRightAccPoints.length)
//				colRightAccPoints = squashCloseAccumulationPoints(colRightAccPoints, minSignificantHorizontalDist);
			if (DEBUG_BLOCK_LAYOUT) System.out.println("Testing block anchored right accumulation points");
			AccumulationPoint[] blockRightAccPoints = getAccumulationPoints(blockRightLineDists, (minSignificantHorizontalDist / 2), minCenterRightAccPointSupport, minAccPointMemberCount);
			if (1 < blockRightAccPoints.length)
				blockRightAccPoints = squashCloseAccumulationPoints(blockRightAccPoints, minSignificantHorizontalDist);
			
			//	TODOne find accumulation points in difference between left and right margins to detect center alignment
//			System.out.println("Testing column anchored center deviation accumulation points");
//			AccumulationPoint[] colCenterAccPoints = getAccumulationPoints(colEdgeDistDiscrepancies, (minSignificantHorizontalDist / 2), minCenterRightAccPointSupport, minAccPointMemberCount);
//			if (1 < colCenterAccPoints.length)
//				colCenterAccPoints = squashCloseAccumulationPoints(colCenterAccPoints, minSignificantHorizontalDist);
			if (DEBUG_BLOCK_LAYOUT) System.out.println("Testing block anchored center deviation accumulation points");
			AccumulationPoint[] blockCenterAccPoints = getAccumulationPoints(blockEdgeDistDiscrepancies, (minSignificantHorizontalDist / 2), minCenterRightAccPointSupport, minAccPointMemberCount);
			if (1 < blockCenterAccPoints.length)
				blockCenterAccPoints = squashCloseAccumulationPoints(blockCenterAccPoints, minSignificantHorizontalDist);
			
			//	TODOne find accumulation points in line gaps to find line distance splits
			if (DEBUG_BLOCK_LAYOUT) System.out.println("Testing line distance accumulation points");
			AccumulationPoint[] lineGapAccPoints = getAccumulationPoints(lineGaps, (minSignificantVerticalDist / 2), 0.10f, minAccPointMemberCount);
			if (2 < lineGapAccPoints.length)
				lineGapAccPoints = squashCloseAccumulationPoints(lineGapAccPoints, (minSignificantVerticalDist - 1));
			
			/* TODOne analyze accumulation points:
			 * - left := got one or two accumulation points on left (two happen for blocks with in/outdent and short paragraphs, e.g. in bibliography)
			 * - right := got one accumulation point on right, close to 0 at least in block
			 * - centered := got one accumulation point in center deviation
			 * - justified := left & right & centered (maybe waive centered)
			 * 
			 * - list paragraph (e.g. EJT dates in end matter) := left & !right & all lines short in column
			 * - narrow justified paragraph (narrower-than-column section abstract) := left & right 
			 */
			if (DEBUG_BLOCK_LAYOUT) System.out.println("Layout analysis:");
			boolean leftAligned;
			boolean rightAligned;
			boolean centerAligned;
			boolean justified;
			
			//	use position in column for single-line block
			if (lines.length == 1) {
				leftAligned = (lines[0].colLeftMargin < (pageImageDpi / 2)); // cut half an inch of slack for indent
				if (DEBUG_BLOCK_LAYOUT) System.out.println(" - left is " + leftAligned + " for column anchored left margin " + lines[0].colLeftMargin);
				rightAligned = (lines[0].colRightMargin < minSignificantHorizontalDist); // this has to be tight (within 1/25 of an inch (1 mm) by default)
				if (DEBUG_BLOCK_LAYOUT) System.out.println(" - right is " + rightAligned + " for column anchored right margin " + lines[0].colRightMargin);
				centerAligned = (Math.abs(lines[0].colRightMargin - lines[0].colLeftMargin) < ((pageImageDpi + (13 / 2)) / 13)); // this has to be within 1/13 of an inch (2 mm)
				if (DEBUG_BLOCK_LAYOUT) System.out.println(" - center is " + centerAligned + " for column anchored line margin deviation " + (lines[0].colRightMargin - lines[0].colLeftMargin));
				
				//	TODO for accuracy, average out and use proportion of left and right margin in column over multiple centered single-line blocks
				
				//	TODO use minimum significant distance parameters here
			}
			
			//	use block internal measurements for multi-line blocks
			else {
				
				//	try accumulation points first
				int blockLeftAccPointCountSum = 0;
				int minBlockLeftAccPointCenter = Integer.MAX_VALUE;
				int maxBlockLeftAccPointCenter = 0;
				for (int a = 0; a < blockLeftAccPoints.length; a++) {
					blockLeftAccPointCountSum += blockLeftAccPoints[a].count;
					minBlockLeftAccPointCenter = Math.min(minBlockLeftAccPointCenter, blockLeftAccPoints[a].center);
					maxBlockLeftAccPointCenter = Math.max(maxBlockLeftAccPointCenter, blockLeftAccPoints[a].center);
				}
				if (lines.length < 4)
					leftAligned = (((blockLeftAccPoints.length != 0) && (blockLeftAccPoints.length < 2)) && (Math.abs(maxBlockLeftAccPointCenter - minBlockLeftAccPointCenter) < pageImageDpi) && ((lines.length * 2) <= (blockLeftAccPointCountSum * 3)));
				else if (lines.length < 10)
					leftAligned = (((blockLeftAccPoints.length != 0) && (blockLeftAccPoints.length <= 2)) && (Math.abs(maxBlockLeftAccPointCenter - minBlockLeftAccPointCenter) < pageImageDpi) && ((lines.length * 2) < (blockLeftAccPointCountSum * 3)));
//					leftAligned = (((blockLeftAccPoints.length != 0) && (blockLeftAccPoints.length <= 2)) && (Math.abs(maxBlockLeftAccPointCenter - minBlockLeftAccPointCenter) < Math.max(pageImageDpi, (blockWidth / 4))) && ((lines.length * 2) < (blockLeftAccPointCountSum * 3)));
				else leftAligned = (((blockLeftAccPoints.length != 0) && (blockLeftAccPoints.length <= 3)) && (Math.abs(maxBlockLeftAccPointCenter - minBlockLeftAccPointCenter) < pageImageDpi) && (lines.length < (blockLeftAccPointCountSum * 2)));
//				else leftAligned = (((blockLeftAccPoints.length != 0) && (blockLeftAccPoints.length <= 3)) && (Math.abs(maxBlockLeftAccPointCenter - minBlockLeftAccPointCenter) < Math.max(pageImageDpi, (blockWidth / 4))) && (lines.length < (blockLeftAccPointCountSum * 2)));
				if (DEBUG_BLOCK_LAYOUT) System.out.println(" - left is " + leftAligned + " for " + Arrays.toString(blockLeftAccPoints));
				if (blockRightAccPoints.length != 1)
					rightAligned = false; // no clear accumulation point of line ends
				else if (((lines.length * 8) < (blockRightAccPoints[0].count * 10)) && (blockRightAccPoints[0].getDiameter() < ((pageImageDpi + 13) / 25)))
					rightAligned = true; // single accumulation point with >80% support and diameter below (DPI/50) 
				else if (((lines.length - (blockShortLineCount + 1)) <= blockRightAccPoints[0].count) && (blockRightAccPoints[0].getDiameter() < ((pageImageDpi + 13) / 25)))
					rightAligned = true; // single accumulation point with support of all non-short lines and diameter below (DPI/50) 
				else rightAligned = (blockRightAccPoints[0].center < ((pageImageDpi + 13) / 25)); // single accumulation point below (DPI/25) off block edge
				if (DEBUG_BLOCK_LAYOUT) System.out.println(" - right is " + rightAligned + " for " + Arrays.toString(blockRightAccPoints));
				centerAligned = ((blockCenterAccPoints.length == 1) && ((lines.length * 2) < (blockCenterAccPoints[0].count * 3)));
				if (DEBUG_BLOCK_LAYOUT) System.out.println(" - center is " + centerAligned + " for " + Arrays.toString(blockCenterAccPoints));
				
				//	no clear result for accumulation points, fall back to left and right distances (as for single line)
				//	(CENTER ALIGNMENT IS OUT, as there is always an accumulation point with two or more lines)
				if (!leftAligned && !centerAligned && !rightAligned) {
					if (DEBUG_BLOCK_LAYOUT) System.out.println(" ==> re-evaluating due to inconclusive result from accumulation points");
					int minColLeftMargin = ((Integer) colLeftLineDists.first()).intValue();
					int minColRightMargin = ((Integer) colRightLineDists.first()).intValue();
					leftAligned = (minColLeftMargin < minSignificantHorizontalDist); // this has to be tight now (within 1/25 of an inch (1 mm) by default), as with all lines indented we'd have an accumulation point
					if (DEBUG_BLOCK_LAYOUT) System.out.println(" - left is " + leftAligned + " for minimum column anchored left margin " + minColLeftMargin);
					rightAligned = (minColRightMargin < minSignificantHorizontalDist); // this has to be tight (within 1/25 of an inch (1 mm) by default)
					if (DEBUG_BLOCK_LAYOUT) System.out.println(" - right is " + rightAligned + " for minimum column anchored right margin " + minColRightMargin);
					if (DEBUG_BLOCK_LAYOUT) System.out.println(" - center remains " + centerAligned + ", we'd have an accumulation point in that case");
				}
			}
			
			//	compute justification only now
			justified = (leftAligned && rightAligned);
			if (DEBUG_BLOCK_LAYOUT) System.out.println(" ==> justified is " + justified);
			
			//	figure out alignment (left, right, centered, or justified)
			char alignment;
			if (leftAligned && rightAligned)
				alignment = 'J';
			else if (centerAligned)
				alignment = 'C';
			else if (leftAligned)
				alignment = 'L';
			else if (rightAligned)
				alignment = 'R';
			else alignment = 'C';
			if (DEBUG_BLOCK_LAYOUT) System.out.println(" ==> alignment is " + alignment);
			
			//	assess line distance distribution (loop goes in increasing order)
			int maxBelowAvgLineGap = avgLineGap;
			int minAboveAvgLineGap = avgLineGap;
			for (Iterator lgit = lineGaps.iterator(); lgit.hasNext();) {
				int lineGap = ((Integer) (lgit.next())).intValue();
				if (lineGap <= avgLineGap)
					maxBelowAvgLineGap = lineGap;
				else {
					minAboveAvgLineGap = lineGap;
					break;
				}
			}
			int lineGapSplitThreshold;
			if (lineGapAccPoints.length == 1)
				lineGapSplitThreshold = (lineGapAccPoints[0].max + 1);
			else if (1 < lineGapAccPoints.length)
				lineGapSplitThreshold = (lineGapAccPoints[lineGapAccPoints.length - 2].max + 1);
			else lineGapSplitThreshold = -1;
			if (DEBUG_BLOCK_LAYOUT) {
				System.out.println(" - average line gap is " + avgLineGap);
				System.out.println(" - around average gap in line gaps is " + (minAboveAvgLineGap - maxBelowAvgLineGap) + " (" + maxBelowAvgLineGap + "/" + minAboveAvgLineGap + ")");
				System.out.println(" - line gap split threshold is " + lineGapSplitThreshold + " for " + Arrays.toString(lineGapAccPoints));
			}
			
			//	figure out where paragraphs start
			boolean[] isParagraphStartLine = new boolean[lines.length];
			Arrays.fill(isParagraphStartLine, false);
			String[] paragraphStartLineEvidence = new String[lines.length];
			Arrays.fill(paragraphStartLineEvidence, null);
			int paragraphStartLineCount = 0;
			
			//	apply line gap split if distribution has significant gap in middle
			//	TODOne make damn sure not to apply this if average is all too low (with line gaps choked by superscripts and/or subscripts)
			//	TODOne TEST 'diagnosis' paragraph on page 2 of EJT/ejt-428_sendra_weber.pdf.imf
			int minParagraphDist = -1;
			if ((minSignificantVerticalDist < (minAboveAvgLineGap - maxBelowAvgLineGap)) && (((pageImageDpi + 13) / 25) < avgLineGap)) {
				if (DEBUG_BLOCK_LAYOUT) System.out.println("Applying line gap split with threshold " + avgLineGap + " for distribution gap of " + (minAboveAvgLineGap - maxBelowAvgLineGap) + " (" + maxBelowAvgLineGap + "/" + minAboveAvgLineGap + ")");
				boolean[] isLineDistParagraphStart = getLineDistanceParagraphStarts(lines, aboveLineGaps, avgLineGap);
				addParagraphStartLineArrays(isParagraphStartLine, isLineDistParagraphStart, paragraphStartLineEvidence, "G");
				minParagraphDist = avgLineGap;
			}
			
			//	analyze short line split (it might actually be wrong in small blocks like article titles)
			if (DEBUG_BLOCK_LAYOUT) System.out.println("Analyzing short line split");
			boolean[] isShortLineParagraphStart = getShortLineParagraphStarts(lines, colWidth, blockWidth, alignment, normSpaceWidth, minSignificantHorizontalDist);
			boolean shortLineEndsParagraph;
			
			//	assess average space width in short lines and in non-short ones
			int shortLineSpaceWidthSum = 0;
			int shortLineSpaceCount = 0;
			int nonShortLineSpaceWidthSum = 0;
			int nonShortLineSpaceCount = 0;
			for (int l = 0; l < (lines.length - 1); l++) {
				int lineSpaceWidthSum = 0;
				int lineSpaceCount = 0;
				for (int w = 1; w < lines[l].words.length; w++) {
					int spaceWidth = (lines[l].words[w].bounds.left - lines[l].words[w-1].bounds.right);
					if (spaceWidth >= 2) {
						lineSpaceWidthSum += spaceWidth;
						lineSpaceCount++;
					}
				}
				if (isShortLineParagraphStart[l+1]) {
					shortLineSpaceWidthSum += lineSpaceWidthSum;
					shortLineSpaceCount += lineSpaceCount;
				}
				else {
					nonShortLineSpaceWidthSum += lineSpaceWidthSum;
					nonShortLineSpaceCount += lineSpaceCount;
				}
			}
			float avgShortLineSpaceWidth = ((shortLineSpaceCount == 0) ? (avgLineHeight / 3) /* ballpark figure space */ : (((float) shortLineSpaceWidthSum) / shortLineSpaceCount));
			if (DEBUG_BLOCK_LAYOUT) System.out.println(" - average space in short line is " + avgShortLineSpaceWidth + " (" + shortLineSpaceWidthSum + "/" + shortLineSpaceCount + ")");
			float avgNonShortLineSpaceWidth = ((nonShortLineSpaceCount == 0) ? (avgLineHeight / 3) /* ballpark figure space */ : (((float) nonShortLineSpaceWidthSum) / nonShortLineSpaceCount));
			if (DEBUG_BLOCK_LAYOUT) System.out.println(" - average space in non-short line is " + avgNonShortLineSpaceWidth + " (" + nonShortLineSpaceWidthSum + "/" + nonShortLineSpaceCount + ")");
			
			//	verify short line split against sentence continuations if latter present and conclusive
			if ((sentenceContinuingColLeftAccPoints.length != 0) && (sentenceContinuingColLeftAccPoints.length < colLeftAccPoints.length)) {
				if (DEBUG_BLOCK_LAYOUT) System.out.println(" - verifying short line split against sentence continuation accumulation points");
				int shortLineParagraphStartCount = 0;
				int shortLineParagraphStartIsContinuationCount = 0;
				for (int l = 1; l < lines.length; l++)
					if (isShortLineParagraphStart[l]) {
						shortLineParagraphStartCount++;
						if (lines[l].isSentenceContinuation)
							shortLineParagraphStartIsContinuationCount++;
						else if (lines[l].isUriContinuation)
							shortLineParagraphStartIsContinuationCount++;
					}
				if ((shortLineParagraphStartIsContinuationCount * 3) < shortLineParagraphStartCount)
					shortLineEndsParagraph = true; // we have a good majority of non-continuations
				else if ((9 < lines.length) && ((shortLineParagraphStartIsContinuationCount * 2) < shortLineParagraphStartCount))
					shortLineEndsParagraph = true; // we have at least a majority of non-continuations, and enough lines to be rather safe
				else shortLineEndsParagraph = false; // short line split hits all too many continuations
				if (DEBUG_BLOCK_LAYOUT) System.out.println(" ==> short line split mode is " + shortLineEndsParagraph + ", verified by " + shortLineParagraphStartCount + " line splits hitting " + shortLineParagraphStartIsContinuationCount + " sentence continuations out of " + sentenceContinuingColLineStarts.size());
			}
			
			//	verify short line split against indentation jumps if possible
			else if (colLeftAccPoints.length == 2) {
				int colLeftAccPointDist = Math.abs(colLeftAccPoints[1].min - colLeftAccPoints[0].max);
				int shortLineParagraphStartCount = 0;
				int shortLineParagraphStartIsShiftedCount = 0;
				int belowShortLineParagraphStartIsShiftedCount = 0;
				int nonShortLineParagraphStartIsShiftedCount = 0;
				for (int l = 1; l < (lines.length-1); l++) {
					int aboveLineColLeftShift = Math.abs(lines[l-1].colLeftMargin - lines[l].colLeftMargin);
					if (isShortLineParagraphStart[l])
						shortLineParagraphStartCount++;
					if ((aboveLineColLeftShift * 10) > (colLeftAccPointDist * 9)) {
						if (isShortLineParagraphStart[l])
							shortLineParagraphStartIsShiftedCount++;
						else if (isShortLineParagraphStart[l-1])
							belowShortLineParagraphStartIsShiftedCount++;
						else nonShortLineParagraphStartIsShiftedCount++;
					}
				}
				//	TODOne TEST left column on page 5 in Gulec2007.imf (sentence continuation assessment thrown off by many references to 'de Bonis')
				if ((shortLineParagraphStartIsShiftedCount * 3) > (shortLineParagraphStartCount * 2))
					shortLineEndsParagraph = true; // we have a good majority of indentation shifted line starts
				else if ((9 < lines.length) && ((shortLineParagraphStartIsShiftedCount * 2) > shortLineParagraphStartCount))
					shortLineEndsParagraph = true; // we have at least a majority of indentation shifts, and enough lines to be rather safe
				else if ((nonShortLineParagraphStartIsShiftedCount == 0) && (shortLineParagraphStartIsShiftedCount != 0) && ((belowShortLineParagraphStartIsShiftedCount - shortLineParagraphStartIsShiftedCount) <= 1))
					shortLineEndsParagraph = true; // all shifts coincide with short line paragraph starts and have shift-backs right afterwards, we just have very few shifts at all
				else shortLineEndsParagraph = false; // short line split hits all too many continuations
				if (DEBUG_BLOCK_LAYOUT) System.out.println(" ==> short line split mode is " + shortLineEndsParagraph + ", verified by " + shortLineParagraphStartIsShiftedCount + " indentation shifted short line paragraph starts out of " + shortLineParagraphStartCount + ", " + belowShortLineParagraphStartIsShiftedCount + " indentation shift-backs, " + nonShortLineParagraphStartIsShiftedCount + " other indentation shifted line starts");
			}
			
			//	assume short line split valid on straight left edge in absence of line gap split (we have preciously little else to go at ...)
			else if (minParagraphDist == -1) {
				shortLineEndsParagraph = ("JL".indexOf(alignment) != -1);
				if (DEBUG_BLOCK_LAYOUT) System.out.println(" ==> short line split mode is " + shortLineEndsParagraph + " for lack of alternatives");
			}
			
			//	assume short line split valid on straight left edge justified layout
			else {
				shortLineEndsParagraph = (alignment == 'J');
				if (DEBUG_BLOCK_LAYOUT) System.out.println(" ==> short line split mode is " + shortLineEndsParagraph + " for justified layout");
			}
			
			//	apply short line split
			if (shortLineEndsParagraph) {
				if (DEBUG_BLOCK_LAYOUT) System.out.println("Applying short line split");
				paragraphStartLineCount += addParagraphStartLineArrays(isParagraphStartLine, isShortLineParagraphStart, paragraphStartLineEvidence, "S");
			}
			
			//	analyze around URI line split
			if (DEBUG_BLOCK_LAYOUT) System.out.println("Analyzing URI line split");
			boolean[] isUriLineParagraphStart = getUriLineParagraphStarts(lines, 'R');
			int uriLineCount = 0;
			int aboveUriLineSplitVerifiedCount = 0;
			int belowUriLineSplitVerifiedCount = 0;
			int belowUriLineSplitIsContinuationCount = 0;
			for (int l = 0; l < lines.length; l++) {
				if (isParagraphStartLine[l] && isUriLineParagraphStart[l]) {
					if (lines[l].isUri) {
						uriLineCount++;
						aboveUriLineSplitVerifiedCount++;
					}
					else if ((l != 0) && lines[l-1].isUri)
						belowUriLineSplitVerifiedCount++;
				}
				if ((l != 0) && lines[l-1].isUri) {
					if (lines[l].isSentenceContinuation)
						belowUriLineSplitIsContinuationCount++;
					else if (lines[l].isUriContinuation)
						belowUriLineSplitIsContinuationCount++;
				}
			}
			char uriLineSplitMode;
			if ((lines.length < 10) && (belowUriLineSplitIsContinuationCount == 0))
				uriLineSplitMode = ((uriLineCount == 0) ? 'N' : 'R'); // too little basis for conclusive verification, be aggressive unless we have hit a continuation
			else if ((aboveUriLineSplitVerifiedCount != 0) && (belowUriLineSplitVerifiedCount != 0))
				uriLineSplitMode = 'R';
			else if (aboveUriLineSplitVerifiedCount != 0)
				uriLineSplitMode = 'A';
			else if (belowUriLineSplitVerifiedCount != 0)
				uriLineSplitMode = 'B';
			else uriLineSplitMode = 'N';
			if (DEBUG_BLOCK_LAYOUT) System.out.println(" ==> URI line split mode is " + uriLineSplitMode + ", verified by " + aboveUriLineSplitVerifiedCount + " line splits above and " + belowUriLineSplitVerifiedCount + " below, hitting " + belowUriLineSplitIsContinuationCount + " continuations");
			
			//	apply around URI line split if possible
			if ("RAB".indexOf(uriLineSplitMode) != -1) {
				if (DEBUG_BLOCK_LAYOUT) System.out.println("Applying URI line split");
				isUriLineParagraphStart = getUriLineParagraphStarts(lines, uriLineSplitMode);
				addParagraphStartLineArrays(isParagraphStartLine, isUriLineParagraphStart, paragraphStartLineEvidence, "U");
			}
			
			//	determine left alignment of paragraph start lines (indent/outdent/neither)
			char paragraphStartLinePos;
			
			//	in center alignment, we cannot use indent/outdent
			if (alignment == 'C')
				paragraphStartLinePos = 'N';
			
			//	determine and use indent/outdent
			else {
				
				//	TODO move individual approaches to dedicated methods ...
				//	TODO ... apply them all if possible ...
				//	TODO ... and use some kind of vote afterwards
				
				/* TODO if we have two left accumulation points
				 * - analyze (sentence) continuations, like now
				 * - analyze presence of (sorted) sequences in starts of outdented lines
				 *   - sorted sequences of line starts (maybe ignoring repeated intermediate punctuation ... keys !!!)
				 *   - repeating punctuation marks (especially dashes and bulletin points)
				 * - favor indent if no sequences or repeating puntuation marks found
				 * 
				 * ==> helps with enumerations of points
				 * ==> helps with punctuation marked lists of bulletin points
				 * ==> helps with bibliographies
				 * ==> helps with taxonomic keys
				 * 
				 * TODO for tractability, add '_indentationEvidence' attribute
				 */
				
				/* TODO on significant rightward deviation of top-most lines, consider splitting block proper
				 * ==> should help splitting centered headings from top of main text blocks
				 * ==> maybe try and measure similarly for bottom-most lines
				 */
				
				/* TODO on significant breaks in font size or weight, consider splitting block proper
				 * ==> should help splitting bold headings from top of main text blocks
				 */
				
				//	detect indent/outdent
				int avgParagraphStartColLeftLineMargin;
				int avgInParagraphColLeftLineMargin;
				if (DEBUG_BLOCK_LAYOUT) System.out.println("Analyzing indent/outdent");
				
				//	no paragraph starts at all thus far, resort to sentence continuation lines
				if (paragraphStartLineCount == 0) {
					
					//	no sentence continuation accumulation point to work with
					if (sentenceContinuingColLeftAccPoints.length == 0) {
						
						//	if we have a single left margin accumulation point, work with deviation from this point (if any)
						if (colLeftAccPoints.length == 1) {
							int minColLeftLineMargin = ((Integer) colLeftLineDists.first()).intValue();
							int maxColLeftLineMargin = ((Integer) colLeftLineDists.last()).intValue();
							int minColLeftLineMarginDist = Math.abs(colLeftAccPoints[0].center - minColLeftLineMargin);
							int maxColLeftLineMarginDist = Math.abs(maxColLeftLineMargin - colLeftAccPoints[0].center);
							
							//	minimum significantly closer to sentence continuations ==> use maximum for paragraph start lines
							if (minSignificantHorizontalDist <= (maxColLeftLineMarginDist - minColLeftLineMarginDist)) {
								avgParagraphStartColLeftLineMargin = maxColLeftLineMargin;
								avgInParagraphColLeftLineMargin = colLeftAccPoints[0].center;
							}
							
							//	maximum significantly closer to sentence continuations ==> use minimum for paragraph start lines
							else if (minSignificantHorizontalDist <= (minColLeftLineMarginDist - maxColLeftLineMarginDist)) {
								avgParagraphStartColLeftLineMargin = minColLeftLineMargin;
								avgInParagraphColLeftLineMargin = colLeftAccPoints[0].center;
							}
							
							//	no way of calling this one
							else {
								avgParagraphStartColLeftLineMargin = avgColLeftLineMargin;
								avgInParagraphColLeftLineMargin = avgColLeftLineMargin;
							}
						}
						
						//	if we have multiple left margin accumulation points, work with majority vote
						else if (colLeftAccPoints.length == 2) {
							
							//	use smaller accumulation point as paragraph start line margin
							if (colLeftAccPoints[0].count < colLeftAccPoints[1].count) {
								avgParagraphStartColLeftLineMargin = colLeftAccPoints[0].center;
								avgInParagraphColLeftLineMargin = colLeftAccPoints[1].center;
							}
							else if (colLeftAccPoints[1].count < colLeftAccPoints[0].count) {
								avgParagraphStartColLeftLineMargin = colLeftAccPoints[1].center;
								avgInParagraphColLeftLineMargin = colLeftAccPoints[0].center;
							}
							
							//	assume indent on equality TODO figure out if this really does the trick
							else {
								avgParagraphStartColLeftLineMargin = colLeftAccPoints[0].center;
								avgInParagraphColLeftLineMargin = colLeftAccPoints[1].center;
							}
						}
						
						//	set both averages to average left margin, as we have nothing to go on
						else {
							avgParagraphStartColLeftLineMargin = avgColLeftLineMargin;
							avgInParagraphColLeftLineMargin = avgColLeftLineMargin;
						}
					}
					
					//	work with sentence continuation accumulation point
					else {
						
						//	TODO also assess how many _subsequent_ lines start in each accumulation point
						//	TODO TEST left column on page 5 in Gulec2007.imf (sentence continuation assessment thrown off by many references to 'de Bonis')
						
						//	if we have a single left margin accumulation point, work with deviation from this point
						if (colLeftAccPoints.length == 1) {
							
							//	margin accumulation point overlaps with sentence continuations ==> use deviation from that point as line start
							if (colLeftAccPoints[0].overlapsWith(sentenceContinuingColLeftAccPoints[0])) {
								int minColLeftLineMargin = ((Integer) colLeftLineDists.first()).intValue();
								int maxColLeftLineMargin = ((Integer) colLeftLineDists.last()).intValue();
								int minColLeftLineMarginDist = Math.abs(colLeftAccPoints[0].center - minColLeftLineMargin);
								int maxColLeftLineMarginDist = Math.abs(maxColLeftLineMargin - colLeftAccPoints[0].center);
								
								//	minimum significantly closer to sentence continuations ==> use maximum for paragraph start lines
								if (minSignificantHorizontalDist <= (maxColLeftLineMarginDist - minColLeftLineMarginDist)) {
									avgParagraphStartColLeftLineMargin = maxColLeftLineMargin;
									avgInParagraphColLeftLineMargin = colLeftAccPoints[0].center;
								}
								
								//	maximum significantly closer to sentence continuations ==> use minimum for paragraph start lines
								else if (minSignificantHorizontalDist <= (minColLeftLineMarginDist - maxColLeftLineMarginDist)) {
									avgParagraphStartColLeftLineMargin = minColLeftLineMargin;
									avgInParagraphColLeftLineMargin = colLeftAccPoints[0].center;
								}
								
								//	no way of calling this one
								else {
									avgParagraphStartColLeftLineMargin = avgColLeftLineMargin;
									avgInParagraphColLeftLineMargin = avgColLeftLineMargin;
								}
							}
							
							//	margin accumulation point distinct from sentence continuations ==> use accumulation point as line start
							else {
								avgParagraphStartColLeftLineMargin = colLeftAccPoints[0].center;
								avgInParagraphColLeftLineMargin = sentenceContinuingColLeftAccPoints[0].center;
							}
						}
						
						//	we have multiple left margin accumulation points, work with majority, as well as with above sentence continuation stats
						else if (colLeftAccPoints.length == 2) {
							
							//	first margin accumulation point overlaps with sentence continuations ==> use second margin accumulation point as line start
							if (colLeftAccPoints[0].overlapsWith(sentenceContinuingColLeftAccPoints[0])) {
								avgParagraphStartColLeftLineMargin = colLeftAccPoints[1].center;
								avgInParagraphColLeftLineMargin = colLeftAccPoints[0].center;
							}
							
							//	first margin accumulation point distinct from sentence continuations ==> use first accumulation point as line start
							else {
								avgParagraphStartColLeftLineMargin = colLeftAccPoints[0].center;
								avgInParagraphColLeftLineMargin = colLeftAccPoints[1].center;
							}
						}
						
						//	use min and max column anchored left margin in comparison to sentence continuation accumulation point
						else {
							int minColLeftLineMargin = ((Integer) colLeftLineDists.first()).intValue();
							int maxColLeftLineMargin = ((Integer) colLeftLineDists.last()).intValue();
							int minColLeftLineMarginDist = Math.abs(sentenceContinuingColLeftAccPoints[0].center - minColLeftLineMargin);
							int maxColLeftLineMarginDist = Math.abs(maxColLeftLineMargin - sentenceContinuingColLeftAccPoints[0].center);
							
							//	minimum significantly closer to sentence continuations ==> use maximum for paragraph start lines
							if (minSignificantHorizontalDist <= (maxColLeftLineMarginDist - minColLeftLineMarginDist)) {
								avgParagraphStartColLeftLineMargin = maxColLeftLineMargin;
								avgInParagraphColLeftLineMargin = sentenceContinuingColLeftAccPoints[0].center;
							}
							
							//	maximum significantly closer to sentence continuations ==> use minimum for paragraph start lines
							else if (minSignificantHorizontalDist <= (minColLeftLineMarginDist - maxColLeftLineMarginDist)) {
								avgParagraphStartColLeftLineMargin = minColLeftLineMargin;
								avgInParagraphColLeftLineMargin = sentenceContinuingColLeftAccPoints[0].center;
							}
							
							//	no way of calling this one
							else {
								avgParagraphStartColLeftLineMargin = avgColLeftLineMargin;
								avgInParagraphColLeftLineMargin = avgColLeftLineMargin;
							}
						}
					}
				}
				
				//	use previously identified paragraph start lines
				else {
					int paragraphStartColLeftLineMarginSum = 0;
					int paragraphStartColLeftLineMarginCount = 0;
					int inParagraphColLeftLineMarginSum = 0;
					int inParagraphColLeftLineMarginCount = 0;
					for (int l = 1; l < lines.length; l++) {
						if (isParagraphStartLine[l]) {
							paragraphStartColLeftLineMarginSum += lines[l].colLeftMargin;
							paragraphStartColLeftLineMarginCount++;
						}
						else {
							inParagraphColLeftLineMarginSum += lines[l].colLeftMargin;
							inParagraphColLeftLineMarginCount++;
						}
					}
					avgParagraphStartColLeftLineMargin = ((paragraphStartColLeftLineMarginSum + (paragraphStartColLeftLineMarginCount / 2)) / paragraphStartColLeftLineMarginCount);
					avgInParagraphColLeftLineMargin = ((inParagraphColLeftLineMarginCount == 0) ? avgColLeftLineMargin : ((inParagraphColLeftLineMarginSum + (inParagraphColLeftLineMarginCount / 2)) / inParagraphColLeftLineMarginCount));
					if (DEBUG_BLOCK_LAYOUT) {
						System.out.println(" - average column anchored left margin of paragraph start lines is " + avgParagraphStartColLeftLineMargin + " based upon " + paragraphStartColLeftLineMarginCount + " paragraph start lines");
						System.out.println(" - average column anchored left margin of in-paragraph lines is " + avgInParagraphColLeftLineMargin + " based upon " + inParagraphColLeftLineMarginCount + " in-paragraph lines");
					}
					
					//	double-check against sentence continuations
					if (sentenceContinuingColLeftAccPoints.length == 1) {
						int paragraphStartLineSentenceContinuationDist = Math.abs(sentenceContinuingColLeftAccPoints[0].center - avgParagraphStartColLeftLineMargin);
						int inParagraphLineSentenceContinuationDist = Math.abs(sentenceContinuingColLeftAccPoints[0].center - avgInParagraphColLeftLineMargin);
						if (DEBUG_BLOCK_LAYOUT) {
							System.out.println(" - average column anchored left margin of paragraph start lines is " + paragraphStartLineSentenceContinuationDist + " from sentence continuations at " + sentenceContinuingColLeftAccPoints[0].center);
							System.out.println(" - average column anchored left margin of in-paragraph lines is " + inParagraphLineSentenceContinuationDist + " from sentence continuations at " + sentenceContinuingColLeftAccPoints[0].center);
						}
						
						//	paragraph start lines closer to sentence continuations than in-paragraph lines, WTF ?!? ==> reset to neutral
						if (paragraphStartLineSentenceContinuationDist < inParagraphLineSentenceContinuationDist) {
							avgParagraphStartColLeftLineMargin = avgColLeftLineMargin;
							avgInParagraphColLeftLineMargin = avgColLeftLineMargin;
							if (DEBUG_BLOCK_LAYOUT) System.out.println(" --> impossible to tell any difference");
						}
					}
				}
				if (Math.abs(avgParagraphStartColLeftLineMargin - avgInParagraphColLeftLineMargin) < minSignificantHorizontalDist)
					paragraphStartLinePos = 'N';
				else if (avgParagraphStartColLeftLineMargin < avgInParagraphColLeftLineMargin)
					paragraphStartLinePos = 'O';
				else paragraphStartLinePos = 'I';
				if (DEBUG_BLOCK_LAYOUT) System.out.println(" ==> paragraph start line position is " + paragraphStartLinePos);
				
				//	use indent/outdent (if any)
				if (paragraphStartLinePos != 'N') {
					if (DEBUG_BLOCK_LAYOUT) System.out.println("Applying indent/outdent split");
					int thresholdColLeftLineMargin;
					if (paragraphStartLineCount == 0) {
						int minColLeftLineMargin = Integer.MAX_VALUE;
						int maxColLeftLineMargin = -1;
						int maxColLeftNonShortLineMargin = -1;
						int maxColLeftSentendeContinuingLineMargin = -1;
						for (int l = 0; l < lines.length; l++) {
							minColLeftLineMargin = Math.min(minColLeftLineMargin, lines[l].colLeftMargin);
							maxColLeftLineMargin = Math.max(maxColLeftLineMargin, lines[l].colLeftMargin);
							if (((l+1) < lines.length) && !isColShortLine[l])
								maxColLeftNonShortLineMargin = Math.max(maxColLeftNonShortLineMargin, lines[l].colLeftMargin);
							if (lines[l].isSentenceContinuation)
								maxColLeftSentendeContinuingLineMargin = Math.max(maxColLeftSentendeContinuingLineMargin, lines[l].colLeftMargin);
						}
						if (paragraphStartLinePos == 'I') {
							if ((pageImageDpi / 2) < maxColLeftNonShortLineMargin)
								maxColLeftNonShortLineMargin = (pageImageDpi / 2); // cap off at half inch (usual indent is quarter inch) to catch OCR missing some line start word
							thresholdColLeftLineMargin = ((minColLeftLineMargin + maxColLeftNonShortLineMargin) / 2);
							if (DEBUG_BLOCK_LAYOUT) System.out.println(" - threshold is " + thresholdColLeftLineMargin + " from average column-anchored left line margins " + minColLeftLineMargin + " and " + maxColLeftNonShortLineMargin);
						}
						else if (paragraphStartLinePos == 'O') {
							if ((pageImageDpi / 2) < maxColLeftSentendeContinuingLineMargin)
								maxColLeftSentendeContinuingLineMargin = (pageImageDpi / 2); // cap off at half inch (usual indent is quarter inch) to catch OCR missing some line start word
							if ((pageImageDpi / 2) < maxColLeftLineMargin)
								maxColLeftLineMargin = (pageImageDpi / 2); // cap off at half inch (usual indent is quarter inch) to catch OCR missing some line start word
							if (maxColLeftSentendeContinuingLineMargin == -1) {
								thresholdColLeftLineMargin = ((minColLeftLineMargin + maxColLeftLineMargin) / 2);
								if (DEBUG_BLOCK_LAYOUT) System.out.println(" - threshold is " + thresholdColLeftLineMargin + " from average column-anchored left line margins " + minColLeftLineMargin + " and " + maxColLeftLineMargin);
							}
							else {
								thresholdColLeftLineMargin = ((minColLeftLineMargin + maxColLeftSentendeContinuingLineMargin) / 2);
								if (DEBUG_BLOCK_LAYOUT) System.out.println(" - threshold is " + thresholdColLeftLineMargin + " from average column-anchored left line margins " + minColLeftLineMargin + " and " + maxColLeftSentendeContinuingLineMargin);
							}
						}
						else thresholdColLeftLineMargin = avgColLeftLineMargin; // just to satisfy compiler ...
					}
					else {
						thresholdColLeftLineMargin = ((avgParagraphStartColLeftLineMargin + avgInParagraphColLeftLineMargin) / 2);
						if (DEBUG_BLOCK_LAYOUT) System.out.println(" - threshold is " + thresholdColLeftLineMargin + " from average column-anchored left line margins " + avgParagraphStartColLeftLineMargin + " and " + avgInParagraphColLeftLineMargin);
					}
					boolean[] isIndentOutdentParagraphStart = getIndentOutdentParagraphStarts(lines, paragraphStartLinePos, thresholdColLeftLineMargin);
					paragraphStartLineCount += addParagraphStartLineArrays(isParagraphStartLine, isIndentOutdentParagraphStart, paragraphStartLineEvidence, "I");
				}
			}
			
			//	determine in-line heading style
			CountingSet inLineHeadingStyles = new CountingSet(new TreeMap());
			CountingSet inLineHeadingTerminators = new CountingSet(new TreeMap());
			for (int l = 0; l < lines.length; l++) {
				if (lines[l].inLineHeading == null)
					continue;
				inLineHeadingStyles.add(lines[l].inLineHeadingStyle);
				inLineHeadingTerminators.add(lines[l].inLineHeading.substring(lines[l].inLineHeading.length() - 1));
			}
			String inLineHeadingStyle = (inLineHeadingStyles.isEmpty() ? null : ((String) getMostFrequentElement(inLineHeadingStyles)));
			char inLineHeadingTerminator = (inLineHeadingTerminators.isEmpty() ? ((char) 0) : ((String) getMostFrequentElement(inLineHeadingTerminators)).charAt(0));
			
			//	require at least one other split above some in-line heading for verification
			if (inLineHeadingStyle != null) {
				boolean[] isInLineHeadingParagraphStart = getInLineHeadingParagraphStarts(lines, inLineHeadingStyle, inLineHeadingTerminator);
				int inLineHeadingsVerified = 0;
				for (int l = 0; l < lines.length; l++) {
					if (isInLineHeadingParagraphStart[l] && isParagraphStartLine[l])
						inLineHeadingsVerified++;
				}
				if (inLineHeadingsVerified == 0) {
					inLineHeadingStyle = null;
					inLineHeadingTerminator = ((char) 0);
					if (DEBUG_BLOCK_LAYOUT) {
						System.out.println(" ==> in-line heading style is " + inLineHeadingStyle + ", failed to verify by any lines");
						System.out.println(" ==> in-line heading terminator is " + inLineHeadingTerminator + ", failed to verify by any lines");
					}
				}
				else if (DEBUG_BLOCK_LAYOUT) {
					System.out.println(" ==> in-line heading style is " + inLineHeadingStyle + " (in " + inLineHeadingStyles.getCount(inLineHeadingStyle) + " lines), verified by " + inLineHeadingsVerified + " lines");
					System.out.println(" ==> in-line heading terminator is " + inLineHeadingTerminator + " (in " + inLineHeadingTerminators.getCount("" + inLineHeadingTerminator) + " lines), verified by " + inLineHeadingsVerified + " lines");
				}
			}
			
			//	nothing found
			else if (DEBUG_BLOCK_LAYOUT) {
				System.out.println(" ==> in-line heading style is " + inLineHeadingStyle + ", no in-line headings found");
				System.out.println(" ==> in-line heading terminator is " + inLineHeadingTerminator + ", no in-line headings found");
			}
			
			//	apply in-line heading split (if possible)
			if (inLineHeadingStyle != null) {
				System.out.println("Applying in-line heading split");
				boolean[] isInLineHeadingParagraphStart = getInLineHeadingParagraphStarts(lines, inLineHeadingStyle, inLineHeadingTerminator);
				paragraphStartLineCount += addParagraphStartLineArrays(isParagraphStartLine, isInLineHeadingParagraphStart, paragraphStartLineEvidence, "H");
			}
			
			//	make sure to not tamper with very first line (no basis for telling reliably)
			ImWord previousLineEndWord = lines[0].words[0].getPreviousWord();
			isParagraphStartLine[0] = ((previousLineEndWord == null) || (previousLineEndWord.getNextRelation() == ImWord.NEXT_RELATION_PARAGRAPH_END));
			
			//	apply split line patterns
			if (splitLinePatterns != null) {
				System.out.println("Applying line pattern split");
				String[] isPatternParagraphStart = getPatternParagraphStarts(lines, alignment, splitLinePatterns);
				for (int l = 0; l < isParagraphStartLine.length; l++) {
					if (isPatternParagraphStart[l] == null)
						continue;
					if (!isParagraphStartLine[l]) // add to count only if not confirmed before
						paragraphStartLineCount++;
					isParagraphStartLine[l] = true;
					paragraphStartLineEvidence[l] = ((paragraphStartLineEvidence[l] == null) ? ("P-" + isPatternParagraphStart[l]) : (paragraphStartLineEvidence[l] + "P-" + isPatternParagraphStart[l]));
				}
			}
			
			//	finally ...
			return new BlockLayout(null, alignment, paragraphStartLinePos, shortLineEndsParagraph, uriLineSplitMode, inLineHeadingStyle, inLineHeadingTerminator, minParagraphDist, isParagraphStartLine, paragraphStartLineEvidence);
		}
		
		/**
		 * Analyze the (paragraph) layout of the block. Use predetermined layout
		 * parameters.
		 * @param alignment the text alignment in the block, i.e., 'L' for left
		 *        alignment, 'R' for right alignment, 'C' for center alignment,
		 *        or 'J' for justified
		 * @param paragraphStartLinePos the positioning or left margin of the
		 *        first line of paragraphs, i.e., 'I' for indentation, 'O' for
		 *        outdentation, or 'N' for neutral
		 * @param shortLineEndsParagraph split the block after short lines?
		 * @param inLineHeadingStyle style for any in-line headings, i.e., 'B'
		 *        for bold, 'C' for all-caps
		 * @param inLineHeadingTerminator the punctuation character terminating
		 *        in-line headings
		 * @param minParagraphDist minimum distance between two lines to indicate
		 *        a paragraph split
		 * @param uriLineSplitMode the paragraph splitting behavior for lines
		 *        that solely consist of a URI, i.e., 'R' for around (both above
		 *        and below), 'A' for above, 'B' for below, or 'N' for neither
		 * @return the resulting block layout
		 */
		public BlockLayout analyze(char alignment, char paragraphStartLinePos, boolean shortLineEndsParagraph, String inLineHeadingStyle, char inLineHeadingTerminator, int minParagraphDist, char uriLineSplitMode) {
			return this.doAnalyze(null, -1, alignment, paragraphStartLinePos, shortLineEndsParagraph, -1, -1, inLineHeadingStyle, inLineHeadingTerminator, minParagraphDist, uriLineSplitMode);
		}
		
		/**
		 * Analyze the (paragraph) layout of the block. Use predetermined layout
		 * parameters.
		 * @param alignment the text alignment in the block, i.e., 'L' for left
		 *        alignment, 'R' for right alignment, 'C' for center alignment,
		 *        or 'J' for justified
		 * @param paragraphStartLinePos the positioning or left margin of the
		 *        first line of paragraphs, i.e., 'I' for indentation, 'O' for
		 *        outdentation, or 'N' for neutral
		 * @param shortLineEndsParagraph split the block after short lines?
		 * @param normSpaceWidth the width of a normal space as a fraction of
		 *        the height of a line
		 * @param minSignificantHorizontalDist the minimum number of pixels
		 *        between two horizontal measurements for them to be considered
		 *        intentionally distinct rather than a result of inaccuracy
		 * @param inLineHeadingStyle style for any in-line headings, i.e., 'B'
		 *        for bold, 'C' for all-caps
		 * @param inLineHeadingTerminator the punctuation character terminating
		 *        in-line headings
		 * @param minParagraphDist minimum distance between two lines to indicate
		 *        a paragraph split
		 * @param uriLineSplitMode the paragraph splitting behavior for lines
		 *        that solely consist of a URI, i.e., 'R' for around (both above
		 *        and below), 'A' for above, 'B' for below, or 'N' for neither
		 * @return the resulting block layout
		 */
		public BlockLayout analyze(char alignment, char paragraphStartLinePos, boolean shortLineEndsParagraph, float normSpaceWidth, int minSignificantHorizontalDist, String inLineHeadingStyle, char inLineHeadingTerminator, int minParagraphDist, char uriLineSplitMode) {
			return this.doAnalyze(null, -1, alignment, paragraphStartLinePos, shortLineEndsParagraph, normSpaceWidth, minSignificantHorizontalDist, inLineHeadingStyle, inLineHeadingTerminator, minParagraphDist, uriLineSplitMode);
		}
		
		/**
		 * Analyze the (paragraph) layout of the block, virtually merging it up
		 * with a predecessor block from a preceding page or column. This method
		 * produces an analysis result as if the two blocks were one, and not
		 * split up due to page layout constraints. Use predetermined layout
		 * parameters.
		 * @param predecessorBlockMetrics the block to continue from
		 * @param blockMargin the space to assume between the two blocks
		 * @param alignment the text alignment in the block, i.e., 'L' for left
		 *        alignment, 'R' for right alignment, 'C' for center alignment,
		 *        or 'J' for justified
		 * @param paragraphStartLinePos the positioning or left margin of the
		 *        first line of paragraphs, i.e., 'I' for indentation, 'O' for
		 *        outdentation, or 'N' for neutral
		 * @param shortLineEndsParagraph split the block after short lines?
		 * @param inLineHeadingStyle style for any in-line headings, i.e., 'B'
		 *        for bold, 'C' for all-caps
		 * @param inLineHeadingTerminator the punctuation character terminating
		 *        in-line headings
		 * @param minParagraphDist minimum distance between two lines to indicate
		 *        a paragraph split
		 * @param uriLineSplitMode the paragraph splitting behavior for lines
		 *        that solely consist of a URI, i.e., 'R' for around (both above
		 *        and below), 'A' for above, 'B' for below, or 'N' for neither
		 * @return the analysis result
		 */
		public BlockLayout analyzeContinuingFrom(BlockMetrics predecessorBlockMetrics, int blockMargin, char alignment, char paragraphStartLinePos, boolean shortLineEndsParagraph, String inLineHeadingStyle, char inLineHeadingTerminator, int minParagraphDist, char uriLineSplitMode) {
			return this.doAnalyze(predecessorBlockMetrics, blockMargin, alignment, paragraphStartLinePos, shortLineEndsParagraph, -1, -1, inLineHeadingStyle, inLineHeadingTerminator, minParagraphDist, uriLineSplitMode);
		}
		
		/**
		 * Analyze the (paragraph) layout of the block, virtually merging it up
		 * with a predecessor block from a preceding page or column. This method
		 * produces an analysis result as if the two blocks were one, and not
		 * split up due to page layout constraints. Use predetermined layout
		 * parameters.
		 * @param predecessorBlockMetrics the block to continue from
		 * @param blockMargin the space to assume between the two blocks
		 * @param alignment the text alignment in the block, i.e., 'L' for left
		 *        alignment, 'R' for right alignment, 'C' for center alignment,
		 *        or 'J' for justified
		 * @param paragraphStartLinePos the positioning or left margin of the
		 *        first line of paragraphs, i.e., 'I' for indentation, 'O' for
		 *        outdentation, or 'N' for neutral
		 * @param shortLineEndsParagraph split the block after short lines?
		 * @param normSpaceWidth the width of a normal space as a fraction of
		 *        the height of a line
		 * @param minSignificantHorizontalDist the minimum number of pixels
		 *        between two horizontal measurements for them to be considered
		 *        intentionally distinct rather than a result of inaccuracy
		 * @param inLineHeadingStyle style for any in-line headings, i.e., 'B'
		 *        for bold, 'C' for all-caps
		 * @param inLineHeadingTerminator the punctuation character terminating
		 *        in-line headings
		 * @param minParagraphDist minimum distance between two lines to indicate
		 *        a paragraph split
		 * @param uriLineSplitMode the paragraph splitting behavior for lines
		 *        that solely consist of a URI, i.e., 'R' for around (both above
		 *        and below), 'A' for above, 'B' for below, or 'N' for neither
		 * @return the analysis result
		 */
		public BlockLayout analyzeContinuingFrom(BlockMetrics predecessorBlockMetrics, int blockMargin, char alignment, char paragraphStartLinePos, boolean shortLineEndsParagraph, float normSpaceWidth, int minSignificantHorizontalDist, String inLineHeadingStyle, char inLineHeadingTerminator, int minParagraphDist, char uriLineSplitMode) {
			return this.doAnalyze(predecessorBlockMetrics, blockMargin, alignment, paragraphStartLinePos, shortLineEndsParagraph, normSpaceWidth, minSignificantHorizontalDist, inLineHeadingStyle, inLineHeadingTerminator, minParagraphDist, uriLineSplitMode);
		}
		
		private BlockLayout doAnalyze(BlockMetrics predecessorBlockMetrics, int blockMargin, char alignment, char paragraphStartLinePos, boolean shortLineEndsParagraph, float normSpaceWidth, int minSignificantHorizontalDist, String inLineHeadingStyle, char inLineHeadingTerminator, int minParagraphDist, char uriLineSplitMode) {
			
			//	compute parameters
			LineMetrics[] lines;
			int[] aboveLineGaps;
			int colWidth;
			int blockWidth;
			
			//	for single block analysis
			if (predecessorBlockMetrics == null) {
				lines = this.lines;
				aboveLineGaps = this.aboveLineGaps;
				colWidth = this.column.bounds.getWidth();
				blockWidth = this.block.bounds.getWidth();
			}
			
			//	for block continuation analysis
			else {
				lines = new LineMetrics[predecessorBlockMetrics.lines.length + this.lines.length];
				System.arraycopy(predecessorBlockMetrics.lines, 0, lines, 0, predecessorBlockMetrics.lines.length);
				System.arraycopy(this.lines, 0, lines, predecessorBlockMetrics.lines.length, this.lines.length);
				
				aboveLineGaps = new int[predecessorBlockMetrics.lines.length + this.lines.length];
				System.arraycopy(predecessorBlockMetrics.aboveLineGaps, 0, aboveLineGaps, 0, predecessorBlockMetrics.aboveLineGaps.length);
				System.arraycopy(this.aboveLineGaps, 0, aboveLineGaps, predecessorBlockMetrics.lines.length, this.aboveLineGaps.length);
				aboveLineGaps[predecessorBlockMetrics.lines.length] = blockMargin;
				
				colWidth = Math.max(predecessorBlockMetrics.column.bounds.getWidth(), this.column.bounds.getWidth());
				blockWidth = Math.max(predecessorBlockMetrics.block.bounds.getWidth(), this.block.bounds.getWidth());
			}
			
			//	use default space width and significant horizontal distance if required
			if (normSpaceWidth < 0)
				normSpaceWidth = 0.33f;
			if (minSignificantHorizontalDist < 0) {
				int minSignificantHorizontalDistDenom = 25; // makes the tolerance about a millimeter
				minSignificantHorizontalDist = ((this.pageImageDpi + (minSignificantHorizontalDistDenom / 2)) / minSignificantHorizontalDistDenom);
			}
			
			//	perform actual analysis
			BlockLayout blockLayout = doAnalyze(lines, aboveLineGaps, this.pageImageDpi, colWidth, blockWidth, alignment, paragraphStartLinePos, shortLineEndsParagraph, normSpaceWidth, minSignificantHorizontalDist, inLineHeadingStyle, inLineHeadingTerminator, minParagraphDist, uriLineSplitMode);
			
			//	create final result
			if (predecessorBlockMetrics == null)
				return new BlockLayout(this, blockLayout.alignment, blockLayout.paragraphStartLinePos, blockLayout.shortLineEndsParagraph, blockLayout.uriLineSplitMode, blockLayout.inLineHeadingStyle, blockLayout.inLineHeadingTerminator, blockLayout.paragraphDistance, blockLayout.isParagraphStartLine, blockLayout.paragraphStartLineEvidence);
			
			else {
				boolean[] predecessorIsParagraphStartLine = new boolean[predecessorBlockMetrics.lines.length];
				System.arraycopy(blockLayout.isParagraphStartLine, 0, predecessorIsParagraphStartLine, 0, predecessorIsParagraphStartLine.length);
				boolean[] isParagraphStartLine = new boolean[this.lines.length];
				System.arraycopy(blockLayout.isParagraphStartLine, predecessorBlockMetrics.lines.length, isParagraphStartLine, 0, isParagraphStartLine.length);
				
				String[] predecessorParagraphStartLineEvidence = new String[predecessorBlockMetrics.lines.length];
				System.arraycopy(blockLayout.paragraphStartLineEvidence, 0, predecessorParagraphStartLineEvidence, 0, predecessorParagraphStartLineEvidence.length);
				String[] paragraphStartLineEvidence = new String[this.lines.length];
				System.arraycopy(blockLayout.paragraphStartLineEvidence, predecessorBlockMetrics.lines.length, paragraphStartLineEvidence, 0, paragraphStartLineEvidence.length);
				
				return new BlockLayout(predecessorBlockMetrics, this, blockLayout.alignment, blockLayout.paragraphStartLinePos, blockLayout.shortLineEndsParagraph, blockLayout.uriLineSplitMode, blockLayout.inLineHeadingStyle, blockLayout.inLineHeadingTerminator, blockLayout.paragraphDistance, predecessorIsParagraphStartLine, predecessorParagraphStartLineEvidence, isParagraphStartLine, paragraphStartLineEvidence);
			}
		}
		
		//	TODO use progress monitor instead of System.out
		
		private static BlockLayout doAnalyze(LineMetrics[] lines, int[] aboveLineGaps, int pageImageDpi, int colWidth, int blockWidth, char alignment, char paragraphStartLinePos, boolean shortLineEndsParagraph, float normSpaceWidth, int minSignificantHorizontalDist, String inLineHeadingStyle, char inLineHeadingTerminator, int minParagraphDist, char uriLineSplitMode) {
			//	TODO facilitate specifying custom split line patterns
			//	TODO specify (per pattern): maximum number of lines in target block, text orientation in target block, and split position (A, B, or R)
			
			//	assess column anchored left line margins and line distance
			int colLeftLineMarginSum = 0;
			for (int l = 0; l < lines.length; l++) {
				if (DEBUG_BLOCK_LAYOUT) {
					System.out.println(" - '" + lines[l].words[0] + "': " + lines[l].colLeftMargin + "/" + lines[l].colRightMargin + " in column, " + lines[l].blockLeftMargin + "/" + lines[l].blockRightMargin + " in block");
					System.out.println("   start word(s) are " + lines[l].startWordBlockBounds.getWidth() + " long");
				}
				colLeftLineMarginSum += lines[l].colLeftMargin;
			}
			int avgColLeftLineMargin = ((colLeftLineMarginSum + (lines.length / 2)) / lines.length);
			
			//	figure out where paragraphs start
			boolean[] isParagraphStartLine = new boolean[lines.length];
			Arrays.fill(isParagraphStartLine, false);
			String[] paragraphStartLineEvidence = new String[lines.length];
			Arrays.fill(paragraphStartLineEvidence, null);
			
			//	apply line gap split if distribution has significant gap in middle
			if (minParagraphDist != -1) {
				if (DEBUG_BLOCK_LAYOUT) System.out.println("Applying line gap split with threshold " + minParagraphDist);
				boolean[] isLineDistParagraphStart = getLineDistanceParagraphStarts(lines, aboveLineGaps, minParagraphDist);
				addParagraphStartLineArrays(isParagraphStartLine, isLineDistParagraphStart, paragraphStartLineEvidence, "G");
			}
			
			//	apply around URI line split
			if ("RAB".indexOf(uriLineSplitMode) != -1) {
				if (DEBUG_BLOCK_LAYOUT) System.out.println("Applying URI line split");
				boolean[] isUriLineParagraphStart = getUriLineParagraphStarts(lines, uriLineSplitMode);
				addParagraphStartLineArrays(isParagraphStartLine, isUriLineParagraphStart, paragraphStartLineEvidence, "U");
			}
			
			//	apply short line split
			boolean[] isShortLineParagraphStart = null;
			if (shortLineEndsParagraph) {
				if (DEBUG_BLOCK_LAYOUT) System.out.println("Applying short line split");
				isShortLineParagraphStart = getShortLineParagraphStarts(lines, colWidth, blockWidth, alignment, normSpaceWidth, minSignificantHorizontalDist);
				addParagraphStartLineArrays(isParagraphStartLine, isShortLineParagraphStart, paragraphStartLineEvidence, "S");
			}
			
			//	use indent/outdent (if any)
			if ("IO".indexOf(paragraphStartLinePos) != -1) {
				if (DEBUG_BLOCK_LAYOUT) System.out.println("Applying indent/outdent split");
				int paragraphStartColLeftLineMarginSum = 0;
				int paragraphStartLineCount = 0;
				int inParagraphColLeftLineMarginSum = 0;
				int inParagraphLineCount = 0;
				for (int l = 0; l < lines.length; l++) {
					if (isParagraphStartLine[l]) {
						paragraphStartColLeftLineMarginSum += lines[l].colLeftMargin;
						paragraphStartLineCount++;
					}
					else {
						inParagraphColLeftLineMarginSum += lines[l].colLeftMargin;
						inParagraphLineCount++;
					}
				}
				int thresholdColLeftLineMargin;
				if ((paragraphStartLineCount == 0) || (inParagraphLineCount == 0)) {
					if (isShortLineParagraphStart == null) // compute this on demand if not done before
						isShortLineParagraphStart = getShortLineParagraphStarts(lines, colWidth, blockWidth, alignment, normSpaceWidth, minSignificantHorizontalDist);
					int minColLeftLineMargin = Integer.MAX_VALUE;
					int maxColLeftLineMargin = -1;
					int maxColLeftNonShortLineMargin = -1;
					int maxColLeftSentendeContinuingLineMargin = -1;
					for (int l = 0; l < lines.length; l++) {
						minColLeftLineMargin = Math.min(minColLeftLineMargin, lines[l].colLeftMargin);
						maxColLeftLineMargin = Math.max(maxColLeftLineMargin, lines[l].colLeftMargin);
						if (((l+1) < lines.length) && !isShortLineParagraphStart[l+1])
							maxColLeftNonShortLineMargin = Math.max(maxColLeftNonShortLineMargin, lines[l].colLeftMargin);
						if (lines[l].isSentenceContinuation)
							maxColLeftSentendeContinuingLineMargin = Math.max(maxColLeftSentendeContinuingLineMargin, lines[l].colLeftMargin);
					}
					if (paragraphStartLinePos == 'I') {
						if ((pageImageDpi / 2) < maxColLeftNonShortLineMargin)
							maxColLeftNonShortLineMargin = (pageImageDpi / 2); // cap off at half inch (usual indent is quarter inch) to catch OCR missing some line start word
						thresholdColLeftLineMargin = ((minColLeftLineMargin + maxColLeftNonShortLineMargin) / 2);
						if (DEBUG_BLOCK_LAYOUT) System.out.println(" - threshold is " + thresholdColLeftLineMargin + " from average column-anchored left line margins " + minColLeftLineMargin + " and " + maxColLeftNonShortLineMargin);
					}
					else if (paragraphStartLinePos == 'O') {
						if ((pageImageDpi / 2) < maxColLeftSentendeContinuingLineMargin)
							maxColLeftSentendeContinuingLineMargin = (pageImageDpi / 2); // cap off at half inch (usual indent is quarter inch) to catch OCR missing some line start word
						if ((pageImageDpi / 2) < maxColLeftLineMargin)
							maxColLeftLineMargin = (pageImageDpi / 2); // cap off at half inch (usual indent is quarter inch) to catch OCR missing some line start word
						if (maxColLeftSentendeContinuingLineMargin == -1) {
							thresholdColLeftLineMargin = ((minColLeftLineMargin + maxColLeftLineMargin) / 2);
							if (DEBUG_BLOCK_LAYOUT) System.out.println(" - threshold is " + thresholdColLeftLineMargin + " from average column-anchored left line margins " + minColLeftLineMargin + " and " + maxColLeftLineMargin);
						}
						else {
							thresholdColLeftLineMargin = ((minColLeftLineMargin + maxColLeftSentendeContinuingLineMargin) / 2);
							if (DEBUG_BLOCK_LAYOUT) System.out.println(" - threshold is " + thresholdColLeftLineMargin + " from average column-anchored left line margins " + minColLeftLineMargin + " and " + maxColLeftSentendeContinuingLineMargin);
						}
					}
					else thresholdColLeftLineMargin = avgColLeftLineMargin; // just to satisfy compiler ...
				}
				else {
					int avgParagraphStartColLeftLineMargin = ((paragraphStartColLeftLineMarginSum + (paragraphStartLineCount / 2)) / paragraphStartLineCount);
					int avgInParagraphColLeftLineMargin = ((inParagraphColLeftLineMarginSum + (inParagraphLineCount / 2)) / inParagraphLineCount);
					thresholdColLeftLineMargin = ((avgParagraphStartColLeftLineMargin + avgInParagraphColLeftLineMargin) / 2);
					if (DEBUG_BLOCK_LAYOUT) System.out.println(" - threshold is " + thresholdColLeftLineMargin + " from average column-anchored left line margins " + avgParagraphStartColLeftLineMargin + " and " + avgInParagraphColLeftLineMargin);
				}
				boolean[] isIndentOutdentParagraphStartLine = getIndentOutdentParagraphStarts(lines, paragraphStartLinePos, thresholdColLeftLineMargin);
				addParagraphStartLineArrays(isParagraphStartLine, isIndentOutdentParagraphStartLine, paragraphStartLineEvidence, "I");
			}
			
			//	use in-line headings (if any)
			if (inLineHeadingStyle != null) {
				if (DEBUG_BLOCK_LAYOUT) System.out.println("Applying in-line heading split");
				boolean[] isInLineHeadingParagraphStartLine = getInLineHeadingParagraphStarts(lines, inLineHeadingStyle, inLineHeadingTerminator);
				addParagraphStartLineArrays(isParagraphStartLine, isInLineHeadingParagraphStartLine, paragraphStartLineEvidence, "H");
			}
			
			//	make sure to not tamper with very first line (no basis for telling reliably)
			ImWord previousLineEndWord = lines[0].words[0].getPreviousWord();
			isParagraphStartLine[0] = ((previousLineEndWord == null) || (previousLineEndWord.getNextRelation() == ImWord.NEXT_RELATION_PARAGRAPH_END));
			
			//	finally ...
			return new BlockLayout(null, alignment, paragraphStartLinePos, shortLineEndsParagraph, uriLineSplitMode, inLineHeadingStyle, inLineHeadingTerminator, minParagraphDist, isParagraphStartLine, paragraphStartLineEvidence);
		}
		
		//	TODO use progress monitor instead of System.out
		
		private static boolean[] getLineDistanceParagraphStarts(LineMetrics[] lines, int[] aboveLineGaps, int lineGapThreshold) {
			
			//	measure lines
			boolean[] isParagraphStartLine = new boolean[lines.length];
			Arrays.fill(isParagraphStartLine, false);
			for (int l = 1; l < lines.length; l++) {
				if (lineGapThreshold < aboveLineGaps[l]) {
					isParagraphStartLine[l] = true;
					if (DEBUG_BLOCK_LAYOUT) System.out.println(" - identified paragraph start line with gap of " + aboveLineGaps[l] + " to predecessor: " + ImUtils.getString(lines[l].words, true));
				}
			}
			
			//	return result
			return isParagraphStartLine;
		}
		
		//	TODO use progress monitor instead of System.out
		
		private static boolean[] getUriLineParagraphStarts(LineMetrics[] lines, char uriLineSplitMode) {
			
			//	measure lines
			boolean[] isParagraphStartLine = new boolean[lines.length];
			Arrays.fill(isParagraphStartLine, false);
			for (int l = 0; l < lines.length; l++) {
				if (lines[l].isUri && ("RA".indexOf(uriLineSplitMode) != -1)) {
					isParagraphStartLine[l] = true;
					if (DEBUG_BLOCK_LAYOUT) System.out.println(" - identified URI line paragraph: " + ImUtils.getString(lines[l].words, true));
				}
				else if ((l != 0) && lines[l-1].isUri && !lines[l].isUriContinuation && ("RB".indexOf(uriLineSplitMode) != -1)) {
					isParagraphStartLine[l] = true;
					System.out.println(" - identified post URI paragraph start line: " + ImUtils.getString(lines[l].words, true));
				}
			}
			
			//	return result
			return isParagraphStartLine;
		}
		
		//	TODO use progress monitor instead of System.out
		
		private static boolean[] getShortLineParagraphStarts(LineMetrics[] lines, int colWidth, int blockWidth, char alignment, float normSpaceWidth, int minSignificantHorizontalDist) {
			
			//	measure lines
			boolean[] isParagraphStartLine = new boolean[lines.length];
			Arrays.fill(isParagraphStartLine, false);
			for (int l = 1; l < lines.length; l++) {
				
				//	determine space left in preceding line
				int spaceLeftInPrevLine;
				
				//	center alignment ==> consider both left and right margins
				if (alignment == 'C') {
					
					//	block at least 80% of column width, measure by block (to catch narrow-ish section abstracts)
					if ((colWidth * 4) < (blockWidth * 5))
						spaceLeftInPrevLine = (lines[l-1].blockLeftMargin + lines[l-1].blockRightMargin);
					
					//	measure by column otherwise
					else spaceLeftInPrevLine = (lines[l-1].colLeftMargin + lines[l-1].colRightMargin);
				}
				
				//	justified alignment ==> consider only right margin
				else if (alignment == 'J') {
					
					//	block at least 80% of column width, measure by block (to catch narrow-ish section abstracts)
					if ((colWidth * 4) < (blockWidth * 5))
						spaceLeftInPrevLine = lines[l-1].blockRightMargin;
					
					//	measure by column otherwise
					else spaceLeftInPrevLine = lines[l-1].colRightMargin;
				}
				
				//	left alignment ==> consider right column anchored margin
				else if (alignment == 'L')
					spaceLeftInPrevLine = lines[l-1].colRightMargin;
				
				//	right alignment ==> consider left column anchored margin
				else if (alignment == 'R')
					spaceLeftInPrevLine = lines[l-1].colLeftMargin;
				
				//	just to satisfy compiler, as the above four options cover all the cases (we just want them all labeled)
				else continue;
				
				//	measure if extended line start word block would have fit preceding line
				int extStartWordBlockWidth = (Math.round((lines[l].line.bounds.getHeight() * normSpaceWidth) /* ballpark figure space */ + lines[l].extStartWordBlockBounds.getWidth()));
				if (extStartWordBlockWidth <= spaceLeftInPrevLine) {
					isParagraphStartLine[l] = true;
					if (DEBUG_BLOCK_LAYOUT) System.out.println(" - identified paragraph start line with short predecessor (extended start is " + extStartWordBlockWidth + ", predecessor vacant space is " + spaceLeftInPrevLine + "): " + ImUtils.getString(lines[l].words, true));
					continue;
				}
//				
//				//	measure if line start word block would have fit preceding line
//				//	DO NOT USE: all this will catch is a few short lines in paragraph-wise enumerations, and we get these via outdent
//				int startWordBlockWidth = (Math.round((lines[l].line.bounds.getHeight() * normSpaceWidth) /* ballpark figure space */ + lines[l].startWordBlockBounds.getWidth()));
//				if (startWordBlockWidth <= spaceLeftInPrevLine) {
//					isParagraphStartLine[l] = true;
//					paragraphStartLineCount++;
//					System.out.println(" - identified paragraph start line with short predecessor (start is " + startWordBlockWidth + ", predecessor vacant space is " + spaceLeftInPrevLine + "): " + ImUtils.getString(lines[l].words, true));
//					continue;
//				}
				
				//	measure violation of justification in combination with sentence end
				if ((alignment == 'J') && (minSignificantHorizontalDist < spaceLeftInPrevLine) && Gamta.isSentenceEnd(lines[l-1].words[lines[l-1].words.length - 1].getString())) {
					isParagraphStartLine[l] = true;
					if (DEBUG_BLOCK_LAYOUT) System.out.println(" - identified paragraph start line with sentence ending non-justified predecessor (start is " + extStartWordBlockWidth + ", predecessor vacant space is " + spaceLeftInPrevLine + "): " + ImUtils.getString(lines[l].words, true));
					continue;
				}
			}
			
			//	return result
			return isParagraphStartLine;
		}
		
		//	TODO use progress monitor instead of System.out
		
		private static boolean[] getIndentOutdentParagraphStarts(LineMetrics[] lines, char paragraphStartLinePos, int thresholdLineLeftColMargin) {
			
			//	measure lines
			boolean[] isParagraphStartLine = new boolean[lines.length];
			Arrays.fill(isParagraphStartLine, false);
			for (int l = 1; l < lines.length; l++) {
				
				//	indent ==> paragraph starts where left line margin is above threshold
				if ((paragraphStartLinePos == 'I') && (thresholdLineLeftColMargin < lines[l].colLeftMargin)) {
					isParagraphStartLine[l] = true;
					if (DEBUG_BLOCK_LAYOUT) System.out.println(" - identified paragraph start line with indent " + lines[l].colLeftMargin + " > " + thresholdLineLeftColMargin + ": " + ImUtils.getString(lines[l].words, true));
				}
				
				//	outdent ==> paragraph starts where left line margin is below threshold
				else if ((paragraphStartLinePos == 'O') && (lines[l].colLeftMargin < thresholdLineLeftColMargin)) {
					isParagraphStartLine[l] = true;
					if (DEBUG_BLOCK_LAYOUT) System.out.println(" - identified paragraph start line with outdent " + lines[l].colLeftMargin + " < " + thresholdLineLeftColMargin + ": " + ImUtils.getString(lines[l].words, true));
				}
			}
			
			//	return result
			return isParagraphStartLine;
		}
		
		//	TODO use progress monitor instead of System.out
		
		private static boolean[] getInLineHeadingParagraphStarts(LineMetrics[] lines, String inLineHeadingStyle, char inLineHeadingTerminator) {
			
			//	assess lines
			boolean[] isParagraphStartLine = new boolean[lines.length];
			Arrays.fill(isParagraphStartLine, false);
			for (int l = 1; l < lines.length; l++) {
				
				//	no in-line heading candidate
				if (lines[l].inLineHeading == null)
					continue;
				
				//	terminating punctuation mis-match
				if (!lines[l].inLineHeading.endsWith("" + inLineHeadingTerminator))
					continue;
				
				//	style mis-match
				if (!inLineHeadingStyle.equals(lines[l].inLineHeadingStyle))
					continue;
				
				//	style compatible in-line heading ==> paragraph starts
				isParagraphStartLine[l] = true;
				if (DEBUG_BLOCK_LAYOUT) System.out.println(" - identified paragraph start line with leading heading: " + ImUtils.getString(lines[l].words, true));
			}
			
			//	return result
			return isParagraphStartLine;
		}
		
		//	TODO use progress monitor instead of System.out
		
		private static String[] getPatternParagraphStarts(LineMetrics[] lines, char alignment, LinePattern[] splitLinePatterns) {
			String[] isParagraphStartLine = new String[lines.length];
			Arrays.fill(isParagraphStartLine, null);
			for (int p = 0; p < splitLinePatterns.length; p++) {
				
				//	check alignment
				if (!splitLinePatterns[p].matchesParagraphOrientation(BlockLayout.getTextOrientation(alignment)))
					continue;
				
				//	check block size
				String maxBlockLines = splitLinePatterns[p].getParameter("MBL");
				if (maxBlockLines != null) try {
					if (Integer.parseInt(maxBlockLines) < lines.length)
						continue;
				} catch (NumberFormatException nfe) {}
				
				//	check page ID
				String minPageId = splitLinePatterns[p].getParameter("MINPID");
				if (minPageId != null) try {
					if (lines[0].line.pageId < Integer.parseInt(minPageId))
						continue;
				} catch (NumberFormatException nfe) {}
				String maxPageId = splitLinePatterns[p].getParameter("MAXPID");
				if (maxPageId != null) try {
					if (Integer.parseInt(maxPageId) < lines[0].line.pageId)
						continue;
				} catch (NumberFormatException nfe) {}
				
				//	get split direction and pattern reason
				String splitDirection = splitLinePatterns[p].getParameter("SD");
				if ((splitDirection == null) || (splitDirection.length() == 0))
					continue;
				boolean splitAbove = (splitDirection.indexOf('A') != -1);
				boolean splitBelow = (splitDirection.indexOf('B') != -1);
				String reason = splitLinePatterns[p].getParameter("R");
				if (reason == null)
					reason = ("Pattern" + (p+1));
				
				//	assess lines
				for (int l = 0; l < lines.length; l++) {
					
					//	check font size
					if (!splitLinePatterns[p].matchesFontSize(lines[l].words))
						continue;
					
					//	check font properties
					if (!splitLinePatterns[p].matchesStartFontProperties(lines[l].words))
						continue;
					if (!splitLinePatterns[p].matchesFontProperties(lines[l].words))
						continue;
					
					//	check pattern proper
					if (!splitLinePatterns[p].matchesString(lines[l].words))
						continue;
					
					//	we have a match ==> paragraph starts
					if (splitAbove) {
						isParagraphStartLine[l] = ((isParagraphStartLine[l] == null) ? reason : (isParagraphStartLine[l] + "-" + reason));
						if (DEBUG_BLOCK_LAYOUT) System.out.println(" - identified paragraph start line matching pattern '" + reason + "' to split above: " + ImUtils.getString(lines[l].words, true));
					}
					if (splitBelow && ((l+1) < lines.length)) {
						isParagraphStartLine[l+1] = ((isParagraphStartLine[l+1] == null) ? reason : (isParagraphStartLine[l+1] + "-" + reason));
						if (DEBUG_BLOCK_LAYOUT) System.out.println(" - identified paragraph start line matching pattern '" + reason + "' to split below: " + ImUtils.getString(lines[l+1].words, true));
					}
				}
			}
			
			//	return result
			return isParagraphStartLine;
		}
		
		private static int addParagraphStartLineArrays(boolean[] isParagraphStartLine, boolean[] setParagraphStartLine, String[] paragraphStartLineEvidence, String evidence) {
			int newParagraphStartLines = 0;
			for (int l = 0; l < isParagraphStartLine.length; l++)
				if (setParagraphStartLine[l]) {
					if (!isParagraphStartLine[l]) // add to count only if not confirmed before
						newParagraphStartLines++;
					isParagraphStartLine[l] = true;
					paragraphStartLineEvidence[l] = ((paragraphStartLineEvidence[l] == null) ? evidence : (paragraphStartLineEvidence[l] + evidence));
				}
			return newParagraphStartLines;
		}
	}
	
	private static class AccumulationPoint implements Comparable {
		final int min;
		final int max;
		final int count;
		final int[] counts;
		final int center;
		AccumulationPoint(int min, int max, CountingSet indexCounts) {
			this.min = min;
			this.max = max;
			this.counts = new int[this.max - this.min + 1];
			int count = 0;
			int countWeightedIndexSum = 0;
			for (Iterator iit = indexCounts.iterator(); iit.hasNext();) {
				Integer i = ((Integer) iit.next());
				if (i.intValue() < this.min)
					continue;
				if (this.max < i.intValue())
					break;
				int iCount = indexCounts.getCount(i);
				count += iCount;
				this.counts[i.intValue() - this.min] = iCount;
				countWeightedIndexSum += (i.intValue() * iCount);
			}
			this.count = count;
			if (countWeightedIndexSum < 0) // can happen with line distances in tables (before table detection)
				this.center = ((countWeightedIndexSum - (count / 2)) / count);
			else this.center = ((countWeightedIndexSum + (count / 2)) / count);
		}
		AccumulationPoint(int min, int[] counts) {
			this.min = min;
			this.max = (this.min + counts.length - 1);
			this.counts = counts;
			int count = 0;
			int countWeightedIndexSum = 0;
			for (int c = 0; c < this.counts.length; c++) {
				count += this.counts[c];
				countWeightedIndexSum += ((this.min + c) * this.counts[c]);
			}
			this.count = count;
			if (countWeightedIndexSum < 0) // can happen with line distances in tables (before table detection)
				this.center = ((countWeightedIndexSum - (count / 2)) / count);
			else this.center = ((countWeightedIndexSum + (count / 2)) / count);
		}
		public String toString() {
			return ("" + this.min + "/" + this.max + " (" + this.count + " " + Arrays.toString(this.counts) + ") ==> avg " + this.center);
		}
		int getDiameter() {
			return (this.max - this.min);
		}
		float getCenterDistanceScore() {
			int localCenter = (this.center - this.min);
			float score = 0;
			for (int c = 0; c < this.counts.length; c++) {
				int centerDist = Math.abs(c - localCenter);
				score += ((this.counts[c] * this.counts[c]) / Math.max(1, centerDist));
			}
			score /= Math.max(1, (this.max - this.min)); // minimum of one (a) levels ground for rounding errors and (b) prevents arithmetic problems
			return score;
		}
		float getRadiusScore() {
			float score = 0;
			for (int c = 0; c < this.counts.length; c++)
				score += (this.counts[c] * this.counts[c]);
			score /= Math.max(1, (this.max - this.min)); // minimum of one (a) levels ground for rounding errors and (b) prevents arithmetic problems
			return score;
		}
		float getCentralityScore() {
			int localCenter = (this.center - this.min);
			float score = 0;
			for (int c = 0; c < this.counts.length; c++) {
				int centerDist = Math.abs(c - localCenter);
				score += (((float) this.counts[c]) / Math.max(1, centerDist));
			}
			score /= this.count;
			return score;
		}
		int getBellCentralityScore() {
			int localCenter = (this.center - this.min);
			int score = 0;
			score += this.counts[0]; // score left edge drop to 0
			for (int c = 0; c < this.counts.length; c++) {
				int centerDist = Math.abs(c - localCenter);
				if (c < localCenter) {
					int drop = (this.counts[c+1] - this.counts[c]); // TODO maybe use maximum of two counts to right
					score += (drop * ((drop < 0) ? centerDist : 1)); // only punish rise weighted by center distance
				}
				else if (c == localCenter) {
					score += this.counts[c];
				}
				else {
					int drop = (this.counts[c-1] - this.counts[c]); // TODO maybe use maximum of two counts to left
					score += (drop * ((drop < 0) ? centerDist : 1)); // only punish rise weighted by center distance
				}
			}
			score += this.counts[this.counts.length - 1]; // score right edge drop to 0
			score *= this.count;
			return score;
		}
		public int compareTo(Object obj) {
			AccumulationPoint acc = ((AccumulationPoint) obj);
			return ((this.min == acc.min) ? (acc.max - this.max) : (this.min - acc.min));
		}
		boolean overlapsWith(AccumulationPoint ap) {
			return ((this.min <= ap.max) && (ap.min <= this.max));
		}
	}
	
	private static AccumulationPoint[] getAccumulationPoints(CountingSet indexCounts, int maxIndexDist, float minCountFract, int minCount) {
		
		//	collect accumulation points
		ArrayList accumulationPoints = new ArrayList();
		ArrayList indices = new ArrayList(indexCounts);
		Collections.sort(indices); // make damn sure of increasing order (should be OK with TreeMap base CountingSet, but let's be safe)
		for (int i = 0; i < indices.size(); i++) {
			Integer index = ((Integer) indices.get(i));
			int lastCompIndex = index.intValue();
			for (int ci = i; ci < indices.size(); ci++) {
				Integer cIndex = ((Integer) indices.get(ci));
				if (maxIndexDist < (cIndex.intValue() - lastCompIndex))
					break;
				AccumulationPoint accPoint = new AccumulationPoint(index.intValue(), cIndex.intValue() , indexCounts);
				if (accPoint.count < (indexCounts.size() * minCountFract))
					continue;
				if (accPoint.count < minCount)
					continue;
				if (DEBUG_BLOCK_LAYOUT) {
					System.out.println(" - got promising accumulation point: " + accPoint);
					System.out.println("   - center distance score is " + accPoint.getCenterDistanceScore());
					System.out.println("   - radius score is " + accPoint.getRadiusScore());
					System.out.println("   - centrality score is " + accPoint.getCentralityScore());
					System.out.println("   - bell centrality score is " + accPoint.getBellCentralityScore());
				}
				if (0 < accPoint.getBellCentralityScore()) // sort out accumulation points with negative score
					accumulationPoints.add(accPoint);
				lastCompIndex = cIndex.intValue();
			}
		}
		
		//	sort out accumulation points covered by larger and higher scoring ones
		Collections.sort(accumulationPoints);
		for (int a = 0; a < accumulationPoints.size(); a++) {
			AccumulationPoint accPoint = ((AccumulationPoint) accumulationPoints.get(a));
			int apScore = accPoint.getBellCentralityScore();
			for (int ca = (a+1); ca < accumulationPoints.size(); ca++) {
				AccumulationPoint cAccPoint = ((AccumulationPoint) accumulationPoints.get(ca));
				if (accPoint.max < cAccPoint.min)
					break;
				if (accPoint.max < cAccPoint.max) {
					if (accPoint.center < cAccPoint.min)
						continue;
					if (accPoint.max < cAccPoint.center)
						continue;
				}
				int cApScore = cAccPoint.getBellCentralityScore();
				if (apScore < cApScore) {
					if (DEBUG_BLOCK_LAYOUT) System.out.println(" - eliminated accumulation point: " + accPoint);
					accumulationPoints.remove(a--);
					break;
				}
				else {
					if (DEBUG_BLOCK_LAYOUT) System.out.println(" - eliminated accumulation point: " + cAccPoint);
					accumulationPoints.remove(ca--);
				}
			}
		}
		
		//	finally ...
		return ((AccumulationPoint[]) accumulationPoints.toArray(new AccumulationPoint[accumulationPoints.size()]));
	}
	
	private static AccumulationPoint[] squashCloseAccumulationPoints(AccumulationPoint[] accPoints, int maxSquashDist) {
		
		//	little to squash here
		if (accPoints.length < 2)
			return accPoints;
		
		//	start with all accumulation points
		ArrayList accumulationPoints = new ArrayList(Arrays.asList(accPoints));
		
		//	squash close pairs of accumulation points within range
		int accPointCount;
		do {
			accPointCount = accumulationPoints.size();
			for (int a = 0; a < (accumulationPoints.size() - 1); a++) {
				AccumulationPoint accPoint = ((AccumulationPoint) accumulationPoints.get(a));
				AccumulationPoint cAccPoint = ((AccumulationPoint) accumulationPoints.get(a + 1));
				
				//	compute distance
				int centerDist = (cAccPoint.center - accPoint.center);
				if (maxSquashDist < centerDist)
					continue; // these two are too far apart
				
				//	check relation of current pair center distance to center distances on far sides
				int leftCenterDist = ((a == 0) ? Short.MAX_VALUE : (accPoint.center - ((AccumulationPoint) accumulationPoints.get(a - 1)).center));
				if (leftCenterDist < (centerDist * 3))
					continue; // distance to left not sufficiently distinctive
				int rightCenterDist = (((a + 2) < accumulationPoints.size()) ? (((AccumulationPoint) accumulationPoints.get(a + 2)).center - cAccPoint.center) : Short.MAX_VALUE);
				if (rightCenterDist < (centerDist * 3))
					continue; // distance to right not sufficiently distinctive
				
				//	perform squash
				int[] sAccPointCounts = new int[cAccPoint.max - accPoint.min + 1];
				Arrays.fill(sAccPointCounts, 0);
				for (int c = 0; c < accPoint.counts.length; c++)
					sAccPointCounts[c] += accPoint.counts[c];
				for (int c = 0; c < cAccPoint.counts.length; c++)
					sAccPointCounts[c + (cAccPoint.min - accPoint.min)] += cAccPoint.counts[c];
				AccumulationPoint sAccPoint = new AccumulationPoint(accPoint.min, sAccPointCounts);
				//	TODO make sure of min and max order
				accumulationPoints.remove(a+1); // remove second squashed accumulation point
				accumulationPoints.set(a--, sAccPoint); // replace first squashed accumulation point with squash result, compensating for loop increment
			}
		} while (accumulationPoints.size() < accPointCount);
		
		if (DEBUG_BLOCK_LAYOUT)
			for (int a = 0; a < accumulationPoints.size(); a++) {
				AccumulationPoint accPoint = ((AccumulationPoint) accumulationPoints.get(a));
				System.out.println(" - got post-squash accumulation point: " + accPoint);
				System.out.println("   - center distance score is " + accPoint.getCenterDistanceScore());
				System.out.println("   - radius score is " + accPoint.getRadiusScore());
				System.out.println("   - centrality score is " + accPoint.getCentralityScore());
				System.out.println("   - bell centrality score is " + accPoint.getBellCentralityScore());
			}
		
		//	finally ...
		return ((AccumulationPoint[]) accumulationPoints.toArray(new AccumulationPoint[accumulationPoints.size()]));
	}
	
	private static AccumulationPoint[] mergeAccumulationPoints(AccumulationPoint[] accPoints1, AccumulationPoint[] accPoints2) {
		
		//	nothing to merge
		if (accPoints1.length == 0)
			return accPoints2;
		if (accPoints2.length == 0)
			return accPoints1;
		
		//	start with all points
		ArrayList accumulationPoints = new ArrayList();
		accumulationPoints.addAll(Arrays.asList(accPoints1));
		accumulationPoints.addAll(Arrays.asList(accPoints2));
		Collections.sort(accumulationPoints);
		
		//	sort out duplicate accumulation points
		int accPointCount;
		do {
			accPointCount = accumulationPoints.size();
			for (int a = 0; a < (accumulationPoints.size() - 1); a++) {
				AccumulationPoint accPoint = ((AccumulationPoint) accumulationPoints.get(a));
				AccumulationPoint cAccPoint = ((AccumulationPoint) accumulationPoints.get(a + 1));
				
				//	remove lower-support accumulation point in case of overlap
				if (accPoint.overlapsWith(cAccPoint))
					accumulationPoints.remove(a-- + ((accPoint.count < cAccPoint.count) ? 0 : 1));
			}
		} while (accumulationPoints.size() < accPointCount);
		
		if (DEBUG_BLOCK_LAYOUT)
			for (int a = 0; a < accumulationPoints.size(); a++) {
				AccumulationPoint accPoint = ((AccumulationPoint) accumulationPoints.get(a));
				System.out.println(" - got post-merge accumulation point: " + accPoint);
				System.out.println("   - center distance score is " + accPoint.getCenterDistanceScore());
				System.out.println("   - radius score is " + accPoint.getRadiusScore());
				System.out.println("   - centrality score is " + accPoint.getCentralityScore());
				System.out.println("   - bell centrality score is " + accPoint.getBellCentralityScore());
			}
		
		//	finally ...
		return ((AccumulationPoint[]) accumulationPoints.toArray(new AccumulationPoint[accumulationPoints.size()]));
	}
	
	private static final boolean DEBUG_BLOCK_LAYOUT = true;
	
	/**
	 * Result of block metrics analysis with specific parameters.
	 *
	 * @author sautter
	 */
	public static class BlockLayout {
		
		/** the metrics of the predecessor block; null except for cross-block analysis results */
		public final BlockMetrics predecessorBlockMetrics;
		
		/** the parent block metrics that computed the layout */
		public final BlockMetrics blockMetrics;
		
		/** the text alignment in the block, i.e., 'L' for left alignment, 'R' for right alignment, 'C' for center alignment, or 'J' for justified */
		public final char alignment;
		
		/** the positioning or left margin of the first line of paragraphs, i.e., 'I' for indentation, 'O' for outdentation, or 'N' for neutral */
		public final char paragraphStartLinePos;
		
		/** start a new paragraph after a line that is short? */
		public final boolean shortLineEndsParagraph;
		
		/** the mode of splitting at URI lines, 'A' for above, 'B' for below, and 'R' for around */
		public final char uriLineSplitMode;
		
		/** the style of in-line headings (if any), containing 'B' for bold, 'C' for all-caps, and 'I' for italics (null if none) */
		public final String inLineHeadingStyle;
		
		/** the terminating punctuation mark of in-line headings (if any) */
		public final char inLineHeadingTerminator;
		
		/** the vertical distance between two paragraphs, if different from in-paragraph line distance */
		public final int paragraphDistance;
		
		/** booleans indicating which line in the predecessor block metrics starts a new paragraph; length equal to number of lines in that block metrics, null except for cross-block analysis results */
		public final boolean[] predecessorIsParagraphStartLine;
		
		/** strings indicating based on what criteria a line in the predecessor block was determined to start a paragraph, i.e., 'G' for line gap, 'S' for short preceding line, 'I' for indentation, 'U' for URI lines, 'H' for in-line headings, or any sequence of these five */
		public final String[] predecessorParagraphStartLineEvidence;
		
		/** booleans indicating which line in the parent block metrics starts a new paragraph; length equal to number of lines in parent block metrics */
		public final boolean[] isParagraphStartLine;
		
		/** strings indicating based on what criteria a line was determined to start a paragraph, i.e., 'G' for line gap, 'S' for short preceding line, 'I' for indentation, 'U' for URI lines, 'H' for in-line headings, or any sequence of these five */
		public final String[] paragraphStartLineEvidence;
		
		BlockLayout(BlockMetrics blockMetrics, char alignment, char paragraphStartLinePos, boolean shortLineEndsParagraph, char uriLineSplitMode, String inLineHeadingStyle, char inLineHeadingTerminator, int paragraphDistance, boolean[] isParagraphStartLine, String[] paragraphStartLineEvidence) {
			this(null, blockMetrics, alignment, paragraphStartLinePos, shortLineEndsParagraph, uriLineSplitMode, inLineHeadingStyle, inLineHeadingTerminator, paragraphDistance, null, null, isParagraphStartLine, paragraphStartLineEvidence);
		}
		
		BlockLayout(BlockMetrics predecessorBlockMetrics, BlockMetrics blockMetrics, char alignment, char paragraphStartLinePos, boolean shortLineEndsParagraph, char uriLineSplitMode, String inLineHeadingStyle, char inLineHeadingTerminator, int paragraphDistance, boolean[] predecessorIsParagraphStartLine, String[] predecessorParagraphStartLineEvidence, boolean[] isParagraphStartLine, String[] paragraphStartLineEvidence) {
			this.predecessorBlockMetrics = predecessorBlockMetrics;
			this.blockMetrics = blockMetrics;
			this.alignment = alignment;
			this.paragraphStartLinePos = paragraphStartLinePos;
			this.shortLineEndsParagraph = shortLineEndsParagraph;
			this.uriLineSplitMode = uriLineSplitMode;
			this.inLineHeadingStyle = inLineHeadingStyle;
			this.inLineHeadingTerminator = inLineHeadingTerminator;
			this.paragraphDistance = paragraphDistance;
			this.predecessorIsParagraphStartLine = predecessorIsParagraphStartLine;
			this.predecessorParagraphStartLineEvidence = predecessorParagraphStartLineEvidence;
			this.isParagraphStartLine = isParagraphStartLine;
			this.paragraphStartLineEvidence = paragraphStartLineEvidence;
		}
		
		/* TODO add scoring methods to this class:
		 * - consistency of indent/outdent with paragraph starts (for alignment L or J), i.e.:
		 *   - consistency of left margin of paragraph start lines only
		 *   - consistency of left margin of in-paragraph lines only
		 * - sentence continuations marked as paragraph starts
		 * - do paragraph starts form a sorted (or at least almost sorted) sequence (especially for outdent):
		 *   - alphabetical sort order of start words
		 *   - continuous number sequence
		 *   - continuous number sequence interspersed with dashes or similar punctuation marks
		 * - short lines _not_ ending paragraphs
		 * - consistency of line gaps, i.e.:
		 *   - consistency of gap above paragraph start lines only
		 *   - consistency of gap above in-paragraph lines only
		 */
		
		/**
		 * Write the current paragraph structure represented by this object
		 * through to the underlying text block, removing and marking paragraph
		 * regions as required, and adjust text stream structure, in particular
		 * word relationships. The relationship of the first block word and its
		 * predecessor are only altered if the predecessor block is not null.
		 * @return true if any paragraph regions were added or removed
		 */
		public boolean writeParagraphStructure() {
			
			//	collect lines
			ImRegion[] lines = new ImRegion[this.blockMetrics.lines.length];
			for (int l = 0; l < this.blockMetrics.lines.length; l++)
				lines[l] = this.blockMetrics.lines[l].line;
			
			//	get text orientation and indentation attributes
			String textOrientation = getTextOrientation(this.alignment);
			String indentation = getIntentation(this.paragraphStartLinePos);
			
			//	get paragraph start lines
			boolean[] isParagraphStartLine = new boolean[this.isParagraphStartLine.length];
			System.arraycopy(this.isParagraphStartLine, 0, isParagraphStartLine, 0, isParagraphStartLine.length);
			
			//	make sure not to disturb first line unless we have the basis for that decision
			if (this.predecessorBlockMetrics == null)
				isParagraphStartLine[0] = true;
			
			//	write changes
			return PageAnalysis.writeParagraphStructure(this.blockMetrics.page, this.blockMetrics.block, lines, isParagraphStartLine, this.paragraphStartLineEvidence, textOrientation, indentation);
		}
		
		/**
		 * Resolve a single-character text orientation code to the respective
		 * attribute value string.
		 * @param to the text orientation code to resolve
		 * @return the attribute value string
		 */
		public static String getTextOrientation(char to) {
			if (to == 'J') return ImRegion.TEXT_ORIENTATION_JUSTIFIED;
			else if (to == 'L') return ImRegion.TEXT_ORIENTATION_LEFT;
			else if (to == 'C') return ImRegion.TEXT_ORIENTATION_CENTERED;
			else if (to == 'R') return ImRegion.TEXT_ORIENTATION_RIGHT;
			else return ImRegion.TEXT_ORIENTATION_MIXED;
		}
		
		/**
		 * Resolve a single-character indentation code to the respective
		 * attribute value string.
		 * @param i the indentation code to resolve
		 * @return the attribute value string
		 */
		public static String getIntentation(char i) {
			if (i == 'I') return ImRegion.INDENTATION_INDENT;
			else if (i == 'O') return ImRegion.INDENTATION_EXDENT;
			else if (i == 'N') return ImRegion.INDENTATION_NONE;
			else return ImRegion.INDENTATION_MIXED;
		}
	}
	
	/**
	 * Restructure the paragraphs in a block to make each single line into a
	 * separate paragraph.
	 * @param page the page the block lines in
	 * @param block the block whose lines to group into paragraphs
	 * @param lines the lines of the argument block
	 * @param textOrientation the text orientation to add to the paragraphs in
	 *            the respective attribute
	 * @param indentation the indentation to add to the paragraphs in the
	 *            respective attribute
	 * @return true if the argument block was modified
	 */
	public static boolean writeSingleLineParagraphStructure(ImPage page, ImRegion block, ImRegion[] lines, String textOrientation, String indentation) {
		boolean[] isParagraphStartLine = new boolean[lines.length];
		Arrays.fill(isParagraphStartLine, true);
		return writeParagraphStructure(page, block, lines, isParagraphStartLine, null, textOrientation, indentation);
	}
	
	/**
	 * Restructure the paragraphs in a block. The block is split up above lines
	 * whose corresponding entry in the argument boolean array is true.
	 * @param page the page the block lines in
	 * @param block the block whose lines to group into paragraphs
	 * @param lines the lines of the argument block
	 * @param isParagraphStartLine an array of booleans indicating for each line
	 *            whether or not it starts a new paragraph
	 * @param paragraphStartLineEvidence an array of strings explaining on what
	 *            exact grounds lines were determined to start new paragraphs
	 * @param textOrientation the text orientation to add to the paragraphs in
	 *            the respective attribute
	 * @param indentation the indentation to add to the paragraphs in the
	 *            respective attribute
	 * @return true if the argument block was modified
	 */
	public static boolean writeParagraphStructure(ImPage page, ImRegion block, ImRegion[] lines, boolean[] isParagraphStartLine, String[] paragraphStartLineEvidence, String textOrientation, String indentation) {
		
		//	keep track of changes
		boolean changed = false;
		
		//	index existing paragraphs
		ImRegion[] exParagraphs = getBlockParagraphs(page, block);
		HashMap exParagraphsByBounds = new HashMap();
		for (int p = 0; p < exParagraphs.length; p++)
			exParagraphsByBounds.put(exParagraphs[p].bounds, exParagraphs[p]);
		
		//	connect paragraph broken line break for first line
		if (!isParagraphStartLine[0]) {
			ImWord[] lineWords = getRegionWords(page, lines[0]);
			ImWord previousLineEndWord = lineWords[0].getPreviousWord();
			if ((previousLineEndWord != null) && (previousLineEndWord.getNextRelation() == ImWord.NEXT_RELATION_PARAGRAPH_END))
				previousLineEndWord.setNextRelation(ImWord.NEXT_RELATION_SEPARATE);
		}
		
		//	wrap paragraphs around lines, reusing existing ones where possible
		int paragraphStartLineIndex = 0;
		for (int l = 1; l < lines.length; l++) {
			
			//	mark paragraph, and adjust word relationship across boundary to reflect paragraph break
			if (isParagraphStartLine[l]) {
				
				//	get or create paragraph
				BoundingBox paragraphBounds = ImLayoutObject.getAggregateBox(lines, paragraphStartLineIndex, l);
				ImRegion paragraph = ((ImRegion) exParagraphsByBounds.remove(paragraphBounds));
				if (paragraph == null) {
					paragraph = new ImRegion(page, paragraphBounds, ImRegion.PARAGRAPH_TYPE);
					changed = true;
				}
				
				//	set attributes
				paragraph.setAttribute(ImRegion.TEXT_ORIENTATION_ATTRIBUTE, textOrientation);
				paragraph.setAttribute(ImRegion.INDENTATION_ATTRIBUTE, indentation);
				if ((paragraphStartLineEvidence != null) && (paragraphStartLineEvidence[paragraphStartLineIndex] != null))
					paragraph.setAttribute("_evidence", paragraphStartLineEvidence[paragraphStartLineIndex]);
				
				//	mark paragraph break
				ImWord[] lineWords = getRegionWords(page, lines[l]);
				ImWord paragraphEndWord = lineWords[0].getPreviousWord();
				if (paragraphEndWord != null)
					paragraphEndWord.setNextRelation(ImWord.NEXT_RELATION_PARAGRAPH_END);
				paragraphStartLineIndex = l;
			}
			
			//	connect paragraph broken line break
			else {
				ImWord[] lineWords = getRegionWords(page, lines[l]);
				ImWord previousLineEndWord = lineWords[0].getPreviousWord();
				if ((previousLineEndWord != null) && (previousLineEndWord.getNextRelation() == ImWord.NEXT_RELATION_PARAGRAPH_END))
					previousLineEndWord.setNextRelation(ImWord.NEXT_RELATION_SEPARATE);
			}
		}
		
		//	mark last paragraph
		BoundingBox paragraphBounds = ImLayoutObject.getAggregateBox(lines, paragraphStartLineIndex, lines.length);
		ImRegion paragraph = ((ImRegion) exParagraphsByBounds.remove(paragraphBounds));
		if (paragraph == null)
			paragraph = new ImRegion(page, paragraphBounds, ImRegion.PARAGRAPH_TYPE);
		
		//	set attributes
		paragraph.setAttribute(ImRegion.TEXT_ORIENTATION_ATTRIBUTE, textOrientation);
		paragraph.setAttribute(ImRegion.INDENTATION_ATTRIBUTE, indentation);
		if ((paragraphStartLineEvidence != null) && (paragraphStartLineEvidence[paragraphStartLineIndex] != null))
			paragraph.setAttribute("_evidence", paragraphStartLineEvidence[paragraphStartLineIndex]);
		
		//	remove any now-obsolete paragraphs
		for (Iterator pbit = exParagraphsByBounds.keySet().iterator(); pbit.hasNext();) {
			paragraphBounds = ((BoundingBox) pbit.next());
			paragraph = ((ImRegion) exParagraphsByBounds.get(paragraphBounds));
			page.removeRegion(paragraph);
			changed = true;
		}
		
		//	report any changes
		return changed;
	}
	
	private static ImRegion[] getBlockParagraphs(ImPage page, ImRegion block) {
		ImRegion[] pageParagraphs = page.getRegions(ImRegion.PARAGRAPH_TYPE);
		ArrayList blockParagraphs = new ArrayList();
		for (int p = 0; p < pageParagraphs.length; p++) {
			if (block.bounds.includes(pageParagraphs[p].bounds, false))
				blockParagraphs.add(pageParagraphs[p]);
		}
		return ((ImRegion[]) blockParagraphs.toArray(new ImRegion[blockParagraphs.size()]));
	}
	
	private static ImWord[] getRegionWords(ImPage page, ImRegion region) {
		ImWord[] regionWords = page.getWordsInside(region.bounds);
		ImUtils.sortLeftRightTopDown(regionWords);
		return regionWords;
	}
}