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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import javax.swing.UIManager;

import de.uka.ipd.idaho.gamta.MutableAnnotation;
import de.uka.ipd.idaho.gamta.util.AnalyzerDataProvider;
import de.uka.ipd.idaho.gamta.util.AnalyzerDataProviderFileBased;
import de.uka.ipd.idaho.gamta.util.imaging.ImagingConstants;
import de.uka.ipd.idaho.gamta.util.imaging.PageImage;
import de.uka.ipd.idaho.gamta.util.imaging.PageImageInputStream;
import de.uka.ipd.idaho.gamta.util.imaging.PageImageStore;
import de.uka.ipd.idaho.gamta.util.imaging.PageImageStore.AbstractPageImageStore;
import de.uka.ipd.idaho.gamta.util.swing.DialogFactory;
import de.uka.ipd.idaho.gamta.util.swing.ProgressMonitorDialog;
import de.uka.ipd.idaho.im.ImDocument;
import de.uka.ipd.idaho.im.ImFont;
import de.uka.ipd.idaho.im.ImPage;
import de.uka.ipd.idaho.im.ImRegion;
import de.uka.ipd.idaho.im.ImSupplement;
import de.uka.ipd.idaho.im.ImWord;
import de.uka.ipd.idaho.im.pdf.PdfExtractor;
import de.uka.ipd.idaho.im.pdf.PdfFontDecoder;
import de.uka.ipd.idaho.im.util.ImDocumentIO;
import de.uka.ipd.idaho.im.util.ImSupplementCache;
import de.uka.ipd.idaho.im.util.ImUtils;
import de.uka.ipd.idaho.im.utilities.ImageDisplayDialog;

/**
 * Test engine for PDF import into Image Markup data model.
 * 
 * @author sautter
 */
public class PdfExtractorTest implements ImagingConstants {
	public static int aimAtPage = -1;
	private static Set pageIDs = new HashSet();
	public static void main(String[] args) throws Exception {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e) {}
		
		final File pdfDataPath = new File("E:/Testdaten/PdfExtract/");
		final AnalyzerDataProvider dataProvider = new AnalyzerDataProviderFileBased(pdfDataPath);
		
