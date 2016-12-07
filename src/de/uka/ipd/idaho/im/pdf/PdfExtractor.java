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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.ComponentOrientation;
import java.awt.Composite;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
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
import java.util.TreeMap;
import java.util.Vector;

import javax.imageio.ImageIO;

import org.icepdf.core.exceptions.PDFException;
import org.icepdf.core.exceptions.PDFSecurityException;
import org.icepdf.core.io.BitStream;
import org.icepdf.core.io.SeekableByteArrayInputStream;
import org.icepdf.core.io.SeekableInputConstrainedWrapper;
import org.icepdf.core.pobjects.Catalog;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.PageTree;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.Resources;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.filters.ASCII85Decode;
import org.icepdf.core.pobjects.filters.ASCIIHexDecode;
import org.icepdf.core.pobjects.filters.FlateDecode;
import org.icepdf.core.pobjects.filters.LZWDecode;
import org.icepdf.core.pobjects.filters.RunLengthDecode;
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
import de.uka.ipd.idaho.gamta.util.CountingSet;
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
import de.uka.ipd.idaho.im.ImSupplement.Figure;
import de.uka.ipd.idaho.im.ImSupplement.Graphics;
import de.uka.ipd.idaho.im.ImSupplement.Graphics.Path;
import de.uka.ipd.idaho.im.ImSupplement.Graphics.SubPath;
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
import de.uka.ipd.idaho.im.pdf.PdfFontDecoder.FontDecoderCharset;
import de.uka.ipd.idaho.im.pdf.PdfParser.BlendComposite;
import de.uka.ipd.idaho.im.pdf.PdfParser.PFigure;
import de.uka.ipd.idaho.im.pdf.PdfParser.PObject;
import de.uka.ipd.idaho.im.pdf.PdfParser.PPageContent;
import de.uka.ipd.idaho.im.pdf.PdfParser.PPath;
import de.uka.ipd.idaho.im.pdf.PdfParser.PStream;
import de.uka.ipd.idaho.im.pdf.PdfParser.PSubPath;
import de.uka.ipd.idaho.im.pdf.PdfParser.PWord;
import de.uka.ipd.idaho.im.pdf.test.PdfExtractorTest;
import de.uka.ipd.idaho.im.util.ImFontUtils;
import de.uka.ipd.idaho.im.util.ImUtils;
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
	
	//	font preference switch, mainly for testing Liberation Fonts against default system fonts
	private static final boolean USE_FREE_FONTS = true;
	
	/* make sure we have the fonts we need */
	static {
		if (USE_FREE_FONTS)
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
		//	TODO use some configured charset here (introduce it, in the first place)
		PPageData[] pData = this.getPdfPageData(doc, (getWords | getFigures | getPaths), PdfFontDecoder.UNICODE, catalog.getPageTree(), objects, spm);
		
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
					if (liesMostlyIn(pData[p].words[w].bounds, pData[p].figures[f].bounds)) {
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
		else this.addImagePdfPages(doc, pData, pdfDoc, catalog.getPageTree(), objects, true, false, false, (wordInFigureCount > (pageCount * 25)), scaleFactor, true, spm);
		
		//	finally ...
		return doc;
	}
	
	private static class PPageData {
		final int p;
		final Page pdfPage;
		/** MediaBox, use for computations in scanned PDFs, as their converted page size is computed from the scan */
		final Rectangle2D.Float pdfPageBox;
		/** CropBox, use for computations in born-digital PDFs, as their converted page size is computed from the IcePDF page image size, which corresponds to the CropBox */
		final Rectangle2D.Float pdfPageContentBox;
		final int rotate;
		final Hashtable pdfPageResources;
		PWord[] words = null;
		PFigure[] figures = null;
		PPath[] paths = null;
		float rawPageImageDpi = -1;
		int rightPageOffset = 0;
		PPageData(int p, Page pdfPage, Rectangle2D.Float pdfPageBox, Rectangle2D.Float pdfPageContentBox, int rotate, Hashtable pdfPageResources, PWord[] words, PFigure[] figures, PPath[] paths) {
			this.p = p;
			this.pdfPage = pdfPage;
			this.pdfPageBox = pdfPageBox;
			this.pdfPageContentBox = pdfPageContentBox;
			this.rotate = rotate;
			this.words = words;
			this.figures = figures;
			this.paths = paths;
			this.pdfPageResources = pdfPageResources;
		}
	}
	
	private static class PPageImage {
		final BufferedImage image;
		final Rectangle2D.Float bounds;
		PPageImage(BufferedImage image, Rectangle2D.Float bounds) {
			this.image = image;
			this.bounds = bounds;
		}
		float getRawDpi(Rectangle2D.Float pageBounds) {
			float dpiRatioWidth = (((float) this.image.getWidth()) / this.bounds.width);
			float dpiRatioHeight = (((float) this.image.getHeight()) / this.bounds.height);
			if ((pageBounds != null) && ((dpiRatioWidth * defaultDpi) < 100) && ((dpiRatioHeight * defaultDpi) < 100)) {
				dpiRatioWidth = (((float) this.image.getWidth()) / pageBounds.width);
				dpiRatioHeight = (((float) this.image.getHeight()) / pageBounds.height);
			}
			float dpiRatio = ((dpiRatioWidth + dpiRatioHeight) / 2);
			float rawDpi = dpiRatio * defaultDpi;
			return rawDpi;
		}
	}
	
	private static final int getWords = 0x1;
	private static final int getFigures = 0x2;
	private static final int getPaths = 0x4;
	private PPageData[] getPdfPageData(final ImDocument doc, final int getWhat, final FontDecoderCharset fontCharSet, final PageTree pageTree, final Map objects, ProgressMonitor pm) throws IOException {
		
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
				if (PdfExtractorTest.aimAtPage != -1)
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
							Object contentObjId = cit.next();
							Object contentObj = PdfParser.dereference(contentObjId, objects);
							if (contentObj instanceof PStream) {
								Object filter = ((PStream) contentObj).params.get("Filter");
								if (filter == null)
									continue;
								spm.setInfo("   --> stream content part '" + contentObjId + "', filter is " + filter + " (from " + ((PStream) contentObj).params + ", " + ((PStream) contentObj).bytes.length + " bytes)");
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
		if (((getWhat & getWords) != 0) && (fontCharSet != PdfFontDecoder.NO_DECODING)) {
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
				
				//	update status display (might be inaccurate, but better than lock escalation)
				spm.setInfo("Importing page " + p + " of " + pdfPages.length);
				spm.setProgress((p * 100) / pdfPages.length);
				
				//	get page bounding box
//				Rectangle2D.Float pdfPageBox = pdfPages[p].getCropBox();
//				if (pdfPageBox == null)
//					pdfPageBox = pdfPages[p].getMediaBox();
				Rectangle2D.Float pdfPageBox = pdfPages[p].getMediaBox();
				if (pdfPageBox == null)
					pdfPageBox = pdfPages[p].getCropBox();
				Rectangle2D.Float pdfPageContentBox = pdfPages[p].getCropBox();
				if (pdfPageContentBox == null)
					pdfPageContentBox = pdfPageBox;
				
				//	nothing to work with, add dummy data and we're done
				if ((pdfPageContents[p] == null) || (pdfPageResources[p] == null)) {
					pData[p] = new PPageData(p, pdfPages[p], pdfPageBox, pdfPageContentBox, 0, pdfPageResources[p], new PWord[0], new PFigure[0], new PPath[0]);
					return;
				}
				
				//	check rotation
				Object rotateObj = PdfParser.dereference(pdfPages[p].getEntries().get("Rotate"), objects);
				int rotate = ((rotateObj instanceof Number) ? ((Number) rotateObj).intValue() : 0);
				spm.setInfo(" - page rotation is " + rotate);
				if ((rotate == 90) || (rotate == -90) || (rotate == 270) || (rotate == -270))
					pdfPageBox = new Rectangle2D.Float(0, 0, pdfPageBox.height, pdfPageBox.width);
				
				//	get words only
				if (getWhat == getWords) {
					PWord[] pWords = PdfParser.getPageWords(pdfPages[p].getEntries(), pdfPageContents[p], pdfPageResources[p], objects, tokenizer, spm);
					if (PdfExtractorTest.aimAtPage != -1) {
						System.out.println(" --> extracted " + pWords.length + " words");
						for (int w = 0; w < pWords.length; w++)
							System.out.println("   - " + pWords[w]);
					}
					spm.setInfo(" - got page content with " + pWords.length + " words");
					
					//	store page content
					pData[p] = new PPageData(p, pdfPages[p], pdfPageBox, pdfPageContentBox, rotate, pdfPageResources[p], pWords, new PFigure[0], new PPath[0]);
				}
				
				//	get words and other content
				else if ((getWhat & getWords) != 0) {
					PPageContent pContent = PdfParser.getPageContent(pdfPages[p].getEntries(), pdfPageContents[p], pdfPageResources[p], objects, tokenizer, fontCharSet, spm);
					if (PdfExtractorTest.aimAtPage != -1) {
						System.out.println(" --> extracted " + pContent.words.length + " words, " + pContent.figures.length + " figures, and " + pContent.paths.length + " vector graphics paths");
						for (int w = 0; w < pContent.words.length; w++)
							System.out.println("   - " + pContent.words[w]);
						for (int f = 0; f < pContent.figures.length; f++)
							System.out.println("   - " + pContent.figures[f]);
						for (int t = 0; t < pContent.paths.length; t++)
							System.out.println("   - " + pContent.paths[t]);
					}
					spm.setInfo(" - got page content with " + pContent.words.length + " words, " + pContent.figures.length + " figures, and " + pContent.paths.length + " vector graphics paths");
					
					//	store page content
					pData[p] = new PPageData(p, pdfPages[p], pdfPageBox, pdfPageContentBox, rotate, pdfPageResources[p], pContent.words, pContent.figures, pContent.paths);
				}
				
				//	get bitmap figures only
				else if (getWhat == getFigures) {
					PFigure[] pFigures = PdfParser.getPageFigures(pdfPages[p].getEntries(), pdfPageContents[p], pdfPageResources[p], objects, spm);
					if (PdfExtractorTest.aimAtPage != -1) {
						System.out.println(" --> extracted " + pFigures.length + " figures");
						for (int f = 0; f < pFigures.length; f++)
							System.out.println("   - " + pFigures[f]);
					}
					spm.setInfo(" - got page content with " + pFigures.length + " figures");
					
					//	store page content
					pData[p] = new PPageData(p, pdfPages[p], pdfPageBox, pdfPageContentBox, rotate, pdfPageResources[p], new PWord[0], pFigures, new PPath[0]);
				}
				
				//	get vector graphics paths only
				else if (getWhat == getPaths) {
					PPath[] pPaths = PdfParser.getPagePaths(pdfPages[p].getEntries(), pdfPageContents[p], pdfPageResources[p], objects, spm);
					if (PdfExtractorTest.aimAtPage != -1) {
						System.out.println(" --> extracted " + pPaths.length + " vector graphics paths");
						for (int t = 0; t < pPaths.length; t++)
							System.out.println("   - " + pPaths[t]);
					}
					spm.setInfo(" - got page content with " + pPaths.length + " vector graphics paths");
					
					//	store page content
					pData[p] = new PPageData(p, pdfPages[p], pdfPageBox, pdfPageContentBox, rotate, pdfPageResources[p], new PWord[0], new PFigure[0], pPaths);
				}
				
				//	get all visual elements, i.e., both bitmap figures and vector graphics paths
				else {
					PPageContent pContent = PdfParser.getPageGraphics(pdfPages[p].getEntries(), pdfPageContents[p], pdfPageResources[p], objects, spm);
					if (PdfExtractorTest.aimAtPage != -1) {
						System.out.println(" --> extracted " + pContent.figures.length + " figures and " + pContent.paths.length + " vector graphics paths");
						for (int f = 0; f < pContent.figures.length; f++)
							System.out.println("   - " + pContent.figures[f]);
						for (int t = 0; t < pContent.paths.length; t++)
							System.out.println("   - " + pContent.paths[t]);
					}
					spm.setInfo(" - got page content with " + pContent.figures.length + " figures and " + pContent.paths.length + " vector graphics paths");
					
					//	store page content
					pData[p] = new PPageData(p, pdfPages[p], pdfPageBox, pdfPageContentBox, rotate, pdfPageResources[p], pContent.words, pContent.figures, pContent.paths);
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
	
	private BufferedImage getFigureImage(ImDocument doc, PFigure pFigure, boolean isMainFigure, Document pdfDoc, Page pdfPage, int p, Rectangle2D.Float pdfPageBox, Map objects, float magnification, Map figureSupplements, ProgressMonitor spm) throws IOException {
		
		//	figure consists of sub figures
		if (pFigure.subFigures != null) {
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println("Rendering figure from sub images");
			
			//	render sub figures recursively, and compute average resolution
			//	TODO maybe weight resolution by extent in dimensions, to cushion rounding errors
			BufferedImage[] pFigureSubImages = new BufferedImage[pFigure.subFigures.length];
			float widthRatioSum = 0;
			float heightRatioSum = 0;
			for (int s = 0; s < pFigure.subFigures.length; s++) {
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println(" - rendering sub image " + pFigure.subFigures[s].name);
				pFigureSubImages[s] = this.getFigureImage(null, pFigure.subFigures[s], false, pdfDoc, pdfPage, p, pdfPageBox, objects, magnification, null, spm);
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println("   - sub image size is " + pFigureSubImages[s].getWidth() + "x" + pFigureSubImages[s].getHeight());
				float widthRatio = ((float) (((float) pFigureSubImages[s].getWidth()) / pFigure.subFigures[s].bounds.getWidth()));
				widthRatioSum += widthRatio;
				float heightRatio = ((float) (((float) pFigureSubImages[s].getHeight()) / pFigure.subFigures[s].bounds.getHeight()));
				heightRatioSum += heightRatio;
				float dpiRatio = ((widthRatio + heightRatio) / 2);
				float rawDpi = dpiRatio * defaultDpi;
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println("   - resolution is " + rawDpi + " DPI");
				BoundingBox dpiBox = this.getBoundingBox(pFigure.subFigures[s].bounds, pdfPageBox, dpiRatio, 0);
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println("   - bounding box at DPI is " + dpiBox);
			}
			float avgDpiRatio = ((widthRatioSum + heightRatioSum) / (2 * pFigure.subFigures.length));
			float avgRawDpi = avgDpiRatio * defaultDpi;
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" - average resolution is " + avgRawDpi + " DPI");
			BoundingBox avgDpiBox = this.getBoundingBox(pFigure.bounds, pdfPageBox, avgDpiRatio, 0);
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" - bounding box at DPI is " + avgDpiBox);
			
			//	assemble sub figures
			BufferedImage pFigureImage = new BufferedImage((avgDpiBox.right - avgDpiBox.left), (avgDpiBox.bottom - avgDpiBox.top), BufferedImage.TYPE_INT_ARGB);
			Graphics2D pFigureGraphics = pFigureImage.createGraphics();
			pFigureGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			for (int s = 0; s < pFigure.subFigures.length; s++) {
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println("   - sub image size is " + pFigureSubImages[s].getWidth() + "x" + pFigureSubImages[s].getHeight());
				BoundingBox dpiBox = this.getBoundingBox(pFigure.subFigures[s].bounds, pdfPageBox, avgDpiRatio, 0);
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println("   - bounding box at average DPI is " + dpiBox);
				pFigureGraphics.drawImage(pFigureSubImages[s], (dpiBox.left - avgDpiBox.left), (dpiBox.top - avgDpiBox.top), null);
				//	TODO try and stretch sub images to close gaps
			}
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" - image rendered, size is " + pFigureImage.getWidth() + "x" + pFigureImage.getHeight());
			
			//	compute figure resolution
			spm.setInfo(" - figure bounds are " + pFigure.bounds);
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
				ImSupplement.Figure figureSupplement = ImSupplement.Figure.createFigure(doc, p, pFigure.renderOrderNumber, pFigureDpi, pFigureImage, pFigureBox);
				if (figureSupplements != null)
					figureSupplements.put(pFigure, figureSupplement);
			}
			
			//	we're done here
			return pFigureImage;
		}
		
		//	get actual image object
		Object pFigureData = PdfParser.dereference(pFigure.refOrData, objects);
		spm.setInfo("     - figure data is " + pFigureData);
		if (!(pFigureData instanceof PStream)) {
			spm.setInfo("   --> strange figure data");
			return null;
		}
		
		//	decode PStream to image
		BufferedImage pFigureImage = this.decodeImage(pdfPage, ((PStream) pFigureData).params, ((PStream) pFigureData).bytes, objects, false);
//		boolean pFigureImageFromPageImage = false;
		if (pFigureImage == null) {
			spm.setInfo("   --> could not decode figure");
			return null;
		}
		spm.setInfo("     - got figure sized " + pFigureImage.getWidth() + " x " + pFigureImage.getHeight() + ", type is " + pFigureImage.getType());
		
		//	test for blank image only now, as we have all the data for the page image fallback
		if (this.checkEmptyImage(pFigureImage)) {
			pFigureImage = this.decodeImage(pdfPage, ((PStream) pFigureData).params, ((PStream) pFigureData).bytes, objects, true);
			spm.setInfo("     --> blank decoded figure extracted via IcePDF");
		}
		
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
		spm.setInfo("     - figure bounds are " + pFigure.bounds);
		float dpiRatio = ((float) ((((float) pFigureImage.getWidth()) / pFigure.bounds.getWidth()) + (((float) pFigureImage.getHeight()) / pFigure.bounds.getHeight())) / 2);
		float rawDpi = dpiRatio * defaultDpi;
		int pFigureDpi = (Math.round(rawDpi / 10) * 10);
		spm.setInfo("     - resolution computed as " + pFigureDpi + " DPI (" + rawDpi + ")");
		
		//	TODO if we're above 600 DPI, scale down half, third, etc., until at most 600 DPI
//		
//		//	test for blank image only now, as we have all the data for the page image fallback
//		if (this.checkEmptyImage(pFigureImage)) {
//			while (pFigureDpi > 600)
//				pFigureDpi /= 2;
//			pFigureImage = this.extractFigureFromPageImage(pdfDoc, p, pdfPageBox, rotate, pFigureDpi, pFigure.bounds);
//			pFigureImageFromPageImage = true;
//			spm.setInfo("     --> blank decoded figure re-rendered as part of page image");
//		}
//		
//		//	depending on color space, we might have to take the detour via the page image
//		Object csObj = PdfParser.getObject(((PStream) pFigureData).params, "ColorSpace", objects);
//		if (csObj != null) {
//			spm.setInfo("     - color space is " + csObj.toString());
//			
//			//	get filter to catch JPEG ('DCTDecode')
//			Object filterObj = PdfParser.getObject(((PStream) pFigureData).params, "Filter", objects);
//			if (filterObj instanceof Vector) {
//				if (((Vector) filterObj).size() != 0)
//					filterObj = ((Vector) filterObj).get(0);
//			}
//			if (filterObj instanceof Reference)
//				filterObj = objects.get(((Reference) filterObj).getObjectNumber() + " " + ((Reference) filterObj).getGenerationNumber());
//			spm.setInfo("     - filter is " + filterObj.toString());
//			
//			/* If we've used the fallback already, no use doing so again */
//			if (pFigureImageFromPageImage) {}
//			/* If color space is DeviceCMYK, render page image to figure
//			 * resolution and cut out image: images with color space
//			 * DeviceCMYK come out somewhat differently colored than in
//			 * Acrobat, but we cannot seem to do anything about this -
//			 * IcePDF distorts them as well, if more in single image
//			 * rendering than in the page images it generates. */
//			else if ("DeviceCMYK".equals(csObj.toString()) && ((filterObj == null) || !"DCTDecode".equals(filterObj.toString()))) {
//				pFigureImage = this.extractFigureFromPageImage(pdfDoc, p, pdfPageBox, rotate, pFigureDpi, pFigure.bounds);
//				pFigureImageFromPageImage = true;
//				spm.setInfo("     --> figure re-rendered as part of page image");
//			}
//			/* If color space is Separation, values represent color
//			 * intensity rather than brightness, which means 1.0 is
//			 * black and 0.0 is white. This means the have to invert
//			 * brightness to get the actual image. IcePDF gets this
//			 * wrong as well, with images coming out white on black
//			 * rather than the other way around, so we have to do the
//			 * correction ourselves. */
//			else if ((csObj instanceof Vector) && (((Vector) csObj).size() == 4) && ((Vector) csObj).get(0).equals("Separation")) {
//				this.invertFigureBrightness(pFigureImage);
//				pFigureImageFromPageImage = true;
//				spm.setInfo("     --> figure brightness inverted for additive 'Separation' color space");
//			}
//			/* If color space is Indexed, IcePDF seems to do a lot better
//			 * in page image rendering than in single-image rendering. */
//			else if ((csObj instanceof Vector) && (((Vector) csObj).size() == 4) && ((Vector) csObj).get(0).equals("Indexed")) {
//				
//				Object base = PdfParser.dereference(((Vector) csObj).get(1), objects);
//				System.out.println("Base color space is " + base);
//				if (base instanceof Vector)
//					for (int e = 0; e < ((Vector) base).size(); e++) {
//						System.out.println(" - " + ((Vector) base).get(e));
//						Object o = PdfParser.dereference(((Vector) base).get(e), objects);
//						System.out.println("   " + o);
//						if (o instanceof Hashtable)
//							for (Iterator okit = ((Hashtable) o).keySet().iterator(); okit.hasNext();) {
//								Object ok = okit.next();
//								System.out.println("   - " + ok);
//								Object ov = PdfParser.dereference(((Hashtable) o).get(ok), objects);
//								System.out.println("     " + ov);
//							}
//				}
//				Object hival = PdfParser.dereference(((Vector) csObj).get(2), objects);
//				System.out.println("High value is " + hival);
//				Object lookup = PdfParser.dereference(((Vector) csObj).get(3), objects);
//				System.out.println("Lookup is " + lookup);
//				if (lookup instanceof PStream) {
//					ByteArrayOutputStream baos = new ByteArrayOutputStream();
//					PdfParser.decode(((PStream) lookup).params.get("Filter"), ((PStream) lookup).bytes, ((PStream) lookup).params, baos, objects);
//					System.out.println(Arrays.toString(baos.toByteArray()));
//				}
////				
////				pFigureImage = this.extractFigureFromPageImage(pdfDoc, p, pdfPageBox, rotate, pFigureDpi, pFigure.bounds);
////				pFigureImageFromPageImage = true;
////				spm.setInfo("     --> figure re-rendered as part of page image");
//			}
//		}
		
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
			ImSupplement.Figure figureSupplement = ImSupplement.Figure.createFigure(doc, p, pFigure.renderOrderNumber, pFigureDpi, pFigureImage, pFigureBox);
			if (figureSupplements != null)
				figureSupplements.put(pFigure, figureSupplement);
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
		return this.loadTextPdf(null, null, pdfBytes, PdfFontDecoder.UNICODE, pm);
	}
	
	/**
	 * Load a document from a textual PDF, usually a digital-born PDF. Page
	 * images are stored in the page image store handed to the constructor. The
	 * document structure is stored in the argument image markup document,
	 * including textual contents with word bounding boxes.
	 * @param pdfBytes the raw binary data the the PDF document was parsed from
	 * @param fontCharSet the character set to use for font decoding
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the image markup document
	 * @throws IOException
	 */
	public ImDocument loadTextPdf(byte[] pdfBytes, FontDecoderCharset fontCharSet, ProgressMonitor pm) throws IOException {
		return this.loadTextPdf(null, null, pdfBytes, fontCharSet, pm);
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
		return this.loadTextPdf(doc, pdfDoc, pdfBytes, PdfFontDecoder.NO_DECODING, pm);
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
	 * @param fontCharSet the character set to use for font decoding
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the argument image markup document
	 * @throws IOException
	 */
	public ImDocument loadTextPdf(ImDocument doc, Document pdfDoc, byte[] pdfBytes, FontDecoderCharset fontCharSet, ProgressMonitor pm) throws IOException {
		
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
		PPageData[] pData = this.getPdfPageData(doc, (getWords | getFigures | getPaths), fontCharSet, catalog.getPageTree(), objects, spm);
//		
//		//	TODO use this for font tests
//		if (true)
//			throw new RuntimeException("ENOUGH FOR NOW");
		
		//	fill pages
		this.addTextPdfPages(doc, pData, pdfDoc, objects, spm);
		
		//	finally ...
		return doc;
	}
	
	private static final boolean DEBUG_EXTRACT_FIGURES = true;
	private static final boolean DEBUG_MERGE_WORDS = true;
	
	private static class DefaultingMap extends HashMap {
		public Object get(Object key) {
			Object val = super.get(key);
			return ((val == null) ? key : val);
		}
	}
	
	private void addTextPdfPages(final ImDocument doc, final PPageData[] pData, final Document pdfDoc, final Map objects, final SynchronizedProgressMonitor spm) throws IOException {
		final float magnification = (((float) this.textPdfPageImageDpi) / defaultDpi);
		
		//	get tokenizer to check and split words with
		final Tokenizer tokenizer = ((Tokenizer) doc.getAttribute(ImDocument.TOKENIZER_ATTRIBUTE, Gamta.INNER_PUNCTUATION_TOKENIZER));
		
		//	prepare working in parallel
		ParallelFor pf;
		
		//	extract page objects
		spm.setStep("Sanitizing page words and graphics");
		spm.setBaseProgress(30);
		spm.setProgress(0);
		spm.setMaxProgress(35);
		pf = new ParallelFor() {
			public void doFor(int p) throws Exception {
				
				//	nothing to work with
				if ((pData[p] == null) || (pData[p].words == null) || (pData[p].figures == null))
					return;
				
				//	update status display (might be inaccurate, but better than lock escalation)
				spm.setProgress((p * 100) / pData.length);
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println("Got page content with " + pData[p].words.length + " words, " + pData[p].figures.length + " figures, and " + pData[p].paths.length + " vector based paths");
				
				//	sanitize words (if any)
				if (pData[p].words.length != 0) {
					spm.setInfo("Sanitizing words in page " + p + " of " + pData.length);
					sanitizePageWords(pData[p], tokenizer, magnification, spm);
				}
				
				//	sanitize graphics (if any)
				if (pData[p].paths.length != 0) {
					spm.setInfo("Sanitizing graphics in page " + p + " of " + pData.length);
					sanitizePageGraphics(pData[p], magnification, spm);
				}
			}
		};
		ParallelJobRunner.runParallelFor(pf, ((PdfExtractorTest.aimAtPage < 0) ? 0 : PdfExtractorTest.aimAtPage), ((PdfExtractorTest.aimAtPage < 0) ? pData.length : (PdfExtractorTest.aimAtPage + 1)), (this.useMultipleCores ? -1 : 1));
		spm.setProgress(100);
		
		//	check errors
		this.checkException(pf);
		
		//	do page rotation assessment and content flipping right here
		spm.setStep("Handling flipped page content");
		spm.setBaseProgress(35);
		spm.setProgress(0);
		spm.setMaxProgress(40);
		final BoundingBox[] pFlipContentBox = new BoundingBox[pData.length];
		final int[] pFlipContentDirection = new int[pData.length];
		final DefaultingMap[] pFlippedObjects = new DefaultingMap[pData.length];
		pf = new ParallelFor() {
			public void doFor(int p) throws Exception {
				
				//	nothing to work with
				if ((pData[p] == null) || (pData[p].words == null) || (pData[p].figures == null))
					return;
				
				//	update status display (might be inaccurate, but better than lock escalation)
				spm.setProgress((p * 100) / pData.length);
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println("Got page content with " + pData[p].words.length + " words, " + pData[p].figures.length + " figures, and " + pData[p].paths.length + " vector based paths");
				
				//	test if we have non-horizontal blocks, and measure their size
				int bubLeft = Integer.MAX_VALUE;
				int bubRight = Integer.MIN_VALUE;
				int bubTop = Integer.MAX_VALUE;
				int bubBottom = Integer.MIN_VALUE;
				HashSet buWords = new HashSet();
				HashSet buFigures = new HashSet();
				HashSet buPaths = new HashSet();
				int tdbLeft = Integer.MAX_VALUE;
				int tdbRight = Integer.MIN_VALUE;
				int tdbTop = Integer.MAX_VALUE;
				int tdbBottom = Integer.MIN_VALUE;
				HashSet tdWords = new HashSet();
				HashSet tdFigures = new HashSet();
				HashSet tdPaths = new HashSet();
				
				//	assess page word orientation
				for (int w = 0; w < pData[p].words.length; w++) {
					
					//	convert bounds, as PDF Y coordinate is bottom-up, whereas Java, JavaScript, etc. Y coordinate is top-down
					BoundingBox wb = getBoundingBox(pData[p].words[w].bounds, pData[p].pdfPageContentBox, magnification, pData[p].rotate);
					
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
				
				//	assess page figure orientation
				for (int f = 0; f < pData[p].figures.length; f++) {
					
					//	convert bounds, as PDF Y coordinate is bottom-up, whereas Java, JavaScript, etc. Y coordinate is top-down
					BoundingBox fb = getBoundingBox(pData[p].figures[f].bounds, pData[p].pdfPageContentBox, magnification, pData[p].rotate);
					
					//	test for vertical figures
					if ((pData[p].figures[f].rotation < -(Math.PI / 4)) && (pData[p].figures[f].rotation > -((Math.PI * 3) / 4))) {
						bubLeft = Math.min(bubLeft, fb.left);
						bubRight = Math.max(bubRight, fb.right);
						bubTop = Math.min(bubTop, fb.top);
						bubBottom = Math.max(bubBottom, fb.bottom);
						buFigures.add(pData[p].figures[f]);
					}
					if ((pData[p].figures[f].rotation > (Math.PI / 4)) && (pData[p].figures[f].rotation < ((Math.PI * 3) / 4))) {
						tdbLeft = Math.min(tdbLeft, fb.left);
						tdbRight = Math.max(tdbRight, fb.right);
						tdbTop = Math.min(tdbTop, fb.top);
						tdbBottom = Math.max(tdbBottom, fb.bottom);
						tdFigures.add(pData[p].figures[f]);
					}
				}
				
				//	get paths inside to-flip area
				final float lineWidthLimit = ((defaultDpi * magnification) / 50); // half a millimeter
				final float lineDistanceLimit = ((defaultDpi * magnification) / 12); // some two millimeters
				for (int t = 0; t < pData[p].paths.length; t++) {
					
					//	convert bounds, as PDF Y coordinate is bottom-up, whereas Java, JavaScript, etc. Y coordinate is top-down
					BoundingBox pb = getBoundingBox(pData[p].paths[t].getBounds(), pData[p].pdfPageContentBox, magnification, pData[p].rotate);
					int pbCx = ((pb.left + pb.right) / 2);
					int pbCy = ((pb.top + pb.bottom) / 2);
					
					//	test for to-flip paths
					if ((bubLeft < pbCx) && (pbCx < bubRight) && (bubTop < pbCy) && (pbCy < bubBottom))
						buPaths.add(pData[p].paths[t]);
					else if ((tdbLeft < pbCx) && (pbCx < tdbRight) && (tdbTop < pbCy) && (pbCy < tdbBottom))
						tdPaths.add(pData[p].paths[t]);
					
					//	also get lines closing out to-flip tables at top or bottom
					else if ((bubLeft < pbCx) && (pbCx < bubRight) && (pb.getHeight() < lineWidthLimit) && (Math.min(Math.abs(bubTop - pb.bottom), Math.abs(pb.top - bubBottom)) < lineDistanceLimit))
						buPaths.add(pData[p].paths[t]);
					else if ((bubTop < pbCy) && (pbCy < bubBottom) && (pb.getWidth() < lineWidthLimit) && (Math.min(Math.abs(bubLeft - pb.right), Math.abs(pb.left - bubRight)) < lineDistanceLimit))
						buPaths.add(pData[p].paths[t]);
					else if ((tdbLeft < pbCx) && (pbCx < tdbRight) && (pb.getHeight() < lineWidthLimit) && (Math.min(Math.abs(tdbTop - pb.bottom), Math.abs(pb.top - tdbBottom)) < lineDistanceLimit))
						tdPaths.add(pData[p].paths[t]);
					else if ((tdbTop < pbCy) && (pbCy < tdbBottom) && (pb.getWidth() < lineWidthLimit) && (Math.min(Math.abs(tdbLeft - pb.right), Math.abs(pb.left - tdbRight)) < lineDistanceLimit))
						tdPaths.add(pData[p].paths[t]);
				}
				
				//	convert page bounds, as PDF Y coordinate is bottom-up, whereas Java, JavaScript, etc. Y coordinate is top-down
				BoundingBox pb = getBoundingBox(pData[p].pdfPageContentBox, pData[p].pdfPageContentBox, magnification, pData[p].rotate);
				
				//	we do have a significant bottom-up block, flip it
				if ((bubLeft < bubRight) && (bubTop < bubBottom) && (bubLeft < (pb.getWidth() / 3)) && (bubRight > ((pb.getWidth() * 2) / 3)) && (bubTop < (pb.getHeight() / 3)) && (bubBottom > ((pb.getHeight() * 2) / 3))) {
					spm.setInfo("Got bottom-up block with " + buWords.size() + " words to flip");
					pFlippedObjects[p] = new DefaultingMap();
					flipPageContent(pData[p], buWords, buFigures, buPaths, pFlippedObjects[p], PWord.BOTTOM_UP_FONT_DIRECTION);
					pFlipContentBox[p] = new BoundingBox(bubLeft, bubRight, bubTop, bubBottom);
					pFlipContentDirection[p] = PWord.BOTTOM_UP_FONT_DIRECTION;
				}
				
				//	we do have a significant top-down block, flip it
				else if ((tdbLeft < tdbRight) && (tdbTop < tdbBottom) && (tdbLeft < (pb.getWidth() / 3)) && (tdbRight > ((pb.getWidth() * 2) / 3)) && (tdbTop < (pb.getHeight() / 3)) && (tdbBottom > ((pb.getHeight() * 2) / 3))) {
					spm.setInfo("Got top-down block with " + tdWords.size() + " words to flip");
					pFlippedObjects[p] = new DefaultingMap();
					flipPageContent(pData[p], tdWords, tdFigures, tdPaths, pFlippedObjects[p], PWord.TOP_DOWN_FONT_DIRECTION);
					pFlipContentBox[p] = new BoundingBox(tdbLeft, tdbRight, tdbTop, tdbBottom);
					pFlipContentDirection[p] = PWord.TOP_DOWN_FONT_DIRECTION;
				}
			}
		};
		ParallelJobRunner.runParallelFor(pf, ((PdfExtractorTest.aimAtPage < 0) ? 0 : PdfExtractorTest.aimAtPage), ((PdfExtractorTest.aimAtPage < 0) ? pData.length : (PdfExtractorTest.aimAtPage + 1)), (this.useMultipleCores ? -1 : 1));
		spm.setProgress(100);
		
		//	check errors
		this.checkException(pf);
		
		//	extract page objects
		spm.setStep("Sanitizing page words");
		spm.setBaseProgress(40);
		spm.setProgress(0);
		spm.setMaxProgress(45);
		final Set[] pInFigureWords = new Set[pData.length];
		final Set[] pInGraphicsWords = new Set[pData.length];
		final Map[] pFigureSupplements = new Map[pData.length];
		pf = new ParallelFor() {
			public void doFor(int p) throws Exception {
				
				//	nothing to work with
				if ((pData[p] == null) || (pData[p].words == null) || (pData[p].figures == null))
					return;
				
				//	update status display (might be inaccurate, but better than lock escalation)
				spm.setProgress((p * 100) / pData.length);
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println("Got page content with " + pData[p].words.length + " words, " + pData[p].figures.length + " figures, and " + pData[p].paths.length + " vector based paths");
				
				//	preserve figures embedded in pages
				if (pData[p].figures.length != 0) {
					spm.setInfo("Storing figures in page " + p + " of " + pData.length);
					pFigureSupplements[p] = new HashMap();
					storeFiguresAsSupplements(doc, pData[p], pdfDoc, objects, magnification, pFigureSupplements[p], spm);
				}
				
				//	preserve vector based graphics embedded in pages
				if (pData[p].paths.length != 0) {
					spm.setInfo("Storing vector based graphics in page " + p + " of " + pData.length);
					storeGraphicsAsSupplements(doc, pData[p], pdfDoc, magnification, spm);
				}
				
				//	collect words that lie inside images
				if (pData[p].figures.length != 0) {
					spm.setInfo("Getting in-figure words in page " + p + " of " + pData.length);
					for (int f = 0; f < pData[p].figures.length; f++) {
						HashSet inFigureWords = null;
						
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
						spm.setInfo(" - getting words inside figure at " + pData[p].figures[f].bounds);
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
								
								//	mark word as lying in figure
								if (inFigureWords == null)
									inFigureWords = new HashSet();
								inFigureWords.add(pData[p].words[w]);
								spm.setInfo("   - found word at " + pData[p].words[w].bounds);
							}
						
						//	cut top and bottom of figure bounds (figure proper was stored above)
						pData[p].figures[f].bounds.setRect(pData[p].figures[f].bounds.getMinX(), wFigBottom, pData[p].figures[f].bounds.getWidth(), (wFigTop - wFigBottom));
						spm.setInfo(" - figure cropped to " + pData[p].figures[f].bounds);
						
						//	handle words inside current figure
						if (inFigureWords != null) {
							
							//	salvage words that are above wFigTop or below wFigBottom
							for (Iterator ifwit = inFigureWords.iterator(); ifwit.hasNext();) {
								PWord ifw = ((PWord) ifwit.next());
								double ifwCenterY = ((ifw.bounds.getMinY() + ifw.bounds.getMaxY()) / 2);
								if ((ifwCenterY < wFigTop) && (ifwCenterY > wFigBottom))
									spm.setInfo("   - marking word " + ifw.bounds + " as lying in figure");
								else {
									ifwit.remove();
									spm.setInfo("   - retaining word " + ifw.bounds + " as outside cropped figure");
								}
							}
							
							//	merge overlapping word sets for each figure only after salvaging inspection
							if (inFigureWords.size() != 0) {
								if (pInFigureWords[p] == null)
									pInFigureWords[p] = new HashSet();
								pInFigureWords[p].addAll(inFigureWords);
							}
						}
					}
					spm.setInfo("Found " + ((pInFigureWords[p] == null) ? "no" : ("" + pInFigureWords[p].size())) + " in-figure words in page " + p + " of " + pData.length);
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
			for (int p = 0; p < pData.length; p++) {
				if ((pData[p] != null) && (pData[p].words != null))
					docWordCount += pData[p].words.length;
			}
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
				if (PdfExtractorTest.aimAtPage != -1)
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
		spm.setBaseProgress(45);
		spm.setProgress(0);
		spm.setMaxProgress(46);
		final Tokenizer numberTokenizer = getNumberTokenizer(pData, tokenizer, spm);
		
		//	split words only now
		spm.setStep("Splitting page words");
		spm.setBaseProgress(46);
		spm.setProgress(0);
		spm.setMaxProgress(50);
		pf = new ParallelFor() {
			public void doFor(int p) throws Exception {
				
				//	nothing to work with
				if ((pData[p] == null) || (pData[p].words == null) || (pData[p].words.length == 0))
					return;
				
				//	update status display (might be inaccurate, but better than lock escalation)
				spm.setInfo("Splitting words in page " + p + " of " + pData.length);
				spm.setProgress((p * 100) / pData.length);
				
				//	split words that tokenize apart, splitting bounding box based on font measurement
				splitPageWords(pData[p], pFlippedObjects[p], pInFigureWords[p], pInGraphicsWords[p], tokenizer, numberTokenizer, spm);
				
				//	give status update
				if (pData[p].words.length == 0)
					spm.setInfo(" --> empty page");
				else spm.setInfo(" --> got " + pData[p].words.length + " words in PDF");
			}
		};
		ParallelJobRunner.runParallelFor(pf, ((PdfExtractorTest.aimAtPage < 0) ? 0 : PdfExtractorTest.aimAtPage), ((PdfExtractorTest.aimAtPage < 0) ? pData.length : (PdfExtractorTest.aimAtPage + 1)), (this.useMultipleCores ? -1 : 1));
		spm.setProgress(100);
		
		//	did anything go wrong?
		this.checkException(pf);
		
		//	put PDF words into images
		spm.setStep("Generating page images");
		spm.setBaseProgress(50);
		spm.setProgress(0);
		spm.setMaxProgress(70);
		final BufferedImage[] pageImages = new BufferedImage[pData.length];
		final String[] imageNames = new String[pData.length];
		final int[] imageDPIs = new int[pData.length];
		final BoundingBox[] pageBounds = new BoundingBox[pData.length];
		pf = new ParallelFor() {
			public void doFor(int p) throws Exception {
				
				//	nothing to work with
				if ((pData[p] == null) || (pData[p].words == null))
					return;
				
				//	update status display (might be inaccurate, but better than lock escalation)
				spm.setInfo("Extracting image of page " + p + " of " + pData.length);
				spm.setProgress((p * 100) / pData.length);
				
				//	page image generated before
				if (imageStore.isPageImageAvailable(doc.docId, p)) {
					PageImage pi = imageStore.getPageImage(doc.docId, p);
					if (pi.currentDpi == defaultTextPdfPageImageDpi) {
						pageImages[p] = pi.image;
						imageNames[p] = PageImage.getPageImageName(doc.docId, p);
						imageDPIs[p] = pi.currentDpi;
						pageBounds[p] = new BoundingBox(0, pageImages[p].getWidth(), 0, pageImages[p].getHeight());
						spm.setInfo(" --> loaded page image generated earlier, sized " + pageImages[p].getWidth() + " x " + pageImages[p].getHeight() + " at " + pi.currentDpi + " DPI");
					}
					else spm.setInfo(" --> could not use page image generated earlier, resolution mismatch at " + pi.currentDpi + " DPI");
				}
				
				//	generate page image
				if (pageImages[p] == null) {
					spm.setInfo(" - generating page image");
//					synchronized (pdfDoc) {
//						pdfDoc.getPageTree().getPage(p, "").init(); // there might have been a previous call to reduceMemory() ...
//						pageImages[p] = ((BufferedImage) pdfDoc.getPageImage(p, GraphicsRenderingHints.SCREEN, Page.BOUNDARY_CROPBOX, 0, magnification));
//					}
//					if (pageImages[p] == null) {
//						spm.setInfo(" --> page image generation failed");
//						throw new IOException("Could not generate image for page " + p);
//					}
//					spm.setInfo(" - got page image sized " + pageImages[p].getWidth() + " x " + pageImages[p].getHeight());
					
					//	compute page box
					BoundingBox pageBox = getBoundingBox(pData[p].pdfPageContentBox, pData[p].pdfPageContentBox, magnification, pData[p].rotate);
					
					//	factor in flipped content
					if ((pFlipContentBox[p] != null) && (pFlipContentDirection[p] == PWord.BOTTOM_UP_FONT_DIRECTION)) {
						spm.setInfo("Got bottom-up block " + pFlipContentBox[p] + " to flip");
						pageBox = getFlippedContentPageBounds(pageBox, pData[p].pdfPageContentBox, pFlipContentBox[p], pData[p], pFlippedObjects[p], PWord.BOTTOM_UP_FONT_DIRECTION, magnification);
					}
					else if ((pFlipContentBox[p] != null) && (pFlipContentDirection[p] == PWord.TOP_DOWN_FONT_DIRECTION)) {
						spm.setInfo("Got top-down block " + pFlipContentBox[p] + " to flip");
						pageBox = getFlippedContentPageBounds(pageBox, pData[p].pdfPageContentBox, pFlipContentBox[p], pData[p], pFlippedObjects[p], PWord.TOP_DOWN_FONT_DIRECTION, magnification);
					}
					
					//	conflate words, figures, and graphics
					ArrayList pageObjects = new ArrayList();
					pageObjects.addAll(Arrays.asList(pData[p].words));
					pageObjects.addAll(Arrays.asList(pData[p].figures));
					pageObjects.addAll(Arrays.asList(pData[p].paths));
					
					//	order by increasing render order number
					Collections.sort(pageObjects);
					
					//	render them all in a single loop
					pageImages[p] = new BufferedImage(pageBox.getWidth(), pageBox.getHeight(), BufferedImage.TYPE_INT_ARGB);
					Graphics2D piGr = pageImages[p].createGraphics();
					piGr.setColor(Color.WHITE);
					piGr.fillRect(0, 0, pageImages[p].getWidth(), pageImages[p].getHeight());
					piGr.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
					for (int o = 0; o < pageObjects.size(); o++) {
						PObject po = ((PObject) pageObjects.get(o));
						
						//	TODO use ImWord implementation here
						if (po instanceof PWord) {
							PWord pw = ((PWord) po);
							
							//	skip over water marks (all too faint words that do not lie in image or graphics)
							if ((pInFigureWords[p] != null) && pInFigureWords[p].contains(pw)) {}
							else if ((pInGraphicsWords[p] != null) && pInGraphicsWords[p].contains(pw)) {}
							else if (pw.color != null) {
								float pwBrightness = getBrightness(pw.color);
								if (PdfExtractorTest.aimAtPage != -1)
									System.out.println("Word " + pw + " has brightness " + pwBrightness + " from " + pw.color + "");
								if (pwBrightness > 0.67) {
									if (PdfExtractorTest.aimAtPage != -1)
										System.out.println(" ==> skipped as too faint");
									continue;
								}
							}
							
							//	convert bounds, as PDF Y coordinate is bottom-up, whereas Java, JavaScript, etc. Y coordinate is top-down
							BoundingBox wb = getBoundingBox(pw.bounds, pData[p].pdfPageContentBox, magnification, pData[p].rotate);
							
							//	prepare color, observe background luminosity if we're inside an image
							Color color = Color.BLACK;
							if ((pInFigureWords[p] != null) && (pInFigureWords[p].contains(pw))) {
								BufferedImage wi = pageImages[p].getSubimage(wb.left, wb.top, wb.getWidth(), wb.getHeight());
								AnalysisImage wai = Imaging.wrapImage(wi, null);
								byte[][] brightness = wai.getBrightness();
								int brightnessSum = 0;
								for (int c = 0; c < brightness.length; c++) {
									for (int r = 0; r < brightness[c].length; r++)
										brightnessSum += brightness[c][r];
								}
								int avgBrightness = (brightnessSum / (wb.getWidth() * wb.getHeight()));
								if (PdfExtractorTest.aimAtPage != -1)
									System.out.println(" - average backing figure brightness is " + avgBrightness);
								if (avgBrightness < 64)
									color = Color.WHITE;
							}
							piGr.setColor(color);
							
							//	prepare font
							int fontStyle = Font.PLAIN;
							if (pw.bold)
								fontStyle = (fontStyle | Font.BOLD);
							if (pw.italics)
								fontStyle = (fontStyle | Font.ITALIC);
							Font rf = getFont(pw.font.name, fontStyle, pw.serif, Math.round(((float) pw.fontSize) * magnification));
							piGr.setFont(rf);
							
							//	adjust word size and position
							AffineTransform preAt = piGr.getTransform();
							LineMetrics wlm = rf.getLineMetrics(pw.str, piGr.getFontRenderContext());
							TextLayout wtl = new TextLayout(pw.str, rf, piGr.getFontRenderContext());
							piGr.translate(wb.left, wb.bottom);
							float leftShift = ((float) -wtl.getBounds().getMinX());
							double hScale = 1;
							
							//	rotate word if required
							if (pw.fontDirection == PWord.LEFT_RIGHT_FONT_DIRECTION) {
								hScale = (((double) wb.getWidth()) / wtl.getBounds().getWidth());
								if (hScale < 1)
									piGr.scale(hScale, 1);
								else leftShift += ((wb.getWidth() - wtl.getBounds().getWidth()) / 2);
							}
							else if (pw.fontDirection == PWord.BOTTOM_UP_FONT_DIRECTION) {
								piGr.rotate((-Math.PI / 2), (((float) wb.getWidth()) / 2), -(((float) wb.getWidth()) / 2));
								hScale = (((double) wb.getHeight()) / wtl.getBounds().getWidth());
								if (hScale < 1)
									piGr.scale(1, hScale);
								else leftShift += ((wb.getHeight() - wtl.getBounds().getWidth()) / 2);
							}
							else if (pw.fontDirection == PWord.TOP_DOWN_FONT_DIRECTION) {
								piGr.rotate((Math.PI / 2), (((float) wb.getHeight()) / 2), -(((float) wb.getHeight()) / 2));
								hScale = (((double) wb.getHeight()) / wtl.getBounds().getWidth());
								if (hScale < 1)
									piGr.scale(1, hScale);
								else leftShift += ((wb.getHeight() - wtl.getBounds().getWidth()) / 2);
							}
							
							//	render word, finally ...
							if (PdfExtractorTest.aimAtPage != -1)
								System.out.println("Rendering " + pw.str + ((pw.str.length() == 1) ? (" " + Integer.toString(((int) pw.str.charAt(0)), 16)) : "") + ", hScale is " + hScale);
							try {
								piGr.drawGlyphVector(rf.createGlyphVector(new FontRenderContext(preAt, true, true), pw.str), leftShift, (pw.font.hasDescent ? -Math.round(wlm.getDescent()) : 0));
							} catch (InternalError ie) {}
							
							//	reset graphics
							piGr.setTransform(preAt);
						}
						
						//	render figure
						else if (po instanceof PFigure) {
							PFigure pf = ((PFigure) po);
							
							//	convert bounds, as PDF Y coordinate is bottom-up, whereas Java, JavaScript, etc. Y coordinate is top-down
							BoundingBox fb = getBoundingBox(pf.bounds, pData[p].pdfPageContentBox, magnification, pData[p].rotate);
							
							//	use white-neutral blend mode composite for mask images
							Composite maskComp = null;
							if ((pf.refOrData instanceof PStream) && ((PStream) pf.refOrData).params.containsKey("ImageMask"))
								maskComp = BlendComposite.getInstance("Multiply");
							
							//	load and render figure image
							ImSupplement.Figure figureSupplement = ((ImSupplement.Figure) pFigureSupplements[p].get(pf));
							if (figureSupplement != null) {
								BufferedImage fbi = ImageIO.read(figureSupplement.getInputStream());
								AffineTransform preAt = piGr.getTransform();
								Composite preComp = piGr.getComposite();
								piGr.translate(fb.left, fb.top);
								if (maskComp != null)
									piGr.setComposite(maskComp);
								piGr.drawImage(fbi, 0, 0, fb.getWidth(), fb.getHeight(), null);
								piGr.setComposite(preComp);
								piGr.setTransform(preAt);
							}
						}
						
						//	render path (safe for what we skipped above, i.e., out of page paths, bright on white paths, etc.)
						else if (po instanceof PPath) {
							PPath pp = ((PPath) po);
							AffineTransform preAt = piGr.getTransform();
							piGr.translate((-pData[p].pdfPageContentBox.x * magnification), ((-pData[p].pdfPageContentBox.y + pData[p].pdfPageContentBox.height + pData[p].pdfPageBox.height) * magnification));
							piGr.scale(magnification, -magnification);
							Composite preComp = piGr.getComposite();
							if (pp.blendMode != null)
								piGr.setComposite(pp.blendMode);
							Color preColor = piGr.getColor();
							Stroke preStroke = piGr.getStroke();
							Stroke stroke = null;
							if (pp.strokeColor != null) {
								float[] dashPattern = null;
								if (pp.dashPattern != null) {
									dashPattern = new float[pp.dashPattern.size()];
									boolean allZeroDashes = true;
									for (int e = 0; e < pp.dashPattern.size(); e++) {
										dashPattern[e] = ((Number) pp.dashPattern.get(e)).floatValue();
										allZeroDashes = (allZeroDashes && (dashPattern[e] == 0));
									}
									if (allZeroDashes)
										dashPattern = null;
								}
								stroke = new BasicStroke(pp.lineWidth, pp.lineCapStyle, pp.lineJointStyle, ((pp.miterLimit < 1) ? 1.0f : pp.miterLimit), dashPattern, pp.dashPatternPhase);
							}
							PSubPath[] psps = pp.getSubPaths();
							for (int sp = 0; sp < psps.length; sp++) {
								Path2D path = psps[sp].getPath();
								if (pp.fillColor != null) {
									piGr.setColor(pp.fillColor);
									piGr.fill(path);
								}
								if (pp.strokeColor != null) {
									piGr.setColor(pp.strokeColor);
									piGr.setStroke(stroke);
									piGr.draw(path);
								}
								else if (pp.fillColor != null) {
									piGr.setColor(pp.fillColor);
									piGr.draw(path);
								}
							}
							piGr.setStroke(preStroke);
							piGr.setColor(preColor);
							piGr.setComposite(preComp);
							piGr.setTransform(preAt);
						}
					}
					
					//	clean up
					piGr.dispose();
					spm.setInfo(" - got page image sized " + pageImages[p].getWidth() + " x " + pageImages[p].getHeight());
					
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
//					
//					//	DO NOT skip over in-figure words, TODOne_below SET TYPE TO 'label' INSTEAD
//					if ((pInFigureWords[p] != null) && pInFigureWords[p].contains(pData[p].words[w]))
//						continue;
//					
//					//	DO NOT skip over in-graphics words, TODOne_below SET TYPE TO 'label' INSTEAD
//					if ((pInGraphicsWords[p] != null) && pInGraphicsWords[p].contains(pData[p].words[w]))
//						continue;
					
					//	skip over water marks (all too faint words that do not lie in image or graphics)
					if ((pInFigureWords[p] != null) && pInFigureWords[p].contains(pData[p].words[w])) {}
					else if ((pInGraphicsWords[p] != null) && pInGraphicsWords[p].contains(pData[p].words[w])) {}
					else if (pData[p].words[w].color != null) {
						float pwBrightness = getBrightness(pData[p].words[w].color);
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println("Word " + pData[p].words[w] + " has brightness " + pwBrightness + " from " + pData[p].words[w].color + "");
						if (pwBrightness > 0.67) {
							if (PdfExtractorTest.aimAtPage != -1)
								System.out.println(" ==> skipped as too faint");
							continue;
						}
					}
					
					//	make sure word has some minimum width
					BoundingBox wb = getBoundingBox(pData[p].words[w].bounds, pData[p].pdfPageContentBox, magnification, pData[p].rotate);
					if (wb.getWidth() < 4)
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
					
					//	if source PDF word points to a continuation, set next word relation of word just added
					if (pData[p].words[w].joinWithNext)
						word.setNextRelation(ImWord.NEXT_RELATION_CONTINUE);
					
					//	store font direction with word TODO maybe also observe font direction in text analyses
					if (pData[p].words[w].fontDirection == PWord.BOTTOM_UP_FONT_DIRECTION)
						word.setAttribute(ImWord.TEXT_DIRECTION_ATTRIBUTE, ImWord.TEXT_DIRECTION_BOTTOM_UP);
					if (pData[p].words[w].fontDirection == PWord.TOP_DOWN_FONT_DIRECTION)
						word.setAttribute(ImWord.TEXT_DIRECTION_ATTRIBUTE, ImWord.TEXT_DIRECTION_TOP_DOWN);
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
		
		//	check if we need to dispose of any cover pages
		int coverPageCount = docLayout.getIntProperty("coverPageCount", 0);
		for (int p = 0; p < coverPageCount; p++) {
			doc.discardPage(p);
			pages[p] = null;
			pageImages[p] = null;
			imageNames[p] = null;
			pageBounds[p] = null;
			pData[p] = null;
		}
		
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
				if (pFlipContentBox[p] != null) {
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
				
				//	collect page figures and graphics
				Figure[] pageFigures;
				Graphics[] pageGraphics;
				synchronized (doc) { // need to synchronize accessing supplements
					ImSupplement[] pageSupplements = pages[p].getSupplements();
					ArrayList figureList = new ArrayList();
					ArrayList graphicsList = new ArrayList();
					for (int s = 0; s < pageSupplements.length; s++) {
						if (pageSupplements[s] instanceof Figure)
							figureList.add(pageSupplements[s]);
						else if (pageSupplements[s] instanceof Graphics)
							graphicsList.add(pageSupplements[s]);
					}
					pageFigures = ((Figure[]) figureList.toArray(new Figure[figureList.size()]));
					pageGraphics = ((Graphics[]) graphicsList.toArray(new Graphics[graphicsList.size()]));
				}
				
				//	also observe figures in page content
				for (int f = 0; f < pageFigures.length; f++) {
					BoundingBox fbb = pageFigures[f].getBounds();
					pContentLeft = Math.min(pContentLeft, fbb.left);
					pContentRight = Math.max(pContentRight, fbb.right);
					pContentTop = Math.min(pContentTop, fbb.top);
					pContentBottom = Math.max(pContentBottom, fbb.bottom);
				}
				
				//	also observe graphics in page content
				for (int g = 0; g < pageGraphics.length; g++) {
					BoundingBox gbb = pageGraphics[g].getBounds();
					pContentLeft = Math.min(pContentLeft, gbb.left);
					pContentRight = Math.max(pContentRight, gbb.right);
					pContentTop = Math.min(pContentTop, gbb.top);
					pContentBottom = Math.max(pContentBottom, gbb.bottom);
				}
				
				//	aggregate page content box
				BoundingBox pContentBox = new BoundingBox(pContentLeft, pContentRight, pContentTop, pContentBottom);
				
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
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println("Marking figure " + fbb);
					for (int c = Math.max(fbb.left, 0); c < Math.min(fbb.right, apiBrightness.length); c++) {
						for (int r = Math.max(fbb.top, 0); r < Math.min(fbb.bottom, apiBrightness[c].length); r++)
							apiBrightness[c][r] = ((byte) 127);
					}
				}
				
				//	mark words a solid black boxes
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println("Page image size is " + pageImage.getWidth() + "x" + pageImage.getHeight());
				for (Iterator wbbit = wordsByBoxes.keySet().iterator(); wbbit.hasNext();) {
					BoundingBox wbb = ((BoundingBox) wbbit.next());
					if (PdfExtractorTest.aimAtPage != -1)
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
				
				//	get minimum column width to prevent column splits resulting in too narrow columns
				int minColumnWidth = docLayout.getIntProperty("minColumnWidth", -1, imageDPIs[p]);
				
				//	obtain visual page structure
				Region pageRootRegion = PageImageAnalysis.getPageRegion(api, imageDPIs[p], minColumnMargin, minBlockMargin, columnAreas, minColumnWidth, false, spm);
				
				//	add page content to document
				Set pageStructImageRegions = new HashSet();
				addRegionStructure(pages[p], null, pageRootRegion, wordsByBoxes, pageStructImageRegions, spm);
				
				//	add regions created from figure and graphics supplements
				addFiguresAndGraphics(pages[p], pContentBox, pageImage, pageFigures, pFigureSupplements[p], pageGraphics);
				
				//	adjust bounding boxes
				shrinkToChildren(pages[p], LINE_ANNOTATION_TYPE, WORD_ANNOTATION_TYPE, -1);
				ImRegion[] blockRemainders = shrinkToChildren(pages[p], BLOCK_ANNOTATION_TYPE, LINE_ANNOTATION_TYPE, imageDPIs[p]);
				
				//	preserve image blocks that were attached to text blocks
				for (int r = 0; r < blockRemainders.length; r++) {
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println("Got block remainder at " + blockRemainders[r].bounds);
					ImagePartRectangle brBox = pageRootRegion.bounds.getSubRectangle(blockRemainders[r].bounds.left, blockRemainders[r].bounds.right, blockRemainders[r].bounds.top, blockRemainders[r].bounds.bottom);
					brBox = Imaging.narrowLeftAndRight(brBox);
					brBox = Imaging.narrowTopAndBottom(brBox);
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println(" - narrowed to " + brBox.getId());
					if ((imageDPIs[p] <= brBox.getWidth()) && (imageDPIs[p] <= brBox.getHeight())) {
						ImRegion blockRemainder = new ImRegion(pages[p], new BoundingBox(brBox.getLeftCol(), brBox.getRightCol(), brBox.getTopRow(), brBox.getBottomRow()), ImRegion.IMAGE_TYPE);
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println(" ==> added as image at " + blockRemainder.bounds);
					}
				}
				
				//	aggregate image and graphics regions
				handleImagesAndGraphics(pages[p], pageStructImageRegions);
				
				//	catch empty page
				if (pages[p].getWords().length == 0)
					return;
				
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
	
	private static void addFiguresAndGraphics(ImPage page, BoundingBox pageContentBox, BufferedImage pageImage, Figure[] pageFigures, Map pageFigureSupplements, Graphics[] pageGraphics) {
		if ((pageFigures.length + pageGraphics.length) == 0)
			return;
		
		//	get any images marked in page structure
		ImRegion[] pageStructImages = page.getRegions(ImRegion.IMAGE_TYPE);
		int[] pageStructImageCoverage = new int[pageStructImages.length];
		Arrays.fill(pageStructImageCoverage, 0);
		
		//	re-wrap page image, so brightness is re-computed
		AnalysisImage api = Imaging.wrapImage(pageImage, null);
		Imaging.whitenWhite(api);
		
		//	re-get page brightness
		byte[][] apiBrightness = api.getBrightness();
		
		//	whiten out words so they don't get in the way
		ImWord[] words = page.getWords();
		final int wordSafetyMargin = (page.getImageDPI() / 30);
		for (int w = 0; w < words.length; w++) {
			BoundingBox wbb = words[w].bounds;
			for (int c = Math.max(0, (wbb.left - wordSafetyMargin)); c < Math.min(apiBrightness.length, (wbb.right + wordSafetyMargin)); c++) {
				for (int r = Math.max(0, (wbb.top - wordSafetyMargin)); r < Math.min(apiBrightness[c].length, (wbb.bottom + wordSafetyMargin)); r++)
					apiBrightness[c][r] = ((byte) 127);
			}
		}
		
		//	eliminate everything outside page content bounds
		for (int c = 0; c < apiBrightness.length; c++)
			for (int r = 0; r < apiBrightness[c].length; r++) {
				if ((c < pageContentBox.left) || (c >= pageContentBox.right) || (r < pageContentBox.top) || (r >= pageContentBox.bottom))
					apiBrightness[c][r] = ((byte) 127);
			}
		
		//	collect raw bounds of all figures
		ArrayList pageFigureBounds = new ArrayList();
		for (int f = 0; f < pageFigures.length; f++)
			pageFigureBounds.add(new ClusterBoundingBox(pageFigures[f].getBounds(), false));
		
		//	merge overlapping figures before potentially shrinking their edges away
		for (boolean figuresMerged = true; figuresMerged;) {
			figuresMerged = false;
			Collections.sort(pageFigureBounds);
			for (int d = 0; d < pageFigureBounds.size(); d++) {
				ClusterBoundingBox pdb = ((ClusterBoundingBox) pageFigureBounds.get(d));
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println("Checking mergers for figure " + pdb);
				for (int cd = (d + 1); cd < pageFigureBounds.size(); cd++) {
					ClusterBoundingBox cpdb = ((ClusterBoundingBox) pageFigureBounds.get(cd));
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println(" - comparing to " + cpdb);
					if (pdb.getDistance(cpdb) > (page.getImageDPI() / 12)) {
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println("   ==> too far apart at " + pdb.getDistance(cpdb));
						continue;
					}
					if (pdb.isAdjacentTo(cpdb) || pdb.overlaps(cpdb) || pdb.liesIn(cpdb, true)) {
						pageFigureBounds.set(d, pdb.mergeWith(cpdb));
						pageFigureBounds.remove(cd);
						d = pageFigureBounds.size();
						cd = pageFigureBounds.size();
						figuresMerged = true;
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println("   ==> merged");
					}
					else if (PdfExtractorTest.aimAtPage != -1)
						System.out.println("   ==> distinct");
				}
			}
		}
		
		//	add figure regions
		for (int f = 0; f < pageFigureBounds.size(); f++) {
			BoundingBox fbb = ((BoundingBox) pageFigureBounds.get(f));
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println("Adding figure at " + fbb);
			fbb = shrinkToContent(fbb, apiBrightness);
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" - shrunk to " + fbb);
			
			//	no content at all
			if (fbb == null) {
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println(" ==> empty figure");
				continue;
			}
			
			//	expand image bounds to include any labeling words
			fbb = expandToWords(fbb, page);
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" - expanded to " + fbb);
			if (fbb == null) {
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println(" ==> empty figure");
				continue;
			}
			
			//	mark image region
			new ImRegion(page, fbb, ImRegion.IMAGE_TYPE);
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" ==> added to page");
			
			//	count overlap with images marked by page structuring
			for (int i = 0; i < pageStructImages.length; i++) {
				if (fbb.overlaps(pageStructImages[i].bounds))
					pageStructImageCoverage[i] += (
							(Math.min(fbb.right, pageStructImages[i].bounds.right) - Math.max(fbb.left, pageStructImages[i].bounds.left))
							*
							(Math.min(fbb.bottom, pageStructImages[i].bounds.bottom) - Math.max(fbb.top, pageStructImages[i].bounds.top))
						);
			}
		}
		
		//	add graphics regions
		for (int g = 0; g < pageGraphics.length; g++) {
			BoundingBox gbb = pageGraphics[g].getBounds();
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println("Adding graphics at " + gbb);
			gbb = shrinkToContent(gbb, apiBrightness);
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" - shrunk to " + gbb);
			
			//	no content at all
			if (gbb == null) {
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println(" ==> empty graphics");
				continue;
			}
			
			//	expand graphics bounds to include any labeling words
			gbb = expandToWords(gbb, page);
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" - expanded to " + gbb);
			if (gbb == null) {
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println(" ==> empty graphics");
				continue;
			}
			
			//	mark graphics region (unless it's text decoration like a table grid or info box frame, or image decoration like a passe-par-tout made up of overlaid lines)
			if (isTextDecoration(pageGraphics[g], page, pageFigures)) {
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println(" ==> text decoration");
			}
			else if (isFigureDecoration(pageGraphics[g], pageFigures)) {
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println(" ==> figure decoration");
			}
			else {
				new ImRegion(page, gbb, ImRegion.GRAPHICS_TYPE);
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println(" ==> added to page");
			}
			
			//	count overlap with images marked by page structuring
			for (int i = 0; i < pageStructImages.length; i++) {
				if (gbb.overlaps(pageStructImages[i].bounds))
					pageStructImageCoverage[i] += (
							(Math.min(gbb.right, pageStructImages[i].bounds.right) - Math.max(gbb.left, pageStructImages[i].bounds.left))
							*
							(Math.min(gbb.bottom, pageStructImages[i].bounds.bottom) - Math.max(gbb.top, pageStructImages[i].bounds.top))
						);
			}
		}
		
		//	remove image regions from structuring replaced by (presumably more accurate ones) from embedded figures
		for (int i = 0; i < pageStructImages.length; i++) {
			if ((pageStructImageCoverage[i] * 5) > (((pageStructImages[i].bounds.right - pageStructImages[i].bounds.left) * (pageStructImages[i].bounds.bottom - pageStructImages[i].bounds.top)) * 4))
				page.removeRegion(pageStructImages[i]);
		}
	}
	
	private static boolean isTextDecoration(Graphics graphics, ImPage page, Figure[] pageFigures) {
		
		//	too low or narrow to be a table grid or text box
		BoundingBox graphicsBounds = graphics.getBounds();
		if ((graphicsBounds.bottom - graphicsBounds.top) < (page.getImageDPI() / 8))
			return false;
		if ((graphicsBounds.right - graphicsBounds.left) < page.getImageDPI())
			return false;
		
		//	test which fraction of graphics overlaps with figures
		int graphicsPixelCount = graphicsBounds.getArea();
		int overlapFigureCount = 0;
		int figurePixelCount = 0;
		int figureOverlapPixelCount = 0;
		for (int f = 0; f < pageFigures.length; f++) {
			BoundingBox fb = pageFigures[f].getBounds();
			if (!graphicsBounds.overlaps(fb))
				continue;
			if (graphicsBounds.includes(fb, false))
				continue;
			overlapFigureCount++;
			figurePixelCount += fb.getArea();
			figureOverlapPixelCount += ((Math.min(fb.right, graphicsBounds.right) - Math.max(fb.left, graphicsBounds.left)) * (Math.min(fb.bottom, graphicsBounds.bottom) - Math.max(fb.top, graphicsBounds.top)));
		}
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println("Graphics at " + graphics.getBounds() + ":");
		float figureOverlapRatio = ((graphicsPixelCount == 0) ? 0 : (((float) figureOverlapPixelCount) / graphicsPixelCount));
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println(" - " + overlapFigureCount + " figures with a total of " + figurePixelCount + " pixels overlapping graphics without full inclusion, occupying " + figureOverlapPixelCount + " of " + graphicsPixelCount + " pixels (" + (figureOverlapRatio * 100) + "%)");
		
		//	we do not have a text decoration if at least 10% of graphics overlap with some figures that are not fully included
		if ((figureOverlapRatio * 10) > 1)
			return false;
		
		//	count ratio of filled and stroked sub paths
		int subPathCount = 0;
		int strokeSubPathCount = 0;
		int fillSubPathCount = 0;
		
		//	compute curve/line ratio (table grids should have very few curves, if any)
		int lineCount = 0;
		int curveCount = 0;
		
		//	compute average extent of lines (table grids should have pretty long lines)
		int hLineCount = 0;
		int vLineCount = 0;
		CountingSet relativeLineLengths = new CountingSet(new TreeMap());
		CountingSet graphicsRelativeLineLengths = new CountingSet(new TreeMap());
		CountingSet pageRelativeLineLengths = new CountingSet(new TreeMap());
		
		//	compute word density (tables should have way more words than actual graphics)
		int pathAreaSum = 0;
		int inPathWordCount = 0;
		int inPathWordAreaSum = 0;
		int subPathAreaSum = 0;
		int inSubPathWordCount = 0;
		int inSubPathWordAreaSum = 0;
		
		//	investigate individual paths (separate from rendering, as we can exclude a lot without latter, thus reducing computation effort)
		Path[] paths = graphics.getPaths();
		for (int p = 0; p < paths.length; p++) {
			
			//	get rendering parameters to help dodge non-painted paths
			Color strokeColor = paths[p].getStrokeColor();
			Color fillColor = paths[p].getFillColor();
			
			//	do not count lines and curves if they are neither filled nor stroked in anything but white (or some very light color)
			if ((strokeColor == null) && (fillColor == null))
				continue;
			
			//	compute brightness to catch very faint lines and areas
			float strokeBrightness = 1.0f;
			if (strokeColor != null)
				strokeBrightness = getBrightness(strokeColor);
			float fillBrightness = 1.0f;
			if (fillColor != null)
				fillBrightness = getBrightness(fillColor);
			
			//	not painted at all, or too faint to actually display anything without eye sore
			if ((strokeBrightness > 0.9) && (fillBrightness > 0.96))
				continue;
			
			//	investigate path words
			ImWord[] pathWords = page.getWordsInside(paths[p].bounds);
			pathAreaSum += ((paths[p].bounds.right - paths[p].bounds.left) * (paths[p].bounds.bottom - paths[p].bounds.top));
			inPathWordCount+= pathWords.length;
			for (int w = 0; w < pathWords.length; w++)
				inPathWordAreaSum += ((pathWords[w].bounds.right - pathWords[w].bounds.left) * (pathWords[w].bounds.bottom - pathWords[w].bounds.top));
			
			//	investigate individual sub paths
			Rectangle2D pathExtent = paths[p].getExtent();
			SubPath[] subPaths = paths[p].getSubPaths();
			subPathCount += subPaths.length;
			if (strokeColor != null)
				strokeSubPathCount += subPaths.length;
			if (fillColor != null)
				fillSubPathCount += subPaths.length;
			for (int s = 0; s < subPaths.length; s++) {
				
				//	investigate sub path words
				ImWord[] subPathWords = page.getWordsInside(subPaths[s].bounds);
				subPathAreaSum += ((subPaths[s].bounds.right - subPaths[s].bounds.left) * (subPaths[s].bounds.bottom - subPaths[s].bounds.top));
				inSubPathWordCount+= subPathWords.length;
				for (int w = 0; w < subPathWords.length; w++)
					inSubPathWordAreaSum += ((subPathWords[w].bounds.right - subPathWords[w].bounds.left) * (subPathWords[w].bounds.bottom - subPathWords[w].bounds.top));
				
				//	investigate individual segments (lines and curves)
				Shape[] shapes = subPaths[s].getShapes();
				for (int h = 0; h < shapes.length; h++) {
					if (shapes[h] instanceof Line2D) {
						lineCount++;
						Line2D line = ((Line2D) shapes[h]);
						double hExtent = Math.abs(line.getX1() - line.getX2());
						double vExtent = Math.abs(line.getY1() - line.getY2());
						if (((hExtent * 100) < vExtent) && (pathExtent.getHeight() > 1)) {
							vLineCount++;
							relativeLineLengths.add(new Integer((int) Math.round((vExtent * 100) / pathExtent.getHeight())));
							graphicsRelativeLineLengths.add(new Integer((int) Math.round((vExtent * 100 * page.getImageDPI()) / ((graphicsBounds.bottom - graphicsBounds.top) * graphics.getDpi()))));
							pageRelativeLineLengths.add(new Integer((int) Math.round((vExtent * 100 * page.getImageDPI()) / ((page.bounds.bottom - page.bounds.top) * graphics.getDpi()))));
						}
						else if (((vExtent * 100) < hExtent) && (pathExtent.getWidth() > 1)) {
							hLineCount++;
							relativeLineLengths.add(new Integer((int) Math.round((hExtent * 100) / pathExtent.getWidth())));
							graphicsRelativeLineLengths.add(new Integer((int) Math.round((hExtent * 100 * page.getImageDPI()) / ((graphicsBounds.right - graphicsBounds.left) * graphics.getDpi()))));
							pageRelativeLineLengths.add(new Integer((int) Math.round((hExtent * 100 * page.getImageDPI()) / ((page.bounds.right - page.bounds.left) * graphics.getDpi()))));
						}
					}
					else if (shapes[h] instanceof CubicCurve2D)
						curveCount++;
				}
			}
		}
		
		//	what do we have thus far?
		if (PdfExtractorTest.aimAtPage != -1) {
			System.out.println(" - " + paths.length + " paths with " + subPathCount + " sub paths, " + strokeSubPathCount + " stroked and " + fillSubPathCount + " filled");
			System.out.println(" - " + lineCount + " lines and " + curveCount + " curves");
			System.out.println(" - " + hLineCount + " horizontal lines and " + vLineCount + " vertical ones");
			System.out.println(" - relative lenght distribution:");
			for (Iterator rllit = relativeLineLengths.iterator(); rllit.hasNext();) {
				Integer rll = ((Integer) rllit.next());
				System.out.println("   - " + rll + " (" + relativeLineLengths.getCount(rll) + " times)");
			}
			System.out.println(" - graphics relative lenght distribution:");
			for (Iterator rllit = graphicsRelativeLineLengths.iterator(); rllit.hasNext();) {
				Integer rll = ((Integer) rllit.next());
				System.out.println("   - " + rll + " (" + graphicsRelativeLineLengths.getCount(rll) + " times)");
			}
			System.out.println(" - page relative lenght distribution:");
			for (Iterator rllit = pageRelativeLineLengths.iterator(); rllit.hasNext();) {
				Integer rll = ((Integer) rllit.next());
				System.out.println("   - " + rll + " (" + pageRelativeLineLengths.getCount(rll) + " times)");
			}
		}
		float inPathWordRatio = ((pathAreaSum == 0) ? 0 : (((float) inPathWordAreaSum) / pathAreaSum));
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println(" - " + inPathWordCount + " words inside paths, occupying " + inPathWordAreaSum + " of " + pathAreaSum + " pixels (" + (inPathWordRatio * 100) + "%)");
		float inSubPathWordRatio = ((subPathAreaSum == 0) ? 0 : (((float) inSubPathWordAreaSum) / subPathAreaSum));
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println(" - " + inSubPathWordCount + " words inside sub paths, occupying " + inSubPathWordAreaSum + " of " + subPathAreaSum + " pixels (" + (inSubPathWordRatio * 100) + "%)");
		
		//	more than 12 curves ==> not a grid or box (rounded corners rarely consist of more than 3 segments each ...)
		if (curveCount > 12)
			return false;
		
		//	fewer than 4 lines ==> not a table grid or text box
		if (lineCount < 4)
			return false;
		
		//	no axis oriented lines of any significant length at all ==> not a table grid or text box
		if (graphicsRelativeLineLengths.isEmpty())
			return false;
		
		//	prepare rendering paths TODOne use BYTE_GRAY image to reduce memory consumption
		Rectangle2D extent = graphics.getExtent();
		float scale = (((float) page.getImageDPI()) / graphics.getDpi());
		BufferedImage rendering = new BufferedImage((((int) Math.ceil(extent.getWidth() * scale)) + 10), (((int) Math.ceil(extent.getHeight() * scale)) + 10), BufferedImage.TYPE_BYTE_GRAY);
		Graphics2D renderer = rendering.createGraphics();
		renderer.setColor(Color.WHITE);
		renderer.fillRect(0, 0, rendering.getWidth(), rendering.getHeight());
		renderer.translate(5, 5);
		renderer.scale(scale, scale);
		renderer.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		
		//	render individual paths
		for (int p = 0; p < paths.length; p++) {
			
			//	prepare rendering path
			Color preColor = renderer.getColor();
			Stroke preStroke = renderer.getStroke();
			
			Color strokeColor = paths[p].getStrokeColor();
			Stroke stroke = paths[p].getStroke();
			Color fillColor = paths[p].getFillColor();
			
			//	do not count lines and curves if they are neither filled nor stroked in anything but white (or some very light color)
			if ((strokeColor == null) && (fillColor == null))
				continue;
			
			//	investigate individual sub paths
			SubPath[] subPaths = paths[p].getSubPaths();
			for (int s = 0; s < subPaths.length; s++) {
				
				//	render sub path
				Path2D path = subPaths[s].getPath();
				if (fillColor != null) {
					renderer.setColor(fillColor);
					renderer.fill(path);
				}
				if (strokeColor != null) {
					renderer.setColor(strokeColor);
					renderer.setStroke(stroke);
					renderer.draw(path);
				}
				else if (fillColor != null) {
					renderer.setColor(fillColor);
					renderer.draw(path);
				}
			}
			
			//	reset rendering facilities
			renderer.setColor(preColor);
			renderer.setStroke(preStroke);
		}
		
		//	wrap rendering and get brightness
		AnalysisImage aRendering = Imaging.wrapImage(rendering, null);
		byte[][] renderingBrightness = aRendering.getBrightness();
		
		//	flood fill outside area with marker
		Imaging.floodFill(renderingBrightness, 0, 0, ((byte) 127), ((byte) -1));
//		for (int c = 0; c < renderingBrightness.length; c++)
//			for (int r = 0; r < renderingBrightness[c].length; r++) {
//				if (renderingBrightness[c][r] == -1)
//					rendering.setRGB(c, r, 0xFFFF00);
//			}
//		ImageDisplayDialog idd = new ImageDisplayDialog("Flood Filling Result");
//		idd.setSize(800, 600);
//		idd.setLocationRelativeTo(null);
//		idd.addImage(rendering, "");
//		idd.setVisible(true);
		
		//	count words whose center lies on pixels left un-flooded
		ImWord[] graphicsWords = page.getWordsInside(graphicsBounds);
		int wordPixelCount = 0;
		int enclosedWordPixelCount = 0;
		for (int w = 0; w < graphicsWords.length; w++) {
			for (int c = Math.max(0, (graphicsWords[w].bounds.left + 2 - graphicsBounds.left)); c < Math.min(renderingBrightness.length, (graphicsWords[w].bounds.right + 2 - graphicsBounds.left)); c++)
				for (int r = Math.max(0, (graphicsWords[w].bounds.top + 2 - graphicsBounds.top)); r < Math.min(renderingBrightness[c].length, (graphicsWords[w].bounds.bottom + 2 - graphicsBounds.top)); r++) {
					wordPixelCount++;
					if (renderingBrightness[c][r] != -1)
						enclosedWordPixelCount++;
				}
		}
		
		//	what do we have now?
		float enclosedWordRatio = ((wordPixelCount == 0) ? 0 : (((float) enclosedWordPixelCount) / wordPixelCount));
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println(" - " + graphicsWords.length + " words inside graphics, with " + enclosedWordPixelCount + " of " + wordPixelCount + " pixels enclosed (" + (enclosedWordRatio * 100) + "%)");
		float wordRatio = ((graphicsPixelCount == 0) ? 0 : (((float) wordPixelCount) / graphicsPixelCount));
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println(" - " + graphicsWords.length + " words inside graphics, occupying " + wordPixelCount + " of " + graphicsPixelCount + " pixels (" + (wordRatio * 100) + "%)");
		
		//	less than 10% of graphics area covered by words ==> not a table grid or text box
		if ((wordRatio * 10) < 1)
			return false;
		
		//	less than 25% of all words in graphics area enclosed ==> not a table grid or text box
		if ((enclosedWordRatio * 4) < 1)
			return false;
		
		//	more than 12 curves ==> not a grid or box (rounded corners rarely consist of more than 3 segments each ...)
		if (curveCount > 12)
			return false; // already checked above, just repeated here for tests
		
		//	fewer than 4 lines ==> not a table grid or text box
		if (lineCount < 4)
			return false; // already checked above, just repeated here for tests
		
		//	no axis oriented lines of any significant length at all ==> not a table grid or text box
		if (graphicsRelativeLineLengths.isEmpty())
			return false;
		
		//	no counter indications found for this one
		return true;
	}
	
	private static boolean isFigureDecoration(Graphics graphics, Figure[] figures) {
		
		//	no figures at all
		if (figures.length == 0)
			return false;
		
		//	compute overlap with figures
		BoundingBox gBounds = graphics.getBounds();
		int graphicsPixelCount = gBounds.getArea();
		int figurePixelCount = 0;
		int overlapPixelCount = 0;
		for (int f = 0; f < figures.length; f++) {
			BoundingBox fBounds = figures[f].getBounds();
			if (!gBounds.overlaps(fBounds))
				continue;
			figurePixelCount += fBounds.getArea();
			int oLeft = Math.max(gBounds.left, fBounds.left);
			int oRight = Math.min(gBounds.right, fBounds.right);
			int oTop = Math.max(gBounds.top, fBounds.top);
			int oBottom = Math.min(gBounds.bottom, fBounds.bottom);
			overlapPixelCount += ((oRight - oLeft) * (oBottom - oTop));
		}
		
		//	require 95% overlap in either direction
		if (PdfExtractorTest.aimAtPage != -1) {
			System.out.println("Figure/graphics coverage is " + (((float) overlapPixelCount) / graphicsPixelCount));
			System.out.println("Graphics/figure coverage is " + (((float) overlapPixelCount) / figurePixelCount));
		}
		return ((graphicsPixelCount * 19) < (overlapPixelCount * 20));
	}
	
	private static void handleImagesAndGraphics(ImPage page, Set pageStructImageRegions) {
		int pageImageDpi = page.getImageDPI();
		
		//	analyze image and graphics regions for clusters (multi-part plates, etc.)
		ImRegion[] pageImageRegs = page.getRegions(ImRegion.IMAGE_TYPE);
		ImRegion[] pageGraphicsRegs = page.getRegions(ImRegion.GRAPHICS_TYPE);
		
		//	find bundles of image and graphics regions (multi-part plates, etc.)
		ArrayList pageDrawingBounds = new ArrayList();
		for (int i = 0; i < pageImageRegs.length; i++)
			pageDrawingBounds.add(new ClusterBoundingBox(pageImageRegs[i].bounds, false));
		for (int g = 0; g < pageGraphicsRegs.length; g++)
			pageDrawingBounds.add(new ClusterBoundingBox(pageGraphicsRegs[g].bounds, false));
		for (boolean drawingsMerged = true; drawingsMerged;) {
			drawingsMerged = false;
			Collections.sort(pageDrawingBounds);
			for (int d = 0; d < pageDrawingBounds.size(); d++) {
				ClusterBoundingBox pdb = ((ClusterBoundingBox) pageDrawingBounds.get(d));
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println("Checking mergers for figure/graphics block " + pdb);
				for (int cd = (d + 1); cd < pageDrawingBounds.size(); cd++) {
					ClusterBoundingBox cpdb = ((ClusterBoundingBox) pageDrawingBounds.get(cd));
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println(" - comparing to " + cpdb);
					if (pdb.getDistance(cpdb) > (pageImageDpi / 12)) {
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println("   ==> too far apart at " + pdb.getDistance(cpdb));
						continue;
					}
					if (pdb.isAdjacentTo(cpdb) || pdb.overlaps(cpdb) || pdb.liesIn(cpdb, true)) {
						pageDrawingBounds.set(d, pdb.mergeWith(cpdb));
						pageDrawingBounds.remove(cd);
						d = pageDrawingBounds.size();
						cd = pageDrawingBounds.size();
						drawingsMerged = true;
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println("   ==> merged");
					}
					else if (PdfExtractorTest.aimAtPage != -1)
						System.out.println("   ==> distinct");
				}
			}
		}
		
		//	merge text blocks into images and graphics (label text might protrude beyond drawing bounds)
		ImRegion[] pageBlocks = page.getRegions(ImRegion.BLOCK_ANNOTATION_TYPE);
		for (int b = 0; b < pageBlocks.length; b++)
			pageDrawingBounds.add(new ClusterBoundingBox(pageBlocks[b].bounds, true));
		for (boolean blocksMerged = true; blocksMerged;) {
			blocksMerged = false;
			Collections.sort(pageDrawingBounds);
			for (int d = 0; d < pageDrawingBounds.size(); d++) {
				ClusterBoundingBox pdb = ((ClusterBoundingBox) pageDrawingBounds.get(d));
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println("Checking mergers for block " + pdb);
				if (pdb.isBlock) {
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println(" ==> not merging text block");
					continue;
				}
				for (int cd = (d + 1); cd < pageDrawingBounds.size(); cd++) {
					ClusterBoundingBox cpdb = ((ClusterBoundingBox) pageDrawingBounds.get(cd));
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println(" - comparing to " + cpdb);
					if (pdb.getDistance(cpdb) > (pageImageDpi / 10)) {
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println("   ==> too far apart at " + pdb.getDistance(cpdb));
						continue;
					}
					if (cpdb.isBlock && (((pdb.getWidth() * 3) < cpdb.getWidth()) || ((pdb.getHeight() * 3) < cpdb.getHeight()))) {
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println("   ==> too small either way to merge with text block");
						continue; // this catches cases where center of block happens to lie inside table text decoration
					}
					if (cpdb.isBlock && ((pdb.getWidth() * 2) < cpdb.getWidth()) && ((pdb.getHeight() * 2) < cpdb.getHeight())) {
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println("   ==> too small both ways to merge with text block");
						continue; // this catches cases where center of block happens to lie inside table text decoration
					}
					if (cpdb.isBlock ? pdb.includes(cpdb, true) : pdb.liesIn(cpdb, true)) {
						pageDrawingBounds.set(d, pdb.mergeWith(cpdb));
						pageDrawingBounds.remove(cd);
						d = pageDrawingBounds.size();
						cd = pageDrawingBounds.size();
						blocksMerged = true;
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println("   ==> merged");
					}
					else if (PdfExtractorTest.aimAtPage != -1)
						System.out.println("   ==> distinct (" + (cpdb.isBlock ? "not fully containing" : "not fully contained") + ")");
				}
			}
		}
		
		//	refresh image and graphics regions
		pageImageRegs = page.getRegions(ImRegion.IMAGE_TYPE);
		pageGraphicsRegs = page.getRegions(ImRegion.GRAPHICS_TYPE);
		
		//	merge image and graphics regions lying inside clusters
		for (int ic = 0; ic < pageDrawingBounds.size(); ic++) {
			ClusterBoundingBox pdb = ((ClusterBoundingBox) pageDrawingBounds.get(ic));
			if (pdb.mergeCount == 1)
				continue;
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println("Marking merged block " + pdb);
			
			//	clean up parts, and count out aggregate region type
			int imageArea = 0;
			for (int i = 0; i < pageImageRegs.length; i++) {
				if (pageImageRegs[i] == null)
					continue;
				if (pageStructImageRegions.contains(pageImageRegs[i]))
					continue;
				if (pdb.includes(pageImageRegs[i].bounds, false)) {
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println(" - included image region: " + pageImageRegs[i].bounds);
					imageArea += pageImageRegs[i].bounds.getArea();
					page.removeRegion(pageImageRegs[i]);
					pageImageRegs[i] = null;
				}
				else if (PdfExtractorTest.aimAtPage != -1)
					System.out.println(" - non-included image region: " + pageImageRegs[i].bounds);
			}
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" - image area is " + imageArea);
			int graphicsArea = 0;
			for (int g = 0; g < pageGraphicsRegs.length; g++) {
				if (pageGraphicsRegs[g] == null)
					continue;
				if (pdb.includes(pageGraphicsRegs[g].bounds, false)) {
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println(" - included graphics region: " + pageGraphicsRegs[g].bounds);
					graphicsArea += pageGraphicsRegs[g].bounds.getArea();
					page.removeRegion(pageGraphicsRegs[g]);
					pageGraphicsRegs[g] = null;
				}
				else if (PdfExtractorTest.aimAtPage != -1)
					System.out.println(" - non-included graphics region: " + pageGraphicsRegs[g].bounds);
			}
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" - graphics area is " + graphicsArea);
			
			//	determine aggregate region type
			String pdrType;
			
			//	area mostly (>= 80%) covered by figures, any graphics are likely decoration
			if ((imageArea * 5) >= (pdb.getArea() * 4))
				pdrType = ImRegion.IMAGE_TYPE;
			//	area contains more figures that graphics
			else if (imageArea > graphicsArea)
				pdrType = ImRegion.IMAGE_TYPE;
			//	less figures than graphics
			else pdrType = ImRegion.GRAPHICS_TYPE;
			
			//	mark image region
			new ImRegion(page, pdb, pdrType);
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" ==> region type is " + pdrType);
		}
		
		//	remove all blocks, paragraphs, etc. lying inside images or graphics
		ImRegion[] pageRegs = null;
		
		//	clean up images
		pageRegs = page.getRegions();
		pageImageRegs = page.getRegions(ImRegion.IMAGE_TYPE);
		for (int i = 0; i < pageImageRegs.length; i++) {
			
			//	skip over image regions originating from page structure detection (they do not contain any sub regions anyway)
			if (pageStructImageRegions.contains(pageImageRegs[i]))
				continue;
			
			//	cut any labels out of main text
			ImWord[] imageWords = pageImageRegs[i].getWords();
			ImUtils.makeStream(imageWords, ImWord.TEXT_STREAM_TYPE_LABEL, null);
			
			//	clean up paragraphs, etc.
			for (int r = 0; r < pageRegs.length; r++) {
				if ((pageRegs[r] != pageImageRegs[i]) && pageRegs[r].bounds.liesIn(pageImageRegs[i].bounds, false))
					page.removeRegion(pageRegs[r]);
			}
		}
		
		//	sort our graphics that are too small to represent anything meaningful (boundary line artwork, etc.)
		pageGraphicsRegs = page.getRegions(ImRegion.GRAPHICS_TYPE);
		for (int g = 0; g < pageGraphicsRegs.length; g++) {
			if ((pageGraphicsRegs[g].bounds.getWidth() * 8) < pageImageDpi)
				page.removeRegion(pageGraphicsRegs[g]);
			else if ((pageGraphicsRegs[g].bounds.getHeight() * 8) < pageImageDpi)
				page.removeRegion(pageGraphicsRegs[g]);
		}
		
		//	clean up graphics (unless they are table grids ...)
		pageRegs = page.getRegions();
		pageGraphicsRegs = page.getRegions(ImRegion.GRAPHICS_TYPE);
		for (int g = 0; g < pageGraphicsRegs.length; g++) {
			
			//	cut any labels out of main text
			ImWord[] imageWords = pageGraphicsRegs[g].getWords();
			ImUtils.makeStream(imageWords, ImWord.TEXT_STREAM_TYPE_LABEL, null);
			
			//	clean up paragraphs, etc.
			for (int r = 0; r < pageRegs.length; r++) {
				if ((pageRegs[r] != pageGraphicsRegs[g]) && pageRegs[r].bounds.liesIn(pageGraphicsRegs[g].bounds, false))
					page.removeRegion(pageRegs[r]);
			}
		}
	}
	
	private static class ClusterBoundingBox extends BoundingBox implements Comparable {
		final int mergeCount;
		final boolean isBlock;
		ClusterBoundingBox(BoundingBox bb, boolean isBlock) {
			this(bb, 1, isBlock);
		}
		ClusterBoundingBox(BoundingBox bb, int mergeCount, boolean isBlock) {
			super(bb.left, bb.right, bb.top, bb.bottom);
			this.mergeCount = mergeCount;
			this.isBlock = isBlock;
		}
		ClusterBoundingBox(int left, int right, int top, int bottom, int mergeCount, boolean isBlock) {
			super(left, right, top, bottom);
			this.mergeCount = mergeCount;
			this.isBlock = isBlock;
		}
		int getDistance(BoundingBox bb) {
			if (this.overlaps(bb))
				return 0;
			int hDist = 0;
			if (bb.right < this.left)
				hDist = (this.left - bb.right);
			else if (this.right < bb.left)
				hDist = (bb.left - this.right);
			int vDist = 0;
			if (bb.bottom < this.top)
				vDist = (this.top - bb.bottom);
			else if (this.bottom < bb.top)
				vDist = (bb.top - this.bottom);
			return ((int) Math.round(Math.sqrt((hDist * hDist) + (vDist * vDist))));
		}
		boolean isAdjacentTo(BoundingBox bb) {
			if ((this.left <= bb.left) && (bb.right <= this.right))
				return true;
			if ((bb.left <= this.left) && (this.right <= bb.right))
				return true;
			
			if ((this.top <= bb.top) && (bb.bottom <= this.bottom))
				return true;
			if ((bb.top <= this.top) && (this.bottom <= bb.bottom))
				return true;
			
			int hSpan = (Math.max(this.right, bb.right) - Math.min(this.left, bb.left));
			int hOverlap = (Math.min(this.right, bb.right) - Math.max(this.left, bb.left));
			if ((hSpan * 24) < (hOverlap * 25))
				return true;
			
			int vSpan = (Math.max(this.bottom, bb.bottom) - Math.min(this.top, bb.top));
			int vOverlap = (Math.min(this.bottom, bb.bottom) - Math.max(this.top, bb.top));
			if ((vSpan * 24) < (vOverlap * 25))
				return true;
			
			return false;
		}
		ClusterBoundingBox mergeWith(ClusterBoundingBox cbb) {
			return new ClusterBoundingBox(
					Math.min(this.left,  cbb.left),
					Math.max(this.right,  cbb.right),
					Math.min(this.top,  cbb.top),
					Math.max(this.bottom,  cbb.bottom),
					(this.mergeCount + cbb.mergeCount),
					(this.isBlock && cbb.isBlock)
				);
		}
		public int compareTo(Object obj) {
			if (obj instanceof ClusterBoundingBox) {
				ClusterBoundingBox cbb = ((ClusterBoundingBox) obj);
				if (this.isBlock == cbb.isBlock)
					return (this.getArea() - cbb.getArea()); // ordered by size _ascending_ as we want to start out with smallest figures
				else return (cbb.isBlock ? -1 : 1); // keep non-blocks ahead of blocks
			}
			else return -1;
		}
	}
	
	private static BoundingBox shrinkToContent(BoundingBox bounds, byte[][] brightness) {
		int left = Math.max(bounds.left, 0);
		int right = Math.min(bounds.right, brightness.length);
		int top = Math.max(bounds.top, 0);
		int bottom = Math.min(bounds.bottom, brightness[0].length);
		
		//	narrow left and right
		while (left < right) {
			boolean leftVoid = true;
			boolean rightVoid = true;
			for (int r = top; r < bottom; r++) {
				if (brightness[left][r] < 127)
					leftVoid = false;
				if (brightness[right-1][r] < 127)
					rightVoid = false;
				if (!leftVoid && !rightVoid)
					break;
			}
			if (leftVoid)
				left++;
			if (rightVoid)
				right--;
			if (!leftVoid && !rightVoid)
				break;
		}
		
		//	narrow top and bottom
		while (top < bottom) {
			boolean topVoid = true;
			boolean bottomVoid = true;
			for (int c = left; c < right; c++) {
				if (brightness[c][top] < 127)
					topVoid = false;
				if (brightness[c][bottom-1] < 127)
					bottomVoid = false;
				if (!topVoid && !bottomVoid)
					break;
			}
			if (topVoid)
				top++;
			if (bottomVoid)
				bottom--;
			if (!topVoid && !bottomVoid)
				break;
		}
		
		//	finally ...
		if ((left < right) && (top < bottom))
			return new BoundingBox(left, right, top, bottom);
		else return null;
	}
	
	private static BoundingBox expandToWords(BoundingBox bounds, ImPage page) {
		
		//	get page words
		ImWord[] words = page.getWordsInside(bounds);
		if (words.length == 0)
			return bounds;
		
		//	expand bounding box to fully include words
		int left = Math.max(bounds.left, page.bounds.left);
		int right = Math.min(bounds.right, page.bounds.right);
		int top = Math.max(bounds.top, page.bounds.top);
		int bottom = Math.min(bounds.bottom, page.bounds.bottom);
		for (int w = 0; w < words.length; w++) {
			left = Math.min(left, words[w].bounds.left);
			right = Math.max(right, words[w].bounds.right);
			top = Math.min(top, words[w].bounds.top);
			bottom = Math.max(bottom, words[w].bounds.bottom);
		}
		
		//	finally ...
		return new BoundingBox(left, right, top, bottom);
	}
	
	private static void splitPageWords(PPageData pData, DefaultingMap pFlippedWords, Set pInFigureWords, Set pInGraphicsWords, Tokenizer tokenizer, Tokenizer numberTokenizer, ProgressMonitor spm) {
		
		//	split words that tokenize apart, splitting bounding box based on font measurement
		ArrayList pWordList = new ArrayList();
		for (int w = 0; w < pData.words.length; w++) {
			
			//	check tokenization
			TokenSequence pWordTokens = (Gamta.isNumber(pData.words[w].str) ? numberTokenizer.tokenize(pData.words[w].str) : tokenizer.tokenize(pData.words[w].str));
			if (pWordTokens.size() < 2) {
				pWordList.add(pData.words[w]);
				continue;
			}
			if (PdfExtractorTest.aimAtPage != -1) {
				System.out.println(" - splitting " + pData.words[w].str);
				System.out.print("   - char codes are " + pData.words[w].charCodes + " (");
				for (int c = 0; c < pData.words[w].charCodes.length(); c++)
					 System.out.print(((c == 0) ? "" : " ") + Integer.toString(((int) pData.words[w].charCodes.charAt(c)), 16));
				System.out.println(")");
			}
			
			//	get width for each token at word font size
			String[] splitCharCodes = new String[pWordTokens.size()];
			String[] splitTokens = new String[pWordTokens.size()];
			float[] splitTokenWidths = new float[pWordTokens.size()];
			int fontStyle = Font.PLAIN;
			if (pData.words[w].bold)
				fontStyle = (fontStyle | Font.BOLD);
			if (pData.words[w].italics)
				fontStyle = (fontStyle | Font.ITALIC);
			Font pWordFont = getFont(pData.words[w].font.name, fontStyle, pData.words[w].serif, Math.round(((float) pData.words[w].fontSize)));
			float splitTokenWidthSum = 0;
			int charCodeCharPos = 0;
			char charCodeChar = ((char) 0);
			int charCodeCharRepeatLeft = 0;
			for (int s = 0; s < splitTokens.length; s++) {
				splitTokens[s] = pWordTokens.valueAt(s);
				splitCharCodes[s] = ""; // have to do it this way, as char code string might have different length than Unicode string
				for (int splitCharCodeLength = 0; splitCharCodeLength < splitTokens[s].length();) {
					if (charCodeCharRepeatLeft == 0) {
						charCodeChar = pData.words[w].charCodes.charAt(charCodeCharPos++);
						charCodeCharRepeatLeft = (((int) charCodeChar) / 256);
					}
					int splitCharCodeRest = (splitTokens[s].length() - splitCharCodeLength);
					int splitCharCodeCharRepeat = Math.min(charCodeCharRepeatLeft, splitCharCodeRest);
					charCodeCharRepeatLeft -= splitCharCodeCharRepeat;
					splitCharCodes[s] += ((char) ((splitCharCodeCharRepeat * 256) | (((int) charCodeChar) & 255)));
					splitCharCodeLength += splitCharCodeCharRepeat;
				}
				TextLayout tl = new TextLayout(splitTokens[s], pWordFont, new FontRenderContext(null, false, true));
				splitTokenWidths[s] = ((float) tl.getBounds().getWidth());
				splitTokenWidthSum += splitTokenWidths[s];
			}
			
			//	left-right words
			if (pData.words[w].fontDirection == PWord.LEFT_RIGHT_FONT_DIRECTION) {
				
				//	store split result, splitting word bounds accordingly
				float pWordWidth = ((float) (pData.words[w].bounds.getMaxX() - pData.words[w].bounds.getMinX()));
				float splitTokenLeft = ((float) pData.words[w].bounds.getMinX());
				for (int s = 0; s < splitTokens.length; s++) {
					float splitTokenWidth = ((pWordWidth * splitTokenWidths[s]) / splitTokenWidthSum);
					PWord spWord = new PWord(pData.words[w].renderOrderNumber, splitCharCodes[s], splitTokens[s], new Rectangle2D.Float(splitTokenLeft, ((float) pData.words[w].bounds.getMinY()), splitTokenWidth, ((float) pData.words[w].bounds.getHeight())), pData.words[w].color, pData.words[w].fontSize, pData.words[w].fontDirection, pData.words[w].font);
					if ((s+1) == splitTokens.length)
						spWord.joinWithNext = pData.words[w].joinWithNext;
					pWordList.add(spWord);
					if ((pFlippedWords != null) && pFlippedWords.containsKey(pData.words[w]))
						pFlippedWords.put(spWord, pFlippedWords.get(pData.words[w]));
					if ((pInFigureWords != null) && pInFigureWords.contains(pData.words[w]))
						pInFigureWords.add(spWord);
					if ((pInGraphicsWords != null) && pInGraphicsWords.contains(pData.words[w]))
						pInGraphicsWords.add(spWord);
					splitTokenLeft += splitTokenWidth;
					if (PdfExtractorTest.aimAtPage != -1) {
						System.out.println("   --> split word part " + spWord.str + ", bounds are " + spWord.bounds);
						System.out.print("     - char codes are " + spWord.charCodes + " (");
						for (int c = 0; c < spWord.charCodes.length(); c++)
							 System.out.print(((c == 0) ? "" : " ") + Integer.toString(((int) spWord.charCodes.charAt(c)), 16));
						System.out.println(")");
					}
				}
			}
			
			//	bottom-up words
			else if (pData.words[w].fontDirection == PWord.BOTTOM_UP_FONT_DIRECTION) {
				
				//	store split result, splitting word bounds accordingly
				float pWordWidth = ((float) (pData.words[w].bounds.getMaxY() - pData.words[w].bounds.getMinY()));
				float splitTokenLeft = ((float) pData.words[w].bounds.getMinY());
				for (int s = 0; s < splitTokens.length; s++) {
					float splitTokenWidth = ((pWordWidth * splitTokenWidths[s]) / splitTokenWidthSum);
					PWord spWord = new PWord(pData.words[w].renderOrderNumber, splitCharCodes[s], splitTokens[s], new Rectangle2D.Float(((float) pData.words[w].bounds.getMinX()), splitTokenLeft, ((float) pData.words[w].bounds.getWidth()), splitTokenWidth), pData.words[w].color, pData.words[w].fontSize, pData.words[w].fontDirection, pData.words[w].font);
					if ((s+1) == splitTokens.length)
						spWord.joinWithNext = pData.words[w].joinWithNext;
					pWordList.add(spWord);
					if ((pFlippedWords != null) && pFlippedWords.containsKey(pData.words[w]))
						pFlippedWords.put(spWord, pFlippedWords.get(pData.words[w]));
					if ((pInFigureWords != null) && pInFigureWords.contains(pData.words[w]))
						pInFigureWords.add(spWord);
					if ((pInGraphicsWords != null) && pInGraphicsWords.contains(pData.words[w]))
						pInGraphicsWords.add(spWord);
					splitTokenLeft += splitTokenWidth;
					if (PdfExtractorTest.aimAtPage != -1) {
						System.out.println("   --> split word part " + spWord.str + ", bounds are " + spWord.bounds);
						System.out.print("     - char codes are " + spWord.charCodes + " (");
						for (int c = 0; c < spWord.charCodes.length(); c++)
							 System.out.print(((c == 0) ? "" : " ") + Integer.toString(((int) spWord.charCodes.charAt(c)), 16));
						System.out.println(")");
					}
				}
			}
			
			//	top-down words
			else if (pData.words[w].fontDirection == PWord.TOP_DOWN_FONT_DIRECTION) {
				
				//	store split result, splitting word bounds accordingly
				float pWordWidth = ((float) (pData.words[w].bounds.getMaxY() - pData.words[w].bounds.getMinY()));
				float splitTokenLeft = ((float) pData.words[w].bounds.getMaxY());
				for (int s = 0; s < splitTokens.length; s++) {
					float splitTokenWidth = ((pWordWidth * splitTokenWidths[s]) / splitTokenWidthSum);
					PWord spWord = new PWord(pData.words[w].renderOrderNumber, splitCharCodes[s], splitTokens[s], new Rectangle2D.Float(((float) pData.words[w].bounds.getMinX()), (splitTokenLeft - splitTokenWidth), ((float) pData.words[w].bounds.getWidth()), splitTokenWidth), pData.words[w].color, pData.words[w].fontSize, pData.words[w].fontDirection, pData.words[w].font);
					if ((s+1) == splitTokens.length)
						spWord.joinWithNext = pData.words[w].joinWithNext;
					pWordList.add(spWord);
					if ((pFlippedWords != null) && pFlippedWords.containsKey(pData.words[w]))
						pFlippedWords.put(spWord, pFlippedWords.get(pData.words[w]));
					if ((pInFigureWords != null) && pInFigureWords.contains(pData.words[w]))
						pInFigureWords.add(spWord);
					if ((pInGraphicsWords != null) && pInGraphicsWords.contains(pData.words[w]))
						pInGraphicsWords.add(spWord);
					splitTokenLeft -= splitTokenWidth;
					if (PdfExtractorTest.aimAtPage != -1) {
						System.out.println("   --> split word part " + spWord.str + ", bounds are " + spWord.bounds);
						System.out.print("     - char codes are " + spWord.charCodes + " (");
						for (int c = 0; c < spWord.charCodes.length(); c++)
							 System.out.print(((c == 0) ? "" : " ") + Integer.toString(((int) spWord.charCodes.charAt(c)), 16));
						System.out.println(")");
					}
				}
			}
		}
		
		//	refresh PWord array
		if (pWordList.size() != pData.words.length)
			pData.words = ((PWord[]) pWordList.toArray(new PWord[pWordList.size()]));
		pWordList = null;
	}
	
	private void sanitizePageWords(PPageData pData, Tokenizer tokenizer, float magnification, ProgressMonitor spm) {
		
		//	sort out words that lie outside the media box (normalize media box beforehand, ignoring orientation)
		ArrayList pWords = new ArrayList();
//		Rectangle2D pNormBox = new Rectangle2D.Float(0, 0, Math.abs(pData.pdfPageBox.width), Math.abs(pData.pdfPageBox.height));
//		System.out.println(" - raw page bounds are " + pData.pdfPageBox);
//		System.out.println(" - min/max X is " + pData.pdfPageBox.getMinX() + "/" + pData.pdfPageBox.getMaxX());
//		System.out.println(" - min/max Y is " + pData.pdfPageBox.getMinY() + "/" + pData.pdfPageBox.getMaxY());
		double pLeft = pData.pdfPageContentBox.getMinX();
		double pRight = pData.pdfPageContentBox.getMaxX();
		double pTop = pData.pdfPageContentBox.getMinY();
		double pBottom = (pTop - Math.abs(pData.pdfPageContentBox.height));
		Rectangle2D pNormBox = new Rectangle2D.Float(((float) pLeft), ((float) pBottom), Math.abs(pData.pdfPageContentBox.width), Math.abs(pData.pdfPageContentBox.height));
		spm.setInfo(" - removing words outside page bounds " + pNormBox);
		for (int w = 0; w < pData.words.length; w++) {
			if (pNormBox.contains(pData.words[w].bounds))
				pWords.add(pData.words[w]);
			else spm.setInfo("   - removed out-of-page word '" + pData.words[w].str + "' @" + pData.words[w].bounds);
		}
		if (pWords.size() < pData.words.length) {
			spm.setInfo(" ==> removed " + (pData.words.length - pWords.size()) + " out-of-page words");
			pData.words = ((PWord[]) pWords.toArray(new PWord[pWords.size()]));
		}
		
		//	shrink word bounding boxes to actual word size
		BufferedImage mbi = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY);
		Graphics2D mg = mbi.createGraphics();
		for (int w = 0; w < pData.words.length; w++)
			if (pData.words[w].str.trim().length() != 0) {
				pData.words[w] = shrinkWordBounds(mg, pData.words[w]);
			}
		
		//	sort out words overpainted by opaque graphics
		spm.setInfo(" - removing words overpainted by opaque graphics");
		pWords.clear();
		pWords.addAll(Arrays.asList(pData.words));
		Collections.sort(pWords);
		for (int p = 0; p < pData.paths.length; p++) {
			
			//	check path opacity
			if (!isOpaque(pData.paths[p]))
				continue;
			
			//	test word overlap with individual sub paths
			PSubPath[] pSubPaths = pData.paths[p].getSubPaths();
			for (int sp = 0; sp < pSubPaths.length; sp++) {
				
				//	check if sub path is rectangular
				Shape[] pShapes = pSubPaths[sp].getShapes();
				int subPathLineCount = 0;
				int subPathCurveCount = 0;
				for (int s = 0; s < pShapes.length; s++) {
					if (pShapes[s] instanceof Line2D)
						subPathLineCount++;
					else if (pShapes[s] instanceof CubicCurve2D)
						subPathCurveCount++;
				}
				if (subPathLineCount < subPathCurveCount)
					continue;
				
				//	get sub path bounds
				BoundingBox subPathBounds = this.getBoundingBox(pSubPaths[sp].getBounds(), pData.pdfPageContentBox, magnification, pData.rotate);
				
				//	collect words painted over by opaquely filled path, but only if it is non-artsy
				for (int w = 0; w < pWords.size(); w++) {
					PWord pw = ((PWord) pWords.get(w));
					
					//	rendered after current path
					if (pData.paths[p].renderOrderNumber < pw.renderOrderNumber)
						break; // we're done here, thanks to ordering of words
					
					//	check overlap
					BoundingBox wordBounds = this.getBoundingBox(pw.bounds, pData.pdfPageContentBox, magnification, pData.rotate);
					if (wordBounds.liesIn(subPathBounds, false)) {
						pWords.remove(w--);
						spm.setInfo("   - removed word '" + pw.str + "' @" + pw.bounds + " as overpainted by graphics path at " + pSubPaths[sp].getBounds());
					}
				}
			}
		}
		if (pWords.size() < pData.words.length) {
			spm.setInfo(" ==> removed " + (pData.words.length - pWords.size()) + " words overpainted by opaque graphics");
			pData.words = ((PWord[]) pWords.toArray(new PWord[pWords.size()]));
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
		
		/* merge or join subsequent words less than DPI/60 apart, as they are
		 * likely separated due to implicit spaces or font changes (at 10 pt,
		 * that is, using lower threshold for smaller font sizes); join words
		 * that have mismatch in font style or size, as such differences tend
		 * to bear some semantics, as well as ones with mismatching font names,
		 * so font corrections remain possible */
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
			
			//	check if words mergeable or joinable
			if (!areWordsMergeable(lpWord, lpWordTokens, pData.words[w], pWordTokens, maxMergeMargin10pt, tokenizer)) {
				lpWord = pData.words[w];
				lpWordTokens = pWordTokens;
				continue;
			}
			
			//	never merge or join numbers (unless font is same and has implicit spaces)
			if ((Gamta.isNumber(lpWord.str) || Gamta.isNumber(pData.words[w].str)) && ((lpWord.font == null) || (lpWord.font != pData.words[w].font) || !lpWord.font.hasImplicitSpaces)) {
				lpWord = pData.words[w];
				lpWordTokens = pWordTokens;
				if (DEBUG_MERGE_WORDS) System.out.println(" --> not joining or merging numbers");
				continue;
			}
			
			//	logically join (but never physically merge) words if font names don't match, messes up font char strings, and thus gets in the way of correction
			if ((lpWord.font != null) && (lpWord.font.name != null) && (pData.words[w].font != null) && (pData.words[w].font.name != null) && !lpWord.font.name.equals(pData.words[w].font.name)) {
				lpWord.joinWithNext = true;
				spm.setInfo(" --> joined words " + lpWord.str + " and " + pData.words[w].str);
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
			PWord mpWord = new PWord(Math.min(lpWord.renderOrderNumber, pData.words[w].renderOrderNumber), (lpWord.charCodes + pData.words[w].charCodes), (lpWord.str + pData.words[w].str), mpWordBounds, lpWord.color, lpWord.fontSize, lpWord.fontDirection, mpWordFont);
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
	
	private void storeFiguresAsSupplements(ImDocument doc, PPageData pData, Document pdfDoc, Map objects, float magnification, Map figureSupplements, ProgressMonitor spm) throws IOException {
		spm.setInfo(" - storing figures");
		
		//	display figures if testing
		ImageDisplayDialog fdd = null;
		if (DEBUG_EXTRACT_FIGURES && (PdfExtractorTest.aimAtPage >= 0))
			fdd = new ImageDisplayDialog("Figures in Page " + pData.p);
		
		//	store figures
		for (int f = 0; f < pData.figures.length; f++) {
			PFigure pFigure = pData.figures[f];
			spm.setInfo("   - " + pFigure);
			
			//	get image
			BufferedImage pFigureImage = this.getFigureImage(doc, pFigure, true, pdfDoc, pData.pdfPage, pData.p, pData.pdfPageContentBox, /*xObjects, */objects, magnification, figureSupplements, spm);
			
			//	display figures if testing
			if ((fdd != null) && (pFigureImage != null)) {
				BoundingBox pFigureBox = this.getBoundingBox(pFigure.bounds, pData.pdfPageContentBox, magnification, pData.rotate);
				spm.setInfo("     - rendering bounds are " + pFigureBox);
				fdd.addImage(pFigureImage, pFigureBox.toString());
			}
		}
		
		//	display figures if testing
		if (fdd != null) {
			fdd.setSize(600, 800);
			fdd.setLocationRelativeTo(null);
			fdd.setVisible(true);
		}
	}
	
	private void sanitizePageGraphics(PPageData pData, float magnification, ProgressMonitor spm) {
		if (pData.paths.length == 0)
			return;
		Arrays.sort(pData.paths);
		
		//	get (and normalize) page bounds
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println(" - PDF page bounds are " + pData.pdfPageContentBox);
		Rectangle2D.Float pageBounds;
		if (pData.pdfPageContentBox.height <= pData.pdfPageContentBox.y)
			pageBounds = new Rectangle2D.Float(pData.pdfPageContentBox.x, (pData.pdfPageContentBox.y - pData.pdfPageContentBox.height), pData.pdfPageContentBox.width, pData.pdfPageContentBox.height);
		else pageBounds = pData.pdfPageContentBox;
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println(" - page bounds are " + pageBounds);
		BoundingBox pageBox = getBoundingBox(pageBounds, pData.pdfPageContentBox, 1, 0);
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println(" - page box is " + pageBox);
		
		//	create grid of minimum render order number in each point on page
		int[][] minRenderOrderNumbers = new int[Math.round(pData.pdfPageContentBox.width) + 2][Math.round(pData.pdfPageContentBox.height) + 2];
		for (int c = 0; c < minRenderOrderNumbers.length; c++)
			Arrays.fill(minRenderOrderNumbers[c], Integer.MAX_VALUE);
		for (int f = 0; f < pData.figures.length; f++) {
			BoundingBox figureBox = getBoundingBox(pData.figures[f].bounds, pData.pdfPageContentBox, 1, 0);
			if (figureBox.overlaps(pageBox)) {
				for (int c = 0; c < Math.max(1, figureBox.getWidth()); c++) {
					if ((figureBox.left + c + 1) < 0)
						continue;
					if ((figureBox.left + c + 1) >= minRenderOrderNumbers.length)
						continue;
					int[] cMinRenderOrderNumbers = minRenderOrderNumbers[figureBox.left + c + 1];
					for (int r = 0; r < Math.max(1, figureBox.getHeight()); r++) {
						if ((figureBox.top + r + 1) < 0)
							continue;
						if ((figureBox.top + r + 1) >= cMinRenderOrderNumbers.length)
							continue;
						cMinRenderOrderNumbers[figureBox.top + r + 1] = Math.min(cMinRenderOrderNumbers[figureBox.top + r + 1], pData.figures[f].renderOrderNumber);
					}
				}
			}
		}
		for (int w = 0; w < pData.words.length; w++) {
			BoundingBox wordBox = getBoundingBox(pData.words[w].bounds, pData.pdfPageContentBox, 1, 0);
			if (wordBox.overlaps(pageBox)) {
				for (int c = 0; c < Math.max(1, wordBox.getWidth()); c++) {
					if ((wordBox.left + c + 1) < 0)
						continue;
					if ((wordBox.left + c + 1) >= minRenderOrderNumbers.length)
						continue;
					int[] cMinRenderOrderNumbers = minRenderOrderNumbers[wordBox.left + c + 1];
					for (int r = 0; r < Math.max(1, wordBox.getHeight()); r++) {
						if ((wordBox.top + r + 1) < 0)
							continue;
						if ((wordBox.top + r + 1) >= cMinRenderOrderNumbers.length)
							continue;
						cMinRenderOrderNumbers[wordBox.top + r + 1] = Math.min(cMinRenderOrderNumbers[wordBox.top + r + 1], pData.words[w].renderOrderNumber);
					}
				}
			}
		}
		spm.setInfo(" - spacial distribution of minimum render order number computed");
		
		//	compute minimum rendering order number of any non-graphics object (word or figure)
		int minNonGraphicsRenderOrderNumber = Integer.MAX_VALUE;
		for (int f = 0; f < pData.figures.length; f++) {
			BoundingBox figureBox = getBoundingBox(pData.figures[f].bounds, pData.pdfPageContentBox, 1, 0);
			if (figureBox.overlaps(pageBox))
				minNonGraphicsRenderOrderNumber = Math.min(minNonGraphicsRenderOrderNumber, pData.figures[f].renderOrderNumber);
		}
		for (int w = 0; w < pData.words.length; w++) {
			BoundingBox wordBox = getBoundingBox(pData.words[w].bounds, pData.pdfPageContentBox, 1, 0);
			if (wordBox.overlaps(pageBox))
				minNonGraphicsRenderOrderNumber = Math.min(minNonGraphicsRenderOrderNumber, pData.words[w].renderOrderNumber);
		}
		spm.setInfo(" - minimum non-graphics render order number is " + minNonGraphicsRenderOrderNumber);
		
		//	sort out paths that lie outside page, as well as paths rendering bright on white (we need to do that in render order number)
		spm.setInfo(" - removing invisible paths");
		ArrayList pPaths = new ArrayList();
		for (int p = 0; p < pData.paths.length; p++) {
			Rectangle2D pathBounds = pData.paths[p].getBounds();
			spm.setInfo("   - checking path at " + pathBounds + " with RON " + pData.paths[p].renderOrderNumber);
			if (PdfExtractorTest.aimAtPage != -1) {
				System.out.println("Handling path at " + pathBounds);
				System.out.println(" - stroke color is " + pData.paths[p].strokeColor + ((pData.paths[p].strokeColor == null) ? "" : (", alpha " + pData.paths[p].strokeColor.getAlpha())));
				System.out.println(" - line width is " + pData.paths[p].lineWidth);
				System.out.println(" - fill color is " + pData.paths[p].fillColor + ((pData.paths[p].fillColor == null) ? "" : (", alpha " + pData.paths[p].fillColor.getAlpha())));
				System.out.println(" - render order number is " + pData.paths[p].renderOrderNumber);
			}
			
			//	get brightness of stroke and fill colors
			float strokeBrightness = 1.0f;
			if (pData.paths[p].strokeColor != null)
				strokeBrightness = getBrightness(pData.paths[p].strokeColor);
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" - stroke brightness is " + strokeBrightness);
			float fillBrightness = 1.0f;
			if (pData.paths[p].fillColor != null)
				fillBrightness = getBrightness(pData.paths[p].fillColor);
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" - fill brightness is " + fillBrightness);
			
			//	if path painted and filled in white, check if it is on top of anything non-white
			//	TODO assess if 14/16th white for stroking and 15/16 white for filling are sensible thresholds
//			if ((strokeBrightness >= 0.99) && (fillBrightness >= 0.99)) {
			if ((strokeBrightness > 0.88) && (fillBrightness > 0.94)) {
				
				//	painted right on white page, we don't need this one
				if (pData.paths[p].renderOrderNumber < minNonGraphicsRenderOrderNumber) {
					spm.setInfo("     ==> removed as bright on white (RON)");
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println(" ==> removed as bright on white (RON)");
					continue;
				}
				
				//	depending on blend mode, a light overpaint does not have any significant effect
				if ((pData.paths[p].blendMode != null) && (pData.paths[p].blendMode.lightIsTranslucent())) {
					spm.setInfo("     ==> removed as bright on white (BM)");
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println(" ==> removed as bright on white (BM)");
					continue;
				}
				
				//	check minimum over-painted render order number in grid
				BoundingBox pathBox = getBoundingBox(pathBounds, pData.pdfPageContentBox, 1, 0);
				int minOverlappingRenderOrderNumber = Integer.MAX_VALUE;
				for (int c = 0; c < Math.max(1, pathBox.getWidth()); c++) {
					if ((pathBox.left + c + 1) < 0)
						continue;
					if ((pathBox.left + c + 1) >= minRenderOrderNumbers.length)
						continue;
					int[] cMinRenderOrderNumbers = minRenderOrderNumbers[pathBox.left + c + 1];
					for (int r = 0; r < Math.max(1, pathBox.getHeight()); r++) {
						if ((pathBox.top + r + 1) < 0)
							continue;
						if ((pathBox.top + r + 1) >= cMinRenderOrderNumbers.length)
							continue;
						minOverlappingRenderOrderNumber = Math.min(cMinRenderOrderNumbers[pathBox.top + r + 1], minOverlappingRenderOrderNumber);
					}
				}
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println(" - minimum overlapping render order number is " + minOverlappingRenderOrderNumber);
				
				//	painted right on white page, we don't need this one
				if (pData.paths[p].renderOrderNumber < minOverlappingRenderOrderNumber) {
					spm.setInfo("     ==> removed as bright on white for minimum overpainted RON " + minOverlappingRenderOrderNumber);
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println(" ==> removed as bright on white (RON)");
					continue;
				}
			}
			
			//	check sub paths
			PSubPath[] pSubPaths = pData.paths[p].getSubPaths();
			int inPageSubPathCount = 0;
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" - sub paths are:");
			for (int s = 0; s < pSubPaths.length; s++) {
				BoundingBox spBox = getBoundingBox(pSubPaths[s].getBounds(), pData.pdfPageContentBox, 1, 0);
				if (spBox.overlaps(pageBox))
					inPageSubPathCount++;
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println("   - " + pSubPaths[s].getBounds() + ", " + (spBox.overlaps(pageBox) ? "inside" : "outside") + " page");
			}
			
			//	skip over path completely outside page
			if (inPageSubPathCount == 0) {
				spm.setInfo("     ==> removed as completely outside page");
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println(" ==> removed as outside page");
				continue;
			}
			
			//	skip over invisible paths
			if ((pData.paths[p].strokeColor == null) && (pData.paths[p].fillColor == null)) {
				spm.setInfo("     ==> removed as neigher stroked nor filled");
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println(" ==> removed as neigher stroked nor filled");
				continue;
			}
			
			//	retain path, at last
			pPaths.add(pData.paths[p]);
			
			//	update minimum render order number in grid
			BoundingBox pathBox = getBoundingBox(pathBounds, pData.pdfPageContentBox, 1, 0);
			for (int c = 0; c < Math.max(1, pathBox.getWidth()); c++) {
				if ((pathBox.left + c + 1) < 0)
					continue;
				if ((pathBox.left + c + 1) >= minRenderOrderNumbers.length)
					continue;
				int[] cMinRenderOrderNumbers = minRenderOrderNumbers[pathBox.left + c + 1];
				for (int r = 0; r < Math.max(1, pathBox.getHeight()); r++) {
					if ((pathBox.top + r + 1) < 0)
						continue;
					if ((pathBox.top + r + 1) >= cMinRenderOrderNumbers.length)
						continue;
					cMinRenderOrderNumbers[pathBox.top + r + 1] = Math.min(cMinRenderOrderNumbers[pathBox.top + r + 1], pData.paths[p].renderOrderNumber);
				}
			}
		}
		if (pPaths.size() < pData.paths.length) {
			spm.setInfo(" ==> removed " + (pData.paths.length - pPaths.size()) + " paths as invisible");
			pData.paths = ((PPath[]) pPaths.toArray(new PPath[pPaths.size()]));
		}
	}
	
	private void storeGraphicsAsSupplements(ImDocument doc, PPageData pData, Document pdfDoc, float magnification, ProgressMonitor spm) throws IOException {
		if (pData.paths.length == 0)
			return;
		spm.setInfo(" - storing vector based graphics");
		
		//	get (and normalize) page bounds
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println("Page bounds are " + pData.pdfPageContentBox);
		Rectangle2D.Float pageBounds;
		if (pData.pdfPageContentBox.height <= pData.pdfPageContentBox.y)
			pageBounds = new Rectangle2D.Float(pData.pdfPageContentBox.x, (pData.pdfPageContentBox.y - pData.pdfPageContentBox.height), pData.pdfPageContentBox.width, pData.pdfPageContentBox.height);
		else pageBounds = pData.pdfPageContentBox;
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println("Page box is " + pageBounds);
		BoundingBox pageBox = getBoundingBox(pageBounds, pData.pdfPageContentBox, 1, 0);
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println("Page box is " + pageBox);
		
		//	sort paths by area (descending) to speed up clustering
		PPath[] pPaths = new PPath[pData.paths.length];
		System.arraycopy(pData.paths, 0, pPaths, 0, pData.paths.length);
		Arrays.sort(pPaths, Collections.reverseOrder(pathSizeOrder));
		
		//	aggregate paths
		ArrayList pPathClusters = new ArrayList();
		for (;;) {
			PPathCluster pPathCluster = null;
			for (boolean pathAdded = true; pathAdded;) {
				pathAdded = false;
				for (int p = 0; p < pPaths.length; p++) {
					if (pPaths[p] == null)
						continue; // handled this one before
					Rectangle2D pathBounds = pPaths[p].getBounds();
					if (PdfExtractorTest.aimAtPage != -1) {
						System.out.println("Handling path at " + pathBounds);
						System.out.println(" - stroke color is " + pPaths[p].strokeColor + ((pPaths[p].strokeColor == null) ? "" : (", alpha " + pPaths[p].strokeColor.getAlpha())));
						System.out.println(" - line width is " + pPaths[p].lineWidth);
						System.out.println(" - fill color is " + pPaths[p].fillColor + ((pPaths[p].fillColor == null) ? "" : (", alpha " + pPaths[p].fillColor.getAlpha())));
						System.out.println(" - render order number is " + pPaths[p].renderOrderNumber);
					}
					
					//	start new aggregate path
					if (pPathCluster == null) {
						pPathCluster = new PPathCluster(pPaths[p]);
						pathAdded = true;
						pPaths[p] = null;
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println(" ==> started new cluster");
					}
					
					//	this one overlaps with current cluster, add it
					else if (pPathCluster.overlaps(pPaths[p])) {
						pPathCluster.addPath(pPaths[p]);
						pathAdded = true;
						pPaths[p] = null;
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println(" ==> added to current cluster");
					}
					else if (PdfExtractorTest.aimAtPage != -1)
						System.out.println(" ==> outside current cluster");
				}
			}
			if (pPathCluster != null) {
				spm.setInfo("   - got paths cluster with " + pPathCluster.paths.size() + " paths at " + pPathCluster.bounds);
				pPathClusters.add(pPathCluster);
			}
			else break;
		}
		spm.setInfo("   - aggreagted " + pPaths.length + " paths into " + pPathClusters.size() + " clusters");
		
		//	restore original painting order
		System.arraycopy(pData.paths, 0, pPaths, 0, pData.paths.length);
		for (int c = 0; c < pPathClusters.size(); c++) {
			PPathCluster pPathCluster = ((PPathCluster) pPathClusters.get(c));
			pPathCluster.paths.clear();
			for (int p = 0; p < pPaths.length; p++) {
				if (pPaths[p] == null)
					continue; // handled this one before
				
				//	re-check if any sub paths inside page
				PSubPath[] pSubPaths = pPaths[p].getSubPaths();
				int inPageSubPathCount = 0;
				for (int s = 0; s < pSubPaths.length; s++) {
					BoundingBox spBox = getBoundingBox(pSubPaths[s].getBounds(), pData.pdfPageContentBox, 1, 0);
					if (spBox.overlaps(pageBox))
						inPageSubPathCount++;
				}
				
				//	skip over path completely outside page
				if (inPageSubPathCount == 0) {
					pPaths[p] = null;
					continue;
				}
				
				//	add path to cluster
				if (pPathCluster.overlaps(pPaths[p])) {
					pPathCluster.paths.add(pPaths[p]);
					pPaths[p] = null;
				}
			}
		}
		
		//	display graphics if testing
		ImageDisplayDialog gdd = null;
		BufferedImage agi = null;
		Graphics2D agiGr = null;
		if (DEBUG_EXTRACT_FIGURES && (PdfExtractorTest.aimAtPage >= 0)) {
			gdd = new ImageDisplayDialog("Graphics in Page " + pData.p);
			agi = new BufferedImage((((int) Math.ceil(pData.pdfPageContentBox.getWidth() * magnification)) + 20), (((int) Math.ceil(pData.pdfPageContentBox.getHeight() * magnification)) + 20), BufferedImage.TYPE_INT_ARGB);
			agiGr = agi.createGraphics();
			agiGr.setColor(Color.WHITE);
			agiGr.fillRect(0, 0, agi.getWidth(), agi.getHeight());
			agiGr.translate(10, 10);
			agiGr.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		}
		
		//	store vector graphics
		for (int c = 0; c < pPathClusters.size(); c++) {
			PPathCluster pPathCluster = ((PPathCluster) pPathClusters.get(c));
			spm.setInfo("   - " + pPathCluster.bounds);
			
			//	compute bounds in page resolution
			BoundingBox pathClusterBox = this.getBoundingBox(pPathCluster.bounds, pData.pdfPageContentBox, magnification, pData.rotate);
			
			//	add supplement to document
			Graphics graphics;
			synchronized (doc) { // need to synchronize adding graphics to document
				graphics = Graphics.createGraphics(doc, pData.p, ((PPath) pPathCluster.paths.get(0)).renderOrderNumber, pathClusterBox);
			}
			
			//	add individual paths (with stroking and filling properties)
			for (int p = 0; p < pPathCluster.paths.size(); p++) {
				
				//	add path proper
				PPath pPath = ((PPath) pPathCluster.paths.get(p));
				BoundingBox pathBounds = this.getBoundingBox(pPath.getBounds(), pData.pdfPageContentBox, magnification, pData.rotate);
				Graphics.Path path = new Graphics.Path(pathBounds, pPath.renderOrderNumber, pPath.strokeColor, pPath.lineWidth, pPath.lineCapStyle, pPath.lineJointStyle, pPath.miterLimit, pPath.dashPattern, pPath.dashPatternPhase, pPath.fillColor);
				graphics.addPath(path);
				
				//	add individual sub paths
				PSubPath[] pSubPaths = pPath.getSubPaths();
				for (int sp = 0; sp < pSubPaths.length; sp++) {
					
					//	add sub path proper
					BoundingBox subPathBounds = this.getBoundingBox(pSubPaths[sp].getBounds(), pData.pdfPageContentBox, magnification, pData.rotate);
					Graphics.SubPath subPath = new Graphics.SubPath(subPathBounds);
					path.addSubPath(subPath);
					
					//	add individual shapes
					Shape[] pShapes = pSubPaths[sp].getShapes();
					for (int s = 0; s < pShapes.length; s++) {
						if (pShapes[s] instanceof Line2D) {
							float x1 = ((float) ((Line2D) pShapes[s]).getX1());
							float y1 = ((float) ((Line2D) pShapes[s]).getY1());
							float x2 = ((float) ((Line2D) pShapes[s]).getX2());
							float y2 = ((float) ((Line2D) pShapes[s]).getY2());
							
							//	translate X coordinates to left edge of cluster bounds
							x1 -= ((float) pPathCluster.bounds.getMinX());
							x2 -= ((float) pPathCluster.bounds.getMinX());
							
							//	translate Y coordinates to bottom edge of cluster bounds (we're still in PDF coordinates)
							y1 -= ((float) pPathCluster.bounds.getMinY());
							y2 -= ((float) pPathCluster.bounds.getMinY());
							
							//	translate Y coordinates to Java (0 at top instead of bottom)
							y1 = ((float) (pPathCluster.bounds.getHeight() - y1));
							y2 = ((float) (pPathCluster.bounds.getHeight() - y2));
							
							//	store line
							subPath.addLine(new Line2D.Float(x1, y1, x2, y2));
						}
						else if (pShapes[s] instanceof CubicCurve2D) {
							float x1 = ((float) ((CubicCurve2D) pShapes[s]).getX1());
							float y1 = ((float) ((CubicCurve2D) pShapes[s]).getY1());
							float cx1 = ((float) ((CubicCurve2D) pShapes[s]).getCtrlX1());
							float cy1 = ((float) ((CubicCurve2D) pShapes[s]).getCtrlY1());
							float cx2 = ((float) ((CubicCurve2D) pShapes[s]).getCtrlX2());
							float cy2 = ((float) ((CubicCurve2D) pShapes[s]).getCtrlY2());
							float x2 = ((float) ((CubicCurve2D) pShapes[s]).getX2());
							float y2 = ((float) ((CubicCurve2D) pShapes[s]).getY2());
							
							//	translate X coordinates to left edge of cluster bounds
							x1 -= ((float) pPathCluster.bounds.getMinX());
							cx1 -= ((float) pPathCluster.bounds.getMinX());
							cx2 -= ((float) pPathCluster.bounds.getMinX());
							x2 -= ((float) pPathCluster.bounds.getMinX());
							
							//	translate Y coordinates to bottom edge of cluster bounds (we're still in PDF coordinates)
							y1 -= ((float) pPathCluster.bounds.getMinY());
							cy1 -= ((float) pPathCluster.bounds.getMinY());
							cy2 -= ((float) pPathCluster.bounds.getMinY());
							y2 -= ((float) pPathCluster.bounds.getMinY());
							
							//	translate Y coordinates to Java (0 at top instead of bottom)
							y1 = ((float) (pPathCluster.bounds.getHeight() - y1));
							cy1 = ((float) (pPathCluster.bounds.getHeight() - cy1));
							cy2 = ((float) (pPathCluster.bounds.getHeight() - cy2));
							y2 = ((float) (pPathCluster.bounds.getHeight() - y2));
							
							//	store curve
							subPath.addCurve(new CubicCurve2D.Float(x1, y1, cx1, cy1, cx2, cy2, x2, y2));
						}
					}
				}
			}
			
			//	display graphics if testing
			if (gdd != null) {
				BufferedImage pPathClusterImage = new BufferedImage((((int) Math.ceil(pPathCluster.bounds.getWidth() * magnification)) + 20), (((int) Math.ceil(pPathCluster.bounds.getHeight() * magnification)) + 20), BufferedImage.TYPE_INT_ARGB);
				Graphics2D gr = pPathClusterImage.createGraphics();
				gr.setColor(Color.WHITE);
				gr.fillRect(0, 0, pPathClusterImage.getWidth(), pPathClusterImage.getHeight());
				gr.translate(10, 10);
				gr.scale(magnification, magnification);
				gr.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				Graphics.Path[] paths = graphics.getPaths();
				for (int p = 0; p < paths.length; p++) {
					Color preColor = gr.getColor();
					Stroke preStroke = gr.getStroke();
					Color strokeColor = paths[p].getStrokeColor();
					Stroke stroke = paths[p].getStroke();
					Color fillColor = paths[p].getFillColor();
					Graphics.SubPath[] subPaths = paths[p].getSubPaths();
					for (int s = 0; s < subPaths.length; s++) {
						Path2D path = subPaths[s].getPath();
						if (fillColor != null) {
							gr.setColor(fillColor);
							gr.fill(path);
						}
						if (strokeColor != null) {
							gr.setColor(strokeColor);
							gr.setStroke(stroke);
							gr.draw(path);
						}
						else if (fillColor != null) {
							gr.setColor(fillColor);
							gr.draw(path);
						}
					}
					gr.setColor(preColor);
					gr.setStroke(preStroke);
				}
				gdd.addImage(pPathClusterImage, pathClusterBox.toString());
				AffineTransform preAt = agiGr.getTransform();
				agiGr.translate(pathClusterBox.left, pathClusterBox.top);
				agiGr.scale(magnification, magnification);
				for (int p = 0; p < paths.length; p++) {
					Color preColor = agiGr.getColor();
					Stroke preStroke = agiGr.getStroke();
					Color strokeColor = paths[p].getStrokeColor();
					Stroke stroke = paths[p].getStroke();
					Color fillColor = paths[p].getFillColor();
					Graphics.SubPath[] subPaths = paths[p].getSubPaths();
					for (int s = 0; s < subPaths.length; s++) {
						Path2D path = subPaths[s].getPath();
						if (fillColor != null) {
							agiGr.setColor(fillColor);
							agiGr.fill(path);
						}
						if (strokeColor != null) {
							agiGr.setColor(strokeColor);
							agiGr.setStroke(stroke);
							agiGr.draw(path);
						}
						else if (fillColor != null) {
							agiGr.setColor(fillColor);
							agiGr.draw(path);
						}
					}
					agiGr.setColor(preColor);
					agiGr.setStroke(preStroke);
				}
				agiGr.setTransform(preAt);
			}
		}
		
		//	display graphics if testing
		if (gdd != null) {
			gdd.addImage(agi, "All");
			gdd.setSize(600, 800);
			gdd.setLocationRelativeTo(null);
			gdd.setVisible(true);
		}
	}
	
	private static float getBrightness(Color color) {
		int a = color.getAlpha();
		int r = color.getRed();
		int g = color.getGreen();
		int b = color.getBlue();
		if (a < 255) { // compute resulting values if pained on white background
			r = (((r * a) + (255 * (255 - a))) / 255);
			g = (((g * a) + (255 * (255 - a))) / 255);
			b = (((b * a) + (255 * (255 - a))) / 255);
		}
		int max = Math.max(r, Math.max(g, b));
//		return (((float) max) / 255); // this is HSB, maxes out for full-saturation colors like glaring red, which we surely do NOT want
		int min = Math.min(r, Math.min(g, b));
		return (((float) (max + min)) / (2 * 255)); // this is HSL, which what we really need to measure faintness
	}
	
	private static final Comparator pathSizeOrder = new Comparator() {
		public int compare(Object obj1, Object obj2) {
			Rectangle2D b1 = ((PPath) obj1).getBounds();
			Rectangle2D b2 = ((PPath) obj2).getBounds();
			return Double.compare((b1.getWidth() * b1.getHeight()), (b2.getWidth() * b2.getHeight()));
		}
	};
	
//	private static final double overlapTolerance = 0.01; // distance tolerance in overlap computation, to compensate for line width, etc.
	private static final double overlapTolerance = (((float) defaultDpi) / defaultTextPdfPageImageDpi); // distance tolerance in overlap computation, to compensate for line width, etc., set to what will end up on adjacent pixels in the final document
	private static boolean overlaps(Rectangle2D r1, Rectangle2D r2) {
		if ((r2.getMaxX() + overlapTolerance) < r1.getMinX())
			return false;
		if ((r2.getMinX() - overlapTolerance) > r1.getMaxX())
			return false;
		if ((r2.getMaxY() + overlapTolerance) < r1.getMinY())
			return false;
		if ((r2.getMinY() - overlapTolerance) > r1.getMaxY())
			return false;
		return true;
	}
	
	private static boolean isOpaque(PPath pPath) {
		if (pPath.fillColor == null)
			return false;
		if (pPath.fillColor.getAlpha() < 64) // TODO check this threshold
			return true;
		if (pPath.blendMode == null)
			return true;
		float fillBrightness = getBrightness(pPath.fillColor);
		if ((fillBrightness > 0.94) && pPath.blendMode.lightIsTranslucent())
			return false;
		if ((fillBrightness < 0.06) && pPath.blendMode.darkIsTranslucent())
			return false;
		return true;
	}
	
	private static class PPathCluster {
		ArrayList paths = new ArrayList();
		Rectangle2D bounds;
		PPathCluster(PPath firstPath) {
			this.paths.add(firstPath);
			Rectangle2D fpBounds = firstPath.getBounds();
			this.bounds = new Rectangle2D.Float(((float) fpBounds.getX()), ((float) fpBounds.getY()), ((float) fpBounds.getWidth()), ((float) fpBounds.getHeight()));
		}
		boolean overlaps(PPath path) {
			return PdfExtractor.overlaps(path.getBounds(), this.bounds);
		}
		void addPath(PPath path) {
			this.paths.add(path);
			this.bounds = this.bounds.createUnion(path.getBounds());
		}
	}
//	
//	private static BufferedImage flipBlockImage(BufferedImage pageImage, Rectangle2D.Float pageBox, int fbLeft, int fbRight, int fbTop, int fbBottom, PWord[] pWords, int fbWordFontDirection, float magnification) {
//		System.out.println("Flipping block " + fbLeft + "-" + fbRight + " x " + fbTop + "-" + fbBottom);
//		
//		//	compute minimum page margin in IM space
//		int pageLeft = 0;
//		int pageRight = Math.round(pageBox.width * magnification);
//		int pageTop = 0;
//		int pageBottom = Math.round(pageBox.height * magnification);
//		int minPageMargin = Integer.MAX_VALUE;
//		for (int w = 0; w < pWords.length; w++) {
//			Rectangle2D wb = pWords[w].bounds;
//			
//			//	convert bounds, as PDF Y coordinate is bottom-up, whereas Java, JavaScript, etc. Y coordinate is top-down
//			int left = Math.round(((float) (wb.getMinX() - pageBox.getMinX())) * magnification);
//			int right = Math.round(((float) (wb.getMaxX() - pageBox.getMinX()))  * magnification);
//			int top = Math.round((pageBox.height - ((float) (wb.getMaxY() - ((2 * pageBox.getMinY()) - pageBox.getMaxY())))) * magnification);
//			int bottom = Math.round((pageBox.height - ((float) (wb.getMinY() - ((2 * pageBox.getMinY()) - pageBox.getMaxY())))) * magnification);
//			
//			//	adjust minimum
//			minPageMargin = Math.min(minPageMargin, (left - pageLeft));
//			minPageMargin = Math.min(minPageMargin, (pageRight - right));
//			minPageMargin = Math.min(minPageMargin, (top - pageTop));
//			minPageMargin = Math.min(minPageMargin, (pageBottom - bottom));
//		}
//		System.out.println(" - page is " + pageLeft + "-" + pageRight + " x " + pageTop + "-" + pageBottom);
//		System.out.println(" - min margin is " + minPageMargin);
//		
//		//	compute center point of to-flip block and right shift in IM space
//		int fbCenterX = ((fbLeft + fbRight) / 2);
//		int fbCenterY = ((fbTop + fbBottom) / 2);
//		System.out.println(" - flip block center is " + fbCenterX + "," + fbCenterY);
//		int fbFlippedLeft = (fbCenterX - ((fbBottom - fbTop) / 2));
//		System.out.println(" - flipped block left will be " + fbFlippedLeft);
//		int fbRightShift = Math.max(0, (minPageMargin - fbFlippedLeft));
//		System.out.println(" --> right shift is " + fbRightShift);
//		
//		//	create new page image
//		int fbPageImageWidth = Math.max(pageImage.getWidth(), ((fbBottom - fbTop) + (2 * minPageMargin)));
//		BufferedImage fbPageImage = new BufferedImage(fbPageImageWidth, pageImage.getHeight(), pageImage.getType());
//		Graphics2D fbpig = fbPageImage.createGraphics();
//		fbpig.setColor(Color.WHITE);
//		fbpig.fillRect(0, 0, fbPageImage.getWidth(), fbPageImage.getHeight()); // paint background
//		fbpig.drawImage(pageImage, 0, 0, null); // transfer original page image
//		fbpig.fillRect(fbLeft, fbTop, (fbRight - fbLeft), (fbBottom - fbTop)); // erase to-flip block
//		fbpig.translate((fbCenterX - ((fbBottom - fbTop) / 2)), (fbCenterY - ((fbRight - fbLeft) / 2))); // shift to top-left corner of flipped block 
//		fbpig.translate(fbRightShift, 0); // add right shift to keep flipped block out of page margin
//		if (fbWordFontDirection == PWord.BOTTOM_UP_FONT_DIRECTION)
//			fbpig.translate((fbBottom - fbTop), 0); // compensate for top-left corner of to-flip block being top-right in flipped block
//		else if (fbWordFontDirection == PWord.TOP_DOWN_FONT_DIRECTION)
//			fbpig.translate(0, (fbRight - fbLeft)); // compensate for top-left corner of to-flip block being bottom-left in flipped block
//		fbpig.rotate(((Math.PI / 2) * ((fbWordFontDirection == PWord.BOTTOM_UP_FONT_DIRECTION) ? 1 : -1)));
//		fbpig.drawImage(pageImage.getSubimage(fbLeft, fbTop, (fbRight - fbLeft), (fbBottom - fbTop)), 0, 0, null);
//		fbpig.dispose();
//		
//		//	finally ...
//		return fbPageImage;
//	}
	
	private static BoundingBox getFlippedContentPageBounds(BoundingBox pageBox, Rectangle2D.Float pageBounds, BoundingBox flipContentBox, PPageData pData, DefaultingMap flippedObjects, int fbWordFontDirection, float magnification) {
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println("Flipping block " + flipContentBox);
		
		//	compute minimum page margin in IM space
		int pageLeft = 0;
		int pageRight = Math.round(pageBounds.width * magnification);
		int pageTop = 0;
		int pageBottom = Math.round(pageBounds.height * magnification);
		int minPageMargin = Integer.MAX_VALUE;
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println(" - computing page margin and flip block position");
		for (int w = 0; w < pData.words.length; w++) {
			Rectangle2D wb = ((PWord) flippedObjects.get(pData.words[w])).bounds;
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println("   - word" + (((flippedObjects != null) && flippedObjects.containsKey(pData.words[w])) ? " (flipped)" : "") + " '" + pData.words[w].str + "' at " + wb);
			
			//	convert bounds, as PDF Y coordinate is bottom-up, whereas Java, JavaScript, etc. Y coordinate is top-down
			int left = Math.round(((float) (wb.getMinX() - pageBounds.getMinX())) * magnification);
			int right = Math.round(((float) (wb.getMaxX() - pageBounds.getMinX()))  * magnification);
			int top = Math.round((pageBounds.height - ((float) (wb.getMaxY() - ((2 * pageBounds.getMinY()) - pageBounds.getMaxY())))) * magnification);
			int bottom = Math.round((pageBounds.height - ((float) (wb.getMinY() - ((2 * pageBounds.getMinY()) - pageBounds.getMaxY())))) * magnification);
			if (PdfExtractorTest.aimAtPage != -1) {
				System.out.println("     --> " + left + "-" + right + " x " + top + "-" + bottom);
				System.out.println("         " + (left - pageLeft) + "/" + (pageRight - right) + " x " + (top - pageTop) + "/" + (pageBottom - bottom));
			}
			//	adjust minimum
			minPageMargin = Math.min(minPageMargin, (left - pageLeft));
			minPageMargin = Math.min(minPageMargin, (pageRight - right));
			minPageMargin = Math.min(minPageMargin, (top - pageTop));
			minPageMargin = Math.min(minPageMargin, (pageBottom - bottom));
		}
		for (int f = 0; f < pData.figures.length; f++) {
			Rectangle2D fb = ((PFigure) flippedObjects.get(pData.figures[f])).bounds;
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println("   - figure" + (((flippedObjects != null) && flippedObjects.containsKey(pData.figures[f])) ? " (flipped)" : "") + " at " + fb);
			
			//	convert bounds, as PDF Y coordinate is bottom-up, whereas Java, JavaScript, etc. Y coordinate is top-down
			int left = Math.round(((float) (fb.getMinX() - pageBounds.getMinX())) * magnification);
			int right = Math.round(((float) (fb.getMaxX() - pageBounds.getMinX()))  * magnification);
			int top = Math.round((pageBounds.height - ((float) (fb.getMaxY() - ((2 * pageBounds.getMinY()) - pageBounds.getMaxY())))) * magnification);
			int bottom = Math.round((pageBounds.height - ((float) (fb.getMinY() - ((2 * pageBounds.getMinY()) - pageBounds.getMaxY())))) * magnification);
			if (PdfExtractorTest.aimAtPage != -1) {
				System.out.println("     --> " + left + "-" + right + " x " + top + "-" + bottom);
				System.out.println("         " + (left - pageLeft) + "/" + (pageRight - right) + " x " + (top - pageTop) + "/" + (pageBottom - bottom));
			}
			
			//	adjust minimum
			minPageMargin = Math.min(minPageMargin, (left - pageLeft));
			minPageMargin = Math.min(minPageMargin, (pageRight - right));
			minPageMargin = Math.min(minPageMargin, (top - pageTop));
			minPageMargin = Math.min(minPageMargin, (pageBottom - bottom));
		}
		if (PdfExtractorTest.aimAtPage != -1) {
			System.out.println(" - page is " + pageLeft + "-" + pageRight + " x " + pageTop + "-" + pageBottom);
			System.out.println(" - min margin is " + minPageMargin);
		}
		
		//	compute center point of to-flip block and right shift in IM space
		int fbCenterX = ((flipContentBox.left + flipContentBox.right) / 2);
		int fbCenterY = ((flipContentBox.top + flipContentBox.bottom) / 2);
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println(" - flip block center is " + fbCenterX + "," + fbCenterY);
		int fbFlippedLeft = (fbCenterX - (flipContentBox.getHeight() / 2));
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println(" - flipped block left will be " + fbFlippedLeft);
		int fbRightShift = Math.max(0, (minPageMargin - fbFlippedLeft));
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println(" --> right shift is " + fbRightShift);
		
		//	create new page image
		int fbPageWidth = Math.max(pageBox.getWidth(), (flipContentBox.getHeight() + (2 * minPageMargin)));
		BoundingBox fbPageBox = new BoundingBox(0, fbPageWidth, 0, pageBox.getHeight());
		return fbPageBox;
	}
	
	private static void flipPageContent(PPageData pData, HashSet flipWords, HashSet flipFigures, HashSet flipPaths, Map flippedObjects, int flipContentFontDirection) {
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println("Flipping block words ...");
		
		//	compute bounds of to-flip block and minimum page margin in PDF space (have to invert Y axis for transformation, as transformation works in Y coordinates increasing top-down)
		double pFbLeft = Integer.MAX_VALUE;
		double pFbRight = Integer.MIN_VALUE;
		double pFbTop = Integer.MAX_VALUE;
		double pFbBottom = Integer.MIN_VALUE;
		double pPageLeft = pData.pdfPageContentBox.getMinX();
		double pPageRight = pData.pdfPageContentBox.getMaxX();
		double pPageTop = 0;
		double pPageBottom = pData.pdfPageContentBox.height;
		double pMinPageMargin = Integer.MAX_VALUE;
		for (int w = 0; w < pData.words.length; w++) {
			Rectangle2D wb = pData.words[w].bounds;
			
			if (flipWords.contains(pData.words[w])) {
				pFbLeft = Math.min(pFbLeft, wb.getMinX());
				pFbRight = Math.max(pFbRight, wb.getMaxX());
				pFbTop = Math.min(pFbTop, (pData.pdfPageContentBox.height - wb.getMaxY()));
				pFbBottom = Math.max(pFbBottom, (pData.pdfPageContentBox.height - wb.getMinY()));
			}
			
			pMinPageMargin = Math.min(pMinPageMargin, (wb.getMinX() - pPageLeft));
			pMinPageMargin = Math.min(pMinPageMargin, (pPageRight - wb.getMaxX()));
			pMinPageMargin = Math.min(pMinPageMargin, ((pData.pdfPageContentBox.height - wb.getMaxY())) - pPageTop);
			pMinPageMargin = Math.min(pMinPageMargin, (pPageBottom - (pData.pdfPageContentBox.height - wb.getMinY())));
		}
		for (int f = 0; f < pData.figures.length; f++) {
			Rectangle2D fb = pData.figures[f].bounds;
			
			if (flipFigures.contains(pData.figures[f])) {
				pFbLeft = Math.min(pFbLeft, fb.getMinX());
				pFbRight = Math.max(pFbRight, fb.getMaxX());
				pFbTop = Math.min(pFbTop, (pData.pdfPageContentBox.height - fb.getMaxY()));
				pFbBottom = Math.max(pFbBottom, (pData.pdfPageContentBox.height - fb.getMinY()));
			}
			
			pMinPageMargin = Math.min(pMinPageMargin, (fb.getMinX() - pPageLeft));
			pMinPageMargin = Math.min(pMinPageMargin, (pPageRight - fb.getMaxX()));
			pMinPageMargin = Math.min(pMinPageMargin, ((pData.pdfPageContentBox.height - fb.getMaxY())) - pPageTop);
			pMinPageMargin = Math.min(pMinPageMargin, (pPageBottom - (pData.pdfPageContentBox.height - fb.getMinY())));
		}
		if (PdfExtractorTest.aimAtPage != -1) {
			System.out.println(" - adjusted PDF page is " + pPageLeft + "-" + pPageRight + " x " + pPageTop + "-" + pPageBottom);
			System.out.println(" - to-flip block is " + pFbLeft + "-" + pFbRight + " x " + pFbTop + "-" + pFbBottom);
			System.out.println(" - min margin is " + pMinPageMargin);
		}
		
		//	compute center point of to-flip block and right shift in PDF space (have to invert Y axis for transformation, as transformation works in Y coordinates increasing top-down)
		double pFbCenterX = ((pFbLeft + pFbRight) / 2);
		double pFbCenterY = ((pFbTop + pFbBottom) / 2);
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println(" - flip block center is " + pFbCenterX + "," + pFbCenterY);
		double pFbFlippedLeft = (pFbCenterX - ((pFbBottom - pFbTop) / 2));
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println(" - flipped block left will be " + pFbFlippedLeft);
		double pFbRightShift = Math.max(0, (pMinPageMargin - pFbFlippedLeft));
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println(" --> right shift is " + pFbRightShift);
		
		//	create PDF space transformation for flipping words (have to invert Y axis for transformation, as transformation works in Y coordinates increasing top-down)
		AffineTransform pAt = new AffineTransform();
		pAt.translate((pFbCenterX - ((pFbBottom - pFbTop) / 2)), (pFbCenterY - ((pFbRight - pFbLeft) / 2))); // shift to top-left corner of flipped block
		pAt.translate(pFbRightShift, 0); // add right shift to keep flipped block out of page margin
		if (flipContentFontDirection == PWord.BOTTOM_UP_FONT_DIRECTION)
			pAt.translate((pFbBottom - pFbTop), 0); // compensate for top-left corner of to-flip block being top-right in flipped block
		else if (flipContentFontDirection == PWord.TOP_DOWN_FONT_DIRECTION)
			pAt.translate(0, (pFbRight - pFbLeft)); // compensate for top-left corner of to-flip block being bottom-left in flipped block
		pAt.rotate(((Math.PI / 2) * ((flipContentFontDirection == PWord.BOTTOM_UP_FONT_DIRECTION) ? 1 : -1)));
		
		//	flip words (have to invert Y axis for transformation, as transformation works in Y coordinates increasing top-down)
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println(" - flipping words:");
		for (int w = 0; w < pData.words.length; w++) {
			if (!flipWords.contains(pData.words[w]))
				continue;
			Rectangle2D wb = pData.words[w].bounds;
			Point2D wbp = new Point2D.Float(((float) (wb.getMinX() - pFbLeft)), ((float) ((pData.pdfPageContentBox.height - wb.getMaxY()) - pFbTop))); // this one has to be relative to to-flip block to work the same way as the image
			Point2D fWbp = pAt.transform(wbp, null);
			Rectangle2D.Float fWb;
			if (flipContentFontDirection == PWord.BOTTOM_UP_FONT_DIRECTION)
				fWb = new Rectangle2D.Float(
						((float) (fWbp.getX() - wb.getHeight())),
						((float) ((pData.pdfPageContentBox.height - fWbp.getY()) - wb.getWidth())),
						((float) wb.getHeight()),
						((float) wb.getWidth())
					);
			else if (flipContentFontDirection == PWord.TOP_DOWN_FONT_DIRECTION)
				fWb = new Rectangle2D.Float(
						((float) fWbp.getX()),
						((float) ((pData.pdfPageContentBox.height - fWbp.getY()))),
						((float) wb.getHeight()),
						((float) wb.getWidth())
					);
			else continue;
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println("   - '" + pData.words[w] + "' flipped from " + wb + " to " + fWb);
			PWord pWord = new PWord(pData.words[w].renderOrderNumber, pData.words[w].charCodes, pData.words[w].str, fWb, pData.words[w].color, pData.words[w].fontSize, PWord.LEFT_RIGHT_FONT_DIRECTION, pData.words[w].font);
			pWord.joinWithNext = pData.words[w].joinWithNext;
			flippedObjects.put(pWord, pData.words[w]);
			pData.words[w] = pWord;
		}
		
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println(" - flipping figures:");
		for (int f = 0; f < pData.figures.length; f++) {
			if (!flipFigures.contains(pData.figures[f]))
				continue;
			Rectangle2D fb = pData.figures[f].bounds;
			Point2D fbp = new Point2D.Float(((float) (fb.getMinX() - pFbLeft)), ((float) ((pData.pdfPageContentBox.height - fb.getMaxY()) - pFbTop))); // this one has to be relative to to-flip block to work the same way as the image
			Point2D fFbp = pAt.transform(fbp, null);
			Rectangle2D.Float fFb;
			if (flipContentFontDirection == PWord.BOTTOM_UP_FONT_DIRECTION)
				fFb = new Rectangle2D.Float(
						((float) (fFbp.getX() - fb.getHeight())),
						((float) ((pData.pdfPageContentBox.height - fFbp.getY()) - fb.getWidth())),
						((float) fb.getHeight()),
						((float) fb.getWidth())
					);
			else if (flipContentFontDirection == PWord.TOP_DOWN_FONT_DIRECTION)
				fFb = new Rectangle2D.Float(
						((float) fFbp.getX()),
						((float) ((pData.pdfPageContentBox.height - fFbp.getY()))),
						((float) fb.getHeight()),
						((float) fb.getWidth())
					);
			else continue;
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println("   - '" + pData.figures[f] + "' flipped from " + fb + " to " + fFb);
			PFigure pFigure = new PFigure(pData.figures[f].renderOrderNumber, pData.figures[f].name, fFb, pData.figures[f].refOrData, (pData.figures[f].rotation + ((flipContentFontDirection == PWord.BOTTOM_UP_FONT_DIRECTION) ? (Math.PI / 2) : -(Math.PI / 2))), pData.figures[f].upsideDown, pData.figures[f].rightSideLeft);
			flippedObjects.put(pFigure, pData.figures[f]);
			pData.figures[f] = pFigure;
		}
		
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println(" - flipping paths:");
		for (int p = 0; p < pData.paths.length; p++) {
			if (!flipPaths.contains(pData.paths[p]))
				continue;
			Rectangle2D pb = pData.paths[p].getBounds();
			Point2D pbp = new Point2D.Float(((float) (pb.getMinX() - pFbLeft)), ((float) ((pData.pdfPageContentBox.height - pb.getMaxY()) - pFbTop))); // this one has to be relative to to-flip block to work the same way as the image
			Point2D fPbp = pAt.transform(pbp, null);
			Rectangle2D.Float fPb;
			if (flipContentFontDirection == PWord.BOTTOM_UP_FONT_DIRECTION)
				fPb = new Rectangle2D.Float(
						((float) (fPbp.getX() - pb.getHeight())),
						((float) ((pData.pdfPageContentBox.height - fPbp.getY()) - pb.getWidth())),
						((float) pb.getHeight()),
						((float) pb.getWidth())
					);
			else if (flipContentFontDirection == PWord.TOP_DOWN_FONT_DIRECTION)
				fPb = new Rectangle2D.Float(
						((float) fPbp.getX()),
						((float) ((pData.pdfPageContentBox.height - fPbp.getY()))),
						((float) pb.getHeight()),
						((float) pb.getWidth())
					);
			else continue;
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println("   - '" + pData.paths[p] + "' flipped from " + pb + " to " + fPb);
			PPath pPath = new PPath(pData.paths[p].renderOrderNumber);
//			sub paths do this
//			pPath.addPoint(((float) fPb.getMinX()), ((float) fPb.getMinY()));
//			pPath.addPoint(((float) fPb.getMaxX()), ((float) fPb.getMaxY()));
			pPath.strokeColor = pData.paths[p].strokeColor;
			pPath.lineWidth = pData.paths[p].lineWidth;
			pPath.lineCapStyle = pData.paths[p].lineCapStyle;
			pPath.lineJointStyle = pData.paths[p].lineJointStyle;
			pPath.miterLimit = pData.paths[p].miterLimit;
			pPath.dashPattern = pData.paths[p].dashPattern;
			pPath.dashPatternPhase = pData.paths[p].dashPatternPhase;
			pPath.fillColor = pData.paths[p].fillColor;
			pPath.blendMode = pData.paths[p].blendMode;
			PSubPath[] pSubPaths = pData.paths[p].getSubPaths();
			for (int s = 0; s < pSubPaths.length; s++) {
				Point2D.Float fSpp = flipPoint(pSubPaths[s].startX, pSubPaths[s].startY, pData.pdfPageContentBox, pAt, pFbLeft, pFbTop);
				PSubPath pSubPath = new PSubPath(pPath, fSpp.x, fSpp.y);
				Shape[] shapes = pSubPaths[s].getShapes();
				for (int h = 0; h < shapes.length; h++) {
					if (shapes[h] instanceof Line2D) {
						Line2D line = ((Line2D) shapes[h]);
						Point2D.Float fLe = flipPoint(((float) line.getX2()), ((float) line.getY2()), pData.pdfPageContentBox, pAt, pFbLeft, pFbTop);
						pSubPath.lineTo(fLe.x, fLe.y);
					}
					else if (shapes[h] instanceof CubicCurve2D) {
						CubicCurve2D curve = ((CubicCurve2D) shapes[h]);
						Point2D.Float fC1 = flipPoint(((float) curve.getCtrlX1()), ((float) curve.getCtrlY1()), pData.pdfPageContentBox, pAt, pFbLeft, pFbTop);
						Point2D.Float fC2 = flipPoint(((float) curve.getCtrlX2()), ((float) curve.getCtrlY2()), pData.pdfPageContentBox, pAt, pFbLeft, pFbTop);
						Point2D.Float fCe = flipPoint(((float) curve.getX2()), ((float) curve.getY2()), pData.pdfPageContentBox, pAt, pFbLeft, pFbTop);
						pSubPath.curveTo(fC1.x, fC1.y, fC2.x, fC2.y, fCe.x, fCe.y);
					}
				}
			}
			flippedObjects.put(pPath, pData.paths[p]);
			pData.paths[p] = pPath;
		}
	}
	
	private static Point2D.Float flipPoint(float x, float y, Rectangle2D pgb, AffineTransform pAt, double pFbLeft, double pFbTop) {
		Point2D p = new Point2D.Float(((float) (x - pFbLeft)), ((float) ((pgb.getHeight() - y) - pFbTop))); // this one has to be relative to to-flip block to work the same way as the image
		Point2D fp = pAt.transform(p, null);
		return new Point2D.Float((float) (fp.getX()), ((float) ((pgb.getHeight() - fp.getY()))));
	}
	
	private static PWord shrinkWordBounds(Graphics2D mg, PWord pWord) {
		Rectangle2D wb = pWord.bounds;
		if (PdfExtractorTest.aimAtPage != -1)
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
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" - word rendering box is " + wtl.getBounds());
			float boundingBoxY = ((float) wtl.getBounds().getY());
			float boundingBoxHeight = ((float) wtl.getBounds().getHeight());
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" - top is " + top + ", bottom is " + bottom + ", baseline is " + baseline + ", ascent is " + wtl.getAscent() + ", descent is " + wtl.getDescent());
			
			//	if computed bounding box smaller (lower top, higher bottom) than one from PDF, shrink it
			float adjustedTop = (baseline - boundingBoxY);
			float adjustedBottom = (adjustedTop - boundingBoxHeight);
			if (PdfExtractorTest.aimAtPage != -1) {
				System.out.println(" - word box y is " + boundingBoxY + ", word box height is " + boundingBoxHeight);
				System.out.println(" - adjusted top is " + adjustedTop + ", adjusted bottom is " + adjustedBottom);
			}
			if (((top - adjustedTop) > 0.25) || ((adjustedBottom - bottom) > 0.25)) {
				Rectangle2D.Float pwBox = new Rectangle2D.Float(left, adjustedBottom, (right - left), (adjustedTop - adjustedBottom));
				PWord spWord = new PWord(pWord.renderOrderNumber, pWord.charCodes, pWord.str, pwBox, pWord.color, pWord.fontSize, pWord.fontDirection, pWord.font);
				spWord.joinWithNext = pWord.joinWithNext;
				pWord = spWord;
				if (PdfExtractorTest.aimAtPage != -1)
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
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" - word rendering box is " + wtl.getBounds());
			float boundingBoxX = -((float) wtl.getBounds().getY());
			float boundingBoxHeight = ((float) wtl.getBounds().getHeight());
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" - top is " + top + ", bottom is " + bottom + ", baseline is " + baseline + ", ascent is " + wtl.getAscent() + ", descent is " + wtl.getDescent());
			
			//	if computed bounding box smaller (lower top, higher bottom) than one from PDF, shrink it
			float adjustedTop = (baseline - boundingBoxX);
			float adjustedBottom = (adjustedTop + boundingBoxHeight);
			if (PdfExtractorTest.aimAtPage != -1) {
				System.out.println(" - word box y is " + boundingBoxX + ", word box height is " + boundingBoxHeight);
				System.out.println(" - adjusted top is " + adjustedTop + ", adjusted bottom is " + adjustedBottom);
			}
			if (((adjustedTop - top) > 0.25) || ((bottom - adjustedBottom) > 0.25)) {
				Rectangle2D.Float pwBox = new Rectangle2D.Float(adjustedTop, left, (adjustedBottom - adjustedTop), (right - left));
				PWord spWord = new PWord(pWord.renderOrderNumber, pWord.charCodes, pWord.str, pwBox, pWord.color, pWord.fontSize, pWord.fontDirection, pWord.font);
				spWord.joinWithNext = pWord.joinWithNext;
				pWord = spWord;
				if (PdfExtractorTest.aimAtPage != -1)
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
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" - word rendering box is " + wtl.getBounds());
			float boundingBoxX = ((float) wtl.getBounds().getY());
			float boundingBoxHeight = ((float) wtl.getBounds().getHeight());
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" - top is " + top + ", bottom is " + bottom + ", baseline is " + baseline + ", ascent is " + wtl.getAscent() + ", descent is " + wtl.getDescent());
			
			//	if computed bounding box smaller (lower top, higher bottom) than one from PDF, shrink it
			float adjustedTop = (baseline - boundingBoxX);
			float adjustedBottom = (adjustedTop - boundingBoxHeight);
			if (PdfExtractorTest.aimAtPage != -1) {
				System.out.println(" - word box y is " + boundingBoxX + ", word box height is " + boundingBoxHeight);
				System.out.println(" - adjusted top is " + adjustedTop + ", adjusted bottom is " + adjustedBottom);
			}
			if (((top - adjustedTop) > 0.25) || ((adjustedBottom - bottom) > 0.25)) {
				Rectangle2D.Float pwBox = new Rectangle2D.Float(adjustedBottom, right, (adjustedTop - adjustedBottom), (left - right));
				PWord spWord = new PWord(pWord.renderOrderNumber, pWord.charCodes, pWord.str, pwBox, pWord.color, pWord.fontSize, pWord.fontDirection, pWord.font);
				spWord.joinWithNext = pWord.joinWithNext;
				pWord = spWord;
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println(" ==> adjusted bounding box to " + pWord.bounds);
			}
		}
		
		//	finally ...
		return pWord;
	}
	
	private static Font serifFont = new Font((USE_FREE_FONTS ? "FreeSerif" : "Serif"), Font.PLAIN, 1);
	private static Font sansFont = new Font((USE_FREE_FONTS ? "FreeSans" : "SansSerif"), Font.PLAIN, 1);
