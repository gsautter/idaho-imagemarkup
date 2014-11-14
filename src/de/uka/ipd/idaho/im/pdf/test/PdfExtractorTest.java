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
package de.uka.ipd.idaho.im.pdf.test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import javax.swing.UIManager;

import org.icepdf.core.pobjects.Document;

import de.uka.ipd.idaho.gamta.MutableAnnotation;
import de.uka.ipd.idaho.gamta.util.AnalyzerDataProvider;
import de.uka.ipd.idaho.gamta.util.AnalyzerDataProviderFileBased;
import de.uka.ipd.idaho.gamta.util.imaging.ImagingConstants;
import de.uka.ipd.idaho.gamta.util.imaging.PageImage;
import de.uka.ipd.idaho.gamta.util.imaging.PageImageInputStream;
import de.uka.ipd.idaho.gamta.util.imaging.PageImageStore;
import de.uka.ipd.idaho.gamta.util.imaging.PageImageStore.AbstractPageImageStore;
import de.uka.ipd.idaho.gamta.util.imaging.utilities.ImageDisplayDialog;
import de.uka.ipd.idaho.gamta.util.swing.DialogFactory;
import de.uka.ipd.idaho.gamta.util.swing.ProgressMonitorDialog;
import de.uka.ipd.idaho.im.ImDocument;
import de.uka.ipd.idaho.im.ImPage;
import de.uka.ipd.idaho.im.ImRegion;
import de.uka.ipd.idaho.im.ImWord;
import de.uka.ipd.idaho.im.pdf.PdfExtractor;
import de.uka.ipd.idaho.im.util.ImfIO;

/**
 * Test engine for PDF import into Image Markup data model.
 * 
 * @author sautter
 */
public class PdfExtractorTest implements ImagingConstants {
	public static int aimAtPage = -1;
	public static void main(String[] args) throws Exception {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e) {}
		
		final File pdfDataPath = new File("E:/Testdaten/PdfExtract/");
		final AnalyzerDataProvider dataProvider = new AnalyzerDataProviderFileBased(pdfDataPath);
		
		//	register page image source
		PageImageStore pis = new AbstractPageImageStore() {
			public boolean isPageImageAvailable(String name) {
				if (!name.endsWith(IMAGE_FORMAT))
					name += ("." + IMAGE_FORMAT);
				return dataProvider.isDataAvailable(name);
			}
			public PageImageInputStream getPageImageAsStream(String name) throws IOException {
				if (!name.endsWith(IMAGE_FORMAT))
					name += ("." + IMAGE_FORMAT);
				if (!dataProvider.isDataAvailable(name))
					return null;
				return new PageImageInputStream(dataProvider.getInputStream(name), this);
			}
			public boolean storePageImage(String name, PageImage pageImage) throws IOException {
				if (!name.endsWith(IMAGE_FORMAT))
					name += ("." + IMAGE_FORMAT);
				try {
					OutputStream imageOut = dataProvider.getOutputStream(name);
					pageImage.write(imageOut);
					imageOut.close();
					return true;
				}
				catch (IOException ioe) {
					ioe.printStackTrace(System.out);
					return false;
				}
			}
			public int getPriority() {
				return 0; // we're a general page image store, yield to specific ones
			}
		};
		PageImage.addPageImageSource(pis);
		final PdfExtractor pdfExtractor = new PdfExtractor(pdfDataPath, pis, true);
		
