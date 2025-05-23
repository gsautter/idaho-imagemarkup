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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.UIManager;

import de.uka.ipd.idaho.gamta.Attributed;
import de.uka.ipd.idaho.gamta.CharSequenceListener;
import de.uka.ipd.idaho.gamta.Gamta;
import de.uka.ipd.idaho.gamta.MutableAnnotation;
import de.uka.ipd.idaho.gamta.MutableCharSequence;
import de.uka.ipd.idaho.gamta.MutableTokenSequence;
import de.uka.ipd.idaho.gamta.Token;
import de.uka.ipd.idaho.gamta.TokenSequence;
import de.uka.ipd.idaho.gamta.TokenSequenceListener;
import de.uka.ipd.idaho.gamta.Tokenizer;
import de.uka.ipd.idaho.gamta.defaultImplementation.StringBufferCharSequence;
import de.uka.ipd.idaho.gamta.util.CountingSet;
import de.uka.ipd.idaho.gamta.util.imaging.BoundingBox;
import de.uka.ipd.idaho.gamta.util.imaging.ImagingConstants;
import de.uka.ipd.idaho.gamta.util.imaging.PageImage;
import de.uka.ipd.idaho.im.ImAnnotation;
import de.uka.ipd.idaho.im.ImDocument;
import de.uka.ipd.idaho.im.ImDocument.ImDocumentListener;
import de.uka.ipd.idaho.im.ImDocument.WeakImDocumentListener;
import de.uka.ipd.idaho.im.ImFont;
import de.uka.ipd.idaho.im.ImObject;
import de.uka.ipd.idaho.im.ImPage;
import de.uka.ipd.idaho.im.ImRegion;
import de.uka.ipd.idaho.im.ImSupplement;
import de.uka.ipd.idaho.im.ImWord;
import de.uka.ipd.idaho.im.util.ImDocumentIO;
import de.uka.ipd.idaho.im.util.ImUtils;
import de.uka.ipd.idaho.stringUtils.StringUtils;

/**
 * Implementation of a GAMTA mutable token sequence wrapped around an Image
 * Markup document. This class supports deletion of tokens, as well as changes
 * to token values, but not insertion or addition of tokens. The latter is
 * because tokens are anchored in to Image Markup words, which can only be
 * inserted into an Image Markup document or a page thereof with an appropriate
 * bounding box.<br>
 * If an Image Markup document contains multiple logical text streams, the
 * normalization level handed to the constructor of this class determines how
 * these logical text streams are merged into one another to for the token
 * sequence. If a logical text stream is typed as <code>deleted</code>, the
 * constructor of this class that do not take external text stream heads as an
 * argument will ignore this logical text stream.
 * 
 * @author sautter
 */
public class ImTokenSequence implements MutableTokenSequence, ImagingConstants {
	
	/** normalization level at which words are ordered strictly by layout order, but text streams classified as <code>deleted</code> are filtered out */
	public static final int NORMALIZATION_LEVEL_RAW = 0;
	
	/** normalization level at which words are ordered by layout order, but hyphenated and continued words are kept together, and text streams classified as <code>deleted</code> are filtered out */
	public static final int NORMALIZATION_LEVEL_WORDS = 1;
	
	/** normalization level at which words are ordered by reading order, but paragraphs are kept together, and text streams classified as <code>deleted</code>, <code>pageTitle</code>, or <code>artifact</code> are filtered out */
	public static final int NORMALIZATION_LEVEL_PARAGRAPHS = 2;
	
	/** normalization level at which text whole streams are kept together, and text streams classified as <code>deleted</code>, <code>pageTitle</code>, or <code>artifact</code> are filtered out */
	public static final int NORMALIZATION_LEVEL_STREAMS = 3;
	
	/** constant for specifying whether or not diacrit characters are to be normalized to their base forms */
	public static final int NORMALIZE_CHARACTERS = 4;
	
	static final BoundingBox NULL_BOUNDING_BOX = new BoundingBox(0, 0, 0, 0);
	
	class ImToken implements Token {
		int startOffset;
		int index;
		String value = null;
		String whitespace = "";
		ArrayList imWords = new ArrayList(1);
		ArrayList imWordAt;
		BoundingBox boundingBox = null;
		ImToken(int startOffset, int index, ImWord imw, String imwStr) {
			this.startOffset = startOffset;
			this.index = index;
			this.addWord(imw, imwStr);
		}
		private ImToken(int startOffset, int index) {
			this.startOffset = startOffset;
			this.index = index;
		}
		void addWord(ImWord imw, String imwStr) {
//			String imwStr = imw.getString();
			if ((imwStr == null) || (imwStr.length() == 0))
				return;
//			if (normalizeChars)
//				imwStr = normalizeString(imwStr);
			if (this.value == null) {
				this.value = imwStr;
				this.imWordAt = new ArrayList(imwStr.length());
			}
			else {
				ImWord pImw = ((ImWord) this.imWords.get(this.imWords.size()-1));
				if (pImw.getNextRelation() == ImWord.NEXT_RELATION_HYPHENATED) {
					this.value = this.value.substring(0, (this.value.length() - "-".length()));
					this.imWordAt.remove(this.value.length());
				}
				this.value += imwStr;
			}
			this.imWords.add(imw);
			for (int c = 0; c < imwStr.length(); c++)
				this.imWordAt.add(imw);
		}
		BoundingBox getBoundingBox() {
			if (this.boundingBox == NULL_BOUNDING_BOX)
				return null;
			if (this.boundingBox == null) {
				if (this.imWords.size() == 1)
					this.boundingBox = ((ImWord) this.imWords.get(0)).bounds;
				else {
					BoundingBox[] imWordBounds = new BoundingBox[this.imWords.size()];
					for (int w = 0; w < this.imWords.size(); w++) {
						imWordBounds[w] = ((ImWord) this.imWords.get(w)).bounds;
						if ((w != 0) && (imWordBounds[w].left < imWordBounds[w-1].left)) {
							this.boundingBox = NULL_BOUNDING_BOX;
							break;
						}
					}
					if (this.boundingBox == NULL_BOUNDING_BOX)
						return null;
					this.boundingBox = BoundingBox.aggregate(imWordBounds);
					if (this.boundingBox == null)
						this.boundingBox = NULL_BOUNDING_BOX;
				}
			}
			return this.boundingBox;
		}
		
		public int getStartOffset() {
			checkValid();
			return this.startOffset;
		}
		int imtEndOffset() {
			return (this.startOffset + this.imtLength());
		}
		public int getEndOffset() {
			checkValid();
			return (this.startOffset + this.length());
		}
		int imtLength() {
			return (this.value.length() + this.whitespace.length());
		}
		
		ImWord wordAt(int offset) {
			if ((offset >= 0) && (offset < this.imWordAt.size()))
				return ((ImWord) this.imWordAt.get(offset));
			else return null;
		}
		
		public int length() {
			checkValid();
			return this.value.length();
		}
		public char charAt(int index) {
			checkValid();
			return ((index < this.value.length()) ? this.value.charAt(index) : this.whitespace.charAt(index - this.value.length()));
		}
		public CharSequence subSequence(int start, int end) {
			checkValid();
			if ((start < this.value.length()) && (end <= this.value.length()))
				return this.value.substring(start, end);
			else if (start < this.value.length())
				return (this.value.substring(start) + this.whitespace.substring(0, (end - this.value.length())));
			else return this.whitespace.substring((start - this.value.length()), (end - this.value.length()));
		}
		
