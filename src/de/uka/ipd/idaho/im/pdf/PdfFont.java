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

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;

import org.icepdf.core.pobjects.Name;

import de.uka.ipd.idaho.gamta.util.CountingSet;
import de.uka.ipd.idaho.gamta.util.ProgressMonitor;
import de.uka.ipd.idaho.im.pdf.PdfFontDecoder.FontDecoderCharset;
import de.uka.ipd.idaho.im.pdf.PdfParser.PStream;
import de.uka.ipd.idaho.im.pdf.PdfParser.PString;
import de.uka.ipd.idaho.im.pdf.PdfParser.PTag;
import de.uka.ipd.idaho.im.pdf.PdfUtils.PdfByteInputStream;
import de.uka.ipd.idaho.stringUtils.StringUtils;

/**
 * A single font extracted from a PDF. Class methods for font parsing.
 * 
 * @author sautter
 */
public class PdfFont {
	
	private static final boolean DEBUG_CHAR_HANDLING = false;
	
	static class BaseFont {
//		Hashtable descriptor;
		Map descriptor;
		int firstChar;
		int lastChar;
		float[] charWidths;
		float mCharWidth;
		boolean bold;
		boolean italic;
		boolean serif;
		String encoding;
		HashMap charMappings = new HashMap(1);
		HashMap nCharWidths = new HashMap(1);
//		BaseFont(Hashtable data, Hashtable descriptor, float mCharWidth, String name, String encoding) {
		BaseFont(Map data, Map descriptor, float mCharWidth, String name, String encoding) {
			this.descriptor = descriptor;
			this.mCharWidth = mCharWidth;
			this.bold = (name.indexOf("Bold") != -1);
			this.italic = ((name.indexOf("Italic") != -1) || (name.indexOf("Ital") != -1) || (name.indexOf("Oblique") != -1));
			this.serif = name.startsWith("Times");
			this.encoding = encoding;
		}
		char resolveByte(int chb) {
			if (DEBUG_CHAR_HANDLING || (127 < chb))
				System.out.println("   - BF resolving '" + ((char) chb) + "' (" + chb + ")");				
			Integer chi = new Integer(chb);
			Character mCh = ((Character) this.charMappings.get(chi));
			if (mCh != null) {
				if (DEBUG_CHAR_HANDLING || (127 < chb))
					System.out.println("   --> mapping resolved to '" + mCh.charValue() + "' (" + ((int) mCh.charValue()) + ")");				
				return mCh.charValue();
			}
			if ("WinAnsiEncoding".equals(this.encoding))
				mCh = ((Character) PdfFont.winAnsiMappings.get(chi));
			else if ("MacRomanEncoding".equals(this.encoding))
				mCh = ((Character) PdfFont.macRomanMappings.get(chi));
			else if ("MacExpertEncoding".equals(this.encoding))
				mCh = ((Character) PdfFont.macExpertMappings.get(chi));
			else if ("AdobeStandardEncoding".equals(this.encoding) || "StandardEncoding".equals(this.encoding))
				mCh = ((Character) PdfFont.standardAdobeMappings.get(chi));
			if ((DEBUG_CHAR_HANDLING || (127 < chb)) && (mCh != null))
				System.out.println("   --> " + this.encoding + " resolved to '" + mCh.charValue() + "' (" + ((int) mCh.charValue()) + ")");				
			return ((mCh == null) ? 0 : mCh.charValue());
		}
		float getCharWidth(char ch) {
			if (DEBUG_CHAR_HANDLING || (127 < ch)) System.out.println(" - getting width for '" + ch + "' (" + ((int) ch) + ")");
			float cw = 0;
			if (cw == 0) {
				Float cwObj = ((Float) this.nCharWidths.get(new Character(ch)));
				if (cwObj != null) {
					cw = cwObj.floatValue();
					if (DEBUG_CHAR_HANDLING || (127 < ch)) System.out.println("   - got char width from map: " + cw);
				}
			}
			if ((cw == 0) && (this.firstChar <= ch) && (ch <= this.lastChar)) {
				cw = this.charWidths[((int) ch) - this.firstChar];
				if (DEBUG_CHAR_HANDLING || (127 < ch)) System.out.println("   - got char width from array: " + cw);
			}
			if (cw == 0) {
				cw = this.mCharWidth;
				if (DEBUG_CHAR_HANDLING || (127 < ch)) System.out.println("   - falling back to missing width: " + cw);
			}
			return cw;
		}
		void setCharWidths(int fc, int lc, float[] cws) {
			this.firstChar = fc;
			this.lastChar = lc;
			this.charWidths = cws;
		}
		void setNamedCharWidth(char ch, float cw) {
			this.nCharWidths.put(new Character(ch), new Float(cw));
		}
	}
	
	Map data;
	Map descriptor;
	
	int firstChar;
	int lastChar;
	float[] charWidths;
	float mCharWidth;
	
	String name;
	String encoding;
//	int type = 1;
	String type = null;
	
	boolean bold;
	boolean italics;
	boolean serif = true;
	boolean monospaced;
	float ascent = 0;
	float descent = 0;
	float xHeight = 0;
	float capHeight = 0;
	boolean hasDescent = true;
	
	boolean hasImplicitSpaces = false;
	HashMap mCcWidths = null;
	
	HashMap ucMappings = new HashMap(1);
	HashSet fontUcMapped = new HashSet(1);
	HashMap diffMappings = new HashMap(1);
	HashMap diffMappingsReverse = new HashMap(1);
	HashMap diffNameMappings = new HashMap(1);
	HashMap diffNameMappingsReverse = new HashMap(1);
	HashSet subSetFilter = null;
	BaseFont baseFont = null;
	boolean isBuiltInFont = false;
	HashMap ccWidths = new HashMap(1);
	HashMap cWidths = new HashMap(1);
	HashMap charBaselineShifts = new HashMap(2);
	HashMap charImages = new HashMap(2);
	HashMap charNameImages = new HashMap(2);
	
	TreeMap usedCharStats = new TreeMap(); // stats about char usages (predecessors, successors) to help resolve glyph decoding ambiguities
				// when decoding glyphs, only go for chars in this map, as all others are spurious and never used in document
	TreeSet usedCharNames = new TreeSet(); // names of chars in above set, as those might also come from Differences mapping in SID fonts
	HashMap charCodeSynonyms = new HashMap(2);
	CharDecoder charDecoder = null;
	
	private HashMap charCodeSubstitutes = new HashMap();
	
	PdfFont(Map data, Map descriptor, int firstChar, int lastChar, float[] charWidths, float mCharWidth, String name, String encoding) {
		this.data = data;
		this.descriptor = descriptor;
		this.firstChar = firstChar;
		this.lastChar = lastChar;
		this.charWidths = charWidths;
		this.mCharWidth = mCharWidth;
		this.name = name;
		this.bold = (name.indexOf("Bold") != -1);
		this.italics = ((name.indexOf("Italic") != -1) || (name.indexOf("Oblique") != -1));
		this.encoding = encoding;
//		if (this.charWidths != null) {
//			//	TODOne TEST font NJKPLG+TT20490o00 in zt03790p356.pdf.new.imdir (serif, not monospaced, but renders only page number, using digits 0-9, and digits seem to be equal-width in many fonts ...)
//			//	TODOne TEST font CPJNOD+AdvP427B70 in AMNH_Bulletin.355.pdf (actually monospaced)
//			HashSet nonZeroCharWidths = new HashSet();
//			int nonZeroCharWidthCount = 0;
//			for (int c = 0; c < this.charWidths.length; c++) {
//				if (this.charWidths[c] == 0)
//					continue;
//				this.setCharWidth(new Integer((int) (this.firstChar + c)), this.charWidths[c]);
//				this.setCharWidth(new Character((char) (this.firstChar + c)), this.charWidths[c]);
//				nonZeroCharWidths.add(new Float(this.charWidths[c]));
//				nonZeroCharWidthCount++;
//			}
//			if ((nonZeroCharWidths.size() == 1) && (nonZeroCharWidthCount > 5))
//				this.monospaced = true;
//		}
		if (DEBUG_LOAD_FONTS)
			System.out.println("Font " + this.name + " (" + this + ") created");
	}
	void setBaseFont(String baseFontName, boolean isSymbolic) {
		this.setBaseFont(getBuiltInFont(baseFontName, isSymbolic));
	}
	void setBaseFont(BaseFont baseFont) {
		this.baseFont = baseFont;
		if ((this.encoding != null) && !this.encoding.equals(this.baseFont.encoding)) {
			if (DEBUG_LOAD_FONTS)
				System.out.println(" - base font encoding corrected to " + this.encoding);
			this.baseFont.encoding = this.encoding;
		}
		//	TODO figure out if encoding correction makes sense or causes errors
	}
	
	void setIsBuiltInFont() {
		this.isBuiltInFont = true;
	}
	
	boolean usesCharCode(Integer cc) {
		return this.usedCharStats.containsKey(cc);
	}
	int[] getUsedCharCodes() {
//		int[] charCodes = new int[this.usedChars.size()];
//		int cci = 0;
//		for (Iterator ccit = this.usedChars.iterator(); ccit.hasNext();)
//			charCodes[cci++] = ((Integer) ccit.next()).intValue();
//		return charCodes;
		int[] charCodes = new int[this.usedCharStats.size()];
		int cci = 0;
		for (Iterator ccit = this.usedCharStats.keySet().iterator(); ccit.hasNext();)
			charCodes[cci++] = ((Integer) ccit.next()).intValue();
		return charCodes;
	}
	
	private int wordCount = 0;
	private int wordLength = 0;
	private int charCount = 0;
	private int prevUsedChb = -1;
	void startWord() {
		this.prevUsedChb = -1;
//		System.out.println("Font " + this.name + ": word started");
	}
	void endWord() {
		this.setCharUsed(-1);
		this.wordCount++;
		this.charCount += this.wordLength;
		this.wordLength = 0;
//		System.out.println("Font " + this.name + ": word ended");
	}
	synchronized void setCharUsed(int chb) {
		if (chb != -1)
			this.wordLength++;
		
		Integer chbObj = new Integer(chb);
		Object chnObj = null;
		String chn = null;
		CharUsageStats chbStats = ((CharUsageStats) this.usedCharStats.get(chbObj));
		if ((chb != -1) && (chbStats == null)) {
			chnObj = this.diffNameMappings.get(chbObj);
			if (chnObj != null)
				this.usedCharNames.add(chnObj);
			else {
				String uch = this.getUnicode(chb);
				if (uch.length() != 0) {
					chn = StringUtils.getCharName(uch.charAt(0));
					if (chn != null)
						this.usedCharNames.add(chn);
				}
			}
			if (DEBUG_LOAD_FONTS)
				System.out.println("Font " + this.name + " (" + this + "): using char " + chb + " / " + ((chnObj == null) ? chn : chnObj));
			chbStats = new CharUsageStats(chb);
			this.usedCharStats.put(chbObj, chbStats);
			if (DEBUG_LOAD_FONTS)
				System.out.println(" ==> using " + this.usedCharStats.size() + " chars in total: " + this.usedCharStats.keySet());
		}
		
		if (this.charDecoder != null) {
			this.charDecoder.usedCharCodes.add(chbObj);
			if (chnObj != null)
				this.charDecoder.usedCharNames.add(chnObj);
			else if (chn != null)
				this.charDecoder.usedCharNames.add(chn);
		}
		
		Integer prevChbObj = new Integer(this.prevUsedChb);
		CharUsageStats prevChbStats = ((CharUsageStats) this.usedCharStats.get(prevChbObj));
		
		if (chbStats != null) {
			chbStats.usageCount++;
			if (this.prevUsedChb != -1)
				chbStats.predecessors.add(prevChbObj);
		}
		if ((prevChbStats != null) && (chb != -1))
			prevChbStats.successors.add(chbObj);
		
//		System.out.println("Font " + this.name + ": using char " + chb + " after " + this.prevUsedChb);
//		
		this.prevUsedChb = chb;
	}
	synchronized void setCharsNeighbored(int fChb, PdfFont sChbFont, int sChb) {
		fChb = this.getValidCharCode(new Integer(fChb)).intValue();
		sChb = this.getValidCharCode(new Integer(sChb)).intValue();
		
		Integer fChbObj = new Integer(fChb);
		CharUsageStats fChbStats = ((CharUsageStats) this.usedCharStats.get(fChbObj));
		if ((fChb != -1) && (fChbStats == null)) {
			fChbStats = new CharUsageStats(fChb);
			this.usedCharStats.put(fChbObj, fChbStats);
		}
		
		Integer sChbObj = new Integer(sChb);
		CharUsageStats sChbStats = ((CharUsageStats) sChbFont.usedCharStats.get(sChbObj));
		if ((sChb != -1) && (sChbStats == null)) {
			sChbStats = new CharUsageStats(sChb);
			sChbFont.usedCharStats.put(sChbObj, sChbStats);
		}
		
		fChbStats.successorChars.add(new CharNeighbor(sChb, sChbFont));
		sChbStats.predecessorChars.add(new CharNeighbor(fChb, this));
//		System.out.println("Font " + this.name + ": using char " + fChb + " before " + sChb + " in font " + sChbFont.name);
	}
	void setCharCodeSynonym(Integer synCh, Integer withCh) {
		if (withCh.intValue() == 32) // cannot synonymize with space, as char spacing vs. word spacing depends upon it
			System.out.println("Cannot synonymize " + synCh + " with space " + withCh);
		else if (synCh.intValue() == 32) // cannot synonymize space, as char spacing vs. word spacing depends upon it
			System.out.println("Cannot synonymize space " + synCh + " with " + withCh);
		else if (this.usedCharStats.containsKey(withCh)) /* cannot synonymization with unused character code */ {
			String synChUc = this.getUnicode(synCh.intValue());
			String synChName = (((synChUc == null) || (synChUc.length() != 1)) ? null : StringUtils.getCharName(synChUc.charAt(0)));
			String withChUc = this.getUnicode(withCh.intValue());
			String withChName = (((withChUc == null) || (withChUc.length() != 1)) ? null : StringUtils.getCharName(withChUc.charAt(0)));
			if ((synChUc != null) && (withChUc != null) && !synChUc.equals(withChUc))
				System.out.println("Cannot synonymize " + synCh + "(" + synChUc + "/" + synChName + ") with " + withCh + " (" + withChUc + "/" + withChName + ") for different Unicode mappings");
			else {
				System.out.println("Synonymized " + synCh + "(" + synChUc + "/" + synChName + ") with " + withCh + " (" + withChUc + "/" + withChName + ")");
				this.charCodeSynonyms.put(synCh, withCh);
			}
		}
		else System.out.println("Cannot synonymize " + synCh + " with unused char " + withCh);
	}
	Integer getValidCharCode(Integer chc) {
		//	handle transitivity even though it cannot only emerge from rendering sequence equality ...
		//	... because we might end up adding some bitmap based synonymization
		//	however, as the lower index will always become the lead, we don't have to worry about cycles
		while (this.charCodeSynonyms.containsKey(chc)) {
			Integer sChc = ((Integer) this.charCodeSynonyms.get(chc));
			if (chc.equals(sChc))
				return chc;
			else chc = sChc;
		}
		return chc;
	}
	CharUsageStats getCharUsageStats(Integer cc) {
		cc = this.getValidCharCode(cc);
		return ((CharUsageStats) this.usedCharStats.get(cc));
	}
	static class CharUsageStats {
		final int charByte;
		final CountingSet predecessors = new CountingSet(new HashMap());
		final CountingSet successors = new CountingSet(new HashMap());
		int usageCount = 0;
		final CountingSet predecessorChars = new CountingSet(new HashMap());
		final CountingSet successorChars = new CountingSet(new HashMap());
		CharUsageStats(int charByte) {
			this.charByte = charByte;
		}
	}
	static class CharNeighbor {
		final int charByte;
		final PdfFont font;
		CharNeighbor(int charByte, PdfFont font) {
			this.charByte = charByte;
			this.font = font;
		}
		public int hashCode() {
			return (this.font.name.hashCode() & this.charByte);
		}
		public boolean equals(Object obj) {
			return ((obj instanceof CharNeighbor) && this.equals(((CharNeighbor) obj).charByte, ((CharNeighbor) obj).font));
		}
		public boolean equals(int chb, PdfFont font) {
			return ((this.charByte == chb) && (this.font == font));
		}
		public String toString() {
			return (this.charByte + "@" + this.font.name + (this.font.isDecoded() ? (" (" + this.font.getUnicode(this.charByte) + ")") : ""));
		}
		char getChar() {
			String uc = (this.font.isDecoded() ? this.font.getUnicode(this.charByte) : null);
			return ((uc == null) ? ((char) 0) : uc.charAt(0));
		}
	}
	
	boolean isDecoded() {
		return (this.charDecoder == null);
	}
	
	int getCharUsageCount() {
		return this.charCount;
	}
	
	float getAverageWordLength() {
		return ((this.wordCount == 0) ? 0 : (((float) this.charCount) / this.wordCount));
	}
	
	synchronized int getCharCode(int chc) {
		chc = this.getValidCharCode(new Integer(chc)).intValue();
		if (chc < 256)
			return chc;
		Integer chcObj = new Integer(chc);
		Integer sChcObj = ((Integer) this.charCodeSubstitutes.get(chcObj));
		if (sChcObj == null) {
			for (int c = 1; c < 255; c++) {
				Integer tsChcObj = new Integer(c);
				if (this.usedCharStats.containsKey(tsChcObj))
					continue;
				if (this.charCodeSubstitutes.containsKey(tsChcObj))
					continue;
				sChcObj = tsChcObj;
				this.charCodeSubstitutes.put(chcObj, sChcObj); // hold on to substitution mapping
				this.charCodeSubstitutes.put(sChcObj, sChcObj); // mark substitute char code as used
				if (DEBUG_LOAD_FONTS)
					System.out.println("Font " + this.name + ": substituting char code " + chc + " with " + c);
				break;
			}
			if (sChcObj == null)
				throw new RuntimeException("No substitutes available for > 255 char code " + chc);
		}
		return sChcObj.intValue();
	}
	
	private void setCharDecoder(CharDecoder charDecoder) {
		if (this.charDecoder != null)
			System.out.println("Font " + this.name + ": having replaced char decoder " + this.charDecoder);
		this.charDecoder = charDecoder;
		System.out.println("Font " + this.name + ": char decoder set to " + this.charDecoder);
	}
	void decodeChars(ProgressMonitor pm, FontDecoderCharset charSet) throws IOException {
		System.out.println("Font " + this.name + " (" + this + "): decoding chars");
		if (this.charDecoder == null) {
			System.out.println(" ==> decoder not found");
			return;
		}
		synchronized (this) {
			if (this.charDecoder != null) { // multiple threads might get past above shortcut, so we have to re-check here
				System.out.println(" ==> decoder found");
				this.charDecoder.decodeChars(this, charSet, pm);
			}
			this.charDecoder = null; // make all threads wait on synchronized block until decoding process finished (font is not fully usable before that)
		}
	}
	static abstract class CharDecoder {
		final HashSet usedCharCodes = new HashSet();
		final HashSet usedCharNames = new HashSet();
		boolean usesChar(Integer chc, String chn) {
			if (this.usedCharCodes.contains(chc))
				return true;
			if ((chn != null) && this.usedCharNames.contains(chn))
				return true;
			return false;
		}
		final HashMap plainCharCodesToImages = new HashMap();
		final HashMap plainCharCodesToWidths = new HashMap();
		final HashMap plainCharCodesToUnicode = new HashMap();
		abstract void decodeChars(PdfFont font, FontDecoderCharset charSet, ProgressMonitor pm) throws IOException;
	}
	
