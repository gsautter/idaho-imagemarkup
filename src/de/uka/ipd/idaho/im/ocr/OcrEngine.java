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
 *     * Neither the name of the Universität Karlsruhe (TH) nor the
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
package de.uka.ipd.idaho.im.ocr;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.uka.ipd.idaho.gamta.Gamta;
import de.uka.ipd.idaho.gamta.TokenSequence;
import de.uka.ipd.idaho.gamta.Tokenizer;
import de.uka.ipd.idaho.gamta.util.ProgressMonitor;
import de.uka.ipd.idaho.gamta.util.imaging.BoundingBox;
import de.uka.ipd.idaho.gamta.util.imaging.ImagingConstants;
import de.uka.ipd.idaho.gamta.util.imaging.PageImage;
import de.uka.ipd.idaho.im.ImDocument;
import de.uka.ipd.idaho.im.ImPage;
import de.uka.ipd.idaho.im.ImRegion;
import de.uka.ipd.idaho.im.ImWord;
import de.uka.ipd.idaho.im.analysis.PageImageConverter;
import de.uka.ipd.idaho.im.util.ImFontUtils;

/**
 * Wrapper class for Google's Tesseract OCR engine.
 * 
 * @author sautter
 */
public class OcrEngine implements ImagingConstants {
	
	/* make sure we have the fonts we need */
	static {
		ImFontUtils.loadFreeFonts();
	}
	
	private File basePath;
	private File tessPath;
	private File cachePath;
	
	private TreeSet langs = new TreeSet(String.CASE_INSENSITIVE_ORDER);
	
	private int maxInstances;
	private LinkedList instances = new LinkedList();
	private LinkedList allInstances = new LinkedList();
	private Set awaitingInstance = Collections.synchronizedSet(new HashSet());
	
	/**
	 * Constructor (uses single-instance mode)
	 * @param basePath the root path of the individual Tesseract binary folders
	 */
	public OcrEngine(File basePath) {
		this(basePath, null, 0);
	}
	
	/**
	 * Constructor
	 * @param basePath the root path of the individual Tesseract binary folders
	 * @param maxInstances the maximum size of the instance pool (values less
	 *            than two deactivate the instance pool altogether)
	 */
	public OcrEngine(File basePath, int maxInstances) {
		this(basePath, null, maxInstances);
	}
	
	/**
	 * Constructor (uses single-instance mode)
	 * @param basePath the root path of the individual Tesseract binary folders
	 * @param cachePath the folder to use for image caching
	 */
	public OcrEngine(File basePath, File cachePath) {
		this(basePath, cachePath, 0);
	}
	
	/**
	 * Constructor
	 * @param basePath the root path of the individual Tesseract binary folders
	 * @param cachePath the folder to use for image caching
	 * @param maxInstances the maximum size of the instance pool (values less
	 *            than two deactivate the instance pool altogether)
	 */
	public OcrEngine(File basePath, File cachePath, int maxInstances) {
		this.basePath = ("OcrEngine".equals(basePath.getName()) ? basePath : new File(basePath, "OcrEngine"));
		if (!this.basePath.exists())
			this.basePath.mkdirs();
		this.loadCorrections();
		this.tessPath = new File(this.basePath, "StreamTess");
		if (!this.tessPath.exists())
			this.tessPath.mkdirs();
		this.cachePath = cachePath;
		this.maxInstances = Math.max(1, maxInstances);
	}
	
	private String getLangsString() {
		if (this.langs.isEmpty())
			return "eng";
		StringBuffer langs = new StringBuffer("eng");
		for (Iterator lit = this.langs.iterator(); lit.hasNext();) {
			String lang = ((String) lit.next());
			if (!"eng".equalsIgnoreCase(lang))
				langs.append("+" + lang);
		}
		return langs.toString();
	}
	
	/**
	 * Add languages to the OCR engine. The returned boolean indicates if any
	 * languages were actually newly added.
	 * @param langs the languages to add, separated by '+'
	 * @return true if there are new languages
	 * @throws IOException
	 */
	public boolean addLangs(String langs) throws IOException {
		if (langs == null)
			return false;
		boolean newLangs = this.langs.addAll(Arrays.asList(langs.trim().split("\\s*\\+\\s*")));
		if (newLangs) {
			synchronized (this.instances) {
				for (Iterator iit = this.allInstances.iterator(); iit.hasNext();)
					((PooledOcrInstance) iit.next()).scheduleLanguageUpdate();
			}
		}
		return newLangs;
	}
	
	private PooledOcrInstance getOcrInstance(long timeout) throws IOException {
		
		//	we're shutting down ...
		if (this.maxInstances < 1) {
			System.out.println("OCR Engine: returning null instance due to shutdown (1)");
			return null;
		}
		
		//	we're shutting down ...
		if (this.instances == null) {
			System.out.println("OCR Engine: returning null instance due to shutdown (1)");
			return null;
		}
		
		//	get instance from pool
		synchronized (this.instances) {
			
			//	we have an instance available, use it
			if (this.instances.size() != 0)
				return ((PooledOcrInstance) this.instances.removeFirst());
			
			//	we have more resources available, create additional instance
			else if (this.allInstances.size() < this.maxInstances)
				return this.produceOcrInstance();
			
			//	no instances available right now
			else {
				
				//	get waiting thread
				Thread awaitingInstance = Thread.currentThread();
				
				//	wait for instance to become available
				this.awaitingInstance.add(awaitingInstance);
				try {
					this.instances.wait(timeout);
				}
				catch (InterruptedException ie) {
					System.out.println("OCR Engine: waiting thread interrupted");
				}
				this.awaitingInstance.remove(awaitingInstance);
				
				//	we're shutting down ...
				if (this.instances == null) {
					System.out.println("OCR Engine: returning null instance due to shutdown (3)");
					return null;
				}
				
				//	timeout is up
				else if (this.instances.size() == 0) {
					System.out.println("OCR Engine: returning null instance after timeout of " + timeout);
					return null;
				}
				
				//	if we have an instance now, we can use it ... otherwise the timeout is up
				else return ((PooledOcrInstance) this.instances.removeFirst());
			}
		}
	}
	
	private PooledOcrInstance produceOcrInstance() throws IOException {
		PooledOcrInstance poi = new PooledOcrInstance(new OcrInstance(this.tessPath, this.cachePath, this.getLangsString()));
		this.allInstances.addLast(poi);
		return poi;
	}
	
	private class PooledOcrInstance {
		private OcrInstance ocrEngine;
		private boolean updateLanguages = false;
		
		PooledOcrInstance(OcrInstance ocrEngine) {
			this.ocrEngine = ocrEngine;
		}
		
		void scheduleLanguageUpdate() {
			this.updateLanguages = true;
		}
		
		OcrWord[] doBlockOcr(BufferedImage blockTextImage) throws IOException {
			
			//	update languages if required
			if (this.updateLanguages) try {
				this.ocrEngine.addLangs(getLangsString());
				this.updateLanguages = false;
			} catch (IOException ioe) {}
			
			//	do OCR
			try {
				return this.ocrEngine.doBlockOcr(blockTextImage);
			}
			
			//	return instance to pool
			finally {
				synchronized (instances) {
					instances.addLast(this);
					instances.notify();
				}
			}
		}
		
		void shutdown() throws IOException {
			this.ocrEngine.shutdown();
		}
	}
	
	/**
	 * IOExceptions of this class indicate that in a parallel scenario, an OCR
	 * instance did not become available within a specified timeout.
	 * 
	 * @author sautter
	 */
	public static class OcrInstanceUnavailableException extends IOException {
		OcrInstanceUnavailableException(String message) {
			super(message);
		}
	}
	
