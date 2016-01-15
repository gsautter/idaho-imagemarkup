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
package de.uka.ipd.idaho.im.pdf.test;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.QuadCurve2D;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

import de.uka.ipd.idaho.gamta.util.ProgressMonitor;
import de.uka.ipd.idaho.im.utilities.ImageDisplayDialog;
import de.uka.ipd.idaho.stringUtils.StringUtils;

/**
 * This class reads TrueType FreeFont glyph definitions in an attempt at
 * generating both char and font style characteristics from the vector
 * representations of the individual glyphs. The TrueType data handling and
 * rendering code is copied from PdfFontDecoder and PdfCharDecoder.
 * 
 * @author sautter
 */
public class PdfFontReferenceGenerator {
	private static final boolean DEBUG_TRUE_TYPE_LOADING = true;
	
	private static void readFontTrueType(byte[] data, ProgressMonitor pm) {
//		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("Used chars are "+ pFont.usedChars.toString());
//		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("Unicode mapping is "+ pFont.ucMappings.toString());
		
		//	read basic data
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("Got font program type 2:");
		int ffByteOffset = 0;
		int scalerType = readUnsigned(data, ffByteOffset, (ffByteOffset+4));
		ffByteOffset += 4;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - scaler type is " + scalerType);
		int numTables = readUnsigned(data, ffByteOffset, (ffByteOffset+2));
		ffByteOffset += 2;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - num tables is " + numTables);
		int searchRange = readUnsigned(data, ffByteOffset, (ffByteOffset+2));
		ffByteOffset += 2;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - search range is " + searchRange);
		int entrySelector = readUnsigned(data, ffByteOffset, (ffByteOffset+2));
		ffByteOffset += 2;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - entry selector is " + entrySelector);
		int rangeShift = readUnsigned(data, ffByteOffset, (ffByteOffset+2));
		ffByteOffset += 2;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - range shift is " + rangeShift);
		
		//	read tables, and index them by name
		HashMap tables = new HashMap();
		int tableEnd = -1;
		for (int t = 0; t < numTables; t++) {
			String tag = "";
			for (int i = 0; i < 4; i++) {
				tag += ((char) readUnsigned(data, ffByteOffset, (ffByteOffset+1)));
				ffByteOffset++;
			}
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - table " + tag + ":");
			int checkSum = readUnsigned(data, ffByteOffset, (ffByteOffset+4));
			ffByteOffset += 4;
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - checksum is " + checkSum);
			int offset = readUnsigned(data, ffByteOffset, (ffByteOffset+4));
			ffByteOffset += 4;
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - offset is " + offset);
			int length = readUnsigned(data, ffByteOffset, (ffByteOffset+4));
			ffByteOffset += 4;
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - length is " + length);
			tableEnd = offset + length;
			byte[] tableData = new byte[length];
			System.arraycopy(data, offset, tableData, 0, length);
			tables.put(tag.trim(), tableData);
			while ((tableEnd % 4) != 0)
				tableEnd++;
			
		}
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - table end is " + tableEnd);
		if (tableEnd != -1)
			ffByteOffset = tableEnd;
		
		if (ffByteOffset < data.length) {
//			System.out.println(" - bytes are " + new String(ffBytes, ffByteOffset, (ffBytes.length-ffByteOffset)));
			for (int b = ffByteOffset; b < Math.min(data.length, (ffByteOffset + 1024)); b++) {
				int bt = convertUnsigned(data[b]);
				System.out.println(" - " + bt + " (" + ((char) bt) + ")");
			}
		}
		else if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - no bytes remaining after tables");
		
		//	get 'head', 'cmap', 'loca', and 'glyf' tables
		byte[] headBytes = ((byte[]) tables.get("head"));
		byte[] cmapBytes = ((byte[]) tables.get("cmap"));
		byte[] locaBytes = ((byte[]) tables.get("loca"));
		byte[] glyfBytes = ((byte[]) tables.get("glyf"));
		if ((headBytes == null) || (cmapBytes == null) || (locaBytes == null) || (glyfBytes == null))
			return;
		
		//	get parameters from 'head' table (http://developer.apple.com/fonts/TrueType-Reference-Manual/RM06/Chap6head.html)
		int headByteOffset = 0;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - reading 'head' table of " + headBytes.length + " bytes");
		int hVersion = readUnsigned(headBytes, headByteOffset, (headByteOffset+4));
		headByteOffset += 4;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - version is " + (((double) hVersion) / 65536));
		int hFontVersion = readUnsigned(headBytes, headByteOffset, (headByteOffset+4));
		headByteOffset += 4;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - font version is " + (((double) hFontVersion) / 65536));
		int hCheckSumAdjustment = readUnsigned(headBytes, headByteOffset, (headByteOffset+4));
		headByteOffset += 4;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - check sum adjustment is " + hCheckSumAdjustment);
		int hMagicNumber = readUnsigned(headBytes, headByteOffset, (headByteOffset+4));
		headByteOffset += 4;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - magic number is " + hMagicNumber);
		int hFlags = readUnsigned(headBytes, headByteOffset, (headByteOffset+2));
		headByteOffset += 2;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - flags are " + Integer.toString(hFlags, 2));
		int hUnitsPerEm = readUnsigned(headBytes, headByteOffset, (headByteOffset+2));
		headByteOffset += 2;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - units per em is " + hUnitsPerEm);
		long hCreated = readUnsigned(headBytes, headByteOffset, (headByteOffset+4));
		headByteOffset += 4;
		hCreated <<= 32;
		hCreated |= readUnsigned(headBytes, headByteOffset, (headByteOffset+4));
		headByteOffset += 4;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - created is " + hCreated);
		long hModified = readUnsigned(headBytes, headByteOffset, (headByteOffset+4));
		headByteOffset += 4;
		hModified <<= 32;
		hModified |= readUnsigned(headBytes, headByteOffset, (headByteOffset+4));
		headByteOffset += 4;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - modified is " + hModified);
		int hMinX = readUnsigned(headBytes, headByteOffset, (headByteOffset+2));
		headByteOffset += 2;
		if (hMinX > 32768)
			hMinX -= 65536;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - min X is " + hMinX);
		int hMinY = readUnsigned(headBytes, headByteOffset, (headByteOffset+2));
		headByteOffset += 2;
		if (hMinY > 32768)
			hMinY -= 65536;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - min Y is " + hMinY);
		int hMaxX = readUnsigned(headBytes, headByteOffset, (headByteOffset+2));
		headByteOffset += 2;
		if (hMaxX > 32768)
			hMaxX -= 65536;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - max X is " + hMaxX);
		int hMaxY = readUnsigned(headBytes, headByteOffset, (headByteOffset+2));
		headByteOffset += 2;
		if (hMaxY > 32768)
			hMaxY -= 65536;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - max Y is " + hMaxY);
		int hMacStyle = readUnsigned(headBytes, headByteOffset, (headByteOffset+2));
		headByteOffset += 2;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - mac style is " + hMacStyle);
		int hLowestRecPPEM = readUnsigned(headBytes, headByteOffset, (headByteOffset+2));
		headByteOffset += 2;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - lowest pixel size is " + hLowestRecPPEM);
		int hFontDirectionHint = readUnsigned(headBytes, headByteOffset, (headByteOffset+2));
		headByteOffset += 2;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - font direction hint is " + hFontDirectionHint);
		int hIndexToLocFormat = readUnsigned(headBytes, headByteOffset, (headByteOffset+2));
		headByteOffset += 2;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - loca format is " + hIndexToLocFormat); // 0 for short offsets (number of byte pairs), 1 for long (number of bytes)
		int hGlyphDataFormat = readUnsigned(headBytes, headByteOffset, (headByteOffset+2));
		headByteOffset += 2;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - glyph data format is " + hGlyphDataFormat);
		
		//	read CID mappings from 'cmap' table (http://developer.apple.com/fonts/TrueType-Reference-Manual/RM06/Chap6cmap.html)
		HashMap cidsByGlyphIndex = new HashMap();
		int cmapByteOffset = 0;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - reading 'cmap' table of " + cmapBytes.length + " bytes");
		int mVersion = readUnsigned(cmapBytes, cmapByteOffset, (cmapByteOffset+2));
		cmapByteOffset += 2;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - version is " + mVersion);
		int mSubTableCount = readUnsigned(cmapBytes, cmapByteOffset, (cmapByteOffset+2));
		cmapByteOffset += 2;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - number of sub tables is " + mSubTableCount);
		for (int t = 0; t < mSubTableCount; t++) {
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println("     - table " + t);
			int stPlatformId = readUnsigned(cmapBytes, cmapByteOffset, (cmapByteOffset+2));
			cmapByteOffset += 2;
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - platform ID is " + stPlatformId);
			int stPlatformSpecId = readUnsigned(cmapBytes, cmapByteOffset, (cmapByteOffset+2));
			cmapByteOffset += 2;
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - platform specific ID is " + stPlatformSpecId);
			int stOffset = readUnsigned(cmapBytes, cmapByteOffset, (cmapByteOffset+4));
			cmapByteOffset += 4;
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - offset is " + stOffset);
			int stByteOffset = stOffset;
			int stFormat = readUnsigned(cmapBytes, stByteOffset, (stByteOffset+2));
			stByteOffset += 2;
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - format is " + stFormat);
			if ((stFormat == 8) || (stFormat == 10) || (stFormat == 12) || (stFormat == 13))
				stByteOffset += 2;
			int stLength;
			int stLanguage;
			if ((stFormat == 8) || (stFormat == 10) || (stFormat == 12) || (stFormat == 13) || (stFormat == 14)) {
				stLength = readUnsigned(cmapBytes, stByteOffset, (stByteOffset+4));
				stByteOffset += 4;
				if (stFormat == 14)
					stLanguage = -1;
				else {
					stLanguage = readUnsigned(cmapBytes, stByteOffset, (stByteOffset+4));
					stByteOffset += 4;
				}
			}
			else {
				stLength = readUnsigned(cmapBytes, stByteOffset, (stByteOffset+2));
				stByteOffset += 2;
				stLanguage = readUnsigned(cmapBytes, stByteOffset, (stByteOffset+2));
				stByteOffset += 2;
			}
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - length is " + stLength);
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - language is " + stLanguage);
			if (stFormat == 0) {
				for (int c = 0; c < 256; c++) {
					int stGlyphIndex = readUnsigned(cmapBytes, stByteOffset, (stByteOffset+1));
					stByteOffset += 1;
					if (stGlyphIndex != 0) {
						if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - CID " + c + " mapped to " + stGlyphIndex);
						cidsByGlyphIndex.put(new Integer(stGlyphIndex), new Integer(c));
					}
				}
			}
			else if (stFormat == 2) {
				//	TODO implement this once example becomes available
			}
			else if (stFormat == 4) {
				int stSegCountX2 = readUnsigned(cmapBytes, stByteOffset, (stByteOffset+2));
				stByteOffset += 2;
				int stSegCount = (stSegCountX2 / 2); 
				if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - segment count is " + stSegCount);
				int stSearchRange = readUnsigned(cmapBytes, stByteOffset, (stByteOffset+2));
				stByteOffset += 2;
				if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - search range is " + stSearchRange);
				int stEntrySelector = readUnsigned(cmapBytes, stByteOffset, (stByteOffset+2));
				stByteOffset += 2;
				if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - entry selector is " + stEntrySelector);
				int stRangeShift = readUnsigned(cmapBytes, stByteOffset, (stByteOffset+2));
				stByteOffset += 2;
				if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - range shift is " + stRangeShift);
				int[] stSegEnds = new int[stSegCount];
				for (int s = 0; s < stSegCount; s++) {
					stSegEnds[s] = readUnsigned(cmapBytes, stByteOffset, (stByteOffset+2));
					stByteOffset += 2;
				}
				if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - segment ends are " + Arrays.toString(stSegEnds));
				int stReservedPad = readUnsigned(cmapBytes, stByteOffset, (stByteOffset+2));
				stByteOffset += 2;
				if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - reserved pad is " + stReservedPad);
				int[] stSegStarts = new int[stSegCount];
				for (int s = 0; s < stSegCount; s++) {
					stSegStarts[s] = readUnsigned(cmapBytes, stByteOffset, (stByteOffset+2));
					stByteOffset += 2;
				}
				if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - segment starts are " + Arrays.toString(stSegStarts));
				int[] stIdDeltas = new int[stSegCount];
				for (int s = 0; s < stSegCount; s++) {
					stIdDeltas[s] = readUnsigned(cmapBytes, stByteOffset, (stByteOffset+2));
					stByteOffset += 2;
				}
				if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - ID deltas are " + Arrays.toString(stIdDeltas));
				int[] stIdRangeOffsets = new int[stSegCount];
				for (int s = 0; s < stSegCount; s++) {
					stIdRangeOffsets[s] = readUnsigned(cmapBytes, stByteOffset, (stByteOffset+2));
					stByteOffset += 2;
				}
				if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - ID range offsets are " + Arrays.toString(stIdRangeOffsets));
				for (int s = 0; s < stSegCount; s++)
					for (int c = stSegStarts[s]; c <= stSegEnds[s]; c++) {
						if (stIdRangeOffsets[s] == 0) {
							int stGlyphIndex = c + stIdDeltas[s];
							if (stGlyphIndex > 65535)
								stGlyphIndex -= 65536;
							if (cidsByGlyphIndex.containsKey(new Integer(stGlyphIndex)))
								continue;
							if (DEBUG_TRUE_TYPE_LOADING) System.out.println("CID " + c + " mapped (1) to GI " + stGlyphIndex);
							cidsByGlyphIndex.put(new Integer(stGlyphIndex), new Integer(c));
						}
						else {
							//	glyphIndex = *( &idRangeOffset[i] + idRangeOffset[i] / 2 + (c - startCode[i]) )
							//	glyphIndexAddress = idRangeOffset[i] + 2 * (c - startCode[i]) + (Ptr) &idRangeOffset[i]
							int stGlyphIndexAddress = stIdRangeOffsets[s] + (2 * (c - stSegStarts[s])) + (stByteOffset - (2 * (stSegCount - s)));
							int stGlyphIndex = readUnsigned(cmapBytes, stGlyphIndexAddress, (stGlyphIndexAddress+2));
							if (stGlyphIndex == 0)
								continue;
							stGlyphIndex += stIdDeltas[s];
							if (stGlyphIndex > 65535)
								stGlyphIndex -= 65536;
							if (cidsByGlyphIndex.containsKey(new Integer(stGlyphIndex)))
								continue;
							if (DEBUG_TRUE_TYPE_LOADING) System.out.println("CID " + c + " mapped (2) to GI " + stGlyphIndex + " at " + stGlyphIndexAddress);
							cidsByGlyphIndex.put(new Integer(stGlyphIndex), new Integer(c));
						}
					}
			}
			else if (stFormat == 6) {
				//	TODO implement this once example becomes available
			}
			else if (stFormat == 8) {
				//	TODO implement this once example becomes available
			}
			else if (stFormat == 10) {
				//	TODO implement this once example becomes available
			}
			else if (stFormat == 12) {
				//	TODO implement this once example becomes available
			}
			else if (stFormat == 13) {
				//	TODO implement this once example becomes available
			}
			else if (stFormat == 14) {
				//	TODO implement this once example becomes available
			}
		}
		
		//	get locations from 'loca' table and read glyph data (http://developer.apple.com/fonts/TrueType-Reference-Manual/RM06/Chap6loca.html)
		HashMap glyphsByIndex = new HashMap();
		ArrayList glyphData = new ArrayList();
		if (hIndexToLocFormat == 1) {
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - reading 'loca' table of " + locaBytes.length + " bytes in 4-byte mode");
			int glyphIndex = 0;
			int glyphStart = 0;
			for (int o = 4; o < locaBytes.length; o += 4) {
				int glyphEnd = readUnsigned(locaBytes, o, (o+4));
				if (glyphStart < glyphEnd) {
					Integer gi = new Integer(glyphIndex);
					Integer cid = ((Integer) cidsByGlyphIndex.get(gi));
					if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - got glyph " + glyphIndex + " from " + glyphStart + " to " + glyphEnd + ", CID is " + cid);
					//	read glyph from 'glyf' table (http://developer.apple.com/fonts/TrueType-Reference-Manual/RM06/Chap6glyf.html)
					GlyphDataTrueType glyph = readGlyphTrueType(glyphStart, glyphEnd, glyfBytes, ((cid == null) ? -1 : cid.intValue()), glyphIndex);
					glyphsByIndex.put(gi, glyph);
					if (glyph.cid != -1)
						glyphData.add(glyph);
				}
				glyphIndex++;
				glyphStart = glyphEnd;
			}
		}
		else {
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - reading 'loca' table of " + locaBytes.length + " bytes in 2-byte mode");
			int glyphIndex = 0;
			int glyphStart = 0;
			for (int o = 2; o < locaBytes.length; o += 2) {
				int glyphEnd = readUnsigned(locaBytes, o, (o+2));
				glyphEnd *= 2;
				if (glyphStart < glyphEnd) {
					Integer gi = new Integer(glyphIndex);
					Integer cid = ((Integer) cidsByGlyphIndex.get(gi));
					if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - got glyph " + glyphIndex + " from " + glyphStart + " to " + glyphEnd + ", CID is " + cid);
					//	read glyph from 'glyf' table (http://developer.apple.com/fonts/TrueType-Reference-Manual/RM06/Chap6glyf.html)
					GlyphDataTrueType glyph = readGlyphTrueType(glyphStart, glyphEnd, glyfBytes, ((cid == null) ? -1 : cid.intValue()), glyphIndex);
					glyphsByIndex.put(gi, glyph);
					if (glyph.cid != -1)
						glyphData.add(glyph);
				}
				glyphIndex++;
				glyphStart = glyphEnd;
			}
		}
		
		//	measure characters
		pm.setInfo("   - measuring characters");
		int maxDescent = 0;
		int maxCapHeight = 0;
		OpTrackerTrueType[] otrs = new OpTrackerTrueType[glyphData.size()];
		for (int c = 0; c < glyphData.size(); c++) {
			GlyphDataTrueType glyph = ((GlyphDataTrueType) glyphData.get(c));
			otrs[c] = new OpTrackerTrueType(glyph.cid);
			pm.setInfo("     - measuring char " + c + " with CID " + glyph.cid);
			if (glyph instanceof SimpleGlyphDataTrueType)
				runFontTrueTypeOps(((SimpleGlyphDataTrueType) glyph), false, otrs[c], false);
			else runFontTrueTypeOps(((CompoundGlyphDataTrueType) glyph), glyphsByIndex, otrs[c], false);
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println("     --> " + otrs[c].minX + " < X < " + otrs[c].maxX);
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println("     --> " + otrs[c].minY + " < Y < " + otrs[c].maxY);
			maxDescent = Math.min(maxDescent, otrs[c].minY);
			maxCapHeight = Math.max(maxCapHeight, otrs[c].maxY);
		}
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("Max descent is " + maxDescent + ", max cap height is " + maxCapHeight);
		
		//	set up rendering
		int maxRenderSize = 300;
		float scale = 1.0f;
		if ((maxCapHeight - maxDescent) > maxRenderSize)
			scale = (((float) maxRenderSize) / (maxCapHeight - maxDescent));
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" ==> scaledown factor is " + scale);
//		
// 		//	set up style-aware name based checks
//		pm.setInfo("   - decoding characters");
//		Font serifFont = getSerifFont();
//		Font[] serifFonts = new Font[4];
//		Font sansFont = getSansSerifFont();
//		Font[] sansFonts = new Font[4];
//		for (int s = Font.PLAIN; s <= (Font.BOLD | Font.ITALIC); s++) {
//			serifFonts[s] = serifFont.deriveFont(s);
//			sansFonts[s] = sansFont.deriveFont(s);
//		}
//		
//		CharImageMatch[][] serifStyleCims = new CharImageMatch[glyphData.size()][4];
//		int[] serifCharCounts = {0, 0, 0, 0};
//		int[] serifCharCountsNs = {0, 0, 0, 0};
//		double[] serifStyleSimMins = {1, 1, 1, 1};
//		double[] serifStyleSimMinsNs = {1, 1, 1, 1};
//		double[] serifStyleSimSums = {0, 0, 0, 0};
//		double[] serifStyleSimSumsNs = {0, 0, 0, 0};
//		
//		CharImageMatch[][] sansStyleCims = new CharImageMatch[glyphData.size()][4];
//		int[] sansCharCounts = {0, 0, 0, 0};
//		int[] sansCharCountsNs = {0, 0, 0, 0};
//		double[] sansStyleSimMins = {1, 1, 1, 1};
//		double[] sansStyleSimMinsNs = {1, 1, 1, 1};
//		double[] sansStyleSimSums = {0, 0, 0, 0};
//		double[] sansStyleSimSumsNs = {0, 0, 0, 0};
//		
//		double simMin = 1;
//		double simMinNs = 1;
//		double simSum = 0;
//		double simSumNs = 0;
//		int charCount = 0;
//		int charCountNs = 0;
//		
//		CharImage[] charImages = new CharImage[glyphData.size()];
//		char[] chars = new char[glyphData.size()];
//		Arrays.fill(chars, ((char) 0));
//		HashSet unresolvedCIDs = new HashSet();
//		HashSet smallCapsCIDs = new HashSet();
//		
//		HashMap matchCharChache = new HashMap();
		
