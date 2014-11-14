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
package de.uka.ipd.idaho.im.gamta.test;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.swing.UIManager;

import de.uka.ipd.idaho.gamta.Annotation;
import de.uka.ipd.idaho.gamta.AnnotationUtils;
import de.uka.ipd.idaho.gamta.Attributed;
import de.uka.ipd.idaho.gamta.DocumentRoot;
import de.uka.ipd.idaho.gamta.Gamta;
import de.uka.ipd.idaho.gamta.MutableAnnotation;
import de.uka.ipd.idaho.gamta.QueriableAnnotation;
import de.uka.ipd.idaho.gamta.Token;
import de.uka.ipd.idaho.gamta.TokenSequence;
import de.uka.ipd.idaho.gamta.TokenSequenceUtils;
import de.uka.ipd.idaho.gamta.Tokenizer;
import de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed;
import de.uka.ipd.idaho.gamta.util.AnalyzerDataProvider;
import de.uka.ipd.idaho.gamta.util.AnalyzerDataProviderFileBased;
import de.uka.ipd.idaho.gamta.util.imaging.BoundingBox;
import de.uka.ipd.idaho.gamta.util.imaging.ImagingConstants;
import de.uka.ipd.idaho.gamta.util.imaging.PageImage;
import de.uka.ipd.idaho.gamta.util.imaging.PageImageInputStream;
import de.uka.ipd.idaho.gamta.util.imaging.PageImageStore;
import de.uka.ipd.idaho.gamta.util.imaging.PageImageStore.AbstractPageImageStore;
import de.uka.ipd.idaho.im.ImAnnotation;
import de.uka.ipd.idaho.im.ImDocument;
import de.uka.ipd.idaho.im.ImPage;
import de.uka.ipd.idaho.im.ImRegion;
import de.uka.ipd.idaho.im.ImWord;
import de.uka.ipd.idaho.im.util.ImfIO;
import de.uka.ipd.idaho.stringUtils.StringIndex;

/**
 * Test engine for wrapping Image Markup data model into GAMTA.
 * 
 * @author sautter
 */
public class ImGamtaTest {
	public static int aimAtPage = -1;
	public static void main(String[] args) throws Exception {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e) {}
		
		final File imfDataPath = new File("E:/Testdaten/PdfExtract/");
		final AnalyzerDataProvider dataProvider = new AnalyzerDataProviderFileBased(imfDataPath);
		
		//	register page image source
		PageImageStore pis = new AbstractPageImageStore() {
			public boolean isPageImageAvailable(String name) {
				if (!name.endsWith(IMAGE_FORMAT))
					name += ("." + IMAGE_FORMAT);
				return dataProvider.isDataAvailable(name);
			}
			public PageImageInputStream getPageImageAsStream(String name) throws IOException {
				if (!name.endsWith(IMAGE_FORMAT))
					name += ("." + IMAGE_FORMAT);
				if (!dataProvider.isDataAvailable(name))
					return null;
				return new PageImageInputStream(dataProvider.getInputStream(name), this);
			}
			public boolean storePageImage(String name, PageImage pageImage) throws IOException {
				if (!name.endsWith(IMAGE_FORMAT))
					name += ("." + IMAGE_FORMAT);
				try {
					OutputStream imageOut = dataProvider.getOutputStream(name);
					pageImage.write(imageOut);
					imageOut.close();
					return true;
				}
				catch (IOException ioe) {
					ioe.printStackTrace(System.out);
					return false;
				}
			}
			public int getPriority() {
				return 0; // we're a general page image store, yield to specific ones
			}
		};
		PageImage.addPageImageSource(pis);
		
		String docName;
		
		//	only use documents we have an IMF version of right now
		//	TODO produce more such document with PdfExtractorTest
//		docName = "19970730111_ftp.pdf.imf"; // scanned, with born-digital looking text embedded, comes out better when loaded from image
		docName = "MEZ_909-919.pdf.imf"; // born-digital, renders perfectly fine
		
		//	load document
		InputStream docIn = new BufferedInputStream(new FileInputStream(new File(imfDataPath, docName)));
		ImDocument doc = ImfIO.loadDocument(docIn);
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
		