		String pdfName;
		pdfName = "abcofevolution00mcca.pdf"; // clean single column, narrative
//												// rather than treatments
//												// JPX, JBIG2
//		pdfName = "ants_02732.pdf"; // large taxonomic key, some drawings
//									// Flate, white on black
//		pdfName = "SCZ634_Cairns_web_FINAL.pdf"; // digitally born, two column
//													// layout, many figures,
//													// tables without grid,
//													// author omissions in
//													// bibliography
//		pdfName = "ObjectTest.pdf";
//		pdfName = "5834.pdf"; // small fonts, narrow lines, lots of italics
//								// CCITTFaxDecode, multi-line dictionaries
//		pdfName = "21330.pdf"; // pages skewed, otherwise pretty clean
//								// CCITTFaxDecode
//		pdfName = "zt02879p040.pdf"; // digitally born ZooTaxa, Type3 fonts
//		pdfName = "23416.pdf"; // ca 800 page monograph, modern, multiple
//								// languages, with some cyrillic script
//		pdfName = "Test600DPI.pdf";
//		pdfName = "NyBotanicalGarden-plugtmp-17_plugin-view.pdf"; // clean
//																	// botanical
//																	// document
//																	// from NYBG
//		pdfName = "cikm1076-sautter.pdf"; // digital-born multi-column created
//											// with Adobe PDF Printer from MS
//											// Word (computer science paper in
//											// ACM layout)
//		pdfName = "TableTest.pdf"; // textual PDF with table and image
		
//		pdfName = "Menke_Cerat_1.pdf"; // bad image quality, many extended
//										// stains (photocopied staples) that do
//										// fall to white balance, in German,
//										// some pages skewed
//		pdfName = "Forsslund1964.pdf"; // in German, unregular gray background,
//										// some bleed-through, relatively dark
//										// page edges that do not fall to white
//										// balance, some hand markings in text
//		pdfName = "Schmelz+Collado-2005-Achaeta becki.pdf"; // digital-born PDF,
//															// bitmap based
//															// Type3 font,
//															// artifact-only
//															// (blank after
//															// normalization)
//															// last page
//		pdfName = "Schulz_1997.pdf"; // bad image quality, diverse
//									// images, real problems
//		pdfName = "Schulz_1997-2.pdf"; // puncher holes, dark page margins
//		pdfName = "Mellert_et_al_2000.pdf"; // pretty dirty pages,
//											// some fuzzy in
//											// addition, many tables and
//											// diagrams, 600 dpi scans
//		pdfName = "Prasse_1979_1.PDF"; // very light, plus gray
//										// background on some pages, lines
//										// overlapping or blurred together due
//										// to stencil print, fold-outs with
//										// brightness gradient in folds, likely
//										// as bad as it gets regarding
//										// challenges to image enhancement
//		pdfName = "Sansone-2012-44-121.pdf"; // three-column digitally born PDF,
//												// some problem with superscripts
//		pdfName = "Taylor_Wolters_2005.pdf"; // digitally born PDF from
//												// Elsevier, Type1C fonts
//		pdfName = "dikow_2010a.pdf"; // digitally born PDF, Type1 fonts, oversized word boxes
//		pdfName = "dikow_2012.pdf"; // digitally born PDF, Type1 fonts, mdashes on page 7
//		pdfName = "FOG Main text_ pag 1_8.pdf"; // from pro-iBiosphere, born digital, SIDs in excess of 390
//		pdfName = "Ceratolejeunea_ Lejeuneaceae.pdf"; // from pro-iBiosphere, scanned, with wide black page margins, images stored in stripes, encoded in FlateDecode or DTCDecode, DCT images OK, Flate images strange
//		pdfName = "Ceratolejeunea_ Lejeuneaceae.mono.pdf"; // from pro-iBiosphere, scanned, with wide black page margins, images restored to full using Acrobat 6
//		pdfName = "BEC_1890.pdf"; // from pro-iBiosphere, scanned, monochrome
//		pdfName = "zt03456p035.pdf"; // 2013 Zootaxa born-digital PDF
//		pdfName = "fm__1_4_295_6.pdf"; // camera-scanned file from Donat
//		pdfName = "22817.pdf"; // nicely short 2009 Zootaxa paper, Type3 font with PDF commands containing image data
		
		//	pro-iBiosphere trouble documents
//		pdfName = "Bian et al 2011.pdf"; // born-digital, cell-less table issue resolved
//		pdfName = "Cannonetal-PNAS7July2009.pdf"; // born-digital, problem with Type1C font, and word boxes off up and right (looks like hard stuff) ==> FIEXD (media box not at 0/0, but at 9/9, which seems to be exactly the offset ==> subtract lower right corner of media box from coordinates when computing word boxes in PDF page rendering)
//		pdfName = "FOC-Loranthaceae.pdf"; // born-digital, word bounds come out way too low from PDF rendering ==> FIXED (cap height was 0, substituting with ascent now in that case)
//		pdfName = "MallotusKongkandae.pdf"; // scanned, but image does not render properly in ICEPdf (comes out 0x0 from flate decode)
//		pdfName = "Nephrolepis.pdf"; // sannned, but image does not render properly in ICEPdf (comes out 0x0 from flate decode)
//		pdfName = "page0001.pdf"; // scanned, page image turned by 90 degrees, with image proportion inverse to media box
//		pdfName = "Wallace's line.pdf"; // born-digital, trouble rendering or decoding Type1C font (too many char comparisons), still have to reconstruct ArrayIndexOutOfBounds
//		pdfName = "Hovenkamp and Miyamoto 2005 - Nephrolepis.pdf"; // born-digital, Blumea 2005 layout, CID Tyoe 2 fonts, fortunately with ToUnicode mappings
//		pdfName = "Van Tien Tran 2013.pdf"; // born-digital, Blumea 2013 layout, TrueType fonts, but fortunately with ToUnicode mappings
		
