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
package de.uka.ipd.idaho.im;

import de.uka.ipd.idaho.easyIO.util.RandomByteSource;
import de.uka.ipd.idaho.gamta.Attributed;
import de.uka.ipd.idaho.gamta.util.imaging.ImagingConstants;

/**
 * An object in an image markup document.
 * 
 * @author sautter
 */
public interface ImObject extends Attributed, ImagingConstants {
	
	/** the attribute to store the string of custom attributes in, e.g. in persistent storage or serialization */
	public static final String ATTRIBUTES_STRING_ATTRIBUTE = "attributes";
	
	/**
	 * Retrieve the type of the object.
	 * @return the type
	 */
	public abstract String getType();
	
	/**
	 * Set the type of the object. Implementations of this method have to
	 * announce the type change via the <code>notifyTypeChanged()</code>
	 * method of the parent document.
	 * @param type the new type
	 */
	public abstract void setType(String type);
	
	/**
	 * Retrieve the document local identifier of the object. Implementations
	 * must return a value that is unique within the scope of a single Image
	 * Markup document, equal local IDs indicating object equality.
	 * @return the document local ID
	 */
	public abstract String getLocalID();
	
	/**
	 * Retrieve the document local unique identifier of the object, differing
	 * from the local ID in that it is a 128 bit ID in HEX notation.
	 * @return the document local unique ID
	 */
	public abstract String getLocalUID();
	
	/**
	 * Retrieve the UUID of the object. Implementations should use the local
	 * UID and combine it with the document UUID using XOR.
	 * @return the UUID of the object
	 */
	public abstract String getUUID();
	
	/**
	 * Retrieve the image markup document the object belongs to.
	 * @return the document the object belongs to
	 */
	public abstract ImDocument getDocument();
	
	/**
	 * Get a property of the document this object belongs to.
	 * @param propertyName the name of the property
	 * @return the value of the requested property, or null, if there is no
	 *         property with the specified name
	 */
	public abstract String getDocumentProperty(String propertyName);
	
	/**
	 * Get a property of the document this object belongs to.
	 * @param propertyName the name of the property
	 * @param defaultValue the value to return if there is no property with the
	 *            specified name, or if this Annotation does not belong to a
	 *            document
	 * @return the value of the requested property, or the specified def value,
	 *         if there is no property with the specified name
	 */
	public abstract String getDocumentProperty(String propertyName, String defaultValue);
	
	/**
	 * Get the names of all properties of the document.
	 * @return an array holding the names of all properties of the document
	 *         this object belongs to
	 */
	public abstract String[] getDocumentPropertyNames();
	
	/**
	 * Carrier class of helper methods for UID and UUID computation.
	 * 
	 * @author sautter
	 */
	public static class UuidHelper {
		
		/**
		 * Combine an object type with the page extent and top-left and
		 * bottom-right corner into a local object UID.
		 * @param type the object type
		 * @param firstPageId the first page ID
		 * @param lastPageId the last page ID
		 * @param left the left value of the top-left corner
		 * @param top the top value of the top-left corner
		 * @param right the right value of the bottom-right corner
		 * @param bottom the bottom value of the bottom-right corner
		 * @return the local UID of the object described by the arguments
		 */
		public static String getLocalUID(String type, int firstPageId, int lastPageId, int left, int top, int right, int bottom) {
			//	THIS IS BETTER, AS FIRST TOP LEFT AND LAST BOTTOM RIGHT ARE JUST AS UNIQUE,
			//	BUT ARE INSENSITIVE TO SPLITTING AND MERGING OF WORDS
			return (""
					// 4 byte of type hash
					+ asHex(type.hashCode(), 4)
					// 2 + 2 bytes of page IDs
					+ asHex(firstPageId, 2) + asHex(lastPageId, 2)
					// 2 * 4 bytes of bounding boxes (left top of first word, right bottom of last word)
					+ asHex(left, 2)
					+ asHex(top, 2)
					+ asHex(right, 2)
					+ asHex(bottom, 2)
				);
		}
		
		/**
		 * Compute the UUID of an object. If the argument object is not
		 * attached to a document, this method returns null.
		 * @param obj the object to compute the UUID for
		 * @return the UUID of the argument object
		 */
		public static String getUUID(ImObject obj) {
			ImDocument doc = obj.getDocument();
			return ((doc == null) ? null : getUUID(obj, doc.docId));
		}
		
		/**
		 * Compute the UUID of an object and a given document UUID.
		 * @param obj the annotation to compute the UUID for
		 * @param docId the UUID of the document the argument object belongs to
		 * @return the UUID of the argument object
		 */
		public static String getUUID(ImObject obj, String docId) {
			if ((docId.length() == 32) && docId.matches("[0-9A-Fa-f]++"))
				docId = docId.toUpperCase();
			else {
				docId = (""
						+ asHex(docId.hashCode(), 4)
						+ asHex(docId.hashCode(), 4)
						+ asHex(docId.hashCode(), 4)
						+ asHex(docId.hashCode(), 4)
					);
			}
			return RandomByteSource.getHexXor(obj.getLocalUID(), docId);
		}
		
		/**
		 * Convert the bytes of an integer into HEX representation. If the
		 * required number of bytes <code>bytes</code> is less than 4, the
		 * bytes are taken from the significant end of the argument int. The
		 * length of the returned string is <code>bytes * 2</code>.
		 * @param i the int to convert
		 * @param bytes the required number of bytes
		 * @return the HEX representation of the required bytes
		 */
		static String asHex(int i, int bytes) {
			StringBuffer hex = new StringBuffer();
			while (bytes != 0) {
				hex.insert(0, hexDigits.charAt(i & 0xF));
				i >>>= 4;
				hex.insert(0, hexDigits.charAt(i & 0xF));
				i >>>= 4;
				bytes--;
			}
			return hex.toString();
		}
		private static final String hexDigits = "0123456789ABCDEF";
	}
}
