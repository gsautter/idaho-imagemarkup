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
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.SequenceInputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import de.uka.ipd.idaho.easyIO.streams.PeekInputStream;
import de.uka.ipd.idaho.gamta.Attributed;
import de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed;
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
import de.uka.ipd.idaho.im.util.ImDocumentData.FolderImDocumentData;
import de.uka.ipd.idaho.im.util.ImDocumentData.ImDocumentEntry;
import de.uka.ipd.idaho.stringUtils.StringVector;
import de.uka.ipd.idaho.stringUtils.csvHandler.StringRelation;
import de.uka.ipd.idaho.stringUtils.csvHandler.StringTupel;

/**
 * IO utility for Image Markup documents. However, in favor of flexibility,
 * this class works on mainly document data objects. This is to abstract from
 * the exact location the file is loaded from or written to. All methods that
 * work on files or input or output streams are only wrappers.<br>
 * When storing an Image Markup document, this class writes (at least) the
 * following entries to a document data object:
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
 * In addition, it may also write the following optional entries:
 * <ul>
 * <li><b>fonts.csv</b>: a CSV file containing custom fonts if the Image Markup
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
 * Likewise, when loading an Image Markup document, this class expects at least
 * the entries to be present that it always writes on storing.
 * 
 * @author sautter
 */
public class ImDocumentIO implements ImagingConstants {
	
	/**
	 * Load an Image Markup document. If the argument file is an actual file,
	 * this method assumes it to be a zipped-up Image Markup File. If the file
	 * is a folder, however, this method assumes it to be an already un-zipped
	 * Image Markup File.
	 * @param file the file to load from
	 * @return an Image Markup document representing the content of the
	 *            argument stream.
	 * @throws IOException
	 */
	public static ImDocument loadDocument(File file) throws IOException {
		return loadDocument(file, null, null);
	}
	
	/**
	 * Load an Image Markup document. If the argument file is an actual file,
	 * this method assumes it to be a zipped-up Image Markup File. If the file
	 * is a folder, however, this method assumes it to be an already un-zipped
	 * Image Markup File. In the former case, the argument list of IMF entries
	 * is ignored. In the latter case, if the IMF entry list is null, this
	 * method expects a list named 'entries.txt' in the argument folder;
	 * otherwise, it uses the argument list.
	 * @param file the file to load from
	 * @param entries an array holding the IMF entries to use
	 * @return an Image Markup document representing the content of the
	 *            argument stream.
	 * @throws IOException
	 */
	public static ImDocument loadDocument(File file, ImDocumentEntry[] entries) throws IOException {
		return loadDocument(file, entries, null);
	}
	
	/**
	 * Load an Image Markup document. If the argument file is an actual file,
	 * this method assumes it to be a zipped-up Image Markup File. If the file
	 * is a folder, however, this method assumes it to be an already un-zipped
	 * Image Markup File.
	 * @param in the input stream to load from
	 * @param pm a progress monitor to observe the loading process
	 * @return an Image Markup document representing the content of the
	 *            argument stream.
	 * @throws IOException
	 */
	public static ImDocument loadDocument(File file, ProgressMonitor pm) throws IOException {
		return loadDocument(file, null, pm);
	}
	
	/**
	 * Load an Image Markup document. If the argument file is an actual file,
	 * this method assumes it to be a zipped-up Image Markup File. If the file
	 * is a folder, however, this method assumes it to be an already un-zipped
	 * Image Markup File. In the former case, the argument list of IMF entries
	 * is ignored. In the latter case, if the IMF entry list is null, this
	 * method expects a list named 'entries.txt' in the argument folder;
	 * otherwise, it uses the argument list.
	 * @param in the input stream to load from
	 * @param entries an array holding the IMF entries to use
	 * @param pm a progress monitor to observe the loading process
	 * @return an Image Markup document representing the content of the
	 *            argument stream.
	 * @throws IOException
	 */
	public static ImDocument loadDocument(File file, ImDocumentEntry[] entries, ProgressMonitor pm) throws IOException {
		
		//	assume folder to be un-zipped IMF
		if (file.isDirectory())
			return loadDocument(new FolderImDocumentData(file, entries), pm);
		
		//	assume file to be zipped-up IMF
		else {
			InputStream fis = new FileInputStream(file);
			ImDocument doc = loadDocument(fis, null, pm, ((int) file.length()));
			fis.close();
			return doc;
		}
	}
	
