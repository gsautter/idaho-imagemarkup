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
 *     * Neither the name of the Universitaet Karlsruhe (TH) / KIT nor the
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

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import de.uka.ipd.idaho.gamta.util.CountingSet;
import de.uka.ipd.idaho.im.ImDocument;
import de.uka.ipd.idaho.im.ImFont;
import de.uka.ipd.idaho.im.util.ImDocumentData.FolderImDocumentData;
import de.uka.ipd.idaho.stringUtils.csvHandler.StringRelation;
import de.uka.ipd.idaho.stringUtils.csvHandler.StringTupel;

/**
 * @author sautter
 */
public class PdfFontReferenceExtractor {
	private static ImDocument dummyDoc = new ImDocument("dummy");
	public static void main(String[] args) throws Exception {
		String rootPath = "E:/Testdaten/PdfExtract";
		if (args.length > 0)
			rootPath = args[0];
		
		File seekRootFolder = new File(rootPath);
		ArrayList seekFolders = new ArrayList();
		seekFolders.add(seekRootFolder);
		ArrayList docFiles = new ArrayList();
		for (int s = 0; s < seekFolders.size(); s++) {
			File seekFolder = ((File) seekFolders.get(s));
			System.out.println("Checking folder " + seekFolder.getName());
			File[] seekFiles = seekFolder.listFiles();
			for (int f = 0; f < seekFiles.length; f++) {
				if (seekFiles[f].isDirectory()) {
					seekFolders.add(seekFiles[f]);
					System.out.println(" - added folder " + seekFiles[f].getName());
				}
				else if ("entries.txt".equals(seekFiles[f].getName())) {
					docFiles.add(seekFolder);
					System.out.println(" - found IMF entry list");
					break;
				}
				else if (seekFiles[f].getName().endsWith(".imf")) {
					docFiles.add(seekFiles[f]);
					System.out.println(" - found IMF " + seekFiles[f].getName());
				}
			}
		}
		
//		ArrayList fonts = new ArrayList();
		CharMap charsByString = new CharMap();
		for (int d = 0; d < docFiles.size(); d++) {
			File docFile = ((File) docFiles.get(d));
			ByteArrayOutputStream fontsDataBuffer = new ByteArrayOutputStream();
			if (docFile.getName().endsWith(".imf")) {
				ZipInputStream docIn = new ZipInputStream(new BufferedInputStream(new FileInputStream(docFile)));
				for (ZipEntry ze; (ze = docIn.getNextEntry()) != null;) {
					if (!"fonts.csv".equals(ze.getName()))
						continue;
					byte[] buffer = new byte[1024];
					for (int r; (r = docIn.read(buffer, 0, buffer.length)) != -1;)
						fontsDataBuffer.write(buffer, 0, r);
					break;
				}
				docIn.close();
			}
			else try {
				FolderImDocumentData docData = new FolderImDocumentData(docFile, null);
				InputStream fontsIn = docData.getInputStream("fonts.csv");
				byte[] buffer = new byte[1024];
				for (int r; (r = fontsIn.read(buffer, 0, buffer.length)) != -1;)
					fontsDataBuffer.write(buffer, 0, r);
				fontsIn.close();
				docData.dispose();
			}
			catch (FileNotFoundException fnfe) {
				continue; // this one is (most likely) scanned ...
			}
			System.out.println("Got font data from " + docFile.getName() + " (" + d + "/" + docFiles.size() + "): " + fontsDataBuffer.size());
			
			StringRelation fontsData = StringRelation.readCsvData(new InputStreamReader(new ByteArrayInputStream(fontsDataBuffer.toByteArray()), "UTF-8"), true, null);
			ImFont font = null;
			for (int f = 0; f < fontsData.size(); f++) {
				StringTupel charData = fontsData.get(f);
				String fontName = charData.getValue(ImFont.NAME_ATTRIBUTE);
				if (fontName == null)
					continue;
				if ((font == null) || !font.name.equals(fontName)) {
					if (font != null)
						System.out.println(" ==> got " + font.getCharacterCount() + " chars");
					System.out.println("Reading font " + fontName);
					font = new ImFont(dummyDoc, fontName);
					String fontStyle = charData.getValue(ImFont.STYLE_ATTRIBUTE, "");
					font.setBold(fontStyle.indexOf("B") != -1);
					font.setItalics(fontStyle.indexOf("I") != -1);
					font.setSerif(fontStyle.indexOf("S") != -1);
					font.setMonospaced(fontStyle.indexOf("M") != -1);
				}
				int charId = Integer.parseInt(charData.getValue(ImFont.CHARACTER_ID_ATTRIBUTE, "-1"), 16);
				String charStr = charData.getValue(ImFont.CHARACTER_STRING_ATTRIBUTE);
				if (charStr.trim().length() == 0) {
//					System.out.println(" - skipped empty char " + charId);
					continue;
				}
				if ((charStr.length() == 1) && (charStr.charAt(0) < 0x20)) {
//					System.out.println(" - skipped control char " + charId + ": " + charStr);
					continue;
				}
				if ((charStr.length() == 1) && (charStr.charAt(0) == 0x20)) {
//					System.out.println(" - skipped space char " + charId + ": " + charStr);
					continue;
				}
				String charImageHex = charData.getValue(ImFont.CHARACTER_IMAGE_ATTRIBUTE);
				if (charImageHex.replace('0', ' ').trim().length() == 0)
					charImageHex = null;
				font.addCharacter(charId, charStr, charImageHex);
//				System.out.println(" - added char " + charId + ": " + charStr);
//				if (font.getCharacterCount() == 1)
//					fonts.add(font);
				if (charImageHex != null)
//					charsByString.put(new Char(fontName, charData.getValue(ImFont.STYLE_ATTRIBUTE, ""), charStr, charImageHex, font.getImage(charId)));
					charsByString.putChar(charStr, charImageHex);
			}
			if (font != null)
				System.out.println(" ==> got " + font.getCharacterCount() + " chars");
		}
//		System.out.println("Got " + fonts.size() + " fonts");
		System.out.println("Got glyph images for " + charsByString.charCount() + " characters:");
		ArrayList chars = charsByString.strings();
		for (int c = 0; c < chars.size(); c++) {
			String str = ((String) chars.get(c));
			CharList chl = charsByString.getChar(str);
			System.out.println(" - " + str + ((str.length() == 1) ? (" (0x" + Integer.toString(str.charAt(0), 16) + ")") : "") + " with " + chl.size() + " glyph images, " + chl.imageCount() + " distinct ones");
//			if (charData instanceof ArrayList) {
//				System.out.println(" - " + charStr + ((charStr.length() == 1) ? (" (0x" + Integer.toString(charStr.charAt(0), 16) + ")") : "") + " with " + ((ArrayList) charData).size() + " glyph images");
//			}
//			else {
//				System.out.println(" - " + charStr + ((charStr.length() == 1) ? (" (0x" + Integer.toString(charStr.charAt(0), 16) + ")") : "") + " with 1 glyph image");
//			}
		}
		System.out.println("Got " + charsByString.imageCount() + " distinct glyph images");
		ArrayList images = charsByString.images();
		ArrayList ambiguousImages = new ArrayList();
		for (int i = 0; i < images.size(); i++) {
			String imageHex = ((String) images.get(i));
			CharImage chi = charsByString.getImage(imageHex);
			if (chi.strs.elementCount() > 1)
				ambiguousImages.add(chi);
			System.out.print(chi.imageHex);
			System.out.print("\t");
			for (Iterator sit = chi.strs.iterator(); sit.hasNext();) {
				String str = ((String) sit.next());
				System.out.print(str + " " + chi.strs.getCount(str));
				if (sit.hasNext())
					System.out.print("\t");
			}
			System.out.println();
		}
		System.out.println("Got " + ambiguousImages.size() + " ambiguous glyph images");
		Collections.sort(ambiguousImages);
		for (int i = 0; i < ambiguousImages.size(); i++) {
			CharImage chi = ((CharImage) ambiguousImages.get(i));
			System.out.print("IMAGE COLLISION {");
			for (Iterator sit = chi.strs.iterator(); sit.hasNext();) {
				String str = ((String) sit.next());
				System.out.print(str + ((str.length() == 1) ? (" (0x" + Integer.toString(str.charAt(0), 16) + ")") : "") + ": " + chi.strs.getCount(str));
				if (sit.hasNext())
					System.out.print(", ");
			}
			System.out.println("}");
			System.out.println("IMAGE: " + chi.imageHex);
//			JOptionPane.showMessageDialog(null, new JLabel(("IMAGE COLLISION " + chi.strs.elementCount()), new ImageIcon(decodeCharacterImage(chi.imageHex)), JLabel.LEFT));
		}
	}
	