	/**
	 * Shut down the OCR engine, in particular all OCR instances.
	 * @throws IOException
	 */
	public void shutdown() throws IOException {
		
		//	switch to shutdown mode
		this.maxInstances = -1;
		
		//	shut down instance pool
		if (this.instances == null)
			return;
		
		//	free up all threads still waiting for an instance
		LinkedList instances = this.instances;
		this.instances = null;
		while (this.awaitingInstance.size() != 0)
			
			//	we have to release the lock in each loop so notified threads can continue
			synchronized (instances) {
				instances.notify();
			}
		
		//	shut down instances proper
		while (this.allInstances.size() != 0)
			((PooledOcrInstance) this.allInstances.removeFirst()).shutdown();;
	}
	
	/**
	 * Run Tesseract on the blocks marked in a page. This method runs OCR on
	 * the image parts delimited by the regions of type <i>block</i> that
	 * are marked in the argument page. The resulting words are added to the
	 * page, with the OCR result in their string value. This method ignores
	 * any blocks that already contain words.
	 * @param page the page to run Tesseract on
	 * @param psm a monitor object for reporting progress, e.g. to a UI
	 * @throws OcrInstanceUnavailableException
	 * @throws IOException
	 */
	public void doBlockOcr(ImPage page, ProgressMonitor psm) throws IOException {
		this.doBlockOcr(page, psm, 0);
	}
	
	/**
	 * Run Tesseract on the blocks marked in a page. This method runs OCR on
	 * the image parts delimited by the regions of type <i>block</i> that
	 * are marked in the argument page. The resulting words are added to the
	 * page, with the OCR result in their string value. This method ignores
	 * any blocks that already contain words.
	 * @param page the page to run Tesseract on
	 * @param psm a monitor object for reporting progress, e.g. to a UI
	 * @param timeout the maximum time to wait for an OCR instance to become
	 *            available (in milliseconds, relevant only in thread pool mode) 
	 * @throws OcrInstanceUnavailableException
	 * @throws IOException
	 */
	public void doBlockOcr(ImPage page, ProgressMonitor psm, long timeout) throws IOException {
		PageImage pageImage = page.getPageImage();
		if (pageImage == null)
			throw new IOException("Could not find page image");
		this.doBlockOcr(page, pageImage, psm, timeout);
	}
	
	/**
	 * Run Tesseract on the blocks marked in a page. This method runs OCR on
	 * the image parts delimited by the regions of type <i>block</i> that
	 * are marked in the argument page. The resulting words are added to the
	 * page, with the OCR result in their string value. This method ignores
	 * any blocks that already contain words.
	 * @param page the page to run Tesseract on
	 * @param pageImage the image of the page
	 * @param psm a monitor object for reporting progress, e.g. to a UI
	 * @throws OcrInstanceUnavailableException
	 * @throws IOException
	 */
	public void doBlockOcr(ImPage page, PageImage pageImage, ProgressMonitor psm) throws IOException {
		this.doBlockOcr(page, pageImage, psm, 0);
	}
	
	/**
	 * Run Tesseract on the blocks marked in a page. This method runs OCR on
	 * the image parts delimited by the regions of type <i>block</i> that
	 * are marked in the argument page. The resulting words are added to the
	 * page, with the OCR result in their string value. This method ignores
	 * any blocks that already contain words.
	 * @param page the page to run Tesseract on
	 * @param pageImage the image of the page
	 * @param psm a monitor object for reporting progress, e.g. to a UI
	 * @param timeout the maximum time to wait for an OCR instance to become
	 *            available (in milliseconds, relevant only in thread pool mode) 
	 * @throws OcrInstanceUnavailableException
	 * @throws IOException
	 */
	public void doBlockOcr(ImPage page, PageImage pageImage, ProgressMonitor psm, long timeout) throws IOException {
		
		//	get blocks
		ImRegion[] blocks = page.getRegions(BLOCK_ANNOTATION_TYPE);
		if (blocks.length == 0)
			return;
		
		//	test for previous OCR
		boolean[] hasOcr = new boolean[blocks.length];
		int hasOcrCount = 0;
		for (int b = 0; b < blocks.length; b++) {
			hasOcr[b] = (blocks[b].getWords().length != 0);
			if (hasOcr[b])
				hasOcrCount++;
		}
		
		//	nothing to do at all
		if (hasOcrCount == blocks.length)
			return;
		
		//	create single-page document for structure analysis
		ImDocument structDoc = new ImDocument(page.getDocument().docId);
		ImPage structPage = new ImPage(structDoc, page.pageId, page.bounds);
		
		//	work block by block
		for (int b = 0; b < blocks.length; b++) {
			psm.setInfo("Doing block " + b + " at " + blocks[b].bounds.toString());
			psm.setProgress((b * 100) / blocks.length);
			
			//	we've seen this one before
			if (hasOcr[b]) {
				psm.setInfo(" ==> OCRed before");
				continue;
			}
			
			//	TODOnot if we are in multi-thread mode, we can parallelize this
			//	no, we cannot parallelize this, as then we would have to synchronize on page
			
			//	do OCR
			this.doBlockOcr(blocks[b], pageImage, psm, b, structPage, timeout);
		}
		
		//	finish progress
		psm.setProgress(100);
	}
	
	/**
	 * Run Tesseract on a single block. This method runs OCR on the image parts
	 * delimited by the bounding box of the argument block. The resulting words
	 * are added to the page the block lies in, with the OCR result in their
	 * string value. This method does OCR and adds the resulting words even if
	 * the argument block already contains words. However, it is strongly
	 * recommended that pre-existing words are removed before handing a block
	 * to this method. If that is undesired, it is best to work on a copy.
	 * @param block the block to run Tesseract on
	 * @param pageImage the image of the page
	 * @param psm a monitor object for reporting progress, e.g. to a UI
	 * @throws OcrInstanceUnavailableException
	 * @throws IOException
	 */
	public void doBlockOcr(ImRegion block, PageImage pageImage, ProgressMonitor psm) throws IOException {
		this.doBlockOcr(block, pageImage, psm, 0);
	}
	
	/**
	 * Run Tesseract on a single block. This method runs OCR on the image parts
	 * delimited by the bounding box of the argument block. The resulting words
	 * are added to the page the block lies in, with the OCR result in their
	 * string value. This method does OCR and adds the resulting words even if
	 * the argument block already contains words. However, it is strongly
	 * recommended that pre-existing words are removed before handing a block
	 * to this method. If that is undesired, it is best to work on a copy.
	 * @param block the block to run Tesseract on
	 * @param pageImage the image of the page
	 * @param pm a monitor object for reporting progress, e.g. to a UI
	 * @param timeout the maximum time to wait for an OCR instance to become
	 *            available (in milliseconds, relevant only in thread pool mode)
	 * @throws OcrInstanceUnavailableException
	 * @throws IOException
	 */
	public void doBlockOcr(ImRegion block, PageImage pageImage, ProgressMonitor pm, long timeout) throws IOException {
		
		//	create single-page document for structure analysis
		ImDocument structDoc = new ImDocument(block.getDocument().docId);
		ImPage structPage = new ImPage(structDoc, block.getPage().pageId, block.getPage().bounds);
		
		//	do OCR without further checks
		this.doBlockOcr(block, pageImage, pm, ((int) (Thread.currentThread().getId() & Integer.MAX_VALUE)), structPage, timeout);
	}
	