	String getUnicode(int chb) {
		chb = this.getValidCharCode(new Integer(chb)).intValue();
		
		if (DEBUG_CHAR_HANDLING || (127 < chb)) System.out.println("UC-resolving " + chb + " ...");
		if (chb < 0) {
			chb += 256;
			if (DEBUG_CHAR_HANDLING || (127 < chb)) System.out.println(" - incremented to " + ((int) chb));
		}
		
		//	try direct Unicode mapping 
		String ucStr = ((String) this.ucMappings.get(new Integer(chb)));
		if (ucStr != null) {
			if (DEBUG_CHAR_HANDLING || (127 < chb)) System.out.println(" --> UC mapping resolved to " + ucStr);
			return ucStr;
		}
		
		//	use font mapping
		return ("" + this.getChar(chb));
	}
	char getChar(int chb) {
		chb = this.getValidCharCode(new Integer(chb)).intValue();
		
		if (DEBUG_CHAR_HANDLING || (127 < chb)) System.out.println("Font-resolving " + chb + " ...");
		if (chb < 0) {
			chb += 256;
			if (DEBUG_CHAR_HANDLING || (127 < chb)) System.out.println(" - incremented to " + ((int) chb));
		}
		Integer chi = new Integer(chb);
		
		/* We need to cut this out here, as we need access to the char indicated
		 * by the font to decide if word spacing or not. That is independent of
		 * the glyph actually rendered in the process. The latter only comes in
		 * on string generation */
//		//	try direct Unicode mapping 
//		String mStr = ((String) this.ucMappings.get(chi));
//		if (mStr != null) {
//			if (DEBUG_CHAR_HANDLING || (127 < chb)) System.out.println(" --> UC resolved to " + mStr);
//			return mStr;
//		}
		
		//	try differences mapping
		Character mCh = ((Character) this.diffMappings.get(chi));
		if (mCh != null) {
			if (DEBUG_CHAR_HANDLING || (127 < chb)) System.out.println(" --> Diff resolved to " + mCh);
//			return ("" + mCh.charValue());
			return mCh.charValue();
		}
		
		//	check base font if char in range
		if ((this.baseFont != null) && ((chb < this.firstChar) || (this.lastChar < chb))) {
			char ch = this.baseFont.resolveByte(chb);
			if (ch != 0) {
				if (DEBUG_CHAR_HANDLING || (127 < chb)) System.out.println(" --> base font out of own range resolved to " + ch);
//				return ("" + ch);
				return ch;
			}
		}
		
		//	try default encodings (only bytes less than 256)
		mCh = getChar(chi, this.encoding, DEBUG_CHAR_HANDLING || (127 < chb));
		if (mCh != null)
			return mCh.charValue();
		else if ((this.firstChar <= chb) && (chb <= this.lastChar)) {
			if (DEBUG_CHAR_HANDLING || (127 < chb)) System.out.println(" --> Default resolved to " + ((char) chb));
//			return ("" + ((char) chb));
			return ((char) chb);
		}
//		if ("AdobeStandardEncoding".equals(this.encoding)) {
//			mCh = ((Character) standardAdobeMappings.get(chi));
//			if (mCh != null) {
//				if (DEBUG_CHAR_HANDLING || (127 < chb)) System.out.println(" --> Adobe resolved to " + mCh.charValue());
////				return ("" + mCh.charValue());
//				return mCh.charValue();
//			}
//			else if ((this.firstChar <= chb) && (chb <= this.lastChar)) {
//				if (DEBUG_CHAR_HANDLING || (127 < chb)) System.out.println(" --> Adobe default resolved to " + ((char) chb));
////				return ("" + ((char) chb));
//				return ((char) chb);
//			}
//		}
//		else if ("MacRomanEncoding".equals(this.encoding)) {
//			mCh = ((Character) macRomanMappings.get(chi));
//			if (mCh != null) {
//				if (DEBUG_CHAR_HANDLING || (127 < chb)) System.out.println(" --> MacRoman resolved to " + mCh.charValue());
////				return ("" + mCh.charValue());
//				return mCh.charValue();
//			}
//			else if ((this.firstChar <= chb) && (chb <= this.lastChar)) {
//				if (DEBUG_CHAR_HANDLING || (127 < chb)) System.out.println(" --> MacRoman default resolved to " + ((char) chb));
////				return ("" + ((char) chb));
//				return ((char) chb);
//			}
//		}
//		else if ("MacExpertEncoding".equals(this.encoding)) {
//			mCh = ((Character) macExpertMappings.get(chi));
//			if (mCh != null) {
//				if (DEBUG_CHAR_HANDLING || (127 < chb)) System.out.println(" --> MacExpert resolved to " + mCh.charValue());
////				return ("" + mCh.charValue());
//				return mCh.charValue();
//			}
//			else if ((this.firstChar <= chb) && (chb <= this.lastChar)) {
//				if (DEBUG_CHAR_HANDLING || (127 < chb)) System.out.println(" --> MacExpert default resolved to " + ((char) chb));
////				return ("" + ((char) chb));
//				return ((char) chb);
//			}
//		}
//		else if ("WinAnsiEncoding".equals(this.encoding) || (this.encoding == null)) {
//			mCh = ((Character) winAnsiMappings.get(chi));
//			if (mCh != null) {
//				if (DEBUG_CHAR_HANDLING || (127 < chb)) System.out.println(" --> ANSI resolved to " + mCh.charValue());
////				return ("" + mCh.charValue());
//				return mCh.charValue();
//			}
//			else if ((this.firstChar <= chb) && (chb <= this.lastChar)) {
//				if (DEBUG_CHAR_HANDLING || (127 < chb)) System.out.println(" --> ANSI default resolved to " + ((char) chb));
////				return ("" + ((char) chb));
//				return ((char) chb);
//			}
//		}
		
		//	try base font regardless of range
		if (this.baseFont != null) {
			char ch = this.baseFont.resolveByte(chb);
			if (ch != 0) {
				if (DEBUG_CHAR_HANDLING || (127 < chb)) System.out.println(" --> base font resolved to " + ch);
//				return ("" + ch);
				return ch;
			}
		}
		
		//	resort to ASCII
		if (DEBUG_CHAR_HANDLING || (127 < chb)) System.out.println(" --> ASCII resolved to " + ((char) chb));
//		return ("" + ((char) chb));
		return ((char) chb);
	}
	int getCharCode(String chn) {
		Integer chc = ((Integer) this.diffNameMappingsReverse.get(chn));
		if (chc != null)
			return chc.intValue();
		char ch = StringUtils.getCharForName(chn);
		if (ch == 0)
			return 0;
		chc = getCharCode(ch, this.encoding, DEBUG_CHAR_HANDLING);
		return ((chc == null) ? 0 : chc.intValue());
	}
	synchronized void mapDifference(Integer ch, Character mCh, String mChName) {
		if (mCh != null) {
			this.diffMappings.put(ch, mCh);
			this.diffMappingsReverse.put(mCh, ch);
		}
		if (mChName != null) {
			this.diffNameMappings.put(ch, mChName);
			this.diffNameMappingsReverse.put(mChName, ch);
		}
		if (DEBUG_CHAR_HANDLING)
			System.out.println("Diff-Mapped " + ch + " to '" + mCh + "'");
	}
	synchronized void mapUnicode(Integer ch, String str, boolean isFontUcMapping) {
		String nStr = str;
		
		//	mapped string is dissolved ligature (which usually happen to correspond to PostScript names for those very ligatures)
		if ((nStr.length() > 1) && (StringUtils.getCharForName(nStr) != 0))
			nStr = ("" + StringUtils.getCharForName(nStr));
		
		//	mapped string starts with combining accent
		if ((nStr.length() > 1) && PdfCharDecoder.isCombiningAccent(nStr.charAt(0))) {
			char cmbCh = PdfCharDecoder.getCombinedChar(nStr.charAt(1), nStr.charAt(0));
			if (cmbCh != 0)
				nStr = (cmbCh + nStr.substring(2));
		}
		
		//	mapped string ends with combining accent
		if ((nStr.length() > 1) && PdfCharDecoder.isCombiningAccent(nStr.charAt(nStr.length()-1))) {
			char cmbCh = PdfCharDecoder.getCombinedChar(nStr.charAt(nStr.length()-2), nStr.charAt(nStr.length()-1));
			if (cmbCh != 0)
				nStr = (nStr.substring(0, (nStr.length() - 2)) + cmbCh);
		}
		
		//	revert deviations from Unicode (especially default encodings)
		if ((ch.intValue() > 255) && !this.usesCharCode(ch)) {
			Integer mCh = getCharCode(new Character((char) ch.intValue()), this.encoding, DEBUG_CHAR_HANDLING);
			if (DEBUG_LOAD_FONTS) System.out.println(" ==> resolved unused char code " + ch + " to " + mCh);
			if ((mCh != null) && this.usesCharCode(new Integer(mCh))) {
				if (DEBUG_LOAD_FONTS) System.out.println(" ==> replaced unused char code " + ch + " with used char code " + mCh);
				ch = mCh;
			}
		}
		
		//	store mapping
		this.ucMappings.put(ch, nStr);
		
		//	remember mapping comes from font proper (need this for char width lookups)
		if (isFontUcMapping)
			this.fontUcMapped.add(ch);
		
		//	be verbose if required
		if (DEBUG_CHAR_HANDLING) {
			System.out.print(this.name + ": UC-Mapped " + ch + " to '" + str + "' (");
			for (int c = 0; c < str.length(); c++)
				 System.out.print(((c == 0) ? "" : " ") + ((int) str.charAt(c)));
			for (int c = 0; c < str.length(); c++)
				 System.out.print(((c == 0) ? ", " : " ") + Integer.toString(((int) str.charAt(c)), 16));
			System.out.println(")");
			if (nStr != str) {
				System.out.print("  normalized to '" + nStr + "' (");
				for (int c = 0; c < nStr.length(); c++)
					 System.out.print(((c == 0) ? "" : " ") + ((int) nStr.charAt(c)));
				for (int c = 0; c < nStr.length(); c++)
					 System.out.print(((c == 0) ? ", " : " ") + Integer.toString(((int) nStr.charAt(c)), 16));
				System.out.println(")");
			}
		}
	}
	
	BufferedImage getCharImage(int chb) {
		if (this.charImages.containsKey(new Integer(chb)))
			return ((BufferedImage) this.charImages.get(new Integer(chb)));
		else return ((BufferedImage) this.charNameImages.get(this.diffNameMappings.get(new Integer(chb))));
	}
	synchronized void setCharImage(int chb, String chn, BufferedImage chi) {
		//	TODO also map in-font-program codes to char images on char decoder !!!
		//	TODO also map in-font-program codes to char UC mappings !!!
		if (DEBUG_LOAD_FONTS) System.out.println("Font " + this.name + " (" + this + "): char " + chb + " (" + chn + ") got image");
		if ((chb > 255) && !this.usesCharCode(new Integer(chb))) {
			int mChb = this.getCharCode(chn);
			if (mChb == 0) {
				Integer mChbObj = getCharCode(new Character((char) chb), this.encoding, DEBUG_CHAR_HANDLING);
				if ((mChbObj != null) && this.usesCharCode(mChbObj))
					mChb = mChbObj.intValue();
			}
			if (DEBUG_LOAD_FONTS) System.out.println(" ==> resolved unused char code " + chb + " to " + mChb);
			if (this.usesCharCode(new Integer(mChb))) {
				if (DEBUG_LOAD_FONTS) System.out.println(" ==> replaced unused char code " + chb + " with used char code " + mChb);
				chb = mChb;
			}
		}
		this.charImages.put(new Integer(chb), chi);
		this.charNameImages.put(chn, chi);
	}
	
	float getCharWidth(char ch) {
		return this.getCharWidth(ch, true);
	}
	private float getCharWidth(char ch, boolean isPrimaryLookup) {
		if (DEBUG_CHAR_HANDLING || (127 < ch)) System.out.println("Getting width for '" + ch + "' (" + ((int) ch) + ")");
		float cw = -1;
		if (cw == -1) {
			Float cwObj = ((Float) this.ccWidths.get(new Integer((int) ch)));
			if (cwObj != null) {
				cw = cwObj.floatValue();
				if (DEBUG_CHAR_HANDLING || (127 < ch)) System.out.println(" - width from own ur-mapping: " + cw);
			}
		}
		if (cw == -1) {
			Float cwObj = ((Float) this.cWidths.get(new Character(ch)));
			if (cwObj != null) {
				cw = cwObj.floatValue();
				if (DEBUG_CHAR_HANDLING || (127 < ch)) System.out.println(" - width from own r-mapping: " + cw);
			}
		}
		if ((cw == -1) && (this.charWidths != null) && (this.firstChar <= ch) && (ch <= this.lastChar)) {
//			cw = this.charWidths[((int) ch) - this.firstChar];
//			if (DEBUG_CHAR_HANDLING || (127 < ch)) System.out.println(" - width from base array: " + cw);
			float baCw = this.charWidths[((int) ch) - this.firstChar];
			if (baCw > 0) {
				cw = baCw;
				if (DEBUG_CHAR_HANDLING || (127 < ch)) System.out.println(" - width from base array: " + cw);
			}
		}
		if ((cw == -1) && (this.mCharWidth > 0) && this.fontUcMapped.contains(new Integer((int) ch))) {
			cw = this.mCharWidth;
			if (DEBUG_CHAR_HANDLING || (127 < ch)) System.out.println(" - mapped fallback default width: " + cw);
		}
		if ((cw == -1) && (this.baseFont != null)) {
			if (DEBUG_CHAR_HANDLING || (127 < ch)) System.out.println(" - doing base font lookup");
			Integer chi = new Integer((int) ch);
			char aCh = ch;
			if (this.diffMappings.containsKey(chi)) {
				Character mCh = ((Character) this.diffMappings.get(chi));
				if (DEBUG_CHAR_HANDLING || (127 < ch)) System.out.println("   - diff resolved to " + mCh);
				aCh = mCh.charValue();
			}
			else if ("AdobeStandardEncoding".equals(this.encoding)) {
				Character mCh = ((Character) standardAdobeMappings.get(chi));
				if (mCh != null) {
					if (DEBUG_CHAR_HANDLING || (127 < ch)) System.out.println("   - Adobe Standard resolved to " + mCh);
					aCh = mCh.charValue();
				}
			}
			else if ("MacRomanEncoding".equals(this.encoding)) {
				Character mCh = ((Character) macRomanMappings.get(chi));
				if (mCh != null) {
					if (DEBUG_CHAR_HANDLING || (127 < ch)) System.out.println("   - Mac Roman resolved to " + mCh);
					aCh = mCh.charValue();
				}
			}
			else if ("MacExpertEncoding".equals(this.encoding)) {
				Character mCh = ((Character) macExpertMappings.get(chi));
				if (mCh != null) {
					if (DEBUG_CHAR_HANDLING || (127 < ch)) System.out.println("   - Mac Expert resolved to " + mCh);
					aCh = mCh.charValue();
				}
			}
			cw = this.baseFont.getCharWidth(aCh);
			if (DEBUG_CHAR_HANDLING || (127 < ch)) System.out.println(" - width from base font (as " + aCh + "): " + cw);
		}
//		if ((cw == 0) && this.rCharMappings.containsKey(new Character((char) ch))) {
//			Integer rmCh = ((Integer) this.rCharMappings.get(new Character((char) ch)));
//			if ((rmCh != null) && (rmCh.intValue() != ch)) {
//				if (DEBUG_CHAR_HANDLING || (127 < ch)) System.out.println(" - recursing with reverse mapped CID");
//				return this.getCharWidth((char) rmCh.intValue());
//			}
//		}
		if (cw == -1) {
			cw = this.mCharWidth;
			if (DEBUG_CHAR_HANDLING || (127 < ch)) System.out.println(" - fallback missing width: " + cw);
		}
		
		if ((cw == -1) && isPrimaryLookup) {
			Character mCh = ((Character) this.diffMappings.get(new Integer(ch)));
			if ((mCh != null) && (mCh.charValue() != ch)) {
				cw = this.getCharWidth(mCh.charValue(), false);
				if (DEBUG_CHAR_HANDLING || (127 < ch)) System.out.println(" - Diff resolved char fallback: " + cw);
			}
		}
		if (DEBUG_CHAR_HANDLING || (127 < ch)) System.out.println(" --> width is " + cw);
		
		return cw;
	}
	
	/* set width for unresolved character code */
	synchronized void setCharWidth(Integer chc, float cw) {
		this.ccWidths.put(chc, new Float(cw));
		if (DEBUG_CHAR_HANDLING)
			System.out.println("Unresolved width for " + chc + " set to " + cw);
	}
	
	/* set width for actual character */
	synchronized void setCharWidth(Character ch, float cw) {
		this.cWidths.put(ch, new Float(cw));
		if (DEBUG_CHAR_HANDLING)
			System.out.println("Resolved width for '" + ch + "' (" + ((int) ch.charValue()) + ") set to " + cw);
	}
	
	float getMeasuredCharWidth(char ch) {
		if (this.mCcWidths != null) {
			Float cwObj = ((Float) this.mCcWidths.get(new Integer((int) ch)));
			if (cwObj != null)
				return cwObj.floatValue();
		}
		return this.getCharWidth(ch);
	}
	
	/* set measured width for unresolved character code */
	void setMeasuredCharWidth(Integer ch, float cw) {
		if (this.mCcWidths == null)
			this.mCcWidths = new HashMap();
		this.mCcWidths.put(ch, new Float(cw));
		if (DEBUG_CHAR_HANDLING)
			System.out.println("Unresolved measured width for " + ch + " set to " + cw);
	}
	
	float getRelativeBaselineShift(char ch) {
		Float bls = ((Float) this.charBaselineShifts.get(new Character(ch)));
		return ((bls == null) ? 0 : bls.floatValue());
	}
	float getRelativeBaselineShift(String str) {
		float blsUp = 0;
		float blsDown = 0;
		for (int c = 0; c < str.length(); c++) {
			char ch = str.charAt(c);
			if (Character.isWhitespace(ch))
				continue;
			float chBls = this.getRelativeBaselineShift(ch);
			if (DEBUG_CHAR_HANDLING && (chBls != 0))
				System.out.println("Baseline shift for " + ch + " is " + chBls);
			blsUp = Math.max(blsUp, chBls);
			blsDown = Math.min(blsDown, chBls);
		}
		return ((blsDown == 0) ? blsUp : blsDown);
	}
	void setRelativeBaselineShift(Character ch, float bls) {
		this.charBaselineShifts.put(ch, new Float(bls));
	}
	
	synchronized int addCombinedChar(int bChb1, PdfFont bChFont1, int bChb2, PdfFont bChFont2, String cChUc, String cChName) {
		
		//	try and find combined char in Unicode mapping
		for (Iterator ccit = this.ucMappings.keySet().iterator(); ccit.hasNext();) {
			Integer cc = ((Integer) ccit.next());
			if (cChUc.equals(this.ucMappings.get(cc))) {
				CharUsageStats cus = this.getCharUsageStats(cc);
				if (cus != null)
					cus.usageCount++;
				if (DEBUG_CHAR_HANDLING) System.out.println("Found combined char: " + cc);
				return cc.intValue();
			}
		}
		
		//	check if we have any unused bytes left
		if (this.usedCharStats.size() >= 255) {
			if (DEBUG_CHAR_HANDLING) System.out.println("Cannot create any further chars, no bytes left");
			return -1;
		}
		
		//	find free char byte
		int cChb = -1;
		for (int b = 1; b < 256; b++)
			if (!this.usedCharStats.containsKey(new Integer(b))) {
				cChb = b;
				break;
			}
		if (cChb == -1) {
			if (DEBUG_CHAR_HANDLING) System.out.println("Cannot create any further chars, no bytes left");
			return -1;
		}
		if (DEBUG_CHAR_HANDLING) System.out.println("Creating combined char '" + cChUc + "' (" + cChName + ") as code " + cChb);
		
		//	synthesize char image
		BufferedImage bChBi1 = bChFont1.getCharImage(bChb1);
		BufferedImage bChBi2 = bChFont2.getCharImage(bChb2);
		if ((bChBi1 != null) && (bChBi2 != null)) {
			BufferedImage cChBi = new BufferedImage((bChBi1.getWidth() + bChBi2.getWidth()), Math.max(bChBi1.getHeight(), bChBi2.getHeight()), BufferedImage.TYPE_BYTE_GRAY);
			Graphics cChBiGr = cChBi.createGraphics();
			cChBiGr.setColor(Color.WHITE);
			cChBiGr.fillRect(0, 0, cChBi.getWidth(), cChBi.getHeight());
			cChBiGr.drawImage(bChBi1, 0, 0, null);
			cChBiGr.drawImage(bChBi2, bChBi1.getWidth(), 0, null);
			this.setCharImage(cChb, cChName, cChBi);
			if (DEBUG_CHAR_HANDLING) System.out.println(" - got combined char image sized " + cChBi.getWidth() + "x" + cChBi.getHeight());
//			JOptionPane.showMessageDialog(null, "Combined char '" + cChUc + "' created", "Combined Char Created", JOptionPane.INFORMATION_MESSAGE, new ImageIcon(cChBi));
		}
		else if (DEBUG_CHAR_HANDLING) System.out.println(" - could not create char image due to at least one missing base char image");
		
		//	Unicode map newly added char
		this.mapUnicode(new Integer(cChb), cChUc, false);
		if (DEBUG_CHAR_HANDLING) System.out.println(" - Unicode mapped");
		
		//	get and store width of newly added char
		float cChWidth = Math.max(bChFont1.getCharWidth((char) bChb1), bChFont2.getCharWidth((char) bChb2));
		this.setCharWidth(new Integer(cChb), cChWidth);
		if (DEBUG_CHAR_HANDLING) System.out.println(" - char width computed as " + cChWidth);
		
		//	also store newly assigned char code in usage stats, so char doesn't get sorted out
		CharUsageStats cus = new CharUsageStats(cChb);
		cus.usageCount++;
		this.usedCharStats.put(new Integer(cChb), cus);
		if (DEBUG_CHAR_HANDLING) System.out.println(" - char usage stats added");
		
		//	finally ...
		return cChb;
	}
	
	static Character getChar(Integer chi, String encoding, boolean debug) {
		Character ch;
		
		//	try default encodings (only bytes less than 256)
		if ("AdobeStandardEncoding".equals(encoding)) {
			ch = ((Character) standardAdobeMappings.get(chi));
			if (ch != null) {
				if (debug) System.out.println(" --> Adobe resolved to " + ch.charValue());
				return ch;
			}
		}
		else if ("MacRomanEncoding".equals(encoding)) {
			ch = ((Character) macRomanMappings.get(chi));
			if (ch != null) {
				if (debug) System.out.println(" --> MacRoman resolved to " + ch.charValue());
				return ch;
			}
		}
		else if ("MacExpertEncoding".equals(encoding)) {
			ch = ((Character) macExpertMappings.get(chi));
			if (ch != null) {
				if (debug) System.out.println(" --> MacExpert resolved to " + ch.charValue());
				return ch;
			}
		}
		else if ("WinAnsiEncoding".equals(encoding) || (encoding == null)) {
			ch = ((Character) winAnsiMappings.get(chi));
			if (ch != null) {
				if (debug) System.out.println(" --> ANSI resolved to " + ch.charValue());
				return ch;
			}
		}
		
		//	we cannot decode this one ...
		return null;
	}
	
	static Integer getCharCode(Character ch, String encoding, boolean debug) {
		Integer chi;
		
		//	try default encodings (only bytes less than 256)
		if ("AdobeStandardEncoding".equals(encoding)) {
			chi = ((Integer) standardAdobeMappingsReverse.get(ch));
			if (chi != null) {
				if (debug) System.out.println(" --> Adobe encoded to " + chi.intValue());
				return chi;
			}
		}
		else if ("MacRomanEncoding".equals(encoding)) {
			chi = ((Integer) macRomanMappingsReverse.get(ch));
			if (chi != null) {
				if (debug) System.out.println(" --> MacRoman encoded to " + chi.intValue());
				return chi;
			}
		}
		else if ("MacExpertEncoding".equals(encoding)) {
			chi = ((Integer) macExpertMappingsReverse.get(ch));
			if (chi != null) {
				if (debug) System.out.println(" --> MacExpert encoded to " + chi.intValue());
				return chi;
			}
		}
		else if ("WinAnsiEncoding".equals(encoding) || (encoding == null)) {
			chi = ((Integer) winAnsiMappingsReverse.get(ch));
			if (chi != null) {
				if (debug) System.out.println(" --> ANSI encoded to " + chi.intValue());
				return chi;
			}
		}
		
		//	we cannot encode this one ...
		return null;
	}
	
	private static HashMap winAnsiMappings = new HashMap();
	private static HashMap winAnsiMappingsReverse = new HashMap();
	static {
		winAnsiMappings.put(new Integer(128), new Character('\u20AC'));
		
		winAnsiMappings.put(new Integer(130), new Character('\u201A'));
		winAnsiMappings.put(new Integer(131), new Character('\u0192'));
		winAnsiMappings.put(new Integer(132), new Character('\u201E'));
		winAnsiMappings.put(new Integer(133), new Character('\u2026'));
		winAnsiMappings.put(new Integer(134), new Character('\u2020'));
		winAnsiMappings.put(new Integer(135), new Character('\u2021'));
		winAnsiMappings.put(new Integer(136), new Character('\u02C6'));
		winAnsiMappings.put(new Integer(137), new Character('\u2030'));
		winAnsiMappings.put(new Integer(138), new Character('\u0160'));
		winAnsiMappings.put(new Integer(139), new Character('\u2039'));
		winAnsiMappings.put(new Integer(140), new Character('\u0152'));
		
		winAnsiMappings.put(new Integer(142), new Character('\u017D'));
		
		winAnsiMappings.put(new Integer(145), new Character('\u2018'));
		winAnsiMappings.put(new Integer(146), new Character('\u2019'));
		winAnsiMappings.put(new Integer(147), new Character('\u201C'));
		winAnsiMappings.put(new Integer(148), new Character('\u201D'));
		winAnsiMappings.put(new Integer(149), new Character('\u2022'));
		winAnsiMappings.put(new Integer(150), new Character('\u2013'));
		winAnsiMappings.put(new Integer(151), new Character('\u2014'));
		winAnsiMappings.put(new Integer(152), new Character('\u02DC'));
		winAnsiMappings.put(new Integer(153), new Character('\u2122'));
		winAnsiMappings.put(new Integer(154), new Character('\u0161'));
		winAnsiMappings.put(new Integer(155), new Character('\u203A'));
		winAnsiMappings.put(new Integer(156), new Character('\u0153'));
		
		winAnsiMappings.put(new Integer(158), new Character('\u017E'));
		winAnsiMappings.put(new Integer(159), new Character('\u0178'));
		
		for (Iterator cit = winAnsiMappings.keySet().iterator(); cit.hasNext();) {
			Integer chi = ((Integer) cit.next());
			winAnsiMappingsReverse.put(winAnsiMappings.get(chi), chi);
		}
	}
	
