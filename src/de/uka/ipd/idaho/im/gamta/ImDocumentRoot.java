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
package de.uka.ipd.idaho.im.gamta;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.WeakHashMap;

import de.uka.ipd.idaho.easyIO.util.RandomByteSource;
import de.uka.ipd.idaho.gamta.Annotation;
import de.uka.ipd.idaho.gamta.AnnotationListener;
import de.uka.ipd.idaho.gamta.AnnotationUtils;
import de.uka.ipd.idaho.gamta.Attributed;
import de.uka.ipd.idaho.gamta.CharSequenceListener;
import de.uka.ipd.idaho.gamta.DocumentRoot;
import de.uka.ipd.idaho.gamta.EditableAnnotation;
import de.uka.ipd.idaho.gamta.Gamta;
import de.uka.ipd.idaho.gamta.MutableAnnotation;
import de.uka.ipd.idaho.gamta.MutableCharSequence;
import de.uka.ipd.idaho.gamta.MutableTokenSequence;
import de.uka.ipd.idaho.gamta.QueriableAnnotation;
import de.uka.ipd.idaho.gamta.Token;
import de.uka.ipd.idaho.gamta.TokenSequence;
import de.uka.ipd.idaho.gamta.TokenSequenceListener;
import de.uka.ipd.idaho.gamta.TokenSequenceUtils;
import de.uka.ipd.idaho.gamta.Tokenizer;
import de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed;
import de.uka.ipd.idaho.gamta.defaultImplementation.TemporaryAnnotation;
import de.uka.ipd.idaho.gamta.util.ImmutableAnnotation;
import de.uka.ipd.idaho.gamta.util.constants.TableConstants;
import de.uka.ipd.idaho.gamta.util.imaging.BoundingBox;
import de.uka.ipd.idaho.gamta.util.imaging.ImagingConstants;
import de.uka.ipd.idaho.im.ImAnnotation;
import de.uka.ipd.idaho.im.ImDocument;
import de.uka.ipd.idaho.im.ImLayoutObject;
import de.uka.ipd.idaho.im.ImObject;
import de.uka.ipd.idaho.im.ImObject.UuidHelper;
import de.uka.ipd.idaho.im.ImPage;
import de.uka.ipd.idaho.im.ImRegion;
import de.uka.ipd.idaho.im.ImWord;
import de.uka.ipd.idaho.im.util.ImUtils;

/**
 * Implementation of a GAMTA document wrapped around an Image Markup document.
 * This class supports all modifications to annotations, and it writes any
 * such modification through to the backing Image Markup document.<br>
 * Annotation identifiers can be switched between random and non-random (hash
 * based) modes, with the default being random mode.<br>
 * The wrapper can exhibit tokens as <code>word</code> annotations if required.
 * This behavior is switched off by default.
 * 
 * @author sautter
 */
public class ImDocumentRoot extends ImTokenSequence implements DocumentRoot, ImagingConstants, TableConstants {
	static final String GRID_LEFT_COL_ATTRIBUTE = "gridcol";
	static final String GRID_TOP_ROW_ATTRIBUTE = "gridrow";
	static final String GRID_COL_COUNT_ATTRIBUTE = "gridcols";
	static final String GRID_ROW_COUNT_ATTRIBUTE = "gridrows";
	
	static final boolean TRACK_INSTANCES = false;
	final AccessHistory accessHistory = (TRACK_INSTANCES ? new AccessHistory(this) : null);
	private static class AccessHistory {
		final ImDocumentRoot doc;
		final long created;
		final StackTraceElement[] createStack;
		long lastAccessed;
//		StackTraceElement[] lastAccessStack; ==> causes too much debris
		final int staleAge = (1000 * 60 * 10);
		private final boolean untracked;
		private boolean printed = false;
		AccessHistory(ImDocumentRoot doc) {
			this.doc = doc;
			this.created = System.currentTimeMillis();
			this.createStack = Thread.currentThread().getStackTrace();
			this.lastAccessed = this.created;
//			this.lastAccessStack = this.createStack;
			boolean untracked = false;
			for (int e = 0; e < this.createStack.length; e++) {
				String ses = this.createStack[e].toString();
				if (ses.indexOf(".main(") != -1) {
					untracked = true; // no tracking in slaves
					break;
				}
			}
			this.untracked = untracked;
			if (this.untracked)
				return;
			synchronized (accessHistories) {
				accessHistories.add(new WeakReference(this));
			}
		}
		void accessed() {
			if (this.untracked)
				return;
			this.lastAccessed = System.currentTimeMillis();
//			this.lastAccessStack = Thread.currentThread().getStackTrace();
			this.printed = false;
		}
		boolean printData(long time, boolean newOnly) {
			if (this.printed && newOnly)
				return false;
			System.err.println("Stale ImDocumentRoot (" + (time - this.created) + "ms ago, last accessed " + (time - this.lastAccessed) + "ms ago)");
			for (int e = 0; e < this.createStack.length; e++)
				System.err.println("CR\tat " + this.createStack[e].toString());
//			StackTraceElement[] las = this.lastAccessStack; // let's be super safe here
//			for (int e = 0; e < las.length; e++)
//				System.err.println("LA\tat " + las[e].toString());
			this.printed = true;
			return true;
		}
	}
	private static ArrayList accessHistories = (TRACK_INSTANCES ? new ArrayList() : null);
	static {
		if (TRACK_INSTANCES) {
			Thread deadInstanceChecker = new Thread("ImDocumentRootGuard") {
				public void run() {
					int reclaimed = 0;
					WeakReference gcDetector = new WeakReference(new Object());
					while (true) {
						try {
							sleep(1000 * 60 * 2);
						} catch (InterruptedException ie) {}
						int stale = 0;
						if (accessHistories.size() != 0) try {
							long time = System.currentTimeMillis();
							boolean noGc;
							if (gcDetector.get() == null) {
								gcDetector = new WeakReference(new Object());
								noGc = false;
							}
							else noGc = true;
							for (int h = 0; h < accessHistories.size(); h++)
								synchronized (accessHistories) {
									WeakReference ahr = ((WeakReference) accessHistories.get(h));
									AccessHistory ah = ((AccessHistory) ahr.get());
									if (ah == null) /* cleared out by GC as supposed to */ {
										accessHistories.remove(h--);
										reclaimed++;
									}
									else if ((ah.lastAccessed + ah.staleAge) < time) {
										if (ah.printData(time, noGc))
											stale++;
									}
								}
							System.err.println("ImDocumentRootGuard: " + accessHistories.size() + " extant, " + reclaimed + " reclaimed, " + stale + (noGc ? " newly gone" : "") + " stale");
						}
						catch (Exception e) {
							System.err.println("ImDocumentRootGuard: error checking instances: " + e.getMessage());
						}
					}
				}
			};
			deadInstanceChecker.start();
		}
	}
	
	private class ImAnnotationBase {
		
		//	use container for table specific attributes to reduce dead weight fields
		class TableAnnotationAttributes {
			int gridLeft = -1;
			int gridTop = -1;
			
			int gridCols = 0;
			int gridRows = 0;
			
			final boolean isHeader;
			
			final int leftCellsToSpan = 0; // we cannot span leftwards, as we might span multiple rows
			int rightCellsToSpan = 0;
			final int aboveCellsToSpan = 0; // we cannot span upwards, as tables are filled row by row
			int belowCellsToSpan = 0;
			int[] emptyCellsToSpan = null;
			
			TableAnnotationAttributes(boolean isHeader) {
				this.isHeader = isHeader;
			}
			
			void addAttributeNames(TreeSet ans) {
				if ((this.leftCellsToSpan + this.rightCellsToSpan) != 0)
					ans.add(TableConstants.COL_SPAN_ATTRIBUTE);
				if (this.leftCellsToSpan != 0)
					ans.add(TableConstants.COL_SPAN_ATTRIBUTE + "Left");
				if (this.rightCellsToSpan != 0)
					ans.add(TableConstants.COL_SPAN_ATTRIBUTE + "Right");
				if ((this.aboveCellsToSpan + this.belowCellsToSpan) != 0)
					ans.add(TableConstants.ROW_SPAN_ATTRIBUTE);
				if (this.aboveCellsToSpan != 0)
					ans.add(TableConstants.ROW_SPAN_ATTRIBUTE + "Above");
				if (this.belowCellsToSpan != 0)
					ans.add(TableConstants.ROW_SPAN_ATTRIBUTE + "Below");
				if (this.gridLeft != -1)
					ans.add(GRID_LEFT_COL_ATTRIBUTE);
				if (this.gridTop != -1)
					ans.add(GRID_TOP_ROW_ATTRIBUTE);
				if (this.gridCols != 0)
					ans.add(GRID_COL_COUNT_ATTRIBUTE);
				if (this.gridRows != 0)
					ans.add(GRID_ROW_COUNT_ATTRIBUTE);
				if (this.emptyCellsToSpan != null)
					for (int c = 0; c < this.emptyCellsToSpan.length; c++) {
						if (this.emptyCellsToSpan[c] != 0)
							ans.add(TableConstants.ROW_SPAN_ATTRIBUTE + "-" + c);
					}
			}
			boolean hasAttribute(String name) {
				if (name == null)
					return false;
				else if (TableConstants.COL_SPAN_ATTRIBUTE.equals(name) && ((this.leftCellsToSpan + this.rightCellsToSpan) != 0))
					return true;
				else if ((TableConstants.COL_SPAN_ATTRIBUTE + "Left").equals(name) && (this.leftCellsToSpan != 0))
					return true;
				else if ((TableConstants.COL_SPAN_ATTRIBUTE + "Right").equals(name) && (this.rightCellsToSpan != 0))
					return true;
				else if (TableConstants.ROW_SPAN_ATTRIBUTE.equals(name) && ((this.aboveCellsToSpan + this.belowCellsToSpan) != 0))
					return true;
				else if ((TableConstants.ROW_SPAN_ATTRIBUTE + "Above").equals(name) && (this.aboveCellsToSpan != 0))
					return true;
				else if ((TableConstants.ROW_SPAN_ATTRIBUTE + "Below").equals(name) && (this.belowCellsToSpan != 0))
					return true;
				else if (GRID_LEFT_COL_ATTRIBUTE.equals(name) && (this.gridLeft != -1))
					return true;
				else if (GRID_TOP_ROW_ATTRIBUTE.equals(name) && (this.gridTop != -1))
					return true;
				else if (GRID_COL_COUNT_ATTRIBUTE.equals(name) && (this.gridCols != 0))
					return true;
				else if (GRID_ROW_COUNT_ATTRIBUTE.equals(name) && (this.gridRows != 0))
					return true;
				else if ((this.emptyCellsToSpan != null) && name.startsWith(TableConstants.ROW_SPAN_ATTRIBUTE + "-")) {
					try {
						int col = Integer.parseInt(name.substring((TableConstants.ROW_SPAN_ATTRIBUTE + "-").length()));
						return ((0 <= col) && (col < this.emptyCellsToSpan.length) && (this.emptyCellsToSpan[col] != 0));
					} catch (RuntimeException re) {}
					return false;
				}
				else return false;
			}
			Object getAttribute(String name, Object def) {
				if (name == null)
					return null;
				else if (TableConstants.COL_SPAN_ATTRIBUTE.equals(name) && ((this.leftCellsToSpan + this.rightCellsToSpan) != 0))
					return ("" + (this.leftCellsToSpan + 1 + this.rightCellsToSpan));
				else if ((TableConstants.COL_SPAN_ATTRIBUTE + "Left").equals(name) && (this.leftCellsToSpan != 0))
					return ("" + this.leftCellsToSpan);
				else if ((TableConstants.COL_SPAN_ATTRIBUTE + "Right").equals(name) && (this.rightCellsToSpan != 0))
					return ("" + this.rightCellsToSpan);
				else if (TableConstants.ROW_SPAN_ATTRIBUTE.equals(name) && ((this.aboveCellsToSpan + this.belowCellsToSpan) != 0))
					return ("" + (this.aboveCellsToSpan + 1 + this.belowCellsToSpan));
				else if ((TableConstants.ROW_SPAN_ATTRIBUTE + "Above").equals(name) && (this.aboveCellsToSpan != 0))
					return ("" + this.aboveCellsToSpan);
				else if ((TableConstants.ROW_SPAN_ATTRIBUTE + "Below").equals(name) && (this.belowCellsToSpan != 0))
					return ("" + this.belowCellsToSpan);
				else if (GRID_LEFT_COL_ATTRIBUTE.equals(name) && (this.gridLeft != -1))
					return ("" + this.gridLeft);
				else if (GRID_TOP_ROW_ATTRIBUTE.equals(name) && (this.gridTop != -1))
					return ("" + this.gridTop);
				else if (GRID_COL_COUNT_ATTRIBUTE.equals(name) && (this.gridCols != -1))
					return ("" + this.gridCols);
				else if (GRID_ROW_COUNT_ATTRIBUTE.equals(name) && (this.gridRows != -1))
					return ("" + this.gridRows);
				else if ((this.emptyCellsToSpan != null) && name.startsWith(TableConstants.ROW_SPAN_ATTRIBUTE + "-")) {
					try {
						int col = Integer.parseInt(name.substring((TableConstants.ROW_SPAN_ATTRIBUTE + "-").length()));
						if ((0 <= col) && (col < this.emptyCellsToSpan.length) && (this.emptyCellsToSpan[col] != 0))
							return ("" + this.emptyCellsToSpan[col]);
					} catch (RuntimeException re) {}
					return def;
				}
				else return getAttributed().getAttribute(name, def);
			}
		}
		
		//	we need an attribute container for logical paragraphs, as they have no direct counterpart in image based model
		private class ParagraphAnnotationAttributes extends AbstractAttributed {
			public Object getAttribute(String name, Object def) {
				if (PAGE_ID_ATTRIBUTE.equals(name))
					return ("" + pFirstWord.pageId);
				else if (LAST_PAGE_ID_ATTRIBUTE.equals(name))
					return ("" + pLastWord.pageId);
				else if (BLOCK_ID_ATTRIBUTE.equals(name))
					return pFirstBlockId;
				else if (LAST_BLOCK_ID_ATTRIBUTE.equals(name))
					return pLastBlockId;
				else if (PAGE_NUMBER_ATTRIBUTE.equals(name) && pFirstWord.getPage().hasAttribute(PAGE_NUMBER_ATTRIBUTE))
					return pFirstWord.getPage().getAttribute(PAGE_NUMBER_ATTRIBUTE);
				else if (LAST_PAGE_NUMBER_ATTRIBUTE.equals(name) && pLastWord.getPage().hasAttribute(PAGE_NUMBER_ATTRIBUTE))
					return pLastWord.getPage().getAttribute(PAGE_NUMBER_ATTRIBUTE);
				else if (TYPE_ATTRIBUTE.equals(name))
					return pFirstWord.getTextStreamType();
				else if (ImAnnotation.FIRST_WORD_ATTRIBUTE.equals(name))
					return pFirstWord;
				else if (ImAnnotation.LAST_WORD_ATTRIBUTE.equals(name))
					return pLastWord;
				else return super.getAttribute(name, def);
			}
			public Object getAttribute(String name) {
				if (PAGE_ID_ATTRIBUTE.equals(name))
					return ("" + pFirstWord.pageId);
				else if (LAST_PAGE_ID_ATTRIBUTE.equals(name))
					return ("" + pLastWord.pageId);
				else if (BLOCK_ID_ATTRIBUTE.equals(name))
					return pFirstBlockId;
				else if (LAST_BLOCK_ID_ATTRIBUTE.equals(name))
					return pLastBlockId;
				else if (PAGE_NUMBER_ATTRIBUTE.equals(name) && pFirstWord.getPage().hasAttribute(PAGE_NUMBER_ATTRIBUTE))
					return pFirstWord.getPage().getAttribute(PAGE_NUMBER_ATTRIBUTE);
				else if (LAST_PAGE_NUMBER_ATTRIBUTE.equals(name) && pLastWord.getPage().hasAttribute(PAGE_NUMBER_ATTRIBUTE))
					return pLastWord.getPage().getAttribute(PAGE_NUMBER_ATTRIBUTE);
				else if (TYPE_ATTRIBUTE.equals(name))
					return pFirstWord.getTextStreamType();
				else if (ImAnnotation.FIRST_WORD_ATTRIBUTE.equals(name))
					return pFirstWord;
				else if (ImAnnotation.LAST_WORD_ATTRIBUTE.equals(name))
					return pLastWord;
				else return super.getAttribute(name);
			}
			public boolean hasAttribute(String name) {
				if (PAGE_ID_ATTRIBUTE.equals(name))
					return true;
				else if (LAST_PAGE_ID_ATTRIBUTE.equals(name))
					return (pFirstWord.pageId != pLastWord.pageId);
				else if (BLOCK_ID_ATTRIBUTE.equals(name))
					return (pFirstBlockId != null);
				else if (LAST_BLOCK_ID_ATTRIBUTE.equals(name))
					return (pLastBlockId != null);
				else if (PAGE_NUMBER_ATTRIBUTE.equals(name) && pFirstWord.getPage().hasAttribute(PAGE_NUMBER_ATTRIBUTE))
					return pFirstWord.getPage().hasAttribute(PAGE_NUMBER_ATTRIBUTE);
				else if (LAST_PAGE_NUMBER_ATTRIBUTE.equals(name) && pLastWord.getPage().hasAttribute(PAGE_NUMBER_ATTRIBUTE))
					return ((pFirstWord.pageId != pLastWord.pageId) && pLastWord.getPage().hasAttribute(PAGE_NUMBER_ATTRIBUTE));
				else if (TYPE_ATTRIBUTE.equals(name))
					return true;
				else return super.hasAttribute(name);
			}
		}
		
		private WeakHashMap subAnnotationsByType = null;
		ImAnnotationCacheEntry subAnnotationCacheGet(String type) {
			return ((this.subAnnotationsByType == null) ? null : ((ImAnnotationCacheEntry) this.subAnnotationsByType.get(type)));
		}
		void subAnnotationCachePut(String type, ImAnnotationCacheEntry ace) {
			if (this.subAnnotationsByType == null)
				this.subAnnotationsByType = new WeakHashMap();
			this.subAnnotationsByType.put(type, ace);
		}
		
		ImAnnotation aData;
		
		ImRegion rData;
		ImWord[] rDataWords;
		TableAnnotationAttributes rTableAttributes = null;
		
		ImToken tData;
		
		ImWord pFirstWord;
		ImWord pLastWord;
		String pFirstBlockId;
		String pLastBlockId;
		Attributed pAttributes;
		
