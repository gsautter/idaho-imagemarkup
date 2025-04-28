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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeMap;

import de.uka.ipd.idaho.gamta.Annotation;
import de.uka.ipd.idaho.gamta.Attributed;
import de.uka.ipd.idaho.gamta.QueriableAnnotation;
import de.uka.ipd.idaho.gamta.Token;
import de.uka.ipd.idaho.gamta.util.CountingSet;
import de.uka.ipd.idaho.gamta.util.DocumentErrorProtocol;
import de.uka.ipd.idaho.gamta.util.imaging.BoundingBox;
import de.uka.ipd.idaho.gamta.util.imaging.ImagingConstants;
import de.uka.ipd.idaho.im.ImAnnotation;
import de.uka.ipd.idaho.im.ImDocument;
import de.uka.ipd.idaho.im.ImFont;
import de.uka.ipd.idaho.im.ImLayoutObject;
import de.uka.ipd.idaho.im.ImObject;
import de.uka.ipd.idaho.im.ImPage;
import de.uka.ipd.idaho.im.ImRegion;
import de.uka.ipd.idaho.im.ImSupplement;
import de.uka.ipd.idaho.im.ImWord;
import de.uka.ipd.idaho.im.gamta.ImDocumentRoot;
import de.uka.ipd.idaho.im.gamta.LazyAnnotation;

/**
 * Error protocol specific for Image Markup documents. The IO facilities
 * provided by this class store instances as tab separated values.
 * 
 * @author sautter
 */
public class ImDocumentErrorProtocol extends DocumentErrorProtocol implements ImagingConstants {
	
	/** the name (and ID) to use for storing an error protocol as a supplement to an Image Markup document, namely 'errorProtocol.txt' */
	public static final String errorProtocolSupplementName = "errorProtocol.txt";
	
	/** the name of the attribute to put on an error protocol supplement to indicate the last error check precedes the last edit, namely 'errorProtocolStale' */
	public static final String staleErrorProtocolMarker = "errorProtocolStale";
	
	/** the Image Markup document the error protocol pertains to */
	public final ImDocument subject;
	
	private ArrayList errors = new ArrayList();
	private HashMap errorsByIDs = new HashMap();
	private HashMap falsePositivesByIDs = new HashMap();
	private HashMap errorsByCategory = new HashMap();
	private HashMap errorsByCategoryAndType = new HashMap();
	private HashMap errorsBySubjectType = new HashMap();
	private HashMap errorsByTypeInternalSubjectId = new HashMap();
	private HashMap errorsBySourceId = new HashMap();
	private CountingSet errorSeverityCounts = new CountingSet(new TreeMap());
	
