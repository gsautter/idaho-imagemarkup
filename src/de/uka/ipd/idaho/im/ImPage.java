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
package de.uka.ipd.idaho.im;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeMap;

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
		private static class WordIndexRegion {
			/* using our own little list gets us rid of the overhead required 
			 * in general purpose collections and also facilitates more code
			 * inlining by the compiler, improving overall performance */
//			private static int instanceCount = 0;
//			private static int arrayDuplications = 0;
//			private ImWord[] words = new ImWord[8];
			private ImWord[] words = new ImWord[16]; // looks like the better initial size, based on several documents
			private int wordCount = 0;
//			WordIndexRegion() {
//				instanceCount++;
//			}
			void addWord(ImWord imw) {
				for (int w = 0; w < this.wordCount; w++) {
					if (this.words[w] == imw)
						return;
				}
				if (this.wordCount == this.words.length) {
					ImWord[] words = new ImWord[this.words.length * 2];
					System.arraycopy(this.words, 0, words, 0, this.words.length);
					this.words = words;
//					arrayDuplications++;
//					System.out.println("WordIndexRegion: enlarged array to " + this.words.length + ", " + arrayDuplications + " enlargement in " + instanceCount + " instances");
				}
				this.words[this.wordCount++] = imw;
			}
			void removeWord(ImWord imw) {
				for (int w = 0; w < this.wordCount; w++)
					if (this.words[w] == imw) {
						System.arraycopy(this.words, (w+1), this.words, w, (this.wordCount - (w+1)));
						this.wordCount--;
						this.words[this.wordCount] = null;
						break;
					}
			}
			void clear() {
				Arrays.fill(this.words, null);
				this.wordCount = 0;
			}
			ImWord wordAt(int x, int y) {
				for (int w = 0; w < this.wordCount; w++) {
					if (contains(this.words[w].bounds, x, y))
						return this.words[w];
				}
				return null;
			}
		}
		
		private int step;
		private WordIndexRegion[][] wirs;
		WordIndex(int width, int height) {
			this.step = Math.min((width / 8), (height / 11)); // one by one inch on an A4 page
			this.wirs = new WordIndexRegion[(width + this.step - 1) / this.step][(height + this.step - 1) / this.step];
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
			WordIndexRegion wir = this.getRegionAt(x, y, true);
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
			WordIndexRegion wir = this.getRegionAt(x, y, false);
			if (wir != null)
				wir.removeWord(imw);
		}
		private WordIndexRegion getRegionAt(int x, int y, boolean create) {
			int xi = (x / this.step);
			if ((xi < 0) || (this.wirs.length <= xi))
				return null;
			int yi = (y / this.step);
			if ((yi < 0) || (this.wirs[xi].length <= yi))
				return null;
			if ((this.wirs[xi][yi] == null) && create)
				this.wirs[xi][yi] = new WordIndexRegion();
			return this.wirs[xi][yi];
		}
		ImWord getWordAt(int x, int y) {
			WordIndexRegion wir = this.getRegionAt(x, y, false);
			if (wir == null)
				return null;
			return wir.wordAt(x, y);
		}
		private static final boolean contains(BoundingBox box, int x, int y) {
			return ((box.left <= x) && (x < box.right) && (box.top <= y) && (y < box.bottom));
		}
		void clear() {
			for (int c = 0; c < this.wirs.length; c++)
				if (this.wirs[c] != null) {
					for (int r = 0; r < this.wirs[c].length; r++)
						if (this.wirs[c][r] != null) {
							this.wirs[c][r].clear();
							this.wirs[c][r] = null;
						}
				}
		}
	}
	
	private class ImPageWordList {
		private ImWord[] words = new ImWord[128];
		private int wordCount = 0;
		private HashSet contained = new HashSet();
		private HashSet removed = new HashSet();
		private int addCount = 0;
		private int cleanAddCount = 0;
		void addWord(ImWord word) {
			if (word == null)
				return;
			if (this.contained.contains(word))
				return;
			this.contained.add(word);
			if (this.removed.remove(word)) {
				if (this.wordCount != (this.contained.size() + this.removed.size()))
					System.out.println("FUCK, array " + this.wordCount + " != (contained " + this.contained.size() + " + removed " + this.removed.size() + ") on adding " + word.getType());
				return; // this one is was flagged for removal, but still in the array, no need to add to again (also, addition has been counter before)
			}
			if (this.wordCount == this.words.length) {
				ImWord[] words = new ImWord[this.words.length * 2];
				System.arraycopy(this.words, 0, words, 0, this.words.length);
				this.words = words;
			}
			this.words[this.wordCount++] = word;
			this.addCount++;
			if (this.wordCount != (this.contained.size() + this.removed.size()))
				System.out.println("FUCK, array " + this.wordCount + " != (contained " + this.contained.size() + " + removed " + this.removed.size() + ") on adding " + word.getType());
		}
		int size() {
			return this.contained.size();
		}
		ImWord getWord(int index) {
			this.ensureSorted();
			return this.words[index];
		}
		void removeWord(ImWord word) {
			if (word == null)
				return;
			if (this.contained.remove(word))
				this.removed.add(word);
			if (this.wordCount != (this.contained.size() + this.removed.size()))
				System.out.println("FUCK, array " + this.wordCount + " != (contained " + this.contained.size() + " + removed " + this.removed.size() + ") on removing " + word.getType());
		}
		void clear() {
			Arrays.fill(this.words, 0, this.wordCount, null); // free up references to help GC
			this.wordCount = 0;
			this.contained.clear();
			this.removed.clear();
		}
		ImWord[] toWordArray() {
			this.ensureSorted();
			return Arrays.copyOfRange(this.words, 0, this.wordCount);
		}
		ImRegion[] toRegionArray() {
			this.ensureSorted();
			return Arrays.copyOfRange(this.words, 0, this.wordCount, ImRegion[].class);
		}
		private void ensureSorted() {
			this.ensureClean();
			if (this.cleanAddCount == this.addCount)
				return;
			/* TODOnot if order and types unmodified, we can even save sorting the whole list:
			 * - sort only added annotations ...
			 * - ... and then merge them into main list in single pass
			 * ==> but then, TimSort already does pretty much that ... */
			Arrays.sort(this.words, 0, this.wordCount, wordOrder);
			this.cleanAddCount = this.addCount;
		}
		private void ensureClean() {
			if (this.removed.isEmpty())
				return;
			int removed = 0;
			for (int w = 0; w < this.wordCount; w++) {
				if (this.removed.contains(this.words[w]))
					removed++;
				else if (removed != 0)
					this.words[w - removed] = this.words[w];
			}
			Arrays.fill(this.words, (this.wordCount - removed), this.wordCount, null); // free up references to help GC
			this.wordCount -= removed;
			this.removed.clear();
		}
	}
	
	private class ImPageRegionList {
		private ImRegion[] regions = new ImRegion[16];
		private int regionCount = 0;
		private HashSet contained = new HashSet();
		private HashSet removed = new HashSet();
		private int addCount = 0;
		private int cleanAddCount = 0;
		void addRegion(ImRegion region) {
			if (region == null)
				return;
			if (this.contained.contains(region))
				return;
			this.contained.add(region);
			if (this.removed.remove(region)) {
				if (this.regionCount != (this.contained.size() + this.removed.size()))
					System.out.println("FUCK, array " + this.regionCount + " != (contained " + this.contained.size() + " + removed " + this.removed.size() + ") on adding " + region.getType());
				return; // this one is was flagged for removal, but still in the array, no need to add to again (also, addition has been counter before)
			}
			if (this.regionCount == this.regions.length) {
				ImRegion[] regions = new ImRegion[this.regions.length * 2];
				System.arraycopy(this.regions, 0, regions, 0, this.regions.length);
				this.regions = regions;
			}
			this.regions[this.regionCount++] = region;
			this.addCount++;
			if (this.regionCount != (this.contained.size() + this.removed.size()))
				System.out.println("FUCK, array " + this.regionCount + " != (contained " + this.contained.size() + " + removed " + this.removed.size() + ") on adding " + region.getType());
		}
		boolean isEmpty() {
			return this.contained.isEmpty();
		}
		int size() {
			return this.contained.size();
		}
		ImRegion getRegion(int index) {
			this.ensureSorted();
			return this.regions[index];
		}
		void removeRegion(ImRegion region) {
			if (region == null)
				return;
			if (this.contained.remove(region)) {
				this.removed.add(region);
			}
			if (this.regionCount != (this.contained.size() + this.removed.size()))
				System.out.println("FUCK, array " + this.regionCount + " != (contained " + this.contained.size() + " + removed " + this.removed.size() + ") on removing " + region.getType());
		}
		void clear() {
			Arrays.fill(this.regions, 0, this.regionCount, null); // free up references to help GC
			this.regionCount = 0;
			this.contained.clear();
			this.removed.clear();
		}
		ImRegion[] toRegionArray() {
			this.ensureSorted();
			return Arrays.copyOfRange(this.regions, 0, this.regionCount);
		}
		private void ensureSorted() {
			this.ensureClean();
			if (this.cleanAddCount == this.addCount)
				return;
			/* TODOnot if order and types unmodified, we can even save sorting the whole list:
			 * - sort only added annotations ...
			 * - ... and then merge them into main list in single pass
			 * ==> but then, TimSort already does pretty much that ... */
			Arrays.sort(this.regions, 0, this.regionCount, regionOrder);
			this.cleanAddCount = this.addCount;
		}
		private void ensureClean() {
			if (this.removed.isEmpty())
				return;
			int removed = 0;
			for (int a = 0; a < this.regionCount; a++) {
				if (this.removed.contains(this.regions[a]))
					removed++;
				else if (removed != 0)
					this.regions[a - removed] = this.regions[a];
			}
			Arrays.fill(this.regions, (this.regionCount - removed), this.regionCount, null); // free up references to help GC
			this.regionCount -= removed;
			this.removed.clear();
		}
	}
	
	/* sort regions by area, as this
	 * (a) reflects region nesting and
	 * (b) gives a total order independent of position for disjoint or intersecting regions
	 */
	private static final Comparator sizeRegionOrder = new Comparator() {
		public int compare(Object obj1, Object obj2) {
			return (((ImRegion) obj2).bounds.getArea() - ((ImRegion) obj1).bounds.getArea());
		}
	};
	
	/* TODO keep regions sorted according to document reading order contract
	 * This might not even be possible with wildly intersecting and overlapping
	 * regions, however, as a total ordering as required by sort routines in
	 * such cases might be impossible to define even theoretically.
	 */