		//	Smithsonian sample docs
//		pdfName = "94_Norris_web_FINAL.pdf";
//		pdfName = "94_Norris_web_FINAL_23-27.pdf";
//		pdfName = "SCZ640_FisherLudwig_web_FINAL.pdf"; // born-digital, some text in the background of first page that's invisible in Acrobat
		
		//	example from NYBG
//		pdfName = "NYBGExample-Chapter6.pdf";
		
		//	example from European Journal of Taxonomy
//		pdfName = "131-898-1-PB_pages_1_11.pdf"; // born-digital, for some reasons page headings not showing up, don't seem to be in page content
		
		//	example excerpt from BHL document
//		pdfName = "BHLExcerptExample.pdf"; // mainly scanned pages, with two born-digital meta data pages as a preface, requires rethinking the "find page images for all pages" check
//		pdfName = "BHLExcerptExample.cut.pdf"; // scanned pages, born-digital meta data pages removed, but original resolution not recoverable, as media box equal to image size in pixels (72 DPI ...)
//		pdfName = "BHLExample.cut.pdf"; // scanned pages, extracted from 418 page original document
//		
//		//	example from Greg Riccardi
//		pdfName = "SampleFromGregRiccardi.pdf";
		
//		//	mixed image and OCR text from Donat
//		pdfName = "marashi_najafi_library.pdf";
		
		//	pro-iBiosphere file from Sabrina
//		pdfName = "chenopodiacea_hegi_270_281.pdf"; // scanned to own specs, 300 DPI black and white
//		pdfName = "Guianas_Cheop_61_64_176.pdf"; // scanned to own specs, 300 DPI black and white
		
		//	pro-iBiosphere file from Quentin
//		pdfName = "Chenopodium vulvaria (Chenopodiaceae) - a species extinct.pdf"; // born-digital, in Polish
//		pdfName = "Darwiniana V19 p553.pdf"; // scanned, but text represented only as in born-digital, original page image wiped clean of text and used as page background
		
		//	pro-iBiospher files from Peter (same document, run through 5 different PDF generators)
//		pdfName = "generatorTest/A conspectus of the native and naturalized species of Nephrolepis in the world created as.pdf";
//		pdfName = "generatorTest/A conspectus of the native and naturalized species of Nephrolepis in the world optimized acrobat4.pdf";
//		pdfName = "generatorTest/A conspectus of the native and naturalized species of Nephrolepis in the world optimized.pdf";
//		pdfName = "generatorTest/A conspectus of the native and naturalized species of Nephrolepis in the world printed to.pdf";
//		pdfName = "generatorTest/A conspectus of the native and naturalized species of Nephrolepis in the world saved as.pdf";
		/* they all render fine, only structure recognition makes a block out of
		 * every line due to extremely wide line margins
		 */
		
		//	Swiss spider documents
//		pdfName = "Ono2009c.pdf"; // born-digital, fonts with misleading ToUnicode mappings
//		pdfName = "OttBrescovit2003.pdf"; // born-digital, page 0 with negative line matrix entries, currently renders upside down
		
//		//	PDFs from Hong & Elvis
//		pdfName = "AcaxanthumTullyandRazin1970.pdf"; // vertical text at right page edge, messing with line detection
//													 // in addition, caption on 2 is missed
////		pdfName = "Methanobacterium.aarhusense(1).pdf"; // renders fine
//		
//		//	PiB October workshop, unzip error in ZooTaxa file
//		pdfName = "zt01826p058.pdf"; // faulty font program, now failing gracefully and continuing
//		
//		//	Plazi Retreat October, file hangs somewhere
//		pdfName = "zt02534p036.pdf";
//		
//		//	File does not load on Windows8
//		pdfName = "zt03592p085.pdf";
		
