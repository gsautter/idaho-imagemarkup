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

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import de.uka.ipd.idaho.gamta.util.CountingSet;
import de.uka.ipd.idaho.gamta.util.ParallelJobRunner;
import de.uka.ipd.idaho.gamta.util.ParallelJobRunner.ParallelFor;
import de.uka.ipd.idaho.im.ImDocument;
import de.uka.ipd.idaho.im.ImFont;
import de.uka.ipd.idaho.im.util.ImDocumentIO;
import de.uka.ipd.idaho.stringUtils.csvHandler.StringRelation;
import de.uka.ipd.idaho.stringUtils.csvHandler.StringTupel;

/**
 * @author sautter
 */
public class PdfFontReferenceExtractor {
	private static ImDocument dummyDoc = new ImDocument("dummy");
	public static void main(String[] args) throws Exception {
		if ((args.length > 2) && "show".equals(args[0])) {
			int height = Integer.parseInt(args[1]);
			StringBuffer bits = new StringBuffer();
			String hex = args[2].trim();
			for (int h = 0; h < hex.length(); h++) {
				char ch = hex.charAt(h);
				if (ch == '0')
					bits.append("....");
				else if (ch == '1')
					bits.append("...X");
				else if (ch == '2')
					bits.append("..X.");
				else if (ch == '3')
					bits.append("..XX");
				else if (ch == '4')
					bits.append(".X..");
				else if (ch == '5')
					bits.append(".X.X");
				else if (ch == '6')
					bits.append(".XX.");
				else if (ch == '7')
					bits.append(".XXX");
				else if (ch == '8')
					bits.append("X...");
				else if (ch == '9')
					bits.append("X..X");
				else if (ch == 'A')
					bits.append("X.X.");
				else if (ch == 'B')
					bits.append("X.XX");
				else if (ch == 'C')
					bits.append("XX..");
				else if (ch == 'D')
					bits.append("XX.X");
				else if (ch == 'E')
					bits.append("XXX.");
				else if (ch == 'F')
					bits.append("XXXX");
			}
			int width = (bits.length() / height);
			System.out.println("Size is " + width + "x" + height);
			for (int rowStart = 0; rowStart < bits.length(); rowStart += width)
				System.out.println(bits.substring(rowStart, (rowStart+width)));
			return;
		}
		
		String rootPath = "E:/Testdaten/PdfExtract";
		if (args.length > 0)
			rootPath = args[0];
		
		File seekRootFolder = new File(rootPath);
		ArrayList seekFolders = new ArrayList();
		seekFolders.add(seekRootFolder);
		final ArrayList fontFiles = new ArrayList();
		for (int s = 0; s < seekFolders.size(); s++) {
			File seekFolder = ((File) seekFolders.get(s));
			System.out.println("Checking folder " + seekFolder.getName());
			File[] seekFiles = seekFolder.listFiles();
			for (int f = 0; f < seekFiles.length; f++) {
				if (seekFiles[f].isDirectory()) {
					seekFolders.add(seekFiles[f]);
					System.out.println(" - added folder " + seekFiles[f].getName());
				}
				else if (seekFiles[f].getName().endsWith(".fonts.csv")) {
					fontFiles.add(seekFiles[f]);
					System.out.println(" - found extracted font file " + seekFiles[f].getName());
				}
			}
		}
		
		final ArrayList sourceNames = new ArrayList() {
			public synchronized boolean add(Object e) {
				return super.add(e);
			}
		};
		final CountingSet charImageSourceNames = new CountingSet(new TreeMap()) {
			public synchronized boolean add(Object e) {
				return super.add(e);
			}
		};
		final CountingSet cjkSourceNames = new CountingSet(new TreeMap()) {
			public synchronized boolean add(Object e) {
				return super.add(e);
			}
		};
		final CountingSet privateAreaSourceNames = new CountingSet(new TreeMap()) {
			public synchronized boolean add(Object e) {
				return super.add(e);
			}
		};
		final CountingSet outOfProportionSourceNames = new CountingSet(new TreeMap()) {
			public synchronized boolean add(Object e) {
				return super.add(e);
			}
		};
		final TreeMap charImageHexToSources = new TreeMap() {
			public synchronized Object put(Object key, Object value) {
				LinkedHashSet values = ((LinkedHashSet) super.get(key));
				if (values == null) {
					values = new LinkedHashSet();
					super.put(key, values);
				}
				values.add(value);
				return values; // whatever ...
			}
		};
		final LinkedHashMap charImageHex32to64 = new LinkedHashMap() {
			public synchronized Object put(Object key, Object value) {
				LinkedHashSet values = ((LinkedHashSet) super.get(key));
				if (values == null) {
					values = new LinkedHashSet();
					super.put(key, values);
				}
				values.add(value);
				return values; // whatever ...
			}
		};
		final Set charImageHex64blank32 = Collections.synchronizedSet(new LinkedHashSet());
		final Set charImageHexes64 = Collections.synchronizedSet(new HashSet());
		final CountingSet charImagePixelCounts64 = new CountingSet(new TreeMap()) {
			public synchronized boolean add(Object e) {
				return super.add(e);
			}
		};
		final CountingSet charImageWidths64 = new CountingSet(new TreeMap()) {
			public synchronized boolean add(Object e) {
				return super.add(e);
			}
		};
		final LinkedHashMap charImageHex16to32 = new LinkedHashMap() {
			public synchronized Object put(Object key, Object value) {
				LinkedHashSet values = ((LinkedHashSet) super.get(key));
				if (values == null) {
					values = new LinkedHashSet();
					super.put(key, values);
				}
				values.add(value);
				return values; // whatever ...
			}
		};
		final Set charImageHex32blank16 = Collections.synchronizedSet(new LinkedHashSet());
		final Set charImageHexes32 = Collections.synchronizedSet(new HashSet());
		final CountingSet charImagePixelCounts32 = new CountingSet(new TreeMap()) {
			public synchronized boolean add(Object e) {
				return super.add(e);
			}
		};
		final CountingSet charImageWidths32 = new CountingSet(new TreeMap()) {
			public synchronized boolean add(Object e) {
				return super.add(e);
			}
		};
		final LinkedHashMap charImageHex8to16 = new LinkedHashMap() {
			public synchronized Object put(Object key, Object value) {
				LinkedHashSet values = ((LinkedHashSet) super.get(key));
				if (values == null) {
					values = new LinkedHashSet();
					super.put(key, values);
				}
				values.add(value);
				return values; // whatever ...
			}
		};
		final Set charImageHex16blank8 = Collections.synchronizedSet(new LinkedHashSet());
		final Set charImageHexes16 = Collections.synchronizedSet(new HashSet());
		final CountingSet charImagePixelCounts16 = new CountingSet(new TreeMap()) {
			public synchronized boolean add(Object e) {
				return super.add(e);
			}
		};
		final CountingSet charImageWidths16 = new CountingSet(new TreeMap()) {
			public synchronized boolean add(Object e) {
				return super.add(e);
			}
		};
		final Set charImageHexes8 = Collections.synchronizedSet(new HashSet());
		final CountingSet charImagePixelCounts8 = new CountingSet(new TreeMap()) {
			public synchronized boolean add(Object e) {
				return super.add(e);
			}
		};
		final CountingSet charImageWidths8 = new CountingSet(new TreeMap()) {
			public synchronized boolean add(Object e) {
				return super.add(e);
			}
		};
		final LinkedHashMap charImageSignatures64toSources = new LinkedHashMap() {
			public synchronized Object put(Object key, Object value) {
				LinkedHashSet values = ((LinkedHashSet) super.get(key));
				if (values == null) {
					values = new LinkedHashSet();
					super.put(key, values);
				}
				values.add(value);
				return values; // whatever ...
			}
		};
		final LinkedHashMap charImageSignatures32toSources = new LinkedHashMap() {
			public synchronized Object put(Object key, Object value) {
				LinkedHashSet values = ((LinkedHashSet) super.get(key));
				if (values == null) {
					values = new LinkedHashSet();
					super.put(key, values);
				}
				values.add(value);
				return values; // whatever ...
			}
		};
		final LinkedHashMap charImageSignatures16toSources = new LinkedHashMap() {
			public synchronized Object put(Object key, Object value) {
				LinkedHashSet values = ((LinkedHashSet) super.get(key));
				if (values == null) {
					values = new LinkedHashSet();
					super.put(key, values);
				}
				values.add(value);
				return values; // whatever ...
			}
		};
		final LinkedHashMap charImageSignatures8toSources = new LinkedHashMap() {
			public synchronized Object put(Object key, Object value) {
				LinkedHashSet values = ((LinkedHashSet) super.get(key));
				if (values == null) {
					values = new LinkedHashSet();
					super.put(key, values);
				}
				values.add(value);
				return values; // whatever ...
			}
		};
		final LinkedHashMap charsToImageSignatures64 = new LinkedHashMap() {
			public synchronized Object put(Object key, Object value) {
				CountingSet values = ((CountingSet) super.get(key));
				if (values == null) {
					values = new CountingSet(new HashMap());
					super.put(key, values);
				}
				values.add(value);
				return values; // whatever ...
			}
		};
		final LinkedHashMap charsToImageSignatures32 = new LinkedHashMap() {
			public synchronized Object put(Object key, Object value) {
				CountingSet values = ((CountingSet) super.get(key));
				if (values == null) {
					values = new CountingSet(new HashMap());
					super.put(key, values);
				}
				values.add(value);
				return values; // whatever ...
			}
		};
		final LinkedHashMap charsToImageSignatures16 = new LinkedHashMap() {
			public synchronized Object put(Object key, Object value) {
				CountingSet values = ((CountingSet) super.get(key));
				if (values == null) {
					values = new CountingSet(new HashMap());
					super.put(key, values);
				}
				values.add(value);
				return values; // whatever ...
			}
		};
		final LinkedHashMap charsToImageSignatures8 = new LinkedHashMap() {
			public synchronized Object put(Object key, Object value) {
				CountingSet values = ((CountingSet) super.get(key));
				if (values == null) {
					values = new CountingSet(new HashMap());
					super.put(key, values);
				}
				values.add(value);
				return values; // whatever ...
			}
		};
		final CharMap charsByString64 = new CharMap();
		final CharMap charsByString32 = new CharMap();
		final CharMap charsByString16 = new CharMap();
		final CharMap charsByString8 = new CharMap();
		ParallelJobRunner.runParallelFor(new ParallelFor() {
			public void doFor(int index) throws Exception {
				File fontFile = ((File) fontFiles.get(index));
				StringRelation fontsData = StringRelation.readCsvData(new InputStreamReader(new FileInputStream(fontFile), "UTF-8"), true, null);
				ImFont font = null;
				String docId = fontFile.getName();
				docId = docId.substring(0, docId.indexOf('.'));
				for (int f = 0; f < fontsData.size(); f++) {
					StringTupel charData = fontsData.get(f);
					String fontName = charData.getValue(ImFont.NAME_ATTRIBUTE);
					if (fontName == null)
						continue;
					if ((font == null) || !font.name.equals(fontName)) {
						if (font != null)
							System.out.println(" ==> got " + font.getCharacterCount() + " chars");
						System.out.println("Reading font " + fontName);
						font = new ImFont(dummyDoc, fontName, false, false, false, false);
						String fontStyle = charData.getValue(ImFont.STYLE_ATTRIBUTE, "");
						font.setBold(fontStyle.indexOf("B") != -1);
						font.setItalics(fontStyle.indexOf("I") != -1);
						if (fontStyle.indexOf("M") != -1)
							font.setMonospaced(true);
						else if (fontStyle.indexOf("S") != -1)
							font.setSerif(true);
						sourceNames.add(docId + "/" + font.name);
					}
					int charId = Integer.parseInt(charData.getValue(ImFont.CHARACTER_ID_ATTRIBUTE, "-1"), 16);
					String charStr = charData.getValue(ImFont.CHARACTER_STRING_ATTRIBUTE);
					if ((charId == 0) && (charStr.length() == 0)) {
						String fontAttriutes = charData.getValue(ImFont.CHARACTER_IMAGE_ATTRIBUTE);
						if (fontAttriutes != null)
							ImDocumentIO.setAttributes(font, fontAttriutes);
						continue;
					}
					if (charStr.trim().length() == 0) {
//						System.out.println(" - skipped empty char " + charId);
						continue;
					}
					while ((charStr.length() > 1) && (charStr.charAt(0) <= 0x20))
						charStr = charStr.substring(1); // get rid of leading spaces and control characters
					while ((charStr.length() > 1) && (charStr.charAt(charStr.length() - 1) == '\u00AD'))
						charStr = charStr.substring(0, (charStr.length() - 1)); // get rid of soft hyphen (0xAD) attached to char
					if ((charStr.length() == 1) && (charStr.charAt(0) < 0x20)) {
//						System.out.println(" - skipped control char " + charId + ": " + charStr);
						continue;
					}
					if ((charStr.length() == 1) && (charStr.charAt(0) == 0x20)) {
//						System.out.println(" - skipped space char " + charId + ": " + charStr);
						continue;
					}
					if ((charStr.length() == 1) && ("\u0091\u008D\u008F\u0090\u009D".indexOf(charStr.charAt(0)) != -1)) {
//						System.out.println(" - skipped control char " + charId + ": " + charStr);
						continue;
					}
//					JUST DON'T TRY TO DISAMBIGUATE THESE
//					if ((charStr.length() == 1) && ('\uE000' <= charStr.charAt(0))  && (charStr.charAt(0) <= '\uF8FF')) {
////						System.out.println(" - skipped UC private use area char " + charId + ": " + charStr);
//						continue;
//					}
					if ((charStr.length() == 1) && (charStr.charAt(0) == 0x00AD))
						charStr = "-"; // replace standalone soft hyphen with dash
					
					boolean isCjkChar;
					if (charStr.length() != 1)
						isCjkChar = false;
					else if (('\u3200' <= charStr.charAt(0)) && (charStr.charAt(0) <= '\u9FFF'))
						isCjkChar = true; // unified Chinese
					else if (('\uAC00' <= charStr.charAt(0)) && (charStr.charAt(0) <= '\uD7AF'))
						isCjkChar = true; // Hangul (Korean)
					else isCjkChar = false;
					boolean isPrivateAreaChar;
					if (charStr.length() != 1)
						isPrivateAreaChar = false;
					else if (('\uE000' <= charStr.charAt(0)) && (charStr.charAt(0) <= '\uF8FF'))
						isPrivateAreaChar = true; // private use area, no global meaning
					else isPrivateAreaChar = false;
					
					String charImageHex64 = charData.getValue(ImFont.CHARACTER_IMAGE_ATTRIBUTE);
//					if (charImageHex64.replace('0', ' ').trim().length() == 0)
//						charImageHex64 = null;
					if (charImageHex64.matches("0+"))
						charImageHex64 = null;
					font.addCharacter(charId, charStr, charImageHex64, 64); // observe PDF fonts are re-decoded to 64 bits high
//					System.out.println(" - added char " + charId + ": " + charStr);
//					if (font.getCharacterCount() == 1)
//						fonts.add(font);
					if (charImageHex64 == null)
						continue;
					BufferedImage charImageRaw = font.getImage(charId);
					BufferedImage charImage64 = trimCharImage(charImageRaw);
					if (charImage64 != charImageRaw) {
						font.addCharacter(charId, charStr, charImage64);
						charImageHex64 = font.getImageHex(charId);
					}
					
					charImageSourceNames.add(docId + "/" + font.name);
					if (isCjkChar)
						cjkSourceNames.add(docId + "/" + font.name);
					else if (isPrivateAreaChar)
						privateAreaSourceNames.add(docId + "/" + font.name);
					else charImageHexToSources.put(charImageHex64, (docId + "/" + font.name));
					
					charsByString64.putChar(charStr, charImageHex64); // TODO somehow track font properties
					charImageSignatures64toSources.put(getCharImageSignature(charImage64), charImageHex64);
					if (charImageHexes64.add(charImageHex64)) {
						/*
Output HEX of char images wider than high right on first occurrence ...
... also including char string
==> should help identify underlying font decoder fuck-ups (if any)
==> could also originate from fonts comprising only dashes (with respectively skewed measurements of glyph extent)
==> also, exclude such images from font reference downstream
						 */
						charImagePixelCounts64.add(countOccupiedPixels(charImageHex64));
						charImageWidths64.add(Integer.valueOf(charImage64.getWidth()));
						if (isOutOfProportion(charImage64)) {
							System.out.println("Got wider-than-high (" + charImage64.getWidth() + "x" + charImage64.getHeight() + ") char image:");
							System.out.println(" - char is " + charStr + ", font is " + docId + "/" + font.name);
							System.out.println(" - HEX is " + charImageHex64);
						}
					}
					if (isOutOfProportion(charImage64))
						outOfProportionSourceNames.add(docId + "/" + font.name);
					charsToImageSignatures64.put(charStr, getCharImageSignature(charImage64));
					
					BufferedImage charImage32 = ImFont.scaleCharImage(charImage64, 32);
					font.addCharacter(charId, charStr, charImage32);
					String charImageHex32 = font.getImageHex(charId);
					if (charImageHex32.matches("0+")) {
						charImageHex64blank32.add(charImageHex64);
						continue;
					}
					if (!isCjkChar && !isPrivateAreaChar)
						charImageHexToSources.put(charImageHex32, (docId + "/" + font.name));
					charImageHex32to64.put(charImageHex32, charImageHex64);
					charsByString32.putChar(charStr, charImageHex32); // TODO somehow track font properties
					charImageSignatures32toSources.put(getCharImageSignature(charImage32), charImageHex32);
					if (charImageHexes32.add(charImageHex32)) {
						charImagePixelCounts32.add(countOccupiedPixels(charImageHex32));
						charImageWidths32.add(Integer.valueOf(charImage32.getWidth()));
						if (isOutOfProportion(charImage32)) {
							System.out.println("Got wider-than-high (" + charImage32.getWidth() + "x" + charImage32.getHeight() + ") char image:");
							System.out.println(" - char is " + charStr + ", font is " + docId + "/" + font.name);
							System.out.println(" - HEX is " + charImageHex32);
						}
					}
					charsToImageSignatures32.put(charStr, getCharImageSignature(charImage32));
					
					BufferedImage charImage16 = ImFont.scaleCharImage(charImage64, 16);
					font.addCharacter(charId, charStr, charImage16);
					String charImageHex16 = font.getImageHex(charId);
					if (charImageHex16.matches("0+")) {
						charImageHex32blank16.add(charImageHex32);
						continue;
					}
					if (!isCjkChar && !isPrivateAreaChar)
						charImageHexToSources.put(charImageHex16, (docId + "/" + font.name));
					charImageHex16to32.put(charImageHex16, charImageHex32);
					charsByString16.putChar(charStr, charImageHex16); // TODO somehow track font properties
					charImageSignatures16toSources.put(getCharImageSignature(charImage16), charImageHex16);
					if (charImageHexes16.add(charImageHex16)) {
						charImagePixelCounts16.add(countOccupiedPixels(charImageHex16));
						charImageWidths16.add(Integer.valueOf(charImage16.getWidth()));
						if (isOutOfProportion(charImage16)) {
							System.out.println("Got wider-than-high (" + charImage16.getWidth() + "x" + charImage16.getHeight() + ") char image:");
							System.out.println(" - char is " + charStr + ", font is " + docId + "/" + font.name);
							System.out.println(" - HEX is " + charImageHex16);
						}
					}
					charsToImageSignatures16.put(charStr, getCharImageSignature(charImage16));
					
					BufferedImage charImage8 = ImFont.scaleCharImage(charImage64, 8);
					font.addCharacter(charId, charStr, charImage8);
					String charImageHex8 = font.getImageHex(charId);
					if (charImageHex8.matches("0+")) {
						charImageHex16blank8.add(charImageHex16);
						continue;
					}
					if (!isCjkChar && !isPrivateAreaChar)
						charImageHexToSources.put(charImageHex8, (docId + "/" + font.name));
					charImageHex8to16.put(charImageHex8, charImageHex16);
					charsByString8.putChar(charStr, charImageHex8); // TODO somehow track font properties
					charImageSignatures8toSources.put(getCharImageSignature(charImage8), charImageHex8);
					if (charImageHexes8.add(charImageHex8)) {
						charImagePixelCounts8.add(countOccupiedPixels(charImageHex8));
						charImageWidths8.add(Integer.valueOf(charImage8.getWidth()));
						if (isOutOfProportion(charImage8)) {
							System.out.println("Got wider-than-high (" + charImage8.getWidth() + "x" + charImage8.getHeight() + ") char image:");
							System.out.println(" - char is " + charStr + ", font is " + docId + "/" + font.name);
							System.out.println(" - HEX is " + charImageHex8);
						}
					}
					charsToImageSignatures8.put(charStr, getCharImageSignature(charImage8));
				}
				if (font != null)
					System.out.println(" ==> got " + font.getCharacterCount() + " chars");
			}
		}, fontFiles.size(), 8);
//		System.out.println("Got " + fonts.size() + " fonts");
		
//		TODOne remove private use area codes (0xE000-0xF8FF) from any glyph transcript list they cause ambiguity in
//		TODOne eliminate optically identical characters from Greek and Cyrillic if Latin counterpart present
//		TODOne eliminate optically identical characters from Cyrillic if Greek counterpart present
		removeIdenticalAndAndPrivateImageChars(charsByString64);
		removeIdenticalAndAndPrivateImageChars(charsByString32);
		removeIdenticalAndAndPrivateImageChars(charsByString16);
		removeIdenticalAndAndPrivateImageChars(charsByString8);
		
		System.out.println("Got glyph images for " + charsByString64.charCount() + " characters:");
		ArrayList chars = charsByString64.strings();
		CountingSet rareCharSources = new CountingSet(new TreeMap());
		for (int c = 0; c < chars.size(); c++) {
			String str = ((String) chars.get(c));
			if (str.length() != 1) {}
			else if (('\u3200' <= str.charAt(0)) && (str.charAt(0) <= '\u9FFF')) {
				System.out.println(" - " + str + " (0x" + Integer.toString(str.charAt(0), 16) + ") skipped as unified CJK");
				continue;
			}
			else if (('\uAC00' <= str.charAt(0)) && (str.charAt(0) <= '\uD7AF')) {
				System.out.println(" - " + str + " (0x" + Integer.toString(str.charAt(0), 16) + ") skipped as Hangul");
				continue;
			}
			else if (('\uE000' <= str.charAt(0)) && (str.charAt(0) <= '\uF8FF')) {
				System.out.println(" - " + str + " (0x" + Integer.toString(str.charAt(0), 16) + ") skipped as privte use area");
				continue;
			}
			
			CharList chl;
			CountingSet chs;
			chl = charsByString64.getChar(str);
			chs = ((CountingSet) charsToImageSignatures64.get(str));
			System.out.println(" - " + str + ((str.length() == 1) ? (" (0x" + Integer.toString(str.charAt(0), 16) + ")") : "") + " with " + chl.size() + " glyph images, " + chl.imageCount() + " distinct ones at 64x64" + ((chs == null) ? "" : (", " + chs.size() + " signatures, " + chs.elementCount() + " distinct ones")));
			if (chl.size() < 3) {
				for (Iterator ihit = chl.imageHex.iterator(); ihit.hasNext();) {
					String imageHex = ((String) ihit.next());
					LinkedHashSet sources = ((LinkedHashSet) charImageHexToSources.get(imageHex));
					if (sources == null)
						continue; // be safe
					if (sources.size() > 2) // avoid flooding log file
						System.out.println("   " + sources.size() + " sources: " + imageHex);
					else for (Iterator sit = sources.iterator(); sit.hasNext();) {
						String source = ((String) sit.next());
						System.out.println("   " + source + ": " + imageHex);
						rareCharSources.add(source);
					}
				}
			}
			chl = charsByString32.getChar(str);
			chs = ((CountingSet) charsToImageSignatures32.get(str));
			if (chl != null) {
				System.out.println(" - " + str + ((str.length() == 1) ? (" (0x" + Integer.toString(str.charAt(0), 16) + ")") : "") + " with " + chl.size() + " glyph images, " + chl.imageCount() + " distinct ones at 32x32" + ((chs == null) ? "" : (", " + chs.size() + " signatures, " + chs.elementCount() + " distinct ones")));
				if (chl.size() < 3) {
					for (Iterator ihit = chl.imageHex.iterator(); ihit.hasNext();) {
						String imageHex = ((String) ihit.next());
						LinkedHashSet sources = ((LinkedHashSet) charImageHexToSources.get(imageHex));
						if (sources == null)
							continue; // be safe
						if (sources.size() > 2) // avoid flooding log file
							System.out.println("   " + sources.size() + " sources: " + imageHex);
						else for (Iterator sit = sources.iterator(); sit.hasNext();) {
							String source = ((String) sit.next());
							System.out.println("   " + source + ": " + imageHex);
							rareCharSources.add(source);
						}
					}
				}
			}
			chl = charsByString16.getChar(str);
			chs = ((CountingSet) charsToImageSignatures16.get(str));
			if (chl != null) {
				System.out.println(" - " + str + ((str.length() == 1) ? (" (0x" + Integer.toString(str.charAt(0), 16) + ")") : "") + " with " + chl.size() + " glyph images, " + chl.imageCount() + " distinct ones at 16x16" + ((chs == null) ? "" : (", " + chs.size() + " signatures, " + chs.elementCount() + " distinct ones")));
				if (chl.size() < 3) {
					for (Iterator ihit = chl.imageHex.iterator(); ihit.hasNext();) {
						String imageHex = ((String) ihit.next());
						LinkedHashSet sources = ((LinkedHashSet) charImageHexToSources.get(imageHex));
						if (sources == null)
							continue; // be safe
						if (sources.size() > 2) // avoid flooding log file
							System.out.println("   " + sources.size() + " sources: " + imageHex);
						else for (Iterator sit = sources.iterator(); sit.hasNext();) {
							String source = ((String) sit.next());
							System.out.println("   " + source + ": " + imageHex);
							rareCharSources.add(source);
						}
					}
				}
			}
			chl = charsByString8.getChar(str);
			chs = ((CountingSet) charsToImageSignatures8.get(str));
			if (chl != null) {
				System.out.println(" - " + str + ((str.length() == 1) ? (" (0x" + Integer.toString(str.charAt(0), 16) + ")") : "") + " with " + chl.size() + " glyph images, " + chl.imageCount() + " distinct ones at 8x8" + ((chs == null) ? "" : (", " + chs.size() + " signatures, " + chs.elementCount() + " distinct ones")));
//				if (chl.size() < 3) {
//					for (Iterator ihit = chl.imageHex.iterator(); ihit.hasNext();) {
//						String imageHex = ((String) ihit.next());
//						LinkedHashSet sources = ((LinkedHashSet) charImageHexToSources.get(imageHex));
//						if (sources == null)
//							continue; // be safe
//						for (Iterator sit = sources.iterator(); sit.hasNext();) {
//							String source = ((String) sit.next());
//							System.out.println("   " + source + ": " + imageHex);
//							rareCharSources.add(source);
//						}
//					}
//				}
			}
//			if (charData instanceof ArrayList) {
//				System.out.println(" - " + charStr + ((charStr.length() == 1) ? (" (0x" + Integer.toString(charStr.charAt(0), 16) + ")") : "") + " with " + ((ArrayList) charData).size() + " glyph images");
//			}
//			else {
//				System.out.println(" - " + charStr + ((charStr.length() == 1) ? (" (0x" + Integer.toString(charStr.charAt(0), 16) + ")") : "") + " with 1 glyph image");
//			}
		}
		System.out.println("Got " + charsByString64.imageCount() + " distinct 64x64 glyph images (set " + charImageHexes64.size() + ")");
		System.out.println("Got " + charImageSignatures64toSources.size() + " distinct 64 bit glyph image signatures");
		printSignatureStats(charImageSignatures64toSources);
		System.out.println("Got " + charImagePixelCounts64.elementCount() + " distinct pixel counts (" + charImagePixelCounts64.size() + " in total):");
		System.out.println("  " + charImagePixelCounts64);
		System.out.println("Got " + charImageWidths64.elementCount() + " distinct widths (" + charImageWidths64.size() + " in total):");
		System.out.println("  " + charImageWidths64);
		
		System.out.println("Got " + charsByString32.imageCount() + " distinct 32x32 glyph images (set " + charImageHexes32.size() + ")");
		printDerivationStats(charsByString32, charImageHex32to64, charImageHex64blank32);
		System.out.println("Got " + charImageSignatures32toSources.size() + " distinct 32 bit glyph image signatures");
		printSignatureStats(charImageSignatures32toSources);
		System.out.println("Got " + charImagePixelCounts32.elementCount() + " distinct pixel counts (" + charImagePixelCounts32.size() + " in total):");
		System.out.println("  " + charImagePixelCounts32);
		System.out.println("Got " + charImageWidths32.elementCount() + " distinct widths (" + charImageWidths32.size() + " in total):");
		System.out.println("  " + charImageWidths32);
		
		System.out.println("Got " + charsByString16.imageCount() + " distinct 16x16 glyph images (set " + charImageHexes16.size() + ")");
		printDerivationStats(charsByString16, charImageHex16to32, charImageHex32blank16);
		System.out.println("Got " + charImageSignatures16toSources.size() + " distinct 16 bit glyph image signatures");
		printSignatureStats(charImageSignatures16toSources);
		System.out.println("Got " + charImagePixelCounts16.elementCount() + " distinct pixel counts (" + charImagePixelCounts16.size() + " in total):");
		System.out.println("  " + charImagePixelCounts16);
		System.out.println("Got " + charImageWidths16.elementCount() + " distinct widths (" + charImageWidths16.size() + " in total):");
		System.out.println("  " + charImageWidths16);
		
		System.out.println("Got " + charsByString8.imageCount() + " distinct 8x8 glyph images (set " + charImageHexes8.size() + ")");
		printDerivationStats(charsByString8, charImageHex8to16, charImageHex16blank8);
		System.out.println("Got " + charImageSignatures8toSources.size() + " distinct 8 bit glyph image signatures");
		printSignatureStats(charImageSignatures8toSources);
		System.out.println("Got " + charImagePixelCounts8.elementCount() + " distinct pixel counts (" + charImagePixelCounts8.size() + " in total):");
		System.out.println("  " + charImagePixelCounts8);
		System.out.println("Got " + charImageWidths8.elementCount() + " distinct widths (" + charImageWidths8.size() + " in total):");
		System.out.println("  " + charImageWidths8);
		
//		System.out.println("Got " + rareCharSources.elementCount() + " sources of rare char images");
		System.out.println("Got " + rareCharSources.elementCount() + " sources of rare char images:");
		for (Iterator sit = rareCharSources.iterator(); sit.hasNext();) {
			String source = ((String) sit.next());
			System.out.println(" - " + source + ": " + rareCharSources.getCount(source) + " rare char images (of " + charImageSourceNames.getCount(source) + " total)");
		}
		System.out.println("Got " + cjkSourceNames.elementCount() + " sources of CJK char images:");
		for (Iterator sit = cjkSourceNames.iterator(); sit.hasNext();) {
			String source = ((String) sit.next());
			System.out.println(" - " + source + ": " + cjkSourceNames.getCount(source) + " CJK char images (of " + charImageSourceNames.getCount(source) + " total)");
		}
		System.out.println("Got " + privateAreaSourceNames.elementCount() + " sources of char images Unicode mapped to private use area:");
		for (Iterator sit = privateAreaSourceNames.iterator(); sit.hasNext();) {
			String source = ((String) sit.next());
			System.out.println(" - " + source + ": " + privateAreaSourceNames.getCount(source) + " char images (of " + charImageSourceNames.getCount(source) + " total)");
		}
		System.out.println("Got " + outOfProportionSourceNames.elementCount() + " sources of out-of-proportion char images:");
		for (Iterator sit = outOfProportionSourceNames.iterator(); sit.hasNext();) {
			String source = ((String) sit.next());
			System.out.println(" - " + source + ": " + outOfProportionSourceNames.getCount(source) + " disproportionate char images (of " + charImageSourceNames.getCount(source) + " total)");
		}
		
//		ArrayList images = charsByString64.images();
//		ArrayList ambiguousImages = new ArrayList();
//		for (int i = 0; i < images.size(); i++) {
//			String imageHex = ((String) images.get(i));
//			CharImage chi = charsByString64.getImage(imageHex);
//			if (chi.strs.elementCount() > 1)
//				ambiguousImages.add(chi);
//			System.out.print(chi.imageHex);
//			System.out.print("\t");
//			for (Iterator sit = chi.strs.iterator(); sit.hasNext();) {
//				String str = ((String) sit.next());
//				System.out.print(str + " " + chi.strs.getCount(str));
//				if (sit.hasNext())
//					System.out.print("\t");
//			}
//			System.out.println();
//		}
		ArrayList ambiguousImages64 = getAmbiguousCharImages(charsByString64);
		System.out.println("Got " + ambiguousImages64.size() + " ambiguous 64x64 glyph images");
		printAmbiguousCharImages(ambiguousImages64, charImageHexToSources, null);
		ArrayList ambiguousImages32 = getAmbiguousCharImages(charsByString32);
		System.out.println("Got " + ambiguousImages32.size() + " ambiguous 32x32 glyph images");
		printAmbiguousCharImages(ambiguousImages32, charImageHexToSources, charImageHex32to64);
		ArrayList ambiguousImages16 = getAmbiguousCharImages(charsByString16);
		System.out.println("Got " + ambiguousImages16.size() + " ambiguous 16x16 glyph images");
		printAmbiguousCharImages(ambiguousImages16, charImageHexToSources, charImageHex16to32);
		ArrayList ambiguousImages8 = getAmbiguousCharImages(charsByString8);
		System.out.println("Got " + ambiguousImages8.size() + " ambiguous 8x8 glyph images");
		printAmbiguousCharImages(ambiguousImages8, charImageHexToSources, charImageHex8to16);
//		Collections.sort(ambiguousImages);
//		for (int i = 0; i < ambiguousImages.size(); i++) {
//			CharImage chi = ((CharImage) ambiguousImages.get(i));
//			System.out.print("IMAGE COLLISION {");
//			for (Iterator sit = chi.strs.iterator(); sit.hasNext();) {
//				String str = ((String) sit.next());
//				System.out.print(str + ((str.length() == 1) ? (" (0x" + Integer.toString(str.charAt(0), 16) + ")") : "") + ": " + chi.strs.getCount(str));
//				if (sit.hasNext())
//					System.out.print(", ");
//			}
//			System.out.println("}");
//			System.out.println("IMAGE: " + chi.imageHex);
////			JOptionPane.showMessageDialog(null, new JLabel(("IMAGE COLLISION " + chi.strs.elementCount()), new ImageIcon(decodeCharacterImage(chi.imageHex)), JLabel.LEFT));
//		}
	}
	
