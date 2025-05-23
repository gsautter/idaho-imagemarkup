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
 *     * Neither the name of the Universitaet Karlsruhe (TH) nor the
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
package de.uka.ipd.idaho.im.analysis;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.imageio.ImageIO;

import de.uka.ipd.idaho.gamta.util.ProgressMonitor;
import de.uka.ipd.idaho.gamta.util.imaging.BoundingBox;
import de.uka.ipd.idaho.im.pdf.PdfExtractor;
import de.uka.ipd.idaho.im.utilities.ImageDisplayDialog;
import de.uka.ipd.idaho.stringUtils.csvHandler.StringTupel;

/**
 * Function library for image processing.
 * 
 * This class uses the FFT code and complex number representation from
 * http://introcs.cs.princeton.edu/java/97data/. These code artifacts are copied
 * over here only for the lack of a respective JAR library.
 * 
 * @author sautter
 */
public class Imaging {
	
	/* TODO offer computing pixel row and column brightness histograms public methods ... and add
- shearing angle as an argument (defaulting to 0 in shorter signature), which facilitates computing multiple variants without any actual image processing at all
  - either in a single argument (as rotation angle)
  - or in two arguments for horizontal and vertical
- contrast computation based on average plain or square (flag argument) jump size
- methods for computing maximum and minimum of histograms (check if java.util.Arrays already has max() and min() methods for arrays)
- method for getting histogram peaks within some radius (argument) and/or above or below (two arguments) some thresholds
	 */
	
	private static final int analysisImageCacheSize = 128;
	private static Map analysisImageCache = Collections.synchronizedMap(new LinkedHashMap(128, 0.9f, true) {
		protected boolean removeEldestEntry(Entry eldest) {
			return (this.size() > analysisImageCacheSize);
		}
	});
	
	/**
	 * Wrap an image for analysis. If the argument cache key is null, caching is
	 * deactivated.
	 * @param image the image to wrap
	 * @param cacheKey a string ID to use for caching
	 * @return the wrapped image
	 */
	public static AnalysisImage wrapImage(BufferedImage image, String cacheKey) {
		return wrapImage(image, null, null, cacheKey);
	}
	
	/**
	 * Wrap an image for analysis. If the argument cache key is null, caching
	 * is deactivated. The two additional images can both be null; they exist
	 * to allow for bundling individual layers of an image with the overall
	 * image, mainly to support scans whose text is in a separate mask.
	 * @param image the image to wrap
	 * @param backgroundImage the background part of an image originating from
	 *            a pair of background and mask image
	 * @param textImage the text mask part of an image originating from a pair
	 *            of background and mask image
	 * @param cacheKey a string ID to use for caching
	 * @return the wrapped image
	 */
	public static AnalysisImage wrapImage(BufferedImage image, BufferedImage backgroundImage, BufferedImage textImage, String cacheKey) {
		synchronized (analysisImageCache) {
			AnalysisImage ai = ((cacheKey == null) ? null : ((AnalysisImage) analysisImageCache.get(cacheKey)));
			if (ai == null) {
				ai = new AnalysisImage(image, backgroundImage, textImage);
				if (cacheKey != null)
					analysisImageCache.put(cacheKey, ai);
			}
			return ai;
		}
	}
	
	/**
	 * Clean up wrapped image cache, removing all wrapped images whose cache
	 * keys start with the argument prefix.
	 * @param cacheKeyPrefix the prefix of the cache keys to invalidate
	 */
	public static void cleanUpCache(String cacheKeyPrefix) {
		synchronized (analysisImageCache) {
			for (Iterator ckit = analysisImageCache.keySet().iterator(); ckit.hasNext();) {
				String cacheKey = ((String) ckit.next());
				if (cacheKey.startsWith(cacheKeyPrefix))
					ckit.remove();
			}
		}
	}
	
	/**
	 * Wrapper for an image and computed bounds while processing.
	 * 
	 * @author sautter
	 */
	public static class AnalysisImage {
		BufferedImage image;
		BufferedImage backgroundImage;
		BufferedImage textImage;
		private byte[][] brightness;
		private double rotatedBy = 0;
		AnalysisImage(BufferedImage image, BufferedImage backgroundImage, BufferedImage textImage) {
			this(image, backgroundImage, textImage, null);
		}
		AnalysisImage(BufferedImage image, BufferedImage backgroundImage, BufferedImage textImage, byte[][] brightnesses) {
			this.image = image;
			this.backgroundImage = backgroundImage;
			this.textImage = textImage;
			this.brightness = brightnesses;
		}
		public BufferedImage getImage() {
			return this.image;
		}
		void setImage(BufferedImage image, BufferedImage backgroundImage, BufferedImage textImage) {
			this.image = image;
			this.brightness = null;
//			this.fftCache.clear();
		}
		
		/**
		 * Retrieve the angle (in radiants) an image was rotated by during
		 * correction. If the image was never passed to the
		 * <code>correctRotation()</code> method, this method always returns 0.
		 * @return the angle by which the image was rotated during correction
		 */
		public double getRotatedBy() {
			return this.rotatedBy;
		}
		void setRotatedBy(double rotatedBy) {
			this.rotatedBy = rotatedBy;
		}
		
		/**
		 * Retrieve a two-dimensional array holding the brightness values of the
		 * wrapped image, discretized to values between 0-127, inclusive. The
		 * outer dimension is the columns, the inner dimension the rows. The
		 * array must not be modified.
		 * @return an array holding the brightness values of the wrapped image
		 */
		public byte[][] getBrightness() {
			if (this.brightness == null) {
				this.brightness = new byte[this.image.getWidth()][this.image.getHeight()];
				int rgb;
				for (int c = 0; c < this.image.getWidth(); c++) {
					for (int r = 0; r < this.image.getHeight(); r++) {
						rgb = this.image.getRGB(c, r);
						this.brightness[c][r] = getByteBrightness(rgb);
					}
				}
			}
			return this.brightness;
		}
//		
//		/**
//		 * Retrieve the FFT of the wrapped image. Having the image repeated
//		 * computes the FFT of a plain parquetted with the argument image
//		 * instead.
//		 * @param tdim the size of the FFT
//		 * @param repeatImage repeat the image?
//		 * @return the FFT of the wrapped image, sized tdim x tdim
//		 */
//		private Complex[][] getFft(int tdim, boolean repeatImage) {
//			String fftKey = ("" + tdim + "-" + repeatImage);
//			Complex[][] fft = ((Complex[][]) this.fftCache.get(fftKey));
//			if (fft == null) {
//				fft = Imaging.getFft(this.fullImage, tdim, tdim, repeatImage);
//				this.fftCache.put(fftKey, fft);
//			}
//			return fft;
//		}
//		private HashMap fftCache = new HashMap(2);
//		
//		/**
//		 * Retrieve the FFT of the wrapped image. Having the image repeated
//		 * computes the FFT of a plain parquetted with the argument image
//		 * instead.
//		 * @param repeatImage repeat the image?
//		 * @return the FFT of the wrapped image
//		 */
//		private Complex[][] getFft(boolean repeatImage) {
//			int dim = 64;
//			int size = Math.min(256, Math.max(this.fullImage.getWidth(), this.fullImage.getHeight()));
//			while (dim < size)
//				dim *= 2;
//			return this.getFft(dim, repeatImage);
//		}
//		
//		/**
//		 * @return the FFT of the wrapped image
//		 */
//		private Complex[][] getFft() {
//			int dim = 64;
//			int size = Math.min(256, Math.max(this.fullImage.getWidth(), this.fullImage.getHeight()));
//			while (dim < size)
//				dim *= 2;
//			return this.getFft(dim, (this.fullImage.getWidth() < this.fullImage.getHeight()));
//		}
	}
	
	/**
	 * View-based representation of a rectangular sub image of an AnalysisImage.
	 * 
	 * @author sautter
	 */
	public static class ImagePartRectangle {
		final AnalysisImage ai;
		int topRow; // inclusive
		int bottomRow; // exclusive
		int leftCol; // inclusive
		int rightCol; // exclusive
		boolean splitClean = false;
		ImagePartRectangle(AnalysisImage ai) {
			this.ai = ai;
			this.leftCol = 0;
			this.rightCol = this.ai.image.getWidth();
			this.topRow = 0;
			this.bottomRow = this.ai.image.getHeight();
		}
		/**
		 * @return the backing AnalysisImage
		 */
		public AnalysisImage getImage() {
			return this.ai;
		}
		/**
		 * @return the topRow
		 */
		public int getTopRow() {
			return this.topRow;
		}
		/**
		 * @return the bottomRow
		 */
		public int getBottomRow() {
			return this.bottomRow;
		}
		/**
		 * @return the leftCol
		 */
		public int getLeftCol() {
			return this.leftCol;
		}
		/**
		 * @return the rightCol
		 */
		public int getRightCol() {
			return this.rightCol;
		}
		public int getWidth() {
			return (this.rightCol - this.leftCol);
		}
		public int getHeight() {
			return (this.bottomRow - this.topRow);
		}
		public boolean isEmpty() {
			return ((this.getWidth() * this.getHeight()) == 0);
		}
		/**
		 * Create a separate image from the content of this rectangle.
		 * @return the content of the rectangle as a separate image
		 */
		public AnalysisImage toImage() {
			if ((this.leftCol == 0) && (this.topRow == 0) && (this.rightCol == this.ai.image.getWidth()) && (this.bottomRow == this.ai.image.getHeight()))
				return this.ai;
			return new AnalysisImage(
					this.ai.image.getSubimage(this.leftCol, this.topRow, (this.rightCol - this.leftCol), (this.bottomRow - this.topRow)),
					((this.ai.backgroundImage == null) ? null : this.ai.backgroundImage.getSubimage(this.leftCol, this.topRow, (this.rightCol - this.leftCol), (this.bottomRow - this.topRow))),
					((this.ai.textImage == null) ? null : this.ai.textImage.getSubimage(this.leftCol, this.topRow, (this.rightCol - this.leftCol), (this.bottomRow - this.topRow)))
			);
		}
		/**
		 * Create a sub rectangle of this one, referring to the same image.
		 * @param l the left bound of the sub rectangle
		 * @param r the right bound of the sub rectangle
		 * @param t the top bound of the sub rectangle
		 * @param b the bottom bound of the sub rectangle
		 * @return a sub rectangle with the specified boundaries
		 */
		public ImagePartRectangle getSubRectangle(int l, int r, int t, int b) {
			ImagePartRectangle ipr = new ImagePartRectangle(this.ai);
			ipr.leftCol = ((l < 0) ? 0 : l);
			ipr.rightCol = r;
			ipr.topRow = ((t < 0) ? 0 : t);
			ipr.bottomRow = b;
			return ipr;
		}
		public boolean equals(Object obj) {
			if (obj instanceof ImagePartRectangle) {
				ImagePartRectangle ipr = ((ImagePartRectangle) obj);
				if (this.leftCol != ipr.leftCol)
					return false;
				else if (this.rightCol != ipr.rightCol)
					return false;
				else if (this.topRow != ipr.topRow)
					return false;
				else if (this.bottomRow != ipr.bottomRow)
					return false;
				else return true;
			}
			else return false;
		}
		public String getId() {
			return ("[" + this.leftCol + "," + this.rightCol + "," + this.topRow + "," + this.bottomRow + "]");
		}
		public String toString() {
			return this.getId();
		}
	}
	
	//	no need for fully blown RGB -> HSL conversion (described in https://www.rapidtables.com/convert/color/rgb-to-hsl.html), only need luminosity, and that as 0-127
	private static byte getByteBrightness(int rgb) {
		int r = (((rgb >> 16) & 0xFF) / 2); // directly to 0-127 range
		int g = (((rgb >> 8) & 0xFF) / 2); // directly to 0-127 range
		int b = (((rgb >> 0) & 0xFF) / 2); // directly to 0-127 range
		int lMax = Math.max(r, Math.max(g, b)); // compute maximum
		int lMin = Math.min(r, Math.min(g, b)); // compute minimum
		return ((byte) ((lMax + lMin) / 2)); // return average
	}
	
	/** control flag switching on or off check for and inversion of extremely dark page images */
	public static final int INVERT_WHITE_ON_BLACK = 0x0001;
	
	/** control flag switching on or off smoothing of letters and other edges */
	public static final int SMOOTH_LETTERS = 0x0002;
	
	/** control flag switching on or off background elimination */
	public static final int ELIMINATE_BACKGROUND = 0x0004;
	
	/** control flag switching on or off white balance (use strongly recommended) */
	public static final int WHITE_BALANCE = 0x0008;
	
	/** control flag switching on or off removal of dark areas along page edges (e.g. shadows on recessed areas of materials on a scanner surface) */
	public static final int CLEAN_PAGE_EDGES = 0x0010;
	
	/** control flag switching on or off removal of speckles (dark areas too small to even be punctuation marks) */
	public static final int REMOVE_SPECKLES = 0x0020;
	
	/** control flag switching on or off detection and correction of large deviations (>2�) from the upright */
	public static final int CORRECT_ROTATION = 0x0040;
	
	/** control flag switching on or off detection and correction of small deviations (<2�) from the upright (use strongly recommended) */
	public static final int CORRECT_SKEW = 0x0080;
	
	/** control flag switching on or off leveling out brightness gradients in background removal and edge cleanup */
	public static final int LEVEL_GRADIENTS = 0x0100;
	
	/** control flag combination switching on all page image correction steps (useful as shorthand for defaults, and for bit masking) */
	public static final int ALL_OPTIONS = 0x01FF;
	
	/**
	 * Correct a page image. This method aggregates several lower level
	 * corrections for convenience, namely inversion check, white balance,
	 * rotation correction, and cutting off white margins.
	 * @param ai the image to correct
	 * @param dpi the resolution of the image
	 * @param pm a monitor object observing progress
	 * @return the corrected image
	 */
	public static AnalysisImage correctImage(AnalysisImage ai, int dpi, ProgressMonitor pm) {
		return correctImage(ai, dpi, null, ALL_OPTIONS, pm);
	}
	
	/**
	 * Correct a page image. This method aggregates several lower level
	 * corrections for convenience, namely inversion check, white balance,
	 * rotation correction, and cutting off white margins.
	 * @param ai the image to correct
	 * @param dpi the resolution of the image
	 * @param flags flags controlling individual steps of image correction
	 * @param pm a monitor object observing progress
	 * @return the corrected image
	 */
	public static AnalysisImage correctImage(AnalysisImage ai, int dpi, int flags, ProgressMonitor pm) {
		return correctImage(ai, dpi, null, flags, pm);
	}
	
	/**
	 * Correct a page image. This method aggregates several lower level
	 * corrections for convenience, namely inversion check, white balance,
	 * rotation correction, and cutting off white margins. If rotation has to
	 * be corrected and the argument OCR word boundaries are not null, they are
	 * rotated alongside the page image and placed in the array in the order
	 * they come in. The bounding boxes have to be in the same coordinate
	 * system and resolution as the argument page image proper for results to
	 * be meaningful.
	 * @param ai the image to correct
	 * @param dpi the resolution of the image
	 * @param exOcrWordBounds an array holding the bounding boxes of existing
	 *            OCR words
	 * @param pm a monitor object observing progress
	 * @return the corrected image
	 */
	public static AnalysisImage correctImage(AnalysisImage ai, int dpi, BoundingBox[] exOcrWordBounds, ProgressMonitor pm) {
		return correctImage(ai, dpi, exOcrWordBounds, ALL_OPTIONS, pm);
	}
	
	/**
	 * Correct a page image. This method aggregates several lower level
	 * corrections for convenience, namely inversion check, white balance,
	 * rotation correction, and cutting off white margins. If rotation has to
	 * be corrected and the argument OCR word boundaries are not null, they are
	 * rotated alongside the page image and placed in the array in the order
	 * they come in. The bounding boxes have to be in the same coordinate
	 * system and resolution as the argument page image proper for results to
	 * be meaningful.
	 * @param ai the image to correct
	 * @param dpi the resolution of the image
	 * @param exOcrWordBounds an array holding the bounding boxes of existing
	 *            OCR words
	 * @param flags flags controlling individual steps of image correction
	 * @param pm a monitor object observing progress
	 * @return the corrected image
	 */
	public static AnalysisImage correctImage(AnalysisImage ai, int dpi, BoundingBox[] exOcrWordBounds, int flags, ProgressMonitor pm) {
		if (pm == null)
			pm = ProgressMonitor.dummy;
		
		//	show what's happening
		ImageDisplayDialog idd = (DEBUG_CLEANUP ? new ImageDisplayDialog("Page Image Cleanup Steps") : null);
		if (idd != null)
			idd.addImage(copyImage(ai.getImage()), "Original");
		
		//	prepare image for enhancement
		boolean changed;
		
		//	check for white on black
		if ((flags & INVERT_WHITE_ON_BLACK) != 0) {
			changed = false;
			changed = correctWhiteOnBlack(ai, ((byte) 32));
			if (changed) {
				pm.setInfo("   - white-on-black inverted");
				if (idd != null)
					idd.addImage(copyImage(ai.getImage()), "White/Black Inverted");
			}
		}
		
		//	check binary vs. gray scale or color
		boolean isGrayScale = isGrayScale(ai);
		boolean isBlurry = false;
		byte[][] faintingDiffs = null;
		
		//	do the fuzzy stuff only to gray scale images
		if (isGrayScale) {
			
			//	measure blurriness
			int contrast = measureContrast(ai);
			pm.setInfo("   - image found non-binary, contrast is " + contrast);
			if (contrast < 16)
				isBlurry = true;
			
			//	do background elimination only to blurry images, so not to destroy non-blurry images
			if (isBlurry) {
				
				//	smooth out unevenly printed letters
				if ((flags & SMOOTH_LETTERS) != 0) {
					changed = false;
					changed = gaussBlur(ai, 1);
					if (changed) {
						pm.setInfo("   - letters smoothed");
						if (idd != null)
							idd.addImage(copyImage(ai.getImage()), "Letters Smoothed");
					}
				}
				
				//	apply low pass filter
				if ((flags & ELIMINATE_BACKGROUND) != 0) {
					/* TODOnot figure out if this makes sense here, as images might suffer,
					 * maybe better in OCR engine, applied to individual text images, but
					 * then, this also hampers block identification */
					changed = false;
//					changed = eliminateBackground(ai, (dpi / 4), 3, 12);
//					changed = eliminateBackground(ai, dpi);
					faintingDiffs = doEliminateBackground(ai, dpi, ((flags & LEVEL_GRADIENTS) != 0));
					changed = (faintingDiffs != null);
					if (changed) {
						pm.setInfo("   - background elimination done");
						if (idd != null)
							idd.addImage(copyImage(ai.getImage()), "Background Eliminated");
					}
				}
			}
			
			//	apply low pass filter also if contrast somewhat higher
			else if (contrast < 64 /* used to be 32 ... we can be a bit more aggressive now that we have switch flags */) {
				if ((flags & ELIMINATE_BACKGROUND) != 0) {
					/* TODO figure out if this makes sense here, as images might suffer,
					 * maybe better in OCR engine, applied to individual text images, but
					 * then, this also hampers block identification */
					changed = false;
//					changed = eliminateBackground(ai, dpi);
					faintingDiffs = doEliminateBackground(ai, dpi, ((flags & LEVEL_GRADIENTS) != 0));
					changed = (faintingDiffs != null);
					if (changed) {
						pm.setInfo("   - background elimination done");
						if (idd != null)
							idd.addImage(copyImage(ai.getImage()), "Background Eliminated");
					}
				}
			}
			
			//	whiten white
			if ((flags & WHITE_BALANCE) != 0) {
				changed = false;
				changed = whitenWhite(ai);
				if (changed) {
					pm.setInfo("   - white balance done");
					if (idd != null)
						idd.addImage(copyImage(ai.getImage()), "White Balanced");
				}
			}
		}
		
//		//	do feather dusting to get rid of spots in the middle of nowhere
//		if ((flags & REMOVE_SPECKLES) != 0) {
//			changed = false;
//			changed = featherDust(ai, dpi, !isGrayScale, isSharp);
//			if (changed) {
//				pm.setInfo("   - feather dusting done");
//				if (idd != null)
//					idd.addImage(copyImage(ai.getImage()), "Feather Dusted");
//			}
//		}
		//	remove speckles, dark page edges, and faint areas (or any subset of the three)
		if ((flags & (ELIMINATE_BACKGROUND | CLEAN_PAGE_EDGES | REMOVE_SPECKLES)) != 0) {
			int minRetainSize = (((flags & REMOVE_SPECKLES) == 0) ? 1 : (dpi / 100));
			int minSoloRetainSize = (((flags & REMOVE_SPECKLES) == 0) ? 1 : (dpi / 25));
			byte maxRetainExtentPercentage = (((flags & CLEAN_PAGE_EDGES) == 0) ? Byte.MAX_VALUE : ((byte) 70)); // region exceeding 70% of page width or height TODO find out if this threshold makes sense
			byte maxRetainBrightness = (((flags & ELIMINATE_BACKGROUND) == 0) ? Byte.MAX_VALUE : ((byte) 96)); // whole region lighter than 25% gray TODO find out if this threshold makes sense
			changed = false;
			changed = regionColorAndClean(ai, minRetainSize, minSoloRetainSize, maxRetainExtentPercentage, maxRetainBrightness, dpi, !isGrayScale, !isBlurry);
			if (changed) {
				pm.setInfo("   - page edge removal done");
				if (idd != null)
					idd.addImage(copyImage(ai.getImage()), "Page Edges Removed");
			}
		}
		
		//	reverse any fainting effects on pixels that were not cleaned up
		if (faintingDiffs != null) {
			byte[][] brightness = ai.getBrightness();
			for (int c = 0; c < brightness.length; c++)
				for (int r = 0; r < brightness[c].length; r++) {
					if (brightness[c][r] == 127)
						continue; // eliminated
					brightness[c][r] = ((byte) Math.max(0, (brightness[c][r] - faintingDiffs[c][r])));
					ai.image.setRGB(c, r, Color.HSBtoRGB(0, 0, (((float) brightness[c][r]) / 127)));
					if ((ai.backgroundImage != null) && (96 < brightness[c][r]))
						ai.backgroundImage.setRGB(c, r, Color.HSBtoRGB(0, 0, (((float) brightness[c][r]) / 127)));
				}
			pm.setInfo("   - image unfainted");
		}
		
//		//	do fine grained feather dusting to get rid of smaller spots
//		if (dpi >= 200) {
//			changed = false;
//			changed = featherDust(ai, 1, (dpi/100), (dpi/25), dpi, !isGrayScale);
//			if (changed)
//				psm.setInfo("   - fine grained feather dusting done");
//		}
//		
//		//	do coarse feather dusting to get rid of spots in the middle of nowhere
//		changed = false;
//		changed = featherDust(ai, (dpi/25), (dpi/25), (dpi/25), dpi, !isGrayScale);
//		if (changed)
//			psm.setInfo("   - coarse feather dusting done");
		
		//	correct page rotation
		if (((flags & CORRECT_ROTATION) != 0) || (flags & CORRECT_SKEW) != 0) {
			changed = false;
			double granularity = (((flags & CORRECT_ROTATION) == 0) ? -1 : 0.1);
			changed = correctPageRotation(ai, dpi, granularity, ADJUST_MODE_SQUARE_ROOT, exOcrWordBounds);
			if (changed) {
				pm.setInfo("   - page rotation corrected");
				if (idd != null)
					idd.addImage(copyImage(ai.getImage()), "Put Upright");
			}
		}
		
//		//	cut white margins
//		//	LET'S NOT DO THIS - SAVES LITTLE, BUT BLOWS PAGES OUT OF PROPORTION
//		ImagePartRectangle textBounds = getContentBox(ai);
//		ai = textBounds.toImage();
//		psm.setInfo("   - white margins removed, size is " + ai.getImage().getWidth() + " x " + ai.getImage().getHeight());
//		
		if (idd != null) {
			idd.setSize(new Dimension(1000, 800));
			idd.setLocationRelativeTo(null);
			idd.setVisible(true);
		}
		
		//	we're done here
		return ai;
	}
	
	/**
	 * Determine whether an image is grayscale or plain black and white. More
	 * specifically, this method tests if an image consists of two brightness
	 * values (black and white) or more. Colors are reduced to their brightness
	 * in this analysis, so this method will recognize a color image as
	 * grayscale as well.
	 * @param ai the image to check
	 * @return true if the image is grayscale, false otherwise
	 */
	public static boolean isGrayScale(AnalysisImage ai) {
		byte[][] brightness = ai.getBrightness();
		
		int[] brightnessCounts = new int[16];
		Arrays.fill(brightnessCounts, 0);
		for (int c = 0; c < brightness.length; c++) {
			for (int r = 0; r < brightness[c].length; r++) {
				int bci = ((brightness[c][r] * brightnessCounts.length) / 128);
				while (bci >= brightnessCounts.length)
					bci--;
				brightnessCounts[bci]++;
			}
		}
		
		int nonZeroBrightnessCounts = 0;
		for (int b = 0; b < brightnessCounts.length; b++) {
			if (brightnessCounts[b] == 0)
				continue;
			nonZeroBrightnessCounts++;
			if (nonZeroBrightnessCounts > 2)
				return true;
		}
		return (nonZeroBrightnessCounts > 2);
	}
	