		//	test PDF from Roos Mounce
//		pdfName = "zt03652p155_p31-p35.pdf";
//		pdfName = "zt03652p155.pdf";
		
		//	more test files from Jeremy
//		pdfName = "98_sulawesi_2012.pdf"; // text renders with one command per character, and all characters come in hex strings
//		pdfName = "zt00109.pdf"; // encrypted, no chance right now
//		pdfName = "zt00109.o.pdf"; // removed encryption with Acrobat, and works just fine
//		pdfName = "zt00619.pdf"; // encrypted, no chance right now
//		pdfName = "zt00619.o.pdf"; // removed encryption with Acrobat, and works just fine, TODO nice table for testing detection on page 3
//		pdfName = "zt00872.pdf"; // slightly faulty table column alignment on page 8, likely due to char width underestimation or kerning ==> solved (char width array problem)
//		pdfName = "19970730111_ftp.pdf"; // scanned, with born-digital looking text embedded, comes out better when loaded from image
		
		//	comprehensive tests from Jeremy
//		pdfName = "RSZ117_121.pdf"; // scanned PDF at 500 DPI, with embedded OCR, page cleanup destroying multiple dots ==> page cleanup fixed, was due to error in resolution correction
		pdfName = "RSZ117_121.bd.pdf"; // same as above, but born-digital version, TODO_ne massive problems with decoding SID fonts ==> some fraction characters below minimum match threshold (==> excluded from minimum), and some characters not completely surrounded by white pixels, causing problems with filling (white edge of one pixel added)
//		pdfName = "RSZ117_57.pdf"; // works OK after array index bugfix in Imaging
//		pdfName = "RSZ118_49.pdf"; // works OK after line metric correction bugfix
//		pdfName = "2010 Gao Li Artema atlanta a Pantropical Species New for.pdf"; // born-digital with identity text matrix, working with transformer matrix instead ==> OK after transformation matrix factored in on positioning and font sizing
//		pdfName = "2012 Lin Wu Gao Fei Courtship and mating behaviors of Pinelema bailongensis.pdf"; // born-digital image based, images coming in XObjects
//		pdfName = "2011 Tang On the Spider Fauna and a New Species of the.pdf"; // 
//		pdfName = "59_1.pdf"; // password protected encryption, little we can do about this one ...
//		pdfName = "AZS_20100104.pdf"; // even Acrobat 9.0 Professional requires some custom extension to render this one
//		pdfName = "AMN_3683.pdf"; // born-digital, text renders OK, but off toward upper right ==> OK after transformation matrix factored in on positioning
//		pdfName = "AMN_3732.pdf"; // born-digital, text renders somewhat wide ==> solved problem in char width computation, colliding with prior fix for other font type
//		pdfName = "AS_19_1_007_009_Mar_Xerophaeus.pdf"; // born-digital, some SID font with 'G<xy>' char IDs (<xy> being a two-digit hex number), characters not mapped to unicode, garbled copy&paste, from Russian generator, best save to images and OCR back
//		pdfName = "BAMNH_345.pdf"; // born-digital, test pages render fine ==> whole PDF renders fine ... at 2GB memory ==> think about page data caching (a lot easier with IMF)
//		pdfName = "ISZ_a11v1002.pdf"; // born-digital, renders fine, only male & female symboles embedded as images
//		pdfName = "ISZ_v101n4a10.pdf"; // born-digital, renders just fine
//		pdfName = "JA-38-02-212.pdf"; // born-digital, renders OK, only '°' mis-rendered to 'u', and also copy&pastes as 'u' ==> maybe store font name in IMF to facilitate custom find&replace
//		pdfName = "JNH_00222933_2010_512397.pdf"; // born-digital, with front matter, transformation matrix driving content off page ==> renders OK after factoring in transformation matrices as sequence
//		pdfName = "JNH_00222933_2012_707249.pdf"; // born-digital, renders perfectly fine
//		pdfName = "MEZ_1199-1213.pdf"; // born-digital, some word width problems ==> solved after factoring in font size in TJ array number re-positioning
//		pdfName = "MEZ_909-919.pdf"; // born-digital, renders perfectly fine
//		pdfName = "ZC_v27n2a17.pdf"; // born-digital, but encrypted with password, so no getting in
//		pdfName = "ZC_v27n3a13.pdf"; // born-digital, streams come up as Chinese in Notepad++, but renders perfectly fine
//		pdfName = "ZC_v28n1a15.pdf"; // born-digital, renders perfectly fine
//		pdfName = "ZJLS_j.1096-3642.2012.00831.x.pdf"; // born-digital, renders OK, some words below line (throughout ==> solved, problem with word extent correction), and male symbol mis-read (page 34 onward ==> missing to-unicode mapping, little we can do)
//		pdfName = "ZJLS_j.1096-3642.2012.00845.x.pdf"; // born-digital, renders perfectly fine
		