	private static HashMap macRomanMappings = new HashMap();
	private static HashMap macRomanMappingsReverse = new HashMap();
	static {
		macRomanMappings.put(new Integer(128), new Character('\u00C4'));
		macRomanMappings.put(new Integer(129), new Character('\u00C5'));
		macRomanMappings.put(new Integer(130), new Character('\u00C7'));
		macRomanMappings.put(new Integer(131), new Character('\u00C9'));
		macRomanMappings.put(new Integer(132), new Character('\u00D1'));
		macRomanMappings.put(new Integer(133), new Character('\u00D6'));
		macRomanMappings.put(new Integer(134), new Character('\u00DC'));
		macRomanMappings.put(new Integer(135), new Character('\u00E1'));
		macRomanMappings.put(new Integer(136), new Character('\u00E0'));
		macRomanMappings.put(new Integer(137), new Character('\u00E2'));
		macRomanMappings.put(new Integer(138), new Character('\u00E4'));
		macRomanMappings.put(new Integer(139), new Character('\u00E3'));
		macRomanMappings.put(new Integer(140), new Character('\u00E5'));
		macRomanMappings.put(new Integer(141), new Character('\u00E7'));
		macRomanMappings.put(new Integer(142), new Character('\u00E9'));
		macRomanMappings.put(new Integer(143), new Character('\u00E8'));
		
		macRomanMappings.put(new Integer(144), new Character('\u00EA'));
		macRomanMappings.put(new Integer(145), new Character('\u00EB'));
		macRomanMappings.put(new Integer(146), new Character('\u00ED'));
		macRomanMappings.put(new Integer(147), new Character('\u00EC'));
		macRomanMappings.put(new Integer(148), new Character('\u00EE'));
		macRomanMappings.put(new Integer(149), new Character('\u00EF'));
		macRomanMappings.put(new Integer(150), new Character('\u00F1'));
		macRomanMappings.put(new Integer(151), new Character('\u00F3'));
		macRomanMappings.put(new Integer(152), new Character('\u00F2'));
		macRomanMappings.put(new Integer(153), new Character('\u00F4'));
		macRomanMappings.put(new Integer(154), new Character('\u00F6'));
		macRomanMappings.put(new Integer(155), new Character('\u00F5'));
		macRomanMappings.put(new Integer(156), new Character('\u00FA'));
		macRomanMappings.put(new Integer(157), new Character('\u00F9'));
		macRomanMappings.put(new Integer(158), new Character('\u00FB'));
		macRomanMappings.put(new Integer(159), new Character('\u00FC'));
		
		macRomanMappings.put(new Integer(160), new Character('\u2020'));
		macRomanMappings.put(new Integer(161), new Character('\u00B0'));
		macRomanMappings.put(new Integer(162), new Character('\u00A2'));
		macRomanMappings.put(new Integer(163), new Character('\u00A3'));
		macRomanMappings.put(new Integer(164), new Character('\u00A7'));
		macRomanMappings.put(new Integer(165), new Character('\u2022'));
		macRomanMappings.put(new Integer(166), new Character('\u00B6'));
		macRomanMappings.put(new Integer(167), new Character('\u00DF'));
		macRomanMappings.put(new Integer(168), new Character('\u00AE'));
		macRomanMappings.put(new Integer(169), new Character('\u00A9'));
		macRomanMappings.put(new Integer(170), new Character('\u2122'));
		macRomanMappings.put(new Integer(171), new Character('\u00B4'));
		macRomanMappings.put(new Integer(172), new Character('\u00A8'));
		macRomanMappings.put(new Integer(173), new Character('\u2260'));
		macRomanMappings.put(new Integer(174), new Character('\u00C6'));
		macRomanMappings.put(new Integer(175), new Character('\u00D8'));
		
		macRomanMappings.put(new Integer(176), new Character('\u221E'));
		macRomanMappings.put(new Integer(177), new Character('\u00B1'));
		macRomanMappings.put(new Integer(178), new Character('\u2264'));
		macRomanMappings.put(new Integer(179), new Character('\u2265'));
		macRomanMappings.put(new Integer(180), new Character('\u00A5'));
		macRomanMappings.put(new Integer(181), new Character('\u00B5'));
		macRomanMappings.put(new Integer(182), new Character('\u2202'));
		macRomanMappings.put(new Integer(183), new Character('\u2211'));
		macRomanMappings.put(new Integer(184), new Character('\u220F'));
		macRomanMappings.put(new Integer(185), new Character('\u03C0'));
		macRomanMappings.put(new Integer(186), new Character('\u222B'));
		macRomanMappings.put(new Integer(187), new Character('\u00AA'));
		macRomanMappings.put(new Integer(188), new Character('\u00BA'));
		macRomanMappings.put(new Integer(189), new Character('\u03A9'));
		macRomanMappings.put(new Integer(190), new Character('\u00E6'));
		macRomanMappings.put(new Integer(191), new Character('\u00F8'));
		
		macRomanMappings.put(new Integer(192), new Character('\u00BF'));
		macRomanMappings.put(new Integer(193), new Character('\u00A1'));
		macRomanMappings.put(new Integer(194), new Character('\u00AC'));
		macRomanMappings.put(new Integer(195), new Character('\u221A'));
		macRomanMappings.put(new Integer(196), new Character('\u0192'));
		macRomanMappings.put(new Integer(197), new Character('\u2248'));
		macRomanMappings.put(new Integer(198), new Character('\u2206'));
		macRomanMappings.put(new Integer(199), new Character('\u00AB'));
		macRomanMappings.put(new Integer(200), new Character('\u00BB'));
		macRomanMappings.put(new Integer(201), new Character('\u2026'));
		macRomanMappings.put(new Integer(202), new Character('\u00A0'));
		macRomanMappings.put(new Integer(203), new Character('\u00C0'));
		macRomanMappings.put(new Integer(204), new Character('\u00C3'));
		macRomanMappings.put(new Integer(205), new Character('\u00D5'));
		macRomanMappings.put(new Integer(206), new Character('\u0152'));
		macRomanMappings.put(new Integer(207), new Character('\u0153'));
		
		macRomanMappings.put(new Integer(208), new Character('\u2013'));
		macRomanMappings.put(new Integer(209), new Character('\u2014'));
		macRomanMappings.put(new Integer(210), new Character('\u201C'));
		macRomanMappings.put(new Integer(211), new Character('\u201D'));
		macRomanMappings.put(new Integer(212), new Character('\u2018'));
		macRomanMappings.put(new Integer(213), new Character('\u2019'));
		macRomanMappings.put(new Integer(214), new Character('\u00F7'));
		macRomanMappings.put(new Integer(215), new Character('\u25CA'));
		macRomanMappings.put(new Integer(216), new Character('\u00FF'));
		macRomanMappings.put(new Integer(217), new Character('\u0178'));
		macRomanMappings.put(new Integer(218), new Character('\u2044'));
		macRomanMappings.put(new Integer(219), new Character('\u20AC'));
		macRomanMappings.put(new Integer(220), new Character('\u2039'));
		macRomanMappings.put(new Integer(221), new Character('\u203A'));
		macRomanMappings.put(new Integer(222), new Character('\uFB01'));
		macRomanMappings.put(new Integer(223), new Character('\uFB02'));
		
		macRomanMappings.put(new Integer(224), new Character('\u2021'));
		macRomanMappings.put(new Integer(225), new Character('\u00B7'));
		macRomanMappings.put(new Integer(226), new Character('\u201A'));
		macRomanMappings.put(new Integer(227), new Character('\u201E'));
		macRomanMappings.put(new Integer(228), new Character('\u2030'));
		macRomanMappings.put(new Integer(229), new Character('\u00C2'));
		macRomanMappings.put(new Integer(230), new Character('\u00CA'));
		macRomanMappings.put(new Integer(231), new Character('\u00C1'));
		macRomanMappings.put(new Integer(232), new Character('\u00CB'));
		macRomanMappings.put(new Integer(233), new Character('\u00C8'));
		macRomanMappings.put(new Integer(234), new Character('\u00CD'));
		macRomanMappings.put(new Integer(235), new Character('\u00CE'));
		macRomanMappings.put(new Integer(236), new Character('\u00CF'));
		macRomanMappings.put(new Integer(237), new Character('\u00CC'));
		macRomanMappings.put(new Integer(238), new Character('\u00D3'));
		macRomanMappings.put(new Integer(239), new Character('\u00D4'));
		
		macRomanMappings.put(new Integer(240), new Character('\uF8FF'));
		macRomanMappings.put(new Integer(241), new Character('\u00D2'));
		macRomanMappings.put(new Integer(242), new Character('\u00DA'));
		macRomanMappings.put(new Integer(243), new Character('\u00DB'));
		macRomanMappings.put(new Integer(244), new Character('\u00D9'));
		macRomanMappings.put(new Integer(245), new Character('\u0131'));
		macRomanMappings.put(new Integer(246), new Character('\u02C6'));
		macRomanMappings.put(new Integer(247), new Character('\u02DC'));
		macRomanMappings.put(new Integer(248), new Character('\u00AF'));
		macRomanMappings.put(new Integer(249), new Character('\u02D8'));
		macRomanMappings.put(new Integer(250), new Character('\u02D9'));
		macRomanMappings.put(new Integer(251), new Character('\u02DA'));
		macRomanMappings.put(new Integer(252), new Character('\u00B8'));
		macRomanMappings.put(new Integer(253), new Character('\u02DD'));
		macRomanMappings.put(new Integer(254), new Character('\u02DB'));
		macRomanMappings.put(new Integer(255), new Character('\u02C7'));
		
		for (Iterator cit = macRomanMappings.keySet().iterator(); cit.hasNext();) {
			Integer chi = ((Integer) cit.next());
			macRomanMappingsReverse.put(macRomanMappings.get(chi), chi);
		}
	}
	
	private static HashMap macExpertMappings = new HashMap();
	private static HashMap macExpertMappingsReverse = new HashMap();
	static {
		macExpertMappings.put(new Integer(33), new Character('\uf721'));
		macExpertMappings.put(new Integer(34), new Character('\uf6f8'));
		macExpertMappings.put(new Integer(35), new Character('\uf7a2'));
		macExpertMappings.put(new Integer(36), new Character('\uf724'));
		macExpertMappings.put(new Integer(37), new Character('\uf6e4'));
		macExpertMappings.put(new Integer(38), new Character('\uf726'));
		macExpertMappings.put(new Integer(39), new Character('\uf7b4'));
		macExpertMappings.put(new Integer(40), new Character('\u207d'));
		macExpertMappings.put(new Integer(41), new Character('\u207e'));
		macExpertMappings.put(new Integer(43), new Character('\u2024'));
		macExpertMappings.put(new Integer(47), new Character('\u2044'));
		macExpertMappings.put(new Integer(49), new Character('\uf731'));
		macExpertMappings.put(new Integer(51), new Character('\uf733'));
		macExpertMappings.put(new Integer(52), new Character('\uf734'));
		macExpertMappings.put(new Integer(53), new Character('\uf735'));
		macExpertMappings.put(new Integer(54), new Character('\uf736'));
		macExpertMappings.put(new Integer(55), new Character('\uf737'));
		macExpertMappings.put(new Integer(56), new Character('\uf738'));
		macExpertMappings.put(new Integer(57), new Character('\uf739'));
		macExpertMappings.put(new Integer(61), new Character('\uf6de'));
		macExpertMappings.put(new Integer(63), new Character('\uf73f'));
		macExpertMappings.put(new Integer(68), new Character('\uf7f0'));
		macExpertMappings.put(new Integer(71), new Character('\u00bc'));
		macExpertMappings.put(new Integer(72), new Character('\u00bd'));
		macExpertMappings.put(new Integer(73), new Character('\u00be'));
		macExpertMappings.put(new Integer(74), new Character('\u215b'));
		macExpertMappings.put(new Integer(75), new Character('\u215c'));
		macExpertMappings.put(new Integer(76), new Character('\u215d'));
		macExpertMappings.put(new Integer(77), new Character('\u215e'));
		macExpertMappings.put(new Integer(78), new Character('\u2153'));
		macExpertMappings.put(new Integer(86), new Character('\ufb00'));
		macExpertMappings.put(new Integer(87), new Character('\ufb01'));
		macExpertMappings.put(new Integer(88), new Character('\ufb02'));
		macExpertMappings.put(new Integer(89), new Character('\ufb03'));
		macExpertMappings.put(new Integer(90), new Character('\ufb04'));
		macExpertMappings.put(new Integer(91), new Character('\u208d'));
		macExpertMappings.put(new Integer(93), new Character('\u208e'));
		macExpertMappings.put(new Integer(94), new Character('\uf6f6'));
		macExpertMappings.put(new Integer(95), new Character('\uf6e5'));
		macExpertMappings.put(new Integer(96), new Character('\uf760'));
		macExpertMappings.put(new Integer(97), new Character('\uf761'));
		macExpertMappings.put(new Integer(98), new Character('\uf762'));
		macExpertMappings.put(new Integer(99), new Character('\uf763'));
		macExpertMappings.put(new Integer(100), new Character('\uf764'));
		macExpertMappings.put(new Integer(101), new Character('\uf765'));
		macExpertMappings.put(new Integer(102), new Character('\uf766'));
		macExpertMappings.put(new Integer(103), new Character('\uf767'));
		macExpertMappings.put(new Integer(104), new Character('\uf768'));
		macExpertMappings.put(new Integer(105), new Character('\uf769'));
		macExpertMappings.put(new Integer(106), new Character('\uf76a'));
		macExpertMappings.put(new Integer(107), new Character('\uf76b'));
		macExpertMappings.put(new Integer(108), new Character('\uf76c'));
		macExpertMappings.put(new Integer(109), new Character('\uf76d'));
		macExpertMappings.put(new Integer(110), new Character('\uf76e'));
		macExpertMappings.put(new Integer(111), new Character('\uf76f'));
		macExpertMappings.put(new Integer(112), new Character('\uf770'));
		macExpertMappings.put(new Integer(113), new Character('\uf771'));
		macExpertMappings.put(new Integer(114), new Character('\uf772'));
		macExpertMappings.put(new Integer(115), new Character('\uf773'));
		macExpertMappings.put(new Integer(116), new Character('\uf774'));
		macExpertMappings.put(new Integer(117), new Character('\uf775'));
		macExpertMappings.put(new Integer(118), new Character('\uf776'));
		macExpertMappings.put(new Integer(119), new Character('\uf777'));
		macExpertMappings.put(new Integer(120), new Character('\uf778'));
		macExpertMappings.put(new Integer(121), new Character('\uf779'));
		macExpertMappings.put(new Integer(122), new Character('\uf77a'));
		macExpertMappings.put(new Integer(123), new Character('\u20a1'));
		macExpertMappings.put(new Integer(124), new Character('\uf6dc'));
		macExpertMappings.put(new Integer(125), new Character('\uf6dd'));
		macExpertMappings.put(new Integer(126), new Character('\uf6fe'));
		macExpertMappings.put(new Integer(129), new Character('\uf6e9'));
		macExpertMappings.put(new Integer(130), new Character('\uf6e0'));
		macExpertMappings.put(new Integer(135), new Character('\uf7e1'));
		macExpertMappings.put(new Integer(136), new Character('\uf7e0'));
		macExpertMappings.put(new Integer(137), new Character('\uf7e2'));
		macExpertMappings.put(new Integer(138), new Character('\uf7e4'));
		macExpertMappings.put(new Integer(139), new Character('\uf7e3'));
		macExpertMappings.put(new Integer(140), new Character('\uf7e5'));
		macExpertMappings.put(new Integer(141), new Character('\uf7e7'));
		macExpertMappings.put(new Integer(142), new Character('\uf7e9'));
		macExpertMappings.put(new Integer(143), new Character('\uf7e8'));
		macExpertMappings.put(new Integer(144), new Character('\uf7ea'));
		macExpertMappings.put(new Integer(145), new Character('\uf7eb'));
		macExpertMappings.put(new Integer(146), new Character('\uf7ed'));
		macExpertMappings.put(new Integer(147), new Character('\uf7ec'));
		macExpertMappings.put(new Integer(148), new Character('\uf7ee'));
		macExpertMappings.put(new Integer(149), new Character('\uf7ef'));
		macExpertMappings.put(new Integer(150), new Character('\uf7f1'));
		macExpertMappings.put(new Integer(151), new Character('\uf7f3'));
		macExpertMappings.put(new Integer(152), new Character('\uf7f2'));
		macExpertMappings.put(new Integer(153), new Character('\uf7f4'));
		macExpertMappings.put(new Integer(154), new Character('\uf7f6'));
		macExpertMappings.put(new Integer(155), new Character('\uf7f5'));
		macExpertMappings.put(new Integer(156), new Character('\uf7fa'));
		macExpertMappings.put(new Integer(157), new Character('\uf7f9'));
		macExpertMappings.put(new Integer(158), new Character('\uf7fb'));
		macExpertMappings.put(new Integer(159), new Character('\uf7fc'));
		macExpertMappings.put(new Integer(161), new Character('\u2078'));
		macExpertMappings.put(new Integer(162), new Character('\u2084'));
		macExpertMappings.put(new Integer(163), new Character('\u2083'));
		macExpertMappings.put(new Integer(164), new Character('\u2086'));
		macExpertMappings.put(new Integer(165), new Character('\u2088'));
		macExpertMappings.put(new Integer(166), new Character('\u2087'));
		macExpertMappings.put(new Integer(167), new Character('\uf6fd'));
		macExpertMappings.put(new Integer(169), new Character('\uf6df'));
		macExpertMappings.put(new Integer(172), new Character('\uf7a8'));
		macExpertMappings.put(new Integer(174), new Character('\uf6f5'));
		macExpertMappings.put(new Integer(175), new Character('\uf6f0'));
		macExpertMappings.put(new Integer(176), new Character('\u2085'));
		macExpertMappings.put(new Integer(178), new Character('\uf6e1'));
		macExpertMappings.put(new Integer(179), new Character('\uf6e7'));
		macExpertMappings.put(new Integer(180), new Character('\uf7fd'));
		macExpertMappings.put(new Integer(182), new Character('\uf6e3'));
		macExpertMappings.put(new Integer(185), new Character('\uf7fe'));
		macExpertMappings.put(new Integer(187), new Character('\u2089'));
		macExpertMappings.put(new Integer(189), new Character('\uf6ff'));
		macExpertMappings.put(new Integer(190), new Character('\uf7e6'));
		macExpertMappings.put(new Integer(191), new Character('\uf7f8'));
		macExpertMappings.put(new Integer(192), new Character('\uf7bf'));
		macExpertMappings.put(new Integer(193), new Character('\u2081'));
		macExpertMappings.put(new Integer(194), new Character('\uf6f9'));
		macExpertMappings.put(new Integer(201), new Character('\uf7b8'));
		macExpertMappings.put(new Integer(207), new Character('\uf6fa'));
		macExpertMappings.put(new Integer(208), new Character('\u2012'));
		macExpertMappings.put(new Integer(209), new Character('\uf6e6'));
		macExpertMappings.put(new Integer(214), new Character('\uf7a1'));
		macExpertMappings.put(new Integer(216), new Character('\uf7ff'));
		macExpertMappings.put(new Integer(218), new Character('\u00b9'));
		macExpertMappings.put(new Integer(221), new Character('\u2074'));
		macExpertMappings.put(new Integer(222), new Character('\u2075'));
		macExpertMappings.put(new Integer(223), new Character('\u2076'));
		macExpertMappings.put(new Integer(224), new Character('\u2077'));
		macExpertMappings.put(new Integer(225), new Character('\u2079'));
		macExpertMappings.put(new Integer(228), new Character('\uf6ec'));
		macExpertMappings.put(new Integer(229), new Character('\uf6f1'));
		macExpertMappings.put(new Integer(233), new Character('\uf6ed'));
		macExpertMappings.put(new Integer(234), new Character('\uf6f2'));
		macExpertMappings.put(new Integer(235), new Character('\uf6eb'));
		macExpertMappings.put(new Integer(241), new Character('\uf6ee'));
		macExpertMappings.put(new Integer(242), new Character('\uf6fb'));
		macExpertMappings.put(new Integer(243), new Character('\uf6f4'));
		macExpertMappings.put(new Integer(244), new Character('\uf7af'));
		macExpertMappings.put(new Integer(245), new Character('\uf6ea'));
		macExpertMappings.put(new Integer(246), new Character('\u207f'));
		macExpertMappings.put(new Integer(247), new Character('\uf6ef'));
		macExpertMappings.put(new Integer(248), new Character('\uf6e2'));
		macExpertMappings.put(new Integer(249), new Character('\uf6e8'));
		macExpertMappings.put(new Integer(250), new Character('\uf6f7'));
		macExpertMappings.put(new Integer(251), new Character('\uf6fc'));
		
		for (Iterator cit = macExpertMappings.keySet().iterator(); cit.hasNext();) {
			Integer chi = ((Integer) cit.next());
			macExpertMappingsReverse.put(macExpertMappings.get(chi), chi);
		}
	}
	
	private static HashMap standardAdobeMappings = new HashMap();
	private static HashMap standardAdobeMappingsReverse = new HashMap();
	static {
		standardAdobeMappings.put(new Integer(39), new Character('\u2019'));
		
		standardAdobeMappings.put(new Integer(45), new Character('\u00AD'));
		
		standardAdobeMappings.put(new Integer(96), new Character('\u2018'));
		
		standardAdobeMappings.put(new Integer(164), new Character('\u2044'));
		standardAdobeMappings.put(new Integer(164), new Character('\u2215'));
		standardAdobeMappings.put(new Integer(166), new Character('\u0192'));
		standardAdobeMappings.put(new Integer(168), new Character('\u00A4'));
		standardAdobeMappings.put(new Integer(169), new Character('\''));
		standardAdobeMappings.put(new Integer(170), new Character('\u201C'));
		standardAdobeMappings.put(new Integer(172), new Character('\u2039'));
		standardAdobeMappings.put(new Integer(173), new Character('\u203A'));
		standardAdobeMappings.put(new Integer(174), new Character('\uFB01'));
		standardAdobeMappings.put(new Integer(175), new Character('\uFB02'));
		
		standardAdobeMappings.put(new Integer(177), new Character('\u2013'));
		standardAdobeMappings.put(new Integer(178), new Character('\u2020'));
		standardAdobeMappings.put(new Integer(179), new Character('\u2021'));
		standardAdobeMappings.put(new Integer(180), new Character('\u00B7'));
		standardAdobeMappings.put(new Integer(180), new Character('\u2219'));
		standardAdobeMappings.put(new Integer(183), new Character('\u2022'));
		standardAdobeMappings.put(new Integer(184), new Character('\u201A'));
		standardAdobeMappings.put(new Integer(185), new Character('\u201E'));
		standardAdobeMappings.put(new Integer(186), new Character('\u201D'));
		standardAdobeMappings.put(new Integer(188), new Character('\u2026'));
		standardAdobeMappings.put(new Integer(189), new Character('\u2030'));
		
		standardAdobeMappings.put(new Integer(193), new Character('\u0060'));
		standardAdobeMappings.put(new Integer(194), new Character('\u00B4'));
		standardAdobeMappings.put(new Integer(195), new Character('\u02C6'));
		standardAdobeMappings.put(new Integer(196), new Character('\u02DC'));
		standardAdobeMappings.put(new Integer(197), new Character('\u00AF'));
		standardAdobeMappings.put(new Integer(197), new Character('\u02C9'));
		standardAdobeMappings.put(new Integer(198), new Character('\u02D8'));
		standardAdobeMappings.put(new Integer(199), new Character('\u02D9'));
		standardAdobeMappings.put(new Integer(200), new Character('\u00A8'));
		standardAdobeMappings.put(new Integer(202), new Character('\u02DA'));
		standardAdobeMappings.put(new Integer(203), new Character('\u00B8'));
		standardAdobeMappings.put(new Integer(205), new Character('\u02DD'));
		standardAdobeMappings.put(new Integer(206), new Character('\u02DB'));
		standardAdobeMappings.put(new Integer(207), new Character('\u02C7'));
		
		standardAdobeMappings.put(new Integer(208), new Character('\u2014'));
		
		standardAdobeMappings.put(new Integer(225), new Character('\u00C6'));
		standardAdobeMappings.put(new Integer(227), new Character('\u00AA'));
		standardAdobeMappings.put(new Integer(232), new Character('\u0141'));
		standardAdobeMappings.put(new Integer(233), new Character('\u00D8'));
		standardAdobeMappings.put(new Integer(234), new Character('\u0152'));
		standardAdobeMappings.put(new Integer(235), new Character('\u00BA'));
		
		standardAdobeMappings.put(new Integer(241), new Character('\u00E6'));
		standardAdobeMappings.put(new Integer(245), new Character('\u0131'));
		standardAdobeMappings.put(new Integer(248), new Character('\u0142'));
		standardAdobeMappings.put(new Integer(249), new Character('\u00F8'));
		standardAdobeMappings.put(new Integer(250), new Character('\u0153'));
		standardAdobeMappings.put(new Integer(251), new Character('\u00DF'));
		
		for (Iterator cit = standardAdobeMappings.keySet().iterator(); cit.hasNext();) {
			Integer chi = ((Integer) cit.next());
			standardAdobeMappingsReverse.put(standardAdobeMappings.get(chi), chi);
		}
	}
	
