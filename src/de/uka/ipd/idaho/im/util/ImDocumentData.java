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

import java.awt.ComponentOrientation;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Properties;
import java.util.TreeMap;
import java.util.zip.ZipEntry;

import de.uka.ipd.idaho.easyIO.streams.DataHashOutputStream;
import de.uka.ipd.idaho.gamta.Attributed;
import de.uka.ipd.idaho.gamta.util.ProgressMonitor;
import de.uka.ipd.idaho.im.ImAnnotation;
import de.uka.ipd.idaho.im.ImDocument;
import de.uka.ipd.idaho.im.ImRegion;
import de.uka.ipd.idaho.im.ImSupplement;
import de.uka.ipd.idaho.im.util.ImDocumentIO.EntryVerificationLogger;

/**
 * A object providing and storing the data for an Image Markup document, to
 * abstract from different ways of storing Image Markup documents.<br>
 * The attributes of the Image Markup document represented by the underlying
 * data are accessible in a read-only fashion via this object. This is to avoid
 * loading the entire document before being able to decide whether or not some
 * action is to be taken.<br>
 * Likewise, the supplements belonging to the Image Markup document represented
 * by the underlying data are also accessible in a read-only fashion via this
 * object. This is to allow, for instance, loading a page image without having
 * to load the entire document.
 * 
 * @author sautter
 */
public abstract class ImDocumentData {
	
	/**
	 * An Image Markup document backed by an instance of Image Markup document
	 * data.
	 * 
	 * @author sautter
	 */
	public static abstract class DataBackedImDocument extends ImDocument {
		
		/** Constructor
		 * @param docId
		 * @param orientation
		 */
		protected DataBackedImDocument(String docId, ComponentOrientation orientation) {
			super(docId, orientation);
		}
		
		/** Constructor
		 * @param docId
		 */
		protected DataBackedImDocument(String docId) {
			super(docId);
		}
		
		/**
		 * Retrieve the Image Markup document data backing the document.
		 * @return the backing Image Markup document data
		 */
		public abstract ImDocumentData getDocumentData();
		
		/**
		 * Dispose both the document proper as well as its backing document
		 * data object.
		 * @see de.uka.ipd.idaho.im.ImDocument#dispose()
		 * @see de.uka.ipd.idaho.im.util.ImDocumentData#dispose()
		 */
		public void dispose() {
			super.dispose();
			ImDocumentData data = this.getDocumentData();
			if (data != null)
				data.dispose();
		}
	}
	
	/**
	 * Metadata of a single entry in an Image Markup document.
	 * 
	 * @author sautter
	 */
	public static class ImDocumentEntry implements Comparable {
		
		/** the logical name of the entry, e.g. 'document.tsv' */
		public final String name;
		
		/** the size of the entry in bytes */
		public final int size;
		
		/** the timestamp the entry was last modified */
		public final long updateTime;
		
		/** the hash (checksum) of the entry data */
		public final String dataHash;
		
		/** the physical name of the file holding the entry data (including the hash), e.g. 'document.&lt;hashAs Hex&gt;.tsv' */
		public final String fileName;
		
		/** Constructor
		 * @param name the logical name of the entry
		 * @param updateTime the timestamp the entry was last modified
		 * @param dataHash the hash of the entry data
		 * @deprecated retained for now for backward compatibility with CSV mode
		 */
		public ImDocumentEntry(String name, long updateTime, String dataHash) {
			this(name, -1, updateTime, dataHash);
		}
		
		/** Constructor
		 * @param name the logical name of the entry
		 * @param size the size of the entry in bytes
		 * @param updateTime the timestamp the entry was last modified
		 * @param dataHash the hash of the entry data
		 */
		public ImDocumentEntry(String name, int size, long updateTime, String dataHash) {
			this.name = name;
			this.size = size;
			this.updateTime = updateTime;
			this.dataHash = dataHash;
			if (name.lastIndexOf('.') == -1)
				this.fileName = (this.name + "." + this.dataHash);
			else this.fileName = (this.name.substring(0, this.name.lastIndexOf('.')) + "." + this.dataHash + this.name.substring(this.name.lastIndexOf('.')));
		}
		
		/** Constructor for exchange folder based transfer
		 * @param file the file containing the IMF entry
		 */
		public ImDocumentEntry(File file) {
			this(file.getName(), ((int) file.length()), file.lastModified());
		}
		
		/** Constructor for ZIP compressed stream transfer
		 * @param ze the ZIP entry describing the transferred IMF entry
		 */
		public ImDocumentEntry(ZipEntry ze) {
			this(ze.getName(), ((int) ze.getSize()), ze.getTime());
		}
		
		private ImDocumentEntry(String fileName, int size, long updateTime) {
			String[] fileNameParts = fileName.split("\\.");
			if (fileNameParts.length < 2)
				throw new IllegalArgumentException("Illegal name+hash string '" + fileName + "'");
			if (updateTime < 1)
				throw new IllegalArgumentException("Illegal update time " + updateTime + " in IMF Entry '" + fileName + "'");
			this.size = size;
			this.updateTime = updateTime;
			if (fileNameParts.length == 2) {
				this.name = fileNameParts[0];
				this.dataHash = fileNameParts[1];
			}
			else {
				StringBuffer entryName = new StringBuffer();
				for (int p = 0; p < fileNameParts.length; p++) {
					if (p == (fileNameParts.length - 2))
						continue; // skip data hash in logical file name (entry name)
					if (p != 0)
						entryName.append('.');
					entryName.append(fileNameParts[p]);
				}
				this.name = entryName.toString();
				this.dataHash = fileNameParts[fileNameParts.length - 2];
			}
			this.fileName = fileName;
		}
		
		/**
		 * Create the name of a file to store this IMF Entry on persistent
		 * storage. The returned file name takes the form '&lt;nameLessFileExtension&gt;.
		 * &lt;dataHash&gt;.&lt;fileExtension&gt;'.
		 * @return the file name for the IMF Entry
		 * @deprecated use public final field directly
		 */
		public String getFileName() {
			return this.fileName;
		}
		
		/**
		 * Convert the IMF Entry into a tab separated string for listing. The
		 * returned string has the form '&lt;name&gt; &lt;updateTime&gt; 
		 * &lt;dataHash&gt;'.
		 * @return a tab separated string representation of the IMF Entry
		 * @deprecated use signature with boolean argument
		 */
		public String toTabString() {
			return this.toTabString((ImDocumentIO.defaultStorageModeParams & ImDocumentIO.STORAGE_MODE_TSV) != 0);
		}
		
		/**
		 * Convert the IMF Entry into a tab separated string for listing. The
		 * returned string has the form '&lt;name&gt; &lt;updateTime&gt; 
		 * &lt;dataHash&gt;'.
		 * @param includeSize include entry size (4 column TSV mode)?
		 * @return a tab separated string representation of the IMF Entry
		 */
		public String toTabString(boolean includeSize) {
			return (this.name + (includeSize ? ("\t" + this.size) : "") + "\t" + this.updateTime + "\t" + this.dataHash);
		}
		
		/**
		 * Compares this IMF entry to another one based on the names, sorting
		 * in case sensitive lexicographical order.
		 * @param obj the IMF entry to compare this one to
		 * @return the comparison result
		 * @see java.lang.Comparable#compareTo(java.lang.Object)
		 */
		public int compareTo(Object obj) {
			return ((obj instanceof ImDocumentEntry) ? this.name.compareTo(((ImDocumentEntry) obj).name) : -1);
		}
		
		/**
		 * Parse an IMF entry from its tab separated string representation, as
		 * returned by the <code>toTabString()</code> method.
		 * @param tabStr the tab separated string representation to parse
		 * @return the IMF entry parsed from the argument string
		 */
		public static ImDocumentEntry fromTabString(String tabStr) {
			String[] imfEntryData = tabStr.split("\\t");
			if (imfEntryData.length == 4) try {
				return new ImDocumentEntry(imfEntryData[0], Integer.parseInt(imfEntryData[1]), Long.parseLong(imfEntryData[2]), imfEntryData[3]);
			} catch (NumberFormatException nfe) {}
			if (imfEntryData.length == 3) try {
				return new ImDocumentEntry(imfEntryData[0], -1, Long.parseLong(imfEntryData[1]), imfEntryData[2]);
			} catch (NumberFormatException nfe) {}
			return null;
		}
	}
	
	private String documentId = null;
	private long storageFlags = -1; // ==> in fact, need to initialize to 'unknown', so any existing entry list can set flags
	final HashMap entriesByName = new LinkedHashMap();
	
	/** Constructor
	 * @param documentId the ID of the Image Markup document represented by this data object
	 */
	protected ImDocumentData() {}
	
	//	TODO allow specifying storage flags via constructor !!!
	
	/**
	 * Retrieve the storage flags to use with the document data object. These
	 * flags are mainly intended for use in IO facilities. If the flags have
	 * not been set, this method returns -1, in which case storage facilities
	 * should use their configured default flags.
	 * @return the storage flags (as a bit vector)
	 */
	public long getStorageFlags() {
		return this.storageFlags; // ==> IN FACT, leave defaulting to storage facilities proper
	}
	
	/**
	 * Set the storage flags to use with the document data object. If the flags
	 * have been set to a value of 0 or greater before, this method throws an
	 * illegal state exception.
	 * @param storageFlags the storageFlags to set
	 */
	protected void setStorageFlags(long storageFlags) {
		if (-1 < this.storageFlags)
			throw new IllegalStateException("Storage flags can be set only once");
		this.storageFlags = storageFlags;
	}
	
	/**
	 * Retrieve the current entries of the document.
	 * @return an array holding the entries
	 */
	public ImDocumentEntry[] getEntries() {
		return ((ImDocumentEntry[]) this.entriesByName.values().toArray(new ImDocumentEntry[this.entriesByName.size()]));
	}
	
	/**
	 * Test if this document data object has an entry with a specific name.
	 * @param entryName the name of the entry to test
	 * @return true if the argument entry if included in this document data
	 */
	public boolean hasEntry(String entryName) {
		return this.entriesByName.containsKey(entryName);
	}
	
	/**
	 * Test if this document data object has a specific entry.
	 * @param entry the entry to test
	 * @return true if the argument entry if included in this document data
	 */
	public boolean hasEntry(ImDocumentEntry entry) {
		return this.hasEntry(entry.name);
	}
	
	/**
	 * Test if this document data object has an entry with a specific name as
	 * well as the data it points to.
	 * @param entryName the name of the entry to test
	 * @return true if this document data has the data for the argument entry
	 */
	public boolean hasEntryData(String entryName) {
		ImDocumentEntry entry = this.getEntry(entryName);
		return ((entry != null) && this.hasEntryData(entry));
	}
	
	/**
	 * Test if this document data object has the data for a specific entry.
	 * Implementations should check for is data is available for the file name
	 * (i.e., entry name and data hash) of the argument entry. The argument
	 * entry does not necessarily have to be part of the current entry list
	 * returned by <code>getEntries()</code>.
	 * @param entry the entry to test
	 * @return true if this document data has the data for the argument entry
	 */
	public abstract boolean hasEntryData(ImDocumentEntry entry);
	