	/**
	 * Load an Image Markup document.
	 * @param in the input stream to load from
	 * @return an Image Markup document representing the content of the
	 *            argument stream.
	 * @throws IOException
	 */
	public static ImDocument loadDocument(InputStream in) throws IOException {
		return loadDocument(in, null, null);
	}
	
	/**
	 * Load an Image Markup document.
	 * @param in the input stream to load from
	 * @param pm a progress monitor to observe the loading process
	 * @return an Image Markup document representing the content of the
	 *            argument stream.
	 * @throws IOException
	 */
	public static ImDocument loadDocument(InputStream in, ProgressMonitor pm) throws IOException {
		return loadDocument(in, null, pm, -1);
	}
	
	/**
	 * Load an Image Markup document.
	 * @param in the input stream to load from
	 * @param pm a progress monitor to observe the loading process
	 * @param inLength the number of bytes to expect from the argument input stream
	 * @return an Image Markup document representing the content of the
	 *            argument stream.
	 * @throws IOException
	 */
	public static ImDocument loadDocument(InputStream in, ProgressMonitor pm, int inLength) throws IOException {
		return loadDocument(in, null, pm, inLength);
	}
	
	/**
	 * Load an Image Markup document. If the argument cache folder is null,
	 * page images will be cached in memory. For large documents, this can
	 * cause high memory consumption. The argument input stream is not closed.
	 * @param in the input stream to load from
	 * @param cacheFolder the folder to use for caching
	 * @return an Image Markup document representing the content of the
	 *            argument stream.
	 * @throws IOException
	 */
	public static ImDocument loadDocument(InputStream in, File cacheFolder) throws IOException {
		return loadDocument(in, cacheFolder, null, -1);
	}
	
	/**
	 * Load an Image Markup document. If the argument cache folder is null,
	 * page images will be cached in memory. For large documents, this can
	 * cause high memory consumption. The argument input stream is not closed.
	 * @param in the input stream to load from
	 * @param cacheFolder the folder to use for caching
	 * @param pm a progress monitor to observe the loading process
	 * @return an Image Markup document representing the content of the
	 *            argument stream.
	 * @throws IOException
	 */
	public static ImDocument loadDocument(InputStream in, File cacheFolder, ProgressMonitor pm) throws IOException {
		return loadDocument(in, cacheFolder, pm, -1);
	}
	
