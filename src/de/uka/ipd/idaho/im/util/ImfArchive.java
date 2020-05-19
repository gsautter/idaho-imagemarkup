///*
// * Copyright (c) 2006-, IPD Boehm, Universitaet Karlsruhe (TH) / KIT, by Guido Sautter
// * All rights reserved.
// *
// * Redistribution and use in source and binary forms, with or without
// * modification, are permitted provided that the following conditions are met:
// *
// *     * Redistributions of source code must retain the above copyright
// *       notice, this list of conditions and the following disclaimer.
// *     * Redistributions in binary form must reproduce the above copyright
// *       notice, this list of conditions and the following disclaimer in the
// *       documentation and/or other materials provided with the distribution.
// *     * Neither the name of the Universitaet Karlsruhe (TH) / KIT nor the
// *       names of its contributors may be used to endorse or promote products
// *       derived from this software without specific prior written permission.
// *
// * THIS SOFTWARE IS PROVIDED BY UNIVERSITAET KARLSRUHE (TH) / KIT AND CONTRIBUTORS 
// * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
// * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// * ARE DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE FOR ANY
// * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
// * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
// * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
// * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
// */
//package de.uka.ipd.idaho.im.util;
//
//import java.io.BufferedInputStream;
//import java.io.BufferedOutputStream;
//import java.io.ByteArrayInputStream;
//import java.io.ByteArrayOutputStream;
//import java.io.File;
//import java.io.FileInputStream;
//import java.io.FileNotFoundException;
//import java.io.FileOutputStream;
//import java.io.FilterOutputStream;
//import java.io.IOException;
//import java.io.InputStream;
//import java.io.OutputStream;
//import java.io.SequenceInputStream;
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.TreeMap;
//
//import de.uka.ipd.idaho.easyIO.streams.PeekInputStream;
//import de.uka.ipd.idaho.gamta.Attributed;
//import de.uka.ipd.idaho.gamta.util.imaging.ImagingConstants;
//import de.uka.ipd.idaho.gamta.util.imaging.PageImage;
//import de.uka.ipd.idaho.gamta.util.imaging.PageImageInputStream;
//import de.uka.ipd.idaho.gamta.util.imaging.PageImageStore.AbstractPageImageStore;
//import de.uka.ipd.idaho.im.ImDocument;
//
///**
// * Wrapper for a persistent representation of an IMF document to retrieve IMF
// * files from. IMF archives include provenance information and versioning of
// * individual entries, whereas IMF files simply represent the current state of
// * an Image markup document. Conceptually, an IMF file is the projection of an
// * IMF archive to the current document version.<br>
// * This class can also directly produce Image Markup documents, which can
// * comprise the whole archive content, or a single continuous range of pages.
// * 
// * @author sautter
// */
//public class ImfArchive implements ImagingConstants {
//	
//	public class Entry {
//		String name;
//		ArrayList versions;
//		
//		Entry(String name) {
//			this.name = name;
//		}
//		
//		long getUpdateTimestamp() {
//			if (this.tempFileName != null)
//				return this.tempFileTimestamp;
//			return ((EntryVersion) this.versions.get(this.versions.size()-1)).createTime;
//		}
//		String getUpdateUser() {
//			if (this.tempFileName != null)
//				return null; // TODO figure out how to return a meaningful user name here (current user !!!)
//			return ((EntryVersion) this.versions.get(this.versions.size()-1)).createUser;
//		}
//		int getVersionNr() {
//			if (this.tempFileName != null)
//				return 0;
//			return ((EntryVersion) this.versions.get(this.versions.size()-1)).nr;
//		}
//		
//		EntryVersion getVersion(int nr) {
//			if (nr < 1)
//				return ((EntryVersion) this.versions.get(this.versions.size()-1));
//			else if (nr <= this.versions.size())
//				return ((EntryVersion) this.versions.get(nr-1));
//			else return null;
//		}
//		
//		boolean isDirty() {
//			return (this.tempFileName != null);
//		}
//		
//		String tempFileName;
//		long tempFileTimestamp;
//		
//		OutputStream getOutputStream() throws IOException {
//			return new FilterOutputStream(ImfArchive.this.getOutputStream(this.name + ".tmp")) {
//				public void close() throws IOException {
//					super.close();
//					tempFileName = (name + ".tmp");
//					tempFileTimestamp = System.currentTimeMillis();
//				}
//			};
//		}
//		
//		void persist(long timestamp, String userName) throws IOException {
//			if (this.tempFileName == null)
//				return;
//			
//			String fileName = ((this.name.lastIndexOf('.') == -1) ? (this.name + "." + timestamp) : (this.name.substring(0, this.name.lastIndexOf('.')) + "." + timestamp + this.name.substring(this.name.lastIndexOf('.'))));
//			
//			InputStream in = ImfArchive.this.getInputStream(this.tempFileName);
//			OutputStream out = ImfArchive.this.getOutputStream(fileName);
//			byte[] buffer = new byte[1024];
//			for (int r; (r = in.read(buffer, 0, buffer.length)) != -1;)
//				out.write(buffer, 0, r);
//			out.flush();
//			out.close();
//			in.close();
//			
//			this.versions.add(new EntryVersion((this.versions.size() + 1), timestamp, userName, fileName));
//			
//			this.tempFileName = null;
//			this.tempFileTimestamp = -1;
//		}
//	}
//	
//	public class EntryVersion {
//		int nr;
//		long createTime;
//		String createUser;
//		String fileName;
//		EntryVersion(int nr, long createTime, String createUser, String fileName) {
//			this.nr = nr;
//			this.createTime = createTime;
//			this.createUser = createUser;
//			this.fileName = fileName;
//		}
//	}
//	
//	private class Page {
//		int id;
//		Attributed attributes;
//		
//		int iOriginalWidth;
//		int iOriginalHeight;
//		int iOriginalDpi;
//		int iCurrentDpi;
//		int iLeftEdge;
//		int iRightEdge;
//		int iTopEdge;
//		int iBottomEdge;
//		byte[] iMetaDataBytes = null;
//		boolean iMetaDataDirty = false;
//		
//		Page(int id, int iOriginalWidth, int iOriginalHeight, int iOriginalDpi, int iCurrentDpi, int iLeftEdge, int iRightEdge, int iTopEdge, int iBottomEdge) {
//			this.id = id;
//			this.iOriginalWidth = iOriginalWidth;
//			this.iOriginalHeight = iOriginalHeight;
//			this.iOriginalDpi = iOriginalDpi;
//			this.iCurrentDpi = iCurrentDpi;
//			this.iLeftEdge = iLeftEdge;
//			this.iRightEdge = iRightEdge;
//			this.iTopEdge = iTopEdge;
//			this.iBottomEdge = iBottomEdge;
//		}
//		Page(int id, PageImageInputStream piis) {
//			this.id = id;
//			this.updateImageMetaData(piis);
//			this.iMetaDataDirty = false;
//		}
//		
//		byte[] getImageMetaBytes() {
//			if (this.iMetaDataBytes == null) {
//				ByteArrayOutputStream baos = new ByteArrayOutputStream();
//				baos.write((this.iOriginalWidth >> 8) & 255);
//				baos.write(this.iOriginalWidth & 255);
//				baos.write((this.iOriginalHeight >> 8) & 255);
//				baos.write(this.iOriginalHeight & 255);
//				baos.write((this.iOriginalDpi >> 8) & 255);
//				baos.write(this.iOriginalDpi & 255);
//				baos.write((this.iCurrentDpi >> 8) & 255);
//				baos.write(this.iCurrentDpi & 255);
//				baos.write((this.iLeftEdge >> 8) & 255);
//				baos.write(this.iLeftEdge & 255);
//				baos.write((this.iRightEdge >> 8) & 255);
//				baos.write(this.iRightEdge & 255);
//				baos.write((this.iTopEdge >> 8) & 255);
//				baos.write(this.iTopEdge & 255);
//				baos.write((this.iBottomEdge >> 8) & 255);
//				baos.write(this.iBottomEdge & 255);
//				this.iMetaDataBytes = baos.toByteArray();
//			}
//			return this.iMetaDataBytes;
//		}
//		void updateImageMetaData(PageImage pi) {
//			this.iOriginalWidth = pi.originalWidth;
//			this.iOriginalHeight = pi.originalHeight;
//			this.iOriginalDpi = pi.originalDpi;
//			this.iCurrentDpi = pi.currentDpi;
//			this.iLeftEdge = pi.leftEdge;
//			this.iRightEdge = pi.rightEdge;
//			this.iTopEdge = pi.topEdge;
//			this.iBottomEdge = pi.bottomEdge;
//			this.iMetaDataBytes = null;
//			this.iMetaDataDirty = true;
//		}
//		void updateImageMetaData(PageImageInputStream piis) {
//			this.iOriginalWidth = piis.originalWidth;
//			this.iOriginalHeight = piis.originalHeight;
//			this.iOriginalDpi = piis.originalDpi;
//			this.iCurrentDpi = piis.currentDpi;
//			this.iLeftEdge = piis.leftEdge;
//			this.iRightEdge = piis.rightEdge;
//			this.iTopEdge = piis.topEdge;
//			this.iBottomEdge = piis.bottomEdge;
//			this.iMetaDataBytes = null;
//			this.iMetaDataDirty = true;
//		}
//	}
//	
//	private String docId;
//	private Attributed docAttributes;
//	
//	private TreeMap entries = new TreeMap();
//	private ArrayList pages = new ArrayList();
//	
//	private File cacheFolder;
//	private HashMap cacheInMemory;
//	
//	private TreeMap dirtyEntryReGenerators = new TreeMap();
//	
//	private ImfaPageImageStore pageImageStore;
//	
//	/** Constructor (if the argument file is an actual file, it is un-zipped;
//	 * if it actually is a folder, the archive contents are sought inside the
//	 * folder, and the folder is used for caching)
//	 * @param file the file or folder to work with
//	 */
//	public ImfArchive(File file) throws IOException {
//		if (file.isDirectory()) {
//			//	TODO load files.csv (or reconstruct it from file listing)
//			
//			//	TODO construct this around a zipped IMF file (with or without file based cache) or directly around a cache folder
//			
//			//	TODO cache document ID and attributes
//			
//			//	TODO cache pages
//			
//			//	TODO cache page image meta data
//		}
//		else {
//			FileInputStream fis = new FileInputStream(file);
//			this.initFromZip(fis, null);
//			fis.close();
//		}
//		this.pageImageStore = new ImfaPageImageStore();
//		PageImage.addPageImageStore(this.pageImageStore);
//	}
//	
//	/** Constructor (loads the document data by un-zipping the data provided by
//	 * the argument input stream; if the argument cache folder is null, all
//	 * data is cached in an in-memory data structure)
//	 * @param in the input stream to load data from
//	 * @param cacheFolder the folder to use for caching
//	 */
//	public ImfArchive(InputStream in, File cacheFolder) throws IOException {
//		this.initFromZip(in, cacheFolder);
//	}
//	
//	private void initFromZip(InputStream in, File cacheFolder) throws IOException {
//		//	TODO load files.csv (or reconstruct it from zip entry listing)
//		
//		//	TODO construct this around a zipped IMF file (with or without file based cache) or directly around a cache folder
//		
//		//	TODO cache document ID and attributes
//		
//		//	TODO cache pages
//		
//		//	TODO cache page image meta data
//	}
//	
//	private InputStream getInputStream(String fileName) throws IOException {
//		if (this.cacheFolder == null) {
//			byte[] fileBytes = ((byte[]) this.cacheInMemory.get(fileName));
//			if (fileBytes == null)
//				throw new FileNotFoundException(fileName + " does not exist.");
//			return new ByteArrayInputStream(fileBytes);
//		}
//		else return new BufferedInputStream(new FileInputStream(new File(this.cacheFolder, fileName)));
//	}
//	
//	private OutputStream getOutputStream(final String fileName) throws IOException {
//		if (this.cacheFolder == null)
//			return new ByteArrayOutputStream() {
//				public void close() throws IOException {
//					cacheInMemory.put(fileName, this.toByteArray());
//				}
//			};
//		else return new BufferedOutputStream(new FileOutputStream(new File(cacheFolder, fileName)));
//	}
//	
//	public Entry getEntry(String entryName) {
//		return ((Entry) this.entries.get(entryName));
//	}
//	
//	public long getEntryUpdateTimestamp(String entryName) {
//		Entry e = this.getEntry(entryName);
//		return ((e == null) ? -1 : e.getUpdateTimestamp());
//	}
//	
//	public String getEntryUpdateUser(String entryName) {
//		Entry e = this.getEntry(entryName);
//		return ((e == null) ? null : e.getUpdateUser());
//	}
//	
//	public int getEntryVersionNr(String entryName) {
//		Entry e = this.getEntry(entryName);
//		return ((e == null) ? -1 : e.getVersionNr());
//	}
//	
//	public EntryVersion[] getEntryHistory(String entryName) {
//		Entry e = this.getEntry(entryName);
//		return ((e == null) ? null : ((EntryVersion[]) e.versions.toArray(new EntryVersion[e.versions.size()])));
//	}
//	
//	public InputStream getEntryInputStream(String entryName) throws IOException {
//		return this.getEntryInputStream(entryName, -1);
//	}
//	
//	public InputStream getEntryInputStream(String entryName, int version) throws IOException {
//		Entry e = this.getEntry(entryName);
//		if (e == null)
//			throw new FileNotFoundException(entryName + " does not exist.");
//		EntryVersion ev = e.getVersion(version);
//		if (ev == null)
//			throw new FileNotFoundException(entryName + " does not have a version " + version + ".");
//		return this.getInputStream(ev.fileName);
//	}
//	
//	public OutputStream getEntryOutputStream(String entryName) throws IOException {
//		Entry e = this.getEntry(entryName);
//		if (e == null) {
//			e = new Entry(entryName);
//			this.entries.put(entryName, e);
//		}
//		return e.getOutputStream();
//	}
//	
//	public int getPageCount() {
//		return this.pages.size();
//	}
//	
//	public ImDocument getImDocument() {
//		return this.getImDocument(0, (this.getPageCount()-1));
//	}
//	
//	public ImDocument getImDocument(int firstPageId, int lastPageId) {
//		
//		//	TODO create document
//		ImDocument doc = new ImDocument(this.docId);
//		
//		//	TODO add pages, words, regions, and annotations
//		
//		/* TODO add listener to keep track of changes:
//		 * - map name of dirty file to Runnable regenerating it from previous version and document
//		 * - re-generate words.<pageId>.csv completely from page (we're loading full pages anyway)
//		 * - re-generate regions.csv by looping through all regions from pages outside of range and adding in the ones from the document pages
//		 * - re-generate annotations.csv the same way
//		 * - re-generate pages.csv the same way (observe attributes and image meta data)
//		 */
//		
//		//	for now ...
//		return null;
//	}
//	
//	PageImageInputStream getPageImageAsStream(int pageId) throws IOException {
//		return this.getPageImageAsStream(pageId, -1);
//	}
//	
//	PageImageInputStream getPageImageAsStream(int pageId, int version) throws IOException {
//		Entry e = this.getEntry("page." + getPageIdString(pageId, 4) + "." + IMAGE_FORMAT);
//		if (e == null)
//			return null;
//		EntryVersion ev = e.getVersion(version);
//		if (ev == null)
//			return null;
//		PeekInputStream pin = new PeekInputStream(this.getInputStream(ev.fileName), "%PNG".length());
//		if (pin.startsWith("‰PNG", "UTF-8")) {
//			Page p = ((Page) this.pages.get(pageId));
//			byte[] iMetaBytes = p.getImageMetaBytes();
//			return new PageImageInputStream(new SequenceInputStream(new ByteArrayInputStream(iMetaBytes), pin), this.pageImageStore);
//		}
//		else return new PageImageInputStream(pin, this.pageImageStore);
//	}
//	
//	public void close() {
//		PageImage.removePageImageStore(this.pageImageStore);
//	}
//	
//	/* TODO use a files.csv list to manage archive content
//	 * - file name (logical file name)
//	 * - version
//	 * - update user
//	 * - update timestamp
//	 * - archive entry name (likely file name + timestamp) (physical file name)
//	 * ==> files with meta data and past versions
//	 */
//	
//	/**
//	 * Persist the IMF archive to the internal data structures, i.e., to the
//	 * cache folder or the in-memory byte storage.
//	 */
//	public void persist() throws IOException {
//		
//		//	TODO re-generate document.csv every time, adding update timestamp and user
//		
//		//	TODO re-generate all entries flagged for re-generation (marks respective entries as dirty along the way)
//		
//		//	TODO write all dirty entries to cache data structure (cache folder or in-memory)
//	}
//	
//	/**
//	 * Persist the IMF archive. If the argument file is an actual file, the
//	 * archive is zipped and up persisted and persisted to the file. If the
//	 * argument file actually is a folder, the archive contents are persisted
//	 * as individual files inside the folder.
//	 * @param file the file to persist to
//	 */
//	public void persist(File file) throws IOException {
//		if (file.isDirectory()) {
//			//	TODO write files.csv
//			
//			//	TODO re-generate document.csv every time, adding update timestamp and user
//			
//			//	TODO re-generate all entries flagged for re-generation (marks respective entries as dirty along the way)
//			
//			//	TODO write all dirty entries
//		}
//		else this.persist(new FileOutputStream(file));
//	}
//	
//	/**
//	 * Persist the IMF archive in zipped up form to an output stream.
//	 * @param out the output stream to persist to
//	 */
//	public void persist(OutputStream out) throws IOException {
//		//	TODO
//	}
//	
//	private class ImfaPageImageStore extends AbstractPageImageStore {
//		
//		/* (non-Javadoc)
//		 * @see de.uka.ipd.idaho.gamta.util.imaging.PageImageStore#getPriority()
//		 */
//		public int getPriority() {
//			return 10; // we want only images from one document, but those we really want
//		}
//		
//		/* (non-Javadoc)
//		 * @see de.uka.ipd.idaho.gamta.util.imaging.PageImageStore#storePageImage(java.lang.String, de.uka.ipd.idaho.gamta.util.imaging.PageImage)
//		 */
//		public boolean storePageImage(String name, PageImage pageImage) throws IOException {
//			if (!name.startsWith(docId + "."))
//				return false;
//			return (this.storePageImage(docId, Integer.parseInt(name.substring((docId + ".").length())), pageImage) != null);
//		}
//		
//		/* (non-Javadoc)
//		 * @see de.uka.ipd.idaho.gamta.util.imaging.PageImageStore.AbstractPageImageStore#storePageImage(java.lang.String, int, de.uka.ipd.idaho.gamta.util.imaging.PageImage)
//		 */
//		public String storePageImage(String docId, int pageId, PageImage pageImage) throws IOException {
//			if (!ImfArchive.this.docId.equals(docId))
//				return null;
//			((Page) pages.get(pageId)).updateImageMetaData(pageImage);
//			OutputStream out = getEntryOutputStream("page." + getPageIdString(pageId, 4) + "." + IMAGE_FORMAT);
//			pageImage.writeImage(out);
//			out.flush();
//			out.close();
//			return PageImage.getPageImageName(docId, pageId);
//		}
//		
//		/* (non-Javadoc)
//		 * @see de.uka.ipd.idaho.gamta.util.imaging.PageImageSource#isPageImageAvailable(java.lang.String)
//		 */
//		public boolean isPageImageAvailable(String name) {
//			if (!name.startsWith(ImfArchive.this.docId + "."))
//				return false;
//			return this.isPageImageAvailable(ImfArchive.this.docId, Integer.parseInt(name.substring((ImfArchive.this.docId + ".").length())));
//		}
//		
//		/* (non-Javadoc)
//		 * @see de.uka.ipd.idaho.gamta.util.imaging.PageImageSource.AbstractPageImageSource#isPageImageAvailable(java.lang.String, int)
//		 */
//		public boolean isPageImageAvailable(String docId, int pageId) {
//			if (!ImfArchive.this.docId.equals(docId))
//				return false;
//			try {
//				return (ImfArchive.this.getPageImageAsStream(pageId) != null);
//			}
//			catch (Exception e) {
//				return false;
//			}
//		}
//		
//		/* (non-Javadoc)
//		 * @see de.uka.ipd.idaho.gamta.util.imaging.PageImageSource#getPageImageAsStream(java.lang.String)
//		 */
//		public PageImageInputStream getPageImageAsStream(String name) throws IOException {
//			if (!name.startsWith(ImfArchive.this.docId + "."))
//				return null;
//			return this.getPageImageAsStream(ImfArchive.this.docId, Integer.parseInt(name.substring((ImfArchive.this.docId + ".").length())));
//		}
//		
//		/* (non-Javadoc)
//		 * @see de.uka.ipd.idaho.gamta.util.imaging.PageImageSource.AbstractPageImageSource#getPageImageAsStream(java.lang.String, int)
//		 */
//		public PageImageInputStream getPageImageAsStream(String docId, int pageId) throws IOException {
//			if (!ImfArchive.this.docId.equals(docId))
//				return null;
//			return ImfArchive.this.getPageImageAsStream(pageId);
//		}
//	}
//	
//	private static String getPageIdString(int pageId, int length) {
//		String pis = ("" + pageId);
//		while (pis.length() < length)
//			pis = ("0" + pis);
//		return pis;
//	}
//	
//	//	!!! TEST ONLY !!!
//	public static void main(String[] args) {
//		//	TODO test this whole thing
//		
//	}
//}
