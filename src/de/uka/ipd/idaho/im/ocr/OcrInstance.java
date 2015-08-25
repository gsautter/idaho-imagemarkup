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
package de.uka.ipd.idaho.im.ocr;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.TreeSet;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageOutputStreamImpl;

import de.uka.ipd.idaho.gamta.util.imaging.BoundingBox;
import de.uka.ipd.idaho.htmlXmlUtil.Parser;
import de.uka.ipd.idaho.htmlXmlUtil.TokenReceiver;
import de.uka.ipd.idaho.htmlXmlUtil.TreeNodeAttributeSet;
import de.uka.ipd.idaho.htmlXmlUtil.accessories.IoTools;
import de.uka.ipd.idaho.htmlXmlUtil.grammars.Html;

/**
 * Wrapper for a single Stream Tesseract process. The Stream Tesseract engine
 * has been built as part of a bachelor thesis by Maximilian Peters in 2013.
 * 
 * @author sautter
 */
public class OcrInstance {
	
	private static final String TESS_INIT = "init";
	private static final String TESS_END = "end";
	private static final String TESS_QUIT = "quit";
	
	private static final String TESS_SET_PAGE_SEG_MODE = "setpagesegmode";
	private static final String PAGE_SEG_MODE_SINGLE_BLOCK = "PSM_SINGLE_BLOCK";
	
	private static final String TESS_SET_IMAGE = "setimage";
	private static final String TESS_PROCESS_PAGES = "processpages";
	private static final String TESS_GET_HTML_TEXT = "gethtmltext";
	
	private static final Html html = new Html();
	private static final Parser parser = new Parser(html);
	
	private static String getTessCommand() {
		String osName = System.getProperty("os.name");
		if (osName.matches("Win.*"))
			return "streamtess-windows.exe";
		else if (osName.matches(".*Linux.*"))
			return "streamtess-linux";
		else if (osName.matches("Mac.*"))
			return "streamtess-mac";
		else {
			System.out.println("OcrEngin: unknown OS name: " + osName);
			return null;
		}
	}
	
	private Process process;
	private BufferedOutputStream out;
	private BufferedReader in;
	private Thread errThread;
	
	private String tessCommand;
	private String tessVersion;
	private TreeSet langs = new TreeSet(String.CASE_INSENSITIVE_ORDER);
	private boolean gotTessInstance = false;
	
	private File tessPath;
	private File cachePath;
	private boolean streamImages = false;
	
	
	/** Constructor (determines Tesseract command from <code>os.name</code>
	 * system property, initially loads English, French, and German)
	 * @param tessPath the folder to work in
	 * @throws IOException
	 */
	public OcrInstance(File tessPath) throws IOException {
		this(null, tessPath, null, null);
	}
	
	/** Constructor (determines Tesseract command from <code>os.name</code>
	 * system property)
	 * @param tessPath the folder to work in
	 * @param langs the languages to load initially, separated by '+'
	 * @throws IOException
	 */
	public OcrInstance(File tessPath, String langs) throws IOException {
		this(null, tessPath, langs, null);
	}
	
	/** Constructor (determines Tesseract command from <code>os.name</code>
	 * system property)
	 * @param tessPath the folder to work in
	 * @param langs the languages to load initially, separated by '+'
	 * @param version the version string to use in the language data path
	 * @throws IOException
	 */
	public OcrInstance(File tessPath, String langs, String version) throws IOException {
		this(null, tessPath, langs, version);
	}
	
	/** Constructor (initially loads English, French, and German)
	 * @param tessCommand the name of the Tesseract binary to use
	 * @param tessPath the folder to work in
	 * @throws IOException
	 */
	public OcrInstance(String tessCommand, File tessPath) throws IOException {
		this(tessCommand, tessPath, null, null);
	}
	
	/** Constructor
	 * @param tessCommand the name of the Tesseract binary to use
	 * @param tessPath the folder to work in
	 * @param langs the languages to load initially, separated by '+'
	 * @throws IOException
	 */
	public OcrInstance(String tessCommand, File tessPath, String langs) throws IOException {
		this(tessCommand, tessPath, langs, null);
	}
	