		BoundingBox boundingBox = null;
		ImAnnotationBase(ImAnnotation aData) {
			this.aData = aData;
		}
		ImAnnotationBase(ImRegion rData, ImWord[] rDataWords, boolean rIsTable, boolean rIsTableHeader) {
			this.rData = rData;
			this.rDataWords = rDataWords;
			Arrays.sort(this.rDataWords, ImUtils.textStreamOrder);
			if (rIsTable)
				this.rTableAttributes = new TableAnnotationAttributes(rIsTableHeader);
		}
		ImAnnotationBase(ImToken tData) {
			this.tData = tData;
		}
		ImAnnotationBase(ImWord pfw, ImWord plw) {
			this.pFirstWord = pfw;
			this.pLastWord = plw;
			this.pAttributes = new ParagraphAnnotationAttributes();
		}
		Attributed getAttributed() {
			checkValid();
			if (TRACK_INSTANCES) accessHistory.accessed();
			if (this.aData != null)
				return this.aData;
			else if (this.rData != null)
				return this.rData;
			else if (this.tData != null)
				return this.tData;
			else return this.pAttributes;
		}
		ImWord firstWord() {
			checkValid();
			if (TRACK_INSTANCES) accessHistory.accessed();
			if (this.aData != null)
				return this.aData.getFirstWord();
			else if (this.rData != null)
				return this.rDataWords[0];
			else if (this.tData != null)
				return ((ImWord) this.tData.imWords.get(0));
			else return this.pFirstWord;
		}
		ImWord lastWord() {
			checkValid();
			if (TRACK_INSTANCES) accessHistory.accessed();
			if (this.aData != null)
				return this.aData.getLastWord();
			else if (this.rData != null)
				return this.rDataWords[this.rDataWords.length-1];
			else if (this.tData != null)
				return ((ImWord) this.tData.imWords.get(this.tData.imWords.size()-1));
			else return this.pLastWord;
		}
		String getType() {
			checkValid();
			if (TRACK_INSTANCES) accessHistory.accessed();
			if (this.aData != null)
				return this.aData.getType();
			else if (this.rData != null) {
				String type = this.rData.getType();
				if (ImRegion.TABLE_ROW_TYPE.equals(type))
					return TABLE_ROW_ANNOTATION_TYPE;
				else if (ImRegion.TABLE_CELL_TYPE.equals(type))
//					return TABLE_CELL_ANNOTATION_TYPE;
					return (((this.rTableAttributes != null) && this.rTableAttributes.isHeader) ? TABLE_HEADER_ANNOTATION_TYPE : TABLE_CELL_ANNOTATION_TYPE);
				else return type;
			}
			else if (this.tData != null)
				return WORD_ANNOTATION_TYPE;
			else return ImagingConstants.PARAGRAPH_TYPE;
		}
		String setType(String type) {
			checkValid();
			if (TRACK_INSTANCES) accessHistory.accessed();
			try {
				docListener.parentModifyingBaseDoc = true;
				String oldType = this.getType();
				if (this.aData != null)
					this.aData.setType(type);
				else if (this.rData != null)
					this.rData.setType(type);
				if (!oldType.equals(type)) {
					String oldHashId = this.hashId;
					this.hashId = null;
					if ((this.aData != null) || (this.rData != null))
						annotationTypeChanged(this, oldType, oldHashId);
				}
				return oldType;
			}
			finally {
				docListener.parentModifyingBaseDoc = false;
			}
		}
		BoundingBox getBoundingBox() {
			checkValid();
			if (TRACK_INSTANCES) accessHistory.accessed();
			if (this.boundingBox == null)
				this.boundingBox = this.buildBoundingBox();
			return ((this.boundingBox == NULL_BOUNDING_BOX) ? null : this.boundingBox);
		}
		private BoundingBox buildBoundingBox() {
			
			//	this one's trivial
			if (this.rData != null)
				return this.rData.bounds;
			
			//	collect words to wrap
			ArrayList imWordsToWrap = null;
			if (this.aData != null) {
				imWordsToWrap = new ArrayList();
				for (ImWord imw = this.aData.getFirstWord(); imw != null; imw = imw.getNextWord()) {
					imWordsToWrap.add(imw);
					if (imw == this.aData.getLastWord())
						break;
				}
			}
			else if (this.tData != null)
				imWordsToWrap = this.tData.imWords;
			else if (this.pFirstWord != null) {
				imWordsToWrap = new ArrayList();
				for (ImWord imw = this.pFirstWord; imw != null; imw = imw.getNextWord()) {
					imWordsToWrap.add(imw);
					if (imw == this.pLastWord)
						break;
				}
			}
			
			//	nothing to work with
			if (imWordsToWrap == null)
				return NULL_BOUNDING_BOX;
			
			//	build aggregate bounding box
			BoundingBox[] imWordBounds = new BoundingBox[imWordsToWrap.size()];
			for (int w = 0; w < imWordsToWrap.size(); w++) {
				imWordBounds[w] = ((ImWord) imWordsToWrap.get(w)).bounds;
				if ((w != 0) && (imWordBounds[w].left < imWordBounds[w-1].left))
					return NULL_BOUNDING_BOX;
			}
			BoundingBox aggregateImWordBounds = BoundingBox.aggregate(imWordBounds);
			return ((aggregateImWordBounds == null) ? NULL_BOUNDING_BOX : aggregateImWordBounds);
		}
		int getStartIndex() {
			checkValid();
			if (TRACK_INSTANCES) accessHistory.accessed();
//			int startIndex = getTokenIndexOf(this.firstWord());
//			if (startIndex < 0)
//				System.out.println("Strange start index for " + this.firstWord() + " in " + this.getType() + ": " + startIndex);
//			return startIndex;
			//	TODOne harden this against empty words that don't have associated tokens
//			return getTokenIndexOf(this.firstWord());
			ImWord fImw = this.firstWord();
			int fi = getTokenIndexOf(fImw);
			if (fi != -1)
				return fi;
			ImWord lImw = this.lastWord();
			for (ImWord imw = fImw.getNextWord(); imw != null; imw = imw.getNextWord()) {
				fi = getTokenIndexOf(imw);
				if (fi != -1)
					return fi;
				if (imw == lImw)
					break;
			}
			return -1;
		}
		int getEndIndex() {
			checkValid();
			if (TRACK_INSTANCES) accessHistory.accessed();
			//	TODOne harden this against empty words that don't have associated tokens
//			return (getTokenIndexOf(this.lastWord()) + 1);
			ImWord lImw = this.lastWord();
			int li = getTokenIndexOf(lImw);
			if (li != -1)
				return (li + 1);
			ImWord fImw = this.firstWord();
			for (ImWord imw = lImw.getPreviousWord(); imw != null; imw = imw.getPreviousWord()) {
				li = getTokenIndexOf(imw);
				if (li != -1)
					return (li + 1);
				if (imw == fImw)
					break;
			}
			return -1;
		}
		int size() {
			checkValid();
			if (TRACK_INSTANCES) accessHistory.accessed();
			return (this.getEndIndex() - this.getStartIndex());
		}
		int getStartOffset() {
			checkValid();
			if (TRACK_INSTANCES) accessHistory.accessed();
			//	TODOne harden this against empty words that don't have associated tokens
//			return getTokenFor(this.firstWord()).getStartOffset();
			ImWord fImw = this.firstWord();
			ImToken fTok = getTokenFor(fImw);
			if (fTok != null)
				return fTok.getStartOffset();
			ImWord lImw = this.lastWord();
			for (ImWord imw = fImw.getNextWord(); imw != null; imw = imw.getNextWord()) {
				fTok = getTokenFor(imw);
				if (fTok != null)
					return fTok.getStartOffset();
				if (imw == lImw)
					break;
			}
			return -1;
		}
		int getEndOffset() {
			checkValid();
			if (TRACK_INSTANCES) accessHistory.accessed();
			//	TODOne harden this against empty words that don't have associated tokens
//			return getTokenFor(this.lastWord()).getEndOffset();
			ImWord lImw = this.lastWord();
			ImToken lTok = getTokenFor(lImw);
			if (lTok != null)
				return lTok.getEndOffset();
			ImWord fImw = this.firstWord();
			for (ImWord imw = lImw.getPreviousWord(); imw != null; imw = imw.getPreviousWord()) {
				lTok = getTokenFor(imw);
				if (lTok != null)
					return lTok.getEndOffset();
				if (imw == fImw)
					break;
			}
			return -1;
		}
		ImToken tokenAt(int index) {
			checkValid();
			if (TRACK_INSTANCES) accessHistory.accessed();
			return imTokenAtIndex(this.getStartIndex() + index);
		}
		String valueAt(int index) {
			checkValid();
			if (TRACK_INSTANCES) accessHistory.accessed();
//			System.out.println("ImAnnotationView: getting value at " + index + " with own start index " + this.getStartIndex());
			return ImDocumentRoot.this.valueAt(this.getStartIndex() + index);
		}
		String getWhitespaceAfter(int index) {
			checkValid();
			if (TRACK_INSTANCES) accessHistory.accessed();
			return ImDocumentRoot.this.getWhitespaceAfter(this.getStartIndex() + index);
		}
		char charAt(int index) {
			checkValid();
			if (TRACK_INSTANCES) accessHistory.accessed();
			return ImDocumentRoot.this.charAt(this.getStartOffset() + index);
		}
		CharSequence subSequence(int start, int end) {
			checkValid();
			if (TRACK_INSTANCES) accessHistory.accessed();
			return ImDocumentRoot.this.subSequence((this.getStartOffset() + start), (this.getStartOffset() + end));
		}
		String getId() {
			checkValid();
			if (TRACK_INSTANCES) accessHistory.accessed();
			if (useRandomAnnotationIDs) {
				if (this.randomId == null)
					this.randomId = Gamta.getAnnotationID();
				return this.randomId;
			}
			else {
				if (this.hashId == null)
					this.hashId = this.buildHashId();
				return this.hashId;
			}
		}
//		private String buildHashId() {
//			ImWord fImw = this.firstWord();
//			ImWord lImw = this.lastWord();
//			String docId = doc.docId;
//			if (!docId.matches("[0-9A-Fa-f]++")) {
//				docId = (""
//						+ asHex(doc.docId.hashCode(), 4)
//						+ asHex(doc.docId.hashCode(), 4)
//						+ asHex(doc.docId.hashCode(), 4)
//						+ asHex(doc.docId.hashCode(), 4)
//					);
//			}
////			//	THIS IS EXTREMELY SENSITIVE TO WORD CHANGES
////			return hexXor((""
////					// 1 byte of type hash
////					+ asHex(this.getType().hashCode(), 1)
////					// 2 + 1 bytes of page IDs
////					+ asHex(fImw.pageId, 2) + asHex(lImw.pageId, 1)
////					// 2 * 6 bytes of bounding boxes (two bytes left & top, one byte right and bottom)
////					+ asHex(fImw.bounds.left, 2) + asHex(fImw.bounds.right, 1)
////					+ asHex(fImw.bounds.top, 2) + asHex(fImw.bounds.bottom, 1)
////					+ asHex(lImw.bounds.left, 2) + asHex(lImw.bounds.right, 1)
////					+ asHex(lImw.bounds.top, 2) + asHex(lImw.bounds.bottom, 1)
////				), docId);
//			//	THIS IS BETTER, AS FIRST TOP LEFT AND LAST BOTTOM RIGHT ARE JUST AS UNIQUE,
//			//	BUT ARE INSENSITIVE TO SPLITTING AND MERGING OF WORDS
//			return hexXor((""
//					// 4 byte of type hash
//					+ asHex(this.getType().hashCode(), 4)
//					// 2 + 2 bytes of page IDs
//					+ asHex(fImw.pageId, 2) + asHex(lImw.pageId, 2)
//					// 2 * 4 bytes of bounding boxes (left top of first word, right bottom of last word)
//					+ asHex(fImw.bounds.left, 2)
//					+ asHex(fImw.bounds.top, 2)
//					+ asHex(lImw.bounds.right, 2)
//					+ asHex(lImw.bounds.bottom, 2)
//				), docId);
//			//	THIS DOES NOT ALLOW DUPLICATE ANNOTATIONS !!!
//			//	BUT THEN, IS THAT A BAD THING IN EXPORT MODE?
//			/* TODO_ make selection of points dependent on document orientation
//			 * - top-left + bottom-right in left-right + top-down orientation (also top-down + left-right, like in Chinese)
//			 * - top-right + bottom-left in right-left + top-down orientation (like in Hebrew and Arabic)
//			 * - and so forth ...
//			 */
//		}
		private String buildHashId() {
			if (this.aData != null)
				return this.aData.getUUID();
			else if (this.rData != null)
				return this.rData.getUUID();
			else {
				ImWord fImw = this.firstWord();
				ImWord lImw = this.lastWord();
				return RandomByteSource.getHexXor(UuidHelper.getLocalUID(this.getType(), fImw.pageId, lImw.pageId, fImw.bounds.left, fImw.bounds.top, lImw.bounds.right, lImw.bounds.bottom), doc.docId);
			}
		}
		private String hashId = null;
		private String randomId = null;
	}
	
	private abstract class ImAnnotationView implements QueriableAnnotation {
		ImAnnotationBase base;
		ImAnnotationBase sourceBase;
		ImAnnotationView(ImAnnotationBase base, ImAnnotationBase sourceBase) {
			this.base = base;
			this.sourceBase = sourceBase;
		}
		public int getStartOffset() {
			return (this.base.getStartOffset() - ((this.sourceBase == null) ? 0 : this.sourceBase.getStartOffset()));
		}
		public int getEndOffset() {
			return (this.base.getEndOffset() - ((this.sourceBase == null) ? 0 : this.sourceBase.getStartOffset()));
		}
		
		public int getStartIndex() {
			return (this.base.getStartIndex() - ((this.sourceBase == null) ? 0 : this.sourceBase.getStartIndex()));
		}
		public int getEndIndex() {
			return (this.base.getEndIndex() - ((this.sourceBase == null) ? 0 : this.sourceBase.getStartIndex()));
		}
		public String getType() {
			return this.base.getType();
		}
		public String changeTypeTo(String newType) {
			return this.base.setType(newType);
		}
		public String getAnnotationID() {
			return this.base.getId();
		}
		public String getValue() {
			return TokenSequenceUtils.concatTokens(this, false, false);
		}
		public String toString() {
			return this.getValue();
		}
		public String toXML() {
			return (AnnotationUtils.produceStartTag(this, true) + AnnotationUtils.escapeForXml(TokenSequenceUtils.concatTokens(this, true, true)) + AnnotationUtils.produceEndTag(this));
		}
		public QueriableAnnotation getDocument() {
			checkValid();
			if (TRACK_INSTANCES) accessHistory.accessed();
			return ImDocumentRoot.this.getDocument();
		}
		
		public int getAbsoluteStartIndex() {
			return this.base.getStartIndex();
		}
		public int getAbsoluteStartOffset() {
			return this.base.getStartOffset();
		}
		public QueriableAnnotation getAnnotation(String id) {
			return doGetAnnotation(this.base, id, 'Q');
		}
		public QueriableAnnotation[] getAnnotations() {
			return doGetAnnotations(this.base, 'Q');
		}
		public QueriableAnnotation[] getAnnotations(String type) {
			return doGetAnnotations(type, this.base, 'Q');
		}
		public QueriableAnnotation[] getAnnotationsSpanning(int startIndex, int endIndex) {
			return doGetAnnotations(this.base, startIndex, endIndex, 'Q');
		}
		public QueriableAnnotation[] getAnnotationsSpanning(String type, int startIndex, int endIndex) {
			return doGetAnnotations(type, this.base, startIndex, endIndex, 'Q');
		}
		public QueriableAnnotation[] getAnnotationsOverlapping(int startIndex, int endIndex) {
			return doGetAnnotations(this.base, (endIndex - 1), (startIndex + 1), 'Q');
		}
		public QueriableAnnotation[] getAnnotationsOverlapping(String type, int startIndex, int endIndex) {
			return doGetAnnotations(type, this.base, (endIndex - 1), (startIndex + 1), 'Q');
		}
		public String[] getAnnotationTypes() {
			TreeSet annotationTypes = new TreeSet();
			Annotation[] annotations = this.getAnnotations();
			for (int a = 0; a < annotations.length; a++)
				annotationTypes.add(annotations[a].getType());
			return ((String[]) annotationTypes.toArray(new String[annotationTypes.size()]));
		}
		public String getAnnotationNestingOrder() {
			return null;
		}
		
		public boolean hasAttribute(String name) {
			if (name == null)
				return false;
			else if (PAGE_ID_ATTRIBUTE.equals(name))
				return true;
			else if (LAST_PAGE_ID_ATTRIBUTE.equals(name))
				return true;
			else if (BLOCK_ID_ATTRIBUTE.equals(name))
				return ((this.base.pFirstBlockId != null) || this.base.getAttributed().hasAttribute(name));
			else if (LAST_BLOCK_ID_ATTRIBUTE.equals(name))
				return ((this.base.pLastBlockId != null) || this.base.getAttributed().hasAttribute(name));
			else if (BOUNDING_BOX_ATTRIBUTE.equals(name))
				return (this.base.getBoundingBox() != null);
			else if (PAGE_NUMBER_ATTRIBUTE.equals(name))
				return (this.base.getAttributed().hasAttribute(PAGE_NUMBER_ATTRIBUTE) || this.base.firstWord().getPage().hasAttribute(PAGE_NUMBER_ATTRIBUTE));
			else if (LAST_PAGE_NUMBER_ATTRIBUTE.equals(name))
				return (this.base.getAttributed().hasAttribute(LAST_PAGE_NUMBER_ATTRIBUTE) || this.base.lastWord().getPage().hasAttribute(PAGE_NUMBER_ATTRIBUTE));
			else if ((this.base.rTableAttributes != null) && this.base.rTableAttributes.hasAttribute(name))
				return true;
			else return this.base.getAttributed().hasAttribute(name);
		}
		public Object getAttribute(String name) {
			return this.getAttribute(name, null);
		}
		public Object getAttribute(String name, Object def) {
			if (name == null)
				return def;
			else if (PAGE_ID_ATTRIBUTE.equals(name))
				return ("" + this.base.firstWord().getPage().pageId);
			else if (LAST_PAGE_ID_ATTRIBUTE.equals(name))
				return ("" + this.base.lastWord().getPage().pageId);
			else if (BLOCK_ID_ATTRIBUTE.equals(name) && (this.base.pFirstBlockId != null))
				return this.base.pFirstBlockId;
			else if (LAST_BLOCK_ID_ATTRIBUTE.equals(name) && (this.base.pLastBlockId != null))
				return this.base.pLastBlockId;
			else if (BOUNDING_BOX_ATTRIBUTE.equals(name)) {
				BoundingBox bb  = this.base.getBoundingBox();
				return ((bb == null) ? def : bb.toString());
			}
			else if (PAGE_NUMBER_ATTRIBUTE.equals(name) && !this.base.getAttributed().hasAttribute(name) && this.base.firstWord().getPage().hasAttribute(PAGE_NUMBER_ATTRIBUTE))
				return ("" + this.base.firstWord().getPage().getAttribute(PAGE_NUMBER_ATTRIBUTE));
			else if (LAST_PAGE_NUMBER_ATTRIBUTE.equals(name) && !this.base.getAttributed().hasAttribute(name) && this.base.lastWord().getPage().hasAttribute(PAGE_NUMBER_ATTRIBUTE))
				return ("" + this.base.lastWord().getPage().getAttribute(PAGE_NUMBER_ATTRIBUTE));
			else if (ANNOTATION_ID_ATTRIBUTE.equals(name))
				return this.getAnnotationID();
			else if (this.base.rTableAttributes != null)
				return this.base.rTableAttributes.getAttribute(name, def);
			else return this.base.getAttributed().getAttribute(name, def);
		}
		public String[] getAttributeNames() {
			TreeSet ans = new TreeSet(Arrays.asList(this.base.getAttributed().getAttributeNames()));
			ans.add(PAGE_ID_ATTRIBUTE);
			if (this.base.firstWord().getPage().hasAttribute(PAGE_NUMBER_ATTRIBUTE))
				ans.add(PAGE_NUMBER_ATTRIBUTE);
			if (this.base.firstWord().getPage().pageId != this.base.lastWord().getPage().pageId) {
				ans.add(LAST_PAGE_ID_ATTRIBUTE);
				if (this.base.lastWord().getPage().hasAttribute(PAGE_NUMBER_ATTRIBUTE))
					ans.add(LAST_PAGE_NUMBER_ATTRIBUTE);
			}
			if (this.base.pFirstBlockId != null)
				ans.add(BLOCK_ID_ATTRIBUTE);
			if ((this.base.pLastBlockId != null) && !this.base.pLastBlockId.equals(this.base.pFirstBlockId))
				ans.add(LAST_BLOCK_ID_ATTRIBUTE);
			if (this.base.getBoundingBox() != null)
				ans.add(BOUNDING_BOX_ATTRIBUTE);
			if (this.base.rTableAttributes != null)
				this.base.rTableAttributes.addAttributeNames(ans);
			return ((String[]) ans.toArray(new String[ans.size()]));
		}
		public void setAttribute(String name) {
			if (ANNOTATION_ID_ATTRIBUTE.equals(name))
				return; // need to catch this here, must not go through to underlying document
			if ((this.base.rTableAttributes != null) && this.base.rTableAttributes.hasAttribute(name))
				return;
			this.base.getAttributed().setAttribute(name);
		}
		public Object setAttribute(String name, Object value) {
			if (ANNOTATION_ID_ATTRIBUTE.equals(name))
				return this.getAnnotationID(); // need to catch this here, must not go through to underlying document
			else if ((this.base.rTableAttributes != null) && this.base.rTableAttributes.hasAttribute(name))
				return this.base.rTableAttributes.getAttribute(name, null);
			else return this.base.getAttributed().setAttribute(name, value);
		}
		public void copyAttributes(Attributed source) {
			this.base.getAttributed().copyAttributes(source);
		}
		public Object removeAttribute(String name) {
			if (ANNOTATION_ID_ATTRIBUTE.equals(name))
				return this.getAnnotationID(); // need to catch this here, must not go through to underlying document
			else return this.base.getAttributed().removeAttribute(name);
		}
		public void clearAttributes() {
			this.base.getAttributed().clearAttributes();
		}
		
		public String getDocumentProperty(String propertyName) {
			return ImDocumentRoot.this.getDocumentProperty(propertyName);
		}
		public String getDocumentProperty(String propertyName, String defaultValue) {
			return ImDocumentRoot.this.getDocumentProperty(propertyName, defaultValue);
		}
		public String[] getDocumentPropertyNames() {
			return ImDocumentRoot.this.getDocumentPropertyNames();
		}
		
		public int length() {
			return (this.base.getEndOffset() - this.base.getStartOffset());
		}
		public char charAt(int index) {
			return this.base.charAt(index);
		}
		public CharSequence subSequence(int start, int end) {
			return this.base.subSequence(start, end);
		}
		
		public Token tokenAt(int index) {
			ImToken imt = this.base.tokenAt(index);
			return new ImTokenView(imt, this.base);
		}
		public Token firstToken() {
			return this.tokenAt(0);
		}
		public Token lastToken() {
			return this.tokenAt(this.size()-1);
		}
		public String valueAt(int index) {
//			System.out.println("ImAnnotationView: getting value at " + index);
			return this.base.valueAt(index);
		}
		public String firstValue() {
			return this.base.valueAt(0);
		}
		public String lastValue() {
			return this.base.valueAt(this.size()-1);
		}
		public String getLeadingWhitespace() {
			return "";
		}
		public String getWhitespaceAfter(int index) {
			return this.base.getWhitespaceAfter(index);
		}
		public int size() {
			return this.base.size();
		}
		public TokenSequence getSubsequence(int start, int size) {
			return ImDocumentRoot.this.getMutableSubsequence((this.base.getStartIndex() + start), size);
		}
		public Tokenizer getTokenizer() {
			return ImDocumentRoot.this.getTokenizer();
		}
		
		public int compareTo(Object obj) {
			if (obj instanceof Annotation)
				return AnnotationUtils.compare(this, ((Annotation) obj));
			else return -1;
		}
	}
	
	private class ImQueriableAnnotationView extends ImAnnotationView {
		ImQueriableAnnotationView(ImAnnotationBase base, ImAnnotationBase sourceBase) {
			super(base, sourceBase);
		}
	}
	
	private abstract class ImAnnotatableAnnotationView extends ImAnnotationView implements EditableAnnotation {
		ImAnnotatableAnnotationView(ImAnnotationBase base, ImAnnotationBase sourceBase) {
			super(base, sourceBase);
		}
		public EditableAnnotation addAnnotation(Annotation annotation) {
			EditableAnnotation annot = this.addAnnotation(annotation.getType(), annotation.getStartIndex(), annotation.size());
			annot.copyAttributes(annotation);
			return annot;
		}
		public EditableAnnotation addAnnotation(String type, int startIndex, int size) {
			return doAddAnnotation(type, startIndex, (startIndex + size), this.base);
		}
		public EditableAnnotation addAnnotation(int startIndex, int endIndex, String type) {
			return doAddAnnotation(type, startIndex, endIndex, this.base);
		}
		public Annotation removeAnnotation(Annotation annotation) {
			return ImDocumentRoot.this.removeAnnotation(annotation);
		}
		public EditableAnnotation getEditableAnnotation(String id) {
			return ((EditableAnnotation) doGetAnnotation(this.base, id, 'E'));
		}
		public EditableAnnotation[] getEditableAnnotations() {
			return ((EditableAnnotation[]) doGetAnnotations(this.base, 'E'));
		}
		public EditableAnnotation[] getEditableAnnotations(String type) {
			return ((EditableAnnotation[]) doGetAnnotations(type, this.base, 'E'));
		}
		public EditableAnnotation[] getEditableAnnotationsSpanning(int startIndex, int endIndex) {
			return ((EditableAnnotation[]) doGetAnnotations(this.base, startIndex, endIndex, 'E'));
		}
		public EditableAnnotation[] getEditableAnnotationsSpanning(String type, int startIndex, int endIndex) {
			return ((EditableAnnotation[]) doGetAnnotations(type, this.base, startIndex, endIndex, 'E'));
		}
		public EditableAnnotation[] getEditableAnnotationsOverlapping(int startIndex, int endIndex) {
			return ((EditableAnnotation[]) doGetAnnotations(this.base, (endIndex - 1), (startIndex + 1), 'E'));
		}
		public EditableAnnotation[] getEditableAnnotationsOverlapping(String type, int startIndex, int endIndex) {
			return ((EditableAnnotation[]) doGetAnnotations(type, this.base, (endIndex - 1), (startIndex + 1), 'E'));
		}
		public void addAnnotationListener(AnnotationListener al) {/* no use listening on a short-lived wrapper */}
		public void removeAnnotationListener(AnnotationListener al) {/* no use listening on a short-lived wrapper */}
	}
	
	private class ImEditableAnnotationView extends ImAnnotatableAnnotationView {
		ImEditableAnnotationView(ImAnnotationBase base, ImAnnotationBase sourceBase) {
			super(base, sourceBase);
		}
	}
	
	private class ImMutableAnnotationView extends ImAnnotatableAnnotationView implements MutableAnnotation {
		ImMutableAnnotationView(ImAnnotationBase base, ImAnnotationBase sourceBase) {
			super(base, sourceBase);
		}
		
