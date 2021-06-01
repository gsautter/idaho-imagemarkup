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
package de.uka.ipd.idaho.im.util;

import java.util.LinkedHashSet;

import de.uka.ipd.idaho.gamta.Annotation;
import de.uka.ipd.idaho.gamta.QueriableAnnotation;
import de.uka.ipd.idaho.gamta.Token;
import de.uka.ipd.idaho.gamta.util.DocumentErrorChecker;
import de.uka.ipd.idaho.gamta.util.DocumentErrorProtocol;
import de.uka.ipd.idaho.im.ImAnnotation;
import de.uka.ipd.idaho.im.ImDocument;
import de.uka.ipd.idaho.im.ImRegion;
import de.uka.ipd.idaho.im.ImWord;
import de.uka.ipd.idaho.im.gamta.ImDocumentRoot;

/**
 * Document error checker specifically aimed at Image Markup documents.
 * 
 * @author sautter
 */
public abstract class ImDocumentErrorChecker extends DocumentErrorChecker {
	
	/**
	 * @param name the name of the error checker (must not be null)
	 */
	public ImDocumentErrorChecker(String name) {
		super(name);
	}
	
	/**
	 * @param name the name of the error checker (must not be null)
	 * @param label the label of the error checker, for use in a UI
	 * @param description the description of the error checker, for use in a UI
	 */
	public ImDocumentErrorChecker(String name, String label, String description) {
		super(name, label, description);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.gamta.util.DocumentErrorChecker#addDocumentErrors(de.uka.ipd.idaho.gamta.QueriableAnnotation, de.uka.ipd.idaho.gamta.util.DocumentErrorProtocol, java.lang.String, java.lang.String)
	 */
	public int addDocumentErrors(QueriableAnnotation doc, DocumentErrorProtocol dep, String category, String type) {
		if ((doc instanceof ImDocumentRoot) && (dep instanceof ImDocumentErrorProtocol))
			return this.addDocumentErrors(((ImDocument) doc), ((ImDocumentErrorProtocol) dep), category, type);
		else return 0;
	}
	
	/**
	 * Check a document for errors of a specific category and type and add
	 * any detected errors to a document error protocol. <code>null</code>
	 * values in the category or type arguments should be interpreted as
	 * wildcards. The returned <code>int</code> indicates the number of errors
	 * that were added to the argument error protocol. Implementations should
	 * specify their name as the error source.
	 * @param doc the document to check
	 * @param dep the error protocol to add detected errors to
	 * @param category the category of errors to check for
	 * @param type the type of errors to check for
	 * @return the number of detected errors
	 */
	public abstract int addDocumentErrors(ImDocument doc, ImDocumentErrorProtocol dep, String category, String type);
	
	/**
	 * Check an individual annotation for errors of a specific category and
	 * type and add any detected errors to a document error protocol.
	 * <code>null</code> values in the category or type arguments should be
	 * interpreted as wildcards. This method is intended mostly for localized
	 * re-checks after modifications. The returned <code>int</code> indicates
	 * the number of errors that were added to the argument error protocol.
	 * Implementations should specify their name as the error source.
	 * @param annot the annotation to check
	 * @param dep the error protocol to add detected errors to
	 * @param category the category of errors to check for
	 * @param type the type of errors to check for
	 * @return the number of detected errors
	 */
	public abstract int addAnnotationErrors(ImAnnotation annot, ImDocumentErrorProtocol dep, String category, String type);
	
	/**
	 * Check an individual region for errors of a specific category and type
	 * and add any detected errors to a document error protocol.
	 * <code>null</code> values in the category or type arguments should be
	 * interpreted as wildcards. This method is intended mostly for localized
	 * re-checks after modifications. The returned <code>int</code> indicates
	 * the number of errors that were added to the argument error protocol.
	 * Implementations should specify their name as the error source.
	 * @param region the region to check
	 * @param dep the error protocol to add detected errors to
	 * @param category the category of errors to check for
	 * @param type the type of errors to check for
	 * @return the number of detected errors
	 */
	public abstract int addRegionErrors(ImRegion region, ImDocumentErrorProtocol dep, String category, String type);
	
	/**
	 * Wrapper for non-Image-Markup error checkers to work on Image Markup
	 * objects. It is illegal to wrap an <code>ImDocumentErrorChecker</code> in
	 * an instance of this class.
	 * 
	 * @author sautter
	 */
	public static class WrappedDocumentErrorChecker extends ImDocumentErrorChecker {
		private DocumentErrorChecker errorChecker;
		private int xmlWrapperFlags;
		
		/**
		 * @param errorChecker the document error checker to wrap
		 * @param xmlWrapperFlags the configuration flags for the XML wrapper
		 *        to use for looping through to the wrapped error checker
		 */
		public WrappedDocumentErrorChecker(DocumentErrorChecker errorChecker, int xmlWrapperFlags) {
			super(errorChecker.name, errorChecker.getLabel(), errorChecker.getDescription());
			if (errorChecker instanceof ImDocumentErrorChecker)
				throw new IllegalArgumentException("WrappedDocumentErrorChecker: cannot wrap ImDocumentErrorChecker " + errorChecker.name + " (" + errorChecker.getClass().getName() + ")");
			this.errorChecker = errorChecker;
			this.xmlWrapperFlags = xmlWrapperFlags;
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.gamta.util.DocumentErrorChecker#getErrorCategories()
		 */
		public String[] getErrorCategories() {
			return this.errorChecker.getErrorCategories();
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.gamta.util.DocumentErrorChecker#getErrorCategoryLabel(java.lang.String)
		 */
		public String getErrorCategoryLabel(String category) {
			return this.errorChecker.getErrorCategoryLabel(category);
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.gamta.util.DocumentErrorChecker#getErrorCategoryDescription(java.lang.String)
		 */
		public String getErrorCategoryDescription(String category) {
			return this.errorChecker.getErrorCategoryDescription(category);
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.gamta.util.DocumentErrorChecker#getErrorTypes(java.lang.String)
		 */
		public String[] getErrorTypes(String category) {
			return this.errorChecker.getErrorTypes(category);
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.gamta.util.DocumentErrorChecker#getErrorTypeLabel(java.lang.String, java.lang.String)
		 */
		public String getErrorTypeLabel(String category, String type) {
			return this.errorChecker.getErrorTypeLabel(category, type);
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.gamta.util.DocumentErrorChecker#getErrorTypeDescription(java.lang.String, java.lang.String)
		 */
		public String getErrorTypeDescription(String category, String type) {
			return this.errorChecker.getErrorTypeDescription(category, type);
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.gamta.util.DocumentErrorChecker#getObservedTargets(java.lang.String, java.lang.String)
		 */
		public String[] getObservedTargets(String category, String type) {
			String[] targetTypes = this.errorChecker.getObservedTargets(category, type);
			if (targetTypes.length == 0)
				return targetTypes;
			
			//	add corresponding types and attributes affecting indicated ones in Image Markup model
			LinkedHashSet imTargetTypes = new LinkedHashSet();
			for (int t = 0; t < targetTypes.length; t++) {
				imTargetTypes.add(targetTypes[t]);
				if (("@" + Annotation.START_INDEX_ATTRIBUTE).equals(targetTypes[t]))
					imTargetTypes.add("@" + ImWord.FIRST_WORD_ATTRIBUTE);
				else if (("@" + Annotation.END_INDEX_ATTRIBUTE).equals(targetTypes[t]))
					imTargetTypes.add("@" + ImWord.FIRST_WORD_ATTRIBUTE);
				else if (("@" + Annotation.SIZE_ATTRIBUTE).equals(targetTypes[t])) {
					imTargetTypes.add("@" + ImWord.FIRST_WORD_ATTRIBUTE);
					imTargetTypes.add("@" + ImWord.LAST_WORD_ATTRIBUTE);
				}
				else if (Token.TOKEN_ANNOTATION_TYPE.equals(targetTypes[t])) {
					imTargetTypes.add(ImWord.WORD_ANNOTATION_TYPE);
					imTargetTypes.add("@" + ImWord.STRING_ATTRIBUTE);
					imTargetTypes.add("@" + ImWord.NEXT_RELATION_ATTRIBUTE);
					imTargetTypes.add("@" + ImWord.PREVIOUS_RELATION_ATTRIBUTE);
					imTargetTypes.add("@" + ImWord.NEXT_WORD_ATTRIBUTE);
					imTargetTypes.add("@" + ImWord.PREVIOUS_WORD_ATTRIBUTE);
					imTargetTypes.add("@" + ImWord.TEXT_STREAM_TYPE_ATTRIBUTE);
					imTargetTypes.add("@" + ImWord.FONT_SIZE_ATTRIBUTE);
				}
			}
			return ((String[]) imTargetTypes.toArray(new String[imTargetTypes.size()]));
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.gamta.util.DocumentErrorChecker#getCheckedSubjects(java.lang.String, java.lang.String)
		 */
		public String[] getCheckedSubjects(String category, String type) {
			String[] subjectTypes = this.errorChecker.getCheckedSubjects(category, type);
			if (subjectTypes.length == 0)
				return subjectTypes;
			
			//	add corresponding types in Image Markup model
			LinkedHashSet imSubjectTypes = new LinkedHashSet();
			for (int t = 0; t < subjectTypes.length; t++) {
				imSubjectTypes.add(subjectTypes[t]);
				if (Token.TOKEN_ANNOTATION_TYPE.equals(subjectTypes[t]))
					imSubjectTypes.add(ImWord.WORD_ANNOTATION_TYPE);
			}
			return ((String[]) imSubjectTypes.toArray(new String[imSubjectTypes.size()]));
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.gamta.util.DocumentErrorChecker#requiresTopLevelDocument(java.lang.String, java.lang.String)
		 */
		public boolean requiresTopLevelDocument(String category, String type) {
			return this.errorChecker.requiresTopLevelDocument(category, type);
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.gamta.util.DocumentErrorChecker#getCheckLevel(java.lang.String, java.lang.String)
		 */
		public String getCheckLevel(String category, String type) {
			return this.errorChecker.getCheckLevel(category, type);
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.gamta.util.DocumentErrorChecker#isDefaultErrorChecker()
		 */
		public boolean isDefaultErrorChecker() {
			return this.errorChecker.isDefaultErrorChecker();
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.gamta.util.DocumentErrorChecker#getErrorMetadata()
		 */
		public DocumentErrorMetadata[] getErrorMetadata() {
			return this.errorChecker.getErrorMetadata();
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.gamta.util.DocumentErrorChecker#getErrorMetadata(java.lang.String)
		 */
		public DocumentErrorMetadata[] getErrorMetadata(String category) {
			return this.errorChecker.getErrorMetadata(category);
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.gamta.util.DocumentErrorChecker#getErrorMetadata(java.lang.String, java.lang.String)
		 */
		public DocumentErrorMetadata[] getErrorMetadata(String category, String type) {
			return this.errorChecker.getErrorMetadata(category, type);
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.im.util.ImDocumentErrorChecker#addDocumentErrors(de.uka.ipd.idaho.gamta.QueriableAnnotation, de.uka.ipd.idaho.gamta.util.DocumentErrorProtocol, java.lang.String, java.lang.String)
		 */
		public int addDocumentErrors(QueriableAnnotation doc, DocumentErrorProtocol dep, String category, String type) {
			return this.errorChecker.addDocumentErrors(doc, dep, category, type); // no use unwrapping and re-wrapping document ...
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.im.util.ImDocumentErrorChecker#addDocumentErrors(de.uka.ipd.idaho.im.ImDocument, de.uka.ipd.idaho.im.util.ImDocumentErrorProtocol, java.lang.String, java.lang.String)
		 */
		public int addDocumentErrors(ImDocument doc, ImDocumentErrorProtocol dep, String category, String type) {
			ImDocumentRoot xmlDoc = new ImDocumentRoot(doc, this.xmlWrapperFlags);
			return this.errorChecker.addDocumentErrors(xmlDoc, dep, category, type);
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.im.util.ImDocumentErrorChecker#addAnnotationErrors(de.uka.ipd.idaho.im.ImAnnotation, de.uka.ipd.idaho.im.util.ImDocumentErrorProtocol, java.lang.String, java.lang.String)
		 */
		public int addAnnotationErrors(ImAnnotation annot, ImDocumentErrorProtocol dep, String category, String type) {
			ImDocumentRoot xmlDoc = new ImDocumentRoot(annot, this.xmlWrapperFlags);
			return this.errorChecker.addDocumentErrors(xmlDoc, dep, category, type);
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.im.util.ImDocumentErrorChecker#addRegionErrors(de.uka.ipd.idaho.im.ImRegion, de.uka.ipd.idaho.im.util.ImDocumentErrorProtocol, java.lang.String, java.lang.String)
		 */
		public int addRegionErrors(ImRegion region, ImDocumentErrorProtocol dep, String category, String type) {
			ImDocumentRoot xmlDoc = new ImDocumentRoot(region, this.xmlWrapperFlags);
			return this.errorChecker.addDocumentErrors(xmlDoc, dep, category, type);
		}
	}
}
