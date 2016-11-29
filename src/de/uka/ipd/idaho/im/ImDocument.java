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
package de.uka.ipd.idaho.im;

import java.awt.ComponentOrientation;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Properties;
import java.util.TreeMap;

import de.uka.ipd.idaho.gamta.AttributeUtils;
import de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed;
import de.uka.ipd.idaho.gamta.util.imaging.BoundingBox;
import de.uka.ipd.idaho.gamta.util.imaging.PageImage;
import de.uka.ipd.idaho.gamta.util.imaging.PageImageInputStream;
import de.uka.ipd.idaho.gamta.util.imaging.PageImageSource;
import de.uka.ipd.idaho.gamta.util.imaging.PageImageStore;
import de.uka.ipd.idaho.im.util.ImUtils;

/**
 * An image markup document, root of the object hierarchy. An instance of this
 * class represents a range of consecutive pages from an Image Markup archive.
 * 
 * @author sautter
 */
public class ImDocument extends AbstractAttributed implements ImObject {
	
	/* TODO facilitate dynamically loading and disposing pages from some ImDocumentDataSource (to-create interface)
	 * - add isPageLoaded(int pageId) method
	 * - add loadPage(int pageId)
	 *   - add words
	 *   - add regions
	 *   - add annotations starting or ending on page and ending or starting on this page or one populated before
	 * - add disposePage(int pageId)
	 *   - store all words
	 *   - store all regions
	 *   - store and remove annotations starting or ending on page and ending or starting on this page or one populated before
	 * - if loading with gaps, load gap pages first, extending from what's already there
	 * - allow disposing only if one adjacent page is not loaded, i.e., previous or subsequent page is not loaded
	 * ==> add isPageLoaded(int pageId) method
	 * ==> add loadPage(int pageId) method
	 * ==> load pages on demand if not done before
	 * ==> page is loaded if words or regions are added
	 * 
	 * TODO disable listener notification for loading and disposing pages to facilitate using the regular addition and removal methods
	 */
	
	/* TODO to facilitate this:
	 * - add constructor taking ImDocumentDataSource as an argument
	 * - and add setImDocumentDataSource() method
	 * - add isPageLoaded(int pageId) method
	 * - auto-load page when it is retrieved by ID
	 */
	
	/* TODO implement atomic changes
	 * - startAtomicChange() and endAtomicChange()
	 * - no notifying listeners within atomic changes, only after the end
	 * - ImObjects issuing events store their changes instead ...
	 * - ... register with parent document to be asked to issue events after atomic change ends ...
	 * - ... and only do issue an even if final state different from original one
	 * 
	 * - maybe implement all of this right with event notification of this class
	 */
	
	/** The name of the document attribute to store any non-default
	 * <code>de.uka.ipd.idaho.gamte.Tokenizer</code> in that should be used for
	 * text handling and extraction purposes. If this attribute is not set at
	 * an <code>ImDocument</code>, the tokenizer client code should default to
	 * is <code>de.uka.ipd.idaho.gamte.Gamta.INNER_PUNCTUATION_TOKENIZER</code> */
	public static final String TOKENIZER_ATTRIBUTE = "tokenizer";
	
	private class ImDocumentAnnotation extends AbstractAttributed implements ImAnnotation {
		private ImWord firstWord;
		private ImWord lastWord;
		private String type;
		
		final long createTime;
		private String id = null;
		
		ImDocumentAnnotation(ImWord firstWord, ImWord lastWord, String type) {
			this.firstWord = firstWord;
			this.lastWord = lastWord;
			this.type = ((type == null) ? "annotation" : type);
			this.createTime = System.currentTimeMillis();
		}
		String getId() {
			if (this.id == null)
				this.id = (this.type + ":" + this.firstWord.getLocalID() + "-" + this.lastWord.getLocalID());
			return this.id;
		}
		public String getType() {
			return this.type;
		}
		public void setType(String type) {
			String oldType = this.type;
			this.type = type;
			String oldId = this.id;
			this.id = null;
			annotationTypeChanged(this, oldType, oldId);
			notifyTypeChanged(this, oldType);
		}
		public ImDocument getDocument() {
			return ImDocument.this;
		}
		public ImWord getFirstWord() {
			return this.firstWord;
		}
		public void setFirstWord(ImWord firstWord) {
			ImWord oldFirstWord = this.firstWord;
			this.firstWord = firstWord;
			String oldId = this.id;
			this.id = null;
			annotationFirstWordChanged(this, oldFirstWord, oldId);
			notifyAttributeChanged(this, FIRST_WORD_ATTRIBUTE, oldFirstWord);
		}
		public ImWord getLastWord() {
			return this.lastWord;
		}
		public void setLastWord(ImWord lastWord) {
			ImWord oldLastWord = this.lastWord;
			this.lastWord = lastWord;
			String oldId = this.id;
			this.id = null;
			annotationLastWordChanged(this, oldLastWord, oldId);
			notifyAttributeChanged(this, LAST_WORD_ATTRIBUTE, oldLastWord);
		}
		public Object getAttribute(String name) {
			if (FIRST_WORD_ATTRIBUTE.equals(name))
				return this.firstWord;
			if (LAST_WORD_ATTRIBUTE.equals(name))
				return this.lastWord;
			return super.getAttribute(name);
		}
		public Object getAttribute(String name, Object def) {
			if (FIRST_WORD_ATTRIBUTE.equals(name))
				return ((this.firstWord == null) ? def : this.firstWord);
			if (LAST_WORD_ATTRIBUTE.equals(name))
				return ((this.lastWord == null) ? def : this.lastWord);
			return super.getAttribute(name, def);
		}
		public boolean hasAttribute(String name) {
			if (FIRST_WORD_ATTRIBUTE.equals(name))
				return (this.firstWord != null);
			if (LAST_WORD_ATTRIBUTE.equals(name))
				return (this.lastWord != null);
			return super.hasAttribute(name);
		}
		public Object setAttribute(String name, Object value) {
			if (FIRST_WORD_ATTRIBUTE.equals(name) && (value instanceof ImWord)) {
				ImWord oldFirstWord = this.getFirstWord();
				this.setFirstWord((ImWord) value);
				return oldFirstWord;
			}
			if (LAST_WORD_ATTRIBUTE.equals(name) && (value instanceof ImWord)) {
				ImWord oldLastWord = this.getLastWord();
				this.setLastWord((ImWord) value);
				return oldLastWord;
			}
			Object oldValue = super.setAttribute(name, value);
			notifyAttributeChanged(this, name, oldValue);
			return oldValue;
		}
		public String getDocumentProperty(String propertyName) {
			return this.getDocument().getDocumentProperty(propertyName);
		}
		public String getDocumentProperty(String propertyName, String defaultValue) {
			return this.getDocument().getDocumentProperty(propertyName, defaultValue);
		}
		public String[] getDocumentPropertyNames() {
			return this.getDocument().getDocumentPropertyNames();
		}
	}
	