	private void doBlockOcr(ImRegion block, PageImage pageImage, ProgressMonitor pm, int blockNr, ImPage structPage, long timeout) throws IOException {
		if (pm == null)
			pm = ProgressMonitor.dummy;
		
		//	build text image
		BufferedImage blockTextImage = this.getOcrImage(block.bounds, pageImage);
		
		//	get tokenizer to check and split words with
		Tokenizer tokenizer = ((Tokenizer) block.getDocument().getAttribute(ImDocument.TOKENIZER_ATTRIBUTE, Gamta.INNER_PUNCTUATION_TOKENIZER));
		
		//	do OCR
		OcrWord[] blockWords = this.doBlockOcr(blockNr, blockTextImage, pageImage.currentDpi, pm, false, timeout, tokenizer);
		if (blockWords.length == 0) {
			pm.setInfo(" ==> no text found");
			return;
		}
		pm.setInfo(" ==> found " + blockWords.length + " words:");
		for (int w = 0; w < blockWords.length; w++)
			pm.setInfo("   - '" + blockWords[w].str + "' at " + blockWords[w].box.toString());
		
		//	split up words that tokenize apart
		blockWords = this.checkTokenization(blockWords, blockTextImage, tokenizer);
		
		//	index and sort Tesseract OCR result
		ArrayList blockWordBoxList = new ArrayList();
		HashMap blockWordsByBoxes = new HashMap();
		for (int w = 0; w < blockWords.length; w++) {
			if (blockWords[w].str.length() == 0)
				continue;
			BoundingBox blockWordBox = new BoundingBox((blockWords[w].box.left + block.bounds.left), (blockWords[w].box.right + block.bounds.left), (blockWords[w].box.top + block.bounds.top), (blockWords[w].box.bottom + block.bounds.top));
			blockWordBoxList.add(blockWordBox);
			blockWordsByBoxes.put(blockWordBox.toString(), blockWords[w]);
		}
		
		//	do own content analysis to find words missed by Tessearct (tends to happen with page numbers ...)
		pm.setInfo(" - analyzing block structure without words");
		ImRegion structBlock = new ImRegion(structPage, block.bounds);
		PageImageConverter.fillInTextBlockStructure(structBlock, pageImage, ((BoundingBox[]) null), pm);
		
		//	get words
		ImWord[] blockStructureWords = structBlock.getWords();
		pm.setInfo(" ==> found " + blockStructureWords.length + " own words:");
		for (int w = 0; w < blockStructureWords.length; w++)
			pm.setInfo("   - " + blockStructureWords[w].bounds);
		
		//	index and sort result of own layout analysis
		TreeMap blockStructureWordsByBoxes = new TreeMap(new Comparator() {
			public int compare(Object o1, Object o2) {
				BoundingBox bb1 = ((BoundingBox) o1);
				BoundingBox bb2 = ((BoundingBox) o2);
				if (bb1.bottom <= bb2.top)
					return -1;
				if (bb2.bottom <= bb1.top)
					return 1;
				if (bb1.right <= bb2.left)
					return -1;
				if (bb2.right <= bb1.left)
					return 1;
				return 0;
			}
		});
		for (int w = 0; w < blockStructureWords.length; w++)
			blockStructureWordsByBoxes.put(blockStructureWords[w].bounds, blockStructureWords[w]);
		
		//	subtract Tesseract result TODO retain words whose Tessearcted counterpart has an empty string
		for (int w = 0; w < blockWordBoxList.size(); w++) {
			BoundingBox blockWordBox = ((BoundingBox) blockWordBoxList.get(w));
			for (Iterator bswbit = blockStructureWordsByBoxes.keySet().iterator(); bswbit.hasNext();) {
				BoundingBox blockStructureWordBox = ((BoundingBox) bswbit.next());
				if (blockWordBox.bottom <= blockStructureWordBox.top)
					break;
				if (blockStructureWordBox.bottom <= blockWordBox.top)
					continue;
				if (blockWordBox.right <= blockStructureWordBox.left)
					break;
				if (blockStructureWordBox.right <= blockWordBox.left)
					continue;
				bswbit.remove();
			}
		}
		pm.setInfo(" ==> " + blockStructureWordsByBoxes.size() + " words missed by Tesseract");
		
		//	individually OCR whatever Tesseract did not find in full block pass
		pm.setInfo(" - recovering possible words missed by Tesseract");
		int blockStructureWordNr = 0;
		for (Iterator bswbit = blockStructureWordsByBoxes.keySet().iterator(); bswbit.hasNext();) {
			BoundingBox blockStructureWordBox = ((BoundingBox) bswbit.next());
			pm.setInfo("   - OCRing " + blockStructureWordBox.toString());
			BufferedImage blockStructureWordImage = this.getOcrImage(blockStructureWordBox, pageImage);
			OcrWord[] blockStructureWordContent = this.doBlockOcr(((blockNr * 100) + blockStructureWordNr++), blockStructureWordImage, pageImage.currentDpi, pm, false, timeout, tokenizer);
			blockStructureWordContent = this.checkTokenization(blockStructureWordContent, blockStructureWordImage, tokenizer);
			for (int w = 0; w < blockStructureWordContent.length; w++) {
				pm.setInfo("     - '" + blockStructureWordContent[w].str + "' at " + blockStructureWordContent[w].box.toString());
				if (blockStructureWordContent[w].str.length() == 0)
					continue;
				BoundingBox blockStructureWordContentBox = new BoundingBox((blockStructureWordContent[w].box.left + blockStructureWordBox.left), (blockStructureWordContent[w].box.right + blockStructureWordBox.left), (blockStructureWordContent[w].box.top + blockStructureWordBox.top), (blockStructureWordContent[w].box.bottom + blockStructureWordBox.top));
				blockWordBoxList.add(blockStructureWordContentBox);
				blockWordsByBoxes.put(blockStructureWordContentBox.toString(), blockStructureWordContent[w]);
			}
		}
		
		//	simply take OCR result words and add them to block
		for (int w = 0; w < blockWordBoxList.size(); w++) {
			BoundingBox blockWordBox = ((BoundingBox) blockWordBoxList.get(w));
			OcrWord blockWord = ((OcrWord) blockWordsByBoxes.remove(blockWordBox.toString()));
			if (blockWord == null)
				continue;
			
			int bwbLeft = blockWordBox.left;
			int bwbRight = blockWordBox.right;
			int bwbTop = blockWordBox.top;
			int bwbBottom = blockWordBox.bottom;
			while (bwbLeft < bwbRight) {
				boolean gotNonWhite = false;
				for (int r = bwbTop; r < bwbBottom; r++) {
					if (pageImage.image.getRGB((bwbLeft - pageImage.leftEdge), (r - pageImage.topEdge)) != whiteRgb) {
						gotNonWhite = true;
						break;
					}
				}
				if (gotNonWhite)
					break;
				bwbLeft++;
			}
			while (bwbLeft < bwbRight) {
				boolean gotNonWhite = false;
				for (int r = bwbTop; r < bwbBottom; r++)
					if (pageImage.image.getRGB((bwbRight-1 - pageImage.leftEdge), (r - pageImage.topEdge)) != whiteRgb) {
						gotNonWhite = true;
						break;
					}
				if (gotNonWhite)
					break;
				bwbRight--;
			}
			if (!this.isFlatString(blockWord.str)) {
				while (bwbTop < bwbBottom) {
					boolean gotNonWhite = false;
					for (int c = bwbLeft; c < bwbRight; c++)
						if (pageImage.image.getRGB((c - pageImage.leftEdge), (bwbTop - pageImage.topEdge)) != whiteRgb) {
							gotNonWhite = true;
							break;
						}
					if (gotNonWhite)
						break;
					bwbTop++;
				}
				while (bwbTop < bwbBottom) {
					boolean gotNonWhite = false;
					for (int c = bwbLeft; c < bwbRight; c++)
						if (pageImage.image.getRGB((c - pageImage.leftEdge), (bwbBottom-1 - pageImage.topEdge)) != whiteRgb) {
							gotNonWhite = true;
							break;
						}
					if (gotNonWhite)
						break;
					bwbBottom--;
				}
			}
			if ((bwbLeft != blockWordBox.left) || (bwbRight != blockWordBox.right) || (bwbTop != blockWordBox.top) || (bwbBottom != blockWordBox.bottom))
				blockWordBox = new BoundingBox(bwbLeft, bwbRight, bwbTop, bwbBottom);
			new ImWord(block.getPage(), blockWordBox, blockWord.str);
		}
	}
	
