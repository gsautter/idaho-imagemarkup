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
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

import de.uka.ipd.idaho.gamta.util.ProgressMonitor;
import de.uka.ipd.idaho.gamta.util.imaging.BoundingBox;
import de.uka.ipd.idaho.gamta.util.imaging.ImagingConstants;
import de.uka.ipd.idaho.im.analysis.Imaging.AnalysisImage;
import de.uka.ipd.idaho.im.analysis.Imaging.ImagePartRectangle;
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
			this.bounds = new ImagePartRectangle(bounds.analysisImage);
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
				BufferedImage pi = region.bounds.analysisImage.getImage();
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
					BufferedImage pi = region.bounds.analysisImage.getImage();
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
					BufferedImage pi = region.bounds.analysisImage.getImage();
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
				ImagePartRectangle testRegionBounds = new ImagePartRectangle(subRegion.bounds.analysisImage);
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
				BufferedImage pi = region.bounds.analysisImage.getImage();
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
			if (((avgLastLineHighRowPixelWidth * 10) < (this.avgHighRowPixelWidth * 8)) && ((this.avgHighRowPixelWidth * 8) < (avgLastLineHighRowPixelWidth * 8))) {
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
		byte[][] brightness = block.bounds.analysisImage.getBrightness();
		
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
				ImagePartRectangle wordBox = new ImagePartRectangle(words[w].bounds.analysisImage);
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
				baselineComputationWords[w].baseline = findBaseline(block.bounds.analysisImage, nwrs);
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
				int lineBaseline = ((lineBaselineWordCount == 0) ? findBaseline(block.bounds.analysisImage, wordBounds) : ((lineBaselineSum + (lineBaselineWordCount / 2)) / lineBaselineWordCount));
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
				int windowBaseline = ((windowBaselineWordCount == 0) ? findBaseline(block.bounds.analysisImage, wordBounds) : ((windowBaselineSum + (windowBaselineWordCount / 2)) / windowBaselineWordCount));
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
}