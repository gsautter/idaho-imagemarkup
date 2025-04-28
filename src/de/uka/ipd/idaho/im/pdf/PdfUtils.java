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
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.icepdf.core.io.BitStream;
import org.icepdf.core.io.SeekableByteArrayInputStream;
import org.icepdf.core.io.SeekableInputConstrainedWrapper;
import org.icepdf.core.pobjects.ImageStream;
import org.icepdf.core.pobjects.Resources;
import org.icepdf.core.pobjects.filters.ASCII85Decode;
import org.icepdf.core.pobjects.filters.ASCIIHexDecode;
import org.icepdf.core.pobjects.filters.CCITTFaxDecoder;
import org.icepdf.core.pobjects.filters.FlateDecode;
import org.icepdf.core.pobjects.filters.LZWDecode;
import org.icepdf.core.pobjects.filters.PredictorDecode;
import org.icepdf.core.pobjects.filters.RunLengthDecode;
import org.icepdf.core.util.Library;

import de.uka.ipd.idaho.im.pdf.test.PdfExtractorTest;

/**
 * Utility methods for PDF handling.
 * 
 * @author sautter
 */
class PdfUtils {
//	
//	static class PdfLineInputStream extends PeekInputStream {
//		PdfLineInputStream(InputStream in) throws IOException {
//			super(in, 2048);
//		}
//		//	returns a line of bytes, INCLUDING its terminal line break bytes
//		byte[] readLine() throws IOException {
//			if (this.peek() == -1)
//				return null;
//			ByteArrayOutputStream baos = new ByteArrayOutputStream();
//			for (int b; (b = this.peek()) != -1;) {
//				baos.write(this.read());
//				if (b == '\r') {
//					if (this.peek() == '\n')
//						baos.write(this.read());
//					break;
//				}
//				else if (b == '\n')
//					break;
//			}
//			return baos.toByteArray();
//		}
//	}
//	
//	static class PdfByteInputStream extends PeekInputStream {
//		PdfByteInputStream(ByteArrayInputStream in) throws IOException {
//			super(in, 16);
//		}
//		boolean skipSpaceCheckEnd() throws IOException {
//			int p = this.peek();
//			while ((p != -1) && (p <= 0x0020)) {
//				this.read();
//				p = this.peek();
//			}
//			return (p != -1);
//		}
//	}
	static class PdfByteInputStream extends InputStream {
		private byte[] data;
		private int start;
		private int end;
		private int pos;
		PdfByteInputStream(byte[] data) throws IOException {
			this(data, 0, data.length);
		}
		PdfByteInputStream(byte[] data, int start, int end) throws IOException {
			this.data = data;
			this.start = start;
			this.end = end;
			this.pos = this.start;
		}
		//	returns a line of bytes, INCLUDING its terminal line break bytes
		byte[] readLine() throws IOException {
			if (this.pos == this.end)
				return null;
			int leo = this.pos;
			while (leo < this.end) {
				if (this.data[leo] == '\r') {
					leo++;
					if ((leo < this.end) && (this.data[leo] == '\n'))
						leo++;
					break;
				}
				else if (this.data[leo] == '\n') {
					leo++;
					break;
				}
				else leo++;
			}
			byte[] line = Arrays.copyOfRange(this.data, this.pos, leo);
			this.pos = leo;
			return line;
		}
		int peek(byte[] buf) throws IOException {
			return this.peek(buf, 0, buf.length);
		}
		int peek(byte[] buf, int off, int len) throws IOException {
			if (this.pos == this.end)
				return -1;
			if (this.end < (this.pos + len))
				len = (this.end - this.pos);
			System.arraycopy(this.data, this.pos, buf, off, len);
			return len;
		}
		public int read(byte[] buf, int off, int len) throws IOException {
			if (this.pos == this.end)
				return -1;
			if (this.end < (this.pos + len))
				len = (this.end - this.pos);
			System.arraycopy(this.data, this.pos, buf, off, len);
			this.pos += len;
			return len;
		}
		public long skip(long n) throws IOException {
			int skip = ((int) n);
			if (this.end < (this.pos + skip))
				skip = (this.end - this.pos);
			this.pos += skip;
			return skip;
		}
		int peek() throws IOException {
			if (this.pos == this.end)
				return -1;
			else return (this.data[this.pos] & 0x000000FF);
		}
		int peek(int l) throws IOException {
			if ((this.pos + l) < this.end)
				return (this.data[this.pos + l] & 0x000000FF);
			else return -1;
		}
		public int read() throws IOException {
			if (this.pos == this.end)
				return -1;
			else return (this.data[this.pos++] & 0x000000FF);
		}
		int skipSpace() throws IOException {
			int sPos = this.pos;
			while ((this.pos < this.end) && ((this.data[this.pos] & 0x000000FF) <= 0x0020))
				this.pos++;
			return (this.pos - sPos);
		}
		boolean skipSpaceCheckEnd() throws IOException {
			while ((this.pos < this.end) && ((this.data[this.pos] & 0x000000FF) <= 0x0020))
				this.pos++;
			return (this.pos < this.end);
		}
		int bytesRead() {
			return (this.pos - this.start);
		}
		boolean checkEnd() {
			return (this.pos < this.end);
		}
	}
	