	private static boolean isOutOfProportion(BufferedImage bi) {
//		return (bi.getWidth() > bi.getHeight());
		return ((bi.getWidth() * 2) > (bi.getHeight() * 3));
	}
	
	private static void printCharImages(CharMap charsByString) {
		ArrayList images = charsByString.images();
		for (int i = 0; i < images.size(); i++) {
			String imageHex = ((String) images.get(i));
			CharImage chi = charsByString.getImage(imageHex);
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
	}
	
	private static void removeIdenticalAndAndPrivateImageChars(CharMap charsByString) {
		ArrayList images = charsByString.images();
		for (int i = 0; i < images.size(); i++) {
			String imageHex = ((String) images.get(i));
			CharImage chi = charsByString.getImage(imageHex);
			if (chi.strs.elementCount() < 2)
				continue; // this one's unique
			ArrayList strings = new ArrayList(chi.strs);
			boolean noNonPrivateChar = true;
			boolean noPrivateChars = true;
			for (int s = 0; s < strings.size(); s++) {
				String str = ((String) strings.get(s));
				if (str.length() == 0)
					continue;
				if (greekToLatin.containsKey(str)) {
					String lStr = greekToLatin.getProperty(str);
					if (chi.strs.contains(lStr)) {
						charsByString.replaceString(str, lStr, imageHex);
						strings.remove(s--);
						continue;
					}
				}
				if (cyrillicToLatin.containsKey(str)) {
					String lStr = cyrillicToLatin.getProperty(str);
					if (chi.strs.contains(lStr)) {
						charsByString.replaceString(str, lStr, imageHex);
						strings.remove(s--);
						continue;
					}
				}
				if (cyrillicToGreek.containsKey(str)) {
					String gStr = cyrillicToGreek.getProperty(str);
					if (chi.strs.contains(gStr)) {
						charsByString.replaceString(str, gStr, imageHex);
						strings.remove(s--);
						continue;
					}
				}
				if ((str.length() == 1) && (0xE000 <= str.charAt(0)) && (str.charAt(0) <= 0xF8FF))
					noPrivateChars = false;
				else noNonPrivateChar = false;
			}
			if (noPrivateChars)
				continue; // no private chars to remove
			for (int s = (noNonPrivateChar ? 1 : 0); s < strings.size(); s++) {
				String str = ((String) strings.get(s));
				if (str.length() != 1)
					continue;
				if (noNonPrivateChar || ((str.length() == 1) && (0xE000 <= str.charAt(0)) && (str.charAt(0) <= 0xF8FF))) {
					charsByString.removeString(str, imageHex);
					strings.remove(s--);
				}
			}
		}
	}
	
	private static void printDerivationStats(CharMap charsByString, Map charHexToNextLarger, Set blankToNextLarger) {
		ArrayList images = charsByString.images();
		int count = 0;
		int sum = 0;
		int min = Integer.MAX_VALUE;
		int max = 0;
		for (int i = 0; i < images.size(); i++) {
			String imageHex = ((String) images.get(i));
			Set nextLarger = ((Set) charHexToNextLarger.get(imageHex));
			if (nextLarger == null)
				continue;
			count++;
			sum += nextLarger.size();
			min = Math.min(min, nextLarger.size());
			max = Math.max(max, nextLarger.size());
		}
		if (count == 0)
			return;
		System.out.println("  derived from " + ((sum + (count / 2)) / count) + " larger images on average [" + min + "," + max + "]");
		System.out.println("  blank image from " + blankToNextLarger.size() + " larger images");
	}
	
	private static void printSignatureStats(Map charImageSignaturesToSources) {
		ArrayList signatures = new ArrayList(charImageSignaturesToSources.keySet());
		int count = 0;
		int sum = 0;
		int min = Integer.MAX_VALUE;
		int max = 0;
		for (int s = 0; s < signatures.size(); s++) {
			String signature = ((String) signatures.get(s));
			Set nextLarger = ((Set) charImageSignaturesToSources.get(signature));
			if (nextLarger == null)
				continue;
			count++;
			sum += nextLarger.size();
			min = Math.min(min, nextLarger.size());
			max = Math.max(max, nextLarger.size());
		}
		if (count == 0)
			return;
		System.out.println("  derived from " + ((sum + (count / 2)) / count) + " images on average [" + min + "," + max + "]");
	}
	
	private static ArrayList getAmbiguousCharImages(CharMap charsByString) {
		ArrayList images = charsByString.images();
		ArrayList ambiguousImages = new ArrayList();
		for (int i = 0; i < images.size(); i++) {
			String imageHex = ((String) images.get(i));
			CharImage chi = charsByString.getImage(imageHex);
			if (chi.strs.elementCount() > 1)
				ambiguousImages.add(chi);
		}
		return ambiguousImages;
	}
	
	private static void printAmbiguousCharImages(ArrayList ambiguousImages, Map charHexToSources, Map charHexToNextLarger) {
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
			Set sources = ((Set) charHexToSources.get(chi.imageHex));
			Set nextLarger = ((charHexToNextLarger == null) ? null : ((Set) charHexToNextLarger.get(chi.imageHex)));
			System.out.println("IMAGE: " + chi.imageHex + " (from " + ((sources == null) ? "unknown" : ("" + sources.size())) + " sources" + ((nextLarger == null) ? "" : (", derived from " + nextLarger.size() + " larger images")) + ")");
//			JOptionPane.showMessageDialog(null, new JLabel(("IMAGE COLLISION " + chi.strs.elementCount()), new ImageIcon(decodeCharacterImage(chi.imageHex)), JLabel.LEFT));
		}
	}
	
