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
package de.uka.ipd.idaho.im.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.SequenceInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import de.uka.ipd.idaho.easyIO.streams.DataHashInputStream;
import de.uka.ipd.idaho.easyIO.streams.DataHashOutputStream;
import de.uka.ipd.idaho.easyIO.streams.PeekInputStream;
import de.uka.ipd.idaho.easyIO.util.HashUtils.MD5;
import de.uka.ipd.idaho.gamta.Attributed;
import de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed;
import de.uka.ipd.idaho.gamta.util.CountingSet;
import de.uka.ipd.idaho.gamta.util.ProgressMonitor;
import de.uka.ipd.idaho.gamta.util.ProgressMonitor.CascadingProgressMonitor;
import de.uka.ipd.idaho.gamta.util.imaging.BoundingBox;
import de.uka.ipd.idaho.gamta.util.imaging.ImagingConstants;
import de.uka.ipd.idaho.gamta.util.imaging.PageImage;
import de.uka.ipd.idaho.gamta.util.imaging.PageImageInputStream;
import de.uka.ipd.idaho.gamta.util.imaging.PageImageSource;
import de.uka.ipd.idaho.gamta.util.imaging.PageImageStore.AbstractPageImageStore;
import de.uka.ipd.idaho.gamta.util.swing.ProgressMonitorDialog;
import de.uka.ipd.idaho.im.ImAnnotation;
import de.uka.ipd.idaho.im.ImDocument;
import de.uka.ipd.idaho.im.ImFont;
import de.uka.ipd.idaho.im.ImObject;
import de.uka.ipd.idaho.im.ImPage;
import de.uka.ipd.idaho.im.ImRegion;
import de.uka.ipd.idaho.im.ImSupplement;
import de.uka.ipd.idaho.im.ImWord;
import de.uka.ipd.idaho.im.util.ImDocumentData.DataBackedImDocument;
import de.uka.ipd.idaho.im.util.ImDocumentData.FolderImDocumentData;
import de.uka.ipd.idaho.im.util.ImDocumentData.ImDocumentEntry;
import de.uka.ipd.idaho.stringUtils.StringVector;
import de.uka.ipd.idaho.stringUtils.csvHandler.StringRelation;
import de.uka.ipd.idaho.stringUtils.csvHandler.StringTupel;

/**
 * IO utility for Image Markup documents. However, in favor of flexibility,
 * this class works on mainly document data objects. This is to abstract from
 * the exact location the file is loaded from or written to. All methods that
 * work on files or input or output streams are only wrappers.<br>
 * When storing an Image Markup document, this class writes (at least) the
 * following entries to a document data object:
 * <ul>
 * <li><b>document.csv</b>: a CSV file containing the document ID and document
 * attributes.</li>
 * <li><b>pages.csv</b>: a CSV file containing information about individual
 * pages, namely the page ID, the page bounding box, and additional page
 * attributes.</li>
 * <li><b>words.csv</b>: a CSV file containing individual layout words, their
 * bounding boxes, assigned string value, IDs of their predecessor and
 * successor, type of connection to their successor (if any), as well as
 * additional attributes.</li>
 * <li><b>regions.csv</b>: a CSV file containing individual layout regions,
 * namely the region type, the ID of the page the region lies on, the region
 * bounding box, as well as additional attributes.</li>
 * <li><b>annotations.csv</b>: a CSV file containing the semantic markup of the
 * document, namely logical structure and detail annotations. They are described
 * by the IDs of their first and last word, their type, and additional attributes.
 * </li>
 * <li><b>pageImages.csv</b>: a CSV file containing meta data for the page
 * images, namely the page ID, image dimensions, resolution in DPI, and the
 * widths of any cut-off white margins.</li>
 * <li><b>&lt;pageId&gt;.png</b>: images of the individual pages, one per page.
 * </li>
 * </ul>
 * In addition, it may also write the following optional entries:
 * <ul>
 * <li><b>fonts.csv</b>: a CSV file containing custom fonts if the Image Markup
 * file was generated from a source supporting such. Each character of each
 * font is described with the font name and style, its character ID, its Unicode
 * transcription, and a hex-serialized representation its glyph.</li>
 * <li><b>supplements.csv</b>: a CSV file containing meta data of binary
 * supplements to the document. Each supplement is described with its name,
 * type, MIME type, and file name, plus additional type specific attributes.</li>
 * <li><b>&lt;supplementName&gt;.&lt;supplementType&gt;</b>: the data of binary
 * supplements, pointed to by the supplement names listed in <i>supplements.csv</i>.
 * </li>
 * </ul>
 * Likewise, when loading an Image Markup document, this class expects at least
 * the entries to be present that it always writes on storing.
 * 
 * @author sautter
 */
public class ImDocumentIO implements ImagingConstants {
	
	/*
TODO General thoughts for IMFv2:
- add observance of flags to GGI desktop batch ...
- ... as well as to batch cache part of loading routine ...
  ==> might actually want to have IM document IO read entries in that routine to stay transparent ...
  ==> ... and limit adjustment of GGI proper to also accepting 'entries.tsv' in batch cache loading filter
- ... and add individual parts of IMFv2 storage flags to installed GGI desktop batch config file ...
- ... assembling overall flags in GGI desktop batch on startup
  ==> IN FACT, put those flag contributions in installed part of GGI family app config ...
  ==> ... assemble them in GGI core ...
  ==> ... and use in both GGI desktop batch and GGI proper (for storing IMF or IMD after decoding PDF)
  ==> also add respective explanations to 'GgImagine.ggAppConfig.README.txt'

TODO More thoughts for parameter/storage flag handling in IMFv2 aware facilities:
- MAKE DAMN SURE to test IMFv2 enabled 'ImageMarkup.jar' still with old IMS document IO in 'Default.imagine' in local 'client' installation before putting out respective GGI update ...
- ... as we don't have any other way of making sure updated IMS document IO, etc. come onto user installations with IMFv2 enabled GGI core
  ==> should be easy enough to test whole thing against local IMS with nothing but old IMS document IO (plus dependencies) in local master part of local 'client' installation
	 */
	
	/**
	 * Receiver of load verification results for the entries of Image Markup
	 * documents. It is up to implementations how they go about failures. Load
	 * verification may only apply to the core tabular document entries, i.e.,
	 * the main document data proper, pages, page image metadata, fonts, words,
	 * regions, annotations, and supplement metadata, but not to page images
	 * proper, nor the binary data representing the contents of supplements.
	 * 
	 * @author sautter
	 */
	public static interface EntryVerificationLogger {
		
		/**
		 * Receive notification that an Image Markup document entry was
		 * verified successfully, i.e., that its indicated size and hash match
		 * the size and hash observed on reading.
		 * @param entry the entry that was verified
		 */
		public abstract void entryVerificationSuccessful(ImDocumentEntry entry);
		
		/**
		 * Receive notification that an Image Markup document entry failed to
		 * verify, i.e., that its indicated size and/or hash does not match the
		 * size and/or hash observed on reading, respectively.
		 * @param entry the entry that failed to verify
		 * @param observedSize the observed size (in bytes) of the binary data
		 *        associated with the argument document entry
		 * @param observedHash the observed hash of the binary data associated
		 *        with the argument document entry
		 * @throws IOException if the implementation chooses to throw one
		 */
		public abstract void entryVerificationFailed(ImDocumentEntry entry, int observedSize, String observedHash) throws IOException;
		
		/**
		 * Receive notification that an Image Markup document entry that the
		 * entry lists indicates to exist does not have any underlying data.
		 * @param entry the entry whose data is missing
		 * @throws IOException if the implementation chooses to throw one
		 */
		public abstract void entryDataMissing(ImDocumentEntry entry) throws IOException;
		
		/** implementation that logs all verification results to <code>System.out</code> and <code>System.err</code> */
		public static EntryVerificationLogger dummy = new EntryVerificationLogger() {
			public void entryVerificationSuccessful(ImDocumentEntry entry) {
				System.out.println("Entry '" + entry.name + "' verified successfully");
			}
			public void entryVerificationFailed(ImDocumentEntry entry, int observedSize, String observedHash) throws IOException {
				System.err.println("Entry '" + entry.name + "' failed to verify:");
				System.err.println(" - size is " + entry.size + " bytes indicated vs. " + observedSize + " bytes observed");
				System.err.println(" - hash is " + entry.dataHash + " indicated vs. " + observedHash + " observed");
			}
			public void entryDataMissing(ImDocumentEntry entry) throws IOException {
				System.err.println("Data for entry '" + entry.name + "' is missing");
			}
		};
		
		/** implementation that logs verification failures to <code>System.err</code>, but quietly accepts successful verifications */
		public static EntryVerificationLogger quiet = new EntryVerificationLogger() {
			public void entryVerificationSuccessful(ImDocumentEntry entry) {}
			public void entryVerificationFailed(ImDocumentEntry entry, int observedSize, String observedHash) throws IOException {
				System.err.println("Entry '" + entry.name + "' failed to verify:");
				System.err.println(" - size is " + entry.size + " bytes indicated vs. " + observedSize + " bytes observed");
				System.err.println(" - hash is " + entry.dataHash + " indicated vs. " + observedHash + " observed");
			}
			public void entryDataMissing(ImDocumentEntry entry) throws IOException {
				System.err.println("Data for entry '" + entry.name + "' is missing");
			}
		};
		
		/** implementation that does not produce any output at all */
		public static EntryVerificationLogger silent = new EntryVerificationLogger() {
			public void entryVerificationSuccessful(ImDocumentEntry entry) {}
			public void entryVerificationFailed(ImDocumentEntry entry, int observedSize, String observedHash) throws IOException {}
			public void entryDataMissing(ImDocumentEntry entry) throws IOException {}
		};
	}
	
	/**
	 * Load an Image Markup document. If the argument file is an actual file,
	 * this method assumes it to be a zipped-up Image Markup File. If the file
	 * is a folder, however, this method assumes it to be an already un-zipped
	 * Image Markup File.
	 * @param file the file to load from
	 * @return an Image Markup document representing the content of the
	 *            argument stream.
	 * @throws IOException
	 */
	public static ImDocument loadDocument(File file) throws IOException {
		return loadDocument(file, null, null);
	}
	
	/**
	 * Load an Image Markup document. If the argument file is an actual file,
	 * this method assumes it to be a zipped-up Image Markup File. If the file
	 * is a folder, however, this method assumes it to be an already un-zipped
	 * Image Markup File.
	 * @param in the input stream to load from
	 * @param pm a progress monitor to observe the loading process
	 * @return an Image Markup document representing the content of the
	 *            argument stream.
	 * @throws IOException
	 */
	public static ImDocument loadDocument(File file, ProgressMonitor pm) throws IOException {
		return loadDocument(file, null, pm);
	}
	
	/**
	 * Load an Image Markup document. If the argument file is an actual file,
	 * this method assumes it to be a zipped-up Image Markup File. If the file
	 * is a folder, however, this method assumes it to be an already un-zipped
	 * Image Markup File.
	 * @param file the file to load from
	 * @param evl a logger to handle load verification results
	 * @return an Image Markup document representing the content of the
	 *            argument stream.
	 * @throws IOException
	 */
	public static ImDocument loadDocument(File file, EntryVerificationLogger evl) throws IOException {
		return loadDocument(file, evl, null);
	}
	
	/**
	 * Load an Image Markup document. If the argument file is an actual file,
	 * this method assumes it to be a zipped-up Image Markup File. If the file
	 * is a folder, however, this method assumes it to be an already un-zipped
	 * Image Markup File.
	 * @param in the input stream to load from
	 * @param evl a logger to handle load verification results
	 * @param pm a progress monitor to observe the loading process
	 * @return an Image Markup document representing the content of the
	 *            argument stream.
	 * @throws IOException
	 */
	public static ImDocument loadDocument(File file, EntryVerificationLogger evl, ProgressMonitor pm) throws IOException {
		
		//	assume folder to be un-zipped IMF
		if (file.isDirectory())
			return loadDocument(new FolderImDocumentData(file, ((String) null)), evl, pm);
		
		//	assume file to be zipped-up IMF
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(file);
			ImDocument doc = loadDocument(fis, null, evl, pm, file.length());
			fis.close();
			return doc;
		}
		finally {
			if (fis != null)
				fis.close();
		}
	}
	
	/**
	 * Load an Image Markup document.
	 * @param in the input stream to load from
	 * @return an Image Markup document representing the content of the
	 *            argument stream.
	 * @throws IOException
	 */
	public static ImDocument loadDocument(InputStream in) throws IOException {
		return loadDocument(in, null, null, null, -1);
	}
	
	/**
	 * Load an Image Markup document.
	 * @param in the input stream to load from
	 * @param pm a progress monitor to observe the loading process
	 * @return an Image Markup document representing the content of the
	 *            argument stream.
	 * @throws IOException
	 */
	public static ImDocument loadDocument(InputStream in, ProgressMonitor pm) throws IOException {
		return loadDocument(in, null, null, pm, -1);
	}
	
	/**
	 * Load an Image Markup document.
	 * @param in the input stream to load from
	 * @param pm a progress monitor to observe the loading process
	 * @param inLength the number of bytes to expect from the argument input stream
	 * @return an Image Markup document representing the content of the
	 *            argument stream.
	 * @throws IOException
	 */
	public static ImDocument loadDocument(InputStream in, ProgressMonitor pm, long inLength) throws IOException {
		return loadDocument(in, null, null, pm, inLength);
	}
	
	/**
	 * Load an Image Markup document. If the argument cache folder is null,
	 * page images will be cached in memory. For large documents, this can
	 * cause high memory consumption. The argument input stream is not closed.
	 * @param in the input stream to load from
	 * @param cacheFolder the folder to use for caching
	 * @return an Image Markup document representing the content of the
	 *            argument stream.
	 * @throws IOException
	 */
	public static ImDocument loadDocument(InputStream in, File cacheFolder) throws IOException {
		return loadDocument(in, cacheFolder, null, null, -1);
	}
	
	/**
	 * Load an Image Markup document. If the argument cache folder is null,
	 * page images will be cached in memory. For large documents, this can
	 * cause high memory consumption. The argument input stream is not closed.
	 * @param in the input stream to load from
	 * @param cacheFolder the folder to use for caching
	 * @param pm a progress monitor to observe the loading process
	 * @return an Image Markup document representing the content of the
	 *            argument stream.
	 * @throws IOException
	 */
	public static ImDocument loadDocument(InputStream in, File cacheFolder, ProgressMonitor pm) throws IOException {
		return loadDocument(in, cacheFolder, null, pm, -1);
	}
	
	/**
	 * Load an Image Markup document. If the argument cache folder is null,
	 * page images will be cached in memory. For large documents, this can
	 * cause high memory consumption. The argument input stream is not closed.
	 * @param in the input stream to load from
	 * @param cacheFolder the folder to use for caching
	 * @param pm a progress monitor to observe the loading process
	 * @param inLength the number of bytes to expect from the argument input stream
	 * @return an Image Markup document representing the content of the
	 *            argument stream.
	 * @throws IOException
	 */
	public static ImDocument loadDocument(InputStream in, File cacheFolder, ProgressMonitor pm, long inLength) throws IOException {
		return loadDocument(in, cacheFolder, null, pm, inLength);
	}
	
	/**
	 * Load an Image Markup document. If the <code>verify</code> flag is set to
	 * <code>true</code>, this method checks the data in the IMF entries
	 * against any terminal 'entries.tsv' entry (TSV mode only).
	 * @param in the input stream to load from
	 * @param evl a logger to handle load verification results
	 * @return an Image Markup document representing the content of the
	 *            argument stream.
	 * @throws IOException
	 */
	public static ImDocument loadDocument(InputStream in, EntryVerificationLogger evl) throws IOException {
		return loadDocument(in, null, evl, null, -1);
	}
	
	/**
	 * Load an Image Markup document. If the <code>verify</code> flag is set to
	 * <code>true</code>, this method checks the data in the IMF entries
	 * against any terminal 'entries.tsv' entry (TSV mode only).
	 * @param in the input stream to load from
	 * @param evl a logger to handle load verification results
	 * @param pm a progress monitor to observe the loading process
	 * @return an Image Markup document representing the content of the
	 *            argument stream.
	 * @throws IOException
	 */
	public static ImDocument loadDocument(InputStream in, EntryVerificationLogger evl, ProgressMonitor pm) throws IOException {
		return loadDocument(in, null, evl, pm, -1);
	}
	
	/**
	 * Load an Image Markup document. If the <code>verify</code> flag is set to
	 * <code>true</code>, this method checks the data in the IMF entries
	 * against any terminal 'entries.tsv' entry (TSV mode only).
	 * @param in the input stream to load from
	 * @param evl a logger to handle load verification results
	 * @param pm a progress monitor to observe the loading process
	 * @param inLength the number of bytes to expect from the argument input stream
	 * @return an Image Markup document representing the content of the
	 *            argument stream.
	 * @throws IOException
	 */
	public static ImDocument loadDocument(InputStream in, EntryVerificationLogger evl, ProgressMonitor pm, long inLength) throws IOException {
		return loadDocument(in, null, evl, pm, inLength);
	}
	
	/**
	 * Load an Image Markup document. If the argument cache folder is null,
	 * page images will be cached in memory. For large documents, this can
	 * cause high memory consumption. The argument input stream is not closed.
	 * If the <code>verify</code> flag is set to <code>true</code>, this method
	 * checks the data in the IMF entries against any terminal 'entries.tsv'
	 * entry in the ZIP (TSV mode only).
	 * @param in the input stream to load from
	 * @param cacheFolder the folder to use for caching
	 * @param evl a logger to handle load verification results
	 * @return an Image Markup document representing the content of the
	 *            argument stream.
	 * @throws IOException
	 */
	public static ImDocument loadDocument(InputStream in, File cacheFolder, EntryVerificationLogger evl) throws IOException {
		return loadDocument(in, cacheFolder, evl, null, -1);
	}
	
	/**
	 * Load an Image Markup document. If the argument cache folder is null,
	 * page images will be cached in memory. For large documents, this can
	 * cause high memory consumption. The argument input stream is not closed.
	 * If the <code>verify</code> flag is set to <code>true</code>, this method
	 * checks the data in the IMF entries against any terminal 'entries.tsv'
	 * entry in the ZIP (TSV mode only).
	 * @param in the input stream to load from
	 * @param cacheFolder the folder to use for caching
	 * @param evl a logger to handle load verification results
	 * @param pm a progress monitor to observe the loading process
	 * @return an Image Markup document representing the content of the
	 *            argument stream.
	 * @throws IOException
	 */
	public static ImDocument loadDocument(InputStream in, File cacheFolder, EntryVerificationLogger evl, ProgressMonitor pm) throws IOException {
		return loadDocument(in, cacheFolder, evl, pm, -1);
	}
	
	/**
	 * Load an Image Markup document. If the argument cache folder is null,
	 * page images will be cached in memory. For large documents, this can
	 * cause high memory consumption. The argument input stream is not closed.
	 * If the <code>verify</code> flag is set to <code>true</code>, this method
	 * checks the data in the IMF entries against any terminal 'entries.tsv'
	 * entry in the ZIP (TSV mode only).
	 * @param in the input stream to load from
	 * @param cacheFolder the folder to use for caching
	 * @param evl a logger to handle load verification results
	 * @param pm a progress monitor to observe the loading process
	 * @param inLength the number of bytes to expect from the argument input stream
	 * @return an Image Markup document representing the content of the
	 *            argument stream.
	 * @throws IOException
	 */
	public static ImDocument loadDocument(InputStream in, File cacheFolder, EntryVerificationLogger evl, ProgressMonitor pm, long inLength) throws IOException {
		
		//	check entry verification logger
		if (evl == null)
			evl = EntryVerificationLogger.quiet;
		
		//	check progress monitor
		if (pm == null)
			pm = ProgressMonitor.quiet;
		
		//	make reading observable if we know how many bytes to expect
		if (0 < inLength)
			in = new ProgressMonitorInputStream(in, pm, inLength);
		
		//	get ready to unzip
		ImDocumentData data = new ZipInImDocumentData(cacheFolder);
		ZipInputStream zin = new ZipInputStream(in);
		
		//	read and cache archive contents
		pm.setStep("Un-zipping & caching archive");
		pm.setBaseProgress(0);
		pm.setMaxProgress(50);
		for (ZipEntry ze; (ze = zin.getNextEntry()) != null;) {
			String zen = ze.getName();
			if ("entries.tsv".equals(zen)) {
				BufferedReader entryIn = new BufferedReader(new InputStreamReader(zin, "UTF-8"));
				data.readEntryList(entryIn); // also sets storage flags, and also loads any properties
			}
			else {
				pm.setInfo(zen);
				OutputStream cacheOut = data.getOutputStream(zen);
				byte[] buffer = new byte[1024];
				for (int r; (r = zin.read(buffer, 0, buffer.length)) != -1;)
					cacheOut.write(buffer, 0, r);
				cacheOut.flush();
				cacheOut.close();
			}
		}
		zin.close();
		if (data.getStorageFlags() < 0) // no entry list at all, must be CSV mode
			data.setStorageFlags(STORAGE_MODE_CSV);
		
		//	instantiate and return document proper
		pm.setBaseProgress(50);
		pm.setProgress(0);
		pm.setMaxProgress(100);
		CascadingProgressMonitor cpm = new CascadingProgressMonitor(pm);
		return loadDocument(data, EntryVerificationLogger.silent /* we have already verified all entries above */, cpm);
	}
	
	private static class ProgressMonitorInputStream extends FilterInputStream {
		final ProgressMonitor pm;
		final long offset;
		final long length;
		long read;
		ProgressMonitorInputStream(InputStream in, ProgressMonitor pm, long length) {
			this(in, pm, 0, length);
		}
		ProgressMonitorInputStream(InputStream in, ProgressMonitor pm, long readOffset, long totalLength) {
			super(in);
			this.pm = pm;
			this.offset = readOffset;
			this.length = totalLength;
		}
		public int read() throws IOException {
			int r = super.read();
			if (r != -1)
				this.updateProgress(1);
			return r;
		}
		public int read(byte[] b) throws IOException {
			return this.read(b, 0, b.length);
		}
		public int read(byte[] b, int off, int len) throws IOException {
			int r = super.read(b, off, len);
			if (r != -1)
				this.updateProgress(r);
			return r;
		}
		public long skip(long n) throws IOException {
			int s = ((int) super.skip(n));
			if (s != -1)
				this.updateProgress(s);
			return s;
		}
		private void updateProgress(int r) {
			this.read += r;
			this.pm.setProgress((int) (((this.offset + this.read) * 100) / this.length));
		}
	}
	
	private static class ZipInImDocumentData extends ImDocumentData {
		private HashMap entryDataCache;
		private File entryDataCacheFolder;
		ZipInImDocumentData(File entryDataCacheFolder) {
			this.entryDataCache = ((entryDataCacheFolder == null) ? new HashMap() : null);
			this.entryDataCacheFolder = entryDataCacheFolder;
		}
		public boolean canLoadDocument() {
			return true;
		}
		public boolean canStoreDocument() {
			return false;
		}
		public boolean canReuseEntryData() {
			return false;
		}
		public String getDocumentDataId() {
			return null;
		}
		public boolean hasEntryData(ImDocumentEntry entry) {
			return this.hasEntry(entry);
		}
		public InputStream getInputStream(String entryName) throws IOException {
			ImDocumentEntry entry = this.getEntry(entryName);
			if (entry == null)
				throw new FileNotFoundException(entryName);
			if (this.entryDataCacheFolder == null) {
				byte[] entryData = ((byte[]) this.entryDataCache.get(entryName));
				if (entryData == null)
					throw new FileNotFoundException(entryName);
				else return new ByteArrayInputStream(entryData);
			}
			else {
				File entryDataFile = new File(this.entryDataCacheFolder, entryName);
				return new BufferedInputStream(new FileInputStream(entryDataFile));
			}
		}
		public OutputStream getOutputStream(final String entryName, boolean writeDirectly) throws IOException {
			OutputStream out;
			final int[] size = {-1};
			
			//	we have to use in memory caching
			if (this.entryDataCacheFolder == null)
				out = new ByteArrayOutputStream() {
					public void close() throws IOException {
						super.close();
						byte[] data = this.toByteArray();
						entryDataCache.put(entryName, data);
						size[0] = data.length;
					}
				};
			
			//	we do have a cache folder, use it
			else {
				checkEntryName(entryName);
				final File newEntryDataFile = new File(this.entryDataCacheFolder, (entryName + ".new"));
				out = new BufferedOutputStream(new FileOutputStream(newEntryDataFile) {
					public void close() throws IOException {
						super.flush();
						super.close();
						File exEntryDataFile = new File(entryDataCacheFolder, entryName);
						if (exEntryDataFile.exists() && newEntryDataFile.exists() && newEntryDataFile.getName().endsWith(".new"))
							exEntryDataFile.renameTo(new File(entryDataCacheFolder, (entryName + "." + System.currentTimeMillis() + ".old")));
						newEntryDataFile.renameTo(new File(entryDataCacheFolder, entryName));
						size[0] = ((int) newEntryDataFile.length());
					}
				});
			}
			
			//	make sure to update entry list
			return new DataHashOutputStream(out) {
				public void close() throws IOException {
					super.flush();
					super.close();
					//	cannot do V1/V2 distinction and abridge hash here ... replacing entries above instead
					putEntry(new ImDocumentEntry(entryName, size[0], System.currentTimeMillis(), this.getDataHash()));
				}
			};
		}
		public void dispose() {
			(new RuntimeException("ZipInImDocumentData: disposed")).printStackTrace(System.out);
			super.dispose();
			if (this.entryDataCache != null)
				this.entryDataCache.clear();
			if ((this.entryDataCacheFolder != null) && this.entryDataCacheFolder.exists())
				cleanCacheFolder(this.entryDataCacheFolder);
		}
		private static void cleanCacheFolder(File folder) {
			File[] folderContent = folder.listFiles();
			for (int c = 0; c < folderContent.length; c++) try {
				if (folderContent[c].isDirectory())
					cleanCacheFolder(folderContent[c]);
				else folderContent[c].delete();
			}
			catch (Throwable t) {
				System.out.println("Error cleaning up cached file '" + folderContent[c].getAbsolutePath() + "': " + t.getMessage());
				t.printStackTrace(System.out);
			}
			try {
				folder.delete();
			}
			catch (Throwable t) {
				System.out.println("Error cleaning up cache folder '" + folder.getAbsolutePath() + "': " + t.getMessage());
				t.printStackTrace(System.out);
			}
		}
	}
	
	private static class ZipOutImDocumentData extends ImDocumentData {
		ZipOutputStream zipOut;
		long updateTime;
		ZipOutImDocumentData(ZipOutputStream zipOut, long updateTime, long storageFlags) {
			this.zipOut = zipOut;
			this.updateTime = updateTime;
			this.setStorageFlags(storageFlags);
		}
		public boolean canLoadDocument() {
			return false;
		}
		public boolean canStoreDocument() {
			return true;
		}
		public boolean canReuseEntryData() {
			return false;
		}
		public String getDocumentDataId() {
			return null;
		}
		public boolean hasEntryData(ImDocumentEntry entry) {
			return this.hasEntry(entry);
		}
		public InputStream getInputStream(String entryName) throws IOException {
			throw new FileNotFoundException(entryName);
		}
		public OutputStream getOutputStream(final String entryName, boolean writeDirectly) throws IOException {
			
			//	put entry, and make sure not to close the stream proper
			checkEntryName(entryName);
			ZipEntry entry = new ZipEntry(entryName);
			entry.setTime(this.updateTime);
			this.zipOut.putNextEntry(entry);
			final int[] size = {0};
			OutputStream out = new FilterOutputStream(this.zipOut) {
				public void write(int b) throws IOException {
					super.write(b);
					size[0]++;
				}
				public void write(byte[] b) throws IOException {
					this.write(b, 0, b.length);
				}
				public void write(byte[] b, int off, int len) throws IOException {
					super.write(b, off, len);
					size[0] += len;
				}
				public void close() throws IOException {
					zipOut.flush();
					zipOut.closeEntry();
				}
			};
			
			//	make sure to update entry list
			return new DataHashOutputStream(out) {
				public void close() throws IOException {
					super.flush();
					super.close();
					String entryDataHash = this.getDataHash();
					if (0 < getStorageFlags());
						entryDataHash = abridgeEntryDataHash(entryDataHash);
					putEntry(new ImDocumentEntry(entryName, size[0], updateTime, entryDataHash));
				}
			};
		}
	}
	
	/**
	 * Instantiate an Image Markup document from the data provided by a
	 * document data object.
	 * @param data the document data object to load the document from
	 * @param pm a progress monitor to observe the loading process
	 * @return the document instantiated from the argument data
	 * @throws IOException
	 */
	public static DataBackedImDocument loadDocument(ImDocumentData data, ProgressMonitor pm) throws IOException {
		return loadDocument(data, null, pm);
	}
	
	/**
	 * Instantiate an Image Markup document from the data provided by a
	 * document data object.
	 * @param data the document data object to load the document from
	 * @param evl a logger to handle load verification results
	 * @param pm a progress monitor to observe the loading process
	 * @return the document instantiated from the argument data
	 * @throws IOException
	 */
	public static DataBackedImDocument loadDocument(ImDocumentData data, EntryVerificationLogger evl, ProgressMonitor pm) throws IOException {
		
		//	check entry verification logger
		if (evl == null)
			evl = EntryVerificationLogger.quiet;
		
		//	check progress monitor
		if (pm == null)
			pm = ProgressMonitor.quiet;
		
		//	read document meta data
		pm.setStep("Reading document data");
		pm.setBaseProgress(0);
		pm.setProgress(0);
		pm.setMaxProgress(5);
		StringTupel docData;
		if (data.hasEntry("document.tsv")) {
//			InputStream docIn = data.getInputStream("document.tsv");
//			TsvReader docTsv = new TsvReader(new BufferedReader(new InputStreamReader(docIn, "UTF-8")));
			TsvReader docTsv = new TsvReader(getReader(data, "document.tsv", evl, pm));
			docData = new StringTupel();
			String[] record = new String[docTsv.keys.length];
			for (int length; (length = docTsv.fillRecord(record)) != -1;) {
				if (length < 2)
					continue;
				docData.setValue(record[0], record[1]);
			}
			docTsv.close();
		}
		else if (data.hasEntry("document.csv")) {
			InputStream docIn = data.getInputStream("document.csv");
			StringRelation docDatas = StringRelation.readCsvData(new InputStreamReader(docIn, "UTF-8"), true, null);
			docIn.close();
			docData = docDatas.get(0);
		}
		else throw new IOException("Invalid Image Markup data: document data table missing");
		String docId = docData.getValue(DOCUMENT_ID_ATTRIBUTE);
		if (docId == null)
			throw new IOException("Invalid Image Markup data: document ID missing");
		final DataBoundImDocument doc = new DataBoundImDocument(docId, data);
//		setAttributes(doc, docData.getValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE));
		if (docData.getValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE) == null) {
			String[] keys = docData.getKeyArray();
			for (int k = 0; k < keys.length; k++) {
				if (DOCUMENT_ID_ATTRIBUTE.equals(keys[k]))
					continue;
				doc.setAttribute(keys[k], docData.getValue(keys[k]));
			}
		}
		else setAttributes(doc, docData.getValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE));
		//	TODO_later load orientation
		
		//	read fonts (if any)
		//	TODOnot only add supplement proxy holding plain data, and parse on demand
		//	==> we need the fonts proper because they have UUIDs, which need to resolve ...
		//	==> ... and holding string data in Map actually needs more memory than glyph bitmap proper
		pm.setStep("Reading font data");
		pm.setBaseProgress(5);
		pm.setProgress(0);
		pm.setMaxProgress(15);
		if (data.hasEntry("fonts.tsv")) {
			TsvReader fontTsv = new TsvReader(getReader(data, "fonts.tsv", evl, pm));
			String[] record = new String[fontTsv.keys.length];
			ImFont font = null;
			String fontStyle = null;
			for (int length; (length = fontTsv.fillRecord(record)) != -1;) {
				if (length < 2)
					continue;
				int charId = Integer.parseInt(record[0], 16);
				if (charId == 0) /* char ID zero marks header record of new font */ {
					font = new ImFont(doc, record[1] /* char string column holds font name in font header records */);
					fontStyle = ((2 < length) ? record[2] : "");
					if ("X".equals(fontStyle)) {
						fontStyle = null;
						font.setMixedStyle(true);
					}
					else {
						font.setBold(fontStyle.indexOf("B") != -1);
						font.setItalics(fontStyle.indexOf("I") != -1);
						if (fontStyle.indexOf("M") != -1)
							font.setMonospaced(true);
						else if (fontStyle.indexOf("S") != -1)
							font.setSerif(true);
						else if (fontStyle.indexOf("G") != -1) {
							font.setMonospaced(false);
							font.setSerif(false);
						}
					}
					if (3 < length) // char image column holds attributes in font header records
						setAttributes(font, record[3], null);
					doc.addFont(font);
				}
				else if (font != null) {
					String charStr = record[1];
					String charImageHex = ((3 < length) ? record[3] : null);
					String charPathString = ((4 < length) ? record[4] : null);;
					font.addCharacter(charId, charStr, charImageHex, charPathString);
					if (fontStyle == null) /* mixed style */ {
						String charStyle = ((2 < length) ? record[2] : "");
						font.setBold((charStyle.indexOf("B") != -1), charId);
						font.setItalics((charStyle.indexOf("I") != -1), charId);
						if (charStyle.indexOf("M") != -1)
							font.setMonospaced(true, charId);
						else if (charStyle.indexOf("S") != -1)
							font.setSerif(true, charId);
					}
				}
			}
			fontTsv.close();
		}
		else if (data.hasEntry("fonts.csv")) {
			InputStream fontsIn = data.getInputStream("fonts.csv");
			StringRelation fontsData = StringRelation.readCsvData(new InputStreamReader(fontsIn, "UTF-8"), true, null);
			fontsIn.close();
			ImFont font = null;
			for (int f = 0; f < fontsData.size(); f++) {
				StringTupel charData = fontsData.get(f);
				String fontName = charData.getValue(ImFont.NAME_ATTRIBUTE);
				if (fontName == null)
					continue;
				if ((font == null) || !font.name.equals(fontName)) {
					pm.setInfo("Adding font " + fontName);
					font = new ImFont(doc, fontName, false, false, false, false);
					String fontStyle = charData.getValue(ImFont.STYLE_ATTRIBUTE, "");
					font.setMixedStyle(fontStyle.indexOf("X") != -1);
					font.setBold(fontStyle.indexOf("B") != -1);
					font.setItalics(fontStyle.indexOf("I") != -1);
					if (fontStyle.indexOf("M") != -1)
						font.setMonospaced(true);
					else if (fontStyle.indexOf("S") != -1)
						font.setSerif(true);
					doc.addFont(font);
				}
				int charId = Integer.parseInt(charData.getValue(ImFont.CHARACTER_ID_ATTRIBUTE, "-1"), 16);
				String charStr = charData.getValue(ImFont.CHARACTER_STRING_ATTRIBUTE);
				if ((charId == 0) && (charStr.length() == 0)) {
					String fontAttriutes = charData.getValue(ImFont.CHARACTER_IMAGE_ATTRIBUTE);
					if (fontAttriutes != null)
						setAttributes(font, fontAttriutes);
				}
				else if (charId > 0) {
					String charImageHex = charData.getValue(ImFont.CHARACTER_IMAGE_ATTRIBUTE);
					String charPathString = charData.getValue(ImFont.CHARACTER_PATH_ATTRIBUTE);
					font.addCharacter(charId, charStr, charImageHex, charPathString);
					if (font.isMixedStyle()) {
						String charStyle = charData.getValue(ImFont.STYLE_ATTRIBUTE, "");
						font.setBold((charStyle.indexOf("B") != -1), charId);
						font.setItalics((charStyle.indexOf("I") != -1), charId);
						if (charStyle.indexOf("M") != -1)
							font.setMonospaced(true, charId);
						else if (charStyle.indexOf("S") != -1)
							font.setSerif(true, charId);
					}
				}
			}
		}
		
		//	read page image data
		pm.setStep("Reading page image data");
		pm.setBaseProgress(15);
		pm.setProgress(0);
		pm.setMaxProgress(20);
		final HashMap pageImageAttributesById = new HashMap();
		if (data.hasEntry("pageImages.tsv")) {
			TsvReader pageImageTsv = new TsvReader(getReader(data, "pageImages.tsv", evl, pm));
			String[] record = new String[pageImageTsv.keys.length];
			for (int length; (length = pageImageTsv.fillRecord(record)) != -1;) {
				if (length < 4)
					continue;
				pageImageAttributesById.put(new Integer(record[0]), new PageImageAttributes(record, 1, length));
			}
			pageImageTsv.close();
		}
		else if (data.hasEntry("pageImages.csv")) {
			InputStream pageImagesIn = data.getInputStream("pageImages.csv");
			StringRelation pageImagesData = StringRelation.readCsvData(new InputStreamReader(pageImagesIn, "UTF-8"), true, null);
			pageImagesIn.close();
			for (int p = 0; p < pageImagesData.size(); p++) {
				StringTupel pageImageData = pageImagesData.get(p);
				int piPageId = Integer.parseInt(pageImageData.getValue(ImObject.PAGE_ID_ATTRIBUTE));
				String piAttributes = pageImageData.getValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE);
				pageImageAttributesById.put(new Integer(piPageId), new PageImageAttributes(piAttributes));
			}
		}
		
		//	read pages
		pm.setStep("Reading page data");
		pm.setBaseProgress(20);
		pm.setProgress(0);
		pm.setMaxProgress(30);
		if (data.hasEntry("pages.tsv")) {
			TsvReader pageTsv = new TsvReader(getReader(data, "pages.tsv", evl, pm));
			String[] record = new String[pageTsv.keys.length];
			for (int length; (length = pageTsv.fillRecord(record)) != -1;) {
				if (length < 2)
					continue;
				int pageId = Integer.parseInt(record[0]);
				BoundingBox bounds = BoundingBox.parse(record[1]);
				ImPage page = new ImPage(doc, pageId, bounds); // constructor adds page to document automatically
				setAttributes(page, pageTsv.keys, 2, length, record, 0, null);
				PageImageAttributes pia = ((PageImageAttributes) pageImageAttributesById.get(new Integer(pageId)));
				if (pia != null)
					page.setImageDPI(pia.currentDpi);
			}
			pageTsv.close();
		}
		else if (data.hasEntry("pages.csv")) {
			InputStream pagesIn = data.getInputStream("pages.csv");
			StringRelation pagesData = StringRelation.readCsvData(new InputStreamReader(pagesIn, "UTF-8"), true, null);
			pagesIn.close();
			pm.setInfo("Adding " + pagesData.size() + " pages");
			for (int p = 0; p < pagesData.size(); p++) {
				pm.setProgress((p * 100) / pagesData.size());
				StringTupel pageData = pagesData.get(p);
				int pageId = Integer.parseInt(pageData.getValue(PAGE_ID_ATTRIBUTE));
				BoundingBox bounds = BoundingBox.parse(pageData.getValue(BOUNDING_BOX_ATTRIBUTE));
				ImPage page = new ImPage(doc, pageId, bounds); // constructor adds page to document automatically
				setAttributes(page, pageData.getValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, ""));
				PageImageAttributes pia = ((PageImageAttributes) pageImageAttributesById.get(new Integer(pageId)));
				if (pia != null)
					page.setImageDPI(pia.currentDpi);
			}
		}
		else throw new IOException("Invalid Image Markup data: page table missing");
		
		//	set up progress indication for chunked/segmented entries
		int wordSizeSum = 0;
		int regSizeSum = 0;
		int annotSizeSum = 0;
		ImDocumentEntry[] entries = data.getEntries();
		for (int e = 0; e < entries.length; e++) {
			if (!entries[e].name.endsWith(".tsv"))
				continue;
			if (entries[e].name.startsWith("words."))
				wordSizeSum += entries[e].size;
			else if (entries[e].name.startsWith("regions."))
				regSizeSum += entries[e].size;
			if (entries[e].name.startsWith("annotations."))
				annotSizeSum += entries[e].size;
		}
		
		//	read words (first add words, then chain them and set attributes, as they might not be stored in stream order)
		pm.setStep("Reading word data");
		pm.setBaseProgress(30);
		pm.setProgress(0);
		pm.setMaxProgress(70);
		if (data.hasEntry("words.tsv")) {
			int[] sizeOffset = {0};
			TsvReader wordTsv = new TsvReader(getReader(data, "words.tsv", evl, pm, sizeOffset, wordSizeSum));
			LinkedHashMap toChainWords = new LinkedHashMap();
			
			//	read word data, collecting chunk page ranges to process one by one
			ArrayList includeChunkEntryRanges = new ArrayList();
			readWordChunk(doc, wordTsv, toChainWords, includeChunkEntryRanges);
			for (int c = 0; c < includeChunkEntryRanges.size(); c++) {
				String chunkEntryRange = ((String) includeChunkEntryRanges.get(c));
				wordTsv = new TsvReader(getReader(data, ("words." + chunkEntryRange + ".tsv"), evl, pm, sizeOffset, wordSizeSum));
				readWordChunk(doc, wordTsv, toChainWords, includeChunkEntryRanges);
			}
			
			//	process words whose predecessors were somehow loaded after themselves
			for (Iterator wit = toChainWords.keySet().iterator(); wit.hasNext();) {
				ImWord word = ((ImWord) wit.next());
				String[] chainWordIDs = ((String[]) toChainWords.get(word));
				String prevWordId = chainWordIDs[0];
				ImWord prevWord = doc.getWord(prevWordId);
				if (prevWord == null)
					System.out.println("Failed to post-hoc chain word '" + word.getString() + "' at " + word.getLocalID() + " to predecessor " + prevWordId);
				else word.setPreviousWord(prevWord);
			}
		}
		else if (data.hasEntry("words.csv")) {
			InputStream wordsIn = data.getInputStream("words.csv");
			StringRelation wordsData = StringRelation.readCsvData(new InputStreamReader(wordsIn, "UTF-8"), true, null);
			wordsIn.close();
			pm.setInfo("Adding " + wordsData.size() + " words");
			for (int w = 0; w < wordsData.size(); w++) {
				pm.setProgress((w * 100) / wordsData.size());
				StringTupel wordData = wordsData.get(w);
				ImPage page = doc.getPage(Integer.parseInt(wordData.getValue(PAGE_ID_ATTRIBUTE)));
				BoundingBox bounds = BoundingBox.parse(wordData.getValue(BOUNDING_BOX_ATTRIBUTE));
				new ImWord(page, bounds, wordData.getValue(STRING_ATTRIBUTE));
			}
			pm.setStep("Chaining text streams");
			pm.setBaseProgress(75);
			pm.setMaxProgress(80);
			for (int w = 0; w < wordsData.size(); w++) {
				pm.setProgress((w * 100) / wordsData.size());
				StringTupel wordData = wordsData.get(w);
				BoundingBox bounds = BoundingBox.parse(wordData.getValue(BOUNDING_BOX_ATTRIBUTE));
				ImWord word = doc.getWord(Integer.parseInt(wordData.getValue(PAGE_ID_ATTRIBUTE)), bounds);
				ImWord prevWord = doc.getWord(wordData.getValue(ImWord.PREVIOUS_WORD_ATTRIBUTE));
				if (prevWord != null)
					word.setPreviousWord(prevWord);
				else {
					String textStreamType = wordData.getValue(ImWord.TEXT_STREAM_TYPE_ATTRIBUTE);
					if ((textStreamType == null) || (textStreamType.trim().length() == 0))
						textStreamType = ImWord.TEXT_STREAM_TYPE_MAIN_TEXT;
					word.setTextStreamType(textStreamType);
				}
				word.setNextRelation(wordData.getValue(ImWord.NEXT_RELATION_ATTRIBUTE).charAt(0));
				setAttributes(word, wordData.getValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, ""));
			}
		}
		else throw new IOException("Invalid Image Markup data: word table missing");
		
		//	read regions
		pm.setStep("Reading region data");
		pm.setBaseProgress(70);
		pm.setProgress(0);
		pm.setMaxProgress(80);
		loadRegions(doc, null, null, data, evl, pm, regSizeSum);
		
		//	read annotations
		pm.setStep("Reading annotation data");
		pm.setBaseProgress(80);
		pm.setProgress(0);
		pm.setMaxProgress(90);
		loadAnnotations(doc, null, null, data, evl, pm, annotSizeSum);
		
//		//	create GraphicsDataProvider if document has respective entry
//		GraphicsDataProvider gdp = null;
//		if (data.hasEntry(GRAPHICS_DATA_BUNDLE_ENTRY_NAME))
//			gdp = new GraphicsDataProvider(data);
//		
		//	read metadata of supplements (if any)
		//	TODOnot only add supplement proxy holding plain data, and parse on demand
		//	==> we need the supplements proper because they have UUIDs, which need to resolve ...
		//	==> ... and holding string data in Map actually needs more memory than parsed supplement proper
		pm.setStep("Reading supplement data");
		pm.setBaseProgress(90);
		pm.setProgress(0);
		pm.setMaxProgress(97);
		loadSupplements(doc, null, data, evl, pm);
		/* We need to do this explicitly because supplements add themselves to
		 * their document upon creation via the normal addSupplement() method,
		 * marking themselves as dirty in our data bound document. */
		doc.markSupplementsNotDirty();
		
		//	create page image source
		pm.setStep("Creating page image source");
		pm.setBaseProgress(97);
		pm.setProgress(0);
		pm.setMaxProgress(100);
		doc.setPageImageSource(new DbidPageImageStore(doc, pageImageAttributesById));
		pm.setProgress(100);
		pm.setBaseProgress(100);
		
		//	finally ...
		return doc;
	}
	
	private static BufferedReader getReader(ImDocumentData data, String entryName, EntryVerificationLogger evl, ProgressMonitor pm) throws IOException {
		return getReader(data, entryName, evl, pm, null, -1);
	}
	private static BufferedReader getReader(final ImDocumentData data, String entryName, final EntryVerificationLogger evl, final ProgressMonitor pm, final int[] sizeOffset, final int sizeTotal) throws IOException {
		final ImDocumentEntry entry = data.getEntry(entryName);
		int readOffset = 0;
		if (sizeOffset != null) {
			readOffset = sizeOffset[0];
			sizeOffset[0] += entry.size;
		}
		final DataHashInputStream hasher = new DataHashInputStream(data.getInputStream(entry.name));
		return new BufferedReader(new InputStreamReader(new ProgressMonitorInputStream(hasher, pm, readOffset, sizeTotal) {
			public void close() throws IOException {
				super.close();
				String dataHash = abridgeEntryDataHash(hasher.getDataHashString());
				if ((this.read == entry.size) && dataHash.equals(entry.dataHash))
					evl.entryVerificationSuccessful(entry);
				else evl.entryVerificationFailed(entry, ((int) this.read), dataHash);
			}
		}, "UTF-8"));
	}
	
	private static void readWordChunk(ImDocument doc, TsvReader wordTsv, LinkedHashMap toChainWords, ArrayList includeChunkEntryRanges) throws IOException {
		String[] record = new String[wordTsv.keys.length];
		for (int length; (length = wordTsv.fillRecord(record)) != -1;) {
			if (length < 2)
				continue;
			if ("@INCLUDE".equals(record[0])) {
				includeChunkEntryRanges.add(record[1]);
				continue;
			}
			if (length < 3)
				continue;
			ImPage page = doc.getPage(Integer.parseInt(record[0]));
			BoundingBox wordBounds = BoundingBox.parse(record[1]);
			String wordStr = record[2];
			ImWord word = new ImWord(page, wordBounds, wordStr);
			String prevWordId = ((3 < length) ? record[3] : null);
			if (prevWordId == null) /* start of new text stream */ {
				if (6 < length)
					word.setTextStreamType(record[6] /* text stream type, only stored in head word */);
			}
			else /* text stream continues */ {
				ImWord prevWord = doc.getWord(prevWordId);
				if (prevWord == null) {
					String[] chainWordIDs = {
						prevWordId,
						((4 < length) ? record[4] : null)
					}; // need to create separate array, as record will be re-filled in next round of loop
					toChainWords.put(word, chainWordIDs);
					System.out.println("Failed to in-line chain word '" + wordStr + "' at " + word.getLocalID() + " to predecessor " + prevWordId);
				}
				else word.setPreviousWord(prevWord);
			}
			String nextRelation = ((5 < length) ? record[5] : null);
			if ((nextRelation != null) && (nextRelation.length() != 0))
				word.setNextRelation(nextRelation.charAt(0));
			setAttributes(word, wordTsv.keys, 7, length, record, 0, null);
		}
		wordTsv.close();
	}
	
	static void loadRegions(ImDocument doc, String docId, ArrayList regs, ImDocumentData data, EntryVerificationLogger evl, ProgressMonitor pm, int regSizeSum) throws IOException {
		if (data.hasEntry("regions.tsv")) {
			int[] sizeOffset = {0};
			TsvReader regTsv = new TsvReader(getReader(data, "regions.tsv", evl, pm, sizeOffset, regSizeSum));
			ArrayList includeRegionTypes = new ArrayList();
//			readRegions(doc, regTsv, null, includeRegionTypes);
			ImRegionList allRegions = null;
			allRegions = readRegions(doc, docId, regs, regTsv, null, allRegions, includeRegionTypes);
			for (int t = 0; t < includeRegionTypes.size(); t++) {
				String regType = ((String) includeRegionTypes.get(t));
				regTsv = new TsvReader(getReader(data, ("regions." + regType + ".tsv"), evl, pm, sizeOffset, regSizeSum));
//				readRegions(doc, regTsv, regType, includeRegionTypes);
				readRegions(doc, docId, regs, regTsv, regType, allRegions, includeRegionTypes);
			}
			if (allRegions != null) {
				allRegions.sort();
				ImPage regPage = null;
				for (int r = 0; r < allRegions.size(); r++) {
					ImRegion reg = ((ImRegion) allRegions.get(r));
					if ((regPage == null) || (regPage.pageId != reg.pageId))
						regPage = doc.getPage(reg.pageId);
					regPage.addRegion(reg);
//					System.out.println(reg.getType() + " at " + reg.pageId + "." + reg.bounds); // TODO remove this !!!
				}
			}
		}
		else if (data.hasEntry("regions.csv")) {
			InputStream regsIn = data.getInputStream("regions.csv");
			StringRelation regsData = StringRelation.readCsvData(new InputStreamReader(regsIn, "UTF-8"), true, null);
			regsIn.close();
			pm.setInfo("Adding " + regsData.size() + " regions");
			HashMap regionsById = new HashMap();
			for (int r = 0; r < regsData.size(); r++) {
				pm.setProgress((r * 100) / regsData.size());
				StringTupel regData = regsData.get(r);
				ImPage page = doc.getPage(Integer.parseInt(regData.getValue(PAGE_ID_ATTRIBUTE)));
				BoundingBox bounds = BoundingBox.parse(regData.getValue(BOUNDING_BOX_ATTRIBUTE));
				String type = regData.getValue(TYPE_ATTRIBUTE);
				String regId = (type + "@" + page.pageId + "." + bounds.toString());
				ImRegion reg = ((ImRegion) regionsById.get(regId));
				if (reg == null) {
					reg = new ImRegion(page, bounds, type);
					regionsById.put(regId, reg);
				}
				setAttributes(reg, regData.getValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, ""));
			}
		}
		else throw new IOException("Invalid Image Markup data: region table missing");
	}
	private static ImRegionList readRegions(ImDocument doc, String docId, ArrayList regs, TsvReader regTsv, String regType, ImRegionList allRegions, ArrayList includeRegionTypes) throws IOException {
		int recordOffset = ((regType == null) ? 0 : 1);
		String[] record = new String[recordOffset + regTsv.keys.length];
		if (regType != null)
			record[0] = regType;
		boolean firstReg = (regType == null);
		for (int length; (length = regTsv.fillRecord(record, recordOffset)) != -1;) {
			if (length < 2)
				continue;
			if ("@INCLUDE".equals(record[0])) {
				includeRegionTypes.add(record[1]);
				continue;
			}
			if (length < 3)
				continue;
			if (doc == null) {
				ImRegion reg = new ImRegionStub(docId, Integer.parseInt(record[1]), BoundingBox.parse(record[2]), record[0] /* type, regardless if loaded or injected */);
				setAttributes(reg, regTsv.keys, 3, length, record, recordOffset, regTsv.extValues);
				regs.add(reg);
				continue;
			}
			if (firstReg) {
				ImObjectTypeOrder[] regTypeOrders = regTsv.getTypeOrders();
				if (regTypeOrders != null)
					allRegions = new ImRegionList(regTypeOrders);
				firstReg = false;
			}
			int pageId = Integer.parseInt(record[1]);
			BoundingBox regBounds = BoundingBox.parse(record[2]);
			ImRegion reg;
			if (allRegions == null) /* no type order to observe, attach regions directly */ {
				ImPage page = doc.getPage(pageId);
				reg = new ImRegion(page, regBounds, record[0] /* type, regardless if loaded or injected */);
			}
			else /* need to enforce type order after loading all regions, create detached */ {
				reg = new ImRegion(doc, pageId, regBounds, record[0] /* type, regardless if loaded or injected */);
				allRegions.add(reg);
			}
			setAttributes(reg, regTsv.keys, 3, length, record, recordOffset, regTsv.extValues);
		}
		regTsv.close();
		return allRegions;
	}
	
	private static class ImRegionStub extends ImRegion {
		private String docId;
		private String uuid = null;
		ImRegionStub(String docId, int pageId, BoundingBox bounds, String type) {
			super(null, pageId, bounds, type);
			this.docId = docId;
		}
		public ImPage getPage() {
			return null;
		}
		public void setPage(ImPage page) {}
		public PageImage getImage() {
			return null;
		}
		public ImWord[] getWords() {
			return new ImWord[0];
		}
		public ImWord[] getTextStreamHeads() {
			return new ImWord[0];
		}
		public ImRegion[] getRegions() {
			return new ImRegion[0];
		}
		public ImRegion[] getRegions(String type) {
			return new ImRegion[0];
		}
		public ImRegion[] getRegions(boolean fuzzy) {
			return new ImRegion[0];
		}
		public ImRegion[] getRegions(String type, boolean fuzzy) {
			return new ImRegion[0];
		}
		public String[] getRegionTypes() {
			return new String[0];
		}
		public String getDocumentProperty(String propertyName) {
			return null;
		}
		public String getDocumentProperty(String propertyName, String defaultValue) {
			return defaultValue;
		}
		public String[] getDocumentPropertyNames() {
			return new String[0];
		}
		public PageImage getPageImage() {
			return null;
		}
		public PageImageInputStream getPageImageAsStream() {
			return null;
		}
		public String getUUID() {
			if (this.uuid == null)
				this.uuid = UuidHelper.getUUID(this, this.docId);
			return this.uuid;
		}
	}
	
	static void loadAnnotations(ImDocument doc, String docId, ArrayList annots, ImDocumentData data, EntryVerificationLogger evl, ProgressMonitor pm, int annotSizeSum) throws IOException {
		if (data.hasEntry("annotations.tsv")) {
			int[] sizeOffset = {0};
			TsvReader annotTsv = new TsvReader(getReader(data, "annotations.tsv", evl, pm, sizeOffset, annotSizeSum));
			ArrayList includeAnnotTypes = new ArrayList();
			ImAnnotationList allAnnots = null;
			allAnnots = readAnnotations(doc, docId, annots, annotTsv, null, allAnnots, includeAnnotTypes);
			for (int t = 0; t < includeAnnotTypes.size(); t++) {
				String annotType = ((String) includeAnnotTypes.get(t));
				annotTsv = new TsvReader(getReader(data, ("annotations." + annotType + ".tsv"), evl, pm, sizeOffset, annotSizeSum));
				readAnnotations(doc, docId, annots, annotTsv, annotType, allAnnots, includeAnnotTypes);
			}
			if (allAnnots != null) {
				allAnnots.sort();
				for (int a = 0; a < allAnnots.size(); a++) {
					ImAnnotation annot = ((ImAnnotation) allAnnots.get(a));
					annot = doc.addAnnotation(annot);
//					System.out.println(annot.getType() + " at " + annot.getFirstWord().getLocalID() + "." + annot.getLastWord().getLocalID()); // TODO remove this !!!
				}
			}
		}
		else if (data.hasEntry("annotations.csv")) {
			InputStream annotsIn = data.getInputStream("annotations.csv");
			StringRelation annotsData = StringRelation.readCsvData(new InputStreamReader(annotsIn, "UTF-8"), true, null);
			annotsIn.close();
			pm.setInfo("Adding " + annotsData.size() + " annotations");
			for (int a = 0; a < annotsData.size(); a++) {
				pm.setProgress((a * 100) / annotsData.size());
				StringTupel annotData = annotsData.get(a);
				ImWord firstWord = doc.getWord(annotData.getValue(ImAnnotation.FIRST_WORD_ATTRIBUTE));
				ImWord lastWord = doc.getWord(annotData.getValue(ImAnnotation.LAST_WORD_ATTRIBUTE));
				if ((firstWord == null) || (lastWord == null))
					continue;
				String type = annotData.getValue(TYPE_ATTRIBUTE);
				ImAnnotation annot = doc.addAnnotation(firstWord, lastWord, type);
				if (annot != null)
					setAttributes(annot, annotData.getValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, ""));
			}
		}
		else throw new IOException("Invalid Image Markup data: annotation table missing");
	}
	private static ImAnnotationList readAnnotations(ImDocument doc, String docId, ArrayList annots, TsvReader annotTsv, String annotType, ImAnnotationList allAnnots, ArrayList includeAnnotTypes) throws IOException {
		int recordOffset = ((annotType == null) ? 0 : 1);
		String[] record = new String[recordOffset + annotTsv.keys.length];
		if (annotType != null)
			record[0] = annotType;
		boolean firstAnnot = (annotType == null);
		for (int length; (length = annotTsv.fillRecord(record, recordOffset)) != -1;) {
			if (length < 2)
				continue;
			if ("@INCLUDE".equals(record[0])) {
				includeAnnotTypes.add(record[1]);
				continue;
			}
			if (length < 3)
				continue;
			if (doc == null) {
				ImAnnotation annot = new ImAnnotationStub(docId, record[1], record[2]);
				setAttributes(annot, annotTsv.keys, 3, length, record, recordOffset, annotTsv.extValues);
				annot.setType(record[0]); // after all the other attributes, as this makes it read-only
				annots.add(annot);
				continue;
			}
			if (firstAnnot) {
				ImObjectTypeOrder[] annotTypeOrders = annotTsv.getTypeOrders();
				if (annotTypeOrders != null)
					allAnnots = new ImAnnotationList(annotTypeOrders);
				firstAnnot = false;
			}
			ImWord firstWord = ((doc == null) ? null : doc.getWord(record[1]));
			if ((firstWord == null) && (doc != null)) {
				System.out.println("Could not find first word of '" + record[0] + "' annotation: " + record[1]);
				continue;
			}
			ImWord lastWord = ((doc == null) ? null : doc.getWord(record[2]));
			if ((lastWord == null) && (doc != null)) {
				System.out.println("Could not find last word of '" + record[0] + "' annotation: " + record[2]);
				continue;
			}
			ImAnnotation annot;
			if (allAnnots == null) /* no type order to observe, attach regions directly */ {
				annot = doc.addAnnotation(firstWord, lastWord, record[0] /* type, regardless if loaded or injected */);
				if (annot == null) {
					System.out.println("Failed to annotate '" + record[0] + "' from " + record[1] + " to " + record[2]);
					continue;
				}
			}
			else /* need to enforce type order after loading all regions, create detached */ {
				annot = new ImAnnotationLoadTray(firstWord, lastWord, record[0] /* type, regardless if loaded or injected */);
				allAnnots.add(annot);
			}
			setAttributes(annot, annotTsv.keys, 3, length, record, recordOffset, annotTsv.extValues);
		}
		annotTsv.close();
		return allAnnots;
	}
	private static class ImAnnotationLoadTray extends AbstractAttributed implements ImAnnotation {
		final ImWord firstWord;
		final ImWord lastWord;
		final String type;
		ImAnnotationLoadTray(ImWord firstWord, ImWord lastWord, String type) {
			this.firstWord = firstWord;
			this.lastWord = lastWord;
			this.type = type;
		}
		public String getType() {
			return this.type;
		}
		public void setType(String type) { /* no need to set anything, we're a short-lived placeholder */ }
		public String getLocalID() {
//			return (this.type + "@" + this.firstWord.getLocalID() + "-" + this.lastWord.getLocalID());
			return null; // we're only for sorting
		}
		public String getLocalUID() {
			return null; // we're only for sorting
		}
		public String getUUID() {
			return null; // we're only for sorting
		}
		public ImDocument getDocument() {
			return null;
		}
		public String getDocumentProperty(String propertyName) {
			return null;
		}
		public String getDocumentProperty(String propertyName, String defaultValue) {
			return defaultValue;
		}
		public String[] getDocumentPropertyNames() {
			return new String[0];
		}
		public ImWord getFirstWord() {
			return this.firstWord;
		}
		public void setFirstWord(ImWord firstWord) { /* we're read-only for now */ }
		public ImWord getLastWord() {
			return this.lastWord;
		}
		public void setLastWord(ImWord lastWord) { /* we're read-only for now */ }
	}
	private static class ImAnnotationStub extends AbstractAttributed implements ImAnnotation {
		private String docId;
		private String firstWordId;
		private String lastWordId;
		private String type;
		private String luid = null;
		private String uuid = null;
		ImAnnotationStub(String docId, String firstWordId, String lastWordId) {
			this.docId = docId;
			this.firstWordId = firstWordId;
			this.lastWordId = lastWordId;
		}
		public String getType() {
			return this.type;
		}
		public void setType(String type) {
			if (this.type == null)
				this.type = type; // we're read-only for now (first call seals the attributes)
		}
		public String getLocalID() {
			return (this.type + "@" + this.firstWordId + "-" + this.lastWordId);
		}
		public String getLocalUID() {
			if (this.luid == null) {
				int fwPageId = Integer.parseInt(this.firstWordId.substring(0, this.firstWordId.indexOf(".")));
				BoundingBox fwBounds = BoundingBox.parse(this.firstWordId.substring(this.firstWordId.indexOf(".") + ".".length()));
				int lwPageId = Integer.parseInt(this.lastWordId.substring(0, this.lastWordId.indexOf(".")));
				BoundingBox lwBounds = BoundingBox.parse(this.lastWordId.substring(this.lastWordId.indexOf(".") + ".".length()));
				this.luid = AnnotationUuidHelper.getLocalUID(this.type, fwPageId, lwPageId, fwBounds.left, fwBounds.top, lwBounds.right, lwBounds.bottom);
			}
			return this.luid;
		}
		public String getUUID() {
			if (this.uuid == null)
				this.uuid = UuidHelper.getUUID(this, this.docId);
			return this.uuid;
		}
		public ImDocument getDocument() {
			return null;
		}
		public String getDocumentProperty(String propertyName) {
			return null;
		}
		public String getDocumentProperty(String propertyName, String defaultValue) {
			return defaultValue;
		}
		public String[] getDocumentPropertyNames() {
			return new String[0];
		}
		public Object setAttribute(String name, Object value) {
			if (this.type == null)
				return super.setAttribute(name, value);
			else return value;
		}
		public void copyAttributes(Attributed source) { /* we're read-only for now */ }
		public Object removeAttribute(String name) {
			return null; // we're read-only for now
		}
		public void clearAttributes() { /* we're read-only for now */ }
		
		public ImWord getFirstWord() {
			return null;
		}
		public void setFirstWord(ImWord firstWord) { /* we're read-only for now */ }
		public ImWord getLastWord() {
			return null;
		}
		public void setLastWord(ImWord lastWord) { /* we're read-only for now */ }
	}
	
	private static class GraphicsDataProvider {
		private ImDocumentData data;
		private HashMap entryNamesToDataBytes = null;
		GraphicsDataProvider(ImDocumentData data) {
			this.data = data;
		}
		InputStream getInputStream(String entryName) throws IOException {
			this.ensureInitialized();
			byte[] data = ((byte[]) this.entryNamesToDataBytes.get(entryName));
			if (data == null)
				throw new FileNotFoundException(entryName);
			return new ByteArrayInputStream(data);
		}
		private void ensureInitialized() throws IOException {
			if (this.entryNamesToDataBytes != null)
				return;
			ZipInputStream gdIn = new ZipInputStream(this.data.getInputStream(GRAPHICS_DATA_BUNDLE_ENTRY_NAME));
			HashMap entryNamesToDataHashes = new HashMap();
			HashMap dataHashesToDataBytes = new HashMap();
			for (ZipEntry ze; (ze = gdIn.getNextEntry()) != null;) {
				String gdName = ze.getName();
				if ("entryNamesToDataHashes.tsv".equals(gdName)) /* read entry name to data mapping */ {
					BufferedReader br = new BufferedReader(new InputStreamReader(gdIn, "UTF-8"));
					for (String row; (row = br.readLine()) != null;) {
						String[] mapping = row.split("\\t");
						if (mapping.length < 2)
							continue;
						entryNamesToDataHashes.put(mapping[0], mapping[1]);
					}
				}
				else /* read entry data */ {
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					byte[] buffer = new byte[1024];
					for (int r; (r = gdIn.read(buffer, 0, buffer.length)) != -1;)
						baos.write(buffer, 0, r);
					dataHashesToDataBytes.put(gdName, baos.toByteArray());
				}
			}
			gdIn.close();
			this.entryNamesToDataBytes = new HashMap();
			for (Iterator enit = entryNamesToDataHashes.keySet().iterator(); enit.hasNext();) {
				String entryName = ((String) enit.next());
				String dataHash = ((String) entryNamesToDataHashes.get(entryName));
				byte[] dataBytes = ((byte[]) dataHashesToDataBytes.get(dataHash));
				if (dataBytes != null)
					this.entryNamesToDataBytes.put(entryName, dataBytes);
			}
		}
	}
	
	static void loadSupplements(DataBoundImDocument doc, TreeMap supplementsById, ImDocumentData data, EntryVerificationLogger evl, ProgressMonitor pm) throws IOException {
		
		//	create GraphicsDataProvider if document has respective entry
		GraphicsDataProvider gdp = null;
		if (data.hasEntry(GRAPHICS_DATA_BUNDLE_ENTRY_NAME))
			gdp = new GraphicsDataProvider(data);
		
		//	read metadata of supplements (if any)
		if (data.hasEntry("supplements.tsv")) {
			TsvReader supplTsv = new TsvReader(getReader(data, "supplements.tsv", evl, pm));
			String[] record = new String[supplTsv.keys.length];
			for (int length; (length = supplTsv.fillRecord(record)) != -1;) {
				if (length < 3)
					continue;
				String supplId = record[0];
				String supplType = record[1];
				String supplMimeType = record[2];
				pm.setInfo(supplId);
				ImSupplement suppl = createSupplement(doc, data, gdp, supplId, supplType, supplMimeType);
				setAttributes(suppl, supplTsv.keys, 3, length, record, 0, null);
				if (doc != null)
					doc.addSupplement(suppl, false);
				else if (supplementsById != null)
					supplementsById.put(supplId, suppl);
			}
			supplTsv.close();
		}
		else if (data.hasEntry("supplements.csv")) {
			InputStream supplementsIn = data.getInputStream("supplements.csv");
			StringRelation supplementsData = StringRelation.readCsvData(new InputStreamReader(supplementsIn, "UTF-8"), true, null);
			supplementsIn.close();
			pm.setInfo("Adding " + supplementsData.size() + " supplements");
			for (int s = 0; s < supplementsData.size(); s++) {
				pm.setProgress((s * 100) / supplementsData.size());
				StringTupel supplementData = supplementsData.get(s);
				String sid = supplementData.getValue(ImSupplement.ID_ATTRIBUTE);
				String st = supplementData.getValue(ImSupplement.TYPE_ATTRIBUTE);
				String smt = supplementData.getValue(ImSupplement.MIME_TYPE_ATTRIBUTE);
				pm.setInfo(sid);
				ImSupplement supplement = createSupplement(doc, data, gdp, sid, st, smt);
				setAttributes(supplement, supplementData.getValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, ""));
				if (doc != null)
					doc.addSupplement(supplement, false);
				else if (supplementsById != null)
					supplementsById.put(sid, supplement);
			}
		}
	}
	private static ImSupplement createSupplement(ImDocument doc, final ImDocumentData docData, final GraphicsDataProvider gdp, String supplId, String supplType, String supplMimeType) {
		final String supplDataEntryName = (supplId + "." + supplMimeType.substring(supplMimeType.lastIndexOf('/') + "/".length()));
		if (ImSupplement.SOURCE_TYPE.equals(supplType))
			return new ImSupplement.Source(doc, supplMimeType) {
				public InputStream getInputStream() throws IOException {
					return docData.getInputStream(supplDataEntryName);
				}
			};
		else if (ImSupplement.SCAN_TYPE.equals(supplType))
			return new ImSupplement.Scan(doc, supplId, supplMimeType) {
				public InputStream getInputStream() throws IOException {
					return docData.getInputStream(supplDataEntryName);
				}
			};
		else if (ImSupplement.FIGURE_TYPE.equals(supplType))
			return new ImSupplement.Figure(doc, supplId, supplMimeType) {
				public InputStream getInputStream() throws IOException {
					return docData.getInputStream(supplDataEntryName);
				}
			};
		else if (ImSupplement.GRAPHICS_TYPE.equals(supplType))
			return new ImSupplement.Graphics(doc, supplId) {
				public InputStream getInputStream() throws IOException {
					if (gdp == null)
						return docData.getInputStream(supplDataEntryName);
					else return gdp.getInputStream(supplDataEntryName);
				}
			};
		else return new ImSupplement(doc, supplId, supplType, supplMimeType) {
			public InputStream getInputStream() throws IOException {
				return docData.getInputStream(supplDataEntryName);
			}
		};
	}
	
	private static class TsvReader {
		BufferedReader br;
		String[] keys;
		ArrayList typeOrders = null;
		TreeMap extValues = null;
		TsvReader(BufferedReader br) throws IOException {
			this(br, null);
		}
		TsvReader(BufferedReader br, String[] keys) throws IOException {
			this.br = br;
			if (keys == null) {
				String keyRow = this.br.readLine();
				this.keys = keyRow.split("\\t");
			}
			else this.keys = keys;
		}
		private String readTypeOrders(String row) throws IOException {
			this.typeOrders = new ArrayList();
			do {
				String[] typeOrder = row.split("\\t");
				if (1 < typeOrder.length)
					this.typeOrders.add(new ImObjectTypeOrder(typeOrder[1].split("\\s+"), ((2 < typeOrder.length) ? Integer.parseInt(typeOrder[2]) : -1), ((3 < typeOrder.length) ? Integer.parseInt(typeOrder[3]) : -1)));
				row = this.br.readLine();
			}
			while (row.startsWith("@TYPEORDER\t"));
			return row;
		}
		ImObjectTypeOrder[] getTypeOrders() {
			return ((this.typeOrders == null) ? null : ((ImObjectTypeOrder[]) this.typeOrders.toArray(new ImObjectTypeOrder[this.typeOrders.size()])));
		}
		private String readExtValues(String row) throws IOException {
			this.extValues = new TreeMap();
			do {
				String[] extVal = row.split("\\t");
				if (extVal.length == 3)
					this.extValues.put(("@EXTVAL:" + extVal[1]), this.unescapeValue(extVal[2], 0, extVal[2].length(), false));
				row = this.br.readLine();
			}
			while (row.startsWith("@EXTVAL\t"));
			return row;
		}
		String[] nextRecord() throws IOException {
			String row = this.br.readLine();
			if ((row == null) || (row.length() == 0))
				return null;
			if (row.startsWith("@TYPEORDER\t"))
				row = this.readTypeOrders(row);
			if (row.startsWith("@EXTVAL\t"))
				row = this.readExtValues(row);
			String[] values = row.split("\\t");
			for (int v = 0; v < values.length; v++)
				values[v] = this.unescapeValue(values[v], 0, values[v].length(), (this.extValues != null));
			return values;
		}
		int fillRecord(String[] record) throws IOException {
			return this.fillRecord(record, 0);
		}
		int fillRecord(String[] record, int from) throws IOException {
			if (from < 0)
				throw new IllegalArgumentException("Cannot fill argument array (" + record.length + ") from " + from);
			String row = this.br.readLine();
			if ((row == null) || (row.length() == 0))
				return -1;
			if (row.startsWith("@TYPEORDER\t"))
				row = this.readTypeOrders(row);
			if (row.startsWith("@EXTVAL\t"))
				row = this.readExtValues(row);
			int index = from;
			for (int start = 0, end, count = 0; start < row.length(); count++) {
				if (count == this.keys.length)
					break;
				if (record.length <= index)
					throw new IllegalArgumentException("Argument array too small (" + record.length + ") to fill with " + this.keys.length + " values starting from " + from);
				end = row.indexOf('\t', start);
				if (end == -1)
					end = row.length();
				record[index++] = this.unescapeValue(row, start, end, (this.extValues != null));
				start = (end + "\t".length());
			}
			return index;
		}
		private String unescapeValue(String row, int start, int end, boolean tryExtVal) throws IOException {
			if (end <= start)
				return null;
			if (tryExtVal && row.startsWith("@EXTVAL:", start)) {
				String extVal = ((String) this.extValues.get(row.substring(start, end)));
				if (extVal == null)
					throw new IOException("Unable to resolve external value key " + row.substring(start, end));
				else return extVal;
			}
			StringBuffer value = new StringBuffer();
			boolean escaped = false;
			for (int c = start; c < end; c++) {
				char ch = row.charAt(c);
				if (escaped) {
					if (ch == 'n')
						value.append('\n');
					else if (ch == 'r')
						value.append('\r');
					else if (ch == 't')
						value.append('\t');
					else value.append(ch);
					escaped = false;
				}
				else if (ch == '\\')
					escaped = true;
				else value.append(ch);
			}
			return value.toString();
		}
		void close() throws IOException {
			this.br.close();
		}
	}
	
	/**
	 * Load the attributes of an Image Markup document from the data provided
	 * by a document data object, but without loading the document as a whole.
	 * Namely, this method only reads the 'document.csv' entry.
	 * @param data the document data object to load the attributes from
	 * @return the attributes read from the argument data
	 * @throws IOException
	 */
	public static Attributed loadDocumentAttributes(ImDocumentData data) throws IOException {
		Attributed docAttributes = new AbstractAttributed();
		if (data.hasEntry("document.tsv")) {
			TsvReader docTsv = new TsvReader(getReader(data, "document.tsv", EntryVerificationLogger.silent, ProgressMonitor.silent));
			String[] record = new String[docTsv.keys.length];
			for (int length; (length = docTsv.fillRecord(record)) != -1;) {
				if (length < 2)
					continue;
				docAttributes.setAttribute(record[0], record[1]);
			}
			docTsv.close();
		}
		else if (data.hasEntry("document.csv")) {
			InputStream docIn = data.getInputStream("document.csv");
			StringRelation docDatas = StringRelation.readCsvData(new InputStreamReader(docIn, "UTF-8"), true, null);
			docIn.close();
			StringTupel docData = docDatas.get(0);
			setAttributes(docAttributes, docData.getValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE));
			String docId = docData.getValue(DOCUMENT_ID_ATTRIBUTE);
			if (docId != null)
				docAttributes.setAttribute(DOCUMENT_ID_ATTRIBUTE, docId);
		}
		else throw new FileNotFoundException("document.csv");
		return docAttributes;
	}
	
	private static class DataBoundImDocument extends DataBackedImDocument {
		ImDocumentData docData;
		DbidPageImageStore dbidPis;
		HashSet dirtySupplementNames = new HashSet();
		HashSet dirtyPageImageIDs = new HashSet();
		DataBoundImDocument(String docId, ImDocumentData docData) {
			super(docId);
			this.docData = docData;
		}
		
		public ImDocumentData getDocumentData() {
			return this.docData;
		}
		ImDocumentData bindToData(ImDocumentData docData) {
			ImDocumentData oldDocData = this.docData;
			this.docData = docData;
			return ((oldDocData == this.docData) ? null : oldDocData);
		}
		
		public void dispose() {
			super.dispose();
			this.dirtySupplementNames.clear();
			if (this.dbidPis != null)
				this.dbidPis.dispose();
		}
		
		public ImSupplement addSupplement(ImSupplement ims) {
			return this.addSupplement(ims, true);
		}
		ImSupplement addSupplement(ImSupplement ims, boolean isExternal) {
			if (isExternal) {
				String sfn = ims.getFileName();
				this.dirtySupplementNames.add(sfn); // mark supplement as dirty
			}
			return super.addSupplement(ims);
		}
		boolean isSupplementDirty(String sfn) {
			return this.dirtySupplementNames.contains(sfn);
		}
		void markSupplementsNotDirty() {
			this.dirtySupplementNames.clear();
		}
		
		public String storePageImage(PageImage pi, int pageId) throws IOException {
			String piStorageName = super.storePageImage(pi, pageId);
			this.dirtyPageImageIDs.add(new Integer(pageId));
			return piStorageName;
		}
		boolean isPageImageDirty(int pageId) {
			return this.dirtyPageImageIDs.contains(new Integer(pageId));
		}
		public void setPageImageSource(PageImageSource pis) {
			super.setPageImageSource(pis);
			this.dbidPis = (((pis instanceof DbidPageImageStore) && (((DbidPageImageStore) pis).doc == this)) ? ((DbidPageImageStore) pis) : null);
		}
		
		boolean isInputStreamAvailable(String entryName) {
			return this.docData.hasEntryData(entryName);
		}
		InputStream getInputStream(String entryName) throws IOException {
			return this.docData.getInputStream(entryName);
		}
		OutputStream getOutputStream(final String entryName, boolean writeDirectly) throws IOException {
			return new FilterOutputStream(this.docData.getOutputStream(entryName, writeDirectly)) {
				public void close() throws IOException {
					super.close();
					dirtySupplementNames.remove(entryName);
				}
			};
		}
	}
	
	private static class DbidPageImageStore extends AbstractPageImageStore {
		final DataBoundImDocument doc;
		final HashMap pageImageAttributesById;
		DbidPageImageStore(DataBoundImDocument doc, HashMap pageImageAttributesById) {
			this.doc = doc;
			this.doc.dbidPis = this;
			this.pageImageAttributesById = pageImageAttributesById;
		}
		public boolean isPageImageAvailable(String name) {
			if (!name.startsWith(this.doc.docId))
				return false;
			name = ("page" + name.substring(this.doc.docId.length() + ".".length()) + "." + IMAGE_FORMAT);
			return this.doc.isInputStreamAvailable(name);
		}
		public PageImageInputStream getPageImageAsStream(String name) throws IOException {
			if (!name.startsWith(this.doc.docId))
				return null;
			String piPageIdStr = name.substring(this.doc.docId.length() + ".".length());
			name = ("page" + piPageIdStr + "." + IMAGE_FORMAT);
			return this.getPageImageInputStream(this.doc.getInputStream(name), piPageIdStr);
		}
		private PageImageInputStream getPageImageInputStream(InputStream in, String piPageIdStr) throws IOException {
			
			//	test for PNG byte order mark
			PeekInputStream peekIn = new PeekInputStream(in, pngSignature.length);
			
			//	this one's the old way
			if (!peekIn.startsWith(pngSignature)) {
				PageImageInputStream piis = new PageImageInputStream(peekIn, this);
				return piis;
			}
			
			//	get attributes TODO consider parsing on demand, saving effort with large documents of which only an excerpt is used
			PageImageAttributes piAttributes = this.getPageImageAttributes(new Integer(piPageIdStr));
			if (piAttributes == null) {
				peekIn.close();
				throw new FileNotFoundException("page" + piPageIdStr + "." + IMAGE_FORMAT);
			}
			
			//	wrap and return page image input stream
			return piAttributes.wrap(peekIn, this);
		}
		public boolean storePageImage(String name, PageImage pageImage) throws IOException {
			if (!name.startsWith(this.doc.docId))
				return false;
			String piPageIdStr = name.substring(this.doc.docId.length() + ".".length());
			name = ("page" + piPageIdStr + "." + IMAGE_FORMAT);
			OutputStream out = this.doc.getOutputStream(name, true);
			pageImage.writeImage(out);
			out.close();
			Integer pageId = new Integer(piPageIdStr);
			PageImageAttributes piAttributes = this.getPageImageAttributes(pageId);
			if (piAttributes == null) {
				piAttributes = new PageImageAttributes(pageImage);
				this.pageImageAttributesById.put(pageId, piAttributes);
			}
			else piAttributes.updateAttributes(pageImage);
			return true;
		}
		public int getPriority() {
			return 10; // we only want page images for one document, but those we really do want
		}
		PageImageAttributes getPageImageAttributes(Integer pageId) {
			return ((PageImageAttributes) this.pageImageAttributesById.get(pageId));
		}
		void dispose() {
			this.pageImageAttributesById.clear();
		}
	};
	
	private static class PageImageAttributes {
		int originalWidth = -1;
		int originalHeight = -1;
		int originalDpi = -1;
		int currentDpi = -1;
		int leftEdge = 0;
		int rightEdge = 0;
		int topEdge = 0;
		int bottomEdge = 0;
		byte[] piAttributeBytes = new byte[16];
		
		//	used for loading in CSV mode
		PageImageAttributes(String piAttributes) {
			
			//	read attribute string
			String[] piAttributeNameValuePairs = piAttributes.split("[\\<\\>]");
			for (int a = 0; a < (piAttributeNameValuePairs.length-1); a+= 2) {
				if ("originalWidth".equals(piAttributeNameValuePairs[a]))
					this.originalWidth = Integer.parseInt(piAttributeNameValuePairs[a+1]);
				else if ("originalHeight".equals(piAttributeNameValuePairs[a]))
					this.originalHeight = Integer.parseInt(piAttributeNameValuePairs[a+1]);
				else if ("originalDpi".equals(piAttributeNameValuePairs[a]))
					this.originalDpi = Integer.parseInt(piAttributeNameValuePairs[a+1]);
				else if ("currentDpi".equals(piAttributeNameValuePairs[a]))
					this.currentDpi = Integer.parseInt(piAttributeNameValuePairs[a+1]);
				else if ("leftEdge".equals(piAttributeNameValuePairs[a]))
					this.leftEdge = Integer.parseInt(piAttributeNameValuePairs[a+1]);
				else if ("rightEdge".equals(piAttributeNameValuePairs[a]))
					this.rightEdge = Integer.parseInt(piAttributeNameValuePairs[a+1]);
				else if ("topEdge".equals(piAttributeNameValuePairs[a]))
					this.topEdge = Integer.parseInt(piAttributeNameValuePairs[a+1]);
				else if ("bottomEdge".equals(piAttributeNameValuePairs[a]))
					this.bottomEdge = Integer.parseInt(piAttributeNameValuePairs[a+1]);
			}
			
			//	default current DPI to original DPI if not given explicitly
			if (this.currentDpi == -1)
				this.currentDpi = this.originalDpi;
			
			//	wrap attribute values in byte array
			this.updateAttributeBytes();
		}
		
		//	used for loading in TSV mode
		PageImageAttributes(String[] piAttributes, int from, int to) {
			
			//	read attribute fields
			this.originalWidth = Integer.parseInt(piAttributes[from + 0]);
			this.originalHeight = Integer.parseInt(piAttributes[from + 1]);
			this.originalDpi = Integer.parseInt(piAttributes[from + 2]);
			if ((from + 3) < to)
				this.currentDpi = Integer.parseInt(piAttributes[from + 3]);
			if ((from + 4) < to)
				this.leftEdge = Integer.parseInt(piAttributes[from + 4]);
			if ((from + 5) < to)
				this.rightEdge = Integer.parseInt(piAttributes[from + 5]);
			if ((from + 6) < to)
				this.topEdge = Integer.parseInt(piAttributes[from + 6]);
			if ((from + 7) < to)
				this.bottomEdge = Integer.parseInt(piAttributes[from + 7]);
			
			//	default current DPI to original DPI if not given explicitly
			if (this.currentDpi == -1)
				this.currentDpi = this.originalDpi;
			
			//	wrap attribute values in byte array
			this.updateAttributeBytes();
		}
		
		PageImageAttributes(PageImage pi) {
			this.updateAttributes(pi);
		}
		
		void updateAttributes(PageImage pi) {
			this.originalWidth = pi.originalWidth;
			this.originalHeight = pi.originalHeight;
			this.originalDpi = pi.originalDpi;
			this.currentDpi = pi.currentDpi;
			this.leftEdge = pi.leftEdge;
			this.rightEdge = pi.rightEdge;
			this.topEdge = pi.topEdge;
			this.bottomEdge = pi.bottomEdge;
			this.updateAttributeBytes();
		}
		
		private void updateAttributeBytes() {
			this.piAttributeBytes[0] = ((byte) ((this.originalWidth >>> 8) & 0x000000FF));
			this.piAttributeBytes[1] = ((byte) (this.originalWidth & 0x000000FF));
			this.piAttributeBytes[2] = ((byte) ((this.originalHeight >>> 8) & 0x000000FF));
			this.piAttributeBytes[3] = ((byte) ((this.originalHeight & 0x000000FF)));
			this.piAttributeBytes[4] = ((byte) ((this.originalDpi >>> 8) & 0x000000FF));
			this.piAttributeBytes[5] = ((byte) (this.originalDpi & 0x000000FF));
			this.piAttributeBytes[6] = ((byte) ((this.currentDpi >>> 8) & 0x000000FF));
			this.piAttributeBytes[7] = ((byte) (this.currentDpi & 0x000000FF));
			this.piAttributeBytes[8] = ((byte) ((this.leftEdge >>> 8) & 0x000000FF));
			this.piAttributeBytes[9] = ((byte) (this.leftEdge & 0x000000FF));
			this.piAttributeBytes[10] = ((byte) ((this.rightEdge >>> 8) & 0x000000FF));
			this.piAttributeBytes[11] = ((byte) (this.rightEdge & 0x000000FF));
			this.piAttributeBytes[12] = ((byte) ((this.topEdge >>> 8) & 0x000000FF));
			this.piAttributeBytes[13] = ((byte) (this.topEdge & 0x000000FF));
			this.piAttributeBytes[14] = ((byte) ((this.bottomEdge >>> 8) & 0x000000FF));
			this.piAttributeBytes[15] = ((byte) (this.bottomEdge & 0x000000FF));
		}
		
		PageImageInputStream wrap(InputStream in, PageImageSource pis) throws IOException {
			return new PageImageInputStream(new SequenceInputStream(new ByteArrayInputStream(this.piAttributeBytes), in), pis);
		}
		
		String toAttributeString() {
			StringBuffer piAttributes = new StringBuffer();
			piAttributes.append("originalWidth<" + this.originalWidth + ">");
			piAttributes.append("originalHeight<" + this.originalHeight + ">");
			piAttributes.append("originalDpi<" + this.originalDpi + ">");
			if (this.currentDpi != this.originalDpi)
				piAttributes.append("currentDpi<" + this.currentDpi + ">");
			if (this.leftEdge != 0)
				piAttributes.append("leftEdge<" + this.leftEdge + ">");
			if (this.rightEdge != 0)
				piAttributes.append("rightEdge<" + this.rightEdge + ">");
			if (this.topEdge != 0)
				piAttributes.append("topEdge<" + this.topEdge + ">");
			if (this.bottomEdge != 0)
				piAttributes.append("bottomEdge<" + this.bottomEdge + ">");
			return piAttributes.toString();
		}
		
		String[] toTsvRecord(int pageId) {
			String[] piRecord = new String[9];
			Arrays.fill(piRecord, null);
			piRecord[0] = ("" + pageId);
			piRecord[1] = ("" + this.originalWidth);
			piRecord[2] = ("" + this.originalHeight);
			piRecord[3] = ("" + this.originalDpi);
			if (this.currentDpi != this.originalDpi)
				piRecord[4] = ("" + this.currentDpi);
			if (this.leftEdge != 0)
				piRecord[5] = ("" + this.leftEdge);
			if (this.rightEdge != 0)
				piRecord[6] = ("" + this.rightEdge);
			if (this.topEdge != 0)
				piRecord[7] = ("" + this.topEdge);
			if (this.bottomEdge != 0)
				piRecord[8] = ("" + this.bottomEdge);
			return piRecord;
		}
	}
	
	/* we need to hard-code the first byte 0x89 ('‰' on ISO-8859), as it
	 * converts differently on MacRoman and other encodings if embedded as
	 * part of a string constant in the code */
	private static final byte[] pngSignature = {((byte) 0x89), ((byte) 'P'), ((byte) 'N'), ((byte) 'G')};
	
	/** store a document in CSV mode */
	public static final long STORAGE_MODE_CSV = 0x0000000000000000L;
	
	/** store a document in TSV mode (to be combined with further detail parameters to control exact behavior) */
	public static final long STORAGE_MODE_TSV = 0x0000000000000001L;
	
	/** store frequent supplement attributes in dedicated columns (adaptive, TSV mode only) */
	public static final long STORAGE_MODE_TSV_SUPPLEMENT_ATTRIBUTE_COLUMNS = 0x0000000000000004L;
	
	/** bundle up data of graphics supplements to eliminate duplicates (TSV mode only) */
	public static final long STORAGE_MODE_TSV_BUNDLE_GRAPHICS_DATA = 0x0000000000000008L;
	
	/** the name of the document entry holding the bundled graphics data */
	public static final String GRAPHICS_DATA_BUNDLE_ENTRY_NAME = "graphicsData.zip";
	
	/** store frequent page attributes in dedicated columns (adaptive, TSV mode only) */
	public static final long STORAGE_MODE_TSV_PAGE_ATTRIBUTE_COLUMNS = 0x0000000000000040L;
	
	/** store frequent word attributes in dedicated columns (adaptive, TSV mode only) */
	public static final long STORAGE_MODE_TSV_WORD_ATTRIBUTE_COLUMNS = 0x0000000000000010L;
	
	/** store words in multiple chunks of smaller size (adaptive, TSV mode only); chunk size defaults to 4096, but can be set in the 4th and 3rd most significant byte of the parameters, i.e., the byte masked by <code>0x00000000FFFF0000</code> */
	public static final long STORAGE_MODE_TSV_WORD_CHUNKS = 0x0000000000000020L;
	
	/** default size of individual chunks in chunked word storage */
	public static final int STORAGE_MODE_TSV_DEFAULT_WORD_CHUNK_SIZE = 0x00001000;
	
	/** bit mask for custom word chunk sizes in chunked TSV mode storage (times 0x0100, i.e., 256) */
//	public static final long STORAGE_MODE_TSV_WORD_CHUNK_SIZE_MASK = 0x00000000FFFF0000L;
	public static final long STORAGE_MODE_TSV_WORD_CHUNK_SIZE_MASK = 0x0000000000FF0000L;
	
	/** store frequent region attributes in dedicated columns (adaptive, TSV mode only) */
	public static final long STORAGE_MODE_TSV_REGION_ATTRIBUTE_COLUMNS = 0x0000000000000100L;
	
	/** store long, frequent region attribute values in variables referenced from data columns (adaptive, TSV mode only) */
	public static final long STORAGE_MODE_TSV_EXTERNAL_REGION_ATTRIBUTES = 0x0000000000000200L;
	
	/** store regions of frequent types in separate files (adaptive, TSV mode only); threshold for dedicated files defaults to 1024, but can be set in the 5th and 5th most significant byte of the parameters, i.e., the byte masked by <code>0x0000FFFF00000000</code> */
	public static final long STORAGE_MODE_TSV_EXTERNAL_REGION_TYPES = 0x0000000000000400L;
	
	/** default minimum number of regions of a given type for these regions to be stored in a type specific file */
	public static final int STORAGE_MODE_TSV_DEFAULT_EXTERNAL_REGION_THRESHOLD = 0x00000400;
	
	/** bit mask for custom per-type region count threshold for type specific entry in TSV mode storage (times 0x0010, i.e., 16) */
//	public static final long STORAGE_MODE_TSV_EXTERNAL_REGION_COUNT_MASK = 0x0000FFFF00000000L;
	public static final long STORAGE_MODE_TSV_EXTERNAL_REGION_COUNT_MASK = 0x00000000FF000000L;
	
	/** store frequent annotation attributes in dedicated columns (adaptive, TSV mode only) */
	public static final long STORAGE_MODE_TSV_ANNOTATION_ATTRIBUTE_COLUMNS = 0x0000000000001000L;
	
	/** store long, frequent annotation attribute values in variables referenced from data columns (adaptive, TSV mode only) */
	public static final long STORAGE_MODE_TSV_EXTERNAL_ANNOTATION_ATTRIBUTES = 0x0000000000002000L;
	
	/** store annotations of frequent types in separate files (adaptive, TSV mode only); threshold for dedicated files defaults to 128, but can be set in the 8th and 7th most significant byte of the parameters, i.e., the byte masked by <code>0xFFFF000000000000</code> */
	public static final long STORAGE_MODE_TSV_EXTERNAL_ANNOTATION_TYPES = 0x0000000000004000L;
	
	/** default minimum number of annotations of a given type for these annotations to be stored in a type specific file */
	public static final int STORAGE_MODE_TSV_DEFAULT_EXTERNAL_ANNOTATION_THRESHOLD = 0x00000100;
	
	/** bit mask for custom per-type annotation count threshold for type specific entry in TSV mode storage (times 0x0010, i.e., 16) */
//	public static final long STORAGE_MODE_TSV_EXTERNAL_ANNOTATION_COUNT_MASK = 0xFFFF000000000000L;
	public static final long STORAGE_MODE_TSV_EXTERNAL_ANNOTATION_COUNT_MASK = 0x000000FF00000000L;
	
	/*
TODO quite possibly use highest bit in thresholds for type specific annotation and region tables as shift bit ...
- ... increasing multiplication factor to 0x0100/256 if set
  ==> uses factor 0x0010/16 up to 0x07FF/2047, and factor 0x0100/256 from 0x0800/2048 onward ...
  ==> ... increasing maximum threshold to 0x7FFF/32767 ...
  ==> ... as we have to reserve highest bit for storing shift ...
  ==> ... e.g. representing 0x0800/2048 as 0x90(00) (with shift bit set and last two zeros implicit)
    ==> make damn sure to properly JavaDoc this
	 */
	
	//	TODO pre-combine a set of default modes ...
	//	TODO ... most likely including one for currently hard-wired behavior
	
//	/* TODO default version switch to true after some grace period:
//	 * - all server side data converted
//	 * - TSV mode support distributed to all clients */
//	static final boolean defaultStoreTsvMode = false;
	/* TODO default version switch to true after some grace period:
	 * - all server side data converted
	 * - TSV mode support distributed to all clients */
	static final long defaultStorageModeParams = STORAGE_MODE_CSV;
	
	/**
	 * Store an Image Markup document to a file. If the argument file is an
	 * actual file, this method zips up the document and stores it in that
	 * file. If the argument file actually is a folder, on the other hand, this
	 * method does not zip up the individual entries, but stores them in the
	 * argument folder as individual files. If the argument file does not
	 * exist, it is created and treated as a file. When storing to an actual
	 * file, this method returns null. When storing to a folder, the returned
	 * array of IMF entries describes the mapping of logical file names (the
	 * entry names) to physical file names, whose name includes the MD5 of the
	 * file content. The latter is to avoid duplication to as high a degree as
	 * possible, while still preserving files that are logically overwritten.
	 * @param doc the Image Markup document to store.
	 * @param file the file or folder to store the document to
	 * @return an array describing the entries (in folder storage mode)
	 * @throws IOException
	 */
	public static ImDocumentEntry[] storeDocument(ImDocument doc, File file) throws IOException {
		return storeDocument(doc, file, defaultStorageModeParams, null);
	}
	
	/**
	 * Store an Image Markup document to a file. If the argument file is an
	 * actual file, this method zips up the document and stores it in that
	 * file. If the argument file actually is a folder, on the other hand, this
	 * method does not zip up the individual entries, but stores them in the
	 * argument folder as individual files. If the argument file does not
	 * exist, it is created and treated as a file. When storing to an actual
	 * file, this method returns null. When storing to a folder, the returned
	 * array of IMF entries describes the mapping of logical file names (the
	 * entry names) to physical file names, whose name includes the MD5 of the
	 * file content. The latter is to avoid duplication to as high a degree as
	 * possible, while still preserving files that are logically overwritten.
	 * @param doc the Image Markup document to store.
	 * @param file the file or folder to store the document to
	 * @param pm a progress monitor to observe the storage process
	 * @return an array describing the entries (in folder storage mode)
	 * @throws IOException
	 */
	public static ImDocumentEntry[] storeDocument(ImDocument doc, File file, ProgressMonitor pm) throws IOException {
		return storeDocument(doc, file, defaultStorageModeParams, pm);
	}
	
	/**
	 * Store an Image Markup document to a file. If the argument file is an
	 * actual file, this method zips up the document and stores it in that
	 * file. If the argument file actually is a folder, on the other hand, this
	 * method does not zip up the individual entries, but stores them in the
	 * argument folder as individual files. If the argument file does not
	 * exist, it is created and treated as a file. When storing to an actual
	 * file, this method returns null. When storing to a folder, the returned
	 * array of IMF entries describes the mapping of logical file names (the
	 * entry names) to physical file names, whose name includes the MD5 of the
	 * file content. The latter is to avoid duplication to as high a degree as
	 * possible, while still preserving files that are logically overwritten.
	 * @param doc the Image Markup document to store.
	 * @param file the file or folder to store the document to
	 * @param storageFlags vector of flags indicating to store the argument
	 *            document in TSV mode rather than CSV mode, and with which
	 *            options
	 * @return an array describing the entries (in folder storage mode)
	 * @throws IOException
	 */
	public static ImDocumentEntry[] storeDocument(ImDocument doc, File file, long storageFlags) throws IOException {
		return storeDocument(doc, file, storageFlags, null);
	}
	
	/**
	 * Store an Image Markup document to a file. If the argument file is an
	 * actual file, this method zips up the document and stores it in that
	 * file. If the argument file actually is a folder, on the other hand, this
	 * method does not zip up the individual entries, but stores them in the
	 * argument folder as individual files. If the argument file does not
	 * exist, it is created and treated as a file. When storing to an actual
	 * file, this method returns null. When storing to a folder, the returned
	 * array of IMF entries describes the mapping of logical file names (the
	 * entry names) to physical file names, whose name includes the MD5 of the
	 * file content. The latter is to avoid duplication to as high a degree as
	 * possible, while still preserving files that are logically overwritten.
	 * @param doc the Image Markup document to store.
	 * @param file the file or folder to store the document to
	 * @param storageFlags vector of flags indicating to store the argument
	 *            document in TSV mode rather than CSV mode, and with which
	 *            options
	 * @param pm a progress monitor to observe the storage process
	 * @return an array describing the entries (in folder storage mode)
	 * @throws IOException
	 */
	public static ImDocumentEntry[] storeDocument(ImDocument doc, File file, long storageFlags, ProgressMonitor pm) throws IOException {
		
		//	file does not exist, create it as a file
		if (!file.exists()) {
			file.getAbsoluteFile().getParentFile().mkdirs();
			file.createNewFile();
		}
		
		//	we have a directory, store document without zipping
		if (file.isDirectory()) {
			ImDocumentData data = null;
			if (doc instanceof DataBoundImDocument) {
				ImDocumentData docData = ((DataBoundImDocument) doc).docData;
				if (file.getAbsolutePath().equals(docData.getDocumentDataId()))
					data = docData;
			}
			if (data == null)
				data = new FolderImDocumentData(file, storageFlags /* constructor ignores argument flags if folder already populated */);
			return storeDocument(doc, data, pm);
		}
		
		//	we have an actual file (maybe of our own creation), zip up document
		else {
			OutputStream out = new BufferedOutputStream(new FileOutputStream(file));
			storeDocument(doc, out, storageFlags, pm);
			out.close();
			return null;
		}
	}
	
	/**
	 * Store an Image Markup document to an output stream. The argument output
	 * stream is flushed and closed at the end of this method.
	 * @param doc the Image Markup document to store.
	 * @param out the output stream to store the document to
	 * @throws IOException
	 */
	public static void storeDocument(ImDocument doc, OutputStream out) throws IOException {
		storeDocument(doc, out, defaultStorageModeParams, null);
	}
	
	/**
	 * Store an Image Markup document to an output stream. The argument output
	 * stream is flushed and closed at the end of this method.
	 * @param doc the Image Markup document to store.
	 * @param out the output stream to store the document to
	 * @param pm a progress monitor to observe the storage process
	 * @throws IOException
	 */
	public static void storeDocument(ImDocument doc, OutputStream out, ProgressMonitor pm) throws IOException {
		storeDocument(doc, out, defaultStorageModeParams, pm);
	}
	
	/**
	 * Store an Image Markup document to an output stream. The argument output
	 * stream is flushed and closed at the end of this method.
	 * @param doc the Image Markup document to store.
	 * @param out the output stream to store the document to
	 * @param storageFlags vector of flags indicating to store the argument
	 *            document in TSV mode rather than CSV mode, and with which
	 *            options
	 * @throws IOException
	 */
	public static void storeDocument(ImDocument doc, OutputStream out, long storageFlags) throws IOException {
		storeDocument(doc, out, storageFlags, null);
	}
	
	/**
	 * Store an Image Markup document to an output stream. The argument output
	 * stream is flushed and closed at the end of this method.
	 * @param doc the Image Markup document to store.
	 * @param out the output stream to store the document to
	 * @param storageFlags vector of flags indicating to store the argument
	 *            document in TSV mode rather than CSV mode, and with which
	 *            options
	 * @param pm a progress monitor to observe the storage process
	 * @throws IOException
	 */
	public static void storeDocument(ImDocument doc, OutputStream out, long storageFlags, ProgressMonitor pm) throws IOException {
		
		//	set up zipped output
		ZipOutputStream zout = new ZipOutputStream(new FilterOutputStream(out) {
			public void close() throws IOException {}
		});
		
		//	store document
		long storageTime = System.currentTimeMillis();
		ZipOutImDocumentData data = new ZipOutImDocumentData(zout, storageTime, storageFlags);
		storeDocument(doc, data, pm);
		
		//	in TSV mode, add 'entries.tsv' with size and hashes of other entries (facilitates verification on loading)
		if (0 < data.getStorageFlags()) {
			ZipEntry entry = new ZipEntry("entries.tsv");
			entry.setTime(storageTime);
			zout.putNextEntry(entry);
			BufferedWriter entryOut = new BufferedWriter(new OutputStreamWriter(zout, "UTF-8"));
			data.writeEntryList(entryOut);
			zout.closeEntry();
		}
		
		//	finally ...
		zout.flush();
		zout.close();
	}
	
	/**
	 * Store an Image Markup document to a document data object.
	 * @param doc the document to store
	 * @param data the document data object to store the document to
	 * @param pm a progress monitor observing the storage process
	 * @return an array describing the entries
	 * @throws IOException
	 */
	public static ImDocumentEntry[] storeDocument(ImDocument doc, ImDocumentData data, ProgressMonitor pm) throws IOException {
		
		//	check progress monitor
		if (pm == null)
			pm = ProgressMonitor.quiet;
		
		//	check memory (estimate 100 bytes per word, 1000 words per page, should be on the safe side)
		boolean lowMemory = (Runtime.getRuntime().freeMemory() < (100 * 1000 * doc.getPageCount()));
		if (lowMemory) {
			System.gc();
			lowMemory = (Runtime.getRuntime().freeMemory() < (100 * 1000 * doc.getPageCount()));
		}
		
		//	get storage flags (defaut to CSV mode if undefined)
		long storageFlags = data.getStorageFlags();
		if (storageFlags < 0)
			storageFlags = STORAGE_MODE_CSV;
		
		//	check mode main switch
		boolean tsvMode = ((storageFlags & STORAGE_MODE_TSV) != 0);
//		
//		//	get existing document data to help support chunking decisions in TSV mode
//		//	TODOne only do this when storing to same folder document was loaded from
//		ImDocumentData exData = ((doc instanceof DataBackedImDocument) ? ((DataBackedImDocument) doc).getDocumentData() : null);
		
		//	collect existing entry names for later cleanup
		HashSet staleEntryNames = new HashSet();
		if (tsvMode) {
			ImDocumentEntry[] exEntries = data.getEntries();
			for (int e = 0; e < exEntries.length; e++)
				staleEntryNames.add(exEntries[e].name);
		}
		
		//	get ready to zip, and to collect IMF entries
		BufferedWriter bw;
		StringVector keys = (tsvMode ? null : new StringVector());
		
		//	store document meta data
		pm.setStep("Storing document data");
		pm.setBaseProgress(0);
		pm.setMaxProgress(5);
		if (tsvMode) {
			bw = getWriter(data, "document.tsv", staleEntryNames, false);
			String[] docKeys = {"@name", "@value"};
			TsvWriter docTsv = new TsvWriter(bw, docKeys, null);
			docTsv.writeValue("@name", DOCUMENT_ID_ATTRIBUTE);
			docTsv.writeValue("@value", doc.docId);
			docTsv.endRecord();
			String[] ans = doc.getAttributeNames();
			for (int n = 0; n < ans.length; n++) {
				if (DOCUMENT_ID_ATTRIBUTE.equals(ans[n]))
					continue;
				Object value = doc.getAttribute(ans[n]);
				if (value == null)
					continue;
				docTsv.writeValue("@name", ans[n]);
				docTsv.writeValue("@value", value.toString());
				docTsv.endRecord();
			}
			docTsv.close();
		}
		else {
			bw = getWriter(data, "document.csv", null, false);
			keys.clear();
			keys.parseAndAddElements((DOCUMENT_ID_ATTRIBUTE + ";" + ImObject.ATTRIBUTES_STRING_ATTRIBUTE), ";");
			writeKeys(keys, bw);
			StringTupel docData = new StringTupel(keys.size());
			docData.setValue(DOCUMENT_ID_ATTRIBUTE, doc.docId);
			docData.setValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, getAttributesString(doc));
			writeValues(keys, docData, bw);
			bw.close();
		}
		
		//	assemble data for pages, words, and regions
		pm.setStep("Storing page data");
		pm.setBaseProgress(5);
		pm.setMaxProgress(8);
		ImPage[] pages = doc.getPages();
		if (tsvMode) {
			CountingSet allAns = new CountingSet(new TreeMap());
			addAttributeNames(pages, allAns, null);
			allAns.removeAll(ImPage.PAGE_ID_ATTRIBUTE);
			allAns.removeAll(ImPage.BOUNDING_BOX_ATTRIBUTE);
			String[] pageAns;
			Set lfPageAns;
//			boolean pageAttributeColumns = ((storageFlags & STORAGE_MODE_TSV_PAGE_ATTRIBUTE_COLUMNS) != 0);
			boolean pageAttributeColumns = usePageAttributeColumns(storageFlags);
			if (pageAttributeColumns) {
				pageAns = ((String[]) allAns.toArray(new String[allAns.elementCount()]));
				Arrays.sort(pageAns, allAns.getDecreasingCountOrder());
				lfPageAns = null;
			}
			else {
				pageAns = new String[0];
				lfPageAns = new HashSet(allAns);
			}
			String[] pageKeys = new String[2 + pageAns.length];
			pageKeys[0] = ("@" + ImPage.PAGE_ID_ATTRIBUTE);
			pageKeys[1] = ("@" + ImPage.BOUNDING_BOX_ATTRIBUTE);
			System.arraycopy(pageAns, 0, pageKeys, 2, pageAns.length);
			bw = getWriter(data, "pages.tsv", staleEntryNames, false);
			TsvWriter pageTsv = new TsvWriter(bw, pageKeys, null);
			for (int p = 0; p < pages.length; p++) {
				pm.setInfo("Page " + p);
				pm.setProgress((p * 100) / pages.length);
				pageTsv.writeValue(("@" + ImPage.PAGE_ID_ATTRIBUTE), ("" + pages[p].pageId));
				pageTsv.writeValue(("@" + ImPage.BOUNDING_BOX_ATTRIBUTE), pages[p].bounds.toString());
				pageTsv.writeAttributes(pages[p], pageAns, lfPageAns);
				pageTsv.endRecord();
			}
			pageTsv.close();
		}
		else {
			bw = getWriter(data, "pages.csv", null, false);
			keys.clear();
			keys.parseAndAddElements((PAGE_ID_ATTRIBUTE + ";" + BOUNDING_BOX_ATTRIBUTE + ";" + ImObject.ATTRIBUTES_STRING_ATTRIBUTE), ";");
			writeKeys(keys, bw);
			for (int p = 0; p < pages.length; p++) {
				pm.setInfo("Page " + p);
				pm.setProgress((p * 100) / pages.length);
				StringTupel pageData = new StringTupel(keys.size());
				pageData.setValue(PAGE_ID_ATTRIBUTE, ("" + pages[p].pageId));
				pageData.setValue(BOUNDING_BOX_ATTRIBUTE, pages[p].bounds.toString());
				pageData.setValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, getAttributesString(pages[p]));
				writeValues(keys, pageData, bw);
			}
			bw.close();
		}
		
		//	store words
		pm.setStep("Storing word data");
		pm.setBaseProgress(8);
		pm.setMaxProgress(17);
//		boolean wordAttributeColumns = (tsvMode ? ((storageFlags & STORAGE_MODE_TSV_WORD_ATTRIBUTE_COLUMNS) != 0) : false);
		boolean wordAttributeColumns = (tsvMode ? useWordAttributeColumns(storageFlags) : false);
//		boolean wordChunks = (tsvMode ? ((storageFlags & STORAGE_MODE_TSV_WORD_CHUNKS) != 0) : false);
		boolean wordChunks = (tsvMode ? useWordChunks(storageFlags) : false);
		int wordChunkSize;
		if (wordChunks) {
//			long wcs = ((storageFlags & STORAGE_MODE_TSV_WORD_CHUNK_SIZE_MASK) >>> 16);
			long wcs = getWordChunkSize(storageFlags);
			//	TODO most likely add some sort of minimum chunk size sanity check !!!
			wordChunkSize = ((wcs == 0) ? STORAGE_MODE_TSV_DEFAULT_WORD_CHUNK_SIZE : ((int) wcs));
		}
		else wordChunkSize = -1;
		//	TODOne if storing to same document data object document was loaded from, keep using whatever word chunk boundaries exist !!!
		//	==> far more economical than changing chunk bounaries and create two or more new entries as result of changing words in single chunk
		int[] wordChunkStarts = ((tsvMode && data.canReuseEntryData() && wordChunks) ? getWordChunkStarts(data, pages, wordChunkSize) : null);
		if (wordChunkStarts != null) {
			
			//	store words in chunk files
			ArrayList chunkEntryRanges = new ArrayList();
			for (int c = 1; c < wordChunkStarts.length; c++) {
				
				//	get chunk boundaries, and compose chunk entry name
				int fromPage = wordChunkStarts[c-1];
				int toPage = wordChunkStarts[c];
				String firstPageStr = ("" + pages[fromPage].pageId);
				while (firstPageStr.length() < "0000".length())
					firstPageStr = ("0" + firstPageStr);
				String lastPageStr = ("" + pages[toPage - 1].pageId);
				while (lastPageStr.length() < "0000".length())
					lastPageStr = ("0" + lastPageStr);
				String chunkEntryRange = (firstPageStr + "-" + lastPageStr);
				
				//	store words
				bw = getWriter(data, ("words." + chunkEntryRange + ".tsv"), staleEntryNames, false);
				storePageWords(pages, fromPage, toPage, wordAttributeColumns, bw, pm);
				chunkEntryRanges.add(chunkEntryRange);
			}
			
			//	store main 'words.tsv' to hold @INCLUDE commands
			String[] wordKeys = {
				("@" + ImWord.PAGE_ID_ATTRIBUTE),
				("@" + ImWord.BOUNDING_BOX_ATTRIBUTE)
			};
			bw = getWriter(data, "words.tsv", staleEntryNames, false);
			TsvWriter wordTsv = new TsvWriter(bw, wordKeys, null);
			for (int c = 0; c < chunkEntryRanges.size(); c++) {
				String chunkEntryRange = ((String) chunkEntryRanges.get(c));
				wordTsv.writeValue(("@" + ImWord.PAGE_ID_ATTRIBUTE), "@INCLUDE");
				wordTsv.writeValue(("@" + ImWord.BOUNDING_BOX_ATTRIBUTE), chunkEntryRange);
				wordTsv.endRecord();
			}
			wordTsv.close();
		}
		else if (tsvMode) {
			bw = getWriter(data, "words.tsv", staleEntryNames, false);
			storePageWords(pages, 0, pages.length, wordAttributeColumns, bw, pm);
		}
		else {
			bw = getWriter(data, "words.csv", null, lowMemory);
			keys.clear();
			keys.parseAndAddElements((PAGE_ID_ATTRIBUTE + ";" + BOUNDING_BOX_ATTRIBUTE + ";" + STRING_ATTRIBUTE + ";" + ImWord.PREVIOUS_WORD_ATTRIBUTE + ";" + ImWord.NEXT_WORD_ATTRIBUTE + ";" + ImWord.NEXT_RELATION_ATTRIBUTE + ";" + ImWord.TEXT_STREAM_TYPE_ATTRIBUTE + ";" + ImObject.ATTRIBUTES_STRING_ATTRIBUTE), ";");
			writeKeys(keys, bw);
			for (int p = 0; p < pages.length; p++) {
				pm.setInfo("Page " + p);
				pm.setProgress((p * 100) / pages.length);
				
				//	data for words
				ImWord[] pageWords = pages[p].getWords();
				Arrays.sort(pageWords, ImUtils.textStreamOrder); // some effort, but negligible in comparison to image handling, and helps a lot reading word table
				for (int w = 0; w < pageWords.length; w++) {
					StringTupel wordData = new StringTupel(keys.size());
					wordData.setValue(PAGE_ID_ATTRIBUTE, ("" + pageWords[w].pageId));
					wordData.setValue(BOUNDING_BOX_ATTRIBUTE, pageWords[w].bounds.toString());
					wordData.setValue(STRING_ATTRIBUTE, pageWords[w].getString());
					if (pageWords[w].getPreviousWord() != null)
						wordData.setValue(ImWord.PREVIOUS_WORD_ATTRIBUTE, pageWords[w].getPreviousWord().getLocalID());
					else wordData.setValue(ImWord.TEXT_STREAM_TYPE_ATTRIBUTE, pageWords[w].getTextStreamType());
					if (pageWords[w].getNextWord() != null)
						wordData.setValue(ImWord.NEXT_WORD_ATTRIBUTE, pageWords[w].getNextWord().getLocalID());
					wordData.setValue(ImWord.NEXT_RELATION_ATTRIBUTE, ("" + pageWords[w].getNextRelation()));
					wordData.setValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, getAttributesString(pageWords[w]));
					writeValues(keys, wordData, bw);
				}
			}
			bw.close();
		}
		
		//	store regions
		pm.setStep("Storing region data");
		pm.setBaseProgress(17);
		pm.setMaxProgress(20);
		if (tsvMode) {
//			boolean attributeColumns = ((storageFlags & STORAGE_MODE_TSV_REGION_ATTRIBUTE_COLUMNS) != 0);
			boolean attributeColumns = useRegionAttributeColumns(storageFlags);
//			boolean attributeExtVals = ((storageFlags & STORAGE_MODE_TSV_EXTERNAL_REGION_ATTRIBUTES) != 0);
			boolean attributeExtVals = externalizeRegionAttributeValues(storageFlags);
//			boolean typeSpecificEntries = ((storageFlags & STORAGE_MODE_TSV_EXTERNAL_REGION_TYPES) != 0);
			boolean typeSpecificEntries = useRegionTypeSpecificEntries(storageFlags);
			int typeEntryMinSize;
			if (typeSpecificEntries) {
//				long tems = ((storageFlags & STORAGE_MODE_TSV_EXTERNAL_REGION_COUNT_MASK) >>> 32);
				long tems = getRegionTypeEntrySizeThreshold(storageFlags);
				//	TODO most likely add some sort of minimum chunk size sanity check !!!
				typeEntryMinSize = ((tems == 0) ? STORAGE_MODE_TSV_DEFAULT_EXTERNAL_REGION_THRESHOLD : ((int) tems));
			}
			else typeEntryMinSize = Integer.MAX_VALUE;
			
			//	set up progress monitoring
			int allRegCount = 0;
			int outRegCount = 0;
			
			//	collect all regions, and assess frequency and nesting relationships of individual types
			ImRegionList allRegions = new ImRegionList(null);
			CountingSet allRegionTypes = null;
			CountingSet regTypePairs = null;
			CountingSet invRegTypePairs = null;
			CountingSet clashRegTypes = null;
			CountingSet clashRegTypePairs = null;
			CountingSet regClashTraces = null;
			CountingSet invClashRegTypePairs = null;
			CountingSet sizeRelRegTypePairs = null;
			CountingSet invSizeRelRegTypePairs = null;
			CountingSet rel1NregTypePairs = null;
			CountingSet relN1regTypePairs = null;
			if (typeSpecificEntries) {
				allRegionTypes = new CountingSet(new TreeMap());
				regTypePairs = new CountingSet(new TreeMap());
				invRegTypePairs = new CountingSet(new TreeMap());
				clashRegTypes = new CountingSet(new TreeMap());
				clashRegTypePairs = new CountingSet(new TreeMap());
				regClashTraces = new CountingSet(new TreeMap());
				invClashRegTypePairs = new CountingSet(new TreeMap());
				sizeRelRegTypePairs = new CountingSet(new TreeMap());
				invSizeRelRegTypePairs = new CountingSet(new TreeMap());
				rel1NregTypePairs = new CountingSet(new TreeMap());
				relN1regTypePairs = new CountingSet(new TreeMap());
			}
			for (int p = 0; p < pages.length; p++) {
				ImRegion[] pageRegs = pages[p].getRegions();
				Arrays.sort(pageRegs, ImUtils.leftRightOrder); // sort regions to keep order and thus file hash stable (3.: left to right)
				Arrays.sort(pageRegs, ImUtils.topDownOrder); // sort regions to keep order and thus file hash stable (2.: top to bottom)
				Arrays.sort(pageRegs, ImUtils.sizeOrder); // sort regions to keep order and thus file hash stable (1.: by decreasing size)
				allRegCount += pageRegs.length;
				allRegions.addAll(pageRegs);
				//	TODOne if storing to same document data object document was loaded from, keep using any extant type specific entries long as region type exists at all !!!
				//	==> far more economical to update type specific file than to re-inline into usually larger table of all remaining types
				if (typeSpecificEntries) {
					addObjectTypes(pageRegs, allRegionTypes);
					ImRegionTreeNode pageRtn = new ImRegionTreeNode(pages[p].bounds, "page");
//					System.out.println("Doing page " + p + " with " + pageRegs.length + " regions");
					for (int r = 0; r < pageRegs.length; r++)
						pageRtn.addChild(new ImRegionTreeNode(pageRegs[r].bounds, pageRegs[r].getType()));
					pageRtn.addChildTypePairs(regTypePairs, invRegTypePairs, new ArrayList());
					pageRtn.addSizeMatches(clashRegTypes, clashRegTypePairs, invClashRegTypePairs, regClashTraces, new ArrayList());
					pageRtn.addSizeNestings(sizeRelRegTypePairs, invSizeRelRegTypePairs);
					pageRtn.addMultiChildTypePairs(rel1NregTypePairs, relN1regTypePairs);
				}
			}
			
			//	store frequent region types in separate entry files
			LinkedHashSet separateEntryTypes = new LinkedHashSet();
			if (typeSpecificEntries)
				for (Iterator rtit = allRegionTypes.iterator(); rtit.hasNext();) {
					String type = ((String) rtit.next());
					if (data.hasEntry("regions." + type + ".tsv") && data.canReuseEntryData()) {} // stick with existing dedicated entry in target data object (better to have it undersized than to modify additional entries)
////					else if (allRegionTypes.getCount(type) < 768)
//					else if ((allRegionTypes.getCount(type) * 4) < (typeEntryMinSize * 3))
//						continue; // below absolute minimum (3/4 of normal threshold)
////					else if ((allRegionTypes.getCount(type) < 1024) && ((exData == null) || !exData.canReuseEntryData() || !exData.hasEntry("regions." + type + ".tsv")))
//					else if ((allRegionTypes.getCount(type) < typeEntryMinSize) && ((exData == null) || !exData.canReuseEntryData() || !exData.hasEntry("regions." + type + ".tsv")))
//						continue; // still below threshold, and not externalized in reusable way
					else if ((allRegionTypes.getCount(type) < typeEntryMinSize))
						continue; // below threshold, and not externalized in reusable way in target document data
//					TsvWriteParams regParams = getRegionTsvWriteParams(pages, type, null, attributeColumns, attributeExtVals);
					ImRegionList typeRegions = new ImRegionList(null);
					for (int r = 0; r < allRegions.size(); r++) {
						ImRegion reg = ((ImRegion) allRegions.get(r));
						if (type.equals(reg.getType())) {
							typeRegions.add(reg);
							allRegions.expunge(r);
						}
					}
					allRegions.purgeExpunged();
					TsvWriteParams regParams = getRegionTsvWriteParams(typeRegions, true, attributeColumns, attributeExtVals);
					bw = getWriter(data, ("regions." + type + ".tsv"), staleEntryNames, false);
					TsvWriter regTsv = new TsvWriter(bw, regParams.keys, regParams.extValues);
					pm.setInfo("'" + type + "' regions");
//					for (int p = 0; p < pages.length; p++) {
//						pm.setProgress((outRegCount * 100) / allRegCount);
//						ImRegion[] pageRegs = pages[p].getRegions(type);
//						Arrays.sort(pageRegs, ImUtils.leftRightOrder); // sort regions to keep order and hash stable
//						Arrays.sort(pageRegs, ImUtils.topDownOrder); // sort regions to keep order and hash stable
//						Arrays.sort(pageRegs, ImUtils.sizeOrder); // sort regions to keep order and hash stable
//						for (int r = 0; r < pageRegs.length; r++) {
//							regTsv.writeValue(("@" + ImRegion.PAGE_ID_ATTRIBUTE), ("" + pageRegs[r].pageId));
//							regTsv.writeValue(("@" + ImRegion.BOUNDING_BOX_ATTRIBUTE), pageRegs[r].bounds.toString());
//							regTsv.writeAttributes(pageRegs[r], regParams.highFreqAttribNames, null);
//							regTsv.endRecord();
//							outRegCount++;
//						}
//					}
					for (int r = 0; r < typeRegions.size(); r++) {
						pm.setProgress((outRegCount * 100) / allRegCount);
						ImRegion reg = ((ImRegion) typeRegions.get(r));
						regTsv.writeValue(("@" + ImRegion.PAGE_ID_ATTRIBUTE), ("" + reg.pageId));
						regTsv.writeValue(("@" + ImRegion.BOUNDING_BOX_ATTRIBUTE), reg.bounds.toString());
						regTsv.writeAttributes(reg, regParams.highFreqAttribNames, null);
						regTsv.endRecord();
						outRegCount++;
					}
					regTsv.close();
					separateEntryTypes.add(type);
				}
			
			//	compute region type orders if we did store any regions in dedicated files
			ImObjectTypeOrder[] regTypeOrders = null;
			if (separateEntryTypes.size() != 0)
				regTypeOrders = computeObjectTypeOrders(regClashTraces, regTypePairs, invRegTypePairs, sizeRelRegTypePairs, invSizeRelRegTypePairs, rel1NregTypePairs, relN1regTypePairs);
			
			//	store less frequent region types in central entry file ...
//			TsvWriteParams regParams = getRegionTsvWriteParams(pages, null, separateEntryTypes, attributeColumns, attributeExtVals);
			TsvWriteParams regParams = getRegionTsvWriteParams(allRegions, false, attributeColumns, attributeExtVals);
			bw = getWriter(data, "regions.tsv", staleEntryNames, lowMemory);
			TsvWriter regTsv = new TsvWriter(bw, regParams.keys, regParams.extValues, regTypeOrders);
			pm.setInfo("Remaining regions");
//			for (int p = 0; p < pages.length; p++) {
//				pm.setProgress((outRegCount * 100) / allRegCount);
//				ImRegion[] pageRegs = pages[p].getRegions();
//				Arrays.sort(pageRegs, ImUtils.sizeOrder); // sort regions to keep order and hash stable
//				for (int r = 0; r < pageRegs.length; r++) {
//					if (separateEntryTypes.contains(pageRegs[r].getType()))
//						continue;
//					regTsv.writeValue(("@" + ImRegion.TYPE_ATTRIBUTE), pageRegs[r].getType());
//					regTsv.writeValue(("@" + ImRegion.PAGE_ID_ATTRIBUTE), ("" + pageRegs[r].pageId));
//					regTsv.writeValue(("@" + ImRegion.BOUNDING_BOX_ATTRIBUTE), pageRegs[r].bounds.toString());
//					regTsv.writeAttributes(pageRegs[r], regParams.highFreqAttribNames, regParams.lowFreqAttribNames);
//					regTsv.endRecord();
//					outRegCount++;
//				}
//			}
			//	TODO sort remaining regions by any type order we just inferred to keep whole thing round trip stable
			for (int r = 0; r < allRegions.size(); r++) {
				pm.setProgress((outRegCount * 100) / allRegCount);
				ImRegion reg = ((ImRegion) allRegions.get(r));
				regTsv.writeValue(("@" + ImRegion.TYPE_ATTRIBUTE), reg.getType());
				regTsv.writeValue(("@" + ImRegion.PAGE_ID_ATTRIBUTE), ("" + reg.pageId));
				regTsv.writeValue(("@" + ImRegion.BOUNDING_BOX_ATTRIBUTE), reg.bounds.toString());
				regTsv.writeAttributes(reg, regParams.highFreqAttribNames, regParams.lowFreqAttribNames);
				regTsv.endRecord();
				outRegCount++;
			}
			
			//	... including the inclusion commands for type specific entry files
			for (Iterator rtit = separateEntryTypes.iterator(); rtit.hasNext();) {
				String type = ((String) rtit.next());
				regTsv.writeValue(("@" + ImRegion.TYPE_ATTRIBUTE), "@INCLUDE");
//				regTsv.writeValue(ImRegion.PAGE_ID_ATTRIBUTE, ("" + allRegionTypes.getCount(type)));
//				regTsv.writeValue(ImRegion.BOUNDING_BOX_ATTRIBUTE, ("regions." + type + ".tsv"));
				//	BETTER omit count in extra file (only incurs additional file changes)
				//	BETTER list region type only, full entry name is redundant
				regTsv.writeValue(("@" + ImRegion.PAGE_ID_ATTRIBUTE), type);
				regTsv.endRecord();
			}
			regTsv.close();
		}
		else {
			bw = getWriter(data, "regions.csv", null, lowMemory);
			keys.clear();
			keys.parseAndAddElements((PAGE_ID_ATTRIBUTE + ";" + BOUNDING_BOX_ATTRIBUTE + ";" + TYPE_ATTRIBUTE + ";" + ImObject.ATTRIBUTES_STRING_ATTRIBUTE), ";");
			writeKeys(keys, bw);
			for (int p = 0; p < pages.length; p++) {
				pm.setInfo("Page " + p);
				pm.setProgress((p * 100) / pages.length);
				
				//	data for regions
				ImRegion[] pageRegs = pages[p].getRegions();
				Arrays.sort(pageRegs, ImUtils.sizeOrder); // sort regions to keep order and hash stable
				for (int r = 0; r < pageRegs.length; r++) {
					StringTupel regData = new StringTupel(keys.size());
					regData.setValue(PAGE_ID_ATTRIBUTE, ("" + pageRegs[r].pageId));
					regData.setValue(BOUNDING_BOX_ATTRIBUTE, pageRegs[r].bounds.toString());
					regData.setValue(TYPE_ATTRIBUTE, pageRegs[r].getType());
					regData.setValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, getAttributesString(pageRegs[r]));
					writeValues(keys, regData, bw);
				}
			}
			bw.close();
		}
		
		//	store annotations
		pm.setStep("Storing annotation data");
		pm.setBaseProgress(20);
		pm.setMaxProgress(25);
		ImAnnotation[] annots = doc.getAnnotations();
		Arrays.sort(annots, annotationOrder); // sort annotations to keep order and hash stable
		if (tsvMode) {
//			boolean attributeColumns = ((storageFlags & STORAGE_MODE_TSV_ANNOTATION_ATTRIBUTE_COLUMNS) != 0);
			boolean attributeColumns = useAnnotationAttributeColumns(storageFlags);
//			boolean attributeExtVals = ((storageFlags & STORAGE_MODE_TSV_EXTERNAL_ANNOTATION_ATTRIBUTES) != 0);
			boolean attributeExtVals = externalizeAnnotationAttributeValues(storageFlags);
//			boolean typeSpecificEntries = ((storageFlags & STORAGE_MODE_TSV_EXTERNAL_ANNOTATION_TYPES) != 0);
			boolean typeSpecificEntries = useAnnotationTypeSpecificEntries(storageFlags);
			int typeEntryMinSize;
			if (typeSpecificEntries) {
//				long tems = ((storageFlags & STORAGE_MODE_TSV_EXTERNAL_ANNOTATION_COUNT_MASK) >>> 48);
				long tems = getAnnotationTypeEntrySizeThreshold(storageFlags);
				//	TODO most likely add some sort of minimum chunk size sanity check !!!
				typeEntryMinSize = ((tems == 0) ? STORAGE_MODE_TSV_DEFAULT_EXTERNAL_ANNOTATION_THRESHOLD : ((int) tems));
			}
			else typeEntryMinSize = Integer.MAX_VALUE;
			
			//	set up progress monitoring
			int outAnnotCount = 0;
			
			//	assess frequency and nesting relationships of individual annotation types
			CountingSet allAnnotTypes = null;
			CountingSet clashAnnotTypes = null;
			CountingSet annotTypePairs = null;
			CountingSet invAnnotTypePairs = null;
			CountingSet clashAnnotTypePairs = null;
			CountingSet invClashAnnotTypePairs = null;
			CountingSet annotClashTraces = null;
			CountingSet sizeRelAnnotTypePairs = null;
			CountingSet invSizeRelAnnotTypePairs = null;
			CountingSet rel1NannotTypePairs = null;
			CountingSet relN1annotTypePairs = null;
			//	TODOne if storing to same document data object document was loaded from, keep using any extant type specific entries long as annotation type exists at all !!!
			//	==> far more economical to update type specific file than to re-inline into usually larger table of all remaining types
			if (typeSpecificEntries) {
				LinkedHashMap rootAnnotsByTextStreamId = new LinkedHashMap();
				allAnnotTypes = new CountingSet(new TreeMap());
				for (int a = 0; a < annots.length; a++) {
					allAnnotTypes.add(annots[a].getType());
					ImWord fWord = annots[a].getFirstWord();
					ImWord lWord = annots[a].getLastWord();
					ImAnnotationTreeNode tsAtn = ((ImAnnotationTreeNode) rootAnnotsByTextStreamId.get(fWord.getTextStreamId()));
					if (tsAtn == null) {
						ImWord tsfWord = fWord;
						while (tsfWord.getPreviousWord() != null)
							tsfWord = tsfWord.getPreviousWord();
						ImWord tslWord = lWord;
						while (tslWord.getNextWord() != null)
							tslWord = tslWord.getNextWord();
						tsAtn = new ImAnnotationTreeNode(tsfWord, tslWord, fWord.getTextStreamId(), "textStreamRoot");
						rootAnnotsByTextStreamId.put(fWord.getTextStreamId(), tsAtn);
					}
					tsAtn.addChild(new ImAnnotationTreeNode(fWord, lWord, fWord.getTextStreamId(), annots[a].getType()));
				}
				clashAnnotTypes = new CountingSet(new TreeMap());
				annotTypePairs = new CountingSet(new TreeMap());
				invAnnotTypePairs = new CountingSet(new TreeMap());
				clashAnnotTypePairs = new CountingSet(new TreeMap());
				invClashAnnotTypePairs = new CountingSet(new TreeMap());
				annotClashTraces = new CountingSet(new TreeMap());
				sizeRelAnnotTypePairs = new CountingSet(new TreeMap());
				invSizeRelAnnotTypePairs = new CountingSet(new TreeMap());
				rel1NannotTypePairs = new CountingSet(new TreeMap());
				relN1annotTypePairs = new CountingSet(new TreeMap());
				for (Iterator tsit = rootAnnotsByTextStreamId.keySet().iterator(); tsit.hasNext();) {
					ImAnnotationTreeNode tsAtn = ((ImAnnotationTreeNode) rootAnnotsByTextStreamId.get(tsit.next()));
					tsAtn.addChildTypePairs(annotTypePairs, invAnnotTypePairs, new ArrayList());
					tsAtn.addSizeMatches(clashAnnotTypes, clashAnnotTypePairs, invClashAnnotTypePairs, annotClashTraces, new ArrayList());
					tsAtn.addSizeNestings(sizeRelAnnotTypePairs, invSizeRelAnnotTypePairs);
					tsAtn.addMultiChildTypePairs(rel1NannotTypePairs, relN1annotTypePairs);
				}
				if (DEBUG_OBJECT_TYPE_ODERES)
					for (Iterator tsit = rootAnnotsByTextStreamId.keySet().iterator(); tsit.hasNext();) {
						ImAnnotationTreeNode tsAtn = ((ImAnnotationTreeNode) rootAnnotsByTextStreamId.get(tsit.next()));
						System.out.println(tsAtn.textStreamId + ": " + tsAtn.children.size() + " disjoint chunks");
					}
			}
			
			//	tray up annoations
			ImAnnotationList allAnnots = new ImAnnotationList(annots, typeSpecificEntries);
			
			//	store frequent annotation types in separate entry files
			LinkedHashSet separateEntryTypes = new LinkedHashSet();
			if (typeSpecificEntries)
				for (Iterator atit = allAnnotTypes.iterator(); atit.hasNext();) {
					String type = ((String) atit.next());
					if (data.hasEntry("annotations." + type + ".tsv")) {} // stick with existing dedicated entry in target data object (better to have it undersized than to modify additional entries)
////					else if (allAnnotTypes.getCount(type) < 96)
//					else if ((allAnnotTypes.getCount(type) * 4) < (typeEntryMinSize * 3))
//						continue; // below absolute minimum (3/4 of normal threshold)
////					else if ((allAnnotTypes.getCount(type) < 128) && ((exData == null) || !exData.canReuseEntryData() || !exData.hasEntry("annotations." + type + ".tsv")))
//					else if ((allAnnotTypes.getCount(type) < typeEntryMinSize) && ((exData == null) || !exData.canReuseEntryData() || !exData.hasEntry("annotations." + type + ".tsv")))
//						continue; // still below threshold, and not externalized in reusable way
					else if ((allAnnotTypes.getCount(type) < typeEntryMinSize))
						continue; // below threshold, and not externalized in reusable way in target document data
//					ImAnnotation[] typeAnnots = doc.getAnnotations(type);
//					Arrays.sort(typeAnnots, annotationOrder); // sort annotations to keep order and hash stable
//					TsvWriteParams annotParams = getAnnotTsvWriteParams(typeAnnots, type, null, attributeColumns, attributeExtVals);
					ImAnnotationList typeAnnots = new ImAnnotationList(null);
					for (int a = 0; a < allAnnots.size(); a++) {
						ImAnnotation annot = ((ImAnnotation) allAnnots.get(a));
						if (type.equals(annot.getType())) {
							typeAnnots.add(annot);
							allAnnots.expunge(a);
						}
					}
					allAnnots.purgeExpunged();
					TsvWriteParams annotParams = getAnnotTsvWriteParams(typeAnnots, true, attributeColumns, attributeExtVals);
					bw = getWriter(data, ("annotations." + type + ".tsv"), staleEntryNames, false);
					TsvWriter annotTsv = new TsvWriter(bw, annotParams.keys, annotParams.extValues);
					pm.setInfo("'" + type + "' annotations");
//					for (int a = 0; a < typeAnnots.length; a++) {
//						pm.setProgress((outAnnotCount * 100) / annots.length);
//						annotTsv.writeValue(("@" + ImAnnotation.FIRST_WORD_ATTRIBUTE), typeAnnots[a].getFirstWord().getLocalID());
//						annotTsv.writeValue(("@" + ImAnnotation.LAST_WORD_ATTRIBUTE), typeAnnots[a].getLastWord().getLocalID());
//						annotTsv.writeAttributes(typeAnnots[a], annotParams.highFreqAttribNames, null);
//						annotTsv.endRecord();
//						outAnnotCount++;
//					}
					for (int a = 0; a < typeAnnots.size(); a++) {
						pm.setProgress((outAnnotCount * 100) / annots.length);
						ImAnnotation annot = ((ImAnnotation) typeAnnots.get(a));
						annotTsv.writeValue(("@" + ImAnnotation.FIRST_WORD_ATTRIBUTE), annot.getFirstWord().getLocalID());
						annotTsv.writeValue(("@" + ImAnnotation.LAST_WORD_ATTRIBUTE), annot.getLastWord().getLocalID());
						annotTsv.writeAttributes(annot, annotParams.highFreqAttribNames, null);
						annotTsv.endRecord();
						outAnnotCount++;
					}
					annotTsv.close();
					separateEntryTypes.add(type);
				}
			
			//	compute annotation type orders if we did store any annotation in dedicated files
			ImObjectTypeOrder[] annotTypeOrders = null;
			if (separateEntryTypes.size() != 0)
				annotTypeOrders = computeObjectTypeOrders(annotClashTraces, annotTypePairs, invAnnotTypePairs, sizeRelAnnotTypePairs, invSizeRelAnnotTypePairs, rel1NannotTypePairs, relN1annotTypePairs);
			
			//	store less frequent annotation types in central entry file ...
//			TsvWriteParams annotParams = getAnnotTsvWriteParams(annots, null, separateEntryTypes, attributeColumns, attributeExtVals);
			TsvWriteParams annotParams = getAnnotTsvWriteParams(allAnnots, false, attributeColumns, attributeExtVals);
			if (annotParams.lowFreqAttribNames != null) {
				annotParams.lowFreqAttribNames.remove(ImAnnotation.PAGE_ID_ATTRIBUTE); // we don't store this, as it's implicit from spanned words
				annotParams.lowFreqAttribNames.remove(ImAnnotation.LAST_PAGE_ID_ATTRIBUTE); // we don't store this, as it's implicit from spanned words
				annotParams.lowFreqAttribNames.remove(ImAnnotation.BOUNDING_BOX_ATTRIBUTE); // we don't store this, as it's implicit from spanned words
			}
			bw = getWriter(data, "annotations.tsv", staleEntryNames, lowMemory);
			TsvWriter annotTsv = new TsvWriter(bw, annotParams.keys, annotParams.extValues, annotTypeOrders);
			pm.setInfo("Remaining annotations");
//			for (int a = 0; a < annots.length; a++) {
//				if (separateEntryTypes.contains(annots[a].getType()))
//					continue;
//				pm.setProgress((outAnnotCount * 100) / annots.length);
//				annotTsv.writeValue(("@" + ImAnnotation.TYPE_ATTRIBUTE), annots[a].getType());
//				annotTsv.writeValue(("@" + ImAnnotation.FIRST_WORD_ATTRIBUTE), annots[a].getFirstWord().getLocalID());
//				annotTsv.writeValue(("@" + ImAnnotation.LAST_WORD_ATTRIBUTE), annots[a].getLastWord().getLocalID());
//				annotTsv.writeAttributes(annots[a], annotParams.highFreqAttribNames, annotParams.lowFreqAttribNames);
//				annotTsv.endRecord();
//				outAnnotCount++;
//			}
			//	TODO sort remaining annotations by any type order we just inferred to keep whole thing round trip stable
			for (int a = 0; a < allAnnots.size(); a++) {
				pm.setProgress((outAnnotCount * 100) / annots.length);
				ImAnnotation annot = ((ImAnnotation) allAnnots.get(a));
				annotTsv.writeValue(("@" + ImAnnotation.TYPE_ATTRIBUTE), annot.getType());
				annotTsv.writeValue(("@" + ImAnnotation.FIRST_WORD_ATTRIBUTE), annot.getFirstWord().getLocalID());
				annotTsv.writeValue(("@" + ImAnnotation.LAST_WORD_ATTRIBUTE), annot.getLastWord().getLocalID());
				annotTsv.writeAttributes(annot, annotParams.highFreqAttribNames, annotParams.lowFreqAttribNames);
				annotTsv.endRecord();
				outAnnotCount++;
			}
			
			//	... including the inclusion commands for type specific entry files
			for (Iterator rtit = separateEntryTypes.iterator(); rtit.hasNext();) {
				String type = ((String) rtit.next());
				annotTsv.writeValue(("@" + ImAnnotation.TYPE_ATTRIBUTE), "@INCLUDE");
				//	BETTER list annotation type only, full entry name is redundant
				annotTsv.writeValue(("@" + ImAnnotation.FIRST_WORD_ATTRIBUTE), type);
				annotTsv.endRecord();
			}
			annotTsv.close();
		}
		else {
			bw = getWriter(data, "annotations.csv", null, lowMemory);
			keys.clear();
			keys.parseAndAddElements((ImAnnotation.FIRST_WORD_ATTRIBUTE + ";" + ImAnnotation.LAST_WORD_ATTRIBUTE + ";" + TYPE_ATTRIBUTE + ";" + ImObject.ATTRIBUTES_STRING_ATTRIBUTE), ";");
			writeKeys(keys, bw);
			for (int a = 0; a < annots.length; a++) {
				pm.setProgress((a * 100) / annots.length);
				StringTupel annotData = new StringTupel(keys.size());
				annotData.setValue(ImAnnotation.FIRST_WORD_ATTRIBUTE, annots[a].getFirstWord().getLocalID());
				annotData.setValue(ImAnnotation.LAST_WORD_ATTRIBUTE, annots[a].getLastWord().getLocalID());
				annotData.setValue(TYPE_ATTRIBUTE, annots[a].getType());
				annotData.setValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, getAttributesString(annots[a]));
				writeValues(keys, annotData, bw);
			}
			bw.close();
		}
		
		//	store fonts (if any)
		pm.setStep("Storing font data");
		pm.setBaseProgress(25);
		pm.setMaxProgress(30);
		if (tsvMode) {
			String[] fontKeys = {
				("@" + ImFont.CHARACTER_ID_ATTRIBUTE),
				("@" + ImFont.CHARACTER_STRING_ATTRIBUTE),
				("@" + ImFont.STYLE_ATTRIBUTE),
				("@" + ImFont.CHARACTER_IMAGE_ATTRIBUTE),
				("@" + ImFont.CHARACTER_PATH_ATTRIBUTE)
			};
			bw = getWriter(data, "fonts.tsv", staleEntryNames, lowMemory);
			TsvWriter fontTsv = new TsvWriter(bw, fontKeys, null);
			ImFont[] fonts = doc.getFonts(); // no need to sort here, fonts come sorted
			for (int f = 0; f < fonts.length; f++) {
				pm.setProgress((f * 100) / fonts.length);
				String fontStyle;
				if (fonts[f].isMixedStyle())
					fontStyle = "X";
				else {
					fontStyle = "";
					if (fonts[f].isBold())
						fontStyle += "B";
					if (fonts[f].isItalics())
						fontStyle += "I";
					if (fonts[f].isSerif())
						fontStyle += "S"; // serif
					else if (fonts[f].isMonospaced())
						fontStyle += "M"; // monospaced
					else fontStyle += "G"; // sans-serif/gothic
				}
				String fontAttributes = getAttributesString(fonts[f], null);
				fontTsv.writeValue(("@" + ImFont.CHARACTER_ID_ATTRIBUTE), ((fonts[f].getCharCodeLength() == 2) ? "00" : ((fonts[f].getCharCodeLength() == 3) ? "000" : "0000")));
				fontTsv.writeValue(("@" + ImFont.CHARACTER_STRING_ATTRIBUTE), fonts[f].name);
				fontTsv.writeValue(("@" + ImFont.STYLE_ATTRIBUTE), fontStyle);
				fontTsv.writeValue(("@" + ImFont.CHARACTER_IMAGE_ATTRIBUTE), fontAttributes);
				fontTsv.endRecord();
				int[] charIDs = fonts[f].getCharacterIDs(); // no need to sort here, those IDs come sorted
				for (int c = 0; c < charIDs.length; c++) {
					String charId = Integer.toString(charIDs[c], 16).toUpperCase();
					while (charId.length() < fonts[f].getCharCodeLength())
						charId = ("0" + charId);
					fontTsv.writeValue(("@" + ImFont.CHARACTER_ID_ATTRIBUTE), charId);
					fontTsv.writeValue(("@" + ImFont.CHARACTER_STRING_ATTRIBUTE), fonts[f].getString(charIDs[c]));
					if (fonts[f].isMixedStyle()) {
						String charStyle = "";
						if (fonts[f].isBold(charIDs[c]))
							charStyle += "B";
						if (fonts[f].isItalics(charIDs[c]))
							charStyle += "I";
						if (fonts[f].isSerif(charIDs[c]))
							charStyle += "S"; // serif
						else if (fonts[f].isMonospaced(charIDs[c]))
							charStyle += "M"; // monospaced
						else charStyle += "G"; // sans-serif/gothic
						fontTsv.writeValue(("@" + ImFont.STYLE_ATTRIBUTE), charStyle);
					}
					fontTsv.writeValue(("@" + ImFont.CHARACTER_IMAGE_ATTRIBUTE), fonts[f].getImageHex(charIDs[c]));
					fontTsv.writeValue(("@" + ImFont.CHARACTER_PATH_ATTRIBUTE), fonts[f].getPathString(charIDs[c]));
					fontTsv.endRecord();
				}
			}
			fontTsv.close();
		}
		else {
			bw = getWriter(data, "fonts.csv", null, lowMemory);
			keys.clear();
			keys.parseAndAddElements((ImFont.NAME_ATTRIBUTE + ";" + ImFont.STYLE_ATTRIBUTE + ";" + ImFont.CHARACTER_ID_ATTRIBUTE + ";" + ImFont.CHARACTER_STRING_ATTRIBUTE + ";" + ImFont.CHARACTER_IMAGE_ATTRIBUTE + ";" + ImFont.CHARACTER_PATH_ATTRIBUTE), ";");
			writeKeys(keys, bw);
			ImFont[] fonts = doc.getFonts(); // no need to sort here, fonts come sorted
			for (int f = 0; f < fonts.length; f++) {
				pm.setProgress((f * 100) / fonts.length);
				String fontStyle;
				if (fonts[f].isMixedStyle())
					fontStyle = "X";
				else {
					fontStyle = "";
					if (fonts[f].isBold())
						fontStyle += "B";
					if (fonts[f].isItalics())
						fontStyle += "I";
					if (fonts[f].isSerif())
						fontStyle += "S";
					if (fonts[f].isMonospaced())
						fontStyle += "M";
				}
				String fontAttributes = getAttributesString(fonts[f]);
				if ((fontAttributes.length() != 0) || fonts[f].isMixedStyle()) {
					StringTupel fontData = new StringTupel(keys.size());
					fontData.setValue(ImFont.NAME_ATTRIBUTE, fonts[f].name);
					fontData.setValue(ImFont.STYLE_ATTRIBUTE, fontStyle);
					fontData.setValue(ImFont.CHARACTER_ID_ATTRIBUTE, ((fonts[f].getCharCodeLength() == 2) ? "00" : ((fonts[f].getCharCodeLength() == 3) ? "000" : "0000")));
					fontData.setValue(ImFont.CHARACTER_STRING_ATTRIBUTE, "");
					fontData.setValue(ImFont.CHARACTER_IMAGE_ATTRIBUTE, fontAttributes);
					writeValues(keys, fontData, bw);
				}
				int[] charIDs = fonts[f].getCharacterIDs(); // no need to sort here, those IDs come sorted
				for (int c = 0; c < charIDs.length; c++) {
					StringTupel charData = new StringTupel(keys.size());
					charData.setValue(ImFont.NAME_ATTRIBUTE, fonts[f].name);
					if (fonts[f].isMixedStyle()) {
						String charStyle = "";
						if (fonts[f].isBold(charIDs[c]))
							charStyle += "B";
						if (fonts[f].isItalics(charIDs[c]))
							charStyle += "I";
						if (fonts[f].isSerif(charIDs[c]))
							charStyle += "S";
						if (fonts[f].isMonospaced(charIDs[c]))
							charStyle += "M";
						charData.setValue(ImFont.STYLE_ATTRIBUTE, charStyle);
					}
					else charData.setValue(ImFont.STYLE_ATTRIBUTE, fontStyle);
					charData.setValue(ImFont.CHARACTER_ID_ATTRIBUTE, Integer.toString(charIDs[c], 16).toUpperCase());
					String charStr = fonts[f].getString(charIDs[c]);
					if (charStr != null)
						charData.setValue(ImFont.CHARACTER_STRING_ATTRIBUTE, charStr);
					String charImageHex = fonts[f].getImageHex(charIDs[c]);
					if (charImageHex != null)
						charData.setValue(ImFont.CHARACTER_IMAGE_ATTRIBUTE, charImageHex);
					String charPathString = fonts[f].getPathString(charIDs[c]);
					if (charPathString != null)
						charData.setValue(ImFont.CHARACTER_PATH_ATTRIBUTE, charPathString);
					writeValues(keys, charData, bw);
				}
			}
			bw.close();
		}
		
		//	store page images
		pm.setStep("Storing page images");
		pm.setBaseProgress(30);
		pm.setMaxProgress(79);
		StringRelation pageImagesDataCsv = (tsvMode ? null : new StringRelation());
		ArrayList pageImagesDataTsv = (tsvMode ? new ArrayList() : null);
		for (int p = 0; p < pages.length; p++) {
			pm.setInfo("Page " + p);
			pm.setProgress((p * 100) / pages.length);
			String piAttributes = null;
			String[] piRecord = null;
			
			//	create page image name
			String piName = PageImage.getPageImageName(doc.docId, pages[p].pageId);
			piName = ("page" + piName.substring(doc.docId.length() + ".".length()) + "." + IMAGE_FORMAT);
			
			/* we have to write the page image proper if
			 * - we're zipping up the document
			 * - the document was loaded via other facilities
			 * - the page image store has been replaced
			 * - the document was loaded from, and is still bound to, its zipped-up form
			 * - the document was loaded from, and is still bound to, a different folder
			 * - the page image was modified (so we need to write the data in any case, if not necessarily directly) ...
			 * - ... unless we're storing back to same folder document was loaded from and page image entry is virtual ...
			 * - ... i.e., actual data not present in folder (e.g. in folder only holding temporary working copy of data) */
			boolean writePi = true;
			boolean writePiDirectly = false;
			if (data instanceof ZipOutImDocumentData)
				writePiDirectly = true;
			else if (!(doc instanceof DataBoundImDocument))
				writePiDirectly = true;
			else if (((DataBoundImDocument) doc).dbidPis == null)
				writePiDirectly = true;
			else if (((DataBoundImDocument) doc).docData.getDocumentDataId() == null)
				writePiDirectly = true;
			else if (!((DataBoundImDocument) doc).docData.getDocumentDataId().equals(data.getDocumentDataId()))
				writePiDirectly = true;
			else if (!((DataBoundImDocument) doc).isPageImageDirty(pages[p].pageId))
				writePi = false;
			else if (((DataBoundImDocument) doc).docData.hasEntry(piName) && !((DataBoundImDocument) doc).docData.hasEntryData(piName))
				writePi = false;
			
			//	store page image proper if we have to
			if (writePi) {
				PageImageInputStream piis = pages[p].getPageImageAsStream();
				if (tsvMode)
					piRecord = getPageImageRecordTsv(pages[p].pageId, piis);
				else piAttributes = getPageImageAttributesCsv(piis);
				OutputStream piOut = data.getOutputStream(piName, writePiDirectly /* write directly if we know we need to */);
				byte[] pib = new byte[1024];
				for (int r; (r = piis.read(pib, 0, pib.length)) != -1;)
					piOut.write(pib, 0, r);
				piOut.close();
				piis.close();
			}
			
			//	otherwise, we only have to get the attributes (the IMF entry already exists, as otherwise, we'd write the page image)
			else {
				PageImageAttributes pia = ((DataBoundImDocument) doc).dbidPis.getPageImageAttributes(new Integer(pages[p].pageId));
				if (pia == null) {
					PageImageInputStream piis = pages[p].getPageImageAsStream();
					if (tsvMode)
						piRecord = getPageImageRecordTsv(pages[p].pageId, piis);
					else piAttributes = getPageImageAttributesCsv(piis);
					piis.close();
				}
				else if (tsvMode)
					piRecord = pia.toTsvRecord(pages[p].pageId);
				else piAttributes = pia.toAttributeString();
			}
			
			//	mark page image entry as extant
			if (staleEntryNames != null)
				staleEntryNames.remove(piName);
			
			//	add record to page image attribute table
			if (tsvMode)
				pageImagesDataTsv.add(piRecord);
			else {
				StringTupel piData = new StringTupel(keys.size());
				piData.setValue(ImObject.PAGE_ID_ATTRIBUTE, ("" + pages[p].pageId));
				piData.setValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, piAttributes);
				pageImagesDataCsv.addElement(piData);
			}
		}
		
		//	store page image data
		pm.setStep("Storing page image data");
		pm.setBaseProgress(79);
		pm.setMaxProgress(80);
		if (tsvMode) {
			String[] pageImageKeys = {
				("@" + ImObject.PAGE_ID_ATTRIBUTE),
				"@originalWidth",
				"@originalHeight",
				"@originalDpi",
				"@currentDpi",
				"@leftEdge",
				"@rightEdge",
				"@topEdge",
				"@bottomEdge",
			};
			bw = getWriter(data, "pageImages.tsv", staleEntryNames, false);
			TsvWriter pageImageTsv = new TsvWriter(bw, pageImageKeys, null);
			for (int p = 0; p < pageImagesDataTsv.size(); p++) {
				String[] piRecord = ((String[]) pageImagesDataTsv.get(p));
				int to = piRecord.length;
				while ((to != 0) && (piRecord[to-1] == null))
					to--;
				pageImageTsv.writeRecord(piRecord, to);
			}
			pageImageTsv.close();
		}
		else {
			bw = getWriter(data, "pageImages.csv", null, false);
			keys.clear();
			keys.parseAndAddElements((ImObject.PAGE_ID_ATTRIBUTE + ";" + ImObject.ATTRIBUTES_STRING_ATTRIBUTE), ";");
			writeKeys(keys, bw);
			for (int p = 0; p < pageImagesDataCsv.size(); p++)
				writeValues(keys, pageImagesDataCsv.get(p), bw);
			bw.close();
		}
		
		//	store meta data of supplements
		pm.setStep("Storing supplement data");
		pm.setBaseProgress(80);
		pm.setMaxProgress(85);
		ImSupplement[] suppls = doc.getSupplements();
		if (tsvMode) {
			CountingSet allAns = new CountingSet(new TreeMap());
			addAttributeNames(suppls, allAns, null);
			allAns.removeAll(ImSupplement.ID_ATTRIBUTE);
			allAns.removeAll(ImSupplement.TYPE_ATTRIBUTE);
			allAns.removeAll(ImSupplement.MIME_TYPE_ATTRIBUTE);
			String[] supplAns;
			Set lfSupplAns;
//			boolean suppleAttributeColumns = ((storageFlags & STORAGE_MODE_TSV_SUPPLEMENT_ATTRIBUTE_COLUMNS) != 0);
			boolean suppleAttributeColumns = useSupplementAttributeColumns(storageFlags);
			if (suppleAttributeColumns) {
				supplAns = ((String[]) allAns.toArray(new String[allAns.elementCount()]));
				Arrays.sort(supplAns, allAns.getDecreasingCountOrder());
				lfSupplAns = null;
			}
			else {
				supplAns = new String[0];
				lfSupplAns = new HashSet(allAns);
			}
			String[] supplKeys = new String[3 + supplAns.length];
			supplKeys[0] = ("@" + ImSupplement.ID_ATTRIBUTE);
			supplKeys[1] = ("@" + ImSupplement.TYPE_ATTRIBUTE);
			supplKeys[2] = ("@" + ImSupplement.MIME_TYPE_ATTRIBUTE);
			System.arraycopy(supplAns, 0, supplKeys, 3, supplAns.length);
			bw = getWriter(data, "supplements.tsv", staleEntryNames, false);
			TsvWriter supplTsv = new TsvWriter(bw, supplKeys, null);
			for (int s = 0; s < suppls.length; s++) {
				supplTsv.writeValue(("@" + ImSupplement.ID_ATTRIBUTE), suppls[s].getId());
				supplTsv.writeValue(("@" + ImSupplement.TYPE_ATTRIBUTE), suppls[s].getType());
				supplTsv.writeValue(("@" + ImSupplement.MIME_TYPE_ATTRIBUTE), suppls[s].getMimeType());
				supplTsv.writeAttributes(suppls[s], supplAns, lfSupplAns);
				supplTsv.endRecord();
			}
			supplTsv.close();
		}
		else {
			bw = getWriter(data, "supplements.csv", null, false);
			keys.clear();
			keys.parseAndAddElements((ImSupplement.ID_ATTRIBUTE + ";" + ImSupplement.TYPE_ATTRIBUTE + ";" + ImSupplement.MIME_TYPE_ATTRIBUTE + ";" + ImObject.ATTRIBUTES_STRING_ATTRIBUTE), ";");
			writeKeys(keys, bw);
			for (int s = 0; s < suppls.length; s++) {
				StringTupel supplementData = new StringTupel(keys.size());
				supplementData.setValue(ImSupplement.ID_ATTRIBUTE, suppls[s].getId());
				supplementData.setValue(ImSupplement.TYPE_ATTRIBUTE, suppls[s].getType());
				supplementData.setValue(ImSupplement.MIME_TYPE_ATTRIBUTE, suppls[s].getMimeType());
				supplementData.setValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, getAttributesString(suppls[s]));
				writeValues(keys, supplementData, bw);
			}
			bw.close();
		}
		
		//	set up zipping up graphics (in directory mode only)
//		boolean bundleGraphicsData = (tsvMode ? ((storageFlags & STORAGE_MODE_TSV_BUNDLE_GRAPHICS_DATA) != 0) : false);
		boolean bundleGraphicsData = (tsvMode ? bundleSupplementGraphicsData(storageFlags) : false);
		GraphicsDataPersister gdp = null;
		boolean graphicsDirtyOrLoose = false;
		if (bundleGraphicsData && !(data instanceof ZipOutImDocumentData)) {
			gdp = new GraphicsDataPersister(data);
			if ((doc instanceof DataBoundImDocument) && ((DataBoundImDocument) doc).docData.hasEntry(GRAPHICS_DATA_BUNDLE_ENTRY_NAME)) {
				for (int s = 0; s < suppls.length; s++)
					if (suppls[s] instanceof ImSupplement.Graphics) {
						String sfn = suppls[s].getFileName();
						if (((DataBoundImDocument) doc).isSupplementDirty(sfn))
							graphicsDirtyOrLoose = true;
					}
			}
			else graphicsDirtyOrLoose = true;
		}
		
		//	store supplements proper
		pm.setStep("Storing supplements");
		pm.setBaseProgress(85);
		pm.setMaxProgress(100);
		for (int s = 0; s < suppls.length; s++) {
			pm.setProgress((s * 100) / suppls.length);
			String sfn = suppls[s].getFileName();
			pm.setInfo("Supplement " + sfn);
			
			/* we have to write the supplement data proper if
			 * - we're zipping up the document
			 * - the document was loaded via other facilities
			 * - the document was loaded from, and is still bound to, its zipped-up form
			 * - the document was loaded from, and is still bound to, a different folder
			 * - the supplement was newly added or modified
			 * - the supplement is a graphics object and
			 *   - we're zipping up graphics and any graphics object was modified
			 *   - we're zipping up graphics, but graphics were not zipped up on loading
			 *   - we're not zipping up graphics, but graphics were zipped up on loading */
			boolean writeSupplData = false;
			if (data instanceof ZipOutImDocumentData)
				writeSupplData = true;
			else if (!(doc instanceof DataBoundImDocument))
				writeSupplData = true;
			else if (((DataBoundImDocument) doc).docData.getDocumentDataId() == null)
				writeSupplData = true;
			else if (!((DataBoundImDocument) doc).docData.getDocumentDataId().equals(data.getDocumentDataId()))
				writeSupplData = true;
			else if (((DataBoundImDocument) doc).isSupplementDirty(sfn))
				writeSupplData = true;
			else if (!(suppls[s] instanceof ImSupplement.Graphics)) {}
			else if ((gdp == null) && !((DataBoundImDocument) doc).docData.hasEntry(sfn))
				writeSupplData = true;
			else if (graphicsDirtyOrLoose)
				writeSupplData = true;
			
			//	store supplement data proper if we have to
			if (writeSupplData) {
				InputStream sdIn = suppls[s].getInputStream();
//				OutputStream sdOut = data.getOutputStream(sfn, true);
				OutputStream sdOut = (((gdp != null) && (suppls[s] instanceof ImSupplement.Graphics)) ? gdp.getOutputStream(sfn) : data.getOutputStream(sfn, true));
				byte[] sdb = new byte[1024];
				for (int r; (r = sdIn.read(sdb, 0, sdb.length)) != -1;)
					sdOut.write(sdb, 0, r);
				sdOut.close();
				sdIn.close();
			}
			
			//	clean up now-bundled entry
			if ((gdp != null) && (suppls[s] instanceof ImSupplement.Graphics) && data.hasEntry(sfn))
				data.removeEntry(sfn);
			
			//	mark supplement file as extant
			else if (staleEntryNames != null)
				staleEntryNames.remove(sfn);
		}
		
		//	finish up graphics storage
		if (gdp != null) {
			gdp.close();
			if (staleEntryNames != null)
				staleEntryNames.remove(GRAPHICS_DATA_BUNDLE_ENTRY_NAME);
		}
		
		//	clean up any obsoleted entries
		if (staleEntryNames != null) {
			for (Iterator enit = staleEntryNames.iterator(); enit.hasNext();)
				data.removeEntry((String) enit.next());
		}
		
		//	finally
		pm.setProgress(100);
		
		//	finalize folder storage
		if (data instanceof FolderImDocumentData) {
			
			//	if the document was loaded here, update IMF entries (both ways), and switch document to folder mode
			if (doc instanceof DataBoundImDocument) {
				ImDocumentData oldDocData = ((DataBoundImDocument) doc).bindToData(data);
				if (oldDocData != null)
					oldDocData.dispose(); // dispose any replaced document data
			}
			
			//	write or overwrite 'enries.txt'/'enries.tsv' (document data knows format to use)
			((FolderImDocumentData) data).storeEntryList();
		}
		
		//	return entry list for external use
		return data.getEntries();
	}
	
	private static int[] getWordChunkStarts(ImDocumentData data, ImPage[] pages, int targetChunkSize) {
		
		//	check for any existing word chunks
		if (data.hasEntry("words.tsv")) {
			ImDocumentEntry[] entries = data.getEntries();
			ArrayList wordChunkRanges = null;
			for (int e = 0; e < entries.length; e++) {
				if (!entries[e].name.startsWith("words.") || !entries[e].name.endsWith(".tsv"))
					continue;
				if ("words.tsv".equals(entries[e].name))
					continue;
				String wordChunkRange = entries[e].name;
				wordChunkRange = wordChunkRange.substring("words.".length());
				wordChunkRange = wordChunkRange.substring(0, (wordChunkRange.length() - ".tsv".length()));
				if (wordChunkRanges == null)
					wordChunkRanges = new ArrayList();
				wordChunkRanges.add(wordChunkRange);
			}
			if (wordChunkRanges != null) try {
				int[] wordChunkStarts = new int[wordChunkRanges.size() + 1];
				Collections.sort(wordChunkRanges);
				for (int r = 0; r < wordChunkRanges.size(); r++) {
					String wordChunkRange = ((String) wordChunkRanges.get(r));
					int wordChunkStartPageId = Integer.parseInt(wordChunkRange.substring(0, wordChunkRange.indexOf("-")));
					int wordChunkStartPageIndex = (wordChunkStartPageId - pages[0].pageId);
					while ((wordChunkStartPageIndex < 0) || (pages[wordChunkStartPageIndex].pageId < wordChunkStartPageId))
						wordChunkStartPageIndex++;
					while ((pages.length <= wordChunkStartPageIndex) || (wordChunkStartPageId < pages[wordChunkStartPageIndex].pageId))
						wordChunkStartPageIndex--;
					if ((wordChunkStartPageIndex < 0) || (pages.length <= wordChunkStartPageIndex)) {
						wordChunkStarts = null;
						break;
					}
					else wordChunkStarts[r] = wordChunkStartPageIndex;
				}
				if (wordChunkStarts != null) {
					wordChunkStarts[wordChunkRanges.size()] = pages.length;
					System.out.println("Word chunks boundaries recovered from " + wordChunkRanges.size() + " existing document entries");
					System.out.println(" ==> chunk starts: " + Arrays.toString(wordChunkStarts));
					return wordChunkStarts;
				}
			}
			catch (RuntimeException re) {
				System.out.println("Error recovering existing word chunk boundaries: " + re.getMessage());
				re.printStackTrace(System.out);
			}
		}
		
		//	count out words per page, collecting attribute names along the way
		int allWordCount = 0;
		int[] pageWordCounts = new int[pages.length];
		for (int p = 0; p < pages.length; p++) {
			ImWord[] pageWords = pages[p].getWords();
			allWordCount += pageWords.length;
			pageWordCounts[p] = pageWords.length;
		}
		System.out.println("Got " + allWordCount + " words overall: " + Arrays.toString(pageWordCounts));
		
		//	compute ideal chunk size
		int wordChunkCount = ((allWordCount + (targetChunkSize / 2)) / targetChunkSize);
		if (wordChunkCount < 3)
			return null; // no use chunking with so few parts
		int wordChunkSize = ((allWordCount + (wordChunkCount / 2)) / wordChunkCount);
		System.out.println(" ==> aiming for " + wordChunkCount + " chunks sized about " + wordChunkSize);
		
		//	count out chunking
		int[] wordChunkStarts = new int[wordChunkCount + 1];
		wordChunkStarts[0] = 0;
		wordChunkStarts[wordChunkCount] = pages.length; // saves lots of edge case handling below
		int[] wordChunkSizes = new int[wordChunkCount];
		int chunkSize = 0;
		int chunkIndex = 0;
		int chunkedWordCount = 0;
		int chunkedWordTarget = ((allWordCount * (chunkIndex + 1)) / wordChunkCount);
		chunkSize = pageWordCounts[0];
		chunkedWordCount += pageWordCounts[0];
		System.out.println(" - adding page " + 0 + " with " + pageWordCounts[0] + " words");
		System.out.println(" --> started new chunk with " + chunkSize + " words, aiming for total of " + chunkedWordTarget + " words");
		for (int p = 1; p < pages.length; p++) {
			System.out.println(" - adding page " + p + " with " + pageWordCounts[p] + " words");
			int underRun = (chunkedWordTarget - chunkedWordCount);
			System.out.println("   - currentyl " + underRun + " short of current target");
			int overRun = ((chunkedWordCount + pageWordCounts[p]) - chunkedWordTarget);
			System.out.println("   - adding page to current chunk would be " + overRun + " beyond current target");
			if (underRun < overRun) {
				wordChunkSizes[chunkIndex] = chunkSize;
				System.out.println(" --> finished chunk " + chunkIndex + " before page " + p + " at " + wordChunkSizes[chunkIndex] + " words, total of " + chunkedWordCount + " words");
				chunkIndex++;
				wordChunkStarts[chunkIndex] = p;
				chunkSize = pageWordCounts[p];
				chunkedWordCount += pageWordCounts[p];
				chunkedWordTarget = ((allWordCount * (chunkIndex + 1)) / wordChunkCount);
				System.out.println(" --> started new chunk with " + chunkSize + " words, aiming for total of " + chunkedWordTarget + " words");
			}
			else {
				chunkSize += pageWordCounts[p];
				chunkedWordCount += pageWordCounts[p];
				System.out.println(" --> included in current chunk, now at " + chunkSize + " words");
			}
		}
		wordChunkSizes[chunkIndex] = chunkSize;
		System.out.println(" --> finished chunk " + chunkIndex + " before page " + pages.length + " at " + wordChunkSizes[chunkIndex] + " words");
		System.out.println(" ==> chunk starts: " + Arrays.toString(wordChunkStarts));
		System.out.println(" ==> chunk sizes: " + Arrays.toString(wordChunkSizes));
		return wordChunkStarts;
	}
	private static void storePageWords(ImPage[] pages, int fromPage, int toPage, boolean attributeColumns, BufferedWriter bw, ProgressMonitor pm) throws IOException {
		
		//	collect attributes
		CountingSet allAns = new CountingSet(new TreeMap());
		for (int p = fromPage; p < toPage; p++) {
			ImWord[] pageWords = pages[p].getWords();
			addAttributeNames(pageWords, allAns, null);
		}
		allAns.removeAll(ImWord.PAGE_ID_ATTRIBUTE);
		allAns.removeAll(ImWord.BOUNDING_BOX_ATTRIBUTE);
		allAns.removeAll(ImWord.STRING_ATTRIBUTE);
		allAns.removeAll(ImWord.PREVIOUS_WORD_ATTRIBUTE);
		allAns.removeAll(ImWord.NEXT_WORD_ATTRIBUTE);
		allAns.removeAll(ImWord.NEXT_RELATION_ATTRIBUTE);
		allAns.removeAll(ImWord.TEXT_STREAM_TYPE_ATTRIBUTE);
		String[] wordAns;
		Set lfWordAns;
		if (attributeColumns) {
			wordAns = ((String[]) allAns.toArray(new String[allAns.elementCount()]));
			Arrays.sort(wordAns, allAns.getDecreasingCountOrder());
			lfWordAns = null;
		}
		else {
			wordAns = new String[0];
			lfWordAns = new HashSet(allAns);
		}
		
		//	set up output keys
		String[] wordKeys = new String[7 + wordAns.length + ((lfWordAns == null) ? 0 : 1)];
		wordKeys[0] = ("@" + ImWord.PAGE_ID_ATTRIBUTE);
		wordKeys[1] = ("@" + ImWord.BOUNDING_BOX_ATTRIBUTE);
		wordKeys[2] = ("@" + ImWord.STRING_ATTRIBUTE);
		wordKeys[3] = ("@" + ImWord.PREVIOUS_WORD_ATTRIBUTE);
		wordKeys[4] = ("@" + ImWord.NEXT_WORD_ATTRIBUTE);
		wordKeys[5] = ("@" + ImWord.NEXT_RELATION_ATTRIBUTE);
		wordKeys[6] = ("@" + ImWord.TEXT_STREAM_TYPE_ATTRIBUTE);
		if (lfWordAns == null)
			System.arraycopy(wordAns, 0, wordKeys, 7, wordAns.length);
		else wordKeys[7] = ("@" + ImWord.ATTRIBUTES_STRING_ATTRIBUTE);
		TsvWriter wordTsv = new TsvWriter(bw, wordKeys, null);
		
		//	store words
		for (int p = fromPage; p < toPage; p++) {
			pm.setInfo("Page " + p);
			pm.setProgress((p * 100) / pages.length);
			ImWord[] pageWords = pages[p].getWords();
			Arrays.sort(pageWords, ImUtils.textStreamOrder); // some effort, but negligible in comparison to image handling, and helps a lot reading word table
			for (int w = 0; w < pageWords.length; w++) {
				wordTsv.writeValue(("@" + ImWord.PAGE_ID_ATTRIBUTE), ("" + pageWords[w].pageId));
				wordTsv.writeValue(("@" + ImWord.BOUNDING_BOX_ATTRIBUTE), pageWords[w].bounds.toString());
				wordTsv.writeValue(("@" + ImWord.STRING_ATTRIBUTE), pageWords[w].getString());
				ImWord prevWord = pageWords[w].getPreviousWord();
				if (prevWord != null)
					wordTsv.writeValue(("@" + ImWord.PREVIOUS_WORD_ATTRIBUTE), prevWord.getLocalID());
				ImWord nextWord = pageWords[w].getNextWord();
				if (nextWord != null)
					wordTsv.writeValue(("@" + ImWord.NEXT_WORD_ATTRIBUTE), nextWord.getLocalID());
				wordTsv.writeValue(("@" + ImWord.NEXT_RELATION_ATTRIBUTE), ("" + pageWords[w].getNextRelation()));
				if (prevWord == null)
					wordTsv.writeValue(("@" + ImWord.TEXT_STREAM_TYPE_ATTRIBUTE), pageWords[w].getTextStreamType());
				wordTsv.writeAttributes(pageWords[w], wordAns, lfWordAns);
				wordTsv.endRecord();
			}
		}
		wordTsv.close();
	}
	
	private static abstract class ImObjectList implements Comparator {
		private ImObjectTypeOrder[] typeOrders;
		private ImObject[] objects;
		private int size = 0;
		private int expunged = 0;
		ImObjectList(ImObjectTypeOrder[] iotos) /* used on loading */ {
			this(iotos, 128); // rather large initial size for a list, but we're dealing with whole documents ...
		}
		ImObjectList(ImObjectTypeOrder[] iotos, int capacity) /* used on loading */ {
			this.typeOrders = iotos;
			this.objects = new ImObject[capacity];
		}
		ImObjectList(ImObject[] imos, boolean copy) /* used on storing */ {
			this.typeOrders = null;
			if (copy) {
				this.objects = new ImObject[imos.length];
				System.arraycopy(imos, 0, this.objects, 0, imos.length);
			}
			else this.objects = imos;
			this.size = this.objects.length;
		}
		int size() {
			return this.size;
		}
		void add(ImObject imo) {
			this.ensureFreeSpace(1);
			this.objects[this.size++] = imo;
		}
		void addAll(ImObject[] imos) {
			this.ensureFreeSpace(imos.length);
			System.arraycopy(imos, 0, this.objects, this.size, imos.length);
			this.size += imos.length;
		}
		private void ensureFreeSpace(int space) {
			int requiredLength = (this.size + space);
			if (requiredLength <= this.objects.length)
				return;
			int factor = 2;
			while ((this.objects.length * factor) < requiredLength)
				factor *= 2;
			ImObject[] cObjects = new ImObject[this.objects.length * factor];
			System.arraycopy(this.objects, 0, cObjects, 0, this.size);
			this.objects = cObjects;
		}
		ImObject get(int index) {
			return this.objects[index];
		}
		void expunge(int index) {
			if (this.objects[index] == null)
				return;
			this.objects[index] = null;
			this.expunged++;
		}
		void purgeExpunged() {
			if (this.expunged == 0)
				return;
			for (int o = 0, s = 0; o < this.size; o++) {
				if (this.objects[o] == null)
					s++;
				else if (s != 0)
					this.objects[o - s] = this.objects[o];
			}
			Arrays.fill(this.objects, (this.size - this.expunged), this.size, null);
			this.size -= this.expunged;
			this.expunged = 0;
		}
		public int compare(Object obj1, Object obj2) {
			return this.comparePosition(((ImObject) obj1), ((ImObject) obj2));
		}
		abstract int comparePosition(ImObject imo1, ImObject imo2);
		void sort() {
			
			 // sort by position first ...
			Arrays.sort(this.objects, 0, this.size, this);
			
			//	... and then order positionally equal objects by type
			for (int o = 1; o < this.size; o++) {
				
				//	anything to sort here?
				if (this.comparePosition(this.objects[o-1], this.objects[o]) != 0)
					continue;
				
				//	gather whole group of positionally clashing objects
				int cgs = (o-1);
				int cge = (o+1);
				while ((cge < this.size) && (this.comparePosition(this.objects[cgs], this.objects[cge]) == 0))
					cge++;
				
				//	tray up object types and find type order
				String[] cgots = new String[cge - cgs];
				for (int co = cgs; co < cge; co++)
					cgots[co - cgs] = this.objects[co].getType();
				ImObjectTypeOrder ioto = null;
				if (this.typeOrders == null) // TODOne fall back to lexicographic order instead in deployment !!!
					throw new IllegalStateException("Cannot find order for object types " + Arrays.toString(cgots));
				else for (int to = 0; to < this.typeOrders.length; to++)
					if (this.typeOrders[to].canOrder(cgots)) {
						ioto = this.typeOrders[to];
						break;
					}
				//	TODO try and aggregate available type orders until clash group pair wise comparable
				if (ioto == null)
					ioto = this.createAggregateTypeOrder(cgots);
				if (ioto == null) // TODOne fall back to lexicographic order instead in deployment !!!
					throw new IllegalStateException("Cannot find order for object types " + Arrays.toString(cgots));
				
				//	sort group of positionally clashing objects by type (no use going all-out TimSort on some 2-5 objects)
//				Arrays.sort(this.objects, cgs, cge, ioto);
				this.sortByType(cgs, cge, ioto);
				
				//	we're done with whole group, jump to last involved object (but _not_ after it, loop increment does that)
				o = (cge-1);
			}
		}
		private ImObjectTypeOrder createAggregateTypeOrder(String[] cgots) {
			ImObjectTypeOrder ioto = ImDocumentIO.createAggregateTypeOrder(this.typeOrders, cgots, true /* allow using lexicographic fallback as last resort on loading */);
			if (ioto != null) {
				if (DEBUG_OBJECT_TYPE_ODERES) System.out.println("Inferred order for object types " + Arrays.toString(cgots) + ": " + ioto.toString());
				ImObjectTypeOrder[] typeOrders = new ImObjectTypeOrder[this.typeOrders.length + 1];
				System.arraycopy(this.typeOrders, 0, typeOrders, 0, this.typeOrders.length);
				typeOrders[this.typeOrders.length] = ioto;
				this.typeOrders = typeOrders;
			}
			return ioto;
		}
		private void sortByType(int from, int to, ImObjectTypeOrder ioto) {
			boolean changed;
			do {
				changed = false;
				for (int o = (from+1); o < to; o++)
					if (0 < ioto.compare(this.objects[o-1], this.objects[o])) {
						ImObject imo = this.objects[o-1];
						this.objects[o-1] = this.objects[o];
						this.objects[o] = imo;
						changed = true;
					}
				/* no use even going bidirectional with 2-5 objects, as control
				 * structure overhead outweighs benefits with usual range size
				 * more towards 2 or 3 objects ... */
			} while (changed);
		}
	}
	
	static ImObjectTypeOrder createAggregateTypeOrder(ImObjectTypeOrder[] typeOrders, String[] cgots, boolean allowStringComp) {
		return createAggregateTypeOrder(typeOrders, cgots, allowStringComp, false);
	}
	private static ImObjectTypeOrder createAggregateTypeOrder(ImObjectTypeOrder[] typeOrders, String[] cgots, boolean allowStringComp, boolean useStringComp) {
		if (DEBUG_OBJECT_TYPE_ODERES) System.out.println("Creating aggregate object type order for " + Arrays.toString(cgots));
		ArrayList cgotList = new ArrayList(Arrays.asList(cgots));
		ArrayList orderedCgots = new ArrayList();
		while (1 < cgotList.size()) {
			if (DEBUG_OBJECT_TYPE_ODERES) System.out.println(" - seeking minimum in " + cgotList);
			ArrayList minCgots = new ArrayList();
			for (int t = 0; t < cgotList.size(); t++) {
				String ot = ((String) cgotList.get(t));
				if (DEBUG_OBJECT_TYPE_ODERES) System.out.println("   - testing '" + ot + "'");
				int otlCount = 0;
				int otgCount = 0;
				for (int ct = 0; ct < cgotList.size(); ct++) {
					if (ct == t)
						continue;
					String cot = ((String) cgotList.get(ct));
					String[] cgotp = {ot, cot};
					int otpComp = 0;
					for (int to = 0; to < typeOrders.length; to++)
						if (typeOrders[to].canOrder(cgotp)) {
							int otPos = typeOrders[to].getPosition(ot);
							int cotPos = typeOrders[to].getPosition(cot);
							if ((otPos == -1) || (cotPos == -1))
								continue;
							otpComp = (otPos - cotPos);
							if (DEBUG_OBJECT_TYPE_ODERES) {
								if (otpComp < 0)
									System.out.println("     - less than '" + cot + "' according to " + typeOrders[to]);
								else if (0 < otpComp)
									System.out.println("     - greater than '" + cot + "' according to " + typeOrders[to]);
							}
							break;
						}
					if (otpComp == 0) {} // no direct comparison between these two, but we might infer transitive one
					else if (otpComp < 0)
						otlCount++;
					else if (0 < otpComp)
						otgCount++;
				}
				if ((otgCount == 0) && (otlCount != 0)) {
					minCgots.add(ot);
					if (DEBUG_OBJECT_TYPE_ODERES) System.out.println("     ==> found minimum: '" + ot + "'");
				}
			}
			if (minCgots.isEmpty()) {
				if (DEBUG_OBJECT_TYPE_ODERES) System.out.println("   ==> no minimum found at all");
				break; // no minimum found at all (note: 'minimum' is larger object, which takes smaller/earlier position in nesting order)
			}
			else if (minCgots.size() == 1) {
				String minOt = ((String) minCgots.get(0));
				orderedCgots.add(minOt);
				cgotList.remove(minOt);
				if (DEBUG_OBJECT_TYPE_ODERES) System.out.println("   ==> found unambiguous minimum: '" + minOt + "'");
			}
			else if (useStringComp) /* down to lexicographical fallback */ {
				Collections.sort(minCgots);
				cgotList.removeAll(minCgots);
				orderedCgots.addAll(minCgots);
				if (DEBUG_OBJECT_TYPE_ODERES) System.out.println("   ==> found " + minCgots.size() + " ambiguous minimums, ordered as " + minCgots);
			}
			else {
				if (DEBUG_OBJECT_TYPE_ODERES) System.out.println("   ==> found " + minCgots.size() + " ambiguous minimums: " + minCgots);
				break; // no unambiguous minimum found (note: 'minimum' is larger object, which takes smaller/earlier position in nesting order)
			}
		}
		if (cgotList.size() < 2) /* at most sole maximum type remaining, we have total order */ {
			orderedCgots.addAll(cgotList);
			return new ImObjectTypeOrder(((String[]) orderedCgots.toArray(new String[orderedCgots.size()])), 0, 0);
		}
		else if (useStringComp) {
			Collections.sort(cgotList);
			orderedCgots.addAll(cgotList);
			return new ImObjectTypeOrder(((String[]) orderedCgots.toArray(new String[orderedCgots.size()])), 0, 0);
		}
		else if (allowStringComp)
			return createAggregateTypeOrder(typeOrders, cgots, true, true);
		else return null;
	}
	
	private static class ClashObjectTypeTray implements Comparable {
		final String type;
		int rel1Npos = 0;
		int sizePos = 0;
		int freqPos = 0;
		int pos = 0;
		ClashObjectTypeTray(String type) {
			this.type = type;
		}
		public int compareTo(Object obj) {
			return (this.pos - ((ClashObjectTypeTray) obj).pos);
		}
	}
	
	private static class ImObjectTypeOrder implements Comparator, Comparable {
		final String[] types;
		int strictness;
		int weight;
		ImObjectTypeOrder(String[] types, int strictness, int weight) {
			this.types = types;
			this.strictness = strictness;
			this.weight = weight;
		}
		public int compareTo(Object obj) {
			int c = (((ImObjectTypeOrder) obj).types.length - this.types.length);
			if (c == 0)
				c = (((ImObjectTypeOrder) obj).weight - this.weight); // descending frequency order
			if (c == 0)
				c = this.types[0].compareTo(((ImObjectTypeOrder) obj).types[0]);
			if (c != 0)
				return c;
			for (int t = 1; t < this.types.length; t++) {
				c = this.types[t].compareTo(((ImObjectTypeOrder) obj).types[t]);
				if (c != 0)
					break;
			}
			return c;
		}
		boolean subsumes(ImObjectTypeOrder ioto) /* same types, same order */ {
			int lastPos = -1;
			for (int t = 0; t < ioto.types.length; t++) {
				int pos = this.getPosition(ioto.types[t]);
				if (lastPos < pos) // we're OK moving forward from previous type ...
					lastPos = pos;
				else return false; // ... but only then
			}
			return true;
		}
		boolean contains(ImObjectTypeOrder ioto) /* same types, independent of order */ {
			for (int t = 0; t < ioto.types.length; t++) {
				int pos = this.getPosition(ioto.types[t]);
				if (pos == -1)
					return false;
			}
			return true;
		}
//		boolean overlaps(ImObjectTypeOrder ioto) /* at least two types in common, in same order */ {
//			for (int t = 0; t < ioto.types.length; t++) {
//				int pos = this.getPosition(ioto.types[t]);
//				if (pos == -1)
//					continue;
//				for (int ct = (t+1); ct < ioto.types.length; ct++) {
//					int cPos = this.getPosition(ioto.types[ct]);
//					if (pos < cPos)
//						return true;
//				}
//			}
//			return false;
//		}
		boolean overlaps(ImObjectTypeOrder ioto) /* at least one type in common */ {
			for (int t = 0; t < ioto.types.length; t++) {
				int pos = this.getPosition(ioto.types[t]);
				if (pos != -1)
					return true;
			}
			return false;
		}
		//	TODO also check for prefix/suffix arrangement compatibitity
		int getPosition(String type) {
			/* While linear search in theoryy is far slower than a hash lookup,
			 * for 2-5 types it is way faster, as hash also has linear
			 * collision resolution, and we hardly ever have clash groups
			 * larger than that. */
			for (int t = 0; t < this.types.length; t++) {
				if (this.types[t].equals(type))
					return t;
			}
			return -1;
		}
		boolean canOrder(String[] iots) {
			for (int t = 0; t < iots.length; t++) {
				int pos = this.getPosition(iots[t]);
				if (pos == -1)
					return false;
			}
			return true;
		}
		public int compare(Object obj1, Object obj2) {
			int pos1 = this.getPosition(((ImObject) obj1).getType());
			int pos2 = this.getPosition(((ImObject) obj2).getType());
			return (((pos1 == -1) || (pos2 == -1)) ? 0 : (pos1 - pos2));
		}
		public String toString() {
			StringBuffer str = new StringBuffer(this.types[0]);
			for (int t = 1; t < this.types.length; t++) {
				str.append(" ");
				str.append(this.types[t]);
			}
			return str.toString();
		}
	}
	
	//	TODO shut this thing up via debug flag
	private static final boolean DEBUG_OBJECT_TYPE_ODERES = false;
	private static ImObjectTypeOrder[] computeObjectTypeOrders(CountingSet clashTraces,
			CountingSet typePairs, CountingSet invTypePairs,
//			CountingSet clashTypePairs, CountingSet invClashTypePairs,
			CountingSet sizeRelTypePairs, CountingSet invSizeRelTypePairs,
			CountingSet rel1NtypePairs, CountingSet relN1typePairs
		) {
		ArrayList typeOrders = new ArrayList();
		for (Iterator ctit = clashTraces.iterator(); ctit.hasNext();) {
			String clashTrace = ((String) ctit.next());
			if (DEBUG_OBJECT_TYPE_ODERES) System.out.println(" - " + clashTrace + ": " + clashTraces.getCount(clashTrace));
			String[] ctTypes = clashTrace.split("\\s*\\-\\>\\s*"); // TODO use more efficient separator string
			ClashObjectTypeTray[] clashTypes = new ClashObjectTypeTray[ctTypes.length];
			for (int t = 0; t < ctTypes.length; t++)
				clashTypes[t] = new ClashObjectTypeTray(ctTypes[t]);
			for (int t = 0; t < clashTypes.length; t++) {
				if (DEBUG_OBJECT_TYPE_ODERES) System.out.println(clashTypes[t].type + ":");
				for (int ct = 0; ct < clashTypes.length; ct++) {
					if (ct == t)
						continue;
					String typePair = (clashTypes[t].type + " -> " + clashTypes[ct].type);
					int rel1Nscore = rel1NtypePairs.getCount(typePair);
					int invRel1Nscore = relN1typePairs.getCount(typePair);
					if (rel1Nscore < invRel1Nscore) {
						clashTypes[t].rel1Npos++;
						clashTypes[ct].rel1Npos--;
					}
					else if (invRel1Nscore < rel1Nscore) {
						clashTypes[t].rel1Npos--;
						clashTypes[ct].rel1Npos++;
					}
					int sizeScore = sizeRelTypePairs.getCount(typePair);
					int invSizeScore = invSizeRelTypePairs.getCount(typePair);
					if (sizeScore < invSizeScore) {
						clashTypes[t].sizePos++;
						clashTypes[ct].sizePos--;
					}
					else if (invSizeScore < sizeScore) {
						clashTypes[t].sizePos--;
						clashTypes[ct].sizePos++;
					}
					int freqScore = typePairs.getCount(typePair);
					int invFreqScore = invTypePairs.getCount(typePair);
					if (freqScore < invFreqScore) {
						clashTypes[t].freqPos++;
						clashTypes[ct].freqPos--;
					}
					else if (invFreqScore < freqScore) {
						clashTypes[t].freqPos--;
						clashTypes[ct].freqPos++;
					}
					if (DEBUG_OBJECT_TYPE_ODERES) System.out.println(" - " + clashTypes[ct].type + ": 1:N " + rel1Nscore + "/" + invRel1Nscore + ", size " + sizeScore + "/" + invSizeScore + ", count " + freqScore + "/" + invFreqScore);
				}
			}
			if (DEBUG_OBJECT_TYPE_ODERES) {
				System.out.println("RAW ORDER:");
				for (int t = 0; t < clashTypes.length; t++)
					System.out.println(" - " + clashTypes[t].type + " " + clashTypes[t].rel1Npos + " " + clashTypes[t].sizePos + " " + clashTypes[t].freqPos);
			}
			boolean orderWellDefined;
			for (int t = 0; t < clashTypes.length; t++)
				clashTypes[t].pos = clashTypes[t].rel1Npos;
			Arrays.sort(clashTypes);
			if (DEBUG_OBJECT_TYPE_ODERES) {
				System.out.println("1:N ORDER:");
				for (int t = 0; t < clashTypes.length; t++)
					System.out.println(" - " + clashTypes[t].type + " " + clashTypes[t].rel1Npos + " " + clashTypes[t].sizePos + " " + clashTypes[t].freqPos);
			}
			orderWellDefined = true;
			for (int t = 1; t < clashTypes.length; t++) {
				if (clashTypes[t-1].pos < clashTypes[t].pos)
					continue;
				if (DEBUG_OBJECT_TYPE_ODERES) System.out.println("ORDER UNDEFINED: " + clashTypes[t-1].type + " vs. " + clashTypes[t].type);
				String typePair = (clashTypes[t-1].type + " -> " + clashTypes[t].type);
				if (DEBUG_OBJECT_TYPE_ODERES) System.out.println(" ==> ACTUAL PAIRINGS: " + typePairs.getCount(typePair) + ", " + invTypePairs.getCount(typePair) + " reverse");
//				System.out.println(" ==> ACTUAL CLASHES: " + clashTypePairs.getCount(typePair) + ", " + invClashTypePairs.getCount(typePair) + " reverse");
				orderWellDefined = false; // refuse using 1:N scores for ambiguity
			}
			if (orderWellDefined && (ctTypes != null)) {
				for (int t = 0; t < clashTypes.length; t++)
					ctTypes[t] = clashTypes[t].type;
				typeOrders.add(new ImObjectTypeOrder(ctTypes, 3, clashTraces.getCount(clashTrace)));
				ctTypes = null;
			}
			for (int t = 0; t < clashTypes.length; t++)
				clashTypes[t].pos = clashTypes[t].sizePos;
			Arrays.sort(clashTypes);
			if (DEBUG_OBJECT_TYPE_ODERES) {
				System.out.println("SIZE ORDER:");
				for (int t = 0; t < clashTypes.length; t++)
					System.out.println(" - " + clashTypes[t].type + " " + clashTypes[t].rel1Npos + " " + clashTypes[t].sizePos + " " + clashTypes[t].freqPos);
			}
			orderWellDefined = true;
			for (int t = 1; t < clashTypes.length; t++) {
				if (clashTypes[t-1].pos < clashTypes[t].pos)
					continue;
				if (DEBUG_OBJECT_TYPE_ODERES) System.out.println("ORDER UNDEFINED: " + clashTypes[t-1].type + " vs. " + clashTypes[t].type);
				String typePair = (clashTypes[t-1].type + " -> " + clashTypes[t].type);
				if (DEBUG_OBJECT_TYPE_ODERES) System.out.println(" ==> ACTUAL PAIRINGS: " + typePairs.getCount(typePair) + ", " + invTypePairs.getCount(typePair) + " reverse");
//				System.out.println(" ==> ACTUAL CLASHES: " + clashTypePairs.getCount(typePair) + ", " + invClashTypePairs.getCount(typePair) + " reverse");
				orderWellDefined = false; // refuse using size scores for ambiguity
			}
			if (orderWellDefined && (ctTypes != null)) {
				for (int t = 0; t < clashTypes.length; t++)
					ctTypes[t] = clashTypes[t].type;
				typeOrders.add(new ImObjectTypeOrder(ctTypes, 2, clashTraces.getCount(clashTrace)));
				ctTypes = null;
			}
			for (int t = 0; t < clashTypes.length; t++)
				clashTypes[t].pos = clashTypes[t].freqPos;
			Arrays.sort(clashTypes);
			if (DEBUG_OBJECT_TYPE_ODERES) {
				System.out.println("NESTING FREQUENCY ORDER:");
				for (int t = 0; t < clashTypes.length; t++)
					System.out.println(" - " + clashTypes[t].type + " " + clashTypes[t].rel1Npos + " " + clashTypes[t].sizePos + " " + clashTypes[t].freqPos);
			}
			orderWellDefined = true;
			for (int t = 1; t < clashTypes.length; t++) {
				if (clashTypes[t-1].pos < clashTypes[t].pos)
					continue;
				if (DEBUG_OBJECT_TYPE_ODERES) System.out.println("ORDER UNDEFINED: " + clashTypes[t-1].type + " vs. " + clashTypes[t].type);
				String typePair = (clashTypes[t-1].type + " -> " + clashTypes[t].type);
				if (DEBUG_OBJECT_TYPE_ODERES) System.out.println(" ==> ACTUAL PAIRINGS: " + typePairs.getCount(typePair) + ", " + invTypePairs.getCount(typePair) + " reverse");
//				System.out.println(" ==> ACTUAL CLASHES: " + clashTypePairs.getCount(typePair) + ", " + invClashTypePairs.getCount(typePair) + " reverse");
				orderWellDefined = false; // refuse using nestring frequency scores for ambiguity
			}
			if (orderWellDefined && (ctTypes != null)) {
				for (int t = 0; t < clashTypes.length; t++)
					ctTypes[t] = clashTypes[t].type;
				typeOrders.add(new ImObjectTypeOrder(ctTypes, 1, clashTraces.getCount(clashTrace)));
				ctTypes = null;
			}
		}
		Collections.sort(typeOrders);
		for (int o = 0; o < typeOrders.size(); o++) {
			ImObjectTypeOrder ioto = ((ImObjectTypeOrder) typeOrders.get(o));
			if (DEBUG_OBJECT_TYPE_ODERES) System.out.println("Comparing '" + ioto + "' (strict " + ioto.strictness + ")");
			for (int co = (o+1); co < typeOrders.size(); co++) {
				ImObjectTypeOrder cIoto = ((ImObjectTypeOrder) typeOrders.get(co));
				if (DEBUG_OBJECT_TYPE_ODERES) System.out.println(" - to '" + cIoto + "' (strict " + cIoto.strictness + ")");
				if (ioto.subsumes(cIoto)) {
					ioto.weight += cIoto.weight;
					typeOrders.remove(co--);
					if (DEBUG_OBJECT_TYPE_ODERES) System.out.println("   ==> absorbed");
					continue;
				}
				if (ioto.contains(cIoto)) {
					if (DEBUG_OBJECT_TYPE_ODERES) System.out.println("   ==> order clash");
					continue;
				}
				if (ioto.overlaps(cIoto)) {
					if (DEBUG_OBJECT_TYPE_ODERES) System.out.println("   ==> might need combining");
					continue;
				}
			}
		}
		if (DEBUG_OBJECT_TYPE_ODERES) {
			System.out.println("Type orders: " + typeOrders.size());
			for (int o = 0; o < typeOrders.size(); o++) {
				ImObjectTypeOrder ioto = ((ImObjectTypeOrder) typeOrders.get(o));
				System.out.println(" - '" + ioto + "' (strict " + ioto.strictness + ", weight " + ioto.weight + ")");
			}
		}
		return ((ImObjectTypeOrder[]) typeOrders.toArray(new ImObjectTypeOrder[typeOrders.size()]));
	}
	
	private static class ImRegionList extends ImObjectList {
		ImRegionList(ImObject[] imos, boolean copy) {
			super(imos, copy);
		}
		ImRegionList(ImObjectTypeOrder[] iotos) {
			super(iotos);
		}
		int comparePosition(ImObject imo1, ImObject imo2) {
			ImRegion reg1 = ((ImRegion) imo1);
			ImRegion reg2 = ((ImRegion) imo2);
			int c = (reg1.pageId - reg2.pageId);
			if (c == 0)
				c = ImUtils.sizeOrder.compare(reg1, reg2);
			if (c == 0)
				c = ImUtils.topDownOrder.compare(reg1, reg2);
			if (c == 0)
				c = ImUtils.leftRightOrder.compare(reg1, reg2);
			return c;
		}
	}
	
	private static class ImRegionTreeNode {
		final BoundingBox bounds;
		final String type;
		private ArrayList children = null;
		private CountingSet descTypeCounts = null;
		ImRegionTreeNode(BoundingBox bounds, String type) {
			this.bounds = bounds;
			this.type = type;
		}
		boolean addChild(ImRegionTreeNode rtn) {
			if (!this.bounds.includes(rtn.bounds, false))
				return false;
			if (this.children == null)
				this.children = new ArrayList();
			if (this.descTypeCounts == null)
				this.descTypeCounts = new CountingSet();
			this.descTypeCounts.add(rtn.type);
			for (int c = 0; c < this.children.size(); c++) {
				if (((ImRegionTreeNode) this.children.get(c)).addChild(rtn))
					return true;
			}
			this.children.add(rtn);
			return true;
		}
//		void printSubTree(String prefix) {
//			if (this.children == null)
//				System.out.println(prefix + "+-- " + this.type + "@" + this.bounds);
//			else {
//				System.out.println(prefix + "+-+ " + this.type + "@" + this.bounds);
//				String cPrefix = (prefix + "| ");
//				for (int c = 0; c < this.children.size(); c++) {
//					ImRegionTreeNode cRtn = ((ImRegionTreeNode) this.children.get(c));
//					cRtn.printSubTree(cPrefix);
//				}
//			}
//		}
//		void appendChildren(CountingSet typeTraces, String typeTrace) {
//			if (this.children == null)
//				return;
//			for (int c = 0; c < this.children.size(); c++) {
//				ImRegionTreeNode cRtn = ((ImRegionTreeNode) this.children.get(c));
//				String cTypeTrace = (typeTrace + " -> " + cRtn.type);
//				typeTraces.add(cTypeTrace);
//				cRtn.appendChildren(typeTraces, cTypeTrace);
//			}
//		}
		void addChildTypePairs(CountingSet typePairs, CountingSet invTypePairs, ArrayList typeTrace) {
			if (this.children == null)
				return;
			typeTrace.add(this.type);
			for (int c = 0; c < this.children.size(); c++) {
				ImRegionTreeNode cRtn = ((ImRegionTreeNode) this.children.get(c));
				for (int t = 0; t < typeTrace.size(); t++) {
					String type = ((String) typeTrace.get(t));
					typePairs.add(type + " -> " + cRtn.type);
					invTypePairs.add(cRtn.type + " -> " + type);
				}
				cRtn.addChildTypePairs(typePairs, invTypePairs, typeTrace);
			}
			typeTrace.remove(typeTrace.size()-1);
		}
		void addSizeMatches(CountingSet types, CountingSet typePairs, CountingSet invTypePairs, CountingSet clashTraces, ArrayList regTrace) {
			boolean storeTrace = (regTrace.size() != 0);
			if (this.children != null)
				for (int c = 0; c < this.children.size(); c++) {
					ImRegionTreeNode cRtn = ((ImRegionTreeNode) this.children.get(c));
					if (this.bounds.equals(cRtn.bounds)) {
						regTrace.add(this);
						for (int r = 0; r < regTrace.size(); r++) {
							ImRegionTreeNode tRtn = ((ImRegionTreeNode) regTrace.get(r));
							types.add(tRtn.type);
							types.add(cRtn.type);
							typePairs.add(tRtn.type + " -> " + cRtn.type);
							invTypePairs.add(cRtn.type + " -> " + tRtn.type);
						}
						cRtn.addSizeMatches(types, typePairs, invTypePairs, clashTraces, regTrace);
						storeTrace = false;
						regTrace.remove(regTrace.size()-1);
					}
					else cRtn.addSizeMatches(types, typePairs, invTypePairs, clashTraces, new ArrayList());
				}
			if (storeTrace) {
				StringBuffer clashTrace = new StringBuffer();
				for (int r = 0; r < regTrace.size(); r++) {
					ImRegionTreeNode tRtn = ((ImRegionTreeNode) regTrace.get(r));
					if (r != 0)
						clashTrace.append(" -> ");
					clashTrace.append(tRtn.type);
				}
				clashTrace.append(" -> ");
				clashTrace.append(this.type);
				clashTraces.add(clashTrace.toString());
			}
		}
		void addSizeNestings(CountingSet typePairs, CountingSet invTypePairs) {
			if (this.children == null)
				return;
			for (int c = 0; c < this.children.size(); c++) {
				ImRegionTreeNode cRtn = ((ImRegionTreeNode) this.children.get(c));
				if (cRtn.bounds.getArea() < this.bounds.getArea()) {
					typePairs.add(this.type + " -> " + cRtn.type);
					invTypePairs.add(cRtn.type + " -> " + this.type);
				}
				cRtn.addSizeNestings(typePairs, invTypePairs);
			}
		}
		void addMultiChildTypePairs(CountingSet typePairs, CountingSet invTypePairs) {
			if (this.children == null)
				return;
			for (Iterator dtit = this.descTypeCounts.iterator(); dtit.hasNext();) {
				String descType = ((String) dtit.next());
				if (this.descTypeCounts.getCount(descType) < 2)
					continue;
				typePairs.add(type + " -> " + descType);
				invTypePairs.add(descType + " -> " + type);
			}
			for (int c = 0; c < this.children.size(); c++) {
				ImRegionTreeNode cRtn = ((ImRegionTreeNode) this.children.get(c));
				cRtn.addMultiChildTypePairs(typePairs, invTypePairs);
			}
		}
	}
	
	private static class ImAnnotationList extends ImObjectList {
		ImAnnotationList(ImObject[] imos, boolean copy) {
			super(imos, copy);
		}
		ImAnnotationList(ImObjectTypeOrder[] iotos) {
			super(iotos);
		}
		int comparePosition(ImObject imo1, ImObject imo2) {
			return annotationOrder.compare(imo1, imo2);
		}
	}
	
	private static class ImAnnotationTreeNode {
		final ImWord firstWord;
		final ImWord lastWord;
		final String textStreamId;
		final String type;
		private ArrayList children = null;
		private CountingSet descTypeCounts = null;
		ImAnnotationTreeNode(ImWord firstWord, ImWord lastWord, String textStreamId, String type) {
			this.firstWord = firstWord;
			this.lastWord = lastWord;
			this.textStreamId = textStreamId;
			this.type = type;
		}
		boolean addChild(ImAnnotationTreeNode atn) {
			if (!this.textStreamId.equals(atn.textStreamId))
				return false;
			if (atn.firstWord.getTextStreamPos() < this.firstWord.getTextStreamPos())
				return false;
			if (this.lastWord.getTextStreamPos() < atn.lastWord.getTextStreamPos())
				return false;
			if (this.children == null)
				this.children = new ArrayList();
			if (this.descTypeCounts == null)
				this.descTypeCounts = new CountingSet();
			this.descTypeCounts.add(atn.type);
			for (int c = this.children.size(); c != 0; c--) /* backwards should be faster, as annotations come ordered */ {
				if (((ImAnnotationTreeNode) this.children.get(c-1)).addChild(atn))
					return true;
			}
			this.children.add(atn);
			return true;
		}
//		void printSubTree(String prefix) {
//			if (this.children == null)
//				System.out.println(prefix + "+-- " + this.type + "@" + this.bounds);
//			else {
//				System.out.println(prefix + "+-+ " + this.type + "@" + this.bounds);
//				String cPrefix = (prefix + "| ");
//				for (int c = 0; c < this.children.size(); c++) {
//					ImRegionTreeNode cRtn = ((ImRegionTreeNode) this.children.get(c));
//					cRtn.printSubTree(cPrefix);
//				}
//			}
//		}
//		void appendChildren(CountingSet typeTraces, String typeTrace) {
//			if (this.children == null)
//				return;
//			for (int c = 0; c < this.children.size(); c++) {
//				ImRegionTreeNode cRtn = ((ImRegionTreeNode) this.children.get(c));
//				String cTypeTrace = (typeTrace + " -> " + cRtn.type);
//				typeTraces.add(cTypeTrace);
//				cRtn.appendChildren(typeTraces, cTypeTrace);
//			}
//		}
		void addChildTypePairs(CountingSet typePairs, CountingSet invTypePairs, ArrayList typeTrace) {
			if (this.children == null)
				return;
			typeTrace.add(this.type);
			for (int c = 0; c < this.children.size(); c++) {
				ImAnnotationTreeNode cAtn = ((ImAnnotationTreeNode) this.children.get(c));
				for (int t = 0; t < typeTrace.size(); t++) {
					String type = ((String) typeTrace.get(t));
					typePairs.add(type + " -> " + cAtn.type);
					invTypePairs.add(cAtn.type + " -> " + type);
				}
				cAtn.addChildTypePairs(typePairs, invTypePairs, typeTrace);
			}
			typeTrace.remove(typeTrace.size()-1);
		}
		void addSizeMatches(CountingSet types, CountingSet typePairs, CountingSet invTypePairs, CountingSet clashTraces, ArrayList annotTrace) {
			boolean storeTrace = (annotTrace.size() != 0);
			if (this.children != null)
				for (int c = 0; c < this.children.size(); c++) {
					ImAnnotationTreeNode cAtn = ((ImAnnotationTreeNode) this.children.get(c));
					if (!"textStreamRoot".equals(this.type) && (this.firstWord == cAtn.firstWord) && (this.lastWord == cAtn.lastWord)) {
						annotTrace.add(this);
						for (int a = 0; a < annotTrace.size(); a++) {
							ImAnnotationTreeNode tAtn = ((ImAnnotationTreeNode) annotTrace.get(a));
							types.add(tAtn.type);
							types.add(cAtn.type);
							typePairs.add(tAtn.type + " -> " + cAtn.type);
							invTypePairs.add(cAtn.type + " -> " + tAtn.type);
						}
						cAtn.addSizeMatches(types, typePairs, invTypePairs, clashTraces, annotTrace);
						storeTrace = false;
						annotTrace.remove(annotTrace.size()-1);
					}
					else cAtn.addSizeMatches(types, typePairs, invTypePairs, clashTraces, new ArrayList());
				}
			if (storeTrace) {
				StringBuffer clashTrace = new StringBuffer();
				for (int a = 0; a < annotTrace.size(); a++) {
					ImAnnotationTreeNode tAtn = ((ImAnnotationTreeNode) annotTrace.get(a));
					if (a != 0)
						clashTrace.append(" -> ");
					clashTrace.append(tAtn.type);
				}
				clashTrace.append(" -> ");
				clashTrace.append(this.type);
				clashTraces.add(clashTrace.toString());
			}
		}
		void addSizeNestings(CountingSet typePairs, CountingSet invTypePairs) {
			if (this.children == null)
				return;
			for (int c = 0; c < this.children.size(); c++) {
				ImAnnotationTreeNode cAtn = ((ImAnnotationTreeNode) this.children.get(c));
				if (!"textStreamRoot".equals(this.type) && ((this.firstWord != cAtn.firstWord) || (this.lastWord != cAtn.lastWord))) {
					typePairs.add(this.type + " -> " + cAtn.type);
					invTypePairs.add(cAtn.type + " -> " + this.type);
				}
				cAtn.addSizeNestings(typePairs, invTypePairs);
			}
		}
		void addMultiChildTypePairs(CountingSet typePairs, CountingSet invTypePairs) {
			if (this.children == null)
				return;
			for (Iterator dtit = this.descTypeCounts.iterator(); dtit.hasNext();) {
				String descType = ((String) dtit.next());
				if (this.descTypeCounts.getCount(descType) < 2)
					continue;
				typePairs.add(type + " -> " + descType);
				invTypePairs.add(descType + " -> " + type);
			}
			for (int c = 0; c < this.children.size(); c++) {
				ImAnnotationTreeNode cAtn = ((ImAnnotationTreeNode) this.children.get(c));
				cAtn.addMultiChildTypePairs(typePairs, invTypePairs);
			}
		}
	}
	
	private static class GraphicsDataPersister {
		private ImDocumentData data;
		final TreeMap entryNamesToDataHashes = new TreeMap();
		final TreeMap dataHashesToDataBytes = new TreeMap();
		GraphicsDataPersister(ImDocumentData data) {
			this.data = data;
		}
		OutputStream getOutputStream(final String entryName) throws IOException {
			return new DataHashOutputStream(new ByteArrayOutputStream()) {
				public void close() throws IOException {
					super.flush();
					super.close();
					String entryDataHash = ImDocumentIO.abridgeEntryDataHash(this.getDataHash());
					entryNamesToDataHashes.put(entryName, entryDataHash);
					if (!dataHashesToDataBytes.containsKey(entryDataHash))
						dataHashesToDataBytes.put(entryDataHash, ((ByteArrayOutputStream) this.out).toByteArray());
				}
			};		}
		void close() throws IOException {
			if (this.entryNamesToDataHashes.isEmpty())
				return;
			ZipOutputStream gdOut = new ZipOutputStream(this.data.getOutputStream(GRAPHICS_DATA_BUNDLE_ENTRY_NAME, !this.data.hasEntry(GRAPHICS_DATA_BUNDLE_ENTRY_NAME)));
			
			//	store name-to-hash mapping
			ZipEntry ze = new ZipEntry("entryNamesToDataHashes.tsv");
			ze.setTime(0); // need to keep time constant to keep hash stable
			gdOut.putNextEntry(ze);
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(gdOut, "UTF-8"));
			for (Iterator enit = this.entryNamesToDataHashes.keySet().iterator(); enit.hasNext();) {
				String entryName = ((String) enit.next());
				String dataHash = ((String) entryNamesToDataHashes.get(entryName));
				bw.write(entryName);
				bw.write("\t");
				bw.write(dataHash);
				bw.write("\r\n");
			}
			bw.flush();
			gdOut.closeEntry();
			
			//	store data by hashes
			for (Iterator dhit = this.dataHashesToDataBytes.keySet().iterator(); dhit.hasNext();) {
				String dataHash = ((String) dhit.next());
				byte[] data = ((byte[]) this.dataHashesToDataBytes.get(dataHash));
				ze = new ZipEntry(dataHash);
				ze.setTime(0); // need to keep time constant to keep hash stable
				gdOut.putNextEntry(ze);
				gdOut.write(data);
				gdOut.flush();
				gdOut.closeEntry();
			}
			
			//	finally ...
			gdOut.close();
		}
	}
	
	private static class TsvWriteParams {
		String[] keys;
		String[] highFreqAttribNames;
		HashSet lowFreqAttribNames;
		TreeMap extValues;
		TsvWriteParams(String[] keys, String[] highFreqAttribNames, HashSet lowFreqAttribNames, TreeMap extValues) {
			this.keys = keys;
			this.highFreqAttribNames = highFreqAttribNames;
			if ((lowFreqAttribNames == null) || (lowFreqAttribNames.size() == 0))
				this.lowFreqAttribNames = null;
			else this.lowFreqAttribNames = lowFreqAttribNames;
			this.extValues = extValues;
		}
	}
//	private static TsvWriteParams getRegionTsvWriteParams(ImPage[] pages, String type, HashSet exceptForTypes, boolean attributeColumns, boolean attributeExtValues) {
//		CountingSet allAns = new CountingSet(new TreeMap());
//		CountingSet longAvs = (attributeExtValues ? new CountingSet(new HashMap()) : null);
//		int lftRegCount = 0; // number of regions in general file, for assessing sensibility for dedicated attribute columns
//		for (int p = 0; p < pages.length; p++) {
//			ImRegion[] pageRegs = pages[p].getRegions(type); // type 'null' gets all regions
//			for (int r = 0; r < pageRegs.length; r++) {
//				if ((type == null) && (exceptForTypes != null) && exceptForTypes.contains(pageRegs[r].getType()))
//					continue;
//				lftRegCount++;
//				addAttributeNames(pageRegs[r], allAns, longAvs);
//			}
//		}
//		String[] baseKeys = {
//			("@" + ImRegion.TYPE_ATTRIBUTE),
//			("@" + ImRegion.PAGE_ID_ATTRIBUTE),
//			("@" + ImRegion.BOUNDING_BOX_ATTRIBUTE)
//		};
//		return getTsvWriteParams(allAns, (attributeColumns ? ((type == null) ? lftRegCount : -1) : Integer.MAX_VALUE), baseKeys, ((type == null) ? 0 : 1), longAvs);
//	}
	private static TsvWriteParams getRegionTsvWriteParams(ImRegionList regs, boolean singleType, boolean attributeColumns, boolean attributeExtValues) {
		CountingSet allAns = new CountingSet(new TreeMap());
		CountingSet longAvs = (attributeExtValues ? new CountingSet(new HashMap()) : null);
		for (int r = 0; r < regs.size(); r++)
			addAttributeNames(regs.get(r), allAns, longAvs);
		String[] baseKeys = {
			("@" + ImRegion.TYPE_ATTRIBUTE),
			("@" + ImRegion.PAGE_ID_ATTRIBUTE),
			("@" + ImRegion.BOUNDING_BOX_ATTRIBUTE)
		};
		return getTsvWriteParams(allAns, (attributeColumns ? (singleType ? -1 : regs.size()) : Integer.MAX_VALUE), baseKeys, (singleType ? 1 : 0), longAvs);
	}
//	private static TsvWriteParams getAnnotTsvWriteParams(ImAnnotation[] annots, String type, HashSet exceptForTypes, boolean attributeColumns, boolean attributeExtValues) {
//		CountingSet allAns = new CountingSet(new TreeMap());
//		CountingSet longAvs = (attributeExtValues ? new CountingSet(new HashMap()) : null);
//		int lftAnnotCount = 0; // number of regions in general file, for assessing sensibility for dedicated attribute columns
//		for (int a = 0; a < annots.length; a++) {
//			if ((type == null) && (exceptForTypes != null) && exceptForTypes.contains(annots[a].getType()))
//				continue;
//			lftAnnotCount++;
//			addAttributeNames(annots[a], allAns, longAvs);
//		}
//		allAns.removeAll(ImAnnotation.PAGE_ID_ATTRIBUTE); // implicitly present in all annotations, but we don't store it
//		allAns.removeAll(ImAnnotation.LAST_PAGE_ID_ATTRIBUTE); // implicitly present in all annotations, but we don't store it
//		allAns.removeAll(ImAnnotation.BOUNDING_BOX_ATTRIBUTE); // implicitly present in all annotations, but we don't store it
//		String[] baseKeys = {
//			("@" + ImAnnotation.TYPE_ATTRIBUTE),
//			("@" + ImAnnotation.FIRST_WORD_ATTRIBUTE),
//			("@" + ImAnnotation.LAST_WORD_ATTRIBUTE),
//		};
//		return getTsvWriteParams(allAns, (attributeColumns ? ((type == null) ? lftAnnotCount : -1) : Integer.MAX_VALUE), baseKeys, ((type == null) ? 0 : 1), longAvs);
//	}
	private static TsvWriteParams getAnnotTsvWriteParams(ImAnnotationList annots, boolean singleType, boolean attributeColumns, boolean attributeExtValues) {
		CountingSet allAns = new CountingSet(new TreeMap());
		CountingSet longAvs = (attributeExtValues ? new CountingSet(new HashMap()) : null);
//		int lftAnnotCount = 0; // number of regions in general file, for assessing sensibility for dedicated attribute columns
		for (int a = 0; a < annots.size(); a++) {
			ImAnnotation annot = ((ImAnnotation) annots.get(a));
			addAttributeNames(annot, allAns, longAvs);
		}
		allAns.removeAll(ImAnnotation.PAGE_ID_ATTRIBUTE); // implicitly present in all annotations, but we don't store it
		allAns.removeAll(ImAnnotation.LAST_PAGE_ID_ATTRIBUTE); // implicitly present in all annotations, but we don't store it
		allAns.removeAll(ImAnnotation.BOUNDING_BOX_ATTRIBUTE); // implicitly present in all annotations, but we don't store it
		String[] baseKeys = {
			("@" + ImAnnotation.TYPE_ATTRIBUTE),
			("@" + ImAnnotation.FIRST_WORD_ATTRIBUTE),
			("@" + ImAnnotation.LAST_WORD_ATTRIBUTE),
		};
		return getTsvWriteParams(allAns, (attributeColumns ? (singleType ? -1 : annots.size()) : Integer.MAX_VALUE), baseKeys, (singleType ? 1 : 0), longAvs);
	}
	private static TsvWriteParams getTsvWriteParams(CountingSet allAns, int lftObjCount, String[] baseKeys, int bkFrom, CountingSet longAvs) {
		HashSet lowFreqAns = new HashSet();
		for (Iterator anit = allAns.iterator(); anit.hasNext();) {
			String an = ((String) anit.next());
			if ((an.length() * allAns.getCount(an)) < lftObjCount) {
				anit.remove();
				lowFreqAns.add(an);
			}
		}
		if ((lftObjCount < Integer.MAX_VALUE) && (lowFreqAns.size() < 2)) /* no use going generic for single attribute name */ {
			allAns.addAll(lowFreqAns);
			lowFreqAns.clear();
		}
		TreeMap extValues = null;
		if ((longAvs != null) && (longAvs.size() != 0)) {
			extValues = new TreeMap();
			HashSet extValKeys = new HashSet(); // TODOne waive attribute value externalization in case of collisions (also with actual attribute values)
			for (Iterator avit = longAvs.iterator(); avit.hasNext();) {
				String av = ((String) avit.next());
				if (longAvs.getCount(av) < 2)
					continue;
//				extValues.put(av, ("" + av.hashCode()));
				String extValKey = ("" + av.hashCode());
				if (extValKeys.add(extValKey))
					extValues.put(av, extValKey);
				else /* we have hash collision, waive external attribute values altogether */ {
					extValues = null;
					break;
				}
			}
//			if (extValues.isEmpty())
			if ((extValues != null) && extValues.isEmpty())
				extValues = null;
		}
		String[] highFreqAns = ((String[]) allAns.toArray(new String[allAns.elementCount()]));
		Arrays.sort(highFreqAns, allAns.getDecreasingCountOrder());
		String[] tsvKeys = new String[(baseKeys.length - bkFrom) + highFreqAns.length + ((lowFreqAns.size() != 0) ? 1 : 0)];
		System.arraycopy(baseKeys, bkFrom, tsvKeys, 0, (baseKeys.length - bkFrom));
		System.arraycopy(highFreqAns, 0, tsvKeys, (baseKeys.length - bkFrom), highFreqAns.length);
		if (lowFreqAns.size() != 0)
			tsvKeys[(baseKeys.length - bkFrom) + highFreqAns.length] = ("@" + ImObject.ATTRIBUTES_STRING_ATTRIBUTE);
		return new TsvWriteParams(tsvKeys, highFreqAns, lowFreqAns, extValues);
	}
	
	private static void addObjectTypes(ImObject[] objs, CountingSet allTypes) {
		for (int o = 0; o < objs.length; o++)
			allTypes.add(objs[o].getType());
	}
	
	private static void addAttributeNames(ImObject[] objs, CountingSet allAns, CountingSet longAvs) {
		for (int o = 0; o < objs.length; o++)
			addAttributeNames(objs[o], allAns, longAvs);
	}
//	private static void addAttributeNames(ImObject[] objs, HashSet exceptForTypes, CountingSet allAns) {
//		for (int o = 0; o < objs.length; o++) {
//			if (exceptForTypes.contains(objs[o].getType()))
//				continue;
//			addAttributeNames(objs[o], allAns);
//		}
//	}
	private static void addAttributeNames(ImObject obj, CountingSet allAns, CountingSet longAvs) {
		String[] ans = obj.getAttributeNames();
		for (int n = 0; n < ans.length; n++) {
			allAns.add(ans[n]);
			if (longAvs == null)
				continue;
			Object av = obj.getAttribute(ans[n]);
			if (av == null)
				continue;
			String avStr = av.toString();
			if (avStr.length() < 32)
				continue;
			longAvs.add(avStr);
		}
	}
	
	private static class TsvWriter {
		BufferedWriter bw;
		String[] keys;
		int currentKey = 0;
		boolean currentValueWritten = false;
		TreeMap extValues;
		TsvWriter(BufferedWriter bw, String[] keys, TreeMap extValues) throws IOException {
			this(bw, keys, extValues, null);
		}
		TsvWriter(BufferedWriter bw, String[] keys, TreeMap extValues, ImObjectTypeOrder[] typeOrders) throws IOException {
			this.bw = bw;
			this.keys = keys;
			this.bw.write(this.keys[0]);
			for (int k = 1; k < this.keys.length; k++) {
				this.bw.write("\t");
				this.bw.write(this.keys[k]);
			}
			this.endRecord();
			if (typeOrders != null)
				for (int to = 0; to < typeOrders.length; to++) {
					this.bw.write("@TYPEORDER");
					for (int t = 0; t < typeOrders[to].types.length; t++) {
						this.bw.write((t == 0) ? "\t" : " ");
						this.bw.write(typeOrders[to].types[t]);
					}
					this.bw.write("\t" + typeOrders[to].strictness);
					this.bw.write("\t" + typeOrders[to].weight);
					this.endRecord();
				}
			this.extValues = extValues;
			if (this.extValues != null) {
				for (Iterator evit = this.extValues.keySet().iterator(); evit.hasNext();) {
					String extVal = ((String) evit.next());
					String valKey = ((String) this.extValues.get(extVal));
					this.bw.write("@EXTVAL");
					this.bw.write("\t");
					this.bw.write(valKey);
					this.bw.write("\t");
					this.writeValue(extVal, false);
					this.endRecord();
				}
			}
		}
		void endRecord() throws IOException {
			this.bw.write("\r\n");
			this.currentKey = 0;
			this.currentValueWritten = false;
		}
		void writeRecord(String[] values) throws IOException {
			this.writeRecord(values, values.length);
		}
		void writeRecord(String[] values, int to) throws IOException {
			if ((values[0] != null) && (values[0].length() != 0))
				this.writeValue(values[0], (this.extValues != null));
			for (int v = 1; v < to; v++) {
				if (this.keys.length <= v)
					throw new IllegalArgumentException("Cannot write field " + v + ", keys are " + Arrays.toString(this.keys));
				this.bw.write("\t");
				if ((values[v] != null) && (values[v].length() != 0))
					this.writeValue(values[v], (this.extValues != null));
			}
			this.endRecord();
		}
		void writeAttributes(Attributed attr, String[] highFreqAttribNames, Set lowFreqAttribNames) throws IOException {
			for (int n = 0; n < highFreqAttribNames.length; n++) {
				Object value = attr.getAttribute(highFreqAttribNames[n]);
				if (value != null)
					this.writeValue(highFreqAttribNames[n], value.toString());
			}
			if (lowFreqAttribNames != null)
				this.writeValue(("@" + ImRegion.ATTRIBUTES_STRING_ATTRIBUTE), getAttributesString(attr, lowFreqAttribNames, this.extValues));
		}
		void writeValue(String key, String value) throws IOException {
			if ((value == null) || (value.length() == 0))
				return;
			int currentKey = this.currentKey;
			while ((this.currentKey < this.keys.length) && !this.keys[this.currentKey].equals(key)) {
				this.bw.write("\t");
				this.currentKey++;
				this.currentValueWritten = false;
			}
			if (this.currentKey == this.keys.length)
				throw new IllegalArgumentException("Cannot write field '" + key + "' in column " + currentKey + " or after, keys are " + Arrays.toString(this.keys));
			if (this.currentValueWritten)
				throw new IllegalArgumentException("Cannot write field '" + key + "' more than once per record");
			this.writeValue(value, (this.extValues != null));
			this.currentValueWritten = true;
		}
		private void writeValue(String value, boolean tryExtVal) throws IOException {
			if (tryExtVal) {
				String valKey = ((String) this.extValues.get(value));
				if (valKey != null) {
					this.bw.write("@EXTVAL:" + valKey);
					return;
				}
			}
			for (int c = 0; c < value.length(); c++) {
				char ch = value.charAt(c);
				if (ch == '\\')
					this.bw.write("\\\\");
				else if (ch == '\n')
					this.bw.write("\\n");
				else if (ch == '\r')
					this.bw.write("\\r");
				else if (ch == '\t')
					this.bw.write("\\t");
				else if (ch < ' ')
					this.bw.write(" ");
				else this.bw.write(ch);
			}
		}
		void close() throws IOException {
			this.bw.flush();
			this.bw.close();
		}
	}
	
	private static Comparator annotationOrder = new Comparator() {
		public int compare(Object obj1, Object obj2) {
			ImAnnotation annot1 = ((ImAnnotation) obj1);
			ImAnnotation annot2 = ((ImAnnotation) obj2);
			int c = ImUtils.textStreamOrder.compare(annot1.getFirstWord(), annot2.getFirstWord());
			if (c != 0)
				return c;
			c = ImUtils.textStreamOrder.compare(annot2.getLastWord(), annot1.getLastWord());
			if (c != 0)
				return c;
			return 0; // no type comparison, ImDocument orders by creation timestamp, and that should pertain on loading
		}
	};
	
	private static void writeKeys(StringVector keys, BufferedWriter bw) throws IOException {
		for (int k = 0; k < keys.size(); k++) {
			if (k != 0)
				bw.write(',');
			bw.write('"');
			bw.write(keys.get(k));
			bw.write('"');
		}
		bw.newLine();
	}
	
	private static void writeValues(StringVector keys, StringTupel st, BufferedWriter bw) throws IOException {
		for (int k = 0; k < keys.size(); k++) {
			if (k != 0)
				bw.write(',');
			bw.write('"');
			String value = st.getValue(keys.get(k), "");
			for (int c = 0; c < value.length(); c++) {
				char ch = value.charAt(c);
				if (ch == '"')
					bw.write('"');
				bw.write(ch);
			}
			bw.write('"');
		}
		bw.newLine();
	}
	
	private static BufferedWriter getWriter(ImDocumentData data, String entryName, HashSet staleEntryNames, boolean writeDirectly) throws IOException {
		if (staleEntryNames != null)
			staleEntryNames.remove(entryName);
		return new BufferedWriter(new OutputStreamWriter(data.getOutputStream(entryName, writeDirectly), "UTF-8"));
	}
	
	/**
	 * Linearize the attributes of an attributed object into a single-line, CSV
	 * friendly form. Attribute values are converted into strings and escaped
	 * based on XML rules. Afterwards, each attribute value is appended to its
	 * corresponding attribute name, enclosed in angle brackets. The
	 * concatenation of these attribute name and value pairs yields the final
	 * linearization.
	 * @param attr the attributed object whose attributes to linearize
	 * @return the linearized attributes of the argument object
	 */
	public static String getAttributesString(Attributed attr) {
		return getAttributesString(attr, null);
	}
	
	/**
	 * Linearize the attributes of an attributed object into a single-line, CSV
	 * friendly form. Attribute values are converted into strings and escaped
	 * based on XML rules. Afterwards, each attribute value is appended to its
	 * corresponding attribute name, enclosed in angle brackets. The
	 * concatenation of these attribute name and value pairs yields the final
	 * linearization.
	 * @param attr the attributed object whose attributes to linearize
	 * @param filterAns a set containing attribute names to include
	 * @param tsvMode linearize the attributes of the argument object in TSV 
	 *            mode rather than CSV mode?
	 * @return the linearized attributes of the argument object
	 */
	public static String getAttributesString(Attributed attr, Set filterAns) {
		return getAttributesString(attr, filterAns, null);
	}
	private static String getAttributesString(Attributed attr, Set filterAns, TreeMap extValues) {
		StringBuffer attributes = new StringBuffer();
		String[] attribNames = attr.getAttributeNames();
		for (int n = 0; n < attribNames.length; n++) {
			if ((filterAns != null) && !filterAns.contains(attribNames[n]))
				continue;
			Object value = attr.getAttribute(attribNames[n]);
			if (value == null)
				continue;
			String valueStr = value.toString();
			try {
				attributes.append(attribNames[n]);
				attributes.append("<");
				String valueKey = ((extValues == null) ? null : ((String) extValues.get(valueStr)));
				appendAttributeValue(((valueKey == null) ? valueStr : ("@EXTVAL:" + valueKey)), attributes);
				attributes.append(">");
			} catch (RuntimeException re) {}
		}
		return attributes.toString();
	}
	private static void appendAttributeValue(String value, StringBuffer attributes) {
		for (int c = 0; c < value.length(); c++) {
			char ch = value.charAt(c);
			if (ch == '<')
				attributes.append("&lt;");
			else if (ch == '>')
				attributes.append("&gt;");
			else if (ch == '"')
				attributes.append("&quot;");
			else if (ch == '&')
				attributes.append("&amp;");
			else if ((ch < 32) || (ch == 127))
				attributes.append("&x" + Integer.toString(((int) ch), 16).toUpperCase() + ";");
			else attributes.append(ch);
		}
	}
	
	private static String getPageImageAttributesCsv(PageImageInputStream piis) {
		StringBuffer piAttributes = new StringBuffer();
		piAttributes.append("originalWidth<" + piis.originalWidth + ">");
		piAttributes.append("originalHeight<" + piis.originalHeight + ">");
		piAttributes.append("originalDpi<" + piis.originalDpi + ">");
		if (piis.currentDpi != piis.originalDpi)
			piAttributes.append("currentDpi<" + piis.currentDpi + ">");
		if (piis.leftEdge != 0)
			piAttributes.append("leftEdge<" + piis.leftEdge + ">");
		if (piis.rightEdge != 0)
			piAttributes.append("rightEdge<" + piis.rightEdge + ">");
		if (piis.topEdge != 0)
			piAttributes.append("topEdge<" + piis.topEdge + ">");
		if (piis.bottomEdge != 0)
			piAttributes.append("bottomEdge<" + piis.bottomEdge + ">");
		return piAttributes.toString();
	}
	private static String[] getPageImageRecordTsv(int pageId, PageImageInputStream piis) {
		String[] piRecord = new String[9];
		Arrays.fill(piRecord, null);
		piRecord[0] = ("" + pageId);
		piRecord[1] = ("" + piis.originalWidth);
		piRecord[2] = ("" + piis.originalHeight);
		piRecord[3] = ("" + piis.originalDpi);
		if (piis.currentDpi != piis.originalDpi)
			piRecord[4] = ("" + piis.currentDpi);
		if (piis.leftEdge != 0)
			piRecord[5] = ("" + piis.leftEdge);
		if (piis.rightEdge != 0)
			piRecord[6] = ("" + piis.rightEdge);
		if (piis.topEdge != 0)
			piRecord[7] = ("" + piis.topEdge);
		if (piis.bottomEdge != 0)
			piRecord[8] = ("" + piis.bottomEdge);
		return piRecord;
	}
	
	/**
	 * Parse and un-escape attribute name and value pairs that were previously
	 * linearized via <code>getAttributesString()</code>. The parsed attribute
	 * name and value pairs are stored in the argument attributed object.
	 * @param attr the attributed objects to store the attributes in
	 * @param attributes the linearized attribute and value string
	 */
	public static void setAttributes(Attributed attr, String attributes) {
		setAttributes(attr, attributes, null);
	}
	private static void setAttributes(Attributed attr, String attributes, TreeMap extValues) {
		for (int nameStart = 0, nameEnd, valueStart, valueEnd; nameStart < attributes.length();) {
			nameEnd = attributes.indexOf("<", nameStart);
			valueStart = (nameEnd + "<".length());
			valueEnd = attributes.indexOf(">", valueStart);
			attr.setAttribute(attributes.substring(nameStart, nameEnd), unescapeAttributeValue(attributes, valueStart, valueEnd, extValues));
			nameStart = (valueEnd + ">".length());
		}
	}
	private static String unescapeAttributeValue(String attributes, int start, int end, TreeMap extValues) {
		if ((extValues != null) && attributes.startsWith("@EXTVAL:", start)) {
			String extVal = ((String) extValues.get(attributes.substring(start, end)));
			if (extVal == null)
				throw new RuntimeException("Unable to resolve external value key " + attributes.substring(start, end));
			else return extVal;
		}
		StringBuffer value = new StringBuffer();
		for (int c = start; c < end;) {
			char ch = attributes.charAt(c);
			if (ch == '&') {
				if (attributes.startsWith("amp;", (c+1))) {
					value.append('&');
					c += "&amp;".length();
				}
				else if (attributes.startsWith("lt;", (c+1))) {
					value.append('<');
					c += "&lt;".length();
				}
				else if (attributes.startsWith("gt;", (c+1))) {
					value.append('>');
					c += "&gt;".length();
				}
				else if (attributes.startsWith("quot;", (c+1))) {
					value.append('"');
					c += "&quot;".length();
				}
				else if (attributes.startsWith("x", (c+1))) {
					int sci = attributes.indexOf(';', (c+1));
					if ((sci != -1) && (sci <= (c+4))) try {
						ch = ((char) Integer.parseInt(attributes.substring((c+2), sci), 16));
						c = sci;
					} catch (Exception e) {}
					value.append(ch);
					c++;
				}
				else {
					value.append(ch);
					c++;
				}
			}
			else {
				value.append(ch);
				c++;
			}
		}
		return value.toString();
	}
	
	private static void setAttributes(Attributed attr, String[] keys, int from, int to, String[] values, int valueOffset, TreeMap extValues) {
		for (int k = (from - valueOffset); k < (to - valueOffset); k++) {
			if (("@" + ImObject.ATTRIBUTES_STRING_ATTRIBUTE).equals(keys[k]))
				setAttributes(attr, values[k + valueOffset], extValues);
			else attr.setAttribute(keys[k], values[k + valueOffset]);
		}
	}
	
	static String abridgeEntryDataHash(String hash) {
		if (hash.length() == 32)
			return (hash.substring(2, 4) +
					hash.substring(6, 8) +
					hash.substring(10, 12) +
					hash.substring(14, 16) +
					hash.substring(18, 20) +
					hash.substring(22, 24) +
					hash.substring(26, 28) +
					hash.substring(30, 32));
		else return hash;
	}
	
	/**
	 * Check the TSV mode storage parameter flag that indicates to use
	 * dedicated columns for the values of frequent page attributes.
	 * @param storageFlags the storage parameter vector to check
	 * @return true if the respective bit is set
	 */
	public static boolean usePageAttributeColumns(long storageFlags) {
		return ((storageFlags & STORAGE_MODE_TSV_PAGE_ATTRIBUTE_COLUMNS) != 0);
	}
	
	/**
	 * Set the TSV mode storage parameter flag that indicates to use dedicated
	 * columns for the values of frequent page attributes.
	 * @param storageFlags the storage parameter vector to adjust
	 * @param uac use dedicated columns for frequent attributes?
	 * @return the adjusted parameter vector
	 */
	public static long setUsePageAttributeColumns(long storageFlags, boolean uac) {
		if (uac)
			return (storageFlags | STORAGE_MODE_TSV_PAGE_ATTRIBUTE_COLUMNS);
		else return (storageFlags & ~STORAGE_MODE_TSV_PAGE_ATTRIBUTE_COLUMNS);
	}
	
	/**
	 * Check the TSV mode storage parameter flag that indicates to use
	 * dedicated columns for the values of frequent word attributes.
	 * @param storageFlags the storage parameter vector to check
	 * @return true if the respective bit is set
	 */
	public static boolean useWordAttributeColumns(long storageFlags) {
		return ((storageFlags & STORAGE_MODE_TSV_WORD_ATTRIBUTE_COLUMNS) != 0);
	}
	
	/**
	 * Set the TSV mode storage parameter flag that indicates to use dedicated
	 * columns for the values of frequent word attributes.
	 * @param storageFlags the storage parameter vector to adjust
	 * @param uac use dedicated columns for frequent attributes?
	 * @return the adjusted parameter vector
	 */
	public static long setUseWordAttributeColumns(long storageFlags, boolean uac) {
		if (uac)
			return (storageFlags | STORAGE_MODE_TSV_WORD_ATTRIBUTE_COLUMNS);
		else return (storageFlags & ~STORAGE_MODE_TSV_WORD_ATTRIBUTE_COLUMNS);
	}
	
	/**
	 * Check the TSV mode storage parameter flag that indicates to store words
	 * in multiple chunks of smaller size, rather than one big file.
	 * @param storageFlags the storage parameter vector to check
	 * @return true if the respective bit is set
	 */
	public static boolean useWordChunks(long storageFlags) {
		return ((storageFlags & STORAGE_MODE_TSV_WORD_CHUNKS) != 0);
	}
	
	/**
	 * Set the TSV mode storage parameter flag that indicates to store words
	 * in multiple chunks of smaller size, rather than one big file. Chunk size
	 * defaults to 4096 (0x1000), but can be set in the 4th and 3rd most
	 * significant byte of the parameters, i.e., the two byte masked by
	 * <code>0x0000000000FF0000</code>.
	 * @param storageFlags the storage parameter vector to adjust
	 * @param uwc use multiple smaller chunks for storing words?
	 * @return the adjusted parameter vector
	 */
	public static long setUseWordChunks(long storageFlags, boolean uwc) {
		if (uwc)
			return (storageFlags | STORAGE_MODE_TSV_WORD_CHUNKS);
		else return (storageFlags & ~STORAGE_MODE_TSV_WORD_CHUNKS);
	}
	
	/**
	 * Get the size of word chunks in a TSV mode storage parameter vector,
	 * rounded to the next lower multiple of 0x0100 (256), i.e., read the size
	 * in the 3rd most significant byte. A word chunk size to 0 indicates using
	 * the default of 4096 (0x1000).
	 * @param storageFlags the storage parameter vector to read out
	 * @return the word chunk size encoded in the argument parameter vector
	 */
	public static int getWordChunkSize(long storageFlags) {
		long wcs = (storageFlags &= STORAGE_MODE_TSV_WORD_CHUNK_SIZE_MASK);
		wcs >>>= 16; // adjust bit position
		wcs <<= 8; // multiply times 0x0100/256
		return ((int) (wcs & 0x000000007FFFFFFFL));
	}
	
	/**
	 * Set the size of word chunks in a TSV mode storage parameter vector,
	 * rounded to the next lower multiple of 0x0100 (256), i.e., store the
	 * argument number in the 3rd most significant byte. Setting word chunk
	 * size to 0 indicates to use the default of 4096 (0x1000). Otherwise, the
	 * minimum chunk size is 256.
	 * @param storageFlags the storage parameter vector to adjust
	 * @param wcs the word chunk size to set
	 * @return the adjusted parameter vector
	 */
	public static long setWordChunkSize(long storageFlags, int wcs) {
		if (0x0000FFFF < wcs)
			throw new IllegalArgumentException("The maximum word chunk size is 65335 (0xFFFF)");
		if ((wcs != 0) && (wcs < 0x00000100))
			throw new IllegalArgumentException("The minimum word chunk size is 256 (0x0100), or 0 (0x0000) for using the default");
		storageFlags &= ~STORAGE_MODE_TSV_WORD_CHUNK_SIZE_MASK;
//		storageFlags |= (((long) wcs) << 16);
		wcs >>>= 8; // divide by 0x0100/256
		storageFlags |= (((long) wcs) << 16); // adjust bit position and add into storage flags
		return storageFlags;
	}
	
	/**
	 * Check the TSV mode storage parameter flag that indicates to use
	 * dedicated columns for the values of frequent region attributes.
	 * @param storageFlags the storage parameter vector to check
	 * @return true if the respective bit is set
	 */
	public static boolean useRegionAttributeColumns(long storageFlags) {
		return ((storageFlags & STORAGE_MODE_TSV_REGION_ATTRIBUTE_COLUMNS) != 0);
	}
	
	/**
	 * Set the TSV mode storage parameter flag that indicates to use dedicated
	 * columns for the values of frequent region attributes.
	 * @param storageFlags the storage parameter vector to adjust
	 * @param uac use dedicated columns for frequent attributes?
	 * @return the adjusted parameter vector
	 */
	public static long setUseRegionAttributeColumns(long storageFlags, boolean uac) {
		if (uac)
			return (storageFlags | STORAGE_MODE_TSV_REGION_ATTRIBUTE_COLUMNS);
		else return (storageFlags & ~STORAGE_MODE_TSV_REGION_ATTRIBUTE_COLUMNS);
	}
	
	/**
	 * Check the TSV mode storage parameter flag that indicates to externalize
	 * long region attribute values and reference them by a code.
	 * @param storageFlags the storage parameter vector to check
	 * @return true if the respective bit is set
	 */
	public static boolean externalizeRegionAttributeValues(long storageFlags) {
		return ((storageFlags & STORAGE_MODE_TSV_EXTERNAL_REGION_ATTRIBUTES) != 0);
	}
	
	/**
	 * Set the TSV mode storage parameter flag that indicates to externalize
	 * long region attribute values and reference them by a code. This saves
	 * storing lengthy strings multiple times.
	 * @param storageFlags the storage parameter vector to adjust
	 * @param eav externalize long region attribute values?
	 * @return the adjusted parameter vector
	 */
	public static long setExternalizeRegionAttributeValues(long storageFlags, boolean eav) {
		if (eav)
			return (storageFlags | STORAGE_MODE_TSV_EXTERNAL_REGION_ATTRIBUTES);
		else return (storageFlags & ~STORAGE_MODE_TSV_EXTERNAL_REGION_ATTRIBUTES);
	}
	
	/**
	 * Check the TSV mode storage parameter flag that indicates to externalize
	 * regions of frequent types to dedicated files, which might facilitate
	 * additional optimizations, e.g. more efficient use of dedicated attribute
	 * columns.
	 * @param storageFlags the storage parameter vector to check
	 * @return true if the respective bit is set
	 */
	public static boolean useRegionTypeSpecificEntries(long storageFlags) {
		return ((storageFlags & STORAGE_MODE_TSV_EXTERNAL_REGION_TYPES) != 0);
	}
	
	/**
	 * Set the TSV mode storage parameter flag that indicates to externalize
	 * regions of frequent types to dedicated files, which might facilitate
	 * additional optimizations, e.g. more efficient use of dedicated attribute
	 * columns. However, storing and Image Markup document in this way may not
	 * faithfully preserve the nesting order of regions whose bounding boxes
	 * coincide. To render nesting well-defined, it will be normalized to the
	 * order observed in (the majority of) unambiguous nesting situations.
	 * @param storageFlags the storage parameter vector to adjust
	 * @param tse use dedicated document entries for frequent regions types?
	 * @return the adjusted parameter vector
	 */
	public static long setUseRegionTypeSpecificEntries(long storageFlags, boolean tse) {
		if (tse)
			return (storageFlags | STORAGE_MODE_TSV_EXTERNAL_REGION_TYPES);
		else return (storageFlags & ~STORAGE_MODE_TSV_EXTERNAL_REGION_TYPES);
	}
	
	/**
	 * Get the minimum number of regions per type that have to be present for
	 * these regions to be stored in a dedicated document entry rather than the
	 * general region table on a TSV mode storage parameter vector, rounded to
	 * the next lower multiple of 0x0010 (16).
	 * @param storageFlags the storage parameter vector to read out
	 * @return the minimum type specific region entry encoded in the argument
	 *            flag vector
	 */
	public static int getRegionTypeEntrySizeThreshold(long storageFlags) {
		long test = (storageFlags &= STORAGE_MODE_TSV_EXTERNAL_REGION_COUNT_MASK);
		test >>>= 24; // adjust bit position
		test <<= 4; // multiply times 0x0010/16
		return ((int) (test & 0x000000007FFFFFFFL));
	}
	
	/**
	 * Set the minimum number of regions per type that have to be present for
	 * these regions to be stored in a dedicated document entry rather than the
	 * general region table on a TSV mode storage parameter vector, rounded to
	 * the next lower multiple of 0x0010 (16), i.e., store the argument number
	 * in the 4th most significant byte. Setting this threshold to 0 indicates
	 * to use the default of 1024 (0x0400). Setting this number very low may
	 * result in extremely small document entries (the extreme being every
	 * region type being stored in its own document entry for threshold 1);
	 * setting it very high may result in a large IO and/or storage overhead
	 * for punctual changes because it keeps a large number of regions in the
	 * general table.
	 * @param storageFlags the storage parameter vector to adjust
	 * @param wcs the threshold for type specific region storage to set
	 * @return the adjusted parameter vector
	 */
	public static long setRegionTypeEntrySizeThreshold(long storageFlags, int test) {
//		if (0x0000FFFF < test)
//			throw new IllegalArgumentException("The maximum threshold for type specific region entries is 65335 (0xFFFF)");
		if (0x00000FFF < test)
			throw new IllegalArgumentException("The maximum threshold for type specific region entries is 4095 (0x0FFF)");
		if (test < 0)
//			throw new IllegalArgumentException("The minimum threshold for type specific region entries is 1 (0x0001), or 0 (0x0000) for using the default");
			throw new IllegalArgumentException("The minimum threshold for type specific region entries is 16 (0x0010), or 0 (0x0000) for using the default");
		storageFlags &= ~STORAGE_MODE_TSV_EXTERNAL_REGION_COUNT_MASK;
//		storageFlags |= (((long) test) << 32);
		test >>>= 4; // divide by 0x0010/16
		storageFlags |= (((long) test) << 24); // adjust bit position and add into storage flags
		return storageFlags;
	}
	
	/**
	 * Check the TSV mode storage parameter flag that indicates to use
	 * dedicated columns for the values of frequent annotation attributes.
	 * @param storageFlags the storage parameter vector to check
	 * @return true if the respective bit is set
	 */
	public static boolean useAnnotationAttributeColumns(long storageFlags) {
		return ((storageFlags & STORAGE_MODE_TSV_ANNOTATION_ATTRIBUTE_COLUMNS) != 0);
	}
	
	/**
	 * Set the TSV mode storage parameter flag that indicates to use dedicated
	 * columns for the values of frequent annotation attributes.
	 * @param storageFlags the storage parameter vector to adjust
	 * @param uac use dedicated columns for frequent attributes?
	 * @return the adjusted parameter vector
	 */
	public static long setUseAnnotationAttributeColumns(long storageFlags, boolean uac) {
		if (uac)
			return (storageFlags | STORAGE_MODE_TSV_ANNOTATION_ATTRIBUTE_COLUMNS);
		else return (storageFlags & ~STORAGE_MODE_TSV_ANNOTATION_ATTRIBUTE_COLUMNS);
	}
	
	/**
	 * Check the TSV mode storage parameter flag that indicates to externalize
	 * long annotation attribute values and reference them by a code. This
	 * saves storing lengthy strings multiple times.
	 * @param storageFlags the storage parameter vector to check
	 * @return true if the respective bit is set
	 */
	public static boolean externalizeAnnotationAttributeValues(long storageFlags) {
		return ((storageFlags & STORAGE_MODE_TSV_EXTERNAL_ANNOTATION_ATTRIBUTES) != 0);
	}
	
	/**
	 * Set the TSV mode storage parameter flag that indicates to externalize
	 * long annotation attribute values and reference them by a code. This
	 * saves storing lengthy strings multiple times.
	 * @param storageFlags the storage parameter vector to adjust
	 * @param eav externalize long region attribute values?
	 * @return the adjusted parameter vector
	 */
	public static long setExternalizeAnnotationAttributeValues(long storageFlags, boolean eav) {
		if (eav)
			return (storageFlags | STORAGE_MODE_TSV_EXTERNAL_ANNOTATION_ATTRIBUTES);
		else return (storageFlags & ~STORAGE_MODE_TSV_EXTERNAL_ANNOTATION_ATTRIBUTES);
	}
	
	/**
	 * Check the TSV mode storage parameter flag that indicates to externalize
	 * annotations of frequent types to dedicated files, which might facilitate
	 * additional optimizations, e.g. more efficient use of dedicated attribute
	 * columns.
	 * @param storageFlags the storage parameter vector to check
	 * @return true if the respective bit is set
	 */
	public static boolean useAnnotationTypeSpecificEntries(long storageFlags) {
		return ((storageFlags & STORAGE_MODE_TSV_EXTERNAL_ANNOTATION_TYPES) != 0);
	}
	
	/**
	 * Set the TSV mode storage parameter flag that indicates to externalize
	 * annotations of frequent types to dedicated files, which might facilitate
	 * additional optimizations, e.g. more efficient use of dedicated attribute
	 * columns. However, storing and Image Markup document in this way may not
	 * faithfully preserve the nesting order of annotations whose first and
	 * last words coincide. To render nesting well-defined, it will be
	 * normalized to the order observed in (the majority of) unambiguous
	 * nesting situations.
	 * @param storageFlags the storage parameter vector to adjust
	 * @param tse use dedicated document entries for frequent annotation types?
	 * @return the adjusted parameter vector
	 */
	public static long setUseAnnotationTypeSpecificEntries(long storageFlags, boolean tse) {
		if (tse)
			return (storageFlags | STORAGE_MODE_TSV_EXTERNAL_ANNOTATION_TYPES);
		else return (storageFlags & ~STORAGE_MODE_TSV_EXTERNAL_ANNOTATION_TYPES);
	}
	
	/**
	 * Get the minimum number of annotation per type that have to be present
	 * for these anotations to be stored in a dedicated document entry rather
	 * than the general annotation table on a TSV mode storage parameter
	 * vector, rounded to the next lower multiple of 0x0010 (16).
	 * @param storageFlags the storage parameter vector to read out
	 * @return the minimum type specific annotation entry encoded in the
	 *            argument flag vector
	 */
	public static int getAnnotationTypeEntrySizeThreshold(long storageFlags) {
		long test = (storageFlags &= STORAGE_MODE_TSV_EXTERNAL_ANNOTATION_COUNT_MASK);
		test >>>= 32; // adjust bit position
		test <<= 4; // multiply times 0x0010/16
		return ((int) (test & 0x000000007FFFFFFFL));
	}
	
	/**
	 * Set the minimum number of annotations per type that have to be present
	 * for these annotations to be stored in a dedicated document entry rather
	 * than the general annotation table on a TSV mode storage parameter vector,
	 * rounded to the next lower multiple of 0x0010 (16), i.e., store the
	 * argument number in the 5rd most significant byte. Setting this threshold
	 * to 0 indicates to use the default of 256 (0x0100). Setting this number
	 * very low may result in extremely small document entries (the extreme
	 * being every annotation type being stored in its own document entry for
	 * threshold 1); setting it very high may result in a large IO and/or
	 * storage overhead for punctual changes because it keeps a large number of
	 * annotations in the general table.
	 * @param storageFlags the storage parameter vector to adjust
	 * @param wcs the threshold for type specific annotation storage to set
	 * @return the adjusted parameter vector
	 */
	public static long setAnnotationTypeEntrySizeThreshold(long storageFlags, int test) {
//		if (0x0000FFFF < test)
//			throw new IllegalArgumentException("The maximum threshold for type specific annotation entries is 65335 (0xFFFF)");
		if (0x00000FFF < test)
			throw new IllegalArgumentException("The maximum threshold for type specific annotation entries is 4095 (0x0FFF)");
		if (test < 0)
//			throw new IllegalArgumentException("The minimum threshold for type specific annotation entries is 1 (0x0001), or 0 (0x0000) for using the default");
			throw new IllegalArgumentException("The minimum threshold for type specific annotation entries is 16 (0x0010), or 0 (0x0000) for using the default");
		storageFlags &= ~STORAGE_MODE_TSV_EXTERNAL_ANNOTATION_COUNT_MASK;
//		storageFlags |= (((long) test) << 48);
		test >>>= 4; // divide by 0x0010/16
		storageFlags |= (((long) test) << 32); // adjust bit position and add into storage flags
		return storageFlags;
	}
	
	/**
	 * Check the TSV mode storage parameter flag that indicates to use
	 * dedicated columns for the values of frequent supplement attributes.
	 * @param storageFlags the storage parameter vector to check
	 * @return true if the respective bit is set
	 */
	public static boolean useSupplementAttributeColumns(long storageFlags) {
		return ((storageFlags & STORAGE_MODE_TSV_SUPPLEMENT_ATTRIBUTE_COLUMNS) != 0);
	}
	
	/**
	 * Set the TSV mode storage parameter flag that indicates to use dedicated
	 * columns for the values of frequent supplement attributes.
	 * @param storageFlags the storage parameter vector to adjust
	 * @param uac use dedicated columns for frequent attributes?
	 * @return the adjusted parameter vector
	 */
	public static long setUseSupplementAttributeColumns(long storageFlags, boolean uac) {
		if (uac)
			return (storageFlags | STORAGE_MODE_TSV_SUPPLEMENT_ATTRIBUTE_COLUMNS);
		else return (storageFlags & ~STORAGE_MODE_TSV_SUPPLEMENT_ATTRIBUTE_COLUMNS);
	}
	
	/**
	 * Check the TSV mode storage parameter flag that indicates to use bundle
	 * up the data of graphics supplements.
	 * @param storageFlags the storage parameter vector to check
	 * @return true if the respective bit is set
	 */
	public static boolean bundleSupplementGraphicsData(long storageFlags) {
		return ((storageFlags & STORAGE_MODE_TSV_BUNDLE_GRAPHICS_DATA) != 0);
	}
	
	/**
	 * Set the TSV mode storage parameter flag that indicates to use bundle up
	 * the data of graphics supplements.
	 * @param storageFlags the storage parameter vector to adjust
	 * @param uac bundle up data of graphics supplements?
	 * @return the adjusted parameter vector
	 */
	public static long setBundleSupplementGraphicsData(long storageFlags, boolean bgd) {
		if (bgd)
			return (storageFlags | STORAGE_MODE_TSV_BUNDLE_GRAPHICS_DATA);
		else return (storageFlags & ~STORAGE_MODE_TSV_BUNDLE_GRAPHICS_DATA);
	}
	/**
	 * Compute the hash of a document entry list, e.g. to serve as a means of
	 * verifying the entry list on loading. This method hashes entry names,
	 * sizes, and content hashes, but ignores entry timestamps to ensure hash
	 * stability across multiple storage operations. How to store the hash is
	 * up to client code.
	 * @param entries the entry list to hash
	 * @return the hash of the argument entry list
	 */
	public static String hashEntryList(ImDocumentEntry[] entries) throws IOException {
		return hashEntryList(entries, new MD5());
	}
	
	/**
	 * Compute the hash of a document entry list, e.g. to serve as a means of
	 * verifying the entry list on loading. This method hashes entry names,
	 * sizes, and content hashes, but ignores entry timestamps to ensure hash
	 * stability across multiple storage operations. How to store the hash is
	 * up to client code.
	 * @param entries the entry list to hash
	 * @param hash the hasher to use
	 * @return the hash of the argument entry list
	 */
	public static String hashEntryList(ImDocumentEntry[] entries, MessageDigest hash) throws IOException {
		ImDocumentEntry[] sorterdEntries = new ImDocumentEntry[entries.length];
		System.arraycopy(entries, 0, sorterdEntries, 0, entries.length);
		Arrays.sort(sorterdEntries);
		DataHashOutputStream entryHasher = new DataHashOutputStream(new OutputStream() {
			public void write(int i) throws IOException {}
			public void write(byte[] b) throws IOException {}
			public void write(byte[] b, int off, int len) throws IOException {}
			public void flush() throws IOException {}
			public void close() throws IOException {}
		}, hash);
		BufferedWriter entryOut = new BufferedWriter(new OutputStreamWriter(entryHasher, "UTF-8"));
		for (int e = 0; e < sorterdEntries.length; e++) {
			entryOut.write(sorterdEntries[e].name + "\t" + sorterdEntries[e].size + "\t" + sorterdEntries[e].dataHash);
			entryOut.write("\r\n"); // fixed to cross-platform line break
		}
		entryOut.flush();
		entryOut.close();
		return abridgeEntryDataHash(entryHasher.getDataHashString());
	}
	
	public static void main(String[] args) throws Exception {
		String[][] atoData = {
			{"key keyStep keyLead", "3", "53"},
			{"taxonomicName emphasis", "3", "1047"},
			{"pageTitle pageNumber", "2", "629"},
			{"heading taxonomicName", "3", "34"},
			{"heading emphasis", "3", "9"},
			{"smallCapsWord emphasis", "1", "4"},
			{"taxonomicName smallCapsWord", "3", "2"},
		};
		ArrayList annotTypeOrders = new ArrayList();
		for (int o = 0; o < atoData.length; o++)
			annotTypeOrders.add(new ImObjectTypeOrder(atoData[o][0].split("\\s+"), Integer.parseInt(atoData[o][1]), Integer.parseInt(atoData[o][2])));
		Collections.sort(annotTypeOrders);
		for (int o = 0; o < annotTypeOrders.size(); o++) {
			ImObjectTypeOrder ioto = ((ImObjectTypeOrder) annotTypeOrders.get(o));
			System.out.println("Comparing '" + ioto + "' (strict " + ioto.strictness + ", weight " + ioto.weight + ")");
			for (int co = (o+1); co < annotTypeOrders.size(); co++) {
				ImObjectTypeOrder cIoto = ((ImObjectTypeOrder) annotTypeOrders.get(co));
				System.out.println(" - to '" + cIoto + "' (strict " + cIoto.strictness + ", weight " + cIoto.weight + ")");
				if (ioto.subsumes(cIoto)) {
					ioto.weight += cIoto.weight;
					annotTypeOrders.remove(co--);
					System.out.println("   ==> absorbed");
					continue;
				}
				if (ioto.contains(cIoto)) {
					System.out.println("   ==> order clash");
					continue;
				}
				if (ioto.overlaps(cIoto)) {
					System.out.println("   ==> might need combining");
					LinkedHashSet cgotSet = new LinkedHashSet();
					cgotSet.addAll(Arrays.asList(ioto.types));
					cgotSet.addAll(Arrays.asList(cIoto.types));
					ImObjectTypeOrder[] typeOrders = ((ImObjectTypeOrder[]) annotTypeOrders.toArray(new ImObjectTypeOrder[annotTypeOrders.size()]));
					String[] cgots = ((String[]) cgotSet.toArray(new String[cgotSet.size()]));
					ImObjectTypeOrder combIoto = createAggregateTypeOrder(typeOrders, cgots, false);
					if (combIoto == null) {
						System.out.println("     ==> failed to combine type orders");
						continue;
					}
					else {
						combIoto = new ImObjectTypeOrder(combIoto.types,  Math.min(ioto.strictness, cIoto.strictness), (ioto.weight + cIoto.weight));
						System.out.println("     ==> combined to '" + combIoto + "' (strict " + combIoto.strictness + ", weight " + combIoto.weight + ")");
						annotTypeOrders.set(o, combIoto);
						annotTypeOrders.remove(co);
						Collections.sort(annotTypeOrders);
						o = -1; // loop increment takes us back up to 0
						break;
					}
				}
			}
		}
		System.out.println("Annotation type orders: " + annotTypeOrders.size());
		for (int o = 0; o < annotTypeOrders.size(); o++) {
			ImObjectTypeOrder ioto = ((ImObjectTypeOrder) annotTypeOrders.get(o));
			System.out.println(" - '" + ioto + "' (strict " + ioto.strictness + ", weight " + ioto.weight + ")");
		}
	}
	public static void mainNestingOrderTest(String[] args) throws Exception {
		final File baseFolder = new File("E:/Testdaten/PdfExtract/");
//		final String pdfName = "zt00872.pdf";
//		
//		long startTime = System.currentTimeMillis();
//		long startMem = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
//		ImDocument imDoc = loadDocument(new FileInputStream(new File(baseFolder, (pdfName + ".imf"))));
//		System.out.println("LOAD TIME: " + (System.currentTimeMillis() - startTime));
//		System.out.println("LOAD MEMORY: " + ((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) - startMem));
//		System.gc();
//		System.out.println("LOAD MEMORY GC: " + ((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) - startMem));
////		ImDocument imDoc = loadDocument(new File(baseFolder, (pdfName + ".new.imd")));
//		
////		File imFile = new File(baseFolder, (pdfName + ".widd.imf"));
////		OutputStream imOut = new BufferedOutputStream(new FileOutputStream(imFile));
////		storeDocument(imDoc, imOut);
////		imOut.flush();
////		imOut.close();
////		
////		File imFolder = new File(baseFolder, (pdfName + ".widd.imd"));
////		imFolder.mkdirs();
////		storeDocument(imDoc, imFolder);
//		
////		ImDocument imDoc = loadDocument(new File(baseFolder, (pdfName + ".new.imd")));
//		
////		File imFile = new File(baseFolder, (pdfName + ".widd.imf"));
////		OutputStream imOut = new BufferedOutputStream(new FileOutputStream(imFile));
////		storeDocument(imDoc, imOut);
////		imOut.flush();
////		imOut.close();
////		
////		File imFolder = new File(baseFolder, (pdfName + ".widd.imd"));
////		imFolder.mkdirs();
////		storeDocument(imDoc, imFolder);
		
		//	V2 storage helper test
//		String docInFolderName = "EJT/ejt-399_zonstein_kunt.pdf.imdir";
		String docInFolderName = "EJT/ejt-398_atherton_jondelius.pdf.imdir";
//		String docInFolderName = "EJT/ejt-392_germann.pdf.imdir";
		File docInFolder = new File(baseFolder, docInFolderName);
		ImDocument imDoc = loadDocument(docInFolder);
		ImPage[] pages = imDoc.getPages();
//		CountingSet regTypeTraces = new CountingSet(new TreeMap());
		CountingSet regTypes = new CountingSet(new TreeMap());
		CountingSet regTypePairs = new CountingSet(new TreeMap());
		CountingSet invRegTypePairs = new CountingSet(new TreeMap());
		CountingSet clashRegTypes = new CountingSet(new TreeMap());
		CountingSet clashRegTypePairs = new CountingSet(new TreeMap());
		CountingSet regClashTraces = new CountingSet(new TreeMap());
		CountingSet invClashRegTypePairs = new CountingSet(new TreeMap());
		CountingSet sizeRelRegTypePairs = new CountingSet(new TreeMap());
		CountingSet invSizeRelRegTypePairs = new CountingSet(new TreeMap());
		CountingSet rel1NregTypePairs = new CountingSet(new TreeMap());
		CountingSet relN1regTypePairs = new CountingSet(new TreeMap());
		for (int p = 0; p < pages.length; p++) {
			ImRegionTreeNode pageRtn = new ImRegionTreeNode(pages[p].bounds, "page");
			ImRegion[] pageRegs = pages[p].getRegions();
			System.out.println("Doing page " + p + " with " + pageRegs.length + " regions");
			addObjectTypes(pageRegs, regTypes);
			Arrays.sort(pageRegs, ImUtils.sizeOrder);
			for (int r = 0; r < pageRegs.length; r++)
				pageRtn.addChild(new ImRegionTreeNode(pageRegs[r].bounds, pageRegs[r].getType()));
//			pageRtn.printSubTree("");
//			pageRtn.appendChildren(regTypeTraces, pageRtn.type);
			pageRtn.addChildTypePairs(regTypePairs, invRegTypePairs, new ArrayList());
			pageRtn.addSizeMatches(clashRegTypes, clashRegTypePairs, invClashRegTypePairs, regClashTraces, new ArrayList());
			pageRtn.addSizeNestings(sizeRelRegTypePairs, invSizeRelRegTypePairs);
			pageRtn.addMultiChildTypePairs(rel1NregTypePairs, relN1regTypePairs);
		}
//		for (Iterator ttit = regTypeTraces.iterator(); ttit.hasNext();) {
//			String typeTrace = ((String) ttit.next());
//			System.out.println(typeTrace + ": " + regTypeTraces.getCount(typeTrace));
//		}
		System.out.println("TYPES: " + regTypes);
		for (Iterator tpit = regTypePairs.iterator(); tpit.hasNext();) {
			String typePair = ((String) tpit.next());
			System.out.println("PAIR " + typePair + ": " + regTypePairs.getCount(typePair) + ", " + invRegTypePairs.getCount(typePair) + " reverse");
		}
		for (Iterator tpit = sizeRelRegTypePairs.iterator(); tpit.hasNext();) {
			String typePair = ((String) tpit.next());
			System.out.println("SIZE PAIR " + typePair + ": " + sizeRelRegTypePairs.getCount(typePair) + ", " + invSizeRelRegTypePairs.getCount(typePair) + " reverse");
		}
		for (Iterator tpit = rel1NregTypePairs.iterator(); tpit.hasNext();) {
			String typePair = ((String) tpit.next());
			System.out.println("1:N PAIR " + typePair + ": " + rel1NregTypePairs.getCount(typePair) + ", " + relN1regTypePairs.getCount(typePair) + " reverse");
		}
		System.out.println("CLASHING TYPES: " + clashRegTypes);
		for (Iterator tpit = clashRegTypePairs.iterator(); tpit.hasNext();) {
			String typePair = ((String) tpit.next());
			System.out.println("CLASH " + typePair + ": " + clashRegTypePairs.getCount(typePair) + ", " + invClashRegTypePairs.getCount(typePair) + " reverse");
			System.out.println("  GLOBAL: " + regTypePairs.getCount(typePair) + ", " + invRegTypePairs.getCount(typePair) + " reverse");
		}
		System.out.println("CLASH TRACES:");
		ArrayList regTypeOrders = new ArrayList();
		for (Iterator ctit = regClashTraces.iterator(); ctit.hasNext();) {
			String clashTrace = ((String) ctit.next());
			System.out.println(" - " + clashTrace + ": " + regClashTraces.getCount(clashTrace));
//			ClashObjectTypeTray[] regClashTypes = new ClashObjectTypeTray[clashRegTypes.elementCount()];
//			int rcti = 0;
//			for (Iterator tit = clashRegTypes.iterator(); tit.hasNext();)
//				regClashTypes[rcti++] = new ClashObjectTypeTray((String) tit.next());
			String[] ctRegionTypes = clashTrace.split("\\s*\\-\\>\\s*");
			ClashObjectTypeTray[] regClashTypes = new ClashObjectTypeTray[ctRegionTypes.length];
			for (int t = 0; t < ctRegionTypes.length; t++)
				regClashTypes[t] = new ClashObjectTypeTray(ctRegionTypes[t]);
			for (int t = 0; t < regClashTypes.length; t++) {
				System.out.println(regClashTypes[t].type + ":");
				for (int ct = 0; ct < regClashTypes.length; ct++) {
					if (ct == t)
						continue;
					String typePair = (regClashTypes[t].type + " -> " + regClashTypes[ct].type);
					int rel1Nscore = rel1NregTypePairs.getCount(typePair);
					int invRel1Nscore = relN1regTypePairs.getCount(typePair);
					if (rel1Nscore < invRel1Nscore) {
						regClashTypes[t].rel1Npos++;
						regClashTypes[ct].rel1Npos--;
					}
					else if (invRel1Nscore < rel1Nscore) {
						regClashTypes[t].rel1Npos--;
						regClashTypes[ct].rel1Npos++;
					}
					int sizeScore = sizeRelRegTypePairs.getCount(typePair);
					int invSizeScore = invSizeRelRegTypePairs.getCount(typePair);
					if (sizeScore < invSizeScore) {
						regClashTypes[t].sizePos++;
						regClashTypes[ct].sizePos--;
					}
					else if (invSizeScore < sizeScore) {
						regClashTypes[t].sizePos--;
						regClashTypes[ct].sizePos++;
					}
					int freqScore = regTypePairs.getCount(typePair);
					int invFreqScore = invRegTypePairs.getCount(typePair);
					if (freqScore < invFreqScore) {
						regClashTypes[t].freqPos++;
						regClashTypes[ct].freqPos--;
					}
					else if (invFreqScore < freqScore) {
						regClashTypes[t].freqPos--;
						regClashTypes[ct].freqPos++;
					}
					System.out.println(" - " + regClashTypes[ct].type + ": 1:N " + rel1Nscore + "/" + invRel1Nscore + ", size " + sizeScore + "/" + invSizeScore + ", count " + freqScore + "/" + invFreqScore);
				}
			}
			System.out.println("RAW ORDER:");
			for (int t = 0; t < regClashTypes.length; t++)
				System.out.println(" - " + regClashTypes[t].type + " " + regClashTypes[t].rel1Npos + " " + regClashTypes[t].sizePos + " " + regClashTypes[t].freqPos);
			boolean orderWellDefined;
			for (int t = 0; t < regClashTypes.length; t++)
				regClashTypes[t].pos = regClashTypes[t].rel1Npos;
			Arrays.sort(regClashTypes);
			System.out.println("1:N ORDER:");
			for (int t = 0; t < regClashTypes.length; t++)
				System.out.println(" - " + regClashTypes[t].type + " " + regClashTypes[t].rel1Npos + " " + regClashTypes[t].sizePos + " " + regClashTypes[t].freqPos);
			orderWellDefined = true;
			for (int t = 1; t < regClashTypes.length; t++) {
				if (regClashTypes[t-1].pos < regClashTypes[t].pos)
					continue;
				System.out.println("ORDER UNDEFINED: " + regClashTypes[t-1].type + " vs. " + regClashTypes[t].type);
				String typePair = (regClashTypes[t-1].type + " -> " + regClashTypes[t].type);
				System.out.println(" ==> ACTUAL PAIRINGS: " + regTypePairs.getCount(typePair) + ", " + invRegTypePairs.getCount(typePair) + " reverse");
				System.out.println(" ==> ACTUAL CLASHES: " + clashRegTypePairs.getCount(typePair) + ", " + invClashRegTypePairs.getCount(typePair) + " reverse");
				orderWellDefined = false; // refuse using 1:N scores for ambiguity
			}
			if (orderWellDefined && (ctRegionTypes != null)) {
				for (int t = 0; t < regClashTypes.length; t++)
					ctRegionTypes[t] = regClashTypes[t].type;
				regTypeOrders.add(new ImObjectTypeOrder(ctRegionTypes, 3, regClashTraces.getCount(clashTrace)));
				ctRegionTypes = null;
			}
			for (int t = 0; t < regClashTypes.length; t++)
				regClashTypes[t].pos = regClashTypes[t].sizePos;
			Arrays.sort(regClashTypes);
			System.out.println("SIZE ORDER:");
			for (int t = 0; t < regClashTypes.length; t++)
				System.out.println(" - " + regClashTypes[t].type + " " + regClashTypes[t].rel1Npos + " " + regClashTypes[t].sizePos + " " + regClashTypes[t].freqPos);
			orderWellDefined = true;
			for (int t = 1; t < regClashTypes.length; t++) {
				if (regClashTypes[t-1].pos < regClashTypes[t].pos)
					continue;
				System.out.println("ORDER UNDEFINED: " + regClashTypes[t-1].type + " vs. " + regClashTypes[t].type);
				String typePair = (regClashTypes[t-1].type + " -> " + regClashTypes[t].type);
				System.out.println(" ==> ACTUAL PAIRINGS: " + regTypePairs.getCount(typePair) + ", " + invRegTypePairs.getCount(typePair) + " reverse");
				System.out.println(" ==> ACTUAL CLASHES: " + clashRegTypePairs.getCount(typePair) + ", " + invClashRegTypePairs.getCount(typePair) + " reverse");
				orderWellDefined = false; // refuse using size scores for ambiguity
			}
			if (orderWellDefined && (ctRegionTypes != null)) {
				for (int t = 0; t < regClashTypes.length; t++)
					ctRegionTypes[t] = regClashTypes[t].type;
				regTypeOrders.add(new ImObjectTypeOrder(ctRegionTypes, 2, regClashTraces.getCount(clashTrace)));
				ctRegionTypes = null;
			}
			for (int t = 0; t < regClashTypes.length; t++)
				regClashTypes[t].pos = regClashTypes[t].freqPos;
			Arrays.sort(regClashTypes);
			System.out.println("NESTING FREQUENCY ORDER:");
			for (int t = 0; t < regClashTypes.length; t++)
				System.out.println(" - " + regClashTypes[t].type + " " + regClashTypes[t].rel1Npos + " " + regClashTypes[t].sizePos + " " + regClashTypes[t].freqPos);
			orderWellDefined = true;
			for (int t = 1; t < regClashTypes.length; t++) {
				if (regClashTypes[t-1].pos < regClashTypes[t].pos)
					continue;
				System.out.println("ORDER UNDEFINED: " + regClashTypes[t-1].type + " vs. " + regClashTypes[t].type);
				String typePair = (regClashTypes[t-1].type + " -> " + regClashTypes[t].type);
				System.out.println(" ==> ACTUAL PAIRINGS: " + regTypePairs.getCount(typePair) + ", " + invRegTypePairs.getCount(typePair) + " reverse");
				System.out.println(" ==> ACTUAL CLASHES: " + clashRegTypePairs.getCount(typePair) + ", " + invClashRegTypePairs.getCount(typePair) + " reverse");
				orderWellDefined = false; // refuse using nestring frequency scores for ambiguity
			}
			if (orderWellDefined && (ctRegionTypes != null)) {
				for (int t = 0; t < regClashTypes.length; t++)
					ctRegionTypes[t] = regClashTypes[t].type;
				regTypeOrders.add(new ImObjectTypeOrder(ctRegionTypes, 1, regClashTraces.getCount(clashTrace)));
				ctRegionTypes = null;
			}
		}
		Collections.sort(regTypeOrders);
		for (int o = 0; o < regTypeOrders.size(); o++) {
			ImObjectTypeOrder ioto = ((ImObjectTypeOrder) regTypeOrders.get(o));
			System.out.println("Comparing '" + ioto + "' (strict " + ioto.strictness + ")");
			for (int co = (o+1); co < regTypeOrders.size(); co++) {
				ImObjectTypeOrder cIoto = ((ImObjectTypeOrder) regTypeOrders.get(co));
				System.out.println(" - to '" + cIoto + "' (strict " + cIoto.strictness + ")");
				if (ioto.subsumes(cIoto)) {
					ioto.weight += cIoto.weight;
					regTypeOrders.remove(co--);
					System.out.println("   ==> absorbed");
					continue;
				}
				if (ioto.contains(cIoto)) {
					System.out.println("   ==> order clash");
					continue;
				}
				if (ioto.overlaps(cIoto)) {
					System.out.println("   ==> might need combining");
					LinkedHashSet cgotSet = new LinkedHashSet();
					cgotSet.addAll(Arrays.asList(ioto.types));
					cgotSet.addAll(Arrays.asList(cIoto.types));
					ImObjectTypeOrder[] typeOrders = ((ImObjectTypeOrder[]) regTypeOrders.toArray(new ImObjectTypeOrder[regTypeOrders.size()]));
					String[] cgots = ((String[]) cgotSet.toArray(new String[cgotSet.size()]));
					ImObjectTypeOrder combIoto = createAggregateTypeOrder(typeOrders, cgots, false);
					if (combIoto == null)
						System.out.println("     ==> failed to combine type orders");
					else System.out.println("     ==> combined to " + combIoto);
					continue;
				}
			}
		}
		System.out.println("Region type orders: " + regTypeOrders.size());
		for (int o = 0; o < regTypeOrders.size(); o++) {
			ImObjectTypeOrder ioto = ((ImObjectTypeOrder) regTypeOrders.get(o));
			System.out.println(" - '" + ioto + "' (strict " + ioto.strictness + ", weight " + ioto.weight + ")");
		}
		
		ImAnnotation[] annots = imDoc.getAnnotations();
		LinkedHashMap rootAnnotsByTextStreamId = new LinkedHashMap();
		for (int a = 0; a < annots.length; a++) {
			ImWord fWord = annots[a].getFirstWord();
			ImWord lWord = annots[a].getLastWord();
			ImAnnotationTreeNode tsAtn = ((ImAnnotationTreeNode) rootAnnotsByTextStreamId.get(fWord.getTextStreamId()));
			if (tsAtn == null) {
				ImWord tsfWord = fWord;
				while (tsfWord.getPreviousWord() != null)
					tsfWord = tsfWord.getPreviousWord();
				ImWord tslWord = lWord;
				while (tslWord.getNextWord() != null)
					tslWord = tslWord.getNextWord();
				tsAtn = new ImAnnotationTreeNode(tsfWord, tslWord, fWord.getTextStreamId(), "textStreamRoot");
				rootAnnotsByTextStreamId.put(fWord.getTextStreamId(), tsAtn);
			}
			tsAtn.addChild(new ImAnnotationTreeNode(fWord, lWord, fWord.getTextStreamId(), annots[a].getType()));
		}
		CountingSet annotTypes = new CountingSet(new TreeMap());
		CountingSet clashAnnotTypes = new CountingSet(new TreeMap());
		CountingSet annotTypePairs = new CountingSet(new TreeMap());
		CountingSet invAnnotTypePairs = new CountingSet(new TreeMap());
		CountingSet clashAnnotTypePairs = new CountingSet(new TreeMap());
		CountingSet invClashAnnotTypePairs = new CountingSet(new TreeMap());
		CountingSet annotClashTraces = new CountingSet(new TreeMap());
		CountingSet sizeRelAnnotTypePairs = new CountingSet(new TreeMap());
		CountingSet invSizeRelAnnotTypePairs = new CountingSet(new TreeMap());
		CountingSet rel1NannotTypePairs = new CountingSet(new TreeMap());
		CountingSet relN1annotTypePairs = new CountingSet(new TreeMap());
		addObjectTypes(annots, annotTypes);
		for (Iterator tsit = rootAnnotsByTextStreamId.keySet().iterator(); tsit.hasNext();) {
			ImAnnotationTreeNode tsAtn = ((ImAnnotationTreeNode) rootAnnotsByTextStreamId.get(tsit.next()));
			tsAtn.addChildTypePairs(annotTypePairs, invAnnotTypePairs, new ArrayList());
			tsAtn.addSizeMatches(clashAnnotTypes, clashAnnotTypePairs, invClashAnnotTypePairs, annotClashTraces, new ArrayList());
			tsAtn.addSizeNestings(sizeRelAnnotTypePairs, invSizeRelAnnotTypePairs);
			tsAtn.addMultiChildTypePairs(rel1NannotTypePairs, relN1annotTypePairs);
		}
		for (Iterator tsit = rootAnnotsByTextStreamId.keySet().iterator(); tsit.hasNext();) {
			ImAnnotationTreeNode tsAtn = ((ImAnnotationTreeNode) rootAnnotsByTextStreamId.get(tsit.next()));
			System.out.println(tsAtn.textStreamId + ": " + tsAtn.children.size() + " disjoint chunks");
		}
//		for (Iterator ttit = regTypeTraces.iterator(); ttit.hasNext();) {
//			String typeTrace = ((String) ttit.next());
//			System.out.println(typeTrace + ": " + regTypeTraces.getCount(typeTrace));
//		}
		System.out.println("TYPES: " + annotTypes);
		for (Iterator tpit = annotTypePairs.iterator(); tpit.hasNext();) {
			String typePair = ((String) tpit.next());
			System.out.println("PAIR " + typePair + ": " + annotTypePairs.getCount(typePair) + ", " + invAnnotTypePairs.getCount(typePair) + " reverse");
		}
		for (Iterator tpit = sizeRelAnnotTypePairs.iterator(); tpit.hasNext();) {
			String typePair = ((String) tpit.next());
			System.out.println("SIZE PAIR " + typePair + ": " + sizeRelAnnotTypePairs.getCount(typePair) + ", " + invSizeRelAnnotTypePairs.getCount(typePair) + " reverse");
		}
		for (Iterator tpit = rel1NannotTypePairs.iterator(); tpit.hasNext();) {
			String typePair = ((String) tpit.next());
			System.out.println("1:N PAIR " + typePair + ": " + rel1NannotTypePairs.getCount(typePair) + ", " + relN1annotTypePairs.getCount(typePair) + " reverse");
		}
		System.out.println("CLASHING TYPES: " + clashAnnotTypes);
		for (Iterator tpit = clashAnnotTypePairs.iterator(); tpit.hasNext();) {
			String typePair = ((String) tpit.next());
			System.out.println("CLASH " + typePair + ": " + clashAnnotTypePairs.getCount(typePair) + ", " + invClashAnnotTypePairs.getCount(typePair) + " reverse");
			System.out.println("  GLOBAL: " + annotTypePairs.getCount(typePair) + ", " + invAnnotTypePairs.getCount(typePair) + " reverse");
		}
		System.out.println("CLASH TRACES:");
		//	TODO make these buggers into comparators ...
		//	TODO use whichever nesting order works first, starting with 1:N, then size, then counts
		ArrayList annotTypeOrders = new ArrayList();
		for (Iterator ctit = annotClashTraces.iterator(); ctit.hasNext();) {
			String clashTrace = ((String) ctit.next());
			System.out.println(" - " + clashTrace + ": " + annotClashTraces.getCount(clashTrace));
			String[] ctAnnotTypes = clashTrace.split("\\s*\\-\\>\\s*");
			ClashObjectTypeTray[] annotClashTypes = new ClashObjectTypeTray[ctAnnotTypes.length];
			for (int t = 0; t < ctAnnotTypes.length; t++)
				annotClashTypes[t] = new ClashObjectTypeTray(ctAnnotTypes[t]);
			for (int t = 0; t < annotClashTypes.length; t++) {
				System.out.println(annotClashTypes[t].type + ":");
				for (int ct = 0; ct < annotClashTypes.length; ct++) {
					if (ct == t)
						continue;
					String typePair = (annotClashTypes[t].type + " -> " + annotClashTypes[ct].type);
					int rel1Nscore = rel1NannotTypePairs.getCount(typePair);
					int invRel1Nscore = relN1annotTypePairs.getCount(typePair);
					if (rel1Nscore < invRel1Nscore) {
						annotClashTypes[t].rel1Npos++;
						annotClashTypes[ct].rel1Npos--;
					}
					else if (invRel1Nscore < rel1Nscore) {
						annotClashTypes[t].rel1Npos--;
						annotClashTypes[ct].rel1Npos++;
					}
					int sizeScore = sizeRelAnnotTypePairs.getCount(typePair);
					int invSizeScore = invSizeRelAnnotTypePairs.getCount(typePair);
					if (sizeScore < invSizeScore) {
						annotClashTypes[t].sizePos++;
						annotClashTypes[ct].sizePos--;
					}
					else if (invSizeScore < sizeScore) {
						annotClashTypes[t].sizePos--;
						annotClashTypes[ct].sizePos++;
					}
					int freqScore = annotTypePairs.getCount(typePair);
					int invFreqScore = invAnnotTypePairs.getCount(typePair);
					if (freqScore < invFreqScore) {
						annotClashTypes[t].freqPos++;
						annotClashTypes[ct].freqPos--;
					}
					else if (invFreqScore < freqScore) {
						annotClashTypes[t].freqPos--;
						annotClashTypes[ct].freqPos++;
					}
					System.out.println(" - " + annotClashTypes[ct].type + ": 1:N " + rel1Nscore + "/" + invRel1Nscore + ", size " + sizeScore + "/" + invSizeScore + ", count " + freqScore + "/" + invFreqScore);
				}
			}
			System.out.println("RAW ORDER:");
			for (int t = 0; t < annotClashTypes.length; t++)
				System.out.println(" - " + annotClashTypes[t].type + " " + annotClashTypes[t].rel1Npos + " " + annotClashTypes[t].sizePos + " " + annotClashTypes[t].freqPos);
			int nestingConflicts;
			int actNestingConflicts;
			int clashNestingConflicts;
			boolean orderWellDefined;
			for (int t = 0; t < annotClashTypes.length; t++)
				annotClashTypes[t].pos = annotClashTypes[t].rel1Npos;
			Arrays.sort(annotClashTypes);
			System.out.println("1:N ORDER:");
			for (int t = 0; t < annotClashTypes.length; t++)
				System.out.println(" - " + annotClashTypes[t].type + " " + annotClashTypes[t].rel1Npos + " " + annotClashTypes[t].sizePos + " " + annotClashTypes[t].freqPos);
			nestingConflicts = 0;
			actNestingConflicts = 0;
			clashNestingConflicts = 0;
			orderWellDefined = true;
			for (int t = 1; t < annotClashTypes.length; t++) {
				if (annotClashTypes[t-1].pos < annotClashTypes[t].pos)
					continue;
				nestingConflicts++;
				System.out.println(" ==> ORDER UNDEFINED: " + annotClashTypes[t-1].type + " vs. " + annotClashTypes[t].type);
				String typePair = (annotClashTypes[t-1].type + " -> " + annotClashTypes[t].type);
				if (annotTypePairs.contains(typePair) || invAnnotTypePairs.contains(typePair))
					actNestingConflicts++;
				System.out.println("         ACTUAL PAIRINGS: " + annotTypePairs.getCount(typePair) + ", " + invAnnotTypePairs.getCount(typePair) + " reverse");
				if (clashAnnotTypePairs.contains(typePair) || invClashAnnotTypePairs.contains(typePair))
					clashNestingConflicts++;
				System.out.println("         ACTUAL CLASHES: " + clashAnnotTypePairs.getCount(typePair) + ", " + invClashAnnotTypePairs.getCount(typePair) + " reverse");
				orderWellDefined = false;
				for (int lt = t+1; lt < annotClashTypes.length; lt++) {
					if (annotClashTypes[t].pos < annotClashTypes[lt].pos)
						break;
					nestingConflicts++;
					System.out.println(" ==> ORDER UNDEFINED: " + annotClashTypes[t-1].type + " vs. " + annotClashTypes[lt].type);
					typePair = (annotClashTypes[t-1].type + " -> " + annotClashTypes[lt].type);
					if (annotTypePairs.contains(typePair) || invAnnotTypePairs.contains(typePair))
						actNestingConflicts++;
					System.out.println("         ACTUAL PAIRINGS: " + annotTypePairs.getCount(typePair) + ", " + invAnnotTypePairs.getCount(typePair) + " reverse");
					if (clashAnnotTypePairs.contains(typePair) || invClashAnnotTypePairs.contains(typePair))
						clashNestingConflicts++;
					System.out.println("         ACTUAL CLASHES: " + clashAnnotTypePairs.getCount(typePair) + ", " + invClashAnnotTypePairs.getCount(typePair) + " reverse");
				}
			}
			System.out.println(" ==> NESTING CONFLICTS: " + nestingConflicts + ", " + actNestingConflicts + " ACTUAL, " + clashNestingConflicts + " CLASHES");
			if (orderWellDefined && (ctAnnotTypes != null)) {
				for (int t = 0; t < annotClashTypes.length; t++)
					ctAnnotTypes[t] = annotClashTypes[t].type;
				annotTypeOrders.add(new ImObjectTypeOrder(ctAnnotTypes, 3, annotClashTraces.getCount(clashTrace)));
				ctAnnotTypes = null;
			}
			
			for (int t = 0; t < annotClashTypes.length; t++)
				annotClashTypes[t].pos = annotClashTypes[t].sizePos;
			Arrays.sort(annotClashTypes);
			System.out.println("SIZE ORDER:");
			for (int t = 0; t < annotClashTypes.length; t++)
				System.out.println(" - " + annotClashTypes[t].type + " " + annotClashTypes[t].rel1Npos + " " + annotClashTypes[t].sizePos + " " + annotClashTypes[t].freqPos);
			nestingConflicts = 0;
			actNestingConflicts = 0;
			clashNestingConflicts = 0;
			orderWellDefined = true;
			for (int t = 1; t < annotClashTypes.length; t++) {
				if (annotClashTypes[t-1].pos < annotClashTypes[t].pos)
					continue;
				nestingConflicts++;
				System.out.println(" ==> ORDER UNDEFINED: " + annotClashTypes[t-1].type + " vs. " + annotClashTypes[t].type);
				String typePair = (annotClashTypes[t-1].type + " -> " + annotClashTypes[t].type);
				if (annotTypePairs.contains(typePair) || invAnnotTypePairs.contains(typePair))
					actNestingConflicts++;
				System.out.println("         ACTUAL PAIRINGS: " + annotTypePairs.getCount(typePair) + ", " + invAnnotTypePairs.getCount(typePair) + " reverse");
				if (clashAnnotTypePairs.contains(typePair) || invClashAnnotTypePairs.contains(typePair))
					clashNestingConflicts++;
				System.out.println("         ACTUAL CLASHES: " + clashAnnotTypePairs.getCount(typePair) + ", " + invClashAnnotTypePairs.getCount(typePair) + " reverse");
				orderWellDefined = false;
				for (int lt = t+1; lt < annotClashTypes.length; lt++) {
					if (annotClashTypes[t].pos < annotClashTypes[lt].pos)
						break;
					nestingConflicts++;
					System.out.println(" ==> ORDER UNDEFINED: " + annotClashTypes[t-1].type + " vs. " + annotClashTypes[lt].type);
					typePair = (annotClashTypes[t-1].type + " -> " + annotClashTypes[lt].type);
					if (annotTypePairs.contains(typePair) || invAnnotTypePairs.contains(typePair))
						actNestingConflicts++;
					System.out.println("         ACTUAL PAIRINGS: " + annotTypePairs.getCount(typePair) + ", " + invAnnotTypePairs.getCount(typePair) + " reverse");
					if (clashAnnotTypePairs.contains(typePair) || invClashAnnotTypePairs.contains(typePair))
						clashNestingConflicts++;
					System.out.println("         ACTUAL CLASHES: " + clashAnnotTypePairs.getCount(typePair) + ", " + invClashAnnotTypePairs.getCount(typePair) + " reverse");
				}
			}
			System.out.println(" ==> NESTING CONFLICTS: " + nestingConflicts + ", " + actNestingConflicts + " ACTUAL, " + clashNestingConflicts + " CLASHES");
			if (orderWellDefined && (ctAnnotTypes != null)) {
				for (int t = 0; t < annotClashTypes.length; t++)
					ctAnnotTypes[t] = annotClashTypes[t].type;
				annotTypeOrders.add(new ImObjectTypeOrder(ctAnnotTypes, 2, annotClashTraces.getCount(clashTrace)));
				ctAnnotTypes = null;
			}
			
			for (int t = 0; t < annotClashTypes.length; t++)
				annotClashTypes[t].pos = annotClashTypes[t].freqPos;
			Arrays.sort(annotClashTypes);
			System.out.println("NESTING FREQUENCY ORDER:");
			for (int t = 0; t < annotClashTypes.length; t++)
				System.out.println(" - " + annotClashTypes[t].type + " " + annotClashTypes[t].rel1Npos + " " + annotClashTypes[t].sizePos + " " + annotClashTypes[t].freqPos);
			nestingConflicts = 0;
			actNestingConflicts = 0;
			clashNestingConflicts = 0;
			orderWellDefined = true;
			for (int t = 1; t < annotClashTypes.length; t++) {
				if (annotClashTypes[t-1].pos < annotClashTypes[t].pos)
					continue;
				nestingConflicts++;
				System.out.println(" ==> ORDER UNDEFINED: " + annotClashTypes[t-1].type + " vs. " + annotClashTypes[t].type);
				String typePair = (annotClashTypes[t-1].type + " -> " + annotClashTypes[t].type);
				if (annotTypePairs.contains(typePair) || invAnnotTypePairs.contains(typePair))
					actNestingConflicts++;
				System.out.println("         ACTUAL PAIRINGS: " + annotTypePairs.getCount(typePair) + ", " + invAnnotTypePairs.getCount(typePair) + " reverse");
				if (clashAnnotTypePairs.contains(typePair) || invClashAnnotTypePairs.contains(typePair))
					clashNestingConflicts++;
				System.out.println("         ACTUAL CLASHES: " + clashAnnotTypePairs.getCount(typePair) + ", " + invClashAnnotTypePairs.getCount(typePair) + " reverse");
				orderWellDefined = false;
				for (int lt = t+1; lt < annotClashTypes.length; lt++) {
					if (annotClashTypes[t].pos < annotClashTypes[lt].pos)
						break;
					nestingConflicts++;
					System.out.println(" ==> ORDER UNDEFINED: " + annotClashTypes[t-1].type + " vs. " + annotClashTypes[lt].type);
					typePair = (annotClashTypes[t-1].type + " -> " + annotClashTypes[lt].type);
					if (annotTypePairs.contains(typePair) || invAnnotTypePairs.contains(typePair))
						actNestingConflicts++;
					System.out.println("         ACTUAL PAIRINGS: " + annotTypePairs.getCount(typePair) + ", " + invAnnotTypePairs.getCount(typePair) + " reverse");
					if (clashAnnotTypePairs.contains(typePair) || invClashAnnotTypePairs.contains(typePair))
						clashNestingConflicts++;
					System.out.println("         ACTUAL CLASHES: " + clashAnnotTypePairs.getCount(typePair) + ", " + invClashAnnotTypePairs.getCount(typePair) + " reverse");
				}
			}
			System.out.println(" ==> NESTING CONFLICTS: " + nestingConflicts + ", " + actNestingConflicts + " ACTUAL, " + clashNestingConflicts + " CLASHES");
			if (orderWellDefined && (ctAnnotTypes != null)) {
				for (int t = 0; t < annotClashTypes.length; t++)
					ctAnnotTypes[t] = annotClashTypes[t].type;
				annotTypeOrders.add(new ImObjectTypeOrder(ctAnnotTypes, 1, annotClashTraces.getCount(clashTrace)));
				ctAnnotTypes = null;
			}
		}
		Collections.sort(annotTypeOrders);
		for (int o = 0; o < annotTypeOrders.size(); o++) {
			ImObjectTypeOrder ioto = ((ImObjectTypeOrder) annotTypeOrders.get(o));
			System.out.println("Comparing '" + ioto + "' (strict " + ioto.strictness + ", weight " + ioto.weight + ")");
			for (int co = (o+1); co < annotTypeOrders.size(); co++) {
				ImObjectTypeOrder cIoto = ((ImObjectTypeOrder) annotTypeOrders.get(co));
				System.out.println(" - to '" + cIoto + "' (strict " + cIoto.strictness + ", weight " + cIoto.weight + ")");
				if (ioto.subsumes(cIoto)) {
					ioto.weight += cIoto.weight;
					annotTypeOrders.remove(co--);
					System.out.println("   ==> absorbed");
					continue;
				}
				if (ioto.contains(cIoto)) {
					System.out.println("   ==> order clash");
					continue;
				}
				if (ioto.overlaps(cIoto)) {
					System.out.println("   ==> might need combining");
					LinkedHashSet cgotSet = new LinkedHashSet();
					cgotSet.addAll(Arrays.asList(ioto.types));
					cgotSet.addAll(Arrays.asList(cIoto.types));
					ImObjectTypeOrder[] typeOrders = ((ImObjectTypeOrder[]) annotTypeOrders.toArray(new ImObjectTypeOrder[annotTypeOrders.size()]));
					String[] cgots = ((String[]) cgotSet.toArray(new String[cgotSet.size()]));
					ImObjectTypeOrder combIoto = createAggregateTypeOrder(typeOrders, cgots, false);
					if (combIoto == null)
						System.out.println("     ==> failed to combine type orders");
					else System.out.println("     ==> combined to " + combIoto);
					continue;
				}
			}
		}
		System.out.println("Annotation type orders: " + annotTypeOrders.size());
		for (int o = 0; o < annotTypeOrders.size(); o++) {
			ImObjectTypeOrder ioto = ((ImObjectTypeOrder) annotTypeOrders.get(o));
			System.out.println(" - '" + ioto + "' (strict " + ioto.strictness + ", weight " + ioto.weight + ")");
		}
	}
	
	public static void mainIoTest(String[] args) throws Exception {
//		mainOldIO(); // works, doesn't change anything, as it should
		mainNewIO(); // TODO need to test different parameter combinations
//		mainVersionUpgrade(); // works TODO need to test different parameter combinations
//		mainVersionDowngrade();
	}
	
	private static void mainOldIO() throws Exception {
		final File baseFolder = new File("E:/Testdaten/PdfExtract/");
		//	TODO test this with more documents, specifically type nesting orders !!!
		
		//	V1 -> V1 TEST
//		String docInFolderName = "EJT/ejt-399_zonstein_kunt.pdf.imdir"; // original V1 -> V1 (copy to spot any changes)
		String docInFolderName = "EJT/ejt-399_zonstein_kunt.pdf.imdir.testV1"; // V1 -> V1 (round trip with V2 storage parameters correcvtly ignored)
		File docInFolder = new File(baseFolder, docInFolderName);
		ProgressMonitorDialog pmd = null;
		ImDocument imDoc = loadDocument(docInFolder, EntryVerificationLogger.dummy, pmd);
		String docOutFolderName = "EJT/ejt-399_zonstein_kunt.pdf.imdir.testV1";
		File docOutFolder = new File(baseFolder, docOutFolderName);
		docOutFolder.mkdirs();
		long defParams = STORAGE_MODE_TSV;
		defParams = setUsePageAttributeColumns(defParams, true);
		
		defParams = setUseSupplementAttributeColumns(defParams, true);
		defParams = setBundleSupplementGraphicsData(defParams, true);
		
		defParams = setUseWordAttributeColumns(defParams, true);
		defParams = setUseWordChunks(defParams, true);
		defParams = setWordChunkSize(defParams, 0x1000);
		
		defParams = setUseRegionAttributeColumns(defParams, true);
		defParams = setExternalizeRegionAttributeValues(defParams, true);
		defParams = setUseRegionTypeSpecificEntries(defParams, true);
		defParams = setRegionTypeEntrySizeThreshold(defParams, 0x0200); // 512 decimal
		
		defParams = setUseAnnotationAttributeColumns(defParams, true);
		defParams = setExternalizeAnnotationAttributeValues(defParams, true);
		defParams = setUseAnnotationTypeSpecificEntries(defParams, true);
		defParams = setAnnotationTypeEntrySizeThreshold(defParams, 0x0080); // 128 decimal
		ImDocumentEntry[] entries = storeDocument(imDoc, docOutFolder, defParams, ProgressMonitor.dummy);
//		ImDocumentEntry[] entries = storeDocument(imDoc, docOutFolder, ProgressMonitor.dummy);
		System.out.println(hashEntryList(entries));
	}
	
	private static void mainVersionUpgrade() throws Exception {
		final File baseFolder = new File("E:/Testdaten/PdfExtract/");
		//	TODO test this with more documents, specifically type nesting orders !!!
		
		//	V1 -> V2 TEST
//		String docInFolderName = "EJT/ejt-399_zonstein_kunt.pdf.imdir"; // original V1 -> V2
		String docInFolderName = "EJT/ejt-399_zonstein_kunt.pdf.imdir.testV1"; // copy of original V1
		File docInFolder = new File(baseFolder, docInFolderName);
		ImDocument imDoc = loadDocument(docInFolder);
		String docOutFolderName = "EJT/ejt-399_zonstein_kunt.pdf.imdir.testV2d";
		File docOutFolder = new File(baseFolder, docOutFolderName);
		docOutFolder.mkdirs();
		long storageFlags = STORAGE_MODE_TSV;
		
		//	option for pages
		storageFlags = setUsePageAttributeColumns(storageFlags, true);
		
		//	option for supplements
		storageFlags = setUseSupplementAttributeColumns(storageFlags, true);
		storageFlags = setBundleSupplementGraphicsData(storageFlags, true);
		
		//	option for words
		storageFlags = setUseWordAttributeColumns(storageFlags, true);
		storageFlags = setUseWordChunks(storageFlags, true);
		storageFlags = setWordChunkSize(storageFlags, 0x1000); // 4096 decomal
		
		//	option for regions
		storageFlags = setUseRegionAttributeColumns(storageFlags, true);
//		storageFlags = setExternalizeRegionAttributeValues(storageFlags, true);
		storageFlags = setUseRegionTypeSpecificEntries(storageFlags, true);
		setRegionTypeEntrySizeThreshold(storageFlags, 0x0200); // 512 decimal
		
		//	option for annotations
		storageFlags = setUseAnnotationAttributeColumns(storageFlags, true);
//		storageFlags = setExternalizeAnnotationAttributeValues(storageFlags, true);
		storageFlags = setUseAnnotationTypeSpecificEntries(storageFlags, true);
		setAnnotationTypeEntrySizeThreshold(storageFlags, 0x0080); // 128 decimal
		
		ImDocumentEntry[] entries = storeDocument(imDoc, docOutFolder, storageFlags, ProgressMonitor.dummy);
		System.out.println(hashEntryList(entries));
	}
	
	private static void mainVersionDowngrade() throws Exception {
		final File baseFolder = new File("E:/Testdaten/PdfExtract/");
		//	TODO test this with more documents, specifically type nesting orders !!!
		
		//	V1 -> V2 TEST
		String docInFolderName = "EJT/ejt-399_zonstein_kunt.pdf.imdir.testV2"; // re-stored V1 -> V2 to complete round trip
		File docInFolder = new File(baseFolder, docInFolderName);
		ImDocument imDoc = loadDocument(docInFolder);
		String docOutFolderName = "EJT/ejt-399_zonstein_kunt.pdf.imdir.testV1";
		File docOutFolder = new File(baseFolder, docOutFolderName);
		docOutFolder.mkdirs();
//		long storageFlags = STORAGE_MODE_TSV;
//		storageFlags = setUsePageAttributeColumns(storageFlags, true);
//		
//		storageFlags = setUseSupplementAttributeColumns(storageFlags, true);
//		storageFlags = setBundleSupplementGraphicsData(storageFlags, true);
//		
//		storageFlags = setUseWordAttributeColumns(storageFlags, true);
//		storageFlags = setUseWordChunks(storageFlags, true);
//		storageFlags = setWordChunkSize(storageFlags, 0x1000);
//		
//		storageFlags = setUseRegionAttributeColumns(storageFlags, true);
//		storageFlags = setExternalizeRegionAttributeValues(storageFlags, true);
//		storageFlags = setUseRegionTypeSpecificEntries(storageFlags, true);
//		setRegionTypeEntrySizeThreshold(storageFlags, 0x0200); // 512 decimal
//		
//		storageFlags = setUseAnnotationAttributeColumns(storageFlags, true);
//		storageFlags = setExternalizeAnnotationAttributeValues(storageFlags, true);
//		storageFlags = setUseAnnotationTypeSpecificEntries(storageFlags, true);
//		setAnnotationTypeEntrySizeThreshold(storageFlags, 0x0080); // 128 decimal
		ImDocumentEntry[] entries = storeDocument(imDoc, docOutFolder, 0, ProgressMonitor.dummy);
		System.out.println(hashEntryList(entries));
	}
	
	private static void mainNewIO() throws Exception {
		final File baseFolder = new File("E:/Testdaten/PdfExtract/");
		//	TODO test this with more documents, specifically type nesting orders !!!
		
		//	V2 -> V2 TEST
		String docInFolderName = "EJT/ejt-399_zonstein_kunt.pdf.imdir.testV2d";
		File docInFolder = new File(baseFolder, docInFolderName);
		ProgressMonitorDialog pmd = null;
		ImDocument imDoc = loadDocument(docInFolder, EntryVerificationLogger.dummy, pmd);
		String docOutFolderName = "EJT/ejt-399_zonstein_kunt.pdf.imdir.testV2d";
		File docOutFolder = new File(baseFolder, docOutFolderName);
		docOutFolder.mkdirs();
		long storageFlags = STORAGE_MODE_TSV;
		storageFlags = setUsePageAttributeColumns(storageFlags, true);
		
		storageFlags = setUseSupplementAttributeColumns(storageFlags, true);
		storageFlags = setBundleSupplementGraphicsData(storageFlags, true);
		
		storageFlags = setUseWordAttributeColumns(storageFlags, true);
		storageFlags = setUseWordChunks(storageFlags, true);
		storageFlags = setWordChunkSize(storageFlags, 0x1000);
		
		storageFlags = setUseRegionAttributeColumns(storageFlags, true);
		storageFlags = setExternalizeRegionAttributeValues(storageFlags, true);
		storageFlags = setUseRegionTypeSpecificEntries(storageFlags, true);
		setRegionTypeEntrySizeThreshold(storageFlags, 0x0200); // 512 decimal
		
		storageFlags = setUseAnnotationAttributeColumns(storageFlags, true);
		storageFlags = setExternalizeAnnotationAttributeValues(storageFlags, true);
		storageFlags = setUseAnnotationTypeSpecificEntries(storageFlags, true);
		setAnnotationTypeEntrySizeThreshold(storageFlags, 0x0080); // 128 decimal
		ImDocumentEntry[] entries = storeDocument(imDoc, docOutFolder, storageFlags, ProgressMonitor.dummy);
		System.out.println(hashEntryList(entries));
		
//		//	PROGRESS MONITOR CASCADE TEST
//		String docInFileName = "EJT/ejt-392_germann.pdf.imf";
//		File docInFile = new File(baseFolder, docInFileName);
//		ProgressMonitorDialog pmd = new ProgressMonitorDialog(null, "Test");
//		pmd.setSize(400, 100);
//		pmd.setLocationRelativeTo(null);
//		pmd.popUp(false);
//		loadDocument(docInFile, EntryVerificationLogger.dummy, pmd);
	}
//	
//	public static void mainOld(String[] args) throws Exception {
//		final File baseFolder = new File("E:/Testdaten/PdfExtract/");
//		//	TODO test this with more documents, specifically type nesting orders !!!
//		
////		//	V1 -> V2 TEST
//////		String docInFolderName = "EJT/ejt-399_zonstein_kunt.pdf.imdir"; // original V1 -> V2
////		String docInFolderName = "EJT/ejt-399_zonstein_kunt.pdf.imdir.testCsv"; // re-stored V1 -> V2 to complete round trip
////		File docInFolder = new File(baseFolder, docInFolderName);
////		ImDocument imDoc = loadDocument(docInFolder);
////		String docOutFolderName = "EJT/ejt-399_zonstein_kunt.pdf.imdir.test";
////		File docOutFolder = new File(baseFolder, docOutFolderName);
////		docOutFolder.mkdirs();
////		long storageFlags = STORAGE_MODE_TSV;
////		storageFlags = setUsePageAttributeColumns(storageFlags, true);
////		
////		storageFlags = setUseSupplementAttributeColumns(storageFlags, true);
////		storageFlags = setBundleSupplementGraphicsData(storageFlags, true);
////		
////		storageFlags = setUseWordAttributeColumns(storageFlags, true);
////		storageFlags = setUseWordChunks(storageFlags, true);
////		storageFlags = setWordChunkSize(storageFlags, 0x1000);
////		
////		storageFlags = setUseRegionAttributeColumns(storageFlags, true);
////		storageFlags = setExternalizeRegionAttributeValues(storageFlags, true);
////		storageFlags = setUseRegionTypeSpecificEntries(storageFlags, true);
////		setRegionTypeEntrySizeThreshold(storageFlags, 0x0200); // 512 decimal
////		
////		storageFlags = setUseAnnotationAttributeColumns(storageFlags, true);
////		storageFlags = setExternalizeAnnotationAttributeValues(storageFlags, true);
////		storageFlags = setUseAnnotationTypeSpecificEntries(storageFlags, true);
////		setAnnotationTypeEntrySizeThreshold(storageFlags, 0x0080); // 128 decimal
////		ImDocumentEntry[] entries = storeDocument(imDoc, docOutFolder, storageFlags, ProgressMonitor.dummy);
////		System.out.println(hashEntryList(entries));
//		
////		//	V2 -> V1 TEST
////		String docInFolderName = "EJT/ejt-399_zonstein_kunt.pdf.imdir.test";
////		File docInFolder = new File(baseFolder, docInFolderName);
////		ProgressMonitorDialog pmd = null;
//////		ProgressMonitorDialog pmd = new ProgressMonitorDialog(null, "Test") {
//////			public void setProgress(int progress) {
//////				super.setProgress(progress);
//////				System.out.println(progress);
//////			}
//////		};
//////		pmd.setSize(400, 100);
//////		pmd.setLocationRelativeTo(null);
//////		pmd.popUp(false);
////		ImDocument imDoc = loadDocument(docInFolder, EntryVerificationLogger.dummy, pmd);
////		String docOutFolderName = "EJT/ejt-399_zonstein_kunt.pdf.imdir.testCsv";
////		File docOutFolder = new File(baseFolder, docOutFolderName);
////		docOutFolder.mkdirs();
////		storeDocument(imDoc, docOutFolder, STORAGE_MODE_CSV, ProgressMonitor.dummy);
//		
//		//	V2 -> V2 TEST
//		String docInFolderName = "EJT/ejt-399_zonstein_kunt.pdf.imdir.testTsv";
//		File docInFolder = new File(baseFolder, docInFolderName);
//		ProgressMonitorDialog pmd = null;
//		ImDocument imDoc = loadDocument(docInFolder, EntryVerificationLogger.dummy, pmd);
//		String docOutFolderName = "EJT/ejt-399_zonstein_kunt.pdf.imdir.testTsv";
//		File docOutFolder = new File(baseFolder, docOutFolderName);
//		docOutFolder.mkdirs();
//		long storageFlags = STORAGE_MODE_TSV;
//		storageFlags = setUsePageAttributeColumns(storageFlags, true);
//		
//		storageFlags = setUseSupplementAttributeColumns(storageFlags, true);
//		storageFlags = setBundleSupplementGraphicsData(storageFlags, true);
//		
//		storageFlags = setUseWordAttributeColumns(storageFlags, true);
//		storageFlags = setUseWordChunks(storageFlags, true);
//		storageFlags = setWordChunkSize(storageFlags, 0x1000);
//		
//		storageFlags = setUseRegionAttributeColumns(storageFlags, true);
//		storageFlags = setExternalizeRegionAttributeValues(storageFlags, true);
//		storageFlags = setUseRegionTypeSpecificEntries(storageFlags, true);
//		setRegionTypeEntrySizeThreshold(storageFlags, 0x0200); // 512 decimal
//		
//		storageFlags = setUseAnnotationAttributeColumns(storageFlags, true);
//		storageFlags = setExternalizeAnnotationAttributeValues(storageFlags, true);
//		storageFlags = setUseAnnotationTypeSpecificEntries(storageFlags, true);
//		setAnnotationTypeEntrySizeThreshold(storageFlags, 0x0080); // 128 decimal
//		ImDocumentEntry[] entries = storeDocument(imDoc, docOutFolder, storageFlags, ProgressMonitor.dummy);
//		System.out.println(hashEntryList(entries));
//		
////		//	PROGRESS MONITOR CASCADE TEST
////		String docInFileName = "EJT/ejt-392_germann.pdf.imf";
////		File docInFile = new File(baseFolder, docInFileName);
////		ProgressMonitorDialog pmd = new ProgressMonitorDialog(null, "Test");
////		pmd.setSize(400, 100);
////		pmd.setLocationRelativeTo(null);
////		pmd.popUp(false);
////		loadDocument(docInFile, EntryVerificationLogger.dummy, pmd);
//	}
}
//public class ImDocumentIO implements ImagingConstants {
////	/**
////	 * TODO remove this class.
////	 * 
////	 * @deprecated retained here merely to give move update time to spread
////	 *            without causing errors.
////	 * 
////	 * @author sautter
////	 */
////	public static class DataHashOutputStream extends de.uka.ipd.idaho.easyIO.streams.DataHashOutputStream {
////		public DataHashOutputStream(OutputStream out) {
////			super(out);
////		}
////	}
//	
//	/**
//	 * Load an Image Markup document. If the argument file is an actual file,
//	 * this method assumes it to be a zipped-up Image Markup File. If the file
//	 * is a folder, however, this method assumes it to be an already un-zipped
//	 * Image Markup File.
//	 * @param file the file to load from
//	 * @return an Image Markup document representing the content of the
//	 *            argument stream.
//	 * @throws IOException
//	 */
//	public static ImDocument loadDocument(File file) throws IOException {
//		return loadDocument(file, null, null);
//	}
//	
//	/**
//	 * Load an Image Markup document. If the argument file is an actual file,
//	 * this method assumes it to be a zipped-up Image Markup File. If the file
//	 * is a folder, however, this method assumes it to be an already un-zipped
//	 * Image Markup File. In the former case, the argument list of IMF entries
//	 * is ignored. In the latter case, if the IMF entry list is null, this
//	 * method expects a list named 'entries.txt' in the argument folder;
//	 * otherwise, it uses the argument list.
//	 * @param file the file to load from
//	 * @param entries an array holding the IMF entries to use
//	 * @return an Image Markup document representing the content of the
//	 *            argument stream.
//	 * @throws IOException
//	 */
//	public static ImDocument loadDocument(File file, ImDocumentEntry[] entries) throws IOException {
//		return loadDocument(file, entries, null);
//	}
//	
//	/**
//	 * Load an Image Markup document. If the argument file is an actual file,
//	 * this method assumes it to be a zipped-up Image Markup File. If the file
//	 * is a folder, however, this method assumes it to be an already un-zipped
//	 * Image Markup File.
//	 * @param in the input stream to load from
//	 * @param pm a progress monitor to observe the loading process
//	 * @return an Image Markup document representing the content of the
//	 *            argument stream.
//	 * @throws IOException
//	 */
//	public static ImDocument loadDocument(File file, ProgressMonitor pm) throws IOException {
//		return loadDocument(file, null, pm);
//	}
//	
//	/**
//	 * Load an Image Markup document. If the argument file is an actual file,
//	 * this method assumes it to be a zipped-up Image Markup File. If the file
//	 * is a folder, however, this method assumes it to be an already un-zipped
//	 * Image Markup File. In the former case, the argument list of IMF entries
//	 * is ignored. In the latter case, if the IMF entry list is null, this
//	 * method expects a list named 'entries.txt' in the argument folder;
//	 * otherwise, it uses the argument list.
//	 * @param in the input stream to load from
//	 * @param entries an array holding the IMF entries to use
//	 * @param pm a progress monitor to observe the loading process
//	 * @return an Image Markup document representing the content of the
//	 *            argument stream.
//	 * @throws IOException
//	 */
//	public static ImDocument loadDocument(File file, ImDocumentEntry[] entries, ProgressMonitor pm) throws IOException {
//		
//		//	assume folder to be un-zipped IMF
//		if (file.isDirectory())
//			return loadDocument(new FolderImDocumentData(file, entries), pm);
//		
//		//	assume file to be zipped-up IMF
//		else {
//			InputStream fis = new FileInputStream(file);
//			ImDocument doc = loadDocument(fis, null, pm, file.length());
//			fis.close();
//			return doc;
//		}
//	}
//	
//	/**
//	 * Load an Image Markup document.
//	 * @param in the input stream to load from
//	 * @return an Image Markup document representing the content of the
//	 *            argument stream.
//	 * @throws IOException
//	 */
//	public static ImDocument loadDocument(InputStream in) throws IOException {
//		return loadDocument(in, null, null);
//	}
//	
//	/**
//	 * Load an Image Markup document.
//	 * @param in the input stream to load from
//	 * @param pm a progress monitor to observe the loading process
//	 * @return an Image Markup document representing the content of the
//	 *            argument stream.
//	 * @throws IOException
//	 */
//	public static ImDocument loadDocument(InputStream in, ProgressMonitor pm) throws IOException {
//		return loadDocument(in, null, pm, -1);
//	}
//	
//	/**
//	 * Load an Image Markup document.
//	 * @param in the input stream to load from
//	 * @param pm a progress monitor to observe the loading process
//	 * @param inLength the number of bytes to expect from the argument input stream
//	 * @return an Image Markup document representing the content of the
//	 *            argument stream.
//	 * @throws IOException
//	 */
//	public static ImDocument loadDocument(InputStream in, ProgressMonitor pm, long inLength) throws IOException {
//		return loadDocument(in, null, pm, inLength);
//	}
//	
//	/**
//	 * Load an Image Markup document. If the argument cache folder is null,
//	 * page images will be cached in memory. For large documents, this can
//	 * cause high memory consumption. The argument input stream is not closed.
//	 * @param in the input stream to load from
//	 * @param cacheFolder the folder to use for caching
//	 * @return an Image Markup document representing the content of the
//	 *            argument stream.
//	 * @throws IOException
//	 */
//	public static ImDocument loadDocument(InputStream in, File cacheFolder) throws IOException {
//		return loadDocument(in, cacheFolder, null, -1);
//	}
//	
//	/**
//	 * Load an Image Markup document. If the argument cache folder is null,
//	 * page images will be cached in memory. For large documents, this can
//	 * cause high memory consumption. The argument input stream is not closed.
//	 * @param in the input stream to load from
//	 * @param cacheFolder the folder to use for caching
//	 * @param pm a progress monitor to observe the loading process
//	 * @return an Image Markup document representing the content of the
//	 *            argument stream.
//	 * @throws IOException
//	 */
//	public static ImDocument loadDocument(InputStream in, File cacheFolder, ProgressMonitor pm) throws IOException {
//		return loadDocument(in, cacheFolder, pm, -1);
//	}
//	
//	/**
//	 * Load an Image Markup document. If the argument cache folder is null,
//	 * page images will be cached in memory. For large documents, this can
//	 * cause high memory consumption. The argument input stream is not closed.
//	 * @param in the input stream to load from
//	 * @param cacheFolder the folder to use for caching
//	 * @param pm a progress monitor to observe the loading process
//	 * @param inLength the number of bytes to expect from the argument input stream
//	 * @return an Image Markup document representing the content of the
//	 *            argument stream.
//	 * @throws IOException
//	 */
//	public static ImDocument loadDocument(InputStream in, File cacheFolder, ProgressMonitor pm, long inLength) throws IOException {
//		
//		//	check progress monitor
//		if (pm == null)
//			pm = ProgressMonitor.quiet;
//		
//		//	make reading observable if we know how many bytes to expect
//		if (inLength != -1) {
//			final ProgressMonitor fpm = pm;
//			long iInLength = inLength;
//			int iInShift = 0;
//			while (iInLength > Integer.MAX_VALUE) {
//				iInLength >>>= 1;
//				iInShift++;
//			}
//			final int fInLength = (((int) iInLength) / 100);
//			final int fInShift = iInShift;
//			in = new FilterInputStream(in) {
//				long read = 0;
//				int iRead = 0;
//				public int read() throws IOException {
//					int r = super.read();
//					if (r != -1) {
//						this.read++;
//						this.iRead = ((int) (this.read >>> fInShift));
//						fpm.setProgress(this.iRead / fInLength);
//					}
//					return r;
//				}
//				public int read(byte[] b) throws IOException {
//					return this.read(b, 0, b.length);
//				}
//				public int read(byte[] b, int off, int len) throws IOException {
//					int r = super.read(b, off, len);
//					if (r != -1) {
//						this.read += r;
//						this.iRead = ((int) (this.read >>> fInShift));
//						fpm.setProgress(this.iRead / fInLength);
//					}
//					return r;
//				}
//				public long skip(long n) throws IOException {
//					long s = super.skip(n);
//					this.read += s;
//					this.iRead = ((int) (this.read >>> fInShift));
//					fpm.setProgress(this.iRead / fInLength);
//					return s;
//				}
//			};
//		}
//		
//		//	get ready to unzip
//		ImDocumentData data = new ZipInImDocumentData(cacheFolder);
//		ZipInputStream zin = new ZipInputStream(in);
//		
//		//	read and cache archive contents
//		pm.setStep("Un-zipping & caching archive");
//		pm.setBaseProgress(0);
//		pm.setMaxProgress(50);
//		for (ZipEntry ze; (ze = zin.getNextEntry()) != null;) {
//			String zen = ze.getName();
//			pm.setInfo(zen);
//			OutputStream cacheOut = data.getOutputStream(zen);
//			byte[] buffer = new byte[1024];
//			for (int r; (r = zin.read(buffer, 0, buffer.length)) != -1;)
//				cacheOut.write(buffer, 0, r);
//			cacheOut.flush();
//			cacheOut.close();
//		}
//		
//		//	instantiate and return document proper
//		return loadDocument(data, pm);
//	}
//	
//	private static class ZipInImDocumentData extends ImDocumentData {
//		private HashMap entryDataCache;
//		private File entryDataCacheFolder;
//		ZipInImDocumentData(File entryDataCacheFolder) {
//			this.entryDataCache = ((entryDataCacheFolder == null) ? new HashMap() : null);
//			this.entryDataCacheFolder = entryDataCacheFolder;
//		}
//		public boolean canLoadDocument() {
//			return true;
//		}
//		public boolean canStoreDocument() {
//			return false;
//		}
//		public String getDocumentDataId() {
//			return null;
//		}
//		public boolean hasEntryData(ImDocumentEntry entry) {
//			return this.hasEntry(entry);
//		}
//		public InputStream getInputStream(String entryName) throws IOException {
//			ImDocumentEntry entry = this.getEntry(entryName);
//			if (entry == null)
//				throw new FileNotFoundException(entryName);
//			if (this.entryDataCacheFolder == null) {
//				byte[] entryData = ((byte[]) this.entryDataCache.get(entryName));
//				if (entryData == null)
//					throw new FileNotFoundException(entryName);
//				else return new ByteArrayInputStream(entryData);
//			}
//			else {
//				File entryDataFile = new File(this.entryDataCacheFolder, entryName);
//				return new BufferedInputStream(new FileInputStream(entryDataFile));
//			}
//		}
//		public OutputStream getOutputStream(final String entryName, boolean writeDirectly) throws IOException {
//			OutputStream out;
//			
//			//	we have to use in memory caching
//			if (this.entryDataCacheFolder == null)
//				out = new ByteArrayOutputStream() {
//					public void close() throws IOException {
//						super.close();
//						entryDataCache.put(entryName, this.toByteArray());
//					}
//				};
//			
//			//	we do have a cache folder, use it
//			else {
//				checkEntryName(entryName);
//				final File newEntryDataFile = new File(this.entryDataCacheFolder, (entryName + ".new"));
//				out = new BufferedOutputStream(new FileOutputStream(newEntryDataFile) {
//					public void close() throws IOException {
//						super.flush();
//						super.close();
//						File exEntryDataFile = new File(entryDataCacheFolder, entryName);
//						if (exEntryDataFile.exists() && newEntryDataFile.exists() && newEntryDataFile.getName().endsWith(".new"))
//							exEntryDataFile.renameTo(new File(entryDataCacheFolder, (entryName + "." + System.currentTimeMillis() + ".old")));
//						newEntryDataFile.renameTo(new File(entryDataCacheFolder, entryName));
//					}
//				});
//			}
//			
//			//	make sure to update entry list
//			return new DataHashOutputStream(out) {
//				public void close() throws IOException {
//					super.flush();
//					super.close();
//					putEntry(new ImDocumentEntry(entryName, System.currentTimeMillis(), this.getDataHash()));
//				}
//			};
//		}
//		public void dispose() {
//			super.dispose();
//			if (this.entryDataCache != null)
//				this.entryDataCache.clear();
//			if ((this.entryDataCacheFolder != null) && this.entryDataCacheFolder.exists())
//				this.cleanCacheFolder(this.entryDataCacheFolder);
//		}
//		private void cleanCacheFolder(File folder) {
//			File[] folderContent = folder.listFiles();
//			for (int c = 0; c < folderContent.length; c++) try {
//				if (folderContent[c].isDirectory())
//					cleanCacheFolder(folderContent[c]);
//				else folderContent[c].delete();
//			}
//			catch (Throwable t) {
//				System.out.println("Error cleaning up cached file '" + folderContent[c].getAbsolutePath() + "': " + t.getMessage());
//				t.printStackTrace(System.out);
//			}
//			try {
//				folder.delete();
//			}
//			catch (Throwable t) {
//				System.out.println("Error cleaning up cache folder '" + folder.getAbsolutePath() + "': " + t.getMessage());
//				t.printStackTrace(System.out);
//			}
//		}
//	}
//	
//	private static class ZipOutImDocumentData extends ImDocumentData {
//		ZipOutputStream zipOut;
//		ZipOutImDocumentData(ZipOutputStream zipOut) {
//			this.zipOut = zipOut;
//		}
//		public boolean canLoadDocument() {
//			return false;
//		}
//		public boolean canStoreDocument() {
//			return true;
//		}
//		public String getDocumentDataId() {
//			return null;
//		}
//		public boolean hasEntryData(ImDocumentEntry entry) {
//			return this.hasEntry(entry);
//		}
//		public InputStream getInputStream(String entryName) throws IOException {
//			throw new FileNotFoundException(entryName);
//		}
//		public OutputStream getOutputStream(final String entryName, boolean writeDirectly) throws IOException {
//			
//			//	put entry, and make sure not to close the stream proper
//			checkEntryName(entryName);
//			this.zipOut.putNextEntry(new ZipEntry(entryName));
//			OutputStream out = new FilterOutputStream(this.zipOut) {
//				public void close() throws IOException {
//					zipOut.flush();
//					zipOut.closeEntry();
//				}
//			};
//			
//			//	make sure to update entry list
//			return new DataHashOutputStream(out) {
//				public void close() throws IOException {
//					super.flush();
//					super.close();
//					putEntry(new ImDocumentEntry(entryName, System.currentTimeMillis(), this.getDataHash()));
//				}
//			};
//		}
//	}
//	
//	/**
//	 * Instantiate an Image Markup document from the data provided by a
//	 * document data object.
//	 * @param data the document data object to load the document from
//	 * @param pm a progress monitor to observe the loading process
//	 * @return the document instantiated from the argument data
//	 * @throws IOException
//	 */
//	public static DataBackedImDocument loadDocument(ImDocumentData data, ProgressMonitor pm) throws IOException {
//		
//		//	check progress monitor
//		if (pm == null)
//			pm = ProgressMonitor.quiet;
//		
//		//	read document meta data
//		pm.setStep("Reading document data");
//		pm.setBaseProgress(50);
//		pm.setMaxProgress(55);
//		InputStream docIn = data.getInputStream("document.csv");
//		StringRelation docsData = StringRelation.readCsvData(new InputStreamReader(docIn, "UTF-8"), true, null);
//		StringTupel docData = docsData.get(0);
//		final String docId = docData.getValue(DOCUMENT_ID_ATTRIBUTE);
//		docIn.close();
//		if (docId == null)
//			throw new IOException("Invalid Image Markup data: document ID missing");
//		//	TODO_later load orientation
//		final DataBoundImDocument doc = new DataBoundImDocument(docId, data);
//		setAttributes(doc, docData.getValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE));
//		
//		//	read fonts (if any)
//		//	TODOnot only add supplement proxy holding plain data, and parse on demand
//		//	==> we need the fonts proper because they have UUIDs, which need to resolve ...
//		//	==> ... and holding string data in Map actually needs more memory than glyph bitmap proper
//		pm.setStep("Reading font data");
//		pm.setBaseProgress(55);
//		pm.setMaxProgress(60);
//		try {
//			InputStream fontsIn = doc.getInputStream("fonts.csv");
//			StringRelation fontsData = StringRelation.readCsvData(new InputStreamReader(fontsIn, "UTF-8"), true, null);
//			fontsIn.close();
//			ImFont font = null;
//			for (int f = 0; f < fontsData.size(); f++) {
//				StringTupel charData = fontsData.get(f);
//				String fontName = charData.getValue(ImFont.NAME_ATTRIBUTE);
//				if (fontName == null)
//					continue;
//				if ((font == null) || !font.name.equals(fontName)) {
//					pm.setInfo("Adding font " + fontName);
//					font = new ImFont(doc, fontName, false, false, false, false);
//					String fontStyle = charData.getValue(ImFont.STYLE_ATTRIBUTE, "");
//					font.setMixedStyle(fontStyle.indexOf("X") != -1);
//					font.setBold(fontStyle.indexOf("B") != -1);
//					font.setItalics(fontStyle.indexOf("I") != -1);
//					if (fontStyle.indexOf("M") != -1)
//						font.setMonospaced(true);
//					else if (fontStyle.indexOf("S") != -1)
//						font.setSerif(true);
//					doc.addFont(font);
//				}
//				int charId = Integer.parseInt(charData.getValue(ImFont.CHARACTER_ID_ATTRIBUTE, "-1"), 16);
//				String charStr = charData.getValue(ImFont.CHARACTER_STRING_ATTRIBUTE);
//				if ((charId == 0) && (charStr.length() == 0)) {
//					String fontAttriutes = charData.getValue(ImFont.CHARACTER_IMAGE_ATTRIBUTE);
//					if (fontAttriutes != null)
//						setAttributes(font, fontAttriutes);
//				}
//				else if (charId > 0) {
//					String charImageHex = charData.getValue(ImFont.CHARACTER_IMAGE_ATTRIBUTE);
//					String charPathString = charData.getValue(ImFont.CHARACTER_PATH_ATTRIBUTE);
//					font.addCharacter(charId, charStr, charImageHex, charPathString);
//					if (font.isMixedStyle()) {
//						String charStyle = charData.getValue(ImFont.STYLE_ATTRIBUTE, "");
//						font.setBold((charStyle.indexOf("B") != -1), charId);
//						font.setItalics((charStyle.indexOf("I") != -1), charId);
//						if (charStyle.indexOf("M") != -1)
//							font.setMonospaced(true, charId);
//						else if (charStyle.indexOf("S") != -1)
//							font.setSerif(true, charId);
//					}
//				}
//			}
//		} catch (IOException ioe) { /* we might not have fonts ... */ }
//		
//		//	read page image data
//		final HashMap pageImageAttributesById = new HashMap();
//		try {
//			pm.setStep("Reading page image data");
//			pm.setBaseProgress(95);
//			pm.setMaxProgress(99);
//			InputStream pageImagesIn = doc.getInputStream("pageImages.csv");
//			StringRelation pageImagesData = StringRelation.readCsvData(new InputStreamReader(pageImagesIn, "UTF-8"), true, null);
//			pageImagesIn.close();
//			for (int p = 0; p < pageImagesData.size(); p++) {
//				StringTupel pageImageData = pageImagesData.get(p);
//				int piPageId = Integer.parseInt(pageImageData.getValue(ImObject.PAGE_ID_ATTRIBUTE));
//				String piAttributes = pageImageData.getValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE);
//				pageImageAttributesById.put(new Integer(piPageId), new PageImageAttributes(piAttributes));
//			}
//		} catch (IOException ioe) { /* we might be faced with the old way ... */ }
//		
//		//	read pages
//		pm.setStep("Reading page data");
//		pm.setBaseProgress(60);
//		pm.setMaxProgress(65);
//		InputStream pagesIn = doc.getInputStream("pages.csv");
//		StringRelation pagesData = StringRelation.readCsvData(new InputStreamReader(pagesIn, "UTF-8"), true, null);
//		pagesIn.close();
//		pm.setInfo("Adding " + pagesData.size() + " pages");
//		for (int p = 0; p < pagesData.size(); p++) {
//			pm.setProgress((p * 100) / pagesData.size());
//			StringTupel pageData = pagesData.get(p);
//			int pageId = Integer.parseInt(pageData.getValue(PAGE_ID_ATTRIBUTE));
//			BoundingBox bounds = BoundingBox.parse(pageData.getValue(BOUNDING_BOX_ATTRIBUTE));
//			ImPage page = new ImPage(doc, pageId, bounds); // constructor adds page to document automatically
//			setAttributes(page, pageData.getValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, ""));
//			PageImageAttributes pia = ((PageImageAttributes) pageImageAttributesById.get(new Integer(pageId)));
//			if (pia != null)
//				page.setImageDPI(pia.currentDpi);
//		}
//		
//		//	read words (first add words, then chain them and set attributes, as they might not be stored in stream order)
//		pm.setStep("Reading word data");
//		pm.setBaseProgress(65);
//		pm.setMaxProgress(75);
//		InputStream wordsIn = doc.getInputStream("words.csv");
//		StringRelation wordsData = StringRelation.readCsvData(new InputStreamReader(wordsIn, "UTF-8"), true, null);
//		wordsIn.close();
//		pm.setInfo("Adding " + wordsData.size() + " words");
//		for (int w = 0; w < wordsData.size(); w++) {
//			pm.setProgress((w * 100) / wordsData.size());
//			StringTupel wordData = wordsData.get(w);
//			ImPage page = doc.getPage(Integer.parseInt(wordData.getValue(PAGE_ID_ATTRIBUTE)));
//			BoundingBox bounds = BoundingBox.parse(wordData.getValue(BOUNDING_BOX_ATTRIBUTE));
//			new ImWord(page, bounds, wordData.getValue(STRING_ATTRIBUTE));
//		}
//		pm.setStep("Chaining text streams");
//		pm.setBaseProgress(75);
//		pm.setMaxProgress(80);
//		for (int w = 0; w < wordsData.size(); w++) {
//			pm.setProgress((w * 100) / wordsData.size());
//			StringTupel wordData = wordsData.get(w);
//			BoundingBox bounds = BoundingBox.parse(wordData.getValue(BOUNDING_BOX_ATTRIBUTE));
//			ImWord word = doc.getWord(Integer.parseInt(wordData.getValue(PAGE_ID_ATTRIBUTE)), bounds);
//			ImWord prevWord = doc.getWord(wordData.getValue(ImWord.PREVIOUS_WORD_ATTRIBUTE));
//			if (prevWord != null)
//				word.setPreviousWord(prevWord);
//			else {
//				String textStreamType = wordData.getValue(ImWord.TEXT_STREAM_TYPE_ATTRIBUTE);
//				if ((textStreamType == null) || (textStreamType.trim().length() == 0))
//					textStreamType = ImWord.TEXT_STREAM_TYPE_MAIN_TEXT;
//				word.setTextStreamType(textStreamType);
//			}
//			word.setNextRelation(wordData.getValue(ImWord.NEXT_RELATION_ATTRIBUTE).charAt(0));
//			setAttributes(word, wordData.getValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, ""));
//		}
//		
//		//	read regions
//		pm.setStep("Reading region data");
//		pm.setBaseProgress(80);
//		pm.setMaxProgress(85);
//		InputStream regsIn = doc.getInputStream("regions.csv");
//		StringRelation regsData = StringRelation.readCsvData(new InputStreamReader(regsIn, "UTF-8"), true, null);
//		regsIn.close();
//		pm.setInfo("Adding " + regsData.size() + " regions");
//		HashMap regionsById = new HashMap();
//		for (int r = 0; r < regsData.size(); r++) {
//			pm.setProgress((r * 100) / regsData.size());
//			StringTupel regData = regsData.get(r);
//			ImPage page = doc.getPage(Integer.parseInt(regData.getValue(PAGE_ID_ATTRIBUTE)));
//			BoundingBox bounds = BoundingBox.parse(regData.getValue(BOUNDING_BOX_ATTRIBUTE));
//			String type = regData.getValue(TYPE_ATTRIBUTE);
//			String regId = (type + "@" + page.pageId + "." + bounds.toString());
//			ImRegion reg = ((ImRegion) regionsById.get(regId));
//			if (reg == null) {
//				reg = new ImRegion(page, bounds, type);
//				regionsById.put(regId, reg);
//			}
//			setAttributes(reg, regData.getValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, ""));
//		}
//		
//		//	read annotations
//		pm.setStep("Reading annotation data");
//		pm.setBaseProgress(85);
//		pm.setMaxProgress(90);
//		InputStream annotsIn = doc.getInputStream("annotations.csv");
//		StringRelation annotsData = StringRelation.readCsvData(new InputStreamReader(annotsIn, "UTF-8"), true, null);
//		annotsIn.close();
//		pm.setInfo("Adding " + annotsData.size() + " annotations");
//		for (int a = 0; a < annotsData.size(); a++) {
//			pm.setProgress((a * 100) / annotsData.size());
//			StringTupel annotData = annotsData.get(a);
//			ImWord firstWord = doc.getWord(annotData.getValue(ImAnnotation.FIRST_WORD_ATTRIBUTE));
//			ImWord lastWord = doc.getWord(annotData.getValue(ImAnnotation.LAST_WORD_ATTRIBUTE));
//			if ((firstWord == null) || (lastWord == null))
//				continue;
//			String type = annotData.getValue(TYPE_ATTRIBUTE);
//			ImAnnotation annot = doc.addAnnotation(firstWord, lastWord, type);
//			if (annot != null)
//				setAttributes(annot, annotData.getValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, ""));
//		}
//		
//		//	read meta data of supplements (if any)
//		//	TODOnot only add supplement proxy holding plain data, and parse on demand
//		//	==> we need the supplements proper because they have UUIDs, which need to resolve ...
//		//	==> ... and holding string data in Map actually needs more memory than parsed supplement proper
//		pm.setStep("Reading supplement data");
//		pm.setBaseProgress(90);
//		pm.setMaxProgress(95);
//		try {
//			InputStream supplementsIn = doc.getInputStream("supplements.csv");
//			StringRelation supplementsData = StringRelation.readCsvData(new InputStreamReader(supplementsIn, "UTF-8"), true, null);
//			supplementsIn.close();
//			pm.setInfo("Adding " + supplementsData.size() + " supplements");
//			for (int s = 0; s < supplementsData.size(); s++) {
//				pm.setProgress((s * 100) / supplementsData.size());
//				StringTupel supplementData = supplementsData.get(s);
//				final String sid = supplementData.getValue(ImSupplement.ID_ATTRIBUTE);
//				String st = supplementData.getValue(ImSupplement.TYPE_ATTRIBUTE);
//				String smt = supplementData.getValue(ImSupplement.MIME_TYPE_ATTRIBUTE);
//				final String sfn = (sid + "." + smt.substring(smt.lastIndexOf('/') + "/".length()));
//				pm.setInfo(sfn);
//				ImSupplement supplement;
//				if (ImSupplement.SOURCE_TYPE.equals(st))
//					supplement = new ImSupplement.Source(doc, smt) {
//						public InputStream getInputStream() throws IOException {
//							return doc.getInputStream(sfn);
//						}
//					};
//				else if (ImSupplement.SCAN_TYPE.equals(st))
//					supplement = new ImSupplement.Scan(doc, sid, smt) {
//						public InputStream getInputStream() throws IOException {
//							return doc.getInputStream(sfn);
//						}
//					};
//				else if (ImSupplement.FIGURE_TYPE.equals(st))
//					supplement = new ImSupplement.Figure(doc, sid, smt) {
//						public InputStream getInputStream() throws IOException {
//							return doc.getInputStream(sfn);
//						}
//					};
//				else if (ImSupplement.GRAPHICS_TYPE.equals(st))
//					supplement = new ImSupplement.Graphics(doc, sid) {
//						public InputStream getInputStream() throws IOException {
//							return doc.getInputStream(sfn);
//						}
//					};
//				else supplement = new ImSupplement(doc, sid, st, smt) {
//					public InputStream getInputStream() throws IOException {
//						return doc.getInputStream(sfn);
//					}
//				};
//				setAttributes(supplement, supplementData.getValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, ""));
//				doc.addSupplement(supplement, false);
//			}
//		} catch (IOException ioe) { /* we might not have supplements ... */ }
//		/* We need to do this explicitly because supplements add themselves to
//		 * their document upon creation via the normal addSupplement() method,
//		 * marking themselves as dirty in our data bound document. */
//		doc.markSupplementsNotDirty();
//		
//		//	create page image source
//		pm.setStep("Creating page image source");
//		pm.setBaseProgress(99);
//		pm.setMaxProgress(100);
//		doc.setPageImageSource(new DbidPageImageStore(doc, pageImageAttributesById));
//		
//		//	finally ...
//		return doc;
//	}
//	
//	/**
//	 * Load the attributes of an Image Markup document from the data provided
//	 * by a document data object, but without loading the document as a whole.
//	 * Namely, this method only reads the 'document.csv' entry.
//	 * @param data the document data object to load the attributes from
//	 * @return the attributes read from the argument data
//	 * @throws IOException
//	 */
//	public static Attributed loadDocumentAttributes(ImDocumentData data) throws IOException {
//		InputStream docIn = data.getInputStream("document.csv");
//		StringRelation docsData = StringRelation.readCsvData(new InputStreamReader(docIn, "UTF-8"), true, null);
//		StringTupel docData = docsData.get(0);
//		docIn.close();
//		Attributed docAttributes = new AbstractAttributed();
//		setAttributes(docAttributes, docData.getValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE));
//		String docId = docData.getValue(DOCUMENT_ID_ATTRIBUTE);
//		if (docId != null)
//			docAttributes.setAttribute(DOCUMENT_ID_ATTRIBUTE, docId);
//		return docAttributes;
//	}
//	
//	private static class DataBoundImDocument extends DataBackedImDocument {
//		ImDocumentData docData;
//		DbidPageImageStore dbidPis;
//		HashSet dirtySupplementNames = new HashSet();
//		DataBoundImDocument(String docId, ImDocumentData docData) {
//			super(docId);
//			this.docData = docData;
//		}
//		
//		public ImDocumentData getDocumentData() {
//			return this.docData;
//		}
//		ImDocumentData bindToData(ImDocumentData docData) {
//			ImDocumentData oldDocData = this.docData;
//			this.docData = docData;
//			return ((oldDocData == this.docData) ? null : oldDocData);
//		}
//		
//		public void dispose() {
//			super.dispose();
//			this.dirtySupplementNames.clear();
//			if (this.dbidPis != null)
//				this.dbidPis.dispose();
//		}
//		
//		public ImSupplement addSupplement(ImSupplement ims) {
//			return this.addSupplement(ims, true);
//		}
//		ImSupplement addSupplement(ImSupplement ims, boolean isExternal) {
//			if (isExternal) {
//				String sfn = ims.getFileName();
//				this.dirtySupplementNames.add(sfn); // mark supplement as dirty
//			}
//			return super.addSupplement(ims);
//		}
//		boolean isSupplementDirty(String sfn) {
//			return this.dirtySupplementNames.contains(sfn);
//		}
//		void markSupplementsNotDirty() {
//			this.dirtySupplementNames.clear();
//		}
//		
//		public void setPageImageSource(PageImageSource pis) {
//			super.setPageImageSource(pis);
//			this.dbidPis = (((pis instanceof DbidPageImageStore) && (((DbidPageImageStore) pis).doc == this)) ? ((DbidPageImageStore) pis) : null);
//		}
//		boolean isInputStreamAvailable(String entryName) {
//			return this.docData.hasEntryData(entryName);
//		}
//		InputStream getInputStream(String entryName) throws IOException {
//			return this.docData.getInputStream(entryName);
//		}
//		OutputStream getOutputStream(final String entryName, boolean writeDirectly) throws IOException {
//			return new FilterOutputStream(this.docData.getOutputStream(entryName, writeDirectly)) {
//				public void close() throws IOException {
//					super.close();
//					dirtySupplementNames.remove(entryName);
//				}
//			};
//		}
//	}
//	
//	private static class DbidPageImageStore extends AbstractPageImageStore {
//		final DataBoundImDocument doc;
//		final HashMap pageImageAttributesById;
//		DbidPageImageStore(DataBoundImDocument doc, HashMap pageImageAttributesById) {
//			this.doc = doc;
//			this.doc.dbidPis = this;
//			this.pageImageAttributesById = pageImageAttributesById;
//		}
//		public boolean isPageImageAvailable(String name) {
//			if (!name.startsWith(this.doc.docId))
//				return false;
//			name = ("page" + name.substring(this.doc.docId.length() + ".".length()) + "." + IMAGE_FORMAT);
//			return this.doc.isInputStreamAvailable(name);
//		}
//		public PageImageInputStream getPageImageAsStream(String name) throws IOException {
//			if (!name.startsWith(this.doc.docId))
//				return null;
//			String piPageIdStr = name.substring(this.doc.docId.length() + ".".length());
//			name = ("page" + piPageIdStr + "." + IMAGE_FORMAT);
//			return this.getPageImageInputStream(this.doc.getInputStream(name), piPageIdStr);
//		}
//		private PageImageInputStream getPageImageInputStream(InputStream in, String piPageIdStr) throws IOException {
//			
//			//	test for PNG byte order mark
//			PeekInputStream peekIn = new PeekInputStream(in, pngSignature.length);
//			
//			//	this one's the old way
//			if (!peekIn.startsWith(pngSignature)) {
//				PageImageInputStream piis = new PageImageInputStream(peekIn, this);
//				return piis;
//			}
//			
//			//	get attributes TODO consider parsing on demand, saving effort with large documents of which only an excerpt is used
//			PageImageAttributes piAttributes = this.getPageImageAttributes(new Integer(piPageIdStr));
//			if (piAttributes == null) {
//				peekIn.close();
//				throw new FileNotFoundException("page" + piPageIdStr + "." + IMAGE_FORMAT);
//			}
//			
//			//	wrap and return page image input stream
//			return piAttributes.wrap(peekIn, this);
//		}
//		public boolean storePageImage(String name, PageImage pageImage) throws IOException {
//			if (!name.startsWith(this.doc.docId))
//				return false;
//			String piPageIdStr = name.substring(this.doc.docId.length() + ".".length());
//			name = ("page" + piPageIdStr + "." + IMAGE_FORMAT);
//			OutputStream out = this.doc.getOutputStream(name, true);
//			pageImage.writeImage(out);
//			out.close();
//			Integer pageId = new Integer(piPageIdStr);
//			PageImageAttributes piAttributes = this.getPageImageAttributes(pageId);
//			if (piAttributes == null) {
//				piAttributes = new PageImageAttributes(pageImage);
//				this.pageImageAttributesById.put(pageId, piAttributes);
//			}
//			else piAttributes.updateAttributes(pageImage);
//			return true;
//		}
//		public int getPriority() {
//			return 10; // we only want page images for one document, but those we really do want
//		}
//		PageImageAttributes getPageImageAttributes(Integer pageId) {
//			return ((PageImageAttributes) this.pageImageAttributesById.get(pageId));
//		}
//		void dispose() {
//			this.pageImageAttributesById.clear();
//		}
//	};
//	
//	private static class PageImageAttributes {
//		int originalWidth = -1;
//		int originalHeight = -1;
//		int originalDpi = -1;
//		int currentDpi = -1;
//		int leftEdge = 0;
//		int rightEdge = 0;
//		int topEdge = 0;
//		int bottomEdge = 0;
//		byte[] piAttributeBytes = new byte[16];
//		PageImageAttributes(String piAttributes) {
//			
//			//	read attribute string
//			String[] piAttributeNameValuePairs = piAttributes.split("[\\<\\>]");
//			for (int a = 0; a < (piAttributeNameValuePairs.length-1); a+= 2) {
//				if ("originalWidth".equals(piAttributeNameValuePairs[a]))
//					this.originalWidth = Integer.parseInt(piAttributeNameValuePairs[a+1]);
//				else if ("originalHeight".equals(piAttributeNameValuePairs[a]))
//					this.originalHeight = Integer.parseInt(piAttributeNameValuePairs[a+1]);
//				else if ("originalDpi".equals(piAttributeNameValuePairs[a]))
//					this.originalDpi = Integer.parseInt(piAttributeNameValuePairs[a+1]);
//				else if ("currentDpi".equals(piAttributeNameValuePairs[a]))
//					this.currentDpi = Integer.parseInt(piAttributeNameValuePairs[a+1]);
//				else if ("leftEdge".equals(piAttributeNameValuePairs[a]))
//					this.leftEdge = Integer.parseInt(piAttributeNameValuePairs[a+1]);
//				else if ("rightEdge".equals(piAttributeNameValuePairs[a]))
//					this.rightEdge = Integer.parseInt(piAttributeNameValuePairs[a+1]);
//				else if ("topEdge".equals(piAttributeNameValuePairs[a]))
//					this.topEdge = Integer.parseInt(piAttributeNameValuePairs[a+1]);
//				else if ("bottomEdge".equals(piAttributeNameValuePairs[a]))
//					this.bottomEdge = Integer.parseInt(piAttributeNameValuePairs[a+1]);
//			}
//			
//			//	default current DPI to original DPI if not given explicitly
//			if (this.currentDpi == -1)
//				this.currentDpi = this.originalDpi;
//			
//			//	wrap attribute values in byte array
//			this.updateAttributeBytes();
//		}
//		
//		PageImageAttributes(PageImage pi) {
//			this.updateAttributes(pi);
//		}
//		
//		void updateAttributes(PageImage pi) {
//			this.originalWidth = pi.originalWidth;
//			this.originalHeight = pi.originalHeight;
//			this.originalDpi = pi.originalDpi;
//			this.currentDpi = pi.currentDpi;
//			this.leftEdge = pi.leftEdge;
//			this.rightEdge = pi.rightEdge;
//			this.topEdge = pi.topEdge;
//			this.bottomEdge = pi.bottomEdge;
//			this.updateAttributeBytes();
//		}
//		private void updateAttributeBytes() {
//			this.piAttributeBytes[0] = ((byte) ((this.originalWidth / 256)));
//			this.piAttributeBytes[1] = ((byte) ((this.originalWidth & 255)));
//			this.piAttributeBytes[2] = ((byte) ((this.originalHeight / 256)));
//			this.piAttributeBytes[3] = ((byte) ((this.originalHeight & 255)));
//			this.piAttributeBytes[4] = ((byte) ((this.originalDpi / 256)));
//			this.piAttributeBytes[5] = ((byte) ((this.originalDpi & 255)));
//			this.piAttributeBytes[6] = ((byte) ((this.currentDpi / 256)));
//			this.piAttributeBytes[7] = ((byte) ((this.currentDpi & 255)));
//			this.piAttributeBytes[8] = ((byte) ((this.leftEdge / 256)));
//			this.piAttributeBytes[9] = ((byte) ((this.leftEdge & 255)));
//			this.piAttributeBytes[10] = ((byte) ((this.rightEdge / 256)));
//			this.piAttributeBytes[11] = ((byte) ((this.rightEdge & 255)));
//			this.piAttributeBytes[12] = ((byte) ((this.topEdge / 256)));
//			this.piAttributeBytes[13] = ((byte) ((this.topEdge & 255)));
//			this.piAttributeBytes[14] = ((byte) ((this.bottomEdge / 256)));
//			this.piAttributeBytes[15] = ((byte) ((this.bottomEdge & 255)));
//		}
//		
//		PageImageInputStream wrap(InputStream in, PageImageSource pis) throws IOException {
//			return new PageImageInputStream(new SequenceInputStream(new ByteArrayInputStream(this.piAttributeBytes), in), pis);
//		}
//		
//		String toAttributeString() {
//			StringBuffer piAttributes = new StringBuffer();
//			piAttributes.append("originalWidth<" + this.originalWidth + ">");
//			piAttributes.append("originalHeight<" + this.originalHeight + ">");
//			piAttributes.append("originalDpi<" + this.originalDpi + ">");
//			if (this.currentDpi != this.originalDpi)
//				piAttributes.append("currentDpi<" + this.currentDpi + ">");
//			if (this.leftEdge != 0)
//				piAttributes.append("leftEdge<" + this.leftEdge + ">");
//			if (this.rightEdge != 0)
//				piAttributes.append("rightEdge<" + this.rightEdge + ">");
//			if (this.topEdge != 0)
//				piAttributes.append("topEdge<" + this.topEdge + ">");
//			if (this.bottomEdge != 0)
//				piAttributes.append("bottomEdge<" + this.bottomEdge + ">");
//			return piAttributes.toString();
//		}
//	}
//	
//	/* we need to hard-code the first byte 0x89 ('‰' on ISO-8859) converts
//	 * differently on MacRoman and other encodings if embedded as a string
//	 * constant in the code */
//	private static final byte[] pngSignature = {((byte) 0x89), ((byte) 'P'), ((byte) 'N'), ((byte) 'G')};
//	
//	/**
//	 * Store an Image Markup document to a file. If the argument file is an
//	 * actual file, this method zips up the document and stores it in that
//	 * file. If the argument file actually is a folder, on the other hand, this
//	 * method does not zip up the individual entries, but stores them in the
//	 * argument folder as individual files. If the argument file does not
//	 * exist, it is created and treated as a file. When storing to an actual
//	 * file, this method returns null. When storing to a folder, the returned
//	 * array of IMF entries describes the mapping of logical file names (the
//	 * entry names) to physical file names, whose name includes the MD5 of the
//	 * file content. The latter is to avoid duplication to as high a degree as
//	 * possible, while still preserving files that are logically overwritten.
//	 * @param doc the Image Markup document to store.
//	 * @param file the file or folder to store the document to
//	 * @return an array describing the entries (in folder storage mode)
//	 * @throws IOException
//	 */
//	public static ImDocumentEntry[] storeDocument(ImDocument doc, File file) throws IOException {
//		return storeDocument(doc, file, null);
//	}
//	
//	/**
//	 * Store an Image Markup document to a file. If the argument file is an
//	 * actual file, this method zips up the document and stores it in that
//	 * file. If the argument file actually is a folder, on the other hand, this
//	 * method does not zip up the individual entries, but stores them in the
//	 * argument folder as individual files. If the argument file does not
//	 * exist, it is created and treated as a file. When storing to an actual
//	 * file, this method returns null. When storing to a folder, the returned
//	 * array of IMF entries describes the mapping of logical file names (the
//	 * entry names) to physical file names, whose name includes the MD5 of the
//	 * file content. The latter is to avoid duplication to as high a degree as
//	 * possible, while still preserving files that are logically overwritten.
//	 * @param doc the Image Markup document to store.
//	 * @param file the file or folder to store the document to
//	 * @param pm a progress monitor to observe the storage process
//	 * @return an array describing the entries (in folder storage mode)
//	 * @throws IOException
//	 */
//	public static ImDocumentEntry[] storeDocument(ImDocument doc, File file, ProgressMonitor pm) throws IOException {
//		
//		//	file does not exist, create it as a file
//		if (!file.exists()) {
//			file.getAbsoluteFile().getParentFile().mkdirs();
//			file.createNewFile();
//		}
//		
//		//	we have a directory, store document without zipping
//		if (file.isDirectory()) {
//			ImDocumentData data = null;
//			if (doc instanceof DataBoundImDocument) {
//				ImDocumentData docData = ((DataBoundImDocument) doc).docData;
//				if (file.getAbsolutePath().equals(docData.getDocumentDataId()))
//					data = docData;
//			}
//			if (data == null)
//				data = new FolderImDocumentData(file);
//			return storeDocument(doc, data, pm);
//		}
//		
//		//	we have an actual file (maybe of our own creation), zip up document
//		else {
//			OutputStream out = new BufferedOutputStream(new FileOutputStream(file));
//			storeDocument(doc, out, pm);
//			out.close();
//			return null;
//		}
//	}
//	
//	/**
//	 * Store an Image Markup document to an output stream. The argument output
//	 * stream is flushed and closed at the end of this method.
//	 * @param doc the Image Markup document to store.
//	 * @param out the output stream to store the document to
//	 * @throws IOException
//	 */
//	public static void storeDocument(ImDocument doc, OutputStream out) throws IOException {
//		storeDocument(doc, out, null);
//	}
//	
//	/**
//	 * Store an Image Markup document to an output stream. The argument output
//	 * stream is flushed and closed at the end of this method.
//	 * @param doc the Image Markup document to store.
//	 * @param out the output stream to store the document to
//	 * @param pm a progress monitor to observe the storage process
//	 * @throws IOException
//	 */
//	public static void storeDocument(ImDocument doc, OutputStream out, ProgressMonitor pm) throws IOException {
//		ZipOutputStream zout = new ZipOutputStream(new FilterOutputStream(out) {
//			public void close() throws IOException {}
//		});
//		storeDocument(doc, new ZipOutImDocumentData(zout), pm);
//		zout.flush();
//		zout.close();
//	}
//	
//	/**
//	 * Store an Image Markup document to a document data object.
//	 * @param doc the document to store
//	 * @param data the document data object to store the document to
//	 * @param pm a progress monitor observing the storage process
//	 * @return an array describing the entries
//	 * @throws IOException
//	 */
//	public static ImDocumentEntry[] storeDocument(ImDocument doc, ImDocumentData data, ProgressMonitor pm) throws IOException {
//		
//		//	check progress monitor
//		if (pm == null)
//			pm = ProgressMonitor.quiet;
//		
//		//	check memory (estimate 100 bytes per word, 1000 words per page, should be on the safe side)
//		boolean lowMemory = (Runtime.getRuntime().freeMemory() < (100 * 1000 * doc.getPageCount()));
//		if (lowMemory) {
//			System.gc();
//			lowMemory = (Runtime.getRuntime().freeMemory() < (100 * 1000 * doc.getPageCount()));
//		}
//		
//		//	get ready to zip, and to collect IMF entries
//		BufferedWriter bw;
//		StringVector keys = new StringVector();
//		
//		//	store document meta data
//		pm.setStep("Storing document data");
//		pm.setBaseProgress(0);
//		pm.setMaxProgress(5);
//		bw = getWriter(data, "document.csv", false);
//		keys.clear();
//		keys.parseAndAddElements((DOCUMENT_ID_ATTRIBUTE + ";" + ImObject.ATTRIBUTES_STRING_ATTRIBUTE), ";");
//		writeKeys(keys, bw);
//		StringTupel docData = new StringTupel(keys.size());
//		docData.setValue(DOCUMENT_ID_ATTRIBUTE, doc.docId);
//		docData.setValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, getAttributesString(doc));
//		writeValues(keys, docData, bw);
//		bw.close();
//		
//		//	assemble data for pages, words, and regions
//		pm.setStep("Storing page data");
//		pm.setBaseProgress(5);
//		pm.setMaxProgress(8);
//		ImPage[] pages = doc.getPages();
//		bw = getWriter(data, "pages.csv", false);
//		keys.clear();
//		keys.parseAndAddElements((PAGE_ID_ATTRIBUTE + ";" + BOUNDING_BOX_ATTRIBUTE + ";" + ImObject.ATTRIBUTES_STRING_ATTRIBUTE), ";");
//		writeKeys(keys, bw);
//		for (int p = 0; p < pages.length; p++) {
//			pm.setInfo("Page " + p);
//			pm.setProgress((p * 100) / pages.length);
//			
//			//	data for page
//			StringTupel pageData = new StringTupel(keys.size());
//			pageData.setValue(PAGE_ID_ATTRIBUTE, ("" + pages[p].pageId));
//			pageData.setValue(BOUNDING_BOX_ATTRIBUTE, pages[p].bounds.toString());
//			pageData.setValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, getAttributesString(pages[p]));
//			writeValues(keys, pageData, bw);
//		}
//		bw.close();
//		
//		//	store words
//		pm.setStep("Storing word data");
//		pm.setBaseProgress(8);
//		pm.setMaxProgress(17);
//		bw = getWriter(data, "words.csv", lowMemory);
//		keys.clear();
//		keys.parseAndAddElements((PAGE_ID_ATTRIBUTE + ";" + BOUNDING_BOX_ATTRIBUTE + ";" + STRING_ATTRIBUTE + ";" + ImWord.PREVIOUS_WORD_ATTRIBUTE + ";" + ImWord.NEXT_WORD_ATTRIBUTE + ";" + ImWord.NEXT_RELATION_ATTRIBUTE + ";" + ImWord.TEXT_STREAM_TYPE_ATTRIBUTE + ";" + ImObject.ATTRIBUTES_STRING_ATTRIBUTE), ";");
//		writeKeys(keys, bw);
//		for (int p = 0; p < pages.length; p++) {
//			pm.setInfo("Page " + p);
//			pm.setProgress((p * 100) / pages.length);
//			
//			//	data for words
//			ImWord[] pageWords = pages[p].getWords();
//			Arrays.sort(pageWords, ImUtils.textStreamOrder); // some effort, but negligible in comparison to image handling, and helps a lot reading word table
//			for (int w = 0; w < pageWords.length; w++) {
//				StringTupel wordData = new StringTupel(keys.size());
//				wordData.setValue(PAGE_ID_ATTRIBUTE, ("" + pageWords[w].pageId));
//				wordData.setValue(BOUNDING_BOX_ATTRIBUTE, pageWords[w].bounds.toString());
//				wordData.setValue(STRING_ATTRIBUTE, pageWords[w].getString());
//				if (pageWords[w].getPreviousWord() != null)
//					wordData.setValue(ImWord.PREVIOUS_WORD_ATTRIBUTE, pageWords[w].getPreviousWord().getLocalID());
//				else wordData.setValue(ImWord.TEXT_STREAM_TYPE_ATTRIBUTE, pageWords[w].getTextStreamType());
//				if (pageWords[w].getNextWord() != null)
//					wordData.setValue(ImWord.NEXT_WORD_ATTRIBUTE, pageWords[w].getNextWord().getLocalID());
//				wordData.setValue(ImWord.NEXT_RELATION_ATTRIBUTE, ("" + pageWords[w].getNextRelation()));
//				wordData.setValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, getAttributesString(pageWords[w]));
//				writeValues(keys, wordData, bw);
//			}
//		}
//		bw.close();
//		
//		//	store regions
//		pm.setStep("Storing region data");
//		pm.setBaseProgress(17);
//		pm.setMaxProgress(20);
//		bw = getWriter(data, "regions.csv", lowMemory);
//		keys.clear();
//		keys.parseAndAddElements((PAGE_ID_ATTRIBUTE + ";" + BOUNDING_BOX_ATTRIBUTE + ";" + TYPE_ATTRIBUTE + ";" + ImObject.ATTRIBUTES_STRING_ATTRIBUTE), ";");
//		writeKeys(keys, bw);
//		for (int p = 0; p < pages.length; p++) {
//			pm.setInfo("Page " + p);
//			pm.setProgress((p * 100) / pages.length);
//			
//			//	data for regions
//			ImRegion[] pageRegs = pages[p].getRegions();
//			Arrays.sort(pageRegs, ImUtils.sizeOrder); // sort regions to keep order and hash stable
//			for (int r = 0; r < pageRegs.length; r++) {
//				StringTupel regData = new StringTupel(keys.size());
//				regData.setValue(PAGE_ID_ATTRIBUTE, ("" + pageRegs[r].pageId));
//				regData.setValue(BOUNDING_BOX_ATTRIBUTE, pageRegs[r].bounds.toString());
//				regData.setValue(TYPE_ATTRIBUTE, pageRegs[r].getType());
//				regData.setValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, getAttributesString(pageRegs[r]));
//				writeValues(keys, regData, bw);
//			}
//		}
//		bw.close();
//		
//		//	store annotations
//		pm.setStep("Storing annotation data");
//		pm.setBaseProgress(20);
//		pm.setMaxProgress(25);
//		bw = getWriter(data, "annotations.csv", lowMemory);
//		keys.clear();
//		keys.parseAndAddElements((ImAnnotation.FIRST_WORD_ATTRIBUTE + ";" + ImAnnotation.LAST_WORD_ATTRIBUTE + ";" + TYPE_ATTRIBUTE + ";" + ImObject.ATTRIBUTES_STRING_ATTRIBUTE), ";");
//		writeKeys(keys, bw);
//		ImAnnotation[] annots = doc.getAnnotations();
//		Arrays.sort(annots, annotationOrder); // sort annotations to keep order and hash stable
//		for (int a = 0; a < annots.length; a++) {
//			pm.setProgress((a * 100) / annots.length);
//			StringTupel annotData = new StringTupel(keys.size());
//			annotData.setValue(ImAnnotation.FIRST_WORD_ATTRIBUTE, annots[a].getFirstWord().getLocalID());
//			annotData.setValue(ImAnnotation.LAST_WORD_ATTRIBUTE, annots[a].getLastWord().getLocalID());
//			annotData.setValue(TYPE_ATTRIBUTE, annots[a].getType());
//			annotData.setValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, getAttributesString(annots[a]));
//			writeValues(keys, annotData, bw);
//		}
//		bw.close();
//		
//		//	store fonts (if any)
//		pm.setStep("Storing font data");
//		pm.setBaseProgress(25);
//		pm.setMaxProgress(30);
//		bw = getWriter(data, "fonts.csv", lowMemory);
//		keys.clear();
//		keys.parseAndAddElements((ImFont.NAME_ATTRIBUTE + ";" + ImFont.STYLE_ATTRIBUTE + ";" + ImFont.CHARACTER_ID_ATTRIBUTE + ";" + ImFont.CHARACTER_STRING_ATTRIBUTE + ";" + ImFont.CHARACTER_IMAGE_ATTRIBUTE + ";" + ImFont.CHARACTER_PATH_ATTRIBUTE), ";");
//		writeKeys(keys, bw);
//		ImFont[] fonts = doc.getFonts(); // no need to sort here, fonts come sorted
//		for (int f = 0; f < fonts.length; f++) {
//			pm.setProgress((f * 100) / fonts.length);
//			String fontStyle;
//			if (fonts[f].isMixedStyle())
//				fontStyle = "X";
//			else {
//				fontStyle = "";
//				if (fonts[f].isBold())
//					fontStyle += "B";
//				if (fonts[f].isItalics())
//					fontStyle += "I";
//				if (fonts[f].isSerif())
//					fontStyle += "S";
//				if (fonts[f].isMonospaced())
//					fontStyle += "M";
//			}
//			String fontAttributes = getAttributesString(fonts[f]);
//			if ((fontAttributes.length() != 0) || fonts[f].isMixedStyle()) {
//				StringTupel fontData = new StringTupel(keys.size());
//				fontData.setValue(ImFont.NAME_ATTRIBUTE, fonts[f].name);
//				fontData.setValue(ImFont.STYLE_ATTRIBUTE, fontStyle);
//				fontData.setValue(ImFont.CHARACTER_ID_ATTRIBUTE, ((fonts[f].getCharCodeLength() == 2) ? "00" : ((fonts[f].getCharCodeLength() == 3) ? "000" : "0000")));
//				fontData.setValue(ImFont.CHARACTER_STRING_ATTRIBUTE, "");
//				fontData.setValue(ImFont.CHARACTER_IMAGE_ATTRIBUTE, fontAttributes);
//				writeValues(keys, fontData, bw);
//			}
//			int[] charIDs = fonts[f].getCharacterIDs(); // no need to sort here, those IDs come sorted
//			for (int c = 0; c < charIDs.length; c++) {
//				StringTupel charData = new StringTupel(keys.size());
//				charData.setValue(ImFont.NAME_ATTRIBUTE, fonts[f].name);
//				if (fonts[f].isMixedStyle()) {
//					String charStyle = "";
//					if (fonts[f].isBold(charIDs[c]))
//						charStyle += "B";
//					if (fonts[f].isItalics(charIDs[c]))
//						charStyle += "I";
//					if (fonts[f].isSerif(charIDs[c]))
//						charStyle += "S";
//					if (fonts[f].isMonospaced(charIDs[c]))
//						charStyle += "M";
//					charData.setValue(ImFont.STYLE_ATTRIBUTE, charStyle);
//				}
//				else charData.setValue(ImFont.STYLE_ATTRIBUTE, fontStyle);
//				charData.setValue(ImFont.CHARACTER_ID_ATTRIBUTE, Integer.toString(charIDs[c], 16).toUpperCase());
//				String charStr = fonts[f].getString(charIDs[c]);
//				if (charStr != null)
//					charData.setValue(ImFont.CHARACTER_STRING_ATTRIBUTE, charStr);
//				String charImageHex = fonts[f].getImageHex(charIDs[c]);
//				if (charImageHex != null)
//					charData.setValue(ImFont.CHARACTER_IMAGE_ATTRIBUTE, charImageHex);
//				String charPathString = fonts[f].getPathString(charIDs[c]);
//				if (charPathString != null)
//					charData.setValue(ImFont.CHARACTER_PATH_ATTRIBUTE, charPathString);
//				writeValues(keys, charData, bw);
//			}
//		}
//		bw.close();
//		
//		//	store page images
//		pm.setStep("Storing page images");
//		pm.setBaseProgress(30);
//		pm.setMaxProgress(79);
//		StringRelation pageImagesData = new StringRelation();
//		for (int p = 0; p < pages.length; p++) {
//			pm.setInfo("Page " + p);
//			pm.setProgress((p * 100) / pages.length);
//			String piAttributes;
//			
//			/* we have to write the page image proper if
//			 * - we're zipping up the document
//			 * - the document was loaded via other facilities
//			 * - the page image store has been replaced
//			 * - the document was loaded from, and is still bound to, its zipped-up form
//			 * - the document was loaded from, and is still bound to, a different folder */
//			boolean writePi = false;
//			if (data instanceof ZipOutImDocumentData)
//				writePi = true;
//			else if (!(doc instanceof DataBoundImDocument))
//				writePi = true;
//			else if (((DataBoundImDocument) doc).dbidPis == null)
//				writePi = true;
//			else if (((DataBoundImDocument) doc).docData.getDocumentDataId() == null)
//				writePi = true;
//			else if (!((DataBoundImDocument) doc).docData.getDocumentDataId().equals(data.getDocumentDataId()))
//				writePi = true;
//			
//			//	store page image proper if we have to
//			if (writePi) {
//				PageImageInputStream piis = pages[p].getPageImageAsStream();
//				piAttributes = getPageImageAttributes(piis);
//				String piName = PageImage.getPageImageName(doc.docId, pages[p].pageId);
//				piName = ("page" + piName.substring(doc.docId.length() + ".".length()) + "." + IMAGE_FORMAT);
//				OutputStream piOut = data.getOutputStream(piName, true);
//				byte[] pib = new byte[1024];
//				for (int r; (r = piis.read(pib, 0, pib.length)) != -1;)
//					piOut.write(pib, 0, r);
//				piOut.close();
//				piis.close();
//			}
//			
//			//	otherwise, we only have to get the attributes (the IMF entry already exists, as otherwise, we'd write the page image)
//			else {
//				PageImageAttributes pia = ((DataBoundImDocument) doc).dbidPis.getPageImageAttributes(new Integer(pages[p].pageId));
//				if (pia == null) {
//					PageImageInputStream piis = pages[p].getPageImageAsStream();
//					piAttributes = getPageImageAttributes(piis);
//					piis.close();
//				}
//				else piAttributes = pia.toAttributeString();
//			}
//			
//			StringTupel piData = new StringTupel(keys.size());
//			piData.setValue(ImObject.PAGE_ID_ATTRIBUTE, ("" + pages[p].pageId));
//			piData.setValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, piAttributes);
//			pageImagesData.addElement(piData);
//		}
//		
//		//	store page image data
//		pm.setStep("Storing page image data");
//		pm.setBaseProgress(79);
//		pm.setMaxProgress(80);
//		bw = getWriter(data, "pageImages.csv", false);
//		keys.clear();
//		keys.parseAndAddElements((ImObject.PAGE_ID_ATTRIBUTE + ";" + ImObject.ATTRIBUTES_STRING_ATTRIBUTE), ";");
//		writeKeys(keys, bw);
//		for (int p = 0; p < pageImagesData.size(); p++)
//			writeValues(keys, pageImagesData.get(p), bw);
//		bw.close();
//		
//		//	store meta data of supplements
//		pm.setStep("Storing supplement data");
//		pm.setBaseProgress(80);
//		pm.setMaxProgress(85);
//		bw = getWriter(data, "supplements.csv", false);
//		keys.clear();
//		keys.parseAndAddElements((ImSupplement.ID_ATTRIBUTE + ";" + ImSupplement.TYPE_ATTRIBUTE + ";" + ImSupplement.MIME_TYPE_ATTRIBUTE + ";" + ImObject.ATTRIBUTES_STRING_ATTRIBUTE), ";");
//		writeKeys(keys, bw);
//		ImSupplement[] supplements = doc.getSupplements();
//		for (int s = 0; s < supplements.length; s++) {
//			StringTupel supplementData = new StringTupel(keys.size());
//			supplementData.setValue(ImSupplement.ID_ATTRIBUTE, supplements[s].getId());
//			supplementData.setValue(ImSupplement.TYPE_ATTRIBUTE, supplements[s].getType());
//			supplementData.setValue(ImSupplement.MIME_TYPE_ATTRIBUTE, supplements[s].getMimeType());
//			supplementData.setValue(ImObject.ATTRIBUTES_STRING_ATTRIBUTE, getAttributesString(supplements[s]));
//			writeValues(keys, supplementData, bw);
//		}
//		bw.close();
//		
//		//	store supplements proper
//		pm.setStep("Storing supplements");
//		pm.setBaseProgress(85);
//		pm.setMaxProgress(100);
//		for (int s = 0; s < supplements.length; s++) {
//			pm.setProgress((s * 100) / supplements.length);
////			String smt = supplements[s].getMimeType();
////			String sfn = (supplements[s].getId() + "." + smt.substring(smt.lastIndexOf('/') + "/".length()));
//			String sfn = supplements[s].getFileName();
//			pm.setInfo("Supplement " + sfn);
//			
//			/* we have to write the supplement proper if
//			 * - we're zipping up the document
//			 * - the document was loaded via other facilities
//			 * - the document was loaded from, and is still bound to, its zipped-up form
//			 * - the document was loaded from, and is still bound to, a different folder
//			 * - the supplement was newly added or modified */
//			boolean writeSd = false;
//			if (data instanceof ZipOutImDocumentData)
//				writeSd = true;
//			else if (!(doc instanceof DataBoundImDocument))
//				writeSd = true;
//			else if (((DataBoundImDocument) doc).docData.getDocumentDataId() == null)
//				writeSd = true;
//			else if (!((DataBoundImDocument) doc).docData.getDocumentDataId().equals(data.getDocumentDataId()))
//				writeSd = true;
//			else if (((DataBoundImDocument) doc).isSupplementDirty(sfn))
//				writeSd = true;
//			
//			//	store supplement data proper if we have to
//			if (writeSd) {
//				InputStream sdIn = supplements[s].getInputStream();
//				OutputStream sdOut = data.getOutputStream(sfn, true);
//				byte[] sdb = new byte[1024];
//				for (int r; (r = sdIn.read(sdb, 0, sdb.length)) != -1;)
//					sdOut.write(sdb, 0, r);
//				sdOut.close();
//				sdIn.close();
//			}
//		}
//		
//		//	finally
//		pm.setProgress(100);
//		
//		//	finalize folder storage
//		if (data instanceof FolderImDocumentData) {
//			
//			//	if the document was loaded here, update IMF entries (both ways), and switch document to folder mode
//			if (doc instanceof DataBoundImDocument) {
//				ImDocumentData oldDocData = ((DataBoundImDocument) doc).bindToData(data);
//				if (oldDocData != null)
//					oldDocData.dispose(); // dispose any replaced document data
//			}
//			
//			//	write or overwrite 'enries.txt'
//			((FolderImDocumentData) data).storeEntryList();
//		}
//		
//		//	return entry list for external use
//		return data.getEntries();
//	}
//	
//	private static Comparator annotationOrder = new Comparator() {
//		public int compare(Object obj1, Object obj2) {
//			ImAnnotation annot1 = ((ImAnnotation) obj1);
//			ImAnnotation annot2 = ((ImAnnotation) obj2);
//			int c = ImUtils.textStreamOrder.compare(annot1.getFirstWord(), annot2.getFirstWord());
//			if (c != 0)
//				return c;
//			c = ImUtils.textStreamOrder.compare(annot2.getLastWord(), annot1.getLastWord());
//			if (c != 0)
//				return c;
//			return 0; // no type comparison, ImDocument orders by creation timestamp, and that should pertain on loading
//		}
//	};
//	
//	private static void writeKeys(StringVector keys, BufferedWriter bw) throws IOException {
//		for (int k = 0; k < keys.size(); k++) {
//			if (k != 0)
//				bw.write(',');
//			bw.write('"');
//			bw.write(keys.get(k));
//			bw.write('"');
//		}
//		bw.newLine();
//	}
//	
//	private static void writeValues(StringVector keys, StringTupel st, BufferedWriter bw) throws IOException {
//		for (int k = 0; k < keys.size(); k++) {
//			if (k != 0)
//				bw.write(',');
//			bw.write('"');
//			String value = st.getValue(keys.get(k), "");
//			for (int c = 0; c < value.length(); c++) {
//				char ch = value.charAt(c);
//				if (ch == '"')
//					bw.write('"');
//				bw.write(ch);
//			}
//			bw.write('"');
//		}
//		bw.newLine();
//	}
//	
//	private static BufferedWriter getWriter(ImDocumentData data, String entryName, boolean writeDirectly) throws IOException {
//		return new BufferedWriter(new OutputStreamWriter(data.getOutputStream(entryName, writeDirectly), "UTF-8"));
//	}
//	
//	/**
//	 * Linearize the attributes of an attributed object into a single-line, CSV
//	 * friendly form. Attribute values are converted into strings and escaped
//	 * based on XML rules. Afterwards, each attribute value is appended to its
//	 * corresponding attribute name, enclosed in angle brackets. The
//	 * concatenation of these attribute name and value pairs yields the final
//	 * linearization.
//	 * @param attr the attributed object whose attributes to linearize
//	 * @return the linearized attributes of the argument object
//	 */
//	public static String getAttributesString(Attributed attr) {
//		StringBuffer attributes = new StringBuffer();
//		String[] attributeNames = attr.getAttributeNames();
//		for (int a = 0; a < attributeNames.length; a++) {
//			Object value = attr.getAttribute(attributeNames[a]);
//			if (value != null) try {
//				attributes.append(attributeNames[a] + "<" + escape(value.toString()) + ">");
//			} catch (RuntimeException re) {}
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
//	private static String getPageImageAttributes(PageImageInputStream piis) {
//		StringBuffer piAttributes = new StringBuffer();
//		piAttributes.append("originalWidth<" + piis.originalWidth + ">");
//		piAttributes.append("originalHeight<" + piis.originalHeight + ">");
//		piAttributes.append("originalDpi<" + piis.originalDpi + ">");
//		if (piis.currentDpi != piis.originalDpi)
//			piAttributes.append("currentDpi<" + piis.currentDpi + ">");
//		if (piis.leftEdge != 0)
//			piAttributes.append("leftEdge<" + piis.leftEdge + ">");
//		if (piis.rightEdge != 0)
//			piAttributes.append("rightEdge<" + piis.rightEdge + ">");
//		if (piis.topEdge != 0)
//			piAttributes.append("topEdge<" + piis.topEdge + ">");
//		if (piis.bottomEdge != 0)
//			piAttributes.append("bottomEdge<" + piis.bottomEdge + ">");
//		return piAttributes.toString();
//	}
//	
//	/**
//	 * Parse and un-escape attribute name and value pairs that were previously
//	 * linearized via <code>getAttributesString()</code>. The parsed attribute
//	 * name and value pairs are stored in the argument attributed object.
//	 * @param attr the attributed objects to store the attributes in
//	 * @param attributes the linearized attribute and value string
//	 */
//	public static void setAttributes(Attributed attr, String attributes) {
//		String[] nameValuePairs = attributes.split("[\\<\\>]");
//		for (int p = 0; p < (nameValuePairs.length-1); p+= 2)
//			attr.setAttribute(nameValuePairs[p], unescape(nameValuePairs[p+1]));
//	}
//	private static String unescape(String escapedString) {
//		StringBuffer string = new StringBuffer();
//		for (int c = 0; c < escapedString.length();) {
//			char ch = escapedString.charAt(c);
//			if (ch == '&') {
//				if (escapedString.startsWith("amp;", (c+1))) {
//					string.append('&');
//					c += "&amp;".length();
//				}
//				else if (escapedString.startsWith("lt;", (c+1))) {
//					string.append('<');
//					c += "&lt;".length();
//				}
//				else if (escapedString.startsWith("gt;", (c+1))) {
//					string.append('>');
//					c += "&gt;".length();
//				}
//				else if (escapedString.startsWith("quot;", (c+1))) {
//					string.append('"');
//					c += "&quot;".length();
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
////	
////	/**
////	 * @param args
////	 */
////	public static void main(String[] args) throws Exception {
////		final File baseFolder = new File("E:/Testdaten/PdfExtract/");
////		final String pdfName = "zt00872.pdf";
////		
////		long startTime = System.currentTimeMillis();
////		long startMem = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
////		ImDocument imDoc = loadDocument(new FileInputStream(new File(baseFolder, (pdfName + ".imf"))));
////		System.out.println("LOAD TIME: " + (System.currentTimeMillis() - startTime));
////		System.out.println("LOAD MEMORY: " + ((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) - startMem));
////		System.gc();
////		System.out.println("LOAD MEMORY GC: " + ((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) - startMem));
//////		ImDocument imDoc = loadDocument(new File(baseFolder, (pdfName + ".new.imd")));
////		
//////		File imFile = new File(baseFolder, (pdfName + ".widd.imf"));
//////		OutputStream imOut = new BufferedOutputStream(new FileOutputStream(imFile));
//////		storeDocument(imDoc, imOut);
//////		imOut.flush();
//////		imOut.close();
//////		
//////		File imFolder = new File(baseFolder, (pdfName + ".widd.imd"));
//////		imFolder.mkdirs();
//////		storeDocument(imDoc, imFolder);
////	}
//}