		public MutableAnnotation addAnnotation(Annotation annotation) {
			MutableAnnotation annot = this.addAnnotation(annotation.getType(), annotation.getStartIndex(), annotation.size());
			annot.copyAttributes(annotation);
			return annot;
		}
		public MutableAnnotation addAnnotation(String type, int startIndex, int size) {
			return doAddAnnotation(type, startIndex, (startIndex + size), this.base);
		}
		public MutableAnnotation addAnnotation(int startIndex, int endIndex, String type) {
			return doAddAnnotation(type, startIndex, endIndex, this.base);
		}
		public TokenSequence removeTokens(Annotation annotation) {
			return this.removeTokensAt(annotation.getStartIndex(), annotation.size());
		}
		public MutableAnnotation getMutableAnnotation(String id) {
			return ((MutableAnnotation) doGetAnnotation(this.base, id, 'M'));
		}
		public MutableAnnotation[] getMutableAnnotations() {
			return ((MutableAnnotation[]) doGetAnnotations(this.base, 'M'));
		}
		public MutableAnnotation[] getMutableAnnotations(String type) {
			return ((MutableAnnotation[]) doGetAnnotations(type, this.base, 'M'));
		}
		public MutableAnnotation[] getMutableAnnotationsSpanning(int startIndex, int endIndex) {
			return ((MutableAnnotation[]) doGetAnnotations(this.base, startIndex, endIndex, 'M'));
		}
		public MutableAnnotation[] getMutableAnnotationsSpanning(String type, int startIndex, int endIndex) {
			return ((MutableAnnotation[]) doGetAnnotations(type, this.base, startIndex, endIndex, 'M'));
		}
		public MutableAnnotation[] getMutableAnnotationsOverlapping(int startIndex, int endIndex) {
			return ((MutableAnnotation[]) doGetAnnotations(this.base, (endIndex - 1), (startIndex + 1), 'M'));
		}
		public MutableAnnotation[] getMutableAnnotationsOverlapping(String type, int startIndex, int endIndex) {
			return ((MutableAnnotation[]) doGetAnnotations(type, this.base, (endIndex - 1), (startIndex + 1), 'M'));
		}
		
		public void addChar(char ch) {
			ImDocumentRoot.this.insertChar(ch, this.base.getEndOffset());
		}
		public void addChars(CharSequence chars) {
			ImDocumentRoot.this.insertChars(chars, this.base.getEndOffset());
		}
		public void insertChar(char ch, int offset) {
			ImDocumentRoot.this.insertChar(ch, (this.base.getStartOffset() + offset));
		}
		public void insertChars(CharSequence chars, int offset) {
			ImDocumentRoot.this.insertChars(chars, (this.base.getStartOffset() + offset));
		}
		public char removeChar(int offset) {
			return ImDocumentRoot.this.removeChar(this.base.getStartOffset() + offset);
		}
		public CharSequence removeChars(int offset, int length) {
			return ImDocumentRoot.this.removeChars((this.base.getStartIndex() + offset), length);
		}
		public char setChar(char ch, int offset) {
			return ImDocumentRoot.this.setChar(ch, (this.base.getStartOffset() + offset));
		}
		public CharSequence setChars(CharSequence chars, int offset, int length) {
			return ImDocumentRoot.this.setChars(chars, (this.base.getStartOffset() + offset), length);
		}
		public MutableCharSequence mutableSubSequence(int start, int end) {
			return ImDocumentRoot.this.mutableSubSequence((this.base.getStartOffset() + start), (this.base.getStartOffset() + end));
		}
		public void addCharSequenceListener(CharSequenceListener csl) {/* no use listening on a short-lived wrapper */}
		public void removeCharSequenceListener(CharSequenceListener csl) {/* no use listening on a short-lived wrapper */}
		
		public CharSequence setLeadingWhitespace(CharSequence whitespace) throws IllegalArgumentException {
			return ((this.base.getStartIndex() == 0) ? ImDocumentRoot.this.setLeadingWhitespace(whitespace) : ImDocumentRoot.this.setWhitespaceAfter(whitespace, (this.base.getStartIndex()-1)));
		}
		public CharSequence setWhitespaceAfter(CharSequence whitespace, int index) throws IllegalArgumentException {
			return ImDocumentRoot.this.setWhitespaceAfter(whitespace, (this.base.getStartIndex() + index));
		}
		public CharSequence setValueAt(CharSequence value, int index) throws IllegalArgumentException {
			return ImDocumentRoot.this.setValueAt(value, (this.base.getStartIndex() + index));
		}
		public TokenSequence removeTokensAt(int index, int size) {
			return ImDocumentRoot.this.removeTokensAt((this.base.getStartIndex() + index), size);
		}
		public CharSequence insertTokensAt(CharSequence tokens, int index) {
			return ImDocumentRoot.this.insertTokensAt(tokens, (this.base.getStartIndex() + index));
		}
		public CharSequence addTokens(CharSequence tokens) {
			return ImDocumentRoot.this.insertTokensAt(tokens, this.getEndIndex());
		}
		public void clear() {
			ImDocumentRoot.this.removeTokensAt(this.base.getStartIndex(), this.size());
		}
		public MutableTokenSequence getMutableSubsequence(int start, int size) {
			return ImDocumentRoot.this.getMutableSubsequence((this.base.getStartIndex() + start), size);
		}
		public void addTokenSequenceListener(TokenSequenceListener tsl) {/* no use listening on a short-lived wrapper */}
		public void removeTokenSequenceListener(TokenSequenceListener tsl) {/* no use listening on a short-lived wrapper */}
	}
	
	class ImTokenView implements Token {
		ImToken base;
		ImAnnotationBase source;
		ImTokenView(ImToken base, ImAnnotationBase source) {
			this.base = base;
			this.source = source;
		}
		
		public boolean hasAttribute(String name) {
			return this.base.hasAttribute(name);
		}
		public Object getAttribute(String name) {
			return this.base.getAttribute(name);
		}
		public Object getAttribute(String name, Object def) {
			return this.base.getAttribute(name, def);
		}
		public String[] getAttributeNames() {
			return this.base.getAttributeNames();
		}
		public void setAttribute(String name) {
			this.base.setAttribute(name);
		}
		public Object setAttribute(String name, Object value) {
			return this.base.setAttribute(name, value);
		}
		public void copyAttributes(Attributed source) {
			this.base.copyAttributes(source);
		}
		public Object removeAttribute(String name) {
			return this.base.removeAttribute(name);
		}
		public void clearAttributes() {
			this.base.clearAttributes();
		}
		
		public int getStartOffset() {
			return (this.base.getStartOffset() - ((this.source == null) ? 0 : this.source.getStartOffset()));
		}
		public int getEndOffset() {
			return (this.base.getEndOffset() - ((this.source == null) ? 0 : this.source.getStartOffset()));
		}
		
		public int length() {
			return this.base.length();
		}
		public char charAt(int index) {
			return this.base.charAt(index);
		}
		public CharSequence subSequence(int start, int end) {
			return this.base.subSequence(start, end);
		}
		
		public String getValue() {
			return this.base.getValue();
		}
		public Tokenizer getTokenizer() {
			return ImDocumentRoot.this.getTokenizer();
		}
	}
	
	/** constant for specifying whether or not to filter out tables */
	public static final int EXCLUDE_TABLES = 8;
	
	/** constant for specifying whether or not to filter out captions and footnotes */
	public static final int EXCLUDE_CAPTIONS_AND_FOOTNOTES = 16;
	
	/** constant for specifying whether or not to include tables */
	public static final int SHOW_TOKENS_AS_WORD_ANNOTATIONS = 32;
	
	/** constant for specifying whether or not to include tables
	 * @deprecated looping through UUIDs of underlying objects instead now */
	public static final int USE_RANDOM_ANNOTATION_IDS = 64;
	
	/** constant for specifying whether or not to include page titles in paragraph or stream normalized mode */
	public static final int INCLUDE_PAGE_TITLES = 128;
	
	/** constant for specifying whether or not to include image or graphics labels in paragraph or stream normalized mode */
	public static final int INCLUDE_LABELS = 256;
	
	/** constant for specifying whether or not to include artifacts in paragraph or stream normalized mode */
	public static final int INCLUDE_ARTIFACTS = 512;
	
	private class ImAnnotationList {
		/* Handling our own array saves lots of method calls to ArrayList,
		 * enables more efficient single-pass cleanup (without shifting the
		 * array by one for each removed element), and and saves allocating
		 * many new arrays (mainly for sorting, as Collections.sort() uses
		 * toArray() internally). */
		private ImAnnotationBase[] annots = new ImAnnotationBase[16];
		private int annotCount = 0;
		private HashSet contained = new HashSet();
		private HashSet removed = new HashSet();
		int modCount = 0; // used by cache entry
		private int addCount = 0;
		private int cleanAddCount = 0;
		private int typeModCount = 0;
		private int cleanTypeModCount = 0;
		private int cleanOrderModCount = orderModCount;
		private final String type;
		
		private int maxAnnotSize = 0;
		
		ImAnnotationList(String type) {
			this.type = type;
		}
		void addAnnotation(ImAnnotationBase imab) {
			if (imab == null)
				return;
			if (this.contained.contains(imab))
				return;
			this.contained.add(imab);
			if (this.removed.remove(imab)) {
				this.modCount++;
				if (this.annotCount != (this.contained.size() + this.removed.size()))
					System.out.println("FUCK, array " + this.annotCount + " != (contained " + this.contained.size() + " + removed " + this.removed.size() + ") on adding " + imab.getType());
				return; // this one is was flagged for removal, but still in the array, no need to add to again (also, addition has been counted before)
			}
			if (this.annotCount == this.annots.length) {
				ImAnnotationBase[] annots = new ImAnnotationBase[this.annots.length * 2];
				System.arraycopy(this.annots, 0, annots, 0, this.annots.length);
				this.annots = annots;
			}
			this.annots[this.annotCount++] = imab;
			if (this.maxAnnotSize < imab.size())
				this.maxAnnotSize = imab.size();
			this.modCount++;
			this.addCount++;
			if (this.annotCount != (this.contained.size() + this.removed.size()))
				System.out.println("FUCK, array " + this.annotCount + " != (contained " + this.contained.size() + " + removed " + this.removed.size() + ") on adding " + imab.getType());
		}
		void removeAnnotation(ImAnnotationBase imab) {
			if (imab == null)
				return;
			if (this.contained.remove(imab)) {
				this.removed.add(imab);
				this.modCount++;
			}
			if (this.annotCount != (this.contained.size() + this.removed.size()))
				System.out.println("FUCK, array " + this.annotCount + " != (contained " + this.contained.size() + " + removed " + this.removed.size() + ") on removing " + imab.getType());
		}
		ImAnnotationBase getAnnotation(int index) {
			this.ensureSorted();
			return this.annots[index];
		}
		boolean isEmpty() {
			return this.contained.isEmpty();
		}
		int size() {
			return this.contained.size();
		}
		ImAnnotationBase[] getAnnotations(int maxAbsoluteStartIndex, int minAbsoluteEndIndex) {
			//	no use caching ranges, way too little chance of cache hits
			int minAbsoluteStartIndex = Math.max(0, (minAbsoluteEndIndex - this.maxAnnotSize));
			int maxAbsoluteEndIndex = Math.min(ImDocumentRoot.this.size(), (maxAbsoluteStartIndex + this.maxAnnotSize));
			return this.getAnnotationsIn(minAbsoluteStartIndex, maxAbsoluteStartIndex, minAbsoluteEndIndex, maxAbsoluteEndIndex);
		}
		ImAnnotationBase[] getAnnotationsIn(ImAnnotationBase base) {
			ImAnnotationCacheEntry annots = base.subAnnotationCacheGet(this.type);
			if ((annots == null) || annots.isInvalid(this)) /* cache miss, or entry stale */ {
				ImAnnotationBase[] imabs = this.getAnnotationsIn(base.getStartIndex(), (base.getEndIndex()-1), (base.getStartIndex()+1), base.getEndIndex());
				annots = new ImAnnotationCacheEntry(imabs, this);
				base.subAnnotationCachePut(this.type, annots);
			}
			return annots.annotations;
		}
		ImAnnotationBase[] getAnnotationsIn(ImAnnotationBase base, int maxRelativeStartIndex, int minRelativeEndIndex) {
			
			//	get all contained annotations (no use caching ranges, way too little chance of cache hits)
			ImAnnotationBase[] allAnnots = this.getAnnotationsIn(base);
			
			//	make indexes absolute
			int maxAbsoluteStartIndex = (base.getStartIndex() + maxRelativeStartIndex);
			int minAbsoluteEndIndex = (base.getStartIndex() + minRelativeEndIndex);
			
			//	get qualifying annotations
			ArrayList annotList = new ArrayList();
			for (int a = 0; a < allAnnots.length; a++) {
				if (maxAbsoluteStartIndex < allAnnots[a].getStartIndex())
					break;
				if (minAbsoluteEndIndex <= allAnnots[a].getEndIndex())
					annotList.add(allAnnots[a]);
			}
			return ((ImAnnotationBase[]) annotList.toArray(new ImAnnotationBase[annotList.size()]));
		}
		private ImAnnotationBase[] getAnnotationsIn(int minAbsoluteStartIndex, int maxAbsoluteStartIndex, int minAbsoluteEndIndex, int maxAbsoluteEndIndex) {
			
			//	make sure we're good to go
			this.ensureSorted();
			
			//	binary search first annotation
			int left = 0;
			int right = this.annotCount;
			
			//	narrow staring point with binary search down to a 2 interval
			int start = -1;
			for (int c; (start == -1) && ((right - left) > 2);) {
				int middle = ((left + right) / 2);
				
				//	start linear search if interval down to 4
				if ((right - left) < 4)
					c = 0;
				else c = (this.annots[middle].getStartIndex() - minAbsoluteStartIndex);
				
				if (c < 0)
					left = middle; // starting point is right of middle
				else if (c == 0) { // start of Annotation at middle is equal to base start of base, scan leftward for others at same start
					start = middle;
					while ((start != 0) && (minAbsoluteStartIndex <= this.annots[start].getStartIndex()))
						start --; // count down to 0 at most
				}
				else right = middle; // starting point is left of middle
			}
			
			//	ensure valid index
			start = Math.max(start, 0);
			
			//	move right to exact staring point
			while ((start < this.annotCount) && (this.annots[start].getStartIndex() < minAbsoluteStartIndex))
				start++;
			
			//	move left to exact staring point
			while ((start != 0) && (minAbsoluteStartIndex <= this.annots[start-1].getStartIndex()))
				start--;
			
			//	collect and return matching annotations
			ArrayList annotList = new ArrayList();
			for (int a = start; a < this.annotCount; a++) {
				if (maxAbsoluteStartIndex < this.annots[a].getStartIndex()) // to right of last potential match
					break;
				if ((minAbsoluteEndIndex <= this.annots[a].getEndIndex()) && (this.annots[a].getEndIndex() <= maxAbsoluteEndIndex)) // end index in range, we have a match
					annotList.add(this.annots[a]);
			}
			return ((ImAnnotationBase[]) annotList.toArray(new ImAnnotationBase[annotList.size()]));
		}
		void clear() {
			Arrays.fill(this.annots, 0, this.annotCount, null); // free up references to help GC
			this.annotCount = 0;
			this.contained.clear();
			this.removed.clear();
			this.modCount++;
		}
		private void ensureSorted() {
			this.ensureClean();
			if ((this.cleanAddCount == this.addCount) && (this.cleanTypeModCount == this.typeModCount) && (this.cleanOrderModCount == orderModCount))
				return;
			/* TODOnot if order and types unmodified, we can even save sorting the whole list:
			 * - sort only added annotations ...
			 * - ... and then merge them into main list in single pass
			 * ==> but then, TimSort already does pretty much that ... */
			Arrays.sort(this.annots, 0, this.annotCount, ((this.type == null) ? typedAnnotationBaseOrder : annotationBaseOrder));
			this.cleanAddCount = this.addCount;
			this.cleanTypeModCount = this.typeModCount;
			this.cleanOrderModCount = orderModCount;
		}
		private void ensureClean() {
			if (this.removed.isEmpty())
				return;
			int removed = 0;
			int maxAnnotSize = 0;
			for (int a = 0; a < this.annotCount; a++) {
				if (this.removed.contains(this.annots[a]))
					removed++;
				else {
					if (maxAnnotSize < this.annots[a].size())
						maxAnnotSize = this.annots[a].size();
					if (removed != 0)
						this.annots[a - removed] = this.annots[a];
				}
			}
			Arrays.fill(this.annots, (this.annotCount - removed), this.annotCount, null); // free up references to help GC
			this.annotCount -= removed;
			this.maxAnnotSize = maxAnnotSize;
			this.removed.clear();
		}
	}
	
	private static class ImAnnotationCacheEntry {
		final ImAnnotationBase[] annotations;
		private final ImAnnotationList parent;
		private final int createModCount;
		ImAnnotationCacheEntry(ImAnnotationBase[] annotations, ImAnnotationList parent) {
			this.annotations = annotations;
			this.parent = parent;
			this.createModCount = this.parent.modCount;
		}
		boolean isInvalid(ImAnnotationList parent) {
			return ((parent != this.parent) || (this.createModCount != this.parent.modCount));
		}
	}
	
	private static class BaseDocumentListener extends BaseTokenSequenceListener {
		private ImDocumentRoot parent;
		boolean parentModifyingBaseDoc = false; // TODO set this below
		BaseDocumentListener(ImDocumentRoot parent, ImDocument baseDoc) {
			super(parent, baseDoc); // parent constructor adds us to document
			this.parent = parent;
		}
		public void typeChanged(ImObject object, String oldType) {
			if (this.parentModifyingBaseDoc)
				return; // parent already aware of this change
			
			//	get subject annoation or region
			ImAnnotation ima = null;
			ImRegion imr = null;
			if (object instanceof ImAnnotation)
				ima = ((ImAnnotation) object);
			else if (object instanceof ImRegion)
				imr = ((ImRegion) object);
			else return; // we only represent annotations and regions (depending upon normalization level)
			
			//	loop through to annotations if in scope
			if (ima != null)
				this.parent.annotationTypeChanged(ima, oldType);
			else if (imr != null)
				this.parent.regionTypeChanged(imr, oldType);
		}
		public void attributeChanged(ImObject object, String attributeName, Object oldValue) {
			if (object instanceof ImWord) // pertains to word relations and word chaining, handled in superclass
				super.attributeChanged(object, attributeName, oldValue);
			else if (this.parentModifyingBaseDoc)
				return; // already aware of this change
			else if (object instanceof ImAnnotation) // issue notification for annotations only
				this.parent.annotationAttributeChanged(((ImAnnotation) object), attributeName, oldValue);
			else if (object instanceof ImRegion) // issue notification for annotations only
				this.parent.regionAttributeChanged(((ImRegion) object), attributeName, oldValue);
		}
		public void regionAdded(ImRegion region) {
			if (this.parentModifyingBaseDoc)
				return; // parent already aware of this change
			if (region instanceof ImWord)
				super.regionAdded(region); // let token sequence handle word additions and removals
			else this.parent.regionAdded(region);
		}
		public void regionRemoved(ImRegion region) {
			if (this.parentModifyingBaseDoc)
				return; // parent already aware of this change
			if (region instanceof ImWord)
				super.regionRemoved(region); // let token sequence handle word additions and removals
			else this.parent.regionRemoved(region);
		}
		public void annotationAdded(ImAnnotation annotation) {
			if (this.parentModifyingBaseDoc)
				return; // parent already aware of this change
			this.parent.annotationAdded(annotation);
		}
		public void annotationRemoved(ImAnnotation annotation) {
			if (this.parentModifyingBaseDoc)
				return; // parent already aware of this change
			this.parent.annotationRemoved(annotation);
		}
//		void dispose() {
//			if (this.baseDoc != null)
//				this.baseDoc.removeDocumentListener(this.weakWrapper);
//			this.baseDoc = null;
//			this.weakWrapper = null;
//		}
	}
	
	private ImDocument doc;
	private ImAnnotation annotation;
	private ImRegion region;
	private ImWord firstWord;
	private ImWord lastWord;
	
	private int configFlags;
	private ImDocumentRoot fullDoc;
	
	private ImAnnotationList annotations = new ImAnnotationList(null);
	private TreeMap annotationsByType = new TreeMap();
	private HashMap annotationBasesByAnnotations = new HashMap();
	private HashMap annotationBasesByRegions = new HashMap();
	private HashMap annotationBasesByUUIDs = new HashMap();
	private HashMap annotationViewsByBases = new HashMap();
	
	private String annotationNestingOrder = Gamta.getAnnotationNestingOrder();
	private int orderModCount = 0;
	
	private boolean paragraphsAreLogical;
	private boolean showTokensAsWordAnnotations = false;
	
	private boolean useRandomAnnotationIDs = false;
	
	private boolean initializing = true;
	BaseDocumentListener docListener;
	
	/**
	 * Constructor wrapping a whole Image Markup document, safe for deleted
	 * logical text streams.
	 * @param doc the document to wrap
	 * @param configFlags an integer bundling configuration flags, like the
	 *            normalization level, which specifies how to merge individual
	 *            text streams
	 */
	public ImDocumentRoot(ImDocument doc, int configFlags) {
		this(doc, getTextStreamHeads(doc, configFlags), configFlags);
	}
	private static final ImWord[] getTextStreamHeads(ImDocument doc, int configFlags) {
		int normalizationLevel = (configFlags & NORMALIZATION_LEVEL_STREAMS);
		boolean excludeTables = ((configFlags & EXCLUDE_TABLES) != 0);
		boolean excludeCaptionsFootnotes = ((configFlags & EXCLUDE_CAPTIONS_AND_FOOTNOTES) != 0);
		boolean excludePageTitles = (((normalizationLevel == NORMALIZATION_LEVEL_PARAGRAPHS) || (normalizationLevel == NORMALIZATION_LEVEL_STREAMS)) && ((configFlags & INCLUDE_PAGE_TITLES) == 0));
		boolean excludeLabels = (((normalizationLevel == NORMALIZATION_LEVEL_PARAGRAPHS) || (normalizationLevel == NORMALIZATION_LEVEL_STREAMS)) && ((configFlags & INCLUDE_LABELS) == 0));
		boolean excludeArtifacts = (((normalizationLevel == NORMALIZATION_LEVEL_PARAGRAPHS) || (normalizationLevel == NORMALIZATION_LEVEL_STREAMS)) && ((configFlags & INCLUDE_ARTIFACTS) == 0));
		ImWord[] textStreamHeads = doc.getTextStreamHeads();
		LinkedList nonDeletedTextStreamHeads = new LinkedList();
		for (int h = 0; h < textStreamHeads.length; h++) {
			if (ImWord.TEXT_STREAM_TYPE_DELETED.equals(textStreamHeads[h].getTextStreamType()))
				continue;
			if (excludeTables && ImWord.TEXT_STREAM_TYPE_TABLE.equals(textStreamHeads[h].getTextStreamType()))
				continue;
			if (excludeCaptionsFootnotes && (ImWord.TEXT_STREAM_TYPE_CAPTION.equals(textStreamHeads[h].getTextStreamType()) || ImWord.TEXT_STREAM_TYPE_FOOTNOTE.equals(textStreamHeads[h].getTextStreamType()) || ImWord.TEXT_STREAM_TYPE_TABLE_NOTE.equals(textStreamHeads[h].getTextStreamType())))
				continue;
//			if (((normalizationLevel == NORMALIZATION_LEVEL_PARAGRAPHS) || (normalizationLevel == NORMALIZATION_LEVEL_STREAMS)) && (ImWord.TEXT_STREAM_TYPE_PAGE_TITLE.equals(textStreamHeads[h].getTextStreamType()) || ImWord.TEXT_STREAM_TYPE_LABEL.equals(textStreamHeads[h].getTextStreamType()) || ImWord.TEXT_STREAM_TYPE_ARTIFACT.equals(textStreamHeads[h].getTextStreamType())))
//				continue;
			if (excludePageTitles && ImWord.TEXT_STREAM_TYPE_PAGE_TITLE.equals(textStreamHeads[h].getTextStreamType()))
				continue;
			if (excludeLabels && ImWord.TEXT_STREAM_TYPE_LABEL.equals(textStreamHeads[h].getTextStreamType()))
				continue;
			if (excludeArtifacts && ImWord.TEXT_STREAM_TYPE_ARTIFACT.equals(textStreamHeads[h].getTextStreamType()))
				continue;
			nonDeletedTextStreamHeads.addLast(textStreamHeads[h]);
		}
		return ((textStreamHeads.length == nonDeletedTextStreamHeads.size()) ? textStreamHeads : ((ImWord[]) nonDeletedTextStreamHeads.toArray(new ImWord[nonDeletedTextStreamHeads.size()])));
	}
	
