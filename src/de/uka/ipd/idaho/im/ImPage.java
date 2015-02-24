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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.TreeSet;

import de.uka.ipd.idaho.gamta.util.imaging.BoundingBox;
import de.uka.ipd.idaho.gamta.util.imaging.PageImage;

/**
 * A page in an image markup document.
 * 
 * @author sautter
 */
public class ImPage extends ImRegion {
	
	/** the name of the attribute by which the page image can be retrieved in a generic manner */
	public static final String PAGE_IMAGE_ATTRIBUTE = "pageImage";
	
	private static class WordIndex {
		private static class WordIndexRegion extends LinkedList {
			void addWord(ImWord imw) {
				if (this.isEmpty() || (this.getLast() != imw))
					this.addLast(imw);
			}
			void removeWord(ImWord imw) {
				this.remove(imw);
			}
		}
		private int step;
		private WordIndexRegion[][] wirs;
		WordIndex(int width, int height) {
			this.step = Math.min((width / 8), (height / 11)); // one by one inch on an A4 page
			this.wirs = new WordIndexRegion[(width + this.step - 1) / this.step][];
			for (int c = 0; c < this.wirs.length; c++) {
				this.wirs[c] = new WordIndexRegion[(height + this.step - 1) / this.step];
				for (int r = 0; r < this.wirs[c].length; r++)
					this.wirs[c][r] = new WordIndexRegion();
			}
		}
		void addWord(ImWord imw) {
			for (int x = imw.bounds.left; x < imw.bounds.right; x += this.step) {
				for (int y = imw.bounds.top; y < imw.bounds.bottom; y += this.step)
					this.addWordForPoint(imw, x, y);
			}
			this.addWordForPoint(imw, imw.bounds.left, (imw.bounds.bottom-1));
			this.addWordForPoint(imw, (imw.bounds.right-1), imw.bounds.top);
			this.addWordForPoint(imw, (imw.bounds.right-1), (imw.bounds.bottom-1));
		}
		private void addWordForPoint(ImWord imw, int x, int y) {
			WordIndexRegion wir = this.getRegionAt(x, y);
			if (wir != null)
				wir.addWord(imw);
		}
		void removeWord(ImWord imw) {
			for (int x = imw.bounds.left; x < imw.bounds.right; x += this.step) {
				for (int y = imw.bounds.top; y < imw.bounds.bottom; y += this.step)
					this.removeWordForPoint(imw, x, y);
			}
			this.removeWordForPoint(imw, imw.bounds.left, (imw.bounds.bottom-1));
			this.removeWordForPoint(imw, (imw.bounds.right-1), imw.bounds.top);
			this.removeWordForPoint(imw, (imw.bounds.right-1), (imw.bounds.bottom-1));
		}
		private void removeWordForPoint(ImWord imw, int x, int y) {
			this.getRegionAt(x, y).removeWord(imw);
		}
		private WordIndexRegion getRegionAt(int x, int y) {
			int xi = (x / this.step);
			if ((xi < 0) || (this.wirs.length <= xi))
				return null;
			int yi = (y / this.step);
			if ((yi < 0) || (this.wirs[xi].length <= yi))
				return null;
			return this.wirs[xi][yi];
		}
		ImWord getWordAt(int x, int y) {
			WordIndexRegion wir = this.getRegionAt(x, y);
			if (wir == null)
				return null;
			for (Iterator wit = wir.iterator(); wit.hasNext();) {
				ImWord imw = ((ImWord) wit.next());
				if (contains(imw.bounds, x, y))
					return imw;
			}
			return null;
		}
		private static final boolean contains(BoundingBox box, int x, int y) {
			return ((box.left <= x) && (x < box.right) && (box.top <= y) && (y < box.bottom));
		}
		void clear() {
			for (int c = 0; c < this.wirs.length; c++) {
				for (int r = 0; r < this.wirs[c].length; r++)
					this.wirs[c][r].clear();
			}
		}
	}
	
	private final TreeSet words;
	private HashMap wordsByBounds = new HashMap();
	private WordIndex wordsByPoints;
	private final ArrayList regions = new ArrayList();
	
