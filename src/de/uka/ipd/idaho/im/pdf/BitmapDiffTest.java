///*
// * Copyright (c) 2006-, IPD Boehm, Universitaet Karlsruhe (TH) / KIT, by Guido Sautter
// * All rights reserved.
// *
// * Redistribution and use in source and binary forms, with or without
// * modification, are permitted provided that the following conditions are met:
// *
// *     * Redistributions of source code must retain the above copyright
// *       notice, this list of conditions and the following disclaimer.
// *     * Redistributions in binary form must reproduce the above copyright
// *       notice, this list of conditions and the following disclaimer in the
// *       documentation and/or other materials provided with the distribution.
// *     * Neither the name of the Universitaet Karlsruhe (TH) / KIT nor the
// *       names of its contributors may be used to endorse or promote products
// *       derived from this software without specific prior written permission.
// *
// * THIS SOFTWARE IS PROVIDED BY UNIVERSITAET KARLSRUHE (TH) / KIT AND CONTRIBUTORS 
// * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
// * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// * ARE DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE FOR ANY
// * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
// * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
// * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
// * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
// */
//package de.uka.ipd.idaho.im.pdf;
//
//import java.awt.image.BufferedImage;
//import java.io.BufferedInputStream;
//import java.io.ByteArrayOutputStream;
//import java.io.File;
//import java.io.FileInputStream;
//import java.io.IOException;
//import java.io.InputStream;
//import java.util.Set;
//import java.util.TreeSet;
//
//import javax.imageio.ImageIO;
//
///**
// * @author sautter
// *
// */
//public class BitmapDiffTest {
//	
//	/**
//	 * @param args
//	 */
//	public static void main(String[] args) throws Exception {
//		byte[] white = readBytes(new File("E:/Testdaten/PdfExtract/test.white.bmp"));
//		byte[] red = readBytes(new File("E:/Testdaten/PdfExtract/test.red.bmp"));
//		for (int i = 0; i < white.length; i++) {
//			if ((white[i] == red[i]) && (i >= 54))
//				continue;
//			System.out.println("Difference at " + Integer.toString(i, 16).toUpperCase() + ":");
//			System.out.println(" - white " + Integer.toString((white[i] & 0xFF), 16).toUpperCase());
//			System.out.println(" - red   " + Integer.toString((red[i] & 0xFF), 16).toUpperCase());
//		}
////		int dataOffset = 0;
////		for (int i = 0x0A; i <= 0x0D; i++) {
////			dataOffset >>>= 8;
////			dataOffset |= ((white[i] & 0xFF) << 24);
////		}
////		dataOffset |= (white[0x0D] & 0xFF);
////		dataOffset <<= 8;
////		dataOffset |= (white[0x0C] & 0xFF);
////		dataOffset <<= 8;
////		dataOffset |= (white[0x0B] & 0xFF);
////		dataOffset <<= 8;
////		dataOffset |= (white[0x0A] & 0xFF);
//		int dataOffset = readReverse(white, 0x0A, 4);
//		System.out.println("Data bytes start at " + Integer.toString(dataOffset, 16).toUpperCase() + " = " + dataOffset);
//		int dibLength = readReverse(white, 0x0E, 4);
//		int width;
//		int height;
//		int bpp;
//		if (dibLength == 12) {
//			width = readReverse(white, 0x12, 2);
//			height = readReverse(white, 0x14, 2);
//			bpp = readReverse(white, 0x18, 2);
//		}
//		else if (dibLength == 40) {
//			width = readReverse(white, 0x12, 4);
//			height = readReverse(white, 0x16, 4);
//			bpp = readReverse(white, 0x1C, 2);
//		}
//		else throw new RuntimeException("FUCK");
//		System.out.println("Bitmap is " + Integer.toString(width, 16).toUpperCase() + " x " + Integer.toString(height, 16).toUpperCase() + " = " + width + " x " + height);
//		System.out.println("Bits per pixel is " + Integer.toString(bpp, 16).toUpperCase() + " = " + bpp);
//		int bpl = ((bpp * width) / 8);
//		int pbpl = 0;
//		while (((bpl + pbpl) & 0x03) != 0)
//			pbpl++;
//		System.out.println("Bytes per line are " + bpl + " plus " + pbpl + " padding bytes");
//		int bpr = (bpl + pbpl);
//		byte[] data = new byte[bpl * height];
//		for (int l = 0; l < height; l++) {
//			int lo = ((height - l - 1) * bpr);
//			int o = (l * bpl);
//			System.arraycopy(white, lo, data, o, bpl);
//		}
//		for (int o = 0; o < data.length; o += 3) {
//			byte b = data[o];
//			data[o] = data[o+2];
//			data[o+2] = b;
//		}
//	}
//	public static byte[] bitmapToData(BufferedImage bi) throws IOException {
//		ByteArrayOutputStream baos = new ByteArrayOutputStream();
//		ImageIO.write(bi, "bmp", baos);
//		byte[] bitmap = baos.toByteArray();
//		int dataOffset = readReverse(bitmap, 0x0A, 4);
//		System.out.println("Data bytes start at " + Integer.toString(dataOffset, 16).toUpperCase() + " = " + dataOffset);
//		int dibLength = readReverse(bitmap, 0x0E, 4);
//		int width;
//		int height;
//		int bitsPerPixel;
//		if (dibLength == 12) {
//			width = readReverse(bitmap, 0x12, 2);
//			height = readReverse(bitmap, 0x14, 2);
//			bitsPerPixel = readReverse(bitmap, 0x18, 2);
//		}
//		else if (dibLength == 40) {
//			width = readReverse(bitmap, 0x12, 4);
//			height = readReverse(bitmap, 0x16, 4);
//			bitsPerPixel = readReverse(bitmap, 0x1C, 2);
//		}
//		else throw new RuntimeException("FUCK");
//		System.out.println("Bitmap is " + Integer.toString(width, 16).toUpperCase() + " x " + Integer.toString(height, 16).toUpperCase() + " = " + width + " x " + height);
//		System.out.println("Bits per pixel is " + Integer.toString(bitsPerPixel, 16).toUpperCase() + " = " + bitsPerPixel);
//		System.out.println("Bytes overall is " + Integer.toString(bitmap.length, 16).toUpperCase() + " = " + bitmap.length);
//		System.out.println("Requiring " + (width * height * bitsPerPixel) + " bits = " + (((width * height * bitsPerPixel) + 7) / 8) + " bytes");
//		int bitsPerLine = (bitsPerPixel * width);
//		int bytesPerLine = (((bitsPerPixel * width) + 7) / 8);
//		System.out.println("Requiring " + bitsPerLine + " bits per line = " + bytesPerLine + " bytes");
//		int padBytesPerLine = 0;
//		while (((bytesPerLine + padBytesPerLine) & 0x03) != 0)
//			padBytesPerLine++;
//		System.out.println("Bytes per line are " + bytesPerLine + " plus " + padBytesPerLine + " padding bytes");
//		int bytesPerRow = (bytesPerLine + padBytesPerLine);
//		System.out.println("Requiring " + bytesPerRow + " bytes per row = " + (bytesPerRow * height) + " bytes");
//		byte[] data = new byte[bytesPerLine * height];
//		for (int l = 0; l < height; l++) {
//			int lo = ((height - l - 1) * bytesPerRow);
//			int o = (l * bytesPerLine);
//			System.arraycopy(bitmap, lo, data, o, bytesPerLine);
//		}
//		if (bitsPerPixel == 24) // swap BGR to RGB (bitmap uses little-endian ...)
//			for (int o = 0; o < data.length; o += 3) {
//				byte b = data[o];
//				data[o] = data[o+2];
//				data[o+2] = b;
//			}
//		return data;
//	}
//	private static int readReverse(byte[] data, int offset, int bytes) {
//		int i = 0;
//		do {
//			i <<= 8;
//			i |= (data[offset + --bytes] & 0xFF);
//		}
//		while (bytes > 0);
//		return i;
//	}
//	private static byte[] readBytes(File file) throws IOException {
//		InputStream in = new BufferedInputStream(new FileInputStream(file));
//		ByteArrayOutputStream out = new ByteArrayOutputStream();
////		byte[] buffer = new byte[1024];
////		for (int r; (r = in.read(buffer, 0, buffer.length)) != -1;)
////			out.write(buffer, 0, r);
//		BufferedImage bi = ImageIO.read(in);
//		ImageIO.write(bi, "bmp", out);
//		in.close();
//		return out.toByteArray();
//	}
//}