	/**
	 * Measure the contrast of an image. This method measures the brightness
	 * difference between neighboring pixels, ignoring 0 differences, bucketizes
	 * them in 128 buckets, and then returns the 5% quartile of the brightness
	 * differences.
	 * @param ai the image to check
	 * @return true if the image is grayscale, false otherwise
	 */
	public static int measureContrast(AnalysisImage ai) {
		byte[][] brightness = ai.getBrightness();
		
		int[] brightnessDiffCounts = new int[128];
		int brightnessDiffCount = 0;
		Arrays.fill(brightnessDiffCounts, 0);
		for (int c = 1; c < (brightness.length-1); c++) {
			for (int r = 1; r < (brightness[c].length-1); r++) {
				int bd = Math.abs((4 * brightness[c][r]) - brightness[c-1][r] - brightness[c+1][r] - brightness[c][r-1] - brightness[c][r+1]);
				bd /= 4;
				if (bd == 0)
					continue;
				int bdi = ((bd * brightnessDiffCounts.length) / 128);
				while (bdi >= brightnessDiffCounts.length)
					bdi--;
				brightnessDiffCounts[bdi]++;
				brightnessDiffCount++;
			}
		}
//		
//		System.out.println("Contrast buckets: ");
//		for (int d = (brightnessDiffCounts.length - 1); d >= 0; d--)
//			System.out.println("  " + d + ": " + brightnessDiffCounts[d]);
		
		int brightnessDiffsCounted = 0;
		for (int d = (brightnessDiffCounts.length - 1); d >= 0; d--) {
			brightnessDiffsCounted += brightnessDiffCounts[d];
			if ((brightnessDiffsCounted * 20) >= brightnessDiffCount)
				return d;
		}
		
		return 0;
	}
	
	private static final int white = Color.WHITE.getRGB();
	
	/**
	 * Check if an AnalysisImage is inverted, and correct it if so. This method first
	 * computes the average brightness of the AnalysisImage, and then inverts the AnalysisImage
	 * if the latter is below the specified threshold.
	 * @param ai the wrapped AnalysisImage
	 * @param threshold the threshold average brightness
	 * @return true if the AnalysisImage was changed, false otherwise
	 */
	public static boolean correctWhiteOnBlack(AnalysisImage ai, byte threshold) {
		byte brightness = computeAverageBrightness(ai);
		if (brightness > threshold)
			return false;
		float[] hsb = null;
		int rgb;
		for (int c = 0; c < ai.image.getWidth(); c++)
			for (int r = 0; r < ai.image.getHeight(); r++) {
				rgb = ai.image.getRGB(c, r);
				ai.brightness[c][r] = ((byte) (127 - ai.brightness[c][r]));
				hsb = Color.RGBtoHSB(((rgb >> 16) & 0xFF), ((rgb >> 8) & 0xFF), ((rgb >> 0) & 0xFF), hsb);
				ai.image.setRGB(c, r, Color.HSBtoRGB(hsb [0], hsb[1], (1 - hsb[2])));
				//	no use applying this to background or text images
			}
		return true;
	}
	
	/**
	 * Apply a Gaussian blur to an image. This is mainly meant to even out small
	 * gaps or brightness differences in letters in gray scale images. If the
	 * argument radius is less than 1, this method does not change the image and
	 * returns false. The radius of the kernel used for computing the blur is
	 * three times the argument radius, to provide a smooth blurring.
	 * @param ai the wrapped image
	 * @param radius the radius of the blur
	 * @return true
	 */
	public static boolean gaussBlur(AnalysisImage ai, int radius) {
		return gaussBlur(ai,  radius, radius, false);
	}
	
	/**
	 * Apply a Gaussian blur to an image. This is mainly meant to even out small
	 * gaps or brightness differences in letters in gray scale images. If the
	 * argument radius is less than 1, this method does not change the image and
	 * returns false. The radius of the kernel used for computing the blur is
	 * three times the argument radius, to provide a smooth blurring.
	 * @param ai the wrapped image
	 * @param hRadius the horizontal radius of the blur
	 * @param vRadius the vertical radius of the blur
	 * @return true
	 */
	public static boolean gaussBlur(AnalysisImage ai, int hRadius, int vRadius) {
		return gaussBlur(ai, hRadius, vRadius, false);
	}
	
	/**
	 * Apply a Gaussian blur to an image. This is mainly meant to even out small
	 * gaps or brightness differences in letters in gray scale images. If the
	 * argument radius is less than 1, this method does not change the image and
	 * returns false. If the <code>sharpEdge</code> argument is set to true,
	 * the radius of the kernel used to compute the blur is exactly the argument
	 * radius; if it is set to false, radius of the kernel is three times the
	 * argument radius, to provide a smooth blurring.
	 * @param ai the wrapped image
	 * @param radius the radius of the blur
	 * @param sharpEdge use a sharply edged blur instead of a smooth one?
	 * @return true
	 */
	public static boolean gaussBlur(AnalysisImage ai, int radius, boolean sharpEdge) {
		return gaussBlur(ai,  radius, radius);
	}
	
	/**
	 * Apply a Gaussian blur to an image. This is mainly meant to even out small
	 * gaps or brightness differences in letters in gray scale images. If the
	 * argument radius is less than 1, this method does not change the image and
	 * returns false. If the <code>sharpEdge</code> argument is set to true,
	 * the radius of the kernel used to compute the blur is exactly the argument
	 * radius; if it is set to false, radius of the kernel is three times the
	 * argument radius, to provide a smooth blurring.
	 * @param ai the wrapped image
	 * @param hRadius the horizontal radius of the blur
	 * @param vRadius the vertical radius of the blur
	 * @param sharpEdge use a sharply edged blur instead of a smooth one?
	 * @return true
	 */
	public static boolean gaussBlur(AnalysisImage ai, int hRadius, int vRadius, boolean sharpEdge) {
		if ((hRadius < 1) && (vRadius < 1))
			return false;
		
		//	get brightness array
		byte[][] brightness = ai.getBrightness();
		
		//	blur array
		if (hRadius == vRadius)
			gaussBlur2D(brightness, hRadius, sharpEdge);
		else gaussBlur(brightness, hRadius, vRadius, sharpEdge);
		
		//	update image
		for (int c = 0; c < brightness.length; c++)
			for (int r = 0; r < brightness[c].length; r++) {
				ai.image.setRGB(c, r, Color.HSBtoRGB(0, 0, (((float) brightness[c][r]) / 127)));
				//	no use applying this to background or text images, not even for line drawings, etc.
			}
		
		//	finally ...
		return true;
	}
	
	private static void gaussBlur2D(byte[][] brightness, int radius, boolean sharpEdge) {
		
		//	compute one dimensional kernel
		int kernelRadius = (radius * (sharpEdge ? 1 : 3));
		double[] kernel = new double[kernelRadius + 1 + kernelRadius];
		double kernelSum = 0;
		for (int k = -kernelRadius; k <= kernelRadius; k++) {
			kernel[k + kernelRadius] = (1 / Math.sqrt(2 * Math.PI * radius * radius)) * Math.pow(Math.E, -(((double) (k * k)) / (2 * radius * radius)));
			kernelSum += kernel[k + kernelRadius];
		}
		for (int k = -kernelRadius; k <= kernelRadius; k++)
			kernel[k + kernelRadius] /= kernelSum;
		
		//	build intermediate brightness array
		float[][] iBrightness = new float[brightness.length][brightness[0].length];
		
		//	apply kernel across rows
		for (int c = 0; c < brightness.length; c++) {
			for (int r = 0; r < brightness[c].length; r++) {
				double brightnessSum = 0;
				for (int k = -kernelRadius; k <= kernelRadius; k++) {
					int l = (c + k);
					if (l < 0)
						l = 0;
					else if (l > (brightness.length-1))
						l = (brightness.length-1);
					brightnessSum += (kernel[k + kernelRadius] * brightness[l][r]);
				}
				iBrightness[c][r] = ((float) brightnessSum);
			}
		}
		
		//	apply kernel down columns
		for (int c = 0; c < iBrightness.length; c++) {
			for (int r = 0; r < iBrightness[c].length; r++) {
				double iBrightnessSum = 0;
				for (int k = -kernelRadius; k <= kernelRadius; k++) {
					int l = (r + k);
					if (l < 0)
						l = 0;
					else if (l > (iBrightness[c].length-1))
						l = (iBrightness[c].length-1);
					iBrightnessSum += (kernel[k + kernelRadius] * iBrightness[c][l]);
				}
				int b = ((int) Math.round(iBrightnessSum));
				if (b < 0)
					b = 0;
				else if (b > 127)
					b = 127;
				brightness[c][r] = ((byte) b);
			}
		}
	}
	
	private static void gaussBlur(byte[][] brightness, int hRadius, int vRadius, boolean sharpEdge) {
		if (hRadius >= 1)
			gaussBlur1D(brightness, hRadius, sharpEdge, true);
		if (vRadius >= 1)
			gaussBlur1D(brightness, vRadius, sharpEdge, false);
	}
	
	private static void gaussBlur1D(byte[][] brightness, int radius, boolean sharpEdge, boolean blurRows) {
		
		//	compute one dimensional kernel
		int kernelRadius = (radius * (sharpEdge ? 1 : 3));
		double[] kernel = new double[kernelRadius + 1 + kernelRadius];
		double kernelSum = 0;
		for (int k = -kernelRadius; k <= kernelRadius; k++) {
			kernel[k + kernelRadius] = (1 / Math.sqrt(2 * Math.PI * radius * radius)) * Math.pow(Math.E, -(((double) (k * k)) / (2 * radius * radius)));
			kernelSum += kernel[k + kernelRadius];
		}
		for (int k = -kernelRadius; k <= kernelRadius; k++)
			kernel[k + kernelRadius] /= kernelSum;
		
		//	build intermediate brightness array
		float[][] iBrightness = new float[brightness.length][brightness[0].length];
		
		//	apply kernel across rows
		if (blurRows)
			for (int c = 0; c < brightness.length; c++) {
				for (int r = 0; r < brightness[c].length; r++) {
					double brightnessSum = 0;
					for (int k = -kernelRadius; k <= kernelRadius; k++) {
						int l = (c + k);
						if (l < 0)
							l = 0;
						else if (l > (brightness.length-1))
							l = (brightness.length-1);
						brightnessSum += (kernel[k + kernelRadius] * brightness[l][r]);
					}
					iBrightness[c][r] = ((float) brightnessSum);
				}
			}
		
		//	apply kernel down columns
		else for (int c = 0; c < iBrightness.length; c++) {
			for (int r = 0; r < iBrightness[c].length; r++) {
				double brightnessSum = 0;
				for (int k = -kernelRadius; k <= kernelRadius; k++) {
					int l = (r + k);
					if (l < 0)
						l = 0;
					else if (l > (iBrightness[c].length-1))
						l = (iBrightness[c].length-1);
					brightnessSum += (kernel[k + kernelRadius] * brightness[c][l]);
				}
				iBrightness[c][r] = ((float) brightnessSum);
			}
		}
		
		//	write result back to image
		for (int c = 0; c < iBrightness.length; c++) {
			for (int r = 0; r < iBrightness[c].length; r++) {
				int b = ((int) Math.round(iBrightness[c][r]));
				if (b < 0)
					b = 0;
				else if (b > 127)
					b = 127;
				brightness[c][r] = ((byte) b);
			}
		}
	}
	
	/**
	 * Eliminate the background of an image. This method first applies a low
	 * pass filter (large radius Gauss blur) to identify the background, then
	 * subtracts it from the foreground. WARNING: This filter may eliminate or
	 * severely damage both color and gray scale images.
	 * @param ai the wrapped image
	 * @param dpi the resolution of the image
	 * @return true
	 */
	public static boolean eliminateBackground(AnalysisImage ai, int dpi) {
		return eliminateBackground(ai, dpi, false);
	}
	
	/**
	 * Eliminate the background of an image. This method first applies a low
	 * pass filter (large radius Gauss blur) to identify the background, then
	 * subtracts it from the foreground. WARNING: This filter may eliminate or
	 * severely damage both color and gray scale images.
	 * @param ai the wrapped image
	 * @param dpi the resolution of the image
	 * @param levelGradients follow and level out brightness gradients
	 * @return true
	 */
	public static boolean eliminateBackground(AnalysisImage ai, int dpi, boolean levelGradients) {
		return (doEliminateBackground(ai, dpi, levelGradients) != null);
	}
	
	private static byte[][] doEliminateBackground(AnalysisImage ai, int dpi, boolean levelGradients) {
		//	TODO use gradient following area coloring !!!
		
		//	get brightness array
		byte[][] brightness = ai.getBrightness();
		
		//	copy and blur brightness array
		byte[][] workingBrightness = new byte[brightness.length][brightness[0].length];
		for (int c = 0; c < brightness.length; c++)
			System.arraycopy(brightness[c], 0, workingBrightness[c], 0, brightness[c].length);
		gaussBlur2D(workingBrightness, (dpi / 10), false);
//		
//		//	scale brightness to use background as white
//		for (int c = 0; c < brightness.length; c++)
//			for (int r = 0; r < brightness[c].length; r++) {
//				if (workingBrightness[c][r] == 0)
//					continue;
//				int b = ((brightness[c][r] * 127) / workingBrightness[c][r]);
//				if (b > 127)
//					b = 127;
//				int faintingDiff = (b - brightness[c][r]);
//				brightness[c][r] = ((byte) b);
//				workingBrightness[c][r] = ((byte) faintingDiff);
//			}
		
		//	TODO use area coloring to level gradients if activated
		if (levelGradients) {
			int[][] areaColors = getAreaColoring(ai, 1, false);
//			BufferedImage rcbi = new BufferedImage(bi.getWidth(), bi.getHeight(), BufferedImage.TYPE_INT_ARGB);
//			for (int c = 0; c < rcbi.getWidth(); c++)
//				for (int r = 0; r < rcbi.getHeight(); r++) {
//					int rgb = Color.HSBtoRGB((((float) (areaColors[c][r] % 360)) / 360), 0.5f, 1.0f);
//					rcbi.setRGB(c, r, rgb);
//				}
//			idd.addImage(rcbi, "Area Colors");
			
			int maxAreaColor = 0;
			for (int c = 0; c < areaColors.length; c++) {
				for (int r = 0; r < areaColors[c].length; r++)
					maxAreaColor = Math.max(maxAreaColor, areaColors[c][r]);
			}
			int[] areaSizes = new int[maxAreaColor+1];
			long[] areaBrightnessSums = new long[maxAreaColor+1];
//			long[] areaBrightnessDiffSums = new long[maxAreaColor+1];
			long[] areaWorkingBrightnessSums = new long[maxAreaColor+1];
			for (int c = 0; c < areaColors.length; c++)
				for (int r = 0; r < areaColors[c].length; r++) {
					areaSizes[areaColors[c][r]]++;
					areaBrightnessSums[areaColors[c][r]] += brightness[c][r];
//					areaBrightnessDiffSums[areaColors[c][r]] += (brightness[c][r] - workingBrightness[c][r]);
					areaWorkingBrightnessSums[areaColors[c][r]] += workingBrightness[c][r];
				}
			byte[] areaBrightnesses = new byte[maxAreaColor+1];
			byte minAreaBrightness = 127;
			byte maxAreaBrightness = 0;
//			byte[] areaBrightnessDiffs = new byte[maxAreaColor+1];
//			byte minAreaBrightnessDiff = 127;
//			byte maxAreaBrightnessDiff = 0;
			byte[] areaWorkingBrightnesses = new byte[maxAreaColor+1];
			byte minAreaWorkingBrightness = 127;
			byte maxAreaWorkingBrightness = 0;
			for (int a = 1; a < areaSizes.length; a++) {
				areaBrightnesses[a] = ((byte) (areaBrightnessSums[a] / areaSizes[a]));
				minAreaBrightness = ((byte) Math.min(minAreaBrightness, areaBrightnesses[a]));
				maxAreaBrightness = ((byte) Math.max(maxAreaBrightness, areaBrightnesses[a]));
//				areaBrightnessDiffs[a] = ((byte) (areaBrightnessDiffSums[a] / areaSizes[a]));
//				minAreaBrightnessDiff = ((byte) Math.min(minAreaBrightnessDiff, areaBrightnessDiffs[a]));
//				maxAreaBrightnessDiff = ((byte) Math.max(maxAreaBrightnessDiff, areaBrightnessDiffs[a]));
				areaWorkingBrightnesses[a] = ((byte) (areaWorkingBrightnessSums[a] / areaSizes[a]));
				minAreaWorkingBrightness = ((byte) Math.min(minAreaWorkingBrightness, areaWorkingBrightnesses[a]));
				maxAreaWorkingBrightness = ((byte) Math.max(maxAreaWorkingBrightness, areaWorkingBrightnesses[a]));
//				System.out.println("Area " + a + " is brightness " + areaBrightnesses[a] + ", diff " + areaBrightnessDiffs[a] + " on size " + areaSizes[a]);
			}
//			System.out.println("Area brightness is in [" + minAreaBrightness + "," + maxAreaBrightness + "], diff in [" + minAreaBrightnessDiff + "," + maxAreaBrightnessDiff + "]");
			
//			BufferedImage rbbi = new BufferedImage(ai.image.getWidth(), ai.image.getHeight(), BufferedImage.TYPE_INT_ARGB);
//			for (int c = 0; c < rbbi.getWidth(); c++)
//				for (int r = 0; r < rbbi.getHeight(); r++) {
//					int rgb = Color.HSBtoRGB(0, 0, (((float) (areaBrightnesses[areaColors[c][r]])) / 127));
//					rbbi.setRGB(c, r, rgb);
//				}
//			idd.addImage(rbbi, "Area Brightness");
//			BufferedImage rbdbi = new BufferedImage(ai.image.getWidth(), ai.image.getHeight(), BufferedImage.TYPE_INT_ARGB);
//			for (int c = 0; c < brightness.length; c++)
//				for (int r = 0; r < brightness[c].length; r++) {
//					byte bd = ((byte) -areaBrightnessDiffs[areaColors[c][r]]);
//					int rgb;
//					if (bd <= 0)
//						rgb = Color.HSBtoRGB((((float) bd) / -maxAreaBrightnessDiff), 0.5f, 1.0f);
//					else rgb = Color.HSBtoRGB(0, 0, (1.0f - (((float) bd) / -minAreaBrightnessDiff)));
////					rbdbi.setRGB(c, r, rgb);
//				}
//			idd.addImage(rbdbi, "Area Brightness Diffs");
			for (int c = 0; c < brightness.length; c++)
				for (int r = 0; r < brightness[c].length; r++) {
					byte wb = areaWorkingBrightnesses[areaColors[c][r]];
					if (wb == 0)
						continue;
					byte pb = areaBrightnesses[areaColors[c][r]];
					int rb = ((pb * 127) / wb);
					if (rb > 127)
						rb = 127;
					int faintingDiff = (rb - brightness[c][r]);
					brightness[c][r] = ((byte) rb);
					workingBrightness[c][r] = ((byte) faintingDiff);
				}
		}
		
		//	otherwise, scale brightness to use background as white
		else for (int c = 0; c < brightness.length; c++)
			for (int r = 0; r < brightness[c].length; r++) {
				if (workingBrightness[c][r] == 0)
					continue;
				int b = ((brightness[c][r] * 127) / workingBrightness[c][r]);
				if (b > 127)
					b = 127;
				int faintingDiff = (b - brightness[c][r]);
				brightness[c][r] = ((byte) b);
				workingBrightness[c][r] = ((byte) faintingDiff);
			}
		
		//	update image
		for (int c = 0; c < brightness.length; c++)
			for (int r = 0; r < brightness[c].length; r++) {
				ai.image.setRGB(c, r, ((brightness[c][r] == 127) ? backgroundEliminated : Color.HSBtoRGB(0, 0, (((float) brightness[c][r]) / 127))));
				if ((ai.backgroundImage != null) && (96 < brightness[c][r]) /* making sure not to transfer text to background image */)
					ai.backgroundImage.setRGB(c, r, ((brightness[c][r] == 127) ? backgroundEliminated : Color.HSBtoRGB(0, 0, (((float) brightness[c][r]) / 127))));
			}
		
		//	return brightness delta
		return workingBrightness;
	}
	
	/**
	 * Apply white balance to an image.
	 * @param ai the wrapped image
	 * @return true
	 */
	public static boolean whitenWhite(AnalysisImage ai) {
		byte avgBrightness = computeAverageBrightness(ai);
		for (int c = 0; c < ai.image.getWidth(); c++)
			for (int r = 0; r < ai.image.getHeight(); r++) {
				if (ai.brightness[c][r] == 127)
					continue;
				if (ai.brightness[c][r] >= avgBrightness) {
					ai.brightness[c][r] = 127;
					ai.image.setRGB(c, r, whiteBalanced);
					if (ai.backgroundImage != null)
						ai.backgroundImage.setRGB(c, r, whiteBalanced);
				}
			}
		return true;
	}
	
	/**
	 * Compute the average brightness of an image.
	 * @param ai the wrapped image
	 * @return the average brightness
	 */
	public static byte computeAverageBrightness(AnalysisImage ai) {
		byte[][] brightness = ai.getBrightness();
		long brightnessSum = 0; 
		for (int c = 0; c < brightness.length; c++) {
			for (int r = 0; r < brightness[c].length; r++)
				brightnessSum += brightness[c][r];
		}
		return ((byte) (brightnessSum / (brightness.length * brightness[0].length)));
	}
	
	/**
	 * Compute the average brightness of a part of an image.
	 * @param rect the image part to compute the brightness for
	 * @return the average brightness
	 */
	public static byte computeAverageBrightness(ImagePartRectangle rect) {
		if ((rect.rightCol <= rect.leftCol) || (rect.bottomRow <= rect.topRow))
			return 0;
		byte[][] brightness = rect.ai.getBrightness();
		long brightnessSum = 0; 
		for (int c = rect.leftCol; c < rect.rightCol; c++) {
			for (int r = rect.topRow; r < rect.bottomRow; r++)
				brightnessSum += brightness[c][r];
		}
		return ((byte) (brightnessSum / ((rect.rightCol - rect.leftCol) * (rect.bottomRow - rect.topRow))));
	}
	
	/**
	 * Compute the brightness distribution of an image.
	 * @param ai the image to compute the brightness for
	 * @param numBuckets the number of buckets to dicretize to
	 * @return the brightness distribution
	 */
	public static int[] getBrightnessDistribution(AnalysisImage ai, int numBuckets) {
		int[] brightnessDist = new int[Math.max(8, Math.min(128, numBuckets))];
		int brightnessBucketWidth = (128 / brightnessDist.length);
		Arrays.fill(brightnessDist, 0);
		byte[][] brightness = ai.getBrightness();
		for (int c = 0; c < brightness.length; c++) {
			for (int r = 0; r < brightness[c].length; r++)
				brightnessDist[brightness[c][r] / brightnessBucketWidth]++;
		}
		return brightnessDist;
	}
	
	/**
	 * Flood fill the brightness array of an analysis image. This helps detect
	 * continuous areas of a specific brightness, without destroying the source
	 * image proper. If the pixel at <code>startCol/startRow</code> has a
	 * brightness value different from <code>toFillValue</code>, this method
	 * does nothing. Likewise, if the point <code>startCol/startRow</code> lies
	 * outside the dimensions of the agument brightness array, this method has
	 * no effect.
	 * @param brightness the brightness array to work on
	 * @param startCol the column to start at
	 * @param startRow the row to start at
	 * @param toFillValue the brightness value to fill
	 * @param fillValue the brightness value to fill with
	 */
//	public static void floodFill(byte[][] brightness, int startCol, int startRow, byte toFillValue, byte fillValue) {
//		ArrayList pointsToFill = new ArrayList() {
//			private HashSet addedPoints = new HashSet();
//			public boolean add(Object ptf) {
//				if (this.addedPoints.add(ptf))
//					return super.add(ptf);
//				else return false;
//			}
//		};
//		pointsToFill.add(new Point(startCol, startRow));
//		while (pointsToFill.size() != 0) {
//			Point ptf = ((Point) pointsToFill.remove(0));
//			if (brightness[ptf.c][ptf.r] != toFillValue)
//				continue;
//			brightness[ptf.c][ptf.r] = fillValue;
//			
//			//	explore left and right, and one row above and below
//			for (int c = (ptf.c - 1); c >= 0; c--) {
//				if (brightness[c][ptf.r] == toFillValue)
//					brightness[c][ptf.r] = fillValue;
//				else break;
//				if ((ptf.r > 0) && (brightness[c][ptf.r - 1] == toFillValue))
//					pointsToFill.add(new Point(c, (ptf.r - 1)));
//				if (((ptf.r + 1) < brightness[c].length) && (brightness[c][ptf.r + 1] == toFillValue))
//					pointsToFill.add(new Point(c, (ptf.r + 1)));
//			}
//			for (int c = (ptf.c + 1); c < brightness.length; c++) {
//				if (brightness[c][ptf.r] == toFillValue)
//					brightness[c][ptf.r] = fillValue;
//				else break;
//				if ((ptf.r > 0) && (brightness[c][ptf.r - 1] == toFillValue))
//					pointsToFill.add(new Point(c, (ptf.r - 1)));
//				if (((ptf.r + 1) < brightness[c].length) && (brightness[c][ptf.r + 1] == toFillValue))
//					pointsToFill.add(new Point(c, (ptf.r + 1)));
//			}
//			
//			//	explore upward and downward, and one row to left and right
//			for (int r = (ptf.r - 1); r >= 0; r--) {
//				if (brightness[ptf.c][r] == toFillValue)
//					brightness[ptf.c][r] = fillValue;
//				else break;
//				if ((ptf.c > 0) && (brightness[ptf.c - 1][r] == toFillValue))
//					pointsToFill.add(new Point((ptf.c - 1), r));
//				if (((ptf.c + 1) < brightness.length) && (brightness[ptf.c + 1][r] == toFillValue))
//					pointsToFill.add(new Point((ptf.c + 1), r));
//			}
//			for (int r = (ptf.r + 1); r < brightness[ptf.c].length; r++) {
//				if (brightness[ptf.c][r] == toFillValue)
//					brightness[ptf.c][r] = fillValue;
//				else break;
//				if ((ptf.c > 0) && (brightness[ptf.c - 1][r] == toFillValue))
//					pointsToFill.add(new Point((ptf.c - 1), r));
//				if (((ptf.c + 1) < brightness.length) && (brightness[ptf.c + 1][r] == toFillValue))
//					pointsToFill.add(new Point((ptf.c + 1), r));
//			}
//		}
//	}
	public static void floodFill(byte[][] brightness, int startCol, int startRow, byte toFillValue, byte fillValue) {
		if (toFillValue == fillValue)
			return; // no need to go through the hassle, wouldn't change anything
		if (brightness[startCol][startRow] != toFillValue)
			return; // not a valid starting point
		brightness[startCol][startRow] = fillValue;
		PointBuffer filledPoints = new PointBuffer();
		filledPoints.add(startCol, startRow);
		for (int p = 0; p < filledPoints.size(); p++) {
			int pc = filledPoints.cAt(p);
			int pr = filledPoints.rAt(p);
			if (fillPoint((pc-1), pr, brightness, toFillValue, fillValue))
				filledPoints.add((pc-1), pr);
			if (fillPoint((pc+1), pr, brightness, toFillValue, fillValue))
				filledPoints.add((pc+1), pr);
			if (fillPoint(pc, (pr-1), brightness, toFillValue, fillValue))
				filledPoints.add(pc, (pr-1));
			if (fillPoint(pc, (pr+1), brightness, toFillValue, fillValue))
				filledPoints.add(pc, (pr+1));
		}
	}
	private static boolean fillPoint(int pc, int pr, byte[][] brightness, byte toFillValue, byte fillValue) {
		if ((pc == -1) || (pr == -1))
			return false;
		if ((pc == brightness.length) || (pr == brightness[pc].length))
			return false;
		if (brightness[pc][pr] != toFillValue)
			return false;
		brightness[pc][pr] = fillValue;
		return true;
	}
	
