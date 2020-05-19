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
package de.uka.ipd.idaho.im.pdf;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import de.uka.ipd.idaho.gamta.util.CountingSet;
import de.uka.ipd.idaho.im.pdf.PdfParser.PStream;
import de.uka.ipd.idaho.im.pdf.PdfParser.PString;

/**
 * @author sautter
 *
 */
abstract class PdfColorSpace {
	final String name;
	final int numComponents;
	final boolean isAdditive;
	final String[] colorantNames;
	PdfColorSpace(String name, int numComponents, boolean isAdditive) {
		this.name = name;
		this.numComponents = numComponents;
		this.isAdditive = isAdditive;
		this.colorantNames = new String[this.numComponents];
		if ("DeviceCMYK".equalsIgnoreCase(name)) {
			this.colorantNames[0] = "Cyan";
			this.colorantNames[1] = "Magenta";
			this.colorantNames[2] = "Yellow";
			this.colorantNames[3] = "Black";
		}
		else if ("DeviceRGB".equalsIgnoreCase(name) || "CalRGB".equalsIgnoreCase(name)) {
			this.colorantNames[0] = "Red";
			this.colorantNames[1] = "Green";
			this.colorantNames[2] = "Blue";
		}
		else if ("DeviceGray".equalsIgnoreCase(name) || "CalGray".equalsIgnoreCase(name)) {
			this.colorantNames[0] = "Black";
		}
		else if ("Lab".equalsIgnoreCase(name)) {
			this.colorantNames[0] = "A";
			this.colorantNames[1] = "B";
			this.colorantNames[2] = "C";
		}
	}
	Color getColor(LinkedList stack, String indent) {
		while (!(stack.getLast() instanceof Number))
			stack.removeLast();
		return this.decodeColor(stack, indent);
	}
	abstract Color decodeColor(LinkedList stack, String indent);
	
	void printStats() {}
	
	static PdfColorSpace getColorSpace(String name) {
		if ((name != null) && (name.startsWith("/")))
			name = name.substring("/".length());
		if ("DeviceCMYK".equalsIgnoreCase(name))
			return deviceCmyk;
		else if ("DeviceRGB".equalsIgnoreCase(name))
			return deviceRgb;
		else if ("CalRGB".equalsIgnoreCase(name))
			return calRgb;
		else if ("Lab".equalsIgnoreCase(name))
			return lab;
		else if ("DeviceGray".equalsIgnoreCase(name))
			return deviceGray;
		else if ("CalGray".equalsIgnoreCase(name))
			return calGray;
		else if ("Pattern".equalsIgnoreCase(name))
			return pattern;
		else return null;
	}
	
	static PdfColorSpace getColorSpace(Object csObj, Map objects) {
		csObj = PdfParser.dereference(csObj, objects);
//		if (csObj instanceof Vector) {
		if (csObj instanceof List) {
//			Vector csData = ((Vector) csObj);
			List csData = ((List) csObj);
			PdfParser.dereferenceObjects(csData, objects);
			if (csData.size() < 1)
				throw new RuntimeException("Invalid empty Color Space data.");
			String csType = csData.get(0).toString();
			if ("Indexed".equals(csType)) {
				if (csData.size() < 4)
					throw new RuntimeException("Invalid Indexed Color Space.");
				else return new IndexedColorSpace("CustomIndexed", csData, getColorSpace(csData.get(1), objects), objects);
			}
			else if ("Separation".equals(csType)) {
				if (csData.size() < 4)
					throw new RuntimeException("Invalid Separation Color Space.");
				else return new SeparationColorSpace("CustomSeparation", csData, getColorSpace(csData.get(2), objects), objects);
			}
			else if ("DeviceN".equals(csType)) {
				if (csData.size() < 4)
					throw new RuntimeException("Invalid DeviceN Color Space.");
				else return new DeviceNColorSpace("CustomDeviceN", csData, getColorSpace(csData.get(2), objects), objects);
			}
			else if ("ICCBased".equals(csType)) {
				if (csData.size() < 2)
					throw new RuntimeException("Invalid ICCBased Color Space.");
				Object csContentObj = PdfParser.dereference(csData.get(1), objects);
				if (csContentObj instanceof PStream) {
					PStream csContent = ((PStream) csContentObj);
					Object altCsObj = csContent.params.get("Alternate");
					if (altCsObj != null) try {
						PdfColorSpace altCs = getColorSpace(altCsObj, objects);
						if (altCs != null)
							return altCs;
					}
					catch (RuntimeException re) {
						System.out.println("Strange alternate Color Space '" + re.getMessage() + "'");
					}
					int n = ((Number) csContent.params.get("N")).intValue();
					if (n == 1)
						return deviceGray;
					else if (n == 3)
						return deviceRgb;
					else if (n == 4)
						return deviceCmyk;
					else throw new RuntimeException("Invalid ICCBased Color Space component count: " + n);
				}
				else throw new RuntimeException("Invalid ICCBased Color Space content: " + csContentObj);
			}
			else {
				PdfColorSpace cs = getColorSpace(csType);
				if (cs == null)
					throw new RuntimeException("Need to implement Color Space type '" + csType + "'.");
				else return cs;
			}
		}
		else return getColorSpace(csObj.toString());
	}
	
//	static PdfColorSpace getImageMaskColorSpace(Hashtable params, Map objects) {
	static PdfColorSpace getImageMaskColorSpace(Map params, Map objects) {
		Object decodeObj = PdfParser.dereference(params.get("Decode"), objects);
		//	Decode: [0, 1] => 0 is black, 1 is white; [1, 0] => the other way around
		boolean zeroIsBlack = true;
//		if ((decodeObj instanceof Vector) && (((Vector) decodeObj).size() == 2))
//			zeroIsBlack = (((Number) ((Vector) decodeObj).get(0)).intValue() == 0);
		if ((decodeObj instanceof List) && (((List) decodeObj).size() == 2))
			zeroIsBlack = (((Number) ((List) decodeObj).get(0)).intValue() == 0);
		return (zeroIsBlack ? imageMask01 : imageMask10);
	}
	private static PdfColorSpace imageMask01 = new PdfColorSpace("ImageMask01", 1, true) {
		Color decodeColor(LinkedList stack, String indent) {
			boolean pixelIsZero = (((Number) stack.removeLast()).floatValue() < 0.5);
			return (pixelIsZero ? Color.BLACK : Color.WHITE);
		}
	};
	private static PdfColorSpace imageMask10 = new PdfColorSpace("ImageMask10", 1, true) {
		Color decodeColor(LinkedList stack, String indent) {
			boolean pixelIsZero = (((Number) stack.removeLast()).floatValue() < 0.5);
			return (pixelIsZero ? Color.WHITE : Color.BLACK);
		}
	};
	