	private static class CharList {
		final CountingSet imageHex = new CountingSet();
		final String str;
		CharList(String str) {
			this.str = str;
		}
		void addImage(String imageHex) {
			this.imageHex.add(imageHex);
		}
		int size() {
			return this.imageHex.size();
		}
		int imageCount() {
			return this.imageHex.elementCount();
		}
	}
	
	private static class CharMap {
		private HashMap chars = new HashMap();
		private HashMap charImages = new HashMap();
		void putChar(String str, String imageHex) {
			CharList chl = ((CharList) this.chars.get(str));
			if (chl == null) {
				chl = new CharList(str);
				this.chars.put(str, chl);
			}
			chl.addImage(imageHex);
			CharImage chi = ((CharImage) this.charImages.get(imageHex));
			if (chi == null) {
				chi = new CharImage(imageHex);
				this.charImages.put(chi.imageHex, chi);
			}
			chi.strs.add(str);
		}
		CharList getChar(String str) {
			return ((CharList) this.chars.get(str));
		}
		int charCount() {
			return this.chars.size();
		}
		ArrayList strings() {
			ArrayList strings = new ArrayList(this.chars.keySet());
			Collections.sort(strings);
			return strings;
		}
		
		CharImage getImage(String imageHex) {
			return ((CharImage) this.charImages.get(imageHex));
		}
		int imageCount() {
			return this.charImages.size();
		}
		ArrayList images() {
			return new ArrayList(this.charImages.keySet());
		}
	}
	
	private static class CharImage implements Comparable {
		final String imageHex;
		final CountingSet strs = new CountingSet(new TreeMap());
		CharImage(String imageHex) {
			this.imageHex = imageHex;
		}
		public int compareTo(Object obj) {
			return ((String) this.strs.first()).compareTo((String) ((CharImage) obj).strs.first());
		}
	}
}
