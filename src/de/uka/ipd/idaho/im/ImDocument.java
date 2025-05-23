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

import java.awt.ComponentOrientation;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;

import de.uka.ipd.idaho.easyIO.util.RandomByteSource;
import de.uka.ipd.idaho.gamta.AttributeUtils;
import de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed;
import de.uka.ipd.idaho.gamta.util.CountingSet;
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
	
	//	TODO remove this after server clean !!!
	static final boolean TRACK_INSTANCES = false;
	final AccessHistory accessHistory = (TRACK_INSTANCES ? new AccessHistory(this) : null);
	private static class AccessHistory {
		final ImDocument doc;
		final long created;
		final StackTraceElement[] createStack;
		long lastAccessed;
//		StackTraceElement[] lastAccessStack; ==> causes too much debris
		final int staleAge = (1000 * 60 * 10);
		private final boolean untracked;
		private boolean printed = false;
		AccessHistory(ImDocument doc) {
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
			System.err.println("Stale ImDocument (" + (time - this.created) + "ms ago, last accessed " + (time - this.lastAccessed) + "ms ago)");
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
			Thread deadInstanceChecker = new Thread("ImDocumentGuard") {
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
							System.err.println("ImDocumentGuard: " + accessHistories.size() + " extant, " + reclaimed + " reclaimed, " + stale + (noGc ? " newly gone" : "") + " stale");
						}
						catch (Exception e) {
							System.err.println("ImDocumentGuard: error checking instances: " + e.getMessage());
						}
					}
				}
			};
			deadInstanceChecker.start();
		}
	}
	void accessed() {
		if (TRACK_INSTANCES) this.accessHistory.accessed();
	}
	
	/** The name of the document attribute to store any non-default
	 * <code>de.uka.ipd.idaho.gamte.Tokenizer</code> in that should be used for
	 * text handling and extraction purposes. If this attribute is not set at
	 * an <code>ImDocument</code>, the tokenizer client code should default to
	 * is <code>de.uka.ipd.idaho.gamte.Gamta.INNER_PUNCTUATION_TOKENIZER</code> */
	public static final String TOKENIZER_ATTRIBUTE = "tokenizer";
	
	private static final String docLocalUID = "00000000000000000000000000000000"; // upholds XOR semantics for UUID
	
	private long nextCreateOrderNumber = 0;
	private synchronized long getCreateOrderNumber() {
		return nextCreateOrderNumber++;
	}
	
	private static class ImDocumentAnnotation extends AbstractAttributed implements ImAnnotation {
		ImDocument doc;
		ImWord firstWord;
		ImWord lastWord;
		String type;
		
		final long createOrderNumber;
		private String id = null;
		private String lid = null;
		private String luid = null;
		private String uuid = null;
		
		ImDocumentAnnotation(ImWord firstWord, ImWord lastWord, String type, long createOrderNumber) {
			this.firstWord = firstWord;
			this.lastWord = lastWord;
			this.type = ((type == null) ? "annotation" : type);
			this.createOrderNumber = createOrderNumber;
		}
		String getId() {
			if (TRACK_INSTANCES && (this.doc != null)) this.doc.accessed();
			if (this.id == null)
				this.id = (this.type + ":" + this.firstWord.getLocalID() + "-" + this.lastWord.getLocalID());
			return this.id;
		}
		public String getType() {
			if (TRACK_INSTANCES && (this.doc != null)) this.doc.accessed();
			return this.type;
		}
		public void setType(String type) {
			if (TRACK_INSTANCES && (this.doc != null)) this.doc.accessed();
			String oldType = this.type;
			this.type = type;
			String oldId = this.id;
			this.id = null;
			this.lid = null;
			String oldLuid = this.luid;
			this.luid = null;
			this.uuid = null;
			if (this.doc != null) {
				this.doc.annotationTypeChanged(this, oldType, oldId);
				this.doc.notifyTypeChanged(this, oldType, oldLuid);
			}
		}
		public String getLocalID() {
			if (TRACK_INSTANCES && (this.doc != null)) this.doc.accessed();
			if (this.lid == null)
				this.lid = (this.type + "@" + this.firstWord.getLocalID() + "-" + this.lastWord.getLocalID());
			return this.lid;
		}
		public String getLocalUID() {
			if (TRACK_INSTANCES && (this.doc != null)) this.doc.accessed();
			if (this.luid == null)
				this.luid = AnnotationUuidHelper.getLocalUID(this);
			return this.luid;
		}
		public String getUUID() {
			if (TRACK_INSTANCES && (this.doc != null)) this.doc.accessed();
			if (this.uuid == null)
				this.uuid = AnnotationUuidHelper.getUUID(this);
			return this.uuid;
		}
		public ImDocument getDocument() {
			if (TRACK_INSTANCES && (this.doc != null)) this.doc.accessed();
			return this.doc;
		}
		public ImWord getFirstWord() {
			if (TRACK_INSTANCES && (this.doc != null)) this.doc.accessed();
			return this.firstWord;
		}
		public void setFirstWord(ImWord firstWord) {
			ImWord oldFirstWord = this.firstWord;
			this.firstWord = firstWord;
			String oldId = this.id;
			this.id = null;
			this.lid = null;
			String oldLuid = this.luid;
			this.luid = null;
			this.uuid = null;
			if (this.doc != null) {
				this.doc.annotationFirstWordChanged(this, oldFirstWord, oldId, oldLuid);
				this.doc.notifyAttributeChanged(this, FIRST_WORD_ATTRIBUTE, oldFirstWord);
			}
		}
		public ImWord getLastWord() {
			if (TRACK_INSTANCES && (this.doc != null)) this.doc.accessed();
			return this.lastWord;
		}
		public void setLastWord(ImWord lastWord) {
			ImWord oldLastWord = this.lastWord;
			this.lastWord = lastWord;
			String oldId = this.id;
			this.id = null;
			this.lid = null;
			String oldLuid = this.luid;
			this.luid = null;
			this.uuid = null;
			if (this.doc != null) {
				this.doc.annotationLastWordChanged(this, oldLastWord, oldId, oldLuid);
				this.doc.notifyAttributeChanged(this, LAST_WORD_ATTRIBUTE, oldLastWord);
			}
		}
		void ensureStartEndOrder() {
			if ((this.firstWord == null) || (this.lastWord == null))
				return;
			if (!this.firstWord.getTextStreamId().equals(this.lastWord.getTextStreamId()))
				return;
			if (this.firstWord.getTextStreamPos() <= this.lastWord.getTextStreamPos())
				return;
			ImWord swap = this.firstWord;
			this.firstWord = this.lastWord;
			this.lastWord = swap;
			String oldId = this.id;
			this.id = null;
			this.lid = null;
			String oldLuid = this.luid;
			this.luid = null;
			this.uuid = null;
			if (this.doc != null) {
				this.doc.annotationFirstWordChanged(this, this.lastWord, oldId, oldLuid);
				this.doc.annotationLastWordChanged(this, this.firstWord, oldId, oldLuid);
			}
		}
		public Object getAttribute(String name) {
			if (TRACK_INSTANCES && (this.doc != null)) this.doc.accessed();
			return this.getAttribute(name, null);
		}
		public Object getAttribute(String name, Object def) {
			if (TRACK_INSTANCES && (this.doc != null)) this.doc.accessed();
			if (FIRST_WORD_ATTRIBUTE.equals(name))
				return ((this.firstWord == null) ? def : this.firstWord);
			else if (LAST_WORD_ATTRIBUTE.equals(name))
				return ((this.lastWord == null) ? def : this.lastWord);
			else return super.getAttribute(name, def);
		}
		public boolean hasAttribute(String name) {
			if (TRACK_INSTANCES && (this.doc != null)) this.doc.accessed();
			if (FIRST_WORD_ATTRIBUTE.equals(name))
				return (this.firstWord != null);
			else if (LAST_WORD_ATTRIBUTE.equals(name))
				return (this.lastWord != null);
			else return super.hasAttribute(name);
		}
		public Object setAttribute(String name, Object value) {
			if (FIRST_WORD_ATTRIBUTE.equals(name) && (value instanceof ImWord)) {
				ImWord oldFirstWord = this.getFirstWord();
				this.setFirstWord((ImWord) value);
				return oldFirstWord;
			}
			else if (LAST_WORD_ATTRIBUTE.equals(name) && (value instanceof ImWord)) {
				ImWord oldLastWord = this.getLastWord();
				this.setLastWord((ImWord) value);
				return oldLastWord;
			}
			else {
				Object oldValue = super.setAttribute(name, value);
				if (this.doc != null)
					this.doc.notifyAttributeChanged(this, name, oldValue);
				return oldValue;
			}
		}
		public String[] getAttributeNames() {
			if (TRACK_INSTANCES && (this.doc != null)) this.doc.accessed();
			return super.getAttributeNames();
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
	
	private class ImDocumentAnnotationList {
		private ImDocumentAnnotation[] annots = new ImDocumentAnnotation[16];
		private int annotCount = 0;
		private HashSet contained = new HashSet();
		private HashSet removed = new HashSet();
		private int addCount = 0;
		private int cleanAddCount = 0;
		private int endWordModCount = 0;
		private int cleanEndWordModCount = 0;
		private int cleanTextStreamModCount = 0;
		void addAnnot(ImDocumentAnnotation annot) {
			if (TRACK_INSTANCES) accessHistory.accessed();
			if (annot == null)
				return;
			if (this.contained.contains(annot))
				return;
			this.contained.add(annot);
			if (this.removed.remove(annot)) {
				if (this.annotCount != (this.contained.size() + this.removed.size()))
					System.out.println("FUCK, array " + this.annotCount + " != (contained " + this.contained.size() + " + removed " + this.removed.size() + ") on adding " + annot.getType());
				return; // this one is was flagged for removal, but still in the array, no need to add again (also, addition has been countered before)
			}
			if (this.annotCount == this.annots.length) {
				ImDocumentAnnotation[] annots = new ImDocumentAnnotation[this.annots.length * 2];
				System.arraycopy(this.annots, 0, annots, 0, this.annots.length);
				this.annots = annots;
			}
			this.annots[this.annotCount++] = annot;
			this.addCount++;
			if (this.annotCount != (this.contained.size() + this.removed.size()))
				System.out.println("FUCK, array " + this.annotCount + " != (contained " + this.contained.size() + " + removed " + this.removed.size() + ") on adding " + annot.getType());
		}
		boolean isEmpty() {
			return this.contained.isEmpty();
		}
		int size() {
			return this.contained.size();
		}
		ImDocumentAnnotation getAnnot(int index) {
			this.ensureSorted();
			return this.annots[index];
		}
		void removeAnnot(ImDocumentAnnotation annot) {
			if (TRACK_INSTANCES) accessHistory.accessed();
			if (annot == null)
				return;
			if (this.contained.remove(annot))
				this.removed.add(annot);
			if (this.annotCount != (this.contained.size() + this.removed.size()))
				System.out.println("FUCK, array " + this.annotCount + " != (contained " + this.contained.size() + " + removed " + this.removed.size() + ") on removing " + annot.getType());
		}
		void clear() {
			Arrays.fill(this.annots, 0, this.annotCount, null); // free up references to help GC
			this.annotCount = 0;
			this.contained.clear();
			this.removed.clear();
		}
		void annotationEndWordChanged() {
			this.endWordModCount++;
		}
		ImAnnotation[] toAnnotArray() {
			this.ensureSorted();
			return Arrays.copyOfRange(this.annots, 0, this.annotCount);
		}
		private void ensureSorted() {
			this.ensureClean();
			if ((this.cleanAddCount == this.addCount)/* && (this.cleanTypeModCount == this.typeModCount)*/ && (this.cleanEndWordModCount == this.endWordModCount) && (this.cleanTextStreamModCount == textStreamModCount)/* && (this.cleanOrderModCount == orderModCount)*/)
				return;
			if (this.cleanTextStreamModCount != textStreamModCount) {
				for (int a = 0; a < this.annotCount; a++)
					this.annots[a].ensureStartEndOrder();
			}
			/* TODOnot if order and types unmodified, we can even save sorting the whole list:
			 * - sort only added annotations ...
			 * - ... and then merge them into main list in single pass
			 * ==> but then, TimSort already does pretty much that ... */
			Arrays.sort(this.annots, 0, this.annotCount, imDocumentAnnotationOrder);
			this.cleanAddCount = this.addCount;
			this.cleanEndWordModCount = this.endWordModCount;
			this.cleanTextStreamModCount = textStreamModCount;
		}
		private void ensureClean() {
			if (TRACK_INSTANCES) accessHistory.accessed();
			if (this.removed.isEmpty())
				return;
			int removed = 0;
			for (int a = 0; a < this.annotCount; a++) {
				if (this.removed.contains(this.annots[a]))
					removed++;
				else if (removed != 0)
					this.annots[a - removed] = this.annots[a];
			}
			Arrays.fill(this.annots, (this.annotCount - removed), this.annotCount, null); // free up references to help GC
			this.annotCount -= removed;
			this.removed.clear();
		}
	}
	
	private static final Comparator imDocumentAnnotationOrder = new Comparator() {
		public int compare(Object obj1, Object obj2) {
			if (obj1 == obj2)
				return 0;
			ImDocumentAnnotation annot1 = ((ImDocumentAnnotation) obj1);
			ImDocumentAnnotation annot2 = ((ImDocumentAnnotation) obj2);
			int c = ImUtils.textStreamOrder.compare(annot1.firstWord, annot2.firstWord);
			if (c != 0)
				return c;
			c = ImUtils.textStreamOrder.compare(annot2.lastWord, annot1.lastWord);
			if (c != 0)
				return c;
//			return ((int) (annot1.createTime - annot2.createTime));
			return ((int) (annot1.createOrderNumber - annot2.createOrderNumber));
			
//			//	TODOnot compare page IDs first, and text stream IDs only then, so annotations from same page stay together
//			//	==> if any text streams cross page boundaries backwards, this ceases to be a total ordering, which foils TimSort !!!
//			int c = (annot1.firstWord.pageId - annot2.firstWord.pageId);
//			if (c != 0)
//				return c;
//			c = (annot2.lastWord.pageId - annot1.lastWord.pageId);
//			if (c != 0)
//				return c;
//			c = annot1.firstWord.getTextStreamId().compareTo(annot2.firstWord.getTextStreamId());
//			if (c != 0)
//				return c;
//			c = (annot1.firstWord.getTextStreamPos() - annot2.firstWord.getTextStreamPos());
//			if (c != 0)
//				return c;
//			c = (annot2.lastWord.getTextStreamPos() - annot1.lastWord.getTextStreamPos());
//			if (c != 0)
//				return c;
//			return ((int) (annot1.createOrderNumber - annot2.createOrderNumber));
			
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
	};
	
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
		 * Notify the listener that a supplement has changed in an Image Markup
		 * document.
		 * @param supplementId the ID of the supplement
		 * @param oldValue the old supplement, which was just replaced
		 */
		public abstract void supplementChanged(String supplementId, ImSupplement oldValue);
		
		/**
		 * Notify the listener that a font has changed in an Image Markup
		 * document.
		 * @param fontName the name of the font
		 * @param oldValue the old font, which was just replaced
		 */
		public abstract void fontChanged(String fontName, ImFont oldValue);
		
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
	
	/**
	 * Weak reference wrapper for IM document listeners. Client code that needs
	 * to be eligible for reclaiming by GC despite a sole strong reference to
	 * it still existing in a listener added to an IM document it wants to
	 * observe can use this class to add a weak reference link to the actual
	 * listener.
	 * 
	 * @author sautter
	 */
	public static class WeakImDocumentListener implements ImDocumentListener {
		private WeakReference imdlWeakRef;
		private ImDocument doc;
		
		/** Constructor
		 * @param imdl the IM document listener to wrap
		 * @param doc the IM document observed by the argument listener
		 */
		public WeakImDocumentListener(ImDocumentListener imdl, ImDocument doc) {
			this.imdlWeakRef = new WeakReference(imdl);
			this.doc = doc;
		}
		
		private ImDocumentListener getImDocumentListener() {
			ImDocumentListener imdl = ((ImDocumentListener) this.imdlWeakRef.get());
			if (imdl == null) {
				if (this.doc != null)
					this.doc.removeDocumentListener(this);
				this.doc = null;
			}
			return imdl;
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.im.ImDocument.ImDocumentListener#typeChanged(de.uka.ipd.idaho.im.ImObject, java.lang.String)
		 */
		public void typeChanged(ImObject object, String oldType) {
			ImDocumentListener imdl = this.getImDocumentListener();
			if (imdl != null)
				imdl.typeChanged(object, oldType);
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.im.ImDocument.ImDocumentListener#attributeChanged(de.uka.ipd.idaho.im.ImObject, java.lang.String, java.lang.Object)
		 */
		public void attributeChanged(ImObject object, String attributeName, Object oldValue) {
			ImDocumentListener imdl = this.getImDocumentListener();
			if (imdl != null)
				imdl.attributeChanged(object, attributeName, oldValue);
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.im.ImDocument.ImDocumentListener#supplementChanged(java.lang.String, de.uka.ipd.idaho.im.ImSupplement)
		 */
		public void supplementChanged(String supplementId, ImSupplement oldValue) {
			ImDocumentListener imdl = this.getImDocumentListener();
			if (imdl != null)
				imdl.supplementChanged(supplementId, oldValue);
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.im.ImDocument.ImDocumentListener#fontChanged(java.lang.String, de.uka.ipd.idaho.im.ImFont)
		 */
		public void fontChanged(String fontName, ImFont oldValue) {
			ImDocumentListener imdl = this.getImDocumentListener();
			if (imdl != null)
				imdl.fontChanged(fontName, oldValue);
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.im.ImDocument.ImDocumentListener#regionAdded(de.uka.ipd.idaho.im.ImRegion)
		 */
		public void regionAdded(ImRegion region) {
			ImDocumentListener imdl = this.getImDocumentListener();
			if (imdl != null)
				imdl.regionAdded(region);
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.im.ImDocument.ImDocumentListener#regionRemoved(de.uka.ipd.idaho.im.ImRegion)
		 */
		public void regionRemoved(ImRegion region) {
			ImDocumentListener imdl = this.getImDocumentListener();
			if (imdl != null)
				imdl.regionRemoved(region);
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.im.ImDocument.ImDocumentListener#annotationAdded(de.uka.ipd.idaho.im.ImAnnotation)
		 */
		public void annotationAdded(ImAnnotation annotation) {
			ImDocumentListener imdl = this.getImDocumentListener();
			if (imdl != null)
				imdl.annotationAdded(annotation);
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.im.ImDocument.ImDocumentListener#annotationRemoved(de.uka.ipd.idaho.im.ImAnnotation)
		 */
		public void annotationRemoved(ImAnnotation annotation) {
			ImDocumentListener imdl = this.getImDocumentListener();
			if (imdl != null)
				imdl.annotationRemoved(annotation);
		}
	}
	
	/** the UUID of the document */
	public final String docId;
	
	/** the orientation of the text in the images contained in the document, determining the reading order of words */
	public final ComponentOrientation orientation;
	
	private TreeMap fontsByName = new TreeMap();
	private TreeMap supplementsById = new TreeMap();
	
	private TreeMap pagesById = new TreeMap();
	
	private int wordCount = -1;
	
	private CountingSet regionTypeCounts = new CountingSet(new TreeMap());
	private boolean regionTypesClean = true;
	
	private ImWord[] textStreamHeads = null;
	private ImWord[] textStreamTails = null;
	
	private int textStreamModCount = 0;
	
	private ImDocumentAnnotationList annotations = new ImDocumentAnnotationList();
	private ImDocumentAnnotationIdIndex annotationsById = new ImDocumentAnnotationIdIndex();
	private TreeMap annotationsByType = new TreeMap();
	private HashMap annotationsByFirstWord = new HashMap();
	private HashMap annotationsByLastWord = new HashMap();
	private ImDocumentAnnotationList[] annotationsByPageId = null;
	private HashMap objectsByLocalUID = new HashMap() {
//		public Object put(Object key, Object value) {
//			System.out.println("LocalUID '" + key + "' mapped to " + value);
//			Object oldValue = super.put(key, value);
//			if (oldValue != null)
//				System.out.println(" ==> replaced '" + oldValue);
//			return oldValue;
//		}
//		public Object remove(Object key) {
//			System.out.println("LocalUID '" + key + "' removed");
//			return super.remove(key);
//		}
	};
	
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
	 * also a page image store, it is used in the latter function as well.
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
		PageImageInputStream piis = ((this.pageImageSource == null) ? null : this.pageImageSource.getPageImageAsStream(this.docId, pageId));
		return ((piis == null) ? PageImage.getPageImageAsStream(this.docId, pageId) : piis);
	}
	
	/**
	 * Store the image of a particular page in the page image store associated
	 * with this Image Markup document. If no dedicated page image store is
	 * present, the general storages of page images will be consulted.
	 * @param pi the page image to store
	 * @param pageId the ID of the page the image belongs to
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
			if (this.annotationsByPageId != null)
				Arrays.fill(this.annotationsByPageId, null);
			clearTypeAnnotationIndex(this.annotationsByType);
			clearWordAnnotationIndex(this.annotationsByFirstWord);
			clearWordAnnotationIndex(this.annotationsByLastWord);
		}
		synchronized (this.objectsByLocalUID) {
			this.objectsByLocalUID.clear();
		}
		this.supplementsById.clear();
		this.fontsByName.clear();
		this.documentProperties.clear();
		this.clearAttributes();
	}
	private static void clearTypeAnnotationIndex(TreeMap index) {
		for (Iterator alkit = index.keySet().iterator(); alkit.hasNext();) {
			String annotListKey = ((String) alkit.next());
			ImDocumentAnnotationList annotList = ((ImDocumentAnnotationList) index.get(annotListKey));
			annotList.clear();
			alkit.remove();
		}
	}
	private static void clearWordAnnotationIndex(HashMap index) {
		for (Iterator alkit = index.keySet().iterator(); alkit.hasNext();) {
			String annotListKey = ((String) alkit.next());
			ImWordAnnotationList annotList = ((ImWordAnnotationList) index.get(annotListKey));
			annotList.clear();
			alkit.remove();
		}
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#finalize()
	 */
	protected void finalize() throws Throwable {
		this.dispose();
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImObject#getType()
	 */
	public String getType() {
		return DOCUMENT_TYPE;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImObject#getLocalID()
	 */
	public String getLocalID() {
		return this.docId;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImObject#getLocalUID()
	 */
	public String getLocalUID() {
		return docLocalUID; // upholds XOR with document ID semantics
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImObject#getUUID()
	 */
	public String getUUID() {
		return this.docId;
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
		ImFont oldFont;
		synchronized (this.fontsByName) {
			oldFont = ((ImFont) this.fontsByName.put(font.name, font));
		}
		synchronized (this.objectsByLocalUID) {
			if (oldFont != null)
				this.objectsByLocalUID.remove(oldFont.getLocalUID());
			this.objectsByLocalUID.put(font.getLocalUID(), font);
		}
		if (oldFont != null)
			oldFont.setDocument(null);
		font.setDocument(this);
		this.notifyFontChanged(font.name, oldFont);
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
	 * Retrieve the number of fonts present in the document.
	 * @return the number of fonts
	 */
	public int getFontCount() {
		return this.fontsByName.size();
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
		ImFont oldFont = ((ImFont) this.fontsByName.remove(name));
		if (oldFont != null)
			synchronized (this.objectsByLocalUID) {
				this.objectsByLocalUID.remove(oldFont.getLocalUID());
			}
		if (oldFont != null)
			oldFont.setDocument(null);
		this.notifyFontChanged(name, oldFont);
	}
	
	/**
	 * Add a supplement to the document, or replace one with a newer version.
	 * @param ims the supplement to add
	 * @return the supplement replaced by the addition of the argument one
	 */
	public ImSupplement addSupplement(ImSupplement ims) {
		if (ims == null)
			return null;
		ImSupplement oldIms;
		synchronized (this.supplementsById) {
			oldIms = ((ImSupplement) this.supplementsById.put(ims.getId(), ims));
		}
		synchronized (this.objectsByLocalUID) {
			if (oldIms != null)
				this.objectsByLocalUID.remove(oldIms.getLocalUID());
			this.objectsByLocalUID.put(ims.getLocalUID(), ims);
		}
		if (oldIms != null)
			oldIms.setDocument(null);
		ims.setDocument(this);
		this.notifySupplementChanged(ims.getId(), oldIms);
//		DO NOT DISPOSE SUPPLEMENTS, MIGHT BE RE-ADDED	
//		if (oldIms != null)
//			oldIms.dispose();
		return oldIms;
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
	 * Retrieve the types of supplements present in this document. The
	 * supplement types are in lexicographical order.
	 * @return an array holding the supplement types
	 */
	public String[] getSupplementTypes() {
		if (TRACK_INSTANCES) this.accessHistory.accessed();
		TreeSet imsTypes = new TreeSet();
		for (Iterator sidit = this.supplementsById.keySet().iterator(); sidit.hasNext();) {
			ImSupplement ims = ((ImSupplement) this.supplementsById.get(sidit.next()));
			imsTypes.add(ims.getType());
		}
		return ((String[]) imsTypes.toArray(new String[imsTypes.size()]));
	}
	
	/**
	 * Retrieve the number of supplements present in the document.
	 * @return the number of supplements
	 */
	public int getSupplementCount() {
		if (TRACK_INSTANCES) this.accessHistory.accessed();
		return this.supplementsById.size();
	}
	
	/**
	 * Retrieve the number of supplements of a specific type present in this
	 * document.
	 * @param type the supplements type to check
	 * @return the number of supplements of the argument type
	 */
	public int getSupplementCount(String type) {
		if (TRACK_INSTANCES) this.accessHistory.accessed();
		if (type == null)
			return this.getSupplementCount();
		int count = 0;
		for (Iterator sidit = this.supplementsById.keySet().iterator(); sidit.hasNext();) {
			ImSupplement ims = ((ImSupplement) this.supplementsById.get(sidit.next()));
			if (type.equals(ims.getType()))
				count++;
		}
		return count;
	}
	
	/**
	 * Remove a supplement from the document.
	 * @param ims the supplement to remove
	 */
	public void removeSupplement(ImSupplement ims) {
		if (ims == null)
			return;
		ImSupplement oldIms;
		synchronized (this.supplementsById) {
			oldIms = ((ImSupplement) this.supplementsById.remove(ims.getId()));
		}
		if (oldIms != null)
			synchronized (this.objectsByLocalUID) {
				this.objectsByLocalUID.remove(oldIms.getLocalUID());
			}
		if (oldIms != null)
			oldIms.setDocument(null);
		this.notifySupplementChanged(ims.getId(), oldIms);
//		DO NOT DISPOSE SUPPLEMENTS, MIGHT BE RE-ADDED	
//		if (oldIms != null)
//			oldIms.dispose();
	}
	
	/**
	 * Remove a supplement from the document.
	 * @param sid the ID of the supplement to remove
	 */
	public void removeSupplement(String sid) {
		this.removeSupplement(this.getSupplement(sid));
	}
	
	void addPage(ImPage page) {
		ImPage oldPage = ((ImPage) this.pagesById.put(new Integer(page.pageId), page));
		this.wordCount = -1;
		this.regionTypesClean = false;
		if (oldPage != null) {
			synchronized (this.objectsByLocalUID) {
				this.objectsByLocalUID.remove(oldPage.getLocalUID());
			}
			synchronized (this.objectsByLocalUID) {
				this.objectsByLocalUID.put(oldPage.getLocalUID(), page);
			}
			oldPage.dispose();
		}
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
	 * Retrieve the types of regions present in this document. The region types
	 * are in lexicographical order.
	 * @return an array holding the region types
	 */
	public String[] getRegionTypes() {
		this.ensureRegionTypeCounts();
		return ((String[]) this.regionTypeCounts.toArray(new String[this.regionTypeCounts.elementCount()]));
	}
	
	/**
	 * Retrieve the number of regions present in this document.
	 * @return the number of regions
	 */
	public int getRegionCount() {
		this.ensureRegionTypeCounts();
		return this.regionTypeCounts.size();
	}
	
	/**
	 * Retrieve the number of regions of a specific type present in this
	 * document.
	 * @param type the region type to check
	 * @return the number of regions of the argument type
	 */
	public int getRegionCount(String type) {
		if (type == null)
			return this.getRegionCount();
		this.ensureRegionTypeCounts();
		return this.regionTypeCounts.getCount(type);
	}
	
	private void ensureRegionTypeCounts() {
		if (this.regionTypesClean)
			return;
		this.regionTypeCounts.clear();
		for (Iterator pit = this.pagesById.keySet().iterator(); pit.hasNext();) {
			ImPage page = ((ImPage) this.pagesById.get(pit.next()));
			String[] pRegTypes = page.getRegionTypes();
			for (int t = 0; t < pRegTypes.length; t++)
				this.regionTypeCounts.add(pRegTypes[t], page.getRegionCount(pRegTypes[t]));
		}
		this.regionTypesClean = true;
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
		if (page != null) {
			page.dispose();
			this.wordCount = -1;
			this.regionTypesClean = false;
		}
	}
	
	/**
	 * Add an annotation to the document. If the argument annotation originates
	 * from this document, this method simply returns it. Further, if the
	 * argument annotation was previously removed from this document, it is
	 * re-attached and returned. Otherwise, an annotation is added to the
	 * document and returned. It is not the same object as the argument
	 * annotation, but has the same attributes.
	 * @param annot the annotation to add
	 * @return the newly added annotation
	 * @see de.uka.ipd.idaho.im.ImDocument#cleanupAnnotations()
	 */
	public ImAnnotation addAnnotation(ImAnnotation annot) {
		if (!annot.getFirstWord().getTextStreamId().equals(annot.getLastWord().getTextStreamId()))
			return null;
		if (annot instanceof ImDocumentAnnotation) {
			if (annot.getDocument() == this)
				return annot;
			if ((annot.getDocument() == null) && (annot.getFirstWord().getDocument() == this) && (annot.getLastWord().getDocument() == this))
				return this.addImDocumentAnnotation((ImDocumentAnnotation) annot);
		}
		ImDocumentAnnotation docAnnot = new ImDocumentAnnotation(annot.getFirstWord(), annot.getLastWord(), annot.getType(), this.getCreateOrderNumber());
		docAnnot.copyAttributes(annot);
		return this.addImDocumentAnnotation(docAnnot);
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
		ImDocumentAnnotation annot = new ImDocumentAnnotation(firstWord, lastWord, type, this.getCreateOrderNumber());
		return this.addImDocumentAnnotation(annot);
	}
	
	private ImAnnotation addImDocumentAnnotation(ImDocumentAnnotation annot) {
		if (this.annotationsById.containsKey(annot.getId()))
			return ((ImAnnotation) this.annotationsById.get(annot.getId()));
		synchronized (this.annotationsById) {
			this.annotationsById.add(annot);
			this.annotations.addAnnot(annot);
			this.updateTypeAnnotationIndex(null, annot.type, annot);
			updateWordAnnotationIndex(this.annotationsByFirstWord, null, annot.firstWord.getLocalID(), annot);
			updateWordAnnotationIndex(this.annotationsByLastWord, null, annot.lastWord.getLocalID(), annot);
			this.indexAnnotationForPageIDs(annot, annot.firstWord.pageId, annot.lastWord.pageId);
		}
		synchronized (this.objectsByLocalUID) {
			this.objectsByLocalUID.put(annot.getLocalUID(), annot);
		}
		annot.doc = this; // attach annotation
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
				this.updateTypeAnnotationIndex(annot.getType(), null, docAnnot);
				updateWordAnnotationIndex(this.annotationsByFirstWord, annot.getFirstWord().getLocalID(), null, docAnnot);
				updateWordAnnotationIndex(this.annotationsByLastWord, annot.getLastWord().getLocalID(), null, docAnnot);
				this.unIndexAnnotationForPageIDs(docAnnot, annot.getFirstWord().pageId, annot.getLastWord().pageId);
			}
			synchronized (this.objectsByLocalUID) {
				this.objectsByLocalUID.remove(annot.getLocalUID());
			}
			this.notifyAnnotationRemoved(annot);
			docAnnot.doc = null; // detach annotation
		}
	}
	
	void annotationTypeChanged(ImDocumentAnnotation annot, String oldType, String oldId) {
		synchronized (this.annotationsById) {
			boolean annotInIndexes = this.annotationsById.remove(oldId, annot);
			this.annotationsById.add(annot);
			
			if (!annotInIndexes)
				this.annotations.addAnnot(annot);
			
			//	if old ID index removal returns false, we're undoing a duplicate cleanup, need to update _all_ indexes
			this.updateTypeAnnotationIndex(oldType, annot.getType(), annot);
			if (!annotInIndexes) {
				updateWordAnnotationIndex(this.annotationsByFirstWord, null, annot.getFirstWord().getLocalID(), annot);
				updateWordAnnotationIndex(this.annotationsByLastWord, null, annot.getLastWord().getLocalID(), annot);
			}
			
			if (!annotInIndexes)
				this.indexAnnotationForPageIDs(annot, annot.getFirstWord().pageId, annot.getLastWord().pageId);
		}
	}
	
	void annotationFirstWordChanged(ImDocumentAnnotation annot, ImWord oldFirstWord, String oldId, String oldLuid) {
		synchronized (this.annotationsById) {
			boolean annotInIndexes = this.annotationsById.remove(oldId, annot);
			this.annotationsById.add(annot);
			
			if (annotInIndexes)
				this.annotations.annotationEndWordChanged();
			else this.annotations.addAnnot(annot);
			
			//	if old ID index removal returns false, we're undoing a duplicate cleanup, need to update _all_ indexes
			updateWordAnnotationIndex(this.annotationsByFirstWord, oldFirstWord.getLocalID(), annot.getFirstWord().getLocalID(), annot);
			if (!annotInIndexes) {
				this.updateTypeAnnotationIndex(null, annot.getType(), annot);
				updateWordAnnotationIndex(this.annotationsByLastWord, null, annot.getLastWord().getLocalID(), annot);
			}
			
			ImDocumentAnnotationList typeAnnots = ((ImDocumentAnnotationList) this.annotationsByType.get(annot.getType()));
			if (typeAnnots != null)
				typeAnnots.annotationEndWordChanged();
			
			if (annotInIndexes) {
				this.unIndexAnnotationForPageIDs(annot, oldFirstWord.pageId, annot.getFirstWord().pageId);
				this.indexAnnotationForPageIDs(annot, annot.getFirstWord().pageId, oldFirstWord.pageId);
			}
			else this.indexAnnotationForPageIDs(annot, annot.getFirstWord().pageId, annot.getLastWord().pageId);
		}
		
		synchronized (this.objectsByLocalUID) {
			if (oldLuid != null)
				this.objectsByLocalUID.remove(oldLuid);
			this.objectsByLocalUID.put(annot.getLocalUID(), annot);
		}
	}
	
	void annotationLastWordChanged(ImDocumentAnnotation annot, ImWord oldLastWord, String oldId, String oldLuid) {
		synchronized (this.annotationsById) {
			boolean annotInIndexes = this.annotationsById.remove(oldId, annot);
			this.annotationsById.add(annot);
			
			if (annotInIndexes)
				this.annotations.annotationEndWordChanged();
			else this.annotations.addAnnot(annot);
			
			//	if old ID index removal returns false, we're undoing a duplicate cleanup, need to update _all_ indexes
			updateWordAnnotationIndex(this.annotationsByLastWord, oldLastWord.getLocalID(), annot.getLastWord().getLocalID(), annot);
			if (!annotInIndexes) {
				this.updateTypeAnnotationIndex(null, annot.getType(), annot);
				updateWordAnnotationIndex(this.annotationsByFirstWord, null, annot.getFirstWord().getLocalID(), annot);
			}
			
			ImDocumentAnnotationList typeAnnots = ((ImDocumentAnnotationList) this.annotationsByType.get(annot.getType()));
			if (typeAnnots != null)
				typeAnnots.annotationEndWordChanged();
			
			if (annotInIndexes) {
				this.unIndexAnnotationForPageIDs(annot, (annot.getLastWord().pageId + 1), oldLastWord.pageId);
				this.indexAnnotationForPageIDs(annot, (oldLastWord.pageId + 1), annot.getLastWord().pageId);
			}
			else this.indexAnnotationForPageIDs(annot, annot.getFirstWord().pageId, annot.getLastWord().pageId);
		}
		
		synchronized (this.objectsByLocalUID) {
			if (oldLuid != null)
				this.objectsByLocalUID.remove(oldLuid);
			this.objectsByLocalUID.put(annot.getLocalUID(), annot);
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
	
	private class ImDocumentAnnotationIdIndex {
		private HashMap annotsById = new HashMap();
		private int annotListCount = 0;
		private class AnnotationList {
			private ImDocumentAnnotation[] annots = new ImDocumentAnnotation[2];
			private int annotCount = 0;
			AnnotationList() {
				annotListCount++;
			}
			void addAnnot(ImDocumentAnnotation annot) {
				if (this.annotCount == this.annots.length) {
					ImDocumentAnnotation[] annots = new ImDocumentAnnotation[this.annots.length * 2];
					System.arraycopy(this.annots, 0, annots, 0, this.annots.length);
					this.annots = annots;
				}
				this.annots[this.annotCount++] = annot;
			}
			ImDocumentAnnotation getFirst() {
				return this.annots[0];
			}
			ImDocumentAnnotation getAnnot(int index) {
				return this.annots[index];
			}
			int size() {
				return this.annotCount;
			}
			boolean removeAnnot(ImDocumentAnnotation annot) {
				if (annot == null)
					return false;
				for (int a = 0; a < this.annotCount; a++)
					if (this.annots[a] == annot) {
						System.arraycopy(this.annots, (a+1), this.annots, a, (this.annotCount - (a+1)));
						this.annotCount--;
						this.annots[this.annotCount] = null;
						return true;
					}
				return false;
			}
			public void clear() {
				this.annotCount = 0;
				Arrays.fill(this.annots, null);
				annotListCount--;
			}
		}
		ImDocumentAnnotationIdIndex() {}
		synchronized boolean containsKey(String key) {
			return this.annotsById.containsKey(key);
		}
		synchronized ImDocumentAnnotation get(String key) {
			Object annotObj = this.annotsById.get(key);
			if (annotObj instanceof ImDocumentAnnotation)
				return ((ImDocumentAnnotation) annotObj);
			else return ((AnnotationList) annotObj).getFirst();
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
				annotList.addAnnot((ImDocumentAnnotation) oldAnnotObj);
//				System.out.println(" ==> duplicate ID key with " + annotList.size() + " other annotations already present");
			}
			annotList.addAnnot(annot);
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
			//	if we have removed a single annotation that is NOT the argument annotation, we need to re-put it (can happen when UNDOing an operation that resulted in a duplicate, which was cleaned up later)
			if (oldAnnotObj instanceof ImDocumentAnnotation) {
//				System.out.println(" ==> ID key known, but mapped to other annotation");
				this.annotsById.put(annotId, oldAnnotObj);
				return false;
			}
			AnnotationList annotList = ((AnnotationList) oldAnnotObj);
//			boolean removed = annotList.remove(annot);
			boolean removed = annotList.removeAnnot(annot);
			if (annotList.size() == 1) {
//				this.annotsById.put(annotId, annotList.get(0));
				this.annotsById.put(annotId, annotList.getFirst());
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
			HashMap dAnnotsById = new HashMap();
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
				ImDocumentAnnotation annot = annotList.getFirst();
				for (int a = 1; a < annotList.size(); a++) {
					ImDocumentAnnotation dAnnot = annotList.getAnnot(a);
					AttributeUtils.copyAttributes(dAnnot, annot, AttributeUtils.ADD_ATTRIBUTE_COPY_MODE);
					annotations.removeAnnot(dAnnot);
					updateTypeAnnotationIndex(dAnnot.getType(), null, dAnnot);
					updateWordAnnotationIndex(annotationsByFirstWord, dAnnot.getFirstWord().getLocalID(), null, dAnnot);
					updateWordAnnotationIndex(annotationsByLastWord, dAnnot.getLastWord().getLocalID(), null, dAnnot);
					unIndexAnnotationForPageIDs(dAnnot, dAnnot.firstWord.pageId, dAnnot.lastWord.pageId);
				}
				annotList.clear();
				this.annotsById.put(annotId, annot);
			}
		}
	}
	
	private void updateTypeAnnotationIndex(String oldType, String newType, ImDocumentAnnotation annot) {
		if (oldType != null) {
			ImDocumentAnnotationList oldTypeAnnots = ((ImDocumentAnnotationList) this.annotationsByType.get(oldType));
			if (oldTypeAnnots != null) {
				oldTypeAnnots.removeAnnot(annot);
				if (oldTypeAnnots.isEmpty())
					this.annotationsByType.remove(oldType);
			}
		}
		if (newType != null) {
			ImDocumentAnnotationList newTypeAnnots = ((ImDocumentAnnotationList) this.annotationsByType.get(newType));
			if (newTypeAnnots == null) {
				newTypeAnnots = new ImDocumentAnnotationList();
				this.annotationsByType.put(newType, newTypeAnnots);
			}
			newTypeAnnots.addAnnot(annot);
		}
	}
	
	private static void updateWordAnnotationIndex(HashMap index, String oldKey, String newKey, ImDocumentAnnotation annot) {
		if (oldKey != null) {
			ImWordAnnotationList oldKeyAnnots = ((ImWordAnnotationList) index.get(oldKey));
			if (oldKeyAnnots != null) {
				oldKeyAnnots.removeAnnot(annot); // we have to use this method of removal, as sort order might be compromised by first or last word update
				if (oldKeyAnnots.isEmpty())
					index.remove(oldKey);
			}
		}
		if (newKey != null) {
			ImWordAnnotationList newKeyAnnots = ((ImWordAnnotationList) index.get(newKey));
			if (newKeyAnnots == null) {
				newKeyAnnots = new ImWordAnnotationList();
				index.put(newKey, newKeyAnnots);
			}
			newKeyAnnots.addAnnot(annot); // this method automatically maintains sort order
		}
	}
	
	private static class ImWordAnnotationList {
		/* using our own little list gets us rid of the overhead required in
		 * general purpose collections and also facilitates more code inlining
		 * by the compiler, improving overall performance */
		private static int instanceCount = 0;
		private static int arrayDuplications = 0;
		private ImDocumentAnnotation[] annots = new ImDocumentAnnotation[8];
//		private ImDocumentAnnotation[] annots = new ImDocumentAnnotation[16];
		private int annotCount = 0;
		ImWordAnnotationList() {
			instanceCount++;
		}
		void addAnnot(ImDocumentAnnotation annot) {
			if (annot == null)
				return;
			for (int a = 0; a < this.annotCount; a++) {
				if (this.annots[a] == annot)
					return;
			}
			if (this.annotCount == this.annots.length) {
				ImDocumentAnnotation[] annots = new ImDocumentAnnotation[this.annots.length * 2];
				System.arraycopy(this.annots, 0, annots, 0, this.annots.length);
				this.annots = annots;
				arrayDuplications++;
				System.out.println("ImWordAnnotationList: enlarged array to " + this.annots.length + ", " + arrayDuplications + " enlargement in " + instanceCount + " instances");
			}
			this.annots[this.annotCount++] = annot;
		}
		boolean isEmpty() {
			return (this.annotCount == 0);
		}
		int size() {
			return this.annotCount;
		}
		ImDocumentAnnotation getAnnot(int index) {
			return this.annots[index];
		}
		void removeAnnot(ImDocumentAnnotation annot) {
			if (annot == null)
				return;
			for (int a = 0; a < this.annotCount; a++)
				if (this.annots[a] == annot) {
					System.arraycopy(this.annots, (a+1), this.annots, a, (this.annotCount - (a+1)));
					this.annotCount--;
					this.annots[this.annotCount] = null;
					break;
				}
		}
		void clear() {
			Arrays.fill(this.annots, null);
			this.annotCount = 0;
		}
	}
	
	private void indexAnnotationForPageIDs(ImDocumentAnnotation annot, int fromPageId, int toPageId) {
		if (this.annotationsByPageId == null) {
			int maxPageId = ((Integer) this.pagesById.lastKey()).intValue();
			this.annotationsByPageId = new ImDocumentAnnotationList[maxPageId + 1];
		}
		for (int p = fromPageId; p <= toPageId; p++) {
			if (this.annotationsByPageId.length <= p) {
//				ImDocumentAnnotationList[] annotationsByPageId = new ImDocumentAnnotationList[this.annotationsByPageId.length * 2];
				int maxPageId = ((Integer) this.pagesById.lastKey()).intValue();
				ImDocumentAnnotationList[] annotationsByPageId = new ImDocumentAnnotationList[maxPageId + 1];
				System.arraycopy(this.annotationsByPageId, 0, annotationsByPageId, 0, this.annotationsByPageId.length);
				this.annotationsByPageId = annotationsByPageId;
			}
			if (this.annotationsByPageId[p] == null)
				this.annotationsByPageId[p] = new ImDocumentAnnotationList();
			this.annotationsByPageId[p].addAnnot(annot);
		}
	}
	
	private void unIndexAnnotationForPageIDs(ImDocumentAnnotation annot, int fromPageId, int toPageId) {
		if (this.annotationsByPageId == null)
			return;
		for (int p = fromPageId; p <= toPageId; p++) {
			if (this.annotationsByPageId.length <= p)
				break;
			if (this.annotationsByPageId[p] == null)
				continue;
			this.annotationsByPageId[p].removeAnnot(annot);
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
	 * Retrieve the annotations overlapping a specific page of the document.
	 * @param pageId the ID of the page annotations are sought for
	 * @return an array holding the annotations
	 * @see de.uka.ipd.idaho.im.ImDocument#cleanupAnnotations()
	 */
	public ImAnnotation[] getAnnotations(int pageId) {
		return this.getAnnotations(null, pageId, pageId);
	}
	
	/**
	 * Retrieve the annotations overlapping a specific range of pages of the
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
	 * of the document.
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
	 * of pages of the document.
	 * @param the type of the sought annotations
	 * @param firstPageId the ID of the first page annotations are sought for
	 * @param lastPageId the ID of the last page annotations are sought for
	 * @return an array holding the annotations
	 * @see de.uka.ipd.idaho.im.ImDocument#cleanupAnnotations()
	 */
	public ImAnnotation[] getAnnotations(String type, int firstPageId, int lastPageId) {
		if (this.annotationsByPageId == null)
			return new ImAnnotation[0];
		ImAnnotationCollectorList overlappingAnnots = new ImAnnotationCollectorList();
		for (int p = firstPageId; p <= lastPageId; p++) {
			if (this.annotationsByPageId.length <= p)
				break;
			if (this.annotationsByPageId[p] == null)
				continue;
			for (int a = 0; a < this.annotationsByPageId[p].size(); a++) {
				ImDocumentAnnotation annot = this.annotationsByPageId[p].getAnnot(a);
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
			ImWordAnnotationList firstWordAnnots = ((ImWordAnnotationList) this.annotationsByFirstWord.get(firstWord.getLocalID()));
			if (firstWordAnnots != null) {
				for (int a = 0; a < firstWordAnnots.size(); a++)
					wordAnnots.addAnnot(firstWordAnnots.getAnnot(a));
			}
		}
		if (lastWord != null) {
			ImWordAnnotationList lastWordAnnots = ((ImWordAnnotationList) this.annotationsByLastWord.get(lastWord.getLocalID()));
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
		return this.getAnnotationsSpanning(null, word, word);
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
		return this.getAnnotationsSpanning(null, firstWord, lastWord);
	}
	
	/**
	 * Retrieve the annotations of a specific that span a specific word.
	 * @param type the type of the sought annotations
	 * @param word the word covered by the sought annotations
	 * @return an array holding the annotations
	 * @see de.uka.ipd.idaho.im.ImDocument#cleanupAnnotations()
	 */
	public ImAnnotation[] getAnnotationsSpanning(String type, ImWord word) {
		return this.getAnnotationsSpanning(type, word, word);
	}
	
	/**
	 * Retrieve the annotations of a specific type that span a specific range
	 * of words. The two argument words have to belong to the same logical text
	 * stream for the result of this method to be meaningful.
	 * @param type the type of the sought annotations
	 * @param firstWord the first word covered by the sought annotations
	 * @param lastWord the last word covered by the sought annotations
	 * @return an array holding the annotations
	 * @see de.uka.ipd.idaho.im.ImDocument#cleanupAnnotations()
	 */
	public ImAnnotation[] getAnnotationsSpanning(String type, ImWord firstWord, ImWord lastWord) {
		if (this.annotationsByPageId == null)
			return new ImAnnotation[0];
		ImAnnotationCollectorList spanningAnnots = new ImAnnotationCollectorList();
		if ((firstWord.pageId < this.annotationsByPageId.length) && (this.annotationsByPageId[firstWord.pageId] != null))
			for (int a = 0; a < this.annotationsByPageId[firstWord.pageId].size(); a++) {
				ImDocumentAnnotation annot = this.annotationsByPageId[firstWord.pageId].getAnnot(a);
				if ((type != null) && !type.equals(annot.type))
					continue;
				if (!firstWord.getTextStreamId().equals(annot.firstWord.getTextStreamId()))
					continue;
				if (firstWord.getTextStreamPos() < annot.firstWord.getTextStreamPos())
					continue;
				if (annot.lastWord.getTextStreamPos() < lastWord.getTextStreamPos())
					continue;
				spanningAnnots.addAnnot(annot);
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
		return this.getAnnotationsOverlapping(null, word, word);
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
		return this.getAnnotationsOverlapping(null, firstWord, lastWord);
	}
	
	/**
	 * Retrieve the annotations of a specific type that overlap a specific word.
	 * @param type the type of the sought annotations
	 * @param word the word covered by the sought annotations
	 * @return an array holding the annotations
	 * @see de.uka.ipd.idaho.im.ImDocument#cleanupAnnotations()
	 */
	public ImAnnotation[] getAnnotationsOverlapping(String type, ImWord word) {
		return this.getAnnotationsOverlapping(type, word, word);
	}
	
	/**
	 * Retrieve the annotations of a specific type that overlap a specific
	 * range of words. The two argument words have to belong to the same
	 * logical text stream for the result of this method to be meaningful.
	 * @param type the type of the sought annotations
	 * @param firstWord the first word of the range overlapped by the sought annotations
	 * @param lastWord the last word of the range overlapped by the sought annotations
	 * @return an array holding the annotations
	 * @see de.uka.ipd.idaho.im.ImDocument#cleanupAnnotations()
	 */
	public ImAnnotation[] getAnnotationsOverlapping(String type, ImWord firstWord, ImWord lastWord) {
		if (this.annotationsByPageId == null)
			return new ImAnnotation[0];
		ImAnnotationCollectorList overlappingAnnots = new ImAnnotationCollectorList();
		for (int p = firstWord.pageId; p <= lastWord.pageId; p++) {
			if (this.annotationsByPageId.length <= p)
				break;
			if (this.annotationsByPageId[p] == null)
				continue;
			for (int a = 0; a < this.annotationsByPageId[p].size(); a++) {
				ImDocumentAnnotation annot = this.annotationsByPageId[p].getAnnot(a);
				if ((type != null) && !type.equals(annot.type))
					continue;
				if (!firstWord.getTextStreamId().equals(annot.firstWord.getTextStreamId()))
					continue;
				if (annot.lastWord.getTextStreamPos() < firstWord.getTextStreamPos())
					continue;
				if (lastWord.getTextStreamPos() < annot.firstWord.getTextStreamPos())
					continue;
				overlappingAnnots.addAnnot(annot);
			}
		}
		return overlappingAnnots.toAnnotArray();
	}
	
	private static class ImAnnotationCollectorList {
		private ImDocumentAnnotation[] annots = new ImDocumentAnnotation[16];
		private int annotCount = 0;
		void addAnnot(ImDocumentAnnotation annot) {
			if (this.annotCount == this.annots.length) {
				ImDocumentAnnotation[] annots = new ImDocumentAnnotation[this.annots.length * 2];
				System.arraycopy(this.annots, 0, annots, 0, this.annots.length);
				this.annots = annots;
			}
			this.annots[this.annotCount++] = annot;
		}
		ImAnnotation[] toAnnotArray() {
			Arrays.sort(this.annots, 0, this.annotCount, imDocumentAnnotationOrder);
			int duplicates = 0;
			for (int a = 1; a < this.annotCount; a++) {
				if (this.annots[a] == this.annots[a - 1 - duplicates])
					duplicates++;
				else if (duplicates != 0)
					this.annots[a - duplicates] = this.annots[a];
			}
			this.annotCount -= duplicates;
			return ((this.annotCount < this.annots.length) ? Arrays.copyOf(this.annots, this.annotCount) : this.annots);
		}
	}
	
	/**
	 * Retrieve the types of annotations present in this document. The
	 * annotation types are in lexicographical order.
	 * @return an array holding the annotation types
	 */
	public String[] getAnnotationTypes() {
		if (TRACK_INSTANCES) this.accessHistory.accessed();
		return ((String[]) this.annotationsByType.keySet().toArray(new String[this.annotationsByType.size()]));
	}
	
	/**
	 * Retrieve the number of annotations present in this document.
	 * @return the number of annotations
	 */
	public int getAnnotationCount() {
		if (TRACK_INSTANCES) this.accessHistory.accessed();
		return this.annotations.size();
	}
	
	/**
	 * Retrieve the number of annotations of a specific type present in this
	 * document.
	 * @param type the annotation type to check
	 * @return the number of annotations of the argument type
	 */
	public int getAnnotationCount(String type) {
		if (TRACK_INSTANCES) this.accessHistory.accessed();
		if (type == null)
			return this.getAnnotationCount();
		ImDocumentAnnotationList typeAnnots = ((ImDocumentAnnotationList) this.annotationsByType.get(type));
		return ((typeAnnots == null) ? 0 : typeAnnots.size());
	}
	
	/**
	 * Retrieve a word located in a given page. This method is a shortcut for
	 * <code>getPage(pageId).getWord(box)</code>.
	 * @param pageId the ID of the page the word is located on
	 * @param bounds the bounding box of the word
	 * @return the word at the specified position
	 */
	public ImWord getWord(int pageId, BoundingBox bounds) {
		if (TRACK_INSTANCES) this.accessHistory.accessed();
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
		if (TRACK_INSTANCES) this.accessHistory.accessed();
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
		if (TRACK_INSTANCES) this.accessHistory.accessed();
		if ((localId == null) || (localId.indexOf('.') == -1))
			return null;
		ImPage page = ((ImPage) this.pagesById.get(new Integer(localId.substring(0, localId.indexOf('.')))));
		return ((page == null) ? null : page.getWord(localId.substring(localId.indexOf('['))));
	}
	
	/**
	 * Retrieve an object associated with the document by its local UID.The
	 * argument UID is expected to be a 32 character HEX string in upper case.
	 * @param localUid the document local UID of the object
	 * @return the object with the specified local UID
	 */
	public ImObject getObjectByLocalUID(String localUid) {
		if (TRACK_INSTANCES) this.accessHistory.accessed();
		if (docLocalUID.equals(localUid))
			return this;
		else return ((ImObject) this.objectsByLocalUID.get(localUid));
	}
	
	/**
	 * Retrieve an object associated with the document by its UUID. The
	 * argument UUID is expected to be a 32 character HEX string in upper case.
	 * @param uuid the UUID of the object
	 * @return the object with the specified UUID
	 */
	public ImObject getObjectByUUID(String uuid) {
		if (TRACK_INSTANCES) this.accessHistory.accessed();
		String luid = RandomByteSource.getHexXor(uuid, this.docId);
		return this.getObjectByLocalUID(luid);
	}
	
	/**
	 * Retrieve all layout words that are the first of a logical text stream in
	 * the document as a whole, i.e., words that have no predecessor.
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
	 * the document as a whole, i.e., words that have no successor.
	 * @return an array holding the text stream heads
	 */
	public ImWord[] getTextStreamTails() {
		this.ensureTextStreamEnds();
		ImWord[] tsts = new ImWord[this.textStreamTails.length];
		System.arraycopy(this.textStreamTails, 0, tsts, 0, tsts.length);
		return tsts;
	}
	
	/**
	 * Retrieve the number of words in this document.
	 * @return the number of words
	 */
	public int getWordCount() {
		if (this.wordCount < 0) {
			int wc = 0;
			for (Iterator pit = this.pagesById.keySet().iterator(); pit.hasNext();)
				wc += ((ImPage) this.pagesById.get(pit.next())).getWordCount();
			this.wordCount = wc;
		}
		return this.wordCount;
	}
	
	void invalidateTextStreamEnds() {
		this.textStreamHeads = null;
		this.textStreamTails = null;
	}
	
	private void ensureTextStreamEnds() {
		if (TRACK_INSTANCES) this.accessHistory.accessed();
		if ((this.textStreamHeads != null) && (this.textStreamTails != null))
			return;
		ArrayList tshs = new ArrayList(8);
		ArrayList tsts = new ArrayList(8);
		for (Iterator pit = this.pagesById.keySet().iterator(); pit.hasNext();) {
			Object pid = pit.next();
			ImPage page = ((ImPage) this.pagesById.get(pid));
			ImWord[] pTshs = page.getTextStreamHeads();
			for (int h = 0; h < pTshs.length; h++) {
				if ((pTshs[h].getPreviousWord() == null) || (pTshs[h].getPreviousWord().getPage() == null))
					tshs.add(pTshs[h]);
			}
			ImWord[] pTsts = page.getTextStreamTails();
			for (int t = 0; t < pTsts.length; t++) {
				if ((pTsts[t].getNextWord() == null) || (pTsts[t].getNextWord().getPage() == null))
					tsts.add(pTsts[t]);
			}
		}
		this.textStreamHeads = ((ImWord[]) tshs.toArray(new ImWord[tshs.size()]));
		this.textStreamTails = ((ImWord[]) tsts.toArray(new ImWord[tsts.size()]));
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
		else if (this.listeners.contains(dl))
			return;
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
	
	void notifyTypeChanged(ImObject object, String oldType, String oldLuid) {
		if (TRACK_INSTANCES) this.accessHistory.accessed();
		synchronized (this.objectsByLocalUID) {
			if (oldLuid != null)
				this.objectsByLocalUID.remove(oldLuid);
			this.objectsByLocalUID.put(object.getLocalUID(), object);
		}
		if (this.listeners == null)
			return;
		for (int l = 0; l < this.listeners.size(); l++)
			((ImDocumentListener) this.listeners.get(l)).typeChanged(object, oldType);
	}
	
	void notifyAttributeChanged(ImObject object, String attributeName, Object oldValue) {
		if (TRACK_INSTANCES) this.accessHistory.accessed();
		if (object instanceof ImWord) {
			if (ImWord.NEXT_WORD_ATTRIBUTE.equals(attributeName))
				this.textStreamModCount++;
			else if (ImWord.PREVIOUS_WORD_ATTRIBUTE.equals(attributeName))
				this.textStreamModCount++;
		}
		if (this.listeners == null)
			return;
		for (int l = 0; l < this.listeners.size(); l++)
			((ImDocumentListener) this.listeners.get(l)).attributeChanged(object, attributeName, oldValue);
	}
	
	private void notifySupplementChanged(String supplementId, ImSupplement oldValue) {
		if (TRACK_INSTANCES) this.accessHistory.accessed();
		if (this.listeners == null)
			return;
		for (int l = 0; l < this.listeners.size(); l++)
			((ImDocumentListener) this.listeners.get(l)).supplementChanged(supplementId, oldValue);
	}
	
	private void notifyFontChanged(String fontName, ImFont oldValue) {
		if (TRACK_INSTANCES) this.accessHistory.accessed();
		if (this.listeners == null)
			return;
		for (int l = 0; l < this.listeners.size(); l++)
			((ImDocumentListener) this.listeners.get(l)).fontChanged(fontName, oldValue);
	}
	
	void notifyRegionAdded(ImRegion region) {
		if (TRACK_INSTANCES) this.accessHistory.accessed();
		if (region instanceof ImWord) {
			this.textStreamModCount++;
			this.wordCount = -1;
		}
		else this.regionTypesClean = false;
		synchronized (this.objectsByLocalUID) {
			this.objectsByLocalUID.put(region.getLocalUID(), region);
		}
		if (this.listeners == null)
			return;
		for (int l = 0; l < this.listeners.size(); l++)
			((ImDocumentListener) this.listeners.get(l)).regionAdded(region);
	}
	
	void notifyRegionRemoved(ImRegion region) {
		if (TRACK_INSTANCES) this.accessHistory.accessed();
		if (region instanceof ImWord) {
			this.textStreamModCount++;
			this.wordCount = -1;
		}
		else this.regionTypesClean = false;
		synchronized (this.objectsByLocalUID) {
			this.objectsByLocalUID.remove(region.getLocalUID());
		}
		if (this.listeners == null)
			return;
		for (int l = 0; l < this.listeners.size(); l++)
			((ImDocumentListener) this.listeners.get(l)).regionRemoved(region);
	}
	
	private void notifyAnnotationAdded(ImAnnotation annotation) {
		if (TRACK_INSTANCES) this.accessHistory.accessed();
		if (this.listeners == null)
			return;
		for (int l = 0; l < this.listeners.size(); l++)
			((ImDocumentListener) this.listeners.get(l)).annotationAdded(annotation);
	}
	
	private void notifyAnnotationRemoved(ImAnnotation annotation) {
		if (TRACK_INSTANCES) this.accessHistory.accessed();
		if (this.listeners == null)
			return;
		for (int l = 0; l < this.listeners.size(); l++)
			((ImDocumentListener) this.listeners.get(l)).annotationRemoved(annotation);
	}
}