	private static PdfColorSpace deviceCmyk = new PdfColorSpace("DeviceCMYK", 4, false) {
		Color decodeColor(LinkedList stack, String indent) {
			float k = ((Number) stack.removeLast()).floatValue();
			float y = ((Number) stack.removeLast()).floatValue();
			float m = ((Number) stack.removeLast()).floatValue();
			float c = ((Number) stack.removeLast()).floatValue();
			return convertCmykToRgb(c, m, y, k);
		}
	};
	
	private static PdfColorSpace deviceRgb = new PdfColorSpace("DeviceRGB", 3, true) {
		Color decodeColor(LinkedList stack, String indent) {
			float b = ((Number) stack.removeLast()).floatValue();
			float g = ((Number) stack.removeLast()).floatValue();
			float r = ((Number) stack.removeLast()).floatValue();
			return new Color(r, g, b);
		}
	};
	
	private static PdfColorSpace calRgb = new PdfColorSpace("CalRGB", 3, true) {
		Color decodeColor(LinkedList stack, String indent) {
			float b = ((Number) stack.removeLast()).floatValue();
			float g = ((Number) stack.removeLast()).floatValue();
			float r = ((Number) stack.removeLast()).floatValue();
			return new Color(r, g, b); // TODO observe black point and white point
		}
	};
	
	//	Lab color space is somewhat similar to HSB
	private static PdfColorSpace lab = new PdfColorSpace("Lab", 3, true) {
		//	TODO observe ranges of a and b (see PDF 1.6 Spec from page number 220 onwards)
		Color decodeColor(LinkedList stack, String indent) {
			float lab_b = ((Number) stack.removeLast()).floatValue();
			float lab_a = ((Number) stack.removeLast()).floatValue();
			float lab_L = ((Number) stack.removeLast()).floatValue();
			
			//	from http://www.easyrgb.com/index.php?X=MATH&H=08#text8
			float y = ((lab_L + 16) / 116);
			float x = ((lab_a / 500) + y);
			float z = (y - (lab_b / 200));
			
			if (y > 0.2069) // if ((y * y * y) > 0.008856)
				y = (y * y * y);
			else y = ((y - (16.0f / 116)) / 7.787f);
			if (x > 0.2069) // if ((x * x * x) > 0.008856)
				x = (x * x * x);
			else x = ((x - (16.0f / 116)) / 7.787f);
			if (z > 0.2069) // if ((z * z * z) > 0.008856)
				z = (z * z * z);
			else z = ((z - (16.0f / 116)) / 7.787f);
			
			//	multiplication factors for 2° D65 (Daylight, sRGB, Adobe-RGB)
			x = (x * 95.047f);
			y = (y * 100.0f);
			z = (z * 108.883f);
			
			//	from http://www.easyrgb.com/index.php?X=MATH&H=01#text1
			x = (x / 100);
			y = (y / 100);
			z = (z / 100);
			
			float r = ((x * 3.2406f) + (y * -1.5372f) + (z * -0.4986f));
			float g = ((x * -0.9689f) + (y * 1.8758f) + (z * 0.0415f));
			float b = ((x * 0.0557f) + (y * -0.2040f) + (z * 1.0570f));
			
			if (r > 0.0031308)
				r = ((1.055f * ((float) Math.pow(r, (1 / 2.4 )))) - 0.055f);
			else r = (12.92f * r);
			if (g > 0.0031308)
				g = ((1.055f * ((float) Math.pow(g, (1 / 2.4 )))) - 0.055f);
			else g = (12.92f * g);
			if (b > 0.0031308)
				b = ((1.055f * ((float) Math.pow(b, (1 / 2.4 )))) - 0.055f);
			else b = (12.92f * b);
			
			try {
				return new Color(r, g, b);
			}
			catch (RuntimeException re) {
				System.out.println("ColorSpace Lab: illegal RGB values " + r + "/" + g + "/" + b + " for input " + lab_L + "/" + lab_a + "/" + lab_b + ", using range-adjusted fallback");
				return new Color(this.sanitize(r), this.sanitize(g), this.sanitize(b));
//				return Color.RED; TODO use this to find page content color space is applied to (for comparison to Acrobat)
			}
		}
		private float sanitize(float f) {
			return Math.max(0, Math.min(1, f));
		}
	};
	