	/*
Try and cluster char images:
- especially 64 and 32 ones, maybe also 16s
- use row occupation difference as quick filter ...
- ... maybe with tolerance between 1 and 3
  ==> test it out
- use width (number of occupied columns) as quick filter ...
- ... maybe with tolerance between 1 and 3
  ==> test it out
- build graph of char images with pixel difference below threshold ...
- ... and transitively expand clusters
- run thresholds between 1 and 5 (for starters)
- ... and record number of resulting clusters ...
- ... and measure maximum pixel differences within clusters ...
- ... as well as increase in Unicode transcript ambiguity
- try and overlay all images in clusters ...
- ... to facilitate drilling down into ambiguous ones in UI
  ==> should help with chars differing only in diacritic markers ...
  ==> ... while reducing number of images to look at per Unicode character

Store 64 signatures for further analysis:
- include derived 32, 16, and 8 signatures
- include transcripts with frequencies
==> facilitates starting analysis from there ...
==> ... basically cutting out aggregation step ...
==> ... as well as scaling and column reducing char images
==> build respective aggregator script

Count how many 64 signatures reduce to blank at 32, 16, and 8 ...
... and revise below UI strategy accordingly

Re-build clustering UI from scratch:
- use 16 signature where 8 signature blank
- use 32 signature where 16 signature blank
- use 64 signature where 32 signature blank
- load into derivation tree ...
- ... with larger signatures as children of smaller ones ...
- ... and possible transcripts (including frequencies) at each level
- display overlay of 64 signatures for matching smaller signatures ...
- ... indicating number of respective larger signatures in parentheses, separated by slashes
- (recursively) display cluster split by next larger signatures in sub dialog for click
- propagate decisions on parents to children ...
- ... setting parent decisions to "deferred" if decisions on children differ ...
- ... OR BETTER simply stopping propagation where decision already set
- offer "remove" decision (e.g. for CJK char images)
- offer "use current Unicode transcript" decision
- offer "set Unicode transcript to char(s) X, Y, Z" decision (need to accommodate glyphs shared between Latin, Greek, and Cyrillic)
  - also allow 0xXXXX entries (to help with chars unknown to UI font or pain to enter on keyboard)
  - prefill text field with existing transcripts ...
  - ... facilitating deletion of
- encode decisions as bytes (-1 for deferred, 0 for remove, 1 for setting transcript(s) to space separated second property string, and 2 for retaining current transcript)
- highlight char images with ambiguous transcripts with red border or something
- still display grouped by Unicode character
- store 64 signatures decided upon ...
- ... and reload that on starting
  ==> facilitates starting over where left off
- distill reference from that 64 signature result via dedicated scrip

MAYBE: try 2/3 reduction of signature sizes (64, 42, 28, 18, 12, 8) ...
... and see if that increases efficiency
==> put derivation tracking maps in size keyed map in analysis script ...
==> ... and put sizes proper in array
  ==> can also try 3/4 reduction (64, 48, 36, 27, 21, 15, 12, 9)
==> always reduce from 64 signature
  ==> minimizes rounding errors


Sorting out collisions:
- remove minority chars that normalize to or are normal forms of majority chars if frequency difference large enough (factor >10, maybe ???)
  - maybe lower minimum factor to >7 or something for hyphens (think of more)
  - also factor in optical similarity in face of large majority (e.g. "1" vs. capital "I" vs. lower case "L")
  - also factor in letters whose upper and lower case forms are scaled versions of one another (C, O, S, V, W, X, Z)
  - also factor in letters whose upper and lower case forms are shifted versions of one another (P)
- discard minority characters less than 100% as frequent as majority character (safe for optical identity, see below)
- ignore collisions between optically identical characters (Latin vs. Greek vs. Cyrillic script letters) ...
- ... as only way of telling those apart (and assign the correct Unicode point) is by unambiguous context letters
  ==> add mapping of optically identical characters
- be careful about small caps, as they often UC map to lower case counterparts (especially if only colliding characters are upper and lower case form of one another, plus maybe optically identical characters)
  ==> should be OK to discard lower case minority characters, though, as optically lower case glyphs hardly ever map to upper case ones
- be just as careful about equally shaped punctuation marks:
  - any horizontal line at whatever height (i.e., hyphens, dashes, minus, overline, combining macron, underlines)
  - differently sized dots at whatever height (periods, bullet points, black circles, dot above and below accents, etc.)
  - different slashes and different backslashes

Always keep option to display all characters any given glyph co-occurs with in a font (best in font order) to provide basis for inspecting glyph proportions in context (especially for small-caps vs. lower case) as well as baseline position

On aggregation, also count serif, bold, italics, and monospaced properties.

When reading fonts, map smaller glyph signatures to lists of larger ones that reduce to them:
- 32x32 to lists of 64x64
- 16x16 to lists of 32x32
- 8x8 to lists of 16x16
- exact numbers:
  - 225285 distinct 64x64 glyph images, 2852 ambiguous
  - 200894 distinct 32x42 glyph images, 2989 ambiguous
  - 150177 distinct 16x16 glyph images, 3525 ambiguous
  - 58379 distinct 8x8 glyph images, 6517 ambiguous
==> ~52K still unambiguous at 8x8
==> maybe use cascading maps to form signature reduction trees (with index map at each but leaf level)
  ==> need to handle blank-eliminated smaller signatures, though ...
  ==> ... so maybe use empty string as stand-in there

Cut line height in half, thirds, quarters, sixths, and eighths to create respective signatures for clustering:
- refuse match if mismatch in one part and nothing in either adjacent part, either
  ==> way faster comparison than full bitmap
- use thirds only 16x16 and larger, use sixths only 32x32 and larger (to avoid rounding induced mis-matches)
- maybe use majority vote on (linear) line fraction presence signature for each character with multiple glyph signatures as well
  ==> majority of PDF fonts _not_ obfuscated
  ==> should help eliminate overline (and macron, combining or not) as possible character for any kind of dash glyph signature ...
  ==> ... and do the same for underlines ...
  ==> ... as well as any other line height position related mixup
  ==> same should apply for high vs. low quotation marks, single and double alike ...
  ==> ... as well as any accents (combining or not) accidentally happening into the mix

On visualization, inflate 8x8 and 16x16 signatures to 32x32:
- enlarging pixels to 4x4 and 2x2, respectively
  ==> far easier to eyeball
- also, provide mapped list of next larger signatures for click on signature proper
  ==> allows for deferring decision to next higher resolution
    ==> provide respective status value
- also, indicate numbers of mapped larger signatures (maybe in hover text, maybe in parentheses and separated by slashes)

Try and use plain number of black pixels as constant-time filter if comparing with cut-off threshold:
==> facilitates sliding window sort self join of glyph signatures
- first run on glyph signatures that map to same next-smaller representation
- then extrapolate to glyph signatures whose next-smaller representations are close to own
- maybe try cluster centroid approach (as used in OCR word clustering) to reduce number of comparisons
- use percentage difference on sliding window cut-off to increase tolerance as glyphs grow heavier

Run minority character elimination (see previous mail) only on 32x32 and 64x64 signatures ...
... as it's just too indistinct on 16x16 and 8x8 signatures
Remove all but one column of left and right blank pixel columns from char images:
- should speed up comparison
- still indicates where there was a potential rendering cut-off


Create vertical signatures (bit vector indicating which rows have black pixels):
- faster to compare than 2D bitmaps
- little help with actual letters and digits ...
- ... but should help good deal with punctuation marks
==> also do that from reference fonts ...
==> ... and use for sensibility checks in reference generation and transcription filtering
  ==> should help separate overlines and underlines and dashes
  ==> should help distinguish punctuation marks from accents (both combining and non-combining)
==> ultimately, finer grained version of line height regions (see above)

Map combining and non-combining accents together:
- akin to Latin/Greek/Cyrillic letters
- no way of telling them apart anyway in isolation
- should remove good bunch of ambiguous char images

Map small-caps to backing capital letters ...
... and compare the two
==> might yield some kind of line positioning threshold to tell them apart in decoding ...
==> ... and also to distinguish small-caps "O" from lower case "o", etc.
- also include "modifier letters" (U+1D2C-U+1D61) in comparison

For char images with very few total transliterations (<3, maybe, not 3 distinct), also log sources (just found Chinese symbol mapped to "H" diacritic and "ff" ligature one time each)

In sorting UI, sort individual char images for each character by increasing optical similarity to (weighted) cluster centroid
==> most unlikely matches appear at top
==> easier to sort out
==> for second round, re-compute centroid from retained char images

When sorting out char images from character:
- provide option to retain for current character (green)
- provide option to remove from current character, but remain with other characters if latter still available (white)
- provide option to manually enter meaning (yellow) when removing from last character
  - provide possible meanings of next most similar char images in drop-down or as single buttons for quick selection ...
  - ... as well as option to manually enter unicode HEX
- provide option to discard altogether (red)
  - helps get rid of Chinese (and related) symbols at first impression ...
- provide option to discard altogether as mis-rendering (gray)
  ==> should help get rid of megalomaniac renderings (unless we manage to prevent those otherwise and have respective fonts re-extracted)
  ==> maybe allow specifying type of mis-rendering (megalomaniac, complete screw-up, maybe more) ...
  ==> ... and collect sources of respective char images
    ==> helps find example PDFs as test cases for fixing any bugs in font decoder
    ==> allow adding list of document IDs to font extractor to aim at specific PDFs and overwrite respective font files on execution
      ==> allows for iterative refinement of font reference
- click-circle through these options (will be 4 for each char image, as white and yellow are alternatives)
  ==> saves drop-downs, etc.

Facilitate running (and re-running, as we're getting more PDFs) font reference creation incrementally:
- keep file of previously approved char images and their meaning
- keep file of previously discarded char images and the respective reason
- repeat those decisions to reduce interactive effort

Retain baseline as well as ascender and descender (as indicated in font descriptor) with char images;
- store as attributes with font chars (might require extra CSV column) ...
- ... or append to style property in some kind of way
==> facilitates baseline aligned comparison to reference
==> facilitates baseline visualization and thus aided judging in reference creation UI
==> hopefully helps getting rid of megalomaniacs by factoring in descender in fonts with few chars
- also, used "fixed pitch" font descriptor entry as hint for "monospaced" flag ...
- ... and make sure to use remaining flags as indicators of bold and italics (should already be that way)

Maybe add "Interactive OCR" font decoding option to PDF decoder:
- allows font engine to prompt for characters without sufficiently similar match in font reference ...
- ... and maybe even facilitates extending font reference on the fly
- implement as interaction interface injectable to PDF decoder ,,,
  - specify minimum similarity for automated acceptance (maybe 90% by default) ...
  - ... as well as minimum distance to next closest match in same script
  - provide method getting feedback for array of image/unicode pairs ...
  - ... ordered by increasing security of match to have most problematic characters at top
- ... and use that in desktop GGI
  - implement as dialog akin to font editor (if without words, as we don't have those on font decoding) ...
  - ... or even better implement  in font editor and only have GGI _find_ implementation of injection interface amongst plugins
    ==> better separation of concerns ...
    ==> ... even though GGI core still has to now interface ...
    ==> ... but then, it know PDF decoder anyway
- add newly-learned char images to local reference ...
- ... and maybe even send to server via supplement
  ==> be careful about blindly collecting errors server side, though


ALSO: Maintain glyph reference database server side:
- export glyphs from each font to database ...
- ... including transcript
==> at least try this out
	 */
	private static final int whiteRgb = Color.WHITE.getRGB();
	private static BufferedImage trimCharImage(BufferedImage ci) {
		int trimLeft = 0;
		while (trimLeft < ci.getWidth()) {
			boolean trim = true;
			for (int r = 0; r < ci.getHeight(); r++)
				if (ci.getRGB(trimLeft, r) != whiteRgb) {
					trim = false;
					break;
				}
			if (trim)
				trimLeft++;
			else break;
		}
		int trimRight = 0;
		while (trimRight < ci.getWidth()) {
			boolean trim = true;
			for (int r = 0; r < ci.getHeight(); r++)
				if (ci.getRGB((ci.getWidth() - 1 - trimRight), r) != whiteRgb) {
					trim = false;
					break;
				}
			if (trim)
				trimRight++;
			else break;
		}
		if ((trimLeft <= 1) && (trimRight <= 1))
			return ci;
		
		if (trimLeft != 0)
			trimLeft--; // leave one column of white pixels if there are any at all
		if (trimRight != 0)
			trimRight--; // leave one column of white pixels if there are any at all
		BufferedImage tci = new BufferedImage((ci.getWidth() - trimLeft - trimRight), ci.getHeight(), ci.getType());
		for (int c = 0; c < tci.getWidth(); c++) {
			for (int r = 0; r < tci.getHeight(); r++)
				tci.setRGB(c, r, ci.getRGB((c + trimLeft), r));
		}
		return tci;
	}
	