	static final boolean DEBUG_LOAD_FONTS = false;
	
	static PdfFont readFont(Object fnObj, Map fontData, Map objects, boolean needChars, Map charDecoders, FontDecoderCharset charSet, ProgressMonitor pm) throws IOException {
		Object ftObj = fontData.get("Subtype");
		if (ftObj == null) {
			if (DEBUG_LOAD_FONTS) {
				System.out.println("Problem in font " + fontData);
				System.out.println(" --> missing font type");
			}
			return null;
		}
		if (DEBUG_LOAD_FONTS) System.out.println("Reading font type " + ftObj);
		
		if ("Type0".equals(ftObj.toString()))
			return readFontType0(fontData, objects, charDecoders, charSet, pm);
		
		if ("Type1".equals(ftObj.toString()))
			return readFontType1(fontData, objects, charDecoders, pm);
		
		if ("Type3".equals(ftObj.toString()))
			return readFontType3(fnObj, fontData, objects, charDecoders, charSet, pm);
		
		if ("TrueType".equals(ftObj.toString()))
			return readFontTrueType(fontData, objects, charDecoders, pm);
		
		if ("CIDFontType0".equals(ftObj.toString()))
			return readFontCidType0(fontData, objects, needChars, charDecoders, pm);
		
		if ("CIDFontType2".equals(ftObj.toString()))
			return readFontCidType2(fontData, objects, needChars, charDecoders, pm);
		
		if (DEBUG_LOAD_FONTS) {
			System.out.println("Problem in font " + fontData);
			System.out.println(" --> unknown font type");
		}
		return null;
	}
	
	private static PdfFont readFontType0(Map fontData, Map objects, Map charDecoders, FontDecoderCharset charSet, ProgressMonitor pm) throws IOException {
		pm.setInfo("Loading type 0 font");
		Object dfaObj = PdfParser.dereference(fontData.get("DescendantFonts"), objects);
		if (!(dfaObj instanceof List) || (((List) dfaObj).size() != 1)) {
			if (DEBUG_LOAD_FONTS) {
				System.out.println("Problem in font " + fontData);
				System.out.println(" --> strange font descriptor: " + dfaObj);
			}
			pm.setInfo(" ==> error in font descriptor");
			return null;
		}
		
		Object tuObj = PdfParser.dereference(fontData.get("ToUnicode"), objects);
		HashMap toUnicodeMappings = null;
		if (tuObj instanceof PStream) {
			Object filter = ((PStream) tuObj).params.get("Filter");
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			try {
				PdfParser.decode(((filter == null) ? "FlateDecode" : filter), ((PStream) tuObj).bytes, ((PStream) tuObj).params, baos, objects);
			}
			catch (Exception e) {
				if (filter == null)
					PdfParser.decode(null, ((PStream) tuObj).bytes, ((PStream) tuObj).params, baos, objects);
				else if (e instanceof IOException)
					throw ((IOException) e);
				else if (e instanceof RuntimeException)
					throw ((RuntimeException) e);
			}
			byte[] tuMapData = baos.toByteArray();
			if (DEBUG_LOAD_FONTS)
				System.out.println(" --> to unicode: " + new String(tuMapData));
			pm.setInfo(" - got Unicode mapping");
			toUnicodeMappings = readToUnicodeCMap(tuMapData, true);
			if (DEBUG_LOAD_FONTS)
				System.out.println(" ==> to unicode: " + toUnicodeMappings);
		}
		else if (DEBUG_LOAD_FONTS) System.out.println(" --> to unicode: " + tuObj);
		
		Object dfObj = PdfParser.dereference(((List) dfaObj).get(0), objects);
		if (!(dfObj instanceof Map)) {
			if (DEBUG_LOAD_FONTS) {
				System.out.println("Problem in font " + fontData);
				System.out.println(" --> strange descendant font: " + dfObj);
			}
			pm.setInfo(" ==> error in descendant font");
			return null;
		}
		System.out.println(" --> descendant font: " + dfObj);
		
		PdfFont dFont = readFont(null, ((Map) dfObj), objects, (toUnicodeMappings == null), charDecoders, charSet, pm);
		if (dFont == null) {
			if (DEBUG_LOAD_FONTS) {
				System.out.println("Problem in font " + dfObj);
				System.out.println(" --> could not load descendant font");
			}
			pm.setInfo(" ==> could not load descendant font");
			return null;
		}
		if (DEBUG_LOAD_FONTS)
			System.out.println(" - descendant font read: " + dFont.name + " (" + dFont + ")");
		
		Object fnObj = PdfParser.dereference(fontData.get("BaseFont"), objects);
		if (fnObj == null) {
			if (DEBUG_LOAD_FONTS) {
				System.out.println("Problem in font " + fontData);
				System.out.println(" --> missing font name");
			}
			pm.setInfo(" ==> font name missing");
			return null;
		}
		if (DEBUG_LOAD_FONTS)
			System.out.println(" --> base font name: " + fnObj);
		
		Object feObj = PdfParser.dereference(fontData.get("Encoding"), objects);
		if (feObj instanceof Map)
			feObj = PdfParser.dereference(((Map) feObj).get("BaseEncoding"), objects);
		
		String pFontName = ((String) fontData.get("DerivedFontName")); // use base font name with randomized suffix
		if (pFontName == null) {
			//	TODO maybe use hash of font data or font descriptor instead ?!?
			//	TODO maybe toString font data or font descriptor (or union of both) in TreeMap (for ordering of keys)
			pFontName = (fnObj.toString() + "-Dx" + Integer.toString(((int) (Math.random() * 0x00010000)), 16).toUpperCase());
			fontData.put("DerivedFontName", pFontName);
		}
		
		PdfFont pFont = new PdfFont(fontData, dFont.descriptor, dFont.firstChar, dFont.lastChar, dFont.charWidths, dFont.mCharWidth, pFontName, ((feObj == null) ? null : feObj.toString()));
		pFont.ascent = dFont.ascent;
		pFont.descent = dFont.descent;
		pFont.xHeight = dFont.xHeight;
		pFont.capHeight = dFont.capHeight;
		
		pFont.bold = dFont.bold;
		pFont.italics = dFont.italics;
		
		pFont.setBaseFont(dFont.baseFont);
		
		for (Iterator chcit = dFont.ccWidths.keySet().iterator(); chcit.hasNext();) {
			Integer chc = ((Integer) chcit.next());
			pFont.setCharWidth(chc, ((Float) dFont.ccWidths.get(chc)).floatValue());
		}
		for (Iterator chit = dFont.cWidths.keySet().iterator(); chit.hasNext();) {
			Character ch = ((Character) chit.next());
			pFont.setCharWidth(ch, ((Float) dFont.cWidths.get(ch)).floatValue());
		}
		
		if (toUnicodeMappings == null) {
			if (DEBUG_LOAD_FONTS)
				System.out.println(" - to unicode (DF): " + dFont.ucMappings);
			pFont.ucMappings.putAll(dFont.ucMappings);
		}
		else {
			if (DEBUG_LOAD_FONTS)
				System.out.println(" - to unicode (OM): " + toUnicodeMappings);
			for (Iterator cmit = toUnicodeMappings.keySet().iterator(); cmit.hasNext();) {
				Integer chc = ((Integer) cmit.next());
				pFont.mapUnicode(chc, ((String) toUnicodeMappings.get(chc)), true);
			}
		}
		
		pFont.setCharDecoder(dFont.charDecoder);
		
		if (DEBUG_LOAD_FONTS)
			System.out.println(" - type 0 font read: " + pFont.name + " (" + pFont + ")");
		pm.setInfo(" ==> font loaded");
		return pFont;
	}
	
	private static HashMap readToUnicodeCMap(byte[] cMap, boolean isHex) throws IOException {
		
		//	read mapping into list first
		ArrayList toUcMappings = new ArrayList();
		PdfByteInputStream bytes = new PdfByteInputStream(new ByteArrayInputStream(cMap));
		byte[] lookahead = new byte[12];
		while (bytes.peek() != -1) {
			bytes.skipSpace();
			int l = bytes.peek(lookahead);
			if (l < lookahead.length)
				break;
			if (PdfUtils.startsWith(lookahead, "beginbfchar", 0)) {
				bytes.skip("beginbfchar".length());
				cropBfCharMapping(bytes, isHex, toUcMappings);
			}
			else if (PdfUtils.startsWith(lookahead, "beginbfrange", 0)) {
				bytes.skip("beginbfrange".length());
				cropBfCharMappingRange(bytes, isHex, toUcMappings);
			}
			else {
				Object skip = PdfParser.cropNext(bytes, true, false);
				if (skip instanceof PTag) {
					String skipTag = ((PTag) skip).tag;
					if (DEBUG_LOAD_FONTS) System.out.println(" - skipped: " + skipTag);
					if (skipTag.length() == 0)
						break; // nothing sensible to come
				}
			}
		}
		
		//	sort by raw bytes
		Collections.sort(toUcMappings, new Comparator() {
			public int compare(Object obj1, Object obj2) {
				ToUnicodeMapping tucm1 = ((ToUnicodeMapping) obj1);
				ToUnicodeMapping tucm2 = ((ToUnicodeMapping) obj2);
				return (tucm1.ch - tucm2.ch);
			}
		});
		
		//	test if we have decoding conflicts
		int lowerCaseCodeSum = 0;
		int lowerCaseCount = 0;
		int upperCaseCodeSum = 0;
		int upperCaseCount = 0;
		int identityMappingCount = 0;
		TreeMap revToUcMapping = new TreeMap();
		for (int m = 0; m < toUcMappings.size(); m++) {
			ToUnicodeMapping tucm = ((ToUnicodeMapping) toUcMappings.get(m));
			revToUcMapping.put(tucm.ucStr, tucm);
			if (127 < tucm.ch) { /* no use counting out case for anything beyond basic Latin (there is just so many more lower case diacritics than upper case ones in the higher ranges) */ }
			else if (tucm.isLowerCase()) {
				lowerCaseCodeSum += tucm.ch;
				lowerCaseCount++;
			}
			else if (tucm.isUpperCase()) {
				upperCaseCodeSum += tucm.ch;
				upperCaseCount++;
			}
			if (tucm.isIdentity())
				identityMappingCount++;
		}
		
		//	do we have a decoding conflict?
		if ((revToUcMapping.size() < toUcMappings.size()) && (lowerCaseCount != 0) && (upperCaseCount != 0)) {
			if (DEBUG_LOAD_FONTS)
				System.out.println("Resolving " + (toUcMappings.size() - revToUcMapping.size()) + " to-Unicode mapping conflicts");
			int avgLowerCaseCode = (lowerCaseCodeSum / lowerCaseCount);
			if (DEBUG_LOAD_FONTS)
				System.out.println(" - average lower case code is " + avgLowerCaseCode + " from " + lowerCaseCount + " UC mappings");
			int avgUpperCaseCode = (upperCaseCodeSum / upperCaseCount);
			if (DEBUG_LOAD_FONTS)
				System.out.println(" - average upper case code is " + avgUpperCaseCode + " from " + upperCaseCount + " UC mappings");
			if (DEBUG_LOAD_FONTS)
				System.out.println(" - got " + identityMappingCount + " identity mappings out of " + toUcMappings.size() + " UC mappings");
			boolean upperCaseLow = (avgUpperCaseCode < avgLowerCaseCode);
			for (int m = 0; m < toUcMappings.size(); m++) {
				ToUnicodeMapping tucm = ((ToUnicodeMapping) toUcMappings.get(m));
				ToUnicodeMapping cTucm = ((ToUnicodeMapping) revToUcMapping.get(tucm.ucStr));
				if (tucm == cTucm)
					continue;
				if (tucm.isLowerCase() && !revToUcMapping.containsKey(tucm.ucStr.toUpperCase())) {
					if (upperCaseLow) {
						tucm.ucStr = tucm.ucStr.toUpperCase();
						if (DEBUG_LOAD_FONTS)
							System.out.println(" - re-mapped '" + tucm.ch + "' to '" + tucm.ucStr + "'" + ((tucm.ucStr.length() == 1) ? (" (" + ((int) tucm.ucStr.charAt(0)) + ", " + Integer.toString(((int) tucm.ucStr.charAt(0)), 16) + ")") : ""));
						revToUcMapping.put(tucm.ucStr, tucm);
					}
					else {
						cTucm.ucStr = cTucm.ucStr.toUpperCase();
						if (DEBUG_LOAD_FONTS)
							System.out.println(" - re-mapped '" + cTucm.ch + "' to '" + cTucm.ucStr + "'" + ((cTucm.ucStr.length() == 1) ? (" (" + ((int) cTucm.ucStr.charAt(0)) + ", " + Integer.toString(((int) cTucm.ucStr.charAt(0)), 16) + ")") : ""));
						revToUcMapping.put(cTucm.ucStr, cTucm);
					}
				}
				else if (tucm.isUpperCase() && !revToUcMapping.containsKey(tucm.ucStr.toLowerCase())) {
					if (upperCaseLow) {
						cTucm.ucStr = cTucm.ucStr.toLowerCase();
						if (DEBUG_LOAD_FONTS)
							System.out.println(" - re-mapped '" + cTucm.ch + "' to '" + cTucm.ucStr + "'" + ((cTucm.ucStr.length() == 1) ? (" (" + ((int) cTucm.ucStr.charAt(0)) + ", " + Integer.toString(((int) cTucm.ucStr.charAt(0)), 16) + ")") : ""));
						revToUcMapping.put(cTucm.ucStr, cTucm);
					}
					else {
						tucm.ucStr = tucm.ucStr.toLowerCase();
						if (DEBUG_LOAD_FONTS)
							System.out.println(" - re-mapped '" + tucm.ch + "' to '" + tucm.ucStr + "'" + ((tucm.ucStr.length() == 1) ? (" (" + ((int) tucm.ucStr.charAt(0)) + ", " + Integer.toString(((int) tucm.ucStr.charAt(0)), 16) + ")") : ""));
						revToUcMapping.put(tucm.ucStr, tucm);
					}
				}
			}
		}
		
		//	put mapping into map and return it
		HashMap tuc = new HashMap();
		for (int m = 0; m < toUcMappings.size(); m++) {
			ToUnicodeMapping tucm = ((ToUnicodeMapping) toUcMappings.get(m));
			tuc.put(new Integer(tucm.ch), tucm.ucStr);
		}
		return tuc;
		//	TODO also read PostScript char names
	}
	
	private static class ToUnicodeMapping {
		final int ch;
		String ucStr;
		ToUnicodeMapping(int ch, String ucStr) {
			this.ch = ch;
			this.ucStr = ucStr;
		}
		boolean isLowerCase() {
			return (this.ucStr.toLowerCase().equals(this.ucStr) && !this.ucStr.toUpperCase().equals(this.ucStr));
		}
		boolean isUpperCase() {
			return (this.ucStr.toUpperCase().equals(this.ucStr) && !this.ucStr.toLowerCase().equals(this.ucStr));
		}
		boolean isIdentity() {
			return ((this.ucStr.length() == 1) && (this.ch == this.ucStr.charAt(0)));
		}
	}
	
	private static void cropBfCharMapping(PdfByteInputStream bytes, boolean isHex, ArrayList toUcMappings) throws IOException {
		while (bytes.peek() != -1) {
			Integer ch = null;
			Object srcObj = PdfParser.cropNext(bytes, true, true);
			if ((srcObj instanceof PTag) && "endbfchar".equals(((PTag) srcObj).tag))
				break;
			if (srcObj instanceof PString) {
				PString src = ((PString) srcObj);
				if (src.length() == 1) // given as Hex2
					ch = new Integer(src.charAt(0));
				else if (src.length() == 2)// given as Hex4
					ch = new Integer((256 * src.charAt(0)) + src.charAt(1));
			}
			else if (srcObj instanceof Number)
				ch = new Integer(((Number) srcObj).intValue());
			
			String ucStr = null;
			Object destObj = PdfParser.cropNext(bytes, false, false);
			if (DEBUG_LOAD_FONTS) System.out.println(" - got object: " + destObj);
			if (destObj instanceof PString) {
				PString dest = ((PString) destObj);
				if (dest.length() != 0) {
					if (dest.isHexWithSpace) {
						String destStr = dest.toString();
						if (DEBUG_LOAD_FONTS) {
							System.out.println(" - hex with space: " + destStr);
							for (int c = 0; c < destStr.length(); c++)
								System.out.println("   - " + destStr.charAt(c) + " (" + ((int) destStr.charAt(c)) + ", " + Integer.toString(((int) destStr.charAt(c)), 16) + ")");
						}
						if (destStr.length() > 1) {
							char nCh = StringUtils.getCharForName(destStr);
							if (nCh != 0) {
								if (DEBUG_LOAD_FONTS)
									System.out.println(" - aggregated to '" + nCh + "' (" + ((int) nCh) + ")");
								destStr = ("" + nCh);
							}
						}
						for (int c = 0; c < destStr.length(); c++) {
							char ucCh = destStr.charAt(c);
							if ((ucCh < 0xE000) || (0xF8FF < ucCh)) {
								ucStr = ("" + ucCh);
								break;
							}
						}
					}
					else ucStr = dest.toString();
				}
			}
			else if (destObj instanceof Number)
				ucStr = ("" + ((char) ((Number) destObj).intValue()));
			else if ((destObj instanceof String) || (destObj instanceof Name)) {
				char uch = StringUtils.getCharForName(destObj.toString());
				if (uch != 0)
					ucStr = ("" + StringUtils.getNormalForm(uch));
			}
			
			if ((ch != null) && (ucStr != null)) {
				if (DEBUG_LOAD_FONTS) {
					System.out.print(" - mapped (s) '" + ch.intValue() + "' to '" + ucStr + "' (");
					for (int c = 0; c < ucStr.length(); c++)
						 System.out.print(((c == 0) ? "" : " ") + ((int) ucStr.charAt(c)));
					for (int c = 0; c < ucStr.length(); c++)
						 System.out.print(((c == 0) ? ", " : " ") + Integer.toString(((int) ucStr.charAt(c)), 16));
					System.out.println(")");
				}
				toUcMappings.add(new ToUnicodeMapping(ch.intValue(), ucStr));
			}
		}
	}
	
	/*
	n beginbfrange
	srcCode1 srcCode2 dstString
	endbfrange
	
	n beginbfrange
	srcCode1 srcCoden [dstString1 dstString2 … dstStringn]
	endbfrange
	 */	
	private static void cropBfCharMappingRange(PdfByteInputStream bytes, boolean isHex, ArrayList toUcMappings) throws IOException {
		while (bytes.peek() != -1) {
			int fch = -1;
			Object fSrcObj = PdfParser.cropNext(bytes, true, true);
			if ((fSrcObj instanceof PTag) && "endbfrange".equals(((PTag) fSrcObj).tag))
				break;
			else if (fSrcObj instanceof PString) {
				PString src = ((PString) fSrcObj);
				if (src.length() == 1) // given as Hex2
					fch = ((int) src.charAt(0));
				else if (src.length() == 2) // given as Hex4
					fch = ((int) ((256 * src.charAt(0)) + src.charAt(1)));
			}
			else if (fSrcObj instanceof Number)
				fch = ((Number) fSrcObj).intValue();
			
			int lch = -1;
			Object lSrcObj = PdfParser.cropNext(bytes, false, true);
			if (lSrcObj instanceof PString) {
				PString src = ((PString) lSrcObj);
				if (src.length() == 1) // given as Hex2
					lch =((int) src.charAt(0));
				else if (src.length() == 2) // given as Hex4
					lch = ((int) ((256 * src.charAt(0)) + src.charAt(1)));
			}
			else if (lSrcObj instanceof Number)
				lch = ((Number) lSrcObj).intValue();
			
			int mch = -1;
			Object destObj = PdfParser.cropNext(bytes, false, false);
			
			if (destObj instanceof PString) {
				PString dest = ((PString) destObj);
				if ((mch == -1) && (dest.length() > 1)) {
					char nCh = StringUtils.getCharForName(dest.toString());
					if (nCh != 0) {
						if (DEBUG_LOAD_FONTS)
							System.out.println(" - aggregated '" + dest.toString() + "' to '" + nCh + "' (" + ((int) nCh) + ")");
						mch = nCh;
					}
				}
				if ((mch == -1) && (dest.length() != 0))
					mch = ((int) dest.charAt(0));
			}
			else if (destObj instanceof Number)
				mch = ((Number) destObj).intValue();
			
			if ((fch == -1) || (lch == -1))
				continue;
			
			if (mch != -1) {
				for (int c = fch; c <= lch; c++) {
					if (DEBUG_LOAD_FONTS)
						System.out.println(" - mapped (r) '" + c + "' to '" + ("" + ((char) mch)) + "' (" + ((int) mch) + ", " + Integer.toString(((int) mch), 16) + ")");
					toUcMappings.add(new ToUnicodeMapping(c, ("" + ((char) mch++))));
				}
				continue;
			}
			
			if (destObj instanceof List) {
				List dest = ((List) destObj);
				for (int c = fch; (c <= lch) && ((c-fch) < dest.size()); c++) {
					Object dObj = dest.get(c-fch);
					if (dObj instanceof PString) {
						PString d = ((PString) dObj);
						String dStr = d.toString();
						if ((mch == -1) && (dStr.length() > 1)) {
							char nCh = StringUtils.getCharForName(dStr.toString());
							if (nCh != 0) {
								if (DEBUG_LOAD_FONTS)
									System.out.println(" - aggregated '" + dStr.toString() + "' to '" + nCh + "' (" + ((int) nCh) + ")");
								dStr = ("" + nCh);
							}
						}
						if (dStr.length() != 0) {
							if (DEBUG_LOAD_FONTS)
								System.out.println(" - mapped (as) '" + c + "' to '" + dStr.toString() + "'" + ((dStr.toString().length() == 1) ? (" (" + ((int) dStr.toString().charAt(0)) + ", " + Integer.toString(((int) dStr.toString().charAt(0)), 16) + ")") : ""));
							toUcMappings.add(new ToUnicodeMapping(c, dStr.toString()));
						}
					}
					else if (dObj instanceof Number) {
						if (DEBUG_LOAD_FONTS)
							System.out.println(" - mapped (an) '" + c + "' to '" + ("" + ((char) ((Number) dObj).intValue())) + "'" + " (" + ((Number) dObj).intValue() + ", " + Integer.toString(((Number) dObj).intValue(), 16) + ")");
						toUcMappings.add(new ToUnicodeMapping(c, ("" + ((char) ((Number) dObj).intValue()))));
					}
				}
			}
		}
	}
	