		//	80 MB PDF ...
//		pdfName = "27336.pdf"; // born-digital, some problems with SID fonts (decoding base alphabeth) ==> TODO try to use PostScript char names, and find out why letters are compared to punctuation marks
		
		//	'doesn't work' PDFs from Donat
//		pdfName = "27546.pdf"; // born digital, loads perfectly fine, safe for a few upper vs. lower case problems in all-caps parts
//		pdfName = "3868.pdf"; // scanned, block recognition problem in page 8 due to inline image ==> TODO try Gauss blur plus region coloring based block detection
//		pdfName = "zt3699p0001-0061.pdf"; // born-digital
//		pdfName = "3651_0250.pdf"; // scanned, with weird '%BeginPhotoshop' entry, which throws off parser ==> this is comments
//		pdfName = "3651_0250.s.pdf"; // scanned, above one saved from Acrobat Pro 9.0, opens perfectly fine
		pdfName = "27545.pdf"; // born-digital 13 MB PDF TODO test it
		
		//	recent one from Jeremy
//		pdfName = "Milleretal2014Anthracotheres.pdf"; // born-digital, renders perfectly fine
		pdfName = "arac-38-02-328.pdf"; // born-digital, renders OK, fonts take long to decode, all fonts mis-classified as bold
		
		
		
		long start = System.currentTimeMillis();
		int scaleFactor = 1;
		aimAtPage = -1; // TODO always set this to -1 for JAR export
		//	TODO try pages 12, 13, 16, 17, and 21 of Prasse 1979
		System.out.println("Aiming at page " + aimAtPage);
		
		if (false) {
			File pdfFile = new File(pdfDataPath, pdfName);
			FileInputStream pdfIn = new FileInputStream(pdfFile);
			BufferedInputStream bis = new BufferedInputStream(pdfIn);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			byte[] buffer = new byte[1024];
			int read;
			while ((read = bis.read(buffer, 0, buffer.length)) != -1)
				baos.write(buffer, 0, read);
			bis.close();
			byte[] bytes = baos.toByteArray();
			
			ImDocument doc = new ImDocument(pdfName);
			doc.setDocumentProperty("docId", pdfName);
			Document pdfDoc = new Document();
			pdfDoc.setInputStream(new ByteArrayInputStream(bytes), "");
			doc = pdfExtractor.loadGenericPdf(doc, pdfDoc, bytes, 1, null);
			return;
		}
		