		//	test what wrapper should exhibit
		ImWord[] tshs = ((aimAtPage == -1) ? doc.getTextStreamHeads() : pages[aimAtPage].getTextStreamHeads());
		for (int h = 0; h < tshs.length; h++) {
			System.out.println("=== text stream " + h + " ===");
			for (ImWord imw = tshs[h]; imw != null; imw = imw.getNextWord()) {
				if ((aimAtPage != -1) && (imw.getPage().pageId != aimAtPage))
					break;
				System.out.print(imw.getString());
				if (imw.getNextRelation() == ImWord.NEXT_RELATION_PARAGRAPH_END)
					System.out.println();
				else {
					ImWord nImw = imw.getNextWord();
					if ((nImw != null) && (nImw.getPage().pageId == imw.getPage().pageId) && Gamta.insertSpace(imw.getString(), nImw.getString()))
						System.out.print(" ");
				}
			}
			System.out.println();
		}
		
		//	wrap document
//		ImTokenSequence its = new ImTokenSequence((Tokenizer) doc.getAttribute(ImDocument.TOKENIZER_ATTRIBUTE, Gamta.INNER_PUNCTUATION_TOKENIZER));
		ImQueriableAnnotation iqa = new ImQueriableAnnotation(doc, NORMALIZATION_LEVEL_STREAMS);
//		ImQueriableAnnotation iqa = new ImQueriableAnnotation(doc.getPage(1), NORMALIZATION_LEVEL_WORDS);
		