	private boolean isFlatString(String str) {
		for (int c = 0; c < str.length(); c++) {
			if (".,:;°_-~*+'´`\"\u2012\u2013\u2014\u2015\u2212".indexOf(str.charAt(c)) == -1)
				return false;
		}
		return true;
	}
	private static final int whiteRgb = Color.WHITE.getRGB();
	
	private OcrWord[] checkTokenization(OcrWord[] words, BufferedImage blockTextImage, Tokenizer tokenizer) {
		System.out.println(" - checking tokenization splits");
		ArrayList wordList = new ArrayList();
		for (int w = 0; w < words.length; w++) {
			if (words[w].str.trim().length() == 0) {
				wordList.add(words[w]);
				continue;
			}
			TokenSequence wordTokens = tokenizer.tokenize(words[w].str);
			if (wordTokens.size() == 1) {
				wordList.add(words[w]);
				continue;
			}
			System.out.println("   - splitting " + words[w].str + " at " + words[w].box + " into " + wordTokens.size() + " parts");
			
			//	get width for each token at word font size
			String[] splitTokens = new String[wordTokens.size()];
			float[] splitTokenWidths = new float[wordTokens.size()];
			Font wordFont = new Font("Serif", Font.BOLD, 24);
			float splitTokenWidthSum = 0;
			for (int s = 0; s < splitTokens.length; s++) {
				splitTokens[s] = wordTokens.valueAt(s);
				TextLayout tl = new TextLayout(splitTokens[s], wordFont, new FontRenderContext(null, false, true));
				splitTokenWidths[s] = ((float) tl.getBounds().getWidth());
				splitTokenWidthSum += splitTokenWidths[s];
			}
			
			//	store split result, splitting word bounds accordingly
			int wordWidth = (words[w].box.right - words[w].box.left);
			int splitTokenStart = words[w].box.left;
			for (int s = 0; s < splitTokens.length; s++) {
				int splitTokenWidth = Math.round((wordWidth * splitTokenWidths[s]) / splitTokenWidthSum);
				boolean cutLeft = ((s != 0) && (splitTokenWidths[s-1] < splitTokenWidths[s]));
				boolean cutRight = (((s + 1) != splitTokens.length) && (splitTokenWidths[s+1] < splitTokenWidths[s]));
				BoundingBox sWordBox = new BoundingBox(
						(splitTokenStart + (cutLeft ? 1 : 0)),
						Math.min((splitTokenStart + splitTokenWidth - (cutRight ? 1 : 0)), words[w].box.right),
						words[w].box.top,
						words[w].box.bottom
					);
				OcrWord sWord = new OcrWord(wordTokens.valueAt(s), sWordBox);
				wordList.add(sWord);
				System.out.println("     - part " + sWord.str + " at " + sWord.box);
				splitTokenStart += splitTokenWidth;
			}
		}
		if (wordList.size() == words.length)
			return words;
		else return ((OcrWord[]) wordList.toArray(new OcrWord[wordList.size()]));
	}
	
