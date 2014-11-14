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

import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Vector;

import org.icepdf.core.io.BitStream;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.filters.ASCII85Decode;
import org.icepdf.core.pobjects.filters.ASCIIHexDecode;
import org.icepdf.core.pobjects.filters.FlateDecode;
import org.icepdf.core.pobjects.filters.LZWDecode;
import org.icepdf.core.pobjects.filters.RunLengthDecode;
import org.icepdf.core.util.Library;

import de.uka.ipd.idaho.gamta.Gamta;
import de.uka.ipd.idaho.gamta.TokenSequence;
import de.uka.ipd.idaho.gamta.Tokenizer;
import de.uka.ipd.idaho.gamta.util.ProgressMonitor;
import de.uka.ipd.idaho.im.pdf.PdfUtils.PdfByteInputStream;
import de.uka.ipd.idaho.im.pdf.PdfUtils.PdfLineInputStream;
import de.uka.ipd.idaho.stringUtils.StringUtils;

/**
 * PDF parsing utility, plainly extract PDF objects and returns them in a Map
 * with their IDs and generation numbers as keys. To retrieve an object, best
 * use the dereference() method.<br>
 * In addition, this class provides the means to extract text from the content
 * streams of pages in PDF documents, together with word bounding boxes and
 * basic layout information like font name and size, and bold and italic flags.
 * 
 * @author sautter
 */
public class PdfParser {
	
	/**
	 * Parse a binary PDF file into individual objects. The returned map holds
	 * the parsed objects, the keys being the object numbers together with the
	 * generation numbers.
	 * @param bytes the binary PDF file to parse
	 * @return a Map holding the objects parsed from the PDF file
	 * @throws IOException
	 */
	public static HashMap getObjects(byte[] bytes) throws IOException {
		PdfLineInputStream lis = new PdfLineInputStream(new ByteArrayInputStream(bytes));
		HashMap objects = new HashMap() {
			public Object put(Object key, Object value) {
				System.out.println("PDF Object map: " + key + " mapped to " + value);
				return super.put(key, value);
			}
		};
		byte[] line;
		while ((line = lis.readLine()) != null) {
			if (PdfUtils.matches(line, "[1-9][0-9]*\\s[0-9]+\\sobj.*")) {
				String objId = PdfUtils.toString(line, true).trim();
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				if (!objId.endsWith("obj")) {
					objId = objId.substring(0, (objId.indexOf("obj") + "obj".length()));
					baos.write(line, objId.length(), (line.length - objId.length()));
				}
				objId = objId.substring(0, (objId.length() - "obj".length())).trim();
				
				byte[] objLine;
				while ((objLine = lis.readLine()) != null) {
					if (PdfUtils.equals(objLine, "endobj"))
						break;
					baos.write(objLine);
				}
				
				Object obj = parseObject(new PdfByteInputStream(new ByteArrayInputStream(baos.toByteArray())));
				objects.put(objId, obj);
				
				//	decode object streams
				if (obj instanceof PStream) {
					PStream sObj = ((PStream) obj);
					Object type = sObj.params.get("Type");
					if ((type != null) && "ObjStm".equals(type.toString()))
						decodeObjectStream(sObj, objects, false);
				}
			}
		}
		
		dereferenceObjects(objects);
		return objects;
	}
	
	static void decodeObjectStream(PStream objStream, HashMap objects, boolean forInfo) throws IOException {
		Object fObj = objStream.params.get("First");
		int f = ((fObj == null) ? 0 : Integer.parseInt(fObj.toString()));
		Object filter = objStream.params.get("Filter");
		if (filter == null)
			filter = "FlateDecode";
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		decode(filter, objStream.bytes, objStream.params, baos, objects);
		byte[] decodedStream = baos.toByteArray();
		if (forInfo) {
			System.out.println("Decoded: " + new String(decodedStream));
			return;
		}
		//	TODO_ne work on bytes UNNECESSARY, as offsets don't change, and actual decoding works on bytes further down the road
		String[] sObjIdsAndOffsets = (new String(decodedStream, 0, f)).split("\\s++");
		ArrayList sObjList = new ArrayList();
		for (int o = 0; o < sObjIdsAndOffsets.length; o += 2)
			sObjList.add(new PStreamObject(Integer.parseInt(sObjIdsAndOffsets[o]), Integer.parseInt(sObjIdsAndOffsets[o+1])));
		PStreamObject[] sObjs = ((PStreamObject[]) sObjList.toArray(new PStreamObject[sObjList.size()]));
		for (int o = 0; o < sObjs.length; o++) {
			int s = (sObjs[o].offset + f);
			int e = (((o+1) == sObjs.length) ? decodedStream.length : (sObjs[o+1].offset + f));
			byte[] sObjBytes = new byte[e-s];
			System.arraycopy(decodedStream, s, sObjBytes, 0, sObjBytes.length);
			Object sObj = parseObject(new PdfByteInputStream(new ByteArrayInputStream(sObjBytes)));
			objects.put(sObjs[o].objId, sObj);
		}
	}
	
	/**
	 * Decode a byte stream. The decoding algorithm to use is selected based on
	 * the filter argument. This method understands all the abbreviated filter
	 * names allowed in the PDF standard.
	 * @param filter the name of the filter
	 * @param stream the raw stream bytes to decode
	 * @param params the stream parameters
	 * @param baos the output stream to store the decoded bytes in
	 * @throws IOException
	 */
	public static void decode(Object filter, byte[] stream, Hashtable params, ByteArrayOutputStream baos, HashMap objects) throws IOException {
		
		//	make sure we got all the input we need, pad with carriage returns if necessary
		Object length = getObject(params, "Length", objects);
		if ((length instanceof Number) && (stream.length < ((Number) length).intValue())) {
			byte[] padStream = new byte[((Number) length).intValue()];
			System.arraycopy(stream, 0, padStream, 0, stream.length);
			for (int b = stream.length; b < padStream.length; b++)
				padStream[b] = '\r';
			stream = padStream;
		}
		
		//	process multi-level filters (filter object is Vector)
		if (filter instanceof Vector) {
			for (Iterator fit = ((Vector) filter).iterator(); fit.hasNext();) {
				Object fObj = fit.next();
				ByteArrayOutputStream fBaos = new ByteArrayOutputStream();
				decode(fObj, stream, params, fBaos, objects);
				stream = fBaos.toByteArray();
			}
			return;
		}
		
		//	get filter name
		String f = null;
		if (filter != null) {
			f = filter.toString();
			if (f.startsWith("/"))
				f = f.substring(1);
		}
		
		//	TODO observe other decodings (as they occur)
		
		//	process individual filters
		if ("FlateDecode".equals(f) || "Fl".equals(f))
			decodeFlate(stream, params, baos);
		else if ("LZWDecode".equals(f) || "LZW".equals(f))
			decodeLzw(stream, params, baos);
		else if ("RunLengthDecode".equals(f) || "RL".equals(f))
			decodeRunLength(stream, params, baos);
		else if ("ASCIIHexDecode".equals(f) || "AHx".equals(f))
			decodeAsciiHex(stream, params, baos);
		else if ("ASCII85Decode".equals(f) || "A85".equals(f))
			decodeAscii85(stream, params, baos);
//		else if ("CCITTFaxDecode".equals(filter) || "CCF".equals(filter))
//			decodeCcittFax(stream, params, baos);
//		else if ("DCTDecode".equals(filter) || "DCT".equals(filter))
//			decodeDct(stream, params, baos);
		else baos.write(stream);
	}
	
	private static void decodeLzw(byte[] stream, Hashtable params, ByteArrayOutputStream baos) throws IOException {
		LZWDecode d = new LZWDecode(new BitStream(new ByteArrayInputStream(stream)), new Library(), params);
		byte[] buffer = new byte[1024];
		int read;
		while ((read = d.read(buffer)) != -1)
			baos.write(buffer, 0, read);
	}
	
	private static void decodeFlate(byte[] stream, Hashtable params, ByteArrayOutputStream baos) throws IOException {
		FlateDecode d = new FlateDecode(new Library(), params, new ByteArrayInputStream(stream));
		byte[] buffer = new byte[1024];
		int read;
		while ((read = d.read(buffer)) != -1)
			baos.write(buffer, 0, read);
	}
		
	private static void decodeAscii85(byte[] stream, Hashtable params, ByteArrayOutputStream baos) throws IOException {
		ASCII85Decode d = new ASCII85Decode(new ByteArrayInputStream(stream));
		byte[] buffer = new byte[1024];
		int read;
		while ((read = d.read(buffer)) != -1)
			baos.write(buffer, 0, read);
	}
	
	private static void decodeAsciiHex(byte[] stream, Hashtable params, ByteArrayOutputStream baos) throws IOException {
		ASCIIHexDecode d = new ASCIIHexDecode(new ByteArrayInputStream(stream));
		byte[] buffer = new byte[1024];
		int read;
		while ((read = d.read(buffer)) != -1)
			baos.write(buffer, 0, read);
	}
	
//	private static void decodeCcittFax(byte[] stream, Hashtable params, ByteArrayOutputStream baos) throws IOException {
//		CCITTFaxDecoder d = new CCITTFaxDecoder(new Library(), params, new ByteArrayInputStream(stream));
//		byte[] buffer = new byte[1024];
//		int read;
//		while ((read = d.read(buffer)) != -1)
//			baos.write(buffer, 0, read);
//	}
//	
	private static void decodeRunLength(byte[] stream, Hashtable params, ByteArrayOutputStream baos) throws IOException {
		RunLengthDecode rld = new RunLengthDecode(new ByteArrayInputStream(stream));
		byte[] buffer = new byte[1024];
		int read;
		while ((read = rld.read(buffer)) != -1)
			baos.write(buffer, 0, read);
	}
	
	private static class PStreamObject {
		final String objId;
		final int offset;
		PStreamObject(int objId, int offset) {
			this.objId = (objId + " 0");
			this.offset = offset;
		}
	}
	
	private static final boolean DEBUG_HANDLE_PDF_OBJECTS = false;
	
