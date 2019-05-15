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
package de.uka.ipd.idaho.im.pdf;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
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
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.net.URL;
import java.util.HashMap;

import javax.imageio.ImageIO;

import de.uka.ipd.idaho.easyIO.streams.CharSequenceReader;
import de.uka.ipd.idaho.gamta.AnnotationUtils;
import de.uka.ipd.idaho.gamta.util.AnalyzerDataProvider;
import de.uka.ipd.idaho.gamta.util.AnalyzerDataProviderFileBased;
import de.uka.ipd.idaho.gamta.util.ProgressMonitor;
import de.uka.ipd.idaho.gamta.util.imaging.PageImage;
import de.uka.ipd.idaho.gamta.util.imaging.PageImageInputStream;
import de.uka.ipd.idaho.gamta.util.imaging.PageImageStore;
import de.uka.ipd.idaho.gamta.util.imaging.PageImageStore.AbstractPageImageStore;
import de.uka.ipd.idaho.im.ImDocument;
import de.uka.ipd.idaho.im.ImPage;
import de.uka.ipd.idaho.im.ImSupplement;
import de.uka.ipd.idaho.im.gamta.ImDocumentRoot;
import de.uka.ipd.idaho.im.pdf.PdfFontDecoder.CustomFontDecoderCharset;
import de.uka.ipd.idaho.im.pdf.PdfFontDecoder.FontDecoderCharset;
import de.uka.ipd.idaho.im.util.ImDocumentData.ImDocumentEntry;
import de.uka.ipd.idaho.im.util.ImDocumentIO;
import de.uka.ipd.idaho.im.util.ImSupplementCache;

/**
 * Command line wrapper for PDF extractor class.
 * 
 * @author sautter
 */
public class PdfExtractorTool {
	
	private static final int maxInMemorySupplementBytes = (50 * 1024 * 1024); // 50 MB
	
