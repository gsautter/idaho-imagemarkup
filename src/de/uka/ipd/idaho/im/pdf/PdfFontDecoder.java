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

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.QuadCurve2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.SortedSet;

import javax.swing.JOptionPane;

import de.uka.ipd.idaho.gamta.util.ProgressMonitor;
import de.uka.ipd.idaho.im.pdf.PdfCharDecoder.CharImage;
import de.uka.ipd.idaho.im.pdf.PdfCharDecoder.CharImageMatch;
import de.uka.ipd.idaho.im.pdf.PdfCharDecoder.CharMatchResult;
import de.uka.ipd.idaho.im.pdf.PdfCharDecoder.CharMetrics;
import de.uka.ipd.idaho.im.pdf.PdfCharDecoder.ScoredCharSignature;
import de.uka.ipd.idaho.im.utilities.ImageDisplayDialog;
import de.uka.ipd.idaho.stringUtils.StringUtils;

/**
 * Utility class for decoding embedded fonts.
 * 
 * @author sautter
 */
public class PdfFontDecoder {
	
	static void readFontType1(byte[] data, Hashtable dataParams, Hashtable fd, PdfFont pFont, ProgressMonitor pm) {
		//	TODO read meta data up to 'eexec' plus subsequent single space char (Length1 parameter in stream dictionary)
		
		//	TODO get encrypted portion of data (Length2 parameter in stream dictionary)
		
		//	TODO decrypt data
		
		//	TODO deletgate to code already handling Type1C
	}
	
	private static final boolean DEBUG_TYPE1C_LOADING = true;
	
	/** Read a font in Type1C format, in SID mode or CID mode.
	 * @param data the raw bytes
	 * @param fd the font descriptor hash table
	 * @param pFont the font
	 * @param resolvedCodesOrCidMap mapping of resolved char codes in SID mode, mapping of CIDs in CID mode
	 * @param unresolvedCodes mapping of unresolved char codes (SID mode only, null in CID mode)
	 * @param pm a progress monitor to observe font decoding
	 */
	static void readFontType1C(byte[] data, Hashtable fd, PdfFont pFont, HashMap resolvedCodesOrCidMap, HashMap unresolvedCodes, ProgressMonitor pm) {
		pm.setInfo("   - reading meta data");
		int i = 0;
		
		//	read header
//		if (DEBUG_TYPE1C_LOADING) System.out.println(new String(data));
		int major = convertUnsigned(data[i++]);
		int minor = convertUnsigned(data[i++]);
		if (DEBUG_TYPE1C_LOADING) System.out.println("Version is " + major + "." + minor);
		int hdrSize = convertUnsigned(data[i++]);
		if (DEBUG_TYPE1C_LOADING) System.out.println("Header size is " + hdrSize);
		int offSize = convertUnsigned(data[i++]);
		if (DEBUG_TYPE1C_LOADING) System.out.println("Offset size is " + offSize);
		
		//	read base data
		i = readFontType1cIndex(data, i, "name", null, null, false, null);
		if (DEBUG_TYPE1C_LOADING) System.out.println("GOT TO " + i + " of " + data.length + " bytes");
		HashMap topDict = new HashMap() {
			public Object put(Object key, Object value) {
				if (DEBUG_TYPE1C_LOADING) {
					System.out.print("TopDict: " + key + " set to " + value);
					if (value instanceof Number[]) {
						Number[] nums = ((Number[]) value);
						for (int n = 0; n < nums.length; n++)
							System.out.print(" " + nums[n]);
					}
					System.out.println();
				}
				return super.put(key, value);
			}
		};
		i = readFontType1cIndex(data, i, "TopDICT", topDict, type1cTopDictOpResolver, true, null);
		if (DEBUG_TYPE1C_LOADING) System.out.println("GOT TO " + i + " of " + data.length + " bytes");
		
		ArrayList sidIndex = new ArrayList() {
			public boolean add(Object o) {
				System.out.println("StringIndex: " + this.size() + " (SID " + (this.size() + sidResolver.size()) + ") set to " + o);
				return super.add(o);
			}
		};
		i = readFontType1cIndex(data, i, "String", null, null, false, sidIndex);
		if (DEBUG_TYPE1C_LOADING) System.out.println("GOT TO " + i + " of " + data.length + " bytes");
		
		//	TODO from here onward, Type 1 and Type 1C should be the same
		
		//	read encoding
		HashMap eDict = new HashMap();
		int eEnd = 0;
		if (topDict.containsKey("Encoding")) {
			Number[] eos = ((Number[]) topDict.get("Encoding"));
			if (eos.length != 0) {
				int eso = eos[0].intValue();
				eEnd = readFontType1cEncoding(data, eso, eDict);
			}
		}
		
		//	read char rendering data
		HashMap csDict = new HashMap();
		ArrayList csIndexContent = new ArrayList();
		int csEnd = 0;
		if (topDict.containsKey("CharStrings")) {
			Number[] csos = ((Number[]) topDict.get("CharStrings"));
			if (csos.length != 0) {
				int cso = csos[0].intValue();
				csEnd = readFontType1cIndex(data, cso, "CharStrings", csDict, glyphProgOpResolver, false, csIndexContent);
			}
		}
		ArrayList csContent = new ArrayList();
		int cEnd = 0;
		if (topDict.containsKey("Charset") && csDict.containsKey("Count")) {
			Number[] csos = ((Number[]) topDict.get("Charset"));
			Number[] cnts = ((Number[]) csDict.get("Count"));
			if ((csos.length * cnts.length) != 0) {
				int cso = csos[0].intValue();
				int cnt = cnts[0].intValue();
				cEnd = readFontType1cCharset(data, cso, cnt, csContent);
			}
		}
		HashMap pDict = new HashMap();
		int pEnd = 0;
		if (topDict.containsKey("Private")) {
			Number[] pos = ((Number[]) topDict.get("Private"));
			if (pos.length != 0) try {
				int po = pos[0].intValue();
				ArrayList pDictContent = new ArrayList();
				pEnd = readFontType1cDict(data, po, "Private", pDict, false, privateOpResolver, pDictContent);
			}
			catch (RuntimeException re) {
				System.out.println("Error reading private dictionary: " + re.getMessage());
			}
		}
		i = Math.max(Math.max(i, pEnd), Math.max(csEnd, cEnd));
		if (DEBUG_TYPE1C_LOADING) {
			System.out.println("GOT TO " + i + " of " + data.length + " bytes");
			System.out.println("Got " + csContent.size() + " char IDs, " + csIndexContent.size() + " char progs");
		}
		if (csContent.isEmpty() || (((Integer) csContent.get(0)).intValue() != 0))
			csContent.add(0, new Integer(0));
		
		//	get default width
		int dWidth = -1;
		if (pDict.containsKey("defaultWidthX")) {
			Number[] dws = ((Number[]) pDict.get("defaultWidthX"));
			if (dws.length != 0)
				dWidth = dws[0].intValue();
		}
		int nWidth = -1;
		if (pDict.containsKey("nominalWidthX")) {
			Number[] nws = ((Number[]) pDict.get("nominalWidthX"));
			if (nws.length != 0)
				nWidth = nws[0].intValue();
		}
		if (nWidth == -1)
			nWidth = dWidth;
		else if (dWidth == -1)
			dWidth = nWidth;
		if (DEBUG_TYPE1C_LOADING) System.out.println("Default char width is " + dWidth + ", nominal width is " + nWidth);
		
		//	measure characters
		pm.setInfo("   - measuring characters");
		int maxDescent = 0;
		int maxCapHeight = 0;
		OpTrackerType1[] otrs = new OpTrackerType1[Math.min(csIndexContent.size(), csContent.size())];
		for (int c = 0; c < Math.min(csIndexContent.size(), csContent.size()); c++) {
			OpType1[] cs = ((OpType1[]) csIndexContent.get(c));
			otrs[c] = new OpTrackerType1();
			
			//	CID font mode
			if (unresolvedCodes == null) {
				Integer chi = ((Integer) resolvedCodesOrCidMap.get(new Integer(c)));
				if (chi == null)
					continue;
				char ch = ((char) chi.intValue());
				String chn = StringUtils.getCharName(ch);
				pm.setInfo("     - measuring char " + c + " (" + chn + "/'" + ch + "'/'" + StringUtils.getNormalForm(ch) + "'/" + ((int) ch) + ")");
				if (DEBUG_TYPE1C_LOADING) System.out.println("Measuring char " + c + " (" + chn + "/'" + ch + "'/'" + StringUtils.getNormalForm(ch) + "'/" + ((int) ch) + ")");
			}
			
			//	Type 1 font mode
			else {
				Integer sid = ((Integer) csContent.get(c));
				if ((0 < DEBUG_TYPE1C_TARGET_SID) && (sid.intValue() != DEBUG_TYPE1C_TARGET_SID))
					continue;
				String chn = ((String) sidResolver.get(sid));
				if ((chn == null) && (sid.intValue() >= sidResolver.size()) && ((sid.intValue() - sidResolver.size()) < sidIndex.size()))
					chn = ((String) sidIndex.get(sid.intValue() - sidResolver.size()));
				if ((chn == null) && pFont.diffNameMappings.containsKey(new Integer(c)))
					chn = ((String) pFont.diffNameMappings.get(new Integer(c)));
				char ch = StringUtils.getCharForName(chn);
				pm.setInfo("     - measuring char " + c + " with SID " + sid + " (" + chn + "/'" + ch + "'/'" + StringUtils.getNormalForm(ch) + "'/" + ((int) ch) + ")");
				if (DEBUG_TYPE1C_LOADING) System.out.println("Measuring char " + c + ", SID is " + sid + " (" + chn + "/'" + ch + "'/'" + StringUtils.getNormalForm(ch) + "'/" + ((int) ch) + ")");
			}
			runFontType1Ops(cs, otrs[c], false, false, null, -1, -1); // we don't know if multi-path or not so far ... 
//			if (DEBUG_TYPE1C_LOADING) System.out.println(" - " + otrs[c].id + ": " + otrs[c].minX + " < X < " + otrs[c].maxX);
			maxDescent = Math.min(maxDescent, otrs[c].minY);
			maxCapHeight = Math.max(maxCapHeight, otrs[c].maxY);
		}
		if (DEBUG_TYPE1C_LOADING) System.out.println("Max descent is " + maxDescent + ", max cap height is " + maxCapHeight);
		
		//	set up rendering
		int maxRenderSize = 300;
		float scale = 1.0f;
		if ((maxCapHeight - maxDescent) > maxRenderSize)
			scale = (((float) maxRenderSize) / (maxCapHeight - maxDescent));
		if (DEBUG_TYPE1C_LOADING) System.out.println(" ==> scaledown factor is " + scale);
		
 		//	set up style-aware name based checks
		pm.setInfo("   - decoding characters");
		Font serifFont = getSerifFont();
		Font[] serifFonts = new Font[4];
		Font sansFont = getSansSerifFont();
		Font[] sansFonts = new Font[4];
		for (int s = Font.PLAIN; s <= (Font.BOLD | Font.ITALIC); s++) {
			serifFonts[s] = serifFont.deriveFont(s);
			sansFonts[s] = sansFont.deriveFont(s);
		}
		
		CharImageMatch[][] serifStyleCims = new CharImageMatch[Math.min(csContent.size(), csIndexContent.size())][4];
		int[] serifCharCounts = {0, 0, 0, 0};
		int[] serifCharCountsNs = {0, 0, 0, 0};
		double[] serifStyleSimMins = {1, 1, 1, 1};
		double[] serifStyleSimMinsNs = {1, 1, 1, 1};
		double[] serifStyleSimSums = {0, 0, 0, 0};
		double[] serifStyleSimSumsNs = {0, 0, 0, 0};
		
		CharImageMatch[][] sansStyleCims = new CharImageMatch[Math.min(csContent.size(), csIndexContent.size())][4];
		int[] sansCharCounts = {0, 0, 0, 0};
		int[] sansCharCountsNs = {0, 0, 0, 0};
		double[] sansStyleSimMins = {1, 1, 1, 1};
		double[] sansStyleSimMinsNs = {1, 1, 1, 1};
		double[] sansStyleSimSums = {0, 0, 0, 0};
		double[] sansStyleSimSumsNs = {0, 0, 0, 0};
		
		double simMin = 1;
		double simMinNs = 1;
		double simSum = 0;
		double simSumNs = 0;
		int charCount = 0;
		int charCountNs = 0;
		
		CharImage[] charImages = new CharImage[Math.min(csContent.size(), csIndexContent.size())];
		char[] chars = new char[Math.min(csContent.size(), csIndexContent.size())];
		Arrays.fill(chars, ((char) 0));
		HashSet smallCapsChars = new HashSet();
		
		HashMap matchCharChache = new HashMap();
		
		//	generate images and match against named char
		ImageDisplayDialog fidd = (DEBUG_TYPE1C_RENDRING ? new ImageDisplayDialog("Font " + pFont.name) : null);
		for (int c = 0; c < Math.min(csContent.size(), csIndexContent.size()); c++) {
			Integer sid;
			String chn;
			
			//	CID font mode
			if (unresolvedCodes == null) {
				sid = new Integer(-1);
				Integer chi = ((Integer) resolvedCodesOrCidMap.get(new Integer(c)));
				if (chi == null)
					continue;
				chars[c] = ((char) chi.intValue());
				chn = StringUtils.getCharName(chars[c]);
//				if (!DEBUG_TYPE1C_RENDRING && !pFont.usedChars.contains(new Integer(c)) && !pFont.usedCharNames.contains(chn)) {
				if (!DEBUG_TYPE1C_RENDRING && !pFont.usesCharCode(new Integer(c)) && !pFont.usedCharNames.contains(chn)) {
					pm.setInfo("     - ignoring unused char " + c + " (" + chn + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ")");
					continue;
				}
				pm.setInfo("     - decoding char " + c + " (" + chn + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ")");
				if (DEBUG_TYPE1C_LOADING) System.out.println("Decoding char " + c + " (" + chn + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ")");
			}
			
			//	Type 1 font mode
			else {
				sid = ((Integer) csContent.get(c));
				if (sid.intValue() == 0)
					continue;
				if ((0 < DEBUG_TYPE1C_TARGET_SID) && (sid.intValue() != DEBUG_TYPE1C_TARGET_SID))
					continue;
				chn = ((String) sidResolver.get(sid));
				if ((chn == null) && (sid.intValue() >= sidResolver.size()) && ((sid.intValue() - sidResolver.size()) < sidIndex.size()))
					chn = ((String) sidIndex.get(sid.intValue() - sidResolver.size()));
				if ((chn == null) && pFont.diffNameMappings.containsKey(new Integer(c)))
					chn = ((String) pFont.diffNameMappings.get(new Integer(c)));
				chars[c] = StringUtils.getCharForName(chn);
				if (chars[c] == 0) {
					if (chn.indexOf('.') != -1)
						chars[c] = StringUtils.getCharForName(chn.substring(0, chn.indexOf('.')));
					else if (chn.indexOf('_') != -1)
						chars[c] = StringUtils.getCharForName(chn.replaceAll("_", ""));
				}
//				if (!DEBUG_TYPE1C_RENDRING && !pFont.usedChars.contains(new Integer(c)) && !pFont.usedCharNames.contains(chn)) {
				if (!DEBUG_TYPE1C_RENDRING && !pFont.usesCharCode(new Integer(c)) && !pFont.usedCharNames.contains(chn)) {
					pm.setInfo("     - ignoring unused char " + c + " with SID " + sid + " (" + chn + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ")");
					continue;
				}
				pm.setInfo("     - decoding char " + c + " with SID " + sid + " (" + chn + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ")");
				if (DEBUG_TYPE1C_LOADING) System.out.println("Decoding char " + c + ", SID is " + sid + " (" + chn + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ")");
			}
			int chw = ((otrs[c].rWidth == 0) ? dWidth : (nWidth + otrs[c].rWidth));
			if (DEBUG_TYPE1C_LOADING) System.out.println(" - char width is " + chw);
			if (DEBUG_TYPE1C_LOADING) System.out.println(" - stroke count is " + otrs[c].mCount);
			
			boolean ignoreForMin = ((minIgnoreChars.indexOf(chars[c]) != -1) || (chars[c] != StringUtils.getBaseChar(chars[c])));
			
			//	check if char rendering possible
			if ((otrs[c].maxX <= otrs[c].minX) || (otrs[c].maxY <= otrs[c].minY))
				continue;
			
			//	render char
			OpType1[] cs = ((OpType1[]) csIndexContent.get(c));
			int mx = 8;
			int my = ((mx * (maxCapHeight - maxDescent)) / (otrs[c].maxX - otrs[c].minX));
			OpGraphicsType1 ogr = new OpGraphicsType1(
					otrs[c].minX,
					maxDescent,
					(maxCapHeight - maxDescent + (my / 2)),
					scale,
					new BufferedImage(
							Math.round((scale * (otrs[c].maxX - otrs[c].minX + mx)) + (2 * glyphOutlineFillSafetyEdge)),
							Math.round((scale * (maxCapHeight - maxDescent + my)) + (2 * glyphOutlineFillSafetyEdge)),
							BufferedImage.TYPE_INT_RGB)
					);
			runFontType1Ops(cs, ogr, otrs[c].isMultiPath, (0 < DEBUG_TYPE1C_TARGET_SID), fidd, c, sid.intValue());
			if (DEBUG_TYPE1C_LOADING) System.out.println(" - image rendered, size is " + ogr.img.getWidth() + "x" + ogr.img.getHeight() + ", OpGraphics height is " + ogr.height);
			charImages[c] = new CharImage(ogr.img, Math.round(((float) (maxCapHeight * ogr.img.getHeight())) / (maxCapHeight - maxDescent)));
			
			//	CID font mode
			if (unresolvedCodes == null)
				pFont.setCharImage(((int) chars[c]), chn, ogr.img);
			
			//	Type 1 font mode
			else {
				Integer chc = ((Integer) resolvedCodesOrCidMap.get(chn));
				if (chc == null)
					chc = ((Integer) unresolvedCodes.get(chn));
				pFont.setCharImage(((chc == null) ? ((int) chars[c]) : chc.intValue()), chn, ogr.img);
			}
			
			//	measure best match
			float bestSim = -1;
			float bestSimNs = -1;
			char bestSimCh = ((char) 0);
			
			//	try named char match first (render known chars to fill whole image)
//			CharMatchResult matchResult = PdfCharDecoder.matchChar(charImages[c], chars[c], true, serifFonts, sansFonts, matchCharChache, true, ((chn != null) && (chn.indexOf('_') != -1)));
			CharMatchResult matchResult = PdfCharDecoder.matchChar(charImages[c], chars[c], true, serifFonts, sansFonts, matchCharChache, true, false);
			float oSimSum = 0;
			int oSimCount = 0;
			for (int s = Font.PLAIN; s <= (Font.BOLD | Font.ITALIC); s++) {
				if (matchResult.serifStyleCims[s] != null) {
					oSimSum += matchResult.serifStyleCims[s].sim;
					oSimCount++;
				}
				if (matchResult.sansStyleCims[s] != null) {
					oSimSum += matchResult.sansStyleCims[s].sim;
					oSimCount++;
				}
			}
			if (DEBUG_TYPE1C_LOADING)
				System.out.println(" - average similarity is " + ((oSimCount == 0) ? 0 : (oSimSum / oSimCount)));
			
			//	if ToUnicode mapping exists, verify it, and use whatever fits better (not in CID font mode)
			if ((unresolvedCodes != null) && pFont.ucMappings.containsKey(new Integer((int) chars[c]))) {
				String ucStr = ((String) pFont.ucMappings.get(new Integer((int) chars[c])));
				if (ucStr.length() > 1) {
					char lUcCh = StringUtils.getCharForName(ucStr);
					if (lUcCh > 0) {
						if (DEBUG_TYPE1C_LOADING) System.out.println(" - unified Unicode mapping '" + ucStr + "' to '" + lUcCh + "'");
						ucStr = ("" + lUcCh);
					}
				}
				if ((ucStr != null) && (ucStr.length() == 1) && (ucStr.charAt(0) != chars[c])) {
					char ucChar = ucStr.charAt(0);
					if (DEBUG_TYPE1C_LOADING) System.out.println(" - testing Unicode mapping '" + ucChar + "'");
					CharMatchResult ucMatchResult = PdfCharDecoder.matchChar(charImages[c], ucChar, (chars[c] != Character.toUpperCase(ucChar)), serifFonts, sansFonts, matchCharChache, true, false);
					
					if (DEBUG_TYPE1C_LOADING) {
						float ucSimSum = 0;
						int ucSimCount = 0;
						for (int s = Font.PLAIN; s <= (Font.BOLD | Font.ITALIC); s++) {
							if (ucMatchResult.serifStyleCims[s] != null) {
								ucSimSum += ucMatchResult.serifStyleCims[s].sim;
								ucSimCount++;
							}
							if (ucMatchResult.sansStyleCims[s] != null) {
								ucSimSum += ucMatchResult.sansStyleCims[s].sim;
								ucSimCount++;
							}
						}
						System.out.println(" - average similarity is " + ((ucSimCount == 0) ? 0 : (ucSimSum / ucSimCount)));
					}
					
					int originalBetter = 0;
					int ucMappingBetter = 0;
					for (int s = Font.PLAIN; s <= (Font.BOLD | Font.ITALIC); s++) {
						if ((matchResult.serifStyleCims[s] != null) && (ucMatchResult.serifStyleCims[s] != null)) {
							if (matchResult.serifStyleCims[s].sim < ucMatchResult.serifStyleCims[s].sim) {
								ucMappingBetter++;
								matchResult.serifStyleCims[s] = ucMatchResult.serifStyleCims[s];
							}
							else originalBetter++;
						}
						else if (ucMatchResult.serifStyleCims[s] != null) {
							ucMappingBetter++;
							matchResult.serifStyleCims[s] = ucMatchResult.serifStyleCims[s];
						}
						else if (matchResult.serifStyleCims[s] != null)
							originalBetter++;
						
						if ((matchResult.sansStyleCims[s] != null) && (ucMatchResult.sansStyleCims[s] != null)) {
							if (matchResult.sansStyleCims[s].sim < ucMatchResult.sansStyleCims[s].sim) {
								ucMappingBetter++;
								matchResult.sansStyleCims[s] = ucMatchResult.sansStyleCims[s];
							}
							else originalBetter++;
						}
						else if (ucMatchResult.sansStyleCims[s] != null) {
							ucMappingBetter++;
							matchResult.sansStyleCims[s] = ucMatchResult.sansStyleCims[s];
						}
						else if (matchResult.sansStyleCims[s] != null)
							originalBetter++;
					}
					
					if (originalBetter > ucMappingBetter) {
						pFont.ucMappings.remove(new Integer((int) chars[c]));
						if (DEBUG_TYPE1C_LOADING) System.out.println(" --> found original char to be better match (" + originalBetter + " vs. " + ucMappingBetter + "), removing mapping");
					}
					else if (DEBUG_TYPE1C_LOADING) System.out.println(" --> found mapped char to be better match (" + ucMappingBetter + " vs. " + originalBetter + ")");
				}
			}
			
			//	evaluate match result
			if (matchResult.rendered) {
				charCount++;
				if ((matchResult.serifStyleCims[Font.PLAIN] != null) || (matchResult.serifStyleCims[Font.BOLD] != null) || (matchResult.sansStyleCims[Font.PLAIN] != null) || (matchResult.sansStyleCims[Font.BOLD] != null) || (skewChars.indexOf(chars[c]) == -1))
					charCountNs++;
				for (int s = Font.PLAIN; s <= (Font.BOLD | Font.ITALIC); s++) {
					serifStyleCims[c][s] = matchResult.serifStyleCims[s];
					if (serifStyleCims[c][s] == null) {
						serifStyleSimMins[s] = 0;
						continue;
					}
					serifCharCounts[s]++;
					serifStyleSimSums[s] += serifStyleCims[c][s].sim;
					if (bestSim < serifStyleCims[c][s].sim) {
						bestSim = serifStyleCims[c][s].sim;
						bestSimCh = serifStyleCims[c][s].match.ch;
					}
					if (!ignoreForMin)
						serifStyleSimMins[s] = Math.min(serifStyleCims[c][s].sim, serifStyleSimMins[s]);
					if (((s & Font.ITALIC) == 0) || (skewChars.indexOf(chars[c]) == -1)) {
						serifCharCountsNs[s]++;
						serifStyleSimSumsNs[s] += serifStyleCims[c][s].sim;
						bestSimNs = Math.max(bestSimNs, serifStyleCims[c][s].sim);
						if (!ignoreForMin)
							serifStyleSimMinsNs[s] = Math.min(serifStyleCims[c][s].sim, serifStyleSimMinsNs[s]);
					}
				}
				for (int s = Font.PLAIN; s <= (Font.BOLD | Font.ITALIC); s++) {
					sansStyleCims[c][s] = matchResult.sansStyleCims[s];
					if (sansStyleCims[c][s] == null) {
						sansStyleSimMins[s] = 0;
						continue;
					}
					sansCharCounts[s]++;
					sansStyleSimSums[s] += sansStyleCims[c][s].sim;
					if (bestSim < sansStyleCims[c][s].sim) {
						bestSim = sansStyleCims[c][s].sim;
						bestSimCh = sansStyleCims[c][s].match.ch;
					}
					if (!ignoreForMin)
						sansStyleSimMins[s] = Math.min(sansStyleCims[c][s].sim, sansStyleSimMins[s]);
					if (((s & Font.ITALIC) == 0) || (skewChars.indexOf(chars[c]) == -1)) {
						sansCharCountsNs[s]++;
						sansStyleSimSumsNs[s] += sansStyleCims[c][s].sim;
						bestSimNs = Math.max(bestSimNs, sansStyleCims[c][s].sim);
						if (!ignoreForMin)
							sansStyleSimMinsNs[s] = Math.min(sansStyleCims[c][s].sim, sansStyleSimMinsNs[s]);
					}
				}
			}
			
			//	what do we have?
			if (DEBUG_TYPE1C_LOADING) System.out.println(" --> best similarity is " + bestSim + " / " + bestSimNs + " for " + bestSimCh);
			
			//	remember small-caps match
			if (bestSimCh != chars[c]) {
				smallCapsChars.add(new Integer(c));
				if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" --> small-caps match");
			}
			
			//	update overall measures
			if (!ignoreForMin) {
				if (bestSim >= 0)
					simMin = Math.min(simMin, bestSim);
				if (bestSimNs >= 0)
					simMinNs = Math.min(simMinNs, bestSimNs);
			}
			if (bestSim >= 0)
				simSum += bestSim;
			if (bestSimNs >= 0)
				simSumNs += bestSimNs;
		}
		checkImplicitSpaces(pFont, charImages, chars, csContent, sidIndex, resolvedCodesOrCidMap, unresolvedCodes);
		if (fidd != null) {
			fidd.setLocationRelativeTo(null);
			fidd.setSize(600, 400);
			fidd.setVisible(true);
		}
		