	/** Constructor
	 * @param tessCommand the name of the Tesseract binary to use
	 * @param tessPath the folder to work in
	 * @param langs the languages to load initially, separated by '+'
	 * @param version the version string to use in the language data path
	 * @throws IOException
	 */
	public OcrInstance(String tessCommand, File tessPath, String langs, String version) throws IOException {
		this.tessCommand = ((tessCommand == null) ? getTessCommand() : tessCommand);
		this.tessVersion = ((version == null) ? "" : version.trim());
		this.tessPath = tessPath;
		if (!this.tessPath.exists())
			this.tessPath.mkdirs();
		this.cachePath = new File(this.tessPath, "cache");
		if (!this.cachePath.exists())
			this.cachePath.mkdirs();
		
		if (!this.install(this.tessCommand))
			throw new IOException("StreamTess: binary not found");
		if (!this.install("tessdata" + this.tessVersion + "/eng.traineddata"))
			throw new IOException("StreamTess: language data for English not found");
		
		this.process = Runtime.getRuntime().exec((this.tessPath.getAbsolutePath() + "/" + this.tessCommand), new String[0], this.tessPath);
		System.out.println("Tesseract process started");
		this.out = new BufferedOutputStream(this.process.getOutputStream());
		this.in = new BufferedReader(new InputStreamReader(this.process.getInputStream(), "UTF-8"));
		this.errThread = new Thread() {
			public void run() {
				InputStream err = process.getErrorStream();
				try {
					for (int b; (b = err.read()) != -1;)
						System.err.print((char) b);
				} catch (IOException ioe) {}
			}
		};
		this.errThread.start();
		
		this.addLangs((langs == null) ? "deu+eng+fra" : langs);
		this.startTessInstance();
	}
	
	private boolean install(String fileName) {
		System.out.println("OCR Instance: installing file '" + fileName + "'");
		if (fileName == null) {
			System.out.println(" ==> source name not found");
			return false;
		}
		File file = new File(this.tessPath, fileName);
		if (file.exists()) {
			System.out.println(" ==> already installed");
			return true;
		}
		
		InputStream fileIn = this.getClass().getClassLoader().getResourceAsStream("bin/StreamTess/" + fileName);
		if (fileIn == null) {
			System.out.println(" ==> source not found");
			return false;
		}
		fileIn = new BufferedInputStream(fileIn);
		
		try {
			file.getParentFile().mkdirs();
			OutputStream fileOut = new BufferedOutputStream(new FileOutputStream(file));
			byte[] fileBuffer = new byte[2048];
			for (int r; (r = fileIn.read(fileBuffer, 0, fileBuffer.length)) != -1;)
				fileOut.write(fileBuffer, 0, r);
			fileOut.flush();
			fileOut.close();
			fileIn.close();
			System.out.println(" ==> installed successfully");
			return true;
		}
		catch (IOException ioe) {
			System.out.println(" ==> could not install file '" + fileName + "'");
			ioe.printStackTrace(System.out);
			return false;
		}
	}
	
	private void startTessInstance() throws IOException {
		if (this.gotTessInstance)
			return;
		this.sendCommand(TESS_INIT + " " + "./tessdata" + this.tessVersion + " " + this.getLangsString());
		this.sendCommand(TESS_SET_PAGE_SEG_MODE + " " + PAGE_SEG_MODE_SINGLE_BLOCK);
		this.gotTessInstance = true;
	}
	private void endTessInstance() throws IOException {
		if (!this.gotTessInstance)
			return;
		this.sendCommand(TESS_END);
		this.gotTessInstance = false;
	}
	
	private String getLangsString() {
		if (this.langs.isEmpty())
			return "eng";
		StringBuffer langs = new StringBuffer("eng");
		for (Iterator lit = this.langs.iterator(); lit.hasNext();) {
			String lang = ((String) lit.next());
			if (!"eng".equalsIgnoreCase(lang))
				langs.append("+" + lang);
		}
		return langs.toString();
	}
	
	/**
	 * Add languages to the OCR instance. The returned boolean indicates if any
	 * languages were actually newly added. For a language to be added, the
	 * respective data file has to be present in the <code>tessdata</code>
	 * folder, e.g. <code>tessdata/eng.traineddata</code> for English, or has
	 * to be installed there successfully. The data file names are the three
	 * letter ISO codes of the languages, in lower case.
	 * @param langs the languages to add, separated by '+'
	 * @return true if there are new languages
	 * @throws IOException
	 */
	public boolean addLangs(String langs) throws IOException {
		if (langs == null)
			return false;
		String[] theLangs = langs.trim().split("\\s*\\+\\s*");
		boolean newLangs = false;
		for (int l = 0; l < theLangs.length; l++) {
			if ("eng".equalsIgnoreCase(theLangs[l]) || this.langs.contains(theLangs[l]))
				continue;
			if (this.install("tessdata" + this.tessVersion + "/" + theLangs[l] + ".traineddata")) {
				this.langs.add(theLangs[l]);
				newLangs = true;
			}
		}
		if (newLangs && this.gotTessInstance) {
			this.endTessInstance();
			this.startTessInstance();
		}
		return newLangs;
	}
	
	private void sendCommand(String command) throws IOException {
		this.out.write((command.trim() + "\r\n").getBytes("UTF-8"));
		this.out.flush();
	}
	