	private static class ImDocumentAnnotationList extends ArrayList {
		void addAnnot(ImDocumentAnnotation annot) {
			if (this.isEmpty() || (this.compare(annot, this.getAnnot(this.size()-1)) > 0)) {
				this.add(annot);
				return;
			}
			int index = (this.size() / 2);
			int step = (this.size() / 4);
			int c = this.compare(annot, this.getAnnot(index));
			while ((1 < step) && (c != 0)) {
				if (c < 0)
					index -= step;
				else index += step;
				step = (step / 2);
				c = this.compare(annot, this.getAnnot(index));
			}
			while ((c < 0) && (index != 0)) {
				index--;
				c = this.compare(annot, this.getAnnot(index));
			}
			while ((c > 0) && ((index + 1) < this.size())) {
				index++;
				c = this.compare(annot, this.getAnnot(index));
			}
			this.add(index, annot);
		}
		ImDocumentAnnotation getAnnot(int index) {
			return ((ImDocumentAnnotation) this.get(index));
		}
		void removeAnnot(ImDocumentAnnotation annot) {
			int index = (this.size() / 2);
			int step = (this.size() / 4);
			int c = this.compare(annot, this.getAnnot(index));
			while ((1 < step) && (c != 0)) {
				if (c < 0)
					index -= step;
				else index += step;
				step = (step / 2);
				c = this.compare(annot, this.getAnnot(index));
			}
			while ((c <= 0) && (index != 0)) {
				index--;
				c = this.compare(annot, this.getAnnot(index));
			}
			while ((c >= 0) && (index < this.size())) {
				if (annot == this.get(index)) {
					this.remove(index);
					return;
				}
				c = this.compare(annot, this.getAnnot(index));
				index++;
			}
		}
		void reSort() {
			//	do a single pass of bubble sort left to right
			for (int a = 1; a < this.size(); a++) {
				if (this.compare(this.getAnnot(a-1), this.getAnnot(a)) > 0)
					this.set((a-1), this.set(a, this.get(a-1)));
			}
			//	do a single pass of bubble sort right to left
			for (int a = (this.size() - 1); a > 0; a--) {
				if (this.compare(this.getAnnot(a-1), this.getAnnot(a)) > 0)
					this.set((a-1), this.set(a, this.get(a-1)));
			}
		}
		private int compare(ImDocumentAnnotation annot1, ImDocumentAnnotation annot2) {
			int c = ImUtils.textStreamOrder.compare(annot1.firstWord, annot2.firstWord);
			if (c != 0)
				return c;
			c = ImUtils.textStreamOrder.compare(annot2.lastWord, annot1.lastWord);
			if (c != 0)
				return c;
			return ((int) (annot1.createTime - annot2.createTime));
			
			//	TODO compare page IDs first, and text stream IDs only then, so annotations from same page stay together
			
//			if (annot1.firstWord.pageId == annot2.firstWord.pageId)
//				return (annot2.lastWord.pageId - annot1.lastWord.pageId);
//			else return (annot1.firstWord.pageId - annot2.firstWord.pageId);
			
//			int c = 0;
//			if (annot1 == annot2)
//				return c;
//			c = (annot1.getFirstWord().pageId - annot2.getFirstWord().pageId);
//			if (c != 0)
//				return c;
//			c = (annot2.getLastWord().pageId - annot1.getLastWord().pageId);
//			if (c != 0)
//				return c;
//			c = annot1.getFirstWord().getTextStreamId().compareTo(annot2.getFirstWord().getTextStreamId());
//			if (c != 0)
//				return c;
//			c = (annot1.getFirstWord().getTextStreamPos() - annot2.getFirstWord().getTextStreamPos());
//			if (c != 0)
//				return c;
//			c = (annot2.getLastWord().getTextStreamPos() - annot1.getLastWord().getTextStreamPos());
//			if (c != 0)
//				return c;
//			return ((int) (annot1.createTime - annot2.createTime));
		}
		ImAnnotation[] toAnnotArray() {
			return ((ImAnnotation[]) this.toArray(new ImAnnotation[this.size()]));
		}
	}
	
	/**
	 * Listener receiving notifications of changes to an Image Markup document
	 * and its embedded objects.
	 * 
	 * @author sautter
	 */
	public static interface ImDocumentListener {
		
		/**
		 * Notify the listener that the type of an Image Markup object has
		 * changed.
		 * @param object the object whose type changed
		 * @param oldType the old type of the object
		 */
		public abstract void typeChanged(ImObject object, String oldType);
		
		/**
		 * Notify the listener that an attribute has changed in an Image Markup
		 * object. The affected attribute can also be a functional pseudo
		 * attribute, like the predecessor or successor of Image Markup words.
		 * @param object the object whose attribute changed
		 * @param attributeName the name of the attribute
		 * @param oldValue the old value of the attribute, which was just
		 *        replaced
		 */
		public abstract void attributeChanged(ImObject object, String attributeName, Object oldValue);
		
		/**
		 * Notify the listener that a region has been added. The runtime type
		 * of the argument region can also be an Image Markup word or page.
		 * @param region the region that was just added
		 */
		public abstract void regionAdded(ImRegion region);
		
		/**
		 * Notify the listener that a region has been removed. The runtime type
		 * of the argument region can also be an Image Markup word.
		 * @param region the region that was just removed
		 */
		public abstract void regionRemoved(ImRegion region);
		
		/**
		 * Notify the listener that an annotation has been added.
		 * @param annotation the annotation that was just added
		 */
		public abstract void annotationAdded(ImAnnotation annotation);
		
		/**
		 * Notify the listener that an annotation has been removed.
		 * @param region the annotation that was just removed
		 */
		public abstract void annotationRemoved(ImAnnotation annotation);
	}
	
	/** the UUID of the document */
	public final String docId;
	
	/** the orientation of the text in the images contained in the document, determining the reading order of words */
	public final ComponentOrientation orientation;
	
	private TreeMap fontsByName = new TreeMap();
	private TreeMap supplementsById = new TreeMap();
	
	private TreeMap pagesById = new TreeMap();
	
	private ImDocumentAnnotationList annotations = new ImDocumentAnnotationList();
	private AnnotationIdIndex annotationsById = new AnnotationIdIndex();
	private TreeMap annotationsByType = new TreeMap();
	private TreeMap annotationsByFirstWord = new TreeMap();
	private TreeMap annotationsByLastWord = new TreeMap();
	private TreeMap annotationsByPageId = new TreeMap();
	
	private Properties documentProperties = new Properties();
	
	private PageImageSource pageImageSource = null;
	private PageImageStore pageImageStore = null;
	
	/** Constructor
	 * @param docId the ID of the document
	 */
	public ImDocument(String docId) {
		this(docId, null);
	}
	
	/** Constructor
	 * @param docId the ID of the document
	 * @param orientation the orientation of the text in the images contained
	 *            in the document, determining the reading order of words
	 */
	public ImDocument(String docId, ComponentOrientation orientation) {
		this.docId = docId;
		if (orientation == null)
			orientation = ComponentOrientation.getOrientation(Locale.US);
		this.orientation = orientation;
	}
	
