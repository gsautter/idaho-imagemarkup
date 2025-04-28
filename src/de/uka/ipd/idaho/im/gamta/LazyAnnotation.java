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

import de.uka.ipd.idaho.gamta.Annotation;
import de.uka.ipd.idaho.gamta.AnnotationUtils;
import de.uka.ipd.idaho.gamta.Attributed;
import de.uka.ipd.idaho.gamta.Gamta;
import de.uka.ipd.idaho.gamta.QueriableAnnotation;
import de.uka.ipd.idaho.gamta.Token;
import de.uka.ipd.idaho.gamta.TokenSequence;
import de.uka.ipd.idaho.gamta.Tokenizer;
import de.uka.ipd.idaho.im.ImAnnotation;
import de.uka.ipd.idaho.im.ImDocument;
import de.uka.ipd.idaho.im.ImObject;
import de.uka.ipd.idaho.im.ImRegion;
import de.uka.ipd.idaho.im.ImWord;

/**
 * Lazy initialization adapter for Image Markup document GAMTA wrapper.
 * 
 * @author sautter
 */
public class LazyAnnotation implements Annotation {
	final LazyAnnotationBase base;
	
	/**
	 * Constructor for lazily wrapping whole IM documents
	 * @param doc the IM document to wrap
	 * @param flags the wrapper flags to use
	 */
	public LazyAnnotation(ImDocument doc, int flags) {
		this.base = new LazyAnnotationBase(doc, flags);
	}
	
	/**
	 * Constructor for lazily wrapping some annotation on IM documents
	 * @param annot the IM document annotation to wrap
	 * @param flags the wrapper flags to use
	 */
	public LazyAnnotation(ImAnnotation annot, int flags) {
		this.base = new LazyAnnotationBase(annot, flags);
	}
	
	/**
	 * Constructor for lazily wrapping some region on IM documents
	 * @param region the IM document region to wrap
	 * @param flags the wrapper flags to use
	 */
	public LazyAnnotation(ImRegion region, int flags) {
		this.base = new LazyAnnotationBase(region, flags);
	}
	
	/**
	 * Constructor for migrating between different levels of implementation,
	 * e.g. to create an editable clone of a queryable annotation (which the
	 * enclosed GAMTA wrapper readily supports).
	 * @param model the lazy annotation whose data to reuse
	 */
	public LazyAnnotation(LazyAnnotation model) {
		this.base = model.base;
	}
	
	public String[] getAttributeNames() {
		return this.base.imObj.getAttributeNames();
	}
	public boolean hasAttribute(String name) {
		return this.base.imObj.hasAttribute(name);
	}
	public Object getAttribute(String name) {
		if (ANNOTATION_ID_ATTRIBUTE.equals(name))
			return this.getAnnotationID();
		else return this.base.imObj.getAttribute(name);
	}
	public Object getAttribute(String name, Object def) {
		if (ANNOTATION_ID_ATTRIBUTE.equals(name))
			return this.getAnnotationID();
		else return this.base.imObj.getAttribute(name, def);
	}
	public void copyAttributes(Attributed source) {}
	public void setAttribute(String name) {
		try {
			this.base.updateViaData = true;
			this.base.imObj.setAttribute(name);
		}
		finally {
			this.base.updateViaData = false;
		}
	}
	public Object setAttribute(String name, Object value) {
		try {
			this.base.updateViaData = true;
			return this.base.imObj.setAttribute(name, value);
		}
		finally {
			this.base.updateViaData = false;
		}
	}
	public Object removeAttribute(String name) {
		try {
			this.base.updateViaData = true;
			return this.base.imObj.removeAttribute(name);
		}
		finally {
			this.base.updateViaData = false;
		}
	}
	public void clearAttributes() {}
	