	private static PdfFont readFontCidType0(Map fontData, final Map objects, boolean needChars, Map charDecoders, ProgressMonitor pm) throws IOException {
		Object fdObj = PdfParser.dereference(fontData.get("FontDescriptor"), objects);
		if (!(fdObj instanceof Map)) {
			if (DEBUG_LOAD_FONTS) {
				System.out.println("Problem in font " + fontData);
				System.out.println(" --> strange font descriptor: " + fdObj);
			}
			pm.setInfo(" ==> font descriptor missing");
			return null;
		}
		if (DEBUG_LOAD_FONTS)
			System.out.println("Got CID Type 0 font descriptor: " + fdObj);
		
		Object fnObj = PdfParser.dereference(fontData.get("BaseFont"), objects);
		if (fnObj == null) {
			if (DEBUG_LOAD_FONTS) {
				System.out.println("Problem in font " + fontData);
				System.out.println(" --> missing font name");
			}
			pm.setInfo(" ==> font name missing");
			return null;
		}
		
		Object siObj = PdfParser.dereference(fontData.get("CIDSystemInfo"), objects);
		if (DEBUG_LOAD_FONTS)
			System.out.println("Got CID system info: " + siObj);
		
		boolean isSymbolic = false;
		if (fdObj instanceof Map) {
			Object fObj = ((Map) fdObj).get("Flags");
			if (fObj != null)
				isSymbolic = (fObj.toString().indexOf("3") != -1);
		}
		
		BaseFont baseFont = getBuiltInFont(fnObj, isSymbolic);
		
		HashMap toUnicodeMappings = null;
		if (fdObj instanceof Map) {
			if (DEBUG_LOAD_FONTS) System.out.println("Got font descriptor: " + fdObj);
			if (baseFont != null)
				for (Iterator kit = baseFont.descriptor.keySet().iterator(); kit.hasNext();) {
					Object key = kit.next();
					if (!((Map) fdObj).containsKey(key))
						((Map) fdObj).put(key, baseFont.descriptor.get(key));
				}
			Object tuObj = PdfParser.dereference(fontData.get("ToUnicode"), objects);
			if (tuObj instanceof PStream) {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				Object filter = ((PStream) tuObj).params.get("Filter");
				try {
					PdfParser.decode(((filter == null) ? "FlateDecode" : filter), ((PStream) tuObj).bytes, ((PStream) tuObj).params, baos, objects);
				}
				catch (Exception e) {
					if (filter == null)
						PdfParser.decode(null, ((PStream) tuObj).bytes, ((PStream) tuObj).params, baos, objects);
					else if (e instanceof IOException)
						throw ((IOException) e);
					else if (e instanceof RuntimeException)
						throw ((RuntimeException) e);
				}
				byte[] tuMapData = baos.toByteArray();
				if (DEBUG_LOAD_FONTS)
					System.out.println(" --> to unicode: " + new String(tuMapData));
				pm.setInfo(" - got Unicode mapping");
				toUnicodeMappings = readToUnicodeCMap(tuMapData, true);
			}
			else if (DEBUG_LOAD_FONTS) System.out.println(" --> to unicode: " + tuObj);
		}
		else {
			if (baseFont == null) {
				if (DEBUG_LOAD_FONTS) {
					System.out.println("Problem in font " + fontData);
					System.out.println(" --> strange font descriptor: " + fdObj);
				}
				Object dfaObj = fontData.get("DescendantFonts");
				if (dfaObj instanceof List) {
					Object dfObj = PdfParser.dereference(((List) dfaObj).get(0), objects);
					if (dfObj != null) {
						if (DEBUG_LOAD_FONTS) System.out.println(" --> descendant fonts: " + dfObj);
					}
				}
				Object tuObj = PdfParser.dereference(fontData.get("ToUnicode"), objects);
				if (tuObj instanceof PStream) {
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					Object filter = ((PStream) tuObj).params.get("Filter");
					try {
						PdfParser.decode(((filter == null) ? "FlateDecode" : filter), ((PStream) tuObj).bytes, ((PStream) tuObj).params, baos, objects);
					}
					catch (Exception e) {
						if (filter == null)
							PdfParser.decode(null, ((PStream) tuObj).bytes, ((PStream) tuObj).params, baos, objects);
						else if (e instanceof IOException)
							throw ((IOException) e);
						else if (e instanceof RuntimeException)
							throw ((RuntimeException) e);
					}
					byte[] tuMapData = baos.toByteArray();
					if (DEBUG_LOAD_FONTS)
						System.out.println(" --> to unicode: " + new String(tuMapData));
					toUnicodeMappings = readToUnicodeCMap(tuMapData, true);
				}
				else if (DEBUG_LOAD_FONTS) System.out.println(" --> to unicode: " + tuObj);
				return null;
			}
			else fdObj = baseFont.descriptor;
			if (DEBUG_LOAD_FONTS) System.out.println("Got font descriptor: " + fdObj);
		}
		
		final Map fd = ((Map) fdObj);
		
		int fc = -1;
		int lc = -1;
		float[] cws = new float[0];
		Object dwObj = fd.get("DW");
		if (dwObj == null)
			dwObj = PdfParser.dereference(fontData.get("DW"), objects);
		float mcw = ((dwObj instanceof Number) ? ((Number) dwObj).floatValue() : 1000);
		if (DEBUG_LOAD_FONTS)
			System.out.println(" --> default width: " + dwObj);
		
		Object wObj = PdfParser.dereference(fontData.get("W"), objects);
		if (DEBUG_LOAD_FONTS)
			System.out.println(" --> width object: " + wObj);
		HashMap widths = new HashMap();
		final HashMap cidMap = new HashMap();
		if ((wObj instanceof List) && (((List) wObj).size() >= 2)) {
			List w = ((List) wObj);
			ArrayList cwList = new ArrayList();
			HashMap ucMap = new HashMap();
			int cid = 1; // TODO might have to start at 0
			for (int i = 0; i < (w.size()-1);) {
				Object fcObj = w.get(i++);
				if (!(fcObj instanceof Number))
					continue;
				
				int rfc = ((Number) fcObj).intValue();
				if (fc == -1)
					fc = rfc;
				else while (lc < rfc) {
					cwList.add(new Float(0));
					lc++;
				}
				if (DEBUG_LOAD_FONTS)
					System.out.println("   - range first char is " + rfc);
				
				Object lcObj = w.get(i++);
				if (DEBUG_LOAD_FONTS)
					System.out.println("   - range last char object is " + lcObj);
				if ((lcObj instanceof Number) && (i < w.size())) {
					lc = ((Number) lcObj).intValue();
					if (DEBUG_LOAD_FONTS)
						System.out.println("   - (new) last char is " + lc);
					
					Object rwObj = w.get(i++);
					if (DEBUG_LOAD_FONTS)
						System.out.println("   - range width object is " + rwObj);
					if (rwObj instanceof Number) {
						Float rw = new Float(((Number) rwObj).floatValue());
						for (int c = rfc; c <= lc; c++) {
							cwList.add(rw);
							widths.put(new Integer(c), rw);
							char ch = ((char) c);
							String nCh = StringUtils.getNormalForm(ch);
							cidMap.put(new Integer(cid++), new Integer((int) ch));
							ucMap.put(new Integer((int) ch), ((nCh.length() == 1) ? ("" + ch) : nCh));
						}
					}
				}
				else if (lcObj instanceof List) {
					List ws = ((List) lcObj);
					for (int c = 0; c < ws.size(); c++) {
						Object cwObj = ws.get(c);
						if (cwObj instanceof Number) {
							Float cw = new Float(((Number) cwObj).floatValue());
							cwList.add(cw);
							widths.put(new Integer(rfc + c), cw);
							char ch = ((char) (rfc + c));
							String nCh = StringUtils.getNormalForm(ch);
							cidMap.put(new Integer(cid++), new Integer((int) ch));
							ucMap.put(new Integer((int) ch), ((nCh.length() == 1) ? ("" + ch) : nCh));
						}
						else cwList.add(new Float(0));
						if (c != 0)
							lc++;
					}
				}
			}
			cws = new float[cwList.size()];
			for (int c = 0; c < cwList.size(); c++)
				cws[c] = ((Number) cwList.get(c)).floatValue();
			if (toUnicodeMappings == null)
				toUnicodeMappings = ucMap;
			else for (Iterator cit = ucMap.keySet().iterator(); cit.hasNext();) {
				Integer chc = ((Integer) cit.next());
				if (!toUnicodeMappings.containsKey(chc))
					toUnicodeMappings.put(chc, ucMap.get(chc));
			}
			if (DEBUG_LOAD_FONTS)
				System.out.println(" --> Unicode mappig is " + ucMap);
			if (DEBUG_LOAD_FONTS)
				System.out.println(" --> CID mappig is " + cidMap);
		}
		
		float scw = 0;
		if ((fc > 32) && (mcw == 0)) {
			Object csObj = fd.get("CharSet");
			if (DEBUG_LOAD_FONTS)
				System.out.println(" --> char set is " + csObj);
			if ((csObj instanceof PString) && (((PString) csObj).toString().indexOf("/space") == -1) && (cws != null)) {
				float wSum = 0;
				int wCount = 0;
				for (int c = 0; c < cws.length; c++)
					if (cws[c] != 0) {
						wSum += cws[c];
						wCount++;
					}
				if (wCount != 0)
					scw = ((wSum * 2) / (wCount * 3));
				if (DEBUG_LOAD_FONTS)
					System.out.println(" --> space width interpolated as " + scw);
			}
		}
		
		Object feObj = PdfParser.dereference(fontData.get("Encoding"), objects);
		if (feObj instanceof Map)
			feObj = PdfParser.dereference(((Map) feObj).get("BaseEncoding"), objects);
		else if ((feObj == null) && (fdObj instanceof Map))
			feObj = PdfParser.dereference(((Map) fdObj).get("Encoding"), objects);
		if (DEBUG_LOAD_FONTS) System.out.println(" --> encoding is " + feObj + " (" + feObj.getClass().getName() + ")");
		
		final PdfFont pFont = new PdfFont(fontData, fd, fc, lc, cws, mcw, fnObj.toString(), ((feObj == null) ? null : feObj.toString()));
		if (!addLineMetrics(pFont, 0, pm))
			return null;
		if (DEBUG_LOAD_FONTS) System.out.println("CIDType0 font created");
		
		pFont.setBaseFont(baseFont);
		
		for (Iterator ccit = widths.keySet().iterator(); ccit.hasNext();) {
			Integer cc = ((Integer) ccit.next());
			pFont.setCharWidth(cc, ((Float) widths.get(cc)).floatValue());
		}
		
		if (toUnicodeMappings != null)
			for (Iterator cmit = toUnicodeMappings.keySet().iterator(); cmit.hasNext();) {
				Integer chc = ((Integer) cmit.next());
				pFont.mapUnicode(chc, ((String) toUnicodeMappings.get(chc)), true);
			}
		
		if (scw != 0) {
			pFont.setCharWidth(new Character(' '), scw);
			pFont.setCharWidth(new Integer((int) ' '), scw);
		}
		
		final Object ff3RefObj = fd.get("FontFile3");
		final Object ff3Obj = PdfParser.dereference(fd.get("FontFile3"), objects);
		if (ff3Obj instanceof PStream) {
//			pFont.setCharDecoder(new CharDecoder() {
//				public void decodeChars(PdfFont font, FontDecoderCharset charSet, ProgressMonitor pm) throws IOException {
//					System.out.println("Encoding of font " + font.name + " is " + font.encoding + " (CID0, " + pFont + ")");
//					System.out.println("Got font file 3 from " + pFont.name + " (CID0, " + pFont + ")");
//					System.out.println("  --> params are " + ((PStream) ff3Obj).params);
//					Object filter = ((PStream) ff3Obj).params.get("Filter");
//					System.out.println("  --> filter is " + filter);
//					ByteArrayOutputStream baos = new ByteArrayOutputStream();
//					PdfParser.decode(filter, ((PStream) ff3Obj).bytes, ((PStream) ff3Obj).params, baos, objects);
//					Object stObj = ((PStream) ff3Obj).params.get("Subtype");
//					if ((stObj != null) && "CIDFontType0C".equals(stObj.toString())) {
//						pm.setInfo(" - reading type 1c char definitions");
//						PdfFontDecoder.readFontType1C(baos.toByteArray(), fd, font, this, (("Identity-H".equals(font.encoding) || "Identity-V".equals(font.encoding)) ? null : cidMap), null, charSet, pm);
//					}
//				}
//			});
			CharDecoder chd = ((CharDecoder) charDecoders.get(ff3RefObj.toString()));
			if (chd == null) {
				chd = new CharDecoder() {
					public void decodeChars(PdfFont font, FontDecoderCharset charSet, ProgressMonitor pm) throws IOException {
						System.out.println("Encoding of font " + font.name + " is " + font.encoding + " (CID0, " + pFont + ")");
						System.out.println("Got font file 3 from " + pFont.name + " (CID0, " + pFont + ")");
						System.out.println("  --> params are " + ((PStream) ff3Obj).params);
						Object filter = ((PStream) ff3Obj).params.get("Filter");
						System.out.println("  --> filter is " + filter);
						ByteArrayOutputStream baos = new ByteArrayOutputStream();
						PdfParser.decode(filter, ((PStream) ff3Obj).bytes, ((PStream) ff3Obj).params, baos, objects);
						Object stObj = ((PStream) ff3Obj).params.get("Subtype");
						if ((stObj != null) && "CIDFontType0C".equals(stObj.toString())) {
							pm.setInfo(" - reading type 1c char definitions");
							PdfFontDecoder.readFontType1C(baos.toByteArray(), fd, font, this, (("Identity-H".equals(font.encoding) || "Identity-V".equals(font.encoding)) ? null : cidMap), null, charSet, pm);
						}
						font.type = "Cid0C";
					}
				};
				charDecoders.put(ff3RefObj.toString(), chd);
				if (DEBUG_LOAD_FONTS) System.out.println(" - created char decoder for " + ff3RefObj.toString());
			}
			else if (DEBUG_LOAD_FONTS) System.out.println(" - cache hit for char decoder " + ff3RefObj.toString());
			pFont.setCharDecoder(chd);
		}		
		return pFont;
	}
	
	private static PdfFont readFontCidType2(Map fontData, final Map objects, boolean needChars, Map charDecoders, ProgressMonitor pm) throws IOException {
		if (DEBUG_LOAD_FONTS) System.out.println(" - font data: " + fontData);
		Object fdObj = PdfParser.dereference(fontData.get("FontDescriptor"), objects);
		if (!(fdObj instanceof Map)) {
			if (DEBUG_LOAD_FONTS) {
				System.out.println("Problem in font " + fontData);
				System.out.println(" --> strange font descriptor: " + fdObj);
			}
			return null;
		}
		if (DEBUG_LOAD_FONTS) System.out.println(" - font descriptor: " + fdObj);
		
		Object fnObj = PdfParser.dereference(fontData.get("BaseFont"), objects);
		if (fnObj == null) {
			if (DEBUG_LOAD_FONTS) {
				System.out.println("Problem in font " + fontData);
				System.out.println(" --> missing font name");
			}
			return null;
		}
		if (DEBUG_LOAD_FONTS) System.out.println(" - base font: " + fnObj);
		
//		BaseFont baseFont = getBuiltInFont(fnObj.toString().substring(7), false);
		String fn = fnObj.toString();
		BaseFont baseFont = getBuiltInFont((fn.matches("[A-Z0-9]{6}\\+") ? fn.substring(7) : fn), false);
		if (baseFont == null) {
			if (DEBUG_LOAD_FONTS) {
				System.out.println("Problem in font " + fontData);
				System.out.println(" --> could not load base font " + fnObj.toString().substring(7));
			}
			return null;
		}
		
		Map fd = ((Map) fdObj);
		
		int fc = -1;
		int lc = -1;
		float[] cws = new float[0];
		Object dwObj = fd.get("DW");
		if (dwObj == null)
			dwObj = PdfParser.dereference(fontData.get("DW"), objects);
		float mcw = ((dwObj instanceof Number) ? ((Number) dwObj).floatValue() : 1000);
		if (DEBUG_LOAD_FONTS)
			System.out.println(" --> default width: " + dwObj);
		
		Object wObj = PdfParser.dereference(fontData.get("W"), objects);
		if (DEBUG_LOAD_FONTS)
			System.out.println(" --> width object: " + wObj);
		HashMap widths = new HashMap();
		if ((wObj instanceof List) && (((List) wObj).size() >= 2)) {
			List w = ((List) wObj);
			ArrayList cwList = new ArrayList();
			for (int i = 0; i < (w.size()-1);) {
				Object fcObj = w.get(i++);
				if (!(fcObj instanceof Number))
					continue;
				
				int rfc = ((Number) fcObj).intValue();
				if (fc == -1)
					fc = rfc;
				else while (lc < rfc) {
					cwList.add(new Float(0));
					lc++;
				}
				if (DEBUG_LOAD_FONTS)
					System.out.println("   - range first char is " + rfc);
				
				Object lcObj = w.get(i++);
				if (DEBUG_LOAD_FONTS)
					System.out.println("   - range last char object is " + lcObj);
				if ((lcObj instanceof Number) && (i < w.size())) {
					lc = ((Number) lcObj).intValue();
					if (DEBUG_LOAD_FONTS)
						System.out.println("   - (new) last char is " + lc);
					
					Object rwObj = w.get(i++);
					if (DEBUG_LOAD_FONTS)
						System.out.println("   - range width object is " + rwObj);
					if (rwObj instanceof Number) {
						Float rw = new Float(((Number) rwObj).floatValue());
						for (int c = rfc; c <= lc; c++) {
							cwList.add(rw);
							widths.put(new Integer(c), rw);
						}
					}
				}
				else if (lcObj instanceof List) {
					List ws = ((List) lcObj);
					for (int c = 0; c < ws.size(); c++) {
						Object cwObj = ws.get(c);
						if (cwObj instanceof Number) {
							Float cw = new Float(((Number) cwObj).floatValue());
							cwList.add(cw);
							widths.put(new Integer(rfc + c), cw);
						}
						else cwList.add(new Float(0));
						if (c != 0)
							lc++;
					}
				}
			}
			cws = new float[cwList.size()];
			for (int c = 0; c < cwList.size(); c++)
				cws[c] = ((Number) cwList.get(c)).floatValue();
		}
		
		/*
CIDSet=66 0R
FontFile2=65 0R
//FontFamily=Times New Roman
//FontStretch=Normal
//StemV=82
//FontName=OGCEJG+TimesNewRomanPSMT
		 */
		
		Object cidsObj = PdfParser.dereference(fd.get("CIDSet"), objects);
		ArrayList cids = new ArrayList();
		if (cidsObj instanceof PStream) {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			Object filter = ((PStream) cidsObj).params.get("Filter");
			try {
				PdfParser.decode(((filter == null) ? "FlateDecode" : filter), ((PStream) cidsObj).bytes, ((PStream) cidsObj).params, baos, objects);
			}
			catch (Exception e) {
				if (filter == null)
					PdfParser.decode(null, ((PStream) cidsObj).bytes, ((PStream) cidsObj).params, baos, objects);
				else if (e instanceof IOException)
					throw ((IOException) e);
				else if (e instanceof RuntimeException)
					throw ((RuntimeException) e);
			}
			byte[] csBytes = baos.toByteArray();
			int cid = 0;
			for (int b = 0; b < csBytes.length; b++) {
				int bt = convertUnsigned(csBytes[b]);
				for (int i = 0; i < 8; i++) {
					if ((bt & 128) != 0) {
						if (DEBUG_LOAD_FONTS) 
							System.out.println(" - CID " + cid + " is set");
						cids.add(new Integer(cid));
					}
					bt <<= 1;
					cid++;
				}
			}
		}
		else System.out.println("Got CID set: " + cidsObj);
		
		Object ctgmObj = PdfParser.dereference(fontData.get("CIDToGIDMap"), objects);
		final HashMap glyphIdsToCids = new HashMap();
		if (ctgmObj instanceof PStream) {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			Object filter = ((PStream) ctgmObj).params.get("Filter");
			try {
				PdfParser.decode(((filter == null) ? "FlateDecode" : filter), ((PStream) ctgmObj).bytes, ((PStream) ctgmObj).params, baos, objects);
			}
			catch (Exception e) {
				if (filter == null)
					PdfParser.decode(null, ((PStream) ctgmObj).bytes, ((PStream) ctgmObj).params, baos, objects);
				else if (e instanceof IOException)
					throw ((IOException) e);
				else if (e instanceof RuntimeException)
					throw ((RuntimeException) e);
			}
			byte[] ctgmBytes = baos.toByteArray();
			for (int b = 0; (b+1) < ctgmBytes.length; b += 2) {
				int gi = convertUnsigned(ctgmBytes[b]);
				gi <<= 8;
				gi |= convertUnsigned(ctgmBytes[b+1]);
				if (gi != 0)
					glyphIdsToCids.put(new Integer(gi), new Integer(b/2));
				if (DEBUG_LOAD_FONTS) 
					System.out.println(" - CID " + (b/2) + " mapped to glyph " + gi);
			}
		}
		else System.out.println("Got CID to GID map: " + cidsObj);
		
		Object feObj = PdfParser.dereference(fontData.get("Encoding"), objects);
		if (feObj instanceof Map)
			feObj = PdfParser.dereference(((Map) feObj).get("BaseEncoding"), objects);
		else if ((feObj == null) && (fdObj instanceof Map))
			feObj = PdfParser.dereference(((Map) fdObj).get("Encoding"), objects);
		
		final PdfFont pFont = new PdfFont(fontData, fd, fc, lc, cws, mcw, fnObj.toString(), ((feObj == null) ? null : feObj.toString()));
		if (!addLineMetrics(pFont, 0, pm))
			return null;
		if (DEBUG_LOAD_FONTS) System.out.println("CIDType2 font created");
		
		pFont.setBaseFont(baseFont);
		
		for (Iterator ccit = widths.keySet().iterator(); ccit.hasNext();) {
			Integer cc = ((Integer) ccit.next());
			pFont.setCharWidth(cc, ((Float) widths.get(cc)).floatValue());
		}
		
		Object fObj = fd.get("Flags");
		int flags = ((fObj instanceof Number) ? ((Number) fObj).intValue() : -1);
		if (flags != -1) {
			//	TODO interpret flags (should the need arise)
		}
		
		Object fwObj = fd.get("FontWeight");
		float fw = ((fwObj instanceof Number) ? ((Number) fwObj).floatValue() : 400);
		pFont.bold = (fw > 650);
		
		Object iaObj = fd.get("ItalicAngle");
		float ia = ((iaObj instanceof Number) ? ((Number) iaObj).floatValue() : 0);
		pFont.italics = (ia < -5);
		
		//	add special mappings from font file (only after assessing which chars are actually used, however)
		final Object ff2RefObj = fd.get("FontFile2");
		final Object ff2Obj = PdfParser.dereference(fd.get("FontFile2"), objects);
		if (DEBUG_LOAD_FONTS) System.out.println("Got Font File 2: " + ff2Obj);
		if (ff2Obj instanceof PStream) {
//			pFont.setCharDecoder(new CharDecoder() {
//				public void decodeChars(PdfFont font, FontDecoderCharset charSet, ProgressMonitor pm) throws IOException {
//					System.out.println("Got font file 2 from " + pFont.name + " (CID2, " + pFont + ")");
//					System.out.println("  --> argument font is " + font.name + " (" + font + ")");
//					System.out.println("  --> base font mode is " + (font != pFont));
//					System.out.println("  --> params are " + ((PStream) ff2Obj).params);
//					Object filter = ((PStream) ff2Obj).params.get("Filter");
//					System.out.println("  --> filter is " + filter);
//					ByteArrayOutputStream baos = new ByteArrayOutputStream();
//					PdfParser.decode(filter, ((PStream) ff2Obj).bytes, ((PStream) ff2Obj).params, baos, objects);
//					byte[] ffBytes = baos.toByteArray();
//					Object l1Obj = ((PStream) ff2Obj).params.get("Length1");
//					System.out.println("  --> length is " + ffBytes.length + ", length1 is " + l1Obj);
//					PdfFontDecoder.readFontTrueType(ffBytes, font, this, (font != pFont), glyphIdsToCids, charSet, pm);
//				}
//			});
			CharDecoder chd = ((CharDecoder) charDecoders.get(ff2RefObj.toString()));
			if (chd == null) {
				chd = new CharDecoder() {
					public void decodeChars(PdfFont font, FontDecoderCharset charSet, ProgressMonitor pm) throws IOException {
						System.out.println("Got font file 2 from " + pFont.name + " (CID2, " + pFont + ")");
						System.out.println("  --> argument font is " + font.name + " (" + font + ")");
						System.out.println("  --> base font mode is " + (font != pFont));
						System.out.println("  --> params are " + ((PStream) ff2Obj).params);
						Object filter = ((PStream) ff2Obj).params.get("Filter");
						System.out.println("  --> filter is " + filter);
						ByteArrayOutputStream baos = new ByteArrayOutputStream();
						PdfParser.decode(filter, ((PStream) ff2Obj).bytes, ((PStream) ff2Obj).params, baos, objects);
						byte[] ffBytes = baos.toByteArray();
						Object l1Obj = ((PStream) ff2Obj).params.get("Length1");
						System.out.println("  --> length is " + ffBytes.length + ", length1 is " + l1Obj);
						PdfFontDecoder.readFontTrueType(ffBytes, font, this, (font != pFont), glyphIdsToCids, charSet, pm);
						font.type = "Cid2";
					}
				};
				charDecoders.put(ff2RefObj.toString(), chd);
				if (DEBUG_LOAD_FONTS) System.out.println(" - created char decoder for " + ff2RefObj.toString());
			}
			else if (DEBUG_LOAD_FONTS) System.out.println(" - cache hit for char decoder " + ff2RefObj.toString());
			pFont.setCharDecoder(chd);
		}
		
		return pFont;
	}
	