	private OcrWord[] doBlockOcr(int blockNr, BufferedImage blockTextImage, int dpi, ProgressMonitor psm, boolean addPrefix, long timeout, Tokenizer tokenizer) throws IOException {
		
		//	add prefix image
		final int rightShift;
		final int maxRight = blockTextImage.getWidth();
		final int maxBottom = blockTextImage.getHeight();
		if (addPrefix) {
			BufferedImage linePrefixImage = this.getSeparatorImage(blockTextImage.getHeight() - (2 * textImageMargin));
			int linePrefixMargin = (dpi / 30);
			
			BufferedImage pBlockTextImage = new BufferedImage(((linePrefixImage.getWidth() + linePrefixMargin + blockTextImage.getWidth()) + (2 * textImageMargin)), blockTextImage.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
			pBlockTextImage.getGraphics().setColor(Color.WHITE);
			pBlockTextImage.getGraphics().fillRect(0, 0, pBlockTextImage.getWidth(), pBlockTextImage.getHeight());
			
			int rgb;
			for (int c = 0; c < linePrefixImage.getWidth(); c++) {
				for (int r = 0; r < linePrefixImage.getHeight(); r++) try {
					rgb = linePrefixImage.getRGB(c, r);
					pBlockTextImage.setRGB((textImageMargin + c), (textImageMargin + r), rgb);
				}
				catch (Exception e) {
//					System.out.println("   - line text image is " + lineTextImage.getWidth() + " x " + lineTextImage.getHeight());
//					System.out.println("   - line separator image is " + lineSeparatorImage.getWidth() + " x " + lineSeparatorImage.getHeight());
//					System.out.println("   - lsi-x is " + (wordLeft - wordGap - lineSeparatorImage.getWidth() + c));
//					System.out.println("   - lsi-y is " + (lineTextImageMargin + baseline + separatorDownPush - lineSeparatorImage.getHeight() + r));
//					System.out.println("   --> ERROR");
////					throw new RuntimeException(e);
					e.printStackTrace(System.out);
					c = linePrefixImage.getWidth();
				}
			}
			
			int blockLeft = (linePrefixImage.getWidth() + linePrefixMargin + textImageMargin);
			rightShift = blockLeft;
			for (int c = 0; c < blockTextImage.getWidth(); c++)
				for (int r = 0; r < blockTextImage.getHeight(); r++) {
					rgb = blockTextImage.getRGB(c, r);
					pBlockTextImage.setRGB((c + blockLeft), r, rgb);
				}
			
			blockTextImage = pBlockTextImage;
		}
		else rightShift = 0;
		
		//	get OCR engine instance
		PooledOcrInstance poi = this.getOcrInstance(timeout);
		
		//	check timeout
		if (poi == null)
			throw new OcrInstanceUnavailableException("An OCR Instance is not available right now.");
		
		//	do OCR
		OcrWord[] blockWords = poi.doBlockOcr(blockTextImage);
		
		//	make word bounds relative to original page image, and sort out words whose bounds don't make sense
		LinkedList blockWordList = new LinkedList();
		for (int w = 0; w < blockWords.length; w++) {
			int left = (blockWords[w].box.left - rightShift - textImageMargin);
			if (left < 0)
				continue;
			int right = (blockWords[w].box.right - rightShift - textImageMargin);
			if ((right <= left) || (blockTextImage.getWidth() < right))
				continue;
			int top = (blockWords[w].box.top - textImageMargin);
			if (top < 0)
				continue;
			int bottom = (blockWords[w].box.bottom - textImageMargin);
			if ((bottom <= top) || (blockTextImage.getHeight() < bottom))
				continue;
			blockWordList.add(new OcrWord(blockWords[w].str, new BoundingBox(left, Math.min(right, maxRight), top, Math.min(bottom, maxBottom))));
		}
		blockWords = ((OcrWord[]) blockWordList.toArray((blockWords.length == blockWordList.size()) ? blockWords : new OcrWord[blockWordList.size()]));
		blockWordList.clear();
		
		//	catch empty blocks
		if (blockWords.length == 0) {
			
			//	even prefix did not help
			if (addPrefix) {
				psm.setInfo(" --> no text at all in block " + blockNr);
				return blockWords;
			}
			
			//	as this tends to happen in number-only blocks, try again with prefix
			else {
				psm.setInfo(" --> no text in block " + blockNr + ", re-trying with letter prefix");
				return this.doBlockOcr(blockNr, blockTextImage, dpi, psm, true, timeout, tokenizer);
			}
		}
		
		//	cut prefix
		if (addPrefix) {
			if (wordSeparatorString.equals(blockWords[0].str)) {
				OcrWord[] actualBlockWords = new OcrWord[blockWords.length - 1];
				System.arraycopy(blockWords, 1, actualBlockWords, 0, actualBlockWords.length);
				blockWords = actualBlockWords;
			}
			else if (blockWords[0].str.startsWith(wordSeparatorString))
//				blockWords[0] = new OcrWord(blockWords[0].str.substring(wordSeparatorString.length()).trim(), new BoundingBox((blockWords[0].box.left + rightShift), blockWords[0].box.right, blockWords[0].box.top, blockWords[0].box.bottom));
				blockWords[0] = new OcrWord(blockWords[0].str.substring(wordSeparatorString.length()).trim(), blockWords[0].box);
		}
		
		//	do pattern based correction here (O -> 0 in numbers, 1 -> l in letters, etc.)
		for (int w = 0; w < blockWords.length; w++) {
			String wordStr = blockWords[w].str;
			
			/* Do not correct words that tokenize to more than 6 parts. Rationale
			 * for 6 as threshold: space-free dates with one error (like O for 0)
			 * tokenize to 6 parts.
			 */
			if (6 < tokenizer.tokenize(wordStr).size())
				continue;
			
			//	do the corrections
			for (int c = 0; c < this.patternCorrections.size(); c++) {
				PatternCorrection pc = ((PatternCorrection) this.patternCorrections.get(c));
				wordStr = pc.correct(wordStr);
			}
			if (!blockWords[w].str.equals(wordStr)) {
				System.out.println("Pattern correcting " + blockWords[w].str + " to " + wordStr);
				blockWords[w] = new OcrWord(wordStr, blockWords[w].box);
			}
		}
		
		//	TODO also expand word bounds to cover full word
		
		//	TODO consider region coloring to achieve this
		//	TODO or simply expand bounds until they don't intersect any non-white pixels
		
		/* TODO problem are words joint with table grid lines
		 * - do region coloring
		 *   - restricted to word bounds
		 *   - for whole block image
		 * - only even start expanding if char regions
		 *   - do not meet outside word bounds
		 *   - are at least half inside word bounds
		 */
		
		//	sort words by height, descending, to move good line anchors to start of array
		Arrays.sort(blockWords, new Comparator() {
			public int compare(Object obj1, Object obj2) {
				OcrWord w1 = ((OcrWord) obj1);
				OcrWord w2 = ((OcrWord) obj2);
				return ((w2.box.bottom - w2.box.top) - (w1.box.bottom - w1.box.top));
			}
		});
		
		//	keep going until all words are handled
		while (blockWordList.size() < blockWords.length) {
			
			//	start line with first non-null word, collecting all vertically overlapping words
			ArrayList lineWords = new ArrayList();
			int lineWordHeightSum = 0;
			OcrWord lineAnchor = null;
			for (int w = 0; w < blockWords.length; w++) {
				
				//	handled before, in different line
				if (blockWords[w] == null)
					continue;
				
				//	we have a new line anchor
				if (lineAnchor == null) {
					lineAnchor = blockWords[w];
					lineWords.add(blockWords[w]);
					lineWordHeightSum += (blockWords[w].box.bottom - blockWords[w].box.top);
					blockWords[w] = null; // mark as handled
				}
				
				//	this one's inside the current line
				else if ((lineAnchor.box.top < blockWords[w].box.bottom) && (blockWords[w].box.top < lineAnchor.box.bottom)) {
					lineWords.add(blockWords[w]);
					lineWordHeightSum += (blockWords[w].box.bottom - blockWords[w].box.top);
					blockWords[w] = null; // mark as handled
				}
			}
			
			//	compute average word height
			int avgLineWordHeight = (lineWordHeightSum / lineWords.size());
			
			//	sort line words left to right
			Collections.sort(lineWords, new Comparator() {
				public int compare(Object obj1, Object obj2) {
					OcrWord w1 = ((OcrWord) obj1);
					OcrWord w2 = ((OcrWord) obj2);
					return (w1.box.left - w2.box.left);
				}
			});
			
			//	expand word bounding boxes lower than average height
			for (int w = 0; w < lineWords.size(); w++) {
				OcrWord word = ((OcrWord) lineWords.get(w));
				if ((avgLineWordHeight * 2) <= ((word.box.bottom - word.box.top) * 3))
					continue;
				
				int eTop = 0;
				int eBottom = 0;
				int eAnchors = 0;
				if (w > 0) {
					OcrWord pWord = ((OcrWord) lineWords.get(w-1));
					eTop += pWord.box.top;
					eBottom += pWord.box.bottom;
					eAnchors++;
				}
				if ((w+1) < lineWords.size()) {
					OcrWord fWord = ((OcrWord) lineWords.get(w+1));
					eTop += fWord.box.top;
					eBottom += fWord.box.bottom;
					eAnchors++;
				}
				if (eAnchors == 0)
					continue;
				
				BoundingBox eBox = new BoundingBox(word.box.left, word.box.right, (eTop / eAnchors), (eBottom / eAnchors));
				OcrWord eWord = new OcrWord(word.str, eBox);
				lineWords.set(w, eWord);
			}
			
			//	add current line words to whole
			blockWordList.addAll(lineWords);
		}
		blockWords = ((OcrWord[]) blockWordList.toArray((blockWords.length == blockWordList.size()) ? blockWords : new OcrWord[blockWordList.size()]));
		blockWordList.clear();
		
		/* We cannot use this, as top-down-left-right ordering is not a total
		 * order in all cases (e.g. figures that OCR to weird patterns), but as
		 * of Java 1.7, sorting routines require a total order. */
//		//	sort detected words top to bottom and left to right
//		Arrays.sort(blockWords, new Comparator() {
//			public int compare(Object obj1, Object obj2) {
//				OcrWord w1 = ((OcrWord) obj1);
//				OcrWord w2 = ((OcrWord) obj2);
//				
//				//	top down
//				if (w1.box.bottom <= w2.box.top)
//					return -1;
//				if (w2.box.bottom <= w1.box.top)
//					return 1;
//				
//				//	left right
//				if (w1.box.right <= w2.box.left)
//					return -1;
//				if (w2.box.right <= w1.box.left)
//					return 1;
//				
//				//	overlapping top down (can happen in images that OCR to weird patterns)
//				if (w1.box.top < w2.box.top)
//					return -1;
//				if (w2.box.bottom < w1.box.bottom)
//					return 1;
//				
//				//	overlapping left right (can happen in images that OCR to weird patterns)
//				if (w1.box.left < w2.box.left)
//					return -1;
//				if (w2.box.right < w1.box.right)
//					return 1;
//				
//				//	these two are totally the same
//				return 0;
//			}
//		});
//		
//		//	expand bounding boxes of dashes, etc. to full line height
//		for (int sw = 0; sw < blockWords.length; /* flexible increment in loop body */) {
//			
//			//	collect line words and compute average height
//			ArrayList lineWords = new ArrayList();
//			int lineWordHeightSum = 0;
//			OcrWord lastWord = blockWords[sw];
//			for (int w = sw; w < blockWords.length; w++) {
//				if (blockWords[w].box.left < lastWord.box.left)
//					break;
//				if (blockWords[w].box.top >= lastWord.box.bottom)
//					break;
//				lastWord = blockWords[w];
//				lineWords.add(lastWord);
//				lineWordHeightSum += (lastWord.box.bottom - lastWord.box.top);
//			}
//			int avgLineWordHeight = (lineWordHeightSum / lineWords.size());
//			
//			//	expand word bounding boxes lower than average height
//			for (int w = 0; w < lineWords.size(); w++) {
//				OcrWord word = ((OcrWord) lineWords.get(w));
//				if ((avgLineWordHeight * 2) <= ((word.box.bottom - word.box.top) * 3))
//					continue;
//				
//				int eTop = 0;
//				int eBottom = 0;
//				int eAnchors = 0;
//				if (w > 0) {
//					OcrWord pWord = ((OcrWord) lineWords.get(w-1));
//					eTop += pWord.box.top;
//					eBottom += pWord.box.bottom;
//					eAnchors++;
//				}
//				if ((w+1) < lineWords.size()) {
//					OcrWord fWord = ((OcrWord) lineWords.get(w+1));
//					eTop += fWord.box.top;
//					eBottom += fWord.box.bottom;
//					eAnchors++;
//				}
//				if (eAnchors == 0)
//					continue;
//				
//				BoundingBox eBox = new BoundingBox(word.box.left, word.box.right, (eTop / eAnchors), (eBottom / eAnchors));
//				OcrWord eWord = new OcrWord(word.str, eBox);
//				lineWords.set(w, eWord);
//				blockWords[sw + w] = eWord;
//			}
//			
//			//	jump to start of next line
//			sw += lineWords.size();
//		}
		
		//	return words
		return blockWords;
	}
	
	private BufferedImage getOcrImage(BoundingBox box, PageImage pageImage) {
		BufferedImage boxImage = this.getBoxImage(box, pageImage);
		return ((boxImage == null) ? null : this.getOcrImage(boxImage));
	}
	
	private BufferedImage getBoxImage(BoundingBox box, PageImage pageImage) {
		try {
			return pageImage.image.getSubimage((box.left - pageImage.leftEdge), (box.top - pageImage.topEdge), (box.right - box.left), (box.bottom - box.top));
		}
		catch (Exception e) {
			System.out.println("Exception cutting [" + (box.left - pageImage.leftEdge) + "," + (box.right - pageImage.leftEdge) + "] x [" + (box.top - pageImage.topEdge) + "," + (box.bottom - pageImage.topEdge) + "] from " + pageImage.image.getWidth() + " x " + pageImage.image.getHeight() + " image: " + e.getMessage());
			e.printStackTrace(System.out);
		}
		try {
			return pageImage.image.getSubimage((box.left - pageImage.leftEdge), (box.top - pageImage.topEdge), Math.min((box.right - box.left), (pageImage.image.getWidth() - (box.left - pageImage.leftEdge))), Math.min((box.bottom - box.top), (pageImage.image.getHeight() - (box.top - pageImage.topEdge))));
		}
		catch (Exception e) {
			System.out.println("Exception cutting [" + (box.left - pageImage.leftEdge) + "," + (box.right - pageImage.leftEdge) + "] x [" + (box.top - pageImage.topEdge) + "," + (box.bottom - pageImage.topEdge) + "] from " + pageImage.image.getWidth() + " x " + pageImage.image.getHeight() + " image: " + e.getMessage());
			e.printStackTrace(System.out);
		}
		return null;
	}
	
	private BufferedImage getOcrImage(BufferedImage boxImage) {
		BufferedImage ocrImage = new BufferedImage((boxImage.getWidth() + (2 * textImageMargin)), (boxImage.getHeight() + (2 * textImageMargin)), BufferedImage.TYPE_BYTE_GRAY);
		ocrImage.getGraphics().setColor(Color.WHITE);
		ocrImage.getGraphics().fillRect(0, 0, ocrImage.getWidth(), ocrImage.getHeight());
		for (int c = 0; c < boxImage.getWidth(); c++) {
			for (int r = 0; r < boxImage.getHeight(); r++)
				ocrImage.setRGB((c + textImageMargin), (r + textImageMargin), boxImage.getRGB(c, r));
		}
		return ocrImage;
	}
	
	private Set joinPairCharacters = new HashSet();
	private Properties wholeCorrections = new Properties();
	private ArrayList patternCorrections = new ArrayList();
	private static class PatternCorrection {
		private Pattern pattern;
		Properties replacements = new Properties();
		PatternCorrection(String pattern) {
			this.pattern = Pattern.compile(pattern);
		}
		String correct(String str) {
			int cdc = 0;
			for (int c = 0; c < str.length(); c++) {
				if (Character.isLetterOrDigit(str.charAt(c)))
					cdc++;
			}
			if (cdc < 2) // we always need some context
				return str;
//			System.out.println("Matching " + str);
			Matcher m = this.pattern.matcher(str);
			StringBuffer cStr = new StringBuffer();
			int le = 0;
			int lr = 0;
			int llr; // safety for matches that go without replacement
			while (m.find(lr)) {
				llr = lr;
				int s = m.start();
				int e = m.end();
				System.out.println(" - found " + s + " - " + e + " = " + m.group());
				if (le < s)
					cStr.append(str.substring(le, s));
				else if (s < le)
					s = le;
				for (int c = s; c < e;) {
					boolean unchanged = true;
					for (Iterator rcit = this.replacements.keySet().iterator(); rcit.hasNext();) {
						String rStr = ((String) rcit.next());
						if (str.startsWith(rStr, c)) {
							String rs = this.replacements.getProperty(rStr);
							cStr.append(rs);
							c += rStr.length();
							lr = c;
							unchanged = false;
							break;
						}
					}
					if (unchanged) {
						cStr.append(str.charAt(c));
						c++;
					}
				}
				le = e;
				if (lr <= llr)
					lr++;
			}
			cStr.append(str.substring(le));
			return cStr.toString();
		}
	}
	
	/* TODO
==> use whitespace-free leading and tailing wildcards for testing in panels ...
==> ... and highlight actually matching substring in bold
  ==> will require custom match result display ...
  ==> ... but then, what the hell, done worse Swing stuff before ...
	 */
	private static Pattern prefixCatcher = Pattern.compile("\\A" +
			"(Mac|Mc|Della|Delle)" + // TODO extend this list
			"[A-Z]");
	private class PrefixCatchingPatternCorrection extends PatternCorrection {
		PrefixCatchingPatternCorrection(String pattern) {
			super(pattern);
		}
		String correct(String str) {
			Matcher pm = prefixCatcher.matcher(str);
			if (pm.find()) {
				int e = pm.end(1);
				return (str.substring(0, e) + super.correct(str.substring(e)));
			}
			else return super.correct(str);
		}
	}

	private void loadCorrections() {
		PatternCorrection pc;
		
		//	correct ripped-apart 'k' and 'K' characters, e.g. from 'I<' and 'l<'
		pc = new PrefixCatchingPatternCorrection("" +
				"[a-z]+" +
				"([lI1]\\<)" +
				"(" +
					"[^A-Z]" +
					"|" +
					"\\Z" +
				")");
		pc.replacements.setProperty("I<", "k");
		pc.replacements.setProperty("l<", "k");
		this.patternCorrections.add(pc);
		pc = new PatternCorrection("" +
				"[A-Z]" +
				"([Il1]\\<)" +
				"[A-Z]" +
				"");
		pc.replacements.setProperty("I<", "K");
		pc.replacements.setProperty("l<", "K");
		this.patternCorrections.add(pc);
		
		//	correct ripped apart double quotes
		pc = new PatternCorrection("" +
				"(\\,\\,|\\'\\')" +
				"");
		pc.replacements.setProperty(",,", "\"");
		pc.replacements.setProperty("''", "\"");
		this.patternCorrections.add(pc);
		
		//	corrects mis-OCRed '0' and '1' in number blocks (may include degree signs and the like) ==> helps with numbers, coordinates, etc.
		pc = new PatternCorrection("" +
				"(" +
					"([0-9]|\\A)" +
					"[^a-zA-Z\\s\\[]?" +
				")" +
				"(\\]|I|l|O|o|\\(\\))" + // extend options for 1
				"(" +
					"[^a-zA-Z\\s\\[\\@]?" +
					"([0-9]|\\Z)" +
				")" +
				"");
		pc.replacements.setProperty("I", "1");
		pc.replacements.setProperty("l", "1");
		pc.replacements.setProperty("O", "0");
		pc.replacements.setProperty("o", "0");
		pc.replacements.setProperty("()", "0");
		this.patternCorrections.add(pc);
		pc = new PatternCorrection("" +
				"(" +
					"([0-9]|\\A)" +
					"[^a-zA-Z\\s\\[]?" +
				")" +
				"(\\]|I|l|O|o|\\(\\))" + // extend options for 1
				"(" +
					"[^a-zA-Z\\s\\[]?" +
					"[0-9]" +
				")" +
				"");
		pc.replacements.setProperty("I", "1");
		pc.replacements.setProperty("l", "1");
		pc.replacements.setProperty("]", "1");
		pc.replacements.setProperty("O", "0");
		pc.replacements.setProperty("o", "0");
		pc.replacements.setProperty("()", "0");
		this.patternCorrections.add(pc);
		
		//	corrects mis-OCRed 'o' and 'l' in words
		pc = new PrefixCatchingPatternCorrection("" +
				"[a-z]+" +
				"[0I1]+" +
				"(" +
					"[^A-Z]+" +
					"|" +
					"\\Z" +
				")");
		pc.replacements.setProperty("0", "o");
		pc.replacements.setProperty("I", "l");
		pc.replacements.setProperty("1", "l");
		this.patternCorrections.add(pc);
		
		//	corrects mis-OCRed 'O', 'B', and 'I' in words
		pc = new PatternCorrection("" +
				"[A-Z]+" +
				"[081l]+" +
				"[A-Z]+" +
				"");
		pc.replacements.setProperty("0", "O");
		pc.replacements.setProperty("8", "B");
		pc.replacements.setProperty("1", "I");
		pc.replacements.setProperty("l", "I");
		this.patternCorrections.add(pc);
		
		//	corrects lower case letters mistaken for their capital counterparts in lower case blocks
		pc = new PrefixCatchingPatternCorrection("" +
				"[a-z]+" +
				"[CKOSVWXYZ]" +
				"(" +
					"[^A-Z]" +
					"|" +
					"\\Z" +
				")");
		pc.replacements.setProperty("C", "c");
		pc.replacements.setProperty("K", "k");
		pc.replacements.setProperty("O", "o");
		pc.replacements.setProperty("S", "s");
		pc.replacements.setProperty("V", "v");
		pc.replacements.setProperty("W", "w");
		pc.replacements.setProperty("X", "x");
		pc.replacements.setProperty("Y", "y");
		pc.replacements.setProperty("Z", "z");
		this.patternCorrections.add(pc);
		
		//	corrects capital case letters mistaken for their lower case counterparts in capital letter blocks
		pc = new PatternCorrection("" +
				"[A-Z]" +
				"[ckosvwxyz]" +
				"[A-Z]" +
				"");
		pc.replacements.setProperty("c", "C");
		pc.replacements.setProperty("k", "K");
		pc.replacements.setProperty("o", "O");
		pc.replacements.setProperty("s", "S");
		pc.replacements.setProperty("v", "V");
		pc.replacements.setProperty("w", "W");
		pc.replacements.setProperty("x", "X");
		pc.replacements.setProperty("y", "Y");
		pc.replacements.setProperty("z", "Z");
		this.patternCorrections.add(pc);
		
		//	corrects mis-OCRed dots in un-spaced dates
		pc = new PatternCorrection("[0-9]" +
				"\\," +
				"[vViIxX]" +
				"");
		pc.replacements.setProperty(",", ".");
		this.patternCorrections.add(pc);
		
		//	corrects mis-OCRed dots of abbreviations
		pc = new PatternCorrection("\\A[B-HJ-Z][a-z]?\\,\\Z");
		pc.replacements.setProperty(",", ".");
		this.patternCorrections.add(pc);
		
		//	corrects mis-OCRed 0's
		pc = new PatternCorrection("" +
				"[0-9]" +
				"\\(\\)" +
				"[0-9]" +
				"");
		pc.replacements.setProperty("()", "0");
		this.patternCorrections.add(pc);
		
		//	corrects mis-OCRed dashes in number blocks
		pc = new PatternCorrection("" +
				"[0-9]+" +
				"\\~" +
				"[0-9]+" +
				"");
		pc.replacements.setProperty("~", "-");
		this.patternCorrections.add(pc);
		
		//	corrects mis-OCRed dashes in words
		pc = new PatternCorrection("" +
				"[A-Za-z]" +
				"\\~" +
				"[A-Za-z]" +
				"");
		pc.replacements.setProperty("~", "-");
		this.patternCorrections.add(pc);
		
		//	corrects mis-OCRed capital I's
		pc = new PatternCorrection("" +
				"(" +
					"[A-Z]{2,}" +
					"[1l]" +
					"(\\Z|[^a-z])" +
				")" +
				"|" +
				"(" +
					"[A-Z]+" +
					"[1l]" +
					"[A-Z]+" +
				")");
		pc.replacements.setProperty("1", "I");
		pc.replacements.setProperty("l", "I");
		this.patternCorrections.add(pc);
		
		//	TODO load pattern corrections from config file - got to find nice file representation first, though
		//	TODO syntax: "<pattern>": "<match1>==><replacement1> <match2>==><replacement2> ... <matchN>==><replacementN>"
		
		this.wholeCorrections.setProperty("ofthe", "of the");
		this.wholeCorrections.setProperty("ofa", "of a");
		this.wholeCorrections.setProperty("onthe", "on the");
		this.wholeCorrections.setProperty("tothe", "to the");
		
		//	TODO load whole string corrections from config file
		
		this.joinPairCharacters.add("1");
		
		//	TODO load join pair characters from config file
	}
	
	//	TODO make these things configurable
	//	TODO figure out which values make sense
	private static final int textImageMargin = 3;
	
	private static final String wordSeparatorString = "XXX";
	
	private BufferedImage getSeparatorImage(int sepHeight) {
		
		sepHeight = Math.max(1, sepHeight);
		int sepFontSize = 15;
		
		BufferedImage lineSepImage = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY);
		lineSepImage.getGraphics().fillRect(0, 0, lineSepImage.getWidth(), lineSepImage.getHeight());
		Graphics2D graphics = lineSepImage.createGraphics();
		graphics.setFont(new Font("Serif", Font.BOLD, sepFontSize));
		TextLayout tl = new TextLayout(wordSeparatorString, graphics.getFont(), graphics.getFontRenderContext());
		Rectangle2D wordBounds = tl.getBounds();
		if (wordBounds.getHeight() > sepHeight) {
			while (wordBounds.getHeight() > sepHeight) {
				sepFontSize--;
				graphics.setFont(new Font("Serif", Font.BOLD, sepFontSize));
				tl = new TextLayout(wordSeparatorString, graphics.getFont(), graphics.getFontRenderContext());
				wordBounds = tl.getBounds();
			}
		}
		else while (wordBounds.getHeight() <= sepHeight) {
			graphics.setFont(new Font("Serif", Font.BOLD, (sepFontSize+1)));
			tl = new TextLayout(wordSeparatorString, graphics.getFont(), graphics.getFontRenderContext());
			wordBounds = tl.getBounds();
			if (wordBounds.getHeight() <= sepHeight)
				sepFontSize++;
		}
		graphics.setFont(new Font("Serif", Font.BOLD, sepFontSize));
		tl = new TextLayout(wordSeparatorString, graphics.getFont(), graphics.getFontRenderContext());
		wordBounds = tl.getBounds();
		
		lineSepImage = new BufferedImage(((int) Math.round(wordBounds.getWidth() + 1)), sepHeight, BufferedImage.TYPE_BYTE_GRAY);
		graphics = lineSepImage.createGraphics();
		graphics.setColor(Color.WHITE);
		graphics.fillRect(0, 0, lineSepImage.getWidth(), lineSepImage.getHeight());
		
		graphics.setFont(new Font("Serif", Font.BOLD, sepFontSize));
		graphics.setColor(Color.BLACK);
		graphics.drawString(wordSeparatorString, 0, (sepHeight));
		
		return lineSepImage;
	}
	