	static String toString(byte[] bytes, boolean stopAtLineEnd) {
		StringBuffer sb = new StringBuffer();
		for (int c = 0; c < bytes.length; c++) {
			if (stopAtLineEnd && ((bytes[c] == '\n') || (bytes[c] == '\r')))
				break;
			sb.append((char) bytes[c]);
		}
		return sb.toString();
	}
	
	static boolean matches(byte[] bytes, String regEx) {
		return toString(bytes, true).matches(regEx);
	}
	
	static boolean equals(byte[] bytes, String str) {
		if (bytes.length < str.length())
			return false;
		
		int actualByteCount = bytes.length;
		while ((actualByteCount > 0) && ((bytes[actualByteCount-1] == '\n') || (bytes[actualByteCount-1] == '\r') || (bytes[actualByteCount-1] == ' ')))
			actualByteCount--;
		if (actualByteCount != str.length())
			return false;
		
		for (int c = 0; c < str.length(); c++) {
			if (bytes[c] != str.charAt(c))
				return false;
		}
		
		return true;
	}
	
	static boolean startsWith(byte[] bytes, String str) {
		return startsWith(bytes, str, 0);
	}
	
	static boolean startsWith(byte[] bytes, String str, int offset) {
		if (bytes.length < (offset + str.length()))
			return false;
		
		for (int c = 0; c < str.length(); c++) {
			if (bytes[offset + c] != str.charAt(c))
				return false;
		}
		
		return true;
	}
	
	static int indexOf(byte[] bytes, String str) {
		return indexOf(bytes, str, 0, (bytes.length - str.length()));
	}
	
	static int indexOf(byte[] bytes, String str, int seekStart, int seekLimit) {
		if (bytes.length < (str.length() + seekStart))
			return -1;
		
		if (str.length() == 0)
			return 0;
		
		seekLimit = Math.min(seekLimit, (bytes.length - str.length()));
		char sc = str.charAt(0);
		for (int o = seekStart; o < seekLimit; o++) {
			if ((bytes[o] == sc) && startsWith(bytes, str, o))
				return o;
		}
		
		return -1;
	}
	
	static boolean endsWith(byte[] bytes, String str) {
		if (bytes.length < str.length())
			return false;
		
		int actualByteCount = bytes.length;
		while ((actualByteCount > 0) && ((bytes[actualByteCount-1] == '\n') || (bytes[actualByteCount-1] == '\r')))
			actualByteCount--;
		if (actualByteCount < str.length())
			return false;
		
		for (int c = 0; c < str.length(); c++) {
			if (bytes[c + (actualByteCount - str.length())] != str.charAt(c))
				return false;
		}
		
		return true;
	}
	
