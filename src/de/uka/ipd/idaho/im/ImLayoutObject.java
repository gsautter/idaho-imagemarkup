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
import de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed;
import de.uka.ipd.idaho.gamta.util.imaging.BoundingBox;
import de.uka.ipd.idaho.gamta.util.imaging.PageImage;
import de.uka.ipd.idaho.gamta.util.imaging.PageImageInputStream;

/**
 * A layout related object in an image markup document. If a layout object is
 * removed from the page it lies on, it becomes detached, i.e., the page is set
 * to <code>null</code>. Detached layout objects can be re-associated with the
 * page they belong to, however.
 * 
 * @author sautter
 */
public abstract class ImLayoutObject extends AbstractAttributed implements ImObject {
	
	private ImDocument doc;
	
	private String type = "object";
	
	/** the bounding box enclosing the object on the underlying page image */
	public final BoundingBox bounds;
	
	/** the ID of the page the object lies on */
	public final int pageId;
	
	/** Constructor
	 * @param doc the document the layout object belongs to
	 * @param pageId the ID of the page the object lies on
	 */
	public ImLayoutObject(ImDocument doc, int pageId, BoundingBox bounds) {
		this.doc = doc;
		this.pageId = pageId;
		this.bounds = bounds;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImObject#getType()
	 */
	public String getType() {
		return this.type;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImObject#setType(java.lang.String)
	 */
	public void setType(String type) {
		String oldType = this.type;
		this.type = type;
		if (this.getPage() != null)
			this.doc.notifyTypeChanged(this, oldType);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImObject#getDocument()
	 */
	public ImDocument getDocument() {
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
	public PageImageInputStream getImageAsStream() {
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
		if (DOCUMENT_ID_ATTRIBUTE.equals(name))
			return this.getDocument().docId;
		if (PAGE_ID_ATTRIBUTE.equals(name))
			return this.pageId;
		if (BOUNDING_BOX_ATTRIBUTE.equals(name))
			return this.bounds.toString();
		return super.getAttribute(name, def);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#getAttribute(java.lang.String)
	 */
	public Object getAttribute(String name) {
		if (DOCUMENT_ID_ATTRIBUTE.equals(name))
			return this.getDocument().docId;
		if (PAGE_ID_ATTRIBUTE.equals(name))
			return this.pageId;
		if (BOUNDING_BOX_ATTRIBUTE.equals(name))
			return this.bounds.toString();
		return super.getAttribute(name);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#hasAttribute(java.lang.String)
	 */
	public boolean hasAttribute(String name) {
		if (DOCUMENT_ID_ATTRIBUTE.equals(name))
			return true;
		if (PAGE_ID_ATTRIBUTE.equals(name))
			return true;
		if (BOUNDING_BOX_ATTRIBUTE.equals(name))
			return true;
		return super.hasAttribute(name);
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
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#copyAttributes(de.uka.ipd.idaho.gamta.Attributed)
	 */
	public void copyAttributes(Attributed source) {
		if (source == null)
			return;
		if (this.getPage() == null) {
			super.copyAttributes(source);
			return;
		}
		String[] names = source.getAttributeNames();
		for (int n = 0; n < names.length; n++)
			this.setAttribute(names[n], source.getAttribute(names[n]));
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#removeAttribute(java.lang.String)
	 */
	public Object removeAttribute(String name) {
		Object oldValue = super.removeAttribute(name);
		if ((this.getPage() != null) && (oldValue != null))
			this.doc.notifyAttributeChanged(this, name, oldValue);
		return oldValue;
	}
	
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