		//	generate images and match against named char
		ImageDisplayDialog fidd = (DEBUG_TRUE_TYPE_RENDERING ? new ImageDisplayDialog("FreeFont") : null);
		for (int c = 0; c < glyphData.size(); c++) {
			GlyphDataTrueType glyph = ((GlyphDataTrueType) glyphData.get(c));
			String chn = null;
			String ucCh = ("" + ((char) glyph.cid));//pFont.getUnicode(glyph.cid);
			if ((ucCh != null) && (ucCh.length() > 0)) {
//				chars[c] = ucCh.charAt(0);
				chn = StringUtils.getCharName(ucCh.charAt(0));
			}
//			if (!DEBUG_TRUE_TYPE_RENDERING && !pFont.usedChars.contains(new Integer(glyph.cid))) {
//				pm.setInfo("     - ignoring unused char " + c + " with CID " + glyph.cid + " (" + ucCh + ", " + chn + ")");
//				continue;
//			}
//			if (chn == null)
//				unresolvedCIDs.add(new Integer(glyph.cid));
			pm.setInfo("     - decoding char " + c + " with CID " + glyph.cid + " (" + ucCh + ", " + chn + ")");
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println("Decoding char " + c + ", CID " + glyph.cid + " (" + ucCh + ", " + chn + ")");
//			String chs = ("" + chars[c]);
//			int chw = ((otrs[c].rWidth == Integer.MIN_VALUE) ? dWidth : (nWidth + otrs[c].rWidth));
//			int chw = ((otrs[c].rWidth == 0) ? dWidth : (nWidth + otrs[c].rWidth));
//			if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - char width is " + chw);
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - stroke count is " + otrs[c].mCount);
//			if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - " + otrs[c].id + ": " + otrs[c].minX + " < X < " + otrs[c].maxX);
//			
//			boolean ignoreForMin = ((minIgnoreChars.indexOf(chars[c]) != -1) || (chars[c] != StringUtils.getBaseChar(chars[c])));
			
			/* TODO check following characters for problems:
			 * - 0 is filled
			 * - all closing brackets are opening ones
			 */
			
			//	check if char rendering possible
			if ((otrs[c].maxX <= otrs[c].minX) || (otrs[c].maxY <= otrs[c].minY))
				continue;
			
			//	render char
			int mx = 8;
			int my = ((mx * (maxCapHeight - maxDescent)) / (otrs[c].maxX - otrs[c].minX));
			OpGraphicsTrueType ogr = new OpGraphicsTrueType(
					otrs[c].minX,
					maxDescent,
					(maxCapHeight - maxDescent + (my / 2)),
					scale,
					new BufferedImage(
							Math.round((scale * (otrs[c].maxX - otrs[c].minX + mx)) + (2 * glyphOutlineFillSafetyEdge)),
							Math.round((scale * (maxCapHeight - maxDescent + my)) + (2 * glyphOutlineFillSafetyEdge)),
							BufferedImage.TYPE_INT_RGB)
					);
			if (glyph instanceof SimpleGlyphDataTrueType)
				runFontTrueTypeOps(((SimpleGlyphDataTrueType) glyph), false, ogr, (glyph.cid == DEBUG_TRUE_TYPE_TARGET_CID));
			else runFontTrueTypeOps(((CompoundGlyphDataTrueType) glyph), glyphsByIndex, ogr, (glyph.cid == DEBUG_TRUE_TYPE_TARGET_CID));
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - image rendered, size is " + ogr.img.getWidth() + "x" + ogr.img.getHeight() + ", OpGraphics height is " + ogr.height);
//			charImages[c] = new CharImage(ogr.img, Math.round(((float) (maxCapHeight * ogr.img.getHeight())) / (maxCapHeight - maxDescent)));
			if ((fidd != null) && (glyph.cid < 1024))
				fidd.addImage(ogr.img, (glyph.cid + " (" + glyph.gid + "): " + ucCh));
			
			if ((glyph.cid < 65535) && (glyph.cid > 256))
				break;
			
			
//			
//			//	no use going on without a char name
//			if (chn == null) {
//				unresolvedCIDs.add(new Integer(glyph.cid));
//				continue;
//			}
//			
//			//	store char image
//			pFont.setCharImage(glyph.cid, chn, ogr.img);
//			
//			//	measure best match
//			float bestSim = -1;
//			float bestSimNs = -1;
//			char bestSimCh = ((char) 0);
//			
//			//	try named char match first (render known chars to fill whole image)
//			CharMatchResult matchResult = PdfCharDecoder.matchChar(charImages[c], chars[c], true, serifFonts, sansFonts, matchCharChache, true, false);
//			float oSimSum = 0;
//			int oSimCount = 0;
//			for (int s = Font.PLAIN; s <= (Font.BOLD | Font.ITALIC); s++) {
//				if (matchResult.serifStyleCims[s] != null) {
//					oSimSum += matchResult.serifStyleCims[s].sim;
//					oSimCount++;
//				}
//				if (matchResult.sansStyleCims[s] != null) {
//					oSimSum += matchResult.sansStyleCims[s].sim;
//					oSimCount++;
//				}
//			}
//			if (DEBUG_TRUE_TYPE_LOADING)
//				System.out.println(" - average similarity is " + ((oSimCount == 0) ? 0 : (oSimSum / oSimCount)));
//			
//			//	NO USE _CHECKING_ UNICODE MAPPING IN CID FONT, AS ALL WE HAVE FOR DECODING CIDs _IS_ THE UNICODE MAPPING
//			
//			//	evaluate match result
//			if (matchResult.rendered) {
//				charCount++;
//				if ((matchResult.serifStyleCims[Font.PLAIN] != null) || (matchResult.serifStyleCims[Font.BOLD] != null) || (matchResult.sansStyleCims[Font.PLAIN] != null) || (matchResult.sansStyleCims[Font.BOLD] != null) || (skewChars.indexOf(chars[c]) == -1))
//					charCountNs++;
//				for (int s = Font.PLAIN; s <= (Font.BOLD | Font.ITALIC); s++) {
//					serifStyleCims[c][s] = matchResult.serifStyleCims[s];
//					if (serifStyleCims[c][s] == null) {
//						serifStyleSimMins[s] = 0;
//						continue;
//					}
//					serifCharCounts[s]++;
//					serifStyleSimSums[s] += serifStyleCims[c][s].sim;
//					if (bestSim < serifStyleCims[c][s].sim) {
//						bestSim = serifStyleCims[c][s].sim;
//						bestSimCh = serifStyleCims[c][s].match.ch;
//					}
//					if (!ignoreForMin)
//						serifStyleSimMins[s] = Math.min(serifStyleCims[c][s].sim, serifStyleSimMins[s]);
//					if (((s & Font.ITALIC) == 0) || (skewChars.indexOf(chars[c]) == -1)) {
//						serifCharCountsNs[s]++;
//						serifStyleSimSumsNs[s] += serifStyleCims[c][s].sim;
//						bestSimNs = Math.max(bestSimNs, serifStyleCims[c][s].sim);
//						if (!ignoreForMin)
//							serifStyleSimMinsNs[s] = Math.min(serifStyleCims[c][s].sim, serifStyleSimMinsNs[s]);
//					}
//				}
//				for (int s = Font.PLAIN; s <= (Font.BOLD | Font.ITALIC); s++) {
//					sansStyleCims[c][s] = matchResult.sansStyleCims[s];
//					if (sansStyleCims[c][s] == null) {
//						sansStyleSimMins[s] = 0;
//						continue;
//					}
//					sansCharCounts[s]++;
//					sansStyleSimSums[s] += sansStyleCims[c][s].sim;
//					if (bestSim < sansStyleCims[c][s].sim) {
//						bestSim = sansStyleCims[c][s].sim;
//						bestSimCh = sansStyleCims[c][s].match.ch;
//					}
//					if (!ignoreForMin)
//						sansStyleSimMins[s] = Math.min(sansStyleCims[c][s].sim, sansStyleSimMins[s]);
//					if (((s & Font.ITALIC) == 0) || (skewChars.indexOf(chars[c]) == -1)) {
//						sansCharCountsNs[s]++;
//						sansStyleSimSumsNs[s] += sansStyleCims[c][s].sim;
//						bestSimNs = Math.max(bestSimNs, sansStyleCims[c][s].sim);
//						if (!ignoreForMin)
//							sansStyleSimMinsNs[s] = Math.min(sansStyleCims[c][s].sim, sansStyleSimMinsNs[s]);
//					}
//				}
//			}
//			
//			//	we might want to revisit this one
//			if (bestSim < 0.5) {
//				unresolvedCIDs.add(new Integer(glyph.cid));
//				continue;
//			}
//			
//			//	what do we have?
//			if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" --> best similarity is " + bestSim + " / " + bestSimNs + " for " + bestSimCh);
//			
//			//	remember small-caps match
//			if (bestSimCh != chars[c]) {
//				smallCapsCIDs.add(new Integer(glyph.cid));
//				if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" --> small-caps match");
//			}
//			
//			//	update overall measures
//			if (!ignoreForMin) {
//				if (bestSim >= 0)
//					simMin = Math.min(simMin, bestSim);
//				if (bestSimNs >= 0)
//					simMinNs = Math.min(simMinNs, bestSimNs);
//			}
//			if (bestSim >= 0)
//				simSum += bestSim;
//			if (bestSimNs >= 0)
//				simSumNs += bestSimNs;
		}
//		checkImplicitSpaces(pFont, charImages, chars, glyphData);
		if (fidd != null) {
			fidd.setLocationRelativeTo(null);
			fidd.setSize(600, 400);
			fidd.setVisible(true);
		}
//		
//		//	use maximum of all fonts and styles when computing min and average similarity
//		pm.setInfo("   - detecting font style");
//		if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - min similarity is " + simMin + " all / " + simMinNs + " non-skewed");
//		double sim = ((charCount == 0) ? 0 : (simSum / charCount));
//		double simNs = ((charCountNs == 0) ? 0 : (simSumNs / charCountNs));
//		if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - average similarity is " + sim + " (" + charCount + ") all / " + simNs + " (" + charCountNs + ") non-skewed");
//		
//		//	do we have a match? (be more strict with fewer chars, as fonts with few chars tend to be the oddjobs)
//		if (((simMin > 0.5) && (sim > 0.6)) || ((simMin > 0.375) && (sim > 0.7)) || ((simMin > 0.25) && (sim > 0.8))) {
//			if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" ==> match");
//			
//			//	try to select font style if pool sufficiently large
//			int bestStyle = -1;
//			boolean serif = true;
//			if (Math.max(serifStyleSimSums.length, sansStyleSimSums.length) >= 2) {
//				double bestStyleSim = 0;
//				for (int s = Font.PLAIN; s <= (Font.BOLD | Font.ITALIC); s++) {
//					if (DEBUG_TRUE_TYPE_LOADING) {
//						System.out.println(" - checking style " + s);
//						System.out.println("   - min similarity is " + serifStyleSimMins[s] + "/" + sansStyleSimMins[s] + " all / " + serifStyleSimMinsNs[s] + "/" + sansStyleSimMinsNs[s] + " non-skewed");
//					}
//					double serifStyleSim = ((serifCharCounts[s] == 0) ? 0 : (serifStyleSimSums[s] / serifCharCounts[s]));
//					double serifStyleSimNs = ((serifCharCountsNs[s] == 0) ? 0 : (serifStyleSimSumsNs[s] / serifCharCountsNs[s]));
//					double sansStyleSim = ((sansCharCounts[s] == 0) ? 0 : (sansStyleSimSums[s] / sansCharCounts[s]));
//					double sansStyleSimNs = ((sansCharCountsNs[s] == 0) ? 0 : (sansStyleSimSumsNs[s] / sansCharCountsNs[s]));
//					if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - average similarity is " + serifStyleSim + "/" + sansStyleSim + " all / " + serifStyleSimNs + "/" + sansStyleSimNs + " non-skewed");
//					if ((((s & Font.ITALIC) == 0) ? Math.max(serifStyleSim, sansStyleSim) : Math.max(serifStyleSimNs, sansStyleSimNs)) > bestStyleSim) {
//						if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" ==> new best match style");
//						bestStyleSim = (((s & Font.ITALIC) == 0) ? Math.max(serifStyleSim, sansStyleSim) : Math.max(serifStyleSimNs, sansStyleSimNs));
//						bestStyle = s;
//						serif = (((s & Font.ITALIC) == 0) ? (serifStyleSim >= sansStyleSim) : (serifStyleSimNs >= sansStyleSimNs));
//						if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" ==> serif is " + serif);
//					}
//				}
//			}
//			
//			//	style not found, use plain
//			if (bestStyle == -1)
//				bestStyle = 0;
//			
//			//	set base font according to style
//			pFont.bold = ((Font.BOLD & bestStyle) != 0);
//			pFont.italics = ((Font.ITALIC & bestStyle) != 0);
//			pFont.serif = serif;
//			System.out.println(" ==> font decoded");
//			if ((pFont.baseFont == null) || (pFont.bold != pFont.baseFont.bold) || (pFont.italics != pFont.baseFont.italic)) {
//				String bfn = "Times-";
//				if (pFont.bold && pFont.italics)
//					bfn += "BoldItalic";
//				else if (pFont.bold)
//					bfn += "Bold";
//				else if (pFont.italics)
//					bfn += "Italic";
//				else bfn += "Roman";
//				pFont.setBaseFont(bfn, false);
//			}
//			
//			//	check for descent
//			pFont.hasDescent = ((maxDescent < -150) || (pFont.descent < -0.150));
//			
//			//	collect all chars, and get some stats
//			HashSet pFontChars = new HashSet();
//			int xHeightSum = 0;
//			int xHeightCount = 0;
//			int capHeightSum = 0;
//			int capHeightCount = 0;
//			for (int c = 0; c < glyphData.size(); c++) {
//				if (charImages[c] == null)
//					continue;
//				pFontChars.add(new Character(chars[c]));
//				if ("aemnru".indexOf(StringUtils.getBaseChar(chars[c])) != -1) {
//					xHeightSum += charImages[c].box.getHeight();
//					xHeightCount++;
//				}
//				else if ("ABDEFGHIKLMNPRTUYbdfhkl0123456789".indexOf(StringUtils.getBaseChar(chars[c])) != -1) {
//					capHeightSum += charImages[c].box.getHeight();
//					capHeightCount++;
//				}
//			}
//			int capHeight = ((capHeightCount == 0) ? 0 : (capHeightSum / capHeightCount));
//			int xHeight = ((xHeightCount == 0) ? 0 : (xHeightSum / xHeightCount));
//			if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - average cap-height is " + capHeight + ", average x-height is " + xHeight);
//			
//			//	rectify small-caps
//			for (int c = 0; c < glyphData.size(); c++) {
//				if (charImages[c] == null)
//					continue;
//				GlyphDataTrueType glyph = ((GlyphDataTrueType) glyphData.get(c));
//				if (!smallCapsCIDs.contains(new Integer(glyph.cid)))
//					continue;
//				if (pFontChars.contains(new Character(Character.toUpperCase(chars[c]))))
//					continue;
//				if ((xHeight < capHeight) && (Math.abs(charImages[c].box.getHeight() - xHeight) < Math.abs(charImages[c].box.getHeight() - capHeight)))
//					continue;
//				pFont.mapUnicode(new Integer(glyph.cid), ("" + Character.toUpperCase(chars[c])));
//				pFont.setCharImage(glyph.cid, StringUtils.getCharName(Character.toUpperCase(chars[c])), charImages[c].img);
//				if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" ==> small-caps corrected " + glyph.cid + " to " + Character.toUpperCase(chars[c]));
//			}
//			
//			//	we're done here
//			if (unresolvedCIDs.isEmpty())
//				return;
//		}
//		
//		//	reset chars with known names to mark them for re-evaluation (required with fonts that use arbitrary char names)
//		else {
//			if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" ==> mis-match");
//			Arrays.fill(chars, ((char) 0));
//			for (int c = 0; c < glyphData.size(); c++) {
//				GlyphDataTrueType glyph = ((GlyphDataTrueType) glyphData.get(c));
//				unresolvedCIDs.add(new Integer(glyph.cid));
//			}
//		}
//		
//		//	cache character images to speed up matters
//		pm.setInfo("   - OCR decoding remaining characters");
//		final float[] cacheHitRate = {0};
//		HashMap cache = new HashMap() {
//			int lookups = 0;
//			int hits = 0;
//			public Object get(Object key) {
//				this.lookups ++;
//				Object value = super.get(key);
//				if (value != null)
//					this.hits++;
//				cacheHitRate[0] = (((float) this.hits) / this.lookups);
//				return value;
//			}
//		};
//		
//		//	get matches for remaining characters, and measure xHeight and capHeight
//		CharImageMatch[] bestCims = new CharImageMatch[glyphData.size()];
//		int xHeightSum = 0;
//		int xHeightCount = 0;
//		int capHeightSum = 0;
//		int capHeightCount = 0;
//		for (int c = 0; c < glyphData.size(); c++) {
//			if (charImages[c] == null)
//				continue;
//			
//			//	get basic data
//			GlyphDataTrueType glyph = ((GlyphDataTrueType) glyphData.get(c));
//			String chn = null;
//			String ucCh = pFont.getUnicode(glyph.cid);
//			if ((ucCh != null) && (ucCh.length() > 0)) {
//				chars[c] = ucCh.charAt(0);
//				chn = StringUtils.getCharName(chars[c]);
//			}
//			pm.setInfo("     - OCR decoding char " + c + " with CID " + glyph.cid + " (" + ucCh + ", " + chn + ")");
//			if (DEBUG_TRUE_TYPE_LOADING) System.out.println("Decoding char " + c + " with CID " + glyph.cid + " (" + ucCh + ", " + chn + ")");
//			
//			//	perform match
//			bestCims[c] = getCharForImage(charImages[c], serifFonts, sansFonts, cache, false);
//			if (bestCims[c] == null)
//				continue;
//			
//			//	take measurements
//			if ("aemnru".indexOf(StringUtils.getBaseChar(bestCims[c].match.ch)) != -1) {
//				xHeightSum += charImages[c].box.getHeight();
//				xHeightCount++;
//			}
//			else if ("ABDEFGHIKLMNPRTUYbdfhkl0123456789".indexOf(StringUtils.getBaseChar(bestCims[c].match.ch)) != -1) {
//				capHeightSum += charImages[c].box.getHeight();
//				capHeightCount++;
//			}
//		}
//		int capHeight = ((capHeightCount == 0) ? 0 : (capHeightSum / capHeightCount));
//		int xHeight = ((xHeightCount == 0) ? 0 : (xHeightSum / xHeightCount));
//		if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - average cap-height is " + capHeight + ", average x-height is " + xHeight);
//		
//		//	decode remaining characters
//		for (int c = 0; c < glyphData.size(); c++) {
//			
//			//	don't try to match space or unused chars
//			if (charImages[c] == null)
//				continue;
//			
//			//	we don't have a match for this one
//			if (bestCims[c] == null)
//				continue;
//			
//			//	get basic data
//			GlyphDataTrueType glyph = ((GlyphDataTrueType) glyphData.get(c));
//			if (!unresolvedCIDs.contains(new Integer(glyph.cid)))
//				continue;
//			String chn = null;
//			String ucCh = pFont.getUnicode(glyph.cid);
//			if ((ucCh != null) && (ucCh.length() > 0)) {
//				chars[c] = ucCh.charAt(0);
//				chn = StringUtils.getCharName(chars[c]);
//			}
//			if (DEBUG_TRUE_TYPE_LOADING) System.out.println("Decoding char " + c + " with CID " + glyph.cid + " (" + ucCh + ", " + chn + ")");
//			
//			//	catch lower case COSVWXZ mistaken for upper case
//			boolean isLowerCase = (xHeight < capHeight) && (Math.abs(charImages[c].box.getHeight() - xHeight) < Math.abs(charImages[c].box.getHeight() - capHeight));
//			
//			//	do we have a reliable match?
//			if (bestCims[c].sim > 0.8) {
//				if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" ==> char decoded (1) to '" + bestCims[c].match.ch + "' (" + StringUtils.getCharName(bestCims[c].match.ch) + ", '" + StringUtils.getNormalForm(bestCims[c].match.ch) + "') at " + bestCims[c].sim + ", cache hit rate at " + cacheHitRate[0]);
////				Character bsCh = new Character(bestCims[c].match.ch);
//				Character bsCh;
//				if (isLowerCase && ("COSVWXZ".indexOf(bestCims[c].match.ch) != -1))
//					bsCh = new Character(Character.toLowerCase(bestCims[c].match.ch));
//				else bsCh = new Character(bestCims[c].match.ch);
//				if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" ==> mapped (1) " + glyph.cid + " to " + bsCh);
//				
//				//	correct char mapping specified in differences array
//				pFont.mapDifference(new Integer(glyph.cid), bsCh, null);
//				pFont.mapUnicode(new Integer(glyph.cid), StringUtils.getNormalForm(bsCh.charValue()));
//				
//				//	store char image
//				chn = StringUtils.getCharName(bsCh.charValue());
//				pFont.setCharImage(glyph.cid, chn, charImages[c].img);
//				
//				//	no need to hassle about this char any more
//				continue;
//			}
//			
//			//	use whatever we got
//			else {
//				if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" ==> char decoded (2) to '" + bestCims[c].match.ch + "' (" + StringUtils.getCharName(bestCims[c].match.ch) + ", '" + StringUtils.getNormalForm(bestCims[c].match.ch) + "') at " + bestCims[c].sim + ", cache hit rate at " + cacheHitRate[0]);
////				if (DEBUG_TRUE_TYPE_LOADING && (bestSim.sim < 0.7))
////					displayCharMatch(imgs[c], bestSim, "Best Match");
////				Character bsCh = new Character(bestCims[c].match.ch);
//				Character bsCh;
//				if (isLowerCase && ("COSVWXZ".indexOf(bestCims[c].match.ch) != -1))
//					bsCh = new Character(Character.toLowerCase(bestCims[c].match.ch));
//				else bsCh = new Character(bestCims[c].match.ch);
//				if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" ==> mapped (2) " + glyph.cid + " to " + bsCh);
//				
//				//	correct char mapping specified in differences array
//				pFont.mapDifference(new Integer(glyph.cid), bsCh, null);
//				pFont.mapUnicode(new Integer(glyph.cid), StringUtils.getNormalForm(bsCh.charValue()));
//				
//				//	store char image
//				chn = StringUtils.getCharName(bsCh.charValue());
//				pFont.setCharImage(glyph.cid, chn, charImages[c].img);
//				
//				//	check for descent
//				pFont.hasDescent = ((maxDescent < -150) || (pFont.descent < -0.150));
//			}
//		}
	}
	
	private static final int readUnsigned(byte[] bytes, int s, int e) {
		int ui = convertUnsigned(bytes[s++]);
		for (; s < e;) {
			ui <<= 8;
			ui += convertUnsigned(bytes[s++]);
		}
		return ui;
	}
	
	private static final int convertUnsigned(byte b) {
		return ((b < 0) ? (((int) b) + 256) : b);
	}
