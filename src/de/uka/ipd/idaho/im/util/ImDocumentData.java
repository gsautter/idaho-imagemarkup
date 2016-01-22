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
package de.uka.ipd.idaho.im.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.zip.ZipEntry;

import de.uka.ipd.idaho.easyIO.util.RandomByteSource;
import de.uka.ipd.idaho.gamta.util.ProgressMonitor;
import de.uka.ipd.idaho.im.ImDocument;

/**
 * A object providing and storing the data for an Image Markup document, to
 * abstract from different ways of storing Image Markup documents.
 * 
 * @author sautter
 */
public abstract class ImDocumentData {
	
	/**
	 * Metadata of a single entry in an Image Markup document.
	 * 
	 * @author sautter
	 */
	public static class ImDocumentEntry implements Comparable {
		
		/** the name of the entry, e.g. 'document.csv' */
		public final String name;
		
		/** the time the entry was last modified */
		public final long updateTime;
		
		/** the MD5 hash of the entry data */
		public final String dataHash;
		
		private final String fileName;
		
		/**
		 * @param name
		 * @param updateTime
		 * @param dataHash
		 */
		public ImDocumentEntry(String name, long updateTime, String dataHash) {
			this.name = name;
			this.updateTime = updateTime;
			this.dataHash = dataHash;
			if (name.lastIndexOf('.') == -1)
				this.fileName = (this.name + "." + this.dataHash);
			else this.fileName = (this.name.substring(0, this.name.lastIndexOf('.')) + "." + this.dataHash + this.name.substring(this.name.lastIndexOf('.')));
		}
		
		/**
		 * @param file
		 */
		public ImDocumentEntry(File file) {
			this(file.getName(), file.lastModified());
		}
		
		/**
		 * @param name
		 * @param updateTime
		 * @param dataHash
		 */
		public ImDocumentEntry(ZipEntry ze) {
			this(ze.getName(), ze.getTime());
		}
		
		private ImDocumentEntry(String fileName, long updateTime) {
			String[] fileNameParts = fileName.split("\\.");
			if (fileNameParts.length < 2)
				throw new IllegalArgumentException("Illegal name+hash string '" + fileName + "'");
			if (updateTime < 1)
				throw new IllegalArgumentException("Illegal update time " + updateTime + " in IMF Entry '" + fileName + "'");
			this.updateTime = updateTime;
			if (fileNameParts.length == 2) {
				this.name = fileNameParts[0];
				this.dataHash = fileNameParts[1];
			}
			else {
				StringBuffer name = new StringBuffer();
				for (int p = 0; p < fileNameParts.length; p++)
					if (p != (fileNameParts.length - 2)) {
						if (p != 0)
							name.append('.');
						name.append(fileNameParts[p]);
					}
				this.name = name.toString();
				this.dataHash = fileNameParts[fileNameParts.length - 2];
			}
			this.fileName = fileName;
		}
		
		/**
		 * Create the name of a file to store this IMF Entry on persistent
		 * storage. The returned file name takes the form '&lt;nameLessFileExtension&gt;.
		 * &lt;dataHash&gt;.&lt;fileExtension&gt;'.
		 * @return the file name for the IMF Entry
		 */
		public String getFileName() {
			return this.fileName;
		}
		
		/**
		 * Convert the IMF Entry into a tab separated string for listing. The
		 * returned string has the form '&lt;name&gt; &lt;updateTime&gt; 
		 * &lt;dataHash&gt;'.
		 * @return a tab separated string representation of the IMF Entry
		 */
		public String toTabString() {
			return (this.name + "\t" + this.updateTime + "\t" + this.dataHash);
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
			if (imfEntryData.length == 3) try {
				return new ImDocumentEntry(imfEntryData[0], Long.parseLong(imfEntryData[1]), imfEntryData[2]);
			} catch (NumberFormatException nfe) {}
			return null;
		}
	}
	
	private HashMap entriesByName = new LinkedHashMap();
	
	/** Constructor
	 */
	protected ImDocumentData() {}
	