//	private static Font monoFont = new Font((USE_FREE_FONTS ? "FreeMono" : "Monospaced"), Font.PLAIN, 1);
	
	private static Map fontCache = Collections.synchronizedMap(new HashMap(5));
	private static Font getFont(String name, int style, boolean serif, int size) {
		String fontKey = (style + " " + (serif ? "serif" : "sans") + " " + size);
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println("Getting font " + fontKey);
		Font font = ((Font) fontCache.get(fontKey));
		if (font != null) {
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println("==> cache hit");
			return font;
		}
		font = (serif ? serifFont : sansFont).deriveFont(style, size);
		fontCache.put(fontKey, font);
		return font;
		
//		String fontKey = (name + " " + style + " " + (serif ? "serif" : "sans") + " " + size);
//		System.out.println("Getting font " + fontKey);
//		Font font = ((Font) fontCache.get(fontKey));
//		if (font != null) {
//			System.out.println("==> cache hit");
//			return font;
//		}
//		
//		if (name != null) {
//			if (name.matches("[A-Z]+\\+[A-Z][a-z].*"))
//				name = name.substring(name.indexOf('+') + "+".length());
//			if (name.startsWith("Symbol"))
//				font = new Font("Symbol", style, size);
//			else if (name.startsWith("ZapfDingbats"))
//				font = new Font("ZapfDingbats", style, size);
//			
//			if (name.indexOf('-') != -1)
//				name = name.substring(0, name.indexOf('-'));
//			String ffn = PdfFont.getFallbackFontName(name, false);
//			System.out.println("==> falling back to " + ffn);
//			if (ffn.startsWith("Helvetica"))
//				font = new Font((USE_FREE_FONTS ? "FreeSans" : "SansSerif"), style, size);
//			else if (ffn.startsWith("Times"))
//				font = new Font((USE_FREE_FONTS ? "FreeSerif" : "Serif"), style, size);
//			else if (ffn.startsWith("Courier"))
//				font = new Font((USE_FREE_FONTS ? "FreeMono" : "Monospaced"), style, size);
//		}
//		
//		if (font == null) {
//			System.out.println("==> base font not found, using Serif fallback");
//			return new Font((serif ? (USE_FREE_FONTS ? "FreeSerif" : "Serif") : (USE_FREE_FONTS ? "FreeSans" : "SansSerif")), style, size);
//		}
//		fontCache.put(fontKey, font);
//		System.out.println("==> font created");
//		return font;
	}
	
	private ImWord addRegionStructure(ImPage page, ImWord lastWord, Region theRegion, Map wordsByBoxes, Set pageStructImageRegions, ProgressMonitor pm) {
		
		//	mark region
		ImRegion region = new ImRegion(page, theRegion.getBoundingBox());
		
		//	image block ==> set type and we're done
		if (theRegion.isImage()) {
			region.setType(ImRegion.IMAGE_TYPE);
			pageStructImageRegions.add(region);
			return lastWord;
		}
		
		//	set type
		region.setType((theRegion.isColumn() && theRegion.areChildrenAtomic()) ? COLUMN_ANNOTATION_TYPE : REGION_ANNOTATION_TYPE);
		
		//	atomic region ==> block, do further analysis
		if (theRegion.isAtomic()) {
			
			//	analyze block structure
			PageImageAnalysis.getBlockStructure(theRegion, page.getImageDPI(), ((BoundingBox[]) wordsByBoxes.keySet().toArray(new BoundingBox[wordsByBoxes.size()])), pm);
			
			//	get block bounds
			Block theBlock = theRegion.getBlock();
			
			//	mark block
			ImRegion block = new ImRegion(page, theBlock.getBoundingBox());
			block.setType(theBlock.isTable() ? TABLE_ANNOTATION_TYPE : BLOCK_ANNOTATION_TYPE);
			
			//	append block content
			return this.addTextBlockStructure(page, lastWord, theBlock, wordsByBoxes, pm);
		}
		
		//	non-atomic region ==> recurse to child regions
		for (int s = 0; s < theRegion.getSubRegionCount(); s++)
			lastWord = this.addRegionStructure(page, lastWord, theRegion.getSubRegion(s), wordsByBoxes, pageStructImageRegions, pm);
		
		//	finally ...
		return lastWord;
	}
	
	private ImWord addTextBlockStructure(ImPage page, ImWord lastWord, Block theBlock, Map pWordsByBoxes, ProgressMonitor pm) {
		
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
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println("Analyzing text stream structure in page " + page.pageId);
		
		//	get paragraphs
		ImRegion[] paragraphs = page.getRegions(MutableAnnotation.PARAGRAPH_TYPE);
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println(" - got " + paragraphs.length + " paragraphs");
		if (paragraphs.length == 0)
			return lastWord;
		
		//	get main text stream head (can only be one here, theoretically, but artifacts _can_ wreck havoc)
		ImWord[] textStreamHeads = page.getTextStreamHeads();
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println(" - got " + textStreamHeads.length + " text stream heads");
		if (textStreamHeads.length == 0)
			return lastWord;
		ImWord textStreamHead = textStreamHeads[0];
		
		//	if we have multiple text stream heads, find and use the longest / largest one
		if (textStreamHeads.length > 1) {
			int maxTextStreamArea = 0;
			for (int h = 0; h < textStreamHeads.length; h++) {
				
				//	skip over words that are figure or graphics labels
				if (ImWord.TEXT_STREAM_TYPE_LABEL.equals(textStreamHeads[h].getTextStreamType()))
					continue;
				
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
		if (PdfExtractorTest.aimAtPage != -1)
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
	
	/** control flag switching on or off use of embedded OCR in scanned PDF decoding */
	public static final int USE_EMBEDDED_OCR = 0x1;
	
	/** control flag switching on or off skipping over leading or tailing born-digital meta pages in scanned PDF decoding */
	public static final int META_PAGES = 0x2;
	
	/** control flag indicating scan images with single pages in them in scanned PDF decoding */
	public static final int SINGLE_PAGE_SCANS = 0x4;
	
	/** control flag indicating scan images with double pages in them in scanned PDF decoding */
	public static final int DOUBLE_PAGE_SCANS = 0x8;
	
	/** control flag switching on or off heuristical scan enhancement in scanned PDF decoding */
	public static final int ENHANCE_SCANS = 0x10;
	
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
	 * returned image markup document, including word bounding boxes.
	 * @param doc the document to store the structural information in
	 * @param pdfDoc the PDF document to parse
	 * @param pdfBytes the raw binary data the the PDF document was parsed from
	 * @param flags control flags for decoding behavior
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the image markup document
	 * @throws IOException
	 */
	public ImDocument loadImagePdf(byte[] pdfBytes, int flags, ProgressMonitor pm) throws IOException {
		return this.loadImagePdf(null, null, pdfBytes, flags, 1, pm);
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
		int flags = 0;
		if (metaPages)
			flags = (flags | META_PAGES);
		if (useEmbeddedOCR)
			flags = (flags | USE_EMBEDDED_OCR);
		flags = (flags | ENHANCE_SCANS);
		return this.loadImagePdf(doc, pdfDoc, pdfBytes, flags, scaleFactor, pm);
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
	 * @param flags control flags for decoding behavior
	 * @param scaleFactor the scale factor for image PDFs, to correct PDFs that
	 *            are set too small (DPI number is divided by this factor)
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the argument image markup document
	 * @throws IOException
	 */
	public ImDocument loadImagePdf(ImDocument doc, Document pdfDoc, byte[] pdfBytes, int flags, int scaleFactor, ProgressMonitor pm) throws IOException {
		
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
		
		//	decode flags
		System.out.println("Flags are " + flags + " (" + Integer.toString(flags, 2) + ")");
		boolean useEmbeddedOCR = ((flags & USE_EMBEDDED_OCR) != 0);
		System.out.println(" - use embedded OCR is " + useEmbeddedOCR);
		boolean metaPages = ((flags & META_PAGES) != 0);
		System.out.println(" - meta pages is " + metaPages);
		boolean singlePages = ((flags & SINGLE_PAGE_SCANS) != 0);
		System.out.println(" - single page scans is " + singlePages);
		boolean doublePages = ((flags & DOUBLE_PAGE_SCANS) != 0);
		System.out.println(" - double page scans is " + doublePages);
		boolean enhanceScans = ((flags & ENHANCE_SCANS) != 0);
		System.out.println(" - enhance scans is " + enhanceScans);
		
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
		PPageData[] pData = this.getPdfPageData(doc, (getFigures | (useEmbeddedOCR ? getWords : 0)), PdfFontDecoder.NO_DECODING, pageTree, objects, spm);
		
		//	add page content
		this.addImagePdfPages(doc, pData, pdfDoc, pageTree, objects, metaPages, singlePages, doublePages, useEmbeddedOCR, scaleFactor, enhanceScans, spm);
		
		//	finally ...
		return doc;
	}
	
	private PPageData[] addImagePdfPages(ImDocument doc, PPageData[] pData, Document pdfDoc, PageTree pageTree, Map objects, boolean metaPages, boolean singlePages, boolean doublePages, boolean useEmbeddedOCR, int scaleFactor, boolean enhanceScans, SynchronizedProgressMonitor spm) throws IOException {
		
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
		pData = this.addImagePdfPages(doc, pData, pdfDoc, pageTree, objects, metaPages, singlePages, doublePages, scaleFactor, enhanceScans, spm, pageImageCache);
		
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
		WordImageAnalysis.analyzeFontMetrics(doc, this.useMultipleCores, new CascadingProgressMonitor(spm));
		
		//	finally ...
		return pData;
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
					
					//	get minimum column width to prevent column splits resulting in too narrow columns
					int minColumnWidth = docLayout.getIntProperty("minColumnWidth", -1, pi.currentDpi);
					
					//	compute page structure
					AnalysisImage api = Imaging.wrapImage(pi.image, null);
					pageRootRegion = PageImageAnalysis.getPageRegion(api, pi.currentDpi, minColumnMargin, minBlockMargin, columnAreas, minColumnWidth, false, spm);
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
			return this.addTextBlockStructure(page, lastWord, theBlock, wordsByBoxes, pm);
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
		int flags = 0;
		if (metaPages)
			flags = (flags | META_PAGES);
		if (useEmbeddedOCR)
			flags = (flags | USE_EMBEDDED_OCR);
		flags = (flags | ENHANCE_SCANS);
		return this.loadImagePdfBlocks(doc, pdfDoc, pdfBytes, flags, scaleFactor, pm);
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
	 * @param flags control flags for decoding behavior
	 * @param scaleFactor the scale factor for image PDFs, to correct PDFs that
	 *            are set too small (DPI number is divided by this factor)
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the argument image markup document
	 * @throws IOException
	 */
	public ImDocument loadImagePdfBlocks(ImDocument doc, Document pdfDoc, byte[] pdfBytes, int flags, int scaleFactor, ProgressMonitor pm) throws IOException {
		
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
		
		//	decode flags
		boolean useEmbeddedOCR = ((flags & USE_EMBEDDED_OCR) != 0);
		boolean metaPages = ((flags & META_PAGES) != 0);
		boolean singlePages = ((flags & SINGLE_PAGE_SCANS) != 0);
		boolean doublePages = ((flags & DOUBLE_PAGE_SCANS) != 0);
		boolean enhanceScans = ((flags & ENHANCE_SCANS) != 0);
		
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
		PPageData[] pData = this.getPdfPageData(doc, (getFigures | (useEmbeddedOCR ? getWords : 0)), PdfFontDecoder.NO_DECODING, pageTree, objects, spm);
		
		//	load pages
		pm.setStep("Loading document page images");
		pm.setBaseProgress(0);
		pm.setProgress(0);
		pm.setMaxProgress(40);
		pData = this.addImagePdfPages(doc, pData, pdfDoc, pageTree, objects, metaPages, singlePages, doublePages, scaleFactor, enhanceScans, spm, pageImageCache);
		
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
			
			/* no need for word merging here, as good OCR keeps words together
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
					splitPageWords(pData[p], null, null, null, tokenizer, numberTokenizer, spm);
					
					//	give status update
					if (pData[p].words.length == 0)
						cpm.setInfo(" --> empty page");
					else cpm.setInfo(" --> got " + pData[p].words.length + " words in PDF");
				}
			};
			ParallelJobRunner.runParallelFor(pf, ((PdfExtractorTest.aimAtPage < 0) ? 0 : PdfExtractorTest.aimAtPage), ((PdfExtractorTest.aimAtPage < 0) ? pData.length : (PdfExtractorTest.aimAtPage + 1)), (this.useMultipleCores ? -1 : 1));
			cpm.setProgress(100);
			
			//	did anything go wrong?
			this.checkException(pf);
			
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
				
				//	get minimum column width to prevent column splits resulting in too narrow columns
				int minColumnWidth = docLayout.getIntProperty("minColumnWidth", -1, pi.currentDpi);
				
				//	add embedded OCR if (a) asked to do so and (b) available
				//	TODO make this work with split-up double pages
				if ((pData != null) && (pData[pages[p].pageId] != null) && (pData[pages[p].pageId].words != null) && (pData[pages[p].pageId].words.length != 0)) {
					
					//	compute word scale factor
					float magnification = (((pData[pages[p].pageId].rawPageImageDpi < 0) ? ((float) pi.originalDpi) : pData[pages[p].pageId].rawPageImageDpi) / defaultDpi);
					
					//	add words to page
					spm.setInfo("Adding embedded OCR words to page " + pages[p].pageId + " of " + pData.length);
					ArrayList pWords = new ArrayList();
					for (int w = 0; w < pData[pages[p].pageId].words.length; w++) {
						
						//	make sure word has some minimum width
						BoundingBox wb = getBoundingBox(pData[pages[p].pageId].words[w].bounds, pData[pages[p].pageId].pdfPageBox, magnification, pData[pages[p].pageId].rotate);
						if ((wb.right - wb.left) < 4)
							wb = new BoundingBox(wb.left, (wb.left + 4), wb.top, wb.bottom);
						
						//	if we're on the right half of a double page, shift words to left
						if (((p % 2) == 1) && (pData[pages[p].pageId].rightPageOffset != 0))
							wb = new BoundingBox((wb.left - pData[pages[p].pageId].rightPageOffset), (wb.right - pData[pages[p].pageId].rightPageOffset), wb.top, wb.bottom);
						
						//	TODO shrink word bounds to actual word, so OCR overlay rendering stays in bounds
						
						//	add word to page
						ImWord word = new ImWord(pages[p], wb, pData[pages[p].pageId].words[w].str);
						spm.setInfo(" - " + word.getString() + " @ " + word.bounds.toString() + " / " + pData[pages[p].pageId].words[w].bounds);
						pWords.add(word);
						
						//	set layout attributes (no need for font tracking in embedded OCR, should rarely use custom glyphs, let alone obfuscation)
//						if ((pData[pages[p].pageId].words[w].font != null) && (pData[pages[p].pageId].words[w].font.name != null))
//							word.setAttribute(FONT_NAME_ATTRIBUTE, pData[pages[p].pageId].words[w].font.name);
						if (pData[pages[p].pageId].words[w].fontSize != -1)
							word.setAttribute(FONT_SIZE_ATTRIBUTE, ("" + pData[pages[p].pageId].words[w].fontSize));
						if (pData[pages[p].pageId].words[w].bold)
							word.setAttribute(BOLD_ATTRIBUTE);
						if (pData[pages[p].pageId].words[w].italics)
							word.setAttribute(ITALICS_ATTRIBUTE);
						
						//	TODO add baseline attribute, so OCR overlay rendering stays in bounds
					}
					
					//	wrap page image for analysis
					AnalysisImage api = Imaging.wrapImage(pi.image, null);
					
					//	draw embedded words on page image (or into brightness array) to make sure they are observed in blocking
					byte[][] apiBrightness = api.getBrightness();
					for (int w = 0; w < pWords.size(); w++) {
						ImWord word = ((ImWord) pWords.get(w));
						for (int c = Math.max(0, word.bounds.left); c < Math.min(apiBrightness.length, word.bounds.right); c++) {
							for (int r = Math.max(0, word.bounds.top); r < Math.min(apiBrightness[c].length, word.bounds.bottom); r++)
								apiBrightness[c][r] = 0;
						}
					}
					
					//	obtain higher level page structure
					Region pageRootRegion = PageImageAnalysis.getPageRegion(api, pi.currentDpi, minColumnMargin, minBlockMargin, columnAreas, minColumnWidth, false, spm);
					addRegionBlocks(pages[p], pageRootRegion, pi.currentDpi, spm);
					if (pageRegionCache != null)
						synchronized (pageRegionCache) {
							pageRegionCache.put(pages[p], pageRootRegion);
						}
				}
				
				//	do OCR if (a) none embedded or (b) asked to do so
				else {
					
					//	obtain higher level page structure TODO use embedded OCR words if available (makes sure structure covers words)
					AnalysisImage api = Imaging.wrapImage(pi.image, null);
					Region pageRootRegion = PageImageAnalysis.getPageRegion(api, pi.currentDpi, minColumnMargin, minBlockMargin, columnAreas, minColumnWidth, false, spm);
					addRegionBlocks(pages[p], pageRootRegion, pi.currentDpi, spm);
					if (pageRegionCache != null)
						synchronized (pageRegionCache) {
							pageRegionCache.put(pages[p], pageRootRegion);
						}
					
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
	 * comparison to the DPI number.
	 * @param pdfBytes the raw binary data the the PDF document was parsed from
	 * @param flags control flags for decoding behavior
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the image markup document
	 * @throws IOException
	 */
	public ImDocument loadImagePdfPages(byte[] pdfBytes, int flags, ProgressMonitor pm) throws IOException {
		return this.loadImagePdfPages(null, null, pdfBytes, flags, 1, pm);
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
		int flags = 0;
		if (metaPages)
			flags = (flags | META_PAGES);
		if (useEmbeddedOCR)
			flags = (flags | USE_EMBEDDED_OCR);
		flags = (flags | ENHANCE_SCANS);
		return this.loadImagePdfPages(doc, pdfDoc, pdfBytes, flags, scaleFactor, pm);
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
	 * @param flags control flags for decoding behavior
	 * @param scaleFactor the scale factor for image PDFs, to correct PDFs that
	 *            are set too small (DPI number is divided by this factor)
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the argument image markup document
	 * @throws IOException
	 */
	public ImDocument loadImagePdfPages(ImDocument doc, Document pdfDoc, byte[] pdfBytes, int flags, int scaleFactor, ProgressMonitor pm) throws IOException {
		
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
		
		//	decode flags
		boolean metaPages = ((flags & META_PAGES) != 0);
		boolean singlePages = ((flags & SINGLE_PAGE_SCANS) != 0);
		boolean doublePages = ((flags & DOUBLE_PAGE_SCANS) != 0);
		boolean enhanceScans = ((flags & ENHANCE_SCANS) != 0);
		
		//	load pages
		pm.setStep("Loading document page images");
		pm.setBaseProgress(0);
		pm.setProgress(0);
		pm.setMaxProgress(100);
		this.addImagePdfPages(doc, pdfDoc, pdfBytes, metaPages, singlePages, doublePages, scaleFactor, enhanceScans, pm, null);
		
		//	preserve source PDF in supplement
		ImSupplement.Source.createSource(doc, "application/pdf", pdfBytes);
		
		//	finally ...
		return doc;
	}
	
	private static class PImagePart {
		final int pageId;
		final String name;
		final Rectangle2D.Float pdfBounds;
		final double pdfRotation;
		final PStream data;
		final float rawDpi;
		final BoundingBox bounds;
		PImagePart(int pageId, String name, Rectangle2D.Float pdfBounds, double pdfRotation, PStream data, float rawDpi, BoundingBox bounds) {
			this.pageId = pageId;
			this.name = name;
			this.pdfBounds = pdfBounds;
			this.pdfRotation = pdfRotation;
			this.data = data;
			this.rawDpi = rawDpi;
			this.bounds = bounds;
		}
	}
	
	private void addImagePdfPages(ImDocument doc, Document pdfDoc, byte[] pdfBytes, boolean metaPages, boolean singlePages, boolean doublePages, int scaleFactor, boolean enhanceScans, ProgressMonitor pm, Map pageImageCache) throws IOException {
		
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
		PPageData[] pData = this.getPdfPageData(doc, getFigures, PdfFontDecoder.NO_DECODING, pageTree, objects, spm);
		
		//	add page images
		pData = this.addImagePdfPages(doc, pData, pdfDoc, pageTree, objects, metaPages, singlePages, doublePages, scaleFactor, enhanceScans, spm, pageImageCache);
	}
	
	private PPageData[] addImagePdfPages(final ImDocument doc, final PPageData[] pData, final Document pdfDoc, final PageTree pageTree, final Map objects, boolean metaPages, boolean singlePages, boolean doublePages, int scaleFactor, boolean enhanceScans, final SynchronizedProgressMonitor spm, Map pageImageCache) throws IOException {
		
		//	extract page objects
		spm.setInfo("Getting page objects");
		final Page[] pdfPages = new Page[pageTree.getNumberOfPages()];
		for (int p = 0; p < pageTree.getNumberOfPages(); p++)
			pdfPages[p] = pageTree.getPage(p, "");
		spm.setInfo(" --> got " + pageTree.getNumberOfPages() + " page objects");
		
		//	prepare working in parallel
		ParallelFor pf;
		
		//	display figures if testing
		final ImageDisplayDialog idd = ((DEBUG_EXTRACT_FIGURES && (PdfExtractorTest.aimAtPage >= 0)) ? new ImageDisplayDialog("Images in Page " + PdfExtractorTest.aimAtPage) : null);
		
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
				
				//	collect all page figures
				pageImageParts[p] = new ArrayList();
				
				//	store figures / figure parts
				for (int f = 0; f < pData[p].figures.length; f++) {
					spm.setInfo("   - " + pData[p].figures[f]);
					this.addPageImagePart(p, pData[p].pdfPageBox, pData[p].rotate, pData[p].figures[f], pageImageParts[p], spm);
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
			
			private void addPageImagePart(int pageId, Rectangle2D.Float pageBounds, int rotate, PFigure pFigure, ArrayList pImageParts, ProgressMonitor pm) throws IOException {
				
				//	get actual image object
				Object pFigureDataObj = PdfParser.dereference(pFigure.refOrData, objects);
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
				spm.setInfo("     - figure bounds are " + pFigure.bounds);
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
				
				//	show image if testing
				if (idd != null) {
					PPageImage rPip = getPageImagePart(new PImagePart(pageId, pFigure.name, pFigure.bounds, pFigure.rotation, pFigureData, pFigureDpi, pFigureBounds), objects, pdfDoc, pdfDoc.getPageTree().getPage(PdfExtractorTest.aimAtPage, ""), spm);
					idd.addImage(rPip.image, pFigure.name);
				}
				
				//	get mask images as well if given (simply recurse)
				if (pFigureData.params.containsKey("Mask")) {
					String pMaskFigureName = (pFigure.name + "_mask");
					pm.setInfo("   - adding mask figure");
					this.addPageImagePart(pageId, pageBounds, rotate, new PFigure(pFigure.renderOrderNumber, pMaskFigureName, pFigure.bounds, pFigureData.params.get("Mask"), pFigure.rotation, pFigure.rightSideLeft, pFigure.upsideDown), pImageParts, pm);
				}
				if (pFigureData.params.containsKey("SMask")) {
					String pMaskFigureName = (pFigure.name + "_smask");
					pm.setInfo("   - adding smask figure");
					this.addPageImagePart(pageId, pageBounds, rotate, new PFigure(pFigure.renderOrderNumber, pMaskFigureName, pFigure.bounds, pFigureData.params.get("SMask"), pFigure.rotation, pFigure.rightSideLeft, pFigure.upsideDown), pImageParts, pm);
				}
			}
		};
		ParallelJobRunner.runParallelFor(pf, ((PdfExtractorTest.aimAtPage < 0) ? 0 : PdfExtractorTest.aimAtPage), ((PdfExtractorTest.aimAtPage < 0) ? pageTree.getNumberOfPages() : (PdfExtractorTest.aimAtPage + 1)), (this.useMultipleCores ? -1 : 1));
		spm.setProgress(100);
		
		//	check errors
		this.checkException(pf);
		
		//	display figures if testing
		if (idd != null) {
			idd.setSize(600, 800);
			idd.setLocationRelativeTo(null);
			idd.setVisible(true);
		}
		
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
		if (singlePages == doublePages)
			spm.setInfo("Checking page resolution");
		else spm.setInfo("Determining page orientation and checking resolution");
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
		if (singlePages == doublePages) {
			if (portraitPageCount < landscapePageCount) {
				singlePages = false;
				doublePages = true;
				spm.setInfo(" ==> pages are landscape, likely double pages");
			}
			else {
				singlePages = true;
				doublePages = false;
				spm.setInfo(" ==> pages are portrait, likely single pages");
			}
		}
		
		if (((overA4widthPageCount * 2) > pdfPageBoxes.length) && ((overA4heightPageCount * 2) > pdfPageBoxes.length)) {
			spm.setInfo("Page boxes seem to be out of range, scaling");
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
		PPageData[] spData;
//		if (portraitPageCount < landscapePageCount)
//			spData = this.addImagePdfPagesDouble(doc, scaleFactor, pdfDoc, pdfPages, pdfPageBoxes, sPdfPageBoxes, pData, pageImageParts, noImagePageIDs, objects, pageImageCache, spm);
//		else spData = this.addImagePdfPagesSingle(doc, scaleFactor, pdfDoc, pdfPages, pdfPageBoxes, sPdfPageBoxes, pData, pageImageParts, noImagePageIDs, objects, pageImageCache, spm);
		if (doublePages)
			spData = this.addImagePdfPagesDouble(doc, scaleFactor, enhanceScans, pdfDoc, pdfPages, pdfPageBoxes, sPdfPageBoxes, pData, pageImageParts, noImagePageIDs, objects, pageImageCache, spm);
		else spData = this.addImagePdfPagesSingle(doc, scaleFactor, enhanceScans, pdfDoc, pdfPages, pdfPageBoxes, sPdfPageBoxes, pData, pageImageParts, noImagePageIDs, objects, pageImageCache, spm);
		
		//	check errors
		this.checkException(pf);
		
		//	garbage collect page images
		System.gc();
		
		//	finally ...
		return spData;
	}
	
	private PPageData[] addImagePdfPagesSingle(final ImDocument doc, final int scaleFactor, final boolean enhanceScans, final Document pdfDoc, final Page[] pdfPages, final Rectangle2D.Float[] pdfPageBoxes, final Rectangle2D.Float[] sPdfPageBoxes, final PPageData[] pData, final ArrayList[] pageImageParts, final Set noImagePageIDs, final Map objects, final Map pageImageCache, final SynchronizedProgressMonitor spm) throws IOException {
		
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
				PPageImage pPageImage = getPageImage(pageImageParts[p], objects, pdfDoc, pdfPages[p], pdfPageBoxes[p], spm);
				if (pPageImage == null) {
					spm.setInfo(" --> page image generation failed");
					throw new IOException("Could not generate image for page " + p);
				}
				spm.setInfo(" - got page image sized " + pPageImage.image.getWidth() + " x " + pPageImage.image.getHeight());
				
				//	compute DPI based on scan figure bounds
				spm.setInfo(" - figure bounds are " + pPageImage.bounds);
				int dpiScaleFactor = scaleFactor;
				float rawDpi = pPageImage.getRawDpi(null);
				if ((rawDpi < 100) && (sPdfPageBoxes[p] != null)) {
					rawDpi = pPageImage.getRawDpi(sPdfPageBoxes[p]);
					spm.setInfo(" - using scaled resolution");
				}
				int dpi = ((Math.round(rawDpi / 10) * 10) / dpiScaleFactor);
				spm.setInfo(" - resolution computed as " + dpi + " DPI (" + rawDpi + ")");
				
				//	store raw (accurate) DPI with page data
				pData[p].rawPageImageDpi = rawDpi;
				
				//	get image proper for easier handling
				BufferedImage pageImage = pPageImage.image;
				
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
					spig.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
					spig.drawImage(pageImage, 0, 0, sPageImage.getWidth(), sPageImage.getHeight(), Color.WHITE, null);
					pageImage = sPageImage;
					pData[p].rawPageImageDpi = (rawDpi / scaleDownFactor);
					dpi = (dpi / scaleDownFactor);
					spm.setInfo(" - page image scaled to " + dpi + " DPI");
				}
				
				//	preserve original page image as supplement
				synchronized (doc) {
					ImSupplement.Scan.createScan(doc, (p - pDelta), 0, dpi, pageImage);
				}
				
				//	enhance image (cannot use cache here, as image might change during correction)
				if (enhanceScans) {
					AnalysisImage ai = Imaging.wrapImage(pageImage, null);
					spm.setInfo(" - enhancing image ...");
					ai = Imaging.correctImage(ai, dpi, spm);
					pageImage = ai.getImage();
				}
				
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
		return pData;
	}
	
	private PPageData[] addImagePdfPagesDouble(final ImDocument doc, final int scaleFactor, final boolean enhanceScans, final Document pdfDoc, final Page[] pdfPages, final Rectangle2D.Float[] pdfPageBoxes, final Rectangle2D.Float[] sPdfPageBoxes, final PPageData[] pData, final ArrayList[] pageImageParts, final Set noImagePageIDs, final Map objects, final Map pageImageCache, final SynchronizedProgressMonitor spm) throws IOException {
		
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
				PPageImage pDoublePageImage = getPageImage(pageImageParts[pp], objects, pdfDoc, pdfPages[pp], pdfPageBoxes[pp], spm);
				if (pDoublePageImage == null) {
					spm.setInfo(" --> page image generation failed");
					throw new IOException("Could not generate image for double page " + pp);
				}
				spm.setInfo(" - got double page image sized " + pDoublePageImage.image.getWidth() + " x " + pDoublePageImage.image.getHeight());
				
				//	compute DPI based on scan figure bounds for this
				spm.setInfo(" - figure bounds are " + pdfPageBoxes[pp]);
				int dpiScaleFactor = scaleFactor;
				float rawDpi = pDoublePageImage.getRawDpi(null);
				if ((rawDpi < 100) && (sPdfPageBoxes[pp] != null)) {
					rawDpi = pDoublePageImage.getRawDpi(sPdfPageBoxes[pp]);
					spm.setInfo(" - using scaled resolution");
				}
				int dpi = ((Math.round(rawDpi / 10) * 10) / dpiScaleFactor);
				spm.setInfo(" - resolution computed as " + dpi + " DPI");
				
				//	store raw (accurate) DPI with page data
				pData[pp].rawPageImageDpi = rawDpi;
				
				//	get image proper for easier handling
				BufferedImage doublePageImage = pDoublePageImage.image;
				
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
					spig.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
					spig.drawImage(doublePageImage, 0, 0, sDoublePageImage.getWidth(), sDoublePageImage.getHeight(), Color.WHITE, null);
					doublePageImage = sDoublePageImage;
					pData[pp].rawPageImageDpi = (rawDpi / scaleDownFactor);
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
					ImSupplement.Scan.createScan(doc, ((pp * 2) - ppDelta), 0, dpi, pageImageLeft);
					ImSupplement.Scan.createScan(doc, ((pp * 2) - ppDelta + 1), 0, dpi, pageImageRight);
				}
						
				//	enhance image halves (cannot use cache here, as image might change during correction)
				if (enhanceScans) {
					AnalysisImage aiLeft = Imaging.wrapImage(pageImageLeft, null);
					spm.setInfo(" - enhancing left image half ...");
					aiLeft = Imaging.correctImage(aiLeft, dpi, spm);
					pageImageLeft = aiLeft.getImage();
					AnalysisImage aiRight = Imaging.wrapImage(pageImageRight, null);
					spm.setInfo(" - enhancing right image half ...");
					aiRight = Imaging.correctImage(aiRight, dpi, spm);
					pageImageRight = aiRight.getImage();
				}
				
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
		
		//	assemble document (two pages per PDF page here), and split up page data
		PPageData[] spData = new PPageData[pdfPages.length * 2];
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
			
			//	split up page data (especially embedded OCR words)
			//	TODO somehow adjust word and figure boundaries, as well as page boundaries
			ArrayList wordsLeft = new ArrayList();
			ArrayList wordsRight = new ArrayList();
			for (int w = 0; w < pData[pp].words.length; w++) {
				if (pData[pp].words[w].bounds.getMaxX() < (pData[pp].pdfPageBox.getWidth() / 2))
					wordsLeft.add(pData[pp].words[w]);
				else if (pData[pp].words[w].bounds.getMinX() > (pData[pp].pdfPageBox.getWidth() / 2))
					wordsRight.add(new PWord(pData[pp].words[w].renderOrderNumber, pData[pp].words[w].charCodes, pData[pp].words[w].str, new Rectangle2D.Float(
							((float) (pData[pp].words[w].bounds.getMinX() - (pData[pp].pdfPageBox.getWidth() / 2))),
							((float) pData[pp].words[w].bounds.getMinY()),
							((float) pData[pp].words[w].bounds.getWidth()),
							((float) pData[pp].words[w].bounds.getHeight())
						), pData[pp].words[w].color, pData[pp].words[w].fontSize, pData[pp].words[w].fontDirection, pData[pp].words[w].font));
			}
			ArrayList figuresLeft = new ArrayList();
			ArrayList figuresRight = new ArrayList();
			for (int f = 0; f < pData[pp].figures.length; f++) {
				if (pData[pp].figures[f].bounds.getMaxX() < (pData[pp].pdfPageBox.getWidth() / 2))
					figuresLeft.add(pData[pp].figures[f]);
				else if (pData[pp].figures[f].bounds.getMinX() > (pData[pp].pdfPageBox.getWidth() / 2))
					figuresRight.add(new PFigure(pData[pp].figures[f].renderOrderNumber, pData[pp].figures[f].name, new Rectangle2D.Float(
							((float) (pData[pp].figures[f].bounds.getMinX() - (pData[pp].pdfPageBox.getWidth() / 2))),
							((float) pData[pp].figures[f].bounds.getMinY()),
							((float) pData[pp].figures[f].bounds.getWidth()),
							((float) pData[pp].figures[f].bounds.getHeight())
						), pData[pp].figures[f].refOrData, pData[pp].figures[f].rotation, pData[pp].figures[f].rightSideLeft, pData[pp].figures[f].upsideDown));
			}
			ArrayList pathsLeft = new ArrayList();
			ArrayList pathsRight = new ArrayList();
			for (int p = 0; p < pData[pp].paths.length; p++) {
				Rectangle2D pBounds = pData[pp].paths[p].getBounds();
				if (pBounds.getMaxX() < (pData[pp].pdfPageBox.getWidth() / 2))
					pathsLeft.add(pData[pp].paths[p]);
				else if (pBounds.getMinX() > (pData[pp].pdfPageBox.getWidth() / 2))
					pathsRight.add(pData[pp].paths[p].translate(0, ((float) -(pData[pp].pdfPageBox.getWidth() / 2))));
			}
			
			//	tray up split page data
			spData[pp * 2] = new PPageData(pData[pp].p, pData[pp].pdfPage, new Rectangle2D.Float(
					((float) pData[pp].pdfPageBox.getMinX()),
					((float) pData[pp].pdfPageBox.getMinY()),
					((float) (pData[pp].pdfPageBox.getWidth() / 2)),
					((float) pData[pp].pdfPageBox.getHeight())
				), new Rectangle2D.Float(
					((float) pData[pp].pdfPageContentBox.getMinX()),
					((float) pData[pp].pdfPageContentBox.getMinY()),
					((float) (pData[pp].pdfPageContentBox.getWidth() / 2)),
					((float) pData[pp].pdfPageContentBox.getHeight())
				), pData[pp].rotate, pData[pp].pdfPageResources, ((PWord[]) wordsLeft.toArray(new PWord[wordsLeft.size()])), ((PFigure[]) figuresLeft.toArray(new PFigure[figuresLeft.size()])), ((PPath[]) pathsLeft.toArray(new PPath[pathsLeft.size()])));
			spData[(pp * 2) + 1] = new PPageData(pData[pp].p, pData[pp].pdfPage, new Rectangle2D.Float(
					((float) pData[pp].pdfPageBox.getMinX()),
					((float) pData[pp].pdfPageBox.getMinY()),
					((float) (pData[pp].pdfPageBox.getWidth() / 2)),
					((float) pData[pp].pdfPageBox.getHeight())
				), new Rectangle2D.Float(
					((float) pData[pp].pdfPageContentBox.getMinX()),
					((float) pData[pp].pdfPageContentBox.getMinY()),
					((float) (pData[pp].pdfPageContentBox.getWidth() / 2)),
					((float) pData[pp].pdfPageContentBox.getHeight())
				), pData[pp].rotate, pData[pp].pdfPageResources, ((PWord[]) wordsRight.toArray(new PWord[wordsRight.size()])), ((PFigure[]) figuresRight.toArray(new PFigure[figuresRight.size()])), ((PPath[]) pathsRight.toArray(new PPath[pathsRight.size()])));
		}
		
		//	finally ...
		spm.setProgress(100);
		return spData;
	}
	
	private PPageImage getPageImage(ArrayList pageImageParts, Map objects, Document pdfDoc, Page pdfPage, Rectangle2D.Float pdfPageBox, ProgressMonitor spm) throws IOException {
		
		//	sort out figures overpainted later
		for (int p = 0; p < pageImageParts.size(); p++) {
			PImagePart pip = ((PImagePart) pageImageParts.get(p));
			for (int cp = (p+1); cp < pageImageParts.size(); cp++) {
				PImagePart cpip = ((PImagePart) pageImageParts.get(cp));
				if (overpaints(cpip.pdfBounds, pip.pdfBounds)) {
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
		
		//	compute rendering resolution and aggregate bounds
		float maxPipDpi = 0;
		float rawMinX = Float.MAX_VALUE;
		float rawMaxX = -Float.MAX_VALUE;
		float rawMinY = Float.MAX_VALUE;
		float rawMaxY = -Float.MAX_VALUE;
		for (int p = 0; p < pageImageParts.size(); p++) {
			PImagePart pip = ((PImagePart) pageImageParts.get(p));
			float pipDpi = pip.rawDpi;
			int pipScaleDownFactor = 1;
			while ((pipDpi / pipScaleDownFactor) >= minScaleDownDpi)
				pipScaleDownFactor++;
			pipDpi /= pipScaleDownFactor;
			maxPipDpi = Math.max(maxPipDpi, pipDpi);
			rawMinX = Math.min(rawMinX, ((float) pip.pdfBounds.getMinX()));
			rawMaxX = Math.max(rawMaxX, ((float) pip.pdfBounds.getMaxX()));
			rawMinY = Math.min(rawMinY, ((float) pip.pdfBounds.getMinY()));
			rawMaxY = Math.max(rawMaxY, ((float) pip.pdfBounds.getMaxY()));
		}
		spm.setInfo("Rendering resolution computed as " + maxPipDpi + " DPI from " + pageImageParts.size() + " page image parts");
		Rectangle2D.Float piPdfBounds = new Rectangle2D.Float(rawMinX, rawMinY, (rawMaxX - rawMinX), (rawMaxY - rawMinY));
		spm.setInfo("Aggregate bounds computed as " + piPdfBounds + " from " + pageImageParts.size() + " page image parts");
		
		//	create base image
		int piWidth = Math.round(Math.abs(pdfPageBox.width * maxPipDpi) / defaultDpi);
		int piHeight = Math.round(Math.abs(pdfPageBox.height * maxPipDpi) / defaultDpi);
		spm.setInfo("Rendering page image sized " + piWidth + "x" + piHeight);
		BufferedImage pageImage = new BufferedImage(piWidth, piHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D piGraphics = pageImage.createGraphics();
		piGraphics.setColor(Color.WHITE);
		piGraphics.fillRect(0, 0, piWidth, piHeight);
		piGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		
		//	render remaining figures
		for (int p = 0; p < pageImageParts.size(); p++) {
			PImagePart pip = ((PImagePart) pageImageParts.get(p));
			AffineTransform at = piGraphics.getTransform();
			piGraphics.translate(pip.bounds.left, pip.bounds.top);
			if (Math.abs(pip.rawDpi - maxPipDpi) > 0.001 /* cut scaling a fraction of slack */)
				piGraphics.scale((((double) maxPipDpi) / pip.rawDpi), (((double) maxPipDpi) / pip.rawDpi));
			spm.setInfo(" - rendering page image part sized " + (pip.bounds.right - pip.bounds.left) + "x" + (pip.bounds.bottom - pip.bounds.top) + " at " + pip.rawDpi + " scaled by " + (((double) maxPipDpi) / pip.rawDpi));
			PPageImage rPip = this.getPageImagePart(pip, objects, pdfDoc, pdfPage, spm);
			piGraphics.drawImage(rPip.image, 0, 0, null);
			piGraphics.setTransform(at);
		}
		
		//	finally ...
		piGraphics.dispose();
		return new PPageImage(pageImage, piPdfBounds);
	}
	
	private static final boolean overpaints(Rectangle2D overpainting, Rectangle2D overpainted) {
		if (!overpainted.intersects(overpainting))
			return false;
		if (overpainting.contains(overpainted))
			return true;
		Rectangle2D uRect = new Rectangle2D.Float();
		Rectangle2D.union(overpainted, overpainting, uRect);
		Rectangle2D iRect = new Rectangle2D.Float();
		Rectangle2D.intersect(overpainted, overpainting, iRect);
		return ((getSize(iRect) * 20) > (getSize(uRect) * 19));
	}
	
	private static final boolean liesMostlyIn(Rectangle2D inner, Rectangle2D outer) {
		if (!inner.intersects(outer))
			return false;
		if (outer.contains(inner))
			return true;
		Rectangle2D uRect = new Rectangle2D.Float();
		Rectangle2D.union(inner, outer, uRect);
		Rectangle2D iRect = new Rectangle2D.Float();
		Rectangle2D.intersect(inner, outer, iRect);
		return ((getSize(iRect) * 10) > (getSize(uRect) * 9));
	}
	
	private static final double getSize(Rectangle2D rect) {
		return Math.abs(rect.getWidth() * rect.getHeight());
	}
	
	private PPageImage getPageImagePart(PImagePart pip, Map objects, Document pdfDoc, Page pdfPage, ProgressMonitor spm) throws IOException {
		
		//	decode PStream to image
		BufferedImage pipBi = decodeImage(pdfPage, pip.data.params, pip.data.bytes, objects, false);
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
//		
//		Object csObj = PdfParser.dereference(pip.data.params.get("ColorSpace"), objects);
//		if (csObj != null) {
//			spm.setInfo("     - color space is " + csObj.toString());
//			
//			//	get filter to catch JPEG ('DCTDecode')
//			Object filterObj = PdfParser.getObject(pip.data.params, "Filter", objects);
//			if (filterObj instanceof Vector) {
//				if (((Vector) filterObj).size() != 0)
//					filterObj = ((Vector) filterObj).get(0);
//			}
//			if (filterObj instanceof Reference)
//				filterObj = objects.get(((Reference) filterObj).getObjectNumber() + " " + ((Reference) filterObj).getGenerationNumber());
//			spm.setInfo("     - filter is " + filterObj.toString());
//			
//			/* If color space is DeviceCMYK, render page image to figure
//			 * resolution and cut out image: images with color space
//			 * DeviceCMYK come out somewhat differently colored than in
//			 * Acrobat, but we cannot seem to do anything about this -
//			 * IcePDF distorts them as well, if more in single image
//			 * rendering than in the page images it generates. */
//			if ("DeviceCMYK".equals(csObj.toString()) && ((filterObj == null) || !"DCTDecode".equals(filterObj.toString()))) {
////				pipBi = extractFigureFromPageImage(pdfDoc, pip.pageId, pip.dpi, pip.bounds);
//				pipBi = extractFigureFromPageImage(pdfDoc, pip.pageId, pip.rawDpi, pip.bounds);
//				spm.setInfo("     --> figure re-rendered as part of page image");
//			}
//			/* If color space is Separation, values represent color
//			 * intensity rather than brightness, which means 1.0 is
//			 * black and 0.0 is white. This means the have to invert
//			 * brightness to get the actual image. IcePDF gets this
//			 * wrong as well, with images coming out white on black
//			 * rather than the other way around, so we have to do the
//			 * correction ourselves. */
//			else if ((csObj instanceof Vector) && (((Vector) csObj).size() == 4) && ((Vector) csObj).get(0).equals("Separation")) {
//				invertFigureBrightness(pipBi);
//				spm.setInfo("     --> figure brightness inverted for additive 'Separation' color space");
//			}
//			/* If color space is Indexed, IcePDF seems to do a lot better
//			 * in page image rendering than in single-image rendering. */
//			else if ((csObj instanceof Vector) && (((Vector) csObj).size() == 4) && ((Vector) csObj).get(0).equals("Indexed")) {
////				pipBi = extractFigureFromPageImage(pdfDoc, pip.pageId, pip.dpi, pip.bounds);
//				pipBi = extractFigureFromPageImage(pdfDoc, pip.pageId, pip.rawDpi, pip.bounds);
//				spm.setInfo("     --> figure re-rendered as part of page image");
//			}
////			/* Just taking a look a ICCBased color space for now ... IcePDF
////			 * doesn't really do better at those, as it seems ... */
////			else if ((csObj instanceof Vector) && (((Vector) csObj).size() == 2) && ((Vector) csObj).get(0).equals("ICCBased")) {
////				Object csEntryObj = PdfParser.dereference(((Vector) csObj).get(1), objects);
////				spm.setInfo("     --> ICCBased color space");
////				spm.setInfo("     --> ICCBased color space entry is " + csEntryObj);
////				if (csEntryObj instanceof PStream) {
////					PStream csEntry = ((PStream) csEntryObj);
////					Object csFilter = csEntry.params.get("Filter");
////					ByteArrayOutputStream csBaos = new ByteArrayOutputStream();
////					PdfParser.decode(csFilter, csEntry.bytes, csEntry.params, csBaos, objects);
////					System.out.println(new String(csBaos.toByteArray()));
////				}
////			}
//		}
		
		//	finally ...
		return new PPageImage(pipBi, pip.pdfBounds);
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
	
	private BufferedImage decodeImage(Page pdfPage, Hashtable params, byte[] stream, Map objects, boolean forceUseIcePdf) throws IOException {
		if (PdfExtractorTest.aimAtPage != -1) {
			System.out.println(" ==> read " + stream.length + " bytes");
			System.out.println(" ==> Lenght parameter is " + params.get("Length"));
			System.out.println(" ==> Width parameter is " + params.get("Width"));
			System.out.println(" ==> Height parameter is " + params.get("Height"));
		}
		int length;
		try {
			length = Integer.parseInt(params.get("Length").toString());
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" ==> Lenght is " + length);
		}
		catch (Exception e) {
			length = stream.length;
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" ==> fallback Lenght is " + length);
		}
		
		if (stream.length < length) {
			byte[] padStream = new byte[length];
			System.arraycopy(stream, 0, padStream, 0, stream.length);
			for (int b = stream.length; b < padStream.length; b++)
				padStream[b] = '\n';
			stream = padStream;
		}
		else if (stream.length > length) {
			byte[] cropStream = new byte[length];
			System.arraycopy(stream, 0, cropStream, 0, cropStream.length);
			stream = cropStream;
		}
		
		Object filterObj = PdfParser.getObject(params, "Filter", objects);
		if (filterObj instanceof Vector) {
			if (((Vector) filterObj).size() != 0)
				filterObj = ((Vector) filterObj).get(0);
		}
		if (filterObj instanceof Reference)
			filterObj = objects.get(((Reference) filterObj).getObjectNumber() + " " + ((Reference) filterObj).getGenerationNumber());
		String filter = ((filterObj == null) ? null : filterObj.toString());
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println(" ==> filter is " + filter);
		
		Object colorSpaceObj = PdfParser.getObject(params, "ColorSpace", objects);
		if (colorSpaceObj instanceof Vector) {
			if (((Vector) colorSpaceObj).size() != 0)
				colorSpaceObj = ((Vector) colorSpaceObj).get(0);
		}
		String colorSpace = ((colorSpaceObj == null) ? null : colorSpaceObj.toString());
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println(" ==> color space is " + colorSpace);
		
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println(" ==> params are " + params);
//		Object metadataObj = PdfParser.getObject(params, "Metadata", objects);
//		if (metadataObj instanceof PStream) {
//			System.out.println(" ==> metadata params are " + ((PStream) metadataObj).params);
//			System.out.println(" ==> metadata is " + new String(((PStream) metadataObj).bytes));
//		}
		if ((filter == null) && (params.get("Resources") instanceof Hashtable) && (params.get("Type") != null) && "XObject".equals(params.get("Type").toString())) {
			//	get XObject image and recurse
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(new String(stream));
			Hashtable resources = ((Hashtable) params.get("Resources"));
			Hashtable xObject = ((Hashtable) PdfParser.dereference(resources.get("XObject"), objects));
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" ==> getting XObject figures from " + xObject);
			PFigure[] xpFigures = PdfParser.getPageFigures(pdfPage.getEntries(), stream, resources, objects, ProgressMonitor.dummy);
			for (int f = 0; f < xpFigures.length; f++) {
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println("   - " + xpFigures[f].name + " @" + xpFigures[f].bounds);
				Object xpFigureObj = PdfParser.getObject(xObject, xpFigures[f].name, objects);
				if (xpFigureObj instanceof PStream)
					return this.decodeImage(pdfPage, ((PStream) xpFigureObj).params, ((PStream) xpFigureObj).bytes, objects, forceUseIcePdf);
			}
			return null;
		}
		else if ((filter != null) && "JPXDecode".equals(filter.toString())) {
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" ==> decoding JPX");
			return this.decodeJPX(stream);
		}
		else if ((filter != null) && "JBIG2Decode".equals(filter.toString())) {
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" ==> decoding JBIG2");
			//	JPedal seems to be the one ...
			return this.decodeJBig2(stream);
		}
		else if ((filter != null) && "FlateDecode".equals(filter.toString())) {
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" ==> decoding Flate");
			//	TODO use java.util.zip.GZIPInputStream instead of IcePDF
			return this.decodeFlate(stream, params, pdfPage.getLibrary(), pdfPage.getResources(), objects, forceUseIcePdf);
		}
		else if ((filter != null) && "DCTDecode".equals(filter.toString())) {
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" ==> decoding JPEG");
			return this.decodeJpeg(stream, colorSpace);
		}
		else {
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" ==> decoding other");
			return this.decodeOther(stream, params, filter, pdfPage.getLibrary(), pdfPage.getResources(), objects);
		}
	}
	
	private BufferedImage decodeImageMagick(byte[] stream, String format, String colorSpace) throws IOException {
		if (this.imageDecoder == null) {
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" ==> Image Magick not available");
			return null;
		}
		else {
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" ==> decoding via Image Magick");
			return this.imageDecoder.decodeImage(stream, format, colorSpace);
		}
	}
	
	private BufferedImage decodeJBig2(byte[] stream) throws IOException {
		try {
			JBIG2Decoder jbd = new JBIG2Decoder();
			jbd.decodeJBIG2(stream);
			BufferedImage bi = jbd.getPageAsBufferedImage(0);
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" ==> got JBIG2 image, size is " + bi.getWidth() + " x " + bi.getHeight());
			return bi;
		}
		catch (JBIG2Exception jbe) {
			System.out.println(" ==> Could not decode JBIG2 image: " + jbe.getMessage());
			jbe.printStackTrace(System.out);
			return null;
		}
	}
	
	private BufferedImage decodeFlate(byte[] stream, Hashtable params, Library library, Resources resources, Map objects, boolean forceUseIcePdf) throws IOException {
		if (!forceUseIcePdf) {
			BufferedImage bitmapBi = this.decodeBitmap(stream, "FlateDecode", params, library, resources, objects);
			if (bitmapBi != null)
				return bitmapBi;
		}
		
		SeekableInputConstrainedWrapper streamInputWrapper = new SeekableInputConstrainedWrapper(new SeekableByteArrayInputStream(stream), 0, stream.length, true);
		Stream str = new Stream(library, params, streamInputWrapper);
		try {
			Color biBackgroundColor = this.getBackgroundColor(params);
			BufferedImage bi = str.getImage(biBackgroundColor, resources, false);
			if (PdfExtractorTest.aimAtPage != -1) {
				if (bi != null)
					System.out.println(" ==> got flate image, size is " + bi.getWidth() + " x " + bi.getHeight());
				else System.out.println(" ==> Could not decode image");
			}
			return bi;
		}
		catch (Exception e) {
			System.out.println(" ==> Could not decode Flate image: " + e.getMessage());
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
	
	private BufferedImage decodeOther(byte[] stream, Hashtable params, String filter, Library library, Resources resources, Map objects) throws IOException {
//		TODO use this sucker, as we're now getting a hand on color spaces
//		BufferedImage bitmapBi = this.decodeBitmap(stream, filter, params, library, resources);
//		if (bitmapBi != null)
//			return bitmapBi;
		
		SeekableInputConstrainedWrapper streamInputWrapper = new SeekableInputConstrainedWrapper(new SeekableByteArrayInputStream(stream), 0, stream.length, true);
		Stream str = new Stream(library, params, streamInputWrapper);
		try {
			Color biBackgroundColor = this.getBackgroundColor(params);
			BufferedImage bi = str.getImage(biBackgroundColor, resources, false);
			if (PdfExtractorTest.aimAtPage != -1) {
				if (bi != null)
					System.out.println(" ==> got image, size is " + bi.getWidth() + " x " + bi.getHeight());
				else System.out.println(" ==> Could not decode image");
			}
			return bi;
		}
		catch (Exception e) {
			System.out.println(" ==> Could not decode image: " + e.getMessage());
			e.printStackTrace(System.out);
			return null;
		}
	}
	
	private BufferedImage decodeBitmap(byte[] stream, String filter, Hashtable params, Library library, Resources resources, Map objects) throws IOException {
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println(" ==> decoding bitmap");
		
		//	test if we can handle this one
		Object csObj = params.get("ColorSpace");
		if (csObj == null)
			return null;
		csObj = PdfParser.dereference(csObj, objects);
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println("   - color space data is " + csObj);
		PdfColorSpace cs = PdfColorSpace.getColorSpace(csObj, objects);
		if (cs == null)
			return null;
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println("   - color space is " + cs.name + " (" + cs.numComponents + " components)");
		
		//	get and TODO observe decode parameter dictionary
		Hashtable decodeParams = null;//((Hashtable) PdfParser.dereference(params.get("DecodeParms"), objects));
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println("   - decode parameters are " + decodeParams);
		
		//	get image size
		int width;
		if (decodeParams == null) {
			Object widthObj = params.get("Width");
			if (widthObj instanceof Number)
				width = ((Number) widthObj).intValue();
			else return null;
		}
		else {
			Object columnsObj = decodeParams.get("Columns");
			if (columnsObj instanceof Number)
				width = ((Number) columnsObj).intValue();
			else return null;
		}
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println("   - width is " + width);
		
		int height;
		Object heightObj = params.get("Height");
		if (heightObj instanceof Number)
			height = ((Number) heightObj).intValue();
		else return null;
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println("   - height is " + height);
		
		//	get component depth
		int bitsPerComponent;
		if (decodeParams == null) {
			Object bcpObj = params.get("BitsPerComponent");
			if (bcpObj instanceof Number)
				bitsPerComponent = ((Number) bcpObj).intValue();
			else return null;
		}
		else {
			Object bcpObj = decodeParams.get("BitsPerComponent");
			if (bcpObj instanceof Number)
				bitsPerComponent = ((Number) bcpObj).intValue();
			else return null;
		}
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println("   - bits per component is " + bitsPerComponent);
		
		//	prepare decoding stream
		InputStream biIn;
		if ("FlateDecode".equals(filter) || "Fl".equals(filter))
			biIn = new BufferedInputStream(new FlateDecode(library, params, new ByteArrayInputStream(stream)));
		else if ("LZWDecode".equals(filter) || "LZW".equals(filter))
			biIn = new BufferedInputStream(new LZWDecode(new BitStream(new ByteArrayInputStream(stream)), library, params));
		else if ("ASCII85Decode".equals(filter) || "A85".equals(filter))
			biIn = new BufferedInputStream(new ASCII85Decode(new ByteArrayInputStream(stream)));
		else if ("ASCIIHexDecode".equals(filter) || "AHx".equals(filter))
			biIn = new BufferedInputStream(new ASCIIHexDecode(new ByteArrayInputStream(stream)));
		else if ("RunLengthDecode".equals(filter) || "RL".equals(filter))
			biIn = new BufferedInputStream(new RunLengthDecode(new ByteArrayInputStream(stream)));
		//	TODO observe other encodings (as we encounter them)
		else biIn = new ByteArrayInputStream(stream);
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println("   - filter is " + filter);
		
		//	create bit masks
		int colorComponents = cs.numComponents; // this is constant for RGB
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println("   - color components are " + colorComponents);
		int componentBitMask = 0;
		for (int b = 0; b < bitsPerComponent; b++) {
			componentBitMask <<= 1;
			componentBitMask |= 1;
		}
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println("   - component bit mask is " + Integer.toString(componentBitMask, 16));
		int bitsPerPixel = (colorComponents * bitsPerComponent);
		int pixelBitMask = 0;
		for (int b = 0; b < bitsPerPixel; b++) {
			pixelBitMask <<= 1;
			pixelBitMask |= 1;
		}
		if (PdfExtractorTest.aimAtPage != -1) {
			System.out.println("   - bits per pixel is " + bitsPerPixel);
			System.out.println("   - pixel bit mask is " + Integer.toString(pixelBitMask, 16));
		}
		
		//	fill image
		LinkedList colorDecodeStack = new LinkedList();
		ArrayList topPixelRow = null;
		if (PdfExtractorTest.aimAtPage != -1) {
			colorDecodeStack.addFirst("DEBUG_COLORS");
			topPixelRow = new ArrayList();
		}
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
				if ((r == 0) && (topPixelRow != null))
					topPixelRow.add(Integer.toString(pixelBits, 16).toUpperCase());
				
				//	extract component values
				for (int cc = 0; cc < colorComponents; cc++) {
					int rccb = ((pixelBits >>> ((colorComponents - cc - 1) * bitsPerComponent)) & componentBitMask);
					int ccb = rccb;
					for (int b = bitsPerComponent; b < 8; b += bitsPerComponent) {
						ccb <<= bitsPerComponent;
						ccb |= rccb;
					}
					colorDecodeStack.addLast(new Float(((float) ccb) / 255));
				}
				
				//	get color
				Color color = cs.decodeColor(colorDecodeStack, ((PdfExtractorTest.aimAtPage != -1) ? "" : null));
				
				//	set pixel
				bi.setRGB(c, r, color.getRGB());
			}
		}
		
		//	finally ...
		biIn.close();
		return bi;
	}
	
	private BufferedImage decodeJPX(byte[] stream) throws IOException {
		return this.decodeImageMagick(stream, "jp2", null);
	}
	
	private BufferedImage decodeJpeg(byte[] stream, String colorSpace) throws IOException {
		return this.decodeImageMagick(stream, "jpg", colorSpace);
//		return JpegReader.readImage(stream);
	}
