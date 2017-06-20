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
package de.uka.ipd.idaho.im.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import de.uka.ipd.idaho.gamta.util.imaging.BoundingBox;
import de.uka.ipd.idaho.im.ImDocument;
import de.uka.ipd.idaho.im.ImSupplement;

/**
 * Disk caching facility for <code>ImSupplement</code>s. Instances of this
 * class cache the built-in supplements <code>Source</code>,
 * <code>Scan</code>, and <code>Figure</code> in a folder on background storage
 * when their combined size exceeds a defined threshold. This helps reduce the
 * memory footprint of large <code>ImDocument</code>s during creation. Below
 * the defined threshold, this class does not modify any
 * <code>ImSupplement</code>s and thus preserves the high performance of
 * in-memory storage for smaller <code>ImDocument</code>s. <code>Graphics</code>
 * supplements are never cached on disk, for two reasons: (a) Their JSON based
 * serialization tends to be relatively small in comparison to binary images,
 * outright negligible, and (b) because of the way <code>Graphics</code> are
 * populated only after creation and addition to an <code>ImDocument</code>,
 * disk caching them on creation would dodge the addition of content and thus
 * result in data loss.<br/>
 * To use instances of this class, the <code>addSupplement()</code> method of
 * the <code>ImDocument</code> has to be overwritten to loop any added
 * <code>ImSupplement</code> by this class before adding it to its own data
 * structures, and add whatever <code>cacheSupplement()</code> returns to the
 * latter instead of the original argument. Further, the
 * <code>removeSupplement()</code> method should be overwritten as well to
 * notify any instance of this class that a supplement has been discarded. This
 * facilitates cleaning up any persisted data.<br/>
 * An <code>ImDocument</code> using an instance of this class would have
 * supplement handling facilities like this:<br/>
 * <code><pre>
 *  class MyCachingDocument extends ImDocument {
 *  	private ImSupplementCache supplementCache;
 *  	public ImDocument(String docId, File cacheFolder, int maxInMemoryCacheBytes) {
 *  		super(docId);
 *  		this.supplementCache = new ImSupplementCache(this, cacheFolder, maxInMemoryCacheBytes)
 *  	}
 *  	public ImSupplement addSupplement(ImSupplement ims) {
 *  		ims = this.supplementCache.cacheSupplement(ims); // caches supplement on disk if threshold exceeded
 *  		return super.addSupplement(ims); // add (possibly cached) supplement to super class
 *  	}
 *  	public void removeSupplement(ImSupplement ims) {
 *  		this.supplementCache.deleteSupplement(ims); // delete supplement from disk cache (if cached on disk at all)
 *  		super.removeSupplement(ims); // remove supplement from super class
 *  	}
 *  }
 * </code></pre>
 * 
 * @author sautter
 */
public class ImSupplementCache {
	private ImDocument doc;
	private File supplementFolder;
	private final int maxInMemoryCacheBytes;
	private long inMemoryCacheBytes = 0;
	
	/** Constructor
	 * @param doc the document the supplement cache belongs to
	 * @param supplementFolder the folder to store supplements in (created on first use if it doesn't already exist)
	 * @param maxInMemoryCacheBytes the maximum number of bytes to keep in memory before switching to disk caching
	 */
	public ImSupplementCache(ImDocument doc, File supplementFolder, int maxInMemoryCacheBytes) {
		this.doc = doc;
		this.supplementFolder = supplementFolder;
		this.maxInMemoryCacheBytes = maxInMemoryCacheBytes;
	}
	
	/**
	 * Cache a supplement on disk if threshold exceeded, and return the cached
	 * version.
	 * @param ims the supplement to add
	 * @return the (potentially cached) supplement
	 */
	public ImSupplement cacheSupplement(ImSupplement ims) {
		
		//	no use caching twice
		if (ims instanceof CachedSupplement)
			return ims;
		
		//	store known type supplements on disk if there are too many or too large
		if ((ims instanceof ImSupplement.Figure)/* || (ims instanceof ImSupplement.Graphics)*/ || (ims instanceof ImSupplement.Scan) || (ims instanceof ImSupplement.Source)) try {
			
			//	threshold already exceeded, disc cache right away
			if (this.inMemoryCacheBytes > this.maxInMemoryCacheBytes)
				ims = this.createDiskSupplement(ims, null);
			
			//	still below threshold, check source
			else {
				InputStream sis = ims.getInputStream();
				
				//	this one resides in memory, count it
				if (sis instanceof ByteArrayInputStream)
					this.inMemoryCacheBytes += sis.available();
				
				//	threshold just exceeded
				if (this.inMemoryCacheBytes > this.maxInMemoryCacheBytes) {
					
					//	create cache folder only now that we know we need it
					this.supplementFolder.mkdirs();
					
					//	disk cache all existing supplements
					ImSupplement[] imss = this.doc.getSupplements();
					for (int s = 0; s < imss.length; s++) {
						if (imss[s] instanceof CachedSupplement)
							continue; // no use caching twice
						if ((imss[s] instanceof ImSupplement.Figure)/* || (imss[s] instanceof ImSupplement.Graphics)*/ || (imss[s] instanceof ImSupplement.Scan) || (imss[s] instanceof ImSupplement.Scan))
							this.doc.addSupplement(this.createDiskSupplement(imss[s], null));
					}
					
					//	disk cache argument supplement
					ims = this.createDiskSupplement(ims, sis);
				}
			}
		}
		catch (IOException ioe) {
			System.out.println("Error caching supplement '" + ims.getId() + "': " + ioe.getMessage());
			ioe.printStackTrace(System.out);
		}
		
		//	return (possibly modified) supplement
		return ims;
	}
	
