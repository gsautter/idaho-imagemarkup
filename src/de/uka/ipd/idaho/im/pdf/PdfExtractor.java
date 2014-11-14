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
 *     * Neither the name of the Universität Karlsruhe (TH) nor the
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
package de.uka.ipd.idaho.im.pdf;

import java.awt.Color;
import java.awt.ComponentOrientation;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Vector;

import org.icepdf.core.exceptions.PDFException;
import org.icepdf.core.exceptions.PDFSecurityException;
import org.icepdf.core.io.SeekableByteArrayInputStream;
import org.icepdf.core.io.SeekableInputConstrainedWrapper;
import org.icepdf.core.pobjects.Catalog;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.PageTree;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.Resources;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.filters.FlateDecode;
import org.icepdf.core.pobjects.graphics.text.LineText;
import org.icepdf.core.pobjects.graphics.text.PageText;
import org.icepdf.core.util.GraphicsRenderingHints;
import org.icepdf.core.util.Library;
import org.jpedal.jbig2.JBIG2Decoder;
import org.jpedal.jbig2.JBIG2Exception;

import de.uka.ipd.idaho.easyIO.util.RandomByteSource;
import de.uka.ipd.idaho.gamta.Gamta;
import de.uka.ipd.idaho.gamta.MutableAnnotation;
import de.uka.ipd.idaho.gamta.TokenSequence;
import de.uka.ipd.idaho.gamta.Tokenizer;
import de.uka.ipd.idaho.gamta.util.ProgressMonitor;
import de.uka.ipd.idaho.gamta.util.constants.TableConstants;
import de.uka.ipd.idaho.gamta.util.imaging.BoundingBox;
import de.uka.ipd.idaho.gamta.util.imaging.ImagingConstants;
import de.uka.ipd.idaho.gamta.util.imaging.PageImage;
import de.uka.ipd.idaho.gamta.util.imaging.PageImageInputStream;
import de.uka.ipd.idaho.gamta.util.imaging.PageImageStore;
import de.uka.ipd.idaho.im.ImDocument;
import de.uka.ipd.idaho.im.ImPage;
import de.uka.ipd.idaho.im.ImRegion;
import de.uka.ipd.idaho.im.ImWord;
import de.uka.ipd.idaho.im.analysis.Imaging;
import de.uka.ipd.idaho.im.analysis.Imaging.AnalysisImage;
import de.uka.ipd.idaho.im.analysis.Imaging.Complex;
import de.uka.ipd.idaho.im.analysis.Imaging.ImagePartRectangle;
import de.uka.ipd.idaho.im.analysis.Imaging.Peak;
import de.uka.ipd.idaho.im.analysis.PageAnalysis;
import de.uka.ipd.idaho.im.analysis.PageImageAnalysis;
import de.uka.ipd.idaho.im.analysis.PageImageAnalysis.Block;
import de.uka.ipd.idaho.im.analysis.PageImageAnalysis.Line;
import de.uka.ipd.idaho.im.analysis.PageImageAnalysis.Region;
import de.uka.ipd.idaho.im.analysis.PageImageAnalysis.TableCell;
import de.uka.ipd.idaho.im.analysis.PageImageAnalysis.TableRow;
import de.uka.ipd.idaho.im.analysis.PageImageAnalysis.Word;
import de.uka.ipd.idaho.im.ocr.OcrEngine;
import de.uka.ipd.idaho.im.pdf.PdfParser.PImage;
import de.uka.ipd.idaho.im.pdf.PdfParser.PStream;
import de.uka.ipd.idaho.im.pdf.PdfParser.PWord;
import de.uka.ipd.idaho.im.pdf.test.PdfExtractorTest;
import de.uka.ipd.idaho.stringUtils.StringIndex;

/**
 * Utility for extracting page images from a PDF file.
 * 
 * @author sautter
 */
public class PdfExtractor implements ImagingConstants, TableConstants {
	
	private File basePath;
	private PageImageStore imageStore;
	
	private PdfImageDecoder imageDecoder;
	
	private OcrEngine ocrEngine;
	
	private int textPdfPageImageDpi;
	
	private boolean useMultipleCores = false;
	
	/**
	 * Constructor
	 * @param basePath the folder holding the binaries
	 * @param imageStore the page image store to store extracted page images in
	 */
	public PdfExtractor(File basePath, PageImageStore imageStore) {
		this(basePath, imageStore, false, -1);
	}
	
	/**
	 * Constructor
	 * @param basePath the folder holding the binaries
	 * @param imageStore the page image store to store extracted page images in
	 * @param textPdfPageImageDpi the resolution for rendering text PDFs
	 */
	public PdfExtractor(File basePath, PageImageStore imageStore, int textPdfPageImageDpi) {
		this(basePath, imageStore, false, textPdfPageImageDpi);
	}
	
	/**
	 * Constructor
	 * @param basePath the folder holding the binaries
	 * @param imageStore the page image store to store extracted page images in
	 * @param useMultipleCores use multiple CPU cores for PDF extraction if
	 *            possible (i.e., if multiple cores available)?
	 */
	public PdfExtractor(File basePath, PageImageStore imageStore, boolean useMultipleCores) {
		this(basePath, imageStore, useMultipleCores, -1);
	}
	
	/**
	 * Constructor
	 * @param basePath the folder holding the binaries
	 * @param imageStore the page image store to store extracted page images in
	 * @param useMultipleCores use multiple CPU cores for PDF extraction if
	 *            possible (i.e., if multiple cores available)?
	 * @param textPdfPageImageDpi the resolution for rendering text PDFs
	 */
	public PdfExtractor(File basePath, PageImageStore imageStore, boolean useMultipleCores, int textPdfPageImageDpi) {
		this.basePath = basePath;
		this.imageStore = imageStore;
		try {
			this.imageDecoder = new PdfImageDecoder(new File(this.basePath, "ImageMagick"));
		}
		catch (Exception e) {
			System.out.println("PdfExtractor: could not create image decoder - " + e.getMessage());
			e.printStackTrace(System.out);
		}
		try {
			this.ocrEngine = new OcrEngine(this.basePath, Math.max(1, (Runtime.getRuntime().availableProcessors()-1)));
			this.ocrEngine.addLangs("deu+eng+fra+por+spa"); // basically Latin letters with any derived diacritics occurring in western European languages, and thus colonial names and last names
		}
		catch (Exception e) {
			System.out.println("PdfExtractor: could not create OCR engine - " + e.getMessage());
			e.printStackTrace(System.out);
		}
		this.textPdfPageImageDpi = ((textPdfPageImageDpi < 1) ? defaultTextPdfPageImageDpi : textPdfPageImageDpi);
		this.useMultipleCores = useMultipleCores;
	}
	
	/**
	 * Test whether or not the PDF extractor has an OCR engine available and
	 * thus provides the ability to load image based PDFs.
	 * @return true if OCR is available, false otherwise
	 */
	public boolean isOcrAvailable() {
		return (this.ocrEngine != null);
	}
	
	/**
	 * Retrieve the OCR Engine embedded in this PDF Extractor. If the
	 * <code>isOcrAvailable()</code> method returns false, this method returns
	 * null.
	 * @return the OCR Engine
	 */
	public OcrEngine getOcrEngine() {
		return this.ocrEngine;
	}
	
	/**
	 * Shut down the PDF extractor, in particular its embedded OCR engine.
	 */
	public void shutdown() {
		if (this.ocrEngine != null) try {
			this.ocrEngine.shutdown();
		}
		catch (IOException ioe) {
			System.out.println("PdfExtractor: error shutting down OCR engine - " + ioe.getMessage());
			ioe.printStackTrace(System.out);
		}
	}
	
	/**
	 * Load a document from a PDF. Page images are stored in the page image
	 * store handed to the constructor. The document structure is stored in the
	 * argument image markup document, including textual contents if available.
	 * @param pdfBytes the raw binary data the the PDF document was parsed from
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the argument GAMTA document
	 * @throws IOException
	 */
	public ImDocument loadGenericPdf(byte[] pdfBytes, ProgressMonitor pm) throws IOException {
		return this.loadGenericPdf(null, null, pdfBytes, 1, pm);
	}

	/**
	 * Load a document from a PDF. Page images are stored in the page image
	 * store handed to the constructor. The document structure is stored in the
	 * argument image markup document, including textual contents if available.
	 * If the argument ImDocument is null, this method creates a new one with
	 * the MD5 checksum of the argument PDF bytes as the ID.
	 * @param doc the document to store the structural information in
	 * @param pdfDoc the PDF document to parse
	 * @param pdfBytes the raw binary data the the PDF document was parsed from
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the argument GAMTA document
	 * @throws IOException
	 */
	public ImDocument loadGenericPdf(ImDocument doc, Document pdfDoc, byte[] pdfBytes, ProgressMonitor pm) throws IOException {
		return this.loadGenericPdf(doc, pdfDoc, pdfBytes, 1, pm);
	}
	
	/**
	 * Load a document from a PDF. Page images are stored in the page image
	 * store handed to the constructor. The document structure is stored in the
	 * argument image markup document, including textual contents if available.
	 * The scale factor helps with PDFs whose page boundaries (media boxes) are
	 * set too small. This can be recognized in any PDF viewer by the fact that
	 * the document is tiny and the writing very small at 100% size display. It
	 * can also be detected automatically, e.g. by means of examining the
	 * average height of lines in comparison to the DPI number. If the argument
	 * ImDocument is null, this method creates a new one with the MD5 checksum
	 * of the argument PDF bytes as the ID.
	 * @param doc the document to store the structural information in
	 * @param pdfDoc the PDF document to parse
	 * @param pdfBytes the raw binary data the the PDF document was parsed from
	 * @param scaleFactor the scale factor for image PDFs, to correct PDFs that
	 *            are set too small (DPI number is divided by this factor)
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the argument GAMTA document
	 * @throws IOException
	 */
	public ImDocument loadGenericPdf(ImDocument doc, Document pdfDoc, byte[] pdfBytes, int scaleFactor, ProgressMonitor pm) throws IOException {
		if (doc == null)
			doc = new ImDocument(getChecksum(pdfBytes));
		if (pdfDoc == null) try {
			pdfDoc = new Document();
			pdfDoc.setInputStream(new ByteArrayInputStream(pdfBytes), "");
		}
		catch (PDFException pdfe) {
			throw new IOException(pdfe.getMessage(), pdfe);
		}
		catch (PDFSecurityException pdfse) {
			throw new IOException(pdfse.getMessage(), pdfse);
		}
		if (pm == null)
			pm = ProgressMonitor.dummy;
		/*
		 * try image loading first, and use text loading only if image loading
		 * fails, as if both images and text are present, the text may well be
		 * some crude OCR we do not want.
		 */
		try {
			return this.loadImagePdf(doc, pdfDoc, pdfBytes, scaleFactor, pm);
		}
		catch (IOException ioe) {
			ioe.printStackTrace(System.out);
			pm.setInfo("Could not find images for all pages, loading PDF as text");
			return this.loadTextPdf(doc, pdfDoc, pdfBytes, pm);
		}
	}
	
	private static final int defaultDpi = 72; // default according to PDF specification
	
	private static final int defaultTextPdfPageImageDpi = 150; // looks a lot better
	private static final int minAveragePageWords = 25; // this should be exceeded by every digital-born PDF
	
	//	A5 paper format in inches: 5.83 × 8.27
	private static final float a5inchWidth = 5.83f;
	private static final float a5inchHeigth = 8.27f;
	private static final int minScaleUpDpi = 301;
	
	//	threshold for scaling down scans
	private static final int minScaleDownDpi = 400;
	
	/**
	 * Load a document from a textual PDF, usually a digital-born PDF. Page
	 * images are stored in the page image store handed to the constructor. The
	 * document structure is stored in the argument image markup document,
	 * including textual contents with word bounding boxes.
	 * @param pdfBytes the raw binary data the the PDF document was parsed from
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the image markup document
	 * @throws IOException
	 */
	public ImDocument loadTextPdf(byte[] pdfBytes, ProgressMonitor pm) throws IOException {
		return this.loadTextPdf(null, null, pdfBytes, pm);
	}
	
	/**
	 * Load a document from a textual PDF, usually a digital-born PDF. Page
	 * images are stored in the page image store handed to the constructor. The
	 * document structure is stored in the argument image markup document,
	 * including textual contents with word bounding boxes. If the argument
	 * ImDocument is null, this method creates a new one with the MD5 checksum
	 * of the argument PDF bytes as the ID.
	 * @param doc the document to store the structural information in
	 * @param pdfDoc the PDF document to parse
	 * @param pdfBytes the raw binary data the the PDF document was parsed from
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the argument image markup document
	 * @throws IOException
	 */
	public ImDocument loadTextPdf(ImDocument doc, Document pdfDoc, byte[] pdfBytes, ProgressMonitor pm) throws IOException {
		if (doc == null)
			doc = new ImDocument(getChecksum(pdfBytes));
		if (pdfDoc == null) try {
			pdfDoc = new Document();
			pdfDoc.setInputStream(new ByteArrayInputStream(pdfBytes), "");
		}
		catch (PDFException pdfe) {
			throw new IOException(pdfe.getMessage(), pdfe);
		}
		catch (PDFSecurityException pdfse) {
			throw new IOException(pdfse.getMessage(), pdfse);
		}
		if (pm == null)
			pm = ProgressMonitor.dummy;
		return this.doLoadTextPdf(doc, pdfDoc, pdfBytes, pm);
	}
	