	private String readResponseLine() throws IOException {
		return this.in.readLine();
	}
	
	private String readResponseLines() throws IOException {
		StringBuffer response = new StringBuffer();
		for (String line; (line = this.in.readLine()) != null;) {
			if (line.trim().length() == 0)
				break;
			if (response.length() != 0)
				response.append("\r\n");
			response.append(line);
		}
		return response.toString().trim();
	}
	
	/**
	 * Run an image through Tesseract. If the <code>streamTransfer</code>
	 * argument is false, the argument image is written to a cache file and
	 * loaded back by Tesseract for processing. Wrd bounding boxes are in the
	 * <code>title</code> attribute of spans marking individual words in the
	 * result. They take the form <code>bbox &lt;left&gt; &lt;top&gt;
	 * &lt;right&gt; &lt;bottom&gt;</code>.
	 * @param bi the image to process
	 * @param streamTransfer stream image to Tesseract process
	 * @return a Reader providing the HTML response
	 * @throws IOException
	 */
	public Reader processImageAsStream(BufferedImage bi, boolean streamTransfer) throws IOException {
		ImageBuffer ib = new ImageBuffer();
		ImageIO.write(bi, "png", ib);
		return this.processImageAsStream(ib.getStreamBuffer(), ((int) ib.getStreamPosition()), streamTransfer);
	}
	
	/**
	 * Run an image through Tesseract. If the <code>streamTransfer</code>
	 * argument is false, the argument image is written to a cache file and
	 * loaded back by Tesseract for processing. Wrd bounding boxes are in the
	 * <code>title</code> attribute of spans marking individual words in the
	 * result. They take the form <code>bbox &lt;left&gt; &lt;top&gt;
	 * &lt;right&gt; &lt;bottom&gt;</code>.
	 * @param biBytes the image to process as a byte array (expected to be PNG)
	 * @param streamTransfer stream image to Tesseract process
	 * @return a Reader providing the HTML response
	 * @throws IOException
	 */
	public Reader processImageAsStream(byte[] biBytes, boolean streamTransfer) throws IOException {
		return this.processImageAsStream(biBytes, biBytes.length, streamTransfer);
	}
	
	private synchronized Reader processImageAsStream(byte[] biBytes, int biByteLenght, boolean streamTransfer) throws IOException {
		long start = System.currentTimeMillis();
		
		//	send image
		File biCacheFile = null;
		if (streamTransfer) {
			this.sendCommand(TESS_SET_IMAGE + " " + biByteLenght);
			this.out.write(biBytes, 0, biByteLenght);
			this.out.flush();
			if (DEBUG_STREAM_TRANSFER) System.out.println("Image bytes streamed in " + (System.currentTimeMillis() - start) + " ms");
		}
		else {
			String biCacheFileName = ("tessCache-" + System.currentTimeMillis() + "-" + Math.random() + ".png");
			biCacheFile = new File(this.cachePath, biCacheFileName);
			OutputStream ios = new BufferedOutputStream(new FileOutputStream(biCacheFile));
			ios.write(biBytes, 0, biByteLenght);
			ios.flush();
			ios.close();
			if (DEBUG_STREAM_TRANSFER) System.out.println("Image bytes cached in " + (System.currentTimeMillis() - start) + " ms");
			start = System.currentTimeMillis();
			this.sendCommand(TESS_PROCESS_PAGES + " " + biCacheFile.getAbsolutePath() + " 0 0");
			if (DEBUG_STREAM_TRANSFER) System.out.println("Command sent in " + (System.currentTimeMillis() - start) + " ms");
			start = System.currentTimeMillis();
			String response = this.readResponseLines();
			if (DEBUG_STREAM_TRANSFER) {
				System.out.println("Response read in " + (System.currentTimeMillis() - start) + " ms:");
				System.out.println(response);
			}
		}
		
		//	get reader for HTML
		final File toCleanUp = biCacheFile;
		this.sendCommand(TESS_GET_HTML_TEXT + " 0");
		if (DEBUG_STREAM_TRANSFER) System.out.println("Result getter command sent");
		return new Reader() {
			private String line = null;
			private int offset = -2;
			private int doRead() throws IOException {
				if (this.offset == -1)
					return -1;
				if (this.offset == -2) {
					while((this.line = in.readLine()) != null) {
						if (this.line.startsWith("<html"))
							break;
						if (DEBUG_STREAM_TRANSFER) System.out.println("Skipping " + this.line);
					}
					if (this.line == null) {
						this.offset = -1;
						return -1;
					}
					else {
						this.line += "\r\n";
						this.offset = 0;
					}
				}
				if (this.offset == this.line.length()) {
					if (this.line.startsWith("</html>")) {
						this.offset = -1;
						return -1;
					}
					this.line = in.readLine();
					if (this.line == null) {
						this.offset = -1;
						return -1;
					}
					else {
						this.line += "\r\n";
						this.offset = 0;
					}
				}
				return ((int) this.line.charAt(this.offset++));
			}
			public int read(char[] cbuf, int off, int len) throws IOException {
				int read = 0;
				for (int r; (read < len) && ((r = this.doRead()) != -1);)
					cbuf[off + (read++)] = ((char) r);
				return ((read == 0) ? -1 : read);
			}
			public void close() throws IOException {
				while (this.doRead() != -1);
				toCleanUp.delete();
			}
		};
	}
	