	private static PdfColorSpace deviceGray = new PdfColorSpace("DeviceGray", 1, true) {
		Color decodeColor(LinkedList stack, String indent) {
			float g = ((Number) stack.removeLast()).floatValue();
			return new Color(g, g, g);
		}
	};
	
	private static PdfColorSpace calGray = new PdfColorSpace("CalGray", 1, true) {
		Color decodeColor(LinkedList stack, String indent) {
			float g = ((Number) stack.removeLast()).floatValue();
			return new Color(g, g, g); // TODO observe black point and white point
		}
	};
	
	private static PdfColorSpace pattern = new PdfColorSpace("Pattern", 1, true) {
		Color decodeColor(LinkedList stack, String indent) {
			throw new RuntimeException("Need to get pattern from page resources ...");
		}
	};
	
	private static class IndexedColorSpace extends PdfColorSpace {
		private PdfColorSpace baseColorSpace;
		private int hival;
		private byte[] lookup;
		HashMap colorCache = new HashMap();
//		IndexedColorSpace(String name, Vector data, PdfColorSpace baseCs, Map objects) {
		IndexedColorSpace(String name, List data, PdfColorSpace baseCs, Map objects) {
			super(name, 1, baseCs.isAdditive);
			if (data.size() < 4)
				throw new RuntimeException("Invalid data for Indexed Color Space: " + data);
			
			this.baseColorSpace = baseCs;
			if (this.baseColorSpace == null)
				throw new RuntimeException("Invalid base Color Space '" + data.get(1) + "'");
			
			this.hival = Math.min(((Number) data.get(2)).intValue(), 255);
			
			Object lookupObj = PdfParser.dereference(data.get(3), objects);
			if (lookupObj instanceof PStream) {
				PStream lookup = ((PStream) lookupObj);
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				try {
					PdfParser.decode(PdfParser.getObject(lookup.params, "Filter", objects), lookup.bytes, lookup.params, baos, objects);
					this.lookup = baos.toByteArray();
				}
				catch (IOException ioe) {
					System.out.println("Error decoding lookup of indexed Color Space '" + name + "': " + ioe.getMessage());
					ioe.printStackTrace(System.out);
					throw new RuntimeException(ioe); // should not happen, but we never can be sure
				}
			}
			else if (lookupObj instanceof PString) {
				PString lookup = ((PString) lookupObj);
				if (lookup.isHex4)
					lookup = new PString(lookup.bytes, true, false, lookup.isHexWithSpace);
				this.lookup = new byte[lookup.length()];
				for (int l = 0; l < this.lookup.length; l++) {
					int b = ((int) lookup.charAt(l));
					if (b > 127)
						b -= 256;
					this.lookup[l] = ((byte) b);
				}
			}
			else {
				String lookup = lookupObj.toString();
				this.lookup = new byte[lookup.length()];
				for (int l = 0; l < this.lookup.length; l++) {
					int b = ((int) lookup.charAt(l));
					if (b > 127)
						b -= 256;
					this.lookup[l] = ((byte) b);
				}
			}
		}
		Color decodeColor(LinkedList stack, String indent) {
			int index = Math.round((255 * ((Number) stack.removeLast()).floatValue()));
			index = Math.min(index, this.hival);
			Color color = ((Color) this.colorCache.get(new Integer(index)));
			if (color == null) {
				if (indent != null)
					System.out.println(indent + this.name + ": Index is " + index);
				int offset = (index * this.baseColorSpace.numComponents);
				if (indent != null)
					System.out.println(indent + this.name + ": Offset is " + offset);
				for (int c = 0; c < this.baseColorSpace.numComponents; c++) {
					int b = this.lookup[offset + c];
					if (b < 0)
						b += 256;
					stack.addLast(new Float(((float) b) / 255));
				}
				if (indent != null)
					System.out.println(indent + this.name + ": Stack is " + stack);
				color = this.baseColorSpace.getColor(stack, ((indent == null) ? null : (indent + "  ")));
				if (indent != null)
					System.out.println(indent + this.name + ": Color from " + this.baseColorSpace.name + " is " + color);
				this.colorCache.put(new Integer(index), color);
			}
			this.colorStats.add(new Integer(index));
			return color;
		}
		private CountingSet colorStats = new CountingSet(new TreeMap());
		void printStats() {
			System.out.println("Indexed color space access stats:");
			for (Iterator cit = this.colorStats.iterator(); cit.hasNext();) {
				Integer ci = ((Integer) cit.next());
				int offset = (ci.intValue() * this.baseColorSpace.numComponents);
				StringBuffer lookup = new StringBuffer("");
				for (int c = 0; c < this.baseColorSpace.numComponents; c++) {
					int b = this.lookup[offset + c];
					if (b < 0)
						b += 256;
					lookup.append(" " + Integer.toString(b, 16).toUpperCase());
				}
				System.out.println(" - " + ci + "/" + Integer.toString(ci.intValue(), 16).toUpperCase() + ": ==>" + lookup + " = " + this.colorCache.get(ci) + " (" + this.colorStats.getCount(ci) + " times)");
			}
		}
	}
	