	private static String getCharImageSignature(BufferedImage ci) {
		StringBuffer signature = new StringBuffer();
		for (int r = 0; r < ci.getHeight(); r++) {
			boolean rowGotPixel = false;
			for (int c = 0; c < ci.getWidth(); c++)
			if (ci.getRGB(c, r) != whiteRgb) {
				rowGotPixel = true;
				break;
			}
			signature.append(rowGotPixel ? "X" : " ");
		}
		return signature.toString();
	}
	
	private static Integer countOccupiedPixels(String ciHex) {
		int ops = 0;
		for (int c = 0; c < ciHex.length(); c++) {
			char ch = ciHex.charAt(c);
			if (ch == 'F')
				ops += 4;
			else if ((ch == '7') || (ch == 'B') || (ch == 'D') || (ch == 'E'))
				ops += 3;
			else if ((ch == '3') || (ch == '5') || (ch == '6') || (ch == '9') || (ch == 'A') || (ch == 'C'))
				ops += 2;
			else if ((ch == '1') || (ch == '2') || (ch == '4') || (ch == '8'))
				ops += 1;
			else ops += 0;
		}
		return Integer.valueOf(ops);
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
		synchronized void putChar(String str, String imageHex) {
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
		synchronized void replaceString(String str, String withStr, String forImageHex) {
			CharList chl = ((CharList) this.chars.get(str));
			int imageHexCount = 0;
			if (chl != null) /* should never be null, but let's be safe */ {
				imageHexCount = chl.imageHex.removeAll(forImageHex);
				if (chl.imageHex.isEmpty())
					this.chars.remove(str);
			}
			chl = ((CharList) this.chars.get(withStr));
			if (chl == null) /* should never be null, but let's be safe */ {
				chl = new CharList(str);
				this.chars.put(withStr, chl);
			}
			chl.imageHex.add(forImageHex, imageHexCount);
			
			CharImage chi = ((CharImage) this.charImages.get(forImageHex));
			int strCount = 0;
			if (chi == null) /* should never be null, but let's be safe */ {
				chi = new CharImage(forImageHex);
				this.charImages.put(chi.imageHex, chi);
			}
			else strCount = chi.strs.removeAll(str);
			chi.strs.add(withStr, strCount);
		}
		synchronized void removeString(String str, String forImageHex) {
			CharList chl = ((CharList) this.chars.get(str));
			if (chl != null) /* should never be null, but let's be safe */ {
				chl.imageHex.removeAll(forImageHex);
				if (chl.imageHex.isEmpty())
					this.chars.remove(str);
			}
			CharImage chi = ((CharImage) this.charImages.get(forImageHex));
			if (chi != null) /* should never be null, but let's be safe */ {
				chi.strs.removeAll(str);
				if (chi.strs.isEmpty())
					this.charImages.remove(forImageHex);
			}
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
	
	private static Properties greekToLatin = new Properties();
	static {
		greekToLatin.setProperty("\u0391", "A");
		greekToLatin.setProperty("\u0392", "B");
		greekToLatin.setProperty("\u0395", "E");
		greekToLatin.setProperty("\u0396", "Z");
		greekToLatin.setProperty("\u0397", "H");
		greekToLatin.setProperty("\u0399", "I");
		greekToLatin.setProperty("\u039A", "K");
		greekToLatin.setProperty("\u039C", "M");
		greekToLatin.setProperty("\u039D", "N");
		greekToLatin.setProperty("\u039F", "O");
		greekToLatin.setProperty("\u03A1", "P");
		greekToLatin.setProperty("\u03A4", "T");
		greekToLatin.setProperty("\u03A5", "Y");
		greekToLatin.setProperty("\u03A7", "X");
		
		greekToLatin.setProperty("\u03B9", "\u0131"); // dotless i
		greekToLatin.setProperty("\u03BC", "µ");
		greekToLatin.setProperty("\u03BF", "o");
	}
	
	private static Properties cyrillicToLatin = new Properties();
	static {
		cyrillicToLatin.setProperty("\u0401", "È");
		cyrillicToLatin.setProperty("\u0405", "S");
		cyrillicToLatin.setProperty("\u0406", "I");
		cyrillicToLatin.setProperty("\u0408", "J");
		cyrillicToLatin.setProperty("\u0410", "A");
		cyrillicToLatin.setProperty("\u0412", "B");
		cyrillicToLatin.setProperty("\u0415", "E");
		cyrillicToLatin.setProperty("\u041A", "K");
		cyrillicToLatin.setProperty("\u041C", "M");
		cyrillicToLatin.setProperty("\u041D", "H");
		cyrillicToLatin.setProperty("\u041E", "O");
		cyrillicToLatin.setProperty("\u0420", "P");
		cyrillicToLatin.setProperty("\u0421", "C");
		cyrillicToLatin.setProperty("\u0422", "T");
		cyrillicToLatin.setProperty("\u0425", "X");
		
		cyrillicToLatin.setProperty("\u0430", "a");
		cyrillicToLatin.setProperty("\u0435", "e");
		cyrillicToLatin.setProperty("\u043E", "o");
		cyrillicToLatin.setProperty("\u0440", "p");
		cyrillicToLatin.setProperty("\u0441", "c");
		cyrillicToLatin.setProperty("\u0443", "y");
		cyrillicToLatin.setProperty("\u0445", "x");
		cyrillicToLatin.setProperty("\u0450", "è");
		cyrillicToLatin.setProperty("\u0455", "s");
		cyrillicToLatin.setProperty("\u0456", "i");
		cyrillicToLatin.setProperty("\u0458", "j");
		cyrillicToLatin.setProperty("\u0461", "w");
	}
	
	private static Properties cyrillicToGreek = new Properties();
	static {
		cyrillicToGreek.setProperty("\u0406", "\u0399"); // CYRILLIC CAPITAL LETTER BYELORUSSIAN-UKRAINIAN I to GREEK CAPITAL LETTER IOTA
		cyrillicToGreek.setProperty("\u0407", "\u03AA"); // CYRILLIC CAPITAL LETTER YI to GREEK CAPITAL LETTER IOTA WITH DIALYTIKA
		cyrillicToGreek.setProperty("\u0410", "\u0391"); // CYRILLIC CAPITAL LETTER A to GREEK CAPITAL LETTER ALPHA
		cyrillicToGreek.setProperty("\u0412", "\u0392"); // CYRILLIC CAPITAL LETTER VE to GREEK CAPITAL LETTER BETA
		cyrillicToGreek.setProperty("\u0413", "\u0393"); // CYRILLIC CAPITAL LETTER GHE to GREEK CAPITAL LETTER GAMMA
		cyrillicToGreek.setProperty("\u0415", "\u0395"); // CYRILLIC CAPITAL LETTER IE to GREEK CAPITAL LETTER EPSILON
		cyrillicToGreek.setProperty("\u041A", "\u039A"); // CYRILLIC CAPITAL LETTER KA to GREEK CAPITAL LETTER KAPPA
		cyrillicToGreek.setProperty("\u041C", "\u039C"); // CYRILLIC CAPITAL LETTER EM to GREEK CAPITAL LETTER MU
		cyrillicToGreek.setProperty("\u041D", "\u0397"); // CYRILLIC CAPITAL LETTER EN to GREEK CAPITAL LETTER ETA
		cyrillicToGreek.setProperty("\u041E", "\u039F"); // CYRILLIC CAPITAL LETTER O to GREEK CAPITAL LETTER OMICRON
		cyrillicToGreek.setProperty("\u041F", "\u03A0"); // CYRILLIC CAPITAL LETTER PE to GREEK CAPITAL LETTER PI
		cyrillicToGreek.setProperty("\u0420", "\u03A1"); // CYRILLIC CAPITAL LETTER ER to GREEK CAPITAL LETTER RHO
		cyrillicToGreek.setProperty("\u0422", "\u03A4"); // CYRILLIC CAPITAL LETTER TE to GREEK CAPITAL LETTER TAU
		cyrillicToGreek.setProperty("\u0424", "\u03A6"); // CYRILLIC CAPITAL LETTER EF to GREEK CAPITAL LETTER PHI
		cyrillicToGreek.setProperty("\u0425", "\u03A7"); // CYRILLIC CAPITAL LETTER HA to GREEK CAPITAL LETTER CHI
		
		cyrillicToGreek.setProperty("\u043A", "\u03BA"); // CYRILLIC SMALL LETTER KA to GREEK SMALL LETTER KAPPA
		cyrillicToGreek.setProperty("\u043E", "\u03BF"); // CYRILLIC SMALL LETTER O to GREEK SMALL LETTER OMICRON
		cyrillicToGreek.setProperty("\u043F", "\u03C0"); // CYRILLIC SMALL LETTER PE to GREEK SMALL LETTER PI
		cyrillicToGreek.setProperty("\u0442", "\u03C4"); // CYRILLIC SMALL LETTER TE to GREEK SMALL LETTER TAU
	}
}
