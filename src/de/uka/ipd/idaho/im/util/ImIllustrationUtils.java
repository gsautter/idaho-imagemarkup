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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.CompositeContext;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.imageio.ImageIO;

import de.uka.ipd.idaho.gamta.AnnotationUtils;
import de.uka.ipd.idaho.gamta.util.ProgressMonitor;
import de.uka.ipd.idaho.gamta.util.ProgressMonitor.CascadingProgressMonitor;
import de.uka.ipd.idaho.gamta.util.imaging.BoundingBox;
import de.uka.ipd.idaho.im.ImDocument;
import de.uka.ipd.idaho.im.ImLayoutObject;
import de.uka.ipd.idaho.im.ImPage;
import de.uka.ipd.idaho.im.ImRegion;
import de.uka.ipd.idaho.im.ImSupplement;
import de.uka.ipd.idaho.im.ImSupplement.Figure;
import de.uka.ipd.idaho.im.ImSupplement.Graphics;
import de.uka.ipd.idaho.im.ImSupplement.Graphics.Path;
import de.uka.ipd.idaho.im.ImSupplement.Graphics.SubPath;
import de.uka.ipd.idaho.im.ImSupplement.Scan;
import de.uka.ipd.idaho.im.ImWord;
import de.uka.ipd.idaho.im.analysis.Imaging;
import de.uka.ipd.idaho.im.analysis.Imaging.AnalysisImage;
import de.uka.ipd.idaho.im.utilities.ImageDisplayDialog;

/**
 * Utility library for extracting illustrations in Image Markup documents.
 * 
 * @author sautter
 */