	private static class SeparationColorSpace extends PdfColorSpace {
		private String colorantName;
		private PdfColorSpace altColorSpace;
		private Function tintTransformer;
		private HashMap colorCache = new HashMap();
//		SeparationColorSpace(String name, Vector data, PdfColorSpace altCs, Map objects) {
		SeparationColorSpace(String name, List data, PdfColorSpace altCs, Map objects) {
			super(name, 1, false);
			if (data.size() < 4)
				throw new RuntimeException("Invalid data for Separation Color Space: " + data);
			
			this.colorantName = data.get(1).toString();
			
			this.altColorSpace = altCs;
			if (this.altColorSpace == null)
				throw new RuntimeException("Invalid alternative Color Space '" + data.get(2) + "'");
			
			Object ttObj = PdfParser.dereference(data.get(3), objects);
			if (ttObj instanceof PStream) {
				PStream ffStream = ((PStream) ttObj);
				this.tintTransformer = new Function(ffStream.params, ffStream, objects);
			}
//			else this.tintTransformer = new Function(((Hashtable) ttObj), null, objects);
			else this.tintTransformer = new Function(((Map) ttObj), null, objects);
		}
		Color decodeColor(LinkedList stack, String indent) {
			float t = ((Number) stack.removeLast()).floatValue();
			if ("None".equals(this.colorantName))
				return Color.WHITE; // nothing being painted at all ...
			else if ("All".equals(this.colorantName))
				return new Color((1 - t), (1 - t), (1 - t));
			else if ("Red".equals(this.colorantName))
				return new Color((1 - t), 0, 0);
			else if ("Green".equals(this.colorantName))
				return new Color(0, (1 - t), 0);
			else if ("Blue".equals(this.colorantName))
				return new Color(0, 0, (1 - t));
			else if ("Cyan".equals(this.colorantName))
				return convertCmykToRgb(t, 0, 0, 0);
			else if ("Magenta".equals(this.colorantName))
				return convertCmykToRgb(0, t, 0, 0);
			else if ("Yellow".equals(this.colorantName))
				return convertCmykToRgb(0, 0, t, 0);
			else if ("Black".equals(this.colorantName))
				return convertCmykToRgb(0, 0, 0, t);
			
			//	TODOne figure out what to do if our colorant is not present in alternative color space ==> implemented below
			boolean colorantNameMatched = false;
			for (int c = 0; c < this.altColorSpace.colorantNames.length; c++) {
				if (this.altColorSpace.colorantNames[c].equals(this.colorantName)) {
					stack.addLast(new Float(t));
					colorantNameMatched = true;
				}
				else stack.addLast(new Float(0));
			}
			if (colorantNameMatched)
				return this.altColorSpace.getColor(stack, ((indent == null) ? null : (indent + "  ")));
			
			//	use tint transformer
			String colorCacheKey = ("" + t);
			Color color = ((Color) this.colorCache.get(colorCacheKey));
			if (color == null) {
				float[] x = {t};
				if (indent != null)
					System.out.println(indent + this.name + ": X is " + Arrays.toString(x));
				float[] y = this.tintTransformer.evaluate(x, ((indent == null) ? null : (indent + "  ")));
				if (indent != null)
					System.out.println(indent + this.name + ": Y is " + Arrays.toString(y));
				for (int r = 0; r < y.length; r++)
					stack.addLast(new Float(y[r]));
				if (indent != null)
					System.out.println(indent + this.name + ": Stack is " + stack);
				color = this.altColorSpace.getColor(stack, ((indent == null) ? null : (indent + "  ")));
				if (indent != null)
					System.out.println(indent + this.name + ": Color from " + this.altColorSpace.name + " is " + color);
				this.colorCache.put(colorCacheKey, color);
			}
			return color;
		}
	}
	
