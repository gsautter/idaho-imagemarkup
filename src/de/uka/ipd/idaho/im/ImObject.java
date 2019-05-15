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