	//	TODO add constructor signature taking a firstPageId and lastPageId argument
	
	/**
	 * Constructor wrapping selected logical text streams of an Image Markup
	 * document.
	 * @param doc the document to wrap
	 * @param textStreamHeads the heads of the logical text streams to include
	 *            in the wrapper token sequence 
	 * @param configFlags an integer bundling configuration flags, like the
	 *            normalization level, which specifies how to merge individual
	 *            text streams
	 */
	public ImDocumentRoot(ImDocument doc, ImWord[] textStreamHeads, int configFlags) {
//		super(((Tokenizer) doc.getAttribute(ImDocument.TOKENIZER_ATTRIBUTE, Gamta.INNER_PUNCTUATION_TOKENIZER)), textStreamHeads, configFlags);
//		super(((Tokenizer) doc.getAttribute(ImDocument.TOKENIZER_ATTRIBUTE, Gamta.getDefaultTokenizer())), textStreamHeads, configFlags);
		super(((Tokenizer) doc.getAttribute(ImDocument.TOKENIZER_ATTRIBUTE, Gamta.getDefaultTokenizer())), textStreamHeads, configFlags, false);
		this.doc = doc;
		this.configFlags = configFlags;
		this.fullDoc = this;
		
		//	set first and last word only if single text stream
		if (textStreamHeads.length == 1) {
			this.firstWord = this.firstWordOf(this.firstToken());
			this.lastWord = this.lastWordOf(this.lastToken());
		}
//		System.out.println("Got first and last word");
		
		//	read normalization level
		int normalizationLevel = (configFlags & NORMALIZATION_LEVEL_STREAMS);
		this.paragraphsAreLogical = ((normalizationLevel == NORMALIZATION_LEVEL_PARAGRAPHS) || (normalizationLevel == NORMALIZATION_LEVEL_STREAMS));
//		System.out.println("Got normalization level");
		
		//	collect text stream IDs
		HashSet textStreamIDs = new HashSet();
		for (int h = 0; h < textStreamHeads.length; h++)
			textStreamIDs.add(textStreamHeads[h].getTextStreamId());
//		System.out.println("Got text stream IDs");
		
		//	add annotation overlay for annotations
		ImAnnotation[] annotations = this.doc.getAnnotations();
		for (int a = 0; a < annotations.length; a++) {
			if (textStreamIDs.contains(annotations[a].getFirstWord().getTextStreamId()))
				this.addAnnotation(annotations[a]);
		}
//		System.out.println("Got annotations");
		
		//	get document pages
		ImPage[] pages = this.doc.getPages();
//		System.out.println("Got pages");
		
		//	add annotation overlay for regions, including pages, for low normalization levels
		if ((normalizationLevel == NORMALIZATION_LEVEL_RAW) || (normalizationLevel == NORMALIZATION_LEVEL_WORDS)) {
			for (int p = 0; p < pages.length; p++) {
				
				//	get page words
				ImWord[] pageWords = pages[p].getWords();
				ArrayList filteredPageWords = new ArrayList();
				for (int w = 0; w < pageWords.length; w++) {
					if (textStreamIDs.contains(pageWords[w].getTextStreamId()))
						filteredPageWords.add(pageWords[w]);
				}
				
				//	nothing to work with in this page
				if (filteredPageWords.isEmpty())
					continue;
				
				//	annotate page
				this.addAnnotation(pages[p], ((ImWord[]) filteredPageWords.toArray(new ImWord[filteredPageWords.size()])));
				
				//	add regions
				ImRegion[] regions = pages[p].getRegions();
				for (int r = 0; r < regions.length; r++) {
					
					//	handle tables separately
					if (ImRegion.TABLE_TYPE.equals(regions[r].getType()) || ImRegion.TABLE_ROW_TYPE.equals(regions[r].getType()) || ImRegion.TABLE_COL_TYPE.equals(regions[r].getType()) || ImRegion.TABLE_CELL_TYPE.equals(regions[r].getType()))
						continue;
					
					//	exclude lines in word normalization (they only make sense in raw physical layout)
					if (LINE_ANNOTATION_TYPE.equals(regions[r].getType()) && (normalizationLevel == NORMALIZATION_LEVEL_WORDS))
						continue;
					
					//	get region words
					ArrayList filteredRegionWords = new ArrayList();
					for (int w = 0; w < filteredPageWords.size(); w++) {
						if (regions[r].bounds.includes(((ImWord) filteredPageWords.get(w)).bounds, true))
							filteredRegionWords.add(filteredPageWords.get(w));
					}
					
					//	annotate region if not empty
					if (filteredRegionWords.size() != 0)
						this.addAnnotation(regions[r], ((ImWord[]) filteredRegionWords.toArray(new ImWord[filteredRegionWords.size()])));
				}
			}
		}
		
		//	emulate paragraphs based on word relations for high normalization levels
		else if ((normalizationLevel == NORMALIZATION_LEVEL_PARAGRAPHS) || (normalizationLevel == NORMALIZATION_LEVEL_STREAMS)) {
			ImRegion[][] pageBlocks = new ImRegion[pages.length][];
			int firstPageId = pages[0].pageId;
			for (int h = 0; h < textStreamHeads.length; h++) {
				ImWord pFirstWord = null;
				for (ImWord imw = textStreamHeads[h]; imw != null; imw = imw.getNextWord()) {
					if (pFirstWord == null)
						pFirstWord = imw;
					if ((imw.getNextRelation() == ImWord.NEXT_RELATION_PARAGRAPH_END) || (imw.getNextWord() == null)) {
						ImAnnotationBase imab = this.addAnnotation(pFirstWord, imw);
						if (pageBlocks[pFirstWord.pageId - firstPageId] == null)
							pageBlocks[pFirstWord.pageId - firstPageId] = pages[pFirstWord.pageId - firstPageId].getRegions(BLOCK_ANNOTATION_TYPE);
						imab.pFirstBlockId = getParentBlockId(pageBlocks[pFirstWord.pageId - firstPageId], pFirstWord);
						if (pageBlocks[imw.pageId - firstPageId] == null)
							pageBlocks[imw.pageId - firstPageId] = pages[imw.pageId - firstPageId].getRegions(BLOCK_ANNOTATION_TYPE);
						imab.pLastBlockId = getParentBlockId(pageBlocks[imw.pageId - firstPageId], imw);
						pFirstWord = null;
					}
				}
			}
//			System.out.println("Got paragraphs and blocks");
		}
		
		//	annotate tables (if not filtered out)
		if ((configFlags & EXCLUDE_TABLES) == 0) {
			for (int p = 0; p < pages.length; p++) {
				ImRegion[] tabels = pages[p].getRegions(TABLE_ANNOTATION_TYPE);
				for (int t = 0; t < tabels.length; t++)
					this.annotateTableStructure(tabels[t]);
			}
		}
//		System.out.println("Got tables");
		
		//	write through included configuration (annotating words makes sense only now)
		this.setShowTokensAsWordsAnnotations((configFlags & SHOW_TOKENS_AS_WORD_ANNOTATIONS) != 0);
//		System.out.println("Got word annotations");
		this.setUseRandomAnnotationIDs((configFlags & USE_RANDOM_ANNOTATION_IDS) != 0);
//		System.out.println("Got identifiers");
		
		//	track changes in underlying document
		this.finishInit();
	}
	
	/**
	 * Constructor wrapping an individual annotation.
	 * @param annotation the annotation to wrap
	 * @param configFlags an integer bundling configuration flags, like the
	 *            normalization level, which specifies how to merge individual
	 *            text streams
	 */
	public ImDocumentRoot(ImAnnotation annotation, int configFlags) {
		this(annotation.getFirstWord(), annotation.getLastWord(), configFlags);
		this.annotation = annotation;
	}
	
	/**
	 * Constructor wrapping a custom span of words. The two argument words have
	 * to belong to the same logical text stream for the wrapper to have any
	 * meaningful behavior.
	 * @param firstWord the first word to include in the wrapper token sequence
	 * @param lastWord the last word to include in the wrapper token sequence
	 * @param configFlags an integer bundling configuration flags, like the
	 *            normalization level, which specifies how to merge individual
	 *            text streams
	 */
	public ImDocumentRoot(ImWord firstWord, ImWord lastWord, int configFlags) {
//		super(((Tokenizer) firstWord.getDocument().getAttribute(ImDocument.TOKENIZER_ATTRIBUTE, Gamta.INNER_PUNCTUATION_TOKENIZER)), firstWord, lastWord, configFlags);
//		super(((Tokenizer) firstWord.getDocument().getAttribute(ImDocument.TOKENIZER_ATTRIBUTE, Gamta.getDefaultTokenizer())), firstWord, lastWord, configFlags);
		super(((Tokenizer) firstWord.getDocument().getAttribute(ImDocument.TOKENIZER_ATTRIBUTE, Gamta.getDefaultTokenizer())), firstWord, lastWord, configFlags, false);
		this.doc = firstWord.getDocument();
		this.firstWord = firstWord;
		this.lastWord = lastWord;
		this.configFlags = configFlags;
		
		//	read normalization level
		int normalizationLevel = (configFlags & NORMALIZATION_LEVEL_STREAMS);
		this.paragraphsAreLogical = ((normalizationLevel == NORMALIZATION_LEVEL_PARAGRAPHS) || (normalizationLevel == NORMALIZATION_LEVEL_STREAMS));
		
		//	add annotation overlay for annotations
		ImAnnotation[] annotations = this.doc.getAnnotations();
		for (int a = 0; a < annotations.length; a++) {
			
			//	check text stream ID
			if (!firstWord.getTextStreamId().equals(annotations[a].getFirstWord().getTextStreamId()))
				continue;
			
			//	check if first word in range
			ImWord aFirstWord = annotations[a].getFirstWord();
			if (ImUtils.textStreamOrder.compare(aFirstWord, firstWord) < 0)
				continue;
			if (ImUtils.textStreamOrder.compare(lastWord, aFirstWord) < 0)
				continue;
			
			//	check if last word in range
			ImWord aLastWord = annotations[a].getLastWord();
			if (ImUtils.textStreamOrder.compare(aLastWord, firstWord) < 0)
				continue;
			if (ImUtils.textStreamOrder.compare(lastWord, aLastWord) < 0)
				continue;
			
			//	finally, we can include this one
			this.addAnnotation(annotations[a]);
		}
		
		//	get document pages
		ImPage[] pages = this.doc.getPages();
		
		//	add annotation overlay for regions, including pages
		if ((normalizationLevel == NORMALIZATION_LEVEL_RAW) || (normalizationLevel == NORMALIZATION_LEVEL_WORDS)) {
			for (int p = 0; p < pages.length; p++) {
				
				//	check if page within range
				if (pages[p].pageId < firstWord.pageId)
					continue;
				if (lastWord.pageId < pages[p].pageId)
					break;
				
				//	get page words
				ImWord[] pageWords = pages[p].getWords();
				ArrayList filteredPageWords = new ArrayList();
				for (int w = 0; w < pageWords.length; w++) {
					
					//	check text stream ID
					if (!firstWord.getTextStreamId().equals(pageWords[w].getTextStreamId()))
						continue;
					
					//	check if word in range
					if (ImUtils.textStreamOrder.compare(pageWords[w], firstWord) < 0)
						continue;
					if (ImUtils.textStreamOrder.compare(lastWord, pageWords[w]) < 0)
						continue;
					
					//	finally, we can include this one
					filteredPageWords.add(pageWords[w]);
				}
				
				//	nothing to work with in this page
				if (filteredPageWords.isEmpty())
					continue;
				
				//	annotate page
				this.addAnnotation(pages[p], ((ImWord[]) filteredPageWords.toArray(new ImWord[filteredPageWords.size()])));
				
				//	annotate regions
				ImRegion[] regions = pages[p].getRegions();
				for (int r = 0; r < regions.length; r++) {
					
					//	handle tables separately
					if (ImRegion.TABLE_TYPE.equals(regions[r].getType()) || ImRegion.TABLE_ROW_TYPE.equals(regions[r].getType()) || ImRegion.TABLE_COL_TYPE.equals(regions[r].getType()) || ImRegion.TABLE_CELL_TYPE.equals(regions[r].getType()))
						continue;
					
					//	exclude lines in word normalization (they only make sense in raw physical layout)
					if (LINE_ANNOTATION_TYPE.equals(regions[r].getType()) && (normalizationLevel == NORMALIZATION_LEVEL_WORDS))
						continue;
					
					//	get region words
					ArrayList filteredRegionWords = new ArrayList();
					for (int w = 0; w < filteredPageWords.size(); w++) {
						if (regions[r].bounds.includes(((ImWord) filteredPageWords.get(w)).bounds, true))
							filteredRegionWords.add(filteredPageWords.get(w));
					}
					
					//	annotate region if not empty
					if (filteredRegionWords.size() != 0)
						this.addAnnotation(regions[r], ((ImWord[]) filteredRegionWords.toArray(new ImWord[filteredRegionWords.size()])));
				}
			}
		}
		
		//	emulate paragraphs based on word relations for high normalization levels
		else if ((normalizationLevel == NORMALIZATION_LEVEL_PARAGRAPHS) || (normalizationLevel == NORMALIZATION_LEVEL_STREAMS)) {
			ImRegion[][] pageBlocks = new ImRegion[pages.length][];
			int firstPageId = pages[0].pageId;
			ImWord pFirstWord = null;
			for (ImWord imw = firstWord; imw != null; imw = imw.getNextWord()) {
				if (pFirstWord == null)
					pFirstWord = imw;
				if ((imw.getNextRelation() == ImWord.NEXT_RELATION_PARAGRAPH_END) || (imw.getNextWord() == null) || (imw == lastWord)) {
					ImAnnotationBase imab = this.addAnnotation(pFirstWord, imw);
					if (pageBlocks[pFirstWord.pageId - firstPageId] == null)
						pageBlocks[pFirstWord.pageId - firstPageId] = pages[pFirstWord.pageId - firstPageId].getRegions(BLOCK_ANNOTATION_TYPE);
					imab.pFirstBlockId = getParentBlockId(pageBlocks[pFirstWord.pageId - firstPageId], pFirstWord);
					if (pageBlocks[imw.pageId - firstPageId] == null)
						pageBlocks[imw.pageId - firstPageId] = pages[imw.pageId - firstPageId].getRegions(BLOCK_ANNOTATION_TYPE);
					imab.pLastBlockId = getParentBlockId(pageBlocks[imw.pageId - firstPageId], imw);
					pFirstWord = null;
				}
				if (imw == lastWord)
					break;
			}
		}
		
		//	annotate tables
		if ((configFlags & EXCLUDE_TABLES) == 0) {
			for (int p = 0; p < pages.length; p++) {
				
				//	check if page within range
				if (pages[p].pageId < firstWord.pageId)
					continue;
				if (lastWord.pageId < pages[p].pageId)
					break;
				
				//	get page words
				ImWord[] pageWords = pages[p].getWords();
				ArrayList filteredPageWords = new ArrayList();
				for (int w = 0; w < pageWords.length; w++) {
					
					//	check text stream ID
					if (!firstWord.getTextStreamId().equals(pageWords[w].getTextStreamId()))
						continue;
					
					//	check if word in range
					if (ImUtils.textStreamOrder.compare(pageWords[w], firstWord) < 0)
						continue;
					if (ImUtils.textStreamOrder.compare(lastWord, pageWords[w]) < 0)
						continue;
					
					//	finally, we can include this one
					filteredPageWords.add(pageWords[w]);
				}
				
				//	nothing to work with in this page
				if (filteredPageWords.isEmpty())
					continue;
				
				//	finally ...
				ImRegion[] tables = pages[p].getRegions(TABLE_ANNOTATION_TYPE);
				for (int t = 0; t < tables.length; t++) {
					
					//	get table words
					ArrayList filteredRegionWords = new ArrayList();
					for (int w = 0; w < filteredPageWords.size(); w++) {
						if (tables[t].bounds.includes(((ImWord) filteredPageWords.get(w)).bounds, true))
							filteredRegionWords.add(filteredPageWords.get(w));
					}
					
					//	annotate table only if not empty
					if (filteredRegionWords.size() != 0)
						this.annotateTableStructure(tables[t]);
				}
			}
		}
		
		//	write through included configuration (annotating words makes sense only now)
		this.setShowTokensAsWordsAnnotations((configFlags & SHOW_TOKENS_AS_WORD_ANNOTATIONS) != 0);
		this.setUseRandomAnnotationIDs((configFlags & USE_RANDOM_ANNOTATION_IDS) != 0);
		
		//	track changes in underlying document
		this.finishInit();
	}
	
	/**
	 * Constructor wrapping an individual region or page of an Image Markup
	 * document, safe for deleted logical text streams.
	 * @param region the region to wrap
	 * @param configFlags an integer bundling configuration flags, like the
	 *            normalization level, which specifies how to merge individual
	 *            text streams
	 */
	public ImDocumentRoot(ImRegion region, int configFlags) {
//		super(((Tokenizer) region.getDocument().getAttribute(ImDocument.TOKENIZER_ATTRIBUTE, Gamta.INNER_PUNCTUATION_TOKENIZER)));
//		super(((Tokenizer) region.getDocument().getAttribute(ImDocument.TOKENIZER_ATTRIBUTE, Gamta.getDefaultTokenizer())));
		super(((Tokenizer) region.getDocument().getAttribute(ImDocument.TOKENIZER_ATTRIBUTE, Gamta.getDefaultTokenizer())), false);
		this.doc = region.getDocument();
		this.region = region;
		this.configFlags = configFlags;
		
		//	get page
		ImPage page = region.getPage();
		if (page == null)
			page = this.doc.getPage(region.pageId);
		
		//	read normalization level
		int normalizationLevel = (configFlags & NORMALIZATION_LEVEL_STREAMS);
		this.paragraphsAreLogical = ((normalizationLevel == NORMALIZATION_LEVEL_PARAGRAPHS) || (normalizationLevel == NORMALIZATION_LEVEL_STREAMS));
		boolean excludeTables = ((configFlags & EXCLUDE_TABLES) != 0);
		boolean excludeCaptionsFootnotes = ((configFlags & EXCLUDE_CAPTIONS_AND_FOOTNOTES) != 0);
		boolean excludePageTitles = (((normalizationLevel == NORMALIZATION_LEVEL_PARAGRAPHS) || (normalizationLevel == NORMALIZATION_LEVEL_STREAMS)) && ((configFlags & INCLUDE_PAGE_TITLES) == 0));
		boolean excludeLabels = (((normalizationLevel == NORMALIZATION_LEVEL_PARAGRAPHS) || (normalizationLevel == NORMALIZATION_LEVEL_STREAMS)) && ((configFlags & INCLUDE_LABELS) == 0));
		boolean excludeArtifacts = (((normalizationLevel == NORMALIZATION_LEVEL_PARAGRAPHS) || (normalizationLevel == NORMALIZATION_LEVEL_STREAMS)) && ((configFlags & INCLUDE_ARTIFACTS) == 0));
		
		//	add words
//		ImWord[] regionWords = region.getWords();
		ImWord[] regionWords = page.getWordsInside(region.bounds);
		HashSet textStreamIdSet = new LinkedHashSet();
		for (int w = 0; w < regionWords.length; w++) {
			if (ImWord.TEXT_STREAM_TYPE_DELETED.equals(regionWords[w].getTextStreamType()))
				continue;
			if (excludeTables && ImWord.TEXT_STREAM_TYPE_TABLE.equals(regionWords[w].getTextStreamType()))
				continue;
			if (excludeCaptionsFootnotes && (ImWord.TEXT_STREAM_TYPE_CAPTION.equals(regionWords[w].getTextStreamType()) || ImWord.TEXT_STREAM_TYPE_FOOTNOTE.equals(regionWords[w].getTextStreamType()) || ImWord.TEXT_STREAM_TYPE_TABLE_NOTE.equals(regionWords[w].getTextStreamType())))
				continue;
//			if ((ImWord.TEXT_STREAM_TYPE_PAGE_TITLE.equals(regionWords[w].getTextStreamType()) || ImWord.TEXT_STREAM_TYPE_LABEL.equals(regionWords[w].getTextStreamType()) || ImWord.TEXT_STREAM_TYPE_ARTIFACT.equals(regionWords[w].getTextStreamType())) && ((normalizationLevel == NORMALIZATION_LEVEL_PARAGRAPHS) || (normalizationLevel == NORMALIZATION_LEVEL_STREAMS)))
//				continue;
			if (excludePageTitles && ImWord.TEXT_STREAM_TYPE_PAGE_TITLE.equals(regionWords[w].getTextStreamType()))
				continue;
			if (excludeLabels && ImWord.TEXT_STREAM_TYPE_LABEL.equals(regionWords[w].getTextStreamType()))
				continue;
			if (excludeArtifacts && ImWord.TEXT_STREAM_TYPE_ARTIFACT.equals(regionWords[w].getTextStreamType()))
				continue;
			textStreamIdSet.add(regionWords[w].getTextStreamId());
		}
		String[] textStreamIDs = ((String[]) textStreamIdSet.toArray(new String[textStreamIdSet.size()]));
		ImWord[] textStreamHeads = new ImWord[textStreamIDs.length];
		for (int s = 0; s < textStreamIDs.length; s++)
			for (int w = 0; w < regionWords.length; w++) {
				if (textStreamIDs[s].equals(regionWords[w].getTextStreamId()) && ((textStreamHeads[s] == null) || (regionWords[w].getTextStreamPos() < textStreamHeads[s].getTextStreamPos())))
					textStreamHeads[s] = regionWords[w];
			}
		this.addTextStreams(textStreamHeads, configFlags, new HashSet(Arrays.asList(regionWords)));
		
		//	set first and last word only if single text stream
		if (textStreamHeads.length == 1) {
			this.firstWord = this.firstWordOf(this.firstToken());
			this.lastWord = this.lastWordOf(this.lastToken());
		}
		
		//	add annotation overlay for annotations
		ImAnnotation[] annotations = this.doc.getAnnotations(region.pageId);
		for (int a = 0; a < annotations.length; a++) {
			
			//	check if words are on page
			if (annotations[a].getFirstWord().pageId != region.pageId)
				continue;
			if (annotations[a].getLastWord().pageId != region.pageId)
				continue;
			
			//	check if words are inside region
			if (!region.bounds.includes(annotations[a].getFirstWord().bounds, true))
				continue;
			if (!region.bounds.includes(annotations[a].getLastWord().bounds, true))
				continue;
			
			//	finally, we can include this one
			this.addAnnotation(annotations[a]);
		}
		
		//	add annotation for argument region
		this.addAnnotation(region, regionWords);
		
		//	annotate regions for low normalization levels
		if ((normalizationLevel == NORMALIZATION_LEVEL_RAW) || (normalizationLevel == NORMALIZATION_LEVEL_WORDS)) {
//			ImRegion[] regions = region.getPage().getRegions();
			ImRegion[] regions = page.getRegions();
			for (int r = 0; r < regions.length; r++) {
				
				//	handle tables separately
				if (ImRegion.TABLE_TYPE.equals(regions[r].getType()) || ImRegion.TABLE_ROW_TYPE.equals(regions[r].getType()) || ImRegion.TABLE_COL_TYPE.equals(regions[r].getType()) || ImRegion.TABLE_CELL_TYPE.equals(regions[r].getType()))
					continue;
				
				//	we already have this one
				if (regions[r] == region)
					continue;
				
				//	annotate region if within range
				if (region.bounds.includes(regions[r].bounds, false))
					this.addAnnotation(regions[r]);
			}
		}
		
		//	emulate paragraphs based on word relations for high normalization levels
		else if ((normalizationLevel == NORMALIZATION_LEVEL_PARAGRAPHS) || (normalizationLevel == NORMALIZATION_LEVEL_STREAMS)) {
//			ImPage page = region.getPage();
			ImRegion[] pageBlocks = page.getRegions(BLOCK_ANNOTATION_TYPE);
			for (int h = 0; h < textStreamHeads.length; h++) {
				ImWord pFirstWord = null;
				for (ImWord imw = textStreamHeads[h]; imw != null; imw = imw.getNextWord()) {
					if (imw.pageId != region.pageId)
						break;
					if (!region.bounds.includes(imw.bounds, true))
						continue;
					if (pFirstWord == null)
						pFirstWord = imw;
					if ((imw.getNextRelation() == ImWord.NEXT_RELATION_PARAGRAPH_END) || (imw.getNextWord() == null) || (imw.getNextWord().pageId != region.pageId) || !region.bounds.includes(imw.getNextWord().bounds, true)) {
						ImAnnotationBase imab = this.addAnnotation(pFirstWord, imw);
						imab.pFirstBlockId = getParentBlockId(pageBlocks, pFirstWord);
						imab.pLastBlockId = getParentBlockId(pageBlocks, imw);
						pFirstWord = null;
					}
				}
			}
		}
		
		//	annotate tables
		if (!excludeTables) {
//			ImRegion[] tables = region.getPage().getRegions(TABLE_ANNOTATION_TYPE);
			ImRegion[] tables = page.getRegions(TABLE_ANNOTATION_TYPE);
			for (int t = 0; t < tables.length; t++) {
				
				//	we already have this one
				if (tables[t] == region)
					continue;
				
				//	annotate table if it's within range
				if (region.bounds.includes(tables[t].bounds, false))
					this.annotateTableStructure(tables[t]);
			}
		}
		
		//	write through included configuration (annotating words makes sense only now)
		this.setShowTokensAsWordsAnnotations((configFlags & SHOW_TOKENS_AS_WORD_ANNOTATIONS) != 0);
		this.setUseRandomAnnotationIDs((configFlags & USE_RANDOM_ANNOTATION_IDS) != 0);
		
		//	track changes in underlying document
		this.finishInit();
	}
	