	/**
	 * Apply feather dusting to an image, i.e., set all non-white blocks below a
	 * given size threshold to white. In particular, this method applies region
	 * identification to the image and then filters non-white regions based on
	 * size, position, and distance to other non-white regions. Depending on
	 * whether or not the image is binary, this method behaves slightly
	 * differently: for binary images, region identification also expands
	 * diagonally, permitted minimum sizes for standalone spots are smaller, and
	 * maximum distances to other non-white sport are larger, this all to
	 * account for gray pixels already set to white during binarization.<br>
	 * This implementation estimates the minSize and minSoloSize parameters of
	 * the six-argument version as (dpi/100) and (dpi/25), respectively.
	 * @param ai the wrapped image
	 * @param dpi the resolution of the image
	 * @param isBinary is the image binary, or gray scale or color?
	 * @param isSharp is the image sharp or blurry?
	 * @return true if the image was modified, false otherwise
	 */
	public static boolean featherDust(AnalysisImage ai, int dpi, boolean isBinary, boolean isSharp) {
		return featherDust(ai, (dpi / 100), (dpi / 25), dpi, isBinary, isSharp);
	}
	
	/**
	 * Apply feather dusting to an image, i.e., set all non-white blocks below a
	 * given size threshold to white. In particular, this method applies region
	 * identification to the image and then filters non-white regions based on
	 * size, position, and distance to other non-white regions. Depending on
	 * whether or not the image is binary, this method behaves slightly
	 * differently: for binary images, region identification also expands
	 * diagonally, permitted minimum sizes for standalone spots are smaller, and
	 * maximum distances to other non-white sport are larger, this all to
	 * account for gray pixels already set to white during binarization.
	 * @param ai the wrapped image
	 * @param minSize the minimum size for non-white spots to be retained
	 * @param minSoloSize the minimum size for non-white spots far apart from
	 *            others to be retained
	 * @param dpi the resolution of the image
	 * @param isBinary is the image binary, or gray scale or color?
	 * @param isSharp is the image sharp or blurry?
	 * @return true if the image was modified, false otherwise
	 */
	public static boolean featherDust(AnalysisImage ai, int minSize, int minSoloSize, int dpi, boolean isBinary, boolean isSharp) {
		return regionColorAndClean(ai, minSize, minSoloSize, ((byte) 101), Byte.MAX_VALUE, dpi, isBinary, isSharp);
		//	TODOne use color coding for coarse cleanup as well
		//	- merge regions closer than chopMargin, measured by bounding box
		//	- re-compute size and bounding box in the process
		//	- set regions to white if they are smaller than a letter ot digit at 6pt and too far away from anything else to be a punctuation mark in a sentence
		//	- make sure not to erase spaced dashes, though
	}
	
	/**
	 * Compute the region coloring of an image, which makes continuous light or
	 * dark regions of an image distinguishable. The result array has the
	 * same dimensions as the argument image. If the brightness threshold value
	 * is positive, this method considers any pixel at least as bright as this
	 * value to be white, i.e., not belonging to any region, but to the area
	 * between regions; if the brightness threshold value is negativ, this
	 * method considers any pixel at most as bright the corresponding absolute
	 * (positive) value as inter-region area. This means this method colors
	 * dark regions for positive thresholds, and light regions for negative
	 * thresholds. In either case, considering diagonally adjacent non-white
	 * (or non-black) pixels as connected is most sensible for binary images.
	 * @param ai the image to analyze
	 * @param brightnessThreshold the white threshold
	 * @param includeDiagonal consider diagonally adjacent pixels connected?
	 * @return the region coloring
	 */
//	public static int[][] getRegionColoring(AnalysisImage ai, byte brightnessThreshold, boolean includeDiagonal) {
//		byte[][] brightness = ai.getBrightness();
//		if (brightness.length == 0)
//			return new int[0][0];
//		int[][] regionColors = new int[brightness.length][brightness[0].length];
//		for (int c = 0; c < regionColors.length; c++)
//			Arrays.fill(regionColors[c], 0);
//		int currentRegionColor = 1;
//		for (int c = 0; c < brightness.length; c++)
//			for (int r = 0; r < brightness[c].length; r++) {
//				if ((0 < brightnessThreshold) && (brightnessThreshold <= brightness[c][r]))
//					continue;
//				if ((brightnessThreshold < 0) && (brightness[c][r] <= -brightnessThreshold))
//					continue;
//				if (regionColors[c][r] != 0)
//					continue;
//				int rs = colorRegion(brightness, regionColors, c, r, currentRegionColor, brightnessThreshold, includeDiagonal);
//				if (DEBUG_REGION_COLORING) System.out.println("Region " + currentRegionColor + " is sized " + rs);
//				currentRegionColor++;
//				//	TODO assemble region size distribution, use it to estimate font size, and use estimate for cleanup thresholds
//			}
//		return regionColors;
//	}
//	private static int colorRegion(byte[][] brightness, int[][] regionColors, int c, int r, int regionColor, byte brightnessThreshold, boolean includeDiagonal) {
//		ArrayList points = new ArrayList() {
//			HashSet distinctContent = new HashSet();
//			public boolean add(Object obj) {
//				return (this.distinctContent.add(obj) ? super.add(obj) : false);
//			}
//		};
//		points.add(new Point(c, r));
//		
//		int regionSize = 0;
//		for (int p = 0; p < points.size(); p++) {
//			Point point = ((Point) points.get(p));
//			if ((point.c == -1) || (point.r == -1))
//				continue;
//			if ((point.c == brightness.length) || (point.r == brightness[point.c].length))
//				continue;
//			if ((0 < brightnessThreshold) && (brightnessThreshold <= brightness[point.c][point.r]))
//				continue;
//			if ((brightnessThreshold < 0) && (brightness[point.c][point.r] <= -brightnessThreshold))
//				continue;
//			if (regionColors[point.c][point.r] != 0)
//				continue;
//			regionColors[point.c][point.r] = regionColor;
//			regionSize++;
//			
//			if (includeDiagonal)
//				points.add(new Point((point.c - 1), (point.r - 1)));
//			points.add(new Point((point.c - 1), point.r));
//			if (includeDiagonal)
//				points.add(new Point((point.c - 1), (point.r + 1)));
//			points.add(new Point(point.c, (point.r - 1)));
//			points.add(new Point(point.c, (point.r + 1)));
//			if (includeDiagonal)
//				points.add(new Point((point.c + 1), (point.r - 1)));
//			points.add(new Point((point.c + 1), point.r));
//			if (includeDiagonal)
//				points.add(new Point((point.c + 1), (point.r + 1)));
//		}
//		return regionSize;
//	}
//	private static class Point {
//		final int c;
//		final int r;
//		Point(int c, int r) {
//			this.c = c;
//			this.r = r;
//		}
//		public boolean equals(Object obj) {
//			return ((obj instanceof Point) && (((Point) obj).c == this.c) && (((Point) obj).r == this.r));
//		}
//		public int hashCode() {
//			return ((this.c << 16) + this.r);
//		}
//		public String toString() {
//			return ("(" + this.c + "/" + this.r + ")");
//		}
//	}
	public static int[][] getRegionColoring(AnalysisImage ai, byte brightnessThreshold, boolean includeDiagonal) {
		byte[][] brightness = ai.getBrightness();
		if (brightness.length == 0)
			return new int[0][0];
		int[][] regionColors = new int[brightness.length][brightness[0].length];
		for (int c = 0; c < regionColors.length; c++)
			Arrays.fill(regionColors[c], 0);
		int currentRegionColor = 1;
		for (int c = 0; c < brightness.length; c++) {
			for (int r = 0; r < brightness[c].length; r++)
				if (addPointToRegion(c, r, brightness, brightnessThreshold, regionColors, currentRegionColor)) {
					int rs = colorRegion(brightness, regionColors, c, r, currentRegionColor, brightnessThreshold, includeDiagonal);
					if (DEBUG_REGION_COLORING) System.out.println("Region " + currentRegionColor + " is sized " + rs);
					currentRegionColor++;
				}
				//	TODO assemble region size distribution, use it to estimate font size, and use estimate for cleanup thresholds
		}
		return regionColors;
	}
	private static int colorRegion(byte[][] brightness, int[][] regionColors, int sc, int sr, int regionColor, byte brightnessThreshold, boolean includeDiagonal) {
		PointBuffer regionPoints = new PointBuffer();
		regionPoints.add(sc, sr);
		for (int p = 0; p < regionPoints.size(); p++) {
			int pc = regionPoints.cAt(p);
			int pr = regionPoints.rAt(p);
			if (addPointToRegion((pc-1), pr, brightness, brightnessThreshold, regionColors, regionColor))
				regionPoints.add((pc-1), pr);
			if (addPointToRegion((pc+1), pr, brightness, brightnessThreshold, regionColors, regionColor))
				regionPoints.add((pc+1), pr);
			if (addPointToRegion(pc, (pr-1), brightness, brightnessThreshold, regionColors, regionColor))
				regionPoints.add(pc, (pr-1));
			if (addPointToRegion(pc, (pr+1), brightness, brightnessThreshold, regionColors, regionColor))
				regionPoints.add(pc, (pr+1));
			if (includeDiagonal) {
				if (addPointToRegion((pc-1), (pr-1), brightness, brightnessThreshold, regionColors, regionColor))
					regionPoints.add((pc-1), (pr-1));
				if (addPointToRegion((pc-1), (pr+1), brightness, brightnessThreshold, regionColors, regionColor))
					regionPoints.add((pc-1), (pr+1));
				if (addPointToRegion((pc+1), (pr-1), brightness, brightnessThreshold, regionColors, regionColor))
					regionPoints.add((pc+1), (pr-1));
				if (addPointToRegion((pc+1), (pr+1), brightness, brightnessThreshold, regionColors, regionColor))
					regionPoints.add((pc+1), (pr+1));
			}
		}
		return regionPoints.size();
	}
	private static boolean addPointToRegion(int pc, int pr, byte[][] brightness, int brightnessThreshold, int[][] regionColors, int regionColor) {
		if ((pc == -1) || (pr == -1))
			return false;
		if ((pc == brightness.length) || (pr == brightness[pc].length))
			return false;
		if ((0 < brightnessThreshold) && (brightnessThreshold <= brightness[pc][pr]))
			return false;
		if ((brightnessThreshold < 0) && (brightness[pc][pr] <= -brightnessThreshold))
			return false;
		if (regionColors[pc][pr] != 0)
			return false;
		regionColors[pc][pr] = regionColor;
		return true;
	}
	
	/**
	 * Compute the area coloring of an image, which makes areas of continuous
	 * image brightness distinguishable. This method is similar to region
	 * coloring, but differs in that it aggregates areas with continuous
	 * brightness, independent of an absolute brightness threshold. The
	 * <code>maxDiff</code> argument controls by how much the brightnesses of
	 * two adjacent pixels may differ for them to still be considered part of
	 * the same area. Areas will continue across such pixel pairs, and two
	 * adjacent points with a larger local brightness difference may still end
	 * up in the same area if there is a transitive connection anywhere. This
	 * means that small increases in the maximum difference can incur vast
	 * differences in the result. It also means that areas follow brightness
	 * gradients, like ones resulting from unevenly illuminated scans.
	 * @param ai the image to analyze
	 * @param maxDiff the maximum brightness difference between adjacent pixels
	 *            for an area to continue across them
	 * @param includeDiagonal consider diagonally adjacent pixels?
	 * @return the area coloring
	 */
	public static int[][] getAreaColoring(AnalysisImage ai, int maxDiff, boolean includeDiagonal) {
		byte[][] brightness = ai.getBrightness();
		if (brightness.length == 0)
			return new int[0][0];
		int[][] areaColors = new int[brightness.length][brightness[0].length];
		for (int c = 0; c < areaColors.length; c++)
			Arrays.fill(areaColors[c], 0);
		int currentAreaColor = 1;
		for (int c = 0; c < brightness.length; c++)
			for (int r = 0; r < brightness[c].length; r++) {
				if (areaColors[c][r] != 0)
					continue;
				int as = colorArea(brightness, areaColors, c, r, currentAreaColor, maxDiff, includeDiagonal);
				if (DEBUG_REGION_COLORING) System.out.println("Area " + currentAreaColor + " is sized " + as);
				currentAreaColor++;
				//	TODO assemble area size distribution, use it to estimate font size, and use estimate for cleanup thresholds
			}
		return areaColors;
	}
	private static int colorArea(byte[][] brightness, int[][] areaColors, int sc, int sr, int areaColor, int maxDiff, boolean includeDiagonal) {
		areaColors[sc][sr] = areaColor;
		
		PointBuffer areaPoints = new PointBuffer();
		areaPoints.add(sc, sr);
		for (int p = 0; p < areaPoints.size(); p++) {
			int pc = areaPoints.cAt(p);
			int pr = areaPoints.rAt(p);
			
			if (addPointToArea((pc-1), pr, brightness[pc][pr], brightness, maxDiff, areaColors, areaColor))
				areaPoints.add((pc-1), pr);
			if (addPointToArea((pc+1), pr, brightness[pc][pr], brightness, maxDiff, areaColors, areaColor))
				areaPoints.add((pc+1), pr);
			if (addPointToArea(pc, (pr-1), brightness[pc][pr], brightness, maxDiff, areaColors, areaColor))
				areaPoints.add(pc, (pr-1));
			if (addPointToArea(pc, (pr+1), brightness[pc][pr], brightness, maxDiff, areaColors, areaColor))
				areaPoints.add(pc, (pr+1));
			if (includeDiagonal) {
				if (addPointToArea((pc-1), (pr-1), brightness[pc][pr], brightness, maxDiff, areaColors, areaColor))
					areaPoints.add((pc-1), (pr-1));
				if (addPointToArea((pc-1), (pr+1), brightness[pc][pr], brightness, maxDiff, areaColors, areaColor))
					areaPoints.add((pc-1), (pr+1));
				if (addPointToArea((pc+1), (pr-1), brightness[pc][pr], brightness, maxDiff, areaColors, areaColor))
					areaPoints.add((pc+1), (pr-1));
				if (addPointToArea((pc+1), (pr+1), brightness[pc][pr], brightness, maxDiff, areaColors, areaColor))
					areaPoints.add((pc+1), (pr+1));
			}
		}
		return areaPoints.size();
	}
	private static boolean addPointToArea(int pc, int pr, byte b, byte[][] brightness, int maxDiff, int[][] areaColors, int areaColor) {
		if ((pc == -1) || (pr == -1))
			return false;
		if ((pc == brightness.length) || (pr == brightness[pc].length))
			return false;
		if (areaColors[pc][pr] != 0)
			return false;
		int bDiff = (brightness[pc][pr] - b);
		if ((-maxDiff <= bDiff) && (bDiff <= maxDiff)) {
			areaColors[pc][pr] = areaColor;
			return true;
		}
		else return false;
	}
	
	private static class PointBuffer {
		private int[] points = new int[16];
		private int size = 0;
		void add(int c, int r) {
			if (this.size == this.points.length)
				this.points = Arrays.copyOf(this.points, (this.points.length * 2));
			this.points[this.size++] = (((c & 0x0000FFFF) << 16) | ((r & 0x0000FFFF) << 0));
//			System.out.println("Adding " + c + "/" + r);
		}
		int size() {
			return this.size;
		}
		int cAt(int index) {
			return ((this.points[index] >>> 16) & 0x0000FFFF);
		}
		int rAt(int index) {
			return ((this.points[index] >>> 0) & 0x0000FFFF);
		}
	}
	private static final boolean DEBUG_REGION_COLORING = false;
	
