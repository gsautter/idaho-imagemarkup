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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.CompositeContext;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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
import de.uka.ipd.idaho.gamta.TokenSequenceUtils;
import de.uka.ipd.idaho.gamta.Tokenizer;
import de.uka.ipd.idaho.gamta.util.CountingSet;
import de.uka.ipd.idaho.gamta.util.ProgressMonitor;
import de.uka.ipd.idaho.im.pdf.PdfFontDecoder.FontDecoderCharset;
import de.uka.ipd.idaho.im.pdf.PdfUtils.PdfByteInputStream;
import de.uka.ipd.idaho.im.pdf.PdfUtils.PdfLineInputStream;
import de.uka.ipd.idaho.im.pdf.test.PdfExtractorTest;
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
	 * @param sm a security manager to use on encrypted object streams
	 * @return a Map holding the objects parsed from the PDF file
	 * @throws IOException
	 */
	public static HashMap getObjects(byte[] bytes, SecurityManager sm) throws IOException {
		HashMap objects = new HashMap() {
			public Object put(Object key, Object value) {
				if (PdfExtractorTest.aimAtPage != -1)
					System.out.println("PDF Object map: " + key + " mapped to " + value);
				return super.put(key, value);
			}
		};
		
		PdfLineInputStream lis = new PdfLineInputStream(new ByteArrayInputStream(bytes));
		for (byte[] line; (line = lis.readLine()) != null;) {
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
					Object type = ((PStream) obj).params.get("Type");
					if ((type != null) && "ObjStm".equals(type.toString())) {
						Reference sObjRef = null;
						if (sm != null) {
							String[] objIdNrs = objId.split("[^0-9]+");
							sObjRef = new Reference(Integer.parseInt(objIdNrs[0]), Integer.parseInt(objIdNrs[1]));
						}
						decodeObjectStream(sObjRef, ((PStream) obj), objects, false, sm);
					}
				}
			}
		}
		lis.close();
		
		dereferenceObjects(objects);
		return objects;
	}
	
	static void decodeObjectStream(Reference objStreamRef, PStream objStream, Map objects, boolean forInfo, SecurityManager sm) throws IOException {
		if ((objStreamRef != null) && (sm != null)) {
			decryptObject(objStreamRef, objStream.params, sm);
			decryptBytes(objStreamRef, objStream.bytes, sm);
		}
		
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
		
		//	make sure we got all the input we need, pad or cut as necessary
		Object length = getObject(params, "Length", objects);
		if (length instanceof Number) {
			if (stream.length < ((Number) length).intValue()) {
				byte[] padStream = new byte[((Number) length).intValue()];
				System.arraycopy(stream, 0, padStream, 0, stream.length);
				for (int b = stream.length; b < padStream.length; b++)
					padStream[b] = '\n';
				stream = padStream;
			}
			else if (stream.length > ((Number) length).intValue()) {
				byte[] cropStream = new byte[((Number) length).intValue()];
				System.arraycopy(stream, 0, cropStream, 0, cropStream.length);
				stream = cropStream;
			}
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
//		System.out.println("Decoding " + filter + " stream of " + stream.length + " bytes, params are " + params);
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
		LZWDecode lzwd = new LZWDecode(new BitStream(new ByteArrayInputStream(stream)), new Library(), params);
		byte[] buffer = new byte[1024];
		int read;
		while ((read = lzwd.read(buffer)) != -1)
			baos.write(buffer, 0, read);
		lzwd.close();
	}
	
	private static void decodeFlate(byte[] stream, Hashtable params, ByteArrayOutputStream baos) throws IOException {
		FlateDecode fd = new FlateDecode(new Library(), params, new ByteArrayInputStream(stream));
		byte[] buffer = new byte[1024];
		int read;
		while ((read = fd.read(buffer)) != -1)
			baos.write(buffer, 0, read);
		fd.close();
	}
		
	private static void decodeAscii85(byte[] stream, Hashtable params, ByteArrayOutputStream baos) throws IOException {
		ASCII85Decode ad = new ASCII85Decode(new ByteArrayInputStream(stream));
		byte[] buffer = new byte[1024];
		int read;
		while ((read = ad.read(buffer)) != -1)
			baos.write(buffer, 0, read);
		ad.close();
	}
	
	private static void decodeAsciiHex(byte[] stream, Hashtable params, ByteArrayOutputStream baos) throws IOException {
		ASCIIHexDecode ahd = new ASCIIHexDecode(new ByteArrayInputStream(stream));
		byte[] buffer = new byte[1024];
		int read;
		while ((read = ahd.read(buffer)) != -1)
			baos.write(buffer, 0, read);
		ahd.close();
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
		rld.close();
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
	
	static void dereferenceObjects(Map objects) {
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
	
	static void dereferenceObjects(Hashtable dict, Map objects) {
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
	
	static void dereferenceObjects(Vector array, Map objects) {
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
			Object type = ((PStream) obj).params.get("Type"); // object streams are decrypted on reading, so we can omit them here
			if ((type == null) || !"ObjStm".equals(type.toString())) {
				decryptObject(objRef, ((PStream) obj).params, sm);
				decryptBytes(objRef, ((PStream) obj).bytes, sm);
			}
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
		if ((marker == null) || !PdfUtils.equals(marker, "stream")) {
			lis.close();
			return obj;
		}
		
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		for (byte[] streamLine, lastStreamLine = null; (streamLine = lis.readLine()) != null;) {
			if (PdfUtils.equals(streamLine, "endstream")) {
				if (lastStreamLine != null)
					baos.write(lastStreamLine, 0, lastStreamLine.length);
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
		
		lis.close();
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
			if (PdfUtils.matches(lookahead, "[1-9][0-9]*\\s+[0-9]+\\s*R.*")) {
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
		 * the raw binary data
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
			if (PdfExtractorTest.aimAtPage != -1)
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
	 * A rendered object in a PDF page, keeping track of when it was rendered.
	 * 
	 * @author sautter
	 */
	public static abstract class PObject implements Comparable {
		final int renderOrderNumber;
		PObject(int renderOrderNumber) {
			this.renderOrderNumber = renderOrderNumber;
		}
		public int compareTo(Object obj) {
			if (obj instanceof PObject)
				return (this.renderOrderNumber - ((PObject) obj).renderOrderNumber);
			else return -1;
		}
	}
	
	/**
	 * Wrapper for a word (not necessarily a complete word in a linguistic
	 * sense, but a whitespace-free string) extracted from the content of a PDF
	 * page, together with its properties.
	 * 
	 * @author sautter
	 */
	public static class PWord extends PObject {
		public static final int LEFT_RIGHT_FONT_DIRECTION = 0;
		public static final int BOTTOM_UP_FONT_DIRECTION = 1;
		public static final int TOP_DOWN_FONT_DIRECTION = -1;
		public final String charCodes;
		public final String str;
		public final Rectangle2D.Float bounds;
		public final Color color;
		public final boolean bold;
		public final boolean italics;
		public final boolean serif;
		public final int fontSize;
		public final int fontDirection;
		public final PdfFont font;
		boolean joinWithNext = false;
		PWord(int renderOrderNumber, String charCodes, Rectangle2D.Float bounds, Color color, int fontSize, int fontDirection, PdfFont font) {
			this(renderOrderNumber, charCodes, null, bounds, color, fontSize, fontDirection, font);
		}
		PWord(int renderOrderNumber, String charCodes, String str, Rectangle2D.Float bounds, Color color, int fontSize, int fontDirection, PdfFont font) {
			super(renderOrderNumber);
			this.charCodes = charCodes;
			this.str = str;
			this.bounds = bounds;
			this.color = color;
			this.bold = font.bold;
			this.italics = font.italics;
			this.serif = font.serif;
			this.fontSize = fontSize;
			this.fontDirection = fontDirection;
			this.font = font;
		}
		public String toString() {
			return (this.str + " [" + Math.round(this.bounds.getMinX()) + "," + Math.round(this.bounds.getMaxX()) + "," + Math.round(this.bounds.getMinY()) + "," + Math.round(this.bounds.getMaxY()) + "], sized " + this.fontSize + (this.bold ? ", bold" : "") + (this.italics ? ", italic" : "") + ((this.color == null) ? "" : (", in " + Integer.toString(((this.color.getRGB() >>> 24) & 0xFF), 16).toUpperCase() + "-" + Integer.toString((this.color.getRGB() & 0xFFFFFF), 16).toUpperCase())) + ", at RON " + this.renderOrderNumber);
		}
	}
	
	/**
	 * Wrapper for a figure (image embedded in a page) and its properties.
	 * 
	 * @author sautter
	 */
	public static class PFigure extends PObject {
		public final String name;
		public final Rectangle2D.Float bounds;
		public final double rotation;
		public final boolean rightSideLeft;
		public final boolean upsideDown;
		public final PFigure[] subFigures;
		public final Object refOrData;
		PFigure(int renderOrderNumber, String name, Rectangle2D.Float bounds, Object refOrData, double rotation, boolean rightSideLeft, boolean upsideDown) {
			super(renderOrderNumber);
			this.name = name;
			this.bounds = bounds;
			this.refOrData = refOrData;
			this.rotation = rotation;
			this.rightSideLeft = rightSideLeft;
			this.upsideDown = upsideDown;
			this.subFigures = null;
		}
		PFigure(PFigure pf1, PFigure pf2) {
			super(Math.min(pf1.renderOrderNumber, pf2.renderOrderNumber));
			ArrayList subFigureList = new ArrayList();
			addFigure(subFigureList, pf1);
			addFigure(subFigureList, pf2);
			this.subFigures = ((PFigure[]) subFigureList.toArray(new PFigure[subFigureList.size()]));
			
			StringBuffer name = new StringBuffer("[");
			float lx = Float.MAX_VALUE;
			float rx = -Float.MAX_VALUE;
			float by = Float.MAX_VALUE;
			float ty = -Float.MAX_VALUE;
			for (int f = 0; f < this.subFigures.length; f++) {
				if (f != 0)
					name.append(" + ");
				name.append(this.subFigures[f].name);
				lx = Math.min(lx, ((float) this.subFigures[f].bounds.getMinX()));
				rx = Math.max(rx, ((float) this.subFigures[f].bounds.getMaxX()));
				by = Math.min(by, ((float) this.subFigures[f].bounds.getMinY()));
				ty = Math.max(ty, ((float) this.subFigures[f].bounds.getMaxY()));
			}
			this.name = name.append("]").toString();
			this.bounds = new Rectangle2D.Float(lx, by, (rx - lx), (ty - by));
			this.refOrData = null;
			this.rotation = 0;
			this.rightSideLeft = false;
			this.upsideDown = false;
		}
		private static void addFigure(ArrayList sfs, PFigure f) {
			if (f.subFigures == null)
				sfs.add(f);
			else for (int s = 0; s < f.subFigures.length; s++)
				addFigure(sfs, f.subFigures[s]);
		}
		public String toString() {
			return (this.name + " [" + Math.round(this.bounds.getMinX()) + "," + Math.round(this.bounds.getMaxX()) + "," + Math.round(this.bounds.getMinY()) + "," + Math.round(this.bounds.getMaxY()) + "] at RON " + this.renderOrderNumber);
		}
	}
	
	/**
	 * Wrapper for a path (line and curve sequence) and its drawing properties.
	 * 
	 * @author sautter
	 */
	public static class PPath extends PObject {
		private float x;
		private float y;
		
		private float minX = Float.MAX_VALUE;
		private float minY = Float.MAX_VALUE;
		private float maxX = -Float.MAX_VALUE;
		private float maxY = -Float.MAX_VALUE;
		
		private PSubPath currentSubPath = null;
		private ArrayList subPaths = new ArrayList();
		
		BlendComposite blendMode;
		
		PPath(int renderOrderNumber) {
			super(renderOrderNumber);
		}
		
		private void dom(LinkedList stack, LinkedList transformationMatrices) {
			float y = ((Number) stack.removeLast()).floatValue();
			float x = ((Number) stack.removeLast()).floatValue();
			float[] p = {x, y, 1};
			p = applyTransformationMatrices(p, transformationMatrices);
			this.currentSubPath = new PSubPath(this, p[0], p[1]);
		}
		void doh(LinkedList stack) {
			if (this.currentSubPath != null)
				this.currentSubPath.doh(stack);
			this.currentSubPath = null;
		}
		
		void dol(LinkedList stack, LinkedList transformationMatrices) {
			if (this.currentSubPath == null)
				this.currentSubPath = new PSubPath(this, this.x, this.y);
			this.currentSubPath.dol(stack, transformationMatrices);
		}
		
		void doc(LinkedList stack, LinkedList transformationMatrices) {
			if (this.currentSubPath == null)
				this.currentSubPath = new PSubPath(this, this.x, this.y);
			this.currentSubPath.doc(stack, transformationMatrices);
		}
		void dov(LinkedList stack, LinkedList transformationMatrices) {
			if (this.currentSubPath == null)
				this.currentSubPath = new PSubPath(this, this.x, this.y);
			this.currentSubPath.dov(stack, transformationMatrices);
		}
		void doy(LinkedList stack, LinkedList transformationMatrices) {
			if (this.currentSubPath == null)
				this.currentSubPath = new PSubPath(this, this.x, this.y);
			this.currentSubPath.doy(stack, transformationMatrices);
		}
		void dore(LinkedList stack, LinkedList transformationMatrices) {
			float height = ((Number) stack.removeLast()).floatValue();
			float width = ((Number) stack.removeLast()).floatValue();
			float y = ((Number) stack.removeLast()).floatValue();
			float x = ((Number) stack.removeLast()).floatValue();
			
			stack.addLast(new Float(x));
			stack.addLast(new Float(y));
			this.dom(stack, transformationMatrices);
			
			stack.addLast(new Float(x + width));
			stack.addLast(new Float(y));
			this.dol(stack, transformationMatrices);
			
			stack.addLast(new Float(x + width));
			stack.addLast(new Float(y + height));
			this.dol(stack, transformationMatrices);
			
			stack.addLast(new Float(x));
			stack.addLast(new Float(y + height));
			this.dol(stack, transformationMatrices);
			
			this.doh(stack);
		}
		
		static int strokeCount = 0;
		static CountingSet strokeColors = new CountingSet(new TreeMap());
		Color strokeColor = null;
		float lineWidth = Float.NaN;
		byte lineCapStyle = ((byte) -1);
		byte lineJointStyle = ((byte) -1);
		float miterLimit = Float.NaN;
		Vector dashPattern = null;
		float dashPatternPhase = Float.NaN;
		void stroke(Color color, float lineWidth, byte lineCapStyle, byte lineJointStyle, float miterLimit, Vector dashPattern, float dashPatternPhase, LinkedList transformationMatrices) {
			strokeCount++;
			if (color != null)
				strokeColors.add(Integer.toString((color.getRGB() & 0xFFFFFF), 16).toUpperCase());
			if (color == null)
				throw new RuntimeException("Cannot stroke without color");
			this.strokeColor = color;
			this.lineWidth = zoom(lineWidth, transformationMatrices);
			this.lineCapStyle = lineCapStyle;
			this.lineJointStyle = lineJointStyle;
			this.miterLimit = zoom(miterLimit, transformationMatrices);
			if (dashPattern != null) {
				this.dashPattern = new Vector();
				for (int e = 0; e < dashPattern.size(); e++)
					this.dashPattern.add(new Float(zoom(((Number) dashPattern.get(e)).floatValue(), transformationMatrices)));
			}
			this.dashPatternPhase = zoom(dashPatternPhase, transformationMatrices);
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println("PPath: stroked with color " + this.strokeColor + ((this.strokeColor == null) ? "" : (", alpha " + this.strokeColor.getAlpha())));
		}
		private static float zoom(float f, LinkedList transformationMatrices) {
			float[] zoomer = {f, f, 0};
			zoomer = applyTransformationMatrices(zoomer, transformationMatrices);
			return ((zoomer[0] + zoomer[1]) / 2);
		}
		
		static int fillCount = 0;
		static CountingSet fillColors = new CountingSet(new TreeMap());
		Color fillColor = null;
		void fill(Color color) {
			fillCount++;
			if (color != null)
				fillColors.add(Integer.toString((color.getRGB() & 0xFFFFFF), 16).toUpperCase());
			if (color == null)
				throw new RuntimeException("Cannot fill without color");
			this.fillColor = color;
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println("PPath: filled with color " + this.fillColor + ((this.fillColor == null) ? "" : (", alpha " + this.fillColor.getAlpha())));
		}
		
		void fillAndStroke(Color fillColor, Color strokeColor, float lineWidth, byte lineCapStyle, byte lineJointStyle, float miterLimit, Vector dashPattern, float dashPatternPhase, LinkedList transformationMatrices) {
			this.fill(fillColor);
			this.stroke(strokeColor, lineWidth, lineCapStyle, lineJointStyle, miterLimit, dashPattern, dashPatternPhase, transformationMatrices);
		}
		
		void addPoint(float x, float y) {
			this.x = x;
			this.y = y;
			this.minX = Math.min(this.minX, this.x);
			this.minY = Math.min(this.minY, this.y);
			this.maxX = Math.max(this.maxX, this.x);
			this.maxY = Math.max(this.maxY, this.y);
		}
		
		void addSubPath(PSubPath subPath) {
			this.subPaths.add(subPath);
		}
		
		PSubPath[] getSubPaths() {
			return ((PSubPath[]) this.subPaths.toArray(new PSubPath[this.subPaths.size()]));
		}
		
		private Rectangle2D bounds = null;
		Rectangle2D getBounds() {
			if (this.bounds == null)
				this.bounds = new Rectangle2D.Float(this.minX, this.minY, (this.maxX - this.minX), (this.maxY - this.minY));
			return this.bounds;
		}
		
		public void paint(Graphics2D gr) {
			Color preColor = gr.getColor();
			Stroke preStroke = gr.getStroke();
			for (int p = 0; p < this.subPaths.size(); p++) {
				Path2D sp = ((PSubPath) this.subPaths.get(p)).getPath();
				if (this.fillColor != null) {
					gr.setColor(this.fillColor);
					gr.fill(sp);
				}
				if (this.strokeColor != null) {
					gr.setColor(this.strokeColor);
					float[] dashPattern = new float[this.dashPattern.size()];
					boolean allZeroDashes = true;
					for (int e = 0; e < this.dashPattern.size(); e++) {
						dashPattern[e] = ((Number) this.dashPattern.get(e)).floatValue();
						allZeroDashes = (allZeroDashes && (dashPattern[e] == 0));
					}
					if (allZeroDashes)
						dashPattern = null;
					gr.setStroke(new BasicStroke(this.lineWidth, this.lineCapStyle, this.lineJointStyle, ((this.miterLimit < 1) ? 1.0f : this.miterLimit), dashPattern, this.dashPatternPhase));
					gr.draw(sp);
				}
				else if (this.fillColor != null) {
					gr.setColor(this.fillColor);
					gr.draw(sp);
				}
			}
			gr.setColor(preColor);
			gr.setStroke(preStroke);
		}
		
		PPath translate(float x, float y) {
			PPath tPath = new PPath(this.renderOrderNumber);
			tPath.minX = (this.minX + x);
			tPath.minY = (this.minY + y);
			tPath.maxX = (this.maxX + x);
			tPath.maxY = (this.maxY + y);
			
			tPath.strokeColor = this.strokeColor;
			tPath.lineWidth = this.lineWidth;
			tPath.lineCapStyle = this.lineCapStyle;
			tPath.lineJointStyle = this.lineJointStyle;
			tPath.miterLimit = this.miterLimit;
			tPath.dashPattern = this.dashPattern;
			tPath.dashPatternPhase = this.dashPatternPhase;
			tPath.fillColor = this.fillColor;
			
			for (int p = 0; p < this.subPaths.size(); p++)
				tPath.subPaths.add(((PSubPath) this.subPaths.get(p)).translate(tPath, x, y));
			
			return tPath;
		}
		
		public String toString() {
			Rectangle2D bounds = this.getBounds();
			return ("path [" + Math.round(bounds.getMinX()) + "," + Math.round(bounds.getMaxX()) + "," + Math.round(bounds.getMinY()) + "," + Math.round(bounds.getMaxY()) + "] at RON " + this.renderOrderNumber);
		}
	}
	
	/**
	 * Wrapper for a single sub path (line and curve sequence).
	 * 
	 * @author sautter
	 */
	public static class PSubPath {
		//	TODO_not derive this class from Path2D.Float for easier handling ==> too much overhead, getPath() works better
		
		final PPath parent;
		final float startX;
		final float startY;
		float x;
		float y;
		
		private float minX = Float.MAX_VALUE;
		private float minY = Float.MAX_VALUE;
		private float maxX = -Float.MAX_VALUE;
		private float maxY = -Float.MAX_VALUE;
		
		private ArrayList shapes = new ArrayList();
		
		PSubPath(PPath parent, float startX, float startY) {
			this.parent = parent;
			this.startX = startX;
			this.startY = startY;
			
			this.x = this.startX;
			this.y = this.startY;
			
			//	remember visiting current point
			this.addPoint(this.x, this.y);
		}
		
		boolean hasContent() {
			return (this.shapes.size() != 0);
		}
		
		void dol(LinkedList stack, LinkedList transformationMatrices) {
			float y = ((Number) stack.removeLast()).floatValue();
			float x = ((Number) stack.removeLast()).floatValue();
			float[] p = {x, y, 1};
			p = applyTransformationMatrices(p, transformationMatrices);
			this.lineTo(p[0], p[1]);
		}
		void doh(LinkedList stack) {
			this.lineTo(this.startX, this.startY);
		}
		private void lineTo(float x, float y) {
			
			//	store shape for later use
			this.shapes.add(new Line2D.Float(this.x, this.y, x, y));
			if (this.shapes.size() == 1)
				this.parent.addSubPath(this);
			this.x = x;
			this.y = y;
			
			//	remember visiting current point
			this.addPoint(this.x, this.y);
		}
		
		void doc(LinkedList stack, LinkedList transformationMatrices) {
			float y3 = ((Number) stack.removeLast()).floatValue();
			float x3 = ((Number) stack.removeLast()).floatValue();
			float y2 = ((Number) stack.removeLast()).floatValue();
			float x2 = ((Number) stack.removeLast()).floatValue();
			float y1 = ((Number) stack.removeLast()).floatValue();
			float x1 = ((Number) stack.removeLast()).floatValue();
			float[] p = {x3, y3, 1};
			p = applyTransformationMatrices(p, transformationMatrices);
			float[] cp1 = {x1, y1, 1};
			cp1 = applyTransformationMatrices(cp1, transformationMatrices);
			float[] cp2 = {x2, y2, 1};
			cp2 = applyTransformationMatrices(cp2, transformationMatrices);
			this.curveTo(cp1[0], cp1[1], cp2[0], cp2[1], p[0], p[1]);
		}
		void dov(LinkedList stack, LinkedList transformationMatrices) {
			float y3 = ((Number) stack.removeLast()).floatValue();
			float x3 = ((Number) stack.removeLast()).floatValue();
			float y2 = ((Number) stack.removeLast()).floatValue();
			float x2 = ((Number) stack.removeLast()).floatValue();
			float[] p = {x3, y3, 1};
			p = applyTransformationMatrices(p, transformationMatrices);
			float[] cp2 = {x2, y2, 1};
			cp2 = applyTransformationMatrices(cp2, transformationMatrices);
			this.curveTo(this.x, this.y, cp2[0], cp2[1], p[0], p[1]);
		}
		void doy(LinkedList stack, LinkedList transformationMatrices) {
			float y3 = ((Number) stack.removeLast()).floatValue();
			float x3 = ((Number) stack.removeLast()).floatValue();
			float y1 = ((Number) stack.removeLast()).floatValue();
			float x1 = ((Number) stack.removeLast()).floatValue();
			float[] p = {x3, y3, 1};
			p = applyTransformationMatrices(p, transformationMatrices);
			float[] cp1 = {x1, y1, 1};
			cp1 = applyTransformationMatrices(cp1, transformationMatrices);
			this.curveTo(cp1[0], cp1[1], p[0], p[1], p[0], p[1]);
		}
		private void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
			
			//	store shape for later use
			this.shapes.add(new CubicCurve2D.Float(this.x, this.y, x1, y1, x2, y2, x3, y3));
			if (this.shapes.size() == 1)
				this.parent.addSubPath(this);
			this.x = x3;
			this.y = y3;
			
			//	remember extent and visiting current
			this.addPoint(x1, y1);
			this.addPoint(x2, y2);
			this.addPoint(this.x, this.y);
		}
		
		Shape[] getShapes() {
			return ((Shape[]) this.shapes.toArray(new Shape[this.shapes.size()]));
		}
		
		Path2D getPath() {
			Path2D path = new Path2D.Float();
			for (int s = 0; s < this.shapes.size(); s++)
				path.append(((Shape) this.shapes.get(s)), true);
			return path;
		}
		
		private void addPoint(float x, float y) {
			this.parent.addPoint(this.x, this.y);
			this.minX = Math.min(this.minX, this.x);
			this.minY = Math.min(this.minY, this.y);
			this.maxX = Math.max(this.maxX, this.x);
			this.maxY = Math.max(this.maxY, this.y);
		}
		
		private Rectangle2D bounds = null;
		Rectangle2D getBounds() {
			if (this.bounds == null)
				this.bounds = new Rectangle2D.Float(this.minX, this.minY, (this.maxX - this.minX), (this.maxY - this.minY));
			return this.bounds;
		}
		
		PSubPath translate(PPath tParent, float x, float y) {
			PSubPath tSubPath = new PSubPath(tParent, (this.startX + x), (this.startY + y));
			for (int s = 0; s < this.shapes.size(); s++) {
				Shape shape = ((Shape) this.shapes.get(s));
				if (shape instanceof Line2D) {
					Line2D line = ((Line2D) shape);
					tSubPath.shapes.add(new Line2D.Float((((float) line.getX1()) + x), (((float) line.getY1()) + y), (((float) line.getX2()) + x), (((float) line.getY2()) + y)));
				}
				else if (shape instanceof CubicCurve2D) {
					CubicCurve2D curve = ((CubicCurve2D) shape);
					tSubPath.shapes.add(new CubicCurve2D.Float((((float) curve.getX1()) + x), (((float) curve.getY1()) + y), (((float) curve.getCtrlX1()) + x), (((float) curve.getCtrlY1()) + y), (((float) curve.getCtrlX2()) + x), (((float) curve.getCtrlY2()) + y), (((float) curve.getX2()) + x), (((float) curve.getY2()) + y)));
				}
			}
			return tSubPath;
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
		public final PPath[] paths;
		PPageContent(PWord[] words, PFigure[] figures, PPath[] paths) {
			this.words = words;
			this.figures = figures;
			this.paths = paths;
		}
	}
//	
//	/** the font mode requesting full decoding of embedded fonts */
//	public static final int FONT_MODE_DECODE = 2;
//	
//	/** the font mode requesting rendering characters from embedded fonts (to enable later correction), but relying on any Unicode mapping for decoding */
//	public static final int FONT_MODE_RENDER = 1;
//	
//	/** the font mode requesting no decoding of embedded fonts, entirely relying on any Unicode mapping */
//	public static final int FONT_MODE_QUICK = 0;
	
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
		runPageContent(entries, content, resources, objects, null, null, PdfFontDecoder.NO_DECODING, null, null, pm);
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
		return getPageWords(entries, content, resources, objects, tokenizer, PdfFontDecoder.UNICODE, null, null, pm);
	}
	
	/**
	 * Parse a content stream of a page and extract the words.
	 * @param entries the page entries
	 * @param content the bytes to parse
	 * @param resources the page resource map
	 * @param objects a map holding objects that might be required, such as
	 *            fonts, etc.
	 * @param tokenizer get tokenizer to check and split words with
	 * @param fontCharSet the character set to use for font decoding
	 * @param pm a progress monitor to receive updates on the word extraction
	 *            process
	 * @return the words extracted from the page
	 * @throws IOException
	 */
	public static PWord[] getPageWords(Hashtable entries, byte[] content, Hashtable resources, Map objects, Tokenizer tokenizer, FontDecoderCharset fontCharSet, ProgressMonitor pm) throws IOException {
		return getPageWords(entries, content, resources, objects, tokenizer, fontCharSet, null, null, pm);
	}
	
	/**
	 * Parse a content stream of a page and extract the words, collecting
	 * figures and paths along the way.
	 * @param entries the page entries
	 * @param content the bytes to parse
	 * @param resources the page resource map
	 * @param objects a map holding objects that might be required, such as
	 *            fonts, etc.
	 * @param tokenizer get tokenizer to check and split words with
	 * @param figures a list to collect figures in during word extraction
	 * @param paths a list to collect paths (PDF vector graphics) in during
	 *            word extraction
	 * @param pm a progress monitor to receive updates on the word extraction
	 *            process
	 * @return the words extracted from the page
	 * @throws IOException
	 */
	public static PWord[] getPageWords(Hashtable entries, byte[] content, Hashtable resources, Map objects, Tokenizer tokenizer, List figures, List paths, ProgressMonitor pm) throws IOException {
		return getPageWords(entries, content, resources, objects, tokenizer, PdfFontDecoder.UNICODE, figures, paths, pm);
	}
	
	/**
	 * Parse a content stream of a page and extract the words, collecting
	 * figures and paths along the way.
	 * @param entries the page entries
	 * @param content the bytes to parse
	 * @param resources the page resource map
	 * @param objects a map holding objects that might be required, such as
	 *            fonts, etc.
	 * @param tokenizer get tokenizer to check and split words with
	 * @param fontCharSet the character set to use for font decoding
	 * @param figures a list to collect figures in during word extraction
	 * @param paths a list to collect paths (PDF vector graphics) in during
	 *            word extraction
	 * @param pm a progress monitor to receive updates on the word extraction
	 *            process
	 * @return the words extracted from the page
	 * @throws IOException
	 */
	public static PWord[] getPageWords(Hashtable entries, byte[] content, Hashtable resources, Map objects, Tokenizer tokenizer, FontDecoderCharset fontCharSet, List figures, List paths, ProgressMonitor pm) throws IOException {
		ArrayList words = new ArrayList();
		runPageContent(entries, content, resources, objects, words, tokenizer, fontCharSet, figures, paths, pm);
		return ((PWord[]) words.toArray(new PWord[words.size()]));
	}
	
	/**
	 * Parse a content stream of a page and extract the figures.
	 * @param entries the page entries
	 * @param content the bytes to parse
	 * @param resources the page resource map
	 * @param objects a map holding objects that might be required, such as
	 *            fonts, etc.
	 * @param pm a progress monitor to receive updates on the word extraction
	 *            process
	 * @return the figures extracted from the page
	 * @throws IOException
	 */
	public static PFigure[] getPageFigures(Hashtable entries, byte[] content, Hashtable resources, Map objects, ProgressMonitor pm) throws IOException {
		ArrayList figures = new ArrayList();
		runPageContent(entries, content, resources, objects, null, null, figures, null, pm);
		return ((PFigure[]) figures.toArray(new PFigure[figures.size()]));
	}
	
	/**
	 * Parse a content stream of a page and extract any vector based graphics.
	 * @param entries the page entries
	 * @param content the bytes to parse
	 * @param resources the page resource map
	 * @param objects a map holding objects that might be required, such as
	 *            fonts, etc.
	 * @param pm a progress monitor to receive updates on the word extraction
	 *            process
	 * @return the vector graphics paths extracted from the page
	 * @throws IOException
	 */
	public static PPath[] getPagePaths(Hashtable entries, byte[] content, Hashtable resources, Map objects, ProgressMonitor pm) throws IOException {
		ArrayList paths = new ArrayList();
		runPageContent(entries, content, resources, objects, null, null, null, paths, pm);
		return ((PPath[]) paths.toArray(new PPath[paths.size()]));
	}
	
	/**
	 * Parse a content stream of a page and extract any bitmap figures and
	 * vector based graphics. The words array of the returned page content
	 * object is empty.
	 * @param entries the page entries
	 * @param content the bytes to parse
	 * @param resources the page resource map
	 * @param objects a map holding objects that might be required, such as
	 *            fonts, etc.
	 * @param pm a progress monitor to receive updates on the word extraction
	 *            process
	 * @return the bitmap figures and vector graphics paths extracted from the
	 *            page
	 * @throws IOException
	 */
	public static PPageContent getPageGraphics(Hashtable entries, byte[] content, Hashtable resources, Map objects, ProgressMonitor pm) throws IOException {
		ArrayList figures = new ArrayList();
		ArrayList paths = new ArrayList();
		runPageContent(entries, content, resources, objects, null, null, figures, paths, pm);
		return new PPageContent(new PWord[0], ((PFigure[]) figures.toArray(new PFigure[figures.size()])), ((PPath[]) paths.toArray(new PPath[paths.size()])));
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
	 * @return the words, figures, and vector graphics paths extracted from the
	 *            page
	 * @throws IOException
	 */
	public static PPageContent getPageContent(Hashtable entries, byte[] content, Hashtable resources, Map objects, Tokenizer tokenizer, ProgressMonitor pm) throws IOException {
		return getPageContent(entries, content, resources, objects, tokenizer, PdfFontDecoder.UNICODE, pm);
	}
	
	/**
	 * Parse a content stream of a page and extract the words and figures.
	 * @param entries the page entries
	 * @param content the bytes to parse
	 * @param resources the page resource map
	 * @param objects a map holding objects that might be required, such as
	 *            fonts, etc.
	 * @param tokenizer get tokenizer to check and split words with
	 * @param fontCharSet the character set to use for font decoding
	 * @param pm a progress monitor to receive updates on the word extraction
	 *            process
	 * @return the words, figures, and vector graphics paths extracted from the
	 *            page
	 * @throws IOException
	 */
	public static PPageContent getPageContent(Hashtable entries, byte[] content, Hashtable resources, Map objects, Tokenizer tokenizer, FontDecoderCharset fontCharSet, ProgressMonitor pm) throws IOException {
		ArrayList words = new ArrayList();
		ArrayList figures = new ArrayList();
		ArrayList paths = new ArrayList();
		runPageContent(entries, content, resources, objects, words, tokenizer, fontCharSet, figures, paths, pm);
		return new PPageContent(((PWord[]) words.toArray(new PWord[words.size()])), ((PFigure[]) figures.toArray(new PFigure[figures.size()])), ((PPath[]) paths.toArray(new PPath[paths.size()])));
	}
	
	private static Hashtable getPageFonts(Hashtable resources, Map objects, FontDecoderCharset charSet, ProgressMonitor pm) throws IOException {
		Hashtable fonts = new Hashtable();
		
		//	get font dictionary
		final Object fontsObj = dereference(resources.get("Font"), objects);
		if (PdfFont.DEBUG_LOAD_FONTS) System.out.println(" --> font object is " + fontsObj);
		
		//	anything to work with?
		if ((fontsObj == null) || !(fontsObj instanceof Hashtable))
			return fonts;
		
		//	get fonts
		ArrayList toDecodeFonts = new ArrayList();
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
				if (charSet != PdfFontDecoder.NO_DECODING)
					toDecodeFonts.add(fontObj);
				if (PdfFont.DEBUG_LOAD_FONTS && (fontRef != null)) System.out.println("Font cache hit for " + ((Reference) fontRef).getObjectNumber() + " " + ((Reference) fontRef).getGenerationNumber());
			}
			
			//	we need to load this one
			else if (fontObj instanceof Hashtable) {
				if (PdfFont.DEBUG_LOAD_FONTS) System.out.println("Loading font from " + fontObj);
				PdfFont pFont = PdfFont.readFont(fontKey, ((Hashtable) fontObj), objects, true, charSet, pm);
				if (pFont != null) {
					if (PdfFont.DEBUG_LOAD_FONTS) System.out.println("Font loaded: " + pFont.name + " (" + pFont + ")");
					fonts.put(fontKey, pFont);
					if (fontRef != null) {
						objects.put((((Reference) fontRef).getObjectNumber() + " " + ((Reference) fontRef).getGenerationNumber()), pFont);
						if (PdfFont.DEBUG_LOAD_FONTS) System.out.println("Font cached as " + ((Reference) fontRef).getObjectNumber() + " " + ((Reference) fontRef).getGenerationNumber());
					}
					if (charSet != PdfFontDecoder.NO_DECODING)
						toDecodeFonts.add(pFont);
				}
			}
			
			//	this one's out of specification
			else if (PdfFont.DEBUG_LOAD_FONTS) System.out.println("Strange font: " + fontObj);
		}
		
		/* order fonts by descending char usage count, so to make sure to decode
		 * larger fonts first, so we have neighbors for more exotic chars stored
		 * in smaller fonts. */
		Collections.sort(toDecodeFonts, new Comparator() {
			public int compare(Object obj1, Object obj2) {
				PdfFont pf1 = ((PdfFont) obj1);
				PdfFont pf2 = ((PdfFont) obj2);
				return (pf2.getCharUsageCount() - pf1.getCharUsageCount());
			}
		});
		
		//	decode chars only now
		for (int f = 0; f < toDecodeFonts.size(); f++) {
			PdfFont pFont = ((PdfFont) toDecodeFonts.get(f));
			if (PdfFont.DEBUG_LOAD_FONTS) System.out.println("Decoding chars in font " + pFont.name + " (" + pFont + ")");
			pFont.decodeChars(pm, charSet);
		}
		
		//	finally ...
		return fonts;
	}
	
	//	factor to apply to a font's normal space width to capture narrow spaces when outputting strings from an array
//	private static final float defaultNarrowSpaceToleranceFactor = 0.67f;
//	private static final float defaultNarrowSpaceToleranceFactor = 0.33f;
	private static final float defaultNarrowSpaceToleranceFactor = 0.25f;
	private static final float absMinNarrowSpaceWidth = 83.33f; // in font space units, one third of (very narrow) space width in default built-in fonts
	private static final float minFontSpaceWidth = 250.0f; // in font space units, (very narrow) space width in default built-in fonts
	
	/**
	 * Parse a content stream of a text page and extract the text word by word.
	 * @param entries the page entries
	 * @param content the bytes to parse
	 * @param resources the page resource map
	 * @param objects a map holding objects that might be required, such as
	 *            fonts, etc.
	 * @param tokenizer the tokenizer to check and split words with
	 * @param pm a progress monitor to receive updates on the word extraction
	 *            process
	 * @return the words extracted from the page
	 * @throws IOException
	 */
	public static void runPageContent(Hashtable entries, byte[] content, Hashtable resources, Map objects, List words, Tokenizer tokenizer, List figures, List paths, ProgressMonitor pm) throws IOException {
		runPageContent(0, entries, content, resources, objects, words, tokenizer, PdfFontDecoder.UNICODE, figures, paths, ((words == null) && (figures == null) && (paths == null)), pm);
	}
	
	/**
	 * Parse a content stream of a text page and extract the text word by word.
	 * @param entries the page entries
	 * @param content the bytes to parse
	 * @param resources the page resource map
	 * @param objects a map holding objects that might be required, such as
	 *            fonts, etc.
	 * @param tokenizer the tokenizer to check and split words with
	 * @param fontCharSet the character set to use for font decoding
	 * @param pm a progress monitor to receive updates on the word extraction
	 *            process
	 * @return the words extracted from the page
	 * @throws IOException
	 */
	public static void runPageContent(Hashtable entries, byte[] content, Hashtable resources, Map objects, List words, Tokenizer tokenizer, FontDecoderCharset fontCharSet, List figures, List paths, ProgressMonitor pm) throws IOException {
		runPageContent(0, entries, content, resources, objects, words, tokenizer, fontCharSet, figures, paths, ((words == null) && (figures == null) && (paths == null)), pm);
	}
	
	private static int runPageContent(int firstRenderOrderNumber, Hashtable entries, byte[] content, Hashtable resources, Map objects, List words, Tokenizer tokenizer, FontDecoderCharset fontCharSet, List figures, List paths, boolean assessFonts, ProgressMonitor pm) throws IOException {
		
		//	get XObject (required for figure extraction and recursive form handling)
		Object xObjectObj = dereference(resources.get("XObject"), objects);
		Hashtable xObject = ((xObjectObj instanceof Hashtable) ? ((Hashtable) xObjectObj) : null);
		
		//	get fonts (unless we're after figures or vector paths, but not after words)
		Hashtable fonts = (((words == null) && ((figures != null) || (paths != null))) ? new Hashtable() : getPageFonts(resources, objects, ((words == null) ? PdfFontDecoder.NO_DECODING : fontCharSet), pm));
		
		//	we need to collect words even in font assessment mode
		if ((words == null) && assessFonts)
			words = new ArrayList();
		
		//	create renderer to fill lists
		PageContentRenderer pcr = new PageContentRenderer(firstRenderOrderNumber, resources, fonts, fontCharSet, xObject, objects, words, figures, paths, assessFonts);
		
		//	process page content through renderer
		PdfByteInputStream bytes = new PdfByteInputStream(new ByteArrayInputStream(content));
		Object obj;
		LinkedList stack = new LinkedList();
		while ((obj = cropNext(bytes, true, false)) != null) {
			if (obj instanceof PtTag) {
				if (DEBUG_RENDER_PAGE_CONTENT) System.out.println("Content tag: " + ((PtTag) obj).tag);
				pcr.evaluateTag(((PtTag) obj).tag, stack, pm);
			}
			else {
				if (DEBUG_RENDER_PAGE_CONTENT) System.out.println(obj.getClass().getName() + ": " + obj.toString());
				stack.addLast(obj);
			}
		}
//		
//		//	TODO remove this after vector graphics tests
//		if ((paths != null) && (paths.size() != 0) && !assessFonts) {
//			ImageDisplayDialog idd = new ImageDisplayDialog("Paths");
//			BufferedImage bi = new BufferedImage(1600, 2000, BufferedImage.TYPE_INT_ARGB);
//			Graphics2D gr = bi.createGraphics();
//			gr.setColor(Color.WHITE);
//			gr.fillRect(0, 0, bi.getWidth(), bi.getHeight());
//			gr.scale(1, -1);
//			gr.translate(0, -bi.getHeight());
//			gr.scale(2, 2);
//			System.out.println("Painting " + paths.size() + " paths:");
//			System.out.println(" - " + PPath.strokeCount + " stroked, colors:");
//			for (Iterator cit = PPath.strokeColors.iterator(); cit.hasNext();) {
//				String color = ((String) cit.next());
//				System.out.println("   - " + color + " (" + PPath.strokeColors.getCount(color) + " times)");
//			}
//			System.out.println(" - " + PPath.fillCount + " filled, colors:");
//			for (Iterator cit = PPath.fillColors.iterator(); cit.hasNext();) {
//				String color = ((String) cit.next());
//				System.out.println("   - " + color + " (" + PPath.fillColors.getCount(color) + " times)");
//			}
//			for (int p = 0; p < paths.size(); p++)
//				((PPath) paths.get(p)).paint(gr);
//			idd.addImage(bi, "All Paths in Page");
//			idd.setSize(900, 1080);
//			idd.setLocationRelativeTo(null);
//			idd.setVisible(true);
//		}
		
		//	sort out words with invalid bounding boxes, duplicate words, and words in all too light colors (water marks, etc.)
		if (words != null) {
			if (DEBUG_RENDER_PAGE_CONTENT && !assessFonts) System.out.println("Page content processed, got " + words.size() + " words");
			Set wordBounds = new HashSet();
			for (int w = 0; w < words.size(); w++) {
				PWord pw = ((PWord) words.get(w));
				if (DEBUG_RENDER_PAGE_CONTENT && !assessFonts) System.out.println(" - '" + pw.str + "' @ " + pw.bounds);
				if ((pw.bounds.getWidth() <= 0) || (pw.bounds.getHeight() <= 0)) {
					if (DEBUG_RENDER_PAGE_CONTENT && !assessFonts)
						System.out.println(" --> removed for invalid bounds");
					words.remove(w--);
					continue;
				}
				if (!wordBounds.add(pw.bounds.toString())) {
					if (DEBUG_RENDER_PAGE_CONTENT && !assessFonts)
						System.out.println(" --> removed as duplicate");
					words.remove(w--);
					continue;
				}
//				//	we cannot do this here, as figure labels might well come in white if image proper is dark !!!
//				if (pw.color != null) {
//					float pwBrightness = getBrightness(pw.color);
//					if (DEBUG_RENDER_PAGE_CONTENT && !assessFonts)
//						System.out.println(" - brightness is " + pwBrightness);
//					if (pwBrightness > 0.75) {
//						if (DEBUG_RENDER_PAGE_CONTENT && !assessFonts)
//							System.out.println(" --> removed as too faint (likely water mark)");
//						words.remove(w--);
//						continue;
//					}
//				}
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
			if (words != null)
				for (int w = 0; w < words.size(); w++) {
					PWord pw = ((PWord) words.get(w));
					if (DEBUG_RENDER_PAGE_CONTENT && !assessFonts) System.out.println(" - '" + pw.str + "' @ " + pw.bounds + " directed " + pw.fontDirection);
					int rfd = pw.fontDirection;
					if ((rotate == 90) || (rotate == -270))
						rfd--;
					else if ((rotate == 270) || (rotate == -90))
						rfd++;
					PWord rpw = new PWord(pw.renderOrderNumber, pw.charCodes, pw.str, rotateBounds(pw.bounds, pgw, pgh, rotate), pw.color, pw.fontSize, rfd, pw.font);
					rpw.joinWithNext = pw.joinWithNext;
					if (DEBUG_RENDER_PAGE_CONTENT && !assessFonts) System.out.println(" --> '" + rpw.str + "' @ " + rpw.bounds + " directed " + rpw.fontDirection);
					words.set(w, rpw);
				}
			if (figures != null)
				for (int f = 0; f < figures.size(); f++) {
					PFigure pf = ((PFigure) figures.get(f));
					if (DEBUG_RENDER_PAGE_CONTENT && !assessFonts) System.out.println(" - '" + pf.name + "' @ " + pf.bounds);
					PFigure rpf = new PFigure(pf.renderOrderNumber, pf.name, rotateBounds(pf.bounds, pgw, pgh, rotate), pf.refOrData, pf.rotation, pf.rightSideLeft, pf.upsideDown);
					if (DEBUG_RENDER_PAGE_CONTENT && !assessFonts) System.out.println(" --> '" + rpf.name + "' @ " + rpf.bounds);
					figures.set(f, rpf);
				}
		}
		
		//	merge words with extremely close or overlapping bounding boxes (can be split due to implicit spaces and caret repositioning, for instance)
		if ((words != null) && (words.size() > 1)) {
			PWord lastWord = ((PWord) words.get(0));
			double lastWordMiddleX = ((lastWord.bounds.getMinX() + lastWord.bounds.getMaxX()) / 2);
			double lastWordMiddleY = ((lastWord.bounds.getMinY() + lastWord.bounds.getMaxY()) / 2);
			for (int w = 1; w < words.size(); w++) {
				PWord word = ((PWord) words.get(w));
				if (DEBUG_MERGE_WORDS && !assessFonts) System.out.println("Checking words " + lastWord.str + " @" + lastWord.bounds.toString() + " and " + word.str + " @" + word.bounds.toString());
				double wordMiddleX = ((word.bounds.getMinX() + word.bounds.getMaxX()) / 2);
				double wordMiddleY = ((word.bounds.getMinY() + word.bounds.getMaxY()) / 2);
				
				//	check for combining accents at adjacent word boundaries
				boolean lastWordEndsCombiningAccent = ((lastWord.str != null) && (lastWord.str.length() >= 1) && PdfCharDecoder.isCombiningAccent(lastWord.str.charAt(lastWord.str.length()-1)));
				boolean lastWordIsCombiningAccent = (lastWordEndsCombiningAccent && (lastWord.str.length() == 1));
				boolean wordStartsCombiningAccent = ((word.str != null) && (word.str.length() >= 1) && PdfCharDecoder.isCombiningAccent(word.str.charAt(0)));
				boolean wordIsCombiningAccent = (wordStartsCombiningAccent && (word.str.length() == 1));
				
				//	do not merge or join words whose font properties don't match
				if (!areWordsCompatible(lastWord, lastWordMiddleX, lastWordMiddleY, word, wordMiddleX, wordMiddleY, (!assessFonts && !lastWordIsCombiningAccent && !wordIsCombiningAccent), (!lastWordIsCombiningAccent && !wordIsCombiningAccent))) {
					lastWord = word;
					lastWordMiddleX = wordMiddleX;
					lastWordMiddleY = wordMiddleY;
					if (DEBUG_MERGE_WORDS && !assessFonts) System.out.println(" --> incompatible");
					continue;
				}
				
				//	in font assessment mode, check distance, record char neighborhood, and re're done
				if (assessFonts || (tokenizer == null)) {
					if ((lastWord.font != null) && (word.font != null) && areWordsCloseEnough(lastWord, word, true)) {
						int lwChb = (lastWord.charCodes.charAt(lastWord.charCodes.length() - 1) & 255);
						int wChb = (word.charCodes.charAt(0) & 255);
						lastWord.font.setCharsNeighbored(lwChb, word.font, wChb);
					}
					lastWord = word;
					lastWordMiddleX = wordMiddleX;
					lastWordMiddleY = wordMiddleY;
					continue;
				}
				
				//	never merge or join numbers (unless font is same and has implicit spaces)
				if ((Gamta.isNumber(lastWord.str) || Gamta.isNumber(word.str)) && ((lastWord.font == null) || (lastWord.font != word.font) || !lastWord.font.hasImplicitSpaces)) {
					lastWord = word;
					lastWordMiddleX = wordMiddleX;
					lastWordMiddleY = wordMiddleY;
					if (DEBUG_MERGE_WORDS && !assessFonts) System.out.println(" --> not merging numbers");
					continue;
				}
				
				TokenSequence lastWordTokens = tokenizer.tokenize(lastWordEndsCombiningAccent ? lastWord.str.substring(0, (lastWord.str.length()-1)) : lastWord.str);
				TokenSequence wordTokens = tokenizer.tokenize(wordStartsCombiningAccent ? word.str.substring(1) : word.str);
				boolean needsSpace = ((lastWordTokens.size() != 0) && (wordTokens.size() != 0) && Gamta.spaceAfter(lastWordTokens.lastValue()) && Gamta.spaceBefore(wordTokens.firstValue()));
				
				//	check distance between words, figuring in word spacing
				if (!areWordsCloseEnough(lastWord, word, needsSpace)) {
					lastWord = word;
					lastWordMiddleX = wordMiddleX;
					lastWordMiddleY = wordMiddleY;
					if (DEBUG_MERGE_WORDS && !assessFonts) System.out.println(" --> too far apart");
					continue;
				}
				
				//	never merge or join words that tokenize apart
				TokenSequence jointWordTokens = tokenizer.tokenize((lastWordEndsCombiningAccent ? lastWord.str.substring(0, (lastWord.str.length()-1)) : lastWord.str) + (wordStartsCombiningAccent ? word.str.substring(1) : word.str));
				if ((1 < jointWordTokens.size()) && ((lastWordTokens.size() + wordTokens.size()) < (jointWordTokens.size() + ((lastWordIsCombiningAccent || wordIsCombiningAccent) ? 0 : 1)))) {
					lastWord = word;
					lastWordMiddleX = wordMiddleX;
					lastWordMiddleY = wordMiddleY;
					if (DEBUG_MERGE_WORDS && !assessFonts) {
						System.out.println(" --> tokenization mismatch:");
						System.out.println("   - " + TokenSequenceUtils.concatTokens(lastWordTokens, false, false));
						System.out.println("   - " + TokenSequenceUtils.concatTokens(wordTokens, false, false));
						System.out.println("   != " + TokenSequenceUtils.concatTokens(jointWordTokens, false, false));
					}
					continue;
				}
				
				/* logically join (but never physically merge) words if font
				 * names don't match, messes up font char strings, and thus gets
				 * in the way of correction; also don't join words of different
				 * font sizes, as that messes up rendering */
				boolean fontMismatch = false;
				if ((lastWord.font != null) && (lastWord.font.name != null) && (word.font != null) && (word.font.name != null) && (!lastWord.font.name.equals(word.font.name) || (lastWord.fontSize != word.fontSize))) {
					
					/* if we have a combining accent coming from another font:
					 * - add combined char to font base char belongs to (if there are bytes free)
					 * - add combination of char images if both available
					 * - map combined char name back to char byte to make additional char reusable
					 * - adjust word char strings accordingly
					 */
					if (lastWordIsCombiningAccent != wordIsCombiningAccent)
						fontMismatch = true;
					
					//	in all other cases, we waive the merger outright
					else {
						lastWord.joinWithNext = true;
						lastWord = word;
						lastWordMiddleX = wordMiddleX;
						lastWordMiddleY = wordMiddleY;
						if (DEBUG_MERGE_WORDS && !assessFonts) System.out.println(" --> font name or size mismatch");
						continue;
					}
				}
				
				float minX = ((float) Math.min(lastWord.bounds.getMinX(), word.bounds.getMinX()));
				float maxX = ((float) Math.max(lastWord.bounds.getMaxX(), word.bounds.getMaxX()));
				float minY = ((float) Math.min(lastWord.bounds.getMinY(), word.bounds.getMinY()));
				float maxY = ((float) Math.max(lastWord.bounds.getMaxY(), word.bounds.getMaxY()));
				Rectangle2D.Float jointWordBox = new Rectangle2D.Float(minX, minY, (maxX - minX), (maxY - minY));
				PWord jointWord;
				boolean revisitPreviousWord = false;
				
				//	test if second word (a) starts with combinable accent, (b) this accent is combinable with last char of first word, and (c) there is sufficient overlap
				if (areWordsCloseEnoughForCombinedAccent(lastWord, lastWordIsCombiningAccent, word, wordIsCombiningAccent) && wordStartsCombiningAccent) {
					if (DEBUG_RENDER_PAGE_CONTENT || DEBUG_MERGE_WORDS) {
						System.out.println("Testing for possible letter diacritic merger:");
						System.out.println(" - first word ends with '" + lastWord.str.charAt(lastWord.str.length() - 1) + "' (" + ((int) lastWord.str.charAt(lastWord.str.length() - 1)) + ")");
						System.out.println(" - second word starts with '" + word.str.charAt(0) + "' (" + ((int) word.str.charAt(0)) + ")");
					}
					String combinedCharName = PdfCharDecoder.getCombinedCharName(lastWord.str.charAt(lastWord.str.length() - 1), word.str.charAt(0));
					if (DEBUG_RENDER_PAGE_CONTENT || DEBUG_MERGE_WORDS)
						System.out.println(" - combined char name is '" + combinedCharName + "'");
					char combinedChar = StringUtils.getCharForName(combinedCharName);
					if (DEBUG_RENDER_PAGE_CONTENT || DEBUG_MERGE_WORDS)
						System.out.println(" - combined char is '" + combinedChar + "' (" + ((int) combinedChar) + ")");
					if (combinedChar > 0) {
						
						//	synthesize combined diacritic in last word font
						int lwEndChb = (((int) lastWord.charCodes.charAt(lastWord.charCodes.length()-1)) & 255);
						int wStartChb = (((int) word.charCodes.charAt(0)) & 255);
						int cChb = lastWord.font.addCombinedChar(lwEndChb, lastWord.font, wStartChb, word.font, ("" + combinedChar), combinedCharName);
						
						//	modify char codes
						String jointCharCodes;
						if (cChb == -1) {
							if (fontMismatch) {
								lastWord.joinWithNext = true;
								lastWord = word;
								lastWordMiddleX = wordMiddleX;
								lastWordMiddleY = wordMiddleY;
								if (DEBUG_MERGE_WORDS && !assessFonts) System.out.println(" --> font name or size mismatch, cannot merge words without char synthesis");
								continue;
							}
							else jointCharCodes = (lastWord.charCodes.substring(0, lastWord.charCodes.length()-1) + ((char) (((int) lastWord.charCodes.charAt(lastWord.charCodes.length()-1)) & 255)) + word.charCodes);
						}
						else jointCharCodes = (lastWord.charCodes.substring(0, lastWord.charCodes.length()-1) + ((char) (256 | cChb)) + word.charCodes.substring(1));
						
						//	create joint word
						jointWord = new PWord(Math.min(lastWord.renderOrderNumber, word.renderOrderNumber), jointCharCodes, (lastWord.str.substring(0, lastWord.str.length()-1) + combinedChar + word.str.substring(1)), jointWordBox, lastWord.color, Math.max(lastWord.fontSize, word.fontSize), lastWord.fontDirection, lastWord.font);
						jointWord.joinWithNext = word.joinWithNext;
						if (DEBUG_RENDER_PAGE_CONTENT || DEBUG_MERGE_WORDS)
							System.out.println(" --> combined letter and diacritic marker");
					}
					else if (fontMismatch) {
						lastWord.joinWithNext = true;
						lastWord = word;
						lastWordMiddleX = wordMiddleX;
						lastWordMiddleY = wordMiddleY;
						if (DEBUG_MERGE_WORDS && !assessFonts) System.out.println(" --> font name or size mismatch, cannot merge words without combined char");
						continue;
					}
					else {
						jointWord = new PWord(Math.min(lastWord.renderOrderNumber, word.renderOrderNumber), (lastWord.charCodes + word.charCodes), (lastWord.str + word.str), jointWordBox, lastWord.color, Math.max(lastWord.fontSize, word.fontSize), lastWord.fontDirection, lastWord.font);
						jointWord.joinWithNext = word.joinWithNext;
						if (DEBUG_RENDER_PAGE_CONTENT || DEBUG_MERGE_WORDS)
							System.out.println(" --> combination not possible");
					}
				}
				
				//	test if first word (a) ends with combinable accent, (b) this accent is combinable with first char of second word, and (c) there is sufficient overlap
				else if (areWordsCloseEnoughForCombinedAccent(lastWord, lastWordIsCombiningAccent, word, wordIsCombiningAccent) && lastWordEndsCombiningAccent) {
					if (DEBUG_RENDER_PAGE_CONTENT || DEBUG_MERGE_WORDS) {
						System.out.println("Testing for possible letter diacritic merger:");
						System.out.println(" - first word ends with '" + lastWord.str.charAt(lastWord.str.length() - 1) + "' (" + ((int) lastWord.str.charAt(lastWord.str.length() - 1)) + ")");
						System.out.println(" - second word starts with '" + word.str.charAt(0) + "' (" + ((int) word.str.charAt(0)) + ")");
					}
					String combinedCharName = PdfCharDecoder.getCombinedCharName(word.str.charAt(0), lastWord.str.charAt(lastWord.str.length() - 1));
					if (DEBUG_RENDER_PAGE_CONTENT || DEBUG_MERGE_WORDS)
						System.out.println(" - combined char name is '" + combinedCharName + "'");
					char combinedChar = StringUtils.getCharForName(combinedCharName);
					if (DEBUG_RENDER_PAGE_CONTENT || DEBUG_MERGE_WORDS)
						System.out.println(" - combined char is '" + combinedChar + "' (" + ((int) combinedChar) + ")");
					if (combinedChar > 0) {
						
						//	synthesize combined diacritic in word font
						int lwEndChb = (((int) lastWord.charCodes.charAt(lastWord.charCodes.length()-1)) & 255);
						int wStartChb = (((int) word.charCodes.charAt(0)) & 255);
						int cChb = word.font.addCombinedChar(lwEndChb, lastWord.font, wStartChb, word.font, ("" + combinedChar), combinedCharName);
						
						//	modify char codes
						String jointCharCodes;
						if (cChb == -1) {
							if (fontMismatch) {
								lastWord.joinWithNext = true;
								lastWord = word;
								lastWordMiddleX = wordMiddleX;
								lastWordMiddleY = wordMiddleY;
								if (DEBUG_MERGE_WORDS && !assessFonts) System.out.println(" --> font name or size mismatch, cannot merge words without char synthesis");
								continue;
							}
							else jointCharCodes = (lastWord.charCodes + ((char) (((int) word.charCodes.charAt(0)) & 255)) + word.charCodes.substring(1));
						}
						else jointCharCodes = (lastWord.charCodes.substring(0, lastWord.charCodes.length()-1) + ((char) (256 | cChb)) + word.charCodes.substring(1));
						
						//	create joint word
						jointWord = new PWord(Math.min(lastWord.renderOrderNumber, word.renderOrderNumber), jointCharCodes, (lastWord.str.substring(0, lastWord.str.length()-1) + combinedChar + word.str.substring(1)), jointWordBox, lastWord.color, Math.max(lastWord.fontSize, word.fontSize), word.fontDirection, word.font);
						jointWord.joinWithNext = word.joinWithNext;
						if (DEBUG_RENDER_PAGE_CONTENT || DEBUG_MERGE_WORDS)
							System.out.println(" --> combined letter and diacritic marker");
						
						//	if we just merged away the last word altogether, we need to jump back by one word
						if (lastWordIsCombiningAccent)
							revisitPreviousWord = true;
					}
					else {
						jointWord = new PWord(Math.min(lastWord.renderOrderNumber, word.renderOrderNumber), (lastWord.charCodes + word.charCodes), (lastWord.str + word.str), jointWordBox, lastWord.color, Math.max(lastWord.fontSize, word.fontSize), lastWord.fontDirection, lastWord.font);
						jointWord.joinWithNext = word.joinWithNext;
						if (DEBUG_RENDER_PAGE_CONTENT || DEBUG_MERGE_WORDS)
							System.out.println(" --> combination not possible");
					}
				}
				else if (fontMismatch) {
					lastWord.joinWithNext = true;
					lastWord = word;
					lastWordMiddleX = wordMiddleX;
					lastWordMiddleY = wordMiddleY;
					if (DEBUG_MERGE_WORDS && !assessFonts) System.out.println(" --> font name or size mismatch, cannot merge words without combined char");
					continue;
				}
				else {
					jointWord = new PWord(Math.min(lastWord.renderOrderNumber, word.renderOrderNumber), (lastWord.charCodes + word.charCodes), (lastWord.str + word.str), jointWordBox, lastWord.color, Math.max(lastWord.fontSize, word.fontSize), lastWord.fontDirection, lastWord.font);
					jointWord.joinWithNext = word.joinWithNext;
				}
				
				if (DEBUG_RENDER_PAGE_CONTENT || DEBUG_MERGE_WORDS) {
					System.out.println("Got joint word: '" + jointWord.str + "' @ " + jointWord.bounds);
					System.out.println(" --> extent is " + ((float) jointWordBox.getMinX()) + "-" + ((float) jointWordBox.getMaxX()) + " x " + ((float) jointWordBox.getMaxY()) + "-" + ((float) jointWordBox.getMinY()));
					System.out.println(" --> base extents are\n" +
							"     " + ((float) lastWord.bounds.getMinX()) + "-" + ((float) lastWord.bounds.getMaxX()) + " x " + ((float) lastWord.bounds.getMaxY()) + "-" + ((float) lastWord.bounds.getMinY()) + "\n" +
							"     " + ((float) word.bounds.getMinX()) + "-" + ((float) word.bounds.getMaxX()) + " x " + ((float) word.bounds.getMaxY()) + "-" + ((float) word.bounds.getMinY()) +
						"");
				}
				
				//	store joint word
				words.set((w-1), jointWord); // replace last word
				words.remove(w--); // remove current word and counter loop increment
				
				//	jump back one word
				if (revisitPreviousWord && (w != 0))
					lastWord = ((PWord) words.get(--w)); // jump back one further
				
				//	go on with joint word
				else lastWord = jointWord;
				
				//	update measurements
				lastWordMiddleX = ((lastWord.bounds.getMinX() + lastWord.bounds.getMaxX()) / 2);
				lastWordMiddleY = ((lastWord.bounds.getMinY() + lastWord.bounds.getMaxY()) / 2);
			}
		}
		
		//	un-tangle overlapping bounding boxes that did not merge
		if ((words != null) && (words.size() > 1) && (tokenizer != null)) {
			PWord lastWord = ((PWord) words.get(0));
			for (int w = 1; w < words.size(); w++) {
				PWord word = ((PWord) words.get(w));
				if (DEBUG_MERGE_WORDS && !assessFonts) System.out.println("Checking words " + lastWord.str + " @" + lastWord.bounds.toString() + " and " + word.str + " @" + word.bounds.toString());
				
				//	no overlap
				if (!lastWord.bounds.intersects(word.bounds)) {
					lastWord = word;
					if (DEBUG_MERGE_WORDS && !assessFonts) System.out.println(" --> no overlap");
					continue;
				}
				
				//	different font directions, skip for now
				if (lastWord.fontDirection != word.fontDirection) {
					lastWord = word;
					if (DEBUG_MERGE_WORDS && !assessFonts) System.out.println(" --> different font directions");
					continue;
				}
				
				//	check if words on same line, last word starts before current one, and vertical overlap sufficient
				if (word.fontDirection == PWord.LEFT_RIGHT_FONT_DIRECTION) {
					if (lastWord.bounds.getMinX() > word.bounds.getMinX()) {
						lastWord = word;
						if (DEBUG_MERGE_WORDS && !assessFonts) System.out.println(" --> inverse overlap");
						continue;
					}
					if (word.fontSize == lastWord.fontSize) {
						float uTop = ((float) Math.max(lastWord.bounds.getMaxY(), word.bounds.getMaxY()));
						float iTop = ((float) Math.min(lastWord.bounds.getMaxY(), word.bounds.getMaxY()));
						float iBottom = ((float) Math.max(lastWord.bounds.getMinY(), word.bounds.getMinY()));
						float uBottom = ((float) Math.min(lastWord.bounds.getMinY(), word.bounds.getMinY()));
						if (((iTop - iBottom) * 10) < ((uTop - uBottom) * 9)) {
							lastWord = word;
							if (DEBUG_MERGE_WORDS && !assessFonts) System.out.println(" --> different lines");
							continue;
						}
					}
					else {
						float lwCenterY = ((float) ((lastWord.bounds.getMinY() + lastWord.bounds.getMaxY()) / 2));
						float wCenterY = ((float) ((word.bounds.getMinY() + word.bounds.getMaxY()) / 2));
						if ((lwCenterY < word.bounds.getMaxY()) && (lwCenterY > word.bounds.getMinY())) { /* last word center Y inside word height */ }
						else if ((wCenterY < lastWord.bounds.getMaxY()) && (wCenterY > lastWord.bounds.getMinY())) { /* word center Y inside last word height */ }
						else {
							lastWord = word;
							if (DEBUG_MERGE_WORDS && !assessFonts) System.out.println(" --> different lines");
							continue;
						}
					}
				}
				else {
					if ((word.fontDirection == PWord.BOTTOM_UP_FONT_DIRECTION) && (lastWord.bounds.getMinY() > word.bounds.getMinY())) {
						lastWord = word;
						if (DEBUG_MERGE_WORDS && !assessFonts) System.out.println(" --> inverse overlap");
						continue;
					}
					else if ((word.fontDirection == PWord.TOP_DOWN_FONT_DIRECTION) && (lastWord.bounds.getMaxY() < word.bounds.getMaxY())) {
						lastWord = word;
						if (DEBUG_MERGE_WORDS && !assessFonts) System.out.println(" --> inverse overlap");
						continue;
					}
					if (word.fontSize == lastWord.fontSize) {
						float uLeft = ((float) Math.min(lastWord.bounds.getMinX(), word.bounds.getMinX()));
						float iLeft = ((float) Math.max(lastWord.bounds.getMinX(), word.bounds.getMinX()));
						float iRight = ((float) Math.max(lastWord.bounds.getMaxX(), word.bounds.getMaxX()));
						float uRight = ((float) Math.min(lastWord.bounds.getMaxX(), word.bounds.getMaxX()));
						if (((iRight - iLeft) * 10) < ((uRight - uLeft) * 9)) {
							lastWord = word;
							if (DEBUG_MERGE_WORDS && !assessFonts) System.out.println(" --> different lines");
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
							if (DEBUG_MERGE_WORDS && !assessFonts) System.out.println(" --> different lines");
							continue;
						}
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
				PWord cutWord = new PWord(lastWord.renderOrderNumber, lastWord.charCodes, lastWord.str, cutWordBox, lastWord.color, lastWord.fontSize, lastWord.fontDirection, lastWord.font);
				cutWord.joinWithNext = lastWord.joinWithNext;
				if (DEBUG_RENDER_PAGE_CONTENT || DEBUG_MERGE_WORDS) System.out.println("Got cut word: '" + cutWord.str + "' @ " + cutWord.bounds);
				words.set((w-1), cutWord);
				lastWord = word;
			}
		}
		
		/* combine figures that are adjacent in one dimension and identical in
		 * the other, and do so recursively, both top-down and left-right, to
		 * also catch weirdly tiled images */
		while ((figures != null) && (figures.size() > 1)) {
			int figureCount = figures.size();
			
			//	try merging left to right
			Collections.sort(figures, leftRightFigureOrder);
			for (int f = 0; f < figures.size(); f++) {
				PFigure pf = ((PFigure) figures.get(f));
				if (DEBUG_RENDER_PAGE_CONTENT && !assessFonts) System.out.println("Checking image " + pf + " for left-right mergers");
				PFigure mpf = null;
				for (int cf = (f+1); cf < figures.size(); cf++) {
					PFigure cpf = ((PFigure) figures.get(cf));
					if (DEBUG_RENDER_PAGE_CONTENT && !assessFonts) System.out.println(" - comparing to image " + cpf);
					if (!figureEdgeMatch(pf.bounds.getMaxY(), cpf.bounds.getMaxY())) {
						if (DEBUG_RENDER_PAGE_CONTENT && !assessFonts) System.out.println(" ==> top edge mismatch");
						continue;
					}
					if (!figureEdgeMatch(pf.bounds.getMinY(), cpf.bounds.getMinY())) {
						if (DEBUG_RENDER_PAGE_CONTENT && !assessFonts) System.out.println(" ==> bottom edge mismatch");
						continue;
					}
					if (!figureEdgeMatch(pf.bounds.getMaxX(), cpf.bounds.getMinX())) {
						if (DEBUG_RENDER_PAGE_CONTENT && !assessFonts) System.out.println(" ==> not adjacent (" + pf.bounds.getMaxX() + " != " + cpf.bounds.getMinX() + ")");
						continue;
					}
					mpf = new PFigure(pf, cpf);
					figures.remove(cf);
					if (DEBUG_RENDER_PAGE_CONTENT && !assessFonts) System.out.println(" ==> got merged figure " + mpf);
					break;
				}
				if (mpf != null)
					figures.set(f--, mpf);
			}
			
			//	try merging top down
			Collections.sort(figures, topDownFigureOrder);
			for (int f = 0; f < figures.size(); f++) {
				PFigure pf = ((PFigure) figures.get(f));
				if (DEBUG_RENDER_PAGE_CONTENT && !assessFonts) System.out.println("Checking image " + pf + " for top-down mergers");
				PFigure mpf = null;
				for (int cf = (f+1); cf < figures.size(); cf++) {
					PFigure cpf = ((PFigure) figures.get(cf));
					if (DEBUG_RENDER_PAGE_CONTENT && !assessFonts) System.out.println(" - comparing to image " + cpf);
					if (!figureEdgeMatch(pf.bounds.getMinX(), cpf.bounds.getMinX())) {
						if (DEBUG_RENDER_PAGE_CONTENT && !assessFonts) System.out.println(" ==> left edge mismatch");
						continue;
					}
					if (!figureEdgeMatch(pf.bounds.getMaxX(), cpf.bounds.getMaxX())) {
						if (DEBUG_RENDER_PAGE_CONTENT && !assessFonts) System.out.println(" ==> right edge mismatch");
						continue;
					}
					if (!figureEdgeMatch(pf.bounds.getMinY(), cpf.bounds.getMaxY())) {
						if (DEBUG_RENDER_PAGE_CONTENT && !assessFonts) System.out.println(" ==> not adjacent (" + pf.bounds.getMinY() + " != " + cpf.bounds.getMaxY() + ")");
						continue;
					}
					mpf = new PFigure(pf, cpf);
					figures.remove(cf);
					if (DEBUG_RENDER_PAGE_CONTENT && !assessFonts) System.out.println(" ==> got merged figure " + mpf);
					break;
				}
				if (mpf != null)
					figures.set(f--, mpf);
			}
			
			//	nothing merged in either direction, we're done
			if (figures.size() == figureCount)
				break;
		}
		
		//	tell any recursive caller where to continue with numbering
		return (pcr.nextRenderOrderNumber + 1);
	}
	
	private static final Comparator leftRightFigureOrder = new Comparator() {
		public int compare(Object obj1, Object obj2) {
			PFigure pf1 = ((PFigure) obj1);
			PFigure pf2 = ((PFigure) obj2);
			int c = Double.compare(pf1.bounds.getMinX(), pf2.bounds.getMinX());
			return ((c == 0) ? Double.compare(pf2.bounds.getMaxX(), pf1.bounds.getMaxX()) : c);
		}
	};
	private static final Comparator topDownFigureOrder = new Comparator() {
		public int compare(Object obj1, Object obj2) {
			PFigure pf1 = ((PFigure) obj1);
			PFigure pf2 = ((PFigure) obj2);
			int c = Double.compare(pf2.bounds.getMaxY(), pf1.bounds.getMaxY());
			return ((c == 0) ? Double.compare(pf1.bounds.getMinY(), pf2.bounds.getMinY()) : c);
		}
	};
	private static final boolean figureEdgeMatch(double e1, double e2) {
//		return (e1 == e2); // TODO use this to see individual values
		return (Math.abs(e1 - e2) < 0.001); // TODO adjust this threshold
	}
	
	private static Rectangle2D.Float rotateBounds(Rectangle2D.Float bounds, float pw, float ph, int rotate) {
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
	
	private static boolean areWordsCloseEnoughForCombinedAccent(PWord lastWord, boolean lastWordIsCombiningAccent, PWord word, boolean wordIsCombiningAccent) {
		if (lastWord.fontDirection == PWord.LEFT_RIGHT_FONT_DIRECTION) {
			if (lastWordIsCombiningAccent) {
				float lwCenterX = ((float) ((lastWord.bounds.getMinX() + lastWord.bounds.getMaxX()) / 2));
				return (word.bounds.getMinX() < lwCenterX);
			}
			else if (wordIsCombiningAccent) {
				float wCenterX = ((float) ((word.bounds.getMinX() + word.bounds.getMaxX()) / 2));
				return (wCenterX < lastWord.bounds.getMaxX());
			}
			else return ((word.bounds.getMinX() - 1) < lastWord.bounds.getMaxX());
		}
		else if (lastWord.fontDirection == PWord.BOTTOM_UP_FONT_DIRECTION) {
			if (lastWordIsCombiningAccent) {
				float lwCenterY = ((float) ((lastWord.bounds.getMinY() + lastWord.bounds.getMaxY()) / 2));
				return (word.bounds.getMinY() < lwCenterY);
			}
			else if (wordIsCombiningAccent) {
				float wCenterY = ((float) ((word.bounds.getMinY() + word.bounds.getMaxY()) / 2));
				return (wCenterY < lastWord.bounds.getMaxY());
			}
			else return ((word.bounds.getMinY() - 1) < lastWord.bounds.getMaxY());
		}
		else if (lastWord.fontDirection == PWord.TOP_DOWN_FONT_DIRECTION) {
			if (lastWordIsCombiningAccent) {
				float lwCenterY = ((float) ((lastWord.bounds.getMinY() + lastWord.bounds.getMaxY()) / 2));
				return (lwCenterY < word.bounds.getMaxY());
			}
			else if (wordIsCombiningAccent) {
				float wCenterY = ((float) ((word.bounds.getMinY() + word.bounds.getMaxY()) / 2));
				return (lastWord.bounds.getMinY() < wCenterY);
			}
			return ((word.bounds.getMaxY() - 1) > lastWord.bounds.getMinY());
		}
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
		return (wordDistance < (1 - (needsSpace ? defaultNarrowSpaceToleranceFactor : 0)));
	}
	
	private static boolean areWordsCompatible(PWord lastWord, double lastWordMiddleX, double lastWordMiddleY, PWord word, double wordMiddleX, double wordMiddleY, boolean checkStyle, boolean checkHorizontalAlignment) {
		
		//	font direction
		if (lastWord.fontDirection != word.fontDirection) {
			if (DEBUG_MERGE_WORDS) System.out.println(" --> font direction mismatch");
			return false;
		}
		
		//	compare font properties
		if (checkStyle) {
			
			//	waive font style check for combining accents
			if (PdfCharDecoder.isCombiningAccent(lastWord.str.charAt(lastWord.str.length()-1)) || PdfCharDecoder.isCombiningAccent(word.str.charAt(0))) {}
			
			//	compare basic properties
			else if ((lastWord.bold != word.bold) || (lastWord.italics != word.italics) || (lastWord.font.hasDescent != word.font.hasDescent)) {
				if (DEBUG_MERGE_WORDS) System.out.println(" --> font style mismatch");
				return false;
			}
		}
//		if (checkStyle && ((lastWord.bold != word.bold) || (lastWord.italics != word.italics) || (lastWord.font.hasDescent != word.font.hasDescent) || (lastWord.fontDirection != word.fontDirection))) {
//			if (DEBUG_MERGE_WORDS) System.out.println(" --> font style mismatch");
//			return false;
//		}
		
		//	check in-line alignment
		if ((lastWord.fontDirection == PWord.LEFT_RIGHT_FONT_DIRECTION) ? ((lastWordMiddleY < word.bounds.getMinY()) || (lastWordMiddleY > word.bounds.getMaxY())) : ((lastWordMiddleX < word.bounds.getMinX()) || (lastWordMiddleX > word.bounds.getMaxX()))) {
			if (DEBUG_MERGE_WORDS) System.out.println(" --> vertical alignment mismatch (1)");
			return false;
		}
		if ((lastWord.fontDirection == PWord.LEFT_RIGHT_FONT_DIRECTION) ? ((wordMiddleY < lastWord.bounds.getMinY()) || (wordMiddleY > lastWord.bounds.getMaxY())) : ((wordMiddleX < lastWord.bounds.getMinX()) || (wordMiddleX > lastWord.bounds.getMaxX()))) {
			if (DEBUG_MERGE_WORDS) System.out.println(" --> vertical alignment mismatch (2)");
			return false;
		}
		
		//	check distance
		if (checkHorizontalAlignment && (lastWord.fontDirection == PWord.LEFT_RIGHT_FONT_DIRECTION) && ((word.bounds.getMinX() < lastWord.bounds.getMinX()) || (word.bounds.getMaxX() < lastWord.bounds.getMaxX()))) {
			if (DEBUG_MERGE_WORDS) System.out.println(" --> horizontal alignment mismatch (1)");
			return false;
		}
		if (checkHorizontalAlignment && (lastWord.fontDirection == PWord.BOTTOM_UP_FONT_DIRECTION) && ((word.bounds.getMinY() < lastWord.bounds.getMinY()) || (word.bounds.getMaxY() < lastWord.bounds.getMaxY()))) {
			if (DEBUG_MERGE_WORDS) System.out.println(" --> horizontal alignment mismatch (2)");
			return false;
		}
		if (checkHorizontalAlignment && (lastWord.fontDirection == PWord.TOP_DOWN_FONT_DIRECTION) && ((word.bounds.getMinY() > lastWord.bounds.getMinY()) || (word.bounds.getMaxY() > lastWord.bounds.getMaxY()))) {
			if (DEBUG_MERGE_WORDS) System.out.println(" --> horizontal alignment mismatch (3)");
			return false;
		}
		
		//	OK, we didn't find any red flags about these two
		return true;
	}
	
	private static final boolean DEBUG_RENDER_PAGE_CONTENT = false;
	private static final boolean DEBUG_MERGE_WORDS = false;
	
	private static float[] applyTransformationMatrices(float[] f, LinkedList transformationMatrices) {
		for (Iterator tmit = transformationMatrices.iterator(); tmit.hasNext();) {
			float[][] tm = ((float[][]) tmit.next());
			f = transform(f, tm);
		}
		return f;
	}
	
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
	private static void printMatrix(float[][] m) {
		for (int r = 0; r < m[0].length; r++) {
			System.out.print("    ");
			for (int c = 0; c < m.length; c++)
				System.out.print(" " + m[c][r]);
			System.out.println();
		}
	}
	
	private static class PageContentRenderer {
		
		private Hashtable resources;
		private Hashtable colorSpaces;
		private Hashtable graphicsStates;
		private Hashtable graphicsState;
		private Hashtable fonts;
		private FontDecoderCharset fontCharSet;
		private Hashtable xObject;
		private Map objects;
		
		private class SavedGraphicsState {
//			private ColorSpace sgsStrokeColorSpace;
			private PdfColorSpace sgsStrokeColorSpace;
			private Color sgsStrokeColor;
//			private ColorSpace sgsNonStrokeColorSpace;
			private PdfColorSpace sgsNonStrokeColorSpace;
			private Color sgsNonStrokeColor;
			
			private float sgsLineWidth;
			private byte sgsLineCapStyle;
			private byte sgsLineJointStyle;
			private float sgsMiterLimit;
			private Vector sgsDashPattern;
			private float sgsDashPatternPhase;
			
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
				this.sgsStrokeColorSpace = pcrStrokeColorSpace;
				this.sgsStrokeColor = pcrStrokeColor;
				this.sgsNonStrokeColorSpace = pcrNonStrokeColorSpace;
				this.sgsNonStrokeColor = pcrNonStrokeColor;
				
				this.sgsLineWidth = pcrLineWidth;
				this.sgsLineCapStyle = pcrLineCapStyle;
				this.sgsLineJointStyle = pcrLineJointStyle;
				this.sgsMiterLimit = pcrMiterLimit;
				this.sgsDashPattern = new Vector(pcrDashPattern);
				this.sgsDashPatternPhase = pcrDashPatternPhase;
				
				this.sgsFontKey = pcrFontKey;
				this.sgsFont = pcrFont;
				this.sgsFontSpaceWidth = pcrFontSpaceWidth;
				
				this.sgsFontSize = pcrFontSize;
				this.sgsLineHeight = pcrLineHeight;
				this.sgsCharSpacing = pcrCharSpacing;
				this.sgsWordSpacing = pcrWordSpacing;
				this.sgsHorizontalScaling = pcrHorizontalScaling;
				this.sgsTextRise = pcrTextRise;
				this.sgsTransformationMatrices = new LinkedList(pcrTransformationMatrices);
			}
			
			void restore() {
				pcrStrokeColorSpace = this.sgsStrokeColorSpace;
				pcrStrokeColor = this.sgsStrokeColor;
				pcrNonStrokeColorSpace = this.sgsNonStrokeColorSpace;
				pcrNonStrokeColor = this.sgsNonStrokeColor;
				
				pcrLineWidth = this.sgsLineWidth;
				pcrLineCapStyle = this.sgsLineCapStyle;
				pcrLineJointStyle = this.sgsLineJointStyle;
				pcrMiterLimit = this.sgsMiterLimit;
				pcrDashPattern = this.sgsDashPattern;
				pcrDashPatternPhase = this.sgsDashPatternPhase;
				
				pcrFontKey = this.sgsFontKey;
				pcrFont = this.sgsFont;
				pcrFontSpaceWidth = this.sgsFontSpaceWidth;
				
				pcrFontSize = this.sgsFontSize;
				pcrLineHeight = this.sgsLineHeight;
				pcrCharSpacing = this.sgsCharSpacing;
				pcrWordSpacing = this.sgsWordSpacing;
				pcrHorizontalScaling = this.sgsHorizontalScaling;
				pcrTextRise = this.sgsTextRise;
				pcrTransformationMatrices = this.sgsTransformationMatrices;
			}
		}
		
//		private ColorSpace pcrStrokeColorSpace;
		private PdfColorSpace pcrStrokeColorSpace;
		private Color pcrStrokeColor;
//		private ColorSpace pcrNonStrokeColorSpace;
		private PdfColorSpace pcrNonStrokeColorSpace;
		private Color pcrNonStrokeColor;
		
		private PPath pcrPath = null;
		private float pcrLineWidth = 1.0f;
		private byte pcrLineCapStyle = ((byte) 0);
		private byte pcrLineJointStyle = ((byte) 0);
		private float pcrMiterLimit = 10.0f;
		private Vector pcrDashPattern = new Vector();
		private float pcrDashPatternPhase = 0;
		
		private Object pcrFontKey = null;
		private PdfFont pcrFont = null;
		private float pcrFontSpaceWidth = 0;
		private float narrowSpaceToleranceFactor = PdfParser.defaultNarrowSpaceToleranceFactor;
		
		private float pcrFontSize = -1;
		private float eFontSize = -1;
		private int eFontDirection = PWord.LEFT_RIGHT_FONT_DIRECTION;
		private float pcrLineHeight = 0;
		private float pcrCharSpacing = 0;
		private float pcrWordSpacing = 0;
		private float pcrHorizontalScaling = 100;
		private float pcrTextRise = 0;
		
		private float[][] textMatrix = new float[3][3];
		private float[][] lineMatrix = new float[3][3];
		
//		private float[][] transformerMatrix = new float[3][3];
//		private LinkedList transformerMatrixStack = new LinkedList();
		private LinkedList pcrTransformationMatrices = new LinkedList();
		
		private LinkedList graphicsStateStack = new LinkedList();
		
		private List words;
		private List figures;
		private List paths;
		
		private boolean assessFonts;
		
		int nextRenderOrderNumber = 0;
		
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
		
		//	constructor for actual word extraction
		PageContentRenderer(int firstRenderOrderNumber, Hashtable resources, Hashtable fonts, FontDecoderCharset fontCharSet, Hashtable xObject, Map objects, List words, List figures, List paths, boolean assessFonts) {
			this.nextRenderOrderNumber = firstRenderOrderNumber;
			this.resources = resources;
			this.colorSpaces = ((Hashtable) getObject(resources, "ColorSpace", objects));
			this.graphicsStates = ((Hashtable) getObject(resources, "ExtGState", objects));
			this.fonts = fonts;
			this.fontCharSet = fontCharSet;
			this.xObject = xObject;
			this.objects = objects;
			this.words = words;
			this.figures = figures;
			this.paths = paths;
			this.assessFonts = assessFonts;
			for (int c = 0; c < this.textMatrix.length; c++) {
				Arrays.fill(this.textMatrix[c], 0);
				this.textMatrix[c][c] = 1;
			}
			cloneValues(this.textMatrix, this.lineMatrix);
		}
		
		private void doBT(LinkedList stack) {
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(" --> start text");
			for (int c = 0; c < this.textMatrix.length; c++) {
				Arrays.fill(this.textMatrix[c], 0);
				this.textMatrix[c][c] = 1;
			}
			cloneValues(this.textMatrix, this.lineMatrix);
		}
		
		private void doET(LinkedList stack) {
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(" --> end text");
		}
		
		private void doBMC(LinkedList stack) {
			Object n = stack.removeLast();
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(" --> start marked content: " + n);
		}
		
		private void doBDC(LinkedList stack) {
			Object d = stack.removeLast();
			Object n = stack.removeLast();
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(" --> start marked content with dictionary: " + n + " - " + d);
		}
		
		private void doEMC(LinkedList stack) {
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(" --> end marked content");
		}
		
		private void doMP(LinkedList stack) {
			Object n = stack.removeLast();
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(" --> mark point: " + n);
		}
		
		private void doDP(LinkedList stack) {
			Object d = stack.removeLast();
			Object n = stack.removeLast();
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(" --> mark point with dictionary: " + n + " - " + d);
		}
		
		private void doq(LinkedList stack) {
			this.graphicsStateStack.addLast(new SavedGraphicsState());
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(" --> save graphics state");
		}
		
		private void doQ(LinkedList stack) {
			((SavedGraphicsState) this.graphicsStateStack.removeLast()).restore();
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts) {
				System.out.println(" --> restore graphics state:");
				for (Iterator tmit = this.pcrTransformationMatrices.iterator(); tmit.hasNext();) {
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
			this.pcrTransformationMatrices.addFirst(nTm);
			/* seems like we really have to concatenate these transformations
			 * from the left ... PDF specification only states we have to
			 * concatenate them, but not from which side, and concatenating
			 * from the left seems to cause less garble (none) than from the
			 * right (messing up a few test files) */
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts) {
				System.out.println(" --> transformation matrix stack set to");
				for (Iterator tmit = this.pcrTransformationMatrices.iterator(); tmit.hasNext();) {
					float[][] tm = ((float[][]) tmit.next());
					printMatrix(tm);
					if (tmit.hasNext())
						System.out.println();
				}
			}
			this.computeEffectiveFontSizeAndDirection();
		}
		
		private float[] applyTransformationMatrices(float[] f) {
			return PdfParser.applyTransformationMatrices(f, this.pcrTransformationMatrices);
		}
		
		// d i j J M w gs
		private void dod(LinkedList stack)  {
			this.pcrDashPatternPhase = ((Number) stack.removeLast()).floatValue();
			this.pcrDashPattern = ((Vector) stack.removeLast());
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(" --> dash pattern " + this.pcrDashPattern + " at phase " + this.pcrDashPatternPhase);
		}
		
		private void doi(LinkedList stack)  {
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(" --> flatness");
		}
		
		private void doj(LinkedList stack) {
			this.pcrLineJointStyle = ((Number) stack.removeLast()).byteValue();
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(" --> line join style " + this.pcrLineJointStyle);
		}
		
		private void doJ(LinkedList stack) {
			this.pcrLineCapStyle = ((Number) stack.removeLast()).byteValue();
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(" --> line cap style " + this.pcrLineCapStyle);
		}
		
		private void doM(LinkedList stack) {
			this.pcrMiterLimit = ((Number) stack.removeLast()).floatValue();
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(" --> line miter limit " + this.pcrMiterLimit);
		}
		
		private void dow(LinkedList stack) {
			this.pcrLineWidth = ((Number) stack.removeLast()).floatValue();
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(" --> line width " + this.pcrLineWidth);
		}
		
		private void dom(LinkedList stack) {
			if (this.pcrPath == null)
				this.pcrPath = new PPath(this.nextRenderOrderNumber++);
			this.pcrPath.dom(stack, this.pcrTransformationMatrices);
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(" --> move to " + this.pcrPath.x + "/" + this.pcrPath.y);
		}
		
		private void dol(LinkedList stack) {
			this.pcrPath.dol(stack, this.pcrTransformationMatrices);
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(" --> line to " + this.pcrPath.x + "/" + this.pcrPath.y);
		}
		private void doh(LinkedList stack) {
			this.pcrPath.doh(stack);
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(" --> path closed to " + this.pcrPath.x + "/" + this.pcrPath.y);
		}
		private void dore(LinkedList stack) {
			if (this.pcrPath == null)
				this.pcrPath = new PPath(this.nextRenderOrderNumber++);
			this.pcrPath.dore(stack, this.pcrTransformationMatrices);
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(" --> rectangle at " + this.pcrPath.x + "/" + this.pcrPath.y);
		}
		
		private void doc(LinkedList stack) {
			this.pcrPath.doc(stack, this.pcrTransformationMatrices);
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(" --> curve to " + this.pcrPath.x + "/" + this.pcrPath.y);
		}
		private void dov(LinkedList stack) {
			this.pcrPath.dov(stack, this.pcrTransformationMatrices);
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(" --> curve to " + this.pcrPath.x + "/" + this.pcrPath.y);
		}
		private void doy(LinkedList stack) {
			this.pcrPath.doy(stack, this.pcrTransformationMatrices);
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(" --> curve to " + this.pcrPath.x + "/" + this.pcrPath.y);
		}
		
		private void doS(LinkedList stack) {
			this.pcrPath.stroke(this.pcrStrokeColor, this.pcrLineWidth, this.pcrLineCapStyle, this.pcrLineJointStyle, this.pcrMiterLimit, this.pcrDashPattern, this.pcrDashPatternPhase, this.pcrTransformationMatrices);
			if (this.paths != null)
				this.paths.add(this.pcrPath);
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(" --> path stroked inside " + this.pcrPath.getBounds());
			this.pcrPath = null;
		}
		private void dos(LinkedList stack) {
			this.doh(stack);
			this.doS(stack);
		}
		private void dof(LinkedList stack) {
			this.doh(stack);
			this.pcrPath.fill(this.pcrNonStrokeColor);
			if (this.paths != null)
				this.paths.add(this.pcrPath);
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(" --> path filled inside " + this.pcrPath.getBounds());
			this.pcrPath = null;
		}
		private void doF(LinkedList stack) {
			this.dof(stack);
		}
		private void dofasterisc(LinkedList stack) {
			this.dof(stack);
		}
		private void doB(LinkedList stack) {
			this.pcrPath.fillAndStroke(this.pcrNonStrokeColor, this.pcrStrokeColor, this.pcrLineWidth, this.pcrLineCapStyle, this.pcrLineJointStyle, this.pcrMiterLimit, this.pcrDashPattern, this.pcrDashPatternPhase, this.pcrTransformationMatrices);
			if (this.paths != null)
				this.paths.add(this.pcrPath);
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(" --> path filled and stroked inside " + this.pcrPath.getBounds());
			this.pcrPath = null;
		}
		private void doBasterisc(LinkedList stack) {
			this.doB(stack);
		}
		private void dob(LinkedList stack) {
			this.doh(stack);
			this.doB(stack);
		}
		private void dobasterisc(LinkedList stack) {
			this.doh(stack);
			this.doBasterisc(stack);
		}
		private void don(LinkedList stack) {
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(" --> path nilled inside " + this.pcrPath.getBounds());
			this.pcrPath = null;
		}
		
		private void dogs(LinkedList stack) {
			Object gsKey = stack.removeLast();
			this.graphicsState = ((Hashtable) getObject(this.graphicsStates, gsKey.toString(), this.objects));
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(" --> graphics state to " + gsKey + ": " + this.graphicsState);
		}
		
		private void doCS(LinkedList stack) {
			this.pcrStrokeColorSpace = this.getColorSpace(stack.removeLast().toString());
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(" --> stroking color space " + this.pcrStrokeColorSpace.name);
			this.pcrStrokeColor = Color.BLACK;
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(" --> stroking color " + this.pcrNonStrokeColor);
		}
		private void docs(LinkedList stack) {
			this.pcrNonStrokeColorSpace = this.getColorSpace(stack.removeLast().toString());
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(" --> non-stroking color space " + this.pcrNonStrokeColorSpace.name);
			this.pcrNonStrokeColor = Color.BLACK;
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(" --> non-stroking color " + this.pcrNonStrokeColor);
		}
		private PdfColorSpace getColorSpace(String csName) {
			PdfColorSpace cs = PdfColorSpace.getColorSpace(csName);
			if (cs != null)
				return cs;
			if ((this.colorSpaces != null) && (this.colorSpaces.get(csName) != null)) {
				Object csObj = getObject(this.colorSpaces, csName, this.objects);
				System.out.println("Got color space '" + csName + "': " + csObj);
				if (csObj instanceof Vector)
					return PdfColorSpace.getColorSpace(csObj, this.objects);
				else if (csObj != null)
					return this.getColorSpace(csObj.toString());
				else throw new RuntimeException("Strange Color Space '" + csName + "': " + csObj);
			}
			else throw new RuntimeException("Unknown Color Space '" + csName + "'");
		}
		
		private void doSCN(LinkedList stack) {
			this.pcrStrokeColor = this.pcrStrokeColorSpace.getColor(stack, null);
			if (this.graphicsState != null) {
				Number sCa = ((Number) this.graphicsState.get("CA"));
				if (sCa != null)
					this.pcrNonStrokeColor = this.getCompositeAlphaColor(this.pcrNonStrokeColor, Math.round(sCa.floatValue() * 255));
			}
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(" --> stroking color " + this.pcrNonStrokeColor);
		}
		private void doscn(LinkedList stack) {
			this.pcrNonStrokeColor = this.pcrNonStrokeColorSpace.getColor(stack, null);
			if (this.graphicsState != null) {
				Number nsCa = ((Number) this.graphicsState.get("ca"));
				if (nsCa != null)
					this.pcrNonStrokeColor = this.getCompositeAlphaColor(this.pcrNonStrokeColor, Math.round(nsCa.floatValue() * 255));
			}
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(" --> non-stroking color " + this.pcrNonStrokeColor);
		}
		private Color getCompositeAlphaColor(Color color, int compositeAlpha) {
			if (color == null)
				return color;
			if (compositeAlpha >= 255)
				return color;
			return new Color(color.getRed(), color.getGreen(), color.getBlue(), ((color.getAlpha() * compositeAlpha) / 255));
		}
		
		private void doG(LinkedList stack) {
			stack.addLast("DeviceGray");
			this.doCS(stack);
			this.doSCN(stack);
		}
		private void dog(LinkedList stack) {
			stack.addLast("DeviceGray");
			this.docs(stack);
			this.doscn(stack);
		}
		
		private void doRG(LinkedList stack) {
			stack.addLast("DeviceRGB");
			this.doCS(stack);
			this.doSCN(stack);
		}
		private void dorg(LinkedList stack) {
			stack.addLast("DeviceRGB");
			this.docs(stack);
			this.doscn(stack);
		}
		
		private void doK(LinkedList stack) {
			stack.addLast("DeviceCMYK");
			this.doCS(stack);
			this.doSCN(stack);
		}
		private void dok(LinkedList stack) {
			stack.addLast("DeviceCMYK");
			this.docs(stack);
			this.doscn(stack);
		}
		
		private void doTc(LinkedList stack) {
			this.pcrCharSpacing = ((Number) stack.removeLast()).floatValue();
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(" --> char spacing " + this.pcrCharSpacing);
		}
		
		private void doTw(LinkedList stack) {
			this.pcrWordSpacing = ((Number) stack.removeLast()).floatValue();
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(" --> word spacing " + this.pcrWordSpacing);
		}
		
		private void doTz(LinkedList stack) {
			this.pcrHorizontalScaling = ((Number) stack.removeLast()).floatValue();
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(" --> horizontal scaling " + this.pcrHorizontalScaling);
		}
		
		private void doTL(LinkedList stack) {
			this.pcrLineHeight = ((Number) stack.removeLast()).floatValue();
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(" --> leading (line height) " + this.pcrLineHeight);
		}
		
		private void doTf(LinkedList stack) {
			this.pcrFontSize = ((Number) stack.removeLast()).floatValue();
			this.pcrFontKey = stack.removeLast();
			if (DEBUG_RENDER_PAGE_CONTENT) System.out.println(" --> font key set to " + this.pcrFontKey);
			this.pcrFont = ((PdfFont) this.fonts.get(this.pcrFontKey));
			if (this.pcrFont != null) {
				this.pcrFontSpaceWidth = this.pcrFont.getCharWidth(' ');
				this.narrowSpaceToleranceFactor = Math.max(defaultNarrowSpaceToleranceFactor, (absMinNarrowSpaceWidth / Math.max(this.pcrFontSpaceWidth, minFontSpaceWidth)));
				if (DEBUG_RENDER_PAGE_CONTENT) {
					System.out.println(" --> font " + this.pcrFontKey + " sized " + this.pcrFontSize);
					System.out.println("   - font is " + this.pcrFont.data);
					System.out.println("   - font descriptor " + this.pcrFont.descriptor);
					System.out.println("   - font name " + this.pcrFont.name);
					System.out.println("   - bold: " + this.pcrFont.bold + ", italic: " + this.pcrFont.italics);
					System.out.println("   - space width is " + this.pcrFontSpaceWidth);
					System.out.println("   - narrow space tolerance factor is " + this.narrowSpaceToleranceFactor);
					System.out.println("   - implicit space: " + this.pcrFont.hasImplicitSpaces);
				}
				this.computeEffectiveFontSizeAndDirection();
			}
		}
		
		private void doTfs(LinkedList stack) {
			this.pcrFontSize = ((Number) stack.removeLast()).floatValue();
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(" --> font size " + this.pcrFontSize);
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
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts) {
				System.out.println(" --> text matrix set to");
				printMatrix(this.lineMatrix);
			}
			this.computeEffectiveFontSizeAndDirection();
		}
		
		//	compute effective font size as round(font size * ((a + d) / 2)) in text matrix
		private void computeEffectiveFontSizeAndDirection() {
			float[] pwrFontSize = {0, this.pcrFontSize, 0};
			pwrFontSize = this.applyTransformationMatrices(transform(pwrFontSize, this.textMatrix));
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(" ==> font analysis vector is " + Arrays.toString(pwrFontSize));
			this.eFontSize = ((float) Math.sqrt((pwrFontSize[0] * pwrFontSize[0]) + (pwrFontSize[1] * pwrFontSize[1])));
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(" ==> effective font size is " + this.eFontSize);
			if (Math.abs(pwrFontSize[1]) > Math.abs(pwrFontSize[0])) {
				this.eFontDirection = PWord.LEFT_RIGHT_FONT_DIRECTION;
				if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
					System.out.println(" ==> effective font direction is left-right");
			}
			else if (pwrFontSize[0] < 0) {
				this.eFontDirection = PWord.BOTTOM_UP_FONT_DIRECTION;
				if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
					System.out.println(" ==> effective font direction is bottom-up");
			}
			else {
				this.eFontDirection = PWord.TOP_DOWN_FONT_DIRECTION;
				if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
					System.out.println(" ==> effective font direction is top-down");
			}
		}
		
		private void doTr(LinkedList stack) {
			Object r = stack.removeLast();
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(" --> text rendering mode " + r);
		}
		
		private void doTs(LinkedList stack) {
			this.pcrTextRise = ((Number) stack.removeLast()).floatValue();
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(" --> text rise " + this.pcrTextRise);
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
			stack.addLast(new Float(-this.pcrLineHeight));
			this.doNewLine(stack, false);
		}
		
		private void doNewLine(LinkedList stack, boolean setLineHeight) {
			Object ty = stack.removeLast();
			if (setLineHeight)
				this.pcrLineHeight = -((Number) ty).floatValue();
			Object tx = stack.removeLast();
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println("Moving to new line");
			updateMatrix(this.textMatrix, ((Number) tx).floatValue(), ((Number) ty).floatValue());
			cloneValues(this.textMatrix, this.lineMatrix);
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts) {
				System.out.println(" --> new line, offset by " + tx + "/" + ty + (setLineHeight ? (", and new leading (line height " + this.pcrLineHeight + ")") : ""));
				printMatrix(this.lineMatrix);
			}
		}
		
		void evaluateTag(String tag, LinkedList stack, ProgressMonitor pm) throws IOException {
			if (false) {}
			
			//	keep track of text color (e.g. to identify water marks that come as light gray text)
			else if ("CS".equals(tag))
				this.doCS(stack);
			else if ("cs".equals(tag))
				this.docs(stack);
			else if ("SC".equals(tag) || "SCN".equals(tag))
				this.doSCN(stack);
			else if ("sc".equals(tag) || "scn".equals(tag))
				this.doscn(stack);
			else if ("G".equals(tag))
				this.doG(stack);
			else if ("g".equals(tag))
				this.dog(stack);
			else if ("RG".equals(tag))
				this.doRG(stack);
			else if ("rg".equals(tag))
				this.dorg(stack);
			else if ("K".equals(tag))
				this.doK(stack);
			else if ("k".equals(tag))
				this.dok(stack);
			
			//	keep track of text state parameters
			else if ("Tc".equals(tag))
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
			
			//	perform line breaks
			else if ("Td".equals(tag))
				this.doTd(stack);
			else if ("TD".equals(tag))
				this.doTD(stack);
			else if ("T*".equals(tag))
				this.doTasterisc(stack);
			
			//	draw text
			else if ("Tj".equals(tag))
				this.doTj(stack);
			else if ("TJ".equals(tag))
				this.doTJ(stack);
			else if ("'".equals(tag))
				this.dohighcomma(stack);
			else if ("\"".equals(tag))
				this.dodoublequote(stack);
			
			//	other text related operations
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
			
			//	keep track of general rendering parameters
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
			
			//	path construction
			else if ("m".equals(tag))
				this.dom(stack);
			else if ("l".equals(tag))
				this.dol(stack);
			else if ("c".equals(tag))
				this.doc(stack);
			else if ("v".equals(tag))
				this.dov(stack);
			else if ("y".equals(tag))
				this.doy(stack);
			else if ("h".equals(tag))
				this.doh(stack);
			else if ("re".equals(tag))
				this.dore(stack);
			
			//	path rendering and filling
			else if ("S".equals(tag))
				this.doS(stack);
			else if ("s".equals(tag))
				this.dos(stack);
			else if ("F".equals(tag))
				this.doF(stack);
			else if ("f".equals(tag))
				this.dof(stack);
			else if ("f*".equals(tag))
				this.dofasterisc(stack);
			else if ("B".equals(tag))
				this.doB(stack);
			else if ("B*".equals(tag))
				this.doBasterisc(stack);
			else if ("b".equals(tag))
				this.dob(stack);
			else if ("b*".equals(tag))
				this.dobasterisc(stack);
			else if ("B".equals(tag))
				this.doB(stack);
			else if ("n".equals(tag))
				this.don(stack);
			
			//	handle graphics state
			else if ("q".equals(tag))
				this.doq(stack);
			else if ("Q".equals(tag))
				this.doQ(stack);
			else if ("gs".equals(tag))
				this.dogs(stack);
			
			//	draw object (usually embedded images)
			else if ("Do".equals(tag))
				this.doDo(stack, pm);
			
			else if (DEBUG_RENDER_PAGE_CONTENT) System.out.println(" ==> UNKNOWN");
		}
		
		private void doDo(LinkedList stack, ProgressMonitor pm) {
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println("Drawing object at RON " + this.nextRenderOrderNumber);
			
			String name = stack.removeLast().toString();
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(" ==> object name is " + name);
			
			Object objObj = dereference(this.xObject.get(name), this.objects);
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(" ==> object is " + objObj);
			if (!(objObj instanceof PStream))
				return;
			
			PStream obj = ((PStream) objObj);
			Object objTypeObj = obj.params.get("Subtype");
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(" ==> object type is " + objTypeObj);
			if (objTypeObj == null)
				return;
			
			if ("Image".equals(objTypeObj.toString()))
				this.doDoFigure(name, objObj);
			else if ("Form".equals(objTypeObj.toString())) try {
				this.doDoForm(obj, pm);
				if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
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
			ArrayList formPaths = ((this.paths == null) ? null : new ArrayList());
			this.nextRenderOrderNumber = runPageContent(this.nextRenderOrderNumber, form.params, formContent, formResources, this.objects, formWords, null, this.fontCharSet, formFigures, formPaths, this.assessFonts, pm);
			
			//	check composite alpha and blend mode of current graphics state
			int sCompositeAlpha = 255;
			int nsCompositeAlpha = 255;
			BlendComposite bmComposite = null;
			if (this.graphicsState != null) {
				Number sCa = ((Number) this.graphicsState.get("CA"));
				if (sCa != null)
					sCompositeAlpha = Math.round(sCa.floatValue() * 255);
				Number nsCa = ((Number) this.graphicsState.get("ca"));
				if (nsCa != null)
					nsCompositeAlpha = Math.round(nsCa.floatValue() * 255);
				Object bm = this.graphicsState.get("BM");
				if ((bm != null) && !"Normal".equals(bm.toString()))
					bmComposite = BlendComposite.getInstance(bm.toString());
			}
			
			//	store form content, adjusting colors based on composite alpha values
			if (formWords != null)
				for (int w = 0; w < formWords.size(); w++) {
					PWord fw = ((PWord) formWords.get(w));
					if (fw.color != null)
						fw = new PWord(fw.renderOrderNumber, fw.charCodes, fw.str, fw.bounds, this.getCompositeAlphaColor(fw.color, nsCompositeAlpha), fw.fontSize, fw.fontDirection, fw.font);
					this.words.add(fw);
				}
			if (formFigures != null) // TODO consider storing composite alpha with figures (if it turns out necessary)
				this.figures.addAll(formFigures);
			if (formPaths != null)
				for (int p = 0; p < formPaths.size(); p++) {
					PPath fp = ((PPath) formPaths.get(p));
					if (fp.strokeColor != null) {
						fp.strokeColor = this.getCompositeAlphaColor(fp.strokeColor, sCompositeAlpha);
						if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
							System.out.println("PPath: stroke color set to " + fp.strokeColor + ((fp.strokeColor == null) ? "" : (", alpha " + fp.strokeColor.getAlpha())));
					}
					if (fp.fillColor != null) {
						fp.fillColor = this.getCompositeAlphaColor(fp.fillColor, nsCompositeAlpha);
						if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
							System.out.println("PPath: fill color set to " + fp.fillColor + ((fp.fillColor == null) ? "" : (", alpha " + fp.fillColor.getAlpha())));
					}
					fp.blendMode = bmComposite;
					this.paths.add(fp);
				}
		}
		
		private void doDoFigure(String name, Object refOrData) {
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
			
			Rectangle2D.Float bounds;
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
				this.figures.add(new PFigure(this.nextRenderOrderNumber++, name, bounds, refOrData, rotation, rightSideLeft, upsideDown));
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
			CharSequence cs = ((CharSequence) stack.removeLast());
			if (this.pcrFont != null) {
				
				//	start line, computing top and bottom
				this.startLine();
				
				//	render char sequence
				String rStr = this.renderCharSequence(cs, 0.0f, 0.0f);
				
				//	finish line, remembering any last word
				this.endLine();
				
				if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
					System.out.println(" --> show string: '" + rStr + "'" + ((rStr.length() == 1) ? (" (" + ((int) rStr.charAt(0)) + ")") : ""));
			}
			else if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts) {
				System.out.println(" --> show string (no font): '" + cs + "'" + ((cs.length() == 1) ? (" (" + ((int) cs.charAt(0)) + ")") : ""));
				System.out.println(" --> cs class is " + cs.getClass().getName());
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
						if (!this.assessFonts)
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
			
			/* Measure width of explicit spaces (including adjacent shifts)
			 * when rendering (need to do that locally on every row, as it
			 * varies too much globally due to text justification and tables).
			 * Use that average explicit space width to assess implicit spaces.
			 * TODO assess if this works, or if it incurs too many conflated words
			 */
			float explicitSpaceWidth = this.measureExplicitSpaceWidth(stros);
			
			//	start line, computing top and bottom
			this.startLine();
			
			//	handle array entries
			for (int s = 0; s < stros.size(); s++) {
				Object o = stros.get(s);
				if (o instanceof CharSequence) {
					CharSequence str = ((CharSequence) o);
					if (this.pcrFont == null) {
						if (!this.assessFonts)
							totalRendered.append(str);
					}
					else {
						float prevShift = 0.0f;
						float nextShift = 0.0f;
						if (s == 0)
							prevShift = 0.0f;
						else if (stros.get(s-1) instanceof Number)
							prevShift = (((Number) stros.get(s-1)).floatValue() / 1000);
						else prevShift = 0.0f;
						
						if ((s+1) == stros.size())
							nextShift = 0.0f;
						else if (stros.get(s+1) instanceof Number)
							nextShift = (((Number) stros.get(s+1)).floatValue() / 1000);
						else nextShift = 0.0f;
						
						String rStr = this.renderCharSequence(str, prevShift, nextShift);
						totalRendered.append(rStr);
					}
				}
				else if (o instanceof Number) {
					float d = (((Number) o).floatValue() / 1000); // TODO does factoring in char spacing make sense or cause trouble ???
					float eShift = ((-d * this.pcrFontSize) + this.pcrCharSpacing);
					if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts) {
						System.out.println("Re-positioning by TJ array number " + d + "*" + this.pcrFontSize + " at effective " + this.eFontSize + ", char spacing is " + this.pcrCharSpacing + ", word spacing is " + this.pcrWordSpacing);
						System.out.println(" - effective shift is " + eShift);
						System.out.println(" - minimum implicit space width is " + ((this.pcrFontSpaceWidth * this.narrowSpaceToleranceFactor * this.pcrFontSize) / 1000));
					}
					if ((this.pcrFont != null) && ((eShift * 3) > (explicitSpaceWidth * 2)) && ((-d + this.pcrCharSpacing) > ((this.pcrFontSpaceWidth * this.narrowSpaceToleranceFactor) / 1000))) {
						if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
							System.out.println(" ==> implicit space");
						totalRendered.append(' ');
						this.endWord();
					}
					else if ((s+1) == stros.size())
						this.endWord();
					updateMatrix(this.lineMatrix, (-d * this.pcrFontSize), 0);
					if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
						this.printPosition();
				}
			}
			
			//	finish line, remembering any last word
			this.endLine();
			
			//	print debug info
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(" --> show strings from array" + ((this.pcrFont == null) ? " (nf)" : "") + ": '" + totalRendered + "'");
		}
		
		private float measureExplicitSpaceWidth(Vector stros) {
			if (this.assessFonts || (this.pcrFont == null))
				return 0;
			
			float exSpaceWidthSum = 0.0f;
			int exSpaceCount = 0;
			for (int s = 0; s < stros.size(); s++) {
				Object o = stros.get(s);
				if (o instanceof CharSequence) {
					float prevShift = 0.0f;
					float nextShift = 0.0f;
					if (s == 0)
						prevShift = 0.0f;
					else if (stros.get(s-1) instanceof Number)
						prevShift = (((Number) stros.get(s-1)).floatValue() / 1000);
					else prevShift = 0.0f;
					
					if ((s+1) == stros.size())
						nextShift = 0.0f;
					else if (stros.get(s+1) instanceof Number)
						nextShift = (((Number) stros.get(s+1)).floatValue() / 1000);
					else nextShift = 0.0f;
					
					//	put characters in array to facilitate modification
					CharSequence cs = ((CharSequence) o);
					char[] cscs = new char[cs.length()];
					for (int c = 0; c < cs.length(); c++)
						cscs[c] = cs.charAt(c);
					
					//	render characters
					int chCount = 0;
					for (int c = 0; c < cscs.length; c++) {
						char ch = cscs[c];
						
						//	split up two bytes conflated in one (only if font doesn't know them as one, though)
						if ((255 < ch) && !this.pcrFont.ucMappings.containsKey(new Integer((int) ch))) {
							cscs[c] = ((char) (ch & 255));
							ch = ((char) (ch >> 8)); // move high byte into rendering range
							c--; // come back for low byte in the next round
						}
						
						//	get effective char
						char ech = ((this.pcrFont == null) ? ch : this.pcrFont.getChar((int) ch));
						
						//	count space width is rendering space
						if ((ech <= 32) || (ech == 160)) {
							float exSpaceWidth = ((((this.pcrFont.getCharWidth(ch) * this.pcrFontSize) + (this.pcrWordSpacing * 1000)) * this.pcrHorizontalScaling) / (1000 * 100));
							exSpaceWidthSum += (((chCount == 0) ? -prevShift : 0) + exSpaceWidth + (((chCount + 1) >= cscs.length) ? -nextShift : 0));
							exSpaceCount++;
						}
						
						//	remember rendering char (we need to keep this explicitly as splitting up chars above can render char sequence length inaccurate)
						chCount++;
					}
				}
			}
			
			//	compute average width of explicit spaces
			if (exSpaceCount == 0) {
				if (DEBUG_RENDER_PAGE_CONTENT)
					System.out.println("Could not compute width of explicit spaces");
				return 0;
			}
			else {
				float exSpaceWidth = (exSpaceWidthSum / exSpaceCount);
				if (DEBUG_RENDER_PAGE_CONTENT)
					System.out.println("Effective width of explicit spaces measured as " + exSpaceWidth + " from " + exSpaceCount + " spaces");
				return exSpaceWidth;
			}
		}
		
		private StringBuffer wordCharCodes = null;
		private StringBuffer wordString = null;
		private float[] start;
		private float[] top;
		private float[] bottom;
		
		private String renderCharSequence(CharSequence cs, float prevShift, float nextShift) {
			StringBuffer rendered = new StringBuffer();
			
			//	put characters in array to facilitate modification
			char[] cscs = new char[cs.length()];
			for (int c = 0; c < cs.length(); c++)
				cscs[c] = cs.charAt(c);
			
			//	render characters
			for (int c = 0; c < cscs.length; c++) {
				char ch = cscs[c];
				
				//	split up two bytes conflated in one (only if font doesn't know them as one, though)
				if ((255 < ch) && !this.pcrFont.ucMappings.containsKey(new Integer((int) ch))) {
					cscs[c] = ((char) (ch & 255));
					ch = ((char) (ch >> 8)); // move high byte into rendering range
					if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
						System.out.println("Handling high byte char '" + ch + "' (" + ((int) ch) + ", " + Integer.toString(((int) ch), 16).toUpperCase() + ") for now, coming back for low byte char '" + cscs[c] + "' (" + ((int) cscs[c]) + ", " + Integer.toString(((int) cscs[c]), 16).toUpperCase() + ") next round");
					c--; // come back for low byte in the next round
				}
				char fCh = this.pcrFont.getChar((int) ch); // the character the font says we're rendering
				boolean fIsSpace = (fCh == ' '); // is the font indicated character a space (32, 0x20)?
				String rCh; // the character actually represented by the glyph we're rendering
				boolean rIsSpace; // is the actually rendered character as space (<= 32, 0x20)?
				boolean isImplicitSpace = (this.pcrFont.hasImplicitSpaces || (((this.pcrCharSpacing + (fIsSpace ? this.pcrWordSpacing : 0)) > ((this.pcrFontSpaceWidth * this.narrowSpaceToleranceFactor) / 1000)))); // do TJ shifts, char or word spacing, or excessive glyph width imply rendering a space?
				
				/* When deciding if we're rendering a space, check what current
				 * font _says_ the char is, not what we've decoded it to ...
				 * it's char 32 that word spacing applies to, no matter what
				 * character the glyph assigned to it actually represents */
				
				//	we're only collecting chars
				if (this.assessFonts) {
					rCh = ("(" + ((int) ch) + ")");
					rIsSpace = (rCh.trim().length() == 0);
				}
				
				//	we're actually extracting words
				else {
					rCh = this.pcrFont.getUnicode((int) ch);
					rIsSpace = (rCh.trim().length() == 0);
				}
				
				//	rendering whitespace char, remember end of previous word (if any)
				if (rIsSpace) {
					//	compute effective space width and effective last shift (only at start and end of char sequence, though)
					//	copy from width computation resulting from drawChar()
					float spaceWidth = ((((this.pcrFont.getCharWidth(ch) * this.pcrFontSize) + ((this.pcrCharSpacing + (fIsSpace ? this.pcrWordSpacing : 0)) * 1000)) * this.pcrHorizontalScaling) / (1000 * 100));
					//	copy from width computation resulting from array shift
					float prevShiftWidth = ((c == 0) ? (prevShift * this.pcrFontSize) : 0.0f);
					float nextShiftWidth = (((c+1) == cscs.length) ? (nextShift * this.pcrFontSize) : 0.0f);
					
					//	end word if space not compensated (to at least 95%) by previous left (positive) shift and/or subsequent (positive) left shift
					if ((spaceWidth * 19) > ((prevShiftWidth + nextShiftWidth) * 20)) {
						if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
							System.out.println(" ==> space accepted");
						this.endWord();
						rendered.append(' ');
					}
					else System.out.println(" ==> space rejected as compensated by shifts");
				}
				
				//	rendering non-whitespace char, remember start of word
				else {
					this.startWord();
					String nrCh = dissolveLigature(rCh);
					if ((this.wordCharCodes != null) && (this.wordString != null)) {
						this.wordCharCodes.append((char) ((nrCh.length() * 256) | ((int) ch)));
						this.wordString.append(nrCh);
					}
					rendered.append(nrCh);
				}
				
				//	update state (move caret forward)
				this.drawChar(ch, fIsSpace, isImplicitSpace, rCh);
				
				//	remember char usage (only now, after word is started)
				if (this.assessFonts)
					this.pcrFont.setCharUsed((int) ch);
				
				//	extreme char or word spacing, add space
				if (isImplicitSpace) {
					if (!rIsSpace)
						rendered.append(' ');
					this.endWord();
					//	APPEARS TO WORK JUST FINE
					float spaceWidth = (((((this.pcrCharSpacing + (fIsSpace ? this.pcrWordSpacing : 0)) * 1000)) * this.pcrHorizontalScaling) / (1000 * 100));
					//	MORE LOGICAL, BUT ABOVE SEEMS TO WORK JUST FINE
//					float spaceWidth = (((((fIsSpace ? this.pcrWordSpacing : this.pcrCharSpacing) * 1000)) * this.pcrHorizontalScaling) / (1000 * 100));
					if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts) {
						System.out.println("Drawing implicit space of width " + spaceWidth);
						System.out.println("  (" + this.pcrCharSpacing + " + (" + fIsSpace + " ? " + this.pcrWordSpacing + " : " + 0 + ")) > ((" + this.pcrFontSpaceWidth + " * " + this.narrowSpaceToleranceFactor + ") / " + 1000 + ")");
						System.out.println("  (" + this.pcrCharSpacing + " + " + (fIsSpace ? this.pcrWordSpacing : 0) + ") > (" + (this.pcrFontSpaceWidth * this.narrowSpaceToleranceFactor) + " / " + 1000 + ")");
						System.out.println("  " + (this.pcrCharSpacing + (fIsSpace ? this.pcrWordSpacing : 0)) + " > " + ((this.pcrFontSpaceWidth * this.narrowSpaceToleranceFactor) / 1000) + "");
					}
					updateMatrix(this.lineMatrix, spaceWidth, 0);
					if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
						this.printPosition();
				}
			}
			
			//	finally ...
			return rendered.toString();
		}
		
		private void drawChar(char ch, boolean fontIndicatesSpace, boolean implicitSpaceComing, String rCh) {
			float spacing = (implicitSpaceComing ? 0 : (this.pcrCharSpacing + (fontIndicatesSpace ? this.pcrWordSpacing : 0)));
			float width = (((((this.pcrFont.hasImplicitSpaces ? this.pcrFont.getMeasuredCharWidth(ch) : this.pcrFont.getCharWidth(ch)) * this.pcrFontSize) + (spacing * 1000)) * this.pcrHorizontalScaling) / (1000 * 100));
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println("Drawing char '" + ch + "' (" + ((int) ch) + ", " + Integer.toString(((int) ch), 16).toUpperCase() + ") ==> '" + rCh + "' (" + ((int) rCh.charAt(0)) + ", '" + dissolveLigature(rCh) + "') with width " + width);
			updateMatrix(this.lineMatrix, width, 0);
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				this.printPosition();
		}
		
		private void printPosition() {
//			float[] start = transform(0, 0, 1, this.lineMatrix);
//			start = this.applyTransformationMatrices(start);
//			System.out.println("NOW AT " + Arrays.toString(start));
		}
		
		private void startLine() {
			if ((this.top != null) && (this.bottom != null))
				return;
			if (this.pcrFont == null)
				return;
			this.top = transform(0, (Math.min(this.pcrFont.capHeight, this.pcrFont.ascent) * this.pcrFontSize), 1, this.lineMatrix);
			this.top = this.applyTransformationMatrices(this.top);
			this.bottom = transform(0, (this.pcrFont.descent * this.pcrFontSize), 1, this.lineMatrix);
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
			if (this.assessFonts)
				this.pcrFont.startWord();
			this.wordCharCodes = new StringBuffer();
			this.wordString = new StringBuffer();
			this.start = transform(0, 0, 1, this.lineMatrix);
			this.start = this.applyTransformationMatrices(this.start);
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(" start set to " + Arrays.toString(this.start));
		}
		
		private void endWord() {
			if (this.start == null)
				return;
			if (this.assessFonts)
				this.pcrFont.endWord();
			float[] end = transform(0, 0, 1, this.lineMatrix);
			end = this.applyTransformationMatrices(end);
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts) {
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
			Rectangle2D.Float pwb = null;
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
//			if ((pwb != null) && !this.assessFont)
//				this.words.add(new PWord(this.wordCharCodes.toString(), this.wordString.toString(), pwb, Math.round(this.eFontSize), this.eFontDirection, this.pwrFont));
			if (pwb != null) {
				if (this.assessFonts)
					this.words.add(new PWord(this.nextRenderOrderNumber++, this.wordCharCodes.toString(), pwb, this.pcrNonStrokeColor, Math.round(this.eFontSize), this.eFontDirection, this.pcrFont));
				else this.words.add(new PWord(this.nextRenderOrderNumber++, this.wordCharCodes.toString(), this.wordString.toString(), pwb, this.pcrNonStrokeColor, Math.round(this.eFontSize), this.eFontDirection, this.pcrFont));
			}
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
	
	//	courtesy http://www.curious-creature.com/2006/09/20/new-blendings-modes-for-java2d/
	static class BlendComposite implements Composite {
		static final int blendModeNormal = 0x0;
		
		static final int blendModeMultiply = 0x1;
		static final int blendModeScreen = 0x2;
		static final int blendModeOverlay = 0x4;
		
		static final int blendModeDarken = 0x8;
		static final int blendModeLighten = 0x10;
		static final int blendModeColorDodge = 0x20;
		static final int blendModeColorBurn = 0x40;
		
		static final int blendModeHardLight = 0x80;
		static final int blendModeSoftLight = 0x100;
		
		static final int blendModeDifference = 0x200;
		static final int blendModeExclusion = 0x400;
		
		static final int blendModeHue = 0x800;
		static final int blendModeSaturation = 0x1000;
		static final int blendModeColor = 0x2000;
		static final int blendModeLuminosity = 0x4000;
		
		private static final Map namesToModes = new TreeMap(String.CASE_INSENSITIVE_ORDER);
		static {
			namesToModes.put("Normal", new Integer(blendModeNormal));
			
			namesToModes.put("Multiply", new Integer(blendModeMultiply));
			namesToModes.put("Screen", new Integer(blendModeScreen));
			namesToModes.put("Overlay", new Integer(blendModeOverlay));
			
			namesToModes.put("Darken", new Integer(blendModeDarken));
			namesToModes.put("Lighten", new Integer(blendModeLighten));
			namesToModes.put("ColorDodge", new Integer(blendModeColorDodge));
			namesToModes.put("ColorBurn", new Integer(blendModeColorBurn));
			
			namesToModes.put("HardLight", new Integer(blendModeHardLight));
			namesToModes.put("SoftLight", new Integer(blendModeSoftLight));
			
			namesToModes.put("Difference", new Integer(blendModeDifference));
			namesToModes.put("Exclusion", new Integer(blendModeExclusion));
			
			namesToModes.put("Hue", new Integer(blendModeHue));
			namesToModes.put("Saturation", new Integer(blendModeSaturation));
			namesToModes.put("Color", new Integer(blendModeColor));
			namesToModes.put("Luminosity", new Integer(blendModeLuminosity));
		}
		
		static BlendComposite getInstance(String name) {
			Integer mode = ((Integer) namesToModes.get(name));
			return ((mode == null) ? null : getInstance(mode.intValue()));
		}
		
		static BlendComposite getInstance(String name, float alpha) {
			Integer mode = ((Integer) namesToModes.get(name));
			return ((mode == null) ? null : getInstance(mode.intValue(), alpha));
		}
		
		static BlendComposite getInstance(int mode) {
			return new BlendComposite(mode);
		}
		
		static BlendComposite getInstance(int mode, float alpha) {
			return new BlendComposite(mode, alpha);
		}
		
		private float alpha;
		private int mode;
		
		private BlendComposite(int mode) {
			this(mode, 1.0f);
		}
		
		private BlendComposite(int mode, float alpha) {
			this.mode = mode;
			setAlpha(alpha);
		}
		
		boolean lightIsTranslucent() {
			return ((this.mode == blendModeMultiply) || (this.mode == blendModeDarken) || (this.mode == blendModeColorBurn));
		}
		
		boolean darkIsTranslucent() {
			return ((this.mode == blendModeScreen) || (this.mode == blendModeLighten) || (this.mode == blendModeColorDodge) || (this.mode == blendModeDifference) || (this.mode == blendModeExclusion));
		}
		
		private void setAlpha(float alpha) {
			this.alpha = alpha;
		}
		
		public int hashCode() {
			return Float.floatToIntBits(this.alpha) * 31 + this.mode;
		}
		
		public boolean equals(Object obj) {
			if (obj instanceof BlendComposite) {
				BlendComposite bc = (BlendComposite) obj;
				return ((this.mode == bc.mode) && (this.alpha == bc.alpha));
			}
			else return false;
		}
		
		public CompositeContext createContext(ColorModel srcColorModel, ColorModel dstColorModel, RenderingHints hints) {
			return new BlendingContext(this);
		}
		
		private static final class BlendingContext implements CompositeContext {
			private final Blender blender;
			private final BlendComposite composite;
			private BlendingContext(BlendComposite composite) {
				this.composite = composite;
				this.blender = Blender.getBlenderFor(composite.mode);
			}
			public void dispose() {}
			public void compose(Raster src, Raster dstIn, WritableRaster dstOut) {
				if (src.getSampleModel().getDataType() != DataBuffer.TYPE_INT ||
					dstIn.getSampleModel().getDataType() != DataBuffer.TYPE_INT ||
					dstOut.getSampleModel().getDataType() != DataBuffer.TYPE_INT) {
					throw new IllegalStateException(
							"Source and destination must store pixels as INT.");
				}
				
				int width = Math.min(src.getWidth(), dstIn.getWidth());
				int height = Math.min(src.getHeight(), dstIn.getHeight());
				float alpha = this.composite.alpha;
				
				int[] srcPixel = new int[4];
				int[] dstPixel = new int[4];
				int[] srcPixels = new int[width];
				int[] dstPixels = new int[width];
				
				for (int y = 0; y < height; y++) {
					src.getDataElements(0, y, width, 1, srcPixels);
					dstIn.getDataElements(0, y, width, 1, dstPixels);
					for (int x = 0; x < width; x++) {
						// pixels are stored as INT_ARGB
						// our arrays are [R, G, B, A]
						int pixel = srcPixels[x];
						srcPixel[0] = (pixel >> 16) & 0xFF;
						srcPixel[1] = (pixel >>  8) & 0xFF;
						srcPixel[2] = (pixel	  ) & 0xFF;
						srcPixel[3] = (pixel >> 24) & 0xFF;

						pixel = dstPixels[x];
						dstPixel[0] = (pixel >> 16) & 0xFF;
						dstPixel[1] = (pixel >>  8) & 0xFF;
						dstPixel[2] = (pixel	  ) & 0xFF;
						dstPixel[3] = (pixel >> 24) & 0xFF;

						int[] result = this.blender.blend(srcPixel, dstPixel);

						// mixes the result with the opacity
						dstPixels[x] = ((int) (dstPixel[3] + (result[3] - dstPixel[3]) * alpha) & 0xFF) << 24 |
									   ((int) (dstPixel[0] + (result[0] - dstPixel[0]) * alpha) & 0xFF) << 16 |
									   ((int) (dstPixel[1] + (result[1] - dstPixel[1]) * alpha) & 0xFF) <<  8 |
										(int) (dstPixel[2] + (result[2] - dstPixel[2]) * alpha) & 0xFF;
					}
					dstOut.setDataElements(0, y, width, 1, dstPixels);
				}
			}
		}
		
		private static abstract class Blender {
			abstract int[] blend(int[] src, int[] dst);
			private static void RGBtoHSL(int r, int g, int b, float[] hsl) {
				float var_R = (r / 255f);
				float var_G = (g / 255f);
				float var_B = (b / 255f);
				
				float var_Min;
				float var_Max;
				float del_Max;
				
				if (var_R > var_G) {
					var_Min = var_G;
					var_Max = var_R;
				}
				else {
					var_Min = var_R;
					var_Max = var_G;
				}
				if (var_B > var_Max)
					var_Max = var_B;
				if (var_B < var_Min)
					var_Min = var_B;
				
				del_Max = var_Max - var_Min;
				
				float H, S, L;
				L = (var_Max + var_Min) / 2f;
				
				if (del_Max - 0.01f <= 0.0f) {
					H = 0;
					S = 0;
				}
				else {
					if (L < 0.5f)
						S = del_Max / (var_Max + var_Min);
					else S = del_Max / (2 - var_Max - var_Min);
					
					float del_R = (((var_Max - var_R) / 6f) + (del_Max / 2f)) / del_Max;
					float del_G = (((var_Max - var_G) / 6f) + (del_Max / 2f)) / del_Max;
					float del_B = (((var_Max - var_B) / 6f) + (del_Max / 2f)) / del_Max;
					
					if (var_R == var_Max)
						H = del_B - del_G;
					else if (var_G == var_Max)
						H = (1 / 3f) + del_R - del_B;
					else H = (2 / 3f) + del_G - del_R;
					if (H < 0)
						H += 1;
					if (H > 1)
						H -= 1;
				}
				
				hsl[0] = H;
				hsl[1] = S;
				hsl[2] = L;
			}
			
			private static void HSLtoRGB(float h, float s, float l, int[] rgb) {
				int R, G, B;
				
				if (s - 0.01f <= 0.0f) {
					R = (int) (l * 255.0f);
					G = (int) (l * 255.0f);
					B = (int) (l * 255.0f);
				}
				else {
					float var_1, var_2;
					if (l < 0.5f)
						var_2 = l * (1 + s);
					else var_2 = (l + s) - (s * l);
					var_1 = 2 * l - var_2;
					
					R = (int) (255.0f * hue2RGB(var_1, var_2, h + (1.0f / 3.0f)));
					G = (int) (255.0f * hue2RGB(var_1, var_2, h));
					B = (int) (255.0f * hue2RGB(var_1, var_2, h - (1.0f / 3.0f)));
				}
				
				rgb[0] = R;
				rgb[1] = G;
				rgb[2] = B;
			}

			private static float hue2RGB(float v1, float v2, float vH) {
				if (vH < 0.0f)
					vH += 1.0f;
				if (vH > 1.0f)
					vH -= 1.0f;
				if ((6.0f * vH) < 1.0f)
					return (v1 + (v2 - v1) * 6.0f * vH);
				if ((2.0f * vH) < 1.0f)
					return (v2);
				if ((3.0f * vH) < 2.0f)
					return (v1 + (v2 - v1) * ((2.0f / 3.0f) - vH) * 6.0f);
				return (v1);
			}
			static Blender getBlenderFor(int composite) {
				switch (composite) {
					case blendModeNormal:
						return new Blender() {
							public int[] blend(int[] src, int[] dst) {
								return src;
							}
						};
						
					case blendModeMultiply:
						return new Blender() {
							public int[] blend(int[] src, int[] dst) {
								return new int[] {
									(src[0] * dst[0]) >> 8,
									(src[1] * dst[1]) >> 8,
									(src[2] * dst[2]) >> 8,
									Math.min(255, src[3] + dst[3])
								};
							}
						};
					case blendModeScreen:
						return new Blender() {
							public int[] blend(int[] src, int[] dst) {
								return new int[] {
									255 - ((255 - src[0]) * (255 - dst[0]) >> 8),
									255 - ((255 - src[1]) * (255 - dst[1]) >> 8),
									255 - ((255 - src[2]) * (255 - dst[2]) >> 8),
									Math.min(255, src[3] + dst[3])
								};
							}
						};
					case blendModeOverlay:
						return new Blender() {
							public int[] blend(int[] src, int[] dst) {
								return new int[] {
									dst[0] < 128 ? dst[0] * src[0] >> 7 :
										255 - ((255 - dst[0]) * (255 - src[0]) >> 7),
									dst[1] < 128 ? dst[1] * src[1] >> 7 :
										255 - ((255 - dst[1]) * (255 - src[1]) >> 7),
									dst[2] < 128 ? dst[2] * src[2] >> 7 :
										255 - ((255 - dst[2]) * (255 - src[2]) >> 7),
									Math.min(255, src[3] + dst[3])
								};
							}
						};
						
					case blendModeLighten:
						return new Blender() {
							public int[] blend(int[] src, int[] dst) {
								return new int[] {
									Math.max(src[0], dst[0]),
									Math.max(src[1], dst[1]),
									Math.max(src[2], dst[2]),
									Math.min(255, src[3] + dst[3])
								};
							}
						};
					case blendModeDarken:
						return new Blender() {
							public int[] blend(int[] src, int[] dst) {
								return new int[] {
									Math.min(src[0], dst[0]),
									Math.min(src[1], dst[1]),
									Math.min(src[2], dst[2]),
									Math.min(255, src[3] + dst[3])
								};
							}
						};
					case blendModeColorDodge:
						return new Blender() {
							public int[] blend(int[] src, int[] dst) {
								return new int[] {
									src[0] == 255 ? 255 :
										Math.min((dst[0] << 8) / (255 - src[0]), 255),
									src[1] == 255 ? 255 :
										Math.min((dst[1] << 8) / (255 - src[1]), 255),
									src[2] == 255 ? 255 :
										Math.min((dst[2] << 8) / (255 - src[2]), 255),
									Math.min(255, src[3] + dst[3])
								};
							}
						};
					case blendModeColorBurn:
						return new Blender() {
							public int[] blend(int[] src, int[] dst) {
								return new int[] {
									src[0] == 0 ? 0 :
										Math.max(0, 255 - (((255 - dst[0]) << 8) / src[0])),
									src[1] == 0 ? 0 :
										Math.max(0, 255 - (((255 - dst[1]) << 8) / src[1])),
									src[2] == 0 ? 0 :
										Math.max(0, 255 - (((255 - dst[2]) << 8) / src[2])),
									Math.min(255, src[3] + dst[3])
								};
							}
						};
						
					case blendModeSoftLight:
						break;
					case blendModeHardLight:
						return new Blender() {
							public int[] blend(int[] src, int[] dst) {
								return new int[] {
									src[0] < 128 ? dst[0] * src[0] >> 7 :
										255 - ((255 - src[0]) * (255 - dst[0]) >> 7),
									src[1] < 128 ? dst[1] * src[1] >> 7 :
										255 - ((255 - src[1]) * (255 - dst[1]) >> 7),
									src[2] < 128 ? dst[2] * src[2] >> 7 :
										255 - ((255 - src[2]) * (255 - dst[2]) >> 7),
									Math.min(255, src[3] + dst[3])
								};
							}
						};
						
					case blendModeDifference:
						return new Blender() {
							public int[] blend(int[] src, int[] dst) {
								return new int[] {
									Math.abs(dst[0] - src[0]),
									Math.abs(dst[1] - src[1]),
									Math.abs(dst[2] - src[2]),
									Math.min(255, src[3] + dst[3])
								};
							}
						};
					case blendModeExclusion:
						return new Blender() {
							public int[] blend(int[] src, int[] dst) {
								return new int[] {
									dst[0] + src[0] - (dst[0] * src[0] >> 7),
									dst[1] + src[1] - (dst[1] * src[1] >> 7),
									dst[2] + src[2] - (dst[2] * src[2] >> 7),
									Math.min(255, src[3] + dst[3])
								};
							}
						};
						
					case blendModeHue:
						return new Blender() {
							public int[] blend(int[] src, int[] dst) {
								float[] srcHSL = new float[3];
								RGBtoHSL(src[0], src[1], src[2], srcHSL);
								float[] dstHSL = new float[3];
								RGBtoHSL(dst[0], dst[1], dst[2], dstHSL);

								int[] result = new int[4];
								HSLtoRGB(srcHSL[0], dstHSL[1], dstHSL[2], result);
								result[3] = Math.min(255, src[3] + dst[3]);

								return result;
							}
						};
					case blendModeSaturation:
						return new Blender() {
							public int[] blend(int[] src, int[] dst) {
								float[] srcHSL = new float[3];
								RGBtoHSL(src[0], src[1], src[2], srcHSL);
								float[] dstHSL = new float[3];
								RGBtoHSL(dst[0], dst[1], dst[2], dstHSL);

								int[] result = new int[4];
								HSLtoRGB(dstHSL[0], srcHSL[1], dstHSL[2], result);
								result[3] = Math.min(255, src[3] + dst[3]);

								return result;
							}
						};
					case blendModeColor:
						return new Blender() {
							public int[] blend(int[] src, int[] dst) {
								float[] srcHSL = new float[3];
								RGBtoHSL(src[0], src[1], src[2], srcHSL);
								float[] dstHSL = new float[3];
								RGBtoHSL(dst[0], dst[1], dst[2], dstHSL);

								int[] result = new int[4];
								HSLtoRGB(srcHSL[0], srcHSL[1], dstHSL[2], result);
								result[3] = Math.min(255, src[3] + dst[3]);

								return result;
							}
						};
					case blendModeLuminosity:
						return new Blender() {
							public int[] blend(int[] src, int[] dst) {
								float[] srcHSL = new float[3];
								RGBtoHSL(src[0], src[1], src[2], srcHSL);
								float[] dstHSL = new float[3];
								RGBtoHSL(dst[0], dst[1], dst[2], dstHSL);

								int[] result = new int[4];
								HSLtoRGB(dstHSL[0], dstHSL[1], srcHSL[2], result);
								result[3] = Math.min(255, src[3] + dst[3]);

								return result;
							}
						};
				}
				throw new IllegalArgumentException("Blender not implement for " + composite);
			}
		}
	}
}