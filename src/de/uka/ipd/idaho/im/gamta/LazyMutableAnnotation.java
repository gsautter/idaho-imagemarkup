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
import de.uka.ipd.idaho.gamta.CharSequenceListener;
import de.uka.ipd.idaho.gamta.MutableAnnotation;
import de.uka.ipd.idaho.gamta.MutableCharSequence;
import de.uka.ipd.idaho.gamta.MutableTokenSequence;
import de.uka.ipd.idaho.gamta.TokenSequence;
import de.uka.ipd.idaho.gamta.TokenSequenceListener;
import de.uka.ipd.idaho.im.ImAnnotation;
import de.uka.ipd.idaho.im.ImDocument;
import de.uka.ipd.idaho.im.ImRegion;

/**
 * Lazy initialization adapter for Image Markup document GAMTA wrapper.
 * 
 * @author sautter
 */
public class LazyMutableAnnotation extends LazyEditableAnnotation implements MutableAnnotation {
	private static final String defaultTokenErrorMessage = "Characters and tokens cannot be modified on this document";
	private String tokenErrorMessage = defaultTokenErrorMessage;
	
	/**
	 * Constructor for lazily wrapping whole IM documents
	 * @param doc the IM document to wrap
	 * @param flags the wrapper flags to use
	 */
	public LazyMutableAnnotation(ImDocument doc, int flags) {
		super(doc, flags);
	}
	
	/**
	 * Constructor for lazily wrapping some annotation on IM documents
	 * @param annot the IM document annotation to wrap
	 * @param flags the wrapper flags to use
	 */
	public LazyMutableAnnotation(ImAnnotation annot, int flags) {
		super(annot, flags);
	}
	
	/**
	 * Constructor for lazily wrapping some region on IM documents
	 * @param region the IM document region to wrap
	 * @param flags the wrapper flags to use
	 */
	public LazyMutableAnnotation(ImRegion region, int flags) {
		super(region, flags);
	}
	
	/**
	 * Constructor for migrating between different levels of implementation,
	 * e.g. to create an editable clone of a queryable annotation (which the
	 * enclosed GAMTA wrapper readily supports).
	 * @param model the lazy annotation whose data to reuse
	 */
	public LazyMutableAnnotation(LazyAnnotation model) {
		super(model);
		if (model instanceof LazyMutableAnnotation)
			this.tokenErrorMessage = ((LazyMutableAnnotation) model).tokenErrorMessage;
	}
	
	public MutableAnnotation addAnnotation(Annotation annotation) {
		this.ensureDataInitialized();
		try {
			this.base.updateViaData = true;
			return this.base.data.addAnnotation(annotation);
		}
		finally {
			this.base.updateViaData = false;
		}
	}
	public MutableAnnotation addAnnotation(String type, int startIndex, int size) {
		this.ensureDataInitialized();
		try {
			this.base.updateViaData = true;
			return this.base.data.addAnnotation(type, startIndex, size);
		}
		finally {
			this.base.updateViaData = false;
		}
	}
	public MutableAnnotation addAnnotation(int startIndex, int endIndex, String type) {
		this.ensureDataInitialized();
		try {
			this.base.updateViaData = true;
			return this.base.data.addAnnotation(startIndex, endIndex, type);
		}
		finally {
			this.base.updateViaData = false;
		}
	}
	
	public MutableAnnotation getMutableAnnotation(String id) {
		this.ensureDataInitialized();
		return this.base.data.getMutableAnnotation(id);
	}
	public MutableAnnotation[] getMutableAnnotations() {
		this.ensureDataInitialized();
		return this.base.data.getMutableAnnotations();
	}
	public MutableAnnotation[] getMutableAnnotations(String type) {
		this.ensureDataInitialized();
		return this.base.data.getMutableAnnotations(type);
	}
	public MutableAnnotation[] getMutableAnnotationsSpanning(int startIndex, int endIndex) {
		this.ensureDataInitialized();
		return this.base.data.getMutableAnnotationsSpanning(startIndex, endIndex);
	}
	public MutableAnnotation[] getMutableAnnotationsSpanning(String type, int startIndex, int endIndex) {
		this.ensureDataInitialized();
		return this.base.data.getMutableAnnotationsSpanning(type, startIndex, endIndex);
	}
	public MutableAnnotation[] getMutableAnnotationsOverlapping(int startIndex, int endIndex) {
		this.ensureDataInitialized();
		return this.base.data.getMutableAnnotationsOverlapping(startIndex, endIndex);
	}
	public MutableAnnotation[] getMutableAnnotationsOverlapping(String type, int startIndex, int endIndex) {
		this.ensureDataInitialized();
		return this.base.data.getMutableAnnotationsOverlapping(type, startIndex, endIndex);
	}
	
