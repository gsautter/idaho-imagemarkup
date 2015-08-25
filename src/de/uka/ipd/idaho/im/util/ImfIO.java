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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.SequenceInputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import de.uka.ipd.idaho.easyIO.streams.PeekInputStream;
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
	
	/* TODO
- add "entries.csv" to IMFs, keeping MD5 checksum and create and update user and timestamp for each entry
--> helps with only sending delta to server on update
- rename ImfIO to ImFileIO
- create ImFile to implement above change tracking, basically wrapping ImfIO loading behavior in dedicated object
  - provide storeTo() methods (with stream or file argument)
  - provide storeChangesTo() method (with stream argument) for server upload

- consider storing content of pages (regions and words) in pageContent.<pageId>.csv files instead of regions.csv and words.csv
--> allows for loading only some page range ImDocument instead of whole document (very helpful in GG Imagine Online), even if annotations still have to reside in single CSV
==> then, don't do the latter, as the two tables differ considerably ...
==> ... but do use, and version, words.<pageId>.csv
	 */
	
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
	public static ImDocument loadDocument(InputStream in, final File cacheFolder) throws IOException {
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
	public static ImDocument loadDocument(InputStream in, final File cacheFolder, ProgressMonitor pm) throws IOException {
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
	public static ImDocument loadDocument(InputStream in, final File cacheFolder, ProgressMonitor pm, int inLength) throws IOException {
		
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
		ZipInputStream zin = new ZipInputStream(in);
		
		//	cache (in memory or on disc)
		final HashMap cache = ((cacheFolder == null) ? new HashMap() : null);
		
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
		
		//	read document meta data
		pm.setStep("Reading document data");
		pm.setBaseProgress(50);
		pm.setMaxProgress(55);
		InputStream docIn = getInputStream("document.csv", cache, cacheFolder);
		StringRelation docsData = StringRelation.readCsvData(new InputStreamReader(docIn, "UTF-8"), true, null);
		StringTupel docData = docsData.get(0);
		final String docId = docData.getValue(DOCUMENT_ID_ATTRIBUTE);
		docIn.close();
		if (docId == null)
			throw new IOException("Invalid image markup data: document ID missing");
		//	TODO_later load orientation
		ImDocument doc = new ImDocument(docId);
		setAttributes(doc, docData.getValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE));
		
		//	read fonts (if any)
		pm.setStep("Reading font data");
		pm.setBaseProgress(55);
		pm.setMaxProgress(60);
		try {
			InputStream fontsIn = getInputStream("fonts.csv", cache, cacheFolder);
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
			InputStream pageImagesIn = getInputStream("pageImages.csv", cache, cacheFolder);
			StringRelation pageImagesData = StringRelation.readCsvData(new InputStreamReader(pageImagesIn, "UTF-8"), true, null);
			pageImagesIn.close();
			for (int p = 0; p < pageImagesData.size(); p++) {
				StringTupel pageImageData = pageImagesData.get(p);
				int piPageId = Integer.parseInt(pageImageData.getValue(ImObject.PAGE_ID_ATTRIBUTE));
				String piAttributes = pageImageData.getValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE);
				pageImageAttributesById.put(new Integer(piPageId), piAttributes);
			}
		} catch (IOException ioe) { /* we might be faced with the old way ... */ }
		
		//	read pages
		pm.setStep("Reading page data");
		pm.setBaseProgress(60);
		pm.setMaxProgress(65);
		InputStream pagesIn = getInputStream("pages.csv", cache, cacheFolder);
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
//			System.out.println("Added page " + page.pageId + " with bounds " + bounds.toString());
			String piAttributes = ((String) pageImageAttributesById.get(new Integer(pageId)));
			if (piAttributes == null)
				continue;
			String[] piAttributeNameValuePairs = piAttributes.split("[\\<\\>]");
			for (int a = 0; a < (piAttributeNameValuePairs.length-1); a+= 2) {
				if ("originalDpi".equals(piAttributeNameValuePairs[a]))
					page.setImageDPI(Integer.parseInt(piAttributeNameValuePairs[a+1]));
				else if ("currentDpi".equals(piAttributeNameValuePairs[a])) {
					page.setImageDPI(Integer.parseInt(piAttributeNameValuePairs[a+1]));
					break;
				}
			}
		}
		
		//	read words (first add words, then chain them and set attributes, as they might not be stored in stream order)
		pm.setStep("Reading word data");
		pm.setBaseProgress(65);
		pm.setMaxProgress(75);
		InputStream wordsIn = getInputStream("words.csv", cache, cacheFolder);
		StringRelation wordsData = StringRelation.readCsvData(new InputStreamReader(wordsIn, "UTF-8"), true, null);
		wordsIn.close();
		pm.setInfo("Adding " + wordsData.size() + " words");
		for (int w = 0; w < wordsData.size(); w++) {
			pm.setProgress((w * 100) / wordsData.size());
			StringTupel wordData = wordsData.get(w);
			ImPage page = doc.getPage(Integer.parseInt(wordData.getValue(PAGE_ID_ATTRIBUTE)));
			BoundingBox bounds = BoundingBox.parse(wordData.getValue(BOUNDING_BOX_ATTRIBUTE));
			new ImWord(page, bounds, wordData.getValue(STRING_ATTRIBUTE));
//			System.out.println("Added word '" + wordData.getValue(STRING_ATTRIBUTE) + "' to page " + page.pageId + " at " + bounds.toString());
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
		InputStream regsIn = getInputStream("regions.csv", cache, cacheFolder);
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
//				System.out.println("Added '" + type + "' region to page " + page.pageId + " at " + bounds.toString());
				regionsById.put(regId, reg);
			}
			setAttributes(reg, regData.getValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, ""));
		}
		
		//	read annotations
		pm.setStep("Reading annotation data");
		pm.setBaseProgress(85);
		pm.setMaxProgress(90);
		InputStream annotsIn = getInputStream("annotations.csv", cache, cacheFolder);
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
			InputStream supplementsIn = getInputStream("supplements.csv", cache, cacheFolder);
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
							return ImfIO.getInputStream(sfn, cache, cacheFolder);
						}
					};
				else if (ImSupplement.SCAN_TYPE.equals(st))
					supplement = new ImSupplement.Scan(doc, smt) {
						public InputStream getInputStream() throws IOException {
							return ImfIO.getInputStream(sfn, cache, cacheFolder);
						}
					};
				else if (ImSupplement.FIGURE_TYPE.equals(st))
					supplement = new ImSupplement.Figure(doc, smt) {
						public InputStream getInputStream() throws IOException {
							return ImfIO.getInputStream(sfn, cache, cacheFolder);
						}
					};
				else supplement = new ImSupplement(doc, st, smt) {
					public String getId() {
						return sid;
					}
					public InputStream getInputStream() throws IOException {
						return ImfIO.getInputStream(sfn, cache, cacheFolder);
					}
				};
				setAttributes(supplement, supplementData.getValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, ""));
				doc.addSupplement(supplement);
			}
		} catch (IOException ioe) { /* we might not have supplements ... */ }
		
		//	create page image source
		pm.setStep("Creating page image source");
		pm.setBaseProgress(99);
		pm.setMaxProgress(100);
		PageImageSource pis = new AbstractPageImageStore() {
			public boolean isPageImageAvailable(String name) {
				if (!name.startsWith(docId))
					return false;
				name = ("page" + name.substring(docId.length() + ".".length()) + "." + IMAGE_FORMAT);
				if (cache == null) {
					File pif = new File(cacheFolder, name);
					return pif.exists();
				}
				else return cache.containsKey(name);
			}
			public PageImageInputStream getPageImageAsStream(String name) throws IOException {
				if (!name.startsWith(docId))
					return null;
				String piPageIdStr = name.substring(docId.length() + ".".length());
				name = ("page" + piPageIdStr + "." + IMAGE_FORMAT);
				return this.getPageImageInputStream(getInputStream(name, cache, cacheFolder), piPageIdStr);
			}
			private PageImageInputStream getPageImageInputStream(InputStream in, String piPageIdStr) throws IOException {
//				
//				//	test for PNG byte order mark
//				PeekInputStream peekIn = new PeekInputStream(in, "‰PNG".length());
//				System.out.println("‰PNG = " + Arrays.toString("‰PNG".getBytes()));
//				byte[] peek = new byte["‰PNG".getBytes().length];
//				peekIn.peek(peek);
//				System.out.println(Arrays.toString(peek) + " = " + new String(peek));
//				
//				//	this one's the old way
//				if (!peekIn.startsWith("‰PNG".getBytes()))
//					return new PageImageInputStream(peekIn, this);
				
				//	test for PNG byte order mark
				PeekInputStream peekIn = new PeekInputStream(in, pngSignature.length);
//				System.out.println("‰PNG = " + Arrays.toString("‰PNG".getBytes()));
//				System.out.println("PNG signature = " + Arrays.toString(pngSignature));
//				byte[] peek = new byte["‰PNG".getBytes().length];
//				peekIn.peek(peek);
//				System.out.println(Arrays.toString(peek) + " = " + new String(peek));
				
				//	this one's the old way
				if (!peekIn.startsWith(pngSignature))
					return new PageImageInputStream(peekIn, this);
				
				//	get attribute string
				String piAttributes = ((String) pageImageAttributesById.get(new Integer(piPageIdStr)));
				if (piAttributes == null) {
					peekIn.close();
					throw new FileNotFoundException("page" + piPageIdStr + "." + IMAGE_FORMAT);
				}
				
				//	initialize attributes, edges with default values
				int originalWidth = -1;
				int originalHeight = -1;
				int originalDpi = -1;
				int currentDpi = -1;
				int leftEdge = 0;
				int rightEdge = 0;
				int topEdge = 0;
				int bottomEdge = 0;
				
				//	read attribute string
				String[] piAttributeNameValuePairs = piAttributes.split("[\\<\\>]");
				for (int a = 0; a < (piAttributeNameValuePairs.length-1); a+= 2) {
					if ("originalWidth".equals(piAttributeNameValuePairs[a]))
						originalWidth = Integer.parseInt(piAttributeNameValuePairs[a+1]);
					else if ("originalHeight".equals(piAttributeNameValuePairs[a]))
						originalHeight = Integer.parseInt(piAttributeNameValuePairs[a+1]);
					else if ("originalDpi".equals(piAttributeNameValuePairs[a]))
						originalDpi = Integer.parseInt(piAttributeNameValuePairs[a+1]);
					else if ("currentDpi".equals(piAttributeNameValuePairs[a]))
						currentDpi = Integer.parseInt(piAttributeNameValuePairs[a+1]);
					else if ("leftEdge".equals(piAttributeNameValuePairs[a]))
						leftEdge = Integer.parseInt(piAttributeNameValuePairs[a+1]);
					else if ("rightEdge".equals(piAttributeNameValuePairs[a]))
						rightEdge = Integer.parseInt(piAttributeNameValuePairs[a+1]);
					else if ("topEdge".equals(piAttributeNameValuePairs[a]))
						topEdge = Integer.parseInt(piAttributeNameValuePairs[a+1]);
					else if ("bottomEdge".equals(piAttributeNameValuePairs[a]))
						bottomEdge = Integer.parseInt(piAttributeNameValuePairs[a+1]);
				}
				
				//	default current DPI to original DPI if not given explicitly
				if (currentDpi == -1)
					currentDpi = originalDpi;
				
				//	wrap attribute values in byte array
				byte[] piAttributeBytes = {
					((byte) ((originalWidth / 256))),
					((byte) ((originalWidth & 255))),
					((byte) ((originalHeight / 256))),
					((byte) ((originalHeight & 255))),
					((byte) ((originalDpi / 256))),
					((byte) ((originalDpi & 255))),
					((byte) ((currentDpi / 256))),
					((byte) ((currentDpi & 255))),
					((byte) ((leftEdge / 256))),
					((byte) ((leftEdge & 255))),
					((byte) ((rightEdge / 256))),
					((byte) ((rightEdge & 255))),
					((byte) ((topEdge / 256))),
					((byte) ((topEdge & 255))),
					((byte) ((bottomEdge / 256))),
					((byte) ((bottomEdge & 255))),
				};
				
				//	prepend attribute bytes to image data
				return new PageImageInputStream(new SequenceInputStream(new ByteArrayInputStream(piAttributeBytes), peekIn), this);
			}
			public boolean storePageImage(String name, PageImage pageImage) throws IOException {
				if (!name.startsWith(docId))
					return false;
				String piPageIdStr = name.substring(docId.length() + ".".length());
				name = ("page" + piPageIdStr + "." + IMAGE_FORMAT);
				OutputStream out = getOutputStream(name, cache, cacheFolder);
				pageImage.writeImage(out);
				out.close();
				pageImageAttributesById.put(new Integer(piPageIdStr), getPageImageAttributes(pageImage));
				return true;
			}
			public int getPriority() {
				return 10; // we only want page images for one document, but those we really do want
			}
		};
