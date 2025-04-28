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

import de.uka.ipd.idaho.gamta.QueriableAnnotation;
import de.uka.ipd.idaho.im.ImAnnotation;
import de.uka.ipd.idaho.im.ImDocument;
import de.uka.ipd.idaho.im.ImRegion;

/**
 * Lazy initialization adapter for Image Markup document GAMTA wrapper.
 * 
 * @author sautter
 */
public class LazyQueriableAnnotation extends LazyAnnotation implements QueriableAnnotation {
	
	/**
	 * Constructor for lazily wrapping whole IM documents
	 * @param doc the IM document to wrap
	 * @param flags the wrapper flags to use
	 */
	public LazyQueriableAnnotation(ImDocument doc, int flags) {
		super(doc, flags);
	}
	
	/**
	 * Constructor for lazily wrapping some annotation on IM documents
	 * @param annot the IM document annotation to wrap
	 * @param flags the wrapper flags to use
	 */
	public LazyQueriableAnnotation(ImAnnotation annot, int flags) {
		super(annot, flags);
	}
	
	/**
	 * Constructor for lazily wrapping some region on IM documents
	 * @param region the IM document region to wrap
	 * @param flags the wrapper flags to use
	 */
	public LazyQueriableAnnotation(ImRegion region, int flags) {
		super(region, flags);
	}
	
	/**
	 * Constructor for migrating between different levels of implementation,
	 * e.g. to create an editable clone of a queriable annotation (which the
	 * enclosed GAMTA wrapper readily supports).
	 * @param model the lazy annotation whose data to reuse
	 */
	public LazyQueriableAnnotation(LazyAnnotation model) {
		super(model);
	}
	
	public int getAbsoluteStartIndex() {
		return this.getStartIndex(); // token indexes in IM document are absolute
	}
	public int getAbsoluteStartOffset() {
		return this.getStartOffset(); // token offsets in IM document are absolute
	}
	
	public QueriableAnnotation getAnnotation(String id) {
		this.ensureDataInitialized();
		return this.base.data.getAnnotation(id);
	}
	public QueriableAnnotation[] getAnnotations() {
		this.ensureDataInitialized();
		return this.base.data.getAnnotations();
	}
	public QueriableAnnotation[] getAnnotations(String type) {
		this.ensureDataInitialized();
		return this.base.data.getAnnotations(type);
	}
	public QueriableAnnotation[] getAnnotationsSpanning(int startIndex, int endIndex) {
		this.ensureDataInitialized();
		return this.base.data.getAnnotationsSpanning(startIndex, endIndex);
	}
	public QueriableAnnotation[] getAnnotationsSpanning(String type, int startIndex, int endIndex) {
		this.ensureDataInitialized();
		return this.base.data.getAnnotationsSpanning(type, startIndex, endIndex);
	}
	public QueriableAnnotation[] getAnnotationsOverlapping(int startIndex, int endIndex) {
		this.ensureDataInitialized();
		return this.base.data.getAnnotationsOverlapping(startIndex, endIndex);
	}
	public QueriableAnnotation[] getAnnotationsOverlapping(String type, int startIndex, int endIndex) {
		this.ensureDataInitialized();
		return this.base.data.getAnnotationsOverlapping(type, startIndex, endIndex);
	}
	public String[] getAnnotationTypes() {
		return this.base.getAnnotationTypes();
	}
	
	public String getAnnotationNestingOrder() {
		return null; // wrapper proper returns null just the same, so no use initializing TODO keep this in sync with actual GAMTA wrapper
	}
}