	/**
	 * Retrieve the current entries of the document.
	 * @return an array holding the entries
	 */
	public ImDocumentEntry[] getEntries() {
		return ((ImDocumentEntry[]) this.entriesByName.values().toArray(new ImDocumentEntry[this.entriesByName.size()]));
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
	 * Test if this document data object has an entry with a specific name.
	 * @param entryName the name of the entry to test
	 * @return true if the argument entry if included in this document data
	 */
	public boolean hasEntry(String entryName) {
		return this.entriesByName.containsKey(entryName);
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
	public ImDocument getDocument() throws IOException {
		return this.getDocument(ProgressMonitor.dummy);
	}
	
	/**
	 * Instantiate an Image Markup document from the data.
	 * @param pm a progress monitor to observe the loading process
	 * @return the Image Markup document
	 */
	public ImDocument getDocument(ProgressMonitor pm) throws IOException {
		return (this.canLoadDocument() ? ImDocumentIO.loadDocument(this, pm) : null);
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
	
//	
//	/**
//	 * Produce a document data object from an input stream and a cache folder.
//	 * This method expects the argument input stream to provide the document
//	 * entries in a zipped-up form; it will un-zip and cache them first thing.
//	 * If the argument cache folder is null, the individual un-zipped entries
//	 * are cached as byte arrays in memory; otherwise, the argument folder is
//	 * used for caching.
//	 * @param in the input stream to read from
//	 * @param inLength the length of the input stream
//	 * @param cacheFolder the folder to use for caching
//	 * @param pm a progress monitor to observe the reading process
//	 * @return a document data object wrapping the data from the argument input
//	 *            stream
//	 * @throws IOException
//	 */
//	public static ImDocumentData getDocumentData(InputStream in, int inLength, File cacheFolder, ProgressMonitor pm) throws IOException {
//		
//	}
//	
//	/**
//	 * Produce a document data object from an input stream and a cache folder.
//	 * This method expects the argument input stream to provide the document
//	 * entries in a zipped-up form; it will un-zip and cache them first thing.
//	 * If the argument cache folder is null, the individual un-zipped entries
//	 * are cached as byte arrays in memory; otherwise, the argument folder is
//	 * used for caching.
//	 * @param in the input stream to read from
//	 * @param inLength the length of the input stream
//	 * @param cacheFolder the folder to use for caching
//	 * @param pm a progress monitor to observe the reading process
//	 * @return a document data object wrapping the data from the argument input
//	 *            stream
//	 * @throws IOException
//	 */
//	public static ImDocumentData getDocumentData(File folder, ImDocumentEntry[] entries) throws IOException {
//		
//	}
	
	/**
	 * An output stream that updates an MD5 message digester as data is written
	 * to it. The hash is finalized when the <code>close()</code> method is
	 * called.
	 * 
	 * @author sautter
	 */
	public static class DataHashOutputStream extends FilterOutputStream {
		private MessageDigest dataHasher = getDataHasher();
		private String dataHash = null;
		
		/** Constructor
		 * @param out the output stream to wrap
		 */
		public DataHashOutputStream(OutputStream out) {
			super(out);
		}
		
		/* (non-Javadoc)
		 * @see java.io.FilterOutputStream#write(int)
		 */
		public synchronized void write(int b) throws IOException {
			this.out.write(b);
			this.dataHasher.update((byte) b);
		}
		
		/* (non-Javadoc)
		 * @see java.io.FilterOutputStream#write(byte[])
		 */
		public void write(byte[] b) throws IOException {
			this.write(b, 0, b.length);
		}
		
		/* (non-Javadoc)
		 * @see java.io.FilterOutputStream#write(byte[], int, int)
		 */
		public synchronized void write(byte[] b, int off, int len) throws IOException {
			this.out.write(b, off, len);
			this.dataHasher.update(b, off, len);
		}
		
		/** 
		 * This implementation first closes the wrapped input stream and theb
		 * finalizes the MD5 hash of the data.
		 * @see java.io.FilterOutputStream#close()
		 */
		public void close() throws IOException {
			super.close();
			
			//	we have been closed before
			if (this.dataHasher == null)
				return;
			
			//	finalize hash and rename file
			this.dataHash = new String(RandomByteSource.getHexCode(this.dataHasher.digest()));
			
			//	return digester to instance pool
			returnDataHash(this.dataHasher);
			this.dataHasher = null;
		}
		
		/**
		 * Retrieve the data hash, i.e., the MD5 of all the bytes written to
		 * the stream. Before the <code>close()</code> method is called. this
		 * method returns null.
		 * @return the MD5 hash of the data written to the stream
		 */
		public String getDataHash() {
			return this.dataHash;
		}
	}
	
	private static LinkedList dataHashPool = new LinkedList();
	private static synchronized MessageDigest getDataHasher() {
		if (dataHashPool.size() != 0) {
			MessageDigest dataHash = ((MessageDigest) dataHashPool.removeFirst());
			dataHash.reset();
			return dataHash;
		}
		try {
			MessageDigest dataHash = MessageDigest.getInstance("MD5");
			dataHash.reset();
			return dataHash;
		}
		catch (NoSuchAlgorithmException nsae) {
			System.out.println(nsae.getClass().getName() + " (" + nsae.getMessage() + ") while creating checksum digester.");
			nsae.printStackTrace(System.out); // should not happen, but Java don't know ...
			return null;
		}
	}
	private static synchronized void returnDataHash(MessageDigest dataHash) {
		dataHashPool.addLast(dataHash);
	}
	
	/**
	 * A document data object backed by a folder that contains the data of the
	 * document entries.
	 * 
	 * @author sautter
	 */
	public static class FolderImDocumentData extends ImDocumentData {
		private File entryDataFolder;
		
		/**
		 * Constructor creating a document data object from a newly created
		 * folder. Its intended use is for storing Image Markup documents to a
		 * new location, populating the entry list as data is stored.
		 * @param entryDataFolder the folder containing the entry data
		 */
		public FolderImDocumentData(File entryDataFolder) throws IOException {
			this.entryDataFolder = entryDataFolder;
		}
		
		/**
		 * Constructor creating a document data object from a folder that is
		 * already filled. If the argument entry list is null, this constructor
		 * attempts to load it from a file named 'entries.txt'.
		 * @param entryDataFolder the folder containing the entry data
		 * @param entries the list of entries
		 * @throws IOException
		 */
		public FolderImDocumentData(File entryDataFolder, ImDocumentEntry[] entries) throws IOException {
			this.entryDataFolder = entryDataFolder;
			
			//	load entry list if not given
			if (entries == null) {
				BufferedReader entryIn = new BufferedReader(new InputStreamReader(new FileInputStream(new File(this.entryDataFolder, "entries.txt")), "UTF-8"));
				for (String imfEntryLine; (imfEntryLine = entryIn.readLine()) != null;) {
					ImDocumentEntry entry = ImDocumentEntry.fromTabString(imfEntryLine);
					if (entry != null)
						this.putEntry(entry);
				}
				entryIn.close();
			}
			
			//	store entry list as is
			else for (int e = 0; e < entries.length; e++)
				this.putEntry(entries[e]);
		}
		
		/**
		 * Write the (current) list of entries to a file named 'entries.txt',
		 * located in the underlying folder. If a file with that name already
		 * exists, it is renamed by appending a timestamp and '.old' to its
		 * name.
		 * @throws IOException
		 */
		public void storeEntryList() throws IOException {
			ImDocumentEntry[] entries = this.getEntries();
			File newEntryFile = new File(this.entryDataFolder, ("entries.txt" + ".new"));
			BufferedWriter entryOut = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(this.entryDataFolder, ("entries.txt" + ".new"))), "UTF-8"));
			for (int e = 0; e < entries.length; e++) {
				entryOut.write(entries[e].toTabString());
				entryOut.newLine();
			}
			entryOut.flush();
			entryOut.close();
			File exEntryFile = new File(this.entryDataFolder, "entries.txt");
			if (exEntryFile.exists())
				exEntryFile.renameTo(new File(this.entryDataFolder, ("entries.txt" + "." + System.currentTimeMillis() + ".old")));
			newEntryFile.renameTo(new File(this.entryDataFolder, "entries.txt"));
		}
		public boolean canLoadDocument() {
			return this.hasEntry("document.csv");
		}
		public boolean canStoreDocument() {
			return true;
		}
		public String getDocumentDataId() {
			return this.entryDataFolder.getAbsolutePath();
		}
		public boolean hasEntryData(ImDocumentEntry entry) {
			File entryDataFile = new File(this.entryDataFolder, entry.getFileName());
			return entryDataFile.exists();
		}
		public InputStream getInputStream(String entryName) throws IOException {
			ImDocumentEntry entry = this.getEntry(entryName);
			if (entry == null)
				throw new FileNotFoundException(entryName);
			File entryDataFile = new File(this.entryDataFolder, entry.getFileName());
			return new BufferedInputStream(new FileInputStream(entryDataFile));
		}
		public OutputStream getOutputStream(final String entryName, boolean writeDirectly) throws IOException {
			
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
						
						//	rename file only now that we have the hash
						File entryDataFile = new File(entryDataFolder, (entryDataFileName + "." + this.getDataHash() + entryDataFileExtension));
						if (entryDataFile.exists())
							entryDataFileWriting.delete();
						else entryDataFileWriting.renameTo(entryDataFile);
						
						//	update entry list
						putEntry(new ImDocumentEntry(entryName, System.currentTimeMillis(), this.getDataHash()));
					}
				};
			}
			
			//	entry already exists, and we're allowed to buffer
			else return new DataHashOutputStream(new ByteArrayOutputStream()) {
				public void close() throws IOException {
					super.flush();
					super.close();
					
					//	write buffer content to persistent storage only if not already there
					File entryDataFile = new File(entryDataFolder, (entryDataFileName + "." + this.getDataHash() + entryDataFileExtension));
					if (!entryDataFile.exists()) {
						BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(entryDataFile));
						((ByteArrayOutputStream) this.out).writeTo(out);
						out.flush();
						out.close();
					}
					
					//	update entry list
					putEntry(new ImDocumentEntry(entryName, System.currentTimeMillis(), this.getDataHash()));
				}
			};
		}
	}
}