//	
//	private static void checkImplicitSpaces(PdfFont pFont, CharImage[] charImages, char[] chars, ArrayList glyphData) {
//		
//		//	rectify small-caps
//		for (int c = 0; c < glyphData.size(); c++) {
//			if (charImages[c] == null)
//				continue;
//			GlyphDataTrueType glyph = ((GlyphDataTrueType) glyphData.get(c));
//		}
//		
//		//	measure maximum char height and relation to nominal font box
//		int maxCharHeight = 0;
//		for (int c = 0; c < charImages.length; c++) {
//			if (charImages[c] != null)
//				maxCharHeight = Math.max(maxCharHeight, charImages[c].box.getHeight());
//		}
//		float charHeightRel = (maxCharHeight / (pFont.ascent - pFont.descent));
//		if (DEBUG_TYPE1C_LOADING) System.out.println(" - maximum height is " + maxCharHeight + " for " + (pFont.ascent - pFont.descent) + ", relation is " + charHeightRel);
//		
//		//	compare nominal to measured char widths
//		float ncCharWidthRelSum = 0;
//		int ncCharWidthRelCount = 0;
//		for (int c = 0; c < glyphData.size(); c++) {
//			if (charImages[c] == null)
//				continue;
//			
//			//	get basic data
//			GlyphDataTrueType glyph = ((GlyphDataTrueType) glyphData.get(c));
//			
//			//	get nominal width and compare to actual width
//			float nCharWidth = pFont.getCharWidth(new Character((char) glyph.cid));
//			float nCharWidthRel = ((charImages[c].box.getWidth() * 1000) / nCharWidth);
//			if (DEBUG_TYPE1C_LOADING) System.out.println(" - nominal width of char " + c + " (CID " + glyph.cid + ", " + StringUtils.getCharName(chars[c]) + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ") is " + nCharWidth);
//			if (DEBUG_TYPE1C_LOADING) System.out.println(" - actual width is " + charImages[c].box.getWidth() + ", relation is " + nCharWidthRel);
//			float cCharWidth = ((nCharWidth * nCharWidthRel) / charHeightRel);
//			float ncCharWidthRel = (nCharWidth / cCharWidth);
//			if (DEBUG_TYPE1C_LOADING) System.out.println(" --> computed width is " + cCharWidth + ", relation is " + ncCharWidthRel);
//			ncCharWidthRelSum += ncCharWidthRel;
//			ncCharWidthRelCount++;
//		}
//		float avgNcCharWidthRel = ((ncCharWidthRelCount == 0) ? 0 : (ncCharWidthRelSum / ncCharWidthRelCount));
//		if (DEBUG_TYPE1C_LOADING) System.out.println(" --> average nominal char width to computed char width relation is " + avgNcCharWidthRel + " from " + ncCharWidthRelCount + " chars");
//		
//		//	this font seams to indicate sincere char widths
//		if (avgNcCharWidthRel < 1.5) {
//			if (DEBUG_TYPE1C_LOADING) System.out.println(" ==> char widths appear to make sense");
//			return;
//		}
//		
//		//	nominal char width way larger than measured char width, we have implicit spaces
//		if (DEBUG_TYPE1C_LOADING) System.out.println(" ==> char widths appear to imply spaces");
//		pFont.hasImplicitSpaces = true;
//		
//		//	add measured char widths
//		for (int c = 0; c < glyphData.size(); c++) {
//			if (charImages[c] == null)
//				continue;
//			
//			//	get basic data
//			GlyphDataTrueType glyph = ((GlyphDataTrueType) glyphData.get(c));
//			
//			//	store actual width
//			float nCharWidth = pFont.getCharWidth(new Character((char) glyph.cid));
//			float nCharWidthRel = ((charImages[c].box.getWidth() * 1000) / nCharWidth);
//			float cCharWidth = ((nCharWidth * nCharWidthRel) / charHeightRel);
//			pFont.setMeasuredCharWidth(new Integer(glyph.cid), cCharWidth);
//		}
//	}

	private static GlyphDataTrueType readGlyphTrueType(int start, int end, byte[] glyfBytes, int cid, int glyphIndex) {
		int glyfByteOffset = start;
		int numberOfContours = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+2));
		glyfByteOffset += 2;
		if (numberOfContours > 32768)
			numberOfContours -= 65536;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("     - number of contours is " + numberOfContours);
		int hMinX = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+2));
		glyfByteOffset += 2;
		if (hMinX > 32768)
			hMinX -= 65536;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("     - min X is " + hMinX);
		int hMinY = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+2));
		glyfByteOffset += 2;
		if (hMinY > 32768)
			hMinY -= 65536;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("     - min Y is " + hMinY);
		int hMaxX = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+2));
		glyfByteOffset += 2;
		if (hMaxX > 32768)
			hMaxX -= 65536;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("     - max X is " + hMaxX);
		int hMaxY = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+2));
		glyfByteOffset += 2;
		if (hMaxY > 32768)
			hMaxY -= 65536;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("     - max Y is " + hMaxY);
		if (numberOfContours < 0)
			return readCompoundGlyphTrueType(glyfByteOffset, end, glyfBytes, cid, glyphIndex, -numberOfContours);
		else return readSimpleGlyphTrueType(glyfByteOffset, end, glyfBytes, cid, glyphIndex, numberOfContours);
	}
	
	private static SimpleGlyphDataTrueType readSimpleGlyphTrueType(int start, int end, byte[] glyfBytes, int cid, int glyphIndex, int numberOfContours) {
		int glyfByteOffset = start;
		int[] contourEnds = new int[numberOfContours];
		int numPoints = 0;
		for (int n = 0; n < numberOfContours; n++) {
			contourEnds[n] = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+2));
			glyfByteOffset += 2;
			numPoints = (contourEnds[n]+1);
		}
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("     - contour ends are " + Arrays.toString(contourEnds));
		int instructionLength = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+2));
		glyfByteOffset += 2;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("     - instruction length is " + instructionLength);
		glyfByteOffset += instructionLength;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("     - instructions skipped");
