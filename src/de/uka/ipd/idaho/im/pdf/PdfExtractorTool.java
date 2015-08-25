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
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.HashMap;

import javax.imageio.ImageIO;

import de.uka.ipd.idaho.gamta.AnnotationUtils;
import de.uka.ipd.idaho.gamta.util.AnalyzerDataProvider;
import de.uka.ipd.idaho.gamta.util.AnalyzerDataProviderFileBased;
import de.uka.ipd.idaho.gamta.util.ProgressMonitor;
import de.uka.ipd.idaho.gamta.util.imaging.PageImage;
import de.uka.ipd.idaho.gamta.util.imaging.PageImageInputStream;
import de.uka.ipd.idaho.gamta.util.imaging.PageImageStore;
import de.uka.ipd.idaho.gamta.util.imaging.PageImageStore.AbstractPageImageStore;
import de.uka.ipd.idaho.im.ImDocument;
import de.uka.ipd.idaho.im.ImSupplement;
import de.uka.ipd.idaho.im.gamta.ImDocumentRoot;
import de.uka.ipd.idaho.im.util.ImfIO;

/**
 * Command line wrapper for PDF extractor class.
 * 
 * @author sautter
 */
public class PdfExtractorTool {
	
	private static final int maxInMemoryImageSupplementBytes = (50 * 1024 * 1024); // 50 MB
	
