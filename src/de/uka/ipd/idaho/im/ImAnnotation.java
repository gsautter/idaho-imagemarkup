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



/**
 * A semantic annotation in an image markup document. An annotation always
 * refers to a single logical text stream, marking a part thereof.
 * 
 * @author sautter
 */
public interface ImAnnotation extends ImObject {
	
	/** the name to use to retrieve the first word in the annotation as a virtual
	 * attribute via the generic <code>getAttribute()</code> methods */
	public static final String FIRST_WORD_ATTRIBUTE = "firstWord";
	
	/** the name to use to retrieve the first word in the annotation as a virtual
	 * attribute via the generic <code>getAttribute()</code> methods */
	public static final String LAST_WORD_ATTRIBUTE = "lastWord";
//	
//	/** the name to use to retrieve the first string value in the annotation as a
//	 * virtual attribute via the generic <code>getAttribute()</code> methods;
//	 * this is relevant in situations when the first value is actually a suffix
//	 * of the first word's string value, e.g. excludes a leading punctuation mark */
//	public static final String FIRST_VALUE_ATTRIBUTE = "firstValue";
//	
//	/** the name to use to retrieve the first string value in the annotation as a
//	 * virtual attribute via the generic <code>getAttribute()</code> methods;
//	 * this is relevant in situations when the last value is actually a prefix
//	 * of the last word's string value, e.g. excludes a leading punctuation mark */
//	public static final String LAST_VALUE_ATTRIBUTE = "lastValue";
	
	/**
	 * Retrieve the first word included in this annotation.
	 * @return the first word included in this annotation
	 */
	public abstract ImWord getFirstWord();
	
	/**
	 * Set the first word included in this annotation.
	 * @param firstWord the word to use as the new first
	 */
	public abstract void setFirstWord(ImWord firstWord);
	
	/**
	 * Retrieve the last word included in this annotation.
	 * @return the last word included in this annotation
	 */
	public abstract ImWord getLastWord();
	