	private static class DeviceNColorSpace extends PdfColorSpace {
		private String[] colorantNames;
		private PdfColorSpace altColorSpace;
		private Function tintTransformer;
		private String[] altComponentNames;
		private int[] colorantAltComponentIndices;
		private HashMap colorCache = new HashMap();
//		DeviceNColorSpace(String name, Vector data, PdfColorSpace altCs, Map objects) {
		DeviceNColorSpace(String name, List data, PdfColorSpace altCs, Map objects) {
//			super(name, ((Vector) data.get(1)).size(), ((data.size() < 5) || !(data.get(4) instanceof Hashtable) || (((Hashtable) data.get(4)).get("Subtype") == null) || !"NChannel".equals(((Hashtable) data.get(4)).get("Subtype"))));
			super(name, ((List) data.get(1)).size(), ((data.size() < 5) || !(data.get(4) instanceof Map) || (((Map) data.get(4)).get("Subtype") == null) || !"NChannel".equals(((Map) data.get(4)).get("Subtype"))));
			
//			Vector colorantNames = ((Vector) data.get(1));
			List colorantNames = ((List) data.get(1));
			this.colorantNames = new String[colorantNames.size()];
			for (int c = 0; c < colorantNames.size(); c++)
				this.colorantNames[c] = colorantNames.get(c).toString();
			
			this.altColorSpace = altCs;
			if (this.altColorSpace == null)
				throw new RuntimeException("Invalid alternative Color Space '" + data.get(2) + "'");
			
			Object ttObj = PdfParser.dereference(data.get(3), objects);
			if (ttObj instanceof PStream) {
				PStream ffStream = ((PStream) ttObj);
				this.tintTransformer = new Function(ffStream.params, ffStream, objects);
			}
//			else this.tintTransformer = new Function(((Hashtable) ttObj), null, objects);
			else this.tintTransformer = new Function(((Map) ttObj), null, objects);
			
			if (data.size() == 5) {
				Object attribObj = PdfParser.dereference(data.get(4), objects);
				System.out.println(this.name + ": attributes are " + attribObj);
//				if (attribObj instanceof Hashtable)
//					this.processAttributes(((Hashtable) attribObj), objects);
				if (attribObj instanceof Map)
					this.processAttributes(((Map) attribObj), objects);
			}
		}
		
//		private void processAttributes(Hashtable attribs, Map objects) {
		private void processAttributes(Map attribs, Map objects) {
			Object subTypeObj = attribs.get("Subtype");
			System.out.println(this.name + ": subtype is " + subTypeObj);
			if ((subTypeObj == null) || !subTypeObj.equals("NChannel"))
				return;
			
			Object processObj = PdfParser.dereference(attribs.get("Process"), objects);
			System.out.println(this.name + ": process is " + processObj);
//			if (!(processObj instanceof Hashtable))
			if (!(processObj instanceof Map))
				return;
//			Hashtable process = ((Hashtable) processObj);
			Map process = ((Map) processObj);
			
			this.altColorSpace = getColorSpace(PdfParser.dereference(process.get("ColorSpace"), objects), objects);
			if (this.altColorSpace == null)
				throw new RuntimeException("Invalid process Color Space '" + PdfParser.dereference(process.get("ColorSpace"), objects) + "'");
			
//			Vector processComponents = ((Vector) PdfParser.dereference(process.get("Components"), objects));
			List processComponents = ((List) PdfParser.dereference(process.get("Components"), objects));
			this.altComponentNames = new String[processComponents.size()];
			for (int c = 0; c < processComponents.size(); c++)
				this.altComponentNames[c] = processComponents.get(c).toString();
			
			this.colorantAltComponentIndices = new int[this.colorantNames.length];
			for (int c = 0; c < this.colorantNames.length; c++) {
				for (int ac = 0; ac < this.altComponentNames.length; ac++)
					if (this.altComponentNames[ac].equals(this.colorantNames[c])) {
						this.colorantAltComponentIndices[c] = ac;
						break;
					}
			}
			
			this.tintTransformer = null;
		}
		
		Color decodeColor(LinkedList stack, String indent) {
			Map colorantNamesToTints = new HashMap();
			LinkedList lStack = new LinkedList();
			String colorCacheKey = "";
			for (int c = this.colorantNames.length; c > 0; c--) {
				Number ci = ((Number) stack.removeLast());
				lStack.addFirst(ci);
				colorantNamesToTints.put(this.colorantNames[c-1], ci);
				colorCacheKey += ci.toString();
			}
			Color color = ((Color) this.colorCache.get(colorCacheKey));
			if (color == null) {
				float[] x = new float[lStack.size()];
				for (int d = 0; d < x.length; d++)
					x[d] = ((Number) lStack.removeFirst()).floatValue();
				if (indent != null)
					System.out.println(indent + this.name + ": X is " + Arrays.toString(x));
				float[] y = this.transformTints(x, indent);
				if (indent != null)
					System.out.println(indent + this.name + ": Y is " + Arrays.toString(y));
				for (int r = 0; r < y.length; r++)
					stack.addLast(new Float(y[r]));
				if (indent != null)
					System.out.println(indent + this.name + ": Stack is " + stack);
				color = this.altColorSpace.getColor(stack, ((indent == null) ? null : (indent + "  ")));
				if (indent != null)
					System.out.println(indent + this.name + ": Color from " + this.altColorSpace.name + " is " + color);
				this.colorCache.put(colorCacheKey, color);
			}
			this.colorStats.add(colorCacheKey);
			return color;
		}
		private float[] transformTints(float[] x, String indent) {
			if (this.tintTransformer == null) {
				float[] y = new float[this.altComponentNames.length];
				Arrays.fill(y, 0);
				for (int c = 0; c < this.colorantNames.length; c++)
					y[this.colorantAltComponentIndices[c]] = x[c];
				return y;
			}
			else return this.tintTransformer.evaluate(x, ((indent == null) ? null : (indent + "  ")));
		}
		private CountingSet colorStats = new CountingSet(new TreeMap());
		void printStats() {
			System.out.println("Indexed color space access stats:");
			for (Iterator cit = this.colorStats.iterator(); cit.hasNext();) {
				String cck = ((String) cit.next());
				System.out.println(" - " + cck + ": " + this.colorCache.get(cck) + " (" + this.colorStats.getCount(cck) + " times)");
			}
		}
	}
	