		public String getValue() {
			checkValid();
			return this.value;
		}
		public Tokenizer getTokenizer() {
			checkValid();
			return tokenizer;
		}
		public boolean hasAttribute(String name) {
			checkValid();
			if (Token.PARAGRAPH_END_ATTRIBUTE.equals(name) && this.isParagraphEnd())
				return true;
			else if (PAGE_ID_ATTRIBUTE.equals(name))
				return true;
			else if (LAST_PAGE_ID_ATTRIBUTE.equals(name))
				return true;
			else if (BOUNDING_BOX_ATTRIBUTE.equals(name))
				return (this.getBoundingBox() != null);
			else if (PAGE_NUMBER_ATTRIBUTE.equals(name))
				return (((ImWord) this.imWords.get(0)).getPage().hasAttribute(PAGE_NUMBER_ATTRIBUTE));
			else if (LAST_PAGE_NUMBER_ATTRIBUTE.equals(name))
				return (((ImWord) this.imWords.get(this.imWords.size()-1)).getPage().hasAttribute(PAGE_NUMBER_ATTRIBUTE));
			else if (ImWord.TEXT_STREAM_TYPE_ATTRIBUTE.equals(name))
				return true;
			else if (ImWord.PREVIOUS_RELATION_ATTRIBUTE.equals(name))
				return true;
			else if (ImWord.NEXT_RELATION_ATTRIBUTE.equals(name))
				return true;
			else if (ImWord.BOLD_ATTRIBUTE.equals(name))
				return ((ImWord) this.imWords.get(0)).hasAttribute(ImWord.BOLD_ATTRIBUTE);
			else if (ImWord.ITALICS_ATTRIBUTE.equals(name))
				return ((ImWord) this.imWords.get(0)).hasAttribute(ImWord.ITALICS_ATTRIBUTE);
			else if (ImWord.ALL_CAPS_ATTRIBUTE.equals(name))
				return ((ImWord) this.imWords.get(0)).hasAttribute(ImWord.ALL_CAPS_ATTRIBUTE);
			else if (ImWord.FONT_SIZE_ATTRIBUTE.equals(name))
				return ((ImWord) this.imWords.get(0)).hasAttribute(ImWord.FONT_SIZE_ATTRIBUTE);
			else if (ImWord.FONT_NAME_ATTRIBUTE.equals(name))
				return ((ImWord) this.imWords.get(0)).hasAttribute(ImWord.FONT_NAME_ATTRIBUTE);
			else if (this.imWords.size() == 1) // just avoiding the computational effort of a for loop in the most common case
				return ((ImWord) this.imWords.get(0)).hasAttribute(name);
			else {
				for (int w = 0; w < this.imWords.size(); w++) {
					if (((ImWord) this.imWords.get(w)).hasAttribute(name))
						return true;
				}
				return false;
			}
		}
		public Object getAttribute(String name) {
			return this.getAttribute(name, null);
		}
		public Object getAttribute(String name, Object def) {
			checkValid();
			if (Token.PARAGRAPH_END_ATTRIBUTE.equals(name) && this.isParagraphEnd())
				return "true";
			else if (PAGE_ID_ATTRIBUTE.equals(name))
				return ("" + ((ImWord) this.imWords.get(0)).getPage().pageId);
			else if (LAST_PAGE_ID_ATTRIBUTE.equals(name))
				return ("" + ((ImWord) this.imWords.get(this.imWords.size()-1)).getPage().pageId);
			else if (BOUNDING_BOX_ATTRIBUTE.equals(name)) {
				BoundingBox bb  = this.getBoundingBox();
				return ((bb == null) ? def : bb.toString());
			}
			else if (PAGE_NUMBER_ATTRIBUTE.equals(name) && ((ImWord) this.imWords.get(0)).getPage().hasAttribute(PAGE_NUMBER_ATTRIBUTE))
				return ((ImWord) this.imWords.get(0)).getPage().getAttribute(PAGE_NUMBER_ATTRIBUTE);
			else if (LAST_PAGE_NUMBER_ATTRIBUTE.equals(name) && ((ImWord) this.imWords.get(this.imWords.size()-1)).getPage().hasAttribute(PAGE_NUMBER_ATTRIBUTE))
				return ((ImWord) this.imWords.get(this.imWords.size()-1)).getPage().getAttribute(PAGE_NUMBER_ATTRIBUTE);
			else if (ImWord.TEXT_STREAM_TYPE_ATTRIBUTE.equals(name))
				return ((ImWord) this.imWords.get(0)).getTextStreamType();
			else if (ImWord.PREVIOUS_RELATION_ATTRIBUTE.equals(name))
				return ("" + ((ImWord) this.imWords.get(0)).getPreviousRelation());
			else if (ImWord.NEXT_RELATION_ATTRIBUTE.equals(name))
				return ("" + ((ImWord) this.imWords.get(this.imWords.size()-1)).getNextRelation());
			else if (ImWord.BOLD_ATTRIBUTE.equals(name))
				return ((ImWord) this.imWords.get(0)).getAttribute(ImWord.BOLD_ATTRIBUTE, def);
			else if (ImWord.ITALICS_ATTRIBUTE.equals(name))
				return ((ImWord) this.imWords.get(0)).getAttribute(ImWord.ITALICS_ATTRIBUTE, def);
			else if (ImWord.ALL_CAPS_ATTRIBUTE.equals(name))
				return ((ImWord) this.imWords.get(0)).getAttribute(ImWord.ALL_CAPS_ATTRIBUTE, def);
			else if (ImWord.FONT_SIZE_ATTRIBUTE.equals(name))
				return ((ImWord) this.imWords.get(0)).getAttribute(ImWord.FONT_SIZE_ATTRIBUTE, def);
			else if (ImWord.FONT_NAME_ATTRIBUTE.equals(name))
				return ((ImWord) this.imWords.get(0)).getAttribute(ImWord.FONT_NAME_ATTRIBUTE, def);
			else if (this.imWords.size() == 1) // just avoiding the computational effort of a for loop in the most common case
				return ((ImWord) this.imWords.get(0)).getAttribute(name, def);
			else {
				for (int w = 0; w < this.imWords.size(); w++) {
					Object value = ((ImWord) this.imWords.get(w)).getAttribute(name);
					if (value != null)
						return value;
				}
				return def;
			}
		}
		public String[] getAttributeNames() {
			checkValid();
			TreeSet ans = new TreeSet();
			if (this.imWords.size() == 1) // just avoiding the computational effort of a for loop in the most common case
				ans.addAll(Arrays.asList(((ImWord) this.imWords.get(0)).getAttributeNames()));
			else for (int w = 0; w < this.imWords.size(); w++)
				ans.addAll(Arrays.asList(((ImWord) this.imWords.get(w)).getAttributeNames()));
			if (this.isParagraphEnd())
				ans.add(PARAGRAPH_END_ATTRIBUTE);
			if (this.getBoundingBox() != null)
				ans.add(BOUNDING_BOX_ATTRIBUTE);
			ans.add(ImWord.TEXT_STREAM_TYPE_ATTRIBUTE);
			if (((ImWord) this.imWords.get(0)).hasAttribute(ImWord.BOLD_ATTRIBUTE))
				ans.add(ImWord.BOLD_ATTRIBUTE);
			if (((ImWord) this.imWords.get(0)).hasAttribute(ImWord.ITALICS_ATTRIBUTE))
				ans.add(ImWord.ITALICS_ATTRIBUTE);
			if (((ImWord) this.imWords.get(0)).hasAttribute(ImWord.ALL_CAPS_ATTRIBUTE))
				ans.add(ImWord.ALL_CAPS_ATTRIBUTE);
			if (((ImWord) this.imWords.get(0)).hasAttribute(ImWord.FONT_SIZE_ATTRIBUTE))
				ans.add(ImWord.FONT_SIZE_ATTRIBUTE);
			if (((ImWord) this.imWords.get(0)).hasAttribute(ImWord.FONT_NAME_ATTRIBUTE))
				ans.add(ImWord.FONT_NAME_ATTRIBUTE);
			
			return ((String[]) ans.toArray(new String[ans.size()]));
		}
		public void clearAttributes() {
			checkValid();
			if (this.imWords.size() == 1) // just avoiding the computational effort of a for loop in the most common case
				((ImWord) this.imWords.get(0)).clearAttributes();
			else for (int w = 0; w < this.imWords.size(); w++)
				((ImWord) this.imWords.get(w)).clearAttributes();
		}
		public void copyAttributes(Attributed source) {
			checkValid();
			if (this.imWords.size() == 1) // just avoiding the computational effort of a for loop in the most common case
				((ImWord) this.imWords.get(0)).copyAttributes(source);
			else for (int w = 0; w < this.imWords.size(); w++)
				((ImWord) this.imWords.get(w)).copyAttributes(source);
		}
		public Object removeAttribute(String name) {
			return this.setAttribute(name, null);
		}
		public void setAttribute(String name) {
			this.setAttribute(name, "true");
		}
		public Object setAttribute(String name, Object value) {
			checkValid();
			if (Token.PARAGRAPH_END_ATTRIBUTE.equals(name)) {
				ImWord lastWord = ((ImWord) this.imWords.get(this.imWords.size() - 1));
				Object oldValue = (((lastWord.getNextRelation() == ImWord.NEXT_RELATION_PARAGRAPH_END) || (lastWord.getNextWord() == null)) ? "true" : null);
				if (value == null)
					lastWord.setNextRelation(ImWord.NEXT_RELATION_SEPARATE);
				else lastWord.setNextRelation(ImWord.NEXT_RELATION_PARAGRAPH_END);
				return oldValue;
			}
			else if (PAGE_ID_ATTRIBUTE.equals(name))
				return ("" + ((ImWord) this.imWords.get(0)).getPage().pageId);
			else if (LAST_PAGE_ID_ATTRIBUTE.equals(name))
				return ("" + ((ImWord) this.imWords.get(this.imWords.size()-1)).getPage().pageId);
			else if (BOUNDING_BOX_ATTRIBUTE.equals(name)) {
				BoundingBox bb  = this.getBoundingBox();
				return ((bb == null) ? null : bb.toString());
			}
			else if (PAGE_NUMBER_ATTRIBUTE.equals(name))
				return ((ImWord) this.imWords.get(0)).getPage().setAttribute(name, value);
			else if (LAST_PAGE_NUMBER_ATTRIBUTE.equals(name))
				return ((ImWord) this.imWords.get(this.imWords.size()-1)).getPage().setAttribute(name, value);
			else if (ImWord.TEXT_STREAM_TYPE_ATTRIBUTE.equals(name)) {
				if (value == null)
					return ((ImWord) this.imWords.get(0)).getTextStreamType();
				else return ((ImWord) this.imWords.get(0)).setTextStreamType(value.toString());
			}
			else if (ImWord.PREVIOUS_RELATION_ATTRIBUTE.equals(name)) {
				String oldValue = ("" + ((ImWord) this.imWords.get(0)).getPreviousRelation());
				if ((value != null) && (value.toString().length() == 1) && ("SCHP".indexOf(value.toString()) != -1) && (((ImWord) this.imWords.get(0)).getPreviousWord() != null))
					((ImWord) this.imWords.get(0)).getPreviousWord().setNextRelation(value.toString().charAt(0));
				return oldValue;
			}
			else if (ImWord.NEXT_RELATION_ATTRIBUTE.equals(name)) {
				String oldValue = ("" + ((ImWord) this.imWords.get(this.imWords.size()-1)).getNextRelation());
				if ((value != null) && (value.toString().length() == 1) && ("SCHP".indexOf(value.toString()) != -1))
					((ImWord) this.imWords.get(this.imWords.size()-1)).setNextRelation(value.toString().charAt(0));
				return oldValue;
			}
			if (this.imWords.size() == 1) // just avoiding the computational effort of a for loop in the most common case
				return ((ImWord) this.imWords.get(0)).setAttribute(name, value);
			Object oldValue = null;
			for (int w = 0; w < this.imWords.size(); w++) {
				Object imwOldValue = ((ImWord) this.imWords.get(w)).setAttribute(name, value);
				if (oldValue == null)
					oldValue = imwOldValue;
			}
			return oldValue;
		}
		boolean isParagraphEnd() {
			ImWord lastWord = ((ImWord) this.imWords.get(this.imWords.size() - 1));
			return ((lastWord.getNextRelation() == ImWord.NEXT_RELATION_PARAGRAPH_END) || (lastWord.getNextWord() == null));
		}
		ImToken deltaClone(int deltaOffset, int deltaIndex) {
			ImToken imt = new ImToken((this.startOffset - deltaOffset), (this.index - deltaIndex));
			imt.value = this.value;
			imt.whitespace = this.whitespace;
			imt.imWords = new ArrayList(this.imWords);
			imt.imWordAt = new ArrayList(this.imWordAt);
			return imt;
		}
		boolean contentEquals(ImToken imt) {
			if (!this.value.equals(imt.value) || !this.whitespace.equals(imt.whitespace))
				return false;
			if (this.imWords.size() != imt.imWords.size())
				return false;
			for (int w = 0; w < this.imWords.size(); w++) {
				if (this.imWords.get(w) != imt.imWords.get(w))
					return false;
			}
			if (this.imWordAt.size() != imt.imWordAt.size())
				return false;
			for (int w = 0; w < this.imWordAt.size(); w++) {
				if (this.imWordAt.get(w) != imt.imWordAt.get(w))
					return false;
			}
			return true;
		}
	}
	
