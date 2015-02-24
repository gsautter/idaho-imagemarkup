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
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
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
import java.util.Map;
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
import de.uka.ipd.idaho.gamta.util.ParallelJobRunner;
import de.uka.ipd.idaho.gamta.util.ParallelJobRunner.ParallelFor;
import de.uka.ipd.idaho.gamta.util.ProgressMonitor;
import de.uka.ipd.idaho.gamta.util.ProgressMonitor.SynchronizedProgressMonitor;
import de.uka.ipd.idaho.gamta.util.constants.TableConstants;
import de.uka.ipd.idaho.gamta.util.imaging.BoundingBox;
import de.uka.ipd.idaho.gamta.util.imaging.ImagingConstants;
import de.uka.ipd.idaho.gamta.util.imaging.PageImage;
import de.uka.ipd.idaho.gamta.util.imaging.PageImageInputStream;
import de.uka.ipd.idaho.gamta.util.imaging.PageImageStore;
import de.uka.ipd.idaho.im.ImDocument;
import de.uka.ipd.idaho.im.ImFont;
import de.uka.ipd.idaho.im.ImPage;
import de.uka.ipd.idaho.im.ImRegion;
import de.uka.ipd.idaho.im.ImSupplement;
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
import de.uka.ipd.idaho.im.pdf.PdfParser.PFigure;
import de.uka.ipd.idaho.im.pdf.PdfParser.PImage;
import de.uka.ipd.idaho.im.pdf.PdfParser.PStream;
import de.uka.ipd.idaho.im.pdf.PdfParser.PWord;
import de.uka.ipd.idaho.im.pdf.test.PdfExtractorTest;
import de.uka.ipd.idaho.im.utilities.ImageDisplayDialog;
import de.uka.ipd.idaho.stringUtils.StringIndex;

/**
 * Utility for extracting page images and text from a PDF file.
 * 
 * @author sautter
 */
public class PdfExtractor implements ImagingConstants, TableConstants {
	
	/* TODO cut page structure analysis and OCR out of this class
	 * - more universal, just need to have ImDocument and page images
	 * ==> facilitates using them on staples (bundles of scans) as well
	 */
	
	/* TODO facilitate using style patterns (keyed by journal name and year of publication):
	 * - block margins
	 * - column margins
	 * - ...
	 */
	
	//	TODO_maybe facilitate learning style patterns
	
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
		
		//	check arguments
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
		
		//	preserve source PDF in supplement
		ImSupplement.Source.createSource(doc, "application/pdf", pdfBytes);
		
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
		
		//	check arguments
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
		
		//	preserve source PDF in supplement
		ImSupplement.Source.createSource(doc, "application/pdf", pdfBytes);
		