	public static void main(String[] args) throws Exception {
		
		//	read parameters
		String sourcePath = null;
		String sourceType = "G";
		String logPath = "S";
		String outPath = "S";
		String cacheBasePath = ".";
		String mode = "A";
		for (int a = 0; a < args.length;) {
			/* source parameter -s
			 * - missing: System.in
			 * - set: file path */
			if ("-s".equalsIgnoreCase(args[a]) && ((a+1) < args.length)) {
				sourcePath = args[a+1];
				a += 2;
			}
			/* path parameter -p
			 * - missing or set to .: execution folder
			 * - set to folder path: cache folder */
			else if ("-p".equalsIgnoreCase(args[a]) && ((a+1) < args.length)) {
				cacheBasePath = args[a+1];
				a += 2;
			}
			/* source type parameter -t
			 * - missing or set to G: generic PDF
			 * - set to D: born-digital
			 * - set to S: scanned
			 * - set to M: scanned with meta pages */
			else if ("-t".equalsIgnoreCase(args[a]) && ((a+1) < args.length)) {
				sourceType = args[a+1];
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
			 * - missing of S: write IMF to file (named after source file, or doc ID on System.in source)
			 * - set to O: write IMF to System.out
			 * - set to file path: write to that file
			 * - set to folder path: write data to that file */
			else if ("-o".equalsIgnoreCase(args[a]) && ((a+1) < args.length)) {
				outPath = args[a+1];
				a += 2;
			}
			/* mode parameter -m
			 * - missing or set to A: convert entire PDF
			 * - set to F: extract figures only (requires output set to file path)
			 * - set to X: extract text only, output as XML
			 * - set to T: extract text only, output plain text */
			else if ("-m".equalsIgnoreCase(args[a]) && ((a+1) < args.length)) {
				mode = args[a+1];
				a += 2;
			}
			/* help parameter -? / -h
			 * - set: print about this mail */
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
		if (("GDSM".indexOf(sourceType) == -1) || (sourceType.length() != 1)) {
			printError("Invalid source type '" + sourceType + "'");
			return;
		}
		if (("AXTF".indexOf(mode) == -1) || (mode.length() != 1)) {
			printError("Invalid conversion mode '" + mode + "'");
			return;
		}
		if ("F".equals(mode) && !"D".equals(sourceType)) {
			printError("Invalid conversion mode '" + mode + "' for source type '" + sourceType + "'");
			return;
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
		else if ("X".equals(mode))
			outFileExt = "xml";
		else if ("T".equals(mode))
			outFileExt = "txt";
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
			if ("F".equals(mode) && (!outFile.exists() || !outFile.isDirectory())) {
				printError("Output destination '" + outPath + "' is invalid for mode 'F'");
				return;
			}
			else if (!"F".equals(mode) && outFile.exists() && outFile.isDirectory())
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
		ImageIO.setUseCache(false);
		final File pisDataPath = new File(cacheBasePath + "/PageImages/");
		final AnalyzerDataProvider pisDataProvider = new AnalyzerDataProviderFileBased(pisDataPath);
		PageImageStore pis = new AbstractPageImageStore() {
			HashMap byteCache = new HashMap();
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
		
		//	create PDF extractor
		final File supplementImageFolder = new File(cacheBasePath + "/SupplementImages");
		if (!supplementImageFolder.exists())
			supplementImageFolder.mkdirs();
		PageImage.addPageImageSource(pis);
		PdfExtractor pdfExtractor = new PdfExtractor(new File("."), pis, true) {
			protected ImDocument createDocument(String docId) {
				return new ImDocument(docId) {
					private long inMemorySupplementBytes = 0;
					public ImSupplement addSupplement(ImSupplement ims) {
						
						//	store known type supplements on disc if there are too many or too large
						if ((ims instanceof ImSupplement.Figure) || (ims instanceof ImSupplement.Scan) || (ims instanceof ImSupplement.Source)) try {
							
							//	threshold already exceeded, disc cache right away
							if (this.inMemorySupplementBytes > maxInMemoryImageSupplementBytes)
								ims = this.createDiscSupplement(ims, null);
							
							//	still below threshold, check source
							else {
								InputStream sis = ims.getInputStream();
								
								//	this one resides in memory, count it
								if (sis instanceof ByteArrayInputStream)
									this.inMemorySupplementBytes += sis.available();
								
								//	threshold just exceeded
								if (this.inMemorySupplementBytes > maxInMemoryImageSupplementBytes) {
									
									//	disc cache all existing image supplements
									ImSupplement[] imss = this.getSupplements();
									for (int s = 0; s < imss.length; s++) {
										if ((imss[s] instanceof ImSupplement.Figure) || (imss[s] instanceof ImSupplement.Scan))
											super.addSupplement(this.createDiscSupplement(imss[s], null));
									}
									
									//	disc cache argument supplement
									ims = this.createDiscSupplement(ims, sis);
								}
							}
						}
						catch (IOException ioe) {
							System.out.println("Error caching supplement '" + ims.getId() + "': " + ioe.getMessage());
							ioe.printStackTrace(System.out);
						}
						
						//	store (possibly modified) supplement
						return super.addSupplement(ims);
					}
					
					private ImSupplement createDiscSupplement(ImSupplement ims, InputStream sis) throws IOException {
						
						//	get input stream if not already done
						if (sis == null)
							sis = ims.getInputStream();
						
						//	this one's not in memory, close input stream and we're done
						if (!(sis instanceof ByteArrayInputStream)) {
							sis.close();
							return ims;
						}
						
						//	get file name and extension
						String sDataName = ims.getId().replaceAll("[^a-zA-Z0-9]", "_");
						String sDataType = ims.getMimeType();
						if (sDataType.indexOf('/') != -1)
							sDataType = sDataType.substring(sDataType.indexOf('/') + "/".length());
						
						//	create file
						final File sFile = new File(supplementImageFolder, (this.docId + "." + sDataName + "." + sDataType));
						
						//	store supplement in file (if not done in previous run)
						if (!sFile.exists()) {
							sFile.createNewFile();
							OutputStream sos = new BufferedOutputStream(new FileOutputStream(sFile));
							byte[] sBuffer = new byte[1024];
							for (int r; (r = sis.read(sBuffer, 0, sBuffer.length)) != -1;)
								sos.write(sBuffer, 0, r);
							sos.flush();
							sos.close();
						}
						
						//	replace supplement with disc based one
						if (ims instanceof ImSupplement.Figure)
							return new ImSupplement.Figure(this, ims.getMimeType(), ((ImSupplement.Figure) ims).getPageId(), ((ImSupplement.Figure) ims).getDpi(), ((ImSupplement.Figure) ims).getBounds()) {
								public InputStream getInputStream() throws IOException {
									return new BufferedInputStream(new FileInputStream(sFile));
								}
							};
						else if (ims instanceof ImSupplement.Scan)
							return new ImSupplement.Scan(this, ims.getMimeType(), ((ImSupplement.Scan) ims).getPageId(), ((ImSupplement.Scan) ims).getDpi()) {
								public InputStream getInputStream() throws IOException {
									return new BufferedInputStream(new FileInputStream(sFile));
								}
							};
						else if (ims instanceof ImSupplement.Source)
							return new ImSupplement.Source(this, ims.getMimeType()) {
								public InputStream getInputStream() throws IOException {
									return new BufferedInputStream(new FileInputStream(sFile));
								}
							};
						else return ims; // never gonna happen, but Java don't know
					}
				};
			}
		};
		
		//	decode input PDF
		ImDocument imDoc;
		if ("G".equals(sourceType))
			imDoc = pdfExtractor.loadGenericPdf(pdfBytes, pm);
		else if ("D".equals(sourceType))
			imDoc = pdfExtractor.loadTextPdf(pdfBytes, pm);
		else if ("S".equals(sourceType))
			imDoc = pdfExtractor.loadImagePdf(pdfBytes, false, pm);
		else if ("M".equals(sourceType))
			imDoc = pdfExtractor.loadImagePdf(pdfBytes, true, pm);
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
			ImfIO.storeDocument(imDoc, imfOut, ProgressMonitor.dummy);
			imfOut.flush();
			imfOut.close();
		}
		
		//	write output XML
		else if ("X".equals(mode)) {
			BufferedWriter xmlOut;
			if (outFile == null) {
				sysOut.println("XML DATA");
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
			ImSupplement[] supplements = imDoc.getSupplements();
			for (int s = 0; s < supplements.length; s++)
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
				"\r\n\t- G: generic PDF, let converter determine type (the default)");
		System.out.println("-l <logMode>\tSelect the log mode:" +
				"\r\n\t- O: log to System.out" +
				"\r\n\t- M: log to System.out with leading progress monitor tags" +
				"\r\n\t- <logFileName>: log to <logFileName>" +
				"\r\n\t- S: silent, don't log (the default)");
		System.out.println("-p <cacheBaseFolder>\tSet the cache base folder:" +
				"\r\n\t- .: cache in sub folders of execution folder (the default)" +
				"\r\n\t- <cacheBaseFolder>: cache in folders under <cacheBaseFolder>");
		System.out.println("-o <outputDestination>\tSelect where to write the converted data:" +
				"\r\n\t- S: write IMF to file named after source file (creates <docId>.imf if" +
				"\r\n\t     input comes from System.in)" +
				"\r\n\t- O: write IMF to System.out" +
				"\r\n\t- <outputFile>: write IMF to this file" +
				"\r\n\t- <outputFolder>: write IMF contents to this folder (folder has to" +
				"\r\n\t                   exist, required in mode F)");
		System.out.println("-m <mode>\tSelect the conversion mode:" +
				"\r\n\t- T: extract only text, output as plain text" +
				"\r\n\t- X: extract only text, output as XML" +
				"\r\n\t- F: extract figures only (requires -o set to existing folder)" +
				"\r\n\t- A: convert complete PDF into IMF (the default)");
	}
	
	private static void printError(String error) {
		System.out.println("=== PDF to IMF Converter / Data Extractor ===");
		System.out.println(error);
	}
}