	//	represent a table with annotations
	private static final boolean DEBUG_ANNOTATE_TABLE = false;
	private void annotateTableStructure(ImRegion table) {
		if (DEBUG_ANNOTATE_TABLE) System.out.println("Annotating table at " + table.bounds + " in page " + table.pageId + ")");
		ImAnnotationBase tableImab = this.addAnnotation(table, table.getWords(), true, false);
		if (tableImab == null) {
			if (DEBUG_ANNOTATE_TABLE) System.out.println(" ==> no content words found");
			return;
		}
		
		//	get table rows and columns (getting cells sorts arrays based on text direction)
		ImRegion[] rows = table.getRegions(ImRegion.TABLE_ROW_TYPE);
		ImRegion[] cols = table.getRegions(ImRegion.TABLE_COL_TYPE);
		
		//	get table cells (also sorts rows and columns in line with predominant text direction)
		ImRegion[][] cells = ImUtils.getTableCells(table, rows, cols);
		if (DEBUG_ANNOTATE_TABLE) System.out.println(" - got " + rows.length + " rows and " + cols.length + " columns");
		
		//	set table attributes
		tableImab.rTableAttributes.gridCols = cols.length;
		tableImab.rTableAttributes.gridRows = rows.length;
		
		//	reflect whole table as grid
		ImAnnotationBase[][] cellImabs = new ImAnnotationBase[rows.length][cols.length];
		
		//	mark header rows and columns
		boolean[] rowIsColHeader = new boolean[rows.length];
		Arrays.fill(rowIsColHeader, false);
		if (2 < rows.length)
			rowIsColHeader[0] = true; // first row usually holds column headers if there is more than two rows
		for (int r = 0; r < rows.length; r++) {
			if (rows[r].hasAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE))
				rowIsColHeader[r] = true;
			else break;
		}
		boolean[] colIsRowHeader = new boolean[cols.length];
		Arrays.fill(colIsRowHeader, false);
		if (2 < cols.length)
			colIsRowHeader[0] = true; // first column usually holds row labels if there is more than two columns
		for (int c = 0; c < cols.length; c++) {
			if (cols[c].hasAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE))
				colIsRowHeader[c] = true;
			else break;
		}
		
		//	go row by row
		for (int r = 0; r < rows.length; r++) {
			if (DEBUG_ANNOTATE_TABLE) System.out.println(" - doing row " + r);
			ImWord rowStart = null;
			ImWord rowEnd = null;
			int[] emptyCellsToSpan = new int[cols.length];
			Arrays.fill(emptyCellsToSpan, 0);
			boolean gotEmptyCellsToSpan = false;
			
			//	annotate cells
			for (int c = 0; c < cells[r].length; c++) {
				if (DEBUG_ANNOTATE_TABLE) System.out.println("   - doing column " + c);
				if ((r != 0) && (cells[r][c] == cells[r-1][c])) {
					if (DEBUG_ANNOTATE_TABLE) System.out.println("     - downwards extending cell in row " + r + ", col " + c + " (page " + rows[r].pageId + ")");
					if (cellImabs[r-1][c] != null) {
						cellImabs[r][c] = cellImabs[r-1][c];
						if ((c == 0) || (cells[r][c] != cells[r][c-1])) // only count up rowspan in first column
							cellImabs[r][c].rTableAttributes.belowCellsToSpan++;
					}
					continue; // we already have these words spanned
				}
				if ((c != 0) && (cells[r][c] == cells[r][c-1])) {
					if (DEBUG_ANNOTATE_TABLE) System.out.println("     - rightwards extending cell in row " + r + ", col " + c + " (page " + rows[r].pageId + ")");
					if (cellImabs[r][c-1] != null) {
						cellImabs[r][c] = cellImabs[r][c-1];
						cellImabs[r][c].rTableAttributes.rightCellsToSpan++;
					}
					else {
						emptyCellsToSpan[c]++;
						gotEmptyCellsToSpan = true;
					}
					continue; // we already have these words spanned
				}
				ImWord[] cellWords;
				if (cells[r][c].getPage() == null)
					cellWords = table.getPage().getWordsInside(cells[r][c].bounds);
				else cellWords = cells[r][c].getWords();
				if (DEBUG_ANNOTATE_TABLE) System.out.println("     - got " + cellWords.length + " words");
				if (cellWords.length != 0) {
					Arrays.sort(cellWords, ImUtils.textStreamOrder);
					if (rowStart == null)
						rowStart = cellWords[0];
					rowEnd = cellWords[cellWords.length - 1];
					cellImabs[r][c] = this.addAnnotation(cells[r][c], cellWords, true, (rowIsColHeader[r] || colIsRowHeader[c]));
					if (cellImabs[r][c] == null) {
						emptyCellsToSpan[c]++;
						gotEmptyCellsToSpan = true;
					}
					else {
						cellImabs[r][c].rTableAttributes.gridLeft = c;
						cellImabs[r][c].rTableAttributes.gridTop = r;
					}
				}
				else {
					emptyCellsToSpan[c]++;
					gotEmptyCellsToSpan = true;
				}
			}
			
			//	nothing to annotate row on
			if ((rowStart == null) || (rowEnd == null))
				continue;
			
			//	try and annotate row proper
			ImAnnotationBase rowImab = this.addAnnotation(rows[r], getRowWords(rowStart, rowEnd), true, false);
			if (rowImab == null)
				continue;
			rowImab.rTableAttributes.gridTop = r;
			
			//	take care of cells to span
			if (gotEmptyCellsToSpan)
				rowImab.rTableAttributes.emptyCellsToSpan = emptyCellsToSpan;
		}
	}
	
	private static ImWord[] getRowWords(ImWord rowStart, ImWord rowEnd) {
		ArrayList rowWords = new ArrayList();
		for (ImWord imw = rowStart; imw != null; imw = imw.getNextWord()) {
			rowWords.add(imw);
			if (imw == rowEnd)
				break;
		}
		return ((ImWord[]) rowWords.toArray(new ImWord[rowWords.size()]));
	}
	
	/**
	 * Test whether or not this wrapper is showing tokens as word annotations.
	 * @return true if tokens are shown as word annotations
	 */
	public boolean isShowingTokensAsWordsAnnotations() {
		return this.showTokensAsWordAnnotations;
	}
	
	/**
	 * Switch showing tokens as word annotations on or off. Especially switching
	 * this property on causes considerable effort, so this property should not
	 * be switched on and off frequently, and used only when needed.
	 * @param stawa show tokens as word annotations?
	 */
	public void setShowTokensAsWordsAnnotations(boolean stawa) {
		
		//	nothing to do
		if (this.showTokensAsWordAnnotations == stawa)
			return;
		
		//	set the property
		this.showTokensAsWordAnnotations = stawa;
		
		//	integrate words in annotation infrastructure
		if (this.showTokensAsWordAnnotations) {
			ImAnnotationList wordAnnotations = new ImAnnotationList(WORD_ANNOTATION_TYPE);
			for (int t = 0; t < this.size(); t++) {
				ImToken imt = this.imTokenAtIndex(t);
				ImAnnotationBase imtAb = new ImAnnotationBase(imt);
				this.annotations.addAnnotation(imtAb);
				this.annotationBasesByUUIDs.put(imtAb.getId(), imtAb);
				wordAnnotations.addAnnotation(imtAb);
			}
			this.annotationsByType.put(WORD_ANNOTATION_TYPE, wordAnnotations);
		}
		
		//	remove words from annotation infrastructure
		else {
			ImAnnotationList wordAnnotations = ((ImAnnotationList) this.annotationsByType.remove(WORD_ANNOTATION_TYPE));
			if (wordAnnotations != null) {
				for (int a = 0; a < wordAnnotations.size(); a++) {
					ImAnnotationBase wImab = wordAnnotations.getAnnotation(a);
					if (wImab.tData != null) {
						this.annotations.removeAnnotation(wImab);
						this.annotationBasesByUUIDs.remove(wImab.getId());
					}
				}
				wordAnnotations.clear();
			}
		}
	}
	
	/**
	 * Test whether or not this wrapper is using random annotation IDs.
	 * @return true if random annotation IDs are in use
	 * @deprecated looping through UUIDs of underlying objects instead now
	 */
	public boolean isUsingRandomAnnotationIDs() {
		return this.useRandomAnnotationIDs;
	}
	
	/**
	 * Switch annotation IDs between random and non-random mode.<br>
	 * In random mode, each annotation gets its ID assigned as a random 128 bit
	 * hexadecimal string of 32 characters when the wrapper is created, and the
	 * ID remains the same for the lifetime of the wrapper. This is favorable
	 * if the wrapper is used for text analysis, where the main purpose of
	 * annotation IDs is to recognize annotations across changes.<br>
	 * In non-random mode, annotation IDs are generated in a well-defined and
	 * reproducible way, i.e., the same annotation will always be assigned the
	 * same identifier. In particular, the identifier is a concatenation of the
	 * first and last word bounding boxes of an annotation, the page IDs of
	 * these two words, and the annotation type, and all this XOR combined with
	 * the document ID. This is favorable for export scenarios, where
	 * annotation IDs need to be stable, as well as fit for duplicate detection.
	 * @param uraids use random annotation IDs?
	 * @deprecated looping through UUIDs of underlying objects instead now
	 */
	public void setUseRandomAnnotationIDs(boolean uraids) {
		//	nothing to recompute here, logic sits in annotation bases
		this.useRandomAnnotationIDs = uraids;
	}
	
	/**
	 * Check whether or not the wrapper is still usable.
	 * @return true if the wrapper is still usable
	 */
	public boolean isValid() {
		return (super.isValid() && (this.docListener != null));
	}
	
	void invalidate() {
		super.invalidate();
		if (this.docListener != null)
			this.docListener.dispose();
		this.docListener = null;
	}
	
	void checkValid() {
		super.checkValid();
		if (this.initializing)
			return;
		if (this.docListener == null)
			throw new IllegalStateException("Invalid due to unrecoverable changes to underlying document");
	}
	
	private void finishInit() {
		this.docListener = new BaseDocumentListener(this, this.doc);
		this.initializing = false;
		this.finishInit(this.docListener);
	}
	
	/**
	 * Dispose of the wrapper, clear any internal data structures, etc. Once
	 * this method has been called, the wrapper is completely unusable.
	 */
	public void dispose() {
		this.invalidate();
		this.annotations.clear();
		this.annotationsByType.clear();
		this.annotationBasesByAnnotations.clear();
		this.annotationBasesByRegions.clear();
		this.annotationBasesByUUIDs.clear();
		this.annotationViewsByBases.clear();
	}
	
	private ArrayList annotationListeners = null;
	
	/* (non-Javadoc)
	 * @see de.gamta.MutableAnnotation#addAnnotationListener(de.gamta.AnnotationListener)
	 */
	public void addAnnotationListener(AnnotationListener al) {
		if (al == null)
			return;
		if (this.annotationListeners == null)
			this.annotationListeners = new ArrayList(2);
		else if (this.annotationListeners.contains(al))
			return;
		this.annotationListeners.add(al);
	}
	
	/* (non-Javadoc)
	 * @see de.gamta.MutableAnnotation#removeAnnotationListener(de.gamta.AnnotationListener)
	 */
	public void removeAnnotationListener(AnnotationListener al) {
		if (this.annotationListeners != null)
			this.annotationListeners.remove(al);
	}
	
	void annotationTypeChanged(ImAnnotation annot, String oldType) {
		ImAnnotationBase imab = ((ImAnnotationBase) this.annotationBasesByAnnotations.get(annot));
		if (imab == null)
			return; // none of ours
		
		//	update indexes (need to store and null out hash ID to force re-computation based upon new type)
		String oldHashId = imab.hashId;
		imab.hashId = null;
		this.annotationTypeChanged(imab, oldType, oldHashId);
		
		//	notify any listeners
		this.notifyAnnotationTypeChanged(imab, oldType);
	}
	void regionTypeChanged(ImRegion region, String oldType) {
		String imrKey = this.getRegionKey(region, oldType);
		ImAnnotationBase imab = ((ImAnnotationBase) this.annotationBasesByRegions.get(imrKey));
		if (imab == null)
			return; // none of ours
		
		//	update indexes (need to store and null out hash ID to force re-computation based upon new type)
		String oldHashId = imab.hashId;
		imab.hashId = null;
		this.annotationTypeChanged(imab, oldType, oldHashId);
		
		//	notify any listeners
		this.notifyAnnotationTypeChanged(imab, oldType);
	}
	void notifyAnnotationTypeChanged(ImAnnotationBase imab, String oldType) {
		if (this.annotationListeners == null)
			return;
		QueriableAnnotation doc = new ImmutableAnnotation(this);
		Annotation reTyped = new ImmutableAnnotation(this.getAnnotationView(imab, null, 'Q'));
		for (int l = 0; l < this.annotationListeners.size(); l++) try {
			((AnnotationListener) this.annotationListeners.get(l)).annotationTypeChanged(doc, reTyped, oldType);
		}
		catch (Exception e) {
			System.out.println("Exception notifying annotation type change: " + e.getMessage());
			e.printStackTrace(System.out);
		}
	}
	
	//	only need to do notification loop-through here, as attributes are transparent through wrapper bases and views
	void annotationAttributeChanged(ImAnnotation annot, String attributeName, Object oldValue) {
		ImAnnotationBase imab = ((ImAnnotationBase) this.annotationBasesByAnnotations.get(annot));
		if (imab == null)
			return; // none of ours
		
		//	catch changes to annotation boundary words and handle as removal and subsequent addition of annotation
		if (ImAnnotation.FIRST_WORD_ATTRIBUTE.equals(attributeName) && (oldValue instanceof ImWord)) {
			if (this.annotationListeners != null) {
				ImWord oldFirstWord = ((ImWord) oldValue);
				ImWord lastWord = annot.getLastWord();
				ImToken oldFirstToken = this.getTokenFor(oldFirstWord);
				ImToken lastToken = this.getTokenFor(lastWord);
				String oldUuid = RandomByteSource.getHexXor(UuidHelper.getLocalUID(imab.getType(), oldFirstWord.pageId, lastWord.pageId, oldFirstWord.bounds.left, oldFirstWord.bounds.top, lastWord.bounds.right, lastWord.bounds.bottom), this.doc.docId);
				this.notifyAnnotationRemoved(annot, oldFirstToken.index, (lastToken.index - oldFirstToken.index + 1), oldUuid);
				this.notifyAnnotationAdded(imab);
			}
			this.orderModCount++; // force re-sorting annotations (can't tell what changed)
		}
		else if (ImAnnotation.LAST_WORD_ATTRIBUTE.equals(attributeName) && (oldValue instanceof ImWord)) {
			if (this.annotationListeners != null) {
				ImWord firstWord = annot.getFirstWord();
				ImWord oldLastWord = ((ImWord) oldValue);
				ImToken firstToken = this.getTokenFor(firstWord);
				ImToken oldLastToken = this.getTokenFor(oldLastWord);
				String oldUuid = RandomByteSource.getHexXor(UuidHelper.getLocalUID(imab.getType(), firstWord.pageId, oldLastWord.pageId, firstWord.bounds.left, firstWord.bounds.top, oldLastWord.bounds.right, oldLastWord.bounds.bottom), this.doc.docId);
				this.notifyAnnotationRemoved(annot, firstToken.index, (oldLastToken.index - firstToken.index + 1), oldUuid);
				this.notifyAnnotationAdded(imab);
			}
			this.orderModCount++; // force re-sorting annotations (can't tell what changed)
		}
		else if (this.annotationListeners != null)
			this.objectAttributeChanged(imab, (annot == null), attributeName, oldValue);
	}
	void regionAttributeChanged(ImRegion region, String attributeName, Object oldValue) {
		if (this.annotationListeners == null)
			return;
		String imrKey = this.getRegionKey(region, null);
		ImAnnotationBase imab = ((ImAnnotationBase) this.annotationBasesByRegions.get(imrKey));
		if (imab == null)
			return; // none of ours
		this.objectAttributeChanged(imab, (region == null), attributeName, oldValue);
	}
	private void objectAttributeChanged(ImAnnotationBase imab, boolean isDocAttribute, String attributeName, Object oldValue) {
		QueriableAnnotation doc = new ImmutableAnnotation(this);
		Annotation target = (isDocAttribute ? doc : new ImmutableAnnotation(this.getAnnotationView(imab, null, 'Q')));
		for (int l = 0; l < this.annotationListeners.size(); l++) try {
			((AnnotationListener) this.annotationListeners.get(l)).annotationAttributeChanged(doc, target, attributeName, oldValue);
		}
		catch (Exception e) {
			System.out.println("Exception notifying annotation attribute change: " + e.getMessage());
			e.printStackTrace(System.out);
		}
	}
	
	void wordRelationChanged(ImWord leftImw, ImToken leftImt, char oldNextRelation, ImWord rightImw, ImToken rightImt) {
		super.wordRelationChanged(leftImw, leftImt, oldNextRelation, rightImw, rightImt);
		
		//	invalidated in superclass, we're one here
		if (!this.isValid())
			return;
		
		//	only need to adjust logical paragraphs
		if (!this.paragraphsAreLogical)
			return;
		
		//	do we have any changes to paragraphs?
		boolean oldIsParagraphBreak = (oldNextRelation == ImWord.NEXT_RELATION_PARAGRAPH_END);
		boolean newIsParagraphBreak = (leftImw.getNextRelation() == ImWord.NEXT_RELATION_PARAGRAPH_END);
		if (oldIsParagraphBreak == newIsParagraphBreak)
			return; // nothing to adjust
		
		//	get list of logical paragraphs
		ImAnnotationList paragraphList = ((ImAnnotationList) this.annotationsByType.get(EditableAnnotation.PARAGRAPH_TYPE));
		if (paragraphList == null)
			return;
		
		//	paragraph break removed
		if (oldIsParagraphBreak) {
			ImAnnotationBase[] oldLeftParaImabs = paragraphList.getAnnotations(leftImt.index, (leftImt.index + 1));
			ImAnnotationBase[] oldRightParaImabs = paragraphList.getAnnotations(rightImt.index, (rightImt.index + 1));
			
			//	tow single paragraphs, as expected
			if ((oldLeftParaImabs.length == 1) && (oldRightParaImabs.length == 1)) {
				
				//	annotate new paragraph
				ImWord newParaFirstWord = oldLeftParaImabs[0].firstWord();
				ImWord newParaLastWord = oldRightParaImabs[0].lastWord();
				ImAnnotationBase newParaImab = this.addAnnotation(newParaFirstWord, newParaLastWord);
				
				//	complete attributes
				newParaImab.pFirstBlockId = getParentBlockId(newParaFirstWord.getPage().getRegions(ImRegion.BLOCK_ANNOTATION_TYPE), newParaFirstWord);
				newParaImab.pLastBlockId = getParentBlockId(newParaLastWord.getPage().getRegions(ImRegion.BLOCK_ANNOTATION_TYPE), newParaLastWord);
				
				//	remove left base from indexes
				this.dropAnnotationBase(oldLeftParaImabs[0]);
				this.unIndexAnnotationBase(oldLeftParaImabs[0], null);
				this.annotationBasesByUUIDs.remove(oldLeftParaImabs[0].getId());
				
				//	remove rigth base from indexes
				this.dropAnnotationBase(oldRightParaImabs[0]);
				this.unIndexAnnotationBase(oldRightParaImabs[0], null);
				this.annotationBasesByUUIDs.remove(oldRightParaImabs[0].getId());
				
				//	notify any listeners about new paragraph (removal notification only works with actual IM objects)
				this.notifyAnnotationAdded(newParaImab);
			}
			
			//	something is strange, be safe
			else this.invalidate();
		}
		
		//	paragraph break added
		else {
			ImAnnotationBase[] oldParaImabs = paragraphList.getAnnotations(leftImt.index, (rightImt.index + 1));
			
			//	single paragraph, as expected
			if (oldParaImabs.length == 1) {
				
				//	annotate new left paragraph
				ImWord newLeftParaFirstWord = oldParaImabs[0].firstWord();
				ImWord newLeftParaLastWord = leftImw;
				ImAnnotationBase newLeftParaImab = this.addAnnotation(newLeftParaFirstWord, newLeftParaLastWord);
				
				//	complete attributes
				newLeftParaImab.pFirstBlockId = getParentBlockId(newLeftParaFirstWord.getPage().getRegions(ImRegion.BLOCK_ANNOTATION_TYPE), newLeftParaFirstWord);
				newLeftParaImab.pLastBlockId = getParentBlockId(newLeftParaLastWord.getPage().getRegions(ImRegion.BLOCK_ANNOTATION_TYPE), newLeftParaLastWord);
				
				//	annotate new left paragraph
				ImWord newRightParaFirstWord = rightImw;
				ImWord newRightParaLastWord = oldParaImabs[0].lastWord();
				ImAnnotationBase newRightParaImab = this.addAnnotation(newRightParaFirstWord, newRightParaLastWord);
				
				//	complete attributes
				newRightParaImab.pFirstBlockId = getParentBlockId(newRightParaFirstWord.getPage().getRegions(ImRegion.BLOCK_ANNOTATION_TYPE), newRightParaFirstWord);
				newRightParaImab.pLastBlockId = getParentBlockId(newRightParaLastWord.getPage().getRegions(ImRegion.BLOCK_ANNOTATION_TYPE), newRightParaLastWord);
				
				//	remove old base from indexes
				this.dropAnnotationBase(oldParaImabs[0]);
				this.unIndexAnnotationBase(oldParaImabs[0], null);
				this.annotationBasesByUUIDs.remove(oldParaImabs[0].getId());
				
				//	notify any listeners about new paragraph (removal notification only works with actual IM objects)
				this.notifyAnnotationAdded(newLeftParaImab);
				this.notifyAnnotationAdded(newRightParaImab);
			}
			
			//	something is strange, be safe
			else this.invalidate();
		}
	}
	
	void annotationAdded(ImAnnotation annot) {
		
		//	check if annotation in scope
		ImToken firstImt = this.getTokenFor(annot.getFirstWord());
		ImToken lastImt = this.getTokenFor(annot.getLastWord());
		if ((firstImt == null) || (lastImt == null))
			return; // not (fully) within our scope
		
		//	add annotation to wrapper registers
		ImAnnotationBase imab = this.getAnnotationBase(annot);
		this.indexAnnotationBase(imab);
		
		//	notify any listeners
		this.notifyAnnotationAdded(imab);
	}
	
	void annotationRemoved(ImAnnotation annot) {
		ImAnnotationBase imab = ((ImAnnotationBase) this.annotationBasesByAnnotations.get(annot));
		if (imab == null)
			return; // none of ours
		
		//	remove base from indexes
		this.dropAnnotationBase(imab);
		this.unIndexAnnotationBase(imab, null);
		this.annotationBasesByUUIDs.remove(imab.getId());
		
		//	notify any listeners
		this.notifyAnnotationRemoved(region, imab.getStartIndex(), imab.size(), imab.getId());
	}
	
	void regionAdded(ImRegion region) {
		if (this.paragraphsAreLogical) /* check if regions relevant at all */ {
			if (!ImRegion.TABLE_TYPE.equals(region.getType()) && !ImRegion.TABLE_ROW_TYPE.equals(region.getType()) && !ImRegion.TABLE_COL_TYPE.equals(region.getType()) && !ImRegion.TABLE_CELL_TYPE.equals(region.getType()))
				return; // table related regions are only ones represented in logical paragraph mode
			if ((this.configFlags & EXCLUDE_TABLES) != 0)
				return; // tables filtered, we can ignore this one (if affecting us, text stream type change will take care of invalidation)
		}
		else if ((this.configFlags & EXCLUDE_TABLES) != 0) {
			if (ImRegion.TABLE_TYPE.equals(region.getType()) || ImRegion.TABLE_ROW_TYPE.equals(region.getType()) || ImRegion.TABLE_COL_TYPE.equals(region.getType()) || ImRegion.TABLE_CELL_TYPE.equals(region.getType()))
				return; // tables filtered, we can ignore this one (if affecting us, text stream type change will take care of invalidation)
		}
		
		//	check if annotation in scope
		ImWord[] regionWords = region.getWords();
		if (regionWords.length == 0)
			return;
		ImUtils.sortLeftRightTopDown(regionWords);
		ImWord firstImw = regionWords[0];
		ImWord lastImw = regionWords[regionWords.length - 1];
		ImToken firstImt = this.getTokenFor(firstImw);
		ImToken lastImt = this.getTokenFor(lastImw);
		if ((firstImt == null) || (lastImt == null))
			return; // not (fully) within our scope
		
		//	add annotation to wrapper registers
		ImAnnotationBase imab = this.addAnnotation(region, regionWords);
//		this.indexAnnotationBase(imab); // happens when adding annotation
		
		//	notify any listeners
		this.notifyAnnotationAdded(imab);
	}
	
	void regionRemoved(ImRegion region) {
		String imrKey = this.getRegionKey(region, null);
		ImAnnotationBase imab = ((ImAnnotationBase) this.annotationBasesByRegions.get(imrKey));
		if (imab == null)
			return; // none of ours
		
		//	remove base from indexes
		this.dropAnnotationBase(imab);
		this.unIndexAnnotationBase(imab, null);
		this.annotationBasesByUUIDs.remove(imab.getId());
		
		//	notify any listeners
		this.notifyAnnotationRemoved(region, imab.getStartIndex(), imab.size(), imab.getId());
	}
	
	private void notifyAnnotationAdded(ImAnnotationBase imab) {
		if (this.annotationListeners == null)
			return;
		QueriableAnnotation doc = new ImmutableAnnotation(this);
		Annotation added = new ImmutableAnnotation(this.getAnnotationView(imab, null, 'Q'));
		for (int l = 0; l < this.annotationListeners.size(); l++) try {
			((AnnotationListener) this.annotationListeners.get(l)).annotationAdded(doc, added);
		}
		catch (Exception e) {
			System.out.println("Exception notifying annotation added: " + e.getMessage());
			e.printStackTrace(System.out);
		}
	}
	private void notifyAnnotationRemoved(ImObject object, int startIndex, int size, String objectUuid) {
		if (this.annotationListeners == null)
			return;
		if (object == null)
			return;
		QueriableAnnotation doc = new ImmutableAnnotation(this);
		Annotation removed = new TemporaryAnnotation(doc, object.getType(), startIndex, size);
		removed.copyAttributes(object);
		removed.setAttribute(ANNOTATION_ID_ATTRIBUTE, objectUuid);
		for (int l = 0; l < this.annotationListeners.size(); l++) try {
			((AnnotationListener) this.annotationListeners.get(l)).annotationRemoved(doc, removed);
		}
		catch (Exception e) {
			System.out.println("Exception notifying annotation removed: " + e.getMessage());
			e.printStackTrace(System.out);
		}
	}
	
	/**
	 * Retrieve the underlying Image Markup document. If the document is
	 * modified in any way, this XML wrapper document becomes invalid and
	 * should not be used any further.
	 * @return the underlying Image Markup document
	 */
	public ImDocument document() {
		return this.doc;
	}
	
	/**
	 * Retrieve the underlying Image Markup object of some annotation. If the
	 * object is modified in any way, this XML wrapper document becomes invalid
	 * and should not be used any further. If the argument annotation has not
	 * been retrieved from this wrapper in any way, results are meaningless.
	 * @param annot the annotation to get the basis of
	 * @return the underlying Image Markup object
	 */
	public ImObject basisOf(Annotation annot) {
		if (annot == this) {
			if (this.annotation != null)
				return this.annotation;
			if (this.region != null)
				return this.region;
			return this.doc;
		}
//		if (annot instanceof ImAnnotationView) {
//			ImAnnotationBase base = ((ImAnnotationView) annot).base;
//			if (base.aData != null)
//				return base.aData;
//			if (base.rData != null)
//				return base.rData;
//		}
		ImAnnotationBase imab = this.getBaseOf(annot);
		if (imab != null) {
			if (imab.aData != null)
				return imab.aData;
			if (imab.rData != null)
				return imab.rData;
		}
		return null;
	}
	
	/**
	 * Retrieve the first word of the underlying Image Markup object of some
	 * annotation. If the word is modified in any way, this XML wrapper document
	 * becomes invalid and should not be used any further. If the argument
	 * annotation has not been retrieved from this wrapper in any way, results
	 * are meaningless.
	 * @param annot the annotation to get the first word of
	 * @return the first word of the underlying Image Markup object
	 */
	public ImWord firstWordOf(Annotation annot) {
		if (annot == this)
			return this.firstWord;
//		if (annot instanceof ImAnnotationView)
//			return ((ImAnnotationView) annot).base.firstWord();
		ImAnnotationBase imab = this.getBaseOf(annot);
		if (imab != null)
			return imab.firstWord();
		return null;
	}
	
	/**
	 * Retrieve the last word of the underlying Image Markup object of some
	 * annotation. If the word is modified in any way, this XML wrapper document
	 * becomes invalid and should not be used any further. If the argument
	 * annotation has not been retrieved from this wrapper in any way, results
	 * are meaningless.
	 * @param annot the annotation to get the last word of
	 * @return the last word of the underlying Image Markup object
	 */
	public ImWord lastWordOf(Annotation annot) {
		if (annot == this)
			return this.lastWord;
//		if (annot instanceof ImAnnotationView)
//			return ((ImAnnotationView) annot).base.lastWord();
		ImAnnotationBase imab = this.getBaseOf(annot);
		if (imab != null)
			return imab.lastWord();
		return null;
	}
	
	/**
	 * Retrieve the underlying Image Markup object of some token. If the object
	 * is modified in any way, this XML wrapper document becomes invalid and
	 * should not be used any further. If the argument token has not been
	 * retrieved from this wrapper in any way, results are meaningless.
	 * @param token the token to get the first word of
	 * @return the underlying Image Markup word
	 */
	public ImWord basisOf(Token token) {
		if (token instanceof ImTokenView) {
			ImToken base = ((ImTokenView) token).base;
			return ((ImWord) base.imWords.get(0));
		}
		if (token instanceof ImToken) {
			ImToken base = ((ImToken) token);
			return ((ImWord) base.imWords.get(0));
		}
		return null;
	}
	
	/**
	 * Retrieve the first word of the underlying Image Markup object of some
	 * token. If the word is modified in any way, this XML wrapper document
	 * becomes invalid and should not be used any further. If the argument
	 * annotation has not been retrieved from this wrapper in any way, results
	 * are meaningless.
	 * @param token the token to get the first word of
	 * @return the first underlying word of the token
	 */
	public ImWord firstWordOf(Token token) {
		if (token instanceof ImTokenView) {
			ImToken base = ((ImTokenView) token).base;
			return ((ImWord) base.imWords.get(0));
		}
		if (token instanceof ImToken) {
			ImToken base = ((ImToken) token);
			return ((ImWord) base.imWords.get(0));
		}
		return null;
	}
	
	/**
	 * Retrieve the last word of the underlying Image Markup object of some
	 * token. If the word is modified in any way, this XML wrapper document
	 * becomes invalid and should not be used any further. If the argument
	 * annotation has not been retrieved from this wrapper in any way, results
	 * are meaningless.
	 * @param token the token to get the last word of
	 * @return the last underlying word of the token
	 */
	public ImWord lastWordOf(Token token) {
		if (token instanceof ImTokenView) {
			ImToken base = ((ImTokenView) token).base;
			return ((ImWord) base.imWords.get(base.imWords.size()-1));
		}
		if (token instanceof ImToken) {
			ImToken base = ((ImToken) token);
			return ((ImWord) base.imWords.get(base.imWords.size()-1));
		}
		return null;
	}
	
	private ImAnnotationBase addAnnotation(ImAnnotation annot) {
		ImAnnotationBase imab = this.getAnnotationBase(annot);
		this.indexAnnotationBase(imab);
		return imab;
	}
	private ImAnnotationBase addAnnotation(ImRegion region) {
		return this.addAnnotation(region, region.getWords());
	}
	private ImAnnotationBase addAnnotation(ImWord pFirstWord, ImWord pLastWord) {
		ImAnnotationBase imab = new ImAnnotationBase(pFirstWord, pLastWord);
		this.indexAnnotationBase(imab);
		return imab;
	}
	private ImAnnotationBase addAnnotation(ImRegion region, ImWord[] regionWords) {
		return this.addAnnotation(region, regionWords, false, false);
	}
	private ImAnnotationBase addAnnotation(ImRegion region, ImWord[] regionWords, boolean imrIsTable, boolean imrIsTableHeader) {
		this.checkValid();
		if (regionWords.length == 0)
			return null;
		ArrayList docRegionWords = new ArrayList();
		for (int w = 0; w < regionWords.length; w++) {
			if (-1 < this.getTokenIndexOf(regionWords[w]))
				docRegionWords.add(regionWords[w]);
		}
		if (docRegionWords.isEmpty())
			return null;
		if (docRegionWords.size() < regionWords.length)
			regionWords = ((ImWord[]) docRegionWords.toArray(new ImWord[docRegionWords.size()]));
		ImAnnotationBase imab = this.getAnnotationBase(region, regionWords, imrIsTable, imrIsTableHeader);
		this.indexAnnotationBase(imab);
		return imab;
	}
	//	TODO check where paragraphs end up
	//	TODO TEST https://github.com/plazi/EJT-testbed/issues/358
	//	TODO TEST https://github.com/plazi/EJT-testbed/issues/360
	//	TODO TEST https://github.com/plazi/EJT-testbed/issues/366
	//	TODO TEST https://github.com/plazi/EJT-testbed/issues/367
	private void indexAnnotationBase(ImAnnotationBase imab) {
		this.annotations.addAnnotation(imab);
		ImAnnotationList typeAnnotsNew = ((ImAnnotationList) this.annotationsByType.get(imab.getType()));
		if (typeAnnotsNew == null) {
			typeAnnotsNew = new ImAnnotationList(imab.getType());
			this.annotationsByType.put(imab.getType(), typeAnnotsNew);
		}
		typeAnnotsNew.addAnnotation(imab);
		this.annotationBasesByUUIDs.put(imab.getId(), imab);
	}
	
	private void annotationTypeChanged(ImAnnotationBase imab, String oldType, String oldHashId) {
		this.unIndexAnnotationBase(imab, oldType);
		if (oldHashId != null)
			this.annotationBasesByUUIDs.remove(oldHashId);
		this.indexAnnotationBase(imab);
	}
	
	private Comparator typedAnnotationBaseOrder = new Comparator() {
		public int compare(Object obj1, Object obj2) {
			int c = annotationBaseOrder.compare(obj1, obj2);
			if (c != 0)
				return c;
			
			if (annotationNestingOrder == null)
				return 0;
			
			int ti1 = annotationNestingOrder.indexOf(((ImAnnotationBase) obj1).getType());
			if (ti1 == -1)
				ti1 = annotationNestingOrder.length();
			int ti2 = annotationNestingOrder.indexOf(((ImAnnotationBase) obj2).getType());
			if (ti2 == -1)
				ti2 = annotationNestingOrder.length();
			return (ti1 - ti2);
		}
	};
	
	private static final Comparator annotationBaseOrder = new Comparator() {
		public int compare(Object obj1, Object obj2) {
			ImAnnotationBase iab1 = ((ImAnnotationBase) obj1);
			ImAnnotationBase iab2 = ((ImAnnotationBase) obj2);
			
			//	compare start and end index (also reflects stream folding mode)
			int c = (iab1.getStartIndex() - iab2.getStartIndex());
			if (c != 0)
				return c;
			c = (iab2.getEndIndex() - iab1.getEndIndex());
			if (c != 0)
				return c;
			
			//	compare bounding boxes
			BoundingBox bb1 = iab1.getBoundingBox();
			BoundingBox bb2 = iab2.getBoundingBox();
			if ((bb1 == null) || (bb2 == null))
				return 0;
			c = (bb1.top - bb2.top);
			if (c != 0)
				return c;
			c = (bb2.bottom - bb1.bottom);
			if (c != 0)
				return c;
			c = (bb1.left - bb2.left);
			if (c != 0)
				return c;
			c = (bb2.right - bb1.right);
			if (c != 0)
				return c;
			
			//	little we can do here
			return 0;
		}
	};
	
	public int getStartOffset() {
		this.checkValid();
		return 0;
	}
	public int getEndOffset() {
		this.checkValid();
		return this.length();
	}
	
	public int getStartIndex() {
		this.checkValid();
		return 0;
	}
	public int getEndIndex() {
		this.checkValid();
		return this.size();
	}
	public String getType() {
		this.checkValid();
		return DocumentRoot.DOCUMENT_TYPE;
	}
	public String changeTypeTo(String newType) {
		this.checkValid();
		return DocumentRoot.DOCUMENT_TYPE;
	}
	public String getAnnotationID() {
		this.checkValid();
		return this.doc.docId;
	}
	public String getValue() {
		this.checkValid();
		return TokenSequenceUtils.concatTokens(this, false, false);
	}
	public String toXML() {
		this.checkValid();
		return (AnnotationUtils.produceStartTag(this, true) + TokenSequenceUtils.concatTokens(this, true, true) + AnnotationUtils.produceEndTag(this));
	}
	public QueriableAnnotation getDocument() {
		this.checkValid();
		if (this.fullDoc == null) {
			this.fullDoc = new ImDocumentRoot(this.doc, this.configFlags);
			this.fullDoc.setAnnotationNestingOrder(this.annotationNestingOrder);
		}
		return this.fullDoc;
	}
	
	public int getAbsoluteStartIndex() {
		this.checkValid();
		return 0;
	}
	public int getAbsoluteStartOffset() {
		this.checkValid();
		return 0;
	}
	public QueriableAnnotation getAnnotation(String id) {
		return this.doGetAnnotation(null, id, 'Q');
	}
	public QueriableAnnotation[] getAnnotations() {
		return this.doGetAnnotations(null, 'Q');
	}
	public QueriableAnnotation[] getAnnotations(String type) {
		return this.doGetAnnotations(type, null, 'Q');
	}
	public QueriableAnnotation[] getAnnotationsSpanning(int startIndex, int endIndex) {
		return this.doGetAnnotations(null, startIndex, endIndex, 'Q');
	}
	public QueriableAnnotation[] getAnnotationsSpanning(String type, int startIndex, int endIndex) {
		return this.doGetAnnotations(type, null, startIndex, endIndex, 'Q');
	}
	public QueriableAnnotation[] getAnnotationsOverlapping(int startIndex, int endIndex) {
		return this.doGetAnnotations(null, (endIndex - 1), (startIndex + 1), 'Q');
	}
	public QueriableAnnotation[] getAnnotationsOverlapping(String type, int startIndex, int endIndex) {
		return this.doGetAnnotations(type, null, (endIndex - 1), (startIndex + 1), 'Q');
	}
	ImAnnotationView doGetAnnotation(ImAnnotationBase source, String id, char type) {
		this.checkValid();
		if (TRACK_INSTANCES) this.accessHistory.accessed();
		ImAnnotationBase imab = ((ImAnnotationBase) this.annotationBasesByUUIDs.get(id));
		if (imab == null)
			return null;
		if (source == null) {}
		else if (imab.getStartIndex() < source.getStartIndex())
			return null;
		else if (source.getEndIndex() < imab.getEndIndex())
			return null;
		return this.getAnnotationView(imab, source, type);
	}
	QueriableAnnotation[] doGetAnnotations(ImAnnotationBase source, char viewType) {
		return this.doGetAnnotations(this.annotations, source, viewType);
	}
	QueriableAnnotation[] doGetAnnotations(String type, ImAnnotationBase source, char viewType) {
		if (type == null)
			return this.doGetAnnotations(source, viewType);
		else return this.doGetAnnotations(((ImAnnotationList) this.annotationsByType.get(type)), source, viewType);
	}
	private static final QueriableAnnotation[] emptyQueriableAnnotations = {};
	private static final EditableAnnotation[] emptyEditableAnnotations = {};
	private static final MutableAnnotation[] emptyMutableAnnotations = {};
	private QueriableAnnotation[] doGetAnnotations(ImAnnotationList annotationList, ImAnnotationBase source, char viewType) {
		this.checkValid();
		if (TRACK_INSTANCES) this.accessHistory.accessed();
		if ((annotationList == null) || annotationList.isEmpty()) {
			if (viewType == 'Q')
				return emptyQueriableAnnotations;
			else if (viewType == 'E')
				return emptyEditableAnnotations;
			else if (viewType == 'M')
				return emptyMutableAnnotations;
			else return null;
		}
		else {
			QueriableAnnotation[] annotations;
			if (source == null) {
				if (viewType == 'Q')
					annotations = new QueriableAnnotation[annotationList.size()];
				else if (viewType == 'E')
					annotations = new EditableAnnotation[annotationList.size()];
				else if (viewType == 'M')
					annotations = new MutableAnnotation[annotationList.size()];
				else return null;
				for (int a = 0; a < annotationList.size(); a++)
					annotations[a] = this.getAnnotationView(annotationList.getAnnotation(a), source, viewType);
			}
			else {
				ImAnnotationBase[] imabs = annotationList.getAnnotationsIn(source);
				if (viewType == 'Q')
					annotations = new QueriableAnnotation[imabs.length];
				else if (viewType == 'E')
					annotations = new EditableAnnotation[imabs.length];
				else if (viewType == 'M')
					annotations = new MutableAnnotation[imabs.length];
				else return null;
				for (int a = 0; a < imabs.length; a++)
					annotations[a] = this.getAnnotationView(imabs[a], source, viewType);
			}
			return annotations;
		}
	}
	QueriableAnnotation[] doGetAnnotations(ImAnnotationBase source, int maxStartIndex, int minEndIndex, char viewType) {
		return this.doGetAnnotations(this.annotations, source, maxStartIndex, minEndIndex, viewType);
	}
	QueriableAnnotation[] doGetAnnotations(String type, ImAnnotationBase source, int maxStartIndex, int minEndIndex, char viewType) {
		if (type == null)
			return this.doGetAnnotations(source, maxStartIndex, minEndIndex, viewType);
		else return this.doGetAnnotations(((ImAnnotationList) this.annotationsByType.get(type)), source, maxStartIndex, minEndIndex, viewType);
	}
	private QueriableAnnotation[] doGetAnnotations(ImAnnotationList annotationList, ImAnnotationBase source, int maxStartIndex, int minEndIndex, char viewType) {
		this.checkValid();
		if (TRACK_INSTANCES) this.accessHistory.accessed();
		if ((annotationList == null) || annotationList.isEmpty()) {
			if (viewType == 'Q')
				return emptyQueriableAnnotations;
			else if (viewType == 'E')
				return emptyEditableAnnotations;
			else if (viewType == 'M')
				return emptyMutableAnnotations;
			else return null;
		}
		else {
			QueriableAnnotation[] annotations;
			if (source == null) {
				ImAnnotationBase[] imabs = annotationList.getAnnotations(maxStartIndex, minEndIndex);
				if (viewType == 'Q')
					annotations = new QueriableAnnotation[imabs.length];
				else if (viewType == 'E')
					annotations = new EditableAnnotation[imabs.length];
				else if (viewType == 'M')
					annotations = new MutableAnnotation[imabs.length];
				else return null;
				for (int a = 0; a < imabs.length; a++)
					annotations[a] = this.getAnnotationView(imabs[a], source, viewType);
			}
			else {
				ImAnnotationBase[] sourceImabs = annotationList.getAnnotationsIn(source, maxStartIndex, minEndIndex);
				if (viewType == 'Q')
					annotations = new QueriableAnnotation[sourceImabs.length];
				else if (viewType == 'E')
					annotations = new EditableAnnotation[sourceImabs.length];
				else if (viewType == 'M')
					annotations = new MutableAnnotation[sourceImabs.length];
				else return null;
				for (int a = 0; a < sourceImabs.length; a++)
					annotations[a] = this.getAnnotationView(sourceImabs[a], source, viewType);
			}
			return annotations;
		}
	}
	public String[] getAnnotationTypes() {
		this.checkValid();
		if (TRACK_INSTANCES) this.accessHistory.accessed();
		return ((String[]) this.annotationsByType.keySet().toArray(new String[this.annotationsByType.size()]));
	}
	public String getAnnotationNestingOrder() {
		return null;
	}
	
	public MutableAnnotation addAnnotation(Annotation annotation) {
		MutableAnnotation annot = this.addAnnotation(annotation.getType(), annotation.getStartIndex(), annotation.size());
		if (annot != null) // can happen for attempts to annotate across two text streams
			annot.copyAttributes(annotation);
		return annot;
	}
	public MutableAnnotation addAnnotation(String type, int startIndex, int size) {
		return this.doAddAnnotation(type, startIndex, (startIndex + size), null);
	}
	public MutableAnnotation addAnnotation(int startIndex, int endIndex, String type) {
		return this.doAddAnnotation(type, startIndex, endIndex, null);
	}
	private ImMutableAnnotationView doAddAnnotation(String type, int startIndex, int endIndex, ImAnnotationBase sourceBase) {
		this.checkValid();
		if (TRACK_INSTANCES) this.accessHistory.accessed();
		if (endIndex <= startIndex)
			return null;
		
		//	get first and last tokens
		ImToken firstToken = this.imTokenAtIndex(startIndex + ((sourceBase == null) ? 0 : sourceBase.getStartIndex()));
		ImToken lastToken = this.imTokenAtIndex(endIndex + ((sourceBase == null) ? 0 : sourceBase.getStartIndex()) - 1);
		
		//	get first and last words
		ImWord firstWord = ((ImWord) firstToken.imWords.get(0));
		ImWord lastWord = ((ImWord) lastToken.imWords.get(lastToken.imWords.size()-1));
		
		//	adding paragraph with normalization level paragraphs or streams, mark on text stream
		if (this.paragraphsAreLogical && ImagingConstants.PARAGRAPH_TYPE.equals(type) && firstWord.getTextStreamId().equals(lastWord.getTextStreamId())) try {
			this.docListener.parentModifyingBaseTokens = true;
			this.docListener.parentModifyingBaseDoc = true;
			ImMutableAnnotationView imav = this.addParagraphAnnotation(firstWord, lastWord, sourceBase);
			if (imav != null) {
				this.notifyAnnotationAdded(imav.base);
				return imav;
			}
		}
		finally {
			this.docListener.parentModifyingBaseDoc = false;
			this.docListener.parentModifyingBaseTokens = false;
		}
		
		//	adding paragraph or block with normalization level raw or words, mark as region
		if (!this.paragraphsAreLogical && (ImagingConstants.LINE_ANNOTATION_TYPE.equals(type) || ImagingConstants.PARAGRAPH_TYPE.equals(type) || ImagingConstants.BLOCK_ANNOTATION_TYPE.equals(type) || ImagingConstants.COLUMN_ANNOTATION_TYPE.equals(type)) && (firstWord.pageId == lastWord.pageId)) try {
			this.docListener.parentModifyingBaseTokens = true;
			this.docListener.parentModifyingBaseDoc = true;
			ImMutableAnnotationView imav = this.addRegionAnnotation(startIndex, endIndex, type, sourceBase);
			if (imav != null) {
				this.notifyAnnotationAdded(imav.base);
				return imav;
			}
		}
		finally {
			this.docListener.parentModifyingBaseDoc = false;
			this.docListener.parentModifyingBaseTokens = false;
		}
		
		//	add annotation to backing document (might go wrong if words belong to different logical text streams)
		ImAnnotation ima;
		try {
			this.docListener.parentModifyingBaseDoc = true;
			ima = this.doc.addAnnotation(firstWord, lastWord, type);
			if (ima == null)
				return null;
		}
		finally {
			this.docListener.parentModifyingBaseDoc = false;
		}
		
		//	add annotation to wrapper registers
		ImAnnotationBase imab = this.getAnnotationBase(ima);
		this.indexAnnotationBase(imab);
		
		//	notify any listeners
		this.notifyAnnotationAdded(imab);
		
		//	finally
		return ((ImMutableAnnotationView) this.getAnnotationView(imab, sourceBase, 'M'));
	}
	private ImMutableAnnotationView addParagraphAnnotation(ImWord firstWord, ImWord lastWord, ImAnnotationBase sourceBase) {
		
		//	collect blocks and paragraphs
		ArrayList blocks = new ArrayList();
		ImRegion block = null;
		ArrayList blockWords = new ArrayList();
		ArrayList paragraphs = new ArrayList();
		ImRegion paragraph = null;
		
		//	check text flow breaks around boundary words
		boolean textFlowBreakBefore = ((firstWord.getPreviousWord() == null) || ImUtils.areTextFlowBreak(firstWord.getPreviousWord(), firstWord));
		boolean textFlowBreakAfter = ((lastWord.getNextWord() == null) || ImUtils.areTextFlowBreak(lastWord, lastWord.getNextWord()));
		
		//	adjust underlying text stream ...
		for (ImWord imw = firstWord; imw != null; imw = imw.getNextWord()) {
			
			//	check block and paragraph
			if (block == null) {}
			else if (block.pageId != imw.pageId)
				block = null;
			else if (!block.bounds.includes(imw.bounds, true))
				block = null;
			if (paragraph == null) {}
			else if (paragraph.pageId != imw.pageId)
				paragraph = null;
			else if (!paragraph.bounds.includes(imw.bounds, true))
				paragraph = null;
			
			//	get and collect new block and paragraph if required
			if (block == null) {
				ImRegion[] imwBlocks = imw.getPage().getRegionsIncluding(BLOCK_ANNOTATION_TYPE, imw.bounds, true);
				if (imwBlocks.length != 0) {
					block = imwBlocks[0]; // simply use first one (there is something very hinky if there are more ...)
					blocks.add(block);
					blockWords.add(new ArrayList());
				}
			}
			if (paragraph == null) {
				ImRegion[] imwParagraphs = imw.getPage().getRegionsIncluding(ImagingConstants.PARAGRAPH_TYPE, imw.bounds, true);
				if (imwParagraphs.length != 0) {
					paragraph = imwParagraphs[0]; // simply use first one (there is something very hinky if there are more ...)
					paragraphs.add(paragraph);
				}
			}
			
			//	remember word belongs to block
			if (block != null)
				((ArrayList) blockWords.get(blockWords.size()-1)).add(imw);
			
			//	set next relation of last word to paragraph break
			if (imw == lastWord) {
				imw.setNextRelation(ImWord.NEXT_RELATION_PARAGRAPH_END);
				break;
			}
			
			//	we only need to handle internal paragraph breaks
			if ((imw.getNextRelation() != ImWord.NEXT_RELATION_PARAGRAPH_END) || (imw.getNextWord() == null))
				continue;
			
			//	we have a hyphenated word, update text stream and adjust token sequence
			if (ImUtils.isHyphenatedAfter(imw)) {
				imw.setNextRelation(ImWord.NEXT_RELATION_HYPHENATED);
				ImToken imwToken = this.getTokenFor(imw);
				ImToken nextImwToken = this.getTokenFor(imw.getNextWord());
				if (imwToken != nextImwToken)
					this.dehyphenateTokens(imwToken, nextImwToken);
			}
			
			//	we have two separate words
			else imw.setNextRelation(ImWord.NEXT_RELATION_SEPARATE);
		}
		
		//	... as well as paragraph regions (but only if boundary words have text flow breaks before and after them, respectively) ...
		System.out.println("Got " + paragraphs.size() + " paragraphs:");
		for (int p = 0; p < paragraphs.size(); p++) {
			paragraph = ((ImRegion) paragraphs.get(p));
			System.out.println(" - " + paragraph.pageId + "." + paragraph.bounds);
		}
		System.out.println("Got " + blocks.size() + " blocks:");
		for (int b = 0; b < blocks.size(); b++) {
			block = ((ImRegion) blocks.get(b));
			System.out.println(" - " + block.pageId + "." + block.bounds);
		}
		System.out.println("Text flow break before first is " + textFlowBreakBefore);
		System.out.println("Text flow break after last is " + textFlowBreakAfter);
		if (textFlowBreakBefore && textFlowBreakAfter)
			for (int b = 0; b < blocks.size(); b++) {
				block = ((ImRegion) blocks.get(b));
				ArrayList modBlockWords = ((ArrayList) blockWords.get(b));
				int modTop = ((ImWord) modBlockWords.get(0)).bounds.top;
				int modBottom = ((ImWord) modBlockWords.get(modBlockWords.size()-1)).bounds.bottom;
				adjustContainedRegions(block, ImagingConstants.PARAGRAPH_TYPE, modTop, modBottom);
			}
		
		//	... and return emulated paragraph annotation
		ImAnnotationBase imab = this.addAnnotation(firstWord, lastWord);
		ImRegion[] firstWordPageBlocks = firstWord.getPage().getRegions(BLOCK_ANNOTATION_TYPE);
		imab.pFirstBlockId = getParentBlockId(firstWordPageBlocks, firstWord);
		ImRegion[] lastWordPageBlocks = firstWord.getPage().getRegions(BLOCK_ANNOTATION_TYPE);
		imab.pLastBlockId = getParentBlockId(lastWordPageBlocks, lastWord);
		return ((ImMutableAnnotationView) this.getAnnotationView(imab, sourceBase, 'M'));
	}
	private ImMutableAnnotationView addRegionAnnotation(int startIndex, int endIndex, String type, ImAnnotationBase sourceBase) {
		
		//	collect blocks and paragraphs or columns and blocks ...
		String parentRegionType = ((ImagingConstants.PARAGRAPH_TYPE.equals(type) || ImagingConstants.LINE_ANNOTATION_TYPE.equals(type)) ? ImagingConstants.BLOCK_ANNOTATION_TYPE : ImagingConstants.COLUMN_ANNOTATION_TYPE);
		ArrayList parentRegions = new ArrayList();
		ImRegion parentRegion = null;
		ArrayList parentRegionWords = new ArrayList();
		ArrayList regions = new ArrayList();
		ImRegion region = null;
		
		//	... along the words to be annotated
		for (int t = startIndex; t < endIndex; t++) {
			ImToken token = this.imTokenAtIndex(t);
			for (int w = 0; w < token.imWords.size(); w++) {
				ImWord imw = ((ImWord) token.imWords.get(w));
				
				//	check block and paragraph or column and block
				if (parentRegion == null) {}
				else if (parentRegion.pageId != imw.pageId)
					parentRegion = null;
				else if (!parentRegion.bounds.includes(imw.bounds, true))
					parentRegion = null;
				if (region == null) {}
				else if (region.pageId != imw.pageId)
					region = null;
				else if (!region.bounds.includes(imw.bounds, true))
					region = null;
				
				//	get and collect new block and paragraph or column and block if required
				if (parentRegion == null) {
					ImRegion[] imwParentRegions = imw.getPage().getRegionsIncluding(parentRegionType, imw.bounds, true);
					if (imwParentRegions.length != 0) {
						parentRegion = imwParentRegions[0]; // simply use first one (there is something very hinky if there are more ...)
						parentRegions.add(parentRegion);
						parentRegionWords.add(new ArrayList());
					}
				}
				if (region == null) {
					ImRegion[] imwRegions = imw.getPage().getRegionsIncluding(type, imw.bounds, true);
					if (imwRegions.length != 0) {
						region = imwRegions[0]; // simply use first one (there is something very hinky if there are more ...)
						regions.add(region);
					}
				}
				
				//	remember word belongs to block or column
				if (parentRegion != null)
					((ArrayList) parentRegionWords.get(parentRegionWords.size()-1)).add(imw);
			}
		}
		
		//	adjust paragraphs or blocks ...
		System.out.println("Got " + regions.size() + " " + type + "s:");
		for (int r = 0; r < regions.size(); r++) {
			region = ((ImRegion) regions.get(r));
			System.out.println(" - " + region.pageId + "." + region.bounds);
		}
		System.out.println("Got " + parentRegions.size() + " " + parentRegionType + "s:");
		for (int p = 0; p < parentRegions.size(); p++) {
			parentRegion = ((ImRegion) parentRegions.get(p));
			System.out.println(" - " + parentRegion.pageId + "." + parentRegion.bounds);
		}
		
		//	... if we have exactly one parent region (handle line separately, no need to do any top and bottom splits) ...
		if (ImagingConstants.LINE_ANNOTATION_TYPE.equals(type) && (parentRegions.size() == 1)) {
			parentRegion = ((ImRegion) parentRegions.get(0));
			ArrayList modParentRegionWords = ((ArrayList) parentRegionWords.get(0));
			int lineTop = ((ImWord) modParentRegionWords.get(0)).bounds.top;
			int lineBottom = ((ImWord) modParentRegionWords.get(modParentRegionWords.size()-1)).bounds.bottom;
			ImRegion modLine = markRegion(parentRegion, lineTop, lineBottom, type);
			
			//	... and return resulting annotation
			if (modLine != null) {
				ImAnnotationBase imab = this.addAnnotation(modLine, modLine.getWords());
				if (imab != null)
					return ((ImMutableAnnotationView) this.getAnnotationView(imab, sourceBase, 'M'));
			}
		}
		
		//	 ... if we have exactly one parent region ...
		else if (parentRegions.size() == 1) {
			parentRegion = ((ImRegion) parentRegions.get(0));
			ArrayList modParentRegionWords = ((ArrayList) parentRegionWords.get(0));
			int modTop = ((ImWord) modParentRegionWords.get(0)).bounds.top;
			int modBottom = ((ImWord) modParentRegionWords.get(modParentRegionWords.size()-1)).bounds.bottom;
			ImRegion modRegion = adjustContainedRegions(parentRegion, type, modTop, modBottom);
			
			//	... and return resulting annotation
			if (modRegion != null) {
				ImAnnotationBase imab = this.addAnnotation(modRegion, modRegion.getWords() /* need to make sure we get them all */);
				if (imab != null)
					return ((ImMutableAnnotationView) this.getAnnotationView(imab, sourceBase, 'M'));
			}
		}
		
		//	unable to annotate across parent regions
		return null;
	}
	private static ImRegion adjustContainedRegions(ImRegion parentRegion, String type, int modTop, int modBottom) {
		ArrayList exRegions = new ArrayList(Arrays.asList(parentRegion.getRegions(type)));
		Collections.sort(exRegions, ImUtils.topDownOrder);
		
		//	eliminate paragraphs or blocks above and below modified one
		int topUnModBottom = parentRegion.bounds.top;
		int bottomUnModTop = parentRegion.bounds.bottom;
		for (int p = 0; p < exRegions.size(); p++) {
			ImRegion exRegion = ((ImRegion) exRegions.get(p));
			if (exRegion.bounds.bottom <= modTop) {
				topUnModBottom = Math.max(topUnModBottom, exRegion.bounds.bottom);
				exRegions.remove(p--); // this one is untouched, above modification
			}
			else if (modBottom <= exRegion.bounds.top) {
				bottomUnModTop = Math.min(bottomUnModTop, exRegion.bounds.top);
				exRegions.remove(p--); // this one is untouched, below modification
			}
		}
		
		//	get extent of newly created paragraph or block
		int exTop = (exRegions.isEmpty() ? topUnModBottom : ((ImRegion) exRegions.get(0)).bounds.top);
		int exBottom = (exRegions.isEmpty() ? bottomUnModTop : ((ImRegion) exRegions.get(exRegions.size()-1)).bounds.bottom);
		
		//	save all the hassle if there is no actual modification (one existing paragraph with correct bounds)
		//	TODO cut a few pixels of slack on top and bottom
		if ((exRegions.size() == 1) && (exTop == modTop) && (modBottom == exBottom))
			return ((ImRegion) exRegions.get(0));
		
		//	mark paragraph above newly created one (there might have been a split)
		if (exTop < modTop)
			markRegion(parentRegion, exTop, modTop, type);
		
		//	mark paragraph below newly created one (there might have been a split)
		if (modBottom < exBottom)
			markRegion(parentRegion, modBottom, exBottom, type);
		
		//	mark newly created paragraph or block
		ImRegion modRegion = markRegion(parentRegion, modTop, modBottom, type);
		
		//	remove overlapping existing paragraphs or blocks
		for (int r = 0; r < exRegions.size(); r++) {
			ImRegion exRegion = ((ImRegion) exRegions.get(r));
			exRegion.getPage().removeRegion(exRegion);
		}
		
		//	return modified region
		return modRegion;
	}
	private static ImRegion markRegion(ImRegion parentRegion, int top, int bottom, String type) {
		BoundingBox modRegionBounds = new BoundingBox(parentRegion.bounds.left, parentRegion.bounds.right, top, bottom);
//		ImRegion[] modRegionLines = parentRegion.getPage().getRegionsInside(LINE_ANNOTATION_TYPE, modRegionBounds, true);
//		if (modRegionLines.length == 0)
//			return null;
//		modRegionBounds = ImLayoutObject.getAggregateBox(modRegionLines);
		ImWord[] modRegionWords = parentRegion.getPage().getWordsInside(modRegionBounds);
		if (modRegionWords.length == 0)
			return null;
		modRegionBounds = ImLayoutObject.getAggregateBox(modRegionWords);
		ImRegion[] exRegions = parentRegion.getPage().getRegionsInside(type, modRegionBounds, true);
		for (int r = 0; r < exRegions.length; r++) {
			if (modRegionBounds.equals(exRegions[r].bounds))
				return exRegions[r]; // prevent duplicates
		}
		ImRegion modRegion = new ImRegion(parentRegion.getPage(), modRegionBounds, type);
		if (parentRegion.hasAttribute(TEXT_ORIENTATION_ATTRIBUTE))
			modRegion.setAttribute(TEXT_ORIENTATION_ATTRIBUTE, parentRegion.getAttribute(TEXT_ORIENTATION_ATTRIBUTE));
		if (parentRegion.hasAttribute(INDENTATION_ATTRIBUTE))
			modRegion.setAttribute(INDENTATION_ATTRIBUTE, parentRegion.getAttribute(INDENTATION_ATTRIBUTE));
		return modRegion;
	}
	private ImAnnotationBase getAnnotationBase(ImAnnotation ima) {
		ImAnnotationBase imab = ((ImAnnotationBase) this.annotationBasesByAnnotations.get(ima));
		if (imab == null) {
			imab = new ImAnnotationBase(ima);
			this.annotationBasesByAnnotations.put(ima, imab);
		}
		return imab;
	}
	private ImAnnotationBase getAnnotationBase(ImRegion imr, ImWord[] imrWords, boolean imrIsTable, boolean imrIsTableHeader) {
		String imrKey = this.getRegionKey(imr, null);
		ImAnnotationBase imab = ((ImAnnotationBase) this.annotationBasesByAnnotations.get(imrKey));
		if (imab == null) {
			imab = new ImAnnotationBase(imr, imrWords, imrIsTable, imrIsTableHeader);
			this.annotationBasesByRegions.put(imrKey, imab);
		}
		return imab;
	}
	private String getRegionKey(ImRegion imr, String oldType) {
		return (((oldType == null) ? imr.getType() : oldType) + "@" + imr.pageId + "." + imr.bounds);
	}
	private ImAnnotationView getAnnotationView(ImAnnotationBase imab, ImAnnotationBase source, char type) {
		HashMap sourceImavs = ((HashMap) this.annotationViewsByBases.get(source));
		if (sourceImavs == null) {
			sourceImavs = new HashMap();
			this.annotationViewsByBases.put(source, sourceImavs);
		}
		ImAnnotationView imav = ((ImAnnotationView) sourceImavs.get(imab));
		if ((type == 'Q') && (imav instanceof ImQueriableAnnotationView))
			return ((ImQueriableAnnotationView) imav);
		if ((type == 'E') && (imav instanceof ImEditableAnnotationView))
			return ((ImEditableAnnotationView) imav);
		if ((type == 'M') && (imav instanceof ImMutableAnnotationView))
			return ((ImMutableAnnotationView) imav);
		if (type == 'Q')
			imav = new ImQueriableAnnotationView(imab, source);
		else if (type == 'E')
			imav = new ImEditableAnnotationView(imab, source);
		else if (type == 'M')
			imav = new ImMutableAnnotationView(imab, source);
		else return null;
		sourceImavs.put(imab, imav);
		return imav;
	}
	public EditableAnnotation getEditableAnnotation(String id) {
		return ((EditableAnnotation) this.doGetAnnotation(null, id, 'E'));
	}
	public EditableAnnotation[] getEditableAnnotations() {
		return ((EditableAnnotation[]) this.doGetAnnotations(null, 'E'));
	}
	public EditableAnnotation[] getEditableAnnotations(String type) {
		return ((EditableAnnotation[]) this.doGetAnnotations(type, null, 'E'));
	}
	public EditableAnnotation[] getEditableAnnotationsSpanning(int startIndex, int endIndex) {
		return ((EditableAnnotation[]) this.doGetAnnotations(null, startIndex, endIndex, 'E'));
	}
	public EditableAnnotation[] getEditableAnnotationsSpanning(String type, int startIndex, int endIndex) {
		return ((EditableAnnotation[]) this.doGetAnnotations(type, null, startIndex, endIndex, 'E'));
	}
	public EditableAnnotation[] getEditableAnnotationsOverlapping(int startIndex, int endIndex) {
		return ((EditableAnnotation[]) this.doGetAnnotations(null, (endIndex - 1), (startIndex + 1), 'E'));
	}
	public EditableAnnotation[] getEditableAnnotationsOverlapping(String type, int startIndex, int endIndex) {
		return ((EditableAnnotation[]) this.doGetAnnotations(type, null, (endIndex - 1), (startIndex + 1), 'E'));
	}
	public Annotation removeAnnotation(Annotation annotation) {
		this.checkValid();
//		
//		//	this is none of ours
//		if (!(annotation instanceof ImAnnotationView))
//			return annotation;
//		
//		//	get annotation base
//		ImAnnotationBase imab = ((ImAnnotationView) annotation).base;
		
		//	get annotation base
		ImAnnotationBase imab = this.getBaseOf(annotation);
		
		//	this is none of ours
		if (imab == null)
			return annotation;
		
		//	remove base from indexes
		this.dropAnnotationView((ImAnnotationView) annotation);
		this.dropAnnotationBase(imab);
		this.unIndexAnnotationBase(imab, null);
		this.annotationBasesByUUIDs.remove(imab.getId());
		
		//	remove annotation or region from wrapped document
		try {
			this.docListener.parentModifyingBaseDoc = true;
			if (imab.aData != null)
				this.doc.removeAnnotation(imab.aData);
			else if (imab.rData != null)
				imab.rData.getPage().removeRegion(imab.rData);
			//	nothing to do for token/word annotations
		}
		finally {
			this.docListener.parentModifyingBaseDoc = false;
		}
		
		//	notify any listeners
		if (imab.aData != null)
			this.notifyAnnotationRemoved(imab.aData, imab.getStartIndex(), imab.size(), imab.getId());
		else if (imab.rData != null)
			this.notifyAnnotationRemoved(imab.rData, imab.getStartIndex(), imab.size(), imab.getId());
		
		//	no need to create some dedicated copy in a short-lived wrapper
		return annotation;
	}
	private ImAnnotationBase getBaseOf(Annotation annotation) {
		if (annotation instanceof ImAnnotationView) // the base case
			return ((ImAnnotationView) annotation).base;
		else return ((ImAnnotationBase) this.annotationBasesByUUIDs.get(annotation.getAnnotationID())); // view might be inside some wrapper, etc.
	}
	private void dropAnnotationBase(ImAnnotationBase imab) {
		if (imab.aData != null)
			this.annotationBasesByAnnotations.remove(imab.aData);
		else if (imab.rData != null)
			this.annotationBasesByRegions.remove(this.getRegionKey(imab.rData, null));
		this.annotationViewsByBases.remove(imab);
	}
	private void dropAnnotationView(ImAnnotationView imav) {
		HashMap sourceBasedViews = ((HashMap) this.annotationViewsByBases.get(imav.sourceBase));
		if (sourceBasedViews != null)
			sourceBasedViews.remove(imav.base);
	}
	private void unIndexAnnotationBase(ImAnnotationBase imab, String oldType) {
		this.annotations.removeAnnotation(imab);
		String uiType = ((oldType == null) ? imab.getType() : oldType);
		ImAnnotationList typeAnnotsNew = ((ImAnnotationList) this.annotationsByType.get(uiType));
		if (typeAnnotsNew != null) {
			typeAnnotsNew.removeAnnotation(imab);
			if (typeAnnotsNew.isEmpty())
				this.annotationsByType.remove(uiType);
		}
	}
	public TokenSequence removeTokens(Annotation annotation) {
		return this.removeTokensAt(annotation.getStartIndex(), annotation.size());
	}
	void replacingTokensAt(int index, ImToken[] replaced, ImToken[] replacement) {
		this.checkValid();
		
		//	index replacement words (main index not yet updated here)
		HashMap rImWordTokens = new HashMap();
		for (int r = 0; r < replacement.length; r++) {
			for (int w = 0; w < replacement[r].imWords.size(); w++)
				rImWordTokens.put(((ImWord) replacement[r].imWords.get(w)).getLocalID(), replacement[r]);
		}
		
		//	inspect annotation bases, collecting the ones to update (we cannot update right away, as this blows sort order and thus hampers removal)
		HashMap annotationsToUpdate = new HashMap();
		ArrayList annotationsToRemove = new ArrayList();
		for (int a = 0; a < this.annotations.size(); a++) {
			ImAnnotationBase base = this.annotations.getAnnotation(a);
			
			//	this one's unaffected
			if (base.getEndIndex() <= index)
				continue;
			
			//	this one's after the deletion, we're done
			if ((index + replaced.length) <= base.getStartIndex())
				break;
			
			//	region annotation, update covered words
			if (base.rDataWords != null) {
				
				//	collect non-deleted words
				ArrayList rDataWords = new ArrayList();
				for (int w = 0; w < base.rDataWords.length; w++) {
					ImToken imt = this.getTokenFor(base.rDataWords[w]);
					if ((imt != null) && ((imt.index < index) || ((index + replaced.length) <= imt.index)))
						rDataWords.add(base.rDataWords[w]);
					else if ((imt != null) && rImWordTokens.containsKey(base.rDataWords[w].getLocalID()))
						rDataWords.add(base.rDataWords[w]);
				}
				
				//	 no changes
				if (base.rDataWords.length == rDataWords.size())
					continue;
				
				//	this one's empty, remove it
				else if (rDataWords.isEmpty()) {
					((ImAnnotationList) this.annotationsByType.get(base.getType())).removeAnnotation(base);
					annotationsToRemove.add(base);
				}
				
				//	store updates to execute them later
				else annotationsToUpdate.put(base, rDataWords);
			}
			
			//	text stream annotation
			else if (base.aData != null) {
				
				//	get first and last tokens
				ImToken firstToken = this.getTokenFor(base.aData.getFirstWord());
				ImToken lastToken = this.getTokenFor(base.aData.getLastWord());
				
				//	either of first and last word is being cut without replacement, remove annotation from wrapper
				if (((index <= firstToken.index) && !rImWordTokens.containsKey(base.aData.getFirstWord().getLocalID())) || ((lastToken.index < (index + replaced.length)) && !rImWordTokens.containsKey(base.aData.getLastWord().getLocalID()))) {
					((ImAnnotationList) this.annotationsByType.get(base.getType())).removeAnnotation(base);
					annotationsToRemove.add(base);
				}
				
				//	either one of first and last word has been cut, remove annotation from wrapped document
				if (((index <= firstToken.index) && !rImWordTokens.containsKey(base.aData.getFirstWord().getLocalID())) != ((lastToken.index < (index + replaced.length)) && !rImWordTokens.containsKey(base.aData.getLastWord().getLocalID())))
					this.doc.removeAnnotation(base.aData);
			}
			
			//	word / token annotation
			else if (base.tData != null) {
				
				//	this one's in range, remove it
				if ((index <= base.tData.index) && (base.tData.index < (index + replaced.length))) {
					((ImAnnotationList) this.annotationsByType.get(base.getType())).removeAnnotation(base);
					annotationsToRemove.add(base);
				}
			}
		}
		
		//	update covered words, and have bounds re-computed
		for (Iterator abit = annotationsToUpdate.keySet().iterator(); abit.hasNext();) {
			ImAnnotationBase base = ((ImAnnotationBase) abit.next());
			ArrayList rDataWords = ((ArrayList) annotationsToUpdate.get(base));
			base.rDataWords = ((ImWord[]) rDataWords.toArray(new ImWord[rDataWords.size()]));
			base.boundingBox = null;
		}
		
		//	remove annotations
		for (int a = 0; a < annotationsToRemove.size(); a++) {
			ImAnnotationBase base = ((ImAnnotationBase) annotationsToRemove.get(a));
			this.annotations.removeAnnotation(base);
			this.annotationBasesByUUIDs.remove(base.getId());
			ImAnnotationList typeAnnots = ((ImAnnotationList) this.annotationsByType.get(base.getType()));
			if (typeAnnots != null) {
				typeAnnots.removeAnnotation(base);
				if (typeAnnots.isEmpty())
					this.annotationsByType.remove(base.getType());
			}
		}
	}
	void replacedTokensAt(int index, ImToken[] replaced, ImToken[] replacement) {
		if (this.showTokensAsWordAnnotations) {
			for (int r = 0; r < replacement.length; r++)
				this.indexAnnotationBase(new ImAnnotationBase(replacement[r]));
		}
	}
	public MutableAnnotation getMutableAnnotation(String id) {
		return ((MutableAnnotation) this.doGetAnnotation(null, id, 'M'));
	}
	public MutableAnnotation[] getMutableAnnotations() {
		return ((MutableAnnotation[]) this.doGetAnnotations(null, 'M'));
	}
	public MutableAnnotation[] getMutableAnnotations(String type) {
		return ((MutableAnnotation[]) this.doGetAnnotations(type, null, 'M'));
	}
	public MutableAnnotation[] getMutableAnnotationsSpanning(int startIndex, int endIndex) {
		return ((MutableAnnotation[]) doGetAnnotations(null, startIndex, endIndex, 'M'));
	}
	public MutableAnnotation[] getMutableAnnotationsSpanning(String type, int startIndex, int endIndex) {
		return ((MutableAnnotation[]) doGetAnnotations(type, null, startIndex, endIndex, 'M'));
	}
	public MutableAnnotation[] getMutableAnnotationsOverlapping(int startIndex, int endIndex) {
		return ((MutableAnnotation[]) doGetAnnotations(null, (endIndex - 1), (startIndex + 1), 'M'));
	}
	public MutableAnnotation[] getMutableAnnotationsOverlapping(String type, int startIndex, int endIndex) {
		return ((MutableAnnotation[]) doGetAnnotations(type, null, (endIndex - 1), (startIndex + 1), 'M'));
	}
