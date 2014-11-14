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
package de.uka.ipd.idaho.im;

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
}
//public abstract class ImObject extends AbstractAttributed implements ImagingConstants {
//	
//	/** the attribute to store the string of custom attributes in, e.g. in persistent storage or serialization */
//	public static final String ATTRIBUTES_STRING_ATTRIBUTE = "attributes";
//	
//	/** the ID of the document the object belongs to */
//	public final String docId;
//	
//	private String type = "object";
//	
//	/** Constructor
//	 * @param docId the ID of the document the object belongs to
//	 */
//	protected ImObject(String docId) {
//		this.docId = docId;
//	}
//	
//	/**
//	 * Retrieve the type of the object.
//	 * @return the type
//	 */
//	public String getType() {
//		return this.type;
//	}
//	
//	/**
//	 * Set the type of the object.
//	 * @param type the new type
//	 */
//	public void setType(String type) {
//		this.type = type;
//	}
//	
//	/**
//	 * Retrieve the image markup document the object belongs to.
//	 * @return the document the object belongs to
//	 */
//	public abstract ImDocument getDocument();
//	
//	/**
//	 * Get a property of the document this object belongs to.
//	 * @param propertyName the name of the property
//	 * @return the value of the requested property, or null, if there is no
//	 *         property with the specified name
//	 */
//	public String getDocumentProperty(String propertyName) {
//		return this.getDocument().getDocumentProperty(propertyName);
//	}
//	
//	/**
//	 * Get a property of the document this object belongs to.
//	 * @param propertyName the name of the property
//	 * @param defaultValue the value to return if there is no property with the
//	 *            specified name, or if this Annotation does not belong to a
//	 *            document
//	 * @return the value of the requested property, or the specified def value,
//	 *         if there is no property with the specified name
//	 */
//	public String getDocumentProperty(String propertyName, String defaultValue) {
//		return this.getDocument().getDocumentProperty(propertyName, defaultValue);
//	}
//	
//	/**
//	 * Get the names of all properties of the document.
//	 * @return an array holding the names of all properties of the document
//	 *         this object belongs to
//	 */
//	public String[] getDocumentPropertyNames() {
//		return this.getDocument().getDocumentPropertyNames();
//	}
//	
//	/* (non-Javadoc)
//	 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#getAttribute(java.lang.String, java.lang.Object)
//	 */
//	public Object getAttribute(String name, Object def) {
//		if (DOCUMENT_ID_ATTRIBUTE.equals(name))
//			return this.docId;
//		if (TYPE_ATTRIBUTE.equals(name))
//			return this.type;
//		return super.getAttribute(name, def);
//	}
//	
//	/* (non-Javadoc)
//	 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#getAttribute(java.lang.String)
//	 */
//	public Object getAttribute(String name) {
//		if (DOCUMENT_ID_ATTRIBUTE.equals(name))
//			return this.docId;
//		if (TYPE_ATTRIBUTE.equals(name))
//			return this.type;
//		return super.getAttribute(name);
//	}
//	
//	/* (non-Javadoc)
//	 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#hasAttribute(java.lang.String)
//	 */
//	public boolean hasAttribute(String name) {
//		if (DOCUMENT_ID_ATTRIBUTE.equals(name))
//			return true;
//		if (TYPE_ATTRIBUTE.equals(name))
//			return true;
//		return super.hasAttribute(name);
//	}
//	
//	/**
//	 * Retrieve a single-string representation of all generic attributes of this
//	 * object, e.g. for persistent storage or serialization
//	 * @return the attributes string
//	 */
//	public String getAttributesString() {
//		StringBuffer attributes = new StringBuffer();
//		String[] ans = this.getAttributeNames();
//		for (int a = 0; a < ans.length; a++) {
//			Object value = this.getAttribute(ans[a]);
//			if (value != null)
//				attributes.append(ans[a] + "<" + escape(value.toString()) + ">");
//		}
//		return attributes.toString();
//	}
//	private static String escape(String string) {
//		StringBuffer escapedString = new StringBuffer();
//		for (int c = 0; c < string.length(); c++) {
//			char ch = string.charAt(c);
//			if (ch == '<')
//				escapedString.append("&lt;");
//			else if (ch == '>')
//				escapedString.append("&gt;");
//			else if (ch == '"')
//				escapedString.append("&quot;");
//			else if (ch == '&')
//				escapedString.append("&amp;");
//			else if ((ch < 32) || (ch == 127))
//				escapedString.append("&x" + Integer.toString(((int) ch), 16).toUpperCase() + ";");
//			else escapedString.append(ch);
//		}
//		return escapedString.toString();
//	}
//	
//	/**
//	 * Set generic attributes from their single-string representation, as
//	 * returned by <code>getAttributesString()</code>, e.g. on loading or
//	 * de-serialization.
//	 * @param attributes the attributes as a single string
//	 */
//	public void setAttributes(String attributes) {
//		String[] nameValuePairs = attributes.split("[\\<\\>]");
//		for (int p = 0; p < (nameValuePairs.length-1); p+= 2)
//			this.setAttribute(nameValuePairs[p], unescape(nameValuePairs[p+1]));
//	}
//	private static String unescape(String escapedString) {
//		StringBuffer string = new StringBuffer();
//		for (int c = 0; c < escapedString.length();) {
//			char ch = escapedString.charAt(c);
//			if (ch == '&') {
//				if (escapedString.startsWith("amp;", (c+1))) {
//					string.append('&');
//					c+=5;
//				}
//				else if (escapedString.startsWith("lt;", (c+1))) {
//					string.append('<');
//					c+=4;
//				}
//				else if (escapedString.startsWith("gt;", (c+1))) {
//					string.append('>');
//					c+=4;
//				}
//				else if (escapedString.startsWith("quot;", (c+1))) {
//					string.append('"');
//					c+=6;
//				}
//				else if (escapedString.startsWith("x", (c+1))) {
//					int sci = escapedString.indexOf(';', (c+1));
//					if ((sci != -1) && (sci <= (c+4))) try {
//						ch = ((char) Integer.parseInt(escapedString.substring((c+2), sci), 16));
//						c = sci;
//					} catch (Exception e) {}
//					string.append(ch);
//					c++;
//				}
//				else {
//					string.append(ch);
//					c++;
//				}
//			}
//			else {
//				string.append(ch);
//				c++;
//			}
//		}
//		return string.toString();
//	}
//}