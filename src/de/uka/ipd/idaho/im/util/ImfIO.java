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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.SequenceInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import de.uka.ipd.idaho.easyIO.streams.PeekInputStream;
import de.uka.ipd.idaho.easyIO.util.RandomByteSource;
import de.uka.ipd.idaho.gamta.util.ProgressMonitor;
import de.uka.ipd.idaho.gamta.util.imaging.BoundingBox;
import de.uka.ipd.idaho.gamta.util.imaging.ImagingConstants;
import de.uka.ipd.idaho.gamta.util.imaging.PageImage;
import de.uka.ipd.idaho.gamta.util.imaging.PageImageInputStream;
import de.uka.ipd.idaho.gamta.util.imaging.PageImageSource;
import de.uka.ipd.idaho.gamta.util.imaging.PageImageStore.AbstractPageImageStore;
import de.uka.ipd.idaho.im.ImAnnotation;
import de.uka.ipd.idaho.im.ImDocument;
import de.uka.ipd.idaho.im.ImFont;
import de.uka.ipd.idaho.im.ImObject;
import de.uka.ipd.idaho.im.ImPage;
import de.uka.ipd.idaho.im.ImRegion;
import de.uka.ipd.idaho.im.ImSupplement;
import de.uka.ipd.idaho.im.ImWord;
import de.uka.ipd.idaho.stringUtils.StringVector;
import de.uka.ipd.idaho.stringUtils.csvHandler.StringRelation;
import de.uka.ipd.idaho.stringUtils.csvHandler.StringTupel;

/**
 * IO utility for image markup files (file ending .imf). However, in favor of
 * flexibility, this class works on input and output streams. This is to
 * abstract from the exact location the file is loaded from.<br>
 * An image markup file is basically a ZIP archive containing (at least) the
 * following entries:
 * <ul>
 * <li><b>document.csv</b>: a CSV file containing the document ID and document
 * attributes.</li>
 * <li><b>pages.csv</b>: a CSV file containing information about individual
 * pages, namely the page ID, the page bounding box, and additional page
 * attributes.</li>
 * <li><b>words.csv</b>: a CSV file containing individual layout words, their
 * bounding boxes, assigned string value, IDs of their predecessor and
 * successor, type of connection to their successor (if any), as well as
 * additional attributes.</li>
 * <li><b>regions.csv</b>: a CSV file containing individual layout regions,
 * namely the region type, the ID of the page the region lies on, the region
 * bounding box, as well as additional attributes.</li>
 * <li><b>annotations.csv</b>: a CSV file containing the semantic markup of the
 * document, namely logical structure and detail annotations. They are described
 * by the IDs of their first and last word, their type, and additional attributes.
 * </li>
 * <li><b>pageImages.csv</b>: a CSV file containing meta data for the page
 * images, namely the page ID, image dimensions, resolution in DPI, and the
 * widths of any cut-off white margins.</li>
 * <li><b>&lt;pageId&gt;.png</b>: images of the individual pages, one per page.
 * </li>
 * </ul>
 * In addition, an image markup file can contain the following optional entries:
 * <ul>
 * <li><b>fonts.csv</b>: a CSV file containing custom fonts if the image markup
 * file was generated from a source supporting such. Each character of each
 * font is described with the font name and style, its character ID, its Unicode
 * transcription, and a hex-serialized representation its glyph.</li>
 * <li><b>supplements.csv</b>: a CSV file containing meta data of binary
 * supplements to the document. Each supplement is described with its name,
 * type, MIME type, and file name, plus additional type specific attributes.</li>
 * <li><b>&lt;supplementName&gt;.&lt;supplementType&gt;</b>: the data of binary
 * supplements, pointed to by the supplement names listed in <i>supplements.csv</i>.
 * </li>
 * </ul>
 * 
 * @author sautter
 */
public class ImfIO implements ImagingConstants {
	
	/**
	 * Metadata of a single entry in an IMF, to allow differential updates of
	 * documents stored in folders.
	 * 
	 * @author sautter
	 */
	public static class ImfEntry implements Comparable {
		
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
		public ImfEntry(String name, long updateTime, String dataHash) {
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
		public ImfEntry(File file) {
			this(file.getName(), file.lastModified());
		}
		
		/**
		 * @param name
		 * @param updateTime
		 * @param dataHash
		 */
		public ImfEntry(ZipEntry ze) {
			this(ze.getName(), ze.getTime());
		}
		
		private ImfEntry(String fileName, long updateTime) {
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
			return ((obj instanceof ImfEntry) ? this.name.compareTo(((ImfEntry) obj).name) : -1);
		}
		
		/**
		 * Parse an IMF entry from its tab separated string representation, as
		 * returned by the <code>toTabString()</code> method.
		 * @param tabStr the tab separated string representation to parse
		 * @return the IMF entry parsed from the argument string
		 */
		public static ImfEntry fromTabString(String tabStr) {
			String[] imfEntryData = tabStr.split("\\t");
			if (imfEntryData.length == 3) try {
				return new ImfEntry(imfEntryData[0], Long.parseLong(imfEntryData[1]), imfEntryData[2]);
			} catch (NumberFormatException nfe) {}
			return null;
		}
	}
	
	/**
	 * Load an image markup document. If the argument file is an actual file,
	 * this method assumes it to be a zipped-up Image Markup File. If the file
	 * is a folder, however, this method assumes it to be an already un-zipped
	 * Image Markup File.
	 * @param file the file to load from
	 * @return an image markup document representing the content of the
	 *            argument stream.
	 * @throws IOException
	 */
	public static ImDocument loadDocument(File file) throws IOException {
		return loadDocument(file, null, null);
	}
	
	/**
	 * Load an image markup document. If the argument file is an actual file,
	 * this method assumes it to be a zipped-up Image Markup File. If the file
	 * is a folder, however, this method assumes it to be an already un-zipped
	 * Image Markup File. In the former case, the argument list of IMF entries
	 * is ignored. In the latter case, if the IMF entry list is null, this
	 * method expects a list named 'entries.txt' in the argument folder;
	 * otherwise, it uses the argument list.
	 * @param file the file to load from
	 * @param entries an array holding the IMF entries to use
	 * @return an image markup document representing the content of the
	 *            argument stream.
	 * @throws IOException
	 */
	public static ImDocument loadDocument(File file, ImfEntry[] entries) throws IOException {
		return loadDocument(file, entries, null);
	}
	
	/**
	 * Load an image markup document. If the argument file is an actual file,
	 * this method assumes it to be a zipped-up Image Markup File. If the file
	 * is a folder, however, this method assumes it to be an already un-zipped
	 * Image Markup File.
	 * @param in the input stream to load from
	 * @param pm a progress monitor to observe the loading process
	 * @return an image markup document representing the content of the
	 *            argument stream.
	 * @throws IOException
	 */
	public static ImDocument loadDocument(File file, ProgressMonitor pm) throws IOException {
		return loadDocument(file, null, pm);
	}
	
	/**
	 * Load an image markup document. If the argument file is an actual file,
	 * this method assumes it to be a zipped-up Image Markup File. If the file
	 * is a folder, however, this method assumes it to be an already un-zipped
	 * Image Markup File. In the former case, the argument list of IMF entries
	 * is ignored. In the latter case, if the IMF entry list is null, this
	 * method expects a list named 'entries.txt' in the argument folder;
	 * otherwise, it uses the argument list.
	 * @param in the input stream to load from
	 * @param entries an array holding the IMF entries to use
	 * @param pm a progress monitor to observe the loading process
	 * @return an image markup document representing the content of the
	 *            argument stream.
	 * @throws IOException
	 */
	public static ImDocument loadDocument(File file, ImfEntry[] entries, ProgressMonitor pm) throws IOException {
		
		//	assume folder to be un-zipped IMF
		if (file.isDirectory())
			return loadDocument(null, -1, null, file, entries, pm);
		
		//	assume file to be zipped-up IMF
		else {
			FileInputStream fis = new FileInputStream(file);
			ImDocument doc = loadDocument(fis, ((int) file.length()), null, null, null, pm);
			fis.close();
			return doc;
		}
	}
	
	/**
	 * Load an image markup document.
	 * @param in the input stream to load from
	 * @return an image markup document representing the content of the
	 *            argument stream.
	 * @throws IOException
	 */
	public static ImDocument loadDocument(InputStream in) throws IOException {
		return loadDocument(in, null, null);
	}
	
	/**
	 * Load an image markup document.
	 * @param in the input stream to load from
	 * @param pm a progress monitor to observe the loading process
	 * @return an image markup document representing the content of the
	 *            argument stream.
	 * @throws IOException
	 */
	public static ImDocument loadDocument(InputStream in, ProgressMonitor pm) throws IOException {
		return loadDocument(in, null, pm, -1);
	}
	