//	public void addAnnotationListener(AnnotationListener al) {/* no use listening on a short-lived wrapper */}
//	public void removeAnnotationListener(AnnotationListener al) {/* no use listening on a short-lived wrapper */}
	
	public boolean hasAttribute(String name) {
		this.checkValid();
		if (TRACK_INSTANCES) this.accessHistory.accessed();
		if (DocumentRoot.DOCUMENT_ID_ATTRIBUTE.equals(name))
			return true;
		else return this.doc.hasAttribute(name);
	}
	public Object getAttribute(String name) {
		this.checkValid();
		if (TRACK_INSTANCES) this.accessHistory.accessed();
		if (DocumentRoot.DOCUMENT_ID_ATTRIBUTE.equals(name))
			return this.doc.docId;
		else return this.doc.getAttribute(name);
	}
	public Object getAttribute(String name, Object def) {
		this.checkValid();
		if (TRACK_INSTANCES) this.accessHistory.accessed();
		if (DocumentRoot.DOCUMENT_ID_ATTRIBUTE.equals(name))
			return this.doc.docId;
		else return this.doc.getAttribute(name, def);
	}
	public String[] getAttributeNames() {
		this.checkValid();
		if (TRACK_INSTANCES) this.accessHistory.accessed();
		TreeSet ans = new TreeSet(Arrays.asList(this.doc.getAttributeNames()));
		ans.add(DocumentRoot.DOCUMENT_ID_ATTRIBUTE);
		return ((String[]) ans.toArray(new String[ans.size()]));
	}
	public void setAttribute(String name) {
		this.checkValid();
		if (TRACK_INSTANCES) this.accessHistory.accessed();
		this.doc.setAttribute(name);
	}
	public Object setAttribute(String name, Object value) {
		this.checkValid();
		if (TRACK_INSTANCES) this.accessHistory.accessed();
		return this.doc.setAttribute(name, value);
	}
	public void copyAttributes(Attributed source) {
		this.checkValid();
		if (TRACK_INSTANCES) this.accessHistory.accessed();
		this.doc.copyAttributes(source);
	}
	public Object removeAttribute(String name) {
		this.checkValid();
		if (TRACK_INSTANCES) this.accessHistory.accessed();
		return this.doc.removeAttribute(name);
	}
	public void clearAttributes() {
		this.checkValid();
		if (TRACK_INSTANCES) this.accessHistory.accessed();
		this.doc.clearAttributes();
	}
	
	public String getDocumentProperty(String propertyName) {
		this.checkValid();
		if (TRACK_INSTANCES) this.accessHistory.accessed();
		if (DocumentRoot.DOCUMENT_ID_ATTRIBUTE.equals(propertyName))
			return this.doc.docId;
		else return this.doc.getDocumentProperty(propertyName);
	}
	public String getDocumentProperty(String propertyName, String defaultValue) {
		this.checkValid();
		if (TRACK_INSTANCES) this.accessHistory.accessed();
		if (DocumentRoot.DOCUMENT_ID_ATTRIBUTE.equals(propertyName))
			return this.doc.docId;
		else return this.doc.getDocumentProperty(propertyName, defaultValue);
	}
	public String[] getDocumentPropertyNames() {
		this.checkValid();
		if (TRACK_INSTANCES) this.accessHistory.accessed();
//		return this.doc.getDocumentPropertyNames();
		TreeSet dpNameSet = new TreeSet();
		String[] dpNames = this.doc.getDocumentPropertyNames();
		if (dpNames != null)
			dpNameSet.addAll(Arrays.asList(dpNames));
		dpNameSet.add(DocumentRoot.DOCUMENT_ID_ATTRIBUTE);
		return ((String[]) dpNameSet.toArray(new String[dpNameSet.size()]));
	}
	public String setDocumentProperty(String propertyName, String value) {
		this.checkValid();
		if (TRACK_INSTANCES) this.accessHistory.accessed();
		return this.doc.setDocumentProperty(propertyName, value);
	}
	public String removeDocumentProperty(String propertyName) {
		this.checkValid();
		if (TRACK_INSTANCES) this.accessHistory.accessed();
		return this.doc.removeDocumentProperty(propertyName);
	}
	public void clearDocumentProperties() {
		this.checkValid();
		if (TRACK_INSTANCES) this.accessHistory.accessed();
		this.doc.clearDocumentProperties();
	}
	
	public String setAnnotationNestingOrder(String ano) {
		this.checkValid();
		String oldAno = this.annotationNestingOrder;
		this.annotationNestingOrder = ano;
//		if ((this.annotationNestingOrder != null) && !this.annotationNestingOrder.equals(oldAno))
//			Collections.sort(this.annotations, this.typedAnnotationBaseOrder);
		if ((this.annotationNestingOrder != null) && !this.annotationNestingOrder.equals(oldAno))
			this.orderModCount++;
		return oldAno;
	}
	
	public int compareTo(Object obj) {
		if (obj instanceof Annotation)
			return AnnotationUtils.compare(this, ((Annotation) obj));
		else return -1;
	}
	
	private static String getParentBlockId(ImRegion[] blocks, ImWord word) {
		for (int b = 0; b < blocks.length; b++) {
			if (blocks[b].bounds.includes(word.bounds, true))
				return (blocks[b].pageId + "." + blocks[b].bounds.toString());
		}
		return null; // should never happen for main text words, unless we have no blocks
	}