		//	register page image source
		PageImageStore pis = new AbstractPageImageStore() {
			HashMap byteCache = new HashMap();
			public boolean isPageImageAvailable(String name) {
				if (!name.endsWith(IMAGE_FORMAT))
					name += ("." + IMAGE_FORMAT);
				if (dataProvider.isDataAvailable(name))
					return true;
				else if ((aimAtPage == -1) && (pageIDs == null))
					return false;
				else return this.byteCache.containsKey(name);
			}
			public PageImageInputStream getPageImageAsStream(String name) throws IOException {
				if (!name.endsWith(IMAGE_FORMAT))
					name += ("." + IMAGE_FORMAT);
				if (dataProvider.isDataAvailable(name))
					return new PageImageInputStream(dataProvider.getInputStream(name), this);
				else if ((aimAtPage == -1) && (pageIDs == null))
					return null;
				else if (this.byteCache.containsKey(name))
					return new PageImageInputStream(new ByteArrayInputStream((byte[]) this.byteCache.get(name)), this);
				else return null;
			}
			public boolean storePageImage(String name, PageImage pageImage) throws IOException {
				if (!name.endsWith(IMAGE_FORMAT))
					name += ("." + IMAGE_FORMAT);
				final String fName = name;
				try {
					OutputStream imageOut;
					if ((aimAtPage == -1) && (pageIDs == null))
						imageOut = dataProvider.getOutputStream(name);
					else imageOut = new ByteArrayOutputStream() {
						public void close() throws IOException {
							super.close();
							byteCache.put(fName, this.toByteArray());
						}
					};
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
		pdfName = "zt03652p155.pdf";
		
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
//		pdfName = "RSZ117_121.bd.pdf"; // same as above, but born-digital version, TODO_ne massive problems with decoding SID fonts ==> some fraction characters below minimum match threshold (==> excluded from minimum), and some characters not completely surrounded by white pixels, causing problems with filling (white edge of one pixel added)
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
//		pdfName = "27545.pdf"; // born-digital 13 MB PDF TODO test it
		
		//	recent one from Jeremy
//		pdfName = "Milleretal2014Anthracotheres.pdf"; // born-digital, renders perfectly fine
//		pdfName = "arac-38-02-328.pdf"; /* born-digital, renders OK, fonts take long to decode, all fonts mis-classified as bold
//		 	 ==> reason fonts take forever to load is them containing a plethora of spurious characters, which are never even used in the document text
//		 	 ==> most chars actually are what their postscript name (if any) says, but some are not in some fonts, rendering mathematical symbols instead
//		 	 ==> TODO_ne load fonts at least half on demand:
//		 	 	 - first go through page texts and record which chars are used in which font
//		 	 	 - then only load and decode those chars that are actually used
//		 	 */
		
		//	very crappy one from Donat
//		pdfName = "Cynoglossum_cheranganiense.pdf"; // crappy 150 DPI scanned document, both page images linked to both pages !!!
//		pdfName = "Cynoglossum_cheranganiense.a.pdf"; // same, only saved back with Acrobat Pro 9.0 ==> no changes
//		pdfName = "Cynoglossum_cheranganiense.ab.pdf"; // same, only bound into new PDF with Acrobat Pro 9.0 ==> no changes
		
		//	Zootaxa from Donat
//		pdfName = "zt03683p356.pdf"; // born-digital, renders fine, safe for page 3, which is rotated by 90° degrees ==> TODO have to detect this and rotate back on rendering, resulting in a landscape page image
		
		//	problem PDF from Donat
//		pdfName = "s4.pdf"; // born-digital, but converter fails to find words ==> document text wrapped in PDF forms !!!
//		pdfName = "s4.pdfa.pdf"; // saved as PDF/A with Acrobat 9 Pro, doesn't change anything
//		pdfName = "s4.p.pdf"; // printed to a PDF with Acrobat 9 Pro, this one seems to work, but embedded fonts take forever to decode
//		pdfName = "s4.ps.pdf"; // printed to a PDF with Acrobat 9 Pro, optimized for small file size, but same as before
//		pdfName = "Enabling Accessible Knowledge.pdf"; // printed from browser ... text rendered with graphics commands, little we can do but OCR
//		pdfName = "NHMD_LighthouseBirds_1887_15-17.pdf"; // scanned, two pages per image ==> solved, splitting landscape pages down the middle now
		
		//	problem PDFs from Jeremy
//		pdfName = "11664_Dankittipakul_&_Singtripop_2011_Rev_118_207-221.pdf"; // born-digital, overlapping words on page 4 TODO test font decoding with this one, has some non-postscript char names
//		pdfName = "sutory_cynoglossum.pdf"; // Donat reports font decoding problems ==> char name overwrite based on 'Differences' mapping caused problems ==> deactivated
//		pdfName = "arac-38-02-328.pdf"; // have to test said 'Differences' based char name overwrite
		
//		//	problems with fi and fl ligatures, fonts are not embedded, see top of page 13
//		pdfName = "dikow_2012.test.pdf";
//		pdfName = "dikowlondt_2000b.test.pdf"; // born-digital, male and femal symbols don't decode properly
		
//		//	excerpt from BHL scan, with two tailing born-digital meta pages; page media boxes blown, have to estimate DPI
//		pdfName = "RSZ_Pictet1893.pdf"; // estimating resolution works pretty well, however TODO cleanup doesn't handle all of page background
		
//		//	born-digital, problem with word spacing
//		pdfName = "SystEnt_Yuri2015.pdf"; // reducing narrow space width seems to have fixed it
		
//		//	born-digital, problem with "cross" splits in pages 15 and 17
//		pdfName = "ZJLS_Hertach2015.pdf"; // reducing narrow space width seems to have fixed it
		
//		//	anothe RSZ from Donat, problems with all-caps parts, and fonts not getting embedded properly, plus some mask image inversion
//		pdfName = "RSZ_Tanasevitch2014.pdf"; // ==> sanitizing to-Unicode mapping based on conflicts seems to do the trick
		
		//	more oddjobs from Donat
//		pdfName = "27978.pdf"; // sans-serif font without Unicode mapping on page 7 ==> solved
//		pdfName = "rebel_1931_trichoptilus_subtilis.pdf"; // scanned, pretty pale after background elimination, focus-based rotation detection fails on page 0 ==> fixed via detecting PDF internal figure rotation right from transformation matrix
//		pdfName = "s10.pdf"; // born-digital, very narrow block and column gaps from page 1 onward ==> fixed, cross-split prevention was wrecking havoc
//		pdfName = "Bannikov_Carnevale_2009.pdf"; // born-digital, block/column structure blown on page 0 ==> fixed, cross-split prevention was wrecking havoc
//		pdfName = "Plas_1972.pdf"; // scanned, image of page 1 comes up strange ==> two parts included at _different_ resolutions (shakin head in disbelief) ... scaling page image parts individually now
//		pdfName = "RSZ_Eguchi2006_115-131.pdf"; // born-digital, figure in page 2 mis-decoded ==> now successfully following reference chain alon _several_ XObject references, but somehow ImageMagick cannot seem to decode the final JPX
//		pdfName = "27998.pdf"; // born-digital, char+accent don't combine in pages 0 and 13 ==> now also considering accent might come _before_ base character
//		pdfName = "ZM1967042005.pdf"; // scanned, and loads OK, but "unknown compression method" error when loading as generic
//		pdfName = "Kullander_ramirezi_1980.pdf"; // contains XObject _forms_ which need to be filtered out
//		pdfName = "Myers and Harry 1948 Apistogramma ramirezi.pdf"; // scanned, pages come up very small
//		pdfName = "Moluccas049-060_Loebl.pdf"; // born-digital, male and female symbols (e.g. page 1) not decoded properly, 'artsy' line around pages hampering structure detection
//		pdfName = "11331_Azarkina_&_Logunov_2010_Afr_51_163-182.pdf"; // born-digital, 'fi' ligature problem (CIDFontFile) ==> solved with newly built TrueType font handler
		
//		//	figure extraction test cases
//		pdfName = "Moluccas049-060_Loebl.pdf"; // mask images with [1,0] decode (page 1, page 3) that require black background, masks flipped upside down
//		pdfName = "RSZ_Tanasevitch2014.pdf"; // image with additive 'Separation' color space (page 2), need to invert images
//		pdfName = "Ono2009c.pdf"; // mask image with [0,1] decode in 1200 DPI (page 2) that requires black background 
//		pdfName = "dikow_2012.test.pdf"; // grayscale images wit ICCBased color space (page 5, page 9) come up inverted
//		pdfName = "dikow_2012.test.pdf"; // mask image with [0,1] decoder (page 16) that requires black background
		
//		//	test file from Torsten
//		pdfName = "hesse_1974.pdf"; // scanned, image cleanup destroys some parts of title on page 0 ==> try and prevent this !!! ==> increase contrast treshold for background elimination, as words were blurred into darkish page edge
		
		//	files from Jeremy
//		pdfName = "11331_Azarkina_&_Logunov_2010_Afr_51_163-182.pdf"; // born-digital, TODOne male and female symbols mis-recognized
//		pdfName = "11350_Cala_Riquelme_2010_Nov_3_85-86.pdf"; // born-digital, TODOne uses CIDFontType0, which isn't implemented thus far
//		pdfName = "11633_Edwards_&_Jennings_2010_Pec_86.1_1-2.pdf"; // born-digital, TODOne some characters mis-decoded due to CIDs off by one in TrueType file after some range ==> fixed by now interpreting cmap table as well
//		pdfName = "11404_Guo_&_Zhu_2010_Jou_30_93-96.pdf"; // scanned, loads OK
//		pdfName = "11429_Jastrzebski_2010_Gen_21_115-120.pdf"; // born-digital, TODOne some characters mis-decoded due to CIDs off by one in TrueType file after some range ==> maybe try chars to left and right in TrueType decoding
//		pdfName = "11430_Jastrzebski_2010_Gen_21_319-323.pdf"; // born-digital, small-caps problems
//		pdfName = "11437_Kaldari_2010_Pec_82.1_1-4.pdf"; // born-digital, TODOne char rendering instructions in plain font (CIDs) off by 1 above 57 (???) ==> fixed by now interpreting cmap table as well
//		pdfName = "11468_Logunov_2010_Bul_15_85-90.pdf"; // born-digital, TODOne fonts not stored completely
//		pdfName = "11471_Logunov_&_Deza_2010_Act_59_21-23.pdf"; // born-digital, TODOne whitespace filled with some obscure blank chars, clogging column gap --> chars found to be extremely wide (1000) according to font, effectively bringing along an implicit space after them ==> implemented implicit space detection
		pdfName = "11471_Logunov_&_Deza_2010_Act_59_21-23.pdf"; // born-digital, TODO multiple font styles mixed up in single font, requires either splitting up font, or char-wise style handling
//		pdfName = "11532_Richardson_2010_Zoo_2418_1-49.pdf"; // born-digital, memory issues, likely due to image supplements (==> TODOne move PDF decoding to sub-JVM), some minor symbol issues with male, female, and degree
//		pdfName = "11617_Yamasaki_2010_Act_59_63-66.pdf"; // born-digital, encrypted, with password, fonts heavily obfuscated
//		pdfName = "12775_Ikeda_2010_Kis_98_21_32.pdf"; // born-digital, mostly Chinese fonts make font decoder labour forever TODO add isChinese flag for font decoding
		
		//	another one from Donat
//		pdfName = "perrichot_2015_cretaceous_research_french_amber_baikuris_maximus.pdf"; // born-digital, TODOne page 4 is landscape
//		pdfName = "Sennikov2014.pdf"; // born-digital, mysterious image in page 3
		
		//	Heraklion Workshop June 2015
//		pdfName = "Cumacea_Puritan expedition.pdf"; // scanned (BHL excerpt) with meta page
//		pdfName = "ZK_article_1928.pdf"; // born-digital, memory issues
//		pdfName = "Lopez_et_al.pdf"; // born-digital, really badass embedded fonts (decoding with multiple errors), plus word spacing problems
		
		//	encrypted PDFs
//		pdfName = "zt00109.pdf"; // TODO_ne
//		pdfName = "zt00619.pdf"; // TODO_ne
//		pdfName = "ZC_v27n2a17.pdf"; // TODO_ne
//		pdfName = "59_1.pdf"; // TODO_ne
//		pdfName = "11617_Yamasaki_2010_Act_59_63-66.pdf"; // TODO_ne
		
		//	some figures not extracted properly
		pdfName = "Moore & Gosliner 2014.pdf";
		
		//	some more problematic Zootaxa
//		pdfName = "Zootaxa/zt00001.pdf"; // strange image bounds on pages 2, 3, 5, 6, and 8 ==> now adjusting figure bounds if figure is upside down or right side left
//		pdfName = "Zootaxa/zt00020.pdf"; // same problem, same fix
//		pdfName = "Zootaxa/zt00094.pdf"; // same problem, same fix
		
		//	figure problems
//		pdfName = "390-715-1-PB.pdf"; // TODO_ne supplements for figures on page 3 come up blank ==> found "Unsupported color conversion request", now re-rendering via IcePDF
//		
//		//	figure and font problems
//		pdfName = "Neumeyer et al. 2015.pdf"; // TODOne figures embedded as stripes, require assembling, pages 2, 3, 4, 5, 7 ==> added figure assembing mechanism
//		pdfName = "367-370_Loebl.pdf"; // TODOne image on page 2 comes up black ==> IcePDF problem, cannot decode JPX
//		pdfName = "AP_54-4_289-294.pdf"; // TODOne find problem in page 1 ==> IcePDF problem, cannot decode JPX
//		
//		//	old (<50) ZooKeys volumes, with font and text-in-figure issues
//		pdfName = "ZooKeys/ZK_article_1939.pdf"; // TODO_ne spaces after ligatures compensated by text-from-array shifts
//		pdfName = "ZooKeys/ZK_article_1939.pdf"; // TODO_ne words rendered inside figures
		
		//	very weird stuff ... from Donat, of course ...
//		pdfName = "295-306_Disney & Prescher.pdf"; // TODO_ne reversed word on page 4
//		pdfName = "295-306_Disney & Prescher.pdf"; // TODO_ne distorted headings on page 11
//		pdfName = "295-306_Disney & Prescher.pdf"; // TODO mingled words 'A male' on page 6
//		pdfName = "295-306_Disney & Prescher.pdf"; // TODO_ne mis-decoded 1-bit RGB bitmap on page 4
		
//		//	scanned PDF with high-accuracy embedded OCR from ABBYY
//		pdfName = "Flora of Japan 2001 Wakabayashi Mitella_abbyypdf.pdf";
		
//		//	TODOne problem PDF from Donat
//		pdfName = "Zootaxa/zt03600p105.pdf";
//		pdfName = "Zootaxa/zt03616p283.pdf";
//		pdfName = "Zootaxa/zt03619p074.pdf";
//		pdfName = "Zootaxa/zt03619p182.pdf";
//		pdfName = "Zootaxa/zt03619p194.pdf";
		
		//	another trouble maker
//		pdfName = "Zootaxa/zt04093p363.pdf"; // TODOne block flipping trouble in pages 3-7 ==> 3 OK, 4 OK, 5 trouble, 6 OK, 7 OK
//		pdfName = "Zootaxa/zt04093p363.pdf"; // TODOne block flipping trouble in page 5 ==> removing out-of-page words now
//		pdfName = "Zootaxa/zt04093p363.pdf"; // TODOne spacing problem in last line of table on flipped pages 4 & 5 ==> font indicated char decides on word spacing, not actually rendered glyph
		
//		//	TODOne first PhytoTaxa
//		pdfName = "pt00020p025.pdf";
		
		//	different embedded OCR options in ABBYY
//		pdfName = "ABBYY_Output_Options/seg-001_2013_86_3-4_a_007_d_text_and_pictures_only.pdf"; // loads OK as born-digital, but TODOne figure images come up gray in page images (page 5) ==> IcePDF just cannot handle JPX decode ==> TODO try and modify image format after decoding via ImageMagick
//		pdfName = "ABBYY_Output_Options/seg-001_2013_86_3-4_a_007_d_text_over_image.pdf"; // comes up without words, even though words are extracted ==> TODOne words intended to be rendered, page image destroyed (cleared of word images)
//		pdfName = "ABBYY_Output_Options/seg-001_2013_86_3-4_a_007_d_text_under_image.pdf"; // comes up without words, even though words are extracted ==> TODOne find out where wrds get lost ==> words OK now
//		pdfName = "ABBYY_Output_Options/seg-001_2013_86_3-4_a_007_d_text_under_image.pdf"; // pages with figures come up without word images (e.g. page 5) ==> TODOne find out where wrods get lost ==> refined page image selection and overpaint filtering
		
//		//	landscape scans with single portrait pages in the middle, and a lot of other stuff on the sides
//		pdfName = "vol4_499_506_textUnderPageImage.pdf";
		
		//	font issues
//		pdfName = "Zootaxa/zt02418p049.pdf"; // TODOne ghost 's' in 's-' on page 7, should be '-' alone ==> not reproducible
//		pdfName = "Carbayo_et_al-2013-Zoologica_Scripta.pdf"; // TODOne font issues, especially with ligatures ==> works great with glyph neighborhood analysis
//		pdfName = "Carbayo_et_al-2013-Zoologica_Scripta.pdf"; // TODOne combining diacritic markers not merged (e.g. on page 18)
//		pdfName = "Zootaxa/zt02418p049.pdf"; // TODOne male symbols mis-decoded to 3, female symbols mis-decoded to some strange variant of 'P' (e.g. on page 8) ==> factoring char signature difference into comparison appears to do the trick
//		pdfName = "Zootaxa/zt02418p049.pdf"; // TODOne figure caption numbers on page 42 NOT merged ==> established absolute minimum for narrow spaces
//		pdfName = "11430_Jastrzebski_2010_Gen_21_319-323.pdf"; // TODOne born-digital, small-caps problems ==> appear to be covered by new histogram alignment backed glyph matching
//		pdfName = "RSZ_Tanasevitch2014.pdf"; // TODOne small-caps problems ==> sanitizing to-Unicode mapping based on conflicts seems to do the trick
//		pdfName = "Zootaxa/zootaxa.4107.4.pdf"; // TODOne good deal of diacritics, good for tests ==> works just great, apart from some dashes (endash/mdash mixup)
//		pdfName = "laciny_et_al_2015_z_arbeitsgem_osterr_entomol_new_diacamma.pdf"; // TODOne make small-caps work
//		pdfName = "RSZ_Tanasevitch2014.pdf"; // TODO male and female symbol problems ==> simply too little similarity to comparison font versions
//		pdfName = "Flora_of_China_Volume_19_Caprifoliaceae.pdf";
		
		//pdfName = "Zootaxa/zt04098p560.pdf"; // TODO keep mask image figure on pages 2 and 3 working 
		
		//	some more that "do not work"
//		pdfName = "Amitus vignus.pdf"; ==> solved (now catching) faulty TrueType fonts that caused an exception
//		pdfName = "Romblonella longinoi online.pdf"; ==> dito
		
//		//	TODO customer document
//		pdfName = "SoutheastAsiaEnergyOutlook_WEO2013SpecialReport.pdf"; // TODOne 'i' misdecoded to 'I' on page 1
//		pdfName = "SoutheastAsiaEnergyOutlook_WEO2013SpecialReport.pdf"; // TODO page bounds too small in page 4, loses words
		
		//	TODO Latin/Cyrillic mix incurring font issues
		pdfName = "13402_Esyunin_._Sozontov_2015_Eur_14_325_333.pdf";
		
		//	TODO need to cut leading two pages (with style templates !!!)
		pdfName = "JourNatHist/00222930010002766.pdf";
//		
//		//	more font issues
//		pdfName = "JourNatHist/00222930010002766.plain.pdf"; // TODOne example of Type1 font file (finally !!!) implement it !!!
//		
//		//	scrambled images, need to repair
//		pdfName = "Zootaxa/zt00201p001.pdf";
//		
//		//	mask images not extracting properly
//		pdfName = "Zootaxa/zt04173p201.pdf"; // TODOne get PDF native vector graphics images on pages 7, 10, 12, 13, 14, and 16
//		
//		//	multi-part images
//		pdfName = "C70.1.13-18.pdf"; // TODO get multi-part image on page 2, and TODO resolve conflict with overlayed graphics (separator lines ...)
//		
//		//	some more trouble makers
//		pdfName = "jHerpetology_42_4_750.pdf"; // TODOne investigate this sucker (CMYK JPEG on page 5) ==> ImageMagick with '-negate' parameter seems to do the trick quite well
//		pdfName = "C66.2.311-315.pdf"; // TODOne fonts include some character without a name, causing a null lookup ==> added respective checks
//		pdfName = "C66.2.311-315.pdf"; // TODOne whole pages covered by graphics regions ==> added filter based on render order number, catching "bright on white"
//		pdfName = "C66.2.311-315.pdf"; // TODOne some words are conflated, check merging thresholds ==> OK
//		pdfName = "C66.2.311-315.pdf"; // TODOne some words are conflated, find miraculous spaces in TJ arrays (might be parsing problem, as space is char 0 (null))
//		pdfName = "C70.1.43-48.pdf"; // TODOne implement named color spaces (get from page resource dictionary)
//		pdfName = "C70.1.43-48.pdf"; // TODOne resolve small-caps problems, e.g. on page 2 ==> hard one, as fonts contain characters THRICE (capital, lower case, and small-caps, so exclusion does not work)
//		pdfName = "C70.1.57-60.pdf"; // TODOne dito ==> same as above
//		pdfName = "C70.1.13-18.pdf"; // TODOne dito ==> same as above
//		pdfName = "C71.1.117-126.pdf"; // TODOne figure out why image on page 3 comes up yellow on white instead of black on white ==> Indexed color space with DeviceN (N = 2) base ...
//		pdfName = "C71.1.117-126.pdf"; // TODOne image on page 3 comes up yellow on white ==> implement our own color space handling
//		pdfName = "C71.1.117-126.pdf"; // TODOne table background on page 6 comes up way too dark ... alpha problem?
//		pdfName = "C71.1.135-141.pdf"; // TODOne test pages 2, 3, and 4 for graphics and images ==> images render just fine with own color spaces
//		pdfName = "1_Jones_deChambrier.pdf";
//		
//		//	bold words fail to be extracted ==> rendered as graphics, little we can do ...
//		pdfName = "FoliaEntHung_2012_Vol_73_105.pdf";
//		
//		//	some nice embedded graphics, but fail to extract ...
//		pdfName = "SystEnt.39.4.691.pdf"; // TODOne check vector graphics diagrams on pages 4, 5, 7, 8 ==> improved aggregation, including label words
		
		//	TODOne trouble maker with embedded OCR Donat says doesn't come up right
		//	==> some words split up, but text mostly quite OK
//		pdfName = "RONQUIST_et_al-2001-Zoological_Journal_of_the_Linnean_Society_n.pdf"; // ==> effective space width measures in around 2.0 - 2.8
//		pdfName = "Zootaxa/zt04173p201.pdf"; // born-digital PDF for comparison ==> effective space width measures in around 0.4 - 0.5
//		pdfName = "C66.2.311-315.pdf"; // born-digital PDF for comparison ==> effective space width measures in around 0.21 - 0.28
//		
//		//	TODOne test watermark brightness
//		pdfName = "RefParse.long.published.pdf"; // watermarks "Author's personal copy" on each page from 2 onward
//		
//		//	TODOne correct flipped page 3
//		pdfName = "Ogawa etal 2016_Acta_Ento.pdf";
		
//		//	TODOne check autonomous page image rendering and subsequent blocking
//		pdfName = "Ogawa etal 2016_Acta_Ento.pdf";
//		pdfName = "C70.1.13-18.pdf"; // TODOne resolve small-caps problems, e.g. on page 2 ==> hard one, as fonts contain characters THRICE (capital, lower case, and small-caps, so exclusion does not work)
//		pdfName = "C70.1.43-48.pdf"; // TODOne resolve small-caps problems, e.g. on page 2 ==> hard one, as fonts contain characters THRICE (capital, lower case, and small-caps, so exclusion does not work)
//		pdfName = "C70.1.57-60.pdf"; // TODOne resolve small-caps problems, e.g. on page 2 ==> hard one, as fonts contain characters THRICE (capital, lower case, and small-caps, so exclusion does not work)
//		pdfName = "Zootaxa/zt04173p201.pdf"; // TODOne make sure to merge graphics on pages 7, 10, 12, 13, 14, and 16
//		pdfName = "C70.1.13-18.pdf"; // TODOne dito ==> same as above
//		pdfName = "SystEnt.39.4.691.pdf";
//		pdfName = "Zootaxa/zt00201p001.pdf"; // TODOne make multi-part figures in pages 1 and 2 merge into one region
//		pdfName = "C71.1.135-141.pdf"; // make sure words stay out of figure in page 4
		
		//	trouble maker (IRS tax return form) from Terry, mistaken for landscape for some reason ...
//		pdfName = "131887440_201406_990.pdf"; // ==> TODOne facilitate deactivating page orientation detection
		
		//	TODOne some more trouble makers
//		pdfName = "ZoolMidEast.62.3.261.pdf"; // TODOne load error ==> solved color initialization problem
//		pdfName = "ZoolMidEast.62.3.261.pdf"; // TODOne figure on page 2 not showing, though extracted OK ==> added supplement mapping for multi-part figures
//		pdfName = "Candollea.c2016v711a16.pdf"; // TODOne filter overlay of figure on page 4
//		pdfName = "Alther_et_al_2016.pdf"; // TODOne trouble with 4410 DPI (!) image in page 0 ==> altered IcePDF fallback rendering
//		pdfName = "Ghielmi_et_al-2016-Journal_of_Zoological_Systematics_and_Evolutionary_Research.pdf"; // TODOne resolve text stream dismemberment on page 9 ==> not reproducible
//		pdfName = "Ghielmi_et_al-2016-Journal_of_Zoological_Systematics_and_Evolutionary_Research.pdf"; // TODOne flip table grid graphics on page 5 and 8
//		pdfName = "10.5281zenodo.163452_Seehausen_Guenther.pdf"; // TODOne figure out small-caps problem in page 18 ==> now ignoring missing CMAP in descendant TrueType fonts
//		pdfName = "10.5281zenodo.163452_Seehausen_Guenther.pdf"; // TODOne looks like some words in page 0 are over-rendered by graphics ==> ... and rightfully so ...
//		pdfName = "10.5281zenodo.163452_Seehausen_Guenther.pdf"; // TODOne sort out words that are over-rendered by graphics and then more words in page 0
//		pdfName = "AM52_38_49.pdf"; // TODOne license logon mis-rendered on page 0 ==> now filling paths as a whole, instead of individual sub paths
//		pdfName = "Waser2016.OrgDiversEvol.pdf"; // TODOne check Figure 7 on page 8 ==> renders and extracts perfectly fine ...
//		pdfName = "AM52_38_49.pdf"; // TODOne make non-Unicode 'Th' ligature work ==> added decoding exemption filter for characters Unicode mapping to out-of-Unicode ligatures
//		pdfName = "Zootaxa/zt00021.pdf"; // TODOne check graphics on page 8 ==> vertical word rendering bugfix in SVG generator
//		pdfName = "zt00904.pdf"; // TODOne assess 'flower' logo top-left on page 0 ==> supporting inline images now
//		pdfName = "Zootaxa/zt03691p559.pdf"; // TODOne check figure in page 4 ==> comes out just fine, only that it's TWO figures, with one obfuscating the other
		
		//	errors identified during PdfFigureRepair effort
//		pdfName = "ZootaxaTrouble/B45CFF93FF93FFD3A13BA86BFFD8FFAB.zt03640p257.pdf"; // TODOne Color parameter outside of expected range: Red Green Blue ==> Fix 'Lab' color space implementation (something in page 15, looks like _whited-out_ text ...)
//		pdfName = "ZootaxaTrouble/B45CFF93FF93FFD3A13BA86BFFD8FFAB.zt03640p257.pdf"; // TODO Fix 'Lab' color space implementation (something in page 15, looks like _whited-out_ text ...)
//		pdfName = "ZootaxaTrouble/BE28FFB4FFB5FF89FFB62450FF9CE37A.zt03635p223.pdf"; // TODOne Color parameter outside of expected range: Red Green Blue ==> Fix 'Lab' color space implementation (some table cell backgrounds on pages 2 and 3, come out as cyan with value range catch)
//		pdfName = "ZootaxaTrouble/BE28FFB4FFB5FF89FFB62450FF9CE37A.zt03635p223.pdf"; // TODO Fix 'Lab' color space implementation (some table cell backgrounds on pages 2 and 3, come out as cyan with value range catch)
//		pdfName = "ZootaxaTrouble/FFB1FFE6FFD6FFCCFFAFFFF3FFC48E27.zt03635p590.pdf"; // TODOne Error reading PNG image data ==> looks OK now ... must have been runtime problem
//		pdfName = "ZootaxaTrouble/FFCF7778195EFFF9DB24BA768836FFF5.zt03666p092.pdf"; // TODOne Source and destination must store pixels as INT: some blend mode issue in page 8 (with empty 1x2 pixel image !!!) ==> catching RuntimeExceptions on figure rendering now
//		pdfName = "ZootaxaTrouble/FFAA0768F31CDF48D1680E3CCA49FFE9.zt03887p582.pdf"; // TODOne Unknown Color Space 'Pattern' in page 3 ==> added fallback colors for now as patterns are not supported just yet (first available example this ...)
//		pdfName = "ZootaxaTrouble/FFAA0768F31CDF48D1680E3CCA49FFE9.zt03887p582.pdf"; // TODO Implement pattern color spaces as repeated form-style Do recursions
//		pdfName = "ZootaxaTrouble/FFC2FFBAFF98FFDF7C78FF990669FFB7.zt03635p438.pdf"; // TODOne Index: 0, Size: 0 graphics storage error in page 7 ==> considering wider page content box now if page has flipped content
//		pdfName = "ZootaxaTrouble/FFC2FFBAFF98FFDF7C78FF990669FFB7.zt03635p438.pdf"; // TODOne (flipped) words on page 7 mingled up ==> added absolute cap-off for measured implicit space width, which in this case ended up just too high as a result of varying table column spacing
//		pdfName = "ZootaxaTrouble/FFBFFF955816FFCFFB700B51381E0E1A.zt03737p279.pdf"; // TODOne Index: 0, Size: 0 ==> resolved with above fix
//		pdfName = "ZootaxaTrouble/FFB5FFD4FFDAFFF75E26FFECAD76FF9A.zt03669p242.pdf"; // TODOne Index: 0, Size: 0 ==> resolved with above fix
//		pdfName = "ZootaxaTrouble/FF841039040BFFC2BD1CFFCC2434FFED.zt03686p243.pdf"; // TODOne Index: 0, Size: 0 ==> resolved with above fix
//		pdfName = "ZootaxaTrouble/772EFFD20E1DAD14FFC5FFB9C9480102.zt04034p363.pdf"; // TODOne Index: 0, Size: 0 ==> resolved with above fix
//		pdfName = "ZootaxaTrouble/BD4DFFA8FF83FFF24C1EFFD0E01EDE56.zt03974p390.pdf"; // TODOne Index: 0, Size: 0 ==> resolved with above fix
//		pdfName = "ZootaxaTrouble/BD4DFFA8FF83FFF24C1EFFD0E01EDE56.zt03974p390.pdf"; // TODOne bottom-up table on pages 8 and 9 has vertical column labels upside-down ==> implement that font direction ==> added upside-down font direction all the way through page block flipping, after which it should disappear
//		pdfName = "ZootaxaTrouble/FFA64504BC7DD923FFA2FFDC1610FFCC.zt03925p093.pdf"; // TODOne check figures ==> decode just fine
//		pdfName = "ZootaxaTrouble/FF9AA331C045B82A8270C427FFA17C4C.zt03752p278.pdf"; // TODOne check 'negative width' error ==> renders fine with fix below
//		pdfName = "ZootaxaTrouble/FFA6177A547AD339A412B724FFD4FFB3.zt03640p199.pdf"; // TODOne check 'negative width' error ==> renders fine with fix below
//		pdfName = "ZootaxaTrouble/FFE2FFF711391969631F22167C5BFFF8.zt03717p592.pdf"; // TODOne check 'negative width' error ==> renders fine with fix below
//		pdfName = "ZootaxaTrouble/FFAEF05E4D723A01FFFEFFF7FFBEFFBB.zt03914p156.pdf"; // TODOne check 'negative width' error ==> renders fine with fix below
//		pdfName = "ZootaxaTrouble/FFCD654BFF8CFF8B7D74FF93F571FFDD.zt03936p558.pdf"; // TODOne check 'negative width' error ==> renders fine with fix below
//		pdfName = "ZootaxaTrouble/7B26F628DB2CFFFEFFDCFF81FF98FF9C.zt03827p516.pdf"; // TODOne check 'negative width' error ==> renders fine with fix below
//		pdfName = "ZootaxaTrouble/FF89FFD4512CFF88FF95491DFF8BFFC5.zootaxa.4161.1.1.pdf"; // TODOne check 'negative width' error ==> renders fine with fix below
//		pdfName = "ZootaxaTrouble/FFC8FFAFFF8B847DFF86FFA0FFD9B918.zootaxa.4125.1.1.pdf"; // TODOne check 'negative width' error ==> renders fine with fix below
//		pdfName = "ZootaxaTrouble/FFDCFFCB4F3472122128FFED6E2DFFDE.zootaxa.4173.3.2.pdf"; // TODOne check 'negative width' error ==> renders fine with fix below
//		pdfName = "ZootaxaTrouble/FFDDFFF8B716FFD1FFCCF675E618A149.zootaxa.4061.4.6.pdf"; // TODOne check 'negative width' error ==> measurement zooming problem in presence of inversion ==> switched to absolute values
//		pdfName = "ZootaxaTrouble/FFA64504BC7DD923FFA2FFDC1610FFCC.zt03925p093.pdf"; // TODOne check flipping table in page 6, and also why table grid not recognized as such ==> try and increase distance tolerance for vertical lines longer than half the whole hight of bottom-up block
//		pdfName = "ZootaxaTrouble/FFA64504BC7DD923FFA2FFDC1610FFCC.zt03925p093.pdf"; // TODOne check flipping table in page 6, and also why table grid not recognized as such ==> flooding page with orientation of words and images provides orientation for graphics now
//		pdfName = "ZootaxaTrouble/FFCD654BFF8CFF8B7D74FF93F571FFDD.zt03936p558.pdf"; // TODOne check flipping table in page 8, and also why table grid not recognized as such ==> flooding fix works here as well
//		pdfName = "ZootaxaTrouble/FF9AA331C045B82A8270C427FFA17C4C.zt03752p278.pdf"; // TODOne make sure graphics on pages 4, 6, 8, 14, and 20 are recognized as such ==> text just too dense for general case of labels, changing that likely to do more harm than good
//		pdfName = "ZootaxaTrouble/F108FFC62E23FFCFC51D58415609FF6E.zootaxa.4372.2.4.pdf"; // TODOne check fonts in page 0 ==> come up just fine
		
		//	more trouble makers ...
//		pdfName = "Ghielmi_et_al-2016-Journal_of_Zoological_Systematics_and_Evolutionary_Research.pdf"; // TODOne check this ==> decodes just fine now, error likely due to incomplete flipping of table on page 5
//		pdfName = "Ogawa etal 2016_Acta_Ento.pdf"; // TODOne check this ==> decodes just fine
//		pdfName = "aaag2737.full.pdf"; // TODOne make sure layout artwork doesn't come up as images (image-based column separator lines ...)
//		pdfName = "LBB_0048_2_1339-1492.pdf"; // TODOne check images on pages 110 - 116 ==> JPX images which ImageMagick doesn't read ... little we can do
//		pdfName = "Waser2016.OrgDiversEvol.pdf"; // TODOne check figures ==> look OK, just need to replace them in TB
//		pdfName = "Grubisha et al 2014.pdf"; // TODOne check figure citations on page 5 ==> skipped as too faint ==> check color ==> Separation color space, colorant named 'blue-link' ... ==> properly implemented tint transformation in Separation color space
//		pdfName = "Grubisha et al 2014.pdf"; // TODOne check why graphics are too high up in page 5 ==> adjusted rendering translation
//		pdfName = "entomologischeBlatterColeoptera.112.1.203.pdf"; // TODOne check why main text comes up as labels ==> cut makers on page edge rendered as single path, creating full-page graphics
//		pdfName = "entomologischeBlatterColeoptera.112.1.203.pdf"; // TODOne split up cut makers on page edges (rendered as single path) into multiple paths so they don't form full-page graphics
//		pdfName = "Lysipomia.mitsyae_Taxon.pdf"; // TODO what's wrong with this one? ==> just all too complex ... even Acrobat takes some 10 seconds to open it ...
//		pdfName = "jBryology.10.1080_03736687.2016.1186858.pdf"; // TODOne figure out content orientation stall in page 7 ==> _deactivated_ legacy page rotation adjustment of word font orientation (which was intended to map to words on IcePDF page images), now relying on page content flipping instead
//		pdfName = "jBryology.10.1080_03736687.2016.1186858.pdf"; // TODOne check out-of-page word cleanup in page 7 ==> very same page rotation based problem ...
//		pdfName = "jBryology.10.1080_03736687.2016.1186858.pdf"; // TODOne find that figure error Donat reported ==> none ... cannot seem to reproduce exception ...
//		pdfName = "jBryology.10.1080_03736687.2016.1186858.pdf"; // TODOne check figures on page 1, 3, and 6 ... appear inverted ==> using specified alternative now in deciding whether or not to invert on ICCBased color space
		
		//	yet more trouble ...
//		pdfName = "Esyunin_._Kazantsev_2008_Art_16_245_250.pdf"; // TODOne check fonts (Latin/Cyrillic mix) ==> no Unicode mapping at all, and glyphs shuffled and then numbered sequentially, needs full OCR decoding for each and every character
//		pdfName = "Fu_Zhang_._Zhu_2009_Art_17_169_173.pdf"; // TODOne dito ==> dito
//		pdfName = "Han_Morris_Ou_Shu_Huang_2017.pdf"; // TODOne check vector graphics parts for Figure 3 on page 2 ==> renders just fine
//		pdfName = "Han_Morris_Ou_Shu_Huang_2017.pdf"; // TODOne check export of vector graphics parts for Figure 3 on page 2 ==> works just fine
//		pdfName = "Han_Morris_Ou_Shu_Huang_2017.pdf"; // TODOne check why caption dragged into vector graphics part of Figure 3 on page 2 ==> gaps arond line graphics just too narrow for block splitting
//		pdfName = "2460FF9DFFA5CA23D37EFFB0A3195D70.pdf"; // TODOne check 'male' symbol, e.g. on Page 8 ==> OCR double-check messes this up ... that does happen to happen
//		pdfName = "2460FF9DFFA5CA23D37EFFB0A3195D70.pdf"; // TODOne check why some words lose font binding (e.g. 'male' symbol) despite fontName and fontCharCodes attributes present, and why only _some_ characters affected ==> chars affected if code byte > 255
//		pdfName = "2460FF9DFFA5CA23D37EFFB0A3195D70.pdf"; // TODOne check how to handle chars whose code is > 255 (e.g. 'male' symbol) ==> substitute with <= 255 unused byte, OR encode as 4 bytes, marked as such by a G-V (0-F + G) first HEX digit ==> added substitution mechanism
//		pdfName = "floraDerSchweizVol1_OCR_cerastium.pdf"; // TODOne check split words, e.g. in page 3 ==> comes from ABBYY, little we could do ...
//		pdfName = "floraEuropaeaConsolidatedIndex_OCRr.pdf"; // TODOne check decoding failure ==> fonts referenced _without_ indirection, causing null pointer exception in debug output ... checking that case now
//		pdfName = "10.1186s40850-017-0013-2.pdf"; // TODOne check this ==> all sorts of trouble loading Type1 font ==> extended PostScript interpreter and Type 1 font loading
//		pdfName = "JourNatHist/00222930010002766.plain.pdf"; // TODOne example of Type1 font file (finally !!!) implement it !!!
//		pdfName = "Skoracki_Flannery_Spicer_2008.pdf"; // TODOne check figure in Page 2, shows up only half ways in page image (which fully there in supplement ...) ==> moved tiled figure merge-up after background graphics elimination to preserve original RONs for latter
//		pdfName = "159-163_Lobl_Ogawa.pdf"; // TODOne check male and female symbols, and why they don't edit ==> come up just fine now, at least with "render only"
//		pdfName = "159-163_Lobl_Ogawa.pdf"; // TODOne check inverted drawing on page 3 ==> now checking inversion for 'Separation' color space with colorant 'Black'
//		pdfName = "peerj.articles.3007.pdf"; // TODOne investigate more PostScript problems, likely from Type1 font ==> looks like a few invalid char strings ...
//		pdfName = "peerj.articles.3007.pdf"; // TODOne check out how to handle invalid char strings (looks like some missed terminators resulting in conflations) ==> fixed charstring parsing problem (was actually due to missed terminators)
//		pdfName = "peerj.articles.3007.pdf"; // TODOne check placement of graphics on Page 0 (we might finally have forms with matrices used for scaling and transposition, as several graphics pile up in lower left corner) ==> translating form content now
//		pdfName = "peerj.articles.3007.pdf"; // TODOne figure out how to handle 1100 DPI (!!!) figures (even Acrobat has problems ...) ==> Extractor tool with figure disk caching and single-core mode did the trick
//		pdfName = "procEntomolSocWash.119.1.78.pdf"; // TODOne check parsing / page content exception ==> garphics state stack underflow in Page 1 ==> renders just fine after catching that
//		pdfName = "ThailandNaturalHistoryMuseumJournal.10.2.67.pdf"; // TODOne try and remove watermarks from figures ==> added alpha-based filter
//		pdfName = "ThailandNaturalHistoryMuseumJournal.10.2.67.pdf"; // TODOne figure out why page title missing on odd pages (1, 3, 5, 7) ==> refrained from pre-computing composite alpha
//		pdfName = "peerj.articles.3007.pdf"; // TODOne check figures on Pages 9, 14, 19, 22, 27, 31, 35, 36 ==> decode OK here, but missed by converter tool, likely due to memory issues ==> work OK in single-core mode with 4GB of RAM (!!!)
//		pdfName = "vitaMalacologica.14.1_2016_BankNeubertEnidaeIran.pdf"; // TODOne check out why char codes in font LLQCOU+ArialMT on page 0 messed up on storage (no >255 codes or anything) ==> base font of derived font also used as main font, incurring name collision ==> marking derived fonts with name suffix now
//		pdfName = "vitaMalacologica.14.1_2016_BankNeubertEnidaeIran.pdf"; // TODOne check wrongful small-caps correction of 'v' in page 0 ==> not reproducible on single page
//		pdfName = "vitaMalacologica.14.1_2016_BankNeubertEnidaeIran.pdf"; // TODOne check wrongful small-caps correction of 'v' in page 0 when decoding whole document ==> not reproducible on whole document, either
//		pdfName = "ZootaxaTrouble/zt00382.pdf"; // TODOne check why caption start not detected in page 5 ==> comes up just fine ... hard to tell what's wrong with server document
//		pdfName = "candollea.c2016v712a1.pdf"; // TODOne check figure on page 3 ==> bugfix in TextBlockActionsProvider
//		pdfName = "candollea.c2016v712a1.pdf"; // TODOne check table words on page 5 ==> decodes just fine, found display issue with table background (non-white parts of page image rendering over markup)
//		pdfName = "candollea.c2016v712a1.pdf"; // TODOne fix ImDocumentMarkupPanel to properly display table in page 5 ==> rendering only pitch black parts of page image on top of markup now, rest below
//		pdfName = "159-163_Lobl_Ogawa.pdf"; // TODOne check figure on page 3, comes up white on black ==> well, used to come up white on black ... just fine now
//		pdfName = "0222933.2016.1257074.pdf"; // TODOne check out page image vs. markup rendering on page 1 ==> looks OK with some little adjsutment to "pitch-black"
//		pdfName = "American_Journal_of_Primatology.10.1002_ajp.22631.pdf"; // TODOne check spaces on page 13 (most likely have to revisit space acceptance fraction threshold) ==> now observing following TJ array shift in implicit space decision
//		pdfName = "plants-05-00023.pdf"; // TODOne check why this one does not open ==> PostScript issues in Type1 fonts (Page 0 fixed, Page 2 to go)
//		pdfName = "plants-05-00023.pdf"; // TODOne fix PostScript issues in Type1 fonts on Page 2 ==> corrected binary char string boundary detection
//		pdfName = "plants-05-00023.pdf"; // TODOne check out duplicate word rendering, e.g. on Pages 4 and 5 ==> those words _are_ duplicate ...
//		pdfName = "plants-05-00023.pdf"; // TODO try and filter out duplicate words, e.g. on Pages 4 and 5
//		pdfName = "naturae-4-pdf.pdf"; // TODOne check out fonts in page 9 (small-caps) ==> adjusted skipping rules for char rendering in font decoder, enabling optical check for all chars
//		pdfName = "az2015n2a5.pdf"; // TODOne check out graphics on pages 6 and 13 ==> page 6 is just fine, page 13 get the words flipped ==> prevent this
//		pdfName = "az2015n2a5.pdf"; // TODOne prevent label word flipping in graphics on page 6 ==> added flip plausibility assessment
//		pdfName = "az2015n2a5.pdf"; // TODOne check small-caps on page 15 ... and other pages ... ==> only few problems left ==> should be most easily solved with neighborhood analysis
//		pdfName = "az2015n2a5.pdf"; // TODOne use neighborhood analysis on small-caps, e.g. on pages 0 and 15 ==> works like acharm
//		pdfName = "castanea_14-036r2.pdf"; // TODOne check font decoding, especially 'n' ==> works just fine
//		pdfName = "Bastawade2002FINAL.pdf"; // TODOne check scan extraction in pages 1, 3, 5 ==> there are 1x1 images zoomed close to 0 DPI, which hamper scan detection
//		pdfName = "Bastawade2002FINAL.pdf"; // TODOne fix scan extraction ==> added filter skipping over all too low resolution (< 36 DPI) page image parts
//		pdfName = "DankittipakulDeeleman2013.pdf"; // TODOne check inverted figure on page 10 ==> comes up just like supposed to
//		pdfName = "DankittipakulTavanocSingtripopa2013.pdf"; // TODOne check line drawings on pages 15, 16, 17, 19, 21, and 24
		
		//	EJT trouble
//		pdfName = "EJT/177-1031-1-PB.pdf"; // TODOne tokenization trouble, and all sorts of downstream problems
//		pdfName = "EJT/177-1031-1-PB.pdf"; // TODOne figure seems to reside outside XObejct dictionary in page 0 ==> now storing data reference in figure object
//		pdfName = "EJT/174-1008-1-SM.pdf"; // TODOne same as others ==> ditto
//		pdfName = "EJT/177-1031-1-PB.pdf"; // TODOne tokenization trouble, and all sorts of downstream problems in page 7 ==> UC mapping with combining accents, sanitizing that now
//		pdfName = "EJT/316-1658-1-PB.pdf"; // TODOne tokenization trouble, and all sorts of downstream problems ==> ditto
//		pdfName = "EJT/318-1660-1-SM.pdf"; // TODOne same as others ==> ditto
//		pdfName = "EJT/318-1660-1-SM.pdf"; // TODOne error in tokenization split, onethird UC-mapping to three-token '1/3' on page 2
//		pdfName = "EJT/337-1744-1-SM.pdf"; // TODOne 118MB behemoth with very high-resolution figure images, pages 82-85 don't render, likely due to 800 DPI mask images
//		pdfName = "EJT/317-1654-1-SM.pdf"; // TODOne char decoding issue on page 29 through 34 (dash mistaken for n with tilde) ==> implemented properly observing TrueType CMAP precedence
//		pdfName = "laciny_et_al_2015_z_arbeitsgem_osterr_entomol_new_diacamma.pdf"; // TODOne n-tilde vs. dash issue (e.g. on pages 52 and 53) ==> implemented properly observing TrueType CMAP precedence
//		pdfName = "EJT/405-2048-1-PB.pdf"; // TODOne get this sucker to decode ==> decodes just fine ...
//		pdfName = "EJT/163-941-1-PB.pdf"; // TODOne make sure page 3 is flipped ==> works just fine ...
//		pdfName = "EJT/2015-123.pdf"; // TODOne check figure colors, e.g. on pages 2 and 5 ==> render just fine ...
//		pdfName = "EJT/2015-123.pdf"; // TODOne make sure fi ligature decodes properly on page 7 (Unicode mapping seems to be faithful ...) ==> preventing unjustified small-caps correction of ligatures now
//		pdfName = "EJT/274-2017.pdf"; // TODOne check why this one doesn't decode ==> decodes just fine ...
//		pdfName = "EJT/274-2017.pdf"; // TODOne make sure table grid on page 32 is flipped as a whole ==> flipping paths cluster-wise now
		
		//	TODOne investigate these suckers
//		pdfName = "Trouble/abrusan__krambeck_2006.pdf"; // TODOne check word ordering exception ==> doesn't seem to occur any more in current version ...
//		pdfName = "Trouble/MyremcNews_21.117.pdf"; // TODOne check color spaces in page 3 ==> added correcting HEX4 lookup string to HEX2 in Indexed color space
//		pdfName = "De.Chambrier.etal_2017_Cichlidocestus.pdf"; // TODOne check font in 'References' section, page 11 ==> mis-aligned accents represented as their non-combining UC points, little we can do without wrecking havoc elsewhere
		
		//	some more Zootaxa ...
//		pdfName = "Zootaxa/zootaxa.4299.2.1.pdf"; // TODOne fix lower case C mistaken for upper case in page 8 ==> decodes just fine with right options set
//		pdfName = "Zootaxa/zootaxa.4300.4.5.pdf"; // TODOne check font in page 10 (no UC mapping) ==> decodes pretty much OK
//		pdfName = "Zootaxa/zootaxa.4319.3.7.pdf"; // TODOne check metadata extraction ==> publication date parsing problem, fixed
//		pdfName = "Zootaxa/zootaxa.4320.3.5.pdf"; // TODOne check fonts in page 0 (some erroneously come up small-caps) ==> not reproducible
//		pdfName = "Zootaxa/zootaxa.4320.3.5.pdf"; // TODOne check page number font in page 0 (erroneously comes up as monospaced) ==> All digits _do_ have same width, little we could do here
//		pdfName = "Zootaxa/zt02456p243.pdf"; // TODOne check size increase (8MB -> 256MB) ==> just a lot of white space on those 243 pages
//		pdfName = "Zootaxa/zootaxa.4323.4.3.pdf"; // TODOne check fonts ==> come up perfectly fine
//		pdfName = "Zootaxa/zootaxa.4324.2.6.pdf"; // TODOne figure out errors in this one ==> there don't seem to be any
//		pdfName = "Zootaxa/zootaxa.4299.4.8.pdf"; // TODOne figure out errors in this one ==> there don't seem to be any
//		pdfName = "Zootaxa/zootaxa.4295.1.1.pdf"; // TODOne figure out errors in this one ==> there don't seem to be any
//		pdfName = "Zootaxa/zootaxa.4325.1.1.pdf"; // TODOne check bottom-up tables (pages 41-52), don't seem to correctly flip to horizontal ==> flip just fine
//		pdfName = "Zootaxa/zootaxa.4290.1.3.pdf"; // TODOne check table in pages 7 and 8 ==> really a font problem (missing Unicode mapping), tables downstream error
//		pdfName = "Zootaxa/zootaxa.4291.1.1.pdf"; // TODOne check fonts in page 6 and following ==> table font is bad, Latin decoding loses 'o' ...
//		pdfName = "Zootaxa/zootaxa.4291.1.1.pdf"; // TODOne investigate lost 'o' ==> identical glyph exists twice in problem font, so one glyph runs out of candidates on decoding
//		pdfName = "Zootaxa/zootaxa.4291.1.1.pdf"; // TODOne catch identical 'o's via twin glyph detection ==> done
//		pdfName = "Zootaxa/zt03911p090.pdf"; // TODOne check figures ==> render perfectly fine now ...
//		pdfName = "Zootaxa/zootaxa.4324.1.3.pdf"; // TODOne investigate 'P' vs. 'p' in page 1 ==> works perfectly fine
//		pdfName = "Zootaxa/zootaxa.4341.4.2.pdf"; // TODOne check fonts in page 0, getting case mixup ==> fonts decode just fine
//		pdfName = "Zootaxa/zootaxa.4372.2.6.pdf"; // TODOne check fonts (letters missing) ==> unfriendly font requires 'Latin' decoding, but perfectly fine with the latter
//		pdfName = "Zootaxa/zootaxa.4085.3.3.pdf"; // TODOne check tables ==> good test example
//		pdfName = "Zootaxa/zootaxa.4353.1.9.pdf"; // TODOne check fonts on page 0 for all-caps errors ==> no such problem there ...
//		pdfName = "Zootaxa/zootaxa.4353.1.9.pdf"; // TODOne check fonts on page 9 ==> no UC mapping, need use Latin based decoding
//		pdfName = "Zootaxa/zootaxa.4353.2.2.pdf"; // TODOne check this ==> exception flipping page 29
//		pdfName = "Zootaxa/zootaxa.4353.2.8.pdf"; // TODOne check this ==> decodes just fine
//		pdfName = "Zootaxa/zootaxa.4353.2.2.pdf"; // TODOne properly flip table page 29 (good share is upside-down ...) ==> expanding to-flip area to transitive hull now
//		pdfName = "Zootaxa/zootaxa.4353.2.2.pdf"; // TODOne check why some lines in table grid come up light gray ==> just an effect of anti-aliazing on page image scale-down
//		pdfName = "Zootaxa/zootaxa.3872.5.5.pdf"; // TODOne check inverted figure in page 12 ==> comes up correctly now
//		pdfName = "Zootaxa/zootaxa.4367.1.1.pdf"; // TODOne check content flipping in pages 16, 18, 88, 89, 97-102 ==> content flipping is fine, but main font comes without UC mapping, causing words to disappear into whitespace
//		pdfName = "Zootaxa/zootaxa.4369.3.4.pdf"; // TODOne check why this one fails to open ==> image to flip to horizontal with no words to go with it at all ... fixed
//		pdfName = "Zootaxa/zt03974p390.pdf"; // TODOne check Image Repair decoding error ==> decodes just fine with current code ==> TODO update PDF libraries on server
//		pdfName = "Zootaxa/zootaxa.4375.3.5.pdf"; // TODOne check fonts in pages 0, 1, 4 ==> just another missing Unicode mapping
//		pdfName = "Zootaxa/zootaxa.4369.4.4.pdf"; // TODOne check table detection (average space width likely too large) ==> fixed (actual problem were serrated column margins)
//		pdfName = "Zootaxa/zootaxa.4379.2.3.pdf"; // TODOne check references ==> works perfectly fine
//		pdfName = "Zootaxa/zootaxa.4382.1.2.pdf"; // TODOne check bibliography ==> works perfectly fine
//		pdfName = "Zootaxa/zootaxa.4383.1.1.pdf"; // TODOne check decoding this one ==> decodes just fine (log shows page block flipping problem solved before)
//		pdfName = "ZootaxaTrouble/zootaxa.4407.3.1.pdf"; // TODOne check straight font case mix-up ==> could not reproduce with either of decoding font options
//		pdfName = "Zootaxa/zootaxa.4418.2.2.pdf"; // TODOne check bibliography ==> works perfectly fine
//		pdfName = "Zootaxa/zootaxa.4418.3.2.pdf"; // TODOne check bibliography ==> works perfectly fine
//		pdfName = "Zootaxa/zootaxa.4418.4.1.pdf"; // TODOne check bibliography ==> works perfectly fine
//		pdfName = "Zootaxa/zootaxa.4418.6.7.pdf"; // TODOne check W case mixup in bibliography ==> works perfectly fine
//		pdfName = "Zootaxa/zootaxa.3399.pdf"; // TODOne check figures ==> decode just fine
//		pdfName = "Zootaxa/zootaxa.3256.pdf"; // TODOne check figures ==> decode just fine
//		pdfName = "Zootaxa/zootaxa.4438.2.5.pdf"; // TODOne check why decoding fails ==> decodes just fine (batch problem caused by reference tagger, solved now)
//		pdfName = "Zootaxa/zootaxa.4438.2.12.pdf"; // TODOne check why decoding fails ==> decodes just fine
//		pdfName = "Zootaxa/zootaxa.4482.3.9.pdf"; // TODOne check color space error ==> added color space handling for image masks
//		pdfName = "Zootaxa/zootaxa.4483.1.1.pdf"; // TODOne check why this one doesn't convert ==> decodes just fine, safe for some phantom text boxes
//		pdfName = "Zootaxa/zootaxa.4483.1.1.pdf"; // TODOne figure out phantom text boxes on pages 3, 10, 11, 15, 16, 22, 23, 28, 31, 32 ==> now making sure potential text box actually _includes_ words
//		pdfName = "Zootaxa/zootaxa.4483.1.1.pdf"; // TODOne figure out font problems (appear to be related to recently-added IsoAdobeCharset in combination with derived fonts) ==> corrected handling of Type1C built-in charsets
//		pdfName = "Zootaxa/zootaxa.4483.1.3.pdf"; // TODOne check why this one doesn't convert ==> it does work ...
//		pdfName = "ZootaxaTrouble/Batch/zootaxa.4444.5.3.pdf"; // TODOne hangs up with enourmous memory usage ==> not here, only in batch ... ??? ... well, allowing 10GB of RAM is a bit of something ...
//		pdfName = "ZootaxaTrouble/Batch/zootaxa.4444.5.3.pdf"; // TODOne adjust table detection to handle table grid graphics in pages 4 and 19 (seem to come as single Graphics objects) ==> graphics come up just fine
//		pdfName = "ZootaxaTrouble/Batch/zootaxa.4479.1.1.pdf"; // TODOne check figure in page 127 ==> 1720 DPI (!!!) bitmap blowing out memory, now scaling down
//		pdfName = "ZootaxaTrouble/Batch/zootaxa.4487.1.1.pdf"; // TODOne make sure page 0 doesn't end up one big graphics object ==> clip bounds are for intersecting !!!
//		pdfName = "ZootaxaTrouble/Batch/zootaxa.4438.3.3.pdf"; // TODOne check fonts ==> decode perfectly fine
//		pdfName = "ZootaxaTrouble/Batch/zootaxa.4438.3.3.pdf"; // TODOne check caption block paragraphs on pages 4, 14 ==> should be OK now
//		pdfName = "Zootaxa/zootaxa.4551.5.1.pdf"; // TODOne check decoding error ==> broken download, PDF breaks up in mid-object with some HTML snippet error message
//		pdfName = "Zootaxa/zootaxa.4555.4.12.pdf"; // TODOne check for errors ==> very short erratum, nothing in usual place
//		pdfName = "Zootaxa/zootaxa.4568.2.2.pdf"; // TODOne check out exception in decoding (page 17) ==> now catching empty figures in structure detection (for whatever reason they might be included in the first place ...)
//		pdfName = "Zootaxa/zootaxa.4563.3.9.pdf"; // TODOne check figure on page 8 (takes in text) ==> clip path helps keep distance now
//		pdfName = "Zootaxa/zootaxa.4521.1.1.pdf"; // TODOne check decoding (page words in particular) ==> decodes just fine
//		pdfName = "Zootaxa/zootaxa.4521.1.12.pdf"; // TODOne check decoding (page words in particular) ==> decodes just fine
//		pdfName = "Zootaxa/zootaxa.4581.1.1.pdf"; // TODOne check out decoding exception ==> decodes just fine ...
//		pdfName = "Zootaxa/zootaxa.4581.1.1.pdf"; // TODOne check figures on page 18, 26, 32, 59, 82, 88 ==> decode just fine, at least in isolation ...
//		pdfName = "Zootaxa/zootaxa.4581.1.1.pdf"; // TODOne make sure figures on pages 18, 26, 32, 59, 82, 88 also decode for whole document ==> they do ... caching problem
//		pdfName = "Zootaxa/zt01159p068.pdf"; // TODOne check figures on pages 48 and 50 (eliminate some text) ==> fixed previously missed clip path command, and added clip path to figure word overlap computation
//		pdfName = "Zootaxa/zootaxa.4603.1.1.pdf"; // TODOne make sure years stay together ==> adjusted tokenizer
//		pdfName = "Zootaxa/zt01354p044.pdf"; // TODOne check figure in page 4 (comes up inverted) ==> comes up perfectly fine
//		pdfName = "Zootaxa/zt01196p032.pdf"; // TODOne check captions on pages 7 and 8 ==> decode perfectly normal
//		pdfName = "Zootaxa/zootaxa.4660.1.1.pdf"; // TODOne check fonts on page 7, "a" with tilde above comes up as double quote ==> comes up fine in single-page test
//		pdfName = "Zootaxa/zootaxa.4660.1.1.pdf"; // TODOne check fonts on page 7 in full-document mode, "a" with tilde above comes up as double quote ==> Unicode mapping is correct, but char image is wrong
//		pdfName = "Zootaxa/zootaxa.4660.1.1.pdf"; // TODOne check on page 7 how "a" with tilde above comes get double quote char image ==> now preventing CID collisions in presence of multiple TrueType encoding tables
//		pdfName = "Zootaxa/zootaxa.4660.1.1.pdf"; // TODOne check table on page 72, grid lines lost on content flip ==> was actually due to zero-width bounding box, preventing that altogether now
//		pdfName = "Zootaxa/zootaxa.4612.3.4.pdf"; // TODOne check figure on page 8, fails to decode ==> decodes just fine, but clipped to oblivion
//		pdfName = "Zootaxa/zootaxa.4612.3.4.pdf"; // TODOne make sure to not over-clip figure on page 8 ==> re-computing clip bounds now on correcting 180° rotated figure bounds
//		pdfName = "Zootaxa/zootaxa.4623.2.11.pdf"; // TODOne prevent images from black-holing in words (pages 1, 2, 3) ==> never get split off ... omitting figures in page structure image now
//		pdfName = "Zootaxa/zootaxa.4652.3.1.pdf"; // TODOne check why page 37 fails to flip ==> table comes as bitmap rendering upright, just depicting bottom-up text ...
//		pdfName = "Zootaxa/zt01298p060.pdf"; // TODOne make sure to properly handle figures and graphics on page 5 ==> handle perfectly fine, problem of super-early graphics representation
//		pdfName = "Zootaxa/zt02356p035.pdf"; // TODOne check if figures decode correctly ==> they do ... throughout
//		pdfName = "Zootaxa/zootaxa.4755.1.3.pdf"; // TODOne check figures in page 16 ==> using decoding predictor requires additional stream wrapper in IcePDF 5 ... added
//		pdfName = "Zootaxa/zootaxa.4755.1.3.pdf"; // TODOne check oversized figure image regions in page 16 ==> fixed column cross split repair glitch
//		pdfName = "Zootaxa/zootaxa.4422.1.4.pdf"; // TODOne check blocking in page 0 ==> switched cross split repair to two-round approach, requiring first merger to actually involve multiple columns
//		pdfName = "Zootaxa/zootaxa.4789.2.8.pdf"; // TODOne check decoding ==> works just fine
//		pdfName = "Zootaxa/zootaxa.4438.3.11.pdf"; // TODOne check conflation of Latin and Cyrillic letters in italics font ==> refusing synonymization of char codes with different UC mappings now
		
		//	... and other trouble
//		pdfName = "srep7.7378.pdf"; // TODOne fix small-caps in page 2 (UC mapping of those chars is all lower case) ==> resolved via neighborhood analysis
//		pdfName = "srep7.7378.pdf"; // TODOne fix dot mistaken for 'ä', and comma mistaken for 'á' ==> observing 16 bit strings now
//		pdfName = "B355.pdf"; // TODOne check images, e.g. on page 9 ==> correctly converting Lab color spaces now, and handling up to 64 bit per pixel
//		pdfName = "AMNH_Bulletin.355.pdf"; // TODOne make sure to properly handle and render monospaced font on pages 85 onward ==> added monospaced as fourth font proprty
//		pdfName = "AMNH_Bulletin.355.pdf"; // TODOne make monospaced a third alternative beside serif and sans-serif
//		pdfName = "AMNH_Bulletin.355.pdf"; // TODOne make sure to properly detect center-aligned paragraphs (table captions), e.g. on pages 5, 70, 72
//		pdfName = "AMNH_Bulletin.415.pdf"; // TODOne check figure in page 23, comes up inverted ==> comes up just fine, must have been older version of decoder causing the problem
//		pdfName = "AMNH_Bulletin.415.pdf"; // TODOne check why figure in page 18 not turned to horizontal ==> figure bitmap is turned, and _renders_ left-to-right ... little we can do here
//		pdfName = "quartReviewBiology693564.pdf"; // TODOne check font char codes (e.g. 'BIOLOGY' in page 0) ==> char codes are just fine
//		pdfName = "quartReviewBiology693564.pdf"; // TODOne check small-caps (e.g. 'BIoLoGY' in page 0) ==> added context based case correction for small-caps mistaken for lower case
//		pdfName = "Ruehli_Ikram_Bickel_KV31_mummies_klein.pdf"; // TODOne check serif font on page 0, totally garbled ==> appear to render OK now, but decode abysmally
//		pdfName = "Ruehli_Ikram_Bickel_KV31_mummies_klein.pdf"; // TODOne check serif font on page 0, decode abysmally ==> Unicode mapping looks broken, verification matches are total crap
//		pdfName = "Ruehli_Ikram_Bickel_KV31_mummies_klein.pdf"; // TODOne check UC mapping and decoding in serif font on page 0 ==> UC mapping parses correctly
//		pdfName = "Ruehli_Ikram_Bickel_KV31_mummies_klein.pdf"; // TODOne check assignment of char codes to glyphs in serif font on page 0 (comes up fine with no decoding) ==> 
//		pdfName = "Lopez-Guerrero.etal.2017.pdf"; // TODOne check fonts (number format exception in PostScrip parser) ==> was running into tailing zero padding, observing 'closefile' now
//		pdfName = "2017.Schistura.titan.pdf"; // TODOne check image in page page 5 (comes up only halfway) ==> two superimposed figures
//		pdfName = "2017.Schistura.titan.pdf"; // TODOne make sure to treat the white (or black) portion of mask images as transparent
//		pdfName = "systEnt.42.837-846.pdf"; // TODOne check figure in page 4 (not recognized as such, lacking label text) ==> just missing labels outside figure proper ... little we can do
//		pdfName = "systEnt.42.837-846.pdf"; // TODOne check figures in pages 6, 7 (come up inverted) ==> Separation color space with CMYK as alternative
//		pdfName = "systEnt.42.837-846.pdf"; // TODOne check additive color space handling in figures in pages 6, 7 (come up inverted) ==> made white-on-black detection less sensitive (stricter threshold)
//		pdfName = "candollea.c2017v721a6.pdf"; // TODOne check why font 'QMBZSF+Vaud-Italic' in page 1 (and onward) comes up incomplete (many characters missing in font editor) ==> two fonts with same base font, which resulted in a name collision and one font replacing the other ... renaming now
//		pdfName = "candollea.c2017v721a2.pdf"; // TODOne check fonts in page 3 ==> come up just fine
//		pdfName = "candollea.c2017v721a9.pdf"; // TODOne check small-caps in bibliography (pages 8 and 9) ==> come up perfectly fine with neighborhood analysis
//		pdfName = "Otto_Hill_2016.pdf"; // TODOne check figures, e.g. on page 76 (seem to mis-decode) ==> come up just fine ...
//		pdfName = "candollea.c2017v721a1_low.pdf"; // TODOne check font decoding with 'Latin full' ==> decodes perfectly fine
//		pdfName = "3533-13278-1-PB.pdf"; // TODOne check decoding this one ==> now observing that color spaces default to DeviceGray
//		pdfName = "hydrobiologia_A_1021297304421.pdf"; // TODOne check decoding of figure 1 on page 1 ==> figure comes in hundreds of horizontal stripes
//		pdfName = "hydrobiologia_A_1021297304421.pdf"; // TODOne make sure to correctly decode stripes ==> added CCITTFaxDecoding based bitmap decoding
//		pdfName = "BMJ_9310563.pdf"; // TODOne check decoding of Type1 font, some PostScript issue ==> weird bytes ahead of private dictionary, sipping them now
//		pdfName = "BMJ_9310563.pdf"; // TODOne check font rendering ==> hardened Type1 font renderer against erroneous data
//		pdfName = "2002_LonginoCoddingtonColwell.pdf"; // TODOne check font rendering, some PostScript issue ==> works just fine
//		pdfName = "EJE_eje-201202-0009.pdf"; // TODOne check decoding of figure 5 in page 5 ==> same CCITTFaxDecoding issue as above
//		pdfName = "WIPF_et_al-2005-Journal_of_Applied_Ecology.pdf"; // TODOne check sub title font in page 1 ==> decodes perfectly fine with 'Latin'
//		pdfName = "J.Biocon.2017.06.008.pdf"; // TODOne check figure 1 in page 5 ==> comes up perfectly fine
//		pdfName = "waldUndHolz.2917.07.23.pdf"; // TODOne check figure in page 1 ==> figure proper is fine, but some graphics content comes as PDF text, little we could do about that
//		pdfName = "c2017v721a5_low.pdf"; // TODOne check out figures ==> come up just fine (at least after observing filter _chain_ decode parameters)
//		pdfName = "Lopez-Guerrero.etal.2017.pdf"; // TODOne check fonts (all sorts of exceptions) ==> hardened Type1 and Type1C decoders against boken glyphs
//		pdfName = "Lopez-Guerrero.etal.2017.pdf"; // TODOne check font MinionPro-It (glyphs render way too small), e.g. in page 16 ==> improved hint collecting (for hintmask size), and implemented Type1C subroutines
//		pdfName = "Aura.new.epidemol.insights.CurrOpNeurol.2006.pdf"; // TODOne check PostScript error during char decoding ==> fixed stack handling in soubroutine calls
//		pdfName = "Aura.new.epidemol.insights.CurrOpNeurol.2006.pdf"; // TODOne check embedded font 'Helvetica', characters misfigured ==> ensured Type1 font operators get the intended operands from top of stack
//		pdfName = "CAMERA.Brain.2005.pdf"; // TODOne check graphics in page 5 ==> catching empty (out-of-page) path clusters now
//		pdfName = "CAMERA.Brain.2005.pdf"; // TODOne facilitate flipping table in page 5 despite right page margin 'Downloaded from ...' note ==> detect 'Downloaded from ...' notes as words repeating on same margin of every page
//		pdfName = "CAMERA.Brain.2005.pdf"; // TODOne implement 'Downloaded from ...' note removal ==> works fine
//		pdfName = "CAMERA.Brain.2005.pdf"; // TODOne make sure to not flip page content beyond left page edge (page 5) ==> need to flip in page box, not page content box 
//		pdfName = "Aura.review.Mosk.2013.Nat.pdf"; // TODOne check figure 4 on page 10 (fillings missing) ==> reproduced, found graphics to use shadings, whose implementation is yet to come
//		pdfName = "Aura.review.Mosk.2013.Nat.pdf"; // TODO implement shadings (some day) ==> 
//		pdfName = "Ferrari_oral.triptans_meta-analysis_Lancet.2001.pdf"; // TODOne check figure 1 (page 2) ==> render perfectly fine
//		pdfName = "Ferrari_oral.triptans_meta-analysis_Lancet.2001.pdf"; // TODOne check blocking on page 7 ==> renders faithful to Acrobar word bounding boxes, just a bad PDF ...
//		pdfName = "Ferrari_oral.triptans_meta-analysis_Lancet.2001.pdf"; // TODO figure out how Acrobat gets page 7 words in line, and emulate that ==> 
//		pdfName = "HA.at.USZ.Sokolovic.JHAPain.2013.pdf"; // TODOne check graphics in pages 3 and 5 ==> both render perfectly fine
//		pdfName = "Indocid.not.suma.Allodynia.2004.pdf"; // TODOne check graphics in page 3 ==> reder perfectly fine
//		pdfName = "contributions_36.pdf"; // TODOne check out fonts and figures (page 10, 12, 13) ==> fonts render just fine, some figures fail ImageMagick, so little we could do about the latter
//		pdfName = "z2017n2a2.pdf"; // TODOne check fonts ==> render perfectly fine
//		pdfName = "z2017n2a2.pdf"; // TODOne column splitting in page 1 ==> works fine, must be problem in style template
//		pdfName = "BTP_article_13003.pdf"; // TODOne check word width (right edges of serif font paragraphs serrated, should be flush) ==> multiple spaces synonymized on equal (empty) rendering sequence, confusing char spacing with word spacing, fixed now
//		pdfName = "huberschmidt_2017_barbonebriola_red.pdf"; // TODOne check image on page 13 ==> JPX that ImageMagick cannot seem to handle ... little we could do here
//		pdfName = "BatraxisLOBL.pdf"; // TODOne check why this one doesn't open ==> renders completely as vector graphics, no text to open at all
//		pdfName = "natureCommunications.s41467-017-01959-6.pdf"; // TODOne check this one ==> decodes just fine
//		pdfName = "natureCommunications.s41467-017-01959-6.pdf"; // TODOne make sure graphics on page 4 doesn't partially get flipped ==> no graphics ... tiled together bitmap images with mixed orientation, no chance of catching this
//		pdfName = "contributions_25_bluemlisalpicola_reduziert.pdf"; // TODOne check male and female symbols ==> don't decode into any font editable glyphs
//		pdfName = "contributions_25_bluemlisalpicola_reduziert.pdf"; // TODOne get male and female symbols in page 2 to decode (they _are_ vector glyphs) ==> now splitting non-UC-mapped two-byte chars only if individual bytes UC-mapped
//		pdfName = "natureScientificReports.s41598-017-09084-6.pdf"; // TODOne check caption word spacing ==> comes up just fine ==> TODO check concatenation in ImUtils.getString()
//		pdfName = "Cavin.etal.2017.pdf"; // TODOne check font decoding error ==> decodes just fine with VSTEM fix from previous PDF
//		pdfName = "Bigelow-2017-Iterating.between.Tools.to.Create.and.Edit.Visualizations.1.pdf"; // TODOne check this ==> color space decoding error
//		pdfName = "Bigelow-2017-Iterating.between.Tools.to.Create.and.Edit.Visualizations.1.pdf"; // TODOne fix color space decoding error in Page 7 ==> added initialization for Lab color space components (this PDF being the very first example with such a color space)
//		pdfName = "Bigelow-2017-Iterating.between.Tools.to.Create.and.Edit.Visualizations.1.pdf"; // TODOne fix font Type1 class cast error in page 0 ==> fixed (now removing subroutine numbers from stack) 
//		pdfName = "Bigelow-2017-Iterating.between.Tools.to.Create.and.Edit.Visualizations.1.pdf"; // TODOne fix font Type1 stack underflow error in Page 2, 5 ==> '255' indicates a four byte integer in Type1, not a double as in Type1C
//		pdfName = "Gleicher-2011-Visual.comparison.for.information.visualization.1.pdf";
//		pdfName = "Bigelow-2017-Iterating.between.Tools.to.Create.and.Edit.Visualizations.1.pdf"; // TODOne observe clipping paths in Page 4, 5 ==> 
//		pdfName = "journal.pone.0149556.PDF.pdf"; // TODOne check word spacing (ggi/418) ==> comes out just fine
//		pdfName = "1-s2.0-S0166061617300593-main.pdf"; // TODOne open this one ==> fixed page content flipping glitch
//		pdfName = "Struwe_50MajorTempPlantFamilies2017.pdf"; // TODOne check image decoding ==> images decode just fine, but get dragged into graphics objects together with page layout artwork
//		pdfName = "Struwe_50MajorTempPlantFamilies2017.pdf"; // TODOne prevent page layout artwork from wrecking havoc ==> layout artwork detection does the trick
//		pdfName = "Struwe_50MajorTempPlantFamilies2017.pdf"; // TODO shrink images to actual content (exclude white margins from marked region) ==>
//		pdfName = "Sprecher-Uebersax_Daccordi_EB35.pdf"; // TODOne check font decoding ==> no glyphs to render or decode at all, but merely a Cp1252 based mapping on the 0x80-0x9F range that is blank in ISO-8859-1
//		pdfName = "Sprecher-Uebersax_Daccordi_EB35.pdf"; // TODO extend Windows ISO-8859-1 mapping to also cover the character codes specific to Cp1252 ==>
//		pdfName = "jElectronicPublishing.21.1.pdf"; // TODOne check this one out ==> total crap ... but then, only an HTML printed to PDF
//		pdfName = "jElectronicPublishing.21.1.pdf"; // TODOne check how graphics painting page background can survive santization ==> some words _partially_ protrude left and right of it
//		pdfName = "indago.34.1.1-95.pdf"; // TODOne check decoding ==> decodes just fine
//		pdfName = "1806.02284.pdf"; // TODOne check fonts ==> some PostScript error in font on Page 4
//		pdfName = "1806.02284.pdf"; // TODOne fix PostScript error in font on Page 4 ==> implemented catch for Type1 flex feature
//		pdfName = "1806.02284.pdf"; // TODOne keep illustration font on Page 6 in bounds (looks like font size coming out 4-fold too big) ==> scaling down font size with form matrix now
//		pdfName = "londt_2012.pdf"; // TODO investigate sub-bounds word dimensions (espacially in bold words) ==> 
//		pdfName = "londt.copeland_2017.pdf"; // TODOne make sure serif font is recognized as such ==> was due to mis-assignment of glyphs to characters
//		pdfName = "londt.copeland_2017.pdf"; // TODOne investigate glyph to char assignment (many chars with same glyph) ==> implemented ISO Adobe Charset
//		pdfName = "londt_1977.pdf"; // TODOne make sure to detect page images with truncated edges ==> adjusted scan image assessment filter
//		pdfName = "nature02417.pdf"; // TODOne check page structure and text flow detection (pages 2 and 3) ==> looks like graphics getting in the way
//		pdfName = "nature02417.pdf"; // TODOne prevent page decoration from getting in the way of page structure and text flow detection (pages 2 and 3) ==> layout artwork detection does the trick
//		pdfName = "s41559-018-0667-3.pdf"; // TODOne check font decoding ==> fix char synonymization order flip
//		pdfName = "s41559-018-0667-3.pdf"; // TODOne make sure to treat text boxes as such in block detection (pages 1, 5, 6) ==> they actually are treated as such
//		pdfName = "s41559-018-0667-3.pdf"; // TODOne make sure to decode caption on page 6 ==> was excluded as too faint, now resetting external graphics state
//		pdfName = "s41559-018-0667-3.pdf"; // TODOne run block detection on individual text boxes ==> implemented, and works fine
//		pdfName = "McPhee_et_al_2018.pdf"; // TODOne check inverted image on page 18 ==> ICCBased with alternative DeviceCMYK inverts by default ...
//		pdfName = "McPhee_et_al_2018.pdf"; // TODOne observe Decode array to prevent inverting image on page 18 ==> works fine for now, at least if all components are [1,0] instead of [0,1]
//		pdfName = "Lysipomia.mitsyae_Taxon.pdf"; // image on page 2 does need inversion with ICCBased alt DeviceCMYK ...
//		pdfName = "candollea.86.1.2.pdf"; // TODOne check this one ==> opens OK, just no embedded OCR there
//		pdfName = "candollea.86.1.2.pdf"; // TODOne make sure to properly split blocks and columns ==> scan enhancement does the trick ... removes black shadows along page edges
//		pdfName = "candollea.86.1.2.pdf"; // TODO make sure to spread lines when doing OCR ==>
//		pdfName = "candollea.86.1.ocr.pdf"; // TODOne check this one ==> opens just fine ...
//		pdfName = "londt_1989b.pdf"; // TODOne check this one ==> opens OK ...
//		pdfName = "londt_2014d.pdf"; // TODOne check this one ==> opens OK, just a few font problems ...
//		pdfName = "londt_2014d.pdf"; // TODOne check all-caps font on page 17 ==> font with additional small-caps colliding with upper case letters
//		pdfName = "londt_2014d.pdf"; // TODO small-caps colliding with upper case letters in same font ==> 
//		pdfName = "Amaranthaceae_FDP_46.170-175.pdf"; // TODOne check line graphics mistaken for table grids ==> lot of label text falls into kind of grid ...
//		pdfName = "Amaranthaceae_FDP_46.170-175.pdf"; // TODOne catch line graphics mistaken for table grids (maybe based on text density) ==> added sparsity filter
//		pdfName = "mqmn-60-raven-hebron-smll.pdf"; // TODOne check figures 66 and 67 (pages 127 and 128) ==> page 127 comes up just fine, page 128 somewhat out of color
//		pdfName = "mqmn-60-raven-hebron-smll.pdf"; // TODOne check colors of figure 67 (page 128) ==> now shading transpaent parts of negated images (subtractive color spaces from offset print) against black backdrop
//		pdfName = "10.5281zenodo.1481114_vanTol_Guenther_2018.pdf"; // TODOne check small-caps mixup on page 0 ==> come up just fine
//		pdfName = "zoosystema2018n40a1_0.pdf"; // TODOne check error on page 6 ==> flipped content too far right
//		pdfName = "zoosystema2018n40a1_0.pdf"; // TODOne keep flipped content of page 6 on left page boundary ==> 
//		pdfName = "adan-40-1-2018-cotez_et_al_0.pdf"; // TODOne check this file ==> identical to zoosystema2018n40a1_0.pdf
//		pdfName = "z1997n4a12.pdf"; // TODOne make sure embedded OCR shows (https://github.com/gsautter/goldengate-imagine/issues/550) ==> implemented chaining of image data decoding filters
//		pdfName = "science.362.6417.897.pdf"; // TODOne decode this
//		pdfName = "science.362.6417.897.supp.pdf"; // TODOne check images in this one ==> they are graphics, and renders as PDF forms with a scaling matrix ... implementing the latter fixed it
//		pdfName = "MyremcNews_29.1.pdf"; // TODOne check fonts ==> decodes just fine
//		pdfName = "BMZOO_S003_1974_T216_N144.415-418.pdf"; // TODOne check this out ==> loads just fine as OCR with embedded text
//		pdfName = "Mu-oz_et_al-2019-Journal_of_Biogeography.pdf"; // TODOne check images ==> they are vector graphics ...
//		pdfName = "Mu-oz_et_al-2019-Journal_of_Biogeography.pdf"; // TODO make damn sure to retain column regions ==> 
//		pdfName = "candollea.c2018v732a11.pdf"; // TODOne check fonts ==> come up just fine ...
//		pdfName = "zoosystema2019v41a5.pdf"; // TODOne check why figure 3 (page 6) comes up block flipped ==> two main images _are_ flipped ...
//		pdfName = "science.363.6433.1284.pdf"; // TODOne check this one out ==> opens OK, but structure messed up on page 0
//		pdfName = "science.363.6433.1284.pdf"; // TODO check structure of page 0 ==> 
//		pdfName = "peerj-6457.pdf"; // TODOne get page content flip on page 16 right ==> staying clear of flip result area content now
//		pdfName = "nature.s41586-019-1067-9.pdf"; // TODOne check out figures 6 & 7 (pages 16 & 17) ==> decode just fine, only gigantic 600 DPI 20MPx bitmaps ...
//		pdfName = "Osborn1912CraniaABBYY_PDF2.pdf"; // TODOne check why page images are not found ==> line breaks missing around 'endobj', solved by inserting them
//		pdfName = "journal.pbio.2006125.pdf"; // TODOne check why this doesn't decode ==> must not skip uncompressed page content parts (no 'Filter' in stream params)
//		pdfName = "RevistaBrasZool_25_4.pdf"; // TODOne check top of page 0 (words multiply) ==> words _are_ multiplied over one another, emulating extra bold face
//		pdfName = "RevistaBrasZool_25_4.pdf"; // TODOne prevent concatenation of superimposed words ==> added filter
//		pdfName = "annurev-ento-031616-034941.pdf"; // TODOne keep years and other numbers together in pages 11-16 ==> added catch for 0 font space width (minimum cap-off)
//		pdfName = "createceousResearch.99.30-40.pdf"; // TODOne check out color space decoding error ==> have to split 'q' PTag from succeeding data without intermediate space ...
//		pdfName = "rbent.61.02.192-202.pdf"; // TODOne check mis-decoded coordinate directions in pages 3, 5, 8 ==> decode just fine
//		pdfName = "currentBiology.29.1-7.pdf"; // TODOne check how this one decodes ==> added cacth for indirect color space pattern object
//		pdfName = "ActaZool_Sziraki.pdf"; // TODOne check this ==> decodes just fine
//		pdfName = "Fraser1943 - iLestes albofasciatai a new species of Odonata from Buru island.pdf"; // TODOne check scan of page 2, comes up inverted ==> two-part scan with inverted part, interpreting decode inversion on all bitmaps now
//		pdfName = "Molnar1991CranialMorphology.ABBYY.pdf"; // TODOne check what happens to drawings, e.g. in page 5, 6, 8 ==> scans split up for compression purposes, only text texts used
//		pdfName = "Molnar1991CranialMorphology.ABBYY.pdf"; // TODOne add all non-masked images to page images even if overlapping, and paint masks with white translucent ==> 
//		pdfName = "Molnar1991CranialMorphology.pdf"; // TODOne rotate embedded OCR alongside page image on page 9 ==> added embedded OCR handling to scan enhancement
//		pdfName = "Wilson - 2004 - New Odonata from South China.pdf"; // TODOne check out fonts (words come up very low) ==> embedded OCT very irregular
//		pdfName = "s41467-019-11690-z.pdf"; // TODOne check this out ==> decodes OK, font sizes are a nightmare, as is left aligned bibliography, which wraps to early on a good few occasions, some text comes as bitmap (publication date)
//		pdfName = "The American journal of science_ocr.pdf"; // TODOne check why embedded OCR comes up with 'o's and 'w's capitalized ==> chaotic character organization in embedded OCR fonts incurs trouble on mapping conflict resolution
//		pdfName = "Personsetal2019LargeTyrannosaurus.pdf"; // TODO make sure to somehow flip table on page 13 to left-right orientation ==>
//		pdfName = "Fabricius_J_C_1798_Classis_V_Odonata.ABBYY.pdf"; // TODOne make page images decode ==> page size in PDF way off, messing up resolution
//		pdfName = "Cunoniaceae_version_imprimeur_25_novembre.pdf"; // TODOne check out font decoding exception ==> PDF too large to handle ...
//		pdfName = "Cunoniaceae_version_imprimeur_25_novembre.pdf"; // TODOne _somehow_ do check out font decoding exception ==> decoded on server, and added catch for non-renderable characters
//		pdfName = "MyremcNews_24.123-144.pdf"; // TODOne test (striped) image on page 4 ==> looks like stripes vary widely in resolution ...
//		pdfName = "MyremcNews_24.123-144.pdf"; // TODOne overcome resolution differences in (striped) image on pages 4, 8, 9, 10, 12, 13, (15), (18) ==> forcing image parts to common resolution on assembly rendering
//		pdfName = "insectSystematicsAndDiversity.3.6.1-42.pdf"; // TODOne move flipped right column away from left one on page 3 ==> adjusted content flipping logic accordingly
//		pdfName = "bulletinSocentomolFrance.107.1.33-41.pdf"; // TODOne check page image decoding ==> inverted, need to interpret 'BlackIs1' parameter
//		pdfName = "bulletinSocentomolFrance.107.1.33-41.pdf"; // TODOne interpret 'BlackIs1' parameter in page image decoding ==> done, works fine now
//		pdfName = "rsz.111.2.385-301_ocr.pdf"; // TODOne check this one ==> broken page dimensions, indicated resolution below half of actual one, small wonder this doesn't work all too well
//		pdfName = "arthropodaSelecta.17.1.111-115.pdf"; // TODOne check fonts (extremely unfriendly, no Unicode mapping) ==> decode fine, except those mixing Latin and Cyrillic script
//		pdfName = "arthropodaSelecta.17.1.111-115.pdf"; // TODOne check figures on page 2 and 3, come up inverted ==> now catching prefixes on 'Black' colorant name in separation color spaces
//		pdfName = "zoosystema2019v41a20.pdf"; // TODOne check this ==> works just fine
//		pdfName = "ActaEntomolSlov.16.2.105-116.pdf"; // TODOne figure out why numbers shatter into digits ==> characters render one per command, and there is merge protection on numbers (removing the latter fixes it)
//		pdfName = "11633_Edwards_&_Jennings_2010_Pec_86.1_1-2.pdf"; // born-digital, TODOne some characters mis-decoded due to CIDs off by one in TrueType file after some range ==> fixed by now interpreting cmap table as well in 'Downloaded from ...' note at page bottom ==> implemented descendant font CID to glyph ID mapping
//		pdfName = "NeotropIchty-Mello-2016.pdf"; // TODOne check out font decoder NullPointerException ==> already fixed
//		pdfName = "InsectMund-Peck-2007.pdf"; // TODOne check graphics issue on pages 5, 6 ==> main text floating around figure and caption ... need density based blocking to handle this
//		pdfName = "JourPaleo-Candela-2019.pdf"; // TODOne get rid of 'Downloaded from ...' note at page bottom ==> extended repeated content to top and bottom page edges
//		pdfName = "JourNatHist-Pronzato-2019.pdf"; // TODOne check why PDF decoder hangs ==> needed to replace ImageMagic with more recent version due to expired security certificate ...
//		pdfName = "RevBrasEntomol-Fernandes-2006.pdf"; // TODOne check why figures decode incompletely (pages 4, 6 ... 56) ==> figures load OK, just flipped about _diagonal_ and rotated ...
//		pdfName = "RevBrasEntomol-Fernandes-2006.pdf"; // TODOne straighten out figures (pages 4, 6 ... 56) ==> added flips about diagonal axis (as rotation plus main axis flip) and refined computation of rotation angle
// 		pdfName = "zoosystema2019v41a20.fls.pdf"; // TODOne check block structure detection on page 8 ==> comes up perfectly fine ...
//		pdfName = "Zoosystema2019v41a26.pdf"; // TODOne check block structure detection on pages 37-40 ==> all just good ...
// 		pdfName = "zoosystema2020v42a3.pdf"; // TODOne check decoding of page 7 (some bottom-up table column headers come up compressed) ==> fixed bug in word overlap detection (only affected vertical directions)
//		pdfName = "LBB_0049_1_0563-0570.pdf"; // TODOne check black figure on page 6 ==> black figure renders on top of actual figure
//		pdfName = "LBB_0049_1_0563-0570.pdf"; // TODOne get rid of black figure rendering on top of actual figure ==> added filter for mono-colored figures to get rid of image painted over actual one for some reason
//		pdfName = "115-118_Ambroziak.pdf"; // TODOne check this file ==> solved parsing problem due to 'endobj' stuck directly on object end
//		pdfName = "NeotropIchthyol.17.2.e180038.pdf"; // TODOne check page content flip in Page 7, 9, 11 (originally top-down) ==> fixed word distance computation for top-down and upside-down font directions
//		pdfName = "zoosystema42.2.31-32.pdf"; // TODOne check this PDF ==> decodes just fine, error from style anchor matching overrunning number of pages ...
//		pdfName = "RSZ00126-Tanasevitch2019.pdf"; // TODOne check page structuring ==> columns get chopped up in pages 1, 3 due to column cross-split repair glitch
//		pdfName = "RSZ00126-Tanasevitch2019.pdf"; // TODOne column cross-split repair glitch ==> fixed, and fixed template
//		pdfName = "zoosystema2019v41a21.pdf"; // TODOne test column cross-split repair with in-line figure on page 17 ==> works fine now
//		pdfName = "ActaEntMusNatPra.60.1.15-22.pdf"; // TODOne check male symbol, comes up completely blank (page 6) ==> hinky Unicode mapping, and synonymized to unused char
//		pdfName = "ActaEntMusNatPra.60.1.15-22.pdf"; // TODOne prevent synonymizing character to unused one ==> done ... added respective filter
//		pdfName = "Zoosystema42.7.105-114.pdf"; // TODOne check mapping of character rendering as '3' ... too many of them ==> implemented Identity-H encoding
//		pdfName = "Linzer.biol.Beitr.51.2.789-802.pdf"; // TODOne check failed block splits in page 1 ==> comes up just fine ... must be template issue
//		pdfName = "Linzer.biol.Beitr.51.2.789-802.pdf"; // TODOne check block splits in page 1 with template ==> added up-front necessity assessment of cross-split repair
//		pdfName = "PapAvZool.59.e20195916.pdf"; // TODOne check font style detection on page 0 (condensed font comes up bold) ==> glyphs just too narrow to properly match plain font face
//		pdfName = "viruses-11-00210.pdf"; // TODOne check out overlapping words on page 4 ==> added filter for form content outside form bounding box
//		pdfName = "Zoosystema2019v41a26.pdf"; // TODOne test column cross split repair with pages 7, 10, 11, 13, 14, 18, 19, 23, 27, 34, 36 ==> 
//		pdfName = "RevBrasEntomol.50.2.165-231.pdf"; // TODOne check figures on pages 15, 18, 22, 27, 38, 40, 44 ==> adjusted image flipping and rotation computations
//		pdfName = "zoosystema.40.1.1-41.pdf"; // TODOne check decoding of color spaces in page 6 ==> added capping for number of colors in indexed color space
//		pdfName = "ActaChiropterologica.21.1.001.pdf"; // TODOne check figures in this one ==> whole form flipped upside-down, figure flipped again inside form
//		pdfName = "ActaChiropterologica.21.1.001.pdf"; // TODOne factor in form orientation when computing figure orientation ==> now looping through whole transformation matrix stack to form content rendering so whole content comes straight into final position and orientation
//		pdfName = "SpeciesDiversity.25.1-9.pdf"; // TODOne check encryption problems ==>  fixed IcePDF encryption/decryption key generation (was lacking Step 6 of PDF Algorithm 3.2, Pages 100-101 of PDF 1.6 spec)
//		pdfName = "SpeciesDiversity.25.11-24.pdf"; // TODOne check encryption problems ==> fixed IcePDF encryption/decryption key generation (was lacking Step 6 of PDF Algorithm 3.2, Pages 100-101 of PDF 1.6 spec)
//		pdfName = "zt00619.pdf"; // TODOne test if decryption works at all after IcePDF update ==> works fine
//		pdfName = "memoiresMuseumNationalHstoireNaturell.212.pdf"; // TODOne check figure 5 on page 42, figure 10 on page 53 ==> decode just fine, but very high resolution, so most likely memory issue during page image rendering while decoding full PDF
//		pdfName = "afin.052.0211.pdf"; // TODOne check error when decoding fonts in any way (https://github.com/plazi/ggi/issues/26) ==> something is _really_ hinky in this one
		pdfName = "afin.052.0211.pdf"; // TODO make sure fonts decode properly (https://github.com/plazi/ggi/issues/26) ==> 
//		pdfName = "pnas.1919176117.full.pdf"; // TODOne check super slow decoding speed in this one ==> tons of mutually cancelling matrices in graphics on page 3
//		pdfName = "pnas.1919176117.full.pdf"; // TODOne speed up rendering of graphics on page 3 ==> added aggregate transformation matrix
//		pdfName = "pnas.1919176117.full.pdf"; // TODOne handle super-small graphics objects on page 3 (will fall victim to anti-aliasing anyway) ==> added duplicate elimination in graphics sanitization and in-page duplicate handling in page decoration detection
//		pdfName = "Yoshiyuki_Lim_2005_A.new.horseshoe.bat.Rhinolophus.chiewkweeae.Chiroptera.Rhinolophidae.from.pdf"; // TODOne check this one ==> now splitting up HEX4 characters for built-in base fonts
//		pdfName = "BullAmMusNatHist.355.pdf"; // TODOne check this one ==> decodes perfectly fine
//		pdfName = "BullAmMusNatHist.424.pdf"; // TODOne check this one ==> decodes perfectly fine
//		pdfName = "BullAmMusNatHist.355.pdf"; // TODOne somehow fix color space conversion in figure images, e.g. page 9 ==> fixed bug in implementation of PostScript 'roll' command
//		pdfName = "PersJourAcarology.9.1.13-21.pdf"; // TODOne check structure detection on page 6 ==> added filter preventing faint graphics from being mistaken for text boxes
//		pdfName = "AnnalesZoologici.70.1.33-96.pdf"; // TODOne check decoding in this one ==> decodes just fine, except graphics on page 47
		pdfName = "AnnalesZoologici.70.1.33-96.pdf"; // TODO check words in graphics on page 47 (combine in strange ways, might have to adjust merging for overlap) ==> 
//		pdfName = "ZK_article_1941.pdf"; // TODOne check lost italics words on page 8 ==> render as vector graphics ... little we could do
//		pdfName = "InsectSystEvol.51.1-61.pdf"; // TODOne check font in "Downloaded from" notice ==> now re-computing font size on newline command that resets line matrix
//		pdfName = "coleopterist_bulletin.19.1.1-19.3999299.pdf"; // TODOne check why embedded OCR is on wrong page ==> fixed faulty elimination of meta pages from page content data array
//		pdfName = "rsos.200092.pdf"; // TODOne check out decoding performance hog ==> dotted lines in tables render each dot as graphics object, which takes forever in nested loop clustering ...
//		pdfName = "rsos.200092.pdf"; // TODOne check out bottom-up content on pages 6, 14 ==> flips just fine ...
//		pdfName = "RecAustMus.71.1.1-32.pdf"; // TODOne check ORCIDs hidden in link attached to 'id' symbol in page 0 ==> need to import 'Link' annotations from PDF
		pdfName = "RecAustMus.71.1.1-32.pdf"; // TODO add 'Link' annotations from PDF to 'link' regions on page ==> 
//		pdfName = "Acarologia.60.1.64-74.pdf"; // TODOne check decoding ==> decodes fine after deactivating log output that caused stack overflow in Java internal character encoder
//		pdfName = "hbmw_9_Muriniae_corr.pdf"; // TODOne check scaling issues between OCR and page images ==> text is 300 DPI, page image 96 DPI and stretching out in Acrobat ... little we can do (at least in a general way)
//		pdfName = "actaChiropterologica.4.2.121-135.pdf"; // TODOne check decoding ==> requires "Decode Unmapped", but otherwise decodes fine
//		pdfName = "Acarologia.60.1.64-74.pdf"; // TODOne check word dimensions (pretty much all pages, 1-11) ==> just strange fonts ...
//		pdfName = "vanderAaetal2006.pdf"; // TODOne check this one ==> crappy fonts and some messy word bounaries, otherwise OK
//		pdfName = "vanderAaetal2006.pdf"; // TODOne check inverted figures in pages 6 and 9 ==> fixed double inversion of mask images
//		pdfName = "Iheringia.96.2.237-248.pdf"; // TODOne check figures in pages 6, 7 ==> fixed rotation by using matrix directly
//		pdfName = "Iheringia.96.2.237-248.pdf"; // TODOne check positioning of figures in pages 6, 7 ==> now computing dimensions from transformed bounds and position via transformed center point
//		pdfName = "Iheringia.92.1.53-61.pdf"; // TODOne check out fonts in this one ==> something weird going on with TrueType fonts ...
//		pdfName = "Iheringia.92.1.53-61.pdf"; // TODOne fix fonts in this one ==> added ignoring empty CMAP tables, and ones without used chars
//		pdfName = "Iheringia.92.1.53-61.pdf"; // TODOne try and find way of checking indicated font encoding ==> turns out 'Decode Unmapped' and above completely compensate the errors
//		pdfName = "EJT/317-1654-1-SM.pdf"; // TODOne re-check CMAP prcedence in this one (which inspired the fix in the first place) ==> above check does the trick
//		pdfName = "InsectaMundi.0328.1-9.pdf"; // TODOne check decryption ==> fixed IcePDF defaulting behavior of metadata encryption (defaults to true, not false, abd is used in Step 6 of PDF Algorithm 3.2, Pages 100-101 of PDF 1.6 spec)
//		pdfName = "SpeciesDiversity.25.1-9.pdf"; // TODOne check encryption problems ==>  fixed IcePDF encryption/decryption key generation (was lacking Step 6 of PDF Algorithm 3.2, Pages 100-101 of PDF 1.6 spec)
//		pdfName = "SpeciesDiversity.25.11-24.pdf"; // TODOne check encryption problems ==> fixed IcePDF encryption/decryption key generation (was lacking Step 6 of PDF Algorithm 3.2, Pages 100-101 of PDF 1.6 spec)
//		pdfName = "Linzer.biol.Beitr.50.1.0303-0308.pdf"; // TODOne check image decoding in page 4 ==> added catch clause for image output to supplements, preciously little else we could do about errors from depths of JRE image IO ...
//		pdfName = "Linzer.biol.Beitr.50.1.0245-0253.pdf"; // TODOne check decoding this one ==> fixed color space handling when switching image to index color model
//		pdfName = "FAPESP.5.101-112.pdf"; // TODOne check decoding this one ==> fixed Type1 fonts requiring (a) handling of char names that are not PostFix names and (b) implementation of 'sbw' rendering command
//		pdfName = "Rayfield2004CranialMechanics.pdf"; // TODOne check rendering of label text in figures on pages 1, 2, 3, 4 ==> works perfectly fine, legacy problem
//		pdfName = "NeotropEntomol.40.5.619-621.pdf"; // TODOne check word alignment ==> now holding on to word baseline during rendering to provide reference point when shrinking word bounds
//		pdfName = "ActaEntMusNatPra.56.suppl.1-418.pdf"; // TODOne check decoding this one ==> decodes fine after factoring in page rotation
//		pdfName = "ActaEntMusNatPra.45.21-50.pdf"; // TODOne check decoding this one ==> error CCITTFax decoding image in page 5, 8 and other figures coming up blank
//		pdfName = "ActaEntMusNatPra.45.21-50.pdf"; // TODOne ensure figures decode properly ==> observibe byte alignment filter in CCITTFax decoding now
//		pdfName = "ActaEntMusNatPra.45.165-182.pdf"; // TODOne check decoding this one ==> error CCITTFax decoding image in page 5, and other figures coming up blank
//		pdfName = "ActaEntMusNatPra.45.165-182.pdf"; // TODOne ensure figures decode properly ==> observibe byte alignment filter in CCITTFax decoding now
//		pdfName = "Linzer.biol.Beitr.50.1.0225-0228.pdf"; // TODOne check figures ==> decode perfectly fine
//		pdfName = "Linzer.biol.Beitr.50.1.0239-0243.pdf"; // TODOne check figures ==> decode perfectly fine
//		pdfName = "Linzer.biol.Beitr.50.1.0245-0253.pdf"; // TODOne check decoding ==> decodes perfectly fine
//		pdfName = "Linzer.biol.Beitr.50.1.0389-0412.pdf"; // TODOne check decoding stall ==> added catch for random padding at end of Unicode mapping
//		pdfName = "Linzer.biol.Beitr.50.1.0687-0716.pdf"; // TODOne check decoding stall ==> added catch for random padding at end of Unicode mapping 
//		pdfName = "Linzer.biol.Beitr.50.1.0723-0763.pdf"; // TODOne check decoding stall ==> added catch for random padding at end of Unicode mapping
//		pdfName = "taxon.69.3.567-577.pdf"; // TODOne check gaps between words ==> increasing detection threshold fixed mis-firing implicit space compensation
//		pdfName = "Lipkinetal2007.pdf"; // TODOne check extraction of Figures 1, 2, 3 (same pages) ==> decode fine (index color spaces fixed earlier)
//		pdfName = "InsectaMundi.1998.3-4.175.pdf"; // TODOne check page rotation ==> works fine now
//		pdfName = "JNATHIST.34.1625-1637.pdf"; // TODOne check fonts ==> no Unicode mapping, 'fi' ligature fails to OCR decode ...
//		pdfName = "ActaEntMusNatPra.50.2.341-368.pdf"; // TODOne check page structure fuck-ups (pages 2, 7) ==> graphics drawing word underlines mistaken for text boxes
//		pdfName = "ActaEntMusNatPra.50.2.341-368.pdf"; // TODOne refine filters for text boxes (pages 2, 7) ==> added filters based on individual paths and sub paths (text boxes tend to actually include the text in some way)
//		pdfName = "s41559-018-0667-3.pdf"; // TODOne double check actual text boxes still recognized (pages 1, 5, 6) ==> still works well with the newly added filters
//		pdfName = "bezzi_1924_mydidae.pdf"; // TODOne figure out how to get rid of page edges without losing letters ==> no way, mingled with darkening page edge
//		pdfName = "zoosystema.27.4.867-882.pdf"; // TODOne check font decoding error ==> fixed Type1 FLEX vs. subroutine behavior
//		pdfName = "zoosystema.27.4.839-866.pdf"; // TODOne check font decoding error ==> fixed Type1 FLEX vs. subroutine behavior 
//		pdfName = "zoosystema.27.4.825-837.pdf"; // TODOne check font decoding error ==> fixed Type1 FLEX vs. subroutine behavior
//		pdfName = "zoosystema.27.4.825-837.pdf"; // TODOne fix font decoding error (chars render just fine, UC mapping faithful) ==> text IS garbled, mapped to readability via 'differences' array of fonts ... observing that now
//		pdfName = "FossilImprint.74.1-2.37-44.pdf"; // TODOne check opening this one ==> works after adding catch for invalid objects (stale 'endstream' in this case)
//		pdfName = "speciesdiversity.23.1.13-37.pdf"; // TODOne check test flow ==> tough one, need to leave this to QC ...
//		pdfName = "LinzerbiolBeitr.42.1.0043-0080.pdf"; // TODOne check figure decoding on page 34 ==> decoding predictor 2 handled internally by FlateDecode, preventing redundant wrapping now
//		pdfName = "zoosystema.27.4.867-882.pdf"; // TODOne check inverted figure in page 3 ==> need to set '-negate' parameter for ImageMagick if 'decode' array indicates inversion
//		pdfName = "jAsia-PacificBiodiversity.13.325-330.pdf"; // TODOne check decoding ==> decodes perfectly fine
//		pdfName = "cnh-001_2016_0__38_d.pdf"; // TODOne check out decoding ==> WTF ... born-digital stored as page images with embedded OCR underneath (loads as such, though)
//		pdfName = "s41586-020-03082-x.pdf"; // TODOne check graphics taking in main text ==> page layout graphics just too close text, and too few pages to detect as text decoration ...
//		pdfName = "zoologicalJLinnSoc.190.1-33.pdf"; // TODOne check decoding this one ==> added fuzzy position handling for download portal markings
//		pdfName = "zoologicalJournalLinnSoc.191.548-574.pdf"; // TODOne check decoding this one ==> added fuzzy position handling for download portal markings
//		pdfName = "zoologicalJLinnSoc.190.1-33.pdf"; // TODOne check contet flip on page 7 ==> now requiring flip blocks to be wide enough to be standalone (1 inch)
//		pdfName = "B237-0004.pdf"; // TODOne check decoding this one ==> decodes perfectly fine ... as born digital, not as scan !!!
//		pdfName = "AmMusNovit.2021.3964.1-52.pdf"; // check figures in pages 30 and 37 ==> tiles render as stack of full-page figures with alphs a SMask images !!!
//		pdfName = "AmMusNovit.2021.3964.1-52.pdf"; // TODOne observe figure SMask pages 30 and 37 ==> added, also observing graphics state SMasks (PDF combines both !!!)
//		pdfName = "kunz1982.pdf"; // TODOne check this one (scanned with _really_ good embedded OCR) ==> some error in embedded OCR adjustment
//		pdfName = "kunz1982.pdf"; // TODOne prevent error in embedded OCR adjustment (e.g. page 3) ==> some error in embedded OCR adjustment
//		pdfName = "Adansonia.43.6.49-60.pdf"; // TODOne check fonts ==> fixed fallback mix-up between encoding mapping and Unicode mapping
//		pdfName = "Zoosystema.43.8.145-154.pdf"; // TODOne check fonts ==> same crap as with Adansonia ...
//		pdfName = "batEcology_3-89.pdf"; // TODOne check opening this with embedded OCR ==> opens just fine with adjusting to words
		pdfName = "batEcology_3-89.pdf"; // TODO fix embedded OCR handling in pages 7 and 8 (bottom-up tables) ==> 
//		pdfName = "MONTE-1943-Tingideos_americanos_OCR.pdf"; // TODOne check page image enhancement ==> text only exists as born-digital, scan in four parts ...
//		pdfName = "MONTE-1943-Tingideos_americanos_Image_OCR.resaved.pdf"; // TODOne check decoding ==> page background in images, text only born-digital ... tough one
		pdfName = "MONTE-1942-Apontamentos_sobre_Tingitideos.pdf";
		
		//	TODOne EJT test files for suggestions
//		pdfName = "EJT/EJT-2_Krapp.pdf"; // TODOne check how fonts decode ==> UC mapping comprehensive and faithful
//		pdfName = "EJT/EJT-2_Krapp.pdf"; // TODOne make sure to UC map multi-character mapped ligatures not to first character, but to UC ligature to be resolved later (test: 'fi' on page 2)
//		pdfName = "EJT/EJT-10_Samyn.pdf"; // TODOne check how fonts decode ==> just fine, fully and faithfully UC mapped
//		pdfName = "EJT/EJT-172_Ustjuzhanin.pdf"; // TODOne check how fonts decode ==> just fine, fully and faithfully UC mapped
//		pdfName = "EJT/EJT-256_Voss_.pdf"; // TODOne check how fonts decode ==> just fine, fully and faithfully UC mapped
//		pdfName = "EJT/EJT-54_Chaowasku.pdf"; // TODOne check figure image on page 5 ==> 1800 DPI is just too much, even in black & white
//		pdfName = "EJT/EJT-54_Chaowasku.pdf"; // TODOne make sure to flip bottom-up block in page 20 ==> all too far out left
//		pdfName = "EJT/EJT-54_Chaowasku.pdf"; // TODOne make sure to flip bottom-up block in page 20, even though positioned far on left page edge ==> now flipping even non-centered page content if all clear to right
//		pdfName = "EJT/EJT-283_Philippe.pdf"; // TODOne adjust RefParse to handle long author lists (only decoding here)
//		pdfName = "EJT/EJT-321_Maxwell.pdf"; // TODOne check for inverted figures in pages 6 and 9 ==> page 6 is perfectly fine
//		pdfName = "EJT/EJT-321_Maxwell.pdf"; // TODOne check why figures in page 9 shifted out top right ==> turned 180°, requires translation, as rotation correction rotates around image center
//		pdfName = "EJT/EJT-322_Lin.pdf"; // TODOne check for inverted figure in page 14 ==> comes up perfectly fine
//		pdfName = "EJT/z2017n1a2.pdf"; // TODOne check rendering of serif fonts (somewhat garbled) ==> fixed encoding of two-byte operators in dictionary
//		pdfName = "EJT/z2017n1a2.pdf"; // TODOne check word width (right edges of serif font paragraphs serrated, should be flush) ==> multiple spaces synonymized on equal (empty) rendering sequence, confusing char spacing with word spacing, fixed now
//		pdfName = "EJT/z2017n1a3_0.pdf"; // TODOne check word width (right edges of serif font paragraphs serrated, should be flush) ==> multiple spaces synonymized on equal (empty) rendering sequence, confusing char spacing with word spacing, fixed now
//		pdfName = "EJT/g2017n1.pdf"; // TODOne check this (not opening) ==> got zero-width word image in rendering color assessment (page 65 or 66)
//		pdfName = "EJT/g2017n1.pdf"; // TODOne zero-width word image in rendering color assessment ==> could not reproduce
//		pdfName = "EJT/z2017n1a2.pdf"; // TODOne check small caps in page 1 ==> fixed Unicode mapping collision cleanup
//		pdfName = "EJT/ejt-393_carvalho_kury.pdf"; // TODOne check author name extraction ==> font size 11 instead of 12 ... just off the template
//		pdfName = "EJT/ejt-399_zonstein_kunt.pdf"; // TODOne check what this one fails to process with FM=R ==> processes just fine ...
//		pdfName = "EJT/ejt-401_salnitska_solodovnikov.pdf"; // TODOne check male symbol in Page 12 ==> comes up OK with "Render Glyphs Only", but ends up misfigured by "Decode Unmapped" ...
//		pdfName = "EJT/ejt-401_salnitska_solodovnikov.pdf"; // TODOne check why "Decode Unmapped" destroys male symbol in Page 12 (EJT-testbed/171) ==> mere 37% on verifocation incurred decoding (made verification separate option)
//		pdfName = "EJT/ejt-428_sendra_weber.pdf"; // TODOne fix no-line block error on page 8 ==> now catching empty block edge case in layout analysis
//		pdfName = "EJT/ejt-433_kantor_fedosov_snyder_bouchet.pdf"; // TODOne check why not converting ==> converts just fine 
//		pdfName = "EJT/ejt-441_vilarino_cavalcante_dumas.pdf"; // TODOne check why author names stick together in bibliography (pages 14 and 15) ==> space encoded as 03 rather than 20 ...
//		pdfName = "EJT/ejt-441_vilarino_cavalcante_dumas.pdf"; // TODOne make sure to cut non-20 spaces off words and exclude them from bounary computation ==> added 0x20xx spaces
//		pdfName = "EJT/ejt-404_brehm.pdf.imf.pdf"; // TODOne check word conflations on page 50 (paragraph 'Guenée A. 1858 ...') ==> Added statistics based compensation for extremely dense lines
//		pdfName = "EJT/ejt-186_huber.pdf"; // TODOne check empty graphics supplements ==> some long-fixed bug had incurred empty graphics JSONs in the past
//		pdfName = "EJT/ejt-186_huber.pdf"; // TODOne check why figures in pages 4, 5, and 9 come up inverted ==> added ICCBased to '-negate' alternatives for 'SeparationBlack' in ImageMagick fixed pages 5 and 9
//		pdfName = "EJT/ejt-186_huber.pdf"; // TODOne check why figures in page 4 comes up inverted ==> implemented DeviceN/NChannel color spaces
//		pdfName = "EJT/ejt-40_Bosselaers.pdf"; // TODOne check why page flip partially fails on appendix pages 44-48 ==> flips just fine now, must have been legacy issue (all-or-nothing for blocks, including graphics, came only later)
//		pdfName = "EJT/ejt-40_Bosselaers.pdf"; // TODOne check why image in page 31 fails to decode ==> 1260 DPI mask image, but decodes just fine (must have been legacy problem, or lack of resources on old machine)
//		pdfName = "EJT/ejt-492_meisch_smith_martens.pdf"; // TODOne check caps mixups in fonts (page 1) ==> come up just fine ...
//		pdfName = "EJT/ejt-496_read_enghoff.pdf"; // TODOne check male symbol ==> comes up OK with 'decode unmapped', messed up by 'verify mapped'
//		pdfName = "EJT/ejt-496_read_enghoff.pdf"; // TODOne check down arrow symbol (page 14) ==> measures out of bounds in comparison fonts for some reason, now keeping such characters at bay
//		pdfName = "EJT/ejt-500_dayrat_goulding_khalil_apte_bourke_comendador_tan.pdf"; // TODOne prevent sucking text into image on pages 45, 52 ==> now trimming white figure image margins for page structuring image
//		pdfName = "EJT/ejt-500_dayrat_goulding_khalil_apte_bourke_comendador_tan.pdf"; // TODOne prevent table in graphics on pages 10-14 ==> requiring full-height peak now if any peaks present
//		pdfName = "EJT/ejt-526_nurinsiyah_neiber_hausdorf.pdf"; // TODOne make sure to flip page 6 ==> using distance weights in orientation now to reduce influence of voids
//		pdfName = "EJT/ejt-2012-34.pdf"; // TODOne check figure on page 2 ==> black parts are transparent, requires rendering on black backdrop to reproduce accurately
//		pdfName = "EJT/ejt-2012-34.pdf"; // TODOne try setting transparent parts to black if JPX negated ==> blending pixels with alpha <255 against black backdrop appears to do the trick
//		pdfName = "EJT/ejt-499_bezdek.pdf"; // TODOne check figures on page 0 ==> added handling for strangely formatted form bounding boxes
//		pdfName = "EJT/ejt-616_mariaux_georgiev.pdf"; // TODOne check duplicate captions in pages 3, 8, 11, 14, 17, 22, 25 ==> added clipping path check for non-form words (forms might be inlined into to level content)
//		pdfName = "EJT/ejt-684_haran_beaudin-olivier_benoit.pdf"; // TODOne check decoding figure 6 (page 25) ==> implemented rendering of text displayed via clipping things drawn over its location
		
//		pdfName = "Zootaxa/zt03691p559.pdf"; // TODOne check figure in page 4 ==> comes out just fine, only that it's TWO figures, with one obfuscating the other
//		pageIDs.add(new Integer(16));
//		pageIDs.add(new Integer(17));
//		pageIDs.add(new Integer(18));
//		pageIDs.add(new Integer(3));
//		pageIDs.add(new Integer(10));
		
		//	use for ad-hoc decoding
//		pdfName = "Molnar1991hybrid.pdf";
//		pdfName = "zt03790p356.pdf";
		
		//	for embedded OCR adjustment tests TODO finish this crap !!!
//		pdfName = "Carr1999TyrannosauridaeABBYY.pdf"; // almost too good
//		pdfName = "z1997n4a12.pdf"; // OCR off upwards in a good few pages (mostly towards bottom), boundaries of italic words short on right
//		pdfName = "BMZOO_S003_1974_T216_N144.415-418.pdf"; // left and right word boundaries off with italics, lines slightly off
		//pdfName = "BMZOO_S003_1974_T216_N144.415-418.pdf"; // TODO get rid of frame around reference in page 3 ==> 
//		pdfName = "coleopterist_bulletin.19.1.1-19.3999299.pdf"; // overall slightly off to the right BUT only in Acrobat, only embedded born-digital text is 'Downloaded from ...' at page bottom
//		pdfName = "hbmw_9_Vespertilionidae_0004.pdf"; // OCR word boundaries slightly off to top (lower boundary sits on baseline) BUT only in Acrobat, PDF decoder doesn't find embedded OCR
//		pdfName = "hbmw_9_Vespertilionidae_0005.pdf"; // OCR word boundaries slightly off to top (lower boundary sits on baseline)
//		pdfName = "Myotinae.pdf";
//		pdfName = "transactionsprocNewZealandInst.33.1-95.pdf"; // TODO check out decoding and OCR ==> adjusting to words looks pretty OK
		
//		//	TODO for font reference generation checks
//		//	TODOne find size fuck-ups ==> looks like culprits are bitmap based glyphs in Type3 fonts
//		pdfName = "Zootaxa/zt03353p068.pdf";
//		pdfName = "Zootaxa/zt03456p035.pdf";
//		pdfName = "JNATHIST.53.23–24.1401–1420.pdf";
		
//		//	TODO make sure to deactivate this unless explicitly required !!!
//		DocumentStyle.addProvider(new DocumentStyle.Provider() {
//			public Properties getStyleFor(Attributed doc) {
//				Properties ds = new Properties();
////				//	values for RSZ
////				ds.setProperty("layout.columnAreas", "[299,561,22,788] [41,294,22,792]");
////				ds.setProperty("layout.columnCount", "2");
////				ds.setProperty("layout.contentArea", "[36,566,22,794]");
////				ds.setProperty("layout.minBlockMargin", "8");
////				ds.setProperty("layout.minColumnMargin", "27");
//				//	values for Linzer Beitraege
////				ds.setProperty("layout.caption.aboveTable", "true");
////				ds.setProperty("layout.caption.belowFigure", "true");
////				ds.setProperty("layout.caption.besideFigure", "true");
////				ds.setProperty("layout.caption.figureStartPatterns", "(Figs|Fig|Abb|Map)");
////				ds.setProperty("layout.caption.fontSize", "9");
////				ds.setProperty("layout.caption.maxFontSize", "9");
////				ds.setProperty("layout.caption.minFontSize", "9");
////				ds.setProperty("layout.columnAreas", "[50,401,75,600]");
////				ds.setProperty("layout.contentArea", "[50,411,42,624]");
//				//ds.setProperty("layout.fontSize", "9");
//				//ds.setProperty("layout.maxFontSize", "9");
//				ds.setProperty("layout.minBlockMargin", "5");
//				//ds.setProperty("layout.minFontSize", "9");
////				ds.setProperty("layout.page.even.headerAreas", "[65,379,35,72]");
////				ds.setProperty("layout.page.first.headerAreas", "[45,400,72,107]");
////				ds.setProperty("layout.page.number.area", "[199,255,40,76]");
////				ds.setProperty("layout.page.number.fontSize", "9");
////				ds.setProperty("layout.page.number.maxFontSize", "9");
////				ds.setProperty("layout.page.number.minFontSize", "9");
////				ds.setProperty("layout.page.number.pattern", "\\d{1,}");
////				ds.setProperty("layout.page.odd.headerAreas", "[55,381,39,76]");
//				return ds;
//			}
//		});
//		pdfName = "zt03790p356.pdf"; // TODOne test mapping of Win-1252 chars (NOT equal to unicode points ...)
//		pdfName = "Zootaxa/zootaxa.4948.3.1.pdf"; // TODOne check word widths (e.g. in page 10) ==> char codes offset towards 0 in two fonts ...
//		pdfName = "Zootaxa/zootaxa.4948.3.1.pdf"; // TODOne figure out word widths (e.g. in page 10) ==> width of em-dash encoded in missing-width, and char UC mapped
//		pdfName = "Zootaxa/zootaxa.4948.3.1.pdf"; // TODOne fix word widths (e.g. in page 10) ==> checking base width array before in missing-width now
		
		long start = System.currentTimeMillis();
		int scaleFactor = 1;
		if (pageIDs.isEmpty())
			pageIDs = null;
		aimAtPage = 0; // TODO_ne always set this to -1 for JAR export ==> no need to, as long as this main() is not executed
		if (pageIDs != null)
			aimAtPage = -1;
		//	TODO try pages 12, 13, 16, 17, and 21 of Prasse 1979
		System.out.println("Aiming at page " + aimAtPage);
		final File cacheBasePath = new File(pdfDataPath, "Cache");
		final PdfExtractor pdfExtractor = new PdfExtractor(pdfDataPath, cacheBasePath, pis, false) {
			protected ImDocument createDocument(String docId) {
				final File supplementFolder = new File(cacheBasePath, ("doc" + docId));
				return new ImDocument(docId) {
					private ImSupplementCache supplementCache;
					{
						if (PdfExtractorTest.aimAtPage == -1)
							this.supplementCache = new ImSupplementCache(this, supplementFolder, (1024 * 1024 * 50));
					}
					public ImSupplement addSupplement(ImSupplement ims) {
						if (PdfExtractorTest.aimAtPage == -1)
							ims = this.supplementCache.cacheSupplement(ims);
						return super.addSupplement(ims);
					}
					public void removeSupplement(ImSupplement ims) {
						if (PdfExtractorTest.aimAtPage == -1)
							this.supplementCache.deleteSupplement(ims);
						super.removeSupplement(ims);
					}
				};
			}
		};
//		File basePath = new File(".");
//		final PdfExtractor pdfExtractor = new PdfExtractor(basePath, basePath, pis, false);
		
//		if (false) {
//			File pdfFile = new File(pdfDataPath, pdfName);
//			FileInputStream pdfIn = new FileInputStream(pdfFile);
//			BufferedInputStream bis = new BufferedInputStream(pdfIn);
//			ByteArrayOutputStream baos = new ByteArrayOutputStream();
//			byte[] buffer = new byte[1024];
//			int read;
//			while ((read = bis.read(buffer, 0, buffer.length)) != -1)
//				baos.write(buffer, 0, read);
//			bis.close();
//			byte[] bytes = baos.toByteArray();
//			
//			ImDocument doc = new ImDocument(pdfName);
//			doc.setDocumentProperty("docId", pdfName);
//			Document pdfDoc = new Document();
//			pdfDoc.setInputStream(new ByteArrayInputStream(bytes), "");
//			doc = pdfExtractor.loadGenericPdf(doc, pdfDoc, bytes, 1, null);
//			return;
//		}
//		
		File docFile = new File(pdfDataPath, (pdfName + ".imf"));
		ImDocument doc = null;
		if (docFile.exists() && (aimAtPage == -1) && (pageIDs == null)) {
			BufferedInputStream docIn = new BufferedInputStream(new FileInputStream(docFile));
			doc = ImDocumentIO.loadDocument(docIn);
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
			System.out.println("PDF document read, got " + bytes.length + " bytes");
			
//			doc = new ImDocument(pdfName);
//			doc.setDocumentProperty("docId", pdfName);
//			Document pdfDoc = new Document();
//			pdfDoc.setInputStream(new ByteArrayInputStream(bytes), "");
			ProgressMonitorDialog pdm = new ProgressMonitorDialog(DialogFactory.getTopWindow(), "Loading PDF");
			pdm.setSize(400, 100);
			pdm.setLocationRelativeTo(null);
			pdm.popUp(false);
			
			int flags = 0;
			flags |= PdfExtractor.USE_EMBEDDED_OCR;
			flags |= PdfExtractor.META_PAGES;
			flags |= PdfExtractor.SINGLE_PAGE_SCANS;
//			flags |= PdfExtractor.DOUBLE_PAGE_SCANS;
			flags |= PdfExtractor.ENHANCE_SCANS;
//			flags |= PdfExtractor.FIX_EMBEDDED_OCR;
//			flags |= PdfExtractor.ADJUST_EMBEDDED_OCR_NONE;
			flags |= PdfExtractor.ADJUST_EMBEDDED_OCR_WORDS;
//			flags |= PdfExtractor.ADJUST_EMBEDDED_OCR_LINES;
//			flags |= PdfExtractor.ADJUST_EMBEDDED_OCR_BLOCKS;
//			flags |= PdfExtractor.ENHANCE_SCANS_INVERT_WHITE_ON_BLACK;
//			flags |= PdfExtractor.ENHANCE_SCANS_SMOOTH_LETTERS;
			flags |= PdfExtractor.ENHANCE_SCANS_ELIMINATE_BACKGROUND;
			flags |= PdfExtractor.ENHANCE_SCANS_WHITE_BALANCE;
			flags |= PdfExtractor.ENHANCE_SCANS_CLEAN_PAGE_EDGES;
//			flags |= PdfExtractor.ENHANCE_SCANS_REMOVE_SPECKLES;
//			flags |= PdfExtractor.ENHANCE_SCANS_CORRECT_ROTATION;
			flags |= PdfExtractor.ENHANCE_SCANS_CORRECT_SKEW;
			System.out.println("Flags are " + flags + " (" + Integer.toString(flags, 2) + ")");
			doc = pdfExtractor.loadImagePdf(doc, null, bytes, flags, scaleFactor, null);
//			doc = pdfExtractor.loadImagePdf(doc, null, bytes, true, true, scaleFactor, null);
//			doc = pdfExtractor.loadImagePdfBlocks(doc, null, bytes, scaleFactor, null);
//			doc = pdfExtractor.loadImagePdfPages(doc, null, bytes, flags, scaleFactor, null, pdm);
//			doc = pdfExtractor.loadTextPdf(doc, null, bytes, PdfFontDecoder.UNICODE, pageIDs, pdm);
//			doc = pdfExtractor.loadTextPdf(doc, null, bytes, PdfFontDecoder.LATIN_FULL, pageIDs, pdm);
//			doc = pdfExtractor.loadTextPdf(doc, null, bytes, PdfFontDecoder.LATIN, pageIDs, pdm);
//			doc = pdfExtractor.loadTextPdf(doc, null, bytes, PdfFontDecoder.FontDecoderCharset.union(PdfFontDecoder.VERIFY_MAPPED, PdfFontDecoder.LATIN), pageIDs, pdm);
//			doc = pdfExtractor.loadTextPdf(doc, null, bytes, PdfFontDecoder.FontDecoderCharset.union(PdfFontDecoder.DECODE_UNMAPPED, PdfFontDecoder.LATIN_FULL), pageIDs, pdm);
//			doc = pdfExtractor.loadTextPdf(doc, null, bytes, PdfFontDecoder.RENDER_ONLY, pageIDs, pdm);
//			doc = pdfExtractor.loadTextPdf(doc, null, bytes, PdfFontDecoder.NO_DECODING, pageIDs, pdm);
//			doc = pdfExtractor.loadGenericPdf(doc, null, bytes, scaleFactor, pageIDs, null);
			
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
			
			ImPage[] pages = doc.getPages();
			if ((aimAtPage == -1) && (pageIDs == null) && (pages.length != 0)) {
				BufferedOutputStream docOut = new BufferedOutputStream(new FileOutputStream(docFile));
				ImDocumentIO.storeDocument(doc, docOut);
				docOut.flush();
				docOut.close();
			}
			else {
				System.out.println("PDF loaded:");
//				ImSupplement[] suppls = doc.getSupplements();
//				for (int s = 0; s < suppls.length; s++) {
//					if (!(suppls[s] instanceof ImSupplement.Graphics))
//						continue;
//					System.out.println(suppls[s].getId());
//					InputStream sin = ((ImSupplement.Graphics) suppls[s]).getInputStream();
//					byte[] sBuffer = new byte[1024];
//					for (int r; (r = sin.read(sBuffer)) != -1;)
//						System.out.write(sBuffer, 0, r);
//					System.out.println();
//				}
//				AnnotationUtils.writeXML(doc, new OutputStreamWriter(System.out));
//				System.out.println();
			}
		}
//		
//		System.out.println("Got document fonts:");
//		ImFont[] fonts = doc.getFonts();
//		for (int f = 0; f < fonts.length; f++) {
//			System.out.println(" - font " + fonts[f].name);
//			String fontStyle = "";
//			if (fonts[f].isBold())
//				fontStyle += "B";
//			if (fonts[f].isItalics())
//				fontStyle += "I";
//			if (fonts[f].isSerif())
//				fontStyle += "S";
//			System.out.println("   - style " + fontStyle);
//			int[] charIDs = fonts[f].getCharacterIDs();
//			System.out.println("   - " + charIDs.length + " chars");
//			for (int c = 0; c < charIDs.length; c++) {
//				StringTupel charData = new StringTupel();
//				charData.setValue(ImFont.NAME_ATTRIBUTE, fonts[f].name);
//				charData.setValue(ImFont.STYLE_ATTRIBUTE, fontStyle);
//				charData.setValue(ImFont.CHARACTER_ID_ATTRIBUTE, Integer.toString(charIDs[c], 16).toUpperCase());
//				String charStr = fonts[f].getString(charIDs[c]);
//				if (charStr != null)
//					charData.setValue(ImFont.CHARACTER_STRING_ATTRIBUTE, charStr);
//				String charImageHex = fonts[f].getImageHex(charIDs[c]);
//				if (charImageHex != null)
//					charData.setValue(ImFont.CHARACTER_IMAGE_ATTRIBUTE, charImageHex);
//				System.out.println("     - " + Integer.toString(charIDs[c], 16).toUpperCase() + " (" + charIDs[c] + ") = '" + charStr + "'");
//			}
//		}
		
		ImPage[] pages = doc.getPages();
		final ImageDisplayDialog dialog = new ImageDisplayDialog(pdfName);
		for (int p = 0; p < pages.length; p++) {
			if ((aimAtPage != -1) && (pages[p].pageId != aimAtPage))
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
			System.out.println("Scaledown for page " + p + " is " + scaleDown);
			int imageMargin = 8;
			final BufferedImage bi = new BufferedImage(((pi.image.getWidth()/scaleDown)+(imageMargin*2)), ((pi.image.getHeight()/scaleDown)+(imageMargin*2)), BufferedImage.TYPE_3BYTE_BGR);
			bi.getGraphics().setColor(Color.WHITE);
			bi.getGraphics().fillRect(0, 0, bi.getWidth(), bi.getHeight());
			bi.getGraphics().drawImage(pi.image, imageMargin, imageMargin, (pi.image.getWidth()/scaleDown), (pi.image.getHeight()/scaleDown), null);
			
			ImWord[] words = pages[p].getWords();
			ImUtils.sortLeftRightTopDown(words);
			for (int w = 0; w < words.length; w++) {
//				Color col = (!"".equals(words[w].getAttribute(STRING_ATTRIBUTE, "")) ? Color.YELLOW : Color.RED);
//				Color col = ("".equals(words[w].getAttribute(BOLD_ATTRIBUTE, "")) ? Color.YELLOW : Color.RED);
				Color col = ("".equals(words[w].getAttribute(ITALICS_ATTRIBUTE, "")) ? ("".equals(words[w].getAttribute(BOLD_ATTRIBUTE, "")) ? Color.YELLOW : Color.MAGENTA) : Color.RED);
				paintBox(bi, scaleDown, words[w], col.getRGB(), imageMargin, 0);
				paintBaseline(bi, scaleDown, words[w], Color.PINK.getRGB(), imageMargin);
				if (aimAtPage != -1)
					System.out.println("Word '" + words[w] + "' in " + words[w].getAttribute(ImWord.FONT_NAME_ATTRIBUTE) + ": " + words[w].getAttribute(ImFont.CHARACTER_CODE_STRING_ATTRIBUTE));
			}
			
			ImRegion[] lines = pages[p].getRegions(LINE_ANNOTATION_TYPE);
			for (int l = 0; l < lines.length; l++)
				paintBox(bi, scaleDown, lines[l], Color.ORANGE.getRGB(), imageMargin, 1);
//			
//			Annotation[] cells = pages[p].getAnnotations("td");
//			for (int c = 0; c < cells.length; c++)
//				paintBox(bi, scaleDown, cells[c], Color.RED.getRGB(), imageMargin, 1);
//			
			ImRegion[] graphics = pages[p].getRegions(ImRegion.GRAPHICS_TYPE);
			for (int g = 0; g < graphics.length; g++)
				paintBox(bi, scaleDown, graphics[g], Color.MAGENTA.getRGB(), imageMargin, 3);
			
			ImRegion[] images = pages[p].getRegions(ImRegion.IMAGE_TYPE);
			for (int i = 0; i < images.length; i++)
				paintBox(bi, scaleDown, images[i], Color.PINK.getRGB(), imageMargin, 3);
			
			ImRegion[] blocks = pages[p].getRegions(BLOCK_ANNOTATION_TYPE);
			for (int b = 0; b < blocks.length; b++)
				paintBox(bi, scaleDown, blocks[b], Color.BLUE.getRGB(), imageMargin, 3);
			
			ImRegion[] paragraphs = pages[p].getRegions(MutableAnnotation.PARAGRAPH_TYPE);
			for (int g = 0; g < paragraphs.length; g++)
				paintBox(bi, scaleDown, paragraphs[g], Color.GREEN.getRGB(), imageMargin, 2);
			
			ImRegion[] columns = pages[p].getRegions(COLUMN_ANNOTATION_TYPE);
			for (int c = 0; c < columns.length; c++)
				paintBox(bi, scaleDown, columns[c], Color.DARK_GRAY.getRGB(), imageMargin, 5);
//			
//			ImRegion[] regions = pages[p].getRegions(REGION_ANNOTATION_TYPE);
//			for (int r = 0; r < regions.length; r++)
//				paintBox(bi, scaleDown, regions[r], Color.GRAY.getRGB(), imageMargin, 5);
			
			dialog.addImage(bi, ("Page " + pages[p].pageId));
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
		try {
			for (int c = (word.bounds.left/scaleDown); c < (word.bounds.right/scaleDown); c++)
				bi.setRGB((c+im), ((baseline/scaleDown)+im), rgb);
		}
		catch (RuntimeException re) {
			System.err.println("Could not paint baseline for word '" + word.getString() + "' at " + ((baseline/scaleDown)+im) + " from " + (word.bounds.left/scaleDown) + " to " + (word.bounds.right/scaleDown));
			re.printStackTrace();
		}
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