		//	finally ...
		return this.doLoadTextPdf(doc, pdfDoc, pdfBytes, pm);
	}
	
	private static final boolean DEBUG_EXTRACT_FIGURES = false;
	
	private ImDocument doLoadTextPdf(final ImDocument doc, final Document pdfDoc, byte[] pdfBytes, ProgressMonitor pm) throws IOException {
		
		//	build progress monitor with synchronized methods instead of synchronizing in-code
		final ProgressMonitor spm = ((pm instanceof SynchronizedProgressMonitor) ? pm : new SynchronizedProgressMonitor(pm));
		
		//	load document structure (IcePDF is better at that ...)
		final Catalog catalog = pdfDoc.getCatalog();
		final Library library = catalog.getLibrary();
		final PageTree pageTree = catalog.getPageTree();
		final float magnification = (((float) this.textPdfPageImageDpi) / defaultDpi);
		
		//	parse PDF
		final HashMap objects = PdfParser.getObjects(pdfBytes);
		
		//	get tokenizer to check and split words with
		final Tokenizer tokenizer = ((Tokenizer) doc.getAttribute(ImDocument.TOKENIZER_ATTRIBUTE, Gamta.INNER_PUNCTUATION_TOKENIZER));
		
		//	prepare working in parallel
		ParallelFor pf;
		
		//	extract page objects
		spm.setStep("Extracting page content");
		spm.setBaseProgress(0);
		spm.setProgress(0);
		spm.setMaxProgress(5);
		final byte[][] pageContents = new byte[pageTree.getNumberOfPages()][];
		final Hashtable[] pageResources = new Hashtable[pageTree.getNumberOfPages()];
		pf = new ParallelFor() {
			public void doFor(int p) throws Exception {
				
				//	update status display (might be inaccurate, but better than lock escalation)
				spm.setInfo("Importing page " + p + " of " + pageTree.getNumberOfPages());
				spm.setProgress((p * 100) / pageTree.getNumberOfPages());
				
				//	get page bounding box
				Page page = pageTree.getPage(p, "");
				
				//	extract page contents to recover layout information
				System.out.println("Page content is " + page.getEntries());
				Object contentsObj = PdfParser.getObject(page.getEntries(), "Contents", objects);
				if (contentsObj == null)
					spm.setInfo(" --> content not found");
				else {
					spm.setInfo(" --> got content");
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					if (contentsObj instanceof PStream) {
						Object filter = ((PStream) contentsObj).params.get("Filter");
						spm.setInfo("   --> stream content, filter is " + filter + " (from " + ((PStream) contentsObj).params + ")");
//						try {
							PdfParser.decode(filter, ((PStream) contentsObj).bytes, ((PStream) contentsObj).params, baos, objects);
//						} catch (Exception e) { pm.setInfo("   --> decoding failed: " + e.getMessage()); }
					}
					else if (contentsObj instanceof Vector) {
						spm.setInfo("   --> array content");
						for (Iterator cit = ((Vector) contentsObj).iterator(); cit.hasNext();) {
							Object contentObj = PdfParser.dereference(cit.next(), objects);
							if (contentObj instanceof PStream) {
								Object filter = ((PStream) contentObj).params.get("Filter");
								if (filter == null)
									continue;
								spm.setInfo("   --> stream content part, filter is " + filter + " (from " + ((PStream) contentObj).params + ")");
//								try {
									 PdfParser.decode(filter, ((PStream) contentObj).bytes, ((PStream) contentObj).params, baos, objects);
//								} catch (Exception e) { pm.setInfo("   --> decoding failed: " + e.getMessage()); }
							}
						}
					}
					pageContents[p] = baos.toByteArray();
					
					Object resourcesObj = PdfParser.getObject(page.getEntries(), "Resources", objects);
					if (resourcesObj == null)
						spm.setInfo(" --> resources not found");
					else {
						resourcesObj = PdfParser.dereference(resourcesObj, objects);
						spm.setInfo(" --> resources are " + resourcesObj);
						pageResources[p] = ((Hashtable) resourcesObj);
					}
				}
			}
		};
		ParallelJobRunner.runParallelFor(pf, ((PdfExtractorTest.aimAtPage < 0) ? 0 : PdfExtractorTest.aimAtPage), ((PdfExtractorTest.aimAtPage < 0) ? pageTree.getNumberOfPages() : (PdfExtractorTest.aimAtPage + 1)), (this.useMultipleCores ? -1 : 1));
		spm.setProgress(100);
		
		//	check errors
		this.checkException(pf);
		
		//	extract page objects
		spm.setStep("Assessing font char usage");
		spm.setBaseProgress(5);
		spm.setProgress(0);
		spm.setMaxProgress(10);
		pf = new ParallelFor() {
			public void doFor(int p) throws Exception {
				
				//	nothing to work with
				if ((pageContents[p] == null) || (pageResources[p] == null))
					return;
				
				//	update status display (might be inaccurate, but better than lock escalation)
				spm.setInfo("Assessing font char usage in page " + p + " of " + pageTree.getNumberOfPages());
				spm.setProgress((p * 100) / pageTree.getNumberOfPages());
				
				//	get page bounding box
				PdfParser.getPageWordChars(pageContents[p], pageResources[p], objects, spm);
			}
		};
		ParallelJobRunner.runParallelFor(pf, ((PdfExtractorTest.aimAtPage < 0) ? 0 : PdfExtractorTest.aimAtPage), ((PdfExtractorTest.aimAtPage < 0) ? pageTree.getNumberOfPages() : (PdfExtractorTest.aimAtPage + 1)), (this.useMultipleCores ? -1 : 1));
		spm.setProgress(100);
		
		//	check errors
		this.checkException(pf);
		
		//	extract page objects
		spm.setStep("Importing page words");
		spm.setBaseProgress(10);
		spm.setProgress(0);
		spm.setMaxProgress(40);
		final Rectangle2D.Float[] pageBoxes = new Rectangle2D.Float[pageTree.getNumberOfPages()];
		final PWord[][] pWords = new PWord[pageTree.getNumberOfPages()][];
		pf = new ParallelFor() {
			public void doFor(int p) throws Exception {
				
				//	nothing to work with
				if ((pageContents[p] == null) || (pageResources[p] == null))
					return;
				
				//	update status display (might be inaccurate, but better than lock escalation)
				spm.setInfo("Importing page " + p + " of " + pageTree.getNumberOfPages());
				spm.setProgress((p * 100) / pageTree.getNumberOfPages());
				
				//	get page bounding box
				Page page = pageTree.getPage(p, "");
				pageBoxes[p] = page.getCropBox();
				if (pageBoxes[p] == null)
					pageBoxes[p] = page.getMediaBox();
				
				//	prepare collecting embedded figures
				ArrayList pFigures = new ArrayList();
				
				//	extract page contents to recover layout information
				pWords[p] = PdfParser.getPageWords(pageContents[p], pageResources[p], objects, pFigures, tokenizer, spm);
				System.out.println(" --> extracted " + pWords[p].length + " words and " + pFigures.size() + " figures");
				for (int w = 0; w < pWords[p].length; w++)
					System.out.println("   - " + pWords[p][w]);
				for (int f = 0; f < pFigures.size(); f++)
					System.out.println("   - " + pFigures.get(f));
				spm.setInfo(" - got page content with " + pWords[p].length + " words and " + pFigures.size() + " figures");
				
				//	preserve figures embedded in pages
				if (pFigures.size() != 0) {
					spm.setInfo(" - storing figures");
					
					//	display figures if testing
					ImageDisplayDialog fdd = null;
					if (DEBUG_EXTRACT_FIGURES && (PdfExtractorTest.aimAtPage >= 0))
						fdd = new ImageDisplayDialog("Figures in Page " + p);
					
					//	get XObject dictionary from page resource dictionary
					Object xObjectsObj = pageResources[p].get("XObject");
					if (xObjectsObj instanceof Hashtable) {
						Hashtable xObjects = ((Hashtable) xObjectsObj);
						spm.setInfo("   - XObject dictionary is " + xObjects);
						
						//	store figures
						for (int f = 0; f < pFigures.size(); f++) {
							PFigure pFigure = ((PFigure) pFigures.get(f));
							spm.setInfo("   - " + pFigure);
							
							//	resolve image names against XObject dictionary
							Object pFigureKey = xObjects.get(pFigure.name);
							spm.setInfo("     - reference is " + pFigureKey);
							
							//	get actual image object
							Object pFigureData = PdfParser.dereference(pFigureKey, objects);
							spm.setInfo("     - figure data is " + pFigureData);
							if (!(pFigureData instanceof PStream)) {
								spm.setInfo("   --> strange figure data");
								continue;
							}
							
							//	decode PStream to image
							BufferedImage pFigureImage = decodeImage(((PStream) pFigureData).params, ((PStream) pFigureData).bytes, objects, library, page.getResources());
							if (pFigureImage == null) {
								spm.setInfo("   --> could not decode figure");
								continue;
							}
							spm.setInfo("     - got figure sized " + pFigureImage.getWidth() + " x " + pFigureImage.getHeight());
							
							//	compute figure resolution
							float dpiRatio = ((float) ((((float) pFigureImage.getWidth()) / pFigure.bounds.getWidth()) + (((float) pFigureImage.getHeight()) / pFigure.bounds.getHeight())) / 2);
							float rawDpi = dpiRatio * defaultDpi;
							int pFigureDpi = (Math.round(rawDpi / 10) * 10);
							spm.setInfo("     - resolution computed as " + pFigureDpi + " DPI (" + rawDpi + ")");
							
							/* If color space is DeviceCMYK, render page image to figure
							 * resolution and cut out image: images with color space
							 * DeviceCMYK come out somewhat differently colored than in
							 * Acrobat, but we cannot seem to do anything about this -
							 * IcePDF distorts them as well, if more in single image
							 * rendering than in the page images it generates. */
							Object csObj = ((PStream) pFigureData).params.get("ColorSpace");
							if (csObj != null) {
								System.out.println("     - color space is " + csObj.toString());
								if ("DeviceCMYK".equals(csObj.toString())) {
									pFigureImage = extractFigureFromPageImage(pdfDoc, p, pageBoxes[p], pFigureDpi, pFigure.bounds);
									System.out.println("     --> figure re-rendered as part of page image");
								}
							}
							
							//	convert bounds, as PDF Y coordinate is bottom-up, whereas Java, JavaScript, etc. Y coordinate is top-down
							int left = Math.round(((float) (pFigure.bounds.getMinX() - pageBoxes[p].getMinX())) * magnification);
							int right = Math.round(((float) (pFigure.bounds.getMaxX() - pageBoxes[p].getMinX()))  * magnification);
							int top = Math.round((pageBoxes[p].height - ((float) (pFigure.bounds.getMaxY() - ((2 * pageBoxes[p].getMinY()) - pageBoxes[p].getMaxY())))) * magnification);
							int bottom = Math.round((pageBoxes[p].height - ((float) (pFigure.bounds.getMinY() - ((2 * pageBoxes[p].getMinY()) - pageBoxes[p].getMaxY())))) * magnification);
							BoundingBox pFigureBounds = new BoundingBox(left, right, top, bottom);
							spm.setInfo("     - rendering bounds are " + pFigureBounds);
							
							//	add figures as supplements to document (synchronized !!!)
							synchronized (doc) {
								ImSupplement.Figure.createFigure(doc, p, pFigureDpi, pFigureImage, pFigureBounds);
							}
							
							//	display figures if testing
							if (fdd != null)
								fdd.addImage(pFigureImage, pFigureBounds.toString());
						}
					}
					
					//	display figures if testing
					if (fdd != null) {
						fdd.setSize(600, 800);
						fdd.setLocationRelativeTo(null);
						fdd.setVisible(true);
					}
				}
				
				if (pWords[p].length == 0) {
					spm.setInfo(" --> empty page");
					return;
				}
				
				//	shrink word bounding boxes to actual word size
				BufferedImage mbi = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY);
				Graphics2D mg = mbi.createGraphics();
				for (int w = 0; w < pWords[p].length; w++) {
					if (pWords[p][w].str.trim().length() != 0)
						pWords[p][w] = shrinkWordBounds(mg, pWords[p][w]);
				}
				
				//	sort words left to right and top to bottom (for horizontal writing direction, for other writing directions accordingly)
				Arrays.sort(pWords[p], new Comparator() {
					public int compare(Object o1, Object o2) {
						PWord pw1 = ((PWord) o1);
						PWord pw2 = ((PWord) o2);
						if (pw1.fontDirection != pw2.fontDirection)
							return (pw1.fontDirection - pw2.fontDirection);
						if (pw1.fontDirection == PWord.LEFT_RIGHT_FONT_DIRECTION) {
							double m1 = ((pw1.bounds.getMinY() + pw1.bounds.getMaxY()) / 2);
							double m2 = ((pw2.bounds.getMinY() + pw2.bounds.getMaxY()) / 2);
							if ((m1 < pw2.bounds.getMaxY()) && (m1 > pw2.bounds.getMinY()))
								return Double.compare(pw1.bounds.getMinX(), pw2.bounds.getMinX());
							if ((m2 < pw1.bounds.getMaxY()) && (m2 > pw1.bounds.getMinY()))
								return Double.compare(pw1.bounds.getMinX(), pw2.bounds.getMinX());
							if (m1 > pw2.bounds.getMaxY())
								return -1;
							if (m1 < pw2.bounds.getMinY())
								return 1;
							if (m2 > pw1.bounds.getMaxY())
								return 1;
							if (m2 < pw1.bounds.getMinY())
								return -1;
						}
						else if (pw1.fontDirection == PWord.BOTTOM_UP_FONT_DIRECTION) {
							double m1 = ((pw1.bounds.getMinX() + pw1.bounds.getMaxX()) / 2);
							double m2 = ((pw2.bounds.getMinX() + pw2.bounds.getMaxX()) / 2);
							if ((m1 < pw2.bounds.getMaxX()) && (m1 > pw2.bounds.getMinX()))
								return Double.compare(pw1.bounds.getMinY(), pw2.bounds.getMinY());
							if ((m2 < pw1.bounds.getMaxX()) && (m2 > pw1.bounds.getMinX()))
								return Double.compare(pw1.bounds.getMinY(), pw2.bounds.getMinY());
							if (m1 > pw2.bounds.getMaxX())
								return 1;
							if (m1 < pw2.bounds.getMinX())
								return -1;
							if (m2 > pw1.bounds.getMaxX())
								return -1;
							if (m2 < pw1.bounds.getMinX())
								return 1;
						}
						else if (pw1.fontDirection == PWord.TOP_DOWN_FONT_DIRECTION) {
							double m1 = ((pw1.bounds.getMinX() + pw1.bounds.getMaxX()) / 2);
							double m2 = ((pw2.bounds.getMinX() + pw2.bounds.getMaxX()) / 2);
							if ((m1 < pw2.bounds.getMaxX()) && (m1 > pw2.bounds.getMinX()))
								return Double.compare(pw2.bounds.getMinY(), pw1.bounds.getMinY());
							if ((m2 < pw1.bounds.getMaxX()) && (m2 > pw1.bounds.getMinX()))
								return Double.compare(pw2.bounds.getMinY(), pw1.bounds.getMinY());
							if (m1 > pw2.bounds.getMaxX())
								return -1;
							if (m1 < pw2.bounds.getMinX())
								return 1;
							if (m2 > pw1.bounds.getMaxX())
								return 1;
							if (m2 < pw1.bounds.getMinX())
								return -1;
						}
						return 0;
					}
				});
				
				//	merge subsequent words less than DPI/60 apart (likely separated due to font change)
				//	however, do not merge words that tokenize apart,
				//	nor ones that have mismatch in font style or size, as such differences usually bear some semantics
				double maxMergeMargin = (((double) defaultDpi) / 60);
				ArrayList pWordList = null;
				PWord lpWord = null;
				TokenSequence lpWordTokens = null;
				System.out.println("Checking word mergers ...");
				for (int w = 0; w < pWords[p].length; w++) {
					
					//	create token sequence
					TokenSequence pWordTokens = tokenizer.tokenize(pWords[p][w].str);
					
					//	no word to compare to as yet
					if ((lpWord == null) || (lpWordTokens == null)) {
						lpWord = pWords[p][w];
						lpWordTokens = pWordTokens;
						continue;
					}
					System.out.println(" - checking words '" + lpWord.str + "'@" + lpWord.bounds + " and '" + pWords[p][w].str + "'@" + pWords[p][w].bounds);
					
					//	check if words mergeable
					if (!areWordsMergeable(lpWord, lpWordTokens, pWords[p][w], pWordTokens, maxMergeMargin, tokenizer)) {
						lpWord = pWords[p][w];
						lpWordTokens = pWordTokens;
						continue;
					}
					
					//	figure out bold, italic, font size, etc. using weighted majority vote
					PdfFont mpWordFont = ((lpWord.str.length() < pWords[p][w].str.length()) ? pWords[p][w].font : lpWord.font);
					Rectangle2D.Float mpWordBounds;
					if (lpWord.fontDirection == PWord.LEFT_RIGHT_FONT_DIRECTION) {
						float top = ((float) Math.max(lpWord.bounds.getMaxY(), pWords[p][w].bounds.getMaxY()));
						float bottom = ((float) Math.min(lpWord.bounds.getMinY(), pWords[p][w].bounds.getMinY()));
						mpWordBounds = new Rectangle2D.Float(((float) lpWord.bounds.getMinX()), bottom, ((float) (pWords[p][w].bounds.getMaxX() - lpWord.bounds.getMinX())), (top - bottom));
					}
					else if (lpWord.fontDirection == PWord.BOTTOM_UP_FONT_DIRECTION) {
						float top = ((float) Math.min(lpWord.bounds.getMinX(), pWords[p][w].bounds.getMinX()));
						float bottom = ((float) Math.max(lpWord.bounds.getMaxX(), pWords[p][w].bounds.getMaxX()));
						mpWordBounds = new Rectangle2D.Float(top, ((float) lpWord.bounds.getMinY()), (bottom - top), ((float) (pWords[p][w].bounds.getMaxY() - lpWord.bounds.getMinY())));
					}
					else if (lpWord.fontDirection == PWord.TOP_DOWN_FONT_DIRECTION) {
						float top = ((float) Math.max(lpWord.bounds.getMaxX(), pWords[p][w].bounds.getMaxX()));
						float bottom = ((float) Math.min(lpWord.bounds.getMinX(), pWords[p][w].bounds.getMinX()));
						mpWordBounds = new Rectangle2D.Float(bottom, ((float) pWords[p][w].bounds.getMinY()), (top - bottom), ((float) (lpWord.bounds.getMaxY() - pWords[p][w].bounds.getMinY())));
					}
					else {
						lpWord = pWords[p][w];
						lpWordTokens = pWordTokens;
						continue;
					}
					
					//	create merged word
					PWord mpWord = new PWord((lpWord.charCodes + pWords[p][w].charCodes), (lpWord.str + pWords[p][w].str), mpWordBounds, lpWord.fontSize, lpWord.fontDirection, mpWordFont);
					System.out.println(" --> merged words " + lpWord.str + " and " + pWords[p][w].str + " to '" + mpWord.str + "'@" + mpWord.bounds);
					
					//	store merged word
					pWords[p][w] = mpWord;
					pWords[p][w-1] = null;
					lpWord = mpWord;
					lpWordTokens = tokenizer.tokenize(mpWord.str);
					
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
					String[] splitCharCodes = new String[pWordTokens.size()];
					String[] splitTokens = new String[pWordTokens.size()];
					float[] splitTokenWidths = new float[pWordTokens.size()];
					int fontStyle = Font.PLAIN;
					if (pWords[p][w].bold)
						fontStyle = (fontStyle | Font.BOLD);
					if (pWords[p][w].italics)
						fontStyle = (fontStyle | Font.ITALIC);
					Font pWordFont = getFont(pWords[p][w].font.name, fontStyle, Math.round(((float) pWords[p][w].fontSize)));
					float splitTokenWidthSum = 0;
					int splitCharCodeStart = 0;
					for (int s = 0; s < splitTokens.length; s++) {
						splitTokens[s] = pWordTokens.valueAt(s);
						splitCharCodes[s] = ""; // have to do it this way, as char code string might have different length than Unicode string
						for (int splitCharCodeLength = 0; splitCharCodeLength < splitTokens[s].length();) {
							char charCode = pWords[p][w].charCodes.charAt(splitCharCodeStart++);
							splitCharCodes[s] += ("" + charCode);
							splitCharCodeLength += (((int) charCode) / 256);
						}
						TextLayout tl = new TextLayout(splitTokens[s], pWordFont, new FontRenderContext(null, false, true));
						splitTokenWidths[s] = ((float) tl.getBounds().getWidth());
						splitTokenWidthSum += splitTokenWidths[s];
					}
					
					//	left-right words
					if (pWords[p][w].fontDirection == PWord.LEFT_RIGHT_FONT_DIRECTION) {
						
						//	store split result, splitting word bounds accordingly
						float pWordWidth = ((float) (pWords[p][w].bounds.getMaxX() - pWords[p][w].bounds.getMinX()));
						float splitTokenLeft = ((float) pWords[p][w].bounds.getMinX());
						for (int s = 0; s < splitTokens.length; s++) {
							float splitTokenWidth = ((pWordWidth * splitTokenWidths[s]) / splitTokenWidthSum);
							PWord spWord = new PWord(splitCharCodes[s], splitTokens[s], new Rectangle2D.Float(splitTokenLeft, ((float) pWords[p][w].bounds.getMinY()), splitTokenWidth, ((float) pWords[p][w].bounds.getHeight())), pWords[p][w].fontSize, pWords[p][w].fontDirection, pWords[p][w].font);
							pWordList.add(spWord);
							splitTokenLeft += splitTokenWidth;
							System.out.println(" --> split word part " + spWord.str + ", bounds are " + spWord.bounds);
						}
					}
					
					//	bottom-up words
					else if (pWords[p][w].fontDirection == PWord.BOTTOM_UP_FONT_DIRECTION) {
						
						//	store split result, splitting word bounds accordingly
						float pWordWidth = ((float) (pWords[p][w].bounds.getMaxY() - pWords[p][w].bounds.getMinY()));
						float splitTokenLeft = ((float) pWords[p][w].bounds.getMinY());
						for (int s = 0; s < splitTokens.length; s++) {
							float splitTokenWidth = ((pWordWidth * splitTokenWidths[s]) / splitTokenWidthSum);
							PWord spWord = new PWord(splitCharCodes[s], splitTokens[s], new Rectangle2D.Float(((float) pWords[p][w].bounds.getMinX()), splitTokenLeft, ((float) pWords[p][w].bounds.getWidth()), splitTokenWidth), pWords[p][w].fontSize, pWords[p][w].fontDirection, pWords[p][w].font);
							pWordList.add(spWord);
							splitTokenLeft += splitTokenWidth;
							System.out.println(" --> split word part " + spWord.str + ", bounds are " + spWord.bounds);
						}
					}
					
					//	top-down words
					else if (pWords[p][w].fontDirection == PWord.TOP_DOWN_FONT_DIRECTION) {
						
						//	store split result, splitting word bounds accordingly
						float pWordWidth = ((float) (pWords[p][w].bounds.getMaxY() - pWords[p][w].bounds.getMinY()));
						float splitTokenLeft = ((float) pWords[p][w].bounds.getMaxY());
						for (int s = 0; s < splitTokens.length; s++) {
							float splitTokenWidth = ((pWordWidth * splitTokenWidths[s]) / splitTokenWidthSum);
							PWord spWord = new PWord(splitCharCodes[s], splitTokens[s], new Rectangle2D.Float(((float) pWords[p][w].bounds.getMinX()), (splitTokenLeft - splitTokenWidth), ((float) pWords[p][w].bounds.getWidth()), splitTokenWidth), pWords[p][w].fontSize, pWords[p][w].fontDirection, pWords[p][w].font);
							pWordList.add(spWord);
							splitTokenLeft -= splitTokenWidth;
							System.out.println(" --> split word part " + spWord.str + ", bounds are " + spWord.bounds);
						}
					}
				}
				
				//	refresh PWord array
				if (pWordList.size() != pWords[p].length)
					pWords[p] = ((PWord[]) pWordList.toArray(new PWord[pWordList.size()]));
				pWordList = null;
				if (pWords[p].length == 0)
					spm.setInfo(" --> empty page");
				else spm.setInfo(" --> got " + pWords[p].length + " words in PDF");
			}
		};
		ParallelJobRunner.runParallelFor(pf, ((PdfExtractorTest.aimAtPage < 0) ? 0 : PdfExtractorTest.aimAtPage), ((PdfExtractorTest.aimAtPage < 0) ? pageTree.getNumberOfPages() : (PdfExtractorTest.aimAtPage + 1)), (this.useMultipleCores ? -1 : 1));
		spm.setProgress(100);
		
		//	check errors
		this.checkException(pf);
		
		//	check plausibility
		if (PdfExtractorTest.aimAtPage == -1) {
			int docWordCount = 0;
			for (int p = 0; p < pWords.length; p++)
				docWordCount += pWords[p].length;
			if ((docWordCount / pageTree.getNumberOfPages()) < minAveragePageWords)
				throw new IOException("Too few words per page (" + docWordCount + " on " + pageTree.getNumberOfPages() + " pages, less than " + minAveragePageWords + ")");
		}
		
		//	attach non-standard PDF fonts to document
		spm.setStep("Storing custom fonts");
		for (Iterator okit = objects.keySet().iterator(); okit.hasNext();) {
			Object obj = objects.get(okit.next());
			if (!(obj instanceof PdfFont))
				continue;
			PdfFont pFont = ((PdfFont) obj);
			spm.setInfo("Doing font " + pFont.name);
			int[] charCodes = pFont.getUsedCharCodes();
			if (charCodes.length == 0) {
				spm.setInfo(" ==> empty font");
				continue;
			}
			spm.setInfo(" - got " + charCodes.length + " characters");
			ImFont imFont = new ImFont(doc, pFont.name, pFont.bold, pFont.italics, true); // TODO observe if serif or not
			for (int c = 0; c < charCodes.length; c++) {
				String charStr = pFont.getUnicode(charCodes[c]);
				BufferedImage charImage = pFont.getCharImage(charCodes[c]);
				if (charImage != null)
					imFont.addCharacter(charCodes[c], charStr, ImFont.scaleCharImage(charImage));
			}
			if (imFont.getCharacterCount() == 0)
				spm.setInfo(" ==> no custom characters");
			else {
				doc.addFont(imFont);
				spm.setInfo(" ==> font stored");
			}
		}
		
		//	put PDF words into images
		spm.setStep("Generating page images");
		spm.setBaseProgress(40);
		spm.setProgress(0);
		spm.setMaxProgress(70);
		final BufferedImage[] pageImages = new BufferedImage[pageTree.getNumberOfPages()];
		final String[] imageNames = new String[pageTree.getNumberOfPages()];
		final int[] imageDPIs = new int[pageTree.getNumberOfPages()];
		pf = new ParallelFor() {
			public void doFor(int p) throws Exception {
				
				//	update status display (might be inaccurate, but better than lock escalation)
				spm.setInfo("Extracting image of page " + p + " of " + pageTree.getNumberOfPages());
				spm.setProgress((p * 100) / pageTree.getNumberOfPages());
				
				//	test if we have non-horizontal blocks, and measure their size
				int bubLeft = Integer.MAX_VALUE;
				int bubRight = Integer.MIN_VALUE;
				int bubTop = Integer.MAX_VALUE;
				int bubBottom = Integer.MIN_VALUE;
				HashSet buWords = new HashSet(); 
				int tdbLeft = Integer.MAX_VALUE;
				int tdbRight = Integer.MIN_VALUE;
				int tdbTop = Integer.MAX_VALUE;
				int tdbBottom = Integer.MIN_VALUE;
				HashSet tdWords = new HashSet(); 
				
				//	clean all own words
				for (int w = 0; w < pWords[p].length; w++) {
					Rectangle2D wb = pWords[p][w].bounds;
					
					//	convert bounds, as PDF Y coordinate is bottom-up, whereas Java, JavaScript, etc. Y coordinate is top-down
					int left = Math.round(((float) (wb.getMinX() - pageBoxes[p].getMinX())) * magnification);
					int right = Math.round(((float) (wb.getMaxX() - pageBoxes[p].getMinX()))  * magnification);
					int top = Math.round((pageBoxes[p].height - ((float) (wb.getMaxY() - ((2 * pageBoxes[p].getMinY()) - pageBoxes[p].getMaxY())))) * magnification);
					int bottom = Math.round((pageBoxes[p].height - ((float) (wb.getMinY() - ((2 * pageBoxes[p].getMinY()) - pageBoxes[p].getMaxY())))) * magnification);
					
					//	test for vertical words
					if (pWords[p][w].fontDirection == PWord.BOTTOM_UP_FONT_DIRECTION) {
						bubLeft = Math.min(bubLeft, left);
						bubRight = Math.max(bubRight, right);
						bubTop = Math.min(bubTop, top);
						bubBottom = Math.max(bubBottom, bottom);
						buWords.add(pWords[p][w]);
					}
					else if (pWords[p][w].fontDirection == PWord.TOP_DOWN_FONT_DIRECTION) {
						tdbLeft = Math.min(tdbLeft, left);
						tdbRight = Math.max(tdbRight, right);
						tdbTop = Math.min(tdbTop, top);
						tdbBottom = Math.max(tdbBottom, bottom);
						tdWords.add(pWords[p][w]);
					}
				}
				
				//	page image generated before
				if (imageStore.isPageImageAvailable(doc.docId, p)) {
					PageImage pi = imageStore.getPageImage(doc.docId, p);
					pageImages[p] = pi.image;
					imageNames[p] = PageImage.getPageImageName(doc.docId, p);
					imageDPIs[p] = pi.currentDpi;
					spm.setInfo(" --> loaded page image generated earlier, sized " + pageImages[p].getWidth() + " x " + pageImages[p].getHeight());
					
					//	we do have a significant bottom-up block, flip its words (image is already extended here, so test against scales page bounds)
					if ((bubLeft < bubRight) && (bubTop < bubBottom) && (bubLeft < ((pageBoxes[p].getWidth() * magnification) / 3)) && (bubRight > ((pageBoxes[p].getWidth() * magnification * 2) / 3)) && (bubTop < ((pageBoxes[p].getHeight() * magnification) / 3)) && (bubBottom > ((pageBoxes[p].getHeight() * magnification * 2) / 3))) {
						System.out.println("Got bottom-up block with " + buWords.size() + " words to flip");
						flipBlockWords(pageBoxes[p], pWords[p], buWords, PWord.BOTTOM_UP_FONT_DIRECTION);
					}
					
					//	we do have a significant top-down block, flip its words (image is already extended here, so test against scales page bounds)
					else if ((tdbLeft < tdbRight) && (tdbTop < tdbBottom) && (tdbLeft < ((pageBoxes[p].getWidth() * magnification) / 3)) && (tdbRight > ((pageBoxes[p].getWidth() * magnification * 2) / 3)) && (tdbTop < ((pageBoxes[p].getHeight() * magnification) / 3)) && (tdbBottom > ((pageBoxes[p].getHeight() * magnification * 2) / 3))) {
						System.out.println("Got top-down block with " + tdWords.size() + " words to flip");
						flipBlockWords(pageBoxes[p], pWords[p], tdWords, PWord.TOP_DOWN_FONT_DIRECTION);
					}
				}
				
				//	generate page image
				else {
					spm.setInfo(" - generating page image");
					synchronized (pdfDoc) {
						pageImages[p] = ((BufferedImage) pdfDoc.getPageImage(p, GraphicsRenderingHints.SCREEN, Page.BOUNDARY_CROPBOX, 0, magnification));
					}
					if (pageImages[p] == null) {
						spm.setInfo(" --> page image generation failed");
						throw new RuntimeException("Could not generate image for page " + p);
					}
					spm.setInfo(" - got page image sized " + pageImages[p].getWidth() + " x " + pageImages[p].getHeight());
					
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
					
					//	clean all own words
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
					
					//	we do have a significant bottom-up block, flip it
					if ((bubLeft < bubRight) && (bubTop < bubBottom) && (bubLeft < (pageImages[p].getWidth() / 3)) && (bubRight > ((pageImages[p].getWidth() * 2) / 3)) && (bubTop < (pageImages[p].getHeight() / 3)) && (bubBottom > ((pageImages[p].getHeight() * 2) / 3))) {
						System.out.println("Got bottom-up block with " + buWords.size() + " words to flip");
						pageImages[p] = flipBlockImage(pageImages[p], pageBoxes[p], bubLeft, bubRight, bubTop, bubBottom, pWords[p], PWord.BOTTOM_UP_FONT_DIRECTION, magnification);
						rg = pageImages[p].createGraphics();
						flipBlockWords(pageBoxes[p], pWords[p], buWords, PWord.BOTTOM_UP_FONT_DIRECTION);
					}
					
					//	we do have a significant top-down block, flip it
					else if ((tdbLeft < tdbRight) && (tdbTop < tdbBottom) && (tdbLeft < (pageImages[p].getWidth() / 3)) && (tdbRight > ((pageImages[p].getWidth() * 2) / 3)) && (tdbTop < (pageImages[p].getHeight() / 3)) && (tdbBottom > ((pageImages[p].getHeight() * 2) / 3))) {
						System.out.println("Got top-down block with " + tdWords.size() + " words to flip");
						pageImages[p] = flipBlockImage(pageImages[p], pageBoxes[p], tdbLeft, tdbRight, tdbTop, tdbBottom, pWords[p], PWord.TOP_DOWN_FONT_DIRECTION, magnification);
						rg = pageImages[p].createGraphics();
						flipBlockWords(pageBoxes[p], pWords[p], buWords, PWord.TOP_DOWN_FONT_DIRECTION);
					}
					
					//	paint own words
					for (int w = 0; w < pWords[p].length; w++) {
						Rectangle2D wb = pWords[p][w].bounds;
						
						//	convert bounds, as PDF Y coordinate is bottom-up, whereas Java, JavaScript, etc. Y coordinate is top-down
						int left = Math.round(((float) (wb.getMinX() - pageBoxes[p].getMinX())) * magnification);
						int right = Math.round(((float) (wb.getMaxX() - pageBoxes[p].getMinX()))  * magnification);
//						int top = Math.round((pageBox.height - ((float) (wb.getMaxY() - ((2 * pageBox.getMinY()) - pageBox.getMaxY())))) * magnification);
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
						AffineTransform at = rg.getTransform();
						rg.translate(left, 0);
						if (hScale < 1)
							rg.scale(hScale, 1);
						rg.drawString(pWords[p][w].str, ((float) - wtl.getBounds().getMinX()), (bottom - (pWords[p][w].font.hasDescent ? Math.round(wlm.getDescent()) : 0)));
						rg.setTransform(at);
					}
					
					//	cannot cut margins here because otherwise PDF word assignment gets broken
					synchronized (imageStore) {
						imageNames[p] = imageStore.storePageImage(doc.docId, p, pageImages[p], textPdfPageImageDpi);
					}
					if (imageNames[p] == null) {
						spm.setInfo(" --> page image storage failed");
						throw new RuntimeException("Could not store image of page " + p);
					}
					else {
						spm.setInfo(" --> page image stored");
						imageDPIs[p] = textPdfPageImageDpi;
					}
				}
			}
		};
		ParallelJobRunner.runParallelFor(pf, ((PdfExtractorTest.aimAtPage < 0) ? 0 : PdfExtractorTest.aimAtPage), ((PdfExtractorTest.aimAtPage < 0) ? pageTree.getNumberOfPages() : (PdfExtractorTest.aimAtPage + 1)), (this.useMultipleCores ? -1 : 1));
		spm.setProgress(100);
		
		//	check errors
		this.checkException(pf);
		
		//	analyze page structure
		spm.setStep("Analyzing page structure");
		spm.setBaseProgress(70);
		spm.setProgress(0);
		spm.setMaxProgress(98);
		final ImPage[] pages = new ImPage[pageTree.getNumberOfPages()];
		if (PdfExtractorTest.aimAtPage != -1) {
			for (int p = 0; p < pages.length; p++)
				if (p != PdfExtractorTest.aimAtPage) {
					BoundingBox pageBounds = new BoundingBox(0, 32, 0, 44); // have to use these numbers to prevent 0 or 1 step in page word index
					ImPage page = new ImPage(doc, p, pageBounds);
				}
		}
		pf = new ParallelFor() {
			public void doFor(int p) throws Exception {
				
				//	generate page (we have to synchronize this, as it adds to main document)
				synchronized (doc) {
					pages[p] = new ImPage(doc, p, new BoundingBox(0, pageImages[p].getWidth(), 0, pageImages[p].getHeight()));
				}
				
				spm.setInfo("Analyzing structure of page " + p + " of " + pageTree.getNumberOfPages());
				spm.setProgress((p * 100) / pageTree.getNumberOfPages());
				
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
					
					//	add font char ID string
					StringBuffer charCodesHex = new StringBuffer();
					for (int c = 0; c < pWords[p][w].charCodes.length(); c++) {
						String charCodeHex = Integer.toString((((int) pWords[p][w].charCodes.charAt(c)) & 255), 16).toUpperCase();
						if (charCodeHex.length() < 2)
							charCodesHex.append("0");
						charCodesHex.append(charCodeHex);
					}
					word.setAttribute(ImFont.CHARACTER_CODE_STRING_ATTRIBUTE, charCodesHex.toString());
					
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
				Region pageRootRegion = PageImageAnalysis.getPageRegion(api, imageDPIs[p], false, spm);
				
				//	add page content to document
				addRegionStructure(pages[p], null, pageRootRegion, imageDPIs[p], wordsByBoxes, spm);
				
				//	catch empty page
				if (pages[p].getWords().length == 0)
					return;
				
				//	adjust bounding boxes
				shrinkToChildren(pages[p], LINE_ANNOTATION_TYPE, WORD_ANNOTATION_TYPE, -1);
				ImRegion[] blockRemainders = shrinkToChildren(pages[p], BLOCK_ANNOTATION_TYPE, LINE_ANNOTATION_TYPE, imageDPIs[p]);
				
				//	preserve image blocks that were attached to text blocks
				for (int r = 0; r < blockRemainders.length; r++) {
					System.out.println("Got block remainder at " + blockRemainders[r].bounds);
					ImagePartRectangle brBox = pageRootRegion.bounds.getSubRectangle(blockRemainders[r].bounds.left, blockRemainders[r].bounds.right, blockRemainders[r].bounds.top, blockRemainders[r].bounds.bottom);
					brBox = Imaging.narrowLeftAndRight(brBox);
					brBox = Imaging.narrowTopAndBottom(brBox);
					System.out.println(" - narrowed to " + brBox.getId());
					if ((imageDPIs[p] <= brBox.getWidth()) && (imageDPIs[p] <= brBox.getHeight())) {
						ImRegion blockRemainder = new ImRegion(pages[p], new BoundingBox(brBox.getLeftCol(), brBox.getRightCol(), brBox.getTopRow(), brBox.getBottomRow()), ImRegion.IMAGE_TYPE);
						System.out.println(" ==> added as image at " + blockRemainder.bounds);
					}
				}
				
				//	do structure analysis
				PageAnalysis.splitIntoParagraphs(pages[p].getRegions(BLOCK_ANNOTATION_TYPE), imageDPIs[p], null);
				spm.setInfo(" - paragraphs done");
				PageAnalysis.computeColumnLayout(pages[p].getRegions(COLUMN_ANNOTATION_TYPE), imageDPIs[p]);
				spm.setInfo(" - layout analysis done");
				
				//	clean up regions and mark images
				cleanUpRegions(pages[p]);
				
				//	finally ...
				spm.setInfo(" - page done");
			}
		};
		ParallelJobRunner.runParallelFor(pf, ((PdfExtractorTest.aimAtPage < 0) ? 0 : PdfExtractorTest.aimAtPage), ((PdfExtractorTest.aimAtPage < 0) ? pageTree.getNumberOfPages() : (PdfExtractorTest.aimAtPage + 1)), (this.useMultipleCores ? -1 : 1));
		spm.setProgress(100);
		
		//	check errors
		this.checkException(pf);
		
		//	finalize text stream structure
		spm.setStep("Analyzing text stream structure");
		spm.setBaseProgress(98);
		spm.setProgress(0);
		spm.setMaxProgress(100);
		ImWord lastWord = null;
		for (int p = 0; p < pages.length; p++) {
			spm.setProgress((p * 100) / pages.length);
			if ((PdfExtractorTest.aimAtPage == -1) || (p == PdfExtractorTest.aimAtPage))
				lastWord = this.addTextStreamStructure(pages[p], lastWord);
		}
		spm.setProgress(100);
		spm.setInfo(" - word sequence analysis done");
		
		//	finally ...
		return doc;
	}
	
	private static boolean areWordsMergeable(PWord lpWord, TokenSequence lpWordTokens, PWord pWord, TokenSequence pWordTokens, double maxMergeMargin, Tokenizer tokenizer) {
		
		//	do not join words with altering bold/italic properties or font sizes, they differ for a reason
		if ((lpWord.bold != pWord.bold) || (lpWord.italics != pWord.italics) || (lpWord.fontSize != pWord.fontSize) || (lpWord.fontDirection != pWord.fontDirection)) {
			System.out.println(" --> font size, direction, or style mismatch");
			return false;
		}
		
		//	left-right words
		if (pWord.fontDirection == PWord.LEFT_RIGHT_FONT_DIRECTION) {
			
			//	not in same line
			if ((pWord.bounds.getMaxY() <= lpWord.bounds.getMinY()) || (lpWord.bounds.getMaxY() <= pWord.bounds.getMinY())) {
				System.out.println(" --> different lines");
				return false;
			}
			
			//	too far a gap
			if ((pWord.bounds.getMinX() < lpWord.bounds.getMinX()) || ((pWord.bounds.getMinX() - lpWord.bounds.getMaxX()) > maxMergeMargin)) {
				System.out.println(" --> too far apart");
				return false;
			}
		}
		
		//	bottom-up words
		else if (pWord.fontDirection == PWord.BOTTOM_UP_FONT_DIRECTION) {
			
			//	not in same line
			if ((pWord.bounds.getMinX() >= lpWord.bounds.getMaxX()) || (lpWord.bounds.getMinX() >= pWord.bounds.getMaxX())) {
				System.out.println(" --> different lines");
				return false;
			}
			
			//	too far a gap
			if ((pWord.bounds.getMinY() < lpWord.bounds.getMinY()) || ((pWord.bounds.getMinY() - lpWord.bounds.getMaxY()) > maxMergeMargin)) {
				System.out.println(" --> too far apart");
				return false;
			}
		}
		
		//	top-down words
		else if (pWord.fontDirection == PWord.TOP_DOWN_FONT_DIRECTION) {
			
			//	not in same line
			if ((pWord.bounds.getMaxX() <= lpWord.bounds.getMinX()) || (lpWord.bounds.getMaxX() <= pWord.bounds.getMinX())) {
				System.out.println(" --> different lines");
				return false;
			}
			
			//	too far a gap
			if ((pWord.bounds.getMaxY() > lpWord.bounds.getMaxY()) || ((lpWord.bounds.getMinY() - pWord.bounds.getMaxY()) > maxMergeMargin)) {
				System.out.println(" --> too far apart");
				return false;
			}
		}
		
		//	do NOT merge words that tokenize apart
		TokenSequence mpWordTokens = tokenizer.tokenize(lpWord.str + pWord.str);
		if ((lpWordTokens.size() + pWordTokens.size()) <= mpWordTokens.size()) {
			System.out.println(" --> tokenization mismatch");
			return false;
		}
		
		//	not counter indications found
		return true;
	}
	
	private static BufferedImage flipBlockImage(BufferedImage pageImage, Rectangle2D.Float pageBox, int fbLeft, int fbRight, int fbTop, int fbBottom, PWord[] pWords, int fbWordFontDirection, float magnification) {
		System.out.println("Flipping block " + fbLeft + "-" + fbRight + " x " + fbTop + "-" + fbBottom);
		
		//	compute minimum page margin in IM space
		int pageLeft = 0;
		int pageRight = Math.round(pageBox.width * magnification);
		int pageTop = 0;
		int pageBottom = Math.round(pageBox.height * magnification);
		int minPageMargin = Integer.MAX_VALUE;
		for (int w = 0; w < pWords.length; w++) {
			Rectangle2D wb = pWords[w].bounds;
			
			//	convert bounds, as PDF Y coordinate is bottom-up, whereas Java, JavaScript, etc. Y coordinate is top-down
			int left = Math.round(((float) (wb.getMinX() - pageBox.getMinX())) * magnification);
			int right = Math.round(((float) (wb.getMaxX() - pageBox.getMinX()))  * magnification);
			int top = Math.round((pageBox.height - ((float) (wb.getMaxY() - ((2 * pageBox.getMinY()) - pageBox.getMaxY())))) * magnification);
			int bottom = Math.round((pageBox.height - ((float) (wb.getMinY() - ((2 * pageBox.getMinY()) - pageBox.getMaxY())))) * magnification);
			
			//	adjust minimum
			minPageMargin = Math.min(minPageMargin, (left - pageLeft));
			minPageMargin = Math.min(minPageMargin, (pageRight - right));
			minPageMargin = Math.min(minPageMargin, (top - pageTop));
			minPageMargin = Math.min(minPageMargin, (pageBottom - bottom));
		}
		System.out.println(" - page is " + pageLeft + "-" + pageRight + " x " + pageTop + "-" + pageBottom);
		System.out.println(" - min margin is " + minPageMargin);
		
		//	compute center point of to-flip block and right shift in IM space
		int fbCenterX = ((fbLeft + fbRight) / 2);
		int fbCenterY = ((fbTop + fbBottom) / 2);
		System.out.println(" - flip block center is " + fbCenterX + "," + fbCenterY);
		int fbFlippedLeft = (fbCenterX - ((fbBottom - fbTop) / 2));
		System.out.println(" - flipped block left will be " + fbFlippedLeft);
		int fbRightShift = Math.max(0, (minPageMargin - fbFlippedLeft));
		System.out.println(" --> right shift is " + fbRightShift);
		
		//	create new page image
		int fbPageImageWidth = Math.max(pageImage.getWidth(), ((fbBottom - fbTop) + (2 * minPageMargin)));
		BufferedImage fbPageImage = new BufferedImage(fbPageImageWidth, pageImage.getHeight(), pageImage.getType());
		Graphics2D fbpig = fbPageImage.createGraphics();
		fbpig.setColor(Color.WHITE);
		fbpig.fillRect(0, 0, fbPageImage.getWidth(), fbPageImage.getHeight()); // paint background
		fbpig.drawImage(pageImage, 0, 0, null); // transfer original page image
		fbpig.fillRect(fbLeft, fbTop, (fbRight - fbLeft), (fbBottom - fbTop)); // erase to-flip block
		fbpig.translate((fbCenterX - ((fbBottom - fbTop) / 2)), (fbCenterY - ((fbRight - fbLeft) / 2))); // shift to top-left corner of flipped block 
		fbpig.translate(fbRightShift, 0); // add right shift to keep flipped block out of page margin
		if (fbWordFontDirection == PWord.BOTTOM_UP_FONT_DIRECTION)
			fbpig.translate((fbBottom - fbTop), 0); // compensate for top-left corner of to-flip block being top-right in flipped block
		else if (fbWordFontDirection == PWord.TOP_DOWN_FONT_DIRECTION)
			fbpig.translate(0, (fbRight - fbLeft)); // compensate for top-left corner of to-flip block being bottom-left in flipped block
		fbpig.rotate(((Math.PI / 2) * ((fbWordFontDirection == PWord.BOTTOM_UP_FONT_DIRECTION) ? 1 : -1)));
		fbpig.drawImage(pageImage.getSubimage(fbLeft, fbTop, (fbRight - fbLeft), (fbBottom - fbTop)), 0, 0, null);
		fbpig.dispose();
		
		//	finally ...
		return fbPageImage;
	}
	
	private static void flipBlockWords(Rectangle2D.Float pageBox, PWord[] pWords, HashSet fbWords, int fbWordFontDirection) {
		System.out.println("Flipping block words ...");
		
		//	compute bounds of to-flip block and minimum page margin in PDF space (have to invert Y axis for transformation, as transformation works in Y coordinates increasing top-down)
		double pFbLeft = Integer.MAX_VALUE;
		double pFbRight = Integer.MIN_VALUE;
		double pFbTop = Integer.MAX_VALUE;
		double pFbBottom = Integer.MIN_VALUE;
		double pPageLeft = pageBox.getMinX();
		double pPageRight = pageBox.getMaxX();
		double pPageTop = 0;
		double pPageBottom = pageBox.height;
		double pMinPageMargin = Integer.MAX_VALUE;
		for (int w = 0; w < pWords.length; w++) {
			Rectangle2D wb = pWords[w].bounds;
			
			if (fbWords.contains(pWords[w])) {
				pFbLeft = Math.min(pFbLeft, wb.getMinX());
				pFbRight = Math.max(pFbRight, wb.getMaxX());
				pFbTop = Math.min(pFbTop, (pageBox.height - wb.getMaxY()));
				pFbBottom = Math.max(pFbBottom, (pageBox.height - wb.getMinY()));
			}
			
			pMinPageMargin = Math.min(pMinPageMargin, (wb.getMinX() - pPageLeft));
			pMinPageMargin = Math.min(pMinPageMargin, (pPageRight - wb.getMaxX()));
			pMinPageMargin = Math.min(pMinPageMargin, ((pageBox.height - wb.getMaxY())) - pPageTop);
			pMinPageMargin = Math.min(pMinPageMargin, (pPageBottom - (pageBox.height - wb.getMinY())));
		}
		System.out.println(" - adjusted PDF page is " + pPageLeft + "-" + pPageRight + " x " + pPageTop + "-" + pPageBottom);
		System.out.println(" - to-flip block is " + pFbLeft + "-" + pFbRight + " x " + pFbTop + "-" + pFbBottom);
		System.out.println(" - min margin is " + pMinPageMargin);
		
		//	compute center point of to-flip block and right shift in PDF space (have to invert Y axis for transformation, as transformation works in Y coordinates increasing top-down)
		double pFbCenterX = ((pFbLeft + pFbRight) / 2);
		double pFbCenterY = ((pFbTop + pFbBottom) / 2);
		System.out.println(" - flip block center is " + pFbCenterX + "," + pFbCenterY);
		double pFbFlippedLeft = (pFbCenterX - ((pFbBottom - pFbTop) / 2));
		System.out.println(" - flipped block left will be " + pFbFlippedLeft);
		double pFbRightShift = Math.max(0, (pMinPageMargin - pFbFlippedLeft));
		System.out.println(" --> right shift is " + pFbRightShift);
		
		//	create PDF space transformation for flipping words (have to invert Y axis for transformation, as transformation works in Y coordinates increasing top-down)
		AffineTransform pAt = new AffineTransform();
		pAt.translate((pFbCenterX - ((pFbBottom - pFbTop) / 2)), (pFbCenterY - ((pFbRight - pFbLeft) / 2))); // shift to top-left corner of flipped block
		pAt.translate(pFbRightShift, 0); // add right shift to keep flipped block out of page margin
		if (fbWordFontDirection == PWord.BOTTOM_UP_FONT_DIRECTION)
			pAt.translate((pFbBottom - pFbTop), 0); // compensate for top-left corner of to-flip block being top-right in flipped block
		else if (fbWordFontDirection == PWord.TOP_DOWN_FONT_DIRECTION)
			pAt.translate(0, (pFbRight - pFbLeft)); // compensate for top-left corner of to-flip block being bottom-left in flipped block
		pAt.rotate(((Math.PI / 2) * ((fbWordFontDirection == PWord.BOTTOM_UP_FONT_DIRECTION) ? 1 : -1)));
		
		//	flip words (have to invert Y axis for transformation, as transformation works in Y coordinates increasing top-down)
		System.out.println(" - flipping words:");
		for (int w = 0; w < pWords.length; w++) {
			if (!fbWords.contains(pWords[w]))
				continue;
			Rectangle2D wb = pWords[w].bounds;
			Point2D wbp = new Point2D.Float(((float) (wb.getMinX() - pFbLeft)), ((float) ((pageBox.height - wb.getMaxY()) - pFbTop))); // this one has to be relative to to-flip block to work the same way as the image
			Point2D fWbp = pAt.transform(wbp, null);
			Rectangle2D fWb;
			if (fbWordFontDirection == PWord.BOTTOM_UP_FONT_DIRECTION)
				fWb = new Rectangle2D.Float(
						((float) (fWbp.getX() - wb.getHeight())),
						((float) ((pageBox.height - fWbp.getY()) - wb.getWidth())),
						((float) wb.getHeight()),
						((float) wb.getWidth())
					);
			else if (fbWordFontDirection == PWord.TOP_DOWN_FONT_DIRECTION)
				fWb = new Rectangle2D.Float(
						((float) fWbp.getX()),
						((float) ((pageBox.height - fWbp.getY()))),
						((float) wb.getHeight()),
						((float) wb.getWidth())
					);
			else continue;
			System.out.println("   - '" + pWords[w] + "' flipped from " + wb + " to " + fWb);
			pWords[w] = new PWord(pWords[w].charCodes, pWords[w].str, fWb, pWords[w].fontSize, PWord.LEFT_RIGHT_FONT_DIRECTION, pWords[w].font);
		}
	}
	
	private static PWord shrinkWordBounds(Graphics2D mg, PWord pWord) {
		Rectangle2D wb = pWord.bounds;
		System.out.println("Measuring word " + pWord.str + " at " + wb);
		
		//	left-right word
		if (pWord.fontDirection == PWord.LEFT_RIGHT_FONT_DIRECTION) {
			
			//	NO ADJUSTMENT TO JAVA COORDINATES HERE, WE'RE TRANSFORMING ALL WORDS LATER ON
			float left = ((float) wb.getMinX());
			float right = ((float) wb.getMaxX());
			float top = ((float) wb.getMaxY());
			float bottom = ((float) wb.getMinY());
			
			//	compute word baseline from font baseline and word box height
			float fontHeight = (Math.abs(pWord.font.ascent) + Math.abs(pWord.font.descent));
			float baseline = (top - (((top - bottom) * pWord.font.ascent) / fontHeight));
			
			//	prepare font for test rendering
			int fontStyle = Font.PLAIN;
			if (pWord.bold)
				fontStyle = (fontStyle | Font.BOLD);
			if (pWord.italics)
				fontStyle = (fontStyle | Font.ITALIC);
			Font mf = getFont(pWord.font.name, fontStyle, Math.round(((float) pWord.fontSize) * 1));
			mg.setFont(mf);
			
			//	adjust word size and vertical position
			TextLayout wtl = new TextLayout((pWord.str + "IpHq"), mf, mg.getFontRenderContext());
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
				Rectangle2D.Float pwBox = new Rectangle2D.Float(left, adjustedBottom, (right - left), (adjustedTop - adjustedBottom));
				pWord = new PWord(pWord.charCodes, pWord.str, pwBox, pWord.fontSize, pWord.fontDirection, pWord.font);
				System.out.println(" ==> adjusted bounding box to " + pWord.bounds);
			}
		}
		
		//	bottom-up word
		else if (pWord.fontDirection == PWord.BOTTOM_UP_FONT_DIRECTION) {
			
			//	NO ADJUSTMENT TO JAVA COORDINATES HERE, WE'RE TRANSFORMING ALL WORDS LATER ON
			float left = ((float) wb.getMinY());
			float right = ((float) wb.getMaxY());
			float top = ((float) wb.getMinX());
			float bottom = ((float) wb.getMaxX());
			
			//	compute word baseline from font baseline and word box height
			float fontHeight = (Math.abs(pWord.font.ascent) + Math.abs(pWord.font.descent));
			float baseline = (top + (((bottom - top) * pWord.font.ascent) / fontHeight));
			
			//	prepare font for test rendering
			int fontStyle = Font.PLAIN;
			if (pWord.bold)
				fontStyle = (fontStyle | Font.BOLD);
			if (pWord.italics)
				fontStyle = (fontStyle | Font.ITALIC);
			Font mf = getFont(pWord.font.name, fontStyle, Math.round(((float) pWord.fontSize) * 1));
			mg.setFont(mf);
			
			//	adjust word size and vertical position
			TextLayout wtl = new TextLayout((pWord.str + "IpHq"), mf, mg.getFontRenderContext());
			System.out.println(" - word rendering box is " + wtl.getBounds());
			float boundingBoxX = -((float) wtl.getBounds().getY());
			float boundingBoxHeight = ((float) wtl.getBounds().getHeight());
			System.out.println(" - top is " + top + ", bottom is " + bottom + ", baseline is " + baseline + ", ascent is " + wtl.getAscent() + ", descent is " + wtl.getDescent());
			
			//	if computed bounding box smaller (lower top, higher bottom) than one from PDF, shrink it
			float adjustedTop = (baseline - boundingBoxX);
			float adjustedBottom = (adjustedTop + boundingBoxHeight);
			System.out.println(" - word box y is " + boundingBoxX + ", word box height is " + boundingBoxHeight);
			System.out.println(" - adjusted top is " + adjustedTop + ", adjusted bottom is " + adjustedBottom);
			if (((adjustedTop - top) > 0.25) || ((bottom - adjustedBottom) > 0.25)) {
				Rectangle2D.Float pwBox = new Rectangle2D.Float(adjustedTop, left, (adjustedBottom - adjustedTop), (right - left));
				pWord = new PWord(pWord.charCodes, pWord.str, pwBox, pWord.fontSize, pWord.fontDirection, pWord.font);
				System.out.println(" ==> adjusted bounding box to " + pWord.bounds);
			}
		}
		
		//	top-down word
		else if (pWord.fontDirection == PWord.TOP_DOWN_FONT_DIRECTION) {
			
			//	NO ADJUSTMENT TO JAVA COORDINATES HERE, WE'RE TRANSFORMING ALL WORDS LATER ON
			float left = ((float) wb.getMaxY());
			float right = ((float) wb.getMinY());
			float top = ((float) wb.getMaxX());
			float bottom = ((float) wb.getMinX());
			
			//	compute word baseline from font baseline and word box height
			float fontHeight = (Math.abs(pWord.font.ascent) + Math.abs(pWord.font.descent));
			float baseline = (top - (((top - bottom) * pWord.font.ascent) / fontHeight));
			
			//	prepare font for test rendering
			int fontStyle = Font.PLAIN;
			if (pWord.bold)
				fontStyle = (fontStyle | Font.BOLD);
			if (pWord.italics)
				fontStyle = (fontStyle | Font.ITALIC);
			Font mf = getFont(pWord.font.name, fontStyle, Math.round(((float) pWord.fontSize) * 1));
			mg.setFont(mf);
			
			//	adjust word size and vertical position
			TextLayout wtl = new TextLayout((pWord.str + "IpHq"), mf, mg.getFontRenderContext());
			System.out.println(" - word rendering box is " + wtl.getBounds());
			float boundingBoxX = ((float) wtl.getBounds().getY());
			float boundingBoxHeight = ((float) wtl.getBounds().getHeight());
			System.out.println(" - top is " + top + ", bottom is " + bottom + ", baseline is " + baseline + ", ascent is " + wtl.getAscent() + ", descent is " + wtl.getDescent());
			
			//	if computed bounding box smaller (lower top, higher bottom) than one from PDF, shrink it
			float adjustedTop = (baseline - boundingBoxX);
			float adjustedBottom = (adjustedTop - boundingBoxHeight);
			System.out.println(" - word box y is " + boundingBoxX + ", word box height is " + boundingBoxHeight);
			System.out.println(" - adjusted top is " + adjustedTop + ", adjusted bottom is " + adjustedBottom);
			if (((top - adjustedTop) > 0.25) || ((adjustedBottom - bottom) > 0.25)) {
				Rectangle2D.Float pwBox = new Rectangle2D.Float(adjustedBottom, right, (adjustedTop - adjustedBottom), (left - right));
				pWord = new PWord(pWord.charCodes, pWord.str, pwBox, pWord.fontSize, pWord.fontDirection, pWord.font);
				System.out.println(" ==> adjusted bounding box to " + pWord.bounds);
			}
		}
		
		//	finally ...
		return pWord;
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
	
	private ImWord addRegionStructure(ImPage page, ImWord lastWord, Region theRegion, int dpi, Map wordsByBoxes, ProgressMonitor pm) {
		
		//	mark region
		ImRegion region = new ImRegion(page, theRegion.getBoundingBox());
		
		//	image block ==> set type and we're done
		if (theRegion.isImage()) {
			region.setType(ImRegion.IMAGE_TYPE);
			return lastWord;
		}
		
		//	set type
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
	
	private ImWord addTextBlockStructure(ImPage page, ImWord lastWord, Block theBlock, int dpi, Map pWordsByBoxes, ProgressMonitor pm) {
		
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
	
	private ImWord addLineStructure(ImPage page, ImWord lastWord, ImagePartRectangle blockBounds, Line[] lines, Map wordsByBoxes, ProgressMonitor pm) {
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
	
	private static ImRegion[] shrinkToChildren(ImPage page, String pType, String cType, int retainThreshold) {
		ImRegion[] pRegions = page.getRegions(pType);
		LinkedList pRegionRemainders = new LinkedList();
		for (int p = 0; p < pRegions.length; p++) {
			int pLeft = pRegions[p].bounds.right;
			int pRight = pRegions[p].bounds.left;
			int pTop = pRegions[p].bounds.bottom;
			int pBottom = pRegions[p].bounds.top;
			
			//	collect convex hull of children
			ImRegion[] cRegions = (WORD_ANNOTATION_TYPE.equals(cType) ? page.getWordsInside(pRegions[p].bounds) : page.getRegionsInside(pRegions[p].bounds, false));
			for (int c = 0; c < cRegions.length; c++) {
				if (!cType.equals(cRegions[c].getType()))
					continue;
				pLeft = Math.min(pLeft, cRegions[c].bounds.left);
				pRight = Math.max(pRight, cRegions[c].bounds.right);
				pTop = Math.min(pTop, cRegions[c].bounds.top);
				pBottom = Math.max(pBottom, cRegions[c].bounds.bottom);
			}
			
			//	nothing to do here
			if ((pRight <= pLeft) || (pBottom <= pTop) || ((pLeft <= pRegions[p].bounds.left) && (pRegions[p].bounds.right <= pRight) && (pTop <= pRegions[p].bounds.top) && (pRegions[p].bounds.bottom <= pBottom)))
				continue;
			
			//	collect remainders if requested and region sufficiently large
			if ((0 < retainThreshold) && (retainThreshold < (pRegions[p].bounds.right - pRegions[p].bounds.left)) && (retainThreshold < (pRegions[p].bounds.bottom - pRegions[p].bounds.top))) {
				if (retainThreshold < (pLeft - pRegions[p].bounds.left))
					pRegionRemainders.add(new ImRegion(page.getDocument(), page.pageId, new BoundingBox(pRegions[p].bounds.left, pLeft, pRegions[p].bounds.top, pRegions[p].bounds.bottom), pType));
				if (retainThreshold < (pRegions[p].bounds.right - pRight))
					pRegionRemainders.add(new ImRegion(page.getDocument(), page.pageId, new BoundingBox(pRight, pRegions[p].bounds.right, pRegions[p].bounds.top, pRegions[p].bounds.bottom), pType));
				if (retainThreshold < (pTop - pRegions[p].bounds.top))
					pRegionRemainders.add(new ImRegion(page.getDocument(), page.pageId, new BoundingBox(pRegions[p].bounds.left, pRegions[p].bounds.right, pRegions[p].bounds.top, pTop), pType));
				if (retainThreshold < (pRegions[p].bounds.bottom - pBottom))
					pRegionRemainders.add(new ImRegion(page.getDocument(), page.pageId, new BoundingBox(pRegions[p].bounds.left, pRegions[p].bounds.right, pBottom, pRegions[p].bounds.bottom), pType));
			}
			
			//	shrink to convex hull of children
			BoundingBox pBox = new BoundingBox(pLeft, pRight, pTop, pBottom);
			ImRegion pRegion = new ImRegion(page, pBox, pType);
			pRegion.copyAttributes(pRegions[p]);
			page.getPage().removeRegion(pRegions[p]);
			pRegions[p] = pRegion;
		}
		
		//	return remainders
		return ((ImRegion[]) pRegionRemainders.toArray(new ImRegion[pRegionRemainders.size()]));
	}
	
	private static void cleanUpRegions(ImPage page) {
		ImRegion[] regions = page.getRegions();
		
		//	get empty regions
		ArrayList emptyRegions = new ArrayList();
		for (int r = 0; r < regions.length; r++) {
			ImWord[] regionWords = regions[r].getWords();
			if (regionWords.length == 0)
				emptyRegions.add(regions[r]);
		}
		
		//	sort by size
		Collections.sort(emptyRegions, regionSizeOrder);
		
		//	remove empty regions contained in larger ones
		for (int o = 0; o < emptyRegions.size(); o++) {
			ImRegion outer = ((ImRegion) emptyRegions.get(o));
			for (int i = (o+1); i < emptyRegions.size(); i++) {
				ImRegion inner = ((ImRegion) emptyRegions.get(i));
				if (inner.bounds.liesIn(outer.bounds, false)) {
					emptyRegions.remove(i--);
					page.removeRegion(inner);
				}
			}
		}
		
		//	mark remaining empty regions as images
		for (int r = 0; r < emptyRegions.size(); r++)
			((ImRegion) emptyRegions.get(r)).setType(ImRegion.IMAGE_TYPE);
	}
		
	private static final Comparator regionSizeOrder = new Comparator() {
		public int compare(Object obj1, Object obj2) {
			return (this.getSize(((ImRegion) obj2).bounds) - this.getSize(((ImRegion) obj1).bounds));
		}
		private int getSize(BoundingBox bb) {
			return ((bb.right - bb.left) * (bb.bottom - bb.top));
		}
	};
	
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
		
		//	check arguments
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
		
		//	preserve source PDF in supplement
		ImSupplement.Source.createSource(doc, "application/pdf", pdfBytes);
		
		//	cache higher level page structure as well
		Map pageRegionCache = Collections.synchronizedMap(new HashMap());
		
		//	fill in blocks and do OCR
		pm.setStep("Extracting blocks & doing OCR");
		pm.setBaseProgress(40);
		pm.setProgress(0);
		pm.setMaxProgress(70);
		this.addImagePdfPageBlocks(doc, pm, pageImageCache, pageRegionCache);
		
		//	analyze block structure and layout
		pm.setStep("Analyzing page text structure");
		pm.setBaseProgress(70);
		pm.setProgress(0);
		pm.setMaxProgress(100);
		this.addImagePdfPageLayout(doc, pm, pageImageCache, pageRegionCache);
		
		//	finally
		return doc;
	}
	
	private void addImagePdfPageLayout(final ImDocument doc, ProgressMonitor pm, final Map pageImageCache, final Map pageRegionCache) throws IOException {
		
		//	build progress monitor with synchronized methods instead of synchronizing in-code
		final ProgressMonitor spm = ((pm instanceof SynchronizedProgressMonitor) ? pm : new SynchronizedProgressMonitor(pm));
		
		//	get document pages
		final ImPage[] pages = doc.getPages();
		
		//	analyze page text layout
		ParallelFor pf = new ParallelFor() {
			public void doFor(int p) throws Exception {
				
				//	update status display (might be inaccurate, but better than lock escalation)
				spm.setInfo("Doing OCR for page " + p + " of " + pages.length);
				spm.setProgress((100 * p) / pages.length);
				
				//	get page image
				PageImage pi = ((PageImage) pageImageCache.get(new Integer(p)));
				if (pi == null)
					pi = PageImage.getPageImage(doc.docId, p);
				if (pi == null) {
					spm.setInfo(" - page image not found");
					throw new RuntimeException("Could not find image for page " + p);
				}
				
				//	index words by bounding boxes
				ImWord[] pageWords = pages[p].getWords();
				HashMap wordsByBoxes = new HashMap();
				for (int w = 0; w < pageWords.length; w++)
					wordsByBoxes.put(pageWords[w].bounds, pageWords[w]);
				
				//	obtain higher level page structure
				Region pageRootRegion;
				synchronized (pageRegionCache) {
					pageRootRegion = ((Region) pageRegionCache.remove(pages[p]));
				}
				if (pageRootRegion == null) {
					AnalysisImage api = Imaging.wrapImage(pi.image, null);
					pageRootRegion = PageImageAnalysis.getPageRegion(api, pi.currentDpi, false, spm);
				}
				addTextBlockStructure(pages[p], null, pageRootRegion, pi.currentDpi, wordsByBoxes, spm);
				
				//	do structure analysis
				PageAnalysis.splitIntoParagraphs(pages[p].getRegions(BLOCK_ANNOTATION_TYPE), pi.currentDpi, null);
				spm.setInfo(" - paragraphs done");
			
				PageAnalysis.computeColumnLayout(pages[p].getRegions(COLUMN_ANNOTATION_TYPE), pi.currentDpi);
				spm.setInfo(" - layout analysis done");
				
				//	clean up regions and mark images
				cleanUpRegions(pages[p]);
				
				//	... finally
				spm.setInfo(" - page done");
				
				//	clean up analysis image cache
				Imaging.cleanUpCache(pi.image.hashCode() + "-");
			}
		};
		ParallelJobRunner.runParallelFor(pf, ((PdfExtractorTest.aimAtPage < 0) ? 0 : PdfExtractorTest.aimAtPage), ((PdfExtractorTest.aimAtPage < 0) ? pages.length : (PdfExtractorTest.aimAtPage + 1)), (this.useMultipleCores ? -1 : 1));
		spm.setProgress(100);
		
		//	check errors
		this.checkException(pf);
		
		//	finalize text stream structure
		ImWord lastWord = null;
		for (int p = 0; p < pages.length; p++)
			lastWord = this.addTextStreamStructure(pages[p], lastWord);
		spm.setInfo(" - word sequence analysis done");
	}
	
	private ImWord addTextBlockStructure(ImPage page, ImWord lastWord, Region theRegion, int dpi, HashMap wordsByBoxes, ProgressMonitor pm) {
		
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
			lastWord = this.addTextBlockStructure(page, lastWord, theRegion.getSubRegion(s), dpi, wordsByBoxes, pm);
		
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
		
		//	check arguments
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
		Map pageImageCache = Collections.synchronizedMap(new HashMap() {
			public Object put(Object cacheKey, Object pageImage) {
				if (pageImage instanceof PageImage) {
					PageImage pi = ((PageImage) pageImage);
					if (pi.currentDpi > 300)
						return null;
					if ((pi.image.getType() != BufferedImage.TYPE_BYTE_BINARY) && (pi.image.getType() != BufferedImage.TYPE_BYTE_GRAY))
						return null;
				}
				return super.put(cacheKey, pageImage);
			}
		});
		
		//	load pages
		pm.setStep("Loading document page images");
		pm.setBaseProgress(0);
		pm.setProgress(0);
		pm.setMaxProgress(40);
		this.addImagePdfPages(doc, pdfDoc, pdfBytes, scaleFactor, pm, pageImageCache);
		
		//	preserve source PDF in supplement
		ImSupplement.Source.createSource(doc, "application/pdf", pdfBytes);
		
		//	fill in blocks and do OCR
		pm.setStep("Extracting blocks & doing OCR");
		pm.setBaseProgress(40);
		pm.setProgress(0);
		pm.setMaxProgress(100);
		this.addImagePdfPageBlocks(doc, pm, pageImageCache, null);
		
		//	finally ...
		return doc;
	}
	
	private ImDocument addImagePdfPageBlocks(final ImDocument doc, ProgressMonitor pm, final Map pageImageCache, final Map pageRegionCache) throws IOException {
		
		//	build progress monitor with synchronized methods instead of synchronizing in-code
		final ProgressMonitor spm = ((pm instanceof SynchronizedProgressMonitor) ? pm : new SynchronizedProgressMonitor(pm));
		
		//	get document pages
		final ImPage[] pages = doc.getPages();
		
		//	do high level structure analysis (down to blocks) and OCR
		ParallelFor pf = new ParallelFor() {
			public void doFor(int p) throws Exception {
				
				//	update status display (might be inaccurate, but better than lock escalation)
				spm.setInfo("Analyzing structure of page " + p + " of " + pages.length);
				spm.setProgress((100 * p) / pages.length);
				
				//	get page image
				PageImage pi = ((PageImage) pageImageCache.get(new Integer(p)));
				if (pi == null)
					pi = PageImage.getPageImage(doc.docId, p);
				if (pi == null) {
					spm.setInfo(" - page image not found");
					throw new RuntimeException("Could not find image for page " + p);
				}
				
				//	obtain higher level page structure
				AnalysisImage api = Imaging.wrapImage(pi.image, null);
				Region pageRootRegion = PageImageAnalysis.getPageRegion(api, pi.currentDpi, false, spm);
				addRegionBlocks(pages[p], pageRootRegion, pi.currentDpi, spm);
				if (pageRegionCache != null)
					synchronized (pageRegionCache) {
						pageRegionCache.put(pages[p], pageRootRegion);
					}
				
				//	do OCR
				spm.setInfo(" - doing OCR");
				ocrEngine.doBlockOcr(pages[p], pi, spm);
				spm.setInfo(" - OCR done");
				
				//	clean up regions and mark images
				cleanUpRegions(pages[p]);
				
				//	clean up analysis image cache
				Imaging.cleanUpCache(pi.image.hashCode() + "-");
			}
		};
		ParallelJobRunner.runParallelFor(pf, ((PdfExtractorTest.aimAtPage < 0) ? 0 : PdfExtractorTest.aimAtPage), ((PdfExtractorTest.aimAtPage < 0) ? pages.length : (PdfExtractorTest.aimAtPage + 1)), (this.useMultipleCores ? -1 : 1));
		spm.setProgress(100);
		
		//	check errors
		this.checkException(pf);
		
		//	... finally
		return doc;
	}
	
	private void addRegionBlocks(ImPage page, Region theRegion, int dpi, ProgressMonitor pm) {
		
		//	mark region
		ImRegion region = new ImRegion(page, theRegion.getBoundingBox());
		
		//	image region ==> mark it and we're done here
		if (theRegion.isImage())
			region.setType(ImRegion.IMAGE_TYPE);
		
		//	atomic region ==> block, mark it and we're done here
		else if (theRegion.isAtomic())
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
		
		//	check arguments
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
		
		//	preserve source PDF in supplement
		ImSupplement.Source.createSource(doc, "application/pdf", pdfBytes);
		
		//	finally ...
		return doc;
	}
	
	private void addImagePdfPages(final ImDocument doc, final Document pdfDoc, final byte[] pdfBytes, final int scaleFactor, ProgressMonitor pm, Map pageImageCache) throws IOException {
		
		//	build progress monitor with synchronized methods instead of synchronizing in-code
		final ProgressMonitor spm = ((pm instanceof SynchronizedProgressMonitor) ? pm : new SynchronizedProgressMonitor(pm));
		
		//	load document structure (IcePDF is better at that ...)
		final Catalog catalog = pdfDoc.getCatalog();
		final Library library = catalog.getLibrary();
		final PageTree pageTree = catalog.getPageTree();
		
//		//	we're testing, and the test page image has already been found
//		//	WE CANNOT DO THIS RIGHT HERE, AS THIS ALSO APPLIES TO A BORN-DIGITAL PDF WHEN NOT LOADED FOR THE FIRST TIME
//		if ((PdfExtractorTest.aimAtPage != -1) && this.imageStore.isPageImageAvailable(doc.docId, PdfExtractorTest.aimAtPage)) {
//			for (int p = 0; p < pageTree.getNumberOfPages(); p++) {
//				BoundingBox pageBounds;
//				if (p == PdfExtractorTest.aimAtPage) {
//					PageImageInputStream piis = this.imageStore.getPageImageAsStream(doc.docId, p);
//					piis.close();
//					pageBounds = new BoundingBox(0, ((piis.originalWidth * piis.currentDpi) / piis.originalDpi), 0, ((piis.originalHeight * piis.currentDpi) / piis.originalDpi));
//				}
//				else pageBounds = new BoundingBox(0, 32, 0, 44); // have to use these numbers to prevent 0 or 1 step in page word index
//				ImPage page = new ImPage(doc, p, pageBounds);
//			}
//			return;
//		}
//		
		//	extract page objects
		spm.setInfo("Getting page objects");
		final Page[] pdfPages = new Page[pageTree.getNumberOfPages()];
		final Hashtable[] pages = new Hashtable[pageTree.getNumberOfPages()];
		for (int p = 0; p < pageTree.getNumberOfPages(); p++) {
			pdfPages[p] = pageTree.getPage(p, "");
			if (pdfPages[p] == null)
				pages[p] = new Hashtable();
			else pages[p] = pdfPages[p].getEntries();
		}
		spm.setInfo(" --> got " + pageTree.getNumberOfPages() + " page objects");
		spm.setProgress(2);
		
//		//	test if we have to extract any page images at all
//		//	WE CANNOT DO THIS RIGHT HERE, AS THIS ALSO APPLIES TO A BORN-DIGITAL PDF WHEN NOT LOADED FOR THE FIRST TIME
//		boolean gotAllPageImages = true;
//		pm.setInfo("Checking if page images to extract");
//		for (int p = 0; p < pages.length; p++) {
//			if ((PdfExtractorTest.aimAtPage != -1) && (PdfExtractorTest.aimAtPage != p))
//				continue;
//			if (!this.imageStore.isPageImageAvailable(doc.docId, p)) {
//				gotAllPageImages = false;
//				break;
//			}
//		}
//		
//		//	we already have all page images in cache, save the hassle
//		//	WE CANNOT DO THIS RIGHT HERE, AS THIS ALSO APPLIES TO A BORN-DIGITAL PDF WHEN NOT LOADED FOR THE FIRST TIME (GENERIC LOAD OF BORN-DIGITAL PDF TRIGGERS MISTAKING IT AS SCANNED !!!)
//		if (gotAllPageImages) {
//			pm.setInfo(" --> all page images extracted before, loading from cache");
//			for (int p = 0; p < pageTree.getNumberOfPages(); p++) {
//				pm.setProgress(2 + ((p * 98) / pages.length));
//				PageImageInputStream piis = this.imageStore.getPageImageAsStream(doc.docId, p);
//				piis.close();
//				BoundingBox pageBounds = new BoundingBox(0, ((piis.originalWidth * piis.currentDpi) / piis.originalDpi), 0, ((piis.originalHeight * piis.currentDpi) / piis.originalDpi));
//				ImPage page = new ImPage(doc, p, pageBounds);
//			}
//			pm.setProgress(100);
//			return;
//		}
//		
		//	read objects
		spm.setInfo("Getting remaining objects");
		final Map objects = PdfParser.getObjects(pdfBytes);
		spm.setInfo(" --> done");
		spm.setProgress(10);
		
		//	extract page image objects
		spm.setInfo("Getting page image objects");
		final HashMap[] pageImages = new HashMap[pages.length];
		final int[] minPageImages = {Integer.MAX_VALUE};
		final int[] maxPageImages = {0};
		final int[] sumPageImages = {0};
		ParallelFor pf = new ParallelFor() {
			public void doFor(int p) throws Exception {
				
				//	any use going on?
				synchronized (minPageImages) {
					if (minPageImages[0] == 0)
						return;
				}
				
				//	initialize next page
				pageImages[p] = new HashMap(2);
				
				//	check contents (not whole resource dictionary might be rendered in every page)
				HashMap contentsImgResKeyPos = new HashMap();
				Object contentsObj = PdfParser.getObject(pages[p], "Contents", objects);
				if (contentsObj == null)
					spm.setInfo(" --> content not found");
				else {
					spm.setInfo(" --> got content");
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					if (contentsObj instanceof PStream) {
						Object filter = ((PStream) contentsObj).params.get("Filter");
						spm.setInfo("   --> stream content, filter is " + filter + " (from " + ((PStream) contentsObj).params + ")");
//						try {
							PdfParser.decode(filter, ((PStream) contentsObj).bytes, ((PStream) contentsObj).params, baos, objects);
//						} catch (Exception e) { pm.setInfo("   --> decoding failed: " + e.getMessage()); }
					}
					else if (contentsObj instanceof Vector) {
						spm.setInfo("   --> array content");
						for (Iterator cit = ((Vector) contentsObj).iterator(); cit.hasNext();) {
							Object contentObj = PdfParser.dereference(cit.next(), objects);
							if (contentObj instanceof PStream) {
								Object filter = ((PStream) contentObj).params.get("Filter");
								if (filter == null)
									continue;
								spm.setInfo("   --> stream content part, filter is " + filter + " (from " + ((PStream) contentObj).params + ")");
//								try {
									 PdfParser.decode(filter, ((PStream) contentObj).bytes, ((PStream) contentObj).params, baos, objects);
//								} catch (Exception e) { pm.setInfo("   --> decoding failed: " + e.getMessage()); }
							}
						}
					}
					System.out.println(" --> content decoded: " + new String(baos.toByteArray()));
					BufferedReader baosBr = new BufferedReader(new StringReader(new String(baos.toByteArray())));
					int imgCount = 0;
					for (String contentsLine; (contentsLine = baosBr.readLine()) != null;) {
						if (!contentsLine.matches("\\/[\\u0021-\\u007E]+\\s+Do"))
							continue;
						String imgResKey = contentsLine.substring("/".length());
						imgResKey = imgResKey.substring(0, (imgResKey.length() - " Do".length())).trim();
						contentsImgResKeyPos.put(imgResKey, new Integer(imgCount++));
					}
				}
				
				//	get resources
				Object resourcesObj = PdfParser.getObject(pages[p], "Resources", objects);
				if ((resourcesObj == null) || !(resourcesObj instanceof Hashtable)) {
					spm.setInfo(" --> resource map not found");
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
							if ((contentsImgResKeyPos.size() != 0) && !contentsImgResKeyPos.containsKey(xResKey.toString()))
								continue;
							if (xResObj instanceof PStream) {
								if (contentsImgResKeyPos.isEmpty())
									pageImages[p].put(("i" + imgCount), new PImage((PStream) xResObj));
								else pageImages[p].put(("i" + contentsImgResKeyPos.get(xResKey.toString())), new PImage((PStream) xResObj));
								PImage mImg = PdfParser.getMaskImage(((PStream) xResObj), objects);
								if (mImg != null) {
									if (contentsImgResKeyPos.isEmpty())
										pageImages[p].put(("m" + imgCount), mImg);
									else pageImages[p].put(("m" + contentsImgResKeyPos.get(xResKey.toString())), new PImage((PStream) xResObj));
								}
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
		};
		ParallelJobRunner.runParallelFor(pf, ((PdfExtractorTest.aimAtPage < 0) ? 0 : PdfExtractorTest.aimAtPage), ((PdfExtractorTest.aimAtPage < 0) ? pageTree.getNumberOfPages() : (PdfExtractorTest.aimAtPage + 1)), (this.useMultipleCores ? -1 : 1));
		spm.setInfo(" --> got at least " + minPageImages[0] + ", at most " + maxPageImages[0] + " images per page, " + sumPageImages[0] + " in total");
		spm.setProgress(20);
		
		//	check errors
		this.checkException(pf);
		
		//	check consistency
		if (minPageImages[0] == 0)
			throw new IOException("Unable to find images for all pages");
		
		//	extract page bounds and assess format
		spm.setInfo("Determining page proportions");
		Rectangle2D.Float[] pageBoxes = new Rectangle2D.Float[pages.length];
		int portraitPageCount = 0;
		int landscapePageCount = 0;
		for (int p = 0; p < pageTree.getNumberOfPages(); p++) {
			pageBoxes[p] = pageTree.getPage(p, "").getMediaBox();
			if ((pageBoxes[p].getWidth() * 5) < (pageBoxes[p].getHeight() * 4))
				portraitPageCount++;
			else if ((pageBoxes[p].getHeight() * 5) < (pageBoxes[p].getWidth() * 4))
				landscapePageCount++;
		}
		if (portraitPageCount < landscapePageCount)
			spm.setInfo(" ==> pages are landscape, likely double pages");
		else spm.setInfo(" ==> pages are portrait, likely single pages");
		
		//	find out which page image holds the text
		spm.setInfo("Determining text image ID");
		final String textImageId = this.findTextImageId(pageImages, pageTree, objects, (portraitPageCount < landscapePageCount), library);
		if (textImageId == null)
			throw new IOException("Unable to find images for all pages");
		spm.setInfo(" ==> text page image id is " + textImageId);
		spm.setProgress(30);
		
		//	extract & save page images
		if (portraitPageCount < landscapePageCount)
			this.addImagePdfPagesDouble(doc, scaleFactor, pages, pdfPages, pageBoxes, pageImages, textImageId, objects, pageImageCache, spm, library);
		else this.addImagePdfPagesSingle(doc, scaleFactor, pages, pdfPages, pageBoxes, pageImages, textImageId, objects, pageImageCache, spm, library);
	}
	
	private void addImagePdfPagesSingle(final ImDocument doc, final int scaleFactor, final Hashtable[] pages, final Page[] pdfPages, final Rectangle2D.Float[] pageBoxes, final HashMap[] pageImages, final String textImageId, final Map objects, final Map pageImageCache, ProgressMonitor pm, final Library library) throws IOException {
		
		//	build progress monitor with synchronized methods instead of synchronizing in-code
		final ProgressMonitor spm = ((pm instanceof SynchronizedProgressMonitor) ? pm : new SynchronizedProgressMonitor(pm));
		
		//	extract & save page images
		final BoundingBox[] pageBounds = new BoundingBox[pages.length];
		ParallelFor pf = new ParallelFor() {
			public void doFor(int p) throws Exception {
				
				//	update status display (might be inaccurate, but better than lock escalation)
				spm.setStep("Importing page " + p + " of " + pages.length);
				spm.setProgress(30 + ((p * 70) / pages.length));
				spm.setInfo(" - getting page image");
				
				//	image already extracted
				if (imageStore.isPageImageAvailable(doc.docId, p)) {
					spm.setInfo(" --> image already extracted");
					spm.setInfo(" - getting image data ...");
					PageImageInputStream piis = imageStore.getPageImageAsStream(doc.docId, p);
					piis.close();
					int dpi = piis.currentDpi;
					pageBounds[p] = new BoundingBox(0, ((piis.originalWidth * piis.currentDpi) / piis.originalDpi), 0, ((piis.originalHeight * piis.currentDpi) / piis.originalDpi));
					spm.setInfo(" - resolution is " + dpi + " DPI, page bounds are " + pageBounds[p].toString());
				}
				
				//	image not extracted as yet, do it now
				else {
					
					//	get compressed image
					PImage imageObject = ((PImage) pageImages[p].get(textImageId));
					if (imageObject == null) {
						spm.setInfo(" --> image not found");
						throw new RuntimeException("Could not find image object for page " + p);
					}
					spm.setInfo(" --> image not rendered yet");
					spm.setInfo(" - rendering image ...");
					
					//	get raw image
					BufferedImage pageImage = getImage(imageObject, objects, library, pdfPages[p].getResources());
					if (pageImage == null) {
						spm.setInfo(" --> page image generation failed");
						throw new RuntimeException("Could not generate image for page " + p);
					}
					spm.setInfo(" - got page image sized " + pageImage.getWidth() + " x " + pageImage.getHeight());
					
					//	compute DPI
					int dpiScaleFactor = scaleFactor;
					float dpiRatio = ((((float) pageImage.getWidth()) / pageBoxes[p].width) + (((float) pageImage.getHeight()) / pageBoxes[p].height)) / 2;
					float rawDpi = dpiRatio * defaultDpi;
					int dpi = ((Math.round(rawDpi / 10) * 10) / dpiScaleFactor);
					spm.setInfo(" - resolution computed as " + dpi + " DPI");
					
					//	guess DPI based on page size (minimum page size for (very most) publications should be A5 or so)
					float a5distWidth = Math.abs((pageBoxes[p].width / defaultDpi) - a5inchWidth);
					float a5distHeigth = Math.abs((pageBoxes[p].height / defaultDpi) - a5inchHeigth);
					float a7distWidth = Math.abs((pageBoxes[p].width / defaultDpi) - (a5inchWidth / 2));
					float a7distHeigth = Math.abs((pageBoxes[p].height / defaultDpi) - (a5inchHeigth / 2));
					while ((a7distWidth < a5distWidth) && (a7distHeigth < a5distHeigth) && (minScaleUpDpi < dpi)) {
						spm.setInfo(" - A5 dist is " + a5distWidth + " / " + a5distHeigth);
						spm.setInfo(" - A7 dist is " + a7distWidth + " / " + a7distHeigth);
						dpiScaleFactor++;
						dpi = ((Math.round(rawDpi / 10) * 10) / dpiScaleFactor);
						a5distWidth = Math.abs(((pageBoxes[p].width * dpiScaleFactor) / defaultDpi) - a5inchWidth);
						a5distHeigth = Math.abs(((pageBoxes[p].height * dpiScaleFactor) / defaultDpi) - a5inchHeigth);
						a7distWidth = Math.abs(((pageBoxes[p].width * dpiScaleFactor) / defaultDpi) - (a5inchWidth / 2));
						a7distHeigth = Math.abs(((pageBoxes[p].height * dpiScaleFactor) / defaultDpi) - (a5inchHeigth / 2));
					}
					if (dpiScaleFactor > 1)
						spm.setInfo(" - resolution scaled to " + dpi + " DPI");
					
					//	if image is beyond 400 DPI, scale to half of that
					if (dpi >= minScaleDownDpi) {
						int scaleDownFactor = 2;
						while ((dpi / scaleDownFactor) >= minScaleDownDpi)
							scaleDownFactor++;
						spm.setInfo(" - scaling page image to " + (dpi / scaleDownFactor) + " DPI");
						BufferedImage sPageImage = new BufferedImage((pageImage.getWidth() / scaleDownFactor), (pageImage.getHeight() / scaleDownFactor), pageImage.getType());
						Graphics2D spig = sPageImage.createGraphics();
						spig.drawImage(pageImage, 0, 0, sPageImage.getWidth(), sPageImage.getHeight(), Color.WHITE, null);
						pageImage = sPageImage;
						dpi = (dpi / scaleDownFactor);
						spm.setInfo(" - page image scaled to " + dpi + " DPI");
					}
					
					//	preserve original page image as supplement
					synchronized (doc) {
						ImSupplement.Scan.createScan(doc, p, dpi, pageImage);
					}
					
					//	enhance image (cannot use cache here, as image might change during correction)
					AnalysisImage ai = Imaging.wrapImage(pageImage, null);
					spm.setInfo(" - enhancing image ...");
					ai = Imaging.correctImage(ai, dpi, spm);
					pageImage = ai.getImage();
					
					//	TODO when splitting OCR words for tokenization, pull up bottom of full words that have no descent
					
					//	TODO do further cleanup, e.g. removing pencil strokes, etc.
					//	TODO ==> collect examples and talk to Hilde
					
					//	store image
					String imageName;
					synchronized (imageStore) {
						imageName = imageStore.storePageImage(doc.docId, p, pageImage, dpi);
					}
					if (imageName == null) {
						spm.setInfo(" --> page image storage failed");
						throw new RuntimeException("Could not store image for page " + p);
					}
					if (pageImageCache != null)
						pageImageCache.put(new Integer(p), new PageImage(pageImage, dpi, imageStore)); // specifying image store from outside indicates who's handling the image now, as we know it was stored successfully
					Imaging.cleanUpCache(pageImage.hashCode() + "-");
					
					//	compute page bounds
					pageBounds[p] = new BoundingBox(0, pageImage.getWidth(), 0, pageImage.getHeight());
					spm.setInfo(" --> page image stored, page bounds are " + pageBounds[p].toString());
				}
			}
		};
		ParallelJobRunner.runParallelFor(pf, ((PdfExtractorTest.aimAtPage < 0) ? 0 : PdfExtractorTest.aimAtPage), ((PdfExtractorTest.aimAtPage < 0) ? pages.length : (PdfExtractorTest.aimAtPage + 1)), (this.useMultipleCores ? -1 : 1));
		spm.setProgress(99);
		
		//	check errors
		this.checkException(pf);
		
		//	assemble document
		for (int p = 0; p < pages.length; p++) {
			if ((PdfExtractorTest.aimAtPage != -1) && (PdfExtractorTest.aimAtPage != p))
				continue;
			ImPage page = new ImPage(doc, p, pageBounds[p]);
		}
		
		//	finally ...
		spm.setProgress(100);
	}
	
	private void addImagePdfPagesDouble(final ImDocument doc, final int scaleFactor, final Hashtable[] pages, final Page[] pdfPages, final Rectangle2D.Float[] pageBoxes, final HashMap[] pageImages, final String textImageId, final Map objects, final Map pageImageCache, ProgressMonitor pm, final Library library) throws IOException {
		
		//	build progress monitor with synchronized methods instead of synchronizing in-code
		final ProgressMonitor spm = ((pm instanceof SynchronizedProgressMonitor) ? pm : new SynchronizedProgressMonitor(pm));
		
		//	extract & save page images
		final BoundingBox[] pageBounds = new BoundingBox[pages.length * 2];
		ParallelFor pf = new ParallelFor() {
			public void doFor(int pp) throws Exception {
				
				//	update status display (might be inaccurate, but better than lock escalation)
				spm.setStep("Importing double page " + pp + " of " + pages.length);
				spm.setProgress(30 + ((pp * 70) / pages.length));
				spm.setInfo(" - getting double page image");
				
				//	images already extracted
				if (imageStore.isPageImageAvailable(doc.docId, (pp * 2)) && imageStore.isPageImageAvailable(doc.docId, ((pp * 2) + 1))) {
					spm.setInfo(" --> image halves already extracted");
					spm.setInfo(" - getting image data ...");
					PageImageInputStream piisLeft = imageStore.getPageImageAsStream(doc.docId, (pp * 2));
					piisLeft.close();
					int dpiLeft = piisLeft.currentDpi;
					pageBounds[pp * 2] = new BoundingBox(0, ((piisLeft.originalWidth * piisLeft.currentDpi) / piisLeft.originalDpi), 0, ((piisLeft.originalHeight * piisLeft.currentDpi) / piisLeft.originalDpi));
					spm.setInfo(" - resolution left is " + dpiLeft + " DPI, page bounds are " + pageBounds[pp * 2].toString());
					PageImageInputStream piisRight = imageStore.getPageImageAsStream(doc.docId, ((pp * 2) + 1));
					piisRight.close();
					int dpiRight = piisRight.currentDpi;
					pageBounds[(pp * 2) + 1] = new BoundingBox(0, ((piisRight.originalWidth * piisRight.currentDpi) / piisRight.originalDpi), 0, ((piisRight.originalHeight * piisRight.currentDpi) / piisRight.originalDpi));
					spm.setInfo(" - resolution right is " + dpiRight + " DPI, page bounds are " + pageBounds[(pp * 2) + 1].toString());
				}
				
				//	image not extracted as yet, do it now
				else {
					
					//	get compressed image
					PImage imageObject = ((PImage) pageImages[pp].get(textImageId));
					if (imageObject == null) {
						spm.setInfo(" --> image not found");
						throw new RuntimeException("Could not find image object for double page " + pp);
					}
					spm.setInfo(" --> image not rendered yet");
					spm.setInfo(" - rendering image ...");
					
					//	get raw image
					BufferedImage doublePageImage = getImage(imageObject, objects, library, pdfPages[pp].getResources());
					if (doublePageImage == null) {
						spm.setInfo(" --> page image generation failed");
						throw new RuntimeException("Could not generate image for double page " + pp);
					}
					spm.setInfo(" - got double page image sized " + doublePageImage.getWidth() + " x " + doublePageImage.getHeight());
					
					//	compute DPI
					int dpiScaleFactor = scaleFactor;
					float dpiRatio = ((((float) doublePageImage.getWidth()) / pageBoxes[pp].width) + (((float) doublePageImage.getHeight()) / pageBoxes[pp].height)) / 2;
					float rawDpi = dpiRatio * defaultDpi;
					int dpi = ((Math.round(rawDpi / 10) * 10) / dpiScaleFactor);
					spm.setInfo(" - resolution computed as " + dpi + " DPI");
					
					//	guess DPI based on page size (minimum page size for (very most) publications should be A5 or so, twice A5 here)
					float a5distWidth = Math.abs((pageBoxes[pp].width / defaultDpi) - (a5inchWidth * 2));
					float a5distHeigth = Math.abs((pageBoxes[pp].height / defaultDpi) - a5inchHeigth);
					float a7distWidth = Math.abs((pageBoxes[pp].width / defaultDpi) - a5inchWidth);
					float a7distHeigth = Math.abs((pageBoxes[pp].height / defaultDpi) - (a5inchHeigth / 2));
					while ((a7distWidth < a5distWidth) && (a7distHeigth < a5distHeigth) && (minScaleUpDpi < dpi)) {
						spm.setInfo(" - A5 dist is " + a5distWidth + " / " + a5distHeigth);
						spm.setInfo(" - A7 dist is " + a7distWidth + " / " + a7distHeigth);
						dpiScaleFactor++;
						dpi = ((Math.round(rawDpi / 10) * 10) / dpiScaleFactor);
						a5distWidth = Math.abs(((pageBoxes[pp].width * dpiScaleFactor) / defaultDpi) - (a5inchWidth * 2));
						a5distHeigth = Math.abs(((pageBoxes[pp].height * dpiScaleFactor) / defaultDpi) - a5inchHeigth);
						a7distWidth = Math.abs(((pageBoxes[pp].width * dpiScaleFactor) / defaultDpi) - a5inchWidth);
						a7distHeigth = Math.abs(((pageBoxes[pp].height * dpiScaleFactor) / defaultDpi) - (a5inchHeigth / 2));
					}
					if (dpiScaleFactor > 1)
						spm.setInfo(" - resolution scaled to " + dpi + " DPI");
					
					//	if image is beyond 400 DPI, scale to half of that
					if (dpi >= minScaleDownDpi) {
						int scaleDownFactor = 2;
						while ((dpi / scaleDownFactor) >= minScaleDownDpi)
							scaleDownFactor++;
						spm.setInfo(" - scaling double page image to " + (dpi / scaleDownFactor) + " DPI");
						BufferedImage sDoublePageImage = new BufferedImage((doublePageImage.getWidth() / scaleDownFactor), (doublePageImage.getHeight() / scaleDownFactor), doublePageImage.getType());
						Graphics2D spig = sDoublePageImage.createGraphics();
						spig.drawImage(doublePageImage, 0, 0, sDoublePageImage.getWidth(), sDoublePageImage.getHeight(), Color.WHITE, null);
						doublePageImage = sDoublePageImage;
						dpi = (dpi / scaleDownFactor);
						spm.setInfo(" - double page image scaled to " + dpi + " DPI");
					}
					
					//	cut double page image into two single pages
					BufferedImage pageImageLeft = doublePageImage.getSubimage(0, 0, (doublePageImage.getWidth() / 2), doublePageImage.getHeight());
					BufferedImage pageImageRight = doublePageImage.getSubimage((doublePageImage.getWidth() / 2), 0, (doublePageImage.getWidth() / 2), doublePageImage.getHeight());
					
					//	preserve original page image halves
					synchronized (doc) {
						ImSupplement.Scan.createScan(doc, (pp * 2), dpi, pageImageLeft);
						ImSupplement.Scan.createScan(doc, ((pp * 2) + 1), dpi, pageImageRight);
					}
							
					//	enhance image halves (cannot use cache here, as image might change during correction)
					AnalysisImage aiLeft = Imaging.wrapImage(pageImageLeft, null);
					spm.setInfo(" - enhancing left image half ...");
					aiLeft = Imaging.correctImage(aiLeft, dpi, spm);
					pageImageLeft = aiLeft.getImage();
					AnalysisImage aiRight = Imaging.wrapImage(pageImageRight, null);
					spm.setInfo(" - enhancing right image half ...");
					aiRight = Imaging.correctImage(aiRight, dpi, spm);
					pageImageRight = aiRight.getImage();
					
					//	TODO do further cleanup, e.g. removing pencil strokes, etc.
					//	TODO ==> collect examples and talk to Hilde
					
					//	store image halves
					String imageNameLeft;
					synchronized (imageStore) {
						imageNameLeft = imageStore.storePageImage(doc.docId, (pp * 2), pageImageLeft, dpi);
					}
					if (imageNameLeft == null) {
						spm.setInfo(" --> page image storage failed for left half");
						throw new RuntimeException("Could not store left half of image for double page " + pp);
					}
					if (pageImageCache != null)
						pageImageCache.put(new Integer(pp * 2), new PageImage(pageImageLeft, dpi, imageStore)); // specifying image store from outside indicates who's handling the image now, as we know it was stored successfully
					Imaging.cleanUpCache(pageImageLeft.hashCode() + "-");
					String imageNameRight;
					synchronized (imageStore) {
						imageNameRight = imageStore.storePageImage(doc.docId, ((pp * 2) + 1), pageImageRight, dpi);
					}
					if (imageNameRight == null) {
						spm.setInfo(" --> page image storage failed for right half");
						throw new RuntimeException("Could not store right half of image for double page " + pp);
					}
					if (pageImageCache != null)
						pageImageCache.put(new Integer((pp * 2) + 1), new PageImage(pageImageRight, dpi, imageStore)); // specifying image store from outside indicates who's handling the image now, as we know it was stored successfully
					Imaging.cleanUpCache(pageImageRight.hashCode() + "-");
					
					//	compute page bounds
					pageBounds[pp * 2] = new BoundingBox(0, pageImageLeft.getWidth(), 0, pageImageLeft.getHeight());
					pageBounds[(pp * 2) + 1] = new BoundingBox(0, pageImageRight.getWidth(), 0, pageImageRight.getHeight());
					spm.setInfo(" --> double page image halves stored, page bounds are " + pageBounds[pp].toString() + " and " + pageBounds[pp + 1].toString());
				}
			}
		};
		ParallelJobRunner.runParallelFor(pf, ((PdfExtractorTest.aimAtPage < 0) ? 0 : PdfExtractorTest.aimAtPage), ((PdfExtractorTest.aimAtPage < 0) ? pages.length : (PdfExtractorTest.aimAtPage + 1)), (this.useMultipleCores ? -1 : 1));
		spm.setProgress(99);
		
		//	check errors
		this.checkException(pf);
		
		//	assemble document (two pages per PDF page here)
		for (int p = 0; p < pages.length; p++) {
			if ((PdfExtractorTest.aimAtPage != -1) && (PdfExtractorTest.aimAtPage != p))
				continue;
			ImPage pageLeft = new ImPage(doc, (p * 2), pageBounds[p * 2]);
			ImPage pageRight = new ImPage(doc, ((p * 2) + 1), pageBounds[(p * 2) + 1]);
		}
		
		//	finally ...
		spm.setProgress(100);
	}
	
	private void checkException(ParallelFor pf) throws IOException, RuntimeException {
		Exception error = pf.getException();
		if (error != null) {
			if (error instanceof RuntimeException)
				throw ((RuntimeException) error);
			else if (error instanceof IOException)
				throw ((IOException) error);
			else throw new IOException(error.getMessage());
		}
	}
	
	private static int textImageIdMinSamples = 3;
	private static int textImageIdMaxSamples = 20;
	private String findTextImageId(HashMap[] pageImages, PageTree pageTree, Map objects, boolean pagesAreLandscape, Library library) throws IOException {
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
				this.addImageIdsAndScores(pageImages[p], objects, imageIds, imageIdScores, pageBounds, pagesAreLandscape, library, pageTree.getPage(p, "").getResources());
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
					this.addImageIdsAndScores(pageImages[p], objects, imageIds, imageIdScores, pageBounds, pagesAreLandscape, library, pageTree.getPage(p, "").getResources());
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
				this.addImageIdsAndScores(pageImages[p], objects, imageIds, imageIdScores, pageBounds, pagesAreLandscape, library, pageTree.getPage(p, "").getResources());
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
					this.addImageIdsAndScores(pageImages[p], objects, imageIds, imageIdScores, pageBounds, pagesAreLandscape, library, pageTree.getPage(p, "").getResources());
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
					this.addImageIdsAndScores(pageImages[p], objects, imageIds, imageIdScores, pageBounds, pagesAreLandscape, library, pageTree.getPage(p, "").getResources());
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
	
	private void addImageIdsAndScores(HashMap pageImages, Map objects, HashSet imageIds, StringIndex imageIdScores, BoundingBox pageBounds, boolean pagesAreLandscape, Library library, Resources resources) throws IOException {
		int pageWidth = (pageBounds.right - pageBounds.left);
		int pageHeight = (pageBounds.bottom - pageBounds.top);
		float pageWidthByHeight = (((float) pageWidth) / pageHeight);
		for (Iterator iit = pageImages.keySet().iterator(); iit.hasNext();) {
			String imgId = ((String) iit.next());
			System.out.println(" - doing image " + imgId);
			PImage img = ((PImage) pageImages.get(imgId));
			
			//	get image
			BufferedImage bi = this.getImage(img, objects, library, resources);
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
			
			//	if image is landscape, cut it down the middle and score halves
			if (pagesAreLandscape) {
				int imgScoreLeft = this.scoreImage(bi.getSubimage(0, 0, (bi.getWidth() / 2), bi.getHeight()), imgId);
				System.out.println(" --> left score is " + imgScoreLeft);
				int imgScoreRight = this.scoreImage(bi.getSubimage((bi.getWidth() / 2), 0, (bi.getWidth() / 2), bi.getHeight()), imgId);
				System.out.println(" --> right score is " + imgScoreRight);
				imageIds.add(imgId);
				imageIdScores.add(imgId, (imgScoreLeft + imgScoreRight));
			}
			
			//	if image is portrait, score it as a whole
			else {
				int imgScore = this.scoreImage(bi, imgId);
				System.out.println(" --> score is " + imgScore);
				imageIds.add(imgId);
				imageIdScores.add(imgId, imgScore);
			}
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
	
	private BufferedImage getImage(PImage pi, Map objects, Library lib, Resources res) throws IOException {
		BufferedImage bi = pi.getImage();
		System.out.println("Getting image from " + pi.data.params);
		if (bi == null) {
			bi = this.decodeImage(pi.data.params, pi.data.bytes, objects, lib, res);
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
	
	private BufferedImage decodeImage(Hashtable params, byte[] stream, Map objects, Library lib, Resources res) throws IOException {
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
			return this.decodeFlate(stream, params);
		}
		else return this.decodeOther(stream, params, filter, lib, res);
	}
	
	private BufferedImage decodeJPX(byte[] stream) throws IOException {
		return this.decodeImageMagick(stream, "jp2");
	}
	
	private BufferedImage decodeImageMagick(byte[] stream, String format) throws IOException {
		return ((this.imageDecoder == null) ? null : this.imageDecoder.decodeImage(stream, format));
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
	
	private BufferedImage decodeFlate(byte[] stream, Hashtable params) throws IOException {
		SeekableInputConstrainedWrapper streamInputWrapper = new SeekableInputConstrainedWrapper(new SeekableByteArrayInputStream(stream), 0, stream.length, true);
		Stream str = new Stream(new Library(), params, streamInputWrapper);
		try {
			/* if we have a mask image
			 * Decoding [0, 1] ==> 0 means paint-through (default), requires black background
			 * Decoding [1, 0] ==> 1 means paint-through, requires white background */
			Color biBackgroundColor;
			Object imObj = params.get("ImageMask");
			if (imObj == null) {
				System.out.println(" - image mask is null");
				biBackgroundColor = Color.WHITE;
			}
			else {
				System.out.println(" - image mask is " + imObj.getClass().getName() + " - " + imObj);
				Object dObj = params.get("Decode");
				if ((dObj instanceof Vector) && (((Vector) dObj).size() != 0)) {
					System.out.println(" - decoder is " + dObj.getClass().getName() + " - " + dObj);
					Object ptObj = ((Vector) dObj).get(0);
					System.out.println(" - paint-through is " + dObj.getClass().getName() + " - " + dObj);
					if (ptObj instanceof Number) {
						if (((Number) ptObj).intValue() == 0)
							biBackgroundColor = Color.BLACK;
						else biBackgroundColor = Color.WHITE;
					}
					else biBackgroundColor = Color.BLACK;
				}
				else {
					if (dObj == null)
						System.out.println(" - decoder is null");
					else System.out.println(" - decoder is " + dObj.getClass().getName() + " - " + dObj);
					biBackgroundColor = Color.BLACK;
				}
			}
			BufferedImage bi = str.getImage(biBackgroundColor, new Resources(new Library(), params), false);
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
	
	private BufferedImage decodeOther(byte[] stream, Hashtable params, String filter, Library lib, Resources res) throws IOException {
		SeekableInputConstrainedWrapper streamInputWrapper = new SeekableInputConstrainedWrapper(new SeekableByteArrayInputStream(stream), 0, stream.length, true);
		Stream str = new Stream(lib, params, streamInputWrapper);
		try {
			BufferedImage bi = str.getImage(Color.white, new Resources(lib, params), false);
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
	
	private BufferedImage extractFigureFromPageImage(Document doc, int p, Rectangle2D.Float pageBounds, int dpi, Rectangle2D figureBounds) {
		
		//	render page image at figure resolution
		float figureMagnification = (((float) dpi) / defaultDpi);
		BufferedImage figurePageImage;
		synchronized (doc) {
			figurePageImage = ((BufferedImage) doc.getPageImage(p, GraphicsRenderingHints.SCREEN, Page.BOUNDARY_CROPBOX, 0, figureMagnification));
		}
		
		//	convert bounds, as PDF Y coordinate is bottom-up, whereas Java, JavaScript, etc. Y coordinate is top-down
		int figureLeft = Math.round(((float) (figureBounds.getMinX() - pageBounds.getMinX())) * figureMagnification);
		int figureRight = Math.round(((float) (figureBounds.getMaxX() - pageBounds.getMinX()))  * figureMagnification);
		int figureTop = Math.round((pageBounds.height - ((float) (figureBounds.getMaxY() - ((2 * pageBounds.getMinY()) - pageBounds.getMaxY())))) * figureMagnification);
		int figureBottom = Math.round((pageBounds.height - ((float) (figureBounds.getMinY() - ((2 * pageBounds.getMinY()) - pageBounds.getMaxY())))) * figureMagnification);
		
		//	extract figure
		return figurePageImage.getSubimage(figureLeft, figureTop, (figureRight - figureLeft), (figureBottom - figureTop));
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
//	
//	public static void main(String[] args) throws Exception {
//		File imagePath = new File("E:/Testdaten/PdfExtract/DeviceCMYK/");
//		String imageName;
//		imageName = "figure@11.[148,886,180,1164]";
////		imageName = "figure@25.[148,886,176,1160]";
////		imageName = "figure@29.[146,886,159,664]";
//		BufferedImage bia = ImageIO.read(new File(imagePath, (imageName + ".a.png")));
//		System.out.println("Acrobat image is " + bia.getWidth() + " x " + bia.getHeight());
//		BufferedImage big = ImageIO.read(new File(imagePath, (imageName + ".g.png")));
//		System.out.println("GoldenGATE image is " + big.getWidth() + " x " + big.getHeight());
//		
//		long rDistSum = 0;
//		long gDistSum = 0;
//		long bDistSum = 0;
//		
//		float[] hsla = new float[3];
//		float[] hslg = new float[3];
//		
//		float hDist;
//		float hDistMin = 1;
//		float hDistMax = 0;
//		double hDistSum = 0;
//		double hDistSumAbs = 0;
//		float sDist;
//		float sDistMin = 1;
//		float sDistMax = -1;
//		double sDistSum = 0;
//		double sDistSumAbs = 0;
//		float lDist;
//		float lDistMin = 1;
//		float lDistMax = -1;
//		double lDistSum = 0;
//		double lDistSumAbs = 0;
//		float whDist;
//		float whDistMin = 1;
//		float whDistMax = 0;
//		double whDistSum = 0;
//		double whDistSumAbs = 0;
//		for (int x = 0; x < Math.min(bia.getWidth(), big.getWidth()); x++)
//			for (int y = 0; y < Math.min(bia.getHeight(), big.getHeight()); y++) {
//				int rgba = bia.getRGB(x, y);
//				int rgbg = big.getRGB(x, y);
//				rDistSum += Math.abs(((rgba >>> 16) & 255) - ((rgbg >>> 16) & 255));
//				gDistSum += Math.abs(((rgba >>> 8) & 255) - ((rgbg >>> 8) & 255));
//				bDistSum += Math.abs((rgba & 255) - (rgbg & 255));
//				
//				hsla = Color.RGBtoHSB(((rgba >>> 16) & 255), ((rgba >>> 8) & 255), (rgba & 255), hsla);
//				hslg = Color.RGBtoHSB(((rgbg >>> 16) & 255), ((rgbg >>> 8) & 255), (rgbg & 255), hslg);
//				hDist = (hsla[0] - hslg[0]);
//				if (hDist < 0)
//					hDist += 1;
//				sDist = (hsla[1] - hslg[1]);
//				lDist = (hsla[2] - hslg[2]);
//				whDist = ((hDist * (hsla[1] + hslg[1])) / 2);
//				
//				hDistMin = Math.min(hDistMin, hDist);
//				hDistMax = Math.max(hDistMax, hDist);
//				hDistSum += hDist;
//				hDistSumAbs += Math.abs(hDist);
//				sDistMin = Math.min(sDistMin, sDist);
//				sDistMax = Math.max(sDistMax, sDist);
//				sDistSum += sDist;
//				sDistSumAbs += Math.abs(sDist);
//				lDistMin = Math.min(lDistMin, lDist);
//				lDistMax = Math.max(lDistMax, lDist);
//				lDistSum += lDist;
//				lDistSumAbs += Math.abs(lDist);
//				whDistMin = Math.min(whDistMin, whDist);
//				whDistMax = Math.max(whDistMax, whDist);
//				whDistSum += whDist;
//				whDistSumAbs += Math.abs(whDist);
//			}
//		int biSize = (Math.min(bia.getWidth(), big.getWidth()) * Math.min(bia.getHeight(), big.getHeight()));
//		System.out.println("Average R-Dist is " + (rDistSum / biSize));
//		System.out.println("Average G-Dist is " + (gDistSum / biSize));
//		System.out.println("Average B-Dist is " + (bDistSum / biSize));
//		System.out.println();
//		System.out.println("Average H-Dist is " + (hDistSum / biSize) + ", " + (hDistSumAbs / biSize) + " abs [" + hDistMin + "," + hDistMax + "]");
//		System.out.println("Average S-Dist is " + (sDistSum / biSize) + ", " + (sDistSumAbs / biSize) + " abs [" + sDistMin + "," + sDistMax + "]");
//		System.out.println("Average L-Dist is " + (lDistSum / biSize) + ", " + (lDistSumAbs / biSize) + " abs [" + lDistMin + "," + lDistMax + "]");
//		System.out.println("Average S-weighted H-Dist is " + (whDistSum / biSize) + ", " + (whDistSumAbs / biSize) + " abs [" + whDistMin + "," + whDistMax + "]");
//	}
}