	/**
	 * Test if this document data object has a specific entry. Implementations
	 * should compare both the entry name and the data hash.
	 * @param entry the entry to test
	 * @return true if the argument entry if included in this document data
	 */
	public ImDocumentEntry getEntry(String entryName) {
		return ((ImDocumentEntry) this.entriesByName.get(entryName));
	}
	
	/**
	 * Put an entry into the list of the document data object, potentially
	 * replacing an existing one with the same name. This method is intended
	 * for adding or replacing entries when an entry output stream returned by
	 * either of the the <code>getOutputStream()</code> methods is closed. It
	 * can also be used to update the entry list when reverting to an existing
	 * file with a hash other than the current one.
	 * @param entry the entry to put
	 */
	public ImDocumentEntry putEntry(ImDocumentEntry entry) {
		return ((ImDocumentEntry) this.entriesByName.put(entry.name, entry));
	}
	
	/**
	 * Remove an entry from the list of the document data object. This method
	 * is intended cleaning up entries that have become obsolete in a new
	 * version of the document.
	 * @param entry the entry to put
	 */
	public ImDocumentEntry removeEntry(String entryName) {
		return ((ImDocumentEntry) this.entriesByName.remove(entryName));
	}
	
	/**
	 * Retrieve an input stream to read the data of a document entry from.
	 * Implementations should go by the entry name only, returning an input
	 * stream for the currently valid entry whose name matches the name of the
	 * argument one.
	 * @param entry the entry to get the input stream for
	 * @return an input stream for the entry data
	 * @throws IOException
	 */
	public InputStream getInputStream(ImDocumentEntry entry) throws IOException {
		return this.getInputStream(entry.name);
	}
	
	/**
	 * Retrieve an input stream to read the data of a document entry from.
	 * @param entryName the name of the entry to get the input stream for
	 * @return an input stream for the entry data
	 * @throws IOException
	 */
	public abstract InputStream getInputStream(String entryName) throws IOException;
	
	/**
	 * Retrieve an output stream to write the data of a document entry to.
	 * Implementations should use the entry name only, and compute the data
	 * hash and update the entry list via <code>putEntry()</code> only when
	 * the returned output stream is closed. However, the data has of the
	 * argument entry may be used as a hint regarding the runtime behavior of
	 * implementations.
	 * @param entry the entry to obtain an output stream for
	 * @return an output stream for the argument entry
	 * @throws IOException
	 */
	public OutputStream getOutputStream(ImDocumentEntry entry) throws IOException {
		return this.getOutputStream(entry, !this.hasEntry(entry));
	}
	
	/**
	 * Retrieve an output stream to write the data of a document entry to.
	 * Implementations should use the entry name only, and compute the data
	 * hash and update the entry list via <code>putEntry()</code> only when
	 * the returned output stream is closed. However, the data has of the
	 * argument entry may be used as a hint regarding the runtime behavior of
	 * implementations.
	 * @param entry the entry to obtain an output stream for
	 * @param writeDirectly write directly to the underlying source in all
	 *            cases, avoiding intermediate buffering even at the risk of
	 *            unnecessary writes to persistent storage?
	 * @return an output stream for the argument entry
	 * @throws IOException
	 */
	public OutputStream getOutputStream(ImDocumentEntry entry, boolean writeDirectly) throws IOException {
		return this.getOutputStream(entry.name, writeDirectly);
	}
	
	/**
	 * Retrieve an output stream to write the data of a document entry to.
	 * Implementations should compute the data hash and update the entry list
	 * via <code>putEntry()</code> when the returned output stream is closed.
	 * @param entryName the name of entry to obtain an output stream for
	 * @return an output stream for the argument entry
	 * @throws IOException
	 */
	public OutputStream getOutputStream(String entryName) throws IOException {
		return this.getOutputStream(entryName, !this.hasEntry(entryName));
	}
	
	/**
	 * Retrieve an output stream to write the data of a document entry to.
	 * Implementations should compute the data hash and update the entry list
	 * via <code>putEntry()</code> when the returned output stream is closed.
	 * @param entryName the name of entry to obtain an output stream for
	 * @param writeDirectly write directly to the underlying source in all
	 *            cases, avoiding intermediate buffering even at the risk of
	 *            unnecessary writes to persistent storage?
	 * @return an output stream for the argument entry
	 * @throws IOException
	 */
	public abstract OutputStream getOutputStream(String entryName, boolean writeDirectly) throws IOException;
	
	/**
	 * Instantiate an Image Markup document from the data.
	 * @return the Image Markup document
	 */
	public DataBackedImDocument getDocument() throws IOException {
		return this.getDocument(ProgressMonitor.dummy);
	}
	
	/**
	 * Instantiate an Image Markup document from the data.
	 * @param pm a progress monitor to observe the loading process
	 * @return the Image Markup document
	 */
	public DataBackedImDocument getDocument(ProgressMonitor pm) throws IOException {
		return (this.canLoadDocument() ? ImDocumentIO.loadDocument(this, pm) : null);
	}
	
	private Attributed attributes = null;
	private boolean attributeLoadError = false;
	private void ensureAttributesLoaded() {
		if ((this.attributes == null) && !this.attributeLoadError) try {
			this.attributes = ImDocumentIO.loadDocumentAttributes(this);
		}
		catch (IOException ioe) {
			this.attributeLoadError = true;
		}
	}
	
	/**
	 * Retrieve the attributes of the document. This method is a shorthand for
	 * <code>ImDocumentIO.loadDocumentAttributes(this)</code>. The returned
	 * object is immutable.
	 * @return the document attributes
	 */
	public Attributed getDocumentAttributes() {
		this.ensureAttributesLoaded();
		return new Attributed() {
			public void setAttribute(String name) { /* we're read-only for now */ }
			public Object setAttribute(String name, Object value) {
				return value; // we're read-only for now
			}
			public void copyAttributes(Attributed source) { /* we're read-only for now */ }
			public Object getAttribute(String name) {
				return this.getAttribute(name, null);
			}
			public Object getAttribute(String name, Object def) {
				if (attributes == null)
					return def;
				else return attributes.getAttribute(name, def);
			}
			public boolean hasAttribute(String name) {
				if (attributes == null)
					return false;
				else return attributes.hasAttribute(name);
			}
			public String[] getAttributeNames() {
				if (attributes == null)
					return new String[0];
				else return attributes.getAttributeNames();
			}
			public Object removeAttribute(String name) {
				return null; // we're read-only for now
			}
			public void clearAttributes() { /* we're read-only for now */ }
		};
	}
	
	private ArrayList regions = null;
	private boolean regionLoadError = false;
	private boolean ensureRegionsLoaded() {
		if ((this.regions == null) && !this.regionLoadError) try {
			this.getDocumentId(); // make sure we have a document ID to work with
			ArrayList regs = new ArrayList();
			ImDocumentIO.loadRegions(null, this.documentId, regs, this, EntryVerificationLogger.silent, ProgressMonitor.silent, Integer.MAX_VALUE);
			this.regions = regs;
		}
		catch (IOException ioe) {
			this.regionLoadError = true;
		}
		return (this.regions != null);
	}
	
	/**
	 * Retrieve the annotations of the document. The returned annotations are
	 * immutable, and they are not bounds to an actual document. As a result,
	 * the <code>getFirstWord()</code>, <code>getLastWord()</code>, and
	 * <code>getDocument()</code> methods return null.
	 * @return the document annotations
	 */
	public ImRegion[] getRegions() {
		if (this.ensureRegionsLoaded())
			return ((ImRegion[]) this.regions.toArray(new ImRegion[this.regions.size()]));
		else return new ImRegion[0];
	}
	
	private ArrayList annotations = null;
	private boolean annotationLoadError = false;
	private boolean ensureAnnotationsLoaded() {
		if ((this.annotations == null) && !this.annotationLoadError) try {
			this.getDocumentId(); // make sure we have a document ID to work with
			ArrayList annots = new ArrayList();
			ImDocumentIO.loadAnnotations(null, this.documentId, annots, this, EntryVerificationLogger.silent, ProgressMonitor.silent, Integer.MAX_VALUE);
			this.annotations = annots;
		}
		catch (IOException ioe) {
			this.annotationLoadError = true;
		}
		return (this.annotations != null);
	}
	
	/**
	 * Retrieve the annotations of the document. The returned annotations are
	 * immutable, and they are not bounds to an actual document. As a result,
	 * the <code>getFirstWord()</code>, <code>getLastWord()</code>, and
	 * <code>getDocument()</code> methods return null.
	 * @return the document annotations
	 */
	public ImAnnotation[] getAnnotations() {
		if (this.ensureAnnotationsLoaded())
			return ((ImAnnotation[]) this.annotations.toArray(new ImAnnotation[this.annotations.size()]));
		else return new ImAnnotation[0];
	}
	
	private TreeMap supplementsById = null;
	private boolean supplementLoadError = false;
	private boolean ensureSupplementsLoaded() {
		if ((this.supplementsById == null) && !this.supplementLoadError) try {
			TreeMap supplsById = new TreeMap();
			ImDocumentIO.loadSupplements(null, supplsById, this, EntryVerificationLogger.silent, ProgressMonitor.silent);
			this.supplementsById = supplsById;
		}
		catch (IOException ioe) {
			this.supplementLoadError = true;
		}
		return (this.supplementsById != null);
	}
	
	/**
	 * Retrieve a document supplement by its ID.
	 * @param sid the ID of the required supplement
	 */
	public ImSupplement getSupplement(String sid) {
		if (this.ensureSupplementsLoaded())
			return ((sid == null) ? null : ((ImSupplement) this.supplementsById.get(sid)));
		else return null;
	}
	
	/**
	 * Retrieve the IDs of the document supplements.
	 * @return an array holding the supplement IDs
	 */
	public String[] getSupplementIDs() {
		if (this.ensureSupplementsLoaded())
			return ((String[]) this.supplementsById.keySet().toArray(new String[this.supplementsById.size()]));
		else return new String[0];
	}
	
	/**
	 * Retrieve the document supplements proper.
	 * @return an array holding the supplements
	 */
	public ImSupplement[] getSupplements() {
		if (this.ensureSupplementsLoaded())
			return ((ImSupplement[]) this.supplementsById.values().toArray(new ImSupplement[this.supplementsById.size()]));
		else return new ImSupplement[0];
	}
	
	/**
	 * Retrieve the identifier of the Image Markup document represented by the
	 * underlying data. This default implementation reads the identifier from
	 * the document attributes. Sub classes are welcome to overwrite it with a
	 * more efficient implementation.
	 * @return an identifier of the document
	 */
	public String getDocumentId() {
		if (this.documentId == null) {
			Attributed attributes = this.getDocumentAttributes();
			this.documentId = ((String) attributes.getAttribute(ImDocumentIO.DOCUMENT_ID_ATTRIBUTE));
		}
		return this.documentId;
	}
	