	private static Color convertCmykToRgb(float c, float m, float y, float k) {
		//	0-255 int based version from http://www.rapidtables.com/convert/color/cmyk-to-rgb.htm
		float r = ((1 - c) * (1 - k));
		float g = ((1 - m) * (1 - k));
		float b = ((1 - y) * (1 - k));
		return new Color(r, g, b);
	}
	
	private static class Function {
		final int type;
		final float[][] domains;
		
		//	data for multiple types
		float[][] ranges; // types 0 and 4, optional for types 2 and 3
		float[][] encode; // types 0 and 3
		byte[] data; // types 0 and 4
		
		//	data for type 0
		int[] size;
		float[][] sampledValues;
		int maxSampleValue;
		int bitsPerSample;
		int interpolationOrder = 1;
		float[][] decode;
		
		//	data for type 2
		float[] c0 = {0};
		float[] c1 = {1};
		float n;
		
		//	data for type 3
		Function[] functions;
		float[] bounds;
		
//		Function(Hashtable params, PStream stream, Map objects) {
		Function(Map params, PStream stream, Map objects) {
			Number type = ((Number) params.get("FunctionType"));
			this.type = type.intValue();
			
//			Vector domains = ((Vector) params.get("Domain"));
			List domains = ((List) params.get("Domain"));
			this.domains = new float[domains.size() / 2][2];
			for (int d = 0; d < this.domains.length; d++) {
				this.domains[d][0] = ((Number) domains.get(d * 2)).floatValue();
				this.domains[d][1] = ((Number) domains.get((d * 2) + 1)).floatValue();
			}
			
//			Vector ranges = ((Vector) params.get("Range"));
			List ranges = ((List) params.get("Range"));
			if (ranges != null) {
				this.ranges = new float[ranges.size() / 2][2];
				for (int r = 0; r < this.ranges.length; r++) {
					this.ranges[r][0] = ((Number) ranges.get(r * 2)).floatValue();
					this.ranges[r][1] = ((Number) ranges.get((r * 2) + 1)).floatValue();
				}
			}
			
			if (this.type == 0) {
//				Vector size = ((Vector) params.get("Size"));
				List size = ((List) params.get("Size"));
				this.size = new int[size.size()];
				this.sampledValues = new float[this.size.length][];
				for (int s = 0; s < this.size.length; s++) {
					this.size[s] = ((Number) size.get(s)).intValue();
					this.sampledValues[s] = new float[this.size[s]];
					for (int v = 0; v < this.sampledValues[s].length; v++)
						this.sampledValues[s][v] = (this.domains[s][0] + (((this.domains[s][1] - this.domains[s][0]) * v) / (this.size[s] - 1)));
				}
				
				Number bitsPerSample = ((Number) params.get("BitsPerSample"));
				this.bitsPerSample = bitsPerSample.intValue();
				int maxSampleValue = 0;
				for (int b = 0; b < this.bitsPerSample; b++) {
					maxSampleValue <<= 1;
					maxSampleValue |= 1;
				}
				this.maxSampleValue = maxSampleValue;
				
				Number interpolationOrder = ((Number) params.get("Order"));
				if (interpolationOrder != null)
					this.interpolationOrder = interpolationOrder.intValue();
				
//				Vector encode = ((Vector) params.get("Encode"));
				List encode = ((List) params.get("Encode"));
				if (encode != null) {
					this.encode = new float[encode.size() / 2][2];
					for (int e = 0; e < this.encode.length; e++) {
						this.encode[e][0] = ((Number) encode.get(e * 2)).floatValue();
						this.encode[e][1] = ((Number) encode.get((e * 2) + 1)).floatValue();
					}
				}
				else {
					this.encode = new float[size.size()][2];
					for (int e = 0; e < this.encode.length; e++) {
						this.encode[e][0] = 0;
						this.encode[e][1] = (((Number) size.get(e)).intValue() - 1);
					}
				}
				
//				Vector decode = ((Vector) params.get("Decode"));
				List decode = ((List) params.get("Decode"));
				if (decode != null) {
					this.decode = new float[decode.size() / 2][2];
					for (int d = 0; d < this.decode.length; d++) {
						this.decode[d][0] = ((Number) decode.get(d * 2)).floatValue();
						this.decode[d][1] = ((Number) decode.get((d * 2) + 1)).floatValue();
					}
				}
				else this.decode = this.ranges;
				
				try {
					ByteArrayOutputStream data = new ByteArrayOutputStream();
					PdfParser.decode(stream.params.get("Filter"), stream.bytes, stream.params, data, objects);
					this.data = data.toByteArray();
				}
				catch (Exception e) {
					System.out.println("Error decoding sample array: " + e.getMessage());
					e.printStackTrace(System.out);
				}
			}
			
			else if (this.type == 2) {
				Number n = ((Number) params.get("N"));
				this.n = n.floatValue();
				
//				Vector c0 = ((Vector) params.get("C0"));
				List c0 = ((List) params.get("C0"));
				if (c0 != null) {
					this.c0 = new float[c0.size()];
					for (int c = 0; c < this.c0.length; c++)
						this.c0[c] = ((Number) c0.get(c)).floatValue();
				}
				
//				Vector c1 = ((Vector) params.get("C1"));
				List c1 = ((List) params.get("C1"));
				if (c1 != null) {
					this.c1 = new float[c1.size()];
					for (int c = 0; c < this.c1.length; c++)
						this.c1[c] = ((Number) c1.get(c)).floatValue();
				}
			}
			
			else if (this.type == 3) {
//				Vector functions = ((Vector) params.get("Functions"));
				List functions = ((List) params.get("Functions"));
				this.functions = new Function[functions.size()];
				for (int f = 0; f < functions.size(); f++) {
					Object fObj = PdfParser.dereference(functions.get(f), objects);
					if (fObj instanceof PStream) {
						PStream fStream = ((PStream) fObj);
						this.functions[f] = new Function(fStream.params, fStream, objects);
					}
//					else this.functions[f] = new Function(((Hashtable) fObj), null, objects);
					else this.functions[f] = new Function(((Map) fObj), null, objects);
				}
				
//				Vector bounds = ((Vector) params.get("Bounds"));
				List bounds = ((List) params.get("Bounds"));
				this.bounds = new float[bounds.size()];
				for (int b = 0; b < this.bounds.length; b++)
					this.bounds[b] = ((Number) bounds.get(b)).floatValue();
				
//				Vector encode = ((Vector) params.get("Encode"));
				List encode = ((List) params.get("Encode"));
				this.encode = new float[encode.size() / 2][2];
				for (int e = 0; e < this.encode.length; e++) {
					this.encode[e][0] = ((Number) encode.get(e * 2)).floatValue();
					this.encode[e][1] = ((Number) encode.get((e * 2) + 1)).floatValue();
				}
			}
			
			else if (this.type == 4) try {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				PdfParser.decode(stream.params.get("Filter"), stream.bytes, stream.params, baos, objects);
				byte[] data = baos.toByteArray();
				int s = 0;
				while (s < data.length) {
					if (data[s++] == '{')
						break;
				}
				int e = data.length;
				while (e > s) {
					if (data[e-- - 1] == '}')
						break;
				}
				if (s < e) {
					this.data = new byte[e - s];
					System.arraycopy(data, s, this.data, 0, this.data.length);
				}
				else this.data = baos.toByteArray();
			}
			catch (Exception e) {
				System.out.println("Error decoding PostScript function: " + e.getMessage());
				e.printStackTrace(System.out);
			}
		}
		
