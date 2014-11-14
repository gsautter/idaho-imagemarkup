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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import de.uka.ipd.idaho.gamta.util.imaging.BoundingBox;
import de.uka.ipd.idaho.gamta.util.imaging.ImagingConstants;
import de.uka.ipd.idaho.gamta.util.imaging.PageImage;
import de.uka.ipd.idaho.gamta.util.imaging.PageImageInputStream;
import de.uka.ipd.idaho.gamta.util.imaging.PageImageStore.AbstractPageImageStore;
import de.uka.ipd.idaho.im.ImAnnotation;
import de.uka.ipd.idaho.im.ImDocument;
import de.uka.ipd.idaho.im.ImObject;
import de.uka.ipd.idaho.im.ImPage;
import de.uka.ipd.idaho.im.ImRegion;
import de.uka.ipd.idaho.im.ImWord;
import de.uka.ipd.idaho.stringUtils.StringVector;
import de.uka.ipd.idaho.stringUtils.csvHandler.StringRelation;
import de.uka.ipd.idaho.stringUtils.csvHandler.StringTupel;

/**
 * IO utility for image markup files (file ending .imf). However, in favor of
 * flexibility, this class works on input and output streams. This is to
 * abstract from the exact location the file is loaded from.<br>
 * An image markup file is basically a ZIP archive containing the following
 * entries:
 * <ul>
 * <li><b>document.txt</b>: a settings text file, containing the document ID,
 * the writing system (if different from Latin script), number of pages, and
 * also stores bibliographic meta data once it becomes available.</li>
 * <li><b>pages.csv</b>: a CSV file containing information about individual
 * pages, such as the resolution of the respective images.</li>
 * <li><b>words.csv</b>: a CSV file containing individual layout words, their
 * bounding boxes, assigned string value, IDs of their predecessor and
 * successor, type of connection to their successor (if any), as well as
 * additional attributes.</li>
 * <li><b>regions.csv</b>: a CSV file containing individual layout regions,
 * namely the IDs of their first and last word, type, as well as additional
 * attributes.</li>
 * <li><b>annotations.csv</b>: a CSV file containing the semantic markup of the
 * document, namely sections and detail annotations. Similar to the layout
 * regions, they are described by the IDs of their first and last word, their
 * type, and additional attributes. For each detail annotation, it additionally
 * stores whether or not to cut leading and/or tailing punctuation marks, as the
 * boundaries of the latter do not necessarily coincide with the boundaries of
 * layout words. In particular, layout words may include punctuation marks
 * adjacent to words, which may not be part of the semantic annotation.</li>
 * <li><b>&lt;docId&gt;&lt;pageId&gt;.png</b>: images of the individual pages,
 * one per page.</li>
 * </ul>
 * 
 * @author sautter
 */
public class ImfIO implements ImagingConstants {
	