	private static final boolean DEBUG_NORMALIZE_STRING = false;
	private static final String normalizeString(String str) {
		StringBuffer nStr = new StringBuffer();
		for (int c = 0; c < str.length(); c++) {
			char ch = str.charAt(c);
			String nCh = getNormalizedChar(ch);
			
			//	current letter not normalized to anything but itself, simply append it
			if (nCh == null) {
				nStr.append(ch);
				continue;
			}
			
			//	normalized value has upper case letters, and is longer than 1, take care of capitalization
			if (!nCh.equals(nCh.toLowerCase()) && (nCh.length() > 1)) {
				boolean upper = true;
				
				//	check character before (if any)
				if (c != 0) {
					String lnCh = nStr.substring(nStr.length() - 1);
					
					//	character before in lower case, keep camel case
					if (lnCh.equals(lnCh.toLowerCase()))
						upper = false;
				}
				
				//	check character after (if any)
				if ((c+1) < str.length()) {
					String nextStr = str.substring((c+1), (c+2));
					
					//	character before in lower case, keep camel case
					if (nextStr.equals(nextStr.toLowerCase()))
						upper = false;
				}
				
				//	we're in all upper case, follow suite
				if (upper)
					nCh = nCh.toUpperCase();
			}
			
			//	append what we've got
			nStr.append(nCh);
		}
		
		//	... finally
		if (DEBUG_NORMALIZE_STRING && !str.equals(nStr.toString()))
			System.out.println("Normalized '" + str + "' to '" + nStr.toString() + "'");
		return nStr.toString();
	}
	private static final String getNormalizedChar(char ch) {
		if ((ch <= 0x0020) || (ch == 0x00A0))
			return " "; // turn all control characters into spaces, along with non-breaking space
		else if (ch < 127)
			return null; // no need to normalize basic ASCII characters
//		else if ("-\u00AD\u2010\u2011\u2012\u2013\u2014\u2015\u2212".indexOf(ch) != -1)
		else if (StringUtils.DASHES.indexOf(ch) != -1)
			return "-"; // normalize dashes right here
		else return StringUtils.getNormalForm(ch);
	}
	
	
	static class BaseTokenSequenceListener implements ImDocumentListener {
		private ImTokenSequence parent;
		private ImDocument baseDoc;
		private WeakImDocumentListener weakWrapper;
		boolean parentModifyingBaseTokens = false;
		BaseTokenSequenceListener(ImTokenSequence parent, ImDocument baseDoc) {
			this.parent = parent;
			this.baseDoc = baseDoc;
			this.weakWrapper = new WeakImDocumentListener(this, this.baseDoc);
			baseDoc.addDocumentListener(this.weakWrapper);
		}
		public void typeChanged(ImObject object, String oldType) { /* not relevant for token sequence, implemented in full-document subclass */ }
		public void attributeChanged(ImObject object, String attributeName, Object oldValue) {
			if (this.parentModifyingBaseTokens)
				return; // we're doing this ourselves
			if ((object instanceof ImWord) && ImWord.NEXT_WORD_ATTRIBUTE.equals(attributeName)) /* change to text stream chaining not recoverable in general case */ {
				this.parent.invalidate();
				return;
			}
			if ((object instanceof ImWord) && ImWord.PREVIOUS_WORD_ATTRIBUTE.equals(attributeName)) /* change to text stream chaining not recoverable in general case */ {
				this.parent.invalidate();
				return;
			}
			if ((object instanceof ImWord) && ImWord.TEXT_STREAM_TYPE_ATTRIBUTE.equals(attributeName)) /* change to text stream types not recoverable in general case */ {
				this.parent.invalidate();
				return;
			}
			if ((object instanceof ImWord) && ImWord.STRING_ATTRIBUTE.equals(attributeName)) /* change to word string not recoverable in general case */ {
				ImWord imw;
				if (object instanceof ImWord)
					imw = ((ImWord) object);
				else return; // only handling word relation changes
				ImToken imt = this.parent.getTokenFor(imw);
				if (imt == null)
					return; // word outside our scope
				this.parent.wordStringChanged(imt);
			}
			if (ImWord.NEXT_RELATION_ATTRIBUTE.equals(attributeName)) {
				ImWord leftImw;
				if (object instanceof ImWord)
					leftImw = ((ImWord) object);
				else return; // only handling word relation changes
				ImToken leftImt = this.parent.getTokenFor(leftImw);
				if (leftImt == null)
					return; // word outside our scope
				ImWord rightImw = leftImw.getNextWord();
				if (rightImw == null)
					return; // text stream ends here (should hardly happen, but let's be safe)
				ImToken rightImt = this.parent.getTokenFor(rightImw);
				if (rightImt == null)
					return; // word outside our scope
				char oldNextRelation;
				if (oldValue instanceof CharSequence) {
					String oldValueStr = oldValue.toString();
					if (oldValueStr.length() == 1)
						oldNextRelation = oldValueStr.charAt(0);
					else return; // strange notification ...
				}
				else if (oldValue instanceof Character)
					oldNextRelation = ((Character) oldValue).charValue();
				else return; // strange notification ...
				this.parent.wordRelationChanged(leftImw, leftImt, oldNextRelation, rightImw, rightImt);
			}
		}
		public void supplementChanged(String supplementId, ImSupplement oldValue) { /* not relevant for token sequence */ }
		public void fontChanged(String fontName, ImFont oldValue) { /* not relevant for token sequence */ }
		public void regionAdded(ImRegion region) {
			if (this.parentModifyingBaseTokens)
				return; // we're doing this ourselves
			if ((region instanceof ImWord) && (this.parent.getTokenFor((ImWord) region) != null))
				this.parent.invalidate();
		}
		public void regionRemoved(ImRegion region) {
			if (this.parentModifyingBaseTokens)
				return; // we're doing this ourselves
			if ((region instanceof ImWord) && (this.parent.getTokenFor((ImWord) region) != null))
				this.parent.invalidate();
		}
		public void annotationAdded(ImAnnotation annotation) { /* not relevant for token sequence, implemented in full-document subclass */ }
		public void annotationRemoved(ImAnnotation annotation) { /* not relevant for token sequence, implemented in full-document subclass */ }
		void dispose() {
			if (this.baseDoc != null)
				this.baseDoc.removeDocumentListener(this.weakWrapper);
			this.baseDoc = null;
			this.weakWrapper = null;
		}
	}
	
	private Tokenizer tokenizer;
	private ArrayList tokens = new ArrayList();
	private HashMap imWordTokens = new HashMap();
	
	private int configFlags;
	
	private boolean initializing = true;
	private BaseTokenSequenceListener docListener;
	
	//	this one is for internal use only, namely for sub sequences, and for wrapping regions
//	ImTokenSequence(Tokenizer tokenizer) {
//		this(tokenizer, new ImWord[0], NORMALIZATION_LEVEL_STREAMS);
//	}
	ImTokenSequence(Tokenizer tokenizer, boolean finishInit) {
		this(tokenizer, new ImWord[0], NORMALIZATION_LEVEL_STREAMS, finishInit);
	}
	
	/**
	 * Constructor wrapping selected logical text streams of an Image Markup
	 * document.
	 * @param tokenizer the tokenizer to use
	 * @param textStreamHeads the heads of the logical text streams to include
	 *            in the wrapper token sequence 
	 * @param configFlags an integer bundling configuration flags, like the
	 *            normalization level, which specifies how to merge individual
	 *            text streams
	 */
	public ImTokenSequence(Tokenizer tokenizer, ImWord[] textStreamHeads, int configFlags) {
//		this.tokenizer = tokenizer;
//		this.addTextStreams(textStreamHeads, configFlags, null);
//		if (textStreamHeads.length != 0)
//			this.docListener = new BaseTokenSequenceListener(this, textStreamHeads[0].getDocument());
		this(tokenizer, textStreamHeads, configFlags, true);
	}
	ImTokenSequence(Tokenizer tokenizer, ImWord[] textStreamHeads, int configFlags, boolean finishInit) {
		this.tokenizer = tokenizer;
		this.configFlags = configFlags;
		this.addTextStreams(textStreamHeads, this.configFlags, null);
		if (finishInit && (textStreamHeads.length != 0)) {
			this.docListener = new BaseTokenSequenceListener(this, textStreamHeads[0].getDocument());
			this.initializing = false;
		}
	}
	
	void addTextStreams(ImWord[] textStreamHeads, int configFlags, Set wordFilter) {
		
		//	read config flags
		int normalizationLevel = (configFlags & NORMALIZATION_LEVEL_STREAMS);
		boolean normalizeChars = ((configFlags & NORMALIZE_CHARACTERS) != 0);
		
		//	set up word filtering
		CountingSet tsIdCounts;
		if (wordFilter == null)
			tsIdCounts = null;
		else {
			tsIdCounts = new CountingSet(new HashMap());
			for (Iterator wit = wordFilter.iterator(); wit.hasNext();)
				tsIdCounts.add(((ImWord) wit.next()).getTextStreamId());
		}
		
		//	line up and sort text stream heads
		ArrayList tshList = new ArrayList(Arrays.asList(textStreamHeads));
		sort(tshList); // we need to use our own little sort routine, as the JRE ones require a total order as of Java 1.7
		
		//	add text
		while (tshList.size() != 0) {
			
			//	get next stream head to process
			ImWord tsh = ((ImWord) tshList.get(0));
			
			//	add next chunk of words from current text stream
			ImWord imw = tsh;
			while (imw != null) {
				
				//	are we supposed to use this word?
				if ((wordFilter != null) && !wordFilter.remove(imw)) {
					imw = imw.getNextWord();
					break;
				}
				
				//	add word
				this.addImWord(imw, (normalizationLevel == NORMALIZATION_LEVEL_RAW), normalizeChars);
				
				//	remember we've added one word from this stream
				if (tsIdCounts != null) {
					tsIdCounts.remove(imw.getTextStreamId());
					if (tsIdCounts.getCount(imw.getTextStreamId()) == 0) {
						imw = null;
						break;
					}
				}
				
				//	store relation to next word
				char nr = imw.getNextRelation();
				
				//	switch to next word
				imw = imw.getNextWord();
				
				//	we're going paragraph by paragraph, stop only at paragraph ends
				if (normalizationLevel == NORMALIZATION_LEVEL_PARAGRAPHS) {
					if (nr == ImWord.NEXT_RELATION_PARAGRAPH_END)
						break;
				}
				
				//	we're going word by word, stop only at word ends
				else if (normalizationLevel == NORMALIZATION_LEVEL_WORDS) {
					if ((nr != ImWord.NEXT_RELATION_CONTINUE) && (nr != ImWord.NEXT_RELATION_HYPHENATED))
						break;
				}
				
				//	we're going raw word by raw word, always stop
				else if (normalizationLevel == NORMALIZATION_LEVEL_RAW)
					break;
			}
			
			//	we're done with this stream
			if (imw == null)
				tshList.remove(0);
			
			//	continue with next word
			else {
				
				//	put it into list ...
				tshList.set(0, imw);
				
				//	... and use a single pass of bubble sort to get it into position
				bubbleFirstIntoPlace(tshList);
			}
		}
	}
	
	private static void bubbleFirstIntoPlace(ArrayList words) {
		for (int w = 1; w < words.size(); w++) {
			ImWord imw1 = ((ImWord) words.get(w-1));
			ImWord imw2 = ((ImWord) words.get(w));
			if ((imw1.pageId < imw2.pageId))
				return;
			if ((imw1.pageId == imw2.pageId) && (compare(imw1, imw2) <= 0))
				return;
			words.set(w, words.set((w-1), words.get(w)));
		}
	}
	
	private static void sort(ArrayList words) {
		
		//	sort by page ID first (this _is_ a total order)
		Collections.sort(words, textStreamHeadPageIdOrder);
		
		//	bubble sort with our own comparison inside pages
		int pageStart = 0;
		for (int w = 1; w < words.size(); w++)
			
			//	this word starts a new page, sort previous one
			if (((ImWord) words.get(w)).pageId > ((ImWord) words.get(pageStart)).pageId) {
				sort(words, pageStart, w);
				pageStart = w;
			}
		
		//	sort last page
		sort(words, pageStart, words.size());
	}
	
	private static final Comparator textStreamHeadPageIdOrder = new Comparator() {
		public int compare(Object obj1, Object obj2) {
			return (((ImWord) obj1).pageId - ((ImWord) obj2).pageId);
		}
	};
	
	/* Yes, admittedly, this bubble-sorts the specified section of the list.
	 * In practice, however, the number of text stream heads in a single page
	 * will be less than 10 in the very most cases, so we can safely accept
	 * square complexity for the benefit of true in-place behavior, and the
	 * property of being able to make do with a sort order that is not a total
	 * order in some pathologic cases of weirdly overlapping and intersecting
	 * words, like the strange OCR 'results' originating from figures */
	private static void sort(ArrayList words, int from, int to) {
		for (int r = 0; r < (to - from); r++) {
			boolean unmodified = true;
			for (int w = (from + 1); w < to; w++)
				if (0 < compare(((ImWord) words.get(w-1)), ((ImWord) words.get(w)))) {
					words.set(w, words.set((w-1), words.get(w)));
					unmodified = false;
				}
			if (unmodified)
				return;
		}
	}
	
