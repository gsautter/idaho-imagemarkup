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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.Vector;

import javax.imageio.ImageIO;

import de.uka.ipd.idaho.easyIO.util.JsonParser;
import de.uka.ipd.idaho.gamta.AnnotationUtils;
import de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed;
import de.uka.ipd.idaho.gamta.util.imaging.BoundingBox;
import de.uka.ipd.idaho.im.ImSupplement.Graphics.Path;
import de.uka.ipd.idaho.im.ImSupplement.Graphics.SubPath;
import de.uka.ipd.idaho.im.analysis.Imaging;
import de.uka.ipd.idaho.im.analysis.Imaging.AnalysisImage;

/**
 * A binary supplement to an Image Markup document, e.g. an embedded figure or
 * an unmodified scan, or a binary file the document was originally generated
 * from.
 * 
 * @author sautter
 */
public abstract class ImSupplement extends AbstractAttributed implements ImObject {
	
	/** type of a supplement representing the original binary source of a document */
	public static final String SOURCE_TYPE = "source";
	
	/** type of a supplement representing the original scan of a page in a document */
	public static final String SCAN_TYPE = "scan";
	
	/** type of a supplement representing a bitmap figure extracted from a page in a document */
	public static final String FIGURE_TYPE = "figure";
	
	/** type of a supplement representing a vector based graphics object extracted from a page in a document */
	public static final String GRAPHICS_TYPE = "graphics";
	
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
		else {
			Object oldValue = super.setAttribute(name, value);
			if ((this.doc != null) && ((oldValue == null) ? (value != null) : !oldValue.equals(value)))
				this.doc.notifyAttributeChanged(this, name, oldValue);
			return oldValue;
		}
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
	 * Dispose of the supplement. This method is called after a supplement has
	 * been replaced in or removed from a document. Its intent is, for
	 * instance, to free up any underlying resources allocated to storing the
	 * binary supplement data. This default implementation does nothing, sub
	 * classes are welcome to overwrite it as needed.
	 */
	public void dispose() {}
	
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
	 * as the origin of the input stream depends on how objects are created in
	 * a specific scenario rather than the type of the supplement.
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
	 * format, but the recommended format is 'image/png'. This class exists to
	 * bundle properties of <code>Scan</code>, <code>Figure</code>, and
	 * <code>Graphics</code>.
	 * 
	 * @author sautter
	 */
	static abstract class Image extends ImSupplement {
		
		/** the name of the attribute holding the resolution of an image, namely 'dpi' */
		public static final String DPI_ATTRIBUTE = "dpi";
		
		/** the name of the attribute holding the page internal rendering order position of an image, namely 'ron' */
		public static final String RENDER_ORDER_NUMBER_ATTRIBUTE = "ron";
		
		/** the name of the attribute holding the bounding box of the visible portion of the image on the document page (in page image resolution), namely 'clipBox' */
		public static final String CLIP_BOX_ATTRIBUTE = "clipBox";
		
		int pageId = -1;
		int renderOrderNumber = -1;
		int dpi = -1;
		
		Image(ImDocument doc, String type, String mimeType) {
			super(doc, type, mimeType);
		}
		
		Image(ImDocument doc, String type, String mimeType, int pageId, int renderOrderNumber, int dpi) {
			super(doc, type, mimeType);
			this.pageId = pageId;
			this.renderOrderNumber = renderOrderNumber;
			this.dpi = dpi;
		}
		
		/**
		 * @return the ID of the page the image belongs to
		 */
		public int getPageId() {
			return this.pageId;
		}
		
		/**
		 * @return the page internal rendering order position of the image
		 */
		public int getRenderOrderNumber() {
			return this.renderOrderNumber;
		}
		
		/**
		 * @return the resolution of the graphics
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
			else if (RENDER_ORDER_NUMBER_ATTRIBUTE.equals(name) && (this.renderOrderNumber != -1))
				return ("" + this.renderOrderNumber);
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
			else if (RENDER_ORDER_NUMBER_ATTRIBUTE.equals(name) && (this.renderOrderNumber != -1))
				return ("" + this.renderOrderNumber);
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
			if (this.renderOrderNumber != -1)
				ans.add(RENDER_ORDER_NUMBER_ATTRIBUTE);
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
			else if (RENDER_ORDER_NUMBER_ATTRIBUTE.equals(name))
				return (this.renderOrderNumber != -1);
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
			else if (RENDER_ORDER_NUMBER_ATTRIBUTE.equals(name)) {
				int oldRon = this.renderOrderNumber;
				if (value == null)
					this.renderOrderNumber = -1;
				else this.renderOrderNumber = Integer.parseInt(value.toString());
				return ("" + oldRon);
			}
			else return super.setAttribute(name, value);
		}
//		
//		/* (non-Javadoc)
//		 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#removeAttribute(java.lang.String)
//		 */
//		public Object removeAttribute(String name) {
//			if (PAGE_ID_ATTRIBUTE.equals(name)) {
//				int oldPageId = this.pageId;
//				this.pageId = -1;
//				return ("" + oldPageId);
//			}
//			else if (DPI_ATTRIBUTE.equals(name)) {
//				int oldDpi = this.dpi;
//				this.dpi = -1;
//				return ("" + oldDpi);
//			}
//			else return super.removeAttribute(name);
//		}
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
		 * @param renderOrderNumber the page internal position at which the scan is rendered
		 * @param dpi the resolution of the scan
		 */
		public Scan(ImDocument doc, String mimeType, int pageId, int renderOrderNumber, int dpi) {
			super(doc, SCAN_TYPE, mimeType, pageId, renderOrderNumber, dpi);
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
		 * @param renderOrderNumber the page internal position at which the scan is rendered
		 * @param dpi the resolution of the scan
		 * @param scan the object representation of the scan
		 * @return the newly created scan
		 */
		public static Scan createScan(ImDocument doc, int pageId, int renderOrderNumber, int dpi, BufferedImage scan) {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			try {
				ImageIO.write(scan, "PNG", baos);
			} catch (IOException ioe) { /* never gonna happen, but Java don't know */ }
			final byte[] scanBytes = baos.toByteArray();
			return new Scan(doc, "image/png", pageId, renderOrderNumber, dpi) {
				public InputStream getInputStream() throws IOException {
					return new ByteArrayInputStream(scanBytes);
				}
			};
		}
	}
	
	/**
	 * A supplement representing a bitmap figure in a page of an Image Markup
	 * document. The MIME type of objects of this class can be any image
	 * format, but the recommended format is 'image/png'. The bounding box of a
	 * figure has to be in page image resolution, independent of the resolution
	 * of the actual image. This is to facilitate relating an original figure
	 * to an ImRegion marking its counterpart in a document page. The identifier
	 * of objects of this class is 'figure@&lt;pageId&gt;.&lt;boundingBox&gt;'.
	 * 
	 * @author sautter
	 */
	public static abstract class Figure extends Image {
		private BoundingBox bounds = null;
		private BoundingBox clipBounds = null;
		
		/** Constructor
		 * @param doc the document the figure belongs to
		 * @param mimeType the MIME type of the binary representation of the figure
		 */
		public Figure(ImDocument doc, String mimeType) {
			super(doc, FIGURE_TYPE, mimeType);
		}
		
		/** Constructor
		 * @param doc the document the figure belongs to
		 * @param mimeType the MIME type of the binary representation of the figure
		 * @param pageId the ID of the page the figure lies on
		 * @param renderOrderNumber the page internal position at which the figure is rendered
		 * @param dpi the resolution of the figure
		 * @param bounds the bounding box of the figure (in page image resolution)
		 * @param clipBounds the bounding box of the visible portion of the figure on the document page
		 */
		public Figure(ImDocument doc, String mimeType, int pageId, int renderOrderNumber, int dpi, BoundingBox bounds) {
			this(doc, mimeType, pageId, renderOrderNumber, dpi, bounds, null);
		}
		