	/**
	 * Load an image markup document.
	 * @param in the input stream to load from
	 * @param pm a progress monitor to observe the loading process
	 * @param inLength the number of bytes to expect from the argument input stream
	 * @return an image markup document representing the content of the
	 *            argument stream.
	 * @throws IOException
	 */
	public static ImDocument loadDocument(InputStream in, ProgressMonitor pm, int inLength) throws IOException {
		return loadDocument(in, null, pm, inLength);
	}
	
	/**
	 * Load an image markup document. If the argument cache folder is null,
	 * page images will be cached in memory. For large documents, this can
	 * cause high memory consumption. The argument input stream is not closed.
	 * @param in the input stream to load from
	 * @param cacheFolder the folder to use for caching
	 * @return an image markup document representing the content of the
	 *            argument stream.
	 * @throws IOException
	 */
	public static ImDocument loadDocument(InputStream in, File cacheFolder) throws IOException {
		return loadDocument(in, cacheFolder, null, -1);
	}
	
	/**
	 * Load an image markup document. If the argument cache folder is null,
	 * page images will be cached in memory. For large documents, this can
	 * cause high memory consumption. The argument input stream is not closed.
	 * @param in the input stream to load from
	 * @param cacheFolder the folder to use for caching
	 * @param pm a progress monitor to observe the loading process
	 * @return an image markup document representing the content of the
	 *            argument stream.
	 * @throws IOException
	 */
	public static ImDocument loadDocument(InputStream in, File cacheFolder, ProgressMonitor pm) throws IOException {
		return loadDocument(in, cacheFolder, pm, -1);
	}
	
	/**
	 * Load an image markup document. If the argument cache folder is null,
	 * page images will be cached in memory. For large documents, this can
	 * cause high memory consumption. The argument input stream is not closed.
	 * @param in the input stream to load from
	 * @param cacheFolder the folder to use for caching
	 * @param pm a progress monitor to observe the loading process
	 * @param inLength the number of bytes to expect from the argument input stream
	 * @return an image markup document representing the content of the
	 *            argument stream.
	 * @throws IOException
	 */
	public static ImDocument loadDocument(InputStream in, File cacheFolder, ProgressMonitor pm, int inLength) throws IOException {
		return loadDocument(in, inLength, cacheFolder, null, null, pm);
	}
	
	private static ImDocument loadDocument(InputStream in, int inLength, final File cacheFolder, File folder, ImfEntry[] imfEntryList, ProgressMonitor pm) throws IOException {
		
		//	check progress monitor
		if (pm == null)
			pm = ProgressMonitor.dummy;
		
		//	make reading observable if we know how many bytes to expect
		if ((in != null) && (inLength != -1)) {
			final ProgressMonitor fpm = pm;
			final int fInLength = (inLength / 100);
			in = new FilterInputStream(in) {
				int read = 0;
				public int read() throws IOException {
					int r = super.read();
					if (r != -1) {
						this.read++;
						fpm.setProgress(this.read / fInLength);
					}
					return r;
				}
				public int read(byte[] b) throws IOException {
					return this.read(b, 0, b.length);
				}
				public int read(byte[] b, int off, int len) throws IOException {
					int r = super.read(b, off, len);
					if (r != -1) {
						this.read += r;
						fpm.setProgress(this.read / fInLength);
					}
					return r;
				}
				public long skip(long n) throws IOException {
					long s = super.skip(n);
					this.read += ((int) s);
					fpm.setProgress(this.read / fInLength);
					return s;
				}
			};
		}
		
		//	cache (in memory or on disc)
		final HashMap cache = (((in != null) && (cacheFolder == null)) ? new HashMap() : null);
		final Map imfEntries = ((folder != null) ? new LinkedHashMap() : null);
		
		//	load 'entries.txt' if loading from folder
		if (folder != null) {
			if (imfEntryList == null) {
				BufferedReader imfEntryIn = new BufferedReader(new InputStreamReader(new FileInputStream(new File(folder, "entries.txt")), "UTF-8"));
				for (String imfEntryLine; (imfEntryLine = imfEntryIn.readLine()) != null;) {
					ImfEntry imfe = ImfEntry.fromTabString(imfEntryLine);
					if (imfe != null)
						imfEntries.put(imfe.name, imfe);
				}
				imfEntryIn.close();
			}
			else for (int e = 0; e < imfEntryList.length; e++)
				imfEntries.put(imfEntryList[e].name, imfEntryList[e]);
		}
		
		//	un-zip data from input stream
		if (in != null) {
			
			//	get ready to unzip
			ZipInputStream zin = new ZipInputStream(in);
			
			//	read and cache archive contents
			pm.setStep("Un-zipping & caching archive");
			pm.setBaseProgress(0);
			pm.setMaxProgress(50);
			for (ZipEntry ze; (ze = zin.getNextEntry()) != null;) {
				String zen = ze.getName();
				pm.setInfo(zen);
				OutputStream cacheOut;
				
				//	we do have a cache folder
				if (cache == null) {
					File cacheFile = new File(cacheFolder, zen);
					cacheFile.getParentFile().mkdirs();
					cacheFile.createNewFile();
					cacheOut = new BufferedOutputStream(new FileOutputStream(cacheFile));
				}
				
				//	we have to use in memory caching
				else cacheOut = new ByteArrayOutputStream();
				
				//	cache entry
				byte[] buffer = new byte[1024];
				for (int r; (r = zin.read(buffer, 0, buffer.length)) != -1;)
					cacheOut.write(buffer, 0, r);
				cacheOut.flush();
				cacheOut.close();
				
				//	add to in memory cache
				if (cache != null)
					cache.put(zen, ((ByteArrayOutputStream) cacheOut).toByteArray());
			}
		}
		
		//	read document meta data
		pm.setStep("Reading document data");
		pm.setBaseProgress(50);
		pm.setMaxProgress(55);
		InputStream docIn = getInputStream("document.csv", cache, cacheFolder, folder, imfEntries);
		StringRelation docsData = StringRelation.readCsvData(new InputStreamReader(docIn, "UTF-8"), true, null);
		StringTupel docData = docsData.get(0);
		final String docId = docData.getValue(DOCUMENT_ID_ATTRIBUTE);
		docIn.close();
		if (docId == null)
			throw new IOException("Invalid image markup data: document ID missing");
		//	TODO_later load orientation
		final ImfIoDocument doc = new ImfIoDocument(docId);
		setAttributes(doc, docData.getValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE));
		if (cache != null)
			doc.cache = cache;
		else if (cacheFolder != null)
			doc.cacheFolder = cacheFolder;
		else if (folder != null) {
			doc.folder = folder;
			doc.imfEntries.putAll(imfEntries);
		}
		
		//	read fonts (if any)
		pm.setStep("Reading font data");
		pm.setBaseProgress(55);
		pm.setMaxProgress(60);
		try {
			InputStream fontsIn = doc.getInputStream("fonts.csv");
			StringRelation fontsData = StringRelation.readCsvData(new InputStreamReader(fontsIn, "UTF-8"), true, null);
			fontsIn.close();
			ImFont font = null;
			for (int f = 0; f < fontsData.size(); f++) {
				StringTupel charData = fontsData.get(f);
				String fontName = charData.getValue(ImFont.NAME_ATTRIBUTE);
				if (fontName == null)
					continue;
				if ((font == null) || !font.name.equals(fontName)) {
					pm.setInfo("Adding font " + fontName);
					font = new ImFont(doc, fontName);
					String fontStyle = charData.getValue(ImFont.STYLE_ATTRIBUTE, "");
					font.setBold(fontStyle.indexOf("B") != -1);
					font.setItalics(fontStyle.indexOf("I") != -1);
					font.setSerif(fontStyle.indexOf("S") != -1);
					doc.addFont(font);
				}
				int charId = Integer.parseInt(charData.getValue(ImFont.CHARACTER_ID_ATTRIBUTE, "-1"), 16);
				String charStr = charData.getValue(ImFont.CHARACTER_STRING_ATTRIBUTE);
				String charImageHex = charData.getValue(ImFont.CHARACTER_IMAGE_ATTRIBUTE);
				font.addCharacter(charId, charStr, charImageHex);
			}
		} catch (IOException ioe) { /* we might not have fonts ... */ }
		
		//	read page image data
		final HashMap pageImageAttributesById = new HashMap();
		try {
			pm.setStep("Reading page image data");
			pm.setBaseProgress(95);
			pm.setMaxProgress(99);
			InputStream pageImagesIn = doc.getInputStream("pageImages.csv");
			StringRelation pageImagesData = StringRelation.readCsvData(new InputStreamReader(pageImagesIn, "UTF-8"), true, null);
			pageImagesIn.close();
			for (int p = 0; p < pageImagesData.size(); p++) {
				StringTupel pageImageData = pageImagesData.get(p);
				int piPageId = Integer.parseInt(pageImageData.getValue(ImObject.PAGE_ID_ATTRIBUTE));
				String piAttributes = pageImageData.getValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE);
				pageImageAttributesById.put(new Integer(piPageId), new PageImageAttributes(piAttributes));
			}
		} catch (IOException ioe) { /* we might be faced with the old way ... */ }
		