	private static int compare(ImWord imw1, ImWord imw2) {
		
		//	same line (center Y of either word lies within the other) ==> sort left to right
		if (((imw2.bounds.top < imw1.centerY) && (imw1.centerY < imw2.bounds.bottom)) || (imw1.bounds.top < imw2.centerY) && (imw2.centerY < imw1.bounds.bottom))
			return (imw1.centerX - imw2.centerX);
		
		//	different lines, ==> sort top to bottom
		else return (imw1.centerY - imw2.centerY);
	}
	
//	/* WE CANNOT USE THIS SOPHISTICATED COMPARISON: As of Java 1.7, sorting
//	 * routines strictly require a total order, and this comparison doesn't
//	 * represent one in all cases. */
//	private static final Comparator layoutWordOrder = new Comparator() {
//		public int compare(Object obj1, Object obj2) {
//			ImWord imw1 = ((ImWord) obj1);
//			ImWord imw2 = ((ImWord) obj2);
//			
//			//	different pages
//			if (imw1.pageId != imw2.pageId)
//				return (imw1.pageId - imw2.pageId);
//			
//			//	same text stream, use ordering position
//			if (imw1.getTextStreamId().equals(imw2.getTextStreamId()))
//				return (imw1.getTextStreamPos() - imw2.getTextStreamPos());
//			
//			//	use bounding boxes
//			if (imw1.bounds.bottom <= imw2.bounds.top)
//				return -1;
//			if (imw2.bounds.bottom <= imw1.bounds.top)
//				return 1;
//			if (imw1.bounds.right <= imw2.bounds.left)
//				return -1;
//			if (imw2.bounds.right <= imw1.bounds.left)
//				return 1;
//			
//			//	handle overlapping bounding boxes
//			if (imw1.bounds.top != imw2.bounds.top)
//				return (imw1.bounds.top - imw2.bounds.top);
//			if (imw1.bounds.left != imw2.bounds.left)
//				return (imw1.bounds.left - imw2.bounds.left);
//			if (imw1.bounds.bottom != imw2.bounds.bottom)
//				return (imw2.bounds.bottom - imw1.bounds.bottom);
//			if (imw1.bounds.right != imw2.bounds.right)
//				return (imw2.bounds.right - imw1.bounds.right);
//			
//			//	little we can do about these two ...
//			return 0;
//		}
//	};
	
	/**
	 * Constructor wrapping a sequence of words of an Image Markup document.
	 * The two argument words have to belong to the same logical text stream
	 * for the constructed object to work in a meaningful way.
	 * @param firstWord the first word to include in the wrapper token sequence
	 * @param lastWord the last word to include in the wrapper token sequence
	 */
	public ImTokenSequence(ImWord firstWord, ImWord lastWord) {
		this(firstWord, lastWord, false);
	}
	
	/**
	 * Constructor wrapping a sequence of words of an Image Markup document.
	 * The two argument words have to belong to the same logical text stream
	 * for the constructed object to work in a meaningful way.
	 * @param firstWord the first word to include in the wrapper token sequence
	 * @param lastWord the last word to include in the wrapper token sequence
	 * @param normalizeChars normalize diacritics to their base characters?
	 */
	public ImTokenSequence(ImWord firstWord, ImWord lastWord, boolean normalizeChars) {
		this(firstWord, lastWord, (normalizeChars ? NORMALIZE_CHARACTERS : 0));
	}
	
	/**
	 * Constructor wrapping a sequence of words of an Image Markup document.
	 * The two argument words have to belong to the same logical text stream
	 * for the constructed object to work in a meaningful way.
	 * @param firstWord the first word to include in the wrapper token sequence
	 * @param lastWord the last word to include in the wrapper token sequence
	 * @param configFlags an integer bundling configuration flags, like the
	 *            normalization level, which specifies how to merge individual
	 *            text streams
	 */
	public ImTokenSequence(ImWord firstWord, ImWord lastWord, int configFlags) {
//		this(((Tokenizer) firstWord.getDocument().getAttribute(ImDocument.TOKENIZER_ATTRIBUTE, Gamta.INNER_PUNCTUATION_TOKENIZER)), firstWord, lastWord, configFlags);
//		this(((Tokenizer) firstWord.getDocument().getAttribute(ImDocument.TOKENIZER_ATTRIBUTE, Gamta.getDefaultTokenizer())), firstWord, lastWord, configFlags);
		this(((Tokenizer) firstWord.getDocument().getAttribute(ImDocument.TOKENIZER_ATTRIBUTE, Gamta.getDefaultTokenizer())), firstWord, lastWord, configFlags, true);
	}
	
//	ImTokenSequence(Tokenizer tokenizer, ImWord firstWord, ImWord lastWord, int configFlags) {
	ImTokenSequence(Tokenizer tokenizer, ImWord firstWord, ImWord lastWord, int configFlags, boolean finishInit) {
		this.tokenizer = tokenizer;
		this.configFlags = configFlags;
		boolean normalizeChars = ((this.configFlags & NORMALIZE_CHARACTERS) != 0);
		boolean rawWords = ((this.configFlags & NORMALIZATION_LEVEL_STREAMS) == NORMALIZATION_LEVEL_RAW); 
		for (ImWord imw = firstWord; imw != null; imw = imw.getNextWord()) {
			this.addImWord(imw, rawWords, normalizeChars);
			if (imw == lastWord)
				break;
		}
		if (finishInit) {
			this.docListener = new BaseTokenSequenceListener(this, firstWord.getDocument());
			this.initializing = false;
		}
	}
	
	private static final boolean DEBUG_ADD_WORDS = false;
	private void addImWord(ImWord imw, boolean forceNewToken, boolean normalizeChars) {
		if (DEBUG_ADD_WORDS) System.out.println("ADDING WORD '" + imw.getString() + "' at " + imw.getLocalID());
//		if ((imw.getString() == null) || (imw.getString().length() == 0) || (imw.getPage() == null))
//			return;
		if (imw.getPage() == null) {
			if (DEBUG_ADD_WORDS) System.out.println(" ==> ignoring detached word");
			return;
		}
		String imwStr = imw.getString();
		if (imwStr == null) {
			if (DEBUG_ADD_WORDS) System.out.println(" ==> ignoring word without string");
			return;
		}
		if (normalizeChars) {
			imwStr = normalizeString(imwStr);
			if (DEBUG_ADD_WORDS) System.out.println(" - string normalized to '" + imwStr + "' (length " + imwStr.length() + ")");
		}
		if (imwStr.length() == 0) {
			if (DEBUG_ADD_WORDS) System.out.println(" ==> ignoring word with empty string");
			return;
		}
		
		if (this.tokens.isEmpty()) {
//			ImToken imt = new ImToken(0, 0, imw, normalizeChars);
			ImToken imt = new ImToken(0, 0, imw, imwStr);
			this.tokens.add(imt);
			this.imWordTokens.put(imw.getLocalID(), imt);
			if (DEBUG_ADD_WORDS) System.out.println(" ==> first word");
			return;
		}
		
		//	get predecessors
		ImToken pImt = this.imTokenAtIndex(this.tokens.size()-1);
		ImWord pImw = imw.getPreviousWord();
		
		//	determine whether or not to start new token
		boolean startNewToken;
		if (forceNewToken)
			startNewToken = true;
		else if (pImw == null)
			startNewToken = true;
		else if (pImw.getNextRelation() == ImWord.NEXT_RELATION_SEPARATE)
			startNewToken = true;
		else if (pImw.getNextRelation() == ImWord.NEXT_RELATION_PARAGRAPH_END)
			startNewToken = true;
		else /* check tokenization with 'continue' and 'hyphenated' */ {
			String pImtStr = pImt.value;
//			String imwStr = imw.getString();
//			if (imwStr == null)
//				imwStr = "";
//			else if (normalizeChars)
//				imwStr = normalizeString(imwStr);
			if (pImw.getNextRelation() == ImWord.NEXT_RELATION_HYPHENATED)
				pImtStr = pImtStr.substring(0, (pImtStr.length() - "-".length()));
			String imtStr = (pImtStr + imwStr);
			startNewToken = (this.tokenizer.tokenize(imtStr).size() > 1);
		}
		
		//	no previous word, or words not connected
//		if ((pImw == null) || forceNewToken || ((pImw.getNextRelation() != ImWord.NEXT_RELATION_CONTINUE) && (pImw.getNextRelation() != ImWord.NEXT_RELATION_HYPHENATED))) {
		if (startNewToken) {
			if (DEBUG_ADD_WORDS) System.out.println(" ==> new word");
			
			//	text streams not connected ==> set whitespace
			if (pImw == null) {
				pImt.whitespace = " ";
				if (DEBUG_ADD_WORDS) System.out.println("   ==> space for null predecessor");
			}
			
			//	make paragraph break explicit
			else if (pImw.getNextRelation() == ImWord.NEXT_RELATION_PARAGRAPH_END) {
				pImt.whitespace = "\r\n";
				if (DEBUG_ADD_WORDS) System.out.println("   ==> explicit paragraph break");
			}
			
			//	word continues, if with new token, omit space (unless with a forced new token, with these two word relations we only get here if they tokenize apart anyway)
			else if (!forceNewToken && (pImw.getNextRelation() == ImWord.NEXT_RELATION_CONTINUE)) {}
			else if (!forceNewToken && (pImw.getNextRelation() == ImWord.NEXT_RELATION_HYPHENATED)) {}
			
			//	check for text flow break
			else if (ImUtils.areTextFlowBreak(pImw, imw)) {
				pImt.whitespace = " ";
				if (DEBUG_ADD_WORDS) System.out.println("   ==> space for text flow break");
			}
			
			//	words not on same page ==> set whitespace
			else if (pImw.pageId != imw.pageId) {
				pImt.whitespace = " ";
				if (DEBUG_ADD_WORDS) System.out.println("   ==> space for page break");
			}
			
			//	words not on same line ==> set whitespace
			else if ((pImw.bounds.bottom <= imw.bounds.top) || (imw.bounds.bottom <= pImw.bounds.top) || (imw.bounds.left < pImw.bounds.left)) {
				pImt.whitespace = " ";
				if (DEBUG_ADD_WORDS) System.out.println("   ==> space for line break");
			}
			
			//	words distance large enough ==> set whitespace
			else if (ImUtils.areSpaced(pImw, imw)) {
				pImt.whitespace = " ";
				if (DEBUG_ADD_WORDS) System.out.println("   ==> space for distance to predecessor");
			}
			
			//	words distance of more than one eighth of word height ==> set whitespace
			else if ((((pImw.bounds.bottom - pImw.bounds.top) + 7 + (imw.bounds.bottom - imw.bounds.top) + 7) / (8 * 2)) <= (imw.bounds.left - pImw.bounds.right)) {
				pImt.whitespace = " ";
				if (DEBUG_ADD_WORDS) System.out.println("   ==> space for distance to predecessor (space " + (imw.bounds.left - pImw.bounds.right) + " vs. line height " + (((pImw.bounds.bottom - pImw.bounds.top) + (imw.bounds.bottom - imw.bounds.top)) / 2) + ")");
			}
			
			//	words on the same line and would not tokenize apart ==> set whitespace for padding (e.g. in raw mode)
//			else if (this.tokenizer.tokenize(pImt.value + imw.getString()).size() <= this.tokenizer.tokenize(pImt.value).size()) {
			else if (this.tokenizer.tokenize(pImt.value + imwStr).size() <= this.tokenizer.tokenize(pImt.value).size()) {
				pImt.whitespace = " ";
				if (DEBUG_ADD_WORDS) System.out.println("   ==> space for token padding");
			}
			
			else if (DEBUG_ADD_WORDS) System.out.println("   ==> no space");
			
			//	start new token
//			ImToken imt = new ImToken(pImt.imtEndOffset(), this.tokens.size(), imw, normalizeChars);
			ImToken imt = new ImToken(pImt.imtEndOffset(), this.tokens.size(), imw, imwStr);
			this.tokens.add(imt);
			this.imWordTokens.put(imw.getLocalID(), imt);
		}
		
		//	this word belongs to previous one
		else {
//			pImt.addWord(imw, normalizeChars);
			pImt.addWord(imw, imwStr);
			this.imWordTokens.put(imw.getLocalID(), pImt);
			if (DEBUG_ADD_WORDS) System.out.println(" ==> continued word");
		}
	}
//	
//	void hyphenateTokens(ImToken token) {
//		WILL NEVER BE NEEDED, CANNOT SPLIT PARAGRAPH IN MIDDLE OF TOKEN
//	}
	