//	
//	private static String asHex(int i, int bytes) {
//		StringBuffer hex = new StringBuffer();
//		while (bytes != 0) {
//			hex.insert(0, hexDigits.charAt(i & 0xF));
//			i >>>= 4;
//			hex.insert(0, hexDigits.charAt(i & 0xF));
//			i >>>= 4;
//			bytes--;
//		}
//		return hex.toString();
//	}
//	private static String hexXor(String str1, String str2) {
//		str1 = str1.toUpperCase();
//		str2 = str2.toUpperCase();
//		StringBuffer xor = new StringBuffer();
//		for (int c = 0; c < Math.max(str1.length(), str2.length()); c++) {
//			int i1 = ((c < str1.length()) ? hexDigits.indexOf(str1.charAt(c)) : 0);
//			int i2 = ((c < str2.length()) ? hexDigits.indexOf(str2.charAt(c)) : 0);
//			xor.append(hexDigits.charAt(i1 ^ i2));
//		}
//		return xor.toString();
//	}
//	private static final String hexDigits = "0123456789ABCDEF";
	
	/**
	 * Retrieve the type of the annotations that regions of a given type show
	 * up as in the wrapper. This method mainly maps table related region types
	 * to respective annotation types, which are kept HTML compliant. If regions
	 * of the argument type are not mapped to annotations at all, this method
	 * returns null.
	 * @param regType the region type to map
	 * @return the corresponding annotation type
	 */
	public static String getRegionAnnotationType(String regType) {
		if (ImRegion.TABLE_CELL_TYPE.equals(regType))
			return TABLE_CELL_ANNOTATION_TYPE;
		else if (ImRegion.TABLE_ROW_TYPE.equals(regType))
			return TABLE_ROW_ANNOTATION_TYPE;
		else if (ImRegion.TABLE_COL_TYPE.equals(regType))
			return null;
		else return regType;
	}