	private static boolean regionColorAndClean(AnalysisImage ai, int minRetainSize, int minSoloRetainSize, byte maxRetainExtentPercentage, byte maxRetainBrightness, int dpi, boolean isBinary, boolean isSharp) {
		boolean changed = false;
		
		byte[][] brightness = ai.getBrightness();
		if (brightness.length == 0)
			return changed;
		
		int[][] regionCodes = getRegionColoring(ai, ((byte) 127), isBinary);
		int regionCodeCount = 0;
		for (int c = 0; c < regionCodes.length; c++) {
			for (int r = 0; r < regionCodes[c].length; r++)
				regionCodeCount = Math.max(regionCodeCount, regionCodes[c][r]);
		}
		regionCodeCount++; // account for 0
		
		//	measure regions (size, surface, min and max column and row, min brightness)
		int[] regionSizes = new int[regionCodeCount];
		Arrays.fill(regionSizes, 0);
		int[] regionSurfaces = new int[regionCodeCount];
		Arrays.fill(regionSurfaces, 0);
		int[] regionMinCols = new int[regionCodeCount];
		Arrays.fill(regionMinCols, regionCodes.length);
		int[] regionMaxCols = new int[regionCodeCount];
		Arrays.fill(regionMaxCols, 0);
		int[] regionMinRows = new int[regionCodeCount];
		Arrays.fill(regionMinRows, regionCodes[0].length);
		int[] regionMaxRows = new int[regionCodeCount];
		Arrays.fill(regionMaxRows, 0);
		byte[] regionMinBrightness = new byte[regionCodeCount];
		Arrays.fill(regionMinBrightness, ((byte) 127));
		HashMap regionSurfacePointSets = new HashMap();
		HashSet regionCodesInRow = new HashSet();
		for (int c = 0; c < regionCodes.length; c++) {
			regionCodesInRow.clear();
			for (int r = 0; r < regionCodes[c].length; r++) {
				if (regionCodes[c][r] == 0)
					continue;
				regionSizes[regionCodes[c][r]]++;
				Integer regionCode = new Integer(regionCodes[c][r]);
				regionCodesInRow.add(regionCode);
				HashSet regionSurfacePoints = ((HashSet) regionSurfacePointSets.get(regionCode));
				if (regionSurfacePoints == null) {
					regionSurfacePoints = new HashSet();
					regionSurfacePointSets.put(regionCode, regionSurfacePoints);
				}
				if (((c-1) >= 0) && (regionCodes[c-1][r] == 0))
					regionSurfacePoints.add((c-1) + "-" + r);
				if (((c+1) < regionCodes.length) && (regionCodes[c+1][r] == 0))
					regionSurfacePoints.add((c+1) + "-" + r);
				if (((r-1) >= 0) && (regionCodes[c][r-1] == 0))
					regionSurfacePoints.add(c + "-" + (r-1));
				if (((r+1) < regionCodes[c].length) && (regionCodes[c][r+1] == 0))
					regionSurfacePoints.add(c + "-" + (r+1));
				regionMinCols[regionCodes[c][r]] = Math.min(c, regionMinCols[regionCodes[c][r]]);
				regionMaxCols[regionCodes[c][r]] = Math.max(c, regionMaxCols[regionCodes[c][r]]);
				regionMinRows[regionCodes[c][r]] = Math.min(r, regionMinRows[regionCodes[c][r]]);
				regionMaxRows[regionCodes[c][r]] = Math.max(r, regionMaxRows[regionCodes[c][r]]);
				regionMinBrightness[regionCodes[c][r]] = ((byte) Math.min(regionMinBrightness[regionCodes[c][r]], brightness[c][r]));
			}
			for (Iterator rcit = regionSurfacePointSets.keySet().iterator(); rcit.hasNext();) {
				Integer regionCode = ((Integer) rcit.next());
				if (regionCodesInRow.contains(regionCode))
					continue;
				HashSet regionSurfacePoints = ((HashSet) regionSurfacePointSets.get(regionCode));
				regionSurfaces[regionCode.intValue()] = regionSurfacePoints.size();
				regionSurfacePoints.clear();
				rcit.remove();
			}
		}
		for (Iterator rcit = regionSurfacePointSets.keySet().iterator(); rcit.hasNext();) {
			Integer regionCode = ((Integer) rcit.next());
			HashSet regionSurfacePoints = ((HashSet) regionSurfacePointSets.get(regionCode));
			regionSurfaces[regionCode.intValue()] = regionSurfacePoints.size();
			regionSurfacePoints.clear();
			rcit.remove();
		}
		
		//	clean up regions below and above size thresholds
		for (int c = 0; c < regionCodes.length; c++)
			for (int r = 0; r < regionCodes[c].length; r++) {
				if (regionCodes[c][r] <= 0)
					continue;
				
				boolean retain = true;
				int regionCode = regionCodes[c][r];
				if (DEBUG_FEATHERDUST) System.out.println("Assessing region " + regionCode + " (size " + regionSizes[regionCode] + ", surface " + regionSurfaces[regionCode] + ")");
				
				//	too faint to retain
				if (retain && (regionMinBrightness[regionCode] > maxRetainBrightness))
					retain = false;
				if (!retain) {
					for (int cc = regionMinCols[regionCode]; cc <= regionMaxCols[regionCode]; cc++)
						for (int cr = regionMinRows[regionCode]; cr <= regionMaxRows[regionCode]; cr++) {
							if (regionCodes[cc][cr] != regionCode)
								continue;
							ai.brightness[cc][cr] = 127;
							ai.image.setRGB(cc, cr, tooFaint);
							if (ai.backgroundImage != null)
								ai.backgroundImage.setRGB(cc, cr, tooFaint);
							regionCodes[cc][cr] = 0;
							changed = true;
						}
					if (DEBUG_FEATHERDUST) System.out.println(" ==> removed for faintness");
					continue;
				}
				
				//	TODO remove region if min size square is not embeddable
				
				//	compute region size to surface ratio
				int minSurface = (((int) Math.ceil(Math.sqrt(regionSizes[regionCode]))) * 4); // square
				int maxSurface = ((regionSizes[regionCode] * 2) + 2); // straight line
				
				//	TODO refine this estimate, ceil(sqrt) is too coarse for small regions, retains too much
				
				//	too thin in whichever direction
				if ((regionSizes[regionCode] < (minRetainSize * minSoloRetainSize)) && (((minSurface + maxSurface) / 2) <= regionSurfaces[regionCode])) {
					retain = false;
					if (DEBUG_FEATHERDUST) System.out.println(" --> removed as too thin");
				}
				
				//	too narrow, low, or overall small to retain
//				if (retain && ((colorCodeMaxCols[colorCode] - colorCodeMinCols[colorCode] + 1) < minSize))
//					retain = false;
//				if (retain && ((colorCodeMaxRows[colorCode] - colorCodeMinRows[colorCode] + 1) < minSize))
//					retain = false;
//				if (retain && (colorCodeCounts[colorCode] < (isBinaryImage ? ((minSize-1) * (minSize-1)) : (minSize * minSize))))
//					retain = false;
				if (retain && ((regionSizes[regionCode] * (isBinary ? 2 : 1)) < ((minRetainSize * minRetainSize) + (isBinary ? 1 : 0)))) {
					retain = false;
					if (DEBUG_FEATHERDUST) System.out.println(" --> removed for size below " + ((minRetainSize * minRetainSize) / (isBinary ? 2 : 1)));
				}
				
				//	covering at least 70% of page width or height (e.g. A5 scanned A4), likely dark scanning margin (subject to check, though)
//				if (retain && ((regionMaxCols[regionCode] - regionMinCols[regionCode] + 1) > ((brightness.length * 7) / 10))) {
//				if (retain && (((regionMaxCols[regionCode] - regionMinCols[regionCode] + 1) * 100) > (brightness.length * 70))) {
				if (retain && (((regionMaxCols[regionCode] - regionMinCols[regionCode] + 1) * 100) > (brightness.length * maxRetainExtentPercentage))) {
					if (DEBUG_FEATHERDUST) System.out.println(" - page wide");
					
					//	test if region close to page edges (outer 3% of page height)
					int topEdge = 0;
					int bottomEdge = 0;
					for (int lc = regionMinCols[regionCode]; lc <= regionMaxCols[regionCode]; lc++) {
//						if (regionCodes[lc][0] == regionCode)
//							topEdge++;
//						if (regionCodes[lc][brightness[lc].length-1] == regionCode)
//							bottomEdge++;
						for (int lr = 0; lr < (brightness[lc].length / 33); lr++)
							if (regionCodes[lc][lr] == regionCode) {
								topEdge++;
								break;
							}
						for (int lr = ((brightness[lc].length * 32) / 33); lr < brightness[lc].length; lr++)
							if (regionCodes[lc][lr] == regionCode) {
								bottomEdge++;
								break;
							}
					}
					
					//	at page edge
					if (((topEdge + bottomEdge) * 2) > brightness[c].length) {
						retain = false;
						if (DEBUG_FEATHERDUST) System.out.println(" --> removed for page width and edge position");
					}
					
					//	test if at least (dpi/15) wide in most parts, and at least 90% of page width
					else if (((regionMaxCols[regionCode] - regionMinCols[regionCode] + 1) > ((brightness.length * 9) / 10))) {
						int squareArea = getSquareArea(regionCodes, regionMinCols[regionCode], regionMaxCols[regionCode], regionMinRows[regionCode], regionMaxRows[regionCode], regionCode, (dpi / 15), true);
						if (DEBUG_FEATHERDUST) System.out.println(" - got " + squareArea + " square area");
						if ((squareArea * 2) > regionSizes[regionCode]) {
							retain = false;
							if (DEBUG_FEATHERDUST) System.out.println(" --> removed for page width and thickness beyond " + (dpi / 15));
						}
					}
				}
//				if (retain && ((regionMaxRows[regionCode] - regionMinRows[regionCode] + 1) > ((brightness[0].length * 7) / 10))) {
//				if (retain && (((regionMaxRows[regionCode] - regionMinRows[regionCode] + 1) * 100) > (brightness[0].length * 70))) {
				if (retain && (((regionMaxRows[regionCode] - regionMinRows[regionCode] + 1) * 100) > (brightness[0].length * maxRetainExtentPercentage))) {
					if (DEBUG_FEATHERDUST) System.out.println(" - page high");
					
					//	test if region close to page edges (outer 4% of page width)
					int leftEdge = 0;
					int rightEdge = 0;
					for (int lr = regionMinRows[regionCode]; lr <= regionMaxRows[regionCode]; lr++) {
//						if (regionCodes[0][lr] == regionCode)
//							leftEdge++;
//						if (regionCodes[brightness.length-1][lr] == regionCode)
//							rightEdge++;
						for (int lc = 0; lc < (brightness.length / 25); lc++)
							if (regionCodes[lc][lr] == regionCode) {
								leftEdge++;
								break;
							}
						for (int lc = ((brightness.length * 24) / 25); lc < brightness.length; lc++)
							if (regionCodes[lc][lr] == regionCode) {
								rightEdge++;
								break;
							}
					}
					
					//	at page edge
					if (((leftEdge + rightEdge) * 2) > brightness.length) {
						retain = false;
						if (DEBUG_FEATHERDUST) System.out.println(" --> removed for page height and edge position");
					}
					
					//	test if at least (dpi/15) wide in most parts, and at least 90% of page height
					else if (((regionMaxRows[regionCode] - regionMinRows[regionCode] + 1) > ((brightness[0].length * 9) / 10))) {
						int squareArea = getSquareArea(regionCodes, regionMinCols[regionCode], regionMaxCols[regionCode], regionMinRows[regionCode], regionMaxRows[regionCode], regionCode, (dpi / 15), true);
						if (DEBUG_FEATHERDUST) System.out.println(" - got " + squareArea + " square area");
						if ((squareArea * 2) > regionSizes[regionCode]) {
							retain = false;
							if (DEBUG_FEATHERDUST) System.out.println(" --> removed for page height and thickness beyond " + (dpi / 15));
						}
					}
				}
				
				if (retain) {
					for (int cc = regionMinCols[regionCode]; cc <= regionMaxCols[regionCode]; cc++)
						for (int cr = regionMinRows[regionCode]; cr <= regionMaxRows[regionCode]; cr++) {
							if (regionCodes[cc][cr] == regionCode)
								regionCodes[cc][cr] = -regionCode;
						}
					if (DEBUG_FEATHERDUST) System.out.println(" ==> retained");
				}
				else {
					for (int cc = regionMinCols[regionCode]; cc <= regionMaxCols[regionCode]; cc++)
						for (int cr = regionMinRows[regionCode]; cr <= regionMaxRows[regionCode]; cr++) {
							if (regionCodes[cc][cr] != regionCode)
								continue;
							ai.brightness[cc][cr] = 127;
							ai.image.setRGB(cc, cr, tooSmallOrBig);
							if (ai.backgroundImage != null)
								ai.backgroundImage.setRGB(cc, cr, tooSmallOrBig);
							regionCodes[cc][cr] = 0;
							changed = true;
						}
					if (DEBUG_FEATHERDUST) System.out.println(" ==> removed");
				}
			}
		
		//	set regions above solo size limit back to positive
		boolean[] assessed = new boolean[regionCodeCount];
		Arrays.fill(assessed, false);
		for (int c = 0; c < regionCodes.length; c++)
			for (int r = 0; r < regionCodes[c].length; r++) {
				if (regionCodes[c][r] >= 0)
					continue;
				if (assessed[-regionCodes[c][r]])
					continue;
				assessed[-regionCodes[c][r]] = true;
				
				boolean standalone = true;
				int regionCode = -regionCodes[c][r];
				if (DEBUG_FEATHERDUST) System.out.println("Assessing region " + regionCode + " (size " + regionSizes[regionCode] + ")");
				
				//	too narrow, low, or overall small to stand alone
				if (isBinary || isSharp) {
					if (standalone && ((regionMaxCols[regionCode] - regionMinCols[regionCode] + 1) < minSoloRetainSize) && ((regionMaxRows[regionCode] - regionMinRows[regionCode] + 1) < minSoloRetainSize)) {
						standalone = false;
						if (DEBUG_FEATHERDUST) System.out.println(" --> too small for standalone");
					}
				}
				else {
					if (standalone && ((regionMaxCols[regionCode] - regionMinCols[regionCode] + 1) < minSoloRetainSize)) {
						standalone = false;
						if (DEBUG_FEATHERDUST) System.out.println(" --> too small for standalone");
					}
					if (standalone && ((regionMaxRows[regionCode] - regionMinRows[regionCode] + 1) < minSoloRetainSize)) {
						standalone = false;
						if (DEBUG_FEATHERDUST) System.out.println(" --> too small for standalone");
					}
				}
				if (standalone && ((regionSizes[regionCode] * (isBinary ? 2 : 1)) < (minRetainSize * minSoloRetainSize))) {
					standalone = false;
					if (DEBUG_FEATHERDUST) System.out.println(" --> too small for standalone, below " + ((minRetainSize * minSoloRetainSize) / (isBinary ? 2 : 1)));
				}
				
				//	count minSize by minSize squares, and deny standalone if not covering at least 33% of region
				if (standalone && !isBinary && !isSharp) {
					int squareArea = getSquareArea(regionCodes, regionMinCols[regionCode], regionMaxCols[regionCode], regionMinRows[regionCode], regionMaxRows[regionCode], -regionCode, minRetainSize, false);
					if ((squareArea * 3) < regionSizes[regionCode]) {
						standalone = false;
						if (DEBUG_FEATHERDUST) System.out.println(" --> too strewn for standalone");
					}
				}
				
				if (standalone) {
					for (int cc = regionMinCols[regionCode]; cc <= regionMaxCols[regionCode]; cc++)
						for (int cr = regionMinRows[regionCode]; cr <= regionMaxRows[regionCode]; cr++) {
							if (regionCodes[cc][cr] == -regionCode)
								regionCodes[cc][cr] = regionCode;
						}
					if (DEBUG_FEATHERDUST) System.out.println(" --> standalone");
				}
			}
		
		//	(iteratively) test regions that are still negative for proximity to positive regions, and set them to positive if any close
		boolean attachedNew;
		int attachRound = 0;
		do {
			attachedNew = false;
			attachRound++;
			Arrays.fill(assessed, false);
			for (int c = 0; c < regionCodes.length; c++)
				for (int r = 0; r < regionCodes[c].length; r++) {
					if (regionCodes[c][r] >= 0)
						continue;
					if (assessed[-regionCodes[c][r]])
						continue;
					assessed[-regionCodes[c][r]] = true;
					
					boolean attach = false;
					int regionCode = -regionCodes[c][r];
					if (DEBUG_FEATHERDUST) System.out.println("Attaching region " + regionCode + " (size " + regionSizes[regionCode] + ")");
					
					//	determine maximum distance, dependent on region size, as dots and hyphens in small fonts are also closer to adjacent letters
					int maxHorizontalMargin = Math.min(minSoloRetainSize, regionSizes[regionCode]);
					int maxVerticalMargin = Math.min(((isBinary || isSharp) ? minSoloRetainSize : (minSoloRetainSize / 2)), regionSizes[regionCode]);
					
					//	search for standalone or attached regions around current one
					for (int cc = Math.max(0, (regionMinCols[regionCode] - maxHorizontalMargin)); cc <= Math.min((brightness.length-1), (regionMaxCols[regionCode] + maxHorizontalMargin)); cc++) {
						for (int cr = Math.max(0, (regionMinRows[regionCode] - maxVerticalMargin)); cr <= Math.min((brightness[cc].length-1), (regionMaxRows[regionCode] + maxVerticalMargin)); cr++)
							if (regionCodes[cc][cr] > 0) {
								attach = true;
								break;
							}
						if (attach)
							break;
					}
					
					//	attach current region
					if (attach) {
						for (int cc = regionMinCols[regionCode]; cc <= regionMaxCols[regionCode]; cc++)
							for (int cr = regionMinRows[regionCode]; cr <= regionMaxRows[regionCode]; cr++) {
								if (regionCodes[cc][cr] == -regionCode)
									regionCodes[cc][cr] = regionCode;
							}
						if (DEBUG_FEATHERDUST) System.out.println(" --> attached");
						attachedNew = true;
					}
				}
		}
		while (attachedNew && (attachRound < maxAttachRounds));
		if (DEBUG_FEATHERDUST) System.out.println("Attachments done in " + attachRound + " rounds");
		
		//	eliminate remaining negative regions
		for (int c = 0; c < regionCodes.length; c++)
			for (int r = 0; r < regionCodes[c].length; r++) {
				if (regionCodes[c][r] >= 0)
					continue;
				
				int regionCode = -regionCodes[c][r];
				if (DEBUG_FEATHERDUST) System.out.println("Removing region " + regionCode + " (size " + regionSizes[regionCode] + ")");
				for (int cc = regionMinCols[regionCode]; cc <= regionMaxCols[regionCode]; cc++)
					for (int cr = regionMinRows[regionCode]; cr <= regionMaxRows[regionCode]; cr++) {
						if (regionCodes[cc][cr] != -regionCode)
							continue;
						ai.brightness[cc][cr] = 127;
						ai.image.setRGB(cc, cr, tooSmallForStandalone);
						if (ai.backgroundImage != null)
							ai.backgroundImage.setRGB(cc, cr, tooSmallForStandalone);
						regionCodes[cc][cr] = 0;
						changed = true;
					}
			}
		
		return changed;
	}
	private static final boolean DEBUG_FEATHERDUST = false;
	private static final int maxAttachRounds = 3;
	/* working left to right, this should be sufficient for dotted lines, as
	 * well as punctuation marks like colons and semicolons, while preventing
	 * randomly dotted areas from being attached gradually from a few standalone
	 * spots */
	
	private static BufferedImage copyImage(BufferedImage bi) {
		BufferedImage cbi = new BufferedImage(bi.getWidth(), bi.getHeight(), ((bi.getType() == BufferedImage.TYPE_CUSTOM) ? BufferedImage.TYPE_INT_ARGB : bi.getType()));
		Graphics cg = cbi.createGraphics();
		cg.drawImage(bi, 0, 0, null);
		cg.dispose();
		return cbi;
	}
	
	private static final boolean DEBUG_CLEANUP = false;
	private static final int whiteBalanced = (DEBUG_CLEANUP ? Color.CYAN.brighter().getRGB() : white);
	private static final int backgroundEliminated = (DEBUG_CLEANUP ? Color.YELLOW.getRGB() : white);
	private static final int tooFaint = (DEBUG_CLEANUP ? Color.MAGENTA.getRGB() : white);
	private static final int tooSmallOrBig = (DEBUG_CLEANUP ? Color.RED.getRGB() : white);
	private static final int tooSmallForStandalone = (DEBUG_CLEANUP ? Color.GREEN.getRGB(): white);
	
	private static int getSquareArea(int[][] colorCodes, int minCol, int maxCol, int minRow, int maxRow, int colorCode, int size, boolean sample) {
		int squareArea = 0;
		int probeStep = (sample ? Math.min((size / 4), 1) : 1);
		int iColorCode = -colorCode;
		for (int sc = minCol; sc <= (maxCol - size + 1); sc += probeStep)
			for (int sr = minRow; sr <= (maxRow - size + 1); sr += probeStep) {
				
				//	check for match
				boolean match = true;
				for (int c = sc; c < (sc + size); c++) {
					if ((colorCodes[c][sr] != colorCode) && (colorCodes[c][sr] != iColorCode)) {
						match = false;
						break;
					}
					if ((colorCodes[c][sr + size -1] != colorCode) && (colorCodes[c][sr + size -1] != iColorCode)) {
						match = false;
						break;
					}
				}
				for (int r = sr; r < (sr + size); r++) {
					if ((colorCodes[sc][r] != colorCode) && (colorCodes[sc][r] != iColorCode)) {
						match = false;
						break;
					}
					if ((colorCodes[sc + size - 1][r] != colorCode) && (colorCodes[sc][r] != iColorCode)) {
						match = false;
						break;
					}
				}
				
				//	count un-marked pixels
				if (match)
					for (int c = sc; c < (sc + size); c++) {
						for (int r = sr; r < (sr + size); r++)
							if (colorCodes[c][r] == colorCode) {
								colorCodes[c][r] = iColorCode;
								squareArea++;
							}
					}
			}
		
		//	undo inversion
		for (int c = minCol; c <= maxCol; c++)
			for (int r = minRow; r <= maxRow; r++) {
				if (colorCodes[c][r] == iColorCode)
					colorCodes[c][r] = colorCode;
			}
		
		//	finally ...
		return squareArea;
	}
	
//	found contrast increase not improving OCR result, rather the contrary !!!
//	
//	int blackBrightness = 0;
//	int counted = colorCodeBrightness[colorCode][blackBrightness];
//	while (counted < (colorCodeCounts[colorCode] / 50)) {
//		blackBrightness++;
//		counted += colorCodeBrightness[colorCode][blackBrightness];
//	}
//	
//	if (blackBrightness == 0) {
//		for (int cc = colorCodeMinCols[colorCode]; cc <= colorCodeMaxCols[colorCode]; cc++)
//			for (int cr = colorCodeMinRows[colorCode]; cr <= colorCodeMaxRows[colorCode]; cr++) {
//				if (colorCodes[cc][cr] == colorCode)
//					colorCodes[cc][cr] = 0;
//			}
//	}
//	
//	else {
//		System.out.println("Found black in region " + colorCode + " at " + blackBrightness);
//		float[] hsb = null;
//		int rgb;
//		for (int cc = colorCodeMinCols[colorCode]; cc <= colorCodeMaxCols[colorCode]; cc++)
//			for (int cr = colorCodeMinRows[colorCode]; cr <= colorCodeMaxRows[colorCode]; cr++) {
//				if (colorCodes[cc][cr] != colorCode)
//					continue;
//				if (ai.brightness[cc][cr] <= blackBrightness)
//					ai.brightness[cc][cr] = 0;
//				else ai.brightness[cc][cr] = ((byte) ((ai.brightness[cc][cr] * (127 - blackBrightness)) / 127));
//				rgb = ai.image.getRGB(cc, cr);
//				hsb = new Color(rgb).getColorComponents(hsb);
//				hsb[2] = (((float) ai.brightness[cc][cr]) / 127);
//				ai.image.setRGB(cc, cr, Color.HSBtoRGB(0, 0, hsb[2]));
//				colorCodes[cc][cr] = 0;
//			}
//		changed = true;
//	}
//	we cannot use recursion, even though it's more elegant,  because of stack overflows in practice (images as large as 3000 by 4000 pixels vs. at most 1024 stack frames) !!!
//	private static void extend(byte[][] brightness, int[][] colorCodes, int c, int r, int colorCode, int[] regionSize) {
//		if ((c == brightness.length) || (r == brightness[c].length))
//			return;
//		if (brightness[c][r] == 127)
//			return;
//		if (colorCodes[c][r] != 0)
//			return;
//		colorCodes[c][r] = colorCode;
//		regionSize[0]++;
//		if (c != 0)
//			extend(brightness, colorCodes, (c-1), r, colorCode, regionSize);
//		extend(brightness, colorCodes, (c+1), r, colorCode, regionSize);
//		if (r != 0)
//			extend(brightness, colorCodes, c, (r-1), colorCode, regionSize);
//		extend(brightness, colorCodes, c, (r+1), colorCode, regionSize);
//	}
	
	/**
	 * Obtain a rectangle encompassing the content of an image, i.e., the whole
	 * image except for any white margins.
	 * @param ai the image to base the rectangle on
	 * @return a rectangle encompassing the content of the image
	 */
	public static ImagePartRectangle getContentBox(AnalysisImage ai) {
		ImagePartRectangle rect = new ImagePartRectangle(ai);
		rect = narrowLeftAndRight(rect);
		rect = narrowTopAndBottom(rect);
		return rect;
	}
	
	/**
	 * Produce a new image part rectangle covering several existing ones.
	 * @param parts the image part rectangles to cover
	 * @returna an image part rectangle covering the argument ones
	 */
	public static ImagePartRectangle getHull(ImagePartRectangle[] parts) {
		if (parts.length == 0)
			return null;
		else if (parts.length == 1)
			return parts[0];
		int left = Integer.MAX_VALUE;
		int right = 0;
		int top = Integer.MAX_VALUE;
		int bottom = 0;
		for (int p = 0; p < parts.length; p++) {
			left = Math.min(left, parts[p].leftCol);
			right = Math.max(right, parts[p].rightCol);
			top = Math.min(top, parts[p].topRow);
			bottom = Math.max(bottom, parts[p].bottomRow);
		}
		ImagePartRectangle hull = new ImagePartRectangle(parts[0].ai);
		hull.leftCol = left;
		hull.rightCol = right;
		hull.topRow = top;
		hull.bottomRow = bottom;
		return hull;
	}
	
	/**
	 * Compute the area of an image part rectangle, i.e., the number of pixels it spans.
	 * @param ipr the image part rectangle whose area to compute
	 * @return the area of the argument image part rectangle
	 */
	public static int getArea(ImagePartRectangle ipr) {
		return ((ipr.rightCol - ipr.leftCol) * (ipr.bottomRow - ipr.topRow));
	}
	
	/**
	 * Copy the boundaries of one image part rectangle into another one.
	 * @param source the image part rectangle to copy the bounds from
	 * @param target the image part rectangle to copy the bounds to
	 */
	public static void copyBounds(ImagePartRectangle source, ImagePartRectangle target) {
		target.leftCol = source.leftCol;
		target.rightCol = source.rightCol;
		target.topRow = source.topRow;
		target.bottomRow = source.bottomRow;
	}
	
	/**
	 * Test if two image part rectangles are side by side, i.e., overlap in their
	 * vertical extent, i.e., there is at least one row of pixels that both
	 * intersect.
	 * @param ipr1 the first image part rectangle to check
	 * @param ipr2 the second image part rectangle to check
	 * @return true if the argument image part rectangle are side by side
	 */
	public static boolean isSideBySide(ImagePartRectangle ipr1, ImagePartRectangle ipr2) {
		return ((ipr1.topRow < ipr2.bottomRow) && (ipr2.topRow < ipr1.bottomRow));
	}
	
	/**
	 * Test if two image part rectangles are above one another, i.e., overlap in their
	 * horizontal extent, i.e., there is at least one column of pixels that both
	 * intersect.
	 * @param ipr1 the first image part rectangle to check
	 * @param ipr2 the second image part rectangle to check
	 * @return true if the argument image part rectangle are above one another
	 */
	public static boolean isAboveOneAnother(ImagePartRectangle ipr1, ImagePartRectangle ipr2) {
		return ((ipr1.leftCol < ipr2.rightCol) && (ipr2.leftCol < ipr1.rightCol));
	}
	
	/**
	 * Test if two image part rectangles overlap, i.e., there is at least one
	 * pixels that both include.
	 * @param ipr1 the first image part rectangle to check
	 * @param ipr2 the second image part rectangle to check
	 * @return true if the argument image part rectangle overlap
	 */
	public static boolean isOverlapping(ImagePartRectangle ipr1, ImagePartRectangle ipr2) {
		return (isSideBySide(ipr1, ipr2) && isAboveOneAnother(ipr1, ipr2));
	}
	
	/**
	 * Compute the fractional horizontal overlap of two image part rectangles. If
	 * all columns of pixels spanned by one of the argument image part rectangles
	 * is also spanned by the other, this method returns 1 even if the other
	 * image part rectangle is wider.
	 * @param ipr1 the first image part rectangle to check
	 * @param ipr2 the second image part rectangle to check
	 * @return the fractional horizontal overlap of the argument image part
	 *         rectangles
	 */
	public static double getHorizontalOverlap(ImagePartRectangle ipr1, ImagePartRectangle ipr2) {
		//	no overlap at all
		if (!isAboveOneAnother(ipr1, ipr2))
			return 0;
		
		//	one completely above or below the other
		if ((ipr1.leftCol <= ipr2.leftCol) && (ipr2.rightCol <= ipr1.rightCol))
			return 1;
		else if ((ipr2.leftCol <= ipr1.leftCol) && (ipr1.rightCol <= ipr2.rightCol))
			return 1;
		
		//	partially overlapping
		double overlap = ((ipr1.leftCol < ipr2.leftCol) ? (ipr1.rightCol - ipr2.leftCol) : (ipr2.rightCol - ipr1.leftCol));
		
		//	compute fraction (overlap divided by average width)
		return ((overlap * 2) / ((ipr1.rightCol - ipr1.leftCol) + (ipr2.rightCol - ipr2.leftCol)));
	}
	
	/**
	 * Compute the fractional vertical overlap of two image part rectangles. If
	 * all rows of pixels spanned by one of the argument image part rectangles
	 * is also spanned by the other, this method returns 1 even if the other
	 * image part rectangle is higher.
	 * @param ipr1 the first image part rectangle to check
	 * @param ipr2 the second image part rectangle to check
	 * @return the fractional vertical overlap of the argument image part
	 *         rectangles
	 */
	public static double getVerticalOverlap(ImagePartRectangle ipr1, ImagePartRectangle ipr2) {
		//	no overlap at all
		if (!isSideBySide(ipr1, ipr2))
			return 0;
		
		//	one completely above or below the other
		if ((ipr1.topRow <= ipr2.topRow) && (ipr2.bottomRow <= ipr1.bottomRow))
			return 1;
		else if ((ipr2.topRow <= ipr1.topRow) && (ipr1.bottomRow <= ipr2.bottomRow))
			return 1;
		
		//	partially overlapping
		double overlap = ((ipr1.topRow < ipr2.topRow) ? (ipr1.bottomRow - ipr2.topRow) : (ipr2.bottomRow - ipr1.topRow));
		
		//	compute fraction (overlap divided by average width)
		return ((overlap * 2) / ((ipr1.bottomRow - ipr1.topRow) + (ipr2.bottomRow - ipr2.topRow)));
	}
	
	/**
	 * Remove any white margines from the left and right edges of a rectangle.
	 * @param rect the rectangle to crop
	 * @return a rectangle with the left and right margins removed
	 */
	public static ImagePartRectangle narrowLeftAndRight(ImagePartRectangle rect) {
		if ((rect.bottomRow <= rect.topRow) || (rect.rightCol <= rect.leftCol))
			return rect;
		
		byte[][] brightness = rect.ai.getBrightness();
		byte[] colBrightnesses = new byte[rect.rightCol - rect.leftCol];
		for (int c = rect.leftCol; c < rect.rightCol; c++) {
			int brightnessSum = 0;
			for (int r = rect.topRow; r < rect.bottomRow; r++)
				brightnessSum += brightness[c][r];
			colBrightnesses[c - rect.leftCol] = ((byte) (brightnessSum / (rect.bottomRow - rect.topRow)));
		}
		
		byte colBrightnessPivot = 127;//getPivot(colBrightnesses, offset);
		int minCol = -1;
		int maxCol = rect.rightCol;
		for (int c = rect.leftCol; c < rect.rightCol; c++) {
			if (colBrightnesses[c - rect.leftCol] < colBrightnessPivot) {
				if (minCol == -1)
					minCol = c;
				maxCol = c;
			}
		}
		
		if ((minCol <= rect.leftCol) && ((maxCol+1) >= rect.rightCol))
			return rect;
		
		ImagePartRectangle res = new ImagePartRectangle(rect.ai);
		res.topRow = rect.topRow;
		res.bottomRow = rect.bottomRow;
		res.leftCol = ((minCol < 0) ? 0 : minCol);
		res.rightCol = maxCol+1;
		return res;
	}
	