	public CharSequence setLeadingWhitespace(CharSequence whitespace) throws IllegalArgumentException {
		throw new UnsupportedOperationException(this.tokenErrorMessage);
	}
	public CharSequence setValueAt(CharSequence value, int index) throws IllegalArgumentException {
		throw new UnsupportedOperationException(this.tokenErrorMessage);
	}
	public CharSequence setWhitespaceAfter(CharSequence whitespace, int index) throws IllegalArgumentException {
		throw new UnsupportedOperationException(this.tokenErrorMessage);
	}
	public TokenSequence removeTokensAt(int index, int size) {
		throw new UnsupportedOperationException(this.tokenErrorMessage);
	}
	public CharSequence insertTokensAt(CharSequence tokens, int index) {
		throw new UnsupportedOperationException(this.tokenErrorMessage);
	}
	public CharSequence addTokens(CharSequence tokens) {
		throw new UnsupportedOperationException(this.tokenErrorMessage);
	}
	public void clear() {
		throw new UnsupportedOperationException(this.tokenErrorMessage);
	}
	public MutableTokenSequence getMutableSubsequence(int start, int size) {
		throw new UnsupportedOperationException(this.tokenErrorMessage);
	}
	public TokenSequence removeTokens(Annotation annotation) {
		throw new UnsupportedOperationException(this.tokenErrorMessage);
	}
	public void addTokenSequenceListener(TokenSequenceListener tsl) {}
	public void removeTokenSequenceListener(TokenSequenceListener tsl) {}
	public void addChar(char ch) {
		throw new UnsupportedOperationException(this.tokenErrorMessage);
	}
	public void addChars(CharSequence chars) {
		throw new UnsupportedOperationException(this.tokenErrorMessage);
	}
	public void insertChar(char ch, int offset) {
		throw new UnsupportedOperationException(this.tokenErrorMessage);
	}
	public void insertChars(CharSequence chars, int offset) {
		throw new UnsupportedOperationException(this.tokenErrorMessage);
	}
	public char removeChar(int offset) {
		throw new UnsupportedOperationException(this.tokenErrorMessage);
	}
	public CharSequence removeChars(int offset, int length) {
		throw new UnsupportedOperationException(this.tokenErrorMessage);
	}
	public char setChar(char ch, int offset) {
		throw new UnsupportedOperationException(this.tokenErrorMessage);
	}
	public CharSequence setChars(CharSequence chars, int offset, int length) {
		throw new UnsupportedOperationException(this.tokenErrorMessage);
	}
	public MutableCharSequence mutableSubSequence(int start, int end) {
		throw new UnsupportedOperationException(this.tokenErrorMessage);
	}
	public void addCharSequenceListener(CharSequenceListener csl) {}
	public void removeCharSequenceListener(CharSequenceListener csl) {}

	/**
	 * Retrieve the error message to use on attempts at modifying tokens or
	 * characters on the enclosed IM document wrapper.
	 * @return the error message
	 */
	public String getTokenErrorMessage() {
		return this.tokenErrorMessage;
	}
	
	/**
	 * Set the error message to use on attempts at modifying tokens or
	 * characters on the enclosed IM document wrapper.
	 * @param tem the error message
	 */
	public void setTokenErrorMessage(String tem) {
		this.tokenErrorMessage = ((tem == null) ? defaultTokenErrorMessage : tem);
	}
}