	/** Constructor
	 * @param subject the document the error protocol pertains to
	 */
	public ImDocumentErrorProtocol(ImDocument subject) {
		this.subject = subject;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.gamta.util.errorLogging.DocumentErrorProtocol#findErrorSubject(de.uka.ipd.idaho.gamta.Attributed, java.lang.String[])
	 */
	public Attributed findErrorSubject(Attributed doc, String[] data) {
		if (this.subject == null)
			return null;
		String subjectClass = data[0];
		String subjectType = data[1];
		String typeInternalSubjectId = data[2];
		int subjectPageId = Integer.parseInt(data[3]);
//		int subjectLastPageId = Integer.parseInt(data[4]);
		ImWord subjectFirstWord = ((data.length < 5) ? null : this.subject.getWord(data[5]));
//		ImWord subjectLastWord = ((data.length < 6) ? null : this.subject.getWord(data[6]));
		return getErrorSubject(this.subject, subjectClass, subjectType, typeInternalSubjectId, subjectPageId, subjectFirstWord);
	}
	
	public void addError(String source, Attributed subject, Attributed parent, String category, String type, String description, String severity, boolean falsePositive) {
		
		//	find underlying IM object
		ImObject imSubject = getErrorSubject(subject, parent, type);
		
		//	create and index error
//		this.addError(new ImDocumentError(source, ((imSubject == null) ? subject : imSubject), this.subject, category, type, description, severity));
		ImDocumentError imDe = new ImDocumentError(source, ((imSubject == null) ? subject : imSubject), this.subject, category, type, description, severity);
		if (falsePositive)
			this.markFalsePositive(imDe);
		else this.addError(imDe);
	}
	
	/**
	 * Add an error to the protocol. If the error is a duplicate, this method
	 * returns false. The same applies if the argument error was previously
	 * marked as a false positive.
	 * @param error the error to add
	 * @return true if the error was actually added
	 */
	public boolean addError(ImDocumentError error) {
		if (this.errorsByIDs.containsKey(error.id))
			return false;
		if (this.falsePositivesByIDs.containsKey(error.id))
			return false;
		this.errors.add(error);
		this.errorsByIDs.put(error.id, error);
		getIndexList(this.errorsByCategory, error.category, true).add(error);
		getIndexList(this.errorsByCategoryAndType, (error.category + "." + error.type), true).add(error);
		getIndexList(this.errorsBySubjectType, error.subjectType, true).add(error);
		getIndexList(this.errorsByTypeInternalSubjectId, error.typeInternalSubjectId, true).add(error);
		if (error.source != null)
			getIndexList(this.errorsBySourceId, error.source, true).add(error);
		this.errorSeverityCounts.add(error.severity);
		this.errorSeverityCounts.add(error.category + "." + error.severity);
		this.errorSeverityCounts.add(error.category + "." + error.type + "." + error.severity);
		return true;
	}
	
	/**
	 * Retrieve a list from a map using a given key. If the argument key is not
	 * present in the argument map and the create flag is set to true, the list
	 * will be created, put in the argument map for the argument key, and then
	 * returned. This method is intended for use by sub classes that want to
	 * maintain map based indexes beyond what this class proper does.
	 * @param index the index map to retrieve the index list from
	 * @param key the key to retrieve the index list for
	 * @param create create the requested list if absent?
	 * @return the requested list
	 */
	protected static ArrayList getIndexList(HashMap index, String key, boolean create) {
		ArrayList list = ((ArrayList) index.get(key));
		if ((list == null) && create) {
			list = new ArrayList(2);
			index.put(key, list);
		}
		return list;
	}
	
	/**
	 * Remove all errors of a specific category and type. Null values of either
	 * argument are treated as wildcards: If the argument category is null, all
	 * errors will be removed; if the argument type is null, all errors of the
	 * argument category will be removed.
	 * @param category the category of errors to remove
	 * @param type the type of errors to remove
	 * @return true if any errors were actually removed
	 */
	public boolean removeErrors(String category, String type) {
		
		//	clear whole protocol on category wildcard
		if (category == null) {
			boolean removed = (this.errors.size() != 0);
			this.errors.clear();
			this.errorsByIDs.clear();
			this.errorsByCategory.clear();
			this.errorsByCategoryAndType.clear();
			this.errorsBySubjectType.clear();
			this.errorsByTypeInternalSubjectId.clear();
			this.errorsBySourceId.clear();
			this.errorSeverityCounts.clear();
			return removed;
		}
		
		//	get errors to remove
		ArrayList ctErrors = null;
		
		//	type wildcard, clean up category right away
		if (type == null) {
			ctErrors = getIndexList(this.errorsByCategory, category, false);
			this.errorsByCategory.remove(category);
			for (Iterator ctit = this.errorsByCategoryAndType.keySet().iterator(); ctit.hasNext();) {
				String ct = ((String) ctit.next());
				if (ct.startsWith(category + "."))
					ctit.remove();
			}
		}
		
		//	specific error type, clean up that index at least
		else {
			ctErrors = getIndexList(this.errorsByCategoryAndType, (category + "." + type), false);
			this.errorsByCategoryAndType.remove(category + "." + type);
		}
		
		//	anything to clean up?
		if (ctErrors == null)
			return false;
		
		//	clean up remaining indexes
		boolean removed = false;
		for (int e = 0; e < ctErrors.size(); e++) {
			ImDocumentError ide = ((ImDocumentError) ctErrors.get(e));
			if (this.removeError(ide))
				removed = true;
		}
		
		//	finally ...
		return removed;
	}
	
	/**
	 * Remove the errors pertaining to a given object.
	 * @param subject the object to remove the errors for
	 * @return true if any error was actually removed
	 */
	public boolean removeErrorsBySubject(ImObject subject) {
		return this.removeErrorsBySubject(subject, subject.getType());
	}
	
	/**
	 * Remove the errors pertaining to a given object, assuming a specific
	 * type. The latter is useful after an object type change.
	 * @param subject the object to remove the errors for
	 * @param type the object type to remove the errors for
	 * @return true if any error was actually removed
	 */
	public boolean removeErrorsBySubject(ImObject subject, String type) {
		if (this.subject == null)
			return false;
		String tisId = getTypeInternalErrorSubjectId(subject, this.subject);
		ArrayList tisErrors = getIndexList(this.errorsByTypeInternalSubjectId, tisId, false);
		if (tisErrors == null)
			return false;
		tisErrors = new ArrayList(tisErrors); // make local copy of index list to prevent any errors from slipping past scrutiny due to downstream index removals
		boolean removed = false;
		for (int e = 0; e < tisErrors.size(); e++) {
			ImDocumentError ide = ((ImDocumentError) tisErrors.get(e));
			if ((type == null) || ide.subjectType.equals(type)) {
				if (this.removeError(ide))
					removed = true;
			}
		}
		return removed;
	}
	
	/**
	 * Remove the errors created by a given source.
	 * @param sourceId the ID of the error source
	 * @return true if any error was actually removed
	 */
	public boolean removeErrorsBySource(String sourceId) {
		ArrayList srcErrors = getIndexList(this.errorsBySourceId, sourceId, false);
		if (srcErrors == null)
			return false;
		srcErrors = new ArrayList(srcErrors); // make local copy of index list to prevent any errors from slipping past scrutiny due to downstream index removals
		boolean removed = false;
		for (int e = 0; e < srcErrors.size(); e++) {
			ImDocumentError ide = ((ImDocumentError) srcErrors.get(e));
			if (this.removeError(ide))
				removed = true;
		}
		return removed;
	}
	
	public void removeError(DocumentError error) {
		this.removeError((ImDocumentError) error);
	}
	
	/**
	 * Remove an error from the protocol.
	 * @param error the error to remove
	 * @return true if the error was actually removed
	 */
	public boolean removeError(ImDocumentError error) {
		if (!this.errorsByIDs.containsKey(error.id))
			return false;
		this.errors.remove(error);
		this.errorsByIDs.remove(error.id);
		removeFromIndexList(this.errorsByCategory, error.category, error);
		removeFromIndexList(this.errorsByCategoryAndType, (error.category + "." + error.type), error);
		removeFromIndexList(this.errorsBySubjectType, error.subjectType, error);
		removeFromIndexList(this.errorsByTypeInternalSubjectId, error.typeInternalSubjectId, error);
		if (error.source != null)
			removeFromIndexList(this.errorsBySourceId, error.source, error);
		this.errorSeverityCounts.remove(error.severity);
		this.errorSeverityCounts.remove(error.category + "." + error.severity);
		this.errorSeverityCounts.remove(error.category + "." + error.type + "." + error.severity);
		return true;
	}
	
	/**
	 * Remove an error from an index list identified by a given key in an index
	 * map. If the list exists and ends up empty after removal of the argument
	 * error, the list is removed from the index map. This method is intended
	 * for use by sub classes that want to maintain map based indexes beyond
	 * what this class proper does.
	 * @param index the map holding the index list to remove the error from
	 * @param key the key to the index list to remove the error from
	 * @param error the error to remove
	 * @return true if the index list ran empty and the key was removed
	 */
	protected static boolean removeFromIndexList(HashMap index, String key, DocumentError error) {
		ArrayList list = ((ArrayList) index.get(key));
		if (list == null)
			return false;
		list.remove(error);
		if (list.isEmpty()) {
			index.remove(key);
			return true;
		}
		else return false;
	}
	
	public boolean isFalsePositive(DocumentError error) {
		return this.isFalsePositive((ImDocumentError) error);
	}
	
	/**
	 * Check whether or not error is marked as a false positive.
	 * @param error the error to check for false positive
	 */
	public boolean isFalsePositive(ImDocumentError error) {
		return this.falsePositivesByIDs.containsKey(error.id);
	}
	
	public boolean markFalsePositive(DocumentError error) {
		return this.markFalsePositive((ImDocumentError) error);
	}
	
	/**
	 * Mark an error as a false positive. Any downstream attempt at adding the
	 * same error to the protocol will be ignored.
	 * @param error the error to mark as a false positive
	 */
	public boolean markFalsePositive(ImDocumentError error) {
		return (this.falsePositivesByIDs.put(error.id, error) == null);
	}
	
	public boolean unmarkFalsePositive(DocumentError error) {
		return this.unmarkFalsePositive((ImDocumentError) error);
	}
	
	/**
	 * Un-mark an error as a false positive to facilitate re-adding it to the
	 * protocol.
	 * @param error the error to un-mark as a false positive
	 */
	public boolean unmarkFalsePositive(ImDocumentError error) {
		return (this.falsePositivesByIDs.remove(error.id) != null);
	}
	
	/**
	 * Retrieve the false positive with a given ID.
	 * @param errorId the ID of the false positive
	 * @return the false positive with the argument ID
	 */
	public ImDocumentError getFalsePositiveById(String falPosId) {
		return ((ImDocumentError) this.falsePositivesByIDs.get(falPosId));
	}
	
	public DocumentError[] getFalsePositives() {
		ArrayList fps = this.getFalsePositiveList();
		return ((DocumentError[]) fps.toArray(new DocumentError[fps.size()]));
	}
	ArrayList getFalsePositiveList() {
		return new ArrayList(this.falsePositivesByIDs.values());
	}
	
	public int getErrorCount() {
		return this.errors.size();
	}
	
	public int getErrorSeverityCount(String severity) {
		return this.errorSeverityCounts.getCount(severity);
	}
	
	public DocumentError[] getErrors() {
		return ((DocumentError[]) this.errors.toArray(new DocumentError[this.errors.size()]));
	}
	
	public int getErrorCount(String category) {
		ArrayList categoryErrors = getIndexList(this.errorsByCategory, category, false);
		return ((categoryErrors == null) ? 0 : categoryErrors.size());
	}
	
	public int getErrorSeverityCount(String category, String severity) {
		return this.errorSeverityCounts.getCount(category + "." + severity);
	}
	
	public DocumentError[] getErrors(String category) {
		if (category == null)
			return this.getErrors();
		ArrayList categoryErrors = getIndexList(this.errorsByCategory, category, false);
		return ((categoryErrors == null) ? new DocumentError[0] : ((DocumentError[]) categoryErrors.toArray(new DocumentError[categoryErrors.size()])));
	}
	
	public int getErrorCount(String category, String type) {
		ArrayList categoryAndTypeErrors = getIndexList(this.errorsByCategoryAndType, (category + "." + type), false);
		return ((categoryAndTypeErrors == null) ? 0 : categoryAndTypeErrors.size());
	}
	
	public int getErrorSeverityCount(String category, String type, String severity) {
		return this.errorSeverityCounts.getCount(category + "." + type + "." + severity);
	}
	
	public DocumentError[] getErrors(String category, String type) {
		if (category == null)
			return this.getErrors();
		if (type == null)
			return this.getErrors(category);
		ArrayList categoryAndTypeErrors = getIndexList(this.errorsByCategoryAndType, (category + "." + type), false);
		return ((categoryAndTypeErrors == null) ? new DocumentError[0] : ((DocumentError[]) categoryAndTypeErrors.toArray(new DocumentError[categoryAndTypeErrors.size()])));
	}
	
	/**
	 * Retrieve the errors pertaining to a given object.
	 * @param subject the object to obtain the errors for
	 * @return an array holding the errors
	 */
	public ImDocumentError[] getErrorsForSubject(ImObject subject) {
		return this.getErrorsForSubject(subject, subject.getType());
	}
	
	/**
	 * Retrieve the errors pertaining to a given object, assuming a specific
	 * type. The latter is useful after an object type change.
	 * @param subject the object to obtain the errors for
	 * @param type the type to assume
	 * @return an array holding the errors
	 */
	public ImDocumentError[] getErrorsForSubject(ImObject subject, String type) {
		if (this.subject == null)
			return new ImDocumentError[0];
		String tisId = getTypeInternalErrorSubjectId(subject, this.subject);
		ArrayList tisErrors = getIndexList(this.errorsByTypeInternalSubjectId, tisId, false);
		if (tisErrors == null)
			return new ImDocumentError[0];
		ArrayList sErrors = new ArrayList();
		for (int e = 0; e < tisErrors.size(); e++) {
			ImDocumentError ide = ((ImDocumentError) tisErrors.get(e));
			if ((type == null) || ide.subjectType.equals(type))
				sErrors.add(ide);
		}
		if (sErrors.isEmpty())
			return new ImDocumentError[0];
		return ((ImDocumentError[]) sErrors.toArray(new ImDocumentError[sErrors.size()]));
	}
	
	/**
	 * Retrieve the errors created by a given source.
	 * @param sourceId the ID of the error source
	 * @return an array holding the errors
	 */
	public DocumentError[] getErrorsFromSource(String sourceId) {
		ArrayList srcErrors = getIndexList(this.errorsBySourceId, sourceId, false);
		if (srcErrors == null)
			return new ImDocumentError[0];
		return ((ImDocumentError[]) srcErrors.toArray(new ImDocumentError[srcErrors.size()]));
	}
	
	/**
	 * Retrieve the error with a given ID.
	 * @param errorId the ID of the error
	 * @return the error with the argument ID
	 */
	public ImDocumentError getErrorById(String errorId) {
		return ((ImDocumentError) this.errorsByIDs.get(errorId));
	}
	
	public Comparator getErrorComparator() {
		return imDocumentErrorOrder;
	}
	private static final Comparator imDocumentErrorOrder = new Comparator() {
		public int compare(Object obj1, Object obj2) {
			ImDocumentError ide1 = ((ImDocumentError) obj1);
			ImDocumentError ide2 = ((ImDocumentError) obj2);
			if (ide1.subjectPageId != ide2.subjectPageId)
				return (ide1.subjectPageId - ide2.subjectPageId);
			if (ide1.subjectLastPageId != ide2.subjectLastPageId)
				return (ide1.subjectLastPageId - ide2.subjectLastPageId);
			if ((ide1.subjectFirstWord != null) && (ide2.subjectFirstWord != null)) {
				int c = ImUtils.textStreamOrder.compare(ide1.subjectFirstWord, ide2.subjectFirstWord);
				if ((c == 0) && (ide1.subjectLastWord != null) && (ide2.subjectLastWord != null))
					c = ImUtils.textStreamOrder.compare(ide2.subjectLastWord, ide1.subjectLastWord);
				return c;
			}
			if (ide1.subjectFirstWord != null)
				return 1; // put general errors ahead of specific ones
			if (ide2.subjectFirstWord != null)
				return -1; // keep general errors ahead of specific ones
			return ide1.subjectType.compareTo(ide2.subjectType);
		}
	};
	
	/**
	 * Load an error protocol from the default supplement of a document. If the
	 * default 'errorProtocol.txt' supplement is absent, this method returns
	 * null.
	 * @param doc the document to read the error protocol from
	 * @return the error protocol, or null, if the supplement is absent
	 * @throws IOException
	 */
	public static ImDocumentErrorProtocol loadErrorProtocol(ImDocument doc) throws IOException {
		ImSupplement idepSupp = doc.getSupplement(errorProtocolSupplementName);
		if (idepSupp == null)
			return null;
		return loadErrorProtocol(doc, idepSupp.getInputStream());
	}
	
	/**
	 * Load an error protocol from the data provided by a given input stream.
	 * If the argument document is null, the errors in the protocol will only
	 * have error metadata, but the error subjects will be null, and the first
	 * and last word will be dummies. This method reads the argument stream to
	 * its end and closes it afterwards.
	 * @param doc the document the error protocol pertains to
	 * @param in the input stream to populate the error protocol from
	 * @return the error protocol
	 * @throws IOException
	 */
	public static ImDocumentErrorProtocol loadErrorProtocol(ImDocument doc, InputStream in) throws IOException {
		ImDocumentErrorProtocol idep = new ImDocumentErrorProtocol(doc);
		fillErrorProtocol(idep, in);
		return idep;
	}
	
	/**
	 * Load an error protocol from the data provided by a given input stream.
	 * If the argument document is null, the errors in the protocol will only
	 * have error metadata, but the error subjects will be null, and the first
	 * and last word will be dummies. This method reads the argument stream to
	 * its end and closes it afterwards.
	 * @param doc the document the error protocol pertains to
	 * @param in the input stream to populate the error protocol from
	 * @return the error protocol
	 * @throws IOException
	 */
	public static ImDocumentErrorProtocol loadErrorProtocol(ImDocument doc, Reader in) throws IOException {
		ImDocumentErrorProtocol idep = new ImDocumentErrorProtocol(doc);
		fillErrorProtocol(idep, in);
		return idep;
	}
	
	/**
	 * Fill an error protocol with the data provided by a given input stream.
	 * If the argument document is null, the errors in the protocol will only
	 * have error metadata, but the error subjects will be null, and the first
	 * and last word will be dummies. This method reads the argument stream to
	 * its end and closes it afterwards.
	 * @param doc the document the error protocol pertains to
	 * @param idep the error protocol to populate
	 * @param in the input stream to populate the error protocol from
	 * @throws IOException
	 */
	public static void fillErrorProtocol(ImDocumentErrorProtocol idep, InputStream in) throws IOException {
		fillErrorProtocol(idep, new InputStreamReader(in, "UTF-8"));
	}
	
	/**
	 * Fill an error protocol with the data provided by a given input stream.
	 * If the argument document is null, the errors in the protocol will only
	 * have error metadata, but the error subjects will be null, and the first
	 * and last word will be dummies. This method reads the argument stream to
	 * its end and closes it afterwards.
	 * @param doc the document the error protocol pertains to
	 * @param idep the error protocol to populate
	 * @param in the input stream to populate the error protocol from
	 * @throws IOException
	 */
	public static void fillErrorProtocol(ImDocumentErrorProtocol idep, Reader in) throws IOException {
		
		//	load error protocol, scoping error categories and types
		BufferedReader epBr = ((in instanceof BufferedReader) ? ((BufferedReader) in) : new BufferedReader(in));
		String category = "null";
		String type = "null";
		for (String line; (line = epBr.readLine()) != null;) {
			line = line.trim();
			if (line.length() == 0)
				continue;
			
			//	parse data
			String[] data = line.split("\\t");
			if (data.length < 2)
				continue;
			
			//	read error category
			if ("CATEGORY".equals(data[0])) {
				category = data[1];
				String label = getElement(data, 2, category);
				String description = getElement(data, 3, category);
				idep.addErrorCategory(category, label, description);
				continue;
			}
			
			//	read error type
			if ("TYPE".equals(data[0])) {
				type = data[1];
				String label = getElement(data, 2, type);
				String description = getElement(data, 3, type);
				idep.addErrorType(category, type, label, description);
				continue;
			}
			
			//	do we have an actual error or false positive?
			if (!"ERROR".equals(data[0]) && !"FALPOS".equals(data[0]))
				continue; // something weird, ignore it
			if (data.length < (isSeverity(data[1]) ? 9 : 8))
				continue; // not the data we expected ...
			
			//	read error (handle absence of severity for now, we do have a few existing protocols without it out there)
			int i = 1;
			String severity = (isSeverity(data[i]) ? data[i++] : DocumentError.SEVERITY_CRITICAL);
			String description = data[i++];
			String source = data[i++];
			String subjectClass = data[i++];
			String subjectType = data[i++];
			String typeInternalSubjectId = data[i++];
			int subjectPageId = Integer.parseInt(data[i++]);
			int subjectLastPageId = Integer.parseInt(data[i++]);
			ImWord subjectFirstWord;
			ImWord subjectLastWord;
			ImObject subject;
			if (idep.subject == null) {
				subjectFirstWord = ((data.length < 10) ? null : buildDummyWord(data[i++]));
				subjectLastWord = ((data.length < 11) ? null : buildDummyWord(data[i++]));
				subject = null;
			}
			else {
				subjectFirstWord = ((data.length < 10) ? null : idep.subject.getWord(data[i++]));
				subjectLastWord = ((data.length < 11) ? null : idep.subject.getWord(data[i++]));
				subject = getErrorSubject(idep.subject, subjectClass, subjectType, typeInternalSubjectId, subjectPageId, subjectFirstWord);
			}
			ImDocumentError ide = new ImDocumentError(source, subject, category, type, description, severity, subjectClass, subjectType, typeInternalSubjectId, subjectPageId, subjectFirstWord, subjectLastPageId, subjectLastWord);
			if ("ERROR".equals(data[0]))
				idep.addError(ide);
			else if ("FALPOS".equals(data[0]))
				idep.markFalsePositive(ide);
		}
		epBr.close();
	}
	private static String getElement(String[] data, int index, String def) {
		return ((index < data.length) ? data[index] : def);
	}
	private static boolean isSeverity(String data) {
		if (DocumentError.SEVERITY_BLOCKER.equals(data))
			return true;
		if (DocumentError.SEVERITY_CRITICAL.equals(data))
			return true;
		if (DocumentError.SEVERITY_MAJOR.equals(data))
			return true;
		if (DocumentError.SEVERITY_MINOR.equals(data))
			return true;
		return false;
	}
	private static ImWord buildDummyWord(String wordLocalId) {
		try {
			String pidStr = wordLocalId.substring(0, wordLocalId.indexOf("."));
			String bbStr = wordLocalId.substring(wordLocalId.indexOf(".") + ".".length());
			return new ImWord(null, Integer.parseInt(pidStr), BoundingBox.parse(bbStr), "dummy");
		}
		catch (RuntimeException re) {
			return null;
		}
	}
	
	/**
	 * Store an error protocol to a given output stream. This method does not
	 * close the argument stream.
	 * @param idep the error protocol to store
	 * @param out the output stream to store the error protocol to
	 * @throws IOException
	 */
	public static void storeErrorProtocol(ImDocumentErrorProtocol idep, OutputStream out) throws IOException {
		storeErrorProtocol(idep, new OutputStreamWriter(out, "UTF-8"));
	}
	
	/**
	 * Store an error protocol to a given output stream. This method does not
	 * close the argument stream.
	 * @param idep the error protocol to store
	 * @param out the output stream to store the error protocol to
	 * @throws IOException
	 */
	public static void storeErrorProtocol(ImDocumentErrorProtocol idep, Writer out) throws IOException {
		
		//	persist error protocol
		BufferedWriter epBw = ((out instanceof BufferedWriter) ? ((BufferedWriter) out) : new BufferedWriter(out));
		ArrayList falsePositives = null;
		String[] categories = idep.getErrorCategories();
		for (int c = 0; c < categories.length; c++) {
			
			//	store category proper
			epBw.write("CATEGORY");
			epBw.write("\t" + categories[c]);
			epBw.write("\t" + idep.getErrorCategoryLabel(categories[c]));
			epBw.write("\t" + idep.getErrorCategoryDescription(categories[c]));
			epBw.newLine();
			
			//	store error types in current category
			String[] types = idep.getErrorTypes(categories[c]);
			for (int t = 0; t < types.length; t++) {
				
				//	store type proper
				epBw.write("TYPE");
				epBw.write("\t" + types[t]);
				epBw.write("\t" + idep.getErrorTypeLabel(categories[c], types[t]));
				epBw.write("\t" + idep.getErrorTypeDescription(categories[c], types[t]));
				epBw.newLine();
				
				//	store actual errors
				DocumentError[] errors = idep.getErrors(categories[c], types[t]);
				for (int e = 0; e < errors.length; e++)
					storeError("ERROR", ((ImDocumentError) errors[e]), epBw);
				
				//	store false positives
				if (falsePositives == null)
					falsePositives = idep.getFalsePositiveList();
				for (int fp = 0; fp < falsePositives.size(); fp++) {
					ImDocumentError ide = ((ImDocumentError) falsePositives.get(fp));
					if (!categories[c].equals(ide.category))
						continue;
					if (!types[t].equals(ide.type))
						continue;
					storeError("FALPOS", ide, epBw);
					falsePositives.remove(fp--);
				}
			}
		}
		epBw.flush();
	}
	private static void storeError(String type, ImDocumentError ide, BufferedWriter epBw) throws IOException {
		epBw.write(type);
		epBw.write("\t" + ide.severity);
		epBw.write("\t" + ide.description);
		epBw.write("\t" + ide.source);
		epBw.write("\t" + ide.subjectClass);
		epBw.write("\t" + ide.subjectType);
		epBw.write("\t" + ide.typeInternalSubjectId);
		epBw.write("\t" + ide.subjectPageId);
		epBw.write("\t" + ide.subjectLastPageId);
		epBw.write("\t");
		if (ide.subjectFirstWord != null)
			epBw.write(ide.subjectFirstWord.getLocalID());
		epBw.write("\t");
		if (ide.subjectLastWord != null)
			epBw.write(ide.subjectLastWord.getLocalID());
		epBw.newLine();
	}
	
	/**
	 * An error in an Image Markup document.
	 * 
	 * @author sautter
	 */
	public static class ImDocumentError extends DocumentError {
		
		/** the class of the error subject, i.e., the type of Image Markup object (one of 'document', 'font', 'word', 'page', 'region', 'annotation', and 'supplement') */
		public final String subjectClass;
		
		/** the type of the error subject */
		public final String subjectType;
		
		/** a type independent identifier for the error subject */
		public final String typeInternalSubjectId;
		
		/** the ID of the (first) page of the error subject */
		public final int subjectPageId;
		
		/** the first word of the error subject (may be null) */
		public final ImWord subjectFirstWord;
		
		/** the ID of the (last) page of the error subject */
		public final int subjectLastPageId;
		
		/** the last word of the error subject (may be null) */
		public final ImWord subjectLastWord;
		
		/** the (document internally) unique identifier for the error, used for duplicate tracking */
		public final String id;
		
		/** Constructor
		 * @param source the name of the error source
		 * @param subject the object the error pertains to
		 * @param parent the document the error subject belongs to
		 * @param category the error category (for grouping)
		 * @param type the error type (for grouping)
		 * @param description the detailed error description
		 * @param severity the severity of the error (one of 'blocker', 'critical', 'major', and 'minor')
		 */
		public ImDocumentError(String source, Attributed subject, ImDocument parent, String category, String type, String description, String severity) {
			super(source, subject, category, type, description, severity);
			this.subjectClass = getErrorSubjectClass(subject);
			this.subjectType = getErrorSubjectType(subject);
			this.typeInternalSubjectId = getTypeInternalErrorSubjectId(subject, parent);
			this.subjectPageId = getErrorSubjectPageId(subject, parent);
			this.subjectFirstWord = getErrorSubjectFirstWord(subject, parent);
			this.subjectLastPageId = getErrorSubjectLastPageId(subject, parent);
			this.subjectLastWord = getErrorSubjectLastWord(subject, parent);
			this.id = (this.category + "." + this.type + "." + this.subjectType + "." + this.typeInternalSubjectId);
		}
		
		/** Constructor (for de-serialization)
		 * @param source the name of the error source
		 * @param subject the object the error pertains to
		 * @param category the error category (for grouping)
		 * @param type the error type (for grouping)
		 * @param description the detailed error description
		 * @param severity the severity of the error (one of 'blocker', 'critical', 'major', and 'minor')
		 * @param subjectClass the class of the error subject (one of 'document', 'font', 'word', 'page', 'region', 'annotation', and 'supplement')
		 * @param subjectType the type of the error subject
		 * @param typeInternalSubjectId the type independent identifier if the subject
		 * @param subjectPageId the ID of the (first) page of the error subject
		 * @param subjectFirstWord the first word of the error subject (may be null)
		 * @param subjectLastPageId the ID of the (last) page of the error subject
		 * @param subjectLastWord the last word of the error subject (may be null)
		 */
		ImDocumentError(String source, Attributed subject, String category, String type, String description, String severity, String subjectClass, String subjectType, String typeInternalSubjectId, int subjectPageId, ImWord subjectFirstWord, int subjectLastPageId, ImWord subjectLastWord) {
			super(source, subject, category, type, description, severity);
			this.subjectClass = subjectClass;
			this.subjectType = subjectType;
			this.typeInternalSubjectId = typeInternalSubjectId;
			this.subjectPageId = subjectPageId;
			this.subjectFirstWord = subjectFirstWord;
			this.subjectLastPageId = subjectLastPageId;
			this.subjectLastWord = subjectLastWord;
			this.id = (this.category + "." + this.type + "." + this.subjectType + "." + this.typeInternalSubjectId);
		}
		public String[] getSubjectData() {
			String[] sData = {
				this.subjectClass,
				this.subjectType,
				this.typeInternalSubjectId,
				("" + this.subjectPageId),
				("" + this.subjectLastPageId),
				((this.subjectFirstWord == null) ? "" : this.subjectFirstWord.getLocalID()),
				((this.subjectLastWord == null) ? "" : this.subjectLastWord.getLocalID())
			};
			return sData;
		}
		public int hashCode() {
			return this.id.hashCode();
		}
		public boolean equals(Object obj) {
			return ((obj instanceof ImDocumentError) && this.id.equals(((ImDocumentError) obj).id));
		}
	}
	
	private static ImObject getErrorSubject(Attributed subject, Attributed parent, String errorType) {
		if (subject instanceof ImObject)
			return ((ImObject) subject);
		if (subject instanceof LazyAnnotation)
			return ((LazyAnnotation) subject).getImBasisOf((LazyAnnotation) subject);
		if (subject instanceof Annotation) {
			Annotation annot = ((Annotation) subject);
			ImDocumentRoot imDoc;
			if (parent instanceof ImDocumentRoot)
				imDoc = ((ImDocumentRoot) parent);
			else if (annot.getDocument() instanceof ImDocumentRoot) // also works if parent is lazy annotation, as annotation has wrapped document root as parent
				imDoc = ((ImDocumentRoot) annot.getDocument());
			else return null;
			
			//	try direct access first
			ImObject imo = imDoc.basisOf(annot);
			if (imo != null)
				return imo;
			
			//	try going by words
			ImWord startImw = imDoc.firstWordOf(annot);
			ImWord endImw = imDoc.lastWordOf(annot);
			if ((startImw == null) && (endImw == null)) {
				if (Token.TOKEN_ANNOTATION_TYPE.equals(annot.getType()) && (annot instanceof QueriableAnnotation))
					return getErrorSubject(imDoc.tokenAt(((QueriableAnnotation) annot).getAbsoluteStartIndex()), parent, errorType);
				return null;
			}
			
			//	try annotations first, we have better filters for them (more accurate indexes)
			ImAnnotation[] imAnnots = startImw.getDocument().getAnnotations(startImw, null);
			for (int a = 0; a < imAnnots.length; a++) {
				if ((imAnnots[a].getLastWord() == endImw) && imAnnots[a].getType().equals(annot.getType()))
					return imAnnots[a];
			}
			
			//	try regions first if start and end are on same page (type filter should catch but everything)
			if ((startImw.pageId == endImw.pageId)) {
				ImRegion[] regions = startImw.getDocument().getPage(startImw.pageId).getRegions(annot.getType());
				ImRegion subjectRegion = null;
				for (int r = 0; r < regions.length; r++) {
					if (!regions[r].bounds.includes(startImw.bounds, false))
						continue;
					if (!regions[r].bounds.includes(endImw.bounds, false))
						continue;
					subjectRegion = regions[r]; // prefer inmost match (regions come in size order, so smallest comes last)
				}
				if (subjectRegion != null)
					return subjectRegion;
			}
			
			//	handle paragraphs (logical ones are synthetic)
			if (PARAGRAPH_TYPE.equals(annot.getType())) {
				ImRegion subjectRegion = null;
				
				//	this error is about the paragraph end, use (last) region spanned by logical paragraph
				if (errorType.endsWith("End")) {
					ImRegion[] regions = endImw.getDocument().getPage(endImw.pageId).getRegions(ImRegion.PARAGRAPH_TYPE);
					for (int r = 0; r < regions.length; r++) {
						if (regions[r].bounds.includes(endImw.bounds, false))
//							return regions[r]; // prefer inmost match (regions come in size order, so smallest comes last)
							subjectRegion = regions[r]; // prefer inmost match (regions come in size order, so smallest comes last)
					}
				}
				
				//	use (first) region spanned by logical paragraph
				else {
					ImRegion[] regions = startImw.getDocument().getPage(startImw.pageId).getRegions(ImRegion.PARAGRAPH_TYPE);
					for (int r = 0; r < regions.length; r++) {
						if (regions[r].bounds.includes(startImw.bounds, false))
//							return regions[r]; // prefer inmost match (regions come in size order, so smallest comes last)
							subjectRegion = regions[r]; // prefer inmost match (regions come in size order, so smallest comes last)
					}
				}
				
				//	found something, use it
				if (subjectRegion != null)
					return subjectRegion;
			}
		}
		if (subject instanceof Token) {
			if (parent instanceof LazyAnnotation)
				 return ((LazyAnnotation) parent).getImBasisOf((Token) subject);
			else if (parent instanceof ImDocumentRoot)
				 return ((ImDocumentRoot) parent).basisOf((Token) subject);
		}
		return null;
	}
	
	private static ImObject getErrorSubject(ImDocument doc, String subjectClass, String subjectType, String subjectId, int subjectPageId, ImWord subjectFirstWord) {
		if ("document".equals(subjectClass))
			return doc;
		if ("font".equals(subjectClass))
			return doc.getFont(subjectId);
		if ("supplement".equals(subjectClass))
			return doc.getSupplement(subjectId);
		if ("word".equals(subjectClass))
			return doc.getWord(subjectId);
		if ("page".equals(subjectClass))
			return doc.getPage(subjectPageId);
		if ("region".equals(subjectClass)) {
			ImPage page = doc.getPage(subjectPageId);
			ImRegion[] regions = page.getRegions(subjectType);
			for (int r = 0; r < regions.length; r++) {
				if (subjectId.equals(getTypeInternalErrorSubjectId(regions[r], doc)))
					return regions[r];
			}
		}
		if ("annotation".equals(subjectClass)) {
			ImAnnotation[] annots = doc.getAnnotations(subjectFirstWord, null);
			for (int a = 0; a < annots.length; a++) {
				if (subjectType.equals(annots[a].getType()) && subjectId.equals(getTypeInternalErrorSubjectId(annots[a], doc)))
					return annots[a];
			}
		}
		return null;
	}
	
	/**
	 * Obtain the class of the error subject, i.e., the type of Image Markup
	 * object (one of 'document', 'font', 'word', 'page', 'region',
	 * 'annotation', and 'supplement').
	 * @param subject the error subject whose object class to determine
	 * @return the object type
	 */
	public static String getErrorSubjectClass(Attributed subject) {
		if (subject instanceof ImDocument)
			return "document";
		if (subject instanceof ImFont)
			return "font";
		if (subject instanceof ImWord)
			return "word";
		if (subject instanceof ImPage)
			return "page";
		if (subject instanceof ImRegion)
			return "region";
		if (subject instanceof ImAnnotation)
			return "annotation";
		if (subject instanceof ImSupplement)
			return "supplement";
		return null;
	}
	
	/**
	 * Obtain the type of an error subject.
	 * @param subject the error subject whose type to determine
	 * @return the subject type
	 */
	public static String getErrorSubjectType(Attributed subject) {
		if (subject instanceof ImObject)
			return ((ImObject) subject).getType();
		if (subject instanceof Annotation)
			((Annotation) subject).getType();
		if (subject instanceof Token)
			return "token";
		return subject.toString();
	}
	
	/**
	 * Obtain a type independent identifier for the error subject. This helps
	 * un-wrapping Image Markup objects from XML wrappers for comparison.
	 * @param subject the error subject whose identifier to determine
	 * @param parent the parent document of the error subject
	 * @return the subject identifier
	 */
	public static String getTypeInternalErrorSubjectId(Attributed subject, Attributed parent) {
		if (subject instanceof ImDocument)
			return ((ImDocument) subject).docId;
		if (subject instanceof ImFont)
			return ((ImFont) subject).name;
		if (subject instanceof ImSupplement)
			return ((ImSupplement) subject).getId();
		if (subject instanceof ImLayoutObject)
			return (((ImLayoutObject) subject).pageId + "." + ((ImLayoutObject) subject).bounds);
		if (subject instanceof ImAnnotation) {
			ImWord startImw = ((ImAnnotation) subject).getFirstWord();
			ImWord endImw = ((ImAnnotation) subject).getLastWord();
			return (startImw.getLocalID() + "-" + endImw.getLocalID());
		}
		if (subject instanceof ImDocumentRoot)
			return ((ImDocumentRoot) subject).document().docId;
		if (subject instanceof LazyAnnotation)
			return getTypeInternalErrorSubjectId(((LazyAnnotation) subject).getImBasisOf((LazyAnnotation) subject), parent);
		if (subject instanceof Annotation) {
			Annotation annot = ((Annotation) subject);
			ImDocumentRoot imDoc;
			if (parent instanceof ImDocumentRoot)
				imDoc = ((ImDocumentRoot) parent);
			else if (annot.getDocument() instanceof ImDocumentRoot) // also works if parent is lazy annotation, as annotation has wrapped document root as parent
				imDoc = ((ImDocumentRoot) annot.getDocument());
			else return null;
			ImWord startImw = imDoc.firstWordOf(annot);
			ImWord endImw = imDoc.lastWordOf(annot);
			if ((startImw == null) && (endImw == null)) {
				if (Token.TOKEN_ANNOTATION_TYPE.equals(annot.getType()) && (annot instanceof QueriableAnnotation))
					return getTypeInternalErrorSubjectId(imDoc.tokenAt(((QueriableAnnotation) annot).getAbsoluteStartIndex()), parent);
				return null;
			}
			return (startImw.getLocalID() + "-" + endImw.getLocalID());
		}
		if (subject instanceof Token) {
			if (parent instanceof LazyAnnotation) {
				ImWord startImw = ((LazyAnnotation) parent).getFirstImWordOf((Token) subject);
				ImWord endImw = ((LazyAnnotation) parent).getLastImWordOf((Token) subject);
				return (startImw.getLocalID() + "-" + endImw.getLocalID());
			}
			else if (parent instanceof ImDocumentRoot) {
				ImWord startImw = ((ImDocumentRoot) parent).firstWordOf((Token) subject);
				ImWord endImw = ((ImDocumentRoot) parent).lastWordOf((Token) subject);
				return (startImw.getLocalID() + "-" + endImw.getLocalID());
			}
		}
		return ("" + subject.hashCode());
	}
	
	private static int getErrorSubjectPageId(Attributed subject, Attributed parent) {
		if (subject instanceof ImDocument)
			return -1;
		if (subject instanceof ImFont)
			return -1;
		if (subject instanceof ImLayoutObject)
			return ((ImLayoutObject) subject).pageId;
		if (subject instanceof ImAnnotation)
			return ((ImAnnotation) subject).getFirstWord().pageId;
		if (subject instanceof ImDocumentRoot) {
			ImWord firstWord = ((ImDocumentRoot) subject).firstWordOf((ImDocumentRoot) subject);
			return ((firstWord == null) ? -1 : firstWord.pageId);
		}
		if (subject instanceof LazyAnnotation)
			return getErrorSubjectPageId(((LazyAnnotation) subject).getImBasisOf((LazyAnnotation) subject), parent);
		if (subject instanceof Annotation) {
			Annotation annot = ((Annotation) subject);
			if (parent instanceof ImDocumentRoot) {
				ImWord startImw = ((ImDocumentRoot) parent).firstWordOf(annot);
				if (startImw == null) {
					if (Token.TOKEN_ANNOTATION_TYPE.equals(annot.getType()) && (annot instanceof QueriableAnnotation))
						return getErrorSubjectPageId(((ImDocumentRoot) parent).tokenAt(((QueriableAnnotation) annot).getAbsoluteStartIndex()), parent);
					return -1;
				}
				return startImw.pageId;
			}
			QueriableAnnotation doc = annot.getDocument(); // also works if parent is lazy annotation, as annotation has wrapped document root as parent
			if (doc instanceof ImDocumentRoot) {
				ImWord startImw = ((ImDocumentRoot) doc).firstWordOf(annot);
				if (startImw == null) {
					if (Token.TOKEN_ANNOTATION_TYPE.equals(annot.getType()) && (annot instanceof QueriableAnnotation))
						return getErrorSubjectPageId(((ImDocumentRoot) doc).tokenAt(((QueriableAnnotation) annot).getAbsoluteStartIndex()), parent);
					return -1;
				}
				return startImw.pageId;
			}
		}
		if (subject instanceof Token) {
			if (parent instanceof LazyAnnotation)
				 return ((LazyAnnotation) parent).getFirstImWordOf((Token) subject).pageId;
			else if (parent instanceof ImDocumentRoot)
				return ((ImDocumentRoot) parent).firstWordOf((Token) subject).pageId;
		}
		Object pidObj = subject.getAttribute(PAGE_ID_ATTRIBUTE);
		if (pidObj != null) try {
			return Integer.parseInt(pidObj.toString());
		} catch (NumberFormatException nfe) {}
		return -1;
	}
	
	private static ImWord getErrorSubjectFirstWord(Attributed subject, Attributed parent) {
		if (subject instanceof ImDocument)
			return null;
		if (subject instanceof ImFont)
			return null;
		if (subject instanceof ImWord)
			return ((ImWord) subject);
		if (subject instanceof ImRegion) {
			ImWord[] words = ((ImRegion) subject).getWords();
			if (words.length == 0)
				return null;
			ImUtils.sortLeftRightTopDown(words);
			return words[0];
		}
		if (subject instanceof ImAnnotation)
			return ((ImAnnotation) subject).getFirstWord();
		if (subject instanceof ImDocumentRoot)
			return ((ImDocumentRoot) subject).firstWordOf((ImDocumentRoot) subject);
		if (subject instanceof LazyAnnotation)
			return getErrorSubjectFirstWord(((LazyAnnotation) subject).getImBasisOf((LazyAnnotation) subject), parent);
		if (subject instanceof Annotation) {
			Annotation annot = ((Annotation) subject);
			if (parent instanceof ImDocumentRoot) {
				ImWord startImw = ((ImDocumentRoot) parent).firstWordOf(annot);
				if ((startImw == null) && Token.TOKEN_ANNOTATION_TYPE.equals(annot.getType()) && (annot instanceof QueriableAnnotation))
					return getErrorSubjectFirstWord(((ImDocumentRoot) parent).tokenAt(((QueriableAnnotation) annot).getAbsoluteStartIndex()), parent);
				return startImw;
			}
			QueriableAnnotation doc = annot.getDocument(); // also works if parent is lazy annotation, as annotation has wrapped document root as parent
			if (doc instanceof ImDocumentRoot) {
				ImWord startImw = ((ImDocumentRoot) doc).firstWordOf(annot);
				if ((startImw == null) && Token.TOKEN_ANNOTATION_TYPE.equals(annot.getType()) && (annot instanceof QueriableAnnotation))
					return getErrorSubjectFirstWord(((ImDocumentRoot) doc).tokenAt(((QueriableAnnotation) annot).getAbsoluteStartIndex()), parent);
				return startImw;
			}
		}
		if (subject instanceof Token) {
			if (parent instanceof LazyAnnotation)
				 return ((LazyAnnotation) parent).getFirstImWordOf((Token) subject);
			else if (parent instanceof ImDocumentRoot)
				return ((ImDocumentRoot) parent).firstWordOf((Token) subject);
		}
		return null;
	}
	
	private static int getErrorSubjectLastPageId(Attributed subject, Attributed parent) {
		if (subject instanceof ImDocument)
			return -1;
		if (subject instanceof ImFont)
			return -1;
		if (subject instanceof ImLayoutObject)
			return ((ImLayoutObject) subject).pageId;
		if (subject instanceof ImAnnotation)
			return ((ImAnnotation) subject).getLastWord().pageId;
		if (subject instanceof ImDocumentRoot) {
			ImWord lastWord = ((ImDocumentRoot) subject).lastWordOf((ImDocumentRoot) subject);
			return ((lastWord == null) ? -1 : lastWord.pageId);
		}
		if (subject instanceof LazyAnnotation)
			return getErrorSubjectLastPageId(((LazyAnnotation) subject).getImBasisOf((LazyAnnotation) subject), parent);
		if (subject instanceof Annotation) {
			Annotation annot = ((Annotation) subject);
			if (parent instanceof ImDocumentRoot) {
				ImWord endImw = ((ImDocumentRoot) parent).lastWordOf(annot);
				if (endImw == null) {
					if (Token.TOKEN_ANNOTATION_TYPE.equals(annot.getType()) && (annot instanceof QueriableAnnotation))
						return getErrorSubjectLastPageId(((ImDocumentRoot) parent).tokenAt(((QueriableAnnotation) annot).getAbsoluteStartIndex()), parent);
					return -1;
				}
				return endImw.pageId;
			}
			QueriableAnnotation doc = annot.getDocument(); // also works if parent is lazy annotation, as annotation has wrapped document root as parent
			if (doc instanceof ImDocumentRoot) {
				ImWord endImw = ((ImDocumentRoot) doc).lastWordOf(annot);
				if (endImw == null) {
					if (Token.TOKEN_ANNOTATION_TYPE.equals(annot.getType()) && (annot instanceof QueriableAnnotation))
						return getErrorSubjectLastPageId(((ImDocumentRoot) doc).tokenAt(((QueriableAnnotation) annot).getAbsoluteStartIndex()), parent);
					return -1;
				}
				return endImw.pageId;
			}
		}
		if (subject instanceof Token) {
			if (parent instanceof LazyAnnotation)
				 return ((LazyAnnotation) parent).getLastImWordOf((Token) subject).pageId;
			else if (parent instanceof ImDocumentRoot)
				return ((ImDocumentRoot) parent).lastWordOf((Token) subject).pageId;
		}
		Object pidObj = subject.getAttribute(LAST_PAGE_ID_ATTRIBUTE, subject.getAttribute(PAGE_ID_ATTRIBUTE));
		if (pidObj != null) try {
			return Integer.parseInt(pidObj.toString());
		} catch (NumberFormatException nfe) {}
		return -1;
	}
	
	private static ImWord getErrorSubjectLastWord(Attributed subject, Attributed parent) {
		if (subject instanceof ImDocument)
			return null;
		if (subject instanceof ImFont)
			return null;
		if (subject instanceof ImWord)
			return ((ImWord) subject);
		if (subject instanceof ImRegion) {
			ImWord[] words = ((ImRegion) subject).getWords();
			if (words.length == 0)
				return null;
			ImUtils.sortLeftRightTopDown(words);
			return words[words.length - 1];
		}
		if (subject instanceof ImAnnotation)
			return ((ImAnnotation) subject).getLastWord();
		if (subject instanceof ImDocumentRoot)
			return ((ImDocumentRoot) subject).lastWordOf((ImDocumentRoot) subject);
		if (subject instanceof LazyAnnotation)
			return getErrorSubjectLastWord(((LazyAnnotation) subject).getImBasisOf((LazyAnnotation) subject), parent);
		if (subject instanceof Annotation) {
			Annotation annot = ((Annotation) subject);
			if (parent instanceof ImDocumentRoot) {
				ImWord endImw = ((ImDocumentRoot) parent).lastWordOf(annot);
				if ((endImw == null) && Token.TOKEN_ANNOTATION_TYPE.equals(annot.getType()) && (annot instanceof QueriableAnnotation))
					return getErrorSubjectLastWord(((ImDocumentRoot) parent).tokenAt(((QueriableAnnotation) annot).getAbsoluteStartIndex()), parent);
				return endImw;
			}
			QueriableAnnotation doc = annot.getDocument(); // also works if parent is lazy annotation, as annotation has wrapped document root as parent
			if (doc instanceof ImDocumentRoot) {
				ImWord endImw = ((ImDocumentRoot) doc).lastWordOf(annot);
				if ((endImw == null) && Token.TOKEN_ANNOTATION_TYPE.equals(annot.getType()) && (annot instanceof QueriableAnnotation))
					return getErrorSubjectLastWord(((ImDocumentRoot) doc).tokenAt(((QueriableAnnotation) annot).getAbsoluteStartIndex()), parent);
				return endImw;
			}
		}
		if (subject instanceof Token) {
			if (parent instanceof LazyAnnotation)
				 return ((LazyAnnotation) parent).getLastImWordOf((Token) subject);
			else if (parent instanceof ImDocumentRoot)
				return ((ImDocumentRoot) parent).lastWordOf((Token) subject);
		}
		return null;
	}
//	
//	private static Token getAnnotatedToken(Annotation annot, Attributed parent) {
//		if (Token.TOKEN_ANNOTATION_TYPE.equals(annot.getType()) && (annot instanceof QueriableAnnotation)) {
//			if (parent instanceof ImDocumentRoot)
//				return ((ImDocumentRoot) parent).tokenAt(((QueriableAnnotation) annot).getAbsoluteStartIndex());
//			QueriableAnnotation doc = annot.getDocument();
//			if (doc instanceof ImDocumentRoot)
//				return ((ImDocumentRoot) doc).tokenAt(((QueriableAnnotation) annot).getAbsoluteStartIndex());
//		}
//		return null;
//	}
}