		//	read pages
		pm.setStep("Reading page data");
		pm.setBaseProgress(60);
		pm.setMaxProgress(65);
		InputStream pagesIn = doc.getInputStream("pages.csv");
		StringRelation pagesData = StringRelation.readCsvData(new InputStreamReader(pagesIn, "UTF-8"), true, null);
		pagesIn.close();
		pm.setInfo("Adding " + pagesData.size() + " pages");
		for (int p = 0; p < pagesData.size(); p++) {
			pm.setProgress((p * 100) / pagesData.size());
			StringTupel pageData = pagesData.get(p);
			int pageId = Integer.parseInt(pageData.getValue(PAGE_ID_ATTRIBUTE));
			BoundingBox bounds = BoundingBox.parse(pageData.getValue(BOUNDING_BOX_ATTRIBUTE));
			ImPage page = new ImPage(doc, pageId, bounds); // constructor adds page to document automatically
			setAttributes(page, pageData.getValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, ""));
			PageImageAttributes pia = ((PageImageAttributes) pageImageAttributesById.get(new Integer(pageId)));
			if (pia != null)
				page.setImageDPI(pia.currentDpi);
		}
		
		//	read words (first add words, then chain them and set attributes, as they might not be stored in stream order)
		pm.setStep("Reading word data");
		pm.setBaseProgress(65);
		pm.setMaxProgress(75);
		InputStream wordsIn = doc.getInputStream("words.csv");
		StringRelation wordsData = StringRelation.readCsvData(new InputStreamReader(wordsIn, "UTF-8"), true, null);
		wordsIn.close();
		pm.setInfo("Adding " + wordsData.size() + " words");
		for (int w = 0; w < wordsData.size(); w++) {
			pm.setProgress((w * 100) / wordsData.size());
			StringTupel wordData = wordsData.get(w);
			ImPage page = doc.getPage(Integer.parseInt(wordData.getValue(PAGE_ID_ATTRIBUTE)));
			BoundingBox bounds = BoundingBox.parse(wordData.getValue(BOUNDING_BOX_ATTRIBUTE));
			new ImWord(page, bounds, wordData.getValue(STRING_ATTRIBUTE));
		}
		pm.setStep("Chaining text streams");
		pm.setBaseProgress(75);
		pm.setMaxProgress(80);
		for (int w = 0; w < wordsData.size(); w++) {
			pm.setProgress((w * 100) / wordsData.size());
			StringTupel wordData = wordsData.get(w);
			BoundingBox bounds = BoundingBox.parse(wordData.getValue(BOUNDING_BOX_ATTRIBUTE));
			ImWord word = doc.getWord(Integer.parseInt(wordData.getValue(PAGE_ID_ATTRIBUTE)), bounds);
			ImWord prevWord = doc.getWord(wordData.getValue(ImWord.PREVIOUS_WORD_ATTRIBUTE));
			if (prevWord != null)
				word.setPreviousWord(prevWord);
			else {
				String textStreamType = wordData.getValue(ImWord.TEXT_STREAM_TYPE_ATTRIBUTE);
				if ((textStreamType == null) || (textStreamType.trim().length() == 0))
					textStreamType = ImWord.TEXT_STREAM_TYPE_MAIN_TEXT;
				word.setTextStreamType(textStreamType);
			}
			word.setNextRelation(wordData.getValue(ImWord.NEXT_RELATION_ATTRIBUTE).charAt(0));
			setAttributes(word, wordData.getValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, ""));
		}
		
		//	read regions
		pm.setStep("Reading region data");
		pm.setBaseProgress(80);
		pm.setMaxProgress(85);
		InputStream regsIn = doc.getInputStream("regions.csv");
		StringRelation regsData = StringRelation.readCsvData(new InputStreamReader(regsIn, "UTF-8"), true, null);
		regsIn.close();
		pm.setInfo("Adding " + regsData.size() + " regions");
		HashMap regionsById = new HashMap();
		for (int r = 0; r < regsData.size(); r++) {
			pm.setProgress((r * 100) / regsData.size());
			StringTupel regData = regsData.get(r);
			ImPage page = doc.getPage(Integer.parseInt(regData.getValue(PAGE_ID_ATTRIBUTE)));
			BoundingBox bounds = BoundingBox.parse(regData.getValue(BOUNDING_BOX_ATTRIBUTE));
			String type = regData.getValue(TYPE_ATTRIBUTE);
			String regId = (type + "@" + page.pageId + "." + bounds.toString());
			ImRegion reg = ((ImRegion) regionsById.get(regId));
			if (reg == null) {
				reg = new ImRegion(page, bounds, type);
				regionsById.put(regId, reg);
			}
			setAttributes(reg, regData.getValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, ""));
		}
		
		//	read annotations
		pm.setStep("Reading annotation data");
		pm.setBaseProgress(85);
		pm.setMaxProgress(90);
		InputStream annotsIn = doc.getInputStream("annotations.csv");
		StringRelation annotsData = StringRelation.readCsvData(new InputStreamReader(annotsIn, "UTF-8"), true, null);
		annotsIn.close();
		pm.setInfo("Adding " + annotsData.size() + " annotations");
		for (int a = 0; a < annotsData.size(); a++) {
			pm.setProgress((a * 100) / annotsData.size());
			StringTupel annotData = annotsData.get(a);
			ImWord firstWord = doc.getWord(annotData.getValue(ImAnnotation.FIRST_WORD_ATTRIBUTE));
			ImWord lastWord = doc.getWord(annotData.getValue(ImAnnotation.LAST_WORD_ATTRIBUTE));
			if ((firstWord == null) || (lastWord == null))
				continue;
			String type = annotData.getValue(TYPE_ATTRIBUTE);
			ImAnnotation annot = doc.addAnnotation(firstWord, lastWord, type);
			if (annot != null)
				setAttributes(annot, annotData.getValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, ""));
		}
		
		//	read meta data of supplements (if any)
		pm.setStep("Reading supplement data");
		pm.setBaseProgress(90);
		pm.setMaxProgress(95);
		try {
			InputStream supplementsIn = doc.getInputStream("supplements.csv");
			StringRelation supplementsData = StringRelation.readCsvData(new InputStreamReader(supplementsIn, "UTF-8"), true, null);
			supplementsIn.close();
			pm.setInfo("Adding " + supplementsData.size() + " supplements");
			for (int s = 0; s < supplementsData.size(); s++) {
				pm.setProgress((s * 100) / supplementsData.size());
				StringTupel supplementData = supplementsData.get(s);
				final String sid = supplementData.getValue(ImSupplement.ID_ATTRIBUTE);
				String st = supplementData.getValue(ImSupplement.TYPE_ATTRIBUTE);
				String smt = supplementData.getValue(ImSupplement.MIME_TYPE_ATTRIBUTE);
				final String sfn = (sid + "." + smt.substring(smt.lastIndexOf('/') + "/".length()));
				pm.setInfo(sfn);
				ImSupplement supplement;
				if (ImSupplement.SOURCE_TYPE.equals(st))
					supplement = new ImSupplement.Source(doc, smt) {
						public InputStream getInputStream() throws IOException {
							return doc.getInputStream(sfn);
						}
					};
				else if (ImSupplement.SCAN_TYPE.equals(st))
					supplement = new ImSupplement.Scan(doc, smt) {
						public InputStream getInputStream() throws IOException {
							return doc.getInputStream(sfn);
						}
					};
				else if (ImSupplement.FIGURE_TYPE.equals(st))
					supplement = new ImSupplement.Figure(doc, smt) {
						public InputStream getInputStream() throws IOException {
							return doc.getInputStream(sfn);
						}
					};
				else supplement = new ImSupplement(doc, st, smt) {
					public String getId() {
						return sid;
					}
					public InputStream getInputStream() throws IOException {
						return doc.getInputStream(sfn);
					}
				};
				setAttributes(supplement, supplementData.getValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, ""));
				doc.addSupplement(supplement, false);
			}
		} catch (IOException ioe) { /* we might not have supplements ... */ }
		
		//	create page image source
		pm.setStep("Creating page image source");
		pm.setBaseProgress(99);
		pm.setMaxProgress(100);
		doc.setPageImageSource(new ImfIoPageImageStore(doc, pageImageAttributesById));
		
		//	if we're in folder mode, store clean IMF entries
		if (imfEntries != null)
			doc.imfEntries.putAll(imfEntries);
		
		//	finally ...
		return doc;
	}
	
	private static class ImfIoDocument extends ImDocument {
		ImfIoPageImageStore imfIoPis;
		ImfIoDocument(String docId) {
			super(docId);
		}
		
		public void setPageImageSource(PageImageSource pis) {
			super.setPageImageSource(pis);
			this.imfIoPis = (((pis instanceof ImfIoPageImageStore) && (((ImfIoPageImageStore) pis).doc == this)) ? ((ImfIoPageImageStore) pis) : null);
		}
		
		public ImSupplement addSupplement(ImSupplement ims) {
			return this.addSupplement(ims, true);
		}
		ImSupplement addSupplement(ImSupplement ims, boolean isExternal) {
			if (isExternal) {
				String smt = ims.getMimeType();
				String sfn = (ims.getId() + "." + smt.substring(smt.lastIndexOf('/') + "/".length()));
				this.imfEntries.remove(sfn); // mark supplement as dirty
			}
			return super.addSupplement(ims);
		}
		ImfEntry getImfEntry(ImSupplement ims) {
			String smt = ims.getMimeType();
			String sfn = (ims.getId() + "." + smt.substring(smt.lastIndexOf('/') + "/".length()));
			ImfEntry imfe = ((ImfEntry) this.imfEntries.get(sfn));
			if (imfe != null) {
				File sf = new File(this.folder, imfe.getFileName());
				return (sf.exists() ? imfe : null);
			}
			else return null;
		}
		
		HashMap cache;
		File cacheFolder;
		File folder;
		final Map imfEntries = new HashMap();
		
		void bindToFolder(File folder, Map imfEntries) {
			if (this.cache != null)
				this.cache.clear();
			this.cache = null;
			this.cacheFolder = null;
			this.folder = folder;
			this.imfEntries.putAll(imfEntries);
		}
		
		boolean isInputStreamAvailable(String entryName) {
			
			//	we're in zip-based im-memory mode
			if (this.cache != null)
				return this.cache.containsKey(entryName);
			
			//	we're in zip-based folder mode
			if (this.cacheFolder != null)
				return (new File(this.cacheFolder, entryName)).exists();
			
			//	we're in folder mode
			if (this.folder != null) {
				ImfEntry imfe = ((ImfEntry) this.imfEntries.get(entryName));
				if (imfe == null)
					return false;
				else return (new File(this.folder, imfe.getFileName())).exists();
			}
			
			//	whatever went wrong ...
			return false;
		}
		InputStream getInputStream(String entryName) throws IOException {
			return ImfIO.getInputStream(entryName, this.cache, this.cacheFolder, this.folder, this.imfEntries);
		}
		OutputStream getOutputStream(String entryName, boolean writeDirectly) throws IOException {
			return ImfIO.getOutputStream(entryName, this.cache, this.cacheFolder, this.folder, writeDirectly, this.imfEntries);
		}
	}
	
	
	private static class ImfIoPageImageStore extends AbstractPageImageStore {
		final ImfIoDocument doc;
		final HashMap pageImageAttributesById;
		ImfIoPageImageStore(ImfIoDocument doc, HashMap pageImageAttributesById) {
			this.doc = doc;
			this.doc.imfIoPis = this;
			this.pageImageAttributesById = pageImageAttributesById;
		}
		public boolean isPageImageAvailable(String name) {
			if (!name.startsWith(this.doc.docId))
				return false;
			name = ("page" + name.substring(this.doc.docId.length() + ".".length()) + "." + IMAGE_FORMAT);
			return this.doc.isInputStreamAvailable(name);
		}
		public PageImageInputStream getPageImageAsStream(String name) throws IOException {
			if (!name.startsWith(this.doc.docId))
				return null;
			String piPageIdStr = name.substring(this.doc.docId.length() + ".".length());
			name = ("page" + piPageIdStr + "." + IMAGE_FORMAT);
			return this.getPageImageInputStream(this.doc.getInputStream(name), piPageIdStr);
		}
		private PageImageInputStream getPageImageInputStream(InputStream in, String piPageIdStr) throws IOException {
			
			//	test for PNG byte order mark
			PeekInputStream peekIn = new PeekInputStream(in, pngSignature.length);
			
			//	this one's the old way
			if (!peekIn.startsWith(pngSignature)) {
				PageImageInputStream piis = new PageImageInputStream(peekIn, this);
				return piis;
			}
			
			//	get attributes TODO consider parsing on demand, saving effort with large documents of which only an excerpt is used
			PageImageAttributes piAttributes = this.getPageImageAttributes(new Integer(piPageIdStr));
			if (piAttributes == null) {
				peekIn.close();
				throw new FileNotFoundException("page" + piPageIdStr + "." + IMAGE_FORMAT);
			}
			
			//	wrap and return page image input stream
			return piAttributes.wrap(peekIn, this);
		}
		public boolean storePageImage(String name, PageImage pageImage) throws IOException {
			if (!name.startsWith(this.doc.docId))
				return false;
			String piPageIdStr = name.substring(this.doc.docId.length() + ".".length());
			name = ("page" + piPageIdStr + "." + IMAGE_FORMAT);
			OutputStream out = this.doc.getOutputStream(name, true);
			pageImage.writeImage(out);
			out.close();
			Integer pageId = new Integer(piPageIdStr);
			PageImageAttributes piAttributes = this.getPageImageAttributes(pageId);
			if (piAttributes == null) {
				piAttributes = new PageImageAttributes(pageImage);
				this.pageImageAttributesById.put(pageId, piAttributes);
			}
			else piAttributes.updateAttributes(pageImage);
			return true;
		}
		public int getPriority() {
			return 10; // we only want page images for one document, but those we really do want
		}
		PageImageAttributes getPageImageAttributes(Integer pageId) {
			return ((PageImageAttributes) this.pageImageAttributesById.get(pageId));
		}
	};
	
	private static class PageImageAttributes {
		int originalWidth = -1;
		int originalHeight = -1;
		int originalDpi = -1;
		int currentDpi = -1;
		int leftEdge = 0;
		int rightEdge = 0;
		int topEdge = 0;
		int bottomEdge = 0;
		byte[] piAttributeBytes = new byte[16];
		PageImageAttributes(String piAttributes) {
			
			//	read attribute string
			String[] piAttributeNameValuePairs = piAttributes.split("[\\<\\>]");
			for (int a = 0; a < (piAttributeNameValuePairs.length-1); a+= 2) {
				if ("originalWidth".equals(piAttributeNameValuePairs[a]))
					this.originalWidth = Integer.parseInt(piAttributeNameValuePairs[a+1]);
				else if ("originalHeight".equals(piAttributeNameValuePairs[a]))
					this.originalHeight = Integer.parseInt(piAttributeNameValuePairs[a+1]);
				else if ("originalDpi".equals(piAttributeNameValuePairs[a]))
					this.originalDpi = Integer.parseInt(piAttributeNameValuePairs[a+1]);
				else if ("currentDpi".equals(piAttributeNameValuePairs[a]))
					this.currentDpi = Integer.parseInt(piAttributeNameValuePairs[a+1]);
				else if ("leftEdge".equals(piAttributeNameValuePairs[a]))
					this.leftEdge = Integer.parseInt(piAttributeNameValuePairs[a+1]);
				else if ("rightEdge".equals(piAttributeNameValuePairs[a]))
					this.rightEdge = Integer.parseInt(piAttributeNameValuePairs[a+1]);
				else if ("topEdge".equals(piAttributeNameValuePairs[a]))
					this.topEdge = Integer.parseInt(piAttributeNameValuePairs[a+1]);
				else if ("bottomEdge".equals(piAttributeNameValuePairs[a]))
					this.bottomEdge = Integer.parseInt(piAttributeNameValuePairs[a+1]);
			}
			
			//	default current DPI to original DPI if not given explicitly
			if (this.currentDpi == -1)
				this.currentDpi = this.originalDpi;
			
			//	wrap attribute values in byte array
			this.updateAttributeBytes();
		}
		
		PageImageAttributes(PageImage pi) {
			this.updateAttributes(pi);
		}
		
		void updateAttributes(PageImage pi) {
			this.originalWidth = pi.originalWidth;
			this.originalHeight = pi.originalHeight;
			this.originalDpi = pi.originalDpi;
			this.currentDpi = pi.currentDpi;
			this.leftEdge = pi.leftEdge;
			this.rightEdge = pi.rightEdge;
			this.topEdge = pi.topEdge;
			this.bottomEdge = pi.bottomEdge;
			this.updateAttributeBytes();
		}
		private void updateAttributeBytes() {
			this.piAttributeBytes[0] = ((byte) ((this.originalWidth / 256)));
			this.piAttributeBytes[1] = ((byte) ((this.originalWidth & 255)));
			this.piAttributeBytes[2] = ((byte) ((this.originalHeight / 256)));
			this.piAttributeBytes[3] = ((byte) ((this.originalHeight & 255)));
			this.piAttributeBytes[4] = ((byte) ((this.originalDpi / 256)));
			this.piAttributeBytes[5] = ((byte) ((this.originalDpi & 255)));
			this.piAttributeBytes[6] = ((byte) ((this.currentDpi / 256)));
			this.piAttributeBytes[7] = ((byte) ((this.currentDpi & 255)));
			this.piAttributeBytes[8] = ((byte) ((this.leftEdge / 256)));
			this.piAttributeBytes[9] = ((byte) ((this.leftEdge & 255)));
			this.piAttributeBytes[10] = ((byte) ((this.rightEdge / 256)));
			this.piAttributeBytes[11] = ((byte) ((this.rightEdge & 255)));
			this.piAttributeBytes[12] = ((byte) ((this.topEdge / 256)));
			this.piAttributeBytes[13] = ((byte) ((this.topEdge & 255)));
			this.piAttributeBytes[14] = ((byte) ((this.bottomEdge / 256)));
			this.piAttributeBytes[15] = ((byte) ((this.bottomEdge & 255)));
		}
		
		PageImageInputStream wrap(InputStream in, PageImageSource pis) throws IOException {
			return new PageImageInputStream(new SequenceInputStream(new ByteArrayInputStream(this.piAttributeBytes), in), pis);
		}
		
		String toAttributeString() {
			StringBuffer piAttributes = new StringBuffer();
			piAttributes.append("originalWidth<" + this.originalWidth + ">");
			piAttributes.append("originalHeight<" + this.originalHeight + ">");
			piAttributes.append("originalDpi<" + this.originalDpi + ">");
			if (this.currentDpi != this.originalDpi)
				piAttributes.append("currentDpi<" + this.currentDpi + ">");
			if (this.leftEdge != 0)
				piAttributes.append("leftEdge<" + this.leftEdge + ">");
			if (this.rightEdge != 0)
				piAttributes.append("rightEdge<" + this.rightEdge + ">");
			if (this.topEdge != 0)
				piAttributes.append("topEdge<" + this.topEdge + ">");
			if (this.bottomEdge != 0)
				piAttributes.append("bottomEdge<" + this.bottomEdge + ">");
			return piAttributes.toString();
		}
	}
	
	/* we need to hard-code the first byte 0x89 ('‰' on ISO-8859) converts
	 * differently on MacRoman and other encodings if embedded as a string
	 * constant in the code */
	private static final byte[] pngSignature = {((byte) 0x89), ((byte) 'P'), ((byte) 'N'), ((byte) 'G')};
	
	private static InputStream getInputStream(String entryName, HashMap cache, File cacheFolder, File folder, Map imfEntries) throws IOException {
		
		//	we're in zip-based im-memory mode
		if (cache != null) {
			byte[] entryBytes = ((byte[]) cache.get(entryName));
			if (entryBytes == null)
				throw new FileNotFoundException(entryName);
			else return new ByteArrayInputStream(entryBytes);
		}
		
		//	we're in zip-based folder mode
		if (cacheFolder != null)
			return new BufferedInputStream(new FileInputStream(new File(cacheFolder, entryName)));
		
		//	we're in folder mode
		if (folder != null) {
			ImfEntry imfe = ((ImfEntry) imfEntries.get(entryName));
			if (imfe == null)
				throw new FileNotFoundException(entryName);
			else return new BufferedInputStream(new FileInputStream(new File(folder, imfe.getFileName())));
		}
		
		//	whatever went wrong ...
		throw new FileNotFoundException(entryName);
	}
	
	private static OutputStream getOutputStream(final String entryName, final HashMap cache, final File cacheFolder, final File folder, boolean writeDirectly, final Map imfEntries) throws IOException {
		
		//	we're in zip-based im-memory mode
		if (cache != null)
			return new ByteArrayOutputStream() {
				public void close() throws IOException {
					super.close();
					cache.put(entryName, this.toByteArray());
				}
			};
		
		//	we're in zip-based folder mode
		if (cacheFolder != null) {
			final File newFile = new File(cacheFolder, (entryName + ".new"));
			return new BufferedOutputStream(new FileOutputStream(newFile) {
				public void close() throws IOException {
					super.flush();
					super.close();
					File exFile = new File(cacheFolder, entryName);
					if (exFile.exists() && newFile.exists() && newFile.getName().endsWith(".new"))
						exFile.renameTo(new File(cacheFolder, (entryName + "." + System.currentTimeMillis() + ".old")));
					newFile.renameTo(new File(cacheFolder, entryName));
				}
			});
		}
		
		//	we're in folder mode (write directly if told so, e.g. for page images, or if too little memory available for buffering)
		if (folder != null) {
			if (writeDirectly)
				return new DirectHashOutputStream(folder, entryName) {
					public void close() throws IOException {
						super.flush();
						super.close();
						ImfEntry newImfe = new ImfEntry(this.outFile);
						imfEntries.put(newImfe.name, newImfe);
					}
				};
			else return new MemoryHashOutputStream(folder, entryName) {
				public void close() throws IOException {
					super.flush();
					super.close();
					ImfEntry newImfe = new ImfEntry(this.file);
					imfEntries.put(newImfe.name, newImfe);
				}
			};
		}
		
		//	whatever went wrong ...
		throw new FileNotFoundException(entryName);
	}
	
	/**
	 * Store an image markup document to a file. If the argument file is an
	 * actual file, this method zips up the document and stores it in that
	 * file. If the argument file actually is a folder, on the other hand, this
	 * method does not zip up the individual entries, but stores them in the
	 * argument folder as individual files. If the argument file does not
	 * exist, it is created and treated as a file. When storing to an actual
	 * file, this method returns null. When storing to a folder, the returned
	 * array of IMF entries describes the mapping of logical file names (the
	 * entry names) to physical file names, whose name includes the MD5 of the
	 * file content. The latter is to avoid duplication to as high a degree as
	 * possible, while still preserving files that are logically overwritten.
	 * @param doc the image markup document to store.
	 * @param file the file or folder to store the document to
	 * @return an array describing the entries (in folder storage mode)
	 * @throws IOException
	 */
	public static ImfEntry[] storeDocument(ImDocument doc, File file) throws IOException {
		return storeDocument(doc, file, null);
	}
	
	/**
	 * Store an image markup document to a file. If the argument file is an
	 * actual file, this method zips up the document and stores it in that
	 * file. If the argument file actually is a folder, on the other hand, this
	 * method does not zip up the individual entries, but stores them in the
	 * argument folder as individual files. If the argument file does not
	 * exist, it is created and treated as a file. When storing to an actual
	 * file, this method returns null. When storing to a folder, the returned
	 * array of IMF entries describes the mapping of logical file names (the
	 * entry names) to physical file names, whose name includes the MD5 of the
	 * file content. The latter is to avoid duplication to as high a degree as
	 * possible, while still preserving files that are logically overwritten.
	 * @param doc the image markup document to store.
	 * @param file the file or folder to store the document to
	 * @param pm a progress monitor to observe the storage process
	 * @return an array describing the entries (in folder storage mode)
	 * @throws IOException
	 */
	public static ImfEntry[] storeDocument(ImDocument doc, File file, ProgressMonitor pm) throws IOException {
		
		//	file does not exist, create it as a file
		if (!file.exists()) {
			file.getAbsoluteFile().getParentFile().mkdirs();
			file.createNewFile();
		}
		
		//	we have a directory, store document without zipping
		if (file.isDirectory()) {
			return storeDocument(doc, null, file, pm);
		}
		
		//	we have an actual file (maybe of our own creation), zip up document
		else return storeDocument(doc, new BufferedOutputStream(new FileOutputStream(file)), null, pm);
	}
	
	/**
	 * Store an image markup document to an output stream. The argument output
	 * stream is flushed and closed at the end of this method.
	 * @param doc the image markup document to store.
	 * @param out the output stream to store the document to
	 * @throws IOException
	 */
	public static void storeDocument(ImDocument doc, OutputStream out) throws IOException {
		storeDocument(doc, out, null);
	}
	
	/**
	 * Store an image markup document to an output stream. The argument output
	 * stream is flushed and closed at the end of this method.
	 * @param doc the image markup document to store.
	 * @param out the output stream to store the document to
	 * @param pm a progress monitor to observe the storage process
	 * @throws IOException
	 */
	public static void storeDocument(ImDocument doc, OutputStream out, ProgressMonitor pm) throws IOException {
		storeDocument(doc, out, null, pm);
	}
	
	private static ImfEntry[] storeDocument(ImDocument doc, OutputStream out, File folder, ProgressMonitor pm) throws IOException {
		
		//	check progress monitor
		if (pm == null)
			pm = ProgressMonitor.dummy;
		
		//	check memory (estimate 100 bytes per word, 1000 words per page, should be on the safe side)
		boolean lowMemory = (Runtime.getRuntime().freeMemory() < (100 * 1000 * doc.getPageCount()));
		if (lowMemory) {
			System.gc();
			lowMemory = (Runtime.getRuntime().freeMemory() < (100 * 1000 * doc.getPageCount()));
		}
		
		//	get ready to zip, and to collect IMF entries
		ZipOutputStream zout = ((out == null) ? null : new ZipOutputStream(out));
		Map imfEntries = ((out == null) ? new LinkedHashMap() : null);
		BufferedWriter zbw;
		StringVector keys = new StringVector();
		
		//	store document meta data
		pm.setStep("Storing document data");
		pm.setBaseProgress(0);
		pm.setMaxProgress(5);
		zbw = getWriter(zout, doc, folder, imfEntries, "document.csv", false);
		keys.clear();
		keys.parseAndAddElements((DOCUMENT_ID_ATTRIBUTE + ";" + ImObject.ATTRIBUTES_STRING_ATTRIBUTE), ";");
		writeKeys(keys, zbw);
		StringTupel docData = new StringTupel();
		docData.setValue(DOCUMENT_ID_ATTRIBUTE, doc.docId);
		docData.setValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, getAttributesString(doc));
		writeValues(keys, docData, zbw);
		zbw.close();
		
		//	assemble data for pages, words, and regions
		pm.setStep("Storing page data");
		pm.setBaseProgress(5);
		pm.setMaxProgress(8);
		ImPage[] pages = doc.getPages();
		zbw = getWriter(zout, doc, folder, imfEntries, "pages.csv", false);
		keys.clear();
		keys.parseAndAddElements((PAGE_ID_ATTRIBUTE + ";" + BOUNDING_BOX_ATTRIBUTE + ";" + ImObject.ATTRIBUTES_STRING_ATTRIBUTE), ";");
		writeKeys(keys, zbw);
		for (int p = 0; p < pages.length; p++) {
			pm.setInfo("Page " + p);
			pm.setProgress((p * 100) / pages.length);
			
			//	data for page
			StringTupel pageData = new StringTupel();
			pageData.setValue(PAGE_ID_ATTRIBUTE, ("" + pages[p].pageId));
			pageData.setValue(BOUNDING_BOX_ATTRIBUTE, pages[p].bounds.toString());
			pageData.setValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, getAttributesString(pages[p]));
			writeValues(keys, pageData, zbw);
		}
		zbw.close();
		
		//	store words
		pm.setStep("Storing word data");
		pm.setBaseProgress(8);
		pm.setMaxProgress(17);
		zbw = getWriter(zout, doc, folder, imfEntries, "words.csv", lowMemory);
		keys.clear();
		keys.parseAndAddElements((PAGE_ID_ATTRIBUTE + ";" + BOUNDING_BOX_ATTRIBUTE + ";" + STRING_ATTRIBUTE + ";" + ImWord.PREVIOUS_WORD_ATTRIBUTE + ";" + ImWord.NEXT_WORD_ATTRIBUTE + ";" + ImWord.NEXT_RELATION_ATTRIBUTE + ";" + ImWord.TEXT_STREAM_TYPE_ATTRIBUTE + ";" + ImObject.ATTRIBUTES_STRING_ATTRIBUTE), ";");
		writeKeys(keys, zbw);
		for (int p = 0; p < pages.length; p++) {
			pm.setInfo("Page " + p);
			pm.setProgress((p * 100) / pages.length);
			
			//	data for words
			ImWord[] pageWords = pages[p].getWords();
			Arrays.sort(pageWords, ImUtils.textStreamOrder); // some effort, but negligible in comparison to image handling, and helps a lot reading word table
			for (int w = 0; w < pageWords.length; w++) {
				StringTupel wordData = new StringTupel();
				wordData.setValue(PAGE_ID_ATTRIBUTE, ("" + pageWords[w].pageId));
				wordData.setValue(BOUNDING_BOX_ATTRIBUTE, pageWords[w].bounds.toString());
				wordData.setValue(STRING_ATTRIBUTE, pageWords[w].getString());
				if (pageWords[w].getPreviousWord() != null)
					wordData.setValue(ImWord.PREVIOUS_WORD_ATTRIBUTE, pageWords[w].getPreviousWord().getLocalID());
				else wordData.setValue(ImWord.TEXT_STREAM_TYPE_ATTRIBUTE, pageWords[w].getTextStreamType());
				if (pageWords[w].getNextWord() != null)
					wordData.setValue(ImWord.NEXT_WORD_ATTRIBUTE, pageWords[w].getNextWord().getLocalID());
				wordData.setValue(ImWord.NEXT_RELATION_ATTRIBUTE, ("" + pageWords[w].getNextRelation()));
				wordData.setValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, getAttributesString(pageWords[w]));
				writeValues(keys, wordData, zbw);
			}
		}
		zbw.close();
		
		//	store regions
		pm.setStep("Storing region data");
		pm.setBaseProgress(17);
		pm.setMaxProgress(20);
		zbw = getWriter(zout, doc, folder, imfEntries, "regions.csv", lowMemory);
		keys.clear();
		keys.parseAndAddElements((PAGE_ID_ATTRIBUTE + ";" + BOUNDING_BOX_ATTRIBUTE + ";" + TYPE_ATTRIBUTE + ";" + ImObject.ATTRIBUTES_STRING_ATTRIBUTE), ";");
		writeKeys(keys, zbw);
		for (int p = 0; p < pages.length; p++) {
			pm.setInfo("Page " + p);
			pm.setProgress((p * 100) / pages.length);
			
			//	data for regions
			ImRegion[] pageRegs = pages[p].getRegions();
			for (int r = 0; r < pageRegs.length; r++) {
				StringTupel regData = new StringTupel();
				regData.setValue(PAGE_ID_ATTRIBUTE, ("" + pageRegs[r].pageId));
				regData.setValue(BOUNDING_BOX_ATTRIBUTE, pageRegs[r].bounds.toString());
				regData.setValue(TYPE_ATTRIBUTE, pageRegs[r].getType());
				regData.setValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, getAttributesString(pageRegs[r]));
				writeValues(keys, regData, zbw);
			}
		}
		zbw.close();
		
		//	store annotations
		pm.setStep("Storing annotation data");
		pm.setBaseProgress(20);
		pm.setMaxProgress(25);
		zbw = getWriter(zout, doc, folder, imfEntries, "annotations.csv", lowMemory);
		keys.clear();
		keys.parseAndAddElements((ImAnnotation.FIRST_WORD_ATTRIBUTE + ";" + ImAnnotation.LAST_WORD_ATTRIBUTE + ";" + TYPE_ATTRIBUTE + ";" + ImObject.ATTRIBUTES_STRING_ATTRIBUTE), ";");
		writeKeys(keys, zbw);
		ImAnnotation[] annots = doc.getAnnotations();
		for (int a = 0; a < annots.length; a++) {
			pm.setProgress((a * 100) / annots.length);
			StringTupel annotData = new StringTupel();
			annotData.setValue(ImAnnotation.FIRST_WORD_ATTRIBUTE, annots[a].getFirstWord().getLocalID());
			annotData.setValue(ImAnnotation.LAST_WORD_ATTRIBUTE, annots[a].getLastWord().getLocalID());
			annotData.setValue(TYPE_ATTRIBUTE, annots[a].getType());
			annotData.setValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, getAttributesString(annots[a]));
			writeValues(keys, annotData, zbw);
		}
		zbw.close();
		
		//	store fonts (if any)
		pm.setStep("Storing font data");
		pm.setBaseProgress(25);
		pm.setMaxProgress(30);
		zbw = getWriter(zout, doc, folder, imfEntries, "fonts.csv", false);
		keys.clear();
		keys.parseAndAddElements((ImFont.NAME_ATTRIBUTE + ";" + ImFont.STYLE_ATTRIBUTE + ";" + ImFont.CHARACTER_ID_ATTRIBUTE + ";" + ImFont.CHARACTER_STRING_ATTRIBUTE + ";" + ImFont.CHARACTER_IMAGE_ATTRIBUTE), ";");
		writeKeys(keys, zbw);
		ImFont[] fonts = doc.getFonts();
		for (int f = 0; f < fonts.length; f++) {
			pm.setProgress((f * 100) / fonts.length);
			String fontStyle = "";
			if (fonts[f].isBold())
				fontStyle += "B";
			if (fonts[f].isItalics())
				fontStyle += "I";
			if (fonts[f].isSerif())
				fontStyle += "S";
			int[] charIDs = fonts[f].getCharacterIDs();
			for (int c = 0; c < charIDs.length; c++) {
				StringTupel charData = new StringTupel();
				charData.setValue(ImFont.NAME_ATTRIBUTE, fonts[f].name);
				charData.setValue(ImFont.STYLE_ATTRIBUTE, fontStyle);
				charData.setValue(ImFont.CHARACTER_ID_ATTRIBUTE, Integer.toString(charIDs[c], 16).toUpperCase());
				String charStr = fonts[f].getString(charIDs[c]);
				if (charStr != null)
					charData.setValue(ImFont.CHARACTER_STRING_ATTRIBUTE, charStr);
				String charImageHex = fonts[f].getImageHex(charIDs[c]);
				if (charImageHex != null)
					charData.setValue(ImFont.CHARACTER_IMAGE_ATTRIBUTE, charImageHex);
				writeValues(keys, charData, zbw);
			}
		}
		zbw.close();
		
		//	store page images
		pm.setStep("Storing page images");
		pm.setBaseProgress(30);
		pm.setMaxProgress(79);
		StringRelation pageImagesData = new StringRelation();
		for (int p = 0; p < pages.length; p++) {
			pm.setInfo("Page " + p);
			pm.setProgress((p * 100) / pages.length);
			String piAttributes;
			
			/* we have to write the page image proper if
			 * - we're zipping up the document
			 * - the document was loaded via other facilities
			 * - the page image store has been replaced
			 * - the document was loaded from, and is still bound to, its zipped-up form
			 * - the document was loaded from, and is still bound to, a different folder */
			if ((folder == null) || !(doc instanceof ImfIoDocument) || (((ImfIoDocument) doc).imfIoPis == null) || (((ImfIoDocument) doc).folder == null) || !folder.equals(((ImfIoDocument) doc).folder)) {
				PageImageInputStream piis = pages[p].getPageImageAsStream();
				piAttributes = getPageImageAttributes(piis);
				String piName = PageImage.getPageImageName(doc.docId, pages[p].pageId);
				piName = ("page" + piName.substring(doc.docId.length() + ".".length()) + "." + IMAGE_FORMAT);
				OutputStream piOut = getOutputStream(zout, doc, folder, imfEntries, piName, true);
				byte[] pib = new byte[1024];
				for (int r; (r = piis.read(pib, 0, pib.length)) != -1;)
					piOut.write(pib, 0, r);
				piOut.close();
				piis.close();
			}
			
			//	otherwise, we only have to get the attributes (the IMF entry already exists, as otherwise, we'd write the page image)
			else {
				PageImageAttributes pia = ((ImfIoDocument) doc).imfIoPis.getPageImageAttributes(new Integer(pages[p].pageId));
				if (pia == null) {
					PageImageInputStream piis = pages[p].getPageImageAsStream();
					piAttributes = getPageImageAttributes(piis);
					piis.close();
				}
				else piAttributes = pia.toAttributeString();
			}
			
			StringTupel piData = new StringTupel();
			piData.setValue(ImObject.PAGE_ID_ATTRIBUTE, ("" + pages[p].pageId));
			piData.setValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, piAttributes);
			pageImagesData.addElement(piData);
		}
		
		//	store page image data
		pm.setStep("Storing page image data");
		pm.setBaseProgress(79);
		pm.setMaxProgress(80);
		zbw = getWriter(zout, doc, folder, imfEntries, "pageImages.csv", false);
		keys.clear();
		keys.parseAndAddElements((ImObject.PAGE_ID_ATTRIBUTE + ";" + ImObject.ATTRIBUTES_STRING_ATTRIBUTE), ";");
		writeKeys(keys, zbw);
		for (int p = 0; p < pageImagesData.size(); p++)
			writeValues(keys, pageImagesData.get(p), zbw);
		zbw.close();
		
		//	store meta data of supplements
		pm.setStep("Storing supplement data");
		pm.setBaseProgress(80);
		pm.setMaxProgress(85);
		zbw = getWriter(zout, doc, folder, imfEntries, "supplements.csv", false);
		keys.clear();
		keys.parseAndAddElements((ImSupplement.ID_ATTRIBUTE + ";" + ImSupplement.TYPE_ATTRIBUTE + ";" + ImSupplement.MIME_TYPE_ATTRIBUTE + ";" + ImObject.ATTRIBUTES_STRING_ATTRIBUTE), ";");
		writeKeys(keys, zbw);
		ImSupplement[] supplements = doc.getSupplements();
		for (int s = 0; s < supplements.length; s++) {
			StringTupel supplementData = new StringTupel();
			supplementData.setValue(ImSupplement.ID_ATTRIBUTE, supplements[s].getId());
			supplementData.setValue(ImSupplement.TYPE_ATTRIBUTE, supplements[s].getType());
			supplementData.setValue(ImSupplement.MIME_TYPE_ATTRIBUTE, supplements[s].getMimeType());
			supplementData.setValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, getAttributesString(supplements[s]));
			writeValues(keys, supplementData, zbw);
		}
		zbw.close();
		
		//	store supplements proper
		pm.setStep("Storing supplements");
		pm.setBaseProgress(85);
		pm.setMaxProgress(100);
		for (int s = 0; s < supplements.length; s++) {
			pm.setProgress((s * 100) / supplements.length);
			String smt = supplements[s].getMimeType();
			String sfn = (supplements[s].getId() + "." + smt.substring(smt.lastIndexOf('/') + "/".length()));
			pm.setInfo("Supplement " + sfn);
			
			/* we have to write the supplement proper if
			 * - we're zipping up the document
			 * - the document was loaded via other facilities
			 * - the document was loaded from, and is still bound to, its zipped-up form
			 * - the document was loaded from, and is still bound to, a different folder
			 * - the supplement was newly added or modified */
			if ((folder == null) || !(doc instanceof ImfIoDocument) || (((ImfIoDocument) doc).folder == null) || !folder.equals(((ImfIoDocument) doc).folder) || (((ImfIoDocument) doc).getImfEntry(supplements[s]) == null)) {
				InputStream sdIn = supplements[s].getInputStream();
				OutputStream sdOut = getOutputStream(zout, doc, folder, imfEntries, sfn, true);
				byte[] sdb = new byte[1024];
				for (int r; (r = sdIn.read(sdb, 0, sdb.length)) != -1;)
					sdOut.write(sdb, 0, r);
				sdOut.close();
				sdIn.close();
			}
		}
		
		//	finally
		pm.setProgress(100);
		
		//	finalize folder storage
		if (folder != null) {
			ImfEntry[] imfes = ((ImfEntry[]) imfEntries.values().toArray(new ImfEntry[imfEntries.size()]));
			
			//	if the document was loaded here, update IMF entries (both ways), and switch document to folder mode
			if (doc instanceof ImfIoDocument) {
				
				/* bind document to folder (we can update entries in document
				 * first, as we've externally written to the argument folder
				 * what was not in the document already, so the files are sure
				 * to exist) */
				((ImfIoDocument) doc).bindToFolder(folder, imfEntries);
				
				/* update IMF entries (if the document was already bound to the
				 * argument folder before, we might well have skipped over both
				 * supplements and page images) */
				imfEntries.putAll(((ImfIoDocument) doc).imfEntries);
			}
			
			/* write or overwrite 'enries.txt' (specifying document folder as
			 * 'cache folder' gives us file renaming instead of hashing
			 * behavior used in zip-based folder mode , which is exactly what
			 * we need here) */
			BufferedWriter imfEntryOut = new BufferedWriter(new OutputStreamWriter(getOutputStream("entries.txt", null, folder, null, true, null), "UTF-8"));
			for (Iterator enit = imfEntries.keySet().iterator(); enit.hasNext();) {
				String imfen = ((String) enit.next());
				ImfEntry imfe = ((ImfEntry) imfEntries.get(imfen));
				imfEntryOut.write(imfe.toTabString());
				imfEntryOut.newLine();
			}
			imfEntryOut.flush();
			imfEntryOut.close();
			
			//	return entry list for external use
			return imfes;
		}
		
		//	finalize zip storage
		if (zout != null) {
			zout.flush();
			zout.close();
			return null;
		}
		
		//	whatever ...
		return null;
	}
	
	private static void writeKeys(StringVector keys, BufferedWriter zbw) throws IOException {
		for (int k = 0; k < keys.size(); k++) {
			if (k != 0)
				zbw.write(',');
			zbw.write('"');
			zbw.write(keys.get(k));
			zbw.write('"');
		}
		zbw.newLine();
	}
	
	private static void writeValues(StringVector keys, StringTupel st, BufferedWriter zbw) throws IOException {
		for (int k = 0; k < keys.size(); k++) {
			if (k != 0)
				zbw.write(',');
			zbw.write('"');
			String value = st.getValue(keys.get(k), "");
			for (int c = 0; c < value.length(); c++) {
				char ch = value.charAt(c);
				if (ch == '"')
					zbw.write('"');
				zbw.write(ch);
			}
			zbw.write('"');
		}
		zbw.newLine();
	}
	
	private static OutputStream getOutputStream(final ZipOutputStream zout, ImDocument doc, final File folder, final Map imfEntries, final String entryName, boolean writeDirectly) throws IOException {
		
		//	we're zipping up the document, doesn't matter what the document is like
		if (zout != null) {
			zout.putNextEntry(new ZipEntry(entryName));
			return new BufferedOutputStream(zout) {
				public void close() throws IOException {
					super.flush();
					zout.closeEntry();
				}
			};
		}
		
		//	document loaded from same folder as argument one
		if ((doc instanceof ImfIoDocument) && (((ImfIoDocument) doc).folder != null) && ((ImfIoDocument) doc).folder.equals(folder))
			return ((ImfIoDocument) doc).getOutputStream(entryName, writeDirectly);
		
		//	document loaded from other facilities, from different folder, or from ZIP
		if (folder != null)
			return new DirectHashOutputStream(folder, entryName) {
				public void close() throws IOException {
					super.flush();
					super.close();
					ImfEntry newImfe = new ImfEntry(this.outFile);
					imfEntries.put(newImfe.name, newImfe);
				}
			};
		
		//	whatever went wrong ..
		throw new FileNotFoundException(entryName);
	}
	
	private static class DirectHashOutputStream extends BufferedOutputStream {
		private MessageDigest dataHash = getDataHash();
		private File outFolder;
		private String outFileName;
		private String outFileExtension;
		private File outFileWriting;
		File outFile;
		DirectHashOutputStream(File outFolder, String outEntryName) throws IOException {
			super(null);
			this.outFolder = outFolder;
			if (outEntryName.indexOf('.') == -1) {
				this.outFileName = outEntryName;
				this.outFileExtension = null;
			}
			else {
				this.outFileName = outEntryName.substring(0, outEntryName.lastIndexOf('.'));
				this.outFileExtension = outEntryName.substring(outEntryName.lastIndexOf('.'));
			}
			this.outFileWriting = new File(this.outFolder, (this.outFileName + ".writing" + ((this.outFileExtension == null) ? "" : this.outFileExtension)));
			this.out = new FileOutputStream(this.outFileWriting);
		}
		public synchronized void write(int b) throws IOException {
			super.write(b);
			this.dataHash.update((byte) b);
		}
		public synchronized void write(byte[] b, int off, int len) throws IOException {
			super.write(b, off, len);
			this.dataHash.update(b, off, len);
		}
		public void close() throws IOException {
			super.close();
			
			//	finalize hash and rename file
			this.outFile = new File(this.outFolder, (this.outFileName + "." + new String(RandomByteSource.getHexCode(this.dataHash.digest())) + ((this.outFileExtension == null) ? "" : this.outFileExtension)));
			if (this.outFile.exists())
				this.outFileWriting.delete();
			else this.outFileWriting.renameTo(this.outFile);
			
			//	return digester to instance pool
			returnDataHash(this.dataHash);
			this.dataHash = null;
		}
	}
	
	private static class MemoryHashOutputStream extends ByteArrayOutputStream {
		private MessageDigest dataHash = getDataHash();
		private File folder;
		private String fileName;
		private String fileExtension;
		File file;
		MemoryHashOutputStream(File folder, String name) {
			this.folder = folder;
			if (name.indexOf('.') == -1) {
				this.fileName = name;
				this.fileExtension = null;
			}
			else {
				this.fileName = name.substring(0, name.lastIndexOf('.'));
				this.fileExtension = name.substring(name.lastIndexOf('.'));
			}
		}
		public synchronized void write(int b) {
			super.write(b);
			this.dataHash.update((byte) b);
		}
		public synchronized void write(byte[] b, int off, int len) {
			super.write(b, off, len);
			this.dataHash.update(b, off, len);
		}
		public void close() throws IOException {
			super.close();
			
			//	write buffer content to persistent storage only if not already there
			this.file = new File(this.folder, (this.fileName + "." + new String(RandomByteSource.getHexCode(this.dataHash.digest())) + ((this.fileExtension == null) ? "" : this.fileExtension)));
			if (!this.file.exists()) {
				BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(this.file));
				this.writeTo(out);
				out.flush();
				out.close();
			}
			
			//	return digester to instance pool
			returnDataHash(this.dataHash);
			this.dataHash = null;
		}
	}
	
	private static LinkedList dataHashPool = new LinkedList();
	private static synchronized MessageDigest getDataHash() {
		if (dataHashPool.size() != 0)
			return ((MessageDigest) dataHashPool.removeFirst());
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
	
	private static BufferedWriter getWriter(final ZipOutputStream zout, ImDocument doc, File folder, Map imfEntries, String name, boolean writeDirectly) throws IOException {
		return new BufferedWriter(new OutputStreamWriter(getOutputStream(zout, doc, folder, imfEntries, name, writeDirectly), "UTF-8"));
	}
	
	private static String getAttributesString(ImObject imo) {
		StringBuffer attributes = new StringBuffer();
		String[] ans = imo.getAttributeNames();
		for (int a = 0; a < ans.length; a++) {
			Object value = imo.getAttribute(ans[a]);
			if (value != null) try {
				attributes.append(ans[a] + "<" + escape(value.toString()) + ">");
			} catch (RuntimeException re) {}
		}
		return attributes.toString();
	}
	private static String escape(String string) {
		StringBuffer escapedString = new StringBuffer();
		for (int c = 0; c < string.length(); c++) {
			char ch = string.charAt(c);
			if (ch == '<')
				escapedString.append("&lt;");
			else if (ch == '>')
				escapedString.append("&gt;");
			else if (ch == '"')
				escapedString.append("&quot;");
			else if (ch == '&')
				escapedString.append("&amp;");
			else if ((ch < 32) || (ch == 127))
				escapedString.append("&x" + Integer.toString(((int) ch), 16).toUpperCase() + ";");
			else escapedString.append(ch);
		}
		return escapedString.toString();
	}
	
	private static String getPageImageAttributes(PageImageInputStream piis) {
		StringBuffer piAttributes = new StringBuffer();
		piAttributes.append("originalWidth<" + piis.originalWidth + ">");
		piAttributes.append("originalHeight<" + piis.originalHeight + ">");
		piAttributes.append("originalDpi<" + piis.originalDpi + ">");
		if (piis.currentDpi != piis.originalDpi)
			piAttributes.append("currentDpi<" + piis.currentDpi + ">");
		if (piis.leftEdge != 0)
			piAttributes.append("leftEdge<" + piis.leftEdge + ">");
		if (piis.rightEdge != 0)
			piAttributes.append("rightEdge<" + piis.rightEdge + ">");
		if (piis.topEdge != 0)
			piAttributes.append("topEdge<" + piis.topEdge + ">");
		if (piis.bottomEdge != 0)
			piAttributes.append("bottomEdge<" + piis.bottomEdge + ">");
		return piAttributes.toString();
	}
	
	private static void setAttributes(ImObject imo, String attributes) {
		String[] nameValuePairs = attributes.split("[\\<\\>]");
		for (int p = 0; p < (nameValuePairs.length-1); p+= 2)
			imo.setAttribute(nameValuePairs[p], unescape(nameValuePairs[p+1]));
	}
	private static String unescape(String escapedString) {
		StringBuffer string = new StringBuffer();
		for (int c = 0; c < escapedString.length();) {
			char ch = escapedString.charAt(c);
			if (ch == '&') {
				if (escapedString.startsWith("amp;", (c+1))) {
					string.append('&');
					c+=5;
				}
				else if (escapedString.startsWith("lt;", (c+1))) {
					string.append('<');
					c+=4;
				}
				else if (escapedString.startsWith("gt;", (c+1))) {
					string.append('>');
					c+=4;
				}
				else if (escapedString.startsWith("quot;", (c+1))) {
					string.append('"');
					c+=6;
				}
				else if (escapedString.startsWith("x", (c+1))) {
					int sci = escapedString.indexOf(';', (c+1));
					if ((sci != -1) && (sci <= (c+4))) try {
						ch = ((char) Integer.parseInt(escapedString.substring((c+2), sci), 16));
						c = sci;
					} catch (Exception e) {}
					string.append(ch);
					c++;
				}
				else {
					string.append(ch);
					c++;
				}
			}
			else {
				string.append(ch);
				c++;
			}
		}
		return string.toString();
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		final File baseFolder = new File("E:/Testdaten/PdfExtract/");
		final String pdfName = "zt00872.pdf";
		
		ImDocument imDoc = loadDocument(new FileInputStream(new File(baseFolder, (pdfName + ".imf"))));
//		ImDocument imDoc = loadDocument(new File(baseFolder, (pdfName + ".new.imd")));
		
		File imFile = new File(baseFolder, (pdfName + ".new.imf"));
		OutputStream imOut = new BufferedOutputStream(new FileOutputStream(imFile));
		storeDocument(imDoc, imOut);
		imOut.flush();
		imOut.close();
//		
//		File imFolder = new File(baseFolder, (pdfName + ".new.imd"));
//		imFolder.mkdirs();
//		storeDocument(imDoc, imFolder);
	}
}