		File docFile = new File(pdfDataPath, (pdfName + ".imf"));
		ImDocument doc;
		if (docFile.exists() && (aimAtPage == -1)) {
			BufferedInputStream docIn = new BufferedInputStream(new FileInputStream(docFile));
			doc = ImfIO.loadDocument(docIn);
			docIn.close();
			doc.setDocumentProperty("docId", pdfName);
		}
		else {
			File pdfFile = new File(pdfDataPath, pdfName);
			FileInputStream pdfIn = new FileInputStream(pdfFile);
			BufferedInputStream bis = new BufferedInputStream(pdfIn);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			byte[] buffer = new byte[1024];
			int read;
			while ((read = bis.read(buffer, 0, buffer.length)) != -1)
				baos.write(buffer, 0, read);
			bis.close();
			byte[] bytes = baos.toByteArray();
			
			doc = new ImDocument(pdfName);
			doc.setDocumentProperty("docId", pdfName);
			Document pdfDoc = new Document();
			pdfDoc.setInputStream(new ByteArrayInputStream(bytes), "");
			ProgressMonitorDialog pdm = new ProgressMonitorDialog(DialogFactory.getTopWindow(), "Loading PDF");
			pdm.setSize(400, 100);
			pdm.setLocationRelativeTo(null);
			pdm.popUp(false);
			
//			doc = pdfExtractor.loadImagePdf(doc, pdfDoc, bytes, scaleFactor, null);
//			doc = pdfExtractor.loadImagePdfBlocks(doc, pdfDoc, bytes, scaleFactor, null);
//			doc = pdfExtractor.loadImagePdfPages(doc, pdfDoc, bytes, scaleFactor, null);
			doc = pdfExtractor.loadTextPdf(doc, pdfDoc, bytes, pdm);
//			doc = pdfExtractor.loadGenericPdf(doc, pdfDoc, bytes, scaleFactor, null);
			
//			ProcessStatusMonitor psm = new ProcessStatusMonitor() {
//				public void setStep(String step) {
//					System.out.println(step);
//				}
//				public void setLabel(String text) {
//					System.out.println(text);
//					if ((aimAtPage != -1) && (text.indexOf("OCR") != -1))
//						throw new RuntimeException("NO OCR FOR NOW");
//				}
//				public void setBaseProgress(int baseProgress) {}
//				public void setMaxProgress(int maxProgress) {}
//				public void setProgress(int progress) {}
//			};
//			try {
//				doc = pdfExtractor.loadGenericPdf(doc, pdfDoc, bytes, scaleFactor, psm);
//			}
//			catch (Exception e) {
//				System.out.println(e.getMessage());
//			}
			
			if (aimAtPage == -1) {
				BufferedOutputStream docOut = new BufferedOutputStream(new FileOutputStream(docFile));
				ImfIO.storeDocument(doc, docOut);
				docOut.flush();
				docOut.close();
			}
			else {
				System.out.println("PDF loaded:");
//				AnnotationUtils.writeXML(doc, new OutputStreamWriter(System.out));
//				System.out.println();
			}
		}
		
		ImPage[] pages = doc.getPages();
		final ImageDisplayDialog dialog = new ImageDisplayDialog(pdfName);
		for (int p = 0; p < pages.length; p++) {
			if ((aimAtPage != -1) && (p != aimAtPage))
				continue;
			PageImage pi = pages[p].getImage();
//			if ("P".equals(pages[p].firstValue())) {
//				System.out.println("Page " + p + " is generic, filling in structure");
//				PageImageConverter.fillInPageRegions(pages[p], pi, null);
//			}
			System.gc();
			
//			if (pages[p].getAnnotations(MutableAnnotation.PARAGRAPH_TYPE).length == 0) {
//				System.out.println("Splitting blocks in page " + p);
//				PageAnalysis.splitIntoParagraphs(pages[p].getMutableAnnotations(BLOCK_ANNOTATION_TYPE), pi.currentDpi, null);
//				PageAnalysis.computeColumnLayout(pages[p].getMutableAnnotations(COLUMN_ANNOTATION_TYPE), pi.currentDpi);
//			}
//			if (true)
//				continue;
//			
			int scaleDown = 1;
			int displayDpi = (((aimAtPage == -1) && (pages.length > 5)) ? 150 : 300);
			while ((scaleDown * displayDpi) < pi.currentDpi)
				scaleDown++;
			System.out.println("Scaledown is " + scaleDown);
			int imageMargin = 8;
			final BufferedImage bi = new BufferedImage(((pi.image.getWidth()/scaleDown)+(imageMargin*2)), ((pi.image.getHeight()/scaleDown)+(imageMargin*2)), BufferedImage.TYPE_3BYTE_BGR);
			bi.getGraphics().setColor(Color.WHITE);
			bi.getGraphics().fillRect(0, 0, bi.getWidth(), bi.getHeight());
			bi.getGraphics().drawImage(pi.image, imageMargin, imageMargin, (pi.image.getWidth()/scaleDown), (pi.image.getHeight()/scaleDown), null);
			
			ImWord[] words = pages[p].getWords();
			for (int w = 0; w < words.length; w++) {
//				Color col = (!"".equals(words[w].getAttribute(STRING_ATTRIBUTE, "")) ? Color.YELLOW : Color.RED);
//				Color col = ("".equals(words[w].getAttribute(BOLD_ATTRIBUTE, "")) ? Color.YELLOW : Color.RED);
				Color col = ("".equals(words[w].getAttribute(ITALICS_ATTRIBUTE, "")) ? ("".equals(words[w].getAttribute(BOLD_ATTRIBUTE, "")) ? Color.YELLOW : Color.MAGENTA) : Color.RED);
				paintBox(bi, scaleDown, words[w], col.getRGB(), imageMargin, 0);
				paintBaseline(bi, scaleDown, words[w], Color.PINK.getRGB(), imageMargin);
			}
//			
//			ImRegion[] lines = pages[p].getRegions(LINE_ANNOTATION_TYPE);
//			for (int l = 0; l < lines.length; l++)
//				paintBox(bi, scaleDown, lines[l], Color.ORANGE.getRGB(), imageMargin, 1);
//			
//			Annotation[] cells = pages[p].getAnnotations("td");
//			for (int c = 0; c < cells.length; c++)
//				paintBox(bi, scaleDown, cells[c], Color.RED.getRGB(), imageMargin, 1);
//			
			ImRegion[] blocks = pages[p].getRegions(BLOCK_ANNOTATION_TYPE);
			for (int b = 0; b < blocks.length; b++)
				paintBox(bi, scaleDown, blocks[b], Color.BLUE.getRGB(), imageMargin, 3);
			
			ImRegion[] paragraphs = pages[p].getRegions(MutableAnnotation.PARAGRAPH_TYPE);
			for (int g = 0; g < paragraphs.length; g++)
				paintBox(bi, scaleDown, paragraphs[g], Color.GREEN.getRGB(), imageMargin, 2);
			
			ImRegion[] columns = pages[p].getRegions(COLUMN_ANNOTATION_TYPE);
			for (int c = 0; c < columns.length; c++)
				paintBox(bi, scaleDown, columns[c], Color.DARK_GRAY.getRGB(), imageMargin, 5);
			
			ImRegion[] regions = pages[p].getRegions(REGION_ANNOTATION_TYPE);
			for (int r = 0; r < regions.length; r++)
				paintBox(bi, scaleDown, regions[r], Color.GRAY.getRGB(), imageMargin, 5);
			
			dialog.addImage(bi, ("Page " + p));
		}
		