	/**
	 * Retrieve an identifier for the underlying data source. This identifier
	 * is not equal to the ID of the document represented by the underlying
	 * data, even though it may include it. Rather, the identifier returned by
	 * this method is intended to enable IO facilities to distinguish different
	 * document data objects and adjust their behavior accordingly. Pure in-
	 * memory or cache based implementations should return null to indicate
	 * they are not persistent.
	 * @return an identifier of the underlying data source
	 */
	public abstract String getDocumentDataId();
	
	/**
	 * Retrieve a generic property of the IM document data object proper (not
	 * a property of the document it can instantiate).
	 * @param name the name of the property
	 * @return the property with the argument name
	 */
	public String getProperty(String name) {
		return (((this.properties == null) || (name == null)) ? null : this.properties.getProperty(name));
	}
	
	/**
	 * Retrieve the names of the generic properties of the IM document data
	 * object proper (not the properties of the document it can instantiate).
	 * @return an array holding the property names
	 */
	public String[] getPropertyNames() {
		if (this.properties == null)
			return new String[0];
		String[] propNames = ((String[]) this.properties.keySet().toArray(new String[this.properties.size()]));
		Arrays.sort(propNames);
		return propNames;
	}
	
	/**
	 * Set a generic property of the IM document data object proper (this does
	 * not affect the properties of the document it can instantiate). Setting a
	 * property to null erases it. In TSV mode, properties can be stored in the
	 * entry list of a document data object; In CSV mode, client code has to
	 * take care of storing any properties in some custom way.
	 * @param name the name of the property
	 * @param value the value of the property
	 * @return the value previously associated with the argument property name
	 */
	public String setProperty(String name, String value) {
		if (name == null)
			return null;
		if (value == null) {
			if (this.properties == null)
				return null; // nothing to remove
			String oldValue = this.properties.getProperty(name);
			this.properties.remove(name);
			return oldValue;
		}
		else {
			if (this.properties == null)
				this.properties = new Properties();
			String oldValue = this.properties.getProperty(name);
			this.properties.setProperty(name, value);
			return oldValue;
		}
	}
	private Properties properties = null;
	
	/**
	 * Test if this document data object is suitable for instantiating an Image
	 * Markup document. This method should return true in most implementations,
	 * but there might be cases of document data objects only suited for
	 * storing Image Markup documents, e.g. instances storing data to a wrapped
	 * output stream.
	 * @return true if this document data object is suitable for instantiating
	 *            an Image Markup document
	 */
	public abstract boolean canLoadDocument();
	
	/**
	 * Test if this document data object is suitable for persisting an Image
	 * Markup document. This method should return true in most implementations,
	 * but there might be cases of document data objects only suited for 
	 * loading Image Markup documents, e.g. instances wrapping data read from
	 * a stream.
	 * @return true if this document data object is suitable for persisting an
	 *            Image Markup document
	 */
	public abstract boolean canStoreDocument();
	
	/**
	 * Test if this document data object can reuse previously persisted entry
	 * data, e.g. files stored in a folder that is reused across multiple
	 * storage events of the same Image Markup document.
	 * @return true if this document data object can reuse previously persisted
	 *            entry data
	 */
	public abstract boolean canReuseEntryData();
	
