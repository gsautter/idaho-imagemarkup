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

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.TreeSet;

import javax.imageio.ImageIO;

import de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed;
import de.uka.ipd.idaho.gamta.util.imaging.BoundingBox;

/**
 * A binary supplement to an Image Markup document, e.g. an embedded figure or
 * an unmodified scan, or a binary file the document was originally generated
 * from.
 * 
 * @author sautter
 */
/**
 * @author sautter
 *
 */
public abstract class ImSupplement extends AbstractAttributed implements ImObject {
	
	/** type of a supplement representing the original binary source of a document */
	public static final String SOURCE_TYPE = "source";
	
	/** type of a supplement representing the original scan of a page in a document */
	public static final String SCAN_TYPE = "scan";
	
	/** type of a supplement representing a figure extracted from a page in a document */
	public static final String FIGURE_TYPE = "figure";
	
	/** the name of the attribute holding the ID of the supplement, namely 'id' */
	public static final String ID_ATTRIBUTE = "id";
	
	/** the name of the attribute holding the MIME type of the supplement, namely 'mimeType' */
	public static final String MIME_TYPE_ATTRIBUTE = "mimeType";
	
	private ImDocument doc;
	
	private String type;
	
	private String mimeType;
	
	/** Constructor
	 * @param doc the document the supplement belongs to
	 * @param type the type of supplement
	 * @param mimeType the MIME type of the binary data
	 */
	protected ImSupplement(ImDocument doc, String type, String mimeType) {
		this.doc = doc;
		this.type = type;
		this.mimeType = mimeType;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#getAttribute(java.lang.String)
	 */
	public Object getAttribute(String name) {
		if (TYPE_ATTRIBUTE.equals(name))
			return this.type;
		else if (MIME_TYPE_ATTRIBUTE.equals(name))
			return this.mimeType;
		else if (ID_ATTRIBUTE.equals(name))
			return this.getId();
		else return super.getAttribute(name);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#getAttribute(java.lang.String, java.lang.Object)
	 */
	public Object getAttribute(String name, Object def) {
		if (TYPE_ATTRIBUTE.equals(name))
			return this.type;
		else if (MIME_TYPE_ATTRIBUTE.equals(name))
			return this.mimeType;
		else if (ID_ATTRIBUTE.equals(name))
			return this.getId();
		else return super.getAttribute(name, def);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#getAttributeNames()
	 */
	public String[] getAttributeNames() {
		TreeSet ans = new TreeSet(Arrays.asList(super.getAttributeNames()));
		ans.add(TYPE_ATTRIBUTE);
		ans.add(MIME_TYPE_ATTRIBUTE);
		return ((String[]) ans.toArray(new String[ans.size()]));
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#hasAttribute(java.lang.String)
	 */
	public boolean hasAttribute(String name) {
		if (TYPE_ATTRIBUTE.equals(name))
			return true;
		else if (MIME_TYPE_ATTRIBUTE.equals(name))
			return true;
		else if (ID_ATTRIBUTE.equals(name))
			return true;
		else return super.hasAttribute(name);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#setAttribute(java.lang.String, java.lang.Object)
	 */
	public Object setAttribute(String name, Object value) {
		if (TYPE_ATTRIBUTE.equals(name))
			return this.type;
		else if (MIME_TYPE_ATTRIBUTE.equals(name))
			return this.mimeType;
		else if (ID_ATTRIBUTE.equals(name))
			return this.getId();
		else return super.setAttribute(name, value);
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
	public void setType(String type) { /* we're not taking type modifications */ }
	
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
		return ((this.doc == null) ? null : this.doc.getDocumentProperty(propertyName));
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImObject#getDocumentProperty(java.lang.String, java.lang.String)
	 */
	public String getDocumentProperty(String propertyName, String defaultValue) {
		return ((this.doc == null) ? defaultValue : this.doc.getDocumentProperty(propertyName, defaultValue));
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImObject#getDocumentPropertyNames()
	 */
	public String[] getDocumentPropertyNames() {
		return ((this.doc == null) ? new String[0] : this.doc.getDocumentPropertyNames());
	}
	
	/**
	 * Obtain the identifier of the supplement, unique within the scope of the
	 * Image Markup document the supplement belongs to.
	 * @return the identifier of the supplement
	 */
	public abstract String getId();
	
	/**
	 * Retrieve the MIME type of the binary data making up the supplement.
	 * @return the MIME type of the supplement data
	 */
	public String getMimeType() {
		return this.mimeType;
	}
	
	/**
	 * Obtain an input stream providing the binary data the supplement consists
	 * of. This method will mostly be implemented ad hoc in anonymous classes,
	 * as the origin of the input stream depends how objects are created in a
	 * specific scenario rather than the type of the supplement.
	 * @return an input stream providing the binary data
	 */
	public abstract InputStream getInputStream() throws IOException;
	
	/**
	 * A supplement representing the original binary source of an Image Markup
	 * document. The identifier of objects of this class is simply 'source'.
	 * 
	 * @author sautter
	 */
	public static abstract class Source extends ImSupplement {
		
		/** Constructor
		 * @param doc the document this supplement is the source of
		 * @param mimeType the MIME type of the source data
		 */
		public Source(ImDocument doc, String mimeType) {
			super(doc, SOURCE_TYPE, mimeType);
			if (doc != null)
				doc.addSupplement(this);
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.im.ImSupplement#getId()
		 */
		public String getId() {
			return this.getType();
		}
		
		/**
		 * Create a Source around binary data held in an array of bytes. The
		 * argument byte array is copied, so it can be modified later without
		 * breaking the created Source supplement. Further, the created Source
		 * is automatically added to the argument document.
		 * @param doc the document the source belongs to
		 * @param mimeType the MIME type of the source
		 * @param sourceData the source data
		 * @return the newly created source
		 */
		public static Source createSource(ImDocument doc, String mimeType, byte[] sourceData) {
			final byte[] sourceBytes = new byte[sourceData.length];
			System.arraycopy(sourceData, 0, sourceBytes, 0, sourceData.length);
			return new Source(doc, mimeType) {
				public InputStream getInputStream() throws IOException {
					return new ByteArrayInputStream(sourceBytes);
				}
			};
		}
	}
	
	/**
	 * A supplement representing an image somehow associated with an Image
	 * Markup document. The MIME type of objects of this class can be any image
	 * format, but the recommended format is 'image/png'. This class exists as
	 * to bundle properties of <code>Scan</code> and <code>Figure</code>.
	 * 
	 * @author sautter
	 */
	static abstract class Image extends ImSupplement {
		
		/** the name of the attribute holding the resolution of an image, namely 'dpi' */
		public static final String DPI_ATTRIBUTE = "dpi";
		
		int pageId = -1;
		int dpi = -1;
		
		Image(ImDocument doc, String type, String mimeType) {
			super(doc, type, mimeType);
		}
		
		Image(ImDocument doc, String type, String mimeType, int pageId, int dpi) {
			super(doc, type, mimeType);
			this.pageId = pageId;
			this.dpi = dpi;
		}
		
		/**
		 * @return the ID of the page the scan belongs to
		 */
		public int getPageId() {
			return this.pageId;
		}
		
		/**
		 * @return the resolution of the scan
		 */
		public int getDpi() {
			return this.dpi;
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#getAttribute(java.lang.String)
		 */
		public Object getAttribute(String name) {
			if (PAGE_ID_ATTRIBUTE.equals(name) && (this.pageId != -1))
				return ("" + this.pageId);
			else if (DPI_ATTRIBUTE.equals(name) && (this.dpi != -1))
				return ("" + this.dpi);
			else return super.getAttribute(name);
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#getAttribute(java.lang.String, java.lang.Object)
		 */
		public Object getAttribute(String name, Object def) {
			if (PAGE_ID_ATTRIBUTE.equals(name) && (this.pageId != -1))
				return ("" + this.pageId);
			else if (DPI_ATTRIBUTE.equals(name) && (this.dpi != -1))
				return ("" + this.dpi);
			else return super.getAttribute(name, def);
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#getAttributeNames()
		 */
		public String[] getAttributeNames() {
			TreeSet ans = new TreeSet(Arrays.asList(super.getAttributeNames()));
			if (this.pageId != -1)
				ans.add(PAGE_ID_ATTRIBUTE);
			if (this.dpi != -1)
				ans.add(DPI_ATTRIBUTE);
			return ((String[]) ans.toArray(new String[ans.size()]));
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#hasAttribute(java.lang.String)
		 */
		public boolean hasAttribute(String name) {
			if (PAGE_ID_ATTRIBUTE.equals(name))
				return (this.pageId != -1);
			else if (DPI_ATTRIBUTE.equals(name))
				return (this.dpi != -1);
			else return super.hasAttribute(name);
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#setAttribute(java.lang.String, java.lang.Object)
		 */
		public Object setAttribute(String name, Object value) {
			if (PAGE_ID_ATTRIBUTE.equals(name)) {
				int oldPageId = this.pageId;
				if (value == null)
					this.pageId = -1;
				else this.pageId = Integer.parseInt(value.toString());
				return ("" + oldPageId);
			}
			else if (DPI_ATTRIBUTE.equals(name)) {
				int oldDpi = this.dpi;
				if (value == null)
					this.dpi = -1;
				else this.dpi = Integer.parseInt(value.toString());
				return ("" + oldDpi);
			}
			else return super.setAttribute(name, value);
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#removeAttribute(java.lang.String)
		 */
		public Object removeAttribute(String name) {
			if (PAGE_ID_ATTRIBUTE.equals(name)) {
				int oldPageId = this.pageId;
				this.pageId = -1;
				return ("" + oldPageId);
			}
			else if (DPI_ATTRIBUTE.equals(name)) {
				int oldDpi = this.dpi;
				this.dpi = -1;
				return ("" + oldDpi);
			}
			else return super.removeAttribute(name);
		}
	}
	
	/**
	 * A supplement representing the original scan of a page in an Image Markup
	 * document. The MIME type of objects of this class can be any image format,
	 * but the recommended format is 'image/png'. The identifier of objects of
	 * this class is 'scan@&lt;pageId&gt;'.
	 * 
	 * @author sautter
	 */
	public static abstract class Scan extends Image {
		
		/** Constructor
		 * @param doc the document the scan belongs to
		 * @param mimeType the MIME type of the binary representation of the scan
		 */
		public Scan(ImDocument doc, String mimeType) {
			super(doc, SCAN_TYPE, mimeType);
		}
		
		/** Constructor
		 * @param doc the document the scan belongs to
		 * @param mimeType the MIME type of the binary representation of the scan
		 * @param pageId the ID of the page the scan belongs to
		 * @param dpi the resolution of the scan
		 */
		public Scan(ImDocument doc, String mimeType, int pageId, int dpi) {
			super(doc, SCAN_TYPE, mimeType, pageId, dpi);
			if (doc != null)
				doc.addSupplement(this);
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.im.ImSupplement#getId()
		 */
		public String getId() {
			return (SCAN_TYPE + "@" + this.pageId);
		}
		
		/**
		 * Create a Scan around a <code>BufferedImage</code>. The argument
		 * image is serialized into a byte stream in PNG format, so it can be
		 * modified later without breaking the created Scan supplement. Further,
		 * the created Scan is automatically added to the argument document.
		 * @param doc the document the source belongs to
		 * @param pageId the ID of the page the scan belongs to
		 * @param dpi the resolution of the scan
		 * @param scan the object representation of the scan
		 * @return the newly created scan
		 */
		public static Scan createScan(ImDocument doc, int pageId, int dpi, BufferedImage scan) {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			try {
				ImageIO.write(scan, "PNG", baos);
			} catch (IOException ioe) { /* never gonna happen, but Java don't know */ }
			final byte[] scanBytes = baos.toByteArray();
			return new Scan(doc, "image/png", pageId, dpi) {
				public InputStream getInputStream() throws IOException {
					return new ByteArrayInputStream(scanBytes);
				}
			};
		}
	}
	
	/**
	 * A supplement representing a figure in a page of an Image Markup document.
	 * The MIME type of objects of this class can be any image format, but the
	 * recommended format is 'image/png'. The bounding box of a figure has to
	 * be in page image resolution, independent of the resolution of the actual
	 * image. This is to facilitate relating an original figure to an ImRegion
	 * marking its counterpart in a document page. The identifier of objects of
	 * this class is 'figure@&lt;pageId&gt;.&lt;boundingBox&gt;'.
	 * 
	 * @author sautter
	 */
	public static abstract class Figure extends Image {
		private BoundingBox bounds = null;
		
		/** Constructor
		 * @param doc the document the scan belongs to
		 * @param mimeType the MIME type of the binary representation of the scan
		 */
		public Figure(ImDocument doc, String mimeType) {
			super(doc, FIGURE_TYPE, mimeType);
		}
		
		/** Constructor
		 * @param doc the document the scan belongs to
		 * @param mimeType the MIME type of the binary representation of the scan
		 * @param pageId the ID of the page the scan belongs to
		 * @param dpi the resolution of the scan
		 * @param bounds the bounding box of the figure (in page image resolution)
		 */
		public Figure(ImDocument doc, String mimeType, int pageId, int dpi, BoundingBox bounds) {
			super(doc, FIGURE_TYPE, mimeType);
			this.pageId = pageId;
			this.dpi = dpi;
			this.bounds = bounds;
			if (doc != null)
				doc.addSupplement(this);
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.im.ImSupplement#getId()
		 */
		public String getId() {
			return (FIGURE_TYPE + "@" + this.pageId + "." + this.bounds.toString());
		}
		
		/**
		 * @return the bounding box of the figure (in page image resolution)
		 */
		public BoundingBox getBounds() {
			return this.bounds;
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#getAttribute(java.lang.String)
		 */
		public Object getAttribute(String name) {
			if (BOUNDING_BOX_ATTRIBUTE.equals(name) && (this.bounds != null))
				return this.bounds;
			else return super.getAttribute(name);
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#getAttribute(java.lang.String, java.lang.Object)
		 */
		public Object getAttribute(String name, Object def) {
			if (BOUNDING_BOX_ATTRIBUTE.equals(name) && (this.bounds != null))
				return this.bounds;
			else return super.getAttribute(name, def);
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#getAttributeNames()
		 */
		public String[] getAttributeNames() {
			TreeSet ans = new TreeSet(Arrays.asList(super.getAttributeNames()));
			if (this.bounds != null)
				ans.add(BOUNDING_BOX_ATTRIBUTE);
			return ((String[]) ans.toArray(new String[ans.size()]));
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#hasAttribute(java.lang.String)
		 */
		public boolean hasAttribute(String name) {
			if (BOUNDING_BOX_ATTRIBUTE.equals(name))
				return (this.bounds != null);
			else return super.hasAttribute(name);
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#setAttribute(java.lang.String, java.lang.Object)
		 */
		public Object setAttribute(String name, Object value) {
			if (BOUNDING_BOX_ATTRIBUTE.equals(name)) {
				BoundingBox oldBounds = this.bounds;
				if (value == null)
					this.bounds = null;
				else if (value instanceof BoundingBox)
					this.bounds = ((BoundingBox) value);
				else this.bounds = BoundingBox.parse(value.toString());
				return oldBounds;
			}
			else return super.setAttribute(name, value);
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#removeAttribute(java.lang.String)
		 */
		public Object removeAttribute(String name) {
			if (BOUNDING_BOX_ATTRIBUTE.equals(name)) {
				BoundingBox oldBounds = this.bounds;
				this.bounds = null;
				return oldBounds;
			}
			else return super.removeAttribute(name);
		}
		
		/**
		 * Create a Figure around a <code>BufferedImage</code>. The argument
		 * image is serialized into a byte stream in PNG format, so it can be
		 * modified later without breaking the created Figure supplement. The
		 * bounding box of the figure has to be in page image resolution, not
		 * in figure resolution, to facilitate relating an original figure to
		 * an ImRegion marking its counterpart in a document page. Further, the
		 * created Figure is automatically added to the argument document.
		 * @param doc the document the source belongs to
		 * @param pageId the ID of the page the scan belongs to
		 * @param dpi the resolution of the scan
		 * @param figure the object representation of the figure
		 * @param bounds the bounding box of the figure on the document page
		 * @return the newly created scan
		 */
		public static Figure createFigure(ImDocument doc, int pageId, int dpi, BufferedImage figure, BoundingBox bounds) {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			try {
				ImageIO.write(figure, "PNG", baos);
			} catch (IOException ioe) { /* never gonna happen, but Java don't know */ }
			final byte[] figureBytes = baos.toByteArray();
			return new Figure(doc, "image/png", pageId, dpi, bounds) {
				public InputStream getInputStream() throws IOException {
					return new ByteArrayInputStream(figureBytes);
				}
			};
		}
	}
}