		System.out.println("Document done in " + ((System.currentTimeMillis() - start) / 1000) + " seconds");
		
		dialog.setSize(800, 1000);
		dialog.setLocationRelativeTo(null);
		Thread dialogThread = new Thread() {
			public void run() {
				dialog.setVisible(true);
			}
		};
		dialogThread.start();
		pdfExtractor.shutdown();
	}
	
	private static void paintBox(BufferedImage bi, int scaleDown, ImRegion region, int rgb, int im, int exp) {
		try {
			for (int c = ((region.bounds.left/scaleDown) - exp); c < ((region.bounds.right/scaleDown) + exp); c++) {
				bi.setRGB((c+im), ((region.bounds.top/scaleDown)+im-exp), rgb);
				bi.setRGB((c+im), ((region.bounds.bottom/scaleDown)-1+im+exp), rgb);
			}
			for (int r = ((region.bounds.top/scaleDown) - exp); r < ((region.bounds.bottom/scaleDown) + exp); r++) {
				bi.setRGB(((region.bounds.left/scaleDown)+im-exp), (r+im), rgb);
				bi.setRGB(((region.bounds.right/scaleDown)-1+im+exp), (r+im), rgb);
			}
		} catch (Exception e) {}
	}
	
	private static void paintBaseline(BufferedImage bi, int scaleDown, ImWord word, int rgb, int im) {
		int baseline = Integer.parseInt((String) word.getAttribute(BASELINE_ATTRIBUTE, "-1"));
		if (baseline < word.bounds.top) {
//			System.out.println("Cannot paint baseline");
			return;
		}
		for (int c = (word.bounds.left/scaleDown); c < (word.bounds.right/scaleDown); c++)
			bi.setRGB((c+im), ((baseline/scaleDown)+im), rgb);
	}
	
	private static String getPageImageName(String docId, int pageId) {
		return (docId + "." + getPageIdString(pageId, 4) + "." + IMAGE_FORMAT);
	}
	private static String getPageIdString(int pn, int length) {
		String pns = ("" + pn);
		while (pns.length() < length)
			pns = ("0" + pns);
		return pns;
	}
}