		float[] evaluate(float[] x, String indent) {
			
			//	truncate input to domains
			for (int d = 0; d < this.domains.length; d++)
				x[d] = this.clipToRange(x[d], this.domains[d][0], this.domains[d][1]);
			
			if (this.type == 0) {
				
				//	encode input values
				float[] e = new float[x.length];
//				int[] li = new int[x.length];
//				int[] hi = new int[x.length];
				int[] si = new int[x.length];
				Arrays.fill(si, -1);
				for (int d = 0; d < x.length; d++) {
					e[d] = this.interpolate(x[d], this.domains[d][0], this.domains[d][1], this.encode[d][0], this.encode[d][1]);
					e[d] = this.clipToRange(e[d], 0, this.size[d]);
					
					//	compute indices of values present in samples
//					for (int v = 0; v < this.sampledValues[d].length; v++)
//						if (e[d] < this.sampledValues[d][v]) {
//							li[d] = (v-1;
//							hi[d] = v;
//							break;
//						}
//					if (hi[d] == this.sampledValues[d].length)
//						hi[d] = li[d];
					for (int v = 1; v < this.sampledValues[d].length; v++)
						if (e[d] < this.sampledValues[d][v]) {
							if ((e[d] - this.sampledValues[d][v - 1]) <= (this.sampledValues[d][v] - e[d]))
								si[d] = (v - 1);
							else si[d] = v;
							break;
						}
					if (si[d] == -1)
						si[d] = (this.sampledValues[d].length - 1);
					/* While simply snapping to the nearest sampled value is
					 * not exactly the interpolation specified in the Spec, it
					 * is hard to imagine how to interpolate on a function
					 * with, say, 2 inputs and 3 outputs. The Spec just states
					 * that interpolation is to be used, but does not provide a
					 * formula that would do this in a multi-dimensional case.
					 * Thus, snapping to the closest sampled value appears to
					 * be a sensible solution. The IcePDF implementation is
					 * outright crap in this regard ... */
				}
				
				//	do sample lookups
//				int[] ls = this.doSampleLookup(li);
//				int[] hs = this.doSampleLookup(hi);
				int[] sv = this.doSampleLookup(si);
				
				//	compute result values
				float[] y = new float[this.decode.length];
				for (int r = 0; r < this.ranges.length; r++) {
					y[r] = this.interpolate(sv[r], 0, this.maxSampleValue, this.decode[r][0], this.decode[r][1]);
					y[r] = this.clipToRange(y[r], this.ranges[r][0], this.ranges[r][1]);
				}
				return y;
			}
			
			else if (this.type == 2) {
				float[] y = new float[this.c0.length];
				for (int r = 0; r < y.length; r++) {
					y[r] = (this.c0[r] + ((float) (Math.pow(x[0], this.n) * (this.c1[r] - this.c0[r]))));
					if (this.ranges != null)
						y[r] = this.clipToRange(y[r], this.ranges[r][0], this.ranges[r][1]);
				}
				return y;
			}
			
			else if (this.type == 3) {
				float[] y = null;
				for (int b = 0; b < this.bounds.length; b++)
					if (x[0] < this.bounds[b]) {
						x[0] = this.interpolate(x[0], ((b == 0) ? this.domains[0][0] : this.bounds[b - 1]), this.bounds[b], this.encode[b][0], this.encode[b][1]);
						y = this.functions[b].evaluate(x, indent);
						break;
					}
				if (y == null) {
					x[0] = this.interpolate(x[0], ((this.bounds.length == 0) ? this.domains[0][0] : this.bounds[this.bounds.length - 1]), this.domains[0][1], this.encode[this.bounds.length][0], this.encode[this.bounds.length][1]);
					y = this.functions[this.bounds.length].evaluate(x, indent);
				}
				if (this.ranges != null) {
					for (int r = 0; r < y.length; r++)
						y[r] = this.clipToRange(y[r], this.ranges[r][0], this.ranges[r][1]);
				}
				return y;
			}
			
			else if (this.type == 4) {
//				Hashtable state = new Hashtable();
				Map state = new HashMap();
				LinkedList stack = new LinkedList();
				for (int d = 0; d < x.length; d++)
					stack.addLast(new Float(x[d]));
				if (indent != null)
					System.out.println(indent + "Program: " + new String(this.data));
				if (indent != null)
					System.out.println(indent + "Input stack: " + stack);
				try {
					PsParser.executePs(this.data, state, stack, ((indent == null) ? null : (indent + "  ")));
				}
				catch (IOException ioe) {
					System.out.println("Error running PostScript function: " + ioe.getMessage());
					ioe.printStackTrace(System.out);
				}
				if (indent != null)
					System.out.println(indent + "Result stack: " + stack);
				float[] y = new float[stack.size()];
				for (int r = 0; r < this.ranges.length; r++)
					y[r] = this.clipToRange(((Number) stack.removeFirst()).floatValue(), this.ranges[r][0], this.ranges[r][1]);
				return y;
			}
			
			//	never gonna happen, but Java don't know
			else return null;
		}
		