//	private static final Comparator leftRightRegionOrder = new Comparator() {
//		public int compare(Object obj1, Object obj2) {
//			return 0;
//		}
//	};
//	
//	private static final Comparator rightLeftRegionOrder = new Comparator() {
//		public int compare(Object obj1, Object obj2) {
//			return 0;
//		}
//	};
	
	private ImPageWordList words = new ImPageWordList();
	private Comparator wordOrder;
	private HashMap wordsByBounds = new HashMap();
	private WordIndex wordsByPoints = null;
	private ImWord[] textStreamHeads = null;
	private ImWord[] textStreamTails = null;
	
	private ImPageRegionList regions = new ImPageRegionList();
	private TreeMap regionsByType = new TreeMap();
	private Comparator regionOrder;
	
	private int imageDpi = -1;
	
	/** Constructor (automatically adds the page to the argument document)
	 * @param doc the document the page belongs to
	 * @param pageId the ID of the page
	 * @param bounds the bounding box of the page
	 */
	public ImPage(ImDocument doc, int pageId, BoundingBox bounds) {
		super(doc, pageId, bounds, PAGE_TYPE);
		this.wordOrder = ImWord.getComparator(doc.orientation);
//		this.wordsByPoints = new WordIndex((this.bounds.right - this.bounds.left), (this.bounds.bottom - this.bounds.top));
		this.regionOrder = sizeRegionOrder;
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
		return this.getAttribute(name, null);
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
			this.regionsByType.clear();
		}
		synchronized (this.words) {
			this.words.clear();
			this.wordsByBounds.clear();
			if (this.wordsByPoints != null)
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
	 * @see de.uka.ipd.idaho.im.ImObject#getLocalID()
	 */
	public String getLocalID() {
		return ("" + this.pageId);
	}
	
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
		PageImage pi;
		try {
			pi = this.getDocument().getPageImage(this.pageId);
		}
		catch (Exception e) {
			pi = super.getPageImage();
		}
		this.imageDpi = pi.currentDpi;
		return pi;
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
			this.getDocument().storePageImage(pi, this.pageId);
			this.imageDpi = pi.currentDpi;
			this.getDocument().notifyAttributeChanged(this, PAGE_IMAGE_ATTRIBUTE, opi);
			return true;
		}
		catch (IOException ioe) {
			ioe.printStackTrace(System.out);
			return false;
		}
	}
	
	/**
	 * Retrieve the resolution of the page image. This method exists to allow
	 * IO facilities to implement it more efficiently than by loading the page
	 * image for merely accessing its DPI property.
	 * @return the resolution of the page image
	 */
	public int getImageDPI() {
		if (this.imageDpi < 0)
			this.imageDpi = this.getImage().currentDpi;
		return this.imageDpi;
	}
	
	/**
	 * Set the resolution of the page image. This method exists to allow IO
	 * facilities to inject the resolution without having to load the page
	 * image proper.
	 * @param imageDpi the resolution of the page image
	 */
	public void setImageDPI(int imageDpi) {
		this.imageDpi = imageDpi;
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
			this.words.addWord(word);
			this.wordsByBounds.put(word.bounds.toString(), word);
			if (this.wordsByPoints != null)
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
			if (this.wordsByPoints != null)
				this.wordsByPoints.removeWord(word);
			this.wordsByBounds.remove(word.bounds.toString());
			this.words.removeWord(word);
		}
		
		//	detach word & notify listeners
		this.getDocument().notifyRegionRemoved(word);
		word.setPage(null);
		
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
		synchronized (this.words) {
			if (this.wordsByPoints == null) /* need to check synchronously, as otherwise half empty index might get used */ {
				this.wordsByPoints = new WordIndex((this.bounds.right - this.bounds.left), (this.bounds.bottom - this.bounds.top));
				for (int w = 0; w < this.words.size(); w++)
					this.wordsByPoints.addWord(this.words.getWord(w));
			}
		}
		return this.wordsByPoints.getWordAt(x, y);
		/*
TODO Try switching point access support for IM words in page to single array:
- sort strictly top-down for top, bottom-up for bottom
- binary search clicked Y coordinate in said array ...
- ... and then scan backward and forward from that position ...
- ... until match found for X coordinate or Y coordinate no longer covered
==> uses only single array (way fewer objects, and only one empty tail) ...
==> ... and only one reference per word
==> should be similarly fast, as tiles also need linear scan
		 */
	}
	
	/**
	 * Retrieve the number of words in the page.
	 * @return the number of words
	 */
	public int getWordCount() {
		return this.words.size();
	}
	
	/**
	 * Retrieve the layout words in the page.
	 * @return an array holding the words
	 */
	public ImWord[] getWords() {
		return this.words.toWordArray();
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
		ImWordCollectorList wi = new ImWordCollectorList();
		for (int w = 0; w < this.words.size(); w++) {
			ImWord imw = this.words.getWord(w);
			if ((imw.centerX >= box.left) && (imw.centerX < box.right) && (imw.centerY >= box.top) && (imw.centerY < box.bottom))
				wi.addWord(imw);
		}
		return wi.toWordArray();
	}
	
	private class ImWordCollectorList {
		/* using our own little list gets us rid of the overhead required in
		 * general purpose collections and also facilitates more code inlining
		 * by the compiler, improving overall performance */
		private ImWord[] words = new ImWord[16];
		private int wordCount = 0;
		void addWord(ImWord word) {
			if (this.wordCount == this.words.length) {
				ImWord[] words = new ImWord[this.words.length * 2];
				System.arraycopy(this.words, 0, words, 0, this.words.length);
				this.words = words;
			}
			this.words[this.wordCount++] = word;
		}
		ImWord[] toWordArray() {
			return ((this.wordCount < this.words.length) ? Arrays.copyOf(this.words, this.wordCount) : this.words);
		}
	}
	
	/**
	 * Retrieve all layout words that are the first of a logical text stream in
	 * the page. This includes both words that do not have a predecessor and
	 * ones whose predecessor lies on a different page.
	 * @return an array holding the text stream heads
	 */
	public ImWord[] getTextStreamHeads() {
		this.ensureTextStreamEnds();
		ImWord[] tshs = new ImWord[this.textStreamHeads.length];
		System.arraycopy(this.textStreamHeads, 0, tshs, 0, tshs.length);
		return tshs;
	}
	
	/**
	 * Retrieve all layout words that are the last of a logical text stream in
	 * the page. This includes both words that do not have a successor and ones
	 * whose successor lies on a different page.
	 * @return an array holding the text stream tails
	 */
	public ImWord[] getTextStreamTails() {
		this.ensureTextStreamEnds();
		ImWord[] tsts = new ImWord[this.textStreamTails.length];
		System.arraycopy(this.textStreamTails, 0, tsts, 0, tsts.length);
		return tsts;
	}
	
	void invalidateTextStreamEnds() {
		this.textStreamHeads = null;
		this.textStreamTails = null;
		if (this.getDocument() != null)
			this.getDocument().invalidateTextStreamEnds();
	}
	
	private void ensureTextStreamEnds() {
		if ((this.textStreamHeads != null) && (this.textStreamTails != null))
			return;
		ImWordCollectorList tshs = new ImWordCollectorList();
		ImWordCollectorList tsts = new ImWordCollectorList();
		for (int w = 0; w < this.words.size(); w++) {
			ImWord imw = this.words.getWord(w);
			if ((imw.getPreviousWord() == null) || (imw.getPreviousWord().pageId != imw.pageId) || (imw.getPreviousWord().getPage() == null))
				tshs.addWord(imw);
			if ((imw.getNextWord() == null) || (imw.getNextWord().pageId != imw.pageId) || (imw.getNextWord().getPage() == null))
				tsts.addWord(imw);
		}
		this.textStreamHeads = tshs.toWordArray();
		this.textStreamTails = tsts.toWordArray();
	}
	
	void layoutObjectTypeChanged(ImLayoutObject imlo, String oldType) {
		if (imlo instanceof ImRegion) 
			synchronized (this.regions) {
				ImPageRegionList imrs = this.getRegionList(oldType, false);
				if (imrs != null) {
					imrs.removeRegion((ImRegion) imlo);
					if (imrs.isEmpty())
						this.regionsByType.remove(oldType);
				}
				imrs = this.getRegionList(imlo.getType(), true);
				imrs.addRegion((ImRegion) imlo);
			}
	}
	
	private ImPageRegionList getRegionList(String type, boolean create) {
		if (type == null)
			return this.regions;
		ImPageRegionList regions = ((ImPageRegionList) this.regionsByType.get(type));
		if ((regions == null) && create) {
			regions = new ImPageRegionList();
			this.regionsByType.put(type, regions);
		}
		return regions;
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
				this.regions.addRegion(region);
				this.getRegionList(region.getType(), true).addRegion(region);
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
				this.regions.removeRegion(region);
				ImPageRegionList imrs = this.getRegionList(region.getType(), false);
				if (imrs != null) {
					imrs.removeRegion(region);
					if (imrs.isEmpty())
						this.regionsByType.remove(region.getType());
				}
			}
			this.getDocument().notifyRegionRemoved(region);
			region.setPage(null);
		}
	}
	
	/**
	 * Retrieve the regions (e.g. columns and blocks) that include a given
	 * bounding box. Regions higher up in the hierarchy appear before the ones
	 * nested in them in the returned array. If <code>fuzzy</code> is set to
	 * <code>true</code>, the result also includes all regions that contain the
	 * center point of the argument box, even if they do not fully include the
	 * argument box.
	 * @param box the bounding box whose parent regions to retrieve
	 * @param fuzzy do fuzzy matching?
	 * @return an array holding the regions that include the argument box
	 */
	public ImRegion[] getRegionsIncluding(BoundingBox box, boolean fuzzy) {
		return this.getRegionsIncluding(null, box, fuzzy);
	}
	
	/**
	 * Retrieve the regions (e.g. columns or blocks) of a given type that
	 * include a given bounding box. Regions higher up in the hierarchy appear
	 * before the ones nested in them in the returned array. If <code>fuzzy</code>
	 * is set to <code>true</code>, the result also includes all regions that
	 * contain the center point of the argument box, even if they do not fully
	 * include the argument box.
	 * @param type the type of the sought regions
	 * @param box the bounding box whose parent regions to retrieve
	 * @param fuzzy do fuzzy matching?
	 * @return an array holding the regions of the argument type that include
	 *            the argument box
	 */
	public ImRegion[] getRegionsIncluding(String type, BoundingBox box, boolean fuzzy) {
		ImDocument doc = this.getDocument();
		if (ImDocument.TRACK_INSTANCES && (doc != null)) doc.accessed();
		ImRegionCollectorList rs = new ImRegionCollectorList();
		ImPageRegionList imrs = this.getRegionList(type, false);
		if (imrs == null)
			return new ImRegion[0];
		for (int r = 0; r < imrs.size(); r++) {
			ImRegion imr = imrs.getRegion(r);
			if (imr.bounds.includes(box, fuzzy))
				rs.addRegion(imr);
			if (!fuzzy && (imr.bounds.getArea() < box.getArea()))
				break; // all to come is smaller than argument box, so we can stop right here if full inclusion required
		}
		return rs.toRegionArray();
	}
	
	/**
	 * Retrieve the regions (e.g. columns and blocks) that lie inside a given
	 * bounding box. Regions higher up in the hierarchy appear before the ones
	 * nested in them in the returned array. If <code>fuzzy</code> is set to
	 * <code>true</code>, the result also includes all regions whose center
	 * point lies inside the argument box, even if they do not fully lie inside
	 * the argument box.
	 * @param box the bounding box whose child regions to retrieve
	 * @param fuzzy do fuzzy matching?
	 * @return an array holding the regions that lie inside the argument box
	 */
	public ImRegion[] getRegionsInside(BoundingBox box, boolean fuzzy) {
		return this.getRegionsInside(null, box, fuzzy);
	}
	
	/**
	 * Retrieve the regions (e.g. columns or blocks) of a given type that lie
	 * inside a given bounding box. Regions higher up in the hierarchy appear
	 * before the ones nested in them in the returned array. If <code>fuzzy</code>
	 * is set to <code>true</code>, the result also includes all regions whose
	 * center point lies inside the argument box, even if they do not fully lie
	 * inside the argument box.
	 * @param type type the type of the sought regions
	 * @param box the bounding box whose child regions to retrieve
	 * @param fuzzy do fuzzy matching?
	 * @return an array holding the regions that lie inside the argument box
	 */
	public ImRegion[] getRegionsInside(String type, BoundingBox box, boolean fuzzy) {
		ImDocument doc = this.getDocument();
		if (ImDocument.TRACK_INSTANCES && (doc != null)) doc.accessed();
		ImRegionCollectorList rs = new ImRegionCollectorList();
		ImPageRegionList imrs = this.getRegionList(type, false);
		if (imrs == null)
			return new ImRegion[0];
		for (int r = 0; r < imrs.size(); r++) {
			ImRegion imr = imrs.getRegion(r);
			if (box.includes(imr.bounds, fuzzy))
				rs.addRegion(imr);
		}
		return rs.toRegionArray();
	}
	
	private class ImRegionCollectorList {
		/* using our own little list gets us rid of the overhead required in
		 * general purpose collections and also facilitates more code inlining
		 * by the compiler, improving overall performance */
		private ImRegion[] regions = new ImRegion[16];
		private int regionCount = 0;
		void addRegion(ImRegion region) {
			if (this.regionCount == this.regions.length) {
				ImRegion[] regions = new ImRegion[this.regions.length * 2];
				System.arraycopy(this.regions, 0, regions, 0, this.regions.length);
				this.regions = regions;
			}
			this.regions[this.regionCount++] = region;
		}
		ImRegion[] toRegionArray() {
			return ((this.regionCount < this.regions.length) ? Arrays.copyOf(this.regions, this.regionCount) : this.regions);
		}
	}
	
	/**
	 * Retrieve the regions (e.g. columns and blocks) representing the layout
	 * of the page. This method returns all regions, regardless of nesting.
	 * However, regions higher up in the hierarchy appear before the ones
	 * nested in them in the returned array.
	 * @return an array holding the regions representing the layout of the page
	 */
	public ImRegion[] getRegions() {
		return this.getRegions(null);
	}
	
	/**
	 * Retrieve the regions (e.g. columns or blocks) representing some level of
	 * the layout of the page. This method returns all regions of the argument
	 * type, regardless of nesting. However, regions higher up in the hierarchy
	 * appear before the ones nested in them in the returned array.
	 * @param type the type of regions to return
	 * @return an array holding the regions representing the layout of the page
	 */
	public ImRegion[] getRegions(String type) {
		ImDocument doc = this.getDocument();
		if (ImDocument.TRACK_INSTANCES && (doc != null)) doc.accessed();
		if (type == null)
			return this.regions.toRegionArray();
		else if (WORD_ANNOTATION_TYPE.equals(type))
			return this.words.toRegionArray();
		ImPageRegionList imrs = this.getRegionList(type, false);
		return ((imrs == null) ? new ImRegion[0] : imrs.toRegionArray());
	}
	
	/**
	 * Retrieve the types of regions present in this page. The region types are
	 * in lexicographical order.
	 * @return an array holding the region types
	 */
	public String[] getRegionTypes() {
		ImDocument doc = this.getDocument();
		if (ImDocument.TRACK_INSTANCES && (doc != null)) doc.accessed();
		return ((String[]) this.regionsByType.keySet().toArray(new String[this.regionsByType.size()]));
	}
	
	/**
	 * Retrieve the number of regions present in this page.
	 * @return the number of regions
	 */
	public int getRegionCount() {
		ImDocument doc = this.getDocument();
		if (ImDocument.TRACK_INSTANCES && (doc != null)) doc.accessed();
		return this.regions.size();
	}
	
	/**
	 * Retrieve the number of regions of a specific type present in this page.
	 * @param type the region type to check
	 * @return the number of regions of the argument type
	 */
	public int getRegionCount(String type) {
		ImDocument doc = this.getDocument();
		if (ImDocument.TRACK_INSTANCES && (doc != null)) doc.accessed();
		if (type == null)
			return this.getRegionCount();
		else if (WORD_ANNOTATION_TYPE.equals(type))
			return this.getWordCount();
		ImPageRegionList imrs = this.getRegionList(type, false);
		return ((imrs == null) ? 0 : imrs.size());
	}
	
	/**
	 * Retrieve the supplements present in the page.
	 * @return an array holding the supplements
	 */
	public ImSupplement[] getSupplements() {
		ImDocument doc = this.getDocument();
		if (ImDocument.TRACK_INSTANCES && (doc != null)) doc.accessed();
		ImSupplement[] docSupplements = this.getDocument().getSupplements();
		int retained = 0;
		for (int s = 0; s < docSupplements.length; s++) {
			if (("" + this.pageId).equals(docSupplements[s].getAttribute(PAGE_ID_ATTRIBUTE, "").toString()))
				docSupplements[retained++] = docSupplements[s];
		}
		return Arrays.copyOf(docSupplements, retained);
	}
}