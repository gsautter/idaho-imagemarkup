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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import org.icepdf.core.io.BitStream;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.filters.ASCII85Decode;
import org.icepdf.core.pobjects.filters.ASCIIHexDecode;
import org.icepdf.core.pobjects.filters.FlateDecode;
import org.icepdf.core.pobjects.filters.LZWDecode;
import org.icepdf.core.pobjects.filters.RunLengthDecode;
import org.icepdf.core.pobjects.security.SecurityManager;
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
	
	static void decodeObjectStream(PStream objStream, Map objects, boolean forInfo) throws IOException {
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
	public static void decode(Object filter, byte[] stream, Hashtable params, ByteArrayOutputStream baos, Map objects) throws IOException {
		
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
			baos.write(stream);
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
	public static Object dereference(Object obj, Map objects) {
		while (obj instanceof Reference)
			obj = objects.get(((Reference) obj).getObjectNumber() + " " + ((Reference) obj).getGenerationNumber());
		return obj;
	}
	
	private static void dereferenceObjects(Map objects) {
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
	
	private static void dereferenceObjects(Hashtable dict, Map objects) {
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
				else if (obj instanceof Hashtable)
					continue;
				else if (obj instanceof Vector)
					continue;
				dict.put(id, obj);
			}
		}
	}
	
	private static void dereferenceObjects(Vector array, Map objects) {
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
				else if (obj instanceof Hashtable)
					continue;
				else if (obj instanceof Vector)
					continue;
				array.set(i, obj);
			}
		}
	}
	
	private static final boolean DEBUG_DECRYPT_PDF = false;
	
	/**
	 * Decrypt the objects from a PDF document. This method applies default
	 * decryption to streams and strings. Hashtables and Vectors are treated
	 * recursively.
	 * @param objects the Map holding the objects to decrypt
	 * @param sm the security manager to use for decryption
	 * @throws IOException
	 */
	public static void decryptObjects(Map objects, SecurityManager sm) throws IOException {
		if (sm == null)
			return;
		for (Iterator rit = objects.keySet().iterator(); rit.hasNext();) {
			Object objKey = rit.next();
			if (DEBUG_DECRYPT_PDF) System.out.println("Object key is " + objKey);
			Reference objRef;
			if (objKey instanceof String) {
				String[] refNrs = ((String) objKey).split("\\s+");
				objRef = new Reference(Integer.parseInt(refNrs[0]), Integer.parseInt(refNrs[1]));
			}
			else if (objKey instanceof Reference)
				objRef = ((Reference) objKey);
			else continue;
			if (DEBUG_DECRYPT_PDF) System.out.println("Reference is " + objKey);
			Object obj = objects.get(objKey);
			if (DEBUG_DECRYPT_PDF) System.out.println("Object is " + obj + " (" + obj.getClass().getName() + ")");
			decryptObject(objRef, obj, sm);
			if (DEBUG_DECRYPT_PDF) System.out.println("Decrypted object is " + obj + " (" + obj.getClass().getName() + ")");
		}
	}
	
	private static void decryptObject(Reference objRef, Object obj, SecurityManager sm) throws IOException {
		if (DEBUG_DECRYPT_PDF) {
			System.out.println("Decrypting object " + objRef + " (" + obj.getClass().getName() + ")");
			System.out.println(obj);
		}
		if (obj instanceof PStream) {
			decryptObject(objRef, ((PStream) obj).params, sm);
			decryptBytes(objRef, ((PStream) obj).bytes, sm);
		}
		else if (obj instanceof Hashtable) {
			Hashtable dict = ((Hashtable) obj);
			for (Iterator kit = dict.keySet().iterator(); kit.hasNext();) {
				Object key = kit.next();
				decryptObject(objRef, dict.get(key), sm);
			}
		}
		else if (obj instanceof Vector) {
			Vector array = ((Vector) obj);
			for (int i = 0; i < array.size(); i++)
				decryptObject(objRef, array.get(i), sm);
		}
		else if (obj instanceof PString) {
			PString str = ((PString) obj);
			byte[] strBytes;
			if (str.isHex2 || str.isHex4) {
				strBytes = new byte[(str.bytes.length + 1) / 2];
				for (int i = 0; i < str.bytes.length; i += 2) {
					byte hb = str.bytes[i];
					byte lb = (((i+1) < str.bytes.length) ? str.bytes[i+1] : 0);
					strBytes[i / 2] = ((byte) ((hb << 4) + lb));
				}
			}
			else strBytes = str.bytes;
			decryptBytes(objRef, strBytes, sm);
			if (str.isHex2 || str.isHex4) {
				for (int i = 0; i < str.bytes.length; i += 2) {
					str.bytes[i] = ((byte) (strBytes[i / 2] >> 4));
					if ((i+1) < str.bytes.length)
						str.bytes[i+1] = ((byte) (strBytes[i / 2] & 0xF));
				}
			}
		}
	}
	
	private static void decryptBytes(Reference objRef, byte[] bytes, SecurityManager sm) throws IOException {
		InputStream is = sm.getEncryptionInputStream(objRef, sm.getDecryptionKey(), new ByteArrayInputStream(bytes), true);
		byte[] buffer = new byte[Math.min(1024, bytes.length)];
		int streamBytePos = 0;
		for (int r; (r = is.read(buffer, 0, buffer.length)) != -1;) {
			System.arraycopy(buffer, 0, bytes, streamBytePos, r);
			streamBytePos += r;
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
	public static Object getObject(Hashtable data, String name, Map objects) {
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
		ByteArrayOutputStream oBaos = (DEBUG_PARSE_PDF ? new ByteArrayOutputStream() : null);
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
					if (oBaos != null) oBaos.write(bytes.peek());
					int b0 = bytes.read();
					peek = bytes.peek();
					if (('0' <= peek) && (peek <= '9')) {
						if (oBaos != null) oBaos.write(bytes.peek());
						int b1 = bytes.read();
						peek = bytes.peek();
						if (('0' <= peek) && (peek <= '9')) {
							if (oBaos != null) oBaos.write(bytes.peek());
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
					if (oBaos != null) oBaos.write(bytes.peek());
					bytes.read();
				}
				else if (peek == 'r') {
					baos.write('\r');
					if (oBaos != null) oBaos.write(bytes.peek());
					bytes.read();
				}
				else if (peek == 't') {
					baos.write('\t');
					if (oBaos != null) oBaos.write(bytes.peek());
					bytes.read();
				}
				else if (peek == 'f') {
					baos.write('\f');
					if (oBaos != null) oBaos.write(bytes.peek());
					bytes.read();
				}
				else if (peek == 'b') {
					baos.write('\b');
					if (oBaos != null) oBaos.write(bytes.peek());
					bytes.read();
				}
				else if (peek == '\r') {
					if (oBaos != null) oBaos.write(bytes.peek());
					bytes.read();
					peek = bytes.peek();
					if (peek == '\n') {
						if (oBaos != null) oBaos.write(bytes.peek());
						bytes.read();
					}
				}
				else if (peek == '\n') {
					if (oBaos != null) oBaos.write(bytes.peek());
					bytes.read();
					peek = bytes.peek();
					if (peek == '\r') {
						if (oBaos != null) oBaos.write(bytes.peek());
						bytes.read();
					}
				}
				else {
					if (oBaos != null) oBaos.write(bytes.peek());
					baos.write(bytes.read());
				}
				escaped = false;
			}
			else if (bytes.peek() == '\\') {
				escaped = true;
				if (oBaos != null) oBaos.write(bytes.peek());
				bytes.read();
			}
			else if (bytes.peek() == '(') {
				if (oBaos != null) oBaos.write(bytes.peek());
				baos.write(bytes.read());
				open++;
			}
			else if (bytes.peek() == ')') {
				open--;
				if (open == 0) {
					bytes.read();
					break;
				}
				else {
					if (oBaos != null) oBaos.write(bytes.peek());
					baos.write(bytes.read());
				}
			}
			else {
				if (oBaos != null) oBaos.write(bytes.peek());
				baos.write(bytes.read());
			}
		}
		if (DEBUG_PARSE_PDF) {
			System.out.println(" -->  " + Arrays.toString(oBaos.toByteArray()));
			System.out.println(" ==> " + Arrays.toString(baos.toByteArray()));
			System.out.println("  ANSI " + new String(baos.toByteArray()));
			System.out.println("  UTF-16LE " + new String(baos.toByteArray(), "UTF-16LE"));
			System.out.println("  UTF-16BE " + new String(baos.toByteArray(), "UTF-16BE"));
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
		return new PString(baos.toByteArray(), hexIsHex2, !hexIsHex2, withSpace);
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
					throw new IOException("Broken array");
			}
			if (bytes.peek() == ']') {
				bytes.read();
				break;
			}
			while (bytes.peek() == '%') {
				skipComment(bytes);
				if (!bytes.skipSpaceCheckEnd())
					throw new IOException("Broken array");
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
		
		/* (non-Javadoc)
		 * @see java.lang.Object#toString()
		 */
		public String toString() {
			return (this.params.toString() + ", [" + this.bytes.length + "]");
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
		
		/** does the binary data come from a HEX with 2 bytes per output byte object? */
		public final boolean isHex2;
		
		/** does the binary data come from a HEX with 4 bytes per output byte object? */
		public final boolean isHex4;
		
		final boolean isHexWithSpace;
		
		final boolean isUtf16beAnsi;
		final boolean isUtf16leAnsi;
		
		PString(byte[] bytes) {
			this(bytes, false, false, false);
		}
		PString(byte[] bytes, boolean isHex2, boolean isHex4, boolean withSpace) {
			this.bytes = bytes;
			this.isHex2 = isHex2;
			this.isHex4 = isHex4;
			this.isHexWithSpace = withSpace;
			if (!this.isHex2 && !this.isHex4 && ((bytes.length & 1) == 0))  {
				int evenZeros = 0;
				int oddZeros = 0;
				for (int b = 0; b < bytes.length; b++)
					if (bytes[b] == 0) {
						if ((b & 1) == 0)
							evenZeros++;
						else oddZeros++;
					}
				this.isUtf16beAnsi = ((evenZeros * 2) == bytes.length);
				this.isUtf16leAnsi = ((oddZeros * 2) == bytes.length);
			}
			else {
				this.isUtf16beAnsi = false;
				this.isUtf16leAnsi = false;
			}
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
				for (int b = 0; b < this.bytes.length; b += 4)
					string.append((char) this.getHex4(b));
			}
			else if (this.isUtf16beAnsi) {
				for (int c = 1; c < this.bytes.length; c+=2)
					string.append((char) convertUnsigned(this.bytes[c]));
			}
			else if (this.isUtf16leAnsi) {
				for (int c = 0; c < this.bytes.length; c+=2)
					string.append((char) convertUnsigned(this.bytes[c]));
			}
			else for (int c = 0; c < this.bytes.length; c++)
				string.append((char) convertUnsigned(this.bytes[c]));
			return string.toString();
		}
		public int length() {
			return (this.isHex4 ? ((this.bytes.length + 3) / 4) : ((this.isHex2 || this.isUtf16beAnsi || this.isUtf16leAnsi) ? ((this.bytes.length + 1) / 2) : this.bytes.length));
		}
		public char charAt(int index) {
			if (this.isHex2)
				return ((char) this.getHex2(index * 2));
			else if (this.isHex4)
				return ((char) this.getHex4(index * 4));
			else if (this.isUtf16beAnsi || this.isUtf16leAnsi)
				return ((char) convertUnsigned(this.bytes[(index * 2) + (this.isUtf16beAnsi ? 1 : 0)]));
			else return ((char) convertUnsigned(this.bytes[index]));
		}
		public CharSequence subSequence(int start, int end) {
			if (this.isHex2 || this.isUtf16beAnsi || this.isUtf16leAnsi) {
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
	 * @param objects the object map to use for dereferencing objects
	 * @return the an image object representing the mask image
	 */
	public static PImage getMaskImage(PStream img, Map objects) {
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
	 * Wrapper object for a raw un-decoded image, also providing a cache
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
		public static final int LEFT_RIGHT_FONT_DIRECTION = 0;
		public static final int BOTTOM_UP_FONT_DIRECTION = 1;
		public static final int TOP_DOWN_FONT_DIRECTION = -1;
		public final String charCodes;
		public final String str;
		public final Rectangle2D bounds;
		public final boolean bold;
		public final boolean italics;
		public final boolean serif;
		public final int fontSize;
		public final int fontDirection;
		public final PdfFont font;
		PWord(String charCodes, String str, Rectangle2D bounds, int fontSize, int fontDirection, PdfFont font) {
			this.charCodes = charCodes;
			this.str = str;
			this.bounds = bounds;
			this.bold = font.bold;
			this.italics = font.italics;
			this.serif = font.serif;
			this.fontSize = fontSize;
			this.fontDirection = fontDirection;
			this.font = font;
		}
		public String toString() {
			return (this.str + " [" + Math.round(this.bounds.getMinX()) + "," + Math.round(this.bounds.getMaxX()) + "," + Math.round(this.bounds.getMinY()) + "," + Math.round(this.bounds.getMaxY()) + "], sized " + this.fontSize + (this.bold ? ", bold" : "") + (this.italics ? ", italic" : ""));
		}
	}
	
	/**
	 * Wrapper for a figure (image embedded in a page) and its properties.
	 * 
	 * @author sautter
	 */
	public static class PFigure {
		public final String name;
		public final Rectangle2D bounds;
		public final double rotation;
		public final boolean rightSideLeft;
		public final boolean upsideDown;
		PFigure(String name, Rectangle2D bounds, double rotation, boolean rightSideLeft, boolean upsideDown) {
			this.name = name;
			this.bounds = bounds;
			this.rotation = rotation;
			this.rightSideLeft = rightSideLeft;
			this.upsideDown = upsideDown;
		}
		public String toString() {
			return (this.name + " [" + Math.round(this.bounds.getMinX()) + "," + Math.round(this.bounds.getMaxX()) + "," + Math.round(this.bounds.getMinY()) + "," + Math.round(this.bounds.getMaxY()) + "]");
		}
	}
	
	/**
	 * Wrapper for words and figures in a PDF page.
	 * 
	 * @author sautter
	 */
	public static class PPageContent {
		public final PWord[] words;
		public final PFigure[] figures;
		PPageContent(PWord[] words, PFigure[] figures) {
			this.words = words;
			this.figures = figures;
		}
	}
	
	/**
	 * Assess which chars from which fonts are actually used in page text. This
	 * method simulates the word extraction process, but without actually
	 * constructing any words. Fonts are loaded only to the basic data, without
	 * any glyph decoding.
	 * @param entries the page entries
	 * @param content the bytes to parse
	 * @param resources the page resource map
	 * @param objects a map holding objects that might be required, such as
	 *            fonts, etc.
	 * @return the words extracted from the page
	 * @throws IOException
	 */
	public static void getPageWordChars(Hashtable entries, byte[] content, Hashtable resources, Map objects, ProgressMonitor pm) throws IOException {
		runPageContent(entries, content, resources, objects, null, null, null, pm);
	}
	
	/**
	 * Parse a content stream of a page and extract the words.
	 * @param entries the page entries
	 * @param content the bytes to parse
	 * @param resources the page resource map
	 * @param objects a map holding objects that might be required, such as
	 *            fonts, etc.
	 * @param tokenizer get tokenizer to check and split words with
	 * @param pm a progress monitor to receive updates on the word extraction
	 *            process
	 * @return the words extracted from the page
	 * @throws IOException
	 */
	public static PWord[] getPageWords(Hashtable entries, byte[] content, Hashtable resources, Map objects, Tokenizer tokenizer, ProgressMonitor pm) throws IOException {
		return getPageWords(entries, content, resources, objects, tokenizer, null, pm);
	}
	
	/**
	 * Parse a content stream of a page and extract the words, collecting
	 * figures along the way.
	 * @param entries the page entries
	 * @param content the bytes to parse
	 * @param resources the page resource map
	 * @param objects a map holding objects that might be required, such as
	 *            fonts, etc.
	 * @param tokenizer get tokenizer to check and split words with
	 * @param pm a progress monitor to receive updates on the word extraction
	 *            process
	 * @return the words extracted from the page
	 * @throws IOException
	 */
	public static PWord[] getPageWords(Hashtable entries, byte[] content, Hashtable resources, Map objects, Tokenizer tokenizer, List figures, ProgressMonitor pm) throws IOException {
		ArrayList words = new ArrayList();
		runPageContent(entries, content, resources, objects, words, tokenizer, figures, pm);
		return ((PWord[]) words.toArray(new PWord[words.size()]));
	}
	
	/**
	 * Parse a content stream of a page and extract the figures.
	 * @param entries the page entries
	 * @param content the bytes to parse
	 * @param resources the page resource map
	 * @param objects a map holding objects that might be required, such as
	 *            fonts, etc.
	 * @param tokenizer get tokenizer to check and split words with
	 * @param pm a progress monitor to receive updates on the word extraction
	 *            process
	 * @return the words extracted from the page
	 * @throws IOException
	 */
	public static PFigure[] getPageFigures(Hashtable entries, byte[] content, Hashtable resources, Map objects, ProgressMonitor pm) throws IOException {
		ArrayList figures = new ArrayList();
		runPageContent(entries, content, resources, objects, null, null, figures, pm);
		return ((PFigure[]) figures.toArray(new PFigure[figures.size()]));
	}
	
	/**
	 * Parse a content stream of a page and extract the words and figures.
	 * @param entries the page entries
	 * @param content the bytes to parse
	 * @param resources the page resource map
	 * @param objects a map holding objects that might be required, such as
	 *            fonts, etc.
	 * @param tokenizer get tokenizer to check and split words with
	 * @param pm a progress monitor to receive updates on the word extraction
	 *            process
	 * @return the words and figures extracted from the page
	 * @throws IOException
	 */
	public static PPageContent getPageContent(Hashtable entries, byte[] content, Hashtable resources, Map objects, Tokenizer tokenizer, ProgressMonitor pm) throws IOException {
		ArrayList words = new ArrayList();
		ArrayList figures = new ArrayList();
		runPageContent(entries, content, resources, objects, words, tokenizer, figures, pm);
		return new PPageContent(((PWord[]) words.toArray(new PWord[words.size()])), ((PFigure[]) figures.toArray(new PFigure[figures.size()])));
	}
	
	private static Hashtable getPageFonts(Hashtable resources, Map objects, boolean decodeChars, ProgressMonitor pm) throws IOException {
		Hashtable fonts = new Hashtable();
		
		//	get font dictionary
		final Object fontsObj = dereference(resources.get("Font"), objects);
		if (PdfFont.DEBUG_LOAD_FONTS) System.out.println(" --> font object is " + fontsObj);
		
		//	anything to work with?
		if ((fontsObj == null) || !(fontsObj instanceof Hashtable))
			return fonts;
		
		//	get fonts
		for (Iterator fit = ((Hashtable) fontsObj).keySet().iterator(); fit.hasNext();) {
			
			//	read basic data
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
			
			//	this one has already been loaded
			if (fontObj instanceof PdfFont) {
				fonts.put(fontKey, ((PdfFont) fontObj));
				if (decodeChars) {
					if (PdfFont.DEBUG_LOAD_FONTS) System.out.println("Decoding chars in font " + ((PdfFont) fontObj).name);
					((PdfFont) fontObj).decodeChars(pm);
				}
				if (PdfFont.DEBUG_LOAD_FONTS && (fontRef != null)) System.out.println("Font cache hit for " + ((Reference) fontRef).getObjectNumber() + " " + ((Reference) fontRef).getGenerationNumber());
			}
			
			//	we need to load this one
			else if (fontObj instanceof Hashtable) {
				if (PdfFont.DEBUG_LOAD_FONTS) System.out.println("Loading font from " + fontObj);
				PdfFont pFont = PdfFont.readFont(fontKey, ((Hashtable) fontObj), objects, true, pm);
				if (pFont != null) {
					if (decodeChars) {
						if (PdfFont.DEBUG_LOAD_FONTS) System.out.println("Decoding chars in font " + ((PdfFont) fontObj).name);
						pFont.decodeChars(pm);
					}
					fonts.put(fontKey, pFont);
					if (fontRef != null) {
						objects.put((((Reference) fontRef).getObjectNumber() + " " + ((Reference) fontRef).getGenerationNumber()), pFont);
						if (PdfFont.DEBUG_LOAD_FONTS) System.out.println("Font cached as " + ((Reference) fontRef).getObjectNumber() + " " + ((Reference) fontRef).getGenerationNumber());
					}
				}
			}
			
			//	this one's out of specification
			else if (PdfFont.DEBUG_LOAD_FONTS) System.out.println("Strange font: " + fontObj);
		}
		
		//	finally ...
		return fonts;
	}
	
	//	factor to apply to a font's normal space width to capture narrow spaces when outputting strings from an array
//	private static final float narrowSpaceToleranceFactor = 0.67f;
//	private static final float narrowSpaceToleranceFactor = 0.33f;
	private static final float narrowSpaceToleranceFactor = 0.25f;
	
	/**
	 * Parse a content stream of a text page and extract the text word by word.
	 * @param entries the page entries
	 * @param content the bytes to parse
	 * @param resources the page resource map
	 * @param objects a map holding objects that might be required, such as
	 *            fonts, etc.
	 * @param tokenizer get tokenizer to check and split words with
	 * @param pm a progress monitor to receive updates on the word extraction
	 *            process
	 * @return the words extracted from the page
	 * @throws IOException
	 */
	public static void runPageContent(Hashtable entries, byte[] content, Hashtable resources, Map objects, List words, Tokenizer tokenizer, List figures, ProgressMonitor pm) throws IOException {
		
		//	get XObject (required for figure extraction and recursive form handling)
		Object xObjectObj = dereference(resources.get("XObject"), objects);
		Hashtable xObject = ((xObjectObj instanceof Hashtable) ? ((Hashtable) xObjectObj) : null);
		
		//	get fonts (unless we're after figures, but not after words)
		Hashtable fonts = (((words == null) && (figures != null)) ? new Hashtable() : getPageFonts(resources, objects, (words != null), pm));
		
		//	create renderer to fill lists
		PageWordRenderer pwr = new PageWordRenderer(fonts, xObject, objects, words, figures);
		
		//	process page content through renderer
		PdfByteInputStream bytes = new PdfByteInputStream(new ByteArrayInputStream(content));
		Object obj;
		LinkedList stack = new LinkedList();
		while ((obj = cropNext(bytes, true, false)) != null) {
			if (obj instanceof PtTag) {
				if (DEBUG_RENDER_PAGE_CONTENT) System.out.println("Content tag: " + ((PtTag) obj).tag);
				pwr.evaluateTag(((PtTag) obj).tag, stack, pm);
			}
			else {
				if (DEBUG_RENDER_PAGE_CONTENT) System.out.println(obj.getClass().getName() + ": " + obj.toString());
				stack.addLast(obj);
			}
		}
		
		//	any words to process further?
		if (words == null)
			return;
		
		//	sort out words with invalid bounding boxes, as well as duplicate words
		if (DEBUG_RENDER_PAGE_CONTENT) System.out.println("Page content processed, got " + words.size() + " words");
		Set wordBounds = new HashSet();
		for (int w = 0; w < words.size(); w++) {
			PWord pw = ((PWord) words.get(w));
			if (DEBUG_RENDER_PAGE_CONTENT) System.out.println(" - '" + pw.str + "' @ " + pw.bounds);
			if ((pw.bounds.getWidth() <= 0) || (pw.bounds.getHeight() <= 0)) {
				if (DEBUG_RENDER_PAGE_CONTENT)
					System.out.println(" --> removed for invalid bounds");
				words.remove(w--);
			}
			else if (!wordBounds.add(pw.bounds.toString())) {
				if (DEBUG_RENDER_PAGE_CONTENT)
					System.out.println(" --> removed as duplicate");
				words.remove(w--);
			}
		}
		
		//	check rotation
		Object rotateObj = dereference(entries.get("Rotate"), objects);
		int rotate = ((rotateObj instanceof Number) ? ((Number) rotateObj).intValue() : 0);
		if (DEBUG_RENDER_PAGE_CONTENT) System.out.println("Page rotation is " + rotate);
		
		//	rotate words and figures
		if (rotate != 0) {
			if (DEBUG_RENDER_PAGE_CONTENT) System.out.println("Rotating words and figures");
			Object mbObj = dereference(entries.get("MediaBox"), objects);
			if (DEBUG_RENDER_PAGE_CONTENT) System.out.println(" - media box is " + mbObj);
			float pgw = -1;
			float pgh = -1;
			if ((mbObj instanceof Vector) && (((Vector) mbObj).size() == 4)) {
				Object pgwObj = ((Vector) mbObj).get(2);
				if (pgwObj instanceof Number)
					pgw = ((Number) pgwObj).floatValue();
				Object pghObj = ((Vector) mbObj).get(3);
				if (pghObj instanceof Number)
					pgh = ((Number) pghObj).floatValue();
			}
			if (DEBUG_RENDER_PAGE_CONTENT) System.out.println(" - page width is " + pgw + ", page height is " + pgh);
			for (int w = 0; w < words.size(); w++) {
				PWord pw = ((PWord) words.get(w));
				if (DEBUG_RENDER_PAGE_CONTENT) System.out.println(" - '" + pw.str + "' @ " + pw.bounds + " directed " + pw.fontDirection);
				int rfd = pw.fontDirection;
				if ((rotate == 90) || (rotate == -270))
					rfd--;
				else if ((rotate == 270) || (rotate == -90))
					rfd++;
				PWord rpw = new PWord(pw.charCodes, pw.str, rotateBounds(pw.bounds, pgw, pgh, rotate), pw.fontSize, rfd, pw.font);
				if (DEBUG_RENDER_PAGE_CONTENT) System.out.println(" --> '" + rpw.str + "' @ " + rpw.bounds + " directed " + rpw.fontDirection);
				words.set(w, rpw);
			}
			for (int f = 0; f < figures.size(); f++) {
				PFigure pf = ((PFigure) figures.get(f));
				if (DEBUG_RENDER_PAGE_CONTENT) System.out.println(" - '" + pf.name + "' @ " + pf.bounds);
				PFigure rpf = new PFigure(pf.name, rotateBounds(pf.bounds, pgw, pgh, rotate), pf.rotation, pf.rightSideLeft, pf.upsideDown);
				if (DEBUG_RENDER_PAGE_CONTENT) System.out.println(" --> '" + rpf.name + "' @ " + rpf.bounds);
				figures.set(f, rpf);
			}
		}
		
		//	join words with extremely close or overlapping bounding boxes (can be split due to in-word font changes, for instance)
		if ((words.size() > 1) && (tokenizer != null)) {
			PWord lastWord = ((PWord) words.get(0));
			double lastWordMiddleX = ((lastWord.bounds.getMinX() + lastWord.bounds.getMaxX()) / 2);
			double lastWordMiddleY = ((lastWord.bounds.getMinY() + lastWord.bounds.getMaxY()) / 2);
			for (int w = 1; w < words.size(); w++) {
				PWord word = ((PWord) words.get(w));
				if (DEBUG_JOIN_WORDS) System.out.println("Checking words " + lastWord.str + " @" + lastWord.bounds.toString() + " and " + word.str + " @" + word.bounds.toString());
				double wordMiddleX = ((word.bounds.getMinX() + word.bounds.getMaxX()) / 2);
				double wordMiddleY = ((word.bounds.getMinY() + word.bounds.getMaxY()) / 2);
				if (!areWordsCompatible(lastWord, lastWordMiddleX, lastWordMiddleY, word, wordMiddleX, wordMiddleY, true, (((word.str.length() != 1) || (COMBINABLE_ACCENTS.indexOf(word.str.charAt(0)) == -1)) && ((lastWord.str.length() != 1) || (COMBINABLE_ACCENTS.indexOf(lastWord.str.charAt(lastWord.str.length()-1)) == -1))))) {
					lastWord = word;
					lastWordMiddleX = wordMiddleX;
					lastWordMiddleY = wordMiddleY;
					if (DEBUG_JOIN_WORDS) System.out.println(" --> incompatible");
					continue;
				}
				
				TokenSequence lastWordTokens = tokenizer.tokenize((COMBINABLE_ACCENTS.indexOf(lastWord.str.charAt(lastWord.str.length()-1)) == -1) ? lastWord.str : lastWord.str.substring(0, (lastWord.str.length()-1)));
				TokenSequence wordTokens = tokenizer.tokenize((COMBINABLE_ACCENTS.indexOf(word.str.charAt(0)) == -1) ? word.str : word.str.substring(1));
				boolean needsSpace = ((lastWordTokens.size() != 0) && (wordTokens.size() != 0) && Gamta.spaceAfter(lastWordTokens.lastValue()) && Gamta.spaceBefore(wordTokens.firstValue()));
				
				if (!areWordsCloseEnough(lastWord, word, needsSpace)) {
					lastWord = word;
					lastWordMiddleX = wordMiddleX;
					lastWordMiddleY = wordMiddleY;
					if (DEBUG_JOIN_WORDS) System.out.println(" --> too far apart");
					continue;
				}
				
				TokenSequence jointWordTokens = tokenizer.tokenize(((COMBINABLE_ACCENTS.indexOf(lastWord.str.charAt(lastWord.str.length()-1)) == -1) ? lastWord.str : lastWord.str.substring(0, (lastWord.str.length()-1))) + ((COMBINABLE_ACCENTS.indexOf(word.str.charAt(0)) == -1) ? word.str : word.str.substring(1)));
				if ((1 < jointWordTokens.size()) && ((lastWordTokens.size() + wordTokens.size()) <= jointWordTokens.size())) {
					lastWord = word;
					lastWordMiddleY = wordMiddleY;
					if (DEBUG_JOIN_WORDS) System.out.println(" --> tokenization mismatch");
					continue;
				}
				
				float minX = ((float) Math.min(lastWord.bounds.getMinX(), word.bounds.getMinX()));
				float maxX = ((float) Math.max(lastWord.bounds.getMaxX(), word.bounds.getMaxX()));
				float minY = ((float) Math.min(lastWord.bounds.getMinY(), word.bounds.getMinY()));
				float maxY = ((float) Math.max(lastWord.bounds.getMaxY(), word.bounds.getMaxY()));
				Rectangle2D.Float jointWordBox = new Rectangle2D.Float(minX, minY, (maxX - minX), (maxY - minY));
				PWord jointWord;
				
				//	test if second word (a) starts with combinable accent, (b) this accent is combinable with last char of first word, and (c) there is sufficient overlap
				if (areWordsCloseEnoughForCombinedAccent(lastWord, word) && (COMBINABLE_ACCENTS.indexOf(word.str.charAt(0)) != -1)) {
					if (DEBUG_RENDER_PAGE_CONTENT || DEBUG_JOIN_WORDS) {
						System.out.println("Testing for possible letter diacritic merger:");
						System.out.println(" - first word ends with '" + lastWord.str.charAt(lastWord.str.length() - 1) + "' (" + ((int) lastWord.str.charAt(lastWord.str.length() - 1)) + ")");
						System.out.println(" - second word starts with '" + word.str.charAt(0) + "' (" + ((int) word.str.charAt(0)) + ")");
					}
					String combinedCharName = ("" + StringUtils.getBaseChar(lastWord.str.charAt(lastWord.str.length() - 1)) + "" + COMBINABLE_ACCENT_MAPPINGS.get(new Character(word.str.charAt(0))));
					if (DEBUG_RENDER_PAGE_CONTENT || DEBUG_JOIN_WORDS)
						System.out.println(" - combined char name is '" + combinedCharName + "'");
					char combinedChar = StringUtils.getCharForName(combinedCharName);
					if (DEBUG_RENDER_PAGE_CONTENT || DEBUG_JOIN_WORDS)
						System.out.println(" - combined char is '" + combinedChar + "' (" + ((int) combinedChar) + ")");
					if (combinedChar > 0) {
						jointWord = new PWord((lastWord.charCodes.substring(0, lastWord.charCodes.length()-1) + ((char) (((int) lastWord.charCodes.charAt(lastWord.charCodes.length()-1)) & 255)) + word.charCodes), (lastWord.str.substring(0, lastWord.str.length()-1) + combinedChar + word.str.substring(1)), jointWordBox, Math.max(lastWord.fontSize, word.fontSize), lastWord.fontDirection, lastWord.font);
						if (DEBUG_RENDER_PAGE_CONTENT || DEBUG_JOIN_WORDS)
							System.out.println(" --> combined letter and diacritic marker");
					}
					else {
						jointWord = new PWord((lastWord.charCodes + word.charCodes), (lastWord.str + word.str), jointWordBox, Math.max(lastWord.fontSize, word.fontSize), lastWord.fontDirection, lastWord.font);
						if (DEBUG_RENDER_PAGE_CONTENT || DEBUG_JOIN_WORDS)
							System.out.println(" --> combination not possible");
					}
				}
				
				//	test if first word (a) ends with combinable accent, (b) this accent is combinable with first char of second word, and (c) there is sufficient overlap
				else if (areWordsCloseEnoughForCombinedAccent(lastWord, word) && (COMBINABLE_ACCENTS.indexOf(lastWord.str.charAt(lastWord.str.length()-1)) != -1)) {
					if (DEBUG_RENDER_PAGE_CONTENT || DEBUG_JOIN_WORDS) {
						System.out.println("Testing for possible letter diacritic merger:");
						System.out.println(" - first word ends with '" + lastWord.str.charAt(lastWord.str.length() - 1) + "' (" + ((int) lastWord.str.charAt(lastWord.str.length() - 1)) + ")");
						System.out.println(" - second word starts with '" + word.str.charAt(0) + "' (" + ((int) word.str.charAt(0)) + ")");
					}
					String combinedCharName = ("" + StringUtils.getBaseChar(word.str.charAt(0)) + "" + COMBINABLE_ACCENT_MAPPINGS.get(new Character(lastWord.str.charAt(lastWord.str.length() - 1))));
					if (DEBUG_RENDER_PAGE_CONTENT || DEBUG_JOIN_WORDS)
						System.out.println(" - combined char name is '" + combinedCharName + "'");
					char combinedChar = StringUtils.getCharForName(combinedCharName);
					if (DEBUG_RENDER_PAGE_CONTENT || DEBUG_JOIN_WORDS)
						System.out.println(" - combined char is '" + combinedChar + "' (" + ((int) combinedChar) + ")");
					if (combinedChar > 0) {
						jointWord = new PWord((lastWord.charCodes + ((char) (((int) word.charCodes.charAt(0)) & 255)) + word.charCodes.substring(1)), (lastWord.str.substring(0, lastWord.str.length()-1) + combinedChar + word.str.substring(1)), jointWordBox, Math.max(lastWord.fontSize, word.fontSize), lastWord.fontDirection, lastWord.font);
						if (DEBUG_RENDER_PAGE_CONTENT || DEBUG_JOIN_WORDS)
							System.out.println(" --> combined letter and diacritic marker");
					}
					else {
						jointWord = new PWord((lastWord.charCodes + word.charCodes), (lastWord.str + word.str), jointWordBox, Math.max(lastWord.fontSize, word.fontSize), lastWord.fontDirection, lastWord.font);
						if (DEBUG_RENDER_PAGE_CONTENT || DEBUG_JOIN_WORDS)
							System.out.println(" --> combination not possible");
					}
				}
				else jointWord = new PWord((lastWord.charCodes + word.charCodes), (lastWord.str + word.str), jointWordBox, Math.max(lastWord.fontSize, word.fontSize), lastWord.fontDirection, lastWord.font);
				
				if (DEBUG_RENDER_PAGE_CONTENT || DEBUG_JOIN_WORDS) {
					System.out.println("Got joint word: '" + jointWord.str + "' @ " + jointWord.bounds);
					System.out.println(" --> extent is " + ((float) jointWordBox.getMinX()) + "-" + ((float) jointWordBox.getMaxX()) + " x " + ((float) jointWordBox.getMaxY()) + "-" + ((float) jointWordBox.getMinY()));
					System.out.println(" --> base extents are\n" +
							"     " + ((float) lastWord.bounds.getMinX()) + "-" + ((float) lastWord.bounds.getMaxX()) + " x " + ((float) lastWord.bounds.getMaxY()) + "-" + ((float) lastWord.bounds.getMinY()) + "\n" +
							"     " + ((float) word.bounds.getMinX()) + "-" + ((float) word.bounds.getMaxX()) + " x " + ((float) word.bounds.getMaxY()) + "-" + ((float) word.bounds.getMinY()) +
						"");
				}
				
				words.set((w-1), jointWord);
				words.remove(w--);
				lastWord = jointWord;
				lastWordMiddleX = ((jointWord.bounds.getMinX() + jointWord.bounds.getMaxX()) / 2);
				lastWordMiddleY = ((jointWord.bounds.getMinY() + jointWord.bounds.getMaxY()) / 2);
			}
		}
		
		//	un-tangle overlapping bounding boxes that did not merge
		if ((words.size() > 1) && (tokenizer != null)) {
			PWord lastWord = ((PWord) words.get(0));
			for (int w = 1; w < words.size(); w++) {
				PWord word = ((PWord) words.get(w));
				if (DEBUG_JOIN_WORDS) System.out.println("Checking words " + lastWord.str + " @" + lastWord.bounds.toString() + " and " + word.str + " @" + word.bounds.toString());
				
				//	no overlap
				if (!lastWord.bounds.intersects(word.bounds)) {
					lastWord = word;
					if (DEBUG_JOIN_WORDS) System.out.println(" --> no overlap");
					continue;
				}
				
				//	different font directions, skip for now
				if (lastWord.fontDirection != word.fontDirection) {
					lastWord = word;
					if (DEBUG_JOIN_WORDS) System.out.println(" --> different font directions");
					continue;
				}
				
				//	check if words on same line
				if (word.fontDirection == PWord.LEFT_RIGHT_FONT_DIRECTION) {
					float lwCenterY = ((float) ((lastWord.bounds.getMinY() + lastWord.bounds.getMaxY()) / 2));
					float wCenterY = ((float) ((word.bounds.getMinY() + word.bounds.getMaxY()) / 2));
					if ((lwCenterY < word.bounds.getMaxY()) && (lwCenterY > word.bounds.getMinY())) { /* last word center Y inside word height */ }
					else if ((wCenterY < lastWord.bounds.getMaxY()) && (wCenterY > lastWord.bounds.getMinY())) { /* word center Y inside last word height */ }
					else {
						lastWord = word;
						if (DEBUG_JOIN_WORDS) System.out.println(" --> different lines");
						continue;
					}
				}
				else {
					float lwMinX = ((float) Math.min(lastWord.bounds.getMinX(), lastWord.bounds.getMaxX()));
					float lwMaxX = ((float) Math.max(lastWord.bounds.getMinX(), lastWord.bounds.getMaxX()));
					float lwCenterX = ((float) ((lastWord.bounds.getMinX() + lastWord.bounds.getMaxX()) / 2));
					float wMinX = ((float) Math.min(word.bounds.getMinX(), word.bounds.getMaxX()));
					float wMaxX = ((float) Math.max(word.bounds.getMinX(), word.bounds.getMaxX()));
					float wCenterX = ((float) ((word.bounds.getMinX() + word.bounds.getMaxX()) / 2));
					if ((lwCenterX < wMaxX) && (lwCenterX > wMinX)) { /* last word center Y inside word height (turned by 90°) */ }
					else if ((wCenterX < lwMaxX) && (wCenterX > lwMinX)) { /* word center Y inside last word height (turned by 90°) */ }
					else {
						lastWord = word;
						if (DEBUG_JOIN_WORDS) System.out.println(" --> different lines");
						continue;
					}
				}
				
				//	compute cut word box, depending on font direction
				Rectangle2D.Float cutWordBox;
				if (word.fontDirection == PWord.LEFT_RIGHT_FONT_DIRECTION)
					cutWordBox = new Rectangle2D.Float(((float) lastWord.bounds.getMinX()), ((float) lastWord.bounds.getMinY()), ((float) (word.bounds.getMinX() - lastWord.bounds.getMinX())), ((float) lastWord.bounds.getHeight()));
				else if (lastWord.fontDirection == PWord.BOTTOM_UP_FONT_DIRECTION)
					cutWordBox = new Rectangle2D.Float(((float) lastWord.bounds.getMinX()), ((float) lastWord.bounds.getMinY()), ((float) lastWord.bounds.getWidth()), ((float) (word.bounds.getMinY() - lastWord.bounds.getMinY())));
				else if (lastWord.fontDirection == PWord.TOP_DOWN_FONT_DIRECTION)
					cutWordBox = new Rectangle2D.Float(((float) lastWord.bounds.getMinX()), ((float) lastWord.bounds.getMinY()), ((float) lastWord.bounds.getWidth()), ((float) (word.bounds.getMinY() - lastWord.bounds.getMinY())));
				else continue; // never gonna happen, but Java don't know
				
				//	cut previous word
				PWord cutWord = new PWord(lastWord.charCodes, lastWord.str, cutWordBox, lastWord.fontSize, lastWord.fontDirection, lastWord.font);
				if (DEBUG_RENDER_PAGE_CONTENT || DEBUG_JOIN_WORDS) System.out.println("Got cut word: '" + cutWord.str + "' @ " + cutWord.bounds);
				words.set((w-1), cutWord);
				lastWord = word;
			}
		}
	}
	
	private static Rectangle2D rotateBounds(Rectangle2D bounds, float pw, float ph, int rotate) {
		if ((rotate == 90) || (rotate == -270)) {
			float rx = ((float) bounds.getMinY());
			float ry = ((float) (pw - bounds.getMaxX()));
			return new Rectangle2D.Float(rx, ry, ((float) bounds.getHeight()), ((float) bounds.getWidth()));
		}
		else if ((rotate == 270) || (rotate == -90)) {
			float rx = ((float) (ph - bounds.getMaxY()));
			float ry = ((float) bounds.getMinX());
			return new Rectangle2D.Float(rx, ry, ((float) bounds.getHeight()), ((float) bounds.getWidth()));
		}
		else if ((rotate == 180) || (rotate == -180)) {
			float rx = ((float) (pw - bounds.getMaxX()));
			float ry = ((float) (ph - bounds.getMaxY()));
			return new Rectangle2D.Float(rx, ry, ((float) bounds.getWidth()), ((float) bounds.getHeight()));
		}
		else return bounds;
	}
	
	private static boolean areWordsCloseEnoughForCombinedAccent(PWord lastWord, PWord word) {
		if (lastWord.fontDirection == PWord.LEFT_RIGHT_FONT_DIRECTION)
			return ((word.bounds.getMinX() - 1) < lastWord.bounds.getMaxX());
		else if (lastWord.fontDirection == PWord.BOTTOM_UP_FONT_DIRECTION)
			return ((word.bounds.getMinY() - 1) < lastWord.bounds.getMaxY());
		else if (lastWord.fontDirection == PWord.TOP_DOWN_FONT_DIRECTION)
			return ((word.bounds.getMaxY() - 1) > lastWord.bounds.getMinY());
		else return false;
	}
	
	private static boolean areWordsCloseEnough(PWord lastWord, PWord word, boolean needsSpace) {
		double wordDistance;
		if (lastWord.fontDirection == PWord.LEFT_RIGHT_FONT_DIRECTION)
			wordDistance = (word.bounds.getMinX() - lastWord.bounds.getMaxX());
		else if (lastWord.fontDirection == PWord.BOTTOM_UP_FONT_DIRECTION)
			wordDistance = (word.bounds.getMinY() - lastWord.bounds.getMaxY());
		else if (lastWord.fontDirection == PWord.TOP_DOWN_FONT_DIRECTION)
			wordDistance = (word.bounds.getMaxY() - lastWord.bounds.getMinY());
		else return false;
		
		//	are we close enough
		return (wordDistance < (1 - (needsSpace ? narrowSpaceToleranceFactor : 0)));
	}
	
	private static boolean areWordsCompatible(PWord lastWord, double lastWordMiddleX, double lastWordMiddleY, PWord word, double wordMiddleX, double wordMiddleY, boolean checkStyle, boolean checkHorizontalAlignment) {
		
		//	compare basic properties
		if (checkStyle && ((lastWord.bold != word.bold) || (lastWord.italics != word.italics) || (lastWord.font.hasDescent != word.font.hasDescent) || (lastWord.fontDirection != word.fontDirection))) {
			if (DEBUG_JOIN_WORDS) System.out.println(" --> font style mismatch");
			return false;
		}
		
		//	check in-line alignment
		if ((lastWord.fontDirection == PWord.LEFT_RIGHT_FONT_DIRECTION) ? ((lastWordMiddleY < word.bounds.getMinY()) || (lastWordMiddleY > word.bounds.getMaxY())) : ((lastWordMiddleX < word.bounds.getMinX()) || (lastWordMiddleX > word.bounds.getMaxX()))) {
			if (DEBUG_JOIN_WORDS) System.out.println(" --> vertical alignment mismatch (1)");
			return false;
		}
		if ((lastWord.fontDirection == PWord.LEFT_RIGHT_FONT_DIRECTION) ? ((wordMiddleY < lastWord.bounds.getMinY()) || (wordMiddleY > lastWord.bounds.getMaxY())) : ((wordMiddleX < lastWord.bounds.getMinX()) || (wordMiddleX > lastWord.bounds.getMaxX()))) {
			if (DEBUG_JOIN_WORDS) System.out.println(" --> vertical alignment mismatch (2)");
			return false;
		}
		
		//	check distance
		if (checkHorizontalAlignment && (lastWord.fontDirection == PWord.LEFT_RIGHT_FONT_DIRECTION) && ((word.bounds.getMinX() < lastWord.bounds.getMinX()) || (word.bounds.getMaxX() < lastWord.bounds.getMaxX()))) {
			if (DEBUG_JOIN_WORDS) System.out.println(" --> horizontal alignment mismatch (1)");
			return false;
		}
		if (checkHorizontalAlignment && (lastWord.fontDirection == PWord.BOTTOM_UP_FONT_DIRECTION) && ((word.bounds.getMinY() < lastWord.bounds.getMinY()) || (word.bounds.getMaxY() < lastWord.bounds.getMaxY()))) {
			if (DEBUG_JOIN_WORDS) System.out.println(" --> horizontal alignment mismatch (2)");
			return false;
		}
		if (checkHorizontalAlignment && (lastWord.fontDirection == PWord.TOP_DOWN_FONT_DIRECTION) && ((word.bounds.getMinY() > lastWord.bounds.getMinY()) || (word.bounds.getMaxY() > lastWord.bounds.getMaxY()))) {
			if (DEBUG_JOIN_WORDS) System.out.println(" --> horizontal alignment mismatch (3)");
			return false;
		}
		
		//	OK, we didn't find any red flags about these two
		return true;
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
	
	private static final boolean DEBUG_RENDER_PAGE_CONTENT = true;
	private static final boolean DEBUG_JOIN_WORDS = false;
	
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
		
		private Hashtable fonts;
		private Hashtable xObject;
		private Map objects;
		
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
		private int eFontDirection = PWord.LEFT_RIGHT_FONT_DIRECTION;
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
		
		private List words;
		private List figures;
		
//		//	constructor for text char extraction
//		PageWordRenderer(Hashtable fonts) {
//			this.fonts = fonts;
//			this.words = null;
//			this.figures = null;
//			for (int c = 0; c < this.textMatrix.length; c++) {
//				Arrays.fill(this.textMatrix[c], 0);
//				this.textMatrix[c][c] = 1;
//			}
//			cloneValues(this.textMatrix, this.lineMatrix);
//			//	TODO consider switching off all the computational rigmarole if word list is null
//		}
//		
//		//	constructor for actual word extraction
		PageWordRenderer(Hashtable fonts, Hashtable xObject, Map objects, List words, List figures) {
			this.fonts = fonts;
			this.xObject = xObject;
			this.objects = objects;
			this.words = words;
			this.figures = figures;
			for (int c = 0; c < this.textMatrix.length; c++) {
				Arrays.fill(this.textMatrix[c], 0);
				this.textMatrix[c][c] = 1;
			}
			cloneValues(this.textMatrix, this.lineMatrix);
		}
		
		private void doBT(LinkedList stack) {
			if (DEBUG_RENDER_PAGE_CONTENT && (this.words != null))
				System.out.println(" --> start text");
			for (int c = 0; c < this.textMatrix.length; c++) {
				Arrays.fill(this.textMatrix[c], 0);
				this.textMatrix[c][c] = 1;
			}
			cloneValues(this.textMatrix, this.lineMatrix);
		}
		
		private void doET(LinkedList stack) {
			if (DEBUG_RENDER_PAGE_CONTENT && (this.words != null))
				System.out.println(" --> end text");
		}
		
		private void doBMC(LinkedList stack) {
			Object n = stack.removeLast();
			if (DEBUG_RENDER_PAGE_CONTENT && (this.words != null))
				System.out.println(" --> start marked content: " + n);
		}
		
		private void doBDC(LinkedList stack) {
			Object d = stack.removeLast();
			Object n = stack.removeLast();
			if (DEBUG_RENDER_PAGE_CONTENT && (this.words != null))
				System.out.println(" --> start marked content with dictionary: " + n + " - " + d);
		}
		
		private void doEMC(LinkedList stack) {
			if (DEBUG_RENDER_PAGE_CONTENT && (this.words != null))
				System.out.println(" --> end marked content");
		}
		
		private void doMP(LinkedList stack) {
			Object n = stack.removeLast();
			if (DEBUG_RENDER_PAGE_CONTENT && (this.words != null))
				System.out.println(" --> mark point: " + n);
		}
		
		private void doDP(LinkedList stack) {
			Object d = stack.removeLast();
			Object n = stack.removeLast();
			if (DEBUG_RENDER_PAGE_CONTENT && (this.words != null))
				System.out.println(" --> mark point with dictionary: " + n + " - " + d);
		}
		
		private void doq(LinkedList stack) {
			this.graphicsStateStack.addLast(new SavedGraphicsState());
			if (DEBUG_RENDER_PAGE_CONTENT && (this.words != null))
				System.out.println(" --> save graphics state");
		}
		
		private void doQ(LinkedList stack) {
			((SavedGraphicsState) this.graphicsStateStack.removeLast()).restore();
			if (DEBUG_RENDER_PAGE_CONTENT && (this.words != null)) {
				System.out.println(" --> restore graphics state:");
				for (Iterator tmit = this.pwrTransformationMatrices.iterator(); tmit.hasNext();) {
					float[][] tm = ((float[][]) tmit.next());
					printMatrix(tm);
					if (tmit.hasNext())
						System.out.println();
				}
			}
			this.computeEffectiveFontSizeAndDirection();
		}
		
		private void docm(LinkedList stack) {
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
			if (DEBUG_RENDER_PAGE_CONTENT && (this.words != null)) {
				System.out.println(" --> transformation matrix stack set to");
				for (Iterator tmit = this.pwrTransformationMatrices.iterator(); tmit.hasNext();) {
					float[][] tm = ((float[][]) tmit.next());
					printMatrix(tm);
					if (tmit.hasNext())
						System.out.println();
				}
			}
			this.computeEffectiveFontSizeAndDirection();
		}
		
		private float[] applyTransformationMatrices(float[] f) {
			for (Iterator tmit = this.pwrTransformationMatrices.iterator(); tmit.hasNext();) {
				float[][] tm = ((float[][]) tmit.next());
				f = transform(f, tm);
			}
			return f;
		}
		
		// d i j J M w gs
		private void dod(LinkedList stack)  {
			if (DEBUG_RENDER_PAGE_CONTENT && (this.words != null))
				System.out.println(" --> dash pattern");
		}
		
		private void doi(LinkedList stack)  {
			if (DEBUG_RENDER_PAGE_CONTENT && (this.words != null))
				System.out.println(" --> flatness");
		}
		
		private void doj(LinkedList stack) {
			if (DEBUG_RENDER_PAGE_CONTENT && (this.words != null))
				System.out.println(" --> line join");
		}
		
		private void doJ(LinkedList stack) {
			if (DEBUG_RENDER_PAGE_CONTENT && (this.words != null))
				System.out.println(" --> line ends");
		}
		
		private void doM(LinkedList stack) {
			if (DEBUG_RENDER_PAGE_CONTENT && (this.words != null))
				System.out.println(" --> line miter limit");
		}
		
		private void dow(LinkedList stack) {
			if (DEBUG_RENDER_PAGE_CONTENT && (this.words != null))
				System.out.println(" --> line width");
		}
		
		private void dogs(LinkedList stack) {
			if (DEBUG_RENDER_PAGE_CONTENT && (this.words != null))
				System.out.println(" --> graphics state to " + stack.getLast());
		}
		
		private void doTc(LinkedList stack) {
			this.pwrCharSpacing = ((Number) stack.removeLast()).floatValue();
			if (DEBUG_RENDER_PAGE_CONTENT && (this.words != null))
				System.out.println(" --> char spacing " + this.pwrCharSpacing);
		}
		
		private void doTw(LinkedList stack) {
			this.pwrWordSpacing = ((Number) stack.removeLast()).floatValue();
			if (DEBUG_RENDER_PAGE_CONTENT && (this.words != null))
				System.out.println(" --> word spacing " + this.pwrWordSpacing);
		}
		
		private void doTz(LinkedList stack) {
			this.pwrHorizontalScaling = ((Number) stack.removeLast()).floatValue();
			if (DEBUG_RENDER_PAGE_CONTENT && (this.words != null))
				System.out.println(" --> horizontal scaling " + this.pwrHorizontalScaling);
		}
		
		private void doTL(LinkedList stack) {
			this.pwrLineHeight = ((Number) stack.removeLast()).floatValue();
			if (DEBUG_RENDER_PAGE_CONTENT && (this.words != null))
				System.out.println(" --> leading (line height) " + this.pwrLineHeight);
		}
		
		private void doTf(LinkedList stack) {
			this.pwrFontSize = ((Number) stack.removeLast()).floatValue();
			this.pwrFontKey = stack.removeLast();
			if (DEBUG_RENDER_PAGE_CONTENT) System.out.println(" --> font key set to " + this.pwrFontKey);
			this.pwrFont = ((PdfFont) this.fonts.get(this.pwrFontKey));
			if (this.pwrFont != null) {
				this.pwrFontSpaceWidth = this.pwrFont.getCharWidth(' ');
				if (DEBUG_RENDER_PAGE_CONTENT) {
					System.out.println(" --> font " + this.pwrFontKey + " sized " + this.pwrFontSize);
					System.out.println("   - font is " + this.pwrFont.data);
					System.out.println("   - font descriptor " + this.pwrFont.descriptor);
					System.out.println("   - font name " + this.pwrFont.name);
					System.out.println("   - bold: " + this.pwrFont.bold + ", italic: " + this.pwrFont.italics);
					System.out.println("   - space width is " + this.pwrFontSpaceWidth);
					System.out.println("   - implicit space: " + this.pwrFont.hasImplicitSpaces);
				}
				this.computeEffectiveFontSizeAndDirection();
			}
		}
		
		private void doTfs(LinkedList stack) {
			this.pwrFontSize = ((Number) stack.removeLast()).floatValue();
			if (DEBUG_RENDER_PAGE_CONTENT && (this.words != null))
				System.out.println(" --> font size " + this.pwrFontSize);
			this.computeEffectiveFontSizeAndDirection();
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
			if (DEBUG_RENDER_PAGE_CONTENT && (this.words != null)) {
				System.out.println(" --> text matrix set to");
				printMatrix(this.lineMatrix);
			}
			this.computeEffectiveFontSizeAndDirection();
		}
		
		//	compute effective font size as round(font size * ((a + d) / 2)) in text matrix
		private void computeEffectiveFontSizeAndDirection() {
			float[] pwrFontSize = {0, this.pwrFontSize, 0};
			pwrFontSize = this.applyTransformationMatrices(transform(pwrFontSize, this.textMatrix));
			if (DEBUG_RENDER_PAGE_CONTENT && (this.words != null))
				System.out.println(" ==> font analysis vector is " + Arrays.toString(pwrFontSize));
			this.eFontSize = ((float) Math.sqrt((pwrFontSize[0] * pwrFontSize[0]) + (pwrFontSize[1] * pwrFontSize[1])));
			if (DEBUG_RENDER_PAGE_CONTENT && (this.words != null))
				System.out.println(" ==> effective font size is " + this.eFontSize);
			if (Math.abs(pwrFontSize[1]) > Math.abs(pwrFontSize[0])) {
				this.eFontDirection = PWord.LEFT_RIGHT_FONT_DIRECTION;
				if (DEBUG_RENDER_PAGE_CONTENT && (this.words != null))
					System.out.println(" ==> effective font direction is left-right");
			}
			else if (pwrFontSize[0] < 0) {
				this.eFontDirection = PWord.BOTTOM_UP_FONT_DIRECTION;
				if (DEBUG_RENDER_PAGE_CONTENT && (this.words != null))
					System.out.println(" ==> effective font direction is bottom-up");
			}
			else {
				this.eFontDirection = PWord.TOP_DOWN_FONT_DIRECTION;
				if (DEBUG_RENDER_PAGE_CONTENT && (this.words != null))
					System.out.println(" ==> effective font direction is top-down");
			}
		}
		
		private void doTr(LinkedList stack) {
			Object r = stack.removeLast();
			if (DEBUG_RENDER_PAGE_CONTENT && (this.words != null))
				System.out.println(" --> text rendering mode " + r);
		}
		
		private void doTs(LinkedList stack) {
			this.pwrTextRise = ((Number) stack.removeLast()).floatValue();
			if (DEBUG_RENDER_PAGE_CONTENT && (this.words != null))
				System.out.println(" --> text rise " + this.pwrTextRise);
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
			if (DEBUG_RENDER_PAGE_CONTENT && (this.words != null))
				System.out.println("Moving to new line");
			updateMatrix(this.textMatrix, ((Number) tx).floatValue(), ((Number) ty).floatValue());
			cloneValues(this.textMatrix, this.lineMatrix);
			if (DEBUG_RENDER_PAGE_CONTENT && (this.words != null)) {
				System.out.println(" --> new line, offset by " + tx + "/" + ty + (setLineHeight ? (", and new leading (line height " + this.pwrLineHeight + ")") : ""));
				printMatrix(this.lineMatrix);
			}
		}
		
		void evaluateTag(String tag, LinkedList stack, ProgressMonitor pm) throws IOException {
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
			
			//	TODO keep track of text color (G, g, RG, and rg tags) to identify water marks (light gray text)
			
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
			
			else if ("Do".equals(tag))
				this.doDo(stack, pm);
			
			else if (DEBUG_RENDER_PAGE_CONTENT) System.out.println(" ==> UNKNOWN");
		}
		
		private void doDo(LinkedList stack, ProgressMonitor pm) {
			String name = stack.removeLast().toString();
			if (DEBUG_RENDER_PAGE_CONTENT)
				System.out.println(" ==> object name is " + name);
			
			Object objObj = dereference(this.xObject.get(name), this.objects);
			if (DEBUG_RENDER_PAGE_CONTENT)
				System.out.println(" ==> object is " + objObj);
			if (!(objObj instanceof PStream))
				return;
			
			PStream obj = ((PStream) objObj);
			Object objTypeObj = obj.params.get("Subtype");
			if (DEBUG_RENDER_PAGE_CONTENT)
				System.out.println(" ==> object type is " + objTypeObj);
			if (objTypeObj == null)
				return;
			
			if ("Image".equals(objTypeObj.toString()))
				this.doDoFigure(name);
			else if ("Form".equals(objTypeObj.toString())) try {
				this.doDoForm(obj, pm);
				if (DEBUG_RENDER_PAGE_CONTENT)
					System.out.println(" ==> form object " + name + " handled");
			}
			catch (IOException ioe) {
				System.out.println(" ==> exception handling form object " + ioe.getMessage());
				ioe.printStackTrace(System.out);
			}
		}
		
		private void doDoForm(PStream form, ProgressMonitor pm) throws IOException {
			Object formResObj = dereference(form.params.get("Resources"), this.objects);
			if (!(formResObj instanceof Hashtable))
				return;
			Hashtable formResources = ((Hashtable) formResObj);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			Object filter = form.params.get("Filter");
			PdfParser.decode(filter, form.bytes, form.params, baos, objects);
			byte[] formContent = baos.toByteArray();
			/* TODO observe scaling matrix (once we have a respective example PDF):
			 * - use form specific list for words and figures
			 * - add scaled words and figures to main lists
			 * - use same measurement vectors as for images */
			ArrayList formWords = ((this.words == null) ? null : new ArrayList());
			ArrayList formFigures = ((this.figures == null) ? null : new ArrayList());
			runPageContent(form.params, formContent, formResources, this.objects, formWords, null, formFigures, pm);
			if (formWords != null)
				this.words.addAll(formWords);
			if (formFigures != null)
				this.figures.addAll(formFigures);
		}
		
		private void doDoFigure(String name) {
//			//	new computation, figuring in rotation, according to
//			//	http://math.stackexchange.com/questions/13150/extracting-rotation-scale-values-from-2d-transformation-matrix/13165#13165
			
			float[] translate = {0, 0, 1};
			translate = this.applyTransformationMatrices(translate);
			if (DEBUG_RENDER_PAGE_CONTENT && (this.figures != null))
				System.out.println(" ==> image rendering origin " + Arrays.toString(translate));
			
			float[] scaleRotate1 = {1, 0, 0};
			scaleRotate1 = this.applyTransformationMatrices(scaleRotate1);
			if (DEBUG_RENDER_PAGE_CONTENT && (this.figures != null))
				System.out.println(" ==> image rotation and scaling 1 " + Arrays.toString(scaleRotate1));
			float[] scaleRotate2 = {0, 1, 0};
			scaleRotate2 = this.applyTransformationMatrices(scaleRotate2);
			if (DEBUG_RENDER_PAGE_CONTENT && (this.figures != null))
				System.out.println(" ==> image rotation and scaling 2 " + Arrays.toString(scaleRotate2));
			
			float scaleX = ((float) Math.sqrt((scaleRotate1[0] * scaleRotate1[0]) + (scaleRotate2[0] * scaleRotate2[0])));
			float scaleY = ((float) Math.sqrt((scaleRotate1[1] * scaleRotate1[1]) + (scaleRotate2[1] * scaleRotate2[1])));
			
			double rotation;
			boolean rightSideLeft;
			boolean upsideDown;
			if ((scaleRotate1[1] == 0) && (scaleRotate2[0] == 0)) {
				if ((scaleRotate1[0] < 0) && (scaleRotate2[1] < 0)) {
					rotation = Math.PI;
					rightSideLeft = false;
					upsideDown = false;
					if (DEBUG_RENDER_PAGE_CONTENT && (this.figures != null))
						System.out.println(" ==> image rotation is " + rotation + " (for horizontal and vertical flip) = " + ((180.0 / Math.PI) * rotation) + "°");
				}
				else {
					rotation = 0;
					if (DEBUG_RENDER_PAGE_CONTENT && (this.figures != null))
						System.out.println(" ==> image rotation is " + rotation + "°");
					if (scaleRotate1[0] < 0) {
						rightSideLeft = true;
						upsideDown = false;
						if (DEBUG_RENDER_PAGE_CONTENT && (this.figures != null))
							System.out.println(" ==> figure flipped horizontally (right side left)");
					}
					else if (scaleRotate2[1] < 0) {
						rightSideLeft = false;
						upsideDown = true;
						if (DEBUG_RENDER_PAGE_CONTENT && (this.figures != null))
							System.out.println(" ==> figure flipped vertically (upside down)");
					}
					else {
						rightSideLeft = false;
						upsideDown = false;
					}
				}
			}
			else {
				double rotation1 = Math.atan2(-scaleRotate1[1], scaleRotate1[0]);
				double rotation2 = Math.atan2(scaleRotate2[0], scaleRotate2[1]);
				rotation = ((rotation1 + rotation2) / 2);
				rightSideLeft = false;
				upsideDown = false;
				if (DEBUG_RENDER_PAGE_CONTENT && (this.figures != null))
					System.out.println(" ==> image rotation is " + rotation + " (" + rotation1 + "/" + rotation2 + ") = " + ((180.0 / Math.PI) * rotation) + "°");
			}
			
			Rectangle2D bounds;
			if (Math.abs(((180.0 / Math.PI) * rotation) - 90) < 5)
				bounds = new Rectangle2D.Float(translate[0], (translate[1] - scaleY), scaleX, scaleY);
			else if (Math.abs(((180.0 / Math.PI) * rotation) + 90) < 5)
				bounds = new Rectangle2D.Float((translate[0] - scaleX), translate[1], scaleX, scaleY);
			else if (rightSideLeft)
				bounds = new Rectangle2D.Float((translate[0] - scaleX), translate[1], scaleX, scaleY);
			else if (upsideDown)
				bounds = new Rectangle2D.Float(translate[0], (translate[1] - scaleY), scaleX, scaleY);
			else bounds = new Rectangle2D.Float(translate[0], translate[1], scaleX, scaleY);
			if (DEBUG_RENDER_PAGE_CONTENT && (this.figures != null))
				System.out.println(" ==> image rendering bounds " + bounds);
			
			if (DEBUG_RENDER_PAGE_CONTENT && (this.figures != null))
				System.out.println(" ==> image name " + name);
			if (this.figures != null)
				this.figures.add(new PFigure(name, bounds, rotation, rightSideLeft, upsideDown));
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
				
				if (DEBUG_RENDER_PAGE_CONTENT && (this.words != null))
					System.out.println(" --> show string: '" + rStr + "'" + ((rStr.length() == 1) ? (" (" + ((int) rStr.charAt(0)) + ")") : ""));
			}
			else if (DEBUG_RENDER_PAGE_CONTENT && (this.words != null)) {
				System.out.println(" --> show string (no font): '" + str + "'" + ((str.length() == 1) ? (" (" + ((int) str.charAt(0)) + ")") : ""));
				System.out.println(" --> cs class is " + str.getClass().getName());
			}
		}
		
		private void doTJ(LinkedList stack) {
			StringBuffer totalRendered = new StringBuffer();
			Vector stros = ((Vector) stack.removeLast());
			
			//	print debug info
			if (DEBUG_RENDER_PAGE_CONTENT) {
				System.out.println("TEXT ARRAY: " + stros);
				for (int s = 0; s < stros.size(); s++) {
					Object o = stros.get(s);
					if (o instanceof Number) {
						if (this.words != null)
							System.out.println(" - n " + o);
					}
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
					if (this.pwrFont == null) {
						if (this.words != null)
							totalRendered.append(str);
					}
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
					if (DEBUG_RENDER_PAGE_CONTENT && (this.words != null))
						System.out.println("Re-positioning by TJ array number " + d + "*" + this.pwrFontSize);
					updateMatrix(this.lineMatrix, (-d * this.pwrFontSize), 0);
				}
			}
			
			//	finish line, remembering any last word
			this.endLine();
			
			//	print debug info
			if (DEBUG_RENDER_PAGE_CONTENT && (this.words != null))
				System.out.println(" --> show strings from array" + ((this.pwrFont == null) ? " (nf)" : "") + ": '" + totalRendered + "'");
		}
		
		private StringBuffer wordCharCodes = null;
		private StringBuffer wordString = null;
		private float[] start;
		private float[] top;
		private float[] bottom;
		
		private String renderCharSequence(CharSequence cs) {
			StringBuffer rendered = new StringBuffer();
			
			//	put characters in array to facilitate modification
			char[] cscs = new char[cs.length()];
			for (int c = 0; c < cs.length(); c++)
				cscs[c] = cs.charAt(c);
			
			//	render characters
			for (int c = 0; c < cscs.length; c++) {
				char ch = cscs[c];
				
				//	split up two bytes conflated in one (only if font doesn't know them as one, though)
				if ((255 < ch) && !this.pwrFont.ucMappings.containsKey(new Integer((int) ch))) {
					cscs[c] = ((char) (ch & 255));
					ch = ((char) (ch >> 8)); // move high byte into rendering range
					if (DEBUG_RENDER_PAGE_CONTENT && (this.words != null))
						System.out.println("Handling high byte char '" + ch + "' (" + ((int) ch) + ", " + Integer.toString(((int) ch), 16).toUpperCase() + ") for now, coming back for low byte char '" + cscs[c] + "' (" + ((int) cscs[c]) + ", " + Integer.toString(((int) cscs[c]), 16).toUpperCase() + ") next round");
					c--; // come back for low byte in the next round
				}
				String rCh;
				boolean isSpace;
				boolean implicitSpace;
				
				//	we're only collecting chars
				if (this.words == null) {
					this.pwrFont.setCharUsed((int) ch);
					rCh = ("(" + ((int) ch) + ")");
					isSpace = (("" + ch).trim().length() == 0);
					implicitSpace = (this.pwrFont.hasImplicitSpaces || ((this.pwrCharSpacing > ((this.pwrFontSpaceWidth * narrowSpaceToleranceFactor) / 1000))));				
				}
				
				//	we're actually extracting words
				else {
					rCh = this.pwrFont.getUnicode((int) ch);
					isSpace = (rCh.trim().length() == 0);
					implicitSpace = (this.pwrFont.hasImplicitSpaces || ((this.pwrCharSpacing > ((this.pwrFontSpaceWidth * narrowSpaceToleranceFactor) / 1000))));				
				}
				
				//	whitespace char, remember end of previous word (if any)
				if (isSpace) {
					this.endWord();
					rendered.append(' ');
				}
				
				//	non-whitespace char, remember start of word
				else {
					this.startWord();
					String nrCh = dissolveLigature(rCh);
					if ((this.wordCharCodes != null) && (this.wordString != null)) {
//						this.wordCharCodes.append((char) ((rCh.length() * 256) | ((int) ch)));
//						this.wordString.append(rCh);
						this.wordCharCodes.append((char) ((nrCh.length() * 256) | ((int) ch)));
						this.wordString.append(nrCh);
					}
//					rendered.append(rCh);
					rendered.append(nrCh);
				}
				
				//	update state (move caret forward)
				this.drawChar(ch, isSpace, implicitSpace, rCh);
				
				//	extreme char spacing, add space
				if (implicitSpace) {
					if (!isSpace)
						rendered.append(' ');
					this.endWord();
//					updateMatrix(this.lineMatrix, this.pwrCharSpacing, 0);
					float spaceWidth = (this.pwrCharSpacing + (this.pwrFont.hasImplicitSpaces ? ((this.pwrFont.getCharWidth(ch) - this.pwrFont.getMeasuredCharWidth(ch)) / 1000) : 0));
					if (DEBUG_RENDER_PAGE_CONTENT && (this.words != null))
						System.out.println("Drawing implicit space of width " + spaceWidth);
					updateMatrix(this.lineMatrix, spaceWidth, 0);
				}
			}
			
			//	finally ...
			return rendered.toString();
		}
		
		private void drawChar(char ch, boolean isSpace, boolean implicitSpace, String rCh) {
//			float spacing = (isSpace ? this.pwrWordSpacing : (implicitSpace ? 0 : this.pwrCharSpacing));
			float spacing = (implicitSpace ? 0 : this.pwrCharSpacing);
			if (isSpace)
				spacing += this.pwrWordSpacing;
//			float width = ((((this.pwrFont.getCharWidth(ch) * this.pwrFontSize) + (spacing * 1000)) * this.pwrHorizontalScaling) / (1000 * 100));
			float width = (((((this.pwrFont.hasImplicitSpaces ? this.pwrFont.getMeasuredCharWidth(ch) : this.pwrFont.getCharWidth(ch)) * this.pwrFontSize) + (spacing * 1000)) * this.pwrHorizontalScaling) / (1000 * 100));
			if (DEBUG_RENDER_PAGE_CONTENT && (this.words != null))
				System.out.println("Drawing char '" + ch + "' (" + ((int) ch) + ", " + Integer.toString(((int) ch), 16).toUpperCase() + ") ==> '" + rCh + "' (" + ((int) rCh.charAt(0)) + ", '" + dissolveLigature(rCh) + "')");
			updateMatrix(this.lineMatrix, width, 0);
		}
		
		private void startLine() {
			if ((this.top != null) && (this.bottom != null))
				return;
			if (this.pwrFont == null)
				return;
//			this.top = transform(0, (this.pwrFont.capHeight * this.pwrFontSize), 1, this.lineMatrix);
			this.top = transform(0, (Math.min(this.pwrFont.capHeight, this.pwrFont.ascent) * this.pwrFontSize), 1, this.lineMatrix);
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
			if (this.words == null)
				this.pwrFont.startWord();
			this.wordCharCodes = new StringBuffer();
			this.wordString = new StringBuffer();
			this.start = transform(0, 0, 1, this.lineMatrix);
			this.start = this.applyTransformationMatrices(this.start);
			if (DEBUG_RENDER_PAGE_CONTENT && (this.words != null))
				System.out.println(" start set to " + Arrays.toString(this.start));
		}
		
		private void endWord() {
			if (this.start == null)
				return;
			if (this.words == null)
				this.pwrFont.endWord();
			float[] end = transform(0, 0, 1, this.lineMatrix);
			end = this.applyTransformationMatrices(end);
			if (DEBUG_RENDER_PAGE_CONTENT && (this.words != null)) {
				System.out.println(" - start is " + Arrays.toString(this.start));
				System.out.println(" - end is " + Arrays.toString(end));
				System.out.println(" - top is " + Arrays.toString(this.top));
				System.out.println(" - bottom is " + Arrays.toString(this.bottom));
				if (this.eFontDirection == PWord.LEFT_RIGHT_FONT_DIRECTION)
					System.out.println(" --> extent is " + this.start[0] + "-" + end[0] + " x " + this.top[1] + "-" + this.bottom[1]);
				else if (this.eFontDirection == PWord.BOTTOM_UP_FONT_DIRECTION)
					System.out.println(" --> extent is " + this.top[0] + "-" + this.bottom[0] + " x " + this.start[1] + "-" + end[1]);
				else if (this.eFontDirection == PWord.TOP_DOWN_FONT_DIRECTION)
					System.out.println(" --> extent is " + this.bottom[0] + "-" + this.top[0] + " x " + end[1] + "-" + this.start[1]);
			}
			Rectangle2D pwb = null;
//			float bls = this.pwrFont.getRelativeBaselineShift(this.wordString.toString());
//			if (bls == 0)
//				pwb = new Rectangle2D.Float(this.start[0], this.bottom[1], (end[0] - this.start[0]), (this.top[1] - this.bottom[1]));
//			else {
//				bls *= this.eFontSize;
//				if (DEBUG_RENDER_TEXT) System.out.println(" --> extent corrected to " + this.start[0] + "-" + end[0] + " x " + (top[1] + bls) + "-" + (bottom[1] + bls));
//				pwb = new Rectangle2D.Float(this.start[0], (this.bottom[1] + bls), (end[0] - this.start[0]), (this.top[1] - this.bottom[1]));
//			}
			//	correction seems to be causing a bit of trouble, switched off for now
			if (this.eFontDirection == PWord.LEFT_RIGHT_FONT_DIRECTION)
				pwb = new Rectangle2D.Float(this.start[0], this.bottom[1], (end[0] - this.start[0]), (this.top[1] - this.bottom[1]));
			else if (this.eFontDirection == PWord.BOTTOM_UP_FONT_DIRECTION)
				pwb = new Rectangle2D.Float(this.top[0], this.start[1], (this.bottom[0] - this.top[0]), (end[1] - this.start[1]));
			else if (this.eFontDirection == PWord.TOP_DOWN_FONT_DIRECTION)
				pwb = new Rectangle2D.Float(this.bottom[0], end[1], (this.top[0] - this.bottom[0]), (this.start[1] - end[1]));
			if ((pwb != null) && (this.words != null))
				this.words.add(new PWord(this.wordCharCodes.toString(), this.wordString.toString(), pwb, Math.round(this.eFontSize), this.eFontDirection, this.pwrFont));
			this.wordCharCodes = null;
			this.wordString = null;
			this.start = null;
		}
		
		private static String dissolveLigature(String rCh) {
			if (rCh.length() != 1)
				return rCh;
			String nrCh = StringUtils.getNormalForm(rCh.charAt(0));
			if (nrCh.length() == 1)
				return rCh;
			if (DEBUG_RENDER_PAGE_CONTENT)
				System.out.println("Dissolved '" + rCh + "' to '" + nrCh + "'");
			return nrCh;
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