	/**
	 * Remove any white margines from the top and bottom edges of a rectangle.
	 * @param rect the rectangle to crop
	 * @return a rectangle with the top and bottom margins removed
	 */
	public static ImagePartRectangle narrowTopAndBottom(ImagePartRectangle rect) {
		if ((rect.bottomRow <= rect.topRow) || (rect.rightCol <= rect.leftCol))
			return rect;
		
		byte[][] brightness = rect.ai.getBrightness();
		byte[] rowBrightnesses = new byte[rect.bottomRow - rect.topRow];
		for (int r = rect.topRow; r < rect.bottomRow; r++) {
			int brightnessSum = 0; 
			for (int c = rect.leftCol; c < rect.rightCol; c++)
				brightnessSum += brightness[c][r];
			rowBrightnesses[r - rect.topRow] = ((byte) (brightnessSum / (rect.rightCol - rect.leftCol)));
		}
		
		byte rowBrightnessPivot = 127;//getPivot(rowBrightnesses, offset);
		
		int minRow = -1;
		int maxRow = rect.bottomRow;
		for (int r = rect.topRow; r < rect.bottomRow; r++) {
			if ((rowBrightnessPivot == 127) ? (rowBrightnesses[r - rect.topRow] < rowBrightnessPivot) : (rowBrightnesses[r - rect.topRow] <= rowBrightnessPivot)) {
				if (minRow == -1)
					minRow = r;
				maxRow = r;
			}
		}
		
		if ((minRow <= rect.topRow) && ((maxRow+1) >= rect.bottomRow))
			return rect;
		
		ImagePartRectangle res = new ImagePartRectangle(rect.ai);
		res.leftCol = rect.leftCol;
		res.rightCol = rect.rightCol;
		res.topRow = ((minRow < 0) ? 0 : minRow);
		res.bottomRow = maxRow+1;
		return res;
	}
	
	/**
	 * Split a rectangular area of an image into columns at embedded vertical
	 * white stripes.
	 * @param rect the rectangle to split
	 * @return an array holding the columns
	 */
	public static ImagePartRectangle[] splitIntoColumns(ImagePartRectangle rect) {
		return splitIntoColumns(rect, 1, 0, true);
	}
	
	/**
	 * Split a rectangular area of an image into columns at embedded vertical
	 * white stripes wider than a given threshold.
	 * @param rect the rectangle to split
	 * @param minSplitMargin the minimum width for splitting
	 * @return an array holding the columns
	 */
	public static ImagePartRectangle[] splitIntoColumns(ImagePartRectangle rect, int minSplitMargin) {
		return splitIntoColumns(rect, minSplitMargin, 0, true);
	}
	
	/**
	 * Split a rectangular area of an image into columns at embedded vertical
	 * white stripes.
	 * @param rect the rectangle to split
	 * @param shearDegrees the deviation of the split lines from the vertical
	 * @param requireVerticalSplit require a white vertical pass in sheared
	 *            splits?
	 * @return an array holding the columns
	 */
	public static ImagePartRectangle[] splitIntoColumns(ImagePartRectangle rect, double shearDegrees, boolean requireVerticalSplit) {
		return splitIntoColumns(rect, 1, shearDegrees, requireVerticalSplit);
	}
	
	/**
	 * Split a rectangular area of an image into columns at embedded vertical
	 * white stripes wider than a given threshold.
	 * @param rect the rectangle to split
	 * @param minSplitMargin the minimum width for splitting
	 * @param shearDegrees the deviation of the split lines from the vertical
	 * @param requireVerticalSplit require a white vertical pass in sheared
	 *            splits?
	 * @return an array holding the columns
	 */
	public static ImagePartRectangle[] splitIntoColumns(ImagePartRectangle rect, int minSplitMargin, double shearDegrees, boolean requireVerticalSplit) {
		if ((rect.bottomRow <= rect.topRow) || (rect.rightCol <= rect.leftCol)) {
			ImagePartRectangle[] iprs = {rect};
			return iprs;
		}
		
		int maxOffset = ((shearDegrees == 0) ? 0 : Math.abs((int) (Math.tan(shearDegrees * Math.PI / 180) * ((double) (rect.bottomRow - rect.topRow)))));
		maxOffset = ((int) (maxOffset * Math.signum(shearDegrees)));
		if (maxOffset == 0)
			requireVerticalSplit = false; // no need for that hassle in vertical split
		int[] offsets = new int[rect.bottomRow - rect.topRow];
		for (int o = 0; o < offsets.length; o++) {
			if (maxOffset > 0)
				offsets[o] = (((o * maxOffset) + (offsets.length / 2)) / offsets.length);
			else offsets[o] = ((((offsets.length - o - 1) * -maxOffset) + (offsets.length / 2)) / offsets.length);
		}
		
		byte[][] brightness = rect.ai.getBrightness();
		byte[] colBrightnesses = new byte[rect.rightCol - rect.leftCol];
		byte[] sColBrightnesses = new byte[rect.rightCol - rect.leftCol];
		for (int c = rect.leftCol; c < rect.rightCol; c++) {
			int brightnessSum = 0;
			byte minBrightness = 127;
			int sBrightnessSum = 0;
			byte sMinBrightness = 127;
			int sc;
			byte sb;
			for (int r = rect.topRow; r < rect.bottomRow; r++) {
				brightnessSum += brightness[c][r];
				minBrightness = ((byte) Math.min(minBrightness, brightness[c][r]));
//				sc = c - offsets[r - rect.topRow]; // WRONG: we have to subtract the offset so positive angles correspond to shearing top of rectangle rightward
				sc = c + offsets[r - rect.topRow]; // RIGHT: we have to add the offset so positive angles correspond to shearing top of rectangle rightward
				sb = (((rect.leftCol <= sc) && (sc < rect.rightCol)) ? brightness[sc][r] : 127);
				sBrightnessSum += sb;
				sMinBrightness = ((byte) Math.min(sMinBrightness, sb));
			}
			colBrightnesses[c - rect.leftCol] = ((byte) (brightnessSum / (rect.bottomRow - rect.topRow)));
			sColBrightnesses[c - rect.leftCol] = ((byte) (sBrightnessSum / (rect.bottomRow - rect.topRow)));
		}
		
		ArrayList rects = new ArrayList();
		boolean[] whiteCols = new boolean[rect.rightCol - rect.leftCol];
		boolean[] sWhiteCols = new boolean[rect.rightCol - rect.leftCol];
		
		byte colBrightnessPivot = 127;
		
		for (int c = rect.leftCol; c < rect.rightCol; c++) {
			whiteCols[c - rect.leftCol] = (colBrightnesses[c - rect.leftCol] >= colBrightnessPivot);
			sWhiteCols[c - rect.leftCol] = (sColBrightnesses[c - rect.leftCol] >= colBrightnessPivot);
		}
		
		int white = 0;
		int left = -1;
//		int lastRight = 0;
		for (int c = rect.leftCol; c < rect.rightCol; c++) {
			if (sWhiteCols[c - rect.leftCol]) {
				white++;
				if ((white >= minSplitMargin) && (left != -1)) {
					int lc = Math.max((left - (maxOffset / 2)), rect.leftCol);
					int rc = Math.min(((c - white + 1) - (maxOffset / 2)), rect.rightCol);
					if (lc < rect.leftCol)
						lc = rect.leftCol;
					if (rc > rect.rightCol)
						rc = rect.rightCol;
					
					//	this actually can happen with tiny chunks (e.g. horizontal lines) and big slopes ...
					if (rc < lc)
						continue;
					
					ImagePartRectangle res = new ImagePartRectangle(rect.ai);
					res.topRow = rect.topRow;
					res.bottomRow = rect.bottomRow;
					res.leftCol = lc;
					res.rightCol = rc;
					res = narrowTopAndBottom(res);
					if (requireVerticalSplit) {
						while ((rect.leftCol < res.leftCol) && (((res.leftCol - rect.leftCol) >= whiteCols.length) || !whiteCols[res.leftCol - rect.leftCol]))
							res.leftCol--;
						while (((res.leftCol + 1) < rect.rightCol) && whiteCols[res.leftCol - rect.leftCol])
							res.leftCol++;
						while ((res.rightCol < rect.rightCol) && ((res.rightCol - rect.leftCol) < whiteCols.length) && !whiteCols[res.rightCol - rect.leftCol])
							res.rightCol++;
						while ((rect.leftCol < (res.rightCol - 1)) && whiteCols[res.rightCol-1 - rect.leftCol])
							res.rightCol--;
					}
//					if (lastRight < res.leftCol) {
//						rects.add(res);
//						lastRight = res.rightCol;
//						left = -1;
//					}
					rects.add(res);
					left = -1;
				}
			}
			else {
				if (left == -1)
					left = c;
				white = 0;
			}
		}
		
		if (left == rect.leftCol) {
			ImagePartRectangle[] res = {rect};
			return res;
		}
		
		if (left != -1) {
			ImagePartRectangle res = new ImagePartRectangle(rect.ai);
			res.topRow = rect.topRow;
			res.bottomRow = rect.bottomRow;
			res.leftCol = Math.min(Math.max((left - (maxOffset / 2)), rect.leftCol), (rect.rightCol - 1));
			if (requireVerticalSplit) {
				while ((rect.leftCol < res.leftCol) && !whiteCols[res.leftCol - rect.leftCol])
					res.leftCol--;
				while (((res.leftCol + 1) < rect.rightCol) && whiteCols[res.leftCol - rect.leftCol])
					res.leftCol++;
			}
			res.rightCol = rect.rightCol;
			rects.add(res);
		}
		return ((ImagePartRectangle[]) rects.toArray(new ImagePartRectangle[rects.size()]));
	}
	
	/**
	 * Split a rectangular area of an image into rows at embedded horizontal
	 * white stripes.
	 * @param rect the rectangle to split
	 * @return an array holding the rows
	 */
	public static ImagePartRectangle[] splitIntoRows(ImagePartRectangle rect) {
		return splitIntoRows(rect, 1, 0, -1, 0);
	}
	
	/**
	 * Split a rectangular area of an image into rows at embedded horizontal
	 * white stripes.
	 * @param rect the rectangle to split
	 * @param maxSkewAngle the maximum angle (in degrees) for skewed splits
	 *            (non-positive values deactivate skewed splitting)
	 * @return an array holding the rows
	 */
	public static ImagePartRectangle[] splitIntoRows(ImagePartRectangle rect, double maxSkewAngle) {
		return splitIntoRows(rect, 1, maxSkewAngle, -1, 0);
	}
	
	/**
	 * Split a rectangular area of an image into rows at embedded horizontal
	 * white stripes higher than a given threshold.
	 * @param rect the rectangle to split
	 * @param minSplitMargin the minimum height for splitting
	 * @return an array holding the rows
	 */
	public static ImagePartRectangle[] splitIntoRows(ImagePartRectangle rect, int minSplitMargin) {
		return splitIntoRows(rect, minSplitMargin, 0, -1, 0);
	}
	
	/**
	 * Split a rectangular area of an image into rows at embedded horizontal
	 * white stripes higher than a given threshold.
	 * @param rect the rectangle to split
	 * @param minSplitMargin the minimum height for splitting
	 * @param maxSkewAngle the maximum angle (in degrees) for skewed splits
	 *            (non-positive values deactivate skewed splitting)
	 * @return an array holding the rows
	 */
	public static ImagePartRectangle[] splitIntoRows(ImagePartRectangle rect, int minSplitMargin, double maxSkewAngle) {
		return splitIntoRows(rect, minSplitMargin, maxSkewAngle, -1, 0);
	}
	
	/**
	 * Split a rectangular area of an image into rows at embedded horizontal
	 * white stripes higher than a given threshold.
	 * @param rect the rectangle to split
	 * @param minSplitMargin the minimum height for splitting
	 * @param maxSkewAngle the maximum angle (in degrees) for skewed splits
	 *            (non-positive values deactivate skewed splitting)
	 * @param zigzagPartWidth the minimum width of a straight part in a zigzag
	 *            split (non-positive values deactivate zigzag splitting)
	 * @param maxZigzagSlope the maximum vertical deviation of a zigzag split
	 * @return an array holding the rows
	 */
	public static ImagePartRectangle[] splitIntoRows(ImagePartRectangle rect, int minSplitMargin, double maxSkewAngle, int zigzagPartWidth, int maxZigzagSlope) {
		if ((rect.bottomRow <= rect.topRow) || (rect.rightCol <= rect.leftCol)) {
			ImagePartRectangle[] iprs = {rect};
			return iprs;
		}
		
		ArrayList rects = new ArrayList();
		ImagePartRectangle[] iprs = splitIntoRows(rect, minSplitMargin, 0);
		rects.addAll(Arrays.asList(iprs));
//		System.out.println(" --> got " + iprs.length + " rows after straight split");
		
		//	try skewed cuts up to two tenths of a degree upward and downward, as FFT might fail to discover small rotations
		ImagePartRectangle[] skewCutIprs;
		final int minSkew = 1;
		final int maxSkew = ((maxSkewAngle > 0) ? Math.abs((int) (Math.tan(maxSkewAngle * Math.PI / 180) * ((double) (rect.rightCol - rect.leftCol)))) : 0);
		int lastSkew = -1;
		HashSet completedSplits = new HashSet();
		for (int r = 0; r < rects.size(); r++) {
			ImagePartRectangle ipr = ((ImagePartRectangle) rects.get(r));
			int skew = ((lastSkew == -1) ? minSkew : lastSkew);
			while (maxSkew != 0) {
				if (completedSplits.add(ipr.getId() + skew)) {
					skewCutIprs = splitIntoRows(ipr, minSplitMargin, skew);
					if (skewCutIprs.length > 1) {
						rects.set(r, skewCutIprs[0]);
						for (int s = 1; s < skewCutIprs.length; s++) {
							r++;
							rects.add(r, skewCutIprs[s]);
						}
						r--;
						lastSkew = skew;
						break;
					}
				}
				if (completedSplits.add(ipr.getId() + (-skew))) {
					skewCutIprs = splitIntoRows(ipr, minSplitMargin, -skew);
					if (skewCutIprs.length > 1) {
						rects.set(r, skewCutIprs[0]);
						for (int s = 1; s < skewCutIprs.length; s++) {
							r++;
							rects.add(r, skewCutIprs[s]);
						}
						r--;
						lastSkew = skew;
						break;
					}
				}
				if (skew >= maxSkew) {
					lastSkew = -1;
					break;
				}
				skew++;
			}
		}
		
		//	no zigzagging, we're done
		if (zigzagPartWidth < 1)
			return ((ImagePartRectangle[]) rects.toArray(new ImagePartRectangle[rects.size()]));
		
		//	try skewed cuts up to two tenths of a degree upward and downward, as FFT might fail to discover small rotations
		ImagePartRectangle[] zigzagCutIprs;
		for (int r = 0; r < rects.size(); r++) {
			ImagePartRectangle ipr = ((ImagePartRectangle) rects.get(r));
			zigzagCutIprs = splitIntoRowsZigzag(ipr, minSplitMargin, zigzagPartWidth, maxZigzagSlope);
			if (zigzagCutIprs.length > 1) {
				rects.set(r, zigzagCutIprs[0]);
				for (int s = 1; s < zigzagCutIprs.length; s++) {
					r++;
					rects.add(r, zigzagCutIprs[s]);
				}
			}
		}
		
		return ((ImagePartRectangle[]) rects.toArray(new ImagePartRectangle[rects.size()]));
	}
	
	private static ImagePartRectangle[] splitIntoRowsZigzag(ImagePartRectangle rect, int minSplitMargin, int minPartLength, int maxSlope) {
		
		//	avoid rediculously small parts
		if (minPartLength < 2) {
			ImagePartRectangle[] iprs = {rect};
			return iprs;
		}
		
		//	get brightness grid
		byte[][] brightness = rect.ai.getBrightness();
		
		//	this array stores how far to the right a part extends, so finding a path becomes easier
		int[][] parts = new int[brightness.length][];
		for (int p = 0; p < parts.length; p++) {
			parts[p] = new int[brightness[p].length];
			Arrays.fill(parts[p], 0);
		}
		
		//	find partial passes
		for (int c = 0; c < ((rect.rightCol - rect.leftCol) - minPartLength); c++) {
			boolean foundPassRow = false;
			for (int r = 0; r < (rect.bottomRow - rect.topRow); r++) {
				
				//	we've been here before
				if (parts[c][r] != 0) {
					foundPassRow = true;
					continue;
				}
				
				//	try to find part from current starting point rightward
				int partLength = 0;
				while ((c + rect.leftCol + partLength) < rect.rightCol) {
					if (brightness[c + rect.leftCol + partLength][r + rect.topRow] == 127)
						partLength++;
					else break;
				}
				
				//	this one's long enough, mark it
				if (minPartLength <= partLength) {
					for (int pc = 0; pc < partLength; pc++)
						parts[c + pc][r] = (c + partLength);
					foundPassRow = true;
				}
			}
			
			//	some column we just cannot get through, no use checking any further
			if (!foundPassRow) {
				ImagePartRectangle[] res = {rect};
				return res;
			}
		}
		
		//	assemble partial passes to complete passes
		ArrayList splits = new ArrayList();
		ArrayList splitGroup = new ArrayList();
		for (int r = 0; r < (rect.bottomRow - rect.topRow); r++) {
			
			//	no passage here
			if (parts[0][r] == 0) {
				
				//	close current split group
				if (splitGroup.size() != 0) {
					int minWidth = Integer.MAX_VALUE;
					ZigzagSplit minWidthSplit = null;
					for (int s = 0; s < splitGroup.size(); s++) {
						ZigzagSplit zs = ((ZigzagSplit) splitGroup.get(s));
						if ((zs.maxRow - zs.minRow) < minWidth) {
							minWidth = (zs.maxRow - zs.minRow);
							minWidthSplit = zs;
						}
					}
					if (minWidthSplit != null)
						splits.add(minWidthSplit);
					splitGroup.clear();
				}
				continue;
			}
			
			//	start following partial pass
			int midPassRow = -1;
			int row = r;
			int col = rect.leftCol + 1;
			int minRow = row;
			int maxRow = row;
			int searchOffset = (minPartLength / 10);
			while (true) {
				col = (rect.leftCol + parts[col - rect.leftCol - 1][row]);
				
				//	find continuation above or below
				if (col < rect.rightCol) {
					int maxProgress = col - rect.leftCol;
					int maxProgressRow = -1;
					int pr = row;
					
					//	TODO_ne: minimum lenght of parts prevents wild straying between words, etc.// limit deviation to sensible bounds to prevent trouble cutting wild paths through images
					//	TODO_ne: unnecessary, as later split happens at brightest row between split limits anyways// remember staring row, and keep tendency back there
					
					while ((pr != 0) && (parts[col - searchOffset - rect.leftCol - 1][pr-1] != 0)) {
						pr--;
						if (parts[col - searchOffset - rect.leftCol - 1][pr] > maxProgress) {
							maxProgress = parts[col - searchOffset - rect.leftCol - 1][pr];
							maxProgressRow = pr;
						}
					}
					pr = row;
					while (((pr+1) < (rect.bottomRow - rect.topRow)) && (parts[col - searchOffset - rect.leftCol - 1][pr+1] != 0)) {
						pr++;
						if (parts[col - searchOffset - rect.leftCol - 1][pr] > maxProgress) {
							maxProgress = parts[col - searchOffset - rect.leftCol - 1][pr];
							maxProgressRow = pr;
						}
					}
					
					//	we have a continuation
					if (maxProgressRow != -1) {
						row = maxProgressRow;
						minRow = Math.min(minRow, row);
						maxRow = Math.max(maxRow, row);
					}
					
					//	we've reached a dead end
					else break;
				}
				
				//	we are through, compute horizontal pass
				else {
					midPassRow = ((r + row) / 2) + rect.topRow;
					break;
				}
			}
			
			//	we've found a pass (strict 'less than' is OK, as both maxRow and minRow are inclusive)
			if (midPassRow != -1) {
				if ((maxRow - minRow) < maxSlope)
					splitGroup.add(new ZigzagSplit((minRow + rect.topRow), (maxRow + rect.topRow)));
			}
		}
		
		//	close remaining split group
		if (splitGroup.size() != 0) {
			int minWidth = Integer.MAX_VALUE;
			ZigzagSplit minWidthSplit = null;
			for (int s = 0; s < splitGroup.size(); s++) {
				ZigzagSplit zs = ((ZigzagSplit) splitGroup.get(s));
				if ((zs.maxRow - zs.minRow) < minWidth) {
					minWidth = (zs.maxRow - zs.minRow);
					minWidthSplit = zs;
				}
			}
			if (minWidthSplit != null)
				splits.add(minWidthSplit);
			splitGroup.clear();
		}
		
		//	nothing to do
		if (splits.isEmpty()) {
			ImagePartRectangle[] res = {rect};
			return res;
		}
		
		//	TODOne try out straigh right-through-the-middle approach (with word correction, this should not cause too much demage)
		//	==> still no good
		//	TODOne try inserting margin, size of split
		//	==> seems to work fine, verify with more tests
		
		//	perform splits
		ArrayList rects = new ArrayList();
		int topRow = rect.topRow;
		for (int s = 0; s < splits.size(); s++) {
			ZigzagSplit zs = ((ZigzagSplit) splits.get(s));
			
			//	perform split
			ImagePartRectangle res = new ImagePartRectangle(rect.ai);
			res.leftCol = rect.leftCol;
			res.rightCol = rect.rightCol;
			res.topRow = topRow;
			res.bottomRow = zs.minRow + 1;
			while (res.bottomRow <= res.topRow) {
				res.bottomRow++;
				res.topRow--;
			}
			res = narrowTopAndBottom(res);
			if (res.isEmpty())
				continue;
			rects.add(res);
			topRow = zs.maxRow;
		}
		
		//	mark last rectangle
		if (topRow < rect.bottomRow-1) {
			ImagePartRectangle res = new ImagePartRectangle(rect.ai);
			res.topRow = topRow;
			res.leftCol = rect.leftCol;
			res.bottomRow = rect.bottomRow;
			res.rightCol = rect.rightCol;
			res = narrowTopAndBottom(res);
			if (!res.isEmpty())
				rects.add(res);
		}
		
		//	finally ...
		return ((ImagePartRectangle[]) rects.toArray(new ImagePartRectangle[rects.size()]));
	}
	
	private static class ZigzagSplit {
		final int minRow;
		final int maxRow;
		ZigzagSplit(int minRow, int maxRow) {
			this.minRow = minRow;
			this.maxRow = maxRow;
		}
	}
	
	private static ImagePartRectangle[] splitIntoRows(ImagePartRectangle rect, int minSplitMargin, int maxOffset) {
		int[] offsets = new int[rect.rightCol - rect.leftCol];
		for (int o = 0; o < offsets.length; o++) {
			if (maxOffset > 0)
				offsets[o] = (((o * maxOffset) + (offsets.length / 2)) / offsets.length);
			else offsets[o] = (((o * maxOffset) - (offsets.length / 2)) / offsets.length);
		}
		
		byte[][] brightness = rect.ai.getBrightness();
		byte[] rowBrightnesses = new byte[rect.bottomRow - rect.topRow];
		for (int r = rect.topRow; r < rect.bottomRow; r++) {
			int brightnessSum = 0;
			int or;
			byte b;
			for (int c = rect.leftCol; c < rect.rightCol; c++) {
				or = r + offsets[c - rect.leftCol];
				b = (((rect.topRow <= or) && (or < rect.bottomRow)) ? brightness[c][or] : 127);
				brightnessSum += b;
			}
			rowBrightnesses[r - rect.topRow] = ((byte) (brightnessSum / (rect.rightCol - rect.leftCol)));
		}
		
		ArrayList rects = new ArrayList();
		boolean[] whiteRows = new boolean[rect.bottomRow - rect.topRow];
		
		short rowBrightnessPivot = 127;
		
		//	TODOne try out straigh right-through-the-middle approach (with word correction, this should not cause too much demage)
		//	==> still no good
		//	TODOne try inserting margin, size of split
		//	==> seems to work fine, verify with more tests
		
		for (int r = rect.topRow; r < rect.bottomRow; r++)
			whiteRows[r - rect.topRow] = (rowBrightnesses[r - rect.topRow] >= rowBrightnessPivot);
		int white = 0;
		int top = -1;
		for (int r = rect.topRow; r < rect.bottomRow; r++) {
			if (whiteRows[r - rect.topRow]) {
				white++;
				if ((white >= minSplitMargin) && (top != -1)) {
					int tr = top + ((maxOffset < 0) ? 0 : maxOffset);
					int br = (r - white + 1) + ((maxOffset < 0) ? maxOffset : 0);
					if (tr < rect.topRow)
						tr = rect.topRow;
					if (br > rect.bottomRow)
						br = rect.bottomRow;
					
					//	this actually can happen with tiny chunks (e.g. horizontal lines) and big slopes ...
					if (br < tr)
						continue;
					
					ImagePartRectangle res = new ImagePartRectangle(rect.ai);
					res.topRow = tr;
					res.bottomRow = br;
					res.leftCol = rect.leftCol;
					res.rightCol = rect.rightCol;
					res = narrowLeftAndRight(res);
					if (maxOffset == 0)
						res.splitClean = true;
					if (!res.isEmpty())
						rects.add(res);
					top = -1;
				}
			}
			else {
				if (top == -1)
					top = r;
				white = 0;
			}
		}
		
		if (top == rect.topRow) {
			ImagePartRectangle[] res = {rect};
			return res;
		}
		
		if (top != -1) {
			ImagePartRectangle res = new ImagePartRectangle(rect.ai);
			int tr = top + ((maxOffset < 0) ? 0 : maxOffset);
			if (tr < rect.topRow)
				tr = rect.topRow;
			if (tr < rect.bottomRow) {
				res.topRow = tr;
				res.leftCol = rect.leftCol;
				res.bottomRow = rect.bottomRow;
				res.rightCol = rect.rightCol;
				res = narrowTopAndBottom(res);
				if (maxOffset == 0)
					res.splitClean = true;
				if (!res.isEmpty())
					rects.add(res);
			}
		}
		
		return ((ImagePartRectangle[]) rects.toArray(new ImagePartRectangle[rects.size()]));
	}
	