		//	use maximum of all fonts and styles when computing min and average similarity
		pm.setInfo("   - detecting font style");
		if (DEBUG_TYPE1C_LOADING) System.out.println(" - min similarity is " + simMin + " all / " + simMinNs + " non-skewed");
		double sim = ((charCount == 0) ? 0 : (simSum / charCount));
		double simNs = ((charCountNs == 0) ? 0 : (simSumNs / charCountNs));
		if (DEBUG_TYPE1C_LOADING) System.out.println(" - average similarity is " + sim + " (" + charCount + ") all / " + simNs + " (" + charCountNs + ") non-skewed");
		
		//	do we have a match? (be more strict with fewer chars, as fonts with few chars tend to be the oddjobs)
//		if ((simMin > 0.5) && (sim > ((charCount < 26) ? 0.7 : 0.6))) {
		if (((simMin > 0.5) && (sim > 0.6)) || ((simMin > 0.375) && (sim > 0.7)) || ((simMin > 0.25) && (sim > 0.8))) {
			if (DEBUG_TYPE1C_LOADING) System.out.println(" ==> match");
			
			//	TODO somehow assess the number or fraction of black pixels in the font being decoded and the comparison fonts, and penalize all too big differences in style detection
			
			//	try to select font style if pool sufficiently large
			int bestStyle = -1;
			boolean serif = true;
			if (Math.max(serifStyleSimSums.length, sansStyleSimSums.length) >= 2) {
				double bestStyleSim = 0;
				for (int s = Font.PLAIN; s <= (Font.BOLD | Font.ITALIC); s++) {
					if (DEBUG_TYPE1C_LOADING) {
						System.out.println(" - checking style " + s);
						System.out.println("   - min similarity is " + serifStyleSimMins[s] + "/" + sansStyleSimMins[s] + " all / " + serifStyleSimMinsNs[s] + "/" + sansStyleSimMinsNs[s] + " non-skewed");
					}
					double serifStyleSim = ((serifCharCounts[s] == 0) ? 0 : (serifStyleSimSums[s] / serifCharCounts[s]));
					double serifStyleSimNs = ((serifCharCountsNs[s] == 0) ? 0 : (serifStyleSimSumsNs[s] / serifCharCountsNs[s]));
					double sansStyleSim = ((sansCharCounts[s] == 0) ? 0 : (sansStyleSimSums[s] / sansCharCounts[s]));
					double sansStyleSimNs = ((sansCharCountsNs[s] == 0) ? 0 : (sansStyleSimSumsNs[s] / sansCharCountsNs[s]));
					if (DEBUG_TYPE1C_LOADING) System.out.println("   - average similarity is " + serifStyleSim + "/" + sansStyleSim + " all / " + serifStyleSimNs + "/" + sansStyleSimNs + " non-skewed");
					if ((((s & Font.ITALIC) == 0) ? Math.max(serifStyleSim, sansStyleSim) : Math.max(serifStyleSimNs, sansStyleSimNs)) > bestStyleSim) {
						if (DEBUG_TYPE1C_LOADING) System.out.println(" ==> new best match style");
						bestStyleSim = (((s & Font.ITALIC) == 0) ? Math.max(serifStyleSim, sansStyleSim) : Math.max(serifStyleSimNs, sansStyleSimNs));
						bestStyle = s;
						serif = (((s & Font.ITALIC) == 0) ? (serifStyleSim >= sansStyleSim) : (serifStyleSimNs >= sansStyleSimNs));
						if (DEBUG_TYPE1C_LOADING) System.out.println(" ==> serif is " + serif);
					}
				}
			}
			
			//	style not found, use plain
			if (bestStyle == -1)
				bestStyle = 0;
			
			//	set base font according to style
			pFont.bold = ((Font.BOLD & bestStyle) != 0);
			pFont.italics = ((Font.ITALIC & bestStyle) != 0);
			pFont.serif = serif;
			System.out.println(" ==> font decoded");
			if ((pFont.baseFont == null) || (pFont.bold != pFont.baseFont.bold) || (pFont.italics != pFont.baseFont.italic)) {
				String bfn = "Times-";
				if (pFont.bold && pFont.italics)
					bfn += "BoldItalic";
				else if (pFont.bold)
					bfn += "Bold";
				else if (pFont.italics)
					bfn += "Italic";
				else bfn += "Roman";
				pFont.setBaseFont(bfn, false);
			}
			
			//	store character widths
			if (dWidth != -1)
				pFont.mCharWidth = dWidth;
			
			//	check for descent
			pFont.hasDescent = ((maxDescent < -150) || (pFont.descent < -0.150));
			
			//	collect all chars, and measure xHeight and capHeight
			HashSet pFontChars = new HashSet();
			int xHeightSum = 0;
			int xHeightCount = 0;
			int capHeightSum = 0;
			int capHeightCount = 0;
			for (int c = 0; c < Math.min(csContent.size(), csIndexContent.size()); c++) {
				if (charImages[c] == null)
					continue;
				pFontChars.add(new Character(chars[c]));
				if ("aemnru".indexOf(StringUtils.getBaseChar(chars[c])) != -1) {
					xHeightSum += charImages[c].box.getHeight();
					xHeightCount++;
				}
				else if ("ABDEFGHIKLMNPRTUYbdfhkl0123456789".indexOf(StringUtils.getBaseChar(chars[c])) != -1) {
					capHeightSum += charImages[c].box.getHeight();
					capHeightCount++;
				}
			}
			int capHeight = ((capHeightCount == 0) ? 0 : (capHeightSum / capHeightCount));
			int xHeight = ((xHeightCount == 0) ? 0 : (xHeightSum / xHeightCount));
			if (DEBUG_TYPE1C_LOADING) System.out.println(" - average cap-height is " + capHeight + ", average x-height is " + xHeight);
			
			//	rectify small-caps TODO use char usage stats
			for (int c = 0; c < Math.min(csContent.size(), csIndexContent.size()); c++) {
				if (charImages[c] == null)
					continue;
				if (!smallCapsChars.contains(new Integer(c)))
					continue;
				if (pFontChars.contains(new Character(Character.toUpperCase(chars[c]))))
					continue;
				if ((xHeight < capHeight) && (Math.abs(charImages[c].box.getHeight() - xHeight) < Math.abs(charImages[c].box.getHeight() - capHeight)))
					continue;
				
				//	get basic data
				Integer sid;
				String chn;
				Integer chc;
				
				//	CID font mode
				if (unresolvedCodes == null) {
					sid = new Integer(-1);
					chn = StringUtils.getCharName(chars[c]);
					chc = new Integer((int) chars[c]);
				}
				
				//	Type 1 font mode
				else {
					sid = ((Integer) csContent.get(c));
					if (sid.intValue() == 0)
						continue;
					chn = ((String) sidResolver.get(sid));
					if ((chn == null) && (sid.intValue() >= sidResolver.size()) && ((sid.intValue() - sidResolver.size()) < sidIndex.size()))
						chn = ((String) sidIndex.get(sid.intValue() - sidResolver.size()));
					if ((chn == null) && pFont.diffNameMappings.containsKey(new Integer(c)))
						chn = ((String) pFont.diffNameMappings.get(new Integer(c)));
					chc = ((Integer) unresolvedCodes.get(chn));
					if (chc == null)
						chc = ((Integer) resolvedCodesOrCidMap.get(chn));
					if (chc == null) {
						chc = new Integer((int) StringUtils.getCharForName(chn));
						if (chc.intValue() < 1)
							continue;
					}
				}
				
				//	do actual correction
				pFont.mapUnicode(chc, ("" + Character.toUpperCase(chars[c])));
				pFont.setCharImage(chc, StringUtils.getCharName(Character.toUpperCase(chars[c])), charImages[c].img);
				if (DEBUG_TYPE1C_LOADING) System.out.println(" ==> small-caps corrected " + chc + " to " + Character.toUpperCase(chars[c]));
			}
			
			//	we're done here
			if ((unresolvedCodes == null) || unresolvedCodes.isEmpty())
				return;
		}
		
		//	reset chars with known names to mark them for re-evaluation (required with fonts that use arbitrary char names)
		else {
			if (DEBUG_TYPE1C_LOADING) System.out.println(" ==> mis-match");
			Arrays.fill(chars, ((char) 0));
		}
		
		//	TODO use char usage stats
		
		//	cache character images to speed up matters
		pm.setInfo("   - OCR decoding remaining characters");
		final float[] cacheHitRate = {0};
		HashMap cache = new HashMap() {
			int lookups = 0;
			int hits = 0;
			public Object get(Object key) {
				this.lookups ++;
				Object value = super.get(key);
				if (value != null)
					this.hits++;
				cacheHitRate[0] = (((float) this.hits) / this.lookups);
				return value;
			}
		};
		
		//	get matches for remaining characters, and measure xHeight and capHeight
		CharImageMatch[] bestCims = new CharImageMatch[Math.min(csContent.size(), csIndexContent.size())];
		int xHeightSum = 0;
		int xHeightCount = 0;
		int capHeightSum = 0;
		int capHeightCount = 0;
		for (int c = 0; c < Math.min(csContent.size(), csIndexContent.size()); c++) {
			if (charImages[c] == null)
				continue;
			
			//	get basic char data
			Integer sid;
			String chn;
			Integer chc;
			
			//	CID font mode
			if (unresolvedCodes == null) {
				sid = new Integer(-1);
				chn = StringUtils.getCharName(chars[c]);
				chc = new Integer((int) chars[c]);
				pm.setInfo("     - OCR decoding char " + c + " (" + chn + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ")");
				if (DEBUG_TYPE1C_LOADING) System.out.println("Decoding char " + c + " (" + chn + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ")");
			}
			
			//	Type 1 font mode
			else {
				sid = ((Integer) csContent.get(c));
				if (sid.intValue() == 0)
					continue;
				if ((0 < DEBUG_TYPE1C_TARGET_SID) && (sid.intValue() != DEBUG_TYPE1C_TARGET_SID))
					continue;
				chn = ((String) sidResolver.get(sid));
				if ((chn == null) && (sid.intValue() >= sidResolver.size()) && ((sid.intValue() - sidResolver.size()) < sidIndex.size()))
					chn = ((String) sidIndex.get(sid.intValue() - sidResolver.size()));
//				if (pFont.diffNameMappings.containsKey(new Integer(c))) {
//					if (DEBUG_TYPE1C_LOADING) System.out.println(" - char name is " + chn + ", diff correcting to " + ((String) pFont.diffNameMappings.get(new Integer(c))));
//					chn = ((String) pFont.diffNameMappings.get(new Integer(c)));
//				}
				if ((chn == null) && pFont.diffNameMappings.containsKey(new Integer(c)))
					chn = ((String) pFont.diffNameMappings.get(new Integer(c)));
				chc = ((Integer) unresolvedCodes.get(chn));
				if (chc == null)
					chc = ((Integer) resolvedCodesOrCidMap.get(chn));
				if (chc == null) {
					chc = new Integer((int) StringUtils.getCharForName(chn));
					if (chc.intValue() < 1)
						continue;
				}
				pm.setInfo("     - OCR decoding char " + c + " with SID " + sid + " (" + chn + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ")");
				if (DEBUG_TYPE1C_LOADING) System.out.println("Decoding char " + c + ", SID is " + sid + " (" + chn + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ")");
			}
			
			//	perform match
			bestCims[c] = getCharForImage(charImages[c], serifFonts, sansFonts, cache, false);
			if (bestCims[c] == null)
				continue;
			
			//	take measurements
			if ("aemnru".indexOf(StringUtils.getBaseChar(bestCims[c].match.ch)) != -1) {
				xHeightSum += charImages[c].box.getHeight();
				xHeightCount++;
			}
			else if ("ABDEFGHIKLMNPRTUYbdfhkl0123456789".indexOf(StringUtils.getBaseChar(bestCims[c].match.ch)) != -1) {
				capHeightSum += charImages[c].box.getHeight();
				capHeightCount++;
			}
		}
		int capHeight = ((capHeightCount == 0) ? 0 : (capHeightSum / capHeightCount));
		int xHeight = ((xHeightCount == 0) ? 0 : (xHeightSum / xHeightCount));
		if (DEBUG_TYPE1C_LOADING) System.out.println(" - average cap-height is " + capHeight + ", average x-height is " + xHeight);
		if ((xHeight == 0) && (capHeight != 0)) {
			xHeight = ((capHeight * 2) / 3);
			if (DEBUG_TYPE1C_LOADING) System.out.println(" - average x-height extrapolated to " + xHeight);
		}
		