	public String getType() {
		return this.base.imObj.getType();
	}
	public String changeTypeTo(String newType) {
		String oldType = this.base.imObj.getType();
		try {
			this.base.updateViaData = true;
			this.base.imObj.setType(newType);
			return oldType;
		}
		finally {
			this.base.updateViaData = false;
		}
	}
	public String getAnnotationID() {
		return this.base.imObj.getUUID();
	}
	public String getDocumentProperty(String propertyName) {
		return this.base.imObj.getDocumentProperty(propertyName);
	}
	public String getDocumentProperty(String propertyName, String defaultValue) {
		return this.base.imObj.getDocumentProperty(propertyName, defaultValue);
	}
	public String[] getDocumentPropertyNames() {
		return this.base.imObj.getDocumentPropertyNames();
	}
	public int getStartIndex() {
		this.ensureDataInitialized();
		return this.base.data.getStartIndex();
	}
	public int getEndIndex() {
		this.ensureDataInitialized();
		return this.base.data.getEndIndex();
	}
	public int compareTo(Object obj) {
		return ((obj instanceof Annotation) ? AnnotationUtils.compare(this, ((Annotation) obj)) : -1);
	}
	public String getValue() {
		this.ensureDataInitialized();
		return this.base.data.getValue();
	}
	public String toXML() {
		this.ensureDataInitialized();
		return this.base.data.toXML();
	}
	public QueriableAnnotation getDocument() {
		this.ensureDataInitialized();
		return this.base.data.getDocument();
	}
	
	public Tokenizer getTokenizer() {
		return this.base.getTokenizer();
	}
	public String getLeadingWhitespace() {
		return "";
	}
	public int size() {
		this.ensureDataInitialized();
		return this.base.data.size();
	}
	public Token tokenAt(int index) {
		this.ensureDataInitialized();
		return this.base.data.tokenAt(index);
	}
	public Token firstToken() {
		this.ensureDataInitialized();
		return this.base.data.firstToken();
	}
	public Token lastToken() {
		this.ensureDataInitialized();
		return this.base.data.lastToken();
	}
	public String valueAt(int index) {
		this.ensureDataInitialized();
		return this.base.data.valueAt(index);
	}
	public String firstValue() {
		this.ensureDataInitialized();
		return this.base.data.firstValue();
	}
	public String lastValue() {
		this.ensureDataInitialized();
		return this.base.data.lastValue();
	}
	public String getWhitespaceAfter(int index) {
		this.ensureDataInitialized();
		return this.base.data.getWhitespaceAfter(index);
	}
	public TokenSequence getSubsequence(int start, int size) {
		this.ensureDataInitialized();
		return this.base.data.getSubsequence(start, size);
	}
	
	public int getStartOffset() {
		this.ensureDataInitialized();
		return this.base.data.getStartOffset();
	}
	public int getEndOffset() {
		this.ensureDataInitialized();
		return this.base.data.getEndOffset();
	}
	public int length() {
		this.ensureDataInitialized();
		return this.base.data.length();
	}
	public char charAt(int index) {
		this.ensureDataInitialized();
		return this.base.data.charAt(index);
	}
	public CharSequence subSequence(int start, int end) {
		this.ensureDataInitialized();
		return this.base.data.subSequence(start, end);
	}
	
//	public int getFlags() {
//		return this.base.flags;
//	}
//	public void setFlags(int flags) {
//		this.base.setFlags(flags);
//	}
//	public ImAnnotation getBaseAnnotation() {
//		return this.base.imAnnot;
//	}
//	public ImDocument getBaseDocument() {
//		return this.base.imDoc;
//	}
//	void ensureDataInitialized() {
//		this.base.ensureDataInitialized();
//	}
	public int getFlags() {
		return this.base.flags;
	}
	public void setFlags(int flags) {
		this.base.setFlags(flags);
	}
	public void invalidateData() {
		this.base.invalidateData(); // clear out any extant wrapper to reduce memory consumption
	}
	void ensureDataInitialized() {
		this.base.ensureValidData();
	}
	public ImAnnotation getBaseAnnotation() {
		return this.base.imAnnot;
	}
	public ImDocument getImDocument() {
		return this.base.getDocument();
	}
	public ImObject getImBasisOf(Annotation annot) {
		if (annot == this)
			return ((this.base.imAnnot == null) ? this.base.getDocument() : this.base.imAnnot);
		else {
			this.ensureDataInitialized();
			return this.base.data.basisOf(annot);
		}
	}
	public ImWord getImBasisOf(Token token) {
		this.ensureDataInitialized();
		return this.base.data.basisOf(token);
	}
	public int indexOfImWord(ImWord imw) {
		this.ensureDataInitialized();
		return this.base.data.getTokenIndexOf(imw);
	}
	public ImWord getFirstImWordOf(Token token) {
		this.ensureDataInitialized();
		return this.base.data.firstWordOf(token);
	}
	public ImWord getLastImWordOf(Token token) {
		this.ensureDataInitialized();
		return this.base.data.lastWordOf(token);
	}
	public ImWord getFirstImWordOf(Annotation annot) {
		this.ensureDataInitialized();
		return this.base.data.firstWordOf(annot);
	}
	public ImWord getLastImWordOf(Annotation annot) {
		this.ensureDataInitialized();
		return this.base.data.lastWordOf(annot);
	}
	
