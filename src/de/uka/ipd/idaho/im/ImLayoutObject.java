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

import de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed;
import de.uka.ipd.idaho.gamta.util.imaging.BoundingBox;
import de.uka.ipd.idaho.gamta.util.imaging.PageImage;
import de.uka.ipd.idaho.gamta.util.imaging.PageImageInputStream;
import de.uka.ipd.idaho.im.util.ImObjectTransformer;
import de.uka.ipd.idaho.im.util.ImObjectTransformer.AttributeTransformer;

/**
 * A layout related object in an image markup document. If a layout object is
 * removed from the page it lies on, it becomes detached, i.e., the page is set
 * to <code>null</code>. Detached layout objects can be re-associated with the
 * page they belong to, however.
 * 
 * @author sautter
 */
public abstract class ImLayoutObject extends AbstractAttributed implements ImObject {
	
	/**
	 * Carrier class of helper methods for local UID and UUID computation.
	 * 
	 * @author sautter
	 */
	public static class LayoutObjectUuidHelper extends UuidHelper {
		
		/**
		 * Combine an object type with the page extent and top-left and
		 * bottom-right corner into a local object UID.
		 * @param type the object type
		 * @param pageId the page ID
		 * @param bounds the bounding box of the object
		 * @return the local UID of the object described by the arguments
		 */
		public static String getLocalUID(String type, int pageId, BoundingBox bounds) {
			return getLocalUID(type, pageId, -1, bounds.left, bounds.top, bounds.right, bounds.bottom);
		}
	}
	
	/** the text direction indicating a layout object oriented left to right */
	public static final String TEXT_DIRECTION_LEFT_RIGHT = "lr";
	
	/** the text direction indicating a layout object oriented bottom up */
	public static final String TEXT_DIRECTION_BOTTOM_UP = "bu";
	
	/** the text direction indicating a layout object oriented top down */
	public static final String TEXT_DIRECTION_TOP_DOWN = "td";
	
	/** the text direction indicating a layout object oriented right to left and upside-down */
	public static final String TEXT_DIRECTION_RIGHT_LEFT_UPSIDE_DOWN = "ud";
	
	/** the name of the attribute holding the text direction a layout object is
	 * oriented in, namely 'textDirection'. Its associated values are 'lr' for
	 * left-to-right (the default, can be omitted), 'bu' for bottom-up, 'td'
	 * for top-down, or 'ud' for upside-down (and thus also right-to-left).
	 * Other values may be added over time to represent non-Cartesian text
	 * orientations. */
	public static final String TEXT_DIRECTION_ATTRIBUTE = "textDirection";
	
	static String transformTextDirection(Object value, ImObjectTransformer transformer) {
		if (TEXT_DIRECTION_LEFT_RIGHT.equals(value) || (value == null) /* the default, need to transform it back */) {
			if (transformer.turnDegrees == 90)
				return TEXT_DIRECTION_TOP_DOWN;
			else if (transformer.turnDegrees == -90)
				return TEXT_DIRECTION_BOTTOM_UP;
			else return null;//TEXT_DIRECTION_LEFT_RIGHT;
		}
		else if (TEXT_DIRECTION_BOTTOM_UP.equals(value)) {
			if (transformer.turnDegrees == 90)
				return null;//TEXT_DIRECTION_LEFT_RIGHT;
			else if (transformer.turnDegrees == -90)
				return TEXT_DIRECTION_RIGHT_LEFT_UPSIDE_DOWN;
			else return TEXT_DIRECTION_BOTTOM_UP;
		}
		else if (TEXT_DIRECTION_TOP_DOWN.equals(value)) {
			if (transformer.turnDegrees == 90)
				return TEXT_DIRECTION_RIGHT_LEFT_UPSIDE_DOWN;
			else if (transformer.turnDegrees == -90)
				return null;//TEXT_DIRECTION_LEFT_RIGHT;
			else return TEXT_DIRECTION_TOP_DOWN;
		}
		else if (TEXT_DIRECTION_RIGHT_LEFT_UPSIDE_DOWN.equals(value)) {
			if (transformer.turnDegrees == 90)
				return TEXT_DIRECTION_BOTTOM_UP;
			else if (transformer.turnDegrees == -90)
				return TEXT_DIRECTION_TOP_DOWN;
			else return TEXT_DIRECTION_RIGHT_LEFT_UPSIDE_DOWN;
		}
		else return null;
	}
	static {
		ImObjectTransformer.addGlobalAttributeTransformer(new AttributeTransformer() {
			public boolean canTransformAttribute(String name) {
				return TEXT_DIRECTION_ATTRIBUTE.equals(name);
			}
			public Object transformAttributeValue(ImObject object, String name, Object value, ImObjectTransformer transformer) {
				return transformTextDirection(value, transformer);
			}
		});
	}
	
