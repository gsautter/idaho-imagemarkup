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
 *     * Neither the name of the Universität Karlsruhe (TH) nor the
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import de.uka.ipd.idaho.easyIO.streams.PeekInputStream;

/**
 * Utility methods for PDF handling.
 * 
 * @author sautter
 */
class PdfUtils {
	
	static class PdfLineInputStream extends PeekInputStream {
		PdfLineInputStream(InputStream in) throws IOException {
			super(in, 2048);
		}
		//	returns a line of bytes, INCLUDING its terminal line break bytes
		byte[] readLine() throws IOException {
			if (this.peek() == -1)
				return null;
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			for (int b; (b = this.peek()) != -1;) {
				baos.write(this.read());
				if (b == '\r') {
					if (this.peek() == '\n')
						baos.write(this.read());
					break;
				}
				else if (b == '\n')
					break;
			}
			return baos.toByteArray();
		}
	}
	
	static class PdfByteInputStream extends PeekInputStream {
		PdfByteInputStream(ByteArrayInputStream in) throws IOException {
			super(in, 16);
		}
		boolean skipSpaceCheckEnd() throws IOException {
			int p = this.peek();
			while ((p != -1) && (p < 33)) {
				this.read();
				p = this.peek();
			}
			return (p != -1);
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
}