	/**
	 * Load an Image Markup document. If the argument cache folder is null,
	 * page images will be cached in memory. For large documents, this can
	 * cause high memory consumption. The argument input stream is not closed.
	 * @param in the input stream to load from
	 * @param cacheFolder the folder to use for caching
	 * @param pm a progress monitor to observe the loading process
	 * @param inLength the number of bytes to expect from the argument input stream
	 * @return an Image Markup document representing the content of the
	 *            argument stream.
	 * @throws IOException
	 */
	public static ImDocument loadDocument(InputStream in, File cacheFolder, ProgressMonitor pm, int inLength) throws IOException {
		
		//	check progress monitor
		if (pm == null)
			pm = ProgressMonitor.dummy;
		
		//	make reading observable if we know how many bytes to expect
		if (inLength != -1) {
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
		
		//	get ready to unzip
		ImDocumentData data = new ZipInImDocumentData(cacheFolder);
		ZipInputStream zin = new ZipInputStream(in);
		
		//	read and cache archive contents
		pm.setStep("Un-zipping & caching archive");
		pm.setBaseProgress(0);
		pm.setMaxProgress(50);
		for (ZipEntry ze; (ze = zin.getNextEntry()) != null;) {
			String zen = ze.getName();
			pm.setInfo(zen);
			OutputStream cacheOut = data.getOutputStream(zen);
			byte[] buffer = new byte[1024];
			for (int r; (r = zin.read(buffer, 0, buffer.length)) != -1;)
				cacheOut.write(buffer, 0, r);
			cacheOut.flush();
			cacheOut.close();
		}
		
		//	instantiate and return document proper
		return loadDocument(data, pm);
	}
	
	private static class ZipInImDocumentData extends ImDocumentData {
		private HashMap entryDataCache;
		private File entryDataCacheFolder;
		ZipInImDocumentData(File entryDataCacheFolder) {
			this.entryDataCache = ((entryDataCacheFolder == null) ? new HashMap() : null);
			this.entryDataCacheFolder = entryDataCacheFolder;
		}
		public boolean canLoadDocument() {
			return true;
		}
		public boolean canStoreDocument() {
			return false;
		}
		public String getDocumentDataId() {
			return null;
		}
		public boolean hasEntryData(ImDocumentEntry entry) {
			return this.hasEntry(entry);
		}
		public InputStream getInputStream(String entryName) throws IOException {
			ImDocumentEntry entry = this.getEntry(entryName);
			if (entry == null)
				throw new FileNotFoundException(entryName);
			if (this.entryDataCacheFolder == null) {
				byte[] entryData = ((byte[]) this.entryDataCache.get(entryName));
				if (entryData == null)
					throw new FileNotFoundException(entryName);
				else return new ByteArrayInputStream(entryData);
//				ByteArrayBuffer entryData = ((ByteArrayBuffer) this.entryDataCache.get(entryName));
//				if (entryData == null)
//					throw new FileNotFoundException(entryName);
//				else return entryData.getInputStream();
			}
			else {
				File entryDataFile = new File(this.entryDataCacheFolder, entryName);
				return new BufferedInputStream(new FileInputStream(entryDataFile));
			}
		}
		public OutputStream getOutputStream(final String entryName, boolean writeDirectly) throws IOException {
			OutputStream out;
			
			//	we have to use in memory caching
			if (this.entryDataCacheFolder == null)
				out = new ByteArrayOutputStream() {
					public void close() throws IOException {
						super.close();
						entryDataCache.put(entryName, this.toByteArray());
					}
				};
//				out = new ByteArrayBuffer() {
//					public void close() throws IOException {
//						super.close();
//						entryDataCache.put(entryName, this);
//					}
//				};
			
			//	we do have a cache folder, use it
			else {
				final File newEntryDataFile = new File(this.entryDataCacheFolder, (entryName + ".new"));
				out = new BufferedOutputStream(new FileOutputStream(newEntryDataFile) {
					public void close() throws IOException {
						super.flush();
						super.close();
						File exEntryDataFile = new File(entryDataCacheFolder, entryName);
						if (exEntryDataFile.exists() && newEntryDataFile.exists() && newEntryDataFile.getName().endsWith(".new"))
							exEntryDataFile.renameTo(new File(entryDataCacheFolder, (entryName + "." + System.currentTimeMillis() + ".old")));
						newEntryDataFile.renameTo(new File(entryDataCacheFolder, entryName));
					}
				});
			}
			
			//	make sure to update entry list
			return new DataHashOutputStream(out) {
				public void close() throws IOException {
					super.flush();
					super.close();
					putEntry(new ImDocumentEntry(entryName, System.currentTimeMillis(), this.getDataHash()));
				}
			};
		}
	}
	
	private static class ZipOutImDocumentData extends ImDocumentData {
		ZipOutputStream zipOut;
		ZipOutImDocumentData(ZipOutputStream zipOut) {
			this.zipOut = zipOut;
		}
		public boolean canLoadDocument() {
			return false;
		}
		public boolean canStoreDocument() {
			return true;
		}
		public String getDocumentDataId() {
			return null;
		}
		public boolean hasEntryData(ImDocumentEntry entry) {
			return this.hasEntry(entry);
		}
		public InputStream getInputStream(String entryName) throws IOException {
			throw new FileNotFoundException(entryName);
		}
		public OutputStream getOutputStream(final String entryName, boolean writeDirectly) throws IOException {
			
			//	put entry, and make sure not to close the stream proper
			this.zipOut.putNextEntry(new ZipEntry(entryName));
			OutputStream out = new FilterOutputStream(this.zipOut) {
				public void close() throws IOException {
					zipOut.flush();
					zipOut.closeEntry();
				}
			};
			
			//	make sure to update entry list
			return new DataHashOutputStream(out) {
				public void close() throws IOException {
					super.flush();
					super.close();
					putEntry(new ImDocumentEntry(entryName, System.currentTimeMillis(), this.getDataHash()));
				}
			};
		}
	}
	
	/**
	 * Instantiate an Image Markup document from the data provided by a
	 * document data object.
	 * @param data the document data object to load the document from
	 * @param pm a progress monitor to observe the loading process
	 * @return the document instantiated from the argument data
	 * @throws IOException
	 */
	public static ImDocument loadDocument(ImDocumentData data, ProgressMonitor pm) throws IOException {
		
		//	check progress monitor
		if (pm == null)
			pm = ProgressMonitor.dummy;
		
		//	read document meta data
		pm.setStep("Reading document data");
		pm.setBaseProgress(50);
		pm.setMaxProgress(55);
		InputStream docIn = data.getInputStream("document.csv");
		StringRelation docsData = StringRelation.readCsvData(new InputStreamReader(docIn, "UTF-8"), true, null);
		StringTupel docData = docsData.get(0);
		final String docId = docData.getValue(DOCUMENT_ID_ATTRIBUTE);
		docIn.close();
		if (docId == null)
			throw new IOException("Invalid Image Markup data: document ID missing");
		//	TODO_later load orientation
		final DataBoundImDocument doc = new DataBoundImDocument(docId, data);
		setAttributes(doc, docData.getValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE));
		
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
		/* We need to do this explicitly because supplements add themselves to
		 * their document upon creation via the normal addSupplement() method,
		 * marking themselves as dirty in our data bound document. */
		doc.markSupplementsNotDirty();
		
		//	create page image source
		pm.setStep("Creating page image source");
		pm.setBaseProgress(99);
		pm.setMaxProgress(100);
		doc.setPageImageSource(new DbidPageImageStore(doc, pageImageAttributesById));
		
		//	finally ...
		return doc;
	}
	
