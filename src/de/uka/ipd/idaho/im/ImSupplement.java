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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Shape;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
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
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import javax.imageio.ImageIO;

import de.uka.ipd.idaho.easyIO.util.JsonParser;
import de.uka.ipd.idaho.easyIO.util.RandomByteSource;
import de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed;
import de.uka.ipd.idaho.gamta.util.imaging.BoundingBox;
import de.uka.ipd.idaho.im.util.ImObjectTransformer;

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
	String luid;
	String uuid;
	
	private final String id;
	private final String type;
	private final String mimeType;
	
	/** Constructor
	 * @param doc the document the supplement belongs to
	 * @param id the identifier of the supplement
	 * @param type the type of supplement
	 * @param mimeType the MIME type of the binary data
	 */
	protected ImSupplement(ImDocument doc, String id, String type, String mimeType) {
		this.doc = doc;
		if (!id.matches("[a-zA-Z0-9][a-zA-Z0-9\\-\\_\\=\\+\\#\\~\\;\\'\\.\\,\\@\\[\\]\\(\\)\\{\\}\\$\\§\\&\\%\\!]*"))
			throw new IllegalArgumentException("Invalid supplement ID '" + id + "'");
		this.id = id;
		if (!type.matches("[a-zA-Z][a-zA-Z0-9\\-\\_]*"))
			throw new IllegalArgumentException("Invalid object type '" + type + "'");
		this.type = type;
		if (!mimeType.toLowerCase().matches("[a-z]+\\/" + // type
				"[a-z]+(\\-[a-z]+)*(\\.[a-z]+(\\-[a-z]+)*)*" + // tree and subtype
				"(\\+[a-z]+(\\-[a-z]+)*)?" + // suffix
				"(\\;.*)*"))
			throw new IllegalArgumentException("Invalid MIME type '" + mimeType + "'");
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
	
	/**
	 * Notify document listeners that some implementation specific attribute
	 * has changed. This method is intended to make changes to sub class
	 * specific data fields tractable to listeners via the generic attribute
	 * handling methods. Sub classes that make use of this mechanism are
	 * strongly recommended to also overwrite the generic attribute handling
	 * methods and handle the names they issue notifications for via their
	 * respective data fields.
	 * @param name the name of the attribute
	 * @param oldValue the value before the change
	 */
	protected void notifyAttributeChanged(String name, Object oldValue) {
		if (this.doc != null)
			this.doc.notifyAttributeChanged(this, name, oldValue);
	}
	
	/**
	 * Obtain the identifier of the supplement, unique within the scope of the
	 * Image Markup document the supplement belongs to. The value returned by
	 * this method does not include any spaces or control characters and should
	 * start with a letter.
	 * @return the identifier of the supplement
	 */
	public String getId() {
		return this.id;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImObject#getLocalID()
	 */
	public String getLocalID() {
		return this.id;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImObject#getLocalUID()
	 */
	public String getLocalUID() {
		if (this.luid == null) {
			String id = this.id;
			while (id.length() < 10)
				id = (id + id);
			this.luid = (UuidHelper.asHex(this.id.hashCode(), 4) +
					UuidHelper.asHex(-1, 2) +
					String.valueOf(RandomByteSource.getHexCode(id.substring(0, 5).getBytes())) +
					String.valueOf(RandomByteSource.getHexCode(id.substring(id.length() - 5).getBytes())) +
//					//	CANNOT USE CALSS NAME, AS LUID WOULD CHANGE ON DOCUMENT LOADING WITH GENERIC SUPPLEMENTS
//					UuidHelper.asHex(this.getClass().getName().hashCode(), 4) +
//					String.valueOf(RandomByteSource.getHexCode(id.substring(0, 6).getBytes())) +
				"");
		}
		return this.luid;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImObject#getUUID()
	 */
	public String getUUID() {
		if ((this.uuid == null) && (this.doc != null))
			this.uuid = UuidHelper.getUUID(this, this.doc.docId);
		return this.uuid;
	}
	
	/**
	 * Create the name for a file to persist the supplement in, combining the
	 * supplement ID with the file extension indicated by the MIME type. This
	 * method ensures the file name is valid.
	 * @return the file name
	 */
	public String getFileName() {
		String dataType = this.mimeType;
		if (dataType.indexOf(';') != -1)
			dataType = dataType.substring(0, dataType.indexOf(';')); // cut any parameters
		if (dataType.indexOf('/') != -1)
			dataType = dataType.substring(dataType.indexOf('/') + "/".length()); // cut type, sub type makes for better file extension
		if (dataType.indexOf('+') != -1)
			dataType = dataType.substring(dataType.indexOf('+') + "+".length()); // use sub type suffix if present, usually holds actual data type
		return (this.id + "." + dataType);
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
	void setDocument(ImDocument doc) {
		if (this.doc == doc)
			return;
		this.doc = doc;
		this.uuid = null;
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
			super(doc, SOURCE_TYPE, SOURCE_TYPE, mimeType);
			if (doc != null)
				doc.addSupplement(this);
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
	 * bundle properties of <code>Scan</code> and <code>Illustration</code>,
	 * the common parent of <code>Figure</code> and <code>Graphics</code>.
	 * 
	 * @author sautter
	 */
	static abstract class Image extends ImSupplement {
		
		/** the name of the attribute holding the resolution of an image, namely 'dpi' */
		public static final String DPI_ATTRIBUTE = "dpi";
		
		/** the name of the attribute holding the page internal rendering order position of an image, namely 'ron' */
		public static final String RENDER_ORDER_NUMBER_ATTRIBUTE = "ron";
		
		int pageId = -1;
		int renderOrderNumber = -1;
		int dpi = -1;
		
		Image(ImDocument doc, String id, String type, String mimeType) {
			super(doc, id, type, mimeType);
		}
		
		Image(ImDocument doc, String id, String type, String mimeType, int pageId, int renderOrderNumber, int dpi) {
			super(doc, id, type, mimeType);
			this.pageId = pageId;
			this.renderOrderNumber = renderOrderNumber;
			this.dpi = dpi;
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.im.ImSupplement#getLocalUID()
		 */
		public abstract String getLocalUID();
		
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
				this.luid = null;
				this.uuid = null;
				return ("" + oldDpi);
			}
			else if (RENDER_ORDER_NUMBER_ATTRIBUTE.equals(name)) {
				int oldRon = this.renderOrderNumber;
				if (value == null)
					this.renderOrderNumber = -1;
				else this.renderOrderNumber = Integer.parseInt(value.toString());
				this.luid = null;
				this.uuid = null;
				return ("" + oldRon);
			}
			else return super.setAttribute(name, value);
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
		public Scan(ImDocument doc, String id, String mimeType) {
			super(doc, id, SCAN_TYPE, mimeType);
		}
		
		/** Constructor
		 * @param doc the document the scan belongs to
		 * @param mimeType the MIME type of the binary representation of the scan
		 * @param pageId the ID of the page the scan belongs to
		 * @param renderOrderNumber the page internal position at which the scan is rendered
		 * @param dpi the resolution of the scan
		 */
		public Scan(ImDocument doc, String mimeType, int pageId, int renderOrderNumber, int dpi) {
			super(doc, (SCAN_TYPE + "@" + pageId), SCAN_TYPE, mimeType, pageId, renderOrderNumber, dpi);
			if (doc != null)
				doc.addSupplement(this);
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.im.ImSupplement.Image#getLocalUID()
		 */
		public String getLocalUID() {
			if (this.luid == null)
				this.luid = UuidHelper.getLocalUID(this.getId(), this.pageId, this.renderOrderNumber, this.dpi, this.dpi, 0, 0);
			return this.luid;
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
		
		/**
		 * Transform a scan through an object transformer. The data of the
		 * returned scan is backed by argument scan and extracted only on first
		 * access. The scan constitutes a cut-out of the argument scan defined
		 * by the origin bounds of the argument transformer.
		 * @param scan the scan to transform
		 * @param transformer the transform to use
		 * @return the transformed scan
		 */
		public static Scan transformScan(final Scan scan, final ImObjectTransformer transformer) {
			return new Scan(null, scan.getMimeType(), transformer.toPageId, scan.getRenderOrderNumber(), scan.getDpi()) {
				private byte[] data = null;
				public InputStream getInputStream() throws IOException {
					if (this.data == null) {
						BufferedImage bi = ImageIO.read(scan.getInputStream()).getSubimage(transformer.fromBounds.left, transformer.fromBounds.top, transformer.fromBounds.getWidth(), transformer.fromBounds.getHeight());
						BufferedImage tBi = transformer.transformImage(bi);
						ByteArrayOutputStream baos = new ByteArrayOutputStream();
						ImageIO.write(tBi, "PNG", baos);
						this.data = baos.toByteArray();
					}
					return new ByteArrayInputStream(this.data);
				}
			};
		}
	}
	
	/**
	 * A supplement representing an illustration in a page of an Image Markup
	 * document. The MIME type of objects of this class can be any image bundle
	 * properties of <code>Figure</code> and <code>Graphics</code>.
	 * 
	 * @author sautter
	 */
	static abstract class Illustration extends Image {
		
		/** the name of the attribute holding the bounding box of the visible portion of the image on the document page (in page image resolution), namely 'clipBox' */
		public static final String CLIP_BOX_ATTRIBUTE = "clipBox";
		
		BoundingBox bounds = null;
		BoundingBox clipBounds = null;
		
		Illustration(ImDocument doc, String id, String type, String mimeType) {
			super(doc, id, type, mimeType);
		}
		
		Illustration(ImDocument doc, String type, String mimeType, int pageId, int renderOrderNumber, int dpi, BoundingBox bounds, BoundingBox clipBounds) {
			super(doc, ((type + "-" + renderOrderNumber + "@" + pageId + "." + bounds.toString())), type, mimeType, pageId, renderOrderNumber, dpi);
			this.bounds = bounds;
			this.clipBounds = ((clipBounds == null) ? this.bounds : clipBounds);
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.im.ImSupplement.Image#getLocalUID()
		 */
		public String getLocalUID() {
			if ((this.luid == null) && (this.bounds != null))
				this.luid = UuidHelper.getLocalUID(this.getId(), this.pageId, this.renderOrderNumber, this.bounds.left, this.bounds.top, this.bounds.right, this.bounds.bottom);
			return this.luid;
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
			else if (CLIP_BOX_ATTRIBUTE.equals(name))
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
				this.luid = null;
				this.uuid = null;
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
	public static abstract class Figure extends Illustration {
		
		/** the attribute marking a figure as an image mask, as a rendering hint */
		public static final String IMAGE_MASK_MARKER_ATTRIBUTE = "isImageMask";
		
		/** Constructor
		 * @param doc the document the figure belongs to
		 * @param mimeType the MIME type of the binary representation of the figure
		 */
		public Figure(ImDocument doc, String id, String mimeType) {
			super(doc, id, FIGURE_TYPE, mimeType);
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
			super(doc, FIGURE_TYPE, mimeType, pageId, renderOrderNumber, dpi, bounds, clipBounds);
			if (doc != null)
				doc.addSupplement(this);
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
			}
			catch (IOException ioe) { /* never gonna happen, but Java don't know */ }
			final byte[] figureBytes = baos.toByteArray();
			return new Figure(doc, "image/png", pageId, renderOrderNumber, dpi, bounds, clipBounds) {
				public InputStream getInputStream() throws IOException {
					return new ByteArrayInputStream(figureBytes);
				}
			};
		}
		
		/**
		 * Transform a figure through an object transformer. The data of the
		 * returned figure is backed by argument figure and extracted only on
		 * first access. The size of the figure image remain, potentially with
		 * swapped dimensions if the argument transformer performs a rotation.
		 * @param figure the figure to transform
		 * @param transformer the transform to use
		 * @return the transformed figure
		 */
		public static Figure transformFigure(final Figure figure, final ImObjectTransformer transformer) {
			BoundingBox tFigureBounds = transformer.transformBounds(figure.getBounds());
			return new Figure(null, figure.getMimeType(), transformer.toPageId, figure.getRenderOrderNumber(), figure.getDpi(), tFigureBounds) {
				private byte[] data = null;
				public InputStream getInputStream() throws IOException {
					if (transformer.turnDegrees == 0)
						return figure.getInputStream();
					if (this.data == null) {
						BufferedImage bi = ImageIO.read(figure.getInputStream());
						BufferedImage tBi = transformer.transformImage(bi);
						ByteArrayOutputStream baos = new ByteArrayOutputStream();
						ImageIO.write(tBi, "PNG", baos);
						this.data = baos.toByteArray();
					}
					return new ByteArrayInputStream(this.data);
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
	public static abstract class Graphics extends Illustration {
		
		/** the attribute for marking a graphics object as part of page layout artwork, namely 'pageDecoration' */
		public static final String PAGE_DECORATION_ATTRIBUTE = "pageDecoration";
		
		/** the MIME type vector based graphics objects are stored in, namely 'application/json' */
		public static final String MIME_TYPE = "application/json";
		
		/** the resolution of the unscaled vector based graphics, namely 72 DPI */
		public static final int RESOLUTION = 72;
		
		private boolean modifiable = false;
		ArrayList paths = null; // needs to be accessible from anonymous sub class used in createGraphics() ...
		
		/** Constructor
		 * @param doc the document the graphics belongs to
		 */
		public Graphics(ImDocument doc, String id) {
			super(doc, id, GRAPHICS_TYPE, MIME_TYPE);
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
			super(doc, GRAPHICS_TYPE, MIME_TYPE, pageId, renderOrderNumber, RESOLUTION, bounds, clipBounds);
			if (modifiable) {
				this.modifiable = true;
				this.paths = new ArrayList();
			}
			if (doc != null)
				doc.addSupplement(this);
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
			
			/** the bounding box of the path (in page image resolution) */
			public final BoundingBox bounds;
			
			/** the bounding box of the visible portion of the path (in page image resolution) */
			public final BoundingBox clipBounds;
			
			/** the page internal position at which the path is rendered */
			public final int renderOrderNumber;
			
			private Color strokeColor = null;
			private float lineWidth = Float.NaN;
			private byte lineCapStyle = ((byte) -1);
			private byte lineJointStyle = ((byte) -1);
			private float miterLimit = Float.NaN;
			private List dashPattern = null;
			private float dashPatternPhase = Float.NaN;
			
			private Color fillColor = null;
			private boolean fillEvenOdd = false;
			
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
			 * @param fillEvenOdd fill using the even-odd rule? (if the path is filled)
			 */
			public Path(BoundingBox bounds, BoundingBox clipBounds, int renderOrderNumber, Color strokeColor, float lineWidth, byte lineCapStyle, byte lineJointStyle, float miterLimit, List dashPattern, float dashPatternPhase, Color fillColor, boolean fillEvenOdd) {
				this(bounds, clipBounds, renderOrderNumber);
				this.strokeColor = strokeColor;
				this.lineWidth = lineWidth;
				this.lineCapStyle = lineCapStyle;
				this.lineJointStyle = lineJointStyle;
				this.miterLimit = miterLimit;
				this.dashPattern = dashPattern;
				this.dashPatternPhase = dashPatternPhase;
				this.fillColor = fillColor;
				this.fillEvenOdd = fillEvenOdd;
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
			 * Fill this path using the even-odd rule? This method returning
			 * false indicates to use the zero-winding-number rule instead.
			 * @return true to indicate using the even-odd rule for filling
			 */
			public boolean isFilledEvenOdd() {
				return this.fillEvenOdd;
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
					if (this.fillEvenOdd) {
						out.write("\"fillEvenOdd\": ".getBytes("UTF-8"));
						out.write("true".getBytes("UTF-8"));
						out.write((int) ',');
					}
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
					path.dashPattern = new ArrayList();
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
				if (fillColor != null) {
					path.fillColor = decodeColor(fillColor);
					Boolean fillEvenOdd = JsonParser.getBoolean(data, "fillEvenOdd");
					path.fillEvenOdd = ((fillEvenOdd != null) && fillEvenOdd.booleanValue());
				}
				
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
		
		/**
		 * Transform a graphics object through an object transformer. The data
		 * of the returned graphics object is backed by argument graphics object
		 * and generated only on first access. The size of the graphics object
		 * remains unchanged, potentially with swapped dimensions if the argument
		 * transformer performs a rotation.
		 * @param graphics the graphics to transform
		 * @param transformer the transform to use
		 * @return the transformed graphics
		 */
		public static Graphics transformGraphics(final Graphics graphics, final ImObjectTransformer transformer) {
			BoundingBox tGraphicsBounds = transformer.transformBounds(graphics.getBounds());
			BoundingBox tGraphicsClipBounds = transformer.transformBounds(graphics.getClipBounds());
			ImSupplement.Graphics tGraphics = new ImSupplement.Graphics(null, transformer.toPageId, graphics.getRenderOrderNumber(), tGraphicsBounds, tGraphicsClipBounds) {
				private ImSupplement.Graphics ioHelperGraphics;
				public InputStream getInputStream() throws IOException {
					if (this.ioHelperGraphics == null) {
						this.ioHelperGraphics = ImSupplement.Graphics.createGraphics(null, this.getPageId(), this.getRenderOrderNumber(), this.getBounds(), this.getClipBounds());
						Path[] paths = this.getPaths(); // performs lazy initialization
						for (int p = 0; p < paths.length; p++)
							this.ioHelperGraphics.addPath(paths[p]);
					}
					return this.ioHelperGraphics.getInputStream();
				}
				private Path[] tPaths = null;
				public Path[] getPaths() {
					if (this.tPaths == null) {
						Path[] paths = graphics.getPaths();
						this.tPaths = new Path[paths.length];
						for (int p = 0; p < paths.length; p++) {
							BoundingBox tPathBounds = transformer.transformBounds(paths[p].bounds);
							BoundingBox tPathClipBounds = transformer.transformBounds(paths[p].clipBounds);
							BasicStroke pathStroke = paths[p].getStroke();
							if (pathStroke == null)
								this.tPaths[p] = new Path(tPathBounds, tPathClipBounds, paths[p].renderOrderNumber, paths[p].getStrokeColor(), 1, ((byte) 0), ((byte) 0), 0, null, 0, paths[p].getFillColor(), paths[p].isFilledEvenOdd());
							else {
								ArrayList pathDashPattern = null;
								if (pathStroke.getDashArray() != null) {
									float[] strokeDashPattern = pathStroke.getDashArray();
									pathDashPattern = new ArrayList();
									for (int s = 0; s < strokeDashPattern.length; s++)
										pathDashPattern.add(new Float(strokeDashPattern[s]));
								}
								this.tPaths[p] = new Path(tPathBounds, tPathClipBounds, paths[p].renderOrderNumber, paths[p].getStrokeColor(), pathStroke.getLineWidth(), ((byte) pathStroke.getEndCap()), ((byte) pathStroke.getLineJoin()), pathStroke.getMiterLimit(), pathDashPattern, pathStroke.getDashPhase(), paths[p].getFillColor(), paths[p].isFilledEvenOdd());
							}
							SubPath[] subPaths = paths[p].getSubPaths();
							for (int sp = 0; sp < subPaths.length; sp++) {
								BoundingBox tSubPathBounds = transformer.transformBounds(subPaths[sp].bounds);
								SubPath tSubPath = new SubPath(tSubPathBounds);
								this.tPaths[p].addSubPath(tSubPath);
								Shape[] shapes = subPaths[sp].getShapes();
								for (int s = 0; s < shapes.length; s++) {
									if (shapes[s] instanceof CubicCurve2D) {
										CubicCurve2D curve = ((CubicCurve2D) shapes[s]);
										Point2D.Float p1 = transformer.transformPoint(curve.getP1());
										Point2D.Float cp1 = transformer.transformPoint(curve.getCtrlP1());
										Point2D.Float cp2 = transformer.transformPoint(curve.getCtrlP2());
										Point2D.Float p2 = transformer.transformPoint(curve.getP2());
										tSubPath.addCurve(new CubicCurve2D.Float(p1.x, p1.y, cp1.x, cp1.y, cp2.x, cp2.y, p2.x, p2.y));
									}
									else if (shapes[s] instanceof Line2D) {
										Line2D line = ((Line2D) shapes[s]);
										Point2D.Float p1 = transformer.transformPoint(line.getP1());
										Point2D.Float p2 = transformer.transformPoint(line.getP2());
										tSubPath.addLine(new Line2D.Float(p1.x, p1.y, p2.x, p2.y));
									}
								}
							}
						}
					}
					return this.tPaths;
				}
			};
			if (graphics.hasAttribute(ImSupplement.Graphics.PAGE_DECORATION_ATTRIBUTE))
				tGraphics.setAttribute(ImSupplement.Graphics.PAGE_DECORATION_ATTRIBUTE);
			return tGraphics;
		}
	}
	
	/**
	 * From an array of supplements, filter out the <code>Figure</code> ones
	 * lying in a given bounding box.
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
	 * lying in a given bounding box.
	 * @param supplements the supplements to choose from
	 * @param box the bounding box whose contained graphics to get
	 * @return an array holding the graphics
	 */
	public static Graphics[] getGraphicsIn(ImSupplement[] supplements, BoundingBox box) {
		ArrayList containedGraphics = new ArrayList(1);
		for (int s = 0; s < supplements.length; s++) {
			if ((supplements[s] instanceof Graphics) && ((Graphics) supplements[s]).getBounds().liesIn(box, true))
				containedGraphics.add(supplements[s]);
		}
		return ((Graphics[]) containedGraphics.toArray(new Graphics[containedGraphics.size()]));
	}
	
	/**
	 * From an array of supplements, filter out the <code>Graphics</code> ones
	 * that overlap with a given bounding box.
	 * @param supplements the supplements to choose from
	 * @param box the bounding box whose overlapping graphics to get
	 * @return an array holding the graphics
	 */
	public static Graphics[] getGraphicsAt(ImSupplement[] supplements, BoundingBox box) {
		ArrayList containingGraphics = new ArrayList(1);
		for (int s = 0; s < supplements.length; s++) {
			if ((supplements[s] instanceof Graphics) && ((Graphics) supplements[s]).getBounds().includes(box, true))
				containingGraphics.add(supplements[s]);
		}
		return ((Graphics[]) containingGraphics.toArray(new Graphics[containingGraphics.size()]));
	}
	
	/**
	 * From an array of supplements, filter out the (first) one that is a
	 * <code>Scan</code>. If the argument array does not contain a
	 * <code>Scan</code>, this method returns null.
	 * @param supplements the supplements to choose from
	 * @return the scan
	 */
	public static Scan getScan(ImSupplement[] supplements) {
		for (int s = 0; s < supplements.length; s++) {
			if (supplements[s] instanceof Scan)
				return ((Scan) supplements[s]);
		}
		return null;
	}
}