	/**
	 * Dispose of the document data object. This method clears all internal
	 * data structures, so the document data object is no longer usable after
	 * an invocation of this method.
	 */
	public void dispose() {
		if (this.annotations != null)
			this.annotations.clear();
		if (this.attributes != null)
			this.attributes.clearAttributes();
		if (this.supplementsById != null)
			this.supplementsById.clear();
		this.entriesByName.clear();
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#finalize()
	 */
	protected void finalize() throws Throwable {
		this.dispose();
	}
	
	/**
	 * Populate the list of entries from the data provided by a reader. Unless
	 * done before, this method also sets the storage flags of the document
	 * data object.
	 * @param entryIn the reader to read from
	 * @throws IOException
	 */
	protected void readEntryList(BufferedReader entryIn) throws IOException {
		long storageFlags = ImDocumentIO.STORAGE_MODE_CSV; // default to CSV mode, as TSV mode entry list always specifies flags explicitly
		for (String entryLine; (entryLine = entryIn.readLine()) != null;) {
//			System.out.println("Got entry list row: " + entryLine);
			if (entryLine.startsWith("@")) {
				String[] propData = entryLine.split("\\t");
//				System.out.println(" - property data split: " + Arrays.toString(propData));
				if (propData.length < 2) {
//					System.out.println(" ==> value missing");
					continue;
				}
				if ("@flags".equals(propData[0])) {
					storageFlags = Long.parseLong(propData[1], 16); // use whatever flag combination we found
//					System.out.println(" ==> storage flags set to " + storageFlags);
				}
				else {
					StringBuffer propValue = new StringBuffer();
					boolean escaped = false;
					for (int c = 0; c < propData[1].length(); c++) {
						char ch = propData[1].charAt(c);
						if (escaped) {
							if (ch == 'n')
								propValue.append('\n');
							else if (ch == 'r')
								propValue.append('\r');
							else if (ch == 't')
								propValue.append('\t');
							else propValue.append(ch);
							escaped = false;
						}
						else if (ch == '\\')
							escaped = true;
						else propValue.append(ch);
					}
					this.setProperty(propData[0].substring("@".length()), propValue.toString());
//					System.out.println(" ==> property '" + propData[0].substring("@".length()) + "' set to '" + propValue.toString() + "'");
				}
			}
			else {
//				System.out.println(" ==> regular entry");
				ImDocumentEntry entry = ImDocumentEntry.fromTabString(entryLine);
				if (entry != null)
					this.putEntry(entry);
			}
		}
		if (this.storageFlags < 0)
			this.storageFlags = storageFlags;
	}
	
	/**
	 * Write the (current) list of entries to a writer. The exact format of the
	 * produced output depends upon the storage flags of the document data
	 * object.
	 * @param entryOut the writer to write to
	 * @throws IOException
	 */
	public void writeEntryList(BufferedWriter entryOut) throws IOException {
		this.writeEntryList(entryOut, true);
	}
	
	/**
	 * Write the (current) list of entries to a writer. The exact format of the
	 * produced output depends upon the storage flags of the document data
	 * object, optionally including any generic properties of the document data
	 * object proper. The latter only works in TSV mode, however; in CSV mode,
	 * client code has to persist any properties in a custom way.
	 * @param entryOut the writer to write to
	 * @param includeProperties also write any properties of the document data
	 *            object proper?
	 * @throws IOException
	 */
	public void writeEntryList(BufferedWriter entryOut, boolean includeProperties) throws IOException {
		ImDocumentEntry[] entries = this.getEntries();
		Arrays.sort(entries);
		boolean tsvMode = (0 < this.storageFlags);
		if (tsvMode) {
			if (includeProperties)
				this.writeProperties(entryOut);
			entryOut.write("@flags");
			entryOut.write("\t");
			entryOut.write(Long.toString(this.storageFlags, 16).toUpperCase());
			entryOut.write("\r\n"); // fixed to cross-platform line break
		}
		for (int e = 0; e < entries.length; e++) {
			entryOut.write(entries[e].toTabString(tsvMode));
			entryOut.write("\r\n"); // fixed to cross-platform line break
		}
		entryOut.flush();
	}
	private void writeProperties(BufferedWriter propOut) throws IOException {
		String[] propNames = this.getPropertyNames();
		for (int p = 0; p < propNames.length; p++) {
			String propValue = this.getProperty(propNames[p]);
			propOut.write("@" + propNames[p]);
			propOut.write("\t");
			for (int c = 0; c < propValue.length(); c++) {
				char ch = propValue.charAt(c);
				if (ch == '\\')
					propOut.write("\\\\");
				else if (ch == '\n')
					propOut.write("\\n");
				else if (ch == '\r')
					propOut.write("\\r");
				else if (ch == '\t')
					propOut.write("\\t");
				else if (ch < ' ')
					propOut.write(" ");
				else propOut.write(ch);
			}
			propOut.write("\r\n"); // fixed to cross-platform line break
		}
	}
	
	/*
General and specific thoughts on IM document data:
- exists in two basic modes ...
- ... (1) loading mode (ZIP-in, IMS client cache, folder, as well as derived QC tool IME client cache, GGI desktop batch cache, and IMS): ...
  - requires full entry list
  - does _not_ require storage flags
  - requires any properties (IMFv2 only)
    ==> still have to load entry list even with ZIP stored IMF
- ... and (2) storing mode (ZIP-out, IMS client cache, folder, as well as derived QC tool IME client cache, GGI desktop batch cache, and IMS):
  - has two cases ...
  - ... (a) storing to empty document data (all storing mode document data objects): ...
    - requires storage flags ...
    - ... but has freedom to adjust and default them
  - ... and (b) storing to previously populated document data (IMS client cache, folder, as well as derived QC tool IME client cache, GGI desktop batch cache, and IMS):
    - requires storage flags that produced existing entry files ...
    - ... as well as full entry list ...
    - ... to recover word chunk boundaries as well as separate-file annotation and region types
      ==> implement said recovery mechanisms in IM document IO in first place !!!
- make sure to retain existing constructor signatures in folder document data, with same semantics:
  - for loading (mode 1): file (folder) object and entry array (even if null):
    - load entry list from file either way ...
    - ... as we need those flags anyway when storing back to same folder ...
    - ... and also need those properties (IMFv2 only) ...
    - ... but only ever throw file not found exception on entry list file if entry array null ...
    - ... as client code might well intend to set those flags downstream
    ==> HOWEVER, leave storage flags on -1 to enable subclass constructors and other client code to set them if entry list file not found
  - for storing (mode 2): file (folder) object only:
    - load any existing entry list file and also set flags from there ...
    - ... and default storage flags to CSV mode for now if entry list file not found
  ==> still deprecate both constructors
- add whole new constructors to folder document data for IMFv2 aware client code:
  - for loading (mode 1): file (folder) object and entry list file base name:
    - default latter to 'entries' if null ...
    - ... and use for injecting 'entries.<version>' in IMS document data ...
      ==> works with both IMFv1 and IMFv2
    - ... and also add 'subclass loads entry list' signaling constant ...
    - ... and use that where needed (e.g. in IMS with provenance attributes atop IMFv1 entry list)
      ==> empty string might well take that role
    - load entry list and set storage flags unless explicitly told not to do so
  - for storing (mode 2): file (folder) object and storage flags:
    - load any existing entry list (always need current version) ...
    - ... and set storage flags accordingly
    - default to CSV mode if entry list not found and argument flags undefined (less than zero)
    - use this to inject IMS configured default for new documents
      ==> definitely need to create HEX computation UI for storage flags ...
      ==> ... most likely to use same configuration widget as for local IMFv2 saving in GGI
        ==> build said widget in first place ...
        ==> ... and most likely simply add HEX flags preview (possibly restricted to master mode)
        ==> IN FACT, could well put respective panel into IM document IO proper
      ==> set and remove said panel as accessory for storing to file in GGI ...
      ==> ... and have 'use IMFv2' checkbox atop of it all to enable/disable all other options (for both ZIP and folder output) ...
      ==> ... and most likely also add read-only monospaced font text field showing HeX flags at very bottom (just to enable copy&paste)
      ==> also, add reading methods for individual storage parameters to IMF document IO (right next to flag vector assembly methods) ...
      ==> ... and use those for initializing preferred local IMF storage mode on GGI startup ...
      ==> ... after storing individual parameters on GGI shutdown in first place
        ==> make sure to put those individual parameters into display properties after each local IMF saving ...
        ==> ... and initialize to same display properties on startup ...
        ==> ... making damn sure to default to IMFv1 if nothing found
          ==> IN FACT, better store flags as HEX display property (using same value as for IMF entry list) ...
          ==> ... and enable storage option panel to initialize from that very HEX string
	 */
	
	/**
	 * Check whether or not an entry name is valid, i.e., whitespace free and
	 * only consisting of characters legal in file names across all major
	 * platforms. In case of a violation, this method throws an
	 * <code>IllegalArgumentException</code>.
	 * @param entryName the entry name to check
	 */
	public static void checkEntryName(String entryName) {
		if (!entryName.matches("[a-zA-Z0-9][a-zA-Z0-9\\-\\_\\=\\+\\#\\~\\;\\'\\.\\,\\@\\[\\]\\(\\)\\{\\}\\$\\§\\&\\%\\!]*"))
			throw new IllegalArgumentException("Invalid document entry name '" + entryName + "'");
	}
	
	/**
	 * A document data object backed by a folder that contains the data of the
	 * document entries.
	 * 
	 * @author sautter
	 */
	public static class FolderImDocumentData extends ImDocumentData {
		private File entryDataFolder;
//		final boolean tsvMode;
		
		/**
		 * Constructor creating a document data object from a newly created
		 * folder. Its intended use is for storing Image Markup documents to a
		 * new location, populating the entry list as data is stored.
		 * @param entryDataFolder the folder containing the entry data
		 * @deprecated use signature that also takes storage flags
		 */
		public FolderImDocumentData(File entryDataFolder) throws IOException {
//			this(entryDataFolder, ((ImDocumentIO.defaultStorageModeParams & ImDocumentIO.STORAGE_MODE_TSV) != 0));
//			this(entryDataFolder, ImDocumentIO.defaultStorageModeParams);
			this(entryDataFolder, -1 /* need to indicate 'yet to be defined' so IO facilities can inject flags if we don't have an entry list */);
		}
		
		/**
		 * Constructor creating a document data object from a newly created
		 * folder. Its intended use is for storing Image Markup documents to a
		 * new location, populating the entry list as data is stored.
		 * @param storageFlags the storage flags to use
		 * @param entryDataFolder the folder containing the entry data
		 */
		public FolderImDocumentData(File entryDataFolder, long storageFlags) throws IOException {
			this.entryDataFolder = entryDataFolder;
			
			//	find most recent entry list
			File tsvEntryListFile = new File(this.entryDataFolder, "entries.tsv");
			File csvEntryListFile = new File(this.entryDataFolder, "entries.txt");
			File entryListFile;
			if (tsvEntryListFile.exists() && tsvEntryListFile.isFile() && csvEntryListFile.exists() && csvEntryListFile.isFile()) {
				if (tsvEntryListFile.lastModified() < csvEntryListFile.lastModified())
					entryListFile = csvEntryListFile; // V1 more recently stored, use it
				else if (csvEntryListFile.lastModified() < tsvEntryListFile.lastModified())
					entryListFile = tsvEntryListFile; // V2 more recently stored, use it
				else if (storageFlags < 0)
					entryListFile = csvEntryListFile; // call from backwards compatibility constructor, use V1
				else if ((storageFlags & ImDocumentIO.STORAGE_MODE_TSV) == 0)
					entryListFile = csvEntryListFile; // flags indicate V1, use it
				else entryListFile = tsvEntryListFile; // flags indicate V2, use it
			}
			else if (tsvEntryListFile.exists() && tsvEntryListFile.isFile())
				entryListFile = tsvEntryListFile;
			else if (csvEntryListFile.exists() && csvEntryListFile.isFile())
				entryListFile = csvEntryListFile;
			else entryListFile = null;
			
			//	read whatever entry list we found
			if (entryListFile == null)
				this.setStorageFlags((storageFlags < 0) ? ImDocumentIO.STORAGE_MODE_CSV /* call from backwards compatibility constructor, use CSV */ : storageFlags);
			else /* this reads entry lists for both modes, and also sets storage flags, and also loads properties in TSV mode */ {
				BufferedReader entryIn = new BufferedReader(new InputStreamReader(new FileInputStream(entryListFile), "UTF-8"));
				this.readEntryList(entryIn);
				entryIn.close();
			}
		}
		
		/**
		 * Constructor creating a document data object from a folder that is
		 * already filled. If the argument entry list is null, this constructor
		 * attempts to load it from a file named 'entries.txt'.
		 * @param entryDataFolder the folder containing the entry data
		 * @param entries the list of entries
		 * @deprecated TODO add signature also taking any storage flags (we need that, e.g. to support versioning in IMS)
		 */
		public FolderImDocumentData(File entryDataFolder, ImDocumentEntry[] entries) throws IOException {
			this.entryDataFolder = entryDataFolder;
			
			//	find most recent entry list
			File tsvEntryListFile = new File(this.entryDataFolder, "entries.tsv");
			File csvEntryListFile = new File(this.entryDataFolder, "entries.txt");
			File entryListFile;
			if (tsvEntryListFile.exists() && tsvEntryListFile.isFile() && csvEntryListFile.exists() && csvEntryListFile.isFile()) {
				if (tsvEntryListFile.lastModified() < csvEntryListFile.lastModified())
					entryListFile = csvEntryListFile; // V1 more recently stored, use it
				else if (csvEntryListFile.lastModified() < tsvEntryListFile.lastModified())
					entryListFile = tsvEntryListFile; // V2 more recently stored, use it
				else entryListFile = csvEntryListFile; // default to CSV mode, as TSV mode aware code shouldn't even call this constructor
			}
			else if (tsvEntryListFile.exists() && tsvEntryListFile.isFile())
				entryListFile = tsvEntryListFile;
			else if (csvEntryListFile.exists() && csvEntryListFile.isFile())
				entryListFile = csvEntryListFile;
			else entryListFile = null;
			
			//	load entry list if not given
			if (entries == null) {
				if (entryListFile == null)
					throw new FileNotFoundException("Entry list not found: 'entries.tsv' or 'entries.txt'");
				BufferedReader entryIn = new BufferedReader(new InputStreamReader(new FileInputStream(entryListFile), "UTF-8"));
				this.readEntryList(entryIn);
				entryIn.close();
			}
			
			//	load any given entry list anyway, and store argument entries as they are
			else {
				if (entryListFile == null)
					this.setStorageFlags(ImDocumentIO.STORAGE_MODE_CSV); // default to CSV mode TODO and make damn sure TSV mode aware client code doesn't call this constructor !!!
				else /* this set storage flags matching existing entry list */ {
					BufferedReader entryIn = new BufferedReader(new InputStreamReader(new FileInputStream(entryListFile), "UTF-8"));
					this.readEntryList(entryIn);
					entryIn.close();
				}
				for (int e = 0; e < entries.length; e++)
					this.putEntry(entries[e]);
			}
		}
		
		/**
		 * Constructor creating a document data object from a folder that is
		 * already filled. If the argument entry list file name prefix is null,
		 * it defaults to 'entries'. This constructor attempts to load the
		 * entry list from both 'entries.txt' and  'entries.tsv', whichever is
		 * more recent. If the argument entry list file name prefix is the
		 * empty string, this constructor does not attempt to load any entry
		 * list, leaving that part to the calling client code. The latter is
		 * mainly meant for subclass constructors and should be used with care.
		 * @param entryDataFolder the folder containing the entry data
		 * @param entryListFileNamePrefix the prefix of the entry list file
		 *        name to load, basically the file name less the file extension
		 */
		public FolderImDocumentData(File entryDataFolder, String entryListFileNamePrefix) throws IOException {
			this.entryDataFolder = entryDataFolder;
			
			//	check entry list file name
			if (entryListFileNamePrefix == null)
				entryListFileNamePrefix = "entries";
			else {
				entryListFileNamePrefix = entryListFileNamePrefix.trim();
				if (entryListFileNamePrefix.length() == 0)
					return; // we'll get populated by subclass
			}
			System.out.println("Entry list file name starts with '" + entryListFileNamePrefix + "'");
			
			//	find most recent entry list
			File tsvEntryListFile = new File(this.entryDataFolder, (entryListFileNamePrefix + ".tsv"));
			File csvEntryListFile = new File(this.entryDataFolder, (entryListFileNamePrefix + ".txt"));
			File entryListFile;
			if (tsvEntryListFile.exists() && tsvEntryListFile.isFile() && csvEntryListFile.exists() && csvEntryListFile.isFile()) {
				if (tsvEntryListFile.lastModified() < csvEntryListFile.lastModified()) {
					entryListFile = csvEntryListFile; // V1 more recently stored, use it
					System.out.println(" - preferring more recent CSV entry list");
				}
				else if (csvEntryListFile.lastModified() < tsvEntryListFile.lastModified()) {
					entryListFile = tsvEntryListFile; // V2 more recently stored, use it
					System.out.println(" - preferring more recent TSV entry list");
				}
				else {
					entryListFile = csvEntryListFile; // default to CSV mode, as TSV mode aware code shouldn't even call this constructor
					System.out.println(" - defaulting to CSV entry list");
				}
			}
			else if (tsvEntryListFile.exists() && tsvEntryListFile.isFile()) {
				entryListFile = tsvEntryListFile;
				System.out.println(" - found TSV entry list");
			}
			else if (csvEntryListFile.exists() && csvEntryListFile.isFile()) {
				entryListFile = csvEntryListFile;
				System.out.println(" - found CSV entry list");
			}
			else throw new FileNotFoundException("Entry list not found: '" + entryListFileNamePrefix + ".tsv' or '" + entryListFileNamePrefix + ".txt'");
			
			//	load entry lists, also setting storage flags, and also loading properties in TSV mode
			BufferedReader entryIn = new BufferedReader(new InputStreamReader(new FileInputStream(entryListFile), "UTF-8"));
			this.readEntryList(entryIn);
			entryIn.close();
			System.out.println(" ==> entry list read, got flags 0x" + Long.toString(this.getStorageFlags(), 16).toUpperCase() + " (" + this.getStorageFlags() + ")");
		}
		
		/**
		 * Write the (current) list of entries to a file named 'entries.tsv'
		 * (or 'entries.txt' in CSV mode), located in the underlying folder.
		 * If a file with that name already exists, it is renamed by appending
		 * a timestamp and '.old' to its name.
		 * @throws IOException
		 */
		public void storeEntryList() throws IOException {
			boolean tsvMode = (0 < this.getStorageFlags());
			String entryFileName = (tsvMode ? "entries.tsv" : "entries.txt");
			File newEntryFile = new File(this.entryDataFolder, (entryFileName + ".new"));
			BufferedWriter entryOut = new BufferedWriter(new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(newEntryFile)), "UTF-8"));
			this.writeEntryList(entryOut);
			entryOut.flush();
			entryOut.close();
			File exEntryFile = new File(this.entryDataFolder, entryFileName);
			if (exEntryFile.exists())
				exEntryFile.renameTo(new File(this.entryDataFolder, (entryFileName + "." + System.currentTimeMillis() + ".old")));
			newEntryFile.renameTo(new File(this.entryDataFolder, entryFileName));
		}
		