//		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("     - instructions skipped: " + new String(glyfBytes, (glyfByteOffset - instructionLength), instructionLength));
		
		int[] pointFlags = new int[numPoints];
		for (int p = 0; p < numPoints;) {
			int flags = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+1));
			glyfByteOffset += 1;
			if ((flags & 8) == 0)
				pointFlags[p++] = flags;
			else {
				pointFlags[p++] = (flags ^ 8);
				int repeat = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+1));
				glyfByteOffset += 1;
				while (repeat-- != 0)
					pointFlags[p++] = (flags ^ 8);
			}
		}
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("     - flags are " + Arrays.toString(pointFlags));
		
		int[] pointXs = new int[numPoints];
		for (int p = 0; p < numPoints; p++) {
			if ((pointFlags[p] & 2) == 0) {
				if ((pointFlags[p] & 16) == 0) {
					pointXs[p] = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+2));
					glyfByteOffset += 2;
					if (pointXs[p] > 32768)
						pointXs[p] -= 65536;
				}
				else pointXs[p] = 0;
			}
			else {
				pointXs[p] = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+1));
				glyfByteOffset += 1;
				if ((pointFlags[p] & 16) == 0)
					pointXs[p] = -pointXs[p];
			}
		}
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("     - point Xs are " + Arrays.toString(pointXs));
		int[] pointYs = new int[numPoints];
		for (int p = 0; p < numPoints; p++) {
			if ((pointFlags[p] & 4) == 0) {
				if ((pointFlags[p] & 32) == 0) {
					pointYs[p] = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+2));
					glyfByteOffset += 2;
					if (pointYs[p] > 32768)
						pointYs[p] -= 65536;
				}
				else pointYs[p] = 0;
			}
			else {
				pointYs[p] = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+1));
				glyfByteOffset += 1;
				if ((pointFlags[p] & 32) == 0)
					pointYs[p] = -pointYs[p];
			}
		}
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("     - point Ys are " + Arrays.toString(pointYs));
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("     - got to " + glyfByteOffset + ", end is " + end);
		if (DEBUG_TRUE_TYPE_LOADING && ((glyfByteOffset + 1) < end)) {
			System.out.print("     - remainder is");
			while (glyfByteOffset < end)
				System.out.print(" " + Integer.toString(convertUnsigned(glyfBytes[glyfByteOffset++]), 16));
			System.out.println();
		}
		
		return new SimpleGlyphDataTrueType(cid, glyphIndex, pointFlags, pointXs, pointYs, contourEnds);
	}
	
	private static CompoundGlyphDataTrueType readCompoundGlyphTrueType(int start, int end, byte[] glyfBytes, int cid, int glyphIndex, int numberOfContours) {
		int glyfByteOffset = start;
		ArrayList components = new ArrayList(2);
		while (true) {
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println("     - reading component " + components.size());
			int flags = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+2));
			glyfByteOffset += 2;
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - flags are " + Integer.toString(flags, 2));
			int glyfIndex = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+2));
			glyfByteOffset += 2;
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - glyph index is " + glyfIndex);
			int arg1;
			int arg2;
			if ((flags & 1) == 0) {
				arg1 = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+1));
				glyfByteOffset += 1;
				arg2 = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+1));
				glyfByteOffset += 1;
			}
			else {
				arg1 = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+2));
				glyfByteOffset += 2;
				arg2 = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+2));
				glyfByteOffset += 2;
			}
			if (arg1 > 32768)
				arg1 -= 65536;
			if (arg2 > 32768)
				arg2 -= 65536;
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - arguments are " + arg1 + " and " + arg2);
			
			int[] opts;
			if ((flags & 128) != 0) {
				opts = new int[4];
				opts[0] = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+2));
				if (opts[0] > 32768)
					opts[0] -= 65536;
				glyfByteOffset += 2;
				opts[1] = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+2));
				if (opts[1] > 32768)
					opts[1] -= 65536;
				glyfByteOffset += 2;
				opts[2] = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+2));
				if (opts[2] > 32768)
					opts[2] -= 65536;
				glyfByteOffset += 2;
				opts[3] = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+2));
				if (opts[3] > 32768)
					opts[3] -= 65536;
				glyfByteOffset += 2;
				if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - options are " + Arrays.toString(opts));
			}
			else if ((flags & 64) != 0) {
				opts = new int[2];
				opts[0] = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+2));
				if (opts[0] > 32768)
					opts[0] -= 65536;
				glyfByteOffset += 2;
				opts[1] = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+2));
				if (opts[1] > 32768)
					opts[1] -= 65536;
				glyfByteOffset += 2;
				if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - options are " + Arrays.toString(opts));
			}
			else if ((flags & 8) != 0) {
				opts = new int[1];
				opts[0] = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+2));
				if (opts[0] > 32768)
					opts[0] -= 65536;
				glyfByteOffset += 2;
				if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - options are " + Arrays.toString(opts));
			}
			else opts = new int[0];
			
			components.add(new CompoundGlyphDataTrueType.Component(flags, glyfIndex, arg1, arg2, opts));
			
			if ((flags & 32) == 0)
				break;
		}
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("     - got to " + glyfByteOffset + ", end is " + end);
		if (DEBUG_TRUE_TYPE_LOADING && ((glyfByteOffset + 1) < end)) {
			System.out.print("     - remainder is");
			while (glyfByteOffset < end)
				System.out.print(" " + Integer.toString(convertUnsigned(glyfBytes[glyfByteOffset++]), 16));
			System.out.println();
		}
		
		return new CompoundGlyphDataTrueType(cid, glyphIndex, ((CompoundGlyphDataTrueType.Component[]) components.toArray(new CompoundGlyphDataTrueType.Component[components.size()])));
	}
	
	private static final boolean DEBUG_TRUE_TYPE_RENDERING = true;
	private static final int DEBUG_TRUE_TYPE_TARGET_CID = 48;
	
	private static void runFontTrueTypeOps(CompoundGlyphDataTrueType cGlyph, HashMap glyphsByIndex, OpReceiverTrueType opr, boolean show) {
		runFontTrueTypeOps(cGlyph, false, 0, 0, glyphsByIndex, opr, show);
	}
	
	private static void runFontTrueTypeOps(CompoundGlyphDataTrueType cGlyph, boolean isComponent, int x, int y, HashMap glyphsByIndex, OpReceiverTrueType opr, boolean show) {
		ImageDisplayDialog idd = null;
		
		//	render components
//		int arg1Sum = 0;
//		int arg2Sum = 0;
		for (int c = 0; c < cGlyph.components.length; c++) {
			GlyphDataTrueType glyph = ((GlyphDataTrueType) glyphsByIndex.get(new Integer(cGlyph.components[c].glyphIndex)));
			
			//	can happen for spaces
			if (glyph == null) {
				System.out.println("Component glyph " + cGlyph.components[c].glyphIndex + " not found");
				continue;
			}
			
			//	scale, or reset scaling
			if (cGlyph.components[c].opts.length == 1)
				opr.scale(((double) cGlyph.components[c].opts[0]) / 16384);
			else if (cGlyph.components[c].opts.length == 2)
				opr.scale((((double) cGlyph.components[c].opts[0]) / 16384), (((double) cGlyph.components[c].opts[1]) / 16384));
			else if (cGlyph.components[c].opts.length == 4) {
				
			}
			else opr.scale(1);
			
			//	do actual rendering
			if (glyph instanceof SimpleGlyphDataTrueType) {
				
				//	adjust position
				if ((cGlyph.components[c].flags & 2) == 0) {
					/* If [flag 1] not set, args are points:
				     * - 1st arg contains the index of matching point in compound being constructed
				     * - 2nd arg contains index of matching point in component
				     * ==> might have to collect points in this case */
				}
				
				//	If [flag 1] set, the arguments are xy values;
				else opr.moveToAbs((x + cGlyph.components[c].arg1), (y + cGlyph.components[c].arg2));
//				else {
//					arg1Sum += cGlyph.components[c].arg1;
//					arg2Sum += cGlyph.components[c].arg2;
//					opr.moveToAbs((x + arg1Sum), (y + arg2Sum));
//				}
//				else opr.moveTo(cGlyph.components[c].arg1, cGlyph.components[c].arg2);
				
				//	draw glyph
				runFontTrueTypeOps(((SimpleGlyphDataTrueType) glyph), true, opr, show);
			}
			else runFontTrueTypeOps(((CompoundGlyphDataTrueType) glyph), true, (x + cGlyph.components[c].arg1), (y + cGlyph.components[c].arg2), glyphsByIndex, opr, show);
			
			if ((opr instanceof OpGraphicsTrueType) && show) {
				BufferedImage dImg = new BufferedImage(((OpGraphicsTrueType) opr).img.getWidth(), ((OpGraphicsTrueType) opr).img.getHeight(), BufferedImage.TYPE_INT_RGB);
				Graphics g = dImg.getGraphics();
				g.drawImage(((OpGraphicsTrueType) opr).img, 0, 0, dImg.getWidth(), dImg.getHeight(), null);
				g.setColor(Color.RED);
				if (idd == null) {
					idd = new ImageDisplayDialog("Rendering Progress");
					idd.setSize((dImg.getWidth() + 200), (dImg.getHeight() + 100));
				}
				idd.addImage(dImg, ("Component " + c));
				idd.setLocationRelativeTo(null);
				idd.setVisible(true);
			}
		}
		
		//	only tracking, we're done here
		if (isComponent || (opr instanceof OpTrackerTrueType))
			return;
		
		//	fill outline
		fillGlyphOutline(((OpGraphicsTrueType) opr).img, ((OpGraphicsTrueType) opr).scale, show);
		
		//	scale down image
		int maxHeight = 300;
		((OpGraphicsTrueType) opr).setImage(scaleImage(((OpGraphicsTrueType) opr).img, maxHeight));
	}
	
	private static void runFontTrueTypeOps(SimpleGlyphDataTrueType sGlyph, boolean isComponent, OpReceiverTrueType opr, boolean show) {
		ImageDisplayDialog idd = null;
		
		//	run points
		int pointIndex = 0;
		for (int c = 0; c < sGlyph.contourEnds.length; c++) {
			if ((pointIndex == sGlyph.pointXs.length) || (pointIndex == sGlyph.pointYs.length)) {
				if (DEBUG_TRUE_TYPE_LOADING) System.out.println("     - problem in glyph, end of points reached before end of contour ends");
				opr.closePath();
				break;
			}
			opr.moveTo(sGlyph.pointXs[pointIndex], sGlyph.pointYs[pointIndex]);
			pointIndex += 1;
			boolean lastWasInterpolated = false;
			while (pointIndex < sGlyph.pointFlags.length) {
				if ((sGlyph.pointFlags[pointIndex] & 1) == 0) {
					int dx1 = (sGlyph.pointXs[pointIndex] / (lastWasInterpolated ? 2 : 1));
					int dy1 = (sGlyph.pointYs[pointIndex] / (lastWasInterpolated ? 2 : 1));
					pointIndex += 1;
					if (sGlyph.contourEnds[c] == (pointIndex-1)) {
						opr.closePath(dx1, dy1);
						break;
					}
					else if ((sGlyph.pointFlags[pointIndex] & 1) == 0) {
						opr.curveTo(dx1, dy1, (sGlyph.pointXs[pointIndex] / 2), (sGlyph.pointYs[pointIndex] / 2));
						lastWasInterpolated = true;
					}
					else {
						opr.curveTo(dx1, dy1, sGlyph.pointXs[pointIndex], sGlyph.pointYs[pointIndex]);
						pointIndex += 1;
						lastWasInterpolated = false;
					}
				}
				else {
					opr.lineTo((sGlyph.pointXs[pointIndex] / (lastWasInterpolated ? 2 : 1)), (sGlyph.pointYs[pointIndex] / (lastWasInterpolated ? 2 : 1)));
					pointIndex += 1;
					lastWasInterpolated = false;
				}
				
				if ((opr instanceof OpGraphicsTrueType) && show) {
					BufferedImage dImg = new BufferedImage(((OpGraphicsTrueType) opr).img.getWidth(), ((OpGraphicsTrueType) opr).img.getHeight(), BufferedImage.TYPE_INT_RGB);
					Graphics g = dImg.getGraphics();
					g.drawImage(((OpGraphicsTrueType) opr).img, 0, 0, dImg.getWidth(), dImg.getHeight(), null);
					g.setColor(Color.RED);
					if (idd == null) {
						idd = new ImageDisplayDialog("Rendering Progress");
						idd.setSize((dImg.getWidth() + 200), (dImg.getHeight() + 100));
					}
					idd.addImage(dImg, ("Point " + pointIndex));
					idd.setLocationRelativeTo(null);
					idd.setVisible(true);
				}
				
				if (sGlyph.contourEnds[c] == (pointIndex-1)) {
					opr.closePath();
					break;
				}
			}
			
			if ((opr instanceof OpGraphicsTrueType) && show) {
				BufferedImage dImg = new BufferedImage(((OpGraphicsTrueType) opr).img.getWidth(), ((OpGraphicsTrueType) opr).img.getHeight(), BufferedImage.TYPE_INT_RGB);
				Graphics g = dImg.getGraphics();
				g.drawImage(((OpGraphicsTrueType) opr).img, 0, 0, dImg.getWidth(), dImg.getHeight(), null);
				g.setColor(Color.RED);
				if (idd == null) {
					idd = new ImageDisplayDialog("Rendering Progress");
					idd.setSize((dImg.getWidth() + 200), (dImg.getHeight() + 100));
				}
				idd.addImage(dImg, ("Point " + pointIndex));
				idd.setLocationRelativeTo(null);
				idd.setVisible(true);
			}
		}
		
		//	only tracking, we're done here
		if ((opr instanceof OpTrackerTrueType) || isComponent)
			return;
		
		//	fill outline
		fillGlyphOutline(((OpGraphicsTrueType) opr).img, ((OpGraphicsTrueType) opr).scale, show);
		
		//	scale down image
		int maxHeight = 300;
		((OpGraphicsTrueType) opr).setImage(scaleImage(((OpGraphicsTrueType) opr).img, maxHeight));
	}
	
	private static class GlyphDataTrueType {
		int cid; // TODO need to diestinguish CID from glyph index
		int gid;
		char ch;
		GlyphDataTrueType(int cid, int gid) {
			this.cid = cid;
			this.gid = gid;
		}
	}
	
	private static class SimpleGlyphDataTrueType extends GlyphDataTrueType {
		int[] pointFlags;
		int[] pointXs;
		int[] pointYs;
		int[] contourEnds;
		SimpleGlyphDataTrueType(int cid, int gid, int[] pointFlags, int[] pointXs, int[] pointYs, int[] contourEnds) {
			super(cid, gid);
			this.pointFlags = pointFlags;
			this.pointXs = pointXs;
			this.pointYs = pointYs;
			this.contourEnds = contourEnds;
		}
	}
	
	private static class CompoundGlyphDataTrueType extends GlyphDataTrueType {
		Component[] components;
		CompoundGlyphDataTrueType(int cid, int gid, Component[] components) {
			super(cid, gid);
			this.components = components;
		}
		static class Component {
			int flags;
			int glyphIndex;
			int arg1;
			int arg2;
			int[] opts;
			Component(int flags, int glyphIndex, int arg1, int arg2, int[] opts) {
				this.flags = flags;
				this.glyphIndex = glyphIndex;
				this.arg1 = arg1;
				this.arg2 = arg2;
				this.opts = opts;
			}
		}
	}
	
	private static abstract class OpReceiverTrueType {
		int rWidth = 0;
		int x = 0;
		int y = 0;
		double scaleX = 1.0;
		double scaleY = 1.0;
		abstract void moveTo(int dx, int dy);
		abstract void moveToAbs(int x, int y);
		abstract void lineTo(int dx, int dy);
		abstract void curveTo(int dx1, int dy1, int dx2, int dy2);
		abstract void closePath();
		abstract void closePath(int dx, int dy);
		void scale(double s) {
			this.scale(s, s);
		}
		void scale(double sx, double sy) {
			this.scaleX = sx;
			this.scaleY = sy;
		}
	}
	
	private static class OpTrackerTrueType extends OpReceiverTrueType {
//		String id = ("" + Math.random());
		int minX = 0;
		int minY = 0;
		int minPaintX = Integer.MAX_VALUE;
		int minPaintY = Integer.MAX_VALUE;
		int maxX = 0;
		int maxY = 0;
		int mCount = 0;
		boolean isMultiPath = false;
		int cid;
		OpTrackerTrueType(int cid) {
			this.cid = cid;
		}
		void moveTo(int dx, int dy) {
			if (this.mCount != 0)
				this.isMultiPath = true;
			dx = ((int) (dx * this.scaleX));
			dy = ((int) (dy * this.scaleY));
			this.x += dx;
			this.minX = Math.min(this.minX, this.x);
			this.maxX = Math.max(this.maxX, this.x);
			this.y += dy;
			this.minY = Math.min(this.minY, this.y);
			this.maxY = Math.max(this.maxY, this.y);
//			System.out.println("Move " + this.id + " to " + dx + "/" + dy + ":");
//			System.out.println(" " + this.minX + " < X < " + this.maxX);
//			System.out.println(" " + this.minY + " < Y < " + this.maxY);
		}
		void moveToAbs(int x, int y) {
			if (this.mCount != 0)
				this.isMultiPath = true;
			this.x = x;
			this.minX = Math.min(this.minX, this.x);
			this.maxX = Math.max(this.maxX, this.x);
			this.y = y;
			this.minY = Math.min(this.minY, this.y);
			this.maxY = Math.max(this.maxY, this.y);
//			System.out.println("Move " + this.id + " to " + dx + "/" + dy + ":");
//			System.out.println(" " + this.minX + " < X < " + this.maxX);
//			System.out.println(" " + this.minY + " < Y < " + this.maxY);
		}
		void lineTo(int dx, int dy) {
			dx = ((int) (dx * this.scaleX));
			dy = ((int) (dy * this.scaleY));
			this.lineTo(dx, dy, true);
		}
		private void lineTo(int dx, int dy, boolean isLine) {
			if (isLine) {
				this.minPaintX = Math.min(this.minPaintX, this.x);
				this.minPaintY = Math.min(this.minPaintY, this.y);
			}
			this.x += dx;
			if (isLine) {
				this.minX = Math.min(this.minX, this.x);
				this.maxX = Math.max(this.maxX, this.x);
			}
			this.y += dy;
			if (isLine) {
				this.minY = Math.min(this.minY, this.y);
				this.maxY = Math.max(this.maxY, this.y);
			}
			if (isLine) {
				this.minPaintX = Math.min(this.minPaintX, this.x);
				this.minPaintY = Math.min(this.minPaintY, this.y);
			}
			this.mCount++;
			if (isLine && (Math.abs(dy) > Math.abs(dx))) {
				//	TODO measure Y delta, and if line long enough, remember angle of primary stroke
			}
//			System.out.println("Line " + this.id + " to " + dx + "/" + dy + ":");
//			System.out.println(" " + this.minX + " < X < " + this.maxX);
//			System.out.println(" " + this.minY + " < Y < " + this.maxY);
			//	TODO measure where line is going, length, angle, etc.
			//	TODO add flagged version of this, disabling min/max measurements, to handle curveTo() operations
		}
		void curveTo(int dx1, int dy1, int dx2, int dy2) {
			dx1 = ((int) (dx1 * this.scaleX));
			dy1 = ((int) (dy1 * this.scaleY));
			dx2 = ((int) (dx2 * this.scaleX));
			dy2 = ((int) (dy2 * this.scaleY));
			
			this.minPaintX = Math.min(this.minPaintX, this.x);
			this.minPaintY = Math.min(this.minPaintY, this.y);
			
			int px0 = this.x;
			int py0 = this.y;
			this.lineTo(dx1, dy1, false);
			
			int px1 = this.x;
			int py1 = this.y;
			this.lineTo(dx2, dy2, false);
			this.minX = Math.min(this.minX, this.x);
			this.maxX = Math.max(this.maxX, this.x);
			this.minY = Math.min(this.minY, this.y);
			this.maxY = Math.max(this.maxY, this.y);
			
			this.minPaintX = Math.min(this.minPaintX, this.x);
			this.minPaintY = Math.min(this.minPaintY, this.y);
			
			int px2 = this.x;
			int py2 = this.y;
			int px3 = (px0 + px2);
			int py3 = (py0 + py2);
			float n = (((px0 - px1) * (px3 - (2 * px1))) + ((py0 - py1) * (py3 - (2 * py1))));
			float d = (((px3 * px3) + (py3 * py3)) - ((4 * px1 * (px3 - px1)) + (4 * py1 * (py3 - py1))));
			int vx = ((int) ((px0 * (1 - (n / d)) * (1 - (n / d))) + (px1 * (n / d) * (1 - (n / d))) + (px2 * (n / d) * (n / d))));
			int vy = ((int) ((py0 * (1 - (n / d)) * (1 - (n / d))) + (py1 * (n / d) * (1 - (n / d))) + (py2 * (n / d) * (n / d))));
			if (this.cid == 48) {
				System.out.println("Bezier is (" + px0 + "/" + py0 + ") to (" + px2 + "/" + py2 + ") via (" + px1 + "/" + py1 + ")");
				System.out.println("  P3 is (" + px3 + "/" + py3 + ")");
				System.out.println("  n is " + n + ", d is " + d);
				System.out.println("  t is " + (n / d));
				System.out.println("  vortex is (" + vx + "/" + vy + ")");
			}
			
			//	vortex inside painted part of parabola
			if ((n >= 0) && (n <= d)) {
				this.minX = Math.min(this.minX, vx);
				this.maxX = Math.max(this.maxX, vx);
				this.minY = Math.min(this.minY, vy);
				this.maxY = Math.max(this.maxY, vy);
				this.minPaintX = Math.min(this.minPaintX, vx);
				this.minPaintY = Math.min(this.minPaintY, vy);
			}
			
			//	TODO measure turning direction, and record angle of continuous turns, even if consisting of multiple curves
			
			//	TODO also measure starting and ending angle
			
			/*
t=(P0-P1)*(P3-2P1) / P3*P3 - 4P1*(P3-P1)
P(t)=P0(1-t)2+P1t(1-t)+P2t2
			 */
			//	TODO measure where curve is going, length, angle, etc.
			//	TODO and, for heaven's sake, measure only how high up spline really goes, not middle point !!!
			//	TODO ==> copy that stuff from PDF Font.
		}
		void closePath() {}
		void closePath(int dx, int dy) {
			this.lineTo(dx, dy);
		}
	}
	
	private static class OpGraphicsTrueType extends OpReceiverTrueType {
		int minX;
		int minY;
		int height;
		int sx = 0;
		int sy = 0;
		int lCount = 0;
		BufferedImage img;
		Graphics2D gr;
		private float scale = 1.0f;
		OpGraphicsTrueType(int minX, int minY, int height, float scale, BufferedImage img) {
			this.minX = minX;
			this.minY = minY;
			this.height = height;
			this.scale = scale;
			this.setImage(img);
			this.gr.setColor(Color.WHITE);
			this.gr.fillRect(0, 0, img.getWidth(), img.getHeight());
			this.gr.setColor(Color.BLACK);
		}
		void setImage(BufferedImage img) {
			this.img = img;
			this.gr = this.img.createGraphics();
		}
		void lineTo(int dx, int dy) {
			if (this.lCount == 0) {
				this.sx = this.x;
				this.sy = this.y;
			}
			dx = ((int) (dx * this.scaleX));
			dy = ((int) (dy * this.scaleY));
			this.gr.drawLine(
					Math.round((this.scale * (this.x - this.minX)) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.height - (this.y - this.minY))) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.x - this.minX + dx)) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.height - (this.y - this.minY + dy))) + glyphOutlineFillSafetyEdge)
				);
			this.x += dx;
			this.y += dy;
			this.lCount++;
		}
		void curveTo(int dx1, int dy1, int dx2, int dy2) {
			if (this.lCount == 0) {
				this.sx = this.x;
				this.sy = this.y;
			}
			dx1 = ((int) (dx1 * this.scaleX));
			dy1 = ((int) (dy1 * this.scaleY));
			dx2 = ((int) (dx2 * this.scaleX));
			dy2 = ((int) (dy2 * this.scaleY));
			QuadCurve2D.Float qc = new QuadCurve2D.Float();
			qc.setCurve(
					Math.round((this.scale * (this.x - this.minX)) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.height - (this.y - this.minY))) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.x - this.minX + dx1)) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.height - (this.y - this.minY + dy1))) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.x - this.minX + dx1 + dx2)) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.height - (this.y - this.minY + dy1 + dy2))) + glyphOutlineFillSafetyEdge)
				);
			this.gr.draw(qc);
			this.x += (dx1 + dx2);
			this.y += (dy1 + dy2);
			this.lCount++;
		}
		void moveTo(int dx, int dy) {
			this.lCount = 0;
			dx = ((int) (dx * this.scaleX));
			dy = ((int) (dy * this.scaleY));
			this.x += dx;
			this.y += dy;
		}
		void moveToAbs(int x, int y) {
			this.lCount = 0;
			this.x = x;
			this.y = y;
		}
		void closePath() {
			this.gr.drawLine(
					Math.round((this.scale * (this.x - this.minX)) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.height - (this.y - this.minY))) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.sx - this.minX)) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.height - (this.sy - this.minY))) + glyphOutlineFillSafetyEdge)
				);
		}
		void closePath(int dx, int dy) {
			dx = ((int) (dx * this.scaleX));
			dy = ((int) (dy * this.scaleY));
			QuadCurve2D.Float qc = new QuadCurve2D.Float();
			qc.setCurve(
					Math.round((this.scale * (this.x - this.minX)) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.height - (this.y - this.minY))) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.x - this.minX + dx)) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.height - (this.y - this.minY + dy))) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.sx - this.minX)) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.height - (this.sy - this.minY))) + glyphOutlineFillSafetyEdge)
				);
			this.gr.draw(qc);
			this.x += dx;
			this.y += dy;
		}
	}
	
	private static final int glyphOutlineFillSafetyEdge = 3;
	
	private static void fillGlyphOutline(BufferedImage img, float scale, boolean show) {
		ImageDisplayDialog idd = null;
		
		//	fill outline
		int blackRgb = Color.BLACK.getRGB();
		int whiteRgb = Color.WHITE.getRGB();
		int outsideRgb = Color.GRAY.getRGB();
		int insideRgb = Color.GRAY.darker().getRGB();
		HashSet insideRgbs = new HashSet();
		HashSet outsideRgbs = new HashSet();
		
		//	fill outside
		fill(img, 1, 1, whiteRgb, outsideRgb);
		insideRgbs.add(new Integer(insideRgb));
		insideRgbs.add(new Integer(blackRgb));
		outsideRgbs.add(new Integer(outsideRgb));
		
		//	fill characters outside-in
		outsideRgb = (new Color(outsideRgb)).brighter().getRGB();
		int seekWidth = Math.max(1, (Math.round(scale * 10)));
		boolean gotWhite = true;
		boolean outmostWhiteIsInside = true;
		while (gotWhite) {
			
			if (show) {
				BufferedImage dImg = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
				Graphics g = dImg.getGraphics();
				g.drawImage(img, 0, 0, dImg.getWidth(), dImg.getHeight(), null);
				g.setColor(Color.RED);
				if (idd == null) {
					idd = new ImageDisplayDialog("Rendering Progress");
					idd.setSize((dImg.getWidth() + 200), (dImg.getHeight() + 100));
				}
				idd.addImage(dImg, ("Filling"));
				idd.setLocationRelativeTo(null);
				idd.setVisible(true);
			}
			
			gotWhite = false;
			int fillRgb;
			if (outmostWhiteIsInside) {
				fillRgb = insideRgb;
				insideRgbs.add(new Integer(fillRgb));
			}
			else {
				fillRgb = outsideRgb;
				outsideRgbs.add(new Integer(fillRgb));
			}
//			System.out.println("Fill RGB is " + fillRgb);
			for (int c = seekWidth; c < img.getWidth(); c += seekWidth) {
//				System.out.println("Investigating column " + c);
				int r = 0;
				while ((r < img.getHeight()) && (img.getRGB(c, r) != whiteRgb) && (img.getRGB(c, r) != fillRgb)) {
//					if ((r % 10) == 0) System.out.println(" - ignoring pixel with RGB " + img.getRGB(c, r));
					r++;
				}
//				System.out.println(" - found interesting pixel at row " + r);
				if (r >= img.getHeight()) {
//					System.out.println(" --> bottom of column");
					continue;
				}
//				System.out.println(" - RGB is " + img.getRGB(c, r));
				if (img.getRGB(c, r) == fillRgb) {
//					System.out.println(" --> filled before");
					continue;
				}
				
//				System.out.println(" --> filling at " + c + "/" + r + " with " + fillRgb);
				fill(img, c, r, whiteRgb, fillRgb);
				gotWhite = true;
			}
			
			for (int c = (img.getWidth() - seekWidth); c > 0; c -= seekWidth) {
//				System.out.println("Investigating column " + c);
				int r = (img.getHeight() - 1);
				while ((r >= 0) && (img.getRGB(c, r) != whiteRgb) && (img.getRGB(c, r) != fillRgb)) {
//					if ((r % 10) == 0) System.out.println(" - ignoring pixel with RGB " + img.getRGB(c, r));
					r--;
				}
//				System.out.println(" - found interesting pixel at row " + r);
				if (r < 0) {
//					System.out.println(" --> bottom of column");
					continue;
				}
//				System.out.println(" - RGB is " + img.getRGB(c, r));
				if (img.getRGB(c, r) == fillRgb) {
//					System.out.println(" --> filled before");
					continue;
				}
				
//				System.out.println(" --> filling at " + c + "/" + r + " with " + fillRgb);
				fill(img, c, r, whiteRgb, fillRgb);
				gotWhite = true;
			}
			
			for (int r = seekWidth; r < img.getHeight(); r += seekWidth) {
//				System.out.println("Investigating row " + r);
				int c = 0;
				while ((c < img.getWidth()) && (img.getRGB(c, r) != whiteRgb) && (img.getRGB(c, r) != fillRgb)) {
//					if ((c % 10) == 0) System.out.println(" - ignoring pixel with RGB " + img.getRGB(c, r));
					c++;
				}
//				System.out.println(" - found interesting pixel at column " + r);
				if (c >= img.getWidth()) {
//					System.out.println(" --> right end of row");
					continue;
				}
//				System.out.println(" - RGB is " + img.getRGB(c, r));
				if (img.getRGB(c, r) == fillRgb) {
//					System.out.println(" --> filled before");
					continue;
				}
				
//				System.out.println(" --> filling at " + c + "/" + r + " with " + fillRgb);
				fill(img, c, r, whiteRgb, fillRgb);
				gotWhite = true;
			}
			
			for (int r = (img.getHeight() - seekWidth); r >= 0; r -= seekWidth) {
//				System.out.println("Investigating row " + r);
				int c = (img.getWidth() - 1);
				while ((c >= 0) && (img.getRGB(c, r) != whiteRgb) && (img.getRGB(c, r) != fillRgb)) {
//					if ((c % 10) == 0) System.out.println(" - ignoring pixel with RGB " + img.getRGB(c, r));
					c--;
				}
//				System.out.println(" - found interesting pixel at column " + r);
				if (c < 0) {
//					System.out.println(" --> right end of row");
					continue;
				}
//				System.out.println(" - RGB is " + img.getRGB(c, r));
				if (img.getRGB(c, r) == fillRgb) {
//					System.out.println(" --> filled before");
					continue;
				}
				
//				System.out.println(" --> filling at " + c + "/" + r + " with " + fillRgb);
				fill(img, c, r, whiteRgb, fillRgb);
				gotWhite = true;
			}
			
			if (outmostWhiteIsInside) {
				outmostWhiteIsInside = false;
				insideRgb = (new Color(insideRgb)).darker().getRGB();
//				System.out.println("Inside RGB set to " + insideRgb);
			}
			else {
				outmostWhiteIsInside = true;
				outsideRgb = (new Color(outsideRgb)).brighter().getRGB();
//				System.out.println("Outside RGB set to " + outsideRgb);
			}
		}
		
		//	make it black and white, finally
		for (int c = 0; c < img.getWidth(); c++) {
			for (int r = 0; r < img.getHeight(); r++) {
				int rgb = img.getRGB(c, r);
				if (insideRgbs.contains(new Integer(rgb)))
					img.setRGB(c, r, blackRgb);
				else img.setRGB(c, r, whiteRgb);
			}
		}
	}
	
	private static void fill(BufferedImage img, int x, int y, int toFillRgb, int fillRgb) {
		if ((x < 0) || (x >= img.getWidth()))
			return;
		if ((y < 0) || (y >= img.getHeight()))
			return;
		int rgb = img.getRGB(x, y);
		if (rgb != toFillRgb)
			return;
		img.setRGB(x, y, fillRgb);
//		System.out.println("Filling from " + x + "/" + y + ", boundary is " + boundaryRgb + ", fill is " + fillRgb + ", set is " + img.getRGB(x, y));
		int xlm = x;
		for (int xl = (x-1); xl >= 0; xl--) {
			rgb = img.getRGB(xl, y);
			if (rgb != toFillRgb)
				break;
			img.setRGB(xl, y, fillRgb);
			xlm = xl;
		}
		int xrm = x;
		for (int xr = (x+1); xr < img.getWidth(); xr++) {
			rgb = img.getRGB(xr, y);
			if (rgb != toFillRgb)
				break;
			img.setRGB(xr, y, fillRgb);
			xrm = xr;
		}
		for (int xe = xlm; xe <= xrm; xe++) {
			fill(img, xe, (y - 1), toFillRgb, fillRgb);
			fill(img, xe, (y + 1), toFillRgb, fillRgb);
		}
	}
	
	private static BufferedImage scaleImage(BufferedImage img, int maxHeight) {
		if (img.getHeight() > maxHeight) {
			BufferedImage sImg = new BufferedImage(((maxHeight * img.getWidth()) / img.getHeight()), maxHeight, img.getType());
			sImg.getGraphics().drawImage(img, 0, 0, sImg.getWidth(), sImg.getHeight(), null);
			return sImg;
		}
		else return img;
	}
	
	public static void main(String[] args) throws Exception {
		File fontPath = new File("E:/Testdaten/PdfExtract/FreeFontTrueType/");
		File fontFile = new File(fontPath, "FreeSerif.ttf");
		InputStream fontIn = new BufferedInputStream(new FileInputStream(fontFile));
		ByteArrayOutputStream fontData = new ByteArrayOutputStream();
		byte[] fontDataBuffer = new byte[1024];
		for (int r; (r = fontIn.read(fontDataBuffer, 0, fontDataBuffer.length)) != -1;)
			fontData.write(fontDataBuffer, 0, r);
		fontIn.close();
		readFontTrueType(fontData.toByteArray(), ProgressMonitor.dummy);
	}
}