	private static PdfFont readFontType3(Object fnObj, Map fontData, final Map objects, Map charDecoders, FontDecoderCharset charSet, ProgressMonitor pm) throws IOException {
		pm.setInfo("Loading type 3 font");
		Object fdObj = PdfParser.dereference(fontData.get("FontDescriptor"), objects);
		if (!(fdObj instanceof Map)) {
			if (DEBUG_LOAD_FONTS) {
				System.out.println("Problem in font " + fontData);
				System.out.println(" --> strange font descriptor: " + fdObj);
			}
		}
		
		Object tuObj = PdfParser.dereference(fontData.get("ToUnicode"), objects);
		HashMap toUnicodeMappings = null;
		if (tuObj instanceof PStream) {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			Object filter = ((PStream) tuObj).params.get("Filter");
			try {
				PdfParser.decode(((filter == null) ? "FlateDecode" : filter), ((PStream) tuObj).bytes, ((PStream) tuObj).params, baos, objects);
			}
			catch (Exception e) {
				if (filter == null)
					PdfParser.decode(null, ((PStream) tuObj).bytes, ((PStream) tuObj).params, baos, objects);
				else if (e instanceof IOException)
					throw ((IOException) e);
				else if (e instanceof RuntimeException)
					throw ((RuntimeException) e);
			}
			byte[] tuMapData = baos.toByteArray();
			pm.setInfo(" - got Unicode mapping");
			if (DEBUG_LOAD_FONTS)
				System.out.println(" --> to unicode: " + new String(tuMapData));
			toUnicodeMappings = readToUnicodeCMap(tuMapData, true);
		}
		else System.out.println(" --> to unicode: " + tuObj);
		
		Object rObj = PdfParser.dereference(fontData.get("Resources"), objects);
		if (rObj instanceof Map) {
			if (DEBUG_LOAD_FONTS) System.out.println("Got font resources: " + rObj);
		}
		else if (DEBUG_LOAD_FONTS) {
			System.out.println("Problem in font " + fontData);
			System.out.println(" --> strange resources: " + rObj);
		}
		
		Object eObj = PdfParser.dereference(fontData.get("Encoding"), objects);
		final HashMap diffEncodings = new HashMap();
		final HashMap diffEncodingNames = new HashMap();
		HashMap resolvedCodes = new HashMap();
		HashMap unresolvedDiffEncodings = new HashMap();
		HashMap unresolvedCodes = new HashMap();
		if (eObj instanceof Map) {
			if (DEBUG_LOAD_FONTS) System.out.println("Got encoding: " + eObj);
			Object dObj = PdfParser.dereference(((Map) eObj).get("Differences"), objects);
			if (DEBUG_LOAD_FONTS) System.out.println("Got differences: " + dObj);
			if (dObj instanceof List) {
				List diffs = ((List) dObj);
				int nextCode = -1;
				for (int d = 0; d < diffs.size(); d++) {
					Object diff = diffs.get(d);
					if (diff instanceof Number)
						nextCode = ((Number) diff).intValue();
					else if (nextCode != -1) {
						Integer code = new Integer(nextCode++);
						String diffCharName = diff.toString();
						diffEncodingNames.put(code, diffCharName);
						char diffChar = StringUtils.getCharForName(diffCharName);
						if (diffChar != 0) {
							diffEncodings.put(code, new Character(diffChar));
							resolvedCodes.put(diffCharName, code);
						}
						else if ((toUnicodeMappings == null) || !toUnicodeMappings.containsKey(code)) {
							unresolvedDiffEncodings.put(code, diffCharName);
							unresolvedCodes.put(diffCharName, code);
						}
					}
				}
			}
			else {
				if (DEBUG_LOAD_FONTS) {
					System.out.println("Problem in font " + fontData);
					System.out.println(" --> strange differences definition: " + dObj);
				}
				pm.setInfo(" ==> error in differences descriptor");
				return null;
			}
			if (DEBUG_LOAD_FONTS) {
				System.out.print("Got mappings: {");
				for (Iterator dit = diffEncodings.keySet().iterator(); dit.hasNext();) {
					Integer d = ((Integer) dit.next());
					Character ch = ((Character) diffEncodings.get(d));
					System.out.print(d + "=" + ch + "(" + StringUtils.getNormalForm(ch.charValue()) + ")");
					if (dit.hasNext())
						System.out.print(", ");
				}
				System.out.println("}");
			}
		}
		else {
			if (DEBUG_LOAD_FONTS) {
				System.out.println("Problem in font " + fontData);
				System.out.println(" --> strange encoding: " + eObj);
			}
			pm.setInfo(" ==> error in encoding");
			return null;
		}
		
		final Object cpsObj = PdfParser.dereference(fontData.get("CharProcs"), objects);
		if (!(cpsObj instanceof Map)) {
			if (DEBUG_LOAD_FONTS) {
				System.out.println("Problem in font " + fontData);
				System.out.println(" --> strange char procs: " + cpsObj);
			}
			pm.setInfo(" ==> error in char programs");
			return null;
		}
		
		float normFactor = 1; // factor for normalizing to 1/1000 scale used in Type1 fonts
		Object fmObj = PdfParser.dereference(fontData.get("FontMatrix"), objects);
		float fma = 0.001f;
		float fmd = 0.001f;
		if (fmObj instanceof List) {
			if (DEBUG_LOAD_FONTS) System.out.println("Got font matrix: " + fmObj);
			List fm = ((List) fmObj);
			if (fm.size() >= 4) {
				Object aObj = fm.get(0);
				if (aObj instanceof Number)
					fma = ((Number) aObj).floatValue();
				Object dObj = fm.get(3);
				if (dObj instanceof Number)
					fmd = ((Number) dObj).floatValue();
				normFactor = (((Math.abs(fma) + Math.abs(fmd)) / 2) / 0.001f);
				if (DEBUG_LOAD_FONTS) System.out.println("Norm factor is: " + normFactor);
			}
		}
		else {
			if (DEBUG_LOAD_FONTS) {
				System.out.println("Problem in font " + fontData);
				System.out.println(" --> strange font matrix: " + fmObj);
			}
			pm.setInfo(" ==> error in font matrix");
			return null;
		}
		
		Object fcObj = fontData.get("FirstChar");
		if (!(fcObj instanceof Number)) {
			if (DEBUG_LOAD_FONTS) {
				System.out.println("Problem in font " + fontData);
				System.out.println(" --> strange first char: " + fcObj);
			}
			pm.setInfo(" ==> error in first char");
			return null;
		}
		Object lcObj = fontData.get("LastChar");
		if (!(lcObj instanceof Number)) {
			if (DEBUG_LOAD_FONTS) {
				System.out.println("Problem in font " + fontData);
				System.out.println(" --> strange last char: " + lcObj);
			}
			pm.setInfo(" ==> error in last char");
			return null;
		}
		Object cwObj = PdfParser.dereference(fontData.get("Widths"), objects);
		if (!(cwObj instanceof List)) {
			if (DEBUG_LOAD_FONTS) {
				System.out.println("Problem in font " + fontData);
				System.out.println(" --> strange char widths: " + cwObj);
			}
			pm.setInfo(" ==> error in char widths");
			return null;
		}
		
		Object feObj = PdfParser.dereference(fontData.get("Encoding"), objects);
		if (feObj instanceof Map)
			feObj = PdfParser.dereference(((Map) feObj).get("BaseEncoding"), objects);
		else if ((feObj == null) && (fdObj instanceof Map))
			feObj = PdfParser.dereference(((Map) fdObj).get("Encoding"), objects);
		
		Map fd = ((Map) fdObj);
		final int fc = ((Number) fcObj).intValue();
		final int lc = ((Number) lcObj).intValue();
		List cwv = ((List) cwObj);
		if (cwv.size() != (lc - fc + 1)) {
			if (DEBUG_LOAD_FONTS) {
				System.out.println("Problem in font " + fontData);
				System.out.println(" --> invalid char widths");
			}
			pm.setInfo(" ==> error in char widths");
			return null;
		}
		final float[] cws = new float[lc - fc + 1];
		for (int c = 0; c < cws.length; c++) {
			Object cwo = cwv.get(c);
			cws[c] = ((cwo instanceof Number) ? (((Number) cwo).floatValue() * normFactor) : 0);
		}
		Object mcwObj = ((fd == null) ? null : fd.get("MissingWidth"));
		float mcw = ((mcwObj instanceof Number) ? ((Number) mcwObj).floatValue() : 0);
		
		PdfFont pFont = new PdfFont(fontData, fd, fc, lc, cws, mcw, fnObj.toString(), ((feObj == null) ? null : feObj.toString()));
		if (!addLineMetrics(pFont, ((fmd < 0) ? -normFactor : normFactor), pm))
			return null;
		if (DEBUG_LOAD_FONTS) System.out.println("Type3 font created");
		
//		pFont.type = 3;
		pFont.hasDescent = (pFont.descent < -0.150);
		
		for (Iterator cmit = diffEncodings.keySet().iterator(); cmit.hasNext();) {
			Integer chc = ((Integer) cmit.next());
			pFont.mapDifference(chc, ((Character) diffEncodings.get(chc)), ((String) diffEncodingNames.get(chc)));
		}
		
		if (toUnicodeMappings != null)
			for (Iterator cmit = toUnicodeMappings.keySet().iterator(); cmit.hasNext();) {
				Integer chc = ((Integer) cmit.next());
				pFont.mapUnicode(chc, ((String) toUnicodeMappings.get(chc)), true);
			}
		
		//	TODO pass charset to decoder, but union with Unicode mapped chars first
		pFont.setCharDecoder(new CharDecoder() {
			public void decodeChars(PdfFont font, FontDecoderCharset charSet, ProgressMonitor pm) throws IOException {
				pm.setInfo(" - decoding characters from char programs");
				if (DEBUG_LOAD_FONTS) System.out.println("Got char procs: " + cpsObj);
				Font serifFont = PdfFontDecoder.getSerifFont();
				Font[] serifFonts = new Font[4];
				Font sansFont = PdfFontDecoder.getSansSerifFont();
				Font[] sansFonts = new Font[4];
				Font monoFont = PdfFontDecoder.getSansSerifFont();
				Font[] monoFonts = new Font[4];
				for (int s = Font.PLAIN; s <= (Font.BOLD | Font.ITALIC); s++) {
					serifFonts[s] = serifFont.deriveFont(s);
					sansFonts[s] = sansFont.deriveFont(s);
					monoFonts[s] = monoFont.deriveFont(s);
				}
				Map cps = ((Map) cpsObj);
				HashMap cache = ((diffEncodings.size() < 10) ? null : new HashMap());
				boolean hasDescent = false;
				for (Iterator eit = diffEncodings.keySet().iterator(); eit.hasNext();) {
					Integer charCode = ((Integer) eit.next());
					Object charName = diffEncodingNames.get(charCode);
					if (!font.usesCharCode(charCode)) {
						pm.setInfo("   - ignoring unused char " + charName);
						continue;
					}
					Object cpObj = PdfParser.dereference(cps.get(charName), objects);
					if (cpObj instanceof PStream) {
						pm.setInfo("   - decoding char " + charName);
						if (DEBUG_LOAD_FONTS) System.out.println("  Got char proc for '" + charName + "': " + ((PStream) cpObj).params);
						char cpChar = PdfCharDecoder.getChar(font, ((PStream) cpObj), charCode, charName.toString(), charSet, objects, serifFonts, sansFonts, monoFonts, cache, false);
						if (cpChar != 0) {
							font.mapUnicode(charCode, ("" + cpChar), false);
							if (((charCode.intValue() - fc) >= 0) && ((charCode.intValue() - fc) < cws.length))
								font.setCharWidth(charCode, cws[charCode.intValue() - fc]);
							hasDescent = (hasDescent || ("Qgjpqy".indexOf(StringUtils.getBaseChar(cpChar)) != -1));
						}
//						//	TODO obtain and store font size relative baseline shift
//						pFont.setRelativeBaselineShift(bsCh, (((float) bestSim.match.shiftBelowBaseline) / bestSim.match.fontSize));
					}
					else if (DEBUG_LOAD_FONTS) System.out.println(" --> strange char proc for '" + charName + "': " + cpObj);
					
					//	TODO also observe FontFile2 field
				}
				font.hasDescent = (hasDescent || (font.descent < -0.150));
				font.type = "Type3";
			}
		});
		
		pm.setInfo(" ==> font loaded");
		return pFont;
	}
	
	private static PdfFont readFontType1(Map fontData, final Map objects, Map charDecoders, ProgressMonitor pm) throws IOException {
		pm.setInfo("Loading type 1 font");
		Object fnObj = PdfParser.dereference(fontData.get("BaseFont"), objects);
		if (fnObj == null) {
			if (DEBUG_LOAD_FONTS) {
				System.out.println("Problem in font " + fontData);
				System.out.println(" --> missing font name");
			}
			pm.setInfo(" ==> font name missing");
			return null;
		}
		
		boolean isSymbolic = false;
		Object fdObj = PdfParser.dereference(fontData.get("FontDescriptor"), objects);
		if (fdObj instanceof Map) {
			Object fObj = ((Map) fdObj).get("Flags");
			if (fObj != null)
				isSymbolic = (fObj.toString().indexOf("3") != -1);
		}
		
		BaseFont baseFont = getBuiltInFont(fnObj, isSymbolic);
		boolean isBuiltInFont = true;
		
		HashMap toUnicodeMappings = null;
		if (fdObj instanceof Map) {
			if (DEBUG_LOAD_FONTS) System.out.println("Got font descriptor: " + fdObj);
			if (baseFont != null)
				for (Iterator kit = baseFont.descriptor.keySet().iterator(); kit.hasNext();) {
					Object key = kit.next();
					if (!((Map) fdObj).containsKey(key))
						((Map) fdObj).put(key, baseFont.descriptor.get(key));
				}
			Object tuObj = PdfParser.dereference(fontData.get("ToUnicode"), objects);
			if (tuObj instanceof PStream) {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				Object filter = ((PStream) tuObj).params.get("Filter");
				try {
					PdfParser.decode(((filter == null) ? "FlateDecode" : filter), ((PStream) tuObj).bytes, ((PStream) tuObj).params, baos, objects);
				}
				catch (Exception e) {
					if (filter == null)
						PdfParser.decode(null, ((PStream) tuObj).bytes, ((PStream) tuObj).params, baos, objects);
					else if (e instanceof IOException)
						throw ((IOException) e);
					else if (e instanceof RuntimeException)
						throw ((RuntimeException) e);
				}
				byte[] tuMapData = baos.toByteArray();
				if (DEBUG_LOAD_FONTS)
					System.out.println(" --> to unicode: " + new String(tuMapData));
				pm.setInfo(" - got Unicode mapping");
				toUnicodeMappings = readToUnicodeCMap(tuMapData, true);
			}
			else if (DEBUG_LOAD_FONTS) System.out.println(" --> to unicode: " + tuObj);
		}
		else {
			if (baseFont == null) {
				if (DEBUG_LOAD_FONTS) {
					System.out.println("Problem in font " + fontData);
					System.out.println(" --> strange font descriptor: " + fdObj);
				}
				Object dfaObj = fontData.get("DescendantFonts");
				if (dfaObj instanceof List) {
					Object dfObj = PdfParser.dereference(((List) dfaObj).get(0), objects);
					if (dfObj != null) {
						if (DEBUG_LOAD_FONTS) System.out.println(" --> descendant fonts: " + dfObj);
					}
				}
				Object tuObj = PdfParser.dereference(fontData.get("ToUnicode"), objects);
				if (tuObj instanceof PStream) {
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					Object filter = ((PStream) tuObj).params.get("Filter");
					try {
						PdfParser.decode(((filter == null) ? "FlateDecode" : filter), ((PStream) tuObj).bytes, ((PStream) tuObj).params, baos, objects);
					}
					catch (Exception e) {
						if (filter == null)
							PdfParser.decode(null, ((PStream) tuObj).bytes, ((PStream) tuObj).params, baos, objects);
						else if (e instanceof IOException)
							throw ((IOException) e);
						else if (e instanceof RuntimeException)
							throw ((RuntimeException) e);
					}
					byte[] tuMapData = baos.toByteArray();
					if (DEBUG_LOAD_FONTS)
						System.out.println(" --> to unicode: " + new String(tuMapData));
					toUnicodeMappings = readToUnicodeCMap(tuMapData, true);
				}
				else if (DEBUG_LOAD_FONTS) System.out.println(" --> to unicode: " + tuObj);
				return null;
			}
			else fdObj = baseFont.descriptor;
			if (DEBUG_LOAD_FONTS) System.out.println("Got font descriptor: " + fdObj);
		}
		
		Object fcObj = fontData.get("FirstChar");
		if (fcObj instanceof Number)
			isBuiltInFont = false;
		else {
			if (baseFont == null) {
				if (DEBUG_LOAD_FONTS) {
					System.out.println("Problem in font " + fontData);
					System.out.println(" --> strange first char: " + fcObj);
				}
				pm.setInfo(" ==> error in first char");
				return null;
			}
			else fcObj = new Integer(baseFont.firstChar);
		}
		Object lcObj = fontData.get("LastChar");
		if (lcObj instanceof Number)
			isBuiltInFont = false;
		else {
			if (baseFont == null) {
				if (DEBUG_LOAD_FONTS) {
					System.out.println("Problem in font " + fontData);
					System.out.println(" --> strange last char: " + lcObj);
				}
				pm.setInfo(" ==> error in last char");
				return null;
			}
			else lcObj = new Integer(baseFont.lastChar);
		}
		Object cwObj = PdfParser.dereference(fontData.get("Widths"), objects);
		if (!(cwObj instanceof List)) {
			if (DEBUG_LOAD_FONTS) {
				System.out.println("Problem in font " + fontData);
				System.out.println(" --> strange char widths: " + cwObj);
			}
			cwObj = null;
		}
		
		Object feObj = PdfParser.dereference(fontData.get("Encoding"), objects);
		HashMap diffEncodings = new HashMap();
		HashMap diffEncodingNames = new HashMap();
		final HashMap resolvedCodes = new HashMap();
		HashMap unresolvedDiffEncodings = new HashMap();
		final HashMap unresolvedCodes = new HashMap();
		if (feObj instanceof Map) {
			if (DEBUG_LOAD_FONTS) System.out.println("Got encoding: " + feObj);
			Object dObj = PdfParser.dereference(((Map) feObj).get("Differences"), objects);
			if (dObj != null) {
				if (DEBUG_LOAD_FONTS) System.out.println("Got differences: " + dObj);
				if (dObj instanceof List) {
					List diffs = ((List) dObj);
					int nextCode = -1;
					for (int d = 0; d < diffs.size(); d++) {
						Object diff = diffs.get(d);
						if (diff instanceof Number)
							nextCode = ((Number) diff).intValue();
						else if (nextCode != -1) {
							Integer code = new Integer(nextCode++);
							String diffCharName = diff.toString();
							diffEncodingNames.put(code, diffCharName);
							char diffChar = StringUtils.getCharForName(diffCharName);
							if ((diffChar == 0) && (toUnicodeMappings != null) && toUnicodeMappings.containsKey(code)) {
								String tuc = ((String) toUnicodeMappings.get(code));
								diffChar = tuc.charAt(0);
							}
							if (diffChar != 0) {
								diffEncodings.put(code, new Character(diffChar));
								resolvedCodes.put(diffCharName, code);
							}
							else if ((toUnicodeMappings == null) || !toUnicodeMappings.containsKey(code)) {
								unresolvedDiffEncodings.put(code, diffCharName);
								unresolvedCodes.put(diffCharName, code);
							}
						}
					}
					isBuiltInFont = false;
				}
				else {
					if (DEBUG_LOAD_FONTS) {
						System.out.println("Problem in font " + fontData);
						System.out.println(" --> strange differences definition: " + dObj);
					}
					pm.setInfo(" ==> error in differences descriptor");
					return null;
				}
				if (DEBUG_LOAD_FONTS) {
					System.out.print("Got mappings: {");
					for (Iterator dit = diffEncodings.keySet().iterator(); dit.hasNext();) {
						Integer d = ((Integer) dit.next());
						Character c = ((Character) diffEncodings.get(d));
						System.out.print(d + "=" + c.charValue() + "(" + StringUtils.getNormalForm(c.charValue()) + ")");
						if (dit.hasNext())
							System.out.print(", ");
					}
					System.out.println("}");
					System.out.println("Resolved codes are " + resolvedCodes);
					if (unresolvedDiffEncodings.size() != 0) {
						System.out.print("Still to map: {");
						for (Iterator dit = unresolvedDiffEncodings.keySet().iterator(); dit.hasNext();) {
							Integer d = ((Integer) dit.next());
							String cn = ((String) unresolvedDiffEncodings.get(d));
							System.out.print(d + "=" + cn);
							if (dit.hasNext())
								System.out.print(", ");
						}
						System.out.println("}");
					}
				}
			}
			
			//	get encoding name
			feObj = PdfParser.dereference(((Map) feObj).get("BaseEncoding"), objects);
		}
		if ((feObj == null) && (fdObj instanceof Map))
			feObj = PdfParser.dereference(((Map) fdObj).get("Encoding"), objects);
		
		final Map fd = ((Map) fdObj);
		int fc = ((Number) fcObj).intValue();
		int lc = ((Number) lcObj).intValue();
		float[] cws;
		if (cwObj == null)
			cws = null;
		else {
			List cwv = ((List) cwObj);
			if (cwv.size() != (lc - fc + 1)) {
				if (DEBUG_LOAD_FONTS) {
					System.out.println("Problem in font " + fontData);
					System.out.println(" --> invalid char widths");
				}
				pm.setInfo(" ==> error in char widths");
				return null;
			}
			cws = new float[lc - fc + 1];
			for (int c = 0; c < cws.length; c++) {
				Object cwo = cwv.get(c);
				cws[c] = ((cwo instanceof Number) ? ((Number) cwo).floatValue() : 0);
			}
		}
		Object mcwObj = fd.get("MissingWidth");
		float mcw = ((mcwObj instanceof Number) ? ((Number) mcwObj).floatValue() : 0);
		if ((mcw == 0) && (DEBUG_LOAD_FONTS)) {
			System.out.println("Problem in font " + fontData);
			System.out.println(" --> invalid missing char width");
		}
		float scw = 0;
		
		if ((fc > 32) && (mcw == 0)) {
			Object csObj = fd.get("CharSet");
			if (DEBUG_LOAD_FONTS)
				System.out.println(" --> char set is " + csObj);
			if ((csObj instanceof PString) && (((PString) csObj).toString().indexOf("/space") == -1) && (cws != null)) {
				float wSum = 0;
				int wCount = 0;
				for (int c = 0; c < cws.length; c++)
					if (cws[c] != 0) {
						wSum += cws[c];
						wCount++;
					}
				if (wCount != 0)
					scw = ((wSum * 2) / (wCount * 3));
				if (DEBUG_LOAD_FONTS)
					System.out.println(" --> space width interpolated as " + scw);
			}
		}
		
		final PdfFont pFont = new PdfFont(fontData, fd, fc, lc, cws, mcw, fnObj.toString(), ((feObj == null) ? null : feObj.toString()));
		if (!addLineMetrics(pFont, 0, pm))
			return null;
		if (DEBUG_LOAD_FONTS) System.out.println("Type1 font created");
		
		for (Iterator cmit = diffEncodings.keySet().iterator(); cmit.hasNext();) {
			Integer chc = ((Integer) cmit.next());
			pFont.mapDifference(chc, ((Character) diffEncodings.get(chc)), ((String) diffEncodingNames.get(chc)));
		}
		for (Iterator cmit = diffEncodingNames.keySet().iterator(); cmit.hasNext();) {
			Integer chc = ((Integer) cmit.next());
			pFont.mapDifference(chc, ((Character) diffEncodings.get(chc)), ((String) diffEncodingNames.get(chc)));
		}
		pFont.setBaseFont(baseFont);
		
		if (toUnicodeMappings != null) {
			for (Iterator cmit = toUnicodeMappings.keySet().iterator(); cmit.hasNext();) {
				Integer chc = ((Integer) cmit.next());
				pFont.mapUnicode(chc, ((String) toUnicodeMappings.get(chc)), true);
			}
			isBuiltInFont = false;
		}
		
		if (scw != 0) {
			pFont.setCharWidth(new Character(' '), scw);
			pFont.setCharWidth(new Integer((int) ' '), scw);
		}
		
		final Object ffRefObj = fd.get("FontFile");
		final Object ffObj = PdfParser.dereference(fd.get("FontFile"), objects);
		if (ffObj instanceof PStream) {
//			pFont.setCharDecoder(new CharDecoder() {
//				public void decodeChars(PdfFont font, FontDecoderCharset charSet, ProgressMonitor pm) throws IOException {
//					System.out.println("Got font file 1 from " + pFont.name + " (T1, " + pFont + ")");
//					System.out.println("  --> params are " + ((PStream) ffObj).params);
//					Object filter = ((PStream) ffObj).params.get("Filter");
//					System.out.println("  --> filter is " + filter);
//					ByteArrayOutputStream baos = new ByteArrayOutputStream();
//					PdfParser.decode(filter, ((PStream) ffObj).bytes, ((PStream) ffObj).params, baos, objects);
////					System.out.println(new String(baos.toByteArray()));
//					pm.setInfo(" - reading type 1 char definitions");
//					PdfFontDecoder.readFontType1(baos.toByteArray(), ((PStream) ffObj).params, fd, font, this, charSet, pm);
//				}
//			});
			CharDecoder chd = ((CharDecoder) charDecoders.get(ffRefObj.toString()));
			if (chd == null) {
				chd = new CharDecoder() {
					public void decodeChars(PdfFont font, FontDecoderCharset charSet, ProgressMonitor pm) throws IOException {
						System.out.println("Got font file 1 from " + pFont.name + " (T1, " + pFont + ")");
						System.out.println("  --> params are " + ((PStream) ffObj).params);
						Object filter = ((PStream) ffObj).params.get("Filter");
						System.out.println("  --> filter is " + filter);
						ByteArrayOutputStream baos = new ByteArrayOutputStream();
						PdfParser.decode(filter, ((PStream) ffObj).bytes, ((PStream) ffObj).params, baos, objects);
//						System.out.println(new String(baos.toByteArray()));
						pm.setInfo(" - reading type 1 char definitions");
						PdfFontDecoder.readFontType1(baos.toByteArray(), ((PStream) ffObj).params, fd, font, this, charSet, pm);
						font.type = "Type1";
					}
				};
				charDecoders.put(ffRefObj.toString(), chd);
				if (DEBUG_LOAD_FONTS) System.out.println(" - created char decoder for " + ffRefObj.toString());
			}
			else if (DEBUG_LOAD_FONTS) System.out.println(" - cache hit for char decoder " + ffRefObj.toString());
			pFont.setCharDecoder(chd);
			isBuiltInFont = false;
		}
		
		//	add special mappings from font file
		final Object ff3RefObj = fd.get("FontFile3");
		final Object ff3Obj = PdfParser.dereference(fd.get("FontFile3"), objects);
		if (ff3Obj instanceof PStream) {
//			pFont.setCharDecoder(new CharDecoder() {
//				public void decodeChars(PdfFont font, FontDecoderCharset charSet, ProgressMonitor pm) throws IOException {
//					System.out.println("Got font file 3 from " + pFont.name + " (T1C, " + pFont + ")");
//					System.out.println("  --> params are " + ((PStream) ff3Obj).params);
//					Object filter = ((PStream) ff3Obj).params.get("Filter");
//					System.out.println("  --> filter is " + filter);
//					ByteArrayOutputStream baos = new ByteArrayOutputStream();
//					PdfParser.decode(filter, ((PStream) ff3Obj).bytes, ((PStream) ff3Obj).params, baos, objects);
//					Object stObj = ((PStream) ff3Obj).params.get("Subtype");
//					if ((stObj != null) && "Type1C".equals(stObj.toString())) {
//						pm.setInfo(" - reading type 1c char definitions");
//						PdfFontDecoder.readFontType1C(baos.toByteArray(), fd, font, this, resolvedCodes, unresolvedCodes, charSet, pm);
//					}
//				}
//			});
			CharDecoder chd = ((CharDecoder) charDecoders.get(ff3RefObj.toString()));
			if (chd == null) {
				chd = new CharDecoder() {
					public void decodeChars(PdfFont font, FontDecoderCharset charSet, ProgressMonitor pm) throws IOException {
						System.out.println("Got font file 3 from " + pFont.name + " (T1C, " + pFont + ")");
						System.out.println("  --> params are " + ((PStream) ff3Obj).params);
						Object filter = ((PStream) ff3Obj).params.get("Filter");
						System.out.println("  --> filter is " + filter);
						ByteArrayOutputStream baos = new ByteArrayOutputStream();
						PdfParser.decode(filter, ((PStream) ff3Obj).bytes, ((PStream) ff3Obj).params, baos, objects);
						Object stObj = ((PStream) ff3Obj).params.get("Subtype");
						if ((stObj != null) && "Type1C".equals(stObj.toString())) {
							pm.setInfo(" - reading type 1c char definitions");
							PdfFontDecoder.readFontType1C(baos.toByteArray(), fd, font, this, resolvedCodes, unresolvedCodes, charSet, pm);
						}
						font.type = "Type1C";
					}
				};
				charDecoders.put(ff3RefObj.toString(), chd);
				if (DEBUG_LOAD_FONTS) System.out.println(" - created char decoder for " + ff3RefObj.toString());
			}
			else if (DEBUG_LOAD_FONTS) System.out.println(" - cache hit for char decoder " + ff3RefObj.toString());
			pFont.setCharDecoder(chd);
			isBuiltInFont = false;
		}
		
		if (isBuiltInFont)
			pFont.setIsBuiltInFont();
		
		pm.setInfo(" ==> font loaded");
		return pFont;
	}
	
