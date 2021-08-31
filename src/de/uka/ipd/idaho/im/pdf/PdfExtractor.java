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
 *     * Neither the name of the Universitaet Karlsruhe (TH) nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY UNIVERSITAET KARLSRUHE (TH) / KIT AND CONTRIBUTORS 
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
import java.awt.Point;
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
import java.awt.image.DataBuffer;
import java.awt.image.IndexColorModel;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import javax.imageio.ImageIO;

import org.icepdf.core.exceptions.PDFException;
import org.icepdf.core.exceptions.PDFSecurityException;
import org.icepdf.core.io.BitStream;
import org.icepdf.core.io.SeekableByteArrayInputStream;
import org.icepdf.core.io.SeekableInputConstrainedWrapper;
import org.icepdf.core.pobjects.Catalog;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.ImageStream;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.PageTree;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.Resources;
import org.icepdf.core.pobjects.filters.ASCII85Decode;
import org.icepdf.core.pobjects.filters.ASCIIHexDecode;
import org.icepdf.core.pobjects.filters.CCITTFaxDecoder;
import org.icepdf.core.pobjects.filters.FlateDecode;
import org.icepdf.core.pobjects.filters.LZWDecode;
import org.icepdf.core.pobjects.filters.PredictorDecode;
import org.icepdf.core.pobjects.filters.RunLengthDecode;
import org.icepdf.core.util.Library;
import org.jpedal.jbig2.JBIG2Decoder;
import org.jpedal.jbig2.JBIG2Exception;

import de.uka.ipd.idaho.easyIO.util.HashUtils;
import de.uka.ipd.idaho.gamta.Gamta;
import de.uka.ipd.idaho.gamta.MutableAnnotation;
import de.uka.ipd.idaho.gamta.TokenSequence;
import de.uka.ipd.idaho.gamta.Tokenizer;
import de.uka.ipd.idaho.gamta.defaultImplementation.RegExTokenizer;
import de.uka.ipd.idaho.gamta.util.CountingSet;
import de.uka.ipd.idaho.gamta.util.DocumentStyle.PropertiesData;
import de.uka.ipd.idaho.gamta.util.ParallelJobRunner;
import de.uka.ipd.idaho.gamta.util.ParallelJobRunner.ParallelFor;
import de.uka.ipd.idaho.gamta.util.ProgressMonitor;
import de.uka.ipd.idaho.gamta.util.ProgressMonitor.CascadingProgressMonitor;
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
import de.uka.ipd.idaho.im.analysis.PageImageAnalysis.BlockLine;
import de.uka.ipd.idaho.im.analysis.PageImageAnalysis.Line;
import de.uka.ipd.idaho.im.analysis.PageImageAnalysis.LineWord;
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
import de.uka.ipd.idaho.im.pdf.PdfParser.PMatrix;
import de.uka.ipd.idaho.im.pdf.PdfParser.PObject;
import de.uka.ipd.idaho.im.pdf.PdfParser.PPageContent;
import de.uka.ipd.idaho.im.pdf.PdfParser.PPath;
import de.uka.ipd.idaho.im.pdf.PdfParser.PRotate;
import de.uka.ipd.idaho.im.pdf.PdfParser.PStream;
import de.uka.ipd.idaho.im.pdf.PdfParser.PSubPath;
import de.uka.ipd.idaho.im.pdf.PdfParser.PWord;
import de.uka.ipd.idaho.im.pdf.test.PdfExtractorTest;
import de.uka.ipd.idaho.im.util.ImDocumentStyle;
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
	
	//private File basePath;
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
		//this.basePath = basePath;
		this.imageStore = imageStore;
		try {
//			this.imageDecoder = new PdfImageDecoder(new File(this.basePath, "ImageMagick"));
			this.imageDecoder = new PdfImageDecoder(new File(basePath, "ImageMagick"));
		}
		catch (Exception e) {
			System.out.println("PdfExtractor: could not create image decoder - " + e.getMessage());
			e.printStackTrace(System.out);
		}
		try {
//			this.ocrEngine = new OcrEngine(this.basePath, ((cachePath == null) ? null : new File(cachePath, "OcrEngine")), (useMultipleCores ? Math.max(1, (Runtime.getRuntime().availableProcessors()-1)) : 1));
			this.ocrEngine = new OcrEngine(basePath, ((cachePath == null) ? null : new File(cachePath, "OcrEngine")), (useMultipleCores ? Math.max(1, (Runtime.getRuntime().availableProcessors()-1)) : 1));
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
	 * @return the decoded Image Markup document
	 * @throws IOException
	 */
	public ImDocument loadGenericPdf(byte[] pdfBytes, ProgressMonitor pm) throws IOException {
		return this.loadGenericPdf(null, null, pdfBytes, 1, null, pm);
	}
	
	/**
	 * Load a document from a PDF. Page images are stored in the page image
	 * store handed to the constructor. The document structure is stored in the
	 * argument image markup document, including textual contents if available.
	 * If the argument set of page IDs is null, all pages are loaded.
	 * @param pdfBytes the raw binary data the the PDF document was parsed from
	 * @param pageIDs a set containing the IDs of the pages to decode
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the decoded Image Markup document
	 * @throws IOException
	 */
	public ImDocument loadGenericPdf(byte[] pdfBytes, Set pageIDs, ProgressMonitor pm) throws IOException {
		return this.loadGenericPdf(null, null, pdfBytes, 1, pageIDs, pm);
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
	 * @return the argument Image Markup document
	 * @throws IOException
	 */
	public ImDocument loadGenericPdf(ImDocument doc, Document pdfDoc, byte[] pdfBytes, ProgressMonitor pm) throws IOException {
		return this.loadGenericPdf(doc, pdfDoc, pdfBytes, 1, null, pm);
	}
	
	/**
	 * Load a document from a PDF. Page images are stored in the page image
	 * store handed to the constructor. The document structure is stored in the
	 * argument image markup document, including textual contents if available.
	 * If the argument ImDocument is null, this method creates a new one with
	 * the MD5 checksum of the argument PDF bytes as the ID. If the argument
	 * set of page IDs is null, all pages are loaded.
	 * @param doc the document to store the structural information in
	 * @param pdfDoc the PDF document to parse
	 * @param pdfBytes the raw binary data the the PDF document was parsed from
	 * @param pageIDs a set containing the IDs of the pages to decode
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the argument Image Markup document
	 * @throws IOException
	 */
	public ImDocument loadGenericPdf(ImDocument doc, Document pdfDoc, byte[] pdfBytes, Set pageIDs, ProgressMonitor pm) throws IOException {
		return this.loadGenericPdf(doc, pdfDoc, pdfBytes, 1, pageIDs, pm);
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
	 * @return the argument Image Markup document
	 * @throws IOException
	 */
	public ImDocument loadGenericPdf(ImDocument doc, Document pdfDoc, byte[] pdfBytes, int scaleFactor, ProgressMonitor pm) throws IOException {
		return this.loadGenericPdf(doc, pdfDoc, pdfBytes, scaleFactor, null, pm);
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
	 * of the argument PDF bytes as the ID. If the argument set of page IDs is
	 * null, all pages are loaded.
	 * @param doc the document to store the structural information in
	 * @param pdfDoc the PDF document to parse
	 * @param pdfBytes the raw binary data the the PDF document was parsed from
	 * @param scaleFactor the scale factor for image PDFs, to correct PDFs that
	 *            are set too small (DPI number is divided by this factor)
	 * @param pageIDs a set containing the IDs of the pages to decode
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the argument Image Markup document
	 * @throws IOException
	 */
	public ImDocument loadGenericPdf(ImDocument doc, Document pdfDoc, byte[] pdfBytes, int scaleFactor, Set pageIDs, ProgressMonitor pm) throws IOException {
		
		//	check arguments
		pdfDoc = this.getPdfDocument(pdfDoc, pdfBytes);
		if (doc == null)
			doc = this.doCreateDocument(getChecksum(pdfBytes), ((pageIDs == null) ? 0 : pdfDoc.getNumberOfPages()), pageIDs);
		if (pm == null)
			pm = ProgressMonitor.dummy;
		
		//	preserve source PDF in supplement
		ImSupplement.Source.createSource(doc, "application/pdf", pdfBytes);
		
		//	finally ...
		return this.doLoadGenericPdf(doc, pdfDoc, pdfBytes, scaleFactor, pageIDs, pm);
	}
	
	private Document getPdfDocument(Document pdfDoc, byte[] pdfBytes) throws IOException {
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
		return pdfDoc;
	}
	
	private ImDocument doLoadGenericPdf(final ImDocument doc, final Document pdfDoc, byte[] pdfBytes, int scaleFactor, Set pageIDs, ProgressMonitor pm) throws IOException {
		
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
		PPageData[] pData = this.getPdfPageData(doc, (getWords | getFigures | getPaths), PdfFontDecoder.UNICODE, true, pageIDs, catalog.getPageTree(), objects, spm);
		
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
		if (isTextPdf) {
			//	TODO filter invisible words
			this.addTextPdfPages(doc, pData, objects, spm);
		}
//		else this.addImagePdfPages(doc, pData, catalog.getPageTree(), objects, true, false, false, -1, (wordInFigureCount > (pageCount * 25)), false, scaleFactor, Imaging.ALL_OPTIONS, spm);
		else this.addImagePdfPages(doc, pData, catalog.getPageTree(), objects, true, false, false, -1, (wordInFigureCount > (pageCount * 25)), ADJUST_EMBEDDED_OCR_BLOCKS, scaleFactor, Imaging.ALL_OPTIONS, spm);
		
		//	finally ...
		return doc;
	}
	
	private static class PPageData {
		final int p;
		final Page pdfPage;
		/** MediaBox, use for computations in scanned PDFs, as their converted page size is computed from the scan */
		final Rectangle2D.Float pdfPageBox;
		/** CropBox, use for computations in born-digital PDFs, as their converted page size is computed from the page image size, which corresponds to the CropBox */
		final Rectangle2D.Float pdfPageContentBox;
		final int rotate;
		final Map pdfPageResources;
		PWord[] words = null;
		PFigure[] figures = null;
		PPath[] paths = null;
		float rawPageImageDpi = -1;
		int rightPageOffset = 0;
		PPageData(int p, Page pdfPage, Rectangle2D.Float pdfPageBox, Rectangle2D.Float pdfPageContentBox, PRotate rotate, Map pdfPageResources, PWord[] words, PFigure[] figures, PPath[] paths) {
			this.p = p;
			this.pdfPage = pdfPage;
			this.pdfPageBox = pdfPageBox;
			this.pdfPageContentBox = pdfPageContentBox;
			this.rotate = 0;//rotate.degrees;
			this.words = words;
			this.figures = figures;
			this.paths = paths;
			this.pdfPageResources = pdfPageResources;
		}
		PPageData(int p, Page pdfPage, Rectangle2D.Float pdfPageBox, Rectangle2D.Float pdfPageContentBox, int rotate, Map pdfPageResources, PWord[] words, PFigure[] figures, PPath[] paths) {
			this.p = p;
			this.pdfPage = pdfPage;
			this.pdfPageBox = pdfPageBox;
			this.pdfPageContentBox = pdfPageContentBox;
			this.rotate = 0;//rotate;
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
		float getRawDpi(Rectangle2D.Float pageBounds, boolean debug) {
			float dpiRatioWidth = (((float) this.image.getWidth()) / this.bounds.width);
			float dpiRatioHeight = (((float) this.image.getHeight()) / this.bounds.height);
			if (debug) System.out.println("Image DPI ratios are " + dpiRatioWidth + " / " + dpiRatioHeight);
			if ((pageBounds != null) && ((dpiRatioWidth * defaultDpi) < 100) && ((dpiRatioHeight * defaultDpi) < 100)) {
				dpiRatioWidth = (((float) this.image.getWidth()) / pageBounds.width);
				dpiRatioHeight = (((float) this.image.getHeight()) / pageBounds.height);
				if (debug) System.out.println("Page DPI ratios are " + dpiRatioWidth + " / " + dpiRatioHeight);
			}
			float dpiRatio = ((dpiRatioWidth + dpiRatioHeight) / 2);
			if (debug) System.out.println("DPI ratio is " + dpiRatio);
			float rawDpi = dpiRatio * defaultDpi;
			if (debug) System.out.println("Raw DPI is " + rawDpi);
			return rawDpi;
		}
	}
	
	private static final int getWords = 0x1;
	private static final int getFigures = 0x2;
	private static final int getPaths = 0x4;
	private PPageData[] getPdfPageData(ImDocument doc, int getWhat, FontDecoderCharset fontCharSet, boolean wordsAreOcr, Set pageIDs, PageTree pageTree, Map objects, ProgressMonitor pm) throws IOException {
		Tokenizer tokenizer = ((Tokenizer) doc.getAttribute(ImDocument.TOKENIZER_ATTRIBUTE, Gamta.INNER_PUNCTUATION_TOKENIZER));
		return this.getPdfPageData(tokenizer, getWhat, fontCharSet, wordsAreOcr, pageIDs, pageTree, objects, pm);
	}
	private PPageData[] getPdfPageData(final Tokenizer tokenizer, final int getWhat, final FontDecoderCharset fontCharSet, final boolean wordsAreOcr, final Set pageIDs, final PageTree pageTree, final Map objects, ProgressMonitor pm) throws IOException {
		
		//	build progress monitor with synchronized methods instead of synchronizing in-code
		final ProgressMonitor spm = ((pm instanceof SynchronizedProgressMonitor) ? pm : new SynchronizedProgressMonitor(pm));
		
		//	prepare working in parallel
		ParallelFor pf;
		
		//	extract page objects
		spm.setStep("Extracting page content");
		spm.setBaseProgress(0);
		spm.setProgress(0);
		spm.setMaxProgress(5);
		final Page[] pdfPages = new Page[pageTree.getNumberOfPages()];
		final byte[][] pdfPageContents = new byte[pageTree.getNumberOfPages()][];
		final Map[] pdfPageResources = new Map[pageTree.getNumberOfPages()];
		pf = new ParallelFor() {
			public void doFor(int p) throws Exception {
				
				//	are we interested in this one?
				if ((pageIDs != null) && (pageIDs.size() != 0) && !pageIDs.contains(new Integer(p)))
					return;
				
				//	update status display (might be inaccurate, but better than lock escalation)
				spm.setInfo("Importing page " + p + " of " + pdfPages.length);
				spm.setProgress((p * 100) / pdfPages.length);
				
				//	get page bounding box
				pdfPages[p] = pageTree.getPage(p);
				
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
						if (baos.size() < 64)
							System.out.println("   ==> short page content: " + new String(baos.toByteArray()) + " " + Arrays.toString(baos.toByteArray()));
					}
					else if (contentsObj instanceof List) {
						spm.setInfo("   --> array content");
						for (Iterator cit = ((List) contentsObj).iterator(); cit.hasNext();) {
							Object contentObjId = cit.next();
							Object contentObj = PdfParser.dereference(contentObjId, objects);
							spm.setInfo("     - " + contentObjId + " [" + contentObjId.getClass().getName() + "]" + " --> " + contentObj);
							if (contentObj instanceof PStream) {
								Object filter = ((PStream) contentObj).params.get("Filter");
//								if (filter == null)
//									continue; // MUST NOT FILTER THIS (some PDFs actually do come with non-compressed content ...)
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
						pdfPageResources[p] = ((Map) resourcesObj);
					}
				}
			}
		};
		ParallelJobRunner.runParallelFor(pf, ((PdfExtractorTest.aimAtPage < 0) ? 0 : PdfExtractorTest.aimAtPage), ((PdfExtractorTest.aimAtPage < 0) ? pdfPages.length : (PdfExtractorTest.aimAtPage + 1)), (this.useMultipleCores ? -1 : 1));
		spm.setProgress(100);
		
		//	check errors
		checkException(pf);
		
		//	assess which chars in which fonts are actually used (no use decoding all the others, that do actually exist in some PDFs)
		if (((getWhat & getWords) != 0) && (fontCharSet != PdfFontDecoder.NO_DECODING)) {
			spm.setStep("Assessing font char usage");
			spm.setBaseProgress(5);
			spm.setProgress(0);
			spm.setMaxProgress(10);
			for (int p = 0; p < pdfPages.length; p++) {
				
				//	nothing to work with
				if ((pdfPageContents[p] == null) || (pdfPageResources[p] == null) || (pdfPages[p] == null))
					continue;
				
				//	update status display (might be inaccurate, but better than lock escalation)
				spm.setInfo("Assessing font char usage in page " + p + " of " + pdfPages.length);
				spm.setProgress((p * 100) / pdfPages.length);
				
				//	check rotation
				PRotate rotate = new PRotate(pdfPages[p], objects);
				spm.setInfo(" - page rotation is " + rotate);
				
				//	assess char usage
				PdfParser.getPageWordChars(pdfPages[p].getEntries(), pdfPageContents[p], rotate, pdfPageResources[p], objects, wordsAreOcr, spm);
			}
//			WE CANNOT RUN THIS IN PARALLEL, FONTS START DOING FUNNY THINGS ...
//			pf = new ParallelFor() {
//				public void doFor(int p) throws Exception {
//					
//					//	nothing to work with
//					if ((pdfPageContents[p] == null) || (pdfPageResources[p] == null) || (pdfPages[p] == null))
//						return;
//					
//					//	update status display (might be inaccurate, but better than lock escalation)
//					spm.setInfo("Assessing font char usage in page " + p + " of " + pdfPages.length);
//					spm.setProgress((p * 100) / pdfPages.length);
//					
//					//	assess char usage
//					PdfParser.getPageWordChars(pdfPages[p].getEntries(), pdfPageContents[p], pdfPageResources[p], objects, spm);
//				}
//			};
//			ParallelJobRunner.runParallelFor(pf, ((PdfExtractorTest.aimAtPage < 0) ? 0 : PdfExtractorTest.aimAtPage), ((PdfExtractorTest.aimAtPage < 0) ? pdfPages.length : (PdfExtractorTest.aimAtPage + 1)), (this.useMultipleCores ? -1 : 1));
			spm.setProgress(100);
			
			//	check errors
			checkException(pf);
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
				if (pdfPages[p] == null)
					return;
				
				//	update status display (might be inaccurate, but better than lock escalation)
				spm.setInfo("Importing page " + p + " of " + pdfPages.length);
				spm.setProgress((p * 100) / pdfPages.length);
				
				//	get page bounding box
				Rectangle2D.Float pdfPageBox = pdfPages[p].getMediaBox();
				if (pdfPageBox == null)
					pdfPageBox = pdfPages[p].getCropBox();
				spm.setInfo(" - page box is " + pdfPageBox);
				Rectangle2D.Float pdfPageContentBox = pdfPages[p].getCropBox();
				if (pdfPageContentBox == null)
					pdfPageContentBox = pdfPageBox;
				spm.setInfo(" - page content box is " + pdfPageContentBox);
				
				//	nothing to work with, add with dummy data and we're done
				if ((pdfPageContents[p] == null) || (pdfPageResources[p] == null)) {
					pData[p] = new PPageData(p, pdfPages[p], pdfPageBox, pdfPageContentBox, 0, pdfPageResources[p], new PWord[0], new PFigure[0], new PPath[0]);
					return;
				}
				
				//	check rotation
				Object rotateObj = PdfParser.dereference(pdfPages[p].getEntries().get("Rotate"), objects);
				int rotateDegrees = ((rotateObj instanceof Number) ? ((Number) rotateObj).intValue() : 0);
				spm.setInfo(" - page rotation is " + rotateDegrees);
				if ((rotateDegrees == 90) || (rotateDegrees == -90) || (rotateDegrees == 270) || (rotateDegrees == -270)) {
					if (pdfPageContentBox != pdfPageBox) {
						pdfPageContentBox = new Rectangle2D.Float(0, pdfPageContentBox.width, pdfPageContentBox.height, pdfPageContentBox.width); // IcePDF uses top left corner, and we've built everything around that
						spm.setInfo(" - page content box now is " + pdfPageContentBox);
					}
					pdfPageBox = new Rectangle2D.Float(0, pdfPageBox.width, pdfPageBox.height, pdfPageBox.width); // IcePDF uses top left corner, and we've built everything around that
					spm.setInfo(" - page box now is " + pdfPageBox);
				}
				PRotate rotate = new PRotate(pdfPages[p], objects);
				
				//	get words only
				if (getWhat == getWords) {
					PWord[] pWords = PdfParser.getPageWords(pdfPages[p].getEntries(), pdfPageContents[p], rotate, pdfPageResources[p], objects, tokenizer, wordsAreOcr, spm);
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
					PPageContent pContent = PdfParser.getPageContent(pdfPages[p].getEntries(), pdfPageContents[p], rotate, pdfPageResources[p], objects, tokenizer, fontCharSet, wordsAreOcr, spm);
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
					PFigure[] pFigures = PdfParser.getPageFigures(pdfPages[p].getEntries(), pdfPageContents[p], rotate, pdfPageResources[p], objects, spm);
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
					PPath[] pPaths = PdfParser.getPagePaths(pdfPages[p].getEntries(), pdfPageContents[p], rotate, pdfPageResources[p], objects, spm);
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
					PPageContent pContent = PdfParser.getPageGraphics(pdfPages[p].getEntries(), pdfPageContents[p], rotate, pdfPageResources[p], objects, spm);
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
		checkException(pf);
		
		//	finally ...
		return pData;
	}
	
	private BufferedImage getFigureImage(ImDocument doc, int pageId, PFigure pFigure, boolean isMainFigure, boolean isSoftMask, Page pdfPage, Rectangle2D.Float pdfPageBox, Map objects, float magnification, Map figureSupplementIDs, BufferedImage[] pdfFigureImage, ProgressMonitor spm) throws IOException {
		
		//	figure consists of sub figures
		if (pFigure.subFigures != null) {
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println("Rendering figure from sub images");
			
			//	render sub figures recursively, and compute average resolution
			//	TODOnot maybe weight resolution by extent in dimensions, to cushion rounding errors
			//	TODOne catch figures whose parts come at varying resolutions
			BufferedImage[] pFigureSubImages = new BufferedImage[pFigure.subFigures.length];
			float widthRatioSum = 0;
			float heightRatioSum = 0;
			int ratioCount = 0;
			for (int s = 0; s < pFigure.subFigures.length; s++) {
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println(" - rendering sub image " + pFigure.subFigures[s].name);
				pFigureSubImages[s] = this.getFigureImage(null, -1, pFigure.subFigures[s], false, false, pdfPage, pdfPageBox, objects, magnification, null, null, spm);
				if (pFigureSubImages[s] == null) {
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println("   - sub image failed to decode or is blank");
					continue;
				}
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println("   - sub image size is " + pFigureSubImages[s].getWidth() + "x" + pFigureSubImages[s].getHeight());
				float widthRatio = ((float) (((float) pFigureSubImages[s].getWidth()) / pFigure.subFigures[s].bounds.getWidth()));
				widthRatioSum += widthRatio;
				float heightRatio = ((float) (((float) pFigureSubImages[s].getHeight()) / pFigure.subFigures[s].bounds.getHeight()));
				heightRatioSum += heightRatio;
				ratioCount++;
				float dpiRatio = ((widthRatio + heightRatio) / 2);
				float rawDpi = (dpiRatio * defaultDpi);
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println("   - resolution is " + rawDpi + " DPI");
				BoundingBox dpiBox = getBoundingBox(pFigure.subFigures[s].bounds, pdfPageBox, dpiRatio, 0);
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println("   - bounding box at DPI is " + dpiBox);
			}
			if (ratioCount == 0) {
				spm.setInfo("   --> omitting blank decoded figure");
				return null;
			}
			float avgRawDpiRatio = ((widthRatioSum + heightRatioSum) / (2 * ratioCount));
			float avgRawDpi = (avgRawDpiRatio * defaultDpi);
			int avgDpi = Math.round((float) (Math.ceil(avgRawDpi / 10) * 10));
			float avgDpiRatio = (((float) avgDpi) / defaultDpi);
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" - average resolution is " + avgDpi + " DPI (" + avgRawDpi + ")");
			BoundingBox avgDpiBox = getBoundingBox(pFigure.bounds, pdfPageBox, avgDpiRatio, 0);
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" - bounding box at DPI is " + avgDpiBox);
			
			//	assemble sub figures
			BufferedImage pFigureImage = new BufferedImage(avgDpiBox.getWidth(), avgDpiBox.getHeight(), BufferedImage.TYPE_INT_ARGB);
			Graphics2D pFigureGraphics = pFigureImage.createGraphics();
			pFigureGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			for (int s = 0; s < pFigure.subFigures.length; s++) {
				if (pFigureSubImages[s] == null)
					continue;
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println("   - sub image size is " + pFigureSubImages[s].getWidth() + "x" + pFigureSubImages[s].getHeight());
				BoundingBox dpiBox = getBoundingBox(pFigure.subFigures[s].bounds, pdfPageBox, avgDpiRatio, 0);
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println("   - bounding box at average DPI is " + dpiBox);
				pFigureGraphics.drawImage(pFigureSubImages[s], (dpiBox.left - avgDpiBox.left), (dpiBox.top - avgDpiBox.top), dpiBox.getWidth(), dpiBox.getHeight(), null);
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
			BoundingBox pFigureBox = getBoundingBox(pFigure.bounds, pdfPageBox, magnification, 0);
			spm.setInfo(" - rendering bounds are " + pFigureBox);
			
			//	add figures as supplements to document if required (synchronized !!!)
			if (doc != null) synchronized (doc) {
				ImSupplement.Figure figureSupplement = ImSupplement.Figure.createFigure(doc, pageId, pFigure.renderOrderNumber, pFigureDpi, pFigureImage, pFigureBox);
				if (figureSupplementIDs != null)
					figureSupplementIDs.put(pFigure, figureSupplement.getId());
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
		BufferedImage pFigureImage = this.decodeImage(pdfPage, ((PStream) pFigureData).bytes, ((PStream) pFigureData).params, objects, false, isSoftMask);
		if (pFigureImage == null) {
			spm.setInfo("   --> could not decode figure");
			return null;
		}
		spm.setInfo("     - got figure sized " + pFigureImage.getWidth() + " x " + pFigureImage.getHeight() + ", type is " + pFigureImage.getType());
		spm.setInfo("     - figure bounds are " + pFigure.bounds);
		spm.setInfo("     - clipping bounds are " + pFigure.visibleBounds);
		
		//	check rotation
		Object rotateObj = PdfParser.dereference(pdfPage.getEntries().get("Rotate"), objects);
		int rotate = ((rotateObj instanceof Number) ? ((Number) rotateObj).intValue() : 0);
		spm.setInfo("     - page rotation is " + rotate);
//		
//		//	apply any soft masks
//		//	TODOne observe transformation matrices for soft mask images ...
//		//	TODOne ... and apply soft masks to final transformed images only
//		if (pFigure.softMasks != null) {
//			spm.setInfo("     - applying " + pFigure.softMasks.length + " clipping soft masks");
//			BoundingBox pFigureBox = getBoundingBox(pFigure.bounds, pdfPageBox, magnification, rotate);
//			for (int sm = 0; sm < pFigure.softMasks.length; sm++) {
//				BoundingBox pFigureSmBox = getBoundingBox(pFigure.softMasks[sm].bounds, pdfPageBox, magnification, rotate);
//				spm.setInfo("       - " + pFigureSmBox + ": " + pFigure.softMasks[sm].name);
//				Object smData = PdfParser.dereference(pFigure.softMasks[sm].refOrData, objects);
//				if (smData instanceof PStream) {
//					BufferedImage smImage = this.decodeImage(pdfPage, ((PStream) smData).bytes, ((PStream) smData).params, objects, false, true);
//					pFigureImage = applySoftMaskImage(pFigureImage, pFigureBox, smImage, pFigureSmBox);
//				}
//			}
//		}
		
		//	test for blank image only now, as we have all the data for the page image fallback
		if (checkEmptyImage(pFigureImage)) {
			spm.setInfo("   --> omitting blank decoded figure");
			return null;
		}
		
		//	store original image (for debugging)
		if (pdfFigureImage != null)
			pdfFigureImage[0] = pFigureImage;
		
		//	check brightness
		float brightness = getBrightness(pFigureImage, false);
		float brightnessInv = getBrightness(pFigureImage, true);
		spm.setInfo("     - brightness is " + brightness + "/" + brightnessInv);
		
		//	correct right side left ...
		if (pFigure.transform.isRightSideLeft()) {
			BufferedImage fImage = new BufferedImage(pFigureImage.getWidth(), pFigureImage.getHeight(), ((pFigureImage.getType() == BufferedImage.TYPE_CUSTOM) ? BufferedImage.TYPE_INT_ARGB : pFigureImage.getType()));
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
		else if (pFigure.transform.isTopSideDown()) {
			BufferedImage fImage = new BufferedImage(pFigureImage.getWidth(), pFigureImage.getHeight(), ((pFigureImage.getType() == BufferedImage.TYPE_CUSTOM) ? BufferedImage.TYPE_INT_ARGB : pFigureImage.getType()));
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
//		
//		//	turn by full multiple of 90 degrees
//		else if (pFigure.transform.getQuadrantRotation() > 0) {
//			BufferedImage rImage;
//			
//			//	rotation by two quadrants, retain dimensions
//			if (pFigure.transform.getQuadrantRotation() == 2)
//				rImage = new BufferedImage(pFigureImage.getWidth(), pFigureImage.getHeight(), ((pFigureImage.getType() == BufferedImage.TYPE_CUSTOM) ? BufferedImage.TYPE_INT_ARGB : pFigureImage.getType()));
//			
//			//	flip dimensions for rotation by one or three quadrants
//			else rImage = new BufferedImage(pFigureImage.getHeight(), pFigureImage.getWidth(), ((pFigureImage.getType() == BufferedImage.TYPE_CUSTOM) ? BufferedImage.TYPE_INT_ARGB : pFigureImage.getType()));
//			
//			//	perform rotation (reverse direction of PDF, as PDF coordinate space has Y=0 at bottom)
//			Graphics2D rImageGraphics = rImage.createGraphics();
//			rImageGraphics.setColor(Color.WHITE);
//			rImageGraphics.fillRect(0, 0, rImage.getWidth(), rImage.getHeight());
//			rImageGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
//			rImageGraphics.translate(((rImage.getWidth() - pFigureImage.getWidth()) / 2), ((rImage.getHeight() - pFigureImage.getHeight()) / 2));
//			rImageGraphics.rotate(((4 - pFigure.transform.getQuadrantRotation()) * (Math.PI / 2)), (pFigureImage.getWidth() / 2), (pFigureImage.getHeight() / 2));
//			rImageGraphics.drawRenderedImage(pFigureImage, null);
//			rImageGraphics.dispose();
//			pFigureImage = rImage;
//			spm.setInfo("     - figure rotated by " + (90 * (4 - pFigure.transform.getQuadrantRotation())) + "°");
//			spm.setInfo("     - figure sized " + pFigureImage.getWidth() + " x " + pFigureImage.getHeight() + " now, type is " + pFigureImage.getType());
//		}
		
		//	modify figure image via its transformation matrix
		else if (!pFigure.transform.isPlainScaleTranslate()) {
			spm.setInfo("     - transforming image");
			AffineTransform at = pFigure.transform.getAffineTransform();
			spm.setInfo("     - transformation matrix is " + at);
			spm.setInfo("     - inversion is " + pFigure.transform.containsInversion());
			spm.setInfo("     - rotation is " + pFigure.transform.getRotation() + " (" + pFigure.transform.getRotation1() + "/" + pFigure.transform.getRotation2() + ")");
			double rotationDiff = (pFigure.transform.getRotation1() - pFigure.transform.getRotation2());
			spm.setInfo("     - rotation diff is " + rotationDiff);
			double piRotationDiff = (rotationDiff - Math.PI);
			if (piRotationDiff < -Math.PI)
				piRotationDiff += (Math.PI * 2);
			spm.setInfo("     - PI rotation diff is " + piRotationDiff);
			boolean isMirroring = (Math.abs(piRotationDiff) < Math.abs(rotationDiff));
			spm.setInfo("       ==> mirroring is " + isMirroring);
			
			//	compute transformed width and height
			Point2D pTransBottomLeft = at.transform(new Point2D.Float(0, 0), null);
			Point2D pTransBottomRight = at.transform(new Point2D.Float(1, 0), null);
			Point2D pTransTopLeft = at.transform(new Point2D.Float(0, 1), null);
			Point2D pTransTopRight = at.transform(new Point2D.Float(1, 1), null);
			spm.setInfo("     - PDF bottom left transforms to " + pTransBottomLeft);
			spm.setInfo("     - PDF bottom right transforms to " + pTransBottomRight);
			spm.setInfo("     - PDF top left transforms to " + pTransTopLeft);
			spm.setInfo("     - PDF top right transforms to " + pTransTopRight);
			double pTransWidth = ((
					Math.sqrt(
						((pTransBottomRight.getX() - pTransBottomLeft.getX()) * (pTransBottomRight.getX() - pTransBottomLeft.getX()))
						+
						((pTransBottomRight.getY() - pTransBottomLeft.getY()) * (pTransBottomRight.getY() - pTransBottomLeft.getY()))
					)
					+ 
					Math.sqrt(
						((pTransTopRight.getX() - pTransTopLeft.getX()) * (pTransTopRight.getX() - pTransTopLeft.getX()))
						+
						((pTransTopRight.getY() - pTransTopLeft.getY()) * (pTransTopRight.getY() - pTransTopLeft.getY()))
					)
				) / 2);
			double pTransHeight = ((
					Math.sqrt(
						((pTransTopLeft.getX() - pTransBottomLeft.getX()) * (pTransTopLeft.getX() - pTransBottomLeft.getX()))
						+
						((pTransTopLeft.getY() - pTransBottomLeft.getY()) * (pTransTopLeft.getY() - pTransBottomLeft.getY()))
					)
					+ 
					Math.sqrt(
						((pTransTopRight.getX() - pTransBottomRight.getX()) * (pTransTopRight.getX() - pTransBottomRight.getX()))
						+
						((pTransTopRight.getY() - pTransBottomRight.getY()) * (pTransTopRight.getY() - pTransBottomRight.getY()))
					)
				) / 2);
			spm.setInfo("     - PDF extent transforms to " + pTransWidth + " x " + pTransHeight);
			
			//	compute transformed resolution, and swap dimensions if figure turns around 90° or 270°
			float pTransDpiRatio;
			if ((pFigureImage.getWidth() <= pFigureImage.getHeight()) == (pTransWidth <= pTransHeight))
				pTransDpiRatio = ((float) ((((float) pFigureImage.getWidth()) / pTransWidth) + (((float) pFigureImage.getHeight()) / pTransHeight)) / 2);
			else pTransDpiRatio = ((float) ((((float) pFigureImage.getHeight()) / pTransWidth) + (((float) pFigureImage.getWidth()) / pTransHeight)) / 2);
			float pTransRawDpi = pTransDpiRatio * defaultDpi;
			int pTransFigureDpi = (Math.round(pTransRawDpi / 10) * 10);
			spm.setInfo("     - PDF transformed resolution computed as " + pTransFigureDpi + " DPI (" + pTransRawDpi + ")");
			
			/*	TODOne WTF: vertebrateZoology.70.1.23-59.pdf
			 * page 8:
				- double inversion gets Im2, Im3, Im4, Im5
				  errors: Im1, Im6, Im0 rotated wrong way
				- no double inversion gets Im1, Im2, Im4, Im5, Im0, Im6
				  errors: Im3 rotated wrong way (nose slightly up)
				==> Im4, Im5 don't go through matrices at all
				
			 * page 9:
				- double inversion gets Im3, Im4, Im5, Im6
				  errors: Im1, Im2, Im0 rotated wrong way
				- no double inversion gets Im1, Im2, Im5
				  errors: Im3, Im4, Im6 rotated wrong way
				==> Im5 doesn't go through matrices at all
				
				TODOne idea: invert and revert top and bottom depending on mirroring about vertical axis ... might do the trick ...
				==> it DOES do the trick
			*/
			
//			if (pFigure.transform.containsInversion())
			if (isMirroring)
				at.concatenate(AffineTransform.getScaleInstance(1, -1)); // need to invert top and bottom, as PDF coordinate space has Y=0 at bottom (important to maintain angle of inversion axis)
			at.preConcatenate(AffineTransform.getTranslateInstance(-pFigure.bounds.x, -pFigure.bounds.y)); // need to move back to (0,0), as bitmap only comprises image, not whole page
			at.preConcatenate(AffineTransform.getScaleInstance((1.0 / pFigureImage.getWidth()), (1.0 / pFigureImage.getHeight()))); // need to scale figure to 1x1 before transforming
//			if (pFigure.transform.containsInversion())
			if (isMirroring)
				at.preConcatenate(AffineTransform.getScaleInstance(1, -1)); // need to invert back top and bottom, as original bitmap is upright
			at.preConcatenate(AffineTransform.getScaleInstance(pTransDpiRatio, pTransDpiRatio)); // scale to original resolution
			
			//	measure dimensions of rotated image
			Point2D transTopLeft = at.transform(new Point2D.Float(0, 0), null);
			Point2D transTopRight = at.transform(new Point2D.Float(pFigureImage.getWidth(), 0), null);
			Point2D transBottomLeft = at.transform(new Point2D.Float(0, pFigureImage.getHeight()), null);
			Point2D transBottomRight = at.transform(new Point2D.Float(pFigureImage.getWidth(), pFigureImage.getHeight()), null);
			spm.setInfo("     - top left transforms to " + transTopLeft);
			spm.setInfo("     - top right transforms to " + transTopRight);
			spm.setInfo("     - bottom left transforms to " + transBottomLeft);
			spm.setInfo("     - bottom right transforms to " + transBottomRight);
			double transMinX = Math.min(Math.min(transTopLeft.getX(), transTopRight.getX()), Math.min(transBottomLeft.getX(), transBottomRight.getX()));
			double transMaxX = Math.max(Math.max(transTopLeft.getX(), transTopRight.getX()), Math.max(transBottomLeft.getX(), transBottomRight.getX()));
			double transMinY = Math.min(Math.min(transTopLeft.getY(), transTopRight.getY()), Math.min(transBottomLeft.getY(), transBottomRight.getY()));
			double transMaxY = Math.max(Math.max(transTopLeft.getY(), transTopRight.getY()), Math.max(transBottomLeft.getY(), transBottomRight.getY()));
			int transWidth = (((int) Math.round(transMaxX - transMinX)) + 2); // one pixel of safety margin on either side
			int transHeight = (((int) Math.round(transMaxY - transMinY)) + 2); // one pixel of safety margin on either side
			spm.setInfo("     - transformed width is " + transWidth);
			spm.setInfo("     - transformed height is " + transHeight);
			
			//	make sure to render in bounds (plus one pixel of safety margin)
			at.preConcatenate(AffineTransform.getTranslateInstance((-transMinX + 1), (-transMinY + 1)));
			spm.setInfo("     - compensation translated by " + (-transMinX + 1) + "/" + (-transMinY + 1));
			transTopLeft = at.transform(new Point2D.Float(0, 0), transTopLeft);
			transTopRight = at.transform(new Point2D.Float(pFigureImage.getWidth(), 0), transTopRight);
			transBottomLeft = at.transform(new Point2D.Float(0, pFigureImage.getHeight()), transBottomLeft);
			transBottomRight = at.transform(new Point2D.Float(pFigureImage.getWidth(), pFigureImage.getHeight()), transBottomRight);
			spm.setInfo("     - top left compensated transforms to " + transTopLeft);
			spm.setInfo("     - top right compensated transforms to " + transTopRight);
			spm.setInfo("     - bottom left compensated transforms to " + transBottomLeft);
			spm.setInfo("     - bottom right compensated transforms to " + transBottomRight);
			
			//	transform image
			BufferedImage tImage = new BufferedImage(transWidth, transHeight, ((pFigureImage.getType() == BufferedImage.TYPE_CUSTOM) ? BufferedImage.TYPE_INT_ARGB : pFigureImage.getType()));
			Graphics2D tImageGraphics = tImage.createGraphics();
			tImageGraphics.setColor(Color.WHITE);
			tImageGraphics.fillRect(0, 0, tImage.getWidth(), tImage.getHeight());
			tImageGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			tImageGraphics.setTransform(at);
			tImageGraphics.drawRenderedImage(pFigureImage, null);
			tImageGraphics.dispose();
			
			//	trim transformed image to size
			int pFigureWidth;
			int pFigureHeight;
			if ((tImage.getWidth() <= tImage.getHeight()) == (pFigureImage.getWidth() <= pFigureImage.getHeight())) {
				pFigureWidth = pFigureImage.getWidth();
				pFigureHeight = pFigureImage.getHeight();
			}
			else {
				pFigureWidth = pFigureImage.getHeight();
				pFigureHeight = pFigureImage.getWidth();
			}
			if ((tImage.getWidth() > pFigureWidth) || (tImage.getHeight() > pFigureHeight)) {
				AnalysisImage pAi = Imaging.wrapImage(tImage, null);
				byte[][] pAiBrightness = pAi.getBrightness();
				boolean[] isWhiteCol = new boolean[tImage.getWidth()];
				Arrays.fill(isWhiteCol, true);
				boolean[] isWhiteRow = new boolean[tImage.getHeight()];
				Arrays.fill(isWhiteRow, true);
				for (int c = 0; c < pAiBrightness.length; c++) {
					for (int r = 0; r < pAiBrightness[c].length; r++)
						if (pAiBrightness[c][r] < 126) {
							isWhiteCol[c] = false;
							isWhiteRow[r] = false;
						}
				}
				int whiteLeft = 0;
				for (int c = 0; c < pAiBrightness.length; c++) {
					if (isWhiteCol[c])
						whiteLeft++;
					else break;
				}
				int whiteRight = 0;
				for (int c = (isWhiteCol.length-1); c >= 0; c--) {
					if (isWhiteCol[c])
						whiteRight++;
					else break;
				}
				int whiteTop = 0;
				for (int r = 0; r < isWhiteRow.length; r++) {
					if (isWhiteRow[r])
						whiteTop++;
					else break;
				}
				int whiteBottom = 0;
				for (int r = (isWhiteRow.length-1); r >= 0; r--) {
					if (isWhiteRow[r])
						whiteBottom++;
					else break;
				}
				spm.setInfo("     - found core image of " + (tImage.getWidth() - whiteLeft - whiteRight) + " x " + (tImage.getHeight() - whiteTop - whiteBottom));
				int cutLeft = 0;
				int cutRight = 0;
				int cutTop = 0;
				int cutBottom = 0;
				if ((whiteLeft + whiteRight) > 0) {
					int cutWidth = (tImage.getWidth() - pFigureWidth);
					cutLeft = Math.max(((cutWidth * whiteLeft) / (whiteLeft + whiteRight)), 0);
					cutRight = Math.max(((cutWidth * whiteRight) / (whiteLeft + whiteRight)), 0);
					while ((cutLeft + cutRight) < cutWidth) {
						if (((whiteLeft - cutLeft) < (whiteRight - cutRight)) && (cutRight < whiteRight))
							cutRight++;
						else if (((whiteRight - cutRight) < (whiteLeft - cutLeft)) && (cutLeft < whiteLeft))
							cutLeft++;
						else if (cutRight < whiteRight)
							cutRight++;
						else if (cutLeft < whiteLeft)
							cutLeft++;
						else break;
					}
				}
				if ((whiteLeft + whiteRight) > 0) {
					int cutHeight = (tImage.getHeight() - pFigureHeight);
					cutTop = Math.max(((cutHeight * whiteTop) / (whiteTop + whiteBottom)), 0);
					cutBottom = Math.max(((cutHeight * whiteBottom) / (whiteTop + whiteBottom)), 0);
					while ((cutTop + cutBottom) < cutHeight) {
						if (((whiteTop - cutTop) < (whiteBottom - cutBottom)) && (cutBottom < whiteBottom))
							cutBottom++;
						else if (((whiteBottom - cutBottom) < (whiteTop - cutTop)) && (cutTop < whiteTop))
							cutTop++;
						else if (cutBottom < whiteBottom)
							cutBottom++;
						else if (cutTop < whiteTop)
							cutTop++;
						else break;
					}
				}
				tImage = tImage.getSubimage(cutLeft, cutTop, (tImage.getWidth() - (cutLeft + cutRight)), (tImage.getHeight() - (cutTop + cutBottom)));
				spm.setInfo("     - figure truncated to " + tImage.getWidth() + " x " + tImage.getHeight());
			}
			
			//	finally ...
			pFigureImage = tImage;
			spm.setInfo("     - figure transformed");
			spm.setInfo("     - figure sized " + pFigureImage.getWidth() + " x " + pFigureImage.getHeight() + " now, type is " + pFigureImage.getType());
		}
		
		//	paint image on white canvas for type normalization
		else if (pFigureImage.getType() == BufferedImage.TYPE_CUSTOM) {
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
		spm.setInfo("     - clipping bounds are " + pFigure.visibleBounds);
		float dpiRatio = ((float) ((((float) pFigureImage.getWidth()) / pFigure.bounds.getWidth()) + (((float) pFigureImage.getHeight()) / pFigure.bounds.getHeight())) / 2);
		float rawDpi = dpiRatio * defaultDpi;
		int pFigureDpi = (Math.round(rawDpi / 10) * 10);
		spm.setInfo("     - resolution computed as " + pFigureDpi + " DPI (" + rawDpi + ")");
		
		//	if we're above 5 times our page image rendering DPI, scale down half, third, etc., until at most that
		if (pFigureDpi > (5 * this.textPdfPageImageDpi)) {
			int scaleDown = 1;
			while (pFigureDpi > (5 * this.textPdfPageImageDpi * scaleDown))
				scaleDown++;
			spm.setInfo("     - scaling down by factor " + scaleDown);
			BufferedImage sImage = new BufferedImage((pFigureImage.getWidth() / scaleDown), (pFigureImage.getHeight() / scaleDown), (pFigureImage.getType() == BufferedImage.TYPE_CUSTOM) ? BufferedImage.TYPE_INT_ARGB : pFigureImage.getType());
			Graphics2D sImageGraphics = sImage.createGraphics();
			sImageGraphics.setColor(Color.WHITE);
			sImageGraphics.fillRect(0, 0, sImage.getWidth(), sImage.getHeight());
			sImageGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			sImageGraphics.drawRenderedImage(pFigureImage, AffineTransform.getScaleInstance((1.0 / scaleDown), (1.0 / scaleDown)));
			sImageGraphics.dispose();
			pFigureImage = sImage;
			dpiRatio = ((float) ((((float) pFigureImage.getWidth()) / pFigure.bounds.getWidth()) + (((float) pFigureImage.getHeight()) / pFigure.bounds.getHeight())) / 2);
			rawDpi = dpiRatio * defaultDpi;
			pFigureDpi = (Math.round(rawDpi / 10) * 10);
			spm.setInfo("     - resolution scaled down to " + pFigureDpi + " DPI (" + rawDpi + ")");
		}
		
		//	convert bounds, as PDF Y coordinate is bottom-up, whereas Java, JavaScript, etc. Y coordinate is top-down
		BoundingBox pFigureBox = getBoundingBox(pFigure.bounds, pdfPageBox, magnification, rotate);
		spm.setInfo("     - rendering bounds are " + pFigureBox);
		BoundingBox pFigureClipBox = getBoundingBox(pFigure.visibleBounds, pdfPageBox, magnification, rotate);
		//	TODO observe clipping (maybe even actually clip bitmap)
		spm.setInfo("     - clipping bounds are " + pFigureClipBox);
		
		//	apply any soft masks
		if (!isSoftMask && (pFigure.softMasks != null)) {
			spm.setInfo("     - applying " + pFigure.softMasks.length + " clipping soft masks");
			for (int sm = 0; sm < pFigure.softMasks.length; sm++) {
				BoundingBox pFigureSmBox = getBoundingBox(pFigure.softMasks[sm].bounds, pdfPageBox, magnification, rotate);
				spm.setInfo("       - " + pFigureSmBox + ": " + pFigure.softMasks[sm].name);
				BufferedImage smImage = this.getFigureImage(null, -1, pFigure.softMasks[sm], true, true, pdfPage, pdfPageBox, objects, magnification, figureSupplementIDs, null, spm);
				if (smImage != null)
					pFigureImage = applySoftMaskImage(pFigureImage, pFigureBox, smImage, pFigureSmBox);
			}
		}
		
		//	add figures as supplements to document if requested (synchronized !!!)
		if (doc != null) synchronized (doc) {
			try {
				ImSupplement.Figure figureSupplement = ImSupplement.Figure.createFigure(doc, pageId, pFigure.renderOrderNumber, pFigureDpi, pFigureImage, pFigureBox, pFigureClipBox);
				if (figureSupplementIDs != null)
					figureSupplementIDs.put(pFigure, figureSupplement.getId());
				if ((pFigure.refOrData instanceof PStream) && ((PStream) pFigure.refOrData).params.containsKey("ImageMask"))
					figureSupplement.setAttribute(ImSupplement.Figure.IMAGE_MASK_MARKER_ATTRIBUTE);
			}
			catch (Exception e) /* somehow Java fails to output some figures as PNGs  */ {
				e.printStackTrace(System.out);
			}
		}
		
		//	finally ...
		return pFigureImage;
	}
	
	private static float getBrightness(BufferedImage bi, boolean invert) {
		if (bi == null)
			return 0;
		
		int rgb;
		float[] hsb = new float[3];
		double bSum = 0;
		for (int x = 0; x < bi.getWidth(); x++)
			for (int y = 0; y < bi.getHeight(); y++) {
				rgb = bi.getRGB(x, y);
				hsb = Color.RGBtoHSB(((rgb >> 16) & 255), ((rgb >> 8) & 255), ((rgb >> 0) & 255), hsb);
				if (invert) {
					rgb = Color.HSBtoRGB(hsb[0], hsb[1], (1.0f - hsb[2]));
					hsb = Color.RGBtoHSB(((rgb >> 16) & 255), ((rgb >> 8) & 255), ((rgb >> 0) & 255), hsb);
				}
				bSum += hsb[2];
			}
		return ((float) (bSum / (bi.getWidth() * bi.getHeight())));
	}
	
	private static boolean checkEmptyImage(BufferedImage bi) {
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
	
	//	default according to PDF specification
	private static final int defaultDpi = 72;
	
	//	steps for scaling up if scanned page images come out at 72 DPI
	private static final int[] scaleUpDpiSteps = {150, 200, 300, 400, 500, 600};
	
	//	steps for rounding resolution of scanned page images (is there a 190 DPI scanner at all ?!?)
	private static final int[] commonScannedPageImageDpi = {150, 200, 225, 250, 300, 400, 500, 600};
	
	//	using double screen DPI improves scaling behavior at common zoom levels (as does anti-aliasing)
	private static final int defaultTextPdfPageImageDpi = (2 * 96); // looks a lot better
	private static final int minAveragePageWords = 25; // this should be exceeded by every digital-born PDF
	
	//	A4 paper format in inches: 8.27 x 11.66
	private static final float a4inchWidth = 8.27f;
	private static final float a4inchHeigth = 11.66f;
	
	//	A5 paper format in inches: 5.83 × 8.27
	private static final float a5inchWidth = 5.83f;
	private static final float a5inchHeigth = 8.27f;
	
	//	threshold for halving resolution on below-A5 sized pages
	private static final int minScaleUpDpi = 301;
	
	//	threshold for scaling down scans
	private static final int minScaleDownDpi = 401;
	
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
		return this.loadTextPdf(null, null, pdfBytes, PdfFontDecoder.UNICODE, null, pm);
	}
	
	/**
	 * Load a document from a textual PDF, usually a digital-born PDF. Page
	 * images are stored in the page image store handed to the constructor. The
	 * document structure is stored in the argument image markup document,
	 * including textual contents with word bounding boxes.
	 * @param pdfBytes the raw binary data the the PDF document was parsed from
	 * @param pageIDs a set containing the IDs of the pages to decode
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the image markup document
	 * @throws IOException
	 */
	public ImDocument loadTextPdf(byte[] pdfBytes, Set pageIDs, ProgressMonitor pm) throws IOException {
		return this.loadTextPdf(null, null, pdfBytes, PdfFontDecoder.UNICODE, pageIDs, pm);
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
		return this.loadTextPdf(null, null, pdfBytes, fontCharSet, null, pm);
	}
	
	/**
	 * Load a document from a textual PDF, usually a digital-born PDF. Page
	 * images are stored in the page image store handed to the constructor. The
	 * document structure is stored in the argument image markup document,
	 * including textual contents with word bounding boxes.
	 * @param pdfBytes the raw binary data the the PDF document was parsed from
	 * @param fontCharSet the character set to use for font decoding
	 * @param pageIDs a set containing the IDs of the pages to decode
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the image markup document
	 * @throws IOException
	 */
	public ImDocument loadTextPdf(byte[] pdfBytes, FontDecoderCharset fontCharSet, Set pageIDs, ProgressMonitor pm) throws IOException {
		return this.loadTextPdf(null, null, pdfBytes, fontCharSet, pageIDs, pm);
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
		return this.loadTextPdf(doc, pdfDoc, pdfBytes, PdfFontDecoder.NO_DECODING, null, pm);
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
	 * @param pageIDs a set containing the IDs of the pages to decode
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the argument image markup document
	 * @throws IOException
	 */
	public ImDocument loadTextPdf(ImDocument doc, Document pdfDoc, byte[] pdfBytes, Set pageIDs, ProgressMonitor pm) throws IOException {
		return this.loadTextPdf(doc, pdfDoc, pdfBytes, PdfFontDecoder.NO_DECODING, pageIDs, pm);
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
		return this.loadTextPdf(doc, pdfDoc, pdfBytes, fontCharSet, null, pm);
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
	 * @param pageIDs a set containing the IDs of the pages to decode
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the argument image markup document
	 * @throws IOException
	 */
	public ImDocument loadTextPdf(ImDocument doc, Document pdfDoc, byte[] pdfBytes, FontDecoderCharset fontCharSet, Set pageIDs, ProgressMonitor pm) throws IOException {
		
		//	check arguments
		pdfDoc = this.getPdfDocument(pdfDoc, pdfBytes);
		if (doc == null)
			doc = this.doCreateDocument(getChecksum(pdfBytes), ((pageIDs == null) ? 0 : pdfDoc.getNumberOfPages()), pageIDs);
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
		PPageData[] pData = this.getPdfPageData(doc, (getWords | getFigures | getPaths), fontCharSet, false, pageIDs, catalog.getPageTree(), objects, spm);
		
		//	fill pages
		this.addTextPdfPages(doc, pData, objects, spm);
		
		//	finally ...
		return doc;
	}
	
	/**
	 * Load the fonts from a textual PDF, usually a digital-born PDF. This
	 * method is a special mode for font reference generation, not intended for
	 * loading any documents.
	 * @param pdfBytes the raw binary data the the PDF document was parsed from
	 * @param fontCharSet the character set to use for font decoding
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the fonts from the argument PDF
	 * @throws IOException
	 */
	public ImFont[] loadTextPdfFonts(byte[] pdfBytes, FontDecoderCharset fontCharSet, ProgressMonitor pm) throws IOException {
		return this.loadTextPdfFonts(pdfBytes, fontCharSet, -1, pm);
	}
	
	/**
	 * Load the fonts from a textual PDF, usually a digital-born PDF. This
	 * method is a special mode for font reference generation, not intended for
	 * loading any documents.
	 * @param pdfBytes the raw binary data the the PDF document was parsed from
	 * @param fontCharSet the character set to use for font decoding
	 * @param charImageHeight the desired height of the char images (default is
	 *            32 pixels)
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the fonts from the argument PDF
	 * @throws IOException
	 */
	public ImFont[] loadTextPdfFonts(byte[] pdfBytes, FontDecoderCharset fontCharSet, int charImageHeight, ProgressMonitor pm) throws IOException {
		
		//	check arguments
		Document pdfDoc = this.getPdfDocument(null, pdfBytes);
		if (pm == null)
			pm = ProgressMonitor.dummy;
		if (charImageHeight <= 0)
			charImageHeight = 32;
		
		//	build progress monitor with synchronized methods instead of synchronizing in-code
		SynchronizedProgressMonitor spm = ((pm instanceof SynchronizedProgressMonitor) ? ((SynchronizedProgressMonitor) pm) : new SynchronizedProgressMonitor(pm));
		
		//	load document structure (IcePDF is better at that ...)
		Catalog catalog = pdfDoc.getCatalog();
		
		//	parse PDF
		HashMap objects = PdfParser.getObjects(pdfBytes, pdfDoc.getSecurityManager());
		
		//	decrypt streams and strings
		PdfParser.decryptObjects(objects, pdfDoc.getSecurityManager());
		
		//	get basic page data (takes progress to 30%)
		PPageData[] pData = this.getPdfPageData(Gamta.INNER_PUNCTUATION_TOKENIZER, getWords, fontCharSet, false, null, catalog.getPageTree(), objects, spm);
		
		//	find fonts between PDF objects
		spm.setStep("Storing custom fonts");
		ArrayList pdfFonts = new ArrayList();
		CountingSet pdfFontNames = new CountingSet(new TreeMap());
		for (Iterator okit = objects.keySet().iterator(); okit.hasNext();) {
			Object obj = objects.get(okit.next());
			if (obj instanceof PdfFont) {
				pdfFonts.add((PdfFont) obj);
				pdfFontNames.add(((PdfFont) obj).name);
			}
		}
		if (pdfFonts.isEmpty())
			return new ImFont[0];
		
		//	attach non-standard PDF fonts to document (substituting > 255 char codes with unused ones <= 255)
		ImDocument doc = this.createDocument(getChecksum(pdfBytes));
		ArrayList fonts = new ArrayList();
		for (int f = 0; f < pdfFonts.size(); f++) {
			PdfFont pFont = ((PdfFont) pdfFonts.get(f));
			spm.setInfo("Doing font " + pFont.name + " (" + pFont + ")");
			int[] charCodes = pFont.getUsedCharCodes();
			if (charCodes.length == 0) {
				spm.setInfo(" ==> empty font");
				continue;
			}
			spm.setInfo(" - got " + charCodes.length + " characters");
			if (pdfFontNames.getCount(pFont.name) > 1) {
				pFont.name = (pFont.name + "-" + f);
				spm.setInfo(" - renamed to " + pFont.name + " to resolve collision");
			}
			ImFont imFont = new ImFont(doc, pFont.name, pFont.bold, pFont.italics, pFont.serif, pFont.monospaced);
			if (pFont.type != null)
				imFont.setAttribute("type", pFont.type);
			if (pFont.encoding != null)
				imFont.setAttribute("encoding", pFont.encoding);
			if (pFont.ascent != 0)
				imFont.setAttribute("ascent", ("" + pFont.ascent));
			if (pFont.capHeight != 0)
				imFont.setAttribute("capHeight", ("" + pFont.capHeight));
			if (pFont.xHeight != 0)
				imFont.setAttribute("xHeight", ("" + pFont.xHeight));
			if (pFont.descent != 0)
				imFont.setAttribute("descent", ("" + pFont.descent));
			for (int c = 0; c < charCodes.length; c++) {
				String charStr = pFont.getUnicode(charCodes[c]);
				BufferedImage charImage = pFont.getCharImage(charCodes[c]);
				if ((charStr != null) && (charStr.length() == 1))
					charStr = ("" + PdfCharDecoder.getNonCombiningChar(charStr.charAt(0)));
				imFont.addCharacter(pFont.getCharCode(charCodes[c]), charStr, (((charImage == null) || ((charImage.getWidth() * charImage.getHeight()) == 0)) ? null : ImFont.scaleCharImage(charImage, charImageHeight)));
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println("   - added char " + charCodes[c] + " (" + pFont.getCharCode(charCodes[c]) + "): '" + charStr + "', image is " + charImage);
			}
			if (imFont.getCharacterCount() == 0)
				spm.setInfo(" ==> no custom characters");
			else {
				fonts.add(imFont);
				spm.setInfo(" ==> font stored");
			}
		}
		
		//	TODO maybe attribute each font with words it's used for, including char codes (for neighborhood anlysis)
		
		//	finally ...
		return ((ImFont[]) fonts.toArray(new ImFont[fonts.size()]));
	}
	
	private static final boolean DEBUG_EXTRACT_FIGURES = true;
	private static final boolean DEBUG_MERGE_WORDS = false;
	private static final boolean ALLOW_FLIP_PAGES = true;
	
	private static class DefaultingMap extends HashMap {
		public Object get(Object key) {
			Object val = super.get(key);
			return ((val == null) ? key : val);
		}
	}
	
	private void addTextPdfPages(final ImDocument doc, final PPageData[] pData, final Map objects, final SynchronizedProgressMonitor spm) throws IOException {
		final float magnification = (((float) this.textPdfPageImageDpi) / defaultDpi);
		
		//	get tokenizer to check and split words with
		final Tokenizer tokenizer = ((Tokenizer) doc.getAttribute(ImDocument.TOKENIZER_ATTRIBUTE, Gamta.INNER_PUNCTUATION_TOKENIZER));
		
		//	prepare working in parallel
		ParallelFor pf;
		
		//	extract page objects
		spm.setStep("Sanitizing page words and graphics");
		spm.setBaseProgress(30);
		spm.setProgress(0);
		spm.setMaxProgress(32);
		pf = new ParallelFor() {
			public void doFor(int p) throws Exception {
				
				//	nothing to work with
				if ((pData[p] == null) || (pData[p].words == null) || (pData[p].figures == null) || (pData[p].paths == null))
					return;
				
				//	update status display (might be inaccurate, but better than lock escalation)
				spm.setProgress((p * 100) / pData.length);
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println("Got page content with " + pData[p].words.length + " words, " + pData[p].figures.length + " figures, and " + pData[p].paths.length + " vector based paths");
				
				//	sanitize words (if any)
				if (pData[p].words.length != 0) {
					spm.setInfo("Sanitizing words in page " + p + " of " + pData.length);
					int rawCount = pData[p].words.length;
					sanitizePageWords(pData[p], tokenizer,/* magnification,*/ spm);
					spm.setInfo(" ==> retained " + pData[p].words.length + " of " + rawCount + " words in page " + p + " of " + pData.length);
				}
				
				//	sanitize graphics (if any)
				if (pData[p].paths.length != 0) {
					spm.setInfo("Sanitizing graphics in page " + p + " of " + pData.length);
					int rawCount = pData[p].paths.length;
					sanitizePageGraphics(pData[p],/* magnification,*/ spm);
					spm.setInfo(" ==> retained " + pData[p].paths.length + " of " + rawCount + " graphics in page " + p + " of " + pData.length);
				}
				
				//	aggregate figures (if any)
				if (pData[p].figures.length != 0) {
					spm.setInfo("Sanitizing figures in page " + p + " of " + pData.length);
					int rawCount = pData[p].figures.length;
					sanitizePageFigures(pData[p]);
					spm.setInfo(" ==> retained " + pData[p].figures.length + " of " + rawCount + " figures in page " + p + " of " + pData.length);
				}
			}
		};
		ParallelJobRunner.runParallelFor(pf, ((PdfExtractorTest.aimAtPage < 0) ? 0 : PdfExtractorTest.aimAtPage), ((PdfExtractorTest.aimAtPage < 0) ? pData.length : (PdfExtractorTest.aimAtPage + 1)), (this.useMultipleCores ? -1 : 1));
		spm.setProgress(100);
		
		//	check errors
		checkException(pf);
		
		//	catch word sequences up or down vertical page edges, as well as ones across top or bottom ("Downloaded from ...")
		spm.setStep("Removing watermarks along page margins");
		spm.setBaseProgress(32);
		spm.setProgress(0);
		spm.setMaxProgress(35);
		this.removePageEdgeWatermarks(pData, false, 0, 0, new CascadingProgressMonitor(spm));
		
		//	assess page content in preparation for flipping
		spm.setStep("Assessing page graphics");
		spm.setBaseProgress(35);
		spm.setProgress(0);
		spm.setMaxProgress(36);
		final CountingSet pageGraphicSigs = new CountingSet(new HashMap());
		final CountingSet evenPageGraphicSigs = new CountingSet(new HashMap());
		final CountingSet oddPageGraphicSigs = new CountingSet(new HashMap());
		/*
		 * TODOne to prevent inclusion of page layout artwork in content graphics
		 * (a) refine page layout artwork detection to not only take into account the bounding box of
		 *     graphics, but the very rendering command sequence (to increase precision) if bounding
		 *     box exceeds dimensions of plain horizontal lines or bars (use TreeMap with size adaptive
		 *     Comparator to implement this).
		 * (b) exclude page layout graphics from formation of path clusters (keep them separate), and
		 * (c) also exclude page layout artwork graphics from detection of coherent graphics objects
		 * 
		 * TODOne TEST with Struwe_50MajorTempPlantFamilies2017.pdf (angled yellow line around top left page corner, with quite some extent)
		 */
		for (int pg = 0; pg < pData.length; pg++) {
			
			//	nothing to work with
			if ((pData[pg] == null) || (pData[pg].paths == null) || (pData[pg].paths.length == 0))
				continue;
			
			//	update status display (might be inaccurate, but better than lock escalation)
			spm.setProgress((pg * 100) / pData.length);
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println("Counting page graphics of " + pData[pg].paths.length + " vector paths");
			
			//	assess individual paths (make sure to count each signature only once per page)
			HashSet graphicSigs = new HashSet();
			for (int p = 0; p < pData[pg].paths.length; p++) {
				graphicSigs.add(pData[pg].paths[p].getShapesSignature());
				graphicSigs.add(pData[pg].paths[p].getBoundsSignature());
			}
			pageGraphicSigs.addAll(graphicSigs);
			(((pg % 2) == 0) ? evenPageGraphicSigs : oddPageGraphicSigs).addAll(graphicSigs);
		}
		
		//	evaluate page occurrence statistics for individual paths
		for (int pg = 0; pg < pData.length; pg++) {
			
			//	nothing to work with
			if ((pData[pg] == null) || (pData[pg].paths == null) || (pData[pg].paths.length == 0))
				continue;
			
			//	update status display (might be inaccurate, but better than lock escalation)
			spm.setProgress((pg * 100) / pData.length);
			
			//	assess individual paths
			spm.setInfo("Assessing page graphics of " + pData[pg].paths.length + " paths in " + pData[pg].pdfPageContentBox);
			for (int p = 0; p < pData[pg].paths.length; p++) {
				spm.setInfo("Path at " + pg + "." + pData[pg].paths[p].getBounds());
				spm.setInfo(" - signature (shapes): " + pData[pg].paths[p].getShapesSignature());
				spm.setInfo(" - signature (bounds): " + pData[pg].paths[p].getBoundsSignature());
				
				int allGraphicsPageCount = pData.length;
				int evenOddGraphicsPageCount = ((pData.length + (((pg % 2) == 0) ? 1 : 0)) / 2);
				
				int allPageCountShapes = pageGraphicSigs.getCount(pData[pg].paths[p].getShapesSignature());
				spm.setInfo(" - all pages (shapes): " + allPageCountShapes + " of " + allGraphicsPageCount);
				boolean isAllPageDecorationShapes = ((3 * allPageCountShapes) > (2 * allGraphicsPageCount));
				boolean sIsAllPageDecorationShapes = ((4 * allPageCountShapes) > (3 * allGraphicsPageCount));
				spm.setInfo(" ==> decoration on all pages (shapes): " + isAllPageDecorationShapes + " (secure : " + sIsAllPageDecorationShapes + ")");
				
				int evenOddPageCountShapes = (((pg % 2) == 0) ? evenPageGraphicSigs : oddPageGraphicSigs).getCount(pData[pg].paths[p].getShapesSignature());
				spm.setInfo(" - even/odd pages (shapes): " + evenOddPageCountShapes + " of " + evenOddGraphicsPageCount);
				boolean isEvenOddPageDecorationShapes = ((3 * evenOddPageCountShapes) > (2 * evenOddGraphicsPageCount));
				boolean sIsEvenOddPageDecorationShapes = ((4 * evenOddPageCountShapes) > (3 * evenOddGraphicsPageCount));
				spm.setInfo(" ==> decoration on even/odd pages (shapes): " + isEvenOddPageDecorationShapes + " (secure : " + sIsEvenOddPageDecorationShapes + ")");
				
				if (sIsAllPageDecorationShapes || sIsEvenOddPageDecorationShapes || (isAllPageDecorationShapes && isEvenOddPageDecorationShapes)) {
					pData[pg].paths[p].setIsPageDecoration(true);
					spm.setInfo(" ==> marked as page decoration (shapes)");
					continue;
				}
				
				int allPageCountBounds = pageGraphicSigs.getCount(pData[pg].paths[p].getBoundsSignature());
				spm.setInfo(" - all pages (bounds): " + allPageCountBounds + " of " + allGraphicsPageCount);
				boolean isAllPageDecorationBounds = ((4 * allPageCountBounds) > (3 * allGraphicsPageCount));
				boolean sIsAllPageDecorationBounds = ((5 * allPageCountBounds) > (4 * allGraphicsPageCount));
				spm.setInfo(" ==> decoration on all pages (bounds): " + isAllPageDecorationBounds + " (secure : " + sIsAllPageDecorationBounds + ")");
				
				int evenOddPageCountBounds = (((pg % 2) == 0) ? evenPageGraphicSigs : oddPageGraphicSigs).getCount(pData[pg].paths[p].getBoundsSignature());
				spm.setInfo(" - even/odd pages (bounds): " + evenOddPageCountBounds + " of " + evenOddGraphicsPageCount);
				boolean isEvenOddPageDecorationBounds = ((4 * evenOddPageCountBounds) > (3 * evenOddGraphicsPageCount));
				boolean sIsEvenOddPageDecorationBounds = ((5 * evenOddPageCountBounds) > (4 * evenOddGraphicsPageCount));
				spm.setInfo(" ==> decoration on even/odd pages (bounds): " + isEvenOddPageDecorationBounds + " (secure : " + sIsEvenOddPageDecorationBounds + ")");
				
				if (sIsAllPageDecorationBounds || sIsEvenOddPageDecorationBounds || (isAllPageDecorationBounds && isEvenOddPageDecorationBounds)) {
					pData[pg].paths[p].setIsPageDecoration(true);
					spm.setInfo(" ==> marked as page decoration (bounds)");
					continue;
				}
			}
		}
		
		//	classify whole page content
		spm.setStep("Assessing page content");
		spm.setBaseProgress(36);
		spm.setProgress(0);
		spm.setMaxProgress(37);
		double pMinPageMarginTemp = Integer.MAX_VALUE;
		final PPathCluster[][] pPathClusters = new PPathCluster[pData.length][];
		for (int p = 0; p < pData.length; p++) {
			
			//	nothing to work with
			if ((pData[p] == null) || (pData[p].words == null) || (pData[p].figures == null))
				continue;
			
			//	update status display (might be inaccurate, but better than lock escalation)
			spm.setProgress((p * 100) / pData.length);
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println("Assessing page content with " + pData[p].words.length + " words, " + pData[p].figures.length + " figures, and " + pData[p].paths.length + " vector based paths");
			
			//	check words (left and right margins only, should be sufficient)
			double pPageLeft = pData[p].pdfPageBox.getMinX();
			double pPageRight = pData[p].pdfPageBox.getMaxX();
			double pMinMarginLeft = pData[p].pdfPageBox.width;
			double pMinMarginRight = pData[p].pdfPageBox.width;
			for (int w = 0; w < pData[p].words.length; w++) {
				Rectangle2D wb = pData[p].words[w].bounds;
				pMinPageMarginTemp = Math.min(pMinPageMarginTemp, catchNegativePageMargin(wb.getMinX() - pPageLeft));
				pMinPageMarginTemp = Math.min(pMinPageMarginTemp, catchNegativePageMargin(pPageRight - wb.getMaxX()));
				pMinMarginLeft = Math.min(pMinMarginLeft, catchNegativePageMargin(wb.getMinX() - pPageLeft));
				pMinMarginRight = Math.min(pMinMarginRight, catchNegativePageMargin(pPageRight - wb.getMaxX()));
			}
			spm.setInfo("   ==> minimum margin on page " + p + " is " + pMinMarginLeft + "/" + pMinMarginRight + " ==> global " + pMinPageMarginTemp);
			
			//	DO NOT check figures, might be clipped, and words should do
			
			//	aggregate paths
			pPathClusters[p] = getPathClusters(pData[p].paths);
			spm.setInfo("   - aggregated " + pData[p].paths.length + " paths into " + pPathClusters[p].length + " clusters");
//			
//			//	count graphics occurrences
//			for (int c = 0; c < pPathClusters[p].length; c++) {
//				
//				//	convert bounds, as PDF Y coordinate is bottom-up, whereas Java, JavaScript, etc. Y coordinate is top-down
//				BoundingBox pcb = getBoundingBox(pPathClusters[p][c].bounds, pData[p].pdfPageContentBox, magnification, pData[p].rotate);
//				pageGraphics.add(pcb);
//				(((p % 2) == 0) ? evenPageGraphics : oddPageGraphics).add(pcb);
//			}
		}
		final double pMinPageMargin = pMinPageMarginTemp;
		spm.setInfo(" ==> minimum page margin is " + pMinPageMargin);
		
		//	do page rotation assessment and content flipping right here
		spm.setStep("Handling flipped page content");
		spm.setBaseProgress(37);
		spm.setProgress(0);
		spm.setMaxProgress(40);
		final BoundingBox[] pFlipContentBox = new BoundingBox[pData.length];
		final int[] pFlipContentDirection = new int[pData.length];
		final DefaultingMap[] pFlippedObjects = new DefaultingMap[pData.length];
		final BoundingBox[] pFlippedContentBox = new BoundingBox[pData.length];
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
				
				//	also measure page content as a whole in the process (for comparison)
				int pcLeft = Integer.MAX_VALUE;
				int pcRight = Integer.MIN_VALUE;
				int pcTop = Integer.MAX_VALUE;
				int pcBottom = Integer.MIN_VALUE;
				
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
					
					//	also factor into page content bounds
					pcLeft = Math.min(pcLeft, wb.left);
					pcRight = Math.max(pcRight, wb.right);
					pcTop = Math.min(pcTop, wb.top);
					pcBottom = Math.max(pcBottom, wb.bottom);
				}
				
				//	assess page figure orientation
				for (int f = 0; f < pData[p].figures.length; f++) {
					
					//	convert bounds, as PDF Y coordinate is bottom-up, whereas Java, JavaScript, etc. Y coordinate is top-down
					BoundingBox fb = getBoundingBox(pData[p].figures[f].bounds, pData[p].pdfPageContentBox, magnification, pData[p].rotate);
					
					//	test for vertical figures
					if ((pData[p].figures[f].transform.getRotation() < -(Math.PI / 4)) && (pData[p].figures[f].transform.getRotation() > -((Math.PI * 3) / 4))) {
						bubLeft = Math.min(bubLeft, fb.left);
						bubRight = Math.max(bubRight, fb.right);
						bubTop = Math.min(bubTop, fb.top);
						bubBottom = Math.max(bubBottom, fb.bottom);
						buFigures.add(pData[p].figures[f]);
					}
					if ((pData[p].figures[f].transform.getRotation() > (Math.PI / 4)) && (pData[p].figures[f].transform.getRotation() < ((Math.PI * 3) / 4))) {
						tdbLeft = Math.min(tdbLeft, fb.left);
						tdbRight = Math.max(tdbRight, fb.right);
						tdbTop = Math.min(tdbTop, fb.top);
						tdbBottom = Math.max(tdbBottom, fb.bottom);
						tdFigures.add(pData[p].figures[f]);
					}
					
					//	also factor into page content bounds
					pcLeft = Math.min(pcLeft, fb.left);
					pcRight = Math.max(pcRight, fb.right);
					pcTop = Math.min(pcTop, fb.top);
					pcBottom = Math.max(pcBottom, fb.bottom);
				}
				
				//	any content to flip?
				if ((buWords.size() + buFigures.size() + tdWords.size() + tdFigures.size()) == 0)
					return;
				
				//	collect _all_ words in to-flip area
				for (int w = 0; w < pData[p].words.length; w++) {
					
					//	convert bounds, as PDF Y coordinate is bottom-up, whereas Java, JavaScript, etc. Y coordinate is top-down
					BoundingBox wb = getBoundingBox(pData[p].words[w].bounds, pData[p].pdfPageContentBox, magnification, pData[p].rotate);
					int wbCx = ((wb.left + wb.right) / 2);
					int wbCy = ((wb.top + wb.bottom) / 2);
					
					//	test for to-flip words
					if ((bubLeft < wbCx) && (wbCx < bubRight) && (bubTop < wbCy) && (wbCy < bubBottom))
						buWords.add(pData[p].words[w]);
					else if ((tdbLeft < wbCx) && (wbCx < tdbRight) && (tdbTop < wbCy) && (wbCy < tdbBottom))
						tdWords.add(pData[p].words[w]);
//					else if (PdfExtractorTest.aimAtPage != -1) {
//						if (buWords.size() != 0)
//							System.out.println("Got word outside to-flip bottom-up area of " + bubLeft + "-" + bubRight + "x" + bubTop + "-" + bubBottom + ": " + pData[p].words[w].str + " at " + wb);
//						if (tdWords.size() != 0)
//							System.out.println("Got word outside to-flip top-down area of " + tdbLeft + "-" + tdbRight + "x" + tdbTop + "-" + tdbBottom + ": " + pData[p].words[w].str + " at " + wb);
//					}
				}
				
				//	compute predominant font direction for whole page areas
				PPageContentOrientation pPageContentOrientation = getPageContentOrientation(pData[p]);
				
				//	get paths inside to-flip area (handling clusters as a whole - table grids !!!), and also check flip plausibility
				for (int c = 0; c < pPathClusters[p].length; c++) {
					
					//	convert bounds, as PDF Y coordinate is bottom-up, whereas Java, JavaScript, etc. Y coordinate is top-down
					BoundingBox pcb = getBoundingBox(pPathClusters[p][c].bounds, pData[p].pdfPageContentBox, magnification, pData[p].rotate);
					
					//	check if path cluster is page decoration (present in two thirds of all pages, or two thirds of even or odd pages)
					if (pPathClusters[p][c].isPageDecoration) {
						spm.setInfo("Ignoring page decoration in content flip: " + pcb.toString());
						continue;
					}
					
					//	assess path cluster orientation (also collecting overlapping paths)
					int pOrientation = getPageContentObjectOrientation(pPathClusters[p][c].bounds, pData[p].pdfPageContentBox, pData[p].rotate, pPageContentOrientation);
					if ((pOrientation == PWord.BOTTOM_UP_FONT_DIRECTION) || ((bubLeft < pcb.right) && (pcb.left < bubRight) && (bubTop < pcb.bottom) && (pcb.top < bubBottom))) {
						buPaths.addAll(pPathClusters[p][c].paths);
						bubLeft = Math.min(bubLeft, pcb.left);
						bubRight = Math.max(bubRight, pcb.right);
						bubTop = Math.min(bubTop, pcb.top);
						bubBottom = Math.max(bubBottom, pcb.bottom);
					}
					else if ((pOrientation == PWord.TOP_DOWN_FONT_DIRECTION) || ((tdbLeft < pcb.right) && (pcb.left < tdbRight) && (tdbTop < pcb.bottom) && (pcb.top < tdbBottom))) {
						tdPaths.addAll(pPathClusters[p][c].paths);
						tdbLeft = Math.min(tdbLeft, pcb.left);
						tdbRight = Math.max(tdbRight, pcb.right);
						tdbTop = Math.min(tdbTop, pcb.top);
						tdbBottom = Math.max(tdbBottom, pcb.bottom);
					}
					
					//	also factor into page content bounds
					pcLeft = Math.min(pcLeft, pcb.left);
					pcRight = Math.max(pcRight, pcb.right);
					pcTop = Math.min(pcTop, pcb.top);
					pcBottom = Math.max(pcBottom, pcb.bottom);
					
					//	reject flip if path cluster (a) encloses whole to-flip block and (b) is predominantly oriented other than bottom-up
					boolean pIncludesAllBu = true;
					if (pOrientation != PWord.BOTTOM_UP_FONT_DIRECTION) {
						for (Iterator wit = buWords.iterator(); wit.hasNext();) {
							PWord pw = ((PWord) wit.next());
							if (!pPathClusters[p][c].bounds.contains(pw.bounds)) {
								pIncludesAllBu = false;
								break;
							}
						}
						for (Iterator fit = buFigures.iterator(); fit.hasNext();) {
							PFigure pf = ((PFigure) fit.next());
							if (!pPathClusters[p][c].bounds.contains(pf.bounds)) {
								pIncludesAllBu = false;
								break;
							}
						}
						if (pIncludesAllBu) {
							bubLeft = Integer.MAX_VALUE;
							bubRight = Integer.MIN_VALUE;
							bubTop = Integer.MAX_VALUE;
							bubBottom = Integer.MIN_VALUE;
							buWords.clear();
							buFigures.clear();
							buPaths.clear();
						}
					}
					
					//	reject flip if path cluster (a) encloses whole to-flip block and (b) is predominantly oriented other than top-down
					boolean pIncludesAllTd = true;
					if (pOrientation != PWord.TOP_DOWN_FONT_DIRECTION) {
						for (Iterator wit = tdWords.iterator(); wit.hasNext();) {
							PWord pw = ((PWord) wit.next());
							if (!pPathClusters[p][c].bounds.contains(pw.bounds)) {
								pIncludesAllTd = false;
								break;
							}
						}
						for (Iterator fit = tdFigures.iterator(); fit.hasNext();) {
							PFigure pf = ((PFigure) fit.next());
							if (!pPathClusters[p][c].bounds.contains(pf.bounds)) {
								pIncludesAllTd = false;
								break;
							}
						}
						if (pIncludesAllTd) {
							tdbLeft = Integer.MAX_VALUE;
							tdbRight = Integer.MIN_VALUE;
							tdbTop = Integer.MAX_VALUE;
							tdbBottom = Integer.MIN_VALUE;
							tdWords.clear();
							tdFigures.clear();
							tdPaths.clear();
						}
					}
				}
				
				//	collect _all_ words in to-flip area once again in case adding paths expanded it
				for (int w = 0; w < pData[p].words.length; w++) {
					
					//	convert bounds, as PDF Y coordinate is bottom-up, whereas Java, JavaScript, etc. Y coordinate is top-down
					BoundingBox wb = getBoundingBox(pData[p].words[w].bounds, pData[p].pdfPageContentBox, magnification, pData[p].rotate);
					int wbCx = ((wb.left + wb.right) / 2);
					int wbCy = ((wb.top + wb.bottom) / 2);
					
					//	test for to-flip words
					if ((bubLeft < wbCx) && (wbCx < bubRight) && (bubTop < wbCy) && (wbCy < bubBottom))
						buWords.add(pData[p].words[w]);
					else if ((tdbLeft < wbCx) && (wbCx < tdbRight) && (tdbTop < wbCy) && (wbCy < tdbBottom))
						tdWords.add(pData[p].words[w]);
//					else if (PdfExtractorTest.aimAtPage != -1) {
//						if (buWords.size() != 0)
//							System.out.println("Got word outside path expanded to-flip bottom-up area of " + bubLeft + "-" + bubRight + "x" + bubTop + "-" + bubBottom + ": " + pData[p].words[w].str + " at " + wb);
//						if (tdWords.size() != 0)
//							System.out.println("Got word outside path expanded to-flip top-down area of " + tdbLeft + "-" + tdbRight + "x" + tdbTop + "-" + tdbBottom + ": " + pData[p].words[w].str + " at " + wb);
//					}
				}
				
				//	collect _all_ figures in to-flip area once again in case adding paths expanded it
				for (int f = 0; f < pData[p].figures.length; f++) {
					
					//	convert bounds, as PDF Y coordinate is bottom-up, whereas Java, JavaScript, etc. Y coordinate is top-down
					BoundingBox fb = getBoundingBox(pData[p].figures[f].bounds, pData[p].pdfPageContentBox, magnification, pData[p].rotate);
					int fbCx = ((fb.left + fb.right) / 2);
					int fbCy = ((fb.top + fb.bottom) / 2);
					
					//	test for to-flip words
					if ((bubLeft < fbCx) && (fbCx < bubRight) && (bubTop < fbCy) && (fbCy < bubBottom))
						buFigures.add(pData[p].figures[f]);
					else if ((tdbLeft < fbCx) && (fbCx < tdbRight) && (tdbTop < fbCy) && (fbCy < tdbBottom))
						tdFigures.add(pData[p].figures[f]);
//					else if (PdfExtractorTest.aimAtPage != -1) {
//						if (buFigures.size() != 0)
//							System.out.println("Got word outside path expanded to-flip bottom-up area of " + bubLeft + "-" + bubRight + "x" + bubTop + "-" + bubBottom + ": " + fb);
//						if (tdFigures.size() != 0)
//							System.out.println("Got word outside path expanded to-flip top-down area of " + tdbLeft + "-" + tdbRight + "x" + tdbTop + "-" + tdbBottom + ": " + fb);
//					}
				}
				
				//	check if flipping top-down block sensible
				if ((tdFigures.size() + tdWords.size()) != 0) {
					BoundingBox tdb = new BoundingBox(tdbLeft, tdbRight, tdbTop, tdbBottom);
					BoundingBox pcb = new BoundingBox(pcLeft, pcRight, pcTop, pcBottom);
					spm.setInfo("Checking top-down content flip in " + tdb + " on page " + p + ", page content in " + pcb);
					if (this.declineContentFlip(tdb, pcb, PWord.TOP_DOWN_FONT_DIRECTION, tdWords, pData[p], spm)) {
						tdWords.clear();
						tdFigures.clear();
						tdPaths.clear();
						spm.setInfo(" ==> flip declined");
					}
					else spm.setInfo(" ==> flip plausible");
				}
				
				//	check if flipping bottom-up block sensible
				if ((buFigures.size() + buWords.size()) != 0) {
					BoundingBox bub = new BoundingBox(bubLeft, bubRight, bubTop, bubBottom);
					BoundingBox pcb = new BoundingBox(pcLeft, pcRight, pcTop, pcBottom);
					spm.setInfo("Checking bottom-up content flip in " + bub + " on page " + p + ", page content in " + pcb);
					if (this.declineContentFlip(bub, pcb, PWord.BOTTOM_UP_FONT_DIRECTION, buWords, pData[p], spm)) {
						buWords.clear();
						buFigures.clear();
						buPaths.clear();
						spm.setInfo(" ==> flip declined");
					}
					else spm.setInfo(" ==> flip plausible");
				}
				
				//	any content remaining to flip?
				if ((buWords.size() + buFigures.size() + tdWords.size() + tdFigures.size()) == 0)
					return;
				
				//	convert page bounds, as PDF Y coordinate is bottom-up, whereas Java, JavaScript, etc. Y coordinate is top-down
				BoundingBox pb = getBoundingBox(pData[p].pdfPageContentBox, pData[p].pdfPageContentBox, magnification, pData[p].rotate);
				int maxTfLeft = (pb.getWidth() / 3);
				int minTfRight = ((pb.getWidth() * 2) / 3);
				int maxTfTop = (pb.getHeight() / 3);
				int minTfBottom = ((pb.getHeight() * 2) / 3);
				
				//	check dimensions of to-flip block (if it is higher than wide and all clear to the right, we can do the flip regardless of extent)
				if ((((tdWords.size() + tdFigures.size()) != 0) && ((tdbRight - tdbLeft) < (tdbBottom - tdbTop))) || (((buWords.size() + buFigures.size()) != 0) && ((bubRight - bubLeft) < (bubBottom - bubTop)))) {
					int tfLeft = Math.min(tdbLeft, bubLeft);
					int tfRight = Math.max(tdbRight, bubRight);
					int tfTop = Math.min(tdbTop, bubTop);
					int tfBottom = Math.max(tdbBottom, bubBottom);
					HashSet tfWords = new HashSet();
					tfWords.addAll(tdWords);
					tfWords.addAll(buWords);
					HashSet tfFigures = new HashSet();
					tfFigures.addAll(tdFigures);
					tfFigures.addAll(buFigures);
					spm.setInfo("Checking page content flip in [" + tfLeft + "," + tfRight + "," + tfTop + "," + tfBottom + "] on page " + p);
					boolean tfClearToRight = true;
					
					//	check words
					for (int w = 0; w < pData[p].words.length; w++) {
						if (tfWords.contains(pData[p].words[w]))
							continue;
						
						//	convert bounds, as PDF Y coordinate is bottom-up, whereas Java, JavaScript, etc. Y coordinate is top-down
						BoundingBox wb = getBoundingBox(pData[p].words[w].bounds, pData[p].pdfPageContentBox, magnification, pData[p].rotate);
						
						//	check if word gets in the way
						if (wb.bottom <= tfTop)
							continue; // above to-flip area
						if (wb.top >= tfBottom)
							continue; // below to-flip area
						if (wb.right <= tfRight)
							continue; // inside or left of to-flip area
						
						//	this one _is_ in the way
						spm.setInfo(" ==> word '" + pData[p].words[w].str + "' at " + wb + " in the way");
						tfClearToRight = false;
						break;
					}
					
					//	check figures
					for (int f = 0; f < pData[p].figures.length; f++) {
						if (tfFigures.contains(pData[p].figures[f]))
							continue;
						
						//	convert bounds, as PDF Y coordinate is bottom-up, whereas Java, JavaScript, etc. Y coordinate is top-down
						BoundingBox fb = getBoundingBox(pData[p].figures[f].bounds, pData[p].pdfPageContentBox, magnification, pData[p].rotate);
						
						//	check if word gets in the way
						if (fb.bottom <= tfTop)
							continue; // above to-flip area
						if (fb.top >= tfBottom)
							continue; // below to-flip area
						if (fb.right <= tfRight)
							continue; // inside or left of to-flip area
						
						//	this one _is_ in the way
						spm.setInfo(" ==> figure at " + fb + " in the way");
						tfClearToRight = false;
						break;
					}
					
					//	adjust page center if to-flip area clear to right
					if (tfClearToRight) {
						maxTfLeft = pb.getWidth();
						minTfRight = 0;
						maxTfTop = pb.getHeight();
						minTfBottom = 0;
						spm.setInfo(" --> adjusted minimum flip bounds to [" + maxTfLeft + "," + minTfRight + "," + maxTfTop + "," + minTfBottom + "]");
					}
				}
				
				//	we do have a significant bottom-up block, flip it
				if (ALLOW_FLIP_PAGES && (bubLeft < bubRight) && (bubTop < bubBottom) && (bubLeft < maxTfLeft) && (bubRight > minTfRight) && (bubTop < maxTfTop) && (bubBottom > minTfBottom)) {
					spm.setInfo("Got bottom-up block with " + buWords.size() + " words to flip");
					
					//	check if predominant orientation of to-flip block is really bottom-up (require at least 67% to accept)
					Rectangle2D bubBounds = null;
					for (Iterator wit = buWords.iterator(); wit.hasNext();) {
						PWord pw = ((PWord) wit.next());
						if (bubBounds == null)
							bubBounds = pw.bounds;
						else bubBounds = bubBounds.createUnion(pw.bounds);
					}
					for (Iterator fit = buFigures.iterator(); fit.hasNext();) {
						PFigure pf = ((PFigure) fit.next());
						if (bubBounds == null)
							bubBounds = pf.bounds;
						else bubBounds = bubBounds.createUnion(pf.bounds);
					}
					int bubBlockOrientation = getPageContentObjectOrientation(bubBounds, pData[p].pdfPageContentBox, pData[p].rotate, pPageContentOrientation, 0.67);
					if (bubBlockOrientation != PWord.BOTTOM_UP_FONT_DIRECTION) {
						spm.setInfo(" ==> block flip rejected due to too many non-bottom-up parts");
						return;
					}
					
					//	perform flip
					pFlippedObjects[p] = new DefaultingMap();
					pFlipContentBox[p] = new BoundingBox(bubLeft, bubRight, bubTop, bubBottom);
					pFlipContentDirection[p] = PWord.BOTTOM_UP_FONT_DIRECTION;
					pFlippedContentBox[p] = flipPageContent(pData[p], pMinPageMargin, buWords, buFigures, buPaths, pFlippedObjects[p], PWord.BOTTOM_UP_FONT_DIRECTION, magnification);
					
					//	re-compute path clusters
					pPathClusters[p] = getPathClusters(pData[p].paths);
				}
				
				//	we do have a significant top-down block, flip it
				else if (ALLOW_FLIP_PAGES && (tdbLeft < tdbRight) && (tdbTop < tdbBottom) && (tdbLeft < maxTfLeft) && (tdbRight > minTfRight) && (tdbTop < maxTfTop) && (tdbBottom > minTfBottom)) {
					spm.setInfo("Got top-down block with " + tdWords.size() + " words to flip");
					
					//	check if predominant orientation of to-flip block is really top-down (require at least 67% to accept)
					Rectangle2D tdbBounds = null;
					for (Iterator wit = tdWords.iterator(); wit.hasNext();) {
						PWord pw = ((PWord) wit.next());
						if (tdbBounds == null)
							tdbBounds = pw.bounds;
						else tdbBounds = tdbBounds.createUnion(pw.bounds);
					}
					for (Iterator fit = tdFigures.iterator(); fit.hasNext();) {
						PFigure pf = ((PFigure) fit.next());
						if (tdbBounds == null)
							tdbBounds = pf.bounds;
						else tdbBounds = tdbBounds.createUnion(pf.bounds);
					}
					int bubBlockOrientation = getPageContentObjectOrientation(tdbBounds, pData[p].pdfPageContentBox, pData[p].rotate, pPageContentOrientation, 0.67);
					if (bubBlockOrientation != PWord.TOP_DOWN_FONT_DIRECTION) {
						spm.setInfo(" ==> block flip rejected due to too many non-top-down parts");
						return;
					}
					
					//	perform flip
					pFlippedObjects[p] = new DefaultingMap();
					pFlipContentBox[p] = new BoundingBox(tdbLeft, tdbRight, tdbTop, tdbBottom);
					pFlipContentDirection[p] = PWord.TOP_DOWN_FONT_DIRECTION;
					pFlippedContentBox[p] = flipPageContent(pData[p], pMinPageMargin, tdWords, tdFigures, tdPaths, pFlippedObjects[p], PWord.TOP_DOWN_FONT_DIRECTION, magnification);
					
					//	re-compute path clusters
					pPathClusters[p] = getPathClusters(pData[p].paths);
				}
			}
			
			/* decline flipping lone figures embedded in larger multi-figure:
			 * - check for words that would need flipping
			 * - check if any words in flipping area
			 * - check size of flipping area:
			 *   - should be higher than column width for flip to make sense
			 *   - should be whole column width
			 * - refuse flip if:
			 *   - flipping area small
			 *   - no words needing flipping, but left-right words in flipping area
			 * */
			private boolean declineContentFlip(BoundingBox fb, BoundingBox pcb, int fDirection, Set fWords, PPageData pData, ProgressMonitor pm) {
				
				//	no use flipping an area that is wider than high ...
				if (fb.getHeight() < fb.getWidth()) {
					pm.setInfo(" - flip area wider than heigh, flip makes no sense");
					return true;
				}
				pm.setInfo(" - flip area higher than wide");
				
				//	no sense in flipped representation in source PDF if block would have fit in its actual reading direction
				//	TODO maybe require only lower height, there might be some sick things in multi-column layouts
				if (fb.getHeight() <= pcb.getWidth()) {
					pm.setInfo(" - flip area lower than page content width, would have fit without flipping");
					return true;
				}
				pm.setInfo(" - flip area higher than page content width, would not have fit without flipping");
				
				//	too narrow for standalone content (less than an inch wide)
				if (fb.getWidth() < textPdfPageImageDpi) {
					pm.setInfo(" - flip area too narrow for standalone block, less than an inch wide");
					return true;
				}
				pm.setInfo(" - flip area " + (((float) fb.getWidth()) / textPdfPageImageDpi) + " inches wide");
				
				//	check to-flip words
				int fdWordCount = 0;
				for (Iterator wit = fWords.iterator(); wit.hasNext();) {
					PWord pw = ((PWord) wit.next());
					if (pw.fontDirection == fDirection)
						fdWordCount++;
				}
				pm.setInfo(" - " + fdWordCount + " of " + fWords.size() + " words in area in flip direction");
				
				//	flip not backed by any of included words at all
				if ((fWords.size() != 0) && (fdWordCount == 0))
					return true;
				
				//	this one looks OK
				return false;
			}
		};
		ParallelJobRunner.runParallelFor(pf, ((PdfExtractorTest.aimAtPage < 0) ? 0 : PdfExtractorTest.aimAtPage), ((PdfExtractorTest.aimAtPage < 0) ? pData.length : (PdfExtractorTest.aimAtPage + 1)), (this.useMultipleCores ? -1 : 1));
		spm.setProgress(100);
		
		//	check errors
		checkException(pf);
		
		//	extract page objects
		spm.setStep("Storing figures and graphics");
		spm.setBaseProgress(40);
		spm.setProgress(0);
		spm.setMaxProgress(42);
		final Map[] pFigureSupplementIDs = new Map[pData.length];
		pf = new ParallelFor() {
			public void doFor(int p) throws Exception {
				
				//	nothing to work with
				if ((pData[p] == null) || ((pData[p].figures == null) && (pData[p].paths == null)))
					return;
				
				//	update status display (might be inaccurate, but better than lock escalation)
				spm.setProgress((p * 100) / pData.length);
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println("Storing " + ((pData[p].figures == null) ? 0 : pData[p].figures.length) + " figures and " + ((pData[p].paths == null) ? 0 : pData[p].paths.length) + " vector based paths");
				
				//	preserve figures embedded in pages
				if (pData[p].figures.length != 0) {
					spm.setInfo("Storing figures in page " + p + " of " + pData.length);
					pFigureSupplementIDs[p] = new HashMap();
					storeFiguresAsSupplements(doc, pData[p]/*, pdfDoc*/, objects, magnification, pFigureSupplementIDs[p], spm);
				}
				
				//	preserve vector based graphics embedded in pages
				if (pData[p].paths.length != 0) {
					spm.setInfo("Storing vector based graphics in page " + p + " of " + pData.length);
					storeGraphicsAsSupplements(doc, pData[p], pPathClusters[p], (pFlippedObjects[p] != null), textPdfPageImageDpi, magnification, spm);
				}
			}
		};
		ParallelJobRunner.runParallelFor(pf, ((PdfExtractorTest.aimAtPage < 0) ? 0 : PdfExtractorTest.aimAtPage), ((PdfExtractorTest.aimAtPage < 0) ? pData.length : (PdfExtractorTest.aimAtPage + 1)), (this.useMultipleCores ? -1 : 1));
		spm.setProgress(100);
		
		//	check errors
		checkException(pf);
		
		//	extract page objects
		spm.setStep("Analyzing words in relation to figures and graphics");
		spm.setBaseProgress(42);
		spm.setProgress(0);
		spm.setMaxProgress(45);
		final Set[] pInFigureWords = new Set[pData.length];
		final Set[] pInGraphicsWords = new Set[pData.length];
		final Set[] pWatermarkWords = new Set[pData.length];
		pf = new ParallelFor() {
			public void doFor(int p) throws Exception {
				
				//	nothing to work with
				if ((pData[p] == null) || (pData[p].words == null) || ((pData[p].figures == null) && (pData[p].paths == null)))
					return;
				
				//	update status display (might be inaccurate, but better than lock escalation)
				spm.setProgress((p * 100) / pData.length);
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println("Got page content with " + pData[p].words.length + " words, " + ((pData[p].figures == null) ? 0 : pData[p].figures.length) + " figures, and " + ((pData[p].paths == null) ? 0 : pData[p].paths.length) + " vector based paths");
				
				//	collect words that lie inside images
				if ((pData[p].figures != null) && (pData[p].figures.length != 0)) {
					spm.setInfo("Getting in-figure words in page " + p + " of " + pData.length);
					for (int f = 0; f < pData[p].figures.length; f++) {
						HashSet inFigureWords = null;
						
						//	get top and bottom (considering any clipping), and observe inversions
						int figTop = ((int) Math.ceil(Math.max(pData[p].figures[f].visibleBounds.getMinY(), pData[p].figures[f].visibleBounds.getMaxY())));
						int figBottom = ((int) Math.floor(Math.min(pData[p].figures[f].visibleBounds.getMinY(), pData[p].figures[f].visibleBounds.getMaxY())));
						
						//	count words per pixel line
						boolean figureClear = true;
						int[] rowWordPixels = new int[figTop + 1 - figBottom];
						Arrays.fill(rowWordPixels, 0);
						for (int w = 0; w < pData[p].words.length; w++)
							if (pData[p].figures[f].visibleBounds.intersects(pData[p].words[w].bounds)) {
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
						spm.setInfo(" - getting words inside figure at " + pData[p].figures[f].visibleBounds);
						double wFigTop = Math.max(pData[p].figures[f].visibleBounds.getMinY(), pData[p].figures[f].visibleBounds.getMaxY());
						double wFigBottom = Math.min(pData[p].figures[f].visibleBounds.getMinY(), pData[p].figures[f].visibleBounds.getMaxY());
						for (int w = 0; w < pData[p].words.length; w++)
							if (pData[p].figures[f].visibleBounds.intersects(pData[p].words[w].bounds)) {
								
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
								
//								//	over 50% of word row occupied with words, likely text rather than numbering
//								if (((wordRowPixelCount / wordRowCount) * 2) > Math.abs(pData[p].figures[f].visibleBounds.getWidth())) {
								//	over 67% of word row occupied with words, likely text rather than numbering
								if (((wordRowPixelCount / wordRowCount) * 3) > (Math.abs(pData[p].figures[f].visibleBounds.getWidth() * 2))) {
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
								spm.setInfo("   - found word at " + pData[p].words[w].bounds + ": " + pData[p].words[w]);
							}
						
						//	cut top and bottom of figure bounds (figure proper was stored above)
						//	==> truncate visible bounds to keep figure separate from words
						pData[p].figures[f].visibleBounds.setRect(pData[p].figures[f].visibleBounds.getMinX(), wFigBottom, pData[p].figures[f].visibleBounds.getWidth(), (wFigTop - wFigBottom));
						spm.setInfo(" - figure cropped to " + pData[p].figures[f].visibleBounds);
						
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
				
				//	collect words that lie inside vector graphics paths (safe for page layout artwork)
				if ((pData[p].paths != null) && (pData[p].paths.length != 0)) {
					spm.setInfo("Getting in-graphics words in page " + p + " of " + pData.length);
					for (int t = 0; t < pData[p].paths.length; t++) {
						if (pData[p].paths[t].isPageDecoration())
							continue; // ignore page decoration here
						HashSet inGraphicsWords = null;
						
						//	get top and bottom, and observe inversions
						Rectangle2D pathBounds = pData[p].paths[t].getBounds();
						int pathTop = ((int) Math.ceil(Math.max(pathBounds.getMinY(), pathBounds.getMaxY())));
						int pathBottom = ((int) Math.floor(Math.min(pathBounds.getMinY(), pathBounds.getMaxY())));
						
						//	check words, and compute image margin
						spm.setInfo(" - getting words inside graphics at " + pathBounds);
						for (int w = 0; w < pData[p].words.length; w++)
							if (pathBounds.intersects(pData[p].words[w].bounds)) {
								
								//	mark word as lying in path
								if (inGraphicsWords == null)
									inGraphicsWords = new HashSet();
								inGraphicsWords.add(pData[p].words[w]);
								spm.setInfo("   - found word at " + pData[p].words[w].bounds + ": " + pData[p].words[w]);
							}
						
						//	handle words inside current figure
						if (inGraphicsWords != null) {
							
							//	salvage words that are mainly above pathTop or below pathBottom
							for (Iterator ifwit = inGraphicsWords.iterator(); ifwit.hasNext();) {
								PWord ifw = ((PWord) ifwit.next());
								double ifwCenterY = ((ifw.bounds.getMinY() + ifw.bounds.getMaxY()) / 2);
								if ((ifwCenterY < pathTop) && (ifwCenterY > pathBottom))
									spm.setInfo("   - marking word " + ifw.bounds + " as lying in graphics");
								else {
									ifwit.remove();
									spm.setInfo("   - retaining word " + ifw.bounds + " as outside cropped graphics");
								}
							}
							
							//	merge overlapping word sets for each figure only after salvaging inspection
							if (inGraphicsWords.size() != 0) {
								if (pInGraphicsWords[p] == null)
									pInGraphicsWords[p] = new HashSet();
								pInGraphicsWords[p].addAll(inGraphicsWords);
							}
						}
					}
					spm.setInfo("Found " + ((pInGraphicsWords[p] == null) ? "no" : ("" + pInGraphicsWords[p].size())) + " in-graphics words in page " + p + " of " + pData.length);
				}
				
				//	mark words that are water marks (all too faint words that do not lie in image or graphics)
				for (int w = 0; w < pData[p].words.length; w++) {
					if ((pInFigureWords[p] != null) && pInFigureWords[p].contains(pData[p].words[w])) {}
					else if ((pInGraphicsWords[p] != null) && pInGraphicsWords[p].contains(pData[p].words[w])) {}
					else if (pData[p].words[w].color != null) {
						float pwBrightness = getBrightness(pData[p].words[w].color);
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println("Word " + pData[p].words[w] + " has brightness " + pwBrightness + " from " + pData[p].words[w].color + " with alpha " + pData[p].words[w].color.getAlpha());
						if (pwBrightness > 0.67) {
							if (pWatermarkWords[p] == null)
								pWatermarkWords[p] = new HashSet();
							pWatermarkWords[p].add(pData[p].words[w]);
							if (PdfExtractorTest.aimAtPage != -1)
								System.out.println(" ==> skipping as too faint");
						}
					}
				}
				spm.setInfo("Found " + ((pWatermarkWords[p] == null) ? "no" : ("" + pWatermarkWords[p].size())) + " watermark words in page " + p + " of " + pData.length);
			}
		};
		ParallelJobRunner.runParallelFor(pf, ((PdfExtractorTest.aimAtPage < 0) ? 0 : PdfExtractorTest.aimAtPage), ((PdfExtractorTest.aimAtPage < 0) ? pData.length : (PdfExtractorTest.aimAtPage + 1)), (this.useMultipleCores ? -1 : 1));
		spm.setProgress(100);
		
		//	check errors
		checkException(pf);
		
		//	check plausibility
		if (PdfExtractorTest.aimAtPage == -1) {
			int docWordCount = 0;
			int docPageCount = 0;
			for (int p = 0; p < pData.length; p++)
				if ((pData[p] != null) && (pData[p].words != null)) {
					docWordCount += pData[p].words.length;
					docPageCount++;
				}
			if (docWordCount < (docPageCount * minAveragePageWords))
				throw new IOException("Too few words per page (" + docWordCount + " on " + docPageCount + " pages, less than " + minAveragePageWords + ")");
		}
		
		//	attach non-standard PDF fonts to document (substituting > 255 char codes with unused ones <= 255)
		spm.setStep("Storing custom fonts");
		ArrayList fonts = new ArrayList();
		CountingSet fontNames = new CountingSet(new TreeMap());
		for (Iterator okit = objects.keySet().iterator(); okit.hasNext();) {
			Object obj = objects.get(okit.next());
			if (obj instanceof PdfFont) {
				fonts.add((PdfFont) obj);
				fontNames.add(((PdfFont) obj).name);
			}
		}
		for (int f = 0; f < fonts.size(); f++) {
			PdfFont pFont = ((PdfFont) fonts.get(f));
			spm.setInfo("Doing font " + pFont.name + " (" + pFont + ")");
			int[] charCodes = pFont.getUsedCharCodes();
			if (charCodes.length == 0) {
				spm.setInfo(" ==> empty font");
				continue;
			}
			spm.setInfo(" - got " + charCodes.length + " characters");
			if (fontNames.getCount(pFont.name) > 1) {
				pFont.name = (pFont.name + "-" + f);
				spm.setInfo(" - renamed to " + pFont.name + " to resolve collision");
			}
			ImFont imFont = new ImFont(doc, pFont.name, pFont.bold, pFont.italics, pFont.serif, pFont.monospaced);
			if (pFont.type != null)
				imFont.setAttribute("type", pFont.type);
			if (pFont.encoding != null)
				imFont.setAttribute("encoding", pFont.encoding);
			if (pFont.ascent != 0)
				imFont.setAttribute("ascent", ("" + pFont.ascent));
			if (pFont.capHeight != 0)
				imFont.setAttribute("capHeight", ("" + pFont.capHeight));
			if (pFont.xHeight != 0)
				imFont.setAttribute("xHeight", ("" + pFont.xHeight));
			if (pFont.descent != 0)
				imFont.setAttribute("descent", ("" + pFont.descent));
			for (int c = 0; c < charCodes.length; c++) {
				String charStr = pFont.getUnicode(charCodes[c]);
				BufferedImage charImage = pFont.getCharImage(charCodes[c]);
				if ((charStr != null) && (charStr.length() == 1))
					charStr = ("" + PdfCharDecoder.getNonCombiningChar(charStr.charAt(0)));
				imFont.addCharacter(pFont.getCharCode(charCodes[c]), charStr, (((charImage == null) || ((charImage.getWidth() * charImage.getHeight()) == 0)) ? null : ImFont.scaleCharImage(charImage)));
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println("   - added char " + charCodes[c] + " (" + pFont.getCharCode(charCodes[c]) + "): '" + charStr + "', image is " + charImage);
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
				splitPageWords(pData[p], pFlippedObjects[p], pInFigureWords[p], pInGraphicsWords[p], pWatermarkWords[p], tokenizer, numberTokenizer, spm);
				
				//	give status update
				if (pData[p].words.length == 0)
					spm.setInfo(" --> empty page");
				else spm.setInfo(" --> got " + pData[p].words.length + " words in PDF");
			}
		};
		ParallelJobRunner.runParallelFor(pf, ((PdfExtractorTest.aimAtPage < 0) ? 0 : PdfExtractorTest.aimAtPage), ((PdfExtractorTest.aimAtPage < 0) ? pData.length : (PdfExtractorTest.aimAtPage + 1)), (this.useMultipleCores ? -1 : 1));
		spm.setProgress(100);
		
		//	did anything go wrong?
		checkException(pf);
		
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
					
					//	compute page box
					BoundingBox pageBox = getBoundingBox(pData[p].pdfPageContentBox, pData[p].pdfPageContentBox, magnification, pData[p].rotate);
					
					//	factor in flipped content
					if ((pFlipContentBox[p] != null) && (pFlipContentDirection[p] == PWord.BOTTOM_UP_FONT_DIRECTION)) {
						spm.setInfo("Got bottom-up block " + pFlipContentBox[p] + " to flip");
//						pageBox = getFlippedContentPageBounds(pageBox, pData[p].pdfPageContentBox, pFlipContentBox[p], pData[p], pFlippedObjects[p], PWord.BOTTOM_UP_FONT_DIRECTION, magnification);
						pageBox = new BoundingBox(pageBox.left, (pageBox.right + Math.max(0, (pFlippedContentBox[p].right - pFlipContentBox[p].right))), pageBox.top, pageBox.bottom);
					}
					else if ((pFlipContentBox[p] != null) && (pFlipContentDirection[p] == PWord.TOP_DOWN_FONT_DIRECTION)) {
						spm.setInfo("Got top-down block " + pFlipContentBox[p] + " to flip");
//						pageBox = getFlippedContentPageBounds(pageBox, pData[p].pdfPageContentBox, pFlipContentBox[p], pData[p], pFlippedObjects[p], PWord.TOP_DOWN_FONT_DIRECTION, magnification);
						pageBox = new BoundingBox(pageBox.left, (pageBox.right + Math.max(0, (pFlippedContentBox[p].right - pFlipContentBox[p].right))), pageBox.top, pageBox.bottom);
					}
					//	TODO_not observe upside-down font direction ==> there won't be any upside-down _blocks_, just individual words in bottom-up or top-down blocks, and those are flipped by now
					
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
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println("Rendering " + po);
						
						//	TODO use rendering implementation of ImWord here
						if (po instanceof PWord) {
							PWord pw = ((PWord) po);
							
							//	skip over water marks (all too faint words that do not lie in image or graphics)
							if ((pWatermarkWords[p] != null) && pWatermarkWords[p].contains(pw)) {
								if (PdfExtractorTest.aimAtPage != -1)
									System.out.println(" ==> skipped as too faint");
								continue;
							}
							
							//	convert bounds, as PDF Y coordinate is bottom-up, whereas Java, JavaScript, etc. Y coordinate is top-down
							BoundingBox wb = getBoundingBox(pw.bounds, pData[p].pdfPageContentBox, magnification, pData[p].rotate);
							if (PdfExtractorTest.aimAtPage != -1)
								System.out.println(" - " + wb + " in page");
							
							//	prepare color, observe background luminosity if we're inside an image or graphics (and in bounds)
//							Color color = Color.BLACK;
//							if ((wb.left < 0) || (wb.top < 0)) {}
//							else if ((wb.getWidth() < 1) || (wb.getHeight() < 1)) {}
//							else if ((pageImages[p].getWidth() < wb.right) || (pageImages[p].getHeight() < wb.bottom)) {}
//							else if (((pInFigureWords[p] == null) || !pInFigureWords[p].contains(pw)) && ((pInGraphicsWords[p] == null) || !pInGraphicsWords[p].contains(pw))) {}
//							else {
//								if (PdfExtractorTest.aimAtPage != -1)
//									System.out.println(" - getting background of " + wb);
//								BufferedImage wi = pageImages[p].getSubimage(wb.left, wb.top, wb.getWidth(), wb.getHeight());
//								AnalysisImage wai = Imaging.wrapImage(wi, null);
//								byte[][] brightness = wai.getBrightness();
//								int brightnessSum = 0;
//								for (int c = 0; c < brightness.length; c++) {
//									for (int r = 0; r < brightness[c].length; r++)
//										brightnessSum += brightness[c][r];
//								}
//								int avgBrightness = (brightnessSum / (wb.getWidth() * wb.getHeight()));
//								if (PdfExtractorTest.aimAtPage != -1)
//									System.out.println(" - average backing figure or graphics brightness is " + avgBrightness);
//								if (avgBrightness < 64)
//									color = Color.WHITE;
//							}
							Color color = ((PdfExtractorTest.aimAtPage == -1) ? pw.color : ((pw.color == Color.RED) ? Color.RED : Color.GREEN));
							piGr.setColor(color);
							
							//	prepare font
							int fontStyle = Font.PLAIN;
							if (pw.bold)
								fontStyle = (fontStyle | Font.BOLD);
							if (pw.italics)
								fontStyle = (fontStyle | Font.ITALIC);
							Font rf = getFont(pw.font.name, fontStyle, pw.serif, pw.monospaced, Math.round(((float) pw.fontSize) * magnification));
							piGr.setFont(rf);
							
							//	adjust word size and position
							AffineTransform preAt = piGr.getTransform();
							FontRenderContext wfrc = new FontRenderContext(preAt, true, true);
							LineMetrics wlm = rf.getLineMetrics(pw.str, wfrc);
							TextLayout wtl = new TextLayout(pw.str, rf, wfrc);
							piGr.translate(wb.left, wb.bottom);
							float leftShift = ((float) -wtl.getBounds().getMinX());
							double hScale = 1;
							
							//	rotate and scale word as required
							if (pw.fontDirection == PWord.LEFT_RIGHT_FONT_DIRECTION) {
								if (pw.italics)
									hScale = (((double) wb.getWidth()) / wtl.getBounds().getWidth());
								else {
									hScale = (((double) wb.getWidth()) / wtl.getAdvance());
									leftShift = 0;
								}
								piGr.scale(hScale, 1);
							}
							else if (pw.fontDirection == PWord.BOTTOM_UP_FONT_DIRECTION) {
								piGr.rotate((-Math.PI / 2), (((float) wb.getWidth()) / 2), -(((float) wb.getWidth()) / 2));
								if (pw.italics)
									hScale = (((double) wb.getHeight()) / wtl.getBounds().getWidth());
								else {
									hScale = (((double) wb.getHeight()) / wtl.getAdvance());
									leftShift = 0;
								}
								piGr.scale(1, hScale);
							}
							else if (pw.fontDirection == PWord.TOP_DOWN_FONT_DIRECTION) {
								piGr.rotate((Math.PI / 2), (((float) wb.getHeight()) / 2), -(((float) wb.getHeight()) / 2));
								if (pw.italics)
									hScale = (((double) wb.getHeight()) / wtl.getBounds().getWidth());
								else {
									hScale = (((double) wb.getHeight()) / wtl.getAdvance());
									leftShift = 0;
								}
								piGr.scale(1, hScale);
							}
							//	TODO_not observe upside-down font direction ==> upside-down words should only occur in bottom-up or top-down blocks, and those are flipped to bottom-up or top-down by now
							
							//	render word, finally ...
							if (PdfExtractorTest.aimAtPage != -1)
								System.out.println("Rendering " + pw.str + ((pw.str.length() == 1) ? (" " + Integer.toString(((int) pw.str.charAt(0)), 16)) : "") + ", hScale is " + hScale);
							try {
								piGr.drawGlyphVector(rf.createGlyphVector(wfrc, pw.str), leftShift, (pw.font.hasDescent ? -Math.round(wlm.getDescent()) : 0));
							} catch (InternalError ie) {}
							
							//	reset graphics
							piGr.setTransform(preAt);
						}
						
						//	render figure
						else if (po instanceof PFigure) {
							PFigure pf = ((PFigure) po);
							
							//	convert bounds, as PDF Y coordinate is bottom-up, whereas Java, JavaScript, etc. Y coordinate is top-down
							BoundingBox fb = getBoundingBox(pf.bounds, pData[p].pdfPageContentBox, magnification, pData[p].rotate);
							if (PdfExtractorTest.aimAtPage != -1) {
								System.out.println(" - bounds are " + pf.bounds);
								System.out.println(" - " + fb + " in page");
							}
							
							//	load and render figure image
							ImSupplement.Figure figureSupplement = ((ImSupplement.Figure) doc.getSupplement((String) pFigureSupplementIDs[p].get(pf)));
							if (figureSupplement != null) try {
								BufferedImage fbi = ImageIO.read(figureSupplement.getInputStream());
								Shape preClip = piGr.getClip();
								AffineTransform preAt = piGr.getTransform();
								Composite preComp = piGr.getComposite();
								
								//	use white-neutral blend mode composite for mask images
								Composite maskComp = null;
								if ((pf.refOrData instanceof PStream) && ((PStream) pf.refOrData).params.containsKey("ImageMask"))
									maskComp = BlendComposite.getInstance("Multiply");
								
								//	observe clipping
								if (pf.clipPaths != null) {
									BoundingBox cb = getBoundingBox(pf.visibleBounds, pData[p].pdfPageContentBox, magnification, pData[p].rotate);
									piGr.clipRect(cb.left, cb.top, cb.getWidth(), cb.getHeight());
									if (PdfExtractorTest.aimAtPage != -1) {
										System.out.println(" - clip bounds are " + pf.visibleBounds);
										System.out.println(" - clipped to " + cb + " in page");
									}
								}
								
								//	render image
								piGr.translate(fb.left, fb.top);
								if (maskComp != null)
									piGr.setComposite(maskComp);
								piGr.drawImage(fbi, 0, 0, fb.getWidth(), fb.getHeight(), null);
								
								//	reset graphics
								piGr.setComposite(preComp);
								piGr.setTransform(preAt);
								piGr.setClip(preClip);
							}
							catch (RuntimeException re) {
								System.out.println("Error rendering figure at " + figureSupplement.getBounds() + ": " + re.getMessage());
								re.printStackTrace(System.out);
							}
						}
						
						//	render path (safe for what we skipped above, i.e., out of page paths, bright on white paths, etc.)
						else if (po instanceof PPath) {
							PPath pp = ((PPath) po);
							if (PdfExtractorTest.aimAtPage != -1) {
								BoundingBox pb = getBoundingBox(pp.getBounds(), pData[p].pdfPageContentBox, magnification, pData[p].rotate);
								System.out.println(" - " + pb + " in page");
							}
							
							Shape preClip = piGr.getClip();
							AffineTransform preAt = piGr.getTransform();
							Composite preComp = piGr.getComposite();
							Color preColor = piGr.getColor();
							Stroke preStroke = piGr.getStroke();
							
							//	observe clipping (we need this despite all sanitization, as some paths draw all over and show only partially)
							if (pp.clipPaths != null) {
								BoundingBox cb = getBoundingBox(pp.visibleBounds, pData[p].pdfPageContentBox, magnification, pData[p].rotate);
								piGr.clipRect(cb.left, cb.top, cb.getWidth(), cb.getHeight());
								if (PdfExtractorTest.aimAtPage != -1)
									System.out.println(" - clipped to " + cb + " in page");
							}
							
							//	adjust graphics (account for size and position differences between page box and page content box, magnification, and switch of top and bottom)
							piGr.translate((-pData[p].pdfPageContentBox.x * magnification), (pData[p].pdfPageContentBox.y * magnification));
							piGr.scale(magnification, -magnification);
							if (pp.blendMode != null)
								piGr.setComposite(pp.blendMode);
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
							
							//	render path
							Path2D path = new Path2D.Float();
							PSubPath[] psps = pp.getSubPaths();
							for (int sp = 0; sp < psps.length; sp++) {
								Path2D subPath = psps[sp].getPath();
								path.append(subPath, false);
							}
							if (pp.fillColor != null) {
								piGr.setColor(pp.fillColor); // TODO track remaining source of MAGENTA fallback !!!
								piGr.fill(path);
								if (PdfExtractorTest.aimAtPage != -1)
									System.out.println(" - filled in " + pp.fillColor + " with a=" + pp.fillColor.getAlpha());
							}
							if (pp.strokeColor != null) {
								piGr.setColor(pp.strokeColor);
								piGr.setStroke(stroke);
								piGr.draw(path);
								if (PdfExtractorTest.aimAtPage != -1)
									System.out.println(" - stroked in " + pp.strokeColor + " with a=" + pp.strokeColor.getAlpha());
							}
							
							//	reset graphics
							piGr.setStroke(preStroke);
							piGr.setColor(preColor);
							piGr.setComposite(preComp);
							piGr.setTransform(preAt);
							piGr.setClip(preClip);
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
		checkException(pf);
		
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
				
				//	nothing to work with TODO also work with full image/graphics pages (plates !!!)
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
					
					//	skip over water marks (all too faint words that do not lie in image or graphics)
					if ((pWatermarkWords[p] != null) && pWatermarkWords[p].contains(pData[p].words[w])) {
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println("Word " + pData[p].words[w] + " skipped as too faint");
						continue;
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
						word.setFontSize(pData[p].words[w].fontSize);
					if (pData[p].words[w].bold)
						word.setAttribute(BOLD_ATTRIBUTE);
					if (pData[p].words[w].italics)
						word.setAttribute(ITALICS_ATTRIBUTE);
					
					//	add font char ID string (using <=255 substitutes for char codes in excess of 255)
					StringBuffer charCodesHex = new StringBuffer();
					for (int c = 0; c < pData[p].words[w].charCodes.length(); c++) {
						String charCodeHex = Integer.toString((((int) pData[p].words[w].charCodes.charAt(c)) & 255), 16).toUpperCase();
						if (charCodeHex.length() < 2)
							charCodesHex.append("0");
						charCodesHex.append(charCodeHex);
					}
					word.setAttribute(ImFont.CHARACTER_CODE_STRING_ATTRIBUTE, charCodesHex.toString());
					if (PdfExtractorTest.aimAtPage != -1) {
						System.out.print("Got word '" + pData[p].words[w].str + "' with char codes '");
						for (int c = 0; c < pData[p].words[w].charCodes.length(); c++)
							System.out.print(((c == 0) ? "" : " ") + Integer.toString(((int) pData[p].words[w].charCodes.charAt(c)), 16));
						System.out.println("' ==> " + charCodesHex);
					}
					
					//	if source PDF word points to a continuation, set next word relation of word just added
					if (pData[p].words[w].joinWithNext)
						word.setNextRelation(ImWord.NEXT_RELATION_CONTINUE);
					
					//	store font direction with word TODO maybe also observe font direction in text analyses
					if (pData[p].words[w].fontDirection == PWord.BOTTOM_UP_FONT_DIRECTION)
						word.setAttribute(ImWord.TEXT_DIRECTION_ATTRIBUTE, ImWord.TEXT_DIRECTION_BOTTOM_UP);
					else if (pData[p].words[w].fontDirection == PWord.TOP_DOWN_FONT_DIRECTION)
						word.setAttribute(ImWord.TEXT_DIRECTION_ATTRIBUTE, ImWord.TEXT_DIRECTION_TOP_DOWN);
					else if (pData[p].words[w].fontDirection == PWord.UPSIDE_DOWN_FONT_DIRECTION)
						word.setAttribute(ImWord.TEXT_DIRECTION_ATTRIBUTE, ImWord.TEXT_DIRECTION_RIGHT_LEFT_UPSIDE_DOWN);
				}
			}
		};
		ParallelJobRunner.runParallelFor(pf, ((PdfExtractorTest.aimAtPage < 0) ? 0 : PdfExtractorTest.aimAtPage), ((PdfExtractorTest.aimAtPage < 0) ? pData.length : (PdfExtractorTest.aimAtPage + 1)), (this.useMultipleCores ? -1 : 1));
		spm.setProgress(100);
		
		//	check errors
		checkException(pf);
		
		//	now that we have words, we can get a document style and use it for page structure analysis
		ImDocumentStyle docStyle = ImDocumentStyle.getStyleFor(doc);
		if (docStyle == null)
			docStyle = new ImDocumentStyle(new PropertiesData(new Properties()));
		final ImDocumentStyle docLayout = docStyle.getImSubset("layout");
		
		//	check if we need to dispose of any cover pages
		final int coverPageCount = docLayout.getIntProperty("coverPageCount", 0);
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
		final ImWord[] pageFirstWords = new ImWord[pData.length];
		final ImWord[] pageLastWords = new ImWord[pData.length];
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
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println(" - content area is " + contentArea + " [from " + docLayout.getStringProperty("contentArea", null) + "]");
				int columnCount = docLayout.getIntProperty("columnCount", -1);
				
				//	we've had a block flip, extend content area to square to prevent content cutoff
				if (pFlipContentBox[p] != null) {
					if (contentArea != pages[p].bounds)
						contentArea = new BoundingBox(contentArea.left, (contentArea.left + (contentArea.bottom - contentArea.top)), contentArea.top, contentArea.bottom);
					//	TODO might even be wider than square if we only flip one of two columns !!!
					if (columnCount != -1)
						columnCount = 1;
				}
				//	TODO revise this, especially column count (mixed-orientation pages !!!)
				
				//	index words by bounding boxes, determine page content bounds, and compute average word height
				ImWord[] pWords = pages[p].getWords();
				HashMap wordsByBoxes = new HashMap();
				int pContentLeft = Integer.MAX_VALUE;
				int pContentRight = 0;
				int pContentTop = Integer.MAX_VALUE;
				int pContentBottom = 0;
				int wordHeightCount = 0;
				int wordHeightSum = 0;
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
					
					//	factor into average word height
					wordHeightCount++;
					wordHeightSum += pWords[w].bounds.getHeight();
				}
				int avgWordHeight = ((wordHeightCount == 0) ? 0 : (wordHeightSum / wordHeightCount));
				
				//	collect page figures and graphics
				ImSupplement[] pageSupplements;
				synchronized (doc) { // need to synchronize accessing supplements
					pageSupplements = pages[p].getSupplements();
				}
				ArrayList figureList = new ArrayList();
				ArrayList graphicsList = new ArrayList();
				for (int s = 0; s < pageSupplements.length; s++) {
					if (pageSupplements[s] instanceof Figure)
						figureList.add(pageSupplements[s]);
					else if (pageSupplements[s] instanceof Graphics)
						graphicsList.add(pageSupplements[s]);
				}
				Figure[] pageFigures = ((Figure[]) figureList.toArray(new Figure[figureList.size()]));
				Graphics[] pageGraphics = ((Graphics[]) graphicsList.toArray(new Graphics[graphicsList.size()]));
				
				//	also observe figures in page content
				for (int f = 0; f < pageFigures.length; f++) {
					BoundingBox fbb = pageFigures[f].getBounds();
					if (pageFigures[f].getClipBounds() != null)
						fbb = fbb.intersect(pageFigures[f].getClipBounds());
					if (fbb == null)
						continue; // no intersection between bounding box and clipping area
					if ((pFlipContentBox[p] == null) && !contentArea.includes(fbb, true))
						continue; // unless we have flipped content, omit figures whose center is outside content bounds
					pContentLeft = Math.min(pContentLeft, fbb.left);
					pContentRight = Math.max(pContentRight, fbb.right);
					pContentTop = Math.min(pContentTop, fbb.top);
					pContentBottom = Math.max(pContentBottom, fbb.bottom);
				}
				
				//	also observe graphics in page content (unless they are layout artwork)
				for (int g = 0; g < pageGraphics.length; g++) {
					if (pageGraphics[g].hasAttribute(Graphics.PAGE_DECORATION_ATTRIBUTE))
						continue;
					BoundingBox gbb = pageGraphics[g].getBounds();
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println(" - graphics bounding box is " + gbb);
					if (pageGraphics[g].getClipBounds() != null) {
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println(" - graphics clip area is " + pageGraphics[g].getClipBounds());
						gbb = gbb.intersect(pageGraphics[g].getClipBounds());
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println(" - graphics visible area is " + gbb);
					}
					if ((pFlipContentBox[p] == null) && !contentArea.includes(gbb, true))
						continue; // unless we have flipped content, omit graphics whose center is outside content bounds
					pContentLeft = Math.min(pContentLeft, gbb.left);
					pContentRight = Math.max(pContentRight, gbb.right);
					pContentTop = Math.min(pContentTop, gbb.top);
					pContentBottom = Math.max(pContentBottom, gbb.bottom);
				}
				
				//	aggregate page content box
				BoundingBox pContentBox = new BoundingBox(pContentLeft, pContentRight, pContentTop, pContentBottom);
				
				//	collect text boxes, as well as words contained in them (no need to go for table grids here, though)
				ArrayList pTextBoxGraphics = new ArrayList();
				Set pInTextBoxWordBounds = new HashSet();
				for (int g = 0; g < pageGraphics.length; g++) {
					if (pageGraphics[g].hasAttribute(Graphics.PAGE_DECORATION_ATTRIBUTE))
						continue;
					BoundingBox gBounds = pageGraphics[g].getBounds();
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println("Checking graphics at " + gBounds + " for text box");
					if (gBounds.getHeight() < avgWordHeight) {
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println(" ==> too low for a text box");
						continue;
					}
					if (gBounds.getWidth() < imageDPIs[p]) {
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println(" ==> too narrow for a text box");
						continue;
					}
					boolean gIsInvisible = true;
					Path[] gPaths = pageGraphics[g].getPaths();
					for (int gp = 0; gp < gPaths.length; gp++) {
						Color gStrokeColor = gPaths[gp].getStrokeColor();
						if (gStrokeColor == null) {}
						else if (gStrokeColor.getAlpha() < 32) {}
						else if (getBrightness(gStrokeColor) > 0.95) {}
						else {
							gIsInvisible = false;
							break;
						}
						Color gFillColor = gPaths[gp].getFillColor();
						if (gFillColor == null) {}
						else if (gFillColor.getAlpha() < 32) {}
						else if (getBrightness(gFillColor) > 0.95) {}
						else {
							gIsInvisible = false;
							break;
						}
					}
					if (gIsInvisible) {
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println(" ==> too faint for a text box");
						continue;
					}
					int gArea = gBounds.getArea();
					int gWordAreaFuzzy = 0;
					int gWordAreaStrict = 0;
					for (Iterator wbbit = wordsByBoxes.keySet().iterator(); wbbit.hasNext();) {
						BoundingBox wbb = ((BoundingBox) wbbit.next());
						if (gBounds.includes(wbb, true))
							gWordAreaFuzzy += wbb.getArea();
						if (gBounds.includes(wbb, false))
							gWordAreaStrict += wbb.getArea();
					}
					if (PdfExtractorTest.aimAtPage != -1) {
						System.out.println(" - area is " + gArea);
						System.out.println(" - word area is " + gWordAreaFuzzy + " fuzzy, " + gWordAreaStrict + " strict");
						System.out.println(" - word percentage is " + ((gWordAreaFuzzy * 100) / gArea) + " fuzzy, " + ((gWordAreaStrict * 100) / gArea) + " strict");
					}
					if ((gWordAreaFuzzy * 2) < gArea) {
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println(" ==> not a text box (too few words)");
						continue;
					}
					int gPathWordAreaFuzzy = 0;
					HashSet gPathWordsCountedFuzzy = new HashSet();
					int gPathWordAreaStrict = 0;
					HashSet gPathWordsCountedStrict = new HashSet();
					int gSubPathWordAreaFuzzy = 0;
					HashSet gSubPathWordsCountedFuzzy = new HashSet();
					int gSubPathWordAreaStrict = 0;
					HashSet gSubPathWordsCountedStrict = new HashSet();
					for (int gp = 0; gp < gPaths.length; gp++) {
						BoundingBox gPathBounds = gPaths[gp].bounds;
						System.out.println(" - checking path at " + gPathBounds);
						for (Iterator wbbit = wordsByBoxes.keySet().iterator(); wbbit.hasNext();) {
							BoundingBox wbb = ((BoundingBox) wbbit.next());
							if (gPathBounds.includes(wbb, true) && gPathWordsCountedFuzzy.add(wbb))
								gPathWordAreaFuzzy += wbb.getArea();
							if (gPathBounds.includes(wbb, false) && gPathWordsCountedStrict.add(wbb))
								gPathWordAreaStrict += wbb.getArea();
						}
						SubPath[] gSubPaths = gPaths[gp].getSubPaths();
						for (int sp = 0; sp < gSubPaths.length; sp++) {
							BoundingBox gSubPathBounds = gSubPaths[sp].bounds;
							System.out.println("   - checking sub path at " + gSubPathBounds);
							for (Iterator wbbit = wordsByBoxes.keySet().iterator(); wbbit.hasNext();) {
								BoundingBox wbb = ((BoundingBox) wbbit.next());
								if (gSubPathBounds.includes(wbb, true) && gSubPathWordsCountedFuzzy.add(wbb))
									gSubPathWordAreaFuzzy += wbb.getArea();
								if (gSubPathBounds.includes(wbb, false) && gSubPathWordsCountedStrict.add(wbb))
									gSubPathWordAreaStrict += wbb.getArea();
							}
						}
					}
					if (PdfExtractorTest.aimAtPage != -1) {
						System.out.println(" - area is " + gArea);
						System.out.println(" - path word area is " + gPathWordAreaFuzzy + " fuzzy, " + gPathWordAreaStrict + " strict");
						System.out.println(" - path word percentage is " + ((gPathWordAreaFuzzy * 100) / gArea) + " fuzzy, " + ((gPathWordAreaStrict * 100) / gArea) + " strict");
						System.out.println(" - sub path word area is " + gSubPathWordAreaFuzzy + " fuzzy, " + gSubPathWordAreaStrict + " strict");
						System.out.println(" - sub path word percentage is " + ((gSubPathWordAreaFuzzy * 100) / gArea) + " fuzzy, " + ((gSubPathWordAreaStrict * 100) / gArea) + " strict");
					}
					if ((gPathWordAreaFuzzy * 2) < gWordAreaFuzzy) {
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println(" ==> not a text box (too few words inside actual paths)");
						continue;
					}
					if ((gSubPathWordAreaFuzzy * 2) < gPathWordAreaFuzzy) {
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println(" ==> not a text box (too few words inside actual sub paths)");
						continue;
					}
					//	TODO maybe also check if contained words have any discernable layout
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println(" ==> text box, analyzing structure");
					pTextBoxGraphics.add(pageGraphics[g]);
					for (Iterator wbbit = wordsByBoxes.keySet().iterator(); wbbit.hasNext();) {
						BoundingBox wbb = ((BoundingBox) wbbit.next());
						if (gBounds.overlaps(wbb))
							pInTextBoxWordBounds.add(wbb);
					}
				}
				
				/* TODO Further criteria for applying column cross split repair:
	ULTIMATELY, infer column areas (in absence of templates):
	- compute and collect preliminary blocking of all pages
	- collect height weighted distribution of block width
	  - and maybe even distinguish even and odd pages
	    (some journal might have some graphics on edge of either side ...)
	- use proximity of actual block width to average block width to establish column areas as weighted average of combined transitive hull of block bounds
	  - use actual left and right bounds only now
	  - add some averaged out slack on either side (there has to be some margin ...)
	- use resulting column areas and minimum column margin to establish page structure
				 */
				
				//	process page main text, and get last main text word in page
				Set pageStructImageRegions = new HashSet();
				ImWord pageLastWord = analyzeRegionStructure(pData[p], (p - coverPageCount), pages[p], pContentBox, imageDPIs[p], magnification, docLayout, null, wordsByBoxes, pWatermarkWords[p], pInTextBoxWordBounds, pageStructImageRegions, spm);
				LinkedList pageWords = null;
				if (pageLastWord != null) {
					pageWords = new LinkedList();
					pageWords.addFirst(pageLastWord);
					while ((pageLastWord = pageLastWord.getPreviousWord()) != null)
						pageWords.addFirst(pageLastWord);
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println("First page word is '" + ((ImWord) pageWords.getFirst()).getString() + "', last is '" + ((ImWord) pageWords.getLast()).getString() + "'");
				}
				
				//	process any text boxes
				for (int g = 0; g < pTextBoxGraphics.size(); g++)
					analyzeRegionStructure(pData[p], (p - coverPageCount), pages[p], pContentBox, imageDPIs[p], magnification, docLayout, ((Graphics) pTextBoxGraphics.get(g)), wordsByBoxes, pWatermarkWords[p], pInTextBoxWordBounds, null, spm);
				
				//	add regions created from figure and graphics supplements, converting any contained words to labels
				addFiguresAndGraphics(pages[p], pContentBox, pageImage, pageFigures, pageGraphics);
				
				//	adjust bounding boxes
				shrinkToChildren(pages[p], LINE_ANNOTATION_TYPE, WORD_ANNOTATION_TYPE, -1);
				ImRegion[] blockRemainders = shrinkToChildren(pages[p], BLOCK_ANNOTATION_TYPE, LINE_ANNOTATION_TYPE, imageDPIs[p]);
				
				//	preserve image blocks that were attached to text blocks
				if (blockRemainders.length != 0) {
					AnalysisImage api = Imaging.wrapImage(pageImage, null);
					Imaging.whitenWhite(api);
					ImagePartRectangle pageBounds = Imaging.getContentBox(api);
					for (int r = 0; r < blockRemainders.length; r++) {
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println("Got block remainder at " + blockRemainders[r].bounds);
						ImagePartRectangle brBox = pageBounds.getSubRectangle(blockRemainders[r].bounds.left, blockRemainders[r].bounds.right, blockRemainders[r].bounds.top, blockRemainders[r].bounds.bottom);
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
				}
				
				//	aggregate image and graphics regions
				handleImagesAndGraphics(pages[p], pageStructImageRegions);
				
				//	find first and last main text words in page (if any)
				if (pageWords != null) {
					while ((pageWords.size() != 0) && !ImWord.TEXT_STREAM_TYPE_MAIN_TEXT.equals(((ImWord) pageWords.getFirst()).getTextStreamType()))
						pageWords.removeFirst();
					while ((pageWords.size() != 0) && !ImWord.TEXT_STREAM_TYPE_MAIN_TEXT.equals(((ImWord) pageWords.getLast()).getTextStreamType()))
						pageWords.removeLast();
					if (pageWords.size() != 0) {
						pageFirstWords[p] = ((ImWord) pageWords.getFirst());
						while ((pageFirstWords[p].getPreviousWord() != null) && (pageFirstWords[p].getPreviousWord().pageId == pageFirstWords[p].pageId))
							pageFirstWords[p] = pageFirstWords[p].getPreviousWord();
						pageLastWords[p] = ((ImWord) pageWords.getLast());
						while ((pageLastWords[p].getNextWord() != null) && (pageLastWords[p].getNextWord().pageId == pageLastWords[p].pageId))
							pageLastWords[p] = pageLastWords[p].getNextWord();
					}
				}
				
				//	catch empty page
				if (pages[p].getWords().length == 0)
					return;
				
				//	do structure analysis
				ImDocumentStyle blockLayout = docLayout.getImSubset("block");
				ImRegion[] pageBlocks = pages[p].getRegions(BLOCK_ANNOTATION_TYPE);
				for (int b = 0; b < pageBlocks.length; b++) {
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println(" - analyzing block " + pageBlocks[b].bounds);
					PageAnalysis.splitIntoParagraphs(pages[p], imageDPIs[p], pageBlocks[b], blockLayout);
				}
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
		checkException(pf);
		
		//	finalize text stream structure
		spm.setStep("Analyzing text stream structure");
		spm.setBaseProgress(98);
		spm.setProgress(0);
		spm.setMaxProgress(100);
		ImWord lastWord = null;
		for (int p = 0; p < pages.length; p++) {
			spm.setProgress((p * 100) / pages.length);
			if ((pages[p] != null) && ((PdfExtractorTest.aimAtPage == -1) || (p == PdfExtractorTest.aimAtPage)))
				lastWord = addTextStreamStructure(pages[p], lastWord, pageFirstWords[p]);
		}
		spm.setProgress(100);
		spm.setInfo(" - word sequence analysis done");
	}
	
	private static void filterOffPageWords(PPageData pData, ProgressMonitor spm) {
		if ((pData == null) || (pData.words == null))
			return; // nothing to work with
		ArrayList pWords = new ArrayList();
		double pLeft = pData.pdfPageContentBox.getMinX();
		//double pRight = pData.pdfPageContentBox.getMaxX();
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
	}
	
	private void removePageEdgeWatermarks(final PPageData[] pData, final boolean isEmbeddedOCR, final int ignoreStart, final int ignoreEnd, final ProgressMonitor spm) throws IOException {
		
		//	catch word sequences up or down vertical page edges, as well as ones across top or bottom ("Downloaded from ...")
		final PWordSet[] leftEdgeWords = new PWordSet[pData.length];
		final PWordSet[] rightEdgeWords = new PWordSet[pData.length];
		final PWordSet[] topEdgeWords = new PWordSet[pData.length];
		final PWordSet[] bottomEdgeWords = new PWordSet[pData.length];
		final int[] fontSizeSums = new int[pData.length];
		final int[] fontSizeCounts = new int[pData.length];
		spm.setStep("Removing watermarks along page margins");
		spm.setBaseProgress(0);
		spm.setProgress(0);
		spm.setMaxProgress(33);
		ParallelFor pf = new ParallelFor() {
			public void doFor(int p) throws Exception {
				
				//	initialize data structures
				leftEdgeWords[p] = new PWordSet();
				rightEdgeWords[p] = new PWordSet();
				topEdgeWords[p] = new PWordSet();
				bottomEdgeWords[p] = new PWordSet();
				fontSizeSums[p] = 0;
				fontSizeCounts[p] = 0;
				
				//	nothing to work with
				if ((pData[p] == null) || (pData[p].words == null))
					return;
				
				//	page marked for ignoring
				if (p < ignoreStart)
					return;
				if (pData.length <= (p + ignoreEnd))
					return;
				
				//	update status display (might be inaccurate, but better than lock escalation)
				spm.setProgress((p * 100) / pData.length);
				
				//	collect vertically rendered words from page edges
				for (int w = 0; w < pData[p].words.length; w++) {
					
					//	check word position in page, and store word hash accordingly
					if ((pData[p].words[w].fontDirection == PWord.BOTTOM_UP_FONT_DIRECTION) || (pData[p].words[w].fontDirection == PWord.TOP_DOWN_FONT_DIRECTION)) {
						if (pData[p].words[w].bounds.getMaxX() < ((pData[p].pdfPageBox.width * 1) / 10))
							leftEdgeWords[p].add(pData[p].words[w]);
						else if (pData[p].words[w].bounds.getMinX() > ((pData[p].pdfPageBox.width * 9) / 10))
							rightEdgeWords[p].add(pData[p].words[w]);
					}
					else if (pData[p].words[w].fontDirection == PWord.LEFT_RIGHT_FONT_DIRECTION) {
						if (pData[p].words[w].bounds.getMaxY() < ((pData[p].pdfPageBox.height * 1) / 10))
							bottomEdgeWords[p].add(pData[p].words[w]);
						else if (pData[p].words[w].bounds.getMinY() > ((pData[p].pdfPageBox.height * 9) / 10))
							topEdgeWords[p].add(pData[p].words[w]);
					}
					
					//	aggregate font size
					fontSizeSums[p] += pData[p].words[w].fontSize;
					fontSizeCounts[p]++;
				}
			}
		};
		ParallelJobRunner.runParallelFor(pf, ((PdfExtractorTest.aimAtPage < 0) ? 0 : PdfExtractorTest.aimAtPage), ((PdfExtractorTest.aimAtPage < 0) ? pData.length : (PdfExtractorTest.aimAtPage + 1)), (this.useMultipleCores ? -1 : 1));
		spm.setProgress(100);
		
		//	check errors
		checkException(pf);
		
		//	collect vertical extents of repeating and non-repeating words
		final int[] maxRepeatingTopWordBottoms = new int[pData.length];
		Arrays.fill(maxRepeatingTopWordBottoms, Integer.MIN_VALUE);
		final int[] minRepeatingBottomWordTops = new int[pData.length];
		Arrays.fill(minRepeatingBottomWordTops, Integer.MAX_VALUE);
		final int[] minNonRepeatingWordTops = new int[pData.length];
		Arrays.fill(minNonRepeatingWordTops, Integer.MAX_VALUE);
		final int[] maxNonRepeatingWordBottoms = new int[pData.length];
		Arrays.fill(maxNonRepeatingWordBottoms, Integer.MIN_VALUE);
		final int repeatingWordPageFrequencyTolerance = (((pData.length < 2) ? -1 : ((pData.length < 4) ? 0 : 1)) + ignoreStart + ignoreEnd);
		
		//	catch and remove word sequences up or down vertical page edges ("Downloaded from ...")
		spm.setBaseProgress(33);
		spm.setProgress(0);
		spm.setMaxProgress(67);
		pf = new ParallelFor() {
			public void doFor(int p) throws Exception {
				
				//	nothing to work with
				if ((pData[p] == null) || (pData[p].words == null))
					return;
				
				//	page marked for ignoring
				if (p < ignoreStart)
					return;
				if (pData.length <= (p + ignoreEnd))
					return;
				
				//	update status display (might be inaccurate, but better than lock escalation)
				spm.setProgress((p * 100) / pData.length);
				
				//	eliminate all vertically rendered words existing on every page edge
				ArrayList pageWords = new ArrayList();
				for (int w = 0; w < pData[p].words.length; w++) {
					
					//	check word position in page, and sort out ones present in all pages
					if ((pData[p].words[w].fontDirection == PWord.BOTTOM_UP_FONT_DIRECTION) || (pData[p].words[w].fontDirection == PWord.TOP_DOWN_FONT_DIRECTION)) {
						if (pData[p].words[w].bounds.getMaxX() < ((pData[p].pdfPageBox.width * 1) / 10)) {}
						else if (pData[p].words[w].bounds.getMinX() > ((pData[p].pdfPageBox.width * 9) / 10)) {}
						else {
							pageWords.add(pData[p].words[w]);
							continue;
						}
					}
					else {
						pageWords.add(pData[p].words[w]);
//						if (pData[p].words[w].bounds.getMaxY() < ((pData[p].pdfPageBox.height * 1) / 10)) {}
//						else if (pData[p].words[w].bounds.getMinY() > ((pData[p].pdfPageBox.height * 9) / 10)) {}
//						else continue;
//						//	TODOne we need ALL THE WORDS in horizontal direction, as otherwise non-repeating boundaries might end up empty !!!
					}
					
					//	get word bounds (they tend to be identical on every page for download portal markings)
					BoundingBox wb = getBoundingBox(pData[p].words[w].bounds, pData[p].pdfPageContentBox, 1, 0);
					
					//	compute page frequency
					int leftCount = 0;
					int rightCount = 0;
					int topCount = 0;
					int bottomCount = 0;
					for (int cp = 0; cp < pData.length; cp++) {
						if (pData[cp] == null) {}
						else if (leftEdgeWords[cp].contains(pData[p].words[w]))
							leftCount++;
						else if (rightEdgeWords[cp].contains(pData[p].words[w]))
							rightCount++;
						else if (topEdgeWords[cp].contains(pData[p].words[w]))
							topCount++;
						else if (bottomEdgeWords[cp].contains(pData[p].words[w]))
							bottomCount++;
					}
					
					//	remove vertical word if present on all pages (cut one page of slack with 4 or more pages)
					if ((pData[p].words[w].fontDirection == PWord.BOTTOM_UP_FONT_DIRECTION) || (pData[p].words[w].fontDirection == PWord.TOP_DOWN_FONT_DIRECTION)) {
						if (((leftCount + repeatingWordPageFrequencyTolerance) < pData.length) && ((rightCount + repeatingWordPageFrequencyTolerance) < pData.length)) {
//						if (pData[p].words[w].fontDirection != PWord.TOP_DOWN_FONT_DIRECTION) // TODOne reactivate frequency check
							pageWords.add(pData[p].words[w]);
							System.out.println("Retained vertical page edge word (" + p + "," + leftCount + "," + rightCount + "): " + pData[p].words[w].str + " at " + pData[p].words[w].bounds);
						}
						else System.out.println("Removed vertical page edge watermark (" + p + "," + leftCount + "," + rightCount + "): " + pData[p].words[w].str + " at " + pData[p].words[w].bounds);
					}
					
					//	collect extent of horizontal page edge words if present on all pages (cut one page of slack above with 4 or more pages)
					else {
						if ((topCount + repeatingWordPageFrequencyTolerance) < pData.length)
							minNonRepeatingWordTops[p] = Math.min(minNonRepeatingWordTops[p], wb.top); // should wreck no havoc even if repeating at bottom
						else maxRepeatingTopWordBottoms[p] = Math.max(maxRepeatingTopWordBottoms[p], wb.bottom);
						if ((bottomCount + repeatingWordPageFrequencyTolerance) < pData.length)
							maxNonRepeatingWordBottoms[p] = Math.max(maxNonRepeatingWordBottoms[p], wb.bottom); // should wreck no havoc even if repeating at top
						else minRepeatingBottomWordTops[p] = Math.min(minRepeatingBottomWordTops[p], wb.top);
					}
				}
				
				//	removed anything?
				if (pageWords.size() < pData[p].words.length)
					pData[p].words = ((PWord[]) pageWords.toArray(new PWord[pageWords.size()]));
			}
		};
		ParallelJobRunner.runParallelFor(pf, ((PdfExtractorTest.aimAtPage < 0) ? 0 : PdfExtractorTest.aimAtPage), ((PdfExtractorTest.aimAtPage < 0) ? pData.length : (PdfExtractorTest.aimAtPage + 1)), (this.useMultipleCores ? -1 : 1));
		spm.setProgress(100);
		
		//	check errors
		checkException(pf);
		
		//	compute average font size
		int fontSizeSum = 0;
		int fontSizeCount = 0;
		for (int p = 0; p < pData.length; p++) {
			fontSizeSum += fontSizeSums[p];
			fontSizeCount += fontSizeCounts[p];
		}
		final int avgFontSize = ((fontSizeCount == 0) ? -1 : ((fontSizeSum + (fontSizeCount / 2)) / fontSizeCount));
		
		//	catch and remove repeating word sequences across top or bottom ("Downloaded from ...")
		spm.setBaseProgress(67);
		spm.setProgress(0);
		spm.setMaxProgress(100);
		pf = new ParallelFor() {
			public void doFor(int p) throws Exception {
				
				//	nothing to work with
				if ((pData[p] == null) || (pData[p].words == null))
					return;
				
				//	page marked for ignoring
				if (p < ignoreStart)
					return;
				if (pData.length <= (p + ignoreEnd))
					return;
				
				//	update status display (might be inaccurate, but better than lock escalation)
				spm.setProgress((p * 100) / pData.length);
				System.out.println("Checking horizontal page edge watermarks (" + p + "):");
				System.out.println(" - non-repeating words between " + minNonRepeatingWordTops[p] + " and " + maxNonRepeatingWordBottoms[p]);
				System.out.println(" - repeating words above " + maxRepeatingTopWordBottoms[p] + " and below " + minRepeatingBottomWordTops[p]);
				
				//	anything to work with on either horizontal page edge?
				boolean removeTop = false;
				if ((maxRepeatingTopWordBottoms[p] != Integer.MIN_VALUE) && (minNonRepeatingWordTops[p] != Integer.MAX_VALUE) && (maxRepeatingTopWordBottoms[p] <= minNonRepeatingWordTops[p])) {
					removeTop = true;
					System.out.println(" ==> removing top page edge watermarks above " + maxRepeatingTopWordBottoms[p] + " <= " + minNonRepeatingWordTops[p]);
				}
				boolean removeBottom = false;
				if ((minRepeatingBottomWordTops[p] != Integer.MAX_VALUE) && (maxNonRepeatingWordBottoms[p] != Integer.MIN_VALUE) && (maxNonRepeatingWordBottoms[p] <= minRepeatingBottomWordTops[p])) {
					removeBottom = true;
					System.out.println(" ==> removing bottom page edge watermarks below " + minRepeatingBottomWordTops[p] + " >= " + maxNonRepeatingWordBottoms[p]);
				}
				if (!removeTop && !removeBottom)
					return;
				
				//	eliminate all vertically rendered words existing on every page edge
				ArrayList pageWords = new ArrayList();
				for (int w = 0; w < pData[p].words.length; w++) {
					
					//	check word position in page, and sort out ones present in all pages
					if ((pData[p].words[w].fontDirection == PWord.BOTTOM_UP_FONT_DIRECTION) || (pData[p].words[w].fontDirection == PWord.TOP_DOWN_FONT_DIRECTION)) {
						pageWords.add(pData[p].words[w]);
						continue;
					}
					else if ((pData[p].words[w].fontDirection == PWord.LEFT_RIGHT_FONT_DIRECTION) && (isEmbeddedOCR || ((pData[p].words[w].fontSize + 1) < avgFontSize))) {
						if (removeBottom && (pData[p].words[w].bounds.getMaxY() < ((pData[p].pdfPageBox.height * 1) / 10))) {}
						else if (removeTop && (pData[p].words[w].bounds.getMinY() > ((pData[p].pdfPageBox.height * 9) / 10))) {}
						else {
							pageWords.add(pData[p].words[w]);
							continue;
						}
					}
					else {
						pageWords.add(pData[p].words[w]);
						continue;
					}
					
					//	get word bounds (they tend to be identical on every page for download portal markings)
					BoundingBox wb = getBoundingBox(pData[p].words[w].bounds, pData[p].pdfPageContentBox, 1, 0);
					
					//	remove repeating top and bottom edge words
					if (removeTop && (wb.bottom <= maxRepeatingTopWordBottoms[p]))
						System.out.println("Removed top page edge watermark (" + p + "): " + pData[p].words[w].str + " at " + pData[p].words[w].bounds);
					else if (removeBottom && (minRepeatingBottomWordTops[p] <= wb.top))
						System.out.println("Removed bottom page edge watermark (" + p + "): " + pData[p].words[w].str + " at " + pData[p].words[w].bounds);
					else pageWords.add(pData[p].words[w]);
				}
				
				//	removed anything?
				if (pageWords.size() < pData[p].words.length)
					pData[p].words = ((PWord[]) pageWords.toArray(new PWord[pageWords.size()]));
			}
		};
		ParallelJobRunner.runParallelFor(pf, ((PdfExtractorTest.aimAtPage < 0) ? 0 : PdfExtractorTest.aimAtPage), ((PdfExtractorTest.aimAtPage < 0) ? pData.length : (PdfExtractorTest.aimAtPage + 1)), (this.useMultipleCores ? -1 : 1));
		spm.setProgress(100);
		
		//	check errors
		checkException(pf);
	}
	
	private static class PWordSet {
		HashSet pWordHashes = new HashSet();
		ArrayList pWords = new ArrayList();
		void add(PWord word) {
			String wh = (word.charCodes + "@" + word.font.name);
			this.pWordHashes.add(wh);
			String whp = (word.charCodes + "@" + word.font.name + "@" + word.bounds);
			this.pWordHashes.add(whp);
			this.pWords.add(word);
		}
		boolean contains(PWord word) {
			String wh = (word.charCodes + "@" + word.font.name);
			if (!this.pWordHashes.contains(wh))
				return false;
			String whp = (word.charCodes + "@" + word.font.name + "@" + word.bounds);
			if (this.pWordHashes.contains(whp))
				return true;
			for (int w = 0; w < this.pWords.size(); w++) {
				PWord cWord = ((PWord) this.pWords.get(w));
				if (!cWord.charCodes.equals(word.charCodes))
					continue;
				if (!cWord.font.name.equals(word.font.name))
					continue;
				if (cWord.fontSize != word.fontSize)
					continue;
				if (cWord.fontDirection != word.fontDirection)
					continue;
				if (!cWord.bounds.intersects(word.bounds))
					continue;
				Rectangle2D.Float iBounds = new Rectangle2D.Float();
				Rectangle2D.intersect(cWord.bounds, word.bounds, iBounds);
				if (((iBounds.getWidth() * iBounds.getHeight()) * 10) < ((word.bounds.getWidth() * word.bounds.getHeight()) * 9))
					continue;
				return true;
			}
			return false;
		}
	}
	
	private static void addFiguresAndGraphics(ImPage page, BoundingBox pageContentBox, BufferedImage pageImage, Figure[] pageFigures, Graphics[] pageGraphics) {
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
		
		//	collect raw bounds of all figures (excluding line figures, though)
		ArrayList pageFigureBounds = new ArrayList();
		for (int f = 0; f < pageFigures.length; f++) {
			
			//	check figure dimensions, using clip bounds if available, as they're the visible part
			BoundingBox fbb = pageFigures[f].getBounds();
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println("Checking figure at " + fbb);
			if (pageFigures[f].getClipBounds() != null) {
				fbb = fbb.intersect(pageFigures[f].getClipBounds());
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println(" - visible area is " + fbb);
				if (fbb == null) {
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println(" ==> empty figure");
					continue;
				}
			}
			
			//	cut white edges
			fbb = shrinkToContent(fbb, apiBrightness);
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" - shrunk to " + fbb);
			if (fbb == null) {
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println(" ==> empty figure");
				continue;
			}
			
			//	check size (actual content figures tend to exhibit some size, as opposed to logos, etc.)
			if ((fbb.getWidth() < (page.getImageDPI() / 10)) || (fbb.getHeight() < (page.getImageDPI() / 10))) {
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println(" ==> too narrow or low for actual image, ignored as layout artwork");
				continue;
			}
			
			//	inspect figure image proper
			try {
				BufferedImage fbi = ImageIO.read(pageFigures[f].getInputStream());
				AnalysisImage afbi = Imaging.wrapImage(fbi, null);
				Imaging.whitenWhite(afbi);
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println(" - got figure image sized " + fbi.getWidth() + "x" + fbi.getHeight());
				
				//	re-get page brightness
				byte[][] afbiBrightness = afbi.getBrightness();
				BoundingBox fbibb = new BoundingBox(0, fbi.getWidth(), 0, fbi.getHeight());
				fbibb = shrinkToContent(fbibb, afbiBrightness);
				if (fbibb == null) {
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println(" ==> empty figure");
					continue;
				}
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println(" - shrunk to " + fbibb.getWidth() + "x" + fbibb.getHeight());
				if ((fbibb.getWidth() < (pageFigures[f].getDpi() / 10)) || (fbibb.getHeight() < (pageFigures[f].getDpi() / 10))) {
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println(" ==> too narrow or low for actual image, ignored as layout artwork");
					continue;
				}
			}
			catch (IOException ioe) {
				ioe.printStackTrace(System.out);
			}
			
			//	this one looks OK
			pageFigureBounds.add(new ClusterBoundingBox(pageFigures[f].getBounds(), false));
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" - retained");
		}
		
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
			fbb = expandToWords(fbb, page, false);
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" - expanded to " + fbb);
			if (fbb == null) {
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println(" ==> empty figure");
				continue;
			}
			
			//	mark image region (if dimensions are sensible)
			if ((fbb.getWidth() > (page.getImageDPI() / 2)) && (fbb.getHeight() > (page.getImageDPI() / 3))) {
				new ImRegion(page, fbb, ImRegion.IMAGE_TYPE);
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println(" ==> added to page");
			}
			else if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" ==> too narrow or low for actual image, ignored as layout artwork");
			
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
			
			//	use visible paths only
			gbb = getVisiblePathBounds(pageGraphics[g]);
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" - visible paths are in " + gbb);
			if (gbb == null) {
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println(" ==> empty graphics");
				continue;
			}
			
			//	shrink to content
			gbb = shrinkToContent(gbb, apiBrightness);
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" - shrunk to " + gbb);
			if (gbb == null) {
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println(" ==> empty graphics");
				continue;
			}
			
			//	expand graphics bounds to include any labeling words
			gbb = expandToWords(gbb, page, true);
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" - expanded to " + gbb);
			if (gbb == null) {
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println(" ==> empty graphics");
				continue;
			}
			
			//	mark graphics region (unless it's layout artwork, text decoration like a table grid or info box frame, or image decoration like a passe-par-tout made up of overlaid lines)
			if (pageGraphics[g].hasAttribute(Graphics.PAGE_DECORATION_ATTRIBUTE)) {
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println(" ==> page decoration");
			}
			else if (isTextDecoration(pageGraphics[g], page, pageFigures)) {
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
	
	private static BoundingBox getVisiblePathBounds(Graphics graphics) {
		
		//	compute dimensions of actually enclosed area (ignoring white backgrounds)
		int gLeft = Integer.MAX_VALUE;
		int gRight = 0;
		int gTop = Integer.MAX_VALUE;
		int gBottom = 0;
		
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
			
			//	observe path dimensions (intersecting with clip bounds if present, as that's the visible part)
			BoundingBox pClipBounds = ((paths[p].clipBounds == null) ? paths[p].bounds : paths[p].clipBounds);
			gLeft = Math.min(gLeft, Math.max(paths[p].bounds.left, pClipBounds.left));
			gRight = Math.max(gRight, Math.min(paths[p].bounds.right, pClipBounds.right));
			gTop = Math.min(gTop, Math.max(paths[p].bounds.top, pClipBounds.top));
			gBottom = Math.max(gBottom, Math.min(paths[p].bounds.bottom, pClipBounds.bottom));
		}
		
		//	compute bounds of visible paths
		if ((gLeft < gRight) && (gTop < gBottom))
			return new BoundingBox(gLeft, gRight, gTop, gBottom);
		else return null;
	}
	
	private static boolean isTextDecoration(Graphics graphics, ImPage page, Figure[] pageFigures) {
		
		//	too low or narrow to be a table grid or text box
		BoundingBox graphicsBounds = graphics.getBounds();
		if (graphicsBounds.getHeight() < (page.getImageDPI() / 8))
			return false;
		if (graphicsBounds.getWidth() < page.getImageDPI())
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
		
		//	keep track of single (very wide) line rectangle
		int rectLineCount = 0;
		
		//	compute average extent of lines (table grids should have pretty long lines)
		int hLineCount = 0;
		int vLineCount = 0;
		CountingSet relativeLineLengths = new CountingSet(new TreeMap());
		CountingSet graphicsRelativeLineLengths = new CountingSet(new TreeMap());
		CountingSet pageRelativeLineLengths = new CountingSet(new TreeMap());
		
		//	compute dimensions of actually enclosed area (ignoring white backgrounds)
		int pathLeft = Integer.MAX_VALUE;
		int pathRight = 0;
		int pathTop = Integer.MAX_VALUE;
		int pathBottom = 0;
		
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
			
			//	observe path dimensions
			pathLeft = Math.min(pathLeft, paths[p].bounds.left);
			pathRight = Math.max(pathRight, paths[p].bounds.right);
			pathTop = Math.min(pathTop, paths[p].bounds.top);
			pathBottom = Math.max(pathBottom, paths[p].bounds.bottom);
			
			//	investigate path words
			ImWord[] pathWords = page.getWordsInside(paths[p].bounds);
			pathAreaSum += paths[p].bounds.getArea();
			inPathWordCount+= pathWords.length;
			int inPathWordHeightSum = 0;
			for (int w = 0; w < pathWords.length; w++) {
				inPathWordAreaSum += pathWords[w].bounds.getArea();
				inPathWordHeightSum += pathWords[w].bounds.getHeight();
			}
			int inPathWordHeight = ((pathWords.length == 0) ? Integer.MAX_VALUE : (inPathWordHeightSum / pathWords.length));
			
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
						if ((fillColor == null) && paths[p].isFilled((inPathWordHeight * defaultDpi) / page.getImageDPI()))
							rectLineCount++;
					}
					else if (shapes[h] instanceof CubicCurve2D)
						curveCount++;
				}
			}
		}
		
		//	compute bounds of visible paths
		BoundingBox pathBounds = new BoundingBox(pathLeft, pathRight, pathTop, pathBottom);
		
		//	what do we have thus far?
		if (PdfExtractorTest.aimAtPage != -1) {
			System.out.println(" - " + paths.length + " paths with " + subPathCount + " sub paths, " + strokeSubPathCount + " stroked and " + fillSubPathCount + " filled");
			System.out.println(" - aggregate path bounds are " + pathBounds);
			System.out.println(" - " + lineCount + " lines and " + curveCount + " curves");
			System.out.println(" - " + hLineCount + " horizontal lines and " + vLineCount + " vertical ones");
			System.out.println(" - " + rectLineCount + " very wide lines creating rectangles all by themselves");
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
		
		//	visible part too low or narrow to be a table grid or text box
		if (pathBounds.getHeight() < (page.getImageDPI() / 8))
			return false;
		if (pathBounds.getWidth() < page.getImageDPI())
			return false;
		
		//	more than 12 curves ==> not a grid or box (rounded corners rarely consist of more than 3 segments each ...)
		if (curveCount > 12)
			return false;
		
		//	fewer than 4 lines ==> not a table grid or text box (mind rectangles painted as a single very wide line, though)
		if ((lineCount < 4) && (rectLineCount == 0))
			return false;
		
		//	no axis oriented lines of any significant length at all ==> not a table grid or text box
		if (graphicsRelativeLineLengths.isEmpty())
			return false;
		
		//	prepare rendering paths TODOne use BYTE_GRAY image to reduce memory consumption
		Rectangle2D extent = graphics.getExtent();
		float scale = (((float) page.getImageDPI()) / graphics.getDpi());
		int renderingWidth = (((int) Math.ceil(extent.getWidth() * scale)) + 10);
		int renderingHeight = (((int) Math.ceil(extent.getHeight() * scale)) + 10);
		if (renderingWidth > page.bounds.getWidth())
			renderingWidth = page.bounds.getWidth();
		if (renderingHeight > page.bounds.getHeight())
			renderingHeight = page.bounds.getHeight();
		BufferedImage rendering = new BufferedImage(renderingWidth, renderingHeight, BufferedImage.TYPE_BYTE_GRAY);
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
			
			//	assemble and render path
			Path2D path = new Path2D.Float();
			SubPath[] subPaths = paths[p].getSubPaths();
			for (int sp = 0; sp < subPaths.length; sp++) {
				Path2D subPath = subPaths[sp].getPath();
				path.append(subPath, false);
			}
			
			//	render path
			if (fillColor != null) {
				renderer.setColor(fillColor);
				renderer.fill(path);
			}
			if (strokeColor != null) {
				renderer.setColor(strokeColor);
				renderer.setStroke(stroke);
				renderer.draw(path);
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
		
		//	fewer than 4 lines ==> not a table grid or text box (mind rectangles painted as a single very wide line, though)
		if ((lineCount < 4) && (rectLineCount == 0))
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
		
		//	TODO split figures out of blocks if distance too close for page blocking, but without actual overlap
		
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
			
			//	area mostly (>= 80%) covered by figures, any graphics are likely decoration, no matter how large their bounds
			if ((imageArea * 5) >= (pdb.getArea() * 4))
				pdrType = ImRegion.IMAGE_TYPE;
			//	area contains more figures that graphics
			else if (imageArea > graphicsArea)
				pdrType = ImRegion.IMAGE_TYPE;
			//	smaller and fewer figures than graphics
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
			
			//	cut any labels out of main text (unless they have been sorted out as artifacts before, that is)
			ImWord[] imageWords = filterArtifactWords(pageImageRegs[i].getWords());
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
			
			//	cut any labels out of main text (unless they have been sorted out as artifacts before, that is)
			ImWord[] graphicsWords = filterArtifactWords(pageGraphicsRegs[g].getWords());
			ImUtils.makeStream(graphicsWords, ImWord.TEXT_STREAM_TYPE_LABEL, null);
			
			//	clean up paragraphs, etc.
			for (int r = 0; r < pageRegs.length; r++) {
				if ((pageRegs[r] != pageGraphicsRegs[g]) && pageRegs[r].bounds.liesIn(pageGraphicsRegs[g].bounds, false))
					page.removeRegion(pageRegs[r]);
			}
		}
	}
	
	private static ImWord[] filterArtifactWords(ImWord[] words) {
		ArrayList wordList = new ArrayList();
		for (int w = 0; w < words.length; w++) {
			if (!ImWord.TEXT_STREAM_TYPE_ARTIFACT.equals(words[w].getTextStreamType()) && !ImWord.TEXT_STREAM_TYPE_DELETED.equals(words[w].getTextStreamType()))
				wordList.add(words[w]);
		}
		return ((wordList.size() < words.length) ? ((ImWord[]) wordList.toArray(new ImWord[wordList.size()])) : words);
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
	
	private static BoundingBox expandToWords(BoundingBox bounds, ImPage page, boolean isGraphics) {
		
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
	
	private static void splitPageWords(PPageData pData, DefaultingMap pFlippedWords, Set pInFigureWords, Set pInGraphicsWords, Set pWatermarkWords, Tokenizer tokenizer, Tokenizer numberTokenizer, ProgressMonitor spm) {
		
		//	split words that tokenize apart, splitting bounding box based on font measurement
		ArrayList pWordList = new ArrayList();
		for (int w = 0; w < pData.words.length; w++) {
			
			//	check tokenization
			TokenSequence pWordTokens = (Gamta.isNumber(pData.words[w].str) ? numberTokenizer : tokenizer).tokenize(pData.words[w].str);
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
			float splitTokenWidthSum = 0;
			
			//	initialize font (only if we need character width proportional split)
			Font pWordFont;
			if (pData.words[w].monospaced)
				pWordFont = null;
			else {
				int fontStyle = Font.PLAIN;
				if (pData.words[w].bold)
					fontStyle = (fontStyle | Font.BOLD);
				if (pData.words[w].italics)
					fontStyle = (fontStyle | Font.ITALIC);
				pWordFont = getFont(pData.words[w].font.name, fontStyle, pData.words[w].serif, pData.words[w].monospaced, Math.round(((float) pData.words[w].fontSize)));
			}
			
			//	perform word split
			int charCodeCharPos = 0;
			char charCodeChar = ((char) 0);
			int charCodeCharRepeatLeft = 0;
			for (int s = 0; s < splitTokens.length; s++) {
				
				//	split character codes according to tokens
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
				
				//	apply simple equidistant split for monospaced fonts
				if (pWordFont == null)
					splitTokenWidths[s] = ((float) splitTokens[s].length());
				
				//	do character width proportional split otherwise
				else {
					TextLayout tl = new TextLayout(splitTokens[s], pWordFont, new FontRenderContext(null, false, true));
					splitTokenWidths[s] = ((float) tl.getBounds().getWidth());
				}
				splitTokenWidthSum += splitTokenWidths[s];
			}
			
			//	left-right words
			if (pData.words[w].fontDirection == PWord.LEFT_RIGHT_FONT_DIRECTION) {
				
				//	store split result, splitting word bounds accordingly
				float pWordWidth = ((float) (pData.words[w].bounds.getMaxX() - pData.words[w].bounds.getMinX()));
				float splitTokenLeft = ((float) pData.words[w].bounds.getMinX());
				for (int s = 0; s < splitTokens.length; s++) {
					float splitTokenWidth = ((pWordWidth * splitTokenWidths[s]) / splitTokenWidthSum);
					PWord spWord = new PWord(pData.words[w].renderOrderNumber, splitCharCodes[s], splitTokens[s], pData.words[w].baseline, new Rectangle2D.Float(splitTokenLeft, ((float) pData.words[w].bounds.getMinY()), splitTokenWidth, ((float) pData.words[w].bounds.getHeight())), pData.words[w].color, pData.words[w].fontSize, pData.words[w].fontDirection, pData.words[w].font);
					if ((s+1) == splitTokens.length)
						spWord.joinWithNext = pData.words[w].joinWithNext;
					pWordList.add(spWord);
					if ((pFlippedWords != null) && pFlippedWords.containsKey(pData.words[w]))
						pFlippedWords.put(spWord, pFlippedWords.get(pData.words[w]));
					if ((pInFigureWords != null) && pInFigureWords.contains(pData.words[w]))
						pInFigureWords.add(spWord);
					if ((pInGraphicsWords != null) && pInGraphicsWords.contains(pData.words[w]))
						pInGraphicsWords.add(spWord);
					if ((pWatermarkWords != null) && pWatermarkWords.contains(pData.words[w]))
						pWatermarkWords.add(spWord);
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
					PWord spWord = new PWord(pData.words[w].renderOrderNumber, splitCharCodes[s], splitTokens[s], pData.words[w].baseline, new Rectangle2D.Float(((float) pData.words[w].bounds.getMinX()), splitTokenLeft, ((float) pData.words[w].bounds.getWidth()), splitTokenWidth), pData.words[w].color, pData.words[w].fontSize, pData.words[w].fontDirection, pData.words[w].font);
					if ((s+1) == splitTokens.length)
						spWord.joinWithNext = pData.words[w].joinWithNext;
					pWordList.add(spWord);
					if ((pFlippedWords != null) && pFlippedWords.containsKey(pData.words[w]))
						pFlippedWords.put(spWord, pFlippedWords.get(pData.words[w]));
					if ((pInFigureWords != null) && pInFigureWords.contains(pData.words[w]))
						pInFigureWords.add(spWord);
					if ((pInGraphicsWords != null) && pInGraphicsWords.contains(pData.words[w]))
						pInGraphicsWords.add(spWord);
					if ((pWatermarkWords != null) && pWatermarkWords.contains(pData.words[w]))
						pWatermarkWords.add(spWord);
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
					PWord spWord = new PWord(pData.words[w].renderOrderNumber, splitCharCodes[s], splitTokens[s], pData.words[w].baseline, new Rectangle2D.Float(((float) pData.words[w].bounds.getMinX()), (splitTokenLeft - splitTokenWidth), ((float) pData.words[w].bounds.getWidth()), splitTokenWidth), pData.words[w].color, pData.words[w].fontSize, pData.words[w].fontDirection, pData.words[w].font);
					if ((s+1) == splitTokens.length)
						spWord.joinWithNext = pData.words[w].joinWithNext;
					pWordList.add(spWord);
					if ((pFlippedWords != null) && pFlippedWords.containsKey(pData.words[w]))
						pFlippedWords.put(spWord, pFlippedWords.get(pData.words[w]));
					if ((pInFigureWords != null) && pInFigureWords.contains(pData.words[w]))
						pInFigureWords.add(spWord);
					if ((pInGraphicsWords != null) && pInGraphicsWords.contains(pData.words[w]))
						pInGraphicsWords.add(spWord);
					if ((pWatermarkWords != null) && pWatermarkWords.contains(pData.words[w]))
						pWatermarkWords.add(spWord);
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
			//	TODO_not observe upside-down font direction ==> upside-down words should only occur in bottom-up-blocks, and should be flipped to bottom-up by now
		}
		
		//	refresh PWord array
		if (pWordList.size() != pData.words.length)
			pData.words = ((PWord[]) pWordList.toArray(new PWord[pWordList.size()]));
		pWordList = null;
	}
	
	private static final float wordPathOverlapCheckMagnification = (96f / defaultDpi); // no need to go 200% resolution here, as we're out for areas, not lines
	private static void sanitizePageWords(PPageData pData, Tokenizer tokenizer,/* float magnification,*/ ProgressMonitor spm) {
		
		//	sort out words that lie outside the media box (normalize media box beforehand, ignoring orientation)
		filterOffPageWords(pData, spm);
		
		//	remove words painted with extremely low alpha
		spm.setInfo(" - removing words painted with low alpha");
		ArrayList pWords = new ArrayList();
		for (int w = 0; w < pData.words.length; w++) {
			int alpha = pData.words[w].color.getAlpha();
			if (alpha >= 64)
				pWords.add(pData.words[w]);
			else spm.setInfo("   - removed transparent word '" + pData.words[w].str + "' @" + pData.words[w].bounds + " for alpha " + alpha + " in color " + pData.words[w].color);
		}
		if (pWords.size() < pData.words.length) {
			spm.setInfo(" ==> removed " + (pData.words.length - pWords.size()) + " transparent words");
			pData.words = ((PWord[]) pWords.toArray(new PWord[pWords.size()]));
		}
		
		//	shrink word bounding boxes to actual word size
		BufferedImage mbi = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY);
		Graphics2D mg = mbi.createGraphics();
		for (int w = 0; w < pData.words.length; w++) {
			if (pData.words[w].str.trim().length() != 0)
				pData.words[w] = shrinkWordBounds(mg, pData.words[w]);
		}
		
		//	sort out words overpainted by opaque graphics
		spm.setInfo(" - removing words painted over by opaque graphics");
		pWords.clear();
		pWords.addAll(Arrays.asList(pData.words));
		Collections.sort(pWords);
		for (int p = 0; p < pData.paths.length; p++) {
			
			//	check path opacity
			if (!isOpaque(pData.paths[p]))
				continue;
			
			//	get clipping path (if any)
			BoundingBox clipBox = ((pData.paths[p].clipPaths == null) ? null : getBoundingBox(pData.paths[p].visibleBounds, pData.pdfPageContentBox, wordPathOverlapCheckMagnification, pData.rotate));
			if ((clipBox != null) && (PdfExtractorTest.aimAtPage != -1))
				System.out.println(" - clip box is " + clipBox);
			
			//	test if path interferes with words at all
			int pathWordCount = 0;
			
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
				BoundingBox subPathBounds = getBoundingBox(pSubPaths[sp].getBounds(pData.paths[p]), pData.pdfPageContentBox, wordPathOverlapCheckMagnification, pData.rotate);
				if (clipBox != null)
					subPathBounds = new BoundingBox(
								Math.max(subPathBounds.left, clipBox.left),
								Math.min(subPathBounds.right, clipBox.right),
								Math.max(subPathBounds.top, clipBox.top),
								Math.min(subPathBounds.bottom, clipBox.bottom)
							);
//				
//				//	collect words painted over by opaquely filled path, but only if it is non-artsy
//				for (int w = 0; w < pWords.size(); w++) {
//					PWord pw = ((PWord) pWords.get(w));
//					
//					//	rendered after current path
//					if (pData.paths[p].renderOrderNumber < pw.renderOrderNumber)
//						break; // we're done here, thanks to ordering of words
//					
//					//	check overlap
//					BoundingBox wordBounds = getBoundingBox(pw.bounds, pData.pdfPageContentBox, magnification, pData.rotate);
//					if (wordBounds.liesIn(subPathBounds, false)) {
//						pWords.remove(w--);
//						spm.setInfo("   - removed word '" + pw.str + "' @" + wordBounds + " with RON " + pw.renderOrderNumber + " as overpainted by graphics path at " + subPathBounds + " with RON " + pData.paths[p].renderOrderNumber);
//					}
//				}
				
				//	collect words painted over by opaquely filled path, but only if it is non-artsy
				for (int w = 0; w < pWords.size(); w++) {
					PWord pw = ((PWord) pWords.get(w));
					
					//	rendered after current path
					if (pData.paths[p].renderOrderNumber < pw.renderOrderNumber)
						break; // we're done here, thanks to ordering of words
					
					//	check overlap
					BoundingBox wordBounds = getBoundingBox(pw.bounds, pData.pdfPageContentBox, wordPathOverlapCheckMagnification, pData.rotate);
					if (wordBounds.liesIn(subPathBounds, false))
						pathWordCount++;
				}
			}
			
			//	any words to check at all?
			if (pathWordCount == 0)
				continue;
			
			//	render path on transparent background
			BoundingBox pathBounds = getBoundingBox(pData.paths[p].getBounds(), pData.pdfPageContentBox, wordPathOverlapCheckMagnification, pData.rotate);
			BufferedImage pathImage = new BufferedImage(pathBounds.getWidth(), pathBounds.getHeight(), BufferedImage.TYPE_INT_ARGB);
			Graphics2D piGr = pathImage.createGraphics();
			piGr.setColor(new Color(0xFF, 0xFF, 0xFF, 0x00)); // transparent white
			piGr.fillRect(0, 0, pathImage.getWidth(), pathImage.getHeight());
			piGr.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			
			//	observe clipping (we need this despite all sanitization, as some paths draw all over and show only partially)
			if (clipBox != null)
				piGr.clipRect(clipBox.left, clipBox.top, clipBox.getWidth(), clipBox.getHeight());
			
			//	adjust graphics
			piGr.translate((-pData.pdfPageContentBox.x * wordPathOverlapCheckMagnification), (pData.pdfPageContentBox.y * wordPathOverlapCheckMagnification));
			piGr.translate(-pathBounds.left, -pathBounds.top);
			piGr.scale(wordPathOverlapCheckMagnification, -wordPathOverlapCheckMagnification);
			if (pData.paths[p].blendMode != null)
				piGr.setComposite(pData.paths[p].blendMode);
			Stroke stroke = null;
			if (pData.paths[p].strokeColor != null) {
				float[] dashPattern = null;
				if (pData.paths[p].dashPattern != null) {
					dashPattern = new float[pData.paths[p].dashPattern.size()];
					boolean allZeroDashes = true;
					for (int e = 0; e < pData.paths[p].dashPattern.size(); e++) {
						dashPattern[e] = ((Number) pData.paths[p].dashPattern.get(e)).floatValue();
						allZeroDashes = (allZeroDashes && (dashPattern[e] == 0));
					}
					if (allZeroDashes)
						dashPattern = null;
				}
				stroke = new BasicStroke(pData.paths[p].lineWidth, pData.paths[p].lineCapStyle, pData.paths[p].lineJointStyle, ((pData.paths[p].miterLimit < 1) ? 1.0f : pData.paths[p].miterLimit), dashPattern, pData.paths[p].dashPatternPhase);
			}
			
			//	render paths
			Path2D path = new Path2D.Float();
			for (int sp = 0; sp < pSubPaths.length; sp++) {
				Path2D subPath = pSubPaths[sp].getPath();
				path.append(subPath, false);
			}
			if (pData.paths[p].fillColor != null) {
				piGr.setColor(pData.paths[p].fillColor);
				piGr.fill(path);
			}
			if (pData.paths[p].strokeColor != null) {
				piGr.setColor(pData.paths[p].strokeColor);
				piGr.setStroke(stroke);
				piGr.draw(path);
			}
			
			//	what do we have?
			if (PdfExtractorTest.aimAtPage != -1) {
				ImageDisplayDialog idd = new ImageDisplayDialog("Graphics at " + pathBounds + " (including " + pathWordCount + " words)");
				idd.addImage(pathImage, "");
				idd.setSize(700, 900);
				idd.setLocationRelativeTo(null);
				idd.setVisible(true);
			}
			
			//	remove words painted over by opaquely filled areas of path
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println("Testing words against path bounds " + pathBounds + " with path image sized " + pathImage.getWidth() + "x" + pathImage.getHeight());
			for (int w = 0; w < pWords.size(); w++) {
				PWord pw = ((PWord) pWords.get(w));
				
				//	rendered after current path
				if (pData.paths[p].renderOrderNumber < pw.renderOrderNumber)
					break; // we're done here, thanks to ordering of words
				
				//	check overlap (on word bounds relative to graphics)
				BoundingBox wordBounds = getBoundingBox(pw.bounds, pData.pdfPageContentBox, wordPathOverlapCheckMagnification, pData.rotate);
				if (!wordBounds.liesIn(pathBounds, false))
					continue;
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.print(" - word at " + wordBounds);
				wordBounds = wordBounds.translate(-pathBounds.left, -pathBounds.top);
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println(" translated to " + wordBounds);
				boolean wordObscured = true;
				for (int x = wordBounds.left; (x < wordBounds.right) && wordObscured; x++) {
					for (int y = wordBounds.top; y < wordBounds.bottom; y++) {
						int a = ((pathImage.getRGB(x, y) >>> 24) & 0xFF);
						if (a < 64) {
							wordObscured = false;
							break;
						}
					}
				}
				if (wordObscured) {
					pWords.remove(w--);
					spm.setInfo("   - removed word '" + pw.str + "' @" + wordBounds + " with RON " + pw.renderOrderNumber + " as overpainted by graphics path at " + pathBounds + " with RON " + pData.paths[p].renderOrderNumber);
				}
				else spm.setInfo("   - retained word '" + pw.str + "' @" + wordBounds + " with RON " + pw.renderOrderNumber + " located in unpainted area of graphics path at " + pathBounds + " with RON " + pData.paths[p].renderOrderNumber);
			}
		}
		if (pWords.size() < pData.words.length) {
			spm.setInfo(" ==> removed " + (pData.words.length - pWords.size()) + " words overpainted by opaque graphics");
			pData.words = ((PWord[]) pWords.toArray(new PWord[pWords.size()]));
		}
		
		//	partition words by font direction
		Arrays.sort(pData.words, fontDirectionWordOrder);
		
		//	for each line, gather word distance distribution statistics
		HashMap pWordLineSpaceStatistics = new HashMap();
		LineWordSpaceStatistics pWordSpaceStats = new LineWordSpaceStatistics();
		
		//	sort words left to right and top to bottom (for horizontal writing direction, for other writing directions accordingly)
		for (int fdStart = 0; fdStart < pData.words.length;) {
			int fd = pData.words[fdStart].fontDirection;
			for (int fdEnd = (fdStart + 1);;) {
				if ((fdEnd < pData.words.length) && (pData.words[fdEnd].fontDirection == fd)) {
					fdEnd++;
					continue;
				}
				
				//	sort words in current font direction top-down
				Arrays.sort(pData.words, fdStart, fdEnd, getTopDownWordOrder(fd));
				
				//	sort individual lines left to right
				Comparator leftRightWordOrder = getLeftRightWordOrder(fd);
				int lineStart = fdStart;
				for (int w = (fdStart + 1); w < fdEnd; w++)
					
					//	this word starts a new line, sort previous one
					if (isLineBreak(pData.words[lineStart], pData.words[w], fd)) {
						Arrays.sort(pData.words, lineStart, w, leftRightWordOrder);
						LineWordSpaceStatistics pLineWordSpaces = computeLineWordSpaceStatistics(pData.words, lineStart, w, fd);
						for (int iw = lineStart; iw < w; iw++)
							pWordLineSpaceStatistics.put(pData.words[iw], pLineWordSpaces);
						pWordSpaceStats.addWordSpaces(pLineWordSpaces);
						lineStart = w;
					}
				
				//	sort last line
				Arrays.sort(pData.words, lineStart, fdEnd, leftRightWordOrder);
				LineWordSpaceStatistics pLineWordSpaces = computeLineWordSpaceStatistics(pData.words, lineStart, fdEnd, fd);
				for (int iw = lineStart; iw < fdEnd; iw++)
					pWordLineSpaceStatistics.put(pData.words[iw], pLineWordSpaces);
				pWordSpaceStats.addWordSpaces(pLineWordSpaces);
				
				//	continue with next font direction
				fdStart = fdEnd;
				break;
			}
		}
		System.out.println("Overall word space stats:");
		for (Iterator wsit = pWordSpaceStats.iterator(); wsit.hasNext();) {
			Float ws = ((Float) wsit.next());
			System.out.println(" - " + ws + " (" + pWordSpaceStats.getCount(ws) + ")");
		}
		System.out.println(" ==> average is " + pWordSpaceStats.getAverageWordSpace());
		System.out.println(" ==> median is " + pWordSpaceStats.getMedianWordSpace());
		
		/* merge or join subsequent words less than DPI/60 apart, as they are
		 * likely separated due to implicit spaces or font changes (at 10 pt,
		 * that is, using lower threshold for smaller font sizes); join words
		 * that have mismatch in font style or size, as such differences tend
		 * to bear some semantics, as well as ones with mismatching font names,
		 * so font corrections remain possible */
		double maxMergeMargin10pt = (((double) defaultDpi) / 60);
		ArrayList pWordList = null;
		PWord lpWord = null;
		LineWordSpaceStatistics lpWordLineSpaceStats = null;
		TokenSequence lpWordTokens = null;
		float lineDensityCompFactor = 1;
		spm.setInfo("Checking word mergers ...");
		for (int w = 0; w < pData.words.length; w++) {
			
			//	create token sequence
			TokenSequence pWordTokens = tokenizer.tokenize(pData.words[w].str);
			
			//	no word to compare to as yet
			if ((lpWord == null) || (lpWordTokens == null)) {
				lpWord = pData.words[w];
				lpWordLineSpaceStats = ((LineWordSpaceStatistics) pWordLineSpaceStatistics.get(lpWord));
				lpWordTokens = pWordTokens;
				System.out.println(" - different lines by statistics objects at '" + pData.words[w].str + "'"); // TODO debug flag this after tests
				float lineDensityByAverage = (lpWordLineSpaceStats.getAverageWordSpace() / pWordSpaceStats.getAverageWordSpace());
				float lineDensityByMedian = (lpWordLineSpaceStats.getMedianWordSpace() / pWordSpaceStats.getMedianWordSpace());
				System.out.println(" - line density is " + lineDensityByAverage + " (avg), " + lineDensityByMedian + " (med)"); // TODO debug flag this after tests
				lineDensityCompFactor = Math.min(1, Math.max(lineDensityByAverage, lineDensityByMedian));
				System.out.println(" --> line density compensation factor is " + lineDensityCompFactor); // TODO debug flag this after tests
				continue;
			}
			if (DEBUG_MERGE_WORDS) System.out.println(" - checking words '" + lpWord.str + "'@" + lpWord.bounds + " and '" + pData.words[w].str + "'@" + pData.words[w].bounds);
			
			//	consult word spacing statistics to assess mergeability 
			LineWordSpaceStatistics pWordLineSpaceStats = ((LineWordSpaceStatistics) pWordLineSpaceStatistics.get(pData.words[w]));
			if (lpWordLineSpaceStats != pWordLineSpaceStats) {
				lpWord = pData.words[w];
				lpWordLineSpaceStats = pWordLineSpaceStats;
				lpWordTokens = pWordTokens;
				System.out.println(" - different lines by statistics objects at '" + pData.words[w].str + "'"); // TODO debug flag this after tests
				float lineDensityByAverage = (pWordLineSpaceStats.getAverageWordSpace() / pWordSpaceStats.getAverageWordSpace());
				float lineDensityByMedian = (pWordLineSpaceStats.getMedianWordSpace() / pWordSpaceStats.getMedianWordSpace());
				System.out.println(" - line density is " + lineDensityByAverage + " (avg), " + lineDensityByMedian + " (med)"); // TODO debug flag this after tests
				lineDensityCompFactor = Math.min(1, Math.max(lineDensityByAverage, lineDensityByMedian));
				System.out.println(" --> line density compensation factor is " + lineDensityCompFactor); // TODO debug flag this after tests
				continue;
			}
			
			//	check if words mergeable or joinable
			if (!areWordsMergeable(lpWord, lpWordTokens, pData.words[w], pWordTokens, (maxMergeMargin10pt * lineDensityCompFactor), tokenizer)) {
				lpWord = pData.words[w];
				lpWordLineSpaceStats = pWordLineSpaceStats;
				lpWordTokens = pWordTokens;
				continue;
			}
			
			//	eliminate second word if word strings, font, and font size are same and words overlap by more than 90% in each direction
			//	(some documents actually emulate bold face by slightly offset multi-rendering, TEST page 0 of RevistaBrasZool_25_4.pdf)
			if (lpWord.str.equals(pData.words[w].str) && (lpWord.fontSize == pData.words[w].fontSize) && (lpWord.font == pData.words[w].font)) {
				Rectangle2D.Float overlap = new Rectangle2D.Float();
				Rectangle2D.intersect(lpWord.bounds, pData.words[w].bounds, overlap);
				if ((overlap.getWidth() * 10) < (lpWord.bounds.getWidth() * 9)) {}
				else if ((overlap.getWidth() * 10) < (pData.words[w].bounds.getWidth() * 9)) {}
				else if ((overlap.getHeight() * 10) < (lpWord.bounds.getHeight() * 9)) {}
				else if ((overlap.getHeight() * 10) < (pData.words[w].bounds.getHeight() * 9)) {}
				else {
					System.out.println(" --> eliminated overlapping word duplicte '" + pData.words[w].str + "' at " + pData.words[w].bounds); // TODO debug flag this after tests
					pData.words[w] = null;
					if (pWordList == null)
						pWordList = new ArrayList();
					continue;
				}
			}
			
			//	never merge or join numbers (unless font is same and has implicit spaces)
			if ((Gamta.isNumber(lpWord.str) || Gamta.isNumber(pData.words[w].str)) && ((lpWord.font == null) || (lpWord.font != pData.words[w].font) || !lpWord.font.hasImplicitSpaces)) {
				lpWord = pData.words[w];
				lpWordLineSpaceStats = pWordLineSpaceStats;
				lpWordTokens = pWordTokens;
				if (DEBUG_MERGE_WORDS) System.out.println(" --> not joining or merging numbers");
				continue;
			}
			
			//	logically join (but never physically merge) words if font names don't match, messes up font char strings, and thus gets in the way of correction
			if ((lpWord.font != null) && (lpWord.font.name != null) && (pData.words[w].font != null) && (pData.words[w].font.name != null) && !lpWord.font.name.equals(pData.words[w].font.name)) {
				lpWord.joinWithNext = true;
				spm.setInfo(" --> joined words " + lpWord.str + " and " + pData.words[w].str);
				lpWord = pData.words[w];
				lpWordLineSpaceStats = pWordLineSpaceStats;
				lpWordTokens = pWordTokens;
				continue;
			}
			
			//	figure out bold, italic, font size, etc. using weighted majority vote
			PdfFont mpWordFont = ((lpWord.str.length() < pData.words[w].str.length()) ? pData.words[w].font : lpWord.font);
			float mpWordBaseline;
			Rectangle2D.Float mpWordBounds;
			if (lpWord.fontDirection == PWord.LEFT_RIGHT_FONT_DIRECTION) {
				float top = ((float) Math.max(lpWord.bounds.getMaxY(), pData.words[w].bounds.getMaxY()));
				float bottom = ((float) Math.min(lpWord.bounds.getMinY(), pData.words[w].bounds.getMinY()));
				mpWordBaseline = lpWord.baseline;
				mpWordBounds = new Rectangle2D.Float(((float) lpWord.bounds.getMinX()), bottom, ((float) (pData.words[w].bounds.getMaxX() - lpWord.bounds.getMinX())), (top - bottom));
			}
			else if (lpWord.fontDirection == PWord.BOTTOM_UP_FONT_DIRECTION) {
				float top = ((float) Math.min(lpWord.bounds.getMinX(), pData.words[w].bounds.getMinX()));
				float bottom = ((float) Math.max(lpWord.bounds.getMaxX(), pData.words[w].bounds.getMaxX()));
				mpWordBaseline = lpWord.baseline;
				mpWordBounds = new Rectangle2D.Float(top, ((float) lpWord.bounds.getMinY()), (bottom - top), ((float) (pData.words[w].bounds.getMaxY() - lpWord.bounds.getMinY())));
			}
			else if (lpWord.fontDirection == PWord.TOP_DOWN_FONT_DIRECTION) {
				float top = ((float) Math.max(lpWord.bounds.getMaxX(), pData.words[w].bounds.getMaxX()));
				float bottom = ((float) Math.min(lpWord.bounds.getMinX(), pData.words[w].bounds.getMinX()));
				mpWordBaseline = lpWord.baseline;
				mpWordBounds = new Rectangle2D.Float(bottom, ((float) pData.words[w].bounds.getMinY()), (top - bottom), ((float) (lpWord.bounds.getMaxY() - pData.words[w].bounds.getMinY())));
			}
			else if (lpWord.fontDirection == PWord.UPSIDE_DOWN_FONT_DIRECTION) {
				float top = ((float) Math.max(lpWord.bounds.getMaxY(), pData.words[w].bounds.getMaxY()));
				float bottom = ((float) Math.min(lpWord.bounds.getMinY(), pData.words[w].bounds.getMinY()));
				mpWordBaseline = lpWord.baseline;
				mpWordBounds = new Rectangle2D.Float(((float) pData.words[w].bounds.getMinX()), bottom, ((float) (lpWord.bounds.getMaxX() - pData.words[w].bounds.getMinX())), (top - bottom));
			}
			else {
				lpWord = pData.words[w];
				lpWordTokens = pWordTokens;
				continue;
			}
			
			//	create merged word
			PWord mpWord = new PWord(Math.min(lpWord.renderOrderNumber, pData.words[w].renderOrderNumber), (lpWord.charCodes + pData.words[w].charCodes), (lpWord.str + pData.words[w].str), mpWordBaseline, mpWordBounds, lpWord.color, lpWord.fontSize, lpWord.fontDirection, mpWordFont);
			spm.setInfo(" --> merged words " + lpWord.str + " and " + pData.words[w].str + " to '" + mpWord.str + "'@" + mpWord.bounds);
			
			//	store merged word
			pData.words[w] = mpWord;
			pData.words[w-1] = null;
			lpWord = mpWord;
			lpWordLineSpaceStats = pWordLineSpaceStats;
			lpWordTokens = tokenizer.tokenize(mpWord.str);
			
			//	remember we need to filter words
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
	
	private static final Comparator fontDirectionWordOrder = new Comparator() {
		public int compare(Object obj1, Object obj2) {
			PWord pw1 = ((PWord) obj1);
			PWord pw2 = ((PWord) obj2);
			return (pw1.fontDirection - pw2.fontDirection);
		}
	};
	
	private static final Comparator topDownWordOrderLR = new Comparator() {
		public int compare(Object obj1, Object obj2) {
			PWord pw1 = ((PWord) obj1);
			PWord pw2 = ((PWord) obj2);
			double m1 = ((pw1.bounds.getMinY() + pw1.bounds.getMaxY()) / 2);
			double m2 = ((pw2.bounds.getMinY() + pw2.bounds.getMaxY()) / 2);
			return Double.compare(m2, m1); // PDF coordinates, 0 at bottom
		}
	};
	
	private static final Comparator topDownWordOrderUD = new Comparator() {
		public int compare(Object obj1, Object obj2) {
			PWord pw1 = ((PWord) obj1);
			PWord pw2 = ((PWord) obj2);
			double m1 = ((pw1.bounds.getMinY() + pw1.bounds.getMaxY()) / 2);
			double m2 = ((pw2.bounds.getMinY() + pw2.bounds.getMaxY()) / 2);
			return Double.compare(m1, m2); // PDF coordinates, 0 at bottom
		}
	};
	
	private static final Comparator topDownWordOrderTD = new Comparator() {
		public int compare(Object obj1, Object obj2) {
			PWord pw1 = ((PWord) obj1);
			PWord pw2 = ((PWord) obj2);
			double m1 = ((pw1.bounds.getMinX() + pw1.bounds.getMaxX()) / 2);
			double m2 = ((pw2.bounds.getMinX() + pw2.bounds.getMaxX()) / 2);
			return Double.compare(m2, m1); // top is at right, bottom is at left
		}
	};
	
	private static final Comparator topDownWordOrderBU = new Comparator() {
		public int compare(Object obj1, Object obj2) {
			PWord pw1 = ((PWord) obj1);
			PWord pw2 = ((PWord) obj2);
			double m1 = ((pw1.bounds.getMinX() + pw1.bounds.getMaxX()) / 2);
			double m2 = ((pw2.bounds.getMinX() + pw2.bounds.getMaxX()) / 2);
			return Double.compare(m1, m2); // top is at left, bottom is at right
		}
	};
	
	private static Comparator getTopDownWordOrder(int fontDirection) {
		if (fontDirection == PWord.LEFT_RIGHT_FONT_DIRECTION)
			return topDownWordOrderLR;
		else if (fontDirection == PWord.BOTTOM_UP_FONT_DIRECTION)
			return topDownWordOrderBU;
		else if (fontDirection == PWord.TOP_DOWN_FONT_DIRECTION)
			return topDownWordOrderTD;
		else if (fontDirection == PWord.UPSIDE_DOWN_FONT_DIRECTION)
			return topDownWordOrderUD;
		else return topDownWordOrderLR; // fallback for invalid font direction
	}
	
	private static final Comparator leftRightWordOrderLR = new Comparator() {
		public int compare(Object obj1, Object obj2) {
			PWord pw1 = ((PWord) obj1);
			PWord pw2 = ((PWord) obj2);
			double m1 = ((pw1.bounds.getMinX() + pw1.bounds.getMaxX()) / 2);
			double m2 = ((pw2.bounds.getMinX() + pw2.bounds.getMaxX()) / 2);
			return Double.compare(m1, m2); // PDF coordinates, 0 at left
		}
	};
	
	private static final Comparator leftRightWordOrderUD = new Comparator() {
		public int compare(Object obj1, Object obj2) {
			PWord pw1 = ((PWord) obj1);
			PWord pw2 = ((PWord) obj2);
			double m1 = ((pw1.bounds.getMinX() + pw1.bounds.getMaxX()) / 2);
			double m2 = ((pw2.bounds.getMinX() + pw2.bounds.getMaxX()) / 2);
			return Double.compare(m2, m1); // PDF coordinates, 0 at left
		}
	};
	
	private static final Comparator leftRightWordOrderTD = new Comparator() {
		public int compare(Object obj1, Object obj2) {
			PWord pw1 = ((PWord) obj1);
			PWord pw2 = ((PWord) obj2);
			double m1 = ((pw1.bounds.getMinY() + pw1.bounds.getMaxY()) / 2);
			double m2 = ((pw2.bounds.getMinY() + pw2.bounds.getMaxY()) / 2);
			return Double.compare(m2, m1); // left is at top, right is at bottom, PDF coordinates with 0 at bottom
		}
	};
	
	private static final Comparator leftRightWordOrderBU = new Comparator() {
		public int compare(Object obj1, Object obj2) {
			PWord pw1 = ((PWord) obj1);
			PWord pw2 = ((PWord) obj2);
			double m1 = ((pw1.bounds.getMinY() + pw1.bounds.getMaxY()) / 2);
			double m2 = ((pw2.bounds.getMinY() + pw2.bounds.getMaxY()) / 2);
			return Double.compare(m1, m2); // left is at bottom, right is at top, PDF coordinates with 0 at bottom
		}
	};
	
	private static Comparator getLeftRightWordOrder(int fontDirection) {
		if (fontDirection == PWord.LEFT_RIGHT_FONT_DIRECTION)
			return leftRightWordOrderLR;
		else if (fontDirection == PWord.BOTTOM_UP_FONT_DIRECTION)
			return leftRightWordOrderBU;
		else if (fontDirection == PWord.TOP_DOWN_FONT_DIRECTION)
			return leftRightWordOrderTD;
		else if (fontDirection == PWord.UPSIDE_DOWN_FONT_DIRECTION)
			return leftRightWordOrderUD;
		else return leftRightWordOrderLR; // fallback for invalid font direction
	}
	
	private static boolean isLineBreak(PWord lineStart, PWord word, int fontDirection) {
//		for left-right font direction:
//		if ((pData.words[w].centerY > pData.words[lineStart].bounds.bottom) && (pData.words[lineStart].centerY < pData.words[w].bounds.top)) {
		if (fontDirection == PWord.LEFT_RIGHT_FONT_DIRECTION) {
			double lsm = ((lineStart.bounds.getMinY() + lineStart.bounds.getMaxY()) / 2);
			double wm = ((word.bounds.getMinY() + word.bounds.getMaxY()) / 2);
			if (wm < lineStart.bounds.getMinY()) // PDF coordinates, 0 at bottom
				return true;
			else if (lsm > word.bounds.getMaxY()) // PDF coordinates, 0 at bottom
				return true;
			else return false;
		}
		else if (fontDirection == PWord.BOTTOM_UP_FONT_DIRECTION) {
			double lsm = ((lineStart.bounds.getMinX() + lineStart.bounds.getMaxX()) / 2);
			double wm = ((word.bounds.getMinX() + word.bounds.getMaxX()) / 2);
			if (wm > lineStart.bounds.getMaxX()) // 0 at left, bottom at right
				return true;
			else if (lsm < word.bounds.getMinX()) // 0 at left, bottom at right
				return true;
			else return false;
		}
		else if (fontDirection == PWord.TOP_DOWN_FONT_DIRECTION) {
			double lsm = ((lineStart.bounds.getMinX() + lineStart.bounds.getMaxX()) / 2);
			double wm = ((word.bounds.getMinX() + word.bounds.getMaxX()) / 2);
			if (wm < lineStart.bounds.getMinX()) // bottom and 0 at left
				return true;
			else if (lsm > word.bounds.getMaxX()) // bottom and 0 at left
				return true;
			else return false;
		}
		else if (fontDirection == PWord.UPSIDE_DOWN_FONT_DIRECTION) {
			double lsm = ((lineStart.bounds.getMinY() + lineStart.bounds.getMaxY()) / 2);
			double wm = ((word.bounds.getMinY() + word.bounds.getMaxY()) / 2);
			if (wm > lineStart.bounds.getMaxY()) // PDF coordinates, 0 at bottom
				return true;
			else if (lsm < word.bounds.getMinY()) // PDF coordinates, 0 at bottom
				return true;
			else return false;
		}
		else { // left-right fallback for invalid font direction
			double lsm = ((lineStart.bounds.getMinY() + lineStart.bounds.getMaxY()) / 2);
			double wm = ((word.bounds.getMinY() + word.bounds.getMaxY()) / 2);
			if (wm < lineStart.bounds.getMinY()) // PDF coordinates, 0 at bottom
				return true;
			else if (lsm > word.bounds.getMaxY()) // PDF coordinates, 0 at bottom
				return true;
			else return false;
		}
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
				return new RegExTokenizer("" +
						"(" +
							"[0-9]+" +
							"(\\.[0-9]+)?" +
						")" +
						"|\\,|\\.");
			else return new RegExTokenizer("" +
					"(" +
						"(" +
							"[0-9]{4,}" +
							"|" +
							"(" +
								"[0-9]{1,3}" +
								"(\\,[0-9]{3})*" +
							")" +
						")" +
						"(\\.[0-9]+)?" +
					")|\\,|\\.");
		}
		else if ((decimalCommaCount > thousandCommaCount) && (decimalCommaCount > decimalDotCount)) {
			if ((thousandDotCount == 0) && (decimalDotCount == 0))
				return new RegExTokenizer("" +
						"(" +
							"[0-9]+" +
							"(\\,[0-9]+)?" +
						")" +
						"|\\.|\\,");
			else return new RegExTokenizer("" +
					"(" +
						"(" +
							"[0-9]{4,}" +
							"|" +
							"(" +
								"[0-9]{1,3}" +
								"(\\.[0-9]{3})*" +
							")" +
						")" +
						"(\\,[0-9]+)?" +
					")" +
					"|\\.|\\,");
		}
		else return tokenizer;
	}
	
	private static LineWordSpaceStatistics computeLineWordSpaceStatistics(PWord[] pWords, int lStart, int lEnd, int fd) {
		LineWordSpaceStatistics lineWordSpaces = new LineWordSpaceStatistics();
		System.out.println("Computing word space stats for line starting with '" + pWords[lStart].str + "':");
		for (int w = (lStart + 1); w < lEnd; w++) {
			float pWordSpace;
			
			//	left-right words
			if (fd == PWord.LEFT_RIGHT_FONT_DIRECTION)
				pWordSpace = ((float) (pWords[w].bounds.getMinX() - pWords[w-1].bounds.getMaxX()));
			
			//	bottom-up words
			else if (fd == PWord.BOTTOM_UP_FONT_DIRECTION)
				pWordSpace = ((float) (pWords[w].bounds.getMinY() - pWords[w-1].bounds.getMaxY()));
			
			//	top-down words
			else if (fd == PWord.TOP_DOWN_FONT_DIRECTION)
				pWordSpace = ((float) (pWords[w-1].bounds.getMinY() - pWords[w].bounds.getMaxY()));
			
			//	upside-down words
			else if (fd == PWord.UPSIDE_DOWN_FONT_DIRECTION)
				pWordSpace = ((float) (pWords[w-1].bounds.getMinX() - pWords[w].bounds.getMaxX()));
			
			//	never gonna happen, but Java don't know
			else continue;
			
			//	normalize to font size 10 to overcome bias in pages with strong font size variations
			float pWordSpace10pt = (pWordSpace * (10 + 10)) / (pWords[w-1].fontSize + pWords[w].fontSize);
			System.out.println(" - '" + pWords[w-1].str + "' -> '" + pWords[w].str + "': " + pWordSpace10pt + "(" + pWordSpace + ")");
			lineWordSpaces.addWordSpace(pWordSpace10pt);
		}
		System.out.println("Word space stats for line starting with '" + pWords[lStart].str + "':");
		for (Iterator wsit = lineWordSpaces.iterator(); wsit.hasNext();) {
			Float ws = ((Float) wsit.next());
			System.out.println(" - " + ws + " (" + lineWordSpaces.getCount(ws) + ")");
		}
		System.out.println(" ==> average is " + lineWordSpaces.getAverageWordSpace());
		System.out.println(" ==> median is " + lineWordSpaces.getMedianWordSpace());
		return lineWordSpaces;
	}
	
	private static class LineWordSpaceStatistics extends CountingSet {
		float wordSpaceSum = 0;
		LineWordSpaceStatistics() {
			super(new TreeMap());
		}
		boolean addWordSpace(float wordSpace) {
			wordSpace = (((float) Math.round(wordSpace * 100)) / 100);
			this.wordSpaceSum += wordSpace;
			return this.add(Float.valueOf(wordSpace));
		}
		float getAverageWordSpace() {
			return ((this.size() == 0) ? 0 : (this.wordSpaceSum / this.size()));
		}
		float getMedianWordSpace() {
			if (this.size() == 0)
				return 0;
			int pervWssCount = 0;
			for (Iterator wsit = this.iterator(); wsit.hasNext();) {
				Float ws = ((Float) wsit.next());
				int wsCount = this.getCount(ws);
				if (this.size() < ((pervWssCount + wsCount) * 2))
					return ws.floatValue();
				else pervWssCount += wsCount;
			}
			return 0;
		}
		void addWordSpaces(LineWordSpaceStatistics lwss) {
			this.addAll(lwss);
			this.wordSpaceSum += lwss.wordSpaceSum;
		}
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
		
		//	upside-down words
		else if (pWord.fontDirection == PWord.UPSIDE_DOWN_FONT_DIRECTION) {
			
			//	not in same line
			if ((pWord.bounds.getMaxY() <= lpWord.bounds.getMinY()) || (lpWord.bounds.getMaxY() <= pWord.bounds.getMinY())) {
				if (DEBUG_MERGE_WORDS) System.out.println(" --> different lines");
				return false;
			}
			
			//	too far a gap
			if ((pWord.bounds.getMaxX() > lpWord.bounds.getMaxX()) || ((lpWord.bounds.getMinX() - pWord.bounds.getMaxX()) > maxMergeMargin)) {
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
		
		//	do NOT merge words that tokenize apart
		TokenSequence mpWordTokens = tokenizer.tokenize(lpWord.str + pWord.str);
		if ((lpWordTokens.size() + pWordTokens.size()) <= mpWordTokens.size()) {
			if (DEBUG_MERGE_WORDS) System.out.println(" --> tokenization mismatch");
			return false;
		}
		
		//	no counter indications found
		return true;
	}
	
	private void storeFiguresAsSupplements(ImDocument doc, PPageData pData, Map objects, float magnification, Map figureSupplements, ProgressMonitor spm) throws IOException {
		spm.setInfo(" - storing figures");
		
		//	display figures if testing
		ImageDisplayDialog fdd = null;
		BufferedImage[] pdfFigureImage = null;
		if (DEBUG_EXTRACT_FIGURES && (PdfExtractorTest.aimAtPage != -1)) {
			fdd = new ImageDisplayDialog("Figures in Page " + pData.p);
			pdfFigureImage = new BufferedImage[1];
		}
		
		//	store figures
		for (int f = 0; f < pData.figures.length; f++) {
			PFigure pFigure = pData.figures[f];
			spm.setInfo("   - " + pFigure);
			
			//	get image
			BufferedImage pFigureImage = this.getFigureImage(doc, pData.p, pFigure, true, false, pData.pdfPage, pData.pdfPageContentBox, /*xObjects, */objects, magnification, figureSupplements, pdfFigureImage, spm);
			
			//	display figures if testing
			if ((fdd != null) && (pFigureImage != null)) {
				BoundingBox pFigureBox = getBoundingBox(pFigure.bounds, pData.pdfPageContentBox, magnification, pData.rotate);
				if (DEBUG_EXTRACT_FIGURES && (PdfExtractorTest.aimAtPage != -1))
					spm.setInfo("     - figure bounds are " + pFigure.bounds);
				spm.setInfo("     - rendering bounds are " + pFigureBox);
				fdd.addImage(pFigureImage, (pFigureBox.toString() + ": " + pFigure.toString()));
				if (pdfFigureImage[0] != pFigureImage)
					fdd.addImage(pdfFigureImage[0], ("raw " + pFigureBox.toString() + ": " + pFigure.toString()));
				
				Object pFigureDataObj = PdfParser.dereference(pFigure.refOrData, objects);
				Object sMaskObj = ((pFigureDataObj instanceof PStream) ? PdfParser.dereference(((PStream) pFigureDataObj).params.get("SMask"), objects) : null);
				if (sMaskObj instanceof PStream) {
					PStream sMask = ((PStream) sMaskObj);
					BufferedImage sMaskImage = this.decodeImage(pData.pdfPage, sMask.bytes, sMask.params, objects, false, true);
					if (sMaskImage != null)
						fdd.addImage(sMaskImage, (pFigureBox.toString() + "-MASK: " + pFigure.toString()));
				}
			}
		}
		
		//	display figures if testing
		if (fdd != null) {
			fdd.setSize(600, 800);
			fdd.setLocationRelativeTo(null);
			fdd.setVisible(true);
		}
	}
	
	private static void sanitizePageGraphics(PPageData pData,/* float magnification,*/ ProgressMonitor spm) {
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
			
			//	skip over invisible paths
			if ((pData.paths[p].strokeColor == null) && (pData.paths[p].fillColor == null)) {
				spm.setInfo("     ==> removed as neigher stroked nor filled");
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println(" ==> removed as neigher stroked nor filled");
				continue;
			}
//			
//			//	remove paths so small that anti aliasing would not actually paint them anyway
//			float paintWidth = (pData.paths[p].getBounds().width * magnification);
//			float paintHeight = (pData.paths[p].getBounds().height * magnification);
//			if ((paintWidth * paintHeight) < 0.33) {
//				spm.setInfo("     ==> removed as negligibly small");
//				if (PdfExtractorTest.aimAtPage != -1)
//					System.out.println(" ==> removed as negligibly small (" + paintWidth + "x" + paintHeight + " pixels in rendering resolution)");
//				continue;
//			}
//			if (paintWidth < 0.33) {
//				spm.setInfo("     ==> removed as negligibly narrow");
//				if (PdfExtractorTest.aimAtPage != -1)
//					System.out.println(" ==> removed as negligibly narrow (" + paintWidth + " pixels in rendering resolution)");
//				continue;
//			}
//			if (paintHeight < 0.33) {
//				spm.setInfo("     ==> removed as negligibly low");
//				if (PdfExtractorTest.aimAtPage != -1)
//					System.out.println(" ==> removed as negligibly low (" + paintHeight + " pixels in rendering resolution)");
//				continue;
//			}
			
			//	get clipping path (if any)
			BoundingBox clipBox = ((pData.paths[p].clipPaths == null) ? null : getBoundingBox(pData.paths[p].visibleBounds, pData.pdfPageContentBox, 1, 0));
			if ((clipBox != null) && (PdfExtractorTest.aimAtPage != -1))
				System.out.println(" - clip box is " + clipBox);
			
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
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" - blend mode is " + pData.paths[p].blendMode);
			
			//	if path painted and filled in white, check if it is on top of anything non-white
			//	TODOne assess if 14/16th white for stroking and 15/16 white for filling are sensible thresholds ==> looks that way
//			if ((strokeBrightness >= 0.99) && (fillBrightness >= 0.99)) {
			if ((strokeBrightness > 0.88) && (fillBrightness > 0.94)) {
//				
//				//	painted right on white page, we don't need this one
//				//	NO GOOD, CAN ALSO BE PAINTED ON TOP OF OTHER (NON-WHITE) GRAPHICS ...
//				if (pData.paths[p].renderOrderNumber < minNonGraphicsRenderOrderNumber) {
//					spm.setInfo("     ==> removed as bright on white (RON)");
//					if (PdfExtractorTest.aimAtPage != -1)
//						System.out.println(" ==> removed as bright on white (non-graphics RON)");
//					continue;
//				}
				
				//	depending on blend mode, a light overpaint does not have any significant effect
				if ((pData.paths[p].blendMode != null) && (pData.paths[p].blendMode.lightIsTranslucent())) {
					spm.setInfo("     ==> removed as bright on white (BM)");
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println(" ==> removed as bright on white (BM)");
					continue;
				}
				
				//	check minimum over-painted render order number in grid
				BoundingBox pathBox = getBoundingBox(pathBounds, pData.pdfPageContentBox, 1, 0);
				if (clipBox != null)
					pathBox = new BoundingBox(
								Math.max(pathBox.left, clipBox.left),
								Math.min(pathBox.right, clipBox.right),
								Math.max(pathBox.top, clipBox.top),
								Math.min(pathBox.bottom, clipBox.bottom)
							);
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
					System.out.println(" - minimum path overlapping render order number is " + minOverlappingRenderOrderNumber);
				
				//	painted right on white page, we don't need this one
				if (pData.paths[p].renderOrderNumber < minOverlappingRenderOrderNumber) {
					spm.setInfo("     ==> removed as bright on white for minimum overpainted RON " + minOverlappingRenderOrderNumber);
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println(" ==> removed as bright on white (RON)");
					continue;
				}
				
				//	check minimum over-painted render order number for individual sub paths (catches paths with spacially disjoint sub paths)
				PSubPath[] pSubPaths = pData.paths[p].getSubPaths();
				minOverlappingRenderOrderNumber = Integer.MAX_VALUE;
				for (int s = 0; s < pSubPaths.length; s++) {
					BoundingBox spBox = getBoundingBox(pSubPaths[s].getBounds(pData.paths[p]), pData.pdfPageContentBox, 1, 0);
					if (clipBox != null)
						spBox = new BoundingBox(
									Math.max(spBox.left, clipBox.left),
									Math.min(spBox.right, clipBox.right),
									Math.max(spBox.top, clipBox.top),
									Math.min(spBox.bottom, clipBox.bottom)
								);
					for (int c = 0; c < Math.max(1, spBox.getWidth()); c++) {
						if ((spBox.left + c + 1) < 0)
							continue;
						if ((spBox.left + c + 1) >= minRenderOrderNumbers.length)
							continue;
						int[] cMinRenderOrderNumbers = minRenderOrderNumbers[spBox.left + c + 1];
						for (int r = 0; r < Math.max(1, spBox.getHeight()); r++) {
							if ((spBox.top + r + 1) < 0)
								continue;
							if ((spBox.top + r + 1) >= cMinRenderOrderNumbers.length)
								continue;
							minOverlappingRenderOrderNumber = Math.min(cMinRenderOrderNumbers[spBox.top + r + 1], minOverlappingRenderOrderNumber);
						}
					}
				}
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println(" - minimum sub path overlapping render order number is " + minOverlappingRenderOrderNumber);
				
				//	painted right on white page, we don't need this one
				if (pData.paths[p].renderOrderNumber < minOverlappingRenderOrderNumber) {
					spm.setInfo("     ==> removed as bright on white sub paths for minimum overpainted RON " + minOverlappingRenderOrderNumber);
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println(" ==> removed as bright on white (RON SP)");
					continue;
				}
			}
			
			//	check sub paths
			PSubPath[] pSubPaths = pData.paths[p].getSubPaths();
			int inPageSubPathCount = 0;
			int inClipSubPathCount = 0;
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" - sub paths are:");
			for (int s = 0; s < pSubPaths.length; s++) {
				BoundingBox spBox = getBoundingBox(pSubPaths[s].getBounds(pData.paths[p]), pData.pdfPageContentBox, 1, 0);
				if (PdfExtractorTest.aimAtPage != -1) {
					System.out.println("   - " + pSubPaths[s].getBounds(pData.paths[p]) + ":");
					System.out.println("     - " + spBox + ":");
				}
//				
//				//	remove sub paths so small that anti aliasing would not actually paint them anyway
//				paintWidth = (pSubPaths[s].getBounds(pData.paths[p]).width * magnification);
//				paintHeight = (pSubPaths[s].getBounds(pData.paths[p]).height * magnification);
//				if ((paintWidth * paintHeight) < 0.33) {
//					pData.paths[p].removeSubPath(pSubPaths[s]);
//					if (PdfExtractorTest.aimAtPage != -1)
//						System.out.println("     - negligible at " + paintWidth + "x" + paintHeight + " pixels in rendering resolution");
//					continue;
//				}
//				if (paintWidth < 0.33) {
//					pData.paths[p].removeSubPath(pSubPaths[s]);
//					if (PdfExtractorTest.aimAtPage != -1)
//						System.out.println("     - negligible at " + paintWidth + " pixels wide in rendering resolution)");
//					continue;
//				}
//				if (paintHeight < 0.33) {
//					pData.paths[p].removeSubPath(pSubPaths[s]);
//					if (PdfExtractorTest.aimAtPage != -1)
//						System.out.println("     - negligible at " + paintHeight + " pixels high in rendering resolution)");
//					continue;
//				}
				
				//	check against page bounds
				if (spBox.overlaps(pageBox)) {
					inPageSubPathCount++;
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println("     - inside page");
				}
				else {
					pData.paths[p].removeSubPath(pSubPaths[s]);
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println("     - outside page");
					continue;
				}
				
				//	check against clipping
				if ((clipBox == null) || spBox.overlaps(clipBox)) {
					inClipSubPathCount++;
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println((clipBox == null) ? "     - no clipping" : "     - inside clipping area");
				}
				else {
					pData.paths[p].removeSubPath(pSubPaths[s]);
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println("     - outside clipping area");
					continue;
				}
			}
			
			//	skip over path completely outside page
			if (inPageSubPathCount == 0) {
				spm.setInfo("     ==> removed as completely outside page or empty safe for negligible speckles");
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println(" ==> removed as outside page or empty safe for negligible speckles");
				continue;
			}
			
			//	skip over path completely outside clipping area (if any)
			if (inClipSubPathCount == 0) {
				spm.setInfo("     ==> removed as completely clipped");
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println(" ==> removed as clipped");
				continue;
			}
			
			//	remove sub paths lying outside clipping area
			if (inClipSubPathCount < inPageSubPathCount) {
				pSubPaths = pData.paths[p].getSubPaths();
				spm.setInfo("     ==> clipped to " + pSubPaths.length + " sub paths");
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println(" ==> clipped to " + pSubPaths.length + " sub paths");
			}
			
			//	retain path, at last
			pPaths.add(pData.paths[p]);
			
			//	update minimum render order number in grid (for individual sub paths only, they might be disjoint)
			for (int s = 0; s < pSubPaths.length; s++) {
				BoundingBox subPathBox = getBoundingBox(pSubPaths[s].getBounds(pData.paths[p]), pData.pdfPageContentBox, 1, 0);
				for (int c = 0; c < Math.max(1, subPathBox.getWidth()); c++) {
					if ((subPathBox.left + c + 1) < 0)
						continue;
					if ((subPathBox.left + c + 1) >= minRenderOrderNumbers.length)
						continue;
					int[] cMinRenderOrderNumbers = minRenderOrderNumbers[subPathBox.left + c + 1];
					for (int r = 0; r < Math.max(1, subPathBox.getHeight()); r++) {
						if ((subPathBox.top + r + 1) < 0)
							continue;
						if ((subPathBox.top + r + 1) >= cMinRenderOrderNumbers.length)
							continue;
						cMinRenderOrderNumbers[subPathBox.top + r + 1] = Math.min(cMinRenderOrderNumbers[subPathBox.top + r + 1], pData.paths[p].renderOrderNumber);
					}
				}
			}
//			BoundingBox pathBox = getBoundingBox(pathBounds, pData.pdfPageContentBox, 1, 0);
//			for (int c = 0; c < Math.max(1, pathBox.getWidth()); c++) {
//				if ((pathBox.left + c + 1) < 0)
//					continue;
//				if ((pathBox.left + c + 1) >= minRenderOrderNumbers.length)
//					continue;
//				int[] cMinRenderOrderNumbers = minRenderOrderNumbers[pathBox.left + c + 1];
//				for (int r = 0; r < Math.max(1, pathBox.getHeight()); r++) {
//					if ((pathBox.top + r + 1) < 0)
//						continue;
//					if ((pathBox.top + r + 1) >= cMinRenderOrderNumbers.length)
//						continue;
//					cMinRenderOrderNumbers[pathBox.top + r + 1] = Math.min(cMinRenderOrderNumbers[pathBox.top + r + 1], pData.paths[p].renderOrderNumber);
//				}
//			}
		}
		if (pPaths.size() < pData.paths.length) {
			spm.setInfo(" ==> removed " + (pData.paths.length - pPaths.size()) + " paths as invisible");
			pData.paths = ((PPath[]) pPaths.toArray(new PPath[pPaths.size()]));
		}
		
		//	sort out duplicate paths (can occur for overlapping data points in naively included diagrams, for instance)
		HashSet pathSigs = new HashSet();
		pPaths.clear();
		for (int p = (pData.paths.length - 1); p >= 0; p--) {
			if (pathSigs.add(pData.paths[p].getShapesSignature()))
				pPaths.add(pData.paths[p]);
			//	TODO maybe aggregate opacity (observing both alpha and blend mode)
		}
		if (pPaths.size() < pData.paths.length) {
			spm.setInfo(" ==> removed " + (pData.paths.length - pPaths.size()) + " paths as shape duplicates");
			Collections.reverse(pPaths); // working back to front to make sure to retain highest RON, so need to reverse here
			pData.paths = ((PPath[]) pPaths.toArray(new PPath[pPaths.size()]));
		}
		
		//	split up spacially disjoint sub paths
		pPaths.clear();
		for (int p = 0; p < pData.paths.length; p++) {
			PSubPath[] pSubPaths = pData.paths[p].getSubPaths();
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println("Checking " + pSubPaths.length + " sub paths in " + pData.paths[p].getBounds());
			
			//	aggregate sub paths
			ArrayList pSubPathClusters = new ArrayList();
			while (true) {
				PSubPathCluster pSubPathCluster = null;
				for (boolean subPathAdded = true; subPathAdded;) {
					subPathAdded = false;
					for (int s = 0; s < pSubPaths.length; s++) {
						if (pSubPaths[s] == null)
							continue; // handled this one before
						Rectangle2D subPathBounds = pSubPaths[s].getBounds(pData.paths[p]);
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println(" - sub path at " + subPathBounds);
						
						//	start new aggregate path
						if (pSubPathCluster == null) {
							pSubPathCluster = new PSubPathCluster(pData.paths[p], pSubPaths[s]);
							subPathAdded = true;
							pSubPaths[s] = null;
							if (PdfExtractorTest.aimAtPage != -1)
								System.out.println(" ==> started new cluster");
						}
						
						//	this one overlaps with current cluster, add it
						else if (pSubPathCluster.overlaps(pSubPaths[s])) {
							pSubPathCluster.addPath(pSubPaths[s]);
							subPathAdded = true;
							pSubPaths[s] = null;
							if (PdfExtractorTest.aimAtPage != -1)
								System.out.println(" ==> added to current cluster");
						}
						else if (PdfExtractorTest.aimAtPage != -1)
							System.out.println(" ==> outside current cluster");
					}
				}
				if (pSubPathCluster != null) {
					spm.setInfo("   - got sub paths cluster with " + pSubPathCluster.subPaths.size() + " paths at " + pSubPathCluster.bounds + ":");
					pSubPathClusters.add(pSubPathCluster);
				}
				else break;
			}
			spm.setInfo("   - aggregated " + pSubPaths.length + " sub paths into " + pSubPathClusters.size() + " clusters");
			
			//	nothing to split up here
			if (pSubPathClusters.size() < 2) {
				pPaths.add(pData.paths[p]);
				spm.setInfo("   ==> retained as a whole");
				continue;
			}
			
			//	make each cluster into a separate path
			spm.setInfo("   ==> splitting up path");
			for (int c = 0; c < pSubPathClusters.size(); c++) {
				PSubPath[] cSubPaths = ((PSubPathCluster) pSubPathClusters.get(c)).getSubPaths();
				pPaths.add(new PPath(pData.paths[p], cSubPaths));
			}
		}
		if (pPaths.size() > pData.paths.length) {
			spm.setInfo(" ==> added " + (pPaths.size() - pData.paths.length) + " paths via splitting");
			pData.paths = ((PPath[]) pPaths.toArray(new PPath[pPaths.size()]));
		}
	}
	
//	private static BoundingBox getClipBox(PPath[] clipPaths, Rectangle2D.Float pageContentBox, float magnification, int rotate) {
//		if (clipPaths == null)
//			return null;
//		int cpLeft = Integer.MIN_VALUE;
//		int cpRight = Integer.MAX_VALUE;
//		int cpTop = Integer.MIN_VALUE;
//		int cpBottom = Integer.MAX_VALUE;
//		for (int cp = 0; cp < clipPaths.length; cp++) {
//			BoundingBox pClipBox = getBoundingBox(clipPaths[cp].getBounds(), pageContentBox, magnification, rotate);
//			cpLeft = Math.max(cpLeft, pClipBox.left);
//			cpRight = Math.min(cpRight, pClipBox.right);
//			cpTop = Math.max(cpTop, pClipBox.top);
//			cpBottom = Math.min(cpBottom, pClipBox.bottom);
//		}
//		return new BoundingBox(cpLeft, cpRight, cpTop, cpBottom);
//	}
//	
	private static class PSubPathCluster {
		PPath path;
		ArrayList subPaths = new ArrayList();
		Rectangle2D bounds;
		PSubPathCluster(PPath path, PSubPath firstSubPath) {
			this.path = path;
			this.subPaths.add(firstSubPath);
			Rectangle2D.Float fpBounds = firstSubPath.getBounds(this.path);
			this.bounds = new Rectangle2D.Float(fpBounds.x, fpBounds.y, fpBounds.width, fpBounds.height);
		}
		boolean overlaps(PSubPath subPath) {
			return PdfExtractor.overlaps(subPath.getBounds(this.path), this.bounds);
		}
		void addPath(PSubPath subPath) {
			this.subPaths.add(subPath);
			this.bounds = this.bounds.createUnion(subPath.getBounds(this.path));
		}
		PSubPath[] getSubPaths() {
			return ((PSubPath[]) this.subPaths.toArray(new PSubPath[this.subPaths.size()]));
		}
	}
	
	private void storeGraphicsAsSupplements(ImDocument doc, PPageData pData, PPathCluster[] pPathClusters, boolean gotFlippedPaths, int pageImageDpi, float magnification, ProgressMonitor spm) throws IOException {
		if (pData.paths.length == 0)
			return;
		spm.setInfo(" - storing vector based graphics");
		
		//	get (and normalize) page bounds (wider if page has flipped content, as latter might well extend page bounds rightward)
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println("Page bounds are " + pData.pdfPageContentBox);
		Rectangle2D.Float pageBounds;
		if (pData.pdfPageContentBox.height <= pData.pdfPageContentBox.y)
			pageBounds = new Rectangle2D.Float(pData.pdfPageContentBox.x, (pData.pdfPageContentBox.y - pData.pdfPageContentBox.height), pData.pdfPageContentBox.width, pData.pdfPageContentBox.height);
		else pageBounds = pData.pdfPageContentBox;
		if (gotFlippedPaths)
			pageBounds = new Rectangle2D.Float(pageBounds.x, pageBounds.y, Math.max(pageBounds.width, pageBounds.height), pageBounds.height);
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println("Page box is " + pageBounds);
		BoundingBox pageBox = getBoundingBox(pageBounds, pData.pdfPageContentBox, 1, 0);
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println("Page box is " + pageBox);
		
		//	restore original rendering order
		for (int c = 0; c < pPathClusters.length; c++) {
			for (int p = 0; p < pPathClusters[c].paths.size(); p++) {
				PPath pPath = ((PPath) pPathClusters[c].paths.get(p));
				
				//	re-check if any sub paths inside page
				PSubPath[] pSubPaths = pPath.getSubPaths();
//				System.out.println("     - checking path at " + pPath.getBounds() + " with " + pSubPaths.length + " sub paths");
				boolean noSubPathsInPage = true;
				for (int s = 0; s < pSubPaths.length; s++) {
					BoundingBox spBox = getBoundingBox(pSubPaths[s].getBounds(pPath), pData.pdfPageContentBox, 1, 0);
					if (spBox.overlaps(pageBox)) {
						noSubPathsInPage = false;
						break;
					}
				}
				
				//	remove path completely outside page
				if (noSubPathsInPage) {
					pPathClusters[c].paths.remove(p--);
//					System.out.println("       ==> no sub paths inside page");
				}
			}
			
			//	make sure paths render in intended order
			if (pPathClusters[c].paths.size() > 1)
				Collections.sort(pPathClusters[c].paths, pPathRonOrder);
		}
		
		//	make sure path clusters render in intended order
		if (pPathClusters.length > 1)
			Arrays.sort(pPathClusters, pPathClusterRonOrder);
		
		//	eliminate out-of-page clusters
		ArrayList pPathClusterList = new ArrayList(Arrays.asList(pPathClusters));
		for (int c = 0; c < pPathClusterList.size(); c++) {
			PPathCluster pPathCluster = ((PPathCluster) pPathClusterList.get(c));
			if (pPathCluster.paths.isEmpty())
				pPathClusterList.remove(c--);
		}
		
		//	merge path clusters that are close together (less than DPI/6 apart or so, no way anything fits in between there)
		for (boolean pPathClustersMerged = true; pPathClustersMerged;) {
			pPathClustersMerged = false;
			for (int c = 0; c < pPathClusterList.size(); c++) {
				PPathCluster pPathCluster = ((PPathCluster) pPathClusterList.get(c));
				
				//	don't involve page decoration
				if (pPathCluster.isPageDecoration)
					continue;
				
				//	check for mergers
				BoundingBox pPathClusterBox = getBoundingBox(pPathCluster.visibleBounds, pData.pdfPageContentBox, magnification, pData.rotate);
				for (int cc = (c+1); cc < pPathClusterList.size(); cc++) {
					PPathCluster cpPathCluster = ((PPathCluster) pPathClusterList.get(cc));
					
					//	still don't involve page decoration
					if (cpPathCluster.isPageDecoration)
						continue;
					
					//	merge clusters if possible
					BoundingBox cpPathClusterBox = getBoundingBox(cpPathCluster.visibleBounds, pData.pdfPageContentBox, magnification, pData.rotate);
					if (canMergePathClusters(pPathClusterBox, cpPathClusterBox, pageImageDpi)) {
						for (int p = 0; p < cpPathCluster.paths.size(); p++)
							pPathCluster.addPath((PPath) cpPathCluster.paths.get(p));
						if (pPathCluster.paths.size() > 1)
							Collections.sort(pPathCluster.paths, pPathRonOrder);
						pPathClusterList.remove(cc--);
						pPathClustersMerged = true;
					}
				}
			}
		}
		
		//	TODO also try flood filling space between graphics to determine minimum distances
		//	TODO TEST s41559-018-0667-3.pdf, Pages 3 and 4
		
		//	go back to working on array
		if (pPathClusterList.size() < pPathClusters.length)
			pPathClusters = ((PPathCluster[]) pPathClusterList.toArray(new PPathCluster[pPathClusterList.size()]));
		
		//	make sure path clusters render in intended order
		if (pPathClusters.length > 1)
			Arrays.sort(pPathClusters, pPathClusterRonOrder);
		
		//	display graphics if testing
		ImageDisplayDialog gdd = null;
		BufferedImage agi = null;
		Graphics2D agiGr = null;
		float gMinSize = 0;
		if (DEBUG_EXTRACT_FIGURES && (PdfExtractorTest.aimAtPage != -1)) {
			gdd = new ImageDisplayDialog("Graphics in Page " + pData.p);
			agi = new BufferedImage((((int) Math.ceil(pData.pdfPageContentBox.getWidth() * magnification)) + 20), (((int) Math.ceil(pData.pdfPageContentBox.getHeight() * magnification)) + 20), BufferedImage.TYPE_INT_ARGB);
			agiGr = agi.createGraphics();
			agiGr.setColor(Color.WHITE);
			agiGr.fillRect(0, 0, agi.getWidth(), agi.getHeight());
			agiGr.translate(10, 10);
			agiGr.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			if (pPathClusters.length > 256) {
				float[] pPathClusterSizes = new float[pPathClusters.length];
				for (int c = 0; c < pPathClusters.length; c++)
					pPathClusterSizes[c] = ((float) Math.abs(pPathClusters[c].bounds.getWidth() * pPathClusters[c].bounds.getHeight()));
				Arrays.sort(pPathClusterSizes);
				gMinSize = pPathClusterSizes[pPathClusterSizes.length - 256];
			}
		}
		
		//	store vector graphics
		for (int c = 0; c < pPathClusters.length; c++) {
			PPathCluster pPathCluster = pPathClusters[c];
			spm.setInfo("   - " + pPathCluster.bounds + ", " + pPathCluster.paths.size() + " paths");
			
			//	this one only has out-of-page paths
			if (pPathCluster.paths.isEmpty())
				continue;
			
			//	compute bounds in page resolution
			BoundingBox pathClusterBox = getBoundingBox(pPathCluster.bounds, pData.pdfPageContentBox, magnification, pData.rotate);
			if (PdfExtractorTest.aimAtPage != -1)
				spm.setInfo("     - bounding box is " + pathClusterBox + " from " + pPathCluster.bounds);
			BoundingBox pathClusterClipBox = getBoundingBox(pPathCluster.visibleBounds, pData.pdfPageContentBox, magnification, pData.rotate);
			if (PdfExtractorTest.aimAtPage != -1)
				spm.setInfo("     - clip area is " + pathClusterClipBox + " from " + pPathCluster.visibleBounds);
			if ((pathClusterClipBox != null) && !pathClusterBox.overlaps(pathClusterClipBox) && pPathCluster.bounds.intersects(pPathCluster.visibleBounds)) {
				if ((pathClusterBox.getWidth() < 2) && (pathClusterClipBox.getWidth() < 2) && ((pathClusterBox.right == pathClusterClipBox.left) || (pathClusterClipBox.left == pathClusterBox.right))) {
					if (PdfExtractorTest.aimAtPage != -1)
						spm.setInfo("     ==> clip area adjacent to left or right");
					pathClusterClipBox = new BoundingBox(pathClusterBox.left, pathClusterBox.right, pathClusterClipBox.top, pathClusterClipBox.bottom);
					if (PdfExtractorTest.aimAtPage != -1)
						spm.setInfo("     ==> clip area corrected to " + pathClusterClipBox + " from " + pPathCluster.visibleBounds);
				}
				else if ((pathClusterBox.getHeight() < 2) && (pathClusterClipBox.getHeight() < 2) && ((pathClusterBox.bottom == pathClusterClipBox.top) || (pathClusterClipBox.top == pathClusterBox.top))) {
					if (PdfExtractorTest.aimAtPage != -1)
						spm.setInfo("     ==> clip area adjacent to top or bottom");
					pathClusterClipBox = new BoundingBox(pathClusterClipBox.left, pathClusterClipBox.right, pathClusterBox.top, pathClusterBox.bottom);
					if (PdfExtractorTest.aimAtPage != -1)
						spm.setInfo("     ==> clip area corrected to " + pathClusterClipBox + " from " + pPathCluster.visibleBounds);
				}
				else {
					if (PdfExtractorTest.aimAtPage != -1)
						spm.setInfo("     ==> completely clipped");
					continue;
				}
			}
			
			//	add supplement to document
			Graphics graphics;
			synchronized (doc) { // need to synchronize adding graphics to document
				graphics = Graphics.createGraphics(doc, pData.p, ((PPath) pPathCluster.paths.get(0)).renderOrderNumber, pathClusterBox, pathClusterClipBox);
				if (pPathCluster.isPageDecoration)
					graphics.setAttribute(Graphics.PAGE_DECORATION_ATTRIBUTE);
			}
			
			//	add individual paths (with stroking and filling properties)
			for (int p = 0; p < pPathCluster.paths.size(); p++) {
				
				//	add path proper
				PPath pPath = ((PPath) pPathCluster.paths.get(p));
				BoundingBox pathBounds = getBoundingBox(pPath.getBounds(), pData.pdfPageContentBox, magnification, pData.rotate);
				BoundingBox pathClipBounds = ((pPath.clipPaths == null) ? null : getBoundingBox(pPath.visibleBounds, pData.pdfPageContentBox, magnification, pData.rotate));
				Graphics.Path path = new Graphics.Path(pathBounds, pathClipBounds, pPath.renderOrderNumber, pPath.strokeColor, pPath.lineWidth, pPath.lineCapStyle, pPath.lineJointStyle, pPath.miterLimit, pPath.dashPattern, pPath.dashPatternPhase, pPath.fillColor, pPath.fillEvenOdd);
				graphics.addPath(path);
				
				//	add individual sub paths
				PSubPath[] pSubPaths = pPath.getSubPaths();
				for (int sp = 0; sp < pSubPaths.length; sp++) {
					
					//	add sub path proper
					BoundingBox subPathBounds = getBoundingBox(pSubPaths[sp].getBounds(pPath), pData.pdfPageContentBox, magnification, pData.rotate);
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
				Graphics.Path[] paths = graphics.getPaths();
				float pPathClusterSize = ((float) Math.abs(pPathClusters[c].bounds.getWidth() * pPathClusters[c].bounds.getHeight()));
				if (pPathClusterSize > gMinSize) {
					BufferedImage pPathClusterImage = new BufferedImage((((int) Math.ceil(pPathCluster.bounds.getWidth() * magnification)) + 20), (((int) Math.ceil(pPathCluster.bounds.getHeight() * magnification)) + 20), BufferedImage.TYPE_INT_ARGB);
					Graphics2D gr = pPathClusterImage.createGraphics();
					gr.setColor(Color.WHITE);
					gr.fillRect(0, 0, pPathClusterImage.getWidth(), pPathClusterImage.getHeight());
					gr.translate(10, 10);
					gr.scale(magnification, magnification);
					gr.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
					for (int p = 0; p < paths.length; p++) {
						Color preColor = gr.getColor();
						Stroke preStroke = gr.getStroke();
						Shape preClip = gr.getClip();
						Color strokeColor = paths[p].getStrokeColor();
						Stroke stroke = paths[p].getStroke();
						Color fillColor = paths[p].getFillColor();
						//	TODOne observe clipping ==> we're removing all sub paths outside clipping area in sanitization ... but filling remains a problem at times !!!
						Path2D path = new Path2D.Float();
						path.setWindingRule(paths[p].isFilledEvenOdd() ? Path2D.WIND_EVEN_ODD : Path2D.WIND_NON_ZERO);
						Graphics.SubPath[] subPaths = paths[p].getSubPaths();
						for (int s = 0; s < subPaths.length; s++) {
							Path2D subPath = subPaths[s].getPath();
							path.append(subPath, false);
						}
						if (paths[p].clipBounds != null) {
							Rectangle2D.Double clip = new Rectangle2D.Double(
									((paths[p].clipBounds.left - graphics.getBounds().left) / magnification),
									((paths[p].clipBounds.top - graphics.getBounds().top) / magnification),
									(paths[p].clipBounds.getWidth() / magnification),
									(paths[p].clipBounds.getHeight() / magnification)
								);
							gr.clip(clip);
						}
						if (fillColor != null) {
//							gr.setColor(fillColor);
							gr.setColor(Color.YELLOW);
							System.out.println("FILLING PATH WITH RON " + paths[p].renderOrderNumber + " AT " + paths[p].getExtent() + " IN " + Integer.toString((fillColor.getRGB() & 0xFFFFFF), 16) + " WITH ALPHA " + Integer.toString(((fillColor.getRGB() >>> 24) & 0xFF), 16));
							gr.fill(path);
						}
						if (strokeColor != null) {
//							gr.setColor(strokeColor);
							gr.setColor(Color.RED);
							System.out.println("STROKING PATH WITH RON " + paths[p].renderOrderNumber + " AT " + paths[p].getExtent() + " IN " + Integer.toString((strokeColor.getRGB() & 0xFFFFFF), 16) + " WITH ALPHA " + Integer.toString(((strokeColor.getRGB() >>> 24) & 0xFF), 16));
							gr.setStroke(stroke);
							gr.draw(path);
						}
						gr.setColor(preColor);
						gr.setStroke(preStroke);
						gr.setClip(preClip);
					}
					gdd.addImage(pPathClusterImage, pathClusterBox.toString());
				}
				AffineTransform preAt = agiGr.getTransform();
				agiGr.translate(pathClusterBox.left, pathClusterBox.top);
				agiGr.scale(magnification, magnification);
				for (int p = 0; p < paths.length; p++) {
					Color preColor = agiGr.getColor();
					Stroke preStroke = agiGr.getStroke();
					Shape preClip = agiGr.getClip();
					Color strokeColor = paths[p].getStrokeColor();
					Stroke stroke = paths[p].getStroke();
					Color fillColor = paths[p].getFillColor();
					Path2D path = new Path2D.Float();
					path.setWindingRule(paths[p].isFilledEvenOdd() ? Path2D.WIND_EVEN_ODD : Path2D.WIND_NON_ZERO);
					Graphics.SubPath[] subPaths = paths[p].getSubPaths();
					for (int s = 0; s < subPaths.length; s++) {
						Path2D subPath = subPaths[s].getPath();
						path.append(subPath, false);
					}
					if (paths[p].clipBounds != null) {
						Rectangle2D.Double clip = new Rectangle2D.Double(
								(paths[p].clipBounds.left / magnification),
								(paths[p].clipBounds.top / magnification),
								(paths[p].clipBounds.getWidth() / magnification),
								(paths[p].clipBounds.getHeight() / magnification)
							);
						agiGr.clip(clip);
					}
					if (fillColor != null) {
						agiGr.setColor(fillColor);
						agiGr.fill(path);
					}
					if (strokeColor != null) {
						agiGr.setColor(strokeColor);
						agiGr.setStroke(stroke);
						agiGr.draw(path);
					}
//					else if (fillColor != null) {
//						agiGr.setColor(fillColor);
//						agiGr.draw(path);
//					}
					agiGr.setColor(preColor);
					agiGr.setStroke(preStroke);
					agiGr.setClip(preClip);
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
	
	private static boolean canMergePathClusters(BoundingBox pPathClusterBox, BoundingBox cpPathClusterBox, int pageImageDpi) {
		
		//	we have an overlap
		if (pPathClusterBox.overlaps(cpPathClusterBox))
			return true;
		
		//	compute horizontal overlap and distance
		int hOverlap;
		float hOverlapRatio;
		int hDistance;
		int minLeft = Math.min(pPathClusterBox.left, cpPathClusterBox.left);
		int maxLeft = Math.max(pPathClusterBox.left, cpPathClusterBox.left);
		int minRight = Math.min(pPathClusterBox.right, cpPathClusterBox.right);
		int maxRight = Math.max(pPathClusterBox.right, cpPathClusterBox.right);
		
		//	disjoint (in different columns)
		if ((minRight <= maxLeft)) {
			hOverlap = 0;
			hOverlapRatio = 0;
			hDistance = (maxLeft - minRight);
		}
		
		//	we have an overlap (one above the other)
		else {
			hOverlap = (minRight - maxLeft);
			hOverlapRatio = (((float) hOverlap) / (maxRight - minLeft));
			hDistance = -1;
		}
		
		//	compute vertical overlap and distance
		int vOverlap;
		float vOverlapRatio;
		int vDistance;
		int minTop = Math.min(pPathClusterBox.top, cpPathClusterBox.top);
		int maxTop = Math.max(pPathClusterBox.top, cpPathClusterBox.top);
		int minBottom = Math.min(pPathClusterBox.bottom, cpPathClusterBox.bottom);
		int maxBottom = Math.max(pPathClusterBox.bottom, cpPathClusterBox.bottom);
		
		//	disjoint (one above the other)
		if ((minBottom <= maxTop)) {
			vOverlap = 0;
			vOverlapRatio = 0;
			vDistance = (maxTop - minBottom);
		}
		
		//	we have an overlap (side by side)
		else {
			vOverlap = (minBottom - maxTop);
			vOverlapRatio = (((float) vOverlap) / (maxBottom - minTop));
			vDistance = -1;
		}
		
		//	we have both a horizontal and a vertical distance, no way we merge these two
		if ((-1 < hDistance) && (-1 < vDistance))
			return false;
		
		//	clusters on top of one another, less than DPI/8 apart, and considerable horizontal overlap
		if ((-1 < vDistance) && ((vDistance * 8) < pageImageDpi)) {
//			if (pPathClusterBox.getWidth() == hOverlap)
//				return true; // first bounding box completely covered by second one
//			if (cpPathClusterBox.getWidth() == hOverlap)
//				return true; // second bounding box completely covered by first one
			return (hOverlapRatio > 0.9); // 90% overlap
		}
		
		//	clusters side by side one another, less than DPI/6 apart, and considerable vertical overlap
		if ((-1 < hDistance) && ((hDistance * 6) < pageImageDpi)) {
//			if (pPathClusterBox.getHeight() == vOverlap)
//				return true; // first bounding box completely covered by second one
//			if (cpPathClusterBox.getHeight() == vOverlap)
//				return true; // second bounding box completely covered by first one
			return (vOverlapRatio > 0.9); // 90% overlap
		}
		
		//	these two are no good
		return false;
	}
	
	private static final Comparator pPathRonOrder = new Comparator() {
		public int compare(Object obj1, Object obj2) {
			PPath pp1 = ((PPath) obj1);
			PPath pp2 = ((PPath) obj2);
			return (pp1.renderOrderNumber - pp2.renderOrderNumber);
		}
	};
	
	private static final Comparator pPathClusterRonOrder = new Comparator() {
		public int compare(Object obj1, Object obj2) {
			PPathCluster ppc1 = ((PPathCluster) obj1);
			PPathCluster ppc2 = ((PPathCluster) obj2);
			PPath pp1 = (ppc1.paths.isEmpty() ? null : ((PPath) ppc1.paths.get(0)));
			PPath pp2 = (ppc2.paths.isEmpty() ? null : ((PPath) ppc2.paths.get(0)));
			if ((pp1 == null) && (pp2 == null))
				return 0;
			else if (pp2 == null)
				return -1;
			else if (pp1 == null)
				return 1;
			else return pPathRonOrder.compare(pp1, pp2);
		}
	};
	
	private static PPathCluster[] getPathClusters(PPath[] paths) {
		
		//	sort paths by area (descending) to speed up clustering
		PPath[] pathsWorking = new PPath[paths.length];
		System.arraycopy(paths, 0, pathsWorking, 0, paths.length);
		Arrays.sort(pathsWorking, Collections.reverseOrder(pathSizeOrder));
		
		//	aggregate paths
		ArrayList pathClusters = new ArrayList();
		while (true) {
			/* TODO make this faster for large number of disjoint paths:
			 * - put every path in array
			 * - initialize path bounds in array to index of path in linear aray
			 * - use maybe 300 DPI scale, for better accuracy
			 *   ==> try 192 DPI first, as it's faster, and scale up in case of problems
			 * - do several rounds of flood filling around each path (maybe so each path can extend (DPI/50) to all directions)
			 * - collect indexes of paths that end up adjacent in lists ...
			 * - ... compute convext hull, and collect all other indexes inside ...
			 * - ... until not hing more added
			 * - cluster up paths that end up in same list
			 * ==> saves O(n^2) overlap checks
			 * 
			 * TEST rsos.200092.pdf (all pages with tables have dotted lines as distinct graphics objects)
			 */
			PPathCluster pathCluster = null;
			for (boolean pathAdded = true; pathAdded;) {
				pathAdded = false;
				for (int p = 0; p < pathsWorking.length; p++) {
					if (pathsWorking[p] == null)
						continue; // handled this one before
					Rectangle2D pathBounds = pathsWorking[p].getBounds();
					if (PdfExtractorTest.aimAtPage != -1) {
						System.out.println("Handling path at " + pathBounds);
						System.out.println(" - stroke color is " + pathsWorking[p].strokeColor + ((pathsWorking[p].strokeColor == null) ? "" : (", alpha " + pathsWorking[p].strokeColor.getAlpha())));
						System.out.println(" - line width is " + pathsWorking[p].lineWidth);
						System.out.println(" - fill color is " + pathsWorking[p].fillColor + ((pathsWorking[p].fillColor == null) ? "" : (", alpha " + pathsWorking[p].fillColor.getAlpha())));
						System.out.println(" - render order number is " + pathsWorking[p].renderOrderNumber);
					}
					
					//	keep page decoration out of content graphics clusters
					if (pathsWorking[p].isPageDecoration()) {
						pathClusters.add(new PPathCluster(pathsWorking[p]));
						pathAdded = true;
						pathsWorking[p] = null;
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println(" ==> added page decoration cluster");
					}
					
					//	start new aggregate path
					else if (pathCluster == null) {
						pathCluster = new PPathCluster(pathsWorking[p]);
						pathAdded = true;
						pathsWorking[p] = null;
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println(" ==> started new cluster");
					}
					
					//	this one overlaps with current cluster, add it
					else if (pathCluster.overlaps(pathsWorking[p])) {
						pathCluster.addPath(pathsWorking[p]);
						pathAdded = true;
						pathsWorking[p] = null;
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println(" ==> added to current cluster");
					}
					
					//	this one doesn't fit right now
					else if (PdfExtractorTest.aimAtPage != -1)
						System.out.println(" ==> outside current cluster");
				}
			}
			if (pathCluster != null) {
				System.out.println("   - got path cluster with " + pathCluster.paths.size() + " paths at " + pathCluster.bounds + ":");
//				for (int p = 0; p < pPathCluster.paths.size(); p++) {
//					PPath pPath = ((PPath) pPathCluster.paths.get(p));
//					System.out.println("     - path at " + pPath.getBounds());
//					System.out.println("       - stroke color is " + pPath.strokeColor + ((pPath.strokeColor == null) ? "" : (", alpha " + pPath.strokeColor.getAlpha())));
//					System.out.println("       - line width is " + pPath.lineWidth);
//					System.out.println("       - fill color is " + pPath.fillColor + ((pPath.fillColor == null) ? "" : (", alpha " + pPath.fillColor.getAlpha())));
//					System.out.println("       - render order number is " + pPath.renderOrderNumber);
//				}
				pathClusters.add(pathCluster);
			}
			else break;
		}
		
		//	in each cluster, sort paths by render order number
		for (int c = 0; c < pathClusters.size(); c++) {
			PPathCluster pc = ((PPathCluster) pathClusters.get(c));
			if (pc.paths.size() > 1)
				Collections.sort(pc.paths, pPathRonOrder);
		}
		
		//	sort path clusters proper by (minimum) render order number
		Collections.sort(pathClusters, pPathClusterRonOrder);
		
		//	finally ...
		return ((PPathCluster[]) pathClusters.toArray(new PPathCluster[pathClusters.size()]));
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
		final ArrayList paths = new ArrayList();
		Rectangle2D bounds;
		Rectangle2D visibleBounds;
		final boolean isPageDecoration;
		PPathCluster(PPath firstPath) {
			this.paths.add(firstPath);
			Rectangle2D.Float fpBounds = firstPath.getBounds();
			this.bounds = new Rectangle2D.Float(fpBounds.x, fpBounds.y, fpBounds.width, fpBounds.height);
			this.visibleBounds = firstPath.visibleBounds;
			this.isPageDecoration = firstPath.isPageDecoration();
		}
		boolean overlaps(PPath path) {
			return PdfExtractor.overlaps(path.visibleBounds, this.visibleBounds);
		}
		void addPath(PPath path) {
			this.paths.add(path);
			this.bounds = this.bounds.createUnion(path.getBounds());
			this.visibleBounds = this.visibleBounds.createUnion(path.visibleBounds);
		}
	}
	
	private static void sanitizePageFigures(PPageData pData) {
		
		//	little to merge with fewer than two figures
		if (pData.figures.length < 2)
			return;
		
		/* combine figures that are adjacent in one dimension and identical in
		 * the other, and do so recursively, both top-down and left-right, to
		 * also catch weirdly tiled images */
		ArrayList figures = new ArrayList(Arrays.asList(pData.figures));
		while (figures.size() > 1) {
			int figureCount = figures.size();
			
			//	try merging left to right
			Collections.sort(figures, leftRightFigureOrder);
			for (int f = 0; f < figures.size(); f++) {
				PFigure pf = ((PFigure) figures.get(f));
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println("Checking image " + pf + " for left-right mergers");
				PFigure mpf = null;
				for (int cf = (f+1); cf < figures.size(); cf++) {
					PFigure cpf = ((PFigure) figures.get(cf));
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println(" - comparing to image " + cpf);
					if (!figureEdgeMatch(pf.bounds.getMaxY(), cpf.bounds.getMaxY())) {
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println(" ==> top edge mismatch");
						continue;
					}
					if (!figureEdgeMatch(pf.bounds.getMinY(), cpf.bounds.getMinY())) {
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println(" ==> bottom edge mismatch");
						continue;
					}
					if (!figureEdgeMatch(pf.bounds.getMaxX(), cpf.bounds.getMinX())) {
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println(" ==> not adjacent (" + pf.bounds.getMaxX() + " != " + cpf.bounds.getMinX() + ")");
						continue;
					}
					mpf = new PFigure(pf, cpf);
					figures.remove(cf);
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println(" ==> got merged figure " + mpf);
					break;
				}
				if (mpf != null)
					figures.set(f--, mpf);
			}
			
			//	try merging top down
			Collections.sort(figures, topDownFigureOrder);
			for (int f = 0; f < figures.size(); f++) {
				PFigure pf = ((PFigure) figures.get(f));
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println("Checking image " + pf + " for top-down mergers");
				PFigure mpf = null;
				for (int cf = (f+1); cf < figures.size(); cf++) {
					PFigure cpf = ((PFigure) figures.get(cf));
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println(" - comparing to image " + cpf);
					if (!figureEdgeMatch(pf.bounds.getMinX(), cpf.bounds.getMinX())) {
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println(" ==> left edge mismatch");
						continue;
					}
					if (!figureEdgeMatch(pf.bounds.getMaxX(), cpf.bounds.getMaxX())) {
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println(" ==> right edge mismatch");
						continue;
					}
					if (!figureEdgeMatch(pf.bounds.getMinY(), cpf.bounds.getMaxY())) {
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println(" ==> not adjacent (" + pf.bounds.getMinY() + " != " + cpf.bounds.getMaxY() + ")");
						continue;
					}
					mpf = new PFigure(pf, cpf);
					figures.remove(cf);
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println(" ==> got merged figure " + mpf);
					break;
				}
				if (mpf != null)
					figures.set(f--, mpf);
			}
			
			//	nothing merged in either direction, we're done
			if (figures.size() == figureCount)
				break;
		}
		
		//	did we change anything?
		if (figures.size() < pData.figures.length)
			pData.figures = ((PFigure[]) figures.toArray(new PFigure[figures.size()]));
	}
	
	private static final Comparator leftRightFigureOrder = new Comparator() {
		public int compare(Object obj1, Object obj2) {
			PFigure pf1 = ((PFigure) obj1);
			PFigure pf2 = ((PFigure) obj2);
			int c = Double.compare(pf1.bounds.getMinX(), pf2.bounds.getMinX());
			return ((c == 0) ? Double.compare(pf2.bounds.getMaxX(), pf1.bounds.getMaxX()) : c);
		}
	};
	private static final Comparator topDownFigureOrder = new Comparator() {
		public int compare(Object obj1, Object obj2) {
			PFigure pf1 = ((PFigure) obj1);
			PFigure pf2 = ((PFigure) obj2);
			int c = Double.compare(pf2.bounds.getMaxY(), pf1.bounds.getMaxY());
			return ((c == 0) ? Double.compare(pf1.bounds.getMinY(), pf2.bounds.getMinY()) : c);
		}
	};
	private static final boolean figureEdgeMatch(double e1, double e2) {
//		return (e1 == e2); // TODO use this to see individual values
		return (Math.abs(e1 - e2) < 0.001); // TODO adjust this threshold
	}
	
	private static class PPageContentOrientation {
		final byte[][] direction;
		final byte[][] weight;
		PPageContentOrientation(byte[][] direction, byte[][] weight) {
			this.direction = direction;
			this.weight = weight;
		}
	}
	
	private static PPageContentOrientation getPageContentOrientation(PPageData pData) {
		
		//	convert bounds, as PDF Y coordinate is bottom-up, whereas Java, JavaScript, etc. Y coordinate is top-down
		BoundingBox pb = getBoundingBox(pData.pdfPageContentBox, pData.pdfPageContentBox, 1, pData.rotate);
		//	TODOne figure out why this sucker comes up with negative top and 0 bottom ==> media box simply has its X set to page top, and then positive height in PDF sense ... it IS above the page
		System.out.println("Page bounds for content orientation is " + pData.pdfPageContentBox);
		System.out.println("Page box for content orientation is " + pb);
		byte[][] pPageContentOrientation = new byte[pb.getWidth()][pb.getHeight()];
		for (int c = 0; c < pPageContentOrientation.length; c++)
			Arrays.fill(pPageContentOrientation[c], Byte.MIN_VALUE);
		byte[][] pPageContentOrientationWeight = new byte[pb.getWidth()][pb.getHeight()];
		
		//	assess page figure orientation
		for (int f = 0; f < pData.figures.length; f++) {
			
			//	convert bounds, as PDF Y coordinate is bottom-up, whereas Java, JavaScript, etc. Y coordinate is top-down
			BoundingBox fb = getBoundingBox(pData.figures[f].bounds, pData.pdfPageContentBox, 1, pData.rotate);
			
			//	test for vertical figures
//			if ((pData.figures[f].rotation < -(Math.PI / 4)) && (pData.figures[f].rotation > -((Math.PI * 3) / 4))) {
//				 if (pData.figures[f].upsideDown || pData.figures[f].rightSideLeft)
//					 continue; //  ignore figures that have both rotation and flips, as these are results of funny transformations
//				 fillPageContentObjectBound(pPageContentOrientation, fb, ((byte) PWord.BOTTOM_UP_FONT_DIRECTION));
//			}
			if ((pData.figures[f].transform.getRotation() < -(Math.PI / 4)) && (pData.figures[f].transform.getRotation() > -((Math.PI * 3) / 4))) {
				 if (pData.figures[f].transform.containsInversion())
					 continue; //  ignore figures that have both rotation and flips, as these are results of funny transformations
				 fillPageContentObjectBound(pPageContentOrientation, fb, ((byte) PWord.BOTTOM_UP_FONT_DIRECTION));
			}
			else if ((pData.figures[f].transform.getRotation() > (Math.PI / 4)) && (pData.figures[f].transform.getRotation() < ((Math.PI * 3) / 4))) {
				 if (pData.figures[f].transform.containsInversion())
					 continue; //  ignore figures that have both rotation and flips, as these are results of funny transformations
				fillPageContentObjectBound(pPageContentOrientation, fb, ((byte) PWord.TOP_DOWN_FONT_DIRECTION));
			}
			else fillPageContentObjectBound(pPageContentOrientation, fb, ((byte) PWord.LEFT_RIGHT_FONT_DIRECTION));
			fillPageContentObjectBound(pPageContentOrientationWeight, fb, ((byte) (Byte.MAX_VALUE / 2)));
//			System.out.println("Marked figure at " + fb);
		}
		
		//	assess page word orientation (after figures to make sure word orientation prevails ... it's more definitive, after all)
		for (int w = 0; w < pData.words.length; w++) {
			if (pData.words[w].fontDirection == PWord.UPSIDE_DOWN_FONT_DIRECTION)
				continue; // upside-down words appear mostly as parts of otherwise flipped blocks, let's not let them get in the way
			
			//	convert bounds, as PDF Y coordinate is bottom-up, whereas Java, JavaScript, etc. Y coordinate is top-down
			BoundingBox wb = getBoundingBox(pData.words[w].bounds, pData.pdfPageContentBox, 1, pData.rotate);
			fillPageContentObjectBound(pPageContentOrientation, wb, ((byte) pData.words[w].fontDirection));
			fillPageContentObjectBound(pPageContentOrientationWeight, wb, Byte.MAX_VALUE);
//			System.out.println("Marked word at " + wb);
		}
		
		//	flood all still-white pixels with surrounding orientation (also record round to use for weighting above)
		System.out.println("Computing overall page content orientation via flooding ...");
		int round = 0;
		for (boolean blankPixelsRemaining = true; blankPixelsRemaining;) {
			blankPixelsRemaining = false;
			int blankPixelCount = 0;
			round++;
//			System.out.println(" - round " + round);
			
			//	fill pixels adjacent to filled ones, but mark them as just filled to prevent leftward or downward steamrolling
			for (int c = 0; c < pPageContentOrientation.length; c++)
				for (int r = 0; r < pPageContentOrientation[c].length; r++) {
					if (pPageContentOrientation[c][r] != Byte.MIN_VALUE)
						continue;
					if ((c != 0) && (pPageContentOrientation[c-1][r] >= PWord.UPSIDE_DOWN_FONT_DIRECTION)) {
						pPageContentOrientation[c][r] = ((byte) (pPageContentOrientation[c-1][r] - 16));
						pPageContentOrientationWeight[c][r] = ((byte) Math.max(0, (pPageContentOrientationWeight[c-1][r] - 1)));
					}
					else if ((r != 0) && (pPageContentOrientation[c][r-1] >= PWord.UPSIDE_DOWN_FONT_DIRECTION)) {
						pPageContentOrientation[c][r] = ((byte) (pPageContentOrientation[c][r-1] - 16));
						pPageContentOrientationWeight[c][r] = ((byte) Math.max(0, (pPageContentOrientationWeight[c][r-1] - 1)));
					}
					else if (((c + 1) != pPageContentOrientation.length) && (pPageContentOrientation[c+1][r] >= PWord.UPSIDE_DOWN_FONT_DIRECTION)) {
						pPageContentOrientation[c][r] = ((byte) (pPageContentOrientation[c+1][r] - 16));
						pPageContentOrientationWeight[c][r] = ((byte) Math.max(0, (pPageContentOrientationWeight[c+1][r] - 1)));
					}
					else if (((r + 1) != pPageContentOrientation[c].length) && (pPageContentOrientation[c][r+1] >= PWord.UPSIDE_DOWN_FONT_DIRECTION)) {
						pPageContentOrientation[c][r] = ((byte) (pPageContentOrientation[c][r+1] - 16));
						pPageContentOrientationWeight[c][r] = ((byte) Math.max(0, (pPageContentOrientationWeight[c][r+1] - 1)));
					}
					else {
						blankPixelCount++;
						blankPixelsRemaining = true;
					}
				}
//			System.out.println("   ==> " + blankPixelCount + " blank pixels remaining");
			
			//	un-mark just-filled pixels
			for (int c = 0; c < pPageContentOrientation.length; c++)
				for (int r = 0; r < pPageContentOrientation[c].length; r++) {
					if (pPageContentOrientation[c][r] == Byte.MIN_VALUE)
						continue;
					if (pPageContentOrientation[c][r] < PWord.UPSIDE_DOWN_FONT_DIRECTION)
						pPageContentOrientation[c][r] += 16;
				}
		}
		System.out.println("Page content orientation flooded in " + round + " rounds");
		
		//	display result to make sure
		if (PdfExtractorTest.aimAtPage != -1) {
			BufferedImage pcoBi = new BufferedImage(pPageContentOrientation.length, pPageContentOrientation[0].length, BufferedImage.TYPE_INT_ARGB);
			for (int c = 0; c < pPageContentOrientation.length; c++)
				for (int r = 0; r < pPageContentOrientation[c].length; r++) {
					int alphaRed = ((127 - pPageContentOrientationWeight[c][r]) << 17);
					int alphaGreen = ((127 - pPageContentOrientationWeight[c][r]) << 9);
					int alphaBlue = ((127 - pPageContentOrientationWeight[c][r]) << 1);
					if (pPageContentOrientation[c][r] == PWord.UPSIDE_DOWN_FONT_DIRECTION)
						pcoBi.setRGB(c, r, (0xFF000000 | alphaRed | alphaGreen | 0x000000FF)); // ==> blue
					else if (pPageContentOrientation[c][r] == PWord.TOP_DOWN_FONT_DIRECTION)
						pcoBi.setRGB(c, r, (0xFF000000 | alphaRed | 0x0000FF00 | alphaBlue)); // ==> green
					else if (pPageContentOrientation[c][r] == PWord.LEFT_RIGHT_FONT_DIRECTION)
						pcoBi.setRGB(c, r, (0xFF000000 | 0x00FFFF00 | alphaBlue)); // ==> yellow
					else if (pPageContentOrientation[c][r] == PWord.BOTTOM_UP_FONT_DIRECTION)
						pcoBi.setRGB(c, r, (0xFF000000 | 0x00FF0000 | alphaGreen | alphaBlue)); // ==> red
					else pcoBi.setRGB(c, r, (0xFFFFFFFF));
				}
			ImageDisplayDialog gdd = new ImageDisplayDialog("Page Content Orientation");
			gdd.addImage(pcoBi, "All");
			gdd.setSize(600, 800);
			gdd.setLocationRelativeTo(null);
			gdd.setVisible(true);
		}
		
		//	finally ...
		return new PPageContentOrientation(pPageContentOrientation, pPageContentOrientationWeight);
	}
	
	private static void fillPageContentObjectBound(byte[][] pPageContentOrientation, BoundingBox bounds, byte fill) {
		for (int c = Math.max(0, bounds.left); c < Math.min(pPageContentOrientation.length, bounds.right); c++) {
			for (int r = Math.max(0, bounds.top); r < Math.min(pPageContentOrientation[c].length, bounds.bottom); r++)
				pPageContentOrientation[c][r] = fill;
		}
	}
	
	private static int getPageContentObjectOrientation(Rectangle2D coBounds, Rectangle2D.Float pageBounds, int pageRotate, PPageContentOrientation pPageContentOrientation) {
		return getPageContentObjectOrientation(coBounds, pageBounds, pageRotate, pPageContentOrientation, 0);
	}
	
	private static int getPageContentObjectOrientation(Rectangle2D coBounds, Rectangle2D.Float pageBounds, int pageRotate, PPageContentOrientation pPageContentOrientation, double minCoOrientationPercentage) {
		
		//	convert bounds, as PDF Y coordinate is bottom-up, whereas Java, JavaScript, etc. Y coordinate is top-down
		BoundingBox coBox = getBoundingBox(coBounds, pageBounds, 1, pageRotate);
		
		//	count out orientation in content object bounds
		CountingSet coOrientations = new CountingSet(new TreeMap());
		int coSize = 0;
		for (int c = Math.max(0, coBox.left); c < Math.min(pPageContentOrientation.direction.length, Math.max(coBox.right, (coBox.left+1))); c++)
			for (int r = Math.max(0, coBox.top); r < Math.min(pPageContentOrientation.direction[c].length, Math.max(coBox.bottom, (coBox.top+1))); r++) {
//				coOrientations.add(new Integer(pPageContentOrientation.direction[c][r]), pPageContentOrientation.weight[c][r]);
				coOrientations.add(new Integer(pPageContentOrientation.direction[c][r]), ((pPageContentOrientation.weight[c][r] * pPageContentOrientation.weight[c][r]) / 256)); // division makes sure to prevent int overflow
				coSize++;
			}
		
		//	catch trivial cases first
		if (coOrientations.isEmpty())
			return PWord.LEFT_RIGHT_FONT_DIRECTION;
		else if (coOrientations.size() == 1)
			return ((Integer) coOrientations.first()).intValue();
		
		//	return predominant orientation
		System.out.println("Content orientation for " + coBounds + ":");
		int coOrientation = PWord.LEFT_RIGHT_FONT_DIRECTION;
		int coOrientationSupport = 0;
		for (Iterator coit = coOrientations.iterator(); coit.hasNext();) {
			Integer coo = ((Integer) coit.next());
			int cooSupport = coOrientations.getCount(coo);
			if (cooSupport > coOrientationSupport) {
				coOrientation = coo.intValue();
				coOrientationSupport = cooSupport;
			}
			System.out.println(" - " + coo + ": " + cooSupport);
		}
		return ((coOrientationSupport < (coSize * minCoOrientationPercentage)) ? PWord.LEFT_RIGHT_FONT_DIRECTION : coOrientation);
	}
	
//	private static BoundingBox getFlippedContentPageBounds(BoundingBox pageBox, Rectangle2D.Float pageBounds, BoundingBox flipContentBox, PPageData pData, DefaultingMap flippedObjects, int fbWordFontDirection, float magnification) {
//		if (PdfExtractorTest.aimAtPage != -1)
//			System.out.println("Flipping block " + flipContentBox);
//		//	TODO might even be wider than square if we only flip one of two columns !!!
//		
//		//	compute minimum page margin in IM space
//		int pageLeft = 0;
//		int pageRight = Math.round(pageBounds.width * magnification);
//		int pageTop = 0;
//		int pageBottom = Math.round(pageBounds.height * magnification);
//		int minPageMargin = Integer.MAX_VALUE;
//		int maxNonFlipRight = Integer.MIN_VALUE;
//		if (PdfExtractorTest.aimAtPage != -1)
//			System.out.println(" - computing page margin and flip block position");
//		for (int w = 0; w < pData.words.length; w++) {
//			Rectangle2D wb = ((PWord) flippedObjects.get(pData.words[w])).bounds;
//			if (PdfExtractorTest.aimAtPage != -1)
//				System.out.println("   - word" + (((flippedObjects != null) && flippedObjects.containsKey(pData.words[w])) ? " (flipped)" : "") + " '" + pData.words[w].str + "' at " + wb);
//			
//			//	convert bounds, as PDF Y coordinate is bottom-up, whereas Java, JavaScript, etc. Y coordinate is top-down
//			int left = Math.round(((float) (wb.getMinX() - pageBounds.getMinX())) * magnification);
//			int right = Math.round(((float) (wb.getMaxX() - pageBounds.getMinX()))  * magnification);
//			int top = Math.round((pageBounds.height - ((float) (wb.getMaxY() - ((2 * pageBounds.getMinY()) - pageBounds.getMaxY())))) * magnification);
//			int bottom = Math.round((pageBounds.height - ((float) (wb.getMinY() - ((2 * pageBounds.getMinY()) - pageBounds.getMaxY())))) * magnification);
//			if (PdfExtractorTest.aimAtPage != -1) {
//				System.out.println("     --> " + left + "-" + right + " x " + top + "-" + bottom);
//				System.out.println("         " + (left - pageLeft) + "/" + (pageRight - right) + " x " + (top - pageTop) + "/" + (pageBottom - bottom));
//			}
//			
//			//	adjust minimum page margin
//			minPageMargin = Math.min(minPageMargin, (left - pageLeft));
//			minPageMargin = Math.min(minPageMargin, (pageRight - right));
//			minPageMargin = Math.min(minPageMargin, (top - pageTop));
//			minPageMargin = Math.min(minPageMargin, (pageBottom - bottom));
//			
//			//	adjust maximum non-flipped right
//			if ((bottom <= flipContentBox.top) || (flipContentBox.bottom <= top))
//				continue; // above or below to-flip area
//			if ((flippedObjects != null) && flippedObjects.containsKey(pData.words[w]))
//				continue; // will flip along
//			if (flipContentBox.left < right)
//				continue; // extending beyond left edge of to-flip area (inside, as to right should not happen)
//			maxNonFlipRight = Math.max(maxNonFlipRight, right);
//		}
//		for (int f = 0; f < pData.figures.length; f++) {
//			Rectangle2D fb = ((PFigure) flippedObjects.get(pData.figures[f])).bounds;
//			if (PdfExtractorTest.aimAtPage != -1)
//				System.out.println("   - figure" + (((flippedObjects != null) && flippedObjects.containsKey(pData.figures[f])) ? " (flipped)" : "") + " at " + fb);
//			
//			//	convert bounds, as PDF Y coordinate is bottom-up, whereas Java, JavaScript, etc. Y coordinate is top-down
//			int left = Math.round(((float) (fb.getMinX() - pageBounds.getMinX())) * magnification);
//			int right = Math.round(((float) (fb.getMaxX() - pageBounds.getMinX()))  * magnification);
//			int top = Math.round((pageBounds.height - ((float) (fb.getMaxY() - ((2 * pageBounds.getMinY()) - pageBounds.getMaxY())))) * magnification);
//			int bottom = Math.round((pageBounds.height - ((float) (fb.getMinY() - ((2 * pageBounds.getMinY()) - pageBounds.getMaxY())))) * magnification);
//			if (PdfExtractorTest.aimAtPage != -1) {
//				System.out.println("     --> " + left + "-" + right + " x " + top + "-" + bottom);
//				System.out.println("         " + (left - pageLeft) + "/" + (pageRight - right) + " x " + (top - pageTop) + "/" + (pageBottom - bottom));
//			}
//			
//			//	adjust minimum page margin
//			minPageMargin = Math.min(minPageMargin, (left - pageLeft));
//			minPageMargin = Math.min(minPageMargin, (pageRight - right));
//			minPageMargin = Math.min(minPageMargin, (top - pageTop));
//			minPageMargin = Math.min(minPageMargin, (pageBottom - bottom));
//			
//			//	adjust maximum non-flipped right
//			if ((bottom <= flipContentBox.top) || (flipContentBox.bottom <= top))
//				continue; // above or below to-flip area
//			if (flipContentBox.left < right)
//				continue; // extending beyond left edge of to-flip area (inside, as to right should not happen)
//			if ((flippedObjects != null) && flippedObjects.containsKey(pData.figures[f]))
//				continue; // will flip along
//			maxNonFlipRight = Math.max(maxNonFlipRight, right);
//		}
//		if (PdfExtractorTest.aimAtPage != -1) {
//			System.out.println(" - page is " + pageLeft + "-" + pageRight + " x " + pageTop + "-" + pageBottom);
//			System.out.println(" - min margin is " + minPageMargin);
//			System.out.println(" - max non-flipped right is " + maxNonFlipRight);
//		}
//		
//		//	compute center point of to-flip block and right shift in IM space
//		int fbCenterX = ((flipContentBox.left + flipContentBox.right) / 2);
//		int fbCenterY = ((flipContentBox.top + flipContentBox.bottom) / 2);
//		if (PdfExtractorTest.aimAtPage != -1)
//			System.out.println(" - flip block center is " + fbCenterX + "," + fbCenterY);
//		int fbFlippedLeft = (fbCenterX - (flipContentBox.getHeight() / 2));
//		fbFlippedLeft = Math.max(fbFlippedLeft, minPageMargin); // maintain left page margin
//		fbFlippedLeft = Math.max(fbFlippedLeft, (maxNonFlipRight + Math.round((72 / 12) * magnification))); // make sure to leave enough space on right to not cut off anything
//		if (PdfExtractorTest.aimAtPage != -1)
//			System.out.println(" - flipped block left will be " + fbFlippedLeft);
//		int fbRightShift = Math.max(0, (minPageMargin - fbFlippedLeft));
//		if (PdfExtractorTest.aimAtPage != -1)
//			System.out.println(" --> right shift is " + fbRightShift);
//		
//		//	create new page image size
//		int fbPageWidth = Math.max(pageBox.getWidth(), (fbFlippedLeft + flipContentBox.getHeight() + minPageMargin));
//		BoundingBox fbPageBox = new BoundingBox(0, fbPageWidth, 0, pageBox.getHeight());
//		return fbPageBox;
//	}
	
	private static BoundingBox flipPageContent(PPageData pData, double pMinPageMargin, HashSet flipWords, HashSet flipFigures, HashSet flipPaths, Map flippedObjects, int flipContentFontDirection, float magnification) {
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println("Flipping block content ...");
		
		//	compute bounds of to-flip block and minimum page margin in PDF space (have to invert Y axis for transformation, as transformation works in Y coordinates increasing top-down)
		double pFbLeft = Integer.MAX_VALUE;
		double pFbRight = Integer.MIN_VALUE;
		double pFbTop = Integer.MAX_VALUE;
		double pFbBottom = Integer.MIN_VALUE;
		double pPageLeft = pData.pdfPageBox.getMinX();
		double pPageRight = pData.pdfPageBox.getMaxX();
		double pPageTop = 0;
		double pPageBottom = pData.pdfPageBox.height;
//		double pMinPageMargin = Integer.MAX_VALUE;
		for (int w = 0; w < pData.words.length; w++) {
			Rectangle2D wb = pData.words[w].bounds;
			if (flipWords.contains(pData.words[w])) {
				pFbLeft = Math.min(pFbLeft, wb.getMinX());
				pFbRight = Math.max(pFbRight, wb.getMaxX());
				pFbTop = Math.min(pFbTop, (pData.pdfPageBox.height - wb.getMaxY()));
				pFbBottom = Math.max(pFbBottom, (pData.pdfPageBox.height - wb.getMinY()));
			}
//			pMinPageMargin = Math.min(pMinPageMargin, catchNegativePageMargin(wb.getMinX() - pPageLeft));
//			pMinPageMargin = Math.min(pMinPageMargin, catchNegativePageMargin(pPageRight - wb.getMaxX()));
//			pMinPageMargin = Math.min(pMinPageMargin, catchNegativePageMargin((pData.pdfPageBox.height - wb.getMaxY())) - pPageTop);
//			pMinPageMargin = Math.min(pMinPageMargin, catchNegativePageMargin(pPageBottom - (pData.pdfPageBox.height - wb.getMinY())));
		}
		for (int f = 0; f < pData.figures.length; f++) {
			Rectangle2D fb = pData.figures[f].bounds;
			if (flipFigures.contains(pData.figures[f])) {
				pFbLeft = Math.min(pFbLeft, fb.getMinX());
				pFbRight = Math.max(pFbRight, fb.getMaxX());
				pFbTop = Math.min(pFbTop, (pData.pdfPageBox.height - fb.getMaxY()));
				pFbBottom = Math.max(pFbBottom, (pData.pdfPageBox.height - fb.getMinY()));
			}
//			pMinPageMargin = Math.min(pMinPageMargin, catchNegativePageMargin(fb.getMinX() - pPageLeft));
//			pMinPageMargin = Math.min(pMinPageMargin, catchNegativePageMargin(pPageRight - fb.getMaxX()));
//			pMinPageMargin = Math.min(pMinPageMargin, catchNegativePageMargin((pData.pdfPageBox.height - fb.getMaxY())) - pPageTop);
//			pMinPageMargin = Math.min(pMinPageMargin, catchNegativePageMargin(pPageBottom - (pData.pdfPageBox.height - fb.getMinY())));
		}
		
		//	compute center point of to-flip block and extent in PDF space (have to invert Y axis for transformation, as transformation works in Y coordinates increasing top-down)
		double pFbCenterX = ((pFbLeft + pFbRight) / 2);
		double pFbCenterY = ((pFbTop + pFbBottom) / 2);
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println(" - flip block center is " + pFbCenterX + "," + pFbCenterY);
		double pFbFlippedLeft = (pFbCenterX - ((pFbBottom - pFbTop) / 2));
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println(" - flipped block left will be " + pFbFlippedLeft);
		double pFbFlippedRight = (pFbCenterX + ((pFbBottom - pFbTop) / 2));
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println(" - flipped block right will be " + pFbFlippedRight);
		double pFbFlippedTop = (pFbCenterY - ((pFbRight - pFbLeft) / 2));
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println(" - flipped block top will be " + pFbFlippedTop);
		double pFbFlippedBottom = (pFbCenterY + ((pFbRight - pFbLeft) / 2));
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println(" - flipped block bottom will be " + pFbFlippedBottom);
		
		//	make sure not to overwrite anything to left of to-flip area
		double pFbMinClearDist = Double.POSITIVE_INFINITY;
		for (int w = 0; w < pData.words.length; w++) {
			Rectangle2D wb = pData.words[w].bounds;
			if (flipWords.contains(pData.words[w]))
				continue; // this one will flip, ignore it
			if ((pData.pdfPageBox.height - wb.getMinY()) < pFbFlippedTop)
				continue; // above flip result area
			if ((pData.pdfPageBox.height - wb.getMaxY()) > pFbFlippedBottom)
				continue; // below flip result area
			System.out.println("   - staying clear of word " + pData.words[w].str + " at " + pData.words[w].bounds);
			pMinPageMargin = Math.max(pMinPageMargin, wb.getMaxX());
			pFbMinClearDist = Math.min(pFbMinClearDist, (pFbLeft - wb.getMaxX()));
		}
		for (int f = 0; f < pData.figures.length; f++) {
			Rectangle2D fb = pData.figures[f].bounds;
			if (flipFigures.contains(pData.figures[f]))
				continue; // this one will flip, ignore it
			if ((pData.pdfPageBox.height - fb.getMinY()) < pFbFlippedTop)
				continue; // above flip result area
			if ((pData.pdfPageBox.height - fb.getMaxY()) > pFbFlippedBottom)
				continue; // below flip result area
			System.out.println("   - staying clear of figure at " + pData.figures[f].bounds);
			pMinPageMargin = Math.max(pMinPageMargin, fb.getMaxX());
			pFbMinClearDist = Math.min(pFbMinClearDist, (pFbLeft - fb.getMaxX()));
		}
		
		if (PdfExtractorTest.aimAtPage != -1) {
			System.out.println(" - PDF page is " + pPageLeft + "-" + pPageRight + " x " + pPageTop + "-" + pPageBottom);
			System.out.println(" - to-flip block is " + pFbLeft + "-" + pFbRight + " x " + pFbTop + "-" + pFbBottom);
			System.out.println(" - min clearance is " + pFbMinClearDist);
			System.out.println(" - min margin is " + pMinPageMargin);
		}
		
		//	compute center point of to-flip block and right shift in PDF space (have to invert Y axis for transformation, as transformation works in Y coordinates increasing top-down)
		double pFbRightShift = Math.max(0, ((pMinPageMargin + ((pFbMinClearDist < pData.pdfPageBox.getWidth()) ? pFbMinClearDist : 0)) - pFbFlippedLeft));
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println(" --> right shift is " + pFbRightShift);
		
		//	adjust page width
		pPageRight = Math.max(pPageRight, (pFbRightShift + (pFbFlippedRight - pFbFlippedLeft) + pMinPageMargin));
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println(" --> adjusted PDF page is " + pPageLeft + "-" + pPageRight + " x " + pPageTop + "-" + pPageBottom);
		
		//	create PDF space transformation for flipping words (have to invert Y axis for transformation, as transformation works in Y coordinates increasing top-down)
		AffineTransform pAt = new AffineTransform();
		pAt.translate((pFbCenterX - ((pFbBottom - pFbTop) / 2)), (pFbCenterY - ((pFbRight - pFbLeft) / 2))); // shift to top-left corner of flipped block
		pAt.translate(pFbRightShift, 0); // add right shift to keep flipped block out of page margin, or content on its left
		if (flipContentFontDirection == PWord.BOTTOM_UP_FONT_DIRECTION)
			pAt.translate((pFbBottom - pFbTop), 0); // compensate for top-left corner of to-flip block being top-right in flipped block
		else if (flipContentFontDirection == PWord.TOP_DOWN_FONT_DIRECTION)
			pAt.translate(0, (pFbRight - pFbLeft)); // compensate for top-left corner of to-flip block being bottom-left in flipped block
		//	TODO_not observe upside-down font direction ==> there won't be upside-down _blocks_ to flip, just individual words
		pAt.rotate(((Math.PI / 2) * ((flipContentFontDirection == PWord.BOTTOM_UP_FONT_DIRECTION) ? 1 : -1)));
		
		//	flip words (have to invert Y axis for transformation, as transformation works in Y coordinates increasing top-down)
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println(" - flipping words:");
		for (int w = 0; w < pData.words.length; w++) {
			if (!flipWords.contains(pData.words[w]))
				continue;
			Rectangle2D wb = pData.words[w].bounds;
			Point2D wblp;
			if ((pData.words[w].fontDirection == PWord.LEFT_RIGHT_FONT_DIRECTION) || (pData.words[w].fontDirection == PWord.UPSIDE_DOWN_FONT_DIRECTION))
				wblp = new Point2D.Float(((float) (wb.getMinX() - pFbLeft)), ((float) ((pData.pdfPageBox.height - pData.words[w].baseline) - pFbTop))); // this one has to be relative to to-flip block to work the same way as the image
			else wblp = new Point2D.Float(((float) (pData.words[w].baseline - pFbLeft)), ((float) ((pData.pdfPageBox.height - wb.getMaxY() + ((pData.words[w].fontDirection == PWord.UPSIDE_DOWN_FONT_DIRECTION) ? (wb.getHeight() * 2) : 0)) - pFbTop))); // this one has to be relative to to-flip block to work the same way as the image
			Point2D wbp = new Point2D.Float(((float) (wb.getMinX() - pFbLeft)), ((float) ((pData.pdfPageBox.height - wb.getMaxY() + ((pData.words[w].fontDirection == PWord.UPSIDE_DOWN_FONT_DIRECTION) ? (wb.getHeight() * 2) : 0)) - pFbTop))); // this one has to be relative to to-flip block to work the same way as the image
			Point2D fWblp = pAt.transform(wblp, null);
			float fWbl = ((float) (((pData.words[w].fontDirection == PWord.LEFT_RIGHT_FONT_DIRECTION) || (pData.words[w].fontDirection == PWord.UPSIDE_DOWN_FONT_DIRECTION)) ? fWblp.getX() : fWblp.getY()));
			Point2D fWbp = pAt.transform(wbp, null);
			Rectangle2D.Float fWb;
			if (flipContentFontDirection == PWord.BOTTOM_UP_FONT_DIRECTION)
				fWb = new Rectangle2D.Float(
						((float) (fWbp.getX() - wb.getHeight())),
						((float) ((pData.pdfPageBox.height - fWbp.getY()) - wb.getWidth())),
						((float) wb.getHeight()),
						((float) wb.getWidth())
					);
			else if (flipContentFontDirection == PWord.TOP_DOWN_FONT_DIRECTION)
				fWb = new Rectangle2D.Float(
						((float) fWbp.getX()),
						((float) ((pData.pdfPageBox.height - fWbp.getY()))),
						((float) wb.getHeight()),
						((float) wb.getWidth())
					);
			//	TODO_not observe upside-down font direction ==> there won't be upside-down _blocks_ to flip, just individual words
			else continue;
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println("   - " + pData.words[w] + " flipped from " + wb + " to " + fWb);
			int fwFontDirection = (pData.words[w].fontDirection - flipContentFontDirection);
			if (fwFontDirection > PWord.BOTTOM_UP_FONT_DIRECTION)
				fwFontDirection -= 4;
			else if (fwFontDirection < PWord.UPSIDE_DOWN_FONT_DIRECTION)
				fwFontDirection += 4;
			PWord pWord = new PWord(pData.words[w].renderOrderNumber, pData.words[w].charCodes, pData.words[w].str, fWbl, fWb, pData.words[w].color, pData.words[w].fontSize, fwFontDirection, pData.words[w].font);
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
			Point2D fbp = new Point2D.Float(((float) (fb.getMinX() - pFbLeft)), ((float) ((pData.pdfPageBox.height - fb.getMaxY()) - pFbTop))); // this one has to be relative to to-flip block to work the same way as the image
			Point2D fFbp = pAt.transform(fbp, null);
			Rectangle2D.Float fFb;
			if (flipContentFontDirection == PWord.BOTTOM_UP_FONT_DIRECTION)
				fFb = new Rectangle2D.Float(
						((float) (fFbp.getX() - fb.getHeight())),
						((float) ((pData.pdfPageBox.height - fFbp.getY()) - fb.getWidth())),
						((float) fb.getHeight()),
						((float) fb.getWidth())
					);
			else if (flipContentFontDirection == PWord.TOP_DOWN_FONT_DIRECTION)
				fFb = new Rectangle2D.Float(
						((float) fFbp.getX()),
						((float) ((pData.pdfPageBox.height - fFbp.getY()))),
						((float) fb.getHeight()),
						((float) fb.getWidth())
					);
			//	TODO_not observe upside-down font direction ==> there won't be upside-down _blocks_ to flip, just individual words
			else continue;
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println("   - " + pData.figures[f] + " flipped from " + fb + " to " + fFb);
			PPath[] fClipPaths = pData.figures[f].clipPaths;
			if (fClipPaths != null) {
				for (int p = 0; p < fClipPaths.length; p++)
					fClipPaths[p] = flipPath(fClipPaths[p], pData.pdfPageBox, pAt, pFbLeft, pFbTop);
			}
			PFigure[] fSoftMasks = pData.figures[f].softMasks;
			if (fSoftMasks != null)
				for (int m = 0; m < fSoftMasks.length; m++) {
					Rectangle2D smFb = fSoftMasks[m].bounds;
					Point2D smFbp = new Point2D.Float(((float) (smFb.getMinX() - pFbLeft)), ((float) ((pData.pdfPageBox.height - smFb.getMaxY()) - pFbTop))); // this one has to be relative to to-flip block to work the same way as the image
					Point2D fSmFbp = pAt.transform(smFbp, null);
					Rectangle2D.Float fSmFb;
					if (flipContentFontDirection == PWord.BOTTOM_UP_FONT_DIRECTION)
						fSmFb = new Rectangle2D.Float(
								((float) (fSmFbp.getX() - smFb.getHeight())),
								((float) ((pData.pdfPageBox.height - fSmFbp.getY()) - smFb.getWidth())),
								((float) smFb.getHeight()),
								((float) smFb.getWidth())
							);
					else if (flipContentFontDirection == PWord.TOP_DOWN_FONT_DIRECTION)
						fSmFb = new Rectangle2D.Float(
								((float) fSmFbp.getX()),
								((float) ((pData.pdfPageBox.height - fSmFbp.getY()))),
								((float) smFb.getHeight()),
								((float) smFb.getWidth())
							);
					//	TODO_not observe upside-down font direction ==> there won't be upside-down _blocks_ to flip, just individual words
					else continue;
					fSoftMasks[m] = new PFigure(fSoftMasks[m].renderOrderNumber, fSoftMasks[m].name, fSmFb, null, null, fSoftMasks[m].refOrData, (fSoftMasks[m].transform.quadrantRotate((flipContentFontDirection == PWord.BOTTOM_UP_FONT_DIRECTION) ? 1 : -1)));
				}
//			PFigure pFigure = new PFigure(pData.figures[f].renderOrderNumber, pData.figures[f].name, fFb, fClipPaths, pData.figures[f].refOrData, (pData.figures[f].rotation + ((flipContentFontDirection == PWord.BOTTOM_UP_FONT_DIRECTION) ? (Math.PI / 2) : -(Math.PI / 2))), pData.figures[f].upsideDown, pData.figures[f].rightSideLeft);
			PFigure pFigure = new PFigure(pData.figures[f].renderOrderNumber, pData.figures[f].name, fFb, fClipPaths, fSoftMasks, pData.figures[f].refOrData, (pData.figures[f].transform.quadrantRotate((flipContentFontDirection == PWord.BOTTOM_UP_FONT_DIRECTION) ? 1 : -1)));
			flippedObjects.put(pFigure, pData.figures[f]);
			pData.figures[f] = pFigure;
		}
		
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println(" - flipping paths:");
		for (int p = 0; p < pData.paths.length; p++) {
			if (!flipPaths.contains(pData.paths[p]))
				continue;
			Rectangle2D pb = pData.paths[p].getBounds();
			Point2D pbp = new Point2D.Float(((float) (pb.getMinX() - pFbLeft)), ((float) ((pData.pdfPageBox.height - pb.getMaxY()) - pFbTop))); // this one has to be relative to to-flip block to work the same way as the image
			Point2D fPbp = pAt.transform(pbp, null);
			Rectangle2D.Float fPb;
			if (flipContentFontDirection == PWord.BOTTOM_UP_FONT_DIRECTION)
				fPb = new Rectangle2D.Float(
						((float) (fPbp.getX() - pb.getHeight())),
						((float) ((pData.pdfPageBox.height - fPbp.getY()) - pb.getWidth())),
						((float) pb.getHeight()),
						((float) pb.getWidth())
					);
			else if (flipContentFontDirection == PWord.TOP_DOWN_FONT_DIRECTION)
				fPb = new Rectangle2D.Float(
						((float) fPbp.getX()),
						((float) ((pData.pdfPageBox.height - fPbp.getY()))),
						((float) pb.getHeight()),
						((float) pb.getWidth())
					);
			//	TODO_not observe upside-down font direction ==> there won't be upside-down _blocks_ to flip, just individual words
			else continue;
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println("   - " + pData.paths[p] + " flipped from " + pb + " to " + fPb);
			PPath pPath = flipPath(pData.paths[p], pData.pdfPageBox, pAt, pFbLeft, pFbTop);
			flippedObjects.put(pPath, pData.paths[p]);
			pData.paths[p] = pPath;
		}
		
		Point2D fbp = new Point2D.Float(0, 0); // this one has to be relative to to-flip block to work the same way as the image
		Point2D fFbp = pAt.transform(fbp, null);
		Rectangle2D.Float fFb;
		if (flipContentFontDirection == PWord.BOTTOM_UP_FONT_DIRECTION)
			fFb = new Rectangle2D.Float(
					((float) (fFbp.getX() - (pFbBottom - pFbTop))),
					((float) ((pData.pdfPageBox.height - fFbp.getY()) - (pFbRight - pFbLeft))),
					((float) (pFbBottom - pFbTop)),
					((float) (pFbRight - pFbLeft))
				);
		else if (flipContentFontDirection == PWord.TOP_DOWN_FONT_DIRECTION)
			fFb = new Rectangle2D.Float(
					((float) fFbp.getX()),
					((float) ((pData.pdfPageBox.height - fFbp.getY()))),
					((float) (pFbBottom - pFbTop)),
					((float) (pFbRight - pFbLeft))
				);
		else return null;
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println(" --> flipped content bounds are " + fFb);
		return getBoundingBox(fFb, pData.pdfPageContentBox, magnification, pData.rotate);
	}
	
	private static PPath flipPath(PPath path, Rectangle2D pgb, AffineTransform pAt, double pFbLeft, double pFbTop) {
		
		//	copy properties
		PPath pPath = new PPath(path.renderOrderNumber);
//		sub paths do this
//		pPath.addPoint(((float) fPb.getMinX()), ((float) fPb.getMinY()));
//		pPath.addPoint(((float) fPb.getMaxX()), ((float) fPb.getMaxY()));
		pPath.strokeColor = path.strokeColor;
		pPath.lineWidth = path.lineWidth;
		pPath.lineCapStyle = path.lineCapStyle;
		pPath.lineJointStyle = path.lineJointStyle;
		pPath.miterLimit = path.miterLimit;
		pPath.dashPattern = path.dashPattern;
		pPath.dashPatternPhase = path.dashPatternPhase;
		pPath.fillColor = path.fillColor;
		pPath.fillEvenOdd = path.fillEvenOdd;
		pPath.blendMode = path.blendMode;
		
		//	flip sub paths
		PSubPath[] pSubPaths = path.getSubPaths();
		for (int s = 0; s < pSubPaths.length; s++) {
			Point2D.Float fSpp = flipPoint(pSubPaths[s].startX, pSubPaths[s].startY, pgb, pAt, pFbLeft, pFbTop);
			PSubPath pSubPath = new PSubPath(pPath, fSpp.x, fSpp.y);
			Shape[] shapes = pSubPaths[s].getShapes();
			for (int h = 0; h < shapes.length; h++) {
				if (shapes[h] instanceof Line2D) {
					Line2D line = ((Line2D) shapes[h]);
					Point2D.Float fLe = flipPoint(((float) line.getX2()), ((float) line.getY2()), pgb, pAt, pFbLeft, pFbTop);
					pSubPath.lineTo(fLe.x, fLe.y);
				}
				else if (shapes[h] instanceof CubicCurve2D) {
					CubicCurve2D curve = ((CubicCurve2D) shapes[h]);
					Point2D.Float fC1 = flipPoint(((float) curve.getCtrlX1()), ((float) curve.getCtrlY1()), pgb, pAt, pFbLeft, pFbTop);
					Point2D.Float fC2 = flipPoint(((float) curve.getCtrlX2()), ((float) curve.getCtrlY2()), pgb, pAt, pFbLeft, pFbTop);
					Point2D.Float fCe = flipPoint(((float) curve.getX2()), ((float) curve.getY2()), pgb, pAt, pFbLeft, pFbTop);
					pSubPath.curveTo(fC1.x, fC1.y, fC2.x, fC2.y, fCe.x, fCe.y);
				}
			}
		}
		
		//	flip any clipping paths
		PPath[] pClipPaths = path.clipPaths;
		if (pClipPaths != null) {
			for (int p = 0; p < pClipPaths.length; p++)
				pClipPaths[p] = flipPath(pClipPaths[p], pgb, pAt, pFbLeft, pFbTop);
		}
//		pPath.clipPaths = pClipPaths;
//		pPath.visibleBounds = PdfParser.getVisibleBounds(pPath.getBounds(), pPath.clipPaths);
		pPath.setClipPaths(pClipPaths);
		
		//	finally ...
		return pPath;
	}
	
	private static double catchNegativePageMargin(double margin) {
		return ((margin <= 0) ? Integer.MAX_VALUE : margin);
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
//		
//		//	compute word box height
//		float fontHeight = (Math.abs(pWord.font.ascent) + Math.abs(pWord.font.descent));
		
		//	prepare font for test rendering
		int fontStyle = Font.PLAIN;
		if (pWord.bold)
			fontStyle = (fontStyle | Font.BOLD);
		if (pWord.italics)
			fontStyle = (fontStyle | Font.ITALIC);
		Font mf = getFont(pWord.font.name, fontStyle, pWord.serif, pWord.monospaced, Math.round(((float) pWord.fontSize) * 1));
		mg.setFont(mf);
		
		//	left-right word
		if (pWord.fontDirection == PWord.LEFT_RIGHT_FONT_DIRECTION) {
			
			//	NO ADJUSTMENT TO JAVA COORDINATES HERE, WE'RE TRANSFORMING ALL WORDS LATER ON
			float left = ((float) wb.getMinX());
			float right = ((float) wb.getMaxX());
			float top = ((float) wb.getMaxY());
			float bottom = ((float) wb.getMinY());
			
			//	compute word baseline from font baseline and word box height
			//float baseline = (top - (((top - bottom) * pWord.font.ascent) / fontHeight));
			
			//	adjust word size and vertical position
			TextLayout wtl = new TextLayout((pWord.str + "IpHq"), mf, mg.getFontRenderContext());
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" - word rendering box around " + (pWord.str + "IpHq") + " is " + wtl.getBounds());
			float boundingBoxY = ((float) wtl.getBounds().getY());
			float boundingBoxHeight = ((float) wtl.getBounds().getHeight());
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" - top is " + top + ", bottom is " + bottom + ", baseline is " + pWord.baseline + ", ascent is " + wtl.getAscent() + ", descent is " + wtl.getDescent());
			
			//	if computed bounding box smaller (lower top, higher bottom) than one from PDF, shrink it
			float adjustedTop = (pWord.baseline - boundingBoxY);
			float adjustedBottom = (adjustedTop - boundingBoxHeight);
			if (PdfExtractorTest.aimAtPage != -1) {
				System.out.println(" - word box y is " + boundingBoxY + ", word box height is " + boundingBoxHeight);
				System.out.println(" - adjusted top is " + adjustedTop + ", adjusted bottom is " + adjustedBottom);
			}
			if (((adjustedTop - adjustedBottom) < (top - bottom)) && (((top - adjustedTop) > 0.25) || ((adjustedBottom - bottom) > 0.25))) {
				Rectangle2D.Float pwBox = new Rectangle2D.Float(left, adjustedBottom, (right - left), (adjustedTop - adjustedBottom));
				PWord spWord = new PWord(pWord.renderOrderNumber, pWord.charCodes, pWord.str, pWord.baseline, pwBox, pWord.color, pWord.fontSize, pWord.fontDirection, pWord.font);
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
			//float baseline = (top + (((bottom - top) * pWord.font.ascent) / fontHeight));
			
			//	adjust word size and vertical position
			TextLayout wtl = new TextLayout((pWord.str + "IpHq"), mf, mg.getFontRenderContext());
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" - word rendering box is " + wtl.getBounds());
			float boundingBoxX = -((float) wtl.getBounds().getY());
			float boundingBoxHeight = ((float) wtl.getBounds().getHeight());
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" - top is " + top + ", bottom is " + bottom + ", baseline is " + pWord.baseline + ", ascent is " + wtl.getAscent() + ", descent is " + wtl.getDescent());
			
			//	if computed bounding box smaller (lower top, higher bottom) than one from PDF, shrink it
			float adjustedTop = (pWord.baseline - boundingBoxX);
			float adjustedBottom = (adjustedTop + boundingBoxHeight);
			if (PdfExtractorTest.aimAtPage != -1) {
				System.out.println(" - word box y is " + boundingBoxX + ", word box height is " + boundingBoxHeight);
				System.out.println(" - adjusted top is " + adjustedTop + ", adjusted bottom is " + adjustedBottom);
			}
			if (((adjustedTop - adjustedBottom) < (top - bottom)) && (((adjustedTop - top) > 0.25) || ((bottom - adjustedBottom) > 0.25))) {
				Rectangle2D.Float pwBox = new Rectangle2D.Float(adjustedTop, left, (adjustedBottom - adjustedTop), (right - left));
				PWord spWord = new PWord(pWord.renderOrderNumber, pWord.charCodes, pWord.str, pWord.baseline, pwBox, pWord.color, pWord.fontSize, pWord.fontDirection, pWord.font);
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
			//float baseline = (top - (((top - bottom) * pWord.font.ascent) / fontHeight));
			
			//	adjust word size and vertical position
			TextLayout wtl = new TextLayout((pWord.str + "IpHq"), mf, mg.getFontRenderContext());
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" - word rendering box is " + wtl.getBounds());
			float boundingBoxX = ((float) wtl.getBounds().getY());
			float boundingBoxHeight = ((float) wtl.getBounds().getHeight());
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" - top is " + top + ", bottom is " + bottom + ", baseline is " + pWord.baseline + ", ascent is " + wtl.getAscent() + ", descent is " + wtl.getDescent());
			
			//	if computed bounding box smaller (lower top, higher bottom) than one from PDF, shrink it
			float adjustedTop = (pWord.baseline - boundingBoxX);
			float adjustedBottom = (adjustedTop - boundingBoxHeight);
			if (PdfExtractorTest.aimAtPage != -1) {
				System.out.println(" - word box y is " + boundingBoxX + ", word box height is " + boundingBoxHeight);
				System.out.println(" - adjusted top is " + adjustedTop + ", adjusted bottom is " + adjustedBottom);
			}
			if (((adjustedTop - adjustedBottom) < (top - bottom)) && (((top - adjustedTop) > 0.25) || ((adjustedBottom - bottom) > 0.25))) {
				Rectangle2D.Float pwBox = new Rectangle2D.Float(adjustedBottom, right, (adjustedTop - adjustedBottom), (left - right));
				PWord spWord = new PWord(pWord.renderOrderNumber, pWord.charCodes, pWord.str, pWord.baseline, pwBox, pWord.color, pWord.fontSize, pWord.fontDirection, pWord.font);
				spWord.joinWithNext = pWord.joinWithNext;
				pWord = spWord;
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println(" ==> adjusted bounding box to " + pWord.bounds);
			}
		}
		
		//	upside-down word
		else if (pWord.fontDirection == PWord.UPSIDE_DOWN_FONT_DIRECTION) {
			
			//	NO ADJUSTMENT TO JAVA COORDINATES HERE, WE'RE TRANSFORMING ALL WORDS LATER ON
			float left = ((float) wb.getMaxX());
			float right = ((float) wb.getMinX());
			float top = ((float) wb.getMinY());
			float bottom = ((float) wb.getMaxY());
			
			//	compute word baseline from font baseline and word box height
			//float baseline = (top + (((bottom - top) * pWord.font.ascent) / fontHeight));
			
			//	adjust word size and vertical position
			TextLayout wtl = new TextLayout((pWord.str + "IpHq"), mf, mg.getFontRenderContext());
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" - word rendering box is " + wtl.getBounds());
			float boundingBoxY = ((float) wtl.getBounds().getY());
			float boundingBoxHeight = ((float) wtl.getBounds().getHeight());
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" - top is " + top + ", bottom is " + bottom + ", baseline is " + pWord.baseline + ", ascent is " + wtl.getAscent() + ", descent is " + wtl.getDescent());
			
			//	if computed bounding box smaller (lower top, higher bottom) than one from PDF, shrink it
			float adjustedTop = (pWord.baseline - boundingBoxY);
			float adjustedBottom = (adjustedTop + boundingBoxHeight);
			if (PdfExtractorTest.aimAtPage != -1) {
				System.out.println(" - word box y is " + boundingBoxY + ", word box height is " + boundingBoxHeight);
				System.out.println(" - adjusted top is " + adjustedTop + ", adjusted bottom is " + adjustedBottom);
			}
			if (((adjustedTop - adjustedBottom) < (top - bottom)) && (((adjustedTop - top) > 0.25) || ((bottom - adjustedBottom) > 0.25))) {
				Rectangle2D.Float pwBox = new Rectangle2D.Float(right, adjustedTop, (left - right), (adjustedBottom - adjustedTop));
				PWord spWord = new PWord(pWord.renderOrderNumber, pWord.charCodes, pWord.str, pWord.baseline, pwBox, pWord.color, pWord.fontSize, pWord.fontDirection, pWord.font);
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
	private static Font monospacedFont = new Font((USE_FREE_FONTS ? "FreeMono" : "Monospaced"), Font.PLAIN, 1);
	
	private static Map fontCache = Collections.synchronizedMap(new HashMap(5));
	private static Font getFont(String name, int style, boolean serif, boolean monospaced, int size) {
		String fontKey = (style + " " + (serif ? "serif" : "sans") + " " + size);
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println("Getting font " + fontKey);
		Font font = ((Font) fontCache.get(fontKey));
		if (font != null) {
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println("==> cache hit");
			return font;
		}
		font = (monospaced ? monospacedFont : (serif ? serifFont : sansFont)).deriveFont(style, size);
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
	
	private static final boolean DEBUG_PAGE_STRUCTURE_WORDS_ONLY = false;
	private static ImWord analyzeRegionStructure(PPageData pData, int pageIndex, ImPage page, BoundingBox pageContentBox, int pageImageDpi, float magnification, ImDocumentStyle docLayout, Graphics textBox, HashMap pageWordsByBoxes, Set pWatermarkWords, Set pInTextBoxWordBounds, Set pageStructImageRegions, ProgressMonitor spm) {
		
		/* TODOne if textBox is null, render (partial) page image, including
		 * - render words not included in any textbox, graphics, or image (test via containment in sets)
		 * - render any graphics not flagged as page decoration (including text boxes, we want them in the way)
		 * - do not render any images (we don't need those)
		 */
		
		/* TODOne if textBox is not null, render (partial) page image:
		 * - render words lying inside text box
		 * - do not render any graphics (we want the inner structure of that text box)
		 * - do not render any images (we don't need those)
		 */
		
		//	conflate words, figures, and graphics (we do need figures, as they can (a) connect graphics blocks and (b) influence text blocking order)
		ArrayList pageObjects = new ArrayList();
		pageObjects.addAll(Arrays.asList(pData.words));
		pageObjects.addAll(Arrays.asList(pData.figures));
		pageObjects.addAll(Arrays.asList(pData.paths));
		
		//	order by increasing render order number
		Collections.sort(pageObjects);
		
		//	collect words actually rendered
		HashMap wordsByBoxes = new HashMap();
		
		//	initialize page structure image (grayscale is enough for structure detection)
//		BufferedImage pageImage = getPageStructureImage(pData, page, pageObjects, magnification, textBox, pageWordsByBoxes, wordsByBoxes, pWatermarkWords, pInTextBoxWordBounds, spm);
		BufferedImage pageImage = new BufferedImage(page.bounds.getWidth(), page.bounds.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
		Graphics2D piGr = pageImage.createGraphics();
		piGr.setColor(Color.WHITE);
		piGr.fillRect(0, 0, pageImage.getWidth(), pageImage.getHeight());
		piGr.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		
		//	set up shrinking figure images
		//byte[][] pageImageBrightness = null;
		
		//	render them all in a single loop (grayscale is enough for structure detection)
		for (int o = 0; o < pageObjects.size(); o++) {
			PObject po = ((PObject) pageObjects.get(o));
			
			//	render word
			if (po instanceof PWord) {
				PWord pw = ((PWord) po);
				
				//	skip over water marks (all too faint words that do not lie in image or graphics)
				if ((pWatermarkWords != null) && pWatermarkWords.contains(pw))
					continue;
				
				//	filter words inside graphics for word-ony testing
				if (DEBUG_PAGE_STRUCTURE_WORDS_ONLY) {
					for (int p = 0; p < pData.paths.length; p++)
						if (pData.paths[p].getBounds().intersects(pw.bounds)) {
							pw = null;
							break;
						}
					if (pw == null)
						continue;
				}
				
				//	convert bounds, as PDF Y coordinate is bottom-up, whereas Java, JavaScript, etc. Y coordinate is top-down
				BoundingBox wb = getBoundingBox(pw.bounds, pData.pdfPageContentBox, magnification, pData.rotate);
				if (wb.getWidth() < 4)
					wb = new BoundingBox(wb.left, (wb.left + 4), wb.top, wb.bottom); // need to ensure minimum width here to ensure lookup match
				
				//	for general page (textbox is null), omit any words inside graphics
				if ((textBox == null) && (pInTextBoxWordBounds != null) && pInTextBoxWordBounds.contains(wb))
					continue;
				
				//	for text box content (text box not null), omit any words outside text box
				else if ((textBox != null) && !textBox.getBounds().overlaps(wb))
					continue;
				
				//	remember word in focus
				ImWord imw = ((ImWord) pageWordsByBoxes.remove(wb));
				if (imw != null) {
					wordsByBoxes.put(wb, imw);
					System.out.println("Adding structure layout word '" + imw.getString() + "' at " + wb);
				}
				else System.out.println("Strange structure layout word '" + pw.str + "' at " + wb);
				
				//	fill word as solid black rectangle (actual string and orientation don't matter here)
				//	TODO ignore anything outside page content area !!!
				//	TODO unless we have flipped content !!!
				if ((imw != null) && pageContentBox.includes(imw.bounds, true) && !ImWord.TEXT_STREAM_TYPE_ARTIFACT.equals(imw.getTextStreamType())) /* missing word was marked as artifact above */ {
					piGr.setColor(Color.BLACK);
					piGr.fillRect(wb.left, wb.top, wb.getWidth(), wb.getHeight());
				}
			}
			
			//	omit figures altogether
			//	TODO_not render figure (we do need figures, as they can (a) connect graphics blocks and (b) influence text blocking order)
			//	==> we re-assemble multi-figure graphics blocks later in merging routine, and also pull in any label text
			else if (po instanceof PFigure) {
//				if (textBox != null)
//					continue; // no figures needed for text box content
//				PFigure pf = ((PFigure) po);
//				BoundingBox fb = getBoundingBox(pf.visibleBounds, pData.pdfPageContentBox, magnification, pData.rotate);
//				if (pageImageBrightness == null) {
//					PageImage pi = page.getImage();
//					AnalysisImage api = Imaging.wrapImage(pi.image, null);
//					pageImageBrightness = api.getBrightness();
//				}
//				fb = shrinkToContent(fb, pageImageBrightness);
//				if (fb == null)
//					continue;
//				piGr.setColor(Color.GRAY);
//				piGr.fillRect(fb.left, fb.top, fb.getWidth(), fb.getHeight());
			}
			
			//	render path (safe for what we skipped above, i.e., out of page paths, bright on white paths, etc.)
			else if (po instanceof PPath) {
				if (DEBUG_PAGE_STRUCTURE_WORDS_ONLY)
					continue; // filter graphics for word-only testing
				if (textBox != null)
					continue; // no graphics needed for text box content
				PPath pp = ((PPath) po);
				if (pp.isPageDecoration())
					continue; // omit page decoration
				BoundingBox gb = getBoundingBox(pp.getBounds(), pData.pdfPageContentBox, magnification, pData.rotate);
				if (!pageContentBox.includes(gb, true))
					continue; // outside page content area (might have failed to flag as decoration, or dodged cleanup)
				Shape preClip = piGr.getClip();
				AffineTransform preAt = piGr.getTransform();
				Composite preComp = piGr.getComposite();
				Color preColor = piGr.getColor();
				Stroke preStroke = piGr.getStroke();
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println("Adding path at " + gb);
				
				//	observe clipping (we need this despite all sanitization, as some paths draw all over and show only partially)
				if (pp.clipPaths != null) {
					BoundingBox cb = getBoundingBox(pp.visibleBounds, pData.pdfPageContentBox, magnification, pData.rotate);
					piGr.clipRect(cb.left, cb.top, cb.getWidth(), cb.getHeight());
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println(" - clipped to " + cb + " in page");
				}
				
				//	adjust graphics
				piGr.translate((-pData.pdfPageContentBox.x * magnification), (pData.pdfPageContentBox.y * magnification));
				piGr.scale(magnification, -magnification);
				if (pp.blendMode != null)
					piGr.setComposite(pp.blendMode);
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
					//	TODO consider minimum width of lines (very narrow or low bounds) to prevent them from falling victim to rasterization
					//	TODO do the same on page images
					//	TODO maybe also make that dependent on lightness of stroke and fill color (lighter ==> wider)
					stroke = new BasicStroke(pp.lineWidth, pp.lineCapStyle, pp.lineJointStyle, ((pp.miterLimit < 1) ? 1.0f : pp.miterLimit), dashPattern, pp.dashPatternPhase);
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println(" - dash pattern added (" + pp.lineWidth + ", " + pp.lineCapStyle + ", " + pp.lineJointStyle + ", " + ((pp.miterLimit < 1) ? 1.0f : pp.miterLimit) + ", " + dashPattern + ", " + pp.dashPatternPhase + ")");
				}
				
				//	render paths
				Path2D path = new Path2D.Float();
				PSubPath[] psps = pp.getSubPaths();
				for (int sp = 0; sp < psps.length; sp++) {
					Path2D subPath = psps[sp].getPath();
					path.append(subPath, false);
				}
				if (pp.fillColor != null) {
					piGr.setColor(pp.fillColor);
					piGr.fill(path);
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println(" - filled in " + pp.fillColor);
				}
				if (pp.strokeColor != null) {
					piGr.setColor(pp.strokeColor);
					piGr.setStroke(stroke);
					piGr.draw(path);
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println(" - stroked in " + pp.strokeColor);
				}
				
				//	reset graphics
				piGr.setStroke(preStroke);
				piGr.setColor(preColor);
				piGr.setComposite(preComp);
				piGr.setTransform(preAt);
				piGr.setClip(preClip);
			}
		}
		
		//	clean up
		piGr.dispose();
		if (PdfExtractorTest.aimAtPage != -1) {
			ImageDisplayDialog idd = new ImageDisplayDialog("Structuring Image for Page " + page.pageId + ((textBox == null) ? "" : (", text box at " + textBox.getBounds())));
			idd.addImage(pageImage, "_");
			idd.setSize(600, 800);
			idd.setLocationRelativeTo(null);
			idd.setVisible(true);
		}
		
		//	get page content area layout hint (defaulting to whole page bounds), as well as number of columns (only for main text, though, as text boxes may differ)
		BoundingBox contentArea = docLayout.getBoxProperty("contentArea", page.bounds, pageImageDpi);
		int columnCount = ((textBox == null) ? docLayout.getIntProperty("columnCount", -1) : -1);
		
		//	wrap image for structure analysis
		AnalysisImage api = Imaging.wrapImage(pageImage, null);
		Imaging.whitenWhite(api);
		
		//	get column and block margin layout hints (defaulting to kind of universal ball park figures)
		int minBlockMargin = docLayout.getIntProperty("minBlockMargin", (pageImageDpi / 10), pageImageDpi);
		int minColumnMargin = ((columnCount == 1) ? (page.bounds.right - page.bounds.left) : docLayout.getIntProperty("minColumnMargin", (pageImageDpi / 10), pageImageDpi));
		
		//	get (or compute) column areas to correct erroneous column splits (only for main text, though, as text boxes may differ)
		BoundingBox[] columnAreas;
		if (textBox == null) {
			columnAreas = docLayout.getBoxListProperty("columnAreas", null, pageImageDpi);
			columnAreas = docLayout.getBoxListProperty("page.columnAreas", columnAreas, pageImageDpi);
//			if ((page.pageId % 2) == 0)
			if ((pageIndex % 2) == 0)
				columnAreas = docLayout.getBoxListProperty("page.odd.columnAreas", columnAreas, pageImageDpi);
			else columnAreas = docLayout.getBoxListProperty("page.even.columnAreas", columnAreas, pageImageDpi);
//			if (page.pageId == 0)
			if (pageIndex == 0)
				columnAreas = docLayout.getBoxListProperty("page.first.columnAreas", columnAreas, pageImageDpi);
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
				else if ((columnCount != -1) && (contentArea != page.bounds)) {
					columnAreas = new BoundingBox[columnCount];
					for (int c = 0; c < columnCount; c++)
						columnAreas[c] = new BoundingBox((contentArea.left + (((contentArea.right - contentArea.left) * c) / columnCount)), (contentArea.left + (((contentArea.right - contentArea.left) * (c + 1)) / columnCount)), contentArea.top, contentArea.bottom);
				}
			}
		}
		else columnAreas = null;
		
		//	get minimum column width to prevent column splits resulting in too narrow columns
		int minColumnWidth = docLayout.getIntProperty("minColumnWidth", -1, pageImageDpi);
		
		//	obtain visual page structure
		Region pageRootRegion = PageImageAnalysis.getPageRegion(api, pageImageDpi, minColumnMargin, minBlockMargin, columnAreas, minColumnWidth, false, spm);
		
		//	add page content to document
		return addRegionStructure(page, null, pageRootRegion, wordsByBoxes, pageStructImageRegions, spm);
	}
	
	private static ImWord addRegionStructure(ImPage page, ImWord lastWord, Region rootRegion, Map wordsByBoxes, Set pageStructImageRegions, ProgressMonitor pm) {
		
		//	mark region
		ImRegion region = new ImRegion(page, rootRegion.getBoundingBox());
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println("Adding structure to " + region.bounds);
		
		//	image block ==> set type and we're done
		if (rootRegion.isImage()) {
			region.setType(ImRegion.IMAGE_TYPE);
			if (pageStructImageRegions != null)
				pageStructImageRegions.add(region);
			return lastWord;
		}
		
		//	set type
		region.setType((rootRegion.isColumn() && rootRegion.areChildrenAtomic()) ? COLUMN_ANNOTATION_TYPE : REGION_ANNOTATION_TYPE);
		
		//	atomic region ==> block, do further analysis
		if (rootRegion.isAtomic()) {
			
			//	analyze block structure
			PageImageAnalysis.getBlockStructure(rootRegion, page.getImageDPI(), ((BoundingBox[]) wordsByBoxes.keySet().toArray(new BoundingBox[wordsByBoxes.size()])), pm);
			
			//	get block bounds
			Block theBlock = rootRegion.getBlock();
			
			//	mark block
			ImRegion block = new ImRegion(page, theBlock.getBoundingBox());
			block.setType(theBlock.isTable() ? TABLE_ANNOTATION_TYPE : BLOCK_ANNOTATION_TYPE);
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" - added atomic block " + block.bounds);
			
			//	append block content
			return addTextBlockStructure(page, lastWord, theBlock, wordsByBoxes, pm);
		}
		
		//	non-atomic region ==> recurse to child regions
		for (int s = 0; s < rootRegion.getSubRegionCount(); s++)
			lastWord = addRegionStructure(page, lastWord, rootRegion.getSubRegion(s), wordsByBoxes, pageStructImageRegions, pm);
		
		//	finally ...
		return lastWord;
	}
	
	private static ImWord addTextBlockStructure(ImPage page, ImWord lastWord, Block theBlock, Map pWordsByBoxes, ProgressMonitor pm) {
		
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
					lastWord = addLineStructure(page, lastWord, cells[c].bounds, lines, pWordsByBoxes, pm);
				}
			}
		}
		
		//	regular text lines
		else {
			Line[] lines = theBlock.getLines();
			lastWord = addLineStructure(page, lastWord, theBlock.bounds, lines, pWordsByBoxes, pm);
		}
		
		//	finally ...
		return lastWord;
	}
	
	private static ImWord addLineStructure(ImPage page, ImWord lastWord, ImagePartRectangle blockBounds, Line[] lines, Map wordsByBoxes, ProgressMonitor pm) {
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
						fontSizeSum += lWord.getFontSize();
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
					if (lWord != null)
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
	
	private static ImWord addTextStreamStructure(ImPage page, ImWord lastWord, ImWord pageFirstWord) {
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
		ImWord textStreamHead = ((pageFirstWord == null) ? findMainTextStreamHead(page) : pageFirstWord);
		if (textStreamHead == null)
			return lastWord;
		
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
			
			//	we just don't have any predecessor (first page, etc.)
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
	
	private static ImWord findMainTextStreamHead(ImPage page) {
		ImWord[] textStreamHeads = page.getTextStreamHeads();
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println(" - got " + textStreamHeads.length + " text stream heads");
		if (textStreamHeads.length == 0)
			return null; // nothing to work with
		if (textStreamHeads.length == 1)
			return textStreamHeads[0]; // nothing to disambiguate
		
		//	find largest text stream
		ImWord mainTextStreamHead = null;
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
				textStreamArea += imw.bounds.getArea();
			}
			
			//	do we have a new front runner?
			if (textStreamArea > maxTextStreamArea) {
				maxTextStreamArea = textStreamArea;
				mainTextStreamHead = textStreamHeads[h];
			}
		}
		
		//	finally ...
		return mainTextStreamHead;
	}
//	private ImWord addTextStreamStructure(ImPage page, ImWord lastWord) {
//		if (PdfExtractorTest.aimAtPage != -1)
//			System.out.println("Analyzing text stream structure in page " + page.pageId);
//		
//		//	get paragraphs
//		ImRegion[] paragraphs = page.getRegions(MutableAnnotation.PARAGRAPH_TYPE);
//		if (PdfExtractorTest.aimAtPage != -1)
//			System.out.println(" - got " + paragraphs.length + " paragraphs");
//		if (paragraphs.length == 0)
//			return lastWord;
//		
//		//	get main text stream head (can only be one here, theoretically, but artifacts _can_ wreck havoc)
//		//	TODO make sure to use main text stream head, not one from a text box
//		//	==> TODO keep first non-textbox main-text word for each page ...
//		//	==> TODO ... and loop through here
//		ImWord[] textStreamHeads = page.getTextStreamHeads();
//		if (PdfExtractorTest.aimAtPage != -1)
//			System.out.println(" - got " + textStreamHeads.length + " text stream heads");
//		if (textStreamHeads.length == 0)
//			return lastWord;
//		ImWord textStreamHead = textStreamHeads[0];
//		
//		//	if we have multiple text stream heads, find and use the longest / largest one
//		if (textStreamHeads.length > 1) {
//			int maxTextStreamArea = 0;
//			for (int h = 0; h < textStreamHeads.length; h++) {
//				
//				//	skip over words that are figure or graphics labels
//				if (ImWord.TEXT_STREAM_TYPE_LABEL.equals(textStreamHeads[h].getTextStreamType()))
//					continue;
//				
//				//	skip over words known to be artifacts
//				if (ImWord.TEXT_STREAM_TYPE_ARTIFACT.equals(textStreamHeads[h].getTextStreamType()))
//					continue;
//				
//				//	compute text stream size
//				int textStreamArea = 0;
//				for (ImWord imw = textStreamHeads[h]; imw != null; imw = imw.getNextWord()) {
//					
//					//	we're leaving the page (can hardly happen processing pages front to back, but better make sure)
//					if (imw.pageId != textStreamHeads[h].pageId)
//						break;
//					
//					//	count in this word
//					textStreamArea += ((imw.bounds.right - imw.bounds.left) * (imw.bounds.bottom - imw.bounds.top));
//				}
//				
//				//	do we have a new front runner?
//				if (textStreamArea > maxTextStreamArea) {
//					maxTextStreamArea = textStreamArea;
//					textStreamHead = textStreamHeads[h];
//				}
//			}
//		}
//		
//		//	index words by paragraph
//		HashMap wordParagraphs = new HashMap();
//		for (int p = 0; p < paragraphs.length; p++) {
//			ImWord[] words = paragraphs[p].getWords();
//			for (int w = 0; w < words.length; w++)
//				wordParagraphs.put(words[w], paragraphs[p]);
//		}
//		if (PdfExtractorTest.aimAtPage != -1)
//			System.out.println(" - paragraphs indexed by words");
//		
//		//	add text stream structure
//		ImRegion lastWordPargarph = null;
//		for (ImWord imw = textStreamHead; imw != null; imw = imw.getNextWord()) {
//			
//			//	we just don't have any predecessor
//			if (lastWord == null) {
//				lastWord = imw;
//				lastWordPargarph = ((ImRegion) wordParagraphs.get(imw));
//				continue;
//			}
//			
//			//	check for hyphenation
//			boolean lastWordHyphenated = ((lastWord.getString() != null) && lastWord.getString().matches(".+[\\-\\u00AD\\u2010-\\u2015\\u2212]"));
//			boolean wordContinues = false;
//			if (lastWordHyphenated) {
//				String wordString = imw.getString();
//				if (wordString == null) {} // little we can do here ...
//				else if (wordString.length() == 0) {} // ... or here
//				else if (wordString.charAt(0) == Character.toUpperCase(wordString.charAt(0))) {} // starting with capital letter, not a word continued
//				else if ("and;or;und;oder;et;ou;y;e;o;u;ed".indexOf(wordString.toLowerCase()) != -1) {} // rather looks like an enumeration continued than a word (western European languages for now)
//				else wordContinues = true;
//			}
//			
//			//	get word paragraph
//			ImRegion wordParagraph = ((ImRegion) wordParagraphs.get(imw));
//			
//			//	predecessor on previous page, chain words, and mark (layout) paragraph break if not hyphenated
//			if (lastWord.pageId != imw.pageId) {
//				lastWord.setNextWord(imw);
//				lastWord.setNextRelation((lastWordHyphenated && wordContinues) ? ImWord.NEXT_RELATION_HYPHENATED : ImWord.NEXT_RELATION_PARAGRAPH_END);
//				lastWord = imw;
//				lastWordPargarph = wordParagraph;
//				continue;
//			}
//			
//			//	starting new paragraph
//			if (lastWordPargarph != wordParagraph) {
//				lastWord.setNextRelation(ImWord.NEXT_RELATION_PARAGRAPH_END);
//				lastWord = imw;
//				lastWordPargarph = wordParagraph;
//				continue;
//			}
//			
//			//	do we have a line break?
//			boolean lineBreak;
//			if (page.getDocument().orientation == ComponentOrientation.RIGHT_TO_LEFT)
//				lineBreak = (lastWord.bounds.left < imw.bounds.right); // working with left to right script
//			else lineBreak = (imw.bounds.right < lastWord.bounds.right); // working with right to left script
//			
//			//	line break, check hyphenation
//			if (lineBreak)
//				lastWord.setNextRelation((lastWordHyphenated && wordContinues) ? ImWord.NEXT_RELATION_HYPHENATED : ImWord.NEXT_RELATION_SEPARATE);
//			
//			//	switch to next word
//			lastWord = imw;
//			lastWordPargarph = wordParagraph;
//		}
//		
//		//	finally ...
//		return lastWord;
//	}
	
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
	public static final int USE_EMBEDDED_OCR = 0x01;
	
	/** control flag switching on or off skipping over leading or tailing born-digital meta pages in scanned PDF decoding */
	public static final int META_PAGES = 0x02;
	
	/** control flag indicating scan images with single pages in them in scanned PDF decoding */
	public static final int SINGLE_PAGE_SCANS = 0x04;
	
	/** control flag indicating scan images with double pages in them in scanned PDF decoding */
	public static final int DOUBLE_PAGE_SCANS = 0x08;
	
	/** control flag switching on or off heuristical scan enhancement in scanned PDF decoding */
	public static final int ENHANCE_SCANS = 0x10;
	
	/** control flag indicating the higher two bytes of the decoding flags indicate the resolution of scans (deactivates measurements and heuristical scaling) */
	public static final int USE_FIXED_RESOLUTION = 0x20;
//	
//	/** control flag indicating switching off or on embedded OCR adjustment (deactivates scaling to page image blocks, lines, and words) */
//	public static final int FIX_EMBEDDED_OCR = 0x40;
	
	/** control flag pair indicating to adjust embedded OCR to blocks, lines, and words */
	public static final int ADJUST_EMBEDDED_OCR_BLOCKS = 0x00;
	
	/** control flag pair indicating to adjust embedded OCR to lines and words (deactivates scaling to page image blocks) */
	public static final int ADJUST_EMBEDDED_OCR_LINES = 0x40;
	
	/** control flag pair indicating to adjust embedded OCR to words (deactivates scaling to page image blocks and lines) */
	public static final int ADJUST_EMBEDDED_OCR_WORDS = 0x80;
	
	/** control flag pair indicating switching off embedded OCR adjustment (deactivates scaling to page image blocks, lines, and words) */
	public static final int ADJUST_EMBEDDED_OCR_NONE = 0xC0;
	
	/** control flag switching on or off check for and inversion of extremely dark page images (only meaningful if scan enhancement is activated) */
	public static final int ENHANCE_SCANS_INVERT_WHITE_ON_BLACK = (Imaging.INVERT_WHITE_ON_BLACK << 8);
	
	/** control flag switching on or off smoothing of letters and other edges (only meaningful if scan enhancement is activated) */
	public static final int ENHANCE_SCANS_SMOOTH_LETTERS = (Imaging.SMOOTH_LETTERS << 8);
	
	/** control flag switching on or off background elimination (only meaningful if scan enhancement is activated) */
	public static final int ENHANCE_SCANS_ELIMINATE_BACKGROUND = (Imaging.ELIMINATE_BACKGROUND << 8);
	
	/** control flag switching on or off white balance (use strongly recommended, only meaningful if scan enhancement is activated) */
	public static final int ENHANCE_SCANS_WHITE_BALANCE = (Imaging.WHITE_BALANCE << 8);
	
	/** control flag switching on or off removal of dark areas along page edges (e.g. shadows on recessed areas of materials on a scanner surface) */
	public static final int ENHANCE_SCANS_CLEAN_PAGE_EDGES = (Imaging.CLEAN_PAGE_EDGES << 8);
	
	/** control flag switching on or off removal of speckles (dark areas too small to even be punctuation marks, only meaningful if scan enhancement is activated) */
	public static final int ENHANCE_SCANS_REMOVE_SPECKLES = (Imaging.REMOVE_SPECKLES << 8);
	
	/** control flag switching on or off detection and correction of large deviations (>2°) from the upright (only meaningful if scan enhancement is activated) */
	public static final int ENHANCE_SCANS_CORRECT_ROTATION = (Imaging.CORRECT_ROTATION << 8);
	
	/** control flag switching on or off detection and correction of small deviations (<2°) from the upright (use strongly recommended, only meaningful if scan enhancement is activated) */
	public static final int ENHANCE_SCANS_CORRECT_SKEW = (Imaging.CORRECT_SKEW << 8);
	
	/** control flag combination switching on all scan enhancement steps in scanned PDF decoding */
	public static final int ENHANCE_SCANS_ALL_OPTIONS = (Imaging.ALL_OPTIONS << 8);
	
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
		return this.loadImagePdf(null, null, pdfBytes, flags, 1, null, pm);
	}
	
	/**
	 * Load a document from an OCR PDF. Page images are stored in the page image
	 * store handed to the constructor. The document structure is stored in the
	 * returned image markup document, including word bounding boxes.
	 * @param doc the document to store the structural information in
	 * @param pdfDoc the PDF document to parse
	 * @param pdfBytes the raw binary data the the PDF document was parsed from
	 * @param flags control flags for decoding behavior
	 * @param pageIDs a set containing the IDs of the pages to decode
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the image markup document
	 * @throws IOException
	 */
	public ImDocument loadImagePdf(byte[] pdfBytes, int flags, Set pageIDs, ProgressMonitor pm) throws IOException {
		return this.loadImagePdf(null, null, pdfBytes, flags, 1, pageIDs, pm);
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
		return this.loadImagePdf(doc, pdfDoc, pdfBytes, metaPages, false, scaleFactor, pm);
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
		flags = (flags | ENHANCE_SCANS_ALL_OPTIONS); // set all enhancement detail flags
		return this.loadImagePdf(doc, pdfDoc, pdfBytes, flags, scaleFactor, null, pm);
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
		return this.loadImagePdf(doc, pdfDoc, pdfBytes, flags, scaleFactor, null, pm);
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
	 * @param pageIDs a set containing the IDs of the pages to decode
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the argument image markup document
	 * @throws IOException
	 */
	public ImDocument loadImagePdf(ImDocument doc, Document pdfDoc, byte[] pdfBytes, int flags, int scaleFactor, Set pageIDs, ProgressMonitor pm) throws IOException {
		
		//	check arguments
		pdfDoc = this.getPdfDocument(pdfDoc, pdfBytes);
		if (doc == null)
			doc = this.doCreateDocument(getChecksum(pdfBytes), ((pageIDs == null) ? 0 : pdfDoc.getNumberOfPages()), pageIDs);
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
		int enhanceScanFlags = (enhanceScans ? ((flags >>> 8) & Imaging.ALL_OPTIONS) : 0);
		System.out.println(" - scan enhacement flags are " + enhanceScanFlags);
		boolean useFixedDpi = ((flags & USE_FIXED_RESOLUTION) != 0);
		System.out.println(" - custom resolution is " + useFixedDpi);
		int fixedDpi = (useFixedDpi ? ((flags >>> 16) & 0xFFFF) : -1);
		System.out.println(" - fixed resolution is " + fixedDpi);
//		boolean fixEmbeddedOCR = (useEmbeddedOCR ? ((flags & FIX_EMBEDDED_OCR) != 0) : false);
//		System.out.println(" - fix embedded OCR is " + fixEmbeddedOCR);
		int embeddedOcrMode = (useEmbeddedOCR ? (flags & ADJUST_EMBEDDED_OCR_NONE) : ADJUST_EMBEDDED_OCR_BLOCKS);
		System.out.println(" - embedded OCR mode is " + Integer.toString(embeddedOcrMode, 16));
		
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
		PPageData[] pData = this.getPdfPageData(doc, (getFigures | (useEmbeddedOCR ? getWords : 0)), PdfFontDecoder.NO_DECODING, true, pageIDs, pageTree, objects, spm);
		
		//	add page content
		this.addImagePdfPages(doc, pData, pageTree, objects, metaPages, singlePages, doublePages, fixedDpi, useEmbeddedOCR, embeddedOcrMode, scaleFactor, enhanceScanFlags, spm);
		
		//	finally ...
		return doc;
	}
	
	private PPageData[] addImagePdfPages(ImDocument doc, PPageData[] pData, PageTree pageTree, Map objects, boolean metaPages, boolean singlePages, boolean doublePages, int fixedDpi, boolean useEmbeddedOCR, int embeddedOcrMode, int scaleFactor, int enhanceScanFlags, SynchronizedProgressMonitor spm) throws IOException {
		
		//	test if we can OCR
		if ((this.ocrEngine == null) && !useEmbeddedOCR)
			throw new RuntimeException("OCR Engine unavailable, check startup log.");
		
		//	more pages than we can process in parallel, don't cache at all to save memory
		Map pageImageCache;
//		if (pdfDoc.getPageTree().getNumberOfPages() > Runtime.getRuntime().availableProcessors())
		if (pData.length > Runtime.getRuntime().availableProcessors())
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
		pData = this.addImagePdfPages(doc, pData, pageTree, objects, metaPages, singlePages, doublePages, fixedDpi, scaleFactor, enhanceScanFlags, useEmbeddedOCR, new SynchronizedProgressMonitor(new CascadingProgressMonitor(spm)), pageImageCache);
		
		//	cache higher level page structure as well
		Map pageRegionCache = Collections.synchronizedMap(new HashMap());
		
		//	fill in blocks and do OCR
		spm.setStep("Extracting blocks & " + (useEmbeddedOCR ? "embedded " : "doing ") + "OCR");
		spm.setBaseProgress(30);
		spm.setProgress(0);
		spm.setMaxProgress(70);
		this.addImagePdfPageBlocks(doc, (useEmbeddedOCR ? pData : null), embeddedOcrMode, new CascadingProgressMonitor(spm), pageImageCache, pageRegionCache);
		
		//	analyze block structure and layout
		spm.setStep("Analyzing page text structure");
		spm.setBaseProgress(70);
		spm.setProgress(0);
		spm.setMaxProgress(90);
		this.addImagePdfPageLayout(doc, new CascadingProgressMonitor(spm), pageImageCache, pageRegionCache);
		
		//	analyze font metrics across whole document (unless embedded OCR already gives us that)
		spm.setStep("Analyzing font metrics");
		spm.setBaseProgress(90);
		spm.setProgress(0);
		spm.setMaxProgress(100);
		boolean analyzeFontMetrics = true;
		if (useEmbeddedOCR && (pData != null)) {
			int pWordCount = 0;
			int pWordCountBold = 0;
			int pWordCountItalics = 0;
			CountingSet pWordFontNames = new CountingSet(new TreeMap());
			CountingSet pWordFontSizes = new CountingSet(new TreeMap());
			ImPage[] pages = doc.getPages();
			for (int p = 0; p < pages.length; p++) {
				if (pData[pages[p].pageId] == null)
					continue;
				if (pData[pages[p].pageId].words == null)
					continue;
				if (pData[pages[p].pageId].words.length == 0)
					continue;
				for (int w = 0; w < pData[pages[p].pageId].words.length; w++) {
					pWordCount++;
					if (pData[pages[p].pageId].words[w].font == null)
						continue;
					if (pData[pages[p].pageId].words[w].font.bold)
						pWordCountBold++;
					if (pData[pages[p].pageId].words[w].font.italics)
						pWordCountItalics++;
					pWordFontNames.add(pData[pages[p].pageId].words[w].font.name);
					pWordFontSizes.add(new Integer(pData[pages[p].pageId].words[w].fontSize));
				}
			}
			System.out.println("Counted " + pWordCount + " words, " + pWordCountBold + " in bold, " + pWordCountItalics + " in italics");
			System.out.println("Font names are " + pWordFontNames);
			System.out.println(" ==> " + pWordFontNames.size() + " ones present, " + pWordFontNames.elementCount() + " distinct ones");
			System.out.println("Font sizes are " + pWordFontSizes);
			System.out.println(" ==> " + pWordFontSizes.size() + " ones present, " + pWordFontSizes.elementCount() + " distinct ones");
			if ((pWordCount != 0) && ((pWordCountBold != 0) && (pWordCountBold < pWordCount)))
				analyzeFontMetrics = false;
			else if ((pWordCount != 0) && ((pWordCountItalics != 0) && (pWordCountItalics < pWordCount)))
				analyzeFontMetrics = false;
//			else if ((pWordFontNames.size() != 0) && (pWordFontNames.elementCount() > 1))
//				analyzeFontMetrics = false;
//			else if ((pWordFontSizes.size() != 0) && (pWordFontSizes.elementCount() > 1))
//				analyzeFontMetrics = false;
		}
		if (analyzeFontMetrics) // TODO revisit this with HBMW !!!
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
		ImDocumentStyle docStyle = ImDocumentStyle.getStyleFor(doc);
		if (docStyle == null)
			docStyle = new ImDocumentStyle(new PropertiesData(new Properties()));
		final ImDocumentStyle docLayout = docStyle.getImSubset("layout");
		
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
//				PageAnalysis.splitIntoParagraphs(pages[p].getRegions(BLOCK_ANNOTATION_TYPE), pi.currentDpi, spm);
				ImDocumentStyle blockLayout = docLayout.getImSubset("block");
				ImRegion[] pageBlocks = pages[p].getRegions(BLOCK_ANNOTATION_TYPE);
				for (int b = 0; b < pageBlocks.length; b++)// {
//					BlockMetrics blockMetrics = PageAnalysis.computeBlockMetrics(pages[p], pi.currentDpi, pageBlocks[b]);
//					BlockLayout blockLayout = blockMetrics.analyze();
//					blockLayout.writeParagraphStructure();
					PageAnalysis.splitIntoParagraphs(pages[p], pi.currentDpi, pageBlocks[b], blockLayout);
//				}
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
		checkException(pf);
		
		//	finalize text stream structure
		ImWord lastWord = null;
		for (int p = 0; p < pages.length; p++)
			lastWord = addTextStreamStructure(pages[p], lastWord, null);
		spm.setInfo(" - word sequence analysis done");
	}
	
	private static ImWord addTextBlockStructure(ImPage page, ImWord lastWord, Region theRegion, int dpi, HashMap wordsByBoxes, ProgressMonitor pm) {
		
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
			return addTextBlockStructure(page, lastWord, theBlock, wordsByBoxes, pm);
		}
		
		//	non-atomic region ==> recurse to child regions
		for (int s = 0; s < theRegion.getSubRegionCount(); s++)
			lastWord = addTextBlockStructure(page, lastWord, theRegion.getSubRegion(s), dpi, wordsByBoxes, pm);
		
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
		return this.loadImagePdfBlocks(doc, pdfDoc, pdfBytes, false, false, scaleFactor, pm);
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
		flags = (flags | ENHANCE_SCANS_ALL_OPTIONS); // set all enhancement detail flags
		return this.loadImagePdfBlocks(doc, pdfDoc, pdfBytes, flags, scaleFactor, null, pm);
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
		return this.loadImagePdfBlocks(doc, pdfDoc, pdfBytes, flags, scaleFactor, null, pm);
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
	 * @param pageIDs a set containing the IDs of the pages to decode
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the argument image markup document
	 * @throws IOException
	 */
	public ImDocument loadImagePdfBlocks(ImDocument doc, Document pdfDoc, byte[] pdfBytes, int flags, int scaleFactor, Set pageIDs, ProgressMonitor pm) throws IOException {
		
		//	check arguments
		pdfDoc = this.getPdfDocument(pdfDoc, pdfBytes);
		if (doc == null)
			doc = this.doCreateDocument(getChecksum(pdfBytes), ((pageIDs == null) ? 0 : pdfDoc.getNumberOfPages()), pageIDs);
		if (pm == null)
			pm = ProgressMonitor.dummy;
		
		//	decode flags
		boolean useEmbeddedOCR = ((flags & USE_EMBEDDED_OCR) != 0);
		boolean metaPages = ((flags & META_PAGES) != 0);
		boolean singlePages = ((flags & SINGLE_PAGE_SCANS) != 0);
		boolean doublePages = ((flags & DOUBLE_PAGE_SCANS) != 0);
		boolean enhanceScans = ((flags & ENHANCE_SCANS) != 0);
		int enhanceScanFlags = (enhanceScans ? ((flags >>> 8) & Imaging.ALL_OPTIONS) : 0);
		boolean useFixedDpi = ((flags & USE_FIXED_RESOLUTION) != 0);
		int fixedDpi = (useFixedDpi ? ((flags >>> 16) & 0xFFFF) : -1);
//		boolean fixEmbeddedOCR = (useEmbeddedOCR ? ((flags & FIX_EMBEDDED_OCR) != 0) : false);
		int embeddedOcrMode = (useEmbeddedOCR ? (flags & ADJUST_EMBEDDED_OCR_NONE) : ADJUST_EMBEDDED_OCR_BLOCKS);
		
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
		PPageData[] pData = this.getPdfPageData(doc, (getFigures | (useEmbeddedOCR ? getWords : 0)), PdfFontDecoder.NO_DECODING, true, pageIDs, pageTree, objects, spm);
		
		//	load pages
		pm.setStep("Loading document page images");
		pm.setBaseProgress(0);
		pm.setProgress(0);
		pm.setMaxProgress(40);
		pData = this.addImagePdfPages(doc, pData, pageTree, objects, metaPages, singlePages, doublePages, fixedDpi, scaleFactor, enhanceScanFlags, useEmbeddedOCR, spm, pageImageCache);
		
		//	preserve source PDF in supplement
		ImSupplement.Source.createSource(doc, "application/pdf", pdfBytes);
		
		//	fill in blocks and do OCR
		pm.setStep("Extracting blocks & " + (useEmbeddedOCR ? "embedded " : "doing ") + "OCR");
		pm.setBaseProgress(40);
		pm.setProgress(0);
		pm.setMaxProgress(100);
//		this.addImagePdfPageBlocks(doc, (useEmbeddedOCR ? pData : null), fixEmbeddedOCR, spm, pageImageCache, null);
		this.addImagePdfPageBlocks(doc, (useEmbeddedOCR ? pData : null), embeddedOcrMode, spm, pageImageCache, null);
		
		//	finally ...
		return doc;
	}
	
	private void addImagePdfPageBlocks(final ImDocument doc, final PPageData[] pData, final int embeddedOcrMode, ProgressMonitor pm, final Map pageImageCache, final Map pageRegionCache) throws IOException {
		
		//	build progress monitor with synchronized methods instead of synchronizing in-code
		final ProgressMonitor spm = ((pm instanceof SynchronizedProgressMonitor) ? pm : new SynchronizedProgressMonitor(pm));
		
		//	get tokenizer to check and split words with
		final Tokenizer tokenizer = ((Tokenizer) doc.getAttribute(ImDocument.TOKENIZER_ATTRIBUTE, Gamta.INNER_PUNCTUATION_TOKENIZER));
		final Tokenizer numberTokenizer;
		
		//	set up running in parallel
		ParallelFor pf;
		
		//	sanitize embedded OCR words (if any)
		if (pData == null)
			numberTokenizer = null;
		else {
			final ProgressMonitor cpm = new CascadingProgressMonitor(spm);
			
			/* no need for word merging here, as good OCR keeps words together
			 * pretty well, as opposed to obfuscated born-digital PDFs, so
			 * merging would do more harm than good */
			
			//	assess number punctuation
			cpm.setStep("Assessing number punctuation");
			cpm.setBaseProgress(0);
			cpm.setProgress(0);
			cpm.setMaxProgress(3);
			numberTokenizer = getNumberTokenizer(pData, tokenizer, cpm);
			
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
					splitPageWords(pData[p], null, null, null, null, tokenizer, numberTokenizer, spm);
					
					//	give status update
					if (pData[p].words.length == 0)
						cpm.setInfo(" --> empty page");
					else cpm.setInfo(" --> got " + pData[p].words.length + " words in PDF");
				}
			};
			ParallelJobRunner.runParallelFor(pf, ((PdfExtractorTest.aimAtPage < 0) ? 0 : PdfExtractorTest.aimAtPage), ((PdfExtractorTest.aimAtPage < 0) ? pData.length : (PdfExtractorTest.aimAtPage + 1)), (this.useMultipleCores ? -1 : 1));
			cpm.setProgress(100);
			
			//	did anything go wrong?
			checkException(pf);
			
			//	update progress in main monitor
			spm.setBaseProgress(45);
		}
		
		//	get document pages
		final ImPage[] pages = doc.getPages();
		
		//	get document style and use it for page structure analysis
		ImDocumentStyle docStyle = ImDocumentStyle.getStyleFor(doc);
		if (docStyle == null)
			docStyle = new ImDocumentStyle(new PropertiesData(new Properties()));
		final ImDocumentStyle docLayout = docStyle.getImSubset("layout");
		
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
				if ((pData != null) && (pData[pages[p].pageId] != null) && (pData[pages[p].pageId].words != null) && (pData[pages[p].pageId].words.length != 0)) {
					
					//	compute word scale factor
					float magnification = (((pData[pages[p].pageId].rawPageImageDpi < 0) ? ((float) pi.originalDpi) : pData[pages[p].pageId].rawPageImageDpi) / defaultDpi);
					
					//	add words to page
					spm.setInfo("Adding embedded OCR words to page " + pages[p].pageId + " of " + pData.length);
					ArrayList pWords = new ArrayList();
					for (int w = 0; w < pData[pages[p].pageId].words.length; w++) {
						
						//	compute word bounds, and make sure word has some minimum width
						BoundingBox wb = getBoundingBox(pData[pages[p].pageId].words[w].bounds, pData[pages[p].pageId].pdfPageBox, magnification, pData[pages[p].pageId].rotate);
						if (wb.getWidth() < 4)
							wb = new BoundingBox(wb.left, (wb.left + 4), wb.top, wb.bottom);
						
						//	if we're on the right half of a double page, shift words to left
						if (((p % 2) == 1) && (pData[pages[p].pageId].rightPageOffset != 0))
							wb = new BoundingBox((wb.left - pData[pages[p].pageId].rightPageOffset), (wb.right - pData[pages[p].pageId].rightPageOffset), wb.top, wb.bottom);
						
						//	use embedded OCR as is (add word to page right away)
						ImWord word;
//						if (fixEmbeddedOCR) {
						if (embeddedOcrMode == ADJUST_EMBEDDED_OCR_NONE) {
							word = new ImWord(pages[p], wb, pData[pages[p].pageId].words[w].str);
							
							//	add baseline attribute, so OCR overlay rendering stays in bounds (descender tends to be 25-30%, so 27% should do for an estimate)
							word.setAttribute(BASELINE_ATTRIBUTE, ("" + (wb.bottom - Math.round(((float) (wb.getHeight() * 27)) / 100))));
						}
						
						//	only prepare embedded OCR word for adjustment to page image (we'll compute the baseline below)
						else word = new ImWord(doc, pages[p].pageId, wb, pData[pages[p].pageId].words[w].str);
						
						spm.setInfo(" - " + word.getString() + " @ " + word.bounds.toString() + " / " + pData[pages[p].pageId].words[w].bounds);
						pWords.add(word);
						
						//	set layout attributes (no need for font tracking in embedded OCR, though, should rarely use custom glyphs, let alone obfuscation)
//						if ((pData[pages[p].pageId].words[w].font != null) && (pData[pages[p].pageId].words[w].font.name != null))
//							word.setAttribute(FONT_NAME_ATTRIBUTE, pData[pages[p].pageId].words[w].font.name);
						if (pData[pages[p].pageId].words[w].fontSize != -1)
							word.setAttribute(FONT_SIZE_ATTRIBUTE, ("" + pData[pages[p].pageId].words[w].fontSize));
						if (pData[pages[p].pageId].words[w].bold)
							word.setAttribute(BOLD_ATTRIBUTE);
						if (pData[pages[p].pageId].words[w].italics)
							word.setAttribute(ITALICS_ATTRIBUTE);
					}
					
					//	adjust embedded OCR to page and add it only afterwards
//					if (!fixEmbeddedOCR) {
//						adjustEmbeddedOcr(doc, pages[p].pageId, pi, pWords, minColumnMargin, minBlockMargin, columnAreas, minColumnWidth, tokenizer, numberTokenizer, spm);
					if (embeddedOcrMode != ADJUST_EMBEDDED_OCR_NONE) {
						adjustEmbeddedOcr(doc, pages[p].pageId, pi, pWords, embeddedOcrMode, minColumnMargin, minBlockMargin, columnAreas, minColumnWidth, tokenizer, numberTokenizer, spm);
						
						//	add adjusted words to page
						for (int w = 0; w < pWords.size(); w++)
							pages[p].addWord((ImWord) pWords.get(w));
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
					System.out.println("pData is " + pData);
					if (pData != null)
						System.out.println("pData[p] is " + pData[pages[p].pageId]);
					if ((pData != null) && (pData[pages[p].pageId] != null))
						System.out.println("pData[p].words is " + pData[pages[p].pageId].words);
					if ((pData != null) && (pData[pages[p].pageId] != null) && (pData[pages[p].pageId].words != null))
						System.out.println("pData[p].words.length is " + pData[pages[p].pageId].words.length);
					
					//	obtain higher level page structure
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
		checkException(pf);
	}
	
	private static void adjustEmbeddedOcr(ImDocument doc, int pageId, PageImage pi, ArrayList words, int mode, int minColumnMargin, int minBlockMargin, BoundingBox[] columnAreas, int minColumnWidth, Tokenizer tokenizer, Tokenizer numberTokenizer, ProgressMonitor spm) {
		BufferedImage vbi = null;
		Graphics2D vbiGr = null;
		if (PdfExtractorTest.aimAtPage != -1) {
			vbi = new BufferedImage(pi.image.getWidth(), pi.image.getHeight(), BufferedImage.TYPE_INT_ARGB);
			vbiGr = vbi.createGraphics();
			vbiGr.setColor(Color.WHITE);
			vbiGr.fillRect(0, 0, vbi.getWidth(), vbi.getHeight());
			vbiGr.drawImage(pi.image, 0, 0, pi.image.getWidth(), pi.image.getHeight(), null);
		}
		
		AnalysisImage api = Imaging.wrapImage(pi.image, null);
		Region apiRootRegion = PageImageAnalysis.getPageRegion(api, pi.currentDpi, minColumnMargin, minBlockMargin, columnAreas, minColumnWidth, false, spm);
		Region[] apiBlocks = getAtomicRegions(apiRootRegion);
		
		PageBlock[] apiPageBlocks = new PageBlock[apiBlocks.length];
		boolean separateLines = (mode == ADJUST_EMBEDDED_OCR_BLOCKS);
		boolean regroupWords = ((mode == ADJUST_EMBEDDED_OCR_BLOCKS) || (mode == ADJUST_EMBEDDED_OCR_LINES));
		for (int b = 0; b < apiBlocks.length; b++) {
			BlockLine[] apiBlockLines = PageImageAnalysis.getBlockLinesAndWords(api, pi.currentDpi, apiBlocks[b].getBoundingBox(), separateLines, regroupWords, spm, vbi);
			apiPageBlocks[b] = new PageBlock(apiBlockLines);
		}
		
		/* TODO Also offer adjustment via Tools menu for whole document
==> helps adjust IMFs decoded earlier
  ==> better OCR overlay
Also offer adjustment for click inside block or selected block in scanned IMF
==> helps adjust IMFs om block by block basis

Also use adjustment to line for write-through in OCR line editor ...
... including amended proportion split:
- try and use word block local adjustment to gaps to amend reference font based proportional split, through
  - if any region shared between words, try and adjust boundaries to minimize cutoff ...
  - ... but avoid incurring new cutoffs (some parentheses just do wrap around letters next to them)
==> improves word splits

Also, make sure to render OCR overlay to line height in display panel:
- use line metrics instead of font layout ...
- ... or simply append "HqXp" to OCR string for height adjustment
- render to baseline, not bottom
		 */
		
		BufferedImage obi = new BufferedImage(pi.image.getWidth(), pi.image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
		Graphics2D obiGr = obi.createGraphics();
		obiGr.setColor(Color.WHITE);
		obiGr.fillRect(0, 0, obi.getWidth(), obi.getHeight());
		obiGr.setColor(Color.BLACK);
		for (int w = 0; w < words.size(); w++) {
			ImWord word = ((ImWord) words.get(w));
			obiGr.fillRect(word.bounds.left, word.bounds.top, word.bounds.getWidth(), word.bounds.getHeight());
		}
		AnalysisImage aoi = Imaging.wrapImage(obi, null);
		Region aoiRootRegion = PageImageAnalysis.getPageRegion(aoi, pi.currentDpi, minColumnMargin, minBlockMargin, columnAreas, minColumnWidth, false, spm);
		Region[] aoiBlocks = getAtomicRegions(aoiRootRegion);
		
		//	assign OCR words to blocks
		ArrayList ocrBlocks = new ArrayList(aoiBlocks.length);
		ArrayList ocrWords = new ArrayList(words);
		for (int b = 0; b < aoiBlocks.length; b++) {
			OcrBlock ob = new OcrBlock(aoiBlocks[b].bounds);
			BoundingBox obb = aoiBlocks[b].getBoundingBox();
			for (int w = 0; w < ocrWords.size(); w++) {
				if (obb.includes(((ImWord) ocrWords.get(w)).bounds, true))
					ob.words.add(ocrWords.remove(w--));
			}
			if (ob.words.isEmpty())
				continue;
			ocrBlocks.add(ob);
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println("Got OCR block at " + ob.getBounds() + " with " + ob.words.size() + " words");
		}
		System.out.println(ocrWords.size() + " of " + words.size() + " OCR words remaining");
		obiGr.setColor(Color.GRAY);
		for (int w = 0; w < ocrWords.size(); w++) {
			ImWord word = ((ImWord) ocrWords.get(w));
			obiGr.fillRect(word.bounds.left, word.bounds.top, word.bounds.getWidth(), word.bounds.getHeight());
		}
		obiGr.dispose();
		
		//	pair up blocks (merge multiple OCR blocks if in same page block)
		int emptyApiBlockCount = 0;
		for (int pb = 0; pb < apiPageBlocks.length; pb++) {
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println("Populating page block " + apiPageBlocks[pb].getBounds());
			int obCount = 0; 
			for (int ob = 0; ob < ocrBlocks.size(); ob++) {
				Point obc = ((OcrBlock) ocrBlocks.get(ob)).getCenter();
				if (apiPageBlocks[pb].contains(obc)) {
					apiPageBlocks[pb].addOcrBlock((OcrBlock) ocrBlocks.remove(ob--));
					obCount++;
				}
			}
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" ==> added " + obCount + " OCR blocks");
			if (apiPageBlocks[pb].ocrBlock == null)
				emptyApiBlockCount++;
			else if (PdfExtractorTest.aimAtPage != -1) {
				System.out.println(" ==> OCR block bounds are " + apiPageBlocks[pb].ocrBlock.getBounds());
				System.out.println(" ==> added " + apiPageBlocks[pb].ocrBlock.words.size() + " OCR words");
			}
		}
		System.out.println(ocrBlocks.size() + " OCR blocks and " + emptyApiBlockCount + " page blocks remaining");
		
		//	merge page blocks that are in same OCR block, and re-get lines those
		if (emptyApiBlockCount != 0) {
			ArrayList apiPageBlockList = new ArrayList(Arrays.asList(apiPageBlocks));
			for (int b = 0; b < apiPageBlockList.size(); b++) {
				PageBlock apiPageBlock = ((PageBlock) apiPageBlockList.get(b));
				if (apiPageBlock.ocrBlock != null)
					continue; // taken care of above ...
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println("Attempting to attach empty page block " + apiPageBlock.getBounds());
				Point pbc = apiPageBlock.getCenter();
				System.out.println(" - center is " + pbc);
				for (int cb = 0; cb < apiPageBlockList.size(); cb++) {
					if (cb == b)
						continue; // little we cound do here
					PageBlock cApiPageBlock = ((PageBlock) apiPageBlockList.get(cb));
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println(" - checking " + cApiPageBlock.getBounds());
					if (cApiPageBlock.ocrBlock == null)
						continue; // another empty block won't help ...
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println(" - OCR bounds are " + cApiPageBlock.ocrBlock.getBounds());
					if (!cApiPageBlock.ocrBlock.contains(pbc))
						continue;
					BoundingBox mApiBlockBounds = cApiPageBlock.getBounds().union(apiPageBlock.getBounds());
					BlockLine[] mApiBlockLines = PageImageAnalysis.getBlockLinesAndWords(api, pi.currentDpi, mApiBlockBounds, separateLines, regroupWords, spm, vbi);
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println(" ==> attached to " + cApiPageBlock.getBounds());
					PageBlock mApiPageBlock = new PageBlock(mApiBlockLines);
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println(" ==> bounds are " + mApiPageBlock.getBounds());
					mApiPageBlock.addOcrBlock(cApiPageBlock.ocrBlock);
					apiPageBlockList.set(cb, mApiPageBlock);
					apiPageBlockList.remove(b--);
					break;
				}
			}
			if (apiPageBlockList.size() < apiPageBlocks.length)
				apiPageBlocks = ((PageBlock[]) apiPageBlockList.toArray(new PageBlock[apiPageBlockList.size()]));
		}
		
		//	sort block words into lines (only now that we've merged any OCR blocks lying in same page block, and vice versa)
		for (int b = 0; b < apiPageBlocks.length; b++) {
			if (apiPageBlocks[b].ocrBlock == null)
				continue; // empty block ...
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println("Structuring page block " + apiPageBlocks[b].getBounds());
			ImUtils.sortLeftRightTopDown(apiPageBlocks[b].ocrBlock.words);
			OcrLine ol = null;
			ImWord lastWord = null;
			for (int w = 0; w < apiPageBlocks[b].ocrBlock.words.size(); w++) {
				ImWord word = ((ImWord) apiPageBlocks[b].ocrBlock.words.get(w));
				if ((lastWord == null) || ImUtils.areTextFlowBreak(lastWord, word)) {
					if ((PdfExtractorTest.aimAtPage != -1) && (ol != null)) {
						System.out.print("  - got line at " + ol.getPageBounds() + " with " + ol.words.size() + " words:");
						for (int lw = 0; lw < ol.words.size(); lw++)
							System.out.print(" " + ((OcrWord) ol.words.get(lw)).word.getString());
						System.out.println();
					}
					ol = new OcrLine(apiPageBlocks[b].ocrBlock);
				}
				ol.addWord(word);
				lastWord = word;
			}
			if ((PdfExtractorTest.aimAtPage != -1) && (ol != null)) {
				System.out.print("  - got line at " + ol.getPageBounds() + " with " + ol.words.size() + " words:");
				for (int lw = 0; lw < ol.words.size(); lw++)
					System.out.print(" " + ((OcrWord) ol.words.get(lw)).word.getString());
				System.out.println();
			}
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println("  ==> got " + apiPageBlocks[b].ocrBlock.lines.size() + " lines");
		}
		
		//	adjust OCR block to page block (limiting shift and stretch, though, as especially single-line blocks can be missing leading or tailing words)
		//	TODO handle cases with differing numbers of lines !!!
		for (int b = 0; b < apiPageBlocks.length; b++) {
			if (apiPageBlocks[b].ocrBlock == null)
				continue; // empty block ...
			if (PdfExtractorTest.aimAtPage != -1) {
				System.out.println("Adjusting lines in page block " + apiPageBlocks[b].getBounds());
				System.out.println(" - got " + apiPageBlocks[b].blockLines.length + " block lines and " + apiPageBlocks[b].ocrBlock.lines.size() + " OCR lines");
				System.out.println(" - OCR block is " + apiPageBlocks[b].ocrBlock.getBounds());
			}
			int widthDelta = Math.abs((apiPageBlocks[b].right - apiPageBlocks[b].left) - (apiPageBlocks[b].ocrBlock.right - apiPageBlocks[b].ocrBlock.left));
			int pbWidthPercent = ((widthDelta * 100) / (apiPageBlocks[b].right - apiPageBlocks[b].left));
			int obWidthPercent = ((widthDelta * 100) / (apiPageBlocks[b].ocrBlock.right - apiPageBlocks[b].ocrBlock.left));
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" - width difference is " + widthDelta + " (" + pbWidthPercent + "% of page block, " + obWidthPercent + "% of OCR block)");
			if ((pbWidthPercent < 50) && (obWidthPercent < 50)) {
				apiPageBlocks[b].ocrBlock.shiftHorizontally(apiPageBlocks[b].left - apiPageBlocks[b].ocrBlock.left);
				apiPageBlocks[b].ocrBlock.stretchHorizontally(apiPageBlocks[b].right - apiPageBlocks[b].ocrBlock.right);
			}
			else if (PdfExtractorTest.aimAtPage != -1)
				System.out.println("   ==> horizontal adjustment rejected");
			int heightDelta = Math.abs((apiPageBlocks[b].bottom - apiPageBlocks[b].top) - (apiPageBlocks[b].ocrBlock.bottom - apiPageBlocks[b].ocrBlock.top));
			int pbHeightPercent = ((heightDelta * 100) / (apiPageBlocks[b].bottom - apiPageBlocks[b].top));
			int obHeightPercent = ((heightDelta * 100) / (apiPageBlocks[b].ocrBlock.bottom - apiPageBlocks[b].ocrBlock.top));
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" - heigth difference is " + heightDelta + " (" + pbHeightPercent + "% of page block, " + obHeightPercent + "% of OCR block)");
			if ((pbHeightPercent < 50) && (obHeightPercent < 50)) {
				apiPageBlocks[b].ocrBlock.shiftVertically(apiPageBlocks[b].top - apiPageBlocks[b].ocrBlock.top);
				apiPageBlocks[b].ocrBlock.stretchVertically(apiPageBlocks[b].bottom - apiPageBlocks[b].ocrBlock.bottom);
			}
			else if (PdfExtractorTest.aimAtPage != -1)
				System.out.println("   ==> vertical adjustment rejected");
		}
		
		//	adjust word boundaries, transfer baselines, and collect words
		ArrayList pageWords = new ArrayList();
		for (int b = 0; b < apiPageBlocks.length; b++) {
			if (apiPageBlocks[b].ocrBlock == null)
				continue; // empty block ...
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println("Evaluating page block " + apiPageBlocks[b].getBounds());
			ArrayList[] combWords = new ArrayList[apiPageBlocks[b].blockLines.length];
			
			//	pair up lines inside block
			int lastMatchOl = -1;
			for (int bl = 0; bl < apiPageBlocks[b].blockLines.length; bl++) {
				BoundingBox blBb = apiPageBlocks[b].blockLines[bl].getPageBounds();
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println(" - line " + blBb + " with " + apiPageBlocks[b].blockLines[bl].getWordCount() + " words");
				combWords[bl] = new ArrayList();
				for (int lw = 0; lw < apiPageBlocks[b].blockLines[bl].getWordCount(); lw++)
					combWords[bl].add(new CombinedWord(apiPageBlocks[b].blockLines[bl], apiPageBlocks[b].blockLines[bl].getWord(lw)));
				HashSet emptyCombWords = new HashSet();
				HashSet matchedOcrWords = new HashSet();
				
				//	pair up page image line with OCR line
				for (int ol = (lastMatchOl + 1); ol < apiPageBlocks[b].ocrBlock.lines.size(); ol++) {
					OcrLine ocrLine = ((OcrLine) apiPageBlocks[b].ocrBlock.lines.get(ol));
					BoundingBox olBb = ocrLine.getPageBounds();
					if (!blBb.includes(olBb, true))
						continue;
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println("   - matched to OCR line " + olBb + " with " + ocrLine.words.size() + " words");
					
					//	pair up words
					int lastMatchOw = -1;
					for (int cw = 0; cw < combWords[bl].size(); cw++) {
						CombinedWord combWord = ((CombinedWord) combWords[bl].get(cw));
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println("     - word " + combWord.getPageBounds());
						for (int ow = (lastMatchOw + 1); ow < ocrLine.words.size(); ow++) {
							OcrWord ocrWord = ((OcrWord) ocrLine.words.get(ow));
							if (!combWord.containsOcrWord(ocrWord) && !combWord.liesInOcrWord(ocrWord))
								continue;
							combWord.addOcrWord(ocrWord);
							matchedOcrWords.add(ocrWord);
							lastMatchOw = ow;
						}
						if (combWord.ocrWords.isEmpty())
							emptyCombWords.add(combWord);
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println("       ==> OCR is " + combWord.getOcr());
					}
					lastMatchOl = ol;
					if (emptyCombWords.isEmpty() && (matchedOcrWords.size() == ocrLine.words.size()))
						break; // all words matched on both sides, we're done with this line
					
					//	merge line words contained in same OCR word
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println("   - handling (1) " + emptyCombWords.size() + " empty line words and " + (ocrLine.words.size() - matchedOcrWords.size()) + " unassigned OCR words");
					for (int cw = 1; cw < combWords[bl].size(); cw++) {
						CombinedWord lCombWord = ((CombinedWord) combWords[bl].get(cw-1));
						CombinedWord rCombWord = ((CombinedWord) combWords[bl].get(cw));
						if (!lCombWord.ocrContainsCombinedWord(rCombWord) && !rCombWord.ocrContainsCombinedWord(lCombWord))
							continue; // no shared contents
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println("     - merged " + lCombWord.getPageBounds() + " and " + rCombWord.getPageBounds() + " for OCR overlap");
						lCombWord.absorbCombinedWord(rCombWord);
						emptyCombWords.remove(lCombWord);
						emptyCombWords.remove(rCombWord);
						combWords[bl].remove(cw--);
					}
					
					//	match unassigned OCR words by maximum page word overlap
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println("   - handling (2) " + emptyCombWords.size() + " empty line words and " + (ocrLine.words.size() - matchedOcrWords.size()) + " unassigned OCR words");
					for (int ow = 0; ow < ocrLine.words.size(); ow++) {
						OcrWord ocrWord = ((OcrWord) ocrLine.words.get(ow));
						if (matchedOcrWords.contains(ocrWord))
							continue; // already assigned
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println("     - OCR word " + ocrWord.getPageBounds() + ": " + ocrWord.word.getString());
						for (int cw = 0; cw < combWords[bl].size(); cw++) {
							CombinedWord combWord = ((CombinedWord) combWords[bl].get(cw));
							if (!combWord.overlapsOcrWord(ocrWord))
								continue;
							if ((cw+1) < combWords[bl].size()) /* check for more overlap with next word */ {
								CombinedWord nCombWord = ((CombinedWord) combWords[bl].get(cw+1));
								if (nCombWord.overlapsOcrWord(ocrWord)) {
									int lOverlap = (combWord.right - ocrWord.getPageLeft());
									int rOverlap = (ocrWord.getPageRight() - nCombWord.left);
									if (lOverlap < rOverlap)
										continue;
								}
							}
							combWord.addOcrWord(ocrWord);
							matchedOcrWords.add(ocrWord);
							emptyCombWords.remove(combWord);
							if (PdfExtractorTest.aimAtPage != -1)
								System.out.println("       ==> matched to " + combWord.getPageBounds() + ", OCR is " + combWord.getOcr());
						}
					}
					if (emptyCombWords.isEmpty() && (matchedOcrWords.size() == ocrLine.words.size()))
						break; // all words matched on both sides, we're done with this line
					
					//	merge away empty page words
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println("   - handling (3) " + emptyCombWords.size() + " empty line words and " + (ocrLine.words.size() - matchedOcrWords.size()) + " unassigned OCR words");
					for (int cw = 0; cw < combWords[bl].size(); cw++) {
						CombinedWord combWord = ((CombinedWord) combWords[bl].get(cw));
						if (combWord.ocrWords.size() != 0)
							continue; // this one has its OCR
						CombinedWord lCombWord;
						int lDist;
						if (cw == 0) {
							lCombWord = null;
							lDist = Short.MAX_VALUE; // avoid arithmetic overflow below
						}
						else {
							lCombWord = ((CombinedWord) combWords[bl].get(cw-1));
							lDist = (combWord.left - lCombWord.right);
						}
						CombinedWord rCombWord;
						int rDist;
						if ((cw+1) == combWords[bl].size()) {
							rCombWord = null;
							rDist = Short.MAX_VALUE; // avoid arithmetic overflow below
						}
						else {
							rCombWord = ((CombinedWord) combWords[bl].get(cw+1));
							rDist = (rCombWord.left - combWord.right);
						}
						if (Math.abs(lDist - rDist) < 2)
							continue; // too ambiguous
						
						int minDist = Math.min(lDist, rDist);
						if ((minDist * 3) > apiPageBlocks[b].blockLines[bl].getHeight())
							continue; // looks like a regular space
						if (minDist == lDist) {
							System.out.println("     - merged " + lCombWord.getPageBounds() + " and empty " + combWord.getPageBounds() + " at distance " + lDist);
							lCombWord.absorbCombinedWord(combWord);
							combWords[bl].remove(cw--);
						}
						else if (minDist == rDist) {
							System.out.println("     - merged empty " + combWord.getPageBounds() + " and " + rCombWord.getPageBounds() + " at distance " + rDist);
							combWord.absorbCombinedWord(rCombWord);
							combWords[bl].set((cw+1), combWord);
							combWords[bl].remove(cw--);
						}
						emptyCombWords.remove(combWord);
					}
					if (emptyCombWords.isEmpty() && (matchedOcrWords.size() == ocrLine.words.size()))
						break; // all words matched on both sides, we're done with this line
					
					//	match any unassigned OCR words by maximum page word overlap again (we might have merged page words)
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println("   - handling (4) " + emptyCombWords.size() + " empty line words and " + (ocrLine.words.size() - matchedOcrWords.size()) + " unassigned OCR words");
					for (int ow = 0; ow < ocrLine.words.size(); ow++) {
						OcrWord ocrWord = ((OcrWord) ocrLine.words.get(ow));
						if (matchedOcrWords.contains(ocrWord))
							continue; // already assigned
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println("     - OCR word " + ocrWord.getPageBounds() + ": " + ocrWord.word.getString());
						for (int cw = 0; cw < combWords[bl].size(); cw++) {
							CombinedWord combWord = ((CombinedWord) combWords[bl].get(cw));
							if (!combWord.overlapsOcrWord(ocrWord))
								continue;
							if ((cw+1) < combWords[bl].size()) /* check for more overlap with next word */ {
								CombinedWord nCombWord = ((CombinedWord) combWords[bl].get(cw+1));
								if (nCombWord.overlapsOcrWord(ocrWord)) {
									int lOverlap = (combWord.right - ocrWord.getPageLeft());
									int rOverlap = (ocrWord.getPageRight() - nCombWord.left);
									if (lOverlap < rOverlap)
										continue;
								}
							}
							combWord.addOcrWord(ocrWord);
							matchedOcrWords.add(ocrWord);
							emptyCombWords.remove(combWord);
							if (PdfExtractorTest.aimAtPage != -1)
								System.out.println("       ==> matched to " + combWord.getPageBounds() + ", OCR is " + combWord.getOcr());
						}
					}
					if (emptyCombWords.isEmpty() && (matchedOcrWords.size() == ocrLine.words.size()))
						break; // all words matched on both sides, we're done with this line
					
					//	match any unassigned OCR words by proximity to page words
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println("   - handling (5) " + emptyCombWords.size() + " empty line words and " + (ocrLine.words.size() - matchedOcrWords.size()) + " unassigned OCR words");
					for (int ow = 0; ow < ocrLine.words.size(); ow++) {
						OcrWord ocrWord = ((OcrWord) ocrLine.words.get(ow));
						if (matchedOcrWords.contains(ocrWord))
							continue; // already assigned
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println("     - OCR word " + ocrWord.getPageBounds() + ": " + ocrWord.word.getString());
						int minDist = Short.MAX_VALUE;
						CombinedWord minDistCombWord = null;
						for (int cw = 0; cw < combWords[bl].size(); cw++) {
							CombinedWord combWord = ((CombinedWord) combWords[bl].get(cw));
							int lDist = (ocrWord.getPageLeft() - combWord.right);
							if ((0 <= lDist) && (lDist < minDist)) {
								minDist = lDist;
								minDistCombWord = combWord;
							}
							int rDist = (combWord.left - ocrWord.getPageRight());
							if ((0 <= rDist) && (rDist < minDist)) {
								minDist = rDist;
								minDistCombWord = combWord;
							}
						}
						if (minDistCombWord == null)
							continue; // nothing to work with
						if ((minDist * 3) > apiPageBlocks[b].blockLines[bl].getHeight())
							continue; // looks like a regular space
						minDistCombWord.addOcrWord(ocrWord);
						matchedOcrWords.add(ocrWord);
						emptyCombWords.remove(minDistCombWord);
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println("       ==> matched to " + minDistCombWord.getPageBounds() + " at distance " + minDist + ", OCR is " + minDistCombWord.getOcr());
					}
					if (emptyCombWords.isEmpty() && (matchedOcrWords.size() == ocrLine.words.size()))
						break; // all words matched on both sides, we're done with this line
					
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println("   - handling (6) " + emptyCombWords.size() + " empty line words and " + (ocrLine.words.size() - matchedOcrWords.size()) + " unassigned OCR words");
					break;
				}
				
				//	merge page words that overlap each others' OCR words
				for (int cw = 1; cw < combWords[bl].size(); cw++) {
					CombinedWord lCombWord = ((CombinedWord) combWords[bl].get(cw-1));
					CombinedWord rCombWord = ((CombinedWord) combWords[bl].get(cw));
					if (!lCombWord.ocrOverlapsCombinedWord(rCombWord) && !rCombWord.ocrOverlapsCombinedWord(lCombWord))
						continue; // no shared contents
					int dist = (rCombWord.left - lCombWord.right);
					if ((dist * 3) > apiPageBlocks[b].blockLines[bl].getHeight())
						continue; // looks like a regular space
					int pageDist = (rCombWord.left - lCombWord.right);
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println("   - OCR overlap merged " + lCombWord.getPageBounds() + " and " + rCombWord.getPageBounds() + " (distance in page " + pageDist + ", " + ((pageDist * 100) / apiPageBlocks[b].blockLines[bl].getHeight()) + "%), OCR is " + lCombWord.getOcr() + rCombWord.getOcr());
					lCombWord.absorbCombinedWord(rCombWord);
					emptyCombWords.remove(lCombWord);
					emptyCombWords.remove(rCombWord);
					combWords[bl].remove(cw--);
				}
				
				//	merge page words whose assigned OCR words are too close for a space
				for (int cw = 1; cw < combWords[bl].size(); cw++) {
					CombinedWord lCombWord = ((CombinedWord) combWords[bl].get(cw-1));
					if (lCombWord.ocrWords.isEmpty())
						continue; // nothing to work with, despite all efforts
					CombinedWord rCombWord = ((CombinedWord) combWords[bl].get(cw));
					if (rCombWord.ocrWords.isEmpty())
						continue; // nothing to work with, despite all efforts
					int ocrDist = (rCombWord.ocrLeft - lCombWord.ocrRight);
					if ((ocrDist * 6) > apiPageBlocks[b].blockLines[bl].getHeight())
						continue; // looks like a regular space
					
					//	check characters
					String lCombWordLast = lCombWord.getLastString();
					String lCombWordEnd = (((lCombWordLast == null) || (lCombWordLast.length() == 0)) ? "" : lCombWordLast.substring(lCombWordLast.length() - 1));
					String rCombWordFirst = rCombWord.getFirstString();
					String rCombWordStart = (((rCombWordFirst == null) || (rCombWordFirst.length() == 0)) ? "" : rCombWordFirst.substring(0, 1));
					if (Gamta.isPunctuation(lCombWordEnd) && !Gamta.spaceBefore(lCombWordEnd) && Gamta.spaceAfter(lCombWordEnd))
						continue; // block right merging words that have space after, but not before (periods, colons, etc., and closing brackets)
					if (Gamta.isPunctuation(rCombWordStart) && Gamta.spaceBefore(rCombWordStart) && !Gamta.spaceAfter(rCombWordStart))
						continue; // block left merging words that have space before, but not after (opening brackets)
					if (Character.isLetter(rCombWordStart.charAt(0)) && Character.isUpperCase(rCombWordStart.charAt(0)))
						continue; // block left merging words starting with capital letter
					if (Character.isLetter(lCombWordEnd.charAt(0)) && Character.isLetter(rCombWordStart.charAt(0)))
						continue; // block merging between two letters (words hardly ever break up)
					if (Character.isDigit(lCombWordEnd.charAt(0)) && (lCombWordEnd.charAt(0) != '1') && Character.isDigit(rCombWordStart.charAt(0)) && (rCombWordStart.charAt(0) != '1'))
						continue; // block merging between two digits unless at least one '1' involved (digits tend to be same width, incurring surplus space around '1's)
					
					//	check page image word distance
					int pageDist = (rCombWord.left - lCombWord.right);
					if ("1".equals(lCombWordEnd)) // account for smaller width of '1' with same advance as other digits 
						pageDist -= (apiPageBlocks[b].blockLines[bl].getHeight() / 20);
					if ("1".equals(rCombWordStart)) // account for smaller width of '1' with same advance as other digits
						pageDist -= (apiPageBlocks[b].blockLines[bl].getHeight() / 20);
					if ((pageDist * 4) > apiPageBlocks[b].blockLines[bl].getHeight())
						continue; // looks like a regular space (somewhat more conservative, as OCR didn't seem to see one)
					
					//	perform merger
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println("   - OCR distance merged " + lCombWord.getPageBounds() + " and " + rCombWord.getPageBounds() + " at distance " + ocrDist + " (" + pageDist + " in page, " + ((pageDist * 100) / apiPageBlocks[b].blockLines[bl].getHeight()) + "%), OCR is " + lCombWord.getOcr() + rCombWord.getOcr());
					lCombWord.absorbCombinedWord(rCombWord);
					emptyCombWords.remove(lCombWord);
					emptyCombWords.remove(rCombWord);
					combWords[bl].remove(cw--);
				}
				
				//	merge page words closer than half the line average apart (might be super stretched due to justification)
				CountingSet combWordDists = new CountingSet(new TreeMap());
				CountingSet relCombWordDists = new CountingSet(new TreeMap());
				for (int cw = 1; cw < combWords[bl].size(); cw++) {
					CombinedWord lCombWord = ((CombinedWord) combWords[bl].get(cw-1));
					CombinedWord rCombWord = ((CombinedWord) combWords[bl].get(cw));
					int dist = (rCombWord.left - lCombWord.right);
					combWordDists.add(new Integer(dist));
					dist = Math.min(dist, (apiPageBlocks[b].blockLines[bl].getHeight() / 2));
					relCombWordDists.add(new Integer((dist * 100) / apiPageBlocks[b].blockLines[bl].getHeight()));
				}
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println("   - distances are " + combWordDists);
				int avgCombWordDist = getAverageMid60(combWordDists, 0, Integer.MAX_VALUE);
				if (PdfExtractorTest.aimAtPage != -1) {
					System.out.println("     average distance is " + getAverage(combWordDists, 0, Integer.MAX_VALUE) + ", on mid 60% " + avgCombWordDist);
					System.out.println("   - relative distances are " + relCombWordDists);
					System.out.println("     average relative distance is " + getAverage(relCombWordDists, 0, Integer.MAX_VALUE) + ", on mid 60% " + getAverageMid60(relCombWordDists, 0, Integer.MAX_VALUE));
				}
				if (avgCombWordDist != -1)
					for (int cw = 1; cw < combWords[bl].size(); cw++) {
						CombinedWord lCombWord = ((CombinedWord) combWords[bl].get(cw-1));
						CombinedWord rCombWord = ((CombinedWord) combWords[bl].get(cw));
						int dist = (rCombWord.left - lCombWord.right);
						if (avgCombWordDist < (dist * 2))
							continue;
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println("   - narrow distance merged " + lCombWord.getPageBounds() + " and " + rCombWord.getPageBounds() + " for distance " + dist + " with average at " + avgCombWordDist + ", OCR is " + lCombWord.getOcr() + rCombWord.getOcr());
						lCombWord.absorbCombinedWord(rCombWord);
						emptyCombWords.remove(lCombWord);
						emptyCombWords.remove(rCombWord);
						combWords[bl].remove(cw--);
					}
				
				//	what do we got?
				if (PdfExtractorTest.aimAtPage != -1) {
					System.out.println("   ==> got " + combWords[bl].size() + " words");
					System.out.print("   ==> OCR is");
					for (int cw = 0; cw < combWords[bl].size(); cw++)
						System.out.print(" " + ((CombinedWord) combWords[bl].get(cw)).getOcr());
					System.out.println();
				}
			}
			
			//	add words to list
			for (int bl = 0; bl < combWords.length; bl++)
				for (int cw = 0; cw < combWords[bl].size(); cw++) {
					CombinedWord combWord = ((CombinedWord) combWords[bl].get(cw));
					String combWordOcr = combWord.getOcr();
					if (combWordOcr.length() == 0)
						combWordOcr = "TODO"; // TODO add switch for this one
					ImWord pageWord = new ImWord(doc, pageId, combWord.getPageBounds(), combWordOcr);
					if (combWord.isBold())
						pageWord.setAttribute(BOLD_ATTRIBUTE);
					if (combWord.isItalics())
						pageWord.setAttribute(ITALICS_ATTRIBUTE);
					if (combWord.getFontSize() != -1)
						pageWord.setAttribute(FONT_SIZE_ATTRIBUTE, ("" + combWord.getFontSize()));
					pageWord.setAttribute(BASELINE_ATTRIBUTE, ("" + combWord.line.getPageBaseline()));
					if (combWord.getCapHeight() != -1)
						pageWord.setAttribute("capHeight", ("" + combWord.getCapHeight()));
					if (combWord.getXHeight() != -1)
						pageWord.setAttribute("xHeight", ("" + combWord.getXHeight()));
					addOcrWord(doc, pageId, pageWord, tokenizer, numberTokenizer, pageWords, spm);
				}
		}
		
		if (vbi != null) {
			BufferedImage cbi = new BufferedImage(pi.image.getWidth(), pi.image.getHeight(), BufferedImage.TYPE_INT_ARGB);
			Graphics2D cbiGr = cbi.createGraphics();
			cbiGr.setColor(Color.WHITE);
			cbiGr.fillRect(0, 0, cbi.getWidth(), cbi.getHeight());
			cbiGr.drawImage(pi.image, 0, 0, pi.image.getWidth(), pi.image.getHeight(), null);
			cbiGr.setColor(Color.BLACK);
			for (int w = 0; w < words.size(); w++) {
				ImWord word = ((ImWord) words.get(w));
				cbiGr.fillRect(word.bounds.left, word.bounds.top, word.bounds.getWidth(), word.bounds.getHeight());
			}
			AnalysisImage aci = Imaging.wrapImage(cbi, null);
			Region aciRootRegion = PageImageAnalysis.getPageRegion(aci, pi.currentDpi, minColumnMargin, minBlockMargin, columnAreas, minColumnWidth, false, spm);
			Region[] aciBlocks = getAtomicRegions(aciRootRegion);
			
			vbiGr.setColor(new Color(255, 0, 0, 64));
			for (int w = 0; w < words.size(); w++) {
				ImWord word = ((ImWord) words.get(w));
				vbiGr.fillRect(word.bounds.left, word.bounds.top, word.bounds.getWidth(), word.bounds.getHeight());
			}
			vbiGr.setColor(new Color(0, 0, 255, 64));
			for (int b = 0; b < apiPageBlocks.length; b++) {
				if (apiPageBlocks[b].ocrBlock == null)
					continue; // empty block ...
				for (int l = 0; l < apiPageBlocks[b].ocrBlock.lines.size(); l++) {
					OcrLine ol = ((OcrLine) apiPageBlocks[b].ocrBlock.lines.get(l));
					for (int w = 0; w < ol.words.size(); w++) {
						OcrWord ow = ((OcrWord) ol.words.get(w));
						BoundingBox owbb = ow.getPageBounds();
						vbiGr.fillRect(owbb.left, owbb.top, owbb.getWidth(), owbb.getHeight());
					}
				}
			}
			
			vbiGr.setStroke(new BasicStroke(3));
			vbiGr.setColor(new Color(0, 255, 0, 128));
			for (int b = 0; b < apiBlocks.length; b++) {
				BoundingBox apiBb = apiBlocks[b].getBoundingBox();
				vbiGr.drawRect(apiBb.left, apiBb.top, apiBb.getWidth(), apiBb.getHeight());
			}
//			vbiGr.setStroke(new BasicStroke(1));
//			for (int b = 0; b < apiBlocks.length; b++) {
//				if (apiBlockLines[b] == null)
//					continue;
//				for (int l = 0; l < apiBlockLines[b].length; l++)
//					vbiGr.drawRect(apiBlockLines[b][l].getLeftCol(), apiBlockLines[b][l].getTopRow(), apiBlockLines[b][l].getWidth(), apiBlockLines[b][l].getHeight());
//			}
			vbiGr.setStroke(new BasicStroke(3));
			vbiGr.setColor(new Color(255, 0, 0, 128));
			for (int b = 0; b < aoiBlocks.length; b++) {
				BoundingBox aoiBb = aoiBlocks[b].getBoundingBox();
				vbiGr.drawRect(aoiBb.left, aoiBb.top, aoiBb.getWidth(), aoiBb.getHeight());
			}
			vbiGr.setColor(new Color(0, 0, 255, 128));
			for (int b = 0; b < aciBlocks.length; b++) {
				BoundingBox aciBb = aciBlocks[b].getBoundingBox();
				vbiGr.drawRect(aciBb.left, aciBb.top, aciBb.getWidth(), aciBb.getHeight());
			}
			
			ImageDisplayDialog idd = new ImageDisplayDialog("OCR relative to page images");
			idd.addImage(vbi, ("Page " + pageId + " Lines"));
			idd.addImage(pi.image, ("Page " + pageId + " Scan"));
			idd.addImage(obi, ("Page " + pageId + " OCR"));
			idd.setSize(800, 800);
			idd.setLocationRelativeTo(null);
			idd.setVisible(true);
		}
		
		//	generate and return words
		words.clear();
		words.addAll(pageWords);
	}
	
	private static Region[] getAtomicRegions(Region theRegion) {
		ArrayList regions = new ArrayList();
		addAtomicRegions(theRegion, regions);
		return ((Region[]) regions.toArray(new Region[regions.size()]));
	}
	private static void addAtomicRegions(Region theRegion, ArrayList regions) {
		if (theRegion.isAtomic() || theRegion.isImage())
			regions.add(theRegion);
		else for (int s = 0; s < theRegion.getSubRegionCount(); s++)
			addAtomicRegions(theRegion.getSubRegion(s), regions);
	}
	
	private static int getAverage(CountingSet cs, int minToCount, int maxToCount) {
		if (cs.isEmpty())
			return -1;
		int count = 0;
		int sum = 0;
		for (Iterator it = cs.iterator(); it.hasNext();) {
			Integer i = ((Integer) it.next());
			if (i.intValue() < minToCount)
				continue;
			if (maxToCount < i.intValue())
				break;
			count += cs.getCount(i);
			sum += (i.intValue() * cs.getCount(i));
		}
		return ((count == 0) ? -1 : ((sum + (count / 2)) / count));
	}
	private static int getAverageMid60(CountingSet cs, int minToCount, int maxToCount) {
		if (cs.isEmpty())
			return -1;
		int checkCount = 0;
		int count = 0;
		int sum = 0;
		for (Iterator it = cs.iterator(); it.hasNext();) {
			Integer i = ((Integer) it.next());
			if ((cs.size() * 80) < (checkCount * 100))
				break; // beyond 80%
			int iCount = cs.getCount(i);
			checkCount += iCount;
			if (i.intValue() < minToCount)
				continue;
			if (maxToCount < i.intValue())
				break;
			if ((cs.size() * (100 - 80)) < (checkCount * 100)) /* we're beyond smallest 20% */ {
				count += iCount;
				sum += (i.intValue() * iCount);
			}
		}
		return ((count == 0) ? -1 : ((sum + (count / 2)) / count));
	}
	
	private static void addOcrWord(ImDocument doc, int pageId, ImWord word, Tokenizer tokenizer, Tokenizer numberTokenizer, ArrayList pageWords, ProgressMonitor spm) {
		
		//	check tokenization
		TokenSequence wordTokens = (Gamta.isNumber(word.getString()) ? numberTokenizer : tokenizer).tokenize(word.getString());
		
		//	ensure minimum word width of 4
		if (wordTokens.size() < 2) {
			if (word.bounds.getWidth() < 4) {
				int left = word.bounds.left;
				int right = word.bounds.right;
				if ((right - left) < 4)
					right++;
				if ((right - left) < 4)
					left--;
				if ((right - left) < 4)
					right++;
				if ((right - left) < 4)
					left--;
				ImWord xWord = new ImWord(doc, pageId, new BoundingBox(left, right, word.bounds.top, word.bounds.bottom), word.getString());
				if (word.hasAttribute(BOLD_ATTRIBUTE))
					xWord.setAttribute(BOLD_ATTRIBUTE);
				if (word.hasAttribute(ITALICS_ATTRIBUTE))
					xWord.setAttribute(ITALICS_ATTRIBUTE);
				if (word.getFontSize() != -1)
					xWord.setAttribute(FONT_SIZE_ATTRIBUTE, word.getAttribute(FONT_SIZE_ATTRIBUTE));
				xWord.setAttribute(BASELINE_ATTRIBUTE, word.getAttribute(BASELINE_ATTRIBUTE));
				word = xWord;
			}
			pageWords.add(word);
			return;
		}
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println(" - splitting " + word.getString());
		
		//	get width for each token at word font size
		String[] splitTokens = new String[wordTokens.size()];
		float[] splitTokenWidths = new float[wordTokens.size()];
		float splitTokenWidthSum = 0;
		
		//	perform proportional word split (using default serif font)
		Font pWordFont = getFont("Serif", Font.PLAIN, true, false, 12);
		for (int s = 0; s < splitTokens.length; s++) {
			splitTokens[s] = wordTokens.valueAt(s);
			TextLayout tl = new TextLayout(splitTokens[s], pWordFont, new FontRenderContext(null, false, true));
			splitTokenWidths[s] = ((float) tl.getBounds().getWidth());
			splitTokenWidthSum += splitTokenWidths[s];
		}
		
		//	ensure minimum word width of 4
		float splitTokenMinWidth = ((splitTokenWidthSum * 4) / word.bounds.getWidth());
		splitTokenWidthSum = 0;
		for (int s = 0; s < splitTokens.length; s++) {
			if (splitTokenWidths[s] < splitTokenMinWidth)
				splitTokenWidths[s] = splitTokenMinWidth;
			splitTokenWidthSum += splitTokenWidths[s];
		}
		
		//	store split result, splitting word bounds accordingly
		//	TODO back this up with region coloring (way more accurate) !!!
		float splitTokenLeft = word.bounds.left;
		for (int s = 0; s < splitTokens.length; s++) {
			float splitTokenWidth = ((word.bounds.getWidth() * splitTokenWidths[s]) / splitTokenWidthSum);
			BoundingBox splitWordBounds = new BoundingBox(Math.round(splitTokenLeft), Math.round(splitTokenLeft + splitTokenWidth), word.bounds.top, word.bounds.bottom);
			ImWord splitWord = new ImWord(doc, pageId, splitWordBounds, splitTokens[s]);
			if (word.hasAttribute(BOLD_ATTRIBUTE))
				splitWord.setAttribute(BOLD_ATTRIBUTE);
			if (word.hasAttribute(ITALICS_ATTRIBUTE))
				splitWord.setAttribute(ITALICS_ATTRIBUTE);
			if (word.getFontSize() != -1)
				splitWord.setAttribute(FONT_SIZE_ATTRIBUTE, word.getAttribute(FONT_SIZE_ATTRIBUTE));
			splitWord.setAttribute(BASELINE_ATTRIBUTE, word.getAttribute(BASELINE_ATTRIBUTE));
			splitWord.setAttribute("xHeight", word.getAttribute("xHeight"));
			splitWord.setAttribute("capHeight", word.getAttribute("capHeight"));
			pageWords.add(splitWord);
			splitTokenLeft += splitTokenWidth;
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println("   --> split word part " + splitWord.getString() + ", bounds are " + splitWord.bounds);
		}
	}
	
	private static class PageBlock {
		BlockLine[] blockLines;
		int left = Integer.MAX_VALUE;
		int right = Integer.MIN_VALUE;
		int top = Integer.MAX_VALUE;
		int bottom = Integer.MIN_VALUE;
		OcrBlock ocrBlock = null;
//		ArrayList ocrWords = new ArrayList();
//		ArrayList ocrLines = new ArrayList();
		PageBlock(BlockLine[] blockLines) {
			this.blockLines = blockLines;
			for (int l = 0; l < this.blockLines.length; l++) {
				this.left = Math.min(this.left, this.blockLines[l].getPageLeft());
				this.right = Math.max(this.right, this.blockLines[l].getPageRight());
				this.top = Math.min(this.top, this.blockLines[l].getPageTop());
				this.bottom = Math.max(this.bottom, this.blockLines[l].getPageBottom());
			}
		}
		BoundingBox getBounds() {
			return new BoundingBox(this.left, this.right, this.top, this.bottom);
		}
		Point getCenter() {
			return new Point(((this.left + this.right) / 2), ((this.top + this.bottom) / 2));
		}
		boolean contains(Point p) {
			if (p.x < this.left)
				return false;
			else if (this.right <= p.x)
				return false;
			else if (p.y < this.top)
				return false;
			else if (this.bottom <= p.y)
				return false;
			else return true;
		}
//		void addWords(OcrBlock ob) {
//			this.ocrWords.addAll(ob.words);
//		}
		void addOcrBlock(OcrBlock ob) {
			if (this.ocrBlock == null)
				this.ocrBlock = ob;
			else this.ocrBlock.include(ob);
		}
//		void include(PageBlock pb) {
//			if (pb == this)
//				return;
//			this.left = Math.min(this.left, pb.left);
//			this.right = Math.max(this.right, pb.right);
//			this.top = Math.min(this.top, pb.top);
//			this.bottom = Math.max(this.bottom, pb.bottom);
//		}
	}
	
	private static class OcrBlock {
		int left;
		int right;
		int top;
		int bottom;
		ArrayList lines = new ArrayList();
		ArrayList words = new ArrayList();
		OcrBlock(ImagePartRectangle bounds) {
			this.left = bounds.getLeftCol();
			this.right = bounds.getRightCol();
			this.top = bounds.getTopRow();
			this.bottom = bounds.getBottomRow();
		}
		BoundingBox getBounds() {
			return new BoundingBox(this.left, this.right, this.top, this.bottom);
		}
		Point getCenter() {
			return new Point(((this.left + this.right) / 2), ((this.top + this.bottom) / 2));
		}
		boolean contains(Point p) {
			if (p.x < this.left)
				return false;
			else if (this.right <= p.x)
				return false;
			else if (p.y < this.top)
				return false;
			else if (this.bottom <= p.y)
				return false;
			else return true;
		}
		void include(OcrBlock ob) {
			if (ob == this)
				return;
			this.words.addAll(ob.words);
			this.left = Math.min(this.left, ob.left);
			this.right = Math.max(this.right, ob.right);
			this.top = Math.min(this.top, ob.top);
			this.bottom = Math.max(this.bottom, ob.bottom);
		}
		void shiftHorizontally(int by) {
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" - shifting horizontally by " + by + ", " + ((by * 100) / (this.right - this.left)) + "%");
			this.left += by;
			this.right += by;
		}
		void stretchHorizontally(int by) {
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" - stretching horizontally by " + by + ", " + ((by * 100) / (this.right - this.left)) + "%");
			for (int l = 0; l < this.lines.size(); l++) {
				OcrLine ol = ((OcrLine) this.lines.get(l));
				int lols = ((by * ol.left) / (this.right - this.left));
				ol.left += lols;
				int rols = ((by * ol.right) / (this.right - this.left));
				ol.right += rols;
				if (lols != rols)
					ol.stretchHorizontally(rols - lols);
			}
		}
		void shiftVertically(int by) {
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" - shifting vertically by " + by + ", " + ((by * 100) / (this.bottom - this.top)) + "%");
			this.top += by;
			this.bottom += by;
		}
		void stretchVertically(int by) {
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" - stretching vertically by " + by + ", " + ((by * 100) / (this.bottom - this.top)) + "%");
			for (int l = 0; l < this.lines.size(); l++) {
				OcrLine ol = ((OcrLine) this.lines.get(l));
				int olc = ((ol.top + ol.bottom) / 2);
				int ols = ((by * olc) / (this.bottom - this.top));
				ol.top += ols;
				ol.bottom += ols;
			}
			this.bottom += by;
		}
	}
	private static class OcrLine {
		OcrBlock block;
		int left = Integer.MAX_VALUE; // relative to parent block for easier whole-block bulk shift
		int right = Integer.MIN_VALUE; // relative to parent block for easier whole-block bulk shift
		int top = Integer.MAX_VALUE; // relative to parent block for easier whole-block bulk shift
		int bottom = Integer.MIN_VALUE; // relative to parent block for easier whole-block bulk shift
		ArrayList words = new ArrayList();
		OcrLine(OcrBlock block) {
			this.block = block;
			this.block.lines.add(this);
		}
		void addWord(ImWord word) {
			this.left = Math.min(this.left, (word.bounds.left - this.block.left));
			this.right = Math.max(this.right, (word.bounds.right - this.block.left));
			this.top = Math.min(this.top, (word.bounds.top - this.block.top));
			this.bottom = Math.max(this.bottom, (word.bounds.bottom - this.block.top));
			this.words.add(new OcrWord(word, this));
		}
//		BoundingBox getBlockBounds() {
//			return new BoundingBox(this.left, this.right, this.top, this.bottom);
//		}
		BoundingBox getPageBounds() {
			return new BoundingBox((this.left + this.block.left), (this.right + this.block.left), (this.top + this.block.top), (this.bottom + this.block.top));
		}
		void stretchHorizontally(int by) {
			for (int w = 0; w < this.words.size(); w++) {
				OcrWord ow = ((OcrWord) this.words.get(w));
				int lows = ((by * ow.left) / (this.right - this.left));
				ow.left += lows;
				int rows = ((by * ow.right) / (this.right - this.left));
				ow.right += rows;
			}
		}
	}
	private static class OcrWord implements Comparable {
		final ImWord word;
		OcrLine line;
		int left; // relative to parent line for easier whole-line bulk shift
		int right; // relative to parent line for easier whole-line bulk shift
		OcrWord(ImWord word, OcrLine line) {
			this.word = word;
			this.line = line;
			this.left = (this.word.bounds.left - this.line.left - this.line.block.left);
			this.right = (this.word.bounds.right - this.line.left - this.line.block.left);
		}
//		BoundingBox getLineBounds() {
//			return new BoundingBox(this.left, this.right, 0, (this.line.bottom - this.line.top));
//		}
//		BoundingBox getBlockBounds() {
//			return new BoundingBox((this.left + this.line.left), (this.right + this.line.left), this.line.top, this.line.bottom);
//		}
		int getPageLeft() {
			return (this.left + this.line.left + this.line.block.left);
		}
		int getPageRight() {
			return (this.right + this.line.left + this.line.block.left);
		}
		BoundingBox getPageBounds() {
			return new BoundingBox((this.left + this.line.left + this.line.block.left), (this.right + this.line.left + this.line.block.left), (this.line.top + this.line.block.top), (this.line.bottom + this.line.block.top));
		}
		public int compareTo(Object obj) {
			return ImUtils.leftRightOrder.compare(this.word, ((OcrWord) obj).word);
		}
	}
	
	private static class CombinedWord {
		BlockLine line;
		int left;
		int right;
		ArrayList ocrWords = new ArrayList(2);
		boolean ocrWordsSorted = true;
		int ocrLeft = Integer.MAX_VALUE;
		int ocrRight = Integer.MIN_VALUE;
		int ocrChars = 0;
		int ocrBoldChars = 0;
		int ocrItalicsChars = 0;
		int ocrFontSizeCount = 0;
		int ocrFontSizeSum = 0;
		CombinedWord(BlockLine line, LineWord word) {
			this.line = line;
			this.left = word.getPageLeft();
			this.right = word.getPageRight();
		}
		boolean containsOcrWord(OcrWord ow) {
			int owc = ((ow.getPageLeft() + ow.getPageRight()) / 2);
			return ((this.left <= owc) && (owc <= this.right));
		}
		boolean liesInOcrWord(OcrWord ow) {
			int cwc = ((this.left + this.right) / 2);
			return ((ow.getPageLeft() <= cwc) && (cwc <= ow.getPageRight()));
		}
		boolean overlapsOcrWord(OcrWord ow) {
			return ((this.left < ow.getPageRight()) && (ow.getPageLeft() < this.right));
		}
		void addOcrWord(OcrWord ow) {
			this.ocrWords.add(ow);
			this.ocrWordsSorted = false;
			
			this.ocrLeft = Math.min(this.ocrLeft, ow.getPageLeft());
			this.ocrRight = Math.max(this.ocrRight, ow.getPageRight());
			
			this.ocrChars += ow.word.getString().length();
			this.ocrBoldChars += (ow.word.hasAttribute(BOLD_ATTRIBUTE) ? ow.word.getString().length() : 0);
			this.ocrItalicsChars += (ow.word.hasAttribute(ITALICS_ATTRIBUTE) ? ow.word.getString().length() : 0);
			if (ow.word.getFontSize() != -1) {
				this.ocrFontSizeCount += ow.word.getString().length();
				this.ocrFontSizeSum += (ow.word.getString().length() * ow.word.getFontSize());
			}
		}
		boolean ocrContainsCombinedWord(CombinedWord cw) {
			int cwc = ((cw.left + cw.right) / 2);
			return ((this.ocrLeft <= cwc) && (cwc <= this.ocrRight));
		}
		boolean ocrOverlapsCombinedWord(CombinedWord cw) {
			return ((this.ocrLeft < cw.right) && (cw.left < this.ocrRight));
		}
		void absorbCombinedWord(CombinedWord cw) {
			this.left = Math.min(this.left, cw.left);
			this.right = Math.max(this.right, cw.right);
			
			this.ocrWords.addAll(cw.ocrWords);
			this.ocrWordsSorted = false;
			
			this.ocrLeft = Math.min(this.ocrLeft, cw.ocrLeft);
			this.ocrRight = Math.max(this.ocrRight, cw.ocrRight);
			
			this.ocrChars += cw.ocrChars;
			this.ocrBoldChars += cw.ocrBoldChars;
			this.ocrItalicsChars += cw.ocrItalicsChars;
			
			this.ocrFontSizeCount += cw.ocrFontSizeCount;
			this.ocrFontSizeSum += cw.ocrFontSizeSum;
		}
//		BoundingBox getLineBounds() {
//			return new BoundingBox(this.left, this.right, 0, this.line.getHeight());
//		}
//		BoundingBox getBlockBounds() {
//			return new BoundingBox((this.left + this.line.getBlockLeft()), (this.right + this.line.getBlockLeft()), this.line.getBlockTop(), this.line.getBlockBottom());
//		}
		BoundingBox getPageBounds() {
			return new BoundingBox(this.left, this.right, this.line.getPageTop(), this.line.getPageBottom());
		}
		void ensureOcrWordsSorted() {
			if (this.ocrWordsSorted)
				return;
			if (this.ocrWords.size() > 1)
				Collections.sort(this.ocrWords);
			this.ocrWordsSorted = true;
		}
		String getOcr() {
			this.ensureOcrWordsSorted();
			StringBuffer ocr = new StringBuffer();
			for (int w = 0; w < this.ocrWords.size(); w++)
				ocr.append(((OcrWord) this.ocrWords.get(w)).word.getString().trim());
			return ocr.toString();
		}
		String getFirstString() {
			if (this.ocrWords.isEmpty())
				return null;
			this.ensureOcrWordsSorted();
			return ((OcrWord) this.ocrWords.get(0)).word.getString();
		}
		String getLastString() {
			if (this.ocrWords.isEmpty())
				return null;
			this.ensureOcrWordsSorted();
			return ((OcrWord) this.ocrWords.get(this.ocrWords.size()-1)).word.getString();
		}
		boolean isBold() {
			return ((this.ocrChars == 0) ? false : (this.ocrChars < (this.ocrBoldChars * 2)));
		}
		boolean isItalics() {
			return ((this.ocrChars == 0) ? false : (this.ocrChars < (this.ocrItalicsChars * 2)));
		}
		int getFontSize() {
			return ((this.ocrFontSizeCount == 0) ? -1 : ((this.ocrFontSizeSum + (this.ocrFontSizeCount / 2)) / this.ocrFontSizeCount));
		}
		int getCapHeight() {
			return this.line.getCapHeight();
		}
		int getXHeight() {
			return this.line.getXHeight();
		}
	}
	
	private static void addRegionBlocks(ImPage page, Region theRegion, int dpi, ProgressMonitor pm) {
		
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
				addRegionBlocks(page, theRegion.getSubRegion(s), dpi, pm);
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
		return this.loadImagePdfPages(null, null, pdfBytes, flags, 1, null, pm);
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
	 * @param pageIDs a set containing the IDs of the pages to decode
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the image markup document
	 * @throws IOException
	 */
	public ImDocument loadImagePdfPages(byte[] pdfBytes, int flags, Set pageIDs, ProgressMonitor pm) throws IOException {
		return this.loadImagePdfPages(null, null, pdfBytes, flags, 1, pageIDs, pm);
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
		flags = (flags | ENHANCE_SCANS_ALL_OPTIONS); // set all enhancement detail flags
		return this.loadImagePdfPages(doc, pdfDoc, pdfBytes, flags, scaleFactor, null, pm);
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
		return this.loadImagePdfPages(doc, pdfDoc, pdfBytes, flags, scaleFactor, null, pm);
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
	 * @param pageIDs a set containing the IDs of the pages to decode
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @return the argument image markup document
	 * @throws IOException
	 */
	public ImDocument loadImagePdfPages(ImDocument doc, Document pdfDoc, byte[] pdfBytes, int flags, int scaleFactor, Set pageIDs, ProgressMonitor pm) throws IOException {
		
		//	check arguments
		pdfDoc = this.getPdfDocument(pdfDoc, pdfBytes);
		if (doc == null)
			doc = this.doCreateDocument(getChecksum(pdfBytes), ((pageIDs == null) ? 0 : pdfDoc.getNumberOfPages()), pageIDs);
		if (pm == null)
			pm = ProgressMonitor.dummy;
		
		//	decode flags
		boolean metaPages = ((flags & META_PAGES) != 0);
		boolean singlePages = ((flags & SINGLE_PAGE_SCANS) != 0);
		boolean doublePages = ((flags & DOUBLE_PAGE_SCANS) != 0);
		boolean enhanceScans = ((flags & ENHANCE_SCANS) != 0);
		int enhanceScanFlags = (enhanceScans ? ((flags >>> 8) & Imaging.ALL_OPTIONS) : 0);
		boolean useFixedDpi = ((flags & USE_FIXED_RESOLUTION) != 0);
		int fixedDpi = (useFixedDpi ? ((flags >>> 16) & 0xFFFF) : -1);
		
		//	load pages
		pm.setStep("Loading document page images");
		pm.setBaseProgress(0);
		pm.setProgress(0);
		pm.setMaxProgress(100);
		this.addImagePdfPages(doc, pdfDoc, pdfBytes, metaPages, singlePages, doublePages, fixedDpi, scaleFactor, pageIDs, enhanceScanFlags, false, pm, null);
		
		//	preserve source PDF in supplement
		ImSupplement.Source.createSource(doc, "application/pdf", pdfBytes);
		
		//	finally ...
		return doc;
	}
	
	private static class PImagePart {
		final int pageId;
		final String name;
		final Rectangle2D.Float pdfBounds;
		//final double pdfRotation;
		final PMatrix transform;
		final PStream data;
		final boolean isMask;
		final float rawDpi;
		final BoundingBox bounds;
//		PImagePart(int pageId, String name, Rectangle2D.Float pdfBounds, double pdfRotation, PStream data, boolean isMask, float rawDpi, BoundingBox bounds) {
//			this.pageId = pageId;
//			this.name = name;
//			this.pdfBounds = pdfBounds;
//			this.pdfRotation = pdfRotation;
//			this.data = data;
//			this.isMask = isMask;
//			this.rawDpi = rawDpi;
//			this.bounds = bounds;
//		}
		PImagePart(int pageId, String name, Rectangle2D.Float pdfBounds, PMatrix transform, PStream data, boolean isMask, float rawDpi, BoundingBox bounds) {
			this.pageId = pageId;
			this.name = name;
			this.pdfBounds = pdfBounds;
			this.transform = transform;
			this.data = data;
			this.isMask = isMask;
			this.rawDpi = rawDpi;
			this.bounds = bounds;
		}
	}
	
	private void addImagePdfPages(ImDocument doc, Document pdfDoc, byte[] pdfBytes, boolean metaPages, boolean singlePages, boolean doublePages, int fixedDpi, int scaleFactor, Set pageIDs, int enhanceScanFlags, boolean scanForWatermarks, ProgressMonitor pm, Map pageImageCache) throws IOException {
		
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
		PPageData[] pData = this.getPdfPageData(doc, getFigures, PdfFontDecoder.NO_DECODING, true, pageIDs, pageTree, objects, spm);
		
		//	add page images
		pData = this.addImagePdfPages(doc, pData, pageTree, objects, metaPages, singlePages, doublePages, fixedDpi, scaleFactor, enhanceScanFlags, scanForWatermarks, spm, pageImageCache);
	}
	
	private static int getNominalDPI(float rawDpi) {
		for (int i = 0; i < commonScannedPageImageDpi.length; i++) {
			if (Math.abs(rawDpi - commonScannedPageImageDpi[i]) < 3)
				return commonScannedPageImageDpi[i];
		}
		return (Math.round(rawDpi / 10) * 10);
	}
	
	private PPageData[] addImagePdfPages(final ImDocument doc, final PPageData[] pData, final PageTree pageTree, final Map objects, boolean metaPages, boolean singlePages, boolean doublePages, int fixedDpi, int scaleFactor, int enhanceScanFlags, boolean sanitizeEmbeddedOCR, final SynchronizedProgressMonitor spm, Map pageImageCache) throws IOException {
		
		//	extract page objects
		spm.setInfo("Getting page objects");
		final Page[] pdfPages = new Page[pageTree.getNumberOfPages()];
		for (int p = 0; p < pageTree.getNumberOfPages(); p++)
			pdfPages[p] = pageTree.getPage(p);
		spm.setInfo(" --> got " + pageTree.getNumberOfPages() + " page objects");
		
		//	prepare working in parallel
		ParallelFor pf;
		
		//	display figures if testing
		final ImageDisplayDialog idd = ((DEBUG_EXTRACT_FIGURES && (PdfExtractorTest.aimAtPage >= 0)) ? new ImageDisplayDialog("Images in Page " + PdfExtractorTest.aimAtPage) : null);
		
		//	extract page objects
		spm.setStep("Extracting page figures");
		spm.setBaseProgress(5);
		spm.setProgress(0);
		spm.setMaxProgress(13);
		final ArrayList[] pageImageParts = new ArrayList[pData.length];
		final float[] pageImagePartMinX = new float[pData.length];
		final float[] pageImagePartMaxX = new float[pData.length];
		final float[] pageImagePartMinY = new float[pData.length];
		final float[] pageImagePartMaxY = new float[pData.length];
		final float[] pageImagePartAreaSum = new float[pData.length];
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
				
				//	sanitize page figures before any further processing
				spm.setInfo("Sanitizing figures in page " + p + " of " + pData.length);
				sanitizePageFigures(pData[p]);
				
				//	collect all page figures
				pageImageParts[p] = new ArrayList();
				
				//	store figures / figure parts
				for (int f = 0; f < pData[p].figures.length; f++) {
					spm.setInfo("   - " + pData[p].figures[f]);
					this.addPageImagePart(p, pData[p].pdfPageBox, pData[p].rotate, pData[p].figures[f], false, pageImageParts[p], spm);
				}
				
				//	assess extent and area of page image parts
				pageImagePartMinX[p] = ((float) pData[p].pdfPageBox.getMaxX());
				pageImagePartMaxX[p] = 0;
				pageImagePartMinY[p] = ((float) pData[p].pdfPageBox.getMaxY());
				pageImagePartMaxY[p] = 0;
				pageImagePartAreaSum[p] = 0;
				for (int i = 0; i < pageImageParts[p].size(); i++) {
					PImagePart pip = ((PImagePart) pageImageParts[p].get(i));
					pageImagePartMinX[p] = Math.min(pageImagePartMinX[p], ((float) pip.pdfBounds.getMinX()));
					pageImagePartMaxX[p] = Math.max(pageImagePartMaxX[p], ((float) pip.pdfBounds.getMaxX()));
					pageImagePartMinY[p] = Math.min(pageImagePartMinY[p], ((float) pip.pdfBounds.getMinY()));
					pageImagePartMaxY[p] = Math.max(pageImagePartMaxY[p], ((float) pip.pdfBounds.getMaxY()));
					pageImagePartAreaSum[p] += ((float) Math.abs(pip.pdfBounds.getWidth() * pip.pdfBounds.getHeight()));
				}
				
				//	combined width of images in page less than 10% of page width, too little even for a single column overflowing in a multi-column layout 
				float pageImageWidth = (pageImagePartMaxX[p] - pageImagePartMinX[p]);
				if ((Math.abs(pageImageWidth) * 10) < Math.abs(pData[p].pdfPageBox.getWidth())) {
					spm.setInfo(" ==> Too narrow (" + pageImageWidth + " of " + Math.abs(pData[p].pdfPageBox.getWidth()) + "), even for a single column");
					pageImageParts[p] = null;
				}
			}
			
			private void addPageImagePart(int pageId, Rectangle2D.Float pageBounds, int rotate, PFigure pFigure, boolean isMask, ArrayList pImageParts, ProgressMonitor pm) throws IOException {
				
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
				float pFigureRawDpi = dpiRatio * defaultDpi;
				int pFigureDpi = getNominalDPI(pFigureRawDpi);
				pm.setInfo("     - resolution computed as " + pFigureDpi + " DPI (" + pFigureRawDpi + ")");
				
				//	convert bounds, as PDF Y coordinate is bottom-up, whereas Java, JavaScript, etc. Y coordinate is top-down
				BoundingBox pFigureBounds = getBoundingBox(pFigure.bounds, pageBounds, dpiRatio, rotate);
				
				//	store figure for selection and further processing
				PImagePart pip = new PImagePart(pageId, pFigure.name, pFigure.bounds, pFigure.transform, pFigureData, isMask, pFigureRawDpi, pFigureBounds);
				if (isMask)
					pImageParts.set((pImageParts.size() - 1), pip);
				else pImageParts.add(pip);
				
				//	show image if testing
				if (idd != null) {
//					PPageImage rPip = getPageImagePart(new PImagePart(pageId, pFigure.name, pFigure.bounds, pFigure.rotation, pFigureData, isMask, pFigureRawDpi, pFigureBounds), objects, pdfDoc, pdfDoc.getPageTree().getPage(PdfExtractorTest.aimAtPage, ""), spm);
//					PPageImage rPip = getPageImagePart(pip, objects, pdfDoc, pdfDoc.getPageTree().getPage(PdfExtractorTest.aimAtPage, ""), spm);
//					PPageImage rPip = getPageImagePart(pip, objects, pageTree.getPage(PdfExtractorTest.aimAtPage), spm);
					PPageImage rPip = getPageImagePart(pip, objects, pdfPages[PdfExtractorTest.aimAtPage], spm);
					idd.addImage(rPip.image, pFigure.name);
				}
				
				//	get mask images as well if given (simply recurse)
				//	==> more often than never, text comes as (binary) stencil mask overlaid on top of page
				if (pFigureData.params.containsKey("Mask")) {
					String pMaskFigureName = (pFigure.name + "_mask");
					pm.setInfo("   - adding mask figure");
					this.addPageImagePart(pageId, pageBounds, rotate, new PFigure(pFigure.renderOrderNumber, pMaskFigureName, pFigure.bounds, pFigure.clipPaths, null, pFigureData.params.get("Mask"), pFigure.transform), true, pImageParts, pm);
				}
				if (pFigureData.params.containsKey("SMask")) {
					String pMaskFigureName = (pFigure.name + "_smask");
					pm.setInfo("   - adding smask figure");
					this.addPageImagePart(pageId, pageBounds, rotate, new PFigure(pFigure.renderOrderNumber, pMaskFigureName, pFigure.bounds, pFigure.clipPaths, null, pFigureData.params.get("SMask"), pFigure.transform), true, pImageParts, pm);
				}
			}
		};
		ParallelJobRunner.runParallelFor(pf, ((PdfExtractorTest.aimAtPage < 0) ? 0 : PdfExtractorTest.aimAtPage), ((PdfExtractorTest.aimAtPage < 0) ? pData.length : (PdfExtractorTest.aimAtPage + 1)), (this.useMultipleCores ? -1 : 1));
		spm.setProgress(100);
		
		//	check errors
		checkException(pf);
		
		//	display figures if testing
		if (idd != null) {
			idd.setSize(600, 800);
			idd.setLocationRelativeTo(null);
			idd.setVisible(true);
		}
		
		//	assess result
		//	TODO add method parameter specifying whether or not we have a scan for sure (rather than a generic PDF whose type we're assessing)
		spm.setStep("Assessing page figures");
		spm.setBaseProgress(13);
		spm.setProgress(0);
		spm.setMaxProgress(15);
		assessPageImageParts(pData, pageImageParts, pageImagePartMinX, pageImagePartMaxX, pageImagePartMinY, pageImagePartMaxY, pageImagePartAreaSum, spm);
		
		//	assess overall result
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
		
		//	sanitize page content before we start moving words during scan enhancement
		if (sanitizeEmbeddedOCR) {
			
			//	remove words outside page boundaries
			for (int p = 0; p < pData.length; p++)
				filterOffPageWords(pData[p], spm);
			
			//	catch word sequences up or down vertical page edges, as well as ones across top or bottom ("Downloaded from ...")
			spm.setStep("Removing page edge watermarks");
			spm.setBaseProgress(15);
			spm.setProgress(0);
			spm.setMaxProgress(20);
			this.removePageEdgeWatermarks(pData, true, (noImagePageIDs.contains(new Integer(0)) ? noImagePageIDs.size() : 0), (noImagePageIDs.contains(new Integer(0)) ? 0 : noImagePageIDs.size()), new CascadingProgressMonitor(spm));
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
			pdfPageBoxes[p] = ((pData[p] == null) ? pageTree.getPage(p).getMediaBox() : pData[p].pdfPageBox);
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
		
		if ((fixedDpi <= 0) && ((overA4widthPageCount * 2) > pdfPageBoxes.length) && ((overA4heightPageCount * 2) > pdfPageBoxes.length)) {
			spm.setInfo("Page boxes seem to be out of range, scaling");
			int maxOverA5dpi = Integer.MAX_VALUE;
			for (int p = 0; p < pageTree.getNumberOfPages(); p++) {
				if (noImagePageIDs.contains(new Integer(p)))
					continue;
				for (int s = (scaleUpDpiSteps.length - 1); s >= 0; s--) {
					if ((pdfPageBoxes[p].height) > (a5inchHeigth * scaleUpDpiSteps[s])) {
						maxOverA5dpi = Math.min(maxOverA5dpi, scaleUpDpiSteps[s]);
						break;
					}
					else if ((pdfPageBoxes[p].width) > (a5inchWidth * ((portraitPageCount < landscapePageCount) ? 2 : 1) * scaleUpDpiSteps[s])) {
						maxOverA5dpi = Math.min(maxOverA5dpi, scaleUpDpiSteps[s]);
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
		if (doublePages)
			spData = this.addImagePdfPagesDouble(doc, scaleFactor, enhanceScanFlags, pdfPages, pdfPageBoxes, fixedDpi, sPdfPageBoxes, pData, pageImageParts, noImagePageIDs, objects, pageImageCache, spm);
		else spData = this.addImagePdfPagesSingle(doc, scaleFactor, enhanceScanFlags, pdfPages, pdfPageBoxes, fixedDpi, sPdfPageBoxes, pData, pageImageParts, noImagePageIDs, objects, pageImageCache, spm);
		
		//	garbage collect page images
		System.gc();
		
		//	finally ...
		return spData;
	}
	
	private static void assessPageImageParts(PPageData[] pData, ArrayList[] pageImageParts, float[] pipMinX, float[] pipMaxX, float[] pipMinY, float[] pipMaxY, float[] pipAreaSum, ProgressMonitor spm) {
		
		//	find last page with potential scans
		int firstPageWithPip = -1;
		int lastPageWithPip = -1;
		for (int p = 0; p < pData.length; p++)
			if (pageImageParts[p] != null) {
				if (firstPageWithPip == -1)
					firstPageWithPip = p;
				lastPageWithPip = p;
			}
		
		//	compute average combined margins of page image parts
		float pipLeftEvenMarginSum = 0;
		float pipLeftOddMarginSum = 0;
		float pipRightEvenMarginSum = 0;
		float pipRightOddMarginSum = 0;
		float pipTopMarginSum = 0;
		float pipBottomMarginSum = 0;
		int pipEvenPageCount = 0;
		int pipOddPageCount = 0;
		boolean lastPipPageEven = false;
		int pipPageCount = 0;
//		int pipAndWordPageCount = 0;
//		int pipPageWordCount = 0;
//		int inPipWordCount = 0;
		for (int p = 0; p < pData.length; p++) {
			if (pageImageParts[p] == null)
				continue;
//			
//			//	aggregate margins
			if ((p % 2) == 0)
				pipLeftEvenMarginSum += pipMinX[p];
			else  pipLeftOddMarginSum += pipMinX[p];
			if (p < lastPageWithPip) {
				if ((p % 2) == 0)
					pipRightEvenMarginSum += (pData[p].pdfPageBox.getWidth() - pipMaxX[p]);
				else pipRightOddMarginSum += (pData[p].pdfPageBox.getWidth() - pipMaxX[p]);
			}
			pipTopMarginSum += (pData[p].pdfPageBox.getHeight() - pipMaxY[p]);
			if (p < lastPageWithPip)
				pipBottomMarginSum += pipMinY[p];
			if ((p % 2) == 0)
				pipEvenPageCount++;
			else pipOddPageCount++;
			lastPipPageEven = ((p % 2) == 0);
			pipPageCount++;
//			
//			//	aggregate words inside page image (if any)
//			if ((pData[p].words == null) || (pData[p].words.length == 0))
//				continue;
//			pipAndWordPageCount++;
//			for (int w = 0; w < pData[p].words.length; w++) {
//				pipPageWordCount++;
//				double wCenterX = (pData[p].words[w].bounds.getMinX() + (pData[p].words[w].bounds.getWidth() / 2));
//				if ((wCenterX < pipMinX[p]) || (pipMaxX[p] < wCenterX))
//					continue;
//				double wCenterY = (pData[p].words[w].bounds.getMinY() + (pData[p].words[w].bounds.getHeight() / 2));
//				if ((wCenterY < pipMinY[p]) || (pipMaxY[p] < wCenterY))
//					continue;
//				inPipWordCount++;
//			}
		}
		float avgPipLeftEvenMargin = ((pipEvenPageCount == 0) ? -1 : (pipLeftEvenMarginSum / pipEvenPageCount));
		float avgPipLeftOddMargin = ((pipOddPageCount == 0) ? -1 : (pipLeftOddMarginSum / pipOddPageCount));
		float avgPipRightEvenMargin = ((pipEvenPageCount < (lastPipPageEven ? 2 : 1)) ? -1 : (pipRightEvenMarginSum / (pipEvenPageCount - (lastPipPageEven ? 1 : 0))));
		float avgPipRightOddMargin = ((pipOddPageCount < (lastPipPageEven ? 1 : 2)) ? -1 : (pipRightOddMarginSum / (pipOddPageCount - (lastPipPageEven ? 0 : 1))));
		float avgPipTopMargin = ((pipPageCount == 0) ? -1 : (pipTopMarginSum / pipPageCount));
		float avgPipBottomMargin = ((pipPageCount < 2) ? -1 : (pipBottomMarginSum / (pipPageCount - 1)));
		
		//	identify actual scans
		int scanBySizeCount = 0;
		int scanByRegularityCount = 0;
		for (int p = 0; p < pData.length; p++) {
			if (pageImageParts[p] == null)
				continue;
			spm.setInfo("Assessing figures in page " + p + " of " + pData.length + " for scans");
			spm.setProgress((p * 100) / pData.length);
			
			//	we have two ways of identifying scans: by sheer size, and by position, and by regularity of position
			int scanBySizeScore = 0;
			int scanByPositionScore = 0;
			int scanByRegularityScore = 0;
			
			//	compute combined dimensions of images
			float pageImageWidth = (pipMaxX[p] - pipMinX[p]);
			float pageImageHeight = (pipMaxY[p] - pipMinY[p]);
			
			//	combined width of images in page less than 85% of page width, too little for a scan
			if ((Math.abs(pageImageWidth) * 20) < (Math.abs(pData[p].pdfPageBox.getWidth()) * 17))
				spm.setInfo(" --> Too narrow (" + pageImageWidth + " of " + Math.abs(pData[p].pdfPageBox.getWidth()) + "), not a scan by size");
			else scanBySizeScore++;
			
			//	combined height of images in page less than 85% of page height, too little for a scan
			if ((Math.abs(pageImageHeight) * 20) < (Math.abs(pData[p].pdfPageBox.getHeight()) * 17))
				spm.setInfo(" --> Too low (" + pageImageHeight + " of " + Math.abs(pData[p].pdfPageBox.getHeight()) + "), not a scan by size");
			else scanBySizeScore++;
			
			//	combined area of images in page less than 80% of page area, too little for a scan
			if ((Math.abs(pipAreaSum[p]) * 10) < (Math.abs(pData[p].pdfPageBox.getWidth() * pData[p].pdfPageBox.getHeight()) * 8))
				spm.setInfo(" --> Too small (" + pipAreaSum[p] + " of " + Math.abs(pData[p].pdfPageBox.getWidth() * pData[p].pdfPageBox.getHeight()) + "), not a scan by size");
			else scanBySizeScore++;
			
			//	what do we have?
			spm.setInfo(" ==> score by size is " + scanBySizeScore);
			
			//	compute page margins of combined images (in PDF dimensions, i.e., top is at maximum Y)
			float pipLeftMargin = pipMinX[p];
			float avgPipLeftMargin = (((p % 2) == 0) ? avgPipLeftEvenMargin : avgPipLeftOddMargin);
			float pipRightMargin = ((float) (Math.abs(pData[p].pdfPageBox.getWidth()) - pipMaxX[p]));
			float avgPipRightMargin = (((p % 2) == 0) ? avgPipRightEvenMargin : avgPipRightOddMargin);
			float pipTopMargin = ((float) (Math.abs(pData[p].pdfPageBox.getHeight()) - pipMaxY[p]));
			float pipBottomMargin = pipMinY[p];
			
			//	check if edges within 20% of page edges (waive right and bottom edge for last page, though)
			if ((pipLeftMargin * 5) > Math.abs(pData[p].pdfPageBox.getWidth()))
				spm.setInfo(" --> Too far off left page edge (" + pipLeftMargin + " in " + Math.abs(pData[p].pdfPageBox.getWidth()) + "), not a scan by position");
			else scanByPositionScore++;
			if ((p < lastPageWithPip) && ((pipRightMargin * 5) > Math.abs(pData[p].pdfPageBox.getWidth())))
				spm.setInfo(" --> Too far off right page edge (" + pipRightMargin + " in " + Math.abs(pData[p].pdfPageBox.getWidth()) + "), not a scan by position");
			else scanByPositionScore++;
			if ((pipTopMargin * 5) > Math.abs(pData[p].pdfPageBox.getHeight()))
				spm.setInfo(" --> Too far off top page edge (" + pipTopMargin + " in " + Math.abs(pData[p].pdfPageBox.getHeight()) + "), not a scan by position");
			else scanByPositionScore++;
			if ((p < lastPageWithPip) && ((pipBottomMargin * 5) > Math.abs(pData[p].pdfPageBox.getHeight())))
				spm.setInfo(" --> Too far off bottom page edge (" + pipBottomMargin + " in " + Math.abs(pData[p].pdfPageBox.getHeight()) + "), not a scan by position");
			else scanByPositionScore++;
			spm.setInfo(" ==> score by position is " + scanByPositionScore);
//			
//			//	check if opposing margins are within 20% of one another(waive on last page, though)
//			//	TODO_ne check if these two catches don't backfire ==> they do
//			else if ((p < lastPageWithPageImagePart) && ((Math.abs(pipLeftMargin - pipRightMargin) * 5) > ((pipLeftMargin + pipRightMargin) / 2))) {
//				spm.setInfo(" ==> Too irregular left and right page margins (" + pipLeftMargin + " vs " + pipRightMargin + "), not a scan by regularity");
//				isScanByReglarity = false;
//			}
//			else if ((p < lastPageWithPageImagePart) && ((Math.abs(pipTopMargin - pipBottomMargin) * 5) > ((pipTopMargin + pipBottomMargin) / 2))) {
//				spm.setInfo(" ==> Too irregular top and bottom page margins (" + pipTopMargin + " vs " + pipBottomMargin + "), not a scan by regularity");
//				isScanByReglarity = false;
//			}
			
			//	check if margins are within either 10% or some DPI based absolute threshold of cross-page average (ignore right and bottom margins of last page, though, as well as top margin on first page)
			if (((Math.abs(pipLeftMargin - avgPipLeftMargin) * 10) > avgPipLeftMargin) && (Math.abs(pipLeftMargin - avgPipLeftMargin) > (pData[p].rawPageImageDpi / 25)))
				spm.setInfo(" --> Too irregular left page margin (" + pipLeftMargin + " vs average " + avgPipLeftMargin + "), not a scan by regularity");
			else scanByRegularityScore++;
			if ((p < lastPageWithPip) && ((Math.abs(pipRightMargin - avgPipRightMargin) * 10) > avgPipRightMargin) && (Math.abs(pipRightMargin - avgPipRightMargin) > (pData[p].rawPageImageDpi / 25)))
				spm.setInfo(" --> Too irregular right page margin (" + pipRightMargin + " vs average " + avgPipRightMargin + "), not a scan by regularity");
			else scanByRegularityScore++;
			if ((p != firstPageWithPip) && ((Math.abs(pipTopMargin - avgPipTopMargin) * 10) > avgPipTopMargin) && (Math.abs(pipTopMargin - avgPipTopMargin) > (pData[p].rawPageImageDpi / 25)))
				spm.setInfo(" --> Too irregular top page margin (" + pipTopMargin + " vs average " + avgPipTopMargin + "), not a scan by regularity");
			else scanByRegularityScore++;
			if ((p < lastPageWithPip) && ((Math.abs(pipBottomMargin - avgPipBottomMargin) * 10) > avgPipBottomMargin) && (Math.abs(pipBottomMargin - avgPipBottomMargin) > (pData[p].rawPageImageDpi / 25)))
				spm.setInfo(" --> Too irregular bottom page margin (" + pipBottomMargin + " vs average " + avgPipBottomMargin + "), not a scan by regularity");
			else scanByRegularityScore++;
			spm.setInfo(" ==> score by regularity is " + scanByRegularityScore);
			
			//	full match by size or regularity, should be OK
			if (scanBySizeScore == 3) {
				scanBySizeCount++;
				continue;
			}
			else if (scanByRegularityScore == 4) {
				scanByRegularityCount++;
				continue;
			}
			
			//	allow tolerance of one for each criterion
			boolean isScanBySize = (2 <= scanBySizeScore);
			boolean isScanByPosition = (3 <= scanByPositionScore);
			boolean isScanByRegularity = (3 <= scanByRegularityScore);
			
			//	position OK, as well as either of size and regularity
			if (isScanByPosition) {
				if (isScanBySize) {
					scanBySizeCount++;
					continue;
				}
				else if (isScanByRegularity) {
					scanByRegularityCount++;
					continue;
				}
			}
			
			//	accept regardless of page image position and size if 95% of page words (if any) contained in image
			if ((pData[p].words != null) && (pData[p].words.length != 0)) {
				int inPipWordCount = 0;
				for (int w = 0; w < pData[p].words.length; w++) {
					double wCenterX = (pData[p].words[w].bounds.getMinX() + (pData[p].words[w].bounds.getWidth() / 2));
					if ((wCenterX < pipMinX[p]) || (pipMaxX[p] < wCenterX))
						continue;
					double wCenterY = (pData[p].words[w].bounds.getMinY() + (pData[p].words[w].bounds.getHeight() / 2));
					if ((wCenterY < pipMinY[p]) || (pipMaxY[p] < wCenterY))
						continue;
					inPipWordCount++;
				}
				if ((inPipWordCount * 20) > (pData[p].words.length * 19))
					continue;
			}
			
			//	no way this page has a scan in it
			pageImageParts[p] = null;
		}
		
		//	show final tally
		spm.setInfo(" ==> found " + scanBySizeCount + " scans by size, " + scanByRegularityCount + " scans by regularity");
	}
	
	private PPageData[] addImagePdfPagesSingle(final ImDocument doc, final int scaleFactor, final int enhanceScanFlags, final Page[] pdfPages, final Rectangle2D.Float[] pdfPageBoxes, final int fixedDpi, final Rectangle2D.Float[] sPdfPageBoxes, final PPageData[] pData, final ArrayList[] pageImageParts, final Set noImagePageIDs, final Map objects, final Map pageImageCache, final SynchronizedProgressMonitor spm) throws IOException {
		
		//	extract & save page images
		final BoundingBox[] pageBoxes = new BoundingBox[pdfPages.length];
		ParallelFor pf = new ParallelFor() {
			public void doFor(int p) throws Exception {
				
				//	nothing to work with
				if ((pData[p] == null) || (pageImageParts[p] == null))
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
				int exPageImageDpi = -1;
				if (imageStore.isPageImageAvailable(doc.docId, (p - pDelta))) {
					spm.setInfo(" --> image already extracted");
					spm.setInfo(" - getting image data ...");
					PageImageInputStream piis = imageStore.getPageImageAsStream(doc.docId, (p - pDelta));
					piis.close();
					
					//	check DPI to exclude page images from earlier text based import (can happen due to embedded OCR)
					if (piis.currentDpi != defaultTextPdfPageImageDpi) {
						pageBoxes[p] = new BoundingBox(0, ((piis.originalWidth * piis.currentDpi) / piis.originalDpi), 0, ((piis.originalHeight * piis.currentDpi) / piis.originalDpi));
						spm.setInfo(" - resolution is " + piis.currentDpi + " DPI, page bounds are " + pageBoxes[p].toString());
						//return; // still need to add scan !!!
						exPageImageDpi = piis.currentDpi;
					}
				}
				
				//	get raw image
				PPageImage pPageImage = getPageImage(pageImageParts[p], objects, pdfPages[p], pdfPageBoxes[p], spm);
				if (pPageImage == null) {
					spm.setInfo(" --> page image generation failed");
					throw new IOException("Could not generate image for page " + p);
				}
				spm.setInfo(" - got page image sized " + pPageImage.image.getWidth() + " x " + pPageImage.image.getHeight());
				spm.setInfo(" - figure bounds are " + pPageImage.bounds);
				
				//	get page image and resolution
				BufferedImage pageImage = pPageImage.image;
				float rawDpi;
				int dpi;
				
				//	we have an existing page image, only need to attach scan
				if (exPageImageDpi != -1) {
					rawDpi = exPageImageDpi;
					dpi = exPageImageDpi;
					pData[p].rawPageImageDpi = exPageImageDpi;
				}
				
				//	compute DPI based on scan figure bounds
				else if (fixedDpi <= 0) {
					int dpiScaleFactor = scaleFactor;
					rawDpi = pPageImage.getRawDpi(null, (PdfExtractorTest.aimAtPage != -1));
					if ((rawDpi < 100) && (sPdfPageBoxes[p] != null)) {
						rawDpi = pPageImage.getRawDpi(sPdfPageBoxes[p], (PdfExtractorTest.aimAtPage != -1));
						spm.setInfo(" - using scaled resolution");
					}
					dpi = (getNominalDPI(rawDpi) / dpiScaleFactor);
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
				}
				
				//	nothing to compute here
				else {
					rawDpi = fixedDpi;
					dpi = fixedDpi;
					pData[p].rawPageImageDpi = fixedDpi;
				}
				
				//	if image is beyond 400 DPI, scale to half of that
				if ((dpi >= minScaleDownDpi) && (exPageImageDpi == -1)) {
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
//					pData[p].pdfPageBox.x /= scaleDownFactor;
//					pData[p].pdfPageBox.y /= scaleDownFactor;
//					pData[p].pdfPageBox.width /= scaleDownFactor;
//					pData[p].pdfPageBox.height /= scaleDownFactor;
//					if (pData[p].words != null)
//						for (int w = 0; w < pData[p].words.length; w++) {
//							pData[p].words[w].bounds.x /= scaleDownFactor;
//							pData[p].words[w].bounds.y /= scaleDownFactor;
//							pData[p].words[w].bounds.width /= scaleDownFactor;
//							pData[p].words[w].bounds.height /= scaleDownFactor;
//						}
				}
				
				//	preserve original page image as supplement
				ImSupplement.Scan scan;
				synchronized (doc) {
					scan = ImSupplement.Scan.createScan(doc, (p - pDelta), 0, dpi, pageImage);
				}
				
				//	we' extracted that page image before, only needed the scan
				if (exPageImageDpi != -1)
					return;
				
				//	enhance image (cannot use cache here, as image might change during correction)
				if (enhanceScanFlags != 0) {
					AnalysisImage ai = Imaging.wrapImage(pageImage, null);
					spm.setInfo(" - enhancing image ...");
					BoundingBox[] ocrWordBounds = null;
					BoundingBox[] checkOcrWordBounds = null;
					float magnification = (((float) dpi) / defaultDpi);
					if ((pData[p].words != null) && (pData[p].words.length != 0)) {
						ocrWordBounds = new BoundingBox[pData[p].words.length];
						checkOcrWordBounds = new BoundingBox[pData[p].words.length];
						for (int w = 0; w < pData[p].words.length; w++) {
							BoundingBox wb = getBoundingBox(pData[p].words[w].bounds, pData[p].pdfPageBox, magnification, pData[p].rotate);
							ocrWordBounds[w] = wb;
							checkOcrWordBounds[w] = wb;
						}
					}
					ai = Imaging.correctImage(ai, dpi, ocrWordBounds, enhanceScanFlags, spm);
					if (ocrWordBounds != null)
						for (int w = 0; w < pData[p].words.length; w++) {
							if (ocrWordBounds[w] == checkOcrWordBounds[w])
								continue; // nothing changed
							Rectangle2D.Float wb = getBounds(ocrWordBounds[w], pData[p].pdfPageBox, magnification, pData[p].rotate);
							pData[p].words[w].bounds.setRect(wb);
						}
					if (ai.getRotatedBy() != 0) {
						BufferedImage scanImage = ImageIO.read(scan.getInputStream());
						scanImage = Imaging.rotateImage(scanImage, ai.getRotatedBy());
						synchronized (doc) {
							scan = ImSupplement.Scan.createScan(doc, (p - pDelta), 0, dpi, scanImage);
						}
					}
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
		checkException(pf);
		
		//	assemble document and shift pages
		int pDelta = (noImagePageIDs.contains(new Integer(0)) ? noImagePageIDs.size() : 0);
		PPageData[] spData = ((pDelta == 0) ? pData : new PPageData[pData.length - pDelta]);
		for (int p = 0; p < pdfPageBoxes.length; p++) {
			if ((PdfExtractorTest.aimAtPage != -1) && (PdfExtractorTest.aimAtPage != p))
				continue;
			
			//	nothing to work with
			if ((pData[p] == null) || (pageBoxes[p] == null))
				continue;
			
			//	adjust to ignore meta pages
			if (noImagePageIDs.contains(new Integer(p)))
				continue;
			
			//	create page
			ImPage page = new ImPage(doc, (p - pDelta), pageBoxes[p]);
			
			//	shift page data (words need to be in line with page IDs)
			spData[page.pageId] = pData[p];
		}
		
		//	finally ...
		spm.setProgress(100);
		return spData;
	}
	
	private PPageData[] addImagePdfPagesDouble(final ImDocument doc, final int scaleFactor, final int enhanceScanFlags, final Page[] pdfPages, final Rectangle2D.Float[] pdfPageBoxes, final int fixedDpi, final Rectangle2D.Float[] sPdfPageBoxes, final PPageData[] pData, final ArrayList[] pageImageParts, final Set noImagePageIDs, final Map objects, final Map pageImageCache, final SynchronizedProgressMonitor spm) throws IOException {
		
		//	extract & save page images
		final BoundingBox[] pageBoxes = new BoundingBox[pdfPages.length * 2];
		ParallelFor pf = new ParallelFor() {
			public void doFor(int pp) throws Exception {
				
				//	nothing to work with
				if ((pData[pp] == null) || (pageImageParts[pp] == null))
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
				int exPageDoubleImageDpi = -1;
				if (imageStore.isPageImageAvailable(doc.docId, ((pp * 2) - ppDelta)) && imageStore.isPageImageAvailable(doc.docId, ((pp * 2) - ppDelta + 1))) {
					spm.setInfo(" --> image halves already extracted");
					spm.setInfo(" - getting image data ...");
					PageImageInputStream piisLeft = imageStore.getPageImageAsStream(doc.docId, ((pp * 2) - ppDelta));
					piisLeft.close();
					PageImageInputStream piisRight = imageStore.getPageImageAsStream(doc.docId, ((pp * 2) - ppDelta + 1));
					piisRight.close();
					
					//	check DPI to exclude page images from earlier text based import (can happen due to embedded OCR)
					if ((piisLeft.currentDpi != defaultTextPdfPageImageDpi) && (piisRight.currentDpi != defaultTextPdfPageImageDpi)) {
						pageBoxes[pp * 2] = new BoundingBox(0, ((piisLeft.originalWidth * piisLeft.currentDpi) / piisLeft.originalDpi), 0, ((piisLeft.originalHeight * piisLeft.currentDpi) / piisLeft.originalDpi));
						spm.setInfo(" - resolution left is " + piisLeft.currentDpi + " DPI, page bounds are " + pageBoxes[pp * 2].toString());
						pageBoxes[(pp * 2) + 1] = new BoundingBox(0, ((piisRight.originalWidth * piisRight.currentDpi) / piisRight.originalDpi), 0, ((piisRight.originalHeight * piisRight.currentDpi) / piisRight.originalDpi));
						spm.setInfo(" - resolution right is " + piisRight.currentDpi + " DPI, page bounds are " + pageBoxes[(pp * 2) + 1].toString());
						//return; // still need to add scans !!!
						exPageDoubleImageDpi = piisLeft.currentDpi;
					}
				}
				
				//	get raw image
				PPageImage pDoublePageImage = getPageImage(pageImageParts[pp], objects, pdfPages[pp], pdfPageBoxes[pp], spm);
				if (pDoublePageImage == null) {
					spm.setInfo(" --> page image generation failed");
					throw new IOException("Could not generate image for double page " + pp);
				}
				spm.setInfo(" - got double page image sized " + pDoublePageImage.image.getWidth() + " x " + pDoublePageImage.image.getHeight());
				spm.setInfo(" - figure bounds are " + pdfPageBoxes[pp]);
				
				//	get double page image and resolution
				BufferedImage doublePageImage = pDoublePageImage.image;
				float rawDpi;
				int dpi;
				
				//	we have an existing page image, only need to attach scan
				if (exPageDoubleImageDpi != -1) {
					rawDpi = exPageDoubleImageDpi;
					dpi = exPageDoubleImageDpi;
					pData[pp].rawPageImageDpi = exPageDoubleImageDpi;
				}
				
				//	compute DPI based on scan figure bounds for this
				else if (fixedDpi <= 0) {
					int dpiScaleFactor = scaleFactor;
					rawDpi = pDoublePageImage.getRawDpi(null, (PdfExtractorTest.aimAtPage != -1));
					if ((rawDpi < 100) && (sPdfPageBoxes[pp] != null)) {
						rawDpi = pDoublePageImage.getRawDpi(sPdfPageBoxes[pp], (PdfExtractorTest.aimAtPage != -1));
						spm.setInfo(" - using scaled resolution");
					}
					dpi = (getNominalDPI(rawDpi) / dpiScaleFactor);
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
				}
				
				//	nothing to compute here
				else {
					rawDpi = fixedDpi;
					dpi = fixedDpi;
					pData[pp].rawPageImageDpi = fixedDpi;
				}
				
				//	if image is beyond 400 DPI, scale to half of that
				if ((dpi >= minScaleDownDpi) && (exPageDoubleImageDpi != -1)) {
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
				ImSupplement.Scan scanLeft;
				ImSupplement.Scan scanRight;
				synchronized (doc) {
					scanLeft = ImSupplement.Scan.createScan(doc, ((pp * 2) - ppDelta), 0, dpi, pageImageLeft);
					scanRight = ImSupplement.Scan.createScan(doc, ((pp * 2) - ppDelta + 1), 0, dpi, pageImageRight);
				}
				
				//	we' extracted that page image before, only needed the scan
				if (exPageDoubleImageDpi != -1)
					return;
						
				//	enhance image halves (cannot use cache here, as image might change during correction)
				if (enhanceScanFlags != 0) {
					float magnification = (((float) dpi) / defaultDpi);
					
					AnalysisImage aiLeft = Imaging.wrapImage(pageImageLeft, null);
					spm.setInfo(" - enhancing left image half ...");
					BoundingBox[] ocrWordBoundsLeft = null;
					BoundingBox[] checkOcrWordBoundsLeft = null;
					if ((pData[pp].words != null) && (pData[pp].words.length != 0)) {
						ocrWordBoundsLeft = new BoundingBox[pData[pp].words.length];
						checkOcrWordBoundsLeft = new BoundingBox[pData[pp].words.length];
						for (int w = 0; w < pData[pp].words.length; w++) {
							if ((pData[pp].pdfPageBox.getWidth() / 2) < pData[pp].words[w].bounds.getMinX())
								continue; // word in right page half
							BoundingBox wb = getBoundingBox(pData[pp].words[w].bounds, pData[pp].pdfPageBox, magnification, pData[pp].rotate);
							ocrWordBoundsLeft[w] = wb;
							checkOcrWordBoundsLeft[w] = wb;
						}
					}
					aiLeft = Imaging.correctImage(aiLeft, dpi, ocrWordBoundsLeft, enhanceScanFlags, spm);
					if (ocrWordBoundsLeft != null)
						for (int w = 0; w < pData[pp].words.length; w++) {
							if (ocrWordBoundsLeft[w] == checkOcrWordBoundsLeft[w])
								continue; // nothing changed (null or not)
							Rectangle2D.Float wb = getBounds(ocrWordBoundsLeft[w], pData[pp].pdfPageBox, magnification, pData[pp].rotate);
							pData[pp].words[w].bounds.setRect(wb);
						}
					if (aiLeft.getRotatedBy() != 0) {
						BufferedImage scanImageLeft = ImageIO.read(scanLeft.getInputStream());
						scanImageLeft = Imaging.rotateImage(scanImageLeft, aiLeft.getRotatedBy());
						synchronized (doc) {
							scanLeft = ImSupplement.Scan.createScan(doc, ((pp * 2) - ppDelta), 0, dpi, scanImageLeft);
						}
					}
					pageImageLeft = aiLeft.getImage();
					
					AnalysisImage aiRight = Imaging.wrapImage(pageImageRight, null);
					spm.setInfo(" - enhancing right image half ...");
					BoundingBox[] ocrWordBoundsRight = null;
					BoundingBox[] checkOcrWordBoundsRight = null;
					if ((pData[pp].words != null) && (pData[pp].words.length != 0)) {
						ocrWordBoundsRight = new BoundingBox[pData[pp].words.length];
						checkOcrWordBoundsRight = new BoundingBox[pData[pp].words.length];
						for (int w = 0; w < pData[pp].words.length; w++) {
							if (pData[pp].words[w].bounds.getMaxX() < (pData[pp].pdfPageBox.getWidth() / 2))
								continue; // word in left page half
							BoundingBox wb = getBoundingBox(pData[pp].words[w].bounds, pData[pp].pdfPageBox, magnification, pData[pp].rotate);
							wb = wb.translate(-(doublePageImage.getWidth() / 2), 0);
							ocrWordBoundsRight[w] = wb;
							checkOcrWordBoundsRight[w] = wb;
						}
					}
					aiRight = Imaging.correctImage(aiRight, dpi, ocrWordBoundsRight, enhanceScanFlags, spm);
					if (ocrWordBoundsRight != null)
						for (int w = 0; w < pData[pp].words.length; w++) {
							if (ocrWordBoundsRight[w] == checkOcrWordBoundsRight[w])
								continue; // nothing changed (null or not)
							ocrWordBoundsRight[w] = ocrWordBoundsRight[w].translate((doublePageImage.getWidth() / 2), 0);
							Rectangle2D.Float wb = getBounds(ocrWordBoundsRight[w], pData[pp].pdfPageBox, magnification, pData[pp].rotate);
							pData[pp].words[w].bounds.setRect(wb);
						}
					if (aiRight.getRotatedBy() != 0) {
						BufferedImage scanImageRight = ImageIO.read(scanRight.getInputStream());
						scanImageRight = Imaging.rotateImage(scanImageRight, aiRight.getRotatedBy());
						synchronized (doc) {
							scanRight = ImSupplement.Scan.createScan(doc, ((pp * 2) - ppDelta + 1), 0, dpi, scanImageRight);
						}
					}
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
		checkException(pf);
		
		//	assemble document (two pages per PDF page here), and split up page data
		PPageData[] spData = new PPageData[pdfPages.length * 2];
		for (int pp = 0; pp < pdfPages.length; pp++) {
			if ((PdfExtractorTest.aimAtPage != -1) && (PdfExtractorTest.aimAtPage != pp))
				continue;
			
			//	nothing to work with
			if ((pData[pp] == null) || (pageBoxes[pp * 2] == null) || (pageBoxes[(pp * 2) + 1] == null))
				continue;
			
			//	adjust to ignore meta pages
			if (noImagePageIDs.contains(new Integer(pp)))
				continue;
			int ppDelta = (noImagePageIDs.contains(new Integer(0)) ? noImagePageIDs.size() : 0);
			
			//	create pages
			ImPage pageLeft = new ImPage(doc, ((pp * 2) - ppDelta), pageBoxes[pp * 2]);
			pageLeft.getClass(); // just to silence the 'never used' ...
			ImPage pageRight = new ImPage(doc, ((pp * 2) - ppDelta + 1), pageBoxes[(pp * 2) + 1]);
			pageRight.getClass(); // just to silence the 'never used' ...
			
			//	split up page data (especially embedded OCR words)
			ArrayList wordsLeft = new ArrayList();
			ArrayList wordsRight = new ArrayList();
			for (int w = 0; w < pData[pp].words.length; w++) {
				if (pData[pp].words[w].bounds.getMaxX() < (pData[pp].pdfPageBox.getWidth() / 2))
					wordsLeft.add(pData[pp].words[w]);
				else if (pData[pp].words[w].bounds.getMinX() > (pData[pp].pdfPageBox.getWidth() / 2))
					wordsRight.add(new PWord(pData[pp].words[w].renderOrderNumber, pData[pp].words[w].charCodes, pData[pp].words[w].str, pData[pp].words[w].baseline, new Rectangle2D.Float(
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
				else if (pData[pp].figures[f].bounds.getMinX() > (pData[pp].pdfPageBox.getWidth() / 2)) {
					PPath[] fClipPaths = pData[pp].figures[f].clipPaths;
					if (fClipPaths != null) {
						for (int p = 0; p < fClipPaths.length; p++)
							fClipPaths[p] = fClipPaths[p].translate(0, ((float) -(pData[pp].pdfPageBox.getWidth() / 2)));
					}
//					figuresRight.add(new PFigure(pData[pp].figures[f].renderOrderNumber, pData[pp].figures[f].name, new Rectangle2D.Float(
//							((float) (pData[pp].figures[f].bounds.getMinX() - (pData[pp].pdfPageBox.getWidth() / 2))),
//							((float) pData[pp].figures[f].bounds.getMinY()),
//							((float) pData[pp].figures[f].bounds.getWidth()),
//							((float) pData[pp].figures[f].bounds.getHeight())
//						), fClipPaths, pData[pp].figures[f].refOrData, pData[pp].figures[f].rotation, pData[pp].figures[f].rightSideLeft, pData[pp].figures[f].upsideDown));
					figuresRight.add(new PFigure(pData[pp].figures[f].renderOrderNumber, pData[pp].figures[f].name, new Rectangle2D.Float(
							((float) (pData[pp].figures[f].bounds.getMinX() - (pData[pp].pdfPageBox.getWidth() / 2))),
							((float) pData[pp].figures[f].bounds.getMinY()),
							((float) pData[pp].figures[f].bounds.getWidth()),
							((float) pData[pp].figures[f].bounds.getHeight())
						), fClipPaths, null, pData[pp].figures[f].refOrData, pData[pp].figures[f].transform));
				}
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
	
	private PPageImage getPageImage(ArrayList pageImageParts, Map objects, Page pdfPage, Rectangle2D.Float pdfPageBox, ProgressMonitor spm) throws IOException {
		
		//	sort out figures overpainted later
		for (int p = 0; p < pageImageParts.size(); p++) {
			PImagePart pip = ((PImagePart) pageImageParts.get(p));
			for (int cp = (p+1); cp < pageImageParts.size(); cp++) {
				PImagePart cpip = ((PImagePart) pageImageParts.get(cp));
				if (cpip.isMask)
					continue; // mask images don't overpaint
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
			return this.getPageImagePart(((PImagePart) pageImageParts.get(0)), objects, pdfPage, spm);
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
			if (pip.rawDpi < (defaultDpi / 2)) {
				spm.setInfo(" - skipping low resolution page image part sized " + (pip.bounds.right - pip.bounds.left) + "x" + (pip.bounds.bottom - pip.bounds.top) + " at " + pip.rawDpi);
				continue;
			}
			AffineTransform at = piGraphics.getTransform();
			BoundingBox pipBounds = getBoundingBox(pip.pdfBounds, pdfPageBox, (maxPipDpi / defaultDpi), 0);
			piGraphics.translate(pipBounds.left, pipBounds.top);
			if (Math.abs(pip.rawDpi - maxPipDpi) > 0.001 /* cut scaling a fraction of slack */)
				piGraphics.scale((((double) maxPipDpi) / pip.rawDpi), (((double) maxPipDpi) / pip.rawDpi));
			spm.setInfo(" - rendering page image part sized " + (pip.bounds.right - pip.bounds.left) + "x" + (pip.bounds.bottom - pip.bounds.top) + " at " + pip.rawDpi + " DPI scaled by " + (((double) maxPipDpi) / pip.rawDpi));
			PPageImage rPip = this.getPageImagePart(pip, objects, pdfPage, spm);
			piGraphics.drawImage(rPip.image, 0, 0, null);
			piGraphics.setTransform(at);
		}
		
		//	finally ...
		piGraphics.dispose();
		return new PPageImage(pageImage, pdfPageBox);
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
	
	private static final byte[] maskImageColorModelRgb = {((byte) 0xFF), ((byte) 0x00)};
	private static final byte[] maskImageColorModelAlpha = {((byte) 0x00), ((byte) 0xFF)};
	private static final IndexColorModel maskImageColorModel = new IndexColorModel(1, 2, maskImageColorModelRgb, maskImageColorModelRgb, maskImageColorModelRgb, maskImageColorModelAlpha);
	private PPageImage getPageImagePart(PImagePart pip, Map objects, Page pdfPage, ProgressMonitor spm) throws IOException {
		
		//	decode PStream to image
		BufferedImage pipBi = decodeImage(pdfPage, pip.data.bytes, pip.data.params, objects, false, false);
		if (pipBi == null) {
			spm.setInfo("   --> could not decode figure");
			return null;
		}
		spm.setInfo("     - got figure sized " + pipBi.getWidth() + " x " + pipBi.getHeight());
		
		//	interpret figure rotation (if above some 0.1°)
//		if (Math.abs(pip.pdfRotation) > 0.0005) {
//			pipBi = Imaging.rotateImage(pipBi, pip.pdfRotation);
//			spm.setInfo("     - figure rotated by " + ((180.0 / Math.PI) * pip.pdfRotation) + "°");
//		}
		if (Math.abs(pip.transform.getRotation()) > 0.0005) {
			pipBi = Imaging.rotateImage(pipBi, pip.transform.getRotation());
			spm.setInfo("     - figure rotated by " + ((180.0 / Math.PI) * pip.transform.getRotation()) + "°");
			spm.setInfo("     - figure now sized " + pipBi.getWidth() + " x " + pipBi.getHeight());
		}
		
		//	make white transparent on mask images
		if (pip.isMask) {
			if (pipBi.getColorModel().hasAlpha()) {
				for (int c = 0; c < pipBi.getWidth(); c++)
					for (int r = 0; r < pipBi.getHeight(); r++) {
						int rgb = pipBi.getRGB(c, r);
						if ((rgb & 0xFF) < 128)
							continue; // blue too dark for white
						if (((rgb >> 8) & 0xFF) < 128)
							continue; // green too dark for white
						if (((rgb >> 16) & 0xFF) < 128)
							continue; // red too dark for white
						pipBi.setRGB(c, r, 0x00FFFFFF); // set to white with 0 alpha
					}
				spm.setInfo("     - mask image made transparent in white parts");
			}
			else {
				BufferedImage wtPipBi = new BufferedImage(pipBi.getWidth(), pipBi.getHeight(), BufferedImage.TYPE_BYTE_INDEXED, maskImageColorModel);
				for (int c = 0; c < pipBi.getWidth(); c++)
					for (int r = 0; r < pipBi.getHeight(); r++) {
						int rgb = pipBi.getRGB(c, r);
						int wtRgb;
						if ((rgb & 0xFF) < 128)
							wtRgb = 0xFF000000; // blue too dark for white
						else if (((rgb >> 8) & 0xFF) < 128)
							wtRgb = 0xFF000000; // green too dark for white
						else if (((rgb >> 16) & 0xFF) < 128)
							wtRgb = 0xFF000000; // red too dark for white
						else wtRgb = 0x00FFFFFF;
						wtPipBi.setRGB(c, r, wtRgb); // set to white with 0 alpha
					}
				pipBi = wtPipBi;
				spm.setInfo("     - mask image copied to be transparent in white parts");
			}
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
	
	private static void checkException(ParallelFor pf) throws IOException, RuntimeException {
		Exception error = pf.getException();
		if (error != null) {
			if (error instanceof RuntimeException)
				throw ((RuntimeException) error);
			else if (error instanceof IOException)
				throw ((IOException) error);
			else throw new IOException(error.getMessage());
		}
	}
	
	private BufferedImage decodeImage(Page pdfPage, byte[] data, Map params, Map objects, boolean forceUseIcePdf, boolean isSoftMask) throws IOException {
		if (PdfExtractorTest.aimAtPage != -1) {
			System.out.println(" ==> read " + data.length + " bytes");
			System.out.println(" ==> Lenght parameter is " + params.get("Length"));
			System.out.println(" ==> Width parameter is " + params.get("Width"));
			System.out.println(" ==> Height parameter is " + params.get("Height"));
		}
		
		//	get image data stream length
		int length;
		try {
			length = Integer.parseInt(params.get("Length").toString());
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" ==> Lenght is " + length);
		}
		catch (Exception e) {
			length = data.length;
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" ==> fallback Lenght is " + length);
		}
		
		//	make sure parsing didn't cripple image data
		if (data.length < length) {
			byte[] padData = new byte[length];
			System.arraycopy(data, 0, padData, 0, data.length);
			for (int b = data.length; b < padData.length; b++)
				padData[b] = '\n';
			data = padData;
		}
		else if (data.length > length) {
			byte[] cropData = new byte[length];
			System.arraycopy(data, 0, cropData, 0, cropData.length);
			data = cropData;
		}
		
		//	get decoding filter (and run every one but the last on multi-stage encoding)
		//	TODOne TEST z1997n4a12.pdf (scans are [FlateDecode, DCTDecode])
		Object filterObj = PdfParser.getObject(params, "Filter", objects);
		if (filterObj instanceof List) {
			List filters = ((List) filterObj);
			for (int f = 0; f < filters.size(); f++) {
				if ((f+1) == filters.size())
					filterObj = filters.get(f);
				else if (filters.get(f) != null)
					data = this.decodeImageData(data, params, filters.get(f).toString(), pdfPage.getLibrary(), objects);
			}
		}
		if (filterObj instanceof Reference)
			filterObj = objects.get(((Reference) filterObj).getObjectNumber() + " " + ((Reference) filterObj).getGenerationNumber());
		String filter = ((filterObj == null) ? null : filterObj.toString());
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println(" ==> filter is " + filter);
		
		//	get primary color space
		Object colorSpaceObj = PdfParser.getObject(params, "ColorSpace", objects);
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println(" ==> color space is " + colorSpaceObj);
		String colorSpace = null;
		List colorSpaceData = null;
		String altColorSpace = null;
		String separationColorSpaceComponent = null;
		if (colorSpaceObj instanceof List) {
			colorSpaceData = ((List) colorSpaceObj);
			if (colorSpaceData.size() != 0)
				colorSpace = colorSpaceData.get(0).toString();
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" ==> color space is (V) " + colorSpace);
			for (int d = 1; d < colorSpaceData.size(); d++) {
				Object csdObj = colorSpaceData.get(d);
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println("   - " + csdObj);
				csdObj = PdfParser.dereference(csdObj, objects);
				if ((PdfExtractorTest.aimAtPage != -1) && (csdObj != colorSpaceData.get(d)))
					System.out.println("     " + csdObj);
				if ("Separation".equals(colorSpace)) {
					if ((d == 1) && (csdObj != null)) {
						separationColorSpaceComponent = csdObj.toString();
						if (separationColorSpaceComponent.endsWith("Black"))
							separationColorSpaceComponent = "Black";
						if ((PdfExtractorTest.aimAtPage != -1) && "Black".equals(separationColorSpaceComponent))
							System.out.println("     ==> need to check for inverted figure");
					}
					else if (d == 2) {
						if ((csdObj instanceof String) || (csdObj instanceof Name))
							altColorSpace = csdObj.toString();
						else if ((csdObj instanceof List) && (((List) csdObj).size() != 0))
							altColorSpace = ((List) csdObj).get(0).toString();
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println("     ==> DeviceN alternative color space is (V) " + altColorSpace);
					}
				}
				else if ("DeviceN".equals(colorSpace)) {
					if ((d == 1) && (csdObj instanceof List) && (((List) csdObj).size() == 1)) {
						separationColorSpaceComponent = ((List) csdObj).get(0).toString();
						if (separationColorSpaceComponent.endsWith("Black"))
							separationColorSpaceComponent = "Black";
						if ((PdfExtractorTest.aimAtPage != -1) && "Black".equals(separationColorSpaceComponent))
							System.out.println("     ==> need to check for inverted figure");
					}
					else if (d == 2) {
						if ((csdObj instanceof String) || (csdObj instanceof Name))
							altColorSpace = csdObj.toString();
						else if ((csdObj instanceof List) && (((List) csdObj).size() != 0))
							altColorSpace = ((List) csdObj).get(0).toString();
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println(" ==> DeviceN alternative color space is (V) " + altColorSpace);
					}
				}
				
				//	if we have an indexed color space, we need to make bitmap into byte stream and decode via index byte by byte
				else if ("Indexed".equals(colorSpace)) {
					if (d == 1) {
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println("     ==> base color space is (V) " + csdObj);
					}
					else if (d == 2) {
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println("     ==> high value is (V) " + csdObj);
					}
					else if (d == 3) {
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println("     ==> lookup is (V) " + csdObj);
						if (csdObj instanceof PStream) {
							if (PdfExtractorTest.aimAtPage != -1)
								System.out.println("          " + Arrays.toString(((PStream) csdObj).bytes));
						}
					}
				}
				if (csdObj instanceof List) {
					List csdData = ((List) csdObj);
					for (int sd = 0; sd < csdData.size(); sd++) {
						csdObj = csdData.get(sd);
						if (PdfExtractorTest.aimAtPage != -1)
							System.out.println("     - " + csdObj);
						csdObj = PdfParser.dereference(csdObj, objects);
						if (csdObj != csdData.get(sd))
							System.out.println("       " + csdObj + " (" + csdObj.getClass().getName() + ")");
					}
				}
				else if (csdObj instanceof Map) {
					
				}
//				else if (csdObj instanceof PStream) {
//					PStream csdStream = ((PStream) csdObj);
//					ByteArrayOutputStream csdBaos = new ByteArrayOutputStream();
//					PdfParser.decode(csdStream.params.get("Filter"), csdStream.bytes, csdStream.params, csdBaos, objects);
//					byte[] csdData = csdBaos.toByteArray();
////					byte[] csdDataStart = new byte[Math.min(128, csdData.length)];
////					System.arraycopy(csdData, 0, csdDataStart, 0, csdDataStart.length);
//					System.out.println(" ==> " + new String(csdData));
//				}
			}
		}
		else if ((colorSpaceObj != null)) {
			colorSpace = colorSpaceObj.toString();
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" ==> color space is (NV) " + colorSpace);
		}
		
		//	get alternative color space
		if (altColorSpace == null) {
			if ((colorSpace != null) && colorSpace.startsWith("ICCB")) {
				altColorSpace = getAlternativeColorSpace(colorSpaceObj, objects);
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println(" ==> ICCBased alternative color space is " + altColorSpace);
			}
			else if ("Separation".equals(colorSpace)) {
				altColorSpace = getAlternativeColorSpace(colorSpaceObj, objects);
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println(" ==> Separation alternative color space is " + altColorSpace);
			}
		}
		
		//	check if we're decoding an image mask
		Object isImageMaskObj = params.get("ImageMask");
		boolean isImageMask = ((isImageMaskObj != null) && Boolean.parseBoolean(isImageMaskObj.toString()));
		
		//	check for decode array indicated inversion
		Object decodeObj = PdfParser.getObject(params, "Decode", objects);
		boolean decodeInverted = false;
		if (decodeObj instanceof List) {
			List decode = ((List) decodeObj);
			float lowSum = 0;
			float highSum = 0;
			for (int c = 0; c < (decode.size() - 1); c += 2) {
				lowSum += ((Number) decode.get(c)).floatValue();
				highSum += ((Number) decode.get(c+1)).floatValue();
			}
			decodeInverted = (highSum < lowSum);
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" ==> Decode array inversion is " + decodeInverted);
		}
		
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println(" ==> params are " + params);
//		Object metadataObj = PdfParser.getObject(params, "Metadata", objects);
//		if (metadataObj instanceof PStream) {
//			System.out.println(" ==> metadata params are " + ((PStream) metadataObj).params);
//			System.out.println(" ==> metadata is " + new String(((PStream) metadataObj).bytes));
//		}
		if (PdfExtractorTest.aimAtPage != -1) {
			byte[] dataStart = new byte[Math.min(128, data.length)];
			System.arraycopy(data, 0, dataStart, 0, dataStart.length);
			System.out.println(" ==> start bytes are " + Arrays.toString(dataStart));
//			System.out.println("     " + new String(streamStart));
		}
		
		//	decode XObject image
		if ((filter == null) && (params.get("Resources") instanceof Map) && (params.get("Type") != null) && "XObject".equals(params.get("Type").toString())) {
			//	get XObject image and recurse
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(new String(data));
			
			//	check rotation
			PRotate rotate = new PRotate(pdfPage, objects);
			Map resources = ((Map) params.get("Resources"));
			Map xObject = ((Map) PdfParser.dereference(resources.get("XObject"), objects));
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" ==> getting XObject figures from " + xObject);
			PFigure[] xpFigures = PdfParser.getPageFigures(pdfPage.getEntries(), data, rotate, resources, objects, ProgressMonitor.dummy);
			for (int f = 0; f < xpFigures.length; f++) {
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println("   - " + xpFigures[f].name + " @" + xpFigures[f].bounds);
				Object xpFigureObj = PdfParser.getObject(xObject, xpFigures[f].name, objects);
				if (xpFigureObj instanceof PStream)
					return this.decodeImage(pdfPage, ((PStream) xpFigureObj).bytes, ((PStream) xpFigureObj).params, objects, forceUseIcePdf, isSoftMask);
			}
			return null;
		}
		
		//	decode other image
		BufferedImage bi;
		if ((filter != null) && "JPXDecode".equals(filter.toString())) {
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" ==> decoding JPX");
//			bi = this.decodeJPX(data, ((separationColorSpaceComponent == null) ? colorSpace : ("Separation" + separationColorSpaceComponent)), altColorSpace, decodeInverted);
			//	no need to check decode array unless we're decoding an image mask, either, as JPX ignores it (page 311 in spec)
			bi = this.decodeJPX(data, ((separationColorSpaceComponent == null) ? colorSpace : ("Separation" + separationColorSpaceComponent)), altColorSpace, (isImageMask ? decodeInverted : false));
			//	no need to check for inversion (again) here, ImageMagick wrapper does so based upon color space
		}
		else if ((filter != null) && "JBIG2Decode".equals(filter.toString())) {
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" ==> decoding JBIG2");
			//	JPedal seems to be the one ...
			bi = this.decodeJBig2(data, params, objects);
			//	no need to check for inversion here, JBIG2 is fixed
		}
		else if ((filter != null) && "FlateDecode".equals(filter.toString())) {
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" ==> decoding Flate");
			//	TODO_why use java.util.zip.GZIPInputStream instead of IcePDF
			bi = this.decodeFlate(data, params, colorSpaceObj, pdfPage.getLibrary(), pdfPage.getResources(), objects, forceUseIcePdf, decodeInverted, isSoftMask);
			
			//	check for inversion if we have a (primary) 'Separation' color space specifying intensity of colorant 'Black'
			if ("Black".equals(separationColorSpaceComponent))
				checkForInvertedImage(bi);
		}
		else if ((filter != null) && "DCTDecode".equals(filter.toString())) {
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" ==> decoding JPEG (DCT)");
			bi = this.decodeJpeg(data, ((separationColorSpaceComponent == null) ? colorSpace : ("Separation" + separationColorSpaceComponent)), altColorSpace, decodeInverted);
			//	no need to check for inversion (again) here, ImageMagick wrapper does so based upon color space
		}
		else {
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" ==> decoding other");
			bi = this.decodeOther(data, params, filter, colorSpaceObj, pdfPage.getLibrary(), pdfPage.getResources(), objects, forceUseIcePdf, decodeInverted, isSoftMask);
			
			//	check for inversion if we have a (primary) 'Separation' color space specifying intensity of colorant 'Black'
			if ("Black".equals(separationColorSpaceComponent))
				checkForInvertedImage(bi);
		}
		
		//	TODO integrate this in catch below !!!
//		if ((filter != null) && ("JPXDecode".equals(filter.toString()) || "DCTDecode".equals(filter.toString())) && colorSpace.startsWith("ICCB") && !"Indexed".equals(colorSpace)) {
//			BufferedImage abi = new BufferedImage(bi.getWidth(), bi.getHeight(), BufferedImage.TYPE_INT_ARGB);
//			for (int c = 0; c < bi.getWidth(); c++)
//				for (int r = 0; r < bi.getHeight(); r++) {
//					int rgb = bi.getRGB(c, r);
//					int red = ((rgb >> 16) & 0xFF);
//					int green = ((rgb >> 8) & 0xFF);
//					int blue = ((rgb >> 0) & 0xFF);
//					int min = Math.min(red, Math.min(green, blue));
//					int max = Math.max(red, Math.max(green, blue));
//					int luminosity = ((min + max) / 2);
//					rgb = (((255 - luminosity) << 24) | (rgb & 0x00FFFFFF));
//					abi.setRGB(c, r, rgb);
//				}
//			bi = abi;
//		} else
		
		//	re-interpret JPX and DCT decoded image for index color space
		if ((filter != null) && ("JPXDecode".equals(filter.toString()) || "DCTDecode".equals(filter.toString())) && "Indexed".equals(colorSpace) && (colorSpaceData != null) && (colorSpaceData.size() >= 4)) try {
			if (PdfExtractorTest.aimAtPage != -1) {
				System.out.println(" ==> color space decoding pre-decoded bitmap");
				System.out.println("   - image type is " + bi.getType());
				System.out.println("   - color model is " + bi.getColorModel().getClass().getName());
				System.out.println("   - color model is " + bi.getColorModel());
			}
			
			PdfColorSpace cs = (isImageMask ? PdfColorSpace.getImageMaskColorSpace(params, objects) : PdfColorSpace.getColorSpace(colorSpaceObj, objects));
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println("   - target color space is " + cs);
			if (cs == null) {
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println(" ==> could not decode color space");
				return bi;
			}
			
			int colorCount = (Math.min(((Number) colorSpaceData.get(2)).intValue(), 255) + 1); // hival is inclusive
			int[] argb = new int[colorCount];
			LinkedList stack = new LinkedList();
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println("   - getting " + colorCount + " colors");
			for (int c = 0; c < colorCount; c++) {
				stack.add(new Integer(c));
				Color color = cs.getColor(stack, "");
				argb[c] = color.getRGB();
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println("     - color " + c + " (" + Integer.toString(c, 16) + ") is " + Integer.toString((color.getRGB() & 0x00FFFFFF), 16).toUpperCase() + " with alpha " + Integer.toString(color.getAlpha(), 16).toUpperCase());
				argb[c] &= 0x00FFFFFF;
				stack.clear();
			}
			if (PdfExtractorTest.aimAtPage != -1)
				cs.printStats();
			IndexColorModel ibiCm = new IndexColorModel(bi.getColorModel().getPixelSize(), colorCount, argb, 0, true, -1, DataBuffer.TYPE_BYTE);
			
			BufferedImage ibi = new BufferedImage(bi.getWidth(), bi.getHeight(), BufferedImage.TYPE_BYTE_INDEXED, ibiCm);
			for (int c = 0; c < bi.getWidth(); c++)
				for (int r = 0; r < bi.getHeight(); r++) {
					int index = (bi.getRGB(c, r) & 0xFF);
					if (colorCount <= index)
						index = (colorCount -1);
					ibi.setRGB(c, r, argb[index]);
				}
			bi = ibi;
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println("   ==> color space replaced");
		}
		catch (Exception e) {
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println("   ==> failed to replace color space: " + e.getMessage());
		}
		
		//	apply alpha from SMask
		Object sMaskObj = PdfParser.dereference(params.get("SMask"), objects);
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println("   ==> SMask is " + sMaskObj);
		if (sMaskObj instanceof PStream) {
			PStream sMask = ((PStream) sMaskObj);
			BufferedImage sMaskBi = this.decodeImage(pdfPage, sMask.bytes, sMask.params, objects, false, true);
			if (sMaskBi != null) {
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println("   ==> got SMask image sized " + sMaskBi.getWidth() + "x" + sMaskBi.getHeight());
				BoundingBox biBounds = new BoundingBox(0, bi.getWidth(), 0, bi.getHeight());
				bi = applySoftMaskImage(bi, biBounds, sMaskBi, biBounds); // local soft mask uses same bounds as main image
			}
		}
		
		//	finally ...
		return bi;
	}
	
	private static BufferedImage applySoftMaskImage(BufferedImage bi, BoundingBox biBounds, BufferedImage smBi, BoundingBox smBiBounds) {
		
		//	make sure base image has alphs (switch to new image type if required)
		if (bi.getColorModel().hasAlpha()) {
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println("   ==> alpha available in image type " + bi.getType());
		}
		else {
			BufferedImage alphaBi = new BufferedImage(bi.getWidth(), bi.getHeight(), BufferedImage.TYPE_INT_ARGB);
			Graphics2D alphaBiGr = alphaBi.createGraphics();
			alphaBiGr.drawImage(bi, 0, 0, null);
			alphaBiGr.dispose();
			bi = alphaBi;
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println("   ==> alpha available after move to image type " + bi.getType());
		}
		
		//	cut sub image out of mask image based upon relative position of bounding boxes
		int smBiLeft = ((((biBounds.left - smBiBounds.left) * smBi.getWidth()) + (smBiBounds.getWidth() / 2)) / smBiBounds.getWidth());
		int smBiRight = ((((biBounds.right - smBiBounds.left) * smBi.getWidth()) + (smBiBounds.getWidth() / 2)) / smBiBounds.getWidth());
		int smBiWidth = (smBiRight - smBiLeft);
		int smBiTop = ((((biBounds.top - smBiBounds.top) * smBi.getHeight()) + (smBiBounds.getHeight() / 2)) / smBiBounds.getHeight());
		int smBiBottom = ((((biBounds.bottom - smBiBounds.top) * smBi.getHeight()) + (smBiBounds.getHeight() / 2)) / smBiBounds.getHeight());
		int smBiHeight = (smBiBottom - smBiTop);
		if ((smBiLeft > 0) || (smBiTop > 0) || (smBiRight < smBi.getWidth()) || (smBiBottom < smBi.getHeight())) {
			if (PdfExtractorTest.aimAtPage != -1) {
				System.out.println("   ==> cut " + smBiBounds + " to match " + biBounds);
				System.out.println("   ==> mask image cut from [" + "0," + smBi.getWidth() + ",0," + smBi.getHeight() + "] to [" + smBiLeft + "," + smBiRight + "," + smBiTop + "," + smBiBottom + "]");
			}
//			smBi = smBi.getSubimage(smLeft, smTop, smWidth, smHeight);
			smBi = smBi.getSubimage(smBiLeft, smBiTop, Math.min(smBiWidth, (smBi.getWidth() - smBiLeft)), Math.min(smBiHeight, (smBi.getHeight() - smBiTop)));
		}
		
		//	apply alpha from soft mask
		for (int x = 0; x < bi.getWidth(); x++) {
//			int smx = (((x * smBi.getWidth()) + (bi.getWidth() / 2)) / bi.getWidth());
			int smx = (((x * smBiWidth) + (bi.getWidth() / 2)) / bi.getWidth());
			for (int y = 0; y < bi.getHeight(); y++) {
				if (PdfExtractorTest.aimAtPage != -1) /* adds white frame to visualize overall dimensions in debug views */ {
					if ((x == 0) || ((x + 1) == bi.getWidth())) {
						bi.setRGB(x, y, 0xFFFFFFFF);
						continue;
					}
					else if ((y == 0) || ((y + 1) == bi.getHeight())) {
						bi.setRGB(x, y, 0xFFFFFFFF);
						continue;
					}
				}
//				int smy = (((y * smBi.getHeight()) + (bi.getHeight() / 2)) / bi.getHeight());
				int smy = (((y * smBiHeight) + (bi.getHeight() / 2)) / bi.getHeight());
//				int smrgb = smBi.getRGB(smx, smy);
				int sma;
				if ((smx < 0) || (smBi.getWidth() <= smx))
					sma = 0x00;
				else if ((smy < 0) || (smBi.getHeight() <= smy))
					sma = 0x00;
				else {
					int smrgb = smBi.getRGB(smx, smy);
					int smr = ((smrgb >> 16) & 0xFF);
					int smg = ((smrgb >> 8) & 0xFF);
					int smb = ((smrgb >> 0) & 0xFF);
					sma = ((smr + smb + smg) / 3);
				}
				if (sma == 0xFF)
					continue; // fully opaque, nothing to change (we're only masking, aka _reducing_ any existing alpha)
				if (sma == 0) {
					bi.setRGB(x, y, 0x00000000); // with 0 alpha, reg, green, and blue don't matter
					continue; // we're done with this one
				}
				int rgb = bi.getRGB(x, y);
				int ca = ((sma * ((rgb >>> 24) & 0xFF)) / 0xFF); // multiply alpha values
				bi.setRGB(x, y, ((rgb & 0x00FFFFFF) | (ca << 24)));
			}
		}
		
		//	finally ...
		return bi;
	}
	
	private static String getAlternativeColorSpace(Object csdObj, Map objects) {
		if (csdObj instanceof List) {
			List csdArray = ((List) csdObj);
			for (int d = 0; d < csdArray.size(); d++) {
				csdObj = csdArray.get(d);
				csdObj = PdfParser.dereference(csdObj, objects);
				String altCs = getAlternativeColorSpace(csdObj, objects);
				if (altCs != null)
					return altCs;
			}
			return null;
		}
		
		Map csdDict;
		if (csdObj instanceof Map)
			csdDict = ((Map) csdObj);
		else if (csdObj instanceof PStream)
			csdDict = ((PStream) csdObj).params;
		else return null;
		
		if (csdDict.containsKey("Alternate"))
			return csdDict.get("Alternate").toString();
		if (csdDict.containsKey("N")) {
			Object nObj = csdDict.get("N");
			if (nObj instanceof Number) {
				if (((Number) nObj).intValue() == 1)
					return "DeviceGray";
				else if (((Number) nObj).intValue() == 3)
					return "DeviceRGB";
				else if (((Number) nObj).intValue() == 4)
					return "DeviceCMYK";
			}
		}
		
		return null;
	}
	
	private byte[] decodeImageData(byte[] data, Map params, String filter, Library library, Map objects) throws IOException {
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println(" ==> decoding image data");
		
		//	get and TODO_not observe decode parameter dictionary ==> looks like wrecking more havoc than doing good
		//	TODO interpret array of decode parameters as dedicated to an equally long sequence of decoding filters (once we have an example ...)
		Object decodeParamsObj = PdfParser.dereference(params.get("DecodeParms"), objects);
		Map decodeParams;
		if (decodeParamsObj instanceof Map)
			decodeParams = ((Map) decodeParamsObj);
		else if (decodeParamsObj instanceof List) {
			decodeParams = new HashMap();
			for (int d = 0; d < ((List) decodeParamsObj).size(); d++) {
				Object subDecodeParamsObj = PdfParser.dereference(((List) decodeParamsObj).get(d), objects);
				if (subDecodeParamsObj instanceof Map)
					decodeParams.putAll((Map) subDecodeParamsObj);
			}
		}
		else decodeParams = new HashMap();
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println("   - decode parameters are " + decodeParams);
		
		//	prepare decoding stream
		InputStream idIn;
		if ("FlateDecode".equals(filter) || "Fl".equals(filter)) {
//			idIn = new BufferedInputStream(new FlateDecode(library, new HashMap(params), new ByteArrayInputStream(data)));
			Number predictor = ((Number) decodeParams.get("Predictor"));
			if ((predictor == null) || (((Number) predictor).intValue() == 1 /* 1 means "no predictor" */))
				idIn = new BufferedInputStream(new FlateDecode(library, new HashMap(params), new ByteArrayInputStream(data)));
			else idIn = new BufferedInputStream(new PredictorDecode(new FlateDecode(library, new HashMap(params), new ByteArrayInputStream(data)), library, new HashMap(params)));
		}
		else if ("LZWDecode".equals(filter) || "LZW".equals(filter)) {
//			idIn = new BufferedInputStream(new LZWDecode(new BitStream(new ByteArrayInputStream(data)), library, new HashMap(params)));
			Number predictor = ((Number) decodeParams.get("Predictor"));
			if ((predictor == null) || (((Number) predictor).intValue() == 1 /* 1 means "no predictor" */))
				idIn = new BufferedInputStream(new LZWDecode(new BitStream(new ByteArrayInputStream(data)), library, new HashMap(params)));
			else idIn = new BufferedInputStream(new PredictorDecode(new LZWDecode(new BitStream(new ByteArrayInputStream(data)), library, new HashMap(params)), library, new HashMap(params)));
		}
		else if ("ASCII85Decode".equals(filter) || "A85".equals(filter))
			idIn = new BufferedInputStream(new ASCII85Decode(new ByteArrayInputStream(data)));
		else if ("ASCIIHexDecode".equals(filter) || "AHx".equals(filter))
			idIn = new BufferedInputStream(new ASCIIHexDecode(new ByteArrayInputStream(data)));
		else if ("RunLengthDecode".equals(filter) || "RL".equals(filter))
			idIn = new BufferedInputStream(new RunLengthDecode(new ByteArrayInputStream(data)));
		else if (filter == null)
			idIn = new ByteArrayInputStream(data);
		//	TODO observe other encodings (as we encounter them)
		else idIn = new ByteArrayInputStream(data);
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println("   - filter is " + filter);
		
		//	read decoded data
		ByteArrayOutputStream idOut = new ByteArrayOutputStream();
		byte[] idBuffer = new byte[1024];
		for (int r; (r = idIn.read(idBuffer, 0, idBuffer.length)) != -1;)
			idOut.write(idBuffer, 0, r);
		idIn.close();
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println("   ==> got " + idOut.size() + " decoded bytes");
		
		//	finally ...
		return idOut.toByteArray();
	}
	
	private BufferedImage decodeImageMagick(byte[] data, String format, String colorSpace, String altColorSpace, boolean decodeInverted) throws IOException {
		if (this.imageDecoder == null) {
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" ==> ImageMagick not available");
			return null;
		}
		else {
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" ==> decoding via ImageMagick (color space '" + colorSpace + "', alt '" + altColorSpace + "')");
			return this.imageDecoder.decodeImage(data, format, colorSpace, altColorSpace, decodeInverted);
		}
	}
	
	private BufferedImage decodeJBig2(byte[] data, Map params, Map objects) throws IOException {
		try {
			JBIG2Decoder jbd = new JBIG2Decoder();
			Object decodeParamsObj = PdfParser.dereference(params.get("DecodeParms"), objects);
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" ==> decode params are " + decodeParamsObj);
			if (decodeParamsObj instanceof Map) {
				Map decodeParams = ((Map) decodeParamsObj);
				Object jbGlobalsObj = PdfParser.dereference(decodeParams.get("JBIG2Globals"), objects);
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println(" ==> global data is " + jbGlobalsObj);
				if (jbGlobalsObj instanceof PStream) {
					PStream jbGlobals = ((PStream) jbGlobalsObj);
					jbd.setGlobalData(jbGlobals.bytes);
				}
			}
			jbd.decodeJBIG2(data);
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
	
	private BufferedImage decodeFlate(byte[] data, Map params, Object csObj, Library library, Resources resources, Map objects, boolean forceUseIcePdf, boolean decodeInverted, boolean isSoftMask) throws IOException {
		if (!forceUseIcePdf) {
			BufferedImage bitmapBi = this.decodeBitmap(data, params, "FlateDecode", csObj, library, resources, objects, decodeInverted, isSoftMask);
			if (bitmapBi != null)
				return bitmapBi;
		}
		
		SeekableInputConstrainedWrapper streamInputWrapper = new SeekableInputConstrainedWrapper(new SeekableByteArrayInputStream(data), 0, data.length);
		ImageStream str = new ImageStream(library, new HashMap(params), streamInputWrapper);
		try {
			Color biBackgroundColor = getBackgroundColor(params);
			BufferedImage bi = str.getImage(biBackgroundColor, resources);
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
				FlateDecode fd = new FlateDecode(library, new HashMap(params), new ByteArrayInputStream(data));
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
	
//	private BufferedImage decodeOther(byte[] data, Hashtable params, String filter, Object csObj, Library library, Resources resources, Map objects, boolean forceUseIcePdf, boolean decodeInverted) throws IOException {
	private BufferedImage decodeOther(byte[] data, Map params, String filter, Object csObj, Library library, Resources resources, Map objects, boolean forceUseIcePdf, boolean decodeInverted, boolean isSoftMask) throws IOException {
		if (!forceUseIcePdf) {
			BufferedImage bitmapBi = this.decodeBitmap(data, params, filter, csObj, library, resources, objects, decodeInverted, isSoftMask);
			if (bitmapBi != null)
				return bitmapBi;
		}
		
		SeekableInputConstrainedWrapper streamInputWrapper = new SeekableInputConstrainedWrapper(new SeekableByteArrayInputStream(data), 0, data.length);
		ImageStream str = new ImageStream(library, new HashMap(params), streamInputWrapper);
		try {
			Color biBackgroundColor = getBackgroundColor(params);
			BufferedImage bi = str.getImage(biBackgroundColor, resources);
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
	
//	private BufferedImage decodeBitmap(byte[] data, Hashtable params, String filter, Object csObj, Library library, Resources resources, Map objects, boolean decodeInverted) throws IOException {
	private BufferedImage decodeBitmap(byte[] data, Map params, String filter, Object csObj, Library library, Resources resources, Map objects, boolean decodeInverted, boolean isSoftMask) throws IOException {
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println(" ==> decoding bitmap");
		
		//	check whether or not we're dealing with an image mask
		Object isImageMaskObj = params.get("ImageMask");
		boolean isImageMask = ((isImageMaskObj != null) && Boolean.parseBoolean(isImageMaskObj.toString()));
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println("   - image mask is " + isImageMask + " (for " + isImageMaskObj + ")");
		
		//	test if we can handle this one (image mask or not)
		PdfColorSpace cs = (isImageMask ? PdfColorSpace.getImageMaskColorSpace(params, objects) : PdfColorSpace.getColorSpace(csObj, objects));
		if (cs == null)
			return null;
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println("   - color space is " + cs.name + " (" + cs.numComponents + " components)");
		
		//	get and TODO_not observe decode parameter dictionary ==> looks like wrecking more havoc than doing good
		//	TODO interpret array of decode parameters as dedicated to an equally long sequence of decoding filters (once we have an example ...)
		Object decodeParamsObj = PdfParser.dereference(params.get("DecodeParms"), objects);
		Map decodeParams;
		if (decodeParamsObj instanceof Map)
			decodeParams = ((Map) decodeParamsObj);
		else if (decodeParamsObj instanceof List) {
			decodeParams = new HashMap();
			for (int d = 0; d < ((List) decodeParamsObj).size(); d++) {
				Object subDecodeParamsObj = PdfParser.dereference(((List) decodeParamsObj).get(d), objects);
				if (subDecodeParamsObj instanceof Map)
					decodeParams.putAll((Map) subDecodeParamsObj);
			}
		}
		else decodeParams = new HashMap();
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println("   - decode parameters are " + decodeParams);
		
		//	get image size
		int width;
//		if (decodeParams == null) {
			Object widthObj = params.get("Width");
			if (widthObj instanceof Number)
				width = ((Number) widthObj).intValue();
			else return null;
//		}
//		else {
//			Object columnsObj = decodeParams.get("Columns");
//			if (columnsObj instanceof Number)
//				width = ((Number) columnsObj).intValue();
//			else return null;
//		}
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
//		if (decodeParams == null) {
			Object bcpObj = params.get("BitsPerComponent");
			if (bcpObj instanceof Number)
				bitsPerComponent = ((Number) bcpObj).intValue();
			else return null;
//		}
//		else {
//			Object bcpObj = decodeParams.get("BitsPerComponent");
//			if (bcpObj instanceof Number)
//				bitsPerComponent = ((Number) bcpObj).intValue();
//			else return null;
//		}
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println("   - bits per component is " + bitsPerComponent);
		
		//	prepare decoding stream
		InputStream biIn;
		if ("FlateDecode".equals(filter) || "Fl".equals(filter)) {
			Number predictor = ((Number) PdfParser.dereference(decodeParams.get("Predictor"), objects));
			if ((predictor == null) || (((Number) predictor).intValue() == 1 /* 1 means "no predictor" */) || (((Number) predictor).intValue() == 2 /* handled by flate decoder */))
				biIn = new BufferedInputStream(new FlateDecode(library, new HashMap(params), new ByteArrayInputStream(data)));
			else biIn = new BufferedInputStream(new PredictorDecode(new FlateDecode(library, new HashMap(params), new ByteArrayInputStream(data)), library, new HashMap(params)));
		}
		else if ("LZWDecode".equals(filter) || "LZW".equals(filter)) {
			Number predictor = ((Number) PdfParser.dereference(decodeParams.get("Predictor"), objects));
			if ((predictor == null) || (((Number) predictor).intValue() == 1 /* 1 means "no predictor" */))
				biIn = new BufferedInputStream(new LZWDecode(new BitStream(new ByteArrayInputStream(data)), library, new HashMap(params)));
			else biIn = new BufferedInputStream(new PredictorDecode(new LZWDecode(new BitStream(new ByteArrayInputStream(data)), library, new HashMap(params)), library, new HashMap(params)));
		}
		else if ("ASCII85Decode".equals(filter) || "A85".equals(filter))
			biIn = new BufferedInputStream(new ASCII85Decode(new ByteArrayInputStream(data)));
		else if ("ASCIIHexDecode".equals(filter) || "AHx".equals(filter))
			biIn = new BufferedInputStream(new ASCIIHexDecode(new ByteArrayInputStream(data)));
		else if ("RunLengthDecode".equals(filter) || "RL".equals(filter))
			biIn = new BufferedInputStream(new RunLengthDecode(new ByteArrayInputStream(data)));
		else if ("CCITTFaxDecode".equals(filter) || "CCF".equals(filter)) {
//			System.out.println("   - CCITTFaxDecoding " + Arrays.toString(stream));
			byte[] dStream = new byte[((width + 7) / 8) * height];
//			System.out.println("     - expecting " + dStream.length + " bytes of result");
			Number k = ((decodeParams == null) ? null : ((Number) PdfParser.dereference(decodeParams.get("K"), objects)));
			if (k == null)
				k = new Integer(0);
			Number columns = ((decodeParams == null) ? null : ((Number) PdfParser.dereference(decodeParams.get("Columns"), objects)));
//			System.out.println("     - K is " + k);
			CCITTFaxDecoder decoder = new CCITTFaxDecoder(1, ((columns == null) ? width : columns.intValue()), height);
			Object byteAlign = ((decodeParams == null) ? null : decodeParams.get("EncodedByteAlign"));
			if ((byteAlign != null) && "true".equals(byteAlign.toString()))
				decoder.setAlign(true);
			if (k.intValue() == 0)
				decoder.decodeT41D(dStream, data, 0, height);
			else if (k.intValue() < 0)
				decoder.decodeT6(dStream, data, 0, height);
			else decoder.decodeT42D(dStream, data, 0, height);
			Object inverted = ((decodeParams == null) ? null : decodeParams.get("BlackIs1"));
			if ((inverted == null) || !"true".equals(inverted.toString())) {
				for (int b = 0; b < dStream.length; b++)
					dStream[b] ^= 255; // TODO observe whether or not we need this (appears so initially)
			}
			//	TODO maybe better try and use black-on-white correction, and _after_ image assembly from parts
			biIn = new ByteArrayInputStream(dStream);
		}
		else if (filter == null)
			biIn = new ByteArrayInputStream(data);
		//	TODO observe other encodings (as we encounter them)
		else biIn = new ByteArrayInputStream(data);
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println("   - filter is " + filter);
		
		//	create bit masks
		int colorComponents = cs.numComponents;
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println("   - color components are " + colorComponents + " in bitmap, " + cs.getColorNumComponents() + " in colors");
		int componentBitMask = 0;
		for (int b = 0; b < bitsPerComponent; b++) {
			componentBitMask <<= 1;
			componentBitMask |= 1;
		}
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println("   - component bit mask is " + Long.toString(componentBitMask, 16));
		int bitsPerPixel = (colorComponents * bitsPerComponent);
		long pixelBitMask = 0;
		for (int b = 0; b < bitsPerPixel; b++) {
			pixelBitMask <<= 1;
			pixelBitMask |= 1;
		}
		if (PdfExtractorTest.aimAtPage != -1) {
			System.out.println("   - bits per pixel is " + bitsPerPixel);
			System.out.println("   - pixel bit mask is " + Long.toString(pixelBitMask, 16));
		}
		
		//	determine image type
		int biType;
		if (isSoftMask)
			biType = BufferedImage.TYPE_BYTE_GRAY;
		else if (bitsPerPixel == 1)
			biType = BufferedImage.TYPE_BYTE_BINARY;
		else if (cs.getColorNumComponents() == 1)
			biType = BufferedImage.TYPE_BYTE_GRAY;
		else biType = BufferedImage.TYPE_INT_ARGB;
		
		//	fill image
		LinkedList colorDecodeStack = new LinkedList();
		ArrayList topPixelRow = null;
		if (PdfExtractorTest.aimAtPage != -1) {
			colorDecodeStack.addFirst("DEBUG_COLORS");
			topPixelRow = new ArrayList();
		}
		BufferedImage bi = new BufferedImage(width, height, biType);
		for (int r = 0; r < height; r++) {
			int bitsRemaining = 0;
			long bitData = 0;
			for (int c = 0; c < width; c++) {
				
				//	make sure we have enough bits left in buffer
				while (bitsRemaining < bitsPerPixel) {
					int nextByte = biIn.read();
					bitData <<= 8;
					bitData |= nextByte;
					bitsRemaining += 8;
				}
				
				//	get component values for pixel
				long pixelBits = ((bitData >>> (bitsRemaining - bitsPerPixel)) & pixelBitMask);
				bitsRemaining -= bitsPerPixel;
				if ((r == 0) && (topPixelRow != null))
					topPixelRow.add(Long.toString(pixelBits, 16).toUpperCase());
				
				//	extract component values
				for (int cc = 0; cc < colorComponents; cc++) {
					long rccb = ((pixelBits >>> ((colorComponents - cc - 1) * bitsPerComponent)) & componentBitMask);
					long ccb = rccb;
					for (int b = bitsPerComponent; b < 8; b += bitsPerComponent) {
						ccb <<= bitsPerComponent;
						ccb |= rccb;
					}
					colorDecodeStack.addLast(new Float(((float) ccb) / 255));
				}
				
				//	get color
				Color color = cs.decodeColor(colorDecodeStack, ((PdfExtractorTest.aimAtPage != -1) ? "" : null));
				if (decodeInverted && !isImageMask) // mask image color space does inversion internally
					color = new Color((255 - color.getRed()), (255 - color.getGreen()), (255 - color.getBlue()), (255 - color.getAlpha()));
				
				//	set pixel
				bi.setRGB(c, r, color.getRGB());
			}
		}
		
		//	which colors did we use?
		if (PdfExtractorTest.aimAtPage != -1)
			cs.printStats();
		
		//	finally ...
		biIn.close();
		return bi;
	}
	
	private BufferedImage decodeJPX(byte[] data, String colorSpace, String altColorSpace, boolean decodeInverted) throws IOException {
		return this.decodeImageMagick(data, "jp2", colorSpace, altColorSpace, decodeInverted);
	}
	
	private BufferedImage decodeJpeg(byte[] data, String colorSpace, String altColorSpace, boolean decodeInverted) throws IOException {
		return this.decodeImageMagick(data, "jpg", colorSpace, altColorSpace, decodeInverted);
	}
	
	private static Color getBackgroundColor(Map params) {
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
			if ((dObj instanceof List) && (((List) dObj).size() != 0)) {
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println(" - decoder is " + dObj.getClass().getName() + " - " + dObj);
				Object ptObj = ((List) dObj).get(0);
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
	
	private static void checkForInvertedImage(BufferedImage bi) {
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println(" - checking for inverted image");
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
		double avgBrightness = (bSum / (bi.getWidth() * bi.getHeight()));
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println("   - average brightness is " + avgBrightness);
		double avgSaturation = (sSum / (bi.getWidth() * bi.getHeight()));
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println("   - average saturation is " + avgSaturation);
		
		//	this one's brighter than its inverse, keep it as is
		if ((bSum / (bi.getWidth() * bi.getHeight())) > 0.5) {
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println("   ==> too bright to be inverted (avg)");
			return;
		}
		
		//	this one might still be light stuff on a dark background (and we're primarily after inverted line drawing mask images) ...
//		if ((bSum / (bi.getWidth() * bi.getHeight())) > 0.15) {
		if ((bSum / (bi.getWidth() * bi.getHeight())) > 0.25) {
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println("   ==> too bright to be inverted (light on black)");
			return;
		}
		
		//	this one looks like color, keep it as is
		if (sSum / (bi.getWidth() * bi.getHeight()) > 0.05) {
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println("   ==> too well saturated to be inverted");
			return;
		}
		
		//	invert brightness
		invertBrightness(bi);
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println("   ==> image inverted");
	}
	
	private static void invertBrightness(BufferedImage bi) {
		int rgb;
		int r;
		int g;
		int b;
		for (int x = 0; x < bi.getWidth(); x++)
			for (int y = 0; y < bi.getHeight(); y++) {
				rgb = bi.getRGB(x, y);
				r = getWhiteAdjusted(255 - ((rgb >> 16) & 255));
				g = getWhiteAdjusted(255 - ((rgb >> 8) & 255));
				b = getWhiteAdjusted(255 - ((rgb >> 0) & 255));
				rgb = (0xFF000000 | (r << 16) | (g << 8) | (b << 0));
				bi.setRGB(x, y, rgb);
			}
	}
	
	private static int getWhiteAdjusted(int i) {
		return ((i < 208) ? i : 255); // pretty hard cutoff ... TODO make sure this doesn't wreak havok
	}
	
	private static BoundingBox getBoundingBox(Rectangle2D bounds, Rectangle2D.Float pageBounds, float magnification, int rotate) {
		float fLeft = (((float) (bounds.getMinX() - pageBounds.getMinX())) * magnification);
		float fRight = (((float) (bounds.getMaxX() - pageBounds.getMinX()))  * magnification);
		float fTop = ((pageBounds.height - ((float) (bounds.getMaxY() - ((2 * pageBounds.getMinY()) - pageBounds.getMaxY())))) * magnification);
		float fBottom = ((pageBounds.height - ((float) (bounds.getMinY() - ((2 * pageBounds.getMinY()) - pageBounds.getMaxY())))) * magnification);
		int left = Math.round(fLeft);
		int right = (left + Math.round(fRight - fLeft));
		int top = Math.round(fTop);
		int bottom = (top + Math.round(fBottom - fTop));
//		System.out.println("Transformed " + bounds);
//		System.out.println("  with " + pageBounds);
//		System.out.println("  to [" + fLeft + "," + fRight + "," + fTop + "," + fBottom + "]");
//		System.out.println("  to [" + left + "," + right + "," + top + "," + bottom + "]");
		if (left == right) {
			left = ((int) Math.floor(fLeft));
			right = ((int) Math.ceil(fRight));
//			System.out.println("  corrected to [" + left + "," + right + "," + top + "," + bottom + "]");
		}
		if (top == bottom) {
			top = ((int) Math.floor(fTop));
			bottom = ((int) Math.ceil(fBottom));
//			System.out.println("  corrected to [" + left + "," + right + "," + top + "," + bottom + "]");
		}
		return new BoundingBox(left, right, top, bottom);
	}
	
	private static Rectangle2D.Float getBounds(BoundingBox bb, Rectangle2D.Float pageBounds, float magnification, int rotate) {
		float minX = ((float) ((bb.left / magnification) + pageBounds.getMinX()));
		float maxX = ((float) ((bb.right / magnification) + pageBounds.getMinX()));
		float maxY = ((float) (pageBounds.height + ((2 * pageBounds.getMinY()) - pageBounds.getMaxY()) - (bb.top / magnification)));
		float minY = ((float) (pageBounds.height + ((2 * pageBounds.getMinY()) - pageBounds.getMaxY()) - (bb.bottom / magnification)));
//		System.out.println("Transformed " + bb);
//		System.out.println("  with " + pageBounds);
//		System.out.println("  to " + new Rectangle2D.Float(minX, minY, (maxX - minX), (maxY - minY)));
		return new Rectangle2D.Float(minX, minY, (maxX - minX), (maxY - minY));
	}
	
//	private BufferedImage extractFigureFromPageImage(Document pdfDoc, int p, Rectangle2D.Float pdfPageBounds, int rotate, int figureDpi, Rectangle2D pdfFigureBounds) {
//		
//		//	render page image at figure resolution
//		float figureMagnification = (((float) figureDpi) / defaultDpi);
//		BufferedImage figurePageImage;
//		synchronized (pdfDoc) {
//			pdfDoc.getPageTree().getPage(p, "").init(); // there might have been a previous call to reduceMemory() ...
//			figurePageImage = ((BufferedImage) pdfDoc.getPageImage(p, GraphicsRenderingHints.SCREEN, Page.BOUNDARY_CROPBOX, 0, figureMagnification));
//			pdfDoc.getPageTree().getPage(p, "").reduceMemory();
//		}
//		
//		//	convert bounds, as PDF Y coordinate is bottom-up, whereas Java, JavaScript, etc. Y coordinate is top-down
//		BoundingBox figureBox = getBoundingBox(pdfFigureBounds, pdfPageBounds, figureMagnification, rotate);
//		
//		//	make sure figure box is in bounds
//		int left = Math.max(figureBox.left, 0);
//		int right = Math.min(figureBox.right, figurePageImage.getWidth());
//		int width = (right - left);
//		int top = Math.max(figureBox.top, 0);
//		int bottom = Math.min(figureBox.bottom, figurePageImage.getHeight());
//		int height = (bottom - top);
//		
//		//	extract figure
//		return figurePageImage.getSubimage(left, top, width, height);
//	}
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
//	
//	private void correctInvertedGaryscale(BufferedImage bi) {
//		int rgb;
//		float[] hsb = new float[3];
//		
//		//	check average brightness and saturation
//		double bSum = 0;
//		double sSum = 0;
//		for (int x = 0; x < bi.getWidth(); x++)
//			for (int y = 0; y < bi.getHeight(); y++) {
//				rgb = bi.getRGB(x, y);
//				hsb = Color.RGBtoHSB(((rgb >> 16) & 255), ((rgb >> 8) & 255), ((rgb >> 0) & 255), hsb);
//				bSum += hsb[2];
//				sSum += hsb[1];
//			}
//		
//		//	this one's brighter than its inverse, keep it as is
//		if (bSum / (bi.getWidth() * bi.getHeight()) > 0.5)
//			return;
//		
//		//	this one looks like color, keep it as is
//		if (sSum / (bi.getWidth() * bi.getHeight()) > 0.05)
//			return;
//		
//		//	invert brightness
//		for (int x = 0; x < bi.getWidth(); x++)
//			for (int y = 0; y < bi.getHeight(); y++) {
//				rgb = bi.getRGB(x, y);
//				hsb = Color.RGBtoHSB(((rgb >> 16) & 255), ((rgb >> 8) & 255), ((rgb >> 0) & 255), hsb);
//				int g = ((int) (255 * (1.0f - hsb[2])));
//				rgb = ((255 << 24) | (g << 16) | (g << 8) | g);
//				bi.setRGB(x, y, rgb);
//			}
//	}
//	
//	private void invertFigureBrightness(BufferedImage bi) {
//		int rgb;
//		float[] hsb = new float[3];
//		
//		//	check average brightness
//		double bSum = 0;
//		for (int x = 0; x < bi.getWidth(); x++)
//			for (int y = 0; y < bi.getHeight(); y++) {
//				rgb = bi.getRGB(x, y);
//				hsb = Color.RGBtoHSB(((rgb >> 16) & 255), ((rgb >> 8) & 255), ((rgb >> 0) & 255), hsb);
//				bSum += hsb[2];
//			}
//		
//		//	this one's brighter than its inverse, keep it as is
//		if (bSum / (bi.getWidth() * bi.getHeight()) > 0.5)
//			return;
//		
//		//	invert brightness
//		for (int x = 0; x < bi.getWidth(); x++)
//			for (int y = 0; y < bi.getHeight(); y++) {
//				rgb = bi.getRGB(x, y);
//				hsb = Color.RGBtoHSB(((rgb >> 16) & 255), ((rgb >> 8) & 255), ((rgb >> 0) & 255), hsb);
//				bi.setRGB(x, y, Color.HSBtoRGB(hsb[0], hsb[1], (1.0f - hsb[2])));
//			}
//	}
	
	private ImDocument doCreateDocument(String docId, int pageCount, Set pageIDs) {
		
		//	collect valid page IDs (if any specified)
		Set validPageIDs = null;
		if (pageIDs != null) {
			validPageIDs = new HashSet();
			for (Iterator pidit = pageIDs.iterator(); pidit.hasNext();) {
				Number pid = ((Number) pidit.next());
				if ((pid.intValue() >= 0) && (pid.intValue() < pageCount))
					validPageIDs.add(pid);
			}
		}
		
		//	factor page IDs into document ID if given
		if ((validPageIDs != null) && (validPageIDs.size() != 0) && (validPageIDs.size() < pageCount)) {
			System.out.println("Factoring page IDs into document ID ...");
			System.out.println(" - document ID is " + docId);
			int[] pids = new int[validPageIDs.size()];
			int pidPos = 0;
			for (Iterator pidit = validPageIDs.iterator(); pidit.hasNext();) {
				Number pid = ((Number) pidit.next());
				pids[pidPos++] = pid.intValue();
			}
			Arrays.sort(pids);
			System.out.println(" - page IDs are " + Arrays.toString(pids));
			
			/* we have to seed with a 1 here, as otherwise a sole page ID 0
			 * would incur no changes at all below, resulting in most trivial
			 * collision with full document ID of all */
			int pidHash = 1;
			/* Hash computation akin to String.hashCode(), but also factoring
			 * in position in array. Shift modulo 24 might lose leftmost bits
			 * of numbers greater than 255, but for these to be in high enough
			 * positions in the array, we can trust the remainder to create
			 * enough uniqueness to prevent collisions in practice. */
			for (int i = 0; i < pids.length; i++) {
				pidHash *= 31; // inspired by Java's own array hashing (which, however, is not sufficiently unique on the small arrays we're dealing with here) 
//				pidHash += (pids[i] << (i % 24));
				pidHash += (pids[i] << ((i % 4) * 8));
			}
			System.out.println(" - page ID hash is " + Integer.toString(pidHash, 16).toUpperCase());
			StringBuffer pidHashString = new StringBuffer();
			for (int i = 0; i < 4; i++) {
				int docIdBits = Integer.parseInt(docId.substring((24 + (i * 2)), (24 + (i * 2) + 2)), 16);
				int pidHashBits = ((pidHash >>> ((3 - i) * 8)) & 255);
				String hashString = Integer.toString((docIdBits ^ pidHashBits), 16).toUpperCase();
				if (hashString.length() < 2)
					pidHashString.append("0");
				pidHashString.append(hashString);
			}
			System.out.println(" - combined page ID hash string is " + pidHashString.toString());
			docId = (docId.substring(0, 24) + pidHashString.toString());
			System.out.println(" - document ID adjusted to " + docId);
		}
		
		//	create and return document
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
	
	private static String getChecksum(byte[] pdfBytes) {
		String checksum = HashUtils.getMd5(pdfBytes);
		return ((checksum == null) ? Gamta.getAnnotationID() : checksum); // use random value to avoid collisions
	}
}