	private ImDocument doc;
	
	private String type = "object";
	
	/** the bounding box enclosing the object on the underlying page image */
	public final BoundingBox bounds;
	
	/** the ID of the page the object lies on */
	public final int pageId;
	
	private String luid = null;
	private String uuid = null;
	
	/** Constructor
	 * @param doc the document the layout object belongs to
	 * @param pageId the ID of the page the object lies in
	 * @param bounds the bounding box defining the layout object
	 */
	protected ImLayoutObject(ImDocument doc, int pageId, BoundingBox bounds) {
		this(doc, pageId, bounds, null);
	}
	
	/** Constructor
	 * @param doc the document the layout object belongs to
	 * @param pageId the ID of the page the layout object lies in
	 * @param bounds the bounding box defining the layout object
	 * @param type the type of the layout object
	 */
	protected ImLayoutObject(ImDocument doc, int pageId, BoundingBox bounds, String type) {
		this.doc = doc;
		this.pageId = pageId;
		this.bounds = bounds;
		if (this.bounds == null)
			throw new IllegalArgumentException("Bounding box is must not be null.");
		this.type = ((type == null) ? "object" : type);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImObject#getType()
	 */
	public String getType() {
		if (ImDocument.TRACK_INSTANCES && (this.doc != null)) this.doc.accessed();
		return this.type;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImObject#setType(java.lang.String)
	 */
	public void setType(String type) {
		if (ImDocument.TRACK_INSTANCES && (this.doc != null)) this.doc.accessed();
		String oldType = this.type;
		this.type = type;
		String oldLuid = this.luid;
		this.luid = null;
		this.uuid = null;
		if (this.getPage() != null) {
			this.getPage().layoutObjectTypeChanged(this, oldType);
			this.doc.notifyTypeChanged(this, oldType, oldLuid);
		}
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImObject#getLocalID()
	 */
	public String getLocalID() {
		if (ImDocument.TRACK_INSTANCES && (this.doc != null)) this.doc.accessed();
		return (this.type + "@" + this.pageId + "." + this.bounds.toString());
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImObject#getLocalUID()
	 */
	public String getLocalUID() {
		if (ImDocument.TRACK_INSTANCES && (this.doc != null)) this.doc.accessed();
		if (this.luid == null)
			this.luid = LayoutObjectUuidHelper.getLocalUID(this.getType(), this.pageId, this.bounds);
		return this.luid;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImObject#getUUID()
	 */
	public String getUUID() {
		if (ImDocument.TRACK_INSTANCES && (this.doc != null)) this.doc.accessed();
		if (this.uuid == null)
			this.uuid = UuidHelper.getUUID(this);
		return this.uuid;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImObject#getDocument()
	 */
	public ImDocument getDocument() {
		if (ImDocument.TRACK_INSTANCES && (this.doc != null)) this.doc.accessed();
		return this.doc;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImObject#getDocumentProperty(java.lang.String)
	 */
	public String getDocumentProperty(String propertyName) {
		return this.getDocument().getDocumentProperty(propertyName);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImObject#getDocumentProperty(java.lang.String, java.lang.String)
	 */
	public String getDocumentProperty(String propertyName, String defaultValue) {
		return this.getDocument().getDocumentProperty(propertyName, defaultValue);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImObject#getDocumentPropertyNames()
	 */
	public String[] getDocumentPropertyNames() {
		return this.getDocument().getDocumentPropertyNames();
	}
	
	/**
	 * Retrieve the page the object lies on.
	 * @return the page the object lies on
	 */
	public abstract ImPage getPage();
	
	/**
	 * Associate the layout object with a page. The <code>ImPage</code> class
	 * calls this method when a layout object is added to it or removed from
	 * it. Client code should rarely need to call this method. When a layout
	 * object is removed from a page, the argument is null;
	 * @param page the page to associate with the layout object
	 */
	public abstract void setPage(ImPage page);
	
	/**
	 * Retrieve an image of this layout object.
	 * @return an image of this layout object
	 */
	public abstract PageImage getImage();
	
	/**
	 * Retrieve an image of the page this layout object lies in.
	 * @return an image of the page this layout object lies in
	 */
	public PageImage getPageImage() {
		try {
			return this.getDocument().getPageImage(this.pageId);
		}
		catch (Exception e) {
			e.printStackTrace(System.out);
			return PageImage.getPageImage(this.getDocument().docId, this.pageId);
		}
	}
	
	/**
	 * Retrieve an input stream of an image of the page this layout object lies
	 * in.
	 * @return an image of the page this layout object lies in
	 */
	public PageImageInputStream getPageImageAsStream() {
		try {
			return this.getDocument().getPageImageAsStream(this.pageId);
		}
		catch (Exception e) {
			e.printStackTrace(System.out);
			return PageImage.getPageImageAsStream(this.getDocument().docId, this.pageId);
		}
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#getAttribute(java.lang.String, java.lang.Object)
	 */
	public Object getAttribute(String name, Object def) {
		if (ImDocument.TRACK_INSTANCES && (this.doc != null)) this.doc.accessed();
		if (DOCUMENT_ID_ATTRIBUTE.equals(name))
			return this.getDocument().docId;
		else if (PAGE_ID_ATTRIBUTE.equals(name))
			return ("" + this.pageId);
		else if (BOUNDING_BOX_ATTRIBUTE.equals(name))
			return this.bounds;
		else return super.getAttribute(name, def);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#getAttribute(java.lang.String)
	 */
	public Object getAttribute(String name) {
		return this.getAttribute(name, null);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#hasAttribute(java.lang.String)
	 */
	public boolean hasAttribute(String name) {
		if (ImDocument.TRACK_INSTANCES && (this.doc != null)) this.doc.accessed();
		if (DOCUMENT_ID_ATTRIBUTE.equals(name))
			return true;
		else if (PAGE_ID_ATTRIBUTE.equals(name))
			return true;
		else if (BOUNDING_BOX_ATTRIBUTE.equals(name))
			return true;
		else return super.hasAttribute(name);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#setAttribute(java.lang.String, java.lang.Object)
	 */
	public Object setAttribute(String name, Object value) {
		Object oldValue = super.setAttribute(name, value);
		if ((this.getPage() != null) && ((oldValue == null) ? (value != null) : !oldValue.equals(value)))
			this.doc.notifyAttributeChanged(this, name, oldValue);
		return oldValue;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#getAttributeNames()
	 */
	public String[] getAttributeNames() {
		if (ImDocument.TRACK_INSTANCES && (this.doc != null)) this.doc.accessed();
		return super.getAttributeNames();
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#clearAttributes()
	 */
	public void clearAttributes() {
		if (this.getPage() == null) {
			super.clearAttributes();
			return;
		}
		String[] oldNames = this.getAttributeNames();
		for (int n = 0; n < oldNames.length; n++)
			this.removeAttribute(oldNames[n]);
	}
//	
//	/* (non-Javadoc)
//	 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#copyAttributes(de.uka.ipd.idaho.gamta.Attributed)
//	 */
//	public void copyAttributes(Attributed source) {
//		if (source == null)
//			return;
//		if (this.getPage() == null) {
//			super.copyAttributes(source);
//			return;
//		}
//		String[] names = source.getAttributeNames();
//		for (int n = 0; n < names.length; n++)
//			this.setAttribute(names[n], source.getAttribute(names[n]));
//	}
//	
//	/* (non-Javadoc)
//	 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#removeAttribute(java.lang.String)
//	 */
//	public Object removeAttribute(String name) {
//		Object oldValue = super.removeAttribute(name);
//		if ((this.getPage() != null) && (oldValue != null))
//			this.doc.notifyAttributeChanged(this, name, oldValue);
//		return oldValue;
//	}
	
	/**
	 * Produce the aggregate bounding box of multiple layout objects, i.e., the
	 * hull of their individual bounding boxes. Results of this method are only
	 * meaningful if the objects belong to the same page.
	 * @param ilos the layout objects to compute the bounds for
	 * @return the aggregate bounding box
	 */
	public static BoundingBox getAggregateBox(ImLayoutObject[] ilos) {
		return getAggregateBox(ilos, 0, ilos.length);
	}
	
	/**
	 * Produce the aggregate bounding box of multiple layout objects, i.e., the
	 * hull of their individual bounding boxes. Results of this method are only
	 * meaningful if the objects belong to the same page.
	 * @param ilos the layout objects to compute the bounds for
	 * @param start the index of the first layout object to consider
	 * @param end the index of the layout object to stop at
	 * @return the aggregate bounding box
	 */
	public static BoundingBox getAggregateBox(ImLayoutObject[] ilos, int start, int end) {
		if (end <= start)
			return null;
		if ((start + 1) == end)
			return ilos[start].bounds;
		int left = Integer.MAX_VALUE;
		int right = 0;
		int top = Integer.MAX_VALUE;
		int bottom = 0;
		for (int b = start; b < end; b++) {
			left = Math.min(left, ilos[b].bounds.left);
			right = Math.max(right, ilos[b].bounds.right);
			top = Math.min(top, ilos[b].bounds.top);
			bottom = Math.max(bottom, ilos[b].bounds.bottom);
		}
		return (((left < right) && (top < bottom)) ? new BoundingBox(left, right, top, bottom) : null);
	}
}