	static class LazyAnnotationBase/* implements ImDocumentListener*/ {
		final ImDocument imDoc;
		final ImAnnotation imAnnot;
		final ImRegion imRegion;
		final ImObject imObj;
		int flags;
		ImDocumentRoot data;
		boolean updateViaData = false;
		
		LazyAnnotationBase(ImDocument doc, int flags) {
			this.imDoc = doc;
			this.imAnnot = null;
			this.imRegion = null;
			this.imObj = doc;
			this.flags = flags;
//			this.getDocument().addDocumentListener(this);
		}
		LazyAnnotationBase(ImAnnotation annot, int flags) {
			this.imDoc = null;
			this.imAnnot = annot;
			this.imRegion = null;
			this.imObj = annot;
			this.flags = flags;
//			this.getDocument().addDocumentListener(this);
		}
		LazyAnnotationBase(ImRegion region, int flags) {
			this.imDoc = null;
			this.imAnnot = null;
			this.imRegion = region;
			this.imObj = region;
			this.flags = flags;
//			this.getDocument().addDocumentListener(this);
		}
		
		private ImDocument getDocument() {
			ImDocument doc = this.imDoc;
			if ((doc == null) && (this.imObj != null))
				doc = this.imObj.getDocument();
			if ((doc == null) && (this.imRegion != null))
				doc = this.imDoc;
			if ((doc == null) && (this.imAnnot != null)) {
				doc = this.imAnnot.getDocument();
				if (doc == null) /* annotation has been detached */ {
					ImWord firstWord = this.imAnnot.getFirstWord();
					if (firstWord != null)
						doc = firstWord.getDocument();
				}
				if (doc == null) /* annotation has been detached */ {
					ImWord lastWord = this.imAnnot.getLastWord();
					if (lastWord != null)
						doc = lastWord.getDocument();
				}
			}
			return doc;
		}
//		
//		public void typeChanged(ImObject object, String oldType) {
//			if (object instanceof ImAnnotation)
//				this.annotationChanged((ImAnnotation) object);
//		}
//		public void supplementChanged(String supplementId, ImSupplement oldValue) {
//			//	supplements won't affect GAMTA wrapper, so we're OK
//		}
//		public void attributeChanged(ImObject object, String attributeName, Object oldValue) {
//			ImWord imt;
//			if ((object instanceof ImWord) && ImWord.NEXT_RELATION_ATTRIBUTE.equals(attributeName))
//				imt = ((ImWord) object);
//			else return; // regular attributes are transparent
//			if (this.imAnnot == null)
//				this.data = null; // whitespace and thus character offsets out of place across whole wrapper
//			else if (imt.getAbsoluteIndex() < getStartIndex()) {} // change before wrapped annotation, doesn't affect wrapper
//			else if (getEndIndex() <= imt.getAbsoluteIndex()) {} // change after wrapped annotation, doesn't affect wrapper
//			else this.data = null; // whitespace and thus character offsets out of place within wrapped annotation, let's be safe
//		}
//		public void annotationRemoved(ImAnnotation annotation) {
//			this.annotationChanged(annotation);
//			if (annotation == this.imAnnot)
//				this.getDocument().removeDocumentListener(this); // own basis removed, clean ourselves up
//		}
//		public void annotationAdded(ImAnnotation annotation) {
//			this.annotationChanged(annotation);
//			if (annotation == this.imAnnot)
//				this.getDocument().addDocumentListener(this); // own basis un-removed, start over listening
//		}
//		private void annotationChanged(ImAnnotation ima) {
//			if (this.updateViaData) {} // this one keeps wrapper in sync
//			
//			//	full-document wrapper, definitely invalidating annotation indexes
//			else if (this.imAnnot == null)
//				this.data = null; // full-document wrapper, definitely affected by this one TODO have wrapper proper listen and adjust
//			
//			//	check boundary tokens
//			else {
//				ImWord fImt = ima.getFirstWord();
//				ImWord lImt = ima.getLastWord();
//				if ((fImt == null) || (lImt == null)) {} // empty annotations don't reflect in wrapper at all
//				else if (lImt.getAbsoluteIndex() < getStartIndex()) {} // change before wrapped annotation, doesn't affect wrapper
//				else if (getEndIndex() <= fImt.getAbsoluteIndex()) {} // change after wrapped annotation, doesn't affect wrapper
//				else this.data = null; // change within wrapper, invalidating annotation indexes TODO have wrapper proper listen and adjust
//			}
//		}
//		public void fontChanged(String fontName, ImFont oldValue) { /* fonts have no bearing on validity of wrapper */ }
//		public void regionAdded(ImRegion region) {
//			//	TODO react depending upon text stream normalization level ...
//			//	TODO ... as well as page ranges
//		}
//		public void regionRemoved(ImRegion region) {
//			//	TODO react depending upon text stream normalization level ...
//			//	TODO ... as well as page ranges
//		}
//		
//		int getStartIndex() {
//			if (this.imDoc != null)
//				return 0;
//			else if (this.imAnnot != null) {
//				ImWord firstWord = this.imAnnot.getFirstWord();
//				if (firstWord == null)
//					return (this.imAnnot.getLastWord().getAbsoluteIndex() + 1);
//				else return firstWord.getAbsoluteIndex();
//			}
//			else return -1; // should never happen, but lets be safe
//		}
//		int getEndIndex() {
//			if (this.imDoc != null)
//				return this.imDoc.getWordCount();
//			else if (this.imAnnot != null) {
//				ImWord lastWord = this.imAnnot.getLastWord();
//				if (lastWord == null)
//					return this.imAnnot.getFirstWord().getAbsoluteIndex();
//				else return (lastWord.getAbsoluteIndex() + 1);
//			}
//			else return -1; // should never happen, but lets be safe
//		}
//		int size() {
//			if (this.data != null)
//				return this.data.size();
//			else if (this.imDoc != null)
//				return this.imDoc.getWordCount();
//			else if (this.imAnnot != null) {
//				ImWord firstWord = this.imAnnot.getFirstWord();
//				if (firstWord == null)
//					return 0;
//				ImWord lastWord = this.imAnnot.getLastWord();
//				if (lastWord == null)
//					return 0;
//				return (lastWord.getAbsoluteIndex() - firstWord.getAbsoluteIndex() + 1);
//			}
//			else return -1; // should never happen, but lets be safe
//		}
		