		/** Constructor
		 * @param doc the document the figure belongs to
		 * @param mimeType the MIME type of the binary representation of the figure
		 * @param pageId the ID of the page the figure lies on
		 * @param renderOrderNumber the page internal position at which the figure is rendered
		 * @param dpi the resolution of the figure
		 * @param bounds the bounding box of the figure (in page image resolution)
		 * @param clipBounds the bounding box of the visible portion of the figure on the document page
		 */
		public Figure(ImDocument doc, String mimeType, int pageId, int renderOrderNumber, int dpi, BoundingBox bounds, BoundingBox clipBounds) {
			super(doc, FIGURE_TYPE, mimeType, pageId, renderOrderNumber, dpi);
			this.bounds = bounds;
			this.clipBounds = ((clipBounds == null) ? this.bounds : clipBounds);
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
		
		/**
		 * @return the bounding box of the visible portion of the figure on the document page (in page image resolution)
		 */
		public BoundingBox getClipBounds() {
			return ((this.clipBounds == null) ? this.bounds : this.clipBounds);
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#getAttribute(java.lang.String)
		 */
		public Object getAttribute(String name) {
			if (BOUNDING_BOX_ATTRIBUTE.equals(name) && (this.bounds != null))
				return this.bounds;
			else if (CLIP_BOX_ATTRIBUTE.equals(name) && (this.clipBounds != null))
				return this.clipBounds;
			else return super.getAttribute(name);
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#getAttribute(java.lang.String, java.lang.Object)
		 */
		public Object getAttribute(String name, Object def) {
			if (BOUNDING_BOX_ATTRIBUTE.equals(name) && (this.bounds != null))
				return this.bounds;
			else if (CLIP_BOX_ATTRIBUTE.equals(name) && (this.clipBounds != null))
				return this.clipBounds;
			else return super.getAttribute(name, def);
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#getAttributeNames()
		 */
		public String[] getAttributeNames() {
			TreeSet ans = new TreeSet(Arrays.asList(super.getAttributeNames()));
			if (this.bounds != null)
				ans.add(BOUNDING_BOX_ATTRIBUTE);
			if ((this.clipBounds != null) && !this.clipBounds.equals(this.bounds))
				ans.add(CLIP_BOX_ATTRIBUTE);
			return ((String[]) ans.toArray(new String[ans.size()]));
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#hasAttribute(java.lang.String)
		 */
		public boolean hasAttribute(String name) {
			if (BOUNDING_BOX_ATTRIBUTE.equals(name))
				return (this.bounds != null);
			else if (BOUNDING_BOX_ATTRIBUTE.equals(name))
				return ((this.clipBounds != null) && !this.clipBounds.equals(this.bounds));
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
			else if (CLIP_BOX_ATTRIBUTE.equals(name)) {
				BoundingBox oldClipBounds = this.clipBounds;
				if (value == null)
					this.clipBounds = null;
				else if (value instanceof BoundingBox)
					this.clipBounds = ((BoundingBox) value);
				else this.clipBounds = BoundingBox.parse(value.toString());
				return oldClipBounds;
			}
			else return super.setAttribute(name, value);
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
		 * @param renderOrderNumber the page internal position at which the figure is rendered
		 * @param dpi the resolution of the scan
		 * @param figure the object representation of the figure
		 * @param bounds the bounding box of the figure on the document page
		 * @return the newly created figure
		 */
		public static Figure createFigure(ImDocument doc, int pageId, int renderOrderNumber, int dpi, BufferedImage figure, BoundingBox bounds) {
			return createFigure(doc, pageId, renderOrderNumber, dpi, figure, bounds, bounds);
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
		 * @param renderOrderNumber the page internal position at which the figure is rendered
		 * @param dpi the resolution of the scan
		 * @param figure the object representation of the figure
		 * @param bounds the bounding box of the figure on the document page
		 * @param clipBounds the bounding box of the visible portion of the figure on the document page
		 * @return the newly created figure
		 */
		public static Figure createFigure(ImDocument doc, int pageId, int renderOrderNumber, int dpi, BufferedImage figure, BoundingBox bounds, BoundingBox clipBounds) {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			try {
				ImageIO.write(figure, "PNG", baos);
			} catch (IOException ioe) { /* never gonna happen, but Java don't know */ }
			final byte[] figureBytes = baos.toByteArray();
			return new Figure(doc, "image/png", pageId, renderOrderNumber, dpi, bounds, clipBounds) {
				public InputStream getInputStream() throws IOException {
					return new ByteArrayInputStream(figureBytes);
				}
			};
		}
	}
	
	/**
	 * A supplement representing a vector based graphics object in a page of an
	 * Image Markup document. The MIME type of objects of this class is fixed
	 * to 'application/json'. The bounding box of a graphics object has to be
	 * in page image resolution, independent of the resolution of the vector
	 * coordinate resolution, which is fixed to 72 DPI. This is to facilitate
	 * relating an original graphics to an ImRegion marking its counterpart in
	 * a document page. The identifier of objects of this class is
	 * 'graphics@&lt;pageId&gt;.&lt;boundingBox&gt;'.
	 * 
	 * @author sautter
	 */
	public static abstract class Graphics extends Image {
		
		/** the attribute for marking a graphics object as part of page layout artwork, namely 'pageDecoration' */
		public static final String PAGE_DECORATION_ATTRIBUTE = "pageDecoration";
		
		/** the MIME type vector based graphics objects are stored in, namely 'application/json' */
		public static final String MIME_TYPE = "application/json";
		
		/** the resolution of the unscaled vector based graphics, namely 72 DPI */
		public static final int RESOLUTION = 72;
		
		private BoundingBox bounds = null;
		private BoundingBox clipBounds = null;
		private boolean modifiable = false;
		ArrayList paths = null; // needs to be accessible from anonymous sub class used in createGraphics() ...
		
		/** Constructor
		 * @param doc the document the graphics belongs to
		 */
		public Graphics(ImDocument doc) {
			super(doc, GRAPHICS_TYPE, MIME_TYPE);
		}
		
		/** Constructor
		 * @param doc the document the graphics belongs to
		 * @param pageId the ID of the page the graphics lies on
		 * @param renderOrderNumber the page internal position at which the graphics is rendered
		 * @param bounds the bounding box of the graphics (in page image resolution)
		 */
		public Graphics(ImDocument doc, int pageId, int renderOrderNumber, BoundingBox bounds) {
			this(doc, pageId, renderOrderNumber, bounds, null);
		}
		
		/** Constructor
		 * @param doc the document the graphics belongs to
		 * @param pageId the ID of the page the graphics lies on
		 * @param renderOrderNumber the page internal position at which the graphics is rendered
		 * @param bounds the bounding box of the graphics (in page image resolution)
		 * @param clipBounds the bounding box of the visible portion of the graphics on the document page
		 */
		public Graphics(ImDocument doc, int pageId, int renderOrderNumber, BoundingBox bounds, BoundingBox clipBounds) {
			this(doc, pageId, renderOrderNumber, bounds, clipBounds, false);
		}
		
		private Graphics(ImDocument doc, int pageId, int renderOrderNumber, BoundingBox bounds, BoundingBox clipBounds, boolean modifiable) {
			super(doc, GRAPHICS_TYPE, MIME_TYPE, pageId, renderOrderNumber, RESOLUTION);
			this.bounds = bounds;
			this.clipBounds = ((clipBounds == null) ? this.bounds : clipBounds);
			if (modifiable) {
				this.modifiable = true;
				this.paths = new ArrayList();
			}
			if (doc != null)
				doc.addSupplement(this);
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.im.ImSupplement#getId()
		 */
		public String getId() {
			return (GRAPHICS_TYPE + "@" + this.pageId + "." + this.bounds.toString());
		}
		
		/**
		 * @return the bounding box of the graphics (in page image resolution)
		 */
		public BoundingBox getBounds() {
			return this.bounds;
		}
		
		/**
		 * @return the bounding box of the visible portion of the graphics on the document page (in page image resolution)
		 */
		public BoundingBox getClipBounds() {
			return ((this.clipBounds == null) ? this.bounds : this.clipBounds);
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#getAttribute(java.lang.String)
		 */
		public Object getAttribute(String name) {
			if (BOUNDING_BOX_ATTRIBUTE.equals(name) && (this.bounds != null))
				return this.bounds;
			else if (CLIP_BOX_ATTRIBUTE.equals(name) && (this.clipBounds != null))
				return this.clipBounds;
			else return super.getAttribute(name);
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#getAttribute(java.lang.String, java.lang.Object)
		 */
		public Object getAttribute(String name, Object def) {
			if (BOUNDING_BOX_ATTRIBUTE.equals(name) && (this.bounds != null))
				return this.bounds;
			else if (CLIP_BOX_ATTRIBUTE.equals(name) && (this.clipBounds != null))
				return this.clipBounds;
			else return super.getAttribute(name, def);
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#getAttributeNames()
		 */
		public String[] getAttributeNames() {
			TreeSet ans = new TreeSet(Arrays.asList(super.getAttributeNames()));
			if (this.bounds != null)
				ans.add(BOUNDING_BOX_ATTRIBUTE);
			if ((this.clipBounds != null) && !this.clipBounds.equals(this.bounds))
				ans.add(CLIP_BOX_ATTRIBUTE);
			return ((String[]) ans.toArray(new String[ans.size()]));
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#hasAttribute(java.lang.String)
		 */
		public boolean hasAttribute(String name) {
			if (BOUNDING_BOX_ATTRIBUTE.equals(name))
				return (this.bounds != null);
			else if (BOUNDING_BOX_ATTRIBUTE.equals(name))
				return ((this.clipBounds != null) && !this.clipBounds.equals(this.bounds));
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
			else if (CLIP_BOX_ATTRIBUTE.equals(name)) {
				BoundingBox oldClipBounds = this.clipBounds;
				if (value == null)
					this.clipBounds = null;
				else if (value instanceof BoundingBox)
					this.clipBounds = ((BoundingBox) value);
				else this.clipBounds = BoundingBox.parse(value.toString());
				return oldClipBounds;
			}
			else return super.setAttribute(name, value);
		}
		
		/**
		 * Add a path to the Graphics. If the Graphics object was created in
		 * any other way than via the <code>createGraphics()</code> method,
		 * calling this method results in an <code>IllegalStateException</code>
		 * being thrown.
		 * @param path the path to add
		 */
		public void addPath(Path path) {
			if (this.modifiable) {
				this.paths.add(path);
				this.extent = null;
			}
			else throw new IllegalStateException("Use createGraphics() to obtain a modifiable instance.");
		}
		
		/**
		 * Obtain the extent of this Graphics, i.e., its unscaled size. This
		 * method basically returns a rectangle encircling all points on all
		 * paths contained in the Graphics object.
		 * @return the extent of the Graphics
		 */
		public Rectangle2D getExtent() {
			if (this.extent == null) {
				Rectangle2D extent = null;
				for (int p = 0; p < this.paths.size(); p++) {
					if (extent == null)
						extent = ((Path) this.paths.get(p)).getExtent();
					else extent = extent.createUnion(((Path) this.paths.get(p)).getExtent());
				}
				this.extent = extent;
			}
			return this.extent;
		}
		private Rectangle2D extent = null;
		
		/**
		 * Retrieve the paths making up this Graphics object.
		 * @return an array holding the paths
		 */
		public Path[] getPaths() {
			this.loadPaths();
			return ((Path[]) this.paths.toArray(new Path[this.paths.size()]));
		}
		
		private void loadPaths() {
			if (this.paths != null)
				return; // we've been here before
			this.paths = new ArrayList();
			try {
				InputStream in = this.getInputStream();
				Object data = JsonParser.parseJson(new InputStreamReader(in, "UTF-8"));
				if (data instanceof List) {
					List paths = ((List) data);
					for (int p = 0; p < paths.size(); p++) {
						Map path = JsonParser.getObject(paths, p);
						if (path != null)
							this.paths.add(Path.loadPath(path));
					}
				}
				in.close();
			}
			catch (IOException ioe) {
				ioe.printStackTrace();
			}
		}
		
		/**
		 * A single path in the graphics. A path can actually comprise multiple
		 * sequences of lines and curves (sub paths), but they are all rendered
		 * with the same configuration (color, line width, stroke, dash pattern,
		 * etc.)
		 * 
		 * @author sautter
		 */
		public static class Path {
			
			/** the bounding box of the sub path (in page image resolution) */
			public final BoundingBox bounds;
			
			/** the bounding box of the sub path (in page image resolution) */
			public final BoundingBox clipBounds;
			
			/** the page internal position at which the path is rendered */
			public final int renderOrderNumber;
			
			private Color strokeColor = null;
			private float lineWidth = Float.NaN;
			private byte lineCapStyle = ((byte) -1);
			private byte lineJointStyle = ((byte) -1);
			private float miterLimit = Float.NaN;
			private Vector dashPattern = null;
			private float dashPatternPhase = Float.NaN;
			
			private Color fillColor = null;
			
			private ArrayList subPaths = new ArrayList();
			
			/** Constructor
			 * @param bounds the bounding box of the path (in page image resolution)
			 * @param clipBounds the bounding box of the visible portion of the path on the document page
			 * @param renderOrderNumber the page internal position at which the path is rendered
			 */
			public Path(BoundingBox bounds, BoundingBox clipBounds, int renderOrderNumber) {
				this.bounds = bounds;
				this.clipBounds = ((clipBounds == null) ? bounds : clipBounds);
				this.renderOrderNumber = renderOrderNumber;
			}
			
			/** Constructor
			 * @param bounds the bounding box of the sub path (in page image resolution)
			 * @param clipBounds the bounding box of the visible portion of the path on the document page
			 * @param renderOrderNumber the page internal position at which the path is rendered
			 * @param strokeColor the color for stroking the path (if it is stroked)
			 * @param lineWidth the line width to stroke the path with (if it is stroked)
			 * @param lineCapStyle the line cap style to use for stroking (if the path is stroked)
			 * @param lineJointStyle the line joint style to use for stroking (if the path is stroked)
			 * @param miterLimit the miter limit to use for line joints (if the path is stroked)
			 * @param dashPattern the dash pattern to use for stroking (if the path is stroked)
			 * @param dashPatternPhase the phase of the dash pattern to use for stroking (if the path is stroked)
			 * @param fillColor the color to use for filling sub path (if the path is filled)
			 */
			public Path(BoundingBox bounds, BoundingBox clipBounds, int renderOrderNumber, Color strokeColor, float lineWidth, byte lineCapStyle, byte lineJointStyle, float miterLimit, Vector dashPattern, float dashPatternPhase, Color fillColor) {
				this(bounds, clipBounds, renderOrderNumber);
				this.strokeColor = strokeColor;
				this.lineWidth = lineWidth;
				this.lineCapStyle = lineCapStyle;
				this.lineJointStyle = lineJointStyle;
				this.miterLimit = miterLimit;
				this.dashPattern = dashPattern;
				this.dashPatternPhase = dashPatternPhase;
				this.fillColor = fillColor;
			}
			
			/**
			 * Retrieve the stroke color for this path. If this path is not
			 * stroked, this method returns null.
			 * @return the stroke color
			 */
			public Color getStrokeColor() {
				return this.strokeColor;
			}
			
			/**
			 * Retrieve the stroke for this path, i.e., line width, cap style,
			 * joint stile, and miter limit, as well as dashing information. If
			 * this path is not stroked, this method returns null.
			 * @return the stroke
			 */
			public BasicStroke getStroke() {
				if (this.strokeColor == null)
					return null;
				float[] dashPattern = null;
				if (this.dashPattern != null) {
					dashPattern = new float[this.dashPattern.size()];
					boolean allZeroDashes = true;
					for (int e = 0; e < this.dashPattern.size(); e++) {
						dashPattern[e] = ((Number) this.dashPattern.get(e)).floatValue();
						allZeroDashes = (allZeroDashes && (dashPattern[e] == 0));
					}
					if (allZeroDashes)
						dashPattern = null;
				}
				return new BasicStroke(this.lineWidth, this.lineCapStyle, this.lineJointStyle, ((this.miterLimit < 1) ? 1.0f : this.miterLimit), dashPattern, this.dashPatternPhase);
			}
			
			/**
			 * Retrieve the fill color for this path. If this path is not
			 * filled, this method returns null.
			 * @return the fill color
			 */
			public Color getFillColor() {
				return this.fillColor;
			}
			
			/**
			 * Test if the path is filled. This can be true in two ways: (a)
			 * the path is actually filled, i.e., has a fill color set, or (b)
			 * the path is stroked, i.e., has a stroke color set, and its line
			 * width is so large that stroking actually fills an area rather
			 * than drawing a line in a stricter sense.
			 * @return true if the path is filled
			 */
			public boolean isFilled() {
				return this.isFilled(((float) RESOLUTION) / 6); // we _are_ an area, if one just made up of lines wider than a sixth of an inch (some 4 mm)
			}
			
			/**
			 * Test if the path is filled. This can be true in two ways: (a)
			 * the path is actually filled, i.e., has a fill color set, or (b)
			 * the path is stroked, i.e., has a stroke color set, and its line
			 * width is so large that stroking actually fills an area rather
			 * than drawing a line in a stricter sense. The argument threshold
			 * specifies above which line width the latter should be assumed
			 * the case.
			 * @param minAreaLineWidth the minimum line width above which to
			 *        consider a line a filled area
			 * @return true if the path is filled
			 */
			public boolean isFilled(float minAreaLineWidth) {
				if (this.fillColor != null)
					return true;
				return ((this.strokeColor != null) && (this.lineWidth >= minAreaLineWidth));
			}
			
			/**
			 * Add a sub path to this path.
			 * @param subPath the sub path to add
			 */
			public void addSubPath(SubPath subPath) {
				this.subPaths.add(subPath);
				this.extent = null;
			}
			
			/**
			 * Retrieve the sub paths of this path.
			 * @return an array holding the sub paths
			 */
			public SubPath[] getSubPaths() {
				return ((SubPath[]) this.subPaths.toArray(new SubPath[this.subPaths.size()]));
			}
			
			/**
			 * Obtain the extent of this path, i.e., its unscaled size. This
			 * method basically returns a rectangle encircling all points on all
			 * sub paths contained in the path.
			 * @return the extent of the path
			 */
			public Rectangle2D getExtent() {
				if (this.extent == null) {
					Rectangle2D extent = null;
					for (int s = 0; s < this.subPaths.size(); s++) {
						if (extent == null)
							extent = ((SubPath) this.subPaths.get(s)).getExtent(this);
						else extent = extent.createUnion(((SubPath) this.subPaths.get(s)).getExtent(this));
					}
					this.extent = extent;
				}
				return this.extent;
			}
			private Rectangle2D extent = null;
			
			void store(OutputStream out) throws IOException {
				out.write((int) '{');
				out.write("\"bounds\": \"".getBytes("UTF-8"));
				out.write(this.bounds.toString().getBytes("UTF-8"));
				out.write("\",".getBytes("UTF-8"));
				if ((this.clipBounds != null) && !this.clipBounds.equals(this.bounds)) {
					out.write("\"clipBounds\": \"".getBytes("UTF-8"));
					out.write(this.clipBounds.toString().getBytes("UTF-8"));
					out.write("\",".getBytes("UTF-8"));
				}
				out.write("\"renderOrderNumber\": ".getBytes("UTF-8"));
				out.write(("" + this.renderOrderNumber).getBytes("UTF-8"));
				out.write(",".getBytes("UTF-8"));
				
				if (this.strokeColor != null) {
					out.write("\"strokeColor\": \"".getBytes("UTF-8"));
					out.write(encodeColor(this.strokeColor).getBytes("UTF-8"));
					out.write("\",".getBytes("UTF-8"));
				}
				
				if (!Float.isNaN(this.lineWidth)) {
					out.write("\"lineWidth\": ".getBytes("UTF-8"));
					out.write(getString(this.lineWidth).getBytes("UTF-8"));
					out.write((int) ',');
				}
				if (this.lineCapStyle >= 0) {
					out.write("\"lineCapStyle\": ".getBytes("UTF-8"));
					out.write(Byte.toString(this.lineCapStyle).getBytes("UTF-8"));
					out.write((int) ',');
				}
				if (this.lineJointStyle >= 0) {
					out.write("\"lineJointStyle\": ".getBytes("UTF-8"));
					out.write(Byte.toString(this.lineJointStyle).getBytes("UTF-8"));
					out.write((int) ',');
				}
				if (!Float.isNaN(this.miterLimit)) {
					out.write("\"miterLimit\": ".getBytes("UTF-8"));
					out.write(getString(this.miterLimit).getBytes("UTF-8"));
					out.write((int) ',');
				}
				
				if (this.dashPattern != null) {
					out.write("\"dashPattern\": [".getBytes("UTF-8"));
					for (int e = 0; e < this.dashPattern.size(); e++) {
						if (e != 0)
							out.write((int) ',');
						out.write(getString(((Number) this.dashPattern.get(e)).floatValue()).getBytes("UTF-8"));
					}
					out.write("],".getBytes("UTF-8"));
				}
				if (!Float.isNaN(this.dashPatternPhase)) {
					out.write("\"dashPatternPhase\": ".getBytes("UTF-8"));
					out.write(getString(this.dashPatternPhase).getBytes("UTF-8"));
					out.write((int) ',');
				}
				
				if (this.fillColor != null) {
					out.write("\"fillColor\": \"".getBytes("UTF-8"));
					out.write(encodeColor(this.fillColor).getBytes("UTF-8"));
					out.write("\",".getBytes("UTF-8"));
				}
				
				out.write("\"subPaths\": [".getBytes("UTF-8"));
				for (int s = 0; s < this.subPaths.size(); s++) {
					if (s != 0)
						out.write((int) ',');
					((SubPath) this.subPaths.get(s)).store(out);
				}
				out.write((int) ']');
				out.write((int) '}');
			}
			
			static Path loadPath(Map data) {
				String boundsData = JsonParser.getString(data, "bounds");
				BoundingBox pathBounds = BoundingBox.parse(boundsData);
				String clipBoundsData = JsonParser.getString(data, "clipBounds");
				BoundingBox pathClipBounds = ((clipBoundsData == null) ? null : BoundingBox.parse(clipBoundsData));
				Number renderOrderNumber = JsonParser.getNumber(data, "renderOrderNumber");
				
				Path path = new Path(pathBounds, pathClipBounds, ((renderOrderNumber == null) ? -1 : renderOrderNumber.intValue()));
				
				String strokeColor = JsonParser.getString(data, "strokeColor");
				if (strokeColor != null)
					path.strokeColor = decodeColor(strokeColor);
				
				Number lineWidth = JsonParser.getNumber(data, "lineWidth");
				if (lineWidth != null)
					path.lineWidth = lineWidth.floatValue();
				Number lineCapStyle = JsonParser.getNumber(data, "lineCapStyle");
				if (lineCapStyle != null)
					path.lineCapStyle = lineCapStyle.byteValue();
				Number lineJointStyle = JsonParser.getNumber(data, "lineJointStyle");
				if (lineJointStyle != null)
					path.lineJointStyle = lineJointStyle.byteValue();
				Number miterLimit = JsonParser.getNumber(data, "miterLimit");
				if (miterLimit != null)
					path.miterLimit = miterLimit.floatValue();
				
				List dashPattern = JsonParser.getArray(data, "dashPattern");
				if (dashPattern != null) {
					path.dashPattern = new Vector();
					for (int e = 0; e < dashPattern.size(); e++) {
						Number element = JsonParser.getNumber(dashPattern, e);
						if (element != null)
							path.dashPattern.add(new Float(element.floatValue()));
					}
				}
				Number dashPatternPhase = JsonParser.getNumber(data, "dashPatternPhase");
				if (dashPatternPhase != null)
					path.dashPatternPhase = dashPatternPhase.floatValue();
				
				String fillColor = JsonParser.getString(data, "fillColor");
				if (fillColor != null)
					path.fillColor = decodeColor(fillColor);
				
				List subPaths = JsonParser.getArray(data, "subPaths");
				if (subPaths != null)
					for (int s = 0; s < subPaths.size(); s++) {
						List subPath = JsonParser.getArray(subPaths, s);
						if (subPath != null)
							path.addSubPath(SubPath.loadSubPath(subPath));
					}
				return path;
			}
		}
		
		private static String encodeColor(Color color) {
			return (encodeInt(color.getAlpha()) + encodeInt(color.getRed()) + encodeInt(color.getGreen()) + encodeInt(color.getBlue()));
		}
		private static String encodeInt(int i) {
			String ei = Integer.toString(i, 16).toUpperCase();
			return ((ei.length() < 2) ? ("0" + ei) : ei);
		}
		private static Color decodeColor(String color) {
			long rgba = Long.parseLong(color, 16); // need to use long here, as with alpha included, color might exceed Integer.MAX_VALUE
			return new Color(((int) ((rgba >>> 16) & 255)), ((int) ((rgba >>> 8) & 255)), ((int) ((rgba >>> 0) & 255)), ((int) ((rgba >>> 24) & 255)));
		}
		
		/**
		 * A single path in the graphics. A path can actually comprise multiple
		 * sequences of lines and curves (sub paths), but they are all rendered
		 * with the same configuration (color, line width, stroke, dash pattern,
		 * etc.)
		 * 
		 * @author sautter
		 */
		public static class SubPath {
			
			/** the bounding box of the sub path (in page image resolution) */
			public final BoundingBox bounds;
			
			private ArrayList shapes = new ArrayList();
			
			/** Constructor
			 * @param bounds the bounding box of the sub path (in page image resolution)
			 */
			public SubPath(BoundingBox bounds) {
				this.bounds = bounds;
			}
			
			/**
			 * Add a line to this sub path. Coordinates of the argument line
			 * have to be top-down oriented, i.e. assuming the origin in the
			 * upper left corner of a canvas to paint on. Further, coordinates
			 * have to be in 72 DPI resolution.
			 * @param line the line to add
			 */
			public void addLine(Line2D line) {
				this.shapes.add(line);
				this.extent = null;
			}
			
			/**
			 * Add a cubic Bezier curve to this sub path. Coordinates of the
			 * argument curve have to be top-down oriented, i.e. assuming the
			 * origin in the upper left corner of a canvas to paint on. Further,
			 * coordinates have to be in 72 DPI resolution.
			 * @param curve the cubic Bezier curve to add
			 */
			public void addCurve(CubicCurve2D curve) {
				this.shapes.add(curve);
				this.extent = null;
			}
			
			/**
			 * Retrieve the individual <code>Shape</code>s making up this sub
			 * path.
			 * @return an array holding the shapes
			 */
			public Shape[] getShapes() {
				return ((Shape[]) this.shapes.toArray(new Shape[this.shapes.size()]));
			}
			
			/**
			 * Convert the sub path into a <code>Path2D</code> object for use
			 * in Java 2D graphics. The returned <code>Path2D</code> comprises
			 * the lines and curves of this sub path, connected to enable
			 * filling.
			 * @return a <code>Path2D</code> object painting this sub path
			 */
			public Path2D getPath() {
				Path2D path = new Path2D.Float();
				for (int s = 0; s < this.shapes.size(); s++)
					path.append(((Shape) this.shapes.get(s)), true);
				return path;
			}
			
			/**
			 * Obtain the extent of this sub path, i.e., its unscaled size.
			 * This method basically returns a rectangle encircling all points
			 * on this sub path.
			 * @return the extent of the sub path
			 */
			public Rectangle2D getExtent(Path parent) {
				if (this.extent == null) {
					float minX = Float.MAX_VALUE;
					float minY = Float.MAX_VALUE;
					float maxX = -Float.MAX_VALUE;
					float maxY = -Float.MAX_VALUE;
					
					//	measure actual points
					for (int s = 0; s < this.shapes.size(); s++) {
						Shape shape = ((Shape) this.shapes.get(s));
						if (shape instanceof Line2D) {
							minX = Math.min(minX, ((float) ((Line2D) shape).getX1()));
							maxX = Math.max(maxX, ((float) ((Line2D) shape).getX1()));
							minY = Math.min(minY, ((float) ((Line2D) shape).getY1()));
							maxY = Math.max(maxY, ((float) ((Line2D) shape).getY1()));
							minX = Math.min(minX, ((float) ((Line2D) shape).getX2()));
							maxX = Math.max(maxX, ((float) ((Line2D) shape).getX2()));
							minY = Math.min(minY, ((float) ((Line2D) shape).getY2()));
							maxY = Math.max(maxY, ((float) ((Line2D) shape).getY2()));
						}
						else if (shape instanceof CubicCurve2D) {
							minX = Math.min(minX, ((float) ((CubicCurve2D) shape).getX1()));
							maxX = Math.max(maxX, ((float) ((CubicCurve2D) shape).getX1()));
							minY = Math.min(minY, ((float) ((CubicCurve2D) shape).getY1()));
							maxY = Math.max(maxY, ((float) ((CubicCurve2D) shape).getY1()));
							minX = Math.min(minX, ((float) ((CubicCurve2D) shape).getCtrlX1()));
							maxX = Math.max(maxX, ((float) ((CubicCurve2D) shape).getCtrlX1()));
							minY = Math.min(minY, ((float) ((CubicCurve2D) shape).getCtrlY1()));
							maxY = Math.max(maxY, ((float) ((CubicCurve2D) shape).getCtrlY1()));
							minX = Math.min(minX, ((float) ((CubicCurve2D) shape).getCtrlX2()));
							maxX = Math.max(maxX, ((float) ((CubicCurve2D) shape).getCtrlX2()));
							minY = Math.min(minY, ((float) ((CubicCurve2D) shape).getCtrlY2()));
							maxY = Math.max(maxY, ((float) ((CubicCurve2D) shape).getCtrlY2()));
							minX = Math.min(minX, ((float) ((CubicCurve2D) shape).getX2()));
							maxX = Math.max(maxX, ((float) ((CubicCurve2D) shape).getX2()));
							minY = Math.min(minY, ((float) ((CubicCurve2D) shape).getY2()));
							maxY = Math.max(maxY, ((float) ((CubicCurve2D) shape).getY2()));
						}
					}
					
					/* if path is stroked, factor in line width and line caps
					 * (yes, rectangles _can_ and do come as very wide lines
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
					
					//	finally ...
					this.extent = new Rectangle2D.Float(minX, minY, (maxX - minX), (maxY - minY));
				}
				return this.extent;
			}
			private Rectangle2D extent = null;
			
			void store(OutputStream out) throws IOException {
				out.write((int) '[');
				out.write("\"".getBytes("UTF-8"));
				out.write(this.bounds.toString().getBytes("UTF-8"));
				out.write("\",".getBytes("UTF-8"));
				
				int shapesStored = 0;
				for (int s = 0; s < this.shapes.size(); s++) {
					Shape shape = ((Shape) this.shapes.get(s));
					if (shape instanceof Line2D) {
						Line2D line = ((Line2D) shape);
						if (shapesStored != 0)
							out.write((int) ',');
						out.write((int) '[');
						out.write(getString(line.getX1()).getBytes("UTF-8"));
						out.write((int) ',');
						out.write(getString(line.getY1()).getBytes("UTF-8"));
						out.write((int) ',');
						out.write(getString(line.getX2()).getBytes("UTF-8"));
						out.write((int) ',');
						out.write(getString(line.getY2()).getBytes("UTF-8"));
						out.write((int) ']');
						shapesStored++;
					}
					else if (shape instanceof CubicCurve2D) {
						CubicCurve2D curve = ((CubicCurve2D) shape);
						if (shapesStored != 0)
							out.write((int) ',');
						out.write((int) '[');
						out.write(getString(curve.getX1()).getBytes("UTF-8"));
						out.write((int) ',');
						out.write(getString(curve.getY1()).getBytes("UTF-8"));
						out.write((int) ',');
						out.write(getString(curve.getCtrlX1()).getBytes("UTF-8"));
						out.write((int) ',');
						out.write(getString(curve.getCtrlY1()).getBytes("UTF-8"));
						out.write((int) ',');
						out.write(getString(curve.getCtrlX2()).getBytes("UTF-8"));
						out.write((int) ',');
						out.write(getString(curve.getCtrlY2()).getBytes("UTF-8"));
						out.write((int) ',');
						out.write(getString(curve.getX2()).getBytes("UTF-8"));
						out.write((int) ',');
						out.write(getString(curve.getY2()).getBytes("UTF-8"));
						out.write((int) ']');
						shapesStored++;
					}
				}
				out.write((int) ']');
			}
			
			static SubPath loadSubPath(List data) {
				String boundsData = JsonParser.getString(data, 0);
				BoundingBox subPathBounds = BoundingBox.parse(boundsData);
				
				SubPath subPath = new SubPath(subPathBounds);
				for (int s = 1; s < data.size(); s++) {
					List sData = JsonParser.getArray(data, s);
					if (sData == null) {}
					else if (sData.size() == 4) {
						float x1 = JsonParser.getNumber(sData, 0).floatValue();
						float y1 = JsonParser.getNumber(sData, 1).floatValue();
						float x2 = JsonParser.getNumber(sData, 2).floatValue();
						float y2 = JsonParser.getNumber(sData, 3).floatValue();
						subPath.addLine(new Line2D.Float(x1, y1, x2, y2));
					}
					else if (sData.size() == 8) {
						float x1 = JsonParser.getNumber(sData, 0).floatValue();
						float y1 = JsonParser.getNumber(sData, 1).floatValue();
						float cx1 = JsonParser.getNumber(sData, 2).floatValue();
						float cy1 = JsonParser.getNumber(sData, 3).floatValue();
						float cx2 = JsonParser.getNumber(sData, 4).floatValue();
						float cy2 = JsonParser.getNumber(sData, 5).floatValue();
						float x2 = JsonParser.getNumber(sData, 6).floatValue();
						float y2 = JsonParser.getNumber(sData, 7).floatValue();
						subPath.addCurve(new CubicCurve2D.Float(x1, y1, cx1, cy1, cx2, cy2, x2, y2));
					}
				}
				return subPath;
			}
		}
		
		private static String getString(double d) {
			return Float.toString((float) d); // might be more accurate ...
//			double f = Math.floor(d);
//			int i = ((int) Math.floor(d));
//			int s = ((int) Math.round((d - f) * 1000)); // snapping to 1/1000 is about how precise PDF does it for glyphs ...
//			return (i + "." + s);
		}
		
		/**
		 * Create an empty Graphics object. Only Graphics objects obtained from
		 * this method can be modified via the <code>addPath()</code> method -
		 * all others will throw an <code>IllegalStateException</code>. The
		 * bounding box of the graphics has to be in page image resolution to
		 * facilitate relating a graphics to an ImRegion marking its counterpart
		 * in a document page. Further, the created Graphics is automatically
		 * added to the argument document.
		 * @param doc the document the graphics belongs to
		 * @param pageId the ID of the page the scan belongs to
		 * @param renderOrderNumber the page internal position at which the graphics is rendered
		 * @param bounds the bounding box of the graphics on the document page
		 * @return the newly created Graphics
		 */
		public static Graphics createGraphics(ImDocument doc, int pageId, int renderOrderNumber, BoundingBox bounds) {
			return createGraphics(doc, pageId, renderOrderNumber, bounds, null);
		}
		
		/**
		 * Create an empty Graphics object. Only Graphics objects obtained from
		 * this method can be modified via the <code>addPath()</code> method -
		 * all others will throw an <code>IllegalStateException</code>. The
		 * bounding box of the graphics has to be in page image resolution to
		 * facilitate relating a graphics to an ImRegion marking its counterpart
		 * in a document page. Further, the created Graphics is automatically
		 * added to the argument document.
		 * @param doc the document the graphics belongs to
		 * @param pageId the ID of the page the scan belongs to
		 * @param renderOrderNumber the page internal position at which the graphics is rendered
		 * @param bounds the bounding box of the graphics on the document page
		 * @param clipBounds the bounding box of the visible portion of the graphics on the document page
		 * @return the newly created Graphics
		 */
		public static Graphics createGraphics(ImDocument doc, int pageId, int renderOrderNumber, BoundingBox bounds, BoundingBox clipBounds) {
			return new Graphics(doc, pageId, renderOrderNumber, bounds, clipBounds, true) {
				byte[] data = new byte[0];
				int dataPathCount = 0;
				public InputStream getInputStream() throws IOException {
					/* We need the null catch in case getInputStream() is called
					 * somehow by the super constructor, i.e., before this very
					 * class is completely constructed. This can happen in disk
					 * cache scenarios. */
					if ((this.paths != null) && (this.paths.size() != this.dataPathCount)) {
						ByteArrayOutputStream baos = new ByteArrayOutputStream();
						baos.write((int) '[');
						for (int p = 0; p < this.paths.size(); p++) {
							if (p != 0)
								baos.write((int) ',');
							((Path) this.paths.get(p)).store(baos);
						}
						baos.write((int) ']');
						this.data = baos.toByteArray();
						this.dataPathCount = this.paths.size();
					}
					/* We need the null catch in case getInputStream() is called
					 * somehow by the super constructor, i.e., before this very
					 * class is completely constructed. This can happen in disk
					 * cache scenarios. */
					return new ByteArrayInputStream((this.data == null) ? new byte[0] : this.data);
				}
			};
		}
		
		/**
		 * Merge two graphics objects. The two argument graphics have to lie on
		 * the same page; if they do not, this method throws an
		 * <code>IllegalArgumentException</code>.
		 * @param g1 the first Graphics object to merge
		 * @param g2 the second Graphics object to merge
		 * @return a merged graphics object, comprising the paths of both
		 *        argument graphics
		 */
		public static Graphics merge(Graphics g1, Graphics g2) {
			if (g1 == g2)
				return g1;
			if (g1 == null)
				return g2;
			if (g2 == null)
				return g1;
			if (g1.getDocument() != g2.getDocument())
				throw new IllegalArgumentException("Can only merge Graphics objects from the same document.");
			if ((g1.pageId != -1) && (g2.pageId != -1) && (g1.pageId != g2.pageId))
				throw new IllegalArgumentException("Can only merge Graphics objects from the same page.");
			BoundingBox[] gBounds = {g1.bounds, g2.bounds};
			BoundingBox[] gClipBounds = {g1.clipBounds, g2.clipBounds};
			Graphics m = createGraphics(g1.getDocument(), Math.max(g1.pageId, g2.pageId), Math.max(g1.renderOrderNumber, g2.renderOrderNumber), BoundingBox.aggregate(gBounds), BoundingBox.aggregate(gClipBounds));
			Path[] ps1 = g1.getPaths();
			for (int p = 0; p < ps1.length; p++)
				m.addPath(ps1[p]);
			Path[] ps2 = g2.getPaths();
			for (int p = 0; p < ps2.length; p++)
				m.addPath(ps2[p]);
			return m;
		}
	}
	
	/**
	 * From an array of supplements, filter out the <code>Figure</code> ones
	 * laying in a given bounding box.
	 * @param supplements the supplements to choose from
	 * @param box the bounding box whose contained figures to get
	 * @return an array holding the figures
	 */
	public static Figure[] getFiguresIn(ImSupplement[] supplements, BoundingBox box) {
		ArrayList containedFigures = new ArrayList(1);
		for (int s = 0; s < supplements.length; s++) {
			if ((supplements[s] instanceof Figure) && ((Figure) supplements[s]).getBounds().liesIn(box, true))
				containedFigures.add(supplements[s]);
		}
		return ((Figure[]) containedFigures.toArray(new Figure[containedFigures.size()]));
	}
	
	/**
	 * From an array of supplements, filter out the <code>Figure</code> ones
	 * that overlap with a given bounding box.
	 * @param supplements the supplements to choose from
	 * @param box the bounding box whose overlapping figures to get
	 * @return an array holding the figures
	 */
	public static Figure[] getFiguresAt(ImSupplement[] supplements, BoundingBox box) {
		ArrayList containingFigures = new ArrayList(1);
		for (int s = 0; s < supplements.length; s++) {
			if ((supplements[s] instanceof Figure) && ((Figure) supplements[s]).getBounds().includes(box, true))
				containingFigures.add(supplements[s]);
		}
		return ((Figure[]) containingFigures.toArray(new Figure[containingFigures.size()]));
	}
	
	/**
	 * From an array of supplements, filter out the <code>Graphics</code> ones
	 * laying in a given bounding box.
	 * @param supplements the supplements to choose from
	 * @param box the bounding box whose contained graphics to get
	 * @return an array holding the graphics
	 */
	public static Graphics[] getGraphicsIn(ImSupplement[] supplements, BoundingBox box) {
		ArrayList containedFigures = new ArrayList(1);
		for (int s = 0; s < supplements.length; s++) {
			if ((supplements[s] instanceof Graphics) && ((Graphics) supplements[s]).getBounds().liesIn(box, true))
				containedFigures.add(supplements[s]);
		}
		return ((Graphics[]) containedFigures.toArray(new Graphics[containedFigures.size()]));
	}
	
	/**
	 * From an array of supplements, filter out the <code>Graphics</code> ones
	 * that overlap with a given bounding box.
	 * @param supplements the supplements to choose from
	 * @param box the bounding box whose overlapping graphics to get
	 * @return an array holding the graphics
	 */
	public static Graphics[] getGraphicsAt(ImSupplement[] supplements, BoundingBox box) {
		ArrayList containingFigures = new ArrayList(1);
		for (int s = 0; s < supplements.length; s++) {
			if ((supplements[s] instanceof Graphics) && ((Graphics) supplements[s]).getBounds().includes(box, true))
				containingFigures.add(supplements[s]);
		}
		return ((Graphics[]) containingFigures.toArray(new Graphics[containingFigures.size()]));
	}
	
	/**
	 * Render an image from a combination of <code>Figure</code> and
	 * <code>Graphics</code> supplements, overlaid with any labeling
	 * <code>ImWord</code>s. While there is no strict need for the arguments
	 * objects to lie in the argument page, the latter is the most sensible
	 * scenario. If the argument rendering DPI is a positive integer, it is
	 * used as it is; otherwise, the maximum resolution of any of the argument
	 * <code>Figure</code> and <code>Graphics</code> supplements is used.<br/>
	 * Rendering adds two pixels of margin on every edge of the returned image,
	 * so the image slightly exceeds the aggregate bounds of the rendered
	 * objects.
	 * @param figures the figures to include
	 * @param scaleToDpi the resolution to render at
	 * @param graphics the graphics to include
	 * @param words the labeling words to include
	 * @param page the page the other argument objects lie in
	 * @return the composed image
	 */
	public static BufferedImage getCompositeImage(Figure[] figures, int scaleToDpi, Graphics[] graphics, ImWord[] words, ImPage page) {
		return getCompositeImage(figures, scaleToDpi, graphics, words, page, BufferedImage.TYPE_INT_RGB);
	}
	
	/**
	 * Render an image from a combination of <code>Figure</code> and
	 * <code>Graphics</code> supplements, overlaid with any labeling
	 * <code>ImWord</code>s. While there is no strict need for the arguments
	 * objects to lie in the argument page, the latter is the most sensible
	 * scenario. If the argument rendering DPI is a positive integer, it is
	 * used as it is; otherwise, the maximum resolution of any of the argument
	 * <code>Figure</code> and <code>Graphics</code> supplements is used.<br/>
	 * Rendering adds two pixels of margin on every edge of the returned image,
	 * so the image slightly exceeds the aggregate bounds of the rendered
	 * objects.<br/>
	 * The argument <code>imageType</code> has to be one of the type constants
	 * from <code>BufferedImage</code>.
	 * @param figures the figures to include
	 * @param scaleToDpi the resolution to render at
	 * @param graphics the graphics to include
	 * @param words the labeling words to include
	 * @param page the page the other argument objects lie in
	 * @param imageType the type of image (color model) to use
	 * @return the composed image
	 */
	public static BufferedImage getCompositeImage(Figure[] figures, int scaleToDpi, Graphics[] graphics, ImWord[] words, ImPage page, int imageType) {
		
		//	compute export figure bounds in page resolution
		BoundingBox peBounds = getCompositeBounds(figures, graphics, words);
		if (peBounds == null)
			return null;
		
		//	compute resolution if none specifically given
		if (scaleToDpi < 1) {
			if (figures != null) {
				for (int f = 0; f < figures.length; f++)
					scaleToDpi = Math.max(scaleToDpi, figures[f].getDpi());
			}
			if (graphics != null) {
				for (int g = 0; g < graphics.length; g++)
					scaleToDpi = Math.max(scaleToDpi, graphics[g].getDpi());
			}
			if (scaleToDpi < 1)
				return null;
		}
		
		//	translate any words to export bounds
		BoundingBox[] peWordBounds = new BoundingBox[0];
		if (words != null) {
			peWordBounds = new BoundingBox[words.length];
			for (int w = 0; w < words.length; w++)
				peWordBounds[w] = words[w].getBounds().translate(-peBounds.left, -peBounds.top);
		}
		
		//	scale everything to export resolution
		float boundsScale = (((float) scaleToDpi) / page.getImageDPI());
		BoundingBox eBounds = peBounds.translate(-peBounds.left, -peBounds.top).scale(boundsScale);
		BoundingBox[] eWordBounds = new BoundingBox[peWordBounds.length];
		for (int w = 0; w < peWordBounds.length; w++)
			eWordBounds[w] = peWordBounds[w].scale(boundsScale);
		
		//	tray up figures and graphics or paths, and collect rendering order numbers
		ArrayList objects = new ArrayList();
		HashSet objectRenderingOrderPositions = new HashSet();
		if (figures != null)
			for (int f = 0; f < figures.length; f++) {
				BoundingBox peFigureBounds = figures[f].getBounds().translate(-peBounds.left, -peBounds.top);
				BoundingBox eFigureBounds = peFigureBounds.scale(boundsScale);
				objects.add(new ObjectRenderingTray(figures[f], eFigureBounds));
				objectRenderingOrderPositions.add(new Integer(figures[f].getRenderOrderNumber()));
			}
		if (graphics != null)
			for (int g = 0; g < graphics.length; g++) {
				BoundingBox peGraphicsBounds = graphics[g].getBounds().translate(-peBounds.left, -peBounds.top);
				BoundingBox eGraphicsBounds = peGraphicsBounds.scale(boundsScale);
				
				//	without figures, we can render graphics objects as a whole
				if ((figures == null) || (figures.length == 0)) {
					objects.add(new ObjectRenderingTray(graphics[g], eGraphicsBounds));
					objectRenderingOrderPositions.add(new Integer(graphics[g].getRenderOrderNumber()));
				}
				
				//	with figures present, we need to make sure individual paths and figures are rendered in the appropriate order
				else {
					Path[] paths = graphics[g].getPaths();
					for (int p = 0; p < paths.length; p++) {
						BoundingBox pePathBounds = paths[p].bounds.translate(-peBounds.left, -peBounds.top);
						BoundingBox ePathBounds = pePathBounds.scale(boundsScale);
						objects.add(new ObjectRenderingTray(paths[p], ePathBounds, graphics[g].getDpi(), eGraphicsBounds));
						objectRenderingOrderPositions.add(new Integer(paths[p].renderOrderNumber));
					}
				}
			}
//		System.out.println("Got " + objects.size() + " images and " + objectRenderingOrderPositions.size() + " distinct rendering positions");
		
		//	sort trays, either by rendering order position (if we have sufficiently many distinct ones), or by size
		if ((objectRenderingOrderPositions.size() * 2) > objects.size()) {
//			System.out.println("Using rendering sequence order");
			Collections.sort(objects, renderingSequenceOrder);
		}
		else {
//			System.out.println("Using size order");
			Collections.sort(objects, imageSizeOrder);
		}
		
		//	set up rendering
		BufferedImage image = new BufferedImage((eBounds.getWidth() + 4), (eBounds.getHeight() + 4), imageType);
		Graphics2D renderer = image.createGraphics();
		renderer.setColor(Color.WHITE);
		renderer.fillRect(0, 0, image.getWidth(), image.getHeight());
		renderer.translate(2, 2);
		renderer.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		
		//	render figures and graphics
		for (int o = 0; o < objects.size(); o++)
			((ObjectRenderingTray) objects.get(o)).render(renderer, scaleToDpi);
		
		//	render words
		if (words != null) {
			for (int w = 0; w < words.length; w++) {
				AffineTransform preAt = renderer.getTransform();
				renderer.translate(eWordBounds[w].left, eWordBounds[w].bottom);
				
				//	determine rendering color based on background
				Color wordColor = getTextColorAt(image, eWordBounds[w]);
				renderer.setColor(wordColor);
				
				//	prepare font
				Font font = new Font("FreeSans", Font.PLAIN, 1);
				
				//	render word, finally ...
				ImWord.render(words[w], font, scaleToDpi, false, renderer);
				
				//	reset rendering graphics
				renderer.setTransform(preAt);
			}
		}
		
		//	finally ...
		return image;
	}
	
	private static class ObjectRenderingTray {
		private Figure figure;
		private Graphics graphics;
		private Path path;
		private int sourceDpi;
		private BoundingBox renderingBounds;
		final BoundingBox renderingArea;
		final int renderingOrderPrecedence;
		ObjectRenderingTray(Figure figure, BoundingBox renderingBounds) {
			this.figure = figure;
			this.sourceDpi = this.figure.getDpi();
			this.renderingBounds = renderingBounds;
			this.renderingArea = this.renderingBounds;
			this.renderingOrderPrecedence = 1;
//			System.out.println("Got Figure at " + this.renderingBounds.toString());
		}
		ObjectRenderingTray(Graphics graphics, BoundingBox renderingBounds) {
			this.graphics = graphics;
			this.sourceDpi = this.graphics.getDpi();
			this.renderingBounds = renderingBounds;
			this.renderingArea = this.renderingBounds;
			this.renderingOrderPrecedence = 1;
//			System.out.println("Got Graphics at " + this.renderingBounds.toString());
		}
		ObjectRenderingTray(Path path, BoundingBox renderingBounds, int parentGraphicsDpi, BoundingBox parentGraphicsRenderingBounds) {
			this.path = path;
			this.sourceDpi = parentGraphicsDpi;
			this.renderingBounds = parentGraphicsRenderingBounds;
			this.renderingArea = renderingBounds;
			this.renderingOrderPrecedence = ((path.getFillColor() == null) ? 2 : 0);
//			System.out.println("Got Path at " + this.renderingBounds.toString());
		}
		void render(Graphics2D renderer, int scaleToDpi) {
			AffineTransform preAt = renderer.getTransform();
			renderer.translate(this.renderingBounds.left, this.renderingBounds.top);
			
			if (this.graphics != null) {
//				System.out.println("Rendering Graphics at " + this.renderingBounds.toString());
				float renderScale = (((float) scaleToDpi) / this.sourceDpi);
				renderer.scale(renderScale, renderScale);
				
				Path[] paths = this.graphics.getPaths();
				for (int p = 0; p < paths.length; p++) {
					Color preColor = renderer.getColor();
					Stroke preStroke = renderer.getStroke();
					Color strokeColor = paths[p].getStrokeColor();
					Stroke stroke = paths[p].getStroke();
					Color fillColor = paths[p].getFillColor();
					if ((strokeColor == null) && (fillColor == null))
						continue;
					
					Path2D path = new Path2D.Float();
					Graphics.SubPath[] subPaths = paths[p].getSubPaths();
					for (int s = 0; s < subPaths.length; s++) {
						Path2D subPath = subPaths[s].getPath();
						path.append(subPath, false);
					}
					
					if (fillColor != null) {
						renderer.setColor(fillColor);
						renderer.fill(path);
					}
					if (strokeColor != null) {
						renderer.setColor(strokeColor);
						renderer.setStroke(stroke);
						renderer.draw(path);
					}
//					else if (fillColor != null) {
//						renderer.setColor(fillColor);
//						renderer.draw(path);
//					}
					
//					NEED TO FIRST COLLECT ALL SUB PATHS AND THEN FILL THE WHOLE PATH SO EVEN-ODD RULE CAN TAKE EFFECT ON FILLING
//					SubPath[] subPaths = paths[p].getSubPaths();
//					for (int s = 0; s < subPaths.length; s++) {
//						
//						//	render sub path
//						Path2D path = subPaths[s].getPath();
//						if (fillColor != null) {
//							renderer.setColor(fillColor);
//							renderer.fill(path);
//						}
//						if (strokeColor != null) {
//							renderer.setColor(strokeColor);
//							renderer.setStroke(stroke);
//							renderer.draw(path);
//						}
//						else if (fillColor != null) {
//							renderer.setColor(fillColor);
//							renderer.draw(path);
//						}
//					}
					renderer.setColor(preColor);
					renderer.setStroke(preStroke);
				}
			}
			else if (this.path != null) {
//				System.out.println("Rendering Path at " + this.renderingBounds.toString());
				float renderScale = (((float) scaleToDpi) / this.sourceDpi);
				renderer.scale(renderScale, renderScale);
				
				Color preColor = renderer.getColor();
				Stroke preStroke = renderer.getStroke();
				Color strokeColor = this.path.getStrokeColor();
				Stroke stroke = this.path.getStroke();
				Color fillColor = this.path.getFillColor();
				
				Path2D path = new Path2D.Float();
				Graphics.SubPath[] subPaths = this.path.getSubPaths();
				for (int s = 0; s < subPaths.length; s++) {
					Path2D subPath = subPaths[s].getPath();
					path.append(subPath, false);
				}
				
				if (fillColor != null) {
					renderer.setColor(fillColor);
					renderer.fill(path);
				}
				if (strokeColor != null) {
					renderer.setColor(strokeColor);
					renderer.setStroke(stroke);
					renderer.draw(path);
				}
//				else if (fillColor != null) {
//					renderer.setColor(fillColor);
//					renderer.draw(path);
//				}
				
//				NEED TO FIRST COLLECT ALL SUB PATHS AND THEN FILL THE WHOLE PATH SO EVEN-ODD RULE CAN TAKE EFFECT ON FILLING
//				SubPath[] subPaths = this.path.getSubPaths();
//				for (int s = 0; s < subPaths.length; s++) {
//					
//					//	render sub path
//					Path2D path = subPaths[s].getPath();
//					if (fillColor != null) {
//						renderer.setColor(fillColor);
//						renderer.fill(path);
//					}
//					if (strokeColor != null) {
//						renderer.setColor(strokeColor);
//						renderer.setStroke(stroke);
//						renderer.draw(path);
//					}
//					else if (fillColor != null) {
//						renderer.setColor(fillColor);
//						renderer.draw(path);
//					}
//				}
				renderer.setColor(preColor);
				renderer.setStroke(preStroke);
			}
			else if (this.figure != null) try {
//				System.out.println("Rendering Figure at " + this.renderingBounds.toString());
				BufferedImage fImage = ImageIO.read(this.figure.getInputStream());
//				NO NEED TO SCALE EXPLICITLY, draw() DOES THAT BY ITSELF
//				float hRenderScale = (((float) (eFigureBounds[f].right - eFigureBounds[f].left)) / fImage.getWidth());
//				float vRenderScale = (((float) (eFigureBounds[f].bottom - eFigureBounds[f].top)) / fImage.getHeight());
//				renderer.scale(hRenderScale, vRenderScale);
				renderer.drawImage(fImage, 0, 0, (this.renderingBounds.right - this.renderingBounds.left), (this.renderingBounds.bottom - this.renderingBounds.top), null);
			} catch (IOException ioe) {}
			
			renderer.setTransform(preAt);
		}
		
		int getRenderOrderNumber() {
			if (this.figure != null)
				return this.figure.getRenderOrderNumber();
			else if (this.graphics != null)
				return this.graphics.getRenderOrderNumber();
			else if (this.path != null)
				return this.path.renderOrderNumber;
			else return -1;
		}
	}
	
	private static final Comparator imageSizeOrder = new Comparator() {
		public int compare(Object obj1, Object obj2) {
			BoundingBox bb1 = ((ObjectRenderingTray) obj1).renderingArea;
			BoundingBox bb2 = ((ObjectRenderingTray) obj2).renderingArea;
			int a1 = ((bb1.right - bb1.left) * (bb1.bottom - bb1.top));
			int a2 = ((bb2.right - bb2.left) * (bb2.bottom - bb2.top));
			if (a1 != a2)
				return (a2 - a1);
			int rop1 = ((ObjectRenderingTray) obj1).renderingOrderPrecedence;
			int rop2 = ((ObjectRenderingTray) obj2).renderingOrderPrecedence;
			return (rop1 - rop2);
		}
	};
	
	private static final Comparator renderingSequenceOrder = new Comparator() {
		public int compare(Object obj1, Object obj2) {
			int ron1 = ((ObjectRenderingTray) obj1).getRenderOrderNumber();
			int ron2 = ((ObjectRenderingTray) obj2).getRenderOrderNumber();
			return (ron1 - ron2);
		}
	};
	
	/**
	 * Produce SVG (Scalable Vector Graphics) XML from one or more
	 * <code>Graphics</code> objects, including labeling <code>ImWord</code>s.
	 * The nominal resolution of the returned SVG is the default 72 DPI. While
	 * there is no strict need for the arguments objects to lie in the argument
	 * page, the latter is the most sensible scenario.
	 * @param graphics the graphics to include
	 * @param words the words to include
	 * @param page the page the other argument objects lie in
	 * @return the SVG XML representing the argument objects
	 */
	public static CharSequence getSvg(Graphics[] graphics, ImWord[] words, ImPage page) {
		
		//	compute overall bounds
		BoundingBox peBounds = getCompositeBounds(null, graphics, words);
		if (peBounds == null)
			return null;
		
		//	translate contents to export bounds
		BoundingBox[] peGraphicsBounds = new BoundingBox[0];
		if (graphics != null) {
			peGraphicsBounds = new BoundingBox[graphics.length];
			for (int g = 0; g < graphics.length; g++)
				peGraphicsBounds[g] = graphics[g].getBounds().translate(-peBounds.left, -peBounds.top);
		}
		BoundingBox[] peWordBounds = new BoundingBox[0];
		if (words != null) {
			peWordBounds = new BoundingBox[words.length];
			for (int w = 0; w < words.length; w++)
				peWordBounds[w] = words[w].getBounds().translate(-peBounds.left, -peBounds.top);
		}
		
		//	scale everything to export resolution
		float boundsScale = (((float) Graphics.RESOLUTION) / page.getImageDPI());
		BoundingBox eBounds = peBounds.translate(-peBounds.left, -peBounds.top).scale(boundsScale);
		BoundingBox[] eGraphicsBounds = new BoundingBox[peGraphicsBounds.length];
		for (int g = 0; g < peGraphicsBounds.length; g++)
			eGraphicsBounds[g] = peGraphicsBounds[g].scale(boundsScale);
		BoundingBox[] eWordBounds = new BoundingBox[peWordBounds.length];
		for (int w = 0; w < peWordBounds.length; w++)
			eWordBounds[w] = peWordBounds[w].scale(boundsScale);
		
		//	prepare SVG generation
		StringBuffer svg = new StringBuffer();
		
		//	add preface TODOne do we really need this sucker, especially the DTD? ==> does not seem so
		svg.append("<?xml version=\"1.0\" standalone=\"no\"?>");
//		svg.append("<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">");
		
		//	write start tag (including nominal width and height at 72 DPI)
		svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\"");
		svg.append(" width=\"" + (eBounds.right - eBounds.left) + "px\"");
		svg.append(" height=\"" + (eBounds.bottom - eBounds.top) + "px\"");
		svg.append(">");
		
		//	render graphics
		for (int g = 0; g < graphics.length; g++) {
			
			//	translate rendering bounds
			float hTrans = (eGraphicsBounds[g].left - eBounds.left);
			float vTrans = (eGraphicsBounds[g].top - eBounds.top);
			
			//	render paths
			Path[] paths = graphics[g].getPaths();
			for (int p = 0; p < paths.length; p++) {
				
				//	get path properties
				Color strokeColor = paths[p].getStrokeColor();
				Color fillColor = paths[p].getFillColor();
				
				//	open tag (including stroking and filling properties) and open data attribute
				svg.append("<path");
				if (strokeColor != null) {
					svg.append(" stroke=\"#" + getHexRGB(strokeColor) + "\"");
					if (strokeColor.getAlpha() < 255)
						svg.append(" stroke-opacity=\"" + (((float) strokeColor.getAlpha()) / 255) + "\"");
					BasicStroke bStroke = ((BasicStroke) paths[p].getStroke());
					svg.append(" stroke-width=\"" + bStroke.getLineWidth() + "px\"");
					svg.append(" stroke-linecap=\"" + bStroke.getEndCap() + "\"");
					svg.append(" stroke-linejoin=\"" + bStroke.getLineJoin() + "\"");
					svg.append(" stroke-miterlimit=\"" + bStroke.getMiterLimit() + "\"");
					float[] dashArray = bStroke.getDashArray();
					if (dashArray != null) {
						svg.append(" stroke-dasharray=\"");
						for (int d = 0; d < dashArray.length; d++) {
							if (d != 0)
								svg.append(",");
							svg.append("" + dashArray[d]);
						}
						svg.append("\"");
						svg.append(" stroke-dashoffset=\"" + bStroke.getDashPhase() + "\"");
					}
				}
				if (fillColor != null) {
					svg.append(" fill=\"#" + getHexRGB(fillColor) + "\"");
					if (fillColor.getAlpha() < 255)
						svg.append(" fill-opacity=\"" + (((float) fillColor.getAlpha()) / 255) + "\"");
				}
				else svg.append(" fill=\"none\"");
				svg.append(" d=\"");
				
				//	add rendering commands for sub paths
				SubPath[] subPaths = paths[p].getSubPaths();
				for (int sp = 0; sp < subPaths.length; sp++) {
					
					//	render shapes
					Shape[] shapes = subPaths[sp].getShapes();
					for (int s = 0; s < shapes.length; s++) {
						if (shapes[s] instanceof Line2D) {
							Line2D line = ((Line2D) shapes[s]);
							
							//	move to starting point
							if (s == 0) {
								svg.append(((sp == 0) ? "" : " ") + "M");
								svg.append(" " + (hTrans + line.getX1()));
								svg.append(" " + (vTrans + line.getY1()));
							}
							
							//	draw line
							svg.append(" L");
							svg.append(" " + (hTrans + line.getX2()));
							svg.append(" " + (vTrans + line.getY2()));
						}
						else if (shapes[s] instanceof CubicCurve2D) {
							CubicCurve2D curve = ((CubicCurve2D) shapes[s]);
							
							//	move to starting point
							if (s == 0) {
								svg.append(((sp == 0) ? "" : " ") + "M");
								svg.append(" " + (hTrans + curve.getX1()));
								svg.append(" " + (vTrans + curve.getY1()));
							}
							
							//	draw curve
							svg.append(" C");
							svg.append(" " + (hTrans + curve.getCtrlX1()));
							svg.append(" " + (vTrans + curve.getCtrlY1()));
							svg.append(" " + (hTrans + curve.getCtrlX2()));
							svg.append(" " + (vTrans + curve.getCtrlY2()));
							svg.append(" " + (hTrans + curve.getX2()));
							svg.append(" " + (vTrans + curve.getY2()));
						}
					}
				}
				
				//	close data attribute and tag
				svg.append("\"/>");
			}
		}
		
		//	render words
		if (words != null)
			for (int w = 0; w < words.length; w++) {
				
				//	translate rendering bounds and position
				float x = (eWordBounds[w].left - eBounds.left);
				float y;
				
				//	get font size and text orientation
				int fontSize = words[w].getFontSize();
				Object to = words[w].getAttribute(ImWord.TEXT_DIRECTION_ATTRIBUTE, ImWord.TEXT_DIRECTION_LEFT_RIGHT);
				
				//	write start tag, including rendering properties
				svg.append("<text ");
				svg.append(" x=\"" + x + "px\"");
				if (ImWord.TEXT_DIRECTION_BOTTOM_UP.equals(to)) {
					y = ((eWordBounds[w].bottom - eBounds.top) - (eWordBounds[w].getWidth() * 0.275f)); // factor in that text is anchored at baseline (some 25-30 percent from bottom of word box in most fonts), not top left corner
					svg.append(" y=\"" + y + "px\"");
					svg.append(" transform=\"rotate(-90, " + (x + (((float) eWordBounds[w].getWidth()) / 2)) + ", " + (y - (((float) eWordBounds[w].getWidth()) / 2)) + ")\"");
					svg.append(" textLength=\"" + eWordBounds[w].getHeight() + "px\"");
				}
				else if (ImWord.TEXT_DIRECTION_TOP_DOWN.equals(to)) {
					y = ((eWordBounds[w].bottom - eBounds.top) - (eWordBounds[w].getWidth() * 0.275f)); // factor in that text is anchored at baseline (some 25-30 percent from bottom of word box in most fonts), not top left corner
					svg.append(" y=\"" + y + "px\"");
					svg.append(" transform=\"rotate(90, " + (x + (((float) eWordBounds[w].getHeight()) / 2)) + ", " + (y - (((float) eWordBounds[w].getHeight()) / 2)) + ")\"");
					svg.append(" textLength=\"" + eWordBounds[w].getHeight() + "px\"");
				}
				else {
					y = ((eWordBounds[w].bottom - eBounds.top) - (eWordBounds[w].getHeight() * 0.275f)); // factor in that text is anchored at baseline (some 25-30 percent from bottom of word box in most fonts), not top left corner
					svg.append(" y=\"" + y + "px\"");
					svg.append(" textLength=\"" + eWordBounds[w].getWidth() + "px\"");
				}
				svg.append(" font-family=\"" + "sans-serif" + "\""); // default to sans-serif font for now ...
				if (fontSize != -1)
					svg.append(" font-size=\"" + fontSize + "\"");
				if (words[w].hasAttribute(ImWord.BOLD_ATTRIBUTE))
					svg.append(" font-weight=\"bold\"");
				if (words[w].hasAttribute(ImWord.ITALICS_ATTRIBUTE))
					svg.append(" font-style=\"italic\"");
				svg.append(" fill=\"#" + "000000" + "\""); // default to black TODO use actual word color once we start tracking it
				svg.append(">");
				
				//	add word string
				svg.append(AnnotationUtils.escapeForXml(words[w].getString()));
				
				//	write end tag
				svg.append("</text>");
		}
		
		//	write end tag
		svg.append("</svg>");
		
		//	finally ...
		return svg;
	}
	
	private static String getHexRGB(Color color) {
		return ("" +
				getHex(color.getRed()) + 
				getHex(color.getGreen()) +
				getHex(color.getBlue()) +
				"");
	}
	
	private static final String getHex(int c) {
		int high = (c >>> 4) & 15;
		int low = c & 15;
		String hex = "";
		if (high < 10) hex += ("" + high);
		else hex += ("" + ((char) ('A' + (high - 10))));
		if (low < 10) hex += ("" + low);
		else hex += ("" +  ((char) ('A' + (low - 10))));
		return hex;
	}
	
	private static BoundingBox getCompositeBounds(Figure[] figures, Graphics[] graphics, ImWord[] words) {
		int left = Integer.MAX_VALUE;
		int right = 0;
		int top = Integer.MAX_VALUE;
		int bottom = 0;
		if (figures != null)
			for (int f = 0; f < figures.length; f++) {
				BoundingBox fBounds = figures[f].getBounds();
				left = Math.min(left, fBounds.left);
				right = Math.max(right, fBounds.right);
				top = Math.min(top, fBounds.top);
				bottom = Math.max(bottom, fBounds.bottom);
			}
		if (graphics != null)
			for (int g = 0; g < graphics.length; g++) {
				BoundingBox gBounds = graphics[g].getBounds();
				left = Math.min(left, gBounds.left);
				right = Math.max(right, gBounds.right);
				top = Math.min(top, gBounds.top);
				bottom = Math.max(bottom, gBounds.bottom);
			}
		if (words != null)
			for (int w = 0; w < words.length; w++) {
				BoundingBox gBounds = words[w].getBounds();
				left = Math.min(left, gBounds.left);
				right = Math.max(right, gBounds.right);
				top = Math.min(top, gBounds.top);
				bottom = Math.max(bottom, gBounds.bottom);
			}
		return (((left < right) && (top < bottom)) ? new BoundingBox(left, right, top, bottom) : null);
	}
	
	//	compute average brightness of text rendering area, and then use black or white, whichever contrasts better
	private static Color getTextColorAt(BufferedImage bi, BoundingBox bb) {
		BufferedImage bbBi = bi.getSubimage(bb.left, bb.top, bb.getWidth(), bb.getHeight());
		AnalysisImage aBbBi = Imaging.wrapImage(bbBi, null);
		byte[][] brightness = aBbBi.getBrightness();
		int brightnessSum = 0;
		for (int c = 0; c < brightness.length; c++) {
			for (int r = 0; r < brightness[c].length; r++)
				brightnessSum += brightness[c][r];
		}
		int avgBrightness = (brightnessSum / (bbBi.getWidth() * bbBi.getHeight()));
//		System.out.println("- average brightness is " + avgBrightness);
		return ((avgBrightness < 64) ? Color.WHITE : Color.BLACK);
	}
}