	private ImDocument doLoadTextPdf(final ImDocument doc, final Document pdfDoc, byte[] pdfBytes, final ProgressMonitor pm) throws IOException {
		
		//	load document structure (IcePDF is better at that ...)
		final Catalog catalog = pdfDoc.getCatalog();
		final PageTree pageTree = catalog.getPageTree();
		final float magnification = (((float) this.textPdfPageImageDpi) / defaultDpi);
		
		//	parse PDF
		final HashMap objects = PdfParser.getObjects(pdfBytes);
		
		//	get tokenizer to check and split words with
		final Tokenizer tokenizer = ((Tokenizer) doc.getAttribute(ImDocument.TOKENIZER_ATTRIBUTE, Gamta.INNER_PUNCTUATION_TOKENIZER));
		
		//	prepare working in parallel
		final LinkedList pageIDs = new LinkedList();
		final Throwable[] error = {null};
		
		//	extract page objects
		pm.setStep("Importing page words");
		pm.setBaseProgress(0);
		pm.setProgress(0);
		pm.setMaxProgress(40);
		final Rectangle2D.Float[] pageBoxes = new Rectangle2D.Float[pageTree.getNumberOfPages()];
		final PWord[][] pWords = new PWord[pageTree.getNumberOfPages()][];
		pageIDs.clear();
		if (PdfExtractorTest.aimAtPage != -1)
			pageIDs.addLast(new Integer(PdfExtractorTest.aimAtPage));
		else for (int p = 0; p < pageTree.getNumberOfPages(); p++)
			pageIDs.addLast(new Integer(p));
		error[0] = null;
		this.runJob( new Runnable() {
			public void run() {
				while (true) try {
					
					//	any error or other problems in siblings?
					synchronized (error) {
						if (error[0] != null)
							return;
					}
					
					//	get next page ID to work off
					int p;
					synchronized (pageIDs) {
						if (pageIDs.isEmpty())
							return;
						p = ((Integer) pageIDs.removeFirst()).intValue();
					}
					
					//	update status display (might be inaccurate, but better than lock escalation)
					synchronized (pm) {
						pm.setInfo("Importing page " + (pageTree.getNumberOfPages() - pageIDs.size()) + " of " + pageTree.getNumberOfPages());
						pm.setProgress(( (pageTree.getNumberOfPages() - pageIDs.size() - 1) * 100) / pageTree.getNumberOfPages());
					}
					
					//	get page bounding box
					Page page = pageTree.getPage(p, "");
					pageBoxes[p] = page.getCropBox();
					if (pageBoxes[p] == null)
						pageBoxes[p] = page.getMediaBox();
					
					//	extract page contents to recover layout information
					System.out.println("Page content is " + page.getEntries());
					Object contentsObj = PdfParser.getObject(page.getEntries(), "Contents", objects);
					pWords[p] = null;
					if (contentsObj == null) synchronized (pm) {
						pm.setInfo(" --> content not found");
					}
					else {
						synchronized (pm) {
							pm.setInfo(" --> got content");
						}
						ByteArrayOutputStream baos = new ByteArrayOutputStream();
						if (contentsObj instanceof PStream) {
							Object filter = ((PStream) contentsObj).params.get("Filter");
							synchronized (pm) {
								pm.setInfo("   --> stream content, filter is " + filter + " (from " + ((PStream) contentsObj).params + ")");
							}
//							try {
								PdfParser.decode(filter, ((PStream) contentsObj).bytes, ((PStream) contentsObj).params, baos, objects);
//							} catch (Exception e) { pm.setInfo("   --> decoding failed: " + e.getMessage()); }
						}
						else if (contentsObj instanceof Vector) {
							synchronized (pm) {
								pm.setInfo("   --> array content");
							}
							for (Iterator cit = ((Vector) contentsObj).iterator(); cit.hasNext();) {
								Object contentObj = PdfParser.dereference(cit.next(), objects);
								if (contentObj instanceof PStream) {
									Object filter = ((PStream) contentObj).params.get("Filter");
									if (filter == null)
										continue;
									pm.setInfo("   --> stream content part, filter is " + filter + " (from " + ((PStream) contentObj).params + ")");
//									try {
										 PdfParser.decode(filter, ((PStream) contentObj).bytes, ((PStream) contentObj).params, baos, objects);
//									} catch (Exception e) { pm.setInfo("   --> decoding failed: " + e.getMessage()); }
								}
							}
						}
						Object resourcesObj = PdfParser.getObject(page.getEntries(), "Resources", objects);
						if (resourcesObj == null) synchronized (pm) {
							pm.setInfo(" --> resources not found");
						}
						else {
							resourcesObj = PdfParser.dereference(resourcesObj, objects);
							synchronized (pm) {
								pm.setInfo(" --> resources are " + resourcesObj);
							}
							pWords[p] = PdfParser.getPageWords(baos.toByteArray(), ((Hashtable) resourcesObj), objects, tokenizer, pm);
							System.out.println(" --> extracted " + pWords[p].length + " words");
							for (int w = 0; w < pWords[p].length; w++)
								System.out.println("   - " + pWords[p][w]);
						}
					}
					
					if ((pWords[p] == null) || (pWords[p].length == 0)) {
						synchronized (pm) {
							pm.setInfo(" --> empty page");
						}
						continue;
					}
					
					synchronized (pm) {
						pm.setInfo(" - got page text with " + pWords[p].length + " words");
					}
					
					//	shrink word bounding boxes to actual word size
					BufferedImage mbi = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY);
					Graphics2D mg = mbi.createGraphics();
					for (int w = 0; w < pWords[p].length; w++) {
						if (pWords[p][w].str.trim().length() == 0)
							continue;
						Rectangle2D wb = pWords[p][w].bounds;
						System.out.println("Measuring word " + pWords[p][w].str + " at " + wb);
						
						//	convert bounds, as PDF Y coordinate is bottom-up, whereas Java, JavaScript, etc. Y coordinate is top-down
						//	NO ADJUSTMENT TO JAVA COORDINATES HERE, WE'RE TRANSFORMING ALL WORDS LATER ON
						float left = ((float) wb.getMinX());
						float right = ((float) wb.getMaxX());
						float top = ((float) wb.getMaxY());
						float bottom = ((float) wb.getMinY());
						
						//	compute word baseline from font baseline and word box height
						float fontHeight = (Math.abs(pWords[p][w].font.ascent) + Math.abs(pWords[p][w].font.descent));
						float baseline = (top - (((top - bottom) * pWords[p][w].font.ascent) / fontHeight));
						
						//	prepare font for test rendering
						int fontStyle = Font.PLAIN;
						if (pWords[p][w].bold)
							fontStyle = (fontStyle | Font.BOLD);
						if (pWords[p][w].italics)
							fontStyle = (fontStyle | Font.ITALIC);
						Font mf = getFont(pWords[p][w].font.name, fontStyle, Math.round(((float) pWords[p][w].fontSize) * 1));
						mg.setFont(mf);
						
						//	adjust word size and vertical position
						TextLayout wtl = new TextLayout((pWords[p][w].str + "IpHq"), mf, mg.getFontRenderContext());
						System.out.println(" - word rendering box is " + wtl.getBounds());
						float boundingBoxY = ((float) wtl.getBounds().getY());
						float boundingBoxHeight = ((float) wtl.getBounds().getHeight());
						System.out.println(" - top is " + top + ", bottom is " + bottom + ", baseline is " + baseline + ", ascent is " + wtl.getAscent() + ", descent is " + wtl.getDescent());
						
						//	if computed bounding box smaller (lower top, higher bottom) than one from PDF, shrink it
						float adjustedTop = (baseline - boundingBoxY);
						float adjustedBottom = (adjustedTop - boundingBoxHeight);
						System.out.println(" - word box y is " + boundingBoxY + ", word box height is " + boundingBoxHeight);
						System.out.println(" - adjusted top is " + adjustedTop + ", adjusted bottom is " + adjustedBottom);
						if (((top - adjustedTop) > 0.25) || ((adjustedBottom - bottom) > 0.25)) {
							PWord pw = new PWord(pWords[p][w].str, new Rectangle2D.Float(left, adjustedBottom, (right - left), (adjustedTop - adjustedBottom)), pWords[p][w].fontSize, pWords[p][w].font);
							pWords[p][w] = pw;
							System.out.println(" ==> adjusted bounding box to " + pw.bounds);
						}
					}
					
					//	sort words left to right and top to bottom
					Arrays.sort(pWords[p], new Comparator() {
						public int compare(Object o1, Object o2) {
							PWord pw1 = ((PWord) o1);
							PWord pw2 = ((PWord) o2);
							return Double.compare(pw1.bounds.getMinX(), pw2.bounds.getMinX());
						}
					});
					Arrays.sort(pWords[p], new Comparator() {
						public int compare(Object o1, Object o2) {
							PWord pw1 = ((PWord) o1);
							PWord pw2 = ((PWord) o2);
							double m1 = ((pw1.bounds.getMinY() + pw1.bounds.getMaxY()) / 2);
							double m2 = ((pw2.bounds.getMinY() + pw2.bounds.getMaxY()) / 2);
							if ((m1 < pw2.bounds.getMaxY()) && (m1 > pw2.bounds.getMinY()))
								return 0;
							if ((m2 < pw1.bounds.getMaxY()) && (m2 > pw1.bounds.getMinY()))
								return 0;
							if (m1 > pw2.bounds.getMaxY())
								return -1;
							if (m1 < pw2.bounds.getMinY())
								return 1;
							if (m2 > pw1.bounds.getMaxY())
								return 1;
							if (m2 < pw1.bounds.getMinY())
								return -1;
							return 0;
						}
					});
					
					//	merge subsequent words less than DPI/60 apart (likely separated due to font change)
					//	however, do not merge words that tokenize apart,
					//	nor ones that have mismatch in font style or size, as such differences usually bear some semantics
					int maxMergeMargin = (textPdfPageImageDpi / 60);
					ArrayList pWordList = null;
					PWord lpWord = null;
					BoundingBox lpWordBox = null;
					TokenSequence lpWordTokens = null;
					System.out.println("Checking word mergers ...");
					for (int w = 0; w < pWords[p].length; w++) {
						
						//	generate bounding box
						int bbLeft = Math.round(((float) (pWords[p][w].bounds.getMinX() - pageBoxes[p].getMinX())) * magnification);
						int bbRight = Math.round(((float) (pWords[p][w].bounds.getMaxX() - pageBoxes[p].getMinX()))  * magnification);
						int bbTop = Math.round((pageBoxes[p].height - ((float) (pWords[p][w].bounds.getMaxY() - ((2 * pageBoxes[p].getMinY()) - pageBoxes[p].getMaxY())))) * magnification);
						int bbBottom = Math.round((pageBoxes[p].height - ((float) (pWords[p][w].bounds.getMinY() - ((2 * pageBoxes[p].getMinY()) - pageBoxes[p].getMaxY())))) * magnification);
						BoundingBox pWordBox = new BoundingBox(bbLeft, bbRight, bbTop, bbBottom);
						
						//	no word to compare to
						if ((lpWord == null) || (lpWordBox == null) || (lpWordTokens == null)) {
							lpWord = pWords[p][w];
							lpWordBox = pWordBox;
							lpWordTokens = tokenizer.tokenize(lpWord.str);
							continue;
						}
						System.out.println(" - checking words " + lpWordBox + " and " + pWordBox);
						
						//	not in same line
						if ((pWordBox.top >= lpWordBox.bottom) || (lpWordBox.top >= pWordBox.bottom)) {
							lpWord = pWords[p][w];
							lpWordBox = pWordBox;
							lpWordTokens = tokenizer.tokenize(lpWord.str);
							System.out.println(" --> different lines");
							continue;
						}
						
						//	too far a gap
						if ((pWordBox.left < lpWordBox.left) || ((pWordBox.left - lpWordBox.right) > maxMergeMargin)) {
							lpWord = pWords[p][w];
							lpWordBox = pWordBox;
							lpWordTokens = tokenizer.tokenize(lpWord.str);
							System.out.println(" --> too far apart");
							continue;
						}
						
						//	do not join words with altering bold/italic properties or font sizes, they differ for a reason
						if ((lpWord.bold != pWords[p][w].bold) || (lpWord.italics != pWords[p][w].italics) || (lpWord.fontSize != pWords[p][w].fontSize)) {
							lpWord = pWords[p][w];
							lpWordBox = pWordBox;
							lpWordTokens = tokenizer.tokenize(lpWord.str);
							System.out.println(" --> font size or style mismatch");
							continue;
						}
						
						//	do NOT merge words that tokenize apart
						TokenSequence pWordTokens = tokenizer.tokenize(pWords[p][w].str);
						TokenSequence mpWordTokens = tokenizer.tokenize(lpWord.str + pWords[p][w].str);
						if ((lpWordTokens.size() + pWordTokens.size()) <= mpWordTokens.size()) {
							lpWord = pWords[p][w];
							lpWordBox = pWordBox;
							lpWordTokens = pWordTokens;
							System.out.println(" --> tokenization mismatch");
							continue;
						}
						
						//	figure out bold, italic, font size, etc. using weighted majority vote
						PdfFont font = (((lpWordBox.right - lpWordBox.left) < (pWordBox.right - pWordBox.left)) ? pWords[p][w].font : lpWord.font);
						float top = ((float) Math.max(lpWord.bounds.getMaxY(), pWords[p][w].bounds.getMaxY()));
						float bottom = ((float) Math.min(lpWord.bounds.getMinY(), pWords[p][w].bounds.getMinY()));
						
						//	create merged word
						PWord mpWord = new PWord((lpWord.str + pWords[p][w].str), new Rectangle2D.Float(((float) lpWord.bounds.getMinX()), bottom, ((float) (pWords[p][w].bounds.getMaxX() - lpWord.bounds.getMinX())), (top - bottom)), lpWord.fontSize, font);
						BoundingBox mpWordBox = new BoundingBox(lpWordBox.left, pWordBox.right, Math.min(lpWordBox.top, pWordBox.top), Math.max(lpWordBox.bottom, pWordBox.bottom));
						System.out.println(" --> merged words " + lpWord.str + " and " + pWords[p][w].str + " to " + mpWord.str + " " + mpWordBox);
						
						//	store merged word
						pWords[p][w] = mpWord;
						pWords[p][w-1] = null;
						lpWord = mpWord;
						lpWordBox = mpWordBox;
						lpWordTokens = mpWordTokens;
						
						//	remember merger
						if (pWordList == null)
							pWordList = new ArrayList();
					}
					
					//	refresh PWord array
					if (pWordList != null) {
						for (int w = 0; w < pWords[p].length; w++) {
							if (pWords[p][w] != null)
								pWordList.add(pWords[p][w]);
						}
						pWords[p] = ((PWord[]) pWordList.toArray(new PWord[pWordList.size()]));
						pWordList = null;
					}
					
					//	split words that tokenize apart, splitting bounding box based on font measurement
					pWordList = new ArrayList();
					System.out.println("Checking word splits ...");
					for (int w = 0; w < pWords[p].length; w++) {
						
						//	check tokenization
						TokenSequence pWordTokens = tokenizer.tokenize(pWords[p][w].str);
						if (pWordTokens.size() < 2) {
							pWordList.add(pWords[p][w]);
							continue;
						}
						System.out.println(" - splitting " + pWords[p][w].str);
						
						//	get width for each token at word font size
						String[] splitTokens = new String[pWordTokens.size()];
						float[] splitTokenWidths = new float[pWordTokens.size()];
						int fontStyle = Font.PLAIN;
						if (pWords[p][w].bold)
							fontStyle = (fontStyle | Font.BOLD);
						if (pWords[p][w].italics)
							fontStyle = (fontStyle | Font.ITALIC);
						Font pWordFont = getFont(pWords[p][w].font.name, fontStyle, Math.round(((float) pWords[p][w].fontSize) * magnification));
						float splitTokenWidthSum = 0;
						for (int s = 0; s < splitTokens.length; s++) {
							splitTokens[s] = pWordTokens.valueAt(s);
							TextLayout tl = new TextLayout(splitTokens[s], pWordFont, new FontRenderContext(null, false, true));
							splitTokenWidths[s] = ((float) tl.getBounds().getWidth());
							splitTokenWidthSum += splitTokenWidths[s];
						}
						
						//	store split result, splitting word bounds accordingly
						float pWordWidth = ((float) pWords[p][w].bounds.getWidth());
						float splitTokenStart = ((float) pWords[p][w].bounds.getMinX());
						for (int s = 0; s < splitTokens.length; s++) {
							float splitTokenWidth = ((pWordWidth * splitTokenWidths[s]) / splitTokenWidthSum);
							PWord spWord = new PWord(splitTokens[s], new Rectangle2D.Float(splitTokenStart, ((float) pWords[p][w].bounds.getMinY()), splitTokenWidth, ((float) pWords[p][w].bounds.getHeight())), pWords[p][w].fontSize, pWords[p][w].font);
							pWordList.add(spWord);
							splitTokenStart += splitTokenWidth;
							System.out.println(" --> split word part " + spWord.str + ", bounds are " + spWord.bounds);
						}
					}
					
					//	refresh PWord array
					if (pWordList.size() != pWords[p].length)
						pWords[p] = ((PWord[]) pWordList.toArray(new PWord[pWordList.size()]));
					pWordList = null;
					synchronized (pm) {
						if (pWords[p].length == 0)
							pm.setInfo(" --> empty page");
						else pm.setInfo(" --> got " + pWords[p].length + " words in PDF");
					}
				}
				catch (Throwable t) {
					synchronized (error) {
						error[0] = t;
					}
					return;
				}
			}
		}, pageTree.getNumberOfPages());
		pm.setProgress(100);
		
		//	check errors
		if (error[0] != null) {
			if (error[0] instanceof RuntimeException)
				throw ((RuntimeException) error[0]);
			else if (error[0] instanceof Error)
				throw ((Error) error[0]);
			else if (error[0] instanceof IOException)
				throw ((IOException) error[0]);
			else throw new IOException(error[0].getMessage());
		}
		
		//	check plausibility
		if (PdfExtractorTest.aimAtPage == -1) {
			int docWordCount = 0;
			for (int p = 0; p < pWords.length; p++)
				docWordCount += pWords[p].length;
			if ((docWordCount / pageTree.getNumberOfPages()) < minAveragePageWords)
				throw new IOException("Too few words per page (" + docWordCount + " on " + pageTree.getNumberOfPages() + " pages, less than " + minAveragePageWords + ")");
		}
		