//	
//	//	TEST ONLY !!!
//	public static void main(String[] args) throws Exception {
//		File docFolder = new File("E:/Testdaten/PdfExtract/Zootaxa/zootaxa.5004.2.6.pdf.imdir");
//		ImDocument doc = ImDocumentIO.loadDocument(docFolder);
//		ImDocumentRoot xmlDoc = new ImDocumentRoot(doc, NORMALIZE_CHARACTERS);
//	}
//	
//	//	TEST ONLY !!!
//	public static void main(String[] args) throws Exception {
//		File docFolder = new File("E:/Testdaten/PdfExtract/EJT/ejt-502_trietsch_miko_deans.pdf.gPathTest.imdir");
//		ImDocument doc = ImDocumentIO.loadDocument(docFolder);
//		ImDocumentRoot xmlDoc = new ImDocumentRoot(doc, (NORMALIZE_CHARACTERS | NORMALIZATION_LEVEL_PARAGRAPHS));
//		QueriableAnnotation[] paras = xmlDoc.getAnnotations(MutableAnnotation.PARAGRAPH_TYPE);
//		for (int p = 0; p < paras.length; p++) {
//			System.out.println("paragraph@" + paras[p].getStartIndex() + "-" + paras[p].getEndIndex());
//			Annotation[] tns = paras[p].getAnnotations("taxonomicName");
//			if (tns.length == 0)
//				continue;
//			Annotation[] brcs = paras[p].getAnnotations("bibRefCitation");
//			if (brcs.length == 0)
//				continue;
//			for(int c = 0; c < brcs.length; c++)
//				System.out.println(" - " + brcs[c].getType() + "@" + brcs[c].getStartIndex() + "-" + brcs[c].getEndIndex());
//			for (int t = 0; t < tns.length; t++) {
//				System.out.println(" - " + tns[t].getType() + "@" + tns[t].getStartIndex() + "-" + tns[t].getEndIndex());
//				Annotation[] obrcs = paras[p].getAnnotationsOverlapping("bibRefCitation", tns[t].getStartIndex(), tns[t].getEndIndex());
//				for(int c = 0; c < obrcs.length; c++)
//					System.out.println("   - overlapping " + obrcs[c].getType() + "@" + obrcs[c].getStartIndex() + "-" + obrcs[c].getEndIndex());
//				Annotation[] stcgs = paras[p].getAnnotationsOverlapping("treatmentCitationGroupOld", tns[t].getStartIndex(), tns[t].getEndIndex());
//				for(int c = 0; c < stcgs.length; c++)
//					System.out.println("   - spanning " + stcgs[c].getType() + "@" + stcgs[c].getStartIndex() + "-" + stcgs[c].getEndIndex());
//			}
//		}
//	}
//	
//	//	!!! TEST ONLY !!!
//	public static void main(String[] args) throws Exception {
//		try {
//			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
//		} catch (Exception e) {}
//		
//		final File imfDataPath = new File("E:/Testdaten/PdfExtract/");
//		
//		String docName;
//		
//		//	only use documents we have an IMF version of right now
////		docName = "19970730111_ftp.pdf.imf"; // scanned, with born-digital looking text embedded, comes out better when loaded from image
//		docName = "MEZ_909-919.pdf.imf"; // born-digital, renders perfectly fine
//		
//		//	load document
//		InputStream docIn = new BufferedInputStream(new FileInputStream(new File(imfDataPath, docName)));
//		ImDocument doc = ImDocumentIO.loadDocument(docIn);
//		docIn.close();
//		ImPage[] pages = doc.getPages();
//		System.out.println("Got document with " + pages.length + " pages");
//		for (int p = 0; p < pages.length; p++) {
//			System.out.println(" - page " + p + ":");
//			System.out.println("   - got " + pages[p].getWords().length + " words");
//			System.out.println("   - got " + pages[p].getRegions().length + " regions");
//			PageImage pi = pages[p].getPageImage();
//			System.out.println("   - got page image sized " + pi.image.getWidth() + "x" + pi.image.getHeight());
//		}
//		
//		//	set page to work with
//		aimAtPage = -1;
//		
//		//	mark paragraph ends
//		for (int pg = 0; pg < pages.length; pg++) {
//			if ((aimAtPage != -1) && (pg != aimAtPage))
//				continue;
//			ImRegion[] paragraphs = pages[pg].getRegions(MutableAnnotation.PARAGRAPH_TYPE);
//			for (int p = 0; p < paragraphs.length; p++) {
//				ImWord[] pWords = paragraphs[p].getWords();
//				pWords[pWords.length - 1].setNextRelation(ImWord.NEXT_RELATION_PARAGRAPH_END);
//			}
//		}
//		System.out.println("Paragraph ends marked");
//		
//		//	wrap document
//		ImDocumentRoot idr = new ImDocumentRoot(doc, NORMALIZATION_LEVEL_WORDS);
//		
//		//	remove page numbers
//		for (int t = 0; t < idr.size(); t++) {
//			if (idr.valueAt(t).matches("9[0-9]+"))
//				idr.removeTokensAt(t--, 1);
//			else if (idr.valueAt(t).matches("[\\_]+"))
//				idr.removeTokensAt(t--, 1);
//		}
//		
//		//	alter a few tokens
//		for (int t = 0; t < idr.size(); t++) {
//			String value = idr.valueAt(t);
//			System.out.println(t + ": " + value);
//			value = value.replaceAll("[aou]", "");
//			value = value.replaceAll("[i]", "ii");
//			value = value.replaceAll("[e]", "ee");
//			if (value.length() == 0)
//				value = "X";
//			idr.setValueAt((value.substring(0, 1).toUpperCase() + value.substring(1).toLowerCase()), t);
//		}
//		
//		//	write XML for a first test
//		AnnotationUtils.writeXML(idr, new OutputStreamWriter(System.out));
//	}
//	private static int aimAtPage = -1;
}