	public static void main(String[] args) throws Exception {
//		File basePath = new File("E:/Testdaten/PdfExtract/");
//		Object fileIos = ImageIO.createImageOutputStream(new File(basePath, "/testimage.png"));
//		System.out.println("File ==> " + fileIos);
//		Object fileOsIos = ImageIO.createImageOutputStream(new FileOutputStream(new File(basePath, "/testimage.png")));
//		System.out.println("FileOutputStream ==> " + fileOsIos);
//		Object osIos = ImageIO.createImageOutputStream(new OutputStream() {
//			public void write(int b) throws IOException {}
//		});
//		System.out.println("OutputStream ==> " + osIos);
//		Object baosIos = ImageIO.createImageOutputStream(new ByteArrayOutputStream());
//		System.out.println("ByteArrayOutputStream ==> " + baosIos);
//		
//		BufferedImage testImage = PageImage.readImage(new PageImageInputStream(new FileInputStream(new File(basePath, "94_Norris_web_FINAL.pdf.0003.png")), null));
//		final int[] count = {0};
//		final int[] sum = {0};
//		
//		count[0] = 0;
//		sum[0] = 0;
//		System.out.println("TO FILE");
//		ImageIO.write(testImage, "png", new FileImageOutputStream(new File(basePath, "testimage.file.png")) {
//			public void write(int b) throws IOException {
//				this.writeStackTrace();
//				super.write(b);
//				System.out.write(b);
//				count[0]++;
//				sum[0] += b;
//			}
//			public void write(byte[] b, int off, int len) throws IOException {
//				this.writeStackTrace();
//				super.write(b, off, len);
//				System.out.write(b, off, len);
//				count[0] += len;
//				for (int c = 0; c < len; c++)
//					sum[0] += b[off + c];
//			}
//			boolean gotStackTrace = false;
//			private void writeStackTrace() {
//				if (this.gotStackTrace)
//					return;
//				StackTraceElement[] ste = Thread.currentThread().getStackTrace();
//				for (int e = 0; e < ste.length; e++)
//					System.out.println(ste[e]);
//				this.gotStackTrace = true;
//			}
//		});
//		System.out.println("TO FILE count = " + count[0] + " sum = " + sum[0]);
//		
//		count[0] = 0;
//		sum[0] = 0;
//		System.out.println("TO OUTPUT STREAM");
//		ImageIO.write(testImage, "png", new FileCacheImageOutputStream(new FileOutputStream(new File(basePath, "testimage.os.png")), basePath) {
//			public void write(int b) throws IOException {
//				this.writeStackTrace();
//				super.write(b);
//				System.out.write(b);
//				count[0]++;
//				sum[0] += b;
//			}
//			public void write(byte[] b, int off, int len) throws IOException {
//				this.writeStackTrace();
//				super.write(b, off, len);
//				System.out.write(b, off, len);
//				count[0] += len;
//				for (int c = 0; c < len; c++)
//					sum[0] += b[off + c];
//			}
//			boolean gotStackTrace = false;
//			private void writeStackTrace() {
//				if (this.gotStackTrace)
//					return;
//				StackTraceElement[] ste = Thread.currentThread().getStackTrace();
//				for (int e = 0; e < ste.length; e++)
//					System.out.println(ste[e]);
//				this.gotStackTrace = true;
//			}
//		});
//		System.out.println("TO OUTPUT STREAM count = " + count[0] + " sum = " + sum[0]);
		
		OcrEngine oe = new OcrEngine(new File("E:/Testdaten/PdfExtract/"));
		String str;
		str = "20,viii.20()3";
		for (int p = 0; p < oe.patternCorrections.size(); p++) {
			PatternCorrection pc = ((PatternCorrection) oe.patternCorrections.get(p));
			String cStr = pc.correct(str);
			if (!str.equals(cStr)) {
				System.out.println(" ==> " + str + " corrected to " + cStr);
				str = cStr;
			}
		}
		
		str = "AGOST1";
		for (int p = 0; p < oe.patternCorrections.size(); p++) {
			PatternCorrection pc = ((PatternCorrection) oe.patternCorrections.get(p));
			String cStr = pc.correct(str);
			if (!str.equals(cStr)) {
				System.out.println(" ==> " + str + " corrected to " + cStr);
				str = cStr;
			}
		}
		
		str = "Entomol0gY,";
		for (int p = 0; p < oe.patternCorrections.size(); p++) {
			PatternCorrection pc = ((PatternCorrection) oe.patternCorrections.get(p));
			String cStr = pc.correct(str);
			if (!str.equals(cStr)) {
				System.out.println(" ==> " + str + " corrected to " + cStr);
				str = cStr;
			}
		}
		
		str = "M,";
		for (int p = 0; p < oe.patternCorrections.size(); p++) {
			PatternCorrection pc = ((PatternCorrection) oe.patternCorrections.get(p));
			String cStr = pc.correct(str);
			if (!str.equals(cStr)) {
				System.out.println(" ==> " + str + " corrected to " + cStr);
				str = cStr;
			}
		}
		
		str = "3I°48’E).";
		for (int p = 0; p < oe.patternCorrections.size(); p++) {
			PatternCorrection pc = ((PatternCorrection) oe.patternCorrections.get(p));
			String cStr = pc.correct(str);
			if (!str.equals(cStr)) {
				System.out.println(" ==> " + str + " corrected to " + cStr);
				str = cStr;
			}
		}
		
		str= "31°l5’E,";
		for (int p = 0; p < oe.patternCorrections.size(); p++) {
			PatternCorrection pc = ((PatternCorrection) oe.patternCorrections.get(p));
			String cStr = pc.correct(str);
			if (!str.equals(cStr)) {
				System.out.println(" ==> " + str + " corrected to " + cStr);
				str = cStr;
			}
		}
		
		str = "[0.l84];";
		for (int p = 0; p < oe.patternCorrections.size(); p++) {
			PatternCorrection pc = ((PatternCorrection) oe.patternCorrections.get(p));
			String cStr = pc.correct(str);
			if (!str.equals(cStr)) {
				System.out.println(" ==> " + str + " corrected to " + cStr);
				str = cStr;
			}
		}
		
		str = "MacCallum";
		for (int p = 0; p < oe.patternCorrections.size(); p++) {
			PatternCorrection pc = ((PatternCorrection) oe.patternCorrections.get(p));
			String cStr = pc.correct(str);
			if (!str.equals(cStr)) {
				System.out.println(" ==> " + str + " corrected to " + cStr);
				str = cStr;
			}
		}
		
		str = "MocCa";
		for (int p = 0; p < oe.patternCorrections.size(); p++) {
			PatternCorrection pc = ((PatternCorrection) oe.patternCorrections.get(p));
			String cStr = pc.correct(str);
			if (!str.equals(cStr)) {
				System.out.println(" ==> " + str + " corrected to " + cStr);
				str = cStr;
			}
		}
	}
}