	/**
	 * Set the last word included in this annotation.
	 * @param lastWord the word to use as the new last
	 */
	public abstract void setLastWord(ImWord lastWord);
}
//public class ImAnnotation extends ImObject implements ImWordListener {
//	
//	/** the name to use to retrieve the first word in the annotation as a virtual
//	 * attribute via the generic <code>getAttribute()</code> methods */
//	public static final String FIRST_WORD_ATTRIBUTE = "firstWord";
//	
//	/** the name to use to retrieve the first word in the annotation as a virtual
//	 * attribute via the generic <code>getAttribute()</code> methods */
//	public static final String LAST_WORD_ATTRIBUTE = "lastWord";
//	
//	/** the name to use to retrieve the first string value in the annotation as a
//	 * virtual attribute via the generic <code>getAttribute()</code> methods;
//	 * this is relevant in situations when the first value is actually a suffix
//	 * of the first word's string value, e.g. excludes a leading punctuation mark */
//	public static final String FIRST_VALUE_ATTRIBUTE = "firstValue";
//	
//	/** the name to use to retrieve the first string value in the annotation as a
//	 * virtual attribute via the generic <code>getAttribute()</code> methods;
//	 * this is relevant in situations when the last value is actually a prefix
//	 * of the last word's string value, e.g. excludes a leading punctuation mark */
//	public static final String LAST_VALUE_ATTRIBUTE = "lastValue";
//	
//	private ImDocument doc;
//	
//	private ImWord firstWord;
//	private ImWord lastWord;
//	
//	private String firstValue;
//	private String lastValue;
//	
//	final long createTime;
//	
//	/** Constructor (automatically adds the annotation to the argument document)
//	 * @param doc the document the annotation belongs to
//	 * @param word the word included in the annotation
//	 */
//	public ImAnnotation(ImDocument doc, ImWord word) {
//		this(doc, word, word, "annotation");
//	}
//	
//	/** Constructor (automatically adds the annotation to the argument document)
//	 * @param doc the document the annotation belongs to
//	 * @param word the word included in the annotation
//	 * @param type the type of the annotation
//	 */
//	public ImAnnotation(ImDocument doc, ImWord word, String type) {
//		this(doc, word, word, type);
//	}
//	
//	/** Constructor (automatically adds the annotation to the argument document)
//	 * @param doc the document the annotation belongs to
//	 * @param firstWord the first word included in the annotation
//	 * @param lastWord the last word included in the annotation
//	 */
//	public ImAnnotation(ImDocument doc, ImWord firstWord, ImWord lastWord) {
//		this(doc, firstWord, lastWord, "annotation");
//	}
//	
//	/** Constructor (automatically adds the annotation to the argument document)
//	 * @param doc the document the annotation belongs to
//	 * @param firstWord the first word included in the annotation
//	 * @param lastWord the last word included in the annotation
//	 * @param type the type of the annotation
//	 */
//	public ImAnnotation(ImDocument doc, ImWord firstWord, ImWord lastWord, String type) {
//		super(doc.docId);
//		this.doc = doc;
//		this.firstWord = firstWord;
//		this.lastWord = lastWord;
//		this.firstValue = this.firstWord.getString();
//		this.lastValue = this.lastWord.getString();
//		this.firstWord.addListener(this);
//		this.lastWord.addListener(this);
//		super.setType((type == null) ? "annotation" : type);
//		this.createTime = System.currentTimeMillis();
//		this.doc.addAnnotation(this);
//	}
//	
//	/* (non-Javadoc)
//	 * @see de.uka.ipd.idaho.im.ImObject#setType(java.lang.String)
//	 */
//	public void setType(String type) {
//		String oldType = this.getType();
//		super.setType(type);
//		this.doc.annotationTypeChanged(this, oldType);
//	}
//	
//	/* (non-Javadoc)
//	 * @see de.uka.ipd.idaho.im.ImObject#getDocument()
//	 */
//	public ImDocument getDocument() {
//		return this.doc;
//	}
//	
//	/**
//	 * Retrieve the first word included in this annotation.
//	 * @return the first word included in this annotation
//	 */
//	public ImWord getFirstWord() {
//		return this.firstWord;
//	}
//	
//	/**
//	 * Set the first word included in this annotation.
//	 * @param firstWord the word to use as the new first
//	 */
//	public void setFirstWord(ImWord firstWord) {
//		this.firstWord.removeListener(this);
//		this.firstWord = firstWord;
//		this.firstValue = this.firstWord.getString();
//		this.firstWord.addListener(this);
//	}
//	
//	/**
//	 * Retrieve the last word included in this annotation.
//	 * @return the last word included in this annotation
//	 */
//	public ImWord getLastWord() {
//		return this.lastWord;
//	}
//	
//	/**
//	 * Set the last word included in this annotation.
//	 * @param lastWord the word to use as the new last
//	 */
//	public void setLastWord(ImWord lastWord) {
//		this.lastWord.removeListener(this);
//		this.lastWord = lastWord;
//		this.lastValue = this.lastWord.getString();
//		this.lastWord.addListener(this);
//	}
//	
//	/* (non-Javadoc)
//	 * @see de.uka.ipd.idaho.im.ImWord.ImWordListener#wordPropertyChanged(de.uka.ipd.idaho.im.ImWord, java.lang.String, java.lang.Object)
//	 */
//	public void wordPropertyChanged(ImWord word, String property, Object oldValue) {
//		if (!STRING_ATTRIBUTE.equals(property))
//			return;
//		if (word == this.firstWord)
//			this.setFirstValue(word.getString());
//		else if (word == this.lastWord)
//			this.setLastValue(word.getString());
//		//	TODO try to adjust this more intelligently, e.g. using token sequence, Levenshtein, etc.
//		//	TODO use tokens instead of strings ==> way easier, as now implemented in GantaDocument
//	}
//	
//	/**
//	 * Retrieve the first value. This is helpful in situations when the first
//	 * value is actually a suffix of the first word's string value, e.g.
//	 * excludes a leading punctuation mark.
//	 * @return the first value
//	 */
//	public String getFirstValue() {
//		return this.firstValue;
//	}
//	
//	/**
//	 * Update the first value. This is helpful in situations when the first
//	 * value is actually a suffix of the first word's string value, e.g.
//	 * excludes a leading punctuation mark. The argument string has to be a
//	 * suffix of the first word's string value, or an infix, if first and last
//	 * word are the same.
//	 * @param firstValue the first value to set
//	 */
//	public void setFirstValue(String firstValue) {
//		if (this.firstWord == this.lastWord) {
//			if (this.firstWord.getString().indexOf(firstValue) == -1)
//				throw new IllegalArgumentException("'" + firstValue + "' is not an infix of '" + this.firstWord.getString() + "'");
//			this.firstValue = firstValue;
//			this.lastValue = firstValue;
//		}
//		else {
//			if (!this.firstWord.getString().endsWith(firstValue))
//				throw new IllegalArgumentException("'" + firstValue + "' is not a suffix of '" + this.firstWord.getString() + "'");
//			this.firstValue = firstValue;
//		}
//	}
//	
//	/**
//	 * Retrieve the last value. This is helpful in situations when the last
//	 * value is actually a prefix of the last word's string value, e.g.
//	 * excludes a tailing punctuation mark.
//	 * @return the last value
//	 */
//	public String getLastValue() {
//		return this.lastValue;
//	}
//	
//	/**
//	 * Update the last value. This is helpful in situations when the larst
//	 * value is actually a prefix of the last word's string value, e.g.
//	 * excludes a tailing punctuation mark. The argument string has to be a
//	 * prefix of the last word's string value, or an infix, if first and last
//	 * word are the same.
//	 * @param lastValue the last value to set
//	 */
//	public void setLastValue(String lastValue) {
//		if (this.firstWord == this.lastWord) {
//			if (this.firstWord.getString().indexOf(lastValue) == -1)
//				throw new IllegalArgumentException("'" + lastValue + "' is not an infix of '" + this.firstWord.getString() + "'");
//			this.firstValue = lastValue;
//			this.lastValue = lastValue;
//		}
//		else {
//			if (!this.lastWord.getString().startsWith(lastValue))
//				throw new IllegalArgumentException("'" + lastValue + "' is not a prefix of '" + this.lastWord.getString() + "'");
//			this.lastValue = lastValue;
//		}
//	}
//	
//	/* (non-Javadoc)
//	 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#getAttribute(java.lang.String)
//	 */
//	public Object getAttribute(String name) {
//		if (FIRST_WORD_ATTRIBUTE.equals(name))
//			return this.firstWord;
//		if (LAST_WORD_ATTRIBUTE.equals(name))
//			return this.lastWord;
//		if (FIRST_VALUE_ATTRIBUTE.equals(name))
//			return this.firstValue;
//		if (LAST_VALUE_ATTRIBUTE.equals(name))
//			return this.lastValue;
//		return super.getAttribute(name);
//	}
//	
//	/* (non-Javadoc)
//	 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#getAttribute(java.lang.String, java.lang.Object)
//	 */
//	public Object getAttribute(String name, Object def) {
//		if (FIRST_WORD_ATTRIBUTE.equals(name))
//			return ((this.firstWord == null) ? def : this.firstWord);
//		if (LAST_WORD_ATTRIBUTE.equals(name))
//			return ((this.lastWord == null) ? def : this.lastWord);
//		if (FIRST_VALUE_ATTRIBUTE.equals(name))
//			return ((this.firstValue == null) ? def : this.firstValue);
//		if (LAST_VALUE_ATTRIBUTE.equals(name))
//			return ((this.lastValue == null) ? def : this.lastValue);
//		return super.getAttribute(name, def);
//	}
//	
//	/* (non-Javadoc)
//	 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#hasAttribute(java.lang.String)
//	 */
//	public boolean hasAttribute(String name) {
//		if (FIRST_WORD_ATTRIBUTE.equals(name))
//			return (this.firstWord != null);
//		if (LAST_WORD_ATTRIBUTE.equals(name))
//			return (this.lastWord != null);
//		if (FIRST_VALUE_ATTRIBUTE.equals(name))
//			return (this.firstValue != null);
//		if (LAST_VALUE_ATTRIBUTE.equals(name))
//			return (this.lastValue != null);
//		return super.hasAttribute(name);
//	}
//}