	/**
	 * Load an image markup document.
	 * @param in the input stream to load from
	 * @return an image markup document representing the content of the
	 *            argument stream.
	 * @throws IOException
	 */
	public static ImDocument loadDocument(InputStream in) throws IOException {
		return loadDocument(in, null);
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
		
		//	get ready to unzip
		ZipInputStream zin = new ZipInputStream(in);
		
		//	cache (in memory or on disc)
		final HashMap cache = ((cacheFolder == null) ? new HashMap() : null);
		
		//	read and cache archive contents
		for (ZipEntry ze; (ze = zin.getNextEntry()) != null;) {
			String zen = ze.getName();
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
		
		//	read pages
		InputStream pagesIn = getInputStream("pages.csv", cache, cacheFolder);
		StringRelation pagesData = StringRelation.readCsvData(new InputStreamReader(pagesIn, "UTF-8"), true, null);
		pagesIn.close();
		for (int p = 0; p < pagesData.size(); p++) {
			StringTupel pageData = pagesData.get(p);
			int pageId = Integer.parseInt(pageData.getValue(PAGE_ID_ATTRIBUTE));
			BoundingBox bounds = BoundingBox.parse(pageData.getValue(BOUNDING_BOX_ATTRIBUTE));
			ImPage page = new ImPage(doc, pageId, bounds); // constructor adds page to document automatically
			setAttributes(page, pageData.getValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, ""));
		}
		
		//	read words (first add words, then chain them and set attributes, as they might not be stored in stream order)
		InputStream wordsIn = getInputStream("words.csv", cache, cacheFolder);
		StringRelation wordsData = StringRelation.readCsvData(new InputStreamReader(wordsIn, "UTF-8"), true, null);
		wordsIn.close();
		for (int w = 0; w < wordsData.size(); w++) {
			StringTupel wordData = wordsData.get(w);
			ImPage page = doc.getPage(Integer.parseInt(wordData.getValue(PAGE_ID_ATTRIBUTE)));
			BoundingBox bounds = BoundingBox.parse(wordData.getValue(BOUNDING_BOX_ATTRIBUTE));
			new ImWord(page, bounds, wordData.getValue(STRING_ATTRIBUTE));
		}
		for (int w = 0; w < wordsData.size(); w++) {
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
		InputStream regsIn = getInputStream("regions.csv", cache, cacheFolder);
		StringRelation regsData = StringRelation.readCsvData(new InputStreamReader(regsIn, "UTF-8"), true, null);
		regsIn.close();
		HashMap regionsById = new HashMap();
		for (int r = 0; r < regsData.size(); r++) {
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
		InputStream annotsIn = getInputStream("annotations.csv", cache, cacheFolder);
		StringRelation annotsData = StringRelation.readCsvData(new InputStreamReader(annotsIn, "UTF-8"), true, null);
		annotsIn.close();
		for (int a = 0; a < annotsData.size(); a++) {
			StringTupel annotData = annotsData.get(a);
			ImWord firstWord = doc.getWord(annotData.getValue(ImAnnotation.FIRST_WORD_ATTRIBUTE));
			ImWord lastWord = doc.getWord(annotData.getValue(ImAnnotation.LAST_WORD_ATTRIBUTE));
			if ((firstWord == null) || (lastWord == null))
				continue;
			String type = annotData.getValue(TYPE_ATTRIBUTE);
			ImAnnotation annot = doc.addAnnotation(firstWord, lastWord, type);
			setAttributes(annot, annotData.getValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, ""));
		}
		
		//	create page image source
		PageImage.addPageImageSource(new AbstractPageImageStore() {
			public boolean isPageImageAvailable(String name) {
				if (!name.startsWith(docId))
					return false;
				name = "page" + name.substring(docId.length() + ".".length()) + "." + IMAGE_FORMAT;
				if (cache == null) {
					File pif = new File(cacheFolder, name);
					return pif.exists();
				}
				else return cache.containsKey(name);
			}
			public PageImageInputStream getPageImageAsStream(String name) throws IOException {
				if (!name.startsWith(docId))
					return null;
				name = "page" + name.substring(docId.length() + ".".length()) + "." + IMAGE_FORMAT;
				//	TODO consider reading page image attributes from 'pageImages.csv' and sequence inserting it before plain PNGs (simply check if input stream starts with PNG tag)
				//	TODO however, also consider 'pageImage.csv' not existing at all, so be careful
				return new PageImageInputStream(getInputStream(name, cache, cacheFolder), this);
			}
			public boolean storePageImage(String name, PageImage pageImage) throws IOException {
				if (!name.startsWith(docId))
					return false;
				name = "page" + name.substring(docId.length() + ".".length()) + "." + IMAGE_FORMAT;
				OutputStream out = getOutputStream(name, cache, cacheFolder);
				pageImage.write(out);
				out.close();
				return true;
			}
			public int getPriority() {
				return 10; // we only want page images for one document, but those we really do want
			}
		});
		
		//	finally ...
		return doc;
	}
	private static InputStream getInputStream(String name, HashMap cache, File cacheFolder) throws IOException {
		if (cache == null)
			return new BufferedInputStream(new FileInputStream(new File(cacheFolder, name)));
		else return new ByteArrayInputStream((byte[]) cache.get(name));
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
		
		//	get ready to zip
		ZipOutputStream zout = new ZipOutputStream(out);
		BufferedWriter zbw = new BufferedWriter(new OutputStreamWriter(zout, "UTF-8"));
		StringVector keys = new StringVector();
		
		//	store document meta data
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
		StringRelation pagesData = new StringRelation();
		StringRelation wordsData = new StringRelation();
		StringRelation regsData = new StringRelation();
		ImPage[] pages = doc.getPages();
		for (int p = 0; p < pages.length; p++) {
			
			//	data for page
			StringTupel pageData = new StringTupel();
			pageData.setValue(PAGE_ID_ATTRIBUTE, ("" + pages[p].pageId));
			pageData.setValue(BOUNDING_BOX_ATTRIBUTE, pages[p].bounds.toString());
			pageData.setValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, getAttributesString(pages[p]));
			pagesData.addElement(pageData);
			
			//	data for words
			ImWord[] pageWords = pages[p].getWords();
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
		keys.clear();
		keys.parseAndAddElements((PAGE_ID_ATTRIBUTE + ";" + BOUNDING_BOX_ATTRIBUTE + ";" + ImObject.ATTRIBUTES_STRING_ATTRIBUTE), ";");
		zout.putNextEntry(new ZipEntry("pages.csv"));
		StringRelation.writeCsvData(zbw, pagesData, keys);
		zbw.flush();
		zout.closeEntry();
		
		//	store words
		keys.clear();
		keys.parseAndAddElements((PAGE_ID_ATTRIBUTE + ";" + BOUNDING_BOX_ATTRIBUTE + ";" + STRING_ATTRIBUTE + ";" + ImWord.PREVIOUS_WORD_ATTRIBUTE + ";" + ImWord.NEXT_WORD_ATTRIBUTE + ";" + ImWord.NEXT_RELATION_ATTRIBUTE + ";" + ImWord.TEXT_STREAM_TYPE_ATTRIBUTE + ";" + ImObject.ATTRIBUTES_STRING_ATTRIBUTE), ";");
		zout.putNextEntry(new ZipEntry("words.csv"));
		StringRelation.writeCsvData(zbw, wordsData, keys);
		zbw.flush();
		zout.closeEntry();
		
		//	store regions
		keys.clear();
		keys.parseAndAddElements((PAGE_ID_ATTRIBUTE + ";" + BOUNDING_BOX_ATTRIBUTE + ";" + TYPE_ATTRIBUTE + ";" + ImObject.ATTRIBUTES_STRING_ATTRIBUTE), ";");
		zout.putNextEntry(new ZipEntry("regions.csv"));
		StringRelation.writeCsvData(zbw, regsData, keys);
		zbw.flush();
		zout.closeEntry();
		
		//	store annotations
		StringRelation annotsData = new StringRelation();
		ImAnnotation[] annots = doc.getAnnotations();
		for (int a = 0; a < annots.length; a++) {
			StringTupel annotData = new StringTupel();
			annotData.setValue(ImAnnotation.FIRST_WORD_ATTRIBUTE, annots[a].getFirstWord().getLocalID());
			annotData.setValue(ImAnnotation.LAST_WORD_ATTRIBUTE, annots[a].getLastWord().getLocalID());
			annotData.setValue(TYPE_ATTRIBUTE, annots[a].getType());
			annotData.setValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, getAttributesString(annots[a]));
			annotsData.addElement(annotData);
		}
		keys.clear();
		keys.parseAndAddElements((ImAnnotation.FIRST_WORD_ATTRIBUTE + ";" + ImAnnotation.LAST_WORD_ATTRIBUTE + ";" + TYPE_ATTRIBUTE + ";" /* + ImAnnotation.FIRST_VALUE_ATTRIBUTE + ";" + ImAnnotation.LAST_VALUE_ATTRIBUTE + ";" */ + ImObject.ATTRIBUTES_STRING_ATTRIBUTE), ";");
		zout.putNextEntry(new ZipEntry("annotations.csv"));
		StringRelation.writeCsvData(zbw, annotsData, keys);
		zbw.flush();
		zout.closeEntry();
		
		//	store page images
		for (int p = 0; p < pages.length; p++) {
			PageImage pi = pages[p].getPageImage();
			String pin = PageImage.getPageImageName(doc.docId, pages[p].pageId);
			pin = ("page" + pin.substring(doc.docId.length() + 1) + "." + IMAGE_FORMAT);
			zout.putNextEntry(new ZipEntry(pin));
			pi.write(zout);
			zout.closeEntry();
			//	TODO consider putting page image attributes in 'pageImages.csv' and storing page images proper as plain PNGs
		}
		
		//	finally
		zout.flush();
		zout.close();
	}
	