	static BufferedImage decodeOther(byte[] data, Map params, String filter, Object csObj, Library library, Resources resources, Map objects, boolean forceUseIcePdf, boolean decodeInverted, boolean isMask, boolean isSoftMask) throws IOException {
		if (!forceUseIcePdf) {
			BufferedImage bitmapBi = decodeBitmap(data, params, filter, csObj, library, objects, decodeInverted, isMask, isSoftMask);
			if (bitmapBi != null)
				return bitmapBi;
		}
		
		SeekableInputConstrainedWrapper streamInputWrapper = new SeekableInputConstrainedWrapper(new SeekableByteArrayInputStream(data), 0, data.length);
		ImageStream str = new ImageStream(library, new HashMap(params), streamInputWrapper);
		try {
			Color biBackgroundColor = getMaskImageBackgroundColor(params);
			BufferedImage bi = str.getImage(biBackgroundColor, resources);
			if (PdfExtractorTest.aimAtPage != -1) {
				if (bi != null)
					System.out.println(" ==> got image, size is " + bi.getWidth() + " x " + bi.getHeight());
				else System.out.println(" ==> Could not decode image");
			}
			return bi;
		}
		catch (Exception e) {
			System.out.println(" ==> Could not decode image: " + e.getMessage());
			e.printStackTrace(System.out);
			return null;
		}
	}
	