	/** Constructor (automatically adds the page to the argument document)
	 * @param doc the document the page belongs to
	 * @param pageId the ID of the page
	 * @param bounds the bounding box of the page
	 */
	public ImPage(ImDocument doc, int pageId, BoundingBox bounds) {
		super(doc, pageId, bounds, PAGE_TYPE);
		super.setType(PAGE_TYPE);
		this.words = new TreeSet(ImWord.getComparator(doc.orientation));
		this.wordsByPoints = new WordIndex((this.bounds.right - this.bounds.left), (this.bounds.bottom - this.bounds.top));
		doc.addPage(this);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImLayoutObject#getAttribute(java.lang.String, java.lang.Object)
	 */
	public Object getAttribute(String name, Object def) {
		if (PAGE_IMAGE_ATTRIBUTE.equals(name))
			return this.getImage();
		else return super.getAttribute(name, def);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImLayoutObject#getAttribute(java.lang.String)
	 */
	public Object getAttribute(String name) {
		if (PAGE_IMAGE_ATTRIBUTE.equals(name))
			return this.getImage();
		else return super.getAttribute(name);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImLayoutObject#setAttribute(java.lang.String, java.lang.Object)
	 */
	public Object setAttribute(String name, Object value) {
		if (PAGE_IMAGE_ATTRIBUTE.equals(name) && (value instanceof PageImage)) {
			PageImage opi = this.getPageImage();
			this.setImage((PageImage) value);
			return opi;
		}
		else return super.setAttribute(name, value);
	}
	
	/**
	 * Dispose of the page. This method clears all internal data structures, so
	 * the page is no longer usable after an invocation of this method.
	 */
	void dispose() {
		synchronized (this.regions) {
			this.regions.clear();
		}
		synchronized (this.words) {
			this.words.clear();
			this.wordsByBounds.clear();
			this.wordsByPoints.clear();
		}
	}
	
	/**
	 * This implementation does not have any effect, so its corresponding getter
	 * always returns 'page'.
	 * @see de.uka.ipd.idaho.im.ImObject#setType(java.lang.String)
	 */
	public void setType(String type) {}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImLayoutObject#getPage()
	 */
	public ImPage getPage() {
		return this;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImRegion#setPage(de.uka.ipd.idaho.im.ImPage)
	 */
	public void setPage(ImPage page) {}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImLayoutObject#getImage()
	 */
	public PageImage getImage() {
		return this.getPageImage();
	}
	
	/**
	 * Replace the image of the page. This method stores the argument page
	 * image and notifies listeners.
	 * @param pi the new page image
	 * @return true if the image was stored successfully
	 */
	public boolean setImage(PageImage pi) {
		try {
			PageImage opi = this.getPageImage();
			PageImage.storePageImage(this.getDocument().docId, this.pageId, pi);
			this.getDocument().notifyAttributeChanged(this, PAGE_IMAGE_ATTRIBUTE, opi);
			return true;
		}
		catch (IOException ioe) {
			ioe.printStackTrace(System.out);
			return false;
		}
	}
	
	/**
	 * Add a word to the page. The word is automatically associated with the
	 * page. If the page ID of the word does not match the ID of the page, this
	 * method throws an <code>IllegalArgumentException</code>.
	 * @param word the word to add
	 */
	public void addWord(ImWord word) {
		if (word.pageId != this.pageId)
			throw new IllegalArgumentException("Page ID mismatch, " + word.pageId + " != " + this.pageId);
		synchronized (this.words) {
			this.words.add(word);
			this.wordsByBounds.put(word.bounds.toString(), word);
			this.wordsByPoints.addWord(word);
		}
		word.setPage(this);
		this.getDocument().notifyRegionAdded(word);
	}
	
	/**
	 * Remove a word from the page. If any annotations depend on the word, the
	 * <code>removeAnnots</code> boolean decides how this method behaves: if it
	 * is <code>true</code>, the annotations are removed as well, and this
	 * method returns <code>true</code>; if it is <code>false</code>, this
	 * method does not remove anything and returns <code>false</code>. If the
	 * argument word has text stream predecessor and successor set, the latter
	 * two are connected.
	 * @param word the word to remove
	 * @param removeAnnots remove annotations depending on the word to remove?
	 * @return true if the argument word was removed, false otherwise
	 */
	public boolean removeWord(ImWord word, boolean removeAnnots) {
		if (word.getPage() != this)
			return false;
		
		//	get annotations starting or ending at word to remove
		ImAnnotation[] wordAnnots = this.getDocument().getAnnotations(word);
		
		//	if we cannot remove dependent annotations, we cannot remove the word, either
		if ((wordAnnots.length != 0) && !removeAnnots)
			return false;
		
		//	remove dependent annotations
		for (int a = 0; a < wordAnnots.length; a++)
			this.getDocument().removeAnnotation(wordAnnots[a]);
		
		//	cut word out of logical text stream
		ImWord pWord = word.getPreviousWord();
		ImWord nWord = word.getNextWord();
		if (pWord != null)
			pWord.setNextWord(nWord);
		else if (nWord != null)
			nWord.setPreviousWord(null);
		
		//	remove word
		synchronized (this.words) {
			this.wordsByPoints.removeWord(word);
			this.wordsByBounds.remove(word.bounds.toString());
			this.words.remove(word);
		}
		
		//	detach word & notify listeners
		word.setPage(null);
		this.getDocument().notifyRegionRemoved(word);
		
		//	finally ...
		return true;
	}
	
	/**
	 * Retrieve the layout word with a given bounding box.
	 * @param bounds the bounding box of the sought word
	 * @return the word with the given bounding box
	 */
	public ImWord getWord(BoundingBox bounds) {
		return this.getWord(bounds.toString());
	}
	
	/**
	 * Retrieve the layout word with a given bounding box in its string
	 * representation.
	 * @param bounds the bounding box of the sought word
	 * @return the word with the given bounding box
	 */
	public ImWord getWord(String bounds) {
		return ((ImWord) this.wordsByBounds.get(bounds));
	}
	
	/**
	 * Retrieve the word at a given point. If there is no word at the argument
	 * point, this method returns null.
	 * @param x the x coordinate of the point
	 * @param y the y coordinate of the point
	 * @return the word at the argument point
	 */
	public ImWord getWordAt(int x, int y) {
		return this.wordsByPoints.getWordAt(x, y);
	}
	
	/**
	 * Retrieve the layout words in the page.
	 * @return an array holding the words
	 */
	public ImWord[] getWords() {
		return ((ImWord[]) this.words.toArray(new ImWord[this.words.size()]));
	}
	
	/**
	 * Retrieve all words for the page lying inside a given bounding box. This
	 * includes all word whose center lies inside the argument bounding box,
	 * i.e., ones whose bounding box overlaps more than half way with the
	 * argument box in both dimensions. Words are sorted according to the
	 * orientation of the document, e.g. left to right and top to bottom for
	 * text in Latin scripts. If the argument box intersects with a block of
	 * text, the returned array may return words that belong to the same
	 * logical text stream, but appear to stand predecessors or successors lie outside
	 * the box.
	 * @param box the bounding box whose words to get
	 * @return an array holding the words lying inside the argument box
	 */
	public ImWord[] getWordsInside(BoundingBox box) {
		LinkedList wi = new LinkedList();
		for (Iterator wit = this.words.iterator(); wit.hasNext();) {
			ImWord imw = ((ImWord) wit.next());
			if ((imw.centerX >= box.left) && (imw.centerX < box.right) && (imw.centerY >= box.top) && (imw.centerY < box.bottom))
				wi.addLast(imw);
		}
		return ((ImWord[]) wi.toArray(new ImWord[wi.size()]));
	}
	
	/**
	 * Retrieve all layout words that are the first of a logical text stream in
	 * the page. This includes both words that do not have a predecessor and
	 * ones whose predecessor lies on a different page.
	 * @return an array holding the text stream heads
	 */
	public ImWord[] getTextStreamHeads() {
		LinkedList tshs = new LinkedList();
		for (Iterator wit = this.words.iterator(); wit.hasNext();) {
			ImWord imw = ((ImWord) wit.next());
			if ((imw.getPreviousWord() == null) || (imw.getPreviousWord().pageId != imw.pageId))
				tshs.addLast(imw);
		}
		return ((ImWord[]) tshs.toArray(new ImWord[tshs.size()]));
	}
	
	/**
	 * Add a region to the page. The region is automatically associated with
	 * the page. If the page ID of the region does not match the ID of the
	 * page, this method throws an <code>IllegalArgumentException</code>. If
	 * the argument region is an <code>ImWord</code>, this method loops through
	 * to the <code>addWord()</code> method. If the argument regions is an
	 * <code>ImPage</code>, this method does nothing.
	 * @param region the region to add
	 */
	public void addRegion(ImRegion region) {
		if (region.pageId != this.pageId)
			throw new IllegalArgumentException("Page ID mismatch, " + region.pageId + " != " + this.pageId);
		if (region instanceof ImWord)
			this.addWord((ImWord) region);
		else if (region instanceof ImPage) {}
		else {
			synchronized (this.regions) {
				this.regions.add(region);
			}
			region.setPage(this);
			this.getDocument().notifyRegionAdded(region);
		}
	}
	
	/**
	 * Remove a region from the page. If the argument region is an
	 * <code>ImWord</code>, this method loops through to the
	 * <code>removeWord()</code> method. If the argument regions is an
	 * <code>ImPage</code>, this method does nothing.
	 * @param region the region to remove
	 */
	public void removeRegion(ImRegion region) {
		if (region instanceof ImWord)
			this.removeWord(((ImWord) region), false);
		else if (region instanceof ImPage) {}
		else if (region.getPage() == this) {
			synchronized (this.regions) {
				this.regions.remove(region);
			}
			region.setPage(null);
			this.getDocument().notifyRegionRemoved(region);
		}
	}
	
	/**
	 * Retrieve the regions (e.g. columns and blocks) that include a given
	 * bounding box. Regions higher up in the hierarchy appear before the ones
	 * nested in them in the returned array. In addition, they are sorted
	 * according to the orientation of the document, e.g. left to right and top
	 * to bottom for text in Latin scripts. If <code>fuzzy</code> is set to
	 * <code>true</code>, the result also includes all regions that contain the
	 * center point of the argument box, even if they do not fully include the
	 * argument box.
	 * @param box the bounding box whose parent regions to retrieve
	 * @param fuzzy do fuzzy matching?
	 * @return an array holding the regions that include the argument box
	 */
	public ImRegion[] getRegionsIncluding(BoundingBox box, boolean fuzzy) {
		LinkedList rs = new LinkedList();
		for (Iterator rit = this.regions.iterator(); rit.hasNext();) {
			ImRegion imr = ((ImRegion) rit.next());
			if (imr.bounds.includes(box, fuzzy))
				rs.add(imr);
		}
		return ((ImRegion[]) rs.toArray(new ImRegion[rs.size()]));
	}
	
	/**
	 * Retrieve the regions (e.g. columns and blocks) that lie inside a given
	 * bounding box. Regions higher up in the hierarchy appear before the ones
	 * nested in them in the returned array. In addition, they are sorted
	 * according to the orientation of the document, e.g. left to right and top
	 * to bottom for text in Latin scripts. If <code>fuzzy</code> is set to
	 * <code>true</code>, the result also includes all regions whose center
	 * point lies inside the argument box, even if they do not fully inside the
	 * argument box.
	 * @param box the bounding box whose child regions to retrieve
	 * @param fuzzy do fuzzy matching?
	 * @return an array holding the regions that lie inside the argument box
	 */
	public ImRegion[] getRegionsInside(BoundingBox box, boolean fuzzy) {
		LinkedList rs = new LinkedList();
		for (Iterator rit = this.regions.iterator(); rit.hasNext();) {
			ImRegion imr = ((ImRegion) rit.next());
			if (box.includes(imr.bounds, fuzzy))
				rs.add(imr);
		}
		return ((ImRegion[]) rs.toArray(new ImRegion[rs.size()]));
	}
	
	/**
	 * Retrieve the regions (e.g. columns and blocks) representing the layout
	 * of the page. This method returns all regions, regardless of nesting.
	 * However, regions higher up in the hierarchy appear before the ones
	 * nested in them in the returned array. In addition, they are sorted
	 * according to the orientation of the document, e.g. left to right and top
	 * to bottom for text in Latin scripts.
	 * @return an array holding the regions representing the layout of the page
	 */
	public ImRegion[] getRegions() {
		return this.getRegions(null);
	}
	
	/**
	 * Retrieve the regions (e.g. columns or blocks) representing some level of
	 * the layout of the page. This method returns all regions of the argument
	 * type, regardless of nesting. However, regions higher up in the hierarchy
	 * appear before the ones nested in them in the returned array. In
	 * addition, they are sorted according to the orientation of the document,
	 * e.g. left to right and top to bottom for text in Latin scripts.
	 * @param type the type of regions to return
	 * @return an array holding the regions representing the layout of the page
	 */
	public ImRegion[] getRegions(String type) {
		Collection regions;
		if (type == null)
			regions = this.regions;
		else if (WORD_ANNOTATION_TYPE.equals(type))
			regions = this.words;
		else {
			regions = new ArrayList();
			for (int r = 0; r < this.regions.size(); r++) {
				if (type.equals(((ImRegion) this.regions.get(r)).getType()))
					regions.add(this.regions.get(r));
			}
		}
		return ((ImRegion[]) regions.toArray(new ImRegion[regions.size()]));
	}
	
	/**
	 * Retrieve the types of regions present in this page. The region types are
	 * in lexicographical order.
	 * @return an array holding the region types
	 */
	public String[] getRegionTypes() {
		TreeSet regionTypes = new TreeSet();
		for (int r = 0; r < this.regions.size(); r++)
			regionTypes.add(((ImRegion) this.regions.get(r)).getType());
		return ((String[]) regionTypes.toArray(new String[regionTypes.size()]));
	}
	
	/**
	 * Retrieve the supplements present in the page.
	 * @return an array holding the supplements
	 */
	public ImSupplement[] getSupplements() {
		ImSupplement[] docSupplements = this.getDocument().getSupplements();
		ArrayList pageSupplements = new ArrayList();
		for (int s = 0; s < docSupplements.length; s++) {
			if (("" + this.pageId).equals(docSupplements[s].getAttribute(PAGE_ID_ATTRIBUTE, "").toString()))
				pageSupplements.add(docSupplements[s]);
		}
		return ((ImSupplement[]) pageSupplements.toArray(new ImSupplement[pageSupplements.size()]));
	}
}