	/**
	 * Dereference an object. If the object is not a reference, this method
	 * simply returns it. If it is a reference, this method resolves it by means
	 * of the object map.
	 * @param obj the object to dereference
	 * @param objects the object map to use for dereferencing
	 * @return the dereferences object.
	 */
	public static Object dereference(Object obj, HashMap objects) {
		while (obj instanceof Reference)
			obj = objects.get(((Reference) obj).getObjectNumber() + " " + ((Reference) obj).getGenerationNumber());
		return obj;
	}
	
	private static void dereferenceObjects(HashMap objects) {
		ArrayList ids = new ArrayList(objects.keySet());
		for (Iterator idit = ids.iterator(); idit.hasNext();) {
			Object id = idit.next();
			Object obj = objects.get(id);
			if (obj instanceof Hashtable)
				dereferenceObjects(((Hashtable) obj), objects);
			else if (obj instanceof Vector)
				dereferenceObjects(((Vector) obj), objects);
			else if (obj instanceof PStream)
				dereferenceObjects(((PStream) obj).params, objects);
		}
	}
	
	private static void dereferenceObjects(Hashtable dict, HashMap objects) {
		ArrayList ids = new ArrayList(dict.keySet());
		for (Iterator idit = ids.iterator(); idit.hasNext();) {
			Object id = idit.next();
			Object obj = dict.get(id);
			if (obj instanceof Reference) {
				Reference ref = ((Reference) obj);
				String refId = (ref.getObjectNumber() + " " + ref.getGenerationNumber());
				if (DEBUG_HANDLE_PDF_OBJECTS) System.out.println("Dereferencing " + refId + " R");
				obj = objects.get(refId);
				if (obj == null) {
					if (DEBUG_HANDLE_PDF_OBJECTS) System.out.println("  Invalid reference " + refId + " R, ignoring");
					continue;
				}
				if (DEBUG_HANDLE_PDF_OBJECTS) System.out.println("  Dereferenced " + refId + " R to " + obj);
				if (obj instanceof PStream) {
					dereferenceObjects(((PStream) obj).params, objects);
					continue;
				}
				else if (obj instanceof Hashtable) {
					continue;
				}
				else if (obj instanceof Vector) {
					continue;
				}
				dict.put(id, obj);
			}
		}
	}
	
	private static void dereferenceObjects(Vector array, HashMap objects) {
		for (int i = 0; i < array.size(); i++) {
			Object obj = array.get(i);
			if (obj instanceof Reference) {
				Reference ref = ((Reference) obj);
				String refId = (ref.getObjectNumber() + " " + ref.getGenerationNumber());
				if (DEBUG_HANDLE_PDF_OBJECTS) System.out.println("Dereferencing " + refId + " R");
				obj = objects.get(refId);
				if (obj == null) {
					if (DEBUG_HANDLE_PDF_OBJECTS) System.out.println("  Invalid reference " + refId + " R, ignoring");
					continue;
				}
				if (DEBUG_HANDLE_PDF_OBJECTS) System.out.println("  Dereferenced " + refId + " R to " + obj);
				if (obj instanceof PStream) {
					dereferenceObjects(((PStream) obj).params, objects);
					continue;
				}
				else if (obj instanceof Hashtable) {
					continue;
				}
				else if (obj instanceof Vector) {
					continue;
				}
				array.set(i, obj);
			}
		}
	}
	
	private static final boolean DEBUG_PARSE_PDF = false;
	
	/**
	 * Retrieve an object from a Hashtable based on its name and dereference it
	 * against the object map.
	 * @param data the Hashtable to retrieve the object from
	 * @param name the name of the sought object
	 * @param objects the object map to use for dereferencing
	 * @return the sought object
	 */
	public static Object getObject(Hashtable data, String name, HashMap objects) {
		return dereference(data.get(name), objects);
	}
	
	private static Object parseObject(PdfByteInputStream bytes) throws IOException {
		Object obj = cropNext(bytes, false, false);
		
		//	check for subsequent stream
		if (!bytes.skipSpaceCheckEnd())
			return obj;
		
		//	must be parameters ...
		if (!(obj instanceof Hashtable))
			return obj;
		
		//	read stream start
		PdfLineInputStream lis = new PdfLineInputStream(bytes);
		byte[] marker = lis.readLine();
		if ((marker == null) || !PdfUtils.equals(marker, "stream"))
			return obj;
		
		byte[] streamLine;
		byte[] lastStreamLine = null;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		while ((streamLine = lis.readLine()) != null) {
			if (PdfUtils.equals(streamLine, "endstream")) {
				if (lastStreamLine != null) {
					int endCut;
					if (lastStreamLine.length < 2)
						endCut = 1;
					else if (lastStreamLine[lastStreamLine.length - 2] == '\n')
						endCut = 2;
					else if (lastStreamLine[lastStreamLine.length - 2] == '\r')
						endCut = 2;
					else endCut = 1;
					baos.write(lastStreamLine, 0, (lastStreamLine.length - endCut));
				}
				break;
			}
			else if (PdfUtils.endsWith(streamLine, "endstream")) {
				if (lastStreamLine != null)
					baos.write(lastStreamLine, 0, lastStreamLine.length);
				int dataByteCount = streamLine.length;
				while (!PdfUtils.startsWith(streamLine, "endstream", dataByteCount))
					dataByteCount--;
				baos.write(streamLine, 0, dataByteCount);
				break;
			}
			if (lastStreamLine != null)
				baos.write(lastStreamLine);
			lastStreamLine = streamLine;
		}
		
		return new PStream(((Hashtable) obj), baos.toByteArray());
	}
	
