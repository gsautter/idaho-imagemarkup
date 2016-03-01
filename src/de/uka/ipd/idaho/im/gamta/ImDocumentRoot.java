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
package de.uka.ipd.idaho.im.gamta;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
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

import javax.swing.UIManager;

import de.uka.ipd.idaho.gamta.Annotation;
import de.uka.ipd.idaho.gamta.AnnotationListener;
import de.uka.ipd.idaho.gamta.AnnotationUtils;
import de.uka.ipd.idaho.gamta.Attributed;
import de.uka.ipd.idaho.gamta.CharSequenceListener;
import de.uka.ipd.idaho.gamta.DocumentRoot;
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
import de.uka.ipd.idaho.gamta.util.constants.TableConstants;
import de.uka.ipd.idaho.gamta.util.imaging.BoundingBox;
import de.uka.ipd.idaho.gamta.util.imaging.ImagingConstants;
import de.uka.ipd.idaho.gamta.util.imaging.PageImage;
import de.uka.ipd.idaho.im.ImAnnotation;
import de.uka.ipd.idaho.im.ImDocument;
import de.uka.ipd.idaho.im.ImPage;
import de.uka.ipd.idaho.im.ImRegion;
import de.uka.ipd.idaho.im.ImWord;
import de.uka.ipd.idaho.im.util.ImDocumentIO;
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
	
	class ImAnnotationBase {
		private ImAnnotation aData;
		private ImRegion rData;
		private ImWord[] rDataWords;
		private int leftEmptyCellsToSpan = 0;
		private int rightEmptyCellsToSpan = 0;
		private ImToken tData;
		private ImWord pFirstWord;
		private ImWord pLastWord;
		private Attributed pAttributes;
		private BoundingBox boundingBox = null;
		ImAnnotationBase(ImAnnotation aData) {
			this.aData = aData;
		}
		ImAnnotationBase(ImRegion rData, ImWord[] rDataWords) {
			this.rData = rData;
			this.rDataWords = rDataWords;
			Arrays.sort(this.rDataWords, ImUtils.textStreamOrder);
		}
		ImAnnotationBase(ImToken tData) {
			this.tData = tData;
		}
		ImAnnotationBase(ImWord pfw, ImWord plw) {
			this.pFirstWord = pfw;
			this.pLastWord = plw;
			this.pAttributes = new AbstractAttributed() {
				public Object getAttribute(String name, Object def) {
					if (PAGE_ID_ATTRIBUTE.equals(name))
						return ("" + pFirstWord.pageId);
					if (LAST_PAGE_ID_ATTRIBUTE.equals(name))
						return ("" + pLastWord.pageId);
					if (PAGE_NUMBER_ATTRIBUTE.equals(name) && pFirstWord.getPage().hasAttribute(PAGE_NUMBER_ATTRIBUTE))
						return pFirstWord.getPage().getAttribute(PAGE_NUMBER_ATTRIBUTE);
					if (LAST_PAGE_NUMBER_ATTRIBUTE.equals(name) && pLastWord.getPage().hasAttribute(PAGE_NUMBER_ATTRIBUTE))
						return pLastWord.getPage().getAttribute(PAGE_NUMBER_ATTRIBUTE);
					if (TYPE_ATTRIBUTE.equals(name))
						return pFirstWord.getTextStreamType();
					return super.getAttribute(name, def);
				}
				public Object getAttribute(String name) {
					if (PAGE_ID_ATTRIBUTE.equals(name))
						return ("" + pFirstWord.pageId);
					if (LAST_PAGE_ID_ATTRIBUTE.equals(name))
						return ("" + pLastWord.pageId);
					if (PAGE_NUMBER_ATTRIBUTE.equals(name) && pFirstWord.getPage().hasAttribute(PAGE_NUMBER_ATTRIBUTE))
						return pFirstWord.getPage().getAttribute(PAGE_NUMBER_ATTRIBUTE);
					if (LAST_PAGE_NUMBER_ATTRIBUTE.equals(name) && pLastWord.getPage().hasAttribute(PAGE_NUMBER_ATTRIBUTE))
						return pLastWord.getPage().getAttribute(PAGE_NUMBER_ATTRIBUTE);
					if (TYPE_ATTRIBUTE.equals(name))
						return pFirstWord.getTextStreamType();
					return super.getAttribute(name);
				}
				public boolean hasAttribute(String name) {
					if (PAGE_ID_ATTRIBUTE.equals(name))
						return true;
					if (LAST_PAGE_ID_ATTRIBUTE.equals(name))
						return (pFirstWord.pageId != pLastWord.pageId);
					if (PAGE_NUMBER_ATTRIBUTE.equals(name) && pFirstWord.getPage().hasAttribute(PAGE_NUMBER_ATTRIBUTE))
						return pFirstWord.getPage().hasAttribute(PAGE_NUMBER_ATTRIBUTE);
					if (LAST_PAGE_NUMBER_ATTRIBUTE.equals(name) && pLastWord.getPage().hasAttribute(PAGE_NUMBER_ATTRIBUTE))
						return ((pFirstWord.pageId != pLastWord.pageId) && pLastWord.getPage().hasAttribute(PAGE_NUMBER_ATTRIBUTE));
					if (TYPE_ATTRIBUTE.equals(name))
						return true;
					return super.hasAttribute(name);
				}
			};
		}
		Attributed getAttributed() {
			if (this.aData != null)
				return this.aData;
			else if (this.rData != null)
				return this.rData;
			else if (this.tData != null)
				return this.tData;
			else return this.pAttributes;
		}
		ImWord firstWord() {
			if (this.aData != null)
				return this.aData.getFirstWord();
			else if (this.rData != null)
				return this.rDataWords[0];
			else if (this.tData != null)
				return ((ImWord) this.tData.imWords.get(0));
			else return this.pFirstWord;
		}
		ImWord lastWord() {
			if (this.aData != null)
				return this.aData.getLastWord();
			else if (this.rData != null)
				return this.rDataWords[this.rDataWords.length-1];
			else if (this.tData != null)
				return ((ImWord) this.tData.imWords.get(this.tData.imWords.size()-1));
			else return this.pLastWord;
		}
		String getType() {
			if (this.aData != null)
				return this.aData.getType();
			else if (this.rData != null) {
				String type = this.rData.getType();
				if (ImRegion.TABLE_ROW_TYPE.equals(type))
					return TABLE_ROW_ANNOTATION_TYPE;
				else if (ImRegion.TABLE_CELL_TYPE.equals(type))
					return TABLE_CELL_ANNOTATION_TYPE;
				else return type;
			}
			else if (this.tData != null)
				return WORD_ANNOTATION_TYPE;
			else return ImagingConstants.PARAGRAPH_TYPE;
		}
		String setType(String type) {
			String oldType = this.getType();
			if (this.aData != null)
				this.aData.setType(type);
			else if (this.rData != null)
				this.rData.setType(type);
			if (!oldType.equals(type)) {
				this.hashId = null;
				if ((this.aData != null) || (this.rData != null))
					annotationTypeChanged(this, oldType);
			}
			return oldType;
		}
		BoundingBox getBoundingBox() {
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
			return getTokenIndexOf(this.firstWord());
		}
		int getEndIndex() {
			return (getTokenIndexOf(this.lastWord()) + 1);
		}
		int size() {
			return (this.getEndIndex() - this.getStartIndex());
		}
		int getStartOffset() {
			return getTokenFor(this.firstWord()).getStartOffset();
		}
		int getEndOffset() {
			return getTokenFor(this.lastWord()).getEndOffset();
		}
		ImToken tokenAt(int index) {
			return imTokenAtIndex(this.getStartIndex() + index);
		}
		String valueAt(int index) {
			return ImDocumentRoot.this.valueAt(this.getStartIndex() + index);
		}
		String getWhitespaceAfter(int index) {
			return ImDocumentRoot.this.getWhitespaceAfter(this.getStartIndex() + index);
		}
		char charAt(int index) {
			return ImDocumentRoot.this.charAt(this.getStartOffset() + index);
		}
		CharSequence subSequence(int start, int end) {
			return ImDocumentRoot.this.subSequence((this.getStartOffset() + start), (this.getStartOffset() + end));
		}
		String getId() {
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
		private String buildHashId() {
			ImWord fImw = this.firstWord();
			ImWord lImw = this.lastWord();
			String docId = doc.docId;
			if (!docId.matches("[0-9A-Fa-f]++")) {
				docId = (""
						+ asHex(doc.docId.hashCode(), 4)
						+ asHex(doc.docId.hashCode(), 4)
						+ asHex(doc.docId.hashCode(), 4)
						+ asHex(doc.docId.hashCode(), 4)
					);
			}
//			//	THIS IS EXTREMELY SENSITIVE TO WORD CHANGES
//			return hexXor((""
//					// 1 byte of type hash
//					+ asHex(this.getType().hashCode(), 1)
//					// 2 + 1 bytes of page IDs
//					+ asHex(fImw.pageId, 2) + asHex(lImw.pageId, 1)
//					// 2 * 6 bytes of bounding boxes (two bytes left & top, one byte right and bottom)
//					+ asHex(fImw.bounds.left, 2) + asHex(fImw.bounds.right, 1)
//					+ asHex(fImw.bounds.top, 2) + asHex(fImw.bounds.bottom, 1)
//					+ asHex(lImw.bounds.left, 2) + asHex(lImw.bounds.right, 1)
//					+ asHex(lImw.bounds.top, 2) + asHex(lImw.bounds.bottom, 1)
//				), docId);
			//	THIS IS BETTER, AS FIRST TOP LEFT AND LAST BOTTOM RIGHT ARE JUST AS UNIQUE,
			//	BUT ARE INSENSITIVE TO SPLITTING AND MERGING OF WORDS
			return hexXor((""
					// 4 byte of type hash
					+ asHex(this.getType().hashCode(), 4)
					// 2 + 2 bytes of page IDs
					+ asHex(fImw.pageId, 2) + asHex(lImw.pageId, 2)
					// 2 * 4 bytes of bounding boxes (left top of first word, right bottom of last word)
					+ asHex(fImw.bounds.left, 2)
					+ asHex(fImw.bounds.top, 2)
					+ asHex(lImw.bounds.right, 2)
					+ asHex(lImw.bounds.bottom, 2)
				), docId);
			//	THIS DOES NOT ALLOW DUPLICATE ANNOTATIONS !!!
			//	BUT THEN, IS THAT A BAD THING IN EXPORT MODE?
			/* TODO make selection of points dependent on document orientation
			 * - top-left + bottom-right in left-right + top-down orientation (also top-down + left-right, like in Chinese)
			 * - top-right + bottom-left in right-left + top-down orientation (also top-down + right-left, like in Chinese)
			 * - and so forth ...
			 */
		}
		private String hashId = null;
		private String randomId = null;
	}
	
	class ImAnnotationView implements MutableAnnotation {
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
		public String toXML() {
			return (AnnotationUtils.produceStartTag(this, true) + AnnotationUtils.escapeForXml(TokenSequenceUtils.concatTokens(this, true, true)) + AnnotationUtils.produceEndTag(this));
		}
		
		public int getAbsoluteStartIndex() {
			return this.base.getStartIndex();
		}
		public int getAbsoluteStartOffset() {
			return this.base.getStartOffset();
		}
		public QueriableAnnotation[] getAnnotations() {
			return doGetAnnotations(this.base, false);
		}
		public QueriableAnnotation[] getAnnotations(String type) {
			return doGetAnnotations(type, this.base, false);
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
		
		public MutableAnnotation addAnnotation(Annotation annotation) {
			MutableAnnotation annot = this.addAnnotation(annotation.getType(), annotation.getStartIndex(), annotation.size());
			annot.copyAttributes(annotation);
			return annot;
		}
		public MutableAnnotation addAnnotation(String type, int startIndex, int size) {
			return doAddAnnotation(type, startIndex, size, this.base);
		}
		public Annotation removeAnnotation(Annotation annotation) {
			return ImDocumentRoot.this.removeAnnotation(annotation);
		}
		public TokenSequence removeTokens(Annotation annotation) {
			return this.removeTokensAt(annotation.getStartIndex(), annotation.size());
		}
		public MutableAnnotation[] getMutableAnnotations() {
			return ((MutableAnnotation[]) doGetAnnotations(this.base, true));
		}
		public MutableAnnotation[] getMutableAnnotations(String type) {
			return ((MutableAnnotation[]) doGetAnnotations(type, this.base, true));
		}
		public void addAnnotationListener(AnnotationListener al) {/* no use listening on a short-lived wrapper */}
		public void removeAnnotationListener(AnnotationListener al) {/* no use listening on a short-lived wrapper */}
		public boolean hasAttribute(String name) {
			if (PAGE_ID_ATTRIBUTE.equals(name))
				return true;
			else if (LAST_PAGE_ID_ATTRIBUTE.equals(name))
				return true;
			else if (BOUNDING_BOX_ATTRIBUTE.equals(name))
				return (this.base.getBoundingBox() != null);
			else if (PAGE_NUMBER_ATTRIBUTE.equals(name) || this.base.getAttributed().hasAttribute(name) && this.base.firstWord().getPage().hasAttribute(PAGE_NUMBER_ATTRIBUTE))
				return true;
			else if (LAST_PAGE_NUMBER_ATTRIBUTE.equals(name) || this.base.getAttributed().hasAttribute(name) && this.base.lastWord().getPage().hasAttribute(PAGE_NUMBER_ATTRIBUTE))
				return true;
			else if (TableConstants.COL_SPAN_ATTRIBUTE.equals(name) && ((this.base.leftEmptyCellsToSpan + this.base.rightEmptyCellsToSpan) != 0))
				return true;
			else if ((TableConstants.COL_SPAN_ATTRIBUTE + "Left").equals(name) && (this.base.leftEmptyCellsToSpan != 0))
				return true;
			else if ((TableConstants.COL_SPAN_ATTRIBUTE + "Right").equals(name) && (this.base.rightEmptyCellsToSpan != 0))
				return true;
			else return this.base.getAttributed().hasAttribute(name);
		}
		public Object getAttribute(String name) {
			if (PAGE_ID_ATTRIBUTE.equals(name))
				return ("" + this.base.firstWord().getPage().pageId);
			else if (LAST_PAGE_ID_ATTRIBUTE.equals(name))
				return ("" + this.base.lastWord().getPage().pageId);
			else if (BOUNDING_BOX_ATTRIBUTE.equals(name)) {
				BoundingBox bb  = this.base.getBoundingBox();
				return ((bb == null) ? null : bb.toString());
			}
			else if (PAGE_NUMBER_ATTRIBUTE.equals(name) && !this.base.getAttributed().hasAttribute(name) && this.base.firstWord().getPage().hasAttribute(PAGE_NUMBER_ATTRIBUTE))
				return ("" + this.base.firstWord().getPage().getAttribute(PAGE_NUMBER_ATTRIBUTE));
			else if (LAST_PAGE_NUMBER_ATTRIBUTE.equals(name) && !this.base.getAttributed().hasAttribute(name) && this.base.lastWord().getPage().hasAttribute(PAGE_NUMBER_ATTRIBUTE))
				return ("" + this.base.lastWord().getPage().getAttribute(PAGE_NUMBER_ATTRIBUTE));
			else if (TableConstants.COL_SPAN_ATTRIBUTE.equals(name) && ((this.base.leftEmptyCellsToSpan + this.base.rightEmptyCellsToSpan) != 0))
				return ("" + (this.base.leftEmptyCellsToSpan + 1 + this.base.rightEmptyCellsToSpan));
			else if ((TableConstants.COL_SPAN_ATTRIBUTE + "Left").equals(name) && (this.base.leftEmptyCellsToSpan != 0))
				return ("" + this.base.leftEmptyCellsToSpan);
			else if ((TableConstants.COL_SPAN_ATTRIBUTE + "Right").equals(name) && (this.base.rightEmptyCellsToSpan != 0))
				return ("" + this.base.rightEmptyCellsToSpan);
			else return this.base.getAttributed().getAttribute(name);
		}
		public Object getAttribute(String name, Object def) {
			if (PAGE_ID_ATTRIBUTE.equals(name))
				return ("" + this.base.firstWord().getPage().pageId);
			else if (LAST_PAGE_ID_ATTRIBUTE.equals(name))
				return ("" + this.base.lastWord().getPage().pageId);
			else if (BOUNDING_BOX_ATTRIBUTE.equals(name)) {
				BoundingBox bb  = this.base.getBoundingBox();
				return ((bb == null) ? null : bb.toString());
			}
			else if (PAGE_NUMBER_ATTRIBUTE.equals(name) && !this.base.getAttributed().hasAttribute(name) && this.base.firstWord().getPage().hasAttribute(PAGE_NUMBER_ATTRIBUTE))
				return ("" + this.base.firstWord().getPage().getAttribute(PAGE_NUMBER_ATTRIBUTE));
			else if (LAST_PAGE_NUMBER_ATTRIBUTE.equals(name) && !this.base.getAttributed().hasAttribute(name) && this.base.lastWord().getPage().hasAttribute(PAGE_NUMBER_ATTRIBUTE))
				return ("" + this.base.lastWord().getPage().getAttribute(PAGE_NUMBER_ATTRIBUTE));
			else if (TableConstants.COL_SPAN_ATTRIBUTE.equals(name) && ((this.base.leftEmptyCellsToSpan + this.base.rightEmptyCellsToSpan) != 0))
				return ("" + (this.base.leftEmptyCellsToSpan + 1 + this.base.rightEmptyCellsToSpan));
			else if ((TableConstants.COL_SPAN_ATTRIBUTE + "Left").equals(name) && (this.base.leftEmptyCellsToSpan != 0))
				return ("" + this.base.leftEmptyCellsToSpan);
			else if ((TableConstants.COL_SPAN_ATTRIBUTE + "Right").equals(name) && (this.base.rightEmptyCellsToSpan != 0))
				return ("" + this.base.rightEmptyCellsToSpan);
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
			if (this.base.getBoundingBox() != null)
				ans.add(BOUNDING_BOX_ATTRIBUTE);
			if ((this.base.leftEmptyCellsToSpan + this.base.rightEmptyCellsToSpan) != 0)
				ans.add(TableConstants.COL_SPAN_ATTRIBUTE);
			if (this.base.leftEmptyCellsToSpan != 0)
				ans.add(TableConstants.COL_SPAN_ATTRIBUTE + "Left");
			if (this.base.rightEmptyCellsToSpan != 0)
				ans.add(TableConstants.COL_SPAN_ATTRIBUTE + "Right");
			return ((String[]) ans.toArray(new String[ans.size()]));
		}
		public void setAttribute(String name) {
			if ((name != null) && name.startsWith(TableConstants.COL_SPAN_ATTRIBUTE) && ((this.base.leftEmptyCellsToSpan + this.base.rightEmptyCellsToSpan) != 0))
				return;
			this.base.getAttributed().setAttribute(name);
		}
		public Object setAttribute(String name, Object value) {
			if (TableConstants.COL_SPAN_ATTRIBUTE.equals(name) && ((this.base.leftEmptyCellsToSpan + this.base.rightEmptyCellsToSpan) != 0))
				return ("" + (this.base.leftEmptyCellsToSpan + 1 + this.base.rightEmptyCellsToSpan));
			else if ((TableConstants.COL_SPAN_ATTRIBUTE + "Left").equals(name) && (this.base.leftEmptyCellsToSpan != 0))
				return ("" + this.base.leftEmptyCellsToSpan);
			else if ((TableConstants.COL_SPAN_ATTRIBUTE + "Right").equals(name) && (this.base.rightEmptyCellsToSpan != 0))
				return ("" + this.base.rightEmptyCellsToSpan);
			else return this.base.getAttributed().setAttribute(name, value);
		}
		public void copyAttributes(Attributed source) {
			this.base.getAttributed().copyAttributes(source);
		}
		public Object removeAttribute(String name) {
			return this.base.getAttributed().removeAttribute(name);
		}
		public void clearAttributes() {
			this.base.getAttributed().clearAttributes();
		}
		
		public String getDocumentProperty(String propertyName) {
			if (DocumentRoot.DOCUMENT_ID_ATTRIBUTE.equals(propertyName))
				return doc.docId;
			else return doc.getDocumentProperty(propertyName);
		}
		public String getDocumentProperty(String propertyName, String defaultValue) {
			if (DocumentRoot.DOCUMENT_ID_ATTRIBUTE.equals(propertyName))
				return doc.docId;
			else return doc.getDocumentProperty(propertyName, defaultValue);
		}
		public String[] getDocumentPropertyNames() {
			return doc.getDocumentPropertyNames();
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
			return this.getSubsequence(start, size);
		}
		public Tokenizer getTokenizer() {
			return ImDocumentRoot.this.getTokenizer();
		}
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
		
		public int compareTo(Object obj) {
			if (obj instanceof Annotation)
				return AnnotationUtils.compare(this, ((Annotation) obj));
			else return -1;
		}
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
	
	/** constant for specifying whether or not to include tables */
	public static final int USE_RANDOM_ANNOTATION_IDS = 64;
	
	private ImDocument doc;
	
	private ArrayList annotations = new ArrayList();
	private TreeMap annotationsByType = new TreeMap();
	private HashMap annotationBasesByAnnotations = new HashMap();
	private HashMap annotationBasesByRegions = new HashMap();
	private HashMap annotationViewsByBases = new HashMap();
	
	private String annotationNestingOrder;
	
	private boolean paragraphsAreLogical;
	private boolean showTokensAsWordAnnotations = false;
//	private LinkedList regionAnnotationBaseAttic = null;
	
	private boolean useRandomAnnotationIDs = true;
	
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
		ImWord[] textStreamHeads = doc.getTextStreamHeads();
		LinkedList nonDeletedTextStreamHeads = new LinkedList();
		for (int h = 0; h < textStreamHeads.length; h++) {
			if (ImWord.TEXT_STREAM_TYPE_DELETED.equals(textStreamHeads[h].getTextStreamType()))
				continue;
			if (excludeTables && ImWord.TEXT_STREAM_TYPE_TABLE.equals(textStreamHeads[h].getTextStreamType()))
				continue;
			if (excludeCaptionsFootnotes && (ImWord.TEXT_STREAM_TYPE_CAPTION.equals(textStreamHeads[h].getTextStreamType()) || ImWord.TEXT_STREAM_TYPE_FOOTNOTE.equals(textStreamHeads[h].getTextStreamType()) || ImWord.TEXT_STREAM_TYPE_TABLE_NOTE.equals(textStreamHeads[h].getTextStreamType())))
				continue;
			if (((normalizationLevel == NORMALIZATION_LEVEL_PARAGRAPHS) || (normalizationLevel == NORMALIZATION_LEVEL_STREAMS)) && (ImWord.TEXT_STREAM_TYPE_PAGE_TITLE.equals(textStreamHeads[h].getTextStreamType()) || ImWord.TEXT_STREAM_TYPE_ARTIFACT.equals(textStreamHeads[h].getTextStreamType())))
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
		super(((Tokenizer) doc.getAttribute(ImDocument.TOKENIZER_ATTRIBUTE, Gamta.INNER_PUNCTUATION_TOKENIZER)), textStreamHeads, configFlags);
		this.doc = doc;
		
		//	read normalization level
		int normalizationLevel = (configFlags & NORMALIZATION_LEVEL_STREAMS);
		this.paragraphsAreLogical = ((normalizationLevel == NORMALIZATION_LEVEL_PARAGRAPHS) || (normalizationLevel == NORMALIZATION_LEVEL_STREAMS));
		
		//	collect text stream IDs
		HashSet textStreamIDs = new HashSet();
		for (int h = 0; h < textStreamHeads.length; h++)
			textStreamIDs.add(textStreamHeads[h].getTextStreamId());
		
		//	add annotation overlay for annotations
		ImAnnotation[] annotations = this.doc.getAnnotations();
		for (int a = 0; a < annotations.length; a++) {
			if (textStreamIDs.contains(annotations[a].getFirstWord().getTextStreamId()))
				this.addAnnotation(annotations[a]);
		}
		
		//	add annotation overlay for regions, including pages, for low normalization levels
		if ((normalizationLevel == NORMALIZATION_LEVEL_RAW) || (normalizationLevel == NORMALIZATION_LEVEL_WORDS)) {
			ImPage[] pages = this.doc.getPages();
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
			for (int h = 0; h < textStreamHeads.length; h++) {
				ImWord pFirstWord = null;
				for (ImWord imw = textStreamHeads[h]; imw != null; imw = imw.getNextWord()) {
					if (pFirstWord == null)
						pFirstWord = imw;
					if ((imw.getNextRelation() == ImWord.NEXT_RELATION_PARAGRAPH_END) || (imw.getNextWord() == null)) {
						this.addAnnotation(pFirstWord, imw);
						pFirstWord = null;
					}
				}
			}
		}
		
		//	annotate tables (if not filtered out)
		if ((configFlags & EXCLUDE_TABLES) == 0) {
			ImPage[] pages = this.doc.getPages();
			for (int p = 0; p < pages.length; p++) {
				ImRegion[] regions = pages[p].getRegions();
				for (int r = 0; r < regions.length; r++) {
					if (TABLE_ANNOTATION_TYPE.equals(regions[r].getType()))
						this.annotateTableStructure(regions[r]);
				}
			}
		}
		
		//	write through included configuration (annotating words makes sense only now)
		this.setShowTokensAsWordsAnnotations((configFlags & SHOW_TOKENS_AS_WORD_ANNOTATIONS) != 0);
		this.setUseRandomAnnotationIDs((configFlags & USE_RANDOM_ANNOTATION_IDS) != 0);
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
		super(((Tokenizer) firstWord.getDocument().getAttribute(ImDocument.TOKENIZER_ATTRIBUTE, Gamta.INNER_PUNCTUATION_TOKENIZER)), firstWord, lastWord);
		this.doc = firstWord.getDocument();
		
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
		
		//	add annotation overlay for regions, including pages
		if ((normalizationLevel == NORMALIZATION_LEVEL_RAW) || (normalizationLevel == NORMALIZATION_LEVEL_WORDS)) {
			ImPage[] pages = this.doc.getPages();
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
			ImWord pFirstWord = null;
			for (ImWord imw = firstWord; imw != null; imw = imw.getNextWord()) {
				if (pFirstWord == null)
					pFirstWord = imw;
				if ((imw.getNextRelation() == ImWord.NEXT_RELATION_PARAGRAPH_END) || (imw.getNextWord() == null) || (imw == lastWord)) {
					this.addAnnotation(pFirstWord, imw);
					pFirstWord = null;
				}
				if (imw == lastWord)
					break;
			}
		}
		
		//	annotate tables
		if ((configFlags & EXCLUDE_TABLES) == 0) {
			ImPage[] pages = this.doc.getPages();
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
				ImRegion[] regions = pages[p].getRegions();
				for (int r = 0; r < regions.length; r++) {
					if (!TABLE_ANNOTATION_TYPE.equals(regions[r].getType()))
						continue;
					
					//	get table words
					ArrayList filteredRegionWords = new ArrayList();
					for (int w = 0; w < filteredPageWords.size(); w++) {
						if (regions[r].bounds.includes(((ImWord) filteredPageWords.get(w)).bounds, true))
							filteredRegionWords.add(filteredPageWords.get(w));
					}
					
					//	annotate table only if not empty
					if (filteredRegionWords.size() != 0)
						this.annotateTableStructure(regions[r]);
				}
			}
		}
		
		//	write through included configuration (annotating words makes sense only now)
		this.setShowTokensAsWordsAnnotations((configFlags & SHOW_TOKENS_AS_WORD_ANNOTATIONS) != 0);
		this.setUseRandomAnnotationIDs((configFlags & USE_RANDOM_ANNOTATION_IDS) != 0);
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
		super(((Tokenizer) region.getDocument().getAttribute(ImDocument.TOKENIZER_ATTRIBUTE, Gamta.INNER_PUNCTUATION_TOKENIZER)));
		this.doc = region.getDocument();
		
		//	read normalization level
		int normalizationLevel = (configFlags & NORMALIZATION_LEVEL_STREAMS);
		this.paragraphsAreLogical = ((normalizationLevel == NORMALIZATION_LEVEL_PARAGRAPHS) || (normalizationLevel == NORMALIZATION_LEVEL_STREAMS));
		boolean excludeTables = ((configFlags & EXCLUDE_TABLES) != 0);
		boolean excludeCaptionsFootnotes = ((configFlags & EXCLUDE_CAPTIONS_AND_FOOTNOTES) != 0);
		
		//	add words
		ImWord[] regionWords = region.getWords();
		HashSet textStreamIdSet = new LinkedHashSet();
		for (int w = 0; w < regionWords.length; w++) {
			if (ImWord.TEXT_STREAM_TYPE_DELETED.equals(regionWords[w].getTextStreamType()))
				continue;
			if (excludeTables && ImWord.TEXT_STREAM_TYPE_TABLE.equals(regionWords[w].getTextStreamType()))
				continue;
			if (excludeCaptionsFootnotes && (ImWord.TEXT_STREAM_TYPE_CAPTION.equals(regionWords[w].getTextStreamType()) || ImWord.TEXT_STREAM_TYPE_FOOTNOTE.equals(regionWords[w].getTextStreamType()) || ImWord.TEXT_STREAM_TYPE_TABLE_NOTE.equals(regionWords[w].getTextStreamType())))
				continue;
			if ((ImWord.TEXT_STREAM_TYPE_ARTIFACT.equals(regionWords[w].getTextStreamType()) || ImWord.TEXT_STREAM_TYPE_PAGE_TITLE.equals(regionWords[w].getTextStreamType())) && ((normalizationLevel == NORMALIZATION_LEVEL_PARAGRAPHS) || (normalizationLevel == NORMALIZATION_LEVEL_STREAMS)))
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
		this.addTextStreams(textStreamHeads, normalizationLevel, new HashSet(Arrays.asList(regionWords)));
		
		//	add annotation overlay for annotations
		ImAnnotation[] annotations = this.doc.getAnnotations();
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
			ImRegion[] regions = region.getPage().getRegions();
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
						this.addAnnotation(pFirstWord, imw);
						pFirstWord = null;
					}
				}
			}
		}
		
		//	annotate tables
		if (!excludeTables) {
			ImRegion[] regions = region.getPage().getRegions();
			for (int r = 0; r < regions.length; r++) {
				if (!TABLE_ANNOTATION_TYPE.equals(regions[r].getType()))
					continue;
				
				//	we already have this one
				if (regions[r] == region)
					continue;
				
				//	annotate table if it's within range
				if (region.bounds.includes(regions[r].bounds, false))
					this.annotateTableStructure(regions[r]);
			}
		}
		
		//	write through included configuration (annotating words makes sense only now)
		this.setShowTokensAsWordsAnnotations((configFlags & SHOW_TOKENS_AS_WORD_ANNOTATIONS) != 0);
		this.setUseRandomAnnotationIDs((configFlags & USE_RANDOM_ANNOTATION_IDS) != 0);
	}
	
	//	represent a table with annotations
	private void annotateTableStructure(ImRegion table) {
		this.addAnnotation(table);
		ImRegion[] rows = table.getRegions(ImRegion.TABLE_ROW_TYPE);
		Arrays.sort(rows, ImUtils.topDownOrder);
		for (int r = 0; r < rows.length; r++) {
			ImWord[] rowWords = rows[r].getWords();
			if (rowWords.length != 0) {
				Arrays.sort(rowWords, ImUtils.textStreamOrder);
				this.addAnnotation(rows[r], rowWords);
			}
			ImRegion[] rowCells = rows[r].getRegions(ImRegion.TABLE_CELL_TYPE);
			Arrays.sort(rowCells, ImUtils.leftRightOrder);
			int emptyLeftCellsToSpan = 0;
			ImAnnotationBase leftCellImab = null;
			for (int c = 0; c < rowCells.length; c++) {
				ImWord[] cellWords = rowCells[c].getWords();
				if (cellWords.length != 0) {
					Arrays.sort(cellWords, ImUtils.textStreamOrder);
					ImAnnotationBase cellImab = this.addAnnotation(rowCells[c], cellWords);
					if (cellImab == null) {
						if (leftCellImab != null)
							leftCellImab.rightEmptyCellsToSpan++;
						else emptyLeftCellsToSpan++;
					}
					else {
						leftCellImab = cellImab;
						if (emptyLeftCellsToSpan != 0) {
							leftCellImab.leftEmptyCellsToSpan += emptyLeftCellsToSpan;
							emptyLeftCellsToSpan = 0;
						}
					}
				}
				else if (leftCellImab != null)
					leftCellImab.rightEmptyCellsToSpan++;
				else emptyLeftCellsToSpan++;
			}
		}
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
			ArrayList wordAnnotations = new ArrayList();
			int annotationIndex = 0;
			for (int t = 0; t < this.size(); t++) {
				ImToken imt = this.imTokenAtIndex(t);
				while ((annotationIndex < this.annotations.size()) && (((ImAnnotationBase) this.annotations.get(annotationIndex)).getStartIndex() <= imt.index))
					annotationIndex++;
				ImAnnotationBase imtAb = new ImAnnotationBase(imt);
				this.annotations.add(annotationIndex, imtAb);
				wordAnnotations.add(imtAb);
			}
			this.annotationsByType.put(WORD_ANNOTATION_TYPE, wordAnnotations);
		}
		
		//	remove words from annotation infrastructure
		else {
			for (int a = (this.annotations.size()-1); -1 < a; a--) {
				if (((ImAnnotationBase) this.annotations.get(a)).tData != null)
					this.annotations.remove(a);
			}
			this.annotationsByType.remove(WORD_ANNOTATION_TYPE);
		}
	}
	
	/**
	 * Test whether or not this wrapper is using random annotation IDs.
	 * @return true if random annotation IDs are in use
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
	 */
	public void setUseRandomAnnotationIDs(boolean uraids) {
		//	nothing to recompute here, logic sits in annotation bases
		this.useRandomAnnotationIDs = uraids;
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
		if (regionWords.length == 0)
			return null;
		ArrayList docRegionWords = new ArrayList();
		for (int w = 0; w < regionWords.length; w++) {
			if (this.getTokenIndexOf(regionWords[w]) >= 0)
				docRegionWords.add(regionWords[w]);
		}
		if (docRegionWords.isEmpty())
			return null;
		if (docRegionWords.size() < regionWords.length)
			regionWords = ((ImWord[]) docRegionWords.toArray(new ImWord[docRegionWords.size()]));
		ImAnnotationBase imab = this.getAnnotationBase(region, regionWords);
		this.indexAnnotationBase(imab);
		return imab;
	}
	private void indexAnnotationBase(ImAnnotationBase base) {
		indexAnnotationBase(this.annotations, base, this.typedAnnotationBaseOrder);
		ArrayList typeAnnots = ((ArrayList) this.annotationsByType.get(base.getType()));
		if (typeAnnots == null) {
			typeAnnots = new ArrayList();
			this.annotationsByType.put(base.getType(), typeAnnots);
		}
		indexAnnotationBase(typeAnnots, base, annotationBaseOrder);
	}
	private void annotationTypeChanged(ImAnnotationBase imab, String oldType) {
		this.unIndexAnnotationBase(imab, oldType);
		this.indexAnnotationBase(imab);
	}
	private static void indexAnnotationBase(ArrayList annotList, ImAnnotationBase base, Comparator annotBaseOrder) {
		if (annotList.isEmpty()) {
			annotList.add(base);
			return;
		}
		int index = (annotList.size() / 2);
		for (int step = (annotList.size() / 4); 1 < step; step /= 2) {
			int c = annotBaseOrder.compare(base, annotList.get(index));
			if (c == 0)
				break;
			else if (c < 0)
				index -= step;
			else index += step;
		}
		while ((index != 0) && (annotBaseOrder.compare(base, annotList.get(index)) < 0))
			index--;
		while ((index < annotList.size()) && (0 < annotBaseOrder.compare(base, annotList.get(index))))
			index++;
		int cIndex = index;
		while ((cIndex != 0) && ((annotList.size() <= cIndex) || (annotationBaseOrder.compare(base, annotList.get(cIndex)) <= 0)))
			cIndex--;
		while ((cIndex < annotList.size()) && ((cIndex < 0) || (0 <= annotationBaseOrder.compare(base, annotList.get(cIndex))))) {
			if ((-1 < cIndex) && (base == annotList.get(cIndex)))
				return;
			cIndex++;
		}
		annotList.add(index, base);
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
				ti2 = -annotationNestingOrder.length();
			return (ti1 - ti2);
		}
	};
	private static final Comparator annotationBaseOrder = new Comparator() {
		public int compare(Object obj1, Object obj2) {
			ImAnnotationBase iab1 = ((ImAnnotationBase) obj1);
			ImAnnotationBase iab2 = ((ImAnnotationBase) obj2);
			
			//	compare start and end index
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
		return 0;
	}
	public int getEndOffset() {
		return this.length();
	}
	
	public int getStartIndex() {
		return 0;
	}
	public int getEndIndex() {
		return this.size();
	}
	public String getType() {
		return DocumentRoot.DOCUMENT_TYPE;
	}
	public String changeTypeTo(String newType) {
		return DocumentRoot.DOCUMENT_TYPE;
	}
	public String getAnnotationID() {
		return this.doc.docId;
	}
	public String getValue() {
		return TokenSequenceUtils.concatTokens(this, false, false);
	}
	public String toXML() {
		return (AnnotationUtils.produceStartTag(this, true) + TokenSequenceUtils.concatTokens(this, true, true) + AnnotationUtils.produceEndTag(this));
	}
	
	public int getAbsoluteStartIndex() {
		return 0;
	}
	public int getAbsoluteStartOffset() {
		return 0;
	}
	public QueriableAnnotation[] getAnnotations() {
		return this.doGetAnnotations(null, false);
	}
	public QueriableAnnotation[] getAnnotations(String type) {
		return this.doGetAnnotations(type, null, false);
	}
	QueriableAnnotation[] doGetAnnotations(ImAnnotationBase source, boolean mutableAnnotationArray) {
		return this.doGetAnnotations(this.annotations, source, annotationBaseOrder, mutableAnnotationArray);
	}
	QueriableAnnotation[] doGetAnnotations(String type, ImAnnotationBase source, boolean mutableAnnotationArray) {
		if (type == null)
			return this.doGetAnnotations(source, mutableAnnotationArray);
		else return this.doGetAnnotations(((ArrayList) this.annotationsByType.get(type)), source, annotationBaseOrder, mutableAnnotationArray);
	}
	private QueriableAnnotation[] doGetAnnotations(ArrayList annotationList, ImAnnotationBase source, Comparator annotationBaseOrder, boolean mutableAnnotationArray) {
		if ((annotationList == null) || annotationList.isEmpty())
			return (mutableAnnotationArray ? new MutableAnnotation[0] : new QueriableAnnotation[0]);
		else {
			ArrayList sourceAnnotationList;
			if (source == null)
				sourceAnnotationList = annotationList;
			else {
				sourceAnnotationList = new ArrayList();
				int start = (annotationList.size() / 2);
				for (int step = (annotationList.size() / 4); 1 < step; step /= 2) {
					int c = annotationBaseOrder.compare(source, annotationList.get(start));
					if (c == 0)
						break;
					else if (c < 0)
						start -= step;
					else start += step;
				}
				while ((start != 0) && (annotationBaseOrder.compare(source, annotationList.get(start)) < 0))
					start--;
				while ((start < annotationList.size()) && (0 < annotationBaseOrder.compare(source, annotationList.get(start))))
					start++;
				for (int a = 0; a < annotationList.size(); a++) {
					ImAnnotationBase base = ((ImAnnotationBase) annotationList.get(a));
					if ((source.getStartIndex() <= base.getStartIndex()) && (base.getEndIndex() <= source.getEndIndex()))
						sourceAnnotationList.add(base);
				}
			}
			QueriableAnnotation[] annotations = (mutableAnnotationArray ? new MutableAnnotation[sourceAnnotationList.size()] : new QueriableAnnotation[sourceAnnotationList.size()]);
			for (int a = 0; a < sourceAnnotationList.size(); a++)
				annotations[a] = this.getAnnotationView(((ImAnnotationBase) sourceAnnotationList.get(a)), source);
			return annotations;
		}
	}
	public String[] getAnnotationTypes() {
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
		return this.doAddAnnotation(type, startIndex, size, null);
	}
	private MutableAnnotation doAddAnnotation(String type, int startIndex, int size, ImAnnotationBase sourceBase) {
		if (size < 1)
			return null;
		
		//	get first and last tokens
		ImToken firstToken = this.imTokenAtIndex(startIndex + ((sourceBase == null) ? 0 : sourceBase.getStartIndex()));
		ImToken lastToken = this.imTokenAtIndex(startIndex + ((sourceBase == null) ? 0 : sourceBase.getStartIndex()) + size - 1);
		
		//	get first and last words
		ImWord firstWord = ((ImWord) firstToken.imWords.get(0));
		ImWord lastWord = ((ImWord) lastToken.imWords.get(lastToken.imWords.size()-1));
		
		//	adding paragraph, and normalization level is paragraphs or streams
		if (this.paragraphsAreLogical && ImagingConstants.PARAGRAPH_TYPE.equals(type) && firstWord.getTextStreamId().equals(lastWord.getTextStreamId())) {
			
			//	adjust underlying text stream ...
			for (ImWord imw = firstWord; imw != null; imw = imw.getNextWord()) {
				
				//	set next relation of last word to paragraph break
				if (imw == lastWord) {
					imw.setNextRelation(ImWord.NEXT_RELATION_PARAGRAPH_END);
					break;
				}
				
				//	we only need to handle internal paragraph breaks
				if ((imw.getNextRelation() != ImWord.NEXT_RELATION_PARAGRAPH_END) || (imw.getNextWord() == null))
					continue;
				
				//	check for hyphenation
				boolean imwHyphenated = ((imw.getString() != null) && imw.getString().matches(".+[\\-\\u00AD\\u2010-\\u2015\\u2212]"));
				boolean nImwContinues = false;
				if (imwHyphenated) {
					String nextWordString = imw.getNextWord().getString();
					if (nextWordString == null) {} // little we can do here ...
					else if (nextWordString.length() == 0) {} // ... or here
					else if (nextWordString.charAt(0) == Character.toUpperCase(nextWordString.charAt(0))) {} // starting with capital letter, not a word continued
					else if ("and;or;und;oder;et;ou;y;e;o;u;ed".indexOf(nextWordString.toLowerCase()) != -1) {} // rather looks like an enumeration continued than a word (western European languages for now)
					else nImwContinues = true;
				}
				
				//	we do have a hyphenated word
				if (imwHyphenated && nImwContinues)
					imw.setNextRelation(ImWord.NEXT_RELATION_HYPHENATED);
				
				//	we have two separate words
				else imw.setNextRelation(ImWord.NEXT_RELATION_SEPARATE);
			}
			
			//	... and return emulated paragraph
			ImAnnotationBase imab = this.addAnnotation(firstWord, lastWord);
			return this.getAnnotationView(imab, sourceBase);
		}
		
		//	non-paragraph annotation, or raw or word normalization
		else {
			
			//	add annotation to backing document (might go wrong if words belong to different logical text streams)
			ImAnnotation ima = this.doc.addAnnotation(firstWord, lastWord, type);
			if (ima == null)
				return null;
			
			//	add annotation to wrapper registers
			ImAnnotationBase imab = this.getAnnotationBase(ima);
			this.indexAnnotationBase(imab);
			
			//	finally
			return this.getAnnotationView(imab, sourceBase);
		}
	}
	private ImAnnotationBase getAnnotationBase(ImAnnotation ima) {
		ImAnnotationBase imab = ((ImAnnotationBase) this.annotationBasesByAnnotations.get(ima));
		if (imab == null) {
			imab = new ImAnnotationBase(ima);
			this.annotationBasesByAnnotations.put(ima, imab);
		}
		return imab;
	}
	private ImAnnotationBase getAnnotationBase(ImRegion imr, ImWord[] imrWords) {
		String imrKey = this.getRegionKey(imr, null);
		ImAnnotationBase imab = ((ImAnnotationBase) this.annotationBasesByAnnotations.get(imrKey));
		if (imab == null) {
			imab = new ImAnnotationBase(imr, imrWords);
			this.annotationBasesByRegions.put(imrKey, imab);
		}
		return imab;
	}
	private String getRegionKey(ImRegion imr, String oldType) {
		return (((oldType == null) ? imr.getType() : oldType) + "@" + imr.pageId + "." + imr.bounds);
	}
	private ImAnnotationView getAnnotationView(ImAnnotationBase base, ImAnnotationBase sourceBase) {
		HashMap sourceBasedViews = ((HashMap) this.annotationViewsByBases.get(sourceBase));
		if (sourceBasedViews == null) {
			sourceBasedViews = new HashMap();
			this.annotationViewsByBases.put(sourceBase, sourceBasedViews);
		}
		ImAnnotationView iav = ((ImAnnotationView) sourceBasedViews.get(base));
		if (iav == null) {
			iav = new ImAnnotationView(base, sourceBase);
			sourceBasedViews.put(base, iav);
		}
		return iav;
	}
	public Annotation removeAnnotation(Annotation annotation) {
		
		//	this is none of ours
		if (!(annotation instanceof ImAnnotationView))
			return annotation;
		
		//	get annotation base
		ImAnnotationBase base = ((ImAnnotationView) annotation).base;
		
		//	remove base from indexes
		this.dropAnnotationView((ImAnnotationView) annotation);
		this.dropAnnotationBase(base);
		this.unIndexAnnotationBase(base, null);
		
		//	remove annotation or region from wrapped document
		if (base.aData != null)
			this.doc.removeAnnotation(base.aData);
		else if (base.rData != null)
			base.rData.getPage().removeRegion(base.rData);
		//	nothing to do for token/word annotations
		
		//	no need to create some dedicated copy in a short-lived wrapper
		return annotation;
	}
	private void dropAnnotationBase(ImAnnotationBase imab) {
		if (imab.aData != null)
			this.annotationBasesByAnnotations.remove(imab.aData);
		else if (imab.rData != null)
			this.annotationBasesByRegions.remove(this.getRegionKey(imab.rData, null));
		this.annotationViewsByBases.remove(imab);
	}
	private void dropAnnotationView(ImAnnotationView iav) {
		HashMap sourceBasedViews = ((HashMap) this.annotationViewsByBases.get(iav.sourceBase));
		if (sourceBasedViews != null)
			sourceBasedViews.remove(iav.base);
	}
	private void unIndexAnnotationBase(ImAnnotationBase base, String oldType) {
		unIndexAnnotationBase(this.annotations, base, this.typedAnnotationBaseOrder);
		ArrayList typeAnnots = ((ArrayList) this.annotationsByType.get((oldType == null) ? base.getType() : oldType));
		unIndexAnnotationBase(typeAnnots, base, annotationBaseOrder);
		if (typeAnnots.isEmpty())
			this.annotationsByType.remove((oldType == null) ? base.getType() : oldType);
	}
	private static void unIndexAnnotationBase(ArrayList annotationList, ImAnnotationBase base, Comparator annotationBaseOrder) {
		if ((annotationList == null) || annotationList.isEmpty())
			return;
		int index = (annotationList.size() / 2);
		for (int step = (annotationList.size() / 4); 1 < step; step /= 2) {
			int c = annotationBaseOrder.compare(base, annotationList.get(index));
			if (c == 0)
				break;
			else if (c < 0)
				index -= step;
			else index += step;
		}
		while ((index != 0) && (annotationBaseOrder.compare(base, annotationList.get(index)) <= 0))
			index--;
		while ((index < annotationList.size()) && (0 < annotationBaseOrder.compare(base, annotationList.get(index))))
			index++;
		while ((index < annotationList.size()) && (0 <= annotationBaseOrder.compare(base, annotationList.get(index)))) {
			if (annotationList.get(index) == base) {
				annotationList.remove(index);
				return;
			}
			else index++;
		}
	}
	public TokenSequence removeTokens(Annotation annotation) {
		return this.removeTokensAt(annotation.getStartIndex(), annotation.size());
	}
	void replacingTokensAt(int index, ImToken[] replaced, ImToken[] replacement) {
		
		//	index replacement words (main index not yet updated here)
		HashMap rImWordTokens = new HashMap();
		for (int r = 0; r < replacement.length; r++) {
			for (int w = 0; w < replacement[r].imWords.size(); w++)
				rImWordTokens.put(((ImWord) replacement[r].imWords.get(w)).getLocalID(), replacement[r]);
		}
		
		//	inspect annotation bases, collecting the ones to update (we cannot update right away, as this blows sort order and thus hampers removal)
		HashMap annotationsToUpdate = new HashMap();
		for (int a = 0; a < this.annotations.size(); a++) {
			ImAnnotationBase base = ((ImAnnotationBase) this.annotations.get(a));
			
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
					unIndexAnnotationBase(((ArrayList) this.annotationsByType.get(base.getType())), base, annotationBaseOrder);
					this.annotations.remove(a--);
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
					unIndexAnnotationBase(((ArrayList) this.annotationsByType.get(base.getType())), base, annotationBaseOrder);
					this.annotations.remove(a--);
				}
				
				//	either one of first and last word has been cut, remove annotation from wrapped document
				if (((index <= firstToken.index) && !rImWordTokens.containsKey(base.aData.getFirstWord().getLocalID())) != ((lastToken.index < (index + replaced.length)) && !rImWordTokens.containsKey(base.aData.getLastWord().getLocalID())))
					this.doc.removeAnnotation(base.aData);
			}
			
			//	word / token annotation
			else if (base.tData != null) {
				
				//	this one's in range, remove it
				if ((index <= base.tData.index) && (base.tData.index < (index + replaced.length))) {
					unIndexAnnotationBase(((ArrayList) this.annotationsByType.get(base.getType())), base, annotationBaseOrder);
					this.annotations.remove(a--);
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
	}
	void replacedTokensAt(int index, ImToken[] replaced, ImToken[] replacement) {
		if (this.showTokensAsWordAnnotations) {
			for (int r = 0; r < replacement.length; r++)
				this.indexAnnotationBase(new ImAnnotationBase(replacement[r]));
		}
	}
	public MutableAnnotation[] getMutableAnnotations() {
		return ((MutableAnnotation[]) this.doGetAnnotations(null, true));
	}
	public MutableAnnotation[] getMutableAnnotations(String type) {
		return ((MutableAnnotation[]) this.doGetAnnotations(type, null, true));
	}
	public void addAnnotationListener(AnnotationListener al) {/* no use listening on a short-lived wrapper */}
	public void removeAnnotationListener(AnnotationListener al) {/* no use listening on a short-lived wrapper */}

	public boolean hasAttribute(String name) {
		if (DocumentRoot.DOCUMENT_ID_ATTRIBUTE.equals(name))
			return true;
		else return this.doc.hasAttribute(name);
	}
	public Object getAttribute(String name) {
		if (DocumentRoot.DOCUMENT_ID_ATTRIBUTE.equals(name))
			return this.doc.docId;
		else return this.doc.getAttribute(name);
	}
	public Object getAttribute(String name, Object def) {
		if (DocumentRoot.DOCUMENT_ID_ATTRIBUTE.equals(name))
			return this.doc.docId;
		else return this.doc.getAttribute(name, def);
	}
	public String[] getAttributeNames() {
		TreeSet ans = new TreeSet(Arrays.asList(this.doc.getAttributeNames()));
		ans.add(DocumentRoot.DOCUMENT_ID_ATTRIBUTE);
		return ((String[]) ans.toArray(new String[ans.size()]));
	}
	public void setAttribute(String name) {
		this.doc.setAttribute(name);
	}
	public Object setAttribute(String name, Object value) {
		return this.doc.setAttribute(name, value);
	}
	public void copyAttributes(Attributed source) {
		this.doc.copyAttributes(source);
	}
	public Object removeAttribute(String name) {
		return this.doc.removeAttribute(name);
	}
	public void clearAttributes() {
		this.doc.clearAttributes();
	}
	
	public String getDocumentProperty(String propertyName) {
		if (DocumentRoot.DOCUMENT_ID_ATTRIBUTE.equals(propertyName))
			return this.doc.docId;
		else return this.doc.getDocumentProperty(propertyName);
	}
	public String getDocumentProperty(String propertyName, String defaultValue) {
		if (DocumentRoot.DOCUMENT_ID_ATTRIBUTE.equals(propertyName))
			return this.doc.docId;
		else return this.doc.getDocumentProperty(propertyName, defaultValue);
	}
	public String[] getDocumentPropertyNames() {
		return this.doc.getDocumentPropertyNames();
	}
	public String setDocumentProperty(String propertyName, String value) {
		return this.doc.setDocumentProperty(propertyName, value);
	}
	public String removeDocumentProperty(String propertyName) {
		return this.doc.removeDocumentProperty(propertyName);
	}
	public void clearDocumentProperties() {
		this.doc.clearDocumentProperties();
	}
	
	public String setAnnotationNestingOrder(String ano) {
		String oldAno = this.annotationNestingOrder;
		this.annotationNestingOrder = ano;
		if ((this.annotationNestingOrder != null) && !this.annotationNestingOrder.equals(oldAno))
			Collections.sort(this.annotations, this.typedAnnotationBaseOrder);
		return oldAno;
	}
	
	public int compareTo(Object obj) {
		if (obj instanceof Annotation)
			return AnnotationUtils.compare(this, ((Annotation) obj));
		else return -1;
	}
	
	private static String asHex(int i, int bytes) {
		StringBuffer hex = new StringBuffer();
		while (bytes != 0) {
			hex.insert(0, hexDigits.charAt(i & 15));
			i >>>= 4;
			hex.insert(0, hexDigits.charAt(i & 15));
			i >>>= 4;
			bytes--;
		}
		return hex.toString();
	}
	private static String hexXor(String str1, String str2) {
		str1 = str1.toUpperCase();
		str2 = str2.toUpperCase();
		StringBuffer xor = new StringBuffer();
		for (int c = 0; c < Math.max(str1.length(), str2.length()); c++) {
			int i1 = ((c < str1.length()) ? hexDigits.indexOf(str1.charAt(c)) : 0);
			int i2 = ((c < str2.length()) ? hexDigits.indexOf(str2.charAt(c)) : 0);
			xor.append(hexDigits.charAt(i1 ^ i2));
		}
		return xor.toString();
	}
	private static final String hexDigits = "0123456789ABCDEF";
	
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
	
	//	!!! TEST ONLY !!!
	public static void main(String[] args) throws Exception {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e) {}
		
		final File imfDataPath = new File("E:/Testdaten/PdfExtract/");
		
		String docName;
		
		//	only use documents we have an IMF version of right now
//		docName = "19970730111_ftp.pdf.imf"; // scanned, with born-digital looking text embedded, comes out better when loaded from image
		docName = "MEZ_909-919.pdf.imf"; // born-digital, renders perfectly fine
		
		//	load document
		InputStream docIn = new BufferedInputStream(new FileInputStream(new File(imfDataPath, docName)));
		ImDocument doc = ImDocumentIO.loadDocument(docIn);
		docIn.close();
		ImPage[] pages = doc.getPages();
		System.out.println("Got document with " + pages.length + " pages");
		for (int p = 0; p < pages.length; p++) {
			System.out.println(" - page " + p + ":");
			System.out.println("   - got " + pages[p].getWords().length + " words");
			System.out.println("   - got " + pages[p].getRegions().length + " regions");
			PageImage pi = pages[p].getPageImage();
			System.out.println("   - got page image sized " + pi.image.getWidth() + "x" + pi.image.getHeight());
			
		}
		
		//	set page to work with
		aimAtPage = -1;
		
		//	mark paragraph ends
		for (int pg = 0; pg < pages.length; pg++) {
			if ((aimAtPage != -1) && (pg != aimAtPage))
				continue;
			ImRegion[] paragraphs = pages[pg].getRegions(MutableAnnotation.PARAGRAPH_TYPE);
			for (int p = 0; p < paragraphs.length; p++) {
				ImWord[] pWords = paragraphs[p].getWords();
				pWords[pWords.length - 1].setNextRelation(ImWord.NEXT_RELATION_PARAGRAPH_END);
			}
		}
		System.out.println("Paragraph ends marked");
		
		//	wrap document
		ImDocumentRoot idr = new ImDocumentRoot(doc, NORMALIZATION_LEVEL_WORDS);
		
		//	remove page numbers
		for (int t = 0; t < idr.size(); t++) {
			if (idr.valueAt(t).matches("9[0-9]+"))
				idr.removeTokensAt(t--, 1);
			else if (idr.valueAt(t).matches("[\\_]+"))
				idr.removeTokensAt(t--, 1);
		}
		
		//	alter a few tokens
		for (int t = 0; t < idr.size(); t++) {
			String value = idr.valueAt(t);
			System.out.println(t + ": " + value);
			value = value.replaceAll("[aou]", "");
			value = value.replaceAll("[i]", "ii");
			value = value.replaceAll("[e]", "ee");
			if (value.length() == 0)
				value = "X";
			idr.setValueAt((value.substring(0, 1).toUpperCase() + value.substring(1).toLowerCase()), t);
		}
		
		//	write XML for a first test
		AnnotationUtils.writeXML(idr, new OutputStreamWriter(System.out));
	}
	private static int aimAtPage = -1;
}