public class ImIllustrationUtils {
	/*
Also add method converting scan excerpt into figure supplement
==> useful starting point for image editor overlay
==> provide latter from IllustrationActionProvider

Scan excerpt editor overlay:
- provide basic image edit tools (potentially copy):
  - erasers of different shapes and sizes
  - pencil
  - line drawing
  - color selection and color extraction
- flood filling with variable difference threshold:
  - both brightness based (grayscale) and full color (RGB)
  - provide preview function to help visualize effect
- rotating image
- warping image
==> best provide tools as plug-ins to IllustrationActionProvider
- maybe allow adjusting dimensions to include more or less of scan in frame
  ==> helps correct cut-out problems
  ==> adjust both underlying figure supplement and image region on commit ...
  ==> ... and also adjust target area of any assigned caption
- render as display overlay with somewhat wider frame
- pop up toolbar in non-modal dialog
  ==> prevents issues on scaling and scrolling

Add QC check for existence of caption targets ...
... and implement reactions in IllustrationActionProvider to adjust captions to target region changes:
- remove target pointer attributes on region removal ...
- ... but keep cached (per document and caption) ...
- ... and set to any other viable target region marked in an overlapping area
	 */
	
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
	public static BufferedImage renderCompositeImage(Figure[] figures, int scaleToDpi, Graphics[] graphics, ImWord[] words, ImPage page) {
		return renderCompositeImage(figures, scaleToDpi, graphics, words, page, BufferedImage.TYPE_INT_ARGB, ProgressMonitor.silent);
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
	 * @param pm a progress monitor to observe the rendering process
	 * @return the composed image
	 */
	public static BufferedImage renderCompositeImage(Figure[] figures, int scaleToDpi, Graphics[] graphics, ImWord[] words, ImPage page, ProgressMonitor pm) {
		return renderCompositeImage(figures, scaleToDpi, graphics, words, page, BufferedImage.TYPE_INT_ARGB, pm);
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
	public static BufferedImage renderCompositeImage(Figure[] figures, int scaleToDpi, Graphics[] graphics, ImWord[] words, ImPage page, int imageType) {
		return renderCompositeImage(figures, scaleToDpi, graphics, words, page, imageType, ProgressMonitor.silent);
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
	 * @param pm a progress monitor to observe the rendering process
	 * @return the composed image
	 */
	public static BufferedImage renderCompositeImage(Figure[] figures, int scaleToDpi, Graphics[] graphics, ImWord[] words, ImPage page, int imageType, ProgressMonitor pm) {
		if (pm == null)
			pm = ProgressMonitor.silent;
		
		//	compute export figure bounds in page resolution
		pm.setStep("Computing overall bounding box");
		pm.setBaseProgress(0);
		pm.setProgress(0);
		pm.setMaxProgress(5);
		BoundingBox peBounds = getCompositeBounds(figures, graphics, words);
		if (peBounds == null)
			return null;
		
		//	compute resolution if none specifically given
		if (scaleToDpi < 1) {
			pm.setStep("Measuring resolution");
			pm.setBaseProgress(5);
			pm.setProgress(0);
			pm.setMaxProgress(6);
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
		
		/* TODO maybe wipe out non-label words when extracting images from scans ...
		 * ... and filter non-label words when rendering export bitmaps from born-digital IMFs
		 * ==> shrink to non-filtered boundaries in latter case
		 *   ==> should readily handle weirdly shaped figures reported by Jeremy
		 *   ==> implement latter in ImSupplement aggregate rendering ...
		 *   ==> ... maybe controlled by additional boolean argument
		 */
		
		//	translate any words to export bounds
		pm.setStep("Translating label words");
		pm.setBaseProgress(6);
		pm.setProgress(0);
		pm.setMaxProgress(10);
		BoundingBox[] peWordBounds = new BoundingBox[0];
		if (words != null) {
			peWordBounds = new BoundingBox[words.length];
			for (int w = 0; w < words.length; w++) {
				pm.setProgress((w * 100) / words.length);
				peWordBounds[w] = words[w].getBounds().translate(-peBounds.left, -peBounds.top);
			}
		}
		
		//	scale everything to export resolution
		pm.setStep("Scaling object sizes");
		pm.setBaseProgress(10);
		pm.setProgress(0);
		pm.setMaxProgress(15);
		float boundsScale = (((float) scaleToDpi) / page.getImageDPI());
		BoundingBox eBounds = peBounds.translate(-peBounds.left, -peBounds.top).scale(boundsScale);
		BoundingBox[] eWordBounds = new BoundingBox[peWordBounds.length];
		for (int w = 0; w < peWordBounds.length; w++) {
			pm.setProgress((w * 100) / peWordBounds.length);
			eWordBounds[w] = peWordBounds[w].scale(boundsScale);
		}
		
		//	tray up figures and graphics or paths, and collect rendering order numbers
		pm.setStep("Collecting objects");
		pm.setBaseProgress(15);
		pm.setProgress(0);
		pm.setMaxProgress(20);
		int objectCount = (((figures == null) ? 0 : figures.length) + ((graphics == null) ? 0 : graphics.length));
		ArrayList objects = new ArrayList();
		HashSet objectRenderingOrderPositions = new HashSet();
		if (figures != null)
			for (int f = 0; f < figures.length; f++) {
				pm.setProgress((objects.size() * 100) / objectCount);
				BoundingBox peFigureBounds = figures[f].getBounds().translate(-peBounds.left, -peBounds.top);
				BoundingBox eFigureBounds = peFigureBounds.scale(boundsScale);
				BoundingBox peClipBounds = figures[f].getClipBounds().translate(-peBounds.left, -peBounds.top);
				BoundingBox eClipBounds = peClipBounds.scale(boundsScale);
				objects.add(new ObjectRenderingTray(figures[f], eFigureBounds, eClipBounds));
				objectRenderingOrderPositions.add(new Integer(figures[f].getRenderOrderNumber()));
			}
		if (graphics != null)
			for (int g = 0; g < graphics.length; g++) {
				pm.setProgress((objects.size() * 100) / objectCount);
				BoundingBox peGraphicsBounds = graphics[g].getBounds().translate(-peBounds.left, -peBounds.top);
				BoundingBox eGraphicsBounds = peGraphicsBounds.scale(boundsScale);
				BoundingBox peClipBounds = graphics[g].getClipBounds().translate(-peBounds.left, -peBounds.top);
				BoundingBox eClipBounds = peClipBounds.scale(boundsScale);
				
				//	without figures, we can render graphics objects as a whole
				if ((figures == null) || (figures.length == 0)) {
					objects.add(new ObjectRenderingTray(graphics[g], eGraphicsBounds, eClipBounds));
					objectRenderingOrderPositions.add(new Integer(graphics[g].getRenderOrderNumber()));
				}
				
				//	with figures present, we need to make sure individual paths and figures are rendered in the appropriate order
				else {
					Path[] paths = graphics[g].getPaths();
					for (int p = 0; p < paths.length; p++) {
						BoundingBox pePathBounds = paths[p].bounds.translate(-peBounds.left, -peBounds.top);
						BoundingBox ePathBounds = pePathBounds.scale(boundsScale);
						objects.add(new ObjectRenderingTray(paths[p], ePathBounds, graphics[g].getDpi(), eGraphicsBounds, eClipBounds));
						objectRenderingOrderPositions.add(new Integer(paths[p].renderOrderNumber));
					}
				}
			}
//		System.out.println("Got " + objects.size() + " images and " + objectRenderingOrderPositions.size() + " distinct rendering positions");
		
		//	sort trays, either by rendering order position (if we have sufficiently many distinct ones), or by size
		pm.setStep("Ordering objects");
		pm.setBaseProgress(20);
		pm.setProgress(0);
		pm.setMaxProgress(25);
		if ((objectRenderingOrderPositions.size() * 2) > objects.size()) {
//			System.out.println("Using rendering sequence order");
			Collections.sort(objects, renderingSequenceOrder);
		}
		else {
//			System.out.println("Using size order");
			Collections.sort(objects, imageSizeOrder);
		}
		
		//	set up rendering
		pm.setStep("Setting up canvas");
		pm.setBaseProgress(25);
		pm.setProgress(0);
		pm.setMaxProgress(30);
		BufferedImage image = new BufferedImage((eBounds.getWidth() + 4), (eBounds.getHeight() + 4), imageType);
		Graphics2D renderer = image.createGraphics();
		renderer.setColor(Color.WHITE);
		renderer.fillRect(0, 0, image.getWidth(), image.getHeight());
		renderer.translate(2, 2);
		renderer.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		
		//	render figures and graphics
		pm.setStep("Rendering objects");
		pm.setBaseProgress(30);
		pm.setProgress(0);
		pm.setMaxProgress((words == null) ? 100 : 90);
		for (int o = 0; o < objects.size(); o++) {
			pm.setProgress((o * 100) / objects.size());
			((ObjectRenderingTray) objects.get(o)).render(renderer, scaleToDpi, pm);
		}
		
		//	render words
		if (words != null) {
			pm.setStep("Rendering label words");
			pm.setBaseProgress(90);
			pm.setProgress(0);
			pm.setMaxProgress(100);
			for (int w = 0; w < words.length; w++) {
				pm.setProgress((w * 100) / words.length);
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
		pm.setBaseProgress(100);
		return image;
	}
	
	private static class ObjectRenderingTray {
		private Figure figure;
		private Graphics graphics;
		private Path path;
		private int sourceDpi;
		private BoundingBox renderingBounds;
		private BoundingBox clipBounds;
		final BoundingBox renderingArea;
		final int renderingOrderPrecedence;
		ObjectRenderingTray(Figure figure, BoundingBox renderingBounds, BoundingBox clipBounds) {
			this.figure = figure;
			this.sourceDpi = this.figure.getDpi();
			this.renderingBounds = renderingBounds;
			this.clipBounds = clipBounds;
			this.renderingArea = this.renderingBounds;
			this.renderingOrderPrecedence = 1;
//			System.out.println("Got Figure at " + this.renderingBounds.toString());
		}
		ObjectRenderingTray(Graphics graphics, BoundingBox renderingBounds, BoundingBox clipBounds) {
			this.graphics = graphics;
			this.sourceDpi = this.graphics.getDpi();
			this.renderingBounds = renderingBounds;
			this.clipBounds = clipBounds;
			this.renderingArea = this.renderingBounds;
			this.renderingOrderPrecedence = 1;
//			System.out.println("Got Graphics at " + this.renderingBounds.toString());
		}
		ObjectRenderingTray(Path path, BoundingBox renderingBounds, int parentGraphicsDpi, BoundingBox parentGraphicsRenderingBounds, BoundingBox parentGraphicsClipBounds) {
			this.path = path;
			this.sourceDpi = parentGraphicsDpi;
			this.renderingBounds = parentGraphicsRenderingBounds;
			this.clipBounds = parentGraphicsClipBounds;
			this.renderingArea = renderingBounds;
			this.renderingOrderPrecedence = ((path.getFillColor() == null) ? 2 : 0);
//			System.out.println("Got Path at " + this.renderingBounds.toString());
		}
		void render(Graphics2D renderer, int scaleToDpi, ProgressMonitor pm) {
			Shape preClip = renderer.getClip();
			AffineTransform preAt = renderer.getTransform();
			Composite preComp = renderer.getComposite();
			renderer.translate(this.renderingBounds.left, this.renderingBounds.top);
			pm.setInfo("Translated to " + this.renderingBounds.left + "/" + this.renderingBounds.top);
			
			if (this.graphics != null) {
				pm.setInfo("Rendering vector graphics at " + this.renderingBounds.toString());
				float renderScale = (((float) scaleToDpi) / this.sourceDpi);
				renderer.scale(renderScale, renderScale);
				
				if ((this.clipBounds != null) && !this.clipBounds.equals(this.renderingBounds)) {
					pm.setInfo(" - clipped to " + this.clipBounds.toString());
//					renderer.clipRect(this.clipBounds.left, this.clipBounds.top, this.clipBounds.getWidth(), this.clipBounds.getHeight());
					renderer.clipRect((this.clipBounds.left - this.renderingBounds.left), (this.clipBounds.top - this.renderingBounds.top), this.clipBounds.getWidth(), this.clipBounds.getHeight());
				}
				
				Path[] paths = this.graphics.getPaths();
				pm.setInfo(" - got " + paths.length + " paths");
				for (int p = 0; p < paths.length; p++) {
					Color preColor = renderer.getColor();
					Stroke preStroke = renderer.getStroke();
					Color strokeColor = paths[p].getStrokeColor();
					Stroke stroke = paths[p].getStroke();
					Color fillColor = paths[p].getFillColor();
					if ((strokeColor == null) && (fillColor == null))
						continue;
					pm.setInfo(" - rendering path at " + paths[p].getExtent());
					
					Path2D path = new Path2D.Float();
					Graphics.SubPath[] subPaths = paths[p].getSubPaths();
					pm.setInfo("   - got " + subPaths.length + " sub paths");
					for (int s = 0; s < subPaths.length; s++) {
						Path2D subPath = subPaths[s].getPath();
						path.append(subPath, false);
					}
					
					if (fillColor != null) {
						pm.setInfo("   - filling in " + fillColor);
						renderer.setColor(fillColor);
						renderer.fill(path);
					}
					if (strokeColor != null) {
						pm.setInfo("   - stroking in " + strokeColor + ", stroke is " + stroke);
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
				pm.setInfo("Rendering vector path at " + this.renderingBounds.toString());
				
				float renderScale = (((float) scaleToDpi) / this.sourceDpi);
				renderer.scale(renderScale, renderScale);
				
				Color preColor = renderer.getColor();
				Stroke preStroke = renderer.getStroke();
				Color strokeColor = this.path.getStrokeColor();
				Stroke stroke = this.path.getStroke();
				Color fillColor = this.path.getFillColor();
				
				if ((this.clipBounds != null) && !this.clipBounds.equals(this.renderingBounds)) {
					pm.setInfo(" - clipped to " + this.clipBounds.toString());
//					renderer.clipRect(this.clipBounds.left, this.clipBounds.top, this.clipBounds.getWidth(), this.clipBounds.getHeight());
					renderer.clipRect((this.clipBounds.left - this.renderingBounds.left), (this.clipBounds.top - this.renderingBounds.top), this.clipBounds.getWidth(), this.clipBounds.getHeight());
				}
				
				Path2D path = new Path2D.Float();
				Graphics.SubPath[] subPaths = this.path.getSubPaths();
				pm.setInfo(" - got " + subPaths.length + " sub paths");
				for (int s = 0; s < subPaths.length; s++) {
					Path2D subPath = subPaths[s].getPath();
					path.append(subPath, false);
				}
				
				if (fillColor != null) {
					pm.setInfo(" - filling in " + fillColor);
					renderer.setColor(fillColor);
					renderer.fill(path);
				}
				if (strokeColor != null) {
					pm.setInfo(" - stroking in " + strokeColor + ", stroke is " + stroke);
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
				pm.setInfo("Rendering figure at " + this.renderingBounds.toString());
				InputStream fImageIn = this.figure.getInputStream();
				BufferedImage fImage = ImageIO.read(fImageIn);
				fImageIn.close();
				pm.setInfo(" - bitmap image loaded");
//				NO NEED TO SCALE EXPLICITLY, draw() DOES THAT BY ITSELF
//				float hRenderScale = (((float) (eFigureBounds[f].right - eFigureBounds[f].left)) / fImage.getWidth());
//				float vRenderScale = (((float) (eFigureBounds[f].bottom - eFigureBounds[f].top)) / fImage.getHeight());
//				renderer.scale(hRenderScale, vRenderScale);
				if ((this.clipBounds != null) && !this.clipBounds.equals(this.renderingBounds)) {
					pm.setInfo(" - clipped to " + this.clipBounds.toString());
//					renderer.clipRect(this.clipBounds.left, this.clipBounds.top, this.clipBounds.getWidth(), this.clipBounds.getHeight());
					renderer.clipRect((this.clipBounds.left - this.renderingBounds.left), (this.clipBounds.top - this.renderingBounds.top), this.clipBounds.getWidth(), this.clipBounds.getHeight());
				}
				if (this.figure.hasAttribute(Figure.IMAGE_MASK_MARKER_ATTRIBUTE)) {
					pm.setInfo(" - rendering bitmap image mask");
					renderer.setComposite(imageMaskMultiplyComposite); // use multiply composite for image masks
				}
				else pm.setInfo(" - rendering bitmap image");
				try {
					renderer.drawImage(fImage, 0, 0, this.renderingBounds.getWidth(), this.renderingBounds.getHeight(), null);
				}
				catch (RuntimeException re) {
					pm.setInfo(" - failed to render bitmap image: " + re.getMessage());
					System.out.println("Failed to render figure at " + this.figure.getBounds() + ": " + re.getMessage());
					re.printStackTrace(System.out);
				}
			}
			catch (IOException ioe) {
				pm.setInfo(" - failed to load bitmap image: " + ioe.getMessage());
				System.out.println("Failed to load figure at " + this.figure.getBounds() + ": " + ioe.getMessage());
				ioe.printStackTrace(System.out);
			}
			
			renderer.setComposite(preComp);
			renderer.setTransform(preAt);
			renderer.setClip(preClip);
			pm.setInfo(" - done");
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
	
	//	stripped-down copy of PdfParser.BlendComposite (we only need 'Multiply' mode, for image masks)
	private static final Composite imageMaskMultiplyComposite = new Composite() {
		public CompositeContext createContext(ColorModel srcColorModel, ColorModel dstColorModel, RenderingHints hints) {
			return new MultiplyCompositeContext(srcColorModel, dstColorModel);
		}
	};
	private static class MultiplyCompositeContext implements CompositeContext {
		private ColorModel srcColorModel;
		private ColorModel dstColorModel;
		MultiplyCompositeContext(ColorModel srcColorModel, ColorModel dstColorModel) {
			this.srcColorModel = srcColorModel;
			this.dstColorModel = dstColorModel;
		}
		public void dispose() { /* nothing to dispose */ }
		public void compose(Raster src, Raster dstIn, WritableRaster dstOut) {
			if (dstIn.getSampleModel().getDataType() != dstOut.getSampleModel().getDataType())
				throw new IllegalArgumentException("Destination input and output must store pixels as the same type (composite is Multiply).");
			
			int width = Math.min(src.getWidth(), dstIn.getWidth());
			int height = Math.min(src.getHeight(), dstIn.getHeight());
			float alpha = 1.0f;
			
			int[] srcPixel = new int[4];
			int[] dstPixel = new int[4];
			Object srcPixels = this.getPixelRowArray(src.getSampleModel().getDataType(), width);
			Object dstPixels = this.getPixelRowArray(dstIn.getSampleModel().getDataType(), width);
			
			for (int y = 0; y < height; y++) {
				src.getDataElements(0, y, width, 1, srcPixels);
				dstIn.getDataElements(0, y, width, 1, dstPixels);
				for (int x = 0; x < width; x++) {
					int pixel;
					
					// pixels are stored as INT_ARGB
					// our arrays are [R, G, B, A]
					pixel = this.srcColorModel.getRGB(this.getPixel(srcPixels, x));
					srcPixel[0] = (pixel >> 16) & 0xFF;
					srcPixel[1] = (pixel >>  8) & 0xFF;
					srcPixel[2] = (pixel	  ) & 0xFF;
					srcPixel[3] = (pixel >> 24) & 0xFF;
					
					pixel = this.dstColorModel.getRGB(this.getPixel(dstPixels, x));
					dstPixel[0] = (pixel >> 16) & 0xFF;
					dstPixel[1] = (pixel >>  8) & 0xFF;
					dstPixel[2] = (pixel	  ) & 0xFF;
					dstPixel[3] = (pixel >> 24) & 0xFF;
					
					int[] result = this.blend(srcPixel, dstPixel);
					
					// mixes the result with the opacity
					pixel = ((int) (dstPixel[3] + (result[3] - dstPixel[3]) * alpha) & 0xFF) << 24 |
							((int) (dstPixel[0] + (result[0] - dstPixel[0]) * alpha) & 0xFF) << 16 |
							((int) (dstPixel[1] + (result[1] - dstPixel[1]) * alpha) & 0xFF) <<  8 |
							 (int) (dstPixel[2] + (result[2] - dstPixel[2]) * alpha) & 0xFF;
					this.setPixel(dstPixels, x, pixel);
				}
				dstOut.setDataElements(0, y, width, 1, dstPixels);
			}
		}
		private Object getPixelRowArray(int type, int length) {
			if (type == DataBuffer.TYPE_BYTE)
				return new byte[length];
			else if (type == DataBuffer.TYPE_SHORT)
				return new short[length];
			else if (type == DataBuffer.TYPE_USHORT)
				return new short[length];
			else return new int[length];
		}
		private int getPixel(Object pixelArray, int index) {
			if (pixelArray instanceof byte[]) {
				int pixelByte = (((byte[]) pixelArray)[index] & 0xFF);
				return (0xFF000000 | (pixelByte << 16) | (pixelByte << 8) | (pixelByte << 0));
			}
			else if (pixelArray instanceof short[]) {
				int pixelByte = (((short[]) pixelArray)[index] & 0xFFFF);
				pixelByte >>= 8;
				return (0xFF000000 | (pixelByte << 16) | (pixelByte << 8) | (pixelByte << 0));
			}
			else return ((int[]) pixelArray)[index];
		}
		private void setPixel(Object pixelArray, int index, int pixel) {
			if (pixelArray instanceof byte[]) {
				int red = ((pixel >> 16) & 0xFF);
				int green = ((pixel >> 8) & 0xFF);
				int blue = ((pixel >> 0) & 0xFF);
				int pixelByte = ((int) ((0.299f * red) + (0.587f * green) + (0.114f * blue)));
				((byte[]) pixelArray)[index] = ((byte) (pixelByte & 0xFF));
			}
			else if (pixelArray instanceof short[]) {
				int red = (((pixel >> 16) & 0xFF) << 8);
				int green = (((pixel >> 8) & 0xFF) << 8);
				int blue = (((pixel >> 0) & 0xFF) << 8);
				int pixelByte = ((int) ((0.299f * red) + (0.587f * green) + (0.114f * blue)));
				((short[]) pixelArray)[index] = ((short) (pixelByte & 0xFFFF));
			}
			else ((int[]) pixelArray)[index] = pixel;
		}
		private int[] blend(int[] src, int[] dst) {
			return new int[] {
				(src[0] * dst[0]) >> 8,
				(src[1] * dst[1]) >> 8,
				(src[2] * dst[2]) >> 8,
				Math.min(0xFF, src[3] + dst[3])
			};
		}
	}
//	
//	//	stripped-down copy of PdfParser.BlendComposite (we only need 'Multiply' mode, for image masks)
//	private static final Composite imageMaskMultiplyComposite = new Composite() {
//		public CompositeContext createContext(ColorModel srcColorModel, ColorModel dstColorModel, RenderingHints hints) {
//			return imageMaskMultiplyCompositeContext;
//		}
//	};
//	private static final CompositeContext imageMaskMultiplyCompositeContext = new CompositeContext() {
//		public void dispose() { /* we have no internal state */ }
//		public void compose(Raster src, Raster dstIn, WritableRaster dstOut) {
//			if (dstIn.getSampleModel().getDataType() != dstOut.getSampleModel().getDataType())
//				throw new IllegalArgumentException("Destination input and output must store pixels as the same type (composite is Multiply).");
//			
//			int width = Math.min(src.getWidth(), dstIn.getWidth());
//			int height = Math.min(src.getHeight(), dstIn.getHeight());
//			float alpha = 1.0f;
//			
//			int[] srcPixel = new int[4];
//			int[] dstPixel = new int[4];
//			Object srcPixels = this.getPixelRowArray(src.getSampleModel().getDataType(), width);
//			System.out.println("SRC is " + srcPixels);
//			Object dstPixels = this.getPixelRowArray(dstIn.getSampleModel().getDataType(), width);
//			System.out.println("DST is " + dstPixels);
//			
//			HashSet loggedCombinations = new HashSet();
//			for (int y = 0; y < height; y++) {
//				src.getDataElements(0, y, width, 1, srcPixels);
//				dstIn.getDataElements(0, y, width, 1, dstPixels);
//				for (int x = 0; x < width; x++) {
//					int pixel;
//					
//					// pixels are stored as INT_ARGB
//					// our arrays are [R, G, B, A]
//					pixel = getPixel(srcPixels, x);
//					srcPixel[0] = (pixel >> 16) & 0xFF;
//					srcPixel[1] = (pixel >>  8) & 0xFF;
//					srcPixel[2] = (pixel	  ) & 0xFF;
//					srcPixel[3] = (pixel >> 24) & 0xFF;
//					
//					pixel = getPixel(dstPixels, x);
//					dstPixel[0] = (pixel >> 16) & 0xFF;
//					dstPixel[1] = (pixel >>  8) & 0xFF;
//					dstPixel[2] = (pixel	  ) & 0xFF;
//					dstPixel[3] = (pixel >> 24) & 0xFF;
//					
//					int[] result = this.blend(srcPixel, dstPixel);
//					String combKey = (Arrays.toString(srcPixel) + "+" + Arrays.toString(dstPixel));
//					if (loggedCombinations.add(combKey)) {
//						System.out.println("SRC: " + Arrays.toString(srcPixel));
//						System.out.println("DST: " + Arrays.toString(dstPixel));
//						System.out.println("RES: " + Arrays.toString(result));
//					}
//					
//					// mixes the result with the opacity
//					pixel = ((int) (dstPixel[3] + (result[3] - dstPixel[3]) * alpha) & 0xFF) << 24 |
//							((int) (dstPixel[0] + (result[0] - dstPixel[0]) * alpha) & 0xFF) << 16 |
//							((int) (dstPixel[1] + (result[1] - dstPixel[1]) * alpha) & 0xFF) <<  8 |
//							 (int) (dstPixel[2] + (result[2] - dstPixel[2]) * alpha) & 0xFF;
//					this.setPixel(dstPixels, x, pixel);
//				}
//				dstOut.setDataElements(0, y, width, 1, dstPixels);
//			}
//		}
//		private Object getPixelRowArray(int type, int length) {
//			if (type == DataBuffer.TYPE_BYTE)
//				return new byte[length];
//			else if (type == DataBuffer.TYPE_SHORT)
//				return new short[length];
//			else if (type == DataBuffer.TYPE_USHORT)
//				return new short[length];
//			else return new int[length];
//		}
//		private int getPixel(Object pixelArray, int index) {
//			if (pixelArray instanceof byte[]) {
//				int pixelByte = (((byte[]) pixelArray)[index] & 0xFF);
//				return (0xFF000000 | (pixelByte << 16) | (pixelByte << 8) | (pixelByte << 0));
//			}
//			else if (pixelArray instanceof short[]) {
//				int pixelByte = (((short[]) pixelArray)[index] & 0xFFFF);
//				pixelByte >>= 8;
//				return (0xFF000000 | (pixelByte << 16) | (pixelByte << 8) | (pixelByte << 0));
//			}
//			else return ((int[]) pixelArray)[index];
//		}
//		private void setPixel(Object pixelArray, int index, int pixel) {
//			if (pixelArray instanceof byte[]) {
//				int red = ((pixel >> 16) & 0xFF);
//				int green = ((pixel >> 8) & 0xFF);
//				int blue = ((pixel >> 0) & 0xFF);
//				int pixelByte = ((int) ((0.299f * red) + (0.587f * green) + (0.114f * blue)));
//				((byte[]) pixelArray)[index] = ((byte) (pixelByte & 0xFF));
//			}
//			else if (pixelArray instanceof short[]) {
//				int red = (((pixel >> 16) & 0xFF) << 8);
//				int green = (((pixel >> 8) & 0xFF) << 8);
//				int blue = (((pixel >> 0) & 0xFF) << 8);
//				int pixelByte = ((int) ((0.299f * red) + (0.587f * green) + (0.114f * blue)));
//				((short[]) pixelArray)[index] = ((short) (pixelByte & 0xFFFF));
//			}
//			else ((int[]) pixelArray)[index] = pixel;
//		}
//		private int[] blend(int[] src, int[] dst) {
//			return new int[] {
//				(src[0] * dst[0]) >> 8,
//				(src[1] * dst[1]) >> 8,
//				(src[2] * dst[2]) >> 8,
//				Math.min(0xFF, src[3] + dst[3])
//			};
//		}
//	};
	
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
	public static CharSequence renderSvg(Graphics[] graphics, ImWord[] words, ImPage page) {
		return renderSvg(graphics, words, Graphics.RESOLUTION, page);
	}
	
	/**
	 * Produce SVG (Scalable Vector Graphics) XML from one or more
	 * <code>Graphics</code> objects, including labeling <code>ImWord</code>s.
	 * The nominal resolution of the returned SVG is the default 72 DPI. While
	 * there is no strict need for the arguments objects to lie in the argument
	 * page, the latter is the most sensible scenario.
	 * @param graphics the graphics to include
	 * @param words the words to include
	 * @param dpi the resolution to scale to
	 * @param page the page the other argument objects lie in
	 * @return the SVG XML representing the argument objects
	 */
	public static CharSequence renderSvg(Graphics[] graphics, ImWord[] words, int dpi, ImPage page) {
		
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
		float boundsScale = (((float) dpi) / page.getImageDPI());
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
		svg.append(" width=\"" + eBounds.getWidth() + "px\"");
		svg.append(" height=\"" + eBounds.getHeight() + "px\"");
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
		if ((bb.getWidth() < 1) || (bb.getHeight() < 1))
			return Color.BLACK;
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
	
	/**
	 * Rotate an image to horizontal/upright orientation, coming from a
	 * potentially deviating text direction. This method only performs full
	 * quadrant rotations, namely by 90°, -90°, and 180°. If the argument text
	 * direction is none of the constants from <code>ImLayoutObject</code>,
	 * this method returns the argument image unchanged.
	 * @param image the image to rotate
	 * @param textDirection the text direction of the image
	 * @return the upright image
	 */
	public static BufferedImage rotateImageToUpright(BufferedImage image, String textDirection) {
		return ImObjectTransformer.transformImage(image, ImObjectTransformer.getToUprightRotation(textDirection));
	}
	
	/**
	 * Object holding option for rendering an illustration, namely whether or
	 * not to include figure, verctor, graphics, and label words, as well as
	 * the resolution to render the illustration at.
	 * 
	 * @author sautter
	 */
	public static class RenderingOptions {
		
		/** indicate whether or not to include figures in the rendered output image */
		public final boolean includeFigures;
		
		/** indicate whether or not to include vector graphics in the rendered output image */
		public final boolean includeGraphics;
		
		/** indicate whether or not to include lable words in the rendered output image */
		public final boolean includeWords;
		
		/** the desired resolution of the rendered output image */
		public final int dpi;
		
		/**
		 * @param includeFigures include figures in the rendered output image?
		 * @param includeGraphics include vector graphics in the rendered output image?
		 * @param includeWords include lable words in the rendered output image?
		 * @param dpi desired resolution of the rendered output image
		 */
		public RenderingOptions(boolean includeFigures, boolean includeGraphics, boolean includeWords, int dpi) {
			this.includeFigures = includeFigures;
			this.includeGraphics = includeGraphics;
			this.includeWords = includeWords;
			this.dpi = dpi;
		}
		
		/**
		 * Provider of adjusted rendering options, to consult after measuring
		 * he possible values.
		 * 
		 * @author sautter
		 */
		public static interface Provider {
			
			/**
			 * Provide the rendering options to actually use for output. The
			 * arguments are measured from the input image data. If the number
			 * of figures is 0, the set of observed resolutions is empty.
			 * @param figureCount the number of bitmap figures
			 * @param graphicsCount the number of vector graphics objects
			 * @param wordCount the number of textual words
			 * @param dpis the observed bitmap resolutions
			 * @return the rendering options to use
			 */
			public abstract RenderingOptions getRenderingOptions(int figureCount, int graphicsCount, int wordCount, SortedSet dpis);
		}
	}
	
	/**
	 * Render an image, potentially consisting of multiple bitmap and vector
	 * graphics components, as well as labeling words. If there are no bitmap
	 * and vector graphics components, any underlying scan is used. If the
	 * argument resolution is less than 1, the highest resolution of any of the
	 * involved bitmaps components (or the underlying scan) will be used.
	 * @param image the image to render
	 * @param dpi the resolution to render the overall image in
	 * @param includeGraphics include vector based graphics?
	 * @param includeWords include label words?
	 * @return the overall image
	 */
	public static BufferedImage renderImage(ImRegion image, int dpi, boolean includeGraphics, boolean includeWords) {
		return renderImage(image, dpi, includeGraphics, includeWords, image.getDocument(), BufferedImage.TYPE_INT_ARGB, ProgressMonitor.silent);
	}
	
	/**
	 * Render an image, potentially consisting of multiple bitmap and vector
	 * graphics components, as well as labeling words. If there are no bitmap
	 * and vector graphics components, any underlying scan is used. If the
	 * argument resolution is less than 1, the highest resolution of any of the
	 * involved bitmaps components (or the underlying scan) will be used.
	 * @param image the image to render
	 * @param dpi the resolution to render the overall image in
	 * @param includeGraphics include vector based graphics?
	 * @param includeWords include label words?
	 * @param pm a progress monitor to observe the rendering process
	 * @return the overall image
	 */
	public static BufferedImage renderImage(ImRegion image, int dpi, boolean includeGraphics, boolean includeWords, ProgressMonitor pm) {
		return renderImage(image, dpi, includeGraphics, includeWords, image.getDocument(), BufferedImage.TYPE_INT_ARGB, pm);
	}
	
	/**
	 * Render an image, potentially consisting of multiple bitmap and vector
	 * graphics components, as well as labeling words. If there are no bitmap
	 * and vector graphics components, any underlying scan is used. If the
	 * argument resolution is less than 1, the highest resolution of any of the
	 * involved bitmaps components (or the underlying scan) will be used.
	 * @param image the image to render
	 * @param dpi the resolution to render the overall image in
	 * @param includeGraphics include vector based graphics?
	 * @param includeWords include label words?
	 * @param imageType the type of image to use
	 * @return the overall image
	 */
	public static BufferedImage renderImage(ImRegion image, int dpi, boolean includeGraphics, boolean includeWords, int imageType) {
		return renderImage(image, dpi, includeGraphics, includeWords, image.getDocument(), imageType, ProgressMonitor.silent);
	}
	
	/**
	 * Render an image, potentially consisting of multiple bitmap and vector
	 * graphics components, as well as labeling words. If there are no bitmap
	 * and vector graphics components, any underlying scan is used. If the
	 * argument resolution is less than 1, the highest resolution of any of the
	 * involved bitmaps components (or the underlying scan) will be used.
	 * @param image the image to render
	 * @param dpi the resolution to render the overall image in
	 * @param includeGraphics include vector based graphics?
	 * @param includeWords include label words?
	 * @param imageType the type of image to use
	 * @param pm a progress monitor to observe the rendering process
	 * @return the overall image
	 */
	public static BufferedImage renderImage(ImRegion image, int dpi, boolean includeGraphics, boolean includeWords, int imageType, ProgressMonitor pm) {
		return renderImage(image, dpi, includeGraphics, includeWords, image.getDocument(), imageType, pm);
	}
	
	/**
	 * Render an image, potentially consisting of multiple bitmap and vector
	 * graphics components, as well as labeling words. If there are no bitmap
	 * and vector graphics components, any underlying scan is used. If the
	 * argument resolution is less than 1, the highest resolution of any of the
	 * involved bitmaps components (or the underlying scan) will be used.
	 * @param image the image to render
	 * @param rop the provider to ask for the rendering options to use
	 * @return the overall image
	 */
	public static BufferedImage renderImage(ImRegion image, RenderingOptions.Provider rop) {
		return renderImage(image, rop, image.getDocument(), BufferedImage.TYPE_INT_ARGB, ProgressMonitor.silent);
	}
	
	/**
	 * Render an image, potentially consisting of multiple bitmap and vector
	 * graphics components, as well as labeling words. If there are no bitmap
	 * and vector graphics components, any underlying scan is used. If the
	 * argument resolution is less than 1, the highest resolution of any of the
	 * involved bitmaps components (or the underlying scan) will be used.
	 * @param image the image to render
	 * @param rop the provider to ask for the rendering options to use
	 * @param pm a progress monitor to observe the rendering process
	 * @return the overall image
	 */
	public static BufferedImage renderImage(ImRegion image, RenderingOptions.Provider rop, ProgressMonitor pm) {
		return renderImage(image, rop, image.getDocument(), BufferedImage.TYPE_INT_ARGB, pm);
	}
	
	/**
	 * Render an image, potentially consisting of multiple bitmap and vector
	 * graphics components, as well as labeling words. If there are no bitmap
	 * and vector graphics components, any underlying scan is used. If the
	 * argument resolution is less than 1, the highest resolution of any of the
	 * involved bitmaps components (or the underlying scan) will be used.
	 * @param image the image to render
	 * @param rop the provider to ask for the rendering options to use
	 * @param imageType the type of image to use
	 * @return the overall image
	 */
	public static BufferedImage renderImage(ImRegion image, RenderingOptions.Provider rop, int imageType) {
		return renderImage(image, rop, image.getDocument(), imageType, ProgressMonitor.silent);
	}
	
	/**
	 * Render an image, potentially consisting of multiple bitmap and vector
	 * graphics components, as well as labeling words. If there are no bitmap
	 * and vector graphics components, any underlying scan is used. If the
	 * argument resolution is less than 1, the highest resolution of any of the
	 * involved bitmaps components (or the underlying scan) will be used.
	 * @param image the image to render
	 * @param rop the provider to ask for the rendering options to use
	 * @param imageType the type of image to use
	 * @param pm a progress monitor to observe the rendering process
	 * @return the overall image
	 */
	public static BufferedImage renderImage(ImRegion image, RenderingOptions.Provider rop, int imageType, ProgressMonitor pm) {
		return renderImage(image, rop, image.getDocument(), imageType, pm);
	}
	
	/**
	 * Render an image, potentially consisting of multiple bitmap and vector
	 * graphics components, as well as labeling words. If there are no bitmap
	 * and vector graphics components, any underlying scan is used. If the
	 * argument resolution is less than 1, the highest resolution of any of the
	 * involved bitmaps components (or the underlying scan) will be used.
	 * @param image the image to render
	 * @param dpi the resolution to render the overall image in
	 * @param includeGraphics include vector based graphics?
	 * @param includeWords include label words?
	 * @param doc the document the image grid lies in
	 * @return the overall image
	 */
	public static BufferedImage renderImage(ImRegion image, final int dpi, final boolean includeGraphics, final boolean includeWords, ImDocument doc) {
		return renderImage(image, dpi, includeGraphics, includeWords, doc, BufferedImage.TYPE_INT_ARGB, ProgressMonitor.silent);
	}
	
	/**
	 * Render an image, potentially consisting of multiple bitmap and vector
	 * graphics components, as well as labeling words. If there are no bitmap
	 * and vector graphics components, any underlying scan is used. If the
	 * argument resolution is less than 1, the highest resolution of any of the
	 * involved bitmaps components (or the underlying scan) will be used.
	 * @param image the image to render
	 * @param dpi the resolution to render the overall image in
	 * @param includeGraphics include vector based graphics?
	 * @param includeWords include label words?
	 * @param doc the document the image grid lies in
	 * @param pm a progress monitor to observe the rendering process
	 * @return the overall image
	 */
	public static BufferedImage renderImage(ImRegion image, final int dpi, final boolean includeGraphics, final boolean includeWords, ImDocument doc, ProgressMonitor pm) {
		return renderImage(image, dpi, includeGraphics, includeWords, doc, BufferedImage.TYPE_INT_ARGB, pm);
	}
	
	/**
	 * Render an image, potentially consisting of multiple bitmap and vector
	 * graphics components, as well as labeling words. If there are no bitmap
	 * and vector graphics components, any underlying scan is used. If the
	 * argument resolution is less than 1, the highest resolution of any of the
	 * involved bitmaps components (or the underlying scan) will be used.
	 * @param image the image to render
	 * @param dpi the resolution to render the overall image in
	 * @param includeGraphics include vector based graphics?
	 * @param includeWords include label words?
	 * @param doc the document the image grid lies in
	 * @param imageType the type of image to use
	 * @return the overall image
	 */
	public static BufferedImage renderImage(ImRegion image, int dpi, boolean includeGraphics, boolean includeWords, ImDocument doc, int imageType) {
		return renderImage(image, dpi, includeGraphics, includeWords, doc, imageType, ProgressMonitor.silent);
	}
	
	/**
	 * Render an image, potentially consisting of multiple bitmap and vector
	 * graphics components, as well as labeling words. If there are no bitmap
	 * and vector graphics components, any underlying scan is used. If the
	 * argument resolution is less than 1, the highest resolution of any of the
	 * involved bitmaps components (or the underlying scan) will be used.
	 * @param image the image to render
	 * @param dpi the resolution to render the overall image in
	 * @param includeGraphics include vector based graphics?
	 * @param includeWords include label words?
	 * @param doc the document the image grid lies in
	 * @param imageType the type of image to use
	 * @param pm a progress monitor to observe the rendering process
	 * @return the overall image
	 */
	public static BufferedImage renderImage(ImRegion image, final int dpi, final boolean includeGraphics, final boolean includeWords, ImDocument doc, int imageType, ProgressMonitor pm) {
		return renderImage(image, new RenderingOptions.Provider() {
			public RenderingOptions getRenderingOptions(int figureCount, int graphicsCount, int wordCount, SortedSet dpis) {
				return new RenderingOptions(true, includeGraphics, includeWords, dpi);
			}
		}, doc, imageType, pm);
	}
	
	/**
	 * Render an image, potentially consisting of multiple bitmap and vector
	 * graphics components, as well as labeling words. If there are no bitmap
	 * and vector graphics components, any underlying scan is used. If the
	 * argument resolution is less than 1, the highest resolution of any of the
	 * involved bitmaps components (or the underlying scan) will be used.
	 * @param image the image to render
	 * @param dpi the resolution to render the overall image in
	 * @param includeGraphics include vector based graphics?
	 * @param includeWords include label words?
	 * @param doc the document the image grid lies in
	 * @return the overall image
	 */
	public static BufferedImage renderImage(ImRegion image, RenderingOptions.Provider rop, ImDocument doc) {
		return renderImage(image, rop, doc, BufferedImage.TYPE_INT_ARGB, ProgressMonitor.silent);
	}
	/**
	 * Render an image, potentially consisting of multiple bitmap and vector
	 * graphics components, as well as labeling words. If there are no bitmap
	 * and vector graphics components, any underlying scan is used. If the
	 * argument resolution is less than 1, the highest resolution of any of the
	 * involved bitmaps components (or the underlying scan) will be used.
	 * @param image the image to render
	 * @param dpi the resolution to render the overall image in
	 * @param includeGraphics include vector based graphics?
	 * @param includeWords include label words?
	 * @param doc the document the image grid lies in
	 * @param pm a progress monitor to observe the rendering process
	 * @return the overall image
	 */
	public static BufferedImage renderImage(ImRegion image, RenderingOptions.Provider rop, ImDocument doc, ProgressMonitor pm) {
		return renderImage(image, rop, doc, BufferedImage.TYPE_INT_ARGB, pm);
	}
	
	/**
	 * Render an image, potentially consisting of multiple bitmap and vector
	 * graphics components, as well as labeling words. If there are no bitmap
	 * and vector graphics components, any underlying scan is used. If the
	 * argument resolution is less than 1, the highest resolution of any of the
	 * involved bitmaps components (or the underlying scan) will be used.
	 * @param image the image to render
	 * @param dpi the resolution to render the overall image in
	 * @param includeGraphics include vector based graphics?
	 * @param includeWords include label words?
	 * @param doc the document the image grid lies in
	 * @param imageType the type of image to use
	 * @return the overall image
	 */
	public static BufferedImage renderImage(ImRegion image, RenderingOptions.Provider rop, ImDocument doc, int imageType) {
		return renderImage(image, rop, doc, imageType, ProgressMonitor.silent);
	}
	
	/**
	 * Render an image, potentially consisting of multiple bitmap and vector
	 * graphics components, as well as labeling words. If there are no bitmap
	 * and vector graphics components, any underlying scan is used. If the
	 * argument resolution is less than 1, the highest resolution of any of the
	 * involved bitmaps components (or the underlying scan) will be used.
	 * @param image the image to render
	 * @param dpi the resolution to render the overall image in
	 * @param includeGraphics include vector based graphics?
	 * @param includeWords include label words?
	 * @param doc the document the image grid lies in
	 * @param imageType the type of image to use
	 * @param pm a progress monitor to observe the rendering process
	 * @return the overall image
	 */
	public static BufferedImage renderImage(ImRegion image, RenderingOptions.Provider rop, ImDocument doc, int imageType, ProgressMonitor pm) {
//		return renderImageOrGrid(image, true, rop, doc, imageType, pm);
		BoundingBox[] biBounds = {null};
		int[] biDpi = {-1};
		BufferedImage bi = renderImageOrGrid(image, null /* no need for aggregate bounds in single-image mode */, true, rop, doc, imageType, pm, biBounds, biDpi);
		return cropImageToRegionBounds(image.bounds, image.getPage().getImageDPI(), bi, biBounds[0], biDpi[0]);
	}
	
	/**
	 * Render a grid of connected images into a composite image. The image grid
	 * is collected along horizontal and vertical connections starting from the
	 * argument image region, extending both left/upwards and right/downwards.
	 * If the argument resolution is less than 1, the highest resolution of any
	 * of the involved bitmaps components will be used.
	 * @param image the image to start from
	 * @param dpi the resolution to render the overall image in
	 * @param includeGraphics include vector based graphics?
	 * @param includeWords include label words?
	 * @return the overall image
	 */
	public static BufferedImage renderImageGrid(ImRegion image, int dpi, boolean includeGraphics, boolean includeWords) {
		return renderImageGrid(image, dpi, includeGraphics, includeWords, image.getDocument(), BufferedImage.TYPE_INT_ARGB, ProgressMonitor.silent);
	}
	
	/**
	 * Render a grid of connected images into a composite image. The image grid
	 * is collected along horizontal and vertical connections starting from the
	 * argument image region, extending both left/upwards and right/downwards.
	 * If the argument resolution is less than 1, the highest resolution of any
	 * of the involved bitmaps components will be used.
	 * @param image the image to start from
	 * @param dpi the resolution to render the overall image in
	 * @param includeGraphics include vector based graphics?
	 * @param includeWords include label words?
	 * @param imageType the type of image to use
	 * @return the overall image
	 */
	public static BufferedImage renderImageGrid(ImRegion image, int dpi, boolean includeGraphics, boolean includeWords, int imageType) {
		return renderImageGrid(image, dpi, includeGraphics, includeWords, image.getDocument(), imageType, ProgressMonitor.silent);
	}
	
	/**
	 * Render a grid of connected images into a composite image. The image grid
	 * is collected along horizontal and vertical connections starting from the
	 * argument image region, extending both left/upwards and right/downwards.
	 * If the argument resolution is less than 1, the highest resolution of any
	 * of the involved bitmaps components will be used.
	 * @param image the image to start from
	 * @param dpi the resolution to render the overall image in
	 * @param includeGraphics include vector based graphics?
	 * @param includeWords include label words?
	 * @param imageType the type of image to use
	 * @param pm a progress monitor to observe the rendering process
	 * @return the overall image
	 */
	public static BufferedImage renderImageGrid(ImRegion image, int dpi, boolean includeGraphics, boolean includeWords, int imageType, ProgressMonitor pm) {
		return renderImageGrid(image, dpi, includeGraphics, includeWords, image.getDocument(), imageType, pm);
	}
	
	/**
	 * Render a grid of connected images into a composite image. The image grid
	 * is collected along horizontal and vertical connections starting from the
	 * argument image region, extending both left/upwards and right/downwards.
	 * If the argument resolution is less than 1, the highest resolution of any
	 * of the involved bitmaps components will be used.
	 * @param image the image to start from
	 * @param rop the provider to ask for the rendering options to use
	 * @return the overall image
	 */
	public static BufferedImage renderImageGrid(ImRegion image, RenderingOptions.Provider rop) {
		return renderImageGrid(image, rop, image.getDocument(), BufferedImage.TYPE_INT_ARGB, ProgressMonitor.silent);
	}
	
	/**
	 * Render a grid of connected images into a composite image. The image grid
	 * is collected along horizontal and vertical connections starting from the
	 * argument image region, extending both left/upwards and right/downwards.
	 * If the argument resolution is less than 1, the highest resolution of any
	 * of the involved bitmaps components will be used.
	 * @param image the image to start from
	 * @param rop the provider to ask for the rendering options to use
	 * @param pm a progress monitor to observe the rendering process
	 * @return the overall image
	 */
	public static BufferedImage renderImageGrid(ImRegion image, RenderingOptions.Provider rop, ProgressMonitor pm) {
		return renderImageGrid(image, rop, image.getDocument(), BufferedImage.TYPE_INT_ARGB, pm);
	}
	
	/**
	 * Render a grid of connected images into a composite image. The image grid
	 * is collected along horizontal and vertical connections starting from the
	 * argument image region, extending both left/upwards and right/downwards.
	 * If the argument resolution is less than 1, the highest resolution of any
	 * of the involved bitmaps components will be used.
	 * @param image the image to start from
	 * @param rop the provider to ask for the rendering options to use
	 * @param imageType the type of image to use
	 * @return the overall image
	 */
	public static BufferedImage renderImageGrid(ImRegion image, RenderingOptions.Provider rop, int imageType) {
		return renderImageGrid(image, rop, image.getDocument(), imageType, ProgressMonitor.silent);
	}
	
	/**
	 * Render a grid of connected images into a composite image. The image grid
	 * is collected along horizontal and vertical connections starting from the
	 * argument image region, extending both left/upwards and right/downwards.
	 * If the argument resolution is less than 1, the highest resolution of any
	 * of the involved bitmaps components will be used.
	 * @param image the image to start from
	 * @param dpi the resolution to render the overall image in
	 * @param includeGraphics include vector based graphics?
	 * @param includeWords include label words?
	 * @param doc the document the image grid lies in
	 * @return the overall image
	 */
	public static BufferedImage renderImageGrid(ImRegion image, final int dpi, final boolean includeGraphics, final boolean includeWords, ImDocument doc) {
		return renderImageGrid(image, dpi, includeGraphics, includeWords, doc, BufferedImage.TYPE_INT_ARGB, ProgressMonitor.silent);
	}
	
	/**
	 * Render a grid of connected images into a composite image. The image grid
	 * is collected along horizontal and vertical connections starting from the
	 * argument image region, extending both left/upwards and right/downwards.
	 * If the argument resolution is less than 1, the highest resolution of any
	 * of the involved bitmaps components will be used.
	 * @param image the image to start from
	 * @param dpi the resolution to render the overall image in
	 * @param includeGraphics include vector based graphics?
	 * @param includeWords include label words?
	 * @param doc the document the image grid lies in
	 * @param pm a progress monitor to observe the rendering process
	 * @return the overall image
	 */
	public static BufferedImage renderImageGrid(ImRegion image, final int dpi, final boolean includeGraphics, final boolean includeWords, ImDocument doc, ProgressMonitor pm) {
		return renderImageGrid(image, dpi, includeGraphics, includeWords, doc, BufferedImage.TYPE_INT_ARGB, pm);
	}
	
	/**
	 * Render a grid of connected images into a composite image. The image grid
	 * is collected along horizontal and vertical connections starting from the
	 * argument image region, extending both left/upwards and right/downwards.
	 * If the argument resolution is less than 1, the highest resolution of any
	 * of the involved bitmaps components will be used.
	 * @param image the image to start from
	 * @param dpi the resolution to render the overall image in
	 * @param includeGraphics include vector based graphics?
	 * @param includeWords include label words?
	 * @param doc the document the image grid lies in
	 * @param imageType the type of image to use
	 * @return the overall image
	 */
	public static BufferedImage renderImageGrid(ImRegion image, int dpi, boolean includeGraphics, boolean includeWords, ImDocument doc, int imageType) {
		return renderImageGrid(image, dpi, includeGraphics, includeWords, doc, imageType, ProgressMonitor.silent);
	}
	
	/**
	 * Render a grid of connected images into a composite image. The image grid
	 * is collected along horizontal and vertical connections starting from the
	 * argument image region, extending both left/upwards and right/downwards.
	 * If the argument resolution is less than 1, the highest resolution of any
	 * of the involved bitmaps components will be used.
	 * @param image the image to start from
	 * @param dpi the resolution to render the overall image in
	 * @param includeGraphics include vector based graphics?
	 * @param includeWords include label words?
	 * @param doc the document the image grid lies in
	 * @param imageType the type of image to use
	 * @param pm a progress monitor to observe the rendering process
	 * @return the overall image
	 */
	public static BufferedImage renderImageGrid(ImRegion image, final int dpi, final boolean includeGraphics, final boolean includeWords, ImDocument doc, int imageType, ProgressMonitor pm) {
		return renderImageGrid(image, new RenderingOptions.Provider() {
			public RenderingOptions getRenderingOptions(int figureCount, int graphicsCount, int wordCount, SortedSet dpis) {
				return new RenderingOptions(true, includeGraphics, includeWords, dpi);
			}
		}, doc, imageType, pm);
	}
	
	/**
	 * Render a grid of connected images into a composite image. The image grid
	 * is collected along horizontal and vertical connections starting from the
	 * argument image region, extending both left/upwards and right/downwards.
	 * The argument rendering option provider will be prompted for the actual
	 * options to use after assessing the values present in the data.
	 * @param image the image to start from
	 * @param dpi the resolution to render the overall image in
	 * @param includeGraphics include vector based graphics?
	 * @param includeWords include label words?
	 * @param doc the document the image grid lies in
	 * @return the overall image
	 */
	public static BufferedImage renderImageGrid(ImRegion image, RenderingOptions.Provider rop, ImDocument doc) {
		return renderImageGrid(image, rop, doc, BufferedImage.TYPE_INT_ARGB, ProgressMonitor.silent);
	}
	
	/**
	 * Render a grid of connected images into a composite image. The image grid
	 * is collected along horizontal and vertical connections starting from the
	 * argument image region, extending both left/upwards and right/downwards.
	 * The argument rendering option provider will be prompted for the actual
	 * options to use after assessing the values present in the data.
	 * @param image the image to start from
	 * @param dpi the resolution to render the overall image in
	 * @param includeGraphics include vector based graphics?
	 * @param includeWords include label words?
	 * @param doc the document the image grid lies in
	 * @param pm a progress monitor to observe the rendering process
	 * @return the overall image
	 */
	public static BufferedImage renderImageGrid(ImRegion image, RenderingOptions.Provider rop, ImDocument doc, ProgressMonitor pm) {
		return renderImageGrid(image, rop, doc, BufferedImage.TYPE_INT_ARGB, pm);
	}
	
	/**
	 * Render a grid of connected images into a composite image. The image grid
	 * is collected along horizontal and vertical connections starting from the
	 * argument image region, extending both left/upwards and right/downwards.
	 * The argument rendering option provider will be prompted for the actual
	 * options to use after assessing the values present in the data.
	 * @param image the image to start from
	 * @param dpi the resolution to render the overall image in
	 * @param includeGraphics include vector based graphics?
	 * @param includeWords include label words?
	 * @param doc the document the image grid lies in
	 * @param imageType the type of image to use
	 * @return the overall image
	 */
	public static BufferedImage renderImageGrid(ImRegion image, RenderingOptions.Provider rop, ImDocument doc, int imageType) {
		return renderImageGrid(image, rop, doc, imageType, ProgressMonitor.silent);
	}
	
	/**
	 * Render a grid of connected images into a composite image. The image grid
	 * is collected along horizontal and vertical connections starting from the
	 * argument image region, extending both left/upwards and right/downwards.
	 * The argument rendering option provider will be prompted for the actual
	 * options to use after assessing the values present in the data.
	 * @param image the image to start from
	 * @param dpi the resolution to render the overall image in
	 * @param includeGraphics include vector based graphics?
	 * @param includeWords include label words?
	 * @param doc the document the image grid lies in
	 * @param imageType the type of image to use
	 * @param pm a progress monitor to observe the rendering process
	 * @return the overall image
	 */
	public static BufferedImage renderImageGrid(ImRegion image, RenderingOptions.Provider rop, ImDocument doc, int imageType, ProgressMonitor pm) {
//		return renderImageOrGrid(image, false, rop, doc, imageType, pm);
		BoundingBox[] imageGridBounds = {null};
		BoundingBox[] biBounds = {null};
		int[] biDpi = {-1};
		BufferedImage bi = renderImageOrGrid(image, imageGridBounds, false, rop, doc, imageType, pm, biBounds, biDpi);
		if (imageGridBounds[0] == null)
			return bi;
		else return cropImageToRegionBounds(imageGridBounds[0], image.getPage().getImageDPI(), bi, biBounds[0], biDpi[0]);
	}
	
	public static void main(String[] args) throws Exception {
		ImDocument doc = ImDocumentIO.loadDocument(new File("E:/Testdaten/PdfExtract/vertebrateZoology.70.1.23-59.pdf.imdir"));
		ImPage page = doc.getPage(8);
		ImRegion[] images = page.getRegions(ImRegion.IMAGE_TYPE);
		ImageDisplayDialog idd = new ImageDisplayDialog("Images in Page " + page.pageId);
		for (int i = 0; i < images.length; i++) {
			BufferedImage bi = renderImage(images[i], new RenderingOptions.Provider() {
				public RenderingOptions getRenderingOptions(int figureCount, int graphicsCount, int wordCount, SortedSet dpis) {
					return new RenderingOptions(true, true, true, 300);
				}
			}, doc);
			idd.addImage(bi, ("Image at " + images[i].bounds));
		}
		idd.setSize(800, 1000);
		idd.setLocationRelativeTo(null);
		idd.setVisible(true);
	}
	
	private static BufferedImage cropImageToRegionBounds(BoundingBox imageRegionBounds, int imageRegionDpi, BufferedImage bi, BoundingBox biBounds /* this is in image region resolution !!! */, int biDpi) {
		if ((bi == null) || (biBounds == null) || (biDpi < 1))
			return bi;
		if ((biBounds.getArea() * 2) < (imageRegionBounds.getArea() * 3))
			return bi; // over 67% included in region, we're fine
		BoundingBox biResImageRegionBounds = imageRegionBounds.scale(((float) biDpi) / imageRegionDpi);
		BoundingBox biResBiBounds = biBounds.scale(((float) biDpi) / imageRegionDpi);
		int cropLeft = Math.max(0, (biResImageRegionBounds.left - biResBiBounds.left));
		int cropRight = Math.min(bi.getWidth(), (biResImageRegionBounds.right - biResBiBounds.left));
		int cropTop = Math.max(0, (biResImageRegionBounds.top - biResBiBounds.top));
		int cropBottom = Math.min(bi.getHeight(), (biResImageRegionBounds.bottom - biResBiBounds.top));
		return bi.getSubimage(cropLeft, cropTop, (cropRight - cropLeft), (cropBottom - cropTop));
	}
	
	private static BufferedImage renderImageOrGrid(ImRegion image, BoundingBox[] imageGridBounds, boolean singleImageOnly, RenderingOptions.Provider rop, ImDocument doc, int imageType, ProgressMonitor pm, BoundingBox[] renderedBounds, int[] renderedDpi) {
		if (doc == null)
			doc = image.getDocument();
		if (pm == null)
			pm = ProgressMonitor.silent;
		
		//	get connected image regions, and collect supplements and words along the way
		ArrayList seekRegionImageGridCells = new ArrayList(4);
		HashSet seekRegionImageIDs = new HashSet(4);
		seekRegionImageGridCells.add(new RegionImageGridCell(0, 0, image));
		seekRegionImageIDs.add(image.getLocalID());
		ArrayList imageGridCellRelations = new ArrayList();
		int minColumn = 0;
		int minRow = 0;
		
		pm.setStep(singleImageOnly ? "Gathering image content" : "Gathering image grid tiles");
		pm.setBaseProgress(0);
		pm.setProgress(0);
		pm.setMaxProgress(5);
		ArrayList gImageGridCells = new ArrayList(4);
		TreeMap gRegionImageGridCellsByPage = new TreeMap();
		int maxImageGridCellsPerPage = 0;
		ArrayList gScans = new ArrayList();
		ArrayList gFigures = new ArrayList();
		TreeSet dpiSet = new TreeSet();
		ArrayList gGraphics = new ArrayList();
		ArrayList gWords = new ArrayList();
		for (int i = 0; i < seekRegionImageGridCells.size(); i++) {
			RegionImageGridCell seekIgc = ((RegionImageGridCell) seekRegionImageGridCells.get(i));
			pm.setInfo(" - investigating image at " + seekIgc.image.pageId + "." + seekIgc.image.bounds);
			minColumn = Math.min(minColumn, seekIgc.column);
			minRow = Math.min(minRow, seekIgc.row);
			
			//	collect supplements
			ImSupplement[] supplements = seekIgc.page.getSupplements();
			Scan iScan = null;
			ArrayList iFigures = new ArrayList();
			ArrayList cFigures = new ArrayList();
			ArrayList iGraphics = new ArrayList();
			ArrayList cGraphics = new ArrayList();
			for (int s = 0; s < supplements.length; s++) {
				if (supplements[s] instanceof Scan)
					iScan = ((Scan) supplements[s]);
//				else if ((supplements[s] instanceof Figure) && seekIgc.physicalBounds.includes(((Figure) supplements[s]).getBounds(), true)) {
//					iFigures.add(supplements[s]);
//					dpiSet.add(new Integer(((Figure) supplements[s]).getDpi()));
//				}
				else if (supplements[s] instanceof Figure) {
					if (seekIgc.physicalBounds.includes(((Figure) supplements[s]).getBounds(), true))
						iFigures.add(supplements[s]);
					else if (seekIgc.physicalBounds.liesIn(((Figure) supplements[s]).getBounds(), true))
//						cFigures.add(supplements[s]);
						iFigures.add(supplements[s]);
					dpiSet.add(new Integer(((Figure) supplements[s]).getDpi()));
				}
//				else if ((supplements[s] instanceof Graphics) && seekIgc.physicalBounds.includes(((Graphics) supplements[s]).getBounds(), true))
//					iGraphics.add(supplements[s]);
				else if (supplements[s] instanceof Graphics) {
					if (seekIgc.physicalBounds.includes(((Graphics) supplements[s]).getBounds(), true))
						iGraphics.add(supplements[s]);
					else if (seekIgc.physicalBounds.liesIn(((Graphics) supplements[s]).getBounds(), true))
//						cGraphics.add(supplements[s]);
						iGraphics.add(supplements[s]);
				}
			}
			
			if (iFigures.size() != 0)
				iScan = null; // no need for any scan if we have figure supplement(s), and be it alternative rendition
			if (iScan != null) {
				gScans.add(iScan);
				pm.setInfo(" - added scan");
				dpiSet.add(new Integer(iScan.getDpi()));
			}
			if (iFigures.isEmpty() && gFigures.isEmpty() && (iScan == null)) /* fall back on spanning objects, might contain multiple images */ {
				iFigures.addAll(cFigures);
				iGraphics.addAll(cGraphics);
			}
			gFigures.addAll(iFigures);
			pm.setInfo(" - added " + iFigures.size() + " bitmap images");
			gGraphics.addAll(iGraphics);
			pm.setInfo(" - added " + iGraphics.size() + " vector graphics");
			ImWord[] iWords = seekIgc.image.getWords();
			gWords.addAll(Arrays.asList(iWords));
			pm.setInfo(" - added " + iWords.length + " label words");
			
			//	tray up image with data, and bucketize per page
			RegionImageGridCell igc = seekIgc;
			igc.setImageData(iScan, ((Figure[]) iFigures.toArray(new Figure[iFigures.size()])), ((Graphics[]) iGraphics.toArray(new Graphics[iGraphics.size()])), iWords);
			gImageGridCells.add(igc);
			ArrayList pImageGridCells = ((ArrayList) gRegionImageGridCellsByPage.get(new Integer(seekIgc.image.pageId)));
			if (pImageGridCells == null) {
				pImageGridCells = new ArrayList(2);
				gRegionImageGridCellsByPage.put(new Integer(seekIgc.image.pageId), pImageGridCells);
			}
			pImageGridCells.add(igc);
			maxImageGridCellsPerPage = Math.max(maxImageGridCellsPerPage, pImageGridCells.size());
			pm.setInfo(" - image stored");
			
			//	are we supposed to extend?
			if (singleImageOnly)
				continue;
			
			//	enqueue connected images
			ImRegion lImage = ImUtils.getRegionForId(doc, ImRegion.IMAGE_TYPE, ((String) seekIgc.image.getAttribute("rightContinuesFrom")));
			if (lImage != null) {
				RegionImageGridCell lIgc = new RegionImageGridCell((seekIgc.column - 1), seekIgc.row, lImage);
				if (seekRegionImageIDs.add(lImage.getLocalID())) {
					seekRegionImageGridCells.add(lIgc);
					pm.setInfo(" - added image grid tile on left: " + lImage.pageId + "." + lImage.bounds);
				}
				imageGridCellRelations.add(new ImageGridCellRelation(lIgc, seekIgc, true));
			}
			ImRegion rImage = ImUtils.getRegionForId(doc, ImRegion.IMAGE_TYPE, ((String) seekIgc.image.getAttribute("rightContinuesIn")));
			if (rImage != null) {
				RegionImageGridCell rIgc = new RegionImageGridCell((seekIgc.column + 1), seekIgc.row, rImage);
				if (seekRegionImageIDs.add(rImage.getLocalID())) {
					seekRegionImageGridCells.add(rIgc);
					pm.setInfo(" - added image grid tile on right: " + rImage.pageId + "." + rImage.bounds);
				}
				imageGridCellRelations.add(new ImageGridCellRelation(seekIgc, rIgc, true));
			}
			ImRegion tImage = ImUtils.getRegionForId(doc, ImRegion.IMAGE_TYPE, ((String) seekIgc.image.getAttribute("bottomContinuesFrom")));
			if (tImage != null) {
				RegionImageGridCell tIgc = new RegionImageGridCell(seekIgc.column, (seekIgc.row - 1), tImage);
				if (seekRegionImageIDs.add(tImage.getLocalID())) {
					seekRegionImageGridCells.add(tIgc);
					pm.setInfo(" - added image grid tile above: " + tImage.pageId + "." + tImage.bounds);
				}
				imageGridCellRelations.add(new ImageGridCellRelation(tIgc, seekIgc, false));
			}
			ImRegion bImage = ImUtils.getRegionForId(doc, ImRegion.IMAGE_TYPE, ((String) seekIgc.image.getAttribute("bottomContinuesIn")));
			if (bImage != null) {
				RegionImageGridCell bIgc = new RegionImageGridCell(seekIgc.column, (seekIgc.row + 1), bImage);
				if (seekRegionImageIDs.add(bImage.getLocalID())) {
					seekRegionImageGridCells.add(bIgc);
					pm.setInfo(" - added image grid tile below: " + bImage.pageId + "." + bImage.bounds);
				}
				imageGridCellRelations.add(new ImageGridCellRelation(seekIgc, bIgc, false));
			}
		}
		
		//	get rendering options
		pm.setStep("Getting rendering options");
		pm.setBaseProgress(5);
		pm.setProgress(0);
		int dpi;
		boolean includeFigures;
		boolean includeGraphics;
		boolean includeWords;
		if (rop == null) {
			if (dpiSet.isEmpty())
				dpi = 300;
			else dpi = ((Integer) dpiSet.last()).intValue();
			includeFigures = (gFigures.size() != 0);
			includeGraphics = (gGraphics.size() != 0);
			includeWords = (gWords.size() != 0);
		}
		else {
			RenderingOptions ros = rop.getRenderingOptions(gFigures.size(), gGraphics.size(), gWords.size(), Collections.unmodifiableSortedSet(dpiSet));
			if (ros == null)
				return null;
			if (0 < ros.dpi)
				dpi = ros.dpi;
			else if (dpiSet.isEmpty())
				dpi = 300;
			else dpi = ((Integer) dpiSet.last()).intValue();
			includeFigures = ros.includeFigures;
			includeGraphics = ros.includeGraphics;
			includeWords = ros.includeWords;
		}
		if (renderedDpi != null)
			renderedDpi[0] = dpi;
		
		//	check cell relationships within pages
		if (!singleImageOnly)
			pm.setStep("Assessing in-page image grid tile relationships");
		pm.setBaseProgress(5);
		pm.setProgress(0);
		pm.setMaxProgress(10);
		HashSet igcsInPageLayoutRelationPageIDs = new HashSet();
		for (Iterator pidit = gRegionImageGridCellsByPage.keySet().iterator(); pidit.hasNext();) {
			Integer pid = ((Integer) pidit.next());
			if (!singleImageOnly)
				pm.setInfo(" - assessing page " + pid);
			ArrayList pImageGridCells = ((ArrayList) gRegionImageGridCellsByPage.get(pid));
			if (pImageGridCells.size() < 2) {
				igcsInPageLayoutRelationPageIDs.add(pid);
				if (!singleImageOnly)
					pm.setInfo(" ==> got single image grid tile");
				continue;
			}
			pm.setInfo("   - got " + pImageGridCells.size() + " image grid tiles");
			pm.setInfo("   - got " + imageGridCellRelations.size() + " tile relations");
			for (int r = 0; r < imageGridCellRelations.size(); r++) {
				ImageGridCellRelation igcRel = ((ImageGridCellRelation) imageGridCellRelations.get(r));
				if (igcRel.topOrLeft.page.pageId != pid.intValue())
					continue;
				if (igcRel.bottomOrRight.page.pageId != pid.intValue())
					continue;
				if (igcRel.topOrLeft.rotation != igcRel.bottomOrRight.rotation) /* different orientation, need to render in parts */ {
					pImageGridCells = null;
					break;
				}
				if (igcRel.isHorizontal) {
					if (igcRel.bottomOrRight.column <= igcRel.topOrLeft.column) {
						pImageGridCells = null;
						break;
					}
					BoundingBox lBox = igcRel.topOrLeft.logicalBounds;
					BoundingBox rBox = igcRel.bottomOrRight.logicalBounds;
					if (lBox.bottom <= rBox.top) /* left part above right part */ {
						pImageGridCells = null;
						break;
					}
					if (rBox.bottom <= lBox.top) /* right part above left part */ {
						pImageGridCells = null;
						break;
					}
					Point lCp = lBox.getCenterPoint();
					Point rCp = rBox.getCenterPoint();
					if (rBox.left <= lCp.x) /* left center not left of right part */ {
						pImageGridCells = null;
						break;
					}
					if (rCp.x <= lBox.right) /* right center not right of left part */ {
						pImageGridCells = null;
						break;
					}
				}
				else {
					if (igcRel.bottomOrRight.row <= igcRel.topOrLeft.row) {
						pImageGridCells = null;
						break;
					}
					BoundingBox tBox = igcRel.topOrLeft.logicalBounds;
					BoundingBox bBox = igcRel.bottomOrRight.logicalBounds;
					if (tBox.right <= bBox.left) /* top part left of bottom part */ {
						pImageGridCells = null;
						break;
					}
					if (bBox.right <= tBox.left) /* bottom part left of top part */ {
						pImageGridCells = null;
						break;
					}
					Point tCp = tBox.getCenterPoint();
					Point bCp = bBox.getCenterPoint();
					if (bBox.top <= tCp.y) /* top center not above bottom part */ {
						pImageGridCells = null;
						break;
					}
					if (bCp.y <= tBox.bottom) /* bottom center not below top part */ {
						pImageGridCells = null;
						break;
					}
				}
			}
			if (pImageGridCells != null) {
				pm.setInfo(" ==> image grid tiles in page arrangement");
				igcsInPageLayoutRelationPageIDs.add(pid);
			}
			else pm.setInfo(" ==> image grid tiles require rearranging");
		}
		
		//	we have a single page in page layout, can go directly
		if ((gRegionImageGridCellsByPage.size() == 1) && (igcsInPageLayoutRelationPageIDs.size() == 1)) {
			Integer pid = ((Integer) gRegionImageGridCellsByPage.keySet().iterator().next());
			ArrayList pImageGridCells = ((ArrayList) gRegionImageGridCellsByPage.get(pid));
			ImPage page = doc.getPage(pid.intValue());
			if (!singleImageOnly)
				pm.setStep("Rendering image grid in page " + pid + " with " + pImageGridCells.size() + " tiles");
			pm.setBaseProgress(10);
			pm.setProgress(0);
			pm.setMaxProgress(100);
			ProgressMonitor rpm = ((pm == ProgressMonitor.silent) ? pm : new CascadingProgressMonitor(pm));
			BufferedImage gridImage = createPageContentImage(pImageGridCells, dpi, includeFigures, includeGraphics, includeWords, page, imageType, rpm, renderedBounds);
			if (imageGridBounds != null) // compute aggregate bounds of connected image regions
				for (int c = 0; c < pImageGridCells.size(); c++) {
					RegionImageGridCell igc = ((RegionImageGridCell) pImageGridCells.get(c));
					if (imageGridBounds[0] == null)
						imageGridBounds[0] = igc.image.bounds;
					else imageGridBounds[0] = imageGridBounds[0].union(igc.image.bounds);
				}
			return gridImage;
		}
		
		//	all pages in page layout, reduce to page grid
		boolean reduceToPageImageGridCells = (gRegionImageGridCellsByPage.size() == igcsInPageLayoutRelationPageIDs.size());
		if (reduceToPageImageGridCells) {
			ArrayList seekPageImageGridCells = new ArrayList(gRegionImageGridCellsByPage.size());
			HashSet seekPageImageIDs = new HashSet(gRegionImageGridCellsByPage.size());
			seekPageImageGridCells.add(new PageImageGridCell(0, 0, image.getPage(), getPageContentLogicalBounds((ArrayList) gRegionImageGridCellsByPage.get(new Integer(image.pageId)))));
			seekPageImageIDs.add(new Integer(image.pageId));
			pm.setStep("Aggregating image grid tiles in " + gRegionImageGridCellsByPage.size() + " pages");
			pm.setBaseProgress(10);
			pm.setProgress(0);
			pm.setMaxProgress(50);
			int pMinColumn = 0;
			int pMinRow = 0;
			
			//	collect page based image cells
			ArrayList gPageImageGridCells = new ArrayList(4);
			ProgressMonitor rpm = ((pm == ProgressMonitor.silent) ? pm : new CascadingProgressMonitor(pm));
			for (int p = 0; p < seekPageImageGridCells.size(); p++) {
				PageImageGridCell seekIgc = ((PageImageGridCell) seekPageImageGridCells.get(p));
				pMinColumn = Math.min(pMinColumn, seekIgc.column);
				pMinRow = Math.min(pMinRow, seekIgc.row);
				
				//	render image for current page (already scaled)
				ArrayList pImageGridCells = ((ArrayList) gRegionImageGridCellsByPage.get(new Integer(seekIgc.page.pageId)));
				pm.setStep(" - aggregating " + pImageGridCells.size() + " image grid tiles in page " + seekIgc.page.pageId);
				rpm.setBaseProgress((gPageImageGridCells.size() * 100) / gRegionImageGridCellsByPage.size());
				rpm.setProgress(0);
				rpm.setMaxProgress(((gPageImageGridCells.size() + 1) * 100) / gRegionImageGridCellsByPage.size());
				
				//	store image at target resolution
				ProgressMonitor prpm = ((pm == ProgressMonitor.silent) ? pm : new CascadingProgressMonitor(rpm));
				BufferedImage pImage = createPageContentImage(pImageGridCells, dpi, includeFigures, includeGraphics, includeWords, seekIgc.page, imageType, prpm, null);
				PageImageGridCell igc = seekIgc;
				igc.setImageData(pImage, dpi);
				gPageImageGridCells.add(igc);
				
				//	seek neighboring pages via trans-page cell relations
				for (int r = 0; r < imageGridCellRelations.size(); r++) {
					ImageGridCellRelation igcRel = ((ImageGridCellRelation) imageGridCellRelations.get(r));
					if (igcRel.topOrLeft.page.pageId == igcRel.bottomOrRight.page.pageId)
						continue; // inside a page
					if ((igcRel.topOrLeft.page.pageId == igc.page.pageId) && seekPageImageIDs.add(new Integer(igcRel.bottomOrRight.page.pageId))) {
						ImPage borPage = doc.getPage(igcRel.bottomOrRight.page.pageId);
						if (igcRel.isHorizontal)
							seekPageImageGridCells.add(new PageImageGridCell((seekIgc.column + 1), seekIgc.row, borPage, getPageContentLogicalBounds((ArrayList) gRegionImageGridCellsByPage.get(new Integer(borPage.pageId)))));
						else seekPageImageGridCells.add(new PageImageGridCell(seekIgc.column, (seekIgc.row + 1), borPage, getPageContentLogicalBounds((ArrayList) gRegionImageGridCellsByPage.get(new Integer(borPage.pageId)))));
					}
					else if ((igcRel.bottomOrRight.page.pageId == igc.page.pageId) && seekPageImageIDs.add(new Integer(igcRel.topOrLeft.page.pageId))) {
						ImPage tolPage = doc.getPage(igcRel.topOrLeft.page.pageId);
						if (igcRel.isHorizontal)
							seekPageImageGridCells.add(new PageImageGridCell((seekIgc.column - 1), seekIgc.row, tolPage, getPageContentLogicalBounds((ArrayList) gRegionImageGridCellsByPage.get(new Integer(tolPage.pageId)))));
						else seekPageImageGridCells.add(new PageImageGridCell(seekIgc.column, (seekIgc.row - 1), tolPage, getPageContentLogicalBounds((ArrayList) gRegionImageGridCellsByPage.get(new Integer(tolPage.pageId)))));
					}
				}
			}
			
			//	switch measurements to page based
			gImageGridCells = gPageImageGridCells;
			minColumn = pMinColumn;
			minRow = pMinRow;
		}
		
		//	make sure grid positions are zero based, and asses size
		pm.setStep("Assessing image grid size");
		pm.setBaseProgress(reduceToPageImageGridCells ? 50 : 10);
		pm.setProgress(0);
		pm.setMaxProgress(reduceToPageImageGridCells ? 51 : 11);
		int maxColumn = 0;
		int maxRow = 0;
		for (int i = 0; i < gImageGridCells.size(); i++) {
			pm.setProgress((i * 100) / gImageGridCells.size());
			ImageGridCell igc = ((ImageGridCell) gImageGridCells.get(i));
			igc.column -= minColumn;
			igc.row -= minRow;
			maxColumn = Math.max(maxColumn, igc.column);
			maxRow = Math.max(maxRow, igc.row);
		}
		
		//	put entire grid in array
		pm.setStep("Arranging image grid tiles");
		pm.setBaseProgress(reduceToPageImageGridCells ? 51 : 11);
		pm.setProgress(0);
		pm.setMaxProgress(reduceToPageImageGridCells ? 53 : 13);
		ImageGridCell[][] imageGrid = new ImageGridCell[maxColumn + 1][maxRow + 1];
		for (int i = 0; i < gImageGridCells.size(); i++) {
			pm.setProgress((i * 100) / gImageGridCells.size());
			ImageGridCell igc = ((ImageGridCell) gImageGridCells.get(i));
			imageGrid[igc.column][igc.row] = igc;
		}
		
		//	compute left offset of each column
		pm.setStep("Assessing overall image grid dimensions");
		pm.setBaseProgress(reduceToPageImageGridCells ? 53 : 13);
		pm.setProgress(0);
		pm.setMaxProgress(reduceToPageImageGridCells ? 54 : 14);
		int gridImageWidth = 0;
		for (int c = 0; c <= maxColumn; c++) {
			int lColumnRight = gridImageWidth;
			for (int r = 0; r <= maxRow; r++)
				if (imageGrid[c][r] != null) {
					imageGrid[c][r].leftOffset = lColumnRight;
					gridImageWidth = Math.max(gridImageWidth, (imageGrid[c][r].leftOffset + ((imageGrid[c][r].logicalBounds.getWidth() * dpi) / imageGrid[c][r].page.getImageDPI()) + 2));
				}
		}
		
		//	compute top offset of each row
		pm.setBaseProgress(reduceToPageImageGridCells ? 54 : 14);
		pm.setProgress(0);
		pm.setMaxProgress(reduceToPageImageGridCells ? 55 : 15);
		int gridImageHeight = 0;
		for (int r = 0; r <= maxRow; r++) {
			int tRowBotton = gridImageHeight;
			for (int c = 0; c <= maxColumn; c++)
				if (imageGrid[c][r] != null) {
					imageGrid[c][r].topOffset = tRowBotton;
					gridImageHeight = Math.max(gridImageHeight, (imageGrid[c][r].topOffset + ((imageGrid[c][r].logicalBounds.getHeight() * dpi) / imageGrid[c][r].page.getImageDPI()) + 2));
				}
		}
		
		//	create unified bitmap
		pm.setStep("Rendering image grid");
		pm.setBaseProgress(reduceToPageImageGridCells ? 55 : 15);
		pm.setProgress(0);
		pm.setMaxProgress(100);
		BufferedImage gridImage = new BufferedImage(gridImageWidth, gridImageHeight, BufferedImage.TYPE_4BYTE_ABGR);
		Graphics2D gridImageGr = gridImage.createGraphics();
		gridImageGr.setColor(Color.WHITE);
		gridImageGr.fillRect(0, 0, gridImage.getWidth(), gridImage.getHeight());
		ProgressMonitor rpm = ((pm == ProgressMonitor.silent) ? pm : new CascadingProgressMonitor(pm));
		for (int c = 0; c <= maxColumn; c++)
			for (int r = 0; r <= maxRow; r++) {
				if (imageGrid[c][r] == null)
					continue;
				rpm.setBaseProgress((((c * imageGrid.length) + r) * 100) / (imageGrid.length * imageGrid[c].length));
				rpm.setProgress(0);
				rpm.setMaxProgress((((c * imageGrid.length) + r + 1) * 100) / (imageGrid.length * imageGrid[c].length));
				ProgressMonitor crpm = ((pm == ProgressMonitor.silent) ? pm : new CascadingProgressMonitor(rpm));
				BufferedImage igcImage = imageGrid[c][r].getImage(dpi, includeFigures, includeGraphics, includeWords, imageType, crpm);
				if (igcImage != null)
					gridImageGr.drawImage(igcImage, imageGrid[c][r].leftOffset, imageGrid[c][r].topOffset, null);
			}
		
		//	finally ...
		pm.setProgress(100);
		return gridImage;
	}
	
	private static BufferedImage createPageContentImage(ArrayList pImageGridCells, int dpi, boolean includeFigures, boolean includeGraphics, boolean includeWords, ImPage page, int imageType, ProgressMonitor pm, BoundingBox[] renderedBounds) {
		
		//	collect grid image components and rotation
		pm.setInfo(" - collecting objects");
		Scan pScan = null;
		LinkedHashSet pFigures = new LinkedHashSet();
		LinkedHashSet pGraphics = new LinkedHashSet();
		LinkedHashSet pWords = new LinkedHashSet();
		LinkedHashSet pRotations = new LinkedHashSet();
		for (int c = 0; c < pImageGridCells.size(); c++) {
			RegionImageGridCell igc = ((RegionImageGridCell) pImageGridCells.get(c));
			if (igc.scan != null)
				pScan = igc.scan;
			if (includeFigures && (igc.figures != null)) {
				pFigures.addAll(Arrays.asList(igc.figures));
				if (renderedBounds == null) {}
				else for (int f = 0; f < igc.figures.length; f++) {
					if (renderedBounds[0] == null)
						renderedBounds[0] = igc.figures[f].getBounds();
					else renderedBounds[0] = renderedBounds[0].union(igc.figures[f].getBounds());
				}
			}
			if (includeGraphics && (igc.graphics != null)) {
				pGraphics.addAll(Arrays.asList(igc.graphics));
				if (renderedBounds == null) {}
				else for (int g = 0; g < igc.graphics.length; g++) {
					if (renderedBounds[0] == null)
						renderedBounds[0] = igc.graphics[g].getBounds();
					else renderedBounds[0] = renderedBounds[0].union(igc.graphics[g].getBounds());
				}
			}
			if (includeWords && (igc.words != null))
				pWords.addAll(Arrays.asList(igc.words));
			pRotations.add(new Integer(igc.rotation));
		}
		
		//	if we have a scan, use that to create temporary figures (cut out at scan resolution, might not be same as page image !!!)
		if (pScan != null) try {
			pm.setInfo(" - adding scan excerpts");
			BufferedImage pScanImage = ImageIO.read(pScan.getInputStream());
			for (int c = 0; c < pImageGridCells.size(); c++) {
				RegionImageGridCell igc = ((RegionImageGridCell) pImageGridCells.get(c));
				if (igc.scan == null)
					continue; // we have other means of rendering this one
				int pImageDpi = page.getImageDPI();
				int pScanDpi = pScan.getDpi();
				int igcScanLeft = ((igc.image.bounds.left * pScanDpi) / pImageDpi);
				int igcScanRight = ((igc.image.bounds.right * pScanDpi) / pImageDpi);
				int igcScanTop = ((igc.image.bounds.top * pScanDpi) / pImageDpi);
				int igcScanBottom = ((igc.image.bounds.bottom * pScanDpi) / pImageDpi);
				BufferedImage igcScanImage = pScanImage.getSubimage(igcScanLeft, igcScanTop, (igcScanRight - igcScanLeft), (igcScanBottom - igcScanTop));
				Figure igcFigure = Figure.createFigure(null, page.pageId, 0, page.getImageDPI(), igcScanImage, igc.image.bounds);
				pFigures.add(igcFigure);
				if (renderedBounds == null) {}
				else if (renderedBounds[0] == null)
					renderedBounds[0] = igc.image.bounds;
				else renderedBounds[0] = renderedBounds[0].union(igc.image.bounds);
			}
		}
		catch (Exception e) {
			pm.setInfo(" - error extracting scan excerpt: " + e.getMessage());
			System.out.println("Failed to extract images in page " + page.pageId + " from scan: " + e.getMessage());
			e.printStackTrace(System.out);
		}
		
		//	render composite image from the components we have (including wrapped scan excerpts)
		BufferedImage image = renderCompositeImage((includeFigures ? ((Figure[]) pFigures.toArray(new Figure[pFigures.size()])) : null), dpi, (includeGraphics ? ((Graphics[]) pGraphics.toArray(new Graphics[pGraphics.size()])) : null), (includeWords ? ((ImWord[]) pWords.toArray(new ImWord[pWords.size()])) : null), page, imageType, pm);
		if (pRotations.size() == 1)
			image = ImObjectTransformer.transformImage(image, ((Integer) pRotations.iterator().next()).intValue());
		return image;
	}
	
	private static BoundingBox getPageContentLogicalBounds(ArrayList pImageGridCells) {
		if (pImageGridCells.size() == 1)
			return ((RegionImageGridCell) pImageGridCells.get(0)).logicalBounds;
		BoundingBox[] igcBounds = new BoundingBox[pImageGridCells.size()];
		for (int c = 0; c < pImageGridCells.size(); c++) {
			RegionImageGridCell igc = ((RegionImageGridCell) pImageGridCells.get(c));
			igcBounds[c] = igc.logicalBounds;
		}
		return BoundingBox.aggregate(igcBounds);
	}
	
	private static abstract class ImageGridCell {
		final ImPage page;
		final BoundingBox physicalBounds; // original bounds, in page resolution (for identification, and for supplement retrieval)
		final BoundingBox logicalBounds; // rotated bounds, in page resolution (for constraint checking)
		final int rotation; // from orientation in page to upright
		int column;
		int row;
		int leftOffset = 0;
		int topOffset = 0;
		ImageGridCell(int column, int row, ImPage page, BoundingBox bounds, int rotation) {
			this.page = page;
			this.physicalBounds = bounds;
			this.logicalBounds = rotateBounds(bounds, page, rotation);
			this.rotation = rotation;
			this.column = column;
			this.row = row;
		}
		abstract BufferedImage getImage(int dpi, boolean includeFigures, boolean includeGraphics, boolean includeWords, int imageType, ProgressMonitor pm);
		static BufferedImage scaleImage(BufferedImage image, int fromDpi, int toDpi) {
			if (toDpi == fromDpi)
				return image;
			BufferedImage sImage = new BufferedImage(((image.getWidth() * toDpi) / fromDpi), ((image.getHeight() * toDpi) / fromDpi), image.getType());
			Graphics2D sImageGr = sImage.createGraphics();
			sImageGr.setColor(Color.WHITE);
			sImageGr.fillRect(0, 0, sImage.getWidth(), sImage.getHeight());
			sImageGr.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			sImageGr.drawImage(image, 0, 0, sImage.getWidth(), sImage.getHeight(), null);
			sImageGr.dispose();
			return sImage;
		}
		private static BoundingBox rotateBounds(BoundingBox bounds, ImPage page, int rotation) {
			if (rotation == 0)
				return bounds;
			else if (rotation == ImObjectTransformer.CLOCKWISE_ROTATION) // rotate clockwise with page bounds
				return new BoundingBox((page.bounds.getHeight() - bounds.bottom), (page.bounds.getHeight() - bounds.top), bounds.left, bounds.right);
			else if (rotation == ImObjectTransformer.COUNTER_CLOCKWISE_ROTATION) // rotate counter-clockwise with page bounds
				return new BoundingBox(bounds.top, bounds.bottom, (page.bounds.getWidth() - bounds.right), (page.bounds.getWidth() - bounds.left));
			else if (rotation == (ImObjectTransformer.CLOCKWISE_ROTATION + ImObjectTransformer.CLOCKWISE_ROTATION)) // rotate 180° with page bounds
				return new BoundingBox((page.bounds.getWidth() - bounds.right), (page.bounds.getWidth() - bounds.left), (page.bounds.getHeight() - bounds.bottom), (page.bounds.getHeight() - bounds.top));
			else return bounds;
		}
	}
	
	private static class ImageGridCellRelation {
		final ImageGridCell topOrLeft;
		final ImageGridCell bottomOrRight;
		final boolean isHorizontal;
		ImageGridCellRelation(ImageGridCell topOrLeft, ImageGridCell bottomOrRight, boolean isHorizontal) {
			this.topOrLeft = topOrLeft;
			this.bottomOrRight = bottomOrRight;
			this.isHorizontal = isHorizontal;
		}
	}
	
	private static class RegionImageGridCell extends ImageGridCell {
		final ImRegion image;
		Scan scan;
		Figure[] figures;
		Graphics[] graphics;
		ImWord[] words;
		RegionImageGridCell(int column, int row, ImRegion image) {
			super(column, row, image.getPage(), image.bounds, ImObjectTransformer.getToUprightRotation((String) image.getAttribute(ImLayoutObject.TEXT_DIRECTION_ATTRIBUTE)));
			this.image = image;
		}
		void setImageData(Scan scan, Figure[] figures, Graphics[] graphics, ImWord[] words) {
			this.scan = scan;
			this.figures = figures;
			this.graphics = graphics;
			this.words = words;
		}
		BufferedImage getImage(int dpi, boolean includeFigures, boolean includeGraphics, boolean includeWords, int imageType, ProgressMonitor pm) {
			BufferedImage image;
			if (this.scan == null)
				image = renderCompositeImage(this.figures, dpi, (includeGraphics ? this.graphics : null), (includeWords ? this.words : null), this.page, imageType, pm);
			try {
				pm.setInfo(" - extracting image from scan");
				BufferedImage igcScan = ImageIO.read(this.scan.getInputStream());
				BufferedImage igcScanImage = igcScan.getSubimage(this.physicalBounds.left, this.physicalBounds.top, this.physicalBounds.getWidth(), this.physicalBounds.getHeight());
				pm.setInfo(" - scaling image to target resolution");
				image = scaleImage(igcScanImage, this.page.getImageDPI(), dpi);
			}
			catch (Exception e) {
				pm.setInfo(" - error extracting image from scan: " + e.getMessage());
				System.out.println("Failed to extract image " + this.image.getLocalID() + " from scan: " + e.getMessage());
				e.printStackTrace(System.out);
				return null;
			}
			return ImObjectTransformer.transformImage(image, this.rotation);
		}
	}
	
	private static class PageImageGridCell extends ImageGridCell {
		BufferedImage igcImage;
		int igcImageDpi;
		PageImageGridCell(int column, int row, ImPage page, BoundingBox bounds) {
			super(column, row, page, bounds, 0);
		}
		void setImageData(BufferedImage igcImage, int igcImageDpi) {
			this.igcImage = igcImage;
			this.igcImageDpi = igcImageDpi;
		}
		BufferedImage getImage(int dpi, boolean includeFigures, boolean includeGraphics, boolean includeWords, int imageType, ProgressMonitor pm) {
			pm.setInfo(" - scaling image to target resolution");
			return scaleImage(this.igcImage, this.igcImageDpi, dpi);
		}
	}
}