		//	put PDF words into images
		pm.setStep("Generating page images");
		pm.setBaseProgress(40);
		pm.setProgress(0);
		pm.setMaxProgress(70);
		final BufferedImage[] pageImages = new BufferedImage[pageTree.getNumberOfPages()];
		final String[] imageNames = new String[pageTree.getNumberOfPages()];
		final int[] imageDPIs = new int[pageTree.getNumberOfPages()];
		pageIDs.clear();
		if (PdfExtractorTest.aimAtPage != -1)
			pageIDs.addLast(new Integer(PdfExtractorTest.aimAtPage));
		else for (int p = 0; p < pageTree.getNumberOfPages(); p++)
			pageIDs.addLast(new Integer(p));
		error[0] = null;
		this.runJob(new Runnable() {
			public void run() {
				while (true) try {
					
					//	any error or other problems in siblings?
					synchronized (error) {
						if (error[0] != null)
							return;
					}
					
					//	get next page ID to work off
					int p;
					synchronized (pageIDs) {
						if (pageIDs.isEmpty())
							return;
						p = ((Integer) pageIDs.removeFirst()).intValue();
					}
					
					//	update status display (might be inaccurate, but better than lock escalation)
					synchronized (pm) {
						pm.setInfo("Extracting image of page " + (pageTree.getNumberOfPages() - pageIDs.size()) + " of " + pageTree.getNumberOfPages());
						pm.setProgress(( (pageTree.getNumberOfPages() - pageIDs.size() - 1) * 100) / pageTree.getNumberOfPages());
					}
					
					//	page image generated before
					if (imageStore.isPageImageAvailable(doc.docId, p)) {
						PageImage pi = imageStore.getPageImage(doc.docId, p);
						pageImages[p] = pi.image;
						imageNames[p] = PageImage.getPageImageName(doc.docId, p);
						imageDPIs[p] = pi.currentDpi;
						synchronized (pm) {
							pm.setInfo(" --> loaded page image generated earlier");
						}
					}
					
					//	generate page image
					else {
						synchronized (pm) {
							pm.setInfo(" - generating page image");
							pageImages[p] = ((BufferedImage) pdfDoc.getPageImage(p, GraphicsRenderingHints.SCREEN, Page.BOUNDARY_CROPBOX, 0, magnification));
							if (pageImages[p] == null) {
								pm.setInfo(" --> page image generation failed");
								throw new RuntimeException("Could not generate image for page " + p);
							}
							pm.setInfo(" - got page image sized " + pageImages[p].getWidth() + " x " + pageImages[p].getHeight());
						}
						
						//	erase words from IcePDF (can have faulty bounding boxes ...), and paint our own words onto the image
						int cleaningTolerance = (textPdfPageImageDpi / 25); // somewhat less than one mm ... 
						Page page = pageTree.getPage(p, "");
						PageText rpt = page.getText();
						ArrayList rpLines = rpt.getPageLines();
						Graphics2D rg = pageImages[p].createGraphics();
						rg.setColor(Color.WHITE);
						for (int l = 0; l < rpLines.size(); l++) {
							LineText lt = ((LineText) rpLines.get(l));
							Rectangle2D.Float wb = lt.getBounds();
							
							//	convert bounds, as PDF Y coordinate is bottom-up, whereas Java, JavaScript, etc. Y coordinate is top-down
							int left = Math.round((wb.x - pageBoxes[p].x) * magnification);
							int right = Math.round(((wb.x - pageBoxes[p].x) + wb.width)  * magnification);
							int top = Math.round((pageBoxes[p].height - ((wb.y - (pageBoxes[p].y - pageBoxes[p].height)) + wb.height)) * magnification);
							int bottom = Math.round((pageBoxes[p].height - (wb.y - (pageBoxes[p].y - pageBoxes[p].height))) * magnification);
							rg.fillRect((left - (cleaningTolerance / 2)), (top - (cleaningTolerance / 2)), (right - left + cleaningTolerance), (bottom - top + cleaningTolerance));
						}
						
						//	clean up black page margins (outmost two pixels)
						rg.fillRect(0, 0, pageImages[p].getWidth(), 2);
						rg.fillRect(0, (pageImages[p].getHeight() - 2), pageImages[p].getWidth(), 2);
						rg.fillRect(0, 0, 2, pageImages[p].getHeight());
						rg.fillRect((pageImages[p].getWidth() - 2), 0, 2, pageImages[p].getHeight());
						
						//	clean whole page
						for (int w = 0; w < pWords[p].length; w++) {
							Rectangle2D wb = pWords[p][w].bounds;
							
							//	convert bounds, as PDF Y coordinate is bottom-up, whereas Java, JavaScript, etc. Y coordinate is top-down
							int left = Math.round(((float) (wb.getMinX() - pageBoxes[p].getMinX())) * magnification);
							int right = Math.round(((float) (wb.getMaxX() - pageBoxes[p].getMinX()))  * magnification);
							int top = Math.round((pageBoxes[p].height - ((float) (wb.getMaxY() - ((2 * pageBoxes[p].getMinY()) - pageBoxes[p].getMaxY())))) * magnification);
							int bottom = Math.round((pageBoxes[p].height - ((float) (wb.getMinY() - ((2 * pageBoxes[p].getMinY()) - pageBoxes[p].getMaxY())))) * magnification);
							
							//	clean background
							rg.setColor(Color.WHITE);
							rg.fillRect(left, top, (right - left + 1), (bottom - top));
						}
						
						//	paint own words
						for (int w = 0; w < pWords[p].length; w++) {
							Rectangle2D wb = pWords[p][w].bounds;
							
							//	convert bounds, as PDF Y coordinate is bottom-up, whereas Java, JavaScript, etc. Y coordinate is top-down
							int left = Math.round(((float) (wb.getMinX() - pageBoxes[p].getMinX())) * magnification);
							int right = Math.round(((float) (wb.getMaxX() - pageBoxes[p].getMinX()))  * magnification);
//							int top = Math.round((pageBox.height - ((float) (wb.getMaxY() - ((2 * pageBox.getMinY()) - pageBox.getMaxY())))) * magnification);
							int bottom = Math.round((pageBoxes[p].height - ((float) (wb.getMinY() - ((2 * pageBoxes[p].getMinY()) - pageBoxes[p].getMaxY())))) * magnification);
							
							//	prepare font
							rg.setColor(Color.BLACK);
							int fontStyle = Font.PLAIN;
							if (pWords[p][w].bold)
								fontStyle = (fontStyle | Font.BOLD);
							if (pWords[p][w].italics)
								fontStyle = (fontStyle | Font.ITALIC);
							Font rf = getFont(pWords[p][w].font.name, fontStyle, Math.round(((float) pWords[p][w].fontSize) * magnification));
							rg.setFont(rf);
							
							//	adjust word size and vertical position
							LineMetrics wlm = rf.getLineMetrics(pWords[p][w].str, rg.getFontRenderContext());
							TextLayout wtl = new TextLayout(pWords[p][w].str, rf, rg.getFontRenderContext());
							double hScale = (((double) (right - left)) / wtl.getBounds().getWidth());
							if (hScale < 1) {
								AffineTransform at = rg.getTransform();
								rg.translate(left, 0);
								rg.scale(hScale, 1);
								rg.drawString(pWords[p][w].str, 0, (bottom - (pWords[p][w].font.hasDescent ? Math.round(wlm.getDescent()) : 0)));
								rg.setTransform(at);
							}
							else rg.drawString(pWords[p][w].str, left, (bottom - (pWords[p][w].font.hasDescent ? Math.round(wlm.getDescent()) : 0)));
						}
						
						//	cannot cut margins here because otherwise PDF word assignment gets broken
						imageNames[p] = imageStore.storePageImage(doc.docId, p, pageImages[p], textPdfPageImageDpi);
						if (imageNames[p] == null) {
							synchronized (pm) {
								pm.setInfo(" --> page image storage failed");
							}
							throw new RuntimeException("Could not store image of page " + p);
						}
						else {
							synchronized (pm) {
								pm.setInfo(" --> page image stored");
							}
							imageDPIs[p] = textPdfPageImageDpi;
						}
					}
				}
				catch (Throwable t) {
					synchronized (error) {
						error[0] = t;
					}
					return;
				}
			}
		}, pageTree.getNumberOfPages());
		pm.setProgress(100);
		
		//	check errors
		if (error[0] != null) {
			if (error[0] instanceof RuntimeException)
				throw ((RuntimeException) error[0]);
			else if (error[0] instanceof Error)
				throw ((Error) error[0]);
			else if (error[0] instanceof IOException)
				throw ((IOException) error[0]);
			else throw new IOException(error[0].getMessage());
		}
		
		//	analyze page structure
		pm.setStep("Analyzing page structure");
		pm.setBaseProgress(70);
		pm.setProgress(0);
		pm.setMaxProgress(98);
		final ImPage[] pages = new ImPage[pageTree.getNumberOfPages()];
		pageIDs.clear();
		if (PdfExtractorTest.aimAtPage != -1) {
			pageIDs.addLast(new Integer(PdfExtractorTest.aimAtPage));
			for (int p = 0; p < pages.length; p++)
				if (p != PdfExtractorTest.aimAtPage) {
					BoundingBox pageBounds = new BoundingBox(0, 32, 0, 44); // have to use these numbers to prevent 0 or 1 step in page word index
					ImPage page = new ImPage(doc, p, pageBounds);
				}
		}
		else for (int p = 0; p < pageTree.getNumberOfPages(); p++)
			pageIDs.addLast(new Integer(p));
		error[0] = null;
		this.runJob(new Runnable() {
			public void run() {
				while (true) try {
					
					//	any error or other problems in siblings?
					synchronized (error) {
						if (error[0] != null)
							return;
					}
					
					//	get next page ID to work off
					int p;
					synchronized (pageIDs) {
						if (pageIDs.isEmpty())
							return;
						p = ((Integer) pageIDs.removeFirst()).intValue();
						
						//	generate page (we have to synchronize this, as it adds to main document)
						pages[p] = new ImPage(doc, p, new BoundingBox(0, pageImages[p].getWidth(), 0, pageImages[p].getHeight()));
					}
					
					//	add words to page, and index them by bounding boxes
					HashMap wordsByBoxes = new HashMap();
					for (int w = 0; w < pWords[p].length; w++) {
						Rectangle2D wb = pWords[p][w].bounds;
						
						//	convert bounds, as PDF Y coordinate is bottom-up, whereas Java, JavaScript, etc. Y coordinate is top-down
						int left = Math.round(((float) (wb.getMinX() - pageBoxes[p].getMinX())) * magnification);
						int right = Math.round(((float) (wb.getMaxX() - pageBoxes[p].getMinX()))  * magnification);
						if ((right - left) < 4)
							right = (left + 4);
						int top = Math.round((pageBoxes[p].height - ((float) (wb.getMaxY() - ((2 * pageBoxes[p].getMinY()) - pageBoxes[p].getMaxY())))) * magnification);
						int bottom = Math.round((pageBoxes[p].height - ((float) (wb.getMinY() - ((2 * pageBoxes[p].getMinY()) - pageBoxes[p].getMaxY())))) * magnification);
						
						//	add word to page
						ImWord word = new ImWord(pages[p], new BoundingBox(left, right, top, bottom), pWords[p][w].str);
						
						//	set layout attributes
						if ((pWords[p][w].font != null) && (pWords[p][w].font.name != null))
							word.setAttribute(FONT_NAME_ATTRIBUTE, pWords[p][w].font.name);
						if (pWords[p][w].fontSize != -1)
							word.setAttribute(FONT_SIZE_ATTRIBUTE, ("" + pWords[p][w].fontSize));
						if (pWords[p][w].bold)
							word.setAttribute(BOLD_ATTRIBUTE);
						if (pWords[p][w].italics)
							word.setAttribute(ITALICS_ATTRIBUTE);
						
						//	index word
						wordsByBoxes.put(word.bounds, word);
					}
					
					//	prepare page image for structure analysis (we can modify it freely now, as it's not stored afterward)
					Graphics2D pageImageGraphics = pageImages[p].createGraphics();
					pageImageGraphics.setColor(Color.BLACK);
					for (Iterator wbbit = wordsByBoxes.keySet().iterator(); wbbit.hasNext();) {
						BoundingBox wbb = ((BoundingBox) wbbit.next());
						pageImageGraphics.fillRect(wbb.left, wbb.top, (wbb.right - wbb.left), (wbb.bottom - wbb.top));
					}
					
					//	wrap image for structure analysis
					AnalysisImage api = Imaging.wrapImage(pageImages[p], (imageNames[p] + imageDPIs[p]));
					Imaging.whitenWhite(api);
					
					//	obtain visual page structure
					Region pageRootRegion = PageImageAnalysis.getPageRegion(api, imageDPIs[p], false, pm);
					
					//	add page content to document
					addRegionStructure(pages[p], null, pageRootRegion, imageDPIs[p], wordsByBoxes, pm);
					
					//	catch empty page
					if (pages[p].getWords().length == 0)
						continue;
					
					//	adjust bounding boxes
					shrinkToChildren(pages[p], LINE_ANNOTATION_TYPE, WORD_ANNOTATION_TYPE);
					shrinkToChildren(pages[p], BLOCK_ANNOTATION_TYPE, LINE_ANNOTATION_TYPE);
					
					//	do structure analysis
					PageAnalysis.splitIntoParagraphs(pages[p].getRegions(BLOCK_ANNOTATION_TYPE), imageDPIs[p], null);
					synchronized (pm) {
						pm.setInfo(" - paragraphs done");
					}
					PageAnalysis.computeColumnLayout(pages[p].getRegions(COLUMN_ANNOTATION_TYPE), imageDPIs[p]);
					synchronized (pm) {
						pm.setInfo(" - layout analysis done");
					}
					
					//	finally ...
					synchronized (pm) {
						pm.setInfo(" - page done");
					}
				}
				catch (Throwable t) {
					synchronized (error) {
						error[0] = t;
					}
					return;
				}
			}
		}, pageTree.getNumberOfPages());
		pm.setProgress(100);
		
		//	check errors
		if (error[0] != null) {
			if (error[0] instanceof RuntimeException)
				throw ((RuntimeException) error[0]);
			else if (error[0] instanceof Error)
				throw ((Error) error[0]);
			else if (error[0] instanceof IOException)
				throw ((IOException) error[0]);
			else throw new IOException(error[0].getMessage());
		}
		
		//	finalize text stream structure
		pm.setStep("Analyzing text stream structure");
		pm.setBaseProgress(98);
		pm.setProgress(0);
		pm.setMaxProgress(100);
		ImWord lastWord = null;
		for (int p = 0; p < pages.length; p++) {
			pm.setProgress((p * 100) / pages.length);
			if ((PdfExtractorTest.aimAtPage == -1) || (p == PdfExtractorTest.aimAtPage))
				lastWord = this.addTextStreamStructure(pages[p], lastWord);
		}
		pm.setProgress(100);
		pm.setInfo(" - word sequence analysis done");
		