	/**
	 * Wrapper for an FFT peak.
	 * 
	 * @author sautter
	 */
	public static class Peak {
		public final int x;
		public final int y;
		public final double h;
		Peak(int x, int y, double h) {
			this.x = x;
			this.y = y;
			this.h = h;
		}
	}
	
	public static final int ADJUST_MODE_NONE = 0;
	public static final int ADJUST_MODE_SQUARE_ROOT = 1;
	public static final int ADJUST_MODE_LOG = 2;
	private static double adjust(double d, int mode) {
		if (mode == ADJUST_MODE_NONE)
			return d;
		else if (mode == ADJUST_MODE_SQUARE_ROOT)
			return Math.sqrt(d);
		else if (mode == ADJUST_MODE_LOG) {
			double a = Math.log(d);
			if (a < 0)
				return 0;
			return Math.pow(a, 3);
		}
		else return Math.sqrt(d);
	}
	
	private static final int fftCacheSize = 128;
	private static Map fftCache = Collections.synchronizedMap(new LinkedHashMap(128, 0.9f, true) {
		protected boolean removeEldestEntry(Entry eldest) {
			return (this.size() > fftCacheSize);
		}
	});
	
	/**
	 * Compute the FFT of an AnalysisImage. Having the AnalysisImage repeated computes the FFT
	 * of a plain parquetted with the argument AnalysisImage instead.
	 * @param image the image to use
	 * @param tdim the size of the FFT
	 * @param repeatImage repeat the AnalysisImage?
	 * @return the FFT of the argument AnalysisImage, sized tdim x tdim
	 */
	public static Complex[][] getFft(BufferedImage image, int tdim, boolean repeatImage) {
		return getFft(image, tdim, tdim, repeatImage);
	}
	
	/**
	 * Compute the FFT of an AnalysisImage. Having the AnalysisImage repeated computes the FFT
	 * of a plain parquetted with the argument AnalysisImage instead.
	 * @param image the image to use
	 * @param tdimx the width of the FFT
	 * @param tdimy the height of the FFT
	 * @param repeatImage repeat the AnalysisImage?
	 * @return the FFT of the argument AnalysisImage, sized tdim x tdim
	 */
	public static Complex[][] getFft(BufferedImage image, int tdimx, int tdimy, boolean repeatImage) {
		String cacheKey = (image.hashCode() + "-" + tdimx + "-" + tdimy + "-" + repeatImage);
		Complex[][] fft = ((Complex[][]) fftCache.get(cacheKey));
		if (fft != null)
			return fft;
		
		int iw = image.getWidth();
		int ih = image.getHeight();
		int agg = 1;
		agg = Math.max(agg, ((iw + tdimx - 1) / tdimx));
		agg = Math.max(agg, ((ih + tdimy - 1) / tdimy));
		
		Complex[][] ytrans = new Complex[tdimy][]; // first dimension is vertical here
		Complex[] row;
		int ix;
		int iy;
//		float[] hsb = new float[3];
		float bs;
		int rgb;
		int wrgb = Color.WHITE.getRGB();
		for (int y = 0; y < tdimy; y++) {
			row = new Complex[tdimx];
			for (int x = 0; x < tdimx; x++) {
				ix = (x * agg);
				iy = (y * agg);
				bs = 0;
				for (int ax = 0; ax < agg; ax++) {
					for (int ay = 0; ay < agg; ay++) {
						if (repeatImage)
							rgb = image.getRGB(((ix + ax) % iw), ((iy + ay) % ih));
						else rgb = ((y < ih) ? image.getRGB(((ix + ax) % iw), y) : wrgb);
//						hsb = new Color(rgb).getColorComponents(hsb);
//						bs += hsb[2];
						bs += (((float) getByteBrightness(rgb)) / 128);
					}
				}
				row[x] = new Complex((bs / (agg * agg)), 0);
			}
			ytrans[y] = computeFft(row);
		}
		
		Complex[] col;
		fft = new Complex[tdimx][];
		for (int x = 0; x < tdimx; x++) {
			col = new Complex[tdimy];
			for (int y = 0; y < tdimy; y++)
				col[y] = ytrans[y][x];
			fft[x] = computeFft(col);
			if (x == 0)
				fft[0][0] = new Complex(0, 0); // exclude DC
		}
		
		fftCache.put(cacheKey, fft);
		return fft;
	}
	
	/**
	 * Compute the maximum peak of an FFT.
	 * @param fft the FFT
	 * @param adjustMode the peak adjustment mode
	 * @return the adjusted maximum peak height of the argument FFT
	 */
	public static double getMax(Complex[][] fft, int adjustMode) {
		double max = 0;
		for (int x = 0; x < fft.length; x++) {
			for (int y = 0; y < fft[x].length; y++)
				max = Math.max(max, adjust(fft[x][y].abs(), adjustMode));
		}
		return max;
	}
	
	/**
	 * Collect FFT peaks whose adjusted value exceeds a given threshold.
	 * @param fft the FFT result to work with
	 * @param peaks the collection to store the peaks in
	 * @param th the threshold
	 * @param adjustMode the value adjust mode (default is
	 *            ADJUST_MODE_SQUARE_ROOT)
	 */
	public static void collectPeaks(Complex[][] fft, Collection peaks, double th, int adjustMode) {
		double t;
		for (int x = 0; x < fft.length; x++) {
			for (int y = 0; y < fft[x].length; y++) {
				t = adjust(fft[x][y].abs(), adjustMode);
				if (t > th)
					peaks.add(new Peak(((x + fft.length/2) % fft.length), ((y + fft[x].length/2) % fft[x].length), t));
			}
		}
	}
	
	/**
	 * Correct page images who are scanned out of the vertical. This method
	 * first uses FFT peaks to compute the rotation against the vertical, then
	 * rotates the AnalysisImage back to the vertical if the deviation is more
	 * than the specified granularity.
	 * @param ai the AnalysisImage to correct
	 * @param dpi the resolution of the image
	 * @param granularity the granularity in degrees
	 * @param adjustMode the FFT peak adjust mode
	 * @return true if the argument AnalysisImage was modified, false otherwise
	 */
	public static boolean correctPageRotation(AnalysisImage ai, int dpi, double granularity, int adjustMode) {
		return correctPageRotation(ai, dpi, granularity, null, -1, adjustMode, null);
	}
	
	/**
	 * Correct page images who are scanned out of the vertical. This method
	 * first uses FFT peaks to compute the rotation against the vertical, then
	 * rotates the AnalysisImage back to the vertical if the deviation is more
	 * than the specified granularity. If the argument OCR word boundaries are
	 * not null, they are rotated alongside the page image and placed in the
	 * array in the order they come in. The bounding boxes have to be in the
	 * same coordinate system and resolution as the argument page image proper
	 * for results to be meaningful.
	 * @param ai the AnalysisImage to correct
	 * @param dpi the resolution of the image
	 * @param granularity the granularity in degrees
	 * @param adjustMode the FFT peak adjust mode
	 * @param exOcrWordBounds an array holding the bounding boxes of existing
	 *            OCR words
	 * @return true if the argument AnalysisImage was modified, false otherwise
	 */
	public static boolean correctPageRotation(AnalysisImage ai, int dpi, double granularity, int adjustMode, BoundingBox[] exOcrWordBounds) {
		return correctPageRotation(ai, dpi, granularity, null, -1, adjustMode, exOcrWordBounds);
	}
	
	/**
	 * Correct page images who are scanned out of the vertical. This method
	 * first uses peaks in the argument FFT to compute the rotation against the
	 * vertical, then rotates the AnalysisImage back to the vertical if the
	 * deviation is more than the specified granularity. The argument FFT must
	 * originate from the argument AnalysisImage for the result of this method
	 * to be meaningful. If the argument FFT is null, this method computes it
	 * as a 256 by 256 complex array.
	 * @param ai the AnalysisImage to correct
	 * @param dpi the resolution of the image
	 * @param granularity the granularity in degrees
	 * @param fft the FFT of the AnalysisImage (set to null to have it computed here)
	 * @param max the adjusted maximum peak height of the argument FFT (set to
	 *            a negative number to have it computed here)
	 * @param adjustMode the FFT peak adjust mode
	 * @return true if the argument AnalysisImage was modified, false otherwise
	 */
	public static boolean correctPageRotation(AnalysisImage ai, int dpi, double granularity, Complex[][] fft, double max, int adjustMode) {
		return correctPageRotation(ai, dpi, granularity, fft, max, adjustMode, null);
	}
	
	/**
	 * Correct page images who are scanned out of the vertical. This method
	 * first uses peaks in the argument FFT to compute the rotation against the
	 * vertical, then rotates the AnalysisImage back to the vertical if the
	 * deviation is more than the specified granularity. The argument FFT must
	 * originate from the argument AnalysisImage for the result of this method
	 * to be meaningful. If the argument FFT is null, this method computes it
	 * as a 256 by 256 complex array. If the argument OCR word boundaries are
	 * not null, they are rotated alongside the page image and placed in the
	 * array in the order they come in. The bounding boxes have to be in the
	 * same coordinate system and resolution as the argument page image proper
	 * for results to be meaningful.
	 * @param ai the AnalysisImage to correct
	 * @param dpi the resolution of the image
	 * @param granularity the granularity in degrees
	 * @param fft the FFT of the AnalysisImage (set to null to have it computed here)
	 * @param max the adjusted maximum peak height of the argument FFT (set to
	 *            a negative number to have it computed here)
	 * @param adjustMode the FFT peak adjust mode
	 * @param exOcrWordBounds an array holding the bounding boxes of existing
	 *            OCR words
	 * @return true if the argument AnalysisImage was modified, false otherwise
	 */
	public static boolean correctPageRotation(AnalysisImage ai, int dpi, double granularity, Complex[][] fft, double max, int adjustMode, BoundingBox[] exOcrWordBounds) {
		boolean rotationCorrected = false;
		double rotatedBy = 0;
		
		//	use FFT (preferring text mask if given, as the text is what we're trying to align)
		if (0 < granularity) {
			if (fft == null) {
//				fft = ai.getFft();
				BufferedImage fftImage = ((ai.textImage == null) ? ai.image : ai.textImage);
				int fftDim = 64;
				int fftSize = Math.min(256, Math.max(fftImage.getWidth(), fftImage.getHeight()));
				while (fftDim < fftSize)
					fftDim *= 2;
				fft = getFft(fftImage, fftDim, fftDim, (fftImage.getWidth() < fftImage.getHeight()));
				max = getMax(fft, adjustMode);
			}
			else if (max < 0)
				max = getMax(fft, adjustMode);
			ArrayList peaks = new ArrayList();
			collectPeaks(fft, peaks, max/4, adjustMode);
			
//			//	TODO keep this deactivated for export !!!
//			BufferedImage fftImage = getFftImage(fft, adjustMode);
//			try {
//				ImageIO.write(fftImage, "png", new File("E:/Testdaten/PdfExtract/FFT." + System.currentTimeMillis() + ".png"));
//			} catch (IOException ioe) {}
			
			
			//	detect and correct major skewing via FFT
			double pageRotationAngle = getPageRotationAngle(peaks, 20, (180 * ((int) (1 / granularity))));
			System.out.println("Page rotation angle by FFT is " + (((float) ((int) ((180 / Math.PI) * pageRotationAngle * 100))) / 100) + "�");
//			if (fftAngle > maxPageRotationCorrectionAngle)
//				return false;
//			if (Math.abs(angle) > ((Math.PI / 180) * granularity)) {
//				analysisImage.setImage(rotateImage(analysisImage.getImage(), -angle));
//				return true;
//			}
//			else return false;
			if ((pageRotationAngle < maxPageRotationCorrectionAngle) && (Math.abs(pageRotationAngle) > ((Math.PI / 180) * granularity))) {
//				analysisImage.setImage(rotateImage(analysisImage.getImage(), -pageRotationAngle, exOcrWordBounds));
				BufferedImage image = rotateImage(ai.image, -pageRotationAngle, exOcrWordBounds);
				BufferedImage backgroundImage = ((ai.backgroundImage == null) ? null : rotateImage(ai.backgroundImage, -pageRotationAngle, null));
				BufferedImage textImage = ((ai.textImage == null) ? null : rotateImage(ai.textImage, -pageRotationAngle, null));
				ai.setImage(image, backgroundImage, textImage);
				rotatedBy -= pageRotationAngle;
				rotationCorrected = true;
			}
		}
		
		//	detect and correct minor skewing via block line focusing
		//	use text mask if available, as baseline serif peeks is what we're working with
		ArrayList blocks = new ArrayList();
		HashSet blockIDs = new HashSet();
//		ImagePartRectangle pageBox = getContentBox(ai);
		ImagePartRectangle pageBox = getContentBox((ai.textImage == null) ? ai : wrapImage(ai.textImage, null));
		blocks.add(pageBox);
		for (int blk = 0; blk < blocks.size(); blk++) {
			ImagePartRectangle ipr = ((ImagePartRectangle) blocks.get(blk));
			if (DEBUG_LINE_FOCUSSING) System.out.println("Splitting " + ipr.getId() + ":");
			if ((ipr != pageBox) && ((ipr.getWidth() < dpi) || ((ipr.getHeight() / 2) < dpi))) {
				if (DEBUG_LINE_FOCUSSING) System.out.println(" --> too small");
				blocks.remove(blk--);
				continue;
			}
			ArrayList iprParts = new ArrayList();
			ImagePartRectangle[] iprBlocks = splitIntoRows(ipr, (dpi / 8), maxBlockRotationAngle); // 3 mm
			for (int b = 0; b < iprBlocks.length; b++) {
				iprBlocks[b] = narrowLeftAndRight(iprBlocks[b]);
				if (DEBUG_LINE_FOCUSSING) System.out.println(" - " + iprBlocks[b].getId());
				if (!blockIDs.add(iprBlocks[b].getId())) {
					if (DEBUG_LINE_FOCUSSING) System.out.println(" --> seen before");
					continue;
				}
				ImagePartRectangle[] iprCols = splitIntoColumns(iprBlocks[b], (dpi / 8), maxBlockRotationAngle, true); // 3 mm
				if (iprCols.length == 1) {
					iprParts.add(iprBlocks[b]);
					if (DEBUG_LINE_FOCUSSING) System.out.println(" --> single column, added");
				}
				else for (int c = 0; c < iprCols.length; c++) {
					iprCols[c] = narrowTopAndBottom(iprCols[c]);
					if (DEBUG_LINE_FOCUSSING) System.out.println("   - " + iprCols[c].getId());
					if (!blockIDs.add(iprCols[c].getId())) {
						if (DEBUG_LINE_FOCUSSING) System.out.println(" --> seen before");
						continue;
					}
					iprParts.add(iprCols[c]);
					if (DEBUG_LINE_FOCUSSING) System.out.println(" --> added");
				}
			}
			if (iprParts.size() > 1) {
				blocks.addAll(iprParts);
				blocks.remove(blk--);
			}
		}
		if (DEBUG_LINE_FOCUSSING) System.out.println("Got " + blocks.size() + " blocks:");
		for (int b = 0; b < blocks.size(); b++) {
			ImagePartRectangle block = ((ImagePartRectangle) blocks.get(b));
			if (DEBUG_LINE_FOCUSSING) System.out.println(" - " + block.getId());
			if (block.getWidth() < dpi) {
				blocks.remove(b--);
				if (DEBUG_LINE_FOCUSSING) System.out.println(" ==> too small");
			}
			else if (DEBUG_LINE_FOCUSSING) System.out.println(" ==> looks useful");
		}
		
		//	get weighted average rotation angle for page blocks
		double weightedBlockRotationAngleSum = 0.0;
		int blockWeightSum = 0;
		for (int b = 0; b < blocks.size(); b++) {
			ImagePartRectangle block = ((ImagePartRectangle) blocks.get(b));
			double blockRotationAngle = getBlockRotationAngle(ai, block);
			if (blockRotationAngle == invalidBlockRotationAngle) {
				if (DEBUG_LINE_FOCUSSING) System.out.println("Could not determine block rotation angle");
				continue;
			}
			if (DEBUG_LINE_FOCUSSING) System.out.println("Block rotation angle is " + (((float) ((int) (blockRotationAngle * 100))) / 100) + "�");
			int blockWeight = (block.getWidth() * block.getHeight());
			weightedBlockRotationAngleSum += (blockRotationAngle * blockWeight);
			blockWeightSum += blockWeight;
		}
		double blockRotationAngle = (weightedBlockRotationAngleSum / blockWeightSum);
		System.out.println("Page rotation angle by block line focusing is " + (((float) ((int) (blockRotationAngle * 100))) / 100) + "�");
		if (Math.abs(blockRotationAngle) > blockRotationAngleStep) {
//			analysisImage.setImage(rotateImage(analysisImage.getImage(), ((Math.PI / 180) * -blockRotationAngle), exOcrWordBounds));
			BufferedImage image = rotateImage(ai.image, ((Math.PI / 180) * -blockRotationAngle), exOcrWordBounds);
			BufferedImage backgroundImage = ((ai.backgroundImage == null) ? null : rotateImage(ai.backgroundImage, ((Math.PI / 180) * -blockRotationAngle), null));
			BufferedImage textImage = ((ai.textImage == null) ? null : rotateImage(ai.textImage, ((Math.PI / 180) * -blockRotationAngle), null));
			ai.setImage(image, backgroundImage, textImage);
			rotatedBy -= ((Math.PI / 180) * blockRotationAngle);
			rotationCorrected = true;
		}
		
		//	finally ...
		ai.setRotatedBy(rotatedBy);
		return rotationCorrected;
	}
//	private static final double maxPageRotationAngle = ((Math.PI / 180) * 30); // 30�;
//	/* we have to use this limit, as in quite a few page images background
//	 * stains create patterns that create an angle around 90�, causing the page
//	 * image to be flipped by this angle, which we have to prevent */
	private static final double maxPageRotationCorrectionAngle = ((Math.PI / 180) * 12); // 12�
	/* we have to use a limit below 90� because in quite a few page images
	 * background stains or table columns create patterns that create an angle
	 * around 90�, causing the page image to be flipped by this angle, which we
	 * have to prevent; further, we have to stay below the italics angle of of
	 * common fonts because in sparsely populated pages, italics can result in
	 * peaks at their skew angle */
	private static final double maxPageRotationMeasurementAngle = ((Math.PI / 180) * 30); // 30�
	/* we need this extra threshold so table grids, etc. that might look like a
	 * 90� rotation do not hamper detecting an actual rotation angle that is way
	 * lower */
	
	private static double getPageRotationAngle(List peaks, int maxPeaks, int numBucks) {
		ArrayList aPeaks = new ArrayList();
		Collections.sort(peaks, new Comparator() {
			public int compare(Object p1, Object p2) {
				return -Double.compare(((Peak) p1).h, ((Peak) p2).h);
			}
		});
		if (maxPeaks < 2)
			maxPeaks = peaks.size();
		for (int p = 0; p < (Math.min(peaks.size(), maxPeaks)); p++)
			aPeaks.add(peaks.get(p));
		
		double[] aWeights = new double[numBucks];
		Arrays.fill(aWeights, 0);
		int[] aCounts = new int[aWeights.length];
		Arrays.fill(aCounts, 0);
		for (int p1 = 0; p1 < aPeaks.size(); p1++) {
			Peak peak = ((Peak) aPeaks.get(p1));
			for (int p2 = (p1+1); p2 < aPeaks.size(); p2++) {
				Peak cPeak = ((Peak) aPeaks.get(p2));
				double distx = peak.x - cPeak.x;
				double disty = peak.y - cPeak.y;
				int dist = ((int) Math.sqrt((distx * distx) + (disty * disty)));
				if (dist == 0)
					continue;
				double a = ((disty == 0) ? (Math.PI / 2) : Math.atan(distx / -disty));
				if (Math.abs(a) >= maxPageRotationMeasurementAngle)
					continue;
				int aBuck = ((int) ((a * aWeights.length) / Math.PI)) + (aWeights.length / 2) - 1;
				aWeights[aBuck] += (dist * peak.h * cPeak.h);
				aCounts[aBuck]++;
			}
		}
		
		double aWeightMax = 0;
		double aWeightMax2 = 0;
		double angle = 0;
		for (int a = 0; a < aWeights.length; a++) {
			if (aWeights[a] > aWeightMax) {
				angle = ((Math.PI * (a - aWeights.length/2 + 1)) / aWeights.length);
				aWeightMax2 = aWeightMax;
				aWeightMax = aWeights[a];
			}
			else if (aWeights[a] > aWeightMax2)
				aWeightMax2 = aWeights[a];
		}
		return angle;
	}
	
	private static final boolean DEBUG_LINE_FOCUSSING = false;
	/* Rationale behind rotation angle test: lower case letters create dark
	 * stripes across block, line gaps create bright stripes. The contrast
	 * between the two is strongest if page is absolutely horizontal, as then
	 * the two kinds of stripes are never in the same pixel row, preventing any
	 * blurring.
	 */
	private static final double invalidBlockRotationAngle = -360;
	private static final double minBlockRotationAngle = -2;
	private static final double maxBlockRotationAngle = 2;
	private static final double blockRotationAngleStep = 0.05;
	private static double getBlockRotationAngle(AnalysisImage ai, ImagePartRectangle block) {
		if (DEBUG_LINE_FOCUSSING)
			System.out.println("Testing rotation of block " + block.getId());
		
		//	generate list of rotation angles to test
		ArrayList testAngleList = new ArrayList();
		for (double angle = minBlockRotationAngle; angle < (maxBlockRotationAngle + (blockRotationAngleStep / 2)); angle += blockRotationAngleStep)
			testAngleList.add(new Double(angle));
		double[] testAngles = new double[testAngleList.size()];
		for (int a = 0; a < testAngleList.size(); a++) {
			testAngles[a] = ((Double) testAngleList.get(a)).doubleValue();
			testAngles[a] = (((double) Math.round(testAngles[a] * 1000)) / 1000);
//			System.out.println("Angle " + testAngles[a] + "� = " + ((Math.PI / 180) * testAngles[a]));
		}
		
		//	test rotation angles, and identify best one
		byte[][] brightness = ai.getBrightness();
		double rotationAngle = invalidBlockRotationAngle;
		int rotationAngleScore = 0;
		for (int a = 0; a < testAngles.length; a++) {
			
			//	compute vertical offset for each column
			int[] colOffsets = new int[block.getWidth()];
			double maxOffset = (block.getWidth() * Math.sin(((Math.PI / 180) * testAngles[a])));
			for (int c = 0; c < colOffsets.length; c++)
				colOffsets[c] = ((int) Math.round((maxOffset * c) / block.getWidth()));
			
			//	compute brightness for each row, as well as overall
			int brightnessSum = 0;
			int[] rowBrightnessSums = new int[block.getHeight()];
			Arrays.fill(rowBrightnessSums, 0);
			for (int c = 0; c < block.getWidth(); c++) {
				for (int r = 0; r < block.getHeight(); r++) {
					int or = r + colOffsets[c];
					byte b = (((or < 0) || (block.getHeight() <= or)) ? 127 : brightness[c + block.getLeftCol()][or + block.getTopRow()]);
					brightnessSum += b;
					rowBrightnessSums[r] += b;
				}
			}
			
			if (DEBUG_LINE_FOCUSSING) {
				System.out.println("Row brightness for " + testAngles[a] + "� = " + ((Math.PI / 180) * testAngles[a]) + ":");
				System.out.print(" - row brightness:");
			}
			
			//	compute minimum, maximum, and average row brightness, as well as contrast (distance of individual row brightnesses from average)
			int blockContentTopRow = block.getTopRow();
			int blockContentBottomRow = block.getTopRow();
			int minRowBrightness = Byte.MAX_VALUE;
			int maxRowBrightness = 0;
			int avgRowBrightness = (brightnessSum / (block.getWidth() * block.getHeight()));
			int avgDistSquareSum = 0;
			int rowBrightnessDistSum = 0;
			int rowBrightnessDistSquareSum = 0;
			for (int r = 0; r < block.getHeight(); r++) {
				int rowBrightness = ((rowBrightnessSums[r] + (block.getWidth() / 2)) / block.getWidth());
				if (DEBUG_LINE_FOCUSSING) System.out.print(" " + rowBrightness);
				minRowBrightness = Math.min(minRowBrightness, rowBrightness);
				maxRowBrightness = Math.max(maxRowBrightness, rowBrightness);
				avgDistSquareSum += ((rowBrightness - avgRowBrightness) * (rowBrightness - avgRowBrightness));
				if (r != 0) {
					int prevRowBrightness = ((rowBrightnessSums[r-1] + (block.getWidth() / 2)) / block.getWidth());
					rowBrightnessDistSum += Math.abs(rowBrightness - prevRowBrightness);
					rowBrightnessDistSquareSum += ((rowBrightness - prevRowBrightness) * (rowBrightness - prevRowBrightness));
				}
				if (rowBrightness < 126)
					blockContentBottomRow = (block.getTopRow() + r + 1);
				else if (blockContentTopRow == (block.getTopRow() + r))
					blockContentTopRow++;
			}
			int blockContentHeight = (blockContentBottomRow - blockContentTopRow);
			if (DEBUG_LINE_FOCUSSING) {
				System.out.println();
				System.out.println(" - min is " + minRowBrightness);
				System.out.println(" - max is " + maxRowBrightness);
				System.out.println(" - avg is " + avgRowBrightness);
//				System.out.println(" - avg square distance to avg " + ((avgDistSquareSum + (block.getHeight() / 2)) / block.getHeight()));
//				System.out.println(" - avg distance between rows " + ((rowBrightnessDistSum + (block.getHeight() / 2)) / block.getHeight()));
//				System.out.println(" - avg square distance between rows " + ((rowBrightnessDistSquareSum + (block.getHeight() / 2)) / block.getHeight()));
				System.out.println(" - content height is " + blockContentHeight + " of " + block.getHeight());
				System.out.println(" - avg square distance to avg " + ((avgDistSquareSum + (blockContentHeight / 2)) / blockContentHeight));
				System.out.println(" - avg distance between rows " + ((rowBrightnessDistSum + (blockContentHeight / 2)) / blockContentHeight));
				System.out.println(" - avg square distance between rows " + ((rowBrightnessDistSquareSum + (blockContentHeight / 2)) / blockContentHeight));
			}
			
			//	is this angle significant?
//			if (((rowBrightnessDistSquareSum + (block.getHeight() / 2)) / block.getHeight()) < ((maxRowBrightness - minRowBrightness) / 2)) {
			if (((rowBrightnessDistSquareSum + (blockContentHeight / 2)) / blockContentHeight) < ((maxRowBrightness - minRowBrightness) / 2)) {
				if (DEBUG_LINE_FOCUSSING) System.out.println(" --> unsafe");
				continue;
			}
			
			//	do we have a new top angle?
//			int testAngleScore = ((avgDistSquareSum + (block.getHeight() / 2)) / block.getHeight());
			int testAngleScore = ((avgDistSquareSum + (blockContentHeight / 2)) / blockContentHeight);
			if (testAngleScore > rotationAngleScore) {
				if (DEBUG_LINE_FOCUSSING) System.out.println(" --> new top angle");
				rotationAngle = testAngles[a];
				rotationAngleScore = testAngleScore;
			}
		}
		
		//	finally ...
		return rotationAngle;
	}
	