	private ImSupplement createDiskSupplement(ImSupplement ims, InputStream sis) throws IOException {
		
		//	get input stream if not already done
		if (sis == null)
			sis = ims.getInputStream();
		
		//	this one's not in memory, close input stream and we're done
		if (!(sis instanceof ByteArrayInputStream)) {
			sis.close();
			return ims;
		}
		
		//	get file name and extension
		String sDataName = ims.getId().replaceAll("[^a-zA-Z0-9]", "_");
		String sDataType = ims.getMimeType();
		if (sDataType.indexOf('/') != -1)
			sDataType = sDataType.substring(sDataType.indexOf('/') + "/".length());
		
		//	create cache file
		File sFile = new File(this.supplementFolder, (this.doc.docId + "." + sDataName + "." + sDataType));
		
		//	store supplement in file (if not done previously)
		if (!sFile.exists()) {
//			OutputStream sos = new BufferedOutputStream(new FileOutputStream(sFile));
			File sFileCaching = new File(this.supplementFolder, (this.doc.docId + "." + sDataName + "." + sDataType + ".caching"));
			OutputStream sos = new BufferedOutputStream(new FileOutputStream(sFileCaching));
			byte[] sBuffer = new byte[1024];
			for (int r; (r = sis.read(sBuffer, 0, sBuffer.length)) != -1;)
				sos.write(sBuffer, 0, r);
			sos.flush();
			sos.close();
			sFileCaching.renameTo(sFile);
		}
		
		//	replace supplement with disk based one
		if (ims instanceof ImSupplement.Figure)
			return new CachedFigure(this.doc, ims.getMimeType(), ((ImSupplement.Figure) ims).getPageId(), ((ImSupplement.Figure) ims).getRenderOrderNumber(), ((ImSupplement.Figure) ims).getDpi(), ((ImSupplement.Figure) ims).getBounds(), sFile);
//		else if (ims instanceof ImSupplement.Graphics)
//			return new CachedGraphics(this.doc, ((ImSupplement.Graphics) ims).getPageId(), ((ImSupplement.Graphics) ims).getRenderOrderNumber(), ((ImSupplement.Graphics) ims).getBounds(), sFile);
		else if (ims instanceof ImSupplement.Scan)
			return new CachedScan(this.doc, ims.getMimeType(), ((ImSupplement.Scan) ims).getPageId(), ((ImSupplement.Scan) ims).getRenderOrderNumber(), ((ImSupplement.Scan) ims).getDpi(), sFile);
		else if (ims instanceof ImSupplement.Source)
			return new CachedSource(this.doc, ims.getMimeType(), sFile);
		else return ims; // never gonna happen, but Java don't know
	}
	
	/**
	 * Delete a supplement from the cache. If the argument supplement is cached
	 * on disk, this method deletes the cache file; otherwise, it does nothing.
	 * @param ims the supplement to delete
	 */
	public void deleteSupplement(ImSupplement ims) {
		if (ims instanceof CachedSupplement)
			((CachedSupplement) ims).getCacheFile().delete();
	}
	
	private static interface CachedSupplement {
		public abstract File getCacheFile();
	}
	
	private static class CachedSource extends ImSupplement.Source implements CachedSupplement {
		private File cacheFile;
		CachedSource(ImDocument doc, String mimeType, File cacheFile) {
			super(doc, mimeType);
			this.cacheFile = cacheFile;
		}
		public File getCacheFile() {
			return this.cacheFile;
		}
		public InputStream getInputStream() throws IOException {
			return new BufferedInputStream(new FileInputStream(this.cacheFile));
		}
	}
	
	private static class CachedScan extends ImSupplement.Scan implements CachedSupplement {
		private File cacheFile;
		CachedScan(ImDocument doc, String mimeType, int pageId, int renderOrderNumber, int dpi, File cacheFile) {
			super(doc, mimeType, pageId, renderOrderNumber, dpi);
			this.cacheFile = cacheFile;
		}
		public File getCacheFile() {
			return this.cacheFile;
		}
		public InputStream getInputStream() throws IOException {
			return new BufferedInputStream(new FileInputStream(this.cacheFile));
		}
	}
	
	private static class CachedFigure extends ImSupplement.Figure implements CachedSupplement {
		private File cacheFile;
		CachedFigure(ImDocument doc, String mimeType, int pageId, int renderOrderNumber, int dpi, BoundingBox bounds, File cacheFile) {
			super(doc, mimeType, pageId, renderOrderNumber, dpi, bounds);
			this.cacheFile = cacheFile;
		}
		public File getCacheFile() {
			return this.cacheFile;
		}
		public InputStream getInputStream() throws IOException {
			return new BufferedInputStream(new FileInputStream(this.cacheFile));
		}
	}
//	
//	private static class CachedGraphics extends ImSupplement.Graphics implements CachedSupplement {
//		private File cacheFile;
//		CachedGraphics(ImDocument doc, int pageId, int renderOrderNumber, BoundingBox bounds, File cacheFile) {
//			super(doc, pageId, renderOrderNumber, bounds);
//			this.cacheFile = cacheFile;
//		}
//		public File getCacheFile() {
//			return this.cacheFile;
//		}
//		public InputStream getInputStream() throws IOException {
//			return new BufferedInputStream(new FileInputStream(this.cacheFile));
//		}
//	}
}