	private static String getAttributesString(ImObject imo) {
		StringBuffer attributes = new StringBuffer();
		String[] ans = imo.getAttributeNames();
		for (int a = 0; a < ans.length; a++) {
			Object value = imo.getAttribute(ans[a]);
			if (value != null)
				attributes.append(ans[a] + "<" + escape(value.toString()) + ">");
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
		final String pdfName = "dikow_2012.pdf";
		
		ImDocument imDoc = loadDocument(new FileInputStream(new File(baseFolder, (pdfName + ".imf"))));
		ImPage[] imPages = imDoc.getPages();
		System.out.println("Document loaded, got " + imPages.length + " pages");
		for (int p = 0; p < imPages.length; p++) {
			ImWord[] imWords = imPages[p].getWords();
			System.out.println(" - got " + imWords.length + " words in page " + p);
			PageImage pi = PageImage.getPageImage(imDoc.docId, imPages[p].pageId);
			System.out.println(" - got page image, size is " + pi.image.getWidth() + "x" + pi.image.getHeight());
		}
//		
//		File imFile = new File(baseFolder, (pdfName + ".imf"));
//		OutputStream imOut = new BufferedOutputStream(new FileOutputStream(imFile));
//		storeDocument(imDoc, imOut);
//		imOut.flush();
//		imOut.close();
	}
}