	/**
	 * Check whether or not the wrapper is still usable.
	 * @return true if the wrapper is still usable
	 */
	public boolean isValid() {
		return (this.docListener != null);
	}
	
	void invalidate() {
		if (this.docListener != null)
			this.docListener.dispose();
		this.docListener = null;
	}
	
	void finishInit(BaseTokenSequenceListener btsl) {
		if (this.docListener != null)
			throw new IllegalStateException("Base token sequence listener already set");
		this.docListener = btsl;
		this.initializing = false;
	}
	
	void checkValid() {
		if (this.initializing)
			return;
		if (this.docListener == null)
			throw new IllegalStateException("Invalid due to unrecoverable changes to underlying document");
	}
	
	void dehyphenateTokens(ImToken firstToken, ImToken secondToken) {
		
		//	truncate value of first token to get rid of hyphen and space
		int offsetShift = firstToken.whitespace.length();
		while (firstToken.value.endsWith("-")) {
			firstToken.value = firstToken.value.substring(0, (firstToken.value.length() - "-".length()));
			firstToken.imWordAt.remove(firstToken.value.length());
			offsetShift++;
		}
		
		//	append second token, taking whitespace from latter
		firstToken.value = (firstToken.value + secondToken.value);
		firstToken.imWords.addAll(secondToken.imWords);
		firstToken.imWordAt.addAll(secondToken.imWordAt);
		firstToken.whitespace = secondToken.whitespace;
		
		//	remove second token and map words to first token
		this.tokens.remove(secondToken.index);
		for (int w = 0; w < secondToken.imWords.size(); w++)
			this.imWordTokens.put(((ImWord) secondToken.imWords.get(w)).getLocalID(), firstToken);
		
		//	adjust index and offset of subsequent tokens
		for (int t = (firstToken.index + 1); t < this.tokens.size(); t++) {
			ImToken token = ((ImToken) this.tokens.get(t));
			token.index -= 1;
			token.startOffset -= offsetShift;
		}
	}
	
	ImToken getTokenFor(ImWord imw) {
		return ((ImToken) this.imWordTokens.get(imw.getLocalID()));
	}
	
	int getTokenIndexOf(ImWord imw) {
		ImToken imt = this.getTokenFor(imw);
//		if (imt == null) {
//			System.out.println("Strange word " + imw + " at " + imw.getLocalID() + ", could not find token");
//			throw new RuntimeException("GOTCHA !!!");
//		}
		return ((imt == null) ? -1 : imt.index);
	}
	
	ImToken imTokenAtIndex(int index) {
		return ((ImToken) this.tokens.get(index));
	}
	ImToken imTokenAtOffset(int offset) {
		int index = ((offset * this.size()) / this.length());
		if (index < 0)
			index = 0;
		else if ((this.size() - 1) < index)
			index = (this.size() - 1);
		ImToken tao = this.imTokenAtIndex(index);
		while (tao.imtEndOffset() <= offset) {
			index++;
			tao = this.imTokenAtIndex(index);
		}
		while (offset < tao.getStartOffset()) {
			index--;
			tao = this.imTokenAtIndex(index);
		}
		return tao;
	}
	
	/**
	 * Retrieve the underlying word at some char offset. If the string value of
	 * the returned word is modified in any way, this token sequence becomes
	 * invalid and should not be used any further.
	 * @param offset the offset to find the word for
	 * @return the word including the argument char offset
	 */
	public ImWord wordAtOffset(int offset) {
		this.checkValid();
		ImToken tao = this.imTokenAtOffset(offset);
		return ((tao == null) ? null : tao.wordAt(offset - tao.getStartOffset()));
	}
	
	/**
	 * Retrieve the underlying word at some token index. If the string value of
	 * the returned word is modified in any way, this token sequence becomes
	 * invalid and should not be used any further.
	 * @param index the index to find the word for
	 * @return the word at the argument token index
	 */
	public ImWord wordAtIndex(int index) {
		this.checkValid();
		ImToken tao = this.imTokenAtIndex(index);
		return ((tao == null) ? null : tao.wordAt(0));
	}
	