		//	finally ...
		return doc;
	}
	
	private static HashMap fontCache = new HashMap(5);
	private static Font getFont(String name, int style, int size) {
		String fontKey = (name + " " + style + " " + size);
		System.out.println("Getting font " + fontKey);
		Font font = ((Font) fontCache.get(fontKey));
		if (font != null) {
			System.out.println("==> cache hit");
			return font;
		}
		
		if (name != null) {
			if (name.matches("[A-Z]+\\+[A-Z][a-z].*"))
				name = name.substring(name.indexOf('+')+1);
			if (name.startsWith("Symbol"))
				font = new Font("Symbol", style, size);
			else if (name.startsWith("ZapfDingbats"))
				font = new Font("ZapfDingbats", style, size);
			
			if (name.indexOf('-') != -1)
				name = name.substring(0, name.indexOf('-'));
			String ffn = PdfFont.getFallbackFontName(name, false);
			System.out.println("==> falling back to " + ffn);
			if (ffn.startsWith("Helvetica"))
				font = new Font("Helvetica", style, size);
			else if (ffn.startsWith("Times"))
				font = new Font("Times New Roman", style, size);
			else if (ffn.startsWith("Courier"))
				font = new Font("Courier New", style, size);
		}
		
		if (font == null) {
			System.out.println("==> base font not found, using Serif fallback");
			return new Font("Serif", style, size);
		}
		fontCache.put(fontKey, font);
		System.out.println("==> font created");
		return font;
	}
	
	private ImWord addRegionStructure(ImPage page, ImWord lastWord, Region theRegion, int dpi, HashMap wordsByBoxes, ProgressMonitor pm) {
		
		//	mark region
		ImRegion region = new ImRegion(page, theRegion.getBoundingBox());
		region.setType((theRegion.isColumn() && theRegion.areChildrenAtomic()) ? COLUMN_ANNOTATION_TYPE : REGION_ANNOTATION_TYPE);
		
		//	atomic region ==> block, do further analysis
		if (theRegion.isAtomic()) {
			
			//	analyze block structure
			PageImageAnalysis.getBlockStructure(theRegion, dpi, ((BoundingBox[]) wordsByBoxes.keySet().toArray(new BoundingBox[wordsByBoxes.size()])), pm);
			
			//	get block bounds
			Block theBlock = theRegion.getBlock();
			
			//	mark block
			ImRegion block = new ImRegion(page, theBlock.getBoundingBox());
			block.setType(theBlock.isTable() ? TABLE_ANNOTATION_TYPE : BLOCK_ANNOTATION_TYPE);
			
			//	append block content
			return this.addTextBlockStructure(page, lastWord, theBlock, dpi, wordsByBoxes, pm);
		}
		
		//	non-atomic region ==> recurse to child regions
		for (int s = 0; s < theRegion.getSubRegionCount(); s++)
			lastWord = this.addRegionStructure(page, lastWord, theRegion.getSubRegion(s), dpi, wordsByBoxes, pm);
		
		//	finally ...
		return lastWord;
	}
	
	private ImWord addTextBlockStructure(ImPage page, ImWord lastWord, Block theBlock, int dpi, HashMap pWordsByBoxes, ProgressMonitor pm) {
		
		//	catch empty blocks
		if (theBlock.isEmpty())
			return lastWord;
		
		//	lines wrapped in table cells
		if (theBlock.isTable()) {
			
			//	add rows
			TableRow[] rows = theBlock.getRows();
			for (int r = 0; r < rows.length; r++) {
				
				//	mark row
				new ImRegion(page, rows[r].getBoundingBox(), TABLE_ROW_ANNOTATION_TYPE);
				
				//	add cells
				TableCell[] cells = rows[r].getCells();
				for (int c = 0; c < cells.length; c++) {
					
					//	annotate cell
					ImRegion cell = new ImRegion(page, cells[c].getBoundingBox(), TABLE_CELL_ANNOTATION_TYPE);
					
					//	set dimension attributes
					int colSpan = cells[c].getColSpan();
					if (colSpan > 1)
						cell.setAttribute(COL_SPAN_ATTRIBUTE, ("" + colSpan));
					int rowSpan = cells[c].getRowSpan();
					if (rowSpan > 1)
						cell.setAttribute(ROW_SPAN_ATTRIBUTE, ("" + rowSpan));
					
					//	add content
					Line[] lines = cells[c].getLines();
					lastWord = this.addLineStructure(page, lastWord, cells[c].bounds, lines, pWordsByBoxes, pm);
				}
			}
		}
		
		//	regular text lines
		else {
			Line[] lines = theBlock.getLines();
			lastWord = this.addLineStructure(page, lastWord, theBlock.bounds, lines, pWordsByBoxes, pm);
		}
		
		//	finally ...
		return lastWord;
	}
	
	private ImWord addLineStructure(ImPage page, ImWord lastWord, ImagePartRectangle blockBounds, Line[] lines, HashMap wordsByBoxes, ProgressMonitor pm) {
		if (lines.length == 0)
			return lastWord;
		
		//	compute average line height
		int lineHeightSum = 0;
		for (int l = 0; l < lines.length; l++)
			lineHeightSum += (lines[l].bounds.getBottomRow() - lines[l].bounds.getTopRow());
		int avgLineHeight = (lineHeightSum / lines.length);
		
		//	compute line bounds from PDF words
		int[] lineTop = new int[lines.length];
		Arrays.fill(lineTop, blockBounds.getBottomRow());
		int[] lineBottom = new int[lines.length];
		Arrays.fill(lineBottom, blockBounds.getTopRow());
		for (int l = 0; l < lines.length; l++) {
			BoundingBox lineBox = lines[l].getBoundingBox();
			for (Iterator bbit = wordsByBoxes.keySet().iterator(); bbit.hasNext();) {
				BoundingBox wbb = ((BoundingBox) bbit.next());
				if (wbb.bottom <= lineBox.top)
					continue;
				else if (lineBox.bottom <= wbb.top)
					continue;
				else if (wbb.right <= lineBox.left)
					continue;
				else if (lineBox.right <= wbb.left)
					continue;
				
				int wbbMiddle = ((wbb.top + wbb.bottom) / 2);
				if (wbbMiddle <= lineBox.top)
					continue;
				else if (lineBox.bottom <= wbbMiddle)
					continue;
				
				int wbbCenter = ((wbb.left + wbb.right) / 2);
				if (wbbCenter <= lineBox.left)
					continue;
				else if (lineBox.right <= wbbCenter)
					continue;
				
				lineTop[l] = Math.min(lineTop[l], wbb.top);
				lineBottom[l] = Math.max(lineBottom[l], wbb.bottom);
			}
		}
		
		//	check all lines for possibly being super/sub-script
		byte[] linePosition = new byte[lines.length];
		Arrays.fill(linePosition, ((byte) 0));
		for (int l = 0; l < lines.length; l++) {
			
			//	above average height lines are very unlikely to be super/sub script
			if (avgLineHeight <= (lineBottom[l] - lineTop[l]))
				continue;
			
			//	check superscript if line is less than 70% of the one below
			int downwardOverlap = 0;
			if (((l+1) < lines.length) && (((lineBottom[l+1] - lineTop[l+1]) * 7) > ((lineBottom[l] - lineTop[l]) * 10)))
				downwardOverlap = Math.max(0, (lineBottom[l] - lineTop[l+1]));
			
			//	check subscript if line is less than 70% of the one above
			int upwardOverlap = 0;
			if ((l != 0) && (((lineBottom[l-1] - lineTop[l-1]) * 7) > ((lineBottom[l] - lineTop[l]) * 10)))
				upwardOverlap = Math.max(0, (lineBottom[l-1] - lineTop[l]));
			
			//	no overlap at all
			if ((upwardOverlap + downwardOverlap) == 0)
				continue;
			
			//	mark where words are
			boolean[] isWord = new boolean[blockBounds.getRightCol() - blockBounds.getLeftCol()];
			Arrays.fill(isWord, false);
			Word[] words = lines[l].getWords();
			for (int w = 0; w < words.length; w++) {
				for (int x = Math.max(words[w].bounds.getLeftCol(), blockBounds.getLeftCol()); x < Math.min(words[w].bounds.getRightCol(), blockBounds.getRightCol()); x++)
					isWord[x - blockBounds.getLeftCol()] = true;
			}
			
			//	check superscript conflicts
			if (downwardOverlap != 0) {
				Word[] wordsBelow = lines[l+1].getWords();
				for (int w = 0; w < wordsBelow.length; w++) {
					int overlappingCols = 0;
					for (int x = Math.max(wordsBelow[w].bounds.getLeftCol(), blockBounds.getLeftCol()); x < Math.min(wordsBelow[w].bounds.getRightCol(), blockBounds.getRightCol()); x++) {
						if (isWord[x - blockBounds.getLeftCol()])
							overlappingCols++;
					}
					if (overlappingCols > 1) {
						downwardOverlap = 0;
						break;
					}
				}
			}
			
			//	check superscript conflicts
			if (upwardOverlap != 0) {
				Word[] wordsAbove = lines[l-1].getWords();
				for (int w = 0; w < wordsAbove.length; w++) {
					int overlappingCols = 0;
					for (int x = Math.max(wordsAbove[w].bounds.getLeftCol(), blockBounds.getLeftCol()); x < Math.min(wordsAbove[w].bounds.getRightCol(), blockBounds.getRightCol()); x++) {
						if (isWord[x - blockBounds.getLeftCol()])
							overlappingCols++;
					}
					if (overlappingCols > 1) {
						upwardOverlap = 0;
						break;
					}
				}
			}
			
			//	conflicts in all overlapping directions
			if ((upwardOverlap + downwardOverlap) == 0)
				continue;
			
			//	we cannot decide on this one
			if ((upwardOverlap * downwardOverlap) != 0)
				continue;
			
			//	mark super or subscript
			linePosition[l] = ((upwardOverlap < downwardOverlap) ? ((byte) 1) : ((byte) -1));
		}
		
		//	add lines
		for (int l = 0; l < lines.length; l++) {
			if (linePosition[l] != 0)
				continue;
			
			//	mark line
			ImRegion line = new ImRegion(page, lines[l].getBoundingBox(), LINE_ANNOTATION_TYPE);
			int baseline = lines[l].getBaseline();
			if (0 < baseline)
				line.setAttribute(BASELINE_ATTRIBUTE, ("" + baseline));
			
			//	collect words in line
			ArrayList lWordBoxes = new ArrayList();
			for (Iterator bbit = wordsByBoxes.keySet().iterator(); bbit.hasNext();) {
				BoundingBox wbb = ((BoundingBox) bbit.next());
				if (wbb.bottom <= line.bounds.top)
					continue;
				else if (line.bounds.bottom <= wbb.top)
					continue;
				else if (wbb.right <= line.bounds.left)
					continue;
				else if (line.bounds.right <= wbb.left)
					continue;
				
				int wbbMiddle = ((wbb.top + wbb.bottom) / 2);
				if (wbbMiddle <= line.bounds.top)
					continue;
				else if (line.bounds.bottom <= wbbMiddle)
					continue;
				
				int wbbCenter = ((wbb.left + wbb.right) / 2);
				if (wbbCenter <= line.bounds.left)
					continue;
				else if (line.bounds.right <= wbbCenter)
					continue;
				
				lWordBoxes.add(wbb);
			}
			
			//	get superscript lines first and collect words inside them
			Line superScriptLine = (((l != 0) && (linePosition[l-1] > 0)) ? lines[l-1] : null);
			if (superScriptLine != null) {
				BoundingBox sLineBox = superScriptLine.getBoundingBox();
				for (Iterator bbit = wordsByBoxes.keySet().iterator(); bbit.hasNext();) {
					BoundingBox wbb = ((BoundingBox) bbit.next());
					if (wbb.bottom <= sLineBox.top)
						continue;
					else if (sLineBox.bottom <= wbb.top)
						continue;
					else if (wbb.right <= sLineBox.left)
						continue;
					else if (sLineBox.right <= wbb.left)
						continue;
					
					int wbbMiddle = ((wbb.top + wbb.bottom) / 2);
					if (wbbMiddle <= sLineBox.top)
						continue;
					else if (sLineBox.bottom <= wbbMiddle)
						continue;
					
					int wbbCenter = ((wbb.left + wbb.right) / 2);
					if (wbbCenter <= sLineBox.left)
						continue;
					else if (sLineBox.right <= wbbCenter)
						continue;
					
					lWordBoxes.add(wbb);
				}
			}
			
			//	get subscript lines first and collect words inside them
			Line subScriptLine = ((((l+1) < lines.length) && (linePosition[l+1] < 0)) ? lines[l+1] : null);
			if (subScriptLine != null) {
				BoundingBox sLineBox = subScriptLine.getBoundingBox();
				for (Iterator bbit = wordsByBoxes.keySet().iterator(); bbit.hasNext();) {
					BoundingBox wbb = ((BoundingBox) bbit.next());
					if (wbb.bottom <= sLineBox.top)
						continue;
					else if (sLineBox.bottom <= wbb.top)
						continue;
					else if (wbb.right <= sLineBox.left)
						continue;
					else if (sLineBox.right <= wbb.left)
						continue;
					
					int wbbMiddle = ((wbb.top + wbb.bottom) / 2);
					if (wbbMiddle <= sLineBox.top)
						continue;
					else if (sLineBox.bottom <= wbbMiddle)
						continue;
					
					int wbbCenter = ((wbb.left + wbb.right) / 2);
					if (wbbCenter <= sLineBox.left)
						continue;
					else if (sLineBox.right <= wbbCenter)
						continue;
					
					lWordBoxes.add(wbb);
				}
			}
			
			pm.setInfo(" - got " + lWordBoxes.size() + " PDF words in line " + l);
			if (lWordBoxes.isEmpty())
				continue;
			
			Collections.sort(lWordBoxes, new Comparator() {
				public int compare(Object bbo1, Object bbo2) {
					BoundingBox bb1 = ((BoundingBox) bbo1);
					BoundingBox bb2 = ((BoundingBox) bbo2);
					return ((bb1.left == bb2.left) ? (bb1.right - bb2.right) : (bb1.left - bb2.left));
				}
			});
			
			int fontSizeSum = 0;
			int fontSizeCount = 0;
			
			Word[] words = lines[l].getWords();
			pm.setInfo(" - got " + words.length + " original layout words");
			if ((superScriptLine != null) || (subScriptLine != null)) {
				ArrayList wordList = new ArrayList(Arrays.asList(words));
				if (superScriptLine != null)
					wordList.addAll(Arrays.asList(superScriptLine.getWords()));
				if (subScriptLine != null)
					wordList.addAll(Arrays.asList(subScriptLine.getWords()));
				Collections.sort(wordList, new Comparator() {
					public int compare(Object w1, Object w2) {
						ImagePartRectangle bb1 = ((Word) w1).bounds;
						ImagePartRectangle bb2 = ((Word) w2).bounds;
						return ((bb1.getLeftCol() == bb2.getLeftCol()) ? (bb1.getRightCol() - bb2.getRightCol()) : (bb1.getLeftCol() - bb2.getLeftCol()));
					}
				});
				words = ((Word[]) wordList.toArray(new Word[wordList.size()]));
			}
			pm.setInfo(" - got " + words.length + " extended layout words");
			
			//	arrange words in streams, carrying over last word
			for (int w = 0; w < words.length; w++) {
				
				//	gather strings and layout information
				for (int lw = 0; lw < lWordBoxes.size(); lw++) {
					BoundingBox lWordBox = ((BoundingBox) lWordBoxes.get(lw));
					if (words[w].bounds.getRightCol() <= lWordBox.left)
						break;
					
					//	get PDF word
					lWordBoxes.remove(lw--);
					ImWord lWord = ((ImWord) wordsByBoxes.remove(lWordBox));
					if (lWord == null)
						continue;
					
					//	transfer layout information
					if (words[w].isBold())
						lWord.setAttribute(BOLD_ATTRIBUTE);
					if (words[w].isItalics())
						lWord.setAttribute(ITALICS_ATTRIBUTE);
					
					//	connect words
					if (lastWord != null)
						lastWord.setNextWord(lWord);
					lastWord = lWord;
					
					//	aggregate layout information
					if (lWord.hasAttribute(FONT_SIZE_ATTRIBUTE)) try {
						int wfs = Integer.parseInt((String) lWord.getAttribute(FONT_SIZE_ATTRIBUTE));
						fontSizeSum += wfs;
						fontSizeCount++;
					} catch (NumberFormatException nfe) {}
				}
			}
			if (lWordBoxes.isEmpty())
				pm.setInfo(" - got " + lWordBoxes.size() + " PDF words remaining after line");
			else {
				pm.setInfo("Not good, got " + lWordBoxes.size() + " PDF words remaining after line:");
				pm.setInfo(" - line bounds are " + lines[l].getBoundingBox());
				pm.setInfo(" - layout words:");
				for (int w = 0; w < words.length; w++)
					pm.setInfo("   - " + words[w].getBoundingBox());
				pm.setInfo(" - remaining words:");
				for (int lw = 0; lw < lWordBoxes.size(); lw++) {
					BoundingBox lWordBox = ((BoundingBox) lWordBoxes.get(lw));
					ImWord lWord = ((ImWord) wordsByBoxes.remove(lWordBox));
					pm.setInfo("   - " + lWord.getString() + " at " + lWord.bounds.toString());
				}
			}
			
			//	add line font size
			if (0 < fontSizeSum) {
				int fontSize = ((fontSizeSum + (fontSizeCount / 2)) / fontSizeCount);
				if (0 < fontSize)
					line.setAttribute(FONT_SIZE_ATTRIBUTE, ("" + fontSize));
			}
			else {
				int fontSize = lines[l].getFontSize();
				if (0 < fontSize)
					line.setAttribute(FONT_SIZE_ATTRIBUTE, ("" + fontSize));
			}
		}
		
		//	finally ...
		return lastWord;
	}
	
	private ImWord addTextStreamStructure(ImPage page, ImWord lastWord) {
		
		//	get paragraphs
		ImRegion[] paragraphs = page.getRegions(MutableAnnotation.PARAGRAPH_TYPE);
		if (paragraphs.length == 0)
			return lastWord;
		
		//	get text stream heads (can only be one here)
		ImWord[] textStreamHeads = page.getTextStreamHeads();
		if (textStreamHeads.length != 1)
			return lastWord;
		
		//	index words by paragraph
		HashMap wordParagraphs = new HashMap();
		for (int p = 0; p < paragraphs.length; p++) {
			ImWord[] words = paragraphs[p].getWords();
			for (int w = 0; w < words.length; w++)
				wordParagraphs.put(words[w], paragraphs[p]);
		}
		
		//	add text stream structure
		ImRegion lastWordPargarph = null;
		for (ImWord imw = textStreamHeads[0]; imw != null; imw = imw.getNextWord()) {
			
			//	we just don't have any predecessor
			if (lastWord == null) {
				lastWord = imw;
				lastWordPargarph = ((ImRegion) wordParagraphs.get(imw));
				continue;
			}
			
			//	check for hyphenation
			boolean lastWordHyphenated = ((lastWord.getString() != null) && lastWord.getString().matches(".+[\\-\\u00AD\\u2010-\\u2015\\u2212]"));
			boolean wordContinues = false;
			if (lastWordHyphenated) {
				String wordString = imw.getString();
				if (wordString == null) {} // little we can do here ...
				else if (wordString.length() == 0) {} // ... or here
				else if (wordString.charAt(0) == Character.toUpperCase(wordString.charAt(0))) {} // starting with capital letter, not a word continued
				else if ("and;or;und;oder;et;ou;y;e;o;u;ed".indexOf(wordString.toLowerCase()) != -1) {} // rather looks like an enumeration continued than a word (western European languages for now)
				else wordContinues = true;
			}
			
			//	get word paragraph
			ImRegion wordParagraph = ((ImRegion) wordParagraphs.get(imw));
			
			//	predecessor on previous page, chain words, and mark (layout) paragraph break if not hyphenated
			if (lastWord.pageId != imw.pageId) {
				lastWord.setNextWord(imw);
				lastWord.setNextRelation((lastWordHyphenated && wordContinues) ? ImWord.NEXT_RELATION_HYPHENATED : ImWord.NEXT_RELATION_PARAGRAPH_END);
				lastWord = imw;
				lastWordPargarph = wordParagraph;
				continue;
			}
			
			//	starting new paragraph
			if (lastWordPargarph != wordParagraph) {
				lastWord.setNextRelation(ImWord.NEXT_RELATION_PARAGRAPH_END);
				lastWord = imw;
				lastWordPargarph = wordParagraph;
				continue;
			}
			
			//	do we have a line break?
			boolean lineBreak;
			if (page.getDocument().orientation == ComponentOrientation.RIGHT_TO_LEFT)
				lineBreak = (lastWord.bounds.left < imw.bounds.right); // working with left to right script
			else lineBreak = (imw.bounds.right < lastWord.bounds.right); // working with right to left script
			
			//	line break, check hyphenation
			if (lineBreak)
				lastWord.setNextRelation((lastWordHyphenated && wordContinues) ? ImWord.NEXT_RELATION_HYPHENATED : ImWord.NEXT_RELATION_SEPARATE);
			
			//	switch to next word
			lastWord = imw;
			lastWordPargarph = wordParagraph;
		}
		
		//	finally ...
		return lastWord;
	}
	
	private static void shrinkToChildren(ImPage page, String pType, String cType) {
		ImRegion[] pRegions = page.getRegions(pType);
		for (int p = 0; p < pRegions.length; p++) {
			int pLeft = pRegions[p].bounds.right;
			int pRight = pRegions[p].bounds.left;
			int pTop = pRegions[p].bounds.bottom;
			int pBottom = pRegions[p].bounds.top;
			ImRegion[] cRegions = (WORD_ANNOTATION_TYPE.equals(cType) ? page.getWordsInside(pRegions[p].bounds) : page.getRegionsInside(pRegions[p].bounds, false));
			for (int c = 0; c < cRegions.length; c++) {
				if (!cType.equals(cRegions[c].getType()))
					continue;
				pLeft = Math.min(pLeft, cRegions[c].bounds.left);
				pRight = Math.max(pRight, cRegions[c].bounds.right);
				pTop = Math.min(pTop, cRegions[c].bounds.top);
				pBottom = Math.max(pBottom, cRegions[c].bounds.bottom);
			}
			if ((pLeft < pRight) && (pTop < pBottom) && ((pRegions[p].bounds.left < pLeft) || (pRight < pRegions[p].bounds.right) || (pRegions[p].bounds.top < pTop) || (pBottom < pRegions[p].bounds.bottom))) {
				BoundingBox pBox = new BoundingBox(pLeft, pRight, pTop, pBottom);
				ImRegion pRegion = new ImRegion(page, pBox, pType);
				pRegion.copyAttributes(pRegions[p]);
				page.getPage().removeRegion(pRegions[p]);
				pRegions[p] = pRegion;
			}
		}
	}
	
	/**
	 * Load a document from an OCR PDF. Page images are stored in the page image
	 * store handed to the constructor. The document structure is stored in the
	 * returned image markup document, including word bounding boxes.
	 * @param doc the document to store the structural information in
	 * @param pdfDoc the PDF document to parse
	 * @param pdfBytes the raw binary data the the PDF document was parsed from
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the image markup document
	 * @throws IOException
	 */
	public ImDocument loadImagePdf(byte[] pdfBytes, ProgressMonitor pm) throws IOException {
		return this.loadImagePdf(null, null, pdfBytes, 1, pm);
	}
	
	/**
	 * Load a document from an OCR PDF. Page images are stored in the page image
	 * store handed to the constructor. The document structure is stored in the
	 * argument image markup document, including word bounding boxes. If the
	 * argument ImDocument is null, this method creates a new one with the MD5
	 * checksum of the argument PDF bytes as the ID.
	 * @param doc the document to store the structural information in
	 * @param pdfDoc the PDF document to parse
	 * @param pdfBytes the raw binary data the the PDF document was parsed from
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the argument image markup document
	 * @throws IOException
	 */
	public ImDocument loadImagePdf(ImDocument doc, Document pdfDoc, byte[] pdfBytes, ProgressMonitor pm) throws IOException {
		return this.loadImagePdf(doc, pdfDoc, pdfBytes, 1, pm);
	}
	
	/**
	 * Load a document from an OCR PDF. Page images are stored in the page image
	 * store handed to the constructor. The document structure is stored in the
	 * argument image markup document, including word bounding boxes. The scale
	 * factor helps with PDFs whose page boundaries (media boxes) are set too
	 * small. This can be recognized in any PDF viewer by the fact that the
	 * document is tiny and the writing very small at 100% size display. It can
	 * also be detected automatically, e.g. by means of examining the average
	 * height of lines in comparison to the DPI number. If the argument
	 * ImDocument is null, this method creates a new one with the MD5 checksum
	 * of the argument PDF bytes as the ID.
	 * @param doc the document to store the structural information in
	 * @param pdfDoc the PDF document to parse
	 * @param pdfBytes the raw binary data the the PDF document was parsed from
	 * @param scaleFactor the scale factor for image PDFs, to correct PDFs that
	 *            are set too small (DPI number is divided by this factor)
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the argument image markup document
	 * @throws IOException
	 */
	public ImDocument loadImagePdf(ImDocument doc, Document pdfDoc, byte[] pdfBytes, int scaleFactor, ProgressMonitor pm) throws IOException {
		if (doc == null)
			doc = new ImDocument(getChecksum(pdfBytes));
		if (pdfDoc == null) try {
			pdfDoc = new Document();
			pdfDoc.setInputStream(new ByteArrayInputStream(pdfBytes), "");
		}
		catch (PDFException pdfe) {
			throw new IOException(pdfe.getMessage(), pdfe);
		}
		catch (PDFSecurityException pdfse) {
			throw new IOException(pdfse.getMessage(), pdfse);
		}
		if (pm == null)
			pm = ProgressMonitor.dummy;
		
		//	test if we can OCR
		if (this.ocrEngine == null)
			throw new RuntimeException("OCR Engine unavailable, check startup log.");
		
		//	cache page images (only binary and gray scale, and only up to 300 DPI, as memory consumption gets too high beyond that)
		HashMap pageImageCache = new HashMap() {
			public Object put(Object cacheKey, Object gapeImage) {
				if (gapeImage instanceof PageImage) {
					PageImage pi = ((PageImage) gapeImage);
					if (pi.currentDpi > 300)
						return null;
					if ((pi.image.getType() != BufferedImage.TYPE_BYTE_BINARY) && (pi.image.getType() != BufferedImage.TYPE_BYTE_GRAY))
						return null;
				}
				return super.put(cacheKey, gapeImage);
			}
		};
		
		//	load pages
		pm.setStep("Loading document page images");
		pm.setBaseProgress(0);
		pm.setProgress(0);
		pm.setMaxProgress(30);
		this.addImagePdfPages(doc, pdfDoc, pdfBytes, scaleFactor, pm, pageImageCache);
		
		//	fill in blocks and do OCR
		pm.setStep("Extracting blocks & doing OCR");
		pm.setBaseProgress(40);
		pm.setProgress(0);
		pm.setMaxProgress(70);
		this.addImagePdfPageBlocks(doc, pdfDoc, pdfBytes, scaleFactor, pm, pageImageCache);
		
		//	analyze block structure and layout
		pm.setStep("Analyzing page text structure");
		pm.setBaseProgress(70);
		pm.setProgress(0);
		pm.setMaxProgress(100);
		this.addImagePdfPageLayout(doc, pdfDoc, pdfBytes, scaleFactor, pm, pageImageCache);
		
		//	finally
		return doc;
	}
	
	private void addImagePdfPageLayout(final ImDocument doc, Document pdfDoc, byte[] pdfBytes, int scaleFactor, final ProgressMonitor pm, final HashMap pageImageCache) throws IOException {
		
		//	prepare working in parallel
		final LinkedList pageIDs = new LinkedList();
		final Throwable[] error = {null};
		final ImPage[] pages = doc.getPages();
		
		//	analyze page text layout
		pageIDs.clear();
		if (PdfExtractorTest.aimAtPage != -1)
			pageIDs.addLast(new Integer(PdfExtractorTest.aimAtPage));
		else for (int p = 0; p < pages.length; p++)
			pageIDs.addLast(new Integer(p));
		error[0] = null;
		this.runJob( new Runnable() {
			public void run() {
				while (true) try {
					
					//	any error or other problems in siblings?
					synchronized (error) {
						if (error[0] != null)
							return;
					}
					
					//	get next page ID to work off
					int p;
					synchronized (pageIDs) {
						if (pageIDs.isEmpty())
							return;
						p = ((Integer) pageIDs.removeFirst()).intValue();
					}
					
					//	update status display (might be inaccurate, but better than lock escalation)
					synchronized (pm) {
						pm.setInfo("Doing OCR for page " + (pages.length - pageIDs.size()) + " of " + pages.length);
						pm.setProgress((100 * (pages.length - pageIDs.size() - 1)) / pages.length);
					}
					
					//	get page image
					PageImage pi = ((PageImage) pageImageCache.get(new Integer(p)));
					if (pi == null)
						pi = PageImage.getPageImage(doc.docId, p);
					if (pi == null) {
						pm.setInfo(" - page image not found");
						throw new RuntimeException("Could not find image for page " + p);
					}
					
					//	index words by bounding boxes
					ImWord[] pageWords = pages[p].getWords();
					HashMap wordsByBoxes = new HashMap();
					for (int w = 0; w < pageWords.length; w++)
						wordsByBoxes.put(pageWords[w].bounds, pageWords[w]);
					
					//	obtain higher level page structure
					AnalysisImage api = Imaging.wrapImage(pi.image, null);
					Region pageRootRegion = PageImageAnalysis.getPageRegion(api, pi.currentDpi, false, pm);
					addRegionBlocks(pages[p], pageRootRegion, pi.currentDpi, pm);
					addTextBlockStructures(pages[p], null, pageRootRegion, pi.currentDpi, wordsByBoxes, pm);
					
					//	do structure analysis
					PageAnalysis.splitIntoParagraphs(pages[p].getRegions(BLOCK_ANNOTATION_TYPE), pi.currentDpi, null);
					synchronized (pm) {
						pm.setInfo(" - paragraphs done");
					}
					
					PageAnalysis.computeColumnLayout(pages[p].getRegions(COLUMN_ANNOTATION_TYPE), pi.currentDpi);
					synchronized (pm) {
						pm.setInfo(" - layout analysis done");
					}
					
					//	... finally
					synchronized (pm) {
						pm.setInfo(" - page done");
					}
					
					//	clean up analysis image cache
					Imaging.cleanUpCache(pi.image.hashCode() + "-");
				}
				catch (Throwable t) {
					synchronized (error) {
						error[0] = t;
					}
					return;
				}
			}
		}, pages.length);
		pm.setProgress(100);
		
		//	check errors
		if (error[0] != null) {
			if (error[0] instanceof RuntimeException)
				throw ((RuntimeException) error[0]);
			else if (error[0] instanceof Error)
				throw ((Error) error[0]);
			else if (error[0] instanceof IOException)
				throw ((IOException) error[0]);
			else throw new IOException(error[0].getMessage());
		}
		
		//	finalize text stream structure
		ImWord lastWord = null;
		for (int p = 0; p < pages.length; p++)
			lastWord = this.addTextStreamStructure(pages[p], lastWord);
		pm.setInfo(" - word sequence analysis done");
	}
	
	private ImWord addTextBlockStructures(ImPage page, ImWord lastWord, Region theRegion, int dpi, HashMap wordsByBoxes, ProgressMonitor pm) {
		
		//	atomic region ==> block, do further analysis
		if (theRegion.isAtomic()) {
			
			//	analyze block structure
			PageImageAnalysis.getBlockStructure(theRegion, dpi, ((BoundingBox[]) wordsByBoxes.keySet().toArray(new BoundingBox[wordsByBoxes.size()])), pm);
			
			//	get block bounds
			Block theBlock = theRegion.getBlock();
			
			//	compute line baselines
			PageImageAnalysis.computeLineBaselines(theBlock, dpi);
			
			//	compute remaining line metrics
			PageImageAnalysis.computeFontMetrics(theBlock, dpi);
			
			//	append block content
			return this.addTextBlockStructure(page, lastWord, theBlock, dpi, wordsByBoxes, pm);
		}
		
		//	non-atomic region ==> recurse to child regions
		for (int s = 0; s < theRegion.getSubRegionCount(); s++)
			lastWord = this.addTextBlockStructures(page, lastWord, theRegion.getSubRegion(s), dpi, wordsByBoxes, pm);
		
		//	finally ...
		return lastWord;
	}
	
	/**
	 * Load the pages of a document from an OCR PDF, including their block
	 * structure. Page images are stored in the page image store handed to the
	 * constructor. The document structure is analyzed and represented to the
	 * text block level, but not below. 
	 * @param pdfBytes the raw binary data the the PDF document was parsed from
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the image markup document
	 * @throws IOException
	 */
	public ImDocument loadImagePdfBlocks(byte[] pdfBytes, ProgressMonitor pm) throws IOException {
		return this.loadImagePdfBlocks(null, null, pdfBytes, 1, pm);
	}
	
	/**
	 * Load the pages of a document from an OCR PDF, including their block
	 * structure. Page images are stored in the page image store handed to the
	 * constructor. The document structure is analyzed and represented to the
	 * text block level, but not below.  If the argument ImDocument is null,
	 * this method creates a new one with the MD5 checksum of the argument PDF
	 * bytes as the ID.
	 * @param doc the document to store the structural information in
	 * @param pdfDoc the PDF document to parse
	 * @param pdfBytes the raw binary data the the PDF document was parsed from
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the argument image markup document
	 * @throws IOException
	 */
	public ImDocument loadImagePdfBlocks(ImDocument doc, Document pdfDoc, byte[] pdfBytes, ProgressMonitor pm) throws IOException {
		return this.loadImagePdfBlocks(doc, pdfDoc, pdfBytes, 1, pm);
	}
	
	/**
	 * Load the pages of a document from an OCR PDF, including their block
	 * structure. Page images are stored in the page image store handed to the
	 * constructor. The document structure is analyzed and represented to the
	 * text block level, but not below. The scale factor helps with PDFs whose
	 * page boundaries (media boxes) are set too small. This can be recognized
	 * in any PDF viewer by the fact that the document is tiny and the writing
	 * very small at 100% size display. It can also be detected automatically,
	 * e.g. by means of examining the average height of lines in comparison to
	 * the DPI number. If the argument ImDocument is null, this method creates
	 * a new one with the MD5 checksum of the argument PDF bytes as the ID.
	 * @param doc the document to store the structural information in
	 * @param pdfDoc the PDF document to parse
	 * @param pdfBytes the raw binary data the the PDF document was parsed from
	 * @param scaleFactor the scale factor for image PDFs, to correct PDFs that
	 *            are set too small (DPI number is divided by this factor)
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the argument image markup document
	 * @throws IOException
	 */
	public ImDocument loadImagePdfBlocks(ImDocument doc, Document pdfDoc, byte[] pdfBytes, int scaleFactor, ProgressMonitor pm) throws IOException {
		if (doc == null)
			doc = new ImDocument(getChecksum(pdfBytes));
		if (pdfDoc == null) try {
			pdfDoc = new Document();
			pdfDoc.setInputStream(new ByteArrayInputStream(pdfBytes), "");
		}
		catch (PDFException pdfe) {
			throw new IOException(pdfe.getMessage(), pdfe);
		}
		catch (PDFSecurityException pdfse) {
			throw new IOException(pdfse.getMessage(), pdfse);
		}
		if (pm == null)
			pm = ProgressMonitor.dummy;
		
		//	test if we can OCR
		if (this.ocrEngine == null)
			throw new RuntimeException("OCR Engine unavailable, check startup log.");
		
		//	cache page images (only binary and gray scale, and only up to 300 DPI, as memory consumption gets too high beyond that)
		HashMap pageImageCache = new HashMap() {
			public Object put(Object cacheKey, Object gapeImage) {
				if (gapeImage instanceof PageImage) {
					PageImage pi = ((PageImage) gapeImage);
					if (pi.currentDpi > 300)
						return null;
					if ((pi.image.getType() != BufferedImage.TYPE_BYTE_BINARY) && (pi.image.getType() != BufferedImage.TYPE_BYTE_GRAY))
						return null;
				}
				return super.put(cacheKey, gapeImage);
			}
		};
		
		//	load pages
		pm.setStep("Loading document page images");
		pm.setBaseProgress(0);
		pm.setProgress(0);
		pm.setMaxProgress(40);
		this.addImagePdfPages(doc, pdfDoc, pdfBytes, scaleFactor, pm, pageImageCache);
		
		//	fill in blocks and do OCR
		pm.setStep("Extracting blocks & doing OCR");
		pm.setBaseProgress(40);
		pm.setProgress(0);
		pm.setMaxProgress(100);
		this.addImagePdfPageBlocks(doc, pdfDoc, pdfBytes, scaleFactor, pm, pageImageCache);
		
		//	finally ...
		return doc;
	}
	
	private ImDocument addImagePdfPageBlocks(final ImDocument doc, Document pdfDoc, byte[] pdfBytes, int scaleFactor, final ProgressMonitor pm, final HashMap pageImageCache) throws IOException {
		
		//	prepare working in parallel
		final LinkedList pageIDs = new LinkedList();
		final Throwable[] error = {null};
		final ImPage[] pages = doc.getPages();
		
		//	do high level structure analysis (down to blocks) and OCR
		pageIDs.clear();
		if (PdfExtractorTest.aimAtPage != -1)
			pageIDs.addLast(new Integer(PdfExtractorTest.aimAtPage));
		else for (int p = 0; p < pages.length; p++)
			pageIDs.addLast(new Integer(p));
		error[0] = null;
		this.runJob(new Runnable() {
			public void run() {
				while (true) try {
					
					//	any error or other problems in siblings?
					synchronized (error) {
						if (error[0] != null)
							return;
					}
					
					//	get next page ID to work off
					int p;
					synchronized (pageIDs) {
						if (pageIDs.isEmpty())
							return;
						p = ((Integer) pageIDs.removeFirst()).intValue();
					}
					
					//	update status display (might be inaccurate, but better than lock escalation)
					synchronized (pm) {
						pm.setInfo("Analyzing structure of page " + (pages.length - pageIDs.size()) + " of " + pages.length);
						pm.setProgress((100 * (pages.length - pageIDs.size() - 1)) / pages.length);
					}
					
					//	get page image
					PageImage pi = ((PageImage) pageImageCache.get(new Integer(p)));
					if (pi == null)
						pi = PageImage.getPageImage(doc.docId, p);
					if (pi == null) {
						pm.setInfo(" - page image not found");
						throw new RuntimeException("Could not find image for page " + p);
					}
					
					//	obtain higher level page structure
					AnalysisImage api = Imaging.wrapImage(pi.image, null);
					Region pageRootRegion = PageImageAnalysis.getPageRegion(api, pi.currentDpi, false, pm);
					addRegionBlocks(pages[p], pageRootRegion, pi.currentDpi, pm);
					
					//	do OCR
					synchronized (pm) {
						pm.setInfo(" - doing OCR");
					}
					ocrEngine.doBlockOcr(pages[p], pi, pm);
					synchronized (pm) {
						pm.setInfo(" - OCR done");
					}
					
					//	clean up analysis image cache
					Imaging.cleanUpCache(pi.image.hashCode() + "-");
				}
				catch (Throwable t) {
					synchronized (error) {
						error[0] = t;
					}
					return;
				}
			}
		}, pages.length);
		pm.setProgress(100);
		
		//	check errors
		if (error[0] != null) {
			if (error[0] instanceof RuntimeException)
				throw ((RuntimeException) error[0]);
			else if (error[0] instanceof Error)
				throw ((Error) error[0]);
			else if (error[0] instanceof IOException)
				throw ((IOException) error[0]);
			else throw new IOException(error[0].getMessage());
		}
		
		//	... finally
		return doc;
	}
	
	private void addRegionBlocks(ImPage page, Region theRegion, int dpi, ProgressMonitor pm) {
		
		//	mark region
		ImRegion region = new ImRegion(page, theRegion.getBoundingBox());
		
		//	atomic region ==> block, mark it and we're done here
		if (theRegion.isAtomic())
			region.setType(BLOCK_ANNOTATION_TYPE);
		
		//	non-atomic region ==> mark as column or generic region, and recurse to child regions
		else {
			region.setType((theRegion.isColumn() && theRegion.areChildrenAtomic()) ? COLUMN_ANNOTATION_TYPE : REGION_ANNOTATION_TYPE);
			for (int s = 0; s < theRegion.getSubRegionCount(); s++)
				this.addRegionBlocks(page, theRegion.getSubRegion(s), dpi, pm);
		}
	}
	
	/**
	 * Load the pages of a document from an OCR PDF. Page images are stored in
	 * the page image store handed to the constructor. The document structure is
	 * not analyzed or represented beyond the page level. The scale factor helps
	 * with PDFs whose page boundaries (media boxes) are set too small. This can
	 * be recognized in any PDF viewer by the fact that the document is tiny and
	 * the writing very small at 100% size display. It can also be detected
	 * automatically, e.g. by means of examining the average height of lines in
	 * comparison to the DPI number.
	 * @param pdfBytes the raw binary data the the PDF document was parsed from
	 * @param scaleFactor the scale factor for image PDFs, to correct PDFs that
	 *            are set too small (DPI number is divided by this factor)
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the image markup document
	 * @throws IOException
	 */
	public ImDocument loadImagePdfPages(byte[] pdfBytes, ProgressMonitor pm) throws IOException {
		return this.loadImagePdfPages(null, null, pdfBytes, 1, pm);
	}
	
	/**
	 * Load the pages of a document from an OCR PDF. Page images are stored in
	 * the page image store handed to the constructor. The document structure is
	 * not analyzed or represented beyond the page level. The scale factor helps
	 * with PDFs whose page boundaries (media boxes) are set too small. This can
	 * be recognized in any PDF viewer by the fact that the document is tiny and
	 * the writing very small at 100% size display. It can also be detected
	 * automatically, e.g. by means of examining the average height of lines in
	 * comparison to the DPI number. If the argument ImDocument is null, this
	 * method creates a new one with the MD5 checksum of the argument PDF bytes
	 * as the ID.
	 * @param doc the document to store the structural information in
	 * @param pdfDoc the PDF document to parse
	 * @param pdfBytes the raw binary data the the PDF document was parsed from
	 * @param scaleFactor the scale factor for image PDFs, to correct PDFs that
	 *            are set too small (DPI number is divided by this factor)
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the argument image markup document
	 * @throws IOException
	 */
	public ImDocument loadImagePdfPages(ImDocument doc, Document pdfDoc, byte[] pdfBytes, ProgressMonitor pm) throws IOException {
		return this.loadImagePdfPages(doc, pdfDoc, pdfBytes, 1, pm);
	}
	
	/**
	 * Load the pages of a document from an OCR PDF. Page images are stored in
	 * the page image store handed to the constructor. The document structure is
	 * not analyzed or represented beyond the page level. The scale factor helps
	 * with PDFs whose page boundaries (media boxes) are set too small. This can
	 * be recognized in any PDF viewer by the fact that the document is tiny and
	 * the writing very small at 100% size display. It can also be detected
	 * automatically, e.g. by means of examining the average height of lines in
	 * comparison to the DPI number. If the argument ImDocument is null, this
	 * method creates a new one with the MD5 checksum of the argument PDF bytes
	 * as the ID.
	 * @param doc the document to store the structural information in
	 * @param pdfDoc the PDF document to parse
	 * @param pdfBytes the raw binary data the the PDF document was parsed from
	 * @param scaleFactor the scale factor for image PDFs, to correct PDFs that
	 *            are set too small (DPI number is divided by this factor)
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the argument image markup document
	 * @throws IOException
	 */
	public ImDocument loadImagePdfPages(ImDocument doc, Document pdfDoc, byte[] pdfBytes, int scaleFactor, ProgressMonitor pm) throws IOException {
		if (doc == null)
			doc = new ImDocument(getChecksum(pdfBytes));
		if (pdfDoc == null) try {
			pdfDoc = new Document();
			pdfDoc.setInputStream(new ByteArrayInputStream(pdfBytes), "");
		}
		catch (PDFException pdfe) {
			throw new IOException(pdfe.getMessage(), pdfe);
		}
		catch (PDFSecurityException pdfse) {
			throw new IOException(pdfse.getMessage(), pdfse);
		}
		if (pm == null)
			pm = ProgressMonitor.dummy;
		
		//	load pages
		pm.setStep("Loading document page images");
		pm.setBaseProgress(0);
		pm.setProgress(0);
		pm.setMaxProgress(100);
		this.addImagePdfPages(doc, pdfDoc, pdfBytes, scaleFactor, pm, null);
		
		//	finally ...
		return doc;
	}
	
	private void addImagePdfPages(final ImDocument doc, final Document pdfDoc, final byte[] pdfBytes, final int scaleFactor, final ProgressMonitor pm, final HashMap pageImageCache) throws IOException {
		
		//	load document structure (IcePDF is better at that ...)
		final Catalog catalog = pdfDoc.getCatalog();
		final PageTree pageTree = catalog.getPageTree();
		
		//	we're testing, and the test page image has already been found
		if ((PdfExtractorTest.aimAtPage != -1) && this.imageStore.isPageImageAvailable(doc.docId, PdfExtractorTest.aimAtPage)) {
			for (int p = 0; p < pageTree.getNumberOfPages(); p++) {
				BoundingBox pageBounds;
				if (p == PdfExtractorTest.aimAtPage) {
					PageImageInputStream piis = this.imageStore.getPageImageAsStream(doc.docId, p);
					piis.close();
					pageBounds = new BoundingBox(0, ((piis.originalWidth * piis.currentDpi) / piis.originalDpi), 0, ((piis.originalHeight * piis.currentDpi) / piis.originalDpi));
				}
				else pageBounds = new BoundingBox(0, 32, 0, 44); // have to use these numbers to prevent 0 or 1 step in page word index
				ImPage page = new ImPage(doc, p, pageBounds);
			}
			return;
		}
		
		//	extract page objects
		pm.setInfo("Getting page objects");
		final Hashtable[] pages = new Hashtable[pageTree.getNumberOfPages()];
		for (int p = 0; p < pageTree.getNumberOfPages(); p++) {
			Page page = pageTree.getPage(p, "");
			if (page == null) {
				pages[p] = new Hashtable();
				continue;
			}
			pages[p] = page.getEntries();
		}
		pm.setInfo(" --> got " + pageTree.getNumberOfPages() + " page objects");
		pm.setProgress(2);
		
		//	test if we have to extract any page images at all
		boolean gotAllPageImages = true;
		pm.setInfo("Checking if page images to extract");
		for (int p = 0; p < pages.length; p++) {
			if ((PdfExtractorTest.aimAtPage != -1) && (PdfExtractorTest.aimAtPage != p))
				continue;
			if (!this.imageStore.isPageImageAvailable(doc.docId, p)) {
				gotAllPageImages = false;
				break;
			}
		}
		
		//	we already have all page images in cache, save the hassle
		if (gotAllPageImages) {
			pm.setInfo(" --> all page images extracted before, loading from cache");
			for (int p = 0; p < pageTree.getNumberOfPages(); p++) {
				pm.setProgress(2 + ((p * 98) / pages.length));
				PageImageInputStream piis = this.imageStore.getPageImageAsStream(doc.docId, p);
				piis.close();
				BoundingBox pageBounds = new BoundingBox(0, ((piis.originalWidth * piis.currentDpi) / piis.originalDpi), 0, ((piis.originalHeight * piis.currentDpi) / piis.originalDpi));
				ImPage page = new ImPage(doc, p, pageBounds);
			}
			pm.setProgress(100);
			return;
		}
		
		//	read objects
		pm.setInfo("Getting remaining objects");
		final HashMap objects = PdfParser.getObjects(pdfBytes);
		pm.setInfo(" --> done");
		pm.setProgress(10);
		
		//	prepare working in parallel
		final LinkedList pageIDs = new LinkedList();
		final Throwable[] error = {null};
		
		//	extract page image objects
		pm.setInfo("Getting page image objects");
		final HashMap[] pageImages = new HashMap[pages.length];
		final int[] minPageImages = {Integer.MAX_VALUE};
		final int[] maxPageImages = {0};
		final int[] sumPageImages = {0};
		pageIDs.clear();
		for (int p = 0; p < pages.length; p++)
			pageIDs.addLast(new Integer(p));
		error[0] = null;
		this.runJob( new Runnable() {
			public void run() {
				while (true) try {
					
					//	any error or other problems in siblings?
					synchronized (error) {
						if (error[0] != null)
							return;
					}
					synchronized (minPageImages) {
						if (minPageImages[0] == 0)
							return;
					}
					
					//	get next page ID to work off
					int p;
					synchronized (pageIDs) {
						if (pageIDs.isEmpty())
							return;
						p = ((Integer) pageIDs.removeFirst()).intValue();
					}
					pageImages[p] = new HashMap(2);
					
					//	get resources
					Object resourcesObj = PdfParser.getObject(pages[p], "Resources", objects);
					if ((resourcesObj == null) || !(resourcesObj instanceof Hashtable)) {
						synchronized (pm) {
							pm.setInfo(" --> resource map not found");
						}
						synchronized (minPageImages) {
							minPageImages[0] = 0;
						}
						return;
					}
					
					//	map IDs to all images in page
					int imgCount = 0;
					for (Iterator rit = getSortedKeyIterator((Hashtable) resourcesObj); rit.hasNext();) {
						Object resKey = rit.next();
						Object resObj = PdfParser.dereference(((Hashtable) resourcesObj).get(resKey), objects);
						if (resObj == null)
							continue;
						if (resObj instanceof PStream) {
							pageImages[p].put(("i" + imgCount), new PImage((PStream) resObj));
							PImage mImg = PdfParser.getMaskImage(((PStream) resObj), objects);
							if (mImg != null)
								pageImages[p].put(("m" + imgCount), mImg);
							imgCount++;
						}
						else if ("XObject".equalsIgnoreCase(resKey.toString()) && (resObj instanceof Hashtable)) {
							for (Iterator xrit = getSortedKeyIterator((Hashtable) resObj); xrit.hasNext();) {
								Object xResKey = xrit.next();
								Object xResObj = PdfParser.dereference(((Hashtable) resObj).get(xResKey), objects);
								if (xResObj == null)
									continue;
								if (xResObj instanceof PStream) {
									pageImages[p].put(("i" + imgCount), new PImage((PStream) xResObj));
									PImage mImg = PdfParser.getMaskImage(((PStream) xResObj), objects);
									if (mImg != null)
										pageImages[p].put(("m" + imgCount), mImg);
									imgCount++;
								}
							}
						}
					}
					
					//	update registers
					synchronized (minPageImages) {
						minPageImages[0] = Math.min(minPageImages[0], pageImages[p].size());
					}
					synchronized (maxPageImages) {
						maxPageImages[0] = Math.max(maxPageImages[0], pageImages[p].size());
					}
					synchronized (sumPageImages) {
						sumPageImages[0] += pageImages[p].size();
					}
				}
				catch (Throwable t) {
					synchronized (error) {
						error[0] = t;
					}
					return;
				}
			}
		}, pages.length);
		pm.setInfo(" --> got at least " + minPageImages[0] + ", at most " + maxPageImages[0] + " images per page, " + sumPageImages[0] + " in total");
		pm.setProgress(20);
		
		//	check errors
		if (error[0] != null) {
			if (error[0] instanceof RuntimeException)
				throw ((RuntimeException) error[0]);
			else if (error[0] instanceof Error)
				throw ((Error) error[0]);
			else if (error[0] instanceof IOException)
				throw ((IOException) error[0]);
			else throw new IOException(error[0].getMessage());
		}
		
		//	check consistency
		if (minPageImages[0] == 0)
			throw new IOException("Unable to find images for all pages");
		
		//	find out which page image holds the text
		pm.setInfo("Determining text image ID");
		final String textImageId = this.findTextImageId(pageImages, pageTree, objects);
		if (textImageId == null)
			throw new IOException("Unable to find images for all pages");
		pm.setInfo(" ==> text page image id is " + textImageId);
		pm.setProgress(30);
		
		//	extract & save page images
		final BoundingBox[] pageBounds = new BoundingBox[pages.length];
		pageIDs.clear();
		if (PdfExtractorTest.aimAtPage != -1)
			pageIDs.addLast(new Integer(PdfExtractorTest.aimAtPage));
		else for (int p = 0; p < pages.length; p++)
			pageIDs.addLast(new Integer(p));
		error[0] = null;
		this.runJob( new Runnable() {
			public void run() {
				int cp = -1;
				while (true) try {
					cp = -1;
					
					//	any error or other problems in siblings?
					synchronized (error) {
						if (error[0] != null)
							return;
					}
					
					//	get next page ID to work off, and update status info
					int p;
					synchronized (pageIDs) {
						if (pageIDs.isEmpty())
							return;
						p = ((Integer) pageIDs.removeFirst()).intValue();
						cp = p;
					}
					
					//	update status display (might be inaccurate, but better than lock escalation)
					synchronized (pm) {
						pm.setStep("Importing page " + (pages.length - pageIDs.size()) + " of " + pages.length);
						pm.setProgress(30 + (((pages.length - pageIDs.size() - 1) * 70) / pages.length));
					}
					
					//	get page image
					synchronized (pm) {
						pm.setInfo(" - getting page image");
					}
					
					//	image already extracted
					if (imageStore.isPageImageAvailable(doc.docId, p)) {
						pm.setInfo(" --> image already rendered");
						pm.setInfo(" - getting image data ...");
						PageImageInputStream piis = imageStore.getPageImageAsStream(doc.docId, p);
						piis.close();
						int dpi = piis.currentDpi;
						pageBounds[p] = new BoundingBox(0, ((piis.originalWidth * piis.currentDpi) / piis.originalDpi), 0, ((piis.originalHeight * piis.currentDpi) / piis.originalDpi));
						pm.setInfo(" - resolution is " + dpi + " DPI, page bounds are " + pageBounds[p].toString());
					}
					
					//	image not extracted as yet, do it now
					else {
						
						//	get compressed image
						PImage imageObject = ((PImage) pageImages[p].get(textImageId));
						if (imageObject == null) {
							synchronized (pm) {
								pm.setInfo(" --> image not found");
							}
							throw new RuntimeException("Could not find image object for page " + p);
						}
						synchronized (pm) {
							pm.setInfo(" --> image not rendered yet");
							pm.setInfo(" - rendering image ...");
						}
						
						//	get raw image
						BufferedImage pageImage = getImage(imageObject, objects);
						if (pageImage == null) {
							synchronized (pm) {
								pm.setInfo(" --> page image generation failed");
							}
							throw new RuntimeException("Could not generate image for page " + p);
						}
						synchronized (pm) {
							pm.setInfo(" - got page image sized " + pageImage.getWidth() + " x " + pageImage.getHeight());
						}
						
						//	compute DPI
						int dpiScaleFactor = scaleFactor;
						Rectangle2D.Float pb = pageTree.getPage(p, "").getMediaBox();
						float dpiRatio = ((((float) pageImage.getWidth()) / pb.width) + (((float) pageImage.getHeight()) / pb.height)) / 2;
						float rawDpi = dpiRatio * defaultDpi;
						int dpi = ((Math.round(rawDpi / 10) * 10) / dpiScaleFactor);
						synchronized (pm) {
							pm.setInfo(" - resolution computed as " + dpi + " DPI");
						}
						
						//	guess DPI based on page size (minimum page size for (very most) publications should be A5 or so)
						float a5distWidth = Math.abs((pb.width / defaultDpi) - a5inchWidth);
						float a5distHeigth = Math.abs((pb.height / defaultDpi) - a5inchHeigth);
						float a7distWidth = Math.abs((pb.width / defaultDpi) - (a5inchWidth / 2));
						float a7distHeigth = Math.abs((pb.height / defaultDpi) - (a5inchHeigth / 2));
						while ((a7distWidth < a5distWidth) && (a7distHeigth < a5distHeigth) && (minScaleUpDpi < dpi)) {
							synchronized (pm) {
								pm.setInfo(" - A5 dist is " + a5distWidth + " / " + a5distHeigth);
								pm.setInfo(" - A7 dist is " + a7distWidth + " / " + a7distHeigth);
							}
							dpiScaleFactor++;
							dpi = ((Math.round(rawDpi / 10) * 10) / dpiScaleFactor);
							a5distWidth = Math.abs(((pb.width * dpiScaleFactor) / defaultDpi) - a5inchWidth);
							a5distHeigth = Math.abs(((pb.height * dpiScaleFactor) / defaultDpi) - a5inchHeigth);
							a7distWidth = Math.abs(((pb.width * dpiScaleFactor) / defaultDpi) - (a5inchWidth / 2));
							a7distHeigth = Math.abs(((pb.height * dpiScaleFactor) / defaultDpi) - (a5inchHeigth / 2));
						}
						if (dpiScaleFactor > 1)
							synchronized (pm) {
								pm.setInfo(" - resolution scaled to " + dpi + " DPI");
							}
						
						//	TODO if image is beyond 400 DPI, scale to half of that
						if (dpi >= minScaleDownDpi) {
							int scaleDownFactor = 2;
							while ((dpi / scaleDownFactor) >= minScaleDownDpi)
								scaleDownFactor++;
							synchronized (pm) {
								pm.setInfo(" - scaling page image to " + (dpi / scaleDownFactor) + " DPI");
							}
							BufferedImage sPageImage = new BufferedImage((pageImage.getWidth() / scaleDownFactor), (pageImage.getHeight() / scaleDownFactor), pageImage.getType());
							Graphics2D spig = sPageImage.createGraphics();
							spig.drawImage(pageImage, 0, 0, sPageImage.getWidth(), sPageImage.getHeight(), Color.WHITE, null);
							pageImage = sPageImage;
							dpi = (dpi / scaleDownFactor);
							synchronized (pm) {
								pm.setInfo(" - page image scaled to " + dpi + " DPI");
							}
						}
						
						//	enhance image (cannot use cache here, as image might change during correction)
						AnalysisImage ai = Imaging.wrapImage(pageImage, null);
						synchronized (pm) {
							pm.setInfo(" - enhancing image ...");
						}
						ai = Imaging.correctImage(ai, dpi, pm);
						pageImage = ai.getImage();
						
						//	TODO do further cleanup, e.g. removing pencil strokes, etc.
						//	TODO ==> collect examples and talk to Hilde
						
						//	store image
						String imageName = imageStore.storePageImage(doc.docId, p, pageImage, dpi);
						if (imageName == null) {
							synchronized (pm) {
								pm.setInfo(" --> page image storage failed");
							}
							throw new RuntimeException("Could not store image for page " + p);
						}
						if (pageImageCache != null)
							pageImageCache.put(new Integer(p), new PageImage(pageImage, dpi, imageStore)); // specifying image store from outside indicates who's handling the image now, as we know it was stored successfully
						Imaging.cleanUpCache(pageImage.hashCode() + "-");
						pageBounds[p] = new BoundingBox(0, pageImage.getWidth(), 0, pageImage.getHeight());
						synchronized (pm) {
							pm.setInfo(" --> page image stored, page bounds are " + pageBounds[p].toString());
						}
					}
				}
				
				//	catch whatever comes our way, so master thread re-throw it later
				catch (Throwable t) {
					synchronized (error) {
						error[0] = t;
					}
					return;
				}
				
				//	clean up after current page
				finally {
					if (cp != -1)
						pageImages[cp].clear();
					System.gc();
				}
			}
		}, pages.length);
		pm.setInfo(" --> got at least " + minPageImages[0] + ", at most " + maxPageImages[0] + " images per page, " + sumPageImages[0] + " in total");
		pm.setProgress(99);
		
		//	assemble document TODO figure out if attributes really required
		for (int p = 0; p < pages.length; p++) {
			if ((PdfExtractorTest.aimAtPage != -1) && (PdfExtractorTest.aimAtPage != p))
				continue;
			ImPage page = new ImPage(doc, p, pageBounds[p]);
		}
		
		//	finally ...
		pm.setProgress(100);
	}
	
	private void runJob(Runnable job, int numPages) {
		int numCores = Runtime.getRuntime().availableProcessors();
		int useCores = (this.useMultipleCores ? Math.max(1, Math.min((numCores - 1), numPages)) : 1);
		if (useCores < 2) {
			job.run();
			return;
		}
		Thread[] threads = new Thread[useCores];
		for (int t = 0; t < threads.length; t++)
			threads[t] = new Thread(job);
		for (int t = 0; t < threads.length; t++)
			threads[t].start();
		for (int t = 0; t < threads.length; t++) try {
			threads[t].join();
		} catch (InterruptedException ie) {t--; /* we have to make sure all threads are finished before returning */}
	}
	
	private static int textImageIdMinSamples = 3;
	private static int textImageIdMaxSamples = 20;
	private String findTextImageId(HashMap[] pageImages, PageTree pageTree, HashMap objects) throws IOException {
		HashSet imageIds = new HashSet();
		StringIndex imageIdScores = new StringIndex(true);
		
		StringIndex iidValidities = new StringIndex(true);
		int sampledPageCount = 0;
		
		if (pageImages.length <= textImageIdMinSamples) {
			for (int p = 0; p < pageImages.length; p++) {
				System.out.println(" Scoring images in page " + p);
				Rectangle2D.Float pb = pageTree.getPage(p, "").getMediaBox();
				BoundingBox pageBounds = new BoundingBox(((int) Math.round(pb.getMinX())), ((int) Math.round(pb.getMaxX())), ((int) Math.round(pb.getMinY())), ((int) Math.round(pb.getMaxY())));
				System.out.println(" - page bounds are " + (pageBounds.right - pageBounds.left) + " x " + (pageBounds.bottom - pageBounds.top));
				this.addImageIdsAndScores(pageImages[p], objects, imageIds, imageIdScores, pageBounds);
				for (Iterator iit = pageImages[p].keySet().iterator(); iit.hasNext();)
					iidValidities.add((String) iit.next());
				sampledPageCount++;
			}
		}
		else if (pageImages.length <= textImageIdMaxSamples) {
			HashSet sampled = new HashSet();
			while (sampled.size() < textImageIdMinSamples) {
				int p = ((int) (pageImages.length * Math.random()));
				if (sampled.add(new Integer(p))) {
					System.out.println(" Sampling images in page " + p);
					Rectangle2D.Float pb = pageTree.getPage(p, "").getMediaBox();
					BoundingBox pageBounds = new BoundingBox(((int) Math.round(pb.getMinX())), ((int) Math.round(pb.getMaxX())), ((int) Math.round(pb.getMinY())), ((int) Math.round(pb.getMaxY())));
					System.out.println(" - page bounds are " + (pageBounds.right - pageBounds.left) + " x " + (pageBounds.bottom - pageBounds.top));
					this.addImageIdsAndScores(pageImages[p], objects, imageIds, imageIdScores, pageBounds);
					for (Iterator iit = pageImages[p].keySet().iterator(); iit.hasNext();)
						iidValidities.add((String) iit.next());
					sampledPageCount++;
				}
			}
			for (int p = 0; p < pageImages.length; p++) {
				if (!sampled.add(new Integer(p)))
					continue;
				else if (!this.scoresAmbiguous(imageIds, imageIdScores))
					break;
				System.out.println(" Scoring images in page " + p);
				Rectangle2D.Float pb = pageTree.getPage(p, "").getMediaBox();
				BoundingBox pageBounds = new BoundingBox(((int) Math.round(pb.getMinX())), ((int) Math.round(pb.getMaxX())), ((int) Math.round(pb.getMinY())), ((int) Math.round(pb.getMaxY())));
				System.out.println(" - page bounds are " + (pageBounds.right - pageBounds.left) + " x " + (pageBounds.bottom - pageBounds.top));
				this.addImageIdsAndScores(pageImages[p], objects, imageIds, imageIdScores, pageBounds);
				for (Iterator iit = pageImages[p].keySet().iterator(); iit.hasNext();)
					iidValidities.add((String) iit.next());
				sampledPageCount++;
			}
		}
		else {
			HashSet sampled = new HashSet();
			while (sampled.size() < textImageIdMinSamples) {
				int p = ((int) (pageImages.length * Math.random()));
				if (sampled.add(new Integer(p))) {
					System.out.println(" Sampling images in page " + p);
					Rectangle2D.Float pb = pageTree.getPage(p, "").getMediaBox();
					BoundingBox pageBounds = new BoundingBox(((int) Math.round(pb.getMinX())), ((int) Math.round(pb.getMaxX())), ((int) Math.round(pb.getMinY())), ((int) Math.round(pb.getMaxY())));
					System.out.println(" - page bounds are " + (pageBounds.right - pageBounds.left) + " x " + (pageBounds.bottom - pageBounds.top));
					this.addImageIdsAndScores(pageImages[p], objects, imageIds, imageIdScores, pageBounds);
					for (Iterator iit = pageImages[p].keySet().iterator(); iit.hasNext();)
						iidValidities.add((String) iit.next());
					sampledPageCount++;
				}
			}
			while ((sampled.size() < textImageIdMaxSamples) && scoresAmbiguous(imageIds, imageIdScores)) {
				int p = ((int) (pageImages.length * Math.random()));
				if (sampled.add(new Integer(p))) {
					System.out.println(" Sampling images in page " + p);
					Rectangle2D.Float pb = pageTree.getPage(p, "").getMediaBox();
					BoundingBox pageBounds = new BoundingBox(((int) Math.round(pb.getMinX())), ((int) Math.round(pb.getMaxX())), ((int) Math.round(pb.getMinY())), ((int) Math.round(pb.getMaxY())));
					System.out.println(" - page bounds are " + (pageBounds.right - pageBounds.left) + " x " + (pageBounds.bottom - pageBounds.top));
					this.addImageIdsAndScores(pageImages[p], objects, imageIds, imageIdScores, pageBounds);
					for (Iterator iit = pageImages[p].keySet().iterator(); iit.hasNext();)
						iidValidities.add((String) iit.next());
					sampledPageCount++;
				}
			}
		}
		
		int maxScore = 0;
		String imageId = null;
		for (Iterator iit = imageIds.iterator(); iit.hasNext();) {
			String iid = ((String) iit.next());
			
			//	check if ID valid in two thirds of all sampled pages
			if ((iidValidities.getCount(iid) * 3) < (sampledPageCount * 2))
				continue;
			
			//	ensure ID works in at least two thirds of all pages
			int iidPages = 0;
			for (int p = 0; p < pageImages.length; p++) {
				if (pageImages[p].containsKey(iid))
					iidPages++;
			}
			if ((iidPages * 3) < (pageImages.length * 2))
				continue;
			
			//	check if we have a new top ID
			int iidScore = imageIdScores.getCount(iid);
			if (iidScore > maxScore) {
				imageId = iid;
				maxScore = iidScore;
			}
		}
		
		if (imageId != null) {
			for (int p = 0; p < pageImages.length; p++)
				for (Iterator iit = pageImages[p].keySet().iterator(); iit.hasNext();) {
					String imgId = ((String) iit.next());
					if (imageId.equals(imgId))
						continue;
					PImage img = ((PImage) pageImages[p].get(imgId));
					img.setImage(null);
				}
		}
		System.gc();
		return imageId;
	}
	
	private boolean scoresAmbiguous(HashSet imageIds, StringIndex imageIdScores) {
		int maxScore = 0;
		int secondMaxScore = 0;
		for (Iterator iit = imageIds.iterator(); iit.hasNext();) {
			String iid = ((String) iit.next());
			int iidScore = imageIdScores.getCount(iid);
			if (iidScore > maxScore) {
				secondMaxScore = maxScore;
				maxScore = iidScore;
			}
			else if (iidScore > secondMaxScore)
				secondMaxScore = iidScore;
		}
		return ((maxScore / 2) < secondMaxScore);
	}
	
	private void addImageIdsAndScores(HashMap pageImages, HashMap objects, HashSet imageIds, StringIndex imageIdScores, BoundingBox pageBounds) throws IOException {
		int pageWidth = (pageBounds.right - pageBounds.left);
		int pageHeight = (pageBounds.bottom - pageBounds.top);
		float pageWidthByHeight = (((float) pageWidth) / pageHeight);
		for (Iterator iit = pageImages.keySet().iterator(); iit.hasNext();) {
			String imgId = ((String) iit.next());
			System.out.println(" - doing image " + imgId);
			PImage img = ((PImage) pageImages.get(imgId));
			
			//	get image
			BufferedImage bi = this.getImage(img, objects);
			if (bi == null) {
				System.out.println(" --> could not load image");
				iit.remove();
				continue;
			}
			System.out.println("   - got image, size is " + bi.getWidth() + " x " + bi.getHeight());
			
			/* use this to analyze cases with page images sliced into stripes,
			 * tiles, etc., to figure out how to re-assemble them */
//			//	deal with cases of page images stored in tiles
//			ImageIO.write(bi, IMAGE_FORMAT, new File(this.basePath, ("Image." + pageTime + "." + imgId + "." + IMAGE_FORMAT)));
			
			//	check image size (this factually checks for at least 72 DPI)
			if ((bi.getWidth() * 100) < (pageWidth * 98)) {
				System.out.println(" --> image too small, width is less than 98% of " + pageWidth);
				iit.remove();
				continue;
			}
			if ((bi.getHeight() * 100) < (pageHeight * 98)) {
				System.out.println(" --> image too small, height is less than 98% of " + pageHeight);
				iit.remove();
				continue;
			}
			System.out.println("   - image size is fine");
			
			//	check image proportions (must be similar to page proportions if we have an image of the page text)
			float imgWidthByHeight = (((float) bi.getWidth()) / bi.getHeight());
			if (((pageWidthByHeight / imgWidthByHeight) < 0.8) || ((imgWidthByHeight / pageWidthByHeight) < 0.8)) {
				System.out.println(" --> image out of proportion, width by hight is " + imgWidthByHeight + ", but page is " + pageWidthByHeight);
				iit.remove();
				continue;
			}
			System.out.println("   - image proportion is fine");
			
			//	check if it is a text image
			int imgScore = this.scoreImage(bi, imgId);
			imageIds.add(imgId);
			imageIdScores.add(imgId, imgScore);
			System.out.println(" --> score is " + imgScore);
		}
	}
	
	private int scoreImage(BufferedImage img, String imgId) throws IOException {
		Complex[][] fft = getFft(img);
		double max = Imaging.getMax(fft, Imaging.ADJUST_MODE_SQUARE_ROOT);
		System.out.println("Got FFT (" + fft.length + "x" + fft[0].length + "), max is " + max);
		
		ArrayList peaks = new ArrayList();
		Imaging.collectPeaks(fft, peaks, max/4, Imaging.ADJUST_MODE_SQUARE_ROOT);
		System.out.println("Got " + peaks.size() + " peaks");
		
		ArrayList sPeaks = new ArrayList();
		for (int p = 0; p < peaks.size(); p++) {
			Peak peak = ((Peak) peaks.get(p));
			if (peak.h >= (max/2))
				sPeaks.add(peak);
		}
		System.out.println("Got " + sPeaks.size() + " scoring peaks");
		
		int score = 0;
		for (int p = 0; p < sPeaks.size(); p++) {
			Peak peak = ((Peak) sPeaks.get(p));
			score += Math.abs(peak.y - fft.length/2);
		}
		System.out.println("Score for " + imgId + " is " + score);
		return score;
	}
	
	private Complex[][] getFft(BufferedImage image) throws IOException {
		boolean isPage = (image.getWidth() < image.getHeight());
		System.out.println("Image loaded (" + image.getWidth() + " x " + image.getHeight() + ") ==> " + (isPage ? "page" : "word"));
		int tdimMax = 256;
		int tdim = 1;
		while ((tdim < tdimMax) && ((tdim < image.getWidth()) || (tdim < image.getHeight())))
			tdim *= 2;
		Complex[][] fft = Imaging.getFft(image, tdim, isPage);
		return fft;
	}
	
	private static Iterator getSortedKeyIterator(Hashtable ht) {
		ArrayList keys = new ArrayList(ht.keySet());
		Collections.sort(keys, new Comparator() {
			public int compare(Object o1, Object o2) {
				return o1.toString().compareTo(o2.toString());
			}
		});
		return keys.iterator();
	}
	
	private BufferedImage getImage(PImage pi, HashMap objects) throws IOException {
		BufferedImage bi = pi.getImage();
		System.out.println("Getting image from " + pi.data.params);
		if (bi == null) {
			bi = this.decodeImage(pi.data.params, pi.data.bytes, objects);
			System.out.println(" - image extracted, type is " + bi.getType());
			if (bi.getType() == BufferedImage.TYPE_CUSTOM) {
				BufferedImage rgbBi = new BufferedImage(bi.getWidth(), bi.getHeight(), BufferedImage.TYPE_INT_RGB);
				rgbBi.getGraphics().drawImage(bi, 0, 0, null);
				bi = rgbBi;
			}
			else if (bi.getType() == BufferedImage.TYPE_BYTE_BINARY) {}
			else if (bi.getType() == BufferedImage.TYPE_BYTE_GRAY) {}
			else if (bi.getType() != BufferedImage.TYPE_INT_RGB) {
				BufferedImage rgbBi = new BufferedImage(bi.getWidth(), bi.getHeight(), BufferedImage.TYPE_INT_RGB);
				rgbBi.getGraphics().drawImage(bi, 0, 0, null);
				bi = rgbBi;
			}
			System.out.println(" - type adjusted to " + bi.getType());
			pi.setImage(bi);
			System.out.println(" ==> image extracted");
		}
		else System.out.println(" ==> extracted before");
		return bi;
	}
	
	private BufferedImage decodeImage(Hashtable params, byte[] stream, HashMap objects) throws IOException {
		System.out.println(" ==> read " + stream.length + " bytes");
		System.out.println(" ==> Lenght parameter is " + params.get("Length"));
		System.out.println(" ==> Width parameter is " + params.get("Width"));
		System.out.println(" ==> Height parameter is " + params.get("Height"));
		int length;
		try {
			length = Integer.parseInt(params.get("Length").toString());
			System.out.println(" ==> Lenght is " + length);
		}
		catch (Exception e) {
			length = stream.length;
			System.out.println(" ==> fallback Lenght is " + length);
		}
		
		if (stream.length != length) {
			byte[] padStream = new byte[length];
			System.arraycopy(stream, 0, padStream, 0, Math.min(length, stream.length));
			if (Math.min(length, stream.length) < length)
				Arrays.fill(padStream, stream.length, length, ((byte) 13));
			stream = padStream;
		}
		
		Object filterObj = PdfParser.getObject(params, "Filter", objects);
		if (filterObj instanceof Vector) {
			if (((Vector) filterObj).size() != 0)
				filterObj = ((Vector) filterObj).get(0);
		}
		if (filterObj instanceof Reference)
			filterObj = objects.get(((Reference) filterObj).getObjectNumber() + " " + ((Reference) filterObj).getGenerationNumber());
		
		String filter = ((filterObj == null) ? null : filterObj.toString());
		System.out.println(" --> filter is " + filter);
		if ("JPXDecode".equals(filter))
			return this.decodeJPX(stream);
		else if ("JBIG2Decode".equals(filter)) {
			//	JPedal seems to be the one ...
			return this.decodeJBig2(stream);
		}
		else if ("FlateDecode".equals(filter)) {
			//	TODO use java.util.zip.GZIPInputStream instead of IcePDF
			return this.decodeFlate(stream, params, null);
		}
		else return this.decodeOther(stream, params, filter);
	}
	