//	
//	private static class JpegReader {
//		
//		private static final int COLOR_TYPE_RGB = 1;
//		private static final int COLOR_TYPE_CMYK = 2;
//		private static final int COLOR_TYPE_YCCK = 3;
////
////	    private int colorType = COLOR_TYPE_RGB;
////	    private boolean hasAdobeMarker = false;
//	    
//	    static BufferedImage readImage(byte[] imageData) throws IOException {
//	        int colorType = COLOR_TYPE_RGB;
//	        boolean hasAdobeMarker = false;
//
//	        ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(imageData));
//	        Iterator iter = ImageIO.getImageReaders(stream);
//	        while (iter.hasNext()) {
//	            ImageReader reader = ((ImageReader) iter.next());
//	            reader.setInput(stream);
//	            
//	            try {
//	                return reader.read(0);
//	            }
//	            catch (IIOException iioe) {
//	            	System.out.println("Exception loading image via native facilities: " + iioe.getMessage());
//	            	iioe.printStackTrace(System.out);
//	            }
//	            
//	            
//                colorType = COLOR_TYPE_CMYK;
//                
////              checkAdobeMarker(file);
//                try {
//	    	        JpegImageParser parser = new JpegImageParser();
//	    	        ByteSource byteSource = new ByteSourceArray(imageData);
//	    	        int[] markers = {0xFFEE};
//	    	        ArrayList segments = parser.readSegments(byteSource, markers, true);
//	    	        if ((segments != null) && (segments.size() >= 1)) {
//	    	            UnknownSegment app14Segment = (UnknownSegment) segments.get(0);
//	    	            byte[] data = app14Segment.bytes;
//	    	            if (data.length >= 12 && data[0] == 'A' && data[1] == 'd' && data[2] == 'o' && data[3] == 'b' && data[4] == 'e') {
//	    	                hasAdobeMarker = true;
//	    	                int transform = app14Segment.bytes[11] & 0xff;
//	    	                if (transform == 2)
//	    	                    colorType = COLOR_TYPE_YCCK;
//	    	            }
//	    	        }
//	    	        
//	    	        ICC_Profile profile = Sanselan.getICCProfile(imageData);
//	                WritableRaster raster = (WritableRaster) reader.readRaster(0, null);
//	                if (colorType == COLOR_TYPE_YCCK)
//	                    convertYcckToCmyk(raster);
//	                if (hasAdobeMarker)
//	                    convertInvertedColors(raster);
//	                return convertCmykToRgb(raster, profile);
//                }
//                catch (ImageReadException ire) {
//	            	System.out.println("Exception loading image via Sanselan: " + ire.getMessage());
//	            	ire.printStackTrace(System.out);
//                }
//	        }
//	        
//	        return null;
//	    }
////	    
////	    private void checkAdobeMarker(File file) throws IOException, ImageReadException {
////	        JpegImageParser parser = new JpegImageParser();
////	        ByteSource byteSource = new ByteSourceFile(file);
////	        ArrayList segments = parser.readSegments(byteSource, new int[] { 0xffee }, true);
////	        if (segments != null && segments.size() >= 1) {
////	            UnknownSegment app14Segment = (UnknownSegment) segments.get(0);
////	            byte[] data = app14Segment.bytes;
////	            if (data.length >= 12 && data[0] == 'A' && data[1] == 'd' && data[2] == 'o' && data[3] == 'b' && data[4] == 'e') {
////	                hasAdobeMarker = true;
////	                int transform = app14Segment.bytes[11] & 0xff;
////	                if (transform == 2)
////	                    colorType = COLOR_TYPE_YCCK;
////	            }
////	        }
////	    }
//
//	    private static void convertYcckToCmyk(WritableRaster raster) {
//	        int height = raster.getHeight();
//	        int width = raster.getWidth();
//	        int stride = width * 4;
//	        int[] pixelRow = new int[stride];
//	        for (int h = 0; h < height; h++) {
//	            raster.getPixels(0, h, width, 1, pixelRow);
//
//	            for (int x = 0; x < stride; x += 4) {
//	                int y = pixelRow[x];
//	                int cb = pixelRow[x + 1];
//	                int cr = pixelRow[x + 2];
//
//	                int c = (int) (y + 1.402 * cr - 178.956);
//	                int m = (int) (y - 0.34414 * cb - 0.71414 * cr + 135.95984);
//	                y = (int) (y + 1.772 * cb - 226.316);
//
//	                if (c < 0) c = 0; else if (c > 255) c = 255;
//	                if (m < 0) m = 0; else if (m > 255) m = 255;
//	                if (y < 0) y = 0; else if (y > 255) y = 255;
//
//	                pixelRow[x] = 255 - c;
//	                pixelRow[x + 1] = 255 - m;
//	                pixelRow[x + 2] = 255 - y;
//	            }
//
//	            raster.setPixels(0, h, width, 1, pixelRow);
//	        }
//	    }
//	    
//	    private static void convertInvertedColors(WritableRaster raster) {
//	        int height = raster.getHeight();
//	        int width = raster.getWidth();
//	        int stride = width * 4;
//	        int[] pixelRow = new int[stride];
//	        for (int h = 0; h < height; h++) {
//	            raster.getPixels(0, h, width, 1, pixelRow);
//	            for (int x = 0; x < stride; x++)
//	                pixelRow[x] = 255 - pixelRow[x];
//	            raster.setPixels(0, h, width, 1, pixelRow);
//	        }
//	    }
//	    
//	    private static BufferedImage convertCmykToRgb(Raster cmykRaster, ICC_Profile cmykProfile) throws IOException {
//	        if (cmykProfile == null)
//	            cmykProfile = ICC_Profile.getInstance(JpegReader.class.getResourceAsStream("/ISOcoated_v2_300_eci.icc"));
//
//	        if (cmykProfile.getProfileClass() != ICC_Profile.CLASS_DISPLAY) {
//	            byte[] profileData = cmykProfile.getData();
//
//	            if (profileData[ICC_Profile.icHdrRenderingIntent] == ICC_Profile.icPerceptual) {
//	                intToBigEndian(ICC_Profile.icSigDisplayClass, profileData, ICC_Profile.icHdrDeviceClass); // Header is first
//
//	                cmykProfile = ICC_Profile.getInstance(profileData);
//	            }
//	        }
//
//	        ICC_ColorSpace cmykCS = new ICC_ColorSpace(cmykProfile);
//	        BufferedImage rgbImage = new BufferedImage(cmykRaster.getWidth(), cmykRaster.getHeight(), BufferedImage.TYPE_INT_RGB);
//	        WritableRaster rgbRaster = rgbImage.getRaster();
//	        ColorSpace rgbCS = rgbImage.getColorModel().getColorSpace();
//	        ColorConvertOp cmykToRgb = new ColorConvertOp(cmykCS, rgbCS, null);
//	        cmykToRgb.filter(cmykRaster, rgbRaster);
//	        return rgbImage;
//	    }
//	    
//		private static void intToBigEndian(int value, byte[] array, int index) {
//		    array[index]   = (byte) (value >> 24);
//		    array[index+1] = (byte) (value >> 16);
//		    array[index+2] = (byte) (value >>  8);
//		    array[index+3] = (byte) (value);
//		}
//	}
	
	private Color getBackgroundColor(Hashtable params) {
		/* if we have a mask image
		 * Decoding [0, 1] ==> 0 means paint-through (default), requires black background
		 * Decoding [1, 0] ==> 1 means paint-through, requires white background WRONG, black as well */
		Object imObj = params.get("ImageMask");
		if (imObj == null) {
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" - image mask is null");
			return Color.WHITE;
		}
		else {
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" - image mask is " + imObj.getClass().getName() + " - " + imObj);
			Object dObj = params.get("Decode");
			if ((dObj instanceof Vector) && (((Vector) dObj).size() != 0)) {
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println(" - decoder is " + dObj.getClass().getName() + " - " + dObj);
				Object ptObj = ((Vector) dObj).get(0);
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println(" - paint-through is " + ptObj.getClass().getName() + " - " + ptObj);
				if (ptObj instanceof Number) {
					if (((Number) ptObj).intValue() == 0)
						return Color.BLACK;
					else return Color.BLACK;
				}
				else return Color.BLACK;
			}
			else {
				if (dObj == null) {
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println(" - decoder is null");
				}
				else if (PdfExtractorTest.aimAtPage != -1)
					System.out.println(" - decoder is " + dObj.getClass().getName() + " - " + dObj);
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
		int right = Math.min(figureBox.right, figurePageImage.getWidth());
		int width = (right - left);
		int top = Math.max(figureBox.top, 0);
		int bottom = Math.min(figureBox.bottom, figurePageImage.getHeight());
		int height = (bottom - top);
		
		//	extract figure
		return figurePageImage.getSubimage(left, top, width, height);
	}