	public char charAt(int index) {
		this.checkValid();
		ImToken tao = this.imTokenAtOffset(index);
		return tao.charAt(index - tao.getStartOffset());
	}
	public int length() {
		this.checkValid();
		if (this.tokens.isEmpty())
			return 0;
		else return this.imTokenAtIndex(this.tokens.size()-1).getEndOffset();
	}
	public CharSequence subSequence(int start, int end) {
		this.checkValid();
		return this.mutableSubSequence(start, end);
	}
	public void addChar(char ch) {/* we're not modifying any chars in this wrapper for now */}
	public void addChars(CharSequence chars) {/* we're not modifying any chars in this wrapper for now */}
	public void insertChar(char ch, int offset) {
		this.setChars(("" + ch), offset, 0);
	}
	public void insertChars(CharSequence chars, int offset) {
		this.setChars(chars, offset, 0);
	}
	public char removeChar(int offset) {
		return this.setChars("", offset, 1).charAt(0);
	}
	public CharSequence removeChars(int offset, int length) {
		return this.setChars("", offset, length);
	}
	public char setChar(char ch, int offset) {
		return this.setChars(("" + ch), offset, 1).charAt(0);
	}
	public CharSequence setChars(CharSequence chars, int offset, int length) {
		this.checkValid();
		System.out.println("Setting " + length + " chars (" + offset + "-" + (offset + length - 1) + ") to " + chars);
		
		//	remember sub sequence to be replaced
		CharSequence replaced = this.subSequence(offset, (offset + length));
		
		//	get affected tokens and words
		ArrayList imTokens = new ArrayList();
		ArrayList imWords = new ArrayList();
		
		//	get first token (looping over length won't work on insertions, where we have a 0 length)
		ImToken firstImt = this.imTokenAtOffset(offset);
		while (firstImt.index != 0) {
			ImToken pfImt = this.imTokenAtIndex(firstImt.index-1);
			if (pfImt.whitespace.length() != 0)
				break;
			else firstImt = pfImt;
		}
		int startIndex = firstImt.index;
		int startOffset = firstImt.startOffset;
		System.out.println(" - first affected token: " + firstImt.value);
		
		//	get all further tokens
		for (int o = firstImt.getStartOffset(); /* break condition in loop body */; /* increment by token length in loop body */) {
			ImToken imt = this.imTokenAtOffset(o);
			System.out.println(" - affected token: " + imt.value);
			imTokens.add(imt);
			imWords.addAll(imt.imWords);
			o += imt.imtLength();
			if (((offset+length) <= o) && ((imt.whitespace.length() != 0) || ((imt.index+1) == this.tokens.size())))
				break;
		}
		
		//	construct char sequence over affected tokens, keeping track of words
		StringBuffer charSequence = new StringBuffer();
		ArrayList imWordAt = new ArrayList();
		for (int t = 0; t < imTokens.size(); t++) {
			if (t != 0) {
				ImToken pImt = ((ImToken) imTokens.get(t-1));
				for (int w = 0; w < pImt.whitespace.length(); w++)
					imWordAt.add(null);
			}
			ImToken imt = ((ImToken) imTokens.get(t));
			charSequence.append(imt.value);
			imWordAt.addAll(imt.imWordAt);
		}
		System.out.println(" - affected char sequence is '" + charSequence + "'");
		
		//	construct new char sequence
		StringBuffer updateCharSequence = new StringBuffer(charSequence);
		updateCharSequence.delete((offset - firstImt.getStartOffset()), (offset + length - firstImt.getStartOffset()));
		updateCharSequence.insert((offset - firstImt.getStartOffset()), chars);
		System.out.println(" - updated char sequence is '" + updateCharSequence + "'");
		
		//	nothing changing at all, we're done
		if (updateCharSequence.toString().equals(charSequence.toString())) {
			System.out.println(" ==> nothing changing at all");
			return replaced;
		}
		
		//	test if update permitted
		TokenSequence updateTokenSequence = this.tokenizer.tokenize(updateCharSequence);
		if (updateTokenSequence.size() > imWords.size()) {
			System.out.println(" ==> too many tokens for underlying Image Markup words");
			throw new IllegalArgumentException("Increase in number of tokens from '" + charSequence + "' to '" + updateCharSequence + "' not allowed");
		}
		
		//	map old char sequence to new one (Levenshtein?)
		//	we do this case insensitive, so case changes are preferred over other replacements
		int[] les = StringUtils.getLevenshteinEditSequence(charSequence.toString(), updateCharSequence.toString(), false);
		System.out.println(" - edit sequence is " + Arrays.toString(les));
		ArrayList updateImWordAt = new ArrayList();
		int imTsIndex = 0;
		int uImTsIndex = 0;
		for (int s = 0; s < les.length; s++) {
			
			//	keep or replace
			if ((les[s] == StringUtils.LEVENSHTEIN_KEEP) || (les[s] == StringUtils.LEVENSHTEIN_REPLACE)) {
				updateImWordAt.add(imWordAt.get(imTsIndex));
				imTsIndex++;
				uImTsIndex++;
			}
			
			//	delete
			else if (les[s] == StringUtils.LEVENSHTEIN_DELETE)
				imTsIndex++;
			
			//	insert
			if (les[s] == StringUtils.LEVENSHTEIN_INSERT)  {
				if (updateCharSequence.charAt(uImTsIndex) == ' ')
					updateImWordAt.add(null);
				else {
					ImWord pImw = ((uImTsIndex == 0) ? null : ((ImWord) updateImWordAt.get(uImTsIndex-1)));
					ImWord nImw = ((imTsIndex < imWordAt.size()) ? ((ImWord) imWordAt.get(imTsIndex)) : null);
					if ((pImw == null) && (nImw == null))
						throw new RuntimeException("No Image Markup word to map char '" + updateCharSequence.charAt(uImTsIndex) + "' to");
					else if (pImw != null)
						updateImWordAt.add(pImw);
					else if (nImw != null)
						updateImWordAt.add(nImw);
				}
				uImTsIndex++;
			}
		}
		System.out.println(" - got " + updateImWordAt.size() + " char position Image Markup words");
		
		//	change word strings
		ImWord uImw = null;
		StringBuffer uImwString = null;
		for (int c = 0; c < updateCharSequence.length(); c++) {
			ImWord imw = ((ImWord) updateImWordAt.get(c));
			if (imw == uImw) {
				if (uImwString != null)
					uImwString.append(updateCharSequence.charAt(c));
			}
			else {
				if ((uImw != null) && (uImwString != null)) {
					System.out.println(" - '" + uImw.getString() + "' set to '" + uImwString.toString() + "'");
					uImw.setString(uImwString.toString());
				}
				uImw = imw;
				if (uImw != null) {
					uImwString = new StringBuffer();
					uImwString.append(updateCharSequence.charAt(c));
				}
			}
		}
		if ((uImw != null) && (uImwString != null)) {
			System.out.println(" - '" + uImw.getString() + "' set to '" + uImwString.toString() + "'");
			uImw.setString(uImwString.toString());
		}
		
		//	create new token overlay (no need for char normalization here, as we're only copying around already normalized text)
		HashSet updateImWordIDs = new HashSet();
		ArrayList updateImTokens = new ArrayList();
		for (int t = 0; t < updateTokenSequence.size(); t++) {
			Token updateToken = updateTokenSequence.tokenAt(t);
			uImw = ((ImWord) updateImWordAt.get(updateToken.getStartOffset()));
			updateImWordIDs.add(uImw.getLocalID());
//			ImToken uImt = new ImToken((firstImt.startOffset + updateToken.getStartOffset()), (firstImt.index + t), uImw, false);
			ImToken uImt = new ImToken((firstImt.startOffset + updateToken.getStartOffset()), (firstImt.index + t), uImw, uImw.getString());
			for (int o = 1; o < updateToken.length(); o++) {
				 ImWord imw = ((ImWord) updateImWordAt.get(updateToken.getStartOffset() + o));
				 if (imw == uImw)
					 continue;
				 uImw.setNextRelation(ImWord.NEXT_RELATION_CONTINUE);
				 uImw = imw;
				 updateImWordIDs.add(uImw.getLocalID());
//				 uImt.addWord(uImw, false);
				 uImt.addWord(uImw, uImw.getString());
			}
			updateImTokens.add(uImt);
			if (((t+1) < updateTokenSequence.size()) && (updateTokenSequence.getWhitespaceAfter(t).length() != 0))
				uImt.whitespace = " ";
		}
		
		//	copy whitespace of last affected token
		if ((imTokens.size() != 0) && (updateImTokens.size() != 0))
			((ImToken) updateImTokens.get(updateImTokens.size()-1)).whitespace = ((ImToken) imTokens.get(imTokens.size()-1)).whitespace;
		
		//	cut lead and tail update tokens that remain unchanged
		while ((imTokens.size() != 0) && (updateImTokens.size() != 0)) {
			ImToken imt = ((ImToken) imTokens.get(0));
			ImToken uImt = ((ImToken) updateImTokens.get(0));
			System.out.print(" - comparing lead tokens '" + imt.value + "' and '" + uImt.value + "'");
			if (imt.contentEquals(uImt)) {
				imTokens.remove(0);
				updateImTokens.remove(0);
				System.out.println(" ==> retained");
			}
			else {
				firstImt = imt;
				System.out.println(" ==> update required");
				break;
			}
		}
		while ((imTokens.size() != 0) && (updateImTokens.size() != 0)) {
			ImToken imt = ((ImToken) imTokens.get(imTokens.size()-1));
			ImToken uImt = ((ImToken) updateImTokens.get(updateImTokens.size()-1));
			System.out.print(" - comparing tail tokens '" + imt.value + "' and '" + uImt.value + "'");
			if (imt.contentEquals(uImt)) {
				imTokens.remove(imTokens.size()-1);
				updateImTokens.remove(updateImTokens.size()-1);
				System.out.println(" ==> retained");
			}
			else {
				System.out.println(" ==> update required");
				break;
			}
		}
		
		//	notify annotation overlay of imminent replacement (we cannot simply quit here even if tokens match, as word strings and thus offsets might have changed)
		if ((imTokens.size() != 0) || (updateImTokens.size() != 0)) {
			this.replacingTokensAt(firstImt.index, ((ImToken[]) imTokens.toArray(new ImToken[imTokens.size()])), ((ImToken[]) updateImTokens.toArray(new ImToken[updateImTokens.size()])));
			System.out.println(" - before notification done");
		}
		else System.out.println(" - no word changes beyond strings, no need for notification");
		
		//	replace tokens
		for (int t = 0; t < imTokens.size(); t++) {
			ImToken imt = ((ImToken) imTokens.get(t));
			if (t < updateImTokens.size()) {
				ImToken uImt = ((ImToken) updateImTokens.get(t));
				this.tokens.set(imt.index, uImt);
			}
			else this.tokens.remove(firstImt.index + updateImTokens.size());
		}
		for (int t = imTokens.size(); t < updateImTokens.size(); t++)
			this.tokens.add((firstImt.index + t), updateImTokens.get(t));
		System.out.println(" - tokens replaced");
		
		//	update word-to-token index
		for (int t = 0; t < imTokens.size(); t++) {
			ImToken imt = ((ImToken) imTokens.get(t));
			for (int w = 0; w < imt.imWords.size(); w++)
				this.imWordTokens.remove(((ImWord) imt.imWords.get(w)).getLocalID());
		}
		for (int t = 0; t < updateImTokens.size(); t++) {
			ImToken imt = ((ImToken) updateImTokens.get(t));
			for (int w = 0; w < imt.imWords.size(); w++)
				this.imWordTokens.put(((ImWord) imt.imWords.get(w)).getLocalID(), imt);
		}
		
		//	update token indexes and offsets right of replacement
		for (int t = startIndex; t < this.tokens.size(); t++) {
			ImToken imt = this.imTokenAtIndex(t);
			imt.index = t;
			imt.startOffset = startOffset;
			startOffset = imt.imtEndOffset();
		}
		System.out.println(" - subsequent tokens adjusted");
		
		//	flag words as deleted and cut them from stream if not occurring in updated sequence
		for (int w = 0; w < imWords.size(); w++) {
			ImWord imw = ((ImWord) imWords.get(w));
			if (updateImWordIDs.contains(imw.getLocalID()))
				continue;
			ImWord imwPrev = imw.getPreviousWord();
			ImWord imwNext = imw.getNextWord();
			if ((imwPrev != null) && (imwNext != null))
				imwPrev.setNextWord(imwNext);
			else {
				imw.setPreviousWord(null);
				imw.setNextWord(null);
			}
			imw.setTextStreamType(ImWord.TEXT_STREAM_TYPE_DELETED);
			System.out.println(" - obsolete word '" + imw.getString() + "' cut from Image Markup document");
		}
		
		//	notify annotation overlay of completed replacement
		if ((imTokens.size() != 0) || (updateImTokens.size() != 0)) {
			this.replacedTokensAt(firstImt.index, ((ImToken[]) imTokens.toArray(new ImToken[imTokens.size()])), ((ImToken[]) updateImTokens.toArray(new ImToken[updateImTokens.size()])));
			System.out.println(" - after notification done");
		}
		else System.out.println(" - no word changes beyond strings, no need for notification");
		
		//	finally ...
		System.out.println(" ==> token sequence updated");
		return replaced;
	}
	void replacingTokensAt(int index, ImToken[] replaced, ImToken[] replacement) {/* this method only exists so document wrapper can overwrite it and adjust annotations */}
	void replacedTokensAt(int index, ImToken[] replaced, ImToken[] replacement) {/* this method only exists so document wrapper can overwrite it and adjust annotations */}
	public MutableCharSequence mutableSubSequence(int start, int end) {
		this.checkValid();
		StringBuffer subSequence = new StringBuffer();
		ImToken imt = this.imTokenAtOffset(start);
		subSequence.append(imt.subSequence((start - imt.getStartOffset()), Math.min(imt.imtLength(), (end - imt.getStartOffset()))));
		while (imt.imtEndOffset() < end) {
			imt = this.imTokenAtIndex(imt.index+1);
			start = imt.getStartOffset();
			subSequence.append(imt.subSequence((start - imt.getStartOffset()), Math.min(imt.imtLength(), (end - imt.getStartOffset()))));
		}
		return new StringBufferCharSequence(subSequence);
	}
//	public void addCharSequenceListener(CharSequenceListener csl) {/* no use listening on a short-lived wrapper */}
//	public void removeCharSequenceListener(CharSequenceListener csl) {/* no use listening on a short-lived wrapper */}
	
//	void tokenRelationChanged(ImToken imt, String oldWhitespace) {
//		String newWhitespace = imt.whitespace;
//		if (newWhitespace.equals(oldWhitespace))
//			return;
//		int offsetDelta = (newWhitespace.length() - oldWhitespace.length());
//		
//		//	adjust start offsets downstream from argument token
//		for (int i = (imt.index + 1); i < this.tokens.size(); i++)
//			((ImToken) this.tokens.get(i)).startOffset += offsetDelta;
//		
//		//	any listeners to notify?
//		if ((this.charListeners == null)/* && (this.tokenListeners == null)*/)
//			return;
//		
//		//	notify any char sequence listeners
//		CharSequenceEvent cse = new CharSequenceEvent(this, imt.getEndOffset(), imt.whitespace, oldWhitespace);
//		if (this.charListeners != null) {
//			for (int l = 0; l < this.charListeners.size(); l++)
//				((CharSequenceListener) this.charListeners.get(l)).charSequenceChanged(cse);
//		}
//		
//		//	TODOne_below notify any token sequence listeners, as tokens can merge and split
//		if (this.tokenListeners != null) {
//			TokenSequenceEvent tse = new TokenSequenceEvent(this, xmgt.index, null, null, cse);
//			for (int l = 0; l < this.tokenListeners.size(); l++)
//				((TokenSequenceListener) this.tokenListeners.get(l)).tokenSequenceChanged(tse);
//		}
//	}
	