	static Object cropNext(PdfByteInputStream bytes, boolean expectPTags) throws IOException {
		return cropNext(bytes, expectPTags, false);
	}
	static Object cropNext(PdfByteInputStream bytes, boolean expectPTags, boolean hexIsHex2) throws IOException {
		if (!bytes.skipSpaceCheckEnd())
			return null;
		
		//	skip any comment
		while (bytes.peek() == '%') {
			skipComment(bytes);
			if (!bytes.skipSpaceCheckEnd())
				return null;
		}
		
		//	hex string, dictionary, or stream parameters
		if (bytes.peek() == '<') {
			bytes.read();
			if (bytes.peek() == '<') {
				bytes.read();
				return cropHashtable(bytes, expectPTags, hexIsHex2);
			}
			else return cropHexString(bytes, hexIsHex2);
		}
		
		//	string
		else if (bytes.peek() == '(') {
			bytes.read();
			return cropString(bytes);
		}
		
		//	array
		else if (bytes.peek() == '[') {
			bytes.read();
			return cropArray(bytes, expectPTags, hexIsHex2);
		}
		
		//	name
		else if (bytes.peek() == '/') {
			return cropName(bytes);
		}
		
		//	boolean, number, null, or reference
		else {
			byte[] lookahead = new byte[16]; // 16 bytes is reasonable for detecting references, as object number and generation number combined will rarely exceed 13 digits
			int l = bytes.peek(lookahead);
			if (l == -1)
				return null;
			if (DEBUG_PARSE_PDF) System.out.println("Got lookahead: " + new String(lookahead));
			
			//	reference
			if (PdfUtils.matches(lookahead, "[0-9]+\\s+[0-9]+\\s+R.*")) {
				if (DEBUG_PARSE_PDF) System.out.println(" ==> reference");
				StringBuffer objNumber = new StringBuffer();
				while (bytes.peek() > 32)
					objNumber.append((char) bytes.read());
				bytes.skipSpace();
				StringBuffer genNumber = new StringBuffer();
				while (bytes.peek() > 32)
					genNumber.append((char) bytes.read());
				bytes.skipSpace();
				bytes.read();
				if (DEBUG_PARSE_PDF) System.out.println("Got reference: " + objNumber + " " + genNumber + " R");
				return new Reference(new Integer(objNumber.toString()), new Integer(genNumber.toString()));
			}
			
			//	inline image
			else if (expectPTags && PdfUtils.matches(lookahead, "BI(\\s+.*)?")) {
				if (DEBUG_PARSE_PDF) System.out.println(" ==> inline image");
				StringBuffer image = new StringBuffer();
				while (bytes.peek() != -1) {
					while ((bytes.peek() != -1) && (bytes.peek() != 'E'))
						image.append((char) bytes.read());
					if (bytes.peek() == 'E') {
						image.append((char) bytes.read());
						if (bytes.peek() == 'I') {
							image.append((char) bytes.read());
							return new PtTag(image.toString());
						}
						else image.append((char) bytes.read());
					}
				}
				return new PtTag(image.toString());
			}
			
			//	boolean, number, null, or page content tag
			else {
				if (DEBUG_PARSE_PDF) System.out.println(" ==> other object");
				StringBuffer valueBuffer = new StringBuffer();
				while ((bytes.peek() > 32) && ("%()<>[]{}/#".indexOf((char) bytes.peek()) == -1))
					valueBuffer.append((char) bytes.read());
				String value = valueBuffer.toString();
				if (DEBUG_PARSE_PDF) System.out.println("Got value: " + value);
				if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value))
					return new Boolean(value);
				else if ("null".equalsIgnoreCase(value))
					return NULL;
				else if (value.matches("\\-?[0-9]++"))
					return new Integer(value);
				else if (value.matches("\\-?[0-9]*+\\.[0-9]++"))
					return new Double(value);
				else if (expectPTags)
					return new PtTag(value);
				else return new Double(value);
			}
		}
	}
	private static final Object NULL = new Object();
	
	private static void skipComment(PdfByteInputStream bytes) throws IOException {
		if (DEBUG_PARSE_PDF) System.out.println("Skipping comment");
		StringBuffer comment = new StringBuffer();
		while (bytes.peek() != -1) {
			if ((bytes.peek() == '\r') || bytes.peek() == '\n')
				break;
			comment.append((char) bytes.read());
		}
		while ((bytes.peek() == '\r') || (bytes.peek() == '\n'))
			bytes.read();
		if (DEBUG_PARSE_PDF) System.out.println(" --> " + comment.toString());
	}
	
	private static Name cropName(PdfByteInputStream bytes) throws IOException {
		if (DEBUG_PARSE_PDF) System.out.println("Cropping name");
		if (bytes.peek() == '/')
			bytes.read();
		StringBuffer name = new StringBuffer();
		while (true) {
			if ((bytes.peek() < 33) || ("%()<>[]{}/".indexOf((char) bytes.peek()) != -1))
				break;
			if (bytes.peek() == '#') {
				bytes.read();
				int hb = bytes.read();
				int lb = bytes.read();
				if (checkHexByte(hb) && checkHexByte(lb))
					name.append((char) translateHexBytes(hb, lb));
				else throw new IOException("Invalid escaped character #" + ((char) hb) + "" + ((char) lb));
			}
			else name.append((char) bytes.read());
		}
		if (DEBUG_PARSE_PDF) System.out.println(" --> " + name.toString());
		return new Name(name.toString());
	}
	
	private static boolean checkHexByte(int b) {
		if ((b <= '9') && ('0' <= b))
			return true;
		else if ((b <= 'f') && ('a' <= b))
			return true;
		else if ((b <= 'F') && ('A' <= b))
			return true;
		else return false;
	}
	private static int translateHexBytes(int hb, int lb) {
		int v = 0;
		v += translateHexByte(hb);
		v <<= 4;
		v += translateHexByte(lb);
		return v;
	}
	private static int translateHexByte(int b) {
		if (('0' <= b) && (b <= '9')) return (((int) b) - '0');
		else if (('a' <= b) && (b <= 'f')) return (((int) b) - 'a' + 10);
		else if (('A' <= b) && (b <= 'F')) return (((int) b) - 'A' + 10);
		else return 0;
	}
	
	//	TODOne decode bytes only when encoding clear
	private static PString cropString(PdfByteInputStream bytes) throws IOException {
		if (DEBUG_PARSE_PDF) System.out.println("Cropping string");
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		int open = 1;
		boolean escaped = false;
		while (bytes.peek() != -1) {
			if (escaped) {
				int peek = bytes.peek();
//				if (('0' <= peek) && (peek <= '9')) {
//					int b0 = bytes.read();
//					peek = bytes.peek();
//					if (('0' <= peek) && (peek <= '9')) {
//						int b1 = bytes.read();
//						peek = bytes.peek();
//						if (('0' <= peek) && (peek <= '9')) {
//							int b2 = bytes.read();
//							baos.write(((b0 - '0') * 64) + ((b1 - '0') * 8) + (b2 - '0'));
//						}
//						else baos.write(((b0 - '0') * 8) + (b1 - '0'));
//					}
//					else baos.write(b0 - '0');
//				}
				if (('0' <= peek) && (peek <= '9')) {
					int oct = 0;
					int b0 = bytes.read();
					peek = bytes.peek();
					if (('0' <= peek) && (peek <= '9')) {
						int b1 = bytes.read();
						peek = bytes.peek();
						if (('0' <= peek) && (peek <= '9')) {
							int b2 = bytes.read();
							oct = (((b0 - '0') * 64) + ((b1 - '0') * 8) + (b2 - '0'));
						}
						else oct = (((b0 - '0') * 8) + (b1 - '0'));
					}
					else oct = (b0 - '0');
					if (oct > 127)
						oct -= 256;
					baos.write(oct);
				}
				else if (peek == 'n') {
					baos.write('\n');
					bytes.read();
				}
				else if (peek == 'r') {
					baos.write('\r');
					bytes.read();
				}
				else if (peek == 't') {
					baos.write('\t');
					bytes.read();
				}
				else if (peek == 'f') {
					baos.write('\f');
					bytes.read();
				}
				else if (peek == 'b') {
					baos.write('\b');
					bytes.read();
				}
				else if (peek == '\r') {
					bytes.read();
					peek = bytes.peek();
					if (peek == '\n')
						bytes.read();
				}
				else if (peek == '\n') {
					bytes.read();
					peek = bytes.peek();
					if (peek == '\r')
						bytes.read();
				}
				else baos.write(bytes.read());
				escaped = false;
			}
			else if (bytes.peek() == '\\') {
				escaped = true;
				bytes.read();
			}
			else if (bytes.peek() == '(') {
				baos.write(bytes.read());
				open++;
			}
			else if (bytes.peek() == ')') {
				open--;
				if (open == 0) {
					bytes.read();
					break;
				}
				else baos.write(bytes.read());
			}
			else baos.write(bytes.read());
		}
		return new PString(baos.toByteArray());
	}
	
	private static PString cropHexString(PdfByteInputStream bytes, boolean hexIsHex2) throws IOException {
		if (DEBUG_PARSE_PDF) System.out.println("Cropping hex string");
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		boolean withSpace = false;
		while (bytes.peek() != -1) {
			withSpace = ((bytes.peek() < 33) || withSpace);
			bytes.skipSpace();
			if (bytes.peek() == '>') {
				bytes.read();
				break;
			}
			while (bytes.peek() == '%') {
				skipComment(bytes);
				if (!bytes.skipSpaceCheckEnd())
					throw new IOException("Broken dictionary");
			}
			baos.write(bytes.read());
		}
//		return new PString(baos.toByteArray(), true);
		return new PString(baos.toByteArray(), hexIsHex2, !hexIsHex2, withSpace);
//		return new PString(baos.toByteArray(), false, true);
	}
	
	private static Vector cropArray(PdfByteInputStream bytes, boolean expectPTags, boolean hexIsHex2) throws IOException {
		if (DEBUG_PARSE_PDF) System.out.println("Cropping array");
		Vector array = new Vector(2);
		while (bytes.peek() != -1) {
			if (!bytes.skipSpaceCheckEnd())
				break;
			while (bytes.peek() == '%') {
				skipComment(bytes);
				if (!bytes.skipSpaceCheckEnd())
					throw new IOException("Broken dictionary");
			}
			if (bytes.peek() == ']') {
				bytes.read();
				break;
			}
			while (bytes.peek() == '%') {
				skipComment(bytes);
				if (!bytes.skipSpaceCheckEnd())
					throw new IOException("Broken dictionary");
			}
			array.add(cropNext(bytes, expectPTags, hexIsHex2));
		}
		if (DEBUG_PARSE_PDF) System.out.println(" --> " + array.toString());
		return array;
	}
	
	private static Hashtable cropHashtable(PdfByteInputStream bytes, boolean expectPTags, boolean hexIsHex2) throws IOException {
		if (DEBUG_PARSE_PDF) System.out.println("Cropping dictionary");
		Hashtable ht = new Hashtable(2);
		while (true) {
			if (!bytes.skipSpaceCheckEnd())
				throw new IOException("Broken dictionary");
			while (bytes.peek() == '%') {
				skipComment(bytes);
				if (!bytes.skipSpaceCheckEnd())
					throw new IOException("Broken dictionary");
			}
			if (bytes.peek() == '>') {
				bytes.read();
				if (bytes.peek() == '>') {
					bytes.read();
					break;
				}
				else throw new IOException("Broken dictionary");
			}
			if (!bytes.skipSpaceCheckEnd())
				throw new IOException("Broken dictionary");
			while (bytes.peek() == '%') {
				skipComment(bytes);
				if (!bytes.skipSpaceCheckEnd())
					throw new IOException("Broken dictionary");
			}
			Name name = cropName(bytes);
			if (!bytes.skipSpaceCheckEnd())
				throw new IOException("Broken dictionary");
			while (bytes.peek() == '%') {
				skipComment(bytes);
				if (!bytes.skipSpaceCheckEnd())
					throw new IOException("Broken dictionary");
			}
			Object value = cropNext(bytes, expectPTags, hexIsHex2);
			ht.put(name, value);
		}
		if (DEBUG_PARSE_PDF) System.out.println(" --> " + ht.toString());
		return ht;
	}
	
	/**
	 * Wrapper object for a raw, unparsed stream object and its associated meta
	 * data.
	 * 
	 * @author sautter
	 */
	public static class PStream {
		/**
		 * the Hashtable holding the stream parameters
		 */
		public final Hashtable params;
		/**
		 * the raw bynary data
		 */
		public final byte[] bytes;
		PStream(Hashtable params, byte[] bytes) {
			this.params = params;
			this.bytes = bytes;
		}
	}
	
	/**
	 * Wrapper object for a raw, undecoded string object.
	 * 
	 * @author sautter
	 */
	public static class PString implements CharSequence {
		
		/** the raw binary data */
		public final byte[] bytes;
		
//		/** does the binary data come from a HEX object? */
//		public final boolean isHex;
//		
		/** does the binary data come from a HEX with 2 bytes per output byte object? */
		public final boolean isHex2;
		
		/** does the binary data come from a HEX with 4 bytes per output byte object? */
		public final boolean isHex4;
		
		final boolean isHexWithSpace;
		
		PString(byte[] bytes) {
//			this(bytes, false);
			this(bytes, false, false, false);
		}
//		PString(byte[] bytes, boolean isHex) {
//			this.bytes = bytes;
//			this.isHex = isHex;
//		}
		PString(byte[] bytes, boolean isHex2, boolean isHex4, boolean withSpace) {
			this.bytes = bytes;
//			this.isHex2 = (isHex2 || (this.bytes.length < 4));
//			this.isHex4 = (isHex4 && (this.bytes.length >= 4));
			this.isHex2 = isHex2;
			this.isHex4 = isHex4;
			this.isHexWithSpace = withSpace;
		}
		public int hashCode() {
			return this.toString().hashCode();
		}
		public boolean equals(Object obj) {
			if (obj instanceof CharSequence) {
				CharSequence cs = ((CharSequence) obj);
				if (cs.length() != this.length())
					return false;
				for (int c = 0; c < cs.length(); c++) {
					if (this.charAt(c) != cs.charAt(c))
						return false;
				}
				return true;
			}
			return false;
		}
		public String toString() {
			StringBuffer string = new StringBuffer();
			if (this.isHex2) {
				for (int b = 0; b < this.bytes.length; b += 2)
					string.append((char) this.getHex2(b));
			}
			else if (this.isHex4) {
//				for (int b = 0; b < (this.bytes.length - 3); b += 4)
//					string.append((char) this.getHex4(b));
				for (int b = 0; b < this.bytes.length; b += 4)
					string.append((char) this.getHex4(b));
			}
			else for (int c = 0; c < this.bytes.length; c++)
				string.append((char) convertUnsigned(this.bytes[c]));
			return string.toString();
		}
		public String toString(PdfFont font) {
			if (font == null)
				return this.toString();
			StringBuffer string = new StringBuffer();
			if (this.isHex2) {
				for (int b = 0; b < this.bytes.length; b += 2)
					string.append(font.getUnicode(this.getHex2(b)));
			}
			else if (this.isHex4) {
//				for (int b = 0; b < (this.bytes.length - 3); b += 4)
//					string.append(font.getUnicode(this.getHex4(b)));
				for (int b = 0; b < this.bytes.length; b += 4)
					string.append(font.getUnicode(this.getHex4(b)));
			}
			else for (int c = 0; c < this.bytes.length; c++)
				string.append(font.getUnicode(convertUnsigned(this.bytes[c])));
			return string.toString();
		}
		public int length() {
			return (this.isHex4 ? ((this.bytes.length + 3) / 4) : (this.isHex2 ? ((this.bytes.length + 1) / 2) : this.bytes.length));
//			return (this.isHex ? (this.bytes.length / 4) : this.bytes.length);
		}
		public char charAt(int index) {
			if (this.isHex2)
				return ((char) this.getHex2(index * 2));
			else if (this.isHex4)
				return ((char) this.getHex4(index * 4));
			else return ((char) convertUnsigned(this.bytes[index]));
		}
		public CharSequence subSequence(int start, int end) {
			if (this.isHex2) {
				start *= 2;
				end *= 2;
				if (end > this.bytes.length)
					end--;
			}
			else if (this.isHex4) {
				start *= 4;
				end *= 4;
			}
			byte[] bytes = new byte[end - start];
			System.arraycopy(this.bytes, start, bytes, 0, (end - start));
			return new PString(bytes, this.isHex2, this.isHex4, this.isHexWithSpace);
//			return new PString(bytes, this.isHex);
		}
		private int getHex2(int index) {
			int ch = 0;
			if (('0' <= this.bytes[index]) && (this.bytes[index] <= '9'))
				ch += (this.bytes[index] - '0');
			else if (('A' <= this.bytes[index]) && (this.bytes[index] <= 'F'))
				ch += (this.bytes[index] - 'A' + 10);
			else if (('a' <= this.bytes[index]) && (this.bytes[index] <= 'f'))
				ch += (this.bytes[index] - 'a' + 10);
			index++;
			ch <<= 4;
			if (index < this.bytes.length) {
//				ch <<= 4;
				if (('0' <= this.bytes[index]) && (this.bytes[index] <= '9'))
					ch += (this.bytes[index] - '0');
				else if (('A' <= this.bytes[index]) && (this.bytes[index] <= 'F'))
					ch += (this.bytes[index] - 'A' + 10);
				else if (('a' <= this.bytes[index]) && (this.bytes[index] <= 'f'))
					ch += (this.bytes[index] - 'a' + 10);
			}
			return ch;
		}
		private int getHex4(int index) {
			int ch = 0;
			for (int i = 0; i < 4; i++) {
				if ((index+i) == this.bytes.length)
					break;
				if (ch != 0)
					ch <<= 4;
				if (('0' <= this.bytes[index+i]) && (this.bytes[index+i] <= '9'))
					ch += (this.bytes[index+i] - '0');
				else if (('A' <= this.bytes[index+i]) && (this.bytes[index+i] <= 'F'))
					ch += (this.bytes[index+i] - 'A' + 10);
				else if (('a' <= this.bytes[index+i]) && (this.bytes[index+i] <= 'f'))
					ch += (this.bytes[index+i] - 'a' + 10);
			}
			return ch;
		}
		private static final int convertUnsigned(byte b) {
			return ((b < 0) ? (((int) b) + 256) : b);
		}
	}
	
	static class PtTag {
		final String tag;
		PtTag(String tag) {
			this.tag = tag;
		}
	}
	
	//	/SMask 711 0 R
	/**
	 * Extract the mask image of an image object. If the argument image does not
	 * specify a mask image, this method returns null.
	 * @param img the image to extract the mask from
	 * @param objects the object map to use for dereferencing obbjects
	 * @return the an image object representing the mask image
	 */
	public static PImage getMaskImage(PStream img, HashMap objects) {
		Object maskImgObj = getObject(img.params, "SMask", objects);
		if (maskImgObj == null)
			maskImgObj = getObject(img.params, "Mask", objects);
		if ((maskImgObj != null) && (maskImgObj instanceof PStream)) {
			System.out.println(" --> got mask image");
			return new PImage((PStream) maskImgObj);
		}
		else return null;
	}
	
	/**
	 * Wrapper object for a raw undecoded image, also providing a cache
	 * reference for the decoded version. This is to support lazy decoding.
	 * 
	 * @author sautter
	 */
	public static class PImage {
		public final PStream data;
		BufferedImage image;
		/**
		 * Constructor
		 * @param data the raw stream to wrap
		 */
		public PImage(PStream data) {
			this.data = data;
		}
		/**
		 * Retrieve the decoded image. If no decoded version has been stored so
		 * far, this method returns null.
		 * @return the decoded image
		 */
		public BufferedImage getImage() {
			return this.image;
		}
		/**
		 * Store a decoded version of the image represented by the wrapped data.
		 * @param image the decoded image to store
		 */
		public void setImage(BufferedImage image) {
			this.image = image;
		}
	}
	
	/**
	 * Wrapper for a word (not necessarily a complete word in a linguistic
	 * sense, but a whitespace-free string) extracted from the content of a PDF
	 * page, together with its properties.
	 * 
	 * @author sautter
	 */
	public static class PWord {
		public final String str;
		public final Rectangle2D bounds;
		public final boolean bold;
		public final boolean italics;
//		public final String fontName;
		public final int fontSize;
//		public final boolean fontHasDescent;
		final PdfFont font;
		PWord(String str, Rectangle2D bounds, int fontSize, PdfFont font) {
			this.str = str;
			this.bounds = bounds;
			this.bold = font.bold;
			this.italics = font.italic;
//			this.fontName = font.name;
			this.fontSize = fontSize;
//			this.fontHasDescent = font.hasDescent;
			this.font = font;
		}
//		PWord(String str, Rectangle2D bounds, int fontSize, boolean bold, boolean italics, String fontName, boolean fontHasDescent) {
//			this.str = str;
//			this.bounds = bounds;
//			this.fontSize = fontSize;
//			this.bold = bold;
//			this.italics = italics;
//			this.fontName = fontName;
//			this.fontHasDescent = fontHasDescent;
//		}
		public String toString() {
			return (this.str + " [" + Math.round(this.bounds.getMinX()) + "," + Math.round(this.bounds.getMaxX()) + "," + Math.round(this.bounds.getMinY()) + "," + Math.round(this.bounds.getMaxY()) + "], sized " + this.fontSize + (this.bold ? ", bold" : "") + (this.italics ? ", italic" : ""));
		}
	}
	
	//	factor to apply to a font's normal space width to capture narrow spaces when outputting strings from an array
	private static final float narrowSpaceToleranceFactor = 0.67f;
	
	/**
	 * Parse a content stream of a text page.
	 * @param page the bytes to parse
	 * @param resources the page resource map
	 * @param objects a map holding objects that might be required, such as
	 *            fonts, etc.
	 * @param tokenizer get tokenizer to check and split words with
	 * @return the words extracted from the page
	 * @throws IOException
	 */
	public static PWord[] getPageWords(byte[] page, Hashtable resources, HashMap objects, Tokenizer tokenizer, ProgressMonitor pm) throws IOException {
		
		//	line up bytes
		PdfByteInputStream bytes = new PdfByteInputStream(new ByteArrayInputStream(page));
		
//		//	TODO_ne deactivate this after tests
//		if (DEBUG_RENDER_TEXT) {
//			byte[] buf = new byte[256];
//			int rd;
//			while ((rd = bytes.read(buf, 0, buf.length)) != -1)
//				System.out.write(buf, 0, rd);
//			bytes = new PdfByteInputStream(new ByteArrayInputStream(page));
//		}
//		
		//	get fonts
		final Object fontsObj = dereference(resources.get("Font"), objects);
		if (PdfFont.DEBUG_LOAD_FONTS) System.out.println(" --> font object is " + fontsObj);
		Hashtable fonts = new Hashtable();
		if ((fontsObj != null) && (fontsObj instanceof Hashtable)) {
			for (Iterator fit = ((Hashtable) fontsObj).keySet().iterator(); fit.hasNext();) {
				Object fontKey = fit.next();
				Object fontRef = null;
				Object fontObj = ((Hashtable) fontsObj).get(fontKey);
				if (fontObj instanceof Reference) {
					fontRef = fontObj;
					fontObj = dereference(fontRef, objects);
				}
				if (fontObj == null) 
					continue;
				
				if (PdfFont.DEBUG_LOAD_FONTS) System.out.println("Loading font " + ((Reference) fontRef).getObjectNumber() + " " + ((Reference) fontRef).getGenerationNumber());
				if (fontObj instanceof PdfFont) {
					fonts.put(fontKey, ((PdfFont) fontObj));
					if (PdfFont.DEBUG_LOAD_FONTS && (fontRef != null)) System.out.println("Font cache hit for " + ((Reference) fontRef).getObjectNumber() + " " + ((Reference) fontRef).getGenerationNumber());
				}
				else if (fontObj instanceof Hashtable) {
					if (PdfFont.DEBUG_LOAD_FONTS) System.out.println("Loading font from " + fontObj);
					PdfFont pFont = PdfFont.readFont(fontKey, ((Hashtable) fontObj), objects, true, pm);
					if (pFont != null) {
						fonts.put(fontKey, pFont);
						if (fontRef != null) {
							objects.put((((Reference) fontRef).getObjectNumber() + " " + ((Reference) fontRef).getGenerationNumber()), pFont);
							if (PdfFont.DEBUG_LOAD_FONTS) System.out.println("Font cached as " + ((Reference) fontRef).getObjectNumber() + " " + ((Reference) fontRef).getGenerationNumber());
						}
					}
				}
				else if (PdfFont.DEBUG_LOAD_FONTS) System.out.println("Strange font: " + fontObj);
			}
		}
		
//		if (true) // FOR FONT LOAD TESTING
//			throw new RuntimeException("Only fonts for now");
//		
//		//	TODO_ne when creating (bounds of) PWords, compute vertical shift from font size and relative baseline shift of chars
//		//	negative baseline shift ==> char moved down to fit into image ==> move down lower edge of word accordingly
//		//	positive baseline shift ==> char moved up to fit into image ==> move up lower edge of word accordingly
		
		//	create word list and renderer to fill it
		ArrayList pageWords = new ArrayList();
		PageWordRenderer pwr = new PageWordRenderer(fonts, pageWords);
		
		//	process page content through renderer
		Object obj;
		LinkedList stack = new LinkedList();
		while ((obj = cropNext(bytes, true, false)) != null) {
			if (obj instanceof PtTag) {
				if (DEBUG_RENDER_TEXT) System.out.println("Content tag: " + ((PtTag) obj).tag);
				pwr.evaluateTag(((PtTag) obj).tag, stack);
			}
			else {
				if (DEBUG_RENDER_TEXT) System.out.println(obj.getClass().getName() + ": " + obj.toString());
				stack.addLast(obj);
			}
		}
		if (DEBUG_RENDER_TEXT)
			System.out.println("Rendering done, got " + pageWords.size() + " words");
		
		//	sort out words with invalid bounding boxes
		for (int w = 0; w < pageWords.size(); w++) {
			PWord pw = ((PWord) pageWords.get(w));
			if ((pw.bounds.getWidth() <= 0) || (pw.bounds.getHeight() <= 0)) {
				if (DEBUG_RENDER_TEXT)
					System.out.println("Removing word with invalid bounds: '" + pw.str + "', " + pw.bounds);
				pageWords.remove(w--);
			}
		}
		
		//	join words with extremely close or overlapping bounding boxes (can be split due to in-word font changes, for instance)
		if (pageWords.size() > 1) {
			PWord lastWord = ((PWord) pageWords.get(0));
			double lastWordMiddle = ((lastWord.bounds.getMinY() + lastWord.bounds.getMaxY()) / 2);
			for (int w = 1; w < pageWords.size(); w++) {
				PWord word = ((PWord) pageWords.get(w));
				double wordMiddle = ((word.bounds.getMinY() + word.bounds.getMaxY()) / 2);
				if ((lastWord.bold != word.bold) || (lastWord.italics != word.italics) || (lastWord.font.hasDescent != word.font.hasDescent)) {
					lastWord = word;
					lastWordMiddle = wordMiddle;
					continue;
				}
				if ((lastWordMiddle < word.bounds.getMinY()) || (lastWordMiddle > word.bounds.getMaxY())) {
					lastWord = word;
					lastWordMiddle = wordMiddle;
					continue;
				}
				if ((wordMiddle < lastWord.bounds.getMinY()) || (wordMiddle > lastWord.bounds.getMaxY())) {
					lastWord = word;
					lastWordMiddle = wordMiddle;
					continue;
				}
				if ((word.bounds.getMinX() < lastWord.bounds.getMinX()) || (word.bounds.getMaxX() < lastWord.bounds.getMaxX())) {
					lastWord = word;
					lastWordMiddle = wordMiddle;
					continue;
				}
				
				TokenSequence lastWordTokens = tokenizer.tokenize(lastWord.str);
				TokenSequence wordTokens = tokenizer.tokenize(word.str);
				boolean needsSpace = ((lastWordTokens.size() != 0) && (wordTokens.size() != 0) && Gamta.spaceAfter(lastWordTokens.lastValue()) && Gamta.spaceBefore(wordTokens.firstValue()));
				
				if ((word.bounds.getMinX() - lastWord.bounds.getMaxX()) > (1 - (needsSpace ? narrowSpaceToleranceFactor : 0))) {
					lastWord = word;
					lastWordMiddle = wordMiddle;
					continue;
				}
				
				TokenSequence jointWordTokens = tokenizer.tokenize(lastWord.str + word.str);
				if ((lastWordTokens.size() + wordTokens.size()) <= jointWordTokens.size()) {
					lastWord = word;
					lastWordMiddle = wordMiddle;
					continue;
				}
				
				float minY = ((float) Math.min(lastWord.bounds.getMinY(), word.bounds.getMinY()));
				float maxY = ((float) Math.max(lastWord.bounds.getMaxY(), word.bounds.getMaxY()));
				Rectangle2D.Float jointWordBox = new Rectangle2D.Float(((float) lastWord.bounds.getMinX()), minY, ((float) (word.bounds.getMaxX() - lastWord.bounds.getMinX())), (maxY - minY));
				PWord jointWord;
				
				//	test if second word (a) starts with combinable accent, (b) this accent is combinable with last char of first word, and (c) there is sufficient overlap
				if (((word.bounds.getMinX() - 1) < lastWord.bounds.getMaxX()) && COMBINABLE_ACCENTS.indexOf(word.str.charAt(0)) != -1) {
					if (DEBUG_RENDER_TEXT) {
						System.out.println("Testing for possible letter diacritic merger:");
						System.out.println(" - second word starts with '" + word.str.charAt(0) + "' (" + ((int) word.str.charAt(0)) + ")");
					}
					String combinedCharName = ("" + lastWord.str.charAt(lastWord.str.length() - 1) + "" + COMBINABLE_ACCENT_MAPPINGS.get(new Character(word.str.charAt(0))));
					if (DEBUG_RENDER_TEXT)
						System.out.println(" - combined char name is '" + combinedCharName + "'");
					char combinedChar = StringUtils.getCharForName(combinedCharName);
					if (DEBUG_RENDER_TEXT)
						System.out.println(" - combined char is '" + combinedChar + "' (" + ((int) combinedChar) + ")");
					if (combinedChar > 0) {
						jointWord = new PWord((lastWord.str.substring(0, lastWord.str.length()-1) + combinedChar + word.str.substring(1)), jointWordBox, Math.max(lastWord.fontSize, word.fontSize), lastWord.font);
						if (DEBUG_RENDER_TEXT)
							System.out.println(" --> combined letter and diacritic marker");
					}
					else {
						jointWord = new PWord((lastWord.str + word.str), jointWordBox, Math.max(lastWord.fontSize, word.fontSize), lastWord.font);
						if (DEBUG_RENDER_TEXT)
							System.out.println(" --> combination not possible");
					}
				}
				else jointWord = new PWord((lastWord.str + word.str), jointWordBox, Math.max(lastWord.fontSize, word.fontSize), lastWord.font);
				
				if (DEBUG_RENDER_TEXT) {
					System.out.println("Got joint word: " + jointWord.str);
					System.out.println(" --> extent is " + ((float) jointWordBox.getMinX()) + "-" + ((float) jointWordBox.getMaxX()) + " x " + ((float) jointWordBox.getMaxY()) + "-" + ((float) jointWordBox.getMinY()));
					System.out.println(" --> base extents are\n" +
							"     " + ((float) lastWord.bounds.getMinX()) + "-" + ((float) lastWord.bounds.getMaxX()) + " x " + ((float) lastWord.bounds.getMaxY()) + "-" + ((float) lastWord.bounds.getMinY()) + "\n" +
							"     " + ((float) word.bounds.getMinX()) + "-" + ((float) word.bounds.getMaxX()) + " x " + ((float) word.bounds.getMaxY()) + "-" + ((float) word.bounds.getMinY()) +
						"");
				}
				
				pageWords.set((w-1), jointWord);
				pageWords.remove(w--);
				lastWord = jointWord;
				lastWordMiddle = ((jointWord.bounds.getMinY() + jointWord.bounds.getMaxY()) / 2);
			}
		}
		
		//	finally ...
		return ((PWord[]) pageWords.toArray(new PWord[pageWords.size()]));
	}
	
	private static final String COMBINABLE_ACCENTS;
	private static final HashMap COMBINABLE_ACCENT_MAPPINGS = new HashMap();
	static {
//		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u00A8'), "dieresis");
//		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u00AF'), "macron");
//		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u00B4'), "acute");
//		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u00B8'), "cedilla");
//		
//		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u02C6'), "circumflex");
//		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u02C7'), "caron");
//		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u02D8'), "breve");
//		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u02DA'), "ring");
		
		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u0300'), "grave");
		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u0301'), "acute");
		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u0302'), "circumflex");
		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u0303'), "tilde");
		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u0304'), "macron");
		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u0306'), "breve");
		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u0307'), "dot");
		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u0308'), "dieresis");
		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u0309'), "hook");
		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u030A'), "ring");
		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u030B'), "dblacute");
		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u030F'), "dblgrave");
		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u030C'), "caron");
		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u0323'), "dotbelow");
		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u0327'), "cedilla");
		COMBINABLE_ACCENT_MAPPINGS.put(new Character('\u0328'), "ogonek");
		
		StringBuffer combinableAccentCollector = new StringBuffer();
		ArrayList combinableAccents = new ArrayList(COMBINABLE_ACCENT_MAPPINGS.keySet());
		for (int c = 0; c < combinableAccents.size(); c++) {
			Character combiningChar = ((Character) combinableAccents.get(c));
			combinableAccentCollector.append(combiningChar.charValue());
			String charName = ((String) COMBINABLE_ACCENT_MAPPINGS.get(combiningChar));
			char baseChar = StringUtils.getCharForName(charName);
			if ((baseChar > 0) && (baseChar != combiningChar.charValue())) {
				combinableAccentCollector.append(baseChar);
				COMBINABLE_ACCENT_MAPPINGS.put(new Character(baseChar), charName);
			}
		}
		COMBINABLE_ACCENTS = combinableAccentCollector.toString();
	}
	
	private static final boolean DEBUG_RENDER_TEXT = true;
	
	//	observe <userSpace> = <textSpace> * <textMatrix> * <transformationMatrix>
	private static float[] transform(float x, float y, float z, float[][] matrix) {
		float[] res = new float[3];
		for (int c = 0; c < matrix.length; c++)
			res[c] = ((matrix[c][0] * x) + (matrix[c][1] * y) + (matrix[c][2] * z));
		return res;
	}
	private static float[] transform(float[] f, float[][] matrix) {
		return ((f.length == 3) ? transform(f[0], f[1], f[2], matrix) : f);
	}
	private static void cloneValues(float[][] sm, float[][] tm) {
		for (int c = 0; c < sm.length; c++) {
			for (int r = 0; r < sm[c].length; r++)
				tm[c][r] = sm[c][r];
		}
	}
	private static float[][] multiply(float[][] lm, float[][] rm) {
		float[][] res = new float[3][3];
		for (int c = 0; c < res.length; c++) {
			for (int r = 0; r < res[c].length; r++) {
				res[c][r] = 0;
				for (int i = 0; i < res[c].length; i++)
					res[c][r] += (lm[i][r] * rm[c][i]);
			}
		}
		return res;
	}