	/**
	 * Rotate an image by a given angle.
	 * @param image the image to rotate
	 * @param angle the angle to rotate by (in radiants)
	 * @return the rotated image
	 */
	public static BufferedImage rotateImage(BufferedImage image, double angle) {
		return rotateImage(image, angle, null);
	}
	
	/**
	 * Rotate an image by a given angle. If the argument OCR word boundaries
	 * are not null, they are rotated alongside the image and placed in the
	 * array in the order they come in. The bounding boxes have to be in the
	 * same coordinate system and resolution as the argument image proper for
	 * results to be meaningful.
	 * @param image the image to rotate
	 * @param angle the angle to rotate by (in radiants)
	 * @param exOcrWordBounds an array holding the bounding boxes of existing
	 *            OCR words
	 * @return the rotated image
	 */
	public static BufferedImage rotateImage(BufferedImage image, double angle, BoundingBox[] exOcrWordBounds) {
		double aAngle = angle;
		while (aAngle < 0)
			aAngle += (2 * Math.PI);
		if (aAngle > Math.PI)
			aAngle -= Math.PI;
		boolean flipDimensions = (((Math.PI / 4) < aAngle) && (aAngle < ((Math.PI * 3) / 4)));
		BufferedImage rImage;
		if (flipDimensions)
			rImage = new BufferedImage(image.getHeight(), image.getWidth(), ((image.getType() == BufferedImage.TYPE_CUSTOM) ? BufferedImage.TYPE_INT_ARGB : image.getType()));
		else rImage = new BufferedImage(image.getWidth(), image.getHeight(), ((image.getType() == BufferedImage.TYPE_CUSTOM) ? BufferedImage.TYPE_INT_ARGB : image.getType()));
		Graphics2D rImageGraphics = rImage.createGraphics();
		rImageGraphics.setColor(Color.WHITE);
		rImageGraphics.fillRect(0, 0, rImage.getWidth(), rImage.getHeight());
		if (flipDimensions)
			rImageGraphics.translate(((rImage.getWidth() - image.getWidth()) / 2), ((rImage.getHeight() - image.getHeight()) / 2));
		rImageGraphics.rotate(angle, (image.getWidth() / 2), (image.getHeight() / 2));
		rImageGraphics.drawRenderedImage(image, null);
		rImageGraphics.dispose();
		if (exOcrWordBounds == null)
			return rImage; // no words to deal with, we're done here
		AffineTransform wcat = AffineTransform.getRotateInstance(angle, (image.getWidth() / 2), (image.getHeight() / 2));
		for (int w = 0; w < exOcrWordBounds.length; w++) {
			if (exOcrWordBounds[w] == null)
				continue;
			Point2D wc = new Point2D.Float((((float) (exOcrWordBounds[w].left + exOcrWordBounds[w].right)) / 2), (((float) (exOcrWordBounds[w].top + exOcrWordBounds[w].bottom)) / 2));
			wcat.transform(wc, wc);
			int rwl = (((int) Math.round(wc.getX())) - (exOcrWordBounds[w].getWidth() / 2));
			int rwt = (((int) Math.round(wc.getY())) - (exOcrWordBounds[w].getHeight() / 2));
			if ((rwl == exOcrWordBounds[w].left) && (rwt == exOcrWordBounds[w].top))
				continue;
			BoundingBox rwb = new BoundingBox(rwl, (rwl + exOcrWordBounds[w].getWidth()), rwt, (rwt + exOcrWordBounds[w].getHeight()));
			System.out.println("Rotated " + exOcrWordBounds[w] + " to " + rwb);
			exOcrWordBounds[w] = rwb;
		}
		return rImage;
	}
	
	/**
	 * Compute the baseline of a set of words. This method finds the row with
	 * the strongest negative difference in brightness to the row immediately
	 * below it, interpreting it as the lower rim of the letters, save for
	 * descends.
	 * @param words the words to analyze
	 * @param ai the image the words are on
	 * @return the baseline of the argument words
	 */
	public static int findBaseline(AnalysisImage ai, ImagePartRectangle[] words) {
		if (words.length == 0)
			return -1;
		
		int left = words[0].leftCol;
		int right = words[words.length-1].rightCol;
		int top = Integer.MAX_VALUE;
		int bottom = 0;
		for (int w = 0; w < words.length; w++) {
			top = Math.min(top, words[w].topRow);
			bottom = Math.max(bottom, words[w].bottomRow);
		}
		
		int height = (bottom - top);
		byte[][] brightness = ai.getBrightness();
		byte[] rowBrightnesses = new byte[height];
		for (int r = top; (r < bottom) && (r < brightness[0].length); r++) {
			int brightnessSum = 0;
			for (int c = left; (c < right) && (c < brightness.length); c++)
				brightnessSum += brightness[c][r];
			rowBrightnesses[r - top] = ((byte) (brightnessSum / (right - left)));
		}
		
		byte[] rowBrightnessDrops = new byte[height];
		for (int r = 0; r < height; r++)
			rowBrightnessDrops[r] = ((byte) (rowBrightnesses[r] - (((r+1) == height) ? 1 : rowBrightnesses[r+1])));
		byte maxRowBrightnessDrop = 0;
		int maxDropRow = -1;
		for (int r = (height-1); r > (height / 2); r--) {
			if (rowBrightnessDrops[r] < maxRowBrightnessDrop) {
				maxRowBrightnessDrop = rowBrightnessDrops[r];
				maxDropRow = r;
			}
		}
		
		return (top + maxDropRow);
	}
	
	/**
	 * Compute the FFT of complex vector, assuming its length is a power of 2.
	 * @param x the complex vector to transform
	 * @return the Fourier transform of the argument vector
	 */
	public static Complex[] computeFft(Complex[] x) {
		int N = x.length;

		// base case
		if (N == 1)
			return new Complex[] {x[0]};

		// radix 2 Cooley-Tukey FFT
		if (N % 2 != 0)
			throw new RuntimeException("N is not a power of 2");

		// fft of even terms
		Complex[] even = new Complex[N/2];
		for (int k = 0; k < N/2; k++)
			even[k] = x[2*k];
		Complex[] q = computeFft(even);

		// fft of odd terms
		Complex[] odd  = even;  // reuse the array
		for (int k = 0; k < N/2; k++)
			odd[k] = x[2*k + 1];
		Complex[] r = computeFft(odd);

		// combine
		Complex[] y = new Complex[N];
		for (int k = 0; k < N/2; k++) {
			double kth = -2 * k * Math.PI / N;
			Complex wk = new Complex(Math.cos(kth), Math.sin(kth));
			y[k]	   = q[k].plus(wk.times(r[k]));
			y[k + N/2] = q[k].minus(wk.times(r[k]));
		}
		return y;
	}
	
	/**
	 * Object representing a complex number, including basic functionality
	 * revolving around complex numbers.
	 * 
	 * @author sautter
	 */
	public static class Complex {
		/** the real part */
		public final double re;   // the real part
		/** the imaginary part */
		public final double im;   // the imaginary part

		// create a new object with the given real and imaginary parts
		/**
		 * Constructor
		 * @param real the real part
		 * @param imag the imaginary part
		 */
		public Complex(double real, double imag) {
			re = real;
			im = imag;
		}

		/* (non-Javadoc)
		 * @see java.lang.Object#toString()
		 */
		public String toString() {
			if (im == 0) return re + "";
			if (re == 0) return im + "i";
			if (im <  0) return re + " - " + (-im) + "i";
			return re + " + " + im + "i";
		}

		// return abs/modulus/magnitude and angle/phase/argument
		public double abs() {
			return Math.hypot(re, im);
		}  // Math.sqrt(re*re + im*im)
		
		// return a new Complex object whose value is (this + b)
		public Complex plus(Complex b) {
			Complex a = this;			 // invoking object
			double real = a.re + b.re;
			double imag = a.im + b.im;
			return new Complex(real, imag);
		}

		// return a new Complex object whose value is (this - b)
		public Complex minus(Complex b) {
			Complex a = this;
			double real = a.re - b.re;
			double imag = a.im - b.im;
			return new Complex(real, imag);
		}

		// return a new Complex object whose value is (this * b)
		public Complex times(Complex b) {
			Complex a = this;
			double real = a.re * b.re - a.im * b.im;
			double imag = a.re * b.im + a.im * b.re;
			return new Complex(real, imag);
		}
	}
	
	/**
	 * Render an image for visualizing an FFT result.
	 * @param fft the FFT result
	 * @param adjustMode the peak adjustment mode
	 * @return an image visualizing the FFT
	 */
	public static BufferedImage getFftImage(Complex[][] fft, int adjustMode) {
		double max = getMax(fft, adjustMode);
		float b;
		double t;
		int rgb;
		int rrgb = Color.RED.getRGB();
		double rmin = max/2;
		int yrgb = Color.YELLOW.getRGB();
		double ymin = max/4;
//		int grgb = Color.GREEN.getRGB();
//		double gmin = max/8;
		BufferedImage tImage = new BufferedImage(fft.length, fft[0].length, BufferedImage.TYPE_INT_RGB);
		for (int x = 0; x < fft.length; x++) {
			for (int y = 0; y < fft[x].length; y++) {
				t = adjust(fft[x][y].abs(), adjustMode);
				b = ((float) (t / max));
				if (t > rmin)
					rgb = rrgb;
				else if (t > ymin)
					rgb = yrgb;
//				else if (t > gmin) {
//					rgb = grgb;
//				}
				else {
					if (b > 1)
						b = 1;
					else if (b < 0)
						b = 0;
					rgb = Color.HSBtoRGB(0f, 0f, b);
				}
				tImage.setRGB(((x + fft.length/2) % fft.length), ((y + fft[x].length/2) % fft[x].length), rgb);
			}
		}
		return tImage;
	}
	
	/**
	 * Enhance the contrast of a gray-sclae image. This method uses a simple
	 * for of contrast limited adaptive histogram equalization.
	 * @param ai the image to treat
	 * @param dpi the resolution of the image
	 * @return true if the image was modified, false otherwise
	 */
	public static boolean enhanceContrast(AnalysisImage ai, int dpi, int ignoreThreshold) {
		//	TODO figure out if threshold makes sense
		
		//	get brightness array
		byte[][] brightness = ai.getBrightness();
		if ((brightness.length == 0) || (brightness[0].length == 0))
			return false;
		
		//	compute number and offset of tiles
		int ts = (dpi / 10); // TODO play with denominator
		int htc = ((brightness.length + ts - 1) / ts);
		int hto = (((htc * ts) - brightness.length) / 2);
		int vtc = ((brightness[0].length + ts - 1) / ts);
		int vto = (((vtc * ts) - brightness[0].length) / 2);
		
		//	compute tiles
		ImageTile[][] tiles = new ImageTile[htc][vtc];
		for (int ht = 0; ht < tiles.length; ht++) {
			int tl = Math.max(((ht * ts) - hto), 0);
			int tr = Math.min((((ht+1) * ts) - hto), brightness.length);
			for (int vt = 0; vt < tiles[ht].length; vt++) {
				int tt = Math.max(((vt * ts) - vto), 0);
				int tb = Math.min((((vt+1) * ts) - vto), brightness[0].length);
				tiles[ht][vt] = new ImageTile(tl, tr, tt, tb);
			}
		}
		
		//	compute min and max brightness for each tile TODO consider using min and max quantile (e.g. 5%) instead of absolute min and max
		for (int ht = 0; ht < tiles.length; ht++)
			for (int vt = 0; vt < tiles[ht].length; vt++) {
				ImageTile tile = tiles[ht][vt];
				for (int c = tile.left; c < tile.right; c++)
					for (int r = tile.top; r < tile.bottom; r++) {
						if (ignoreThreshold <= brightness[c][r])
							continue;
						tile.minBrightness = ((byte) Math.min(tile.minBrightness, brightness[c][r]));
						tile.maxBrightness = ((byte) Math.max(tile.maxBrightness, brightness[c][r]));
					}
			}
		
		//	enhance contrast
		int radius = 2; // TODO play with radius
		for (int ht = 0; ht < tiles.length; ht++)
			for (int vt = 0; vt < tiles[ht].length; vt++) {
				ImageTile tile = tiles[ht][vt];
				
				//	compute min and max brightness for current tile and its neighborhood
				byte minBrightness = tile.minBrightness;
				byte maxBrightness = tile.maxBrightness;
				for (int htl = Math.max(0, (ht-radius)); htl <= Math.min((htc-1), (ht+radius)); htl++)
					for (int vtl = Math.max(0, (vt-radius)); vtl <= Math.min((vtc-1), (vt+radius)); vtl++) {
						minBrightness = ((byte) Math.min(minBrightness, tiles[htl][vtl].minBrightness));
						maxBrightness = ((byte) Math.max(maxBrightness, tiles[htl][vtl].maxBrightness));
					}
				
				//	nothing we can do about this one ...
				if (maxBrightness <= minBrightness)
					continue;
				
				//	avoid over-amplification of noise TODO play with threshold
				int minBrightnessDist = 48;
				if ((maxBrightness - minBrightness) < minBrightnessDist) {
					System.out.println("Limiting weak contrast " + minBrightness + "-" + maxBrightness);
					int remainingBrightnessDist = (minBrightnessDist - (maxBrightness - minBrightness));
					int darkeningMinBrightnessDist = ((remainingBrightnessDist * minBrightness) / (127 - (maxBrightness - minBrightness)));
					int lighteningMaxBrighnessDist = (remainingBrightnessDist - darkeningMinBrightnessDist);
					minBrightness = ((byte) (minBrightness - darkeningMinBrightnessDist));
					maxBrightness = ((byte) (maxBrightness + lighteningMaxBrighnessDist));
					System.out.println(" ==> " + minBrightness + "-" + maxBrightness);
				}
				
				//	adjust image
				for (int c = tile.left; c < tile.right; c++)
					for (int r = tile.top; r < tile.bottom; r++) {
						int b = (((brightness[c][r] - minBrightness) * 127) / (maxBrightness - minBrightness));
						if (127 < b)
							b = 127;
						else if (b < 0)
							b = 0;
						brightness[c][r] = ((byte) b);
						ai.image.setRGB(c, r, Color.HSBtoRGB(0, 0, (((float) brightness[c][r]) / 127)));
						if (ai.backgroundImage != null)
							ai.backgroundImage.setRGB(c, r, Color.HSBtoRGB(0, 0, (((float) brightness[c][r]) / 127)));
					}
			}
		
		//	finally ...
		return true;
	}
	
	private static class ImageTile {
		final int left;
		final int right;
		final int top;
		final int bottom;
		byte minBrightness = ((byte) 127);
		byte maxBrightness = ((byte) 0);
		ImageTile(int left, int right, int top, int bottom) {
			this.left = left;
			this.right = right;
			this.top = top;
			this.bottom = bottom;
		}
	}
	
	private static class ImageData {
		String fileName;
		int dpi;
		ImageData(String fileName, int dpi) {
			this.fileName = fileName;
			this.dpi = dpi;
		}
	}
	
	/* TODO use region coloring for block detection
	 * - measure distance to next non-white pixel for all white pixels
	 * - use as boundary for region coloring if distance above some (DPI fraction based) threshold
	 * - regions are blocks, no matter of shape
	 * - use size to surface comparison to get rid of lines, etc.
	 * 
	 * PUT THIS IN PageImageAnalysis WHEN DONE
	 */
	
	private static ImageData[] testImages = {
		new ImageData("BEC_1890.pdf.0.png", 200),
		new ImageData("torminosus1.pdf.0.png", 150),
		new ImageData("Mellert_et_al_2000.pdf.2.png", 600),
		new ImageData("Mellert_et_al_2000.pdf.3.png", 600),
		new ImageData("AcaxanthumTullyandRazin1970.pdf.1.png", 600),
		new ImageData("AcaxanthumTullyandRazin1970.pdf.2.png", 600),
		new ImageData("Darwiniana_V19_p553.pdf.0.png", 150),
		new ImageData("Schulz_1997.pdf.5.png", 400),
		new ImageData("23416.pdf.207.png", 300),
		new ImageData("Forsslund1964.pdf.3.png", 300),
		new ImageData("5834.pdf.2.png", 300),
		new ImageData("AcaxanthumTullyandRazin1970.pdf.4.png", 600),
		new ImageData("015798000128826.pdf.2.png", 200),
		new ImageData("Forsslund1964.pdf.1.png", 300),
		new ImageData("Menke_Cerat_1.pdf.12.png", 300),
		new ImageData("fm__1_4_295_6.pdf.0.png", 300),
		new ImageData("fm__1_4_295_6.pdf.1.png", 300),
		new ImageData("fm__1_4_295_6.pdf.2.png", 300),
		new ImageData("Ceratolejeunea_Lejeuneaceae.mono.pdf.0.png", 200),
		new ImageData("chenopodiacea_hegi_270_281.pdf.6.png", 300),
		new ImageData("Prasse_1979_1.pdf.6.png", 300),
		new ImageData("Prasse_1979_1.pdf.9.png", 300),
		new ImageData("Prasse_1979_1.pdf.12.png", 300),
		new ImageData("Prasse_1979_1.pdf.13.png", 300),
	};
	
	/* test PDFs and images: TODO think of further odd cases
	 * - black and white, pages strewn with tiny spots: BEC_1890.pdf.0.png (200 dpi)
	 * - skewed page, somewhat uneven ink distribution: torminosus1.pdf.0.png (150 dpi)
	 * - tables with dotted grid lines, highlighted (darker) cells: Mellert_et_al_2000.pdf.2.png, Mellert_et_al_2000.pdf.3.png (also dirty, as scanned from copy) ==> VERY HARD CASE
	 * - two columns floating around image embedded in mid page: TODO
	 * - change between one and two columns: AcaxanthumTullyandRazin1970.pdf.1.png, AcaxanthumTullyandRazin1970.pdf.2.png
	 * - back frame around page: Darwiniana_V19_p553.pdf.0.png
	 * - black frame around two sides of page, punch holes: Schulz_1997.pdf.5.png
	 * - pencil stroke or fold through mid page: 23416.pdf.207.png
	 * - pencil stroke in text: Forsslund1964.pdf.3.png
	 * - two-columnish drawing with two-column caption: 5834.pdf.2.png
	 * - extremely small font: AcaxanthumTullyandRazin1970.pdf.4.png
	 * - uneven background, bleed-through: 015798000128826.pdf.2.png, Forsslund1964.pdf.1.png
	 * - uneven background with page fold at edge: Menke_Cerat_1.pdf.12.png
	 * - dark, uneven background, disturbed edges: fm__1_4_295_6.pdf.0.png, fm__1_4_295_6.pdf.1.png, fm__1_4_295_6.pdf.2.png
	 * - inverted images: ants_02732.pdf
	 * - wide dark edges, half of neighbor page: Ceratolejeunea_ Lejeuneaceae.mono.pdf.0.png
	 * - light figure, hard to distinguish from text: chenopodiacea_hegi_270_281.pdf.6.png
	 * 
	 * - extremely bad print quality (stenciled): Prasse_1979_1.pdf.6.png
	 * - extremely bad print quality (stenciled), grid-less tables: Prasse_1979_1.pdf.9.png
	 * - extremely bad print quality (stenciled), grid-less tables, folding lines: Prasse_1979_1.pdf.12.png, Prasse_1979_1.pdf.13.png
	 * 
	 * - change from one column to two and back: FOC-Loranthaceae.pdf.7.png (BORN-DIGITAL, MORE FOR STRUCTURING)
	 * - three-column, narrow gaps: Sansone-2012-44-121.pdf (BORN-DIGITAL, MORE FOR STRUCTURING)
	 * - two-column layout, three-column figure, grid-less tables: PlatnickShadab1979b.pdf.13.png (MORE FOR STRUCTURING)
	 * - multi-page grid-less table: SCZ634_Cairns_web_FINAL.pdf (pp 45-47, BORN-DIGITAL, MORE FOR STRUCTURING)
	 * - extremely text-like heading, grid-less table, partially Chinese: ZhangChen1994.pdf.2.png (MORE FOR STRUCTURING)
	 * - mix of one- and two-column layout, embedded footnotes, dividing lines: Wallace's line.pdf (BORN-DIGITAL, MORE FOR STRUCTURING)
	 */
	
	/* TODO try and _analyze_ page images first:
	 * - long horizontal and vertical lines
	 *   ==> TODO implement generalized pattern detection
	 */
	
	//	TODO compute that for all selected images, and put in Excel sheet for overview
	//	TODO plot some clean images against analysis results as basis for classification
	//	TODO store analysis results in AnalysisImage class for reuse
	