	void wordStringChanged(ImToken imt) {
		
		//	compute shifts in both index and offset of tokens, as well as char and token sequence changes
		int offsetDelta;
		
		int charChangeStartOffset;
		String oldChars;
		String newChars;
		
		TokenSequence oldTokens;
		TokenSequence newTokens;
		
		//	rebuild affected token
		ImToken oldImt = imt;
		int oldImtLength = oldImt.imtLength();
		oldChars = oldImt.value; // need to remember this before we actually change anything
		
		//	create new token from words making up existing one (only way of safely adjusting offsets)
		ArrayList imWords = new ArrayList(oldImt.imWords);
		ImWord imw = ((ImWord) imWords.get(0));
		String imwStr = imw.getString();
		if ((this.configFlags & NORMALIZE_CHARACTERS) != 0)
			imwStr = normalizeString(imwStr);
		ImToken newImt = new ImToken(oldImt.startOffset, oldImt.index, imw, imwStr);
		this.imWordTokens.put(imw.getLocalID(), newImt);
		for (int w = 1; w < imWords.size(); w++) {
			imw = ((ImWord) imWords.get(w));
			imwStr = imw.getString();
			if ((this.configFlags & NORMALIZE_CHARACTERS) != 0)
				imwStr = normalizeString(imwStr);
			newImt.addWord(imw, imwStr);
			this.imWordTokens.put(imw.getLocalID(), newImt);
		}
		newImt.whitespace = oldImt.whitespace;
		this.tokens.set(oldImt.index, newImt);
		
		//	specify adjustment of subsequent tokens
		offsetDelta = (newImt.imtLength() - oldImtLength);
		
		//	create char sequence event
		charChangeStartOffset = oldImt.startOffset;
		newChars = newImt.value;
		
		//	create token sequence event (don't use subsequence, though, would also bind to document, etc.)
		oldTokens = this.tokenizer.tokenize(oldChars);
		newTokens = this.tokenizer.tokenize(newChars);
		
		//	adjust start offsets downstream from argument token
		if (offsetDelta != 0) {
			for (int i = (oldImt.index + 1); i < this.tokens.size(); i++)
				((ImToken) this.tokens.get(i)).startOffset += offsetDelta;
		}
		
		//	any listeners to notify?
		if ((this.charListeners == null) && (this.tokenListeners == null))
			return;
		
		//	notify any char sequence listeners
		CharSequenceEvent cse = new CharSequenceEvent(this, charChangeStartOffset, newChars, oldChars);
		if (this.charListeners != null) {
			for (int l = 0; l < this.charListeners.size(); l++)
				((CharSequenceListener) this.charListeners.get(l)).charSequenceChanged(cse);
		}
		
		//	notify any token sequence listeners, as tokens can merge and split
		if (this.tokenListeners != null) {
			TokenSequenceEvent tse = new TokenSequenceEvent(this, oldImt.index, newTokens, oldTokens, cse);
			for (int l = 0; l < this.tokenListeners.size(); l++)
				((TokenSequenceListener) this.tokenListeners.get(l)).tokenSequenceChanged(tse);
		}
	}
	
	void wordRelationChanged(ImWord leftImw, ImToken leftImt, char oldNextRelation, ImWord rightImw, ImToken rightImt) {
		char newNextRelation = leftImw.getNextRelation();
		
		//	compare old and new token arrangement
		boolean oldRelationSeparate;
		if ((this.configFlags & NORMALIZATION_LEVEL_STREAMS) == NORMALIZATION_LEVEL_RAW)
			oldRelationSeparate = true;
		else if (oldNextRelation == ImWord.NEXT_RELATION_CONTINUE)
			oldRelationSeparate = false;
		else if (oldNextRelation == ImWord.NEXT_RELATION_HYPHENATED)
			oldRelationSeparate = false;
		else oldRelationSeparate = true; // separate or paragraph break, or something weird
		boolean newRelationSeparate;
		if ((this.configFlags & NORMALIZATION_LEVEL_STREAMS) == NORMALIZATION_LEVEL_RAW)
			newRelationSeparate = true;
		else if (newNextRelation == ImWord.NEXT_RELATION_CONTINUE)
			newRelationSeparate = false;
		else if (newNextRelation == ImWord.NEXT_RELATION_HYPHENATED)
			newRelationSeparate = false;
		else newRelationSeparate = true; // separate or paragraph break, or something weird
		
		//	compute shifts in both index and offset of tokens, as well as char and token sequence changes
		int indexDelta;
		int offsetDelta;
		int adjustStartIndex;
		
		int charChangeStartOffset;
		String oldChars;
		String newChars;
		
		int tokenChangeStartIndex;
		TokenSequence oldTokens;
		TokenSequence newTokens;
		
		//	tokens separate and remain that way, only need to adjust space
		if (oldRelationSeparate && newRelationSeparate) {
			String oldWhitespace = leftImt.whitespace;
			oldChars = leftImt.whitespace; // need to remember this before we actually change anything
			oldTokens = null; // need to remember this before we actually change anything
			String newWhitespace = ((newNextRelation == ImWord.NEXT_RELATION_PARAGRAPH_END) ? "\r\n" : " ");
			if (newWhitespace.equals(oldWhitespace))
				return;
			leftImt.whitespace = newWhitespace;
			
			//	specify adjustment of subsequent tokens
			indexDelta = 0;
			offsetDelta = (newWhitespace.length() - oldWhitespace.length());
			adjustStartIndex = (leftImt.index + 1);
			
			//	create char sequence event
			charChangeStartOffset = leftImt.getEndOffset();
			newChars = leftImt.whitespace;
			
			//	create token sequence event (if with empty token changes, as we only changed some space)
			tokenChangeStartIndex = leftImt.index;
			oldTokens = null;
			newTokens = null;
		}
		
		//	tokens merger, transfer words from right token to left one, transfer whitespace, and remove right token
		else if (oldRelationSeparate) {
			int oldCombinedImtLength = (leftImt.imtLength() + rightImt.imtLength());
			oldChars = (leftImt.value + leftImt.whitespace + rightImt.value); // need to remember this before we actually change anything
			for (int w = 0; w < rightImt.imWords.size(); w++) {
				ImWord imw = ((ImWord) rightImt.imWords.get(w));
				String imwStr = imw.getString();
				if ((this.configFlags & NORMALIZE_CHARACTERS) != 0)
					imwStr = normalizeString(imwStr);
				leftImt.addWord(imw, imwStr);
				this.imWordTokens.put(imw.getLocalID(), leftImt);
			}
			leftImt.whitespace = rightImt.whitespace;
			this.tokens.remove(rightImt.index);
			
			//	specify adjustment of subsequent tokens
			indexDelta = -1;
			offsetDelta = (leftImt.imtLength() - oldCombinedImtLength);
			adjustStartIndex = (leftImt.index + 1);
			
			//	create char sequence event
			charChangeStartOffset = leftImt.startOffset;
			newChars = leftImt.value;
			
			//	create token sequence event (don't use subsequence, though, would also bind to document, etc.)
			tokenChangeStartIndex = leftImt.index;
			oldTokens = this.tokenizer.tokenize(oldChars);
			newTokens = this.tokenizer.tokenize(newChars);
		}
		
		//	tokens split, divide words up into new left and right tokens
		else if (newRelationSeparate && (leftImt == rightImt)) {
			ImToken oldImt = leftImt;
			int oldImtLength = oldImt.imtLength();
			oldChars = oldImt.value; // need to remember this before we actually change anything
			
			//	distribute underlying words to new tokens
			ArrayList oldImtWords = new ArrayList(oldImt.imWords);
			ArrayList newLeftImtWords = new ArrayList();
			ArrayList newRightImtWords = new ArrayList();
			ArrayList newImtWords = newLeftImtWords;
			for (int w = 0; w < oldImtWords.size(); w++) {
				ImWord imw = ((ImWord) oldImtWords.get(w));
				newImtWords.add(imw);
				if (imw == leftImw)
					newImtWords = newRightImtWords;
			}
			
			//	neither token should be empty ... be safe if so
			if (newLeftImtWords.isEmpty() || newRightImtWords.isEmpty()) {
				this.invalidate();
				return;
			}
			ImWord imw;
			String imwStr;
			
			//	create new left token
			imw = ((ImWord) newLeftImtWords.get(0));
			imwStr = imw.getString();
			if ((this.configFlags & NORMALIZE_CHARACTERS) != 0)
				imwStr = normalizeString(imwStr);
			ImToken newLeftImt = new ImToken(oldImt.startOffset, oldImt.index, imw, imwStr);
			this.imWordTokens.put(imw.getLocalID(), newLeftImt);
			for (int w = 1; w < newLeftImtWords.size(); w++) {
				imw = ((ImWord) newLeftImtWords.get(w));
				imwStr = imw.getString();
				if ((this.configFlags & NORMALIZE_CHARACTERS) != 0)
					imwStr = normalizeString(imwStr);
				newLeftImt.addWord(imw, imwStr);
				this.imWordTokens.put(imw.getLocalID(), newLeftImt);
			}
			newLeftImt.whitespace = ((newNextRelation == ImWord.NEXT_RELATION_PARAGRAPH_END) ? "\r\n" : " ");
			this.tokens.set(newLeftImt.index, newLeftImt);
			
			//	create new right token
			imw = ((ImWord) newRightImtWords.get(0));
			imwStr = imw.getString();
			if ((this.configFlags & NORMALIZE_CHARACTERS) != 0)
				imwStr = normalizeString(imwStr);
			ImToken newRightImt = new ImToken((oldImt.startOffset + newLeftImt.imtLength()), (oldImt.index + 1), imw, imwStr);
			this.imWordTokens.put(imw.getLocalID(), newRightImt);
			for (int w = 1; w < newRightImtWords.size(); w++) {
				imw = ((ImWord) newRightImtWords.get(w));
				imwStr = imw.getString();
				if ((this.configFlags & NORMALIZE_CHARACTERS) != 0)
					imwStr = normalizeString(imwStr);
				newRightImt.addWord(imw, imwStr);
				this.imWordTokens.put(imw.getLocalID(), newRightImt);
			}
			newRightImt.whitespace = oldImt.whitespace;
			this.tokens.add(newRightImt.index, newRightImt);
			
			//	specify adjustment of subsequent tokens
			indexDelta = 1;
			offsetDelta = ((newLeftImt.imtLength() + newRightImt.imtLength()) - oldImtLength);
			adjustStartIndex = (oldImt.index + 2); // only need to adjust first token after newly inserted ones
			
			//	create char sequence event
			charChangeStartOffset = oldImt.startOffset;
			newChars = (newLeftImt.value + newLeftImt.whitespace + newRightImt.value);
			
			//	create token sequence event (don't use subsequence, though, would also bind to document, etc.)
			tokenChangeStartIndex = oldImt.index;
			oldTokens = this.tokenizer.tokenize(oldChars);
			newTokens = this.tokenizer.tokenize(newChars);
		}
		
		//	something must be badly wrong (maybe even from previous adjustment), let's be safe
		else if (newRelationSeparate) {
			this.invalidate();
			return;
		}
		
		//	change between hyphenation and simple concatenation, adjust (rebuild) token value
		else if (leftImt == rightImt) {
			ImToken oldImt = leftImt;
			int oldImtLength = oldImt.imtLength();
			oldChars = oldImt.value; // need to remember this before we actually change anything
			
			//	create new token from words making up existing one (only way of safely adjusting offsets)
			ArrayList imWords = new ArrayList(oldImt.imWords);
			ImWord imw = ((ImWord) imWords.get(0));
			String imwStr = imw.getString();
			if ((this.configFlags & NORMALIZE_CHARACTERS) != 0)
				imwStr = normalizeString(imwStr);
			ImToken newImt = new ImToken(oldImt.startOffset, oldImt.index, imw, imwStr);
			this.imWordTokens.put(imw.getLocalID(), newImt);
			for (int w = 1; w < imWords.size(); w++) {
				imw = ((ImWord) imWords.get(w));
				imwStr = imw.getString();
				if ((this.configFlags & NORMALIZE_CHARACTERS) != 0)
					imwStr = normalizeString(imwStr);
				newImt.addWord(imw, imwStr);
				this.imWordTokens.put(imw.getLocalID(), newImt);
			}
			newImt.whitespace = oldImt.whitespace;
			this.tokens.set(oldImt.index, newImt);
			
			//	specify adjustment of subsequent tokens
			indexDelta = 0;
			offsetDelta = (newImt.imtLength() - oldImtLength);
			adjustStartIndex = (oldImt.index + 1);
			
			//	create char sequence event
			charChangeStartOffset = oldImt.startOffset;
			newChars = newImt.value;
			
			//	create token sequence event (don't use subsequence, though, would also bind to document, etc.)
			tokenChangeStartIndex = oldImt.index;
			oldTokens = this.tokenizer.tokenize(oldChars);
			newTokens = this.tokenizer.tokenize(newChars);
		}
		
		//	something must be badly wrong (maybe even from previous adjustment), let's be safe
		else {
			this.invalidate();
			return;
		}
		
		//	adjust start offsets downstream from argument token
		for (int i = adjustStartIndex; i < this.tokens.size(); i++) {
			ImToken imt = ((ImToken) this.tokens.get(i));
			imt.index += indexDelta;
			imt.startOffset += offsetDelta;
		}
		
		//	any listeners to notify?
		if ((this.charListeners == null) && (this.tokenListeners == null))
			return;
		
		//	notify any char sequence listeners
		CharSequenceEvent cse = new CharSequenceEvent(this, charChangeStartOffset, newChars, oldChars);
		if (this.charListeners != null) {
			for (int l = 0; l < this.charListeners.size(); l++)
				((CharSequenceListener) this.charListeners.get(l)).charSequenceChanged(cse);
		}
		
		//	notify any token sequence listeners, as tokens can merge and split
		if (this.tokenListeners != null) {
			TokenSequenceEvent tse = new TokenSequenceEvent(this, tokenChangeStartIndex, newTokens, oldTokens, cse);
			for (int l = 0; l < this.tokenListeners.size(); l++)
				((TokenSequenceListener) this.tokenListeners.get(l)).tokenSequenceChanged(tse);
		}
	}
	