	static BufferedImage decodeBitmap(byte[] data, Map params, String filter, Object csObj, Library library, Map objects, boolean decodeInverted, boolean isMask, boolean isSoftMask) throws IOException {
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println(" ==> decoding bitmap");
		
		//	check whether or not we're dealing with an image mask
		Object isImageMaskObj = params.get("ImageMask");
//		boolean isImageMask = ((isImageMaskObj != null) && Boolean.parseBoolean(isImageMaskObj.toString()));
		boolean isImageMask = (isMask || ((isImageMaskObj != null) && Boolean.parseBoolean(isImageMaskObj.toString())));
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println("   - image mask is " + isImageMask + (isMask ? " (parameter)" : (" (for " + isImageMaskObj + ")")));
		
		//	test if we can handle this one (image mask or not)
		PdfColorSpace cs = (isImageMask ? PdfColorSpace.getImageMaskColorSpace(params, objects) : PdfColorSpace.getColorSpace(csObj, objects));
		if (cs == null)
			return null;
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println("   - color space is " + cs.name + " (" + cs.numComponents + " components)");
		
		//	get and TODO_not observe decode parameter dictionary ==> looks like wrecking more havoc than doing good
		//	TODO interpret array of decode parameters as dedicated to an equally long sequence of decoding filters (once we have an example ...)
		Object decodeParamsObj = PdfParser.dereference(params.get("DecodeParms"), objects);
		Map decodeParams;
		if (decodeParamsObj instanceof Map)
			decodeParams = ((Map) decodeParamsObj);
		else if (decodeParamsObj instanceof List) {
			decodeParams = new HashMap();
			for (int d = 0; d < ((List) decodeParamsObj).size(); d++) {
				Object subDecodeParamsObj = PdfParser.dereference(((List) decodeParamsObj).get(d), objects);
				if (subDecodeParamsObj instanceof Map)
					decodeParams.putAll((Map) subDecodeParamsObj);
			}
		}
		else decodeParams = new HashMap();
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println("   - decode parameters are " + decodeParams);
		
		//	get image size
		int width;
//		if (decodeParams == null) {
			Object widthObj = params.get("Width");
			if (widthObj instanceof Number)
				width = ((Number) widthObj).intValue();
			else return null;
//		}
//		else {
//			Object columnsObj = decodeParams.get("Columns");
//			if (columnsObj instanceof Number)
//				width = ((Number) columnsObj).intValue();
//			else return null;
//		}
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println("   - width is " + width);
		
		int height;
		Object heightObj = params.get("Height");
		if (heightObj instanceof Number)
			height = ((Number) heightObj).intValue();
		else return null;
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println("   - height is " + height);
		
		//	get component depth
		int bitsPerComponent;
//		if (decodeParams == null) {
			Object bcpObj = params.get("BitsPerComponent");
			if (bcpObj instanceof Number)
				bitsPerComponent = ((Number) bcpObj).intValue();
			else return null;
//		}
//		else {
//			Object bcpObj = decodeParams.get("BitsPerComponent");
//			if (bcpObj instanceof Number)
//				bitsPerComponent = ((Number) bcpObj).intValue();
//			else return null;
//		}
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println("   - bits per component is " + bitsPerComponent);
		
		//	prepare decoding stream
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println("   - filter is " + filter);
		InputStream biIn;
		if ("FlateDecode".equals(filter) || "Fl".equals(filter)) {
			Number predictor = ((Number) PdfParser.dereference(decodeParams.get("Predictor"), objects));
			if ((predictor == null) || (((Number) predictor).intValue() == 1 /* 1 means "no predictor" */) || (((Number) predictor).intValue() == 2 /* handled by flate decoder */))
				biIn = new BufferedInputStream(new FlateDecode(library, new HashMap(params), new ByteArrayInputStream(data)));
			else biIn = new BufferedInputStream(new PredictorDecode(new FlateDecode(library, new HashMap(params), new ByteArrayInputStream(data)), library, new HashMap(params)));
		}
		else if ("LZWDecode".equals(filter) || "LZW".equals(filter)) {
			Number predictor = ((Number) PdfParser.dereference(decodeParams.get("Predictor"), objects));
			if ((predictor == null) || (((Number) predictor).intValue() == 1 /* 1 means "no predictor" */))
				biIn = new BufferedInputStream(new LZWDecode(new BitStream(new ByteArrayInputStream(data)), library, new HashMap(params)));
			else biIn = new BufferedInputStream(new PredictorDecode(new LZWDecode(new BitStream(new ByteArrayInputStream(data)), library, new HashMap(params)), library, new HashMap(params)));
		}
		else if ("ASCII85Decode".equals(filter) || "A85".equals(filter))
			biIn = new BufferedInputStream(new ASCII85Decode(new ByteArrayInputStream(data)));
		else if ("ASCIIHexDecode".equals(filter) || "AHx".equals(filter))
			biIn = new BufferedInputStream(new ASCIIHexDecode(new ByteArrayInputStream(data)));
		else if ("RunLengthDecode".equals(filter) || "RL".equals(filter))
			biIn = new BufferedInputStream(new RunLengthDecode(new ByteArrayInputStream(data)));
		else if ("CCITTFaxDecode".equals(filter) || "CCF".equals(filter)) {
//			System.out.println("   - CCITTFaxDecoding " + Arrays.toString(stream));
			byte[] dStream = new byte[((width + 7) / 8) * height];
//			System.out.println("     - expecting " + dStream.length + " bytes of result");
			Number k = ((decodeParams == null) ? null : ((Number) PdfParser.dereference(decodeParams.get("K"), objects)));
			if (k == null)
				k = new Integer(0);
			Number columns = ((decodeParams == null) ? null : ((Number) PdfParser.dereference(decodeParams.get("Columns"), objects)));
//			System.out.println("     - K is " + k);
			CCITTFaxDecoder decoder = new CCITTFaxDecoder(1, ((columns == null) ? width : columns.intValue()), height);
			Object byteAlign = ((decodeParams == null) ? null : decodeParams.get("EncodedByteAlign"));
			if ((byteAlign != null) && "true".equals(byteAlign.toString()))
				decoder.setAlign(true);
			if (k.intValue() == 0)
				decoder.decodeT41D(dStream, data, 0, height);
			else if (k.intValue() < 0)
				decoder.decodeT6(dStream, data, 0, height);
			else decoder.decodeT42D(dStream, data, 0, height);
			Object inverted = ((decodeParams == null) ? null : decodeParams.get("BlackIs1"));
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println("     - inversion indicator is " + inverted);
			if ((inverted == null) || !"true".equals(inverted.toString())) {
				for (int b = 0; b < dStream.length; b++)
					dStream[b] ^= 0xFF; // TODO observe whether or not we need this (appears so initially)
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println("       ==> bit stream inverted");
			}
			//	TODO maybe better try and use black-on-white correction, and _after_ image assembly from parts
			biIn = new ByteArrayInputStream(dStream);
		}
		else if (filter == null)
			biIn = new ByteArrayInputStream(data);
		//	TODO observe other encodings (as we encounter them)
		else biIn = new ByteArrayInputStream(data);
		
		//	create bit masks
		int colorComponents = cs.numComponents;
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println("   - color components are " + colorComponents + " in bitmap, " + cs.getColorNumComponents() + " in colors");
		int componentBitMask = 0;
		for (int b = 0; b < bitsPerComponent; b++) {
			componentBitMask <<= 1;
			componentBitMask |= 1;
		}
		if (PdfExtractorTest.aimAtPage != -1)
			System.out.println("   - component bit mask is " + Long.toString(componentBitMask, 16));
		int bitsPerPixel = (colorComponents * bitsPerComponent);
		long pixelBitMask = 0;
		for (int b = 0; b < bitsPerPixel; b++) {
			pixelBitMask <<= 1;
			pixelBitMask |= 1;
		}
		if (PdfExtractorTest.aimAtPage != -1) {
			System.out.println("   - bits per pixel is " + bitsPerPixel);
			System.out.println("   - pixel bit mask is " + Long.toString(pixelBitMask, 16));
		}
		
		//	determine image type
		int biType;
		if (isSoftMask)
			biType = BufferedImage.TYPE_BYTE_GRAY;
		else if (bitsPerPixel == 1)
			biType = BufferedImage.TYPE_BYTE_BINARY;
		else if (cs.getColorNumComponents() == 1)
			biType = BufferedImage.TYPE_BYTE_GRAY;
		else biType = BufferedImage.TYPE_INT_ARGB;
		
		//	fill image
		LinkedList colorDecodeStack = new LinkedList();
		ArrayList topPixelRow = null;
		if (PdfExtractorTest.aimAtPage != -1) {
			colorDecodeStack.addFirst("DEBUG_COLORS");
			topPixelRow = new ArrayList();
		}
		BufferedImage bi = new BufferedImage(width, height, biType);
		for (int r = 0; r < height; r++) {
			int bitsRemaining = 0;
			long bitData = 0;
			for (int c = 0; c < width; c++) {
				
				//	make sure we have enough bits left in buffer
				while (bitsRemaining < bitsPerPixel) {
					int nextByte = biIn.read();
					bitData <<= 8;
					bitData |= nextByte;
					bitsRemaining += 8;
				}
				
				//	get component values for pixel
				long pixelBits = ((bitData >>> (bitsRemaining - bitsPerPixel)) & pixelBitMask);
				bitsRemaining -= bitsPerPixel;
				if ((r == 0) && (topPixelRow != null))
					topPixelRow.add(Long.toString(pixelBits, 16).toUpperCase());
				
				//	extract component values
				for (int cc = 0; cc < colorComponents; cc++) {
					long rccb = ((pixelBits >>> ((colorComponents - cc - 1) * bitsPerComponent)) & componentBitMask);
					long ccb = rccb;
					for (int b = bitsPerComponent; b < 8; b += bitsPerComponent) {
						ccb <<= bitsPerComponent;
						ccb |= rccb;
					}
					colorDecodeStack.addLast(new Float(((float) ccb) / 255));
				}
				
				//	get color
				Color color = cs.decodeColor(colorDecodeStack, ((PdfExtractorTest.aimAtPage != -1) ? "" : null));
				if (decodeInverted && !isImageMask) // mask image color space does inversion internally
					color = new Color((255 - color.getRed()), (255 - color.getGreen()), (255 - color.getBlue()), (255 - color.getAlpha()));
				
				//	set pixel
				bi.setRGB(c, r, color.getRGB());
			}
		}
		
		//	which colors did we use?
		if (PdfExtractorTest.aimAtPage != -1)
			cs.printStats();
		
		//	finally ...
		biIn.close();
		return bi;
	}
	