	/**
	 * Give this Image Markup document a specific page image source to provide
	 * page images for this very document. This causes the page images provided
	 * by the argument source to be preferred over any other page image coming
	 * from any other page image sources. If the argument page image source is
	 * also a gape image store, it is used in the latter function as well.
	 * @param pis the preferred page image source for the document
	 */
	public void setPageImageSource(PageImageSource pis) {
		this.pageImageSource = pis;
		if (pis instanceof PageImageStore)
			this.pageImageStore = ((PageImageStore) pis);
		else this.pageImageStore = null;
	}
	
	/**
	 * Retrieve the image of a particular page from the source associated with
	 * this Image Markup document. If no dedicated page image source is present,
	 * the general sources of page images will be consulted.
	 * @return pageId the ID of the page whose image to retrieve
	 * @return the image of the page with the argument ID
	 */
	public PageImage getPageImage(int pageId) throws IOException {
		PageImage pi = ((this.pageImageSource == null) ? null : this.pageImageSource.getPageImage(this.docId,  pageId));
		return ((pi == null) ? PageImage.getPageImage(this.docId, pageId) : pi);
	}
	
	/**
	 * Retrieve the image of a particular page from the source associated with
	 * this Image Markup document. If no dedicated page image source is present,
	 * the general sources of page images will be consulted.
	 * @return pageId the ID of the page whose image to retrieve
	 * @return the image of the page with the argument ID
	 */
	public PageImageInputStream getPageImageAsStream(int pageId) throws IOException {
		PageImageInputStream piis = ((this.pageImageSource == null) ? null : this.pageImageSource.getPageImageAsStream(this.docId,  pageId));
		return ((piis == null) ? PageImage.getPageImageAsStream(this.docId, pageId) : piis);
	}
	
	/**
	 * Store the image of a particular page in the page image store associated
	 * with this Image Markup document. If no dedicated page image store is
	 * present, the general storages of page images will be consulted.
	 * @return pi the page image to store
	 * @return pageId the ID of the page the image belongs to
	 * @return the storage name of the page image
	 */
	public String storePageImage(PageImage pi, int pageId) throws IOException {
		return ((this.pageImageStore == null) ? PageImage.storePageImage(this.docId, pageId, pi) : this.pageImageStore.storePageImage(this.docId, pageId, pi));
	}
	