	private static final int normDpi = 300;
	private static StringTupel generateStats(String imageFileName, int dpi) {
		System.out.println("Generating stats for " + imageFileName);
		
		//	start stats
		StringTupel st = new StringTupel();
		st.setValue("imageName", imageFileName);
		st.setValue("imageDpi", ("" + dpi));
		
		//	load image
		BufferedImage bi;
		try {
			bi = ImageIO.read(new File("E:/Testdaten/PdfExtract", imageFileName));
		}
		catch (IOException ioe) {
			System.out.println("Error loading image '" + imageFileName + "':" + ioe.getMessage());
			ioe.printStackTrace(System.out);
			return st;
		}
		
		//	scale and adjust image is necessary
		if ((bi.getType() != BufferedImage.TYPE_BYTE_GRAY) || (dpi > normDpi)) {
			int scaleFactor = Math.max(1, ((dpi + normDpi - 1) / normDpi));
			BufferedImage abi = new BufferedImage((bi.getWidth() / scaleFactor), (bi.getHeight() / scaleFactor), BufferedImage.TYPE_BYTE_GRAY);
			Graphics2D ag = abi.createGraphics();
			ag.drawImage(bi, 0, 0, abi.getWidth(), abi.getHeight(), null);
			bi = abi;
			dpi /= scaleFactor;
		}
		st.setValue("analysisDpi", ("" + dpi));
		st.setValue("analysisSize", ("" + (bi.getWidth() * bi.getHeight())));
		
		//	wrap image
		AnalysisImage ai = wrapImage(bi, null);
		
		//	do white balance
		whitenWhite(ai);
		
		//	eliminate background
		//eliminateBackground(ai, dpi);
		//whitenWhite(ai);
		
		//	apply mild overall blur (about one third of a millimeter)
		//gaussBlur(ai, (dpi / 67));
		//whitenWhite(ai);
		
		//	apply mild horizontal blur (about half a millimeter)
		//gaussBlur(ai, (dpi / 50), 0);
		//whitenWhite(ai);
		
		//	apply strong horizontal blur (about five millimeters)
		//gaussBlur(ai, (dpi / 5), 0);
		//whitenWhite(ai);
		
		//	compute brightness distribution
		for (int bb = 8; bb <= 32; bb*=2) {
			int[] brightnessDist = getBrightnessDistribution(ai, bb);
			for (int b = 0; b < brightnessDist.length; b++)
				st.setValue(("brightnessDist" + bb + "-" + b), ("" + brightnessDist[b]));
		}
//		
//		//	compute region size distribution
//		int dpiFactor = dpi;
//		int normDpiFactor = normDpi;
//		for (int f = 2; (f < dpiFactor) && (f < normDpiFactor); f++)
//			while (((dpiFactor % f) == 0) && ((normDpiFactor % f) == 0)) {
//				dpiFactor /= f;
//				normDpiFactor /= f;
//			}
//		System.out.println("- DPI based region size norm factor is (" + dpiFactor + "/" + normDpiFactor + ")�");
//		int maxRegionSizeBucketCount = 16;
//		for (int bt = 1; bt <= 1/*16*/; bt*= 4)
//			for (int id = 0; id < 2; id++) {
//				System.out.println("- doing " + ((id == 1) ? "diagonal" : "straight") + " coloring with threshold " + (128 - bt));
//				int[][] regionCodes = getRegionColoring(ai, ((byte) (128 - bt)), (id == 1));
//				int regionCodeCount = 0;
//				for (int c = 0; c < regionCodes.length; c++) {
//					for (int r = 0; r < regionCodes[c].length; r++)
//						regionCodeCount = Math.max(regionCodeCount, regionCodes[c][r]);
//				}
//				regionCodeCount++; // account for 0
//				int[] regionSizes = new int[regionCodeCount];
//				Arrays.fill(regionSizes, 0);
//				for (int c = 0; c < regionCodes.length; c++) {
//					for (int r = 0; r < regionCodes[c].length; r++)
//						regionSizes[regionCodes[c][r]]++;
//				}
//				int regionSizeBucketCount = 1;
//				for (int rs = 1; rs < (regionCodes.length * regionCodes[0].length); rs*=2)
//					regionSizeBucketCount++;
//				regionSizeBucketCount = Math.min(maxRegionSizeBucketCount, regionSizeBucketCount);
//				int[] regionSizeBuckets = new int[regionSizeBucketCount];
//				int[] regionSizeBucketThresholds = new int[regionSizeBucketCount];
//				regionSizeBuckets[0] = 0;
//				regionSizeBucketThresholds[0] = 1;
//				for (int b = 1; b < regionSizeBuckets.length; b++) {
//					regionSizeBuckets[b] = 0;
//					regionSizeBucketThresholds[b] = (regionSizeBucketThresholds[b-1] * 2);
//				}
//				while ((regionSizeBucketCount == maxRegionSizeBucketCount) && (regionSizeBucketThresholds[maxRegionSizeBucketCount-1] < (regionCodes.length * regionCodes[0].length)))
//					regionSizeBucketThresholds[maxRegionSizeBucketCount-1] *= 2;
//				for (int r = 0; r < regionSizes.length; r++)
//					for (int b = 0; b < regionSizeBuckets.length; b++) {
//						int nRegionSize = ((regionSizes[r] * dpiFactor * dpiFactor) / (normDpiFactor * normDpiFactor));
//						if (nRegionSize <= regionSizeBucketThresholds[b]) {
//							regionSizeBuckets[b]++;
//							break;
//						}
//					}
//				for (int b = 0; b < regionSizeBuckets.length; b++)
//					st.setValue(("regionSizeDist" + ((id == 1) ? "D" : "S") + (128 - bt) + "-" + b), ("" + regionSizeBuckets[b]));
//				for (int b = regionSizeBuckets.length; b < maxRegionSizeBucketCount; b++)
//					st.setValue(("regionSizeDist" + ((id == 1) ? "D" : "S") + (128 - bt) + "-" + b), "0");
//			}
		
		//	finally ...
		return st;
	}
//	
//	//	FOR TESTS ONLY !!!
//	public static void main(String[] args) throws Exception {
//		
//		//	test several page images
//		String pageImageName;
//		pageImageName = "NHMD_LighthouseBirds_1887_15-17.pdf.0000.png";
////		pageImageName = "5834.pdf.0002.png";
//		
//		//	load page image
//		FileInputStream fis = new FileInputStream(new File("E:/Testdaten/PdfExtract", pageImageName));
//		PageImageInputStream piis = new PageImageInputStream(fis, null);
//		PageImage pi = new PageImage(piis);
//		fis.close();
//		
//		//	wrap page image
//		AnalysisImage ai = wrapImage(pi.image, null);
//		
//		//	slice and dice page image into blocks
//		ArrayList blocks = new ArrayList();
//		blocks.add(getContentBox(ai));
//		for (int blk = 0; blk < blocks.size(); blk++) {
//			ImagePartRectangle ipr = ((ImagePartRectangle) blocks.get(blk));
//			System.out.println("Splitting " + ipr.getId() + ":");
//			ArrayList iprParts = new ArrayList();
//			ImagePartRectangle[] iprBlocks = splitIntoRows(ipr, (pi.currentDpi / 8), maxBlockRotationAngle); // 3 mm
//			for (int b = 0; b < iprBlocks.length; b++) {
//				iprBlocks[b] = narrowLeftAndRight(iprBlocks[b]);
//				System.out.println(" - " + iprBlocks[b].getId());
//				ImagePartRectangle[] iprCols = splitIntoColumns(iprBlocks[b], (pi.currentDpi / 8), maxBlockRotationAngle, true); // 3 mm
//				for (int c = 0; c < iprCols.length; c++) {
//					iprCols[c] = narrowTopAndBottom(iprCols[c]);
//					System.out.println("   - " + iprCols[c].getId());
//					iprParts.add(iprCols[c]);
//				}
//			}
//			if (iprParts.size() > 1) {
//				blocks.addAll(iprParts);
//				blocks.remove(blk--);
//			}
//		}
//		System.out.println("Got " + blocks.size() + " blocks:");
//		for (int b = 0; b < blocks.size(); b++)
//			System.out.println(((ImagePartRectangle) blocks.get(b)).getId());
//		
//		//	for each block, check row brightness distribution in 0.1� steps, between -2� and 2�
//		double weightedBlockRotationAngleSum = 0.0;
//		int blockWeightSum = 0;
//		for (int b = 0; b < blocks.size(); b++) {
//			ImagePartRectangle block = ((ImagePartRectangle) blocks.get(b));
//			double blockRotationAngle = getBlockRotationAngle(ai, block);
//			int blockWeight = (block.getWidth() * block.getHeight());
//			weightedBlockRotationAngleSum += (blockRotationAngle * blockWeight);
//			blockWeightSum += blockWeight;
//		}
//		double pageRotationAngle = (weightedBlockRotationAngleSum / blockWeightSum);
//		System.out.println("Determined page rotation angle as " + pageRotationAngle + "� = " + ((Math.PI / 180) * pageRotationAngle));
//		
//		//	display what we got
//		BufferedImage bi = new BufferedImage(pi.image.getWidth(), pi.image.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
//		Graphics2D gr = bi.createGraphics();
//		gr.setColor(Color.WHITE);
//		gr.fillRect(0, 0, bi.getWidth(), bi.getHeight());
//		if (pageRotationAngle > blockRotationAngleStep) {
//			AffineTransform at = gr.getTransform();
//			gr.rotate(((Math.PI / 180) * -pageRotationAngle), (pi.image.getWidth() / 2), (pi.image.getHeight() / 2));
//			gr.drawImage(pi.image, 0, 0, pi.image.getWidth(), pi.image.getHeight(), null);
//			gr.setTransform(at);
//		}
//		else gr.drawImage(pi.image, 0, 0, pi.image.getWidth(), pi.image.getHeight(), null);
//		gr.setColor(Color.RED);
//		for (int b = 0; b < blocks.size(); b++) {
//			ImagePartRectangle block = ((ImagePartRectangle) blocks.get(b));
//			gr.drawRect(block.getLeftCol(), block.getTopRow(), block.getWidth(), block.getHeight());
//		}
//		final ImageDisplayDialog dialog = new ImageDisplayDialog("");
//		dialog.addImage(bi, "Page");
//		dialog.setSize(800, 1000);
//		dialog.setLocationRelativeTo(null);
//		Thread dialogThread = new Thread() {
//			public void run() {
//				dialog.setVisible(true);
//			}
//		};
//		dialogThread.start();
//	}
	
	//	ENHANCEMENT AFTER INDIVIDUAL SCAN REPAIR ONLY !!!
	public static void mainScanRepair(String[] args) throws Exception {
		String scanFileName;
		int dpi;
		String pageImageFileName;
		
		scanFileName = "EPHE.9.11-81.pdf.imf.data/scan@70.png"; dpi = 300;
		pageImageFileName = "EPHE.9.11-81.pdf.imf.data/page0070.png";
		int flags = 0;
//		flags |= INVERT_WHITE_ON_BLACK;
//		flags |= SMOOTH_LETTERS;
		flags |= ELIMINATE_BACKGROUND;
		flags |= WHITE_BALANCE;
		flags |= CLEAN_PAGE_EDGES;
//		flags |= REMOVE_SPECKLES;
//		flags |= CORRECT_ROTATION;
//		flags |= CORRECT_SKEW;
		
		//	load and wrap image
		BufferedImage pageImage = ImageIO.read(new File("E:/Testdaten/PdfExtract", scanFileName));
		int scaleFactor = 1;
		if ((pageImage.getType() != BufferedImage.TYPE_BYTE_GRAY) || (dpi > 300)) {
			scaleFactor = Math.max(1, ((dpi + 299) / 300));
			BufferedImage aPageImage = new BufferedImage((pageImage.getWidth() / scaleFactor), (pageImage.getHeight() / scaleFactor), BufferedImage.TYPE_BYTE_GRAY);
			Graphics2D ag = aPageImage.createGraphics();
			ag.drawImage(pageImage, 0, 0, aPageImage.getWidth(), aPageImage.getHeight(), null);
			pageImage = aPageImage;
			dpi /= scaleFactor;
		}
		BufferedImage oPageImage = cloneImage(pageImage);
		AnalysisImage ai = Imaging.wrapImage(pageImage, null);
		ai = Imaging.correctImage(ai, dpi, null, flags, ProgressMonitor.dummy);
		pageImage = ai.getImage();
		
		ImageDisplayDialog idd = new ImageDisplayDialog("Test result for " + scanFileName);
		idd.addImage(oPageImage, "Scan");
		idd.addImage(pageImage, "PageImage");
		idd.setSize(Math.min(1600, pageImage.getWidth()), Math.min(1000, pageImage.getHeight()));
		idd.setLocationRelativeTo(null);
		idd.setVisible(true);
		
		ImageIO.write(pageImage, "PNG", new File("E:/Testdaten/PdfExtract", pageImageFileName));
	}
	
	//	FOR TESTS ONLY !!!
	public static void main(String[] args) throws Exception {
//		if (true) {
//			StringRelation stats = new StringRelation();
//			for (int i = 0; i < testImages.length; i++) {
//				StringTupel st = generateStats(testImages[i].fileName, testImages[i].dpi);
//				stats.addElement(st);
//			}
//			File statsFile = new File("E:/Testdaten/PdfExtract", "TestImageStats.csv");
//			BufferedWriter statsBw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(statsFile)));
//			StringRelation.writeCsvData(statsBw, stats, ';', '"', true);
//			statsBw.flush();
//			statsBw.close();
//			return;
//		}
//		
		String imageFileName;
		int dpi;
		int scaleFactor = 1;
		
		//	feather dusting
//		imageFileName = "BEC_1890.pdf.0.png"; dpi = 200;
		
		//	background elimination
//		imageFileName = "Mellert_et_al_2000.pdf.2.png"; dpi = 600;
//		imageFileName = "Mellert_et_al_2000.pdf.3.png"; dpi = 600;
//		imageFileName = "fm__1_4_295_6.pdf.2.png"; dpi = 300;
//		imageFileName = "BEC_1890.pdf.0.png"; dpi = 200;
//		imageFileName = "Prasse_1979_1.pdf.6.png"; dpi = 300;
//		imageFileName = "Prasse_1979_1.pdf.9.png"; dpi = 300;
		imageFileName = "Prasse_1979_1.pdf.12.png"; dpi = 300;
//		imageFileName = "Prasse_1979_1.pdf.13.png"; dpi = 300;
		
		//	page skew correction
//		imageFileName = "torminosus1.pdf.0.png"; dpi = 150;
		
		//	dotted table grid
//		imageFileName = "Mellert_et_al_2000.pdf.2.png"; dpi = 400;
		
		//	distance coloring
//		imageFileName = "Mellert_et_al_2000.pdf.2.png"; dpi = 600;
//		imageFileName = "torminosus1.pdf.0.png"; dpi = 150;
//		imageFileName = "FOC-Loranthaceae.pdf.7.png"; dpi = 200;
//		imageFileName = "fm__1_4_295_6.pdf.0.png"; dpi = 200;
		
		//	load and wrap image
		BufferedImage bi = ImageIO.read(new File("E:/Testdaten/PdfExtract", imageFileName));
		LinkedList beforeImages = new LinkedList();
		if ((bi.getType() != BufferedImage.TYPE_BYTE_GRAY) || (dpi > 300)) {
			scaleFactor = Math.max(1, ((dpi + 299) / 300));
			BufferedImage abi = new BufferedImage((bi.getWidth() / scaleFactor), (bi.getHeight() / scaleFactor), BufferedImage.TYPE_BYTE_GRAY);
			Graphics2D ag = abi.createGraphics();
			ag.drawImage(bi, 0, 0, abi.getWidth(), abi.getHeight(), null);
			bi = abi;
			dpi /= scaleFactor;
		}
		BufferedImage obi = cloneImage(bi);
		AnalysisImage ai = wrapImage(bi, null);
		BufferedImage obirc = getRegionImage(ai, 127);
		
		//	TODO run test
		//featherDust(ai, dpi, true, true);
		//correctPageRotation(ai, 0.02, ADJUST_MODE_LOG); // doesn't do anything for small skews
		//gaussBlur(ai, 2);
		//whitenWhite(ai);
//		gaussBlur(ai, (dpi / 10));
		
//		//	subtract background from foreground
//		BufferedImage bebi = cloneImage(obi);
//		AnalysisImage beai = wrapImage(bebi, null);
//		byte[][] bBrightness = ai.getBrightness();
//		byte[][] beBrightness = beai.getBrightness();
//		for (int c = 0; c < beBrightness.length; c++)
//			for (int r = 0; r < beBrightness[c].length; r++) {
//				int b = 127 - ((127 - beBrightness[c][r]) - (127 - bBrightness[c][r]));
//				if (b < 0)
//					b = 0;
//				else if (b > 127)
//					b = 127;
//				beai.brightness[c][r] = ((byte) b);
//				if (beai.brightness[c][r] == 127)
//					beai.image.setRGB(c, r, white);
//			}
//		whitenWhite(beai);
//		gaussBlur(beai, 2);
//		whitenWhite(beai);
//		
//		eliminateBackground(ai, dpi);
//		whitenWhite(ai);
//		BufferedImage bebi = cloneImage(bi);
//		AnalysisImage beai = wrapImage(bebi, null);
//		//correctImage(ai, dpi, null);
//		for (int i = 0; i < 10; i++) {
//			gaussBlur(ai, (dpi / 67), 0);
//			whitenWhite(ai);
//		}
//		BufferedImage hbi = bi;
//		bi = cloneImage(bebi);
//		ai = wrapImage(bi, null);
//		for (int i = 0; i < 10; i++) {
//			gaussBlur(ai, 0, (dpi / 67));
//			whitenWhite(ai);
//		}
//		BufferedImage vbi = bi;
//		bi = cloneImage(bebi);
//		ai = wrapImage(bi, null);
//		
//		AnalysisImage hai = wrapImage(hbi, null);
//		gaussBlur(hai, (dpi / 50));
//		AnalysisImage vai = wrapImage(vbi, null);
//		gaussBlur(vai, (dpi / 50));
//		
//		byte[][] beBrightness = beai.getBrightness();
//		byte[][] hBrightness = hai.getBrightness();
//		byte[][] vBrightness = vai.getBrightness();
//		
//		BufferedImage rbi = cloneImage(bebi);
//		for (int c = 0; c < beBrightness.length; c++)
//			for (int r = 0; r < beBrightness[c].length; r++) {
//				int rCount = 0;
//				if (beBrightness[c][r] < 127)
//					rCount++;
//				if (hBrightness[c][r] < 127)
//					rCount++;
//				if (vBrightness[c][r] < 127)
//					rCount++;
//				if (rCount < 3 )
//					rbi.setRGB(c, r, white);
//			}
		
		/* THID LOOKS GOOD FOR ISOLATING TABLE GRIDS
		gaussBlur(ai, (dpi / 50), (dpi / 100));
		whitenWhite(ai);
		regionColorAndClean(ai, dpi, (dpi * 2), dpi, true, false);
		*/
//		
//		int[] rgbs = new int[128];
//		for (int i = 0; i < rgbs.length; i++) {
//			Color c = new Color((i*2), (i*2), (i*2));
//			rgbs[i] = c.getRGB();
//		}
//		byte[][] nonWhiteDistances = getBrightnessDistances(ai, ((byte) 127), ((byte) (dpi / 12)));
//		for (int c = 0; c < nonWhiteDistances.length; c++)
//			for (int r = 0; r < nonWhiteDistances[c].length; r++) {
//				if (nonWhiteDistances[c][r] != 0)
//					bi.setRGB(c, r, rgbs[nonWhiteDistances[c][r]]);
//			}
		
		//	eliminate background
		eliminateBackground(ai, dpi);
		BufferedImage bebi = cloneImage(bi);
		BufferedImage bebirc = getRegionImage(ai, 127);
		
		//	whiten remainder
		whitenWhite(ai);
		BufferedImage wbi = cloneImage(bi);
		BufferedImage wbirc = getRegionImage(ai, 127);
		
		//	enhance contrast
		enhanceContrast(ai, dpi, 128);
		BufferedImage cbi = cloneImage(bi);
		BufferedImage cbirc = getRegionImage(ai, 127);
		
		//	smooth image
		gaussBlur(ai, (dpi / 36), (dpi / 144), true);
		BufferedImage bbi = cloneImage(bi);
		BufferedImage bbirc = getRegionImage(ai, 127);
		
		//	make each region as dark as its darkest spot
		int[][] regionCodes = getRegionColoring(ai, ((byte) 127), true);
		int maxRegionCode = 0;
		for (int c = 0; c < regionCodes.length; c++) {
			for (int r = 0; r < regionCodes[c].length; r++)
				maxRegionCode = Math.max(maxRegionCode, regionCodes[c][r]);
		}
		byte[] minRegionBrightness = new byte[maxRegionCode];
		Arrays.fill(minRegionBrightness, ((byte) 127));
		byte[][] brightness = ai.getBrightness();
		for (int c = 0; c < regionCodes.length; c++)
			for (int r = 0; r < regionCodes[c].length; r++) {
				if (regionCodes[c][r] != 0)
					minRegionBrightness[regionCodes[c][r]-1] = ((byte) Math.min(minRegionBrightness[regionCodes[c][r]-1], brightness[c][r]));
			}
		for (int c = 0; c < regionCodes.length; c++) {
			for (int r = 0; r < regionCodes[c].length; r++)
				if (regionCodes[c][r] != 0) {
					brightness[c][r] = minRegionBrightness[regionCodes[c][r]-1];
					ai.image.setRGB(c, r, Color.HSBtoRGB(0, 0, (((float) brightness[c][r]) / 127)));
				}
			}
		
		//	eliminate light regions
		whitenWhite(ai);
		BufferedImage dbi = cloneImage(bi);
		BufferedImage dbirc = getRegionImage(ai, 127);
		
		//	AND-combine result image with original image
		brightness = ai.getBrightness();
		for (int c = 0; c < brightness.length; c++) {
			for (int r = 0; r < brightness[c].length; r++)
//				ai.image.setRGB(c, r, ((brightness[c][r] == 127) ? white : obi.getRGB(c, r)));
				ai.image.setRGB(c, r, ((brightness[c][r] == 127) ? white : bebi.getRGB(c, r)));
		}
		ai.brightness = null;
		gaussBlur(ai, 1, true);
		BufferedImage acbi = cloneImage(bi);
		BufferedImage acbirc = getRegionImage(ai, 127);
		
		//	enhance contrast
		//enhanceContrast(ai, dpi, 120);
		//	contrast enhancement seems to do more harm than good
		//	==> TODO think of something else to make letters darker, but only letters
		
		BufferedImage birc = getRegionImage(ai, 127);
//		gaussBlur(ai, (dpi / 20), (dpi / 25), true); // for region coloring, we have to blur with 1/4 the designed minimum distance (diameter of blur x2), and 2/3 of that to counter the x3 enlarged kernel radius
//		int[][] regionCodes = getRegionColoring(ai, ((byte) 127), true);
//		int maxRegionCode = 0;
//		for (int c = 0; c < regionCodes.length; c++) {
//			for (int r = 0; r < regionCodes[c].length; r++)
//				maxRegionCode = Math.max(maxRegionCode, regionCodes[c][r]);
//		}
//		System.out.println("Got " + maxRegionCode + " regions");
//		int[] rgbs = new int[maxRegionCode];
//		for (int c = 0; c < rgbs.length; c++)
//			rgbs[c] = Color.HSBtoRGB((((float) c) / rgbs.length), 1.0f, 0.5f);
//		BufferedImage rcbi = new BufferedImage(bi.getWidth(), bi.getHeight(), BufferedImage.TYPE_INT_RGB);
//		for (int c = 0; c < regionCodes.length; c++)
//			for (int r = 0; r < regionCodes[c].length; r++) {
//				if (regionCodes[c][r] == 0)
//					rcbi.setRGB(c, r, white);
//				else rcbi.setRGB(c, r, rgbs[regionCodes[c][r]-1]);
//			}
		
		//	display result
		ImageDisplayDialog idd = new ImageDisplayDialog("Test result for " + imageFileName);
//		int biCount = beforeImages.size();
//		while (beforeImages.size() != 0)
//			idd.addImage(((BufferedImage) beforeImages.removeFirst()), ("Before " + (biCount - beforeImages.size())));
		idd.addImage(obi, "Before");
		idd.addImage(obirc, "Before Regions");
		idd.addImage(bebi, "Background Gone");
		idd.addImage(bebirc, "Background Gone Regions");
		idd.addImage(wbi, "Whitened");
		idd.addImage(wbirc, "Whitened Regions");
		idd.addImage(cbi, "Contrast Enhanced");
		idd.addImage(cbirc, "Contrast Enhanced Regions");
//		idd.addImage(hbi, "Horizontal Blur");
//		idd.addImage(vbi, "Vertical Blur");
//		idd.addImage(rbi, "After");
		idd.addImage(bbi, "Blurred");
		idd.addImage(bbirc, "Blurred Regions");
		idd.addImage(dbi, "Darkened");
		idd.addImage(dbirc, "Darkened Regions");
//		idd.addImage(rcbi, "Regions");
		idd.addImage(acbi, "Recombined");
		idd.addImage(acbirc, "Recombined Regions");
		idd.addImage(bi, "After");
		idd.addImage(birc, "After Regions");
		idd.setSize(Math.min(1600, bi.getWidth()), Math.min(1000, bi.getHeight()));
		idd.setLocationRelativeTo(null);
		idd.setVisible(true);
	}
	private static BufferedImage cloneImage(BufferedImage bi) {
		BufferedImage cbi = new BufferedImage(bi.getWidth(), bi.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
		Graphics2D cg = cbi.createGraphics();
		cg.drawImage(bi, 0, 0, cbi.getWidth(), cbi.getHeight(), null);
		return cbi;
	}
	private static BufferedImage getRegionImage(AnalysisImage ai, int brightnessThreshold) {
		int[][] regionCodes = getRegionColoring(ai, ((byte) brightnessThreshold), true);
		int maxRegionCode = 0;
		for (int c = 0; c < regionCodes.length; c++) {
			for (int r = 0; r < regionCodes[c].length; r++)
				maxRegionCode = Math.max(maxRegionCode, regionCodes[c][r]);
		}
		System.out.println("Got " + maxRegionCode + " regions");
		int[] rgbs = new int[maxRegionCode];
		for (int c = 0; c < rgbs.length; c++)
			rgbs[c] = Color.HSBtoRGB((((float) c) / rgbs.length), 1.0f, 0.5f);
		shuffleArray(rgbs);
		BufferedImage rcbi = new BufferedImage(ai.image.getWidth(), ai.image.getHeight(), BufferedImage.TYPE_INT_RGB);
		for (int c = 0; c < regionCodes.length; c++)
			for (int r = 0; r < regionCodes[c].length; r++) {
				if (regionCodes[c][r] == 0)
					rcbi.setRGB(c, r, white);
				else rcbi.setRGB(c, r, rgbs[regionCodes[c][r]-1]);
			}
		return rcbi;
	}
	private static void shuffleArray(int[] ints) {
		for (int i = ints.length - 1; i > 0; i--) {
			int index = ((int) (Math.random() * ints.length));
			int a = ints[index];
			ints[index] = ints[i];
			ints[i] = a;
		}
	}
}