	private static final int convertUnsigned(byte b) {
		return ((b < 0) ? (((int) b) + 256) : b);
	}
	
	private static PdfFont readFontTrueType(Map fontData, final Map objects, Map charDecoders, ProgressMonitor pm) throws IOException {
		Object fnObj = PdfParser.dereference(fontData.get("BaseFont"), objects);
		if (fnObj == null) {
			if (DEBUG_LOAD_FONTS) {
				System.out.println("Problem in font " + fontData);
				System.out.println(" --> missing font name");
			}
			return null;
		}
		if (DEBUG_LOAD_FONTS) System.out.println(" - font data: " + fontData);
		
		boolean isSymbolic = false;
		Object fdObj = PdfParser.dereference(fontData.get("FontDescriptor"), objects);
		if (fdObj instanceof Map) {
			Object fObj = ((Map) fdObj).get("Flags");
			if (fObj != null)
				isSymbolic = (fObj.toString().indexOf("3") != -1);
		}
		if (DEBUG_LOAD_FONTS) System.out.println(" - font descriptor: " + fdObj);
		
		BaseFont baseFont = getBuiltInFont(fnObj, isSymbolic);
		
		if (fdObj instanceof Map) {
			if (baseFont != null)
				for (Iterator kit = baseFont.descriptor.keySet().iterator(); kit.hasNext();) {
					Object key = kit.next();
					if (!((Map) fdObj).containsKey(key))
						((Map) fdObj).put(key, baseFont.descriptor.get(key));
				}
		}
		else {
			if (baseFont == null) {
				if (DEBUG_LOAD_FONTS) {
					System.out.println("Problem in font " + fontData);
					System.out.println(" --> strange font descriptor: " + fdObj);
				}
				Object dfaObj = fontData.get("DescendantFonts");
				if (dfaObj instanceof List) {
					Object dfObj = PdfParser.dereference(((List) dfaObj).get(0), objects);
					if (dfObj != null) {
						if (DEBUG_LOAD_FONTS) System.out.println(" --> descendant fonts: " + dfObj);
					}
				}
				Object tuObj = PdfParser.dereference(fontData.get("ToUnicode"), objects);
				if (tuObj instanceof PStream)
					PdfParser.decodeObjectStream(null, ((PStream) tuObj), objects, true, null);
				else if (DEBUG_LOAD_FONTS) System.out.println(" --> to unicode: " + tuObj);
				return null;
			}
			else fdObj = baseFont.descriptor;
		}
		
		HashMap toUnicodeMappings = null;
		Object tuObj = PdfParser.dereference(fontData.get("ToUnicode"), objects);
		if (tuObj instanceof PStream) {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			Object filter = ((PStream) tuObj).params.get("Filter");
			try {
				PdfParser.decode(((filter == null) ? "FlateDecode" : filter), ((PStream) tuObj).bytes, ((PStream) tuObj).params, baos, objects);
			}
			catch (Exception e) {
				if (filter == null)
					PdfParser.decode(null, ((PStream) tuObj).bytes, ((PStream) tuObj).params, baos, objects);
				else if (e instanceof IOException)
					throw ((IOException) e);
				else if (e instanceof RuntimeException)
					throw ((RuntimeException) e);
			}
			byte[] tuMapData = baos.toByteArray();
			if (DEBUG_LOAD_FONTS)
				System.out.println(" --> to unicode: " + new String(tuMapData));
			toUnicodeMappings = readToUnicodeCMap(tuMapData, true);
		}
		else if (DEBUG_LOAD_FONTS) System.out.println(" --> to unicode: " + tuObj);
		
		Object fcObj = fontData.get("FirstChar");
		if (!(fcObj instanceof Number)) {
			if (baseFont == null) {
				if (DEBUG_LOAD_FONTS) {
					System.out.println("Problem in font " + fontData);
					System.out.println(" --> strange first char: " + fcObj);
				}
				return null;
			}
			else fcObj = new Integer(baseFont.firstChar);
		}
		Object lcObj = fontData.get("LastChar");
		if (!(lcObj instanceof Number)) {
			if (baseFont == null) {
				if (DEBUG_LOAD_FONTS) {
					System.out.println("Problem in font " + fontData);
					System.out.println(" --> strange last char: " + lcObj);
				}
				return null;
			}
			else lcObj = new Integer(baseFont.lastChar);
		}
		Object cwObj = PdfParser.dereference(fontData.get("Widths"), objects);
		if (!(cwObj instanceof List)) {
			if (baseFont == null) {
				if (DEBUG_LOAD_FONTS) {
					System.out.println("Problem in font " + fontData);
					System.out.println(" --> strange char widths: " + cwObj);
				}
				return null;
			}
			else {
				cwObj = new ArrayList();
				for (int c = 0; c < baseFont.charWidths.length; c++)
					((List) cwObj).add(new Float(baseFont.charWidths[c]));
			}
		}
		
		Object feObj = PdfParser.dereference(fontData.get("Encoding"), objects);
		HashMap diffEncodings = new HashMap();
		HashMap diffEncodingNames = new HashMap();
		if (feObj instanceof Map) {
			if (DEBUG_LOAD_FONTS) System.out.println("Got encoding: " + feObj);
			Object dObj = PdfParser.dereference(((Map) feObj).get("Differences"), objects);
			if (dObj != null) {
				if (DEBUG_LOAD_FONTS) System.out.println("Got differences: " + dObj);
				if (dObj instanceof List) {
					List diffs = ((List) dObj);
					int code = -1;
					for (int d = 0; d < diffs.size(); d++) {
						Object diff = diffs.get(d);
						if (diff instanceof Number)
							code = ((Number) diff).intValue();
						else if (code != -1) {
							char diffCh = StringUtils.getCharForName(diff.toString());
							if (diffCh != 0) {
								diffEncodings.put(new Integer(code), new Character(diffCh));
								diffEncodingNames.put(new Integer(code), diff.toString());
							}
							code++;
						}
					}
				}
				else {
					if (DEBUG_LOAD_FONTS) {
						System.out.println("Problem in font " + fontData);
						System.out.println(" --> strange differences definition: " + dObj);
					}
					return null;
				}
				if (DEBUG_LOAD_FONTS) {
					System.out.print("Got mappings: {");
					for (Iterator dit = diffEncodings.keySet().iterator(); dit.hasNext();) {
						Integer d = ((Integer) dit.next());
						Character c = ((Character) diffEncodings.get(d));
						System.out.print(d + "=" + c.charValue() + "(" + StringUtils.getNormalForm(c.charValue()) + ")");
						if (dit.hasNext())
							System.out.print(", ");
					}
					System.out.println("}");
				}
			}
			
			//	get encoding name
			feObj = PdfParser.dereference(((Map) feObj).get("BaseEncoding"), objects);
		}
		if ((feObj == null) && (fdObj instanceof Map))
			feObj = PdfParser.dereference(((Map) fdObj).get("Encoding"), objects);
		
		Map fd = ((Map) fdObj);
		int fc = ((Number) fcObj).intValue();
		int lc = ((Number) lcObj).intValue();
		List cwv = ((List) cwObj);
		if (cwv.size() != (lc - fc + 1)) {
			if (DEBUG_LOAD_FONTS) {
				System.out.println("Problem in font " + fontData);
				System.out.println(" --> invalid char widths");
			}
			return null;
		}
		float[] cws = new float[lc - fc + 1];
		for (int c = 0; c < cws.length; c++) {
			Object cwo = cwv.get(c);
			cws[c] = ((cwo instanceof Number) ? ((Number) cwo).floatValue() : 0);
		}
		Object mcwObj = fd.get("MissingWidth");
		float mcw = ((mcwObj instanceof Number) ? ((Number) mcwObj).floatValue() : 0);
		
		final PdfFont pFont = new PdfFont(fontData, fd, fc, lc, cws, mcw, fnObj.toString(), ((feObj == null) ? null : feObj.toString()));
		if (!addLineMetrics(pFont, 0, pm))
			return null;
		if (DEBUG_LOAD_FONTS) System.out.println("TrueType font created");
		
		for (Iterator cmit = diffEncodings.keySet().iterator(); cmit.hasNext();) {
			Integer chc = ((Integer) cmit.next());
			pFont.mapDifference(chc, ((Character) diffEncodings.get(chc)), ((String) diffEncodingNames.get(chc)));
		}
		
		pFont.setBaseFont(baseFont);
		
		if (toUnicodeMappings != null)
			for (Iterator cmit = toUnicodeMappings.keySet().iterator(); cmit.hasNext();) {
				Integer chc = ((Integer) cmit.next());
				pFont.mapUnicode(chc, ((String) toUnicodeMappings.get(chc)), true);
			}
		
		//	add special mappings from font file (only after assessing which chars are actually used, however)
		final Object ff2RefObj = fd.get("FontFile2");
		final Object ff2Obj = PdfParser.dereference(fd.get("FontFile2"), objects);
		if (DEBUG_LOAD_FONTS) System.out.println("Got Font File 2: " + ff2Obj);
		if (ff2Obj instanceof PStream) {
//			pFont.setCharDecoder(new CharDecoder() {
//				public void decodeChars(PdfFont font, FontDecoderCharset charSet, ProgressMonitor pm) throws IOException {
//					System.out.println("Got font file 2 from " + pFont.name + " (TT, " + pFont + ")");
//					System.out.println("  --> argument font is " + font.name + " (" + font + ")");
//					System.out.println("  --> base font mode is " + (font != pFont));
//					System.out.println("  --> params are " + ((PStream) ff2Obj).params);
//					Object filter = ((PStream) ff2Obj).params.get("Filter");
//					System.out.println("  --> filter is " + filter);
//					ByteArrayOutputStream baos = new ByteArrayOutputStream();
//					PdfParser.decode(filter, ((PStream) ff2Obj).bytes, ((PStream) ff2Obj).params, baos, objects);
//					byte[] ffBytes = baos.toByteArray();
//					Object l1Obj = ((PStream) ff2Obj).params.get("Length1");
//					System.out.println("  --> length is " + ffBytes.length + ", length1 is " + l1Obj);
//					PdfFontDecoder.readFontTrueType(ffBytes, font, this, (font != pFont), null, charSet, pm);
//				}
//			});
			CharDecoder chd = ((CharDecoder) charDecoders.get(ff2RefObj.toString()));
			if (chd == null) {
				chd = new CharDecoder() {
					public void decodeChars(PdfFont font, FontDecoderCharset charSet, ProgressMonitor pm) throws IOException {
						System.out.println("Got font file 2 from " + pFont.name + " (TT, " + pFont + ")");
						System.out.println("  --> argument font is " + font.name + " (" + font + ")");
						System.out.println("  --> base font mode is " + (font != pFont));
						System.out.println("  --> params are " + ((PStream) ff2Obj).params);
						Object filter = ((PStream) ff2Obj).params.get("Filter");
						System.out.println("  --> filter is " + filter);
						ByteArrayOutputStream baos = new ByteArrayOutputStream();
						PdfParser.decode(filter, ((PStream) ff2Obj).bytes, ((PStream) ff2Obj).params, baos, objects);
						byte[] ffBytes = baos.toByteArray();
						Object l1Obj = ((PStream) ff2Obj).params.get("Length1");
						System.out.println("  --> length is " + ffBytes.length + ", length1 is " + l1Obj);
						PdfFontDecoder.readFontTrueType(ffBytes, font, this, (font != pFont), null, charSet, pm);
						font.type = "TrueType";
					}
				};
				charDecoders.put(ff2RefObj.toString(), chd);
				if (DEBUG_LOAD_FONTS) System.out.println(" - created char decoder for " + ff2RefObj.toString());
			}
			else if (DEBUG_LOAD_FONTS) System.out.println(" - cache hit for char decoder " + ff2RefObj.toString());
			pFont.setCharDecoder(chd);
		}
		
		return pFont;
	}
	
	private static boolean addLineMetrics(PdfFont pFont, float fbbNormFactor, ProgressMonitor pm) {
		
		//	read font bounding box (might be in main font data in Type3 fonts)
		Object fbbObj = ((pFont.descriptor == null) ? pFont.data : pFont.descriptor).get("FontBBox");
		Number fbbLlX = null;
		Number fbbLlY = null;
		Number fbbUrX = null;
		Number fbbUrY = null;
		if ((fbbObj instanceof List) && (((List) fbbObj).size() == 4)) {
			if (((List) fbbObj).get(0) instanceof Number)
				fbbLlX = ((Number) ((List) fbbObj).get(0));
			if (((List) fbbObj).get(1) instanceof Number)
				fbbLlY = ((Number) ((List) fbbObj).get(1));
			if (((List) fbbObj).get(2) instanceof Number)
				fbbUrX = ((Number) ((List) fbbObj).get(2));
			if (((List) fbbObj).get(3) instanceof Number)
				fbbUrY = ((Number) ((List) fbbObj).get(3));
		}
		else {
			if (DEBUG_LOAD_FONTS) {
				System.out.println("Problem in font " + pFont.data);
				System.out.println(" - descriptor: " + pFont.descriptor);
				System.out.println(" --> strange bounding box: " + fbbObj);
			}
			fbbObj = null;
		}
		
		//	check if font bounding box required but erroneous
		if ((fbbNormFactor != 0) && ((fbbLlX == null) || (fbbLlY == null) || (fbbUrX == null) || (fbbUrY == null))) {
			pm.setInfo(" ==> error in font box");
			return false;
		}
		
		//	read ascent
		Number ascent = getNumber(pFont, "Ascent");
		Number ascender = getNumber(pFont, "Ascender");
		if (((ascent == null) || (ascent.floatValue() == 0)) && ((ascender == null) || (ascender.floatValue() == 0))) {
			if (DEBUG_LOAD_FONTS) {
				System.out.println("Problem in font " + pFont.data);
				System.out.println(" - descriptor: " + pFont.descriptor);
				System.out.println(" --> strange ascent and ascender: " + ((pFont.descriptor == null) ? pFont.data : pFont.descriptor).get("Ascent") + ", " + ((pFont.descriptor == null) ? pFont.data : pFont.descriptor).get("Ascender"));
			}
			if ((fbbUrY != null) && (0 <= fbbNormFactor)) {
				ascent = (((fbbNormFactor == 0) || (fbbNormFactor == 1)) ? fbbUrY : new Float(fbbUrY.floatValue() * fbbNormFactor));
				if (DEBUG_LOAD_FONTS)
					System.out.println(" --> fallback from bounding box: " + ascent);
			}
			else if ((fbbLlY != null) && (fbbNormFactor < 0)) {
				ascent = new Float(fbbLlY.floatValue() * fbbNormFactor);
				if (DEBUG_LOAD_FONTS)
					System.out.println(" --> fallback from bounding box: " + ascent);
			}
			else {
				if (DEBUG_LOAD_FONTS)
					System.out.println(" --> could not determine ascent");
				return false;
			}
		}
		
		//	read descent
		Number descent = getNumber(pFont, "Descent");
		Number descender = getNumber(pFont, "Descender");
		if ((descent == null) && (descender == null)) {
			if (DEBUG_LOAD_FONTS) {
				System.out.println("Problem in font " + pFont.data);
				System.out.println(" - descriptor: " + pFont.descriptor);
				System.out.println(" --> strange descent and descender: " + ((pFont.descriptor == null) ? pFont.data : pFont.descriptor).get("Descent") + ", " + ((pFont.descriptor == null) ? pFont.data : pFont.descriptor).get("Descender"));
			}
			if ((fbbLlY != null) && (0 <= fbbNormFactor)) {
				descent = (((fbbNormFactor == 0) || (fbbNormFactor == 1)) ? fbbLlY : new Float(fbbLlY.floatValue() * fbbNormFactor));
				if (DEBUG_LOAD_FONTS)
					System.out.println(" --> fallback from bounding box: " + descent);
			}
			else if ((fbbUrY != null) && (fbbNormFactor < 0)) {
				descent = new Float(fbbUrY.floatValue() * fbbNormFactor);
				if (DEBUG_LOAD_FONTS)
					System.out.println(" --> fallback from bounding box: " + descent);
			}
			else {
				if (DEBUG_LOAD_FONTS)
					System.out.println(" --> could not determine descent");
				return false;
			}
		}
		
		//	read cap height
		Number capHeight = getNumber(pFont, "CapHeight");
		if ((capHeight == null) || (capHeight.floatValue() == 0)) {
			if (DEBUG_LOAD_FONTS) {
				System.out.println("Problem in font " + pFont.data);
				System.out.println(" - descriptor: " + pFont.descriptor);
				System.out.println(" --> strange cap height: " + ((pFont.descriptor == null) ? pFont.data : pFont.descriptor).get("CapHeight"));
			}
			if ((ascent != null) && (ascent.floatValue() != 0)) {
				if (DEBUG_LOAD_FONTS)
					System.out.println(" --> fallback to ascent: " + ascent);
				capHeight = ascent;
			}
			else if ((ascender != null) && (ascender.floatValue() != 0)) {
				if (DEBUG_LOAD_FONTS)
					System.out.println(" --> fallback to ascender: " + ascender);
				capHeight = ascender;
			}
		}
		
		//	set attributes
		if (DEBUG_LOAD_FONTS) System.out.println("Font line metrics computed:");
		
		//	- ascent := max(Ascent, Ascender, XHeight, CapHeight, upperRightY?)
		if (ascent != null)
			pFont.ascent = Math.max(pFont.ascent, (ascent.floatValue() / 1000));
		if (ascender != null)
			pFont.ascent = Math.max(pFont.ascent, (ascender.floatValue() / 1000));
		if (DEBUG_LOAD_FONTS) System.out.println(" - ascent is " + pFont.ascent);
		
		//	- descent := min(Descent, Descender, UnderlinePosition, lowerLeftY?)
		if (descent != null)
			pFont.descent = Math.min(pFont.descent, (descent.floatValue() / 1000));
		if (descender != null)
			pFont.descent = Math.min(pFont.descent, (descender.floatValue() / 1000));
		if (DEBUG_LOAD_FONTS) System.out.println(" - descent is " + pFont.descent);
		Number underlinePos = getNumber(pFont, "UnderlinePosition");
		if ((underlinePos != null) && ((underlinePos.floatValue() / 1000) < pFont.descent)) {
			pFont.descent = (underlinePos.floatValue() / 1000);
			if (DEBUG_LOAD_FONTS) System.out.println(" - adjusted descent to underline position " + pFont.descent);
		}
		
		//	- cap height := max(XHeight, CapHeight)
		if (capHeight != null)
			pFont.capHeight = Math.max(pFont.capHeight, (capHeight.floatValue() / 1000));
		if (DEBUG_LOAD_FONTS) System.out.println(" - cap height is " + pFont.capHeight);
		Number xHeight = getNumber(pFont, "XHeight");
		if ((xHeight != null) && (pFont.capHeight < (xHeight.floatValue() / 1000))) {
			pFont.capHeight = (xHeight.floatValue() / 1000);
			if (DEBUG_LOAD_FONTS) System.out.println(" - adjusted cap height to xHeight " + pFont.capHeight);
		}
		
		//	finally ...
		return true;
	}
	