	private static final boolean DEBUG_STREAM_TRANSFER = false;
	
	/**
	 * Run an image through Tesseract, returning the OCR result as words with
	 * bounding boxes. This method essentially handles decoding of the result
	 * returned as a stream by the <code>processImageAsStream()</code> method.
	 * All word bounding boxes are strictly relative to the argument image.
	 * @param blockTextImage the image to process
	 * @return the OCR result as an array of words
	 * @throws IOException
	 */
	public OcrWord[] doBlockOcr(BufferedImage blockTextImage) throws IOException {
		
		//	process image
		BufferedReader blockTextReader = new BufferedReader(this.processImageAsStream(blockTextImage, this.streamImages));
		
		//	read result
		final ArrayList blockWords = new ArrayList();
		parser.stream(blockTextReader, new TokenReceiver() {
			String str = null;
			BoundingBox box = null;
			public void storeToken(String token, int treeDepth) throws IOException {
				if (html.isTag(token)) {
					if (!"span".equalsIgnoreCase(html.getType(token)))
						return;
					if (html.isEndTag(token)) {
						if ((this.str != null) && (this.box != null))
							blockWords.add(new OcrWord(this.str, this.box));
						this.str = null;
						this.box = null;
					}
					else {
						TreeNodeAttributeSet tnas = TreeNodeAttributeSet.getTagAttributes(token, html);
						if (tnas.getAttribute("class", "").endsWith("ocrx_word")) {
							String[] bBoxData = tnas.getAttribute("title", "").trim().split("\\s+");
							if (bBoxData.length == 5)
								this.box = new BoundingBox(Integer.parseInt(bBoxData[1]), Integer.parseInt(bBoxData[3]), Integer.parseInt(bBoxData[2]), Integer.parseInt(bBoxData[4]));
						}
					}
				}
				else if (this.box != null)
					this.str = IoTools.prepareForPlainText(token.trim());
			}
			public void close() throws IOException {}
		});
		blockTextReader.close();
		
		//	finally ...
		return ((OcrWord[]) blockWords.toArray(new OcrWord[blockWords.size()]));
	}
	
	/**
	 * Shut down the OCR instance, in particular the wrapped Tesseract process.
	 * @throws IOException
	 */
	public void shutdown() throws IOException {
		
		//	end Tesseract instance and wrapper process
		this.endTessInstance();
		this.sendCommand(TESS_QUIT);
		
		//	wait for Tesseract process to finish
		try {
			this.process.waitFor();
		} catch (InterruptedException ie) {}
		
		//	wait for error relay thread to finish
		try {
			this.errThread.join();
		} catch (InterruptedException ie) {}
	}
	
	/* Pure in-memory buffer for ImageIO, preventing the overhead of file based
	 * caching when writing image to byte array.
	 */
	private static class ImageBuffer extends ImageOutputStreamImpl {
		private byte[] buffer = new byte[4096];
		ImageBuffer() {}
		private void doWrite(byte b) {
			if (this.streamPos == this.buffer.length) {
				byte[] buffer = new byte[this.buffer.length * 2];
				System.arraycopy(this.buffer, 0, buffer, 0, this.buffer.length);
				this.buffer = buffer;
			}
			this.buffer[(int) this.streamPos++] = b;
		}
		public void write(int b) throws IOException {
			this.flushBits();
			if (127 < b)
				b -= 256;
			this.doWrite((byte) b);
		}
		public void write(byte[] b, int off, int len) throws IOException {
			this.flushBits();
			for (int i = 0; i < len; i++)
				this.doWrite(b[off + i]);
		}
		public int read() throws IOException {
			this.bitOffset = 0;
			int b = this.buffer[(int) this.streamPos++];
			if (b < 0)
				b += 256;
			return b;
		}
		public int read(byte[] b, int off, int len) throws IOException {
			this.bitOffset = 0;
			int read = 0;
			while (read < len)
				b[off + read++] = this.buffer[(int) this.streamPos++];
			return ((read == 0) ? -1 : read);
		}
		byte[] getStreamBuffer() {
			return this.buffer;
		}
	}
}