	private ArrayList charListeners = null;
	
	/* (non-Javadoc)
	 * @see de.gamta.MutableCharSequence#addCharSequenceListener(de.gamta.CharSequenceListener)
	 */
	public void addCharSequenceListener(CharSequenceListener csl) {
		if (csl == null)
			return;
		if (this.charListeners == null)
			this.charListeners = new ArrayList(2);
		else if (!this.charListeners.contains(csl))
			this.charListeners.add(csl);
	}
	
	/* (non-Javadoc)
	 * @see de.gamta.MutableCharSequence#removeCharSequenceListener(de.gamta.CharSequenceListener)
	 */
	public void removeCharSequenceListener(CharSequenceListener csl) {
		if (this.charListeners != null)
			this.charListeners.remove(csl);
	}
	
	private ArrayList tokenListeners = null;
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.gamta.MutableTokenSequence#addTokenSequenceListener(de.uka.ipd.idaho.gamta.TokenSequenceListener)
	 */
	public void addTokenSequenceListener(TokenSequenceListener tsl) {
		if (tsl == null)
			return;
		if (this.tokenListeners == null)
			this.tokenListeners = new ArrayList(2);
		else if (!this.tokenListeners.contains(tsl))
			this.tokenListeners.add(tsl);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.gamta.MutableTokenSequence#removeTokenSequenceListener(de.uka.ipd.idaho.gamta.TokenSequenceListener)
	 */
	public void removeTokenSequenceListener(TokenSequenceListener tsl) {
		if (this.tokenListeners != null)
			this.tokenListeners.remove(tsl);
	}
	
	public Token tokenAt(int index) {
		this.checkValid();
		return this.imTokenAtIndex(index);
	}
	public Token firstToken() {
		this.checkValid();
		return this.imTokenAtIndex(0);
	}
	public Token lastToken() {
		this.checkValid();
		return this.imTokenAtIndex(this.size()-1);
	}
	public String valueAt(int index) {
		this.checkValid();
		return this.tokenAt(index).getValue();
	}
	public String firstValue() {
		this.checkValid();
		return this.firstToken().getValue();
	}
	public String lastValue() {
		this.checkValid();
		return this.lastToken().getValue();
	}
	public String getLeadingWhitespace() {
		this.checkValid();
		return "";
	}
	public String getWhitespaceAfter(int index) {
		return this.imTokenAtIndex(index).whitespace;
	}
	public int size() {
		this.checkValid();
		return this.tokens.size();
	}
	public TokenSequence getSubsequence(int start, int size) {
		return this.getMutableSubsequence(start, size);
	}
	public Tokenizer getTokenizer() {
		return this.tokenizer;
	}
	public CharSequence setLeadingWhitespace(CharSequence whitespace) throws IllegalArgumentException {
		if (this.tokens.isEmpty())
			return this.getLeadingWhitespace();
		ImToken imt = this.imTokenAtIndex(0);
		ImWord imw = ((ImWord) imt.imWords.get(0));
		return this.setNextWordRelationByWhitespace(null, imw.getPreviousWord(), whitespace);
//		return this.getLeadingWhitespace(); // we're not modifying any chars or tokens in this wrapper for now
	}
	public CharSequence setWhitespaceAfter(CharSequence whitespace, int index) throws IllegalArgumentException {
		ImToken imt = this.imTokenAtIndex(index);
		ImWord imw = ((ImWord) imt.imWords.get(imt.imWords.size() - 1));
		return this.setNextWordRelationByWhitespace(imt, imw, whitespace);
//		if (index < this.size())
//			return this.setChars(whitespace, imt.getEndOffset(), imt.whitespace.length());
//		//	we have to handle last token separately, as its end offset is out of bounds
//		else return (imt.whitespace = whitespace.toString());
	}
	private String setNextWordRelationByWhitespace(ImToken imt, ImWord imw, CharSequence whitespace) throws IllegalArgumentException {
		this.checkValid();
		if (imw == null)
			return this.getLeadingWhitespace();
		if (whitespace == null)
			whitespace = "";
		
		String ws = whitespace.toString();
		if (ws.trim().length() != 0)
			throw new IllegalArgumentException("Whitespace must not include non-space characters.");
		
		String oWs;
		if (imt == null)
			oWs = (((imw.getNextRelation() == ImWord.NEXT_RELATION_PARAGRAPH_END) || (imw.getNextRelation() == ImWord.NEXT_RELATION_SEPARATE)) ? " " : "");
		else oWs = imt.whitespace;
		if (ws.equals(oWs))
			return oWs;
		
		try {
			this.docListener.parentModifyingBaseTokens = true; // make sure not to loop back base document changes
		
			if (ws.length() == 0) {
				if (imw.getNextRelation() != ImWord.NEXT_RELATION_HYPHENATED)
					imw.setNextRelation(ImWord.NEXT_RELATION_CONTINUE);
				if (imt != null)
					imt.whitespace = "";
			}
			else {
				if (imw.getNextRelation() != ImWord.NEXT_RELATION_PARAGRAPH_END)
					imw.setNextRelation(ImWord.NEXT_RELATION_SEPARATE);
				if (imt != null)
					imt.whitespace = " ";
			}
			
			//	leading whitespace only modifies relation between first token and its predecessor, not leading whitespace proper
			if (imt != null) {
				
				//	adjust token offsets
				int offsetDelta = (ws.length() - oWs.length());
				for (int i = (imt.index + 1); i < this.tokens.size(); i++)
					((ImToken) this.tokens.get(i)).startOffset += offsetDelta;
				
				//	notify any char sequence listeners
				if (this.charListeners != null) {
					CharSequenceEvent cse = new CharSequenceEvent(this, imt.getEndOffset(), imt.whitespace, oWs);
					for (int l = 0; l < this.charListeners.size(); l++)
						((CharSequenceListener) this.charListeners.get(l)).charSequenceChanged(cse);
				}
			}
		}
		finally {
			this.docListener.parentModifyingBaseTokens = false; // re-enable loop-though of base document changes
		}
		
		return oWs;
	}
	
	public CharSequence setValueAt(CharSequence value, int index) throws IllegalArgumentException {
		ImToken imt = this.imTokenAtIndex(index);
		return this.setChars(value, imt.getStartOffset(), imt.length());
	}
	public TokenSequence removeTokensAt(int index, int size) {
		this.checkValid();
		
		//	store sub sequence for return value
		TokenSequence removedTokens = this.getSubsequence(index, size);
		
		//	simply set affected char sequence to empty string
		ImToken firstToken = this.imTokenAtIndex(index);
		ImToken lastToken = this.imTokenAtIndex(index + size - 1);
		this.setChars("", firstToken.getStartOffset(), (lastToken.getEndOffset() - firstToken.getStartOffset()));
		
		//	finally ...
		return removedTokens;
	}
	public CharSequence insertTokensAt(CharSequence tokens, int index) {
		return tokens; // we're not modifying any chars or tokens in this wrapper for now
	}
	public CharSequence addTokens(CharSequence tokens) {
		return tokens; // we're not modifying any chars or tokens in this wrapper for now
	}
	public void clear() {/* we're not modifying any chars or tokens in this wrapper for now */}
	public MutableTokenSequence getMutableSubsequence(int start, int size) {
		this.checkValid();
		ImToken sImt = this.imTokenAtIndex(start);
		int deltaOffset = sImt.getStartOffset();
		int deltaIndex = sImt.index;
//		ImTokenSequence imts = new ImTokenSequence(this.tokenizer);
		ImTokenSequence imts = new ImTokenSequence(this.tokenizer, true);
		for (int t = start; t < (start + size); t++)
			imts.tokens.add(this.imTokenAtIndex(t).deltaClone(deltaOffset, deltaIndex));
		return imts;
	}
//	public void addTokenSequenceListener(TokenSequenceListener tsl) {/* no use listening on a short-lived wrapper */}
//	public void removeTokenSequenceListener(TokenSequenceListener tsl) {/* no use listening on a short-lived wrapper */}
	
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
//		ImTokenSequence its = new ImTokenSequence(((Tokenizer) doc.getAttribute(ImDocument.TOKENIZER_ATTRIBUTE, Gamta.INNER_PUNCTUATION_TOKENIZER)), doc.getTextStreamHeads(), NORMALIZATION_LEVEL_WORDS);
		ImTokenSequence its = new ImTokenSequence(((Tokenizer) doc.getAttribute(ImDocument.TOKENIZER_ATTRIBUTE, Gamta.getDefaultTokenizer())), doc.getTextStreamHeads(), NORMALIZATION_LEVEL_WORDS);
		for (int t = 0; t < its.size(); t++) {
			String value = its.valueAt(t);
			System.out.println(t + ": " + value);
			value = value.replaceAll("[aou]", "");
			value = value.replaceAll("[i]", "ii");
			value = value.replaceAll("[e]", "ee");
			if (value.length() == 0)
				value = "X";
			its.setValueAt((value.substring(0, 1).toUpperCase() + value.substring(1).toLowerCase()), t);
		}
		
		//	remove page numbers
		for (int t = 0; t < its.size(); t++) {
			if (its.valueAt(t).matches("9[0-9]+"))
				its.removeTokensAt(t--, 1);
			else if (its.valueAt(t).matches("[\\_]+"))
				its.removeTokensAt(t--, 1);
		}
		
		//	write tokens to see what remains
		for (int t = 0; t < its.size(); t++)
			System.out.println(its.valueAt(t));
	}
	private static int aimAtPage = -1;
}