	static Color getMaskImageBackgroundColor(Map params) {
		/* if we have a mask image
		 * Decoding [0, 1] ==> 0 means paint-through (default), requires black background
		 * Decoding [1, 0] ==> 1 means paint-through, requires white background WRONG, black as well */
		Object imObj = params.get("ImageMask");
		if (imObj == null) {
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" - image mask is null");
			return Color.WHITE;
		}
		else {
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println(" - image mask is " + imObj.getClass().getName() + " - " + imObj);
			Object dObj = params.get("Decode");
			if ((dObj instanceof List) && (((List) dObj).size() != 0)) {
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println(" - decoder is " + dObj.getClass().getName() + " - " + dObj);
				Object ptObj = ((List) dObj).get(0);
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println(" - paint-through is " + ptObj.getClass().getName() + " - " + ptObj);
				if (ptObj instanceof Number) {
					if (((Number) ptObj).intValue() == 0)
						return Color.BLACK;
					else return Color.BLACK;
				}
				else return Color.BLACK;
			}
			else {
				if (dObj == null) {
					if (PdfExtractorTest.aimAtPage != -1)
						System.out.println(" - decoder is null");
				}
				else if (PdfExtractorTest.aimAtPage != -1)
					System.out.println(" - decoder is " + dObj.getClass().getName() + " - " + dObj);
				return Color.BLACK;
			}
		}
	}
}