	public static void main(String[] args) throws Exception {
		
		//	catch absence of all arguments
		if (args.length == 0) {
			printError("Invalid arguments, use '-?' or '-h' to list available arguments");
			return;
		}
		
		//	read parameters
		String sourcePath = null;
		String sourceType = "G";
		String fontMode = "D";
		String fontCharSet = "U";
		String fontCharSetPath = null;
		String logPath = "S";
		String outPath = "S";
		String cacheBasePath = "./Temp";
		String cpuMode = "M";
		String mode = "A";
		for (int a = 0; a < args.length;) {
			/* source parameter -s
			 * - missing: System.in
			 * - set: file path */
			if ("-s".equalsIgnoreCase(args[a]) && ((a+1) < args.length)) {
				sourcePath = args[a+1];
				a += 2;
			}
			/* path parameter -c
			 * - missing or set to .: execution folder
			 * - set to folder path: cache folder */
			else if ("-c".equalsIgnoreCase(args[a]) && ((a+1) < args.length)) {
				cacheBasePath = args[a+1];
				a += 2;
			}
			/* path parameter -p
			 * - missing or set to M: multiple processors
			 * - set to S: single processor */
			else if ("-p".equalsIgnoreCase(args[a]) && ((a+1) < args.length)) {
				cpuMode = args[a+1];
				a += 2;
			}
			/* source type parameter -t
			 * - missing or set to G: generic PDF
			 * - set to D: born-digital
			 * - set to S: scanned
			 * - set to O: scanned, with embedded OCR
			 * - set to M: scanned with meta pages */
			else if ("-t".equalsIgnoreCase(args[a]) && ((a+1) < args.length)) {
				sourceType = args[a+1];
				a += 2;
			}
			/* font mode parameter -f
			 * - missing or set to D: fully decode embedded fonts
			 * - set to V: fully decode only un-mapped fonts or characters, i.e., ones that do not have a Unicode mapping, but also verify ones that do
			 * - set to U: fully decode only un-mapped fonts or characters, i.e., ones that do not have a Unicode mapping
			 * - set to R: render embedded fonts, but do not decode glyphs
			 * - set to Q: quick mode, use Unicode mapping only */
			else if ("-f".equalsIgnoreCase(args[a]) && ((a+1) < args.length)) {
				fontMode = args[a+1];
				a += 2;
			}
			/* font char set parameter -cs
			 * - missing or set to U: use all of Unicode
			 * - set to S: use Latin characters and scientific symbols only
			 * - set to M: use Latin characters and mathematical symbols only
			 * - set to F: use Full Latin characters only
			 * - set to L: use Extended Latin characters only
			 * - set to B: use Basic Latin characters only
			 * - set to C: use custom character set loaded from file */
			else if ("-cs".equalsIgnoreCase(args[a]) && ((a+1) < args.length)) {
				fontCharSet = args[a+1];
				a += 2;
			}
			/* font char set file parameter -cf (relevant and required only for '-f D -cs F') */
			else if ("-cp".equalsIgnoreCase(args[a]) && ((a+1) < args.length)) {
				fontCharSetPath = args[a+1];
				a += 2;
			}
			/* log parameter -l
			 * - missing or S: silent (silence System.out)
			 * - set to O: System.out
			 * - set to M: ProgressMonitor via System.out (use special ProgressMontor implementation)
			 *   - BP: base progress
			 *   - MP: max progress
			 *   - P: progress
			 *   - S: step
			 *   - I: info
			 * - set to file path: log file (also use for errors) */
			else if ("-l".equalsIgnoreCase(args[a]) && ((a+1) < args.length)) {
				logPath = args[a+1];
				a += 2;
			}
			/* output parameter -o
			 * - missing or S: write IMF to file (named after source file, or doc ID on System.in source)
			 * - set to O: write IMF to System.out
			 * - set to file path: write to that file
			 * - set to folder path: write data to that folder */
			else if ("-o".equalsIgnoreCase(args[a]) && ((a+1) < args.length)) {
				outPath = args[a+1];
				a += 2;
			}
			/* mode parameter -m
			 * - missing or set to A: convert entire PDF, store as IMF
			 * - set to D: convert entire PDF, store as IMD (better for file system based hand-over to master process if running as slave to some other JVM)
			 * - set to F: extract figures only (requires output set to file path)
			 * - set to R: extract text only, output as raw XML
			 * - set to X: extract text only, output as XML
			 * - set to T: extract text only, output plain text */
			else if ("-m".equalsIgnoreCase(args[a]) && ((a+1) < args.length)) {
				mode = args[a+1];
				a += 2;
			}
			/* help parameter -? / -h
			 * - set: print help and exit */
			else if ("-?".equalsIgnoreCase(args[a]) || "-h".equalsIgnoreCase(args[a])) {
				printHelp();
				return;
			}
			//	something's wrong
			else {
				printError("Invalid argument '" + args[a] + "', use '-?' or '-h' to list available arguments");
				return;
			}
		}
		
		//	check parameters before investing effort in loading data
		if (("GDSMO".indexOf(sourceType) == -1) || (sourceType.length() != 1)) {
			printError("Invalid source type '" + sourceType + "'");
			return;
		}
		if ("D".equals(sourceType) && ("DVURQ".indexOf(fontMode) == -1)) {
			printError("Invalid font mode '" + fontMode + "'");
			return;
		}
		if ("D".equals(sourceType) && ("DVU".indexOf(fontMode) != -1) && ("USMFLBC".indexOf(fontCharSet) == -1)) {
			printError("Invalid font decoding charset '" + fontCharSet + "'");
			return;
		}
		if ("D".equals(sourceType) && ("DVU".indexOf(fontMode) != -1) && "C".equals(fontCharSet) && (fontCharSetPath == null)) {
			printError("Font decoding charset file missing for font mode D and char set C");
			return;
		}
		if (("ADRXTF".indexOf(mode) == -1) || (mode.length() != 1)) {
			printError("Invalid conversion mode '" + mode + "'");
			return;
		}
		if (("MS".indexOf(cpuMode) == -1) || (cpuMode.length() != 1)) {
			printError("Invalid CPU usage mode '" + cpuMode + "'");
			return;
		}
		if ("F".equals(mode) && !"D".equals(sourceType)) {
			printError("Invalid conversion mode '" + mode + "' for source type '" + sourceType + "'");
			return;
		}
		
		//	redirect temporary files to configured cache
		System.setProperty("java.io.tmpdir", cacheBasePath);
		
		//	load and check char set file if specified
		FontDecoderCharset fontDecoderCharSet = null;
		if ("D".equals(sourceType)) {
			if ("Q".equals(fontMode))
				fontDecoderCharSet = PdfFontDecoder.NO_DECODING;
			else if ("R".equals(fontMode))
				fontDecoderCharSet = PdfFontDecoder.RENDER_ONLY;
			else if ("U".equals(fontMode) || "V".equals(fontMode) || "D".equals(fontMode)) {
				if ("U".equals(fontCharSet))
					fontDecoderCharSet = PdfFontDecoder.UNICODE;
				else if ("S".equals(fontCharSet))
					fontDecoderCharSet = FontDecoderCharset.union(PdfFontDecoder.LATIN_FULL, PdfFontDecoder.SYMBOLS);
				else if ("M".equals(fontCharSet))
					fontDecoderCharSet = FontDecoderCharset.union(PdfFontDecoder.LATIN_FULL, PdfFontDecoder.MATH);
				else if ("F".equals(fontCharSet))
					fontDecoderCharSet = PdfFontDecoder.LATIN_FULL;
				else if ("L".equals(fontCharSet))
					fontDecoderCharSet = PdfFontDecoder.LATIN;
				else if ("B".equals(fontCharSet))
					fontDecoderCharSet = PdfFontDecoder.LATIN_BASIC;
				else if ("C".equals(fontCharSet)) {
					String charSetName;
					BufferedReader charSetReader;
					if (fontCharSetPath.startsWith("http://") || fontCharSetPath.startsWith("https://")) {
						charSetName = fontCharSetPath.substring(fontCharSetPath.lastIndexOf('/') + "/".length());
						charSetReader = new BufferedReader(new InputStreamReader((new URL(fontCharSetPath)).openStream(), "UTF-8"));
					}
					else {
						File charSetFile = new File(fontCharSetPath);
						if (charSetFile.exists()) {
							charSetName = charSetFile.getName();
							charSetReader = new BufferedReader(new InputStreamReader(new FileInputStream(charSetFile), "UTF-8"));
						}
						else {
							printError("Invalid font decoding charset file '" + fontCharSetPath + "'");
							return;
						}
					}
					fontDecoderCharSet = CustomFontDecoderCharset.readCharSet(charSetName, charSetReader);
					charSetReader.close();
				}
				else fontDecoderCharSet = PdfFontDecoder.UNICODE;
				if ("U".equals(fontMode)) // add "unmapped-only" behavior if requested
					fontDecoderCharSet = FontDecoderCharset.union(fontDecoderCharSet, PdfFontDecoder.DECODE_UNMAPPED); 
				else if ("V".equals(fontMode)) // add "only-verify-mapped" behavior if requested
					fontDecoderCharSet = FontDecoderCharset.union(fontDecoderCharSet, PdfFontDecoder.VERIFY_MAPPED); 
			}
			else fontDecoderCharSet = PdfFontDecoder.UNICODE;
		}
		
		//	create input source
		BufferedInputStream pdfIn;
		if (sourcePath == null)
			pdfIn = new BufferedInputStream(System.in);
		else {
			File sourceFile = new File(sourcePath);
			if (!sourceFile.exists()) {
				printError("Invalid input file '" + sourcePath + "'");
				return;
			}
			pdfIn = new BufferedInputStream(new FileInputStream(sourceFile));
		}
		
		//	determine output file extension
		String outFileExt;
		if ("A".equals(mode))
			outFileExt = "imf";
		else if ("D".equals(mode))
			outFileExt = "imdir";
		else if ("R".equals(mode) || "X".equals(mode))
			outFileExt = "xml";
		else if ("T".equals(mode))
			outFileExt = "txt";
		else if ("F".equals(mode))
			outFileExt = "figures";
		else outFileExt = null;
		
		//	create output destination
		File outFile;
		if ("S".equals(outPath)) {
			if (sourcePath == null)
				outFile = new File("./pdf.converted." + outFileExt);
			else outFile = new File(sourcePath + "." + outFileExt);
		}
		else if ("O".equals(outPath))
			outFile = null;
		else {
			outFile = new File(outPath);
			if (("F".equals(mode) || "D".equals(mode)) && outFile.exists() && !outFile.isDirectory()) {
				printError("Output destination '" + outPath + "' is invalid for mode '" + mode + "'");
				return;
			}
			if (!"F".equals(mode) && !"D".equals(mode) && outFile.exists() && outFile.isDirectory())
				outFile = new File(outFile, ("./pdf.converted." + outFileExt));
		}
		
		//	read input PDF
		byte[] pdfByteBuffer = new byte[1024];
		ByteArrayOutputStream pdfByteCollector = new ByteArrayOutputStream();
		for (int r; (r = pdfIn.read(pdfByteBuffer, 0, pdfByteBuffer.length)) != -1;)
			pdfByteCollector.write(pdfByteBuffer, 0, r);
		pdfIn.close();
		byte[] pdfBytes = pdfByteCollector.toByteArray();
		
		//	preserve System.out and System.err
		final PrintStream sysOut = System.out;
		final PrintStream sysErr = System.err;
		
		//	set up logging
		ProgressMonitor pm;
		if ("S".equals(logPath)) {
			pm = new ProgressMonitor() {
				public void setStep(String step) {}
				public void setInfo(String info) {}
				public void setBaseProgress(int baseProgress) {}
				public void setMaxProgress(int maxProgress) {}
				public void setProgress(int progress) {}
			};
		}
		else if ("O".equals(logPath)) {
			pm = new ProgressMonitor() {
				public void setStep(String step) {
					sysOut.println(step);
				}
				public void setInfo(String info) {
					sysOut.println(info);
				}
				public void setBaseProgress(int baseProgress) {}
				public void setMaxProgress(int maxProgress) {}
				public void setProgress(int progress) {}
			};
		}
		else if ("M".equals(logPath)) {
			pm = new ProgressMonitor() {
				public void setStep(String step) {
					sysOut.println("S:" + step);
				}
				public void setInfo(String info) {
					sysOut.println("I:" + info);
				}
				public void setBaseProgress(int baseProgress) {
					sysOut.println("BP:" + baseProgress);
				}
				public void setMaxProgress(int maxProgress) {
					sysOut.println("MP:" + maxProgress);
				}
				public void setProgress(int progress) {
					sysOut.println("P:" + progress);
				}
			};
		}
		else {
			File logFile = new File(logPath);
			if (logFile.exists() && logFile.isDirectory()) {
				printError("Cannot log to folder '" + logPath + "'");
				return;
			}
			logFile.createNewFile();
			if (!logFile.exists()) {
				printError("Could not create log file '" + logPath + "'");
				return;
			}
			final PrintWriter logWriter = new PrintWriter(new OutputStreamWriter(new FileOutputStream(logFile), "UTF-8"));
			pm = new ProgressMonitor() {
				public void setStep(String step) {
					logWriter.println(step);
				}
				public void setInfo(String info) {
					logWriter.println(info);
				}
				public void setBaseProgress(int baseProgress) {}
				public void setMaxProgress(int maxProgress) {}
				public void setProgress(int progress) {}
			};
		}
		
		//	silence System.out
		System.setOut(new PrintStream(new OutputStream() {
			public void write(int b) throws IOException {}
		}));
		
		//	create page image store
		File pageImageFolder = new File(cacheBasePath + "/PageImages/");
		if (!pageImageFolder.exists())
			pageImageFolder.mkdirs();
		ImageIO.setUseCache(false);
		PageImageStore pis = new PetPageImageStore(pageImageFolder);
		PageImage.addPageImageSource(pis);
		
		//	create PDF extractor
		File supplementFolder = new File(cacheBasePath + "/Supplements/");
		if (!supplementFolder.exists())
			supplementFolder.mkdirs();
		PdfExtractor pdfExtractor = new PetPdfExtractor(new File("."), new File(cacheBasePath), pis, "M".equals(cpuMode), supplementFolder);
		
		//	decode input PDF
		ImDocument imDoc;
		if ("G".equals(sourceType))
			imDoc = pdfExtractor.loadGenericPdf(pdfBytes, pm);
		else if ("D".equals(sourceType))
			imDoc = pdfExtractor.loadTextPdf(pdfBytes, fontDecoderCharSet, pm);
		else if ("S".equals(sourceType))
			imDoc = pdfExtractor.loadImagePdf(pdfBytes, false, pm);
		else if ("M".equals(sourceType))
			imDoc = pdfExtractor.loadImagePdf(pdfBytes, true, pm);
		else if ("O".equals(sourceType))
			imDoc = pdfExtractor.loadImagePdf(pdfBytes, true, true, pm);
		else imDoc = null;
		
		//	shut down PDF extractor
		pdfExtractor.shutdown();
		
		//	write output IMF
		if ("A".equals(mode)) {
			BufferedOutputStream imfOut;
			if (outFile == null) {
				sysOut.println("IMF DATA");
				imfOut = new BufferedOutputStream(sysOut);
			}
			else {
				outFile.createNewFile();
				imfOut = new BufferedOutputStream(new FileOutputStream(outFile));
			}
			ImDocumentIO.storeDocument(imDoc, imfOut, pm);
			imfOut.flush();
			imfOut.close();
		}
		
		//	write output IMD
		else if ("D".equals(mode)) {
			outFile.mkdirs();
			ImDocumentEntry[] docEntries = ImDocumentIO.storeDocument(imDoc, outFile, pm);
			
			String eOutPath = outFile.getAbsolutePath();
			if (eOutPath.endsWith(".imdir"))
				eOutPath = eOutPath.substring(0, eOutPath.lastIndexOf('.'));
			File eOutFile = new File(eOutPath + ".imd");
			BufferedWriter eOut = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(eOutFile), "UTF-8"));
			for (int e = 0; e < docEntries.length; e++) {
				eOut.write(docEntries[e].toTabString());
				eOut.newLine();
			}
			eOut.flush();
			eOut.close();
		}
		
		//	write output XML
		else if ("R".equals(mode) || "X".equals(mode)) {
			BufferedWriter xmlOut;
			if (outFile == null) {
				sysOut.println("XML DATA");
				xmlOut = new BufferedWriter(new OutputStreamWriter(sysOut, "UTF-8"));
			}
			else {
				outFile.createNewFile();
				xmlOut = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFile), "UTF-8"));
			}
			ImDocumentRoot xmlDoc = new ImDocumentRoot(imDoc, ("R".equals(mode) ? ImDocumentRoot.NORMALIZATION_LEVEL_RAW : ImDocumentRoot.NORMALIZATION_LEVEL_PARAGRAPHS));
			AnnotationUtils.writeXML(xmlDoc, xmlOut);
			xmlOut.flush();
			xmlOut.close();
		}
		
		//	write output TXT
		else if ("T".equals(mode)) {
			BufferedWriter xmlOut;
			if (outFile == null) {
				sysOut.println("TXT DATA");
				xmlOut = new BufferedWriter(new OutputStreamWriter(sysOut, "UTF-8"));
			}
			else {
				outFile.createNewFile();
				xmlOut = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFile), "UTF-8"));
			}
			ImDocumentRoot xmlDoc = new ImDocumentRoot(imDoc, ImDocumentRoot.NORMALIZATION_LEVEL_RAW);
			AnnotationUtils.writeXML(xmlDoc, xmlOut);
			xmlOut.flush();
			xmlOut.close();
		}
		
		//	write output figures
		else if ("F".equals(mode)) {
			//	TODO consider exporting multi-part figures as a whole
			ImSupplement[] supplements = imDoc.getSupplements();
			for (int s = 0; s < supplements.length; s++) {
				if (supplements[s] instanceof ImSupplement.Figure) {
					InputStream figDataIn = supplements[s].getInputStream();
					String figMimeType = supplements[s].getMimeType();
					String figFileName = (supplements[s].getId() + "." + figMimeType.substring(figMimeType.lastIndexOf('/') + "/".length()));
					File figOutFile = new File(outFile, figFileName);
					BufferedOutputStream figOut = new BufferedOutputStream(new FileOutputStream(figOutFile));					
					byte[] figDataBuffer = new byte[1024];
					for (int r; (r = figDataIn.read(figDataBuffer, 0, figDataBuffer.length)) != -1;)
						figOut.write(figDataBuffer, 0, r);
					figOut.flush();
					figOut.close();
				}
				else if (supplements[s] instanceof ImSupplement.Graphics) {
					ImSupplement.Graphics graphics = ((ImSupplement.Graphics) supplements[s]);
					ImPage page = imDoc.getPage(graphics.getPageId());
					if (page == null)
						continue;
					ImSupplement.Graphics[] graphicsTray = {((ImSupplement.Graphics) supplements[s])};
					CharSequence svg = ImSupplement.getSvg(graphicsTray, page.getWordsInside(graphics.getBounds()), page);
					Reader svgIn = new CharSequenceReader(svg);
					String svgFileName = (supplements[s].getId() + ".svg");
					File svgOutFile = new File(outFile, svgFileName);
					BufferedWriter svgOut = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(svgOutFile), "UTF-8"));
					char[] svgBuffer = new char[1024];
					for (int r; (r = svgIn.read(svgBuffer, 0, svgBuffer.length)) != -1;)
						svgOut.write(svgBuffer, 0, r);
					svgOut.flush();
					svgOut.close();
					svgIn.close();
				}
			}
		}
		
		//	clean up cached data (if we get here, we have stored all the user wanted)
		cleanCacheFolder(pageImageFolder, ("./Temp".equals(cacheBasePath) ? 1 : 0));
		cleanCacheFolder(supplementFolder, ("./Temp".equals(cacheBasePath) ? 1 : 0));
	}
	
	private static void cleanCacheFolder(File folder, int depth) {
		File[] folderContent = folder.listFiles();
		for (int c = 0; c < folderContent.length; c++) try {
			if (folderContent[c].isDirectory()) {
				cleanCacheFolder(folderContent[c], (depth+1));
				if (depth != 0)
					folderContent[c].delete();
			}
			else folderContent[c].delete();
		}
		catch (Throwable t) {
			System.out.println("Error cleaning up cached file '" + folderContent[c].getAbsolutePath() + "': " + t.getMessage());
			t.printStackTrace(System.out);
		}
	}
	
	private static class PetPageImageStore extends AbstractPageImageStore {
		private AnalyzerDataProvider pisDataProvider;
		private HashMap byteCache = new HashMap();
		PetPageImageStore(File pisDataPath) {
			this.pisDataProvider = new AnalyzerDataProviderFileBased(pisDataPath);
		}
		public boolean isPageImageAvailable(String name) {
			if (!name.endsWith(IMAGE_FORMAT))
				name += ("." + IMAGE_FORMAT);
			if (pisDataProvider.isDataAvailable(name))
				return true;
			else return this.byteCache.containsKey(name);
		}
		public PageImageInputStream getPageImageAsStream(String name) throws IOException {
			if (!name.endsWith(IMAGE_FORMAT))
				name += ("." + IMAGE_FORMAT);
			if (pisDataProvider.isDataAvailable(name))
				return new PageImageInputStream(pisDataProvider.getInputStream(name), this);
			else if (this.byteCache.containsKey(name))
				return new PageImageInputStream(new ByteArrayInputStream((byte[]) this.byteCache.get(name)), this);
			else return null;
		}
		public boolean storePageImage(String name, PageImage pageImage) throws IOException {
			if (!name.endsWith(IMAGE_FORMAT))
				name += ("." + IMAGE_FORMAT);
			try {
				OutputStream imageOut = pisDataProvider.getOutputStream(name);
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
	
	private static class PetPdfExtractor extends PdfExtractor {
		private File supplementFolder;
		PetPdfExtractor(File basePath, File cachePath, PageImageStore imageStore, boolean useMultipleCores, File supplementFolder) {
			super(basePath, cachePath, imageStore, useMultipleCores);
			this.supplementFolder = supplementFolder;
		}
		protected ImDocument createDocument(String docId) {
			return new PetImDocument(docId, this.supplementFolder);
		}
	}
	
	private static class PetImDocument extends ImDocument {
		private ImSupplementCache supplementCache;
		PetImDocument(String docId, File supplementFolder) {
			super(docId);
			this.supplementCache = new ImSupplementCache(this, supplementFolder, maxInMemorySupplementBytes);
		}
		public ImSupplement addSupplement(ImSupplement ims) {
			ims = this.supplementCache.cacheSupplement(ims);
			return super.addSupplement(ims);
		}
		public void removeSupplement(ImSupplement ims) {
			this.supplementCache.deleteSupplement(ims);
			super.removeSupplement(ims);
		}
	}
	
	private static void printHelp() {
		System.out.println("=== PDF to IMF Converter / Data Extractor ===");
		System.out.println("Command line arguments:");
		System.out.println("-?/-h\tOutput this help text and exit.");
		System.out.println("-s <sourceFileName>\tRead PDF from <sourceFileName> instead of System.in.");
		System.out.println("-t <pdfType>\tSelect the type of PDF to load:" +
				"\r\n\t- D: born-digital PDF" +
				"\r\n\t- S: scanned PDF" +
				"\r\n\t- M: scanned PDF with born-digital meta pages (leading or tailing)" +
				"\r\n\t- O: scanned PDF with embedded OCR to reuse" +
				"\r\n\t- G: generic PDF, let converter determine type (the default)");
		System.out.println("-f <fontMode>\tSelect how to handle embedded fonts (relevant only for '-t D' and '-t G'):" +
				"\r\n\t- D: completely decode embedded fonts (the default)" +
				"\r\n\t- V: completely decode un-mapped characters from embedded fonts (ones without a Unicode mapping), and verify existing Unicode mappings" +
				"\r\n\t- U: completely decode un-mapped characters from embedded fonts (ones without a Unicode mapping)" +
				"\r\n\t- R: render embedded fonts, but do not decode glyphs" +
				"\r\n\t- Q: quick mode, use Unicode mapping only");
		System.out.println("-cs <charSet>\tSelect char set for decoding embedded fonts (relevant only for '-f D' and '-f U'):" +
				"\r\n\t- U: use all of Unicode (the default)" +
				"\r\n\t- S: use Latin characters and scientific symbols only" +
				"\r\n\t- M: use Latin characters and mathematical symbols only" +
				"\r\n\t- F: use Full Latin and derived characters only" +
				"\r\n\t- L: use Extended Latin characters only" +
				"\r\n\t- B: use Basic Latin characters only" +
				"\r\n\t- C: custom, use '-cp' parameter to specify path (file or URL) to load from");
		System.out.println("-cp <charSetPath>\tSpecify file or URL to load char set for embedded font decoding from (relevant only for '-f D -cs C', and required then).");
		System.out.println("-l <logMode>\tSelect the log mode:" +
				"\r\n\t- O: log to System.out" +
				"\r\n\t- M: log to System.out with leading progress monitor tags" +
				"\r\n\t- <logFileName>: log to <logFileName>" +
				"\r\n\t- S: silent, don't log (the default)");
		System.out.println("-c <cacheBaseFolder>\tSet the cache base folder:" +
				"\r\n\t- .: cache in sub folders of execution folder (the default)" +
				"\r\n\t- <cacheBaseFolder>: cache in folders under <cacheBaseFolder>");
		System.out.println("-p <cpuMode>\tSet the CPU usage mode:" +
				"\r\n\t- M: use all available CPU cores for fast decoding (the default)" +
				"\r\n\t- S: use a single CPU core");
		System.out.println("-o <outputDestination>\tSelect where to write the converted data:" +
				"\r\n\t- S: write IMF to file named after source file (creates <docId>.imf if" +
				"\r\n\t     input comes from System.in)" +
				"\r\n\t- O: write IMF to System.out" +
				"\r\n\t- <outputFile>: write IMF to this file" +
				"\r\n\t- <outputFolder>: write IMF contents to this folder (folder has to" +
				"\r\n\t                   exist, required in mode F)");
		System.out.println("-m <mode>\tSelect the conversion mode:" +
				"\r\n\t- T: extract only text, output as plain text" +
				"\r\n\t- R: extract only text, output as raw XML" +
				"\r\n\t- X: extract only text, output as paragraph-normalized XML" +
				"\r\n\t- F: extract figures only (requires -o set to existing folder)" +
				"\r\n\t- A: convert complete PDF into IMF (the default)" +
				"\r\n\t- D: convert complete PDF into IMD (exploded IMF)");
	}
	
	private static void printError(String error) {
		System.out.println("=== PDF to IMF Converter / Data Extractor ===");
		System.out.println(error);
	}
}