	/**
	 * Load the attributes of an Image Markup document from the data provided
	 * by a document data object, but without loading the document as a whole.
	 * Namely, this method only reads the 'document.csv' entry.
	 * @param data the document data object to load the attributes from
	 * @return the attributes read from the argument data
	 * @throws IOException
	 */
	public static Attributed loadDocumentAttributes(ImDocumentData data) throws IOException {
		InputStream docIn = data.getInputStream("document.csv");
		StringRelation docsData = StringRelation.readCsvData(new InputStreamReader(docIn, "UTF-8"), true, null);
		StringTupel docData = docsData.get(0);
		docIn.close();
		Attributed docAttributes = new AbstractAttributed();
		setAttributes(docAttributes, docData.getValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE));
		String docId = docData.getValue(DOCUMENT_ID_ATTRIBUTE);
		if (docId != null)
			docAttributes.setAttribute(DOCUMENT_ID_ATTRIBUTE, docId);
		return docAttributes;
	}
	
	private static class DataBoundImDocument extends ImDocument {
		ImDocumentData docData;
		DbidPageImageStore dbidPis;
		HashSet dirtySupplementNames = new HashSet();
		DataBoundImDocument(String docId, ImDocumentData docData) {
			super(docId);
			this.docData = docData;
		}
		
		public void setPageImageSource(PageImageSource pis) {
			super.setPageImageSource(pis);
			this.dbidPis = (((pis instanceof DbidPageImageStore) && (((DbidPageImageStore) pis).doc == this)) ? ((DbidPageImageStore) pis) : null);
		}
		
		public ImSupplement addSupplement(ImSupplement ims) {
			return this.addSupplement(ims, true);
		}
		ImSupplement addSupplement(ImSupplement ims, boolean isExternal) {
			if (isExternal) {
				String smt = ims.getMimeType();
				String sfn = (ims.getId() + "." + smt.substring(smt.lastIndexOf('/') + "/".length()));
				this.dirtySupplementNames.add(sfn); // mark supplement as dirty
			}
			return super.addSupplement(ims);
		}
		boolean isSupplementDirty(String sfn) {
			return this.dirtySupplementNames.contains(sfn);
		}
		void markSupplementsNotDirty() {
			this.dirtySupplementNames.clear();
		}
		void bindToData(ImDocumentData docData) {
			this.docData = docData;
		}
		boolean isInputStreamAvailable(String entryName) {
			return this.docData.hasEntry(entryName);
		}
		InputStream getInputStream(String entryName) throws IOException {
			return this.docData.getInputStream(entryName);
		}
		OutputStream getOutputStream(final String entryName, boolean writeDirectly) throws IOException {
			return new FilterOutputStream(this.docData.getOutputStream(entryName, writeDirectly)) {
				public void close() throws IOException {
					super.close();
					dirtySupplementNames.remove(entryName);
				}
			};
		}
	}
	
	
	private static class DbidPageImageStore extends AbstractPageImageStore {
		final DataBoundImDocument doc;
		final HashMap pageImageAttributesById;
		DbidPageImageStore(DataBoundImDocument doc, HashMap pageImageAttributesById) {
			this.doc = doc;
			this.doc.dbidPis = this;
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
	
	/**
	 * Store an Image Markup document to a file. If the argument file is an
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
	 * @param doc the Image Markup document to store.
	 * @param file the file or folder to store the document to
	 * @return an array describing the entries (in folder storage mode)
	 * @throws IOException
	 */
	public static ImDocumentEntry[] storeDocument(ImDocument doc, File file) throws IOException {
		return storeDocument(doc, file, null);
	}
	
	/**
	 * Store an Image Markup document to a file. If the argument file is an
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
	 * @param doc the Image Markup document to store.
	 * @param file the file or folder to store the document to
	 * @param pm a progress monitor to observe the storage process
	 * @return an array describing the entries (in folder storage mode)
	 * @throws IOException
	 */
	public static ImDocumentEntry[] storeDocument(ImDocument doc, File file, ProgressMonitor pm) throws IOException {
		
		//	file does not exist, create it as a file
		if (!file.exists()) {
			file.getAbsoluteFile().getParentFile().mkdirs();
			file.createNewFile();
		}
		
		//	we have a directory, store document without zipping
		if (file.isDirectory()) {
			ImDocumentData data = new FolderImDocumentData(file);
			if (doc instanceof DataBoundImDocument) {
				ImDocumentData docData = ((DataBoundImDocument) doc).docData;
				if (file.getAbsolutePath().equals(docData.getDocumentDataId()))
					data = docData;
			}
			return storeDocument(doc, data, pm);
		}
		
		//	we have an actual file (maybe of our own creation), zip up document
		else {
			OutputStream out = new BufferedOutputStream(new FileOutputStream(file));
			storeDocument(doc, out, pm);
			out.close();
			return null;
		}
	}
	
	/**
	 * Store an Image Markup document to an output stream. The argument output
	 * stream is flushed and closed at the end of this method.
	 * @param doc the Image Markup document to store.
	 * @param out the output stream to store the document to
	 * @throws IOException
	 */
	public static void storeDocument(ImDocument doc, OutputStream out) throws IOException {
		storeDocument(doc, out, null);
	}
	
	/**
	 * Store an Image Markup document to an output stream. The argument output
	 * stream is flushed and closed at the end of this method.
	 * @param doc the Image Markup document to store.
	 * @param out the output stream to store the document to
	 * @param pm a progress monitor to observe the storage process
	 * @throws IOException
	 */
	public static void storeDocument(ImDocument doc, OutputStream out, ProgressMonitor pm) throws IOException {
		ZipOutputStream zout = new ZipOutputStream(new FilterOutputStream(out) {
			public void close() throws IOException {}
		});
		storeDocument(doc, new ZipOutImDocumentData(zout), pm);
		zout.flush();
		zout.close();
	}
	
	/**
	 * Store an Image Markup document to a document data object.
	 * @param doc the document to store
	 * @param data the document data object to store the document to
	 * @param pm a progress monitor observing the storage process
	 * @return an array describing the entries
	 * @throws IOException
	 */
	public static ImDocumentEntry[] storeDocument(ImDocument doc, ImDocumentData data, ProgressMonitor pm) throws IOException {
		
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
		BufferedWriter bw;
		StringVector keys = new StringVector();
		
		//	store document meta data
		pm.setStep("Storing document data");
		pm.setBaseProgress(0);
		pm.setMaxProgress(5);
		bw = getWriter(data, "document.csv", false);
		keys.clear();
		keys.parseAndAddElements((DOCUMENT_ID_ATTRIBUTE + ";" + ImObject.ATTRIBUTES_STRING_ATTRIBUTE), ";");
		writeKeys(keys, bw);
		StringTupel docData = new StringTupel();
		docData.setValue(DOCUMENT_ID_ATTRIBUTE, doc.docId);
		docData.setValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, getAttributesString(doc));
		writeValues(keys, docData, bw);
		bw.close();
		
		//	assemble data for pages, words, and regions
		pm.setStep("Storing page data");
		pm.setBaseProgress(5);
		pm.setMaxProgress(8);
		ImPage[] pages = doc.getPages();
		bw = getWriter(data, "pages.csv", false);
		keys.clear();
		keys.parseAndAddElements((PAGE_ID_ATTRIBUTE + ";" + BOUNDING_BOX_ATTRIBUTE + ";" + ImObject.ATTRIBUTES_STRING_ATTRIBUTE), ";");
		writeKeys(keys, bw);
		for (int p = 0; p < pages.length; p++) {
			pm.setInfo("Page " + p);
			pm.setProgress((p * 100) / pages.length);
			
			//	data for page
			StringTupel pageData = new StringTupel();
			pageData.setValue(PAGE_ID_ATTRIBUTE, ("" + pages[p].pageId));
			pageData.setValue(BOUNDING_BOX_ATTRIBUTE, pages[p].bounds.toString());
			pageData.setValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, getAttributesString(pages[p]));
			writeValues(keys, pageData, bw);
		}
		bw.close();
		
		//	store words
		pm.setStep("Storing word data");
		pm.setBaseProgress(8);
		pm.setMaxProgress(17);
		bw = getWriter(data, "words.csv", lowMemory);
		keys.clear();
		keys.parseAndAddElements((PAGE_ID_ATTRIBUTE + ";" + BOUNDING_BOX_ATTRIBUTE + ";" + STRING_ATTRIBUTE + ";" + ImWord.PREVIOUS_WORD_ATTRIBUTE + ";" + ImWord.NEXT_WORD_ATTRIBUTE + ";" + ImWord.NEXT_RELATION_ATTRIBUTE + ";" + ImWord.TEXT_STREAM_TYPE_ATTRIBUTE + ";" + ImObject.ATTRIBUTES_STRING_ATTRIBUTE), ";");
		writeKeys(keys, bw);
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
				writeValues(keys, wordData, bw);
			}
		}
		bw.close();
		
		//	store regions
		pm.setStep("Storing region data");
		pm.setBaseProgress(17);
		pm.setMaxProgress(20);
		bw = getWriter(data, "regions.csv", lowMemory);
		keys.clear();
		keys.parseAndAddElements((PAGE_ID_ATTRIBUTE + ";" + BOUNDING_BOX_ATTRIBUTE + ";" + TYPE_ATTRIBUTE + ";" + ImObject.ATTRIBUTES_STRING_ATTRIBUTE), ";");
		writeKeys(keys, bw);
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
				writeValues(keys, regData, bw);
			}
		}
		bw.close();
		
		//	store annotations
		pm.setStep("Storing annotation data");
		pm.setBaseProgress(20);
		pm.setMaxProgress(25);
		bw = getWriter(data, "annotations.csv", lowMemory);
		keys.clear();
		keys.parseAndAddElements((ImAnnotation.FIRST_WORD_ATTRIBUTE + ";" + ImAnnotation.LAST_WORD_ATTRIBUTE + ";" + TYPE_ATTRIBUTE + ";" + ImObject.ATTRIBUTES_STRING_ATTRIBUTE), ";");
		writeKeys(keys, bw);
		ImAnnotation[] annots = doc.getAnnotations();
		for (int a = 0; a < annots.length; a++) {
			pm.setProgress((a * 100) / annots.length);
			StringTupel annotData = new StringTupel();
			annotData.setValue(ImAnnotation.FIRST_WORD_ATTRIBUTE, annots[a].getFirstWord().getLocalID());
			annotData.setValue(ImAnnotation.LAST_WORD_ATTRIBUTE, annots[a].getLastWord().getLocalID());
			annotData.setValue(TYPE_ATTRIBUTE, annots[a].getType());
			annotData.setValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, getAttributesString(annots[a]));
			writeValues(keys, annotData, bw);
		}
		bw.close();
		
		//	store fonts (if any)
		pm.setStep("Storing font data");
		pm.setBaseProgress(25);
		pm.setMaxProgress(30);
		bw = getWriter(data, "fonts.csv", lowMemory);
		keys.clear();
		keys.parseAndAddElements((ImFont.NAME_ATTRIBUTE + ";" + ImFont.STYLE_ATTRIBUTE + ";" + ImFont.CHARACTER_ID_ATTRIBUTE + ";" + ImFont.CHARACTER_STRING_ATTRIBUTE + ";" + ImFont.CHARACTER_IMAGE_ATTRIBUTE), ";");
		writeKeys(keys, bw);
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
				writeValues(keys, charData, bw);
			}
		}
		bw.close();
		
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
			boolean writePi = false;
			if (data instanceof ZipOutImDocumentData)
				writePi = true;
			else if (!(doc instanceof DataBoundImDocument))
				writePi = true;
			else if (((DataBoundImDocument) doc).dbidPis == null)
				writePi = true;
			else if (((DataBoundImDocument) doc).docData.getDocumentDataId() == null)
				writePi = true;
			else if (!((DataBoundImDocument) doc).docData.getDocumentDataId().equals(data.getDocumentDataId()))
				writePi = true;
			
			//	store page image proper if we have to
			if (writePi) {
				PageImageInputStream piis = pages[p].getPageImageAsStream();
				piAttributes = getPageImageAttributes(piis);
				String piName = PageImage.getPageImageName(doc.docId, pages[p].pageId);
				piName = ("page" + piName.substring(doc.docId.length() + ".".length()) + "." + IMAGE_FORMAT);
				OutputStream piOut = data.getOutputStream(piName, true);
				byte[] pib = new byte[1024];
				for (int r; (r = piis.read(pib, 0, pib.length)) != -1;)
					piOut.write(pib, 0, r);
				piOut.close();
				piis.close();
			}
			
			//	otherwise, we only have to get the attributes (the IMF entry already exists, as otherwise, we'd write the page image)
			else {
				PageImageAttributes pia = ((DataBoundImDocument) doc).dbidPis.getPageImageAttributes(new Integer(pages[p].pageId));
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
		bw = getWriter(data, "pageImages.csv", false);
		keys.clear();
		keys.parseAndAddElements((ImObject.PAGE_ID_ATTRIBUTE + ";" + ImObject.ATTRIBUTES_STRING_ATTRIBUTE), ";");
		writeKeys(keys, bw);
		for (int p = 0; p < pageImagesData.size(); p++)
			writeValues(keys, pageImagesData.get(p), bw);
		bw.close();
		
		//	store meta data of supplements
		pm.setStep("Storing supplement data");
		pm.setBaseProgress(80);
		pm.setMaxProgress(85);
		bw = getWriter(data, "supplements.csv", false);
		keys.clear();
		keys.parseAndAddElements((ImSupplement.ID_ATTRIBUTE + ";" + ImSupplement.TYPE_ATTRIBUTE + ";" + ImSupplement.MIME_TYPE_ATTRIBUTE + ";" + ImObject.ATTRIBUTES_STRING_ATTRIBUTE), ";");
		writeKeys(keys, bw);
		ImSupplement[] supplements = doc.getSupplements();
		for (int s = 0; s < supplements.length; s++) {
			StringTupel supplementData = new StringTupel();
			supplementData.setValue(ImSupplement.ID_ATTRIBUTE, supplements[s].getId());
			supplementData.setValue(ImSupplement.TYPE_ATTRIBUTE, supplements[s].getType());
			supplementData.setValue(ImSupplement.MIME_TYPE_ATTRIBUTE, supplements[s].getMimeType());
			supplementData.setValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, getAttributesString(supplements[s]));
			writeValues(keys, supplementData, bw);
		}
		bw.close();
		
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
			boolean writeSd = false;
			if (data instanceof ZipOutImDocumentData)
				writeSd = true;
			else if (!(doc instanceof DataBoundImDocument))
				writeSd = true;
			else if (((DataBoundImDocument) doc).docData.getDocumentDataId() == null)
				writeSd = true;
			else if (!((DataBoundImDocument) doc).docData.getDocumentDataId().equals(data.getDocumentDataId()))
				writeSd = true;
			else if (((DataBoundImDocument) doc).isSupplementDirty(sfn))
				writeSd = true;
			
			//	store supplement data proper if we have to
			if (writeSd) {
				InputStream sdIn = supplements[s].getInputStream();
				OutputStream sdOut = data.getOutputStream(sfn, true);
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
		if (data instanceof FolderImDocumentData) {
			
			//	if the document was loaded here, update IMF entries (both ways), and switch document to folder mode
			if (doc instanceof DataBoundImDocument)
				((DataBoundImDocument) doc).bindToData(data);
			
			//	write or overwrite 'enries.txt'
			((FolderImDocumentData) data).storeEntryList();
		}
		
		//	return entry list for external use
		return data.getEntries();
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
	
	private static BufferedWriter getWriter(ImDocumentData data, String entryName, boolean writeDirectly) throws IOException {
		return new BufferedWriter(new OutputStreamWriter(data.getOutputStream(entryName, writeDirectly), "UTF-8"));
	}
	
	/**
	 * Linearize the attributes of an attributed object into a single-line, CSV
	 * friendly form. Attribute values are converted into strings and escaped
	 * based on XML rules. Afterwards, each attribute value is appended to its
	 * corresponding attribute name, enclosed in angle brackets. The
	 * concatenation of these attribute name and value pairs yields the final
	 * linearization.
	 * @param attr the attributed object whose attributes to linearize
	 * @return the linearized attributes of the argument object
	 */
	public static String getAttributesString(Attributed attr) {
		StringBuffer attributes = new StringBuffer();
		String[] attributeNames = attr.getAttributeNames();
		for (int a = 0; a < attributeNames.length; a++) {
			Object value = attr.getAttribute(attributeNames[a]);
			if (value != null) try {
				attributes.append(attributeNames[a] + "<" + escape(value.toString()) + ">");
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
	
	/**
	 * Parse and un-escape attribute name and value pairs that were previously
	 * linearized via <code>getAttributesString()</code>. The parsed attribute
	 * name and value pairs are stored in the argument attributed object.
	 * @param attr the attributed objects to store the attributes in
	 * @param attributes the linearized attribute and value string
	 */
	public static void setAttributes(Attributed attr, String attributes) {
		String[] nameValuePairs = attributes.split("[\\<\\>]");
		for (int p = 0; p < (nameValuePairs.length-1); p+= 2)
			attr.setAttribute(nameValuePairs[p], unescape(nameValuePairs[p+1]));
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
//	
//	/**
//	 * @param args
//	 */
//	public static void main(String[] args) throws Exception {
//		final File baseFolder = new File("E:/Testdaten/PdfExtract/");
//		final String pdfName = "zt00872.pdf";
//		
//		long startTime = System.currentTimeMillis();
//		long startMem = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
//		ImDocument imDoc = loadDocument(new FileInputStream(new File(baseFolder, (pdfName + ".imf"))));
//		System.out.println("LOAD TIME: " + (System.currentTimeMillis() - startTime));
//		System.out.println("LOAD MEMORY: " + ((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) - startMem));
//		System.gc();
//		System.out.println("LOAD MEMORY GC: " + ((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) - startMem));
////		ImDocument imDoc = loadDocument(new File(baseFolder, (pdfName + ".new.imd")));
//		
////		File imFile = new File(baseFolder, (pdfName + ".widd.imf"));
////		OutputStream imOut = new BufferedOutputStream(new FileOutputStream(imFile));
////		storeDocument(imDoc, imOut);
////		imOut.flush();
////		imOut.close();
////		
////		File imFolder = new File(baseFolder, (pdfName + ".widd.imd"));
////		imFolder.mkdirs();
////		storeDocument(imDoc, imFolder);
//	}
}