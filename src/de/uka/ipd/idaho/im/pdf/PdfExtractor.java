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
import java.awt.geom.Rectangle2D.Float;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
import java.util.Set;
import java.util.Vector;

import javax.imageio.ImageIO;

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
import org.icepdf.core.pobjects.graphics.text.WordText;
import org.icepdf.core.util.GraphicsRenderingHints;
import org.icepdf.core.util.Library;
import org.jpedal.jbig2.JBIG2Decoder;
import org.jpedal.jbig2.JBIG2Exception;

import de.uka.ipd.idaho.easyIO.util.RandomByteSource;
import de.uka.ipd.idaho.gamta.Gamta;
import de.uka.ipd.idaho.gamta.MutableAnnotation;
import de.uka.ipd.idaho.gamta.TokenSequence;
import de.uka.ipd.idaho.gamta.Tokenizer;
import de.uka.ipd.idaho.gamta.defaultImplementation.RegExTokenizer;
import de.uka.ipd.idaho.gamta.util.ParallelJobRunner;
import de.uka.ipd.idaho.gamta.util.ParallelJobRunner.ParallelFor;
import de.uka.ipd.idaho.gamta.util.ProgressMonitor;
import de.uka.ipd.idaho.gamta.util.ProgressMonitor.CascadingProgressMonitor;
import de.uka.ipd.idaho.gamta.util.ProgressMonitor.SynchronizedProgressMonitor;
import de.uka.ipd.idaho.gamta.util.constants.TableConstants;
import de.uka.ipd.idaho.gamta.util.imaging.BoundingBox;
import de.uka.ipd.idaho.gamta.util.imaging.DocumentStyle;
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
import de.uka.ipd.idaho.im.analysis.Imaging.ImagePartRectangle;
import de.uka.ipd.idaho.im.analysis.PageAnalysis;
import de.uka.ipd.idaho.im.analysis.PageImageAnalysis;
import de.uka.ipd.idaho.im.analysis.PageImageAnalysis.Block;
import de.uka.ipd.idaho.im.analysis.PageImageAnalysis.Line;
import de.uka.ipd.idaho.im.analysis.PageImageAnalysis.Region;
import de.uka.ipd.idaho.im.analysis.PageImageAnalysis.TableCell;
import de.uka.ipd.idaho.im.analysis.PageImageAnalysis.TableRow;
import de.uka.ipd.idaho.im.analysis.PageImageAnalysis.Word;
import de.uka.ipd.idaho.im.analysis.WordImageAnalysis;
import de.uka.ipd.idaho.im.ocr.OcrEngine;
import de.uka.ipd.idaho.im.ocr.OcrEngine.OcrInstanceUnavailableException;
import de.uka.ipd.idaho.im.pdf.PdfParser.PFigure;
import de.uka.ipd.idaho.im.pdf.PdfParser.PPageContent;
import de.uka.ipd.idaho.im.pdf.PdfParser.PStream;
import de.uka.ipd.idaho.im.pdf.PdfParser.PWord;
import de.uka.ipd.idaho.im.pdf.test.PdfExtractorTest;
import de.uka.ipd.idaho.im.util.ImFontUtils;
import de.uka.ipd.idaho.im.utilities.ImageDisplayDialog;

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
	
	/* We have to do this here, as this class is the one that incurs the IcePDF
	 * dependency, and we have to make sure this system property is set before
	 * any part of IcePDF is loaded, as reading below system property sets a
	 * final boolean in IcePDF code, which incurs unwanted hard disc caching.
	 * Thus, we have to set this system property before any IcePDF class is
	 * even loaded.
	 * 
	 * Glad to learn that classes occurring in method signatures are loaded
	 * after static initializers have been executed. */
	static {
		try {
			System.setProperty("org.icepdf.core.streamcache.enabled", "false");
		} catch (Exception e) { /* let's swallow this one, especially if it is
		for security reasons, as setting this property is a behavioral thing,
		not something that any functionality would depend on */ }
		ImageIO.setUseCache(false);
	}
	
	/* make sure we have the fonts we need */
	static {
		ImFontUtils.loadFreeFonts();
	}
	
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
		this(basePath, null, imageStore, false, -1);
	}
	
	/**
	 * Constructor
	 * @param basePath the folder holding the binaries
	 * @param imageStore the page image store to store extracted page images in
	 * @param textPdfPageImageDpi the resolution for rendering text PDFs
	 */
	public PdfExtractor(File basePath, PageImageStore imageStore, int textPdfPageImageDpi) {
		this(basePath, null, imageStore, false, textPdfPageImageDpi);
	}
	
	/**
	 * Constructor
	 * @param basePath the folder holding the binaries
	 * @param imageStore the page image store to store extracted page images in
	 * @param useMultipleCores use multiple CPU cores for PDF extraction if
	 *            possible (i.e., if multiple cores available)?
	 */
	public PdfExtractor(File basePath, PageImageStore imageStore, boolean useMultipleCores) {
		this(basePath, null, imageStore, useMultipleCores, -1);
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
		this(basePath, null, imageStore, useMultipleCores, textPdfPageImageDpi);
	}
	
	/**
	 * Constructor
	 * @param basePath the folder holding the binaries
	 * @param cachePath the folder to use for data caching
	 * @param imageStore the page image store to store extracted page images in
	 */
	public PdfExtractor(File basePath, File cachePath, PageImageStore imageStore) {
		this(basePath, cachePath, imageStore, false, -1);
	}
	
	/**
	 * Constructor
	 * @param basePath the folder holding the binaries
	 * @param cachePath the folder to use for data caching
	 * @param imageStore the page image store to store extracted page images in
	 * @param textPdfPageImageDpi the resolution for rendering text PDFs
	 */
	public PdfExtractor(File basePath, File cachePath, PageImageStore imageStore, int textPdfPageImageDpi) {
		this(basePath, cachePath, imageStore, false, textPdfPageImageDpi);
	}
	
	/**
	 * Constructor
	 * @param basePath the folder holding the binaries
	 * @param cachePath the folder to use for data caching
	 * @param imageStore the page image store to store extracted page images in
	 * @param useMultipleCores use multiple CPU cores for PDF extraction if
	 *            possible (i.e., if multiple cores available)?
	 */
	public PdfExtractor(File basePath, File cachePath, PageImageStore imageStore, boolean useMultipleCores) {
		this(basePath, cachePath, imageStore, useMultipleCores, -1);
	}
	
	/**
	 * Constructor
	 * @param basePath the folder holding the binaries
	 * @param cachePath the folder to use for data caching
	 * @param imageStore the page image store to store extracted page images in
	 * @param useMultipleCores use multiple CPU cores for PDF extraction if
	 *            possible (i.e., if multiple cores available)?
	 * @param textPdfPageImageDpi the resolution for rendering text PDFs
	 */
	public PdfExtractor(File basePath, File cachePath, PageImageStore imageStore, boolean useMultipleCores, int textPdfPageImageDpi) {
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
			this.ocrEngine = new OcrEngine(this.basePath, ((cachePath == null) ? null : new File(cachePath, "OcrEngine")), (useMultipleCores ? Math.max(1, (Runtime.getRuntime().availableProcessors()-1)) : 1));
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
			doc = this.doCreateDocument(getChecksum(pdfBytes));
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
		return this.doLoadGenericPdf(doc, pdfDoc, pdfBytes, scaleFactor, pm);
	}
	
	private ImDocument doLoadGenericPdf(final ImDocument doc, final Document pdfDoc, byte[] pdfBytes, int scaleFactor, ProgressMonitor pm) throws IOException {
		
		//	build progress monitor with synchronized methods instead of synchronizing in-code
		SynchronizedProgressMonitor spm = ((pm instanceof SynchronizedProgressMonitor) ? ((SynchronizedProgressMonitor) pm) : new SynchronizedProgressMonitor(pm));
		
		//	load document structure (IcePDF is better at that ...)
		Catalog catalog = pdfDoc.getCatalog();
		
		//	parse PDF
		HashMap objects = PdfParser.getObjects(pdfBytes, pdfDoc.getSecurityManager());
		
		//	decrypt streams and strings
		PdfParser.decryptObjects(objects, pdfDoc.getSecurityManager());
		
		//	get basic page data (takes progress to 30%)
		PPageData[] pData = this.getPdfPageData(doc, true, catalog.getPageTree(), objects, spm);
		
		//	distinguish scanned from born-digital by number of figures and words per page ...
		//	... and by percentage of words overlapping with figures
		spm.setInfo("Assessing if scanned or born-digital");
		int pageCount = 0;
		int wordCount = 0;
		int figureCount = 0;
		int wordInFigureCount = 0;
		for (int p = 0; p < pData.length; p++) {
			
			//	nothing to work with
			if (pData[p] == null)
				continue;
			
			//	count words and figures
			pageCount++;
			wordCount += pData[p].words.length;
			figureCount += pData[p].figures.length;
			
			//	any use assessing overlap?
			if ((pData[p].words.length * pData[p].figures.length) == 0)
				continue;
			
			//	assess overlap of words with figures
			for (int w = 0; w < pData[p].words.length; w++) {
				for (int f = 0; f < pData[p].figures.length; f++)
					if (overlapsConsiderably(pData[p].words[w].bounds, pData[p].figures[f].bounds)) {
						wordInFigureCount++;
						break;
					}
			}
		}
		spm.setInfo(" - found " + wordCount + " words in " + pageCount + " pages");
		spm.setInfo(" - found " + figureCount + " figures in " + pageCount + " pages");
		spm.setInfo(" - found " + wordInFigureCount + " of " + wordCount + " words lying inside figures");
		
		//	do we have a text PDF or a scanned PDF?
		boolean isTextPdf;
		if (figureCount < pageCount)
			isTextPdf = true;
		else if (wordCount < (pageCount * 25))
			isTextPdf = false;
		else isTextPdf = ((2 * wordInFigureCount) < wordCount);
		spm.setInfo(" ==> " + (isTextPdf ? "born-digital" : "scanned"));
		
		//	fill pages
		if (isTextPdf)
			this.addTextPdfPages(doc, pData, pdfDoc, objects, spm);
		else this.addImagePdfPages(doc, pData, pdfDoc, catalog.getPageTree(), objects, true, (wordInFigureCount > (pageCount * 25)), scaleFactor, spm);
		
		//	finally ...
		return doc;
	}
	
	private static class PPageData {
		final int p;
		final Page pdfPage;
		final Rectangle2D.Float pdfPageBox;
		final int rotate;
		final Hashtable pdfPageResources;
		PWord[] words = null;
		PFigure[] figures = null;
		float rawPageImageDpi = -1;
		int rightPageOffset = 0;
		PPageData(int p, Page pdfPage, Float pdfPageBox, int rotate, Hashtable pdfPageResources, PWord[] words, PFigure[] figures) {
			this.p = p;
			this.pdfPage = pdfPage;
			this.pdfPageBox = pdfPageBox;
			this.rotate = rotate;
			this.words = words;
			this.figures = figures;
			this.pdfPageResources = pdfPageResources;
		}
	}
	
	private PPageData[] getPdfPageData(final ImDocument doc, final boolean getWords, final PageTree pageTree, final Map objects, ProgressMonitor pm) throws IOException {
		
		//	build progress monitor with synchronized methods instead of synchronizing in-code
		final ProgressMonitor spm = ((pm instanceof SynchronizedProgressMonitor) ? pm : new SynchronizedProgressMonitor(pm));
		
		//	get tokenizer to check and split words with
		final Tokenizer tokenizer = ((Tokenizer) doc.getAttribute(ImDocument.TOKENIZER_ATTRIBUTE, Gamta.INNER_PUNCTUATION_TOKENIZER));
		
		//	prepare working in parallel
		ParallelFor pf;
		
		//	extract page objects
		spm.setStep("Extracting page content");
		spm.setBaseProgress(0);
		spm.setProgress(0);
		spm.setMaxProgress(5);
		final Page[] pdfPages = new Page[pageTree.getNumberOfPages()];
		final byte[][] pdfPageContents = new byte[pageTree.getNumberOfPages()][];
		final Hashtable[] pdfPageResources = new Hashtable[pageTree.getNumberOfPages()];
		pf = new ParallelFor() {
			public void doFor(int p) throws Exception {
				
				//	update status display (might be inaccurate, but better than lock escalation)
				spm.setInfo("Importing page " + p + " of " + pdfPages.length);
				spm.setProgress((p * 100) / pdfPages.length);
				
				//	get page bounding box
				pdfPages[p] = pageTree.getPage(p, "");
				
				//	extract page contents to recover layout information
				System.out.println("Page entries are " + pdfPages[p].getEntries());
				Object contentsObj = PdfParser.getObject(pdfPages[p].getEntries(), "Contents", objects);
				if (contentsObj == null)
					spm.setInfo(" --> content not found");
				else {
					spm.setInfo(" --> got content");
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					if (contentsObj instanceof PStream) {
						Object filter = ((PStream) contentsObj).params.get("Filter");
						spm.setInfo("   --> stream content, filter is " + filter + " (from " + ((PStream) contentsObj).params + ")");
						PdfParser.decode(filter, ((PStream) contentsObj).bytes, ((PStream) contentsObj).params, baos, objects);
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
								PdfParser.decode(filter, ((PStream) contentObj).bytes, ((PStream) contentObj).params, baos, objects);
							}
						}
					}
					pdfPageContents[p] = baos.toByteArray();
					
					Object resourcesObj = PdfParser.getObject(pdfPages[p].getEntries(), "Resources", objects);
					if (resourcesObj == null)
						spm.setInfo(" --> resources not found");
					else {
						resourcesObj = PdfParser.dereference(resourcesObj, objects);
						spm.setInfo(" --> resources are " + resourcesObj);
						pdfPageResources[p] = ((Hashtable) resourcesObj);
					}
				}
			}
		};
		ParallelJobRunner.runParallelFor(pf, ((PdfExtractorTest.aimAtPage < 0) ? 0 : PdfExtractorTest.aimAtPage), ((PdfExtractorTest.aimAtPage < 0) ? pdfPages.length : (PdfExtractorTest.aimAtPage + 1)), (this.useMultipleCores ? -1 : 1));
		spm.setProgress(100);
		
		//	check errors
		this.checkException(pf);
		
		//	assess which chars in which fonts are actually used (no use decoding all the others, that do actually exist in some PDFs)
		if (getWords) {
			spm.setStep("Assessing font char usage");
			spm.setBaseProgress(5);
			spm.setProgress(0);
			spm.setMaxProgress(10);
			pf = new ParallelFor() {
				public void doFor(int p) throws Exception {
					
					//	nothing to work with
					if ((pdfPageContents[p] == null) || (pdfPageResources[p] == null))
						return;
					
					//	update status display (might be inaccurate, but better than lock escalation)
					spm.setInfo("Assessing font char usage in page " + p + " of " + pdfPages.length);
					spm.setProgress((p * 100) / pdfPages.length);
					
					//	assess char usage
					PdfParser.getPageWordChars(pdfPages[p].getEntries(), pdfPageContents[p], pdfPageResources[p], objects, spm);
				}
			};
			ParallelJobRunner.runParallelFor(pf, ((PdfExtractorTest.aimAtPage < 0) ? 0 : PdfExtractorTest.aimAtPage), ((PdfExtractorTest.aimAtPage < 0) ? pdfPages.length : (PdfExtractorTest.aimAtPage + 1)), (this.useMultipleCores ? -1 : 1));
			spm.setProgress(100);
			
			//	check errors
			this.checkException(pf);
		}
		
		//	extract page objects
		spm.setStep("Importing page words");
		spm.setBaseProgress(10);
		spm.setProgress(0);
		spm.setMaxProgress(30);
		final PPageData[] pData = new PPageData[pdfPages.length];
		pf = new ParallelFor() {
			public void doFor(int p) throws Exception {
				
				//	nothing to work with
				if ((pdfPageContents[p] == null) || (pdfPageResources[p] == null))
					return;
				
				//	update status display (might be inaccurate, but better than lock escalation)
				spm.setInfo("Importing page " + p + " of " + pdfPages.length);
				spm.setProgress((p * 100) / pdfPages.length);
				
				//	get page bounding box
				Rectangle2D.Float pdfPageBox = pdfPages[p].getCropBox();
				if (pdfPageBox == null)
					pdfPageBox = pdfPages[p].getMediaBox();
				
				//	check rotation
				Object rotateObj = PdfParser.dereference(pdfPages[p].getEntries().get("Rotate"), objects);
				int rotate = ((rotateObj instanceof Number) ? ((Number) rotateObj).intValue() : 0);
				spm.setInfo(" - page rotation is " + rotate);
				if ((rotate == 90) || (rotate == -90) || (rotate == 270) || (rotate == -270))
					pdfPageBox = new Rectangle2D.Float(0, 0, pdfPageBox.height, pdfPageBox.width);
				
				//	extract page contents (words only if required)
				if (getWords) {
					PPageContent pContent = PdfParser.getPageContent(pdfPages[p].getEntries(), pdfPageContents[p], pdfPageResources[p], objects, tokenizer, spm);
					System.out.println(" --> extracted " + pContent.words.length + " words and " + pContent.figures.length + " figures");
					for (int w = 0; w < pContent.words.length; w++)
						System.out.println("   - " + pContent.words[w]);
					for (int f = 0; f < pContent.figures.length; f++)
						System.out.println("   - " + pContent.figures[f]);
					spm.setInfo(" - got page content with " + pContent.words.length + " words and " + pContent.figures.length + " figures");
					
					//	store page content
					pData[p] = new PPageData(p, pdfPages[p], pdfPageBox, rotate, pdfPageResources[p], pContent.words, pContent.figures);
				}
				else {
					PFigure[] pFigures = PdfParser.getPageFigures(pdfPages[p].getEntries(), pdfPageContents[p], pdfPageResources[p], objects, spm);
					System.out.println(" --> extracted " + pFigures.length + " figures");
					for (int f = 0; f < pFigures.length; f++)
						System.out.println("   - " + pFigures[f]);
					spm.setInfo(" - got page content with " + pFigures.length + " figures");
					
					//	store page content
					pData[p] = new PPageData(p, pdfPages[p], pdfPageBox, rotate, pdfPageResources[p], new PWord[0], pFigures);
				}
			}
		};
		ParallelJobRunner.runParallelFor(pf, ((PdfExtractorTest.aimAtPage < 0) ? 0 : PdfExtractorTest.aimAtPage), ((PdfExtractorTest.aimAtPage < 0) ? pdfPages.length : (PdfExtractorTest.aimAtPage + 1)), (this.useMultipleCores ? -1 : 1));
		spm.setProgress(100);
		
		//	check errors
		this.checkException(pf);
		
		//	finally ...
		return pData;
	}
	
	private BufferedImage getFigureImage(ImDocument doc, PFigure pFigure, boolean isMainFigure, Document pdfDoc, Page pdfPage, int p, Rectangle2D.Float pdfPageBox, Hashtable xObjects, Map objects, float magnification, ProgressMonitor spm) throws IOException {
		
		//	figure consists of sub figures
		if (pFigure.subFigures != null) {
			System.out.println("Rendering figure from sub images");
			
			//	render sub figures recursively, and compute average resolution
			//	TODO maybe weight resolution by extent in dimensions, to cushion rounding errors
			BufferedImage[] pFigureSubImages = new BufferedImage[pFigure.subFigures.length];
			float widthRatioSum = 0;
			float heightRatioSum = 0;
			for (int s = 0; s < pFigure.subFigures.length; s++) {
				System.out.println(" - rendering sub image " + pFigure.subFigures[s].name);
				pFigureSubImages[s] = this.getFigureImage(null, pFigure.subFigures[s], false, pdfDoc, pdfPage, p, pdfPageBox, xObjects, objects, magnification, spm);
				System.out.println("   - sub image size is " + pFigureSubImages[s].getWidth() + "x" + pFigureSubImages[s].getHeight());
				float widthRatio = ((float) (((float) pFigureSubImages[s].getWidth()) / pFigure.subFigures[s].bounds.getWidth()));
				widthRatioSum += widthRatio;
				float heightRatio = ((float) (((float) pFigureSubImages[s].getHeight()) / pFigure.subFigures[s].bounds.getHeight()));
				heightRatioSum += heightRatio;
				float dpiRatio = ((widthRatio + heightRatio) / 2);
				float rawDpi = dpiRatio * defaultDpi;
				System.out.println("   - resolution is " + rawDpi + " DPI");
				BoundingBox dpiBox = this.getBoundingBox(pFigure.subFigures[s].bounds, pdfPageBox, dpiRatio, 0);
				System.out.println("   - bounding box at DPI is " + dpiBox);
			}
			float avgDpiRatio = ((widthRatioSum + heightRatioSum) / (2 * pFigure.subFigures.length));
			float avgRawDpi = avgDpiRatio * defaultDpi;
			System.out.println(" - average resolution is " + avgRawDpi + " DPI");
			BoundingBox avgDpiBox = this.getBoundingBox(pFigure.bounds, pdfPageBox, avgDpiRatio, 0);
			System.out.println(" - bounding box at DPI is " + avgDpiBox);
			
			//	assemble sub figures
			BufferedImage pFigureImage = new BufferedImage((avgDpiBox.right - avgDpiBox.left), (avgDpiBox.bottom - avgDpiBox.top), BufferedImage.TYPE_INT_ARGB);
			Graphics2D pFigureGraphics = pFigureImage.createGraphics();
			for (int s = 0; s < pFigure.subFigures.length; s++) {
				System.out.println("   - sub image size is " + pFigureSubImages[s].getWidth() + "x" + pFigureSubImages[s].getHeight());
				BoundingBox dpiBox = this.getBoundingBox(pFigure.subFigures[s].bounds, pdfPageBox, avgDpiRatio, 0);
				System.out.println("   - bounding box at average DPI is " + dpiBox);
				pFigureGraphics.drawImage(pFigureSubImages[s], (dpiBox.left - avgDpiBox.left), (dpiBox.top - avgDpiBox.top), null);
				//	TODO try and stretch sub images to close gaps
			}
			System.out.println(" - image rendered, size is " + pFigureImage.getWidth() + "x" + pFigureImage.getHeight());
			
			//	compute figure resolution
			float dpiRatio = ((float) ((((float) pFigureImage.getWidth()) / pFigure.bounds.getWidth()) + (((float) pFigureImage.getHeight()) / pFigure.bounds.getHeight())) / 2);
			float rawDpi = dpiRatio * defaultDpi;
			int pFigureDpi = (Math.round(rawDpi / 10) * 10);
			spm.setInfo(" - resolution computed as " + pFigureDpi + " DPI (" + rawDpi + ")");
//			
//			//	adjust figure bounds (not for sub figures, though)
//			if (isMainFigure)
//				this.cutFigureMargins(pFigure, pFigureImage);
			
			//	get figure bounds
			BoundingBox pFigureBox = this.getBoundingBox(pFigure.bounds, pdfPageBox, magnification, 0);
			spm.setInfo(" - rendering bounds are " + pFigureBox);
			
			//	add figures as supplements to document if required (synchronized !!!)
			if (doc != null) synchronized (doc) {
				ImSupplement.Figure.createFigure(doc, p, pFigureDpi, pFigureImage, pFigureBox);
			}
			
			//	we're done here
			return pFigureImage;
		}
		
		//	resolve image names against XObject dictionary
		Object pFigureKey = xObjects.get(pFigure.name);
		spm.setInfo("     - reference is " + pFigureKey);
		
		//	get actual image object
		Object pFigureData = PdfParser.dereference(pFigureKey, objects);
		spm.setInfo("     - figure data is " + pFigureData);
		if (!(pFigureData instanceof PStream)) {
			spm.setInfo("   --> strange figure data");
			return null;
		}
		
		//	decode PStream to image
		BufferedImage pFigureImage = decodeImage(pdfPage, ((PStream) pFigureData).params, ((PStream) pFigureData).bytes, objects);
		boolean pFigureImageFromPageImage = false;
		if (pFigureImage == null) {
			spm.setInfo("   --> could not decode figure");
			return null;
		}
		spm.setInfo("     - got figure sized " + pFigureImage.getWidth() + " x " + pFigureImage.getHeight() + ", type is " + pFigureImage.getType());
		
		//	check rotation
		Object rotateObj = PdfParser.dereference(pdfPage.getEntries().get("Rotate"), objects);
		int rotate = ((rotateObj instanceof Number) ? ((Number) rotateObj).intValue() : 0);
		spm.setInfo("     - page rotation is " + rotate);
		
		//	catch large rotations (above some 15°) if countered by page rotation
		if ((Math.abs(pFigure.rotation) > (Math.PI / 12)) && (Math.abs(((180.0 / Math.PI) * pFigure.rotation) + rotate) < 1))
			spm.setInfo("     - figure not rotated by " + ((180.0 / Math.PI) * pFigure.rotation) + "°");
		
		//	interpret figure rotation (if above some 0.1°)
		else if (Math.abs(pFigure.rotation) > 0.0005) {
			BufferedImage rImage;
			
			//	rotation somewhere around 90° or -90°, have to flip dimensions
			if ((Math.abs(pFigure.rotation / Math.PI) > 0.25) && (Math.abs(pFigure.rotation / Math.PI) < 0.75))
				rImage = new BufferedImage(pFigureImage.getHeight(), pFigureImage.getWidth(), ((pFigureImage.getType() == BufferedImage.TYPE_CUSTOM) ? BufferedImage.TYPE_INT_ARGB : pFigureImage.getType()));
			
			//	rotation somewhere around 270° or -270°, have to flip dimensions
			else if ((Math.abs(pFigure.rotation / Math.PI) > 1.25) && (Math.abs(pFigure.rotation / Math.PI) < 1.75))
				rImage = new BufferedImage(pFigureImage.getHeight(), pFigureImage.getWidth(), ((pFigureImage.getType() == BufferedImage.TYPE_CUSTOM) ? BufferedImage.TYPE_INT_ARGB : pFigureImage.getType()));
			
			//	figure rather upright, retain dimensions
			else rImage = new BufferedImage(pFigureImage.getWidth(), pFigureImage.getHeight(), ((pFigureImage.getType() == BufferedImage.TYPE_CUSTOM) ? BufferedImage.TYPE_INT_ARGB : pFigureImage.getType()));
			
			//	perform rotation
			Graphics2D rImageGraphics = rImage.createGraphics();
			rImageGraphics.setColor(Color.WHITE);
			rImageGraphics.fillRect(0, 0, rImage.getWidth(), rImage.getHeight());
			rImageGraphics.translate(((rImage.getWidth() - pFigureImage.getWidth()) / 2), ((rImage.getHeight() - pFigureImage.getHeight()) / 2));
			rImageGraphics.rotate(pFigure.rotation, (pFigureImage.getWidth() / 2), (pFigureImage.getHeight() / 2));
			rImageGraphics.drawRenderedImage(pFigureImage, null);
			rImageGraphics.dispose();
			pFigureImage = rImage;
//			pFigureImage = Imaging.rotateImage(pFigureImage, pFigure.rotation);
			spm.setInfo("     - figure rotated by " + ((180.0 / Math.PI) * pFigure.rotation) + "°");
			spm.setInfo("     - figure sized " + pFigureImage.getWidth() + " x " + pFigureImage.getHeight() + " now, type is " + pFigureImage.getType());
		}
		
		//	correct right side left ...
		else if (pFigure.rightSideLeft) {
			BufferedImage fImage = new BufferedImage(pFigureImage.getWidth(), pFigureImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
			Graphics2D fImageGraphics = fImage.createGraphics();
			fImageGraphics.setColor(Color.WHITE);
			fImageGraphics.fillRect(0, 0, fImage.getWidth(), fImage.getHeight());
			fImageGraphics.translate(fImage.getWidth(), 0);
			fImageGraphics.scale(-1, 1);
			fImageGraphics.drawRenderedImage(pFigureImage, null);
			fImageGraphics.dispose();
			pFigureImage = fImage;
			spm.setInfo("     - right side left flip corrected");
		}
		
		//	... or upside down
		else if (pFigure.upsideDown) {
			BufferedImage fImage = new BufferedImage(pFigureImage.getWidth(), pFigureImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
			Graphics2D fImageGraphics = fImage.createGraphics();
			fImageGraphics.setColor(Color.WHITE);
			fImageGraphics.fillRect(0, 0, fImage.getWidth(), fImage.getHeight());
			fImageGraphics.translate(0, fImage.getHeight());
			fImageGraphics.scale(1, -1);
			fImageGraphics.drawRenderedImage(pFigureImage, null);
			fImageGraphics.dispose();
			pFigureImage = fImage;
			spm.setInfo("     - upside down flip corrected");
		}
		
		//	paint image on white canvas for normalization
		else {
			BufferedImage fImage = new BufferedImage(pFigureImage.getWidth(), pFigureImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
			Graphics2D fImageGraphics = fImage.createGraphics();
			fImageGraphics.setColor(Color.WHITE);
			fImageGraphics.fillRect(0, 0, fImage.getWidth(), fImage.getHeight());
			fImageGraphics.drawRenderedImage(pFigureImage, null);
			fImageGraphics.dispose();
			pFigureImage = fImage;
			spm.setInfo("     - image type corrected");
		}
		
		//	compute figure resolution
		float dpiRatio = ((float) ((((float) pFigureImage.getWidth()) / pFigure.bounds.getWidth()) + (((float) pFigureImage.getHeight()) / pFigure.bounds.getHeight())) / 2);
		float rawDpi = dpiRatio * defaultDpi;
		int pFigureDpi = (Math.round(rawDpi / 10) * 10);
		spm.setInfo("     - resolution computed as " + pFigureDpi + " DPI (" + rawDpi + ")");
		
		//	TODO if we're above 600 DPI, scale down half, third, etc., until at most 600 DPI
		
		//	test for blank image only now, as we have all the data for the page image fallback
		if (this.checkEmptyImage(pFigureImage)) {
			pFigureImage = this.extractFigureFromPageImage(pdfDoc, p, pdfPageBox, rotate, pFigureDpi, pFigure.bounds);
			pFigureImageFromPageImage = true;
			spm.setInfo("     --> blank decoded figure re-rendered as part of page image");
		}
		
		Object csObj = PdfParser.dereference(((PStream) pFigureData).params.get("ColorSpace"), objects);
		if (csObj != null) {
			spm.setInfo("     - color space is " + csObj.toString());
			/* If we've used the fallback already, no use doing so again */
			if (pFigureImageFromPageImage) {}
			/* If color space is DeviceCMYK, render page image to figure
			 * resolution and cut out image: images with color space
			 * DeviceCMYK come out somewhat differently colored than in
			 * Acrobat, but we cannot seem to do anything about this -
			 * IcePDF distorts them as well, if more in single image
			 * rendering than in the page images it generates. */
			else if ("DeviceCMYK".equals(csObj.toString())) {
				pFigureImage = this.extractFigureFromPageImage(pdfDoc, p, pdfPageBox, rotate, pFigureDpi, pFigure.bounds);
				pFigureImageFromPageImage = true;
				spm.setInfo("     --> figure re-rendered as part of page image");
			}
			/* If color space is Separation, values represent color
			 * intensity rather than brightness, which means 1.0 is
			 * black and 0.0 is white. This means the have to invert
			 * brightness to get the actual image. IcePDF gets this
			 * wrong as well, with images coming out white on black
			 * rather than the other way around, so we have to do the
			 * correction ourselves. */
			else if ((csObj instanceof Vector) && (((Vector) csObj).size() == 4) && ((Vector) csObj).get(0).equals("Separation")) {
				this.invertFigureBrightness(pFigureImage);
				pFigureImageFromPageImage = true;
				spm.setInfo("     --> figure brightness inverted for additive 'Separation' color space");
			}
			/* If color space is Indexed, IcePDF seems to do a lot better
			 * in page image rendering than in single-image rendering. */
			else if ((csObj instanceof Vector) && (((Vector) csObj).size() == 4) && ((Vector) csObj).get(0).equals("Indexed")) {
				pFigureImage = this.extractFigureFromPageImage(pdfDoc, p, pdfPageBox, rotate, pFigureDpi, pFigure.bounds);
				pFigureImageFromPageImage = true;
				spm.setInfo("     --> figure re-rendered as part of page image");
			}
		}
		
		//	if we're grayscale and extremely dark, invert white on black
		this.correctInvertedGaryscale(pFigureImage);
//		
//		//	adjust figure bounds (not for sub figures, though)
//		if (isMainFigure)
//			this.cutFigureMargins(pFigure, pFigureImage);
		
		//	convert bounds, as PDF Y coordinate is bottom-up, whereas Java, JavaScript, etc. Y coordinate is top-down
		BoundingBox pFigureBox = this.getBoundingBox(pFigure.bounds, pdfPageBox, magnification, rotate);
		spm.setInfo("     - rendering bounds are " + pFigureBox);
		
		//	add figures as supplements to document if required (synchronized !!!)
		if (doc != null) synchronized (doc) {
			ImSupplement.Figure.createFigure(doc, p, pFigureDpi, pFigureImage, pFigureBox);
		}
		
		//	finally ...
		return pFigureImage;
	}
//	
//	private void cutFigureMargins(PFigure pFigure, BufferedImage pFigureImage) {
//		ImagePartRectangle pFigureContentBounds = Imaging.getContentBox(Imaging.wrapImage(pFigureImage, null));
//		if ((pFigureContentBounds.getTopRow() > 0) || (pFigureContentBounds.getBottomRow() < pFigureImage.getHeight())) {
//			float topCut = (((float) (pFigureContentBounds.getTopRow() * pFigure.bounds.getHeight())) / pFigureImage.getHeight());
//			System.out.println("Top cut is " + topCut);
//			float bottomCut = (((float) ((pFigureImage.getHeight() - pFigureContentBounds.getBottomRow()) * pFigure.bounds.getHeight())) / pFigureImage.getHeight());
//			System.out.println("Bottom cut is " + bottomCut);
//			pFigure.bounds.setRect(pFigure.bounds.getX(), (pFigure.bounds.getY() + bottomCut), pFigure.bounds.getWidth(), (pFigure.bounds.getHeight() - bottomCut - topCut));
//		}
//	}
	
	private static final int defaultDpi = 72; // default according to PDF specification
	
	private static final int[] scaleUpDpi = {150, 200, 300, 400, 500, 600}; // steps for scaling up if scanned page images come out at 72 DPI
	
	/* using double screen DPI improves scaling behavior at common zoom levels */
	private static final int defaultTextPdfPageImageDpi = (2 * 96); // looks a lot better
//	private static final int defaultTextPdfPageImageDpi = 150; // looks a lot better
	private static final int minAveragePageWords = 25; // this should be exceeded by every digital-born PDF
	
	//	A4 paper format in inches: 8.27 x 11.66
	private static final float a4inchWidth = 8.27f;
	private static final float a4inchHeigth = 11.66f;
	
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
			doc = this.doCreateDocument(getChecksum(pdfBytes));
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
		
		//	build progress monitor with synchronized methods instead of synchronizing in-code
		SynchronizedProgressMonitor spm = ((pm instanceof SynchronizedProgressMonitor) ? ((SynchronizedProgressMonitor) pm) : new SynchronizedProgressMonitor(pm));
		
		//	load document structure (IcePDF is better at that ...)
		Catalog catalog = pdfDoc.getCatalog();
		
		//	parse PDF
		HashMap objects = PdfParser.getObjects(pdfBytes, pdfDoc.getSecurityManager());
		
		//	decrypt streams and strings
		PdfParser.decryptObjects(objects, pdfDoc.getSecurityManager());
		
		//	get basic page data (takes progress to 30%)
		PPageData[] pData = this.getPdfPageData(doc, true, catalog.getPageTree(), objects, spm);
		
		//	fill pages
		this.addTextPdfPages(doc, pData, pdfDoc, objects, spm);
		
		//	finally ...
		return doc;
	}
	
	private static final boolean DEBUG_EXTRACT_FIGURES = true;
	private static final boolean DEBUG_MERGE_WORDS = true;
	
	private void addTextPdfPages(final ImDocument doc, final PPageData[] pData, final Document pdfDoc, final Map objects, final SynchronizedProgressMonitor spm) throws IOException {
		final float magnification = (((float) this.textPdfPageImageDpi) / defaultDpi);
		
		//	get tokenizer to check and split words with
		final Tokenizer tokenizer = ((Tokenizer) doc.getAttribute(ImDocument.TOKENIZER_ATTRIBUTE, Gamta.INNER_PUNCTUATION_TOKENIZER));
		
		//	prepare working in parallel
		ParallelFor pf;
		
		//	extract page objects
		spm.setStep("Sanitizing page words");
		spm.setBaseProgress(30);
		spm.setProgress(0);
		spm.setMaxProgress(35);
		final Set[] pInFigureWords = new Set[pData.length];
		pf = new ParallelFor() {
			public void doFor(int p) throws Exception {
				
				//	nothing to work with
				if ((pData[p] == null) || (pData[p].words == null) || (pData[p].figures == null))
					return;
				
				//	update status display (might be inaccurate, but better than lock escalation)
				spm.setProgress((p * 100) / pData.length);
				System.out.println("Got page content with " + pData[p].words.length + " words and " + pData[p].figures.length + " figures");
				
				//	sanitize words (if any)
				if (pData[p].words.length != 0) {
					spm.setInfo("Sanitizing words in page " + p + " of " + pData.length);
					checkPageWords(pData[p], tokenizer, spm);
				}
				
				//	preserve figures embedded in pages
				if (pData[p].figures.length != 0) {
					spm.setInfo("Storing figures in page " + p + " of " + pData.length);
					storeFiguresAsSupplements(doc, pData[p], pdfDoc, objects, magnification, spm);
				}
				
				//	collect words that lie inside images
				if (pData[p].figures.length != 0) {
					spm.setInfo("Getting in-figures words in page " + p + " of " + pData.length);
					for (int f = 0; f < pData[p].figures.length; f++) {
						
						//	get top and bottom, and observe inversions
						int figTop = ((int) Math.ceil(Math.max(pData[p].figures[f].bounds.getMinY(), pData[p].figures[f].bounds.getMaxY())));
						int figBottom = ((int) Math.floor(Math.min(pData[p].figures[f].bounds.getMinY(), pData[p].figures[f].bounds.getMaxY())));
						
						//	count words per pixel line
						boolean figureClear = true;
						int[] rowWordPixels = new int[figTop + 1 - figBottom];
						Arrays.fill(rowWordPixels, 0);
						for (int w = 0; w < pData[p].words.length; w++)
							if (pData[p].figures[f].bounds.intersects(pData[p].words[w].bounds)) {
								figureClear = false;
								for (int r = ((int) Math.floor(pData[p].words[w].bounds.getMinY())); r < Math.ceil(pData[p].words[w].bounds.getMaxY()); r++) {
									if (r < figBottom)
										continue;
									if (r >= figTop)
										continue;
									rowWordPixels[r - figBottom] += ((int) Math.round(pData[p].words[w].bounds.getWidth()));
									
								}
							}
						
						//	do we need to look any further?
						if (figureClear)
							continue;
						
						//	check words, and compute image margin
						double wFigTop = Math.max(pData[p].figures[f].bounds.getMinY(), pData[p].figures[f].bounds.getMaxY());
						double wFigBottom = Math.min(pData[p].figures[f].bounds.getMinY(), pData[p].figures[f].bounds.getMaxY());
						for (int w = 0; w < pData[p].words.length; w++)
							if (pData[p].figures[f].bounds.intersects(pData[p].words[w].bounds)) {
								
								//	count word density inside image
								int wordRowCount = 0;
								int wordRowPixelCount = 0;
								for (int r = ((int) Math.floor(pData[p].words[w].bounds.getMinY())); r < Math.ceil(pData[p].words[w].bounds.getMaxY()); r++) {
									if (r < figBottom)
										continue;
									if (r >= figTop)
										continue;
									wordRowCount++;
									wordRowPixelCount += rowWordPixels[r - figBottom];
								}
								
								//	overlap is marginal, ignore word
								if (wordRowCount == 0)
									continue;
								
								//	over 50% of word row occupied with words, likely text rather than numbering
								if (((wordRowPixelCount / wordRowCount) * 2) > Math.abs(pData[p].figures[f].bounds.getWidth())) {
									if (pData[p].words[w].bounds.getMaxY() < ((figTop + figBottom) / 2))
										wFigBottom = Math.max(wFigBottom, (pData[p].words[w].bounds.getMaxY() + 0.5));
									if (pData[p].words[w].bounds.getMinY() > ((figTop + figBottom) / 2))
										wFigTop = Math.min(wFigTop, (pData[p].words[w].bounds.getMinY() - 0.5));
									continue;
								}
								
								//	mark word for removal
								if (pInFigureWords[p] == null)
									pInFigureWords[p] = new HashSet();
								pInFigureWords[p].add(pData[p].words[w]);
							}
						
						//	cut top and bottom of figure bounds (figure proper was stored above)
						pData[p].figures[f].bounds.setRect(pData[p].figures[f].bounds.getMinX(), wFigBottom, pData[p].figures[f].bounds.getWidth(), (wFigTop - wFigBottom));
					}
					spm.setInfo("Found " + ((pInFigureWords[p] == null) ? "no" : ("" + pInFigureWords[p].size())) + " in-figures words in page " + p + " of " + pData.length);
				}
			}
		};
		ParallelJobRunner.runParallelFor(pf, ((PdfExtractorTest.aimAtPage < 0) ? 0 : PdfExtractorTest.aimAtPage), ((PdfExtractorTest.aimAtPage < 0) ? pData.length : (PdfExtractorTest.aimAtPage + 1)), (this.useMultipleCores ? -1 : 1));
		spm.setProgress(100);
		
		//	check errors
		this.checkException(pf);
		
		//	check plausibility
		if (PdfExtractorTest.aimAtPage == -1) {
			int docWordCount = 0;
			for (int p = 0; p < pData.length; p++)
				docWordCount += pData[p].words.length;
			if ((docWordCount / pData.length) < minAveragePageWords)
				throw new IOException("Too few words per page (" + docWordCount + " on " + pData.length + " pages, less than " + minAveragePageWords + ")");
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
			ImFont imFont = new ImFont(doc, pFont.name, pFont.bold, pFont.italics, pFont.serif);
			for (int c = 0; c < charCodes.length; c++) {
				String charStr = pFont.getUnicode(charCodes[c]);
				BufferedImage charImage = pFont.getCharImage(charCodes[c]);
				imFont.addCharacter(charCodes[c], charStr, (((charImage == null) || ((charImage.getWidth() * charImage.getHeight()) == 0)) ? null : ImFont.scaleCharImage(charImage)));
				System.out.println("   - added char " + charCodes[c] + ": '" + charStr + "', image is " + charImage);
			}
			if (imFont.getCharacterCount() == 0)
				spm.setInfo(" ==> no custom characters");
			else {
				doc.addFont(imFont);
				spm.setInfo(" ==> font stored");
			}
		}
		
		//	assess number punctuation
		spm.setStep("Assessing number punctuation");
		spm.setBaseProgress(35);
		spm.setProgress(0);
		spm.setMaxProgress(36);
		final Tokenizer numberTokenizer = getNumberTokenizer(pData, tokenizer, spm);
		
		//	split words only now
		spm.setStep("Splitting page words");
		spm.setBaseProgress(36);
		spm.setProgress(0);
		spm.setMaxProgress(40);
		pf = new ParallelFor() {
			public void doFor(int p) throws Exception {
				
				//	nothing to work with
				if ((pData[p] == null) || (pData[p].words == null) || (pData[p].words.length == 0))
					return;
				
				//	update status display (might be inaccurate, but better than lock escalation)
				spm.setInfo("Splitting words in page " + p + " of " + pData.length);
				spm.setProgress((p * 100) / pData.length);
				
				//	split words that tokenize apart, splitting bounding box based on font measurement
				ArrayList pWordList = new ArrayList();
				for (int w = 0; w < pData[p].words.length; w++) {
					
					//	check tokenization
					TokenSequence pWordTokens = (Gamta.isNumber(pData[p].words[w].str) ? numberTokenizer.tokenize(pData[p].words[w].str) : tokenizer.tokenize(pData[p].words[w].str));
					if (pWordTokens.size() < 2) {
						pWordList.add(pData[p].words[w]);
						continue;
					}
					System.out.println(" - splitting " + pData[p].words[w].str);
					
					//	get width for each token at word font size
					String[] splitCharCodes = new String[pWordTokens.size()];
					String[] splitTokens = new String[pWordTokens.size()];
					float[] splitTokenWidths = new float[pWordTokens.size()];
					int fontStyle = Font.PLAIN;
					if (pData[p].words[w].bold)
						fontStyle = (fontStyle | Font.BOLD);
					if (pData[p].words[w].italics)
						fontStyle = (fontStyle | Font.ITALIC);
					Font pWordFont = getFont(pData[p].words[w].font.name, fontStyle, pData[p].words[w].serif, Math.round(((float) pData[p].words[w].fontSize)));
					float splitTokenWidthSum = 0;
					int splitCharCodeStart = 0;
					for (int s = 0; s < splitTokens.length; s++) {
						splitTokens[s] = pWordTokens.valueAt(s);
						splitCharCodes[s] = ""; // have to do it this way, as char code string might have different length than Unicode string
						for (int splitCharCodeLength = 0; splitCharCodeLength < splitTokens[s].length();) {
							char charCode = pData[p].words[w].charCodes.charAt(splitCharCodeStart++);
							splitCharCodes[s] += ("" + charCode);
							splitCharCodeLength += (((int) charCode) / 256);
						}
						TextLayout tl = new TextLayout(splitTokens[s], pWordFont, new FontRenderContext(null, false, true));
						splitTokenWidths[s] = ((float) tl.getBounds().getWidth());
						splitTokenWidthSum += splitTokenWidths[s];
					}
					
					//	left-right words
					if (pData[p].words[w].fontDirection == PWord.LEFT_RIGHT_FONT_DIRECTION) {
						
						//	store split result, splitting word bounds accordingly
						float pWordWidth = ((float) (pData[p].words[w].bounds.getMaxX() - pData[p].words[w].bounds.getMinX()));
						float splitTokenLeft = ((float) pData[p].words[w].bounds.getMinX());
						for (int s = 0; s < splitTokens.length; s++) {
							float splitTokenWidth = ((pWordWidth * splitTokenWidths[s]) / splitTokenWidthSum);
							PWord spWord = new PWord(splitCharCodes[s], splitTokens[s], new Rectangle2D.Float(splitTokenLeft, ((float) pData[p].words[w].bounds.getMinY()), splitTokenWidth, ((float) pData[p].words[w].bounds.getHeight())), pData[p].words[w].fontSize, pData[p].words[w].fontDirection, pData[p].words[w].font);
							pWordList.add(spWord);
							if ((pInFigureWords[p] != null) && pInFigureWords[p].contains(pData[p].words[w]))
								pInFigureWords[p].add(spWord);
							splitTokenLeft += splitTokenWidth;
							System.out.println(" --> split word part " + spWord.str + ", bounds are " + spWord.bounds);
						}
					}
					
					//	bottom-up words
					else if (pData[p].words[w].fontDirection == PWord.BOTTOM_UP_FONT_DIRECTION) {
						
						//	store split result, splitting word bounds accordingly
						float pWordWidth = ((float) (pData[p].words[w].bounds.getMaxY() - pData[p].words[w].bounds.getMinY()));
						float splitTokenLeft = ((float) pData[p].words[w].bounds.getMinY());
						for (int s = 0; s < splitTokens.length; s++) {
							float splitTokenWidth = ((pWordWidth * splitTokenWidths[s]) / splitTokenWidthSum);
							PWord spWord = new PWord(splitCharCodes[s], splitTokens[s], new Rectangle2D.Float(((float) pData[p].words[w].bounds.getMinX()), splitTokenLeft, ((float) pData[p].words[w].bounds.getWidth()), splitTokenWidth), pData[p].words[w].fontSize, pData[p].words[w].fontDirection, pData[p].words[w].font);
							pWordList.add(spWord);
							if ((pInFigureWords[p] != null) && pInFigureWords[p].contains(pData[p].words[w]))
								pInFigureWords[p].add(spWord);
							splitTokenLeft += splitTokenWidth;
							System.out.println(" --> split word part " + spWord.str + ", bounds are " + spWord.bounds);
						}
					}
					
					//	top-down words
					else if (pData[p].words[w].fontDirection == PWord.TOP_DOWN_FONT_DIRECTION) {
						
						//	store split result, splitting word bounds accordingly
						float pWordWidth = ((float) (pData[p].words[w].bounds.getMaxY() - pData[p].words[w].bounds.getMinY()));
						float splitTokenLeft = ((float) pData[p].words[w].bounds.getMaxY());
						for (int s = 0; s < splitTokens.length; s++) {
							float splitTokenWidth = ((pWordWidth * splitTokenWidths[s]) / splitTokenWidthSum);
							PWord spWord = new PWord(splitCharCodes[s], splitTokens[s], new Rectangle2D.Float(((float) pData[p].words[w].bounds.getMinX()), (splitTokenLeft - splitTokenWidth), ((float) pData[p].words[w].bounds.getWidth()), splitTokenWidth), pData[p].words[w].fontSize, pData[p].words[w].fontDirection, pData[p].words[w].font);
							pWordList.add(spWord);
							if ((pInFigureWords[p] != null) && pInFigureWords[p].contains(pData[p].words[w]))
								pInFigureWords[p].add(spWord);
							splitTokenLeft -= splitTokenWidth;
							System.out.println(" --> split word part " + spWord.str + ", bounds are " + spWord.bounds);
						}
					}
				}
				
				//	refresh PWord array
				if (pWordList.size() != pData[p].words.length)
					pData[p].words = ((PWord[]) pWordList.toArray(new PWord[pWordList.size()]));
				pWordList = null;
				
				//	give status update
				if (pData[p].words.length == 0)
					spm.setInfo(" --> empty page");
				else spm.setInfo(" --> got " + pData[p].words.length + " words in PDF");
			}
		};
		ParallelJobRunner.runParallelFor(pf, ((PdfExtractorTest.aimAtPage < 0) ? 0 : PdfExtractorTest.aimAtPage), ((PdfExtractorTest.aimAtPage < 0) ? pData.length : (PdfExtractorTest.aimAtPage + 1)), (this.useMultipleCores ? -1 : 1));
		spm.setProgress(100);
		
		//	put PDF words into images
		spm.setStep("Generating page images");
		spm.setBaseProgress(40);
		spm.setProgress(0);
		spm.setMaxProgress(70);
		final BufferedImage[] pageImages = new BufferedImage[pData.length];
		final String[] imageNames = new String[pData.length];
		final int[] imageDPIs = new int[pData.length];
		final boolean[] blockFlipInPage = new boolean[pData.length];
		final BoundingBox[] pageBounds = new BoundingBox[pData.length];
		pf = new ParallelFor() {
			public void doFor(int p) throws Exception {
				
				//	nothing to work with
				if ((pData[p] == null) || (pData[p].words == null))
					return;
				
				//	update status display (might be inaccurate, but better than lock escalation)
				spm.setInfo("Extracting image of page " + p + " of " + pData.length);
				spm.setProgress((p * 100) / pData.length);
				
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
				for (int w = 0; w < pData[p].words.length; w++) {
					
					//	convert bounds, as PDF Y coordinate is bottom-up, whereas Java, JavaScript, etc. Y coordinate is top-down
					BoundingBox wb = getBoundingBox(pData[p].words[w].bounds, pData[p].pdfPageBox, magnification, pData[p].rotate);
					
					//	test for vertical words
					if (pData[p].words[w].fontDirection == PWord.BOTTOM_UP_FONT_DIRECTION) {
						bubLeft = Math.min(bubLeft, wb.left);
						bubRight = Math.max(bubRight, wb.right);
						bubTop = Math.min(bubTop, wb.top);
						bubBottom = Math.max(bubBottom, wb.bottom);
						buWords.add(pData[p].words[w]);
					}
					else if (pData[p].words[w].fontDirection == PWord.TOP_DOWN_FONT_DIRECTION) {
						tdbLeft = Math.min(tdbLeft, wb.left);
						tdbRight = Math.max(tdbRight, wb.right);
						tdbTop = Math.min(tdbTop, wb.top);
						tdbBottom = Math.max(tdbBottom, wb.bottom);
						tdWords.add(pData[p].words[w]);
					}
				}
				
				//	page image generated before
				if (imageStore.isPageImageAvailable(doc.docId, p)) {
					PageImage pi = imageStore.getPageImage(doc.docId, p);
					if (pi.currentDpi == defaultTextPdfPageImageDpi) {
						pageImages[p] = pi.image;
						imageNames[p] = PageImage.getPageImageName(doc.docId, p);
						imageDPIs[p] = pi.currentDpi;
						pageBounds[p] = new BoundingBox(0, pageImages[p].getWidth(), 0, pageImages[p].getHeight());
						spm.setInfo(" --> loaded page image generated earlier, sized " + pageImages[p].getWidth() + " x " + pageImages[p].getHeight() + " at " + pi.currentDpi + " DPI");
						
						//	we do have a significant bottom-up block, flip its words (image is already extended here, so test against scaled page bounds)
						if ((bubLeft < bubRight) && (bubTop < bubBottom) && (bubLeft < ((pData[p].pdfPageBox.getWidth() * magnification) / 3)) && (bubRight > ((pData[p].pdfPageBox.getWidth() * magnification * 2) / 3)) && (bubTop < ((pData[p].pdfPageBox.getHeight() * magnification) / 3)) && (bubBottom > ((pData[p].pdfPageBox.getHeight() * magnification * 2) / 3))) {
							spm.setInfo("Got bottom-up block with " + buWords.size() + " words to flip");
							flipBlockWords(pData[p].pdfPageBox, pData[p].words, buWords, PWord.BOTTOM_UP_FONT_DIRECTION);
							blockFlipInPage[p] = true;
						}
						
						//	we do have a significant top-down block, flip its words (image is already extended here, so test against scaled page bounds)
						else if ((tdbLeft < tdbRight) && (tdbTop < tdbBottom) && (tdbLeft < ((pData[p].pdfPageBox.getWidth() * magnification) / 3)) && (tdbRight > ((pData[p].pdfPageBox.getWidth() * magnification * 2) / 3)) && (tdbTop < ((pData[p].pdfPageBox.getHeight() * magnification) / 3)) && (tdbBottom > ((pData[p].pdfPageBox.getHeight() * magnification * 2) / 3))) {
							spm.setInfo("Got top-down block with " + tdWords.size() + " words to flip");
							flipBlockWords(pData[p].pdfPageBox, pData[p].words, tdWords, PWord.TOP_DOWN_FONT_DIRECTION);
							blockFlipInPage[p] = true;
						}
					}
					else spm.setInfo(" --> could not use page image generated earlier, resolution mismatch at " + pi.currentDpi + " DPI");
				}
				
				//	generate page image
				if (pageImages[p] == null) {
					spm.setInfo(" - generating page image");
					synchronized (pdfDoc) {
						pdfDoc.getPageTree().getPage(p, "").init(); // there might have been a previous call to reduceMemory() ...
						pageImages[p] = ((BufferedImage) pdfDoc.getPageImage(p, GraphicsRenderingHints.SCREEN, Page.BOUNDARY_CROPBOX, 0, magnification));
					}
					if (pageImages[p] == null) {
						spm.setInfo(" --> page image generation failed");
						throw new IOException("Could not generate image for page " + p);
					}
					spm.setInfo(" - got page image sized " + pageImages[p].getWidth() + " x " + pageImages[p].getHeight());
					
					//	erase words from IcePDF (can have faulty bounding boxes ...), and paint our own words onto the image
					int cleaningTolerance = (textPdfPageImageDpi / 25); // somewhat less than one mm ... 
					PageText pdfPageText = pData[p].pdfPage.getText();
					ArrayList pdfPageLines = pdfPageText.getPageLines();
					Graphics2D rg = pageImages[p].createGraphics();
					rg.setColor(Color.WHITE);
					for (int l = 0; l < pdfPageLines.size(); l++) {
						LineText pdfLineText = ((LineText) pdfPageLines.get(l));
						ArrayList pdfLineWords = pdfLineText.getWords();
						for (int w = 0; w < pdfLineWords.size(); w++) {
							WordText pdfWordText = ((WordText) pdfLineWords.get(w));
							
							//	skip over in-figure words
							if (pData[p].figures.length != 0) {
								for (int f = 0; f < pData[p].figures.length; f++)
									if (pData[p].figures[f].bounds.intersects(pdfWordText.getBounds())) {
										pdfWordText = null;
										break;
									}
								if (pdfWordText == null)
									continue;
							}
							
							//	convert bounds, as PDF Y coordinate is bottom-up, whereas Java, JavaScript, etc. Y coordinate is top-down
							BoundingBox wb = getBoundingBox(pdfWordText.getBounds(), pData[p].pdfPageBox, magnification, 0);
							rg.fillRect((wb.left - (cleaningTolerance / 2)), (wb.top - (cleaningTolerance / 2)), (wb.right - wb.left + cleaningTolerance), (wb.bottom - wb.top + cleaningTolerance));
						}
					}
					
					//	clean IcePDF cache (only now, after cleaning up words)
					synchronized (pdfDoc) {
						pdfDoc.getPageTree().getPage(p, "").reduceMemory();
					}
					
					//	clean up black page margins (outmost two pixels)
					rg.fillRect(0, 0, pageImages[p].getWidth(), 2);
					rg.fillRect(0, (pageImages[p].getHeight() - 2), pageImages[p].getWidth(), 2);
					rg.fillRect(0, 0, 2, pageImages[p].getHeight());
					rg.fillRect((pageImages[p].getWidth() - 2), 0, 2, pageImages[p].getHeight());
					
					//	clean all own words
					for (int w = 0; w < pData[p].words.length; w++) {
						
						//	don't clean in-figure words, as that destroys figure images
						if ((pInFigureWords[p] != null) && pInFigureWords[p].contains(pData[p].words[w]))
							continue;
						
						//	convert bounds, as PDF Y coordinate is bottom-up, whereas Java, JavaScript, etc. Y coordinate is top-down
						BoundingBox wb = getBoundingBox(pData[p].words[w].bounds, pData[p].pdfPageBox, magnification, pData[p].rotate);
						System.out.println("Word '" + pData[p].words[w].str + "' @ " + wb);
						
						//	clean background
						rg.setColor(Color.WHITE);
						rg.fillRect(wb.left, wb.top, (wb.right - wb.left + 1), (wb.bottom - wb.top));
					}
					
					//	we do have a significant bottom-up block, flip it
					if ((bubLeft < bubRight) && (bubTop < bubBottom) && (bubLeft < (pageImages[p].getWidth() / 3)) && (bubRight > ((pageImages[p].getWidth() * 2) / 3)) && (bubTop < (pageImages[p].getHeight() / 3)) && (bubBottom > ((pageImages[p].getHeight() * 2) / 3))) {
						spm.setInfo("Got bottom-up block with " + buWords.size() + " words to flip");
						pageImages[p] = flipBlockImage(pageImages[p], pData[p].pdfPageBox, bubLeft, bubRight, bubTop, bubBottom, pData[p].words, PWord.BOTTOM_UP_FONT_DIRECTION, magnification);
						rg = pageImages[p].createGraphics();
						flipBlockWords(pData[p].pdfPageBox, pData[p].words, buWords, PWord.BOTTOM_UP_FONT_DIRECTION);
						blockFlipInPage[p] = true;
					}
					
					//	we do have a significant top-down block, flip it
					else if ((tdbLeft < tdbRight) && (tdbTop < tdbBottom) && (tdbLeft < (pageImages[p].getWidth() / 3)) && (tdbRight > ((pageImages[p].getWidth() * 2) / 3)) && (tdbTop < (pageImages[p].getHeight() / 3)) && (tdbBottom > ((pageImages[p].getHeight() * 2) / 3))) {
						spm.setInfo("Got top-down block with " + tdWords.size() + " words to flip");
						pageImages[p] = flipBlockImage(pageImages[p], pData[p].pdfPageBox, tdbLeft, tdbRight, tdbTop, tdbBottom, pData[p].words, PWord.TOP_DOWN_FONT_DIRECTION, magnification);
						rg = pageImages[p].createGraphics();
						flipBlockWords(pData[p].pdfPageBox, pData[p].words, buWords, PWord.TOP_DOWN_FONT_DIRECTION);
						blockFlipInPage[p] = true;
					}
					
					//	TODO measure orientation of figure images as well, and flip them
					
					//	TODO if whole page to flip, flip page image as well
					
					//	paint own words (_including_ in-figure words, as IcePDF might have messed up)
					for (int w = 0; w < pData[p].words.length; w++) {
						
						//	convert bounds, as PDF Y coordinate is bottom-up, whereas Java, JavaScript, etc. Y coordinate is top-down
						BoundingBox wb = getBoundingBox(pData[p].words[w].bounds, pData[p].pdfPageBox, magnification, pData[p].rotate);
						
						//	prepare font
						rg.setColor(Color.BLACK);
						int fontStyle = Font.PLAIN;
						if (pData[p].words[w].bold)
							fontStyle = (fontStyle | Font.BOLD);
						if (pData[p].words[w].italics)
							fontStyle = (fontStyle | Font.ITALIC);
						Font rf = getFont(pData[p].words[w].font.name, fontStyle, pData[p].words[w].serif, Math.round(((float) pData[p].words[w].fontSize) * magnification));
						rg.setFont(rf);
						
						//	adjust word size and vertical position
						LineMetrics wlm = rf.getLineMetrics(pData[p].words[w].str, rg.getFontRenderContext());
						TextLayout wtl = new TextLayout(pData[p].words[w].str, rf, rg.getFontRenderContext());
						double hScale = (((double) (wb.right - wb.left)) / wtl.getBounds().getWidth());
						AffineTransform at = rg.getTransform();
						rg.translate(wb.left, 0);
						float leftShift = ((float) -wtl.getBounds().getMinX());
						if (hScale < 1)
							rg.scale(hScale, 1);
						else leftShift += (((wb.right - wb.left) - wtl.getBounds().getWidth()) / 2);
						System.out.println("Rendering " + pData[p].words[w].str + ((pData[p].words[w].str.length() == 1) ? (" " + Integer.toString(((int) pData[p].words[w].str.charAt(0)), 16)) : ""));
						try {
							rg.drawString(pData[p].words[w].str, leftShift, (wb.bottom - (pData[p].words[w].font.hasDescent ? Math.round(wlm.getDescent()) : 0)));
						} catch (InternalError ie) {}
						rg.setTransform(at);
					}
					
					//	cannot cut margins here because otherwise PDF word assignment gets broken
					synchronized (imageStore) {
						imageNames[p] = imageStore.storePageImage(doc.docId, p, pageImages[p], textPdfPageImageDpi);
					}
					if (imageNames[p] == null) {
						spm.setInfo(" --> page image storage failed");
						throw new IOException("Could not store image of page " + p);
					}
					else {
						spm.setInfo(" --> page image stored");
						imageDPIs[p] = textPdfPageImageDpi;
						pageBounds[p] = new BoundingBox(0, pageImages[p].getWidth(), 0, pageImages[p].getHeight());
					}
				}
				
				//	if we have more pages than we can process in parallel, set array element to null to allow garbage collection
				if (pageImages.length > Runtime.getRuntime().availableProcessors())
					pageImages[p] = null;
			}
		};
		ParallelJobRunner.runParallelFor(pf, ((PdfExtractorTest.aimAtPage < 0) ? 0 : PdfExtractorTest.aimAtPage), ((PdfExtractorTest.aimAtPage < 0) ? pData.length : (PdfExtractorTest.aimAtPage + 1)), (this.useMultipleCores ? -1 : 1));
		spm.setProgress(100);
		
		//	check errors
		this.checkException(pf);
		
		//	garbage collect page images in large document
		if (pageImages.length > Runtime.getRuntime().availableProcessors())
			System.gc();
		
		//	create pages and add words
		spm.setStep("Generating pages");
		spm.setBaseProgress(70);
		spm.setProgress(0);
		spm.setMaxProgress(75);
		final ImPage[] pages = new ImPage[pData.length];
		if (PdfExtractorTest.aimAtPage != -1) {
			for (int p = 0; p < pages.length; p++)
				if (p != PdfExtractorTest.aimAtPage) {
					BoundingBox pageBox = new BoundingBox(0, 32, 0, 44); // have to use these numbers to prevent 0 or 1 step in page word index
					ImPage page = new ImPage(doc, p, pageBox);
				}
		}
		pf = new ParallelFor() {
			public void doFor(int p) throws Exception {
				
				//	nothing to work with
				if ((pData[p] == null) || (pData[p].words == null))
					return;
				spm.setInfo("Generating page " + p + " of " + pData.length);
				spm.setProgress((p * 100) / pData.length);
				
				//	generate page (we have to synchronize this, as it adds to main document)
				synchronized (doc) {
					pages[p] = new ImPage(doc, p, pageBounds[p]);
				}
				
				//	add words to page
				for (int w = 0; w < pData[p].words.length; w++) {
					
					//	skip over in-figure words
					if ((pInFigureWords[p] != null) && pInFigureWords[p].contains(pData[p].words[w]))
						continue;
					
					//	make sure word has some minimum width
					BoundingBox wb = getBoundingBox(pData[p].words[w].bounds, pData[p].pdfPageBox, magnification, pData[p].rotate);
					if ((wb.right - wb.left) < 4)
						wb = new BoundingBox(wb.left, (wb.left + 4), wb.top, wb.bottom);
					
					//	add word to page
					ImWord word = new ImWord(pages[p], wb, pData[p].words[w].str);
					
					//	set layout attributes
					if ((pData[p].words[w].font != null) && (pData[p].words[w].font.name != null))
						word.setAttribute(FONT_NAME_ATTRIBUTE, pData[p].words[w].font.name);
					if (pData[p].words[w].fontSize != -1)
						word.setAttribute(FONT_SIZE_ATTRIBUTE, ("" + pData[p].words[w].fontSize));
					if (pData[p].words[w].bold)
						word.setAttribute(BOLD_ATTRIBUTE);
					if (pData[p].words[w].italics)
						word.setAttribute(ITALICS_ATTRIBUTE);
					
					//	add font char ID string
					StringBuffer charCodesHex = new StringBuffer();
					for (int c = 0; c < pData[p].words[w].charCodes.length(); c++) {
						String charCodeHex = Integer.toString((((int) pData[p].words[w].charCodes.charAt(c)) & 255), 16).toUpperCase();
						if (charCodeHex.length() < 2)
							charCodesHex.append("0");
						charCodesHex.append(charCodeHex);
					}
					word.setAttribute(ImFont.CHARACTER_CODE_STRING_ATTRIBUTE, charCodesHex.toString());
				}
			}
		};
		ParallelJobRunner.runParallelFor(pf, ((PdfExtractorTest.aimAtPage < 0) ? 0 : PdfExtractorTest.aimAtPage), ((PdfExtractorTest.aimAtPage < 0) ? pData.length : (PdfExtractorTest.aimAtPage + 1)), (this.useMultipleCores ? -1 : 1));
		spm.setProgress(100);
		
		//	check errors
		this.checkException(pf);
		
		//	now that we have words, we can get a document style and use it for page structure analysis
		DocumentStyle docStyle = DocumentStyle.getStyleFor(doc);
		final DocumentStyle docLayout = docStyle.getSubset("layout");
		
		//	analyze page structure
		spm.setStep("Analyzing page structure");
		spm.setBaseProgress(75);
		spm.setProgress(0);
		spm.setMaxProgress(98);
		pf = new ParallelFor() {
			public void doFor(int p) throws Exception {
				
				//	nothing to work with
				if ((pData[p] == null) || (pData[p].words == null))
					return;
				spm.setInfo("Analyzing structure of page " + p + " of " + pData.length);
				spm.setProgress((p * 100) / pData.length);
				
				//	make sure page image is loaded (might have been discarded to save memory in large document)
				BufferedImage pageImage = pageImages[p];
				if (pageImage == null) {
					PageImage pi = imageStore.getPageImage(doc.docId, p);
					pageImage = pi.image;
				}
				
				//	get page content area layout hint (defaulting to whole page bounds), as well as number of columns
				BoundingBox contentArea = docLayout.getBoxProperty("contentArea", pages[p].bounds, imageDPIs[p]);
				int columnCount = docLayout.getIntProperty("columnCount", -1);
				
				//	we've had a block flip, extend content area to square to prevent content cutoff
				if (blockFlipInPage[p]) {
					if (contentArea != pages[p].bounds)
						contentArea = new BoundingBox(contentArea.left, (contentArea.left + (contentArea.bottom - contentArea.top)), contentArea.top, contentArea.bottom);
					if (columnCount != -1)
						columnCount = 1;
				}
				//	TODO revise this, especially column count (mixed-orientation pages !!!)
				
				//	index words by bounding boxes, and determine page content bounds
				ImWord[] pWords = pages[p].getWords();
				HashMap wordsByBoxes = new HashMap();
				int pContentLeft = Integer.MAX_VALUE;
				int pContentRight = 0;
				int pContentTop = Integer.MAX_VALUE;
				int pContentBottom = 0;
				for (int w = 0; w < pWords.length; w++) {
					
					//	ignore word if outside content area, and mark as artifact
					if ((pWords[w].centerX < contentArea.left) || (pWords[w].centerX > contentArea.right) || (pWords[w].centerY < contentArea.top) || (pWords[w].centerY > contentArea.bottom)) {
						pWords[w].setTextStreamType(ImWord.TEXT_STREAM_TYPE_ARTIFACT);
						continue;
					}
					
					//	update content bounds
					pContentLeft = Math.min(pContentLeft, pWords[w].bounds.left);
					pContentRight = Math.max(pContentRight, pWords[w].bounds.right);
					pContentTop = Math.min(pContentTop, pWords[w].bounds.top);
					pContentBottom = Math.max(pContentBottom, pWords[w].bounds.bottom);
					
					//	index word
					wordsByBoxes.put(pWords[w].bounds, pWords[w]);
				}
				
				//	also observe figures in page content
				for (int f = 0; f < pData[p].figures.length; f++) {
					BoundingBox fbb = getBoundingBox(pData[p].figures[f].bounds, pData[p].pdfPageBox, magnification, pData[p].rotate);
					pContentLeft = Math.min(pContentLeft, fbb.left);
					pContentRight = Math.max(pContentRight, fbb.right);
					pContentTop = Math.min(pContentTop, fbb.top);
					pContentBottom = Math.max(pContentBottom, fbb.bottom);
				}
				
				//	wrap image for structure analysis
				AnalysisImage api = Imaging.wrapImage(pageImage, null);
				Imaging.whitenWhite(api);
				
				//	get page brightness
				byte[][] apiBrightness = api.getBrightness();
				
				//	wipe page completely if number of columns known (no lines or anything required for keeping blocks from being chunked into multiple columns)
				if (columnCount != -1) {
					for (int c = 0; c < apiBrightness.length; c++)
						Arrays.fill(apiBrightness[c], ((byte) 127));
				}
				
				//	whiten out figures so they don't get in the way
				for (int f = 0; f < pData[p].figures.length; f++) {
					BoundingBox fbb = getBoundingBox(pData[p].figures[f].bounds, pData[p].pdfPageBox, magnification, pData[p].rotate);
					System.out.println("Marking figure " + fbb);
					for (int c = Math.max(fbb.left, 0); c < Math.min(fbb.right, apiBrightness.length); c++) {
						for (int r = Math.max(fbb.top, 0); r < Math.min(fbb.bottom, apiBrightness[c].length); r++)
							apiBrightness[c][r] = ((byte) 127);
					}
				}
				
				//	mark words a solid black boxes
				System.out.println("Page image size is " + pageImage.getWidth() + "x" + pageImage.getHeight());
				for (Iterator wbbit = wordsByBoxes.keySet().iterator(); wbbit.hasNext();) {
					BoundingBox wbb = ((BoundingBox) wbbit.next());
					System.out.println("Marking word " + wbb);
					for (int c = Math.max(wbb.left, 0); c < Math.min(wbb.right, apiBrightness.length); c++) {
						for (int r = Math.max(wbb.top, 0); r < Math.min(wbb.bottom, apiBrightness[0].length); r++)
							apiBrightness[c][r] = ((byte) 0);
					}
				}
				
				//	eliminate everything outside page content bounds
				for (int c = 0; c < apiBrightness.length; c++)
					for (int r = 0; r < apiBrightness[c].length; r++) {
						if ((c < pContentLeft) || (c >= pContentRight) || (r < pContentTop) || (r >= pContentBottom))
							apiBrightness[c][r] = ((byte) 127);
					}
				
				//	get column and block margin layout hints (defaulting to kind of universal ball park figures)
				int minBlockMargin = docLayout.getIntProperty("minBlockMargin", (imageDPIs[p] / 10), imageDPIs[p]);
				int minColumnMargin = ((columnCount == 1) ? (pages[p].bounds.right - pages[p].bounds.left) : docLayout.getIntProperty("minColumnMargin", (imageDPIs[p] / 10), imageDPIs[p]));
				
				//	get (or compute) column areas to correct erroneous column splits
				BoundingBox[] columnAreas = docLayout.getBoxListProperty("columnAreas", null, imageDPIs[p]);
				if (columnAreas == null) {
					if (columnCount == 1) {
						columnAreas = new BoundingBox[1];
						columnAreas[0] = contentArea;
					}
					else if (columnCount == 2) {
						columnAreas = new BoundingBox[2];
						columnAreas[0] = new BoundingBox(contentArea.left, ((contentArea.left + contentArea.right) / 2), contentArea.top, contentArea.bottom);
						columnAreas[1] = new BoundingBox(((contentArea.left + contentArea.right) / 2), contentArea.right, contentArea.top, contentArea.bottom);
					}
					else if ((columnCount != -1) && (contentArea != pages[p].bounds)) {
						columnAreas = new BoundingBox[columnCount];
						for (int c = 0; c < columnCount; c++)
							columnAreas[c] = new BoundingBox((contentArea.left + (((contentArea.right - contentArea.left) * c) / columnCount)), (contentArea.left + (((contentArea.right - contentArea.left) * (c + 1)) / columnCount)), contentArea.top, contentArea.bottom);
					}
				}
				
				//	obtain visual page structure
				Region pageRootRegion = PageImageAnalysis.getPageRegion(api, imageDPIs[p], minColumnMargin, minBlockMargin, columnAreas, false, spm);
				
				//	add page content to document
				addRegionStructure(pages[p], null, pageRootRegion, imageDPIs[p], wordsByBoxes, spm);
				
				//	add any figures
				if (pData[p].figures.length != 0) {
					
					//	get any images marked in page structure
					ImRegion[] pageStructImages = pages[p].getRegions(ImRegion.IMAGE_TYPE);
					int[] pageStructImageCoverage = new int[pageStructImages.length];
					Arrays.fill(pageStructImageCoverage, 0);
					
					//	re-wrap page image, so brightness is re-computed
					api = Imaging.wrapImage(pageImage, null);
					Imaging.whitenWhite(api);
					
					//	get page brightness
					apiBrightness = api.getBrightness();
					
					//	whiten out words so they don't get in the way (and add some margin to compensate for IcePDF leftovers)
					final int wordSafetyMargin = (imageDPIs[p] / 30);
					for (Iterator wbbit = wordsByBoxes.keySet().iterator(); wbbit.hasNext();) {
						final BoundingBox wbb = ((BoundingBox) wbbit.next());
						for (int c = Math.max(0, (wbb.left - wordSafetyMargin)); c < Math.min(apiBrightness.length, (wbb.right + wordSafetyMargin)); c++) {
							for (int r = Math.max(0, (wbb.top - wordSafetyMargin)); r < Math.min(apiBrightness[c].length, (wbb.bottom + wordSafetyMargin)); r++)
								apiBrightness[c][r] = ((byte) 127);
						}
					}
					
					//	eliminate everything outside page content bounds
					for (int c = 0; c < apiBrightness.length; c++)
						for (int r = 0; r < apiBrightness[c].length; r++) {
							if ((c < pContentLeft) || (c >= pContentRight) || (r < pContentTop) || (r >= pContentBottom))
								apiBrightness[c][r] = ((byte) 127);
						}
					
					//	add image regions only now
					for (int f = 0; f < pData[p].figures.length; f++) {
						BoundingBox fbb = getBoundingBox(pData[p].figures[f].bounds, pData[p].pdfPageBox, magnification, pData[p].rotate);
						int fLeft = Math.max(fbb.left, 0);
						int fRight = Math.min(fbb.right, apiBrightness.length);
						int fTop = Math.max(fbb.top, 0);
						int fBottom = Math.min(fbb.bottom, apiBrightness[0].length);
						
						//	narrow left and right
						while (fLeft < fRight) {
							boolean leftVoid = true;
							boolean rightVoid = true;
							for (int r = fTop; r < fBottom; r++) {
								if (apiBrightness[fLeft][r] < 127)
									leftVoid = false;
								if (apiBrightness[fRight-1][r] < 127)
									rightVoid = false;
								if (!leftVoid && !rightVoid)
									break;
							}
							if (leftVoid)
								fLeft++;
							if (rightVoid)
								fRight--;
							if (!leftVoid && !rightVoid)
								break;
						}
						
						//	narrow top and bottom
						while (fTop < fBottom) {
							boolean topVoid = true;
							boolean bottomVoid = true;
							for (int c = fLeft; c < fRight; c++) {
								if (apiBrightness[c][fTop] < 127)
									topVoid = false;
								if (apiBrightness[c][fBottom-1] < 127)
									bottomVoid = false;
								if (!topVoid && !bottomVoid)
									break;
							}
							if (topVoid)
								fTop++;
							if (bottomVoid)
								fBottom--;
							if (!topVoid && !bottomVoid)
								break;
						}
						
						//	add image region (and replace almost-duplicates from page structuring)
						if ((fLeft < fRight) && (fTop < fBottom)) {
							BoundingBox fBounds = new BoundingBox(fLeft, fRight, fTop, fBottom);
							
							//	test if image region already exists
							for (int i = 0; i < pageStructImages.length; i++)
								if (pageStructImages[i].bounds.equals(fBounds)) {
									fBounds = null;
									break;
								}
							if (fBounds == null)
								continue;
							
							//	mark image region
							new ImRegion(pages[p], fBounds, ImRegion.IMAGE_TYPE);
							
							//	count overlap with images marked by page structuring
							for (int i = 0; i < pageStructImages.length; i++) {
								if (fBounds.overlaps(pageStructImages[i].bounds))
									pageStructImageCoverage[i] += (
											(Math.min(fBounds.right, pageStructImages[i].bounds.right) - Math.max(fBounds.left, pageStructImages[i].bounds.left))
											*
											(Math.min(fBounds.bottom, pageStructImages[i].bounds.bottom) - Math.max(fBounds.top, pageStructImages[i].bounds.top))
										);
							}
						}
					}
					
					//	remove image regions from structuring replaced by (presumably more accurate ones) from embedded figures
					for (int i = 0; i < pageStructImages.length; i++) {
						if ((pageStructImageCoverage[i] * 5) > (((pageStructImages[i].bounds.right - pageStructImages[i].bounds.left) * (pageStructImages[i].bounds.bottom - pageStructImages[i].bounds.top)) * 4))
							pages[p].removeRegion(pageStructImages[i]);
					}
				}
				
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
				
				//	get factor expressing maximum line margin as multiple of line height
				float maxLineMarginFactor = docLayout.getFloatProperty("maxLineMarginFactor", 0);
				
				//	do structure analysis
				PageAnalysis.splitIntoParagraphs(pages[p].getRegions(BLOCK_ANNOTATION_TYPE), imageDPIs[p], maxLineMarginFactor, null);
				spm.setInfo(" - paragraphs done");
				PageAnalysis.computeColumnLayout(pages[p].getRegions(COLUMN_ANNOTATION_TYPE), imageDPIs[p]);
				spm.setInfo(" - layout analysis done");
				
				//	clean up regions and mark images
				cleanUpRegions(pages[p]);
				
				//	finally ...
				spm.setInfo(" - page done");
			}
		};
		ParallelJobRunner.runParallelFor(pf, ((PdfExtractorTest.aimAtPage < 0) ? 0 : PdfExtractorTest.aimAtPage), ((PdfExtractorTest.aimAtPage < 0) ? pData.length : (PdfExtractorTest.aimAtPage + 1)), (this.useMultipleCores ? -1 : 1));
		spm.setProgress(100);
		
		//	garbage collect all the brightness arrays
		System.gc();
		
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
			if ((pages[p] != null) && ((PdfExtractorTest.aimAtPage == -1) || (p == PdfExtractorTest.aimAtPage)))
				lastWord = this.addTextStreamStructure(pages[p], lastWord);
		}
		spm.setProgress(100);
		spm.setInfo(" - word sequence analysis done");
	}
	
	private static void checkPageWords(PPageData pData, Tokenizer tokenizer, ProgressMonitor spm) {
		
		//	shrink word bounding boxes to actual word size
		BufferedImage mbi = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY);
		Graphics2D mg = mbi.createGraphics();
		for (int w = 0; w < pData.words.length; w++) {
			if (pData.words[w].str.trim().length() != 0)
				pData.words[w] = shrinkWordBounds(mg, pData.words[w]);
		}
		
		//	sort words left to right and top to bottom (for horizontal writing direction, for other writing directions accordingly)
		//	TODO make sure this is a total ordering
		Arrays.sort(pData.words, new Comparator() {
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
		//	at 10 pt, that is ... have to use lower threshold for smaller font sizes
		//	however, do not merge words that tokenize apart,
		//	nor ones that have mismatch in font style or size, as such differences usually bear some semantics
		double maxMergeMargin10pt = (((double) defaultDpi) / 60);
		ArrayList pWordList = null;
		PWord lpWord = null;
		TokenSequence lpWordTokens = null;
		spm.setInfo("Checking word mergers ...");
		for (int w = 0; w < pData.words.length; w++) {
			
			//	create token sequence
			TokenSequence pWordTokens = tokenizer.tokenize(pData.words[w].str);
			
			//	no word to compare to as yet
			if ((lpWord == null) || (lpWordTokens == null)) {
				lpWord = pData.words[w];
				lpWordTokens = pWordTokens;
				continue;
			}
			if (DEBUG_MERGE_WORDS) System.out.println(" - checking words '" + lpWord.str + "'@" + lpWord.bounds + " and '" + pData.words[w].str + "'@" + pData.words[w].bounds);
			
			//	check if words mergeable
			if (!areWordsMergeable(lpWord, lpWordTokens, pData.words[w], pWordTokens, maxMergeMargin10pt, tokenizer)) {
				lpWord = pData.words[w];
				lpWordTokens = pWordTokens;
				continue;
			}
			
			//	figure out bold, italic, font size, etc. using weighted majority vote
			PdfFont mpWordFont = ((lpWord.str.length() < pData.words[w].str.length()) ? pData.words[w].font : lpWord.font);
			Rectangle2D.Float mpWordBounds;
			if (lpWord.fontDirection == PWord.LEFT_RIGHT_FONT_DIRECTION) {
				float top = ((float) Math.max(lpWord.bounds.getMaxY(), pData.words[w].bounds.getMaxY()));
				float bottom = ((float) Math.min(lpWord.bounds.getMinY(), pData.words[w].bounds.getMinY()));
				mpWordBounds = new Rectangle2D.Float(((float) lpWord.bounds.getMinX()), bottom, ((float) (pData.words[w].bounds.getMaxX() - lpWord.bounds.getMinX())), (top - bottom));
			}
			else if (lpWord.fontDirection == PWord.BOTTOM_UP_FONT_DIRECTION) {
				float top = ((float) Math.min(lpWord.bounds.getMinX(), pData.words[w].bounds.getMinX()));
				float bottom = ((float) Math.max(lpWord.bounds.getMaxX(), pData.words[w].bounds.getMaxX()));
				mpWordBounds = new Rectangle2D.Float(top, ((float) lpWord.bounds.getMinY()), (bottom - top), ((float) (pData.words[w].bounds.getMaxY() - lpWord.bounds.getMinY())));
			}
			else if (lpWord.fontDirection == PWord.TOP_DOWN_FONT_DIRECTION) {
				float top = ((float) Math.max(lpWord.bounds.getMaxX(), pData.words[w].bounds.getMaxX()));
				float bottom = ((float) Math.min(lpWord.bounds.getMinX(), pData.words[w].bounds.getMinX()));
				mpWordBounds = new Rectangle2D.Float(bottom, ((float) pData.words[w].bounds.getMinY()), (top - bottom), ((float) (lpWord.bounds.getMaxY() - pData.words[w].bounds.getMinY())));
			}
			else {
				lpWord = pData.words[w];
				lpWordTokens = pWordTokens;
				continue;
			}
			
			//	create merged word
			PWord mpWord = new PWord((lpWord.charCodes + pData.words[w].charCodes), (lpWord.str + pData.words[w].str), mpWordBounds, lpWord.fontSize, lpWord.fontDirection, mpWordFont);
			spm.setInfo(" --> merged words " + lpWord.str + " and " + pData.words[w].str + " to '" + mpWord.str + "'@" + mpWord.bounds);
			
			//	store merged word
			pData.words[w] = mpWord;
			pData.words[w-1] = null;
			lpWord = mpWord;
			lpWordTokens = tokenizer.tokenize(mpWord.str);
			
			//	remember merger
			if (pWordList == null)
				pWordList = new ArrayList();
		}
		
		//	refresh PWord array
		if (pWordList != null) {
			for (int w = 0; w < pData.words.length; w++) {
				if (pData.words[w] != null)
					pWordList.add(pData.words[w]);
			}
			pData.words = ((PWord[]) pWordList.toArray(new PWord[pWordList.size()]));
			pWordList = null;
		}
		
		//	report what we got
		if (pData.words.length == 0)
			spm.setInfo(" --> empty page");
		else spm.setInfo(" --> got " + pData.words.length + " words in PDF");
	}
	
	private static Tokenizer getNumberTokenizer(PPageData[] pData, Tokenizer tokenizer, ProgressMonitor spm) {
		
		//	assess number punctuation
		int decimalDotCount = 0;
		int decimalCommaCount = 0;
		int thousandDotCount = 0;
		int thousandCommaCount = 0;
		for (int p = 0; p < pData.length; p++) {
			
			//	nothing to work with
			if ((pData[p] == null) || (pData[p].words == null) || (pData[p].words.length == 0))
				continue;
			
			//	check word by word
			for (int w = 0; w < pData[p].words.length; w++) {
				
				//	skip over non-numbers
				if (!Gamta.isNumber(pData[p].words[w].str))
					continue;
				
				//	skip over numbers without punctuation
				if (pData[p].words[w].str.matches("[0-9]+"))
					continue;
				
				//	count thousand dots ...
				if (pData[p].words[w].str.matches("[0-9]{1,3}(\\.[0-9]{3})+\\,[0-9]+")) {
					thousandDotCount++;
					decimalCommaCount++;
					spm.setInfo("  thousand dot & decimal comma in " + pData[p].words[w].str + " (page " + p + ")");
				}
				
				//	as well as thousand commas ...
				else if (pData[p].words[w].str.matches("[0-9]{1,3}(\\,[0-9]{3})+\\.[0-9]+")) {
					thousandCommaCount++;
					decimalDotCount++;
					spm.setInfo("  thousand comma & decimal dot in " + pData[p].words[w].str + " (page " + p + ")");
				}
				
				//	count decimal dots ...
				else if (pData[p].words[w].str.matches(".*[0-9]\\.[0-9]+")) {
					decimalDotCount++;
					spm.setInfo("  decimal dot in " + pData[p].words[w].str + " (page " + p + ")");
				}
				
				//	as well as decimal commas ...
				else if (pData[p].words[w].str.matches(".*[0-9]\\,[0-9]+")) {
					decimalCommaCount++;
					spm.setInfo("  decimal comma in " + pData[p].words[w].str + " (page " + p + ")");
				}
			}
		}
		spm.setInfo("Found " + decimalDotCount + " decimal dots and " + decimalCommaCount + " decimal commas");
		spm.setInfo("Found " + thousandDotCount + " thousand dots and " + thousandCommaCount + " thousand commas");
		
		//	return tokenizer based on findings
		if ((decimalDotCount > thousandDotCount) && (decimalDotCount > decimalCommaCount)) {
			if ((thousandCommaCount == 0) && (decimalCommaCount == 0))
				return new RegExTokenizer(
						"(" +
							"(" +
								"([1-9][0-9]*)" +
								"|" +
								"0" +
							")" +
							"(\\.[0-9]+)?" +
						")" +
						"|\\,|\\.");
			else return new RegExTokenizer(
					"(" +
						"(" +
							"(" +
								"[1-9]" +
								"(" +
									"[0-9]" +
									"|" +
									"(\\,[0-9]{3})" +
								")*" +
							")" +
							"|" +
							"0" +
						")" +
						"(\\.[0-9]+)?" +
					")|\\,|\\.");
		}
		else if ((decimalCommaCount > thousandCommaCount) && (decimalCommaCount > decimalDotCount)) {
			if ((thousandDotCount == 0) && (decimalDotCount == 0))
				return new RegExTokenizer(
						"(" +
							"(" +
								"([1-9][0-9]*)" +
								"|" +
								"0" +
							")" +
							"(\\,[0-9]+)?" +
						")" +
						"|\\.|\\,");
			else return new RegExTokenizer(
					"(" +
						"(" +
							"(" +
								"[1-9]" +
								"(" +
									"[0-9]" +
									"|" +
									"(\\.[0-9]{3})" +
								")*" +
							")" +
							"|" +
							"0)" +
						"(\\,[0-9]+)?" +
					")" +
					"|\\.|\\,");
		}
		else return tokenizer;
	}
	
	private static boolean areWordsMergeable(PWord lpWord, TokenSequence lpWordTokens, PWord pWord, TokenSequence pWordTokens, double maxMergeMargin10pt, Tokenizer tokenizer) {
		
		//	do not join words with altering bold/italic properties or font sizes, they differ for a reason
		if ((lpWord.bold != pWord.bold) || (lpWord.italics != pWord.italics) || (lpWord.fontSize != pWord.fontSize) || (lpWord.fontDirection != pWord.fontDirection)) {
			if (DEBUG_MERGE_WORDS) System.out.println(" --> font size, direction, or style mismatch");
			return false;
		}
		
		//	compute maximum margin based on font size
		double maxMergeMargin = ((maxMergeMargin10pt * Math.min(pWord.fontSize, 10)) / 10);
		
		//	left-right words
		if (pWord.fontDirection == PWord.LEFT_RIGHT_FONT_DIRECTION) {
			
			//	not in same line
			if ((pWord.bounds.getMaxY() <= lpWord.bounds.getMinY()) || (lpWord.bounds.getMaxY() <= pWord.bounds.getMinY())) {
				if (DEBUG_MERGE_WORDS) System.out.println(" --> different lines");
				return false;
			}
			
			//	too far a gap
			if ((pWord.bounds.getMinX() < lpWord.bounds.getMinX()) || ((pWord.bounds.getMinX() - lpWord.bounds.getMaxX()) > maxMergeMargin)) {
				if (DEBUG_MERGE_WORDS) System.out.println(" --> too far apart");
				return false;
			}
			
			//	require 80% overlap
			double pWordHeight = Math.abs(pWord.bounds.getMaxY() - pWord.bounds.getMinY());
			double lpWordHeight = Math.abs(lpWord.bounds.getMaxY() - lpWord.bounds.getMinY());
			double overlapHeight = (Math.min(pWord.bounds.getMaxY(), lpWord.bounds.getMaxY()) - Math.max(pWord.bounds.getMinY(), lpWord.bounds.getMinY()));
			if ((overlapHeight * 5) < (Math.min(pWordHeight, lpWordHeight) * 4)) {
				if (DEBUG_MERGE_WORDS) System.out.println(" --> different (if overlapping) lines");
				return false;
			}
		}
		
		//	bottom-up words
		else if (pWord.fontDirection == PWord.BOTTOM_UP_FONT_DIRECTION) {
			
			//	not in same line
			if ((pWord.bounds.getMinX() >= lpWord.bounds.getMaxX()) || (lpWord.bounds.getMinX() >= pWord.bounds.getMaxX())) {
				if (DEBUG_MERGE_WORDS) System.out.println(" --> different lines");
				return false;
			}
			
			//	too far a gap
			if ((pWord.bounds.getMinY() < lpWord.bounds.getMinY()) || ((pWord.bounds.getMinY() - lpWord.bounds.getMaxY()) > maxMergeMargin)) {
				if (DEBUG_MERGE_WORDS) System.out.println(" --> too far apart");
				return false;
			}
			
			//	require 80% overlap
			double pWordHeight = Math.abs(pWord.bounds.getMaxX() - pWord.bounds.getMinX());
			double lpWordHeight = Math.abs(lpWord.bounds.getMaxX() - lpWord.bounds.getMinX());
			double overlapHeight = (Math.min(pWord.bounds.getMaxX(), lpWord.bounds.getMaxX()) - Math.max(pWord.bounds.getMinX(), lpWord.bounds.getMinX()));
			if ((overlapHeight * 5) < (Math.min(pWordHeight, lpWordHeight) * 4)) {
				if (DEBUG_MERGE_WORDS) System.out.println(" --> different (if overlapping) lines");
				return false;
			}
		}
		
		//	top-down words
		else if (pWord.fontDirection == PWord.TOP_DOWN_FONT_DIRECTION) {
			
			//	not in same line
			if ((pWord.bounds.getMaxX() <= lpWord.bounds.getMinX()) || (lpWord.bounds.getMaxX() <= pWord.bounds.getMinX())) {
				if (DEBUG_MERGE_WORDS) System.out.println(" --> different lines");
				return false;
			}
			
			//	too far a gap
			if ((pWord.bounds.getMaxY() > lpWord.bounds.getMaxY()) || ((lpWord.bounds.getMinY() - pWord.bounds.getMaxY()) > maxMergeMargin)) {
				if (DEBUG_MERGE_WORDS) System.out.println(" --> too far apart");
				return false;
			}
			
			//	require 80% overlap
			double pWordHeight = Math.abs(pWord.bounds.getMaxX() - pWord.bounds.getMinX());
			double lpWordHeight = Math.abs(lpWord.bounds.getMaxX() - lpWord.bounds.getMinX());
			double overlapHeight = (Math.min(pWord.bounds.getMaxX(), lpWord.bounds.getMaxX()) - Math.max(pWord.bounds.getMinX(), lpWord.bounds.getMinX()));
			if ((overlapHeight * 5) < (Math.min(pWordHeight, lpWordHeight) * 4)) {
				if (DEBUG_MERGE_WORDS) System.out.println(" --> different (if overlapping) lines");
				return false;
			}
		}
		
		//	do NOT merge words that tokenize apart
		TokenSequence mpWordTokens = tokenizer.tokenize(lpWord.str + pWord.str);
		if ((lpWordTokens.size() + pWordTokens.size()) <= mpWordTokens.size()) {
			if (DEBUG_MERGE_WORDS) System.out.println(" --> tokenization mismatch");
			return false;
		}
		
		//	no counter indications found
		return true;
	}
	
	private void storeFiguresAsSupplements(ImDocument doc, PPageData pData, Document pdfDoc, Map objects, float magnification, ProgressMonitor spm) throws IOException {
		spm.setInfo(" - storing figures");
		
		//	display figures if testing
		ImageDisplayDialog fdd = null;
		if (DEBUG_EXTRACT_FIGURES && (PdfExtractorTest.aimAtPage >= 0))
			fdd = new ImageDisplayDialog("Figures in Page " + pData.p);
		
		//	get XObject dictionary from page resource dictionary
		Object xObjectsObj = PdfParser.dereference(pData.pdfPageResources.get("XObject"), objects);
		if (xObjectsObj instanceof Hashtable) {
			Hashtable xObjects = ((Hashtable) xObjectsObj);
			spm.setInfo("   - XObject dictionary is " + xObjects);
			
			//	store figures
			for (int f = 0; f < pData.figures.length; f++) {
				PFigure pFigure = pData.figures[f];
				spm.setInfo("   - " + pFigure);
				
				//	get image
				BufferedImage pFigureImage = this.getFigureImage(doc, pFigure, true, pdfDoc, pData.pdfPage, pData.p, pData.pdfPageBox, xObjects, objects, magnification, spm);
				
				//	display figures if testing
				if ((fdd != null) && (pFigureImage != null)) {
					BoundingBox pFigureBox = this.getBoundingBox(pFigure.bounds, pData.pdfPageBox, magnification, pData.rotate);
					spm.setInfo("     - rendering bounds are " + pFigureBox);
					fdd.addImage(pFigureImage, pFigureBox.toString());
				}
			}
		}
		else spm.setInfo("   - strange XObject dictionary: " + xObjectsObj);
		
		//	display figures if testing
		if (fdd != null) {
			fdd.setSize(600, 800);
			fdd.setLocationRelativeTo(null);
			fdd.setVisible(true);
		}
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
			Font mf = getFont(pWord.font.name, fontStyle, pWord.serif, Math.round(((float) pWord.fontSize) * 1));
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
			Font mf = getFont(pWord.font.name, fontStyle, pWord.serif, Math.round(((float) pWord.fontSize) * 1));
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
			Font mf = getFont(pWord.font.name, fontStyle, pWord.serif, Math.round(((float) pWord.fontSize) * 1));
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
	private static Font getFont(String name, int style, boolean serif, int size) {
		String fontKey = (name + " " + style + " " + (serif ? "serif" : "sans") + " " + size);
		System.out.println("Getting font " + fontKey);
		Font font = ((Font) fontCache.get(fontKey));
		if (font != null) {
			System.out.println("==> cache hit");
			return font;
		}
		
		if (name != null) {
			if (name.matches("[A-Z]+\\+[A-Z][a-z].*"))
				name = name.substring(name.indexOf('+') + "+".length());
			if (name.startsWith("Symbol"))
				font = new Font("Symbol", style, size);
			else if (name.startsWith("ZapfDingbats"))
				font = new Font("ZapfDingbats", style, size);
			
			if (name.indexOf('-') != -1)
				name = name.substring(0, name.indexOf('-'));
			String ffn = PdfFont.getFallbackFontName(name, false);
			System.out.println("==> falling back to " + ffn);
			if (ffn.startsWith("Helvetica"))
//				font = new Font("SansSerif", style, size);
				font = new Font("FreeSans", style, size);
			else if (ffn.startsWith("Times"))
//				font = new Font("Serif", style, size);
				font = new Font("FreeSerif", style, size);
			else if (ffn.startsWith("Courier"))
//				font = new Font("Monospaced", style, size);
				font = new Font("FreeMono", style, size);
		}
		
		if (font == null) {
			System.out.println("==> base font not found, using Serif fallback");
//			return new Font((serif ? "Serif" : "SansSerif"), style, size);
			return new Font((serif ? "FreeSerif" : "FreeSans"), style, size);
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
		System.out.println("Analyzing text stream structure in page " + page.pageId);
		
		//	get paragraphs
		ImRegion[] paragraphs = page.getRegions(MutableAnnotation.PARAGRAPH_TYPE);
		System.out.println(" - got " + paragraphs.length + " paragraphs");
		if (paragraphs.length == 0)
			return lastWord;
		
		//	get main text stream head (can only be one here, theoretically, but artifacts _can_ wreck havoc)
		ImWord[] textStreamHeads = page.getTextStreamHeads();
		System.out.println(" - got " + textStreamHeads.length + " text stream heads");
		if (textStreamHeads.length == 0)
			return lastWord;
		ImWord textStreamHead = textStreamHeads[0];
		
		//	if we have multiple text stream heads, find and use the longest / largest one
		if (textStreamHeads.length > 1) {
			int maxTextStreamArea = 0;
			for (int h = 0; h < textStreamHeads.length; h++) {
				
				//	skip over words known to be artifacts
				if (ImWord.TEXT_STREAM_TYPE_ARTIFACT.equals(textStreamHeads[h].getTextStreamType()))
					continue;
				
				//	compute text stream size
				int textStreamArea = 0;
				for (ImWord imw = textStreamHeads[h]; imw != null; imw = imw.getNextWord()) {
					
					//	we're leaving the page (can hardly happen processing pages front to back, but better make sure)
					if (imw.pageId != textStreamHeads[h].pageId)
						break;
					
					//	count in this word
					textStreamArea += ((imw.bounds.right - imw.bounds.left) * (imw.bounds.bottom - imw.bounds.top));
				}
				
				//	do we have a new front runner?
				if (textStreamArea > maxTextStreamArea) {
					maxTextStreamArea = textStreamArea;
					textStreamHead = textStreamHeads[h];
				}
			}
		}
		
		//	index words by paragraph
		HashMap wordParagraphs = new HashMap();
		for (int p = 0; p < paragraphs.length; p++) {
			ImWord[] words = paragraphs[p].getWords();
			for (int w = 0; w < words.length; w++)
				wordParagraphs.put(words[w], paragraphs[p]);
		}
		System.out.println(" - paragraphs indexed by words");
		
		//	add text stream structure
		ImRegion lastWordPargarph = null;
		for (ImWord imw = textStreamHead; imw != null; imw = imw.getNextWord()) {
			
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
			ImRegion[] cRegions = (WORD_ANNOTATION_TYPE.equals(cType) ? page.getWordsInside(pRegions[p].bounds) : page.getRegionsInside(pRegions[p].bounds, true));
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
		return this.loadImagePdf(null, null, pdfBytes, false, false, 1, pm);
	}
	
	/**
	 * Load a document from an OCR PDF. Page images are stored in the page image
	 * store handed to the constructor. The document structure is stored in the
	 * returned image markup document, including word bounding boxes.
	 * @param doc the document to store the structural information in
	 * @param pdfDoc the PDF document to parse
	 * @param pdfBytes the raw binary data the the PDF document was parsed from
	 * @param metaPages expect born-digital meta pages at start or end of PDF?
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the image markup document
	 * @throws IOException
	 */
	public ImDocument loadImagePdf(byte[] pdfBytes, boolean metaPages, ProgressMonitor pm) throws IOException {
		return this.loadImagePdf(null, null, pdfBytes, metaPages, false, 1, pm);
	}
	
	/**
	 * Load a document from an OCR PDF. Page images are stored in the page image
	 * store handed to the constructor. The document structure is stored in the
	 * returned image markup document, including word bounding boxes.
	 * @param doc the document to store the structural information in
	 * @param pdfDoc the PDF document to parse
	 * @param pdfBytes the raw binary data the the PDF document was parsed from
	 * @param metaPages expect born-digital meta pages at start or end of PDF?
	 * @param useEmbeddedOCR use embedded OCR if available?
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the image markup document
	 * @throws IOException
	 */
	public ImDocument loadImagePdf(byte[] pdfBytes, boolean metaPages, boolean useEmbeddedOCR, ProgressMonitor pm) throws IOException {
		return this.loadImagePdf(null, null, pdfBytes, metaPages, useEmbeddedOCR, 1, pm);
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
		return this.loadImagePdf(doc, pdfDoc, pdfBytes, false, false, 1, pm);
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
	 * @param metaPages expect born-digital meta pages at start or end of PDF?
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the argument image markup document
	 * @throws IOException
	 */
	public ImDocument loadImagePdf(ImDocument doc, Document pdfDoc, byte[] pdfBytes, boolean metaPages, ProgressMonitor pm) throws IOException {
		return this.loadImagePdf(doc, pdfDoc, pdfBytes, metaPages, false, 1, pm);
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
	 * @param metaPages expect born-digital meta pages at start or end of PDF?
	 * @param useEmbeddedOCR use embedded OCR if available?
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the argument image markup document
	 * @throws IOException
	 */
	public ImDocument loadImagePdf(ImDocument doc, Document pdfDoc, byte[] pdfBytes, boolean metaPages, boolean useEmbeddedOCR, ProgressMonitor pm) throws IOException {
		return this.loadImagePdf(doc, pdfDoc, pdfBytes, metaPages, useEmbeddedOCR, 1, pm);
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
		return this.loadImagePdf(doc, pdfDoc, pdfBytes, false, false, scaleFactor, pm);
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
	 * @param metaPages expect born-digital meta pages at start or end of PDF?
	 * @param scaleFactor the scale factor for image PDFs, to correct PDFs that
	 *            are set too small (DPI number is divided by this factor)
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the argument image markup document
	 * @throws IOException
	 */
	public ImDocument loadImagePdf(ImDocument doc, Document pdfDoc, byte[] pdfBytes, boolean metaPages, int scaleFactor, ProgressMonitor pm) throws IOException {
		return this.loadImagePdf(pdfBytes, metaPages, false, pm);
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
	 * @param metaPages expect born-digital meta pages at start or end of PDF?
	 * @param useEmbeddedOCR use embedded OCR if available?
	 * @param scaleFactor the scale factor for image PDFs, to correct PDFs that
	 *            are set too small (DPI number is divided by this factor)
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the argument image markup document
	 * @throws IOException
	 */
	public ImDocument loadImagePdf(ImDocument doc, Document pdfDoc, byte[] pdfBytes, boolean metaPages, boolean useEmbeddedOCR, int scaleFactor, ProgressMonitor pm) throws IOException {
		
		//	check arguments
		if (doc == null)
			doc = this.doCreateDocument(getChecksum(pdfBytes));
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
		
		//	test if we can OCR
		if ((this.ocrEngine == null) && !useEmbeddedOCR)
			throw new RuntimeException("OCR Engine unavailable, check startup log.");
		
		//	build progress monitor with synchronized methods instead of synchronizing in-code
		SynchronizedProgressMonitor spm = ((pm instanceof SynchronizedProgressMonitor) ? ((SynchronizedProgressMonitor) pm) : new SynchronizedProgressMonitor(pm));
		
		//	load document structure (IcePDF is better at that ...)
		Catalog catalog = pdfDoc.getCatalog();
		PageTree pageTree = catalog.getPageTree();
		
		//	parse PDF
		HashMap objects = PdfParser.getObjects(pdfBytes, pdfDoc.getSecurityManager());
		
		//	decrypt streams and strings
		PdfParser.decryptObjects(objects, pdfDoc.getSecurityManager());
		
		//	get basic page data (takes progress to 30%)
		PPageData[] pData = this.getPdfPageData(doc, useEmbeddedOCR, pageTree, objects, spm);
		
		//	add page content
		this.addImagePdfPages(doc, pData, pdfDoc, pageTree, objects, metaPages, useEmbeddedOCR, scaleFactor, spm);
		
		//	finally ...
		return doc;
	}
	
	private void addImagePdfPages(ImDocument doc, PPageData[] pData, Document pdfDoc, PageTree pageTree, Map objects, boolean metaPages, boolean useEmbeddedOCR, int scaleFactor, SynchronizedProgressMonitor spm) throws IOException {
		
		//	test if we can OCR
		if ((this.ocrEngine == null) && !useEmbeddedOCR)
			throw new RuntimeException("OCR Engine unavailable, check startup log.");
		
		//	more pages than we can process in parallel, don't cache at all to save memory
		Map pageImageCache;
		if (pdfDoc.getPageTree().getNumberOfPages() > Runtime.getRuntime().availableProcessors())
			pageImageCache = null;
		
		//	cache page images (only binary and gray scale, and only up to 300 DPI, as memory consumption gets too high beyond that)
		else pageImageCache = Collections.synchronizedMap(new HashMap() {
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
		spm.setStep("Loading document page images");
		spm.setBaseProgress(0);
		spm.setProgress(0);
		spm.setMaxProgress(30);
		this.addImagePdfPages(doc, pData, pdfDoc, pageTree, objects, metaPages, scaleFactor, spm, pageImageCache);
		
		//	cache higher level page structure as well
		Map pageRegionCache = Collections.synchronizedMap(new HashMap());
		
		//	fill in blocks and do OCR
		spm.setStep("Extracting blocks & " + (useEmbeddedOCR ? "embedded " : "doing ") + "OCR");
		spm.setBaseProgress(40);
		spm.setProgress(0);
		spm.setMaxProgress(70);
		this.addImagePdfPageBlocks(doc, (useEmbeddedOCR ? pData : null), spm, pageImageCache, pageRegionCache);
		
		//	analyze block structure and layout
		spm.setStep("Analyzing page text structure");
		spm.setBaseProgress(70);
		spm.setProgress(0);
		spm.setMaxProgress(90);
		this.addImagePdfPageLayout(doc, spm, pageImageCache, pageRegionCache);
		
		//	analyze font metrics across whole document
		spm.setStep("Analyzing font metrics");
		spm.setBaseProgress(90);
		spm.setProgress(0);
		spm.setMaxProgress(100);
		WordImageAnalysis.analyzeFontMetrics(doc, new CascadingProgressMonitor(spm));
	}
	
	private void addImagePdfPageLayout(final ImDocument doc, ProgressMonitor pm, final Map pageImageCache, final Map pageRegionCache) throws IOException {
		
		//	build progress monitor with synchronized methods instead of synchronizing in-code
		final SynchronizedProgressMonitor spm = ((pm instanceof SynchronizedProgressMonitor) ? ((SynchronizedProgressMonitor) pm) : new SynchronizedProgressMonitor(pm));
		
		//	get document pages
		final ImPage[] pages = doc.getPages();
		
		//	get document style and use it for page structure analysis
		DocumentStyle docStyle = DocumentStyle.getStyleFor(doc);
		final DocumentStyle docLayout = docStyle.getSubset("layout");
		
		//	analyze page text layout
		spm.setBaseProgress(0);
		spm.setMaxProgress(50);
		ParallelFor pf = new ParallelFor() {
			public void doFor(int p) throws Exception {
				
				//	update status display (might be inaccurate, but better than lock escalation)
				spm.setInfo("Analyzing layout of page " + p + " of " + pages.length);
				spm.setProgress((100 * p) / pages.length);
				
				//	get page image
				PageImage pi = null;
				if (pageImageCache != null)
					pi = ((PageImage) pageImageCache.get(new Integer(pages[p].pageId)));
				if (pi == null)
					pi = pages[p].getPageImage();
				if (pi == null) {
					spm.setInfo(" - page image not found");
					throw new IOException("Could not find image for page " + pages[p].pageId);
				}
				
				//	get page content area layout hint (defaulting to whole page bounds), as well as number of columns
				BoundingBox contentArea = docLayout.getBoxProperty("contentArea", pages[p].bounds, pi.currentDpi);
				
				//	index words by bounding boxes, excluding ones outside content area as artifacts
				ImWord[] pageWords = pages[p].getWords();
				HashMap wordsByBoxes = new HashMap();
				for (int w = 0; w < pageWords.length; w++) {
					if (contentArea.includes(pageWords[w].bounds, true))
						wordsByBoxes.put(pageWords[w].bounds, pageWords[w]);
					else pageWords[w].setTextStreamType(ImWord.TEXT_STREAM_TYPE_ARTIFACT);
				}
				
				//	obtain higher level page structure
				Region pageRootRegion;
				synchronized (pageRegionCache) {
					pageRootRegion = ((Region) pageRegionCache.remove(pages[p]));
				}
				if (pageRootRegion == null) {
					int columnCount = docLayout.getIntProperty("columnCount", -1);
					
					//	get column and block margin layout hints (defaulting to kind of universal ball park figures)
					int minBlockMargin = docLayout.getIntProperty("minBlockMargin", (pi.currentDpi / 10), pi.currentDpi);
					int minColumnMargin = ((columnCount == 1) ? (pages[p].bounds.right - pages[p].bounds.left) : docLayout.getIntProperty("minColumnMargin", (pi.currentDpi / 10), pi.currentDpi));
					
					//	get (or compute) column areas to correct erroneous column splits
					BoundingBox[] columnAreas = docLayout.getBoxListProperty("columnAreas", null, pi.currentDpi);
					if (columnAreas == null) {
						if (columnCount == 1) {
							columnAreas = new BoundingBox[1];
							columnAreas[0] = contentArea;
						}
						else if (columnCount == 2) {
							columnAreas = new BoundingBox[2];
							columnAreas[0] = new BoundingBox(contentArea.left, ((contentArea.left + contentArea.right) / 2), contentArea.top, contentArea.bottom);
							columnAreas[1] = new BoundingBox(((contentArea.left + contentArea.right) / 2), contentArea.right, contentArea.top, contentArea.bottom);
						}
						else if ((columnCount != -1) && (contentArea != pages[p].bounds)) {
							columnAreas = new BoundingBox[columnCount];
							for (int c = 0; c < columnCount; c++)
								columnAreas[c] = new BoundingBox((contentArea.left + (((contentArea.right - contentArea.left) * c) / columnCount)), (contentArea.left + (((contentArea.right - contentArea.left) * (c + 1)) / columnCount)), contentArea.top, contentArea.bottom);
						}
					}
					
					//	compute page structure
					AnalysisImage api = Imaging.wrapImage(pi.image, null);
					pageRootRegion = PageImageAnalysis.getPageRegion(api, pi.currentDpi, minColumnMargin, minBlockMargin, columnAreas, false, spm);
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
		ParallelJobRunner.runParallelFor(pf, 0, pages.length, (this.useMultipleCores ? -1 : 1));
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
//			
//			//	compute remaining line metrics
//			PageImageAnalysis.computeFontMetrics(theBlock, dpi);
			
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
		return this.loadImagePdfBlocks(null, null, pdfBytes, false, 1, pm);
	}
	
	/**
	 * Load the pages of a document from an OCR PDF, including their block
	 * structure. Page images are stored in the page image store handed to the
	 * constructor. The document structure is analyzed and represented to the
	 * text block level, but not below. 
	 * @param pdfBytes the raw binary data the the PDF document was parsed from
	 * @param metaPages expect born-digital meta pages at start or end of PDF?
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the image markup document
	 * @throws IOException
	 */
	public ImDocument loadImagePdfBlocks(byte[] pdfBytes, boolean metaPages, ProgressMonitor pm) throws IOException {
		return this.loadImagePdfBlocks(null, null, pdfBytes, metaPages, 1, pm);
	}
	
	/**
	 * Load the pages of a document from an OCR PDF, including their block
	 * structure. Page images are stored in the page image store handed to the
	 * constructor. The document structure is analyzed and represented to the
	 * text block level, but not below. 
	 * @param pdfBytes the raw binary data the the PDF document was parsed from
	 * @param metaPages expect born-digital meta pages at start or end of PDF?
	 * @param useEmbeddedOCR use embedded OCR instead of running internal one?
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the image markup document
	 * @throws IOException
	 */
	public ImDocument loadImagePdfBlocks(byte[] pdfBytes, boolean metaPages, boolean useEmbeddedOCR, ProgressMonitor pm) throws IOException {
		return this.loadImagePdfBlocks(null, null, pdfBytes, metaPages, useEmbeddedOCR, 1, pm);
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
		return this.loadImagePdfBlocks(doc, pdfDoc, pdfBytes, false, 1, pm);
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
	 * @param metaPages expect born-digital meta pages at start or end of PDF?
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the argument image markup document
	 * @throws IOException
	 */
	public ImDocument loadImagePdfBlocks(ImDocument doc, Document pdfDoc, byte[] pdfBytes, boolean metaPages, ProgressMonitor pm) throws IOException {
		return this.loadImagePdfBlocks(doc, pdfDoc, pdfBytes, metaPages, 1, pm);
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
	 * @param metaPages expect born-digital meta pages at start or end of PDF?
	 * @param useEmbeddedOCR use embedded OCR instead of running internal one?
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the argument image markup document
	 * @throws IOException
	 */
	public ImDocument loadImagePdfBlocks(ImDocument doc, Document pdfDoc, byte[] pdfBytes, boolean metaPages, boolean useEmbeddedOCR, ProgressMonitor pm) throws IOException {
		return this.loadImagePdfBlocks(doc, pdfDoc, pdfBytes, metaPages, useEmbeddedOCR, 1, pm);
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
		return this.loadImagePdfBlocks(doc, pdfDoc, pdfBytes, false,scaleFactor, pm);
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
	 * @param metaPages expect born-digital meta pages at start or end of PDF?
	 * @param scaleFactor the scale factor for image PDFs, to correct PDFs that
	 *            are set too small (DPI number is divided by this factor)
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the argument image markup document
	 * @throws IOException
	 */
	public ImDocument loadImagePdfBlocks(ImDocument doc, Document pdfDoc, byte[] pdfBytes, boolean metaPages, int scaleFactor, ProgressMonitor pm) throws IOException {
		return this.loadImagePdfBlocks(doc, pdfDoc, pdfBytes, metaPages, false, scaleFactor, pm);
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
	 * @param metaPages expect born-digital meta pages at start or end of PDF?
	 * @param useEmbeddedOCR use embedded OCR instead of running internal one?
	 * @param scaleFactor the scale factor for image PDFs, to correct PDFs that
	 *            are set too small (DPI number is divided by this factor)
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the argument image markup document
	 * @throws IOException
	 */
	public ImDocument loadImagePdfBlocks(ImDocument doc, Document pdfDoc, byte[] pdfBytes, boolean metaPages, boolean useEmbeddedOCR, int scaleFactor, ProgressMonitor pm) throws IOException {
		
		//	check arguments
		if (doc == null)
			doc = this.doCreateDocument(getChecksum(pdfBytes));
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
		if ((this.ocrEngine == null) && !useEmbeddedOCR)
			throw new RuntimeException("OCR Engine unavailable, check startup log.");
		
		//	more pages than we can process in parallel, don't cache at all to save memory
		Map pageImageCache;
		if (pdfDoc.getPageTree().getNumberOfPages() > Runtime.getRuntime().availableProcessors())
			pageImageCache = null;
		
		//	cache page images (only binary and gray scale, and only up to 300 DPI, as memory consumption gets too high beyond that)
		else pageImageCache = Collections.synchronizedMap(new HashMap() {
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
		
		//	build progress monitor with synchronized methods instead of synchronizing in-code
		SynchronizedProgressMonitor spm = ((pm instanceof SynchronizedProgressMonitor) ? ((SynchronizedProgressMonitor) pm) : new SynchronizedProgressMonitor(pm));
		
		//	load document structure (IcePDF is better at that ...)
		Catalog catalog = pdfDoc.getCatalog();
		PageTree pageTree = catalog.getPageTree();
		
		//	parse PDF
		HashMap objects = PdfParser.getObjects(pdfBytes, pdfDoc.getSecurityManager());
		
		//	decrypt streams and strings
		PdfParser.decryptObjects(objects, pdfDoc.getSecurityManager());
		
		//	get basic page data (takes progress to 30%)
		PPageData[] pData = this.getPdfPageData(doc, useEmbeddedOCR, pageTree, objects, spm);
		
		//	load pages
		pm.setStep("Loading document page images");
		pm.setBaseProgress(0);
		pm.setProgress(0);
		pm.setMaxProgress(40);
//		this.addImagePdfPages(doc, pdfDoc, pdfBytes, metaPages, scaleFactor, spm, pageImageCache);
		this.addImagePdfPages(doc, pData, pdfDoc, pageTree, objects, metaPages, scaleFactor, spm, pageImageCache);
		
		//	preserve source PDF in supplement
		ImSupplement.Source.createSource(doc, "application/pdf", pdfBytes);
		
		//	fill in blocks and do OCR
		pm.setStep("Extracting blocks & " + (useEmbeddedOCR ? "embedded " : "doing ") + "OCR");
		pm.setBaseProgress(40);
		pm.setProgress(0);
		pm.setMaxProgress(100);
		this.addImagePdfPageBlocks(doc, (useEmbeddedOCR ? pData : null), spm, pageImageCache, null);
		
		//	finally ...
		return doc;
	}
	
	private void addImagePdfPageBlocks(final ImDocument doc, final PPageData[] pData, ProgressMonitor pm, final Map pageImageCache, final Map pageRegionCache) throws IOException {
		
		//	build progress monitor with synchronized methods instead of synchronizing in-code
		final ProgressMonitor spm = ((pm instanceof SynchronizedProgressMonitor) ? pm : new SynchronizedProgressMonitor(pm));
		
		//	get tokenizer to check and split words with
		final Tokenizer tokenizer = ((Tokenizer) doc.getAttribute(ImDocument.TOKENIZER_ATTRIBUTE, Gamta.INNER_PUNCTUATION_TOKENIZER));
		
		//	set up running in parallel
		ParallelFor pf;
		
		//	sanitize embedded OCR words (if any)
		if (pData != null) {
			final ProgressMonitor cpm = new CascadingProgressMonitor(spm);
			
			/* no need for word merging here, as good OCR keeps word together
			 * pretty well, as opposed to obfuscated born-digital PDFs, so
			 * merging would do more harm than good */
			
			//	assess number punctuation
			cpm.setStep("Assessing number punctuation");
			cpm.setBaseProgress(0);
			cpm.setProgress(0);
			cpm.setMaxProgress(3);
			final Tokenizer numberTokenizer = getNumberTokenizer(pData, tokenizer, cpm);
			
			//	split words
			cpm.setStep("Splitting page words");
			cpm.setBaseProgress(3);
			cpm.setProgress(0);
			cpm.setMaxProgress(10);
			pf = new ParallelFor() {
				public void doFor(int p) throws Exception {
					
					//	nothing to work with
					if ((pData[p] == null) || (pData[p].words == null) || (pData[p].words.length == 0))
						return;
					
					//	update status display (might be inaccurate, but better than lock escalation)
					cpm.setInfo("Splitting embedded OCR words in page " + p + " of " + pData.length);
					cpm.setProgress((p * 100) / pData.length);
					
					//	split words that tokenize apart, splitting bounding box based on font measurement
					ArrayList pWordList = new ArrayList();
					for (int w = 0; w < pData[p].words.length; w++) {
						
						//	check tokenization
						TokenSequence pWordTokens = (Gamta.isNumber(pData[p].words[w].str) ? numberTokenizer.tokenize(pData[p].words[w].str) : tokenizer.tokenize(pData[p].words[w].str));
						if (pWordTokens.size() < 2) {
							pWordList.add(pData[p].words[w]);
							continue;
						}
						System.out.println(" - splitting " + pData[p].words[w].str);
						
						//	get width for each token at word font size
						String[] splitCharCodes = new String[pWordTokens.size()];
						String[] splitTokens = new String[pWordTokens.size()];
						float[] splitTokenWidths = new float[pWordTokens.size()];
						int fontStyle = Font.PLAIN;
						if (pData[p].words[w].bold)
							fontStyle = (fontStyle | Font.BOLD);
						if (pData[p].words[w].italics)
							fontStyle = (fontStyle | Font.ITALIC);
						Font pWordFont = getFont(pData[p].words[w].font.name, fontStyle, pData[p].words[w].serif, Math.round(((float) pData[p].words[w].fontSize)));
						float splitTokenWidthSum = 0;
						int splitCharCodeStart = 0;
						for (int s = 0; s < splitTokens.length; s++) {
							splitTokens[s] = pWordTokens.valueAt(s);
							splitCharCodes[s] = ""; // have to do it this way, as char code string might have different length than Unicode string
							for (int splitCharCodeLength = 0; splitCharCodeLength < splitTokens[s].length();) {
								char charCode = pData[p].words[w].charCodes.charAt(splitCharCodeStart++);
								splitCharCodes[s] += ("" + charCode);
								splitCharCodeLength += (((int) charCode) / 256);
							}
							TextLayout tl = new TextLayout(splitTokens[s], pWordFont, new FontRenderContext(null, false, true));
							splitTokenWidths[s] = ((float) tl.getBounds().getWidth());
							splitTokenWidthSum += splitTokenWidths[s];
						}
						
						//	left-right words
						if (pData[p].words[w].fontDirection == PWord.LEFT_RIGHT_FONT_DIRECTION) {
							
							//	store split result, splitting word bounds accordingly
							float pWordWidth = ((float) (pData[p].words[w].bounds.getMaxX() - pData[p].words[w].bounds.getMinX()));
							float splitTokenLeft = ((float) pData[p].words[w].bounds.getMinX());
							for (int s = 0; s < splitTokens.length; s++) {
								float splitTokenWidth = ((pWordWidth * splitTokenWidths[s]) / splitTokenWidthSum);
								PWord spWord = new PWord(splitCharCodes[s], splitTokens[s], new Rectangle2D.Float(splitTokenLeft, ((float) pData[p].words[w].bounds.getMinY()), splitTokenWidth, ((float) pData[p].words[w].bounds.getHeight())), pData[p].words[w].fontSize, pData[p].words[w].fontDirection, pData[p].words[w].font);
								pWordList.add(spWord);
								splitTokenLeft += splitTokenWidth;
								System.out.println(" --> split word part " + spWord.str + ", bounds are " + spWord.bounds);
							}
						}
						
						//	bottom-up words
						else if (pData[p].words[w].fontDirection == PWord.BOTTOM_UP_FONT_DIRECTION) {
							
							//	store split result, splitting word bounds accordingly
							float pWordWidth = ((float) (pData[p].words[w].bounds.getMaxY() - pData[p].words[w].bounds.getMinY()));
							float splitTokenLeft = ((float) pData[p].words[w].bounds.getMinY());
							for (int s = 0; s < splitTokens.length; s++) {
								float splitTokenWidth = ((pWordWidth * splitTokenWidths[s]) / splitTokenWidthSum);
								PWord spWord = new PWord(splitCharCodes[s], splitTokens[s], new Rectangle2D.Float(((float) pData[p].words[w].bounds.getMinX()), splitTokenLeft, ((float) pData[p].words[w].bounds.getWidth()), splitTokenWidth), pData[p].words[w].fontSize, pData[p].words[w].fontDirection, pData[p].words[w].font);
								pWordList.add(spWord);
								splitTokenLeft += splitTokenWidth;
								System.out.println(" --> split word part " + spWord.str + ", bounds are " + spWord.bounds);
							}
						}
						
						//	top-down words
						else if (pData[p].words[w].fontDirection == PWord.TOP_DOWN_FONT_DIRECTION) {
							
							//	store split result, splitting word bounds accordingly
							float pWordWidth = ((float) (pData[p].words[w].bounds.getMaxY() - pData[p].words[w].bounds.getMinY()));
							float splitTokenLeft = ((float) pData[p].words[w].bounds.getMaxY());
							for (int s = 0; s < splitTokens.length; s++) {
								float splitTokenWidth = ((pWordWidth * splitTokenWidths[s]) / splitTokenWidthSum);
								PWord spWord = new PWord(splitCharCodes[s], splitTokens[s], new Rectangle2D.Float(((float) pData[p].words[w].bounds.getMinX()), (splitTokenLeft - splitTokenWidth), ((float) pData[p].words[w].bounds.getWidth()), splitTokenWidth), pData[p].words[w].fontSize, pData[p].words[w].fontDirection, pData[p].words[w].font);
								pWordList.add(spWord);
								splitTokenLeft -= splitTokenWidth;
								System.out.println(" --> split word part " + spWord.str + ", bounds are " + spWord.bounds);
							}
						}
					}
					
					//	refresh PWord array
					if (pWordList.size() != pData[p].words.length)
						pData[p].words = ((PWord[]) pWordList.toArray(new PWord[pWordList.size()]));
					pWordList = null;
					
					//	give status update
					if (pData[p].words.length == 0)
						cpm.setInfo(" --> empty page");
					else cpm.setInfo(" --> got " + pData[p].words.length + " words in PDF");
				}
			};
			ParallelJobRunner.runParallelFor(pf, ((PdfExtractorTest.aimAtPage < 0) ? 0 : PdfExtractorTest.aimAtPage), ((PdfExtractorTest.aimAtPage < 0) ? pData.length : (PdfExtractorTest.aimAtPage + 1)), (this.useMultipleCores ? -1 : 1));
			cpm.setProgress(100);
			
			//	update progress in main monitor
			spm.setBaseProgress(45);
		}
		
		//	get document pages
		final ImPage[] pages = doc.getPages();
		
		//	get document style and use it for page structure analysis
		DocumentStyle docStyle = DocumentStyle.getStyleFor(doc);
		final DocumentStyle docLayout = docStyle.getSubset("layout");
		
		//	do high level structure analysis (down to blocks) and OCR
		pf = new ParallelFor() {
			public void doFor(int p) throws Exception {
				
				//	update status display (might be inaccurate, but better than lock escalation)
				spm.setInfo("Analyzing structure of page " + p + " of " + pages.length);
				spm.setProgress((100 * p) / pages.length);
				
				//	get page image
				PageImage pi = null;
				if (pageImageCache != null)
					pi = ((PageImage) pageImageCache.get(new Integer(pages[p].pageId)));
				if (pi == null)
					pi = pages[p].getPageImage();
				if (pi == null) {
					spm.setInfo(" - page image not found");
					throw new IOException("Could not find image for page " + pages[p].pageId);
				}
				
				//	get page content area layout hint (defaulting to whole page bounds), as well as number of columns
				BoundingBox contentArea = docLayout.getBoxProperty("contentArea", pages[p].bounds, pi.currentDpi);
				int columnCount = docLayout.getIntProperty("columnCount", -1);
				
				//	get column and block margin layout hints (defaulting to kind of universal ball park figures)
				int minBlockMargin = docLayout.getIntProperty("minBlockMargin", (pi.currentDpi / 10), pi.currentDpi);
				int minColumnMargin = ((columnCount == 1) ? (pages[p].bounds.right - pages[p].bounds.left) : docLayout.getIntProperty("minColumnMargin", (pi.currentDpi / 10), pi.currentDpi));
				
				//	get (or compute) column areas to correct erroneous column splits
				BoundingBox[] columnAreas = docLayout.getBoxListProperty("columnAreas", null, pi.currentDpi);
				if (columnAreas == null) {
					if (columnCount == 1) {
						columnAreas = new BoundingBox[1];
						columnAreas[0] = contentArea;
					}
					else if (columnCount == 2) {
						columnAreas = new BoundingBox[2];
						columnAreas[0] = new BoundingBox(contentArea.left, ((contentArea.left + contentArea.right) / 2), contentArea.top, contentArea.bottom);
						columnAreas[1] = new BoundingBox(((contentArea.left + contentArea.right) / 2), contentArea.right, contentArea.top, contentArea.bottom);
					}
					else if ((columnCount != -1) && (contentArea != pages[p].bounds)) {
						columnAreas = new BoundingBox[columnCount];
						for (int c = 0; c < columnCount; c++)
							columnAreas[c] = new BoundingBox((contentArea.left + (((contentArea.right - contentArea.left) * c) / columnCount)), (contentArea.left + (((contentArea.right - contentArea.left) * (c + 1)) / columnCount)), contentArea.top, contentArea.bottom);
					}
				}
				
				//	obtain higher level page structure
				AnalysisImage api = Imaging.wrapImage(pi.image, null);
				Region pageRootRegion = PageImageAnalysis.getPageRegion(api, pi.currentDpi, minColumnMargin, minBlockMargin, columnAreas, false, spm);
				addRegionBlocks(pages[p], pageRootRegion, pi.currentDpi, spm);
				if (pageRegionCache != null)
					synchronized (pageRegionCache) {
						pageRegionCache.put(pages[p], pageRootRegion);
					}
				
				//	add embedded OCR if (a) asked to do so and (b) available
				if ((pData != null) && (pData[pages[p].pageId] != null) && (pData[pages[p].pageId].words != null) && (pData[pages[p].pageId].words.length != 0)) {
					
					//	compute word scale factor
					float magnification = (pData[pages[p].pageId].rawPageImageDpi / defaultDpi);
					
					//	add words to page
					spm.setInfo("Adding embedded OCR words to page " + p + " of " + pData.length);
					for (int w = 0; w < pData[pages[p].pageId].words.length; w++) {
						
						//	make sure word has some minimum width
						BoundingBox wb = getBoundingBox(pData[pages[p].pageId].words[w].bounds, pData[pages[p].pageId].pdfPageBox, magnification, pData[pages[p].pageId].rotate);
						if ((wb.right - wb.left) < 4)
							wb = new BoundingBox(wb.left, (wb.left + 4), wb.top, wb.bottom);
						
						//	if we're on the right half of a double page, shift words to left
						if (((p % 2) == 1) && (pData[pages[p].pageId].rightPageOffset != 0))
							wb = new BoundingBox((wb.left - pData[pages[p].pageId].rightPageOffset), (wb.right - pData[pages[p].pageId].rightPageOffset), wb.top, wb.bottom);
						
						//	add word to page
						ImWord word = new ImWord(pages[p], wb, pData[pages[p].pageId].words[w].str);
						
						//	set layout attributes
						if ((pData[pages[p].pageId].words[w].font != null) && (pData[pages[p].pageId].words[w].font.name != null))
							word.setAttribute(FONT_NAME_ATTRIBUTE, pData[pages[p].pageId].words[w].font.name);
						if (pData[pages[p].pageId].words[w].fontSize != -1)
							word.setAttribute(FONT_SIZE_ATTRIBUTE, ("" + pData[pages[p].pageId].words[w].fontSize));
						if (pData[pages[p].pageId].words[w].bold)
							word.setAttribute(BOLD_ATTRIBUTE);
						if (pData[pages[p].pageId].words[w].italics)
							word.setAttribute(ITALICS_ATTRIBUTE);
						
						//	add font char ID string
						StringBuffer charCodesHex = new StringBuffer();
						for (int c = 0; c < pData[pages[p].pageId].words[w].charCodes.length(); c++) {
							String charCodeHex = Integer.toString((((int) pData[pages[p].pageId].words[w].charCodes.charAt(c)) & 255), 16).toUpperCase();
							if (charCodeHex.length() < 2)
								charCodesHex.append("0");
							charCodesHex.append(charCodeHex);
						}
						word.setAttribute(ImFont.CHARACTER_CODE_STRING_ATTRIBUTE, charCodesHex.toString());
					}
				}
				
				//	do OCR if (a) none embedded or (b) asked to do so
				else {
					
					//	do OCR (might have to try multiple times if for some reason OCR instance pool is smaller than number of parallel threads trying to use it)
					spm.setInfo(" - doing OCR");
					for (int attempts = 0; attempts < 16; attempts++) try {
						ocrEngine.doBlockOcr(pages[p], pi, new CascadingProgressMonitor(spm));
						break;
					}
					catch (OcrInstanceUnavailableException oiue) {
						spm.setInfo(" - re-attempting (" + (attempts+1) + ") OCR for page " + p);
					}
					spm.setInfo(" - OCR done");
				}
				
				//	clean up regions and mark images
				cleanUpRegions(pages[p]);
				
				//	clean up analysis image cache
				Imaging.cleanUpCache(pi.image.hashCode() + "-");
			}
		};
		ParallelJobRunner.runParallelFor(pf, 0, pages.length, (this.useMultipleCores ? -1 : 1));
		spm.setProgress(100);
		
		//	check errors
		this.checkException(pf);
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
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the image markup document
	 * @throws IOException
	 */
	public ImDocument loadImagePdfPages(byte[] pdfBytes, ProgressMonitor pm) throws IOException {
		return this.loadImagePdfPages(null, null, pdfBytes, false, false, 1, pm);
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
	 * @param metaPages expect born-digital meta pages at start or end of PDF?
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the image markup document
	 * @throws IOException
	 */
	public ImDocument loadImagePdfPages(byte[] pdfBytes, boolean metaPages, ProgressMonitor pm) throws IOException {
		return this.loadImagePdfPages(null, null, pdfBytes, metaPages, false, 1, pm);
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
	 * @param metaPages expect born-digital meta pages at start or end of PDF?
	 * @param useEmbeddedOCR use embedded OCR instead of running internal one?
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the image markup document
	 * @throws IOException
	 */
	public ImDocument loadImagePdfPages(byte[] pdfBytes, boolean metaPages, boolean useEmbeddedOCR, ProgressMonitor pm) throws IOException {
		return this.loadImagePdfPages(null, null, pdfBytes, metaPages, useEmbeddedOCR, 1, pm);
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
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the argument image markup document
	 * @throws IOException
	 */
	public ImDocument loadImagePdfPages(ImDocument doc, Document pdfDoc, byte[] pdfBytes, ProgressMonitor pm) throws IOException {
		return this.loadImagePdfPages(doc, pdfDoc, pdfBytes, false, false, 1, pm);
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
	 * @param metaPages expect born-digital meta pages at start or end of PDF?
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the argument image markup document
	 * @throws IOException
	 */
	public ImDocument loadImagePdfPages(ImDocument doc, Document pdfDoc, byte[] pdfBytes, boolean metaPages, ProgressMonitor pm) throws IOException {
		return this.loadImagePdfPages(doc, pdfDoc, pdfBytes, metaPages, false, 1, pm);
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
	 * @param metaPages expect born-digital meta pages at start or end of PDF?
	 * @param useEmbeddedOCR use embedded OCR instead of running internal one?
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the argument image markup document
	 * @throws IOException
	 */
	public ImDocument loadImagePdfPages(ImDocument doc, Document pdfDoc, byte[] pdfBytes, boolean metaPages, boolean useEmbeddedOCR, ProgressMonitor pm) throws IOException {
		return this.loadImagePdfPages(doc, pdfDoc, pdfBytes, metaPages, useEmbeddedOCR, 1, pm);
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
		return this.loadImagePdfPages(doc, pdfDoc, pdfBytes, false, false, scaleFactor, pm);
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
	 * @param metaPages expect born-digital meta pages at start or end of PDF?
	 * @param scaleFactor the scale factor for image PDFs, to correct PDFs that
	 *            are set too small (DPI number is divided by this factor)
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the argument image markup document
	 * @throws IOException
	 */
	public ImDocument loadImagePdfPages(ImDocument doc, Document pdfDoc, byte[] pdfBytes, boolean metaPages, int scaleFactor, ProgressMonitor pm) throws IOException {
		return this.loadImagePdf(doc, pdfDoc, pdfBytes, metaPages, false, scaleFactor, pm);
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
	 * @param metaPages expect born-digital meta pages at start or end of PDF?
	 * @param useEmbeddedOCR use embedded OCR instead of running internal one?
	 * @param scaleFactor the scale factor for image PDFs, to correct PDFs that
	 *            are set too small (DPI number is divided by this factor)
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the argument image markup document
	 * @throws IOException
	 */
	public ImDocument loadImagePdfPages(ImDocument doc, Document pdfDoc, byte[] pdfBytes, boolean metaPages, boolean useEmbeddedOCR, int scaleFactor, ProgressMonitor pm) throws IOException {
		
		//	check arguments
		if (doc == null)
			doc = this.doCreateDocument(getChecksum(pdfBytes));
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
		this.addImagePdfPages(doc, pdfDoc, pdfBytes, metaPages, scaleFactor, pm, null);
		
		//	preserve source PDF in supplement
		ImSupplement.Source.createSource(doc, "application/pdf", pdfBytes);
		
		//	finally ...
		return doc;
	}
	
	private static class PImagePart {
		final int pageId;
		final String name;
		final Rectangle2D pdfBounds;
		final double pdfRotation;
		final PStream data;
		final int dpi;
		final BoundingBox bounds;
		PImagePart(int pageId, String name, Rectangle2D pdfBounds, double pdfRotation, PStream data, int dpi, BoundingBox bounds) {
			this.pageId = pageId;
			this.name = name;
			this.pdfBounds = pdfBounds;
			this.pdfRotation = pdfRotation;
			this.data = data;
			this.dpi = dpi;
			this.bounds = bounds;
		}
	}
	
	private void addImagePdfPages(ImDocument doc, Document pdfDoc, byte[] pdfBytes, boolean metaPages, int scaleFactor, ProgressMonitor pm, Map pageImageCache) throws IOException {
		
		//	build progress monitor with synchronized methods instead of synchronizing in-code
		SynchronizedProgressMonitor spm = ((pm instanceof SynchronizedProgressMonitor) ? ((SynchronizedProgressMonitor) pm) : new SynchronizedProgressMonitor(pm));
		
		//	load document structure (IcePDF is better at that ...)
		Catalog catalog = pdfDoc.getCatalog();
		PageTree pageTree = catalog.getPageTree();
		
		//	parse PDF
		HashMap objects = PdfParser.getObjects(pdfBytes, pdfDoc.getSecurityManager());
		
		//	decrypt streams and strings
		PdfParser.decryptObjects(objects, pdfDoc.getSecurityManager());
		
		//	get basic page data (takes progress to 30%)
		PPageData[] pData = this.getPdfPageData(doc, false, pageTree, objects, spm);
		
		//	add page images
		this.addImagePdfPages(doc, pData, pdfDoc, pageTree, objects, metaPages, scaleFactor, spm, pageImageCache);
	}
	
	private void addImagePdfPages(final ImDocument doc, final PPageData[] pData, final Document pdfDoc, final PageTree pageTree, final Map objects, final boolean metaPages, final int scaleFactor, final SynchronizedProgressMonitor spm, Map pageImageCache) throws IOException {
		
		//	extract page objects
		spm.setInfo("Getting page objects");
		final Page[] pdfPages = new Page[pageTree.getNumberOfPages()];
		for (int p = 0; p < pageTree.getNumberOfPages(); p++)
			pdfPages[p] = pageTree.getPage(p, "");
		spm.setInfo(" --> got " + pageTree.getNumberOfPages() + " page objects");
		
		//	prepare working in parallel
		ParallelFor pf;
		
		//	extract page objects
		spm.setStep("Extracting page figures");
		spm.setBaseProgress(5);
		spm.setProgress(0);
		spm.setMaxProgress(15);
		final ArrayList[] pageImageParts = new ArrayList[pageTree.getNumberOfPages()];
		pf = new ParallelFor() {
			public void doFor(int p) throws Exception {
				
				//	nothing to work with
				if (pData[p] == null)
					return;
				
				//	update status display (might be inaccurate, but better than lock escalation)
				spm.setInfo("Importing page " + p + " of " + pData.length);
				spm.setProgress((p * 100) / pData.length);
				
				//	nothing we can do here
				if (pData[p].figures.length == 0) {
					spm.setInfo(" ==> page image not found");
					return;
				}
				
				//	get XObject dictionary from page resource dictionary
				Object xObjectsObj = PdfParser.dereference(pData[p].pdfPageResources.get("XObject"), objects);
				if (xObjectsObj instanceof Hashtable) {
					Hashtable xObjects = ((Hashtable) xObjectsObj);
					spm.setInfo("   - XObject dictionary is " + xObjects);
					
					//	collect all page figures
					pageImageParts[p] = new ArrayList();
					
					//	store figures / figure parts
					for (int f = 0; f < pData[p].figures.length; f++) {
						spm.setInfo("   - " + pData[p].figures[f]);
						this.addPageImagePart(p, xObjects, pData[p].pdfPageBox, pData[p].rotate, pData[p].figures[f], pageImageParts[p], spm);
					}
					
					//	assess extent and area of page image parts
					float pipMinX = ((float) pData[p].pdfPageBox.getMaxX());
					float pipMaxX = 0;
					float pipMinY = ((float) pData[p].pdfPageBox.getMaxY());
					float pipMaxY = 0;
					float pipAreaSum = 0;
					for (int i = 0; i < pageImageParts[p].size(); i++) {
						PImagePart pip = ((PImagePart) pageImageParts[p].get(i));
						pipMinX = Math.min(pipMinX, ((float) pip.pdfBounds.getMinX()));
						pipMaxX = Math.max(pipMaxX, ((float) pip.pdfBounds.getMaxX()));
						pipMinY = Math.min(pipMinY, ((float) pip.pdfBounds.getMinY()));
						pipMaxY = Math.max(pipMaxY, ((float) pip.pdfBounds.getMaxY()));
						pipAreaSum += ((float) Math.abs(pip.pdfBounds.getWidth() * pip.pdfBounds.getHeight()));
					}
					
					//	combined width of images in page less than 85% of page width, too little for a scan
					if ((Math.abs(pipMaxX - pipMinX) * 20) < (Math.abs(pData[p].pdfPageBox.getWidth()) * 17)) {
						spm.setInfo(" ==> Too narrow (" + (pipMaxX - pipMinX) + " of " + Math.abs(pData[p].pdfPageBox.getWidth()) + "), not a scan");
						pageImageParts[p] = null;
					}
					
					//	combined height of images in page less than 85% of page height, too little for a scan
					else if ((Math.abs(pipMaxY - pipMinY) * 20) < (Math.abs(pData[p].pdfPageBox.getHeight()) * 17)) {
						spm.setInfo(" ==> Too low (" + (pipMaxY - pipMinY) + " of " + Math.abs(pData[p].pdfPageBox.getHeight()) + "), not a scan");
						pageImageParts[p] = null;
					}
					
					//	combined area of images in page less than 80% of page area, too little for a scan
					else if ((Math.abs(pipAreaSum) * 10) < (Math.abs(pData[p].pdfPageBox.getWidth() * pData[p].pdfPageBox.getHeight()) * 8)) {
						spm.setInfo(" ==> Too small (" + pipAreaSum + " of " + Math.abs(pData[p].pdfPageBox.getWidth() * pData[p].pdfPageBox.getHeight()) + "), not a scan");
						pageImageParts[p] = null;
					}
				}
				else spm.setInfo(" ==> XObject dictionary not found, cannot decode images");
			}
			
			private void addPageImagePart(int pageId, Hashtable xObjects, Rectangle2D.Float pageBounds, int rotate, PFigure pFigure, ArrayList pImageParts, ProgressMonitor pm) throws IOException {
				
				//	resolve image names against XObject dictionary
				Object pFigureKey = xObjects.get(pFigure.name);
				pm.setInfo("     - reference is " + pFigureKey);
				
				//	get actual image object
				Object pFigureDataObj = PdfParser.dereference(pFigureKey, objects);
				pm.setInfo("     - figure data is " + pFigureDataObj);
				if (!(pFigureDataObj instanceof PStream)) {
					pm.setInfo("   --> strange figure data");
					return;
				}
				PStream pFigureData = ((PStream) pFigureDataObj);
				
				//	catch forms, which also come as XObject
				if ((pFigureData.params.get("Subtype") != null) && !"Image".equals(pFigureData.params.get("Subtype").toString())){
					pm.setInfo("   --> not an image");
					return;
				}
				
				//	compute image resolution
				int width = ((Number) pFigureData.params.get("Width")).intValue();
				int height = ((Number) pFigureData.params.get("Height")).intValue();
				float dpiRatio = ((float) ((((float) width) / pFigure.bounds.getWidth()) + (((float) height) / pFigure.bounds.getHeight())) / 2);
				float rawDpi = dpiRatio * defaultDpi;
				int pFigureDpi = (Math.round(rawDpi / 10) * 10);
				pm.setInfo("     - resolution computed as " + pFigureDpi + " DPI (" + rawDpi + ")");
				
				//	convert bounds, as PDF Y coordinate is bottom-up, whereas Java, JavaScript, etc. Y coordinate is top-down
				BoundingBox pFigureBounds = getBoundingBox(pFigure.bounds, pageBounds, dpiRatio, rotate);
				
				//	store figure for selection and further processing
				pImageParts.add(new PImagePart(pageId, pFigure.name, pFigure.bounds, pFigure.rotation, pFigureData, pFigureDpi, pFigureBounds));
				
				//	get mask images as well if given (simply recurse)
				if (pFigureData.params.containsKey("Mask")) {
					String pMaskFigureName = (pFigure.name + "_mask");
					xObjects.put(pMaskFigureName, pFigureData.params.get("Mask"));
					pm.setInfo("   - adding mask figure");
					this.addPageImagePart(pageId, xObjects, pageBounds, rotate, new PFigure(pMaskFigureName, pFigure.bounds, pFigure.rotation, pFigure.rightSideLeft, pFigure.upsideDown), pImageParts, pm);
				}
				if (pFigureData.params.containsKey("SMask")) {
					String pMaskFigureName = (pFigure.name + "_smask");
					xObjects.put(pMaskFigureName, pFigureData.params.get("SMask"));
					pm.setInfo("   - adding smask figure");
					this.addPageImagePart(pageId, xObjects, pageBounds, rotate, new PFigure(pMaskFigureName, pFigure.bounds, pFigure.rotation, pFigure.rightSideLeft, pFigure.upsideDown), pImageParts, pm);
				}
			}
		};
		ParallelJobRunner.runParallelFor(pf, ((PdfExtractorTest.aimAtPage < 0) ? 0 : PdfExtractorTest.aimAtPage), ((PdfExtractorTest.aimAtPage < 0) ? pageTree.getNumberOfPages() : (PdfExtractorTest.aimAtPage + 1)), (this.useMultipleCores ? -1 : 1));
		spm.setProgress(100);
		
		//	check errors
		this.checkException(pf);
		
		//	assess result
		int minPageImages = Integer.MAX_VALUE;
		int maxPageImages = 0;
		int pageImageCount = 0;
		HashSet noImagePageIDs = new HashSet();
		for (int p = 0; p < pdfPages.length; p++) {
			if ((PdfExtractorTest.aimAtPage != -1) && (PdfExtractorTest.aimAtPage != p))
				continue;
			if (pageImageParts[p] == null) {
				noImagePageIDs.add(new Integer(p));
				if (!metaPages || ((p >= 2) && (p < (pageImageParts.length - 2))))
					minPageImages = 0;
			}
			else {
				minPageImages = Math.min(minPageImages, pageImageParts[p].size());
				maxPageImages = Math.max(maxPageImages, pageImageParts[p].size());
				pageImageCount += pageImageParts[p].size();
			}
		}
		spm.setInfo(" --> got at least " + minPageImages + ", at most " + maxPageImages + " images per page, " + pageImageCount + " in total");
		
		//	check consistency
		if (minPageImages == 0)
			throw new IOException("Unable to find images for all pages");
		if (noImagePageIDs.size() != 0) {
			
			//	too many meta pages
			if (noImagePageIDs.size() > 2)
				throw new IOException("Unable to find images for all pages");
			else if (metaPages) {
				
				//	one or two leading meta pages
				if (noImagePageIDs.contains(new Integer(0)) && ((noImagePageIDs.size() == 1) || noImagePageIDs.contains(new Integer(1))))
					spm.setInfo(" --> ignoring " + noImagePageIDs.size() + " leading meta page(s)");
				
				//	one or two tailing meta pages
				else if (noImagePageIDs.contains(new Integer(pageImageParts.length - 1)) && ((noImagePageIDs.size() == 1) || noImagePageIDs.contains(new Integer(pageImageParts.length - 2))))
					spm.setInfo(" --> ignoring " + noImagePageIDs.size() + " tailing meta page(s)");
				
				//	something else, something weird
				else throw new IOException("Unable to find images for all pages");
			}
			
			//	no meta pages expected
			else throw new IOException("Unable to find images for all pages");
		}
		
		
		//	extract page bounds and assess format
		spm.setInfo("Determining page proportions");
		Rectangle2D.Float[] pdfPageBoxes = new Rectangle2D.Float[pData.length];
		Rectangle2D.Float[] sPdfPageBoxes = new Rectangle2D.Float[pData.length];
		int portraitPageCount = 0;
		int landscapePageCount = 0;
		int overA4widthPageCount = 0;
		int overA4heightPageCount = 0;
		for (int p = 0; p < pageTree.getNumberOfPages(); p++) {
			pdfPageBoxes[p] = ((pData[p] == null) ? pageTree.getPage(p, "").getMediaBox() : pData[p].pdfPageBox);
			if (noImagePageIDs.contains(new Integer(p)))
				continue;
			if ((pdfPageBoxes[p].getWidth() * 5) < (pdfPageBoxes[p].getHeight() * 4)) {
				portraitPageCount++;
				if (pdfPageBoxes[p].width > (a4inchWidth * defaultDpi))
					overA4widthPageCount++;
				if (pdfPageBoxes[p].height > (a4inchHeigth * defaultDpi))
					overA4heightPageCount++;
			}
			else if ((pdfPageBoxes[p].getHeight() * 5) < (pdfPageBoxes[p].getWidth() * 4)) {
				landscapePageCount++;
				if (pdfPageBoxes[p].width > (a4inchWidth * 2 * defaultDpi))
					overA4widthPageCount++;
				if (pdfPageBoxes[p].width > (a4inchHeigth * defaultDpi))
					overA4heightPageCount++;
			}
		}
		if (portraitPageCount < landscapePageCount)
			spm.setInfo(" ==> pages are landscape, likely double pages");
		else spm.setInfo(" ==> pages are portrait, likely single pages");
		if (((overA4widthPageCount * 2) > pdfPageBoxes.length) && ((overA4heightPageCount * 2) > pdfPageBoxes.length)) {
			spm.setInfo("Page boxes seem to be out of bounds, scaling");
			int maxOverA5dpi = Integer.MAX_VALUE;
			for (int p = 0; p < pageTree.getNumberOfPages(); p++) {
				if (noImagePageIDs.contains(new Integer(p)))
					continue;
				for (int s = (scaleUpDpi.length - 1); s >= 0; s--) {
					if ((pdfPageBoxes[p].height) > (a5inchHeigth * scaleUpDpi[s])) {
						maxOverA5dpi = Math.min(maxOverA5dpi, scaleUpDpi[s]);
						break;
					}
					else if ((pdfPageBoxes[p].width) > (a5inchWidth * ((portraitPageCount < landscapePageCount) ? 2 : 1) * scaleUpDpi[s])) {
						maxOverA5dpi = Math.min(maxOverA5dpi, scaleUpDpi[s]);
						break;
					}
				}
			}
			if (maxOverA5dpi == Integer.MAX_VALUE)
				spm.setInfo(" ==> page boxes seem to be OK after all");
			else {
				spm.setInfo(" ==> maximum DPI over A5 page size is " + maxOverA5dpi);
				for (int p = 0; p < pageTree.getNumberOfPages(); p++) {
					if (noImagePageIDs.contains(new Integer(p)))
						continue;
					sPdfPageBoxes[p] = new Rectangle2D.Float(((pdfPageBoxes[p].x * defaultDpi) / maxOverA5dpi), ((pdfPageBoxes[p].y * defaultDpi) / maxOverA5dpi), ((pdfPageBoxes[p].width * defaultDpi) / maxOverA5dpi), ((pdfPageBoxes[p].height * defaultDpi) / maxOverA5dpi));
					spm.setInfo(" - " + pdfPageBoxes[p] + " scaled to " + sPdfPageBoxes[p]);
				}
			}
		}
		
		//	extract & save page images
		if (portraitPageCount < landscapePageCount)
			this.addImagePdfPagesDouble(doc, scaleFactor, pdfDoc, pdfPages, pdfPageBoxes, sPdfPageBoxes, pData, pageImageParts, noImagePageIDs, objects, pageImageCache, spm);
		else this.addImagePdfPagesSingle(doc, scaleFactor, pdfDoc, pdfPages, pdfPageBoxes, sPdfPageBoxes, pData, pageImageParts, noImagePageIDs, objects, pageImageCache, spm);
		
		//	check errors
		this.checkException(pf);
		
		//	garbage collect page images
		System.gc();
	}
	
	private void addImagePdfPagesSingle(final ImDocument doc, final int scaleFactor, final Document pdfDoc, final Page[] pdfPages, final Rectangle2D.Float[] pdfPageBoxes, final Rectangle2D.Float[] sPdfPageBoxes, final PPageData[] pData, final ArrayList[] pageImageParts, final Set noImagePageIDs, final Map objects, final Map pageImageCache, final SynchronizedProgressMonitor spm) throws IOException {
		
		//	extract & save page images
		final BoundingBox[] pageBoxes = new BoundingBox[pdfPages.length];
		ParallelFor pf = new ParallelFor() {
			public void doFor(int p) throws Exception {
				
				//	nothing to work with
				if (pageImageParts[p] == null)
					return;
				
				//	update status display (might be inaccurate, but better than lock escalation)
				spm.setStep("Importing page " + p + " of " + pdfPages.length);
				spm.setProgress(30 + ((p * 70) / pdfPages.length));
				spm.setInfo(" - getting page image");
				
				//	adjust to ignore meta pages
				if (noImagePageIDs.contains(new Integer(p)))
					return;
				int pDelta = (noImagePageIDs.contains(new Integer(0)) ? noImagePageIDs.size() : 0);
				
				//	image already extracted
				if (imageStore.isPageImageAvailable(doc.docId, (p - pDelta))) {
					spm.setInfo(" --> image already extracted");
					spm.setInfo(" - getting image data ...");
					PageImageInputStream piis = imageStore.getPageImageAsStream(doc.docId, (p - pDelta));
					piis.close();
					
					//	check DPI to exclude page images from earlier text based import import (can happen due to embedded OCR)
					if (piis.currentDpi != defaultTextPdfPageImageDpi) {
						pageBoxes[p] = new BoundingBox(0, ((piis.originalWidth * piis.currentDpi) / piis.originalDpi), 0, ((piis.originalHeight * piis.currentDpi) / piis.originalDpi));
						spm.setInfo(" - resolution is " + piis.currentDpi + " DPI, page bounds are " + pageBoxes[p].toString());
						return;
					}
				}
				
				//	get raw image
				BufferedImage pageImage = getPageImage(pageImageParts[p], objects, pdfDoc, pdfPages[p], pdfPageBoxes[p], spm);
				if (pageImage == null) {
					spm.setInfo(" --> page image generation failed");
					throw new IOException("Could not generate image for page " + p);
				}
				spm.setInfo(" - got page image sized " + pageImage.getWidth() + " x " + pageImage.getHeight());
				
				//	compute DPI
				int dpiScaleFactor = scaleFactor;
				float dpiRatioWidth = (((float) pageImage.getWidth()) / pdfPageBoxes[p].width);
				float dpiRatioHeight = (((float) pageImage.getHeight()) / pdfPageBoxes[p].height);
				if (((dpiRatioWidth * defaultDpi) < 100) && ((dpiRatioHeight * defaultDpi) < 100) && (sPdfPageBoxes[p] != null)) {
					dpiRatioWidth = (((float) pageImage.getWidth()) / sPdfPageBoxes[p].width);
					dpiRatioHeight = (((float) pageImage.getHeight()) / sPdfPageBoxes[p].height);
					spm.setInfo(" - using scaled resolution");
				}
				float dpiRatio = ((dpiRatioWidth + dpiRatioHeight) / 2);
				float rawDpi = dpiRatio * defaultDpi;
				int dpi = ((Math.round(rawDpi / 10) * 10) / dpiScaleFactor);
				spm.setInfo(" - resolution computed as " + dpi + " DPI (" + rawDpi + ")");
				
				//	store raw (accurate) DPI with page data
				pData[p].rawPageImageDpi = rawDpi;
				
				//	guess DPI based on page size (minimum page size for (very most) publications should be A5 or so)
				float a5distWidth = Math.abs((pdfPageBoxes[p].width / defaultDpi) - a5inchWidth);
				float a5distHeigth = Math.abs((pdfPageBoxes[p].height / defaultDpi) - a5inchHeigth);
				float a7distWidth = Math.abs((pdfPageBoxes[p].width / defaultDpi) - (a5inchWidth / 2));
				float a7distHeigth = Math.abs((pdfPageBoxes[p].height / defaultDpi) - (a5inchHeigth / 2));
				while ((a7distWidth < a5distWidth) && (a7distHeigth < a5distHeigth) && (minScaleUpDpi < dpi)) {
					spm.setInfo(" - A5 dist is " + a5distWidth + " / " + a5distHeigth);
					spm.setInfo(" - A7 dist is " + a7distWidth + " / " + a7distHeigth);
					dpiScaleFactor++;
					dpi = ((Math.round(rawDpi / 10) * 10) / dpiScaleFactor);
					a5distWidth = Math.abs(((pdfPageBoxes[p].width * dpiScaleFactor) / defaultDpi) - a5inchWidth);
					a5distHeigth = Math.abs(((pdfPageBoxes[p].height * dpiScaleFactor) / defaultDpi) - a5inchHeigth);
					a7distWidth = Math.abs(((pdfPageBoxes[p].width * dpiScaleFactor) / defaultDpi) - (a5inchWidth / 2));
					a7distHeigth = Math.abs(((pdfPageBoxes[p].height * dpiScaleFactor) / defaultDpi) - (a5inchHeigth / 2));
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
					ImSupplement.Scan.createScan(doc, (p - pDelta), dpi, pageImage);
				}
				
				//	enhance image (cannot use cache here, as image might change during correction)
				AnalysisImage ai = Imaging.wrapImage(pageImage, null);
				spm.setInfo(" - enhancing image ...");
				ai = Imaging.correctImage(ai, dpi, spm);
				pageImage = ai.getImage();
				
				//	store image
				String imageName;
				synchronized (imageStore) {
					imageName = imageStore.storePageImage(doc.docId, (p - pDelta), pageImage, dpi);
				}
				if (imageName == null) {
					spm.setInfo(" --> page image storage failed");
					throw new IOException("Could not store image for page " + p);
				}
				if (pageImageCache != null)
					pageImageCache.put(new Integer((p - pDelta)), new PageImage(pageImage, dpi, imageStore)); // specifying image store from outside indicates who's handling the image now, as we know it was stored successfully
				Imaging.cleanUpCache(pageImage.hashCode() + "-");
				
				//	compute page bounds
				pageBoxes[p] = new BoundingBox(0, pageImage.getWidth(), 0, pageImage.getHeight());
				spm.setInfo(" --> page image stored, page bounds are " + pageBoxes[p].toString());
			}
		};
		ParallelJobRunner.runParallelFor(pf, ((PdfExtractorTest.aimAtPage < 0) ? 0 : PdfExtractorTest.aimAtPage), ((PdfExtractorTest.aimAtPage < 0) ? pdfPageBoxes.length : (PdfExtractorTest.aimAtPage + 1)), (this.useMultipleCores ? -1 : 1));
		spm.setProgress(99);
		
		//	check errors
		this.checkException(pf);
		
		//	assemble document
		for (int p = 0; p < pdfPageBoxes.length; p++) {
			if ((PdfExtractorTest.aimAtPage != -1) && (PdfExtractorTest.aimAtPage != p))
				continue;
			
			//	adjust to ignore meta pages
			if (noImagePageIDs.contains(new Integer(p)))
				continue;
			int pDelta = (noImagePageIDs.contains(new Integer(0)) ? noImagePageIDs.size() : 0);
			
			//	create page
			ImPage page = new ImPage(doc, (p - pDelta), pageBoxes[p]);
		}
		
		//	finally ...
		spm.setProgress(100);
	}
	
	private void addImagePdfPagesDouble(final ImDocument doc, final int scaleFactor, final Document pdfDoc, final Page[] pdfPages, final Rectangle2D.Float[] pdfPageBoxes, final Rectangle2D.Float[] sPdfPageBoxes, final PPageData[] pData, final ArrayList[] pageImageParts, final Set noImagePageIDs, final Map objects, final Map pageImageCache, final SynchronizedProgressMonitor spm) throws IOException {
		
		//	extract & save page images
		final BoundingBox[] pageBoxes = new BoundingBox[pdfPages.length * 2];
		ParallelFor pf = new ParallelFor() {
			public void doFor(int pp) throws Exception {
				
				//	nothing to work with
				if (pageImageParts[pp] == null)
					return;
				
				//	update status display (might be inaccurate, but better than lock escalation)
				spm.setStep("Importing double page " + pp + " of " + pdfPages.length);
				spm.setProgress(30 + ((pp * 70) / pdfPages.length));
				spm.setInfo(" - getting double page image");
				
				//	adjust to ignore meta pages
				if (noImagePageIDs.contains(new Integer(pp)))
					return;
				int ppDelta = (noImagePageIDs.contains(new Integer(0)) ? noImagePageIDs.size() : 0);
				
				//	images already extracted
				if (imageStore.isPageImageAvailable(doc.docId, ((pp * 2) - ppDelta)) && imageStore.isPageImageAvailable(doc.docId, ((pp * 2) - ppDelta + 1))) {
					spm.setInfo(" --> image halves already extracted");
					spm.setInfo(" - getting image data ...");
					PageImageInputStream piisLeft = imageStore.getPageImageAsStream(doc.docId, ((pp * 2) - ppDelta));
					piisLeft.close();
					PageImageInputStream piisRight = imageStore.getPageImageAsStream(doc.docId, ((pp * 2) - ppDelta + 1));
					piisRight.close();
					
					//	check DPI to exclude page images from earlier text based import import (can happen due to embedded OCR)
					if ((piisLeft.currentDpi != defaultTextPdfPageImageDpi) && (piisRight.currentDpi != defaultTextPdfPageImageDpi)) {
						pageBoxes[pp * 2] = new BoundingBox(0, ((piisLeft.originalWidth * piisLeft.currentDpi) / piisLeft.originalDpi), 0, ((piisLeft.originalHeight * piisLeft.currentDpi) / piisLeft.originalDpi));
						spm.setInfo(" - resolution left is " + piisLeft.currentDpi + " DPI, page bounds are " + pageBoxes[pp * 2].toString());
						pageBoxes[(pp * 2) + 1] = new BoundingBox(0, ((piisRight.originalWidth * piisRight.currentDpi) / piisRight.originalDpi), 0, ((piisRight.originalHeight * piisRight.currentDpi) / piisRight.originalDpi));
						spm.setInfo(" - resolution right is " + piisRight.currentDpi + " DPI, page bounds are " + pageBoxes[(pp * 2) + 1].toString());
						return;
					}
				}
				
				//	get raw image
				BufferedImage doublePageImage = getPageImage(pageImageParts[pp], objects, pdfDoc, pdfPages[pp], pdfPageBoxes[pp], spm);
				if (doublePageImage == null) {
					spm.setInfo(" --> page image generation failed");
					throw new IOException("Could not generate image for double page " + pp);
				}
				spm.setInfo(" - got double page image sized " + doublePageImage.getWidth() + " x " + doublePageImage.getHeight());
				
				//	compute DPI
				int dpiScaleFactor = scaleFactor;
				float dpiRatioWidth = (((float) doublePageImage.getWidth()) / pdfPageBoxes[pp].width);
				float dpiRatioHeight = (((float) doublePageImage.getHeight()) / pdfPageBoxes[pp].height);
				if (((dpiRatioWidth * defaultDpi) < 100) && ((dpiRatioHeight * defaultDpi) < 100) && (sPdfPageBoxes[pp] != null)) {
					dpiRatioWidth = (((float) doublePageImage.getWidth()) / sPdfPageBoxes[pp].width);
					dpiRatioHeight = (((float) doublePageImage.getHeight()) / sPdfPageBoxes[pp].height);
					spm.setInfo(" - using scaled resolution");
				}
				float dpiRatio = ((dpiRatioWidth + dpiRatioHeight) / 2);
				final float rawDpi = dpiRatio * defaultDpi;
				int dpi = ((Math.round(rawDpi / 10) * 10) / dpiScaleFactor);
				spm.setInfo(" - resolution computed as " + dpi + " DPI");
				
				//	store raw (accurate) DPI with page data
				pData[pp].rawPageImageDpi = rawDpi;
				
				//	guess DPI based on page size (minimum page size for (very most) publications should be A5 or so, twice A5 here)
				float a5distWidth = Math.abs((pdfPageBoxes[pp].width / defaultDpi) - (a5inchWidth * 2));
				float a5distHeigth = Math.abs((pdfPageBoxes[pp].height / defaultDpi) - a5inchHeigth);
				float a7distWidth = Math.abs((pdfPageBoxes[pp].width / defaultDpi) - a5inchWidth);
				float a7distHeigth = Math.abs((pdfPageBoxes[pp].height / defaultDpi) - (a5inchHeigth / 2));
				while ((a7distWidth < a5distWidth) && (a7distHeigth < a5distHeigth) && (minScaleUpDpi < dpi)) {
					spm.setInfo(" - A5 dist is " + a5distWidth + " / " + a5distHeigth);
					spm.setInfo(" - A7 dist is " + a7distWidth + " / " + a7distHeigth);
					dpiScaleFactor++;
					dpi = ((Math.round(rawDpi / 10) * 10) / dpiScaleFactor);
					a5distWidth = Math.abs(((pdfPageBoxes[pp].width * dpiScaleFactor) / defaultDpi) - (a5inchWidth * 2));
					a5distHeigth = Math.abs(((pdfPageBoxes[pp].height * dpiScaleFactor) / defaultDpi) - a5inchHeigth);
					a7distWidth = Math.abs(((pdfPageBoxes[pp].width * dpiScaleFactor) / defaultDpi) - a5inchWidth);
					a7distHeigth = Math.abs(((pdfPageBoxes[pp].height * dpiScaleFactor) / defaultDpi) - (a5inchHeigth / 2));
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
				
				//	store offset in right page, for positioning embedded OCR
				pData[pp].rightPageOffset = (doublePageImage.getWidth() / 2);
				
				//	preserve original page image halves
				synchronized (doc) {
					ImSupplement.Scan.createScan(doc, ((pp * 2) - ppDelta), dpi, pageImageLeft);
					ImSupplement.Scan.createScan(doc, ((pp * 2) - ppDelta + 1), dpi, pageImageRight);
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
				
				//	store image halves
				String imageNameLeft;
				synchronized (imageStore) {
					imageNameLeft = imageStore.storePageImage(doc.docId, ((pp * 2) - ppDelta), pageImageLeft, dpi);
				}
				if (imageNameLeft == null) {
					spm.setInfo(" --> page image storage failed for left half");
					throw new IOException("Could not store left half of image for double page " + pp);
				}
				if (pageImageCache != null)
					pageImageCache.put(new Integer((pp * 2) - ppDelta), new PageImage(pageImageLeft, dpi, imageStore)); // specifying image store from outside indicates who's handling the image now, as we know it was stored successfully
				Imaging.cleanUpCache(pageImageLeft.hashCode() + "-");
				String imageNameRight;
				synchronized (imageStore) {
					imageNameRight = imageStore.storePageImage(doc.docId, ((pp * 2) - ppDelta + 1), pageImageRight, dpi);
				}
				if (imageNameRight == null) {
					spm.setInfo(" --> page image storage failed for right half");
					throw new IOException("Could not store right half of image for double page " + pp);
				}
				if (pageImageCache != null)
					pageImageCache.put(new Integer((pp * 2) - ppDelta + 1), new PageImage(pageImageRight, dpi, imageStore)); // specifying image store from outside indicates who's handling the image now, as we know it was stored successfully
				Imaging.cleanUpCache(pageImageRight.hashCode() + "-");
				
				//	compute page bounds
				pageBoxes[pp * 2] = new BoundingBox(0, pageImageLeft.getWidth(), 0, pageImageLeft.getHeight());
				pageBoxes[(pp * 2) + 1] = new BoundingBox(0, pageImageRight.getWidth(), 0, pageImageRight.getHeight());
				spm.setInfo(" --> double page image halves stored, page bounds are " + pageBoxes[pp * 2].toString() + " and " + pageBoxes[(pp * 2) + 1].toString());
			}
		};
		ParallelJobRunner.runParallelFor(pf, ((PdfExtractorTest.aimAtPage < 0) ? 0 : PdfExtractorTest.aimAtPage), ((PdfExtractorTest.aimAtPage < 0) ? pdfPageBoxes.length : (PdfExtractorTest.aimAtPage + 1)), (this.useMultipleCores ? -1 : 1));
		spm.setProgress(99);
		
		//	check errors
		this.checkException(pf);
		
		//	assemble document (two pages per PDF page here)
		for (int pp = 0; pp < pdfPages.length; pp++) {
			if ((PdfExtractorTest.aimAtPage != -1) && (PdfExtractorTest.aimAtPage != pp))
				continue;
			
			//	adjust to ignore meta pages
			if (noImagePageIDs.contains(new Integer(pp)))
				continue;
			int ppDelta = (noImagePageIDs.contains(new Integer(0)) ? noImagePageIDs.size() : 0);
			
			//	create pages
			ImPage pageLeft = new ImPage(doc, ((pp * 2) - ppDelta), pageBoxes[pp * 2]);
			ImPage pageRight = new ImPage(doc, ((pp * 2) - ppDelta + 1), pageBoxes[(pp * 2) + 1]);
		}
		
		//	finally ...
		spm.setProgress(100);
	}
	
	private BufferedImage getPageImage(ArrayList pageImageParts, Map objects, Document pdfDoc, Page pdfPage, Rectangle2D.Float pdfPageBox, ProgressMonitor spm) throws IOException {
		
		//	sort out figures overpainted later
		for (int p = 0; p < pageImageParts.size(); p++) {
			PImagePart pip = ((PImagePart) pageImageParts.get(p));
			for (int cp = (p+1); cp < pageImageParts.size(); cp++) {
				PImagePart cpip = ((PImagePart) pageImageParts.get(cp));
				if (overlapsConsiderably(pip.pdfBounds, cpip.pdfBounds)) {
					pageImageParts.remove(p--);
					spm.setInfo("Excluded page image part for being overpainted by " + cpip.name + "@" + cpip.pdfBounds + ": " + pip.name + "@" + pip.pdfBounds);
					break;
				}
			}
		}
		
		//	single full page image (at least 99% of page size), use it as it is
		if ((pageImageParts.size() == 1) && ((getSize(((PImagePart) pageImageParts.get(0)).pdfBounds) * 100) > (getSize(pdfPageBox) * 99))) {
			spm.setInfo("Using single full-page image");
			return this.getPageImagePart(((PImagePart) pageImageParts.get(0)), objects, pdfDoc, pdfPage, spm);
		}
		
		//	compute rendering resolution
		int maxPipDpi = 0;
		for (int p = 0; p < pageImageParts.size(); p++) {
			int pipDpi = ((PImagePart) pageImageParts.get(p)).dpi;
			int pipScaleDownFactor = 1;
			while ((pipDpi / pipScaleDownFactor) >= minScaleDownDpi)
				pipScaleDownFactor++;
			pipDpi /= pipScaleDownFactor;
			maxPipDpi = Math.max(maxPipDpi, pipDpi);
		}
		spm.setInfo("Rendering resolution computed as " + maxPipDpi + " DPI from " + pageImageParts.size() + " page image parts");
		
		//	create base image
		int piWidth = Math.round(Math.abs(pdfPageBox.width * maxPipDpi) / defaultDpi);
		int piHeight = Math.round(Math.abs(pdfPageBox.height * maxPipDpi) / defaultDpi);
		spm.setInfo("Rendering page image sized " + piWidth + "x" + piHeight);
		BufferedImage pageImage = new BufferedImage(piWidth, piHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D piGraphics = pageImage.createGraphics();
		piGraphics.setColor(Color.WHITE);
		piGraphics.fillRect(0, 0, piWidth, piHeight);
		
		//	render remaining figures
		for (int p = 0; p < pageImageParts.size(); p++) {
			PImagePart pip = ((PImagePart) pageImageParts.get(p));
			AffineTransform at = null;
			if (pip.dpi != maxPipDpi) {
				at = piGraphics.getTransform();
				piGraphics.scale((((double) maxPipDpi) / pip.dpi), (((double) maxPipDpi) / pip.dpi));
			}
			spm.setInfo(" - rendering page image part sized " + (pip.bounds.right - pip.bounds.left) + "x" + (pip.bounds.bottom - pip.bounds.top) + " at " + pip.dpi + " scaled by " + (((double) maxPipDpi) / pip.dpi));
			BufferedImage rPip = this.getPageImagePart(pip, objects, pdfDoc, pdfPage, spm);
			piGraphics.drawImage(rPip, pip.bounds.left, pip.bounds.top, null);
			if (at != null)
				piGraphics.setTransform(at);
		}
		
		//	finally ...
		piGraphics.dispose();
		return pageImage;
	}
	
	private static final boolean overlapsConsiderably(Rectangle2D rect1, Rectangle2D rect2) {
		if (!rect1.intersects(rect2))
			return false;
		if (rect1.contains(rect2) || rect2.contains(rect1))
			return true;
		Rectangle2D uRect = new Rectangle2D.Float();
		Rectangle2D.union(rect1, rect2, uRect);
		Rectangle2D iRect = new Rectangle2D.Float();
		Rectangle2D.intersect(rect1, rect2, iRect);
		return ((getSize(iRect) * 10) > (getSize(uRect) * 9));
	}
	
	private static final double getSize(Rectangle2D rect) {
		return Math.abs(rect.getWidth() * rect.getHeight());
	}
	
	private BufferedImage getPageImagePart(PImagePart pip, Map objects, Document pdfDoc, Page pdfPage, ProgressMonitor spm) throws IOException {
		
		//	decode PStream to image
		BufferedImage pipBi = decodeImage(pdfPage, pip.data.params, pip.data.bytes, objects);
		if (pipBi == null) {
			spm.setInfo("   --> could not decode figure");
			return null;
		}
		spm.setInfo("     - got figure sized " + pipBi.getWidth() + " x " + pipBi.getHeight());
		
		//	interpret figure rotation (if above some 0.1°)
		if (Math.abs(pip.pdfRotation) > 0.0005) {
			pipBi = Imaging.rotateImage(pipBi, pip.pdfRotation);
			spm.setInfo("     - figure rotated by " + ((180.0 / Math.PI) * pip.pdfRotation) + "°");
		}
		
		Object csObj = PdfParser.dereference(pip.data.params.get("ColorSpace"), objects);
		if (csObj != null) {
			spm.setInfo("     - color space is " + csObj.toString());
			/* If color space is DeviceCMYK, render page image to figure
			 * resolution and cut out image: images with color space
			 * DeviceCMYK come out somewhat differently colored than in
			 * Acrobat, but we cannot seem to do anything about this -
			 * IcePDF distorts them as well, if more in single image
			 * rendering than in the page images it generates. */
			if ("DeviceCMYK".equals(csObj.toString())) {
				pipBi = extractFigureFromPageImage(pdfDoc, pip.pageId, pip.dpi, pip.bounds);
				spm.setInfo("     --> figure re-rendered as part of page image");
			}
			/* If color space is Separation, values represent color
			 * intensity rather than brightness, which means 1.0 is
			 * black and 0.0 is white. This means the have to invert
			 * brightness to get the actual image. IcePDF gets this
			 * wrong as well, with images coming out white on black
			 * rather than the other way around, so we have to do the
			 * correction ourselves. */
			else if ((csObj instanceof Vector) && (((Vector) csObj).size() == 4) && ((Vector) csObj).get(0).equals("Separation")) {
				invertFigureBrightness(pipBi);
				spm.setInfo("     --> figure brightness inverted for additive 'Separation' color space");
			}
			/* If color space is Indexed, IcePDF seems to do a lot better
			 * in page image rendering than in single-image rendering. */
			else if ((csObj instanceof Vector) && (((Vector) csObj).size() == 4) && ((Vector) csObj).get(0).equals("Indexed")) {
				pipBi = extractFigureFromPageImage(pdfDoc, pip.pageId, pip.dpi, pip.bounds);
				spm.setInfo("     --> figure re-rendered as part of page image");
			}
//			/* Just taking a look a ICCBased color space for now ... IcePDF
//			 * doesn't really do better at those, as it seems ... */
//			else if ((csObj instanceof Vector) && (((Vector) csObj).size() == 2) && ((Vector) csObj).get(0).equals("ICCBased")) {
//				Object csEntryObj = PdfParser.dereference(((Vector) csObj).get(1), objects);
//				spm.setInfo("     --> ICCBased color space");
//				spm.setInfo("     --> ICCBased color space entry is " + csEntryObj);
//				if (csEntryObj instanceof PStream) {
//					PStream csEntry = ((PStream) csEntryObj);
//					Object csFilter = csEntry.params.get("Filter");
//					ByteArrayOutputStream csBaos = new ByteArrayOutputStream();
//					PdfParser.decode(csFilter, csEntry.bytes, csEntry.params, csBaos, objects);
//					System.out.println(new String(csBaos.toByteArray()));
//				}
//			}
		}
		
		//	finally ...
		return pipBi;
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
	
	private BufferedImage decodeImage(Page pdfPage, Hashtable params, byte[] stream, Map objects) throws IOException {
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
		System.out.println(" ==> filter is " + filter);
		System.out.println(" ==> params are " + params);
		if ((filter == null) && (params.get("Resources") instanceof Hashtable) && (params.get("Type") != null) && "XObject".equals(params.get("Type").toString())) {
			//	get XObject image and recurse
			System.out.println(new String(stream));
			Hashtable resources = ((Hashtable) params.get("Resources"));
			Hashtable xObject = ((Hashtable) PdfParser.dereference(resources.get("XObject"), objects));
			System.out.println(" ==> getting XObject figures from " + xObject);
			PFigure[] xpFigures = PdfParser.getPageFigures(pdfPage.getEntries(), stream, resources, objects, ProgressMonitor.dummy);
			for (int f = 0; f < xpFigures.length; f++) {
				System.out.println("   - " + xpFigures[f].name + " @" + xpFigures[f].bounds);
				Object xpFigureObj = PdfParser.getObject(xObject, xpFigures[f].name, objects);
				if (xpFigureObj instanceof PStream)
					return this.decodeImage(pdfPage, ((PStream) xpFigureObj).params, ((PStream) xpFigureObj).bytes, objects);
			}
			return null;
		}
		else if ((filter != null) && "JPXDecode".equals(filter.toString())) {
			System.out.println(" ==> decoding JPX");
			return this.decodeJPX(stream);
		}
		else if ((filter != null) && "JBIG2Decode".equals(filter.toString())) {
			System.out.println(" ==> decoding JBIG2");
			//	JPedal seems to be the one ...
			return this.decodeJBig2(stream);
		}
		else if ((filter != null) && "FlateDecode".equals(filter.toString())) {
			System.out.println(" ==> decoding Flate");
			//	TODO use java.util.zip.GZIPInputStream instead of IcePDF
			return this.decodeFlate(stream, params, pdfPage.getLibrary(), pdfPage.getResources());
		}
		else {
			System.out.println(" ==> decoding other");
			return this.decodeOther(stream, params, filter, pdfPage.getLibrary(), pdfPage.getResources());
		}
	}
	
	private BufferedImage decodeJPX(byte[] stream) throws IOException {
		return this.decodeImageMagick(stream, "jp2");
	}
	
	private BufferedImage decodeImageMagick(byte[] stream, String format) throws IOException {
		if (this.imageDecoder == null) {
			System.out.println(" ==> Image Magick not available");
			return null;
		}
		else {
			System.out.println(" ==> decoding via Image Magick");
			return this.imageDecoder.decodeImage(stream, format);
		}
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
	
	private BufferedImage decodeFlate(byte[] stream, Hashtable params, Library library, Resources resources) throws IOException {
		BufferedImage dRgbBi = this.decodeDeviceRGB(stream, "FlateDecode", params, library, resources);
		if (dRgbBi != null)
			return dRgbBi;
		
		SeekableInputConstrainedWrapper streamInputWrapper = new SeekableInputConstrainedWrapper(new SeekableByteArrayInputStream(stream), 0, stream.length, true);
		Stream str = new Stream(library, params, streamInputWrapper);
		try {
			Color biBackgroundColor = this.getBackgroundColor(params);
			BufferedImage bi = str.getImage(biBackgroundColor, resources, false);
			if (bi != null)
				System.out.println(" ==> got flate image, size is " + bi.getWidth() + " x " + bi.getHeight());
			else System.out.println(" ==> Could not decode image");
			return bi;
		}
		catch (Exception e) {
			System.out.println(" ==> Could not decode image: " + e.getMessage());
			e.printStackTrace(System.out);
			if (true) {
				FlateDecode fd = new FlateDecode(library, params, new ByteArrayInputStream(stream));
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
	
	private BufferedImage decodeOther(byte[] stream, Hashtable params, String filter, Library library, Resources resources) throws IOException {
		BufferedImage dRgbBi = this.decodeDeviceRGB(stream, null, params, library, resources);
		if (dRgbBi != null)
			return dRgbBi;
		
		SeekableInputConstrainedWrapper streamInputWrapper = new SeekableInputConstrainedWrapper(new SeekableByteArrayInputStream(stream), 0, stream.length, true);
		Stream str = new Stream(library, params, streamInputWrapper);
		try {
			Color biBackgroundColor = this.getBackgroundColor(params);
			BufferedImage bi = str.getImage(biBackgroundColor, resources, false);
			if (bi != null)
				System.out.println(" ==> got image, size is " + bi.getWidth() + " x " + bi.getHeight());
			else System.out.println(" ==> Could not decode image");
			return bi;
		}
		catch (Exception e) {
			System.out.println(" ==> Could not decode image: " + e.getMessage());
			e.printStackTrace(System.out);
			return null;
		}
	}
	
	private BufferedImage decodeDeviceRGB(byte[] stream, String filter, Hashtable params, Library library, Resources resources) throws IOException {
		
		//	test if we can handle this one
		Object csObj = params.get("ColorSpace");
		if ((csObj == null) || !"DeviceRGB".equals(csObj.toString()))
			return null;
		
		//	get image size
		Object widthObj = params.get("Width");
		int width;
		if (widthObj instanceof Number)
			width = ((Number) widthObj).intValue();
		else return null;
		Object heightObj = params.get("Height");
		int height;
		if (heightObj instanceof Number)
			height = ((Number) heightObj).intValue();
		else return null;
		
		//	get component depth
		Object bcpObj = params.get("BitsPerComponent");
		int bitsPerComponent;
		if (bcpObj instanceof Number)
			bitsPerComponent = ((Number) bcpObj).intValue();
		else return null;
		
		//	prepare decoding stream
		InputStream biIn;
		if ("FlateDecode".equals(filter))
			biIn = new BufferedInputStream(new FlateDecode(library, params, new ByteArrayInputStream(stream)));
		else biIn = new ByteArrayInputStream(stream);
		
		//	create bit masks
		int colorComponents = 3; // this is constant for RGB
		int componentBitMask = 0;
		for (int b = 0; b < bitsPerComponent; b++) {
			componentBitMask <<= 1;
			componentBitMask |= 1;
		}
		int bitsPerPixel = (colorComponents * bitsPerComponent);
		int pixelBitMask = 0;
		for (int b = 0; b < bitsPerPixel; b++) {
			pixelBitMask <<= 1;
			pixelBitMask |= 1;
		}
		
		//	fill image
		BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		for (int r = 0; r < height; r++) {
			int bitsRemaining = 0;
			int bitData = 0;
			for (int c = 0; c < width; c++) {
				
				//	make sure we have enough bits left in window
				while (bitsRemaining < bitsPerPixel) {
					int nextByte = biIn.read();
					bitData <<= 8;
					bitData |= nextByte;
					bitsRemaining += 8;
				}
				
				//	get component values for pixel
				int pixelBits = ((bitData >>> (bitsRemaining - bitsPerPixel)) & pixelBitMask);
				bitsRemaining -= bitsPerPixel;
				
				//	extract component values
				int rpr = ((pixelBits >>> (2 * bitsPerComponent)) & componentBitMask);
				int rpb = ((pixelBits >>> (1 * bitsPerComponent)) & componentBitMask);
				int rpg = ((pixelBits >>> (0 * bitsPerComponent)) & componentBitMask);
				
				//	normalize to 8 bits
				int pr = rpr;
				int pb = rpb;
				int pg = rpg;
				for (int b = bitsPerComponent; b < 8; b += bitsPerComponent) {
					pr <<= bitsPerComponent;
					pr += rpr;
					pb <<= bitsPerComponent;
					pb += rpb;
					pg <<= bitsPerComponent;
					pg += rpg;
				}
				
				//	set pixel
				bi.setRGB(c, r, ((pr << 16) | (pb << 8) | (pg << 0)));
			}
		}
		
		//	TODO consider modifying stream, including parameters, so IcePDF creates a nice page image, including the figure
		
		//	finally ...
		biIn.close();
		return bi;
	}
	
	private Color getBackgroundColor(Hashtable params) {
		/* if we have a mask image
		 * Decoding [0, 1] ==> 0 means paint-through (default), requires black background
		 * Decoding [1, 0] ==> 1 means paint-through, requires white background WRONG, black as well */
		Object imObj = params.get("ImageMask");
		if (imObj == null) {
			System.out.println(" - image mask is null");
			return Color.WHITE;
		}
		else {
			System.out.println(" - image mask is " + imObj.getClass().getName() + " - " + imObj);
			Object dObj = params.get("Decode");
			if ((dObj instanceof Vector) && (((Vector) dObj).size() != 0)) {
				System.out.println(" - decoder is " + dObj.getClass().getName() + " - " + dObj);
				Object ptObj = ((Vector) dObj).get(0);
				System.out.println(" - paint-through is " + ptObj.getClass().getName() + " - " + ptObj);
				if (ptObj instanceof Number) {
					if (((Number) ptObj).intValue() == 0)
						return Color.BLACK;
					else return Color.BLACK;
				}
				else return Color.BLACK;
			}
			else {
				if (dObj == null)
					System.out.println(" - decoder is null");
				else System.out.println(" - decoder is " + dObj.getClass().getName() + " - " + dObj);
				return Color.BLACK;
			}
		}
	}
	
	private boolean checkEmptyImage(BufferedImage bi) {
		if ((bi.getWidth() == 0) || (bi.getHeight() == 0))
			return true;
		int topLeftRgb = bi.getRGB(0, 0);
		for (int c = 0; c < bi.getWidth(); c++)
			for (int r = 0; r < bi.getHeight(); r++) {
				if (bi.getRGB(c, r) != topLeftRgb)
					return false;
			}
		return true;
	}
	
	private BoundingBox getBoundingBox(Rectangle2D bounds, Rectangle2D.Float pageBounds, float magnification, int rotate) {
		float fLeft = (((float) (bounds.getMinX() - pageBounds.getMinX())) * magnification);
		if ((rotate == 270) || (rotate == -90))
			fLeft += (pageBounds.width * magnification);
		float fRight = (((float) (bounds.getMaxX() - pageBounds.getMinX()))  * magnification);
		if ((rotate == 270) || (rotate == -90))
			fRight += (pageBounds.width * magnification);
		float fTop = ((pageBounds.height - ((float) (bounds.getMaxY() - ((2 * pageBounds.getMinY()) - pageBounds.getMaxY())))) * magnification);
		if ((rotate == 90) || (rotate == -270))
			fTop += (pageBounds.height * magnification);
		float fBottom = ((pageBounds.height - ((float) (bounds.getMinY() - ((2 * pageBounds.getMinY()) - pageBounds.getMaxY())))) * magnification);
		if ((rotate == 90) || (rotate == -270))
			fBottom += (pageBounds.height * magnification);
		int left = Math.round(fLeft);
		int right = (left + Math.round(fRight - fLeft));
		int top = Math.round(fTop);
		int bottom = (top + Math.round(fBottom - fTop));
		return new BoundingBox(left, right, top, bottom);
	}
	
	private BufferedImage extractFigureFromPageImage(Document pdfDoc, int p, Rectangle2D.Float pdfPageBounds, int rotate, int figureDpi, Rectangle2D pdfFigureBounds) {
		
		//	render page image at figure resolution
		float figureMagnification = (((float) figureDpi) / defaultDpi);
		BufferedImage figurePageImage;
		synchronized (pdfDoc) {
			pdfDoc.getPageTree().getPage(p, "").init(); // there might have been a previous call to reduceMemory() ...
			figurePageImage = ((BufferedImage) pdfDoc.getPageImage(p, GraphicsRenderingHints.SCREEN, Page.BOUNDARY_CROPBOX, 0, figureMagnification));
			pdfDoc.getPageTree().getPage(p, "").reduceMemory();
		}
		
		//	convert bounds, as PDF Y coordinate is bottom-up, whereas Java, JavaScript, etc. Y coordinate is top-down
		BoundingBox figureBox = this.getBoundingBox(pdfFigureBounds, pdfPageBounds, figureMagnification, rotate);
		
		//	make sure figure box is in bounds
		int left = Math.max(figureBox.left, 0);
		int width = ((Math.min(figureBox.right, figurePageImage.getWidth()) - figureBox.left) - Math.min(figureBox.left, 0));
		int top = Math.max(figureBox.top, 0);
		int height = ((Math.min(figureBox.bottom, figurePageImage.getHeight()) - figureBox.top) - Math.min(figureBox.top, 0));
		
		//	extract figure
		return figurePageImage.getSubimage(left, top, width, height);
	}
	
	private BufferedImage extractFigureFromPageImage(Document pdfDoc, int p, int figureDpi, BoundingBox figureBox) {
		
		//	render page image at figure resolution
		float figureMagnification = (((float) figureDpi) / defaultDpi);
		BufferedImage figurePageImage;
		synchronized (pdfDoc) {
			pdfDoc.getPageTree().getPage(p, "").init(); // there might have been a previous call to reduceMemory() ...
			figurePageImage = ((BufferedImage) pdfDoc.getPageImage(p, GraphicsRenderingHints.SCREEN, Page.BOUNDARY_CROPBOX, 0, figureMagnification));
			pdfDoc.getPageTree().getPage(p, "").reduceMemory();
		}
		
		//	extract figure
		return figurePageImage.getSubimage(figureBox.left, figureBox.top, (figureBox.right - figureBox.left), (figureBox.bottom - figureBox.top));
	}
	
	private void correctInvertedGaryscale(BufferedImage bi) {
		int rgb;
		float[] hsb = new float[3];
		
		//	check average brightness and saturation
		double bSum = 0;
		double sSum = 0;
		for (int x = 0; x < bi.getWidth(); x++)
			for (int y = 0; y < bi.getHeight(); y++) {
				rgb = bi.getRGB(x, y);
				hsb = Color.RGBtoHSB(((rgb >> 16) & 255), ((rgb >> 8) & 255), ((rgb >> 0) & 255), hsb);
				bSum += hsb[2];
				sSum += hsb[1];
			}
		
		//	this one's brighter than its inverse, keep it as is
		if (bSum / (bi.getWidth() * bi.getHeight()) > 0.5)
			return;
		
		//	this one looks like color, keep it as is
		if (sSum / (bi.getWidth() * bi.getHeight()) > 0.05)
			return;
		
		//	invert brightness
		for (int x = 0; x < bi.getWidth(); x++)
			for (int y = 0; y < bi.getHeight(); y++) {
				rgb = bi.getRGB(x, y);
				hsb = Color.RGBtoHSB(((rgb >> 16) & 255), ((rgb >> 8) & 255), ((rgb >> 0) & 255), hsb);
				int g = ((int) (255 * (1.0f - hsb[2])));
				rgb = ((255 << 24) | (g << 16) | (g << 8) | g);
				bi.setRGB(x, y, rgb);
			}
	}
	
	private void invertFigureBrightness(BufferedImage bi) {
		int rgb;
		float[] hsb = new float[3];
		
		//	check average brightness
		double bSum = 0;
		for (int x = 0; x < bi.getWidth(); x++)
			for (int y = 0; y < bi.getHeight(); y++) {
				rgb = bi.getRGB(x, y);
				hsb = Color.RGBtoHSB(((rgb >> 16) & 255), ((rgb >> 8) & 255), ((rgb >> 0) & 255), hsb);
				bSum += hsb[2];
			}
		
		//	this one's brighter than its inverse, keep it as is
		if (bSum / (bi.getWidth() * bi.getHeight()) > 0.5)
			return;
		
		//	invert brightness
		for (int x = 0; x < bi.getWidth(); x++)
			for (int y = 0; y < bi.getHeight(); y++) {
				rgb = bi.getRGB(x, y);
				hsb = Color.RGBtoHSB(((rgb >> 16) & 255), ((rgb >> 8) & 255), ((rgb >> 0) & 255), hsb);
				bi.setRGB(x, y, Color.HSBtoRGB(hsb[0], hsb[1], (1.0f - hsb[2])));
			}
	}
	
	private ImDocument doCreateDocument(String docId) {
		ImDocument doc = this.createDocument(docId);
		doc.setPageImageSource(this.imageStore);
		return doc;
	}
	
	/**
	 * Create an Image Markup document for some identifier. This method is used
	 * to create Image Markup documents in all PDF loading methods in case the
	 * argument Image Markup document is <code>null</code>. This default
	 * implementation simply returns an unmodified <code>ImDocument</code>,
	 * subclasses are welcome to overwrite it and add their specific behavior
	 * to the documents.
	 * @param docId the ID of the document
	 * @return the document
	 */
	protected ImDocument createDocument(String docId) {
		return new ImDocument(docId);
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