		public boolean canLoadDocument() {
			return this.hasEntry((this.getStorageFlags() <= 0) ? "document.csv" : "document.tsv");
		}
		public boolean canStoreDocument() {
			return true;
		}
		public boolean canReuseEntryData() {
			return true;
		}
		public String getDocumentDataId() {
			return this.entryDataFolder.getAbsolutePath();
		}
		public boolean hasEntryData(ImDocumentEntry entry) {
			File entryDataFile = new File(this.entryDataFolder, entry.fileName);
			return entryDataFile.exists();
		}
		public InputStream getInputStream(String entryName) throws IOException {
			ImDocumentEntry entry = this.getEntry(entryName);
			if (entry == null)
				throw new FileNotFoundException(entryName);
			File entryDataFile = new File(this.entryDataFolder, entry.fileName);
			return new BufferedInputStream(new FileInputStream(entryDataFile));
		}
		public OutputStream getOutputStream(final String entryName, boolean writeDirectly) throws IOException {
			
			//	check file name
			checkEntryName(entryName);
			
			//	split file name from file extension so data hash can be inserted in between
			final String entryDataFileName;
			final String entryDataFileExtension;
			if (entryName.indexOf('.') == -1) {
				entryDataFileName = entryName;
				entryDataFileExtension = "";
			}
			else {
				entryDataFileName = entryName.substring(0, entryName.lastIndexOf('.'));
				entryDataFileExtension = entryName.substring(entryName.lastIndexOf('.'));
			}
			
			//	write directly to file (flag defaults to no entry present)
			if (writeDirectly || !this.hasEntry(entryName)) {
				final File entryDataFileWriting = new File(this.entryDataFolder, (entryDataFileName + ".writing" + entryDataFileExtension));
				return new DataHashOutputStream(new BufferedOutputStream(new FileOutputStream(entryDataFileWriting))) {
					public void close() throws IOException {
						super.flush();
						super.close();
						
						//	prepare hash
						String entryDataHash = this.getDataHash();
						if (0 < getStorageFlags())
							entryDataHash = ImDocumentIO.abridgeEntryDataHash(entryDataHash);
						
						//	rename file only now that we have the hash
						File entryDataFile = new File(entryDataFolder, (entryDataFileName + "." + entryDataHash + entryDataFileExtension));
						if (entryDataFile.exists())
							entryDataFileWriting.delete();
						else entryDataFileWriting.renameTo(entryDataFile);
						
						//	update entry list
						putEntry(new ImDocumentEntry(entryName, ((int) entryDataFile.length()), entryDataFile.lastModified(), entryDataHash));
					}
				};
			}
			
			//	entry already exists, and we're allowed to buffer
			else return new DataHashOutputStream(new ByteArrayOutputStream()) {
				public void close() throws IOException {
					super.flush();
					super.close();
					
					//	prepare hash
					String entryDataHash = this.getDataHash();
					if (0 < getStorageFlags())
						entryDataHash = ImDocumentIO.abridgeEntryDataHash(entryDataHash);
					
					//	write buffer content to persistent storage only if not already there
					File entryDataFile = new File(entryDataFolder, (entryDataFileName + "." + entryDataHash + entryDataFileExtension));
					if (!entryDataFile.exists()) {
						BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(entryDataFile));
						((ByteArrayOutputStream) this.out).writeTo(out);
						out.flush();
						out.close();
					}
					
					//	update entry list
					putEntry(new ImDocumentEntry(entryName, ((int) entryDataFile.length()), entryDataFile.lastModified(), entryDataHash));
				}
			};
		}
	}
}
//public abstract class ImDocumentData {
//	
//	/**
//	 * An Image Markup document backed by an instance of Image Markup document
//	 * data.
//	 * 
//	 * @author sautter
//	 */
//	public static abstract class DataBackedImDocument extends ImDocument {
//		
//		/** Constructor
//		 * @param docId
//		 * @param orientation
//		 */
//		protected DataBackedImDocument(String docId, ComponentOrientation orientation) {
//			super(docId, orientation);
//		}
//		
//		/** Constructor
//		 * @param docId
//		 */
//		protected DataBackedImDocument(String docId) {
//			super(docId);
//		}
//		
//		/**
//		 * Retrieve the Image Markup document data backing the document.
//		 * @return the backing Image Markup document data
//		 */
//		public abstract ImDocumentData getDocumentData();
//		
//		/**
//		 * Dispose both the document proper as well as its backing document
//		 * data object.
//		 * @see de.uka.ipd.idaho.im.ImDocument#dispose()
//		 * @see de.uka.ipd.idaho.im.util.ImDocumentData#dispose()
//		 */
//		public void dispose() {
//			super.dispose();
//			ImDocumentData data = this.getDocumentData();
//			if (data != null)
//				data.dispose();
//		}
//	}
//	
//	/**
//	 * Metadata of a single entry in an Image Markup document.
//	 * 
//	 * @author sautter
//	 */
//	public static class ImDocumentEntry implements Comparable {
//		
//		/** the name of the entry, e.g. 'document.csv' */
//		public final String name;
//		
//		/** the time the entry was last modified */
//		public final long updateTime;
//		
//		/** the MD5 hash of the entry data */
//		public final String dataHash;
//		
//		private final String fileName;
//		
//		/**
//		 * @param name
//		 * @param updateTime
//		 * @param dataHash
//		 */
//		public ImDocumentEntry(String name, long updateTime, String dataHash) {
//			this.name = name;
//			this.updateTime = updateTime;
//			this.dataHash = dataHash;
//			if (name.lastIndexOf('.') == -1)
//				this.fileName = (this.name + "." + this.dataHash);
//			else this.fileName = (this.name.substring(0, this.name.lastIndexOf('.')) + "." + this.dataHash + this.name.substring(this.name.lastIndexOf('.')));
//		}
//		
//		/**
//		 * @param file
//		 */
//		public ImDocumentEntry(File file) {
//			this(file.getName(), file.lastModified());
//		}
//		
//		/**
//		 * @param varName
//		 * @param updateTime
//		 * @param dataHash
//		 */
//		public ImDocumentEntry(ZipEntry ze) {
//			this(ze.getName(), ze.getTime());
//		}
//		
//		private ImDocumentEntry(String fileName, long updateTime) {
//			String[] fileNameParts = fileName.split("\\.");
//			if (fileNameParts.length < 2)
//				throw new IllegalArgumentException("Illegal name+hash string '" + fileName + "'");
//			if (updateTime < 1)
//				throw new IllegalArgumentException("Illegal update time " + updateTime + " in IMF Entry '" + fileName + "'");
//			this.updateTime = updateTime;
//			if (fileNameParts.length == 2) {
//				this.name = fileNameParts[0];
//				this.dataHash = fileNameParts[1];
//			}
//			else {
//				StringBuffer name = new StringBuffer();
//				for (int p = 0; p < fileNameParts.length; p++)
//					if (p != (fileNameParts.length - 2)) {
//						if (p != 0)
//							name.append('.');
//						name.append(fileNameParts[p]);
//					}
//				this.name = name.toString();
//				this.dataHash = fileNameParts[fileNameParts.length - 2];
//			}
//			this.fileName = fileName;
//		}
//		
//		/**
//		 * Create the name of a file to store this IMF Entry on persistent
//		 * storage. The returned file name takes the form '&lt;nameLessFileExtension&gt;.
//		 * &lt;dataHash&gt;.&lt;fileExtension&gt;'.
//		 * @return the file name for the IMF Entry
//		 */
//		public String getFileName() {
//			return this.fileName;
//		}
//		
//		/**
//		 * Convert the IMF Entry into a tab separated string for listing. The
//		 * returned string has the form '&lt;name&gt; &lt;updateTime&gt; 
//		 * &lt;dataHash&gt;'.
//		 * @return a tab separated string representation of the IMF Entry
//		 */
//		public String toTabString() {
//			return (this.name + "\t" + this.updateTime + "\t" + this.dataHash);
//		}
//		
//		/**
//		 * Compares this IMF entry to another one based on the names, sorting
//		 * in case sensitive lexicographical order.
//		 * @param obj the IMF entry to compare this one to
//		 * @return the comparison result
//		 * @see java.lang.Comparable#compareTo(java.lang.Object)
//		 */
//		public int compareTo(Object obj) {
//			return ((obj instanceof ImDocumentEntry) ? this.name.compareTo(((ImDocumentEntry) obj).name) : -1);
//		}
//		
//		/**
//		 * Parse an IMF entry from its tab separated string representation, as
//		 * returned by the <code>toTabString()</code> method.
//		 * @param tabStr the tab separated string representation to parse
//		 * @return the IMF entry parsed from the argument string
//		 */
//		public static ImDocumentEntry fromTabString(String tabStr) {
//			String[] imfEntryData = tabStr.split("\\t");
//			if (imfEntryData.length == 3) try {
//				return new ImDocumentEntry(imfEntryData[0], Long.parseLong(imfEntryData[1]), imfEntryData[2]);
//			} catch (NumberFormatException nfe) {}
//			return null;
//		}
//	}
//	
//	private String documentId = null;
//	private HashMap entriesByName = new LinkedHashMap();
//	
//	/** Constructor
//	 * @param documentId the ID of the Image Markup document represented by this data object
//	 */
//	protected ImDocumentData() {}
//	
//	/**
//	 * Retrieve the current entries of the document.
//	 * @return an array holding the entries
//	 */
//	public ImDocumentEntry[] getEntries() {
//		return ((ImDocumentEntry[]) this.entriesByName.values().toArray(new ImDocumentEntry[this.entriesByName.size()]));
//	}
//	
//	/**
//	 * Test if this document data object has an entry with a specific name.
//	 * @param entryName the name of the entry to test
//	 * @return true if the argument entry if included in this document data
//	 */
//	public boolean hasEntry(String entryName) {
//		return this.entriesByName.containsKey(entryName);
//	}
//	
//	/**
//	 * Test if this document data object has a specific entry.
//	 * @param entry the entry to test
//	 * @return true if the argument entry if included in this document data
//	 */
//	public boolean hasEntry(ImDocumentEntry entry) {
//		return this.hasEntry(entry.name);
//	}
//	
//	/**
//	 * Test if this document data object has an entry with a specific name as
//	 * well as the data it points to.
//	 * @param entryName the name of the entry to test
//	 * @return true if this document data has the data for the argument entry
//	 */
//	public boolean hasEntryData(String entryName) {
//		ImDocumentEntry entry = this.getEntry(entryName);
//		return ((entry != null) && this.hasEntryData(entry));
//	}
//	
//	/**
//	 * Test if this document data object has the data for a specific entry.
//	 * Implementations should check for is data is available for the file name
//	 * (i.e., entry name and data hash) of the argument entry. The argument
//	 * entry does not necessarily have to be part of the current entry list
//	 * returned by <code>getEntries()</code>.
//	 * @param entry the entry to test
//	 * @return true if this document data has the data for the argument entry
//	 */
//	public abstract boolean hasEntryData(ImDocumentEntry entry);
//	
//	/**
//	 * Test if this document data object has a specific entry. Implementations
//	 * should compare both the entry name and the data hash.
//	 * @param entry the entry to test
//	 * @return true if the argument entry if included in this document data
//	 */
//	public ImDocumentEntry getEntry(String entryName) {
//		return ((ImDocumentEntry) this.entriesByName.get(entryName));
//	}
//	
//	/**
//	 * Put an entry into the list of the document data object, potentially
//	 * replacing an existing one with the same name. This method is intended
//	 * for adding or replacing entries when an entry output stream returned by
//	 * either of the the <code>getOutputStream()</code> methods is closed. It
//	 * can also be used to update the entry list when reverting to an existing
//	 * file with a hash other than the current one.
//	 * @param entry the entry to put
//	 */
//	public ImDocumentEntry putEntry(ImDocumentEntry entry) {
//		return ((ImDocumentEntry) this.entriesByName.put(entry.name, entry));
//	}
//	
//	/**
//	 * Retrieve an input stream to read the data of a document entry from.
//	 * Implementations should go by the entry name only, returning an input
//	 * stream for the currently valid entry whose name matches the name of the
//	 * argument one.
//	 * @param entry the entry to get the input stream for
//	 * @return an input stream for the entry data
//	 * @throws IOException
//	 */
//	public InputStream getInputStream(ImDocumentEntry entry) throws IOException {
//		return this.getInputStream(entry.name);
//	}
//	
//	/**
//	 * Retrieve an input stream to read the data of a document entry from.
//	 * @param entryName the name of the entry to get the input stream for
//	 * @return an input stream for the entry data
//	 * @throws IOException
//	 */
//	public abstract InputStream getInputStream(String entryName) throws IOException;
//	
//	/**
//	 * Retrieve an output stream to write the data of a document entry to.
//	 * Implementations should use the entry name only, and compute the data
//	 * hash and update the entry list via <code>putEntry()</code> only when
//	 * the returned output stream is closed. However, the data has of the
//	 * argument entry may be used as a hint regarding the runtime behavior of
//	 * implementations.
//	 * @param entry the entry to obtain an output stream for
//	 * @return an output stream for the argument entry
//	 * @throws IOException
//	 */
//	public OutputStream getOutputStream(ImDocumentEntry entry) throws IOException {
//		return this.getOutputStream(entry, !this.hasEntry(entry));
//	}
//	
//	/**
//	 * Retrieve an output stream to write the data of a document entry to.
//	 * Implementations should use the entry name only, and compute the data
//	 * hash and update the entry list via <code>putEntry()</code> only when
//	 * the returned output stream is closed. However, the data has of the
//	 * argument entry may be used as a hint regarding the runtime behavior of
//	 * implementations.
//	 * @param entry the entry to obtain an output stream for
//	 * @param writeDirectly write directly to the underlying source in all
//	 *            cases, avoiding intermediate buffering even at the risk of
//	 *            unnecessary writes to persistent storage?
//	 * @return an output stream for the argument entry
//	 * @throws IOException
//	 */
//	public OutputStream getOutputStream(ImDocumentEntry entry, boolean writeDirectly) throws IOException {
//		return this.getOutputStream(entry.name, writeDirectly);
//	}
//	
//	/**
//	 * Retrieve an output stream to write the data of a document entry to.
//	 * Implementations should compute the data hash and update the entry list
//	 * via <code>putEntry()</code> when the returned output stream is closed.
//	 * @param entryName the name of entry to obtain an output stream for
//	 * @return an output stream for the argument entry
//	 * @throws IOException
//	 */
//	public OutputStream getOutputStream(String entryName) throws IOException {
//		return this.getOutputStream(entryName, !this.hasEntry(entryName));
//	}
//	
//	/**
//	 * Retrieve an output stream to write the data of a document entry to.
//	 * Implementations should compute the data hash and update the entry list
//	 * via <code>putEntry()</code> when the returned output stream is closed.
//	 * @param entryName the name of entry to obtain an output stream for
//	 * @param writeDirectly write directly to the underlying source in all
//	 *            cases, avoiding intermediate buffering even at the risk of
//	 *            unnecessary writes to persistent storage?
//	 * @return an output stream for the argument entry
//	 * @throws IOException
//	 */
//	public abstract OutputStream getOutputStream(String entryName, boolean writeDirectly) throws IOException;
//	
//	/**
//	 * Instantiate an Image Markup document from the data.
//	 * @return the Image Markup document
//	 */
//	public DataBackedImDocument getDocument() throws IOException {
//		return this.getDocument(ProgressMonitor.dummy);
//	}
//	
//	/**
//	 * Instantiate an Image Markup document from the data.
//	 * @param pm a progress monitor to observe the loading process
//	 * @return the Image Markup document
//	 */
//	public DataBackedImDocument getDocument(ProgressMonitor pm) throws IOException {
//		return (this.canLoadDocument() ? ImDocumentIO.loadDocument(this, pm) : null);
//	}
//	
//	private Attributed attributes = null;
//	private boolean attributeLoadError = false;
//	private void ensureAttributesLoaded() {
//		if ((this.attributes == null) && !this.attributeLoadError) try {
//			this.attributes = ImDocumentIO.loadDocumentAttributes(this);
//		}
//		catch (IOException ioe) {
//			this.attributeLoadError = true;
//		}
//	}
//	
//	/**
//	 * Retrieve the attributes of the document. This method is a shorthand for
//	 * <code>ImDocumentIO.loadDocumentAttributes(this)</code>. The returned
//	 * object is immutable.
//	 * @return the document attributes
//	 */
//	public Attributed getDocumentAttributes() {
//		this.ensureAttributesLoaded();
//		return new Attributed() {
//			public void setAttribute(String name) { /* we're read-only for now */ }
//			public Object setAttribute(String name, Object value) {
//				return value; // we're read-only for now
//			}
//			public void copyAttributes(Attributed source) { /* we're read-only for now */ }
//			public Object getAttribute(String name) {
//				return this.getAttribute(name, null);
//			}
//			public Object getAttribute(String name, Object def) {
//				if (attributes == null)
//					return def;
//				else return attributes.getAttribute(name, def);
//			}
//			public boolean hasAttribute(String name) {
//				if (attributes == null)
//					return false;
//				else return attributes.hasAttribute(name);
//			}
//			public String[] getAttributeNames() {
//				if (attributes == null)
//					return new String[0];
//				else return attributes.getAttributeNames();
//			}
//			public Object removeAttribute(String name) {
//				return null; // we're read-only for now
//			}
//			public void clearAttributes() { /* we're read-only for now */ }
//		};
//	}
//	
//	private ArrayList annotations = null;
//	private boolean annotationLoadError = false;
//	private boolean ensureAnnotationsLoaded() {
//		if ((this.annotations == null) && !this.annotationLoadError) try {
//			InputStream annotsIn = this.getInputStream("annotations.csv");
//			StringRelation annotsData = StringRelation.readCsvData(new InputStreamReader(annotsIn, "UTF-8"), true, null);
//			annotsIn.close();
//			this.annotations = new ArrayList();
//			for (int s = 0; s < annotsData.size(); s++) {
//				StringTupel annotData = annotsData.get(s);
////				ImAnnotationStub annot = new ImAnnotationStub();
//				ImAnnotationStub annot = new ImAnnotationStub(this.documentId, annotData.getValue(ImAnnotation.FIRST_WORD_ATTRIBUTE), annotData.getValue(ImAnnotation.LAST_WORD_ATTRIBUTE));
//				ImDocumentIO.setAttributes(annot, annotData.getValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, ""));
////				annot.setFirstWordId(annotData.getValue(ImAnnotation.FIRST_WORD_ATTRIBUTE));
////				annot.setLastWordId(annotData.getValue(ImAnnotation.LAST_WORD_ATTRIBUTE));
//				annot.setType(annotData.getValue(ImAnnotation.TYPE_ATTRIBUTE)); // after all the other attributes, as this makes it read-only
//				this.annotations.add(annot);
//			}
//		}
//		catch (IOException ioe) {
//			this.annotationLoadError = true;
//		}
//		return (this.annotations != null);
//	}
//	
//	private static class ImAnnotationStub extends AbstractAttributed implements ImAnnotation {
//		private String docId;
//		private String firstWordId;
//		private String lastWordId;
//		private String type;
//		private String luid = null;
//		private String uuid = null;
//		ImAnnotationStub(String docId, String firstWordId, String lastWordId) {
//			this.docId = docId;
//			this.firstWordId = firstWordId;
//			this.lastWordId = lastWordId;
//		}
//		public String getType() {
//			return this.type;
//		}
//		public void setType(String type) {
//			if (this.type == null)
//				this.type = type; // we're read-only for now (first call seals the attributes)
//		}
////		void setFirstWordId(String firstWordId) {
////			this.firstWordId = firstWordId;
////		}
////		void setLastWordId(String lastWordId) {
////			this.lastWordId = lastWordId;
////		}
//		public String getLocalID() {
//			return (this.type + "@" + this.firstWordId + "-" + this.lastWordId);
//		}
//		public String getLocalUID() {
//			if (this.luid == null) {
//				int fwPageId = Integer.parseInt(this.firstWordId.substring(0, this.firstWordId.indexOf(".")));
//				BoundingBox fwBounds = BoundingBox.parse(this.firstWordId.substring(this.firstWordId.indexOf(".") + ".".length()));
//				int lwPageId = Integer.parseInt(this.lastWordId.substring(0, this.lastWordId.indexOf(".")));
//				BoundingBox lwBounds = BoundingBox.parse(this.lastWordId.substring(this.lastWordId.indexOf(".") + ".".length()));
//				this.luid = AnnotationUuidHelper.getLocalUID(this.type, fwPageId, lwPageId, fwBounds.left, fwBounds.top, lwBounds.right, lwBounds.bottom);
//			}
//			return this.luid;
//		}
//		public String getUUID() {
//			if (this.uuid == null)
//				this.uuid = UuidHelper.getUUID(this, this.docId);
//			return this.uuid;
//		}
//		public ImDocument getDocument() {
//			return null;
//		}
//		public String getDocumentProperty(String propertyName) {
//			return null;
//		}
//		public String getDocumentProperty(String propertyName, String defaultValue) {
//			return defaultValue;
//		}
//		public String[] getDocumentPropertyNames() {
//			return new String[0];
//		}
//		public Object setAttribute(String name, Object value) {
//			if (this.type == null)
//				return super.setAttribute(name, value);
//			else return value;
//		}
//		public void copyAttributes(Attributed source) { /* we're read-only for now */ }
//		public Object removeAttribute(String name) {
//			return null; // we're read-only for now
//		}
//		public void clearAttributes() { /* we're read-only for now */ }
//		
//		public ImWord getFirstWord() {
//			return null;
//		}
//		public void setFirstWord(ImWord firstWord) { /* we're read-only for now */ }
//		public ImWord getLastWord() {
//			return null;
//		}
//		public void setLastWord(ImWord lastWord) { /* we're read-only for now */ }
//	}
//	
//	/**
//	 * Retrieve the annotations of the document. The returned annotations are
//	 * immutable, and they are not bounds to an actual document. As a result,
//	 * the <code>getFirstWord()</code>, <code>getLastWord()</code>, and
//	 * <code>getDocument()</code> methods return null.
//	 * @return the document annotations
//	 */
//	public ImAnnotation[] getAnnotations() {
//		if (this.ensureAnnotationsLoaded())
//			return ((ImAnnotation[]) this.annotations.toArray(new ImAnnotation[this.annotations.size()]));
//		else return new ImAnnotation[0];
//	}
//	
//	private TreeMap supplementsById = null;
//	private boolean supplementLoadError = false;
//	private boolean ensureSupplementsLoaded() {
//		if ((this.supplementsById == null) && !this.supplementLoadError) try {
//			InputStream supplementsIn = this.getInputStream("supplements.csv");
//			StringRelation supplementsData = StringRelation.readCsvData(new InputStreamReader(supplementsIn, "UTF-8"), true, null);
//			supplementsIn.close();
//			this.supplementsById = new TreeMap();
//			for (int s = 0; s < supplementsData.size(); s++) {
//				StringTupel supplementData = supplementsData.get(s);
//				String sid = supplementData.getValue(ImSupplement.ID_ATTRIBUTE);
//				String st = supplementData.getValue(ImSupplement.TYPE_ATTRIBUTE);
//				String smt = supplementData.getValue(ImSupplement.MIME_TYPE_ATTRIBUTE);
//				ImSupplement supplement;
//				if (ImSupplement.SOURCE_TYPE.equals(st))
//					supplement = new ImSupplement.Source(null, smt) {
//						public InputStream getInputStream() throws IOException {
//							return ImDocumentData.this.getInputStream(this.getFileName());
//						}
//					};
//				else if (ImSupplement.SCAN_TYPE.equals(st))
//					supplement = new ImSupplement.Scan(null, sid, smt) {
//						public InputStream getInputStream() throws IOException {
//							return ImDocumentData.this.getInputStream(this.getFileName());
//						}
//					};
//				else if (ImSupplement.FIGURE_TYPE.equals(st))
//					supplement = new ImSupplement.Figure(null, sid, smt) {
//						public InputStream getInputStream() throws IOException {
//							return ImDocumentData.this.getInputStream(this.getFileName());
//						}
//					};
//				else if (ImSupplement.GRAPHICS_TYPE.equals(st))
//					supplement = new ImSupplement.Graphics(null, sid) {
//						public InputStream getInputStream() throws IOException {
//							return ImDocumentData.this.getInputStream(this.getFileName());
//						}
//					};
//				else supplement = new ImSupplement(null, sid, st, smt) {
//					public InputStream getInputStream() throws IOException {
//						return ImDocumentData.this.getInputStream(this.getFileName());
//					}
//				};
//				ImDocumentIO.setAttributes(supplement, supplementData.getValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, ""));
//				this.supplementsById.put(supplement.getId(), supplement);
//			}
//		}
//		catch (IOException ioe) {
//			this.supplementLoadError = true;
//		}
//		return (this.supplementsById != null);
//	}
//	
//	/**
//	 * Retrieve a document supplement by its ID.
//	 * @param sid the ID of the required supplement
//	 */
//	public ImSupplement getSupplement(String sid) {
//		if (this.ensureSupplementsLoaded())
//			return ((sid == null) ? null : ((ImSupplement) this.supplementsById.get(sid)));
//		else return null;
//	}
//	
//	/**
//	 * Retrieve the IDs of the document supplements.
//	 * @return an array holding the supplement IDs
//	 */
//	public String[] getSupplementIDs() {
//		if (this.ensureSupplementsLoaded())
//			return ((String[]) this.supplementsById.keySet().toArray(new String[this.supplementsById.size()]));
//		else return new String[0];
//	}
//	
//	/**
//	 * Retrieve the document supplements proper.
//	 * @return an array holding the supplements
//	 */
//	public ImSupplement[] getSupplements() {
//		if (this.ensureSupplementsLoaded())
//			return ((ImSupplement[]) this.supplementsById.values().toArray(new ImSupplement[this.supplementsById.size()]));
//		else return new ImSupplement[0];
//	}
//	
//	/**
//	 * Retrieve the identifier of the Image Markup document represented by the
//	 * underlying data. This default implementation reads the identifier from
//	 * the document attributes. Sub classes are welcome to overwrite it with a
//	 * more efficient implementation.
//	 * @return an identifier of the document
//	 */
//	public String getDocumentId() {
//		if (this.documentId == null) {
//			Attributed attributes = this.getDocumentAttributes();
//			this.documentId = ((String) attributes.getAttribute(ImDocumentIO.DOCUMENT_ID_ATTRIBUTE));
//		}
//		return this.documentId;
//	}
//	
//	/**
//	 * Retrieve an identifier for the underlying data source. This identifier
//	 * is not equal to the ID of the document represented by the underlying
//	 * data, even though it may include it. Rather, the identifier returned by
//	 * this method is intended to enable IO facilities to distinguish different
//	 * document data objects and adjust their behavior accordingly. Pure in-
//	 * memory or cache based implementations should return null to indicate
//	 * they are not persistent.
//	 * @return an identifier of the underlying data source
//	 */
//	public abstract String getDocumentDataId();
//	
//	/**
//	 * Test if this document data object is suitable for instantiating an Image
//	 * Markup document. This method should return true in most implementations,
//	 * but there might be cases of document data objects only suited for
//	 * storing Image Markup documents, e.g. instances storing data to a wrapped
//	 * output stream.
//	 * @return true if this document data object is suitable for instantiating
//	 *            an Image Markup document
//	 */
//	public abstract boolean canLoadDocument();
//	
//	/**
//	 * Test if this document data object is suitable for persisting an Image
//	 * Markup document. This method should return true in most implementations,
//	 * but there might be cases of document data objects only suited for 
//	 * loading Image Markup documents, e.g. instances wrapping data read from
//	 * a stream.
//	 * @return true if this document data object is suitable for persisting an
//	 *            Image Markup document
//	 */
//	public abstract boolean canStoreDocument();
//	
//	/**
//	 * Dispose of the document data object. This method clears all internal
//	 * data structures, so the document data object is no longer usable after
//	 * an invocation of this method.
//	 */
//	public void dispose() {
//		if (this.annotations != null)
//			this.annotations.clear();
//		if (this.attributes != null)
//			this.attributes.clearAttributes();
//		if (this.supplementsById != null)
//			this.supplementsById.clear();
//		this.entriesByName.clear();
//	}
//	
//	/* (non-Javadoc)
//	 * @see java.lang.Object#finalize()
//	 */
//	protected void finalize() throws Throwable {
//		this.dispose();
//	}
//	
//	/**
//	 * Check whether or not an entry name is valid, i.e., whitespace free and
//	 * only consisting of characters legal in file names across all major
//	 * platforms. In case of a violation, this method throws an
//	 * <code>IllegalArgumentException</code>.
//	 * @param entryName the entry name to check
//	 */
//	public static void checkEntryName(String entryName) {
//		if (!entryName.matches("[a-zA-Z0-9][a-zA-Z0-9\\-\\_\\=\\+\\#\\~\\;\\'\\.\\,\\@\\[\\]\\(\\)\\{\\}\\$\\§\\&\\%\\!]*"))
//			throw new IllegalArgumentException("Invalid document entry name '" + entryName + "'");
//	}
////	
////	/**
////	 * An output stream that updates an MD5 message digester as data is written
////	 * to it. The hash is finalized when the <code>close()</code> method is
////	 * called.
////	 * 
////	 * @author sautter
////	 */
////	public static class DataHashOutputStream extends FilterOutputStream {
////		private MD5 dataHasher = new MD5();
////		private String dataHash = null;
////		
////		/** Constructor
////		 * @param out the output stream to wrap
////		 */
////		public DataHashOutputStream(OutputStream out) {
////			super(out);
////		}
////		
////		/* (non-Javadoc)
////		 * @see java.io.FilterOutputStream#write(int)
////		 */
////		public synchronized void write(int b) throws IOException {
////			this.out.write(b);
////			this.dataHasher.update((byte) b);
////		}
////		
////		/* (non-Javadoc)
////		 * @see java.io.FilterOutputStream#write(byte[])
////		 */
////		public void write(byte[] b) throws IOException {
////			this.write(b, 0, b.length);
////		}
////		
////		/* (non-Javadoc)
////		 * @see java.io.FilterOutputStream#write(byte[], int, int)
////		 */
////		public synchronized void write(byte[] b, int off, int len) throws IOException {
////			this.out.write(b, off, len);
////			this.dataHasher.update(b, off, len);
////		}
////		
////		/** 
////		 * This implementation first closes the wrapped input stream and then
////		 * finalizes the MD5 hash of the data.
////		 * @see java.io.FilterOutputStream#close()
////		 */
////		public void close() throws IOException {
////			super.close();
////			
////			//	we have been closed before
////			if (this.dataHasher == null)
////				return;
////			
////			//	finalize hash and rename file
////			this.dataHash = this.dataHasher.digestHex();
////			
////			//	return digester to instance pool
////			this.dataHasher = null;
////		}
////		
////		/**
////		 * Retrieve the data hash, i.e., the MD5 of all the bytes written to
////		 * the stream. Before the <code>close()</code> method is called. this
////		 * method returns null.
////		 * @return the MD5 hash of the data written to the stream
////		 */
////		public String getDataHash() {
////			return this.dataHash;
////		}
////	}
//	
//	/**
//	 * A document data object backed by a folder that contains the data of the
//	 * document entries.
//	 * 
//	 * @author sautter
//	 */
//	public static class FolderImDocumentData extends ImDocumentData {
//		private File entryDataFolder;
//		
//		/**
//		 * Constructor creating a document data object from a newly created
//		 * folder. Its intended use is for storing Image Markup documents to a
//		 * new location, populating the entry list as data is stored.
//		 * @param entryDataFolder the folder containing the entry data
//		 */
//		public FolderImDocumentData(File entryDataFolder) throws IOException {
//			this.entryDataFolder = entryDataFolder;
//			this.temp();
//		}
//		
//		/**
//		 * Constructor creating a document data object from a folder that is
//		 * already filled. If the argument entry list is null, this constructor
//		 * attempts to load it from a file named 'entries.txt'.
//		 * @param entryDataFolder the folder containing the entry data
//		 * @param entries the list of entries
//		 * @throws IOException
//		 */
//		public FolderImDocumentData(File entryDataFolder, ImDocumentEntry[] entries) throws IOException {
//			this.entryDataFolder = entryDataFolder;
//			this.temp();
//			
//			//	load entry list if not given
//			if (entries == null) {
//				BufferedReader entryIn = new BufferedReader(new InputStreamReader(new FileInputStream(new File(this.entryDataFolder, "entries.txt")), "UTF-8"));
//				for (String imfEntryLine; (imfEntryLine = entryIn.readLine()) != null;) {
//					ImDocumentEntry entry = ImDocumentEntry.fromTabString(imfEntryLine);
//					if (entry != null)
//						this.putEntry(entry);
//				}
//				entryIn.close();
//			}
//			
//			//	store entry list as is
//			else for (int e = 0; e < entries.length; e++)
//				this.putEntry(entries[e]);
//		}
//		
//		private void temp() {} // TODO remove this, only used for joining call hierarchy overview for all constructors
//		
//		/**
//		 * Write the (current) list of entries to a file named 'entries.txt',
//		 * located in the underlying folder. If a file with that name already
//		 * exists, it is renamed by appending a timestamp and '.old' to its
//		 * name.
//		 * @throws IOException
//		 */
//		public void storeEntryList() throws IOException {
//			ImDocumentEntry[] entries = this.getEntries();
//			File newEntryFile = new File(this.entryDataFolder, ("entries.txt" + ".new"));
//			BufferedWriter entryOut = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(this.entryDataFolder, ("entries.txt" + ".new"))), "UTF-8"));
//			for (int e = 0; e < entries.length; e++) {
//				entryOut.write(entries[e].toTabString());
//				entryOut.newLine();
//			}
//			entryOut.flush();
//			entryOut.close();
//			File exEntryFile = new File(this.entryDataFolder, "entries.txt");
//			if (exEntryFile.exists())
//				exEntryFile.renameTo(new File(this.entryDataFolder, ("entries.txt" + "." + System.currentTimeMillis() + ".old")));
//			newEntryFile.renameTo(new File(this.entryDataFolder, "entries.txt"));
//		}
//		public boolean canLoadDocument() {
//			return this.hasEntry("document.csv");
//		}
//		public boolean canStoreDocument() {
//			return true;
//		}
//		public String getDocumentDataId() {
//			return this.entryDataFolder.getAbsolutePath();
//		}
//		public boolean hasEntryData(ImDocumentEntry entry) {
//			File entryDataFile = new File(this.entryDataFolder, entry.getFileName());
//			return entryDataFile.exists();
//		}
//		public InputStream getInputStream(String entryName) throws IOException {
//			ImDocumentEntry entry = this.getEntry(entryName);
//			if (entry == null)
//				throw new FileNotFoundException(entryName);
//			File entryDataFile = new File(this.entryDataFolder, entry.getFileName());
//			return new BufferedInputStream(new FileInputStream(entryDataFile));
//		}
//		public OutputStream getOutputStream(final String entryName, boolean writeDirectly) throws IOException {
//			
//			//	check file name
//			checkEntryName(entryName);
//			
//			//	split file name from file extension so data hash can be inserted in between
//			final String entryDataFileName;
//			final String entryDataFileExtension;
//			if (entryName.indexOf('.') == -1) {
//				entryDataFileName = entryName;
//				entryDataFileExtension = "";
//			}
//			else {
//				entryDataFileName = entryName.substring(0, entryName.lastIndexOf('.'));
//				entryDataFileExtension = entryName.substring(entryName.lastIndexOf('.'));
//			}
//			
//			//	write directly to file (flag defaults to no entry present)
//			if (writeDirectly || !this.hasEntry(entryName)) {
//				final File entryDataFileWriting = new File(this.entryDataFolder, (entryDataFileName + ".writing" + entryDataFileExtension));
//				return new DataHashOutputStream(new BufferedOutputStream(new FileOutputStream(entryDataFileWriting))) {
//					public void close() throws IOException {
//						super.flush();
//						super.close();
//						
//						//	rename file only now that we have the hash
//						File entryDataFile = new File(entryDataFolder, (entryDataFileName + "." + this.getDataHash() + entryDataFileExtension));
//						if (entryDataFile.exists())
//							entryDataFileWriting.delete();
//						else entryDataFileWriting.renameTo(entryDataFile);
//						
//						//	update entry list
//						putEntry(new ImDocumentEntry(entryName, System.currentTimeMillis(), this.getDataHash()));
//					}
//				};
//			}
//			
//			//	entry already exists, and we're allowed to buffer
//			else return new DataHashOutputStream(new ByteArrayOutputStream()) {
//				public void close() throws IOException {
//					super.flush();
//					super.close();
//					
//					//	write buffer content to persistent storage only if not already there
//					File entryDataFile = new File(entryDataFolder, (entryDataFileName + "." + this.getDataHash() + entryDataFileExtension));
//					if (!entryDataFile.exists()) {
//						BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(entryDataFile));
//						((ByteArrayOutputStream) this.out).writeTo(out);
//						out.flush();
//						out.close();
//					}
//					
//					//	update entry list
//					putEntry(new ImDocumentEntry(entryName, System.currentTimeMillis(), this.getDataHash()));
//				}
//			};
////			else return new DataHashOutputStream(new ByteArrayBuffer()) {
////				public void close() throws IOException {
////					super.flush();
////					super.close();
////					
////					//	write buffer content to persistent storage only if not already there
////					File entryDataFile = new File(entryDataFolder, (entryDataFileName + "." + this.getDataHash() + entryDataFileExtension));
////					if (!entryDataFile.exists()) {
////						BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(entryDataFile));
////						((ByteArrayBuffer) this.out).writeTo(out);
////						out.flush();
////						out.close();
////					}
////					
////					//	update entry list
////					putEntry(new ImDocumentEntry(entryName, System.currentTimeMillis(), this.getDataHash()));
////				}
////			};
//		}
//	}
//	
////	/* 
////	 * Test against ByteArrayOutputStream / ByteArrayInputStream found buffer
////	 * to not be any faster, and consume _more_ memory before GC. Afterward,
////	 * the two are about the same. But lacking advantages, we can just keep on
////	 * using the built-in Java facilities.
////	 */
////	
////	/**
////	 * Buffer for byte data. As opposed to pairs of ByteArrayOutputStream and
////	 * ByteArrayInputStream, instances of this class share their internal data
////	 * structures with input streams obtained from the
////	 * <code>getInputStream()</code> method, saving considerable copying
////	 * overhead. On top of this, instances of this class store data in a
////	 * two-dimensional array (instead of a linear one) and instantiate the
////	 * second dimension step by step as more data comes in. This saves the
////	 * lion's share of the overhead incurred by repeatedly doubling the size of
////	 * a linear array, both in terms of copying and unused space.
////	 * 
////	 * @author sautter
////	 */
////	public static class ByteArrayBuffer extends OutputStream {
////		private static final int defaultDataRowLength = 16192;
////		
////		private final int dataRowLength;
////		private int size = 0; // total number of bytes written so far
////		private byte[][] data = new byte[10][];
////		private int dataOffset = 0; // index of row currently writing to
////		private byte[] dataRow = null;
////		private int dataRowOffset = 0; // index of next byte to write in current row
////		
////		/** Constructor
////		 */
////		public ByteArrayBuffer() {
////			this(defaultDataRowLength);
////		}
////		
////		/**
////		 * Constructor
////		 * @param dataRowLength the length of individual data rows
////		 */
////		public ByteArrayBuffer(int dataRowLength) {
////			this.dataRowLength = dataRowLength;
////			this.dataRow = new byte[this.dataRowLength];
////			this.data[0] = this.dataRow;
////		}
////		
////		/**
////		 * Retrieve an input stream that reads the data in the buffer from the
////		 * beginning. If further data is written to the buffer after the input
////		 * stream is retrieved from this method, the input stream will include
////		 * the latter data as well.
////		 * @return an input stream reading the buffered bytes
////		 */
////		public InputStream getInputStream() {
////			return new InputStream() {
////				private int pos = 0; // total number of bytes read so far
////				private int dataPos = 0; // index of row currently reading from
////				private byte[] dataRow = data[this.dataPos];
////				private int dataRowPos = 0; // index of next byte in row to read
////				
////				/* (non-Javadoc)
////				 * @see java.io.InputStream#available()
////				 */
////				public int available() throws IOException {
////					return (size - this.pos);
////				}
////				
////				/* (non-Javadoc)
////				 * @see java.io.InputStream#read()
////				 */
////				public int read() throws IOException {
////					if (size <= this.pos)
////						return -1;
////					int b = (this.dataRow[this.dataRowPos] & 0xFF);
////					this.dataRowPos++;
////					this.pos++;
////					if (this.dataRowPos == dataRowLength) {
////						this.dataPos++;
////						this.dataRow = data[this.dataPos];
////						this.dataRowPos = 0;
////					}
////					return b;
////				}
////				
////				/* (non-Javadoc)
////				 * @see java.io.InputStream#read(byte[], int, int)
////				 */
////				public int read(byte[] buffer, int off, int len) throws IOException {
////					if (size <= this.pos)
////						return -1;
////					int read = 0;
////					while (len > 0) {
////						int drLen = Math.min(len, (((this.dataPos < dataOffset) ? dataRowLength : dataRowOffset) - this.dataRowPos));
////						System.arraycopy(this.dataRow, this.dataRowPos, buffer, off, drLen);
////						read += drLen;
////						off += drLen;
////						len -= drLen;
////						this.dataRowPos += drLen;
////						this.pos += drLen;
////						if (this.pos == size)
////							break;
////						if (this.dataRowPos == dataRowLength) {
////							this.dataPos++;
////							this.dataRow = data[this.dataPos];
////							this.dataRowPos = 0;
////						}
////					}
////					return ((read == 0) ? -1 : read);
////				}
////			};
////		}
////		
////		/**
////		 * Write the current content of the buffer to an output stream.
////		 * @param out the output stream to write to
////		 * @throws IOException
////		 */
////		public void writeTo(OutputStream out) throws IOException {
////			for (int r = 0; r <= this.dataOffset; r++)
////				out.write(this.data[r], 0, ((r == this.dataOffset) ? this.dataRowOffset : this.dataRowLength));
////		}
////		
////		/* (non-Javadoc)
////		 * @see java.io.OutputStream#write(int)
////		 */
////		public void write(int b) {
////			this.ensureCapacity();
////			this.dataRow[this.dataRowOffset] = ((byte) b);
////			this.dataRowOffset += 1;
////			this.size += 1;
////		}
////		
////		/* (non-Javadoc)
////		 * @see java.io.OutputStream#write(byte[], int, int)
////		 */
////		public void write(byte[] buffer, int off, int len) {
////			while (len > 0) {
////				this.ensureCapacity();
////				int drLen = Math.min(len, (this.dataRowLength - this.dataRowOffset));
////				System.arraycopy(buffer, off, this.dataRow, this.dataRowOffset, drLen);
////				off += drLen;
////				len -= drLen;
////				this.dataRowOffset += drLen;
////				this.size += drLen;
////			}
////		}
////		
////		private void ensureCapacity() {
////			if (this.dataRowOffset < this.dataRow.length)
////				return;
////			if ((this.dataOffset + 1) == this.data.length) {
////				byte[][] data = new byte[this.data.length * 2][];
////				System.arraycopy(this.data, 0, data, 0, this.data.length);
////				this.data = data;
////			}
////			this.dataRowOffset = 0;
////			this.dataRow = new byte[this.dataRowLength];
////			this.dataOffset++;
////			this.data[this.dataOffset] = this.dataRow;
////		}
////	}
//}