	private static Number getNumber(PdfFont pFont, String key) {
		Object nObj = ((pFont.descriptor == null) ? pFont.data : pFont.descriptor).get(key);
		return ((nObj instanceof Number) ? ((Number) nObj) : null); 
	}
	
	private static BaseFont getBuiltInFont(Object fnObj, boolean isSymbolic) {
		String fn = fnObj.toString();
		if (fn.matches("[A-Z]{6}\\+.+"))
			fn = fn.substring(7);
		BaseFont font = ((BaseFont) builtInFontData.get(fn));
		if (font == null) {
			String ppClassResName = PdfParser.class.getName().replaceAll("\\.", "/");
			String afmResName = (ppClassResName.substring(0, ppClassResName.lastIndexOf('/')) + "/afmRes/" + fn + ".afm");
			try {
				InputStream fis = PdfParser.class.getClassLoader().getResourceAsStream(afmResName);
				BufferedReader fr = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
				font = readBaseFont(fr);
				fr.close();
				builtInFontData.put(fn, font);
			}
			catch (Exception e) {
				System.out.println("PdfParser: could not load built-in font '" + fn + "' from resource '" + afmResName + "'");
				if (e instanceof NullPointerException)
					System.out.println("  Resource not found"); // that's the usual 'not found' case
				else e.printStackTrace(System.out);
				
//				String baseName = null;
//				if (fn.startsWith("Times"))
//					baseName = "Times";
//				else if (fn.startsWith("Courier"))
//					baseName = "Courier";
//				else if (fn.startsWith("Helvetica"))
//					baseName = "Helvetica";
//				else if (fn.startsWith("Arial"))
//					baseName = "Helvetica";
				
				String baseName = getFallbackFontName(fn, isSymbolic);
				if (baseName != null) {
					String modifier = "-";
//					if (!isSymbolic) {
						if (fn.indexOf("Bold") != -1)
							modifier += "Bold";
						if (fn.indexOf("Italic") != -1)
							modifier += ("Times".equals(baseName) ? "Italic" : "Oblique");
//					}
					
					String substituteFontName = baseName;
					if (modifier.length() > 1)
						substituteFontName += modifier;
					else if ("Times".equals(baseName))
						substituteFontName += "-Roman";
					
					System.out.println("  Falling back to '" + substituteFontName + "'");
					return getBuiltInFont(substituteFontName, isSymbolic);
				}
			}
		}
		return font;
	}
	
	/**
	 * Retrieve the name of the base font to use in case some font is not
	 * available. This method maps serif fonts to 'Times', sans-serif fonts to
	 * 'Helvetica', and monospaced fonts to 'Courier'. These three fonts are
	 * manadtory to support for all PDF applications.
	 * @param fontName the name of the font to substitute
	 * @param isSymbolic do we need a fallback for a symbolic font?
	 * @return the substitute font name
	 */
	public static String getFallbackFontName(String fontName, boolean isSymbolic) {
		System.out.println("Getting fallback font name for " + fontName);
		if (fallbackFontNameMappings == null) {
			String ppClassResName = PdfParser.class.getName().replaceAll("\\.", "/");
			String fmResName = (ppClassResName.substring(0, ppClassResName.lastIndexOf('/')) + "/afmRes/FallbackMappings.txt");
			try {
				TreeMap fms = new TreeMap(new Comparator() {
					public int compare(Object o1, Object o2) {
						String s1 = ((String) o1);
						String s2 = ((String) o2);
						return ((s1.length() == s2.length()) ? s1.compareTo(s2) : (s2.length() - s1.length()));
					}
				});
				InputStream fmis = PdfParser.class.getClassLoader().getResourceAsStream(fmResName);
				BufferedReader fmr = new BufferedReader(new InputStreamReader(fmis, "UTF-8"));
				String fm;
				String f = null;
				while ((fm = fmr.readLine()) != null) {
					fm = fm.trim();
					if (fm.length() == 0)
						continue;
					if (fm.startsWith("//"))
						continue;
					
					if (fm.startsWith("#"))
						f = fm.substring("#".length()).trim();
					else if (f != null) {
						fm = fm.replaceAll("[^A-Za-z0-9]", "");
						fms.put(fm, f);
					}
				}
				fmr.close();
				fallbackFontNameMappings = fms;
			}
			catch (Exception e) {
				System.out.println("PdfParser: could not load fallback font mappings.");
				e.printStackTrace(System.out);
//				return (isSymbolic ? "ZapfDingbats" : "Times"); // last resort
				System.out.println("Failed to load mappings");
				return "Times"; // last resort
			}
		}
		
		//	normalize font name
		fontName = fontName.replaceAll("[^A-Za-z0-9]", "");
		System.out.println("Normalized to " + fontName);
		
		//	do lookup
		for (Iterator fmit = fallbackFontNameMappings.keySet().iterator(); fmit.hasNext();) {
			String fm = ((String) fmit.next());
			if (fm.startsWith(fontName)) {
				System.out.println(fm + " starts with " + fontName);
				return ((String) fallbackFontNameMappings.get(fm));
			}
			if (fontName.startsWith(fm)) {
				System.out.println(fontName + " starts with " + fm);
				return ((String) fallbackFontNameMappings.get(fm));
			}
		}
		
		//	chunk font name
		String[] fontNameParts = fontName.split("\\+");
		if (fontNameParts.length == 1) {
//			return (isSymbolic ? "ZapfDingbats" : "Times"); // last resort
			System.out.println("Nothing found");
			return "Times"; // last resort
		}
		
		//	look up individual chunks
		for (int p = 0; p < fontNameParts.length; p++) {
			System.out.println("Trying part " + fontNameParts[p]);
			String fm = getFallbackFontName(fontNameParts[p], isSymbolic);
			if (fm != null)
				return fm;
		}
		
		//	last resort
		System.out.println("Nothing found at all");
		return "Times";
	}
	private static TreeMap fallbackFontNameMappings = null;
	
	private static HashMap builtInFontData = new HashMap();
	private static BaseFont readBaseFont(BufferedReader br) throws IOException {
		String line;
		Map fd = new HashMap();
		BaseFont font = null;
		while ((line = br.readLine()) != null) {
			line = line.trim();
			if (line.startsWith("Comment"))
				continue;
			if (line.startsWith("Notice"))
				continue;
			if (line.startsWith("Version"))
				continue;
			
			for (int p = 0; p < afmFontParams.length; p++) {
				String value = readAfmFontParam(afmFontParams[p], line);
				if (value != null) {
					Object pValue = parseAfmFontParameter(afmFontParams[p], value);
					if (pValue != null) {
						fd.put(afmFontParams[p], pValue);
						fd.put(afmFontParamMappings.getProperty(afmFontParams[p], afmFontParams[p]), pValue);
					}
					break;
				}
			}
			
			if (line.startsWith("StartCharMetrics")) {
				Object fnObj = fd.get("FontName");
				if (fnObj == null)
					return null;
				Object eObj = fd.get("EncodingScheme");
				Object mcwObj = ((fd == null) ? null : fd.get("CharWidth"));
				font = new BaseFont(new HashMap(), fd, ((mcwObj instanceof Number) ? ((Number) mcwObj).floatValue() : 0), fnObj.toString(), ((eObj == null) ? null : eObj.toString()));
				readCharMetrics(br, font);
			}
			
			if (line.startsWith("StartKernData")) {
				readKernData(br, font);
			}
		}
		return font;
	}
	
	private static String[] afmFontParams = {
		
		//	string valued
		"FontName",
		"EncodingScheme",
		"CharacterSet",
		
		//	boolean valued
		"IsFixedPitch",
		
		//	number valued
		"ItalicAngle",
		"UnderlinePosition",
		"UnderlineThickness",
		"CapHeight",
		"XHeight",
		"Ascender",
		"Descender",
		
		//	two numbers
		"CharWidth",
		
		//	four numbers
		"FontBBox",
	};
	
	private static Properties afmFontParamMappings = new Properties();
	static {
		afmFontParamMappings.setProperty("EncodingScheme", "Encoding");
		afmFontParamMappings.setProperty("CharWidth", "MissingWidth");
		afmFontParamMappings.setProperty("Ascender", "Ascent");
		afmFontParamMappings.setProperty("Descender", "Descent");
	}
	
	private static String readAfmFontParam(String param, String line) {
		return (line.startsWith(param) ? line.substring(param.length()).trim() : null);
	}
	
	private static Object parseAfmFontParameter(String param, String value) {
		
		//	string valued
		if ("FontName".equals(param))
			return value;
		if ("EncodingScheme".equals(param))
			return value;
		if ("CharacterSet".equals(param))
			return value;
		
		//	boolean valued
		if ("IsFixedPitch".equals(param))
			return new Boolean(value);
		
		//	number valued
		if ("ItalicAngle".equals(param))
			return new Float(value);
		if ("UnderlinePosition".equals(param))
			return new Float(value);
		if ("UnderlineThickness".equals(param))
			return new Float(value);
		if ("CapHeight".equals(param))
			return new Float(value);
		if ("XHeight".equals(param))
			return new Float(value);
		if ("Ascender".equals(param))
			return new Float(value);
		if ("Descender".equals(param))
			return new Float(value);
		
		//	two numbers
		if ("CharWidth".equals(param)) {
			int split = value.indexOf(' ');
			if (split == -1)
				return new Float(value);
			else return new Float(value.substring(0, split));
			//	this only reads the X direction, but that should do for the Latin fonts we handle for now
		}
//		
//		//	four numbers
//		"FontBBox",
		
		//	unknown
		return null;
	}
	
	private static void readCharMetrics(BufferedReader br, BaseFont font) throws IOException {
		String line;
		int fc = -1;
		int lc = -1;
		ArrayList encodedCharWidths = new ArrayList();
		while ((line = br.readLine()) != null) {
			line = line.trim();
			
			if ("EndCharMetrics".equals(line))
				break;
			
			String[] fd = line.split("\\s*\\;\\s*");
			int cc = -1;
			int cw = -1;
			String cn = null;
			for (int d = 0; d < fd.length; d++) {
				if (fd[d].startsWith("C "))
					cc = Integer.parseInt(fd[d].substring("C ".length()).trim());
				else if (fd[d].startsWith("WX "))
					cw = Integer.parseInt(fd[d].substring("WX ".length()).trim());
				else if (fd[d].startsWith("N "))
					cn = fd[d].substring("N ".length()).trim();
			}
			
			if (cw == -1)
				continue;
			
			if (cn != null) {
				char ch = StringUtils.getCharForName(cn);
				if (ch != 0)
					font.setNamedCharWidth(ch, cw);
			}
			
			if (cc == -1)
				continue;
			
			while ((fc != -1) && (lc < (cc-1))) {
				encodedCharWidths.add(new Float(0));
				lc++;
			}
			if (fc == -1)
				fc = cc;
			lc = cc;
			encodedCharWidths.add(new Float(cw));
		}
		
		if (fc != -1) {
			float[] cws = new float[encodedCharWidths.size()];
			for (int c = 0; c < encodedCharWidths.size(); c++)
				cws[c] = ((Float) encodedCharWidths.get(c)).floatValue();
			font.setCharWidths(fc, lc, cws);
		}
	}
	
	private static void readKernData(BufferedReader br, BaseFont font) throws IOException {
		String line;
		while ((line = br.readLine()) != null) {
			line = line.trim();
			if ("EndKernData".equals(line))
				break;
			//	TODO actually read kerning data
		}
	}
	
	static final int FLAG_FixedPitch = (1 << 0);
	static final int FLAG_Serif = (1 << 1);
	static final int FLAG_Symbolic = (1 << 2);
	static final int FLAG_Script = (1 << 3);
	static final int FLAG_Nonsymbolic = (1 << 5);
	static final int FLAG_Italic = (1 << 6);
	static final int FLAG_AllCaps = (1 << 16);
	static final int FLAG_SmallCaps = (1 << 17);
	static final int FLAG_ForceBold = (1 << 18);
//	
//	public static void main(String[] args) throws Exception {
//		try {
//			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
//		} catch (Exception e) {}
//		
//		Font fontSerif = new Font("Serif", Font.PLAIN, 1);
//		System.out.println("" + fontSerif.getFontName());
//		
//		String[] fns = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
//		for (int f = 0; f < fns.length; f++)
//			System.out.println("" + fns[f]);
//		if (true)
//			return;
//		
//		int width = 36;
//		int height = 54;
//		Font font = new Font("Times New Roman", Font.PLAIN, 1);
////		Font font = new Font("Arial Unicode MS", Font.PLAIN, 1);
//		for (int c = 400; c < Character.MAX_VALUE; c++) {
//			char ch = ((char) c);
//			if (!Character.isLetterOrDigit(ch))
//				continue;
//			CharImage chImg = createImageChar(width, height, 0, ch, font, height, null);
//			if (chImg == null)
//				continue;
//		}
//	}
//	
//	private static final String[] unicodeBlocks = {
//		"0000-007F:L (Basic Latin)",
//		"0080-00FF:L (Latin)",
//		"0100-017F:L (Latin Extended-A)",
//		"0180-024F:L (Latin Extended-B)",
//		"0250-02AF:O (IPA Extensions)",
//		"02B0-02FF:O (Spacing Modifier Letters)",
//		"0300-036F:O (Combining Diacritical Marks)",
//		"0370-03FF:G (Greek and Coptic)",
//		"0400-04FF:C (Cyrillic)",
//		"0500-052F:C (Cyrillic Supplement)",
//		"0530-058F:O (Armenian)",
//		"0590-05FF:O (Hebrew)",
//		"0600-06FF:A (Arabic)",
//		"0700-074F:O (Syriac)",
//		"0750-077F:A (Arabic Supplement)",
//		"0780-07BF:O (Thaana)",
//		"07C0-07FF:O (NKo)",
//		"0800-083F:O (Samaritan)",
//		"0840-085F:O (Mandaic)",
//		"0860-086F:O (Syriac Supplement)",
//		"08A0-08FF:A (Arabic Extended-A)",
//		"0900-097F:O (Devanagari)",
//		"0980-09FF:O (Bengali)",
//		"0A00-0A7F:O (Gurmukhi)",
//		"0A80-0AFF:O (Gujarati)",
//		"0B00-0B7F:O (Oriya)",
//		"0B80-0BFF:O (Tamil)",
//		"0C00-0C7F:O (Telugu)",
//		"0C80-0CFF:O (Kannada)",
//		"0D00-0D7F:O (Malayalam)",
//		"0D80-0DFF:O (Sinhala)",
//		"0E00-0E7F:O (Thai)",
//		"0E80-0EFF:O (Lao)",
//		"0F00-0FFF:O (Tibetan)",
//		"1000-109F:O (Myanmar)",
//		"10A0-10FF:O (Georgian)",
//		"1100-11FF:O (Hangul Jamo)",
//		"1200-137F:O (Ethiopic)",
//		"1380-139F:O (Ethiopic Supplement)",
//		"13A0-13FF:O (Cherokee)",
//		"1400-167F:O (Unified Canadian Aboriginal Syllabics)",
//		"1680-169F:O (Ogham)",
//		"16A0-16FF:O (Runic)",
//		"1700-171F:O (Tagalog)",
//		"1720-173F:O (Hanunoo)",
//		"1740-175F:O (Buhid)",
//		"1760-177F:O (Tagbanwa)",
//		"1780-17FF:O (Khmer)",
//		"1800-18AF:O (Mongolian)",
//		"18B0-18FF:O (Unified Canadian Aboriginal Syllabics Extended)",
//		"1900-194F:O (Limbu)",
//		"1950-197F:O (Tai Le)",
//		"1980-19DF:O (New Tai Lue)",
//		"19E0-19FF:O (Khmer Symbols)",
//		"1A00-1A1F:O (Buginese)",
//		"1A20-1AAF:O (Tai Tham)",
//		"1AB0-1AFF:O (Combining Diacritical Marks Extended)",
//		"1B00-1B7F:O (Balinese)",
//		"1B80-1BBF:O (Sundanese)",
//		"1BC0-1BFF:O (Batak)",
//		"1C00-1C4F:O (Lepcha)",
//		"1C50-1C7F:O (Ol Chiki)",
//		"1C80-1C8F:C (Cyrillic Extended-C)",
//		"1C90-1CBF:O (Georgian Extended)",
//		"1CC0-1CCF:O (Sundanese Supplement)",
//		"1CD0-1CFF:O (Vedic Extensions)",
//		"1D00-1D7F:O (Phonetic Extensions)",
//		"1D80-1DBF:O (Phonetic Extensions Supplement)",
//		"1DC0-1DFF:O (Combining Diacritical Marks Supplement)",
//		"1E00-1EFF:L (Latin Extended Additional)",
//		"1F00-1FFF:G (Greek Extended)",
//		"2000-206F:O (General Punctuation)",
//		"2070-209F:O (Superscripts and Subscripts)",
//		"20A0-20CF:O (Currency Symbols)",
//		"20D0-20FF:O (Combining Diacritical Marks for Symbols)",
//		"2100-214F:O (Letterlike Symbols)",
//		"2150-218F:O (Number Forms)",
//		"2190-21FF:O (Arrows)",
//		"2200-22FF:O (Mathematical Operators)",
//		"2300-23FF:O (Miscellaneous Technical)",
//		"2400-243F:O (Control Pictures)",
//		"2440-245F:O (Optical Character Recognition)",
//		"2460-24FF:O (Enclosed Alphanumerics)",
//		"2500-257F:O (Box Drawing)",
//		"2580-259F:O (Block Elements)",
//		"25A0-25FF:O (Geometric Shapes)",
//		"2600-26FF:O (Miscellaneous Symbols)",
//		"2700-27BF:O (Dingbats)",
//		"27C0-27EF:O (Miscellaneous Mathematical Symbols-A)",
//		"27F0-27FF:O (Supplemental Arrows-A)",
//		"2800-28FF:O (Braille Patterns)",
//		"2900-297F:O (Supplemental Arrows-B)",
//		"2980-29FF:O (Miscellaneous Mathematical Symbols-B)",
//		"2A00-2AFF:O (Supplemental Mathematical Operators)",
//		"2B00-2BFF:O (Miscellaneous Symbols and Arrows)",
//		"2C00-2C5F:O (Glagolitic)",
//		"2C60-2C7F:L (Latin Extended-C)",
//		"2C80-2CFF:O (Coptic)",
//		"2D00-2D2F:O (Georgian Supplement)",
//		"2D30-2D7F:O (Tifinagh)",
//		"2D80-2DDF:O (Ethiopic Extended)",
//		"2DE0-2DFF:C (Cyrillic Extended-A)",
//		"2E00-2E7F:O (Supplemental Punctuation)",
//		"2E80-2EFF:O (CJK Radicals Supplement)",
//		"2F00-2FDF:O (Kangxi Radicals)",
//		"2FF0-2FFF:O (Ideographic Description Characters)",
//		"3000-303F:O (CJK Symbols and Punctuation)",
//		"3040-309F:O (Hiragana)",
//		"30A0-30FF:O (Katakana)",
//		"3100-312F:O (Bopomofo)",
//		"3130-318F:O (Hangul Compatibility Jamo)",
//		"3190-319F:O (Kanbun)",
//		"31A0-31BF:O (Bopomofo Extended)",
//		"31C0-31EF:O (CJK Strokes)",
//		"31F0-31FF:O (Katakana Phonetic Extensions)",
//		"3200-32FF:O (Enclosed CJK Letters and Months)",
//		"3300-33FF:O (CJK Compatibility)",
//		"3400-4DBF:O (CJK Unified Ideographs Extension A)",
//		"4DC0-4DFF:O (Yijing Hexagram Symbols)",
//		"4E00-9FFF:O (CJK Unified Ideographs)",
//		"A000-A48F:O (Yi Syllables)",
//		"A490-A4CF:O (Yi Radicals)",
//		"A4D0-A4FF:O (Lisu)",
//		"A500-A63F:O (Vai)",
//		"A640-A69F:C (Cyrillic Extended-B)",
//		"A6A0-A6FF:O (Bamum)",
//		"A700-A71F:O (Modifier Tone Letters)",
//		"A720-A7FF:L (Latin Extended-D)",
//		"A800-A82F:O (Syloti Nagri)",
//		"A830-A83F:O (Common Indic Number Forms)",
//		"A840-A87F:O (Phags-pa)",
//		"A880-A8DF:O (Saurashtra)",
//		"A8E0-A8FF:O (Devanagari Extended)",
//		"A900-A92F:O (Kayah Li)",
//		"A930-A95F:O (Rejang)",
//		"A960-A97F:O (Hangul Jamo Extended-A)",
//		"A980-A9DF:O (Javanese)",
//		"A9E0-A9FF:O (Myanmar Extended-B)",
//		"AA00-AA5F:O (Cham)",
//		"AA60-AA7F:O (Myanmar Extended-A)",
//		"AA80-AADF:O (Tai Viet)",
//		"AAE0-AAFF:O (Meetei Mayek Extensions)",
//		"AB00-AB2F:O (Ethiopic Extended-A)",
//		"AB30-AB6F:L (Latin Extended-E)",
//		"AB70-ABBF:O (Cherokee Supplement)",
//		"ABC0-ABFF:O (Meetei Mayek)",
//		"AC00-D7AF:O (Hangul Syllables)",
//		"D7B0-D7FF:O (Hangul Jamo Extended-B)",
//		"D800-DB7F:O (High Surrogates)",
//		"DB80-DBFF:O (High Private Use Surrogates)",
//		"DC00-DFFF:O (Low Surrogates)",
//		"E000-F8FF:O (Private Use Area)",
//		"F900-FAFF:O (CJK Compatibility Ideographs)",
//		"FB00-FB4F:O (Alphabetic Presentation Forms)",
//		"FB50-FDFF:A (Arabic Presentation Forms-A)",
//		"FE00-FE0F:O (Variation Selectors)",
//		"FE10-FE1F:O (Vertical Forms)",
//		"FE20-FE2F:O (Combining Half Marks)",
//		"FE30-FE4F:O (CJK Compatibility Forms)",
//		"FE50-FE6F:O (Small Form Variants)",
//		"FE70-FEFF:A (Arabic Presentation Forms-B)",
//		"FF00-FFEF:O (Halfwidth and Fullwidth Forms)",
//		"FFF0-FFFF:O (Specials)",
//	};
}