		//	decode remaining characters
		for (int c = 0; c < Math.min(csContent.size(), csIndexContent.size()); c++) {
			
			//	don't try to match space or unused chars
			if (charImages[c] == null)
				continue;
			
			//	we don't have a match for this one
			if (bestCims[c] == null)
				continue;
			
			//	get basic char data
			String chn;
			Integer chc;
			
			//	CID font mode
			if (unresolvedCodes == null) {
				chn = StringUtils.getCharName(chars[c]);
				chc = new Integer((int) chars[c]);
				if (DEBUG_TYPE1C_LOADING) System.out.println("Decoding char " + c + " (" + chn + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ")");
			}
			
			//	Type 1 font mode
			else {
				Integer sid = ((Integer) csContent.get(c));
				if (sid.intValue() == 0)
					continue;
				if ((0 < DEBUG_TYPE1C_TARGET_SID) && (sid.intValue() != DEBUG_TYPE1C_TARGET_SID))
					continue;
				chn = ((String) sidResolver.get(sid));
				if ((chn == null) && (sid.intValue() >= sidResolver.size()) && ((sid.intValue() - sidResolver.size()) < sidIndex.size()))
					chn = ((String) sidIndex.get(sid.intValue() - sidResolver.size()));
//				if (pFont.diffNameMappings.containsKey(new Integer(c))) {
//					if (DEBUG_TYPE1C_LOADING) System.out.println(" - char name is " + chn + ", diff correcting to " + ((String) pFont.diffNameMappings.get(new Integer(c))));
//					chn = ((String) pFont.diffNameMappings.get(new Integer(c)));
//				}
				if ((chn == null) && pFont.diffNameMappings.containsKey(new Integer(c)))
					chn = ((String) pFont.diffNameMappings.get(new Integer(c)));
				chc = ((Integer) unresolvedCodes.get(chn));
				if (chc == null)
					chc = ((Integer) resolvedCodesOrCidMap.get(chn));
				if (chc == null) {
					chc = new Integer((int) StringUtils.getCharForName(chn));
					if (chc.intValue() < 1)
						continue;
				}
				if (DEBUG_TYPE1C_LOADING) System.out.println("Decoding char " + c + ", SID is " + sid + " (" + chn + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ")");
			}
			
			//	catch lower case COSVWXZ mistaken for upper case
			boolean isLowerCase = (xHeight < capHeight) && (Math.abs(charImages[c].box.getHeight() - xHeight) < Math.abs(charImages[c].box.getHeight() - capHeight));
			
			//	do we have a reliable match?
			if (bestCims[c].sim > 0.8) {
				if (DEBUG_TYPE1C_LOADING) System.out.println(" ==> char decoded (1) to '" + bestCims[c].match.ch + "' (" + StringUtils.getCharName(bestCims[c].match.ch) + ", '" + StringUtils.getNormalForm(bestCims[c].match.ch) + "') at " + bestCims[c].sim + ", cache hit rate at " + cacheHitRate[0]);
//				Character bsCh = new Character(bestCims[c].match.ch);
				Character bsCh;
				if (isLowerCase && ("COSVWXZ".indexOf(bestCims[c].match.ch) != -1))
					bsCh = new Character(Character.toLowerCase(bestCims[c].match.ch));
				else bsCh = new Character(bestCims[c].match.ch);
				
				//	correct char mapping specified in differences array
				pFont.mapDifference(chc, bsCh, null);
				pFont.mapUnicode(chc, StringUtils.getNormalForm(bsCh.charValue()));
				
				//	map char named by SID to what image actually displays
				if (DEBUG_TYPE1C_LOADING) System.out.println(" ==> mapped (1) " + chc + " to " + bsCh);
				
				//	no need to hassle about this char any more
				continue;
			}
			
			//	use whatever we got
			else {
				if (DEBUG_TYPE1C_LOADING) System.out.println(" ==> char decoded (2) to '" + bestCims[c].match.ch + "' (" + StringUtils.getCharName(bestCims[c].match.ch) + ", '" + StringUtils.getNormalForm(bestCims[c].match.ch) + "') at " + bestCims[c].sim + ", cache hit rate at " + cacheHitRate[0]);
//				if (DEBUG_TYPE1C_LOADING && (bestSim.sim < 0.7))
//					displayCharMatch(imgs[c], bestSim, "Best Match");
//				Character bsCh = new Character(bestCims[c].match.ch);
				Character bsCh;
				if (isLowerCase && ("COSVWXZ".indexOf(bestCims[c].match.ch) != -1))
					bsCh = new Character(Character.toLowerCase(bestCims[c].match.ch));
				else bsCh = new Character(bestCims[c].match.ch);
				
				//	correct char mapping specified in differences array
				pFont.mapDifference(chc, bsCh, null);
				pFont.mapUnicode(chc, StringUtils.getNormalForm(bsCh.charValue()));
				if (DEBUG_TYPE1C_LOADING) System.out.println(" ==> mapped (2) " + chc + " to " + bsCh);
				
				//	check for descent
				pFont.hasDescent = ((maxDescent < -150) || (pFont.descent < -0.150));
			}
		}
	}
	
	private static void checkImplicitSpaces(PdfFont pFont, CharImage[] charImages, char[] chars, ArrayList csContent, ArrayList sidIndex, HashMap resolvedCodesOrCidMap, HashMap unresolvedCodes) {
		
		//	check average word length (will be well below 2 for implicit space fonts)
		float avgFontWordLength = pFont.getAverageWordLength();
		if (DEBUG_TYPE1C_LOADING) System.out.println(" - average font word length is " + avgFontWordLength);
		if (avgFontWordLength > 2) {
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" ==> based on word length, char widths appear to make sense");
			return;
		}
		
		//	measure maximum char height and relation to nominal font box
		int maxCharHeight = 0;
		for (int c = 0; c < charImages.length; c++) {
			if (charImages[c] != null)
				maxCharHeight = Math.max(maxCharHeight, charImages[c].box.getHeight());
		}
		float charHeightRel = (maxCharHeight / (pFont.ascent - pFont.descent));
		if (DEBUG_TYPE1C_LOADING) System.out.println(" - maximum height is " + maxCharHeight + " for " + (pFont.ascent - pFont.descent) + ", relation is " + charHeightRel);
		
		//	compare nominal to measured char widths
		float ncCharWidthRelSum = 0;
		int ncCharWidthRelCount = 0;
		for (int c = 0; c < charImages.length; c++) {
			if (charImages[c] == null)
				continue;
			
			//	get basic data
			Integer sid;
			String chn;
			Integer chc;
			
			//	CID font mode
			if (unresolvedCodes == null) {
				sid = new Integer(-1);
				chn = StringUtils.getCharName(chars[c]);
				chc = new Integer((int) chars[c]);
			}
			
			//	Type 1 font mode
			else {
				sid = ((Integer) csContent.get(c));
				if (sid.intValue() == 0)
					continue;
				chn = ((String) sidResolver.get(sid));
				if ((chn == null) && (sid.intValue() >= sidResolver.size()) && ((sid.intValue() - sidResolver.size()) < sidIndex.size()))
					chn = ((String) sidIndex.get(sid.intValue() - sidResolver.size()));
				if ((chn == null) && pFont.diffNameMappings.containsKey(new Integer(c)))
					chn = ((String) pFont.diffNameMappings.get(new Integer(c)));
				chc = ((Integer) unresolvedCodes.get(chn));
				if (chc == null)
					chc = ((Integer) resolvedCodesOrCidMap.get(chn));
				if (chc == null) {
					chc = new Integer((int) StringUtils.getCharForName(chn));
					if (chc.intValue() < 1)
						continue;
				}
			}
			
			//	get nominal width and compare to actual width
			float nCharWidth = pFont.getCharWidth(new Character((char) chc.intValue()));
			float nCharWidthRel = ((charImages[c].box.getWidth() * 1000) / nCharWidth);
			if (DEBUG_TYPE1C_LOADING) System.out.println(" - nominal width of char " + c + " (SID " + sid + ", " + chn + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ") is " + nCharWidth);
			if (DEBUG_TYPE1C_LOADING) System.out.println(" - actual width is " + charImages[c].box.getWidth() + ", relation is " + nCharWidthRel);
			float cCharWidth = ((nCharWidth * nCharWidthRel) / charHeightRel);
			float ncCharWidthRel = (nCharWidth / cCharWidth);
			if (DEBUG_TYPE1C_LOADING) System.out.println(" --> computed width is " + cCharWidth + ", relation is " + ncCharWidthRel);
			ncCharWidthRelSum += ncCharWidthRel;
			ncCharWidthRelCount++;
		}
		float avgNcCharWidthRel = ((ncCharWidthRelCount == 0) ? 0 : (ncCharWidthRelSum / ncCharWidthRelCount));
		if (DEBUG_TYPE1C_LOADING) System.out.println(" --> average nominal char width to computed char width relation is " + avgNcCharWidthRel + " from " + ncCharWidthRelCount + " chars");
		
		//	this font seams to indicate sincere char widths
		//	TODO verify threshold, might be safer off with 2
		if (avgNcCharWidthRel < 1.5) {
			if (DEBUG_TYPE1C_LOADING) System.out.println(" ==> char widths appear to make sense");
			return;
		}
		
		//	nominal char width way larger than measured char width, we have implicit spaces
		if (DEBUG_TYPE1C_LOADING) System.out.println(" ==> char widths appear to imply spaces");
		pFont.hasImplicitSpaces = true;
		
		//	add measured char widths
		for (int c = 0; c < charImages.length; c++) {
			if (charImages[c] == null)
				continue;
			
			//	get basic data
			Integer sid;
			String chn;
			Integer chc;
			
			//	CID font mode
			if (unresolvedCodes == null) {
				sid = new Integer(-1);
				chn = StringUtils.getCharName(chars[c]);
				chc = new Integer((int) chars[c]);
			}
			
			//	Type 1 font mode
			else {
				sid = ((Integer) csContent.get(c));
				if (sid.intValue() == 0)
					continue;
				chn = ((String) sidResolver.get(sid));
				if ((chn == null) && (sid.intValue() >= sidResolver.size()) && ((sid.intValue() - sidResolver.size()) < sidIndex.size()))
					chn = ((String) sidIndex.get(sid.intValue() - sidResolver.size()));
				if ((chn == null) && pFont.diffNameMappings.containsKey(new Integer(c)))
					chn = ((String) pFont.diffNameMappings.get(new Integer(c)));
				chc = ((Integer) unresolvedCodes.get(chn));
				if (chc == null)
					chc = ((Integer) resolvedCodesOrCidMap.get(chn));
				if (chc == null) {
					chc = new Integer((int) StringUtils.getCharForName(chn));
					if (chc.intValue() < 1)
						continue;
				}
			}
			
			//	store actual width
			float nCharWidth = pFont.getCharWidth(new Character((char) chc.intValue()));
			float nCharWidthRel = ((charImages[c].box.getWidth() * 1000) / nCharWidth);
			float cCharWidth = ((nCharWidth * nCharWidthRel) / charHeightRel);
			pFont.setMeasuredCharWidth(chc, cCharWidth);
		}
	}
	
	private static abstract class OpReceiverType1 {
		int rWidth = 0;
		int x = 0;
		int y = 0;
		abstract void moveTo(int dx, int dy);
		abstract void lineTo(int dx, int dy);
		abstract void curveTo(int dx1, int dy1, int dx2, int dy2, int dx3, int dy3);
		abstract void closePath();
	}
	
	private static class OpTrackerType1 extends OpReceiverType1 {
//		String id = ("" + Math.random());
		int minX = 0;
		int minY = 0;
		int minPaintX = Integer.MAX_VALUE;
		int minPaintY = Integer.MAX_VALUE;
		int maxX = 0;
		int maxY = 0;
		int mCount = 0;
		boolean isMultiPath = false;
		void moveTo(int dx, int dy) {
			if (this.mCount != 0)
				this.isMultiPath = true;
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
		void lineTo(int dx, int dy) {
			this.minPaintX = Math.min(this.minPaintX, this.x);
			this.minPaintY = Math.min(this.minPaintY, this.y);
			this.x += dx;
			this.minX = Math.min(this.minX, this.x);
			this.maxX = Math.max(this.maxX, this.x);
			this.y += dy;
			this.minY = Math.min(this.minY, this.y);
			this.maxY = Math.max(this.maxY, this.y);
			this.minPaintX = Math.min(this.minPaintX, this.x);
			this.minPaintY = Math.min(this.minPaintY, this.y);
			this.mCount++;
//			System.out.println("Line " + this.id + " to " + dx + "/" + dy + ":");
//			System.out.println(" " + this.minX + " < X < " + this.maxX);
//			System.out.println(" " + this.minY + " < Y < " + this.maxY);
		}
		void curveTo(int dx1, int dy1, int dx2, int dy2, int dx3, int dy3) {
			this.lineTo(dx1, dy1);
			this.lineTo(dx2, dy2);
			this.lineTo(dx3, dy3);
		}
		void closePath() {}
	}
	
	private static class OpGraphicsType1 extends OpReceiverType1 {
		int minX;
		int minY;
		int height;
		int sx = 0;
		int sy = 0;
		int lCount = 0;
		BufferedImage img;
		Graphics2D gr;
		private float scale = 1.0f;
		OpGraphicsType1(int minX, int minY, int height, float scale, BufferedImage img) {
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
		void curveTo(int dx1, int dy1, int dx2, int dy2, int dx3, int dy3) {
			if (this.lCount == 0) {
				this.sx = this.x;
				this.sy = this.y;
			}
			CubicCurve2D.Float cc = new CubicCurve2D.Float();
			cc.setCurve(
					Math.round((this.scale * (this.x - this.minX)) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.height - (this.y - this.minY))) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.x - this.minX + dx1)) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.height - (this.y - this.minY + dy1))) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.x - this.minX + dx1 + dx2)) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.height - (this.y - this.minY + dy1 + dy2))) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.x - this.minX + dx1 + dx2 + dx3)) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.height - (this.y - this.minY + dy1 + dy2 + dy3))) + glyphOutlineFillSafetyEdge)
				);
			this.gr.draw(cc);
			this.x += (dx1 + dx2 + dx3);
			this.y += (dy1 + dy2 + dy3);
			this.lCount++;
		}
		void moveTo(int dx, int dy) {
			if (this.lCount != 0)
				this.closePath();
			this.lCount = 0;
			this.x += dx;
			this.y += dy;
		}
		void closePath() {
			this.gr.drawLine(
					Math.round((this.scale * (this.x - this.minX)) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.height - (this.y - this.minY))) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.sx - this.minX)) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.height - (this.sy - this.minY))) + glyphOutlineFillSafetyEdge)
				);
		}
	}
	
	private static final boolean DEBUG_TYPE1C_RENDRING = false;
	private static final int DEBUG_TYPE1C_TARGET_SID = -1;
	
	private static void runFontType1Ops(OpType1[] ops, OpReceiverType1 opr, boolean isMultiPath, boolean show, ImageDisplayDialog fidd, int cc, int sid) {
		ImageDisplayDialog idd = null;
		boolean emptyOp = false;
		
		for (int o = 0; o < ops.length; o++) {
			int op = ops[o].op;
//			System.out.print("Executing " + ops[o].name);
//			for (int a = 0; a < ops[o].args.length; a++)
//				System.out.print(" " + ops[o].args[a].intValue());
//			System.out.println();
			
//			int a = 0;
			int skipped = 0;// (skipFirst ? 1 : 0);
			int a = skipped;
			
			while (op != -1) {
				
				//	hstem, vstem, hstemhm, or vstemhm
				if ((op == 1) || (op == 3) || (op == 18) || (op == 23)) {
					if (DEBUG_TYPE1C_RENDRING)
						printOp(op, "<hints>", skipped, a, ops[o].args);
					if ((o == 0) && ((ops[o].args.length % 2) == 1))
						opr.rWidth += ops[o].args[a++].intValue();
					op = -1;
				}
				
				//	rmoveto |- dx1 dy1 rmoveto (21) |-
				//	moves the current point to a position at the relative coordinates (dx1, dy1).
				else if (op == 21) {
					if (DEBUG_TYPE1C_RENDRING)
						printOp(op, "rmoveto", skipped, a, ops[o].args);
					if (ops[o].args.length < 2)
						emptyOp = true;
					if ((o == 0) && ((a+2) < ops[o].args.length))
						opr.rWidth += ops[o].args[a++].intValue();
					opr.moveTo(ops[o].args[a++].intValue(), ops[o].args[a++].intValue());
					op = -1;
				}
				
				//Note 4 The first stack-clearing operator, which must be one of 
				// hstem,
				// hstemhm,
				// vstem,
				// vstemhm,
				// cntrmask,
				// hintmask,
				// - hmoveto,
				// - vmoveto,
				// - rmoveto, or
				// - endchar,
				//takes an additional argument — the width (as described earlier), which may be expressed as zero or one numeric argument.
				
				//	hmoveto |- dx1 hmoveto (22) |-
				//	moves the current point dx1 units in the horizontal direction. See Note 4.
				else if (op == 22) {
					if (DEBUG_TYPE1C_RENDRING)
						printOp(op, "hmoveto", skipped, a, ops[o].args);
					if (ops[o].args.length < 1)
						emptyOp = true;
					if ((o == 0) && ((a+1) < ops[o].args.length))
						opr.rWidth += ops[o].args[a++].intValue(); 
					opr.moveTo(ops[o].args[a++].intValue(), 0);
					op = -1;
				}
				
				//	vmoveto |- dy1 vmoveto (4) |-
				//	moves the current point dy1 units in the vertical direction. See Note 4.
				else if (op == 4) {
					if (DEBUG_TYPE1C_RENDRING)
						printOp(op, "vmoveto", skipped, a, ops[o].args);
					if (ops[o].args.length < 1)
						emptyOp = true;
					if ((o == 0) && ((a+1) < ops[o].args.length))
						opr.rWidth += ops[o].args[a++].intValue(); 
					opr.moveTo(0, ops[o].args[a++].intValue());
					op = -1;
				}
				
				//	rlineto |- {dxa dya}+ rlineto (5) |-
				//	appends a line from the current point to a position at the relative coordinates dxa, dya. Additional rlineto operations are performed for all subsequent argument pairs. The number of lines is determined from the number of arguments on the stack.
				else if (op == 5) {
					if (DEBUG_TYPE1C_RENDRING)
						printOp(op, "rlineto", skipped, a, ops[o].args);
					if (((ops[o].args.length - a) % 2) != 0)
						a++;
					if (ops[o].args.length < 2)
						emptyOp = true;
					if ((a+2) <= ops[o].args.length)
						opr.lineTo(ops[o].args[a++].intValue(), ops[o].args[a++].intValue());
					else op = -1;
				}
				
				//	hlineto |- dx1 {dya dxb}* hlineto (6) |- OR |- {dxa dyb}+ hlineto (6) |-
				//	appends a horizontal line of length dx1 to the current point.
				//	With an odd number of arguments, subsequent argument pairs are interpreted as alternating values of dy and dx, for which additional lineto operators draw alternating vertical and horizontal lines.
				//	With an even number of arguments, the arguments are interpreted as alternating horizontal and vertical lines. The number of lines is determined from the number of arguments on the stack.
				else if (op == 6) {
					if (DEBUG_TYPE1C_RENDRING)
						printOp(op, "hlineto", skipped, a, ops[o].args);
					if (ops[o].args.length < 1)
						emptyOp = true;
					if ((a+1) <= ops[o].args.length) {
						opr.lineTo(ops[o].args[a++].intValue(), 0);
						op = 7;
					}
					else op = -1;
				}
				
				//	vlineto |- dy1 {dxa dyb}* vlineto (7) |- OR |- {dya dxb}+ vlineto (7) |-
				//	appends a vertical line of length dy1 to the current point.
				//	With an odd number of arguments, subsequent argument pairs are interpreted as alternating values of dx and dy, for which additional lineto operators draw alternating horizontal and vertical lines.
				//	With an even number of arguments, the arguments are interpreted as alternating vertical and horizontal lines. The number of lines is determined from the number of arguments on the stack.
				else if (op == 7) {
					if (DEBUG_TYPE1C_RENDRING)
						printOp(op, "vlineto", skipped, a, ops[o].args);
					if (ops[o].args.length < 1)
						emptyOp = true;
					if ((a+1) <= ops[o].args.length) {
						opr.lineTo(0, ops[o].args[a++].intValue());
						op = 6;
					}
					else op = -1;
				}
				
				//	rrcurveto |- {dxa dya dxb dyb dxc dyc}+ rrcurveto (8) |-
				//	appends a Bézier curve, defined by dxa...dyc, to the current point. For each subsequent set of six arguments, an additional curve is appended to the current point. The number of curve segments is determined from the number of arguments on the number stack and is limited only by the size of the number stack.
				else if (op == 8) {
					if (DEBUG_TYPE1C_RENDRING)
						printOp(op, "rrcurveto", skipped, a, ops[o].args);
					
					//	skip superfluous arguments
					if (a == skipped) {
						while ((a < ops[o].args.length) && (((ops[o].args.length - a) % 6) != 0))
							a++;
						if (DEBUG_TYPE1C_RENDRING)
							System.out.println(" - skipped to parameter " + a);
					}
					if (ops[o].args.length < 6)
						emptyOp = true;
					
					//	execute op
					if ((a+6) <= ops[o].args.length)
						opr.curveTo(ops[o].args[a++].intValue(), ops[o].args[a++].intValue(), ops[o].args[a++].intValue(), ops[o].args[a++].intValue(), ops[o].args[a++].intValue(), ops[o].args[a++].intValue());
					else op = -1;
				}
				
				//	hhcurveto |- dy1? {dxa dxb dyb dxc}+ hhcurveto (27) |-
				//	appends one or more Bézier curves, as described by the dxa...dxc set of arguments, to the current point. For each curve, if there are 4 arguments, the curve starts and ends horizontal. The first curve need not start horizontal (the odd argument case). Note the argument order for the odd argument case.
				else if (op == 27) {
					if (DEBUG_TYPE1C_RENDRING)
						printOp(op, "hhcurveto", skipped, a, ops[o].args);
					
					//	skip superfluous arguments
					if (a == skipped) {
						while ((a < ops[o].args.length) && (((ops[o].args.length - a) % 4) > 1)) {
							a++;
							skipped++;
						}
						if (DEBUG_TYPE1C_RENDRING)
							System.out.println(" - skipped to parameter " + a);
					}
					if (ops[o].args.length < 4)
						emptyOp = true;
					
					//	execute op
					if ((a+4) <= ops[o].args.length) {
						int dx1;
						int dy1;
						int dx2;
						int dy2;
						int dx3;
						int dy3;
						if ((a == skipped) && (((ops[o].args.length - skipped) % 4) == 1))
							dy1 = ops[o].args[a++].intValue();
						else dy1 = 0;
						dx1 = ops[o].args[a++].intValue();
						dx2 = ops[o].args[a++].intValue();
						dy2 = ops[o].args[a++].intValue();
						dx3 = ops[o].args[a++].intValue();
						dy3 = 0;
						opr.curveTo(dx1, dy1, dx2, dy2, dx3, dy3);
					}
					else op = -1;
				}
				
				//	hvcurveto |- dx1 dx2 dy2 dy3 {dya dxb dyb dxc dxd dxe dye dyf}* dxf? hvcurveto (31) |- OR |- {dxa dxb dyb dyc dyd dxe dye dxf}+ dyf? hvcurveto (31) |-
				//	appends one or more Bézier curves to the current point. The tangent for the first Bézier must be horizontal, and the second must be vertical (except as noted below). If there is a multiple of four arguments, the curve starts horizontal and ends vertical. Note that the curves alternate between start horizontal, end vertical, and start vertical, and end horizontal. The last curve (the odd argument case) need not end horizontal/vertical.
				else if (op == 31) {
					if (DEBUG_TYPE1C_RENDRING)
						printOp(op, "hvcurveto", skipped, a, ops[o].args);
					
					//	skip superfluous arguments
					if (a == skipped) {
						while ((a < ops[o].args.length) && (((ops[o].args.length - a) % 4) > 1)) {
							a++;
							skipped++;
						}
						if (DEBUG_TYPE1C_RENDRING)
							System.out.println(" - skipped to parameter " + a);
					}
					if (ops[o].args.length < 4)
						emptyOp = true;
					
					//	hvcurveto |- dx1 dx2 dy2 dy3 {dya dxb dyb dxc dxd dxe dye dyf}* dxf? hvcurveto (31) |-
					if (((ops[o].args.length - skipped) % 8) >= 4) {
						if (a == skipped) {
							int dx1;
							int dy1;
							int dx2;
							int dy2;
							int dx3;
							int dy3;
							dx1 = ops[o].args[a++].intValue();
							dy1 = 0;
							dx2 = ops[o].args[a++].intValue();
							dy2 = ops[o].args[a++].intValue();
							if ((a+2) == ops[o].args.length) {
								dy3 = ops[o].args[a++].intValue();
								dx3 = ops[o].args[a++].intValue();
							}
							else {
								dy3 = ops[o].args[a++].intValue();
								dx3 = 0;
							}
							opr.curveTo(dx1, dy1, dx2, dy2, dx3, dy3);
						}
						else if ((a+8) <= ops[o].args.length) {
							int dx1;
							int dy1;
							int dx2;
							int dy2;
							int dx3;
							int dy3;
							dx1 = 0;
							dy1 = ops[o].args[a++].intValue();
							dx2 = ops[o].args[a++].intValue();
							dy2 = ops[o].args[a++].intValue();
							dx3 = ops[o].args[a++].intValue();
							dy3 = 0;
							opr.curveTo(dx1, dy1, dx2, dy2, dx3, dy3);
							dx1 = ops[o].args[a++].intValue();
							dy1 = 0;
							dx2 = ops[o].args[a++].intValue();
							dy2 = ops[o].args[a++].intValue();
							if ((a+2) == ops[o].args.length) {
								dy3 = ops[o].args[a++].intValue();
								dx3 = ops[o].args[a++].intValue();
							}
							else {
								dy3 = ops[o].args[a++].intValue();
								dx3 = 0;
							}
							opr.curveTo(dx1, dy1, dx2, dy2, dx3, dy3);
						}
						else op = -1;
					}
					
					//	hvcurveto |- {dxa dxb dyb dyc dyd dxe dye dxf}+ dyf? hvcurveto (31) |-
					else {
						if ((a+8) <= ops[o].args.length) {
							int dx1;
							int dy1;
							int dx2;
							int dy2;
							int dx3;
							int dy3;
							dx1 = ops[o].args[a++].intValue();
							dy1 = 0;
							dx2 = ops[o].args[a++].intValue();
							dy2 = ops[o].args[a++].intValue();
							dx3 = 0;
							dy3 = ops[o].args[a++].intValue();
							opr.curveTo(dx1, dy1, dx2, dy2, dx3, dy3);
							dx1 = 0;
							dy1 = ops[o].args[a++].intValue();
							dx2 = ops[o].args[a++].intValue();
							dy2 = ops[o].args[a++].intValue();
							dx3 = ops[o].args[a++].intValue();
							if ((a+1) == ops[o].args.length)
								dy3 = ops[o].args[a++].intValue();
							else dy3 = 0;
							opr.curveTo(dx1, dy1, dx2, dy2, dx3, dy3);
						}
						else op = -1;
					}
				}
				
				//	rcurveline |- {dxa dya dxb dyb dxc dyc}+ dxd dyd rcurveline (24) |-
				//	is equivalent to one rrcurveto for each set of six arguments dxa...dyc, followed by exactly one rlineto using the dxd, dyd arguments. The number of curves is determined from the count on the argument stack.
				else if (op == 24) {
					if (DEBUG_TYPE1C_RENDRING)
						printOp(op, "rcurveline", skipped, a, ops[o].args);
					
					//	skip superfluous arguments
					if (a == skipped) {
						while ((a < ops[o].args.length) && (((ops[o].args.length - a) % 6) != 2))
							a++;
						if (DEBUG_TYPE1C_RENDRING)
							System.out.println(" - skipped to parameter " + a);
					}
					if (ops[o].args.length < 8)
						emptyOp = true;
					
					//	execute op
					while ((a+8) <= ops[o].args.length)
						opr.curveTo(ops[o].args[a++].intValue(), ops[o].args[a++].intValue(), ops[o].args[a++].intValue(), ops[o].args[a++].intValue(), ops[o].args[a++].intValue(), ops[o].args[a++].intValue());
					if ((a+2) <= ops[o].args.length)
						opr.lineTo(ops[o].args[a++].intValue(), ops[o].args[a++].intValue());
					op = -1;
				}
				
				//	rlinecurve |- {dxa dya}+ dxb dyb dxc dyc dxd dyd rlinecurve (25) |-
				//	is equivalent to one rlineto for each pair of arguments beyond the six arguments dxb...dyd needed for the one rrcurveto command. The number of lines is determined from the count of items on the argument stack.
				else if (op == 25) {
					if (DEBUG_TYPE1C_RENDRING)
						printOp(op, "rlinecurve", skipped, a, ops[o].args);
					
					//	skip superfluous arguments
					if (a == skipped) {
						while ((a < ops[o].args.length) && (((ops[o].args.length - a) % 2) != 0))
							a++;
						if (DEBUG_TYPE1C_RENDRING)
							System.out.println(" - skipped to parameter " + a);
					}
					if (ops[o].args.length < 8)
						emptyOp = true;
					
					//	execute op
					while ((a+8) <= ops[o].args.length)
						opr.lineTo(ops[o].args[a++].intValue(), ops[o].args[a++].intValue());
					if ((a+6) <= ops[o].args.length)
						opr.curveTo(ops[o].args[a++].intValue(), ops[o].args[a++].intValue(), ops[o].args[a++].intValue(), ops[o].args[a++].intValue(), ops[o].args[a++].intValue(), ops[o].args[a++].intValue());
					op = -1;
				}
				
				//	vhcurveto |- dy1 dx2 dy2 dx3 {dxa dxb dyb dyc dyd dxe dye dxf}* dyf? vhcurveto (30) |- OR |- {dya dxb dyb dxc dxd dxe dye dyf}+ dxf? vhcurveto (30) |-
				//	appends one or more Bézier curves to the current point, where the first tangent is vertical and the second tangent is horizontal. This command is the complement of hvcurveto; see the description of hvcurveto for more information.
				else if (op == 30) {
					if (DEBUG_TYPE1C_RENDRING)
						printOp(op, "vhcurveto", skipped, a, ops[o].args);
					
					//	skip superfluous arguments
					if (a == skipped) {
						while ((a < ops[o].args.length) && (((ops[o].args.length - a) % 4) > 1)) {
							a++;
							skipped++;
						}
						if (DEBUG_TYPE1C_RENDRING)
							System.out.println(" - skipped to parameter " + a);
					}
					if (ops[o].args.length < 4)
						emptyOp = true;
					
					//	vhcurveto |- dy1 dx2 dy2 dx3 {dxa dxb dyb dyc dyd dxe dye dxf}* dyf? vhcurveto (30) |-
					if (((ops[o].args.length - skipped) % 8) >= 4) {
						if (a == skipped) {
							int dx1;
							int dy1;
							int dx2;
							int dy2;
							int dx3;
							int dy3;
							dx1 = 0;
							dy1 = ops[o].args[a++].intValue();
							dx2 = ops[o].args[a++].intValue();
							dy2 = ops[o].args[a++].intValue();
							dx3 = ops[o].args[a++].intValue();
							if ((a+1) == ops[o].args.length)
								dy3 = ops[o].args[a++].intValue();
							else dy3 = 0;
							opr.curveTo(dx1, dy1, dx2, dy2, dx3, dy3);
						}
						else if ((a+8) <= ops[o].args.length) {
							int dx1;
							int dy1;
							int dx2;
							int dy2;
							int dx3;
							int dy3;
							dx1 = ops[o].args[a++].intValue();
							dy1 = 0;
							dx2 = ops[o].args[a++].intValue();
							dy2 = ops[o].args[a++].intValue();
							dx3 = 0;
							dy3 = ops[o].args[a++].intValue();
							opr.curveTo(dx1, dy1, dx2, dy2, dx3, dy3);
							dx1 = 0;
							dy1 = ops[o].args[a++].intValue();
							dx2 = ops[o].args[a++].intValue();
							dy2 = ops[o].args[a++].intValue();
							dx3 = ops[o].args[a++].intValue();
							if ((a+1) == ops[o].args.length)
								dy3 = ops[o].args[a++].intValue();
							else dy3 = 0;
							opr.curveTo(dx1, dy1, dx2, dy2, dx3, dy3);
						}
						else op = -1;
					}
					
					//	vhcurveto |- {dya dxb dyb dxc dxd dxe dye dyf}+ dxf? vhcurveto (30) |-
					else {
						if ((a+8) <= ops[o].args.length) {
							int dx1;
							int dy1;
							int dx2;
							int dy2;
							int dx3;
							int dy3;
							dx1 = 0;
							dy1 = ops[o].args[a++].intValue();
							dx2 = ops[o].args[a++].intValue();
							dy2 = ops[o].args[a++].intValue();
							dx3 = ops[o].args[a++].intValue();
							dy3 = 0;
							opr.curveTo(dx1, dy1, dx2, dy2, dx3, dy3);
							dx1 = ops[o].args[a++].intValue();
							dy1 = 0;
							dx2 = ops[o].args[a++].intValue();
							dy2 = ops[o].args[a++].intValue();
							if ((a+2) == ops[o].args.length) {
								dy3 = ops[o].args[a++].intValue();
								dx3 = ops[o].args[a++].intValue();
							}
							else {
								dy3 = ops[o].args[a++].intValue();
								dx3 = 0;
							}
							opr.curveTo(dx1, dy1, dx2, dy2, dx3, dy3);
						}
						else op = -1;
					}
				}
				
				//	vvcurveto |- dx1? {dya dxb dyb dyc}+ vvcurveto (26) |-
				//	appends one or more curves to the current point. If the argument count is a multiple of four, the curve starts and ends vertical. If the argument count is odd, the first curve does not begin with a vertical tangent.
				else if (op == 26) {
					if (DEBUG_TYPE1C_RENDRING)
						printOp(op, "vvcurveto", skipped, a, ops[o].args);
					
					//	skip superfluous arguments
					if (a == skipped) {
						while ((a < ops[o].args.length) && (((ops[o].args.length - a) % 4) > 1)) {
							a++;
							skipped++;
						}
						if (DEBUG_TYPE1C_RENDRING)
							System.out.println(" - skipped to parameter " + a);
					}
					if (ops[o].args.length < 4)
						emptyOp = true;
					
					//	execute op
					if ((a+4) <= ops[o].args.length) {
						int dx1;
						int dy1;
						int dx2;
						int dy2;
						int dx3;
						int dy3;
						if ((a == skipped) && (((ops[o].args.length - skipped) % 4) == 1))
							dx1 = ops[o].args[a++].intValue();
						else dx1 = 0;
						dy1 = ops[o].args[a++].intValue();
						dx2 = ops[o].args[a++].intValue();
						dy2 = ops[o].args[a++].intValue();
						dx3 = 0;
						dy3 = ops[o].args[a++].intValue();
						opr.curveTo(dx1, dy1, dx2, dy2, dx3, dy3);
					}
					else op = -1;
				}
				
				//flex |- dx1 dy1 dx2 dy2 dx3 dy3 dx4 dy4 dx5 dy5 dx6 dy6 fd flex (12 35) |-
				//causes two Bézier curves, as described by the arguments (as shown in Figure 2 below), to be rendered as a straight line when the flex depth is less than fd /100 device pixels, and as curved lines when the flex depth is greater than or equal to fd/100 device pixels. The flex depth for a horizontal curve, as shown in Figure 2, is the distance from the join point to the line connecting the start and end points on the curve. If the curve is not exactly horizontal or vertical, it must be determined whether the curve is more horizontal or vertical by the method described in the flex1 description, below, and as illustrated in Figure 3.
				//Note 5 In cases where some of the points have the same x or y coordinate as other points in the curves, arguments may be omitted by using one of the following forms of the flex operator, hflex, hflex1, or flex1.
				else if (op == 1035) {
					if (DEBUG_TYPE1C_RENDRING)
						printOp(op, "flex", skipped, a, ops[o].args);
					
					if (ops[o].args.length < 13)
						emptyOp = true;
					
					//	execute op
					if ((a+12) <= ops[o].args.length) {
						int dx1;
						int dy1;
						int dx2;
						int dy2;
						int dx3;
						int dy3;
						dx1 = ops[o].args[a++].intValue();
						dy1 = ops[o].args[a++].intValue();
						dx2 = ops[o].args[a++].intValue();
						dy2 = ops[o].args[a++].intValue();
						dx3 = ops[o].args[a++].intValue();
						dy3 = ops[o].args[a++].intValue();
						opr.curveTo(dx1, dy1, dx2, dy2, dx3, dy3);
						dx1 = ops[o].args[a++].intValue();
						dy1 = ops[o].args[a++].intValue();
						dx2 = ops[o].args[a++].intValue();
						dy2 = ops[o].args[a++].intValue();
						dx3 = ops[o].args[a++].intValue();
						dy3 = ops[o].args[a++].intValue();
						opr.curveTo(dx1, dy1, dx2, dy2, dx3, dy3);
					}
					else op = -1;
				}
				
				//hflex |- dx1 dx2 dy2 dx3 dx4 dx5 dx6 hflex (12 34) |-
				//causes the two curves described by the arguments dx1...dx6 to be rendered as a straight line when the flex depth is less than 0.5 (that is, fd is 50) device pixels, and as curved lines when the flex depth is greater than or equal to 0.5 device pixels. hflex is used when the following are all true:
				//a) the starting and ending points, first and last control points have the same y value.
				//b) the joining point and the neighbor control points have the same y value.
				//c) the flex depth is 50.
				else if (op == 1034) {
					if (DEBUG_TYPE1C_RENDRING)
						printOp(op, "hflex", skipped, a, ops[o].args);
					
					if (ops[o].args.length < 7)
						emptyOp = true;
					
					//	execute op
					if ((a+7) <= ops[o].args.length) {
						int dx1;
						int dy1;
						int dx2;
						int dy2;
						int dx3;
						int dy3;
						dx1 = ops[o].args[a++].intValue();
						dy1 = 0;
						dx2 = ops[o].args[a++].intValue();
						dy2 = ops[o].args[a++].intValue();
						dx3 = ops[o].args[a++].intValue();
						dy3 = 0;
						opr.curveTo(dx1, dy1, dx2, dy2, dx3, dy3);
						dx1 = ops[o].args[a++].intValue();
						dy1 = 0;
						dx2 = ops[o].args[a++].intValue();
						dy2 = 0;
						dx3 = ops[o].args[a++].intValue();
						dy3 = 0;
						opr.curveTo(dx1, dy1, dx2, dy2, dx3, dy3);
					}
					else op = -1;
				}
				
				//hflex1 |- dx1 dy1 dx2 dy2 dx3 dx4 dx5 dy5 dx6 hflex1 (12 36) |-
				//causes the two curves described by the arguments to be rendered as a straight line when the flex depth is less than 0.5 device pixels, and as curved lines when the flex depth is greater than or equal to 0.5 device pixels. hflex1 is used if the conditions for hflex are not met but all of the following are true:
				//a) the starting and ending points have the same y value,
				//b) the joining point and the neighbor control points have the same y value.
				else if (op == 1036) {
					if (DEBUG_TYPE1C_RENDRING)
						printOp(op, "hflex1", skipped, a, ops[o].args);
					
					if (ops[o].args.length < 9)
						emptyOp = true;
					
					//	execute op
					if ((a+9) <= ops[o].args.length) {
						int dx1;
						int dy1;
						int dx2;
						int dy2;
						int dx3;
						int dy3;
						dx1 = ops[o].args[a++].intValue();
						dy1 = ops[o].args[a++].intValue();
						dx2 = ops[o].args[a++].intValue();
						dy2 = ops[o].args[a++].intValue();
						dx3 = ops[o].args[a++].intValue();
						dy3 = 0;
						opr.curveTo(dx1, dy1, dx2, dy2, dx3, dy3);
						dx1 = ops[o].args[a++].intValue();
						dy1 = 0;
						dx2 = ops[o].args[a++].intValue();
						dy2 = ops[o].args[a++].intValue();
						dx3 = ops[o].args[a++].intValue();
						dy3 = 0;
						opr.curveTo(dx1, dy1, dx2, dy2, dx3, dy3);
					}
					else op = -1;
				}
				
				//flex1 |- dx1 dy1 dx2 dy2 dx3 dy3 dx4 dy4 dx5 dy5 d6 flex1 (12 37) |-
				//causes the two curves described by the arguments to be rendered as a straight line when the flex depth is less than 0.5 device pixels, and as curved lines when the flex depth is greater than or equal to 0.5 device pixels.
				//The d6 argument will be either a dx or dy value, depending on the curve (see Figure 3). To determine the correct value, compute the distance from the starting point (x, y), the first point of the first curve, to the last flex control point (dx5, dy5) by summing all the arguments except d6; call this (dx, dy). If abs(dx) > abs(dy), then the last point’s x-value is given by d6, and its y-value is equal to y. Otherwise, the last point’s x-value is equal to x and its y-value is given by d6.
				//flex1 is used if the conditions for hflex and hflex1 are notmet but all of the following are true:
				//a) the starting and ending points have the same x or y value,
				//b) the flex depth is 50.
				else if (op == 1037) {
					if (DEBUG_TYPE1C_RENDRING)
						printOp(op, "flex1", skipped, a, ops[o].args);
					
					if (ops[o].args.length < 11)
						emptyOp = true;
					
					//	execute op
					if ((a+11) <= ops[o].args.length) {
						int sx = opr.x;
						int sy = opr.y;
						int dx1;
						int dy1;
						int dx2;
						int dy2;
						int dx3;
						int dy3;
						dx1 = ops[o].args[a++].intValue();
						dy1 = ops[o].args[a++].intValue();
						dx2 = ops[o].args[a++].intValue();
						dy2 = ops[o].args[a++].intValue();
						dx3 = ops[o].args[a++].intValue();
						dy3 = ops[o].args[a++].intValue();
						opr.curveTo(dx1, dy1, dx2, dy2, dx3, dy3);
						dx1 = ops[o].args[a++].intValue();
						dy1 = ops[o].args[a++].intValue();
						dx2 = ops[o].args[a++].intValue();
						dy2 = ops[o].args[a++].intValue();
						int dx = (opr.x + dx1 + dx2 - sx);
						int dy = (opr.y + dy1 + dy2 - sy);
						if (Math.abs(dx) > Math.abs(dy)) {
							dx3 = ops[o].args[a++].intValue();
							dy3 = 0;
						}
						else {
							dx3 = 0;
							dy3 = ops[o].args[a++].intValue();
						}
						opr.curveTo(dx1, dy1, dx2, dy2, dx3, dy3);
					}
					else op = -1;
				}
				
				//	endchar – endchar (14) |–
				//	finishes a charstring outline definition, and must be the last operator in a character’s outline.
				else if (op == 14) {
					if (DEBUG_TYPE1C_RENDRING)
						printOp(op, "endchar", skipped, a, ops[o].args);
					if ((o == 0) && (ops[o].args.length > 0))
						opr.rWidth += ops[o].args[a++].intValue(); 
					opr.closePath();
					op = -1;
				}
				
				//	hintmask
				else if (op == 19) {
					if (DEBUG_TYPE1C_RENDRING)
						printOp(op, "hintmask", skipped, a, ops[o].args);
					if ((o == 0) && (ops[o].args.length > 1))
						opr.rWidth += ops[o].args[a++].intValue();
					op = -1;
				}
				
				//	cntrmask
				else if (op == 20) {
					if (DEBUG_TYPE1C_RENDRING)
						printOp(op, "cntrmask", skipped, a, ops[o].args);
					if ((o == 0) && (ops[o].args.length > 1))
						opr.rWidth += ops[o].args[a++].intValue();
					op = -1;
				}
				
				//Note 6 The charstring itself may end with a call(g)subr; the subroutine must then end with an endchar operator.
				//Note 7 A character that does not have a path (e.g. a space character) may consist of an endchar operator preceded only by a width value. Although the width must be specified in the font, it may be specified as the defaultWidthX in the CFF data, in which case it should not be specified in the charstring. Also, it may appear in the charstring as the difference from nominalWidthX. Thus the smallest legal charstring consists of a single endchar operator.
				//Note 8 endchar also has a deprecated function; see Appendix C, “Comaptibility and Deprecated Operators.”
				else {
					if (DEBUG_TYPE1C_RENDRING)
						printOp(op, null, skipped, a, ops[o].args);
					op = -1;
				}
			}
			
			if (DEBUG_TYPE1C_RENDRING && (opr instanceof OpGraphicsType1))
				System.out.println(" ==> dot at (" + ((OpGraphicsType1) opr).x + "/" + ((OpGraphicsType1) opr).y + ")");
			
			if (DEBUG_TYPE1C_RENDRING && (show || emptyOp) && (opr instanceof OpGraphicsType1)) {
				BufferedImage dImg = new BufferedImage(((OpGraphicsType1) opr).img.getWidth(), ((OpGraphicsType1) opr).img.getHeight(), BufferedImage.TYPE_INT_RGB);
				Graphics g = dImg.getGraphics();
				g.drawImage(((OpGraphicsType1) opr).img, 0, 0, dImg.getWidth(), dImg.getHeight(), null);
				g.setColor(Color.RED);
				g.fillRect(((OpGraphicsType1) opr).x-2, ((OpGraphicsType1) opr).y-2, 5, 5);
				if (idd == null) {
					idd = new ImageDisplayDialog("Rendering Progress");
					idd.setSize((dImg.getWidth() + 200), (dImg.getHeight() + 100));
				}
				idd.addImage(dImg, ("After " + ops[o].op));
				idd.setLocationRelativeTo(null);
				idd.setVisible(true);
			}
		}
		
		//	only tracking, we're done here
		if (opr instanceof OpTrackerType1)
			return;
		
		//	fill outline
		fillGlyphOutline(((OpGraphicsType1) opr).img, ((OpGraphicsType1) opr).scale, show);
//		int blackRgb = Color.BLACK.getRGB();
//		int whiteRgb = Color.WHITE.getRGB();
//		int outsideRgb = Color.GRAY.getRGB();
//		int insideRgb = Color.GRAY.darker().getRGB();
//		HashSet insideRgbs = new HashSet();
//		HashSet outsideRgbs = new HashSet();
//		
//		//	fill outside
//		fill(((OpGraphicsType1) opr).img, 1, 1, whiteRgb, outsideRgb);
//		insideRgbs.add(new Integer(insideRgb));
//		insideRgbs.add(new Integer(blackRgb));
//		outsideRgbs.add(new Integer(outsideRgb));
//		
//		//	fill multi-path characters outside-in
//		if (isMultiPath || true) {
//			outsideRgb = (new Color(outsideRgb)).brighter().getRGB();
//			int seekWidth = Math.max(1, (Math.round(((OpGraphicsType1) opr).scale * 10)));
//			boolean gotWhite = true;
//			boolean outmostWhiteIsInside = true;
//			while (gotWhite) {
//				
//				if (DEBUG_TYPE1C_RENDRING && show && (opr instanceof OpGraphicsType1)) {
//					BufferedImage dImg = new BufferedImage(((OpGraphicsType1) opr).img.getWidth(), ((OpGraphicsType1) opr).img.getHeight(), BufferedImage.TYPE_INT_RGB);
//					Graphics g = dImg.getGraphics();
//					g.drawImage(((OpGraphicsType1) opr).img, 0, 0, dImg.getWidth(), dImg.getHeight(), null);
//					g.setColor(Color.RED);
//					g.fillRect(((OpGraphicsType1) opr).x-2, ((OpGraphicsType1) opr).y-2, 5, 5);
//					if (idd == null) {
//						idd = new ImageDisplayDialog("Rendering Progress");
//						idd.setSize((dImg.getWidth() + 200), (dImg.getHeight() + 100));
//					}
//					idd.addImage(dImg, ("Filling"));
//					idd.setLocationRelativeTo(null);
//					idd.setVisible(true);
//				}
//				
//				gotWhite = false;
//				int fillRgb;
//				if (outmostWhiteIsInside) {
//					fillRgb = insideRgb;
//					insideRgbs.add(new Integer(fillRgb));
//				}
//				else {
//					fillRgb = outsideRgb;
//					outsideRgbs.add(new Integer(fillRgb));
//				}
////				System.out.println("Fill RGB is " + fillRgb);
//				for (int c = seekWidth; c < ((OpGraphicsType1) opr).img.getWidth(); c += seekWidth) {
////					System.out.println("Investigating column " + c);
//					int r = 0;
//					while ((r < ((OpGraphicsType1) opr).img.getHeight()) && (((OpGraphicsType1) opr).img.getRGB(c, r) != whiteRgb) && (((OpGraphicsType1) opr).img.getRGB(c, r) != fillRgb)) {
////						if ((r % 10) == 0) System.out.println(" - ignoring pixel with RGB " + img.getRGB(c, r));
//						r++;
//					}
////					System.out.println(" - found interesting pixel at row " + r);
//					if (r >= ((OpGraphicsType1) opr).img.getHeight()) {
////						System.out.println(" --> bottom of column");
//						continue;
//					}
////					System.out.println(" - RGB is " + img.getRGB(c, r));
//					if (((OpGraphicsType1) opr).img.getRGB(c, r) == fillRgb) {
////						System.out.println(" --> filled before");
//						continue;
//					}
//					
////					System.out.println(" --> filling at " + c + "/" + r + " with " + fillRgb);
//					fill(((OpGraphicsType1) opr).img, c, r, whiteRgb, fillRgb);
//					gotWhite = true;
//				}
//				
//				for (int c = (((OpGraphicsType1) opr).img.getWidth() - seekWidth); c > 0; c -= seekWidth) {
////					System.out.println("Investigating column " + c);
//					int r = (((OpGraphicsType1) opr).img.getHeight() - 1);
//					while ((r >= 0) && (((OpGraphicsType1) opr).img.getRGB(c, r) != whiteRgb) && (((OpGraphicsType1) opr).img.getRGB(c, r) != fillRgb)) {
////						if ((r % 10) == 0) System.out.println(" - ignoring pixel with RGB " + img.getRGB(c, r));
//						r--;
//					}
////					System.out.println(" - found interesting pixel at row " + r);
//					if (r < 0) {
////						System.out.println(" --> bottom of column");
//						continue;
//					}
////					System.out.println(" - RGB is " + img.getRGB(c, r));
//					if (((OpGraphicsType1) opr).img.getRGB(c, r) == fillRgb) {
////						System.out.println(" --> filled before");
//						continue;
//					}
//					
////					System.out.println(" --> filling at " + c + "/" + r + " with " + fillRgb);
//					fill(((OpGraphicsType1) opr).img, c, r, whiteRgb, fillRgb);
//					gotWhite = true;
//				}
//				
//				for (int r = seekWidth; r < ((OpGraphicsType1) opr).img.getHeight(); r += seekWidth) {
////					System.out.println("Investigating row " + r);
//					int c = 0;
//					while ((c < ((OpGraphicsType1) opr).img.getWidth()) && (((OpGraphicsType1) opr).img.getRGB(c, r) != whiteRgb) && (((OpGraphicsType1) opr).img.getRGB(c, r) != fillRgb)) {
////						if ((c % 10) == 0) System.out.println(" - ignoring pixel with RGB " + img.getRGB(c, r));
//						c++;
//					}
////					System.out.println(" - found interesting pixel at column " + r);
//					if (c >= ((OpGraphicsType1) opr).img.getWidth()) {
////						System.out.println(" --> right end of row");
//						continue;
//					}
////					System.out.println(" - RGB is " + img.getRGB(c, r));
//					if (((OpGraphicsType1) opr).img.getRGB(c, r) == fillRgb) {
////						System.out.println(" --> filled before");
//						continue;
//					}
//					
////					System.out.println(" --> filling at " + c + "/" + r + " with " + fillRgb);
//					fill(((OpGraphicsType1) opr).img, c, r, whiteRgb, fillRgb);
//					gotWhite = true;
//				}
//				
//				for (int r = (((OpGraphicsType1) opr).img.getHeight() - seekWidth); r >= 0; r -= seekWidth) {
////					System.out.println("Investigating row " + r);
//					int c = (((OpGraphicsType1) opr).img.getWidth() - 1);
//					while ((c >= 0) && (((OpGraphicsType1) opr).img.getRGB(c, r) != whiteRgb) && (((OpGraphicsType1) opr).img.getRGB(c, r) != fillRgb)) {
////						if ((c % 10) == 0) System.out.println(" - ignoring pixel with RGB " + img.getRGB(c, r));
//						c--;
//					}
////					System.out.println(" - found interesting pixel at column " + r);
//					if (c < 0) {
////						System.out.println(" --> right end of row");
//						continue;
//					}
////					System.out.println(" - RGB is " + img.getRGB(c, r));
//					if (((OpGraphicsType1) opr).img.getRGB(c, r) == fillRgb) {
////						System.out.println(" --> filled before");
//						continue;
//					}
//					
////					System.out.println(" --> filling at " + c + "/" + r + " with " + fillRgb);
//					fill(((OpGraphicsType1) opr).img, c, r, whiteRgb, fillRgb);
//					gotWhite = true;
//				}
//				
//				if (outmostWhiteIsInside) {
//					outmostWhiteIsInside = false;
//					insideRgb = (new Color(insideRgb)).darker().getRGB();
////					System.out.println("Inside RGB set to " + insideRgb);
//				}
//				else {
//					outmostWhiteIsInside = true;
//					outsideRgb = (new Color(outsideRgb)).brighter().getRGB();
////					System.out.println("Outside RGB set to " + outsideRgb);
//				}
//			}
//		}
//		
//		//	fill single-path character
//		else insideRgbs.add(new Integer(whiteRgb));
//		
//		
//		//	make it black and white, finally
//		for (int c = 0; c < ((OpGraphicsType1) opr).img.getWidth(); c++) {
//			for (int r = 0; r < ((OpGraphicsType1) opr).img.getHeight(); r++) {
//				int rgb = ((OpGraphicsType1) opr).img.getRGB(c, r);
//				if (insideRgbs.contains(new Integer(rgb)))
//					((OpGraphicsType1) opr).img.setRGB(c, r, blackRgb);
//				else ((OpGraphicsType1) opr).img.setRGB(c, r, whiteRgb);
//			}
//		}
		
		//	scale down image
		int maxHeight = 100;
		((OpGraphicsType1) opr).setImage(scaleImage(((OpGraphicsType1) opr).img, maxHeight));
//		if (((OpGraphicsType1) opr).img.getHeight() > maxHeight) {
//			BufferedImage sImg = new BufferedImage(((maxHeight * ((OpGraphicsType1) opr).img.getWidth()) / ((OpGraphicsType1) opr).img.getHeight()), maxHeight, ((OpGraphicsType1) opr).img.getType());
//			sImg.getGraphics().drawImage(((OpGraphicsType1) opr).img, 0, 0, sImg.getWidth(), sImg.getHeight(), null);
////			System.out.println("Scaled-down image is " + sImg.getWidth() + " x " + sImg.getHeight() + " (" + (((float) sImg.getWidth()) / sImg.getHeight()) + ")");
//			((OpGraphicsType1) opr).setImage(sImg);
//		}
		
		//	display result for rendering tests
		if (DEBUG_TYPE1C_RENDRING && (show || emptyOp)) {
			if (idd != null) {
				idd.addImage(((OpGraphicsType1) opr).img, "Result");
				idd.setLocationRelativeTo(null);
				idd.setVisible(true);
			}
			if (fidd != null) {
				fidd.addImage(((OpGraphicsType1) opr).img, ("" + sid));
				if (idd == null) {
					fidd.setLocationRelativeTo(null);
					fidd.setVisible(true);
				}
			}
		}
		else if (fidd != null)
			fidd.addImage(((OpGraphicsType1) opr).img, (cc + ": " + sid));
	}
	
	private static final void printOp(int op, String opName, int skipped, int a, Number[] args) {
		System.out.print("Executing " + ((opName == null) ? ("" + op + ":") : (opName + " (" + op + "):")));
		for (int i = 0; i < Math.min(skipped, args.length); i++)
			System.out.print(" [" + args[i].intValue() + "]");
		for (int i = skipped; i < Math.min(a, args.length); i++)
			System.out.print(" (" + args[i].intValue() + ")");
		for (int i = a; i < args.length; i++)
			System.out.print(" " + args[i].intValue());
		System.out.println();
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
//		TODO reactivate this once we have better tracking to splines
//		if (img.getHeight() > maxHeight) {
//			BufferedImage sImg = new BufferedImage(((maxHeight * img.getWidth()) / img.getHeight()), maxHeight, img.getType());
//			sImg.getGraphics().drawImage(img, 0, 0, sImg.getWidth(), sImg.getHeight(), null);
//			return sImg;
//		}
//		else
			return img;
	}
	
	private static int readFontType1cEncoding(byte[] data, int start, HashMap eDict) {
		if (DEBUG_TYPE1C_LOADING) System.out.println("Reading encoding:");
		int i = start;
		int fmt = convertUnsigned(data[i++]);
		if (DEBUG_TYPE1C_LOADING) System.out.println(" - format is " + fmt);
		if (fmt == 0) {
			int nCodes = convertUnsigned(data[i++]);
			if (DEBUG_TYPE1C_LOADING) System.out.println(" - expecting " + nCodes + " codes");
			for (int c = 1; c <= nCodes; c++) {
				int code = convertUnsigned(data[i++]);
//				if (DEBUG_TYPE1C_LOADING) System.out.println(" - 0 got char " + c + ": " + code);
				eDict.put(new Integer(c), new Integer(code));
			}
		}
		else if (fmt == 1) {
			int nRanges = convertUnsigned(data[i++]);
			if (DEBUG_TYPE1C_LOADING) System.out.println(" - expecting " + nRanges + " ranges");
			for (int r = 0; r < nRanges; r++) {
				int rStart = convertUnsigned(data[i++]);
				int rSize = convertUnsigned(data[i++]);
				for (int ro = 0; ro < rSize; ro++) {
					int code = (rStart + ro);
//					if (DEBUG_TYPE1C_LOADING) System.out.println(" - 1 got" + ((ro == 0) ? "" : " next") + " char " + (eDict.size()+1) + ": " + code);
					eDict.put(new Integer(eDict.size()+1), new Integer(code));
				}
			}
		}
		return i;
	}
	
	private static int readFontType1cCharset(byte[] data, int start, int charCount, ArrayList content) {
		if (DEBUG_TYPE1C_LOADING) System.out.println("Reading char set:");
		int i = start;
		int fmt = convertUnsigned(data[i++]);
		if (DEBUG_TYPE1C_LOADING) System.out.println(" - format is " + fmt);
		if (fmt == 0) {
			if (content != null)
				content.add(new Integer(0));
			for (int c = 1; c < charCount; c++) {
				int sid = ((convertUnsigned(data[i++]) * 256) + convertUnsigned(data[i++]));
				if (content != null)
					content.add(new Integer(sid));
//				if (DEBUG_TYPE1C_LOADING) System.out.println(" - 0 got char " + c + ": " + sid);
			}
		}
		else if (fmt == 1) {
			int toCome = charCount;
			while (toCome > 0) {
				int sid = ((convertUnsigned(data[i++]) * 256) + convertUnsigned(data[i++]));
				toCome--;
				if (content != null)
					content.add(new Integer(sid));
//				if (DEBUG_TYPE1C_LOADING) System.out.println(" - 1 got char " + content.size() + ": " + sid);
				int toComeInRange = convertUnsigned(data[i++]);
//				if (DEBUG_TYPE1C_LOADING) System.out.println("   - " + toComeInRange + " more in range, " + toCome + " in total");
				for (int c = 0; c < toComeInRange; c++) {
					sid++;
					toCome--;
					if (content != null)
						content.add(new Integer(sid));
//					if (DEBUG_TYPE1C_LOADING) System.out.println(" - 1 got next char " + content.size() + ": " + sid);
				}
			}
		}
		else if (fmt == 2) {
			int toCome = charCount;
			while (toCome > 0) {
				int sid = ((convertUnsigned(data[i++]) * 256) + convertUnsigned(data[i++]));
				toCome--;
				if (content != null)
					content.add(new Integer(sid));
//				if (DEBUG_TYPE1C_LOADING) System.out.println(" - 2 got char " + content.size() + ": " + sid);
				int toComeInRange = ((convertUnsigned(data[i++]) * 256) + convertUnsigned(data[i++]));
				for (int c = 0; c < toComeInRange; c++) {
					sid++;
					toCome--;
					if (content != null)
						content.add(new Integer(sid));
//					if (DEBUG_TYPE1C_LOADING) System.out.println(" - 2 got char " + content.size() + ": " + sid);
				}
			}
		}
		return i;
	}
	
	private static int readFontType1cIndex(byte[] data, int start, String name, HashMap dictEntries, HashMap dictOpResolver, boolean isTopDict, ArrayList content) {
		int i = start;
		System.out.println("Doing " + name + " index:");
		int count = (256 * convertUnsigned(data[i++]) + convertUnsigned(data[i++]));
		System.out.println(" - count is " + count);
		if (dictEntries != null) {
			Number[] cnt = {new Integer(count)};
			dictEntries.put("Count", cnt);
		}
		if (count == 0)
			return i;
		int offSize = convertUnsigned(data[i++]);
		System.out.println(" - offset size is " + offSize);
		int[] offsets = new int[count+1];
		for (int c = 0; c <= count; c++) {
			offsets[c] = 0;
			for (int b = 0; b < offSize; b++)
				offsets[c] = ((offsets[c] * 256) + convertUnsigned(data[i++]));
//			System.out.println(" - offset[" + c + "] is " + offsets[c]);
		}
		for (int c = 0; c < count; c++) {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			for (int b = offsets[c]; b < offsets[c+1]; b++)
				baos.write(convertUnsigned(data[i++]));
			if ((dictOpResolver != null) && (dictEntries != null)) {
				ArrayList dictContent = ((content == null) ? null : new ArrayList());
				readFontType1cDict(baos.toByteArray(), 0, (name + "-entry[" + c + "]"), dictEntries, isTopDict, dictOpResolver, dictContent);
				if (content != null)
					content.add((OpType1[]) dictContent.toArray(new OpType1[dictContent.size()]));
			}
			else if (content != null)
				content.add(new String(baos.toByteArray()));
//			else System.out.println(" - entry[" + c + "]: " + new String(baos.toByteArray()));
		}
		return i;
	}
	
	private static int readFontType1cDict(byte[] data, int start, String name, HashMap dictEntries, boolean isTopDict, HashMap opResolver, ArrayList content) {
//		System.out.println("Doing " + name + " dict:");
		int i = start;
		LinkedList stack = new LinkedList();
		int hintCount = 0;
		while (i < data.length)  {
			
			//	read value
			int bs = convertUnsigned(data[i++]);
//			System.out.println(" - first byte is " + bs + " (" + data[i-1] + ")");
			int op = Integer.MIN_VALUE;
			int iVal = Integer.MIN_VALUE;
			double dVal = Double.NEGATIVE_INFINITY;
			
			if ((0 <= bs) && (bs <= 11)) {
				op = bs;
			}
			else if (bs == 12) {
				op = (1000 + convertUnsigned(data[i++]));
			}
			else if ((13 <= bs) && (bs <= 18)) {
				op = bs;
			}
			else if (bs == 19) {
				op = bs;
			}
			else if (bs == 20) {
				op = bs;
			}
			else if ((21 <= bs) && (bs <= 27)) {
				op = bs;
			}
			else if (bs == 28) {
				iVal = 0;
				for (int b = 0; b < 2; b++) {
					iVal <<= 8;
					iVal += convertUnsigned(data[i++]);
				}
				if (iVal > 32768)
					iVal -= 65536;
			}
			else if (bs == 29) {
				iVal = 0;
				for (int b = 0; b < 4; b++) {
					iVal <<= 8;
					iVal += convertUnsigned(data[i++]);
				}
			}
			else if (isTopDict && (bs == 30)) {
				StringBuffer val = new StringBuffer();
				while (true) {
					int b = convertUnsigned(data[i++]);
					int hq = (b & 240) >>> 4;
					int lq = (b & 15);
					
					if (hq < 10)
						val.append("" + hq);
					else if (hq == 10)
						val.append(".");
					else if (hq == 11)
						val.append("E");
					else if (hq == 12)
						val.append("E-");
					else if (hq == 13)
						val.append("");
					else if (hq == 14)
						val.append("-");
					else if (hq == 15)
						break;
					
					if (lq < 10)
						val.append("" + lq);
					else if (lq == 10)
						val.append(".");
					else if (lq == 11)
						val.append("E");
					else if (lq == 12)
						val.append("E-");
					else if (lq == 13)
						val.append("");
					else if (lq == 14)
						val.append("-");
					else if (lq == 15)
						break;
				}
				dVal = Double.parseDouble(val.toString());
			}
			else if (bs == 30) {
				op = bs;
			}
			else if (bs == 31) {
				op = bs;
			}
			else if ((32 <= bs) && (bs <= 246)) {
				iVal = (bs - 139);
			}
			else if ((247 <= bs) && (bs <= 250)) {
				int b1 = bs;
				int b2 = convertUnsigned(data[i++]);
				iVal = ((b1 - 247) * 256) + b2 + 108;
			}
			else if ((251 <= bs) && (bs <= 254)) {
				int b1 = bs;
				int b2 = convertUnsigned(data[i++]);
				iVal = -((b1 - 251) * 256) - b2 - 108;
			}
			else if (bs == 255) {
				int ib1 = convertUnsigned(data[i++]);
				int ib2 = convertUnsigned(data[i++]);
				int iv = ((ib1 * 256) + ib2);
				int fb1 = convertUnsigned(data[i++]);
				int fb2 = convertUnsigned(data[i++]);
				int fv = ((fb1 * 256) + fb2);
				dVal = ((iv << 16) + fv);
				dVal /= 65536;
			}
			
			if (op != Integer.MIN_VALUE) {
				
				//	catch hint and mask operators, as the latter take a few _subsequent_ bytes depending on the number of arguments existing for the former
				if ((content != null) && (opResolver == glyphProgOpResolver)) {
					
					//	hintmask & cntrmask
					if ((op == 19) || (op == 20)) {
						
						//	if last op is hstemhm and we have something on the stack, it's an implicit vstemhm
						if ((content.size() != 0) && ((((OpType1) content.get(content.size()-1)).op == 1) || (((OpType1) content.get(content.size()-1)).op == 18))) {
							content.add(new OpType1(23, null, ((Number[]) stack.toArray(new Number[stack.size()]))));
//							System.out.println(" --> got " + (stack.size() / 2) + " new hints from " + stack.size() + " args, op is " + op);
							hintCount += (stack.size() / 2);
							stack.clear();
						}
						
						//	read mask bytes
						int h = 0;
						int hintmask = 0;
						while (h < ((hintCount + 7) / 8)) {
							hintmask <<= 8;
							hintmask += convertUnsigned(data[i++]);
							h++;
//							break;
						}
						stack.addLast(new Integer(hintmask));
//						System.out.println("Skipped " + h + " hint mask bytes: " + hintmask + " (" + hintCount + " hints)");
					}
					
					//	hstem, vstem, hstemhm & vstemhm (hints are number pairs !!)
					else if ((op == 1) || (op == 3) || (op == 18) || (op == 23)) {
//						System.out.println(" --> got " + (stack.size() / 2) + " new hints from " + stack.size() + " args, op is " + op);
						hintCount += (stack.size() / 2);
					}
				}
				
				String opStr = ((String) opResolver.get(new Integer(op)));
//				System.out.print(" --> read operator " + op + " (" + opStr + ")");
				if (opStr != null)
					dictEntries.put(opStr, ((Number[]) stack.toArray(new Number[stack.size()])));
				if (content != null)
					content.add(new OpType1(op, opStr, ((Number[]) stack.toArray(new Number[stack.size()]))));
//				while (stack.size() != 0)
//					System.out.print(" " + ((Number) stack.removeFirst()));
				stack.clear();
//				System.out.println();
			}
			else if (iVal != Integer.MIN_VALUE) {
//				System.out.println(" --> read int " + iVal);
				stack.addLast(new Integer(iVal));
			}
			else if (dVal != Double.NEGATIVE_INFINITY) {
//				System.out.println(" --> read double " + dVal);
				stack.addLast(new Double(dVal));
			}
		}
		
		return i;
	}
	
	private static class OpType1 {
		int op;
//		String name;
		Number[] args;
		OpType1(int op, String name, Number[] args) {
			this.op = op;
//			this.name = name;
			this.args = args;
		}
	}
	
	private static HashMap type1cTopDictOpResolver = new HashMap();
	static {
		type1cTopDictOpResolver.put(new Integer(0), "Version");
		type1cTopDictOpResolver.put(new Integer(1), "Notice");
		type1cTopDictOpResolver.put(new Integer(2), "FullName");
		type1cTopDictOpResolver.put(new Integer(3), "FamilyName");
		type1cTopDictOpResolver.put(new Integer(4), "Weight");
		type1cTopDictOpResolver.put(new Integer(5), "FontBBox");
		type1cTopDictOpResolver.put(new Integer(13), "UniqueID");
		type1cTopDictOpResolver.put(new Integer(14), "XUID");
		type1cTopDictOpResolver.put(new Integer(15), "Charset");
		type1cTopDictOpResolver.put(new Integer(16), "Encoding");
		type1cTopDictOpResolver.put(new Integer(17), "CharStrings");
		type1cTopDictOpResolver.put(new Integer(18), "Private");
		
		type1cTopDictOpResolver.put(new Integer(1000), "Copyright");
		type1cTopDictOpResolver.put(new Integer(1001), "IsFixedPitch");
		type1cTopDictOpResolver.put(new Integer(1002), "ItalicAngle");
		type1cTopDictOpResolver.put(new Integer(1003), "UnderlinePosition");
		type1cTopDictOpResolver.put(new Integer(1004), "UnderlineThickness");
		type1cTopDictOpResolver.put(new Integer(1005), "PaintType");
		type1cTopDictOpResolver.put(new Integer(1006), "CharstringType");
		type1cTopDictOpResolver.put(new Integer(1007), "FontMatrix");
		type1cTopDictOpResolver.put(new Integer(1008), "StrokeWidth");
		type1cTopDictOpResolver.put(new Integer(1020), "SyntheticBase");
		type1cTopDictOpResolver.put(new Integer(1021), "PostScript");
		type1cTopDictOpResolver.put(new Integer(1022), "BaseFontName");
		type1cTopDictOpResolver.put(new Integer(1023), "BaseFondBlend");
		
		type1cTopDictOpResolver.put(new Integer(1030), "ROS");
		type1cTopDictOpResolver.put(new Integer(1031), "CIDFontVersion");
		type1cTopDictOpResolver.put(new Integer(1032), "CIDFontRevision");
		type1cTopDictOpResolver.put(new Integer(1033), "CIDFontType");
		type1cTopDictOpResolver.put(new Integer(1034), "CIDCount");
		type1cTopDictOpResolver.put(new Integer(1035), "UIDBase");
		type1cTopDictOpResolver.put(new Integer(1036), "FDArray");
		type1cTopDictOpResolver.put(new Integer(1037), "FDSelect");
		type1cTopDictOpResolver.put(new Integer(1038), "FontName");
	}
	
	/*
ROS12 30//SID SID number–, Registry Ordering Supplement
CIDFontVersion12 31//number0
CIDFontRevision12 32//number0
CIDFontType12 33//number0
CIDCount12 34//number8720
UIDBase12 35//number–
FDArray12 36//number–, Font DICT (FD) INDEX offset (0)
FDSelect12 37//number–, FDSelect offset (0)
FontName12 38//SID–, FD FontName
	 */
	
	private static HashMap glyphProgOpResolver = new HashMap();
	static {
		glyphProgOpResolver.put(new Integer(1), "hstem");
		
		glyphProgOpResolver.put(new Integer(3), "vstem");
		glyphProgOpResolver.put(new Integer(4), "vmoveto");
		glyphProgOpResolver.put(new Integer(5), "rlineto");
		glyphProgOpResolver.put(new Integer(6), "hlineto");
		glyphProgOpResolver.put(new Integer(7), "vlineto");
		glyphProgOpResolver.put(new Integer(8), "rrcurveto");
		
		glyphProgOpResolver.put(new Integer(10), "callsubr");
		glyphProgOpResolver.put(new Integer(11), "return");
		
		glyphProgOpResolver.put(new Integer(14), "endchar");
		
		glyphProgOpResolver.put(new Integer(18), "hstemhm");
		glyphProgOpResolver.put(new Integer(19), "hintmask");
		glyphProgOpResolver.put(new Integer(20), "cntrmask");
		glyphProgOpResolver.put(new Integer(21), "rmoveto");
		glyphProgOpResolver.put(new Integer(22), "hmoveto");
		glyphProgOpResolver.put(new Integer(23), "vstemhm");
		glyphProgOpResolver.put(new Integer(24), "rcurveline");
		glyphProgOpResolver.put(new Integer(25), "rlinecurve");
		glyphProgOpResolver.put(new Integer(26), "vvcurveto");
		glyphProgOpResolver.put(new Integer(27), "hhcurveto");
		
		glyphProgOpResolver.put(new Integer(29), "callsubr");
		glyphProgOpResolver.put(new Integer(30), "vhcurveto");
		glyphProgOpResolver.put(new Integer(31), "hvcurveto");
	}
	
	private static HashMap privateOpResolver = new HashMap();
	static {
		privateOpResolver.put(new Integer(6), "BlueValues");
		privateOpResolver.put(new Integer(7), "OtherBlues");
		privateOpResolver.put(new Integer(8), "FamilyBlues");
		privateOpResolver.put(new Integer(9), "FamilyOtherBlues");
		privateOpResolver.put(new Integer(1009), "BlueScale");
		privateOpResolver.put(new Integer(1010), "BlueSchift");
		privateOpResolver.put(new Integer(1011), "BlueFuzz");
		privateOpResolver.put(new Integer(10), "StdHW");
		privateOpResolver.put(new Integer(11), "StdVW");
		privateOpResolver.put(new Integer(1012), "StemSnapH");
		privateOpResolver.put(new Integer(1013), "StemSnapV");
		privateOpResolver.put(new Integer(1014), "ForceBold");
		privateOpResolver.put(new Integer(1017), "LanguageGroup");
		privateOpResolver.put(new Integer(1018), "ExpansionFactor");
		privateOpResolver.put(new Integer(1019), "InitialRandomSeed");
		privateOpResolver.put(new Integer(19), "Subrs");
		privateOpResolver.put(new Integer(20), "defaultWidthX");
		privateOpResolver.put(new Integer(21), "nominalWidthX");
	}
	
	private static final boolean DEBUG_TRUE_TYPE_LOADING = true;
	
	static void readFontTrueType(byte[] data, PdfFont pFont, boolean dataFromBaseFont, ProgressMonitor pm) {
//		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("Used chars are "+ pFont.usedChars.toString());
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("Used chars are "+ Arrays.toString(pFont.getUsedCharCodes()));
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("Unicode mapping is "+ pFont.ucMappings.toString());
		
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
		if (!dataFromBaseFont) {
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
//							if (!pFont.usedChars.contains(new Integer(c)) || cidsByGlyphIndex.containsKey(new Integer(stGlyphIndex)))
							if (!pFont.usesCharCode(new Integer(c)) || cidsByGlyphIndex.containsKey(new Integer(stGlyphIndex)))
								continue;
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
//							if (!pFont.usedChars.contains(new Integer(c)))
							if (!pFont.usesCharCode(new Integer(c)))
								continue;
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
					Integer cid = (dataFromBaseFont ? gi : ((Integer) cidsByGlyphIndex.get(gi)));
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
					Integer cid = (dataFromBaseFont ? gi : ((Integer) cidsByGlyphIndex.get(gi)));
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
			otrs[c] = new OpTrackerTrueType();
			pm.setInfo("     - measuring char " + c + " with CID " + glyph.cid + " (" + pFont.getUnicode(glyph.cid) + ")");
			if (glyph instanceof SimpleGlyphDataTrueType)
				runFontTrueTypeOps(((SimpleGlyphDataTrueType) glyph), false, otrs[c], false);
			else runFontTrueTypeOps(((CompoundGlyphDataTrueType) glyph), glyphsByIndex, otrs[c], false);
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println("     --> " + otrs[c].minX + " < X < " + otrs[c].maxX);
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
		
 		//	set up style-aware name based checks
		pm.setInfo("   - decoding characters");
		Font serifFont = getSerifFont();
		Font[] serifFonts = new Font[4];
		Font sansFont = getSansSerifFont();
		Font[] sansFonts = new Font[4];
		for (int s = Font.PLAIN; s <= (Font.BOLD | Font.ITALIC); s++) {
			serifFonts[s] = serifFont.deriveFont(s);
			sansFonts[s] = sansFont.deriveFont(s);
		}
		
		CharImageMatch[][] serifStyleCims = new CharImageMatch[glyphData.size()][4];
		int[] serifCharCounts = {0, 0, 0, 0};
		int[] serifCharCountsNs = {0, 0, 0, 0};
		double[] serifStyleSimMins = {1, 1, 1, 1};
		double[] serifStyleSimMinsNs = {1, 1, 1, 1};
		double[] serifStyleSimSums = {0, 0, 0, 0};
		double[] serifStyleSimSumsNs = {0, 0, 0, 0};
		
		CharImageMatch[][] sansStyleCims = new CharImageMatch[glyphData.size()][4];
		int[] sansCharCounts = {0, 0, 0, 0};
		int[] sansCharCountsNs = {0, 0, 0, 0};
		double[] sansStyleSimMins = {1, 1, 1, 1};
		double[] sansStyleSimMinsNs = {1, 1, 1, 1};
		double[] sansStyleSimSums = {0, 0, 0, 0};
		double[] sansStyleSimSumsNs = {0, 0, 0, 0};
		
		double simMin = 1;
		double simMinNs = 1;
		double simSum = 0;
		double simSumNs = 0;
		int charCount = 0;
		int charCountNs = 0;
		
		CharImage[] charImages = new CharImage[glyphData.size()];
		char[] chars = new char[glyphData.size()];
		Arrays.fill(chars, ((char) 0));
		HashSet unresolvedCIDs = new HashSet();
		HashSet smallCapsCIDs = new HashSet();
		
		HashMap matchCharChache = new HashMap();
		
		//	generate images and match against named char
		ImageDisplayDialog fidd = (DEBUG_TRUE_TYPE_RENDERING ? new ImageDisplayDialog("Font " + pFont.name) : null);
		for (int c = 0; c < glyphData.size(); c++) {
			GlyphDataTrueType glyph = ((GlyphDataTrueType) glyphData.get(c));
			String chn = null;
			String ucCh = pFont.getUnicode(glyph.cid);
			if ((ucCh != null) && (ucCh.length() > 0)) {
				chars[c] = ucCh.charAt(0);
				chn = StringUtils.getCharName(chars[c]);
			}
//			if (!DEBUG_TRUE_TYPE_RENDERING && !pFont.usedChars.contains(new Integer(glyph.cid))) {
			if (!DEBUG_TRUE_TYPE_RENDERING && !pFont.usesCharCode(new Integer(glyph.cid))) {
				pm.setInfo("     - ignoring unused char " + c + " with CID " + glyph.cid + " (" + ucCh + ", " + chn + ")");
				continue;
			}
			if (chn == null)
				unresolvedCIDs.add(new Integer(glyph.cid));
			pm.setInfo("     - decoding char " + c + " with CID " + glyph.cid + " (" + ucCh + ", " + chn + ")");
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println("Decoding char " + c + ", CID " + glyph.cid + " (" + ucCh + ", " + chn + ")");
//			String chs = ("" + chars[c]);
//			int chw = ((otrs[c].rWidth == Integer.MIN_VALUE) ? dWidth : (nWidth + otrs[c].rWidth));
//			int chw = ((otrs[c].rWidth == 0) ? dWidth : (nWidth + otrs[c].rWidth));
//			if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - char width is " + chw);
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - stroke count is " + otrs[c].mCount);
//			if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - " + otrs[c].id + ": " + otrs[c].minX + " < X < " + otrs[c].maxX);
			
			boolean ignoreForMin = ((minIgnoreChars.indexOf(chars[c]) != -1) || (chars[c] != StringUtils.getBaseChar(chars[c])));
			
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
			charImages[c] = new CharImage(ogr.img, Math.round(((float) (maxCapHeight * ogr.img.getHeight())) / (maxCapHeight - maxDescent)));
			if (fidd != null)
				fidd.addImage(ogr.img, (glyph.cid + " (" + glyph.gid + "): " + ucCh));
			
			//	no use going on without a char name
			if (chn == null) {
				unresolvedCIDs.add(new Integer(glyph.cid));
				continue;
			}
			
			//	store char image
			pFont.setCharImage(glyph.cid, chn, ogr.img);
			
			//	measure best match
			float bestSim = -1;
			float bestSimNs = -1;
			char bestSimCh = ((char) 0);
			
			//	try named char match first (render known chars to fill whole image)
			CharMatchResult matchResult = PdfCharDecoder.matchChar(charImages[c], chars[c], true, serifFonts, sansFonts, matchCharChache, true, false);
			float oSimSum = 0;
			int oSimCount = 0;
			for (int s = Font.PLAIN; s <= (Font.BOLD | Font.ITALIC); s++) {
				if (matchResult.serifStyleCims[s] != null) {
					oSimSum += matchResult.serifStyleCims[s].sim;
					oSimCount++;
				}
				if (matchResult.sansStyleCims[s] != null) {
					oSimSum += matchResult.sansStyleCims[s].sim;
					oSimCount++;
				}
			}
			if (DEBUG_TRUE_TYPE_LOADING)
				System.out.println(" - average similarity is " + ((oSimCount == 0) ? 0 : (oSimSum / oSimCount)));
			
			//	NO USE _CHECKING_ UNICODE MAPPING IN CID FONT, AS ALL WE HAVE FOR DECODING CIDs _IS_ THE UNICODE MAPPING
			
			//	evaluate match result
			if (matchResult.rendered) {
				charCount++;
				if ((matchResult.serifStyleCims[Font.PLAIN] != null) || (matchResult.serifStyleCims[Font.BOLD] != null) || (matchResult.sansStyleCims[Font.PLAIN] != null) || (matchResult.sansStyleCims[Font.BOLD] != null) || (skewChars.indexOf(chars[c]) == -1))
					charCountNs++;
				for (int s = Font.PLAIN; s <= (Font.BOLD | Font.ITALIC); s++) {
					serifStyleCims[c][s] = matchResult.serifStyleCims[s];
					if (serifStyleCims[c][s] == null) {
						serifStyleSimMins[s] = 0;
						continue;
					}
					serifCharCounts[s]++;
					serifStyleSimSums[s] += serifStyleCims[c][s].sim;
					if (bestSim < serifStyleCims[c][s].sim) {
						bestSim = serifStyleCims[c][s].sim;
						bestSimCh = serifStyleCims[c][s].match.ch;
					}
					if (!ignoreForMin)
						serifStyleSimMins[s] = Math.min(serifStyleCims[c][s].sim, serifStyleSimMins[s]);
					if (((s & Font.ITALIC) == 0) || (skewChars.indexOf(chars[c]) == -1)) {
						serifCharCountsNs[s]++;
						serifStyleSimSumsNs[s] += serifStyleCims[c][s].sim;
						bestSimNs = Math.max(bestSimNs, serifStyleCims[c][s].sim);
						if (!ignoreForMin)
							serifStyleSimMinsNs[s] = Math.min(serifStyleCims[c][s].sim, serifStyleSimMinsNs[s]);
					}
				}
				for (int s = Font.PLAIN; s <= (Font.BOLD | Font.ITALIC); s++) {
					sansStyleCims[c][s] = matchResult.sansStyleCims[s];
					if (sansStyleCims[c][s] == null) {
						sansStyleSimMins[s] = 0;
						continue;
					}
					sansCharCounts[s]++;
					sansStyleSimSums[s] += sansStyleCims[c][s].sim;
					if (bestSim < sansStyleCims[c][s].sim) {
						bestSim = sansStyleCims[c][s].sim;
						bestSimCh = sansStyleCims[c][s].match.ch;
					}
					if (!ignoreForMin)
						sansStyleSimMins[s] = Math.min(sansStyleCims[c][s].sim, sansStyleSimMins[s]);
					if (((s & Font.ITALIC) == 0) || (skewChars.indexOf(chars[c]) == -1)) {
						sansCharCountsNs[s]++;
						sansStyleSimSumsNs[s] += sansStyleCims[c][s].sim;
						bestSimNs = Math.max(bestSimNs, sansStyleCims[c][s].sim);
						if (!ignoreForMin)
							sansStyleSimMinsNs[s] = Math.min(sansStyleCims[c][s].sim, sansStyleSimMinsNs[s]);
					}
				}
			}
			
			//	we might want to revisit this one
			if (bestSim < 0.5) {
				unresolvedCIDs.add(new Integer(glyph.cid));
				continue;
			}
			
			//	what do we have?
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" --> best similarity is " + bestSim + " / " + bestSimNs + " for " + bestSimCh);
			
			//	remember small-caps match
			if (bestSimCh != chars[c]) {
				smallCapsCIDs.add(new Integer(glyph.cid));
				if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" --> small-caps match");
			}
			
			//	update overall measures
			if (!ignoreForMin) {
				if (bestSim >= 0)
					simMin = Math.min(simMin, bestSim);
				if (bestSimNs >= 0)
					simMinNs = Math.min(simMinNs, bestSimNs);
			}
			if (bestSim >= 0)
				simSum += bestSim;
			if (bestSimNs >= 0)
				simSumNs += bestSimNs;
		}
		checkImplicitSpaces(pFont, charImages, chars, glyphData);
		if (fidd != null) {
			fidd.setLocationRelativeTo(null);
			fidd.setSize(600, 400);
			fidd.setVisible(true);
		}
		
		//	use maximum of all fonts and styles when computing min and average similarity
		pm.setInfo("   - detecting font style");
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - min similarity is " + simMin + " all / " + simMinNs + " non-skewed");
		double sim = ((charCount == 0) ? 0 : (simSum / charCount));
		double simNs = ((charCountNs == 0) ? 0 : (simSumNs / charCountNs));
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - average similarity is " + sim + " (" + charCount + ") all / " + simNs + " (" + charCountNs + ") non-skewed");
		
		//	do we have a match? (be more strict with fewer chars, as fonts with few chars tend to be the oddjobs)
		if (((simMin > 0.5) && (sim > 0.6)) || ((simMin > 0.375) && (sim > 0.7)) || ((simMin > 0.25) && (sim > 0.8))) {
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" ==> match");
			
			//	TODO somehow assess the number or fraction of black pixels in the font being decoded and the comparison fonts, and penalize all too big differences in style detection
			
			//	try to select font style if pool sufficiently large
			int bestStyle = -1;
			boolean serif = true;
			if (Math.max(serifStyleSimSums.length, sansStyleSimSums.length) >= 2) {
				double bestStyleSim = 0;
				for (int s = Font.PLAIN; s <= (Font.BOLD | Font.ITALIC); s++) {
					if (DEBUG_TRUE_TYPE_LOADING) {
						System.out.println(" - checking style " + s);
						System.out.println("   - min similarity is " + serifStyleSimMins[s] + "/" + sansStyleSimMins[s] + " all / " + serifStyleSimMinsNs[s] + "/" + sansStyleSimMinsNs[s] + " non-skewed");
					}
					double serifStyleSim = ((serifCharCounts[s] == 0) ? 0 : (serifStyleSimSums[s] / serifCharCounts[s]));
					double serifStyleSimNs = ((serifCharCountsNs[s] == 0) ? 0 : (serifStyleSimSumsNs[s] / serifCharCountsNs[s]));
					double sansStyleSim = ((sansCharCounts[s] == 0) ? 0 : (sansStyleSimSums[s] / sansCharCounts[s]));
					double sansStyleSimNs = ((sansCharCountsNs[s] == 0) ? 0 : (sansStyleSimSumsNs[s] / sansCharCountsNs[s]));
					if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - average similarity is " + serifStyleSim + "/" + sansStyleSim + " all / " + serifStyleSimNs + "/" + sansStyleSimNs + " non-skewed");
					if ((((s & Font.ITALIC) == 0) ? Math.max(serifStyleSim, sansStyleSim) : Math.max(serifStyleSimNs, sansStyleSimNs)) > bestStyleSim) {
						if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" ==> new best match style");
						bestStyleSim = (((s & Font.ITALIC) == 0) ? Math.max(serifStyleSim, sansStyleSim) : Math.max(serifStyleSimNs, sansStyleSimNs));
						bestStyle = s;
						serif = (((s & Font.ITALIC) == 0) ? (serifStyleSim >= sansStyleSim) : (serifStyleSimNs >= sansStyleSimNs));
						if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" ==> serif is " + serif);
					}
				}
			}
			
			//	style not found, use plain
			if (bestStyle == -1)
				bestStyle = 0;
			
			//	set base font according to style
			pFont.bold = ((Font.BOLD & bestStyle) != 0);
			pFont.italics = ((Font.ITALIC & bestStyle) != 0);
			pFont.serif = serif;
			System.out.println(" ==> font decoded");
			if ((pFont.baseFont == null) || (pFont.bold != pFont.baseFont.bold) || (pFont.italics != pFont.baseFont.italic)) {
				String bfn = "Times-";
				if (pFont.bold && pFont.italics)
					bfn += "BoldItalic";
				else if (pFont.bold)
					bfn += "Bold";
				else if (pFont.italics)
					bfn += "Italic";
				else bfn += "Roman";
				pFont.setBaseFont(bfn, false);
			}
			
			//	check for descent
			pFont.hasDescent = ((maxDescent < -150) || (pFont.descent < -0.150));
			
			//	collect all chars, and get some stats
			HashSet pFontChars = new HashSet();
			int xHeightSum = 0;
			int xHeightCount = 0;
			int capHeightSum = 0;
			int capHeightCount = 0;
			for (int c = 0; c < glyphData.size(); c++) {
				if (charImages[c] == null)
					continue;
				pFontChars.add(new Character(chars[c]));
				if ("aemnru".indexOf(StringUtils.getBaseChar(chars[c])) != -1) {
					xHeightSum += charImages[c].box.getHeight();
					xHeightCount++;
				}
				else if ("ABDEFGHIKLMNPRTUYbdfhkl0123456789".indexOf(StringUtils.getBaseChar(chars[c])) != -1) {
					capHeightSum += charImages[c].box.getHeight();
					capHeightCount++;
				}
			}
			int capHeight = ((capHeightCount == 0) ? 0 : (capHeightSum / capHeightCount));
			int xHeight = ((xHeightCount == 0) ? 0 : (xHeightSum / xHeightCount));
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - average cap-height is " + capHeight + ", average x-height is " + xHeight);
			
			//	rectify small-caps TODO use char usage stats
			for (int c = 0; c < glyphData.size(); c++) {
				if (charImages[c] == null)
					continue;
				GlyphDataTrueType glyph = ((GlyphDataTrueType) glyphData.get(c));
				if (!smallCapsCIDs.contains(new Integer(glyph.cid)))
					continue;
				if (pFontChars.contains(new Character(Character.toUpperCase(chars[c]))))
					continue;
				if ((xHeight < capHeight) && (Math.abs(charImages[c].box.getHeight() - xHeight) < Math.abs(charImages[c].box.getHeight() - capHeight)))
					continue;
				pFont.mapUnicode(new Integer(glyph.cid), ("" + Character.toUpperCase(chars[c])));
				pFont.setCharImage(glyph.cid, StringUtils.getCharName(Character.toUpperCase(chars[c])), charImages[c].img);
				if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" ==> small-caps corrected " + glyph.cid + " to " + Character.toUpperCase(chars[c]));
			}
			
			//	we're done here
			if (unresolvedCIDs.isEmpty())
				return;
		}
		
		//	reset chars with known names to mark them for re-evaluation (required with fonts that use arbitrary char names)
		else {
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" ==> mis-match");
			Arrays.fill(chars, ((char) 0));
			for (int c = 0; c < glyphData.size(); c++) {
				GlyphDataTrueType glyph = ((GlyphDataTrueType) glyphData.get(c));
				unresolvedCIDs.add(new Integer(glyph.cid));
			}
		}
		
		//	TODO use char usage stats
		
		//	cache character images to speed up matters
		pm.setInfo("   - OCR decoding remaining characters");
		final float[] cacheHitRate = {0};
		HashMap cache = new HashMap() {
			int lookups = 0;
			int hits = 0;
			public Object get(Object key) {
				this.lookups ++;
				Object value = super.get(key);
				if (value != null)
					this.hits++;
				cacheHitRate[0] = (((float) this.hits) / this.lookups);
				return value;
			}
		};
		
		//	get matches for remaining characters, and measure xHeight and capHeight
		CharImageMatch[] bestCims = new CharImageMatch[glyphData.size()];
		int xHeightSum = 0;
		int xHeightCount = 0;
		int capHeightSum = 0;
		int capHeightCount = 0;
		for (int c = 0; c < glyphData.size(); c++) {
			if (charImages[c] == null)
				continue;
			
			//	get basic data
			GlyphDataTrueType glyph = ((GlyphDataTrueType) glyphData.get(c));
			String chn = null;
			String ucCh = pFont.getUnicode(glyph.cid);
			if ((ucCh != null) && (ucCh.length() > 0)) {
				chars[c] = ucCh.charAt(0);
				chn = StringUtils.getCharName(chars[c]);
			}
			pm.setInfo("     - OCR decoding char " + c + " with CID " + glyph.cid + " (" + ucCh + ", " + chn + ")");
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println("Decoding char " + c + " with CID " + glyph.cid + " (" + ucCh + ", " + chn + ")");
			
			//	perform match
			bestCims[c] = getCharForImage(charImages[c], serifFonts, sansFonts, cache, false);
			if (bestCims[c] == null)
				continue;
			
			//	take measurements
			if ("aemnru".indexOf(StringUtils.getBaseChar(bestCims[c].match.ch)) != -1) {
				xHeightSum += charImages[c].box.getHeight();
				xHeightCount++;
			}
			else if ("ABDEFGHIKLMNPRTUYbdfhkl0123456789".indexOf(StringUtils.getBaseChar(bestCims[c].match.ch)) != -1) {
				capHeightSum += charImages[c].box.getHeight();
				capHeightCount++;
			}
		}
		int capHeight = ((capHeightCount == 0) ? 0 : (capHeightSum / capHeightCount));
		int xHeight = ((xHeightCount == 0) ? 0 : (xHeightSum / xHeightCount));
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - average cap-height is " + capHeight + ", average x-height is " + xHeight);
		
		//	decode remaining characters
		for (int c = 0; c < glyphData.size(); c++) {
			
			//	don't try to match space or unused chars
			if (charImages[c] == null)
				continue;
			
			//	we don't have a match for this one
			if (bestCims[c] == null)
				continue;
			
			//	get basic data
			GlyphDataTrueType glyph = ((GlyphDataTrueType) glyphData.get(c));
			if (!unresolvedCIDs.contains(new Integer(glyph.cid)))
				continue;
			String chn = null;
			String ucCh = pFont.getUnicode(glyph.cid);
			if ((ucCh != null) && (ucCh.length() > 0)) {
				chars[c] = ucCh.charAt(0);
				chn = StringUtils.getCharName(chars[c]);
			}
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println("Decoding char " + c + " with CID " + glyph.cid + " (" + ucCh + ", " + chn + ")");
			
			//	catch lower case COSVWXZ mistaken for upper case
			boolean isLowerCase = (xHeight < capHeight) && (Math.abs(charImages[c].box.getHeight() - xHeight) < Math.abs(charImages[c].box.getHeight() - capHeight));
			
			//	do we have a reliable match?
			if (bestCims[c].sim > 0.8) {
				if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" ==> char decoded (1) to '" + bestCims[c].match.ch + "' (" + StringUtils.getCharName(bestCims[c].match.ch) + ", '" + StringUtils.getNormalForm(bestCims[c].match.ch) + "') at " + bestCims[c].sim + ", cache hit rate at " + cacheHitRate[0]);
//				Character bsCh = new Character(bestCims[c].match.ch);
				Character bsCh;
				if (isLowerCase && ("COSVWXZ".indexOf(bestCims[c].match.ch) != -1))
					bsCh = new Character(Character.toLowerCase(bestCims[c].match.ch));
				else bsCh = new Character(bestCims[c].match.ch);
				if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" ==> mapped (1) " + glyph.cid + " to " + bsCh);
				
				//	correct char mapping specified in differences array
				pFont.mapDifference(new Integer(glyph.cid), bsCh, null);
				pFont.mapUnicode(new Integer(glyph.cid), StringUtils.getNormalForm(bsCh.charValue()));
				
				//	store char image
				chn = StringUtils.getCharName(bsCh.charValue());
				pFont.setCharImage(glyph.cid, chn, charImages[c].img);
				
				//	no need to hassle about this char any more
				continue;
			}
			
			//	use whatever we got
			else {
				if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" ==> char decoded (2) to '" + bestCims[c].match.ch + "' (" + StringUtils.getCharName(bestCims[c].match.ch) + ", '" + StringUtils.getNormalForm(bestCims[c].match.ch) + "') at " + bestCims[c].sim + ", cache hit rate at " + cacheHitRate[0]);