//	
//	private BufferedImage extractFigureFromPageImage(Document pdfDoc, int p, float figureDpi, BoundingBox figureBox) {
//		
//		//	render page image at figure resolution
//		float figureMagnification = (figureDpi / defaultDpi);
//		BufferedImage figurePageImage;
//		synchronized (pdfDoc) {
//			pdfDoc.getPageTree().getPage(p, "").init(); // there might have been a previous call to reduceMemory() ...
//			figurePageImage = ((BufferedImage) pdfDoc.getPageImage(p, GraphicsRenderingHints.SCREEN, Page.BOUNDARY_CROPBOX, 0, figureMagnification));
//			pdfDoc.getPageTree().getPage(p, "").reduceMemory();
//		}
//		
//		//	extract figure
//		int figLeft = Math.max(figureBox.left, 0);
//		int figRight = Math.min(figureBox.right, figurePageImage.getWidth());
//		int figWidth = Math.min((figRight - figLeft), figurePageImage.getWidth());
//		int figTop = Math.max(figureBox.top, 0);
//		int figBottom = Math.min(figureBox.bottom, figurePageImage.getHeight());
//		int figHeight = Math.min((figBottom - figTop), figurePageImage.getHeight());
//		return figurePageImage.getSubimage(figLeft, figTop, figWidth, figHeight);
//	}
	
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