	/**
	 * Dispose of the document. This method clears all internal data
	 * structures, so the document is no longer usable after an invocation of
	 * this method.
	 */
	public void dispose() {
		if (this.listeners != null)
			this.listeners.clear();
		this.listeners = null;
		ImPage[] pages = this.getPages();
		for (int p = 0; p < pages.length; p++)
			pages[p].dispose();
		this.pagesById.clear();
		synchronized (this.annotationsById) {
			this.annotationsById.clear();
			this.annotations.clear();
			this.annotationsByPageId.clear();
			clearAnnotationIndex(this.annotationsByType);
			clearAnnotationIndex(this.annotationsByFirstWord);
			clearAnnotationIndex(this.annotationsByLastWord);
		}
		this.supplementsById.clear();
		this.fontsByName.clear();
		this.documentProperties.clear();
		this.clearAttributes();
	}
	private static void clearAnnotationIndex(TreeMap index) {
		for (Iterator alkit = index.keySet().iterator(); alkit.hasNext();) {
			String annotListKey = ((String) alkit.next());
			ImDocumentAnnotationList annotList = ((ImDocumentAnnotationList) index.get(annotListKey));
			annotList.clear();
			alkit.remove();
		}
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImObject#getType()
	 */
	public String getType() {
		return DOCUMENT_TYPE;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#setAttribute(java.lang.String, java.lang.Object)
	 */
	public Object setAttribute(String name, Object value) {
		Object oldValue = super.setAttribute(name, value);
		this.notifyAttributeChanged(this, name, oldValue);
		return oldValue;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImObject#getDocumentProperty(java.lang.String)
	 */
	public String getDocumentProperty(String propertyName) {
		return this.getDocumentProperty(propertyName, null);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImObject#getDocumentProperty(java.lang.String, java.lang.String)
	 */
	public String getDocumentProperty(String propertyName, String defaultValue) {
		return this.documentProperties.getProperty(propertyName, defaultValue);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImObject#getDocumentPropertyNames()
	 */
	public String[] getDocumentPropertyNames() {
		return ((String[]) this.documentProperties.keySet().toArray(new String[this.documentProperties.size()]));
	}
	
	/**
	 * Set a property for this document. The value is then accessible from all
	 * child objects that belong to the hierarchy built on this document.
	 * @param propertyName the name for the property
	 * @param value the value for the property
	 * @return the old value of the property, or null, if there was no such
	 *            value
	 */
	public String setDocumentProperty(String propertyName, String value) {
		if (value == null)
			return this.removeDocumentProperty(propertyName);
		else return ((String) this.documentProperties.setProperty(propertyName, value));
	}
	
	/** 
	 * Remove a property from this document.
	 * @param propertyName the name of the property to remove
	 * @return the value of the property that was just removed, or null, if
	 *            there was no value
	 */
	public String removeDocumentProperty(String propertyName) {
		return ((String) this.documentProperties.remove(propertyName));
	}
	
	/**
	 * Remove all properties from this document.
	 */
	public void clearDocumentProperties() {
		this.documentProperties.clear();
	}
	
	/**
	 * This implementation does not have any effect, so its corresponding getter
	 * always returns 'document'.
	 * @see de.uka.ipd.idaho.im.ImObject#setType(java.lang.String)
	 */
	public void setType(String type) {}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImObject#getDocument()
	 */
	public ImDocument getDocument() {
		return this;
	}
	
	/**
	 * Add a font to the document.
	 * @param font the font to add
	 */
	public void addFont(ImFont font) {
		synchronized (this.fontsByName) {
			this.fontsByName.put(font.name, font);
		}
	}
	
	/**
	 * Retrieve a font by its name.
	 * @param name the name of the required font
	 */
	public ImFont getFont(String name) {
		return ((name == null) ? null : ((ImFont) this.fontsByName.get(name)));
	}
	
	/**
	 * Retrieve the names of the fonts present in the document.
	 * @return an array holding the font names
	 */
	public String[] getFontNames() {
		return ((String[]) this.fontsByName.keySet().toArray(new String[this.fontsByName.size()]));
	}
	
	/**
	 * Retrieve the fonts present in the document.
	 * @return an array holding the fonts
	 */
	public ImFont[] getFonts() {
		return ((ImFont[]) this.fontsByName.values().toArray(new ImFont[this.fontsByName.size()]));
	}
	
	/**
	 * Remove a font from the document.
	 * @param font the font to remove
	 */
	public void removeFont(ImFont font) {
		this.removeFont(font.name);
	}
	
	/**
	 * Remove a font from the document.
	 * @param name the name of the font to remove
	 */
	public void removeFont(String name) {
		this.fontsByName.remove(name);
	}
	
	/**
	 * Add a supplement to the document, or replace one with a newer version.
	 * @param ims the supplement to add
	 * @return the supplement replaced by the addition of the argument one
	 */
	public ImSupplement addSupplement(ImSupplement ims) {
		synchronized (this.supplementsById) {
			return ((ImSupplement) this.supplementsById.put(ims.getId(), ims));
			//	TODO dispose any replaced supplement
		}
	}
	
	/**
	 * Retrieve a supplement by its ID.
	 * @param sid the ID of the required supplement
	 */
	public ImSupplement getSupplement(String sid) {
		return ((sid == null) ? null : ((ImSupplement) this.supplementsById.get(sid)));
	}
	
	/**
	 * Retrieve the IDs of the supplements present in the document.
	 * @return an array holding the supplement IDs
	 */
	public String[] getSupplementIDs() {
		return ((String[]) this.supplementsById.keySet().toArray(new String[this.supplementsById.size()]));
	}
	
	/**
	 * Retrieve the supplements present in the document.
	 * @return an array holding the supplements
	 */
	public ImSupplement[] getSupplements() {
		return ((ImSupplement[]) this.supplementsById.values().toArray(new ImSupplement[this.supplementsById.size()]));
	}
	
	/**
	 * Remove a supplement from the document.
	 * @param ims the supplement to remove
	 */
	public void removeSupplement(ImSupplement ims) {
		this.removeSupplement(ims.getId());
		//	TODO dispose any removed supplement
	}
	
	/**
	 * Remove a supplement from the document.
	 * @param sid the ID of the supplement to remove
	 */
	public void removeSupplement(String sid) {
		this.supplementsById.remove(sid);
	}
	
	void addPage(ImPage page) {
		ImPage oldPage = ((ImPage) this.pagesById.put(new Integer(page.pageId), page));
		if (oldPage != null)
			oldPage.dispose();
	}
	
	/**
	 * Retrieve a page by its ID.
	 * @param pageId the ID of the sought page
	 * @return the page with the argument ID
	 */
	public ImPage getPage(int pageId) {
		return ((ImPage) this.pagesById.get(new Integer(pageId)));
	}
	
	/**
	 * Retrieve the pages of the document.
	 * @return an array holding the pages of the document
	 */
	public ImPage[] getPages() {
		ImPage[] pages = new ImPage[this.pagesById.size()];
		int pin = 0;
		for (Iterator pit = this.pagesById.keySet().iterator(); pit.hasNext();) {
			Integer pageId = ((Integer) pit.next());
			pages[pin++] = ((ImPage) this.pagesById.get(pageId));
		}
		return pages;
	}
	
	/**
	 * Retrieve the ID of the first page loaded in this document. This ID is
	 * not necessarily 0, as instances of this class can also be created to
	 * represent only a part of an Image Markup archive. However, a valid ID is
	 * always non-negative. A return value of -1 indicates an empty document
	 * that contains no pages.
	 * @return the ID of the first page
	 */
	public int getFirstPageId() {
		if (this.pagesById.isEmpty())
			return -1;
		return ((Integer) this.pagesById.firstKey()).intValue();
	}
	
	/**
	 * Retrieve the number of pages in this document.
	 * @return the number of pages
	 */
	public int getPageCount() {
		return this.pagesById.size();
	}
	
	/**
	 * Discard a page with a given ID. The discarded page is disposed and
	 * cannot be used after this method was called. This method is not intended
	 * for regular back and forth use, but rather for rare situations during
	 * document generation, where it only becomes clear after loading that some
	 * pages are actually be obsolete, e.g. some tailing blank pages. This is
	 * why this method is not named along the lines of the usual add/remove
	 * method pairs.
	 * @param pageId the ID of the page to discard
	 */
	public void discardPage(int pageId) {
		ImPage page = ((ImPage) this.pagesById.remove(new Integer(pageId)));
		if (page != null)
			page.dispose();
	}
	
	/**
	 * Add an annotation to the document. If the argument annotation originates
	 * from this document, this method does nothing. Otherwise, an annotation
	 * is added to the document and returned. It is not the same object as the
	 * argument annotation, but has the same attributes.
	 * @param annot the annotation to add
	 * @return the newly added annotation
	 * @see de.uka.ipd.idaho.im.ImDocument#cleanupAnnotations()
	 */
	public ImAnnotation addAnnotation(ImAnnotation annot) {
		if ((annot instanceof ImDocumentAnnotation) && (annot.getDocument() == this))
			return annot;
		ImAnnotation docAnnot = this.addAnnotation(annot.getFirstWord(), annot.getLastWord(), annot.getType());
		if (docAnnot != null)
			docAnnot.copyAttributes(annot);
		return docAnnot;
	}
	
	/**
	 * Add an annotation to the document, marking the argument word.
	 * @param word the word to annotate
	 * @param type the type of the annotation
	 * @return the newly added annotation
	 * @see de.uka.ipd.idaho.im.ImDocument#cleanupAnnotations()
	 */
	public ImAnnotation addAnnotation(ImWord word, String type) {
		return this.addAnnotation(word, word, type);
	}
	
	/**
	 * Add an annotation to the document, marking the range between the two
	 * argument words. If the argument words belong to different logical text
	 * streams, this method changes nothing and returns null.
	 * @param firstWord the first word included in the annotation
	 * @param lastWord the last word included in the annotation
	 * @param type the type of the annotation
	 * @return the newly added annotation
	 * @see de.uka.ipd.idaho.im.ImDocument#cleanupAnnotations()
	 */
	public ImAnnotation addAnnotation(ImWord firstWord, ImWord lastWord, String type) {
		if (!firstWord.getTextStreamId().equals(lastWord.getTextStreamId()))
			return null;
		ImDocumentAnnotation annot = new ImDocumentAnnotation(firstWord, lastWord, type);
		if (this.annotationsById.containsKey(annot.getId()))
			return ((ImAnnotation) this.annotationsById.get(annot.getId()));
		synchronized (this.annotationsById) {
			this.annotationsById.add(annot);
			this.annotations.addAnnot(annot);
			updateAnnotationIndex(this.annotationsByType, null, annot.getType(), annot);
			updateAnnotationIndex(this.annotationsByFirstWord, null, annot.getFirstWord().getLocalID(), annot);
			updateAnnotationIndex(this.annotationsByLastWord, null, annot.getLastWord().getLocalID(), annot);
			this.indexAnnotationForPageIDs(annot, firstWord.pageId, lastWord.pageId);
		}
		this.notifyAnnotationAdded(annot);
		return annot;
	}
	
	/**
	 * Remove an annotation from the document. If the argument annotation does
	 * not originate from the document, this method has no effect.
	 * @param annot the annotation to remove
	 * @see de.uka.ipd.idaho.im.ImDocument#cleanupAnnotations()
	 */
	public void removeAnnotation(ImAnnotation annot) {
		if ((annot instanceof ImDocumentAnnotation) && (annot.getDocument() == this)) {
			ImDocumentAnnotation docAnnot = ((ImDocumentAnnotation) annot);
			synchronized (this.annotationsById) {
				this.annotationsById.remove(docAnnot.getId(), docAnnot);
				this.annotations.removeAnnot(docAnnot);
				updateAnnotationIndex(this.annotationsByType, annot.getType(), null, docAnnot);
				updateAnnotationIndex(this.annotationsByFirstWord, annot.getFirstWord().getLocalID(), null, docAnnot);
				updateAnnotationIndex(this.annotationsByLastWord, annot.getLastWord().getLocalID(), null, docAnnot);
				this.unIndexAnnotationForPageIDs(docAnnot, annot.getFirstWord().pageId, annot.getLastWord().pageId);
			}
			this.notifyAnnotationRemoved(annot);
		}
	}
	
	void annotationTypeChanged(ImDocumentAnnotation annot, String oldType, String oldId) {
		synchronized (this.annotationsById) {
			boolean annotInIndexes = this.annotationsById.remove(oldId, annot);
			this.annotationsById.add(annot);
			
			if (!annotInIndexes)
				this.annotations.addAnnot(annot);
			
			//	if old ID index removal returns false, we're undoing a duplicate cleanup, need to update _all_ indexes
			updateAnnotationIndex(this.annotationsByType, oldType, annot.getType(), annot);
			if (!annotInIndexes) {
				updateAnnotationIndex(this.annotationsByFirstWord, null, annot.getFirstWord().getLocalID(), annot);
				updateAnnotationIndex(this.annotationsByLastWord, null, annot.getLastWord().getLocalID(), annot);
			}
			
			if (!annotInIndexes)
				this.indexAnnotationForPageIDs(annot, annot.getFirstWord().pageId, annot.getLastWord().pageId);
		}
	}
	
	void annotationFirstWordChanged(ImDocumentAnnotation annot, ImWord oldFirstWord, String oldId) {
		synchronized (this.annotationsById) {
			boolean annotInIndexes = this.annotationsById.remove(oldId, annot);
			this.annotationsById.add(annot);
			
			if (annotInIndexes)
				this.annotations.reSort();
			else this.annotations.addAnnot(annot);
			
			//	if old ID index removal returns false, we're undoing a duplicate cleanup, need to update _all_ indexes
			updateAnnotationIndex(this.annotationsByFirstWord, oldFirstWord.getLocalID(), annot.getFirstWord().getLocalID(), annot);
			if (!annotInIndexes) {
				updateAnnotationIndex(this.annotationsByType, null, annot.getType(), annot);
				updateAnnotationIndex(this.annotationsByLastWord, null, annot.getLastWord().getLocalID(), annot);
			}
			
			ImDocumentAnnotationList typeAnnots = ((ImDocumentAnnotationList) this.annotationsByType.get(annot.getType()));
			if (typeAnnots != null)
				typeAnnots.reSort();
			
			if (annotInIndexes) {
				this.unIndexAnnotationForPageIDs(annot, oldFirstWord.pageId, annot.getFirstWord().pageId);
				this.indexAnnotationForPageIDs(annot, annot.getFirstWord().pageId, oldFirstWord.pageId);
			}
			else this.indexAnnotationForPageIDs(annot, annot.getFirstWord().pageId, annot.getLastWord().pageId);
		}
	}
	
	void annotationLastWordChanged(ImDocumentAnnotation annot, ImWord oldLastWord, String oldId) {
		synchronized (this.annotationsById) {
			boolean annotInIndexes = this.annotationsById.remove(oldId, annot);
			this.annotationsById.add(annot);
			
			if (annotInIndexes)
				this.annotations.reSort();
			else this.annotations.addAnnot(annot);
			
			//	if old ID index removal returns false, we're undoing a duplicate cleanup, need to update _all_ indexes
			updateAnnotationIndex(this.annotationsByLastWord, oldLastWord.getLocalID(), annot.getLastWord().getLocalID(), annot);
			if (!annotInIndexes) {
				updateAnnotationIndex(this.annotationsByType, null, annot.getType(), annot);
				updateAnnotationIndex(this.annotationsByFirstWord, null, annot.getFirstWord().getLocalID(), annot);
			}
			
			ImDocumentAnnotationList typeAnnots = ((ImDocumentAnnotationList) this.annotationsByType.get(annot.getType()));
			if (typeAnnots != null)
				typeAnnots.reSort();
			
			if (annotInIndexes) {
				this.unIndexAnnotationForPageIDs(annot, (annot.getLastWord().pageId + 1), oldLastWord.pageId);
				this.indexAnnotationForPageIDs(annot, (oldLastWord.pageId + 1), annot.getLastWord().pageId);
			}
			else this.indexAnnotationForPageIDs(annot, annot.getFirstWord().pageId, annot.getLastWord().pageId);
		}
	}
	
	/**
	 * Clean up the document's internal annotation indices, e.g. merging up
	 * duplicate annotations. The latter can emerge when the type, first word,
	 * or last word of an annotation is modified, incurring it to match all
	 * three of another existing annotation. Client code should thus call this
	 * method after extensive modifications to aforementioned three attributes
	 * of annotations.
	 */
	public void cleanupAnnotations() {
		if (this.annotationsById.containsDuplicates())
			this.annotationsById.cleanupDuplicates();
	}
	
	private class AnnotationIdIndex {
		private TreeMap annotsById = new TreeMap();
		private int annotListCount = 0;
		private class AnnotationList extends ArrayList {
			AnnotationList() {
				super(2);
				annotListCount++;
			}
			public void clear() {
				super.clear();
				annotListCount--;
			}
		}
		AnnotationIdIndex() {}
		synchronized boolean containsKey(String key) {
			return this.annotsById.containsKey(key);
		}
		synchronized ImDocumentAnnotation get(String key) {
			Object annotObj = this.annotsById.get(key);
			if (annotObj instanceof ImDocumentAnnotation)
				return ((ImDocumentAnnotation) annotObj);
			else return ((ImDocumentAnnotation) ((AnnotationList) annotObj).get(0));
		}
		synchronized void add(ImDocumentAnnotation annot) {
//			System.out.println("Adding ID key " + annot.getId());
			Object oldAnnotObj = this.annotsById.put(annot.getId(), annot);
			if (oldAnnotObj == null) {
//				System.out.println(" ==> new ID key added");
				return; // the very most common case, and thus we put without getting first, at the danger of having to re-put
			}
			AnnotationList annotList;
			if (oldAnnotObj instanceof AnnotationList)
				annotList = ((AnnotationList) oldAnnotObj);
			else {
				annotList = new AnnotationList();
				annotList.add(oldAnnotObj);
//				System.out.println(" ==> duplicate ID key with " + annotList.size() + " other annotations already present");
			}
			annotList.add(annot);
			this.annotsById.put(annot.getId(), annotList);
		}
		synchronized boolean remove(String annotId, ImDocumentAnnotation annot) {
//			System.out.println("Removing ID key " + annotId);
			Object oldAnnotObj = this.annotsById.remove(annotId);
			if (oldAnnotObj == null) {
//				System.out.println(" ==> ID key unknown");
				return false;
			}
			if (oldAnnotObj == annot) {
//				System.out.println(" ==> ID key removed");
				return true; // the very most common case, and thus we remove without getting first, at the danger of having to re-put
			}
			//	if we have removed a single annotation that is NON the argument annotation, we need to re-put it (can happen when UNDOing an operation that resulted in a duplicate, which was cleaned up later)
			if (oldAnnotObj instanceof ImDocumentAnnotation) {
//				System.out.println(" ==> ID key known, but mapped to other annotation");
				this.annotsById.put(annotId, oldAnnotObj);
				return false;
			}
			AnnotationList annotList = ((AnnotationList) oldAnnotObj);
			boolean removed = annotList.remove(annot);
			if (annotList.size() == 1) {
				this.annotsById.put(annotId, annotList.get(0));
//				System.out.println(" ==> duplicate ID key with " + annotList.size() + " other annotations remaining");
				annotList.clear();
			}
			else {
				this.annotsById.put(annotId, annotList);
//				System.out.println(" ==> duplicate ID key with " + annotList.size() + " other annotations remaining");
			}
			return removed;
		}
		synchronized void clear() {
			this.annotsById.clear();
			this.annotListCount = 0;
		}
		synchronized boolean containsDuplicates() {
//			System.out.println("Checking for duplicate annotations, annotation list count is " + this.annotListCount);
			return (this.annotListCount != 0);
		}
		synchronized void cleanupDuplicates() {
//			System.out.println("Cleaning up duplicate annotations, annotation list count is " + this.annotListCount);
			
			//	collect duplicate lists
			TreeMap dAnnotsById = new TreeMap();
			for (Iterator aidit = this.annotsById.keySet().iterator(); aidit.hasNext();) {
				String annotId = ((String) aidit.next());
				Object annotObj = this.annotsById.get(annotId);
				if (annotObj instanceof AnnotationList)
					dAnnotsById.put(annotId, annotObj);
			}
			
			//	reconcile duplicates
			for (Iterator aidit = dAnnotsById.keySet().iterator(); aidit.hasNext();) {
				String annotId = ((String) aidit.next());
				AnnotationList annotList = ((AnnotationList) dAnnotsById.get(annotId));
				ImDocumentAnnotation annot = ((ImDocumentAnnotation) annotList.get(0));
				for (int a = 1; a < annotList.size(); a++) {
					ImDocumentAnnotation dAnnot = ((ImDocumentAnnotation) annotList.get(a));
					AttributeUtils.copyAttributes(dAnnot, annot, AttributeUtils.ADD_ATTRIBUTE_COPY_MODE);
					annotations.removeAnnot(dAnnot);
					updateAnnotationIndex(annotationsByType, dAnnot.getType(), null, dAnnot);
					updateAnnotationIndex(annotationsByFirstWord, dAnnot.getFirstWord().getLocalID(), null, dAnnot);
					updateAnnotationIndex(annotationsByLastWord, dAnnot.getLastWord().getLocalID(), null, dAnnot);
					unIndexAnnotationForPageIDs(dAnnot, dAnnot.getFirstWord().pageId, dAnnot.getLastWord().pageId);
				}
				annotList.clear();
				this.annotsById.put(annotId, annot);
			}
		}
	}
	
	private static void updateAnnotationIndex(TreeMap index, String oldKey, String newKey, ImDocumentAnnotation annot) {
		if (oldKey != null) {
			ImDocumentAnnotationList oldKeyAnnots = ((ImDocumentAnnotationList) index.get(oldKey));
			if (oldKeyAnnots != null) {
				oldKeyAnnots.remove(annot); // we have to use this method of removal, as sort order might be compromised by first or last word update
				if (oldKeyAnnots.size() == 0)
					index.remove(oldKey);
			}
		}
		if (newKey != null) {
			ImDocumentAnnotationList newKeyAnnots = ((ImDocumentAnnotationList) index.get(newKey));
			if (newKeyAnnots == null) {
				newKeyAnnots = new ImDocumentAnnotationList();
				index.put(newKey, newKeyAnnots);
			}
			newKeyAnnots.addAnnot(annot); // this method automatically maintains sort order
		}
	}
	
	private void indexAnnotationForPageIDs(ImDocumentAnnotation annot, int fromPageId, int toPageId) {
		for (int p = fromPageId; p <= toPageId; p++) {
			ArrayList pageAnnots = ((ArrayList) this.annotationsByPageId.get(new Integer(p)));
			if (pageAnnots == null) {
				pageAnnots = new ArrayList();
				this.annotationsByPageId.put(new Integer(p), pageAnnots);
			}
			pageAnnots.add(annot);
		}
	}
	
	private void unIndexAnnotationForPageIDs(ImDocumentAnnotation annot, int fromPageId, int toPageId) {
		for (int p = fromPageId; p <= toPageId; p++) {
			ArrayList pageAnnots = ((ArrayList) annotationsByPageId.get(new Integer(p)));
			if (pageAnnots != null)
				pageAnnots.remove(annot);
		}
	}
	
	/**
	 * Retrieve all annotations from the document.
	 * @return an array holding the annotations
	 * @see de.uka.ipd.idaho.im.ImDocument#cleanupAnnotations()
	 */
	public ImAnnotation[] getAnnotations() {
		return this.annotations.toAnnotArray();
	}
	
	/**
	 * Retrieve the annotations of a specific type from the document.
	 * @param the type of the sought annotations
	 * @return an array holding the annotations
	 * @see de.uka.ipd.idaho.im.ImDocument#cleanupAnnotations()
	 */
	public ImAnnotation[] getAnnotations(String type) {
		if (type == null)
			return this.getAnnotations();
		ImDocumentAnnotationList typeAnnots = ((ImDocumentAnnotationList) this.annotationsByType.get(type));
		if (typeAnnots == null)
			return new ImAnnotation[0];
		return typeAnnots.toAnnotArray();
	}
	
	/**
	 * Retrieve the annotations overlapping a specific page from the document.
	 * @param pageId the ID of the page annotations are sought for
	 * @return an array holding the annotations
	 * @see de.uka.ipd.idaho.im.ImDocument#cleanupAnnotations()
	 */
	public ImAnnotation[] getAnnotations(int pageId) {
		return this.getAnnotations(null, pageId, pageId);
	}
	
	/**
	 * Retrieve the annotations overlapping a specific range of pages from the
	 * document.
	 * @param firstPageId the ID of the first page annotations are sought for
	 * @param lastPageId the ID of the last page annotations are sought for
	 * @return an array holding the annotations
	 * @see de.uka.ipd.idaho.im.ImDocument#cleanupAnnotations()
	 */
	public ImAnnotation[] getAnnotations(int firstPageId, int lastPageId) {
		return getAnnotations(null, firstPageId, lastPageId);
	}
	
	/**
	 * Retrieve the annotations of a specific type overlapping a specific page
	 * from the document.
	 * @param the type of the sought annotations
	 * @param pageId the ID of the page annotations are sought for
	 * @return an array holding the annotations
	 * @see de.uka.ipd.idaho.im.ImDocument#cleanupAnnotations()
	 */
	public ImAnnotation[] getAnnotations(String type, int pageId) {
		return this.getAnnotations(type, pageId, pageId);
	}
	
	/**
	 * Retrieve the annotations of a specific type overlapping a specific range
	 * of pages from the document.
	 * @param the type of the sought annotations
	 * @param firstPageId the ID of the first page annotations are sought for
	 * @param lastPageId the ID of the last page annotations are sought for
	 * @return an array holding the annotations
	 * @see de.uka.ipd.idaho.im.ImDocument#cleanupAnnotations()
	 */
	public ImAnnotation[] getAnnotations(String type, int firstPageId, int lastPageId) {
		ImAnnotationCollectorList overlappingAnnots = new ImAnnotationCollectorList();
		for (int p = firstPageId; p <= lastPageId; p++) {
			ArrayList pageAnnots = ((ArrayList) this.annotationsByPageId.get(new Integer(p)));
			if (pageAnnots == null)
				continue;
			for (int a = 0; a < pageAnnots.size(); a++) {
				ImDocumentAnnotation annot = ((ImDocumentAnnotation) pageAnnots.get(a));
				if ((type == null) || type.equals(annot.type))
					overlappingAnnots.addAnnot(annot);
			}
		}
		return overlappingAnnots.toAnnotArray();
	}
	
	/**
	 * Retrieve the annotations starting or ending at a specific word.
	 * @param word the word covered by the sought annotations
	 * @return an array holding the annotations
	 * @see de.uka.ipd.idaho.im.ImDocument#cleanupAnnotations()
	 */
	public ImAnnotation[] getAnnotations(ImWord word) {
		return this.getAnnotations(word, word);
	}
	
	/**
	 * Retrieve the annotations starting or ending at a specific word. Either
	 * of the argument words may be null; if both are null, the returned array
	 * is empty.
	 * @param firstWord the first word covered by the sought annotations
	 * @param lastWord the last word covered by the sought annotations
	 * @return an array holding the annotations
	 * @see de.uka.ipd.idaho.im.ImDocument#cleanupAnnotations()
	 */
	public ImAnnotation[] getAnnotations(ImWord firstWord, ImWord lastWord) {
		ImAnnotationCollectorList wordAnnots = new ImAnnotationCollectorList();
		if (firstWord != null) {
			ImDocumentAnnotationList firstWordAnnots = ((ImDocumentAnnotationList) this.annotationsByFirstWord.get(firstWord.getLocalID()));
			if (firstWordAnnots != null) {
				for (int a = 0; a < firstWordAnnots.size(); a++)
					wordAnnots.addAnnot(firstWordAnnots.getAnnot(a));
			}
		}
		if (lastWord != null) {
			ImDocumentAnnotationList lastWordAnnots = ((ImDocumentAnnotationList) this.annotationsByLastWord.get(lastWord.getLocalID()));
			if (lastWordAnnots != null)
				for (int a = 0; a < lastWordAnnots.size(); a++) {
					ImDocumentAnnotation annot = lastWordAnnots.getAnnot(a);
					if ((firstWord == null) || (annot.getFirstWord() != firstWord))
						wordAnnots.addAnnot(annot);
				}
		}
		return wordAnnots.toAnnotArray();
	}
	
	/**
	 * Retrieve the annotations spanning a specific word.
	 * @param word the word covered by the sought annotations
	 * @return an array holding the annotations
	 * @see de.uka.ipd.idaho.im.ImDocument#cleanupAnnotations()
	 */
	public ImAnnotation[] getAnnotationsSpanning(ImWord word) {
		return this.getAnnotationsSpanning(word, word);
	}
	
	/**
	 * Retrieve the annotations spanning a specific range of words. The two
	 * argument words have to belong to the same logical text stream for the
	 * result of this method to be meaningful.
	 * @param firstWord the first word covered by the sought annotations
	 * @param lastWord the last word covered by the sought annotations
	 * @return an array holding the annotations
	 * @see de.uka.ipd.idaho.im.ImDocument#cleanupAnnotations()
	 */
	public ImAnnotation[] getAnnotationsSpanning(ImWord firstWord, ImWord lastWord) {
		ImAnnotationCollectorList spanningAnnots = new ImAnnotationCollectorList();
		ArrayList firstWordPageAnnots = ((ArrayList) this.annotationsByPageId.get(new Integer(firstWord.pageId)));
		if (firstWordPageAnnots != null)
			for (int a = 0; a < firstWordPageAnnots.size(); a++) {
				ImDocumentAnnotation annot = ((ImDocumentAnnotation) firstWordPageAnnots.get(a));
				if (!firstWord.getTextStreamId().equals(annot.firstWord.getTextStreamId()))
					continue;
				if ((firstWord.pageId == annot.firstWord.pageId) && (firstWord.getTextStreamPos() < annot.firstWord.getTextStreamPos()))
					continue;
				if (annot.lastWord.pageId < lastWord.pageId)
					continue;
				if ((annot.lastWord.pageId == lastWord.pageId) && (annot.lastWord.getTextStreamPos() < lastWord.getTextStreamPos()))
					continue;
				spanningAnnots.add(annot);
			}
		return spanningAnnots.toAnnotArray();
	}
	
	/**
	 * Retrieve the annotations overlapping a specific word.
	 * @param word the word covered by the sought annotations
	 * @return an array holding the annotations
	 * @see de.uka.ipd.idaho.im.ImDocument#cleanupAnnotations()
	 */
	public ImAnnotation[] getAnnotationsOverlapping(ImWord word) {
		return this.getAnnotationsOverlapping(word, word);
	}
	
	/**
	 * Retrieve the annotations overlapping a specific range of words. The two
	 * argument words have to belong to the same logical text stream for the
	 * result of this method to be meaningful.
	 * @param firstWord the first word of the range overlapped by the sought annotations
	 * @param lastWord the last word of the range overlapped by the sought annotations
	 * @return an array holding the annotations
	 * @see de.uka.ipd.idaho.im.ImDocument#cleanupAnnotations()
	 */
	public ImAnnotation[] getAnnotationsOverlapping(ImWord firstWord, ImWord lastWord) {
		ImAnnotationCollectorList overlappingAnnots = new ImAnnotationCollectorList();
		for (int p = firstWord.pageId; p <= lastWord.pageId; p++) {
			ArrayList pageAnnots = ((ArrayList) this.annotationsByPageId.get(new Integer(p)));
			if (pageAnnots == null)
				continue;
			for (int a = 0; a < pageAnnots.size(); a++) {
				ImDocumentAnnotation annot = ((ImDocumentAnnotation) pageAnnots.get(a));
				if (!firstWord.getTextStreamId().equals(annot.firstWord.getTextStreamId()))
					continue;
				if ((firstWord.pageId == annot.lastWord.pageId) && (annot.lastWord.getTextStreamPos() < firstWord.getTextStreamPos()))
					continue;
				if ((annot.firstWord.pageId == lastWord.pageId) && (lastWord.getTextStreamPos() < annot.firstWord.getTextStreamPos()))
					continue;
				overlappingAnnots.addAnnot(annot);
			}
		}
		return overlappingAnnots.toAnnotArray();
	}
	
	private static class ImAnnotationCollectorList extends ArrayList {
		private HashSet deduplicator = new HashSet();
		boolean addAnnot(ImDocumentAnnotation annot) {
			return (this.deduplicator.add(annot) && this.add(annot));
		}
		ImAnnotation[] toAnnotArray() {
			ImAnnotation[] annots = ((ImAnnotation[]) this.toArray(new ImAnnotation[this.size()]));
			Arrays.sort(annots, nestingOrder);
			return annots;
		}
		private static Comparator nestingOrder = new Comparator() {
			public int compare(Object obj1, Object obj2) {
				ImDocumentAnnotation ida1 = ((ImDocumentAnnotation) obj1);
				ImDocumentAnnotation ida2 = ((ImDocumentAnnotation) obj2);
				int c = ImUtils.textStreamOrder.compare(ida1.firstWord, ida2.firstWord);
				if (c != 0)
					return c;
				c = ImUtils.textStreamOrder.compare(ida2.lastWord, ida1.lastWord);
				if (c != 0)
					return c;
				c = ((int) (ida1.createTime - ida2.createTime));
				if (c != 0)
					return c;
				return ida1.type.compareTo(ida2.type);
			}
			
		};
	}
	
	/**
	 * Retrieve the types of annotations present in this document. The
	 * annotation types are in lexicographical order.
	 * @return an array holding the annotation types
	 */
	public String[] getAnnotationTypes() {
		return ((String[]) this.annotationsByType.keySet().toArray(new String[this.annotationsByType.size()]));
	}
	
	/**
	 * Retrieve a word located in a given page. This method is a shortcut for
	 * <code>getPage(pageId).getWord(box)</code>.
	 * @param pageId the ID of the page the word is located on
	 * @param bounds the bounding box of the word
	 * @return the word at the specified position
	 */
	public ImWord getWord(int pageId, BoundingBox bounds) {
		ImPage page = this.getPage(pageId);
		return ((page == null) ? null : page.getWord(bounds));
	}
	
	/**
	 * Retrieve a word located in a given page. This method is a shortcut for
	 * <code>getPage(pageId).getWord(box)</code>.
	 * @param pageId the ID of the page the word is located on
	 * @param bounds the bounding box of the word
	 * @return the word at the specified position
	 */
	public ImWord getWord(int pageId, String bounds) {
		ImPage page = this.getPage(pageId);
		return ((page == null) ? null : page.getWord(bounds));
	}
	
	/**
	 * Retrieve a word located in a given page. The argument string has to be of
	 * the form '<code>&lt;pageId&gt;.&lt;bounds&gt;</code>', as returned by
	 * <code>ImWord.getLocalID()</code>.
	 * @param localId the document local ID of the word
	 * @return the word with the specified local ID
	 */
	public ImWord getWord(String localId) {
		if ((localId == null) || (localId.indexOf('.') == -1))
			return null;
		ImPage page = ((ImPage) this.pagesById.get(new Integer(localId.substring(0, localId.indexOf('.')))));
		return ((page == null) ? null : page.getWord(localId.substring(localId.indexOf('['))));
	}
	
	/**
	 * Retrieve all layout words that are the first of a logical text stream in
	 * the whole document.
	 * @return an array holding the text stream heads
	 */
	public ImWord[] getTextStreamHeads() {
		LinkedList tshs = new LinkedList();
		HashSet tsIDs = new HashSet();
		for (Iterator pit = this.pagesById.keySet().iterator(); pit.hasNext();) {
			Object pid = pit.next();
			ImPage page = ((ImPage) this.pagesById.get(pid));
			ImWord[] pTshs = page.getTextStreamHeads();
			for (int h = 0; h < pTshs.length; h++) {
				if (tsIDs.add(pTshs[h].getTextStreamId()))
					tshs.addLast(pTshs[h]);
			}
		}
		return ((ImWord[]) tshs.toArray(new ImWord[tshs.size()]));
	}
	
	/**
	 * Add a document listener to receive notification of changes to the
	 * document and its sub objects.
	 * @param dl the listener to add
	 */
	public void addDocumentListener(ImDocumentListener dl) {
		if (dl == null)
			return;
		if (this.listeners == null)
			this.listeners = new ArrayList(2);
		this.listeners.add(dl);
	}
	
	/**
	 * Remove a document listener.
	 * @param dl the listener to remove
	 */
	public void removeDocumentListener(ImDocumentListener dl) {
		if (this.listeners == null)
			return;
		this.listeners.remove(dl);
		if (this.listeners.isEmpty())
			this.listeners = null;
	}
	
	private ArrayList listeners = null;
	
	void notifyTypeChanged(ImObject object, String oldType) {
		if (this.listeners == null)
			return;
		for (int l = 0; l < this.listeners.size(); l++)
			((ImDocumentListener) this.listeners.get(l)).typeChanged(object, oldType);
	}
	
	void notifyAttributeChanged(ImObject object, String attributeName, Object oldValue) {
		if (this.listeners == null)
			return;
		for (int l = 0; l < this.listeners.size(); l++)
			((ImDocumentListener) this.listeners.get(l)).attributeChanged(object, attributeName, oldValue);
	}
	
	void notifyRegionAdded(ImRegion region) {
		if (this.listeners == null)
			return;
		for (int l = 0; l < this.listeners.size(); l++)
			((ImDocumentListener) this.listeners.get(l)).regionAdded(region);
	}
	
	void notifyRegionRemoved(ImRegion region) {
		if (this.listeners == null)
			return;
		for (int l = 0; l < this.listeners.size(); l++)
			((ImDocumentListener) this.listeners.get(l)).regionRemoved(region);
	}
	
	void notifyAnnotationAdded(ImAnnotation annotation) {
		if (this.listeners == null)
			return;
		for (int l = 0; l < this.listeners.size(); l++)
			((ImDocumentListener) this.listeners.get(l)).annotationAdded(annotation);
	}
	
	void notifyAnnotationRemoved(ImAnnotation annotation) {
		if (this.listeners == null)
			return;
		for (int l = 0; l < this.listeners.size(); l++)
			((ImDocumentListener) this.listeners.get(l)).annotationRemoved(annotation);
	}
}