		Tokenizer getTokenizer() {
			return ((Tokenizer) this.getDocument().getAttribute(ImDocument.TOKENIZER_ATTRIBUTE, Gamta.getDefaultTokenizer()));
		}
		String[] getAnnotationTypes() {
			if (this.data != null)
				return this.data.getAnnotationTypes();
			else if (this.imDoc != null)
				return this.imDoc.getAnnotationTypes(); // might factor in types of empty-only annotations, but risk is quite theoretical
			this.ensureValidData();
			return this.data.getAnnotationTypes();
		}
		
		void setFlags(int flags) {
			if (this.flags == flags)
				return;
			this.flags = flags;
			this.data = null; // TODO any situations where we could avoid invalidation ???
		}
		void invalidateData() {
			if (this.data != null)
				this.data.dispose();
			this.data = null;
		}
		void ensureValidData() {
//			if (this.data != null) {}
			if (this.data != null) {
				if (this.data.isValid())
					return;
				else this.data.dispose();
			}
			System.out.println("LazyAnnotationBase: (re-)initializing GAMTA wrapper");
			if (this.imAnnot != null)
				this.data = new ImDocumentRoot(this.imAnnot, this.flags);
			else if (this.imRegion != null)
				this.data = new ImDocumentRoot(this.imAnnot, this.flags);
			else if (this.imDoc != null)
				this.data = new ImDocumentRoot(this.imDoc, this.flags);
		}
	}
}