		private int[] doSampleLookup(int[] indices) {
			int sampleOffset = 0;
			int indexFactor = 1;
			for (int i = 0; i < indices.length; i++) {
				sampleOffset += (indexFactor * indices[i]);
				indexFactor *= this.size[i];
			}
			
			int bitOffset = (sampleOffset * this.bitsPerSample * this.ranges.length);
			int byteOffset = (bitOffset / 8);
			int initialBitShift = (bitOffset % 8);
			int[] sample = new int[this.ranges.length];
			
			//	less than 8 bits per sample value, need to do the bit pushing
			if (this.bitsPerSample < 8) {
				int bitsRemaining = (8 - initialBitShift);
				int bitData = this.data[byteOffset++];
				if (bitData < 0)
					bitData += 256;
				for (int s = 0; s < sample.length; s++) {
					while (bitsRemaining < this.bitsPerSample) {
						int nextByte = this.data[byteOffset++];
						bitData <<= 8;
						bitData |= nextByte;
						bitsRemaining += 8;
					}
					sample[s] = ((bitData >>> (bitsRemaining - this.bitsPerSample)) & this.maxSampleValue);
					bitsRemaining -= this.bitsPerSample;
				}
			}
			
			//	8 bits per sample value, simply read bytes
			else if (this.bitsPerSample == 8)
				for (int s = 0; s < sample.length; s++) {
					int b = this.data[byteOffset + s];
					if (b < 0)
						b += 256;
					sample[s] = b;
				}
			
			//	more than 8 bits per sample value, need to combine bytes
			else for (int s = 0; s < sample.length; s+= (this.bitsPerSample / 8)) {
				sample[s] = 0;
				for (int sb = 0; sb < (this.bitsPerSample / 8); sb++) {
					sample[s] <<= 8;
					int b = this.data[byteOffset + s + sb];
					if (b < 0)
						b += 256;
					sample[s] |= b;
				}
			}
			
			//	finally ...
			return sample;
		}
		
		private float clipToRange(float x, float min, float max) {
			if (x < min)
				return min;
			else if (max < x)
				return max;
			else return x;
		}
		
		private float interpolate(float x, float minX, float maxX, float minY, float maxY) {
			return (minY + ((x - minX) * ((maxY - minY) / (maxX - minX))));
		}
	}
}