//	private static String getImageMagickPath() {
//		String osName = System.getProperty("os.name");
//		if (osName.matches("Win.*2000"))
//			return "ImageMagickWin2K";
//		else if (osName.matches("Win.*"))
//			return "ImageMagickWinXP";
//		else if (osName.matches(".*Linux.*"))
//			return "ImageMagickLinux";
//		else return null;
//	}
//	
	private BufferedImage decodeJPX(byte[] stream) throws IOException {
		return this.decodeImageMagick(stream, "jp2");
	}
	
	private BufferedImage decodeImageMagick(byte[] stream, String format) throws IOException {
		return ((this.imageDecoder == null) ? null : this.imageDecoder.decodeImage(stream, format));
//		try {
//			//	this is a breach of the data provider principle,
//			//	but that's impossible to avoid if we want to use ImageMagick
//			File imPath = new File(this.basePath, getImageMagickPath());
//			String[] command = {
//					(new File(imPath, "convert.exe").getAbsolutePath()),
//					(format + ":-"),
//					("png:-"),
//			};
//			Process im = Runtime.getRuntime().exec(command, null, imPath.getAbsoluteFile());
//			OutputStream imIn = im.getOutputStream();
//			imIn.write(stream);
//			imIn.flush();
//			imIn.close();
//			BufferedImage bi = ImageIO.read(im.getInputStream());
//			System.out.println(" ==> got " + format.toUpperCase() + " image, size is " + bi.getWidth() + " x " + bi.getHeight());
//			im.waitFor();
//			return bi;
//		}
//		catch (InterruptedException ie) {
//			return null;
//		}
	}
	
	private BufferedImage decodeJBig2(byte[] stream) throws IOException {
		try {
			JBIG2Decoder jbd = new JBIG2Decoder();
			jbd.decodeJBIG2(stream);
			BufferedImage bi = jbd.getPageAsBufferedImage(0);
			System.out.println(" ==> got JBIG2 image, size is " + bi.getWidth() + " x " + bi.getHeight());
			return bi;
		}
		catch (JBIG2Exception jbe) {
			System.out.println(" ==> Could not decode image: " + jbe.getMessage());
			jbe.printStackTrace(System.out);
			return null;
		}
	}
	
	private BufferedImage decodeFlate(byte[] stream, Hashtable params, ByteArrayOutputStream pageContentAssembler) throws IOException {
		SeekableInputConstrainedWrapper streamInputWrapper = new SeekableInputConstrainedWrapper(new SeekableByteArrayInputStream(stream), 0, stream.length, true);
		Stream str = new Stream(new Library(), params, streamInputWrapper);
		try {
			BufferedImage bi = str.getImage(Color.white, new Resources(new Library(), params), false);
			if (bi != null)
				System.out.println(" ==> got flate image, size is " + bi.getWidth() + " x " + bi.getHeight());
			else System.out.println(" ==> Could not decode image");
			return bi;
		}
		catch (Exception e) {
			System.out.println(" ==> Could not decode image: " + e.getMessage());
			e.printStackTrace(System.out);
			if (true) {
				FlateDecode fd = new FlateDecode(new Library(), params, new ByteArrayInputStream(stream));
				byte[] buffer = new byte[1024];
				int read;
				while ((read = fd.read(buffer)) != -1)
					System.out.print(new String(buffer, 0, read));
				System.out.println();
				fd.close();
			}
			return null;
		}
	}
	
	private BufferedImage decodeOther(byte[] stream, Hashtable params, String filter) throws IOException {
		SeekableInputConstrainedWrapper streamInputWrapper = new SeekableInputConstrainedWrapper(new SeekableByteArrayInputStream(stream), 0, stream.length, true);
		Stream str = new Stream(new Library(), params, streamInputWrapper);
		try {
			BufferedImage bi = str.getImage(Color.white, new Resources(new Library(), params), false);
			if (bi != null)
				System.out.println(" ==> got image, size is " + bi.getWidth() + " x " + bi.getHeight());
			else System.out.println(" ==> Could not decode image");
			return bi;
		}
		catch (Exception e) {
			System.out.println(" ==> Could not decode image: " + e.getMessage());
			e.printStackTrace(System.out);
			if (true) {
				System.out.print(new String(stream, 0, stream.length));
				System.out.println();
			}
			return null;
		}
	}
	
	private static MessageDigest checksumDigester = null;
	private static synchronized String getChecksum(byte[] pdfBytes) {
		if (checksumDigester == null) {
			try {
				checksumDigester = MessageDigest.getInstance("MD5");
			}
			catch (NoSuchAlgorithmException nsae) {
				System.out.println(nsae.getClass().getName() + " (" + nsae.getMessage() + ") while creating checksum digester.");
				nsae.printStackTrace(System.out); // should not happen, but Java don't know ...
				return Gamta.getAnnotationID(); // use random value to avoid collisions
			}
		}
		checksumDigester.reset();
		checksumDigester.update(pdfBytes);
		byte[] checksumBytes = checksumDigester.digest();
		String checksum = new String(RandomByteSource.getHexCode(checksumBytes));
		return checksum;
	}
}