		//	write XML for a first test
		AnnotationUtils.writeXML(iqa, new OutputStreamWriter(System.out));
//		
//		//	write individual pages as a test for views
//		QueriableAnnotation[] pageAnnots = iqa.getAnnotations(LiteratureConstants.PAGE_TYPE);
//		for (int p = 0; p < pageAnnots.length; p++)
//			AnnotationUtils.writeXML(pageAnnots[p], new OutputStreamWriter(System.out));
	}
	
	/** normalization level at which words are ordered strictly by layout order */
	static final int NORMALIZATION_LEVEL_RAW = 0;
	
	/** normalization level at which words are ordered by layout order, but hyphenated and continued words are kept together */
	static final int NORMALIZATION_LEVEL_WORDS = 1;
	
	/** normalization level at which words are ordered by reading order, but paragraphs are kept together */
	static final int NORMALIZATION_LEVEL_PARAGRAPHS = 2;
	
	/** normalization level at which text whole streams are kept together */
	static final int NORMALIZATION_LEVEL_STREAMS = 4;
	
	static class ImQueriableAnnotation extends ImTokenSequence implements QueriableAnnotation, ImagingConstants {
		
		class ImAnnotationBase {
			private ImAnnotation aData;
			private ImRegion rData;
			private ImWord[] rDataWords;
			private BoundingBox boundingBox = null;
			ImAnnotationBase(ImAnnotation aData) {
				this.aData = aData;
			}
			ImAnnotationBase(ImRegion rData, ImWord[] rDataWords) {
				this.rData = rData;
				this.rDataWords = rDataWords;
				Arrays.sort(this.rDataWords, new Comparator() {
					public int compare(Object obj1, Object obj2) {
						return (((ImWord) obj1).getTextStreamPos() - ((ImWord) obj2).getTextStreamPos());
					}
				});
			}
			Attributed getAttributed() {
				return ((this.aData == null) ? this.rData : this.aData);
			}
			ImWord firstWord() {
				if (this.aData == null)
					return this.rDataWords[0];
				else return this.aData.getFirstWord();
			}
			ImWord lastWord() {
				if (this.aData == null)
					return this.rDataWords[this.rDataWords.length-1];
				else return this.aData.getLastWord();
			}
			String getType() {
				return ((this.aData == null) ? this.rData.getType() : this.aData.getType());
			}
			String setType(String type) {
				String oldType = this.getType();
				if (this.aData == null)
					this.rData.setType(type);
				else this.aData.setType(type);
				if (!oldType.endsWith(type))
					this.getId = null;
				return oldType;
			}
			BoundingBox getBoundingBox() {
				if (this.boundingBox == NULL_BOUNDING_BOX)
					return null;
				if (this.boundingBox == null) {
					if (this.rData != null)
						this.boundingBox = this.rData.bounds;
					else {
						ArrayList imWords = new ArrayList();
						for (ImWord imw = this.aData.getFirstWord(); imw != null; imw = imw.getNextWord()) {
							imWords.add(imw);
							if (imw == this.aData.getLastWord())
								break;
						}
						BoundingBox[] imWordBounds = new BoundingBox[imWords.size()];
						for (int w = 0; w < imWords.size(); w++) {
							imWordBounds[w] = ((ImWord) imWords.get(w)).bounds;
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
				return ImQueriableAnnotation.this.valueAt(this.getStartIndex() + index);
			}
			String getWhitespaceAfter(int index) {
				return ImQueriableAnnotation.this.getWhitespaceAfter(this.getStartIndex() + index);
			}
			char charAt(int index) {
				return ImQueriableAnnotation.this.charAt(this.getStartOffset() + index);
			}
			CharSequence subSequence(int start, int end) {
				return ImQueriableAnnotation.this.subSequence((this.getStartOffset() + start), (this.getStartOffset() + end));
			}
			String getId() {
				if (this.getId == null) {
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
					this.getId = hexXor((""
							// 1 byte of type hash
							+ asHex(this.getType().hashCode(), 1)
							// 2 + 1 bytes of page IDs
							+ asHex(fImw.pageId, 2) + asHex(lImw.pageId, 1)
							// 2 * 6 bytes of bounding boxes (two bytes left & top, one byte right and bottom)
							+ asHex(fImw.bounds.left, 2) + asHex(fImw.bounds.right, 1)
							+ asHex(fImw.bounds.top, 2) + asHex(fImw.bounds.bottom, 1)
							+ asHex(lImw.bounds.left, 2) + asHex(lImw.bounds.right, 1)
							+ asHex(lImw.bounds.top, 2) + asHex(lImw.bounds.bottom, 1)
						), docId);
					//	THIS DOES NOT ALLOW DUPLICATE ANNOTATIONS !!!
					//	BUT THEN, IS THAT A BAD THING?
				}
				return this.getId;
			}
			private String getId = null;
		}
		
		class ImAnnotationView implements QueriableAnnotation {
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
				return doGetAnnotations(this.base);
			}
			public QueriableAnnotation[] getAnnotations(String type) {
				return doGetAnnotations(type, this.base);
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
				return ((String[]) ans.toArray(new String[ans.size()]));
			}
			public void setAttribute(String name) {
				this.base.getAttributed().setAttribute(name);
			}
			public Object setAttribute(String name, Object value) {
				return this.base.getAttributed().setAttribute(name, value);
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
				return (this.base.getEndIndex() - this.base.getStartIndex());
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
				return ImQueriableAnnotation.this.getSubsequence((this.base.getStartIndex() + start), size);
			}
			public Tokenizer getTokenizer() {
				return ImQueriableAnnotation.this.getTokenizer();
			}
			
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
				return ImQueriableAnnotation.this.getTokenizer();
			}
		}
		
		private ImDocument doc;
		
		private ArrayList annotations = new ArrayList();
		private TreeMap annotationsByType = new TreeMap();
		
		//	constructor for wrapping whole document
		ImQueriableAnnotation(ImDocument doc, int normalizationLevel) {
			this(doc, doc.getTextStreamHeads(), normalizationLevel);
		}
		
		//	constructor for wrapping selected text streams
		ImQueriableAnnotation(ImDocument doc, ImWord[] textStreamHeads, int normalizationLevel) {
			super(((Tokenizer) doc.getAttribute(ImDocument.TOKENIZER_ATTRIBUTE, Gamta.INNER_PUNCTUATION_TOKENIZER)), textStreamHeads, normalizationLevel);
			this.doc = doc;
			
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
			
			//	add annotation overlay for regions, including pages
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
		
		//	constructor for wrapping an individual annotation
		ImQueriableAnnotation(ImAnnotation annotation) {
			this(annotation.getDocument(), annotation.getFirstWord(), annotation.getLastWord());
		}
		
		//	constructor for wrapping custom spans of words
		ImQueriableAnnotation(ImDocument doc, ImWord firstWord, ImWord lastWord) {
			super(((Tokenizer) doc.getAttribute(ImDocument.TOKENIZER_ATTRIBUTE, Gamta.INNER_PUNCTUATION_TOKENIZER)), firstWord, lastWord);
			this.doc = doc;
			
			//	add annotation overlay for annotations
			ImAnnotation[] annotations = this.doc.getAnnotations();
			for (int a = 0; a < annotations.length; a++) {
				
				//	check text stream ID
				if (!firstWord.getTextStreamId().equals(annotations[a].getFirstWord().getTextStreamId()))
					continue;
				
				//	check if first word in range
				ImWord aFirstWord = annotations[a].getFirstWord();
				if ((aFirstWord.pageId < firstWord.pageId) || ((aFirstWord.pageId == firstWord.pageId) && (aFirstWord.getTextStreamPos() < firstWord.getTextStreamPos())))
					continue;
				if ((lastWord.pageId < aFirstWord.pageId) || ((lastWord.pageId == aFirstWord.pageId) && (lastWord.getTextStreamPos() < aFirstWord.getTextStreamPos())))
					continue;
				
				//	check if last word in range
				ImWord aLastWord = annotations[a].getLastWord();
				if ((aLastWord.pageId < firstWord.pageId) || ((aLastWord.pageId == firstWord.pageId) && (aLastWord.getTextStreamPos() < firstWord.getTextStreamPos())))
					continue;
				if ((lastWord.pageId < aLastWord.pageId) || ((lastWord.pageId == aLastWord.pageId) && (lastWord.getTextStreamPos() < aLastWord.getTextStreamPos())))
					continue;
				
				//	finally, we can include this one
				this.addAnnotation(annotations[a]);
			}
			
			//	add annotation overlay for regions, including pages
			ImPage[] pages = this.doc.getPages();
			for (int p = 0; p < pages.length; p++) {
				ImWord[] pageWords = pages[p].getWords();
				ArrayList filteredPageWords = new ArrayList();
				for (int w = 0; w < pageWords.length; w++) {
					
					//	check text stream ID
					if (!firstWord.getTextStreamId().equals(pageWords[w].getTextStreamId()))
						continue;
					
					//	check if word in range
					if ((pageWords[w].pageId < firstWord.pageId) || ((pageWords[w].pageId == firstWord.pageId) && (pageWords[w].getTextStreamPos() < firstWord.getTextStreamPos())))
						continue;
					if ((lastWord.pageId < pageWords[w].pageId) || ((lastWord.pageId == pageWords[w].pageId) && (lastWord.getTextStreamPos() < pageWords[w].getTextStreamPos())))
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
		
		//	constructor for wrapping regions and pages
		ImQueriableAnnotation(ImRegion region, int normalizationLevel) {
			super(((Tokenizer) region.getDocument().getAttribute(ImDocument.TOKENIZER_ATTRIBUTE, Gamta.INNER_PUNCTUATION_TOKENIZER)));
			this.doc = region.getDocument();
			
			//	add words
			ImWord[] regionWords = region.getWords();
			HashSet textStreamIdSet = new LinkedHashSet();
			for (int w = 0; w < regionWords.length; w++)
				textStreamIdSet.add(regionWords[w].getTextStreamId());
			String[] textStreamIDs = ((String[]) textStreamIdSet.toArray(new String[textStreamIdSet.size()]));
			ImWord[] textStreamHeads = new ImWord[textStreamIDs.length];
			for (int s = 0; s < textStreamIDs.length; s++) {
				for (int w = 0; w < regionWords.length; w++) {
					if (textStreamIDs[s].equals(regionWords[w].getTextStreamId()) && ((textStreamHeads[s] == null) || (regionWords[w].getTextStreamPos() < textStreamHeads[s].getTextStreamPos())))
						textStreamHeads[s] = regionWords[w];
				}
			}
			this.addTextStreams(textStreamHeads, normalizationLevel, new HashSet(Arrays.asList(regionWords)));
			
			//	add annotation overlay for annotations
			ImAnnotation[] annotations = this.doc.getAnnotations();
			for (int a = 0; a < annotations.length; a++) {
				
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
			
			//	annotate regions
			ImRegion[] regions = region.getPage().getRegions();
			for (int r = 0; r < regions.length; r++) {
				
				//	we already have this one
				if (regions[r] == region)
					continue;
				
				//	annotate region if within range
				if (region.bounds.includes(regions[r].bounds, false))
					this.addAnnotation(regions[r]);
			}
		}
		
		void addAnnotation(ImAnnotation annot) {
			this.addAnnotation(new ImAnnotationBase(annot));
		}
		void addAnnotation(ImRegion region) {
			this.addAnnotation(region, region.getWords());
		}
		private void addAnnotation(ImRegion region, ImWord[] regionWords) {
			if (regionWords.length == 0)
				return;
			this.addAnnotation(new ImAnnotationBase(region, regionWords));
		}
		private void addAnnotation(ImAnnotationBase base) {
			this.insertAnnotation(this.annotations, base);
			ArrayList typeAnnotations = ((ArrayList) this.annotationsByType.get(base.getType()));
			if (typeAnnotations == null) {
				typeAnnotations = new ArrayList();
				this.annotationsByType.put(base.getType(), typeAnnotations);
			}
			this.insertAnnotation(typeAnnotations, base);
		}
		private void insertAnnotation(ArrayList annotationList, ImAnnotationBase base) {
			if (annotationList.isEmpty()) {
				annotationList.add(base);
				return;
			}
			int index = (annotationList.size() / 2);
			for (int step = (annotationList.size() / 4); 1 < step; step /= 2) {
				int c = annotationBaseOrder.compare(base, annotationList.get(index));
				if (c == 0)
					break;
				else if (c < 0)
					index -= step;
				else index += step;
			}
			while ((index != 0) && (annotationBaseOrder.compare(base, annotationList.get(index)) < 0))
				index--;
			while ((index < annotationList.size()) && (0 < annotationBaseOrder.compare(base, annotationList.get(index))))
				index++;
			annotationList.add(index, base);
		}
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
			return this.doGetAnnotations(null);
		}
		public QueriableAnnotation[] getAnnotations(String type) {
			return this.doGetAnnotations(type, null);
		}
		QueriableAnnotation[] doGetAnnotations(ImAnnotationBase source) {
			return this.doGetAnnotations(this.annotations, source);
		}
		QueriableAnnotation[] doGetAnnotations(String type, ImAnnotationBase source) {
			return this.doGetAnnotations((ArrayList) this.annotationsByType.get(type), source);
		}
		private QueriableAnnotation[] doGetAnnotations(ArrayList annotationList, ImAnnotationBase source) {
			if ((annotationList == null) || annotationList.isEmpty())
				return new QueriableAnnotation[0];
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
				QueriableAnnotation[] annotations = new QueriableAnnotation[sourceAnnotationList.size()];
				for (int a = 0; a < sourceAnnotationList.size(); a++)
					annotations[a] = new ImAnnotationView(((ImAnnotationBase) sourceAnnotationList.get(a)), source);
				return annotations;
			}
		}
		public String[] getAnnotationTypes() {
			return ((String[]) this.annotationsByType.keySet().toArray(new String[this.annotationsByType.size()]));
		}
		public String getAnnotationNestingOrder() {
			return null;
		}
		
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
			ans.add(DOCUMENT_ID_ATTRIBUTE);
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
		
		public int compareTo(Object obj) {
			if (obj instanceof Annotation)
				return AnnotationUtils.compare(this, ((Annotation) obj));
			else return -1;
		}
	}
	
	static class ImTokenSequence implements TokenSequence, ImagingConstants {
		
		class ImToken extends AbstractAttributed implements Token {
			int startOffset;
			int index;
			String value = null;
			String whitespace = "";
			ArrayList imWords = new ArrayList(1);
			ArrayList imWordAt;
			BoundingBox boundingBox = null;
			ImToken(int startOffset, int index, ImWord imw) {
				this.startOffset = startOffset;
				this.index = index;
				this.addWord(imw);
			}
			private ImToken(int startOffset, int index) {
				this.startOffset = startOffset;
				this.index = index;
			}
			void addWord(ImWord imw) {
				String imwValue = imw.getString();
				if ((imwValue == null) || (imwValue.length() == 0))
					return;
				if (this.value == null) {
					this.value = imwValue;
					this.imWordAt = new ArrayList(imwValue.length());
				}
				else {
					ImWord pImw = ((ImWord) this.imWords.get(this.imWords.size()-1));
					if (pImw.getNextRelation() == ImWord.NEXT_RELATION_HYPHENATED) {
						this.value = this.value.substring(0, (this.value.length() - "-".length()));
						this.imWordAt.remove(this.value.length());
					}
					this.value += imwValue;
				}
				this.imWords.add(imw);
				for (int c = 0; c < imwValue.length(); c++)
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
				return this.startOffset;
			}
			int imtEndOffset() {
				return (this.startOffset + this.imtLength());
			}
			public int getEndOffset() {
				return (this.startOffset + this.length());
			}
			int imtLength() {
				return (this.value.length() + this.whitespace.length());
			}
			
			public int length() {
				return this.value.length();
			}
			public char charAt(int index) {
				return ((index < this.value.length()) ? this.value.charAt(index) : this.whitespace.charAt(index - this.value.length()));
			}
			public CharSequence subSequence(int start, int end) {
				if ((start < this.value.length()) && (end <= this.value.length()))
					return this.value.substring(start, end);
				else if (start < this.value.length())
					return (this.value.substring(start) + this.whitespace.substring(0, (end - this.value.length())));
				else return this.whitespace.substring((start - this.value.length()), (end - this.value.length()));
			}
			
			public String getValue() {
				return this.value;
			}
			public Tokenizer getTokenizer() {
				return tokenizer;
			}
			public boolean hasAttribute(String name) {
				if (Token.PARAGRAPH_END_ATTRIBUTE.equals(name) && (((ImWord) this.imWords.get(this.imWords.size() - 1)).getNextRelation() == ImWord.NEXT_RELATION_PARAGRAPH_END))
					return true;
				else if (PAGE_ID_ATTRIBUTE.equals(name))
					return true;
				else if (LAST_PAGE_ID_ATTRIBUTE.equals(name))
					return true;
				else if (BOUNDING_BOX_ATTRIBUTE.equals(name))
					return (this.getBoundingBox() != null);
				else if (PAGE_NUMBER_ATTRIBUTE.equals(name))
					return (super.hasAttribute(name) || ((ImWord) this.imWords.get(0)).getPage().hasAttribute(PAGE_NUMBER_ATTRIBUTE));
				else if (LAST_PAGE_NUMBER_ATTRIBUTE.equals(name))
					return (super.hasAttribute(name) || ((ImWord) this.imWords.get(this.imWords.size()-1)).getPage().hasAttribute(PAGE_NUMBER_ATTRIBUTE));
				else if (ImWord.TEXT_STREAM_TYPE_ATTRIBUTE.equals(name))
					return true;
				else return super.hasAttribute(name);
			}
			public Object getAttribute(String name, Object def) {
				if (Token.PARAGRAPH_END_ATTRIBUTE.equals(name) && (((ImWord) this.imWords.get(this.imWords.size() - 1)).getNextRelation() == ImWord.NEXT_RELATION_PARAGRAPH_END))
					return "true";
				else if (PAGE_ID_ATTRIBUTE.equals(name))
					return ("" + ((ImWord) this.imWords.get(0)).getPage().pageId);
				else if (LAST_PAGE_ID_ATTRIBUTE.equals(name))
					return ("" + ((ImWord) this.imWords.get(this.imWords.size()-1)).getPage().pageId);
				else if (BOUNDING_BOX_ATTRIBUTE.equals(name)) {
					BoundingBox bb  = this.getBoundingBox();
					return ((bb == null) ? def : bb.toString());
				}
				else if (PAGE_NUMBER_ATTRIBUTE.equals(name) && !super.hasAttribute(name) && ((ImWord) this.imWords.get(0)).getPage().hasAttribute(PAGE_NUMBER_ATTRIBUTE))
					return ("" + ((ImWord) this.imWords.get(0)).getPage().getAttribute(PAGE_NUMBER_ATTRIBUTE));
				else if (LAST_PAGE_NUMBER_ATTRIBUTE.equals(name) && !super.hasAttribute(name) && ((ImWord) this.imWords.get(this.imWords.size()-1)).getPage().hasAttribute(PAGE_NUMBER_ATTRIBUTE))
					return ("" + ((ImWord) this.imWords.get(this.imWords.size()-1)).getPage().getAttribute(PAGE_NUMBER_ATTRIBUTE));
				else if (ImWord.TEXT_STREAM_TYPE_ATTRIBUTE.equals(name))
					return ((ImWord) this.imWords.get(0)).getTextStreamType();
				else return super.getAttribute(name, def);
			}
			public Object getAttribute(String name) {
				if (PARAGRAPH_END_ATTRIBUTE.equals(name) && (((ImWord) this.imWords.get(this.imWords.size() - 1)).getNextRelation() == ImWord.NEXT_RELATION_PARAGRAPH_END))
					return "true";
				else if (PAGE_ID_ATTRIBUTE.equals(name))
					return ("" + ((ImWord) this.imWords.get(0)).getPage().pageId);
				else if (LAST_PAGE_ID_ATTRIBUTE.equals(name))
					return ("" + ((ImWord) this.imWords.get(this.imWords.size()-1)).getPage().pageId);
				else if (BOUNDING_BOX_ATTRIBUTE.equals(name)) {
					BoundingBox bb  = this.getBoundingBox();
					return ((bb == null) ? null : bb.toString());
				}
				else if (PAGE_NUMBER_ATTRIBUTE.equals(name) && !super.hasAttribute(name) && ((ImWord) this.imWords.get(0)).getPage().hasAttribute(PAGE_NUMBER_ATTRIBUTE))
					return ("" + ((ImWord) this.imWords.get(0)).getPage().getAttribute(PAGE_NUMBER_ATTRIBUTE));
				else if (LAST_PAGE_NUMBER_ATTRIBUTE.equals(name) && !super.hasAttribute(name) && ((ImWord) this.imWords.get(this.imWords.size()-1)).getPage().hasAttribute(PAGE_NUMBER_ATTRIBUTE))
					return ("" + ((ImWord) this.imWords.get(this.imWords.size()-1)).getPage().getAttribute(PAGE_NUMBER_ATTRIBUTE));
				else if (ImWord.TEXT_STREAM_TYPE_ATTRIBUTE.equals(name))
					return ((ImWord) this.imWords.get(0)).getTextStreamType();
				else return super.getAttribute(name);
			}
			public String[] getAttributeNames() {
				TreeSet ans = new TreeSet(Arrays.asList(super.getAttributeNames()));
				if (((ImWord) this.imWords.get(this.imWords.size() - 1)).getNextRelation() == ImWord.NEXT_RELATION_PARAGRAPH_END)
					ans.add(PARAGRAPH_END_ATTRIBUTE);
				if (this.getBoundingBox() != null)
					ans.add(BOUNDING_BOX_ATTRIBUTE);
				ans.add(ImWord.TEXT_STREAM_TYPE_ATTRIBUTE);
				return ((String[]) ans.toArray(new String[ans.size()]));
			}
			ImToken deltaClone(int deltaOffset, int deltaIndex) {
				ImToken imt = new ImToken((this.startOffset - deltaOffset), (this.index - deltaIndex));
				imt.value = this.value;
				imt.whitespace = this.whitespace;
				imt.imWords = new ArrayList(this.imWords);
				imt.imWordAt = new ArrayList(this.imWordAt);
				return imt;
			}
		}
		
		private Tokenizer tokenizer;
		private ArrayList tokens = new ArrayList();
		private HashMap imWordTokens = new HashMap();
		
		//	constructor for adding words later on
		ImTokenSequence(Tokenizer tokenizer) {
			this(tokenizer, new ImWord[0], NORMALIZATION_LEVEL_STREAMS);
		}
		
		//	constructor for wrapping whole documents or selected text streams
		ImTokenSequence(Tokenizer tokenizer, ImWord[] textStreamHeads, int normalizationLevel) {
			this.tokenizer = tokenizer;
			this.addTextStreams(textStreamHeads, normalizationLevel, null);
		}
		
		void addTextStreams(ImWord[] textStreamHeads, int normalizationLevel, Set wordFilter) {
			StringIndex tsIdCounts;
			if (wordFilter == null)
				tsIdCounts = null;
			else {
				tsIdCounts = new StringIndex(true);
				for (Iterator wit = wordFilter.iterator(); wit.hasNext();)
					tsIdCounts.add(((ImWord) wit.next()).getTextStreamId());
			}
			ArrayList tshList = new ArrayList(Arrays.asList(textStreamHeads));
			Collections.sort(tshList, layoutWordOrder);
			while (tshList.size() != 0) {
				
				//	get next stream head to process
				ImWord tsh = ((ImWord) tshList.get(0));
				
				//	add words
				ImWord imw = tsh;
				while (imw != null) {
					
					//	are we supposed to use this word?
					if ((wordFilter != null) && !wordFilter.remove(imw)) {
						imw = imw.getNextWord();
						break;
					}
					
					//	add word
					this.addImWord(imw);
					
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
					for (int t = 0; (t+1) < tshList.size(); t++) {
						int c = layoutWordOrder.compare(tshList.get(t), tshList.get(t+1));
						if (c <= 0)
							break;
						tshList.set((t+1), tshList.set(t, tshList.get(t+1)));
					}
				}
			}
		}
		
		private static final Comparator layoutWordOrder = new Comparator() {
			public int compare(Object obj1, Object obj2) {
				ImWord imw1 = ((ImWord) obj1);
				ImWord imw2 = ((ImWord) obj2);
				
				//	different pages
				if (imw1.pageId != imw2.pageId)
					return (imw1.pageId - imw2.pageId);
				
				//	same text stream, use ordering position
				if (imw1.getTextStreamId().equals(imw2.getTextStreamId()))
					return (imw1.getTextStreamPos() - imw2.getTextStreamPos());
				
				//	use bounding boxes
				if (imw1.bounds.bottom <= imw2.bounds.top)
					return -1;
				if (imw2.bounds.bottom <= imw1.bounds.top)
					return 1;
				if (imw1.bounds.right <= imw2.bounds.left)
					return -1;
				if (imw2.bounds.right <= imw1.bounds.left)
					return 1;
				
				//	handle overlapping bounding boxes
				if (imw1.bounds.top != imw2.bounds.top)
					return (imw1.bounds.top - imw2.bounds.top);
				if (imw1.bounds.left != imw2.bounds.left)
					return (imw1.bounds.left - imw2.bounds.left);
				if (imw1.bounds.bottom != imw2.bounds.bottom)
					return (imw2.bounds.bottom - imw1.bounds.bottom);
				if (imw1.bounds.right != imw2.bounds.right)
					return (imw2.bounds.right - imw1.bounds.right);
				
				//	little we can do about these two ...
				return 0;
			}
		};
		
		//	constructor for wrapping individual annotations (no need for a normalization level here, as we're dealing with a single text stream only)
		ImTokenSequence(Tokenizer tokenizer, ImWord firstWord, ImWord lastWord) {
			this.tokenizer = tokenizer;
			for (ImWord imw = firstWord; imw != null; imw = imw.getNextWord()) {
				this.addImWord(imw);
				if (imw == lastWord)
					break;
			}
		}
		
		void addImWord(ImWord imw) {
			if (this.tokens.isEmpty()) {
				ImToken imt = new ImToken(0, 0, imw);
				this.tokens.add(imt);
				this.imWordTokens.put(imw.getLocalID(), imt);
				return;
			}
			ImToken pImt = this.imTokenAtIndex(this.tokens.size()-1);
			ImWord pImw = imw.getPreviousWord();
			if ((pImw == null) || ((pImw.getNextRelation() != ImWord.NEXT_RELATION_CONTINUE) && (pImw.getNextRelation() != ImWord.NEXT_RELATION_HYPHENATED))) {
				if (Gamta.insertSpace(pImt.value, imw.getString()))
					pImt.whitespace = " ";
				ImToken imt = new ImToken(pImt.imtEndOffset(), this.tokens.size(), imw);
				this.tokens.add(imt);
				this.imWordTokens.put(imw.getLocalID(), imt);
			}
			else {
				pImt.addWord(imw);
				this.imWordTokens.put(imw.getLocalID(), pImt);
			}
		}
		
		ImToken getTokenFor(ImWord imw) {
			return ((ImToken) this.imWordTokens.get(imw.getLocalID()));
		}
		
		int getTokenIndexOf(ImWord imw) {
			ImToken imt = this.getTokenFor(imw);
			return ((imt == null) ? -1 : imt.index);
		}
		
		ImToken imTokenAtIndex(int index) {
			return ((ImToken) this.tokens.get(index));
		}
		ImToken imTokenAtOffset(int offset) {
			int index = ((offset * this.size()) / this.length());
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
		
		public char charAt(int index) {
			ImToken tao = this.imTokenAtOffset(index);
			return tao.charAt(index - tao.getStartOffset());
		}
		public int length() {
			if (this.tokens.isEmpty())
				return 0;
			else return this.imTokenAtIndex(this.tokens.size()-1).getEndOffset();
		}
		public CharSequence subSequence(int start, int end) {
			StringBuffer subSequence = new StringBuffer();
			ImToken imt = this.imTokenAtOffset(start);
			subSequence.append(imt.subSequence((start - imt.getStartOffset()), Math.min(imt.imtLength(), (end - imt.getStartOffset()))));
			while (imt.imtEndOffset() < end) {
				imt = this.imTokenAtIndex(imt.index+1);
				start = imt.getStartOffset();
				subSequence.append(imt.subSequence((start - imt.getStartOffset()), Math.min(imt.imtLength(), (end - imt.getStartOffset()))));
			}
			return subSequence;
		}
		
		public Token tokenAt(int index) {
			return this.imTokenAtIndex(index);
		}
		public Token firstToken() {
			return this.imTokenAtIndex(0);
		}
		public Token lastToken() {
			return this.imTokenAtIndex(this.size()-1);
		}
		public String valueAt(int index) {
			return this.tokenAt(index).getValue();
		}
		public String firstValue() {
			return this.firstToken().getValue();
		}
		public String lastValue() {
			return this.lastToken().getValue();
		}
		public String getLeadingWhitespace() {
			return "";
		}
		public String getWhitespaceAfter(int index) {
			return ((ImToken) this.tokens.get(index)).whitespace;
		}
		public int size() {
			return this.tokens.size();
		}
		public TokenSequence getSubsequence(int start, int size) {
			ImToken sImt = this.imTokenAtIndex(start);
			int deltaOffset = sImt.getStartOffset();
			int deltaIndex = sImt.index;
			ImTokenSequence imts = new ImTokenSequence(this.tokenizer);
			for (int t = start; t < (start + size); t++)
				imts.tokens.add(this.imTokenAtIndex(t).deltaClone(deltaOffset, deltaIndex));
			return imts;
		}
		public Tokenizer getTokenizer() {
			return this.tokenizer;
		}
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
	
	private static final BoundingBox NULL_BOUNDING_BOX = new BoundingBox(0, 0, 0, 0);
}