//				if (DEBUG_TRUE_TYPE_LOADING && (bestSim.sim < 0.7))
//					displayCharMatch(imgs[c], bestSim, "Best Match");
//				Character bsCh = new Character(bestCims[c].match.ch);
				Character bsCh;
				if (isLowerCase && ("COSVWXZ".indexOf(bestCims[c].match.ch) != -1))
					bsCh = new Character(Character.toLowerCase(bestCims[c].match.ch));
				else bsCh = new Character(bestCims[c].match.ch);
				if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" ==> mapped (2) " + glyph.cid + " to " + bsCh);
				
				//	correct char mapping specified in differences array
				pFont.mapDifference(new Integer(glyph.cid), bsCh, null);
				pFont.mapUnicode(new Integer(glyph.cid), StringUtils.getNormalForm(bsCh.charValue()));
				
				//	store char image
				chn = StringUtils.getCharName(bsCh.charValue());
				pFont.setCharImage(glyph.cid, chn, charImages[c].img);
				
				//	check for descent
				pFont.hasDescent = ((maxDescent < -150) || (pFont.descent < -0.150));
			}
		}
	}
	
	private static void checkImplicitSpaces(PdfFont pFont, CharImage[] charImages, char[] chars, ArrayList glyphData) {
		
		//	rectify small-caps
		for (int c = 0; c < glyphData.size(); c++) {
			if (charImages[c] == null)
				continue;
			GlyphDataTrueType glyph = ((GlyphDataTrueType) glyphData.get(c));
		}
		
		//	check average word length (will be well below 2 for implicit space fonts)
		float avgFontWordLength = pFont.getAverageWordLength();
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - average font word length is " + avgFontWordLength);
		if (avgFontWordLength > 2) {
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" ==> based on word length, char widths appear to make sense");
			return;
		}
		
		//	measure maximum char height and relation to nominal font box
		int maxCharHeight = 0;
		for (int c = 0; c < charImages.length; c++) {
			if (charImages[c] != null)
				maxCharHeight = Math.max(maxCharHeight, charImages[c].box.getHeight());
		}
		float charHeightRel = (maxCharHeight / (pFont.ascent - pFont.descent));
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - maximum height is " + maxCharHeight + " for " + (pFont.ascent - pFont.descent) + ", relation is " + charHeightRel);
		
		//	compare nominal to measured char widths
		float ncCharWidthRelSum = 0;
		int ncCharWidthRelCount = 0;
		for (int c = 0; c < glyphData.size(); c++) {
			if (charImages[c] == null)
				continue;
			
			//	get basic data
			GlyphDataTrueType glyph = ((GlyphDataTrueType) glyphData.get(c));
			
			//	get nominal width and compare to actual width
			float nCharWidth = pFont.getCharWidth(new Character((char) glyph.cid));
			float nCharWidthRel = ((charImages[c].box.getWidth() * 1000) / nCharWidth);
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - nominal width of char " + c + " (CID " + glyph.cid + ", " + StringUtils.getCharName(chars[c]) + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ") is " + nCharWidth);
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - actual width is " + charImages[c].box.getWidth() + ", relation is " + nCharWidthRel);
			float cCharWidth = ((nCharWidth * nCharWidthRel) / charHeightRel);
			float ncCharWidthRel = (nCharWidth / cCharWidth);
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" --> computed width is " + cCharWidth + ", relation is " + ncCharWidthRel);
			ncCharWidthRelSum += ncCharWidthRel;
			ncCharWidthRelCount++;
		}
		float avgNcCharWidthRel = ((ncCharWidthRelCount == 0) ? 0 : (ncCharWidthRelSum / ncCharWidthRelCount));
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" --> average nominal char width to computed char width relation is " + avgNcCharWidthRel + " from " + ncCharWidthRelCount + " chars");
		
		//	this font seems to indicate sincere char widths
		//	TODO verify threshold, might be safer off with 2
		if (avgNcCharWidthRel < 1.5) {
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" ==> char widths appear to make sense");
			return;
		}
		
		//	nominal char width way larger than measured char width, we have implicit spaces
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" ==> char widths appear to imply spaces");
		pFont.hasImplicitSpaces = true;
		
		//	add measured char widths
		for (int c = 0; c < glyphData.size(); c++) {
			if (charImages[c] == null)
				continue;
			
			//	get basic data
			GlyphDataTrueType glyph = ((GlyphDataTrueType) glyphData.get(c));
			
			//	store actual width
			float nCharWidth = pFont.getCharWidth(new Character((char) glyph.cid));
			float nCharWidthRel = ((charImages[c].box.getWidth() * 1000) / nCharWidth);
			float cCharWidth = ((nCharWidth * nCharWidthRel) / charHeightRel);
			pFont.setMeasuredCharWidth(new Integer(glyph.cid), cCharWidth);
		}
	}

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
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - arguments are " + arg1 + " and " + arg2);
			
			int[] opts;
			if ((flags & 128) != 0) {
				opts = new int[4];
				opts[0] = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+2));
				glyfByteOffset += 2;
				opts[1] = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+2));
				glyfByteOffset += 2;
				opts[2] = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+2));
				glyfByteOffset += 2;
				opts[3] = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+2));
				glyfByteOffset += 2;
				if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - options are " + Arrays.toString(opts));
			}
			else if ((flags & 64) != 0) {
				opts = new int[2];
				opts[0] = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+2));
				glyfByteOffset += 2;
				opts[1] = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+2));
				glyfByteOffset += 2;
				if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - options are " + Arrays.toString(opts));
			}
			else if ((flags & 8) != 0) {
				opts = new int[1];
				opts[0] = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+2));
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
	
	private static final boolean DEBUG_TRUE_TYPE_RENDERING = false;
	private static final int DEBUG_TRUE_TYPE_TARGET_CID = -1;
	
	private static void runFontTrueTypeOps(CompoundGlyphDataTrueType cGlyph, HashMap glyphsByCid, OpReceiverTrueType opr, boolean show) {
		ImageDisplayDialog idd = null;
		
		//	render components
		for (int c = 0; c < cGlyph.components.length; c++) {
			GlyphDataTrueType glyph = ((GlyphDataTrueType) glyphsByCid.get(new Integer(cGlyph.components[c].glyphIndex)));
			
			//	TODO adjust transform if options are present (once we have an example)
			
			//	adjust position
			if ((cGlyph.components[c].flags & 2) == 0) {
				/* If [flag 1] not set, args are points:
			     * - 1st arg contains the index of matching point in compound being constructed
			     * - 2nd arg contains index of matching point in component
			     * ==> might have to collect points in this case */
			}
			
			//	If [flag 1] set, the arguments are xy values;
			else opr.moveToAbs(cGlyph.components[c].arg1, cGlyph.components[c].arg2);
			
			//	do actual rendering
			if (glyph instanceof SimpleGlyphDataTrueType)
				runFontTrueTypeOps(((SimpleGlyphDataTrueType) glyph), true, opr, show);
			else runFontTrueTypeOps(((CompoundGlyphDataTrueType) glyph), glyphsByCid, opr, show);
			
			//	TODO reset transform if options are present
			
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
		if (opr instanceof OpTrackerTrueType)
			return;
		
		//	fill outline
		fillGlyphOutline(((OpGraphicsTrueType) opr).img, ((OpGraphicsTrueType) opr).scale, show);
		
		//	scale down image
		int maxHeight = 100;
		((OpGraphicsTrueType) opr).setImage(scaleImage(((OpGraphicsTrueType) opr).img, maxHeight));
	}
	
	private static void runFontTrueTypeOps(SimpleGlyphDataTrueType sGlyph, boolean isComponent, OpReceiverTrueType opr, boolean show) {
		
		//	run points
		int pointIndex = 0;
		for (int c = 0; c < sGlyph.contourEnds.length; c++) {
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
				if (sGlyph.contourEnds[c] == (pointIndex-1)) {
					opr.closePath();
					break;
				}
			}
		}
		
		//	only tracking, we're done here
		if ((opr instanceof OpTrackerTrueType) || isComponent)
			return;
		
		//	fill outline
		fillGlyphOutline(((OpGraphicsTrueType) opr).img, ((OpGraphicsTrueType) opr).scale, show);
		
		//	scale down image
		int maxHeight = 100;
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
		abstract void moveTo(int dx, int dy);
		abstract void moveToAbs(int x, int y);
		abstract void lineTo(int dx, int dy);
		abstract void curveTo(int dx1, int dy1, int dx2, int dy2);
		abstract void closePath();
		abstract void closePath(int dx, int dy);
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
		void moveTo(int dx, int dy) {
			if (this.mCount != 0)
				this.isMultiPath = true;
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
			this.minPaintX = Math.min(this.minPaintX, this.x);
			this.minPaintY = Math.min(this.minPaintY, this.y);
			this.x += dx;
			this.minX = Math.min(this.minX, this.x);
			this.maxX = Math.max(this.maxX, this.x);
			this.y += dy;
			this.minY = Math.min(this.minY, this.y);
			this.maxY = Math.max(this.maxY, this.y);
			this.minPaintX = Math.min(this.minPaintX, this.x);
			this.minPaintY = Math.min(this.minPaintY, this.y);
			this.mCount++;
//			System.out.println("Line " + this.id + " to " + dx + "/" + dy + ":");
//			System.out.println(" " + this.minX + " < X < " + this.maxX);
//			System.out.println(" " + this.minY + " < Y < " + this.maxY);
		}
		void curveTo(int dx1, int dy1, int dx2, int dy2) {
			this.lineTo(dx1, dy1);
			this.lineTo(dx2, dy2);
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
	
	private static HashMap sidResolver = new HashMap();
	static {
		sidResolver.put(new Integer(0), ".notdef");
		sidResolver.put(new Integer(1), "space");
		sidResolver.put(new Integer(2), "exclam");
		sidResolver.put(new Integer(3), "quotedbl");
		sidResolver.put(new Integer(4), "numbersign");
		sidResolver.put(new Integer(5), "dollar");
		sidResolver.put(new Integer(6), "percent");
		sidResolver.put(new Integer(7), "ampersand");
		sidResolver.put(new Integer(8), "quoteright");
		sidResolver.put(new Integer(9), "parenleft");
		sidResolver.put(new Integer(10), "parenright");
		sidResolver.put(new Integer(11), "asterisk");
		sidResolver.put(new Integer(12), "plus");
		sidResolver.put(new Integer(13), "comma");
		sidResolver.put(new Integer(14), "hyphen");
		sidResolver.put(new Integer(15), "period");
		sidResolver.put(new Integer(16), "slash");
		sidResolver.put(new Integer(17), "zero");
		sidResolver.put(new Integer(18), "one");
		sidResolver.put(new Integer(19), "two");
		sidResolver.put(new Integer(20), "three");
		sidResolver.put(new Integer(21), "four");
		sidResolver.put(new Integer(22), "five");
		sidResolver.put(new Integer(23), "six");
		sidResolver.put(new Integer(24), "seven");
		sidResolver.put(new Integer(25), "eight");
		sidResolver.put(new Integer(26), "nine");
		sidResolver.put(new Integer(27), "colon");
		sidResolver.put(new Integer(28), "semicolon");
		sidResolver.put(new Integer(29), "less");
		sidResolver.put(new Integer(30), "equal");
		sidResolver.put(new Integer(31), "greater");
		sidResolver.put(new Integer(32), "question");
		sidResolver.put(new Integer(33), "at");
		sidResolver.put(new Integer(34), "A");
		sidResolver.put(new Integer(35), "B");
		sidResolver.put(new Integer(36), "C");
		sidResolver.put(new Integer(37), "D");
		sidResolver.put(new Integer(38), "E");
		sidResolver.put(new Integer(39), "F");
		sidResolver.put(new Integer(40), "G");
		sidResolver.put(new Integer(41), "H");
		sidResolver.put(new Integer(42), "I");
		sidResolver.put(new Integer(43), "J");
		sidResolver.put(new Integer(44), "K");
		sidResolver.put(new Integer(45), "L");
		sidResolver.put(new Integer(46), "M");
		sidResolver.put(new Integer(47), "N");
		sidResolver.put(new Integer(48), "O");
		sidResolver.put(new Integer(49), "P");
		sidResolver.put(new Integer(50), "Q");
		sidResolver.put(new Integer(51), "R");
		sidResolver.put(new Integer(52), "S");
		sidResolver.put(new Integer(53), "T");
		sidResolver.put(new Integer(54), "U");
		sidResolver.put(new Integer(55), "V");
		sidResolver.put(new Integer(56), "W");
		sidResolver.put(new Integer(57), "X");
		sidResolver.put(new Integer(58), "Y");
		sidResolver.put(new Integer(59), "Z");
		sidResolver.put(new Integer(60), "bracketleft");
		sidResolver.put(new Integer(61), "backslash");
		sidResolver.put(new Integer(62), "bracketright");
		sidResolver.put(new Integer(63), "asciicircum");
		sidResolver.put(new Integer(64), "underscore");
		sidResolver.put(new Integer(65), "quoteleft");
		sidResolver.put(new Integer(66), "a");
		sidResolver.put(new Integer(67), "b");
		sidResolver.put(new Integer(68), "c");
		sidResolver.put(new Integer(69), "d");
		sidResolver.put(new Integer(70), "e");
		sidResolver.put(new Integer(71), "f");
		sidResolver.put(new Integer(72), "g");
		sidResolver.put(new Integer(73), "h");
		sidResolver.put(new Integer(74), "i");
		sidResolver.put(new Integer(75), "j");
		sidResolver.put(new Integer(76), "k");
		sidResolver.put(new Integer(77), "l");
		sidResolver.put(new Integer(78), "m");
		sidResolver.put(new Integer(79), "n");
		sidResolver.put(new Integer(80), "o");
		sidResolver.put(new Integer(81), "p");
		sidResolver.put(new Integer(82), "q");
		sidResolver.put(new Integer(83), "r");
		sidResolver.put(new Integer(84), "s");
		sidResolver.put(new Integer(85), "t");
		sidResolver.put(new Integer(86), "u");
		sidResolver.put(new Integer(87), "v");
		sidResolver.put(new Integer(88), "w");
		sidResolver.put(new Integer(89), "x");
		sidResolver.put(new Integer(90), "y");
		sidResolver.put(new Integer(91), "z");
		sidResolver.put(new Integer(92), "braceleft");
		sidResolver.put(new Integer(93), "bar");
		sidResolver.put(new Integer(94), "braceright");
		sidResolver.put(new Integer(95), "asciitilde");
		sidResolver.put(new Integer(96), "exclamdown");
		sidResolver.put(new Integer(97), "cent");
		sidResolver.put(new Integer(98), "sterling");
		sidResolver.put(new Integer(99), "fraction");
		sidResolver.put(new Integer(100), "yen");
		sidResolver.put(new Integer(101), "florin");
		sidResolver.put(new Integer(102), "section");
		sidResolver.put(new Integer(103), "currency");
		sidResolver.put(new Integer(104), "quotesingle");
		sidResolver.put(new Integer(105), "quotedblleft");
		sidResolver.put(new Integer(106), "guillemotleft");
		sidResolver.put(new Integer(107), "guilsinglleft");
		sidResolver.put(new Integer(108), "guilsinglright");
		sidResolver.put(new Integer(109), "fi");
		sidResolver.put(new Integer(110), "fl");
		sidResolver.put(new Integer(111), "endash");
		sidResolver.put(new Integer(112), "dagger");
		sidResolver.put(new Integer(113), "daggerdbl");
		sidResolver.put(new Integer(114), "periodcentered");
		sidResolver.put(new Integer(115), "paragraph");
		sidResolver.put(new Integer(116), "bullet");
		sidResolver.put(new Integer(117), "quotesinglbase");
		sidResolver.put(new Integer(118), "quotedblbase");
		sidResolver.put(new Integer(119), "quotedblright");
		sidResolver.put(new Integer(120), "guillemotright");
		sidResolver.put(new Integer(121), "ellipsis");
		sidResolver.put(new Integer(122), "perthousand");
		sidResolver.put(new Integer(123), "questiondown");
		sidResolver.put(new Integer(124), "grave");
		sidResolver.put(new Integer(125), "acute");
		sidResolver.put(new Integer(126), "circumflex");
		sidResolver.put(new Integer(127), "tilde");
		sidResolver.put(new Integer(128), "macron");
		sidResolver.put(new Integer(129), "breve");
		sidResolver.put(new Integer(130), "dotaccent");
		sidResolver.put(new Integer(131), "dieresis");
		sidResolver.put(new Integer(132), "ring");
		sidResolver.put(new Integer(133), "cedilla");
		sidResolver.put(new Integer(134), "hungarumlaut");
		sidResolver.put(new Integer(135), "ogonek");
		sidResolver.put(new Integer(136), "caron");
		sidResolver.put(new Integer(137), "emdash");
		sidResolver.put(new Integer(138), "AE");
		sidResolver.put(new Integer(139), "ordfeminine");
		sidResolver.put(new Integer(140), "Lslash");
		sidResolver.put(new Integer(141), "Oslash");
		sidResolver.put(new Integer(142), "OE");
		sidResolver.put(new Integer(143), "ordmasculine");
		sidResolver.put(new Integer(144), "ae");
		sidResolver.put(new Integer(145), "dotlessi");
		sidResolver.put(new Integer(146), "lslash");
		sidResolver.put(new Integer(147), "oslash");
		sidResolver.put(new Integer(148), "oe");
		sidResolver.put(new Integer(149), "germandbls");
		sidResolver.put(new Integer(150), "onesuperior");
		sidResolver.put(new Integer(151), "logicalnot");
		sidResolver.put(new Integer(152), "mu");
		sidResolver.put(new Integer(153), "trademark");
		sidResolver.put(new Integer(154), "Eth");
		sidResolver.put(new Integer(155), "onehalf");
		sidResolver.put(new Integer(156), "plusminus");
		sidResolver.put(new Integer(157), "Thorn");
		sidResolver.put(new Integer(158), "onequarter");
		sidResolver.put(new Integer(159), "divide");
		sidResolver.put(new Integer(160), "brokenbar");
		sidResolver.put(new Integer(161), "degree");
		sidResolver.put(new Integer(162), "thorn");
		sidResolver.put(new Integer(163), "threequarters");
		sidResolver.put(new Integer(164), "twosuperior");
		sidResolver.put(new Integer(165), "registered");
		sidResolver.put(new Integer(166), "minus");
		sidResolver.put(new Integer(167), "eth");
		sidResolver.put(new Integer(168), "multiply");
		sidResolver.put(new Integer(169), "threesuperior");
		sidResolver.put(new Integer(170), "copyright");
		sidResolver.put(new Integer(171), "Aacute");
		sidResolver.put(new Integer(172), "Acircumflex");
		sidResolver.put(new Integer(173), "Adieresis");
		sidResolver.put(new Integer(174), "Agrave");
		sidResolver.put(new Integer(175), "Aring");
		sidResolver.put(new Integer(176), "Atilde");
		sidResolver.put(new Integer(177), "Ccedilla");
		sidResolver.put(new Integer(178), "Eacute");
		sidResolver.put(new Integer(179), "Ecircumflex");
		sidResolver.put(new Integer(180), "Edieresis");
		sidResolver.put(new Integer(181), "Egrave");
		sidResolver.put(new Integer(182), "Iacute");
		sidResolver.put(new Integer(183), "Icircumflex");
		sidResolver.put(new Integer(184), "Idieresis");
		sidResolver.put(new Integer(185), "Igrave");
		sidResolver.put(new Integer(186), "Ntilde");
		sidResolver.put(new Integer(187), "Oacute");
		sidResolver.put(new Integer(188), "Ocircumflex");
		sidResolver.put(new Integer(189), "Odieresis");
		sidResolver.put(new Integer(190), "Ograve");
		sidResolver.put(new Integer(191), "Otilde");
		sidResolver.put(new Integer(192), "Scaron");
		sidResolver.put(new Integer(193), "Uacute");
		sidResolver.put(new Integer(194), "Ucircumflex");
		sidResolver.put(new Integer(195), "Udieresis");
		sidResolver.put(new Integer(196), "Ugrave");
		sidResolver.put(new Integer(197), "Yacute");
		sidResolver.put(new Integer(198), "Ydieresis");
		sidResolver.put(new Integer(199), "Zcaron");
		sidResolver.put(new Integer(200), "aacute");
		sidResolver.put(new Integer(201), "acircumflex");
		sidResolver.put(new Integer(202), "adieresis");
		sidResolver.put(new Integer(203), "agrave");
		sidResolver.put(new Integer(204), "aring");
		sidResolver.put(new Integer(205), "atilde");
		sidResolver.put(new Integer(206), "ccedilla");
		sidResolver.put(new Integer(207), "eacute");
		sidResolver.put(new Integer(208), "ecircumflex");
		sidResolver.put(new Integer(209), "edieresis");
		sidResolver.put(new Integer(210), "egrave");
		sidResolver.put(new Integer(211), "iacute");
		sidResolver.put(new Integer(212), "icircumflex");
		sidResolver.put(new Integer(213), "idieresis");
		sidResolver.put(new Integer(214), "igrave");
		sidResolver.put(new Integer(215), "ntilde");
		sidResolver.put(new Integer(216), "oacute");
		sidResolver.put(new Integer(217), "ocircumflex");
		sidResolver.put(new Integer(218), "odieresis");
		sidResolver.put(new Integer(219), "ograve");
		sidResolver.put(new Integer(220), "otilde");
		sidResolver.put(new Integer(221), "scaron");
		sidResolver.put(new Integer(222), "uacute");
		sidResolver.put(new Integer(223), "ucircumflex");
		sidResolver.put(new Integer(224), "udieresis");
		sidResolver.put(new Integer(225), "ugrave");
		sidResolver.put(new Integer(226), "yacute");
		sidResolver.put(new Integer(227), "ydieresis");
		sidResolver.put(new Integer(228), "zcaron");
		sidResolver.put(new Integer(229), "exclamsmall");
		sidResolver.put(new Integer(230), "Hungarumlautsmall");
		sidResolver.put(new Integer(231), "dollaroldstyle");
		sidResolver.put(new Integer(232), "dollarsuperior");
		sidResolver.put(new Integer(233), "ampersandsmall");
		sidResolver.put(new Integer(234), "Acutesmall");
		sidResolver.put(new Integer(235), "parenleftsuperior");
		sidResolver.put(new Integer(236), "parenrightsuperior");
		sidResolver.put(new Integer(237), "twodotenleader");
		sidResolver.put(new Integer(238), "onedotenleader");
		sidResolver.put(new Integer(239), "zerooldstyle");
		sidResolver.put(new Integer(240), "oneoldstyle");
		sidResolver.put(new Integer(241), "twooldstyle");
		sidResolver.put(new Integer(242), "threeoldstyle");
		sidResolver.put(new Integer(243), "fouroldstyle");
		sidResolver.put(new Integer(244), "fiveoldstyle");
		sidResolver.put(new Integer(245), "sixoldstyle");
		sidResolver.put(new Integer(246), "sevenoldstyle");
		sidResolver.put(new Integer(247), "eightoldstyle");
		sidResolver.put(new Integer(248), "nineoldstyle");
		sidResolver.put(new Integer(249), "commasuperior");
		sidResolver.put(new Integer(250), "threequartersemdash");
		sidResolver.put(new Integer(251), "periodsuperior");
		sidResolver.put(new Integer(252), "questionsmall");
		sidResolver.put(new Integer(253), "asuperior");
		sidResolver.put(new Integer(254), "bsuperior");
		sidResolver.put(new Integer(255), "centsuperior");
		sidResolver.put(new Integer(256), "dsuperior");
		sidResolver.put(new Integer(257), "esuperior");
		sidResolver.put(new Integer(258), "isuperior");
		sidResolver.put(new Integer(259), "lsuperior");
		sidResolver.put(new Integer(260), "msuperior");
		sidResolver.put(new Integer(261), "nsuperior");
		sidResolver.put(new Integer(262), "osuperior");
		sidResolver.put(new Integer(263), "rsuperior");
		sidResolver.put(new Integer(264), "ssuperior");
		sidResolver.put(new Integer(265), "tsuperior");
		sidResolver.put(new Integer(266), "ff");
		sidResolver.put(new Integer(267), "ffi");
		sidResolver.put(new Integer(268), "ffl");
		sidResolver.put(new Integer(269), "parenleftinferior");
		sidResolver.put(new Integer(270), "parenrightinferior");
		sidResolver.put(new Integer(271), "Circumflexsmall");
		sidResolver.put(new Integer(272), "hyphensuperior");
		sidResolver.put(new Integer(273), "Gravesmall");
		sidResolver.put(new Integer(274), "Asmall");
		sidResolver.put(new Integer(275), "Bsmall");
		sidResolver.put(new Integer(276), "Csmall");
		sidResolver.put(new Integer(277), "Dsmall");
		sidResolver.put(new Integer(278), "Esmall");
		sidResolver.put(new Integer(279), "Fsmall");
		sidResolver.put(new Integer(280), "Gsmall");
		sidResolver.put(new Integer(281), "Hsmall");
		sidResolver.put(new Integer(282), "Ismall");
		sidResolver.put(new Integer(283), "Jsmall");
		sidResolver.put(new Integer(284), "Ksmall");
		sidResolver.put(new Integer(285), "Lsmall");
		sidResolver.put(new Integer(286), "Msmall");
		sidResolver.put(new Integer(287), "Nsmall");
		sidResolver.put(new Integer(288), "Osmall");
		sidResolver.put(new Integer(289), "Psmall");
		sidResolver.put(new Integer(290), "Qsmall");
		sidResolver.put(new Integer(291), "Rsmall");
		sidResolver.put(new Integer(292), "Ssmall");
		sidResolver.put(new Integer(293), "Tsmall");
		sidResolver.put(new Integer(294), "Usmall");
		sidResolver.put(new Integer(295), "Vsmall");
		sidResolver.put(new Integer(296), "Wsmall");
		sidResolver.put(new Integer(297), "Xsmall");
		sidResolver.put(new Integer(298), "Ysmall");
		sidResolver.put(new Integer(299), "Zsmall");
		sidResolver.put(new Integer(300), "colonmonetary");
		sidResolver.put(new Integer(301), "onefitted");
		sidResolver.put(new Integer(302), "rupiah");
		sidResolver.put(new Integer(303), "Tildesmall");
		sidResolver.put(new Integer(304), "exclamdownsmall");
		sidResolver.put(new Integer(305), "centoldstyle");
		sidResolver.put(new Integer(306), "Lslashsmall");
		sidResolver.put(new Integer(307), "Scaronsmall");
		sidResolver.put(new Integer(308), "Zcaronsmall");
		sidResolver.put(new Integer(309), "Dieresissmall");
		sidResolver.put(new Integer(310), "Brevesmall");
		sidResolver.put(new Integer(311), "Caronsmall");
		sidResolver.put(new Integer(312), "Dotaccentsmall");
		sidResolver.put(new Integer(313), "Macronsmall");
		sidResolver.put(new Integer(314), "figuredash");
		sidResolver.put(new Integer(315), "hypheninferior");
		sidResolver.put(new Integer(316), "Ogoneksmall");
		sidResolver.put(new Integer(317), "Ringsmall");
		sidResolver.put(new Integer(318), "Cedillasmall");
		sidResolver.put(new Integer(319), "questiondownsmall");
		sidResolver.put(new Integer(320), "oneeighth");
		sidResolver.put(new Integer(321), "threeeighths");
		sidResolver.put(new Integer(322), "fiveeighths");
		sidResolver.put(new Integer(323), "seveneighths");
		sidResolver.put(new Integer(324), "onethird");
		sidResolver.put(new Integer(325), "twothirds");
		sidResolver.put(new Integer(326), "zerosuperior");
		sidResolver.put(new Integer(327), "foursuperior");
		sidResolver.put(new Integer(328), "fivesuperior");
		sidResolver.put(new Integer(329), "sixsuperior");
		sidResolver.put(new Integer(330), "sevensuperior");
		sidResolver.put(new Integer(331), "eightsuperior");
		sidResolver.put(new Integer(332), "ninesuperior");
		sidResolver.put(new Integer(333), "zeroinferior");
		sidResolver.put(new Integer(334), "oneinferior");
		sidResolver.put(new Integer(335), "twoinferior");
		sidResolver.put(new Integer(336), "threeinferior");
		sidResolver.put(new Integer(337), "fourinferior");
		sidResolver.put(new Integer(338), "fiveinferior");
		sidResolver.put(new Integer(339), "sixinferior");
		sidResolver.put(new Integer(340), "seveninferior");
		sidResolver.put(new Integer(341), "eightinferior");
		sidResolver.put(new Integer(342), "nineinferior");
		sidResolver.put(new Integer(343), "centinferior");
		sidResolver.put(new Integer(344), "dollarinferior");
		sidResolver.put(new Integer(345), "periodinferior");
		sidResolver.put(new Integer(346), "commainferior");
		sidResolver.put(new Integer(347), "Agravesmall");
		sidResolver.put(new Integer(348), "Aacutesmall");
		sidResolver.put(new Integer(349), "Acircumflexsmall");
		sidResolver.put(new Integer(350), "Atildesmall");
		sidResolver.put(new Integer(351), "Adieresissmall");
		sidResolver.put(new Integer(352), "Aringsmall");
		sidResolver.put(new Integer(353), "AEsmall");
		sidResolver.put(new Integer(354), "Ccedillasmall");
		sidResolver.put(new Integer(355), "Egravesmall");
		sidResolver.put(new Integer(356), "Eacutesmall");
		sidResolver.put(new Integer(357), "Ecircumflexsmall");
		sidResolver.put(new Integer(358), "Edieresissmall");
		sidResolver.put(new Integer(359), "Igravesmall");
		sidResolver.put(new Integer(360), "Iacutesmall");
		sidResolver.put(new Integer(361), "Icircumflexsmall");
		sidResolver.put(new Integer(362), "Idieresissmall");
		sidResolver.put(new Integer(363), "Ethsmall");
		sidResolver.put(new Integer(364), "Ntildesmall");
		sidResolver.put(new Integer(365), "Ogravesmall");
		sidResolver.put(new Integer(366), "Oacutesmall");
		sidResolver.put(new Integer(367), "Ocircumflexsmall");
		sidResolver.put(new Integer(368), "Otildesmall");
		sidResolver.put(new Integer(369), "Odieresissmall");
		sidResolver.put(new Integer(370), "OEsmall");
		sidResolver.put(new Integer(371), "Oslashsmall");
		sidResolver.put(new Integer(372), "Ugravesmall");
		sidResolver.put(new Integer(373), "Uacutesmall");
		sidResolver.put(new Integer(374), "Ucircumflexsmall");
		sidResolver.put(new Integer(375), "Udieresissmall");
		sidResolver.put(new Integer(376), "Yacutesmall");
		sidResolver.put(new Integer(377), "Thornsmall");
		sidResolver.put(new Integer(378), "Ydieresissmall");
		sidResolver.put(new Integer(379), "001.000");
		sidResolver.put(new Integer(380), "001.001");
		sidResolver.put(new Integer(381), "001.002");
		sidResolver.put(new Integer(382), "001.003");
		sidResolver.put(new Integer(383), "Black");
		sidResolver.put(new Integer(384), "Bold");
		sidResolver.put(new Integer(385), "Book");
		sidResolver.put(new Integer(386), "Light");
		sidResolver.put(new Integer(387), "Medium");
		sidResolver.put(new Integer(388), "Regular");
		sidResolver.put(new Integer(389), "Roman");
		sidResolver.put(new Integer(390), "Semibold");
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
	
	private static CharImageMatch getCharForImage(CharImage charImage, Font[] serifFonts, Font[] sansFonts, HashMap cache, boolean debug) {
		
		//	wrap and measure char
		CharMetrics chMetrics = PdfCharDecoder.getCharMetrics(charImage.img, 1);
		
		//	set up statistics
		CharImageMatch bestCim = null;
		
		//	get ranked list of probably matches
		SortedSet matchChars = PdfCharDecoder.getScoredCharSignatures(chMetrics, 0, true, ((char) 0), null);
		
		//	evaluate probable matches
		float bestCsSim = 0;
		float bestSim = 0;
		for (Iterator mcit = matchChars.iterator(); mcit.hasNext();) {
			ScoredCharSignature scs = ((ScoredCharSignature) mcit.next());
			if (scs.difference > 500)
				break;
			System.out.println(" testing '" + scs.cs.ch + "' (" + ((int) scs.cs.ch) + "), signature difference is " + scs.difference);
			CharMatchResult cmr = PdfCharDecoder.matchChar(charImage, scs.cs.ch, false, serifFonts, sansFonts, cache, false, debug);
			if (!cmr.rendered)
				continue;
			
			CharImageMatch cim = null;
			String cimStyle = null;
			for (int s = 0; s < cmr.serifStyleCims.length; s++) {
				if (cmr.serifStyleCims[s] == null)
					continue;
				if ((cim == null) || (cim.sim < cmr.serifStyleCims[s].sim)) {
					cim = cmr.serifStyleCims[s];
					cimStyle = ("Serif-" + s);
				}
			}
			for (int s = 0; s < cmr.sansStyleCims.length; s++) {
				if (cmr.sansStyleCims[s] == null)
					continue;
				if ((cim == null) || (cim.sim < cmr.sansStyleCims[s].sim)) {
					cim = cmr.sansStyleCims[s];
					cimStyle = ("Sans-" + s);
				}
			}
			
			if (cim == null) {
//				System.out.println("   --> could not render image");
				continue;
			}
			System.out.println("   --> similarity is " + cim.sim + " in " + cimStyle);
//			if ((bestCim == null) || (cim.sim > bestCim.sim)) {
//				bestCim = cim;
//				System.out.println("   ==> new best match '" + scs.cs.ch + "' (" + ((int) scs.cs.ch) + ", " + StringUtils.getCharName((char) scs.cs.ch) + ") in " + cimStyle + ", similarity is " + cim.sim);
//				if (debug)
//					PdfCharDecoder.displayCharMatch(charImage, cim, "New best match");
//			}
			if ((bestCim == null) || ((cim.sim - (scs.difference / 25)) > (bestSim - (bestCsSim / 25)))) {
				bestCim = cim;
				bestCsSim = scs.difference;
				bestSim = cim.sim;
				System.out.println("   ==> new best match '" + scs.cs.ch + "' (" + ((int) scs.cs.ch) + ", " + StringUtils.getCharName((char) scs.cs.ch) + ") in " + cimStyle + ", similarity is " + cim.sim);
				if (debug)
					PdfCharDecoder.displayCharMatch(cim, "New best match");
			}
		}
		
		//	finally ...
		return bestCim;
	}
	
	private static final String skewChars = "AIJSVWXYfgsvwxy7()[]{}/\\!%&"; // chars that can cause trouble in italic fonts due to varying angle
	private static final String diacriticMarkerChars = (""
			+ StringUtils.getCharForName("acute")
			+ StringUtils.getCharForName("grave")
			+ StringUtils.getCharForName("breve")
			+ StringUtils.getCharForName("circumflex")
			+ StringUtils.getCharForName("cedilla")
			+ StringUtils.getCharForName("dieresis")
			+ StringUtils.getCharForName("macron")
			+ StringUtils.getCharForName("caron")
			+ StringUtils.getCharForName("ogonek")
			+ StringUtils.getCharForName("ring")
		);
	private static final String fractionChars = (""
			+ StringUtils.getCharForName("percent")
			+ StringUtils.getCharForName("onehalf")
			+ StringUtils.getCharForName("onethird")
			+ StringUtils.getCharForName("twothirds")
			+ StringUtils.getCharForName("onequarter")
			+ StringUtils.getCharForName("threequarters")
			+ StringUtils.getCharForName("oneeighth")
			+ StringUtils.getCharForName("threeeighths")
			+ StringUtils.getCharForName("fiveeighths")
			+ StringUtils.getCharForName("seveneighths")
		);
	private static final String bracketChars = (""
//			+ StringUtils.getCharForName("parenleft")
//			+ StringUtils.getCharForName("parenright")
//			+ StringUtils.getCharForName("bracketleft")
//			+ StringUtils.getCharForName("bracketright")
//			+ StringUtils.getCharForName("braceleft")
//			+ StringUtils.getCharForName("braceright")
		);
	
	/*
	 * chars (mostly actually char modifiers, safe for a few punctuation marks,
	 * and ligatures) that vary too widely across fonts to prevent a font match;
	 * we have to exclude both upper and lower case V and W as well, as they
	 * vary a lot in their angle (both upper and lower case) or are round (lower
	 * case) in some italics font faces and thus won't match comparison; in
	 * addition, capital A also varies too much in italic angle, and the
	 * descending tails of capital J and Q and lower case Y and Z exhibit
	 * extreme variability as well
	 */
	private static final String minIgnoreChars = ("%@?*&/" + diacriticMarkerChars + fractionChars + bracketChars + '\uFB00' + '\uFB01' + '\uFB02' + '\uFB03' + '\uFB04' + '0' + 'A' + 'J' + 'O' + 'Q' + 'V' + 'W' + 'k' + 'v' + 'w' + 'y' + 'z' + '\u00F8' + '\u00BC' + '\u00BD' + '\u2153'); 
//	private static final int highestPossiblePunctuationMark = 9842; // corresponds to 2672 in Unicode, the end of the Misc Symbols range (we need this for 'male' and 'female' ...)
	
	static Font getSerifFont() {
		String osName = System.getProperty("os.name");
		String fontName = "Serif";
//		if (osName.matches("Win.*"))
//			fontName = "Times New Roman";
//		else if (osName.matches(".*Linux.*") || osName.matches("Mac.*")) {
//			String[] fontNames = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
//			for (int f = 0; f < fontNames.length; f++)
//				if (fontNames[f].toLowerCase().startsWith("times")) {
//					fontName = fontNames[f];
//					break;
//				}
//		}
		return Font.decode(fontName);
	}
	
	static Font getSansSerifFont() {
		String osName = System.getProperty("os.name");
		String fontName = "SansSerif";
//		if (osName.matches("Win.*"))
//			fontName = "Arial Unicode MS";
//		else if (osName.matches(".*Linux.*") || osName.matches("Mac.*")) {
//			String[] fontNames = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
//			for (int f = 0; f < fontNames.length; f++)
//				if (fontNames[f].toLowerCase().startsWith("arial")) {
//					fontName = fontNames[f];
//					break;
//				}
//		}
		return Font.decode(fontName);
	}
}