//		PageImage.addPageImageSource(pis);
		doc.setPageImageSource(pis);
		
		//	finally ...
		return doc;
	}
	
	/* we need to hard-code the first byte 0x89 ('‰' on ISO-8859) converts
	 * differently on MacRoman and other encodings if embedded as a string
	 * constant in the code */
	private static final byte[] pngSignature = {((byte) 0x89), ((byte) 'P'), ((byte) 'N'), ((byte) 'G')};
	
	private static InputStream getInputStream(String name, HashMap cache, File cacheFolder) throws IOException {
		if (cache == null)
			return new BufferedInputStream(new FileInputStream(new File(cacheFolder, name)));
		else if (cache.containsKey(name))
			return new ByteArrayInputStream((byte[]) cache.get(name));
		else throw new FileNotFoundException(name);
	}
	
	private static OutputStream getOutputStream(final String name, final HashMap cache, File cacheFolder) throws IOException {
		if (cache == null)
			return new BufferedOutputStream(new FileOutputStream(new File(cacheFolder, name)));
		else return new ByteArrayOutputStream() {
			public void close() throws IOException {
				super.close();
				cache.put(name, this.toByteArray());
			}
		};
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
		
		//	check progress monitor
		if (pm == null)
			pm = ProgressMonitor.dummy;
		
		//	get ready to zip
		ZipOutputStream zout = new ZipOutputStream(out);
		BufferedWriter zbw = new BufferedWriter(new OutputStreamWriter(zout, "UTF-8"));
		StringVector keys = new StringVector();
		
		//	store document meta data
		pm.setStep("Storing document data");
		pm.setBaseProgress(0);
		pm.setMaxProgress(5);
		StringRelation docsData = new StringRelation();
		StringTupel docData = new StringTupel();
		docData.setValue(DOCUMENT_ID_ATTRIBUTE, doc.docId);
		docData.setValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, getAttributesString(doc));
		docsData.addElement(docData);
		keys.clear();
		keys.parseAndAddElements((DOCUMENT_ID_ATTRIBUTE + ";" + ImObject.ATTRIBUTES_STRING_ATTRIBUTE), ";");
		zout.putNextEntry(new ZipEntry("document.csv"));
		StringRelation.writeCsvData(zbw, docsData, keys);
		zbw.flush();
		zout.closeEntry();
		
		//	assemble data for pages, words, and regions
		pm.setStep("Collecting pages, regions, and words");
		pm.setBaseProgress(5);
		pm.setMaxProgress(10);
		StringRelation pagesData = new StringRelation();
		StringRelation wordsData = new StringRelation();
		StringRelation regsData = new StringRelation();
		ImPage[] pages = doc.getPages();
		for (int p = 0; p < pages.length; p++) {
			pm.setInfo("Page " + p);
			pm.setProgress((p * 100) / pages.length);
			
			//	data for page
			StringTupel pageData = new StringTupel();
			pageData.setValue(PAGE_ID_ATTRIBUTE, ("" + pages[p].pageId));
			pageData.setValue(BOUNDING_BOX_ATTRIBUTE, pages[p].bounds.toString());
			pageData.setValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, getAttributesString(pages[p]));
			pagesData.addElement(pageData);
			
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
				wordsData.addElement(wordData);
			}
			
			//	data for regions
			ImRegion[] pageRegs = pages[p].getRegions();
			for (int r = 0; r < pageRegs.length; r++) {
				StringTupel regData = new StringTupel();
				regData.setValue(PAGE_ID_ATTRIBUTE, ("" + pageRegs[r].pageId));
				regData.setValue(BOUNDING_BOX_ATTRIBUTE, pageRegs[r].bounds.toString());
				regData.setValue(TYPE_ATTRIBUTE, pageRegs[r].getType());
				regData.setValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, getAttributesString(pageRegs[r]));
				regsData.addElement(regData);
			}
		}
		
		//	store pages
		pm.setStep("Storing page data");
		pm.setBaseProgress(10);
		pm.setMaxProgress(12);
		keys.clear();
		keys.parseAndAddElements((PAGE_ID_ATTRIBUTE + ";" + BOUNDING_BOX_ATTRIBUTE + ";" + ImObject.ATTRIBUTES_STRING_ATTRIBUTE), ";");
		zout.putNextEntry(new ZipEntry("pages.csv"));
		StringRelation.writeCsvData(zbw, pagesData, keys);
		zbw.flush();
		zout.closeEntry();
		
		//	store words
		pm.setStep("Storing word data");
		pm.setBaseProgress(12);
		pm.setMaxProgress(18);
		keys.clear();
		keys.parseAndAddElements((PAGE_ID_ATTRIBUTE + ";" + BOUNDING_BOX_ATTRIBUTE + ";" + STRING_ATTRIBUTE + ";" + ImWord.PREVIOUS_WORD_ATTRIBUTE + ";" + ImWord.NEXT_WORD_ATTRIBUTE + ";" + ImWord.NEXT_RELATION_ATTRIBUTE + ";" + ImWord.TEXT_STREAM_TYPE_ATTRIBUTE + ";" + ImObject.ATTRIBUTES_STRING_ATTRIBUTE), ";");
		zout.putNextEntry(new ZipEntry("words.csv"));
		StringRelation.writeCsvData(zbw, wordsData, keys);
		zbw.flush();
		zout.closeEntry();
		
		//	store regions
		pm.setStep("Storing region data");
		pm.setBaseProgress(18);
		pm.setMaxProgress(20);
		keys.clear();
		keys.parseAndAddElements((PAGE_ID_ATTRIBUTE + ";" + BOUNDING_BOX_ATTRIBUTE + ";" + TYPE_ATTRIBUTE + ";" + ImObject.ATTRIBUTES_STRING_ATTRIBUTE), ";");
		zout.putNextEntry(new ZipEntry("regions.csv"));
		StringRelation.writeCsvData(zbw, regsData, keys);
		zbw.flush();
		zout.closeEntry();
		
		//	store annotations
		pm.setStep("Storing annotation data");
		pm.setBaseProgress(20);
		pm.setMaxProgress(25);
		StringRelation annotsData = new StringRelation();
		ImAnnotation[] annots = doc.getAnnotations();
		for (int a = 0; a < annots.length; a++) {
			pm.setProgress((a * 100) / annots.length);
			StringTupel annotData = new StringTupel();
			annotData.setValue(ImAnnotation.FIRST_WORD_ATTRIBUTE, annots[a].getFirstWord().getLocalID());
			annotData.setValue(ImAnnotation.LAST_WORD_ATTRIBUTE, annots[a].getLastWord().getLocalID());
			annotData.setValue(TYPE_ATTRIBUTE, annots[a].getType());
			annotData.setValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, getAttributesString(annots[a]));
			annotsData.addElement(annotData);
		}
		keys.clear();
		keys.parseAndAddElements((ImAnnotation.FIRST_WORD_ATTRIBUTE + ";" + ImAnnotation.LAST_WORD_ATTRIBUTE + ";" + TYPE_ATTRIBUTE + ";" + ImObject.ATTRIBUTES_STRING_ATTRIBUTE), ";");
		zout.putNextEntry(new ZipEntry("annotations.csv"));
		StringRelation.writeCsvData(zbw, annotsData, keys);
		zbw.flush();
		zout.closeEntry();
		
		//	store fonts (if any)
		pm.setStep("Storing font data");
		pm.setBaseProgress(25);
		pm.setMaxProgress(30);
		StringRelation fontsData = new StringRelation();
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
				fontsData.addElement(charData);
			}
		}
		keys.clear();
		keys.parseAndAddElements((ImFont.NAME_ATTRIBUTE + ";" + ImFont.STYLE_ATTRIBUTE + ";" + ImFont.CHARACTER_ID_ATTRIBUTE + ";" + ImFont.CHARACTER_STRING_ATTRIBUTE + ";" + ImFont.CHARACTER_IMAGE_ATTRIBUTE), ";");
		zout.putNextEntry(new ZipEntry("fonts.csv"));
		StringRelation.writeCsvData(zbw, fontsData, keys);
		zbw.flush();
		zout.closeEntry();
		
		//	store page images
		pm.setStep("Storing page images");
		pm.setBaseProgress(30);
		pm.setMaxProgress(79);
		StringRelation pageImagesData = new StringRelation();
		for (int p = 0; p < pages.length; p++) {
			pm.setInfo("Page " + p);
			pm.setProgress((p * 100) / pages.length);
			
			PageImage pi = pages[p].getPageImage();
			String piName = PageImage.getPageImageName(doc.docId, pages[p].pageId);
			piName = ("page" + piName.substring(doc.docId.length() + ".".length()) + "." + IMAGE_FORMAT);
			zout.putNextEntry(new ZipEntry(piName));
			pi.writeImage(zout);
			zout.closeEntry();
			
			StringTupel piData = new StringTupel();
			piData.setValue(ImObject.PAGE_ID_ATTRIBUTE, ("" + pages[p].pageId));
			piData.setValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, getPageImageAttributes(pi));
			pageImagesData.addElement(piData);
		}
		
		//	store page image data
		pm.setStep("Storing page image data");
		pm.setBaseProgress(79);
		pm.setMaxProgress(80);
		keys.clear();
		keys.parseAndAddElements((ImObject.PAGE_ID_ATTRIBUTE + ";" + ImObject.ATTRIBUTES_STRING_ATTRIBUTE), ";");
		zout.putNextEntry(new ZipEntry("pageImages.csv"));
		StringRelation.writeCsvData(zbw, pageImagesData, keys);
		zbw.flush();
		zout.closeEntry();
		
		//	store meta data of supplements
		pm.setStep("Storing supplement data");
		pm.setBaseProgress(80);
		pm.setMaxProgress(85);
		StringRelation supplementsData = new StringRelation();
		ImSupplement[] supplements = doc.getSupplements();
		for (int s = 0; s < supplements.length; s++) {
			StringTupel supplementData = new StringTupel();
			supplementData.setValue(ImSupplement.ID_ATTRIBUTE, supplements[s].getId());
			supplementData.setValue(ImSupplement.TYPE_ATTRIBUTE, supplements[s].getType());
			supplementData.setValue(ImSupplement.MIME_TYPE_ATTRIBUTE, supplements[s].getMimeType());
			supplementData.setValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, getAttributesString(supplements[s]));
			supplementsData.addElement(supplementData);
		}
		keys.clear();
		keys.parseAndAddElements((ImSupplement.ID_ATTRIBUTE + ";" + ImSupplement.TYPE_ATTRIBUTE + ";" + ImSupplement.MIME_TYPE_ATTRIBUTE + ";" + ImObject.ATTRIBUTES_STRING_ATTRIBUTE), ";");
		zout.putNextEntry(new ZipEntry("supplements.csv"));
		StringRelation.writeCsvData(zbw, supplementsData, keys);
		zbw.flush();
		zout.closeEntry();
		
		//	store supplements proper
		pm.setStep("Storing supplements");
		pm.setBaseProgress(85);
		pm.setMaxProgress(100);
		for (int s = 0; s < supplements.length; s++) {
			pm.setProgress((s * 100) / supplements.length);
			InputStream sdis = supplements[s].getInputStream();
			String smt = supplements[s].getMimeType();
			String sfn = (supplements[s].getId() + "." + smt.substring(smt.lastIndexOf('/') + "/".length()));
			pm.setInfo("Supplement " + sfn);
			zout.putNextEntry(new ZipEntry(sfn));
			byte[] sdb = new byte[1024];
			for (int r; (r = sdis.read(sdb, 0, sdb.length)) != -1;)
				zout.write(sdb, 0, r);
			zout.closeEntry();
		}
		
		//	finally
		pm.setProgress(100);
		zout.flush();
		zout.close();
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
	
	private static String getPageImageAttributes(PageImage pi) {
		StringBuffer piAttributes = new StringBuffer();
		piAttributes.append("originalWidth<" + pi.originalWidth + ">");
		piAttributes.append("originalHeight<" + pi.originalHeight + ">");
		piAttributes.append("originalDpi<" + pi.originalDpi + ">");
		if (pi.currentDpi != pi.originalDpi)
			piAttributes.append("currentDpi<" + pi.currentDpi + ">");
		if (pi.leftEdge != 0)
			piAttributes.append("leftEdge<" + pi.leftEdge + ">");
		if (pi.rightEdge != 0)
			piAttributes.append("rightEdge<" + pi.rightEdge + ">");
		if (pi.topEdge != 0)
			piAttributes.append("topEdge<" + pi.topEdge + ">");
		if (pi.bottomEdge != 0)
			piAttributes.append("bottomEdge<" + pi.bottomEdge + ">");
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
//	
//	/**
//	 * @param args
//	 */
//	public static void main(String[] args) throws Exception {
//		final File baseFolder = new File("E:/Testdaten/PdfExtract/");
//		final String pdfName = "dikow_2012.pdf";
//		
//		ImDocument imDoc = loadDocument(new FileInputStream(new File(baseFolder, (pdfName + ".imf"))));
//		ImPage[] imPages = imDoc.getPages();
//		System.out.println("Document loaded, got " + imPages.length + " pages");
//		for (int p = 0; p < imPages.length; p++) {
//			ImWord[] imWords = imPages[p].getWords();
//			System.out.println(" - got " + imWords.length + " words in page " + p);
//			PageImage pi = PageImage.getPageImage(imDoc.docId, imPages[p].pageId);
//			System.out.println(" - got page image, size is " + pi.image.getWidth() + "x" + pi.image.getHeight());
//		}
//		
//		File imFile = new File(baseFolder, (pdfName + ".new.imf"));
//		OutputStream imOut = new BufferedOutputStream(new FileOutputStream(imFile));
//		storeDocument(imDoc, imOut);
//		imOut.flush();
//		imOut.close();
//	}
}