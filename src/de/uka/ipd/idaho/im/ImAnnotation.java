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
	
	/** the name to use to retrieve the last word in the annotation as a virtual
	 * attribute via the generic <code>getAttribute()</code> methods */
	public static final String LAST_WORD_ATTRIBUTE = "lastWord";
	
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
	
	/**
	 * Carrier class of helper methods for local UID and UUID computation.
	 * 
	 * @author sautter
	 */
	public static class AnnotationUuidHelper extends UuidHelper {
		
		/**
		 * Compute the locally unique ID of an annotation.
		 * @param annot the annotation to compute the UID for
		 * @return the UID of the argument annotation
		 */
		public static String getLocalUID(ImAnnotation annot) {
			ImWord fImw = annot.getFirstWord();
			ImWord lImw = annot.getLastWord();
			return getLocalUID(annot.getType(), fImw.pageId, lImw.pageId, fImw.bounds.left, fImw.bounds.top, lImw.bounds.right, lImw.bounds.bottom);
			/* TODO make selection of points dependent on document orientation
			 * - top-left + bottom-right in left-right + top-down orientation (also top-down + left-right, like in Chinese)
			 * - top-right + bottom-left in right-left + top-down orientation (like in Hebrew and Arabic)
			 * - and so forth ...
			 */
		}
	}
}