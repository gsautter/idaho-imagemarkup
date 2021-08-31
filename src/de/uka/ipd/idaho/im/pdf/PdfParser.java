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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.CompositeContext;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import org.icepdf.core.io.BitStream;
import org.icepdf.core.pobjects.Page;
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
//					System.out.println("PDF Object map: " + key + " mapped to " + value.getClass().getName());
				return super.put(key, value);
			}
		};
		
		PdfLineInputStream lis = new PdfLineInputStream(new ByteArrayInputStream(bytes));
		for (byte[] line; (line = lis.readLine()) != null;) {
			if (PdfUtils.matches(line, "[1-9][0-9]*\\s[0-9]+\\sobj.*")) {
				String objId = PdfUtils.toString(line, true).trim();
//				System.out.println("Got object ID: " + objId);
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				if (!objId.endsWith("obj")) {
					objId = objId.substring(0, (objId.indexOf("obj") + "obj".length()));
					baos.write(line, objId.length(), (line.length - objId.length()));
				}
				objId = objId.substring(0, (objId.length() - "obj".length())).trim();
				
				boolean needLineBreakEndObj = false;
				for (byte[] objLine; (objLine = lis.readLine()) != null;) {
					if (PdfUtils.equals(objLine, "endobj"))
						break;
					if (PdfUtils.startsWith(objLine, "endobj ")) {
						needLineBreakEndObj = true;
						break; // need to add line-break after 'endobj' and start over
					}
					if (PdfUtils.indexOf(objLine, " endobj", 0, 32) != -1) {
						needLineBreakEndObj = true;
						break; // need to add line-break before 'endobj' and start over
					}
					if (PdfUtils.endsWith(objLine, "endobj")) {
						baos.write(objLine, 0, (objLine.length - "endobj".length()));
						break;
					}
					else baos.write(objLine);
				}
				
				if (needLineBreakEndObj) {
					System.out.println("PDF File Parser: ensuring line break after 'endobj'");
					int lineBreaksAddedBefore = 0;
					int lineBreaksAddedAfter = 0;
					for (int b = 0; b < bytes.length; b++) {
						if (PdfUtils.startsWith(bytes, " endobj", b)) {
							bytes[b] = ((byte) '\n');
							lineBreaksAddedBefore++;
						}
						else if (PdfUtils.startsWith(bytes, "endobj ", b)) {
							bytes[b + "endobj".length()] = ((byte) '\n');
							b += "endobj".length();
							lineBreaksAddedAfter++;
						}
					}
					System.out.println(" ==> added " + lineBreaksAddedBefore + " line breaks before 'endobj', " + lineBreaksAddedAfter + " after");
					if ((lineBreaksAddedBefore + lineBreaksAddedAfter) != 0)
						return getObjects(bytes, sm);
				}
				
//				System.out.println("Parsing object from " + new String(baos.toByteArray()));
				Object obj = parseObject(new PdfByteInputStream(new ByteArrayInputStream(baos.toByteArray())));
				if (obj instanceof PStream)
					obj = checkStreamLength(((PStream) obj), objects);
				objects.put(objId, obj);
//				System.out.println("Got object: " + obj);
				
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
		System.out.println("Decoding object stream " + objStreamRef + ": " + objStream);
		System.out.println(Arrays.toString(objStream.bytes));
		if ((objStreamRef != null) && (sm != null)) {
			decryptObject(objStreamRef, objStream.params, sm);
			System.out.println(Arrays.toString(objStream.bytes));
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
			if (sObj instanceof PStream)
				sObj = checkStreamLength(((PStream) sObj), objects);
			objects.put(sObjs[o].objId, sObj);
		}
	}
	
	private static PStream checkStreamLength(PStream pStream, Map objects) {
		Object length = getObject(pStream.params, "Length", objects);
		if (length instanceof Number) {
			if (pStream.bytes.length < ((Number) length).intValue()) {
				byte[] padStream = new byte[((Number) length).intValue()];
				System.arraycopy(pStream.bytes, 0, padStream, 0, pStream.bytes.length);
				for (int b = pStream.bytes.length; b < padStream.length; b++)
					padStream[b] = '\n';
				return new PStream(pStream.params, padStream);
			}
			else if (pStream.bytes.length > ((Number) length).intValue()) {
				byte[] cropStream = new byte[((Number) length).intValue()];
				System.arraycopy(pStream.bytes, 0, cropStream, 0, cropStream.length);
				return new PStream(pStream.params, cropStream);
			}
		}
		return pStream;
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
	public static void decode(Object filter, byte[] stream, Map params, ByteArrayOutputStream baos, Map objects) throws IOException {
		
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
		if (filter instanceof List) {
			for (Iterator fit = ((List) filter).iterator(); fit.hasNext();) {
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
	
	private static void decodeLzw(byte[] stream, Map params, ByteArrayOutputStream baos) throws IOException {
		LZWDecode lzwd = new LZWDecode(new BitStream(new ByteArrayInputStream(stream)), new Library(), new HashMap(params));
		byte[] buffer = new byte[1024];
		for (int read; (read = lzwd.read(buffer)) != -1;)
			baos.write(buffer, 0, read);
		lzwd.close();
	}
	
	private static void decodeFlate(byte[] stream, Map params, ByteArrayOutputStream baos) throws IOException {
		FlateDecode fd = new FlateDecode(new Library(), new HashMap(params), new ByteArrayInputStream(stream));
		byte[] buffer = new byte[1024];
		for (int read; (read = fd.read(buffer)) != -1;)
			baos.write(buffer, 0, read);
		fd.close();
	}
	
	private static void decodeAscii85(byte[] stream, Map params, ByteArrayOutputStream baos) throws IOException {
		ASCII85Decode ad = new ASCII85Decode(new ByteArrayInputStream(stream));
		byte[] buffer = new byte[1024];
		for (int read; (read = ad.read(buffer)) != -1;)
			baos.write(buffer, 0, read);
		ad.close();
	}
	
	private static void decodeAsciiHex(byte[] stream, Map params, ByteArrayOutputStream baos) throws IOException {
		ASCIIHexDecode ahd = new ASCIIHexDecode(new ByteArrayInputStream(stream));
		byte[] buffer = new byte[1024];
		for (int read; (read = ahd.read(buffer)) != -1;)
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
	private static void decodeRunLength(byte[] stream, Map params, ByteArrayOutputStream baos) throws IOException {
		RunLengthDecode rld = new RunLengthDecode(new ByteArrayInputStream(stream));
		byte[] buffer = new byte[1024];
		for (int read; (read = rld.read(buffer)) != -1;)
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
//		System.out.println("Dereferencing " + obj);
		while (obj instanceof Reference)
			obj = objects.get(((Reference) obj).getObjectNumber() + " " + ((Reference) obj).getGenerationNumber());
		return obj;
	}
	
	static void dereferenceObjects(Map objects) {
		ArrayList ids = new ArrayList(objects.keySet());
		for (Iterator idit = ids.iterator(); idit.hasNext();) {
			Object id = idit.next();
			Object obj = objects.get(id);
			if (obj instanceof Map)
				dereferenceObjects(((Map) obj), objects);
			else if (obj instanceof List)
				dereferenceObjects(((List) obj), objects);
			else if (obj instanceof PStream)
				dereferenceObjects(((PStream) obj).params, objects);
		}
	}
	
	static void dereferenceObjects(Map dict, Map objects) {
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
				else if (obj instanceof Map)
					continue;
				else if (obj instanceof List)
					continue;
				dict.put(id, obj);
			}
		}
	}
	
	static void dereferenceObjects(List array, Map objects) {
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
				else if (obj instanceof Map)
					continue;
				else if (obj instanceof List)
					continue;
				array.set(i, obj);
			}
		}
	}
	
	private static final boolean DEBUG_DECRYPT_PDF = false;
	
	/**
	 * Decrypt the objects from a PDF document. This method applies default
	 * decryption to streams and strings. Maps and lists are treated
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
		if (obj instanceof PStream) {
			Object type = ((PStream) obj).params.get("Type"); // object streams are decrypted on reading, so we can omit them here
			if ((type == null) || !"ObjStm".equals(type.toString())) {
				decryptObject(objRef, ((PStream) obj).params, sm);
				decryptBytes(objRef, ((PStream) obj).bytes, sm);
			}
		}
		else if (obj instanceof Map) {
			Map dict = ((Map) obj);
			for (Iterator kit = dict.keySet().iterator(); kit.hasNext();) {
				Object key = kit.next();
				decryptObject(objRef, dict.get(key), sm);
			}
		}
		else if (obj instanceof List) {
			List array = ((List) obj);
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
//		else if (obj instanceof Number) {
//			
//		}
//		else if (obj instanceof Boolean) {
//			
//		}
//		else if (obj instanceof Name) {
//			
//		}
//		else if (obj instanceof Reference) {
//			
//		}
//		else throw new RuntimeException("GOTCHA");
	}
	
	private static void decryptBytes(Reference objRef, byte[] bytes, SecurityManager sm) throws IOException {
//		InputStream is = sm.getEncryptionInputStream(objRef, sm.getDecryptionKey(), null, new ByteArrayInputStream(bytes), true);
//		byte[] buffer = new byte[Math.min(1024, bytes.length)];
//		int streamBytePos = 0;
//		for (int r; (r = is.read(buffer, 0, buffer.length)) != -1;) {
//			System.arraycopy(buffer, 0, bytes, streamBytePos, r);
//			streamBytePos += r;
//		}
		byte[] decrypted = sm.decrypt(objRef, sm.getDecryptionKey(), bytes);
		if (decrypted != null)
			System.arraycopy(decrypted, 0, bytes, 0, decrypted.length);
		else if (DEBUG_DECRYPT_PDF) System.out.println("Failed to decrypt " + objRef + ", bytes are " + new String(bytes));
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
	public static Object getObject(Map data, String name, Map objects) {
//		System.out.println("Getting " + name + " from " + data);
//		System.out.println(" ==> " + data.get(name));
		return dereference(data.get(name), objects);
	}
	
	private static Object parseObject(PdfByteInputStream bytes) throws IOException {
		Object obj = cropNext(bytes, false, false);
		
		//	check for subsequent stream
		if (!bytes.skipSpaceCheckEnd())
			return obj;
		
		//	must be parameters ...
		if (!(obj instanceof Map))
			return obj;
		
		//	read stream start TODO use 'Length' parameter if possible
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
		return new PStream(((Map) obj), baos.toByteArray());
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
				bytes.read(); // consume B
				bytes.read(); // consume I
				bytes.skipSpace();
				ByteArrayOutputStream iImageData = new ByteArrayOutputStream();
				while (bytes.peek() != -1) {
					while ((bytes.peek() != -1) && (bytes.peek() != 'E'))
						iImageData.write(bytes.read());
					if (bytes.peek() == 'E') {
						bytes.read(); // consume E
						if (bytes.peek() == 'I') {
							bytes.read(); // consume I
							return new PInlineImage("BI", iImageData.toByteArray());
						}
						else if (bytes.peek() == -1) {
							iImageData.write((int) 'E'); // re-add E
						}
						else {
							iImageData.write((int) 'E'); // re-add E
							iImageData.write(bytes.read());
						}
					}
				}
				return new PInlineImage("BI", iImageData.toByteArray());
			}
			
			//	boolean, number, null, or page content tag
			else {
				if (DEBUG_PARSE_PDF) System.out.println(" ==> other object");
				StringBuffer valueBuffer = new StringBuffer();
				char firstChar = ((char) bytes.peek());
				while ((bytes.peek() > 32) && ("%()<>[]{}/#".indexOf((char) bytes.peek()) == -1)) {
//					valueBuffer.append((char) bytes.read());
					if (valueBuffer.length() == 0) // nothing known thus far
						valueBuffer.append((char) bytes.read());
					else if ((firstChar == 'd') && (valueBuffer.length() == 1)) // have to catch d0 and d1 operators (all others are letters only, at least up to PDF 1.6)
						valueBuffer.append((char) bytes.read());
					else if (Character.isLetter(firstChar) && Character.isDigit((char) bytes.peek())) // digit can only follow on letter in d0 and d1
						break;
					else valueBuffer.append((char) bytes.read());
				}
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
					return new PTag(value);
				else try {
					return new Double(value);
				}
				catch (NumberFormatException nfe) {
					System.out.println("Invalid or unexpected object: " + value);
					return NULL;
				}
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
	
//	private static Name cropName(PdfByteInputStream bytes) throws IOException {
	private static String cropName(PdfByteInputStream bytes) throws IOException {
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
//		return new Name(name.toString());
		return name.toString();
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
	
//	private static Vector cropArray(PdfByteInputStream bytes, boolean expectPTags, boolean hexIsHex2) throws IOException {
	private static List cropArray(PdfByteInputStream bytes, boolean expectPTags, boolean hexIsHex2) throws IOException {
		if (DEBUG_PARSE_PDF) System.out.println("Cropping array");
//		Vector array = new Vector(2);
		List array = new ArrayList(2);
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
	
//	private static Hashtable cropHashtable(PdfByteInputStream bytes, boolean expectPTags, boolean hexIsHex2) throws IOException {
	private static Map cropHashtable(PdfByteInputStream bytes, boolean expectPTags, boolean hexIsHex2) throws IOException {
		if (DEBUG_PARSE_PDF) System.out.println("Cropping dictionary");
//		Hashtable ht = new Hashtable(2);
		Map ht = new HashMap(2);
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
//			Name name = cropName(bytes);
			String name = cropName(bytes);
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
	
//	private static Hashtable cropInlineImageParams(PdfByteInputStream iiBytes) throws IOException {
	private static Map cropInlineImageParams(PdfByteInputStream iiBytes) throws IOException {
		if (DEBUG_PARSE_PDF) System.out.println("Cropping dictionary");
//		Hashtable iip = new Hashtable(2);
		Map iip = new HashMap(2);
		while (true) {
			if (!iiBytes.skipSpaceCheckEnd())
				throw new IOException("Broken inline image dictionary");
			while (iiBytes.peek() == '%') {
				skipComment(iiBytes);
				if (!iiBytes.skipSpaceCheckEnd())
					throw new IOException("Broken inline image dictionary");
			}
			if ((iiBytes.peek(0) == 'I') && (iiBytes.peek(1) == 'D')) {
				iiBytes.read(); // consume I
				iiBytes.read(); // consume D
				if (!"ASCIIHexDecode".equals(iip.get("Filter")) && !"ASCII85Decode".equals(iip.get("Filter")) && (iiBytes.peek() < 33))
					iiBytes.read(); // consume space terminating 'ID' image data start marker (not required on the two checked filters according to spec)
				break;
			}
			if (!iiBytes.skipSpaceCheckEnd())
				throw new IOException("Broken inline image dictionary");
			while (iiBytes.peek() == '%') {
				skipComment(iiBytes);
				if (!iiBytes.skipSpaceCheckEnd())
					throw new IOException("Broken inline image dictionary");
			}
//			Name name = cropName(iiBytes);
			String name = cropName(iiBytes);
			if (!iiBytes.skipSpaceCheckEnd())
				throw new IOException("Broken inline image dictionary");
			while (iiBytes.peek() == '%') {
				skipComment(iiBytes);
				if (!iiBytes.skipSpaceCheckEnd())
					throw new IOException("Broken inline image dictionary");
			}
			Object value = cropNext(iiBytes, false, true);
			iip.put(translateInlineImageObject(name, true), translateInlineImageObject(value, false));
		}
		if (DEBUG_PARSE_PDF) System.out.println(" --> " + iip.toString());
		return iip;
	}
	
	private static Object translateInlineImageObject(Object obj, boolean isKey) {
//		if (obj instanceof Name) {
		if (obj instanceof String) {
			Properties translator = (isKey ? inlineImageParamNameTranslator : inlineImageParamValueTranslator);
//			return new Name(translator.getProperty(obj.toString(), obj.toString()));
			return translator.getProperty(obj.toString(), obj.toString());
		}
//		else if (obj instanceof Vector) {
		else if (obj instanceof List) {
//			Vector array = ((Vector) obj);
			List array = ((List) obj);
			for (int e = 0; e < array.size(); e++)
				array.set(e, translateInlineImageObject(array.get(e), false));
			return array;
		}
//		else if (obj instanceof Hashtable) {
		else if (obj instanceof Map) {
//			Hashtable dict = ((Hashtable) obj);
//			Hashtable tDict = new Hashtable();
			Map dict = ((Map) obj);
			Map tDict = new HashMap();
			for (Iterator kit = dict.keySet().iterator(); kit.hasNext();) {
				Object key = kit.next();
				tDict.put(translateInlineImageObject(key, true), translateInlineImageObject(dict.get(key), false));
			}
			return tDict;
		}
		else return obj;
	}
	
	private static Properties inlineImageParamNameTranslator = new Properties();
	static {
		inlineImageParamNameTranslator.setProperty("BPC","BitsPerComponent");
		inlineImageParamNameTranslator.setProperty("CS","ColorSpace");
		inlineImageParamNameTranslator.setProperty("D","Decode");
		inlineImageParamNameTranslator.setProperty("DP","DecodeParms");
		inlineImageParamNameTranslator.setProperty("F","Filter");
		inlineImageParamNameTranslator.setProperty("H","Height");
		inlineImageParamNameTranslator.setProperty("IM","ImageMask");
		inlineImageParamNameTranslator.setProperty("I","Interpolate");
		inlineImageParamNameTranslator.setProperty("W","Width");
	}
	
	private static Properties inlineImageParamValueTranslator = new Properties();
	static {
		inlineImageParamValueTranslator.setProperty("G","DeviceGray");
		inlineImageParamValueTranslator.setProperty("RGB","DeviceRGB");
		inlineImageParamValueTranslator.setProperty("CMYK","DeviceCMYK");
		inlineImageParamValueTranslator.setProperty("I","Indexed");
		
		inlineImageParamValueTranslator.setProperty("AHx","ASCIIHexDecode");
		inlineImageParamValueTranslator.setProperty("A85","ASCII85Decode");
		inlineImageParamValueTranslator.setProperty("LZW","LZWDecode");
		inlineImageParamValueTranslator.setProperty("Fl","FlateDecode");
		inlineImageParamValueTranslator.setProperty("RL","RunLengthDecode");
		inlineImageParamValueTranslator.setProperty("CCF","CCITTFaxDecode");
		inlineImageParamValueTranslator.setProperty("DCT","DCTDecode");
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
//		public final Hashtable params;
		public final Map params;
		/**
		 * the raw binary data
		 */
		public final byte[] bytes;
//		PStream(Hashtable params, byte[] bytes) {
		PStream(Map params, byte[] bytes) {
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
	
	static class PTag {
		final String tag;
		PTag(String tag) {
			this.tag = tag;
		}
	}
	
	static class PInlineImage extends PTag {
		final byte[] data;
		PInlineImage(String tag, byte[] data) {
			super(tag);
			this.data = data;
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
	 * A graphics transformation matrix.
	 * 
	 * @author sautter
	 */
	public static class PMatrix {
		static final PMatrix identity;
		static {
			float[][] itm = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
			identity = new PMatrix(itm, PRotate.identity);
		}
		final float[][] tm;
		final PRotate rotate;
		PMatrix(float[][] tm, PRotate rotate) {
			this(tm, rotate, true);
		}
		private PMatrix(float[][] tm, PRotate rotate, boolean copy) {
			if (copy) {
				this.tm = new float[3][3];
				for (int c = 0; c < 3; c++)
					System.arraycopy(tm[c], 0, this.tm[c], 0, 3);
			}
			else this.tm = tm;
			this.rotate = rotate;
		}
//		private float[] scaleRotate1 = null;
//		private float[] scaleRotate2 = null;
//		private float scaleX;
//		private float scaleY;
//		private float scaleLin;
//		private float scaleDiag;
		private boolean containsInversion;
		private double rotation1 = Double.NaN;
		private double rotation2 = Double.NaN;
		private double rotation = Double.NaN;
		private boolean plainScaleTranslate;
		private boolean topSideDown;
		private boolean rightSideLeft;
		private int quadrantRotation;
		private void ensureAnalyzed() {
//			if ((this.scaleRotate1 != null) && (this.scaleRotate2 != null))
			if (!Double.isNaN(this.rotation))
				return;
			float[] scaleRotate0 = {1, 0, 0};
			scaleRotate0 = PdfParser.transform(scaleRotate0, this.tm, this.rotate, "");
//			if (DEBUG_RENDER_PAGE_CONTENT && (this.figures != null))
//				System.out.println(" ==> image rotation and scaling 1 " + Arrays.toString(scaleRotate1));
			float[] scaleRotate1 = {0, 1, 0};
			scaleRotate1 = PdfParser.transform(scaleRotate1, this.tm, this.rotate, "");
//			if (DEBUG_RENDER_PAGE_CONTENT && (this.figures != null))
//				System.out.println(" ==> image rotation and scaling 2 " + Arrays.toString(scaleRotate2));
			
//			float scaleX = ((float) Math.sqrt((scaleRotate1[0] * scaleRotate1[0]) + (scaleRotate2[0] * scaleRotate2[0])));
//			float scaleY = ((float) Math.sqrt((scaleRotate1[1] * scaleRotate1[1]) + (scaleRotate2[1] * scaleRotate2[1])));
			
			float scaleLin = ((float) Math.sqrt((scaleRotate0[0] * scaleRotate0[0]) + (scaleRotate1[1] * scaleRotate1[1])));
//			if (DEBUG_RENDER_PAGE_CONTENT && (this.figures != null))
//				System.out.println(" ==> X-X/Y-Y scaling is " + scaleLin);
			float scaleDiag = ((float) Math.sqrt((scaleRotate0[1] * scaleRotate0[1]) + (scaleRotate1[0] * scaleRotate1[0])));
//			if (DEBUG_RENDER_PAGE_CONTENT && (this.figures != null))
//				System.out.println(" ==> X-Y/Y-X scaling is " + scaleDiag);
			
			if ((scaleDiag * 5) < scaleLin) {
				//	either ONE diagonal element inverting
				this.containsInversion = ((scaleRotate0[0] < 0) != (scaleRotate1[1] < 0));
			}
			else if ((scaleLin * 5) < scaleDiag) {
				//	scaling along both or no reverse diagonal elements
				this.containsInversion = (Math.signum(scaleRotate0[1]) == Math.signum(scaleRotate1[0]));
			}
			else this.containsInversion = false;
			
			this.rotation1 = Math.atan2(-scaleRotate0[1], scaleRotate0[0]);
			this.rotation2 = Math.atan2(scaleRotate1[0], scaleRotate1[1]);
//			if ((Math.min(rotation1, rotation2) < (-Math.PI / 2)) && (Math.max(rotation1, rotation2) > (Math.PI / 2)))
//				this.rotation = ((rotation1 + rotation2 + (Math.PI * 2)) / 2);
//			else this.rotation = ((rotation1 + rotation2) / 2);
			if (Math.abs(this.rotation1 - this.rotation2) > Math.PI) // mid point is more around PI than 0
				this.rotation = ((this.rotation1 + this.rotation2 + (Math.PI * 2)) / 2);
			else this.rotation = ((this.rotation1 + this.rotation2) / 2);
			
			if (scaleRotate0[1] != 0)
				this.plainScaleTranslate = false;
			else if (scaleRotate1[0] != 0)
				this.plainScaleTranslate = false;
			else if (scaleRotate0[0] < 0)
				this.plainScaleTranslate = false;
			else if (scaleRotate1[1] < 0)
				this.plainScaleTranslate = false;
			else this.plainScaleTranslate = true;
			
			if (scaleDiag == 0) {
				if ((scaleRotate0[0] < 0) && (scaleRotate0[0] < 0)) {
					this.topSideDown = false;
					this.rightSideLeft = false;
					this.quadrantRotation = 2;
				}
				else if (scaleRotate0[0] < 0) {
					this.topSideDown = false;
					this.rightSideLeft = true;
					this.quadrantRotation = -1;
				}
				else if (scaleRotate1[1] < 0) {
					this.topSideDown = true;
					this.rightSideLeft = false;
					this.quadrantRotation = -1;
				}
				else {
					this.topSideDown = false;
					this.rightSideLeft = false;
					this.quadrantRotation = 0;
				}
			}
			else if (scaleLin == 0) {
				if ((scaleRotate0[1] < 0) && (scaleRotate1[0] < 0))
					this.quadrantRotation = -1;
				else if (scaleRotate0[1] < 0)
					this.quadrantRotation = 1;
				else if (scaleRotate1[0] < 0)
					this.quadrantRotation = 3;
				this.topSideDown = false;
				this.rightSideLeft = false;
			}
			else {
				this.topSideDown = false;
				this.rightSideLeft = false;
				this.quadrantRotation = -1;
			}
		}
		double getRotation() {
			this.ensureAnalyzed();
			return this.rotation;
		}
		double getRotation1() {
			this.ensureAnalyzed();
			return this.rotation1;
		}
		double getRotation2() {
			this.ensureAnalyzed();
			return this.rotation2;
		}
		boolean containsInversion() {
			this.ensureAnalyzed();
			return this.containsInversion;
		}
		boolean isPlainScaleTranslate() {
			this.ensureAnalyzed();
			return this.plainScaleTranslate;
		}
		boolean isTopSideDown() {
			return this.topSideDown;
		}
		boolean isRightSideLeft() {
			return this.rightSideLeft;
		}
		int getQuadrantRotation() {
			return this.quadrantRotation;
		}
		PMatrix quadrantRotate(int quadrants) /* for page content flipping */ {
			float[][] tm = new float[3][3];
			int cos = (((quadrants & 0x01) == 0) ? (((quadrants & 0x02) == 0) ? 1 : -1) : 0);
			int sin = (((quadrants & 0x01) == 0) ? 0 : (((quadrants & 0x02) == 0) ? 1 : -1));
			
			tm[0][0] = (( cos * this.tm[0][0]) + ( sin * this.tm[1][0]));
			tm[1][0] = ((-sin * this.tm[0][0]) + ( cos * this.tm[1][0]));
			
			tm[0][1] = (( cos * this.tm[0][1]) + ( sin * this.tm[1][1]));
			tm[1][1] = ((-sin * this.tm[0][1]) + ( cos * this.tm[1][1]));
			
	        tm[0][2] = this.tm[0][2];
	        tm[1][2] = this.tm[1][2];
	        System.arraycopy(this.tm[2], 0, tm[2], 0, 3); // rightmost column remains
	        
	        return new PMatrix(tm, this.rotate, false);
		}
		AffineTransform getAffineTransform() {
			return new AffineTransform(this.tm[0][0], this.tm[0][1], this.tm[1][0], this.tm[1][1], this.tm[0][2], this.tm[1][2]);
		}
		/*
      [ x']   [  m00  m01  m02  ] [ x ]   [ m00x + m01y + m02 ]
      [ y'] = [  m10  m11  m12  ] [ y ] = [ m10x + m11y + m12 ]
      [ 1 ]   [   0    0    1   ] [ 1 ]   [         1         ]
		 */
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
		public abstract String toString();
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
		public static final int UPSIDE_DOWN_FONT_DIRECTION = -2;
		public final String charCodes;
		public final String str;
		public final float baseline;
		public final Rectangle2D.Float bounds;
		public final Color color;
		public final boolean bold;
		public final boolean italics;
		public final boolean serif;
		public final boolean monospaced;
		public final int fontSize;
		public final int fontDirection;
		public final PdfFont font;
		boolean joinWithNext = false;
		PWord(int renderOrderNumber, String charCodes, float baseline, Rectangle2D.Float bounds, Color color, int fontSize, int fontDirection, PdfFont font) {
			this(renderOrderNumber, charCodes, null, baseline, bounds, color, fontSize, fontDirection, font);
		}
		PWord(int renderOrderNumber, String charCodes, String str, float baseline, Rectangle2D.Float bounds, Color color, int fontSize, int fontDirection, PdfFont font) {
			super(renderOrderNumber);
			this.charCodes = charCodes;
			this.str = str;
			this.baseline = baseline;
			this.bounds = bounds;
			this.color = color;
			this.bold = font.bold;
			this.italics = font.italics;
			this.serif = font.serif;
			this.monospaced = font.monospaced;
			this.fontSize = fontSize;
			this.fontDirection = fontDirection;
			this.font = font;
			if (DEBUG_RENDER_PAGE_CONTENT && (str != null)) {
				System.out.print("Got word '" + this.str + "' at RON " + this.renderOrderNumber + " with char codes '");
				for (int c = 0; c < this.charCodes.length(); c++)
					System.out.print(((c == 0) ? "" : " ") + Integer.toString(((int) this.charCodes.charAt(c)), 16));
				System.out.println("'" + ((this.font == null) ? "" : (" in font " + this.font.name)));
				System.out.println("  color is " + this.color + ((this.color == null) ? "" : (" with alpha " + this.color.getAlpha())));
			}
		}
		public String toString() {
			return ("'" + this.str + "' [" + Math.round(this.bounds.getMinX()) + "," + Math.round(this.bounds.getMaxX()) + "," + Math.round(this.bounds.getMinY()) + "," + Math.round(this.bounds.getMaxY()) + "], sized " + this.fontSize + (this.bold ? ", bold" : "") + (this.italics ? ", italic" : "") + ((this.color == null) ? "" : (", in " + Integer.toString(((this.color.getRGB() >>> 24) & 0xFF), 16).toUpperCase() + "-" + Integer.toString((this.color.getRGB() & 0xFFFFFF), 16).toUpperCase())) + ", at RON " + this.renderOrderNumber);
		}
	}
	
	/**
	 * Wrapper for a visual object (figure or path) and its properties.
	 * 
	 * @author sautter
	 */
	static abstract class PVisualObject extends PObject {
		PPath[] clipPaths;
		Rectangle2D.Float visibleBounds;
		PVisualObject(int renderOrderNumber) {
			super(renderOrderNumber);
		}
		void setClipPaths(PPath[] clipPaths) {
			this.clipPaths = clipPaths;
			this.visibleBounds = convertFloat(getVisibleBounds(this.getBounds(), this.clipPaths));
		}
		abstract Rectangle2D getBounds();
	}
	
	/**
	 * Wrapper for a figure (image embedded in a page) and its properties.
	 * 
	 * @author sautter
	 */
	public static class PFigure extends PVisualObject {
		String name;
		Rectangle2D.Float bounds;
//		double rotation;
//		boolean rightSideLeft;
//		boolean upsideDown;
		PFigure[] subFigures;
		Object refOrData;
//		PFigure(int renderOrderNumber, String name, Rectangle2D.Float bounds, PPath[] clipPaths, Object refOrData, double rotation, boolean rightSideLeft, boolean upsideDown) {
//			super(renderOrderNumber);
//			this.name = name;
//			this.bounds = bounds;
//			this.refOrData = refOrData;
//			this.rotation = rotation;
//			this.rightSideLeft = rightSideLeft;
//			this.upsideDown = upsideDown;
//			this.subFigures = null;
//			this.setClipPaths(clipPaths);
//		}
		PFigure[] softMasks;
		PMatrix transform;
		PFigure(int renderOrderNumber, String name, Rectangle2D.Float bounds, PPath[] clipPaths, PFigure[] softMasks, Object refOrData, PMatrix transform) {
			super(renderOrderNumber);
			this.name = name;
			this.bounds = bounds;
			this.refOrData = refOrData;
			this.transform = transform;
			this.subFigures = null;
			this.setClipPaths(clipPaths);
			this.softMasks = softMasks;
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
				//	TODO observe clipping
			}
			this.name = name.append("]").toString();
			this.bounds = new Rectangle2D.Float(lx, by, (rx - lx), (ty - by));
			this.clipPaths = null;
			this.refOrData = null;
//			this.rotation = 0;
//			this.rightSideLeft = false;
//			this.upsideDown = false;
			this.transform = PMatrix.identity;
			this.softMasks = null;
			this.visibleBounds = convertFloat(pf1.visibleBounds.createUnion(pf2.visibleBounds));
		}
		private static void addFigure(ArrayList sfs, PFigure f) {
			if (f.subFigures == null)
				sfs.add(f);
			else for (int s = 0; s < f.subFigures.length; s++)
				addFigure(sfs, f.subFigures[s]);
		}
		Rectangle2D getBounds() {
			return this.bounds;
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
	public static class PPath extends PVisualObject {
		private float x;
		private float y;
		
		private float minX = Float.MAX_VALUE;
		private float minY = Float.MAX_VALUE;
		private float maxX = -Float.MAX_VALUE;
		private float maxY = -Float.MAX_VALUE;
		
		private PSubPath currentSubPath = null;
		private ArrayList subPaths = new ArrayList();
		
		BlendComposite blendMode;
		
		private boolean isPageDecoration = false;
		
		ArrayList clipWords = null;
		
		PPath(int renderOrderNumber) {
			super(renderOrderNumber);
		}
		
		PPath(PPath model, PSubPath[] subPaths) {
			super(model.renderOrderNumber);
			
			this.strokeColor = model.strokeColor;
			this.lineWidth = model.lineWidth;
			this.lineCapStyle = model.lineCapStyle;
			this.lineJointStyle = model.lineJointStyle;
			this.miterLimit = model.miterLimit;
			this.dashPattern = model.dashPattern;
			this.dashPatternPhase = model.dashPatternPhase;
			
			this.fillColor = model.fillColor;
			this.fillEvenOdd = model.fillEvenOdd;
			
			this.clipPaths = model.clipPaths;
			this.visibleBounds = model.visibleBounds;
			
			this.blendMode = model.blendMode;
			
			for (int s = 0; s < subPaths.length; s++) {
				this.subPaths.add(subPaths[s]);
				this.minX = Math.min(this.minX, subPaths[s].minX);
				this.maxX = Math.max(this.maxX, subPaths[s].maxX);
				this.minY = Math.min(this.minY, subPaths[s].minY);
				this.maxY = Math.max(this.maxY, subPaths[s].maxY);
			}
		}
		
		void doClipWord(PWord pw) {
			this.currentSubPath = new PSubPath(this, pw.bounds.x, pw.bounds.y);
			this.currentSubPath.lineTo((pw.bounds.x + pw.bounds.width), pw.bounds.y);
			this.currentSubPath.lineTo((pw.bounds.x + pw.bounds.width), (pw.bounds.y + pw.bounds.height));
			this.currentSubPath.lineTo(pw.bounds.x, (pw.bounds.y + pw.bounds.height));
			this.currentSubPath.lineTo(pw.bounds.x, pw.bounds.y);
			this.currentSubPath = null;
			if (this.clipWords == null)
				this.clipWords = new ArrayList((pw.color == clipWordColor) ? 8 : 1);
			if (pw.color == clipWordColor)
				this.clipWords.add(pw);
		}
		
		void dom(LinkedList stack, PcrTransformationMatrixStack transformationMatrices) {
			float y = ((Number) stack.removeLast()).floatValue();
			float x = ((Number) stack.removeLast()).floatValue();
			float[] p = {x, y, 1};
			p = applyTransformationMatrices(p, transformationMatrices, "");
			this.currentSubPath = new PSubPath(this, p[0], p[1]);
		}
		void doh(LinkedList stack) {
			if (this.currentSubPath != null)
				this.currentSubPath.doh(stack);
			this.currentSubPath = null;
		}
		
		void dol(LinkedList stack, PcrTransformationMatrixStack transformationMatrices) {
			if (this.currentSubPath == null)
				this.currentSubPath = new PSubPath(this, this.x, this.y);
			this.currentSubPath.dol(stack, transformationMatrices);
		}
		
		void doc(LinkedList stack, PcrTransformationMatrixStack transformationMatrices) {
			if (this.currentSubPath == null)
				this.currentSubPath = new PSubPath(this, this.x, this.y);
			this.currentSubPath.doc(stack, transformationMatrices);
		}
		void dov(LinkedList stack, PcrTransformationMatrixStack transformationMatrices) {
			if (this.currentSubPath == null)
				this.currentSubPath = new PSubPath(this, this.x, this.y);
			this.currentSubPath.dov(stack, transformationMatrices);
		}
		void doy(LinkedList stack, PcrTransformationMatrixStack transformationMatrices) {
			if (this.currentSubPath == null)
				this.currentSubPath = new PSubPath(this, this.x, this.y);
			this.currentSubPath.doy(stack, transformationMatrices);
		}
		void dore(LinkedList stack, PcrTransformationMatrixStack transformationMatrices) {
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
		List dashPattern = null;
		float dashPatternPhase = Float.NaN;
		void stroke(PPath[] clipPaths, Color color, float lineWidth, byte lineCapStyle, byte lineJointStyle, float miterLimit, List dashPattern, float dashPatternPhase, PcrTransformationMatrixStack transformationMatrices) {
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
				this.dashPattern = new ArrayList();
				for (int e = 0; e < dashPattern.size(); e++)
					this.dashPattern.add(new Float(zoom(((Number) dashPattern.get(e)).floatValue(), transformationMatrices)));
			}
			this.dashPatternPhase = zoom(dashPatternPhase, transformationMatrices);
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println("PPath: stroked with color " + this.strokeColor + ((this.strokeColor == null) ? "" : (", alpha " + this.strokeColor.getAlpha())));
			this.setClipPaths(clipPaths);
		}
		private static float zoom(float f, PcrTransformationMatrixStack transformationMatrices) {
			float[] zoomer = {f, f, 0};
			zoomer = applyTransformationMatrices(zoomer, transformationMatrices, "");
			return ((Math.abs(zoomer[0]) + Math.abs(zoomer[1])) / 2);
		}
		
		static int fillCount = 0;
		static CountingSet fillColors = new CountingSet(new TreeMap());
		Color fillColor = null;
		boolean fillEvenOdd = false;
		void fill(PPath[] clipPaths, Color color, boolean fillEvenOdd) {
			fillCount++;
			if (color != null)
				fillColors.add(Integer.toString((color.getRGB() & 0xFFFFFF), 16).toUpperCase());
			if (color == null)
				throw new RuntimeException("Cannot fill without color");
			this.fillColor = color;
			this.fillEvenOdd = fillEvenOdd;
			if (PdfExtractorTest.aimAtPage != -1)
				System.out.println("PPath: filled (" + (this.fillEvenOdd ? "even-odd" : "non-zero winding number") + ") with color " + this.fillColor + ((this.fillColor == null) ? "" : (", alpha " + this.fillColor.getAlpha())));
			this.setClipPaths(clipPaths);
		}
		
		void fillAndStroke(PPath[] clipPaths, Color fillColor, boolean fillEvenOdd, Color strokeColor, float lineWidth, byte lineCapStyle, byte lineJointStyle, float miterLimit, List dashPattern, float dashPatternPhase, PcrTransformationMatrixStack transformationMatrices) {
			this.fill(clipPaths, fillColor, fillEvenOdd);
			this.stroke(clipPaths, strokeColor, lineWidth, lineCapStyle, lineJointStyle, miterLimit, dashPattern, dashPatternPhase, transformationMatrices);
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
			this.bounds = null;
			this.shapesSignature = null;
			this.boundsSignature = null;
		}
		
		void removeSubPath(PSubPath subPath) {
			this.subPaths.remove(subPath);
			this.bounds = null;
			this.shapesSignature = null;
			this.boundsSignature = null;
		}
		
		PSubPath[] getSubPaths() {
			return ((PSubPath[]) this.subPaths.toArray(new PSubPath[this.subPaths.size()]));
		}
		
		private Rectangle2D.Float bounds = null;
		Rectangle2D.Float getBounds() {
			if (this.bounds == null) {
				float minX = this.minX;
				float minY = this.minY;
				float maxX = this.maxX;
				float maxY = this.maxY;
				
				/* if path is stroked, we have to factor in line width and line
				 * caps (yes, rectangles _can_ and do come as very wide lines
				 * ... horizontal or vertical) */
				if (this.strokeColor != null) {
					
					//	we don't have line end decoration, and no line joints at all, add in direction vertical to main extent
					if ((this.lineCapStyle == BasicStroke.CAP_BUTT) && (this.subPaths.size() < 2)) {
						
						//	mostly vertical extent
						if (Math.abs(maxX - minX) < Math.abs(maxY - minY)) {
							minX -= (this.lineWidth / 2);
							maxX += (this.lineWidth / 2);
						}
						
						//	mostly horizontal extent
						else {
							minY -= (this.lineWidth / 2);
							maxY += (this.lineWidth / 2);
						}
					}
					
					//	we have line joint and/or line end decoration, simply add in all directions
					else {
						minX -= (this.lineWidth / 2);
						minY -= (this.lineWidth / 2);
						maxX += (this.lineWidth / 2);
						maxY += (this.lineWidth / 2);
					}
				}
				this.bounds = new Rectangle2D.Float(minX, minY, (maxX - minX), (maxY - minY));
			}
			return this.bounds;
		}
		
		boolean isFilled() {
			return this.isFilled(72.0f / 6); // we _are_ an area, if one just made up of lines wider than a sixth of an inch (some 4 mm)
		}
		boolean isFilled(float minAreaLineWidth) {
			if (this.fillColor != null)
				return true;
			return ((this.strokeColor != null) && (this.lineWidth >= minAreaLineWidth));
		}
		
		private String shapesSignature = null;
		String getShapesSignature() {
			if (this.shapesSignature == null) {
				StringBuffer shapesSignature = new StringBuffer("Path(" + this.getStrokeColorHex() + "/" + this.getFillColorHex() + "):");
				PSubPath[] subPaths = this.getSubPaths();
				for (int sp = 0; sp < subPaths.length; sp++) {
					Shape[] shapes = subPaths[sp].getShapes();
					for (int s = 0; s < shapes.length; s++) {
						if ((sp != 0) && (s == 0))
							shapesSignature.append("+");
						if (shapes[s] instanceof Line2D) {
							float x1 = ((float) ((Line2D) shapes[s]).getX1());
							float y1 = ((float) ((Line2D) shapes[s]).getY1());
							float x2 = ((float) ((Line2D) shapes[s]).getX2());
							float y2 = ((float) ((Line2D) shapes[s]).getY2());
							shapesSignature.append("L" + Math.round(x1) + "/" + Math.round(y1) + ">" + Math.round(x2) + "/" + Math.round(y2) + "");
						}
						else if (shapes[s] instanceof CubicCurve2D) {
							float x1 = ((float) ((CubicCurve2D) shapes[s]).getX1());
							float y1 = ((float) ((CubicCurve2D) shapes[s]).getY1());
							float cx1 = ((float) ((CubicCurve2D) shapes[s]).getCtrlX1());
							float cy1 = ((float) ((CubicCurve2D) shapes[s]).getCtrlY1());
							float cx2 = ((float) ((CubicCurve2D) shapes[s]).getCtrlX2());
							float cy2 = ((float) ((CubicCurve2D) shapes[s]).getCtrlY2());
							float x2 = ((float) ((CubicCurve2D) shapes[s]).getX2());
							float y2 = ((float) ((CubicCurve2D) shapes[s]).getY2());
							shapesSignature.append("CC" + Math.round(x1) + "/" + Math.round(y1) + ">" + Math.round(cx1) + "/" + Math.round(cy1) + ">" + Math.round(cx2) + "/" + Math.round(cy2) + ">" + Math.round(x2) + "/" + Math.round(y2) + "");
						}
					}
				}
				this.shapesSignature = shapesSignature.toString();
			}
			return this.shapesSignature;
		}
		
		private String boundsSignature = null;
		String getBoundsSignature() {
			if (this.boundsSignature == null) {
				StringBuffer boundsSignature = new StringBuffer("Path(" + this.getStrokeColorHex() + "/" + this.getFillColorHex() + "):");
				Rectangle2D.Float bounds = this.getBounds();
				boundsSignature.append("B" + Math.round(bounds.getMinX()) + "/" + Math.round(bounds.getMaxX()) + "x" + Math.round(bounds.getMinY()) + "/" + Math.round(bounds.getMaxY()) + "");
				this.boundsSignature = boundsSignature.toString();
			}
			return this.boundsSignature;
		}
		
		boolean isPageDecoration() {
			return this.isPageDecoration;
		}
		void setIsPageDecoration(boolean ipd) {
			this.isPageDecoration = ipd;
		}
		
		private String getStrokeColorHex() {
			return ((this.strokeColor == null) ? "ffffff" : Integer.toString((this.strokeColor.getRGB() & 0xFFFFFF), 16).toUpperCase());
		}
		private String getFillColorHex() {
			return ((this.fillColor == null) ? "ffffff" : Integer.toString((this.fillColor.getRGB() & 0xFFFFFF), 16).toUpperCase());
		}
		
		public void paint(Graphics2D gr) {
			Color preColor = gr.getColor();
			Stroke preStroke = gr.getStroke();
			
			Path2D path = new Path2D.Float();
			for (int p = 0; p < this.subPaths.size(); p++) {
				Path2D subPath = ((PSubPath) this.subPaths.get(p)).getPath();
				path.append(subPath, false);
			}
			
			if (this.fillColor != null) {
				gr.setColor(this.fillColor);
				gr.fill(path);
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
				gr.draw(path);
			}
			else if (this.fillColor != null) {
				gr.setColor(this.fillColor);
				gr.draw(path);
			}
//			NEED TO FIRST COLLECT ALL SUB PATHS AND THEN FILL THE WHOLE PATH SO EVEN-ODD RULE CAN TAKE EFFECT ON FILLING
//			for (int p = 0; p < this.subPaths.size(); p++) {
//				Path2D sp = ((PSubPath) this.subPaths.get(p)).getPath();
//				if (this.fillColor != null) {
//					gr.setColor(this.fillColor);
//					gr.fill(sp);
//				}
//				if (this.strokeColor != null) {
//					gr.setColor(this.strokeColor);
//					float[] dashPattern = new float[this.dashPattern.size()];
//					boolean allZeroDashes = true;
//					for (int e = 0; e < this.dashPattern.size(); e++) {
//						dashPattern[e] = ((Number) this.dashPattern.get(e)).floatValue();
//						allZeroDashes = (allZeroDashes && (dashPattern[e] == 0));
//					}
//					if (allZeroDashes)
//						dashPattern = null;
//					gr.setStroke(new BasicStroke(this.lineWidth, this.lineCapStyle, this.lineJointStyle, ((this.miterLimit < 1) ? 1.0f : this.miterLimit), dashPattern, this.dashPatternPhase));
//					gr.draw(sp);
//				}
//				else if (this.fillColor != null) {
//					gr.setColor(this.fillColor);
//					gr.draw(sp);
//				}
//			}
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
			
//			if (this.clipPaths != null) {
//				tPath.clipPaths = new PPath[this.clipPaths.length];
//				for (int cp = 0; cp < this.clipPaths.length; cp++)
//					tPath.clipPaths[cp] = this.clipPaths[cp].translate(x, y);
//			}
//			tPath.visibleBounds = getVisibleBounds(tPath.getBounds(), tPath.clipPaths);
			PPath[] tClipPaths = null;
			if (this.clipPaths != null) {
				tClipPaths = new PPath[this.clipPaths.length];
				for (int cp = 0; cp < this.clipPaths.length; cp++)
					tClipPaths[cp] = this.clipPaths[cp].translate(x, y);
			}
			tPath.setClipPaths(tClipPaths);
			
			return tPath;
		}
//		
//		PPath transform(LinkedList matrices) {
//			PPath tPath = new PPath(this.renderOrderNumber);
//			float[] minPoint = transformPoint(this.minX, this.minY, matrices);
//			tPath.minX = minPoint[0];
//			tPath.minY = minPoint[1];
//			float[] maxPoint = transformPoint(this.maxX, this.maxY, matrices);
//			tPath.maxX = maxPoint[0];
//			tPath.maxY = maxPoint[1];
//			
//			tPath.strokeColor = this.strokeColor;
//			tPath.lineWidth = this.lineWidth;
//			tPath.lineCapStyle = this.lineCapStyle;
//			tPath.lineJointStyle = this.lineJointStyle;
//			tPath.miterLimit = this.miterLimit;
//			tPath.dashPattern = this.dashPattern;
//			tPath.dashPatternPhase = this.dashPatternPhase;
//			tPath.fillColor = this.fillColor;
//			
//			for (int p = 0; p < this.subPaths.size(); p++)
//				tPath.subPaths.add(((PSubPath) this.subPaths.get(p)).transform(tPath, matrices));
//			
//			if (this.clipPaths != null) {
//				tPath.clipPaths = new PPath[this.clipPaths.length];
//				for (int cp = 0; cp < this.clipPaths.length; cp++)
//					tPath.clipPaths[cp] = this.clipPaths[cp].transform(matrices);
//			}
//			tPath.visibleBounds = getVisibleBounds(tPath.getBounds(), tPath.clipPaths);
//			
//			return tPath;
//		}
		
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
		
		void dol(LinkedList stack, PcrTransformationMatrixStack transformationMatrices) {
			float y = ((Number) stack.removeLast()).floatValue();
			float x = ((Number) stack.removeLast()).floatValue();
			float[] p = {x, y, 1};
			p = applyTransformationMatrices(p, transformationMatrices, "");
			this.lineTo(p[0], p[1]);
		}
		void doh(LinkedList stack) {
			this.lineTo(this.startX, this.startY);
		}
		void lineTo(float x, float y) {
			
			//	store shape for later use
			this.shapes.add(new Line2D.Float(this.x, this.y, x, y));
			if (this.shapes.size() == 1)
				this.parent.addSubPath(this);
			this.x = x;
			this.y = y;
			
			//	remember visiting current point
			this.addPoint(this.x, this.y);
			
			//	bounds might have changed ...
			this.bounds = null;
		}
		
		void doc(LinkedList stack, PcrTransformationMatrixStack transformationMatrices) {
			float y3 = ((Number) stack.removeLast()).floatValue();
			float x3 = ((Number) stack.removeLast()).floatValue();
			float y2 = ((Number) stack.removeLast()).floatValue();
			float x2 = ((Number) stack.removeLast()).floatValue();
			float y1 = ((Number) stack.removeLast()).floatValue();
			float x1 = ((Number) stack.removeLast()).floatValue();
			float[] p = {x3, y3, 1};
			p = applyTransformationMatrices(p, transformationMatrices, "");
			float[] cp1 = {x1, y1, 1};
			cp1 = applyTransformationMatrices(cp1, transformationMatrices, "");
			float[] cp2 = {x2, y2, 1};
			cp2 = applyTransformationMatrices(cp2, transformationMatrices, "");
			this.curveTo(cp1[0], cp1[1], cp2[0], cp2[1], p[0], p[1]);
		}
		void dov(LinkedList stack, PcrTransformationMatrixStack transformationMatrices) {
			float y3 = ((Number) stack.removeLast()).floatValue();
			float x3 = ((Number) stack.removeLast()).floatValue();
			float y2 = ((Number) stack.removeLast()).floatValue();
			float x2 = ((Number) stack.removeLast()).floatValue();
			float[] p = {x3, y3, 1};
			p = applyTransformationMatrices(p, transformationMatrices, "");
			float[] cp2 = {x2, y2, 1};
			cp2 = applyTransformationMatrices(cp2, transformationMatrices, "");
			this.curveTo(this.x, this.y, cp2[0], cp2[1], p[0], p[1]);
		}
		void doy(LinkedList stack, PcrTransformationMatrixStack transformationMatrices) {
			float y3 = ((Number) stack.removeLast()).floatValue();
			float x3 = ((Number) stack.removeLast()).floatValue();
			float y1 = ((Number) stack.removeLast()).floatValue();
			float x1 = ((Number) stack.removeLast()).floatValue();
			float[] p = {x3, y3, 1};
			p = applyTransformationMatrices(p, transformationMatrices, "");
			float[] cp1 = {x1, y1, 1};
			cp1 = applyTransformationMatrices(cp1, transformationMatrices, "");
			this.curveTo(cp1[0], cp1[1], p[0], p[1], p[0], p[1]);
		}
		void curveTo(float cx1, float cy1, float cx2, float cy2, float x, float y) {
			
			//	store shape for later use
			this.shapes.add(new CubicCurve2D.Float(this.x, this.y, cx1, cy1, cx2, cy2, x, y));
			if (this.shapes.size() == 1)
				this.parent.addSubPath(this);
			this.x = x;
			this.y = y;
			
			//	remember extent and visiting current
			this.addPoint(cx1, cy1);
			this.addPoint(cx2, cy2);
			this.addPoint(this.x, this.y);
			
			//	bounds might have changed ...
			this.bounds = null;
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
		
		private Rectangle2D.Float bounds = null;
		Rectangle2D.Float getBounds(PPath parent) {
			if (this.bounds == null) {
				float minX = this.minX;
				float minY = this.minY;
				float maxX = this.maxX;
				float maxY = this.maxY;
				
				/* if path is stroked, we have to factor in line width and line
				 * caps (yes, rectangles _can_ and do come as very wide lines
				 * ... horizontal or vertical) */
				if (parent.strokeColor != null) {
					
					//	we don't have line end decoration, and no line joints at all, add in direction vertical to main extent
					if ((parent.lineCapStyle == BasicStroke.CAP_BUTT) && (parent.subPaths.size() < 2)) {
						
						//	mostly vertical extent
						if (Math.abs(maxX - minX) < Math.abs(maxY - minY)) {
							minX -= (parent.lineWidth / 2);
							maxX += (parent.lineWidth / 2);
						}
						
						//	mostly horizontal extent
						else {
							minY -= (parent.lineWidth / 2);
							maxY += (parent.lineWidth / 2);
						}
					}
					
					//	we have line joint and/or line end decoration, simply add in all directions
					else {
						minX -= (parent.lineWidth / 2);
						minY -= (parent.lineWidth / 2);
						maxX += (parent.lineWidth / 2);
						maxY += (parent.lineWidth / 2);
					}
				}
				this.bounds = new Rectangle2D.Float(minX, minY, (maxX - minX), (maxY - minY));
			}
//			if (this.bounds == null)
//				this.bounds = new Rectangle2D.Float(this.minX, this.minY, (this.maxX - this.minX), (this.maxY - this.minY));
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
//		
//		PSubPath transform(PPath tParent, LinkedList matrices) {
//			float[] startPoint = transformPoint(this.startX, this.startY, matrices);
//			PSubPath tSubPath = new PSubPath(tParent, startPoint[0], startPoint[1]);
//			float[] minPoint = transformPoint(this.minX, this.minY, matrices);
//			tSubPath.minX = minPoint[0];
//			tSubPath.minY = minPoint[1];
//			float[] maxPoint = transformPoint(this.maxX, this.maxY, matrices);
//			tSubPath.maxX = maxPoint[0];
//			tSubPath.maxY = maxPoint[1];
//			
//			for (int s = 0; s < this.shapes.size(); s++) {
//				Shape shape = ((Shape) this.shapes.get(s));
//				if (shape instanceof Line2D) {
//					Line2D line = ((Line2D) shape);
//					float[] sPoint = transformPoint(((float) line.getX1()), ((float) line.getY1()), matrices);
//					float[] ePoint = transformPoint(((float) line.getX2()), ((float) line.getY2()), matrices);
//					tSubPath.shapes.add(new Line2D.Float(sPoint[0], sPoint[1], ePoint[0], ePoint[1]));
//				}
//				else if (shape instanceof CubicCurve2D) {
//					CubicCurve2D curve = ((CubicCurve2D) shape);
//					float[] sPoint = transformPoint(((float) curve.getX1()), ((float) curve.getY1()), matrices);
//					float[] cPoint1 = transformPoint(((float) curve.getCtrlX1()), ((float) curve.getCtrlY1()), matrices);
//					float[] cPoint2 = transformPoint(((float) curve.getCtrlX2()), ((float) curve.getCtrlY2()), matrices);
//					float[] ePoint = transformPoint(((float) curve.getX2()), ((float) curve.getY2()), matrices);
//					tSubPath.shapes.add(new CubicCurve2D.Float(sPoint[0], sPoint[1], cPoint1[0], cPoint1[1], cPoint2[0], cPoint2[1], ePoint[0], ePoint[1]));
//				}
//			}
//			return tSubPath;
//		}
	}
	
	static Rectangle2D getVisibleBounds(Rectangle2D bounds, PPath[] clipPaths) {
		Rectangle2D visibleBounds = bounds;
		if (clipPaths == null)
			visibleBounds = visibleBounds.createIntersection(bounds); // need to copy for independent modification
		else for (int cp = 0; cp < clipPaths.length; cp++)
			visibleBounds = visibleBounds.createIntersection(clipPaths[cp].getBounds());
		return visibleBounds;
	}
	
	static Rectangle2D.Float convertFloat(Rectangle2D bounds) {
		if (bounds instanceof Rectangle2D.Float)
			return ((Rectangle2D.Float) bounds);
		return new Rectangle2D.Float(((float) bounds.getX()), ((float) bounds.getY()), ((float) bounds.getWidth()), ((float) bounds.getHeight()));
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
	
	/**
	 * Wrapper for data used to rotate a PDF page.
	 * 
	 * @author sautter
	 */
	public static class PRotate {
		static final PRotate identity = new PRotate(0, 0, 0);
		public final float centerX;
		public final float centerY;
		public final int degrees;
		PRotate(float centerX, float centerY, int degrees) {
			this.centerX = centerX;
			this.centerY = centerY;
			this.degrees = degrees;
		}
		PRotate(Page pdfPage, Map objects) {
			Rectangle2D.Float pageBox = pdfPage.getMediaBox();
			if (pageBox == null)
				pageBox = pdfPage.getCropBox();
			Rectangle2D.Float pageContentBox = pdfPage.getCropBox();
			if (pageContentBox == null)
				pageContentBox = pageBox;
			this.centerX = (pageContentBox.width / 2);
			this.centerY = (pageContentBox.height / 2);
			Object rotateObj = PdfParser.dereference(pdfPage.getEntries().get("Rotate"), objects);
			this.degrees = ((rotateObj instanceof Number) ? ((Number) rotateObj).intValue() : 0);
		}
		public String toString() {
			return (this.degrees + " around " + this.centerX + "/" + this.centerY);
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
	 * @param wordsAreOcr are words OCR embedded in a scanned PDF?
	 * @throws IOException
	 */
	public static void getPageWordChars(Map entries, byte[] content, PRotate rotate, Map resources, Map objects, boolean wordsAreOcr, ProgressMonitor pm) throws IOException {
		runPageContent(entries, content, rotate, resources, objects, null, null, PdfFontDecoder.NO_DECODING, wordsAreOcr, null, null, pm);
	}
	
	/**
	 * Parse a content stream of a page and extract the words.
	 * @param entries the page entries
	 * @param content the bytes to parse
	 * @param resources the page resource map
	 * @param objects a map holding objects that might be required, such as
	 *            fonts, etc.
	 * @param tokenizer get tokenizer to check and split words with
	 * @param wordsAreOcr are words OCR embedded in a scanned PDF?
	 * @param pm a progress monitor to receive updates on the word extraction
	 *            process
	 * @return the words extracted from the page
	 * @throws IOException
	 */
	public static PWord[] getPageWords(Map entries, byte[] content, PRotate rotate, Map resources, Map objects, Tokenizer tokenizer, boolean wordsAreOcr, ProgressMonitor pm) throws IOException {
		return getPageWords(entries, content, rotate, resources, objects, tokenizer, PdfFontDecoder.UNICODE, wordsAreOcr, null, null, pm);
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
	 * @param wordsAreOcr are words OCR embedded in a scanned PDF?
	 * @param pm a progress monitor to receive updates on the word extraction
	 *            process
	 * @return the words extracted from the page
	 * @throws IOException
	 */
	public static PWord[] getPageWords(Map entries, byte[] content, PRotate rotate, Map resources, Map objects, Tokenizer tokenizer, FontDecoderCharset fontCharSet, boolean wordsAreOcr, ProgressMonitor pm) throws IOException {
		return getPageWords(entries, content, rotate, resources, objects, tokenizer, fontCharSet, wordsAreOcr, null, null, pm);
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
	 * @param wordsAreOcr are words OCR embedded in a scanned PDF?
	 * @param figures a list to collect figures in during word extraction
	 * @param paths a list to collect paths (PDF vector graphics) in during
	 *            word extraction
	 * @param pm a progress monitor to receive updates on the word extraction
	 *            process
	 * @return the words extracted from the page
	 * @throws IOException
	 */
	public static PWord[] getPageWords(Map entries, byte[] content, PRotate rotate, Map resources, Map objects, Tokenizer tokenizer, boolean wordsAreOcr, List figures, List paths, ProgressMonitor pm) throws IOException {
		return getPageWords(entries, content, rotate, resources, objects, tokenizer, PdfFontDecoder.UNICODE, wordsAreOcr, figures, paths, pm);
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
	 * @param wordsAreOcr are words OCR embedded in a scanned PDF?
	 * @param figures a list to collect figures in during word extraction
	 * @param paths a list to collect paths (PDF vector graphics) in during
	 *            word extraction
	 * @param pm a progress monitor to receive updates on the word extraction
	 *            process
	 * @return the words extracted from the page
	 * @throws IOException
	 */
	public static PWord[] getPageWords(Map entries, byte[] content, PRotate rotate, Map resources, Map objects, Tokenizer tokenizer, FontDecoderCharset fontCharSet, boolean wordsAreOcr, List figures, List paths, ProgressMonitor pm) throws IOException {
		ArrayList words = new ArrayList();
		runPageContent(entries, content, rotate, resources, objects, words, tokenizer, fontCharSet, wordsAreOcr, figures, paths, pm);
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
	public static PFigure[] getPageFigures(Map entries, byte[] content, PRotate rotate, Map resources, Map objects, ProgressMonitor pm) throws IOException {
		ArrayList figures = new ArrayList();
		runPageContent(entries, content, rotate, resources, objects, null, null, false, figures, null, pm);
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
	public static PPath[] getPagePaths(Map entries, byte[] content, PRotate rotate, Map resources, Map objects, ProgressMonitor pm) throws IOException {
		ArrayList paths = new ArrayList();
		runPageContent(entries, content, rotate, resources, objects, null, null, false, null, paths, pm);
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
	public static PPageContent getPageGraphics(Map entries, byte[] content, PRotate rotate, Map resources, Map objects, ProgressMonitor pm) throws IOException {
		ArrayList figures = new ArrayList();
		ArrayList paths = new ArrayList();
		runPageContent(entries, content, rotate, resources, objects, null, null, false, figures, paths, pm);
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
	 * @param wordsAreOcr are words OCR embedded in a scanned PDF?
	 * @param pm a progress monitor to receive updates on the word extraction
	 *            process
	 * @return the words, figures, and vector graphics paths extracted from the
	 *            page
	 * @throws IOException
	 */
	public static PPageContent getPageContent(Map entries, byte[] content, PRotate rotate, Map resources, Map objects, Tokenizer tokenizer, boolean wordsAreOcr, ProgressMonitor pm) throws IOException {
		return getPageContent(entries, content, rotate, resources, objects, tokenizer, PdfFontDecoder.UNICODE, wordsAreOcr, pm);
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
	 * @param wordsAreOcr are words OCR embedded in a scanned PDF?
	 * @param pm a progress monitor to receive updates on the word extraction
	 *            process
	 * @return the words, figures, and vector graphics paths extracted from the
	 *            page
	 * @throws IOException
	 */
	public static PPageContent getPageContent(Map entries, byte[] content, PRotate rotate, Map resources, Map objects, Tokenizer tokenizer, FontDecoderCharset fontCharSet, boolean wordsAreOcr, ProgressMonitor pm) throws IOException {
		ArrayList words = new ArrayList();
		ArrayList figures = new ArrayList();
		ArrayList paths = new ArrayList();
		runPageContent(entries, content, rotate, resources, objects, words, tokenizer, fontCharSet, wordsAreOcr, figures, paths, pm);
		return new PPageContent(((PWord[]) words.toArray(new PWord[words.size()])), ((PFigure[]) figures.toArray(new PFigure[figures.size()])), ((PPath[]) paths.toArray(new PPath[paths.size()])));
	}
	
	//	SYNCHRONIZING THIS AS A WHOLE DOES DO THE TRICK ... BUT MAYBE WE CAN BE FASTER
//	private static synchronized Map getPageFonts(Map resources, Map objects, FontDecoderCharset charSet, ProgressMonitor pm) throws IOException {
	private static Map getPageFonts(Map resources, Map objects, FontDecoderCharset charSet, ProgressMonitor pm) throws IOException {
		synchronized (objects) /* as objects map is local to decoding run, this should allow multiple PDFs to decode in parallel */ {
			return doGetPageFonts(resources, objects, charSet, pm);
		}
	}
	private static Map doGetPageFonts(Map resources, Map objects, FontDecoderCharset charSet, ProgressMonitor pm) throws IOException {
		Map fonts = new HashMap();
		Map charDecoders = new HashMap();
		
		//	get font dictionary
		final Object fontsObj = dereference(resources.get("Font"), objects);
		if (PdfFont.DEBUG_LOAD_FONTS) System.out.println(" --> font object is " + fontsObj);
		
		//	anything to work with?
		if ((fontsObj == null) || !(fontsObj instanceof Map))
			return fonts;
		
		//	get fonts
		ArrayList toDecodeFonts = new ArrayList();
		for (Iterator fit = ((Map) fontsObj).keySet().iterator(); fit.hasNext();) {
			
			//	read basic data
			Object fontKey = fit.next();
			Object fontRef = null;
			Object fontObj = ((Map) fontsObj).get(fontKey);
			if (fontObj instanceof Reference) {
				fontRef = fontObj;
				fontObj = dereference(fontRef, objects);
			}
			if (fontObj == null) 
				continue;
			
			if (PdfFont.DEBUG_LOAD_FONTS) {
				if (fontRef == null)
					System.out.println("Loading font " + fontKey + " (" + fontKey.getClass().getName() + ")");
				else System.out.println("Loading font " + fontKey + " (" + fontKey.getClass().getName() + ") from " + ((Reference) fontRef).getObjectNumber() + " " + ((Reference) fontRef).getGenerationNumber());
			}
			
			//	this one has already been loaded
			if (fontObj instanceof PdfFont) {
				fonts.put(fontKey.toString(), ((PdfFont) fontObj));
				if (charSet != PdfFontDecoder.NO_DECODING)
					toDecodeFonts.add(fontObj);
				if (PdfFont.DEBUG_LOAD_FONTS && (fontRef != null)) System.out.println("Font cache hit for " + ((Reference) fontRef).getObjectNumber() + " " + ((Reference) fontRef).getGenerationNumber());
			}
			
			//	we need to load this one
			else if (fontObj instanceof Map) {
				if (PdfFont.DEBUG_LOAD_FONTS) System.out.println("Loading font from " + fontObj);
				PdfFont pFont = PdfFont.readFont(fontKey, ((Map) fontObj), objects, true, charDecoders, charSet, pm);
				if (pFont != null) {
					if (PdfFont.DEBUG_LOAD_FONTS) System.out.println("Font loaded: " + pFont.name + " (" + pFont + ")");
					fonts.put(fontKey.toString(), pFont);
					if (fontRef != null) synchronized (objects) {
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
	 * @param wordsAreOcr are words OCR embedded in a scanned PDF?
	 * @param pm a progress monitor to receive updates on the word extraction
	 *            process
	 * @return the words extracted from the page
	 * @throws IOException
	 */
	public static void runPageContent(Map entries, byte[] content, PRotate rotate, Map resources, Map objects, List words, Tokenizer tokenizer, boolean wordsAreOcr, List figures, List paths, ProgressMonitor pm) throws IOException {
		runPageContent(0, null, entries, content, rotate, resources, objects, words, tokenizer, PdfFontDecoder.UNICODE, figures, paths, ((words == null) && (figures == null) && (paths == null)), wordsAreOcr, pm, "");
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
	 * @param wordsAreOcr are words OCR embedded in a scanned PDF?
	 * @param pm a progress monitor to receive updates on the word extraction
	 *            process
	 * @return the words extracted from the page
	 * @throws IOException
	 */
	public static void runPageContent(Map entries, byte[] content, PRotate rotate, Map resources, Map objects, List words, Tokenizer tokenizer, FontDecoderCharset fontCharSet, boolean wordsAreOcr, List figures, List paths, ProgressMonitor pm) throws IOException {
		runPageContent(0, null, entries, content, rotate, resources, objects, words, tokenizer, fontCharSet, figures, paths, ((words == null) && (figures == null) && (paths == null)), wordsAreOcr, pm, "");
	}
	
	private static int runPageContent(int firstRenderOrderNumber, PcrTransformationMatrixStack inheritedTransformationMatrices, Map entries, byte[] content, PRotate rotate, Map resources, Map objects, List words, Tokenizer tokenizer, FontDecoderCharset fontCharSet, List figures, List paths, boolean assessFonts, boolean wordsAreOcr, ProgressMonitor pm, String indent) throws IOException {
		
		//	get XObject (required for figure extraction and recursive form handling)
		Object xObjectObj = dereference(resources.get("XObject"), objects);
		Map xObject = ((xObjectObj instanceof Map) ? ((Map) xObjectObj) : null);
		
		//	get fonts (unless we're after figures or vector paths, but not after words)
		Map fonts = (((words == null) && ((figures != null) || (paths != null))) ? new HashMap() : getPageFonts(resources, objects, ((words == null) ? PdfFontDecoder.NO_DECODING : fontCharSet), pm));
		
		//	we need to collect words even in font assessment mode
		if ((words == null) && assessFonts)
			words = new ArrayList();
		
		//	create renderer to fill lists
		PageContentRenderer pcr = new PageContentRenderer(firstRenderOrderNumber, rotate, inheritedTransformationMatrices, resources, fonts, fontCharSet, xObject, objects, words, figures, paths, assessFonts, wordsAreOcr, indent);
		
		//	process page content through renderer
		PdfByteInputStream bytes = new PdfByteInputStream(new ByteArrayInputStream(content));
		System.out.println("Running page content of " + content.length + " bytes");
		Object obj;
		LinkedList stack = new LinkedList();
		while ((obj = cropNext(bytes, true, false)) != null) {
			if (obj instanceof PInlineImage) {
				if (DEBUG_RENDER_PAGE_CONTENT) System.out.println("Inline Image: " + ((PInlineImage) obj).tag + " [" + ((PInlineImage) obj).data.length + "]");
				pcr.evaluateTag(((PInlineImage) obj).tag, ((PInlineImage) obj).data, stack, pm);
			}
			else if (obj instanceof PTag) {
				if (DEBUG_RENDER_PAGE_CONTENT) System.out.println("Content tag: " + ((PTag) obj).tag);
				pcr.evaluateTag(((PTag) obj).tag, null, stack, pm);
			}
			else {
				if (DEBUG_RENDER_PAGE_CONTENT) {
					System.out.println(obj.getClass().getName() + ": " + obj.toString());
					if (obj instanceof PString) {
						PString str = ((PString) obj);
						System.out.println(" --> HEX 2: " + str.isHex2 + ", HEX 4: " + str.isHex4);
						System.out.println(" --> " + Arrays.toString(str.bytes));
						if (str.isHex2) {
							System.out.print(" -->");
							for (int b = 0; (b + 1) < str.bytes.length; b += 2)
								System.out.print(" " + ((char) str.bytes[b]) + ((char) str.bytes[b+1]));
							System.out.println();
						}
						else if (str.isHex4) {
							System.out.print(" -->");
							for (int b = 0; (b + 3) < str.bytes.length; b += 4)
								System.out.print(" " + ((char) str.bytes[b]) + ((char) str.bytes[b+1]) + ((char) str.bytes[b+2]) + ((char) str.bytes[b+3]));
							System.out.println();
						}
					}
				}
				stack.addLast(obj);
			}
		}
		
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
		
//		NO NEED TO CHECK ROTATION HERE ANY MORE, AS WE DON'T HAVE TO FIT AN IcePDF PAGE IMAGE ANY LONGER
//		//	check rotation
//		Object rotateObj = dereference(entries.get("Rotate"), objects);
//		int rotate = ((rotateObj instanceof Number) ? ((Number) rotateObj).intValue() : 0);
//		if (DEBUG_RENDER_PAGE_CONTENT) System.out.println("Page rotation is " + rotate);
//		
//		//	rotate words and figures
//		if (rotate != 0) {
//			if (DEBUG_RENDER_PAGE_CONTENT) System.out.println("Rotating words and figures");
//			Object mbObj = dereference(entries.get("MediaBox"), objects);
//			if (DEBUG_RENDER_PAGE_CONTENT) System.out.println(" - media box is " + mbObj);
//			float pgw = -1;
//			float pgh = -1;
//			if ((mbObj instanceof Vector) && (((Vector) mbObj).size() == 4)) {
//				Object pgwObj = ((Vector) mbObj).get(2);
//				if (pgwObj instanceof Number)
//					pgw = ((Number) pgwObj).floatValue();
//				Object pghObj = ((Vector) mbObj).get(3);
//				if (pghObj instanceof Number)
//					pgh = ((Number) pghObj).floatValue();
//			}
//			if (DEBUG_RENDER_PAGE_CONTENT) System.out.println(" - page width is " + pgw + ", page height is " + pgh);
//			if (words != null)
//				for (int w = 0; w < words.size(); w++) {
//					PWord pw = ((PWord) words.get(w));
//					if (DEBUG_RENDER_PAGE_CONTENT && !assessFonts) System.out.println(" - '" + pw.str + "' @ " + pw.bounds + " directed " + pw.fontDirection);
//					int rfd = pw.fontDirection;
//					if ((rotate == 90) || (rotate == -270))
//						rfd--;
//					else if ((rotate == 270) || (rotate == -90))
//						rfd++;
//					PWord rpw = new PWord(pw.renderOrderNumber, pw.charCodes, pw.str, rotateBounds(pw.bounds, pgw, pgh, rotate), pw.color, pw.fontSize, rfd, pw.font);
//					rpw.joinWithNext = pw.joinWithNext;
//					if (DEBUG_RENDER_PAGE_CONTENT && !assessFonts) System.out.println(" --> '" + rpw.str + "' @ " + rpw.bounds + " directed " + rpw.fontDirection);
//					words.set(w, rpw);
//				}
//			if (figures != null)
//				for (int f = 0; f < figures.size(); f++) {
//					PFigure pf = ((PFigure) figures.get(f));
//					if (DEBUG_RENDER_PAGE_CONTENT && !assessFonts) System.out.println(" - '" + pf.name + "' @ " + pf.bounds);
//					PFigure rpf = new PFigure(pf.renderOrderNumber, pf.name, rotateBounds(pf.bounds, pgw, pgh, rotate), pf.refOrData, pf.rotation, pf.rightSideLeft, pf.upsideDown);
//					if (DEBUG_RENDER_PAGE_CONTENT && !assessFonts) System.out.println(" --> '" + rpf.name + "' @ " + rpf.bounds);
//					figures.set(f, rpf);
//				}
//		}
		
		//	TODO eliminate second words of pairs with same strings, font, and font size that overlap by more than 90% in each direction
		//	(some documents actually emulate bold face by slightly offset multi-rendering, TEST page 0 of RevistaBrasZool_25_4.pdf)
		//	need to go at least partially nested loop here because we need to maintain rendering order for analyses below
		if (!assessFonts && (words != null) && (words.size() > 1)) {
			HashMap wordsByString = new HashMap();
			int needCheck = 0;
			for (int w = 0; w < words.size(); w++) {
				PWord word = ((PWord) words.get(w));
				Object wordStringObject = wordsByString.get(word.str);
				if (wordStringObject == null)
					wordsByString.put(word.str, word);
				else if (wordStringObject instanceof ArrayList)
					((ArrayList) wordStringObject).add(word);
				else {
					ArrayList stringWords = new ArrayList(2);
					stringWords.add(wordStringObject);
					stringWords.add(word);
					wordsByString.put(word.str, stringWords);
					needCheck++;
				}
			}
			HashSet retainWords = new HashSet();
			for (Iterator wsit = wordsByString.keySet().iterator(); wsit.hasNext();) {
				String wordString = ((String) wsit.next());
				Object wordStringObject = wordsByString.get(wordString);
				if (wordStringObject instanceof PWord) {
					retainWords.add(wordStringObject);
					continue; // no duplicates to check
				}
				ArrayList stringWords = ((ArrayList) wordStringObject);
				//	TODO maybe sort by font size to partition further
				//	TODO maybe alternatively sort topologically or by bounding box area
				for (int lw = 0; lw < stringWords.size(); lw++) {
					PWord lastWord = ((PWord) stringWords.get(lw));
					for (int w = (lw +1); w < stringWords.size(); w++) {
						PWord word = ((PWord) stringWords.get(w));
						if (lastWord.fontSize != word.fontSize)
							continue;
						if (lastWord.fontDirection != word.fontDirection)
							continue;
						if (lastWord.font != word.font)
							continue;
						if (!lastWord.str.equals(word.str))
							continue;
						if (DEBUG_MERGE_WORDS) System.out.println("Checking for overlapping words " + lastWord.str + " @" + lastWord.bounds.toString() + " and " + word.str + " @" + word.bounds.toString());
						Rectangle2D.Float overlap = new Rectangle2D.Float();
						Rectangle2D.intersect(lastWord.bounds, word.bounds, overlap);
						if ((overlap.getHeight() * 10) < (lastWord.bounds.getHeight() * 9))
							continue;
						if ((overlap.getHeight() * 10) < (word.bounds.getHeight() * 9))
							continue;
						int minHorizontalOverlapFactor = ((lastWord.str.length() < 2) ? 8 : 9);
						if ((overlap.getWidth() * 10) < (lastWord.bounds.getWidth() * minHorizontalOverlapFactor))
							continue;
						if ((overlap.getWidth() * 10) < (word.bounds.getWidth() * minHorizontalOverlapFactor))
							continue;
						if (DEBUG_MERGE_WORDS) System.out.println(" --> eliminated overlapping word duplicte '" + word.str + "' at " + word.bounds);
						stringWords.remove(w--);
						//	TODO somehow preserve later render order number (eliminate back to front ???)
						//words.remove(lw--);
						//break;
					}
				}
				retainWords.addAll(stringWords);
				needCheck--;
			}
			for (int w = 0; w < words.size(); w++) {
				PWord word = ((PWord) words.get(w));
				if (!retainWords.remove(word))
					words.remove(w--);
			}
//			for (int lw = 0; lw < words.size(); lw++) {
//				PWord lastWord = ((PWord) words.get(lw));
//				for (int w = (lw +1); w < words.size(); w++) {
//					PWord word = ((PWord) words.get(w));
//					if (lastWord.fontSize != word.fontSize)
//						continue;
//					if (lastWord.fontDirection != word.fontDirection)
//						continue;
//					if (lastWord.font != word.font)
//						continue;
//					if (!lastWord.str.equals(word.str))
//						continue;
//					if (DEBUG_MERGE_WORDS) System.out.println("Checking for overlapping words " + lastWord.str + " @" + lastWord.bounds.toString() + " and " + word.str + " @" + word.bounds.toString());
//					Rectangle2D.Float overlap = new Rectangle2D.Float();
//					Rectangle2D.intersect(lastWord.bounds, word.bounds, overlap);
//					if ((overlap.getHeight() * 10) < (lastWord.bounds.getHeight() * 9))
//						continue;
//					if ((overlap.getHeight() * 10) < (word.bounds.getHeight() * 9))
//						continue;
//					int minHorizontalOverlapFactor = ((lastWord.str.length() < 2) ? 8 : 9);
//					if ((overlap.getWidth() * 10) < (lastWord.bounds.getWidth() * minHorizontalOverlapFactor))
//						continue;
//					if ((overlap.getWidth() * 10) < (word.bounds.getWidth() * minHorizontalOverlapFactor))
//						continue;
//					if (DEBUG_MERGE_WORDS) System.out.println(" --> eliminated overlapping word duplicte '" + word.str + "' at " + word.bounds); // TODO debug flag this after tests
//					words.remove(w--);
//					//	TODO somehow preserve later render order number (eliminate back to front ???)
//					//words.remove(lw--);
//					//break;
//				}
//			}
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
				
				//	in font assessment mode, check distance, record char neighborhood, and we're done
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
//				
//				//	never merge or join numbers (unless font is same and has implicit spaces) TODO WHY ?!?
//				if ((Gamta.isNumber(lastWord.str) || Gamta.isNumber(word.str)) && ((lastWord.font == null) || (lastWord.font != word.font) || !lastWord.font.hasImplicitSpaces)) {
//					lastWord = word;
//					lastWordMiddleX = wordMiddleX;
//					lastWordMiddleY = wordMiddleY;
//					if (DEBUG_MERGE_WORDS && !assessFonts) System.out.println(" --> not merging numbers");
//					continue;
//				}
				
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
				if (wordStartsCombiningAccent && areWordsCloseEnoughForCombinedAccent(lastWord, lastWordIsCombiningAccent, word, wordIsCombiningAccent)) {
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
						jointWord = new PWord(Math.min(lastWord.renderOrderNumber, word.renderOrderNumber), jointCharCodes, (lastWord.str.substring(0, lastWord.str.length()-1) + combinedChar + word.str.substring(1)), lastWord.baseline, jointWordBox, lastWord.color, Math.max(lastWord.fontSize, word.fontSize), lastWord.fontDirection, lastWord.font);
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
						jointWord = new PWord(Math.min(lastWord.renderOrderNumber, word.renderOrderNumber), (lastWord.charCodes + word.charCodes), (lastWord.str + word.str), lastWord.baseline, jointWordBox, lastWord.color, Math.max(lastWord.fontSize, word.fontSize), lastWord.fontDirection, lastWord.font);
						jointWord.joinWithNext = word.joinWithNext;
						if (DEBUG_RENDER_PAGE_CONTENT || DEBUG_MERGE_WORDS)
							System.out.println(" --> combination not possible");
					}
				}
				
				//	test if first word (a) ends with combinable accent, (b) this accent is combinable with first char of second word, and (c) there is sufficient overlap
				else if (lastWordEndsCombiningAccent && areWordsCloseEnoughForCombinedAccent(lastWord, lastWordIsCombiningAccent, word, wordIsCombiningAccent)) {
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
						jointWord = new PWord(Math.min(lastWord.renderOrderNumber, word.renderOrderNumber), jointCharCodes, (lastWord.str.substring(0, lastWord.str.length()-1) + combinedChar + word.str.substring(1)), lastWord.baseline, jointWordBox, lastWord.color, Math.max(lastWord.fontSize, word.fontSize), word.fontDirection, word.font);
						jointWord.joinWithNext = word.joinWithNext;
						if (DEBUG_RENDER_PAGE_CONTENT || DEBUG_MERGE_WORDS)
							System.out.println(" --> combined letter and diacritic marker");
						
						//	if we just merged away the last word altogether, we need to jump back by one word
						if (lastWordIsCombiningAccent)
							revisitPreviousWord = true;
					}
					else {
						jointWord = new PWord(Math.min(lastWord.renderOrderNumber, word.renderOrderNumber), (lastWord.charCodes + word.charCodes), (lastWord.str + word.str), lastWord.baseline, jointWordBox, lastWord.color, Math.max(lastWord.fontSize, word.fontSize), lastWord.fontDirection, lastWord.font);
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
					jointWord = new PWord(Math.min(lastWord.renderOrderNumber, word.renderOrderNumber), (lastWord.charCodes + word.charCodes), (lastWord.str + word.str), lastWord.baseline, jointWordBox, lastWord.color, Math.max(lastWord.fontSize, word.fontSize), lastWord.fontDirection, lastWord.font);
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
				
				//	check for inverse overlap
				if ((word.fontDirection == PWord.LEFT_RIGHT_FONT_DIRECTION) && (lastWord.bounds.getMinX() > word.bounds.getMinX())) {
					lastWord = word;
					if (DEBUG_MERGE_WORDS && !assessFonts) System.out.println(" --> inverse overlap");
					continue;
				}
				else if ((word.fontDirection == PWord.UPSIDE_DOWN_FONT_DIRECTION)  && (lastWord.bounds.getMaxX() < word.bounds.getMaxX())) {
					lastWord = word;
					if (DEBUG_MERGE_WORDS && !assessFonts) System.out.println(" --> inverse overlap");
					continue;
				}
				else if ((word.fontDirection == PWord.BOTTOM_UP_FONT_DIRECTION) && (lastWord.bounds.getMinY() > word.bounds.getMinY())) {
					lastWord = word;
					if (DEBUG_MERGE_WORDS && !assessFonts) System.out.println(" --> inverse overlap");
					continue;
				}
				else if ((word.fontDirection == PWord.TOP_DOWN_FONT_DIRECTION) && (lastWord.bounds.getMaxY() < word.bounds.getMaxY())) {
					lastWord = word;
					if (DEBUG_MERGE_WORDS && !assessFonts) System.out.println(" --> inverse overlap");
					continue;
				}
				
				//	check if words on same line, last word starts before current one, and vertical overlap sufficient
				if ((word.fontDirection == PWord.LEFT_RIGHT_FONT_DIRECTION) || (word.fontDirection == PWord.UPSIDE_DOWN_FONT_DIRECTION)) {
					if (word.fontSize == lastWord.fontSize) {
						float uTop = ((float) Math.max(lastWord.bounds.getMaxY(), word.bounds.getMaxY()));
						float iTop = ((float) Math.min(lastWord.bounds.getMaxY(), word.bounds.getMaxY()));
						float iBottom = ((float) Math.max(lastWord.bounds.getMinY(), word.bounds.getMinY()));
						float uBottom = ((float) Math.min(lastWord.bounds.getMinY(), word.bounds.getMinY()));
						if (((iTop - iBottom) * 10) < ((uTop - uBottom) * 9)) {
							lastWord = word;
							if (DEBUG_MERGE_WORDS && !assessFonts) System.out.println(" --> different lines (1.1)");
							continue;
						}
						if (DEBUG_MERGE_WORDS && !assessFonts) System.out.println(" --> same line (1.1): VU " + uTop + "-" + uBottom + " VI " + iTop + "-" + iBottom);
					}
					else {
						float lwCenterY = ((float) ((lastWord.bounds.getMinY() + lastWord.bounds.getMaxY()) / 2));
						float wCenterY = ((float) ((word.bounds.getMinY() + word.bounds.getMaxY()) / 2));
						if ((lwCenterY < word.bounds.getMaxY()) && (lwCenterY > word.bounds.getMinY())) { /* last word center Y inside word height */ }
						else if ((wCenterY < lastWord.bounds.getMaxY()) && (wCenterY > lastWord.bounds.getMinY())) { /* word center Y inside last word height */ }
						else {
							lastWord = word;
							if (DEBUG_MERGE_WORDS && !assessFonts) System.out.println(" --> different lines (1.2)");
							continue;
						}
						if (DEBUG_MERGE_WORDS && !assessFonts) System.out.println(" --> same line (1.2): CY " + lwCenterY + " & " + wCenterY + " L " + lastWord.bounds.getMinY() + "-" + lastWord.bounds.getMaxY() + " & " + word.bounds.getMinY() + "-" + word.bounds.getMaxY());
					}
				}
				else {
					if (word.fontSize == lastWord.fontSize) {
						float uLeft = ((float) Math.min(lastWord.bounds.getMinX(), word.bounds.getMinX()));
						float iLeft = ((float) Math.max(lastWord.bounds.getMinX(), word.bounds.getMinX()));
						float iRight = ((float) Math.min(lastWord.bounds.getMaxX(), word.bounds.getMaxX()));
						float uRight = ((float) Math.max(lastWord.bounds.getMaxX(), word.bounds.getMaxX()));
						if (((iRight - iLeft) * 10) < ((uRight - uLeft) * 9)) {
							lastWord = word;
							if (DEBUG_MERGE_WORDS && !assessFonts) System.out.println(" --> different lines (2.1)");
							continue;
						}
						if (DEBUG_MERGE_WORDS && !assessFonts) System.out.println(" --> same line (2.1): HU " + uLeft + "-" + uRight + " HI " + iLeft + "-" + iRight);
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
							if (DEBUG_MERGE_WORDS && !assessFonts) System.out.println(" --> different lines (2.2)");
							continue;
						}
						if (DEBUG_MERGE_WORDS && !assessFonts) System.out.println(" --> same line (2.2): CX " + lwCenterX + " & " + wCenterX + " L " + lastWord.bounds.getMinX() + "-" + lastWord.bounds.getMaxX() + " & " + word.bounds.getMinX() + "-" + word.bounds.getMaxX());
					}
				}
				
				//	compute cut word box, depending on font direction
				//	TODO cut in direction of lesser overlap (i.e., also consider reducing font height)
				Rectangle2D.Float cutWordBox;
				if (word.fontDirection == PWord.LEFT_RIGHT_FONT_DIRECTION)
					cutWordBox = new Rectangle2D.Float(((float) lastWord.bounds.getMinX()), ((float) lastWord.bounds.getMinY()), ((float) (word.bounds.getMinX() - lastWord.bounds.getMinX())), ((float) lastWord.bounds.getHeight()));
				else if (lastWord.fontDirection == PWord.BOTTOM_UP_FONT_DIRECTION)
					cutWordBox = new Rectangle2D.Float(((float) lastWord.bounds.getMinX()), ((float) lastWord.bounds.getMinY()), ((float) lastWord.bounds.getWidth()), ((float) (word.bounds.getMinY() - lastWord.bounds.getMinY())));
				else if (lastWord.fontDirection == PWord.TOP_DOWN_FONT_DIRECTION)
					cutWordBox = new Rectangle2D.Float(((float) lastWord.bounds.getMinX()), ((float) lastWord.bounds.getMinY()), ((float) lastWord.bounds.getWidth()), ((float) (word.bounds.getMinY() - lastWord.bounds.getMinY())));
				else if (word.fontDirection == PWord.UPSIDE_DOWN_FONT_DIRECTION)
					cutWordBox = new Rectangle2D.Float(((float) lastWord.bounds.getMinX()), ((float) lastWord.bounds.getMinY()), ((float) (word.bounds.getMinX() - lastWord.bounds.getMinX())), ((float) lastWord.bounds.getHeight()));
				else continue; // never gonna happen, but Java don't know
				
				//	cut previous word
				PWord cutWord = new PWord(lastWord.renderOrderNumber, lastWord.charCodes, lastWord.str, lastWord.baseline, cutWordBox, lastWord.color, lastWord.fontSize, lastWord.fontDirection, lastWord.font);
				cutWord.joinWithNext = lastWord.joinWithNext;
				if (DEBUG_RENDER_PAGE_CONTENT || DEBUG_MERGE_WORDS) System.out.println("Got cut word: '" + cutWord.str + "' @ " + cutWord.bounds);
				if (DEBUG_RENDER_PAGE_CONTENT || DEBUG_MERGE_WORDS) System.out.println("  overlaps with: '" + word.str + "' @ " + word.bounds);
				words.set((w-1), cutWord);
				lastWord = word;
			}
		}
//		
//		BETTER WE DO THIS AFTER GRAPHICS CLEANUP, AS IT'S BETTER TO HAVE ORIGINAL RENDER ORDER NUMBERS FOR LATTER
//		/* combine figures that are adjacent in one dimension and identical in
//		 * the other, and do so recursively, both top-down and left-right, to
//		 * also catch weirdly tiled images */
//		while ((figures != null) && (figures.size() > 1)) {
//			int figureCount = figures.size();
//			
//			//	try merging left to right
//			Collections.sort(figures, leftRightFigureOrder);
//			for (int f = 0; f < figures.size(); f++) {
//				PFigure pf = ((PFigure) figures.get(f));
//				if (DEBUG_RENDER_PAGE_CONTENT && !assessFonts) System.out.println("Checking image " + pf + " for left-right mergers");
//				PFigure mpf = null;
//				for (int cf = (f+1); cf < figures.size(); cf++) {
//					PFigure cpf = ((PFigure) figures.get(cf));
//					if (DEBUG_RENDER_PAGE_CONTENT && !assessFonts) System.out.println(" - comparing to image " + cpf);
//					if (!figureEdgeMatch(pf.bounds.getMaxY(), cpf.bounds.getMaxY())) {
//						if (DEBUG_RENDER_PAGE_CONTENT && !assessFonts) System.out.println(" ==> top edge mismatch");
//						continue;
//					}
//					if (!figureEdgeMatch(pf.bounds.getMinY(), cpf.bounds.getMinY())) {
//						if (DEBUG_RENDER_PAGE_CONTENT && !assessFonts) System.out.println(" ==> bottom edge mismatch");
//						continue;
//					}
//					if (!figureEdgeMatch(pf.bounds.getMaxX(), cpf.bounds.getMinX())) {
//						if (DEBUG_RENDER_PAGE_CONTENT && !assessFonts) System.out.println(" ==> not adjacent (" + pf.bounds.getMaxX() + " != " + cpf.bounds.getMinX() + ")");
//						continue;
//					}
//					mpf = new PFigure(pf, cpf);
//					figures.remove(cf);
//					if (DEBUG_RENDER_PAGE_CONTENT && !assessFonts) System.out.println(" ==> got merged figure " + mpf);
//					break;
//				}
//				if (mpf != null)
//					figures.set(f--, mpf);
//			}
//			
//			//	try merging top down
//			Collections.sort(figures, topDownFigureOrder);
//			for (int f = 0; f < figures.size(); f++) {
//				PFigure pf = ((PFigure) figures.get(f));
//				if (DEBUG_RENDER_PAGE_CONTENT && !assessFonts) System.out.println("Checking image " + pf + " for top-down mergers");
//				PFigure mpf = null;
//				for (int cf = (f+1); cf < figures.size(); cf++) {
//					PFigure cpf = ((PFigure) figures.get(cf));
//					if (DEBUG_RENDER_PAGE_CONTENT && !assessFonts) System.out.println(" - comparing to image " + cpf);
//					if (!figureEdgeMatch(pf.bounds.getMinX(), cpf.bounds.getMinX())) {
//						if (DEBUG_RENDER_PAGE_CONTENT && !assessFonts) System.out.println(" ==> left edge mismatch");
//						continue;
//					}
//					if (!figureEdgeMatch(pf.bounds.getMaxX(), cpf.bounds.getMaxX())) {
//						if (DEBUG_RENDER_PAGE_CONTENT && !assessFonts) System.out.println(" ==> right edge mismatch");
//						continue;
//					}
//					if (!figureEdgeMatch(pf.bounds.getMinY(), cpf.bounds.getMaxY())) {
//						if (DEBUG_RENDER_PAGE_CONTENT && !assessFonts) System.out.println(" ==> not adjacent (" + pf.bounds.getMinY() + " != " + cpf.bounds.getMaxY() + ")");
//						continue;
//					}
//					mpf = new PFigure(pf, cpf);
//					figures.remove(cf);
//					if (DEBUG_RENDER_PAGE_CONTENT && !assessFonts) System.out.println(" ==> got merged figure " + mpf);
//					break;
//				}
//				if (mpf != null)
//					figures.set(f--, mpf);
//			}
//			
//			//	nothing merged in either direction, we're done
//			if (figures.size() == figureCount)
//				break;
//		}
		
		//	tell any recursive caller where to continue with numbering
		return (pcr.nextRenderOrderNumber + 1);
	}
//	
//	private static final Comparator leftRightFigureOrder = new Comparator() {
//		public int compare(Object obj1, Object obj2) {
//			PFigure pf1 = ((PFigure) obj1);
//			PFigure pf2 = ((PFigure) obj2);
//			int c = Double.compare(pf1.bounds.getMinX(), pf2.bounds.getMinX());
//			return ((c == 0) ? Double.compare(pf2.bounds.getMaxX(), pf1.bounds.getMaxX()) : c);
//		}
//	};
//	private static final Comparator topDownFigureOrder = new Comparator() {
//		public int compare(Object obj1, Object obj2) {
//			PFigure pf1 = ((PFigure) obj1);
//			PFigure pf2 = ((PFigure) obj2);
//			int c = Double.compare(pf2.bounds.getMaxY(), pf1.bounds.getMaxY());
//			return ((c == 0) ? Double.compare(pf1.bounds.getMinY(), pf2.bounds.getMinY()) : c);
//		}
//	};
//	private static final boolean figureEdgeMatch(double e1, double e2) {
////		return (e1 == e2); // TODO_ use this to see individual values
//		return (Math.abs(e1 - e2) < 0.001); // TODO_ adjust this threshold
//	}
//	
//	private static Rectangle2D.Float rotateBounds(Rectangle2D.Float bounds, float pw, float ph, int rotate) {
//		if ((rotate == 90) || (rotate == -270)) {
//			float rx = ((float) bounds.getMinY());
//			float ry = ((float) (pw - bounds.getMaxX()));
//			return new Rectangle2D.Float(rx, ry, ((float) bounds.getHeight()), ((float) bounds.getWidth()));
//		}
//		else if ((rotate == 270) || (rotate == -90)) {
//			float rx = ((float) (ph - bounds.getMaxY()));
//			float ry = ((float) bounds.getMinX());
//			return new Rectangle2D.Float(rx, ry, ((float) bounds.getHeight()), ((float) bounds.getWidth()));
//		}
//		else if ((rotate == 180) || (rotate == -180)) {
//			float rx = ((float) (pw - bounds.getMaxX()));
//			float ry = ((float) (ph - bounds.getMaxY()));
//			return new Rectangle2D.Float(rx, ry, ((float) bounds.getWidth()), ((float) bounds.getHeight()));
//		}
//		else return bounds;
//	}
	
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
		else if (lastWord.fontDirection == PWord.UPSIDE_DOWN_FONT_DIRECTION) {
			if (lastWordIsCombiningAccent) {
				float lwCenterX = ((float) ((lastWord.bounds.getMinX() + lastWord.bounds.getMaxX()) / 2));
				return (lwCenterX < word.bounds.getMaxX());
			}
			else if (wordIsCombiningAccent) {
				float wCenterX = ((float) ((word.bounds.getMinX() + word.bounds.getMaxX()) / 2));
				return (lastWord.bounds.getMinX() < wCenterX);
			}
			else return ((word.bounds.getMaxX() - 1) > lastWord.bounds.getMinX());
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
			wordDistance = (lastWord.bounds.getMinY() - word.bounds.getMaxY());
		else if (lastWord.fontDirection == PWord.UPSIDE_DOWN_FONT_DIRECTION)
			wordDistance = (lastWord.bounds.getMinX() - word.bounds.getMaxX());
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
		if (((lastWord.fontDirection == PWord.LEFT_RIGHT_FONT_DIRECTION) || (lastWord.fontDirection == PWord.UPSIDE_DOWN_FONT_DIRECTION)) ? ((lastWordMiddleY < word.bounds.getMinY()) || (lastWordMiddleY > word.bounds.getMaxY())) : ((lastWordMiddleX < word.bounds.getMinX()) || (lastWordMiddleX > word.bounds.getMaxX()))) {
			if (DEBUG_MERGE_WORDS) System.out.println(" --> vertical alignment mismatch (1)");
			return false;
		}
		if (((lastWord.fontDirection == PWord.LEFT_RIGHT_FONT_DIRECTION) || (lastWord.fontDirection == PWord.UPSIDE_DOWN_FONT_DIRECTION)) ? ((wordMiddleY < lastWord.bounds.getMinY()) || (wordMiddleY > lastWord.bounds.getMaxY())) : ((wordMiddleX < lastWord.bounds.getMinX()) || (wordMiddleX > lastWord.bounds.getMaxX()))) {
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
		if (checkHorizontalAlignment && (lastWord.fontDirection == PWord.UPSIDE_DOWN_FONT_DIRECTION) && ((word.bounds.getMinX() > lastWord.bounds.getMinX()) || (word.bounds.getMaxX() > lastWord.bounds.getMaxX()))) {
			if (DEBUG_MERGE_WORDS) System.out.println(" --> horizontal alignment mismatch (4)");
			return false;
		}
		
		//	OK, we didn't find any red flags about these two
		return true;
	}
	
	private static final boolean DEBUG_RENDER_PAGE_CONTENT = false;
	private static final boolean DEBUG_MERGE_WORDS = false;
	
	private static float[] applyTransformationMatrices(float[] f, PcrTransformationMatrixStack tms, String indent) {
		return transform(f, tms.etm, tms.rotate, indent);
	}
	
	//	observe <userSpace> = <textSpace> * <textMatrix> * <transformationMatrix> * <pageRotation>
	private static float[] transform(float x, float y, float z, float[][] matrix, PRotate rotate, String indent) {
		float[] res = new float[3];
		for (int c = 0; c < matrix.length; c++)
			res[c] = ((matrix[c][0] * x) + (matrix[c][1] * y) + (matrix[c][2] * z));
		if (rotate.degrees == 0)
			return res;
		if (DEBUG_RENDER_PAGE_CONTENT) {
			System.out.println(indent + "Transforming [" + x + ", " + y + ", " + z + "]");
			System.out.println(indent + " ==> " + Arrays.toString(res));
		}
		res[0] -= (rotate.centerX * res[2]);
		res[1] -= (rotate.centerY * res[2]);
		float rx = res[0];
		float ry = res[1];
		if (rotate.degrees == 90) {
			res[0] = ry;
			res[1] = -rx;
			res[0] += (rotate.centerY * res[2]);
			res[1] += (rotate.centerX * res[2]);
		}
		else if (rotate.degrees == 270) {
			res[0] = -ry;
			res[1] = rx;
			res[0] += (rotate.centerY * res[2]);
			res[1] += (rotate.centerX * res[2]);
		}
		else if (rotate.degrees == 180) {
			res[0] = -rx;
			res[1] = -ry;
			res[0] += (rotate.centerX * res[2]);
			res[1] += (rotate.centerY * res[2]);
		}
		if (DEBUG_RENDER_PAGE_CONTENT) System.out.println(indent + " ==> " + Arrays.toString(res));
		return res;
	}
	private static float[] transform(float[] f, float[][] matrix, PRotate rotate, String indent) {
		return ((f.length == 3) ? transform(f[0], f[1], f[2], matrix, rotate, indent) : f);
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
	private static void printMatrix(float[][] m, String indent) {
		for (int r = 0; r < m[0].length; r++) {
			System.out.print(indent + "    ");
			for (int c = 0; c < m.length; c++)
				System.out.print(" " + m[c][r]);
			System.out.println();
		}
	}
	
	private static class PcrTransformationMatrixStack extends LinkedList {
		PRotate rotate;
		float[][] etm = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
		PcrTransformationMatrixStack(PRotate rotate) {
			this.rotate = rotate;
		}
		PcrTransformationMatrixStack(PcrTransformationMatrixStack tms) {
			this.rotate = tms.rotate;
			this.addAll(tms); // cannot do that from super class constructor (effective transformation matrix is null until latter returns)
		}
		void addFirst(float[][] tm) {
			super.addFirst(tm);
			this.etm = multiply(tm, this.etm);
		}
		void addAll(PcrTransformationMatrixStack tms) {
			super.addAll(tms);
			this.etm = multiply(tms.etm, this.etm);
		}
		PMatrix getMatrix() {
			return new PMatrix(this.etm, this.rotate);
		}
	}
	
	private static class PageContentRenderer {
		private Map resources;
		private Map colorSpaces;
		private Map graphicsStates;
		private Map fonts;
		private FontDecoderCharset fontCharSet;
		private Map xObject;
		private Map objects;
		
		private class SavedGraphicsState {
			private PdfColorSpace sgsStrokeColorSpace;
			private Color sgsStrokeColor;
			private PdfColorSpace sgsNonStrokeColorSpace;
			private Color sgsNonStrokeColor;
			private PStream sgsNonStrokePattern;
			
			private float sgsLineWidth;
			private byte sgsLineCapStyle;
			private byte sgsLineJointStyle;
			private float sgsMiterLimit;
			private List sgsDashPattern;
			private float sgsDashPatternPhase;
			
			private int sgsStrokeCompositAlpha;
			private int sgsNonStrokeCompositAlpha;
			private BlendComposite sgsBlendMode;
			private boolean sgsStrokeOverPrint;
			private boolean sgsNonStrokeOverPrint;
			private int sgsOverPrintMode;
			private boolean sgsAlphaIsShape;
			
			private LinkedList sgsClippingPathStack = new LinkedList();
			private LinkedList sgsSoftMaskStack = new LinkedList();
			
			private Object sgsFontKey;
			private PdfFont sgsFont;
			private float sgsFontSpaceWidth;
			
			private float sgsFontSize;
			private float sgsLineHeight;
			private float sgsCharSpacing;
			private float sgsWordSpacing;
			private float sgsHorizontalScaling;
			private int sgsTextRenderingMode;
			private float sgsTextRise;
			
			private PcrTransformationMatrixStack sgsTransformationMatrices;
			
			SavedGraphicsState() {
				this.sgsStrokeColorSpace = pcrStrokeColorSpace;
				this.sgsStrokeColor = pcrStrokeColor;
				this.sgsNonStrokeColorSpace = pcrNonStrokeColorSpace;
				this.sgsNonStrokeColor = pcrNonStrokeColor;
				this.sgsNonStrokePattern = pcrNonStrokePattern;
				
				this.sgsLineWidth = pcrLineWidth;
				this.sgsLineCapStyle = pcrLineCapStyle;
				this.sgsLineJointStyle = pcrLineJointStyle;
				this.sgsMiterLimit = pcrMiterLimit;
				this.sgsDashPattern = new ArrayList(pcrDashPattern);
				this.sgsDashPatternPhase = pcrDashPatternPhase;
				
				this.sgsStrokeCompositAlpha = pcrStrokeCompositAlpha;
				this.sgsNonStrokeCompositAlpha = pcrNonStrokeCompositAlpha;
				this.sgsBlendMode = pcrBlendMode;
				this.sgsStrokeOverPrint = pcrStrokeOverPrint;
				this.sgsNonStrokeOverPrint = pcrNonStrokeOverPrint;
				this.sgsOverPrintMode = pcrOverPrintMode;
				this.sgsAlphaIsShape = pcrAlphaIsShape;
				
				this.sgsClippingPathStack = new LinkedList(pcrClippingPathStack);
				this.sgsSoftMaskStack = new LinkedList(pcrSoftMaskStack);
				
				this.sgsFontKey = pcrFontKey;
				this.sgsFont = pcrFont;
				this.sgsFontSpaceWidth = pcrFontSpaceWidth;
				
				this.sgsFontSize = pcrFontSize;
				this.sgsLineHeight = pcrLineHeight;
				this.sgsCharSpacing = pcrCharSpacing;
				this.sgsWordSpacing = pcrWordSpacing;
				this.sgsHorizontalScaling = pcrHorizontalScaling;
				this.sgsTextRenderingMode = pcrTextRenderingMode;
				this.sgsTextRise = pcrTextRise;
				
				this.sgsTransformationMatrices = new PcrTransformationMatrixStack(pcrTransformationMatrices);
			}
			
			void restore() {
				pcrStrokeColorSpace = this.sgsStrokeColorSpace;
				pcrStrokeColor = this.sgsStrokeColor;
				pcrNonStrokeColorSpace = this.sgsNonStrokeColorSpace;
				pcrNonStrokeColor = this.sgsNonStrokeColor;
				pcrNonStrokePattern = this.sgsNonStrokePattern;
				
				pcrLineWidth = this.sgsLineWidth;
				pcrLineCapStyle = this.sgsLineCapStyle;
				pcrLineJointStyle = this.sgsLineJointStyle;
				pcrMiterLimit = this.sgsMiterLimit;
				pcrDashPattern = this.sgsDashPattern;
				pcrDashPatternPhase = this.sgsDashPatternPhase;
				
				pcrStrokeCompositAlpha = this.sgsStrokeCompositAlpha;
				pcrNonStrokeCompositAlpha = this.sgsNonStrokeCompositAlpha;
				pcrBlendMode = this.sgsBlendMode;
				pcrStrokeOverPrint = this.sgsStrokeOverPrint;
				pcrNonStrokeOverPrint = this.sgsNonStrokeOverPrint;
				pcrOverPrintMode = this.sgsOverPrintMode;
				pcrAlphaIsShape = this.sgsAlphaIsShape;
				
				pcrClippingPathStack = this.sgsClippingPathStack;
				pcrSoftMaskStack = this.sgsSoftMaskStack;
				
				pcrFontKey = this.sgsFontKey;
				pcrFont = this.sgsFont;
				pcrFontSpaceWidth = this.sgsFontSpaceWidth;
				
				pcrFontSize = this.sgsFontSize;
				pcrLineHeight = this.sgsLineHeight;
				pcrCharSpacing = this.sgsCharSpacing;
				pcrWordSpacing = this.sgsWordSpacing;
				pcrHorizontalScaling = this.sgsHorizontalScaling;
				pcrTextRenderingMode = this.sgsTextRenderingMode;
				pcrTextRise = this.sgsTextRise;
				
				pcrTransformationMatrices = this.sgsTransformationMatrices;
			}
		}
		
		private PdfColorSpace pcrStrokeColorSpace = PdfColorSpace.getColorSpace("DeviceGray");
		private Color pcrStrokeColor = Color.BLACK;
		private PdfColorSpace pcrNonStrokeColorSpace = PdfColorSpace.getColorSpace("DeviceGray");
		private Color pcrNonStrokeColor = Color.BLACK;
		private PStream pcrNonStrokePattern = null;
		
		private PPath pcrPath = null;
		private float pcrLineWidth = 1.0f;
		private byte pcrLineCapStyle = ((byte) 0);
		private byte pcrLineJointStyle = ((byte) 0);
		private float pcrMiterLimit = 10.0f;
		private List pcrDashPattern = new ArrayList();
		private float pcrDashPatternPhase = 0;
		
		private int pcrStrokeCompositAlpha = 255;
		private int pcrNonStrokeCompositAlpha = 255;
		private BlendComposite pcrBlendMode = null;
		private boolean pcrStrokeOverPrint = false;
		private boolean pcrNonStrokeOverPrint = false;
		private int pcrOverPrintMode = 0;
		private boolean pcrAlphaIsShape = false;
		
		private LinkedList pcrClippingPathStack = new LinkedList();
		private LinkedList pcrSoftMaskStack = new LinkedList();
		private ArrayList pcrClipWords = null;
		
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
		private int pcrTextRenderingMode = 0;
		private float pcrTextRise = 0;
		
		private float[][] textMatrix = new float[3][3];
		private float[][] lineMatrix = new float[3][3];
		private float pendingSpace = 0;
		
		private PcrTransformationMatrixStack pcrTransformationMatrices;
		
		private LinkedList graphicsStateStack = new LinkedList();
		
		private List words;
		private List figures;
		private List paths;
		
		private boolean assessFonts;
		private boolean wordsAreOcr;
		
		int nextRenderOrderNumber = 0;
		
		private String indent;
		
		PageContentRenderer(int firstRenderOrderNumber, PRotate rotate, PcrTransformationMatrixStack inheritedTransformationMatrices, Map resources, Map fonts, FontDecoderCharset fontCharSet, Map xObject, Map objects, List words, List figures, List paths, boolean assessFonts, boolean wordsAreOcr, String indent) {
			this.nextRenderOrderNumber = firstRenderOrderNumber;
			this.pcrTransformationMatrices = new PcrTransformationMatrixStack(rotate);
			if (inheritedTransformationMatrices != null)
				this.pcrTransformationMatrices.addAll(inheritedTransformationMatrices);
			this.resources = resources;
			this.colorSpaces = ((Map) getObject(resources, "ColorSpace", objects));
			this.graphicsStates = ((Map) getObject(resources, "ExtGState", objects));
			this.fonts = fonts;
			this.fontCharSet = fontCharSet;
			this.xObject = xObject;
			this.objects = objects;
			this.words = words;
			this.figures = figures;
			this.paths = paths;
			this.assessFonts = assessFonts;
			this.wordsAreOcr = wordsAreOcr;
			for (int c = 0; c < this.textMatrix.length; c++) {
				Arrays.fill(this.textMatrix[c], 0);
				this.textMatrix[c][c] = 1;
			}
			cloneValues(this.textMatrix, this.lineMatrix);
			this.indent = indent;
		}
		
		private void doBT(LinkedList stack) {
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(this.indent + " --> start text");
			for (int c = 0; c < this.textMatrix.length; c++) {
				Arrays.fill(this.textMatrix[c], 0);
				this.textMatrix[c][c] = 1;
			}
			cloneValues(this.textMatrix, this.lineMatrix);
		}
		
		private void doET(LinkedList stack) {
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(this.indent + " --> end text");
			if (this.pcrClipWords == null)
				return;
			PPath clipWordPath = new PPath(this.nextRenderOrderNumber++);
			for (int w = 0; w < this.pcrClipWords.size(); w++)
				clipWordPath.doClipWord((PWord) this.pcrClipWords.get(w));
			this.pcrClipWords = null;
			this.pcrClippingPathStack.addLast(clipWordPath);
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(this.indent + " --> word clipping restricted to " + clipWordPath.getBounds());
		}
		
		private void doBMC(LinkedList stack) {
			Object n = stack.removeLast();
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(this.indent + " --> start marked content: " + n);
		}
		
		private void doBDC(LinkedList stack) {
			Object d = stack.removeLast();
			Object n = stack.removeLast();
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(this.indent + " --> start marked content with dictionary: " + n + " - " + d);
		}
		
		private void doEMC(LinkedList stack) {
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(this.indent + " --> end marked content");
		}
		
		private void doMP(LinkedList stack) {
			Object n = stack.removeLast();
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(this.indent + " --> mark point: " + n);
		}
		
		private void doDP(LinkedList stack) {
			Object d = stack.removeLast();
			Object n = stack.removeLast();
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(this.indent + " --> mark point with dictionary: " + n + " - " + d);
		}
		
		private void doq(LinkedList stack) {
			this.graphicsStateStack.addLast(new SavedGraphicsState());
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(this.indent + " --> save graphics state");
		}
		
		private void doQ(LinkedList stack) {
			if (this.graphicsStateStack.isEmpty()) {
				if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
					System.out.println(this.indent + " --> cannot restore graphics state - empty stack");
				return;
			}
			((SavedGraphicsState) this.graphicsStateStack.removeLast()).restore();
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts) {
				System.out.println(this.indent + " --> restore graphics state:");
				for (Iterator tmit = this.pcrTransformationMatrices.iterator(); tmit.hasNext();) {
					float[][] tm = ((float[][]) tmit.next());
					printMatrix(tm, this.indent);
					if (tmit.hasNext())
						System.out.println();
				}
			}
			this.computeEffectiveFontSizeAndDirection();
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(this.indent + " --> text rendering mode is " + this.pcrTextRenderingMode);
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
				System.out.println(this.indent + " --> transformation matrix stack set to " + this.pcrTransformationMatrices.size() + " matrices");
//				for (Iterator tmit = this.pcrTransformationMatrices.iterator(); tmit.hasNext();) {
//					float[][] tm = ((float[][]) tmit.next());
//					printMatrix(tm);
//					if (tmit.hasNext())
//						System.out.println();
//				}
				System.out.println(this.indent + " --> effective transformation matrix:");
				printMatrix(this.pcrTransformationMatrices.etm, this.indent);
			}
			this.computeEffectiveFontSizeAndDirection();
		}
		
		private float[] applyTransformationMatrices(float[] f) {
			return PdfParser.applyTransformationMatrices(f, this.pcrTransformationMatrices, this.indent);
		}
		
		// d i j J M w gs
		private void dod(LinkedList stack)  {
			this.pcrDashPatternPhase = ((Number) stack.removeLast()).floatValue();
			this.pcrDashPattern = ((List) stack.removeLast());
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(this.indent + " --> dash pattern " + this.pcrDashPattern + " at phase " + this.pcrDashPatternPhase);
		}
		
		private void doi(LinkedList stack)  {
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(this.indent + " --> flatness");
		}
		
		private void doj(LinkedList stack) {
			this.pcrLineJointStyle = ((Number) stack.removeLast()).byteValue();
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(this.indent + " --> line join style " + this.pcrLineJointStyle);
		}
		
		private void doJ(LinkedList stack) {
			this.pcrLineCapStyle = ((Number) stack.removeLast()).byteValue();
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(this.indent + " --> line cap style " + this.pcrLineCapStyle);
		}
		
		private void doM(LinkedList stack) {
			this.pcrMiterLimit = ((Number) stack.removeLast()).floatValue();
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(this.indent + " --> line miter limit " + this.pcrMiterLimit);
		}
		
		private void dow(LinkedList stack) {
			this.pcrLineWidth = ((Number) stack.removeLast()).floatValue();
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(this.indent + " --> line width " + this.pcrLineWidth);
		}
		
		private void dom(LinkedList stack) {
			if (this.pcrPath == null)
				this.pcrPath = new PPath(this.nextRenderOrderNumber++);
			this.pcrPath.dom(stack, this.pcrTransformationMatrices);
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(this.indent + " --> move to " + this.pcrPath.x + "/" + this.pcrPath.y);
		}
		
		private void dol(LinkedList stack) {
			this.pcrPath.dol(stack, this.pcrTransformationMatrices);
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(this.indent + " --> line to " + this.pcrPath.x + "/" + this.pcrPath.y);
		}
		private void doh(LinkedList stack) {
			this.pcrPath.doh(stack);
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(this.indent + " --> path closed to " + this.pcrPath.x + "/" + this.pcrPath.y);
		}
		private void dore(LinkedList stack) {
			if (this.pcrPath == null)
				this.pcrPath = new PPath(this.nextRenderOrderNumber++);
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(this.indent + " --> start rectangle at " + this.pcrPath.x + "/" + this.pcrPath.y);
			this.pcrPath.dore(stack, this.pcrTransformationMatrices);
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(this.indent + " --> finished rectangle at " + this.pcrPath.x + "/" + this.pcrPath.y);
		}
		
		private void doc(LinkedList stack) {
			this.pcrPath.doc(stack, this.pcrTransformationMatrices);
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(this.indent + " --> curve to " + this.pcrPath.x + "/" + this.pcrPath.y);
		}
		private void dov(LinkedList stack) {
			this.pcrPath.dov(stack, this.pcrTransformationMatrices);
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(this.indent + " --> curve to " + this.pcrPath.x + "/" + this.pcrPath.y);
		}
		private void doy(LinkedList stack) {
			this.pcrPath.doy(stack, this.pcrTransformationMatrices);
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(" --> curve to " + this.pcrPath.x + "/" + this.pcrPath.y);
		}
		
		private void doS(LinkedList stack) {
			PPath[] clipPaths = this.getClipPaths();
			this.pcrPath.stroke(clipPaths, this.getStrokeColor(), this.pcrLineWidth, this.pcrLineCapStyle, this.pcrLineJointStyle, this.pcrMiterLimit, this.pcrDashPattern, this.pcrDashPatternPhase, this.pcrTransformationMatrices);
			int clipReason = this.getClipReason(this.pcrPath.getBounds(), clipPaths, this.getStrokeColor());
			if ((this.paths != null) && (clipReason == -1))
				this.paths.add(this.pcrPath);
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts) {
				System.out.println(" --> path at RON " + this.pcrPath.renderOrderNumber + " stroked inside " + this.pcrPath.getBounds());
				if (clipPaths == null)
					System.out.println(this.indent + " --> clip bounds not set");
				else {
					Rectangle2D clipBounds = clipPaths[0].getBounds();
					for (int cp = 1; cp < clipPaths.length; cp++)
						clipBounds = clipBounds.createIntersection(clipPaths[cp].getBounds());
					System.out.println(this.indent + " --> clip bounds are " + clipBounds);
				}
				System.out.println(this.indent + " --> soft mask stack is " + this.pcrSoftMaskStack);
			}
			this.pcrPath = null;
		}
		private void dos(LinkedList stack) {
			this.doh(stack);
			this.doS(stack);
		}
		
		private void dof(LinkedList stack, ProgressMonitor pm) {
			this.doh(stack);
			this.doFillPath(stack, false, pm);
		}
		private void doF(LinkedList stack, ProgressMonitor pm) {
			this.dof(stack, pm);
		}
		private void dofasterisk(LinkedList stack, ProgressMonitor pm) {
			this.doh(stack);
			this.doFillPath(stack, true, pm);
		}
		private void doFillPath(LinkedList stack, boolean fillEvenOdd, ProgressMonitor pm) {
			if (this.pcrNonStrokePattern == null) {
				PPath[] clipPaths = this.getClipPaths();
				this.pcrPath.fill(clipPaths, this.getNonStrokeColor(), fillEvenOdd);
				int clipReason = this.getClipReason(this.pcrPath.getBounds(), clipPaths, this.getNonStrokeColor());
				if ((this.paths != null) && (clipReason == -1))
					this.paths.add(this.pcrPath);
				if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts) {
					System.out.println(this.indent + " --> path at RON " + this.pcrPath.renderOrderNumber + " filled inside " + this.pcrPath.getBounds());
					if (clipPaths == null)
						System.out.println(this.indent + " --> clip bounds not set");
					else {
						Rectangle2D clipBounds = clipPaths[0].getBounds();
						for (int cp = 1; cp < clipPaths.length; cp++)
							clipBounds = clipBounds.createIntersection(clipPaths[cp].getBounds());
						System.out.println(this.indent + " --> clip bounds are " + clipBounds);
					}
					System.out.println(this.indent + " --> soft mask stack is " + this.pcrSoftMaskStack);
					System.out.println(this.indent + " --> non-stroking composite alpha is " + this.pcrNonStrokeCompositAlpha);
					System.out.println(this.indent + " --> non-stroking overprint is " + this.pcrNonStrokeOverPrint);
				}
				this.pcrPath = null;
			}
			else {
				if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
					System.out.println(this.indent + " --> path at RON " + this.pcrPath.renderOrderNumber + " filled with pattern");
				this.doq(stack); // save graphics state
				this.doW(stack); // add current path to clip path to clip pattern drawing
				this.pcrPath = null; // nil out path, we're done with this one (W+n is typical clipping sequence)
				PPath[] clipPaths = this.getClipPaths();
				Rectangle2D clipBounds = clipPaths[0].getBounds();
				for (int cp = 1; cp < clipPaths.length; cp++)
					clipBounds = clipBounds.createIntersection(clipPaths[cp].getBounds());
				if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
					System.out.println(this.indent + "   - visible area is " + clipBounds);
				if ((0 < clipBounds.getWidth()) && (0 < clipBounds.getHeight())) try {
					float xStep = ((Number) this.pcrNonStrokePattern.params.get("XStep")).floatValue();
					float yStep = ((Number) this.pcrNonStrokePattern.params.get("YStep")).floatValue();
					float xStart = ((float) ((xStep < 0) ? clipBounds.getMaxX() : clipBounds.getMinX()));
					float yStart = ((float) ((yStep < 0) ? clipBounds.getMaxY() : clipBounds.getMinY()));
					if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
						System.out.println(this.indent + " === START PATTERN FILLING === ");
					for (float x = xStart; ((xStep < 0) ? (clipBounds.getMinX() < x) : (x < clipBounds.getMaxX())); x += xStep) {
						if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
							System.out.println(this.indent + " - drawing pattern column at " + x);
						for (float y = yStart; ((yStep < 0) ? (clipBounds.getMinY() < y) : (y < clipBounds.getMaxY())); y += yStep) {
							if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
								System.out.println(this.indent + " - drawing pattern at " + x + "/" + y);
							this.doq(stack); // store graphics state for before translating to next pattern root point
							//	TODO measure [0, 0, 1] and [1, 1, 1] to get an idea wher we are
							//	TODO add transformation to get pattern in place
							float[][] nTm = new float[3][3]; // TODO
							nTm[2][2] = 1;
							nTm[1][2] = y;
							nTm[0][2] = x;
							nTm[2][1] = 0;
							nTm[1][1] = 1;
							nTm[0][1] = 0;
							nTm[2][0] = 0;
							nTm[1][0] = 0;
							nTm[0][0] = 1;
							this.pcrTransformationMatrices.addFirst(nTm); // no need for recomputing fornt size and text direction, we merely translate, but don't scale or rotate
							this.doDoForm(this.pcrNonStrokePattern, false, pm); // render whole thing (behaves like form)
							this.doQ(stack); // restore graphics state to translate back
						}
					}
					if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
						System.out.println(this.indent + " === PATTERN FILLING DONE === ");
				}
				catch (Exception e) {
					System.out.println("Error filling with pattern color: " + e);
					e.printStackTrace(System.out);
				}
				this.doQ(stack); // restore graphics state
			}
		}
		
		private void doB(LinkedList stack) {
			this.doFillAndStrokePath(stack, false);
		}
		private void doBasterisk(LinkedList stack) {
			this.doFillAndStrokePath(stack, true);
		}
		private void dob(LinkedList stack) {
			this.doh(stack);
			this.doB(stack);
		}
		private void dobasterisk(LinkedList stack) {
			this.doh(stack);
			this.doBasterisk(stack);
		}
		private void doFillAndStrokePath(LinkedList stack, boolean fillEvenOdd) {
			PPath[] clipPaths = this.getClipPaths();
			this.pcrPath.fillAndStroke(clipPaths, this.getNonStrokeColor(), fillEvenOdd, this.getStrokeColor(), this.pcrLineWidth, this.pcrLineCapStyle, this.pcrLineJointStyle, this.pcrMiterLimit, this.pcrDashPattern, this.pcrDashPatternPhase, this.pcrTransformationMatrices);
			/*
TODO Filling with patterns:
- in page content renderer, keep corresponding pattern object (map) at least for non-stroking color
  ==> also make sure to store with graphics state
- make sure only either one not null at any time
  ==> best make sure to always set both when setting colors
- filling with pattern:
  - add current path proper to clip path
  - compute convex hull of current path
  - null out current path (it's done with)
  - store graphics state
  - run over convex hull of current path in 2D nested for loop (with steps pattern XStep and YStep) bottom-left to top-right ...
  - ... and execute pattern content for each position ...
  - ... via recursive call to runPageContent(), just like for forms ...
  - ... adjusting matrix and bounds just the same way
    ==> still copy doDoForm() method, though, as there might be differences ...
    ==> ... most likely to drawPatternTile() method or something
  - use object or resource map defaulting to page wide one, but adding pattern resources
    ==> somehow need to make sure to properly resolve that XObject wrapped global reference ...
  - collect everything drawn this way in new path ...
  - ... and store that path if not empty
    ==> should eliminate that Rube-Goldberg-white page background
  - restore graphics state
			 */
			int clipReason = this.getClipReason(this.pcrPath.getBounds(), clipPaths, this.getNonStrokeColor());
			if ((this.paths != null) && (clipReason == -1))
				this.paths.add(this.pcrPath);
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts) {
				System.out.println(this.indent + " --> path at RON " + this.pcrPath.renderOrderNumber + " filled and stroked inside " + this.pcrPath.getBounds());
				if (clipPaths == null)
					System.out.println(this.indent + " --> clip bounds not set");
				else {
					Rectangle2D clipBounds = clipPaths[0].getBounds();
					for (int cp = 1; cp < clipPaths.length; cp++)
						clipBounds = clipBounds.createIntersection(clipPaths[cp].getBounds());
					System.out.println(this.indent + " --> clip bounds are " + clipBounds);
				}
				System.out.println(this.indent + " --> soft mask stack is " + this.pcrSoftMaskStack);
			}
			this.pcrPath = null;
		}
		
		private void doW(LinkedList stack) {
			this.pcrClippingPathStack.addLast(this.pcrPath);
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(this.indent + " --> clipping restricted to " + this.pcrPath.getBounds());
		}
		private void doWasterisk(LinkedList stack) {
			this.doW(stack);
		}
		private PPath[] getClipPaths() {
			return (this.pcrClippingPathStack.isEmpty() ? null : ((PPath[]) this.pcrClippingPathStack.toArray(new PPath[this.pcrClippingPathStack.size()])));
		}
		private int getClipReason(Rectangle2D bounds, PPath[] clipPaths, Color clipWordColor) {
			boolean isWordClipped = false;
			if (clipPaths != null) {
				for (int cp = 0; cp < clipPaths.length; cp++) {
					Rectangle2D cpBounds = clipPaths[cp].getBounds();
					if (!bounds.intersects(cpBounds))
						return 1; // would not have any effect on clipping words, either
					if (clipPaths[cp].clipWords != null) {
						for (int cw = 0; cw < clipPaths[cp].clipWords.size(); cw++) {
							PWord cpw = ((PWord) clipPaths[cp].clipWords.get(cw));
							if (cpw.bounds.intersects(bounds)) {
								if (this.assessFonts)
									this.words.add(new PWord(cpw.renderOrderNumber, cpw.charCodes, cpw.baseline, cpw.bounds, clipWordColor, cpw.fontSize, cpw.fontDirection, cpw.font));
								else this.words.add(new PWord(cpw.renderOrderNumber, cpw.charCodes, cpw.str, cpw.baseline, cpw.bounds, clipWordColor, cpw.fontSize, cpw.fontDirection, cpw.font));
								clipPaths[cp].clipWords.remove(cw--);
							}
						}
						isWordClipped = true;
					}
				}
			}
			return (isWordClipped ? 0 : -1);
		}
		private PFigure[] getSoftMasks() {
			return (this.pcrSoftMaskStack.isEmpty() ? null : ((PFigure[]) this.pcrSoftMaskStack.toArray(new PFigure[this.pcrSoftMaskStack.size()])));
		}
		
		private void don(LinkedList stack) {
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts && (this.pcrPath != null))
				System.out.println(this.indent + " --> path nilled inside " + this.pcrPath.getBounds());
			this.pcrPath = null;
		}
		
		private void dogs(LinkedList stack, ProgressMonitor pm) {
			Object gsKey = stack.removeLast();
			Map extGraphicsState = ((Map) getObject(this.graphicsStates, gsKey.toString(), this.objects));
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(this.indent + " --> set graphics state to " + gsKey + ": " + extGraphicsState);
			
			//	get composite alphas and blend mode from external graphics state
			//	TODO also add parameters below (as the need arises)
			if (extGraphicsState == null)
				return;
			Number sCa = ((Number) extGraphicsState.get("CA"));
			if (sCa != null)
				this.pcrStrokeCompositAlpha = Math.round(sCa.floatValue() * 255);
			Number nsCa = ((Number) extGraphicsState.get("ca")); 
			if (nsCa != null)
				this.pcrNonStrokeCompositAlpha = Math.round(nsCa.floatValue() * 255);
			Object bm = extGraphicsState.get("BM");
			if (bm == null) {}
			else if ("Normal".equals(bm.toString()))
				this.pcrBlendMode = null;
			else this.pcrBlendMode = BlendComposite.getInstance(bm.toString());
			Object sOp = extGraphicsState.get("OP");
			if (sOp != null)
				this.pcrStrokeOverPrint = "true".equals(sOp.toString());
			Object nsOp = extGraphicsState.get("op");
			if (nsOp != null)
				this.pcrNonStrokeOverPrint = "true".equals(nsOp.toString());
			Number opm = ((Number) extGraphicsState.get("OPM")); 
			if (opm != null)
				this.pcrOverPrintMode = opm.intValue();
			Object aisOp = extGraphicsState.get("AIS");
			if (aisOp != null)
				this.pcrAlphaIsShape = "true".equals(aisOp.toString());
			
			Number lw = ((Number) extGraphicsState.get("LW"));
			if (lw != null)
				this.pcrLineWidth = Math.round(lw.floatValue() * 255);
			Number lc = ((Number) extGraphicsState.get("LC"));
			if (lc != null)
				this.pcrLineCapStyle = lc.byteValue();
			Number lj = ((Number) extGraphicsState.get("LJ"));
			if (lj != null)
				this.pcrLineJointStyle = lj.byteValue();
			Number ml = ((Number) extGraphicsState.get("ML"));
			if (ml != null)
				this.pcrMiterLimit = lj.floatValue();
			List d = ((List) extGraphicsState.get("D"));
			if ((d != null) && (((List) d).size() == 2)) {
				this.pcrDashPattern = ((List) d.get(0));
				this.pcrDashPatternPhase = ((Number) d.get(1)).floatValue();
			}
			
			//	render form SMask just for getting a look ... TODO deactivate this !!!
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts) {
				Object sMask = dereference(extGraphicsState.get("SMask"), this.objects);
				System.out.println(this.indent + " --> SMask is " + sMask);
				if (sMask instanceof Map) {
					Object smGroup = dereference(((Map) sMask).get("G"), this.objects);
					System.out.println(this.indent + " --> Group is " + smGroup);
					Object backgroundColor = dereference(((Map) sMask).get("BC"), this.objects);
					System.out.println(this.indent + " --> Background color is " + backgroundColor);
					if (smGroup instanceof PStream) try {
						this.doDoForm(((PStream) smGroup), true, pm);
						if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
							System.out.println(this.indent + " ==> soft mask stack is " + this.pcrSoftMaskStack);
					}
					catch (IOException ioe) {
						ioe.printStackTrace(System.out);
					}
				}
			}
			/* TODO observe external GS parameters (simply write to own state parameters):
RI	name (Optional; PDF 1.3) The name of the rendering intent (see "Rendering Intents" on page 230).
OP	boolean (Optional) A flag specifying whether to apply overprint (see Section 4.5.6, "Overprint Control"). In PDF 1.2 and earlier, there is a single overprint parameter that applies to all painting operations. Beginning with PDF 1.3, there are two separate overprint parameters: one for stroking and one for all other painting operations. Specifying an OP entry sets both parameters unless there is also an op entry in the same graphics state parameter dictionary, in which case the OP entry sets only the overprint parameter for stroking.
op	boolean (Optional; PDF 1.3) A flag specifying whether to apply overprint (see Section 4.5.6, "Overprint Control") for painting operations other than stroking. If this entry is absent, the OP entry, if any, sets this parameter.
Font	array (Optional; PDF 1.3) An array of the form [font size], where font is an indirect reference to a font dictionary and size is a number expressed in text space units. These two objects correspond to the operands of the Tf operator (see Section 5.2, "Text State Parameters and Operators"); however, the first operand is an indirect object reference instead of a resource name.
BG	function (Optional) The black-generation function, which maps the interval [0.0 1.0] to the interval [0.0 1.0] (see Section 6.2.3, "Conversion from DeviceRGB to DeviceCMYK").
BG2	function or name (Optional; PDF 1.3) Same as BG except that the value may also be the name Default, denoting the black-generation function that was in effect at the start of the page. If both BG and BG2 are present in the same graphics state parameter dictionary, BG2 takes precedence.
UCR	function (Optional) The undercolor-removal function, which maps the interval [0.0 1.0] to the interval [-1.0 1.0] (see Section 6.2.3, "Conversion from DeviceRGB to DeviceCMYK").
UCR2	function or name (Optional; PDF 1.3) Same as UCR except that the value may also be the name Default, denoting the undercolor-removal function that was in effect at the start of the page. If both UCR and UCR2 are present in the same graphics state parameter dictionary, UCR2 takes precedence.
TR	function, array, or name (Optional) The transfer function, which maps the interval [0.0 1.0] to the interval [0.0 1.0] (see Section 6.3, "Transfer Functions"). The value is either a single function (which applies to all process colorants) or an array of four functions (which apply to the process colorants individually). The name Identity may be used to represent the identity function.
TR2	function, array, or name (Optional; PDF 1.3) Same as TR except that the value may also be the name Default, denoting the transfer function that was in effect at the start of the page. If both TR and TR2 are present in the same graphics state parameter dictionary, TR2 takes precedence.
HT	dictionary, stream, or name (Optional) The halftone dictionary or stream (see Section 6.4, "Halftones") or the name Default, denoting the halftone that was in effect at the start of the page.
FL	number (Optional; PDF 1.3) The flatness tolerance (see Section 6.5.1, "Flatness Tolerance").
SM	number (Optional; PDF 1.3) The smoothness tolerance (see Section 6.5.2, "Smoothness Tolerance").
SA	boolean (Optional) A flag specifying whether to apply automatic stroke adjustment (see Section 6.5.4, "Automatic Stroke Adjustment").
SMask	dictionary or name (Optional; PDF 1.4) The current soft mask, specifying the mask shape or mask opacity values to be used in the transparent imaging model (see "Source Shape and Opacity" on page 495 and "Mask Shape and Opacity" on page 518).
	Note: Although the current soft mask is sometimes referred to as a "soft clip," altering it with the gs operator completely replaces the old value with the new one, rather than intersecting the two as is done with the current clipping path parameter (see Section 4.4.3, "Clipping Path Operators").
AIS	boolean (Optional; PDF 1.4) The alpha source flag ("alpha is shape"), specifying whether the current soft mask and alpha constant are to be interpreted as shape values (true) or opacity values (false).
TK	boolean (Optional; PDF 1.4) The text knockout flag, which determines the behavior of overlapping glyphs within a text object in the transparent imaging model (see Section 5.2.7, "Text Knockout").
			 *
			 * ==> TODO also add all these suckers to saved graphics state
			 */
		}
		
		private void doCS(LinkedList stack) {
			this.pcrStrokeColorSpace = this.getColorSpace(stack.removeLast().toString());
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(this.indent + " --> stroking color space " + this.pcrStrokeColorSpace.name);
			this.pcrStrokeColor = Color.BLACK;
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(this.indent + " --> stroking color " + this.pcrNonStrokeColor);
		}
		private void docs(LinkedList stack) {
			this.pcrNonStrokeColorSpace = this.getColorSpace(stack.removeLast().toString());
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(this.indent + " --> non-stroking color space " + this.pcrNonStrokeColorSpace.name);
			this.pcrNonStrokeColor = Color.BLACK;
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(this.indent + " --> non-stroking color " + this.pcrNonStrokeColor);
		}
		private PdfColorSpace getColorSpace(String csName) {
			PdfColorSpace cs = PdfColorSpace.getColorSpace(csName);
			if (cs != null)
				return cs;
			if ((this.colorSpaces != null) && (this.colorSpaces.get(csName) != null)) {
				Object csObj = getObject(this.colorSpaces, csName, this.objects);
				System.out.println(this.indent + "Got color space '" + csName + "': " + csObj);
				if (csObj instanceof List)
					return PdfColorSpace.getColorSpace(csObj, this.objects);
				else if (csObj != null)
					return this.getColorSpace(csObj.toString());
				else throw new RuntimeException("Strange Color Space '" + csName + "': " + csObj);
			}
			else throw new RuntimeException("Unknown Color Space '" + csName + "'");
		}
		
		private void doSCN(LinkedList stack) {
			if ("Pattern".equals(this.pcrStrokeColorSpace.name)) {
				Object pRef = stack.removeLast();
				Map pPatterns = ((Map) PdfParser.dereference(this.resources.get("Pattern"), this.objects));
				Object pObj = dereference(pPatterns.get(pRef), this.objects);
				System.out.println(this.indent + "Pattern " + pRef + " resolved to " + pObj);
				try {
					PStream pStream = ((PStream) pObj);
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					decode(pStream.params.get("Filter"), pStream.bytes, pStream.params, baos, this.objects);
					System.out.println(this.indent + new String(baos.toByteArray()));
				}
				catch (Exception e) {
					e.printStackTrace(System.out);
				}
				//	TODO treat this as repeated form-style Do command on stroking
				this.pcrStrokeColor = Color.CYAN; // using fallback for now ...
				//	do NOT factor in GS composite alpha here, as GS might change
				if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
					System.out.println(this.indent + " --> stroking color " + this.pcrNonStrokeColor);
			}
			else {
				this.pcrStrokeColor = this.pcrStrokeColorSpace.getColor(stack, ((DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts) ? "" : null));
				//	do NOT factor in GS composit alpha here, as GS might change
				if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
					System.out.println(this.indent + " --> stroking color " + this.pcrStrokeColor + ((this.pcrStrokeColor == null) ? "" : (" with alpha " + this.pcrStrokeColor.getAlpha())));
			}
		}
		private void doscn(LinkedList stack) {
			if ("Pattern".equals(this.pcrNonStrokeColorSpace.name)) {
				Object pRef = stack.removeLast();
				Map pPatterns = ((Map) PdfParser.dereference(this.resources.get("Pattern"), this.objects));
				Object pObj = dereference(pPatterns.get(pRef), this.objects);
				System.out.println(this.indent + "Pattern " + pRef + " resolved to " + pObj);
				try {
					PStream pStream = ((PStream) pObj);
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					decode(pStream.params.get("Filter"), pStream.bytes, pStream.params, baos, this.objects);
					System.out.println(this.indent + new String(baos.toByteArray()));
					this.pcrNonStrokeColor = null;
					this.pcrNonStrokePattern = new PStream(pStream.params, baos.toByteArray());
				}
				catch (Exception e) {
					System.out.println("Error setting pattern color: " + e);
					e.printStackTrace(System.out);
					this.pcrNonStrokeColor = Color.MAGENTA; // using fallback for now ...
					this.pcrNonStrokePattern = null;
				}
//				//	TODOne treat this as repeated form-style Do command on filling
//				this.pcrNonStrokeColor = Color.MAGENTA; // using fallback for now ...
				//	do NOT factor in GS composite alpha here, as GS might change
				if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
					System.out.println(this.indent + " --> non-stroking color " + this.pcrNonStrokeColor + ((this.pcrNonStrokeColor == null) ? "" : (" with alpha " + this.pcrNonStrokeColor.getAlpha())));
			}
			else {
				this.pcrNonStrokeColor = this.pcrNonStrokeColorSpace.getColor(stack, ((DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts) ? "" : null));
				//	do NOT factor in GS composite alpha here, as GS might change
				if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
					System.out.println(this.indent + " --> non-stroking color " + this.pcrNonStrokeColor + ((this.pcrNonStrokeColor == null) ? "" : (" with alpha " + this.pcrNonStrokeColor.getAlpha())));
			}
		}
		
		private Color getStrokeColor() {
			if (this.pcrStrokeCompositAlpha == 255)
				return this.pcrStrokeColor;
			return this.getCompositeAlphaColor(this.pcrStrokeColor, this.pcrStrokeCompositAlpha);
		}
		
		private Color getNonStrokeColor() {
			if (this.pcrNonStrokePattern != null)
				return null; // we're in pattern mode ...
			if (this.pcrNonStrokeCompositAlpha == 255)
				return this.pcrNonStrokeColor;
			return this.getCompositeAlphaColor(this.pcrNonStrokeColor, this.pcrNonStrokeCompositAlpha);
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
				System.out.println(this.indent + " --> char spacing " + this.pcrCharSpacing);
		}
		
		private void doTw(LinkedList stack) {
			this.pcrWordSpacing = ((Number) stack.removeLast()).floatValue();
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(this.indent + " --> word spacing " + this.pcrWordSpacing);
		}
		
		private void doTz(LinkedList stack) {
			this.pcrHorizontalScaling = ((Number) stack.removeLast()).floatValue();
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(this.indent + " --> horizontal scaling " + this.pcrHorizontalScaling);
		}
		
		private void doTL(LinkedList stack) {
			this.pcrLineHeight = ((Number) stack.removeLast()).floatValue();
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(this.indent + " --> leading (line height) " + this.pcrLineHeight);
		}
		
		private void doTf(LinkedList stack) {
			this.pcrFontSize = ((Number) stack.removeLast()).floatValue();
			this.pcrFontKey = stack.removeLast();
			if (DEBUG_RENDER_PAGE_CONTENT)
				System.out.println(this.indent + " --> font key set to " + this.pcrFontKey);
			this.pcrFont = ((PdfFont) this.fonts.get(this.pcrFontKey));
			if (this.pcrFont != null) {
				this.pcrFontSpaceWidth = this.pcrFont.getCharWidth(' ');
				this.narrowSpaceToleranceFactor = Math.max(defaultNarrowSpaceToleranceFactor, (absMinNarrowSpaceWidth / Math.max(this.pcrFontSpaceWidth, minFontSpaceWidth)));
				if (DEBUG_RENDER_PAGE_CONTENT) {
					System.out.println(this.indent + " --> font " + this.pcrFontKey + " sized " + this.pcrFontSize);
					System.out.println(this.indent + "   - font is " + this.pcrFont.data);
					System.out.println(this.indent + "   - font descriptor " + this.pcrFont.descriptor);
					System.out.println(this.indent + "   - font name " + this.pcrFont.name);
					System.out.println(this.indent + "   - bold: " + this.pcrFont.bold + ", italic: " + this.pcrFont.italics);
					System.out.println(this.indent + "   - space width is " + this.pcrFontSpaceWidth);
					System.out.println(this.indent + "   - narrow space tolerance factor is " + this.narrowSpaceToleranceFactor);
					System.out.println(this.indent + "   - implicit space: " + this.pcrFont.hasImplicitSpaces);
				}
				this.computeEffectiveFontSizeAndDirection();
			}
			else if (DEBUG_RENDER_PAGE_CONTENT) {
				System.out.println(this.indent + " --> font " + this.pcrFontKey + " not found");
				System.out.println(this.indent + "     font key class is " + this.pcrFontKey.getClass().getName());
				System.out.println(this.indent + "     fonts are " + this.fonts);
			}
		}
		
		private void doTfs(LinkedList stack) {
			this.pcrFontSize = ((Number) stack.removeLast()).floatValue();
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(this.indent + " --> font size " + this.pcrFontSize);
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
				System.out.println(this.indent + " --> text matrix set to");
				printMatrix(this.lineMatrix, this.indent);
			}
			this.computeEffectiveFontSizeAndDirection();
		}
		
		//	compute effective font size as round(font size * ((a + d) / 2)) in text matrix
		private void computeEffectiveFontSizeAndDirection() {
			float[] pwrFontSize = {0, this.pcrFontSize, 0};
			pwrFontSize = this.applyTransformationMatrices(transform(pwrFontSize, this.textMatrix, PRotate.identity, this.indent));
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts) {
				System.out.println(this.indent + " ==> font analysis vector is " + Arrays.toString(pwrFontSize));
				System.out.println(this.indent + "     text matrix:");
				printMatrix(this.textMatrix, this.indent);
				System.out.println(this.indent + "     line matrix:");
				printMatrix(this.lineMatrix, this.indent);
				System.out.println(this.indent + "     transformation matrix:");
				printMatrix(this.pcrTransformationMatrices.etm, this.indent);
			}
			this.eFontSize = ((float) Math.sqrt((pwrFontSize[0] * pwrFontSize[0]) + (pwrFontSize[1] * pwrFontSize[1])));
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(this.indent + " ==> effective font size is " + this.eFontSize);
			if (Math.abs(pwrFontSize[1]) > Math.abs(pwrFontSize[0])) {
				if (pwrFontSize[1] < 0) {
					this.eFontDirection = PWord.UPSIDE_DOWN_FONT_DIRECTION;
					if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
						System.out.println(this.indent + " ==> effective font direction is upside-down right-left");
				}
				else {
					this.eFontDirection = PWord.LEFT_RIGHT_FONT_DIRECTION;
					if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
						System.out.println(this.indent + " ==> effective font direction is left-right");
				}
			}
			else {
				if (pwrFontSize[0] < 0) {
					this.eFontDirection = PWord.BOTTOM_UP_FONT_DIRECTION;
					if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
						System.out.println(this.indent + " ==> effective font direction is bottom-up");
				}
				else {
					this.eFontDirection = PWord.TOP_DOWN_FONT_DIRECTION;
					if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
						System.out.println(this.indent + " ==> effective font direction is top-down");
				}
			}
		}
		
		private void doTr(LinkedList stack) {
			this.pcrTextRenderingMode = ((Number) stack.removeLast()).intValue();
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(this.indent + " --> text rendering mode " + this.pcrTextRenderingMode);
			if ((this.pcrTextRenderingMode >= 4) && (this.pcrClipWords == null))
				this.pcrClipWords = new ArrayList(8);
		}
		
		private void doTs(LinkedList stack) {
			this.pcrTextRise = ((Number) stack.removeLast()).floatValue();
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(this.indent + " --> text rise " + this.pcrTextRise);
		}
		
		private void doTd(LinkedList stack) {
			this.doNewLine(stack, false);
		}
		
		private void doTD(LinkedList stack) {
			this.doNewLine(stack, true);
		}
		
		private void doTasterisk(LinkedList stack) {
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
				System.out.println(this.indent + "Moving to new line");
			updateMatrix(this.textMatrix, ((Number) tx).floatValue(), ((Number) ty).floatValue());
			cloneValues(this.textMatrix, this.lineMatrix);
			this.pendingSpace = 0;
			this.computeEffectiveFontSizeAndDirection();
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts) {
				System.out.println(this.indent + " --> new line, offset by " + tx + "/" + ty + (setLineHeight ? (", and new leading (line height " + this.pcrLineHeight + ")") : ""));
				printMatrix(this.lineMatrix, this.indent);
			}
		}
		
		void evaluateTag(String tag, byte[] tagData, LinkedList stack, ProgressMonitor pm) throws IOException {
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
				this.doTasterisk(stack);
			
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
			
			//	clipping path
			else if ("W".equals(tag))
				this.doW(stack);
			else if ("W*".equals(tag))
				this.doWasterisk(stack);
			
			//	path rendering and filling
			else if ("S".equals(tag))
				this.doS(stack);
			else if ("s".equals(tag))
				this.dos(stack);
			else if ("F".equals(tag))
				this.doF(stack, pm);
			else if ("f".equals(tag))
				this.dof(stack, pm);
			else if ("f*".equals(tag))
				this.dofasterisk(stack, pm);
			else if ("B".equals(tag))
				this.doB(stack);
			else if ("B*".equals(tag))
				this.doBasterisk(stack);
			else if ("b".equals(tag))
				this.dob(stack);
			else if ("b*".equals(tag))
				this.dobasterisk(stack);
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
				this.dogs(stack, pm);
			
			//	draw object (usually embedded images, or forms with subordinary content)
			else if ("Do".equals(tag))
				this.doDo(stack, pm);
			
			//	draw inline image
			else if ("BI".equals(tag))
				this.doBI(tagData);
			
			else if (DEBUG_RENDER_PAGE_CONTENT)
				System.out.println(this.indent + " ==> UNKNOWN");
		}
		
		private void doBI(byte[] data) {
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(this.indent + "Drawing inline image at RON " + this.nextRenderOrderNumber);
			
			try {
				PdfByteInputStream iImageData = new PdfByteInputStream(new ByteArrayInputStream(data));
				
				Map imageParams = cropInlineImageParams(iImageData);
				if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
					System.out.println(this.indent + " - params are " + imageParams);
				
				ByteArrayOutputStream imageData = new ByteArrayOutputStream();
				byte[] buffer = new byte[1024];
				for (int r; (r = iImageData.read(buffer, 0, buffer.length)) != -1;)
					imageData.write(buffer, 0, r);
				
				PStream image = new PStream(imageParams, imageData.toByteArray());
				if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
					System.out.println(this.indent + " - data is " + Arrays.toString(image.bytes));
				
				this.doDoFigure("Inline", image); // position, size, rotation, and flip computation is same as for XObject images
			}
			catch (IOException ioe) {
				System.out.println("Error parsing inline image: " + ioe.getMessage());
				ioe.printStackTrace(System.out);
			}
		}
		
		private void doDo(LinkedList stack, ProgressMonitor pm) {
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(this.indent + "Drawing object at RON " + this.nextRenderOrderNumber);
			
			String name = stack.removeLast().toString();
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(this.indent + " ==> object name is " + name);
			
			Object objObj = dereference(this.xObject.get(name), this.objects);
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(this.indent + " ==> object is " + objObj);
			if (!(objObj instanceof PStream))
				return;
			
			PStream obj = ((PStream) objObj);
			Object objTypeObj = obj.params.get("Subtype");
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(this.indent + " ==> object type is " + objTypeObj);
			if (objTypeObj == null)
				return;
			
			if ("Image".equals(objTypeObj.toString()))
				this.doDoFigure(name, objObj);
			else if ("Form".equals(objTypeObj.toString())) try {
				this.doDoForm(obj, false, pm);
				if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
					System.out.println(this.indent + " ==> form object " + name + " handled");
			}
			catch (IOException ioe) {
				System.out.println(this.indent + " ==> exception handling form object " + ioe.getMessage());
				ioe.printStackTrace(System.out);
			}
		}
		
		private void doDoForm(PStream form, boolean isSoftMask, ProgressMonitor pm) throws IOException {
			Object formResObj = dereference(form.params.get("Resources"), this.objects);
			if (!(formResObj instanceof Map))
				return;
			
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts) {
				System.out.println(this.indent + "Doing form");
				System.out.println(this.indent + " - parameters are " + form.params);
				System.out.println(this.indent + " - overprint is " + this.pcrStrokeOverPrint + "/" + this.pcrNonStrokeOverPrint + ", mode " + this.pcrOverPrintMode + ", composit alpha " + this.pcrStrokeCompositAlpha + "/" + this.pcrNonStrokeCompositAlpha + ", blend mode " + ((this.pcrBlendMode == null) ? "Normal" : this.pcrBlendMode.name));
			}
			Map formResources = ((Map) formResObj);
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(this.indent + " - resources are " + formResources);
			Object filterObj = form.params.get("Filter");
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			PdfParser.decode(filterObj, form.bytes, form.params, baos, objects);
			byte[] formContent = baos.toByteArray();
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(this.indent + " - content is " + formContent.length + " bytes");
			Object formMatrixObj = form.params.get("Matrix");
			float[][] formMatrix = null;
			if ((formMatrixObj instanceof List) && (((List) formMatrixObj).size() == 6)) {
				List formMatrixData = ((List) formMatrixObj);
				formMatrix = new float[3][3];
				formMatrix[0][0] = ((Number) formMatrixData.get(0)).floatValue();
				formMatrix[1][0] = ((Number) formMatrixData.get(1)).floatValue();
				formMatrix[2][0] = 0;
				formMatrix[0][1] = ((Number) formMatrixData.get(2)).floatValue();
				formMatrix[1][1] = ((Number) formMatrixData.get(3)).floatValue();
				formMatrix[2][1] = 0;
				formMatrix[0][2] = ((Number) formMatrixData.get(4)).floatValue();
				formMatrix[1][2] = ((Number) formMatrixData.get(5)).floatValue();
				formMatrix[2][2] = 1;
				if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts) {
					System.out.println(this.indent + " - matrix is");
					printMatrix(formMatrix, this.indent);
				}
			}
			PcrTransformationMatrixStack formTransformationMatrices = new PcrTransformationMatrixStack(this.pcrTransformationMatrices);
			if (formMatrix != null)
				formTransformationMatrices.addFirst(formMatrix);
			Object formBoundsObj = form.params.get("BBox");
			Rectangle2D.Float formBounds = null;
			if ((formBoundsObj instanceof List) && (((List) formBoundsObj).size() == 4)) {
				List formBoundsData = ((List) formBoundsObj);
				float left = ((Number) formBoundsData.get(0)).floatValue();
				float bottom = ((Number) formBoundsData.get(1)).floatValue();
				float right = ((Number) formBoundsData.get(2)).floatValue();
				float top = ((Number) formBoundsData.get(3)).floatValue();
				formBounds = new Rectangle2D.Float(left, bottom, (right - left), (top - bottom));
				if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
					System.out.println(this.indent + " - raw bounds are " + formBounds);
				formBounds = this.transformBounds(formBounds, formMatrix, PRotate.identity);
				if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
					System.out.println(this.indent + " - transformed bounds are " + formBounds);
				if (formBounds.width < 0) {
					formBounds.x += formBounds.width;
					formBounds.width = -formBounds.width;
				}
				if (formBounds.height < 0) {
					formBounds.y += formBounds.height;
					formBounds.height = -formBounds.height;
				}
				if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
					System.out.println(this.indent + " - sanitized bounds are " + formBounds);
			}
			
			/* TODOne observe scaling matrix (once we have a respective example PDF):
			 * - use form specific list for words and figures
			 * - add scaled words and figures to main lists
			 * - use same measurement vectors as for images */
			//	TODOne TEST science.362.6417.897.supp.pdf, pages 17-22
			ArrayList formWords = ((this.words == null) ? null : new ArrayList());
			ArrayList formFigures = ((this.figures == null) ? null : new ArrayList());
			ArrayList formPaths = ((this.paths == null) ? null : new ArrayList());
			this.nextRenderOrderNumber = runPageContent(this.nextRenderOrderNumber, formTransformationMatrices, form.params, formContent, this.pcrTransformationMatrices.rotate, formResources, this.objects, formWords, null, this.fontCharSet, formFigures, formPaths, this.assessFonts, this.wordsAreOcr, pm, (this.indent + "    "));
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts) {
				System.out.println(this.indent + "Form content done");
				System.out.println(this.indent + "Clipping path stack is " + this.pcrClippingPathStack);
				System.out.println(this.indent + "Soft mask stack is " + this.pcrSoftMaskStack);
				if (formWords != null)
					System.out.println(this.indent + "Got " + formWords.size() + " words");
				if (formFigures != null)
					System.out.println(this.indent + "Got " + formFigures.size() + " figures");
				if (formPaths != null)
					System.out.println(this.indent + "Got " + formPaths.size() + " paths");
			}
			
			//	transform and store form content, adjusting colors based on composite alpha values
			if (formWords != null)
				for (int w = 0; w < formWords.size(); w++) {
					PWord fw = ((PWord) formWords.get(w));
					if ((formBounds != null) && !formBounds.intersects(fw.bounds)) {
						if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
							System.out.println(this.indent + " - out of bounds word filtered: " + fw.str);
						continue;
					}
					float fwBaseline = fw.baseline;//this.transformBounds(fw.bounds, formMatrix);
					Rectangle2D.Float fwBounds = fw.bounds;//this.transformBounds(fw.bounds, formMatrix);
					int fwFontSize = fw.fontSize;//Math.round((fw.fontSize * fwBounds.height) / fw.bounds.height);
					Color fwColor = ((fw.color == null) ? null : this.getCompositeAlphaColor(fw.color, this.pcrNonStrokeCompositAlpha));
					if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
						System.out.println(this.indent + "   word color transformed from " + fw.color + ((fw.color == null) ? "" : (" with alpha " + fw.color.getAlpha())) + " to " + fwColor + ((fwColor == null) ? "" : (" with alpha " + fwColor.getAlpha())));
					fw = new PWord(fw.renderOrderNumber, fw.charCodes, fw.str, fwBaseline, fwBounds, fwColor, fwFontSize, fw.fontDirection, fw.font);
					this.words.add(fw);
				}
			if (formFigures != null) // TODO consider storing composite alpha with figures (if it turns out necessary)
				for (int f = 0; f < formFigures.size(); f++) {
					PFigure ff = ((PFigure) formFigures.get(f));
					if ((formBounds != null) && !formBounds.intersects(ff.bounds)) {
						if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
							System.out.println(this.indent + " - out of bounds figure filtered: " + ff.name + " at " + ff.bounds);
						continue;
					}
					if (isSoftMask) {
						ff.name = ("SoftMask-" + ff.name + "-" + ff.hashCode());
						this.pcrSoftMaskStack.add(ff);
						if (ff.softMasks != null) {
							this.pcrSoftMaskStack.add(Arrays.asList(ff.softMasks));
							ff.softMasks = null;
						}
//						if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
//							this.figures.add(ff);
					}
					else {
						PPath[] ffClipPaths = this.addClippingPaths(ff.clipPaths);
						if (ffClipPaths != ff.clipPaths)
							ff.setClipPaths(ffClipPaths);
						PFigure[] ffSoftMasks = this.addSoftMasks(ff.softMasks);
						if (ffSoftMasks != ff.softMasks)
							ff.softMasks = ffSoftMasks;
						this.figures.add(ff);
					}
				}
			if (formPaths != null)
				for (int p = 0; p < formPaths.size(); p++) {
					PPath fp = ((PPath) formPaths.get(p));
					if ((formBounds != null) && !formBounds.intersects(fp.bounds)) {
						if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
							System.out.println(this.indent + " - out of bounds path filtered: " + fp.bounds);
						continue;
					}
					if (fp.strokeColor != null) {
						fp.strokeColor = this.getCompositeAlphaColor(fp.strokeColor, this.pcrStrokeCompositAlpha);
						if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
							System.out.println(this.indent + "PPath: stroke color set to " + fp.strokeColor + ((fp.strokeColor == null) ? "" : (", alpha " + fp.strokeColor.getAlpha())));
					}
					if (fp.fillColor != null) {
						fp.fillColor = this.getCompositeAlphaColor(fp.fillColor, this.pcrNonStrokeCompositAlpha);
						if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
							System.out.println(this.indent + "PPath: fill color set to " + fp.fillColor + ((fp.fillColor == null) ? "" : (", alpha " + fp.fillColor.getAlpha())));
					}
					if (isSoftMask) {
						//	TODO put paths into graphics state rather (should the need arise) !!!
						if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
							System.out.println(this.indent + " - soft mask form path filtered: " + fp.bounds);
					}
					else {
						PPath[] fpClipPaths = this.addClippingPaths(fp.clipPaths);
						if (fpClipPaths != fp.clipPaths)
							fp.setClipPaths(fpClipPaths);
						fp.blendMode = this.pcrBlendMode;
						this.paths.add(fp);
					}
				}
		}
		
		private Rectangle2D.Float transformBounds(Rectangle2D.Float bounds, float[][] matrix, PRotate rotate) {
			float[] llPoint = {bounds.x, bounds.y, 1};
			if (matrix != null)
				llPoint = PdfParser.transform(llPoint, matrix, rotate, this.indent);
			llPoint = this.applyTransformationMatrices(llPoint);
			float[] urPoint = {(bounds.x + bounds.width), (bounds.y + bounds.height), 1};
			if (matrix != null)
				urPoint = PdfParser.transform(urPoint, matrix, rotate, this.indent);
			urPoint = this.applyTransformationMatrices(urPoint);
			return new Rectangle2D.Float(llPoint[0], llPoint[1], (urPoint[0] - llPoint[0]), (urPoint[1] - llPoint[1]));
		}
		
		private PPath[] addClippingPaths(PPath[] clippingPaths) {
			if (this.pcrClippingPathStack.size() == 0)
				return clippingPaths;
			ArrayList combClippingPaths = new ArrayList(this.pcrClippingPathStack);
			if (clippingPaths != null)
				combClippingPaths.addAll(Arrays.asList(clippingPaths));
			return ((PPath[]) combClippingPaths.toArray(new PPath[combClippingPaths.size()]));
		}
		private PFigure[] addSoftMasks(PFigure[] softMasks) {
			if (this.pcrSoftMaskStack.size() == 0)
				return softMasks;
			ArrayList combSoftMasks = new ArrayList(this.pcrSoftMaskStack);
			if (softMasks != null)
				combSoftMasks.addAll(Arrays.asList(softMasks));
			return ((PFigure[]) combSoftMasks.toArray(new PFigure[combSoftMasks.size()]));
		}
		
		private void doDoFigure(String name, Object refOrData) {
			//	new computation, figuring in rotation, according to
			//	http://math.stackexchange.com/questions/13150/extracting-rotation-scale-values-from-2d-transformation-matrix/13165#13165
			
			//	yet newer computation simply transforming corners for dimension and center for location
			
			float[] transBottomLeft = {0, 0, 1};
			transBottomLeft = this.applyTransformationMatrices(transBottomLeft);
			if (DEBUG_RENDER_PAGE_CONTENT && (this.figures != null))
				System.out.println(this.indent + " ==> image rendering origin " + Arrays.toString(transBottomLeft));
			float[] transTopRight = {1, 1, 1};
			transTopRight = this.applyTransformationMatrices(transTopRight);
			if (DEBUG_RENDER_PAGE_CONTENT && (this.figures != null))
				System.out.println(this.indent + " ==> top right corner at " + Arrays.toString(transTopRight));
			float[] transTopLeft = {0, 1, 1};
			transTopLeft = this.applyTransformationMatrices(transTopLeft);
			if (DEBUG_RENDER_PAGE_CONTENT && (this.figures != null))
				System.out.println(this.indent + " ==> top left corner at " + Arrays.toString(transTopLeft));
			float[] transBottomRight = {1, 0, 1};
			transBottomRight = this.applyTransformationMatrices(transBottomRight);
			if (DEBUG_RENDER_PAGE_CONTENT && (this.figures != null))
				System.out.println(this.indent + " ==> bottom right corner at " + Arrays.toString(transBottomRight));
			float[] translateCenter = {0.5f, 0.5f, 1};
			translateCenter = this.applyTransformationMatrices(translateCenter);
			if (DEBUG_RENDER_PAGE_CONTENT && (this.figures != null))
				System.out.println(this.indent + " ==> center point at " + Arrays.toString(translateCenter));
			
			float[] scaleRotate0 = {1, 0, 0};
			scaleRotate0 = this.applyTransformationMatrices(scaleRotate0);
			if (DEBUG_RENDER_PAGE_CONTENT && (this.figures != null))
				System.out.println(this.indent + " ==> image rotation and scaling 1 " + Arrays.toString(scaleRotate0));
			float[] scaleRotate1 = {0, 1, 0};
			scaleRotate1 = this.applyTransformationMatrices(scaleRotate1);
			if (DEBUG_RENDER_PAGE_CONTENT && (this.figures != null))
				System.out.println(this.indent + " ==> image rotation and scaling 2 " + Arrays.toString(scaleRotate1));
			
			float scaleX = ((float) Math.sqrt((scaleRotate0[0] * scaleRotate0[0]) + (scaleRotate1[0] * scaleRotate1[0])));
			float scaleY = ((float) Math.sqrt((scaleRotate0[1] * scaleRotate0[1]) + (scaleRotate1[1] * scaleRotate1[1])));
			if (DEBUG_RENDER_PAGE_CONTENT && (this.figures != null))
				System.out.println(this.indent + " ==> scaling is " + scaleX + "/" + scaleY);
			
			float transHorizontalDeltaX = (Math.abs(transBottomRight[0] - transBottomLeft[0]) + Math.abs(transTopRight[0] - transTopLeft[0]));
			float transHorizontalDeltaY = (Math.abs(transBottomRight[1] - transBottomLeft[1]) + Math.abs(transTopRight[1] - transTopLeft[1]));
			if (DEBUG_RENDER_PAGE_CONTENT && (this.figures != null))
				System.out.println(this.indent + " ==> transformed point deltas of horizontal edges are " + transHorizontalDeltaX + "/" + transHorizontalDeltaY);
			float transVerticalDeltaX = (Math.abs(transTopLeft[0] - transBottomLeft[0]) + Math.abs(transTopRight[0] - transBottomRight[0]));
			float transVerticalDeltaY = (Math.abs(transTopLeft[1] - transBottomLeft[1]) + Math.abs(transTopRight[1] - transBottomRight[1]));
			if (DEBUG_RENDER_PAGE_CONTENT && (this.figures != null))
				System.out.println(this.indent + " ==> transformed point deltas of vertical edges are " + transVerticalDeltaX + "/" + transVerticalDeltaY);
			double transWidth = ((
					Math.sqrt(
						((transBottomRight[0] - transBottomLeft[0]) * (transBottomRight[0] - transBottomLeft[0]))
						+
						((transBottomRight[1] - transBottomLeft[1]) * (transBottomRight[1] - transBottomLeft[1]))
					)
					+ 
					Math.sqrt(
						((transTopRight[0] - transTopLeft[0]) * (transTopRight[0] - transTopLeft[0]))
						+
						((transTopRight[1] - transTopLeft[1]) * (transTopRight[1] - transTopLeft[1]))
					)
				) / 2);
			double transHeight = ((
					Math.sqrt(
						((transTopLeft[0] - transBottomLeft[0]) * (transTopLeft[0] - transBottomLeft[0]))
						+
						((transTopLeft[1] - transBottomLeft[1]) * (transTopLeft[1] - transBottomLeft[1]))
					)
					+ 
					Math.sqrt(
						((transTopRight[0] - transBottomRight[0]) * (transTopRight[0] - transBottomRight[0]))
						+
						((transTopRight[1] - transBottomRight[1]) * (transTopRight[1] - transBottomRight[1]))
					)
				) / 2);
			float width;
			float height;
			if ((transHorizontalDeltaY < transHorizontalDeltaX) && (transVerticalDeltaX < transVerticalDeltaY)) {
				width = ((float) transWidth);
				height = ((float) transHeight);
			}
			else {
				width = ((float) transHeight);
				height = ((float) transWidth);
			}
			Rectangle2D.Float bounds = new Rectangle2D.Float((translateCenter[0] - (width / 2)), (translateCenter[1] - (height / 2)), width, height);
			
//			if (DEBUG_RENDER_PAGE_CONTENT && (this.figures != null)) {
//				float a = scaleRotate1[0];
//				float b = scaleRotate2[0];
//				float c = scaleRotate1[1];
//				float d = scaleRotate2[1];
//				float delta = ((a * d) - (b * c));
//				System.out.println(" ==> delta would be " + delta);
//				if (a != 0 || b != 0) {
//					float r = ((float) Math.sqrt(a * a + b * b));
//					float rotation = ((float) ((b > 0) ? Math.acos(a / r) : -Math.acos(a / r)));
//					System.out.println(" =1=> rotation would be " + rotation);
//					float scalex = r;
//					float scaley = (delta / r);
//					System.out.println(" =1=> scale would be " + scalex + "/" + scaley);
//				}
//				else if (c != 0 || d != 0) {
//					float s = ((float) Math.sqrt(c * c + d * d));
//					float rotation = ((float) (Math.PI / 2 - (d > 0 ? Math.acos(-c / s) : -Math.acos(c / s))));
//					System.out.println(" =2=> rotation would be " + rotation);
//					float scalex = (delta / s);
//					float scaley = s;
//					System.out.println(" =2=> scale would be " + scalex + "/" + scaley);
//				}
//				/*
//  var delta = a * d - b * c;
//  if (a != 0 || b != 0) {
//    var r = Math.sqrt(a * a + b * b);
//    result.rotation = b > 0 ? Math.acos(a / r) : -Math.acos(a / r);
//    result.scale = [r, delta / r];
//    result.skew = [Math.atan((a * c + b * d) / (r * r)), 0];
//  } else if (c != 0 || d != 0) {
//    var s = Math.sqrt(c * c + d * d);
//    result.rotation =
//      Math.PI / 2 - (d > 0 ? Math.acos(-c / s) : -Math.acos(c / s));
//    result.scale = [delta / s, s];
//    result.skew = [0, Math.atan((a * c + b * d) / (s * s))];
//  }				 */
//			}
//			
//			double rotation;
//			boolean rightSideLeft;
//			boolean upsideDown;
//			
//			float scaleLin = ((float) Math.sqrt((scaleRotate0[0] * scaleRotate0[0]) + (scaleRotate1[1] * scaleRotate1[1])));
//			if (DEBUG_RENDER_PAGE_CONTENT && (this.figures != null))
//				System.out.println(" ==> X-X/Y-Y scaling is " + scaleLin);
//			float scaleDiag = ((float) Math.sqrt((scaleRotate0[1] * scaleRotate0[1]) + (scaleRotate1[0] * scaleRotate1[0])));
//			if (DEBUG_RENDER_PAGE_CONTENT && (this.figures != null))
//				System.out.println(" ==> X-Y/Y-X scaling is " + scaleDiag);
//			
//			//	TODOne soften this up to ((sr1[0] + sr2[1]) >> (sr1[1] + sr2[0]))
//			if ((scaleDiag * 5) < scaleLin) {
//				double aRotation1 = Math.atan2(-scaleRotate0[1], scaleRotate0[0]);
//				double aRotation2 = Math.atan2(scaleRotate1[0], scaleRotate1[1]);
//				double aRotation;
//				if ((Math.min(aRotation1, aRotation2) < (-Math.PI / 2)) && (Math.max(aRotation1, aRotation2) > (Math.PI / 2)))
//					aRotation = ((aRotation1 + aRotation2 + (Math.PI * 2)) / 2);
//				else aRotation = ((aRotation1 + aRotation2) / 2);
//				if (DEBUG_RENDER_PAGE_CONTENT && (this.figures != null))
//					System.out.println(" =1=> actual image rotation is " + aRotation + " (" + aRotation1 + "/" + aRotation2 + ") = " + ((180.0 / Math.PI) * aRotation) + "°");
//				if ((scaleRotate0[0] < 0) && (scaleRotate1[1] < 0)) {
//					rotation = Math.PI;
//					rightSideLeft = false;
//					upsideDown = false;
//					if (DEBUG_RENDER_PAGE_CONTENT && (this.figures != null))
//						System.out.println(" =1=> image rotation is " + rotation + " (for horizontal and vertical flip) = " + ((180.0 / Math.PI) * rotation) + "°");
//					rotation = aRotation;
//					if (DEBUG_RENDER_PAGE_CONTENT && (this.figures != null))
//						System.out.println(" =1=> image rotation corrected to " + rotation + " = " + ((180.0 / Math.PI) * rotation) + "°");
//				}
//				else {
//					rotation = 0;
//					if (DEBUG_RENDER_PAGE_CONTENT && (this.figures != null))
//						System.out.println(" =1=> image rotation is " + rotation + "°");
//					if (scaleRotate0[0] < 0) {
//						rightSideLeft = true;
//						upsideDown = false;
//						if (DEBUG_RENDER_PAGE_CONTENT && (this.figures != null))
//							System.out.println(" =1=> figure flipped horizontally (right side left)");
//					}
//					else if (scaleRotate1[1] < 0) {
//						rightSideLeft = false;
//						upsideDown = true;
//						if (DEBUG_RENDER_PAGE_CONTENT && (this.figures != null))
//							System.out.println(" =1=> figure flipped vertically (upside down)");
//					}
//					else {
//						rightSideLeft = false;
//						upsideDown = false;
//					}
//					if (upsideDown == rightSideLeft)
//						rotation += aRotation;
//					else rotation = ((Math.signum(aRotation) * (Math.PI / 2)) - aRotation);
//					if (DEBUG_RENDER_PAGE_CONTENT && (this.figures != null))
//						System.out.println(" =1=> image rotation corrected to " + rotation + " = " + ((180.0 / Math.PI) * rotation) + "°");
//				}
//			}
//			//	TODOne recognize rotation+flip combinations ((sr1[1] + sr2[0]) >> (sr1[0] + sr2[1]))
//			else if (((scaleLin * 5) < scaleDiag) && (Math.signum(scaleRotate0[1]) == Math.signum(scaleRotate1[0]))) {
//				if (scaleRotate0[1] < 0) {
//					rotation = (Math.PI / 2);
//					rightSideLeft = true; // need to flip to right after rotating 90° clockwise (in Swing, positive y-axis is downwards)
//					upsideDown = false;
//					if (DEBUG_RENDER_PAGE_CONTENT && (this.figures != null))
//						System.out.println(" =2=> image rotation is " + rotation + " (for horizontal flip and rotation) = " + ((180.0 / Math.PI) * rotation) + "°");
//				}
//				else {
//					rotation = (Math.PI / 2);
//					rightSideLeft = false;
//					upsideDown = true; // need to flip upside-down after rotating 90° clockwise (in Swing, positive y-axis is downwards)
//					if (DEBUG_RENDER_PAGE_CONTENT && (this.figures != null))
//						System.out.println(" =2=> image rotation is " + rotation + " (for vertical flip and rotation) = " + ((180.0 / Math.PI) * rotation) + "°");
//				}
//				double aRotation1 = Math.atan2(-scaleRotate0[1], scaleRotate0[0]);
//				double aRotation2 = Math.atan2(scaleRotate1[0], scaleRotate1[1]);
//				double aRotation;
//				if ((Math.min(aRotation1, aRotation2) < (-Math.PI / 2)) && (Math.max(aRotation1, aRotation2) > (Math.PI / 2)))
//					aRotation = ((aRotation1 + aRotation2 + (Math.PI * 2)) / 2);
//				else aRotation = ((aRotation1 + aRotation2) / 2);
//				if (DEBUG_RENDER_PAGE_CONTENT && (this.figures != null))
//					System.out.println(" =2=> actual image rotation is " + aRotation + " (" + aRotation1 + "/" + aRotation2 + ") = " + ((180.0 / Math.PI) * aRotation) + "°");
//				rotation -= aRotation;
//				if (DEBUG_RENDER_PAGE_CONTENT && (this.figures != null))
//					System.out.println(" =2=> image rotation corrected to " + rotation + " = " + ((180.0 / Math.PI) * rotation) + "°");
//			}
//			else {
//				double rotation1 = Math.atan2(-scaleRotate0[1], scaleRotate0[0]);
//				double rotation2 = Math.atan2(scaleRotate1[0], scaleRotate1[1]);
//				if ((Math.min(rotation1, rotation2) < (-Math.PI / 2)) && (Math.max(rotation1, rotation2) > (Math.PI / 2)))
//					rotation = ((rotation1 + rotation2 + (Math.PI * 2)) / 2);
//				else rotation = ((rotation1 + rotation2) / 2);
//				rightSideLeft = false;
//				upsideDown = false;
//				if (DEBUG_RENDER_PAGE_CONTENT && (this.figures != null))
//					System.out.println(" =3=> image rotation is " + rotation + " (" + rotation1 + "/" + rotation2 + ") = " + ((180.0 / Math.PI) * rotation) + "°");
//			}
//			
//			if (((180.0 / Math.PI) * rotation) > 180) {
//				rotation -= (Math.PI * 2);
//				if (DEBUG_RENDER_PAGE_CONTENT && (this.figures != null))
//					System.out.println(" =1=> image rotation normalized to " + rotation + " = " + ((180.0 / Math.PI) * rotation) + "°");
//			}
//			else if (((180.0 / Math.PI) * rotation) < -180) {
//				rotation += (Math.PI * 2);
//				if (DEBUG_RENDER_PAGE_CONTENT && (this.figures != null))
//					System.out.println(" =2=> image rotation normalized to " + rotation + " = " + ((180.0 / Math.PI) * rotation) + "°");
//			}
			PPath[] clipPaths = this.getClipPaths();
			int clipReason = this.getClipReason(bounds, clipPaths, (DEBUG_RENDER_PAGE_CONTENT ? Color.RED : Color.BLACK));
			PFigure[] softMasks = this.getSoftMasks();
			if (DEBUG_RENDER_PAGE_CONTENT && (this.figures != null)) {
				System.out.println(this.indent + " ==> image rendering bounds " + bounds);
				if (clipPaths == null)
					System.out.println(this.indent + " ==> image clip bounds not set");
				else {
					Rectangle2D clipBounds = clipPaths[0].getBounds();
					for (int cp = 1; cp < clipPaths.length; cp++)
						clipBounds = clipBounds.createIntersection(clipPaths[cp].getBounds());
					System.out.println(this.indent + " ==> image clip bounds " + clipBounds);
				}
				if (softMasks == null)
					System.out.println(this.indent + " ==> image soft masks not set (internal one might still be there)");
				else for (int sm = 0; sm < softMasks.length; sm++)
					System.out.println(this.indent + " ==> image soft mask " + softMasks[sm].name + " at " + softMasks[sm].bounds + "/" + softMasks[sm].visibleBounds);
			}
			if (DEBUG_RENDER_PAGE_CONTENT && (this.figures != null)) {
				System.out.println(this.indent + " ==> image name is " + name);
				System.out.println(this.indent + " ==> overprint is " + this.pcrStrokeOverPrint + "/" + this.pcrNonStrokeOverPrint + ", mode " + this.pcrOverPrintMode + ", composit alpha " + this.pcrStrokeCompositAlpha + "/" + this.pcrNonStrokeCompositAlpha + ", blend mode " + ((this.pcrBlendMode == null) ? "Normal" : this.pcrBlendMode.name) + ", mask alpha is " + (this.pcrAlphaIsShape ? "shape" : "alpha"));
			}
			if ((this.figures != null) && (clipReason == -1))
				this.figures.add(new PFigure(this.nextRenderOrderNumber++, name, bounds, clipPaths, softMasks, refOrData, this.pcrTransformationMatrices.getMatrix()));
			else if (DEBUG_RENDER_PAGE_CONTENT && (this.figures != null))
				System.out.println(this.indent + " ==> " + ((clipReason == 0) ? "word " : "") +"clipped image");
		}
		
		private void dohighcomma(LinkedList stack) {
			this.doTasterisk(stack);
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
					System.out.println(this.indent + " --> show string: '" + rStr + "'" + ((rStr.length() == 1) ? (" (" + ((int) rStr.charAt(0)) + ")") : ""));
			}
			else if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts) {
				System.out.println(this.indent + " --> show string (no font): '" + cs + "'" + ((cs.length() == 1) ? (" (" + ((int) cs.charAt(0)) + ")") : ""));
				System.out.println(this.indent + " --> cs class is " + cs.getClass().getName());
			}
		}
		
		private void doTJ(LinkedList stack) {
			StringBuffer totalRendered = new StringBuffer();
			List stros = ((List) stack.removeLast());
			
			//	print debug info
			if (DEBUG_RENDER_PAGE_CONTENT) {
				System.out.println(this.indent + "TEXT ARRAY: " + stros);
				for (int s = 0; s < stros.size(); s++) {
					Object o = stros.get(s);
					if (o instanceof Number) {
						if (!this.assessFonts)
							System.out.println(" - n " + o);
					}
					else if (o instanceof CharSequence) {
						CharSequence cs = ((CharSequence) o);
						System.out.print(this.indent + " - cs " + o + " (");
						for (int c = 0; c < cs.length(); c++)
							System.out.print(" " + ((int) cs.charAt(c)));
						System.out.println(")");
					}
					else System.out.println(this.indent + " - o " + o + " (" + o.getClass().getName() + ")");
				}
			}
			
			/* Measure width of explicit spaces (including adjacent shifts)
			 * when rendering (need to do that locally on every row, as it
			 * varies too much globally due to text justification and tables).
			 * Use that average explicit space width to assess implicit spaces.
			 * TODOne assess if this works, or if it incurs too many conflated
			 * words ==> should be OK with below cap-off */
			float mExplicitSpaceWidth = this.measureExplicitSpaceWidth(stros);
			/* cap off explicit space with so this works in tables with varying
			 * column gaps, as otherwise wide column gaps completely collapse
			 * narrower ones, and empty table cells bridged in a single multi-
			 * column step have the same effect (1.0 still is pretty wide a
			 * space ...) */
			/* use absolute minimum for explicit space width as well, in case
			 * measurements return an all too low value */
			float explicitSpaceWidth = Math.max(Math.min(mExplicitSpaceWidth, 1), 0.25f);
			
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
					float d = (((Number) o).floatValue() / 1000); // TODOne does factoring in char spacing make sense or cause trouble ??? ==> we basically _have_to_ factor it in, as it does contribute
					float eShift = ((-d * this.pcrFontSize) + this.pcrCharSpacing);
					if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts) {
						System.out.println(this.indent + "Re-positioning by TJ array number " + d + "*" + this.pcrFontSize + " at effective " + this.eFontSize + ", char spacing is " + this.pcrCharSpacing + ", word spacing is " + this.pcrWordSpacing);
						System.out.println(this.indent + " - effective shift is " + eShift);
						System.out.println(this.indent + " - minimum implicit space width is " + ((this.pcrFontSpaceWidth * this.narrowSpaceToleranceFactor * this.pcrFontSize) / 1000));
						System.out.println(this.indent + " - average explicit space width is " + explicitSpaceWidth + " (capped off from " + mExplicitSpaceWidth + ")");
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
		
		private float measureExplicitSpaceWidth(List stros) {
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
					System.out.println(this.indent + "Could not compute width of explicit spaces");
				return 0;
			}
			else {
				float exSpaceWidth = (exSpaceWidthSum / exSpaceCount);
				if (DEBUG_RENDER_PAGE_CONTENT)
					System.out.println(this.indent + "Effective width of explicit spaces measured as " + exSpaceWidth + " from " + exSpaceCount + " spaces");
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
			
			//	TODO test if char sequence is actually to be interpreted two bytes
			cscs = checkForSplitBytePairs(cscs, this.pcrFont, this.indent);
			
			//	render characters
			for (int c = 0; c < cscs.length; c++) {
				char ch = cscs[c];
				
				//	split up two bytes conflated in one (only if font doesn't know them as one, and does know them as two, though)
				if ((255 < ch) && !this.pcrFont.ucMappings.containsKey(new Integer((int) ch))) {
					int hch = (ch >>> 8);
					int lch = (ch & 255);
					boolean split = false;
					if (this.pcrFont.ucMappings.containsKey(new Integer((int) hch)) && this.pcrFont.ucMappings.containsKey(new Integer((int) lch))) {
//						cscs[c] = ((char) (ch & 255)); // store low byte for next round
//						ch = ((char) (ch >>> 8)); // move high byte into rendering range
						if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
							System.out.println(this.indent + "Handling high byte char '" + ch + "' (" + ((int) ch) + ", " + Integer.toString(((int) ch), 16).toUpperCase() + ") for now, coming back for low byte char '" + cscs[c] + "' (" + ((int) cscs[c]) + ", " + Integer.toString(((int) cscs[c]), 16).toUpperCase() + ") next round (UC mapping)");
//						c--; // come back for low byte in the next round
						split = true;
					}
					else if (this.pcrFont.isBuiltInFont) {
						if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
							System.out.println(this.indent + "Handling high byte char '" + ch + "' (" + ((int) ch) + ", " + Integer.toString(((int) ch), 16).toUpperCase() + ") for now, coming back for low byte char '" + cscs[c] + "' (" + ((int) cscs[c]) + ", " + Integer.toString(((int) cscs[c]), 16).toUpperCase() + ") next round (built-in font)");
						split = true;
					}
					else if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
						System.out.println(this.indent + "Retaining non-ASCII char '" + ch + "' (" + ((int) ch) + ", " + Integer.toString(((int) ch), 16).toUpperCase() + ") for lack of Unicode mapping on parts.");
					if (split) {
						cscs[c] = ((char) (ch & 255)); // store low byte for next round
						ch = ((char) (ch >>> 8)); // move high byte into rendering range
						c--; // come back for low byte in the next round
					}
				}
				char fCh = this.pcrFont.getChar((int) ch); // the character the font says we're rendering (no matter what it actually renders, Acrobat goes by that in terms of spacing)
				boolean fIsSpace = (fCh == ' '); // is the font indicated character a space (32, 0x20)?
				String rCh; // the character actually represented by the glyph we're rendering
				boolean rIsSpace; // is the actually rendered character as space (<= 32, 0x20)?
				float nextTjShift = (((c+1) == cscs.length) ? nextShift : 0); // what shifts did we just have, or do we have coming up (might well erradicate space)?
//				boolean nextIsImplicitSpace = (this.pcrFont.hasImplicitSpaces || ((this.pcrCharSpacing + (fIsSpace ? this.pcrWordSpacing : 0) - nextTjShift) > ((this.pcrFontSpaceWidth * this.narrowSpaceToleranceFactor) / 1000))); // do TJ shifts, char or word spacing, or excessive glyph width imply rendering a space?
				boolean nextIsImplicitSpace = (this.pcrFont.hasImplicitSpaces || ((this.pcrCharSpacing + (fIsSpace ? this.pcrWordSpacing : 0) - nextTjShift) > ((Math.max(this.pcrFontSpaceWidth, minFontSpaceWidth) * this.narrowSpaceToleranceFactor) / 1000))); // do TJ shifts, char or word spacing, or excessive glyph width imply rendering a space?
				if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts) {
					System.out.println(this.indent + "Handling char '" + ch + "' (" + ((int) ch) + ", " + Integer.toString(((int) ch), 16).toUpperCase() + ")");
					System.out.println(this.indent + " - char spacing is " + this.pcrCharSpacing);
					System.out.println(" - word spacing is " + this.pcrWordSpacing);
					//System.out.println(" - scaling is " + this.pcrHorizontalScaling); got no example where not neutral
					System.out.println(this.indent + " - font space indication is " + fIsSpace);
					System.out.println(this.indent + " - following TJ array shift is " + nextTjShift);
					System.out.println(this.indent + " - implicit space is " + nextIsImplicitSpace);
				}
				
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
					//	also recognize spaces ignored by trim() (NBSP as well as 0x20xx spaces)
					if ((rCh.length() == 1) && ("\u00A0\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200A\u200B\u202F\u205F".indexOf(rCh.charAt(0)) != -1 /* NBSP, ignored by trim() */))
						rCh = " ";
					rIsSpace = (rCh.trim().length() == 0);
				}
				
				//	rendering whitespace char, remember end of previous word (if any)
				if (rIsSpace) {
					//	compute effective space width and effective last shift (only at start and end of char sequence, though)
					//	copy from width computation resulting from drawChar()
					float spaceWidth = ((((this.pcrFont.getCharWidth(ch) * this.pcrFontSize) + ((this.pcrCharSpacing + (fIsSpace ? this.pcrWordSpacing : 0)) * 1000)) * this.pcrHorizontalScaling) / (1000 * 100));
					float spaceCharWidth = ((((this.pcrFont.getCharWidth(ch) * this.pcrFontSize)) * this.pcrHorizontalScaling) / (1000 * 100));
					//	copy from width computation resulting from array shift
					float prevShiftWidth = ((c == 0) ? (prevShift * this.pcrFontSize) : 0.0f);
					float nextShiftWidth = (((c+1) == cscs.length) ? (nextShift * this.pcrFontSize) : 0.0f);
					if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
						System.out.println(this.indent + " - space is " + spaceWidth + " (char " + spaceCharWidth + "), shifts are " + prevShiftWidth + " and " + nextShiftWidth);
					
					//	end word if space not compensated (to at least 95%) by previous left (positive) shift and/or subsequent (positive) left shift
					if ((spaceWidth * 19) > ((prevShiftWidth + nextShiftWidth) * 20)) {
						if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
							System.out.println(this.indent + " ==> space accepted for below 95% compensation at " + (((prevShiftWidth + nextShiftWidth) * 100) / spaceWidth) + "%");
						this.endWord();
						rendered.append(' ');
					}
					//	end word if space wider than specified by font as well, so shift compensation assessment does not backfire in tables that separate columns via excessive word spacing (results in very wide spaces that are easily compensated beyond 95% and still wide enough for an actual space)
					else if (((spaceWidth - prevShiftWidth - nextShiftWidth) * 5) > (spaceCharWidth * 4)) {
						if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
							System.out.println(this.indent + " ==> space accepted for width above 80% of font specified " + spaceCharWidth + " (" + ((spaceCharWidth * 4) / 5) + ") at " + (spaceWidth - prevShiftWidth - nextShiftWidth));
						this.endWord();
						rendered.append(' ');
					}
					else if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
						System.out.println(this.indent + " ==> space rejected as compensated by shifts");
				}
				
				//	rendering non-whitespace char, remember start of word
				else {
					this.startWord();
					String nrCh = dissolveLigature(rCh, this.indent);
					if ((this.wordCharCodes != null) && (this.wordString != null)) {
						int chc = ((this.pcrFont == null) ? ((int) ch) : this.pcrFont.getCharCode((int) ch));
						this.wordCharCodes.append((char) ((nrCh.length() * 256) | chc));
						this.wordString.append(nrCh);
					}
					rendered.append(nrCh);
				}
				
				//	update state (move caret forward)
				this.drawChar(ch, fIsSpace, nextIsImplicitSpace, rCh);
				
				//	remember char usage (only now, after word is started)
				if (this.assessFonts)
					this.pcrFont.setCharUsed((int) ch);
				
				//	extreme char or word spacing, add space
				if (nextIsImplicitSpace) {
					if (!rIsSpace)
						rendered.append(' ');
					this.endWord();
					//	APPEARS TO WORK JUST FINE
					float spaceWidth = (((((this.pcrCharSpacing + (fIsSpace ? this.pcrWordSpacing : 0)) * 1000)) * this.pcrHorizontalScaling) / (1000 * 100));
					//	MORE LOGICAL, BUT ABOVE SEEMS TO WORK JUST FINE
//					float spaceWidth = (((((fIsSpace ? this.pcrWordSpacing : this.pcrCharSpacing) * 1000)) * this.pcrHorizontalScaling) / (1000 * 100));
					if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts) {
						System.out.println(this.indent + "Drawing implicit space of width " + spaceWidth);
//						System.out.println("  (" + this.pcrCharSpacing + " + (" + fIsSpace + " ? " + this.pcrWordSpacing + " : " + 0 + ")) > ((" + this.pcrFontSpaceWidth + " * " + this.narrowSpaceToleranceFactor + ") / " + 1000 + ")");
//						System.out.println("  (" + this.pcrCharSpacing + " + " + (fIsSpace ? this.pcrWordSpacing : 0) + ") > (" + (this.pcrFontSpaceWidth * this.narrowSpaceToleranceFactor) + " / " + 1000 + ")");
//						System.out.println("  " + (this.pcrCharSpacing + (fIsSpace ? this.pcrWordSpacing : 0)) + " > " + ((this.pcrFontSpaceWidth * this.narrowSpaceToleranceFactor) / 1000) + "");
						System.out.println(this.indent + "  (" + this.pcrCharSpacing + " + (" + fIsSpace + " ? " + this.pcrWordSpacing + " : " + 0 + ") - " + nextTjShift + ") > ((Math.max(" + this.pcrFontSpaceWidth + ", " + minFontSpaceWidth + ") * " + this.narrowSpaceToleranceFactor + ") / " + 1000 + ")");
						System.out.println(this.indent + "  (" + this.pcrCharSpacing + " + " + (fIsSpace ? this.pcrWordSpacing : 0) + " - " + nextTjShift + ") > (" + (Math.max(this.pcrFontSpaceWidth, minFontSpaceWidth) * this.narrowSpaceToleranceFactor) + " / " + 1000 + ")");
						System.out.println(this.indent + "  " + (this.pcrCharSpacing + (fIsSpace ? this.pcrWordSpacing : 0) - nextTjShift) + " > " + ((Math.max(this.pcrFontSpaceWidth, minFontSpaceWidth) * this.narrowSpaceToleranceFactor) / 1000) + "");
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
//			float width = (((((this.pcrFont.hasImplicitSpaces ? this.pcrFont.getMeasuredCharWidth(ch) : this.pcrFont.getCharWidth(ch)) * this.pcrFontSize) + (spacing * 1000)) * this.pcrHorizontalScaling) / (1000 * 100));
			float chWidth = (((((this.pcrFont.hasImplicitSpaces ? this.pcrFont.getMeasuredCharWidth(ch) : this.pcrFont.getCharWidth(ch)) * this.pcrFontSize)) * this.pcrHorizontalScaling) / (1000 * 100));
			float spWidth = ((spacing * this.pcrHorizontalScaling) / 100);
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
//				System.out.println("Drawing char '" + ch + "' (" + ((int) ch) + ", " + Integer.toString(((int) ch), 16).toUpperCase() + ") ==> '" + rCh + "' (" + ((int) rCh.charAt(0)) + ", '" + dissolveLigature(rCh) + "') with width " + width + " (" + (this.pcrFont.hasImplicitSpaces ? this.pcrFont.getMeasuredCharWidth(ch) : this.pcrFont.getCharWidth(ch)) + " with spacing " + spacing + ")");
				System.out.println(this.indent + "Drawing char '" + ch + "' (" + ((int) ch) + ", " + Integer.toString(((int) ch), 16).toUpperCase() + ") ==> '" + rCh + "' (" + ((int) rCh.charAt(0)) + ", '" + dissolveLigature(rCh, this.indent) + "') with width " + chWidth + " (" + (this.pcrFont.hasImplicitSpaces ? this.pcrFont.getMeasuredCharWidth(ch) : this.pcrFont.getCharWidth(ch)) + " with spacing " + spWidth + ")");
//			updateMatrix(this.lineMatrix, width, 0);
			this.drawPendingSpace(); // need to do this before drawing next char
			updateMatrix(this.lineMatrix, chWidth, 0);
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				this.printPosition();
//			updateMatrix(this.lineMatrix, spWidth, 0);
//			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
//				this.printPosition();
			this.pendingSpace = spWidth;
		}
		
		private void drawPendingSpace() {
			if (this.pendingSpace == 0)
				return;
			updateMatrix(this.lineMatrix, this.pendingSpace, 0);
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				this.printPosition();
			this.pendingSpace = 0;
		}
		
		private void printPosition() {
			float[] start = transform(0, 0, 1, this.lineMatrix, PRotate.identity, this.indent);
			start = this.applyTransformationMatrices(start);
			System.out.println(this.indent + "NOW AT " + Arrays.toString(start));
		}
		
		private void startLine() {
			if ((this.top != null) && (this.bottom != null))
				return;
			if (this.pcrFont == null)
				return;
			this.top = transform(0, (Math.min(this.pcrFont.capHeight, this.pcrFont.ascent) * this.pcrFontSize), 1, this.lineMatrix, PRotate.identity, this.indent);
			this.top = this.applyTransformationMatrices(this.top);
			this.bottom = transform(0, (this.pcrFont.descent * this.pcrFontSize), 1, this.lineMatrix, PRotate.identity, this.indent);
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
			this.drawPendingSpace(); // need to do this before computing word start position
			this.wordCharCodes = new StringBuffer();
			this.wordString = new StringBuffer();
			this.start = transform(0, 0, 1, this.lineMatrix, PRotate.identity, this.indent);
			this.start = this.applyTransformationMatrices(this.start);
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
				System.out.println(this.indent + " start set to " + Arrays.toString(this.start));
		}
		
		private void endWord() {
			if (this.start == null)
				return;
			if (this.assessFonts)
				this.pcrFont.endWord();
			float[] end = transform(0, 0, 1, this.lineMatrix, PRotate.identity, this.indent);
			end = this.applyTransformationMatrices(end);
			float eTextRise = ((this.pcrTextRise * this.eFontSize) / this.pcrFontSize); // use font size modification to assess effect of current transformation
			if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts) {
				System.out.println(this.indent + "Got word '" + this.wordString.toString() + "' at RON " + this.nextRenderOrderNumber + ":");
				System.out.println(this.indent + " - colors are " + this.pcrStrokeColor + " (S) / " + this.pcrNonStrokeColor + " (NS)");
				System.out.println(this.indent + " - start is " + Arrays.toString(this.start));
				System.out.println(this.indent + " - end is " + Arrays.toString(end));
				System.out.println(this.indent + " - top is " + Arrays.toString(this.top));
				System.out.println(this.indent + " - bottom is " + Arrays.toString(this.bottom));
				System.out.println(this.indent + " - text rise is " + this.pcrTextRise + " (" + eTextRise + " effective)");
				if (this.eFontDirection == PWord.LEFT_RIGHT_FONT_DIRECTION)
					System.out.println(this.indent + " --> extent is " + this.start[0] + "-" + end[0] + " x " + (this.top[1] + eTextRise) + "-" + (this.bottom[1] + eTextRise));
				else if (this.eFontDirection == PWord.BOTTOM_UP_FONT_DIRECTION)
					System.out.println(this.indent + " --> extent is " + (this.top[0] - eTextRise) + "-" + (this.bottom[0] - eTextRise) + " x " + this.start[1] + "-" + end[1]);
				else if (this.eFontDirection == PWord.TOP_DOWN_FONT_DIRECTION)
					System.out.println(this.indent + " --> extent is " + (this.bottom[0] + eTextRise) + "-" + (this.top[0] + eTextRise) + " x " + end[1] + "-" + this.start[1]);
				if (this.eFontDirection == PWord.UPSIDE_DOWN_FONT_DIRECTION)
					System.out.println(this.indent + " --> extent is " + end[0] + "-" + this.start[0] + " x " + (this.bottom[1] - eTextRise) + "-" + (this.top[1] - eTextRise));
				System.out.println(this.indent + " - clipping path stack is " + this.pcrClippingPathStack);
			}
			float pwbl = Float.NaN;
			Rectangle2D.Float pwb = null;
			//	correction seems to be causing a bit of trouble, switched off for now
			if (this.eFontDirection == PWord.LEFT_RIGHT_FONT_DIRECTION) {
				pwbl = this.start[1];
				pwb = new Rectangle2D.Float(this.start[0], (this.bottom[1] + eTextRise), (end[0] - this.start[0]), (this.top[1] - this.bottom[1]));
			}
			else if (this.eFontDirection == PWord.BOTTOM_UP_FONT_DIRECTION) {
				pwbl = this.start[0];
				pwb = new Rectangle2D.Float((this.top[0] - eTextRise), this.start[1], (this.bottom[0] - this.top[0]), (end[1] - this.start[1]));
			}
			else if (this.eFontDirection == PWord.TOP_DOWN_FONT_DIRECTION) {
				pwbl = this.start[0];
				pwb = new Rectangle2D.Float((this.bottom[0] + eTextRise), end[1], (this.top[0] - this.bottom[0]), (this.start[1] - end[1]));
			}
			else if (this.eFontDirection == PWord.UPSIDE_DOWN_FONT_DIRECTION) {
				pwbl = this.start[1];
				pwb = new Rectangle2D.Float(end[0], (this.top[1] - eTextRise), (this.start[0] - end[0]), (this.bottom[1] - this.top[1]));
			}
			if (pwb != null) {
				if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
					System.out.println(this.indent + " - bounds are " + pwb);
				for (Iterator cpit = this.pcrClippingPathStack.iterator(); cpit.hasNext();) {
					PPath cp = ((PPath) cpit.next());
					Rectangle2D.Float cpb = cp.getBounds();
					if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
						System.out.println(this.indent + "   - checking against clipping path bounds " + cpb);
					if (!cpb.intersects(pwb)) {
						pwb = null;
						if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
							System.out.println(this.indent + "     --> clipped completely");
						break;
					}
					else if (!cpb.contains(pwb)) {
						Rectangle2D.intersect(cpb, pwb, pwb);
						if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
							System.out.println(this.indent + "     --> clipped to " + pwb);
					}
					if (DEBUG_RENDER_PAGE_CONTENT && !this.assessFonts)
						System.out.println(this.indent + "     --> contained");
				}
				if ((pwb != null) && (this.wordsAreOcr || (this.pcrTextRenderingMode != 3 /* born-digital and invisible */))) {
					Color pwColor = null;
					if (this.wordsAreOcr)
						pwColor = ((this.pcrTextRenderingMode >= 3) ? clipWordColor : Color.BLACK); // required for generic decoding (clip words will be filtered by alpha in born-digital mode)
					else if ((this.pcrTextRenderingMode == 1) || (this.pcrTextRenderingMode == 5))
						pwColor = this.getStrokeColor(); // outline only, which Java2D doesn't support, so we still fill, but with stroking color
					else if (this.pcrTextRenderingMode == 7)
						pwColor = clipWordColor;
					else pwColor = this.getNonStrokeColor(); // unless purely clipping, use filling (non-stroking) color for rendering
					PWord pw;
					if (this.assessFonts)
						pw = new PWord(this.nextRenderOrderNumber++, this.wordCharCodes.toString(), pwbl, pwb, pwColor, Math.round(this.eFontSize), this.eFontDirection, this.pcrFont);
					else pw = new PWord(this.nextRenderOrderNumber++, this.wordCharCodes.toString(), this.wordString.toString(), pwbl, pwb, pwColor, Math.round(this.eFontSize), this.eFontDirection, this.pcrFont);
					if (this.wordsAreOcr || (this.pcrTextRenderingMode != 7))
						this.words.add(pw);
					if (this.pcrTextRenderingMode >= 4)
						this.pcrClipWords.add(pw);
				}
			}
			this.drawPendingSpace(); // need to do this after computing word end position
			this.pendingSpace = 0;
			this.wordCharCodes = null;
			this.wordString = null;
			this.start = null;
		}
		
		private static char[] checkForSplitBytePairs(char[] chars, PdfFont font, String indent) {
			if ((chars.length % 2) != 0)
				return chars; // no way of pairwise aggregating an odd number of bytes
			for (int c = 0; c < chars.length; c += 2) {
				if ((chars[c] > 255) || (chars[c+1] > 255))
					return chars; // if we have chars with the high byte in use, we cannot aggregate
				if (!font.ucMappings.containsKey(new Integer((chars[c] << 8) | chars[c+1])))
					return chars; // aggregate char code not in use
			}
			char[] aChars = new char[chars.length / 2];
			for (int c = 0; c < chars.length; c += 2)
				aChars[c / 2] = ((char) ((chars[c] << 8) | chars[c+1]));
			if (DEBUG_RENDER_PAGE_CONTENT)
				System.out.println(indent + "Aggregated " + Arrays.toString(chars) + " to " + Arrays.toString(aChars));
			return aChars;
		}
		
		private static String dissolveLigature(String rCh, String indent) {
			if (rCh.length() != 1)
				return rCh;
			String nrCh = StringUtils.getNormalForm(rCh.charAt(0));
			if (nrCh.length() == 1)
				return rCh;
			if (DEBUG_RENDER_PAGE_CONTENT)
				System.out.println(indent + "Dissolved '" + rCh + "' to '" + nrCh + "'");
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
	private static final Color clipWordColor = new Color(0, 0, 0, 0); // fully transparent black
	
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
			return ((mode == null) ? null : getInstance(name, mode.intValue()));
		}
		
		static BlendComposite getInstance(String name, float alpha) {
			Integer mode = ((Integer) namesToModes.get(name));
			return ((mode == null) ? null : getInstance(name, mode.intValue(), alpha));
		}
		
		static BlendComposite getInstance(String name, int mode) {
			return new BlendComposite(name, mode);
		}
		
		static BlendComposite getInstance(String name, int mode, float alpha) {
			return new BlendComposite(name, mode, alpha);
		}
		
		final String name;
		private float alpha;
		private int mode;
		
		private BlendComposite(String name, int mode) {
			this(name, mode, 1.0f);
		}
		
		private BlendComposite(String name, int mode, float alpha) {
			this.name = name;
			this.mode = mode;
//			setAlpha(alpha);
			this.alpha = alpha;
		}
		
		boolean lightIsTranslucent() {
			return ((this.mode == blendModeMultiply) || (this.mode == blendModeDarken) || (this.mode == blendModeColorBurn));
		}
		
		boolean darkIsTranslucent() {
			return ((this.mode == blendModeScreen) || (this.mode == blendModeLighten) || (this.mode == blendModeColorDodge) || (this.mode == blendModeDifference) || (this.mode == blendModeExclusion));
		}
//		
//		private void setAlpha(float alpha) {
//			this.alpha = alpha;
//		}
		
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
		
		public String toString() {
			return (this.name + " (" + this.alpha + ")");
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
//				if (src.getSampleModel().getDataType() != DataBuffer.TYPE_INT ||
//					dstIn.getSampleModel().getDataType() != DataBuffer.TYPE_INT ||
//					dstOut.getSampleModel().getDataType() != DataBuffer.TYPE_INT) {
//					throw new IllegalArgumentException("Source and destination must store pixels as INT (composite is " + this.composite.name + ").");
//				}
				if (dstIn.getSampleModel().getDataType() != dstOut.getSampleModel().getDataType())
					throw new IllegalArgumentException("Destination input and output must store pixels as the same type (composite is " + this.composite.name + ").");
				
				int width = Math.min(src.getWidth(), dstIn.getWidth());
				int height = Math.min(src.getHeight(), dstIn.getHeight());
				float alpha = this.composite.alpha;
				
				int[] srcPixel = new int[4];
				int[] dstPixel = new int[4];
//				int[] srcPixels = new int[width];
//				int[] dstPixels = new int[width];
				Object srcPixels = getPixelRowArray(src.getSampleModel().getDataType(), width);
				Object dstPixels = getPixelRowArray(dstIn.getSampleModel().getDataType(), width);
				
				for (int y = 0; y < height; y++) {
					src.getDataElements(0, y, width, 1, srcPixels);
					dstIn.getDataElements(0, y, width, 1, dstPixels);
					for (int x = 0; x < width; x++) {
						// pixels are stored as INT_ARGB
						// our arrays are [R, G, B, A]
//						int pixel = srcPixels[x];
						int pixel = getPixel(srcPixels, x);
						srcPixel[0] = (pixel >> 16) & 0xFF;
						srcPixel[1] = (pixel >>  8) & 0xFF;
						srcPixel[2] = (pixel	  ) & 0xFF;
						srcPixel[3] = (pixel >> 24) & 0xFF;
						
//						pixel = dstPixels[x];
						pixel = getPixel(dstPixels, x);
						dstPixel[0] = (pixel >> 16) & 0xFF;
						dstPixel[1] = (pixel >>  8) & 0xFF;
						dstPixel[2] = (pixel	  ) & 0xFF;
						dstPixel[3] = (pixel >> 24) & 0xFF;
						
						int[] result = this.blender.blend(srcPixel, dstPixel);
						
						// mixes the result with the opacity
//						dstPixels[x] = ((int) (dstPixel[3] + (result[3] - dstPixel[3]) * alpha) & 0xFF) << 24 |
//									   ((int) (dstPixel[0] + (result[0] - dstPixel[0]) * alpha) & 0xFF) << 16 |
//									   ((int) (dstPixel[1] + (result[1] - dstPixel[1]) * alpha) & 0xFF) <<  8 |
//										(int) (dstPixel[2] + (result[2] - dstPixel[2]) * alpha) & 0xFF;
						pixel = ((int) (dstPixel[3] + (result[3] - dstPixel[3]) * alpha) & 0xFF) << 24 |
								((int) (dstPixel[0] + (result[0] - dstPixel[0]) * alpha) & 0xFF) << 16 |
								((int) (dstPixel[1] + (result[1] - dstPixel[1]) * alpha) & 0xFF) <<  8 |
								 (int) (dstPixel[2] + (result[2] - dstPixel[2]) * alpha) & 0xFF;
						setPixel(dstPixels, x, pixel);
					}
					dstOut.setDataElements(0, y, width, 1, dstPixels);
				}
			}
			
			private static Object getPixelRowArray(int type, int length) {
				if (type == DataBuffer.TYPE_BYTE)
					return new byte[length];
				else if (type == DataBuffer.TYPE_SHORT)
					return new short[length];
				else if (type == DataBuffer.TYPE_USHORT)
					return new short[length];
				else return new int[length];
			}
			
			private static int getPixel(Object pixelArray, int index) {
				if (pixelArray instanceof byte[]) {
					int pixelByte = (((byte[]) pixelArray)[index] & 0xFF);
					return (0xFF000000 | (pixelByte << 16) | (pixelByte << 8) | (pixelByte << 0));
				}
				else if (pixelArray instanceof short[]) {
					int pixelByte = (((short[]) pixelArray)[index] & 0xFFFF);
					pixelByte >>= 8;
					return (0xFF000000 | (pixelByte << 16) | (pixelByte << 8) | (pixelByte << 0));
				}
				else return ((int[]) pixelArray)[index];
			}
			
			private static void setPixel(Object pixelArray, int index, int pixel) {
				if (pixelArray instanceof byte[]) {
					int red = ((pixel >> 16) & 0xFF);
					int green = ((pixel >> 8) & 0xFF);
					int blue = ((pixel >> 0) & 0xFF);
					int pixelByte = ((int) ((0.299f * red) + (0.587f * green) + (0.114f * blue)));
					((byte[]) pixelArray)[index] = ((byte) (pixelByte & 0xFF));
				}
				else if (pixelArray instanceof short[]) {
					int red = (((pixel >> 16) & 0xFF) << 8);
					int green = (((pixel >> 8) & 0xFF) << 8);
					int blue = (((pixel >> 0) & 0xFF) << 8);
					int pixelByte = ((int) ((0.299f * red) + (0.587f * green) + (0.114f * blue)));
					((short[]) pixelArray)[index] = ((short) (pixelByte & 0xFFFF));
				}
				else ((int[]) pixelArray)[index] = pixel;
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
									dst[0] + src[0] - ((dst[0] * src[0]) >> 7),
									dst[1] + src[1] - ((dst[1] * src[1]) >> 7),
									dst[2] + src[2] - ((dst[2] * src[2]) >> 7),
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