//	private static void doTd(float[][] tm, float[][] lm, float tx, float ty) {
//		if ((tm == null) && (lm == null))
//			return;
//		float[][] mm = ((lm == null) ? tm : lm);
//		float[][] td = { // have to give this matrix transposed, as columns are outer dimension
//				{1, 0, tx},
//				{0, 1, ty},
//				{0, 0, 1},
//			};
//		float[][] res = multiply(td, mm);
//		if (lm != null)
//			cloneValues(res, lm);
//		if (tm != null)
//			cloneValues(res, tm);
//	}
	private static void printMatrix(float[][] m) {
		for (int r = 0; r < m[0].length; r++) {
			System.out.print("    ");
			for (int c = 0; c < m.length; c++)
				System.out.print(" " + m[c][r]);
			System.out.println();
		}
	}
	
	// original: <horizontalDisplacement> := ((charWidth - (<adjustmentFromArray>/1000)) * fontSize + charSpacing + wordSpacing) * horizontalZoom
	// adjusted: <horizontalDisplacement> := (charWidth * fontSize + charSpacing + wordSpacing) * horizontalZoom
//	private static void drawChar(char ch, PdfFont font, int fontSize, float charSpacing, float wordSpacing, float horizontalZoom, float[][] lineMatrix, float pageHeight, float dpiFactor) {
//		float cw = font.getCharWidth(ch);
//		float cs = ((ch == 32) ? wordSpacing : charSpacing);
//		float width = ((((cw * fontSize) + (cs * 1000)) * horizontalZoom) / (1000 * 100));
//		doTd(null, lineMatrix, width, 0);
//	}
//	private static void drawChar(float charWidth, int fontSize, float spacing, float horizontalZoom, float[][] lineMatrix, float pageHeight, float dpiFactor) {
//		float width = ((((charWidth * fontSize) + (spacing * 1000)) * horizontalZoom) / (1000 * 100));
//		doTd(null, lineMatrix, width, 0);
//	}
	
	private static class PageWordRenderer {
		
		private static final float narrowSpaceToleranceFactor = 0.67f;
		
		private Hashtable fonts;
		
		private class SavedGraphicsState {
			private Object sgsFontKey;
			private PdfFont sgsFont;
			private float sgsFontSpaceWidth;
			
			private float sgsFontSize;
			private float sgsLineHeight;
			private float sgsCharSpacing;
			private float sgsWordSpacing;
			private float sgsHorizontalScaling;
			private float sgsTextRise;
			
			private LinkedList sgsTransformationMatrices;
			
			SavedGraphicsState() {
				this.sgsFontKey = pwrFontKey;
				this.sgsFont = pwrFont;
				this.sgsFontSpaceWidth = pwrFontSpaceWidth;
				this.sgsFontSize = pwrFontSize;
				this.sgsLineHeight = pwrLineHeight;
				this.sgsCharSpacing = pwrCharSpacing;
				this.sgsWordSpacing = pwrWordSpacing;
				this.sgsHorizontalScaling = pwrHorizontalScaling;
				this.sgsTextRise = pwrTextRise;
				this.sgsTransformationMatrices = new LinkedList(pwrTransformationMatrices);
			}
			
			void restore() {
				pwrFontKey = this.sgsFontKey;
				pwrFont = this.sgsFont;
				pwrFontSpaceWidth = this.sgsFontSpaceWidth;
				pwrFontSize = this.sgsFontSize;
				pwrLineHeight = this.sgsLineHeight;
				pwrCharSpacing = this.sgsCharSpacing;
				pwrWordSpacing = this.sgsWordSpacing;
				pwrHorizontalScaling = this.sgsHorizontalScaling;
				pwrTextRise = this.sgsTextRise;
				pwrTransformationMatrices = this.sgsTransformationMatrices;
			}
		}
		
		private Object pwrFontKey = null;
		private PdfFont pwrFont = null;
		private float pwrFontSpaceWidth = 0;
		
		private float pwrFontSize = -1;
		private float eFontSize = -1;
		private float pwrLineHeight = 0;
		private float pwrCharSpacing = 0;
		private float pwrWordSpacing = 0;
		private float pwrHorizontalScaling = 100;
		private float pwrTextRise = 0;
		
		private float[][] textMatrix = new float[3][3];
		private float[][] lineMatrix = new float[3][3];
		
//		private float[][] transformerMatrix = new float[3][3];
//		private LinkedList transformerMatrixStack = new LinkedList();
		private LinkedList pwrTransformationMatrices = new LinkedList();
		
		private LinkedList graphicsStateStack = new LinkedList();
		
		private ArrayList words;
		
		PageWordRenderer(Hashtable fonts, ArrayList words) {
			this.fonts = fonts;
			this.words = words;
			for (int c = 0; c < this.textMatrix.length; c++) {
				Arrays.fill(this.textMatrix[c], 0);
				this.textMatrix[c][c] = 1;
			}
			cloneValues(this.textMatrix, this.lineMatrix);
//			cloneValues(this.textMatrix, this.transformerMatrix);
		}
		
		private void doBT(LinkedList stack) {
			if (DEBUG_RENDER_TEXT) System.out.println(" --> start text");
			for (int c = 0; c < this.textMatrix.length; c++) {
				Arrays.fill(this.textMatrix[c], 0);
				this.textMatrix[c][c] = 1;
			}
			cloneValues(this.textMatrix, this.lineMatrix);
		}
		
		private void doET(LinkedList stack) {
			if (DEBUG_RENDER_TEXT) System.out.println(" --> end text");
		}
		
		private void doBMC(LinkedList stack) {
			Object n = stack.removeLast();
			if (DEBUG_RENDER_TEXT) System.out.println(" --> start marked content: " + n);
		}
		
		private void doBDC(LinkedList stack) {
			Object d = stack.removeLast();
			Object n = stack.removeLast();
			if (DEBUG_RENDER_TEXT) System.out.println(" --> start marked content with dictionary: " + n + " - " + d);
		}
		
		private void doEMC(LinkedList stack) {
			if (DEBUG_RENDER_TEXT) System.out.println(" --> end marked content");
		}
		
		private void doMP(LinkedList stack) {
			Object n = stack.removeLast();
			if (DEBUG_RENDER_TEXT) System.out.println(" --> mark point: " + n);
		}
		
		private void doDP(LinkedList stack) {
			Object d = stack.removeLast();
			Object n = stack.removeLast();
			if (DEBUG_RENDER_TEXT) System.out.println(" --> mark point with dictionary: " + n + " - " + d);
		}
		
		private void doq(LinkedList stack) {
//			float[][] transformerMatrix = new float[3][3];
//			cloneValues(this.transformerMatrix, transformerMatrix);
//			this.transformerMatrixStack.addLast(transformerMatrix);
			this.graphicsStateStack.addLast(new SavedGraphicsState());
			if (DEBUG_RENDER_TEXT) System.out.println(" --> save graphics state");
		}
		
		private void doQ(LinkedList stack) {
//			if (this.transformerMatrixStack.size() != 0) {
//				float[][] transformerMatrix = ((float[][]) this.transformerMatrixStack.removeLast());
//				cloneValues(transformerMatrix, this.transformerMatrix);
//			}
//			else for (int c = 0; c < this.transformerMatrix.length; c++) {
//				Arrays.fill(this.transformerMatrix[c], 0);
//				this.transformerMatrix[c][c] = 1;
//			}
//			if (DEBUG_RENDER_TEXT) {
//				System.out.println(" --> restore graphics state:");
//				printMatrix(this.transformerMatrix);
//			}
			((SavedGraphicsState) this.graphicsStateStack.removeLast()).restore();
			if (DEBUG_RENDER_TEXT) {
				System.out.println(" --> restore graphics state:");
				for (Iterator tmit = this.pwrTransformationMatrices.iterator(); tmit.hasNext();) {
					float[][] tm = ((float[][]) tmit.next());
					printMatrix(tm);
					if (tmit.hasNext())
						System.out.println();
				}
			}
			this.computeEffectiveFontSize();
		}
		
		private void docm(LinkedList stack) {
//			this.transformerMatrix[2][2] = 1;
//			this.transformerMatrix[1][2] = ((Number) stack.removeLast()).floatValue();
//			this.transformerMatrix[0][2] = ((Number) stack.removeLast()).floatValue();
//			this.transformerMatrix[2][1] = 0;
//			this.transformerMatrix[1][1] = ((Number) stack.removeLast()).floatValue();
//			this.transformerMatrix[0][1] = ((Number) stack.removeLast()).floatValue();
//			this.transformerMatrix[2][0] = 0;
//			this.transformerMatrix[1][0] = ((Number) stack.removeLast()).floatValue();
//			this.transformerMatrix[0][0] = ((Number) stack.removeLast()).floatValue();
//			if (DEBUG_RENDER_TEXT) {
//				System.out.println(" --> transformation matrix set to");
//				printMatrix(this.transformerMatrix);
//			}
			float[][] nTm = new float[3][3];
			nTm[2][2] = 1;
			nTm[1][2] = ((Number) stack.removeLast()).floatValue();
			nTm[0][2] = ((Number) stack.removeLast()).floatValue();
			nTm[2][1] = 0;
			nTm[1][1] = ((Number) stack.removeLast()).floatValue();
			nTm[0][1] = ((Number) stack.removeLast()).floatValue();
			nTm[2][0] = 0;
			nTm[1][0] = ((Number) stack.removeLast()).floatValue();
			nTm[0][0] = ((Number) stack.removeLast()).floatValue();
			this.pwrTransformationMatrices.addFirst(nTm);
			/* seems like we really have to concatenate these transformations
			 * from the left ... PDF specification only states we have to
			 * concatenate them, but not from which side, and concatenating
			 * from the left seems to cause less garble (none) than from the
			 * right (messing up a few test files) */
			if (DEBUG_RENDER_TEXT) {
				System.out.println(" --> transformation matrix stack set to");
				for (Iterator tmit = this.pwrTransformationMatrices.iterator(); tmit.hasNext();) {
					float[][] tm = ((float[][]) tmit.next());
					printMatrix(tm);
					if (tmit.hasNext())
						System.out.println();
				}
			}
			this.computeEffectiveFontSize();
		}
		
		private float[] applyTransformationMatrices(float[] f) {
//			if (IGNORE_TRANSFORMER_MATRICES)
//				return f;
			for (Iterator tmit = this.pwrTransformationMatrices.iterator(); tmit.hasNext();) {
				float[][] tm = ((float[][]) tmit.next());
				f = transform(f, tm);
			}
			return f;
		}
		
		// d i j J M w gs
		private void dod(LinkedList stack)  {
			if (DEBUG_RENDER_TEXT) System.out.println(" --> dash pattern");
		}
		
		private void doi(LinkedList stack)  {
			if (DEBUG_RENDER_TEXT) System.out.println(" --> flatness");
		}
		
		private void doj(LinkedList stack) {
			if (DEBUG_RENDER_TEXT) System.out.println(" --> line join");
		}
		
		private void doJ(LinkedList stack) {
			if (DEBUG_RENDER_TEXT) System.out.println(" --> line ends");
		}
		
		private void doM(LinkedList stack) {
			if (DEBUG_RENDER_TEXT) System.out.println(" --> line miter limit");
		}
		
		private void dow(LinkedList stack) {
			if (DEBUG_RENDER_TEXT) System.out.println(" --> line width");
		}
		
		private void dogs(LinkedList stack) {
			if (DEBUG_RENDER_TEXT) System.out.println(" --> graphics state to " + stack.getLast());
		}
		
		private void doTc(LinkedList stack) {
			this.pwrCharSpacing = ((Number) stack.removeLast()).floatValue();
			if (DEBUG_RENDER_TEXT) System.out.println(" --> char spacing " + this.pwrCharSpacing);
		}
		
		private void doTw(LinkedList stack) {
			this.pwrWordSpacing = ((Number) stack.removeLast()).floatValue();
			if (DEBUG_RENDER_TEXT) System.out.println(" --> word spacing " + this.pwrWordSpacing);
		}
		
		private void doTz(LinkedList stack) {
			this.pwrHorizontalScaling = ((Number) stack.removeLast()).floatValue();
			if (DEBUG_RENDER_TEXT) System.out.println(" --> horizontal scaling " + this.pwrHorizontalScaling);
		}
		
		private void doTL(LinkedList stack) {
			this.pwrLineHeight = ((Number) stack.removeLast()).floatValue();
			if (DEBUG_RENDER_TEXT) System.out.println(" --> leading (line height) " + this.pwrLineHeight);
		}
		
		private void doTf(LinkedList stack) {
			this.pwrFontSize = ((Number) stack.removeLast()).floatValue();
			this.pwrFontKey = stack.removeLast();
			if (DEBUG_RENDER_TEXT) System.out.println(" --> font key set to " + this.pwrFontKey);
			this.pwrFont = ((PdfFont) this.fonts.get(this.pwrFontKey));
			if (this.pwrFont != null) {
				this.pwrFontSpaceWidth = this.pwrFont.getCharWidth(' ');
				if (DEBUG_RENDER_TEXT) {
					System.out.println(" --> font " + this.pwrFontKey + " sized " + this.pwrFontSize);
					System.out.println("   - font is " + this.pwrFont.data);
					System.out.println("   - font descriptor " + this.pwrFont.descriptor);
					System.out.println("   - font name " + this.pwrFont.name);
					System.out.println("   - bold: " + this.pwrFont.bold + ", italic: " + this.pwrFont.italic);
					System.out.println("   - space width is " + this.pwrFontSpaceWidth);
				}
				this.computeEffectiveFontSize();
			}
		}
		
		private void doTfs(LinkedList stack) {
			this.pwrFontSize = ((Number) stack.removeLast()).floatValue();
			if (DEBUG_RENDER_TEXT) System.out.println(" --> font size " + this.pwrFontSize);
			this.computeEffectiveFontSize();
		}
		
		private void doTm(LinkedList stack) {
			this.textMatrix[2][2] = 1;
			this.textMatrix[1][2] = ((Number) stack.removeLast()).floatValue();
			this.textMatrix[0][2] = ((Number) stack.removeLast()).floatValue();
			this.textMatrix[2][1] = 0;
			this.textMatrix[1][1] = ((Number) stack.removeLast()).floatValue();
			this.textMatrix[0][1] = ((Number) stack.removeLast()).floatValue();
			this.textMatrix[2][0] = 0;
			this.textMatrix[1][0] = ((Number) stack.removeLast()).floatValue();
			this.textMatrix[0][0] = ((Number) stack.removeLast()).floatValue();
			cloneValues(this.textMatrix, this.lineMatrix);
			if (DEBUG_RENDER_TEXT) {
				System.out.println(" --> text matrix set to");
				printMatrix(this.lineMatrix);
			}
			this.computeEffectiveFontSize();
		}
		
		//	compute effective font size as round(font size * ((a + d) / 2)) in text matrix
		private void computeEffectiveFontSize() {
			float temFontSize = ((Math.abs(this.textMatrix[0][0]) + Math.abs(this.textMatrix[1][1])) / 2);
			if (temFontSize < 0.5)
				temFontSize = ((Math.abs(this.textMatrix[0][1]) + Math.abs(this.textMatrix[1][0])) / 2);
//			float trmFontSize = ((Math.abs(this.transformerMatrix[0][0]) + Math.abs(this.transformerMatrix[1][1])) / 2);
//			if (trmFontSize < 0.5)
//				trmFontSize = ((Math.abs(this.transformerMatrix[0][1]) + Math.abs(this.transformerMatrix[1][0])) / 2);
			float trmFontSize = 1;
			for (Iterator tmit = this.pwrTransformationMatrices.iterator(); tmit.hasNext();) {
				float[][] tm = ((float[][]) tmit.next());
				float tmFontSize = ((Math.abs(tm[0][0]) + Math.abs(tm[1][1])) / 2);
				if (tmFontSize < 0.5)
					tmFontSize = ((Math.abs(tm[0][1]) + Math.abs(tm[1][0])) / 2);
				trmFontSize *= tmFontSize;
			}
			this.eFontSize = (this.pwrFontSize * temFontSize * trmFontSize);
			if (DEBUG_RENDER_TEXT) System.out.println(" ==> effective font size is " + this.eFontSize);
		}
		
		private void doTr(LinkedList stack) {
			Object r = stack.removeLast();
			if (DEBUG_RENDER_TEXT) System.out.println(" --> text rendering mode " + r);
		}
		
		private void doTs(LinkedList stack) {
			this.pwrTextRise = ((Number) stack.removeLast()).floatValue();
			if (DEBUG_RENDER_TEXT) System.out.println(" --> text rise " + this.pwrTextRise);
			//	TODO observe text rise when drawing strings
		}
		
		private void doTd(LinkedList stack) {
			this.doNewLine(stack, false);
		}
		
		private void doTD(LinkedList stack) {
			this.doNewLine(stack, true);
		}
		
		private void doTasterisc(LinkedList stack) {
			stack.addLast(new Float(0));
			stack.addLast(new Float(-this.pwrLineHeight));
			this.doNewLine(stack, false);
		}
		
		private void doNewLine(LinkedList stack, boolean setLineHeight) {
			Object ty = stack.removeLast();
			if (setLineHeight)
				this.pwrLineHeight = -((Number) ty).floatValue();
			Object tx = stack.removeLast();
			if (DEBUG_RENDER_TEXT)
				System.out.println("Moving to new line");
			updateMatrix(this.textMatrix, ((Number) tx).floatValue(), ((Number) ty).floatValue());
			cloneValues(this.textMatrix, this.lineMatrix);
			if (DEBUG_RENDER_TEXT) {
				System.out.println(" --> new line, offset by " + tx + "/" + ty + (setLineHeight ? (", and new leading (line height " + this.pwrLineHeight + ")") : ""));
				printMatrix(this.lineMatrix);
			}
		}
		
		void evaluateTag(String tag, LinkedList stack) throws IOException {
			if ("Tc".equals(tag))
				this.doTc(stack);
			else if ("Tw".equals(tag))
				this.doTw(stack);
			else if ("Tz".equals(tag))
				this.doTz(stack);
			else if ("TL".equals(tag))
				this.doTL(stack);
			else if ("Tf".equals(tag))
				this.doTf(stack);
			else if ("Tfs".equals(tag))
				this.doTfs(stack);
			else if ("Tm".equals(tag))
				this.doTm(stack);
			else if ("Tr".equals(tag))
				this.doTr(stack);
			else if ("Ts".equals(tag))
				this.doTs(stack);
			
			else if ("Td".equals(tag))
				this.doTd(stack);
			else if ("TD".equals(tag))
				this.doTD(stack);
			else if ("T*".equals(tag))
				this.doTasterisc(stack);
			
			else if ("BT".equals(tag))
				this.doBT(stack);
			else if ("ET".equals(tag))
				this.doET(stack);
			else if ("BMC".equals(tag))
				this.doBMC(stack);
			else if ("BDC".equals(tag))
				this.doBDC(stack);
			else if ("EMC".equals(tag))
				this.doEMC(stack);
			else if ("MP".equals(tag))
				this.doMP(stack);
			else if ("DP".equals(tag))
				this.doDP(stack);
			
			else if ("q".equals(tag))
				this.doq(stack);
			else if ("Q".equals(tag))
				this.doQ(stack);
			else if ("cm".equals(tag))
				this.docm(stack);
			else if ("d".equals(tag))
				this.dod(stack);
			else if ("i".equals(tag))
				this.doi(stack);
			else if ("j".equals(tag))
				this.doj(stack);
			else if ("J".equals(tag))
				this.doJ(stack);
			else if ("M".equals(tag))
				this.doM(stack);
			else if ("w".equals(tag))
				this.dow(stack);
			else if ("gs".equals(tag))
				this.dogs(stack);
			
			else if ("Tj".equals(tag))
				this.doTj(stack);
			else if ("TJ".equals(tag))
				this.doTJ(stack);
			else if ("'".equals(tag))
				this.dohighcomma(stack);
			else if ("\"".equals(tag))
				this.dodoublequote(stack);
			
			else if (DEBUG_RENDER_TEXT) System.out.println(" ==> UNKNOWN");
		}
		
		private void dohighcomma(LinkedList stack) {
			this.doTasterisc(stack);
			this.doTj(stack);
		}
		
		private void dodoublequote(LinkedList stack) {
			CharSequence str = ((CharSequence) stack.removeLast());
			this.doTc(stack);
			this.doTw(stack);
			stack.addLast(str);
			this.dohighcomma(stack);
		}
		
		private void doTj(LinkedList stack) {
			CharSequence str = ((CharSequence) stack.removeLast());
			if (this.pwrFont != null) {
				
				//	start line, computing top and bottom
				this.startLine();
				
				//	render char sequence
				String rStr = this.renderCharSequence(str);
				
				//	finish line, remembering any last word
				this.endLine();
				
				if (DEBUG_RENDER_TEXT)
					System.out.println(" --> show string: '" + rStr + "'" + ((rStr.length() == 1) ? (" (" + ((int) rStr.charAt(0)) + ")") : ""));
			}
			else if (DEBUG_RENDER_TEXT) {
				System.out.println(" --> show string (no font): '" + str + "'" + ((str.length() == 1) ? (" (" + ((int) str.charAt(0)) + ")") : ""));
				System.out.println(" --> cs class is " + str.getClass().getName());
			}
		}
		
		private void doTJ(LinkedList stack) {
			StringBuffer totalRendered = new StringBuffer();
			Vector stros = ((Vector) stack.removeLast());
			
			//	print debug info
			if (DEBUG_RENDER_TEXT) {
				System.out.println("TEXT ARRAY: " + stros);
				for (int s = 0; s < stros.size(); s++) {
					Object o = stros.get(s);
					if (o instanceof Number)
						System.out.println(" - n " + o);
					else if (o instanceof CharSequence) {
						CharSequence cs = ((CharSequence) o);
						System.out.print(" - cs " + o + " (");
						for (int c = 0; c < cs.length(); c++)
							System.out.print(" " + ((int) cs.charAt(c)));
						System.out.println(")");
					}
					else System.out.println(" - o " + o + " (" + o.getClass().getName() + ")");
				}
			}
			
			//	start line, computing top and bottom
			this.startLine();
			
			//	handle array entries
			for (int s = 0; s < stros.size(); s++) {
				Object o = stros.get(s);
				if (o instanceof CharSequence) {
					CharSequence str = ((CharSequence) o);
					if (this.pwrFont == null)
						totalRendered.append(str);
					else {
						String rStr = this.renderCharSequence(str);
						totalRendered.append(rStr);
					}
				}
				else if (o instanceof Number) {
					float d = (((Number) o).floatValue() / 1000);
					if ((this.pwrFont != null) && (d < (-(this.pwrFontSpaceWidth * narrowSpaceToleranceFactor) / 1000))) {
						totalRendered.append(' ');
						this.endWord();
					}
					else if ((s+1) == stros.size())
						this.endWord();
					if (DEBUG_RENDER_TEXT)
						System.out.println("Re-positioning by TJ array number " + d + "*" + this.pwrFontSize);
					updateMatrix(this.lineMatrix, (-d * this.pwrFontSize), 0);
				}
			}
			
			//	finish line, remembering any last word
			this.endLine();
			
			//	print debug info
			if (DEBUG_RENDER_TEXT) System.out.println(" --> show strings from array" + ((this.pwrFont == null) ? " (nf)" : "") + ": '" + totalRendered + "'");
		}
		
		private StringBuffer wordString = null;
		private float[] start;
		private float[] top;
		private float[] bottom;
		
		private String renderCharSequence(CharSequence cs) {
			StringBuffer rendered = new StringBuffer();
			
			//	render characters
			for (int c = 0; c < cs.length(); c++) {
				char ch = cs.charAt(c);
				String rCh = pwrFont.getUnicode((int) ch);
				boolean isSpace = (rCh.trim().length() == 0);
				boolean implicitSpace = ((this.pwrCharSpacing > ((this.pwrFontSpaceWidth * narrowSpaceToleranceFactor) / 1000)));
				
				//	whitespace char, remember end of previous word (if any)
				if (isSpace) {
					this.endWord();
					rendered.append(' ');
				}
				
				//	non-whitespace char, remember start of word
				else {
					this.startWord();
					if (this.wordString != null)
						this.wordString.append(rCh);
					rendered.append(rCh);
				}
				
				//	update state (move caret forward)
				this.drawChar(ch, isSpace, implicitSpace);
				
				//	extreme char spacing, add space
				if (implicitSpace) {
					if (!isSpace)
						rendered.append(' ');
					this.endWord();
					if (DEBUG_RENDER_TEXT)
						System.out.println("Drawing implicit space");
					updateMatrix(this.lineMatrix, this.pwrCharSpacing, 0);
				}
			}
			
			//	finally ...
			return rendered.toString();
		}
		
		private void drawChar(char ch, boolean isSpace, boolean implicitSpace) {
			float spacing = (isSpace ? this.pwrWordSpacing : (implicitSpace ? 0 : this.pwrCharSpacing));
			float width = ((((this.pwrFont.getCharWidth(ch) * this.pwrFontSize) + (spacing * 1000)) * this.pwrHorizontalScaling) / (1000 * 100));
			if (DEBUG_RENDER_TEXT)
				System.out.println("Drawing char '" + ch + "' (" + ((int) ch) + ")");
			updateMatrix(this.lineMatrix, width, 0);
		}
		
//		private static final boolean IGNORE_TRANSFORMER_MATRICES = false;
//		
		private void startLine() {
			if ((this.top != null) && (this.bottom != null))
				return;
			if (this.pwrFont == null)
				return;
			this.top = transform(0, (this.pwrFont.capHeigth * this.pwrFontSize), 1, this.lineMatrix);
			this.top = this.applyTransformationMatrices(this.top);
			this.bottom = transform(0, (this.pwrFont.descent * this.pwrFontSize), 1, this.lineMatrix);
			this.bottom = this.applyTransformationMatrices(this.bottom);
		}
		
		private void endLine() {
			this.endWord();
			this.top = null;
			this.bottom = null;
		}
		
		private void startWord() {
			if ((this.top == null) || (this.bottom == null))
				return;
			if (this.start != null)
				return;
			this.wordString = new StringBuffer();
			this.start = transform(0, 0, 1, this.lineMatrix);
			this.start = this.applyTransformationMatrices(this.start);
			if (DEBUG_RENDER_TEXT) System.out.println(" start set to " + Arrays.toString(this.start));
		}
		
		private void endWord() {
			if (this.start == null)
				return;
			float[] end = transform(0, 0, 1, this.lineMatrix);
			end = this.applyTransformationMatrices(end);
			if (DEBUG_RENDER_TEXT) System.out.println(" --> extent is " + this.start[0] + "-" + end[0] + " x " + this.top[1] + "-" + this.bottom[1]);
			Rectangle2D pwb;
//			float bls = this.pwrFont.getRelativeBaselineShift(this.wordString.toString());
//			if (bls == 0)
//				pwb = new Rectangle2D.Float(this.start[0], this.bottom[1], (end[0] - this.start[0]), (this.top[1] - this.bottom[1]));
//			else {
//				bls *= this.eFontSize;
//				if (DEBUG_RENDER_TEXT) System.out.println(" --> extent corrected to " + this.start[0] + "-" + end[0] + " x " + (top[1] + bls) + "-" + (bottom[1] + bls));
//				pwb = new Rectangle2D.Float(this.start[0], (this.bottom[1] + bls), (end[0] - this.start[0]), (this.top[1] - this.bottom[1]));
//			}
			pwb = new Rectangle2D.Float(this.start[0], this.bottom[1], (end[0] - this.start[0]), (this.top[1] - this.bottom[1]));
			//	correction seems to be causing a bit of trouble, switched off for now
			this.words.add(new PWord(this.wordString.toString(), pwb, Math.round(this.eFontSize), this.pwrFont));
			this.wordString = null;
			this.start = null;
		}
		
		private static final void updateMatrix(float[][] matrix, float tx, float ty) {
			if (matrix == null)
				return;
			float[][] td = { // have to give this matrix transposed, as columns are outer dimension
					{1, 0, tx},
					{0, 1, ty},
					{0, 0, 1},
				};
			float[][] res = multiply(td, matrix);
			cloneValues(res, matrix);
//			if (DEBUG_RENDER_TEXT) {
//				System.out.println("Matrix updated with " + tx + " / " + ty);
//				printMatrix(matrix);
//			}
		}
	}
}