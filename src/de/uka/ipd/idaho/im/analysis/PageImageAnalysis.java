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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;

import de.uka.ipd.idaho.gamta.Gamta;
import de.uka.ipd.idaho.gamta.Token;
import de.uka.ipd.idaho.gamta.TokenSequence;
import de.uka.ipd.idaho.gamta.Tokenizer;
import de.uka.ipd.idaho.gamta.util.CountingSet;
import de.uka.ipd.idaho.gamta.util.DocumentStyle.PropertiesData;
import de.uka.ipd.idaho.gamta.util.ProgressMonitor;
import de.uka.ipd.idaho.gamta.util.imaging.BoundingBox;
import de.uka.ipd.idaho.gamta.util.imaging.ImagingConstants;
import de.uka.ipd.idaho.gamta.util.imaging.PageImage;
import de.uka.ipd.idaho.im.ImAnnotation;
import de.uka.ipd.idaho.im.ImDocument;
import de.uka.ipd.idaho.im.ImLayoutObject;
import de.uka.ipd.idaho.im.ImPage;
import de.uka.ipd.idaho.im.ImRegion;
import de.uka.ipd.idaho.im.ImWord;
import de.uka.ipd.idaho.im.analysis.Imaging.AnalysisImage;
import de.uka.ipd.idaho.im.analysis.Imaging.ImagePartRectangle;
import de.uka.ipd.idaho.im.util.ImDocumentStyle;
import de.uka.ipd.idaho.im.util.ImUtils;
import de.uka.ipd.idaho.im.utilities.ImageDisplayDialog;

/**
 * Function library for page image analysis.
 * 
 * @author sautter
 */
public class PageImageAnalysis implements ImagingConstants {
	
	/**
	 * A part of a text image, namely a block, a line, or an individual word.
	 * 
	 * @author sautter
	 */
	public static abstract class PagePart {
		public final ImagePartRectangle bounds;
		PagePart(ImagePartRectangle bounds) {
			//	clone bounds, so no two parts share the same (causes trouble with adjustments)
			this.bounds = new ImagePartRectangle(bounds.ai);
			this.bounds.leftCol = bounds.leftCol;
			this.bounds.rightCol = bounds.rightCol;
			this.bounds.topRow = bounds.topRow;
			this.bounds.bottomRow = bounds.bottomRow;
			this.bounds.splitClean = bounds.splitClean;
		}
		public BoundingBox getBoundingBox() {
			return this.getBoundingBox(1);
		}
		public BoundingBox getBoundingBox(int scaleFactor) {
			int rbAdd = scaleFactor-1;
			return new BoundingBox((this.bounds.leftCol * scaleFactor), ((this.bounds.rightCol * scaleFactor) + rbAdd), (this.bounds.topRow * scaleFactor), ((this.bounds.bottomRow * scaleFactor) + rbAdd));
		}
	}
	
	/**
	 * Wrapper for an image of a generic region of a page.
	 * 
	 * @author sautter
	 */
	public static class Region extends PagePart {
		boolean isAtomic = false;
		boolean isImage = false;
		Block block;
		final boolean isColumn;
		final Region superRegion;
		ArrayList subRegions = new ArrayList();
		Region(ImagePartRectangle bounds, boolean isColumn, Region superRegion) {
			super(bounds);
			this.isColumn = isColumn;
			this.superRegion = superRegion;
		}
		void addSubRegion(Region subRegion) {
			this.subRegions.add(subRegion);
		}
		public int getSubRegionCount() {
			return this.subRegions.size();
		}
		public Region getSubRegion(int index) {
			return ((Region) this.subRegions.get(index));
		}
		void removeSubRegion(int index) {
			this.subRegions.remove(index);
		}
		void setSubRegion(int index, Region subRegion) {
			this.subRegions.set(index, subRegion);
		}
		public boolean isAtomic() {
			return this.isAtomic;
		}
		void setAtomic() {
			this.subRegions.clear();
			this.isAtomic = true;
		}
		public boolean areChildrenAtomic() {
			for (int s = 0; s < this.getSubRegionCount(); s++) {
				if (!this.getSubRegion(s).isAtomic)
					return false;
			}
			return true;
		}
		public boolean isImage() {
			return this.isImage;
		}
		void setImage() {
			this.setAtomic();
			this.isImage = true;
		}
		public boolean isColumn() {
			return this.isColumn;
		}
		public Block getBlock() {
			return this.block;
		}
		void setBlock(Block block) {
			this.block = block;
		}
	}
	
	/**
	 * Wrapper for an image of a text block.
	 * 
	 * @author sautter
	 */
	public static class Block extends PagePart {
		ArrayList lines = new ArrayList();
		ArrayList rows = new ArrayList();
		boolean isTable = false;
		Block(ImagePartRectangle bounds) {
			super(bounds);
		}
		void addLine(Line line) {
			this.lines.add(line);
		}
		public Line[] getLines() {
			if (this.isTable) {
				ArrayList lines = new ArrayList();
				TableRow[] rows = this.getRows();
				for (int r = 0; r < rows.length; r++) {
					TableCell[] cells = rows[r].getCells();
					for (int c = 0; c < cells.length; c++)
						lines.addAll(cells[c].lines);
				}
				return ((Line[]) lines.toArray(new Line[lines.size()]));
			}
			return ((Line[]) this.lines.toArray(new Line[this.lines.size()]));
		}
		boolean lineSplitClean() {
			boolean clean = true;
			for (Iterator lit = this.lines.iterator(); clean && lit.hasNext();) {
				Line line = ((Line) lit.next());
				clean = (clean && line.bounds.splitClean);
			}
			return clean;
		}
		public boolean isTable() {
			return this.isTable;
		}
		void addRow(TableRow row) {
			this.rows.add(row);
		}
		public TableRow[] getRows() {
			return ((TableRow[]) this.rows.toArray(new TableRow[this.rows.size()]));
		}
		public boolean isEmpty() {
			return ((this.lines.size() == 0) && (this.rows.size() == 0));
		}
	}
	
	/**
	 * Wrapper for an image of a table row.
	 * 
	 * @author sautter
	 */
	public static class TableRow extends PagePart {
		ArrayList cells = new ArrayList();
		TableRow(ImagePartRectangle bounds) {
			super(bounds);
		}
		void addCell(TableCell cell) {
			this.cells.add(cell);
		}
		public TableCell[] getCells() {
			return ((TableCell[]) this.cells.toArray(new TableCell[this.cells.size()]));
		}
	}
	
	/**
	 * Wrapper for an image of a table cell.
	 * 
	 * @author sautter
	 */
	public static class TableCell extends PagePart {
		ArrayList lines = new ArrayList();
		int colSpan = 1;
		int rowSpan = 1;
		TableCell(ImagePartRectangle bounds) {
			super(bounds);
		}
		void addLine(Line line) {
			this.lines.add(line);
		}
		public Line[] getLines() {
			return ((Line[]) this.lines.toArray(new Line[this.lines.size()]));
		}
		public int getColSpan() {
			return colSpan;
		}
		public int getRowSpan() {
			return rowSpan;
		}
	}
	
	/**
	 * Wrapper for an image of a single line of text.
	 * 
	 * @author sautter
	 */
	public static class Line extends PagePart {
		ArrayList words = new ArrayList();
		int baseline = -1;
		int fontSize = -1;
		Line(ImagePartRectangle bounds) {
			super(bounds);
		}
		void addWord(Word word) {
			this.words.add(word);
		}
		public Word[] getWords() {
			return ((Word[]) this.words.toArray(new Word[this.words.size()]));
		}
		public int getBaseline() {
			if (this.baseline == -1)
				this.computeBaseline();
			return this.baseline;
		}
		private void computeBaseline() {
			if (this.words.size() == 0)
				return;
			int wordBaselineSum = 0;
			for (int w = 0; w < this.words.size(); w++) {
				Word word = ((Word) this.words.get(w));
				if (word.baseline == -1)
					return;
				wordBaselineSum += word.baseline;
			}
			this.baseline = (wordBaselineSum / this.words.size());
		}
		public int getFontSize() {
			return this.fontSize;
		}
		public boolean isBold() {
			int wws = 0;
			int bwws = 0;
			for (int w = 0; w < this.words.size(); w++) {
				Word word = ((Word) this.words.get(w));
				wws += (word.bounds.rightCol - word.bounds.leftCol);
				if (word.isBold())
					bwws += (word.bounds.rightCol - word.bounds.leftCol);
			}
			return (wws < (2 * bwws));
		}
	}
	
	/**
	 * Wrapper for an image of an individual word.
	 * 
	 * @author sautter
	 */
	public static class Word extends PagePart {
		int baseline = -1;
//		float fontWeightCoefficient300 = 0;
		boolean bold = false;
		boolean italics = false;
		Word(ImagePartRectangle bounds) {
			super(bounds);
		}
		public int getBaseline() {
			return this.baseline;
		}
//		public float getFontWeightCoefficient300() {
//			return this.fontWeightCoefficient300;
//		}
		public boolean isBold() {
			return this.bold;
		}
		public boolean isItalics() {
			return this.italics;
		}
	}
	
	/* TODO when doing blocks:
	 * - do approximate line chopping (margin 1, but no skew)
	 *   - estimate line height if chopping fails for skew
	 * - measure margin between side-by-side equally wide blocks with many (>5?) lines
	 *   ==> actual column margin
	 *   - no such pairs of blocks exist
	 *     ==> single-column layout
	 *     ==> increase column margin threshold to prevent justified blocks with few lines (e.g. captions) from being split into multiple columns
	 * - merge side-by-side blocks with few lines whose margin is below 70% of measured column margin
	 *   ==> should repair most captions even in multi-column layouts
	 * 
	 * WATCH OUT for measurements from keys, tables, etc., though
	 * ==> test with respective documents
	 */
	
	/**
	 * Analyze the structure of a document page, i.e., chop it into sub regions
	 * and text blocks.
	 * @param ai the page image to analyze
	 * @param dpi the resolution of the underlying page image
	 * @param filterImageBlocks filter out blocks that are likely to be images
	 *            rather than text? (safe to switch off for born-digital page
	 *            images)
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the root region, representing the whole page
	 */
	public static Region getPageRegion(AnalysisImage ai, int dpi, boolean filterImageBlocks, ProgressMonitor pm) {
		return getPageRegion(ai, dpi, null, -1, filterImageBlocks, pm);
	}
	
	/**
	 * Analyze the structure of a document page, i.e., chop it into sub regions
	 * and text blocks.
	 * @param ai the page image to analyze
	 * @param dpi the resolution of the underlying page image
	 * @param columnAreas an array of bounding boxes marking the areas where
	 *            text columns are located, preventing column splits inside
	 *            each individual area
	 * @param minColumnWidth the minimum width for a column after a split
	 * @param filterImageBlocks filter out blocks that are likely to be images
	 *            rather than text? (safe to switch off for born-digital page
	 *            images)
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the root region, representing the whole page
	 */
	public static Region getPageRegion(AnalysisImage ai, int dpi, BoundingBox[] columnAreas, int minColumnWidth, boolean filterImageBlocks, ProgressMonitor pm) {
		ImagePartRectangle pageBounds = Imaging.getContentBox(ai);
//		int minHorizontalBlockMargin = (dpi / 15); // TODO find out if this makes sense (will turn out in the long haul only, though)
		int minHorizontalBlockMargin = (dpi / 10); // TODO find out if this makes sense (will turn out in the long haul only, though)
//		int minHorizontalBlockMargin = (dpi / 8); // TODO find out if this makes sense (will turn out in the long haul only, though)
//		int minVerticalBlockMargin = (dpi / 15); // TODO find out if this makes sense (will turn out in the long haul only, though)
		int minVerticalBlockMargin = (dpi / 10); // TODO find out if this makes sense (will turn out in the long haul only, though)
		return getPageRegion(pageBounds, minHorizontalBlockMargin, minVerticalBlockMargin, columnAreas, minColumnWidth, dpi, filterImageBlocks, pm);
	}
	
	/**
	 * Analyze the structure of a document page, i.e., chop it into sub regions
	 * and text blocks. The argument minimum margins are used as they are, i.e.,
	 * as absolute values without further scaling. It is the responsibility of
	 * client code to specify values appropriate for the given DPI number.
	 * @param ai the page image to analyze
	 * @param dpi the resolution of the underlying page image
	 * @param minHorizontalBlockMargin the minimum number of white pixels to
	 *            the left and right of a text block (the margin between two
	 *            text columns)
	 * @param minVerticalBlockMargin the minimum number of white pixels above
	 *            and below a text block (the margin between two text blocks in
	 *            the same column)
	 * @param filterImageBlocks filter out blocks that are likely to be images
	 *            rather than text? (safe to switch off for born-digital page
	 *            images)
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the root region, representing the whole page
	 */
	public static Region getPageRegion(AnalysisImage ai, int dpi, int minHorizontalBlockMargin, int minVerticalBlockMargin, boolean filterImageBlocks, ProgressMonitor pm) {
		return getPageRegion(ai, dpi, minHorizontalBlockMargin, minVerticalBlockMargin, null, -1, filterImageBlocks, pm);
	}
	
	/**
	 * Analyze the structure of a document page, i.e., chop it into sub regions
	 * and text blocks. The argument minimum margins are used as they are, i.e.,
	 * as absolute values without further scaling. It is the responsibility of
	 * client code to specify values appropriate for the given DPI number.
	 * @param ai the page image to analyze
	 * @param dpi the resolution of the underlying page image
	 * @param minHorizontalBlockMargin the minimum number of white pixels to
	 *            the left and right of a text block (the margin between two
	 *            text columns)
	 * @param minVerticalBlockMargin the minimum number of white pixels above
	 *            and below a text block (the margin between two text blocks in
	 *            the same column)
	 * @param columnAreas an array of bounding boxes marking the areas where
	 *            text columns are located, preventing column splits inside
	 *            each individual area
	 * @param minColumnWidth the minimum width for a column after a split
	 * @param filterImageBlocks filter out blocks that are likely to be images
	 *            rather than text? (safe to switch off for born-digital page
	 *            images)
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the root region, representing the whole page
	 */
	public static Region getPageRegion(AnalysisImage ai, int dpi, int minHorizontalBlockMargin, int minVerticalBlockMargin, BoundingBox[] columnAreas, int minColumnWidth, boolean filterImageBlocks, ProgressMonitor pm) {
		ImagePartRectangle pageBounds = Imaging.getContentBox(ai);
		return getPageRegion(pageBounds, minHorizontalBlockMargin, minVerticalBlockMargin, columnAreas, minColumnWidth, dpi, filterImageBlocks, pm);
	}
	
	private static Region getPageRegion(ImagePartRectangle pageBounds, int minHorizontalBlockMargin, int minVerticalBlockMargin, BoundingBox[] columnAreas, int minColumnWidth, int dpi, boolean filterImageBlocks, ProgressMonitor pm) {
		
		//	create block comprising whole page
		Region page = new Region(pageBounds, false, null);
		
		//	visualize splitting
		ImageDisplayDialog idd = null;//new ImageDisplayDialog("Page split step by step");
		
		//	fill in region tree
		fillInSubRegions(idd, "", page, minHorizontalBlockMargin, minVerticalBlockMargin, columnAreas, minColumnWidth, dpi, filterImageBlocks, pm);
		
		//	show split
		if (idd != null) {
			idd.setSize(800, 1000);
			idd.setLocationRelativeTo(null);
			idd.setVisible(true);
		}
		
		//	finally ...
		return page;
	}
	
	private static final float capitalIHeightWidthRatio = (((float) 8) / 2); // the height/width ratio of a capital I is usually lower in serif fonts, but we want to have some safety margin
	private static void fillInSubRegions(ImageDisplayDialog idd, String indent, Region region, int minHorizontalBlockMargin, int minVerticalBlockMargin, BoundingBox[] columnAreas, int minColumnWidth, int dpi, boolean filterImageBlocks, ProgressMonitor pm) {
		if (minHorizontalBlockMargin != 1) {
			System.out.println("Splitting " + (region.isColumn ? "column" : "block") + " " + region.getBoundingBox());
			if (columnAreas != null) {
				System.out.println("Column areas are:");
				for (int c = 0; c < columnAreas.length; c++)
					System.out.println(" - " + columnAreas[c]);
			}
		}
		
		//	do split orthogonal to the one this region originated from
		ImagePartRectangle[] subRegions;
		if (region.isColumn) {
			if (region.bounds.getWidth() < dpi)
				subRegions = Imaging.splitIntoRows(region.bounds, minVerticalBlockMargin, 0.1);
			else subRegions = Imaging.splitIntoRows(region.bounds, minVerticalBlockMargin, 0.3);
		}
		else if (region.bounds.getHeight() < dpi)
			subRegions = Imaging.splitIntoColumns(region.bounds, minHorizontalBlockMargin, 0, true);
		else {
			ImagePartRectangle[][] subRegionsCandidates = new ImagePartRectangle[7][];
			for (int c = 0; c < subRegionsCandidates.length; c++) {
				double shearDegrees = (((double) (c - (subRegionsCandidates.length / 2))) / 10);
				subRegionsCandidates[c] = Imaging.splitIntoColumns(region.bounds, minHorizontalBlockMargin, shearDegrees, true);
				if (minHorizontalBlockMargin != 1)
					System.out.println(" - got " + subRegionsCandidates[c].length + " columns at margin " + minHorizontalBlockMargin + ", split angle " + shearDegrees);
			}
			subRegions = subRegionsCandidates[subRegionsCandidates.length / 2];
			for (int c = 0; c < subRegionsCandidates.length; c++) {
				if (subRegions.length < subRegionsCandidates[c].length)
					subRegions = subRegionsCandidates[c];
			}
		}
		if (minHorizontalBlockMargin != 1) {
			System.out.println(" - got " + subRegions.length + " sub regions");
			if ((idd != null) && (subRegions.length > 1)) {
				BufferedImage pi = region.bounds.ai.getImage();
				BufferedImage bi = new BufferedImage((pi.getWidth() / 3), (pi.getHeight() / 3), BufferedImage.TYPE_INT_ARGB);
				Graphics2D gr = bi.createGraphics();
				gr.scale(0.33, 0.33);
				gr.drawImage(pi, 0, 0, null);
				gr.setColor(new Color(0, 255, 0, 64));
				gr.fillRect(region.bounds.leftCol, region.bounds.topRow, region.bounds.getWidth(), region.bounds.getHeight());
				gr.setColor(new Color(255, 0, 0, 64));
				for (int s = 0; s < subRegions.length; s++)
					gr.fillRect(subRegions[s].leftCol, subRegions[s].topRow, subRegions[s].getWidth(), subRegions[s].getHeight());
				idd.addImage(bi, (idd.getImageCount() + "." + indent + " Initial " + (region.isColumn ? "block" : "column") + " split of " + region.bounds));
			}
		}
		
		//	nothing to merge if we're splitting into rows, only have a single sub region, or are down to single-pixel splits
		if (region.isColumn) {}
		else if (subRegions.length < 2) {}
		else if (minHorizontalBlockMargin == 1) {}
		
		//	if splitting into columns, re-merge splits lying inside a single column area
		else if (columnAreas != null) {
			ArrayList subRegionList = new ArrayList();
			for (int c = 1; c < subRegions.length; c++) {
				boolean mergeSubRegions = false;
				for (int a = 0; a < columnAreas.length; a++)
					if ((columnAreas[a].left < subRegions[c-1].getRightCol()) && (subRegions[c].getLeftCol() < columnAreas[a].right)) {
						mergeSubRegions = true;
						break;
					}
				if (mergeSubRegions) {
					subRegions[c].leftCol = Math.min(subRegions[c-1].leftCol, subRegions[c].leftCol);
					subRegions[c].rightCol = Math.max(subRegions[c-1].rightCol, subRegions[c].rightCol);
					subRegions[c].topRow = Math.min(subRegions[c-1].topRow, subRegions[c].topRow);
					subRegions[c].bottomRow = Math.max(subRegions[c-1].bottomRow, subRegions[c].bottomRow);
					subRegions[c-1] = null;
				}
				else subRegionList.add(subRegions[c-1]);
			}
			subRegionList.add(subRegions[subRegions.length-1]);
			if (subRegionList.size() < subRegions.length) {
				if ((minHorizontalBlockMargin != 1) && (idd != null) && (subRegions.length > 1)) {
					BufferedImage pi = region.bounds.ai.getImage();
					BufferedImage bi = new BufferedImage((pi.getWidth() / 3), (pi.getHeight() / 3), BufferedImage.TYPE_INT_ARGB);
					Graphics2D gr = bi.createGraphics();
					gr.scale(0.33, 0.33);
					gr.drawImage(pi, 0, 0, null);
					gr.setColor(new Color(0, 255, 0, 64));
					gr.fillRect(region.bounds.leftCol, region.bounds.topRow, region.bounds.getWidth(), region.bounds.getHeight());
					gr.setColor(new Color(255, 0, 0, 64));
					for (int s = 0; s < subRegionList.size(); s++) {
						ImagePartRectangle subRegion = ((ImagePartRectangle) subRegionList.get(s));
						gr.fillRect(subRegion.leftCol, subRegion.topRow, subRegion.getWidth(), subRegion.getHeight());
					}
					idd.addImage(bi, (idd.getImageCount() + "." + indent + " After column area based repair of " + region.bounds));
				}
				subRegions = ((ImagePartRectangle[]) subRegionList.toArray(new ImagePartRectangle[subRegionList.size()]));
				System.out.println(" - got " + subRegions.length + " sub regions after column repair");
			}
		}
		
		//	if splitting into columns, re-merge splits if either side is narrower than minimum column width
		else if (minColumnWidth > 0) {
			ArrayList subRegionList = null;
			for (int c = 0; c < subRegions.length; c++) {
				if (subRegions[c].getWidth() >= minColumnWidth)
					continue;
				int leftDist = (((c == 0) || (subRegions[c-1] == null)) ? Integer.MAX_VALUE : (subRegions[c].leftCol - subRegions[c-1].rightCol));
				int rightDist = (((c+1) == subRegions.length) ? Integer.MAX_VALUE : (subRegions[c+1].leftCol - subRegions[c].rightCol));
				
				//	nothing left to merge with
				if ((leftDist == Integer.MAX_VALUE) && (rightDist == Integer.MAX_VALUE))
					continue;
				
				//	merge left if distance significantly smaller
				if (leftDist < (rightDist / 2)) {
					if (subRegionList == null)
						subRegionList = new ArrayList();
					subRegions[c].leftCol = Math.min(subRegions[c-1].leftCol, subRegions[c].leftCol);
					subRegions[c].rightCol = Math.max(subRegions[c-1].rightCol, subRegions[c].rightCol);
					subRegions[c].topRow = Math.min(subRegions[c-1].topRow, subRegions[c].topRow);
					subRegions[c].bottomRow = Math.max(subRegions[c-1].bottomRow, subRegions[c].bottomRow);
					subRegions[c-1] = null;
				}
				
				//	merge right if distance significantly smaller
				else if (rightDist < (leftDist / 2)) {
					if (subRegionList == null)
						subRegionList = new ArrayList();
					subRegions[c+1].leftCol = Math.min(subRegions[c].leftCol, subRegions[c+1].leftCol);
					subRegions[c+1].rightCol = Math.max(subRegions[c].rightCol, subRegions[c+1].rightCol);
					subRegions[c+1].topRow = Math.min(subRegions[c].topRow, subRegions[c+1].topRow);
					subRegions[c+1].bottomRow = Math.max(subRegions[c].bottomRow, subRegions[c+1].bottomRow);
					if (c == 0)
						subRegions[c] = null;
					else {
						subRegions[c] = subRegions[c-1];
						subRegions[c-1] = null;
					}
				}
				
				//	decide based on difference between resulting column widths, preferring similarity
				else {
					int leftWidth = (((c == 0) || (subRegions[c-1] == null)) ? 0 : (subRegions[c].rightCol - subRegions[c-1].leftCol));
					int leftDiff = Math.abs(leftWidth - subRegions[c+1].getWidth());
					int rightWidth = (((c+1) == subRegions.length) ? 0 : (subRegions[c+1].rightCol - subRegions[c].leftCol));
					int rightDiff = Math.abs(rightWidth - subRegions[c-1].getWidth());
					if (leftDiff < rightDiff) {
						if (subRegionList == null)
							subRegionList = new ArrayList();
						subRegions[c].leftCol = Math.min(subRegions[c-1].leftCol, subRegions[c].leftCol);
						subRegions[c].rightCol = Math.max(subRegions[c-1].rightCol, subRegions[c].rightCol);
						subRegions[c].topRow = Math.min(subRegions[c-1].topRow, subRegions[c].topRow);
						subRegions[c].bottomRow = Math.max(subRegions[c-1].bottomRow, subRegions[c].bottomRow);
						subRegions[c-1] = null;
					}
					else {
						if (subRegionList == null)
							subRegionList = new ArrayList();
						subRegions[c+1].leftCol = Math.min(subRegions[c].leftCol, subRegions[c+1].leftCol);
						subRegions[c+1].rightCol = Math.max(subRegions[c].rightCol, subRegions[c+1].rightCol);
						subRegions[c+1].topRow = Math.min(subRegions[c].topRow, subRegions[c+1].topRow);
						subRegions[c+1].bottomRow = Math.max(subRegions[c].bottomRow, subRegions[c+1].bottomRow);
						if (c == 0)
							subRegions[c] = null;
						else {
							subRegions[c] = subRegions[c-1];
							subRegions[c-1] = null;
						}
					}
				}
			}
			if (subRegionList != null) {
				for (int c = 0; c < subRegions.length; c++) {
					if (subRegions[c] != null)
						subRegionList.add(subRegions[c]);
				}
				if ((minHorizontalBlockMargin != 1) && (idd != null) && (subRegions.length > 1)) {
					BufferedImage pi = region.bounds.ai.getImage();
					BufferedImage bi = new BufferedImage((pi.getWidth() / 3), (pi.getHeight() / 3), BufferedImage.TYPE_INT_ARGB);
					Graphics2D gr = bi.createGraphics();
					gr.scale(0.33, 0.33);
					gr.drawImage(pi, 0, 0, null);
					gr.setColor(new Color(0, 255, 0, 64));
					gr.fillRect(region.bounds.leftCol, region.bounds.topRow, region.bounds.getWidth(), region.bounds.getHeight());
					gr.setColor(new Color(255, 0, 0, 64));
					for (int s = 0; s < subRegionList.size(); s++) {
						ImagePartRectangle subRegion = ((ImagePartRectangle) subRegionList.get(s));
						gr.fillRect(subRegion.leftCol, subRegion.topRow, subRegion.getWidth(), subRegion.getHeight());
					}
					idd.addImage(bi, (idd.getImageCount() + "." + indent + " After column width based repair of " + region.bounds));
				}
				subRegions = ((ImagePartRectangle[]) subRegionList.toArray(new ImagePartRectangle[subRegionList.size()]));
				System.out.println(" - got " + subRegions.length + " sub regions after column repair");
			}
		}
		
		//	narrow sub regions
		for (int r = 0; r < subRegions.length; r++) {
			subRegions[r] = Imaging.narrowLeftAndRight(subRegions[r]);
			subRegions[r] = Imaging.narrowTopAndBottom(subRegions[r]);
			if (minHorizontalBlockMargin != 1) {
				byte avgBrightness = Imaging.computeAverageBrightness(subRegions[r]);
				System.out.println("   - " + new BoundingBox(subRegions[r].leftCol, subRegions[r].rightCol, subRegions[r].topRow, subRegions[r].bottomRow) + ", avg brightness is " + avgBrightness);
			}
		}
		
		//	we're not dealing with a whole page, and no further splits found, we're done
		if ((region.superRegion != null) && (subRegions.length == 1)) {
			Imaging.copyBounds(subRegions[0], region.bounds);
			region.setAtomic();
			return;
		}
		
		//	search for further splits
		for (int r = 0; r < subRegions.length; r++) {
			
			//	check empty regions
			if (subRegions[r].isEmpty())
				continue;
			
			//	create sub region
			Region subRegion = new Region(subRegions[r], !region.isColumn, region);
			
			//	analyze sub region recursively
			fillInSubRegions(idd, (indent + "  "), subRegion, minHorizontalBlockMargin, minVerticalBlockMargin, columnAreas, minColumnWidth, dpi, filterImageBlocks, pm);
			
			//	this sub region is not atomic, but has no sub regions worth retaining either, so forget about it
			if (!subRegion.isAtomic() && (subRegion.getSubRegionCount() == 0))
				continue;
			
			//	atomic and more than an inch in either direction, check brightness if required
			if (filterImageBlocks && subRegion.isAtomic() && ((subRegion.bounds.bottomRow - subRegion.bounds.topRow) > dpi) && ((subRegion.bounds.rightCol - subRegion.bounds.leftCol) > dpi)) {
				byte avgBrightness = Imaging.computeAverageBrightness(subRegions[r]);
				if (avgBrightness <= 96) {
					subRegion.setImage();
					region.addSubRegion(subRegion);
					continue;
				}
			}
			
			//	what remains of this sub region is less than on fifteenth of an inch (5 pt font size) high, and thus very unlikely to be text
			if ((subRegion.bounds.bottomRow - subRegion.bounds.topRow) < (dpi / 15))
				continue;
			
			//	this sub region is too tall (higher than 72pt font size) to be a single line, and narrower than one inch, and thus very unlikely to be text if no line splits exist
			if (((subRegion.bounds.bottomRow - subRegion.bounds.topRow) > dpi) && ((subRegion.bounds.rightCol - subRegion.bounds.leftCol) < dpi)) {
				ImagePartRectangle[] lines = Imaging.splitIntoRows(subRegions[r]);
				if (lines.length < 2) {
					if ((subRegion.bounds.rightCol - subRegion.bounds.leftCol) > (dpi / 2)) {
						subRegion.setImage();
						region.addSubRegion(subRegion);
					}
					continue;
				}
			}
			
			//	this sub region might be a single character with a large (up to 72 pt) font size, but is too narrow even for a capital I, and thus is very unlikely to be text
			float heightWidthRatio = (((float) (subRegion.bounds.bottomRow - subRegion.bounds.topRow)) / (subRegion.bounds.rightCol - subRegion.bounds.leftCol));
			if (((subRegion.bounds.bottomRow - subRegion.bounds.topRow) <= dpi) && (heightWidthRatio > capitalIHeightWidthRatio)) {
				ImagePartRectangle[] lines = Imaging.splitIntoRows(subRegions[r]);
				if (lines.length < 2)
					continue;
			}
			
			//	slice and dice atomic region with 1 pixel margin and see if anything meaningful remains
			if (subRegion.isAtomic() && (minHorizontalBlockMargin > 1) && (minVerticalBlockMargin > 1)) {
				ImagePartRectangle testRegionBounds = new ImagePartRectangle(subRegion.bounds.ai);
				Imaging.copyBounds(subRegion.bounds, testRegionBounds);
				Region testRegion = new Region(testRegionBounds, true, null);
				fillInSubRegions(idd, (indent + "  "), testRegion, 1, 1, null, -1, dpi, true, pm);
				if (!testRegion.isAtomic() && (testRegion.getSubRegionCount() == 0))
					continue;
			}
			
			//	block with single child-column (column cannot be atomic, as otherwise block would be atomic and have no children) (scenario can happen if some artifact column is eliminated) ==> add child-blocks of child-column instead of block itself
			if (!subRegion.isColumn && (subRegion.getSubRegionCount() == 1)) {
				Region onlyChildColumn = subRegion.getSubRegion(0);
				for (int s = 0; s < onlyChildColumn.getSubRegionCount(); s++)
					region.addSubRegion(onlyChildColumn.getSubRegion(s));
			}
			
			//	store other sub region
			else region.addSubRegion(subRegion);
		}
		
		//	only found one sub region worth retaining, so we were merely subtracting dirt, and sub region actually is atomic ==> region is atomic
		if ((region.getSubRegionCount() == 1) && region.getSubRegion(0).isAtomic()) {
			Imaging.copyBounds(region.getSubRegion(0).bounds, region.bounds);
			if (region.getSubRegion(0).isImage())
				region.setImage();
			else region.setAtomic();
		}
		
		//	multiple sub regions worth retaining, shrink bounds of region to hull of their bounds
		else if (region.getSubRegionCount() != 0) {
			subRegions = new ImagePartRectangle[region.getSubRegionCount()];
			for (int s = 0; s < region.getSubRegionCount(); s++)
				subRegions[s] = region.getSubRegion(s).bounds;
			Imaging.copyBounds(Imaging.getHull(subRegions), region.bounds);
		}
		
		//	any columns to repair?
		if (!region.isColumn || (region.getSubRegionCount() < 2) || (minHorizontalBlockMargin < 2))
			return;
		
		//	TODO if we have column areas, merge any columns inside those areas ...
		//	TODO ... if not over barrier blocks that overlap with multiple column areas
		
		//	check if we need to repair any columns (only count blocks of more than one line, though ... page titles with opposing page numbers !!!)
		boolean needNoRepair = true;
		for (int r = 0; r < region.getSubRegionCount(); r++) {
			Region subRegion = region.getSubRegion(r);
			if (subRegion.getSubRegionCount() > 1) {
				System.out.println("Found top level block potentially requiring merger in " + region.bounds + ":");
				System.out.println(" - " + subRegion.bounds);
				needNoRepair = false;
				break;
			}
		}
		if (needNoRepair) {
			System.out.println("Merger of top level blocks in " + region.bounds + " not required");
			return;
		}
		
		//	repair columns that have fallen victim to "cross" splits (only possible from two levels up the region tree though, as we need to go through the individual blocks)
		for (int repairCount = 0;;) {
			int prevRepairCount = repairCount;
			for (int r = 1; r < region.getSubRegionCount(); r++) {
				Region topSubRegion = region.getSubRegion(r-1);
				Region bottomSubRegion = region.getSubRegion(r);
				System.out.println("Investigating merger of top level blocks in " + region.bounds + ":");
				System.out.println(" - " + topSubRegion.bounds);
				System.out.println(" - " + bottomSubRegion.bounds);
				
				//	do we have same >1 number of columns in each of the two blocks (cut some slack for sub regions less than a third of an inch high, might hit gaps)?
//				if ((topSubRegion.getSubRegionCount() < 2) || (bottomSubRegion.getSubRegionCount() < 2))
//					continue;
				if ((topSubRegion.bounds.getHeight() > (dpi / 3)) && (topSubRegion.getSubRegionCount() < 2)) {
					System.out.println(" ==> too few columns in top " + topSubRegion.bounds);
					continue;
				}
				if ((bottomSubRegion.bounds.getHeight() > (dpi / 3)) && (bottomSubRegion.getSubRegionCount() < 2)) {
					System.out.println(" ==> too few columns in bottom " + bottomSubRegion.bounds);
					continue;
				}
//				if (topSubRegion.getSubRegionCount() != bottomSubRegion.getSubRegionCount())
//					continue;
				if ((topSubRegion.bounds.getHeight() > (dpi / 3)) && (bottomSubRegion.bounds.getHeight() > (dpi / 3)) && (topSubRegion.getSubRegionCount() != bottomSubRegion.getSubRegionCount())) {
					System.out.println(" ==> unequal number of columns");
					continue;
				}
				
				//	are the blocks aligned?
				if (!areTopBottom(topSubRegion, bottomSubRegion)) {
					System.out.println(" ==> not top-bottom");
					continue;
				}
				
				//	make sure to not merge two atomic blocks inside same column unless there already was at least one actual multi-column repair
				if ((topSubRegion.getSubRegionCount() < 2) && (bottomSubRegion.getSubRegionCount() < 2)) {
					Region topSubSubRegion = ((topSubRegion.getSubRegionCount() == 0) ? topSubRegion : topSubRegion.getSubRegion(0));
					Region bottomSubSubRegion = ((bottomSubRegion.getSubRegionCount() == 0) ? bottomSubRegion : bottomSubRegion.getSubRegion(0));
					if (topSubSubRegion.bounds.rightCol <= bottomSubSubRegion.bounds.leftCol) {}
					else if (bottomSubSubRegion.bounds.rightCol <= topSubSubRegion.bounds.leftCol) {}
					else {
						System.out.println(" ==> only child blocks in same column");
						if (repairCount == 0)
							continue; // require at least one actual repair before merging those
					}
				}
				
				//	catch barrier blocks
				int maxOverlapCount = 0;
				if (topSubRegion.getSubRegionCount() == 0) {
					int overlapCount = 0;
					System.out.println(" - checking overlaps of atomic top " + topSubRegion.bounds);
					for (int bs = 0; bs < bottomSubRegion.getSubRegionCount(); bs++) {
						Region bottomSubSubRegion = bottomSubRegion.getSubRegion(bs);
						System.out.println("   - against bottom " + bottomSubSubRegion.bounds);
						if (topSubRegion.bounds.rightCol <= bottomSubSubRegion.bounds.leftCol) {
							System.out.println("     ==> top off left");
							continue;
						}
						if (bottomSubSubRegion.bounds.rightCol <= topSubRegion.bounds.leftCol) {
							System.out.println("     ==> bottom off left");
							continue;
						}
						System.out.println("     ==> overlaps");
						overlapCount++;
					}
					maxOverlapCount = Math.max(maxOverlapCount, overlapCount);
				}
				else for (int ts = 0; ts < topSubRegion.getSubRegionCount(); ts++) {
					int overlapCount = 0;
					Region topSubSubRegion = topSubRegion.getSubRegion(ts);
					System.out.println(" - checking overlaps of top " + topSubSubRegion.bounds);
					for (int bs = 0; bs < bottomSubRegion.getSubRegionCount(); bs++) {
						Region bottomSubSubRegion = bottomSubRegion.getSubRegion(bs);
						System.out.println("   - against bottom " + bottomSubSubRegion.bounds);
						if (topSubSubRegion.bounds.rightCol <= bottomSubSubRegion.bounds.leftCol) {
							System.out.println("     ==> top off left");
							continue;
						}
						if (bottomSubSubRegion.bounds.rightCol <= topSubSubRegion.bounds.leftCol) {
							System.out.println("     ==> bottom off left");
							continue;
						}
						System.out.println("     ==> overlaps");
						overlapCount++;
					}
					maxOverlapCount = Math.max(maxOverlapCount, overlapCount);
				}
				if (maxOverlapCount > 1) {
					System.out.println(" ==> top is barrier block");
					continue;
				}
				
				if (bottomSubRegion.getSubRegionCount() == 0) {
					int overlapCount = 0;
					System.out.println(" - checking overlaps of atomic bottom " + bottomSubRegion.bounds);
					for (int ts = 0; ts < topSubRegion.getSubRegionCount(); ts++) {
						Region topSubSubRegion = topSubRegion.getSubRegion(ts);
						System.out.println("   - against top " + topSubSubRegion.bounds);
						if (topSubSubRegion.bounds.rightCol <= bottomSubRegion.bounds.leftCol) {
							System.out.println("     ==> top off left");
							continue;
						}
						if (bottomSubRegion.bounds.rightCol <= topSubSubRegion.bounds.leftCol) {
							System.out.println("     ==> bottom off left");
							continue;
						}
						System.out.println("     ==> overlaps");
						overlapCount++;
					}
					maxOverlapCount = Math.max(maxOverlapCount, overlapCount);
				}
				for (int bs = 0; bs < bottomSubRegion.getSubRegionCount(); bs++) {
					int overlapCount = 0;
					Region bottomSubSubRegion = bottomSubRegion.getSubRegion(bs);
					System.out.println(" - checking overlaps of bottom " + bottomSubSubRegion.bounds);
					for (int ts = 0; ts < topSubRegion.getSubRegionCount(); ts++) {
						Region topSubSubRegion = topSubRegion.getSubRegion(ts);
						System.out.println("   - against " + topSubSubRegion.bounds);
						if (topSubSubRegion.bounds.rightCol <= bottomSubSubRegion.bounds.leftCol) {
							System.out.println("     ==> top off left");
							continue;
						}
						if (bottomSubSubRegion.bounds.rightCol <= topSubSubRegion.bounds.leftCol) {
							System.out.println("     ==> bottom off left");
							continue;
						}
						System.out.println("     ==> overlaps");
						overlapCount++;
					}
					maxOverlapCount = Math.max(maxOverlapCount, overlapCount);
				}
				if (maxOverlapCount > 1) {
					System.out.println(" ==> bottom is barrier block");
					continue;
				}
				
				//	are the columns within the blocks aligned?
				//	TODO catch gap in left column
				boolean subSubRegionMismatch = false;
				for (int s = 0; s < Math.min(topSubRegion.getSubRegionCount(), bottomSubRegion.getSubRegionCount()); s++) {
					Region topSubSubRegion = topSubRegion.getSubRegion(s);
					Region bottomSubSubRegion = bottomSubRegion.getSubRegion(s);
					if (!areTopBottom(topSubSubRegion, bottomSubSubRegion)) {
						subSubRegionMismatch = true;
						System.out.println(" ==> sub regions not top-bottom");
						System.out.println("   - " + topSubSubRegion.bounds);
						System.out.println("   - " + bottomSubSubRegion.bounds);
						break;
					}
				}
				if (subSubRegionMismatch)
					continue;
				
				//	merge blocks
				ImagePartRectangle[] subRegionBounds = {topSubRegion.bounds, bottomSubRegion.bounds};
				Region mergedSubRegion = new Region(Imaging.getHull(subRegionBounds), !region.isColumn, region);
				
				//	re-get sub structure (easier than copying)
				fillInSubRegions(idd, (indent + "  "), mergedSubRegion, minHorizontalBlockMargin, minVerticalBlockMargin, columnAreas, dpi, minColumnWidth, filterImageBlocks, pm);
				
				//	does the merged sub region have as many columns as the original sub regions?
				if (mergedSubRegion.getSubRegionCount() < Math.max(topSubRegion.getSubRegionCount(), bottomSubRegion.getSubRegionCount())) {
					System.out.println(" ==> would incur column loss");
					continue;
				}
				
				//	replace merged blocks in parent column, compensating for loop increment
				region.setSubRegion((r-1), mergedSubRegion);
				region.removeSubRegion(r);
				r--;
				repairCount++;
			}
			if (prevRepairCount == repairCount)
				break; // nothing new this round, we're done
			if ((minHorizontalBlockMargin != 1) && (idd != null)) {
				BufferedImage pi = region.bounds.ai.getImage();
				BufferedImage bi = new BufferedImage((pi.getWidth() / 3), (pi.getHeight() / 3), BufferedImage.TYPE_INT_ARGB);
				Graphics2D gr = bi.createGraphics();
				gr.scale(0.33, 0.33);
				gr.drawImage(pi, 0, 0, null);
				gr.setColor(new Color(0, 255, 0, 64));
				gr.fillRect(region.bounds.leftCol, region.bounds.topRow, region.bounds.getWidth(), region.bounds.getHeight());
				gr.setColor(new Color(255, 0, 0, 64));
//				for (int s = 0; s < subRegions.length; s++)
//					gr.fillRect(subRegions[s].leftCol, subRegions[s].topRow, subRegions[s].getWidth(), subRegions[s].getHeight());
				for (int s = 0; s < region.getSubRegionCount(); s++) {
					Region subRegion = region.getSubRegion(s);
					gr.fillRect(subRegion.bounds.leftCol, subRegion.bounds.topRow, subRegion.bounds.getWidth(), subRegion.bounds.getHeight());
				}
				idd.addImage(bi, (idd.getImageCount() + "." + indent + " After column cross split repair of " + region.bounds));
			}
		}
	}
	
	private static boolean areTopBottom(Region topRegion, Region bottomRegion) {
		
		//	regions off to left or right of one another
		if (topRegion.bounds.rightCol <= bottomRegion.bounds.leftCol)
			return false;
		if (bottomRegion.bounds.rightCol <= topRegion.bounds.leftCol)
			return false;
		
		//	columns of one region fully inside the other region
		if ((topRegion.bounds.leftCol <= bottomRegion.bounds.leftCol) && (bottomRegion.bounds.rightCol <= topRegion.bounds.rightCol))
			return true;
		if ((bottomRegion.bounds.leftCol <= topRegion.bounds.leftCol) && (topRegion.bounds.rightCol <= bottomRegion.bounds.rightCol))
			return true;
		
		//	compute overlap
		int overlapWidth = (Math.min(topRegion.bounds.rightCol, bottomRegion.bounds.rightCol) - Math.max(topRegion.bounds.leftCol, bottomRegion.bounds.leftCol));
		int minWidth = Math.min(topRegion.bounds.getWidth(), bottomRegion.bounds.getWidth());
		
//		//	do we have at least 90% overlap?
//		return ((overlapWidth * 10) >= (minWidth * 9));
		//	do we have at least 70% overlap?
		return ((overlapWidth * 10) >= (minWidth * 7));
	}
	
	/**
	 * Analyze the inner structure of a single text block to individual lines,
	 * but not words, represented as an atomic region. The block object
	 * representing the inner structure of the argument region can be retrieved
	 * via the getBlock() method.
	 * @param region the text block to analyze
	 * @param dpi the resolution of the underlying page image
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 */
	public static void getBlockLines(Region region, int dpi, ProgressMonitor pm) {
		Block block = new Block(region.bounds);
		getBlockLines(block, dpi, pm);
		region.setBlock(block);
	}
	
	/**
	 * Analyze the inner structure of a single text block to individual lines,
	 * but not words.
	 * @param block the text block to analyze
	 * @param dpi the resolution of the underlying page image
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 */
	public static void getBlockLines(Block block, int dpi, ProgressMonitor pm) {
		
		//	compute margin thresholds
		int minVerticalBlockMargin = (dpi / 10); // TODO find out if this makes sense (will turn out in the long haul only, though)
		int minLineMargin = (dpi / 40); // TODO find out if this makes sense (will turn out in the long haul only, though)
		
		//	find text lines
		getBlockLines(block, dpi, minVerticalBlockMargin, minLineMargin, pm);
	}
	
	/**
	 * Analyze the inner structure of a single text block to individual lines,
	 * but not words. The argument minimum margins are used as they are, i.e.,
	 * as absolute values without further scaling. It is the responsibility of
	 * client code to specify values appropriate for the given DPI number.
	 * @param block the text block to analyze
	 * @param dpi the resolution of the underlying page image
	 * @param minVerticalBlockMargin the minimum number of white pixels above
	 *            and below a text block (the margin between two text blocks in
	 *            the same column)
	 * @param minLineMargin the minimum number of white pixels rows between two
	 *            lines of text
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 */
	public static void getBlockLines(Block block, int dpi, int minVerticalBlockMargin, int minLineMargin, ProgressMonitor pm) {
		pm.setInfo("   - doing block " + block.getBoundingBox().toString());
		
		double maxSplitSlopeAngle;
		int minZigzagPartLength;
		double maxZigzagSplitSlopeAngle;
		int maxZigzagSlope;
		
		//	do conservative initial line split, so not to zigzag through page headers
		maxSplitSlopeAngle = 0.1;
		ImagePartRectangle[] lBlocks = Imaging.splitIntoRows(block.bounds, 1, maxSplitSlopeAngle, -1, 0);
		pm.setInfo("     - got " + lBlocks.length + " initial lines");
		
		//	split initial lines into columns
		ArrayList bLines = new ArrayList();
		for (int l = 0; l < lBlocks.length; l++) {
			lBlocks[l] = Imaging.narrowLeftAndRight(lBlocks[l]);
			if (lBlocks[l].isEmpty())
				continue;
			ImagePartRectangle[] cBlocks = Imaging.splitIntoColumns(lBlocks[l], (minVerticalBlockMargin * 3));
			if (cBlocks.length == 1)
				bLines.add(lBlocks[l]);
			else for (int c = 0; c < cBlocks.length; c++) {
				cBlocks[c] = Imaging.narrowTopAndBottom(cBlocks[c]);
				if (cBlocks[c].isEmpty())
					continue;
				bLines.add(cBlocks[c]);
			}
		}
		pm.setInfo("     - got " + bLines.size() + " col block lines at margin " + (minVerticalBlockMargin * 3));
		
		//	do more aggressive line splitting, but only for lines that are higher than a fifth of the dpi, as anything below that will hardly consist of two lines
		int minLineHeight = (dpi / 10); // a single line of text will rarely be lower than one thenth of an inch in total
		int minResplitHeight = (minLineHeight * 2); // the minimum height of two lines, we can securely keep anything below this threshold
		maxSplitSlopeAngle = 0.0;//0.2; zigzagging does it alone
		minZigzagPartLength = (dpi / 2);
		maxZigzagSplitSlopeAngle = 1.0;
		maxZigzagSlope = Math.abs((int) (Math.tan(maxZigzagSplitSlopeAngle * Math.PI / 180) * ((double) (block.bounds.rightCol - block.bounds.leftCol))));
		for (int l = 0; l < bLines.size(); l++) {
			ImagePartRectangle line = ((ImagePartRectangle) bLines.get(l));
			if ((line.bottomRow - line.topRow) < minResplitHeight)
				continue;
			lBlocks = Imaging.splitIntoRows(line, 1, maxSplitSlopeAngle, minZigzagPartLength, maxZigzagSlope);
			if (lBlocks.length > 1) {
				bLines.set(l, lBlocks[0]);
				for (int sl = 1; sl < lBlocks.length; sl++) {
					l++;
					bLines.add(l, lBlocks[sl]);
				}
			}
		}
		
		//	recover line blocks
		lBlocks = ((ImagePartRectangle[]) bLines.toArray(new ImagePartRectangle[bLines.size()]));
		pm.setInfo("     - got " + lBlocks.length + " block lines");
		if (lBlocks.length == 0)
			return;
		
		//	compute average line height
		int lineHeightSum = 0;
		int lineCount = 0;
		for (int l = 0; l < lBlocks.length; l++) {
			
			//	skip over lines higher than one inch
			if (dpi < (lBlocks[l].bottomRow - lBlocks[l].topRow))
				continue;
			
			//	count other lines
			lineHeightSum += (lBlocks[l].bottomRow - lBlocks[l].topRow);
			lineCount++;
		}
		int avgLineHeight = ((lineCount == 0) ? -1 : ((lineHeightSum + (lineCount / 2)) / lineCount));
		
		//	save everything higher than one tenth of an inch, no matter how big surrounding lines are
		int minWordHight = (dpi / 10);
		
		//	filter out lines narrower than one thirtieth of an inch (should still save single 1's)
		int minLineWidth = (dpi / 30);
		
		//	store lines in block
		ImagePartRectangle lostLine = null;
		for (int l = 0; l < lBlocks.length; l++) {
			lBlocks[l] = Imaging.narrowLeftAndRight(lBlocks[l]);
			int lineHeight = (lBlocks[l].bottomRow - lBlocks[l].topRow);
			
			//	keep aside very low lines, likely stains, etc. ... or dots of an i or j, accents of upper case latters, ...
			if (lineHeight < Math.min((avgLineHeight / 2), minWordHight)) {
				lostLine = lBlocks[l];
				continue;
			}
			
			//	filter out lines narrower than minimum width
			int lineWidth = (lBlocks[l].rightCol - lBlocks[l].leftCol);
			if (lineWidth < minLineWidth) {
				lostLine = null;
				continue;
			}
			
			//	join any below-minimum-height line to adjacent smaller-than-average line immediately below it (might be cut-off dot of an i or j)
			//	however, forget about accents of upper case letters, as they blow the line metrics anyways
			if ((lostLine != null)
					&& (lBlocks[l].leftCol <= lostLine.leftCol) && (lostLine.rightCol <= lBlocks[l].rightCol)
					&& (lineHeight < avgLineHeight)
				)
				lBlocks[l].topRow = Math.min(lBlocks[l].topRow, lostLine.topRow);
			block.addLine(new Line(lBlocks[l]));
			lostLine = null;
		}
	}
	
	/**
	 * Analyze the inner structure of a single text block to individual lines.
	 * The returned lines are <i>not</i> added to the argument block. Further,
	 * the returned lines have their baseline set.
	 * @param block the text block to analyze
	 * @param dpi the resolution of the underlying page image
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return an array holding the extracted lines
	 */
	public static Line[] findBlockLines(Block block, int dpi, ProgressMonitor pm) {
		return findBlockLines(block.bounds, dpi, pm);
	}
	
	/**
	 * Analyze the inner structure of a single text block to individual lines.
	 * Further, the returned lines have their baseline set.
	 * @param block the bounds of the text block to analyze
	 * @param dpi the resolution of the underlying page image
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return an array holding the extracted lines
	 */
	public static Line[] findBlockLines(ImagePartRectangle block, int dpi, ProgressMonitor pm) {
		
		//	compute row brightness
		byte[][] brightness = block.getImage().getBrightness();
		int[] rowBrightness = getRowBrightness(block, brightness);
		int[] rowPixelCount = getRowPixelCount(block, brightness);
		
		//	wrap brightness and pixel row occupancy histograms, computing basic analyses along the way
		BlockData bData = new BlockData(block, rowBrightness, rowPixelCount);
		
		//	check for last line
		bData.checkForLastLine();
		
		//	wrap lines, and add baselines
		ImagePartRectangle[] bLines = bData.getBlockLines();
		Line[] lines = new Line[bLines.length];
		for (int l = 0; l < bLines.length; l++) {
			lines[l] = new Line(bLines[l]);
			lines[l].baseline = (block.getTopRow() + bData.rowPixelCountDrops[l].row);
		}
		
		//	finally ...
		return lines;
	}
	
	private static class BlockData {
		ImagePartRectangle block;
		
		int[] rowBrightness;
		int avgRowBrightness;
		
		int[] rowPixelCount;
		int avgRowPixelCount;
		
		HistogramRiseDrop[] rowPixelCountRises; // x-height rows of lines
		HistogramRiseDrop[] rowPixelCountDrops; // baseline rows of lines
		
		int[] highRowPixelCenters; // line centers, i.e., middle of x-height
		int[] highRowPixelWidths; // x-heights of lines
		int avgHighRowPixelWidth; // average x-height
		
		int[] lowRowPixelCenters; // line boundaries
		
		BlockData(ImagePartRectangle block, int[] rowBrightness, int[] rowPixelCount) {
			this.block = block;
			
			this.rowBrightness = rowBrightness;
//			System.out.println("Row brightness:");
//			for (int r = 0; r < rowBrightness.length; r++)
//				System.out.println(r + ": " + rowBrightness[r]);
			this.avgRowBrightness = getHistogramAverage(this.rowBrightness);
//			System.out.println("avg brightness: " + avgRowBrightness);
			
			this.rowPixelCount = rowPixelCount;
//			System.out.println("Row occupation:");
//			for (int r = 0; r < rowBrightness.length; r++)
//				System.out.println(r + ": " + rowPixelCount[r]);
			this.avgRowPixelCount = getHistogramAverage(rowPixelCount);
//			System.out.println("avg row pixels: " + avgRowPixelCount);
			
			//	assess row pixel count distribution
			this.rowPixelCountRises = getHistgramMaxRises(this.rowPixelCount, this.avgRowPixelCount);
//			System.out.println("Row height from x-heights: " + getDistanceAverage(rowPixelCountRises) + " (min " + getDistanceMin(rowPixelCountRises) + ", max " + getDistanceMax(rowPixelCountRises) + ")");
			this.rowPixelCountDrops = getHistgramMaxDrops(this.rowPixelCount, this.avgRowPixelCount);
//			System.out.println("Row height from baselines: " + getDistanceAverage(rowPixelCountDrops) + " (min " + getDistanceMin(rowPixelCountDrops) + ", max " + getDistanceMax(rowPixelCountDrops) + ")");
			
			//	filter very small rises and drops immediately adjacent to larger drops and rises !!!
			this.filterRowPixelCountRisesAndDrops();
			
			//	get centers of high and low areas
			this.highRowPixelCenters = getHighCenters(this.rowPixelCountRises, this.rowPixelCountDrops);
//			System.out.println("Row height from line centers: " + getDistanceAverage(highRowPixelCenters) + " (min " + getDistanceMin(highRowPixelCenters) + ", max " + getDistanceMax(highRowPixelCenters) + ")");
			this.lowRowPixelCenters = getLowCenters(this.rowPixelCountRises, this.rowPixelCountDrops);
//			System.out.println("Row height from line gap centers: " + getDistanceAverage(lowRowPixelCenters) + " (min " + getDistanceMin(lowRowPixelCenters) + ", max " + getDistanceMax(lowRowPixelCenters) + ")");
			
			this.highRowPixelWidths = getHighWidths(this.rowPixelCountRises, this.rowPixelCountDrops);
			this.avgHighRowPixelWidth = getHistogramAverage(this.highRowPixelWidths);
		}
		
		private void filterRowPixelCountRisesAndDrops() {
			ArrayList rowPixelCountRises = new ArrayList();
			for (int r = 0; r < this.rowPixelCountRises.length; r++) {
				for (int d = 0; d < this.rowPixelCountDrops.length; d++) {
					int dist = Math.abs(this.rowPixelCountRises[r].row - this.rowPixelCountDrops[d].row);
					if (dist > 2) // TODO_not maybe only allow 1? ==> TODO_not make this dependent on DPI !!!
						continue;
					if (this.rowPixelCountRises[r].delta < this.rowPixelCountDrops[d].delta) {
						System.out.println("Eliminated rise of " + this.rowPixelCountRises[r].delta + " at " + this.rowPixelCountRises[r].row + " as adjacent to drop of " + this.rowPixelCountDrops[d].delta + " at " + this.rowPixelCountDrops[d].row);
						this.rowPixelCountRises[r] = null;
						break;
					}
				}
				if (this.rowPixelCountRises[r] != null)
					rowPixelCountRises.add(this.rowPixelCountRises[r]);
			}
			if (rowPixelCountRises.size() < this.rowPixelCountRises.length)
				this.rowPixelCountRises = ((HistogramRiseDrop[]) rowPixelCountRises.toArray(new HistogramRiseDrop[rowPixelCountRises.size()]));
			
			ArrayList rowPixelCountDrops = new ArrayList();
			for (int d = 0; d < this.rowPixelCountDrops.length; d++) {
				for (int r = 0; r < this.rowPixelCountRises.length; r++) {
					int dist = Math.abs(this.rowPixelCountDrops[d].row - this.rowPixelCountRises[r].row);
					if (dist > 2) // TODO_not maybe only allow 1? ==> TODO_not make this dependent on DPI !!!
						continue;
					if (this.rowPixelCountDrops[d].delta < this.rowPixelCountRises[r].delta) {
						System.out.println("Eliminated drop of " + this.rowPixelCountDrops[d].delta + " at " + this.rowPixelCountDrops[d].row + " as adjacent to rise of " + this.rowPixelCountRises[r].delta + " at " + this.rowPixelCountRises[r].row);
						this.rowPixelCountDrops[d] = null;
						break;
					}
				}
				if (this.rowPixelCountDrops[d] != null)
					rowPixelCountDrops.add(this.rowPixelCountDrops[d]);
			}
			if (rowPixelCountDrops.size() < this.rowPixelCountDrops.length)
				this.rowPixelCountDrops = ((HistogramRiseDrop[]) rowPixelCountDrops.toArray(new HistogramRiseDrop[rowPixelCountDrops.size()]));
		}
		
		void checkForLastLine() {
			
			//	if we have only one distinctive line, use x-height to see if we have one or two lines (x-height should never be smaller than 1/3 of line height ...)
			if ((this.highRowPixelCenters.length < 2) || (this.lowRowPixelCenters.length < 2))
				this.checkForLastLineXHeight();
			
			//	otherwise still check if we have a short last line (based on average line height, though, which should be more reliable)
			else this.checkForLastLineLineHeight();
		}
		
		private void checkForLastLineXHeight() {
			System.out.println("x-height is " + this.avgHighRowPixelWidth + " from " + Arrays.toString(this.highRowPixelWidths));
			
			//	this x-height looks plausible (usual x-height is 40-50% of line height)
			if ((this.avgHighRowPixelWidth * 3) > this.block.getHeight()) {
				System.out.println(" ==> looking like a single line");
				return;
			}
			
			//	cut row pixel occupancy histogram in half (split off last line), and re-compute average
			int[] lastLineRowPixelCount = new int[this.rowPixelCount.length / 2];
			System.arraycopy(this.rowPixelCount, (this.rowPixelCount.length - lastLineRowPixelCount.length), lastLineRowPixelCount, 0, lastLineRowPixelCount.length);
			int avgLastLineRowPixelCount = getHistogramAverage(lastLineRowPixelCount);
			System.out.println("last line row pixel average is " + avgLastLineRowPixelCount);
			
			//	compute rises and drops of lower half (to allow for local averages to kick in)
//			int[] lastLineRowPixelCountRises = getHistgramMaxRises(lastLineRowPixelCount, avgLastLineRowPixelCount);
			HistogramRiseDrop[] lastLineRowPixelCountRises = getHistgramMaxRises(lastLineRowPixelCount, avgLastLineRowPixelCount);
//			System.out.println("Row height from x-heights: " + getDistanceAverage(rowPixelCountRises) + " (min " + getDistanceMin(rowPixelCountRises) + ", max " + getDistanceMax(rowPixelCountRises) + ")");
//			int[] lastLineRowPixelCountDrops = getHistgramMaxDrops(lastLineRowPixelCount, avgLastLineRowPixelCount);
			HistogramRiseDrop[] lastLineRowPixelCountDrops = getHistgramMaxDrops(lastLineRowPixelCount, avgLastLineRowPixelCount);
//			System.out.println("Row height from baselines: " + getDistanceAverage(rowPixelCountDrops) + " (min " + getDistanceMin(rowPixelCountDrops) + ", max " + getDistanceMax(rowPixelCountDrops) + ")");
			System.out.println("last line row pixel rises are " + Arrays.toString(lastLineRowPixelCountRises));
			System.out.println("last line row pixel drops are " + Arrays.toString(lastLineRowPixelCountDrops));
			
			//	compute center and x-height of second line
//			int[] lastLineHighRowPixelCenters = getHighCenters(lastLineRowPixelCountRises, lastLineRowPixelCountDrops);
//			System.out.println("Row height from line centers: " + getDistanceAverage(highRowPixelCenters) + " (min " + getDistanceMin(highRowPixelCenters) + ", max " + getDistanceMax(highRowPixelCenters) + ")");
			int[] lastLineHighRowPixelWidths = getHighWidths(lastLineRowPixelCountRises, lastLineRowPixelCountDrops);
			int avgLastLineHighRowPixelWidth = getHistogramAverage(lastLineHighRowPixelWidths);
			System.out.println("last line x-height is " + avgLastLineHighRowPixelWidth + " from " + Arrays.toString(lastLineHighRowPixelWidths));
			
			//	x-height differs more than 20% from block average, too unreliable
			if ((avgLastLineHighRowPixelWidth == -1) || (((avgLastLineHighRowPixelWidth * 10) < (this.avgHighRowPixelWidth * 8)) && ((this.avgHighRowPixelWidth * 8) < (avgLastLineHighRowPixelWidth * 8)))) {
				System.out.println(" ==> x-height of last line too far off");
				return;
			}
			
			//	append rises and drops for last line
			for (int r = 0; r < lastLineRowPixelCountRises.length; r++)
//				lastLineRowPixelCountRises[r] += (this.rowPixelCount.length - lastLineRowPixelCount.length);
				lastLineRowPixelCountRises[r] = new HistogramRiseDrop((lastLineRowPixelCountRises[r].row + this.rowPixelCount.length - lastLineRowPixelCount.length), lastLineRowPixelCountRises[r].delta);
			this.rowPixelCountRises = merge(this.rowPixelCountRises, lastLineRowPixelCountRises);
			for (int d = 0; d < lastLineRowPixelCountDrops.length; d++)
//				lastLineRowPixelCountDrops[d] += (this.rowPixelCount.length - lastLineRowPixelCount.length);
				lastLineRowPixelCountDrops[d] = new HistogramRiseDrop((lastLineRowPixelCountDrops[d].row + this.rowPixelCount.length - lastLineRowPixelCount.length), lastLineRowPixelCountDrops[d].delta);
			this.rowPixelCountDrops = merge(this.rowPixelCountDrops, lastLineRowPixelCountDrops);
			this.highRowPixelCenters = getHighCenters(this.rowPixelCountRises, this.rowPixelCountDrops);
			this.lowRowPixelCenters = getLowCenters(this.rowPixelCountRises, this.rowPixelCountDrops);
			
			//	re-compute x-heights and average
			this.highRowPixelWidths = getHighWidths(this.rowPixelCountRises, this.rowPixelCountDrops);
			this.avgHighRowPixelWidth = getHistogramAverage(this.highRowPixelWidths);
			
			//	TODO figure out what to do if x-height of last line too far off
		}
		
		private void checkForLastLineLineHeight() {
			
			//	compute average line height (from both line centers and line boundaries)
			float avgLineCenterDist = getDistanceAverage(this.highRowPixelCenters);
			float avgLineGapDist = getDistanceAverage(this.lowRowPixelCenters);
			float avgLineHeight = ((avgLineCenterDist + avgLineGapDist) / 2);
			System.out.println("line height is " + avgLineHeight + " (line centers: min " + getDistanceMin(this.highRowPixelCenters) + ", max " + getDistanceMax(this.highRowPixelCenters) + ", avg " + avgLineCenterDist + ", line gaps: min " + getDistanceMin(this.lowRowPixelCenters) + ", max " + getDistanceMax(this.lowRowPixelCenters) + ", avg " + avgLineGapDist + ")");
			System.out.println("x-height is " + this.avgHighRowPixelWidth + " from " + Arrays.toString(this.highRowPixelWidths));
			
			//	check distance from block bottom
			int lineCenterBlockBottomDist = (this.rowPixelCount.length - this.highRowPixelCenters[this.highRowPixelCenters.length-1]);
			System.out.println("last line center is " + lineCenterBlockBottomDist + " from block bottom");
			int lineGapBlockBottomDist = (this.rowPixelCount.length - this.lowRowPixelCenters[this.lowRowPixelCenters.length-1]);
			System.out.println("last line boundary is " + lineGapBlockBottomDist + " from block bottom");
			
			//	are we close enough to block bottom, or are we missing a line?
			if ((lineCenterBlockBottomDist < avgLineHeight) && ((lineGapBlockBottomDist * 2) < (avgLineHeight * 3))) {
				System.out.println(" ==> looks like we have all the line boundaries");
				return;
			}
			
			//	extrapolate and merge last rise and drop
//			int[] lastLineRowPixelCountRises = {Math.round(this.rowPixelCountRises[this.rowPixelCountRises.length-1] + avgLineHeight)};
			HistogramRiseDrop[] lastLineRowPixelCountRises = {new HistogramRiseDrop(Math.round(this.rowPixelCountRises[this.rowPixelCountRises.length-1].row + avgLineHeight), 0)};
			this.rowPixelCountRises = merge(this.rowPixelCountRises, lastLineRowPixelCountRises);
//			int[] lastLineRowPixelCountDrops = {Math.round(this.rowPixelCountDrops[this.rowPixelCountDrops.length-1] + avgLineHeight)};
			HistogramRiseDrop[] lastLineRowPixelCountDrops = {new HistogramRiseDrop(Math.round(this.rowPixelCountDrops[this.rowPixelCountDrops.length-1].row + avgLineHeight), 0)};
			this.rowPixelCountDrops = merge(this.rowPixelCountDrops, lastLineRowPixelCountDrops);
			
			//	re-compute line centers and boundaries
			this.highRowPixelCenters = getHighCenters(this.rowPixelCountRises, this.rowPixelCountDrops);
			this.lowRowPixelCenters = getLowCenters(this.rowPixelCountRises, this.rowPixelCountDrops);
			
			//	re-compute individual and average x-heights
			this.highRowPixelWidths = getHighWidths(this.rowPixelCountRises, this.rowPixelCountDrops);
			this.avgHighRowPixelWidth = getHistogramAverage(this.highRowPixelWidths);
		}
		
		ImagePartRectangle[] getBlockLines() {
			
			//	use line boundaries to split up block
			ImagePartRectangle[] lines = new ImagePartRectangle[this.lowRowPixelCenters.length+1];
			for (int l = 0; l <= this.lowRowPixelCenters.length; l++) {
				int lineTop = ((l == 0) ? 0 : this.lowRowPixelCenters[l-1]);
				int lineBottom = ((l == this.lowRowPixelCenters.length) ? block.getHeight() : this.lowRowPixelCenters[l]);
				lines[l] = block.getSubRectangle(block.getLeftCol(), block.getRightCol(), (lineTop + block.getTopRow()), (lineBottom + block.getTopRow()));
				Imaging.narrowTopAndBottom(lines[l]);
				Imaging.narrowLeftAndRight(lines[l]);
				System.out.println("line: " + lines[l].getId());
			}
			
			//	finally ...
			return lines;
		}
	}
	
	private static int[] getRowBrightness(ImagePartRectangle block, byte[][] brightness) {
		int[] rowBrightness = new int[block.getHeight()];
		Arrays.fill(rowBrightness, 0);
		for (int c = block.getLeftCol(); c < block.getRightCol(); c++) {
			for (int r = block.getTopRow(); r < block.getBottomRow(); r++)
				rowBrightness[r - block.getTopRow()] += brightness[c][r];
		}
		for (int r = 0; r < rowBrightness.length; r++)
			rowBrightness[r] /= block.getWidth();
		return rowBrightness;
	}
	
	private static int[] getRowPixelCount(ImagePartRectangle block, byte[][] brightness) {
		int[] rowPixelCount = new int[block.getHeight()];
		Arrays.fill(rowPixelCount, 0);
		for (int c = block.getLeftCol(); c < block.getRightCol(); c++)
			for (int r = block.getTopRow(); r < block.getBottomRow(); r++) {
				if (brightness[c][r] < 120)
					rowPixelCount[r - block.getTopRow()]++;
			}
		return rowPixelCount;
	}
	
	private static int getHistogramAverage(int[] histogram) {
		if (histogram.length == 0)
			return -1;
		int histogramSum = 0;
		for (int h = 0; h < histogram.length; h++)
			histogramSum += histogram[h];
		return ((histogramSum + (histogram.length / 2)) / histogram.length);
	}
	
	private static HistogramRiseDrop[] getHistgramMaxRises(int[] histogram, int histogramAvg) {
		List maxRiseRowList = new ArrayList();
		for (int r = 1; r < histogram.length; r++) {
			if ((histogram[r] >= histogramAvg) && (histogram[r-1] < histogramAvg)) {
				int maxRise = 0;
				int maxRiseRow = -1;
				for (int rr = Math.max((r-1), 1); rr < Math.min((r+2), histogram.length); rr++) {
					int rise = (histogram[rr] - histogram[rr-1]);
					System.out.println(rr + ": Got rise of " + rise + " from " + histogram[rr-1] + " to " + histogram[rr] + " through average of " + histogramAvg);
					if (rise > maxRise) {
						maxRise = rise;
						maxRiseRow = rr;
					}
				}
				if (maxRiseRow != -1) {
					maxRiseRowList.add(new HistogramRiseDrop(maxRiseRow, maxRise));
					System.out.println(" ==> Got maximum rise of " + maxRise + " at " + maxRiseRow);
				}
			}
		}
		return ((HistogramRiseDrop[]) maxRiseRowList.toArray(new HistogramRiseDrop[maxRiseRowList.size()]));
	}
	
	private static HistogramRiseDrop[] getHistgramMaxDrops(int[] histogram, int histogramAvg) {
		List maxDropRowList = new ArrayList();
		for (int r = 1; r < histogram.length; r++) {
			if ((histogram[r] < histogramAvg) && (histogram[r-1] >= histogramAvg)) {
				int maxDrop = 0;
				int maxDropRow = -1;
				for (int dr = Math.max((r-1), 1); dr < Math.min((r+2), histogram.length); dr++) {
					int drop = (histogram[dr-1] - histogram[dr]);
					System.out.println(dr + ": Got drop of " + drop + " from " + histogram[dr-1] + " to " + histogram[dr] + " through average of " + histogramAvg);
					if (drop > maxDrop) {
						maxDrop = drop;
						maxDropRow = (dr-1);
					}
				}
				if (maxDropRow != -1) {
					maxDropRowList.add(new HistogramRiseDrop(maxDropRow, maxDrop));
					System.out.println(" ==> Got maximum drop of " + maxDrop + " at " + maxDropRow);
				}
			}
		}
		return ((HistogramRiseDrop[]) maxDropRowList.toArray(new HistogramRiseDrop[maxDropRowList.size()]));
	}
	
	private static class HistogramRiseDrop {
		final int row;
		final int delta;
		HistogramRiseDrop(int row, int delta) {
			this.row = row;
			this.delta = delta;
		}
	}
	
	private static HistogramRiseDrop[] merge(HistogramRiseDrop[] h1, HistogramRiseDrop[] h2) {
		HistogramRiseDrop[] h = new HistogramRiseDrop[h1.length + h2.length];
		System.arraycopy(h1, 0, h, 0, h1.length);
		System.arraycopy(h2, 0, h, h1.length, h2.length);
		return h;
	}
	
	private static int[] getHighCenters(HistogramRiseDrop[] rises, HistogramRiseDrop[] drops) {
		ArrayList highCenterList = new ArrayList();
		int lastDrop = -1;
		for (int r = 0; r < rises.length; r++) {
			for (int d = 0; d < drops.length; d++)
				if (drops[d].row > rises[r].row) {
					if (drops[d].row == lastDrop)
						highCenterList.remove(highCenterList.size()-1);
					lastDrop = drops[d].row;
					highCenterList.add(new Integer((rises[r].row + drops[d].row + 1) / 2));
					break;
				}
		}
		int[] highCenters = new int[highCenterList.size()];
		for (int c = 0; c < highCenterList.size(); c++)
			highCenters[c] = ((Integer) highCenterList.get(c)).intValue();
		return highCenters;
	}
	
	private static int[] getHighWidths(HistogramRiseDrop[] rises, HistogramRiseDrop[] drops) {
		ArrayList highWidthList = new ArrayList();
		int lastDrop = -1;
		for (int r = 0; r < rises.length; r++) {
			for (int d = 0; d < drops.length; d++)
				if (drops[d].row > rises[r].row) {
					if (drops[d].row == lastDrop)
						highWidthList.remove(highWidthList.size()-1);
					lastDrop = drops[d].row;
					highWidthList.add(new Integer(drops[d].row - rises[r].row));
					break;
				}
		}
		int[] highWidths = new int[highWidthList.size()];
		for (int c = 0; c < highWidthList.size(); c++)
			highWidths[c] = ((Integer) highWidthList.get(c)).intValue();
		return highWidths;
	}
	
	private static int[] getLowCenters(HistogramRiseDrop[] rises, HistogramRiseDrop[] drops) {
		ArrayList lowCenterList = new ArrayList();
		int lastRise = -1;
		for (int d = 0; d < drops.length; d++) {
			for (int r = 0; r < rises.length; r++)
				if (rises[r].row > drops[d].row) {
					if (rises[r].row == lastRise)
						lowCenterList.remove(lowCenterList.size()-1);
					lastRise = rises[r].row;
					lowCenterList.add(new Integer((drops[d].row + rises[r].row + 1) / 2));
					break;
				}
		}
		int[] lowCenters = new int[lowCenterList.size()];
		for (int c = 0; c < lowCenterList.size(); c++)
			lowCenters[c] = ((Integer) lowCenterList.get(c)).intValue();
		return lowCenters;
	}
	
	private static int getDistanceMin(int[] rows) {
		int rowDistanceMin = Integer.MAX_VALUE;
		for (int r = 1; r < rows.length; r++)
			rowDistanceMin = Math.min((rows[r] - rows[r-1]), rowDistanceMin);
		return rowDistanceMin;
	}
	
	private static int getDistanceMax(int[] rows) {
		int rowDistanceMax = 0;
		for (int r = 1; r < rows.length; r++)
			rowDistanceMax = Math.max((rows[r] - rows[r-1]), rowDistanceMax);
		return rowDistanceMax;
	}
	
	private static float getDistanceAverage(int[] rows) {
		float rowDistanceSum = 0;
		for (int r = 1; r < rows.length; r++)
			rowDistanceSum += (rows[r] - rows[r-1]);
		return (rowDistanceSum / (rows.length - 1));
	}
	
	/**
	 * Analyze the inner structure of a single text block, represented as an
	 * atomic region. The block object representing the inner structure of the
	 * argument region can be retrieved via the getBlock() method.
	 * @param region the text block to analyze
	 * @param dpi the resolution of the underlying page image
	 * @param existingWords bounding boxes of words already known to be in the
	 *            block
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 */
	public static void getBlockStructure(Region region, int dpi, BoundingBox[] existingWords, ProgressMonitor pm) {
		Block block = new Block(region.bounds);
		getBlockStructure(block, dpi, existingWords, pm);
		region.setBlock(block);
	}
	
	/**
	 * Analyze the inner structure of a single text block.
	 * @param block the text block to analyze
	 * @param dpi the resolution of the underlying page image
	 * @param existingWords bounding boxes of words already known to be in the
	 *            block
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 */
	public static void getBlockStructure(Block block, int dpi, BoundingBox[] existingWords, ProgressMonitor pm) {
		
		//	compute margin thresholds
		int minVerticalBlockMargin = (dpi / 10); // TODO find out if this makes sense (will turn out in the long haul only, though)
		int minLineMargin = (dpi / 40); // TODO find out if this makes sense (will turn out in the long haul only, though)
		int minWordMargin = (dpi / 40); // TODO find out if this makes sense (will turn out in the long haul only, though)
		
		//	find text lines and words
		if (existingWords == null)
			getBlockStructure(block, dpi, minVerticalBlockMargin, minLineMargin, minWordMargin, pm);
		else getBlockStructure(block, existingWords, pm);
		
		//	catch empty blocks larger than half an inch in both dimensions, might be (to-parse) table 
		if (block.isEmpty() && (dpi < ((block.bounds.rightCol - block.bounds.leftCol) * 2)) && (dpi < ((block.bounds.bottomRow - block.bounds.topRow) * 2)))
			analyzeTable(block, dpi, existingWords, pm);
	}
	
	
	/**
	 * Analyze the inner structure of a single text block to individual lines,
	 * but not words. The argument minimum margins are used as they are, i.e.,
	 * as absolute values without further scaling. It is the responsibility of
	 * client code to specify values appropriate for the given DPI number.
	 * @param block the text block to analyze
	 * @param dpi the resolution of the underlying page image
	/**
	 * Analyze the inner structure of a single text block. The argument minimum
	 * margins are used as they are, i.e., as absolute values without further
	 * scaling. It is the responsibility of client code to specify values
	 * appropriate for the given DPI number.
	 * @param block the text block to analyze
	 * @param dpi the resolution of the underlying page image
	 * @param minVerticalBlockMargin the minimum number of white pixels above
	 *            and below a text block (the margin between two text blocks in
	 *            the same column)
	 * @param minLineMargin the minimum number of white pixels rows between two
	 *            lines of text
	 * @param minWordMargin the minimum number of white pixels columns between
	 *            two adjacent words
	 * @param existingWords bounding boxes of words already known to be in the
	 *            block
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 */
	public static void getBlockStructure(Block block, int dpi, int minVerticalBlockMargin, int minLineMargin, int minWordMargin, BoundingBox[] existingWords, ProgressMonitor pm) {
		
		//	find text lines and words
		if (existingWords == null)
			getBlockStructure(block, dpi, minVerticalBlockMargin, minLineMargin, minWordMargin, pm);
		else getBlockStructure(block, existingWords, pm);
		
		//	catch empty blocks larger than half an inch in both dimensions, might be (to-parse) table 
		if (block.isEmpty() && (dpi < ((block.bounds.rightCol - block.bounds.leftCol) * 2)) && (dpi < ((block.bounds.bottomRow - block.bounds.topRow) * 2)))
			analyzeTable(block, dpi, existingWords, pm);
	}
	
	private static void analyzeTable(Block block, int dpi, BoundingBox[] existingWords, ProgressMonitor pm) {
//		
//		//	run Sobel gradient analysis
//		float[][] xs = getSobelGrid(block.bounds, xSobel);
//		System.out.println("Sobel-X computed:");
////		for (int r = 0; r < xs[0].length; r++) {
////			System.out.print(" ");
////			for (int c = 0; c < xs.length; c++)
////				System.out.print(" " + Math.round(xs[c][r]));
////			System.out.println();
////		}
//		float[][] ys = getSobelGrid(block.bounds, ySobel);
//		System.out.println("Sobel-Y computed:");
////		for (int r = 0; r < ys[0].length; r++) {
////			System.out.print(" ");
////			for (int c = 0; c < ys.length; c++)
////				System.out.print(" " + Math.round(ys[c][r]));
////			System.out.println();
////		}
//		float[][] sobel = new float[xs.length][xs[0].length];
//		for (int c = 0; c < xs.length; c++) {
//			for (int r = 0; r < xs[c].length; r++)
//				sobel[c][r] = xs[c][r] + ys[c][r];
//		}
//		System.out.println("Sobel computed:");
		
		//	TODOne consider using pattern recognition for finding grid lines instead (might be possible to copy from zigzag splitting, exchanging white and black)
		
		//	copy block image
		BufferedImage blockImage = block.bounds.toImage().getImage();
		
		//	paint copied image white where original has black stripes
		byte[][] brightness = block.bounds.ai.getBrightness();
		
		//	collect grid line bounds for later cell merging
		ArrayList gridLines = new ArrayList();
		
		//	do horizontal analysis
		int minHorizontalPartLength = (dpi / 2); // a table narrower than half an inch if very unlikely
		TreeSet hMatchRows = new TreeSet();
		for (int r = block.bounds.topRow; r < block.bounds.bottomRow; r++) {
			for (int c = block.bounds.leftCol; c < (block.bounds.rightCol - minHorizontalPartLength); c++) {
				if (brightness[c][r] == 127)
					continue;
				
				int nonWhiteCount = 0;
				int whiteCount = 0;
				int whiteLength = 0;
				int ec = c;
				for (int lc = c; lc < block.bounds.rightCol; lc++) {
					if (brightness[lc][r] < 127) {
						nonWhiteCount++;
						whiteLength = 0;
						ec = lc;
					}
					else {
						whiteCount++;
						whiteLength++;
						if (nonWhiteCount < (whiteCount * 10)) // less than 90% non-white
							break;
						if (whiteLength < (dpi / 100)) // too wide a gap, more than one fourth of a millimeter
							break;
					}
				}
//				while ((ec < block.bounds.rightCol) && (brightness[ec][r] < 127))
//					ec++;
				
				if ((ec - c) >= minHorizontalPartLength) {
					for (int w = c; w < ec; w++)
						blockImage.setRGB((w - block.bounds.leftCol), (r - block.bounds.topRow), whiteRgb);
					hMatchRows.add(new Integer(r));
					gridLines.add(block.bounds.getSubRectangle(c, ec, r, (r+1)));
				}
				
				c = ec;
			}
		}
		
		//	do vertical analysis
		int minVerticalPartLength = (dpi / 3); // a table lower than a third of an inch if very unlikely
		TreeSet vMatchCols = new TreeSet();
		for (int c = block.bounds.leftCol; c < block.bounds.rightCol; c++) {
			for (int r = block.bounds.topRow; r < (block.bounds.bottomRow - minVerticalPartLength); r++) {
				if (brightness[c][r] == 127)
					continue;
				
				int nonWhiteCount = 0;
				int whiteCount = 0;
				int whiteLength = 0;
				int er = r;
				for (int lr = r; lr < block.bounds.bottomRow; lr++) {
					if (brightness[c][lr] < 127) {
						nonWhiteCount++;
						whiteLength = 0;
						er = lr;
					}
					else {
						whiteCount++;
						whiteLength++;
						if (nonWhiteCount < (whiteCount * 10)) // less than 90% non-white
							break;
						if (whiteLength < (dpi / 100)) // too wide a gap, more than one fourth of a millimeter
							break;
					}
				}
//				while ((er < block.bounds.bottomRow) && (brightness[c][er] < 127))
//					er++;
				
				if ((er - r) >= minVerticalPartLength) {
					for (int w = r; w < er; w++)
						blockImage.setRGB((c - block.bounds.leftCol), (w - block.bounds.topRow), whiteRgb);
					vMatchCols.add(new Integer(c));
					gridLines.add(block.bounds.getSubRectangle(c, (c+1), r, er));
				}
				
				r = er;
			}
		}
		
		//	test criterion: replacements at least two columns and rows, one close to each block boundary, and in at most a tenth of the columns and a fifth of the rows
		if ((hMatchRows.size() < 2) || (vMatchCols.size() < 2))
			return;
		if (((hMatchRows.size() * 5) > (block.bounds.bottomRow - block.bounds.topRow)) || ((vMatchCols.size() * 10) > (block.bounds.rightCol - block.bounds.leftCol)))
			return;
		
		//	test if outmost matches are close to block bounds
		int hMatchMin = ((Integer) hMatchRows.first()).intValue();
		if ((hMatchMin - block.bounds.topRow) > (block.bounds.bottomRow - hMatchMin))
			return;
		int hMatchMax = ((Integer) hMatchRows.last()).intValue();
		if ((hMatchMax - block.bounds.topRow) < (block.bounds.bottomRow - hMatchMax))
			return;
		int vMatchMin = ((Integer) vMatchCols.first()).intValue();
		if ((vMatchMin - block.bounds.leftCol) > (block.bounds.rightCol - vMatchMin))
			return;
		int vMatchMax = ((Integer) vMatchCols.last()).intValue();
		if ((vMatchMax - block.bounds.leftCol) < (block.bounds.rightCol - vMatchMax))
			return;
		
		//	TODOne also evaluate minimum and maximum match lengths (find out if even necessary, though)
		
		//	evaluate clustering of matching rows and columns (at least table rows are likely somewhat equidistant)
		int minSignificantDistance = (dpi / 60); // somewhat less than half a millimeter
		ArrayList hMatchRowGroups = new ArrayList();
		TreeSet hMatchRowGroup = new TreeSet();
		int lastHMatchRow = -minSignificantDistance;
		for (Iterator mit = hMatchRows.iterator(); mit.hasNext();) {
			Integer hMatchRow = ((Integer) mit.next());
			
			//	group ends, store it and start new one
			if (((hMatchRow.intValue() - lastHMatchRow) >= minSignificantDistance) && (hMatchRowGroup.size() != 0)) {
				hMatchRowGroups.add(hMatchRowGroup);
				hMatchRowGroup = new TreeSet();
			}
			
			//	add 
			hMatchRowGroup.add(hMatchRow);
			lastHMatchRow = hMatchRow.intValue();
		}
		
		//	store last group
		if (hMatchRowGroup.size() != 0)
			hMatchRowGroups.add(hMatchRowGroup);
		
		//	clean up column separators that might have survived initial grid detenction due to low table row height
		for (int g = 1; g < hMatchRowGroups.size(); g++) {
			int top = ((Integer) ((TreeSet) hMatchRowGroups.get(g-1)).last()).intValue();
			int bottom = ((Integer) ((TreeSet) hMatchRowGroups.get(g)).first()).intValue();
			for (int c = block.bounds.leftCol; c < block.bounds.rightCol; c++) {
				boolean clean = true;
				for (int r = top; clean && (r < bottom); r++)
					clean = (clean && (brightness[c][r] < 127));
				if (clean) {
					for (int r = top; clean && (r < bottom); r++)
						blockImage.setRGB((c - block.bounds.leftCol), (r - block.bounds.topRow), whiteRgb);
				}
			}
		}
		
		//	compute vertical match groups / grid line positions
		ArrayList vMatchColGroups = new ArrayList();
		TreeSet vMatchColGroup = new TreeSet();
		int lastVMatchCol = -minSignificantDistance;
		for (Iterator mit = vMatchCols.iterator(); mit.hasNext();) {
			Integer vMatchCol = ((Integer) mit.next());
			
			//	group ends, store it and start new one
			if (((vMatchCol.intValue() - lastVMatchCol) >= minSignificantDistance) && (vMatchColGroup.size() != 0)) {
				vMatchColGroups.add(vMatchColGroup);
				vMatchColGroup = new TreeSet();
			}
			
			//	add 
			vMatchColGroup.add(vMatchCol);
			lastVMatchCol = vMatchCol.intValue();
		}
		
		//	store last group
		if (vMatchColGroup.size() != 0)
			vMatchColGroups.add(vMatchColGroup);
		
		//	this one is likely a table
		block.isTable = true;
		
		//	extract table cells
		ImagePartRectangle tBounds = new ImagePartRectangle(Imaging.wrapImage(blockImage, null));
		Block tBlock = new Block(tBounds);
		int minWordMargin = (dpi / 30); // TODOne_above find out if this makes sense (will turn out in the long haul only, though)
		int minVerticalBlockMargin = minWordMargin; // using same as word margin here, as we already know we have a coherent table
		if (existingWords == null)
			getBlockStructure(tBlock, dpi, minVerticalBlockMargin, 1, minWordMargin, pm);
		else getBlockStructure(tBlock, existingWords, pm);
		
		//	adjust table content back to original table block boundaries, and wrap lines in cells
		Line[] tLines = tBlock.getLines();
		ArrayList cells = new ArrayList();
		for (int l = 0; l < tLines.length; l++) {
			TableCell cell = new TableCell(block.bounds.getSubRectangle(
					(tLines[l].bounds.leftCol + block.bounds.leftCol),
					(tLines[l].bounds.rightCol + block.bounds.leftCol),
					(tLines[l].bounds.topRow + block.bounds.topRow),
					(tLines[l].bounds.bottomRow + block.bounds.topRow)
				));
			Line line = new Line(block.bounds.getSubRectangle(
					(tLines[l].bounds.leftCol + block.bounds.leftCol),
					(tLines[l].bounds.rightCol + block.bounds.leftCol),
					(tLines[l].bounds.topRow + block.bounds.topRow),
					(tLines[l].bounds.bottomRow + block.bounds.topRow)
				));
			Word[] tWords = tLines[l].getWords();
			for (int w = 0; w < tWords.length; w++) {
				line.addWord(new Word(block.bounds.getSubRectangle(
						(tWords[w].bounds.leftCol + block.bounds.leftCol),
						(tWords[w].bounds.rightCol + block.bounds.leftCol),
						(tWords[w].bounds.topRow + block.bounds.topRow),
						(tWords[w].bounds.bottomRow + block.bounds.topRow)
					)));
			}
			cell.addLine(line);
			cells.add(cell);
		}
//		
//		//	TODOne remove this (and all rendering) after tests
//		try {
//			ImageIO.write(blockImage, "png", new File("E:/Testdaten/PdfExtract/TableImage." + System.currentTimeMillis() + ".png"));
//		} catch (IOException ioe) {}
		
		//	compare distance between centroids of horizontal MatchRowGroups from grid elimination
		ArrayList hMatchRowGroupCentroids = new ArrayList();
		for (int g = 0; g < hMatchRowGroups.size(); g++) {
			int min = ((Integer) ((TreeSet) hMatchRowGroups.get(g)).first()).intValue();
			int max = ((Integer) ((TreeSet) hMatchRowGroups.get(g)).last()).intValue();
			hMatchRowGroupCentroids.add(new Integer((min + max) / 2));
		}
		int minRowCentroidDistance = block.bounds.bottomRow;
		int maxRowCentroidDistance = 0;
		for (int c = 1; c < hMatchRowGroupCentroids.size(); c++) {
			int tc = ((Integer) hMatchRowGroupCentroids.get(c-1)).intValue();
			int bc = ((Integer) hMatchRowGroupCentroids.get(c)).intValue();
			int cd = (bc - tc);
			minRowCentroidDistance = Math.min(minRowCentroidDistance, cd);
			maxRowCentroidDistance = Math.max(maxRowCentroidDistance, cd);
		}
		
		//	compare distance between centroids of vertical MatchColGroups from grid elimination
		ArrayList vMatchColGroupCentroids = new ArrayList();
		for (int g = 0; g < vMatchColGroups.size(); g++) {
			int min = ((Integer) ((TreeSet) vMatchColGroups.get(g)).first()).intValue();
			int max = ((Integer) ((TreeSet) vMatchColGroups.get(g)).last()).intValue();
			vMatchColGroupCentroids.add(new Integer((min + max) / 2));
		}
		int minColCentroidDistance = block.bounds.rightCol;
		int maxColCentroidDistance = 0;
		for (int c = 1; c < vMatchColGroupCentroids.size(); c++) {
			int lc = ((Integer) vMatchColGroupCentroids.get(c-1)).intValue();
			int rc = ((Integer) vMatchColGroupCentroids.get(c)).intValue();
			int cd = (rc - lc);
			minColCentroidDistance = Math.min(minColCentroidDistance, cd);
			maxColCentroidDistance = Math.max(maxColCentroidDistance, cd);
		}
		
		//	sort cells top-down for merging
		Collections.sort(cells, new Comparator() {
			public int compare(Object o1, Object o2) {
				TableCell c1 = ((TableCell) o1);
				TableCell c2 = ((TableCell) o2);
				return (c1.bounds.topRow - c2.bounds.topRow);
			}
		});
		
		//	count inner centroids (increment each by 1 and their product is the number of fields, save for multi-row or multi-column cells)
		int hInnerCentroids = 1;
		for (int h = 0; h < hMatchRowGroupCentroids.size(); h++) {
			int hc = ((Integer) hMatchRowGroupCentroids.get(h)).intValue();
			boolean ca = false;
			boolean cb = false;
			for (int c = 0; c < cells.size(); c++) {
				TableCell cell = ((TableCell) cells.get(c));
				if (cell.bounds.bottomRow < hc)
					ca = true;
				if (hc < cell.bounds.topRow)
					cb = true;
				if (ca && cb) {
					hInnerCentroids++;
					break;
				}
			}
		}
		int vInnerCentroids = 1;
		for (int v = 0; v < vMatchColGroupCentroids.size(); v++) {
			int vc = ((Integer) vMatchColGroupCentroids.get(v)).intValue();
			boolean cl = false;
			boolean cr = false;
			for (int c = 0; c < cells.size(); c++) {
				TableCell cell = ((TableCell) cells.get(c));
				if (cell.bounds.rightCol < vc)
					cl = true;
				if (vc < cell.bounds.leftCol)
					cr = true;
				if (cl && cr) {
					vInnerCentroids++;
					break;
				}
			}
		}
		boolean gridIncomplete = false;
		//	this definition should catch the desired tables, as an incomplete grid gets more and more unlikely with more inner grid lines painted
		gridIncomplete = (((minRowCentroidDistance * hInnerCentroids) < maxRowCentroidDistance) || ((minColCentroidDistance * vInnerCentroids) < maxColCentroidDistance));
		
		//	grid might be incomplete ==> be careful with merging cells
		if (gridIncomplete) {
			
			//	merge cells that are on top of one another if there is another cell overlapping them on the side (probably a two-line entry)
			for (int c = 0; c < cells.size(); c++) {
				TableCell cell = ((TableCell) cells.get(c));
				for (int m = (c+1); m < cells.size(); m++) {
					TableCell mCell = ((TableCell) cells.get(m));
					if ((mCell.bounds.rightCol < cell.bounds.leftCol) || (cell.bounds.rightCol < mCell.bounds.leftCol))
						continue;
					boolean merge = false;
					for (int t = 0; t < cells.size(); t++) {
						if ((t == c) || (t == m))
							continue;
						TableCell tCell = ((TableCell) cells.get(t));
						boolean oc = ((cell.bounds.topRow < tCell.bounds.bottomRow) && (tCell.bounds.topRow < cell.bounds.bottomRow));
						boolean om = ((mCell.bounds.topRow < tCell.bounds.bottomRow) && (tCell.bounds.topRow < mCell.bounds.bottomRow));
						if (oc && om) {
							merge = true;
							break;
						}
					}
					if (merge) {
						ImagePartRectangle[] cbs = {cell.bounds, mCell.bounds};
						ImagePartRectangle cb = Imaging.getHull(cbs);
						Imaging.copyBounds(cb, cell.bounds);
						cell.lines.addAll(mCell.lines);
						cells.remove(m--);
					}
				}
			}
			
			//	sort cells top-down and left-right for row grouping
			Collections.sort(cells, new Comparator() {
				public int compare(Object o1, Object o2) {
					TableCell c1 = ((TableCell) o1);
					TableCell c2 = ((TableCell) o2);
					if ((c1.bounds.topRow < c2.bounds.bottomRow) && (c2.bounds.topRow < c1.bounds.bottomRow))
						return (c1.bounds.leftCol - c2.bounds.leftCol);
					else return (c1.bounds.topRow - c2.bounds.topRow);
				}
			});
			
			//	group cells into rows
			ArrayList rowCells = new ArrayList();
			TableCell lCell = null;
			for (int c = 0; c < cells.size(); c++) {
				TableCell cell = ((TableCell) cells.get(c));
				if ((lCell != null) && ((cell.bounds.topRow >= lCell.bounds.bottomRow) || (lCell.bounds.topRow >= cell.bounds.bottomRow))) {
					ImagePartRectangle[] cbs = new ImagePartRectangle[rowCells.size()];
					for (int r = 0; r < rowCells.size(); r++)
						cbs[r] = ((TableCell) rowCells.get(r)).bounds;
					ImagePartRectangle rb = Imaging.getHull(cbs);
					TableRow row = new TableRow(rb);
					for (int r = 0; r < rowCells.size(); r++)
						row.addCell((TableCell) rowCells.get(r));
					block.addRow(row);
					rowCells.clear();
				}
				rowCells.add(cell);
				lCell = cell;
			}
			
			//	store table row
			if (rowCells.size() != 0) {
				ImagePartRectangle[] cbs = new ImagePartRectangle[rowCells.size()];
				for (int r = 0; r < rowCells.size(); r++)
					cbs[r] = ((TableCell) rowCells.get(r)).bounds;
				ImagePartRectangle rb = Imaging.getHull(cbs);
				TableRow row = new TableRow(rb);
				for (int r = 0; r < rowCells.size(); r++)
					row.addCell((TableCell) rowCells.get(r));
				block.addRow(row);
			}
		}
		
		//	we have a full grid ==> use it
		else {
			
			//	merge cells that are on top of one another with no grid line in between into table cells
			for (int c = 0; c < cells.size(); c++) {
				TableCell cell = ((TableCell) cells.get(c));
				for (int m = (c+1); m < cells.size(); m++) {
					TableCell mCell = ((TableCell) cells.get(m));
					if ((mCell.bounds.rightCol < cell.bounds.leftCol) || (cell.bounds.rightCol < mCell.bounds.leftCol))
						continue;
					ImagePartRectangle[] cbs = {cell.bounds, mCell.bounds};
					ImagePartRectangle mb = Imaging.getHull(cbs);
					boolean merge = true;
					for (int g = 0; g < gridLines.size(); g++) {
						ImagePartRectangle gridLine = ((ImagePartRectangle) gridLines.get(g));
						if ((mb.rightCol <= gridLine.leftCol) || (gridLine.rightCol <= mb.leftCol))
							continue;
						if ((mb.bottomRow <= gridLine.topRow) || (gridLine.bottomRow <= mb.topRow))
							continue;
						merge = false;
						break;
					}
					if (merge) {
						Imaging.copyBounds(mb, cell.bounds);
						cell.lines.addAll(mCell.lines);
						cells.remove(m--);
					}
				}
			}
			
			//	compute row span of each cell
			for (int c = 0; c < cells.size(); c++) {
				TableCell cell = ((TableCell) cells.get(c));
				for (int t = 0; t < hMatchRowGroupCentroids.size(); t++) {
					Integer mgc = ((Integer) hMatchRowGroupCentroids.get(t));
					if ((cell.bounds.topRow < mgc.intValue()) && (mgc.intValue() < cell.bounds.bottomRow))
						cell.rowSpan++;
				}
			}
			
			//	sort cells left-right for row grouping
			Collections.sort(cells, new Comparator() {
				public int compare(Object o1, Object o2) {
					TableCell c1 = ((TableCell) o1);
					TableCell c2 = ((TableCell) o2);
					return (c1.bounds.leftCol - c2.bounds.leftCol);
				}
			});
			
			//	group cells into rows
			for (int r = 0; r < hMatchRowGroupCentroids.size(); r++) {
				ArrayList rowCells = new ArrayList();
				Integer mgc = ((Integer) hMatchRowGroupCentroids.get(r));
				for (int c = 0; c < cells.size(); c++) {
					TableCell cell = ((TableCell) cells.get(c));
					if (cell.bounds.topRow < mgc.intValue()) {
						rowCells.add(cell);
						cells.remove(c--);
					}
				}
				if (rowCells.isEmpty())
					continue;
				
				ImagePartRectangle[] cbs = new ImagePartRectangle[rowCells.size()];
				for (int c = 0; c < rowCells.size(); c++)
					cbs[c] = ((TableCell) rowCells.get(c)).bounds;
				ImagePartRectangle rb = Imaging.getHull(cbs);
				TableRow row = new TableRow(rb);
				for (int c = 0; c < rowCells.size(); c++)
					row.addCell((TableCell) rowCells.get(c));
				block.addRow(row);
			}
			if (cells.size() != 0) {
				ImagePartRectangle[] cbs = new ImagePartRectangle[cells.size()];
				for (int c = 0; c < cells.size(); c++)
					cbs[c] = ((TableCell) cells.get(c)).bounds;
				ImagePartRectangle rb = Imaging.getHull(cbs);
				TableRow row = new TableRow(rb);
				for (int c = 0; c < cells.size(); c++)
					row.addCell((TableCell) cells.get(c));
				block.addRow(row);
			}
		}
		
		//	compute col span of cells
		TableRow[] rows = block.getRows();
		for (int r = 0; r < rows.length; r++) {
			TableCell[] rCells = rows[r].getCells();
			for (int c = 0; c < rCells.length; c++) {
				for (int tr = 0; tr < rows.length; tr++) {
					if (tr == r)
						continue;
					TableCell[] tCells = rows[tr].getCells();
					int tCellsSpanned = 0;
					for (int tc = 0; tc < tCells.length; tc++) {
						if ((rCells[c].bounds.rightCol > tCells[tc].bounds.leftCol) && (tCells[tc].bounds.rightCol > rCells[c].bounds.leftCol))
							tCellsSpanned++;
					}
					rCells[c].colSpan = Math.max(rCells[c].colSpan, tCellsSpanned);
				}
			}
		}
		
		//	adjust row bounds to very small so empty cells do not grow out of bounds
		for (int r = 0; r < rows.length; r++) {
			TableCell[] rCells = rows[r].getCells();
			for (int c = 0; c < rCells.length; c++) {
				rows[r].bounds.topRow = Math.max(rows[r].bounds.topRow, rCells[c].bounds.topRow);
				rows[r].bounds.bottomRow = Math.min(rows[r].bounds.bottomRow, rCells[c].bounds.bottomRow);
			}
		}
		
		//	build table grid lists
		ArrayList[] rowCells = new ArrayList[rows.length];
		for (int r = 0; r < rows.length; r++)
			rowCells[r] = new ArrayList();
		for (int r = 0; r < rows.length; r++) {
			TableCell[] rCells = rows[r].getCells();
			for (int c = 0; c < rCells.length; c++) {
				rowCells[r].add(rCells[c]);
				for (int s = 1; s < rCells[c].rowSpan; s++)
					rowCells[r+s].add(rCells[c]);
			}
		}
		for (int r = 0; r < rows.length; r++)
			Collections.sort(rowCells[r], new Comparator() {
				public int compare(Object o1, Object o2) {
					TableCell c1 = ((TableCell) o1);
					TableCell c2 = ((TableCell) o2);
					return (c1.bounds.leftCol - c2.bounds.leftCol);
				}
			});
		
		//	fill in empty cells
		for (int r = 0; r < rows.length; r++) {
			for (int c = 0; c <= rowCells[r].size(); c++) {
				int left = ((c == 0) ? block.bounds.leftCol : ((TableCell) rowCells[r].get(c-1)).bounds.rightCol);
				int right = ((c == rowCells[r].size()) ? block.bounds.rightCol : ((TableCell) rowCells[r].get(c)).bounds.leftCol);
				for (int tr = 0; tr < rows.length; tr++) {
					if (tr == r)
						continue;
					TableCell[] tCells = rows[tr].getCells();
					for (int tc = 0; tc < tCells.length; tc++) {
						if ((tCells[tc].bounds.leftCol < left) || (right < tCells[tc].bounds.rightCol))
							continue;
						
						//	generate filler cell
						TableCell fCell = new TableCell(block.bounds.getSubRectangle(
								(tCells[tc].bounds.leftCol),
								(tCells[tc].bounds.rightCol),
								(rows[r].bounds.topRow),
								(rows[r].bounds.bottomRow)
							));
						
						//	store cell in grid list
						rowCells[r].add(c, fCell);
						
						//	add cell to row
						rows[r].addCell(fCell);
						Collections.sort(rows[r].cells, new Comparator() {
							public int compare(Object o1, Object o2) {
								TableCell c1 = ((TableCell) o1);
								TableCell c2 = ((TableCell) o2);
								return (c1.bounds.leftCol - c2.bounds.leftCol);
							}
						});
						
						//	start over
						tc = tCells.length;
						tr = rows.length;
					}
				}
			}
		}
		
		//	adjust row bounds back to normal, ignoring rowspan
		for (int r = 0; r < rows.length; r++) {
			TableCell[] rCells = rows[r].getCells();
			for (int c = 0; c < rCells.length; c++) {
				rows[r].bounds.topRow = Math.min(rows[r].bounds.topRow, rCells[c].bounds.topRow);
				if (rCells[c].rowSpan < 2)
					rows[r].bounds.bottomRow = Math.max(rows[r].bounds.bottomRow, rCells[c].bounds.bottomRow);
			}
		}
	}
	
	private static final int whiteRgb = Color.WHITE.getRGB();
	
	private static void getBlockStructure(Block block, int dpi, int minVerticalBlockMargin, int minLineMargin, int minWordMargin, ProgressMonitor pm) {
		pm.setInfo("   - doing block " + block.getBoundingBox().toString());
		
		double maxSplitSlopeAngle;
		int minZigzagPartLength;
		double maxZigzagSplitSlopeAngle;
		int maxZigzagSlope;
		
		//	do conservative initial line split, so not to zigzag through page headers
		maxSplitSlopeAngle = 0.2;
		ImagePartRectangle[] lBlocks = Imaging.splitIntoRows(block.bounds, 1, maxSplitSlopeAngle, -1, 0);
		pm.setInfo("     - got " + lBlocks.length + " initial lines");
		
		//	split initial lines into columns
		ArrayList bLines = new ArrayList();
		for (int l = 0; l < lBlocks.length; l++) {
			lBlocks[l] = Imaging.narrowLeftAndRight(lBlocks[l]);
			if (lBlocks[l].isEmpty())
				continue;
			ImagePartRectangle[] cBlocks = Imaging.splitIntoColumns(lBlocks[l], (minVerticalBlockMargin * 3));
			if (cBlocks.length == 1)
				bLines.add(lBlocks[l]);
			else for (int c = 0; c < cBlocks.length; c++) {
				cBlocks[c] = Imaging.narrowTopAndBottom(cBlocks[c]);
				if (cBlocks[c].isEmpty())
					continue;
				bLines.add(cBlocks[c]);
			}
		}
		pm.setInfo("     - got " + bLines.size() + " col block lines at margin " + (minVerticalBlockMargin * 3));
		
		//	do more aggressive line splitting, but only for lines that are higher than a fifth of the dpi, as anything below that will hardly consist of two lines
		int minLineHeight = (dpi / 10); // a single line of text will rarely be lower than one thenth of an inch in total
		int minResplitHeight = (minLineHeight * 2); // the minimum height of two lines, we can securely keep anything below this threshold
		maxSplitSlopeAngle = 0.0;//0.2; zigzagging does it alone
		minZigzagPartLength = (dpi / 2);
		maxZigzagSplitSlopeAngle = 1.0;
		maxZigzagSlope = Math.abs((int) (Math.tan(maxZigzagSplitSlopeAngle * Math.PI / 180) * ((double) (block.bounds.rightCol - block.bounds.leftCol))));
		for (int l = 0; l < bLines.size(); l++) {
			ImagePartRectangle line = ((ImagePartRectangle) bLines.get(l));
			if ((line.bottomRow - line.topRow) < minResplitHeight)
				continue;
			lBlocks = Imaging.splitIntoRows(line, 1, maxSplitSlopeAngle, minZigzagPartLength, maxZigzagSlope);
			if (lBlocks.length > 1) {
				bLines.set(l, lBlocks[0]);
				for (int sl = 1; sl < lBlocks.length; sl++) {
					l++;
					bLines.add(l, lBlocks[sl]);
				}
			}
		}
		
		//	recover line blocks
		lBlocks = ((ImagePartRectangle[]) bLines.toArray(new ImagePartRectangle[bLines.size()]));
		pm.setInfo("     - got " + lBlocks.length + " block lines");
		if (lBlocks.length == 0)
			return;
		
		//	compute average line height
		int lineHeightSum = 0;
		int lineCount = 0;
		for (int l = 0; l < lBlocks.length; l++) {
			
			//	skip over lines higher than one inch
			if (dpi < (lBlocks[l].bottomRow - lBlocks[l].topRow))
				continue;
			
			//	count other lines
			lineHeightSum += (lBlocks[l].bottomRow - lBlocks[l].topRow);
			lineCount++;
		}
		int avgLineHeight = ((lineCount == 0) ? -1 : ((lineHeightSum + (lineCount / 2)) / lineCount));
		
		//	save everything higher than one tenth of an inch, no matter how big surrounding lines are
		int minWordHight = (dpi / 10);
		
		//	filter out lines narrower than one thirtieth of an inch (should still save single 1's)
		int minLineWidth = (dpi / 30);
		
		//	store lines in block
		ImagePartRectangle lostLine = null;
		for (int l = 0; l < lBlocks.length; l++) {
			lBlocks[l] = Imaging.narrowLeftAndRight(lBlocks[l]);
			int lineHeight = (lBlocks[l].bottomRow - lBlocks[l].topRow);
			
			//	keep aside very low lines, likely stains, etc. ... or dots of an i or j, accents of upper case latters, ...
			if (lineHeight < Math.min((avgLineHeight / 2), minWordHight)) {
				lostLine = lBlocks[l];
				continue;
			}
			
			//	filter out lines narrower than minimum width
			int lineWidth = (lBlocks[l].rightCol - lBlocks[l].leftCol);
			if (lineWidth < minLineWidth) {
				lostLine = null;
				continue;
			}
			
			//	join any below-minimum-height line to adjacent smaller-than-average line immediately below it (might be cut-off dot of an i or j)
			//	however, forget about accents of upper case letters, as they blow the line metrics anyways
			if ((lostLine != null)
					&& (lBlocks[l].leftCol <= lostLine.leftCol) && (lostLine.rightCol <= lBlocks[l].rightCol)
					&& (lineHeight < avgLineHeight)
				)
				lBlocks[l].topRow = Math.min(lBlocks[l].topRow, lostLine.topRow);
			block.addLine(new Line(lBlocks[l]));
			lostLine = null;
		}
		
		//	split lines into words
		for (int l = 0; l < block.lines.size(); l++) {
			Line line = ((Line) block.lines.get(l));
			pm.setInfo("     - doing line " + line.getBoundingBox().toString());
			ImagePartRectangle[] lwBlocks = Imaging.splitIntoColumns(line.bounds, minWordMargin);
			pm.setInfo("       - got " + lwBlocks.length + " raw words in line " + l + " at margin " + minWordMargin);
			
			//	split words at gaps at least twice as wide as word-local average, and at least half the global minimum margin
			ArrayList lwBlockList = new ArrayList();
			for (int w = 0; w < lwBlocks.length; w++)
				lwBlockList.add(lwBlocks[w]);
			
			//	refresh line word blocks
			if (lwBlocks.length < lwBlockList.size())
				lwBlocks = ((ImagePartRectangle[]) lwBlockList.toArray(new ImagePartRectangle[lwBlockList.size()]));
			
			//	filter words
			pm.setInfo("       - got " + lwBlocks.length + " words in line " + l);
			boolean omittedFirst = false;
			boolean omittedLast = false;
			for (int w = 0; w < lwBlocks.length; w++) {
				lwBlocks[w] = Imaging.narrowTopAndBottom(lwBlocks[w]);
				int minWidth = (lwBlocks[w].rightCol - lwBlocks[w].leftCol);
				int maxWidth = 0;
				int minHeight = (lwBlocks[w].bottomRow - lwBlocks[w].topRow);
				int maxHeight = 0;
				
				//	split into columns with margin 1, then determine minimum and maximum width, and apply horizontal filters to those
				ImagePartRectangle[] vBlocks = Imaging.splitIntoColumns(lwBlocks[w], 1);
				for (int v = 0; v < vBlocks.length; v++) {
					vBlocks[v] = Imaging.narrowTopAndBottom(vBlocks[v]);
					minWidth = Math.min(minWidth, (vBlocks[v].rightCol - vBlocks[v].leftCol));
					maxWidth = Math.max(maxWidth, (vBlocks[v].rightCol - vBlocks[v].leftCol));
				}
				
				//	split into rows with margin 1, then determine minimum and maximum heigth, and apply vertical filters to those
				ImagePartRectangle[] hBlocks = Imaging.splitIntoRows(lwBlocks[w], 1);
				for (int h = 0; h < hBlocks.length; h++) {
					hBlocks[h] = Imaging.narrowLeftAndRight(hBlocks[h]);
					minHeight = Math.min(minHeight, (hBlocks[h].bottomRow - hBlocks[h].topRow));
					maxHeight = Math.max(maxHeight, (hBlocks[h].bottomRow - hBlocks[h].topRow));
				}
				
				//	sort out everything sized at most one hundredth of an inch in both dimensions
				if ((maxWidth * maxHeight) <= ((dpi / 100) * (dpi / 100))) {
					pm.setInfo("       --> stain omitted for size below " + (dpi / 100) + " x " + (dpi / 100));
					if (w == 0)
						omittedFirst = true;
					omittedLast = true;
					lwBlocks[w] = null;
					continue;
				}
				
//				//	sort out everything larger than one inch in either dimension TODOne find out if this is safe ==> seems not so, as German compounds can be longer ...
//				else if ((dpi < minWidth) || (dpi < minHeight)) {
//					pm.setInfo("     - word omitted for massive size " + Math.max(minWidth, minHeight) + " exceeding " + dpi + " in either dimension");
//					if (w == 0)
//						omittedFirst = true;
//					omittedLast = true;
//					lwBlocks[w] = null;
//					continue;
//				}
				//	sort out everything larger than two inches in either dimension TODOne SEEMS SO find out if this is safe now
				else if (((dpi * 2) < minWidth) || ((dpi * 2) < minHeight)) {
					pm.setInfo("       --> word omitted for massive size " + Math.max(minWidth, minHeight) + " exceeding " + (2*dpi) + " in either dimension");
					if (w == 0)
						omittedFirst = true;
					omittedLast = true;
					lwBlocks[w] = null;
					continue;
				}
				
				//	store word in line
				omittedLast = false;
				line.addWord(new Word(lwBlocks[w]));
				pm.setInfo("       --> word added");
			}
			
			//	catch empty lines
			if (line.words.isEmpty())
				continue;
			pm.setInfo("       - got " + line.words.size() + " words in line " + l + ", ultimately");
			
			//	correct line bounds
			if (omittedFirst || omittedLast) {
				int left = line.bounds.rightCol;
				int right = line.bounds.leftCol;
				int top = line.bounds.bottomRow;
				int bottom = line.bounds.topRow;
				for (int w = 0; w < lwBlocks.length; w++) {
					if (lwBlocks[w] == null)
						continue;
					left = Math.min(left, lwBlocks[w].leftCol);
					right = Math.max(right, lwBlocks[w].rightCol);
					top = Math.min(top, lwBlocks[w].topRow);
					bottom = Math.max(bottom, lwBlocks[w].bottomRow);
				}
				line.bounds.leftCol = left;
				line.bounds.rightCol = right;
				line.bounds.topRow = top;
				line.bounds.bottomRow = bottom;
			}
		}
		
		adjustWordsAndLines(block);
	}
	
	private static int getBlockStructure(Block block, BoundingBox[] existingWords, ProgressMonitor pm) {
		BoundingBox[] existingBlockWords = extractOverlapping(block.bounds, existingWords);
		pm.setInfo("   - doing block " + block.getBoundingBox().toString());
		
		//	sort words left to right
		Arrays.sort(existingBlockWords, new Comparator() {
			public int compare(Object o1, Object o2) {
				return (((BoundingBox) o1).left - ((BoundingBox) o2).left);
			}
		});
//		
//		//	count number of word-occupied columns for each row in block, and compute average word height
//		int avgWordHeight = 0;
//		int[] rowWordColumns = new int[block.bounds.getHeight()];
//		Arrays.fill(rowWordColumns, 0);
//		for (int w = 0; w < existingBlockWords.length; w++) {
//			avgWordHeight += (existingBlockWords[w].bottom - existingBlockWords[w].top);
//			for (int r = Math.max(block.bounds.getTopRow(), existingBlockWords[w].top); r < Math.min(block.bounds.getBottomRow(), existingBlockWords[w].bottom); r++)
//				rowWordColumns[r - block.bounds.getTopRow()] += (existingBlockWords[w].right - existingBlockWords[w].left);
//		}
//		avgWordHeight /= existingBlockWords.length;
//		System.out.println("Average height of " + existingBlockWords.length + " block words is " + avgWordHeight);
//		
////		System.out.println("Checking row word columns:");
//		int wordHeightRowColumnSum = 0;
//		for (int r = 0; r < rowWordColumns.length; r++) {
//			wordHeightRowColumnSum += rowWordColumns[r];
//			if (avgWordHeight <= r)
//				wordHeightRowColumnSum -= rowWordColumns[r - avgWordHeight];
//			if ((r+1) < avgWordHeight)
//				continue;
//			for (int c = (r - avgWordHeight + 3); c < (r-1); c++) {
//				if (rowWordColumns[c] == 0)
//					continue;
//				if ((rowWordColumns[c] >= rowWordColumns[r - avgWordHeight + 1]) || (rowWordColumns[c] >= rowWordColumns[r - avgWordHeight + 2]))
//					continue;
//				if ((rowWordColumns[c] >= rowWordColumns[r]) || (rowWordColumns[c] >= rowWordColumns[r-1]))
//					continue;
//				if ((rowWordColumns[c] * 5) < (wordHeightRowColumnSum / avgWordHeight)) {
////					System.out.println(" - cleaning up row " + (c + block.bounds.getTopRow()) + " with minor " + rowWordColumns[c] + " word columns");
//					wordHeightRowColumnSum -= rowWordColumns[c];
//					rowWordColumns[c] = 0;
//				}
//			}
//		}
//		
//		//	compute minimum and maximum line height, as well as distances between line starts and line ends
//		int minLineHeight = block.bounds.getHeight();
//		int maxLineHeight = 0;
//		int lineTopRow = -1;
//		int lineCount = 0;
//		int lastLineTopRow = -1;
//		int minLineTopRowDist = block.bounds.getHeight();
//		int maxLineTopRowDist = 0;
//		int avgLineTopRowDist = 0;
//		int lastLineBottomRow = -1;
//		int minLineBottomRowDist = block.bounds.getHeight();
//		int maxLineBottomRowDist = 0;
//		int avgLineBottomRowDist = 0;
////		System.out.println("Evaluating row word columns:");
//		for (int r = 0; r < rowWordColumns.length; r++) {
////			System.out.println(" - " + (r + block.bounds.getTopRow()) + ": " + rowWordColumns[r]);
//			if (rowWordColumns[r] == 0) {
//				if (lineTopRow != -1) {
//					minLineHeight = Math.min(minLineHeight, (r - lineTopRow));
//					maxLineHeight = Math.max(maxLineHeight, (r - lineTopRow));
//					if (lastLineBottomRow != -1) {
//						minLineBottomRowDist = Math.min(minLineBottomRowDist, (r - lastLineBottomRow));
//						maxLineBottomRowDist = Math.max(maxLineBottomRowDist, (r - lastLineBottomRow));
//						avgLineBottomRowDist += (r - lastLineBottomRow);
//					}
//					lastLineBottomRow = r;
//					lineCount++;
//				}
//				lineTopRow = -1;
//			}
//			else if (lineTopRow == -1) {
//				lineTopRow = r;
//				if (lastLineTopRow != -1) {
//					minLineTopRowDist = Math.min(minLineTopRowDist, (lineTopRow - lastLineTopRow));
//					maxLineTopRowDist = Math.max(maxLineTopRowDist, (lineTopRow - lastLineTopRow));
//					avgLineTopRowDist += (lineTopRow - lastLineTopRow);
//				}
//				lastLineTopRow = r;
//			}
//		}
//		if (lineTopRow != -1) {
//			minLineHeight = Math.min(minLineHeight, (rowWordColumns.length - lineTopRow));
//			maxLineHeight = Math.max(maxLineHeight, (rowWordColumns.length - lineTopRow));
//			if (lastLineBottomRow != -1) {
//				minLineBottomRowDist = Math.min(minLineBottomRowDist, (rowWordColumns.length - lastLineBottomRow));
//				maxLineBottomRowDist = Math.max(maxLineBottomRowDist, (rowWordColumns.length - lastLineBottomRow));
//				avgLineBottomRowDist += (rowWordColumns.length - lastLineBottomRow);
//			}
//			lineCount++;
//		}
//		if (lineCount > 1) {
//			avgLineTopRowDist /= (lineCount-1);
//			avgLineBottomRowDist /= (lineCount-1);
//		}
//		System.out.println("Minimum line height is " + minLineHeight + ", maximum is " + maxLineHeight);
//		System.out.println("Average line top distance is " + avgLineTopRowDist + " (" + minLineTopRowDist + "," + maxLineTopRowDist + ")");
//		System.out.println("Average line bottom distance is " + avgLineBottomRowDist + " (" + minLineBottomRowDist + "," + maxLineBottomRowDist + ")");
//		
//		//	check plausibility
//		if ((maxLineHeight * 4) < (minLineHeight * 5)) {
//			System.out.println(" ==> Counting line split plausible in terms of line height");
//		}
//		if ((maxLineHeight * 4) < (avgWordHeight * 5)) {
//			System.out.println(" ==> Counting line split plausible in terms of line height vs. word height");
//		}
		
		//	group words into lines
		while (true) {
			ArrayList lineWords = new ArrayList();
			int lineLeft = -1;
			int lineRight = -1;
			int lineTop = -1;
			int lineBottom = -1;
			BoundingBox lastWord = null;
			for (int w = 0; w < existingBlockWords.length; w++) {
				if (existingBlockWords[w] == null)
					continue;
				
				//	start line
				if (lineWords.isEmpty()) {
					lineWords.add(existingBlockWords[w]);
					lineLeft = existingBlockWords[w].left;
					lineRight = existingBlockWords[w].right;
					lineTop = existingBlockWords[w].top;
					lineBottom = existingBlockWords[w].bottom;
					lastWord = existingBlockWords[w];
					existingBlockWords[w] = null;
					pm.setInfo("     - starting line with " + lastWord);
					continue;
				}
				
				//	this word is outside the line
				if (existingBlockWords[w].bottom < lineTop)
					continue;
				if (lineBottom < existingBlockWords[w].top)
					continue;
				
				//	word completely inside line (the most usual case)
				if ((existingBlockWords[w].top >= lineTop) && (existingBlockWords[w].bottom <= lineBottom)) {}
				
				//	line completely inside word (happens when starting line with bullet dash or the like)
				else if ((existingBlockWords[w].top <= lineTop) && (existingBlockWords[w].bottom >= lineBottom)) {}
				
				//	check for overlap
				else {
					int maxTop = Math.max(existingBlockWords[w].top, lineTop);
					int minBottom = Math.min(existingBlockWords[w].bottom, lineBottom);
					int overlap = (minBottom - maxTop);
					
					//	word over 50% inside line
					if ((existingBlockWords[w].bottom - existingBlockWords[w].top) < (overlap * 2)) {}
					
					//	line over 50% inside word
					else if ((lineBottom - lineTop) < (overlap * 2)) {}
					
					//	insufficient overlap
					else continue;
				}
				
				//	word overlaps with last (can happen due to in-word font change or tokenization) ==> merge them
				if (lastWord.right >= existingBlockWords[w].left) {
					lastWord = new BoundingBox(
							lastWord.left, 
							existingBlockWords[w].right, 
							Math.min(existingBlockWords[w].top, lastWord.top), 
							Math.max(existingBlockWords[w].bottom, lastWord.bottom)
						);
					lineWords.set((lineWords.size()-1), lastWord);
					pm.setInfo("       - merged overlapping words " + lastWord);
				}
				
				//	separate word, add it
				else {
					lastWord = existingBlockWords[w];
					lineWords.add(lastWord);
					pm.setInfo("       - added word " + lastWord);
				}
				
				//	adjust line bounds
				lineLeft = Math.min(existingBlockWords[w].left, lineLeft);
				lineRight = Math.max(existingBlockWords[w].right, lineRight);
				lineTop = Math.min(existingBlockWords[w].top, lineTop);
				lineBottom = Math.max(existingBlockWords[w].bottom, lineBottom);
				
				//	mark word as expended
				existingBlockWords[w] = null;
			}
			
			//	no words remaining
			if (lineWords.isEmpty())
				break;
			
			//	create and store line
			Line line = new Line(block.bounds.getSubRectangle(lineLeft, lineRight, lineTop, lineBottom));
			block.addLine(line);
			pm.setInfo("     - created line " + line.bounds.getId() + " with " + lineWords.size() + " words");
			
			//	add words
			for (int w = 0; w < lineWords.size(); w++) {
				BoundingBox word = ((BoundingBox) lineWords.get(w));
				line.addWord(new Word(line.bounds.getSubRectangle(word.left, word.right, word.top, word.bottom)));
			}
		}
		
		//	sort lines top to bottom
		Collections.sort(block.lines, new Comparator() {
			public int compare(Object o1, Object o2) {
				return (((Line) o1).bounds.topRow - ((Line) o2).bounds.topRow);
			}
		});
		
		//	easy to tell how many words we have ...
		return existingBlockWords.length;
	}
	
	private static BoundingBox[] extractOverlapping(ImagePartRectangle ipr, BoundingBox[] bbs) {
		ArrayList bbl = new ArrayList();
		for (int b = 0; b < bbs.length; b++) {
			if (overlaps(ipr, bbs[b]))
				bbl.add(bbs[b]);
		}
		return ((BoundingBox[]) bbl.toArray(new BoundingBox[bbl.size()]));
	}
	private static boolean overlaps(ImagePartRectangle ipr, BoundingBox bb) {
		if (bb.bottom <= ipr.topRow)
			return false;
		else if (ipr.bottomRow <= bb.top)
			return false;
		else if (bb.right <= ipr.leftCol)
			return false;
		else if (ipr.rightCol <= bb.left)
			return false;
		else return true;
	}
	
	private static final int wordAdjustmentSafetyMargin = 0;
	private static void adjustWordsAndLines(Block block) {
		byte[][] brightness = block.bounds.getImage().getBrightness();
		if (block.lineSplitClean())
			return;
		
		Line[] lines = block.getLines();
		if (lines.length == 0)
			return;
		
		int[] wordTopRows = new int[lines.length];
		int[] wordBottomRows = new int[lines.length];
		for (int l = 0; l < lines.length; l++) {
			wordTopRows[l] = lines[l].bounds.topRow;
			wordBottomRows[l] = lines[l].bounds.bottomRow;
			Word[] words = lines[l].getWords();
			if (words.length == 0)
				continue;
			
			//	above and below lines for quick access
			Word[] wordsAbove = ((l == 0) ? null : lines[l-1].getWords());
			Word[] wordsBelow = (((l+1) == lines.length) ? null : lines[l+1].getWords());
			
			//	expand words in all directions to cover letters fully, as sloped line cuts might have severed letters
			for (int w = 0; w < words.length; w++) {
				boolean expanded;
				
				//	find maximum space around current word
				int leftBound = ((w == 0) ? block.bounds.leftCol : (words[w-1].bounds.rightCol + wordAdjustmentSafetyMargin));
				int rightBound = (((w+1) == words.length) ? block.bounds.rightCol : (words[w+1].bounds.leftCol - wordAdjustmentSafetyMargin));
				
				int topBound = block.bounds.topRow;
				boolean noWordAbove = true;
				for (int wa = 0; (wordsAbove != null) && (wa < wordsAbove.length); wa++)
					if ((leftBound < wordsAbove[wa].bounds.rightCol) && (wordsAbove[wa].bounds.leftCol < rightBound)) {
						topBound = Math.max(topBound, (wordsAbove[wa].bounds.bottomRow + wordAdjustmentSafetyMargin));
						noWordAbove = false;
					}
				int la = l-2;
				while (noWordAbove && (la > -1)) {
					Word[] eWordsAbove = lines[la].getWords();
					for (int wa = 0; (wa < eWordsAbove.length); wa++)
						if ((leftBound < eWordsAbove[wa].bounds.rightCol) && (eWordsAbove[wa].bounds.leftCol < rightBound)) {
							topBound = Math.max(topBound, (eWordsAbove[wa].bounds.bottomRow + wordAdjustmentSafetyMargin));
							noWordAbove = false;
						}
					if (noWordAbove)
						la--;
				}
				
				int bottomBound = block.bounds.bottomRow;
				boolean noWordBelow = true;
				for (int wb = 0; (wordsBelow != null) && (wb < wordsBelow.length); wb++) {
					if ((leftBound < wordsBelow[wb].bounds.rightCol) && (wordsBelow[wb].bounds.leftCol < rightBound)) {
						bottomBound = Math.min(bottomBound, (wordsBelow[wb].bounds.topRow - wordAdjustmentSafetyMargin));
						noWordBelow = false;
					}
				}
				int lb = l+2;
				while (noWordBelow && (lb < lines.length)) {
					Word[] eWordsBelow = lines[lb].getWords();
					for (int wb = 0; (wb < eWordsBelow.length); wb++) {
						if ((leftBound < eWordsBelow[wb].bounds.rightCol) && (eWordsBelow[wb].bounds.leftCol < rightBound)) {
							bottomBound = Math.min(bottomBound, (eWordsBelow[wb].bounds.topRow - wordAdjustmentSafetyMargin));
							noWordBelow = false;
						}
					}
					if (noWordBelow)
						lb++;
				}
				
				//	extend word within bounds
				do {
					expanded = false;
					boolean expand = false;
					
					//	check upward expansion
					for (int c = words[w].bounds.leftCol; (words[w].bounds.topRow > topBound) && (c < words[w].bounds.rightCol); c++) {
						if (brightness[c][words[w].bounds.topRow] == 127)
							continue;
						
						//	collect non-white part
						int nws = c;
						while ((c < words[w].bounds.rightCol) && (brightness[c][words[w].bounds.topRow] < 127))
							c++;
						int nwe = c;
						
						//	allow diagonal connection
						if (words[w].bounds.leftCol < nws)
							nws--;
						if (nwe < words[w].bounds.rightCol)
							nwe++;
						
						//	check if non-white part extends beyond bounds
						boolean gotExtensionPixel = false;
						for (int s = nws; s < nwe; s++)
							if (brightness[s][words[w].bounds.topRow-1] < 127) {
								gotExtensionPixel = true;
								break;
							}
						
						//	got extension, expand
						if (gotExtensionPixel)
							expand = true;
					}
					if (expand) {
						words[w].bounds.topRow--;
						expanded = true;
						continue;
					}
					
					//	check downward expansion
					for (int c = words[w].bounds.leftCol; (words[w].bounds.bottomRow < bottomBound) && (c < words[w].bounds.rightCol); c++) {
						if (brightness[c][words[w].bounds.bottomRow-1] == 127)
							continue;
						
						//	collect non-white part
						int nws = c;
						while ((c < words[w].bounds.rightCol) && (brightness[c][words[w].bounds.bottomRow-1] < 127))
							c++;
						int nwe = c;
						
						//	allow diagonal connection
						if (words[w].bounds.leftCol < nws)
							nws--;
						if (nwe < words[w].bounds.rightCol)
							nwe++;
						
						//	check if non-white part extends beyond bounds
						boolean gotExtendsionPixel = false;
						for (int s = nws; s < nwe; s++)
							if (brightness[s][words[w].bounds.bottomRow] < 127) {
								gotExtendsionPixel = true;
								break;
							}
						
						//	got extension, expand
						if (gotExtendsionPixel)
							expand = true;
					}
					if (expand) {
						words[w].bounds.bottomRow++;
						expanded = true;
						continue;
					}
					
					//	check leftward expansion
					for (int r = words[w].bounds.topRow; (words[w].bounds.leftCol > leftBound) && (r < words[w].bounds.bottomRow); r++)
						if ((brightness[words[w].bounds.leftCol][r] < 127) && (brightness[words[w].bounds.leftCol-1][r] < 127)) {
							expand = true;
							break;
						}
					if (expand) {
						words[w].bounds.leftCol--;
						expanded = true;
						continue;
					}
					
					//	check rightward expansion
					for (int r = words[w].bounds.topRow; (words[w].bounds.rightCol < rightBound) && (r < words[w].bounds.bottomRow); r++)
						if ((brightness[words[w].bounds.rightCol-1][r] < 127) && (brightness[words[w].bounds.rightCol][r] < 127)) {
							expand = true;
							break;
						}
					if (expand) {
						words[w].bounds.rightCol++;
						expanded = true;
						continue;
					}
				} while (expanded);
			}
			
			//	compute upper and lower line bounds
			for (int w = 0; w < words.length; w++)
				wordTopRows[l] = Math.min(wordTopRows[l], words[w].bounds.topRow);
			for (int w = 0; w < words.length; w++)
				wordBottomRows[l] = Math.max(wordBottomRows[l], words[w].bounds.bottomRow);
			
			//	expand words topward up to line height to capture severed dots of i's, etc.
			for (int w = 0; (l != 0) && (w < words.length); w++) {
				int leftBound = ((w == 0) ? block.bounds.leftCol : (words[w-1].bounds.rightCol + wordAdjustmentSafetyMargin));
				int rightBound = (((w+1) == words.length) ? block.bounds.rightCol : (words[w+1].bounds.leftCol - wordAdjustmentSafetyMargin));
				int topBound = wordTopRows[l];
				for (int wa = 0; (wordsAbove != null) && (wa < wordsAbove.length); wa++) {
					if ((leftBound < wordsAbove[wa].bounds.rightCol) && (wordsAbove[wa].bounds.leftCol < rightBound))
						topBound = Math.max(topBound, (wordsAbove[wa].bounds.bottomRow + wordAdjustmentSafetyMargin));
				}
				if (words[w].bounds.topRow < topBound)
					continue;
				ImagePartRectangle wordBox = new ImagePartRectangle(words[w].bounds.ai);
				Imaging.copyBounds(words[w].bounds, wordBox);
				wordBox.topRow = topBound;
				wordBox = Imaging.narrowTopAndBottom(wordBox);
				Imaging.copyBounds(wordBox, words[w].bounds);
			}
		}
		
		//	expand lines upward and downward, but only so far as they do intersect with adjacent line words
		for (int l = 0; l < lines.length; l++) {
			int wordBottomAbove = -1;
			for (int la = (l-1); la > -1; la--)
				if (Imaging.isAboveOneAnother(lines[la].bounds, lines[l].bounds)) {
					wordBottomAbove = wordBottomRows[la];
					break;
				}
			int wordTopBelow = -1;
			for (int lb = (l+1); lb < lines.length; lb++)
				if (Imaging.isAboveOneAnother(lines[l].bounds, lines[lb].bounds)) {
					wordTopBelow = wordTopRows[lb];
					break;
				}
			if (wordBottomAbove == -1)
				lines[l].bounds.topRow = wordTopRows[l];
			else lines[l].bounds.topRow = Math.max(wordTopRows[l], wordBottomAbove);
			if (wordTopBelow == -1)
				lines[l].bounds.bottomRow = wordBottomRows[l];
			else lines[l].bounds.bottomRow = Math.min(wordBottomRows[l], wordTopBelow);
		}
	}
	
/* DERIVATION OF FONT SIZE FORMULAS:

Times New Roman at 96 DPI (100%):
24 pt: line height 37, cap height 21, non-cap height 14, descent 7
18 pt: line height 28, cap height 16, non-cap height 11, descent 5
12 pt: line height 19, cap height 11, non-cap height  7, descent 3
10 pt: line height 16, cap height  9, non-cap height  6, descent 3
 8 pt: line height 13, cap height  8, non-cap height  5, descent 2
 6 pt: line height 10, cap height  6, non-cap height  4, descent 2


Times New Roman at 400 DPI (417%):
24 pt: line height 153, cap height 90, non-cap height 62, descent 26
18 pt: line height 115, cap height 67, non-cap height 46, descent 20
12 pt: line height  77, cap height 45, non-cap height 32, descent 13
10 pt: line height  64, cap height 37, non-cap height 25, descent 11
 8 pt: line height  52, cap height 30, non-cap height 20, descent  8
 6 pt: line height  38, cap height 22, non-cap height 15, descent  7

==> line height: pt = line height / dpi * 63 (unsafe, as line spacing might differ)
==> cap height: pt = (cap height / dpi) * 107
==> non-cap height: pt = (non-cap height / dpi) * 155


DERIVATION OF FONT WEIGHT FORMULAS:

Times New Roman at 400 DPI (417%):
24 pt: bold width 18-21, plain width 11-12
18 pt: bold width 14-15, plain width 8
12 pt: bold width 10-11, plain width 6
10 pt: bold width 8-9, plain width 5
 8 pt: bold width 6-7, plain width 4

==> looks like (2 / 3) * (dpi/400) * font size might be a good threshold at 400 DPI, but then, this might depend on the font face
==> (2 * dpi * font size) / (3 * 400) --> seems a bit too small
==> (3 * dpi * font size) / (4 * 400) --> still a bit too small
==> (4 * dpi * font size) / (5 * 400) --> still too small
==> (5 * dpi * font size) / (6 * 400) --> still too unreliable
 	 */
//	
//	private static final double italicShearDeg = 15;
//	
//	private static final boolean DEBUG_COMPUTE_FONT_METRICS = true;
//	
//	/**
//	 * Compute font metrics in a text block.
//	 * @param block the block to analyze
//	 * @param dpi the resolution of the backing image
//	 */
//	public static void computeFontMetrics(Block block, int dpi) {
//		computeFontMetrics(block, dpi, true);
//	}
//	
//	private static class LineFontData {
//		Word[] words;
//		ImagePartRectangle[][] wordChars;
//		boolean[] wordIsBaselinePunctuation;
//		int capNonCapCut;
//		int nonCapPunctuationCut;
//		int avgCaHeight;
//		int avgNonCapHeight;
//		LineFontData(Word[] words) {
//			this.words = words;
//			this.wordChars = new ImagePartRectangle[this.words.length][];
//			this.wordIsBaselinePunctuation = new boolean[this.words.length];
//		}
//	}
//	
	/*
Read several research papers on the topic, and especially for bold, well, it works on lines as a whole ... not exactly what we need ...
Surprisingly (not !!!), accuracy is much higher the longer words get ... sure, but we need to recognize that single-letter abbreviated genus as being in italics as well.

Font size:
- create brightness histogram over pixel rows (pretty much what we already have for scan skew detection)
- try and use histogram peaks for font size computation (of sorts already in place, but use for whole line, now that lines are reliably horizontal) ...
- ... as well as baseline detection (kind of already in place with "where it suddenly gets dark", but might benefit from refinement, epecially now that even slight scan skews are also detected and corrected)
- average font size out across blocks, except for top and bottom lines (maybe only after paragraph splitting)

Literature idea for italics:
- create brightness histograms over word pixel columns ...
- for original word as well as for word sheared left by some 15°
--> histogram contrast sharper (higher) for original in plain text ...
--> ... and for sheared word in italics text
- pretty much what we already have with stroke counting in 0° and 15°, which works just as reliable ...
- ... but might be worth testing
- observe / assess dependency on font size

Literature ideas for bold:
- few, except for whole lines (nice for headings, but not in-text) ...
- try using OCR result for assistance:
  - render OCR result over words, both in bold and in plain (observe italics in the process, which identify a LOT more reliably based on page image alone)
  - compute matched, spurious, and missed pixels for all words (like already in use in glyph decoding ==> make that computation an independent method, maybe in Imaging, adding distance, etc., either by a flag or on demand) 
  - average gives general tendency for whole document (or better page, as scans might vary across pages) towards lighter or heavier fonts
  - considerable deviations from average should pretty reliably identify bold words
  --> compute distance from matching to bold as well as distance from matching to plain for each word ...
  --> ... and use that for bold-or-not decision
==> figures in stem width and black-desity differences between individual characters (even independent of OCR errors on similar looking characters like 'l', 'I', and '1', as they should match similarly)
==> a lot better than current approach (hopefully ...)
==> in document structure detector, do font style re-assessment first thing for scanned documents
	 */
	
	/**
	 * Compute the baselines of the lines in a text block.
	 * @param block the block to analyze
	 * @param dpi the resolution of the backing image
	 */
	public static void computeLineBaselines(Block block, int dpi) {
		int minFullWordHeight = (dpi / 25); // use only words higher than one twentyfifthth of an inch (one millimeter) for baseline computation, then fill in baseline of words lower than that (dashes, etc.)
		Line[] lines = block.getLines();
		if (lines.length == 0)
			return;
		for (int l = 0; l < lines.length; l++) {
			Word[] words = lines[l].getWords();
			if (words.length == 0)
				continue;
			
			//	use only words higher than threshold for baseline computation
			ArrayList baselineComputationWordList = new ArrayList();
			for (int w = 0; w < words.length; w++) {
				if (minFullWordHeight < (words[w].bounds.bottomRow - words[w].bounds.topRow))
					baselineComputationWordList.add(words[w]);
			}
			Word[] baselineComputationWords;
			if ((baselineComputationWordList.size() != 0) && (baselineComputationWordList.size() < words.length))
				baselineComputationWords = ((Word[]) baselineComputationWordList.toArray(new Word[baselineComputationWordList.size()]));
			else baselineComputationWords = words;
			
			//	compute baseline for each word
			ImagePartRectangle[] wordBounds = new ImagePartRectangle[baselineComputationWords.length];
			for (int w = 0; w < baselineComputationWords.length; w++) {
				wordBounds[w] = baselineComputationWords[w].bounds;
				ImagePartRectangle[] nwrs = {baselineComputationWords[w].bounds};
				baselineComputationWords[w].baseline = findBaseline(block.bounds.ai, nwrs);
			}
			
			//	detect line slope
			int descCount = 0;
			int evenCount = 0;
			int ascCount = 0;
			for (int w = 1; w < baselineComputationWords.length; w++) {
				if ((baselineComputationWords[w-1].baseline < 1) || (baselineComputationWords[w].baseline < 1))
					continue;
				if (baselineComputationWords[w-1].baseline < baselineComputationWords[w].baseline)
					descCount++;
				else if (baselineComputationWords[w-1].baseline == baselineComputationWords[w].baseline)
					evenCount++;
				else ascCount++;
			}
			boolean evenLine = true;
			if ((evenCount < ascCount) && ((baselineComputationWords.length * 8) <= ((ascCount + evenCount) * 10))) {
				System.out.println(" - got leftward slope");
				evenLine = (baselineComputationWords.length <= slopedBaselineSlidingWindowSize);
			}
			else if ((evenCount < descCount) && ((baselineComputationWords.length * 8) <= ((descCount + evenCount) * 10))) {
				System.out.println(" - got rightward slope");
				evenLine = (baselineComputationWords.length <= slopedBaselineSlidingWindowSize);
			}
			
			//	even line, smoothen at once
			if (evenLine) {
				
				//	compute average word baseline
				int lineBaselineSum = 0;
				int lineBaselineWordCount = 0;
				for (int w = 0; w < baselineComputationWords.length; w++) {
					if (baselineComputationWords[w].baseline < 1)
						continue;
					lineBaselineWordCount++;
					lineBaselineSum += baselineComputationWords[w].baseline;
				}
				int lineBaseline = ((lineBaselineWordCount == 0) ? findBaseline(block.bounds.ai, wordBounds) : ((lineBaselineSum + (lineBaselineWordCount / 2)) / lineBaselineWordCount));
				System.out.println(" - mean line baseline is " + lineBaseline + " based on " + lineBaselineWordCount + " words");
				
				//	smoothen out word baselines
				for (int w = 1; w < (baselineComputationWords.length - 1); w++) {
					int lDist = Math.abs(lineBaseline - baselineComputationWords[w-1].baseline);
					int oDist = Math.abs(lineBaseline - baselineComputationWords[w].baseline);
					int rDist = Math.abs(lineBaseline - baselineComputationWords[w+1].baseline);
					if ((oDist > lDist) && (oDist > rDist))
						baselineComputationWords[w].baseline = ((baselineComputationWords[w-1].baseline + baselineComputationWords[w+1].baseline) / 2);
				}
			}
			
			//	sloped line, use sliding window for smoothing
			else for (int ws = 0; ws <= (baselineComputationWords.length - slopedBaselineSlidingWindowSize); ws++) {
				
				//	compute average word baseline
				int windowBaselineSum = 0;
				int windowBaselineWordCount = 0;
				for (int w = ws; w < (ws + slopedBaselineSlidingWindowSize); w++) {
					if (baselineComputationWords[w].baseline < 1)
						continue;
					windowBaselineWordCount++;
					windowBaselineSum += baselineComputationWords[w].baseline;
				}
				int windowBaseline = ((windowBaselineWordCount == 0) ? findBaseline(block.bounds.ai, wordBounds) : ((windowBaselineSum + (windowBaselineWordCount / 2)) / windowBaselineWordCount));
				System.out.println(" - mean window baseline is " + windowBaseline + " based on " + windowBaselineWordCount + " words");
				
				//	smoothen out word baselines
				for (int w = (ws+1); w < (ws + slopedBaselineSlidingWindowSize - 1); w++) {
					int lDist = Math.abs(windowBaseline - baselineComputationWords[w-1].baseline);
					int oDist = Math.abs(windowBaseline - baselineComputationWords[w].baseline);
					int rDist = Math.abs(windowBaseline - baselineComputationWords[w+1].baseline);
					if ((oDist > lDist) && (oDist > rDist))
						baselineComputationWords[w].baseline = ((baselineComputationWords[w-1].baseline + baselineComputationWords[w+1].baseline) / 2);
				}
			}
			
			//	end word smoothing is too unreliable if sample is too small
			if (4 < baselineComputationWords.length) {
				
				//	compute average baseline distance
				int wordBaselineDistanceSum = 0;
				for (int w = 1; w < baselineComputationWords.length; w++)
					wordBaselineDistanceSum += (baselineComputationWords[w].baseline - baselineComputationWords[w-1].baseline);
				int avgWordBaselineDistance = ((wordBaselineDistanceSum + ((baselineComputationWords.length - 1) / 2)) / (baselineComputationWords.length - 1));
				
				//	smoothen leftmost and rightmost word based on that
				if (Math.abs(avgWordBaselineDistance) < Math.abs(baselineComputationWords[1].baseline - baselineComputationWords[0].baseline))
					baselineComputationWords[0].baseline = (baselineComputationWords[1].baseline - avgWordBaselineDistance);
				if (Math.abs(avgWordBaselineDistance) < Math.abs(baselineComputationWords[baselineComputationWords.length-1].baseline - baselineComputationWords[baselineComputationWords.length-2].baseline))
					baselineComputationWords[baselineComputationWords.length-1].baseline = (baselineComputationWords[baselineComputationWords.length-2].baseline + avgWordBaselineDistance);
			}
			
			//	fill in baseline of words lower than one twentyfifthth of an inch (dashes, etc.)
			for (int w = 0; w < words.length; w++) {
				if (0 < words[w].baseline)
					continue;
				int blLeft = -1;
				for (int lw = (w-1); lw > -1; lw--)
					if (0 < words[lw].baseline) {
						blLeft = words[lw].baseline;
						break;
					}
				int blRight = -1;
				for (int rw = (w+1); rw < words.length; rw++)
					if (0 < words[rw].baseline) {
						blRight = words[rw].baseline;
						break;
					}
				if ((blLeft != -1) && (blRight != -1))
					words[w].baseline = ((blLeft + blRight + 1) / 2);
				else if (blLeft != -1)
					words[w].baseline = blLeft;
				else if (blRight != -1)
					words[w].baseline = blRight;
			}
		}
	}
	private static final int slopedBaselineSlidingWindowSize = 5;
	
	static void checkWordDescents(Block block, int dpi) {
		int minSignificantDifference = (dpi / 60); // somewhat less than half a millimeter
		Line[] lines = block.getLines();
		if (lines.length == 0)
			return;
		
		int[] wordTopRows = new int[lines.length];
		int[] wordBottomRows = new int[lines.length];
		boolean wordChanged = false;
		for (int l = 0; l < lines.length; l++) {
			wordTopRows[l] = lines[l].bounds.bottomRow;
			wordBottomRows[l] = lines[l].bounds.topRow;
			Word[] words = lines[l].getWords();
			if (words.length == 0)
				continue;
			int lineBaseline = lines[l].getBaseline();
			
			//	expand words in all directions to cover letters fully, as sloped line cuts might have severed letters
			for (int w = 0; w < words.length; w++) {
				if ((words[w].bounds.bottomRow - lineBaseline) < minSignificantDifference)
					continue;
				ImagePartRectangle[] wbps = Imaging.splitIntoRows(words[w].bounds, 1);
				if (wbps.length < 2)
					continue;
				for (int p = 0; p < wbps.length; p++) {
					if (lineBaseline < wbps[p].topRow)
						break;
					words[w].bounds.bottomRow = wbps[p].bottomRow;
					wordChanged = true;
				}
			}
			
			//	compute upper and lower line bounds
			for (int w = 0; w < words.length; w++)
				wordTopRows[l] = Math.min(wordTopRows[l], words[w].bounds.topRow);
			for (int w = 0; w < words.length; w++)
				wordBottomRows[l] = Math.max(wordBottomRows[l], words[w].bounds.bottomRow);
		}
		
		//	nothing changed, we're all set
		if (!wordChanged)
			return;
		
		//	expand lines upward and downward, but only so far as they do intersect with adjacent line words
		for (int l = 0; l < lines.length; l++) {
			int wordBottomAbove = -1;
			for (int la = (l-1); la > -1; la--)
				if (Imaging.isAboveOneAnother(lines[la].bounds, lines[l].bounds)) {
					wordBottomAbove = wordBottomRows[la];
					break;
				}
			int wordTopBelow = -1;
			for (int lb = (l+1); lb < lines.length; lb++)
				if (Imaging.isAboveOneAnother(lines[l].bounds, lines[lb].bounds)) {
					wordTopBelow = wordTopRows[lb];
					break;
				}
			if (wordBottomAbove == -1)
				lines[l].bounds.topRow = wordTopRows[l];
			else lines[l].bounds.topRow = Math.max(wordTopRows[l], wordBottomAbove);
			if (wordTopBelow == -1)
				lines[l].bounds.bottomRow = wordBottomRows[l];
			else lines[l].bounds.bottomRow = Math.min(wordBottomRows[l], wordTopBelow);
		}
	}
	
	private static int findBaseline(AnalysisImage ai, ImagePartRectangle[] words) {
		if (words.length == 0)
			return -1;
		
		int left = words[0].leftCol;
		int right = words[words.length-1].rightCol;
		if (right <= left)
			return -1;
		
		int top = Integer.MAX_VALUE;
		int bottom = 0;
		for (int w = 0; w < words.length; w++) {
			top = Math.min(top, words[w].topRow);
			bottom = Math.max(bottom, words[w].bottomRow);
		}
		if (bottom <= top)
			return -1;
		
		int height = (bottom - top);
		byte[][] brightness = ai.getBrightness();
		byte[] rowBrightnesses = new byte[height];
		for (int r = top; (r < bottom) && (r < brightness[0].length); r++) {
			int brightnessSum = 0;
			for (int c = left; (c < right) && (c < brightness.length); c++)
				brightnessSum += brightness[c][r];
			rowBrightnesses[r - top] = ((byte) (brightnessSum / (right - left)));
		}
		
		byte[] rowBrightnessDrops = new byte[height];
		for (int r = 0; r < height; r++)
			rowBrightnessDrops[r] = ((byte) (rowBrightnesses[r] - (((r+1) == height) ? 127 : rowBrightnesses[r+1])));
		byte maxRowBrightnessDrop = 0;
		int maxDropRow = -1;
		for (int r = (height-1); r > (height / 2); r--)
			if (rowBrightnessDrops[r] < maxRowBrightnessDrop) {
				maxRowBrightnessDrop = rowBrightnessDrops[r];
				maxDropRow = r;
			}
		
		return (top + maxDropRow);
	}
	
	/**
	 * Container holding a region coloring for a document page, combined with
	 * several analyses like the extent of individual regions.
	 * 
	 * @author sautter
	 */
	public static class PageRegionColoring {
		
		/** the page whose image the region coloring refers to */
		public final ImPage page;
		/** the page image the region coloring refers to */
		public final PageImage pageImage;
		/** the threshold used to create the region coloring */
		public final byte threshold;
		/** the actual region coloring */
		public final int[][] pageRegionColors;
		/** the number of pixels in every region of the page, indexed by region color */
		public final int[] pageRegionSizes;
		/** the minimum (left-most) X coordinate of every region in the page, indexed by region color */
		public final int[] pageRegionMinX;
		/** the maximum (right-most) X coordinate of every region in the page, indexed by region color */
		public final int[] pageRegionMaxX;
		/** the minimum (top-most) Y coordinate of every region in the page, indexed by region color */
		public final int[] pageRegionMinY;
		/** the maximum (bottom-most) Y coordinate of every region in the page, indexed by region color */
		public final int[] pageRegionMaxY;
		
		/**
		 * @param page the page whose image the region coloring refers to
		 * @param pageImage the page image the region coloring refers to
		 * @param threshold the threshold used to create the region coloring
		 * @param pageRegionColors the actual region coloring
		 */
		public PageRegionColoring(ImPage page, PageImage pageImage, byte threshold, int[][] pageRegionColors) {
			this.page = page;
			this.pageImage = pageImage;
			this.threshold = threshold;
			this.pageRegionColors = pageRegionColors;
			
			//	analyse region coloring
			int regionColorCount = 0;
			for (int c = 0; c < pageRegionColors.length; c++) {
				for (int r = 0; r < pageRegionColors[c].length; r++)
					regionColorCount = Math.max(regionColorCount, pageRegionColors[c][r]);
			}
			regionColorCount++; // account for 0
			this.pageRegionSizes = new int[regionColorCount];
			Arrays.fill(this.pageRegionSizes, 0);
			this.pageRegionMinX = new int[regionColorCount];
			Arrays.fill(this.pageRegionMinX, this.pageRegionColors.length);
			this.pageRegionMaxX = new int[regionColorCount];
			Arrays.fill(this.pageRegionMaxX, 0);
			this.pageRegionMinY = new int[regionColorCount];
			Arrays.fill(this.pageRegionMinY, this.pageRegionColors[0].length);
			this.pageRegionMaxY = new int[regionColorCount];
			Arrays.fill(this.pageRegionMaxY, 0);
			for (int c = 0; c < pageRegionColors.length; c++)
				for (int r = 0; r < pageRegionColors[c].length; r++) {
					if (pageRegionColors[c][r] == 0)
						continue;
					this.pageRegionSizes[pageRegionColors[c][r]]++;
					this.pageRegionMinX[pageRegionColors[c][r]] = Math.min(c, this.pageRegionMinX[pageRegionColors[c][r]]);
					this.pageRegionMaxX[pageRegionColors[c][r]] = Math.max(c, this.pageRegionMaxX[pageRegionColors[c][r]]);
					this.pageRegionMinY[pageRegionColors[c][r]] = Math.min(r, this.pageRegionMinY[pageRegionColors[c][r]]);
					this.pageRegionMaxY[pageRegionColors[c][r]] = Math.max(r, this.pageRegionMaxY[pageRegionColors[c][r]]);
				}
		}
	}
	
	/**
	 * Create a region coloring for the image of a document page, using a
	 * specific brightness threshold.
	 * @param page the page whose image to analyse
	 * @param threshold the broghtness threshold to use
	 * @return the region coloring
	 */
	public static PageRegionColoring getRegionColoring(ImPage page, byte threshold) {
		
		//	get and wrap page image only once
		PageImage pageImage = page.getImage();
		AnalysisImage pageAi = Imaging.wrapImage(pageImage.image, null);
		
		//	create region coloring of whole page
		int[][] pageRegionColors = Imaging.getRegionColoring(pageAi, threshold, false);
		return new PageRegionColoring(page, pageImage, threshold, pageRegionColors);
	}
	
	/**
	 * Adjust the baselines of OCR derived words to the lines in the underlying
	 * page image. This method matches up lines of OCR derived words to page
	 * image lines on a block-by-block basis, also making sure to cover the
	 * entire block with OCR words. Further, words are also adjusted to the
	 * height of the lines.
	 * @param words an array holding the words to adjust
	 * @param adjustToWords adjust words horizontally inside lines?
	 * @param pm a progress monitor to relay status information
	 * @return an array holding the adjusted words
	 */
	public static ImWord[] adjustOcrWordsToBlocks(ImWord[] words, boolean adjustToWords, ProgressMonitor pm) {
		return adjustOcrWordsToBlocks(words, null, null, adjustToWords, pm);
	}
	
	/**
	 * Adjust the baselines of OCR derived words to the lines in the underlying
	 * page image. This method matches up lines of OCR derived words to page
	 * image lines on a block-by-block basis, also making sure to cover the
	 * entire block with OCR words. Further, words are also adjusted to the
	 * height of the lines.
	 * @param words an array holding the words to adjust
	 * @param page the page the argument words lie in
	 * @param adjustToWords adjust words horizontally inside lines?
	 * @param pm a progress monitor to relay status information
	 * @return an array holding the adjusted words
	 */
	public static ImWord[] adjustOcrWordsToBlocks(ImWord[] words, ImPage page, boolean adjustToWords, ProgressMonitor pm) {
		return adjustOcrWordsToBlocks(words, page, null, adjustToWords, pm);
	}
	
	/**
	 * Adjust the baselines of OCR derived words to the lines in the underlying
	 * page image. This method matches up lines of OCR derived words to page
	 * image lines on a block-by-block basis, also making sure to cover the
	 * entire block with OCR words. Further, words are also adjusted to the
	 * height of the lines.
	 * @param words an array holding the words to adjust
	 * @param page the page the argument words lie in
	 * @param pageImage the image of the page the words lie in
	 * @param adjustToWords adjust words horizontally inside lines?
	 * @param pm a progress monitor to relay status information
	 * @return an array holding the adjusted words
	 */
	public static ImWord[] adjustOcrWordsToBlocks(ImWord[] words, ImPage page, PageImage pageImage, boolean adjustToWords, ProgressMonitor pm) {
		return adjustOcrWordsToBlocks(words, page, pageImage, null, adjustToWords, pm);
	}
	
	/**
	 * Adjust the baselines of OCR derived words to the lines in the underlying
	 * page image. This method matches up lines of OCR derived words to page
	 * image lines on a block-by-block basis, also making sure to cover the
	 * entire block with OCR words. Further, words are also adjusted to the
	 * height of the lines.
	 * @param words an array holding the words to adjust
	 * @param page the page the argument words lie in
	 * @param pageImage the image of the page the words lie in
	 * @param docLayout the document style describing page layout
	 * @param adjustToWords adjust words horizontally inside lines?
	 * @param pm a progress monitor to relay status information
	 * @return an array holding the adjusted words
	 */
	public static ImWord[] adjustOcrWordsToBlocks(ImWord[] words, ImPage page, PageImage pageImage, ImDocumentStyle docLayout, boolean adjustToWords, ProgressMonitor pm) {
		if (words.length == 0)
			return words;
		
		//	check parameters
		ImDocument doc = null;
		int pageId = -1;
		for (int w = 0; w < words.length; w++) {
			if (doc == null)
				doc = words[w].getDocument();
			else if (words[w].getDocument() != doc)
				throw new IllegalArgumentException("Cannot adjust words belonging to different documents");
			if (pageId == -1)
				pageId = words[w].pageId;
			else if (words[w].pageId != pageId)
				throw new IllegalArgumentException("Cannot adjust words belonging to different pages");
		}
		if (page == null)
			page = doc.getPage(pageId);
		if (pageImage == null)
			pageImage = page.getImage();
		if (pm == null)
			pm = ProgressMonitor.dummy;
		
		//	get document style and use it for page structure analysis
		if (docLayout == null) {
			ImDocumentStyle docStyle = ImDocumentStyle.getStyleFor(doc);
			if (docStyle == null)
				docStyle = new ImDocumentStyle(new PropertiesData(new Properties()));
			docLayout = docStyle.getImSubset("layout");
		}
		
		//	get page content area layout hint (defaulting to whole page bounds), as well as number of columns
		BoundingBox contentArea = docLayout.getBoxProperty("contentArea", page.bounds, pageImage.currentDpi);
		int columnCount = docLayout.getIntProperty("columnCount", -1);
		
		//	get column and block margin layout hints (defaulting to kind of universal ball park figures)
		int minBlockMargin = docLayout.getIntProperty("minBlockMargin", (pageImage.currentDpi / 10), pageImage.currentDpi);
		int minColumnMargin = ((columnCount == 1) ? (page.bounds.right - page.bounds.left) : docLayout.getIntProperty("minColumnMargin", (pageImage.currentDpi / 10), pageImage.currentDpi));
		
		//	get (or compute) column areas to correct erroneous column splits
		BoundingBox[] columnAreas = docLayout.getBoxListProperty("columnAreas", null, pageImage.currentDpi);
		if (columnAreas == null) {
			if (columnCount == 1) {
				columnAreas = new BoundingBox[1];
				columnAreas[0] = contentArea;
			}
			else if (columnCount == 2) {
				columnAreas = new BoundingBox[2];
				columnAreas[0] = new BoundingBox(contentArea.left, ((contentArea.left + contentArea.right) / 2), contentArea.top, contentArea.bottom);
				columnAreas[1] = new BoundingBox(((contentArea.left + contentArea.right) / 2), contentArea.right, contentArea.top, contentArea.bottom);
			}
			else if ((columnCount != -1) && (contentArea != page.bounds)) {
				columnAreas = new BoundingBox[columnCount];
				for (int c = 0; c < columnCount; c++)
					columnAreas[c] = new BoundingBox((contentArea.left + (((contentArea.right - contentArea.left) * c) / columnCount)), (contentArea.left + (((contentArea.right - contentArea.left) * (c + 1)) / columnCount)), contentArea.top, contentArea.bottom);
			}
		}
		
		//	get minimum column width to prevent column splits resulting in too narrow columns
		int minColumnWidth = docLayout.getIntProperty("minColumnWidth", -1, pageImage.currentDpi);
		
		//	loop through to analysis method
		return adjustOcrWordsToBlocks(doc, words, page, pageImage, minColumnMargin, minBlockMargin, columnAreas, minColumnWidth, adjustToWords, pm);
	}
	
	private static final boolean DEBUG_ADJUST_OCR = false;
	
	/**
	 * Adjust the baselines of OCR derived words to the lines in the underlying
	 * page image. This method matches up lines of OCR derived words to page
	 * image lines on a block-by-block basis, also making sure to cover the
	 * entire block with OCR words. Further, words are also adjusted to the
	 * height of the lines.
	 * @param doc the document the words belong to
	 * @param words an array holding the words to adjust
	 * @param page the page the argument words lie in
	 * @param pageImage the image of the page the words lie in
	 * @param minColumnMargin the minimum horizontal distance between two columns (in pixels)
	 * @param minBlockMargin the minimum vertical distance between two blocks (in pixels)
	 * @param columnAreas an array of bounding boxes specifying where text columns can be
	 * @param minColumnWidth the minimum width of a column (in pixels)
	 * @param adjustToWords adjust words horizontally inside lines?
	 * @param pm a progress monitor to relay status information
	 * @return an array holding the adjusted words
	 */
	public static ImWord[] adjustOcrWordsToBlocks(ImDocument doc, ImWord[] words, ImPage page, PageImage pageImage, int minColumnMargin, int minBlockMargin, BoundingBox[] columnAreas, int minColumnWidth, boolean adjustToWords, ProgressMonitor spm) {
		BufferedImage vbi = null;
		Graphics2D vbiGr = null;
		if (DEBUG_ADJUST_OCR) {
			vbi = new BufferedImage(pageImage.image.getWidth(), pageImage.image.getHeight(), BufferedImage.TYPE_INT_ARGB);
			vbiGr = vbi.createGraphics();
			vbiGr.setColor(Color.WHITE);
			vbiGr.fillRect(0, 0, vbi.getWidth(), vbi.getHeight());
			vbiGr.drawImage(pageImage.image, 0, 0, pageImage.image.getWidth(), pageImage.image.getHeight(), null);
		}
		
		//	get structure of scanned page image
		AnalysisImage api = Imaging.wrapImage(pageImage.image, null);
		Region apiRootRegion = PageImageAnalysis.getPageRegion(api, pageImage.currentDpi, minColumnMargin, minBlockMargin, columnAreas, minColumnWidth, false, spm);
		Region[] apiBlocks = getAtomicRegions(apiRootRegion);
		
		//	get lines and instantiate non-empty blocks
		ArrayList apiPageBlockList = new ArrayList();
		for (int b = 0; b < apiBlocks.length; b++) {
			BlockLine[] apiBlockLines = getBlockLines(api, pageImage.currentDpi, apiBlocks[b].getBoundingBox(), true, spm, vbi);
			if (apiBlockLines.length != 0)
				apiPageBlockList.add(new PageBlock(apiBlockLines));
		}
		PageBlock[] apiPageBlocks = ((PageBlock[]) apiPageBlockList.toArray(new PageBlock[apiPageBlockList.size()]));
		
		//	get structure of OCR word distribution
		BufferedImage obi = new BufferedImage(pageImage.image.getWidth(), pageImage.image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
		Graphics2D obiGr = obi.createGraphics();
		obiGr.setColor(Color.WHITE);
		obiGr.fillRect(0, 0, obi.getWidth(), obi.getHeight());
		obiGr.setColor(Color.BLACK);
		for (int w = 0; w < words.length; w++)
			obiGr.fillRect(words[w].bounds.left, words[w].bounds.top, words[w].bounds.getWidth(), words[w].bounds.getHeight());
		AnalysisImage aoi = Imaging.wrapImage(obi, null);
		Region aoiRootRegion = PageImageAnalysis.getPageRegion(aoi, pageImage.currentDpi, minColumnMargin, minBlockMargin, columnAreas, minColumnWidth, false, spm);
		Region[] aoiBlocks = getAtomicRegions(aoiRootRegion);
		
		//	assign OCR words to blocks
		ArrayList ocrBlocks = new ArrayList(aoiBlocks.length);
		ArrayList ocrWords = new ArrayList(Arrays.asList(words));
		for (int b = 0; b < aoiBlocks.length; b++) {
			OcrBlock ob = new OcrBlock(aoiBlocks[b].bounds);
			BoundingBox obb = aoiBlocks[b].getBoundingBox();
			for (int w = 0; w < ocrWords.size(); w++) {
				if (obb.includes(((ImWord) ocrWords.get(w)).bounds, true))
					ob.words.add(ocrWords.remove(w--));
			}
			if (ob.words.isEmpty())
				continue;
			ocrBlocks.add(ob);
			if (DEBUG_ADJUST_OCR)
				System.out.println("Got OCR block at " + ob.getBounds() + " with " + ob.words.size() + " words");
		}
		System.out.println(ocrWords.size() + " of " + words.length + " OCR words remaining");
		obiGr.setColor(Color.GRAY);
		for (int w = 0; w < ocrWords.size(); w++) {
			ImWord word = ((ImWord) ocrWords.get(w));
			obiGr.fillRect(word.bounds.left, word.bounds.top, word.bounds.getWidth(), word.bounds.getHeight());
		}
		obiGr.dispose();
		
		//	pair up blocks (merge multiple OCR blocks if in same page block)
		int emptyApiBlockCount = 0;
		for (int pb = 0; pb < apiPageBlocks.length; pb++) {
			if (DEBUG_ADJUST_OCR)
				System.out.println("Populating page block " + apiPageBlocks[pb].getBounds());
			int obCount = 0; 
			for (int ob = 0; ob < ocrBlocks.size(); ob++) {
				Point obc = ((OcrBlock) ocrBlocks.get(ob)).getCenter();
				if (apiPageBlocks[pb].contains(obc)) {
					apiPageBlocks[pb].addOcrBlock((OcrBlock) ocrBlocks.remove(ob--));
					obCount++;
				}
			}
			if (DEBUG_ADJUST_OCR)
				System.out.println(" ==> added " + obCount + " OCR blocks");
			if (apiPageBlocks[pb].ocrBlock == null)
				emptyApiBlockCount++;
			else if (DEBUG_ADJUST_OCR) {
				System.out.println(" ==> OCR block bounds are " + apiPageBlocks[pb].ocrBlock.getBounds());
				System.out.println(" ==> added " + apiPageBlocks[pb].ocrBlock.words.size() + " OCR words");
			}
		}
		System.out.println(ocrBlocks.size() + " OCR blocks and " + emptyApiBlockCount + " page blocks remaining (1)");
		if (DEBUG_ADJUST_OCR) {
			for (int b = 0; b < ocrBlocks.size(); b++)
				System.out.println(" - OCR block " + ((OcrBlock) ocrBlocks.get(b)).getBounds());
			for (int b = 0; b < apiPageBlocks.length; b++) {
				if (apiPageBlocks[b].ocrBlock == null)
					System.out.println(" - page block " + apiPageBlocks[b].getBounds());
			}
		}
		
		//	merge page blocks that are in same OCR block, and re-get lines
		if (emptyApiBlockCount != 0) {
			apiPageBlockList.clear();
			apiPageBlockList.addAll(Arrays.asList(apiPageBlocks));
			for (int b = 0; b < apiPageBlockList.size(); b++) {
				PageBlock apiPageBlock = ((PageBlock) apiPageBlockList.get(b));
				if (apiPageBlock.ocrBlock != null)
					continue; // taken care of above ...
				if (DEBUG_ADJUST_OCR)
					System.out.println("Attempting to attach empty page block " + apiPageBlock.getBounds());
				Point pbc = apiPageBlock.getCenter();
				System.out.println(" - center is " + pbc);
				for (int cb = 0; cb < apiPageBlockList.size(); cb++) {
					if (cb == b)
						continue; // little we cound do here
					PageBlock cApiPageBlock = ((PageBlock) apiPageBlockList.get(cb));
					if (DEBUG_ADJUST_OCR)
						System.out.println(" - checking " + cApiPageBlock.getBounds());
					if (cApiPageBlock.ocrBlock == null)
						continue; // another empty block won't help ...
					if (DEBUG_ADJUST_OCR)
						System.out.println(" - OCR bounds are " + cApiPageBlock.ocrBlock.getBounds());
					if (!cApiPageBlock.ocrBlock.contains(pbc))
						continue;
					BoundingBox mApiBlockBounds = cApiPageBlock.getBounds().union(apiPageBlock.getBounds());
					BlockLine[] mApiBlockLines = getBlockLines(api, pageImage.currentDpi, mApiBlockBounds, true, spm, vbi);
					if (DEBUG_ADJUST_OCR)
						System.out.println(" ==> attached to " + cApiPageBlock.getBounds());
					PageBlock mApiPageBlock = new PageBlock(mApiBlockLines);
					if (DEBUG_ADJUST_OCR)
						System.out.println(" ==> bounds are " + mApiPageBlock.getBounds());
					mApiPageBlock.addOcrBlock(cApiPageBlock.ocrBlock);
					apiPageBlockList.set(cb, mApiPageBlock);
					apiPageBlockList.remove(b--);
					emptyApiBlockCount--;
					break;
				}
			}
			if (apiPageBlockList.size() < apiPageBlocks.length)
				apiPageBlocks = ((PageBlock[]) apiPageBlockList.toArray(new PageBlock[apiPageBlockList.size()]));
		}
		System.out.println(ocrBlocks.size() + " OCR blocks and " + emptyApiBlockCount + " page blocks remaining (2)");
		if (DEBUG_ADJUST_OCR) {
			for (int b = 0; b < ocrBlocks.size(); b++)
				System.out.println(" - OCR block " + ((OcrBlock) ocrBlocks.get(b)).getBounds());
			for (int b = 0; b < apiPageBlocks.length; b++) {
				if (apiPageBlocks[b].ocrBlock == null)
					System.out.println(" - page block " + apiPageBlocks[b].getBounds());
			}
		}
		
		//	if both empty OCR blocks and empty page blocks remain, merge them by overlap
		if (emptyApiBlockCount != 0) {
			apiPageBlockList.clear();
			apiPageBlockList.addAll(Arrays.asList(apiPageBlocks));
			for (int b = 0; b < apiPageBlockList.size(); b++) {
				PageBlock apiPageBlock = ((PageBlock) apiPageBlockList.get(b));
				if (apiPageBlock.ocrBlock != null)
					continue; // taken care of above ...
				BoundingBox apiPageBlockBounds = apiPageBlock.getBounds();
				if (DEBUG_ADJUST_OCR)
					System.out.println("Attempting to attach empty page block " + apiPageBlockBounds);
				Point pbc = apiPageBlock.getCenter();
				if (DEBUG_ADJUST_OCR)
					System.out.println(" - center is " + pbc);
				
				//	merge page bloack that lie in same unassigned OCR block
				PageBlock mApiPageBlock = null;
				for (int cb = (b+1); cb < apiPageBlockList.size(); cb++) {
					PageBlock cApiPageBlock = ((PageBlock) apiPageBlockList.get(cb));
					BoundingBox cApiPageBlockBounds = cApiPageBlock.getBounds();
					if (DEBUG_ADJUST_OCR)
						System.out.println(" - checking " + cApiPageBlockBounds);
					if (cApiPageBlock.ocrBlock != null)
						continue; // we would have merged with this one above ...
					Point cPbc = cApiPageBlock.getCenter();
					for (int ob = 0; ob < ocrBlocks.size(); ob++) {
						OcrBlock ocrBlock = ((OcrBlock) ocrBlocks.get(ob));
						if (DEBUG_ADJUST_OCR)
							System.out.println("   - checking against OCR block " + ocrBlock.getBounds());
						if (!ocrBlock.contains(pbc))
							continue;
						if (!ocrBlock.contains(cPbc))
							continue;
						BoundingBox mApiBlockBounds = cApiPageBlockBounds.union(apiPageBlockBounds);
						if (DEBUG_ADJUST_OCR)
							System.out.println("   ==> combined bounds are " + mApiBlockBounds);
						BlockLine[] mApiBlockLines = getBlockLines(api, pageImage.currentDpi, mApiBlockBounds, true, spm, vbi);
						if (DEBUG_ADJUST_OCR)
							System.out.println("   ==> combined lines are:");
						BoundingBox[] mApiBlockLineBounds = new BoundingBox[mApiBlockLines.length];
						for (int l = 0; l < mApiBlockLines.length; l++) {
							mApiBlockLineBounds[l] = mApiBlockLines[l].getPageBounds();
							if (DEBUG_ADJUST_OCR)
								System.out.println("     - " + mApiBlockLines[l].getPageBounds());
						}
						BoundingBox mApiBlockLineHull = BoundingBox.aggregate(mApiBlockLineBounds);
						if (DEBUG_ADJUST_OCR)
							System.out.println("     ==> " + mApiBlockLineHull);
						if (apiPageBlockBounds.includes(mApiBlockLineHull, false)) {
							if (DEBUG_ADJUST_OCR)
								System.out.println("   ==> fully contained in original " + apiPageBlockBounds);
							continue;
						}
						if (cApiPageBlockBounds.includes(mApiBlockLineHull, false)) {
							if (DEBUG_ADJUST_OCR)
								System.out.println("   ==> fully contained in original " + cApiPageBlockBounds);
							continue;
						}
						if (DEBUG_ADJUST_OCR)
							System.out.println(" ==> attached to " + cApiPageBlock.getBounds());
						mApiPageBlock = new PageBlock(mApiBlockLines);
						if (DEBUG_ADJUST_OCR)
							System.out.println(" ==> bounds are " + mApiPageBlock.getBounds());
						break;
					}
					if (mApiPageBlock != null)
						break;
				}
				
				//	clean up all page blcos the lie in merge result (we might have merged across one)
				if (mApiPageBlock == null)
					continue;
				for (int cb = 0; cb < apiPageBlockList.size(); cb++) {
					PageBlock cApiPageBlock = ((PageBlock) apiPageBlockList.get(cb));
					if (DEBUG_ADJUST_OCR)
						System.out.println(" - checking " + cApiPageBlock.getBounds());
					if (cApiPageBlock.ocrBlock != null)
						continue; // we would have handled this one above ...
					Point cPbc = cApiPageBlock.getCenter();
					if (mApiPageBlock.contains(cPbc)) {
						apiPageBlockList.remove(cb--);
						emptyApiBlockCount--;
					}
				}
				
				//	store merged block, and account for it still being empty
				apiPageBlockList.add(mApiPageBlock);
				emptyApiBlockCount++;
				
				//	start over with main loop (our indexes might be all over the place now)
				b = -1;
			}
			
			//	try and attach remaining OCR blocks if we performed any mergers
			if (apiPageBlockList.size() < apiPageBlocks.length) {
				apiPageBlocks = ((PageBlock[]) apiPageBlockList.toArray(new PageBlock[apiPageBlockList.size()]));
				for (int pb = 0; pb < apiPageBlocks.length; pb++) {
					if (apiPageBlocks[pb].ocrBlock != null)
						continue; // handled above
					if (DEBUG_ADJUST_OCR)
						System.out.println("Populating page block " + apiPageBlocks[pb].getBounds());
					int obCount = 0; 
					for (int ob = 0; ob < ocrBlocks.size(); ob++) {
						Point obc = ((OcrBlock) ocrBlocks.get(ob)).getCenter();
						if (apiPageBlocks[pb].contains(obc)) {
							apiPageBlocks[pb].addOcrBlock((OcrBlock) ocrBlocks.remove(ob--));
							obCount++;
						}
					}
					if (DEBUG_ADJUST_OCR)
						System.out.println(" ==> added " + obCount + " OCR blocks");
					if (apiPageBlocks[pb].ocrBlock != null) {
						emptyApiBlockCount--;
						if (DEBUG_ADJUST_OCR) {
							System.out.println(" ==> OCR block bounds are " + apiPageBlocks[pb].ocrBlock.getBounds());
							System.out.println(" ==> added " + apiPageBlocks[pb].ocrBlock.words.size() + " OCR words");
						}
					}
				}
			}
		}
		System.out.println(ocrBlocks.size() + " OCR blocks and " + emptyApiBlockCount + " page blocks remaining (3)");
		if (DEBUG_ADJUST_OCR) {
			for (int b = 0; b < ocrBlocks.size(); b++)
				System.out.println(" - OCR block " + ((OcrBlock) ocrBlocks.get(b)).getBounds());
			for (int b = 0; b < apiPageBlocks.length; b++) {
				if (apiPageBlocks[b].ocrBlock == null)
					System.out.println(" - page block " + apiPageBlocks[b].getBounds());
			}
		}
		
		//	try and assign any remaining OCR blocks (we must be out of vacant page blocks that might match)
		for (int ob = 0; ob < ocrBlocks.size(); ob++) {
			OcrBlock ocrBlock = ((OcrBlock) ocrBlocks.get(ob));
			if (DEBUG_ADJUST_OCR)
				System.out.println("Assigning vacant OCR block " + ocrBlock.getBounds());
			Point obc = ocrBlock.getCenter();
			if (DEBUG_ADJUST_OCR)
				System.out.println(" - center point is " + obc);
			for (int pb = 0; pb < apiPageBlocks.length; pb++) {
				if (DEBUG_ADJUST_OCR)
					System.out.println(" - checking page block " + apiPageBlocks[pb].getBounds());
				if (apiPageBlocks[pb].contains(obc)) {
					apiPageBlocks[pb].addOcrBlock(ocrBlock);
					ocrBlocks.remove(ob--);
					if (DEBUG_ADJUST_OCR)
						System.out.println(" ==> added");
					break;
				}
			}
		}
		System.out.println(ocrBlocks.size() + " OCR blocks and " + emptyApiBlockCount + " page blocks remaining (4)");
		if (DEBUG_ADJUST_OCR) {
			for (int b = 0; b < ocrBlocks.size(); b++)
				System.out.println(" - OCR block " + ((OcrBlock) ocrBlocks.get(b)).getBounds());
			for (int b = 0; b < apiPageBlocks.length; b++) {
				if (apiPageBlocks[b].ocrBlock == null)
					System.out.println(" - page block " + apiPageBlocks[b].getBounds());
			}
		}
		
		//	adjust word boundaries, transfer baselines, and collect corrected words
		ArrayList pageWords = new ArrayList();
		HashSet doneWords = new HashSet();
		for (int b = 0; b < apiPageBlocks.length; b++)
			addAdjustedOcrWordsToBlock(doc, page, apiPageBlocks[b], pageImage, adjustToWords, pageWords, doneWords, spm);
		
		//	salvage unhandled words
		for (int w = 0; w < words.length; w++) {
			if (!doneWords.contains(words[w]))
				pageWords.add(words[w]);
		}
		
		if (vbi != null) {
			BufferedImage cbi = new BufferedImage(pageImage.image.getWidth(), pageImage.image.getHeight(), BufferedImage.TYPE_INT_ARGB);
			Graphics2D cbiGr = cbi.createGraphics();
			cbiGr.setColor(Color.WHITE);
			cbiGr.fillRect(0, 0, cbi.getWidth(), cbi.getHeight());
			cbiGr.drawImage(pageImage.image, 0, 0, pageImage.image.getWidth(), pageImage.image.getHeight(), null);
			cbiGr.setColor(Color.BLACK);
			for (int w = 0; w < words.length; w++)
				cbiGr.fillRect(words[w].bounds.left, words[w].bounds.top, words[w].bounds.getWidth(), words[w].bounds.getHeight());
			AnalysisImage aci = Imaging.wrapImage(cbi, null);
			Region aciRootRegion = PageImageAnalysis.getPageRegion(aci, pageImage.currentDpi, minColumnMargin, minBlockMargin, columnAreas, minColumnWidth, false, spm);
			Region[] aciBlocks = getAtomicRegions(aciRootRegion);
			
			vbiGr.setColor(new Color(255, 0, 0, 64));
			for (int w = 0; w < words.length; w++)
				vbiGr.fillRect(words[w].bounds.left, words[w].bounds.top, words[w].bounds.getWidth(), words[w].bounds.getHeight());
			vbiGr.setColor(new Color(0, 0, 255, 64));
//			for (int b = 0; b < apiPageBlocks.length; b++) {
//				if (apiPageBlocks[b].ocrBlock == null)
//					continue; // empty block ...
//				for (int l = 0; l < apiPageBlocks[b].ocrBlock.lines.size(); l++) {
//					OcrLine ol = ((OcrLine) apiPageBlocks[b].ocrBlock.lines.get(l));
//					for (int w = 0; w < ol.words.size(); w++) {
//						OcrWord ow = ((OcrWord) ol.words.get(w));
//						BoundingBox owbb = ow.getPageBounds();
//						vbiGr.fillRect(owbb.left, owbb.top, owbb.getWidth(), owbb.getHeight());
//					}
//				}
//			}
			for (int w = 0; w < pageWords.size(); w++) {
				ImWord word = ((ImWord) pageWords.get(w));
				vbiGr.fillRect(word.bounds.left, word.bounds.top, word.bounds.getWidth(), word.bounds.getHeight());
			}
			vbiGr.setColor(new Color(0, 0, 255, 255));
			for (int w = 0; w < pageWords.size(); w++) {
				ImWord word = ((ImWord) pageWords.get(w));
				vbiGr.drawRect(word.bounds.left, word.bounds.top, word.bounds.getWidth(), word.bounds.getHeight());
			}
			
			vbiGr.setStroke(new BasicStroke(3));
			vbiGr.setColor(new Color(0, 255, 0, 128));
			for (int b = 0; b < apiBlocks.length; b++) {
				BoundingBox apiBb = apiBlocks[b].getBoundingBox();
				vbiGr.drawRect(apiBb.left, apiBb.top, apiBb.getWidth(), apiBb.getHeight());
			}
//			vbiGr.setStroke(new BasicStroke(1));
//			for (int b = 0; b < apiBlocks.length; b++) {
//				if (apiBlockLines[b] == null)
//					continue;
//				for (int l = 0; l < apiBlockLines[b].length; l++)
//					vbiGr.drawRect(apiBlockLines[b][l].getLeftCol(), apiBlockLines[b][l].getTopRow(), apiBlockLines[b][l].getWidth(), apiBlockLines[b][l].getHeight());
//			}
			vbiGr.setStroke(new BasicStroke(3));
//			vbiGr.setColor(new Color(255, 0, 0, 128));
//			for (int b = 0; b < aoiBlocks.length; b++) {
//				BoundingBox aoiBb = aoiBlocks[b].getBoundingBox();
//				vbiGr.drawRect(aoiBb.left, aoiBb.top, aoiBb.getWidth(), aoiBb.getHeight());
//			}
			vbiGr.setColor(new Color(0, 0, 255, 128));
			for (int b = 0; b < aciBlocks.length; b++) {
				BoundingBox aciBb = aciBlocks[b].getBoundingBox();
				vbiGr.drawRect(aciBb.left, aciBb.top, aciBb.getWidth(), aciBb.getHeight());
			}
			
			ImageDisplayDialog idd = new ImageDisplayDialog("OCR relative to page images");
			idd.addImage(vbi, ("Page " + page.pageId + " Lines"));
			idd.addImage(pageImage.image, ("Page " + page.pageId + " Scan"));
			idd.addImage(obi, ("Page " + page.pageId + " OCR"));
			idd.setSize(800, 800);
			idd.setLocationRelativeTo(null);
			idd.setVisible(true);
		}
		
		//	generate and return words
		return ((ImWord[]) pageWords.toArray(new ImWord[pageWords.size()]));
	}
	
	/**
	 * Adjust the baselines of OCR derived words to the lines in a selected
	 * block of a page image. Further, words are also adjusted to the height of
	 * the lines.
	 * @param words an array holding the words to adjust
	 * @param blockBounds the boundary of the block in the argument page image
	 * @param adjustToWords adjust words horizontally inside lines?
	 * @param pm a progress monitor to relay status information
	 * @return an array holding the adjusted words
	 */
	public static ImWord[] adjustOcrWordsToBlock(ImWord[] words, BoundingBox blockBounds, boolean adjustToWords, ProgressMonitor pm) {
		return adjustOcrWordsToBlock(words, null, null, null, blockBounds, adjustToWords, pm);
	}
	
	/**
	 * Adjust the baselines of OCR derived words to the lines in a selected
	 * block of a page image. Further, words are also adjusted to the height of
	 * the lines.
	 * @param words an array holding the words to adjust
	 * @param page the page the words lie in
	 * @param blockBounds the boundary of the block in the argument page image
	 * @param adjustToWords adjust words horizontally inside lines?
	 * @param pm a progress monitor to relay status information
	 * @return an array holding the adjusted words
	 */
	public static ImWord[] adjustOcrWordsToBlock(ImWord[] words, ImPage page, BoundingBox blockBounds, boolean adjustToWords, ProgressMonitor pm) {
		return adjustOcrWordsToBlock(words, page, null, null, blockBounds, adjustToWords, pm);
	}
	
	/**
	 * Adjust the baselines of OCR derived words to the lines in a selected
	 * block of a page image. Further, words are also adjusted to the height of
	 * the lines.
	 * @param words an array holding the words to adjust
	 * @param page the page the words lie in
	 * @param pageImage the image of the page the words lie in
	 * @param blockBounds the boundary of the block in the argument page image
	 * @param adjustToWords adjust words horizontally inside lines?
	 * @param pm a progress monitor to relay status information
	 * @return an array holding the adjusted words
	 */
	public static ImWord[] adjustOcrWordsToBlock(ImWord[] words, ImPage page, PageImage pageImage, BoundingBox blockBounds, boolean adjustToWords, ProgressMonitor pm) {
		return adjustOcrWordsToBlock(words, page, pageImage, null, blockBounds, adjustToWords, pm);
	}
	
	/**
	 * Adjust the baselines of OCR derived words to the lines in a selected
	 * block of a page image. Further, words are also adjusted to the height of
	 * the lines.
	 * @param words an array holding the words to adjust
	 * @param page the page the words lie in
	 * @param pageImage the image of the page the words lie in
	 * @param api an analysis image wrapped around the page image
	 * @param blockBounds the boundary of the block in the argument page image
	 * @param adjustToWords adjust words horizontally inside lines?
	 * @param pm a progress monitor to relay status information
	 * @return an array holding the adjusted words
	 */
	public static ImWord[] adjustOcrWordsToBlock(ImWord[] words, ImPage page, PageImage pageImage, AnalysisImage api, BoundingBox blockBounds, boolean adjustToWords, ProgressMonitor pm) {
		if (words.length == 0)
			return words;
		
		//	check parameters
		ImDocument doc = null;
		int pageId = -1;
		for (int w = 0; w < words.length; w++) {
			if (doc == null)
				doc = words[w].getDocument();
			else if (words[w].getDocument() != doc)
				throw new IllegalArgumentException("Cannot adjust words belonging to different documents");
			if (pageId == -1)
				pageId = words[w].pageId;
			else if (words[w].pageId != pageId)
				throw new IllegalArgumentException("Cannot adjust words belonging to different pages");
		}
		if (page == null)
			page = doc.getPage(pageId);
		if (pageImage == null)
			pageImage = page.getImage();
		if (pm == null)
			pm = ProgressMonitor.dummy;
		if (api == null)
			api = Imaging.wrapImage(pageImage.image, null);
		
		//	adjust and return words
		ArrayList pageWords = new ArrayList();
		HashSet doneWords = new HashSet();
		addAdjustedOcrWordsToBlock(doc, page, words, pageImage, api, blockBounds, adjustToWords, pageWords, doneWords, pm);
		for (int w = 0; w < words.length; w++) {
			if (!doneWords.contains(words[w]))
				pageWords.add(words[w]);
		}
		return ((ImWord[]) pageWords.toArray(new ImWord[pageWords.size()]));
	}
	
	private static void addAdjustedOcrWordsToBlock(ImDocument doc, ImPage page, ImWord[] words, PageImage pageImage, AnalysisImage api, BoundingBox blockBounds, boolean adjustToWords, ArrayList pageWords, HashSet doneWords, ProgressMonitor pm) {
		BufferedImage vbi = null;
		Graphics2D vbiGr = null;
		if (DEBUG_ADJUST_OCR) {
			vbi = new BufferedImage(pageImage.image.getWidth(), pageImage.image.getHeight(), BufferedImage.TYPE_INT_ARGB);
			vbiGr = vbi.createGraphics();
			vbiGr.setColor(Color.WHITE);
			vbiGr.fillRect(0, 0, vbi.getWidth(), vbi.getHeight());
			vbiGr.drawImage(pageImage.image, 0, 0, pageImage.image.getWidth(), pageImage.image.getHeight(), null);
		}
		BlockLine[] apiBlockLines = getBlockLines(api, pageImage.currentDpi, blockBounds, true, pm, vbi);
		PageBlock apiPageBlock = new PageBlock(apiBlockLines);
		
		//	perform the actual work
		BoundingBox ocrBlockBounds = ImLayoutObject.getAggregateBox(words);
		apiPageBlock.ocrBlock = new OcrBlock(ocrBlockBounds);
		apiPageBlock.ocrBlock.words.addAll(Arrays.asList(words));
		addAdjustedOcrWordsToBlock(doc, page, apiPageBlock, pageImage, adjustToWords, pageWords, doneWords, pm);
		
		if (vbi != null) {
			BufferedImage cbi = new BufferedImage(pageImage.image.getWidth(), pageImage.image.getHeight(), BufferedImage.TYPE_INT_ARGB);
			Graphics2D cbiGr = cbi.createGraphics();
			cbiGr.setColor(Color.WHITE);
			cbiGr.fillRect(0, 0, cbi.getWidth(), cbi.getHeight());
			cbiGr.drawImage(pageImage.image, 0, 0, pageImage.image.getWidth(), pageImage.image.getHeight(), null);
			cbiGr.setColor(Color.BLACK);
			for (int w = 0; w < words.length; w++)
				cbiGr.fillRect(words[w].bounds.left, words[w].bounds.top, words[w].bounds.getWidth(), words[w].bounds.getHeight());
			
			vbiGr.setColor(new Color(255, 0, 0, 64));
			for (int w = 0; w < words.length; w++)
				vbiGr.fillRect(words[w].bounds.left, words[w].bounds.top, words[w].bounds.getWidth(), words[w].bounds.getHeight());
			vbiGr.setColor(new Color(0, 0, 255, 64));
//			for (int b = 0; b < apiPageBlocks.length; b++) {
//				if (apiPageBlocks[b].ocrBlock == null)
//					continue; // empty block ...
//				for (int l = 0; l < apiPageBlocks[b].ocrBlock.lines.size(); l++) {
//					OcrLine ol = ((OcrLine) apiPageBlocks[b].ocrBlock.lines.get(l));
//					for (int w = 0; w < ol.words.size(); w++) {
//						OcrWord ow = ((OcrWord) ol.words.get(w));
//						BoundingBox owbb = ow.getPageBounds();
//						vbiGr.fillRect(owbb.left, owbb.top, owbb.getWidth(), owbb.getHeight());
//					}
//				}
//			}
			for (int w = 0; w < pageWords.size(); w++) {
				ImWord word = ((ImWord) pageWords.get(w));
				vbiGr.fillRect(word.bounds.left, word.bounds.top, word.bounds.getWidth(), word.bounds.getHeight());
			}
			
			vbiGr.setStroke(new BasicStroke(3));
			vbiGr.setColor(new Color(0, 255, 0, 128));
			BoundingBox apiBb = apiPageBlock.getBounds();
			vbiGr.drawRect(apiBb.left, apiBb.top, apiBb.getWidth(), apiBb.getHeight());
//			vbiGr.setStroke(new BasicStroke(1));
//			for (int b = 0; b < apiBlocks.length; b++) {
//				if (apiBlockLines[b] == null)
//					continue;
//				for (int l = 0; l < apiBlockLines[b].length; l++)
//					vbiGr.drawRect(apiBlockLines[b][l].getLeftCol(), apiBlockLines[b][l].getTopRow(), apiBlockLines[b][l].getWidth(), apiBlockLines[b][l].getHeight());
//			}
//			vbiGr.setStroke(new BasicStroke(3));
//			vbiGr.setColor(new Color(255, 0, 0, 128));
//			for (int b = 0; b < aoiBlocks.length; b++) {
//				BoundingBox aoiBb = aoiBlocks[b].getBoundingBox();
//				vbiGr.drawRect(aoiBb.left, aoiBb.top, aoiBb.getWidth(), aoiBb.getHeight());
//			}
//			vbiGr.setColor(new Color(0, 0, 255, 128));
//			for (int b = 0; b < aciBlocks.length; b++) {
//				BoundingBox aciBb = aciBlocks[b].getBoundingBox();
//				vbiGr.drawRect(aciBb.left, aciBb.top, aciBb.getWidth(), aciBb.getHeight());
//			}
			
			ImageDisplayDialog idd = new ImageDisplayDialog("OCR relative to page images");
			idd.addImage(vbi, ("Block " + blockBounds + " Lines"));
			idd.addImage(pageImage.image, ("Block " + blockBounds + " Scan"));
			idd.setSize(800, 800);
			idd.setLocationRelativeTo(null);
			idd.setVisible(true);
		}
	}
	
	/**
	 * Adjust the horizontal extent of OCR derived words to the glyphs in the
	 * lines that lie inside a block of a page image.
	 * @param words an array holding the words to adjust
	 * @param blockBounds the boundary of the block in the argument page image
	 * @param pm a progress monitor to relay status information
	 * @return an array holding the adjusted words
	 */
	public static ImWord[] adjustOcrWordsToPageWords(ImWord[] words, BoundingBox blockBounds, ProgressMonitor pm) {
		return adjustOcrWordsToPageWords(words, null, null, blockBounds, pm);
	}
	
	/**
	 * Adjust the horizontal extent of OCR derived words to the glyphs in the
	 * lines that lie inside a block of a page image.
	 * @param words an array holding the words to adjust
	 * @param pageImage the image of the page the words lie in
	 * @param blockBounds the boundary of the block in the argument page image
	 * @param pm a progress monitor to relay status information
	 * @return an array holding the adjusted words
	 */
	public static ImWord[] adjustOcrWordsToPageWords(ImWord[] words, PageImage pageImage, BoundingBox blockBounds, ProgressMonitor pm) {
		return adjustOcrWordsToPageWords(words, null, pageImage, blockBounds, pm);
	}
	
	/**
	 * Adjust the horizontal extent of OCR derived words to the glyphs in the
	 * lines that lie inside a block of a page image.
	 * @param words an array holding the words to adjust
	 * @param page the page the words lie in
	 * @param blockBounds the boundary of the block in the argument page image
	 * @param pm a progress monitor to relay status information
	 * @return an array holding the adjusted words
	 */
	public static ImWord[] adjustOcrWordsToPageWords(ImWord[] words, ImPage page, BoundingBox blockBounds, ProgressMonitor pm) {
		return adjustOcrWordsToPageWords(words, page, null, blockBounds, pm);
	}
	
	/**
	 * Adjust the horizontal extent of OCR derived words to the glyphs in the
	 * lines that lie inside a block of a page image.
	 * @param words an array holding the words to adjust
	 * @param page the page the words lie in
	 * @param pageImage the image of the page the words lie in
	 * @param blockBounds the boundary of the block in the argument page image
	 * @param pm a progress monitor to relay status information
	 * @return an array holding the adjusted words
	 */
	public static ImWord[] adjustOcrWordsToPageWords(ImWord[] words, ImPage page, PageImage pageImage, BoundingBox blockBounds, ProgressMonitor pm) {
		if (words.length == 0)
			return words;
		
		//	check parameters
		ImDocument doc = null;
		int pageId = -1;
		for (int w = 0; w < words.length; w++) {
			if (doc == null)
				doc = words[w].getDocument();
			else if (words[w].getDocument() != doc)
				throw new IllegalArgumentException("Cannot adjust words belonging to different documents");
			if (pageId == -1)
				pageId = words[w].pageId;
			else if (words[w].pageId != pageId)
				throw new IllegalArgumentException("Cannot adjust words belonging to different pages");
		}
		if (page == null)
			page = doc.getPage(pageId);
		if (pageImage == null)
			pageImage = page.getImage();
		if (pm == null)
			pm = ProgressMonitor.dummy;
		
		//	adjust and return words
		ArrayList pageWords = new ArrayList();
		HashSet doneWords = new HashSet();
		addAdjustedOcrWordsToPageWords(doc, words, page, pageImage, blockBounds, pageWords, doneWords, pm);
		for (int w = 0; w < words.length; w++) {
			if (!doneWords.contains(words[w]))
				pageWords.add(words[w]);
		}
		return ((ImWord[]) pageWords.toArray(new ImWord[pageWords.size()]));
	}
	
	private static void addAdjustedOcrWordsToPageWords(ImDocument doc, ImWord[] words, ImPage page, PageImage pageImage, BoundingBox blockBounds, ArrayList pageWords, HashSet doneWord, ProgressMonitor pm) {
		BufferedImage vbi = null;
		Graphics2D vbiGr = null;
		if (DEBUG_ADJUST_OCR) {
			vbi = new BufferedImage(pageImage.image.getWidth(), pageImage.image.getHeight(), BufferedImage.TYPE_INT_ARGB);
			vbiGr = vbi.createGraphics();
			vbiGr.setColor(Color.WHITE);
			vbiGr.fillRect(0, 0, vbi.getWidth(), vbi.getHeight());
			vbiGr.drawImage(pageImage.image, 0, 0, pageImage.image.getWidth(), pageImage.image.getHeight(), null);
		}
		
		//	make sure we have lines to work with (required during OCR decoding, as lines are added after words)
		ImRegion[] blockLines = page.getRegionsInside(ImRegion.LINE_ANNOTATION_TYPE, blockBounds, false);
		if (blockLines.length == 0) {
			ImUtils.sortLeftRightTopDown(words);
			ImWord lastWord = null;
			int lineStart = 0;
			ArrayList bLines = new ArrayList();
			for (int w = 0; w < words.length; w++) {
				ImWord word = words[w];
				if ((lastWord != null) && ImUtils.areTextFlowBreak(lastWord, word)) {
					BoundingBox lineBounds = ImLayoutObject.getAggregateBox(words, lineStart, w);
					bLines.add(new ImRegion(doc, page.pageId, lineBounds, ImRegion.LINE_ANNOTATION_TYPE));
					if (DEBUG_ADJUST_OCR) {
						System.out.print("  - got line at " + lineBounds + " with " + (w - lineStart) + " words:");
						for (int lw = lineStart; lw < w; lw++)
							System.out.print(" " + words[lw].getString());
						System.out.println();
					}
					lineStart = w;
				}
				lastWord = word;
			}
			if (lastWord != null) {
				BoundingBox lineBounds = ImLayoutObject.getAggregateBox(words, lineStart, words.length);
				bLines.add(new ImRegion(doc, page.pageId, lineBounds, ImRegion.LINE_ANNOTATION_TYPE));
				if (DEBUG_ADJUST_OCR) {
					System.out.print("  - got line at " + lineBounds + " with " + (words.length - lineStart) + " words:");
					for (int lw = lineStart; lw < words.length; lw++)
						System.out.print(" " + words[lw].getString());
					System.out.println();
				}
			}
			blockLines = ((ImRegion[]) bLines.toArray(new ImRegion[bLines.size()]));
		}
		
		//	synthesize block from given OCR words
		Arrays.sort(blockLines, ImUtils.topDownOrder);
		BlockLine[] apiBlockLines = new BlockLine[blockLines.length];
		for (int l = 0; l < blockLines.length; l++) {
			int relLeft = (blockLines[l].bounds.left - blockBounds.left);
			int relRight = (blockLines[l].bounds.right - blockBounds.left);
			int relTop = (blockLines[l].bounds.top - blockBounds.top);
			int relBottom = (blockLines[l].bounds.bottom - blockBounds.top);
			apiBlockLines[l] = new BlockLine(blockBounds, 0 /* noo need for a color here */, relLeft, relRight, relTop, relBottom);
			
			//	add line local region coloring (we need that to adjust words horizontally)
			AnalysisImage lineAi = Imaging.wrapImage(pageImage.image.getSubimage(blockLines[l].bounds.left, blockLines[l].bounds.top, blockLines[l].bounds.getWidth(), blockLines[l].bounds.getHeight()), null);
			apiBlockLines[l].setRegions(lineAi);
			
			//	measure region coloring of line
			measureRegions(apiBlockLines[l].regionColors, apiBlockLines[l].regionSizes, apiBlockLines[l].regionMinCols, apiBlockLines[l].regionMaxCols, apiBlockLines[l].regionMinRows, apiBlockLines[l].regionMaxRows);
			
			//	attach small regions (dots, accents) downward
			attachSmallRegionsDownward(apiBlockLines[l].regionColors, (pageImage.currentDpi / 12), (pageImage.currentDpi / 6), 0, apiBlockLines[l].regionSizes, apiBlockLines[l].regionMinCols, apiBlockLines[l].regionMaxCols, apiBlockLines[l].regionMinRows, apiBlockLines[l].regionMaxRows, -1, -1, null);
			
			//	determine baseline
			CountingSet lineRegionBottomCounts = new CountingSet(new TreeMap());
			for (int reg = 1; reg < apiBlockLines[l].regionSizes.length; reg++) {
				if (apiBlockLines[l].regionSizes[reg] == 0)
					continue; // attached above
				//System.out.println(" - " + reg + ": " + blockLines[l].lineRegionSizes[reg] + " pixels in " + blockLines[l].lineRegionMinCols[reg] + "-" + blockLines[l].lineRegionMaxCols[reg] + " x " + blockLines[l].lineRegionMinRows[reg] + "-" + blockLines[l].lineRegionMaxRows[reg]);
				lineRegionBottomCounts.add(new Integer(apiBlockLines[l].regionMaxRows[reg]));
			}
			int avgLineRegionBottom = getAverageMid60(lineRegionBottomCounts, (apiBlockLines[l].getHeight() / 2), Integer.MAX_VALUE);
			apiBlockLines[l].baseline = avgLineRegionBottom;
		}
		PageBlock apiPageBlock = new PageBlock(apiBlockLines);
		
		//	adjust words
		BoundingBox ocrBlockBounds = ImLayoutObject.getAggregateBox(words);
		apiPageBlock.ocrBlock = new OcrBlock(ocrBlockBounds);
		apiPageBlock.ocrBlock.words.addAll(Arrays.asList(words));
		addAdjustedOcrWordsToBlock(doc, page, apiPageBlock, pageImage, true, pageWords, doneWord, pm);
		
		if (vbi != null) {
			BufferedImage cbi = new BufferedImage(pageImage.image.getWidth(), pageImage.image.getHeight(), BufferedImage.TYPE_INT_ARGB);
			Graphics2D cbiGr = cbi.createGraphics();
			cbiGr.setColor(Color.WHITE);
			cbiGr.fillRect(0, 0, cbi.getWidth(), cbi.getHeight());
			cbiGr.drawImage(pageImage.image, 0, 0, pageImage.image.getWidth(), pageImage.image.getHeight(), null);
			cbiGr.setColor(Color.BLACK);
			for (int w = 0; w < words.length; w++)
				cbiGr.fillRect(words[w].bounds.left, words[w].bounds.top, words[w].bounds.getWidth(), words[w].bounds.getHeight());
//			AnalysisImage aci = Imaging.wrapImage(cbi, null);
			
			vbiGr.setColor(new Color(255, 0, 0, 64));
			for (int w = 0; w < words.length; w++)
				vbiGr.fillRect(words[w].bounds.left, words[w].bounds.top, words[w].bounds.getWidth(), words[w].bounds.getHeight());
			vbiGr.setColor(new Color(0, 0, 255, 64));
//			for (int b = 0; b < apiPageBlocks.length; b++) {
//				if (apiPageBlocks[b].ocrBlock == null)
//					continue; // empty block ...
//				for (int l = 0; l < apiPageBlocks[b].ocrBlock.lines.size(); l++) {
//					OcrLine ol = ((OcrLine) apiPageBlocks[b].ocrBlock.lines.get(l));
//					for (int w = 0; w < ol.words.size(); w++) {
//						OcrWord ow = ((OcrWord) ol.words.get(w));
//						BoundingBox owbb = ow.getPageBounds();
//						vbiGr.fillRect(owbb.left, owbb.top, owbb.getWidth(), owbb.getHeight());
//					}
//				}
//			}
			for (int w = 0; w < pageWords.size(); w++) {
				ImWord word = ((ImWord) pageWords.get(w));
				vbiGr.fillRect(word.bounds.left, word.bounds.top, word.bounds.getWidth(), word.bounds.getHeight());
			}
			vbiGr.setColor(new Color(0, 0, 255, 255));
			for (int w = 0; w < pageWords.size(); w++) {
				ImWord word = ((ImWord) pageWords.get(w));
				vbiGr.drawRect(word.bounds.left, word.bounds.top, word.bounds.getWidth(), word.bounds.getHeight());
			}
			
			vbiGr.setStroke(new BasicStroke(3));
			vbiGr.setColor(new Color(0, 255, 0, 128));
			BoundingBox apiBb = apiPageBlock.getBounds();
			vbiGr.drawRect(apiBb.left, apiBb.top, apiBb.getWidth(), apiBb.getHeight());
//			vbiGr.setStroke(new BasicStroke(1));
//			for (int b = 0; b < apiBlocks.length; b++) {
//				if (apiBlockLines[b] == null)
//					continue;
//				for (int l = 0; l < apiBlockLines[b].length; l++)
//					vbiGr.drawRect(apiBlockLines[b][l].getLeftCol(), apiBlockLines[b][l].getTopRow(), apiBlockLines[b][l].getWidth(), apiBlockLines[b][l].getHeight());
//			}
//			vbiGr.setStroke(new BasicStroke(3));
//			vbiGr.setColor(new Color(255, 0, 0, 128));
//			for (int b = 0; b < aoiBlocks.length; b++) {
//				BoundingBox aoiBb = aoiBlocks[b].getBoundingBox();
//				vbiGr.drawRect(aoiBb.left, aoiBb.top, aoiBb.getWidth(), aoiBb.getHeight());
//			}
//			vbiGr.setColor(new Color(0, 0, 255, 128));
//			for (int b = 0; b < aciBlocks.length; b++) {
//				BoundingBox aciBb = aciBlocks[b].getBoundingBox();
//				vbiGr.drawRect(aciBb.left, aciBb.top, aciBb.getWidth(), aciBb.getHeight());
//			}
			
			ImageDisplayDialog idd = new ImageDisplayDialog("OCR relative to page images");
			idd.addImage(vbi, ("Page " + page.pageId + "." + blockBounds + " Lines"));
			idd.addImage(pageImage.image, ("Page " + page.pageId + "." + blockBounds + " Scan"));
			idd.setSize(800, 800);
			idd.setLocationRelativeTo(null);
			idd.setVisible(true);
		}
	}
	
	//	TODO actually use progress monitor !!!
	private static void addAdjustedOcrWordsToBlock(ImDocument doc, ImPage page, PageBlock apiPageBlock, PageImage pageImage, boolean adjustWords, ArrayList pageWords, HashSet doneWords, ProgressMonitor pm) {
		if (apiPageBlock.ocrBlock == null)
			return;
		
		//	assess existing block structure
		BoundingBox ocrBlockBounds = apiPageBlock.ocrBlock.getBounds();
		apiPageBlock.ocrBlock.exBlocks = page.getRegionsInside(ImRegion.BLOCK_ANNOTATION_TYPE, ocrBlockBounds, true);
		ImRegion[] exParagraphs = page.getRegionsInside(ImRegion.PARAGRAPH_TYPE, ocrBlockBounds, true);
		Arrays.sort(exParagraphs, ImUtils.topDownOrder);
		ImRegion[] exLines = page.getRegionsInside(ImRegion.LINE_ANNOTATION_TYPE, ocrBlockBounds, true);
		Arrays.sort(exLines, ImUtils.topDownOrder);
		ImRegion[] endingExPara = new ImRegion[exLines.length];
		for (int l = 0, p = 0; (l < exLines.length); l++) {
			if ((p < exParagraphs.length) && exParagraphs[p].bounds.includes(exLines[l].bounds, true)) {
				endingExPara[l] = exParagraphs[p];
				p++; // we've found the start of this one, switch to next paragraph
			}
			else if (l != 0) {
				//	we're still inside previous paragraph, carry reference
				endingExPara[l] = endingExPara[l - 1];
				endingExPara[l - 1] = null;
			}
		}
		
		//	sort block words into lines (only now that we've merged any OCR blocks lying in same page block, and vice versa)
		if (DEBUG_ADJUST_OCR)
			System.out.println("Structuring page block " + apiPageBlock.getBounds());
		ImUtils.sortLeftRightTopDown(apiPageBlock.ocrBlock.words);
		OcrLine ocrLine = null;
		ImWord lastWord = null;
		for (int w = 0; w < apiPageBlock.ocrBlock.words.size(); w++) {
			ImWord word = ((ImWord) apiPageBlock.ocrBlock.words.get(w));
			if ((lastWord == null) || ImUtils.areTextFlowBreak(lastWord, word)) {
				if ((DEBUG_ADJUST_OCR) && (ocrLine != null)) {
					System.out.print("  - got line at " + ocrLine.getPageBounds() + " with " + ocrLine.words.size() + " words:");
					for (int lw = 0; lw < ocrLine.words.size(); lw++)
						System.out.print(" " + ((OcrWord) ocrLine.words.get(lw)).word.getString());
					System.out.println();
				}
				if (ocrLine != null)
					Collections.sort(ocrLine.words);
				ocrLine = new OcrLine(apiPageBlock.ocrBlock);
			}
			ocrLine.addWord(word);
			lastWord = word;
		}
		if (ocrLine != null)
			Collections.sort(ocrLine.words);
		if ((DEBUG_ADJUST_OCR) && (ocrLine != null)) {
			System.out.print("  - got line at " + ocrLine.getPageBounds() + " with " + ocrLine.words.size() + " words:");
			for (int lw = 0; lw < ocrLine.words.size(); lw++)
				System.out.print(" " + ((OcrWord) ocrLine.words.get(lw)).word.getString());
			System.out.println();
		}
		if (DEBUG_ADJUST_OCR)
			System.out.println("  ==> got " + apiPageBlock.ocrBlock.lines.size() + " lines");
		
		//	do we have a match?
		if (apiPageBlock.blockLines.length != apiPageBlock.ocrBlock.lines.size())
			return; // just too ambiguous, and word salvaging will take care of the words for us
		
		//	associate OCR lines with existing line regions (before we start moving things around)
		for (int ol = 0; ol < apiPageBlock.ocrBlock.lines.size(); ol++) {
			ocrLine = ((OcrLine) apiPageBlock.ocrBlock.lines.get(ol));
			BoundingBox olBb = ocrLine.getPageBounds();
			for (int l = 0; l < exLines.length; l++)
				if (olBb.includes(exLines[l].bounds, true)) {
					ocrLine.exLine = exLines[l];
					ocrLine.endingExPara = endingExPara[l];
					break;
				}
		}
		
		/*
TODO Tame OCR adjustment to prevent errors like the one reported by Felipe:
- limit how far OCR lines will stretch horizontally _after_ whole-block translation ...
- ... and also apply to horizontal stretch for whole block (especially in single-line blocks)
==> should help prevent havoc in lines with leading or trailing words missed by OCR
- throw OcrAdjustmentException (extends IllegalArgumentException) instead ...
- ... containing lines ot blocks with such problems
- catch those exceptions in PDF decoder ...
- ... and use contained information to mark ocrAdjustmentFailure regions
- maybe handle differently in OcrAdjuster gizmo if latter running on single block ...
- ... but keep up in whole-document mode
  ==> add respective switch(es) to method signatures ...
  ==> ... most likely as maximum horizontal stretch factor ...
  ==> ... taking inverse if <1 to simplify parametrization ...
  ==> ... and also add respective template parameter
- pick up ocrAdjustmentFailure regions in OCR QC (alongside OCR conflict areas) ...
- ... and also provide respective visualization support via display extensions
==> test whole thing with very document from Carol/Felipe to get idea of sensible thresholds for horizontal stretch (for both whole blocks and individual lines) ...
==> ... but also check through usual "off" embedded OCR documents (Puffinus <xyz>, etc.) most likely noted by name in test method
==> TEST: BullSocVaudScNat.68.333-349.pdf.imd (version before we fixed font issues)
		 */
		
		//	adjust OCR block to page block (limiting shift and stretch, though, as especially single-line blocks can be missing leading or tailing words)
		if (DEBUG_ADJUST_OCR) {
			System.out.println("Adjusting lines in page block " + apiPageBlock.getBounds());
			System.out.println(" - got " + apiPageBlock.blockLines.length + " block lines and " + apiPageBlock.ocrBlock.lines.size() + " OCR lines");
			System.out.println(" - OCR block is " + apiPageBlock.ocrBlock.getBounds());
		}
		int widthDelta = Math.abs((apiPageBlock.right - apiPageBlock.left) - (apiPageBlock.ocrBlock.right - apiPageBlock.ocrBlock.left));
		int pbWidthPercent = ((widthDelta * 100) / (apiPageBlock.right - apiPageBlock.left));
		int obWidthPercent = ((widthDelta * 100) / (apiPageBlock.ocrBlock.right - apiPageBlock.ocrBlock.left));
		if (DEBUG_ADJUST_OCR)
			System.out.println(" - width difference is " + widthDelta + " (" + pbWidthPercent + "% of page block, " + obWidthPercent + "% of OCR block)");
		if ((pbWidthPercent < 50) && (obWidthPercent < 50)) {
			apiPageBlock.ocrBlock.shiftHorizontally(apiPageBlock.left - apiPageBlock.ocrBlock.left);
			apiPageBlock.ocrBlock.stretchHorizontally(apiPageBlock.right - apiPageBlock.ocrBlock.right);
		}
		else if (DEBUG_ADJUST_OCR)
			System.out.println("   ==> horizontal adjustment rejected");
		int heightDelta = Math.abs((apiPageBlock.bottom - apiPageBlock.top) - (apiPageBlock.ocrBlock.bottom - apiPageBlock.ocrBlock.top));
		int pbHeightPercent = ((heightDelta * 100) / (apiPageBlock.bottom - apiPageBlock.top));
		int obHeightPercent = ((heightDelta * 100) / (apiPageBlock.ocrBlock.bottom - apiPageBlock.ocrBlock.top));
		if (DEBUG_ADJUST_OCR)
			System.out.println(" - heigth difference is " + heightDelta + " (" + pbHeightPercent + "% of page block, " + obHeightPercent + "% of OCR block)");
		if ((pbHeightPercent < 50) && (obHeightPercent < 50)) {
			apiPageBlock.ocrBlock.shiftVertically(apiPageBlock.top - apiPageBlock.ocrBlock.top);
			apiPageBlock.ocrBlock.stretchVertically(apiPageBlock.bottom - apiPageBlock.ocrBlock.bottom);
		}
		else if (DEBUG_ADJUST_OCR)
			System.out.println("   ==> vertical adjustment rejected");
		
		//	adjust word boundaries, transfer baselines, and collect words
		if (DEBUG_ADJUST_OCR)
			System.out.println("Evaluating page block " + apiPageBlock.getBounds());
		
		//	pair up page image lines with OCR lines
		int lastMatchOl = -1;
		for (int bl = 0; bl < apiPageBlock.blockLines.length; bl++) {
			BoundingBox blBb = apiPageBlock.blockLines[bl].getPageBounds();
			if (DEBUG_ADJUST_OCR)
				System.out.println(" - line " + blBb/* + " with " + apiPageBlocks[b].blockLines[bl].getWordCount() + " words"*/);
			for (int ol = (lastMatchOl + 1); ol < apiPageBlock.ocrBlock.lines.size(); ol++) {
				ocrLine = ((OcrLine) apiPageBlock.ocrBlock.lines.get(ol));
				BoundingBox olBb = ocrLine.getPageBounds();
				if (!blBb.includes(olBb, true))
					continue;
//				if (apiPageBlock.blockLines[bl].ocrLine == null)
//					apiPageBlock.blockLines[bl].ocrLine = ocrLineOld;
//				else throw new RuntimeException("GOTCHA !!!");
				apiPageBlock.blockLines[bl].ocrLine = ocrLine;
				int owLeftGap = (apiPageBlock.blockLines[bl].getPageLeft() - ocrLine.getPageLeft());
				if (DEBUG_ADJUST_OCR) {
					System.out.println("   - matched to OCR line " + olBb + " with " + ocrLine.words.size() + " words");
					System.out.println("   - line region coloring is " + apiPageBlock.blockLines[bl].regionColors.length + "x" + apiPageBlock.blockLines[bl].regionColors[0].length);
					System.out.println("   - block offset compensation gap is " + owLeftGap);
				}
			}
		}
		
		//	if active, adjust word boundaries horizontally to (a) fully include regions and (b) also adjacent regions
		CountingSet blockRegionNeighborGaps = new CountingSet(new TreeMap());
		CountingSet blockWordRegionNeighborGaps = new CountingSet(new TreeMap());
		ArrayList blockWords = new ArrayList();
		ArrayList paraWords = new ArrayList();
		ArrayList lineWords = new ArrayList();
		HashMap blockWordsToLines = new HashMap();
		if (adjustWords) {
			for (int bl = 0; bl < apiPageBlock.blockLines.length; bl++) {
				if (apiPageBlock.blockLines[bl].ocrLine == null)
					continue; // nothing to adjust here
				BoundingBox blBb = apiPageBlock.blockLines[bl].getPageBounds();
				if (DEBUG_ADJUST_OCR)
					System.out.println(" - line " + blBb/* + " with " + apiPageBlocks[b].blockLines[bl].getWordCount() + " words"*/);
				
				//	get fresh region coloring of line (gets somewhat messed up by whole-block analysis)
				AnalysisImage lineAi = Imaging.wrapImage(pageImage.image.getSubimage(apiPageBlock.blockLines[bl].getPageLeft(), apiPageBlock.blockLines[bl].getPageTop(), apiPageBlock.blockLines[bl].getWidth(), apiPageBlock.blockLines[bl].getHeight()), null);
				apiPageBlock.blockLines[bl].setRegions(lineAi);
				
				//	measure region coloring of line
				measureRegions(apiPageBlock.blockLines[bl].regionColors, apiPageBlock.blockLines[bl].regionSizes, apiPageBlock.blockLines[bl].regionMinCols, apiPageBlock.blockLines[bl].regionMaxCols, apiPageBlock.blockLines[bl].regionMinRows, apiPageBlock.blockLines[bl].regionMaxRows);
				
				//	attach small regions (dots, accents) downward
				attachSmallRegionsDownward(apiPageBlock.blockLines[bl].regionColors, (pageImage.currentDpi / 12), (pageImage.currentDpi / 6), 0, apiPageBlock.blockLines[bl].regionSizes, apiPageBlock.blockLines[bl].regionMinCols, apiPageBlock.blockLines[bl].regionMaxCols, apiPageBlock.blockLines[bl].regionMinRows, apiPageBlock.blockLines[bl].regionMaxRows, -1, -1, null);
				
				//	sort regions left to right (simplifies neighbor search)
				long[] lineRegionsLeftRight = new long[apiPageBlock.blockLines[bl].regionSizes.length];
				Arrays.fill(lineRegionsLeftRight, Long.MAX_VALUE);
				for (int reg = 1; reg < apiPageBlock.blockLines[bl].regionSizes.length; reg++) {
					if (apiPageBlock.blockLines[bl].regionSizes[reg] == 0)
						continue; // attached above
					lineRegionsLeftRight[reg] &= apiPageBlock.blockLines[bl].regionMinCols[reg];
					lineRegionsLeftRight[reg] <<= 32;
					lineRegionsLeftRight[reg] |= reg;
				}
				Arrays.sort(lineRegionsLeftRight);
				
				//	measure actual distances of regions to closest right neighbor
				apiPageBlock.blockLines[bl].regionLeftNeighbors = new int[apiPageBlock.blockLines[bl].regionSizes.length];
				Arrays.fill(apiPageBlock.blockLines[bl].regionLeftNeighbors, 0);
				apiPageBlock.blockLines[bl].regionLeftNeighborDistances = new int[apiPageBlock.blockLines[bl].regionSizes.length];
				Arrays.fill(apiPageBlock.blockLines[bl].regionLeftNeighborDistances, Integer.MAX_VALUE);
				apiPageBlock.blockLines[bl].regionRightNeighbors = new int[apiPageBlock.blockLines[bl].regionSizes.length];
				Arrays.fill(apiPageBlock.blockLines[bl].regionRightNeighbors, 0);
				apiPageBlock.blockLines[bl].regionRightNeighborDistances = new int[apiPageBlock.blockLines[bl].regionSizes.length];
				Arrays.fill(apiPageBlock.blockLines[bl].regionRightNeighborDistances, Integer.MAX_VALUE);
				CountingSet lineRegionNeighborGaps = new CountingSet(new TreeMap());
				for (int lrReg = 0; lrReg < (lineRegionsLeftRight.length - 1); lrReg++) {
					if (lineRegionsLeftRight[lrReg] == Long.MAX_VALUE)
						break; // nothing more to come
					if (lineRegionsLeftRight[lrReg + 1] == Long.MAX_VALUE)
						break; // nothing more to come on our right
					int reg = ((int) (lineRegionsLeftRight[lrReg] & 0x7FFFFFFF));
					if (apiPageBlock.blockLines[bl].regionSizes[reg] == 0)
						continue; // attached above
					int enReg = ((int) (lineRegionsLeftRight[lrReg + 1] & 0x7FFFFFFF));
					if (apiPageBlock.blockLines[bl].regionSizes[enReg] == 0)
						continue; // attached above
					for (int c = apiPageBlock.blockLines[bl].regionMinCols[reg]; c <= apiPageBlock.blockLines[bl].regionMaxCols[reg]; c++) {
						for (int r = apiPageBlock.blockLines[bl].regionMinRows[reg]; r <= apiPageBlock.blockLines[bl].regionMaxRows[reg]; r++) {
							if (apiPageBlock.blockLines[bl].regionColors[c][r] != reg)
								continue;
							int nReg = -1;
							int nRegDist = -1;
							for (int lc = (c+1); lc < apiPageBlock.blockLines[bl].regionColors.length; lc++) {
								if (apiPageBlock.blockLines[bl].regionColors[lc][r] == reg)
									break; // we're getting back to this one in next column
								if (apiPageBlock.blockLines[bl].regionColors[lc][r] == 0)
									continue; // nothing there
								int lReg = apiPageBlock.blockLines[bl].regionColors[lc][r];
								if (apiPageBlock.blockLines[bl].regionSizes[lReg] == 0)
									continue; // attached above
								nReg = lReg;
								nRegDist = (lc - (c + 1));
								break; // we found our neighbor on current row
							}
							if (nRegDist == -1)
								continue; // nothing found at all, or we were in our own middle
							if (nRegDist < apiPageBlock.blockLines[bl].regionRightNeighborDistances[reg]) {
								apiPageBlock.blockLines[bl].regionLeftNeighbors[nReg] = reg;
								apiPageBlock.blockLines[bl].regionLeftNeighborDistances[nReg] = nRegDist;
								apiPageBlock.blockLines[bl].regionRightNeighbors[reg] = nReg;
								apiPageBlock.blockLines[bl].regionRightNeighborDistances[reg] = nRegDist;
							}
						}
					}
					if (apiPageBlock.blockLines[bl].regionRightNeighbors[reg] != enReg) /* we _should_ have found our neighbor to the right */ {
						apiPageBlock.blockLines[bl].regionRightNeighbors[reg] = enReg;
						apiPageBlock.blockLines[bl].regionRightNeighborDistances[reg] = Math.max((apiPageBlock.blockLines[bl].regionMinCols[enReg] - (apiPageBlock.blockLines[bl].regionMaxCols[reg] + 1)), 0);
					}
					if (apiPageBlock.blockLines[bl].regionRightNeighborDistances[reg] == Integer.MAX_VALUE)
						continue;
					if (apiPageBlock.blockLines[bl].regionRightNeighborDistances[reg] > (apiPageBlock.blockLines[bl].getHeight() / 2)) // cap distance off at half the line height, space is hardly ever wider
						apiPageBlock.blockLines[bl].regionRightNeighborDistances[reg] = (apiPageBlock.blockLines[bl].getHeight() / 2);
					lineRegionNeighborGaps.add(new Integer(apiPageBlock.blockLines[bl].regionRightNeighborDistances[reg]));
				}
				blockRegionNeighborGaps.addAll(lineRegionNeighborGaps);
				if (DEBUG_ADJUST_OCR) {
					System.out.println("   - region neighbor gaps: " + lineRegionNeighborGaps);
					System.out.println("     ==> average neighbor gap is " + getAverage(lineRegionNeighborGaps, 0, Integer.MAX_VALUE));
				}
				
				//	count out words per region ...
				//	... as well as average region distance inside words
				int owLeftGap = (apiPageBlock.blockLines[bl].getPageLeft() - apiPageBlock.blockLines[bl].ocrLine.getPageLeft());
				if (DEBUG_ADJUST_OCR) {
					System.out.println("   - OCR block is " + apiPageBlock.blockLines[bl].ocrLine.getPageBounds());
					System.out.println("   - block offset compensation gap is " + owLeftGap);
				}
				Object[] lineRegionWords = new Object[apiPageBlock.blockLines[bl].regionSizes.length];
				CountingSet[] wordRegions = new CountingSet[apiPageBlock.blockLines[bl].ocrLine.words.size()];
				for (int ow = 0; ow < apiPageBlock.blockLines[bl].ocrLine.words.size(); ow++) {
					OcrWord ocrWord = ((OcrWord) apiPageBlock.blockLines[bl].ocrLine.words.get(ow));
					int pageLeft = Math.max(0, (ocrWord.left - owLeftGap));
					int pageRight = Math.min(apiPageBlock.blockLines[bl].regionColors.length, (ocrWord.right - owLeftGap));
					
					//	check which regions touch word area
					wordRegions[ow] = new CountingSet();
					for (int c = pageLeft; c < pageRight; c++)
						for (int r = 0; r < apiPageBlock.blockLines[bl].regionColors[c].length; r++) {
							int reg = apiPageBlock.blockLines[bl].regionColors[c][r];
							if (reg == 0)
								continue;
							wordRegions[ow].add(new Integer(reg));
							if (lineRegionWords[reg] == ocrWord)
								continue;
							if (lineRegionWords[reg] == null)
								lineRegionWords[reg] = ocrWord;
							else if (lineRegionWords[reg] instanceof LinkedHashSet)
								((LinkedHashSet) lineRegionWords[reg]).add(ocrWord);
							else {
								LinkedHashSet lrws = new LinkedHashSet();
								lrws.add(lineRegionWords[reg]);
								lrws.add(ocrWord);
								lineRegionWords[reg] = lrws;
							}
						}
					if (DEBUG_ADJUST_OCR)
						System.out.println("     - regions in OCR word " + ocrWord.word.getString() + " at " + ocrWord.word.bounds + " (" + pageLeft + "-" + pageRight + " in line) are " + wordRegions[ow]);
				}
				if (DEBUG_ADJUST_OCR) {
					for (int r = 0; r < lineRegionWords.length; r++)
						System.out.println("     - words in region " + r + " at [" +
								apiPageBlock.blockLines[bl].regionMinCols[r] + ", " +
								apiPageBlock.blockLines[bl].regionMaxCols[r] + ", " +
								apiPageBlock.blockLines[bl].regionMinRows[r] + ", " +
								apiPageBlock.blockLines[bl].regionMaxRows[r] +
								"]: " + lineRegionWords[r]);
				}
				
				//	collect region gaps within current word
				CountingSet[] wordRegionNeighborGaps = new CountingSet[apiPageBlock.blockLines[bl].ocrLine.words.size()];
				CountingSet lineWordRegionNeighborGaps = new CountingSet(new TreeMap());
				for (int ow = 0; ow < apiPageBlock.blockLines[bl].ocrLine.words.size(); ow++) {
					wordRegionNeighborGaps[ow] = new CountingSet(new TreeMap());
					for (Iterator regit = wordRegions[ow].iterator(); regit.hasNext();) {
						Integer reg = ((Integer) regit.next());
						if (apiPageBlock.blockLines[bl].regionRightNeighbors[reg.intValue()] == 0)
							continue; // no neighbor at all
						Integer nReg = new Integer(apiPageBlock.blockLines[bl].regionRightNeighbors[reg.intValue()]);
						if (!wordRegions[ow].contains(nReg))
							continue; // neighbor in different word, we get at those below
						Integer nRegDist = new Integer(apiPageBlock.blockLines[bl].regionRightNeighborDistances[reg.intValue()]);
						wordRegionNeighborGaps[ow].add(nRegDist);
						lineWordRegionNeighborGaps.add(nRegDist);
					}
					if (DEBUG_ADJUST_OCR) {
						OcrWord ocrWord = ((OcrWord) apiPageBlock.blockLines[bl].ocrLine.words.get(ow));
						System.out.println("     - region distances within OCR word " + ocrWord.word.getString() + " at " + ocrWord.word.bounds + " are " + wordRegionNeighborGaps[ow]);
					}
				}
				blockWordRegionNeighborGaps.addAll(lineWordRegionNeighborGaps);
				if (DEBUG_ADJUST_OCR)
					System.out.println("   - region distances within words are " + lineWordRegionNeighborGaps);
				int avgLineWordRegionNeighborGap = getAverageMid60(lineWordRegionNeighborGaps, 0, Integer.MAX_VALUE);
				if (DEBUG_ADJUST_OCR)
					System.out.println("     ==> average word region distance is " + avgLineWordRegionNeighborGap);
				CountingSet lineCrossWordRegionNeighborGaps = new CountingSet(new TreeMap());
				lineCrossWordRegionNeighborGaps.addAll(lineRegionNeighborGaps);
				lineCrossWordRegionNeighborGaps.removeAll(lineWordRegionNeighborGaps);
				if (DEBUG_ADJUST_OCR)
					System.out.println("   - region distances across words are " + lineCrossWordRegionNeighborGaps);
				int avgLineCrossWordRegionNeighborGap = getAverageMid60(lineCrossWordRegionNeighborGaps, 0, Integer.MAX_VALUE);
				if (DEBUG_ADJUST_OCR)
					System.out.println("     ==> average cross word region distance is " + avgLineCrossWordRegionNeighborGap);
				
				//	contract words left and right to fully exclude whitespace regions
				for (int ow = 0; ow < apiPageBlock.blockLines[bl].ocrLine.words.size(); ow++) {
					if (wordRegions[ow].isEmpty())
						continue; // nothing we can do about this one
					OcrWord ocrWord = ((OcrWord) apiPageBlock.blockLines[bl].ocrLine.words.get(ow));
					if (DEBUG_ADJUST_OCR)
						System.out.println("     - horizontally shrinking OCR word " + ocrWord.word.getString() + " at " + ocrWord.word.bounds + " (line relative extent is " + ocrWord.left + "-" + ocrWord.right + ")");
					int pageLeft = (ocrWord.left - owLeftGap);
					int pageRight = (ocrWord.right - owLeftGap);
					int conPageLeft = pageRight;
					int conPageRight = pageLeft;
					for (Iterator regit = wordRegions[ow].iterator(); regit.hasNext();) {
						Integer reg = ((Integer) regit.next());
						if (lineRegionWords[reg.intValue()] == ocrWord) {}
						else if (lineRegionWords[reg.intValue()] == null)
							lineRegionWords[reg.intValue()] = ocrWord; // claim so far unassigned region (can happen with italics)
//						//	CANNOT DO THIS HERE, MIGHT EXCLUDE CONTESTED REGIONS
//						else continue; // skip over contested regions, OCR adjuster does those
						int pageRegLeft = apiPageBlock.blockLines[bl].regionMinCols[reg.intValue()];
						conPageLeft = Math.min(conPageLeft, pageRegLeft);
						int pageRegRight = (apiPageBlock.blockLines[bl].regionMaxCols[reg.intValue()] + 1);
						conPageRight = Math.max(conPageRight, pageRegRight);
					}
					if (conPageRight <= conPageLeft)
						continue; // nothing to work with at all
					if ((conPageLeft <= pageLeft) && (pageRight <= conPageRight))
						continue; // no contraction at all
					ocrWord.left = Math.max(ocrWord.left, (conPageLeft + owLeftGap)); // make sure to not expand, we do that below
					ocrWord.right = Math.min(ocrWord.right, (conPageRight + owLeftGap)); // make sure to not expand, we do that below
					if (DEBUG_ADJUST_OCR)
						System.out.println("       ==> block relative extent contracted to " + ocrWord.left + "-" + ocrWord.right);
				}
				
				//	expand words left and right to fully include overlapping regions
				for (int ow = 0; ow < apiPageBlock.blockLines[bl].ocrLine.words.size(); ow++) {
					if (wordRegions[ow].isEmpty())
						continue; // nothing we can do about this one
					OcrWord ocrWord = ((OcrWord) apiPageBlock.blockLines[bl].ocrLine.words.get(ow));
					if (DEBUG_ADJUST_OCR)
						System.out.println("     - horizontally expanding OCR word " + ocrWord.word.getString() + " at " + ocrWord.word.bounds + " (block relative extent is " + ocrWord.left + "-" + ocrWord.right + ")");
					OcrWord pOcrWord = ((ow == 0) ? null : ((OcrWord) apiPageBlock.blockLines[bl].ocrLine.words.get(ow - 1)));
					int minPageLeft = ((pOcrWord == null) ? 0 : (pOcrWord.right - owLeftGap));
					OcrWord nOcrWord = (((ow + 1) < apiPageBlock.blockLines[bl].ocrLine.words.size()) ? ((OcrWord) apiPageBlock.blockLines[bl].ocrLine.words.get(ow + 1)) : null);
					int maxPageRight = ((nOcrWord == null) ? apiPageBlock.blockLines[bl].regionColors.length : (nOcrWord.left - owLeftGap));
					if (DEBUG_ADJUST_OCR)
						System.out.println("       - expansion limits are " + minPageLeft + "-" + maxPageRight);
					for (boolean expanded = true; expanded;) {
						expanded = false;
						int pageLeft = (ocrWord.left - owLeftGap);
						int pageRight = (ocrWord.right - owLeftGap);
						int exPageLeft = pageLeft;
						int exPageRight = pageRight;
						for (Iterator regit = wordRegions[ow].iterator(); regit.hasNext();) {
							Integer reg = ((Integer) regit.next());
							boolean expandLeft = true;
							boolean expandRight = true;
							if (lineRegionWords[reg.intValue()] == ocrWord) {}
							else if (lineRegionWords[reg.intValue()] == null)
								lineRegionWords[reg.intValue()] = ocrWord; // claim so far unassigned region (can happen with italics)
//							else continue; // skip over contested regions, OCR adjuster does those
							else if (lineRegionWords[reg.intValue()] instanceof LinkedHashSet) {
								OcrWord firstConflictWord = null;
								OcrWord lastConflictWord = null;
								for (Iterator owit = ((LinkedHashSet) lineRegionWords[reg.intValue()]).iterator(); owit.hasNext();) {
									lastConflictWord = ((OcrWord) owit.next());
									if (firstConflictWord == null)
										firstConflictWord = lastConflictWord;
								}
								if (DEBUG_ADJUST_OCR)
									System.out.println("     - last of " + ((LinkedHashSet) lineRegionWords[reg.intValue()]).size() + " OCR words conflicting over region " + reg + " is " + lastConflictWord.word.getString() + " at " + lastConflictWord.word.bounds + " (block relative extent is " + lastConflictWord.left + "-" + lastConflictWord.right + ")");
								expandLeft = (firstConflictWord == ocrWord); // can only expand leftmost contestant to left
								expandRight = (lastConflictWord == ocrWord); // can only expand rightmost contestant to right
								if (!expandLeft && !expandRight)
									continue; // sandwiched in, cannot do anything with current word
							}
							else continue; // shouldn't happen, but let's be safe
							if (expandLeft) {
								int pageRegLeft = apiPageBlock.blockLines[bl].regionMinCols[reg.intValue()];
								if (pageRegLeft < exPageLeft) {
									exPageLeft = Math.max(pageRegLeft, minPageLeft); // avoid bumping into previous word
									expanded = (expanded || (exPageLeft < pageLeft)); // catch endless loop from claiming partially contested region time and again
								}
							}
							if (expandRight) {
								int pageRegRight = (apiPageBlock.blockLines[bl].regionMaxCols[reg.intValue()] + 1);
								if (exPageRight < pageRegRight) {
									exPageRight = pageRegRight;
									expanded = true;
								}
							}
						}
						if (expanded) {
							for (int c = exPageLeft; c < exPageRight; c++) {
								if (c == pageLeft) {
									c = (pageRight - 1); // jump over old extent (right boundary is exclusive, need to revisit)
									continue; // need to go around to have boundary condition checked, though
								}
								for (int r = 0; r < apiPageBlock.blockLines[bl].regionColors[c].length; r++) {
									int reg = apiPageBlock.blockLines[bl].regionColors[c][r];
									if (reg == 0)
										continue;
									wordRegions[ow].add(new Integer(reg));
								}
							}
							ocrWord.left = (exPageLeft + owLeftGap);
							ocrWord.right = (exPageRight + owLeftGap);
							if (DEBUG_ADJUST_OCR)
								System.out.println("       ==> block relative extent expanded to " + ocrWord.left + "-" + ocrWord.right);
						}
					}
				}
				
				//	expand words (mostly) right to also cover previously-unassigned regions if distance small enough
				for (int ow = 0; ow < apiPageBlock.blockLines[bl].ocrLine.words.size(); ow++) {
					if (wordRegions[ow].isEmpty())
						continue; // nothing we can do about this one
					OcrWord ocrWord = ((OcrWord) apiPageBlock.blockLines[bl].ocrLine.words.get(ow));
					if (DEBUG_ADJUST_OCR)
						System.out.println("     - horizontally expanding OCR word " + ocrWord.word.getString() + " at " + ocrWord.word.bounds + " (block relative extent is " + ocrWord.left + "-" + ocrWord.right + ")");
					OcrWord pOcrWord = ((ow == 0) ? null : ((OcrWord) apiPageBlock.blockLines[bl].ocrLine.words.get(ow - 1)));
					int minPageLeft = ((pOcrWord == null) ? 0 : (pOcrWord.right - owLeftGap));
					OcrWord nOcrWord = (((ow + 1) < apiPageBlock.blockLines[bl].ocrLine.words.size()) ? ((OcrWord) apiPageBlock.blockLines[bl].ocrLine.words.get(ow + 1)) : null);
					int maxPageRight = ((nOcrWord == null) ? apiPageBlock.blockLines[bl].regionColors.length : (nOcrWord.left - owLeftGap));
					if (DEBUG_ADJUST_OCR)
						System.out.println("       - expansion limits are " + minPageLeft + "-" + maxPageRight);
					for (boolean expanded = true; expanded;) {
						expanded = false;
						int pageLeft = (ocrWord.left - owLeftGap);
						int pageRight = (ocrWord.right - owLeftGap);
						int exPageLeft = pageLeft;
						int exPageRight = pageRight;
						for (Iterator regit = wordRegions[ow].iterator(); regit.hasNext();) {
							Integer reg = ((Integer) regit.next());
							if (apiPageBlock.blockLines[bl].regionRightNeighbors[reg.intValue()] == 0)
								continue; // no neighbor at all
							int nReg = apiPageBlock.blockLines[bl].regionRightNeighbors[reg.intValue()];
							if (lineRegionWords[nReg] != null)
								continue; // either we already cover this one, or some other word does
//							//	CANNOT DO THIS, as we might already be overlapping with region we have yet to claim in full (especially in italics)
//							if (wordRegions[ow].contains(nReg))
//								continue; // we already cover this one
							int nRegDist = apiPageBlock.blockLines[bl].regionRightNeighborDistances[reg.intValue()];
							if (DEBUG_ADJUST_OCR)
								System.out.println("       - checking (1) unassigned region " + nReg + " at distance " + nRegDist);
//							if (Math.abs(nRegDist - avgLineCrossWordRegionNeighborGap) < Math.abs(nRegDist - avgLineWordRegionNeighborGap)) {
//								if (DEBUG_ADJUST_OCR)
//									System.out.println("       ==> closer to cross-word gap " + avgLineCrossWordRegionNeighborGap + " than to in-word gap " + avgLineWordRegionNeighborGap);
//								continue; // closer to word gap than to in-word letter gap
//							}
							int farSideComparisonGap = ((nOcrWord == null) ? (apiPageBlock.blockLines[bl].getHeight() / 3) /* good upper bound for avearge space */ : avgLineCrossWordRegionNeighborGap);
							if (Math.abs(nRegDist - farSideComparisonGap) < Math.abs(nRegDist - avgLineWordRegionNeighborGap)) {
								if (DEBUG_ADJUST_OCR)
									System.out.println("       ==> closer to " + ((nOcrWord == null) ? "line height derived space" : "cross-word gap") + " " + farSideComparisonGap + " than to in-word gap " + avgLineWordRegionNeighborGap);
								continue; // closer to word gap than to in-word letter gap
							}
							if (apiPageBlock.blockLines[bl].regionRightNeighbors[nReg] == 0) { /* no neighbor on far side */ }
							else if (lineRegionWords[apiPageBlock.blockLines[bl].regionRightNeighbors[nReg]] == null) { /* far side neighbor unclaimed */ }
							else if (nRegDist < apiPageBlock.blockLines[bl].regionRightNeighborDistances[nReg]) { /* closer to us than far side neighbor */ }
							else {
								if (DEBUG_ADJUST_OCR)
									System.out.println("       ==> closer to claimed far-side neighbor " + lineRegionWords[apiPageBlock.blockLines[bl].regionRightNeighbors[nReg]] + " at distance " + apiPageBlock.blockLines[bl].regionRightNeighborDistances[nReg]);
								continue; // this one seems to belong to word further to right
							}
							
							//	claim region and include in bounds
							int pageRegLeft = apiPageBlock.blockLines[bl].regionMinCols[nReg];
							int pageRegRight = (apiPageBlock.blockLines[bl].regionMaxCols[nReg] + 1);
							if (pageRegLeft < exPageLeft) {
								exPageLeft = Math.max(pageRegLeft, minPageLeft); // avoid bumping into previous word
								expanded = (expanded || (exPageLeft < pageLeft)); // catch endless loop from claiming partially contested region time and again
							}
							if (exPageRight < pageRegRight) {
								exPageRight = Math.min(pageRegRight, maxPageRight); // avoid bumping into next word
								expanded = (expanded || (pageRight < exPageRight)); // catch endless loop from claiming partially contested region time and again
							}
							if (exPageRight <= pageRegLeft) {
								if (DEBUG_ADJUST_OCR)
									System.out.println("       ==> could not claim unassigned region " + nReg + " at " + pageRegLeft + "-" + pageRegRight + " due to next word");
								continue;
							}
							if (pageRegRight <= exPageLeft) {
								if (DEBUG_ADJUST_OCR)
									System.out.println("       ==> could not claim unassigned region " + nReg + " at " + pageRegLeft + "-" + pageRegRight + " due to previous word");
								continue;
							}
							lineRegionWords[nReg] = ocrWord;
							if (DEBUG_ADJUST_OCR) {
								System.out.println("       ==> claimed unassigned region " + nReg + " at " + pageRegLeft + "-" + pageRegRight);
								System.out.println("           block relative extent expanded to " + exPageLeft + "-" + exPageRight);
							}
						}
						if (expanded) {
							for (int c = exPageLeft; c < exPageRight; c++) {
								if (c == pageLeft) {
									c = (pageRight - 1); // jump over old extent (right boundary is exclusive, need to revisit)
									continue; // need to go around to have boundary condition checked, though
								}
								for (int r = 0; r < apiPageBlock.blockLines[bl].regionColors[c].length; r++) {
									int reg = apiPageBlock.blockLines[bl].regionColors[c][r];
									if (reg == 0)
										continue;
									wordRegions[ow].add(new Integer(reg));
								}
							}
							ocrWord.left = (exPageLeft + owLeftGap);
							ocrWord.right = (exPageRight + owLeftGap);
							if (DEBUG_ADJUST_OCR)
								System.out.println("       ==> block relative extent expanded to " + ocrWord.left + "-" + ocrWord.right);
						}
					}
				}
				
				//	attach unclaimed regions, iteratively relaxing scrutimy
				for (int round = 0; round < 2; round++) {
					
					//	get extent of line currently covered with words
					OcrWord fOcrWord = ((OcrWord) apiPageBlock.blockLines[bl].ocrLine.words.get(0));
					int firstWordPageLeft = (fOcrWord.left - owLeftGap);
					OcrWord lOcrWord = ((OcrWord) apiPageBlock.blockLines[bl].ocrLine.words.get(apiPageBlock.blockLines[bl].ocrLine.words.size() - 1));
					int lastWordPageRight = (lOcrWord.right - owLeftGap);
					
					//	rightwards assign any remaining unclaimed regions
					for (boolean expanded = true; expanded;) {
						expanded = false;
						for (int reg = 1; reg < apiPageBlock.blockLines[bl].regionSizes.length; reg++) {
							if (lineRegionWords[reg] != null)
								continue; // already assigned
							if (apiPageBlock.blockLines[bl].regionSizes[reg] == 0)
								continue; // attached as dot of i or j or accent
							if (apiPageBlock.blockLines[bl].regionRightNeighbors[reg] == 0)
								continue; // no neighbor at all
							if (DEBUG_ADJUST_OCR)
								System.out.println("       - checking (2) unassigned region " + reg);
							int nReg = apiPageBlock.blockLines[bl].regionRightNeighbors[reg];
							if (lineRegionWords[nReg] == null)
								continue; // neighbor not assigned, either
							int nRegDist = apiPageBlock.blockLines[bl].regionRightNeighborDistances[reg];
							if (DEBUG_ADJUST_OCR)
								System.out.println("       - checking rightward assignment to assigned region " + nReg + " at distance " + nRegDist);
//							if (Math.abs(nRegDist - avgLineCrossWordRegionNeighborGap) < Math.abs(nRegDist - avgLineWordRegionNeighborGap)) {
//								if (DEBUG_ADJUST_OCR)
//									System.out.println("       ==> closer to cross-word gap " + avgLineCrossWordRegionNeighborGap + " than to in-word gap " + avgLineWordRegionNeighborGap);
//								continue; // closer to word gap than to in-word letter gap
//							}
							if ((apiPageBlock.blockLines[bl].regionMaxCols[reg] <= firstWordPageLeft) && ((nRegDist * 2) < apiPageBlock.blockLines[bl].getHeight())) {
								if (DEBUG_ADJUST_OCR)
									System.out.println("       - accepting distance " + nRegDist + " at left end of line");
								//	simply catch this case, so to salvage regions left of leftmost word if less than regular space (half line height) away
							}
							else if ((round == 0) && (Math.abs(nRegDist - avgLineCrossWordRegionNeighborGap) < Math.abs(nRegDist - avgLineWordRegionNeighborGap))) {
								if (DEBUG_ADJUST_OCR)
									System.out.println("       ==> closer to cross-word gap " + avgLineCrossWordRegionNeighborGap + " than to in-word gap " + avgLineWordRegionNeighborGap);
								continue; // closer to word gap than to in-word letter gap
							}
							else if ((round != 0) && (avgLineCrossWordRegionNeighborGap < nRegDist)) {
								if (DEBUG_ADJUST_OCR)
									System.out.println("       ==> larger than cross-word gap " + avgLineCrossWordRegionNeighborGap + " than to in-word gap " + avgLineWordRegionNeighborGap);
								continue; // larger than word gap
							}
							
							//	get word we're assigning to
							OcrWord ocrWord;
							if (lineRegionWords[nReg] instanceof OcrWord)
								ocrWord = ((OcrWord) lineRegionWords[nReg]);
							else ocrWord = ((OcrWord) ((LinkedHashSet) lineRegionWords[nReg]).iterator().next()); // words are added left to right
							
							//	claim region and include in bounds
							lineRegionWords[reg] = ocrWord;
							if (DEBUG_ADJUST_OCR)
								System.out.println("       - rightward assigned to owner of claimed region: " + ocrWord.word.getString() + " at " + ocrWord.word.bounds + " (block relative extent is " + ocrWord.left + "-" + ocrWord.right + ")");
							int pageRegLeft = apiPageBlock.blockLines[bl].regionMinCols[reg];
							int pageLeft = (ocrWord.left - owLeftGap);
							int exPageLeft = pageLeft;
							if (pageRegLeft < exPageLeft) {
								exPageLeft = pageRegLeft;
								expanded = true;
							}
							int pageRegRight = (apiPageBlock.blockLines[bl].regionMaxCols[reg] + 1);
							int pageRight = (ocrWord.right - owLeftGap);
							int exPageRight = pageRight;
							if (exPageRight < pageRegRight) {
								exPageRight = pageRegRight;
								expanded = true;
							}
							if (expanded) {
								ocrWord.left = (exPageLeft + owLeftGap);
								ocrWord.right = (exPageRight + owLeftGap);
								if (DEBUG_ADJUST_OCR)
									System.out.println("       ==> block relative extent expanded to " + ocrWord.left + "-" + ocrWord.right);
							}
						}
					}
					
					//	leftward assign any remaining unclaimed regions
					for (boolean expanded = true; expanded;) {
						expanded = false;
						for (int reg = 1; reg < apiPageBlock.blockLines[bl].regionSizes.length; reg++) {
							if (lineRegionWords[reg] != null)
								continue; // already assigned
							if (apiPageBlock.blockLines[bl].regionSizes[reg] == 0)
								continue; // attached as dot of i or j or accent
							if (apiPageBlock.blockLines[bl].regionLeftNeighbors[reg] == 0)
								continue; // no neighbor at all
							if (DEBUG_ADJUST_OCR)
								System.out.println("       - checking (3) unassigned region " + reg);
							int nReg = apiPageBlock.blockLines[bl].regionLeftNeighbors[reg];
							if (lineRegionWords[nReg] == null)
								continue; // neighbor not assigned, either
							int nRegDist = apiPageBlock.blockLines[bl].regionLeftNeighborDistances[reg];
							if (DEBUG_ADJUST_OCR)
								System.out.println("       - checking leftward assignment to assigned region " + nReg + " at distance " + nRegDist);
//							if (Math.abs(nRegDist - avgLineCrossWordRegionNeighborGap) < Math.abs(nRegDist - avgLineWordRegionNeighborGap)) {
//								if (DEBUG_ADJUST_OCR)
//									System.out.println("       ==> closer to cross-word gap " + avgLineCrossWordRegionNeighborGap + " than to in-word gap " + avgLineWordRegionNeighborGap);
//								continue; // closer to word gap than to in-word letter gap
//							}
							if ((lastWordPageRight <= apiPageBlock.blockLines[bl].regionMinCols[reg]) && ((nRegDist * 2) < apiPageBlock.blockLines[bl].getHeight())) {
								if (DEBUG_ADJUST_OCR)
									System.out.println("       - accepting distance " + nRegDist + " at right end of line");
								//	simply catch this case, so to salvage regions right of rightmost word if less than regular space (half line height) away
							}
							else if ((round == 0) && (Math.abs(nRegDist - avgLineCrossWordRegionNeighborGap) < Math.abs(nRegDist - avgLineWordRegionNeighborGap))) {
								if (DEBUG_ADJUST_OCR)
									System.out.println("       ==> closer to cross-word gap " + avgLineCrossWordRegionNeighborGap + " than to in-word gap " + avgLineWordRegionNeighborGap);
								continue; // closer to word gap than to in-word letter gap
							}
							else if ((round != 0) && (avgLineCrossWordRegionNeighborGap < nRegDist)) {
								if (DEBUG_ADJUST_OCR)
									System.out.println("       ==> larger than cross-word gap " + avgLineCrossWordRegionNeighborGap + " than to in-word gap " + avgLineWordRegionNeighborGap);
								continue; // larger than word gap
							}
							
							//	get word we're assigning to
							OcrWord ocrWord = null;
							if (lineRegionWords[nReg] instanceof OcrWord)
								ocrWord = ((OcrWord) lineRegionWords[nReg]);
							else for (Iterator owit = ((LinkedHashSet) lineRegionWords[nReg]).iterator(); owit.hasNext();)
								ocrWord = ((OcrWord) owit.next()); // words are added left to right, need last one
							if (ocrWord == null)
								continue; // should not happen, but let's be safe
							
							//	claim region and include in bounds
							lineRegionWords[reg] = ocrWord;
							if (DEBUG_ADJUST_OCR)
								System.out.println("       - leftward assigned to owner of claimed region: " + ocrWord.word.getString() + " at " + ocrWord.word.bounds + " (block relative extent is " + ocrWord.left + "-" + ocrWord.right + ")");
							int pageRegLeft = apiPageBlock.blockLines[bl].regionMinCols[reg];
							int pageLeft = (ocrWord.left - owLeftGap);
							int exPageLeft = pageLeft;
							if (pageRegLeft < exPageLeft) {
								exPageLeft = pageRegLeft;
								expanded = true;
							}
							int pageRegRight = (apiPageBlock.blockLines[bl].regionMaxCols[reg] + 1);
							int pageRight = (ocrWord.right - owLeftGap);
							int exPageRight = pageRight;
							if (exPageRight < pageRegRight) {
								exPageRight = pageRegRight;
								expanded = true;
							}
							if (expanded) {
								ocrWord.left = (exPageLeft + owLeftGap);
								ocrWord.right = (exPageRight + owLeftGap);
								if (DEBUG_ADJUST_OCR)
									System.out.println("       ==> block relative extent expanded to " + ocrWord.left + "-" + ocrWord.right);
							}
						}
					}
					
					//	gather status of unassigned regions
					int unassignedRegCount = 0;
					if (DEBUG_ADJUST_OCR)
						System.out.println("Remaining unassigned regions (round " + round + ", gaps " + avgLineWordRegionNeighborGap + " and " + avgLineCrossWordRegionNeighborGap + "):");
					for (int reg = 1; reg < apiPageBlock.blockLines[bl].regionSizes.length; reg++) {
						if (lineRegionWords[reg] != null)
							continue; // already assigned
						if (apiPageBlock.blockLines[bl].regionSizes[reg] == 0)
							continue; // attached above
						unassignedRegCount++;
						if (DEBUG_ADJUST_OCR) {
							BoundingBox regBb = new BoundingBox(
									apiPageBlock.blockLines[bl].regionMinCols[reg],
									(apiPageBlock.blockLines[bl].regionMaxCols[reg] + 1),
									apiPageBlock.blockLines[bl].regionMinRows[reg],
									(apiPageBlock.blockLines[bl].regionMaxRows[reg] + 1)
								);
							System.out.println(" - " + reg + ", sized " + apiPageBlock.blockLines[bl].regionSizes[reg] + " at " + regBb);
							System.out.println("   left neighbor is " + apiPageBlock.blockLines[bl].regionLeftNeighbors[reg] + " at distance " + apiPageBlock.blockLines[bl].regionLeftNeighborDistances[reg]);
							System.out.println("   right neighbor is " + apiPageBlock.blockLines[bl].regionRightNeighbors[reg] + " at distance " + apiPageBlock.blockLines[bl].regionRightNeighborDistances[reg]);
						}
					}
					if (DEBUG_ADJUST_OCR)
						System.out.println(" ==> " + unassignedRegCount + " unassigned regions");
					
					//	anything left to assign at all?
					if (unassignedRegCount == 0)
						break;
					
					//	no need to go another round if contrast of gaps sufficiently clear (larger than half a millimeter)
					if ((avgLineCrossWordRegionNeighborGap - avgLineWordRegionNeighborGap) > (pageImage.currentDpi / 100))
						break;
				}
				
				//	re-assess which regions belong to which word (might be off after all the above adjustment)
				for (int ow = 0; ow < apiPageBlock.blockLines[bl].ocrLine.words.size(); ow++) {
					OcrWord ocrWord = ((OcrWord) apiPageBlock.blockLines[bl].ocrLine.words.get(ow));
					int pageLeft = Math.max(0, (ocrWord.left - owLeftGap));
					int pageRight = Math.min(apiPageBlock.blockLines[bl].regionColors.length, (ocrWord.right - owLeftGap));
					
					//	check which regions touch word area
					wordRegions[ow].clear();
					for (int c = pageLeft; c < pageRight; c++)
						for (int r = 0; r < apiPageBlock.blockLines[bl].regionColors[c].length; r++) {
							int reg = apiPageBlock.blockLines[bl].regionColors[c][r];
							if (reg != 0)
								wordRegions[ow].add(new Integer(reg));
						}
					if (DEBUG_ADJUST_OCR)
						System.out.println("     - regions in OCR word " + ocrWord.word.getString() + " at " + ocrWord.word.bounds + " are " + wordRegions[ow]);
				}
				
				//	move boundary between baseline punctuation and preceding word right if punctuation mark has x-height regions
				for (int ow = 1; ow < apiPageBlock.blockLines[bl].ocrLine.words.size(); ow++) {
					if (wordRegions[ow].isEmpty())
						continue; // nothing we can do about this one
					OcrWord ocrWord = ((OcrWord) apiPageBlock.blockLines[bl].ocrLine.words.get(ow));
					if ((ocrWord.word.getString() == null) || (ocrWord.word.getString().length() != 1) || (".,".indexOf(ocrWord.word.getString()) == -1))
						continue; // we're only after baseline punctuation
					if ((ocrWord.right - ocrWord.left) <= 4)
						continue; // too narrow anyway, leave untouched
					if (wordRegions[ow].elementCount() < 2)
						continue; // this one looks OK
					if (DEBUG_ADJUST_OCR)
						System.out.println("     - checking contents of baseline punctuation OCR word " + ocrWord.word.getString() + " at " + ocrWord.word.bounds + " (block relative extent is " + ocrWord.left + "-" + ocrWord.right + ")");
					OcrWord pOcrWord = ((OcrWord) apiPageBlock.blockLines[bl].ocrLine.words.get(ow - 1));
					int pOcrWordDist = (ocrWord.left - pOcrWord.right);
					if (DEBUG_ADJUST_OCR)
						System.out.println("       - preceding OCR word is " + pOcrWord.word.getString() + " at " + pOcrWord.word.bounds + " (block relative extent is " + pOcrWord.left + "-" + pOcrWord.right + ")");
					if (pOcrWordDist > 1) {
						if (DEBUG_ADJUST_OCR)
							System.out.println("       ==> too far away");
						continue; // no use doing any adjustment here
					}
					if (!Gamta.isWord(pOcrWord.word.getString()) && !Gamta.isNumber(pOcrWord.word.getString())) {
						if (DEBUG_ADJUST_OCR)
							System.out.println("       ==> not the required word or number");
						continue; // nothing to work against
					}
					OcrWord nOcrWord = (((ow + 1) < apiPageBlock.blockLines[bl].ocrLine.words.size()) ? ((OcrWord) apiPageBlock.blockLines[bl].ocrLine.words.get(ow + 1)) : null);
					int nOcrWordDist = ((nOcrWord == null) ? Integer.MAX_VALUE : (nOcrWord.left - ocrWord.right));
					if (nOcrWordDist < 2) {
						if (DEBUG_ADJUST_OCR)
							System.out.println("       ==> too close to subsequent OCR word " + nOcrWord.word.getString() + " at " + nOcrWord.word.bounds + " (block relative extent is " + nOcrWord.left + "-" + nOcrWord.right + ")");
						continue; // no use doing any adjustment here
					}
					boolean noContestedRegions = true;
					boolean noOwnedRegions = true;
					boolean noXHeightRegions = true;
					int maxRegLeft = 0;
					int maxBaselineRegLeft = -1;
					for (Iterator crit = wordRegions[ow].iterator(); crit.hasNext();) {
						int reg = ((Integer) crit.next()).intValue();
						if (lineRegionWords[reg] instanceof LinkedHashSet)
							noContestedRegions = false;
						else if (lineRegionWords[reg] == ocrWord)
							noOwnedRegions = false;
						maxRegLeft = Math.max(maxRegLeft, apiPageBlock.blockLines[bl].regionMinCols[reg]);
						int baselineDist = Math.abs(apiPageBlock.blockLines[bl].baseline - apiPageBlock.blockLines[bl].regionMinRows[reg]);
						int xHeightDist = Math.abs((apiPageBlock.blockLines[bl].baseline - apiPageBlock.blockLines[bl].xHeight) - apiPageBlock.blockLines[bl].regionMinRows[reg]);
						if (xHeightDist < baselineDist)
							noXHeightRegions = false;
						else maxBaselineRegLeft = Math.max(maxBaselineRegLeft, apiPageBlock.blockLines[bl].regionMinCols[reg]);
					}
					if (DEBUG_ADJUST_OCR)
						System.out.println("       - rightmost region of " + wordRegions[ow].elementCount() + " starts at " + maxRegLeft + ", rightmost baseline region at " + maxBaselineRegLeft);
					if (maxBaselineRegLeft == -1) {
						maxBaselineRegLeft = maxRegLeft;
						if (DEBUG_ADJUST_OCR)
							System.out.println("         ==> falling back to " + maxRegLeft);
					}
					if ((maxBaselineRegLeft + owLeftGap) < ocrWord.left) {
						if (DEBUG_ADJUST_OCR)
							System.out.println("       ==> no use shifting word boundary");
						continue;
					}
					if (noXHeightRegions) {
						if (DEBUG_ADJUST_OCR)
							System.out.println("       ==> no regions found that would be too high (closer to " + (apiPageBlock.blockLines[bl].baseline - apiPageBlock.blockLines[bl].xHeight) + " than " + apiPageBlock.blockLines[bl].baseline + ")");
						continue;
					}
					if (noContestedRegions) {
						if (DEBUG_ADJUST_OCR)
							System.out.println("       ==> no region conflicts found that would need resolving");
						continue;
					}
					if (noOwnedRegions) {
						if (DEBUG_ADJUST_OCR)
							System.out.println("       ==> no region found to collapse word to");
						continue;
					}
					ocrWord.left = Math.min((maxBaselineRegLeft + owLeftGap), (ocrWord.right - 4)); // ensure some minimum width
					pOcrWord.right = (ocrWord.left - pOcrWordDist);
					if (DEBUG_ADJUST_OCR) {
						System.out.println("       ==> adjusted word boundaries to block relative extent " + ocrWord.left + "-" + ocrWord.right);
						System.out.println("       ==> adjusted preceding word boundaries to block relative extent " + pOcrWord.left + "-" + pOcrWord.right);
					}
				}
				
				//	ensure each word is at least 4 pixels wide
				for (int ow = 0; ow < apiPageBlock.blockLines[bl].ocrLine.words.size(); ow++) {
					OcrWord ocrWord = ((OcrWord) apiPageBlock.blockLines[bl].ocrLine.words.get(ow));
					if ((ocrWord.right - ocrWord.left) >= 4)
						continue; // no need to take any action here
					if (DEBUG_ADJUST_OCR)
						System.out.println("     - ensuring horizontal extent of OCR word " + ocrWord.word.getString() + " at " + ocrWord.word.bounds + " (block relative extent is " + ocrWord.left + "-" + ocrWord.right + ")");
					OcrWord pOcrWord = ((ow == 0) ? null : ((OcrWord) apiPageBlock.blockLines[bl].ocrLine.words.get(ow - 1)));
					int minPageLeft = ((pOcrWord == null) ? 0 : (pOcrWord.right - owLeftGap));
					OcrWord nOcrWord = (((ow + 1) < apiPageBlock.blockLines[bl].ocrLine.words.size()) ? ((OcrWord) apiPageBlock.blockLines[bl].ocrLine.words.get(ow + 1)) : null);
					int maxPageRight = ((nOcrWord == null) ? apiPageBlock.blockLines[bl].regionColors.length : (nOcrWord.left - owLeftGap));
					if ((pOcrWord == null) && (nOcrWord == null))
						continue; // nothing to work with
					if (DEBUG_ADJUST_OCR) {
						if (pOcrWord != null)
							System.out.println("       - preceding OCR word is " + pOcrWord.word.getString() + " at " + pOcrWord.word.bounds + " (block relative extent is " + pOcrWord.left + "-" + pOcrWord.right + ")");
						if (nOcrWord != null)
							System.out.println("       - following OCR word is " + nOcrWord.word.getString() + " at " + nOcrWord.word.bounds + " (block relative extent is " + nOcrWord.left + "-" + nOcrWord.right + ")");
						System.out.println("       - expansion limits are " + minPageLeft + "-" + maxPageRight);
					}
					boolean expandLeft = ((pOcrWord != null) && ((pOcrWord.right - pOcrWord.left) > 4));
					boolean expandRight = ((nOcrWord != null) && ((nOcrWord.right - nOcrWord.left) > 4));
					while (((ocrWord.right - ocrWord.left) < 4) && (expandLeft || expandRight)) {
						if (expandLeft) {
							ocrWord.left--;
							if ((ocrWord.left - pOcrWord.right) < 0)
								pOcrWord.right--;
						}
						if (expandRight) {
							ocrWord.right++;
							if ((nOcrWord.left - ocrWord.right) < 0)
								nOcrWord.left++;
						}
					}
					if (DEBUG_ADJUST_OCR) {
						System.out.println("       ==> adjusted word boundaries to block relative extent " + ocrWord.left + "-" + ocrWord.right);
						if (pOcrWord != null)
							System.out.println("       ==> adjusted preceding word boundaries to block relative extent " + pOcrWord.left + "-" + pOcrWord.right);
						if (nOcrWord != null)
							System.out.println("       ==> adjusted following word boundaries to block relative extent " + nOcrWord.left + "-" + nOcrWord.right);
					}
				}
			}
		}
		
		//	adjust OCR words to line
		for (int bl = 0; bl < apiPageBlock.blockLines.length; bl++) {
			if (apiPageBlock.blockLines[bl].ocrLine == null)
				continue; // nothing to adjust here
			BoundingBox blBb = apiPageBlock.blockLines[bl].getPageBounds();
			if (DEBUG_ADJUST_OCR)
				System.out.println(" - line " + blBb/* + " with " + apiPageBlocks[b].blockLines[bl].getWordCount() + " words"*/);
			lineWords.clear();
			for (int ow = 0; ow < apiPageBlock.blockLines[bl].ocrLine.words.size(); ow++) {
				OcrWord ocrWord = ((OcrWord) apiPageBlock.blockLines[bl].ocrLine.words.get(ow));
				BoundingBox owBb = new BoundingBox(ocrWord.getPageLeft(), ocrWord.getPageRight(), blBb.top, blBb.bottom);
				
				//	no need to modify this one at all, only ensure baseline is correct
				if (ocrWord.word.bounds.equals(owBb)) {
					if (apiPageBlock.blockLines[bl].baseline != -1)
						ocrWord.word.setBaseline(apiPageBlock.blockLines[bl].getPageBaseline());
					doneWords.add(ocrWord.word);
					blockWords.add(ocrWord.word);
					blockWordsToLines.put(ocrWord.word, apiPageBlock.blockLines[bl]);
					paraWords.add(ocrWord.word);
					lineWords.add(ocrWord.word);
					if (DEBUG_ADJUST_OCR)
						System.out.println("     - retained OCR word " + ocrWord.word.getString() + " at " + ocrWord.word.bounds);
					continue;
				}
				
				//	create adjusted word
				ImWord newWord;
				if (ocrWord.word.getPage() == null)
					newWord = new ImWord(doc, ocrWord.word.pageId, owBb, ocrWord.word.getString());
				else newWord = new ImWord(ocrWord.word.getPage(), owBb, ocrWord.word.getString());
				if (ocrWord.word.hasAttribute(ImWord.BOLD_ATTRIBUTE))
					newWord.setAttribute(ImWord.BOLD_ATTRIBUTE);
				if (ocrWord.word.hasAttribute(ImWord.ITALICS_ATTRIBUTE))
					newWord.setAttribute(ImWord.ITALICS_ATTRIBUTE);
				if (ocrWord.word.hasAttribute(ImWord.FONT_NAME_ATTRIBUTE))
					newWord.setAttribute(ImWord.FONT_NAME_ATTRIBUTE, ocrWord.word.getAttribute(ImWord.FONT_NAME_ATTRIBUTE));
				if (ocrWord.word.getFontSize() != -1)
					newWord.setFontSize(ocrWord.word.getFontSize());
				if (apiPageBlock.blockLines[bl].baseline != -1)
					newWord.setBaseline(apiPageBlock.blockLines[bl].getPageBaseline());
				newWord.setTextStreamType(ocrWord.word.getTextStreamType());
				
				//	get surrounding words and relationship
	//			System.out.println("   - getting text stream neighbors");
				ImWord prev = ocrWord.word.getPreviousWord();
				ImWord next = ocrWord.word.getNextWord();
				
				//	switch annotations to replacement word
				ImAnnotation[] startingAnnots = doc.getAnnotations(ocrWord.word, null);
				for (int a = 0; a < startingAnnots.length; a++)
					startingAnnots[a].setFirstWord(newWord);
				ImAnnotation[] endingAnnots = doc.getAnnotations(null, ocrWord.word);
				for (int a = 0; a < endingAnnots.length; a++)
					endingAnnots[a].setLastWord(newWord);
				
				//	replace word in text stream
	//			System.out.println("   - integrating in text stream");
				if (prev != null)
					prev.setNextWord(newWord);
				if (next != null) {
					newWord.setNextRelation(ocrWord.word.getNextRelation()); // only makes sense if there is next word
					next.setPreviousWord(newWord);
				}
				
				//	remove replaced word
	//			System.out.println("   - cleaning up replaced words");
				if (ocrWord.word.getPage() != null)
					ocrWord.word.getPage().removeWord(ocrWord.word, false);
	//			System.out.println(" ==> done");
				
				//	store adjusted word
				doneWords.add(ocrWord.word);
				blockWords.add(newWord);
				blockWordsToLines.put(newWord, apiPageBlock.blockLines[bl]);
				paraWords.add(newWord);
				lineWords.add(newWord);
				if (DEBUG_ADJUST_OCR)
					System.out.println("     - moved OCR word " + ocrWord.word.getString() + " from " + ocrWord.word.bounds + " to " + owBb);
			}
			
			//	adjust line (if any)
			if (apiPageBlock.blockLines[bl].ocrLine.exLine != null) {
				ImWord[] lws = ((ImWord[]) lineWords.toArray(new ImWord[lineWords.size()]));
				BoundingBox lBb = ImLayoutObject.getAggregateBox(lws);
				if (!apiPageBlock.blockLines[bl].ocrLine.exLine.bounds.equals(lBb)) {
					ImRegion newLine = new ImRegion(page, lBb, ImRegion.LINE_ANNOTATION_TYPE);
					newLine.copyAttributes(apiPageBlock.blockLines[bl].ocrLine.exLine);
					if (apiPageBlock.blockLines[bl].baseline != -1)
						newLine.setAttribute(BASELINE_ATTRIBUTE, ("" + apiPageBlock.blockLines[bl].getPageBaseline()));
					page.removeRegion(apiPageBlock.blockLines[bl].ocrLine.exLine);
					if (DEBUG_ADJUST_OCR)
						System.out.println("     - moved line " + apiPageBlock.blockLines[bl].ocrLine.exLine.bounds + " to " + newLine.bounds);
				}
				else if (DEBUG_ADJUST_OCR)
					System.out.println("     - retained line " + apiPageBlock.blockLines[bl].ocrLine.exLine.bounds);
			}
			
			//	adjust ending paragraph (if any)
			if (apiPageBlock.blockLines[bl].ocrLine.endingExPara != null) {
				ImWord[] pws = ((ImWord[]) paraWords.toArray(new ImWord[paraWords.size()]));
				BoundingBox pBb = ImLayoutObject.getAggregateBox(pws);
				if (!apiPageBlock.blockLines[bl].ocrLine.endingExPara.bounds.equals(pBb)) {
					ImRegion newPara = new ImRegion(page, pBb, ImRegion.PARAGRAPH_TYPE);
					newPara.copyAttributes(apiPageBlock.blockLines[bl].ocrLine.endingExPara);
					page.removeRegion(apiPageBlock.blockLines[bl].ocrLine.endingExPara);
					if (DEBUG_ADJUST_OCR)
						System.out.println("     - moved paragraph " + apiPageBlock.blockLines[bl].ocrLine.endingExPara.bounds + " to " + newPara.bounds);
				}
				else if (DEBUG_ADJUST_OCR)
					System.out.println("     - retained paragraph " + apiPageBlock.blockLines[bl].ocrLine.endingExPara.bounds);
				paraWords.clear();
			}
		}
		
		//	merge scattered words, now that we have done all the rest
		CountingSet blockPunctRegionNeighborGaps = new CountingSet(new TreeMap());
		for (int round = 0; adjustWords; round++) {
			if (DEBUG_ADJUST_OCR)
				System.out.println("Checking for mergers of scattered words (round " + round + ")");
			if (DEBUG_ADJUST_OCR)
				System.out.println(" - block region distances within words are " + blockWordRegionNeighborGaps);
			int avgBlockWordRegionNeighborGap = getAverageMid60(blockWordRegionNeighborGaps, 0, Integer.MAX_VALUE);
			if (DEBUG_ADJUST_OCR)
				System.out.println("   ==> block average word region distance is " + avgBlockWordRegionNeighborGap);
			System.out.println(" - block region distances adjacent to punctuation markas are " + blockPunctRegionNeighborGaps);
			int avgBlockPunctRegionNeighborGap = getAverageMid60(blockPunctRegionNeighborGaps, 0, Integer.MAX_VALUE);
			if (DEBUG_ADJUST_OCR)
				System.out.println("   ==> block average region distance adjacent to punctuation marks is " + avgBlockPunctRegionNeighborGap);
			CountingSet blockCrossWordRegionNeighborGaps = new CountingSet(new TreeMap());
			blockCrossWordRegionNeighborGaps.addAll(blockRegionNeighborGaps);
			blockCrossWordRegionNeighborGaps.removeAll(blockWordRegionNeighborGaps);
			blockCrossWordRegionNeighborGaps.removeAll(blockPunctRegionNeighborGaps);
			if (DEBUG_ADJUST_OCR)
				System.out.println(" - block region distances across words are " + blockCrossWordRegionNeighborGaps);
			int avgBlockCrossWordRegionNeighborGap = getAverageMid60(blockCrossWordRegionNeighborGaps, 0, Integer.MAX_VALUE);
			if (DEBUG_ADJUST_OCR)
				System.out.println("   ==> block average cross word region distance is " + avgBlockCrossWordRegionNeighborGap);
			
			//	collect blocks of words are in same line and close together (block words are sorted left-right-top-down at this point)
			int sWordPos = -1;
			ArrayList wordBlocks = new ArrayList();
			for (int w = 1; w < blockWords.size(); w++) {
				ImWord lWord = ((ImWord) blockWords.get(w - 1));
				ImWord rWord = ((ImWord) blockWords.get(w));
				int hDist = (rWord.bounds.left - lWord.bounds.right);
				if (blockWordsToLines.get(lWord) != blockWordsToLines.get(rWord))
					hDist = (apiPageBlock.right - apiPageBlock.left);
				if (Math.abs((hDist - avgBlockWordRegionNeighborGap) * 2) < Math.abs(avgBlockCrossWordRegionNeighborGap - hDist)) /* close enough */ {
//				if (Math.abs((hDist - avgBlockWordRegionNeighborGap) * 3) < Math.abs(avgBlockCrossWordRegionNeighborGap - hDist)) /* close enough */ {
					if (sWordPos == -1) {
						sWordPos = (w - 1); // start new block if none open
						if (DEBUG_ADJUST_OCR)
							System.out.println(" - starting word block with '" + lWord.getString() + "'");
					}
					if (DEBUG_ADJUST_OCR)
						System.out.println(" - continuing word block with '" + rWord.getString() + "' at distance " + hDist);
				}
				else /* block interrupted (if any), stash to process below (vastly simplifies handling of loop index) */ {
					if (sWordPos != -1)  {
						ImWord[] wordBlock = new ImWord[w - sWordPos];
						for (int bw = sWordPos, wbi = 0; bw < w; bw++)
							wordBlock[wbi++] = ((ImWord) blockWords.get(bw));
						wordBlocks.add(wordBlock);
					}
					sWordPos = -1;
				}
			}
			
			//	stash last block (if any) to process below
			if (sWordPos != -1)  {
				ImWord[] wordBlock = new ImWord[blockWords.size() - sWordPos];
				for (int bw = sWordPos, wbi = 0; bw < blockWords.size(); bw++)
					wordBlock[wbi++] = ((ImWord) blockWords.get(bw));
				wordBlocks.add(wordBlock);
			}
			
			//	chop up word blocks if actual word distance above threshold
			CountingSet caughtCrossWordRegionDistances = new CountingSet(new TreeMap());
			for (int b = 0; b < wordBlocks.size(); b++) {
				ImWord[] wordBlock = ((ImWord[]) wordBlocks.get(b));
				BlockLine blockLine = ((BlockLine) blockWordsToLines.get(wordBlock[0]));
				if (DEBUG_ADJUST_OCR)
					System.out.println(" - assessing regions in block '" + ImUtils.getString(wordBlock, true, wordBlock[0].bounds.getHeight()) + "' of " + wordBlock.length + " words at " + ImLayoutObject.getAggregateBox(wordBlock));
				
				//	count out words per region ...
				//	... as well as average region distance inside words
				Object[] lineRegionWords = new Object[blockLine.regionSizes.length];
				CountingSet[] wordRegions = new CountingSet[wordBlock.length];
				for (int w = 0; w < wordBlock.length; w++) {
					int lineLeft = Math.max(0, (wordBlock[w].bounds.left - blockLine.getPageLeft()));
					int lineRight = Math.min(blockLine.regionColors.length, (wordBlock[w].bounds.right - blockLine.getPageLeft()));
					
					//	check which regions touch word area
					wordRegions[w] = new CountingSet();
					for (int c = lineLeft; c < lineRight; c++)
						for (int r = 0; r < blockLine.regionColors[c].length; r++) {
							int reg = blockLine.regionColors[c][r];
							if (reg == 0)
								continue;
							wordRegions[w].add(new Integer(reg));
							if (lineRegionWords[reg] == wordBlock[w])
								continue;
							if (lineRegionWords[reg] == null)
								lineRegionWords[reg] = wordBlock[w];
							else if (lineRegionWords[reg] instanceof LinkedHashSet)
								((LinkedHashSet) lineRegionWords[reg]).add(wordBlock[w]);
							else {
								LinkedHashSet lrws = new LinkedHashSet();
								lrws.add(lineRegionWords[reg]);
								lrws.add(wordBlock[w]);
								lineRegionWords[reg] = lrws;
							}
						}
					if (DEBUG_ADJUST_OCR)
						System.out.println("   - regions in word " + wordBlock[w].getString() + " at " + wordBlock[w].bounds + " are " + wordRegions[w]);
				}
				
				//	assess actual word distances
				for (int w = 1; w < wordBlock.length; w++) {
					ImWord lWord = wordBlock[w-1];
					ImWord rWord = wordBlock[w];
					if (DEBUG_ADJUST_OCR)
						System.out.println("   - rectangular distance between '" + lWord.getString() + "' and '" + rWord.getString() + "' is " + (rWord.bounds.left - lWord.bounds.right));
					if (wordRegions[w-1].isEmpty() || wordRegions[w].isEmpty())
						continue; // nothing to work with at all, better merge and resolve conflicts later
					int minRegionDistance = Integer.MAX_VALUE;
					for (Iterator rcit = wordRegions[w-1].iterator(); rcit.hasNext();) {
						Integer reg = ((Integer) rcit.next());
						if (wordRegions[w].contains(reg)) {
							minRegionDistance = 0;
							break; // shared (contested) region
						}
						Integer nReg = new Integer(blockLine.regionRightNeighbors[reg.intValue()]);
						if (!wordRegions[w].contains(nReg))
							continue;
						int nRegDist = blockLine.regionRightNeighborDistances[reg.intValue()];
						minRegionDistance = Math.min(minRegionDistance, nRegDist);
					}
					if (DEBUG_ADJUST_OCR)
						System.out.println("     region color distance is " + minRegionDistance);
					if (Math.abs((minRegionDistance - avgBlockWordRegionNeighborGap) * 2) < Math.abs(avgBlockCrossWordRegionNeighborGap - minRegionDistance)) /* still close enough */ {
//					if (Math.abs((minRegionDistance - avgBlockWordRegionNeighborGap) * 3) < Math.abs(avgBlockCrossWordRegionNeighborGap - minRegionDistance)) /* still close enough */ {
						if (DEBUG_ADJUST_OCR)
							System.out.println("     ==> looks OK for merge attempt");
						continue;
					}
					if (DEBUG_ADJUST_OCR)
						System.out.println("     ==> too far apart after compensating for kerning");
					caughtCrossWordRegionDistances.add(new Integer(minRegionDistance));
					if (wordBlock.length == 2) /* no leding or tailing blocks left */ {
						wordBlocks.remove(b-- /* need to compensate loop increment on removal */);
					}
					else if (w == 1) /* only chop off leading word */ {
						ImWord[] rWordBlock = new ImWord[wordBlock.length - w];
						System.arraycopy(wordBlock, w, rWordBlock, 0, rWordBlock.length);
						wordBlocks.set(b-- /* need to start over with tailing block */, rWordBlock);
					}
					else if ((w+1) == wordBlock.length) /* only chop off tailing word */ {
						ImWord[] lWordBlock = new ImWord[w];
						System.arraycopy(wordBlock, 0, lWordBlock, 0, lWordBlock.length);
						wordBlocks.set(b /* no need to check leading block again */, lWordBlock);
					}
					else /* chop blocks apart */ {
						ImWord[] lWordBlock = new ImWord[w];
						System.arraycopy(wordBlock, 0, lWordBlock, 0, lWordBlock.length);
						wordBlocks.set(b /* no need to check leading block again */, lWordBlock);
						ImWord[] rWordBlock = new ImWord[wordBlock.length - w];
						System.arraycopy(wordBlock, w, rWordBlock, 0, rWordBlock.length);
						wordBlocks.add((b + 1) /* we'll get to tailing block in next round of loop */, rWordBlock);
					}
					break;
				}
			}
			
			//	merge sequences of words that do not tokenize apart
			int blockWordCount = blockWords.size();
//			Tokenizer tokenizer = ((Tokenizer) doc.getAttribute(ImDocument.TOKENIZER_ATTRIBUTE, Gamta.INNER_PUNCTUATION_TOKENIZER));
			Tokenizer tokenizer = ((Tokenizer) doc.getAttribute(ImDocument.TOKENIZER_ATTRIBUTE, Gamta.getDefaultTokenizer()));
			for (int b = 0; b < wordBlocks.size(); b++) {
				ImWord[] wordBlock = ((ImWord[]) wordBlocks.get(b));
				
				//	concatenate words
				StringBuffer wordBlockStr = new StringBuffer();
				for (int w = 0; w < wordBlock.length; w++)
					wordBlockStr.append(wordBlock[w].getString());
				if (DEBUG_ADJUST_OCR)
					System.out.println(" - processing block of " + wordBlock.length + " words at " + ImLayoutObject.getAggregateBox(wordBlock) + ": " + wordBlockStr);
				
				//	index words per character
				ImWord[] wordAtChar = new ImWord[wordBlockStr.length()];
				for (int w = 0, bc = 0; w < wordBlock.length; w++) {
					String wordStr = wordBlock[w].getString();
					for (int wc = 0; wc < wordStr.length(); wc++)
						wordAtChar[bc++] = wordBlock[w];
				}
				
				//	tokenize word block, and merge words that are in same token
				TokenSequence wordBlockTokens = tokenizer.tokenize(wordBlockStr);
				for (int t = 0; t < wordBlockTokens.size(); t++) {
					Token token = wordBlockTokens.tokenAt(t);
					if (DEBUG_ADJUST_OCR)
						System.out.println("   - processing token '" + token.getValue() + "'");
					if (wordAtChar[token.getStartOffset()] == wordAtChar[token.getEndOffset() - 1]) {
						if (DEBUG_ADJUST_OCR)
							System.out.println("     ==> retained single word");
						continue; // already same word
					}
					
					//	count out font properties
					int boldCharCount = 0;
					int italicsCharCount = 0;
					CountingSet fontNames = new CountingSet();
					int fontSizeCount = 0;
					int fontSizeSum = 0;
					int baselineSum = 0;
					for (int c = token.getStartOffset(); c < token.getEndOffset(); c++) {
						if (wordAtChar[c].hasAttribute(ImWord.BOLD_ATTRIBUTE))
							boldCharCount++;
						if (wordAtChar[c].hasAttribute(ImWord.ITALICS_ATTRIBUTE))
							italicsCharCount++;
						if (wordAtChar[c].hasAttribute(ImWord.FONT_NAME_ATTRIBUTE))
							fontNames.add(wordAtChar[c].getAttribute(ImWord.FONT_NAME_ATTRIBUTE));
						if (wordAtChar[c].getFontSize() != -1) {
							fontSizeCount++;
							fontSizeSum += wordAtChar[c].getFontSize();
						}
						baselineSum += wordAtChar[c].getBaseline();
					}
					
					//	get border words
					ImWord fWord = wordAtChar[token.getStartOffset()];
					ImWord lWord = wordAtChar[token.getEndOffset() - 1];
					
					//	create adjusted word
					BoundingBox newWordBb = ImLayoutObject.getAggregateBox(wordAtChar, token.getStartOffset(), token.getEndOffset());
					ImWord newWord;
					if (fWord.getPage() == null)
						newWord = new ImWord(doc, fWord.pageId, newWordBb, token.getValue());
					else newWord = new ImWord(fWord.getPage(), newWordBb, token.getValue());
					if (token.length() < (boldCharCount * 2))
						newWord.setAttribute(ImWord.BOLD_ATTRIBUTE);
					if (token.length() < (italicsCharCount * 2))
						newWord.setAttribute(ImWord.ITALICS_ATTRIBUTE);
					Object fontName = fontNames.max();
					if (fontName != null)
						newWord.setAttribute(ImWord.FONT_NAME_ATTRIBUTE, fontName.toString());
					if (fontSizeCount != 0)
						newWord.setFontSize((fontSizeSum + (fontSizeCount / 2)) / fontSizeCount);
					newWord.setBaseline(baselineSum / token.length());
					newWord.setTextStreamType(fWord.getTextStreamType());
					
					//	get surrounding words and relationship
//					System.out.println("   - getting text stream neighbors");
					ImWord prev = fWord.getPreviousWord();
					ImWord next = lWord.getNextWord();
					
					//	switch annotations to replacement word
					for (int c = token.getStartOffset(); c < token.getEndOffset(); c++) {
						if ((c != 0) && (wordAtChar[c] == wordAtChar[c-1]))
							continue;
						ImAnnotation[] startingAnnots = doc.getAnnotations(wordAtChar[c], null);
						for (int a = 0; a < startingAnnots.length; a++)
							startingAnnots[a].setFirstWord(newWord);
						ImAnnotation[] endingAnnots = doc.getAnnotations(null, wordAtChar[c]);
						for (int a = 0; a < endingAnnots.length; a++)
							endingAnnots[a].setLastWord(newWord);
					}
					
					//	replace word in text stream
//					System.out.println("   - integrating in text stream");
					if (prev != null)
						prev.setNextWord(newWord);
					if (next != null) {
						newWord.setNextRelation(lWord.getNextRelation()); // only makes sense if there is next word
						next.setPreviousWord(newWord);
					}
					
					//	remove replaced word
//					System.out.println("   - cleaning up replaced words");
					int wordCount = 0;
					for (int c = token.getStartOffset(); c < token.getEndOffset(); c++) {
						if ((c != 0) && (wordAtChar[c] == wordAtChar[c-1]))
							continue;
						if (wordAtChar[c].getPage() != null)
							wordAtChar[c].getPage().removeWord(wordAtChar[c], false);
						wordCount++;
					}
//					System.out.println(" ==> done");
					if (DEBUG_ADJUST_OCR)
						System.out.println("     ==> merged " + wordCount + " words into " + newWordBb);
					
					//	replace words in list we're filling
					for (int bw = 0; bw < blockWords.size(); bw++) {
						ImWord word = ((ImWord) blockWords.get(bw));
						if (word != fWord)
							continue;
						blockWords.set(bw, newWord);
						blockWords.subList((bw + 1), (bw + wordCount)).clear();
						blockWordsToLines.put(newWord, blockWordsToLines.get(fWord));
					}
				}
			}
			if (DEBUG_ADJUST_OCR) {
				System.out.println(" ==> merged away " + (blockWordCount - blockWords.size()) + " of " + blockWordCount + " words in round " + round + " at distances " + avgBlockWordRegionNeighborGap + " and " + avgBlockCrossWordRegionNeighborGap);
				System.out.println("     region coloring stopped mergers at distances " + caughtCrossWordRegionDistances);
			}
			
			//	nothing merged at all, so no changes to expect (go aroud at least once, as observance of punctuation might make difference)
			if ((blockWords.size() == blockWordCount) && (round != 0))
				break;
			
			//	re-asses in-word region gaps and start over (above mergers might have sharpened contrast in blocks with many scattered words
			if (DEBUG_ADJUST_OCR)
				System.out.println(" - re-assessing in-word region gaps");
			blockWordRegionNeighborGaps.clear();
			blockPunctRegionNeighborGaps.clear();
			CountingSet[] blockWordRegions = new CountingSet[blockWords.size()];
			for (int w = 0; w < blockWords.size(); w++) {
				ImWord blockWord = ((ImWord) blockWords.get(w));
				BlockLine blockLine = ((BlockLine) blockWordsToLines.get(blockWord));
				int lineLeft = Math.max(0, (blockWord.bounds.left - blockLine.getPageLeft()));
				int lineRight = Math.min(blockLine.regionColors.length, (blockWord.bounds.right - blockLine.getPageLeft()));
				
				//	check which regions touch word area
				blockWordRegions[w] = new CountingSet();
				for (int c = lineLeft; c < lineRight; c++)
					for (int r = 0; r < blockLine.regionColors[c].length; r++) {
						int reg = blockLine.regionColors[c][r];
						if (reg == 0)
							continue;
						blockWordRegions[w].add(new Integer(reg));
					}
				if (DEBUG_ADJUST_OCR)
					System.out.println("   - regions in word " + blockWord.getString() + " at " + blockWord.bounds + " are " + blockWordRegions[w]);
			}
			
			//	collect gaps to neighboring regions in same word
			for (int w = 0; w < blockWords.size(); w++) {
				ImWord blockWord = ((ImWord) blockWords.get(w));
				BlockLine blockLine = ((BlockLine) blockWordsToLines.get(blockWord));
				boolean checkNextWord = ((w+1) < blockWords.size());
				ImWord nBlockWord = null;
				BlockLine nBlockLine = null;
				for (Iterator crit = blockWordRegions[w].iterator(); crit.hasNext();) {
					Integer reg = ((Integer) crit.next());
					Integer nReg = new Integer(blockLine.regionRightNeighbors[reg.intValue()]);
					int nRegDist = blockLine.regionRightNeighborDistances[reg.intValue()];
					if (blockWordRegions[w].contains(nReg)) {
						blockWordRegionNeighborGaps.add(new Integer(nRegDist));
						continue;
					}
					
					//	check if there _should_ be space before next word
					if ((nBlockWord == null) && checkNextWord) {
						nBlockWord = ((ImWord) blockWords.get(w+1));
						nBlockLine = ((BlockLine) blockWordsToLines.get(nBlockWord));
						if (nBlockLine != blockLine) {
							checkNextWord = false;
							nBlockWord = null;
							nBlockLine = null;
						}
					}
					if (nBlockWord == null)
						continue; // nothing to work with
					if (!blockWordRegions[w+1].contains(nReg))
						continue; // no dice with next word, either
					if ("({[".indexOf(blockWord.getString()) != -1)
						blockPunctRegionNeighborGaps.add(new Integer(nRegDist));
					else if (".,:;)]}".indexOf(nBlockWord.getString()) != -1)
						blockPunctRegionNeighborGaps.add(new Integer(nRegDist));
				}
			}
		}
		
		//	adjust existing block (if any)
		if ((apiPageBlock.ocrBlock.exBlocks != null) && (apiPageBlock.ocrBlock.exBlocks.length != 0)) {
			ImWord[] bws = ((ImWord[]) blockWords.toArray(new ImWord[blockWords.size()]));
			BoundingBox bBb = ImLayoutObject.getAggregateBox(bws);
			if ((apiPageBlock.ocrBlock.exBlocks.length != 1) || !apiPageBlock.ocrBlock.exBlocks[0].bounds.equals(bBb)) {
				ImRegion newBlock = new ImRegion(page, bBb, ImRegion.BLOCK_ANNOTATION_TYPE);
				for (int b = 0; b < apiPageBlock.ocrBlock.exBlocks.length; b++) {
					newBlock.copyAttributes(apiPageBlock.ocrBlock.exBlocks[b]);
					page.removeRegion(apiPageBlock.ocrBlock.exBlocks[b]);
				}
				if (DEBUG_ADJUST_OCR)
					System.out.println("     - moved/merged block " + apiPageBlock.ocrBlock.exBlocks[0].bounds + " to " + newBlock.bounds);
			}
			else if (DEBUG_ADJUST_OCR)
				System.out.println("     - retained block " + apiPageBlock.ocrBlock.exBlocks[0].bounds);
			paraWords.clear();
		}
		
		//	finally ...
		pageWords.addAll(blockWords);
	}
	
	private static Region[] getAtomicRegions(Region theRegion) {
		ArrayList regions = new ArrayList();
		addAtomicRegions(theRegion, regions);
		return ((Region[]) regions.toArray(new Region[regions.size()]));
	}
	private static void addAtomicRegions(Region theRegion, ArrayList regions) {
		if (theRegion.isAtomic() || theRegion.isImage())
			regions.add(theRegion);
		else for (int s = 0; s < theRegion.getSubRegionCount(); s++)
			addAtomicRegions(theRegion.getSubRegion(s), regions);
	}
	
	private static class PageBlock {
		final BlockLine[] blockLines;
		int left = Integer.MAX_VALUE;
		int right = Integer.MIN_VALUE;
		int top = Integer.MAX_VALUE;
		int bottom = Integer.MIN_VALUE;
		OcrBlock ocrBlock = null;
		PageBlock(BlockLine[] blockLines) {
			this.blockLines = blockLines;
			for (int l = 0; l < this.blockLines.length; l++) {
				this.left = Math.min(this.left, this.blockLines[l].getPageLeft());
				this.right = Math.max(this.right, this.blockLines[l].getPageRight());
				this.top = Math.min(this.top, this.blockLines[l].getPageTop());
				this.bottom = Math.max(this.bottom, this.blockLines[l].getPageBottom());
			}
		}
		BoundingBox getBounds() {
			return new BoundingBox(this.left, this.right, this.top, this.bottom);
		}
		int getWidth() {
			return (this.right - this.left);
		}
		int getHeight() {
			return (this.bottom - this.top);
		}
		Point getCenter() {
			return new Point(((this.left + this.right) / 2), ((this.top + this.bottom) / 2));
		}
		boolean contains(Point p) {
			if (p.x < this.left)
				return false;
			else if (this.right <= p.x)
				return false;
			else if (p.y < this.top)
				return false;
			else if (this.bottom <= p.y)
				return false;
			else return true;
		}
//		void addWords(OcrBlock ob) {
//			this.ocrWords.addAll(ob.words);
//		}
		void addOcrBlock(OcrBlock ob) {
			if (this.ocrBlock == null)
				this.ocrBlock = ob;
			else this.ocrBlock.include(ob);
		}
//		void include(PageBlock pb) {
//			if (pb == this)
//				return;
//			this.left = Math.min(this.left, pb.left);
//			this.right = Math.max(this.right, pb.right);
//			this.top = Math.min(this.top, pb.top);
//			this.bottom = Math.max(this.bottom, pb.bottom);
//		}
	}
	
	private static class BlockLine {
		final BoundingBox blockBounds;
		int color;
		int left; // relative to parent block
		int right; // relative to parent block
		int top; // relative to parent block
		final int oTop; // relative to parent block (need this as basis of region colors as we adjust top)
		int bottom; // relative to parent block
		//final int oBottom; // relative to parent block (need this as basis of region colors as we adjust bottom)
		int baseline = -1; // relative to own top
		int xHeight = -1;
		int capHeight = -1;
		int fontSize = -1;
		OcrLine ocrLine = null;
		BlockLine(BoundingBox blockBounds, int color, int left, int right, int top, int bottom) {
			this.blockBounds = blockBounds;
			this.color = color;
			this.left = left;
			this.right = right;
			this.top = top;
			this.oTop = top;
			this.bottom = bottom;
			//this.oBottom = bottom;
		}
		BoundingBox getPageBounds() {
			return new BoundingBox(this.getPageLeft(), this.getPageRight(), this.getPageTop(), this.getPageBottom());
		}
		int getPageLeft() {
			return (this.blockBounds.left + this.left);
		}
		int getPageRight() {
			return (this.blockBounds.left + this.right);
		}
		int getPageTop() {
			return (this.blockBounds.top + this.top);
		}
		int getPageBottom() {
			return (this.blockBounds.top + this.bottom);
		}
//		BoundingBox getBlockBounds() {
//			return new BoundingBox(this.getBlockLeft(), this.getBlockRight(), this.getBlockTop(), this.getBlockBottom());
//		}
		int getBlockLeft() {
			return this.left;
		}
//		int getBlockRight() {
//			return this.right;
//		}
		int getBlockTop() {
			return this.top;
		}
		int getBlockBottom() {
			return this.bottom;
		}
		int getWidth() {
			return (this.right - this.left);
		}
		int getHeight() {
			return (this.bottom - this.top);
		}
//		int getBlockBaseline() {
//			return (this.top + this.baseline);
//		}
		int getPageBaseline() {
			return (this.getPageTop() + this.baseline);
		}
//		int getCapHeight() {
//			return this.capHeight;
//		}
//		int getXHeight() {
//			return this.xHeight;
//		}
		
		int[][] regionColors = null;
		int[] regionSizes = null;
		int[] regionMinCols = null;
		int[] regionMaxCols = null;
		int[] regionMinRows = null;
		int[] regionMaxRows = null;
		int[] regionLeftNeighbors = null;
		int[] regionLeftNeighborDistances = null;
		int[] regionRightNeighbors = null;
		int[] regionRightNeighborDistances = null;
		void setRegions(AnalysisImage lineAi) {
			this.regionColors = Imaging.getRegionColoring(lineAi, ((byte) 112), true);
			int maxLineAiRegion = getMaxRegionColor(this.regionColors);
			this.regionSizes = new int[maxLineAiRegion + 1];
			this.regionMinCols = new int[maxLineAiRegion + 1];
			this.regionMaxCols = new int[maxLineAiRegion + 1];
			this.regionMinRows = new int[maxLineAiRegion + 1];
			this.regionMaxRows = new int[maxLineAiRegion + 1];
		}
	}
	
	private static class OcrBlock {
		int left;
		int right;
		int top;
		int bottom;
		ArrayList lines = new ArrayList();
		ArrayList words = new ArrayList();
		ImRegion[] exBlocks = null;
		OcrBlock(ImagePartRectangle bounds) {
			this.left = bounds.getLeftCol();
			this.right = bounds.getRightCol();
			this.top = bounds.getTopRow();
			this.bottom = bounds.getBottomRow();
		}
		OcrBlock(BoundingBox bounds) {
			this.left = bounds.left;
			this.right = bounds.right;
			this.top = bounds.top;
			this.bottom = bounds.bottom;
		}
		BoundingBox getBounds() {
			return new BoundingBox(this.left, this.right, this.top, this.bottom);
		}
		Point getCenter() {
			return new Point(((this.left + this.right) / 2), ((this.top + this.bottom) / 2));
		}
		boolean contains(Point p) {
			if (p.x < this.left)
				return false;
			else if (this.right <= p.x)
				return false;
			else if (p.y < this.top)
				return false;
			else if (this.bottom <= p.y)
				return false;
			else return true;
		}
		void include(OcrBlock ob) {
			if (ob == this)
				return;
			this.words.addAll(ob.words);
			this.left = Math.min(this.left, ob.left);
			this.right = Math.max(this.right, ob.right);
			this.top = Math.min(this.top, ob.top);
			this.bottom = Math.max(this.bottom, ob.bottom);
		}
		void shiftHorizontally(int by) {
			if (DEBUG_ADJUST_OCR)
				System.out.println(" - shifting horizontally by " + by + ", " + ((by * 100) / (this.right - this.left)) + "%");
			this.left += by;
			this.right += by;
		}
		void stretchHorizontally(int by) {
			if (DEBUG_ADJUST_OCR)
				System.out.println(" - stretching horizontally by " + by + ", " + ((by * 100) / (this.right - this.left)) + "%");
			for (int l = 0; l < this.lines.size(); l++) {
				OcrLine ol = ((OcrLine) this.lines.get(l));
				int lols = ((by * ol.left) / (this.right - this.left));
				ol.left += lols;
				int rols = ((by * ol.right) / (this.right - this.left));
				ol.right += rols;
				if (lols != rols)
					ol.stretchHorizontally(rols - lols);
			}
		}
		void shiftVertically(int by) {
			if (DEBUG_ADJUST_OCR)
				System.out.println(" - shifting vertically by " + by + ", " + ((by * 100) / (this.bottom - this.top)) + "%");
			this.top += by;
			this.bottom += by;
		}
		void stretchVertically(int by) {
			if (DEBUG_ADJUST_OCR)
				System.out.println(" - stretching vertically by " + by + ", " + ((by * 100) / (this.bottom - this.top)) + "%");
			for (int l = 0; l < this.lines.size(); l++) {
				OcrLine ol = ((OcrLine) this.lines.get(l));
				int olc = ((ol.top + ol.bottom) / 2);
				int ols = ((by * olc) / (this.bottom - this.top));
				ol.top += ols;
				ol.bottom += ols;
			}
			this.bottom += by;
		}
	}
	
	private static class OcrLine {
		OcrBlock block;
		int left = Integer.MAX_VALUE; // relative to parent block for easier whole-block bulk shift
		int right = Integer.MIN_VALUE; // relative to parent block for easier whole-block bulk shift
		int top = Integer.MAX_VALUE; // relative to parent block for easier whole-block bulk shift
		int bottom = Integer.MIN_VALUE; // relative to parent block for easier whole-block bulk shift
		ArrayList words = new ArrayList();
		ImRegion exLine = null;
		ImRegion endingExPara = null;
		OcrLine(OcrBlock block) {
			this.block = block;
			this.block.lines.add(this);
		}
		void addWord(ImWord word) {
//			this.left = Math.min(this.left, (word.bounds.left - this.block.left));
			int owLeft = (word.bounds.left - this.block.left);
			if (owLeft < this.left) {
				this.left = owLeft;
				for (int w = 0; w < this.words.size(); w++)
					((OcrWord) this.words.get(w)).lineExpandedLeft(); // if line expands left, we need to update line-relative word boundaries
			}
			this.right = Math.max(this.right, (word.bounds.right - this.block.left));
			this.top = Math.min(this.top, (word.bounds.top - this.block.top));
			this.bottom = Math.max(this.bottom, (word.bounds.bottom - this.block.top));
			this.words.add(new OcrWord(word, this));
		}
//		BoundingBox getBlockBounds() {
//			return new BoundingBox(this.left, this.right, this.top, this.bottom);
//		}
		BoundingBox getPageBounds() {
			return new BoundingBox((this.left + this.block.left), (this.right + this.block.left), (this.top + this.block.top), (this.bottom + this.block.top));
		}
		int getPageLeft() {
			return (this.left + this.block.left);
		}
		int getPageTop() {
			return (this.top + this.block.top);
		}
		void stretchHorizontally(int by) {
			for (int w = 0; w < this.words.size(); w++) {
				OcrWord ow = ((OcrWord) this.words.get(w));
				int lows = ((by * ow.left) / (this.right - this.left));
				ow.left += lows;
				int rows = ((by * ow.right) / (this.right - this.left));
				ow.right += rows;
			}
		}
	}
	private static class OcrWord implements Comparable {
		final ImWord word;
		OcrLine line;
		int left; // relative to parent line for easier whole-line bulk shift
		int right; // relative to parent line for easier whole-line bulk shift
		OcrWord(ImWord word, OcrLine line) {
			this.word = word;
			this.line = line;
			this.left = (this.word.bounds.left - this.line.left - this.line.block.left);
			this.right = (this.word.bounds.right - this.line.left - this.line.block.left);
		}
		void lineExpandedLeft() {
			this.left = (this.word.bounds.left - this.line.left - this.line.block.left);
			this.right = (this.word.bounds.right - this.line.left - this.line.block.left);
		}
//		BoundingBox getLineBounds() {
//			return new BoundingBox(this.left, this.right, 0, (this.line.bottom - this.line.top));
//		}
//		BoundingBox getBlockBounds() {
//			return new BoundingBox((this.left + this.line.left), (this.right + this.line.left), this.line.top, this.line.bottom);
//		}
		int getPageLeft() {
			return (this.left + this.line.left + this.line.block.left);
		}
		int getPageRight() {
			return (this.right + this.line.left + this.line.block.left);
		}
//		BoundingBox getPageBounds() {
//			return new BoundingBox((this.left + this.line.left + this.line.block.left), (this.right + this.line.left + this.line.block.left), (this.line.top + this.line.block.top), (this.line.bottom + this.line.block.top));
//		}
		public int compareTo(Object obj) {
			return ImUtils.leftRightOrder.compare(this.word, ((OcrWord) obj).word);
		}
	}
	
	private static final boolean DEBUG_BLOCK_ANALYSIS = false;
	private static BlockLine[] getBlockLines(AnalysisImage pageAi, int dpi, BoundingBox blockBounds, boolean separateLines, ProgressMonitor pm, BufferedImage vbi) {
		Graphics2D vbiGr = ((vbi == null) ? null : vbi.createGraphics());
		
		//	get region coloring of block
		AnalysisImage blockAi = Imaging.wrapImage(pageAi.getImage().getSubimage(blockBounds.left, blockBounds.top, blockBounds.getWidth(), blockBounds.getHeight()), null);
		if (DEBUG_BLOCK_ANALYSIS) {
			CountingSet aiPixelBrightnesses = new CountingSet(new TreeMap());
			byte[][] aiBrightness = blockAi.getBrightness();
			for (int c = 0; c < aiBrightness.length; c++) {
				for (int r = 0; r < aiBrightness[c].length; r++)
					aiPixelBrightnesses.add(new Byte(aiBrightness[c][r]));
			}
			System.out.println("Brightnesses are: " + aiPixelBrightnesses);
			//	TODO try this on background elimination: re-add subtracted background to all retained pixels to improve contrast
		}
		int[][] blockRegions = Imaging.getRegionColoring(blockAi, ((byte) 120), true);
		
		//	determine maximum region
		int maxBlockRegion = getMaxRegionColor(blockRegions);
		
		//	determine vertical extent of every region
		int[] blockRegionSizes = new int[maxBlockRegion + 1];
		int[] blockRegionMinCols = new int[maxBlockRegion + 1];
		int[] blockRegionMaxCols = new int[maxBlockRegion + 1];
		int[] blockRegionMinRows = new int[maxBlockRegion + 1];
		int[] blockRegionMaxRows = new int[maxBlockRegion + 1];
		measureRegions(blockRegions, blockRegionSizes, blockRegionMinCols, blockRegionMaxCols, blockRegionMinRows, blockRegionMaxRows);
//		
//		//	eliminate regions that are too small to even see (beware of periods and dots, though)
//		cleanRegions(blockRegions, pi.currentDpi, blockRegionSizes, blockRegionMinCols, blockRegionMaxCols, blockRegionMinRows, blockRegionMaxRows);
		
		//	gather statistics TODOne do we still need those ==> yes, if not all of them
		if (DEBUG_BLOCK_ANALYSIS) System.out.println("Region colors in " + blockBounds + ":");
		CountingSet blockRegionTopCounts = new CountingSet(new TreeMap());
		CountingSet blockRegionBottomCounts = new CountingSet(new TreeMap());
		CountingSet blockRegionHeightCounts = new CountingSet(new TreeMap());
		CountingSet blockRegionSizeCounts = new CountingSet(new TreeMap());
		for (int reg = 1; reg <= maxBlockRegion; reg++) {
			if (blockRegionSizes[reg] == 0)
				continue; // eliminated above
//			System.out.println(" - " + reg + ": " + blockRegionSizes[reg] + " pixels in " + blockRegionMinCols[reg] + "-" + blockRegionMaxCols[reg] + " x " + blockRegionMinRows[reg] + "-" + blockRegionMaxRows[reg]);
			blockRegionTopCounts.add(new Integer(blockRegionMinRows[reg]));
			blockRegionBottomCounts.add(new Integer(blockRegionMaxRows[reg]));
			blockRegionHeightCounts.add(new Integer(blockRegionMaxRows[reg] - blockRegionMinRows[reg] + 1));
			blockRegionSizeCounts.add(new Integer(blockRegionSizes[reg]));
			if (vbi != null) {
				int regRgb = Color.HSBtoRGB(((1.0f / 32) * (reg % 32)), 1.0f, 1.0f);
				for (int c = blockRegionMinCols[reg]; c <= blockRegionMaxCols[reg]; c++)
					for (int r = blockRegionMinRows[reg]; r <= blockRegionMaxRows[reg]; r++) {
						if (blockRegions[c][r] == reg)
							vbi.setRGB((blockBounds.left + c), (blockBounds.top + r), regRgb);
					}
			}
		}
		int avgBlockRegionHeight = getAverage(blockRegionHeightCounts, 0, Integer.MAX_VALUE); // should be about the x-height
		if (DEBUG_BLOCK_ANALYSIS) {
			System.out.println(" ==> tops are " + blockRegionTopCounts);
			System.out.println(" ==> bottoms are " + blockRegionBottomCounts);
			System.out.println(" ==> heights are " + blockRegionHeightCounts);
			System.out.println("     average height is " + avgBlockRegionHeight);
			System.out.println(" ==> sizes are " + blockRegionSizeCounts);
			System.out.println("     average size is " + getAverage(blockRegionSizeCounts, 0, Integer.MAX_VALUE));
		}
		
		/* eliminate altogether if totally out of proportion:
		 * - sans-serif lower case L is about 8 times as tall as wide, at worst
		 *   ==> eliminates vertical lines (page layout graphics, etc.) and letters mingled across lines
		 * - even conflated 40 letter word is about 20 times as wide as the line height
		 *   ==> eliminates gorizontal lines (page layout graphics, etc.) */
		for (int reg = 1; reg <= maxBlockRegion; reg++) {
			if (blockRegionSizes[reg] == 0)
				continue; // eliminated above
			int regHeight = (blockRegionMaxRows[reg] - blockRegionMinRows[reg] + 1);
			int regWidth = (blockRegionMaxCols[reg] - blockRegionMinCols[reg] + 1);
//			if ((regHeight * 1) < (regWidth * 10))
//				continue;
			if (((regHeight * 1) < (regWidth * 10)) && ((regWidth * 1) < (regHeight * 25)))
				continue;
			if (vbi != null) {
				int killRegRgb = Color.GRAY.getRGB();
				for (int c = blockRegionMinCols[reg]; c <= blockRegionMaxCols[reg]; c++)
					for (int r = blockRegionMinRows[reg]; r <= blockRegionMaxRows[reg]; r++) {
						if (blockRegions[c][r] == reg)
							vbi.setRGB((blockBounds.left + c), (blockBounds.top + r), killRegRgb);
					}
			}
			if (DEBUG_BLOCK_ANALYSIS) System.out.println(" - eliminating for aspect ratio " + reg + ": " + blockRegionSizes[reg] + " pixels in " + blockRegionMinCols[reg] + "-" + blockRegionMaxCols[reg] + " x " + blockRegionMinRows[reg] + "-" + blockRegionMaxRows[reg]);
			attachRegion(blockRegions, reg, 0, blockRegionSizes, blockRegionMinCols, blockRegionMaxCols, blockRegionMinRows, blockRegionMaxRows, blockBounds.left, blockBounds.top, vbi);
		}
		
		//	assimilate regions into (larger) ones completely enclosing them
		for (int reg = 1; reg <= maxBlockRegion; reg++) {
			if (blockRegionSizes[reg] == 0)
				continue; // eliminated above
			for (int aReg = 1; aReg <= maxBlockRegion; aReg++) {
				if (aReg == reg)
					continue;
				if (blockRegionSizes[aReg] == 0)
					continue; // eliminated above
				if (blockRegionMinCols[reg] < blockRegionMinCols[aReg])
					continue;
				if (blockRegionMaxCols[aReg] < blockRegionMaxCols[reg])
					continue;
				if (blockRegionMinRows[reg] < blockRegionMinRows[aReg])
					continue;
				if (blockRegionMaxRows[aReg] < blockRegionMaxRows[reg])
					continue;
				if (DEBUG_BLOCK_ANALYSIS) System.out.println(" - assimilating " + reg + " into " + aReg + ": " + blockRegionSizes[reg] + " pixels in " + blockRegionMinCols[reg] + "-" + blockRegionMaxCols[reg] + " x " + blockRegionMinRows[reg] + "-" + blockRegionMaxRows[reg] + " inside " + blockRegionMinCols[aReg] + "-" + blockRegionMaxCols[aReg] + " x " + blockRegionMinRows[aReg] + "-" + blockRegionMaxRows[aReg]);
				attachRegion(blockRegions, reg, aReg, blockRegionSizes, blockRegionMinCols, blockRegionMaxCols, blockRegionMinRows, blockRegionMaxRows, blockBounds.left, blockBounds.top, vbi);
				break;
			}
		}
		
		//	find pronounced upward outliers to detect line mingled letters, and eliminate such regions
		//	DO NOT eliminate in region coloring, though, as we need them later for expanding lines to include their descenders
		for (int reg = 1; reg <= maxBlockRegion; reg++) {
			if (blockRegionSizes[reg] == 0)
				continue; // eliminated above
			int regHeight = (blockRegionMaxRows[reg] - blockRegionMinRows[reg] + 1);
			if (separateLines) {
				if (regHeight < (avgBlockRegionHeight * 2) /* twice the x-height should be about the line height */)
					continue;
			}
			else {
				if (regHeight < (avgBlockRegionHeight * 5) /* account for variations in font size, etc., but still eliminate all too large regions */)
					continue;
			}
			if (vbi != null) {
				int killRegRgb = Color.GRAY.getRGB();
				for (int c = blockRegionMinCols[reg]; c <= blockRegionMaxCols[reg]; c++)
					for (int r = blockRegionMinRows[reg]; r <= blockRegionMaxRows[reg]; r++) {
						if (blockRegions[c][r] == reg)
//							vbi.setRGB((block.bounds.getLeftCol() + c), (block.bounds.getTopRow() + r), killRegRgb);
							vbi.setRGB((blockBounds.left + c), (blockBounds.top + r), killRegRgb);
					}
			}
			if (separateLines) {
				blockRegionSizes[reg] = 0;
//				blockRegionMinCols[reg] = block.bounds.getWidth();
//				blockRegionMaxCols[reg] = 0;
//				blockRegionMinRows[reg] = block.bounds.getHeight();
//				blockRegionMaxRows[reg] = 0;
			}
			else {
				if (DEBUG_BLOCK_ANALYSIS) System.out.println(" - eliminating for size " + reg + ": " + blockRegionSizes[reg] + " pixels in " + blockRegionMinCols[reg] + "-" + blockRegionMaxCols[reg] + " x " + blockRegionMinRows[reg] + "-" + blockRegionMaxRows[reg]);
				attachRegion(blockRegions, reg, 0, blockRegionSizes, blockRegionMinCols, blockRegionMaxCols, blockRegionMinRows, blockRegionMaxRows, blockBounds.left, blockBounds.top, vbi);
			}
		}
		
		//	amalgamate very small regions with larger regions right below them (attach accents to letters)
		attachSmallRegionsDownward(blockRegions, (dpi / 25), (dpi / 50), (dpi / 25), blockRegionSizes, blockRegionMinCols, blockRegionMaxCols, blockRegionMinRows, blockRegionMaxRows, blockBounds.left, blockBounds.top, vbi);
		
//		if (DEBUG_BLOCK_ANALYSIS) {
//			int regCount = 0;
//			for (int reg = 1; reg <= maxBlockRegion; reg++) {
//				if (blockRegionSizes[reg] != 0)
//					regCount++;
//			}
//			System.out.println(" - starting line assembly with " + regCount + " regions:");
//			for (int reg = 1; reg <= maxBlockRegion; reg++) {
//				if (blockRegionSizes[reg] == 0)
//					continue; // already attached
//				System.out.println("   - " + reg + " sized " + blockRegionSizes[reg] + " pixels in " + (blockBounds.left + blockRegionMinCols[reg]) + "-" + (blockBounds.left + blockRegionMaxCols[reg]) + " x " + (blockBounds.top + blockRegionMinRows[reg]) + "-" + (blockBounds.top + blockRegionMaxRows[reg]));
//			}
//		}
//		
		//	amalgamate every two regions if (a) one contains full vertical extent of other or (b) each contains center of vertical extent of other
		for (boolean changed = true; changed;) {
			changed = false;
			
			//	scan left to find adjacent regions
			for (int reg = 1; reg <= maxBlockRegion; reg++) {
				if (blockRegionSizes[reg] == 0)
					continue; // already attached
				int aReg = -1;
				for (int r = blockRegionMinRows[reg]; r <= blockRegionMaxRows[reg]; r++) {
					for (int c = (blockRegionMinCols[reg] - 1); c >= 0; c--) {
						if (blockRegions[c][r] == 0)
							continue;
						int lReg = blockRegions[c][r];
						if (lReg == reg)
							continue;
						if (blockRegionSizes[lReg] == 0)
							continue; // already attached
						boolean lRegContaining;
						boolean lRegContained;
						if ((blockRegionMinRows[reg] <= blockRegionMinRows[lReg]) && (blockRegionMaxRows[lReg] <= blockRegionMaxRows[reg])) {
							lRegContained = true;
							lRegContaining = false;
						}
						else if ((blockRegionMinRows[lReg] <= blockRegionMinRows[reg]) && (blockRegionMaxRows[reg] <= blockRegionMaxRows[lReg])) {
							lRegContained = false;
							lRegContaining = true;
						}
						else {
							lRegContained = false;
							lRegContaining = false;
						}
						
						//	attach vertically contained region to current one
						if (lRegContained) {
							attachRegion(blockRegions, lReg, reg, blockRegionSizes, blockRegionMinCols, blockRegionMaxCols, blockRegionMinRows, blockRegionMaxRows, blockBounds.left, blockBounds.top, vbi);
							changed = true;
						}
						
						//	attach current region to vertically containing one
						else if (lRegContaining) {
							aReg = lReg;
							break;
						}
					}
					if (aReg != -1)
						break;
				}
				if (aReg == -1)
					continue;
				attachRegion(blockRegions, reg, aReg, blockRegionSizes, blockRegionMinCols, blockRegionMaxCols, blockRegionMinRows, blockRegionMaxRows, blockBounds.left, blockBounds.top, vbi);
				changed = true;
			}
			
			//	scan right to find adjacent regions
			for (int reg = 1; reg <= maxBlockRegion; reg++) {
				if (blockRegionSizes[reg] == 0)
					continue; // already attached
				int aReg = -1;
				for (int r = blockRegionMinRows[reg]; r <= blockRegionMaxRows[reg]; r++) {
					for (int c = (blockRegionMaxCols[reg] + 1); c < blockRegions.length; c++) {
						if (blockRegions[c][r] == 0)
							continue;
						int lReg = blockRegions[c][r];
						if (lReg == reg)
							continue;
						if (blockRegionSizes[lReg] == 0)
							continue; // already attached
						boolean lRegContaining;
						boolean lRegContained;
						if ((blockRegionMinRows[reg] <= blockRegionMinRows[lReg]) && (blockRegionMaxRows[lReg] <= blockRegionMaxRows[reg])) {
							lRegContained = true;
							lRegContaining = false;
						}
						else if ((blockRegionMinRows[lReg] <= blockRegionMinRows[reg]) && (blockRegionMaxRows[reg] <= blockRegionMaxRows[lReg])) {
							lRegContained = false;
							lRegContaining = true;
						}
						else {
							lRegContained = false;
							lRegContaining = false;
						}
						
						//	attach vertically contained region to current one
						if (lRegContained) {
							attachRegion(blockRegions, lReg, reg, blockRegionSizes, blockRegionMinCols, blockRegionMaxCols, blockRegionMinRows, blockRegionMaxRows, blockBounds.left, blockBounds.top, vbi);
							changed = true;
						}
						
						//	attach current region to vertically containing one
						else if (lRegContaining) {
							aReg = lReg;
							break;
						}
					}
					if (aReg != -1)
						break;
				}
				if (aReg == -1)
					continue;
				attachRegion(blockRegions, reg, aReg, blockRegionSizes, blockRegionMinCols, blockRegionMaxCols, blockRegionMinRows, blockRegionMaxRows, blockBounds.left, blockBounds.top, vbi);
				changed = true;
			}
		}
		
		//	compute center of mass of every remaining region
		int[] blockRegionRowCenters = new int[maxBlockRegion + 1];
		for (int reg = 1; reg <= maxBlockRegion; reg++) {
			if (blockRegionSizes[reg] == 0)
				continue; // already attached
			long blockRegionRowSum = 0;
			for (int c = blockRegionMinCols[reg]; c <= blockRegionMaxCols[reg]; c++)
				for (int r = blockRegionMinRows[reg]; r <= blockRegionMaxRows[reg]; r++) {
					if (blockRegions[c][r] == reg)
						blockRegionRowSum += r;
				}
			blockRegionRowCenters[reg] = ((int) ((blockRegionRowSum + (blockRegionSizes[reg] / 2)) / blockRegionSizes[reg]));
		}
		
		//	order regions by increasing size
		long[] blockRegionSizesAndNumbers = new long[maxBlockRegion + 1];
		for (int reg = 1; reg <= maxBlockRegion; reg++) {
			if (blockRegionSizes[reg] == 0)
				blockRegionSizesAndNumbers[reg] = Long.MAX_VALUE;
			else blockRegionSizesAndNumbers[reg] = ((((long) blockRegionSizes[reg]) << 32) | reg);
		}
		Arrays.sort(blockRegionSizesAndNumbers);
		
		//	attach smaller regions to larger ones at same height as center of mass
		for (boolean changed = true; changed;) {
			changed = false;
			for (int sizeAndReg = 1; sizeAndReg <= maxBlockRegion; sizeAndReg++) {
				if (blockRegionSizesAndNumbers[sizeAndReg] == Long.MAX_VALUE)
					break; // already attached
				int reg = ((int) (blockRegionSizesAndNumbers[sizeAndReg] & 0x7FFFFFFF));
				if (blockRegionSizes[reg] == 0)
					continue; // already attached
				int aReg = -1;
				
				//	scan left to find adjacent regions
				if (aReg == -1)
					for (int c = (blockRegionMinCols[reg] - 1); c >= 0; c--) {
						if (blockRegions[c][blockRegionRowCenters[reg]] == 0)
							continue;
						int lReg = blockRegions[c][blockRegionRowCenters[reg]];
						if (lReg == reg)
							continue;
						if (blockRegionSizes[lReg] == 0)
							continue; // already attached
						if (blockRegionMaxRows[lReg] < blockRegionRowCenters[reg])
							continue; // bottom above center of mass
						if (blockRegionRowCenters[reg] < blockRegionMinRows[lReg])
							continue; // top below center of mass
						aReg = lReg;
						break;
					}
				
				//	scan right to find adjacent regions
				if (aReg == -1)
					for (int c = (blockRegionMaxCols[reg] + 1); c < blockRegions.length; c++) {
						if (blockRegions[c][blockRegionRowCenters[reg]] == 0)
							continue;
						int lReg = blockRegions[c][blockRegionRowCenters[reg]];
						if (lReg == reg)
							continue;
						if (blockRegionSizes[lReg] == 0)
							continue; // already attached
						if (blockRegionMaxRows[lReg] < blockRegionRowCenters[reg])
							continue; // bottom above center of mass
						if (blockRegionRowCenters[reg] < blockRegionMinRows[lReg])
							continue; // top below center of mass
						aReg = lReg;
						break;
					}
				if (aReg == -1)
					continue;
				attachRegion(blockRegions, reg, aReg, blockRegionSizes, blockRegionMinCols, blockRegionMaxCols, blockRegionMinRows, blockRegionMaxRows, blockBounds.left, blockBounds.top, vbi);
				changed = true;
			}
			
			//	re-compute center of mass
			if (changed)
				for (int reg = 1; reg <= maxBlockRegion; reg++) {
					if (blockRegionSizes[reg] == 0)
						continue; // already attached
					long blockRegionRowSum = 0;
					for (int c = blockRegionMinCols[reg]; c <= blockRegionMaxCols[reg]; c++)
						for (int r = blockRegionMinRows[reg]; r <= blockRegionMaxRows[reg]; r++) {
							if (blockRegions[c][r] == reg)
								blockRegionRowSum += r;
						}
					blockRegionRowCenters[reg] = ((int) ((blockRegionRowSum + (blockRegionSizes[reg] / 2)) / blockRegionSizes[reg]));
				}
		}
		
		//	amalgamate every small region with larger region right below (attach dots to Is and Js in all-lower-case lines without letters reaching cap height)
		//	no risk of erroneously attaching periods or commas any more, as those attach to letters on same line above
		attachSmallRegionsDownward(blockRegions, (dpi / 25), (dpi / 12), (dpi / 25), blockRegionSizes, blockRegionMinCols, blockRegionMaxCols, blockRegionMinRows, blockRegionMaxRows, blockBounds.left, blockBounds.top, vbi);
		
		//	amalgamate regions that overlap with over 50% of their respective heights
		for (boolean changed = true; changed;) {
			changed = false;
			for (int reg = 1; reg <= maxBlockRegion; reg++) {
				if (blockRegionSizes[reg] == 0)
					continue; // attached above
				int regHeight = (blockRegionMaxRows[reg] - blockRegionMinRows[reg]);
				int aReg = -1;
				for (int lReg = (reg+1); lReg <= maxBlockRegion; lReg++) {
					if (blockRegionSizes[lReg] == 0)
						continue; // attached above
					if (blockRegionMaxRows[lReg] <= blockRegionMinRows[reg])
						continue; // above current region
					if (blockRegionMaxRows[reg] <= blockRegionMinRows[lReg])
						continue; // below current region
					int lRegHeight = (blockRegionMaxRows[lReg] - blockRegionMinRows[lReg]);
					int overlapHeight = (Math.min(blockRegionMaxRows[reg], blockRegionMaxRows[lReg]) - Math.max(blockRegionMinRows[reg], blockRegionMinRows[lReg]));
					if (DEBUG_BLOCK_ANALYSIS) System.out.println("Got regions of heights " + regHeight + " and " + lRegHeight + " overlapping by " + overlapHeight);
					if ((overlapHeight * 2) < regHeight)
						continue;
					if ((overlapHeight * 2) < lRegHeight)
						continue;
					aReg = lReg;
					break;
				}
				if (aReg == -1)
					continue;
				attachRegion(blockRegions, reg, aReg, blockRegionSizes, blockRegionMinCols, blockRegionMaxCols, blockRegionMinRows, blockRegionMaxRows, blockBounds.left, blockBounds.top, vbi);
				changed = true;
			}
		}
		
		//	materialize detected lines, and sort them top-down
		ArrayList blockLineList = new ArrayList();
		for (int reg = 1; reg <= maxBlockRegion; reg++) {
			if (blockRegionSizes[reg] == 0)
				continue; // attached above
			int regHeight = (blockRegionMaxRows[reg] - blockRegionMinRows[reg]);
			if (regHeight < (dpi / 25))
				continue; // less than x-height letters in font size 6 (1 millimeters) tall (horizontal separator or table grid line, etc.)
			if (regHeight > dpi)
				continue; // more than full-height letters in font size 72 (25 millimeters) tall (illustration, or vertical separator or table grid line, etc.)
			int regWidth = (blockRegionMaxCols[reg] - blockRegionMinCols[reg]);
			if (regWidth < (dpi / 50))
				continue; // less than half a millimeter wide (vertical separator or table grid line, etc.)
			BlockLine blockLine = new BlockLine(blockBounds, reg, 0, blockBounds.getWidth(), blockRegionMinRows[reg], (blockRegionMaxRows[reg] + 1));
			blockLineList.add(blockLine);
		}
		BlockLine[] blockLines = ((BlockLine[]) blockLineList.toArray(new BlockLine[blockLineList.size()]));
		Arrays.sort(blockLines, new Comparator() {
			public int compare(Object obj1, Object obj2) {
				BlockLine bl1 = ((BlockLine) obj1);
				BlockLine bl2 = ((BlockLine) obj2);
				return (bl1.top - bl2.top);
			}
		});
		
		//	for each ignored block region, compute how many lines they overlap with
		HashSet[] blockRegionLines = new HashSet[maxBlockRegion + 1];
		for (int l = 0; l < blockLines.length; l++) {
			BlockLine blockLine = blockLines[l];
			for (int c = blockLine.left; c < blockLine.right; c++)
				for (int r = blockLine.top; r < blockLine.bottom; r++) {
					int reg = blockRegions[c][r];
					if (reg == 0)
						continue;
					if (blockRegionLines[reg] == null)
						blockRegionLines[reg] = new HashSet();
					blockRegionLines[reg].add(blockLine);
				}
		}
		
		//	expand lines upwards and downwards to fully cover partially included regions (ignoring ones contested between lines)
		for (int l = 0; l < blockLines.length; l++) {
			BlockLine regLine = blockLines[l];
			if (DEBUG_BLOCK_ANALYSIS) System.out.println(" - expanding line at " + regLine.getPageBounds());
			int prevRegLineBottom = ((l == 0) ? 0 : blockLines[l-1].bottom);
			int nextRegLineTop = (((l + 1) == blockLines.length) ? blockRegions[0].length : blockLines[l+1].top);
			while (regLine.top > prevRegLineBottom) {
				int rowRegCount = 0;
				for (int c = regLine.left; c < regLine.right; c++) {
					int reg = blockRegions[c][regLine.top - 1];
					if (reg == 0)
						continue;
					if (blockRegionLines[reg] == null)
						continue;
					if (blockRegionLines[reg].size() == 1) {}
					else if ((blockRegionMinRows[reg] < ((regLine.top + regLine.bottom + regLine.bottom) / 3)) && (blockRegionMaxRows[reg] > ((regLine.top + regLine.top + regLine.bottom) / 3))) {}
					else continue; // accept region anyway if center third of line
					rowRegCount++;
				}
				if (rowRegCount == 0)
					break;
				regLine.top--;
			}
			while (regLine.bottom < nextRegLineTop) {
				int rowRegCount = 0;
				for (int c = regLine.left; c < regLine.right; c++) {
					int reg = blockRegions[c][regLine.bottom];
					if (reg == 0)
						continue;
					if (blockRegionLines[reg] == null)
						continue;
					if (blockRegionLines[reg].size() == 1) {}
					else if ((blockRegionMinRows[reg] < ((regLine.top + regLine.bottom + regLine.bottom) / 3)) && (blockRegionMaxRows[reg] > ((regLine.top + regLine.top + regLine.bottom) / 3))) {}
					else continue; // accept region anyway if overlapping center third of line
					rowRegCount++;
				}
				if (rowRegCount == 0)
					break;
				regLine.bottom++;
			}
			if (DEBUG_BLOCK_ANALYSIS) System.out.println("   ==> expanded line to " + regLine.getPageBounds());
		}
		
		//	shrink line left and right boundaries
		for (int l = 0; l < blockLines.length; l++) {
			BlockLine regLine = blockLines[l];
			while (regLine.left < regLine.right) {
				int colRegCount = 0;
				for (int r = regLine.top; r < regLine.bottom; r++) {
					int reg = blockRegions[regLine.left][r];
					if (reg == 0)
						continue;
					if (blockRegionLines[reg] == null)
						continue;
					if (blockRegionLines[reg].size() == 1) {}
					else if ((blockRegionMinRows[reg] < ((regLine.top + regLine.bottom) / 2)) && (blockRegionMaxRows[reg] > ((regLine.top + regLine.bottom) / 2))) {}
					else continue; // TODOne accept region anyway if overlapping line center
					colRegCount++;
				}
				if (colRegCount == 0)
					regLine.left++;
				else break;
			}
			while (regLine.left < regLine.right) {
				int colRegCount = 0;
				for (int r = regLine.top; r < regLine.bottom; r++) {
					int reg = blockRegions[regLine.right - 1][r];
					if (reg == 0)
						continue;
					if (blockRegionLines[reg] == null)
						continue;
					if (blockRegionLines[reg].size() == 1) {}
					else if ((blockRegionMinRows[reg] < ((regLine.top + regLine.bottom) / 2)) && (blockRegionMaxRows[reg] > ((regLine.top + regLine.bottom) / 2))) {}
					else continue; // TODOne accept region anyway if overlapping line center
					colRegCount++;
				}
				if (colRegCount == 0)
					regLine.right--;
				else break;
			}
			if (vbiGr != null) {
				int regRgb = Color.HSBtoRGB(((1.0f / 32) * (regLine.color % 32)), 1.0f, 1.0f);
				vbiGr.setColor(new Color(regRgb));
				vbiGr.drawRect(regLine.getPageLeft(), regLine.getPageTop(), regLine.getWidth(), regLine.getHeight());
			}
		}
		
		//	find accumulation points in region height to get average letter heights
		//	find accumulation points in region bottoms to determine baseline
		int[][] lineRegionSizeReserves = new int[blockLines.length][];
		for (int l = 0; l < blockLines.length; l++) {
			
			//	get region coloring of line (eliminate protrusions from adjacent lines beforehand)
			if (DEBUG_BLOCK_ANALYSIS) System.out.println(" - doing " + blockLines[l].getPageBounds() + " with color " + blockLines[l].color + ":");
			AnalysisImage lineAi = Imaging.wrapImage(pageAi.getImage().getSubimage(blockLines[l].getPageLeft(), blockLines[l].getPageTop(), blockLines[l].getWidth(), blockLines[l].getHeight()), null);
			byte[][] lineAiBrightness = lineAi.getBrightness();
			int adjacentLinePixelKillCount = 0;
			for (int c = 0; c < lineAiBrightness.length; c++) {
				int bc = (c + blockLines[l].getBlockLeft());
				for (int r = 0; r < lineAiBrightness[c].length; r++) {
					int br = (r + blockLines[l].getBlockTop());
					if (blockRegions[bc][br] == 0)
						continue;
					if (blockRegionSizes[blockRegions[bc][br]] == 0)
						continue;
					if (blockRegions[bc][br] == blockLines[l].color)
						continue;
//					System.out.println(" - eliminating pixel " + c + "/" + r + " (" + (c + blockLines[l].getPageLeft()) + "/" + (r + blockLines[l].getPageTop()) + ") for being colored " + blockRegions[c][br]);
					lineAiBrightness[c][r] = ((byte) 127);
					adjacentLinePixelKillCount++;
				}
			}
			if (DEBUG_BLOCK_ANALYSIS && (adjacentLinePixelKillCount != 0))
				System.out.println(" - eliminated " + adjacentLinePixelKillCount + " pixels from adjacent lines");
			blockLines[l].setRegions(lineAi);
			
			//	measure region coloring of line
			measureRegions(blockLines[l].regionColors, blockLines[l].regionSizes, blockLines[l].regionMinCols, blockLines[l].regionMaxCols, blockLines[l].regionMinRows, blockLines[l].regionMaxRows);
//			
//			//	eliminate regions that are too small to even see (beware of periods and dots, though)
//			cleanRegions(blockLines[l].regionColors, dpi, blockLines[l].regionSizes, blockLines[l].regionMinCols, blockLines[l].regionMaxCols, blockLines[l].regionMinRows, blockLines[l].regionMaxRows);
			
			//	eliminate regions whose top is in bottom 20% of line
			//	DO NOT eliminate dimensions just yet, though, as without a descender, we'd eliminate periods, and we need those for word detection
			lineRegionSizeReserves[l] = new int[blockLines[l].regionSizes.length];
			Arrays.fill(lineRegionSizeReserves[l], 0);
			for (int reg = 1; reg < blockLines[l].regionSizes.length; reg++) {
				if ((blockLines[l].regionMinRows[reg] * 5) < (blockLines[l].regionColors[0].length * 4))
					continue;
				lineRegionSizeReserves[l][reg] = blockLines[l].regionSizes[reg];
				blockLines[l].regionSizes[reg] = 0;
			}
			
			//	attach small regions (dots, accents) downward
			attachSmallRegionsDownward(blockLines[l].regionColors, (dpi / 12), (dpi / 6), 0, blockLines[l].regionSizes, blockLines[l].regionMinCols, blockLines[l].regionMaxCols, blockLines[l].regionMinRows, blockLines[l].regionMaxRows, -1, -1, null);
			
			//	gather statistics
			if (DEBUG_BLOCK_ANALYSIS) System.out.println(" - region colors in " + blockLines[l].getPageBounds() + ":");
			CountingSet lineRegionTopCounts = new CountingSet(new TreeMap());
			CountingSet lineRegionBottomCounts = new CountingSet(new TreeMap());
			CountingSet lineRegionSizeCounts = new CountingSet(new TreeMap());
			CountingSet lineRegionHeightCounts = new CountingSet(new TreeMap());
			for (int reg = 1; reg < blockLines[l].regionSizes.length; reg++) {
				if (blockLines[l].regionSizes[reg] == 0)
					continue; // attached above
				//System.out.println(" - " + reg + ": " + blockLines[l].lineRegionSizes[reg] + " pixels in " + blockLines[l].lineRegionMinCols[reg] + "-" + blockLines[l].lineRegionMaxCols[reg] + " x " + blockLines[l].lineRegionMinRows[reg] + "-" + blockLines[l].lineRegionMaxRows[reg]);
				lineRegionTopCounts.add(new Integer(blockLines[l].regionMinRows[reg]));
				lineRegionBottomCounts.add(new Integer(blockLines[l].regionMaxRows[reg]));
				lineRegionSizeCounts.add(new Integer(blockLines[l].regionSizes[reg]));
				lineRegionHeightCounts.add(new Integer(blockLines[l].regionMaxRows[reg] - blockLines[l].regionMinRows[reg] + 1));
			}
			int lineRegionAboveCenterBottomCount = countElementsUpTo(lineRegionBottomCounts, (blockLines[l].getHeight() / 2));
			int lineRegionBelowCenterBottomCount = (lineRegionBottomCounts.size() - lineRegionAboveCenterBottomCount);
			int avgLineRegionBottom = getAverageMid60(lineRegionBottomCounts, (blockLines[l].getHeight() / 2), Integer.MAX_VALUE);
			blockLines[l].baseline = avgLineRegionBottom;
			if (DEBUG_BLOCK_ANALYSIS) {
				System.out.println("   ==> tops are " + lineRegionTopCounts);
				System.out.println("   ==> bottoms are " + lineRegionBottomCounts);
				System.out.println("       above line center bottoms are " + lineRegionAboveCenterBottomCount);
				System.out.println("       below line center bottoms are " + lineRegionBelowCenterBottomCount);
//				int avgLineRegionBottom;
//				if (lineRegionBelowCenterBottomCount < lineRegionAboveCenterBottomCount) {
//					int maxAboveCenterBottom = this.getMaxUpTo(lineRegionBottomCounts, (blockLines[l].getHeight() / 2));
//					avgLineRegionBottom = this.getAverageMid60(lineRegionBottomCounts, (maxAboveCenterBottom / 2), (blockLines[l].getHeight() / 2));
//				}
//				else avgLineRegionBottom = this.getAverageMid60(lineRegionBottomCounts, (blockLines[l].getHeight() / 2), Integer.MAX_VALUE);
				//	ignore region bottoms above line center (works even if line has no ascenders, as x-height is at least 40% in but any font)
				System.out.println("       mid 60% average bottom is " + avgLineRegionBottom);
				//	==> looks darn good for baseline detection !!!
			}
			if (vbiGr != null) {
				vbiGr.setColor(Color.RED);
				vbiGr.drawLine(blockLines[l].getPageLeft(), (blockLines[l].getPageTop() + avgLineRegionBottom), blockLines[l].getPageRight(), (blockLines[l].getPageTop() + avgLineRegionBottom));
			}
			
			int avgLineRegionHeight = getAverage(lineRegionHeightCounts, (blockLines[l].getHeight() / 4) /* x-height below 25% is very unlikely */, Integer.MAX_VALUE);
			int avgLineRegionHeightBelowAvg = getAverage(lineRegionHeightCounts, (blockLines[l].getHeight() / 4) /* x-height below 25% is very unlikely */, avgLineRegionHeight);
			int avgLineRegionHeightAboveAvg = getAverage(lineRegionHeightCounts, (avgLineRegionHeight + 1), Integer.MAX_VALUE);
			if (DEBUG_BLOCK_ANALYSIS) {
				System.out.println("   ==> sizes are " + lineRegionSizeCounts);
				System.out.println("       average size is " + getAverage(lineRegionSizeCounts, 0, Integer.MAX_VALUE));
				System.out.println("   ==> heights are " + lineRegionHeightCounts);
				System.out.println("       average height " + (blockLines[l].getHeight() / 4) + " and above is " + avgLineRegionHeight);
				System.out.println("       average height between " + (blockLines[l].getHeight() / 4) + " and " + avgLineRegionHeight + " is " + avgLineRegionHeightBelowAvg);
				System.out.println("       average height " + (avgLineRegionHeight + 1) + " and above is " + avgLineRegionHeightAboveAvg);
			}
//			//	ignore x-height if above 67% of cap height (likely line with very few glyphs without ascender)
//			//	==> RATIO CAN BE EVEN HIGHER IN SMALL FONT SIZES, BETTER OFF WITH 75%
//			if ((avgLineRegionBottom * 2) < (avgLineRegionHeightBelowAvg * 3)) {
			//	ignore x-height if above 75% of cap height (likely line with very few glyphs without ascender)
			if ((avgLineRegionBottom * 3) < (avgLineRegionHeightBelowAvg * 4)) {
				avgLineRegionHeightBelowAvg = -1;
				avgLineRegionHeightAboveAvg = avgLineRegionHeight;
				if (DEBUG_BLOCK_ANALYSIS) System.out.println("       ==> average below average height too close to average region bottom, most likely all caps");
			}
			//	ignore x-height if above 75% of line height (likely line with very few glyphs without ascender)
			if ((avgLineRegionHeightAboveAvg * 3) < (avgLineRegionHeightBelowAvg * 4)) {
				avgLineRegionHeightBelowAvg = -1;
				avgLineRegionHeightAboveAvg = avgLineRegionHeight;
				if (DEBUG_BLOCK_ANALYSIS) System.out.println("       ==> average below average height too close to average above average height, most likely all caps");
			}
			if (avgLineRegionHeightBelowAvg != -1) {
				blockLines[l].xHeight = avgLineRegionHeightBelowAvg;
				if (vbiGr != null) {
					vbiGr.setColor(Color.GREEN);
					vbiGr.drawLine(blockLines[l].getPageLeft(), (blockLines[l].getPageTop() + avgLineRegionBottom - avgLineRegionHeightBelowAvg), blockLines[l].getPageRight(), (blockLines[l].getPageTop() + avgLineRegionBottom - avgLineRegionHeightBelowAvg));
				}
			}
			//	cap off cap height to hight above baseline
			if (avgLineRegionHeightAboveAvg > avgLineRegionBottom) {
				avgLineRegionHeightAboveAvg = avgLineRegionBottom;
				if (DEBUG_BLOCK_ANALYSIS) System.out.println("       ==> capping average above average height to average region bottom, line most likely has many below baseline or above cap height overshoots");
			}
			if (avgLineRegionHeightAboveAvg != -1) {
				blockLines[l].capHeight = avgLineRegionHeightAboveAvg;
				if (vbiGr != null) {
					vbiGr.setColor(Color.BLUE);
					vbiGr.drawLine(blockLines[l].getPageLeft(), (blockLines[l].getPageTop() + avgLineRegionBottom - avgLineRegionHeightAboveAvg), blockLines[l].getPageRight(), (blockLines[l].getPageTop() + avgLineRegionBottom - avgLineRegionHeightAboveAvg));
				}
			}
			float fontSizeXHeight = ((avgLineRegionHeightBelowAvg * 300 /* measured DPI */ * 24.0f /* measured font size */) / (dpi * 45 /* x-height of 24pt at 300 DPI */));
			float fontSizeCapHeight = ((avgLineRegionHeightAboveAvg * 300 /* measured DPI */ * 24.0f /* measured font size */) / (dpi * 67 /* cap height of 24pt at 300 DPI */));
			if (DEBUG_BLOCK_ANALYSIS) System.out.println("       ==> font size is " + fontSizeXHeight + " by x-height, " + fontSizeCapHeight + " by cap height");
		}
		
		//	get average retio of cap height to x-height
		CountingSet xHeightCapHeightRelationCounts = new CountingSet(new TreeMap());
		for (int l = 0; l < blockLines.length; l++) {
			if (blockLines[l].xHeight == -1)
				continue;
			if (blockLines[l].capHeight == -1)
				continue;
			xHeightCapHeightRelationCounts.add(new Integer((blockLines[l].xHeight * 100) / blockLines[l].capHeight));
		}
		System.out.println(" - x-height to cap height ratios are " + xHeightCapHeightRelationCounts);
		int avgXHeightCapHeightRatio = getAverage(xHeightCapHeightRelationCounts, 0, 100);
		System.out.println("   average x-height to cap height ratio is " + avgXHeightCapHeightRatio);
		
		//	extrapolate x-height from cap height where former missing
		HashSet extrapolatedXHeightLines = new HashSet();
		if (avgXHeightCapHeightRatio != -1)
			for (int l = 0; l < blockLines.length; l++) {
				if (blockLines[l].xHeight != -1)
					continue;
				if (blockLines[l].capHeight == -1)
					continue;
				blockLines[l].xHeight = ((blockLines[l].capHeight * avgXHeightCapHeightRatio) / 100);
				extrapolatedXHeightLines.add(blockLines[l]);
				if (DEBUG_BLOCK_ANALYSIS) System.out.println("   - extrapolated x-height to " + blockLines[l].xHeight + " in " + blockLines[l].getPageBounds());
			}
//		
//		//	increase line top to reduce height where top exceeds cap height
//		for (int l = 0; l < blockLines.length; l++) {
//			if (blockLines[l].capHeight == -1)
//				continue; // nothing to work with
//			if (Math.abs(blockLines[l].capHeight - blockLines[l].baseline) < 2)
//				continue; // this one is in range
//			System.out.println("   - increasing top of " + blockLines[l].getPageBounds() + " for cap height " + blockLines[l].capHeight + " vs. baseline " + blockLines[l].baseline);
//			while (blockLines[l].capHeight < blockLines[l].baseline) {
//				blockLines[l].top++;
//				blockLines[l].baseline--;
//			}
//			System.out.println("     --> top now is at " + blockLines[l].getPageTop() + " with bounds " + blockLines[l].getPageBounds());
//		}
		
		//	adjust split between overlapping lines (make sure of one pixel of distance to facilitate downstream splitting)
		for (int l = 1; l < blockLines.length; l++) {
			if (blockLines[l].top > blockLines[l-1].bottom)
				continue; // this one is OK
			if ((blockLines[l].left >= blockLines[l-1].right) || (blockLines[l].right <= blockLines[l-1].left))
				continue; // side by side
			if (DEBUG_BLOCK_ANALYSIS) {
				System.out.println("   - resolving overlap of bottom of " + blockLines[l-1].getPageBounds() + " with top of " + blockLines[l].getPageBounds());
				System.out.println("     top line descender is " + (blockLines[l-1].getHeight() - blockLines[l-1].baseline) + " below baseline " + blockLines[l - 1].getPageBaseline());
				System.out.println("     bottom line ascender is " + (blockLines[l].baseline - blockLines[l].xHeight) + " above x-height line " + (blockLines[l].getPageBaseline() - blockLines[l].xHeight));
			}
			if (blockLines[l].bottom <= blockLines[l-1].bottom) {
				if (DEBUG_BLOCK_ANALYSIS)
					System.out.println("     ==> completely contained");
				//	TODO merge these suckers, swap them, or whatever ...
				continue;
			}
			//	TODOne check which line loses fewer pixels, and decrease bottom of line above instead, depending on comparison result
			//	TODOne factor in pixel counts for regions that go above baseline or below x-height, respectively
			//	TODOne factor in deviation of descender vs. line height or cap height ascender vs. line height, respectively
			//	==> TODOne use latter as weighting factor for loss at individual pixel rows
			
			//	compute pixes losses for each conflicting line
			int[] tlbLosses = new int[blockLines[l-1].bottom - blockLines[l].top + 1];
			int[] bltLosses = new int[blockLines[l-1].bottom - blockLines[l].top + 1];
			for (int r = blockLines[l].top; r <= blockLines[l-1].bottom; r++) {
				int lossIndex = (r - blockLines[l].top);
				
				tlbLosses[lossIndex] = 0;
				int tlr = (r - blockLines[l-1].oTop - 1 /* bottom is exclusive */);
				if (tlr < blockLines[l-1].regionColors[0].length) {
					int lineTopShift = (blockLines[l-1].top - blockLines[l-1].oTop);
					int lossWeight = (tlbLosses.length - lossIndex);
					for (int c = 0; c < blockLines[l-1].regionColors.length; c++) {
						int reg = blockLines[l-1].regionColors[c][tlr];
						if (reg == 0)
							continue; // nothing there
						if (blockLines[l-1].regionSizes[reg] == 0)
							continue; // eliminated above
						if ((blockLines[l-1].regionMinRows[reg]) > (blockLines[l-1].baseline + lineTopShift))
							continue; // exists only below baseline
						tlbLosses[lossIndex] += lossWeight;
					}
				}
				if (((blockLines[l - 1].baseline * 4) / 3) < tlr)
					tlbLosses[lossIndex] /= 2; // cut in half if below normal 25% descender
				
				bltLosses[lossIndex] = 0;
				int blr = (r - blockLines[l].oTop);
				if (blr >= 0) {
//					int lineTopShift = (blockLines[l].top - blockLines[l].oTop);
					int lossWeight = (lossIndex + 1);
					for (int c = 0; c < blockLines[l].regionColors.length; c++) {
						int reg = blockLines[l].regionColors[c][blr];
						if (reg == 0)
							continue; // nothing there
						if (blockLines[l].regionSizes[reg] == 0)
							continue; // eliminated above
//						if ((blockLines[l].lineRegionMaxRows[reg]) < (blockLines[l].capHeight + lineTopShift))
//							continue; // exists only above cap height
						bltLosses[lossIndex] += lossWeight;
					}
				}
				if (blr < (blockLines[l].baseline - blockLines[l].capHeight))
					bltLosses[lossIndex] /= 2; // cut in half if above cap height
			}
			
			//	compute cumulative losses
			for (int r = (tlbLosses.length - 1); r > 0; r--)
				tlbLosses[r - 1] += tlbLosses[r];
			if (DEBUG_BLOCK_ANALYSIS) System.out.println("     top line losses are " + Arrays.toString(tlbLosses));
			for (int r = 1; r < bltLosses.length; r++)
				bltLosses[r] += bltLosses[r - 1];
			if (DEBUG_BLOCK_ANALYSIS) System.out.println("     bottom line losses are " + Arrays.toString(bltLosses));
			
			//	use split that minimizes overall loss (akin to two springs centering split between them)
			int tlbCutRows = tlbLosses.length;
			int bltCutRows = 0;
			for (int sr = 0; sr < tlbLosses.length; sr++) {
				if (tlbLosses[sr] < bltLosses[sr])
					break;
				tlbCutRows--;
				bltCutRows++;
			}
			if (DEBUG_BLOCK_ANALYSIS) System.out.println("     decreasing bottom of " + blockLines[l-1].getPageBounds() + " by " + tlbCutRows);
			blockLines[l-1].bottom -= tlbCutRows;
			if (DEBUG_BLOCK_ANALYSIS) System.out.println("     --> bottom now is at " + blockLines[l-1].getPageBottom() + " with bounds " + blockLines[l-1].getPageBounds());
			if (DEBUG_BLOCK_ANALYSIS) System.out.println("     increasing top of " + blockLines[l].getPageBounds() + " by " + bltCutRows);
			blockLines[l].top += bltCutRows;
			blockLines[l].baseline -= bltCutRows;
			if (DEBUG_BLOCK_ANALYSIS) System.out.println("     --> top now is at " + blockLines[l].getPageTop() + " with bounds " + blockLines[l].getPageBounds());
		}
		
		//	extend lines to include descender (at least 25% of height below baseline)
		for (int l = 0; l < blockLines.length; l++) {
			if ((blockLines[l].baseline * 4) <= (blockLines[l].getHeight() * 3))
				continue; // baseline above 75% of height
			int maxBottom = (((l+1) < blockLines.length) ? (blockLines[l+1].top - 1) : Integer.MAX_VALUE /* allow downward expanding block proper */);
			if (DEBUG_BLOCK_ANALYSIS) System.out.println("   - increasing bottom of " + blockLines[l].getPageBounds() + " for too small descender, at most to " + (blockBounds.top + maxBottom));
			while ((blockLines[l].bottom < maxBottom) && ((blockLines[l].baseline * 4) > (blockLines[l].getHeight() * 3)))
				blockLines[l].bottom++;
			if (DEBUG_BLOCK_ANALYSIS) System.out.println("     --> bottom now is at " + blockLines[l].getPageBottom() + " with bounds " + blockLines[l].getPageBounds());
		}
		
		//	add ascender to lines whose cap height is closer to x-height of previous line (even if we extrapolated x-height above)
		//	TODO also check line below (if any), as there might be mid-block font size changes
		for (int l = 0; l < blockLines.length; l++) {
			if (blockLines[l].capHeight == -1)
				continue; // nothing to work with
			if ((blockLines[l].xHeight != -1) && !extrapolatedXHeightLines.contains(blockLines[l])) 
				continue; // we have a measured x-height
			BlockLine cBlockLine;
			if ((l != 0))
				cBlockLine = blockLines[l - 1]; // should work unless subject line overflows alone from previous column or page
			else if ((l+1) < blockLines.length)
				cBlockLine = blockLines[l + 1]; // fall back to line below
			else cBlockLine = null; // preciously little we can do
			if ((cBlockLine == null) || (cBlockLine.capHeight == -1) || (cBlockLine.xHeight == -1))
				continue;
			int capHeightDist = Math.abs(blockLines[l].capHeight - cBlockLine.capHeight);
			int xHeightDist = Math.abs(blockLines[l].capHeight - cBlockLine.xHeight);
			if (capHeightDist < xHeightDist)
				continue; // this one's in the ballpark
			blockLines[l].xHeight = blockLines[l].capHeight; // this is what we actually measured
			blockLines[l].capHeight = ((blockLines[l].xHeight * 100) / avgXHeightCapHeightRatio);
			if (DEBUG_BLOCK_ANALYSIS) System.out.println("   - extrapolated cap height to " + blockLines[l].capHeight + " in " + blockLines[l].getPageBounds());
			int minTop = ((l != 0) ? (blockLines[l-1].bottom + 1) : Integer.MIN_VALUE /* allow upward expanding block proper */);
			if (DEBUG_BLOCK_ANALYSIS) System.out.println("   - decreasing top of " + blockLines[l].getPageBounds() + " for too small ascender, at most to " + minTop);
			while ((blockLines[l].top > minTop) && (blockLines[l].baseline < blockLines[l].capHeight)) {
				blockLines[l].top--;
				blockLines[l].baseline++;
			}
			if (DEBUG_BLOCK_ANALYSIS) System.out.println("     --> top now is at " + blockLines[l].getPageTop() + " with bounds " + blockLines[l].getPageBounds());
		}
		
		//	finally
		return blockLines;
	}
	
	private static int getAverage(CountingSet cs, int minToCount, int maxToCount) {
		if (cs.isEmpty())
			return -1;
		int count = 0;
		int sum = 0;
		for (Iterator it = cs.iterator(); it.hasNext();) {
			Integer i = ((Integer) it.next());
			if (i.intValue() < minToCount)
				continue;
			if (maxToCount < i.intValue())
				break;
			count += cs.getCount(i);
			sum += (i.intValue() * cs.getCount(i));
		}
		return ((count == 0) ? -1 : ((sum + (count / 2)) / count));
	}
	
	private static int getMinMid60(CountingSet cs, int minToCount, int maxToCount) {
		if (cs.isEmpty())
			return -1;
		int checkCount = 0;
		for (Iterator it = cs.iterator(); it.hasNext();) {
			Integer i = ((Integer) it.next());
			if ((cs.size() * 80) < (checkCount * 100))
				break; // beyond 80%
			int iCount = cs.getCount(i);
			checkCount += iCount;
			if (i.intValue() < minToCount)
				continue;
			if (maxToCount < i.intValue())
				break;
			if ((cs.size() * (100 - 80)) < (checkCount * 100)) // we're beyond smallest 20%
				return i.intValue();
		}
		return -1;
	}
	
	private static int getMaxMid60(CountingSet cs, int minToCount, int maxToCount) {
		if (cs.isEmpty())
			return -1;
		int checkCount = 0;
		int lastChecked = -1;
		for (Iterator it = cs.iterator(); it.hasNext();) {
			Integer i = ((Integer) it.next());
			if ((cs.size() * 80) < (checkCount * 100))
				break; // beyond 80%
			int iCount = cs.getCount(i);
			checkCount += iCount;
			if (i.intValue() < minToCount)
				continue;
			if (maxToCount < i.intValue())
				break;
			lastChecked = i.intValue();
		}
		return lastChecked;
	}
	
	private static int getAverageMid60(CountingSet cs, int minToCount, int maxToCount) {
		if (cs.isEmpty())
			return -1;
		int checkCount = 0;
		int count = 0;
		int sum = 0;
		for (Iterator it = cs.iterator(); it.hasNext();) {
			Integer i = ((Integer) it.next());
			if ((cs.size() * 80) < (checkCount * 100))
				break; // beyond 80%
			int iCount = cs.getCount(i);
			checkCount += iCount;
			if (i.intValue() < minToCount)
				continue;
			if (maxToCount < i.intValue())
				break;
			if ((cs.size() * (100 - 80)) < (checkCount * 100)) /* we're beyond smallest 20% */ {
				count += iCount;
				sum += (i.intValue() * iCount);
			}
		}
		return ((count == 0) ? -1 : ((sum + (count / 2)) / count));
	}
	
	private static int countElementsUpTo(CountingSet cs, int maxToCount) {
		if (cs.isEmpty())
			return 0;
		int count = 0;
		for (Iterator it = cs.iterator(); it.hasNext();) {
			Integer i = ((Integer) it.next());
			if (maxToCount < i.intValue())
				break;
			count += cs.getCount(i);
		}
		return count;
	}
//	
//	private static int getMaxUpTo(CountingSet cs, int maxToCount) {
//		if (cs.isEmpty())
//			return -1;
//		int maxUpTo = -1;
//		for (Iterator it = cs.iterator(); it.hasNext();) {
//			Integer i = ((Integer) it.next());
//			if (maxToCount < i.intValue())
//				break;
//			maxUpTo = i.intValue();
//		}
//		return maxUpTo;
//	}
//	
//	private static int getMinAbove(CountingSet cs, int maxToIgnore) {
//		if (cs.isEmpty())
//			return -1;
//		for (Iterator it = cs.iterator(); it.hasNext();) {
//			Integer i = ((Integer) it.next());
//			if (maxToIgnore < i.intValue())
//				return i.intValue();
//		}
//		return -1;
//	}
	
	private static int getMaxRegionColor(int[][] regionColors) {
		int maxRegionColor = 0;
		for (int c = 0; c < regionColors.length; c++) {
			for (int r = 0; r < regionColors[c].length; r++)
				maxRegionColor = Math.max(maxRegionColor, regionColors[c][r]);
		}
		return maxRegionColor;
	}
	
	private static void measureRegions(int[][] regionColors, int[] regionSizes, int[] regionMinCols, int[] regionMaxCols, int[] regionMinRows, int[] regionMaxRows) {
		Arrays.fill(regionSizes, 0);
		Arrays.fill(regionMinCols, regionColors.length);
		Arrays.fill(regionMaxCols, 0);
		Arrays.fill(regionMinRows, regionColors[0].length);
		Arrays.fill(regionMaxRows, 0);
		for (int c = 0; c < regionColors.length; c++)
			for (int r = 0; r < regionColors[c].length; r++) {
				int reg = regionColors[c][r];
				if (reg == 0)
					continue;
				regionSizes[reg]++;
				regionMinCols[reg] = Math.min(regionMinCols[reg], c);
				regionMaxCols[reg] = Math.max(regionMaxCols[reg], c);
				regionMinRows[reg] = Math.min(regionMinRows[reg], r);
				regionMaxRows[reg] = Math.max(regionMaxRows[reg], r);
			}
	}
	
//	private static void cleanRegions(int[][] regionColors, int dpi, int[] regionSizes, int[] regionMinCols, int[] regionMaxCols, int[] regionMinRows, int[] regionMaxRows) {
//		int minDim = ((dpi + (100 / 2)) / 100); // quarter of a millimeter
//		int minSize = (minDim * minDim);
//		int minKeepDim = ((dpi + (50 / 2)) / 50); // half of a millimeter
//		for (int reg = 1; reg < regionSizes.length; reg++) {
//			if (regionSizes[reg] == 0)
//				continue; // eliminated before
//			if (minSize < regionSizes[reg])
//				continue; // too large to consider for elimination
//			int regWidth = (regionMaxCols[reg] - regionMinCols[reg] + 1);
//			if (minKeepDim < regWidth)
//				continue; // too wide to eliminate
//			int regHeight = (regionMaxRows[reg] - regionMinRows[reg] + 1);
//			if (minKeepDim < regHeight)
//				continue; // too high to eliminate
//			if ((minDim < regWidth) && (minDim < regWidth))
//				continue; // above threshold in both directions
//			System.out.println("Eliminating region " + reg + " of " + regionSizes[reg] + " pixels in " + regionMinCols[reg] + "-" + regionMaxCols[reg] + " x " + regionMinRows[reg] + "-" + regionMaxRows[reg]);
//			this.attachRegion(regionColors, reg, 0, regionSizes, regionMinCols, regionMaxCols, regionMinRows, regionMaxRows, -1, -1, null);
//		}
//	}
	
	private static void attachSmallRegionsDownward(int[][] regionColors, int maxAttachSize, int maxAttachDist, int minAttachToSize, int[] regionSizes, int[] regionMinCols, int[] regionMaxCols, int[] regionMinRows, int[] regionMaxRows, int pageLeft, int pageTop, BufferedImage vbi) {
		for (int reg = 1; reg < regionSizes.length; reg++) {
			if (regionSizes[reg] == 0)
				continue; // eliminated before
			int regWidth = (regionMaxCols[reg] - regionMinCols[reg] + 1);
			if (regWidth > maxAttachSize)
				continue;
			int regHeight = (regionMaxRows[reg] - regionMinRows[reg] + 1);
			if (regHeight > maxAttachSize)
				continue;
			int aReg = -1;
			for (int lr = (regionMaxRows[reg] + 1); lr <= (regionMaxRows[reg] + maxAttachDist); lr++) {
				if (lr >= regionColors[0].length)
					break;
				for (int c = regionMinCols[reg]; c <= regionMaxCols[reg]; c++) {
					int lReg = regionColors[c][lr];
					if (lReg == 0)
						continue; // nothing there
					if (lReg == reg)
						continue; // no use attaching region to itself
					if (regionSizes[lReg] == 0)
						continue; // eliminated before
					int lRegWidth = (regionMaxCols[lReg] - regionMinCols[lReg] + 1);
					if (lRegWidth <= minAttachToSize)
						continue;
					int lRegHeight = (regionMaxRows[lReg] - regionMinRows[lReg] + 1);
					if (lRegHeight <= minAttachToSize)
						continue;
					aReg = lReg;
					break;
				}
				if (aReg != -1)
					break;
			}
			if (aReg == -1)
				continue;
			attachRegion(regionColors, reg, aReg, regionSizes, regionMinCols, regionMaxCols, regionMinRows, regionMaxRows, pageLeft, pageTop, vbi);
		}
	}
	
	private static void attachRegion(int[][] regionColors, int reg, int toReg, int[] regionSizes, int[] regionMinCols, int[] regionMaxCols, int[] regionMinRows, int[] regionMaxRows, int pageLeft, int pageTop, BufferedImage vbi) {
		int toRegRgb = Color.HSBtoRGB(((1.0f / 32) * (toReg % 32)), 1.0f, 1.0f);
		for (int c = regionMinCols[reg]; c <= regionMaxCols[reg]; c++) {
			for (int r = regionMinRows[reg]; r <= regionMaxRows[reg]; r++)
				if (regionColors[c][r] == reg) {
					regionColors[c][r] = toReg;
					if ((vbi != null) && (toReg != 0))
						vbi.setRGB((pageLeft + c), (pageTop + r), toRegRgb);
				}
		}
		regionSizes[toReg] += regionSizes[reg];
		regionSizes[reg] = 0;
		regionMinCols[toReg] = Math.min(regionMinCols[toReg], regionMinCols[reg]);
		regionMaxCols[toReg] = Math.max(regionMaxCols[toReg], regionMaxCols[reg]);
		regionMinRows[toReg] = Math.min(regionMinRows[toReg], regionMinRows[reg]);
		regionMaxRows[toReg] = Math.max(regionMaxRows[toReg], regionMaxRows[reg]);
	}
}