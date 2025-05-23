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
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.LineMetrics;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTabbedPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import de.uka.ipd.idaho.gamta.AttributeUtils;
import de.uka.ipd.idaho.gamta.Attributed;
import de.uka.ipd.idaho.gamta.util.CountingSet;
import de.uka.ipd.idaho.gamta.util.ProgressMonitor;
import de.uka.ipd.idaho.gamta.util.imaging.BoundingBox;
import de.uka.ipd.idaho.gamta.util.imaging.ImagingConstants;
import de.uka.ipd.idaho.gamta.util.imaging.PageImage;
import de.uka.ipd.idaho.gamta.util.swing.AttributeEditor;
import de.uka.ipd.idaho.gamta.util.swing.DialogFactory;
import de.uka.ipd.idaho.gamta.util.swing.ProgressMonitorWindow;
import de.uka.ipd.idaho.im.ImAnnotation;
import de.uka.ipd.idaho.im.ImDocument;
import de.uka.ipd.idaho.im.ImDocument.ImDocumentListener;
import de.uka.ipd.idaho.im.ImFont;
import de.uka.ipd.idaho.im.ImLayoutObject;
import de.uka.ipd.idaho.im.ImObject;
import de.uka.ipd.idaho.im.ImPage;
import de.uka.ipd.idaho.im.ImRegion;
import de.uka.ipd.idaho.im.ImSupplement;
import de.uka.ipd.idaho.im.ImWord;
import de.uka.ipd.idaho.im.analysis.Imaging;
import de.uka.ipd.idaho.im.util.ImDocumentMarkupPanel.ImDocumentViewControl.AnnotControl;
import de.uka.ipd.idaho.im.util.ImDocumentMarkupPanel.ImDocumentViewControl.RegionControl;
import de.uka.ipd.idaho.im.util.ImDocumentMarkupPanel.ImDocumentViewControl.TypeControl;
import de.uka.ipd.idaho.im.util.ImImageEditorPanel.ImImageEditTool;
import de.uka.ipd.idaho.stringUtils.StringUtils;

/**
 * Display widget for image markup documents. Instances of this class are best
 * embedded in a scroll pane.
 * 
 * @author sautter
 */
public class ImDocumentMarkupPanel extends JPanel implements ImagingConstants {
	
	/** array holding all fixed names of display properties (this does not
	 * include annotation colors or display modes, as those differ between
	 * annotation types, and the associated display property names include
	 * those very annotation types for distinction) */
	public static final String[] displayPropertyNames = {
		"wordSelection.color",
		"boxSelection.color",
		"boxSelection.thickness",
		"annot.highlightAlpha",
	};
	
	//	TODO make markup and page image editors accessible to plugins through interfaces
	//	==> GUI can be replaced (theoretically)
	
	//	TODO make cursor names for inside words, inside regions (but outside words), and outside regions configurable ...
	//	TODO ... using either names for compiled-in cursors, or names and Cursor objects for completely custom cursors
	//	TODO set default to built-in cursors
	//	TODO use static setters, and a cursor provider plugin (generic resource manager) for managing the cursors (solves data problems along the way)
	
	private static final int transparentWhite = new Color(Color.WHITE.getRed(), Color.WHITE.getGreen(), Color.WHITE.getBlue(), 0).getRGB();
	private static final BasicStroke defaultSelectionBoxStroke = new BasicStroke(3, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER);
	
	/** the default DPI for rendering images, namely 96 */
	public static int DEFAULT_RENDERING_DPI = 96; // TODO_ne figure out how to compute this for current display device ==> Toolkit.getDefaultToolkit().getScreenResolution() returns the resolution, in DPI, and it's usually 96, always
	
	/** the image markup document displayed in this viewer panel */
	public final ImDocument document;
	
	/** indicates if the image markup document displayed in this viewer panel is born digital
	 * @deprecated */
	public final boolean documentBornDigital;
	
	/** indicates if the image markup document displayed in this viewer panel is born-digital page backgrounds */
	public final boolean documentPagesBornDigital;
	
	/** indicates how the text of the image markup document displayed in this viewer panel was generated */
	public final char documentTextOrigin;
	
	/** text origin indicating document text that was born-digital */
	public static final char TEXT_ORIGIN_BORN_DIGITAL = 'B';
	
	/** text origin indicating document text that was scanned and then replaced by a digital rendition */
	public static final char TEXT_ORIGIN_DIGITIZED_OCR = 'D';
	
	/** text origin indicating document text that was scanned and then replaced by a vectorized rendition (e.g. DjVu encoding) */
	public static final char TEXT_ORIGIN_VECTORIZED = 'V';
	
	/** text origin indicating document text that was created by OCR */
	public static final char TEXT_ORIGIN_OCR = 'O';
	
	private ImPage[] pages;
	private ImPageMarkupPanel[] pagePanels;
	private PageThumbnail[] pageThumbnails;
	private boolean[] pageVisible;
	private LinkedList hiddenPageBanners = new LinkedList();
	
	private long renderingDpiModCount = 0;
	private long textStringPercentageModCount = 0;
	private long highlightModCount = 0;
	private long displayExtensionModCount = 0;
	
	private int maxPageWidth = 0;
	private int maxPageHeight = 0;
	private int fixPageMargin = 16;
	
	private int sideBySidePages = 1;
	
	private int pageThumbnailReductionFactor = 16;
	
	private int maxPageImageDpi = -1;
	private int renderingDpi = DEFAULT_RENDERING_DPI;
	
	private boolean textStreamsPainted = false;
	private TreeMap textStreamTypeColors = new TreeMap();
	
	private int textStringPercentage = 0;
	private Color ocrTextStringBackground = new Color(Color.WHITE.getRed(), Color.WHITE.getGreen(), Color.WHITE.getBlue(), ((this.textStringPercentage * 255) / 100));
	private Color ocrTextStringForeground = new Color(Color.BLACK.getRed(), Color.BLACK.getGreen(), Color.BLACK.getBlue(), ((this.textStringPercentage * 255) / 100));
	private IndexColorModel ocrTextStringColorModel = createTextStringColorModel(this.ocrTextStringBackground, this.ocrTextStringForeground);
	
	private static Color bdBlackTextStringBackground = new Color(Color.WHITE.getRed(), Color.WHITE.getGreen(), Color.WHITE.getBlue(), 0);
	private static Color bdBlackTextStringForeground = new Color(Color.BLACK.getRed(), Color.BLACK.getGreen(), Color.BLACK.getBlue(), 255);
	private static IndexColorModel bdBlackTextStringColorModel = createTextStringColorModel(bdBlackTextStringBackground, bdBlackTextStringForeground);
	
	private static Color bdWhiteTextStringBackground = new Color(Color.WHITE.getRed(), Color.WHITE.getGreen(), Color.WHITE.getBlue(), 0);
	private static Color bdWhiteTextStringForeground = new Color(Color.WHITE.getRed(), Color.WHITE.getGreen(), Color.WHITE.getBlue(), 255);
	private static IndexColorModel bdWhiteTextStringColorModel = createTextStringColorModel(bdWhiteTextStringBackground, bdWhiteTextStringForeground);
	
	//	font preference switch, mainly for testing Liberation Fonts against default system fonts
	private static final boolean USE_FREE_FONTS = true;
	
	/* make sure we have the fonts we need */
	static {
		if (USE_FREE_FONTS)
			ImFontUtils.loadFreeFonts();
	}
	
	private static Font serifFont = new Font((USE_FREE_FONTS ? "FreeSerif" : "Serif"), Font.PLAIN, 1);
	private static Font sansFont = new Font((USE_FREE_FONTS ? "FreeSans" : "SansSerif"), Font.PLAIN, 1);
	
	private HashSet paintedLayoutObjectTypes = new HashSet();
	private TreeMap layoutObjectColors = new TreeMap();
	
	private static final int defaultAnnotationHighlightAlpha = 0x40;
	
	private HashSet paintedAnnotationTypes = new HashSet();
	private TreeMap annotationColors = new TreeMap();
	private TreeMap annotationHighlightColors = new TreeMap();
	private int annotationHighlightAlpha = defaultAnnotationHighlightAlpha;
	private int annotationHighlightMargin = 2;
	
	private static final Color defaultSelectionHighlightColor = new Color(Color.GREEN.getRed(), Color.GREEN.getGreen(), Color.GREEN.getBlue(), 128);
	private static final Color defaultSelectionBoxColor = Color.RED;
	private static final int defaultSelectionBoxThickness = 3;
	
	private Color selectionHighlightColor = defaultSelectionHighlightColor;
	private Color selectionBoxColor = defaultSelectionBoxColor;
	private BasicStroke selectionBoxStroke = defaultSelectionBoxStroke;
	private int selectionBoxThickness = defaultSelectionBoxThickness;
	
	private static final IndexColorModel pageImageColorModel = createPageImageColorModel();
	
	private ImWord selectionStartWord = null;
	private ImWord selectionEndWord = null;
	
	private Point selectionStartPoint = null;
	private Point selectionEndPoint = null;
	private ImPageMarkupPanel pointSelectionPage = null;
	
	private boolean selectionClicked = true;
	
	private DisplayOverlay displayOverlay = null;
	private int displayOverlayPage = -1;
	
	TwoClickSelectionAction pendingTwoClickAction = null;
	TwoClickActionMessenger twoClickActionMessenger = null;
	
	private long atomicActionId = 0;
	private ImageMarkupTool atomicActionImt = null;
	private final LinkedHashSet atomicActionListeners = new LinkedHashSet();
	
	/**
	 * Constructor displaying all pages (use with care, high memory
	 * consumption for large documents)
	 * @param document the document to display
	 */
	public ImDocumentMarkupPanel(ImDocument document) {
		this(document, document.getFirstPageId(), document.getPageCount());
	}
	
	/**
	 * Constructor displaying a custom range of pages.
	 * @param document the document to display
	 * @param fvp the ID of the first visible page
	 * @param vpc the number of pages to show from page <code>fvp</code> onward
	 */
	public ImDocumentMarkupPanel(ImDocument document, int fvp, int vpc) {
		super(new GridBagLayout(), true);
		this.setOpaque(false);
		this.document = document;
		
		//	TODO_not move this to config file ==> good to have defaults
		this.setTextStreamTypeColor(ImWord.TEXT_STREAM_TYPE_ARTIFACT, Color.DARK_GRAY);
		this.setTextStreamTypeColor(ImWord.TEXT_STREAM_TYPE_CAPTION, Color.MAGENTA);
		this.setTextStreamTypeColor(ImWord.TEXT_STREAM_TYPE_DELETED, Color.BLACK);
		this.setTextStreamTypeColor(ImWord.TEXT_STREAM_TYPE_FOOTNOTE, Color.ORANGE);
		this.setTextStreamTypeColor(ImWord.TEXT_STREAM_TYPE_MAIN_TEXT, Color.LIGHT_GRAY);
		this.setTextStreamTypeColor(ImWord.TEXT_STREAM_TYPE_PAGE_TITLE, Color.BLUE);
		this.setTextStreamTypeColor(ImWord.TEXT_STREAM_TYPE_TABLE, Color.PINK);
		this.setTextStreamTypeColor(ImWord.TEXT_STREAM_TYPE_TABLE_NOTE, Color.CYAN);
		this.setTextStreamTypeColor(ImWord.TEXT_STREAM_TYPE_LABEL, Color.YELLOW);
		
		ImPage[] pages = this.document.getPages();
		System.out.println("Got " + pages.length + " pages");
		int minPageId = Integer.MAX_VALUE;
		int maxPageId = 0;
		for (int p = 0; p < pages.length; p++) {
			minPageId = Math.min(minPageId, pages[p].pageId);
			maxPageId = Math.max(maxPageId, pages[p].pageId);
		}
		
		this.pages = new ImPage[maxPageId + 1];
		for (int p = 0; p < pages.length; p++)
			this.pages[pages[p].pageId] = pages[p];
		this.pagePanels = new ImPageMarkupPanel[this.pages.length];
		Arrays.fill(this.pagePanels, null);
		this.pageVisible = new boolean[this.pages.length];
		Arrays.fill(this.pageVisible, false);
		this.pageThumbnails = new PageThumbnail[this.pages.length];
		Arrays.fill(this.pageThumbnails, null);
		
		int pageCount = 0;
		int scanCount = 0;
		int wordCount = 0;
//		int fnWordCount = 0;
		int fWordCount = 0;
		int vfWordCount = 0;
		for (int p = 0; p < this.pages.length; p++) {
			if (this.pages[p] == null)
				continue;
			System.out.println(" - page " + p + " with ID " + this.pages[p].pageId + " at " + this.pages[p].getImageDPI() + " DPI");
			this.maxPageImageDpi = Math.max(this.maxPageImageDpi, this.pages[p].getImageDPI());
			pageCount++;
			ImSupplement[] pageSupplements = this.pages[p].getSupplements();
			for (int s = 0; s < pageSupplements.length; s++) {
				if (pageSupplements[s] instanceof ImSupplement.Scan)
					scanCount++;
			}
			ImWord[] pageWords = this.pages[p].getWords();
			int minWordLeft = this.pages[p].bounds.right;
			int maxWordRight = this.pages[p].bounds.left;
			int minWordTop = this.pages[p].bounds.bottom;
			int maxWordBottom = this.pages[p].bounds.top;
			for (int w = 0; w < pageWords.length; w++) {
				wordCount++;
//				if (pageWords[w].hasAttribute(ImWord.FONT_NAME_ATTRIBUTE))
//					fnWordCount++;
				ImFont wordFont = pageWords[w].getFont();
				if (wordFont != null) {
					fWordCount++;
					if (wordFont.hasAttribute(ImFont.VECTORIZED_ATTRIBUTE))
						vfWordCount++;
				}
				minWordLeft = Math.min(minWordLeft, pageWords[w].bounds.left);
				maxWordRight = Math.max(maxWordRight, pageWords[w].bounds.right);
				minWordTop = Math.min(minWordTop, pageWords[w].bounds.top);
				maxWordBottom = Math.max(maxWordBottom, pageWords[w].bounds.bottom);
			}
			System.out.println("   - bounds are " + this.pages[p].bounds + ", content in [" + minWordLeft + "," + maxWordRight + "," + minWordTop + "," + maxWordBottom + "]");
			int addLeft = Math.max(0, ((this.pages[p].getImageDPI() / 2) - (minWordLeft - this.pages[p].bounds.left))); // ensure at least half an inch margin on left
			int addRight = Math.max(0, ((this.pages[p].getImageDPI() / 2) - (this.pages[p].bounds.right - maxWordRight))); // ensure at least half an inch margin on right
			System.out.println("   - adding " + addLeft + " to left page margin, " + addRight + " to right page margin");
			this.maxPageWidth = Math.max(this.maxPageWidth, (addLeft + this.pages[p].bounds.getWidth() + addRight));
			int addTop = Math.max(0, ((this.pages[p].getImageDPI() / 2) - (minWordTop - this.pages[p].bounds.top))); // ensure at least half an inch margin at top
			int addBottom = Math.max(0, (((this.pages[p].getImageDPI() * 3) / 4) - (this.pages[p].bounds.bottom - maxWordBottom)));  // ensure at least three quarters of an inch margin at bottom
			System.out.println("   - adding " + addTop + " to top page margin, " + addBottom + " to bottom page margin");
			this.maxPageHeight = Math.max(this.maxPageHeight, (addTop + this.pages[p].bounds.getHeight() + addBottom));
		}
//		this.documentBornDigital = ((minPageWidth == this.maxPageWidth) && (minPageHeight == this.maxPageHeight));
//		//	we cannot use page width, as latter may be distorted in born-digital PDFs due to vertical block flips
//		this.documentBornDigital = (((wordCount * 2) < (fnWordCount * 3)) || ((pages.length > 2) && (minPageHeight == this.maxPageHeight)));
//		this.documentBornDigital = ((wordCount * 2) < (fnWordCount * 3));
		this.documentBornDigital = ((wordCount * 2) < (fWordCount * 3));
		this.documentPagesBornDigital = ((scanCount * 3) < (pageCount * 2)); // fewer than two thirds or pages have scans
		if ((fWordCount * 3) < (wordCount * 2)) // fewer than two thirds of words bound to font at all
			this.documentTextOrigin = TEXT_ORIGIN_OCR;
		else if ((fWordCount * 2) < (vfWordCount * 3)) // more than two thirds of font-bound words bound to vetorized fonts
			this.documentTextOrigin = TEXT_ORIGIN_VECTORIZED;
		else if (this.documentPagesBornDigital) // no indication of scans at all
			this.documentTextOrigin = TEXT_ORIGIN_BORN_DIGITAL;
		else this.documentTextOrigin = TEXT_ORIGIN_DIGITIZED_OCR; // normal digital fonts, but on top of scanned page backgrounds
		
		for (int p = Math.max(0, fvp); p < Math.min(this.pages.length, (fvp + vpc)); p++) {
			if (this.pages[p] == null)
				continue;
			this.pagePanels[p] = new ImPageMarkupPanel(this.pages[p], this.maxPageWidth, this.maxPageHeight);
			this.pageVisible[p] = true;
		}
		
		this.layoutPages();
		
		MouseAdapter ma = new MouseTracker();
		this.addMouseListener(ma);
		this.addMouseMotionListener(ma);
		
		this.document.addDocumentListener(new ImDocumentChangeTracker());
	}
	
	/**
	 * Check whether or not a bit vector document origin indicates born-digital
	 * page backgrounds.
	 * @param docOrigin the document origin to check
	 * @return true if the argument document origin indicates born-digital pages
	 */
	public boolean arePagesBordDigital() {
		return this.documentPagesBornDigital;
	}
	
	/**
	 * Check whether or not the text of the document showing in this markup
	 * panel is rendered via 'normal' fonts.
	 * @return true if the displaying document uses 'normal' fonts for its text
	 */
	public boolean textUsesNormalFonts() {
		return ((this.documentTextOrigin == TEXT_ORIGIN_BORN_DIGITAL) || (this.documentTextOrigin == TEXT_ORIGIN_DIGITIZED_OCR));
	}
	
	/**
	 * Check whether or not the text of the document showing in this markup
	 * panel  representing the original state of the source document.
	 * @return true if the displaying document shows text in its original state
	 */
	public boolean textShowsOriginalState() {
		return ((this.documentTextOrigin == TEXT_ORIGIN_BORN_DIGITAL) || (this.documentTextOrigin == TEXT_ORIGIN_VECTORIZED));
	}
	
	private class MouseTracker extends MouseAdapter {
		public void mousePressed(MouseEvent me) {
//			System.out.println("Mouse pressed");
			ImPageMarkupPanel ipmp = this.getPageFor(me);
			if (ipmp == null)
				return;
			if (ipmp.pageImageDpi == -1)
				return;
			if (displayOverlay != null)
				displayOverlay.close(displayOverlay.cancelOnOutsideClick());
			Point ipmpLocation = ipmp.getLocation();
			float zoom = (((float) ipmp.pageImageDpi) / renderingDpi);
			int x = (Math.round(zoom * (me.getX() - fixPageMargin - ipmpLocation.x)) - ipmp.pageMarginLeft);
			int y = (Math.round(zoom * (me.getY() - fixPageMargin - ipmpLocation.y)) - ipmp.pageMarginTop);
			ImWord imw = ipmp.page.getWordAt(x, y);
			
			//	click outside any words ==> start box selection
			if (imw == null) {
				selectionStartWord = null; // clean any externally injected selection
				selectionEndWord = null; // clean any externally injected selection
				selectionStartPoint = new Point(x, y);
				pointSelectionPage = ipmp;
				selectionClicked = true;
			}
			
			//	click on word, and no externally injected selection ==> start word selection 
			else if ((selectionStartWord == null) || (selectionEndWord == null)) {
				selectionStartWord = imw;
				selectionEndWord = imw;
				selectionClicked = true;
			}
			
			//	click on word in same logical text stream as externally injected selection
			else if (selectionStartWord.getTextStreamId().equals(imw.getTextStreamId())) {
				
				//	click on word before externally injected selection ==> extend selection if shift pressed, start new one otherwise
				if (imw.getTextStreamPos() < selectionStartWord.getTextStreamPos()) {
					selectionStartWord = imw;
					if (me.isShiftDown())
						selectionClicked = false;
					else {
						selectionEndWord = imw;
						selectionClicked = true;
					}
				}
				
				//	click on word after externally injected selection ==> extend selection if shift pressed, start new one otherwise
				else if (selectionEndWord.getTextStreamPos() < imw.getTextStreamPos()) {
					selectionEndWord = imw;
					if (me.isShiftDown())
						selectionClicked = false;
					else {
						selectionStartWord = imw;
						selectionClicked = true;
					}
				}
				
				//	click on word inside externally injected selection ==> retain selection, but accept click only if single word selected
				else if (selectionStartWord != selectionEndWord)
					selectionClicked = false;
			}
			repaint();
		}
		
		public void mouseReleased(final MouseEvent me) {
//			System.out.println("Mouse released");
			if (selectionClicked)
				return;
			ImPageMarkupPanel ipmp = this.getPageFor(me);
			if (ipmp == null) {
				this.updateCursor(me, ipmp);
				return;
			}
			this.showContextMenu(me, ipmp);
		}
		
		public void mouseClicked(final MouseEvent me) {
//			System.out.println("Mouse clicked " + me.getClickCount() + " times at " + me.getX() + "/" + me.getY());
//			System.out.println("Multi-click interval is " + Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval"));
//			System.out.println("Time since last click is " + (me.getWhen() - this.lastClickWhen));
//			this.lastClickWhen = me.getWhen();
			final ImPageMarkupPanel ipmp = this.getPageFor(me);
			if (ipmp == null) {
				this.updateCursor(me, ipmp);
				return;
			}
			
			//	handle any pending two-click action first
			if (pendingTwoClickAction != null) {
				boolean handleAtomicAction = (pendingTwoClickAction.isAtomicAction() && !isAtomicActionRunning());
				try {
					if (handleAtomicAction) // might have been started from built-in click listener in selection action
						beginAtomicAction(pendingTwoClickAction.label);
					if (selectionStartWord != null)
						pendingTwoClickAction.performAction(selectionStartWord);
					else if (selectionStartPoint != null)
						pendingTwoClickAction.performAction(ipmp.page, selectionStartPoint);
				}
				finally {
					if (handleAtomicAction)
						endAtomicAction();
				}
				cleanupSelection();
				repaint();
				this.updateCursor(me, ipmp);
				return;
			}
			
			//	cancel any context menu coming up for previous click (should be OK without synchronization lock because it all happens on EDT)
			if (this.clickContextMenu != null)
				this.clickContextMenu.cancel();
			
			//	observe click actions first (both for word and for point selections)
			ClickSelectionAction[] clickActions;
			if (selectionStartWord != null)
				clickActions = getClickActions(selectionStartWord, me.getClickCount());
			else if (selectionStartPoint != null)
				clickActions = getClickActions(ipmp.page, selectionStartPoint, me.getClickCount());
			else clickActions = null;
			if ((clickActions != null) && (clickActions.length != 0)) {
				Arrays.sort(clickActions);
				for (int a = 0; a < clickActions.length; a++)
					if (clickActions[a].handleClick(ImDocumentMarkupPanel.this)) {
						cleanupSelection();
						repaint();
						this.updateCursor(me, ipmp);
						return;
					}
			}
			
			//	normally show context menu as default action (wait for multi-click on very first click)
			if (me.getClickCount() < 2) // TODO increase this threshold if we have triple-click actions at some point
				this.clickContextMenu = new DelayedClickContextMenu(me, ipmp);
			else this.showContextMenu(me, ipmp);
		}
		private DelayedClickContextMenu clickContextMenu = null;
		private class DelayedClickContextMenu extends Timer implements ActionListener {
			private MouseEvent me;
			private ImPageMarkupPanel ipmp;
			DelayedClickContextMenu(MouseEvent me, ImPageMarkupPanel ipmp) {
				/* waiting 250ms is a good compromise between reliably catching
				 * double clicks (usual gap is ~200ms) and having user wait
				 * unnecessarily long on single click */
				super(250, null);
				this.me = me;
				this.ipmp = ipmp;
				this.setRepeats(false);
				this.addActionListener(this); // cannot pass 'this' to super constructor
				this.start();
			}
			public void actionPerformed(ActionEvent ae) {
				clickContextMenu = null;
				showContextMenu(this.me, this.ipmp);
			}
			void cancel() {
				this.stop();
			}
		}
		
		private void showContextMenu(final MouseEvent me, final ImPageMarkupPanel ipmp) {
			if (selectionEndWord == null)
				selectionEndWord = selectionStartWord;
			if (selectionEndPoint == null)
				selectionEndPoint = selectionStartPoint;
			final SelectionAction[] actions;
			if (selectionStartWord != null) {
				if (selectionStartWord.getTextStreamId().equals(selectionEndWord.getTextStreamId()) && (selectionEndWord.getTextStreamPos() < selectionStartWord.getTextStreamPos())) {
					ImWord imw = selectionStartWord;
					selectionStartWord = selectionEndWord;
					selectionEndWord = imw;
				}
				actions = getActions(selectionStartWord, selectionEndWord);
			}
			else if (selectionStartPoint != null) {
				this.ensurePointInPageBounds(selectionStartPoint, ipmp.page);
				this.ensurePointInPageBounds(selectionEndPoint, ipmp.page);
				actions = getActions(ipmp.page, selectionStartPoint, selectionEndPoint);
			}
			else actions = null;
			if ((actions == null) || (actions.length == 0)) {
				cleanupSelection();
				repaint();
				this.updateCursor(me, ipmp);
				System.err.println("Empty context menu");
				return;
			}
			final JPopupMenu pm = new JPopupMenu();
			final boolean[] isAdvancedSelectionAction = markAdvancedSelectionActions(actions);
			final JMenuItem[] mis = new JMenuItem[actions.length];
			int advancedSelectionActionCount = 0;
			for (int a = 0; a < actions.length; a++) {
				if (actions[a] == SelectionAction.SEPARATOR)
					continue;
//				mis[a] = actions[a].getMenuItem(ImDocumentMarkupPanel.this);
				mis[a] = actions[a].getContextMenuItem(ImDocumentMarkupPanel.this);
				this.addNotifier(mis[a], actions[a]);
				if (isAdvancedSelectionAction[a])
					advancedSelectionActionCount++;
			}
			this.fillContextMenu(pm, mis, isAdvancedSelectionAction);
			if (advancedSelectionActionCount != 0) {
				JMenuItem mmi = new JMenuItem("More ...");
				mmi.setBorder(BorderFactory.createLoweredBevelBorder());
				mmi.setBackground(new Color(240, 240, 240));
				mmi.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent ae) {
						fillContextMenu(pm, mis, null);
						pm.show(ImDocumentMarkupPanel.this, me.getX(), me.getY());
					}
				});
				pm.add(mmi);
			}
			pm.addPopupMenuListener(new PopupMenuListener() {
				public void popupMenuWillBecomeVisible(PopupMenuEvent pme) {}
				public void popupMenuWillBecomeInvisible(PopupMenuEvent pme) {}
				public void popupMenuCanceled(PopupMenuEvent pme) {
					cleanupSelection();
					updateCursor(me, ipmp);
				}
			});
			pm.show(ImDocumentMarkupPanel.this, me.getX(), me.getY());
		}
		private void ensurePointInPageBounds(Point point, ImPage page) {
			if (point.x < page.bounds.left)
				point.x = page.bounds.left;
			else if (page.bounds.right < point.x)
				point.x = page.bounds.right;
			if (point.y < page.bounds.top)
				point.y = page.bounds.top;
			else if (page.bounds.bottom < point.y)
				point.y = page.bounds.bottom;
		}
		private void fillContextMenu(JPopupMenu pm, JMenuItem[] mis, boolean[] isAdvancedSelectionAction) {
			int windowHeight = Integer.MAX_VALUE;
			Window topWindow = DialogFactory.getTopWindow();
			if ((topWindow != null) && topWindow.isVisible())
				windowHeight = topWindow.getHeight();
			else if (GraphicsEnvironment.isHeadless()) {}
			else {
				Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
				if (screenSize != null)
					windowHeight = screenSize.height;
			}
			pm.removeAll();
			boolean lastWasSeparator = true;
			int misHeight = 0;
			JMenu subMenu = null;
			for (int i = 0; i < mis.length; i++) {
				
				//	separator
				if (mis[i] == null) {
					if (!lastWasSeparator) {
						if (subMenu == null)
							pm.addSeparator();
						else subMenu.addSeparator();
					}
					lastWasSeparator = true;
					continue;
				}
				
				//	hide advanced actions in basic mode
				if ((isAdvancedSelectionAction != null) && isAdvancedSelectionAction[i])
					continue;
				
				//	test if menu items up to next separator fit current menu, and wrap into (new) sub menu if not (unless current (sub) menu empty or only one menu item left to add)
//				if (lastWasSeparator && (isAdvancedSelectionAction == null) && (misHeight != 0) && ((i+1) < mis.length)) {
				if (lastWasSeparator && (misHeight != 0) && ((i+1) < mis.length)) {
					int lMisHeight = 0;
					for (int li = i; li < mis.length; li++) {
						if (mis[li] == null)
							break; // got next separator, group ends
						lMisHeight += mis[li].getPreferredSize().height;
					}
					if (windowHeight < (misHeight + lMisHeight)) {
						JMenu mm = new JMenu("More ...");
						mm.setBorder(BorderFactory.createLoweredBevelBorder());
						mm.setBackground(new Color(240, 240, 240));
						if (subMenu == null)
							pm.add(mm);
						else subMenu.add(mm);
						subMenu = mm;
						misHeight = 0;
					}
				}
				
				//	add menu item to current (sub) menu
				lastWasSeparator = false;
				if (subMenu == null)
					pm.add(mis[i]);
				else subMenu.add(mis[i]);
				misHeight += mis[i].getPreferredSize().height;
			}
		}
		
		private void addNotifier(JMenuItem mi, final SelectionAction sa) {
			if (mi instanceof JMenu) {
				Component[] smcs = ((JMenu) mi).getMenuComponents();
				for (int c = 0; c < smcs.length; c++) {
					if (smcs[c] instanceof JMenuItem)
						this.addNotifier(((JMenuItem) smcs[c]), sa);
				}
			}
			else mi.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent ae) {
					cleanupSelection();
					selectionActionPerformed(sa);
				}
			});
		}
		
		public void mouseMoved(MouseEvent me) {
			this.updateCursor(me, this.getPageFor(me));
		}
		
		private void updateCursor(MouseEvent me, ImPageMarkupPanel ipmp) {
			if (ipmp == null) {
				setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
				return;
			}
			Point ipmpLocation = ipmp.getLocation();
			float zoom = (((float) ipmp.pageImageDpi) / renderingDpi);
			int x = (Math.round(zoom * (me.getX() - fixPageMargin - ipmpLocation.x)) - ipmp.pageMarginLeft);
			int y = (Math.round(zoom * (me.getY() - fixPageMargin - ipmpLocation.y)) - ipmp.pageMarginTop);
			ImWord imw = ipmp.page.getWordAt(x, y);
			if (imw == null) {
				boolean inImageOrGraphics = false;
				boolean inTable = false;
				boolean inTableRowOrCol = false;
				ImRegion[] regions = ipmp.page.getRegionsIncluding(new BoundingBox(x, (x+2), y, (y+2)), false);
				for (int r = 0; r < regions.length; r++) {
					if (!areRegionsPainted(regions[r].getType()))
						continue;
					if (ImRegion.IMAGE_TYPE.equals(regions[r].getType()) || ImRegion.GRAPHICS_TYPE.equals(regions[r].getType())) {
						inImageOrGraphics = true;
						break;
					}
					if (ImRegion.TABLE_TYPE.equals(regions[r].getType()))
						inTable = true;
					if (ImRegion.TABLE_ROW_TYPE.equals(regions[r].getType()) || ImRegion.TABLE_COL_TYPE.equals(regions[r].getType()) || ImRegion.TABLE_CELL_TYPE.equals(regions[r].getType())) {
						inTable = true;
						inTableRowOrCol = true;
						break;
					}
				}
				if (inTableRowOrCol)
					setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
				else if (inImageOrGraphics || inTable)
					setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				else setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
			}
			else if (areTextStringsPainted())
				setCursor(getCustomCursor("DarkPencil"));
			else setCursor(getCustomCursor("MagicMarker"));
		}
		
		public void mouseDragged(MouseEvent me) {
			ImPageMarkupPanel ipmp = this.getPageFor(me);
			if ((ipmp == null) || ((pointSelectionPage != null) && (ipmp != pointSelectionPage))) {
				selectionStartPoint = null;
				selectionEndPoint = null;
				if (pointSelectionPage != null)
					pointSelectionPage.repaint();
				pointSelectionPage = null;
				selectionClicked = false;
				return;
			}
			selectionClicked = false;
			pendingTwoClickAction = null;
			if (twoClickActionMessenger != null)
				twoClickActionMessenger.twoClickActionChanged(null);
			if (ipmp.pageImageDpi == -1)
				return;
			if ((selectionStartWord == null) && (selectionStartPoint == null))
				return;
			Point ipmpLocation = ipmp.getLocation();
			float zoom = (((float) ipmp.pageImageDpi) / renderingDpi);
			int x = (Math.round(zoom * (me.getX() - fixPageMargin - ipmpLocation.x)) - ipmp.pageMarginLeft);
			int y = (Math.round(zoom * (me.getY() - fixPageMargin - ipmpLocation.y)) - ipmp.pageMarginTop);
			ImWord imw = ipmp.page.getWordAt(x, y);
			if ((selectionStartWord != null) && (imw != null)) {
				selectionEndWord = imw;
				repaint();
			}
			else if (selectionStartPoint != null) {
				selectionEndPoint = new Point(x, y);
				repaint();
			}
		}
		
		private ImPageMarkupPanel getPageFor(MouseEvent me) {
			Component pvp = getComponentAt(me.getX(), me.getY());
			return ((pvp instanceof ImPageMarkupPanel) ? ((ImPageMarkupPanel) pvp) : null);
		}
	}
	
	private void cleanupSelection() {
		this.selectionStartWord = null;
		this.selectionEndWord = null;
		this.selectionStartPoint = null;
		this.selectionEndPoint = null;
		this.pointSelectionPage = null;
		this.selectionClicked = false;
		this.pendingTwoClickAction = null;
		if (this.twoClickActionMessenger != null)
			this.twoClickActionMessenger.twoClickActionChanged(null);
	}
	
	private static TreeMap customCursorsByName = new TreeMap();
	private static Cursor getCustomCursor(String name) {
		return getCustomCursor(name, new Point(0, 0));
	}
	private static synchronized Cursor getCustomCursor(String name, Point hotSpot) {
		Cursor customCursor = ((Cursor) customCursorsByName.get(name));
		if (customCursor != null)
			return customCursor;
		String cursorResourceName = ImDocumentMarkupPanel.class.getName();
		cursorResourceName = cursorResourceName.substring(0, cursorResourceName.lastIndexOf('.'));
		cursorResourceName = (cursorResourceName.replaceAll("\\.", "/") + "/cursor." + name + ".png");
		try {
			BufferedImage cursorImage = ImageIO.read(ImDocumentMarkupPanel.class.getClassLoader().getResourceAsStream(cursorResourceName));
			int[][] cursorImageRegions = Imaging.getRegionColoring(Imaging.wrapImage(cursorImage, null), ((byte) -112), false);
			for (int c = 0; c < cursorImage.getWidth(); c++)
				for (int r = 0; r < cursorImage.getHeight(); r++) {
					if (cursorImageRegions[c][r] == 1)
						cursorImage.setRGB(c, r, transparentWhite);
				}
			customCursor = Toolkit.getDefaultToolkit().createCustomCursor(cursorImage, hotSpot, name);
			customCursorsByName.put(name, customCursor);
			return customCursor;
		}
		catch (Exception e) {
			return Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR);
		}
	}
	
	private class ImDocumentChangeTracker implements ImDocumentListener {
		public void typeChanged(ImObject object, String oldType) {
			if (idvc != null) {
				if (immediatelyUpdateIdvc)
					idvc.updateControls();
				else idvcDocModCount++;
			}
			if (object instanceof ImLayoutObject) {
				if (pagePanels[((ImLayoutObject) object).pageId] != null)
					pagePanels[((ImLayoutObject) object).pageId].docModCount++;
			}
			else if (object instanceof ImAnnotation)
				for (int p = ((ImAnnotation) object).getFirstWord().pageId; p <= ((ImAnnotation) object).getLastWord().pageId; p++) {
					if (pagePanels[p] != null)
						pagePanels[p].docModCount++;
				}
		}
		public void attributeChanged(ImObject object, String attributeName, Object oldValue) {
			if ((object instanceof ImWord) && (pagePanels[((ImWord) object).pageId] != null)) {
				if (ImWord.NEXT_RELATION_ATTRIBUTE.equals(attributeName)) {
					pagePanels[((ImWord) object).pageId].docModCount++;
					if ((((ImWord) object).getNextWord() != null) && (((ImWord) object).pageId != ((ImWord) object).getNextWord().pageId) && (pagePanels[((ImWord) object).getNextWord().pageId] != null))
						pagePanels[((ImWord) object).getNextWord().pageId].docModCount++;
				}
				else if (ImWord.NEXT_WORD_ATTRIBUTE.equals(attributeName))
					pagePanels[((ImWord) object).pageId].docModCount++;
				else if (ImWord.PREVIOUS_WORD_ATTRIBUTE.equals(attributeName))
					pagePanels[((ImWord) object).pageId].docModCount++;
				else if (ImWord.TEXT_STREAM_TYPE_ATTRIBUTE.equals(attributeName))
					pagePanels[((ImWord) object).pageId].docModCount++;
				else if (ImWord.STRING_ATTRIBUTE.equals(attributeName))
					pagePanels[((ImWord) object).pageId].docModCount++;
				else if (ImWord.BOLD_ATTRIBUTE.equals(attributeName))
					pagePanels[((ImWord) object).pageId].docModCount++;
				else if (ImWord.ITALICS_ATTRIBUTE.equals(attributeName))
					pagePanels[((ImWord) object).pageId].docModCount++;
			}
			else if ((object instanceof ImAnnotation) && ImAnnotation.FIRST_WORD_ATTRIBUTE.equals(attributeName)) {
				ImWord fw = ((ImUtils.textStreamOrder.compare(((ImAnnotation) object).getFirstWord(), ((ImWord) oldValue)) < 0) ? ((ImAnnotation) object).getFirstWord() : ((ImWord) oldValue));
				ImWord lw = ((ImAnnotation) object).getLastWord();
				for (int p = fw.pageId; p <= lw.pageId; p++) {
					if (pagePanels[p] != null)
						pagePanels[p].docModCount++;
				}
			}
			else if ((object instanceof ImAnnotation) && ImAnnotation.LAST_WORD_ATTRIBUTE.equals(attributeName)) {
				ImWord fw = ((ImAnnotation) object).getFirstWord();
				ImWord lw = ((ImUtils.textStreamOrder.compare(((ImAnnotation) object).getLastWord(), ((ImWord) oldValue)) < 0) ? ((ImWord) oldValue) : ((ImAnnotation) object).getLastWord());
				for (int p = fw.pageId; p <= lw.pageId; p++) {
					if (pagePanels[p] != null)
						pagePanels[p].docModCount++;
				}
			}
			else if ((object instanceof ImPage) && ImPage.PAGE_IMAGE_ATTRIBUTE.equals(attributeName)) {
//				pagePanels[((ImPage) object).pageId].scaledPageImageWeak = null;
				pagePanels[((ImPage) object).pageId].scaledPageImage = null;
				pagePanels[((ImPage) object).pageId].scaledPageImageDpi = -1;
				pagePanels[((ImPage) object).pageId].backgroundObjects = null;
				pagePanels[((ImPage) object).pageId].textStringImages = null;
				pagePanels[((ImPage) object).pageId].docModCount++;
				pagePanels[((ImPage) object).pageId].validate();
				pagePanels[((ImPage) object).pageId].repaint();
			}
		}
		public void supplementChanged(String supplementId, ImSupplement oldValue) {
			//	there are no visible changes from supplement modifications
		}
		public void fontChanged(String fontName, ImFont oldValue) {
			//	there are no visible changes from supplement modifications
		}
		public void regionAdded(ImRegion region) {
			if (idvc != null) {
				if (immediatelyUpdateIdvc)
					idvc.updateControls();
				else idvcDocModCount++;
			}
			if (pagePanels[region.pageId] != null)
				pagePanels[region.pageId].docModCount++;
		}
		public void regionRemoved(ImRegion region) {
			if (idvc != null) {
				if (immediatelyUpdateIdvc)
					idvc.updateControls();
				else idvcDocModCount++;
			}
			if (pagePanels[region.pageId] != null)
				pagePanels[region.pageId].docModCount++;
		}
		public void annotationAdded(ImAnnotation annotation) {
			if (idvc != null) {
				if (immediatelyUpdateIdvc)
					idvc.updateControls();
				else idvcDocModCount++;
			}
			for (int p = annotation.getFirstWord().pageId; p <= annotation.getLastWord().pageId; p++) {
				if (pagePanels[p] != null)
					pagePanels[p].docModCount++;
			}
		}
		public void annotationRemoved(ImAnnotation annotation) {
			if (idvc != null) {
				if (immediatelyUpdateIdvc)
					idvc.updateControls();
				else idvcDocModCount++;
			}
			for (int p = annotation.getFirstWord().pageId; p <= annotation.getLastWord().pageId; p++) {
				if (pagePanels[p] != null)
					pagePanels[p].docModCount++;
			}
		}
	}
	
	private void layoutPages() {
		this.removeAll();
		this.hiddenPageBanners.clear();
		
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets.left = 10;
		gbc.insets.right = 10;
		gbc.insets.top = 10;
		gbc.insets.bottom = 10;
		gbc.gridwidth = 1;
		gbc.gridheight = 1;
		gbc.weightx = 1;
		gbc.weighty = 1;
		gbc.gridx = 0;
		gbc.gridy = 0;
		
		HiddenPageBanner hpb = null;
		
		for (int p = 0; p < this.pagePanels.length; p++) {
			if (this.pages[p] == null)
				continue;
			if (!this.pageVisible[p]) {
				if (this.sideBySidePages < 2) {
					if (hpb == null)
						hpb = new HiddenPageBanner();
					hpb.addPage(this.pages[p]);
				}
				continue;
			}
			if (hpb != null) {
				this.add(hpb, gbc.clone());
				this.hiddenPageBanners.add(hpb);
				hpb = null;
				gbc.gridx++;
				if ((0 < this.sideBySidePages) && (this.sideBySidePages <= gbc.gridx)) {
					gbc.gridx = 0;
					gbc.gridy++;
				}
			}
			this.add(this.pagePanels[p], gbc.clone());
			gbc.gridx++;
			if ((0 < this.sideBySidePages) && (this.sideBySidePages <= gbc.gridx)) {
				gbc.gridx = 0;
				gbc.gridy++;
			}
		}
		
		if (hpb != null) {
			this.add(hpb, gbc.clone());
			this.hiddenPageBanners.add(hpb);
			hpb = null;
			gbc.gridx++;
			if ((0 < this.sideBySidePages) && (this.sideBySidePages <= gbc.gridx)) {
				gbc.gridx = 0;
				gbc.gridy++;
			}
		}
		
		this.getLayout().layoutContainer(this);
		this.validate();
		this.repaint();
	}
	
	private class HiddenPageBanner extends JPanel {
		private int minPageId = document.getPageCount();
		private int maxPageId = 0;
		private int maxPageTileHeight;
		private int maxPageTileWidth;
		private LinkedList pageTiles = new LinkedList();
		private JPanel pageTilePanel = new JPanel(new GridBagLayout(), true);
		private JScrollPane pageTilePanelBox;
		private GridBagConstraints gbc = new GridBagConstraints();
		private JPanel fillerPanel = new JPanel();
		
		HiddenPageBanner() {
			super(new BorderLayout(), true);
			this.gbc.insets.left = 2;
			this.gbc.insets.right = 2;
			this.gbc.insets.top = 2;
			this.gbc.insets.bottom = 2;
			this.gbc.gridwidth = 1;
			this.gbc.gridheight = 1;
			this.gbc.weightx = 0;
			this.gbc.weighty = 0;
			this.gbc.gridx = 0;
			this.gbc.gridy = 0;
			this.gbc.fill = ((sideBySidePages == 1) ? GridBagConstraints.VERTICAL : GridBagConstraints.HORIZONTAL);
			
			this.pageTilePanel.setBackground(Color.WHITE);
			this.fillerPanel.setBackground(Color.WHITE);
			
			this.pageTilePanelBox = new JScrollPane(this.pageTilePanel);
			this.pageTilePanelBox.setHorizontalScrollBarPolicy((sideBySidePages == 1) ? JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED : JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
			this.pageTilePanelBox.setVerticalScrollBarPolicy((sideBySidePages == 1) ? JScrollPane.VERTICAL_SCROLLBAR_NEVER : JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
			
			//	set scroll distances
			final JScrollBar vsb = this.pageTilePanelBox.getVerticalScrollBar();
			vsb.setUnitIncrement(33);
			vsb.setBlockIncrement(100);
			final JScrollBar hsb = this.pageTilePanelBox.getHorizontalScrollBar();
			hsb.setUnitIncrement(33);
			hsb.setBlockIncrement(100);
			this.addComponentListener(new ComponentAdapter() {
				public void componentResized(ComponentEvent ce) {
					Rectangle ptpvs = pageTilePanelBox.getViewport().getViewRect();
					vsb.setUnitIncrement(ptpvs.height / 10);
					vsb.setBlockIncrement(ptpvs.height / 3);
					hsb.setUnitIncrement(ptpvs.width / 10);
					hsb.setBlockIncrement(ptpvs.width / 3);
				}
			});
			
			this.add(this.pageTilePanelBox, BorderLayout.CENTER);
		}
		
		public Dimension getPreferredSize() {
			if ((this.preferredSize == null) || (this.preferredSizeDpi != renderingDpi) || (this.preferredSizeSbsp != sideBySidePages)) {
				this.maxPageTileWidth = 0;
				this.maxPageTileHeight = 0;
				for (Iterator hptit = this.pageTiles.iterator(); hptit.hasNext();)
					((HiddenPageTile) hptit.next()).getPreferredSize();
				if (sideBySidePages < 1)
					this.preferredSize = new Dimension((this.maxPageTileWidth + 10 + this.pageTilePanelBox.getVerticalScrollBar().getSize().width), (Math.round(((float) (maxPageHeight * renderingDpi)) / maxPageImageDpi) + (fixPageMargin * 2))); // pages side-by-side, use vertical layout
				else this.preferredSize = new Dimension((Math.round(((float) (maxPageWidth * renderingDpi)) / maxPageImageDpi) + (fixPageMargin * 2)), (this.maxPageTileHeight + 10 + this.pageTilePanelBox.getHorizontalScrollBar().getSize().height)); // pages on top of one another, use horizontal layout
				this.preferredSizeDpi = renderingDpi;
				this.preferredSizeSbsp = sideBySidePages;
			}
			return this.preferredSize;
		}
		private Dimension preferredSize = null;
		private int preferredSizeDpi = 0;
		private int preferredSizeSbsp = 0;
		
		public void invalidate() {
			this.preferredSize = null;
			super.invalidate();
		}
		
		void addPage(ImPage page) {
			this.minPageId = Math.min(this.minPageId, page.pageId);
			this.maxPageId = Math.max(this.maxPageId, page.pageId);
			this.pageTilePanel.remove(this.fillerPanel);
			HiddenPageTile hpt = new HiddenPageTile(page);
			this.pageTiles.add(hpt);
			this.gbc.weightx = 0;
			this.gbc.weighty = 0;
			this.pageTilePanel.add(hpt, this.gbc.clone());
			if (sideBySidePages < 1)
				this.gbc.gridy++;
			else if (sideBySidePages == 1)
				this.gbc.gridx++;
			this.gbc.weightx = 1;
			this.gbc.weighty = 1;
			this.pageTilePanel.add(this.fillerPanel, this.gbc.clone());
			this.validate();
			this.repaint();
		}
		
		private class HiddenPageTile extends JPanel {
			private PageThumbnail pageThumbnail;
			HiddenPageTile(final ImPage page) {
				super(new BorderLayout(), true);
				this.pageThumbnail = getPageThumbnail(page);
				this.setToolTipText(this.pageThumbnail.getTooltipText());
				this.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY, 1));
				this.addMouseListener(new MouseAdapter() {
					public void mouseClicked(MouseEvent me) {
						JPopupMenu pm = new JPopupMenu();
						JMenuItem mi;
						mi = new JMenuItem("Show Page " + page.pageId);
						mi.addActionListener(new ActionListener() {
							public void actionPerformed(ActionEvent ae) {
								setPageVisible(page.pageId, true);
							}
						});
						pm.add(mi);
						if (minPageId < page.pageId) {
							mi = new JMenuItem("Show Pages " + minPageId + "-" + page.pageId);
							mi.addActionListener(new ActionListener() {
								public void actionPerformed(ActionEvent ae) {
									setPagesVisible(minPageId, page.pageId, true);
								}
							});
							pm.add(mi);
						}
						if (page.pageId < maxPageId) {
							mi = new JMenuItem("Show Pages " + page.pageId + "-" + maxPageId);
							mi.addActionListener(new ActionListener() {
								public void actionPerformed(ActionEvent ae) {
									setPagesVisible(page.pageId, maxPageId, true);
								}
							});
							pm.add(mi);
						}
						pm.show(HiddenPageTile.this, me.getX(), me.getY());
					}
				});
				
				//	initialize size
				this.getPreferredSize();
			}
			
			public void invalidate() {
				this.preferredSize = null;
				super.invalidate();
			}
			
			public void paint(Graphics g) {
				super.paint(g);
				this.pageThumbnail.paint(g, 1, 1, this);
			}
			
			public Dimension getPreferredSize() {
				if ((this.preferredSize == null) || (this.preferredSizeDpi != renderingDpi)) {
					Dimension ptps = this.pageThumbnail.getPreferredSize();
					this.preferredSize = new Dimension((ptps.width + 2), (ptps.height + 2));
					this.preferredSizeDpi = renderingDpi;
				}
				maxPageTileWidth = Math.max(maxPageTileWidth, this.preferredSize.width);
				maxPageTileHeight = Math.max(maxPageTileHeight, this.preferredSize.height);
				return this.preferredSize;
			}
			private Dimension preferredSize = null;
			private int preferredSizeDpi = 0;
		}
	}
	
	/**
	 * Set the display overlay to use and the page to display it on (or remove
	 * the current overlay by setting it to null). If the page with the
	 * argument ID is out of range or not visible, this method still sets the
	 * overlay, but does not add it to any page and returns false.
	 * Sub classes overwriting this implementation, e.g. to move the overlay
	 * into the visible part of a scroll pane, should first check the result of
	 * the super call to check if selection was successful.
	 * @param overlay the overlay to 
	 * @param pageId
	 * @return true if the argument overlay is displaying
	 */
	public boolean setDisplayOverlay(DisplayOverlay overlay, int pageId) {
		
		//	remove any existing overlay (unless current one is re-added, e.g. to move to another page)
		if ((this.displayOverlay != null) && (this.displayOverlay != overlay)) {
			this.displayOverlay.setParentPage(null);
			this.displayOverlay = null;
			this.displayOverlayPage = -1;
		}
		
		//	remember new overlay
		this.displayOverlay = overlay;
		
		//	anything to show?
		if (this.displayOverlay == null)
			return false;
		
		//	we can only add the overlay to an existing page
		else if ((pageId < 0) || (pageId >= this.pageVisible.length)) {
			this.displayOverlayPage = -1;
			this.displayOverlay.setParentPage(null);
			return false;
		}
		
		//	we can only show the overlay in a visible page
		else if ((pageId < 0) || (pageId >= this.pageVisible.length) || !this.isPageVisible(pageId)) {
			this.displayOverlayPage = pageId;
			this.displayOverlay.setParentPage(null);
			return false;
		}
		
		//	attach overlay to new page
		else {
			this.displayOverlayPage = pageId;
			this.displayOverlay.setParentPage(this.pagePanels[pageId]);
			return true;
		}
	}
	
	/**
	 * Externally set the word selected in the document markup panel. If the
	 * argument words is null, this method simply returns false without doing
	 * anything. If the page the word lies upon is not visible, this method 
	 * also returns false and has no effect. Sub classes overwriting the
	 * two-argument version of this method need not overwrite this method
	 * separately, as it loops through to the latter. If another box or word
	 * selection is already set, it is cleared.<BR/>
	 * Sub classes overwriting this implementation, e.g. to move the selection
	 * into the visible part of a scroll pane, should first check the result of
	 * the super call to check if selection was successful. If this method is
	 * to be called from a selection action returned from
	 * <code>getActions()</code>, the call is best made via
	 * <code>SwingUtilities.invokeLater()</code> to make sure it behaves as
	 * planned and does not interfere with an existing word selection.
	 * @param word the word to select
	 * @return true if the selection was set successfully
	 */
	public boolean setWordSelection(ImWord word) {
		return this.setWordSelection(word, word);
	}
	
	/**
	 * Externally set the range of words selected in the document markup panel.
	 * The two argument words have to belong to the same logical text stream
	 * for this method to do anything. Otherwise, this method simply returns
	 * false. If either of the two argument words is null, this method also
	 * simply returns false without doing anything. Further, if a page either
	 * of the argument words lies on is not visible, this method returns false
	 * without taking any action. If another box or word selection is already
	 * set, it is cleared.<BR>
	 * Sub classes overwriting this implementation, e.g. to move the selection
	 * into the visible part of a scroll pane, should first check the result of
	 * the super call to check if selection was successful. If this method is
	 * to be called from a selection action returned from
	 * <code>getActions()</code>, the call is best made via
	 * <code>SwingUtilities.invokeLater()</code> to make sure it behaves as
	 * planned and does not interfere with an existing word selection.
	 * @param startWord the first word of the selection to make
	 * @param endWord the last word of the selection to make
	 * @return true if the selection was set successfully
	 */
	public boolean setWordSelection(ImWord startWord, ImWord endWord) {
		
		//	check arguments
		if ((startWord == null) || (endWord == null))
			return false;
//		
//		//	check page visibility
//		if (!this.pageVisible[startWord.pageId] || !this.pageVisible[endWord.pageId])
//			return false;
		
		//	check text stream ID
		if (!startWord.getTextStreamId().equals(endWord.getTextStreamId()))
			return false;
		
		//	ensure pages visible
		if (!this.pageVisible[startWord.pageId] || !this.pageVisible[endWord.pageId])
			this.setPagesVisible(Math.min(startWord.pageId, endWord.pageId),  Math.max(startWord.pageId, endWord.pageId), true);
		
		//	clear existing selection
		this.cleanupSelection();
		
		//	swap words if necessary
		if (0 < ImUtils.textStreamOrder.compare(startWord, endWord)) {
			ImWord imw = startWord;
			startWord = endWord;
			endWord = imw;
		}
		
		//	make selection
		this.selectionStartWord = startWord;
		this.selectionEndWord = endWord;
		this.selectionClicked = (startWord == endWord);
		
		//	make selection visible
		this.repaint();
		
		//	indicate success
		return true;
	}
	
	/**
	 * Externally set the page and box selected in the document markup panel.
	 * If the page with the argument ID is not visible, this method returns
	 * false without taking any action. Further, if the argument bounding box
	 * exceeds the dimensions of the page with the argument ID, this method
	 * returns false as well. If another box or word selection is already set,
	 * it is cleared.<BR>
	 * Sub classes overwriting this implementation, e.g. to move the selection
	 * into the visible part of a scroll pane, should first check the result of
	 * the super call to check if selection was successful. If this method is
	 * to be called from a selection action returned from
	 * <code>getActions()</code>, the call is best made via
	 * <code>SwingUtilities.invokeLater()</code> to make sure it behaves as
	 * planned and does not interfere with an existing word selection.
	 * @param pageId the ID of the page to make the selection on
	 * @param box the box to select
	 * @return true if the selection was set successfully
	 * @throws IllegalStateException if another selection is already set
	 */
	public boolean setBoxSelection(int pageId, BoundingBox box) {
		
		//	check arguments
		if (box == null)
			return false;
		
		//	get page
		ImPage page = this.document.getPage(pageId);
		if (page == null)
			return false;
		
		//	check bounds
		if (!page.bounds.includes(box, false))
			return false;
//		
//		//	check page visibility
//		if (!this.pageVisible[pageId])
//			return false;
		
		//	ensure pages visible
		if (this.pageVisible[pageId])
			this.setPageVisible(pageId, true);
		
		//	clear existing selection
		this.cleanupSelection();
		
		//	make selection (managed in original page coordinates)
		this.selectionStartPoint = new Point(box.left, box.top);
		this.selectionEndPoint = new Point(box.right, box.bottom);
		this.selectionClicked = ((box.getWidth() < 3) && (box.getHeight() < 3));
		
		//	make selection visible
		this.repaint();
		
		//	indicate success
		return true;
	}
	
	/**
	 * Clear any existing word or box selection.
	 */
	public void clearSelection() {
		
		//	clear existing selection
		this.cleanupSelection();
		
		//	make selection visible
		this.repaint();
	}
	
	/**
	 * Retrieve the absolute position and dimension of a layout object within
	 * the document markup panel scaled to the current display resolution. This
	 * method is helpful for locating objects in a UI. If the page the argument
	 * layout object lies upon is not visible, this method returns null.
	 * @param imo the layout object to locate
	 * @return the position of the layout object
	 */
	public Rectangle getPosition(ImLayoutObject imo) {
		return this.getPosition(imo.bounds, imo.pageId);
	}
	
	/**
	 * Retrieve the absolute position and dimension of a bounding box in
	 * un-scaled document resolution within the document markup panel scaled to
	 * the current display resolution. This method is helpful for locating
	 * objects in a UI. If the page with the argument ID is not visible, this
	 * method returns null.
	 * @param bb the bounding box to locate
	 * @param pageId the ID of the page the bounding box lies on
	 * @return the position of the layout object
	 */
	public Rectangle getPosition(BoundingBox bb, int pageId) {
		if ((pageId < 0) || (pageId >= this.pagePanels.length))
			return null;
		if ((this.pagePanels[pageId] == null) || !this.pageVisible[pageId])
			return null;
		if (this.pagePanels[pageId].pageImageDpi == -1)
			return null;
		Point ipmpLocation = this.pagePanels[pageId].getLocation();
		float zoom = (((float) this.renderingDpi) / this.pagePanels[pageId].pageImageDpi);
		int px = (Math.round(zoom * (bb.left + this.pagePanels[pageId].pageMarginLeft)) + ipmpLocation.x + this.fixPageMargin);
		int py = (Math.round(zoom * (bb.top + this.pagePanels[pageId].pageMarginTop)) + ipmpLocation.y + this.fixPageMargin);
		int pWidth = Math.round(zoom * (bb.right - bb.left));
		int pHeight = Math.round(zoom * (bb.bottom - bb.top));
		return new Rectangle(px, py, pWidth, pHeight);
	}
	
	/**
	 * A point describing an coordinate on the document markup panel, and on a
	 * page therein. The coordinates of the point are relative to the page in
	 * its original resolution.
	 * 
	 * @author sautter
	 */
	public class PagePoint extends Point {
		public final ImPage page;
		PagePoint(ImPage page, int x, int y) {
			super(x, y);
			this.page = page;
		}
	}
	
	/**
	 * Resolve a pair of coordinates relative to the image markup panel in its
	 * current resolution to un-scaled coordinates on a document page. If the
	 * point identified by the argument coordinates does not lie inside any
	 * page, this method returns null. The main purpose of this method is to
	 * simplify event handling.
	 * @param x the x coordinate relative to the document markup panel
	 * @param y the y coordinate relative to the document markup panel
	 * @return the page point corresponding to the argument coordinates
	 */
	public PagePoint pagePointAt(int x, int y) {
		Component pvp = this.getComponentAt(x, y);
		if ((pvp == null) || !(pvp instanceof ImPageMarkupPanel))
			return null;
		ImPageMarkupPanel ipmp = ((ImPageMarkupPanel) pvp);
		if (ipmp.pageImageDpi == -1)
			return null;
		Point ipmpLocation = ipmp.getLocation();
		float zoom = (((float) ipmp.pageImageDpi) / this.renderingDpi);
		int px = (Math.round(zoom * (x - this.fixPageMargin - ipmpLocation.x)) - ipmp.pageMarginLeft);
		int py = (Math.round(zoom * (y - this.fixPageMargin - ipmpLocation.y)) - ipmp.pageMarginTop);
		return new PagePoint(ipmp.page, px, py);
	}
	
	/**
	 * Check if some page is visible.
	 * @param pageId the index of the page to check
	 * @return true if the page is visible, false otherwise
	 */
	public boolean isPageVisible(int pageId) {
		return this.pageVisible[pageId];
	}
	
	/**
	 * Show or hide a page.
	 * @param pageId the ID of the page to show or hide
	 * @param pv show or hide the page?
	 */
	public void setPageVisible(int pageId, boolean pv) {
		this.setPagesVisible(pageId, pageId, pv);
	}
	
	/**
	 * Show or hide a range of pages.
	 * @param fromPageId the ID of the first page to show or hide
	 * @param toPageId the ID of the last page to show or hide
	 * @param pv show or hide the pages?
	 */
	public void setPagesVisible(int fromPageId, int toPageId, boolean pv) {
//		System.out.println("Setting pages " + fromPageId + "-" + toPageId + " " + (pv ? "visible" : "hidden"));
		boolean pageVisibilityChanged = false;
		for (int p = fromPageId; p <= toPageId; p++) {
			if (this.pages[p] == null)
				continue;
			if (this.pageVisible[p] == pv) {
//				System.out.println(" - page " + p + " already " + (pv ? "visible" : "hidden"));
				continue;
			}
			pageVisibilityChanged = true;
//			System.out.println(" - setting page " + p + " " + (pv ? "visible" : "hidden"));
			this.pageVisible[p] = pv;
//			if (this.pageVisible[p] && (this.pagePanels[p] == null)) {
//				this.pagePanels[p] = new ImPageMarkupPanel(this.pages[p], this.maxPageWidth, this.maxPageHeight);
////				System.out.println(" --> page panel created");
//			}
			if (this.pageVisible[p]) {
				if (this.pagePanels[p] == null)
					this.pagePanels[p] = new ImPageMarkupPanel(this.pages[p], this.maxPageWidth, this.maxPageHeight);
				if ((this.displayOverlay != null) && (this.displayOverlayPage == p))
					this.displayOverlay.setParentPage(this.pagePanels[p]);
//				System.out.println(" --> page panel created");
			}
			else if ((this.displayOverlay != null) && (this.displayOverlayPage == p))
				this.displayOverlay.setParentPage(null);
		}
		if (pageVisibilityChanged) {
//			System.out.println(" ==> laying out pages");
			this.layoutPages();
		}
//		else System.out.println(" ==> no need for laying out pages");
	}
	
	/**
	 * Set the visible pages. This method sets all pages whose index is
	 * contained in the argument array to visible, and hides all others.
	 * @param visiblePageIDs an array holding the IDs of the pages to set visible
	 */
	public void setVisiblePages(int[] visiblePageIDs) {
//		System.out.println("Setting visible pages to " + Arrays.toString(visiblePageIDs));
		HashSet visiblePageIdSet = new HashSet();
		for (int i = 0; i < visiblePageIDs.length; i++)
			visiblePageIdSet.add(new Integer(visiblePageIDs[i]));
		boolean pageVisibilityChanged = false;
		for (int p = 0; p < this.pagePanels.length; p++) {
			if (this.pages[p] == null)
				continue;
			boolean pv = visiblePageIdSet.contains(new Integer(p));
			if (this.pageVisible[p] == pv) {
//				System.out.println(" - page " + p + " already " + (pv ? "visible" : "hidden"));
				continue;
			}
			pageVisibilityChanged = true;
//			System.out.println(" - setting page " + p + " " + (pv ? "visible" : "hidden"));
			this.pageVisible[p] = pv;
//			if (this.pageVisible[p] && (this.pagePanels[p] == null)) {
//				this.pagePanels[p] = new ImPageMarkupPanel(this.pages[p], this.maxPageWidth, this.maxPageHeight);
////				System.out.println(" --> page panel created");
//			}
			if (this.pageVisible[p]) {
				if (this.pagePanels[p] == null)
					this.pagePanels[p] = new ImPageMarkupPanel(this.pages[p], this.maxPageWidth, this.maxPageHeight);
				if ((this.displayOverlay != null) && (this.displayOverlayPage == p))
					this.displayOverlay.setParentPage(this.pagePanels[p]);
			}
			else if ((this.displayOverlay != null) && (this.displayOverlayPage == p))
				this.displayOverlay.setParentPage(null);
		}
		if (pageVisibilityChanged) {
//			System.out.println(" ==> laying out pages");
			this.layoutPages();
		}
//		else System.out.println(" ==> no need for laying out pages");
	}
	
	/**
	 * Retrieve the pages currently in this image markup panel.
	 * @return an array holding the pages
	 */
	public ImPage[] getVisiblePages() {
		ArrayList pages = new ArrayList();
		for (int p = 0; p < this.pages.length; p++) {
			if (this.pages[p] == null)
				continue;
			if (this.pageVisible[p])
				pages.add(this.pages[p]);
		}
		return ((ImPage[]) pages.toArray(new ImPage[pages.size()]));
	}
	
	/**
	 * Retrieve the factor by which page thumbnails are reduced in size in
	 * comparison to normal pages.
	 * @return the reduction factor for page thumbnails
	 */
	public int getPageThumbnailReductionFactor() {
		return this.pageThumbnailReductionFactor;
	}
	
	/**
	 * Set the factor by which page thumbnails are reduced in size in comparison
	 * to normal pages. The reduction factor is actually an integer dimensions
	 * are divided by, and thus a higher reduction factor makes the thumbnails
	 * smaller.
	 * @param ptrf the reduction factor for page thumbnails
	 */
	public void setPageThumbnailReductionFactor(int ptrf) {
		if (this.pageThumbnailReductionFactor == ptrf)
			return;
		this.pageThumbnailReductionFactor = ptrf;
		if (this.hiddenPageBanners.size() != 0)
			this.layoutPages();
	}
	
	/**
	 * Retrieve a thumbnail for a page. The thumbnail contains a grayscale copy
	 * of the page image, scaled down by the thumbnail reduction factor, overlaid
	 * with the page ID and, if present, the page number. The thumbnails adjust
	 * their preferred size automatically when the rendering DPI change.
	 * @param pageId the ID of the page to retrieve the thumbnail for
	 * @return a thumbnail of the page with the argument ID
	 */
	public PageThumbnail getPageThumbnail(int pageId) {
		if (this.pageThumbnails[pageId] == null)
			this.pageThumbnails[pageId] = new PageThumbnail(this.document.getPage(pageId));
		else this.pageThumbnails[pageId].validate();
		return this.pageThumbnails[pageId];
	}
	private PageThumbnail getPageThumbnail(ImPage page) {
		if (this.pageThumbnails[page.pageId] == null)
			this.pageThumbnails[page.pageId] = new PageThumbnail(page);
		else this.pageThumbnails[page.pageId].validate();
		return this.pageThumbnails[page.pageId];
	}
	
	/**
	 * A thumbnail representation of a page, containing a grayscale copy of the
	 * page image, scaled down by the thumbnail reduction factor, overlaid with
	 * the page ID and, if present, the page number. Thumbnails automatically
	 * adjust their preferred size when the rendering DPI or the reduction
	 * factor change.
	 * 
	 * @author sautter
	 */
	public class PageThumbnail {
		private ImPage page;
		private int pageImageDpi;
		private String tooltipText = null;
		PageThumbnail(ImPage page) {
			this.page = page;
			this.validate();
		}
		
		private BufferedImage getPageImageThumbnail(ImageObserver io) {
			if ((this.pageImageThumbnail == null) || (this.pageImageThumbnailReductionFactor != pageThumbnailReductionFactor)) {
				
				//	get page image and store its resolution for zooming
				PageImage pageImage = this.page.getImage();
				this.pageImageDpi = pageImage.currentDpi;
				
				//	render page image thumbnail, reduced by factor, but not zoomed to rendering resolution
				this.pageImageThumbnail = new BufferedImage(((maxPageWidth * pageImage.currentDpi) / (pageImage.originalDpi * pageThumbnailReductionFactor)), ((maxPageHeight * pageImage.currentDpi) / (pageImage.originalDpi * pageThumbnailReductionFactor)), BufferedImage.TYPE_BYTE_GRAY);
				Graphics2D pitGraphics = this.pageImageThumbnail.createGraphics();
				pitGraphics.setColor(Color.WHITE);
				pitGraphics.fillRect(0, 0, this.pageImageThumbnail.getWidth(), this.pageImageThumbnail.getHeight());
				pitGraphics.drawImage(pageImage.image,
						((((maxPageWidth - (page.bounds.right - page.bounds.left)) / 2) * pageImage.currentDpi) / (pageImage.originalDpi * pageThumbnailReductionFactor)),
						((((maxPageHeight - (page.bounds.bottom - page.bounds.top)) / 2) * pageImage.currentDpi) / (pageImage.originalDpi * pageThumbnailReductionFactor)),
						(((page.bounds.right - page.bounds.left) * pageImage.currentDpi) / (pageImage.originalDpi * pageThumbnailReductionFactor)),
						(((page.bounds.bottom - page.bounds.top) * pageImage.currentDpi) / (pageImage.originalDpi * pageThumbnailReductionFactor)),
						io);
				
				//	paint gray page ID over page (adjust to at most one third of image height, and at most 80% of image width)
				pitGraphics.setColor(Color.GRAY);
				String pidString = ("" + this.page.pageId);
				int fontSize = ((pageThumbnailFontSize == -1) ? (pageImage.currentDpi / 4) : pageThumbnailFontSize);
				Font pidFont = new Font("Sans", Font.BOLD, fontSize);
				TextLayout pidTl = new TextLayout(pidString, pidFont, pitGraphics.getFontRenderContext());
				while (pidTl.getBounds().getHeight() < (this.pageImageThumbnail.getHeight() / 3)) {
					fontSize++;
					pidFont = new Font("Sans", Font.BOLD, fontSize);
					pidTl = new TextLayout(pidString, pidFont, pitGraphics.getFontRenderContext());
				}
				while (((this.pageImageThumbnail.getHeight() / 3) < pidTl.getBounds().getHeight()) || ((this.pageImageThumbnail.getWidth() * 4) < (pidTl.getBounds().getWidth() * 5))) {
					fontSize--;
					pidFont = new Font("Sans", Font.BOLD, fontSize);
					pidTl = new TextLayout(pidString, pidFont, pitGraphics.getFontRenderContext());
				}
				pageThumbnailFontSize = fontSize;
				pitGraphics.setFont(pidFont);
				pidTl = new TextLayout(pidString, pidFont, pitGraphics.getFontRenderContext());
				pitGraphics.drawString(pidString, ((int) Math.round((this.pageImageThumbnail.getWidth() - pidTl.getBounds().getWidth()) / 2)), ((int) Math.round(((this.pageImageThumbnail.getHeight() + pidTl.getBounds().getHeight()) / 2)/* - Math.round(wlm.getDescent())*/)));
				
				//	remember rendering factor
				this.pageImageThumbnailReductionFactor = pageThumbnailReductionFactor;
			}
			return this.pageImageThumbnail;
		}
		private BufferedImage pageImageThumbnail;
		private int pageImageThumbnailReductionFactor = -1;
		
		/**
		 * Retrieve the tooltip text for the page thumbnail.
		 * @return the tooltip text
		 */
		public String getTooltipText() {
			return this.tooltipText;
		}
		
		/**
		 * Retrieve the preferred size of the page tumbnail.
		 * @return the preferred size
		 */
		public Dimension getPreferredSize() {
			if ((this.preferredSize == null) || (this.preferredSizeDpi != renderingDpi) || (this.preferredSizeReductionFactor != pageThumbnailReductionFactor)) {
				this.preferredSize = new Dimension(Math.round(((float) (maxPageWidth * renderingDpi)) / (this.pageImageDpi * pageThumbnailReductionFactor)), Math.round(((float) (maxPageHeight * renderingDpi)) / (this.pageImageDpi * pageThumbnailReductionFactor)));
				this.preferredSizeDpi = renderingDpi;
				this.preferredSizeReductionFactor = pageThumbnailReductionFactor;
			}
			return this.preferredSize;
		}
		private Dimension preferredSize = null;
		private int preferredSizeDpi = 0;
		private int preferredSizeReductionFactor = 0;
		
		void validate() {
			this.tooltipText = ("Page " + this.page.pageId + (this.page.hasAttribute(PAGE_NUMBER_ATTRIBUTE) ? (" (page number " + ((String) this.page.getAttribute(PAGE_NUMBER_ATTRIBUTE)) + ")") : ""));
			this.getPageImageThumbnail(null);
			this.getPreferredSize();
		}
		
		/**
		 * Render the page thumbnail through some graphics object, in the
		 * preferred size. This method is intended for components wanting to
		 * display the thumbnail to call in their <code>paint()</code> method.
		 * @param graphics the graphics object to render to
		 * @param x the x coordinate
		 * @param y the y coordinate
		 * @param io object to be notified as more of the image is converted
		 */
		public void paint(Graphics graphics, int x, int y, ImageObserver io) {
			graphics.drawImage(this.getPageImageThumbnail(io), x, y, this.getPreferredSize().width, this.getPreferredSize().height, io);
		}
		
		/**
		 * Render the page thumbnail through some graphics object. This method
		 * is intended for components wanting to display the thumbnail to call
		 * in their <code>paint()</code> method.
		 * @param graphics the graphics object to render to
		 * @param x the x coordinate
		 * @param y the y coordinate
		 * @param width the width of the rectangle
		 * @param height the height of the rectangle
		 * @param io object to be notified as more of the image is converted
		 */
		public void paint(Graphics graphics, int x, int y, int width, int height, ImageObserver io) {
			graphics.drawImage(this.getPageImageThumbnail(io), x, y, width, height, io);
		}
	}
	private int pageThumbnailFontSize = -1;
	
	/**
	 * Retrieve the number of pages displayed side by side before breaking into
	 * a new row.
	 * @return the number of pages per row
	 */
	public int getSideBySidePages() {
		return this.sideBySidePages;
	}
	
	/**
	 * Set the number of pages displayed side by side before breaking into a
	 * new row. If the argument number is less than 1, all pages are lain out
	 * in one single row left to right.
	 * @param sbsp the number of pages per row
	 */
	public void setSideBySidePages(int sbsp) {
		if (this.sideBySidePages == sbsp)
			return;
		this.sideBySidePages = sbsp;
		this.layoutPages();
		if (this.idvc != null)
			this.idvc.updateControls();
	}
	
	/**
	 * Retrieve the color for highlighting word selections.
	 * @return the color of word selections
	 */
	public Color getSelectionHighlightColor() {
		return this.selectionHighlightColor;
	}

	/**
	 * Set the color for highlighting word selections.
	 * @param shc the color of word selections
	 */
	public void setSelectionHighlightColor(Color shc) {
		if (shc.getRGB() == this.selectionHighlightColor.getRGB())
			return;
		Color oldShc = this.selectionHighlightColor;
		this.selectionHighlightColor = shc;
		this.notifyDisplayPropertyChanged("wordSelection.color", oldShc, shc);
		this.validate();
		this.repaint();
	}

	/**
	 * Retrieve the color of rectangles visualizing box selections.
	 * @return the color of the selection box
	 */
	public Color getSelectionBoxColor() {
		return this.selectionBoxColor;
	}

	/**
	 * Set the color of the rectangles visualizing box selections.
	 * @param sbc the color for the selection box
	 */
	public void setSelectionBoxColor(Color sbc) {
		if (sbc.getRGB() == this.selectionBoxColor.getRGB())
			return;
		Color oldSbc = this.selectionBoxColor;
		this.selectionBoxColor = sbc;
		this.notifyDisplayPropertyChanged("boxSelection.color", oldSbc, sbc);
		this.validate();
		this.repaint();
	}

	/**
	 * Retrieve the line thickness (in pixels) used for rectangles visualizing
	 * box selections.
	 * @return the line thickness of the selection box in pixels
	 */
	public int getSelectionBoxThickness() {
		return this.selectionBoxThickness;
	}
	
	/**
	 * Set the line thickness (in pixels) to use for the rectangles visualizing
	 * box selections.
	 * @param sbt the thickness of the selection box in pixels
	 */
	public void setSelectionBoxThickness(int sbt) {
		if (sbt == this.selectionBoxThickness)
			return;
		int oldSbt = this.selectionBoxThickness;
		this.selectionBoxThickness = sbt;
		this.selectionBoxStroke = new BasicStroke(this.selectionBoxThickness, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER);;
		this.notifyDisplayPropertyChanged("boxSelection.thickness", new Integer(oldSbt), new Integer(sbt));
		this.validate();
		this.repaint();
	}
	
	/**
	 * Retrieve the stroke used for rectangles visualizing box selections.
	 * @return the current selection box stroke
	 */
	public BasicStroke getSelectionBoxStroke() {
		return this.selectionBoxStroke;
	}
	
	/**
	 * Set the stroke to use for the rectangles visualizing box selections.
	 * Specifying a null argument restores the default stroke.
	 * @param sbs the stroke to use for rendering the selection box
	 */
	public void setSelectionBoxStroke(BasicStroke sbs) {
		this.selectionBoxStroke = ((sbs == null) ? defaultSelectionBoxStroke : sbs);
		this.selectionBoxThickness = Math.round(this.selectionBoxStroke.getLineWidth());
	}
	
	/**
	 * Retrieve the maximum unscaled width (in pixels) of a page displayed in
	 * this document markup panel
	 * @return the maximum page width
	 */
	public int getMaxPageWidth() {
		return this.maxPageWidth;
	}
	
	/**
	 * Retrieve the maximum unscaled height (in pixels) of a page displayed in
	 * this document markup panel
	 * @return the maximum page height
	 */
	public int getMaxPageHeight() {
		return this.maxPageHeight;
	}
	
	/**
	 * Retrieve the maximum original resolution (in DPI) of a page displayed in
	 * this document markup panel
	 * @return the maximum resolution
	 */
	public int getMaxPageImageDpi() {
		return this.maxPageImageDpi;
	}
	
	/**
	 * Retrieve the current rendering DPI.
	 * @return the current rendering DPI
	 */
	public int getRenderingDpi() {
		return this.renderingDpi;
	}
	
	/**
	 * Set the rendering DPI. This method also affects the zoom percentage;
	 * namely, this method sets the zoom percentage to <code>renderingDpi
	 * * 100 / 96</code>.
	 * @param renderingDpi the new rendering DPI
	 */
	public void setRenderingDpi(int renderingDpi) {
		if (this.renderingDpi == renderingDpi)
			return;
		this.renderingDpi = renderingDpi;
		this.renderingDpiModCount++;
		if (this.isVisible()) {
			this.invalidate();
			this.getLayout().layoutContainer(this);
			this.validate();
			this.repaint();
		}
	}
	
	/**
	 * Retrieve the current zoom percentage, rounded to the next full percent.
	 * @return the current zoom percentage
	 */
	public int getZoomPercentage() {
		return Math.round(((float) (this.renderingDpi * 100)) / DEFAULT_RENDERING_DPI);
	}
	
	/**
	 * Set the zoom percentage. This method also affects the rendering DPI;
	 * namely, the rendering DPI are set to <code>zoomPercentage * 96 / 100
	 * </code>.
	 * @param zoomPercentage the zoomPercentage to set
	 */
	public void setZoomPercentage(int zoomPercentage) {
		this.setRenderingDpi(Math.round(((float) (zoomPercentage * DEFAULT_RENDERING_DPI)) / 100));
	}
	
	/**
	 * Retrieve the layout object types currently registered.
	 * @return an array holding the types
	 */
	public String[] getLayoutObjectTypes() {
		return ((String[]) this.layoutObjectColors.keySet().toArray(new String[this.layoutObjectColors.size()]));
	}
	
	/**
	 * Retrieve the color used for painting layout objects of a specific type.
	 * @param type the type of layout object to retrieve the color for
	 * @return the color to use for painting the layout objects of the argument
	 *            type
	 */
	public Color getLayoutObjectColor(String type) {
		return this.getLayoutObjectColor(type, false);
	}
	private Color getLayoutObjectColor(String type, boolean create) {
		Color loc = ((Color) this.layoutObjectColors.get(type));
		if ((loc == null) && create) {
			loc = this.createLayoutObjectColor(type);
			if (loc == null)
				loc = new Color(Color.HSBtoRGB(((float) Math.random()), 0.7f, 1.0f));
			this.layoutObjectColors.put(type, loc);
			this.notifyDisplayPropertyChanged(("region." + type + ".color"), null, loc);
		}
		return loc;
	}
	
	/**
	 * Create a color for visualizing layout objects of a given type that has
	 * not been assigned a color yet. If this method returns null, a color will
	 * be created internally, using HSB with a random hue, 70% saturation, and
	 * full brightness. This default implementation does return null and thus
	 * delegate to the internal approach, subclasses are welcome to overwrite
	 * it and provide their own logic.
	 * @param type the layout object type the color is intended for
	 * @return the color for visualizing the annotations of the argument type
	 */
	protected Color createLayoutObjectColor(String type) {
		return null;
	}
	
	/**
	 * Set the color to use for painting layout objects of a specific type.
	 * @param type the type of layout object to set the color for
	 * @param colors the color to use for painting the layout objects of the
	 *            argument type
	 */
	public void setLayoutObjectColor(String type, Color color) {
		Color oldColor = this.getLayoutObjectColor(type);
		if ((oldColor != null) && (oldColor.getRGB() == color.getRGB()))
			return;
		this.layoutObjectColors.put(type, color);
		this.notifyDisplayPropertyChanged(("region," + type + ".color"), oldColor, color);
		if (this.isVisible() && this.areRegionsPainted(type)) {
			this.highlightModCount++;
			this.validate();
			this.repaint();
		}
		if (this.idvc == null)
			return;
		if (WORD_ANNOTATION_TYPE.equals(type)) {
			this.idvc.wordControl.setColor(color);
			return;
		}
		RegionControl rc = ((RegionControl) this.idvc.regionControls.get(type));
		if (rc != null)
			rc.setColor(color);
	}
	
	/**
	 * Retrieve the text stream types currently registered.
	 * @return an array holding the types
	 */
	public String[] getTextStreamTypes() {
		return ((String[]) this.textStreamTypeColors.keySet().toArray(new String[this.textStreamTypeColors.size()]));
	}
	
	/**
	 * Retrieve the color used for painting text streams of a specific type.
	 * @param type the type of text stream to retrieve the color for
	 * @return the color to use for painting the text streams of the argument
	 *            type
	 */
	public Color getTextStreamTypeColor(String type) {
		return this.getTextStreamTypeColor(type, false);
	}
	private Color getTextStreamTypeColor(String type, boolean create) {
		Color tstc = ((Color) this.textStreamTypeColors.get(type));
		if ((tstc == null) && create) {
			tstc = this.createTextStreamTypeColor(type);
			if (tstc == null)
				tstc = new Color(Color.HSBtoRGB(((float) Math.random()), 0.7f, 1.0f));
			this.textStreamTypeColors.put(type, tstc);
			this.notifyDisplayPropertyChanged(("textStream." + type + ".color"), null, tstc);
		}
		return tstc;
	}
	
	/**
	 * Create a color for visualizing text streams of a given type that has not
	 * been assigned a color yet. If this method returns null, a color will be
	 * created internally, using HSB with a random hue, 70% saturation, and
	 * full brightness. This default implementation does return null and thus
	 * delegate to the internal approach, subclasses are welcome to overwrite
	 * it and provide their own logic.
	 * @param type the text stream type the color is intended for
	 * @return the color for visualizing the annotations of the argument type
	 */
	protected Color createTextStreamTypeColor(String type) {
		return null;
	}
	
	/**
	 * Set the color to use for painting text streams of a specific type. If
	 * the argument text stream type does not exist yet, it is added.
	 * @param type the type of text streams to set the color for
	 * @param colors the color to use for painting the text streams of the
	 *            argument type
	 */
	public void setTextStreamTypeColor(String type, Color color) {
		Color oldColor = this.getTextStreamTypeColor(type);
		if ((oldColor != null) && (oldColor.getRGB() == color.getRGB()))
			return;
		this.textStreamTypeColors.put(type, color);
		this.notifyDisplayPropertyChanged(("rextStream," + type + ".color"), oldColor, color);
		if (this.isVisible()) {
			this.highlightModCount++;
			this.validate();
			this.repaint();
		}
	}
	
	/**
	 * Retrieve the annotation types currently registered.
	 * @return an array holding the types
	 */
	public String[] getAnnotationTypes() {
		return ((String[]) this.annotationColors.keySet().toArray(new String[this.annotationColors.size()]));
	}
	
	/**
	 * Retrieve the alpha used for drawing annotation value highlights. This
	 * alpha value is also used for the central portion of the color selector
	 * buttons representing annotation types in any associated view control
	 * panel.
	 * @return the annotation highlight alpha
	 */
	public int getAnnotationHighlightAlpha() {
		return this.annotationHighlightAlpha;
	}
	
	/**
	 * Set the alpha to use for drawing annotation value highlights. This alpha
	 * value is also used for the central portion of the color selector buttons
	 * representing annotation types in any associated view control panel. The
	 * argument value must be between 0 and 255, inclusive.
	 * @param aha the annotation highlight alpha to set
	 */
	public void setAnnotationHighlightAlpha(int aha) {
		if (aha == this.annotationHighlightAlpha)
			return;
		if (aha < 0x00)
			throw new IllegalArgumentException("Annotation highlight alpha cannot be less than 0");
		if (0xFF < aha)
			throw new IllegalArgumentException("Annotation highlight alpha cannot be more than 255");
		int oldAha = this.annotationHighlightAlpha;
		this.annotationHighlightAlpha = aha;
		this.notifyDisplayPropertyChanged("annot.highlightAlpha", new Integer(oldAha), new Integer(aha));
		for (Iterator atit = this.annotationColors.keySet().iterator(); atit.hasNext();) {
			String type = ((String) atit.next());
			Color color = ((Color) this.annotationColors.get(type));
			this.annotationHighlightColors.put(type, new Color(color.getRed(), color.getGreen(), color.getBlue(), this.annotationHighlightAlpha));
			TypeControl tc = ((this.idvc == null) ? null : ((TypeControl) this.idvc.annotControls.get(type)));
			if (tc != null)
				tc.setColor(color);
		}
		if (this.isVisible())
			this.highlightModCount++;
		this.validate();
		this.repaint();
		if (this.idvc != null) {
			for (Iterator rtit = this.idvc.regionControls.keySet().iterator(); rtit.hasNext();) {
				String type = ((String) rtit.next());
				TypeControl tc = ((TypeControl) this.idvc.regionControls.get(type));
				if (tc != null)
					tc.setColor(tc.color);
			}
			this.idvc.wordControl.setColor(this.getLayoutObjectColor(WORD_ANNOTATION_TYPE, true));
			this.idvc.validate();
			this.idvc.repaint();
		}
	}
	
	/**
	 * Retrieve the color used for painting annotations of a specific type.
	 * @param type the type of annotation to retrieve the color for
	 * @return the color to use for painting the annotations of the argument
	 *            type
	 */
	public Color getAnnotationColor(String type) {
		return this.getAnnotationColor(type, false);
	}
	Color getAnnotationColor(String type, boolean create) {
		Color ac = ((Color) this.annotationColors.get(type));
		if ((ac == null) && create) {
			ac = this.createAnnotationColor(type);
			if (ac == null)
				ac = new Color(Color.HSBtoRGB(((float) Math.random()), 0.7f, 1.0f));
			this.annotationColors.put(type, ac);
			this.annotationHighlightColors.put(type, new Color(ac.getRed(), ac.getGreen(), ac.getBlue(), this.annotationHighlightAlpha));
			this.notifyDisplayPropertyChanged(("annot." + type + ".color"), null, ac);
		}
		return ac;
	}
	Color getAnnotationHighlightColor(String type, boolean create) {
		Color ahc = ((Color) this.annotationHighlightColors.get(type));
		if ((ahc == null) && create)
			this.getAnnotationColor(type, create);
		return ((Color) this.annotationHighlightColors.get(type));
	}
	
	/**
	 * Create a color for visualizing annotations of a given type that has not
	 * been assigned a color yet. If this method returns null, a color will be
	 * created internally, using HSB with a random hue, 70% saturation, and
	 * full brightness. This default implementation does return null and thus
	 * delegate to the internal approach, subclasses are welcome to overwrite
	 * it and provide their own logic.
	 * @param type the annotation type the color is intended for
	 * @return the color for visualizing the annotations of the argument type
	 */
	protected Color createAnnotationColor(String type) {
		return null;
	}
	
	/**
	 * Set the color to use for painting annotations of a specific type.
	 * @param type the type of annotations to set the color for
	 * @param colors the color to use for painting the annotations of the
	 *            argument type
	 */
	public void setAnnotationColor(String type, Color color) {
		Color oldColor = this.getAnnotationColor(type);
		if ((oldColor != null) && (oldColor.getRGB() == color.getRGB()))
			return;
		this.annotationColors.put(type, color);
		this.annotationHighlightColors.put(type, new Color(color.getRed(), color.getGreen(), color.getBlue(), this.annotationHighlightAlpha));
		this.notifyDisplayPropertyChanged(("annot," + type + ".color"), oldColor, color);
		if (this.isVisible() && this.areAnnotationsPainted(type)) {
			this.highlightModCount++;
			this.validate();
			this.repaint();
		}
		if (this.idvc == null)
			return;
		AnnotControl ac = ((AnnotControl) this.idvc.annotControls.get(type));
		if (ac != null)
			ac.setColor(color);
	}
	
	/**
	 * Check whether or not regions of a specific type are painted.
	 * @param type the type of regions to check
	 * @return true if regions of the argument type are painted
	 */
	public boolean areRegionsPainted(String type) {
		return this.paintedLayoutObjectTypes.contains(type);
	}
	
	/**
	 * Activate or deactivate painting regions of a specific type.
	 * @param type the type of region to modify the painting behavior for
	 * @param paint paint the regions or not?
	 */
	public void setRegionsPainted(String type, boolean paint) {
		this.setRegionsPainted(type, paint, true);
	}
	void setRegionsPainted(String type, boolean paint, boolean isApiCall) {
		boolean changed;
		if (paint)
			changed = this.paintedLayoutObjectTypes.add(type);
		else changed = this.paintedLayoutObjectTypes.remove(type);
		if (changed && this.isVisible()) {
			this.highlightModCount++;
			this.validate();
			this.repaint();
		}
		if (isApiCall && (this.idvc != null)) {
			RegionControl rc = ((RegionControl) this.idvc.regionControls.get(type));
			if (rc != null)
				rc.paint.setSelected(paint);
		}
	}
	
	/**
	 * Check whether or not annotations of a specific type are painted.
	 * @param type the type of annotation to check
	 * @return true if annotations of the argument type are painted
	 */
	public boolean areAnnotationsPainted(String type) {
		return this.paintedAnnotationTypes.contains(type);
	}
	
	/**
	 * Activate or deactivate painting annotations of a specific type.
	 * @param type the type of annotation to modify the painting behavior for
	 * @param paint paint the annotations or not?
	 */
	public void setAnnotationsPainted(String type, boolean paint) {
		this.setAnnotationsPainted(type, paint, true);
	}
	void setAnnotationsPainted(String type, boolean paint, boolean isApiCall) {
		boolean changed;
		if (paint)
			changed = this.paintedAnnotationTypes.add(type);
		else changed = this.paintedAnnotationTypes.remove(type);
		if (changed && this.isVisible()) {
			this.highlightModCount++;
			this.validate();
			this.repaint();
		}
		if (isApiCall && (this.idvc != null)) {
			AnnotControl ac = ((AnnotControl) this.idvc.annotControls.get(type));
			if (ac != null)
				ac.paint.setSelected(paint);
		}
	}
	
	/**
	 * Retrieve the current annotation highlight margin.
	 * @return the annotation highlight margin
	 */
	public int getAnnotationHighlightMargin() {
		return this.annotationHighlightMargin;
	}
	
	/**
	 * Set the annotation highlight margin, i.e., the (unscaled) number of
	 * pixels the highlight exceeds the bounding boxes of annotated words.
	 * @param ahm the new annotation highlight margin
	 */
	public void setAnnotationHighlightMargin(int ahm) {
		if ((ahm < 1) || (ahm == this.annotationHighlightMargin))
			return;
		this.annotationHighlightMargin = ahm;
		if (this.isVisible()) {
			this.highlightModCount++;
			this.validate();
			this.repaint();
		}
	}
	
	/**
	 * Check whether or not text streams are painted.
	 * @return true if text streams are painted, false otherwise
	 */
	public boolean areTextStreamsPainted() {
		return this.textStreamsPainted;
	}
	
	/**
	 * Activate or deactivate painting of text streams. If the property is set
	 * to true, words of each logical text stream are painted in different
	 * colors, and words are connected to their successors by lines.
	 * @param textStreamsPainted paint text streams?
	 */
	public void setTextStreamsPainted(boolean textStreamsPainted) {
		this.setTextStreamsPainted(textStreamsPainted, true);
	}
	void setTextStreamsPainted(boolean textStreamsPainted, boolean isApiCall) {
		this.textStreamsPainted = textStreamsPainted;
		if (this.isVisible()) {
			this.highlightModCount++;
			this.validate();
			this.repaint();
		}
		if (isApiCall && (this.idvc != null))
			this.idvc.wordControl.textStreamsPainted.setSelected(textStreamsPainted);
	}
	
	/**
	 * Check whether or not text strings are painted. This method returns true
	 * if the opacity percentage of the text string overlay is at least 20%.
	 * @return true if text strings are painted, false otherwise
	 */
	public boolean areTextStringsPainted() {
		return (this.textStringPercentage >= 20);
	}
	
	/**
	 * Retrieve the opacity percentage of text strings painted over page images.
	 * @return the opacity percentage of text strings
	 */
	public int getTextStringPercentage() {
		return this.textStringPercentage;
	}
	
	/**
	 * Activate or deactivate painting of text strings. If the property is set
	 * to true, the string value of each word is painted into the word bounding
	 * box.
	 * @param textStringsPainted paint text strings?
	 */
	public void setTextStringPercentage(int textStringPercentage) {
		this.setTextStringPercentage(textStringPercentage, true);
	}
	void setTextStringPercentage(int textStringPercentage, boolean isApiCall) {
		textStringPercentage = Math.max(0, Math.min(100, textStringPercentage));
		if (this.textStringPercentage == textStringPercentage)
			return;
		this.textStringPercentage = textStringPercentage;
		
		//	limit background opacity to 80%, so to not completely obfuscate word selection highlighting
		this.ocrTextStringBackground = new Color(Color.WHITE.getRed(), Color.WHITE.getGreen(), Color.WHITE.getBlue(), ((this.textStringPercentage * 255) / 125));
		
		//	compute text string opacity
		int tsfRed;
		int tsfGreen;
		int tsfBlue;
		//	use glaring red (255, 0, 0) below 60%
		if (this.textStringPercentage <= 60) {
			tsfRed = 255;
			tsfGreen = 0;
			tsfBlue = 0;
		}
		//	fade from red (255, 0, 0) to black (0, 0, 0) between 60% and 100%
		else {
			tsfRed = ((255 * (100 - this.textStringPercentage)) / (100 - 60));
			tsfGreen = 0;//((200 * (100 - this.textStringPercentage)) / (100 - 60));
			tsfBlue = 0;
		}
		this.ocrTextStringForeground = new Color(tsfRed, tsfGreen, tsfBlue, ((this.textStringPercentage * 255) / 100));
		
		//	create color model
		this.ocrTextStringColorModel = createTextStringColorModel(this.ocrTextStringBackground, this.ocrTextStringForeground);
		
		//	update display only if visible
		if (this.isVisible()) {
			this.textStringPercentageModCount++;
			this.validate();
			this.repaint();
		}
		
		//	update display control if change didn't come from there
		if (isApiCall && (this.idvc != null))
			this.idvc.wordControl.textStringPercentage.setValue(this.textStringPercentage);
	}
	
	private static IndexColorModel createTextStringColorModel(Color textStringBackground, Color textStringForeground) {
		int[] cmap = {
				textStringBackground.getRGB(),
				textStringForeground.getRGB()
			};
		byte[][] cMapComps = getComponentArrays(cmap);
		return new IndexColorModel(1, cmap.length, cMapComps[0], cMapComps[1], cMapComps[2], cMapComps[3]);
	}
	
	private static IndexColorModel createPageImageColorModel() {
		int[] cmap = new int[256];
		for (int c = 0; c < cmap.length; c++)
			cmap[c] = ((c <= 252) ? (0xFF000000 | (c << 16) | (c << 8) | c) : 0x00FFFFFF);
		byte[][] cMapComps = getComponentArrays(cmap);
		return new IndexColorModel(8, cmap.length, cMapComps[0], cMapComps[1], cMapComps[2], cMapComps[3]);
	}
	
	private static byte[][] getComponentArrays(int[] rgbas) {
		byte[][] comps = new byte[4][rgbas.length];
		for (int c = 0; c < rgbas.length; c++) {
			int rgba = rgbas[c];
			comps[2][c] = ((byte) (rgba & 0xff));
			rgba >>>= 8;
			comps[1][c] = ((byte) (rgba & 0xff));
			rgba >>>= 8;
			comps[0][c] = ((byte) (rgba & 0xff));
			rgba >>>= 8;
			comps[3][c] = ((byte) (rgba & 0xff));
		}
		return comps;
	}
	
	private static Map fontCache = Collections.synchronizedMap(new HashMap(5));
	private static Font getTextStringFont(int style, boolean serif, int size) {
		String fontKey = (style + " " + (serif ? "serif" : "sans") + " " + size);
		Font font = ((Font) fontCache.get(fontKey));
		if (font != null)
			return font;
		font = (serif ? serifFont : sansFont).deriveFont(style, size);
		fontCache.put(fontKey, font);
		return font;
	}
	
	/**
	 * Retrieve the display extensions to show in a specific page. This default
	 * implementation returns an empty array. Sub classes thus have to overwrite
	 * it to actually include any display extensions.
	 * @param page the page to retrieve the display extension graphics for
	 * @return an array holding the display extension graphics
	 */
	protected DisplayExtensionGraphics[] getDisplayExtensionGraphics(ImPage page) {
		return new DisplayExtensionGraphics[0];
	}
	
	/**
	 * Notify the document markup panel that some display extensions have been
	 * modified or changed their status, and thus should be re-rendered.
	 */
	public void setDisplayExtensionsModified() {
		this.displayExtensionModCount++;
		
		//	update display only if visible
		if (this.isVisible()) {
			this.validate();
			this.repaint();
		}
	}
	
	/**
	 * Add a listener to receive notification about changes to document display
	 * properties like annotation colors or the fonts used for rendering the
	 * document. This is not to be confused with the property change listeners
	 * registered via the <code>addPropertyChangeListener()</code> method to
	 * listen for changes to properties of a specific graphics component.
	 * @param pcl the property change listener to add
	 */
	public void addDisplayPropertyChangeListener(PropertyChangeListener pcl) {
		if (pcl == null)
			return;
		if (this.displayPropertyListeners == null)
			this.displayPropertyListeners = new ArrayList();
		if (!this.displayPropertyListeners.contains(pcl))
			this.displayPropertyListeners.add(pcl);
	}
	
	/**
	 * Remove a listener for changes to document display properties like
	 * annotation colors or the fonts used for rendering the document. This is
	 * not to be confused with the property change listeners registered via the
	 * <code>addPropertyChangeListener()</code> method to listen for changes to
	 * properties of a specific graphics component.
	 * @param pcl the property change listener to remove
	 */
	public void removeDisplayPropertyListener(PropertyChangeListener pcl) {
		if (this.displayPropertyListeners == null)
			return;
		this.displayPropertyListeners.remove(pcl);
		if (this.displayPropertyListeners.isEmpty())
			this.displayPropertyListeners = null;
	}
	
	private ArrayList displayPropertyListeners = null;
	
	private void notifyDisplayPropertyChanged(String propName, Object oldValue, Object newValue) {
		if (this.displayPropertyListeners == null)
			return;
		PropertyChangeEvent pce = new PropertyChangeEvent(this, propName, oldValue, newValue);
		for (int l = 0; l < this.displayPropertyListeners.size(); l++) try {
			((PropertyChangeListener) this.displayPropertyListeners.get(l)).propertyChange(pce);
		}
		catch (RuntimeException re) {
			System.out.println("Error notifying about property '" + propName + "' changing from " + oldValue + " to " + newValue + ": " + re.getMessage());
			re.printStackTrace(System.out);
		}
	}
	
	/**
	 * Generically retrieve the default value of a display property by its
	 * name, be it a font or a color. There is no default values for the
	 * display mode or colors of any types of annotations. If the argument
	 * name does not correspond to an actual display property, this method
	 * returns null.
	 * @param name the name of the display property to retrieve
	 * @return the default value of the display propert with the argument name
	 */
	public static Object getDisplayPropertyDefault(String name) {
		if (name == null)
			return null;
		else if ("wordSelection.color".equals(name))
			return defaultSelectionHighlightColor;
		else if ("boxSelection.color".equals(name))
			return defaultSelectionBoxColor;
		else if ("boxSelection.thickness".equals(name))
			return new Integer(defaultSelectionBoxThickness);
		else if ("annot.highlightAlpha".equals(name))
			return new Integer(defaultAnnotationHighlightAlpha);
		else return null;
	}
	
	/**
	 * Check whether or not the markup panel generally supports a generic
	 * display property with a given name, independent of whether or not that
	 * property specifically applies to a given instance.The highlight color
	 * or display mode of any given type of annotation, for instance, can only
	 * ever have any actual effect if the document showing in a given markup
	 * panel actually contains annotations of that type.
	 * @param name the name of the display property to retrieve
	 * @return the current value of the display propert with the argument name
	 */
	public static boolean supportsDisplayProperty(String name) {
		if (name == null)
			return false;
		else if ("wordSelection.color".equals(name))
			return true;
		else if ("boxSelection.color".equals(name))
			return true;
		else if ("boxSelection.thickness".equals(name))
			return true;
		else if ("annot.highlightAlpha".equals(name))
			return true;
		else if (name.startsWith("annot.")) {
			name = name.substring("annot.".length());
			if (name.endsWith(".color"))
				return true;
			//	TODO anything else ??? end cap thickness ???
			else return false;
		}
		else if (name.startsWith("region.")) {
			name = name.substring("region.".length());
			if (name.endsWith(".color"))
				return true;
			//	TODO anything else ??? outline thickness ???
			else return false;
		}
		else if (name.startsWith("textStream.")) {
			name = name.substring("textStream.".length());
			if (name.endsWith(".color"))
				return true;
			//	TODO anything else ??? outline thickness ??? paragraph end thickness ??? connector thickness ???
			else return false;
		}
		else return false;
	}
	
	/**
	 * Generically retrieve a display property by its name, be it a font, or a
	 * color, or the current display mode of some type of annotation. In the
	 * latter case, however, the returned vylue for non-showing annotations
	 * will be null rather than the invisible mode object. If the argument name
	 * does not correspond to an actual display property, this method returns
	 * null.
	 * @param name the name of the display property to retrieve
	 * @return the current value of the display property with the argument name
	 */
	public Object getDisplayProperty(String name) {
		if (name == null)
			return null;
		else if ("wordSelection.color".equals(name))
			return this.selectionHighlightColor;
		else if ("boxSelection.color".equals(name))
			return this.selectionBoxColor;
		else if ("boxSelection.thickness".equals(name))
			return new Integer(this.selectionBoxThickness);
		else if ("annot.highlightAlpha".equals(name))
			return new Integer(this.annotationHighlightAlpha);
		else if (name.startsWith("annot.")) {
			String annotType = name.substring("annot.".length());
			if (annotType.endsWith(".color")) {
				annotType = annotType.substring(0, (annotType.length() - ".color".length()));
				return this.getAnnotationColor(annotType);
			}
			else return null;
		}
		else if (name.startsWith("region.")) {
			String regionType = name.substring("region.".length());
			if (regionType.endsWith(".color")) {
				regionType = regionType.substring(0, (regionType.length() - ".color".length()));
				return this.getLayoutObjectColor(regionType);
			}
			else return null;
		}
		else if (name.startsWith("textStream.")) {
			String textStreamType = name.substring("textStream.".length());
			if (textStreamType.endsWith(".color")) {
				textStreamType = textStreamType.substring(0, (textStreamType.length() - ".color".length()));
				return this.getTextStreamTypeColor(textStreamType);
			}
			else return null;
		}
		else return null;
	}
	
	/**
	 * Generically set a display property by its name. If the argument object
	 * is of the wrong type for the specified display property, this method
	 * throws an illegal argument exception. If the argument name does not
	 * correspond to an actual display property, this method does nothing.
	 * Setting a display property to null resets it to the built-in default, if
	 * any exists, and does nothing otherwise, except setting the display mode
	 * for a given annotation type to null does the same as setting it to the
	 * invisible mode object.
	 * @param name the name of the display property to set
	 * @param value the value to set the display property to
	 */
	public void setDisplayProperty(String name, Object value) {
		if (name == null)
			return;
		else if ("wordSelection.color".equals(name)) {
			if (value == null)
				value = defaultSelectionHighlightColor;
			if (value instanceof Color)
				this.setSelectionHighlightColor((Color) value);
			else throw new IllegalArgumentException("Cannot convert " + value.getClass().getName() + " to color");
		}
		else if ("boxSelection.color".equals(name)) {
			if (value == null)
				value = defaultSelectionBoxColor;
			if (value instanceof Color)
				this.setSelectionBoxColor((Color) value);
			else throw new IllegalArgumentException("Cannot convert " + value.getClass().getName() + " to color");
		}
		else if ("boxSelection.thickness".equals(name)) {
			if (value == null)
				value = Integer.valueOf(defaultSelectionBoxThickness);
			if (value instanceof Number)
				this.setSelectionBoxThickness(((Number) value).intValue());
			else throw new IllegalArgumentException("Cannot convert " + value.getClass().getName() + " to int");
		}
		else if ("annot.highlightAlpha".equals(name)) {
			if (value == null)
				value = Integer.valueOf(defaultAnnotationHighlightAlpha);
			if (value instanceof Number)
				this.setAnnotationHighlightAlpha(((Number) value).intValue());
			else throw new IllegalArgumentException("Cannot convert " + value.getClass().getName() + " to int");
		}
		else if (name.startsWith("annot.")) {
			String annotType = name.substring("annot.".length());
			if (annotType.endsWith(".color")) {
				if (value == null)
					return; // no defaults for annotation colors
				if (value instanceof Color) {
					annotType = annotType.substring(0, (annotType.length() - ".color".length()));
					this.setAnnotationColor(annotType, ((Color) value));
				}
				else throw new IllegalArgumentException("Cannot convert " + value.getClass().getName() + " to color");
			}
		}
		else if (name.startsWith("region.")) {
			String regionType = name.substring("region.".length());
			if (regionType.endsWith(".color")) {
				if (value == null)
					return; // no defaults for region colors
				if (value instanceof Color) {
					regionType = regionType.substring(0, (regionType.length() - ".color".length()));
					this.setLayoutObjectColor(regionType, ((Color) value));
				}
				else throw new IllegalArgumentException("Cannot convert " + value.getClass().getName() + " to color");
			}
		}
		else if (name.startsWith("textStream.")) {
			String textStreamType = name.substring("textStream.".length());
			if (textStreamType.endsWith(".color")) {
				if (value == null)
					return; // no resetting defaults for text stream colors
				if (value instanceof Color) {
					textStreamType = textStreamType.substring(0, (textStreamType.length() - ".color".length()));
					this.setTextStreamTypeColor(textStreamType, ((Color) value));
				}
				else throw new IllegalArgumentException("Cannot convert " + value.getClass().getName() + " to color");
			}
		}
	}
	
	/**
	 * Retrieve the available actions for a word selection. This implementation
	 * returns an empty array. Sub classes thus have to overwrite it to provide
	 * actual functionality.
	 * @param start the word where the selection started
	 * @param end the word the selection ended
	 * @return an array holding the actions
	 */
	protected SelectionAction[] getActions(ImWord start, ImWord end) {
		return new SelectionAction[0];
	}
	
	/**
	 * Retrieve the available actions for a given number of clicks on a word.
	 * This implementation returns an empty array. Sub classes thus have to
	 * overwrite it to provide actual functionality.
	 * @param word the word that was clicked
	 * @param clickCount the number of clicks
	 * @return an array holding the actions
	 */
	protected ClickSelectionAction[] getClickActions(final ImWord word, int clickCount) {
		return new ClickSelectionAction[0];
	}
	
	/**
	 * Retrieve the available actions for a box selection. The argument points
	 * are relative to the argument page, and in its original resolution. This
	 * implementation provides the action for hiding the page the selection
	 * lies on if the selection does not intersect with any words or regions.
	 * Sub classes overwriting this method thus have to include the actions
	 * returned by this implementation.
	 * @param page the document page the selection lies in
	 * @param start the point where the selection started, one corner of the
	 *            box
	 * @param end the point where the selection ended, the opposite corner of
	 *            the box
	 * @return an array holding the actions
	 */
	protected SelectionAction[] getActions(final ImPage page, Point start, Point end) {
		BoundingBox selectedBox = new BoundingBox(Math.min(start.x, end.x), Math.max(start.x, end.x), Math.min(start.y, end.y), Math.max(start.y, end.y));
		
		LinkedList actions = new LinkedList();
		
		ImRegion[] allRegions = page.getRegions();
		LinkedList selectedRegions = new LinkedList();
		for (int r = 0; r < allRegions.length; r++) {
			if (allRegions[r].bounds.right <= selectedBox.left)
				continue;
			if (selectedBox.right <= allRegions[r].bounds.left)
				continue;
			if (allRegions[r].bounds.bottom <= selectedBox.top)
				continue;
			if (selectedBox.bottom <= allRegions[r].bounds.top)
				continue;
			if (areRegionsPainted(allRegions[r].getType()))
				selectedRegions.add(allRegions[r]);
		}
		
		if (selectedRegions.isEmpty()) {
			actions.add(new SelectionAction("hidePage", "Hide Page", "Hide/fold the page.") {
				public boolean performAction(ImDocumentMarkupPanel invoker) {
					setPageVisible(page.pageId, false);
					return false;
				}
			});
		}
		
		return ((SelectionAction[]) actions.toArray(new SelectionAction[actions.size()]));
	}
	
	/**
	 * Retrieve the available actions for a given number of clicks on a point
	 * in a given page. The argument point is relative to the argument page,
	 * and in its original resolution. This implementation returns an empty
	 * array. Sub classes thus have to overwrite it to provide actual
	 * functionality.
	 * @param page the document page the point lies in
	 * @param point the point that was clicked
	 * @param clickCount the number of clicks
	 * @return an array holding the actions
	 */
	protected ClickSelectionAction[] getClickActions(ImPage page, Point point, int clickCount) {
		return new ClickSelectionAction[0];
	}
	
	/**
	 * Assess which selection actions to consider 'advanced' functionality. If
	 * the array returned by this method contains true for a selection action,
	 * that selection action will not be visible in a context menu right away,
	 * but only show after a 'More ...' button is clicked. This helps reducing
	 * the size of the context menu. This default implementation simply returns
	 * an array containing false for each selection action, so all available
	 * actions are visible in the context menu right away. Sub classes willing
	 * to change this behavior can overwrite this method to make more
	 * discriminative decisions.
	 * @param sas an array holding all the selection actions eligible for the
	 *            context menu
	 * @return an array containing true for selection actions to be considered
	 *            advanced, and false for all others
	 */
	protected boolean[] markAdvancedSelectionActions(SelectionAction[] sas) {
		boolean[] isSaAdvanced = new boolean[sas.length];
		Arrays.fill(isSaAdvanced, false);
		return isSaAdvanced;
	}
	
	/**
	 * Receive notification that a selection action has been performed from the
	 * context menu. This default implementation does nothing, sub classes are
	 * welcome to overwrite it as needed.
	 * @param sa the selection action that has been performed
	 */
	protected void selectionActionPerformed(SelectionAction sa) {}
	
	/**
	 * Apply an image markup tool to the document displayed in this editor
	 * panel. If the argument annotation is null, the image markup tool is
	 * applied to the whole image markup document displayed in this editor
	 * panel.
	 * @param imt the image markup tool to apply
	 * @param annot the annotation to apply the image markup tool to
	 */
	public void applyMarkupTool(final ImageMarkupTool imt, final ImAnnotation annot) {
		
		//	get progress monitor
		final ProgressMonitor pm = this.getProgressMonitor(("Running '" + imt.getLabel() + "', Please Wait"), "", false, true);
		final ProgressMonitorWindow pmw = ((pm instanceof ProgressMonitorWindow) ? ((ProgressMonitorWindow) pm) : null);
		
		//	initialize atomic UNDO (unless handled externally)
		final boolean handleAtomicAction = !this.isAtomicActionRunning();
		if (handleAtomicAction)
			this.startAtomicAction(("Apply " + imt.getLabel()), imt, annot, pm);
		
		//	apply document processor, in separate thread
		Thread imtThread = new Thread() {
			public void run() {
				ImDocumentListener idl = null;
				try {
					
					//	wait for splash screen progress monitor to come up (we must not reach the dispose() line before the splash screen even comes up)
					while ((pmw != null) && !pmw.getWindow().isVisible()) try {
						Thread.sleep(10);
					} catch (InterruptedException ie) {}
					
					//	count what is added and removed
					final CountingSet regionCss = new CountingSet();
					final CountingSet annotCss = new CountingSet();
					
					//	listen for annotations being added, but do not update display control for every change
					idl = new ImDocumentListener() {
						public void typeChanged(ImObject object, String oldType) {
							if (object instanceof ImAnnotation) {
								annotCss.remove(oldType);
								annotCss.add(object.getType());
							}
							else if (object instanceof ImRegion) {
								regionCss.remove(oldType);
								regionCss.add(object.getType());
							}
						}
						public void attributeChanged(ImObject object, String attributeName, Object oldValue) {}
						public void supplementChanged(String supplementId, ImSupplement oldValue) {}
						public void fontChanged(String fontName, ImFont oldValue) {}
						public void regionAdded(ImRegion region) {
							regionCss.add(region.getType());
						}
						public void regionRemoved(ImRegion region) {
							regionCss.remove(region.getType());
						}
						public void annotationAdded(ImAnnotation annotation) {
							annotCss.add(annotation.getType());
						}
						public void annotationRemoved(ImAnnotation annotation) {
							annotCss.remove(annotation.getType());
						}
					};
					document.addDocumentListener(idl);
					
					//	apply image markup tool
					imt.process(document, annot, ImDocumentMarkupPanel.this, pm);
					
					//	make sure newly added objects are visible
					for (Iterator rtit = regionCss.iterator(); rtit.hasNext();)
						setRegionsPainted(((String) rtit.next()), true);
					for (Iterator atit = annotCss.iterator(); atit.hasNext();)
						setAnnotationsPainted(((String) atit.next()), true);
				}
				
				//	catch whatever might happen
				catch (Throwable t) {
					t.printStackTrace(System.out);
					DialogFactory.alert(("Error applying " + imt.getLabel() + ":\n" + t.getMessage()), "Error Running DocumentProcessor", JOptionPane.ERROR_MESSAGE, null);
				}
				
				//	clean up
				finally {
					
					//	stop listening
					if (idl != null)
						document.removeDocumentListener(idl);
					
					//	finish atomic UNDO (unless handled externally)
					if (handleAtomicAction)
						finishAtomicAction(pm);
					
					//	dispose splash screen progress monitor
					if (pmw != null)
						pmw.close();
					
					//	make changes show
					/* we need to do repainting on Swing EDT, as otherwise we
					 * might incur a deadlock between this thread and EDT on
					 * synchronized parts of UI or data structures */
					SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							validate();
							repaint();
//							validateControlPanel();
						}
					});
				}
			}
		};
		imtThread.start();
		
		//	open splash screen progress monitor (this waits)
		if (pmw != null)
			pmw.popUp(true);
	}
	
	/**
	 * Start an atomic action, consisting of one or more edits to the Image
	 * Markup document being edited in the panel. This default implementation
	 * loops through to <code>startAtomicAction()</code>; sub classes that
	 * overwrite it either have to make the super call, or call the latter
	 * method directly.
	 * @param label the label of the action
	 */
	public void beginAtomicAction(String label) {
		this.startAtomicAction(label, null, null, null);
	}
	
	/**
	 * Start an atomic action, consisting of one or more edits to the Image
	 * Markup document being edited in the panel.
	 * @param label the label of the action
	 * @param imt the Image Markup Tool performing the action
	 * @param annot the annotation being processed
	 * @param pm the progress monitor observing on the action (if any)
	 */
	public final void startAtomicAction(String label, ImageMarkupTool imt, ImAnnotation annot, ProgressMonitor pm) {
		this.startAtomicAction(System.currentTimeMillis(), label, imt, annot, pm);
	}
	
	/**
	 * Start an atomic action, consisting of one or more edits to the Image
	 * Markup document being edited in the panel.
	 * @param id the unique ID of the action (must be non-zero)
	 * @param label the label of the action
	 * @param imt the Image Markup Tool performing the action
	 * @param annot the annotation being processed
	 * @param pm the progress monitor observing on the action (if any)
	 */
	public final void startAtomicAction(long id, String label, ImageMarkupTool imt, ImAnnotation annot, ProgressMonitor pm) {
		this.atomicActionId = id;
		this.atomicActionImt = imt;
		this.immediatelyUpdateIdvc = false;
		for (Iterator aalit = this.atomicActionListeners.iterator(); aalit.hasNext();)
			((AtomicActionListener) aalit.next()).atomicActionStarted(id, label, imt, annot, pm);
	}
	
	/**
	 * End an atomic action, consisting of one or more edits to the Image
	 * Markup document being edited in the panel. This default implementation
	 * loops through to <code>finishAtomicAction()</code>; sub classes that
	 * overwrite it either have to make the super call, or call the latter
	 * method directly.
	 */
	public void endAtomicAction() {
		this.finishAtomicAction(null);
	}
	
	/**
	 * Finish an atomic action, consisting of one or more edits to the Image
	 * Markup document being edited in the panel.
	 */
	public final void finishAtomicAction(ProgressMonitor pm) {
		long id = this.atomicActionId;
		for (Iterator aalit = this.atomicActionListeners.iterator(); aalit.hasNext();)
			((AtomicActionListener) aalit.next()).atomicActionFinishing(id, pm);
		this.atomicActionId = 0;
		this.atomicActionImt = null;
		this.immediatelyUpdateIdvc = true;
		for (Iterator aalit = this.atomicActionListeners.iterator(); aalit.hasNext();)
			((AtomicActionListener) aalit.next()).atomicActionFinished(id, pm);
		
		/* we need to do repainting on Swing EDT, as otherwise we
		 * might incur a deadlock between this thread and EDT on
		 * synchronized parts of UI or data structures */
		if (SwingUtilities.isEventDispatchThread())
			this.validateControlPanel();
		else SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				validateControlPanel();
			}
		});
	}
	
	/**
	 * Register an atomic action listener to receive notifications of atomic
	 * actions happening on the markup panel.
	 * @param aal the atomic action listener to register
	 */
	public void addAtomicActionListener(AtomicActionListener aal) {
		if (aal != null)
			this.atomicActionListeners.add(aal);
	}
	
	/**
	 * Remove an atomic action listener to refrain from receiving notifications
	 * of atomic actions happening on the markup panel.
	 * @param aal the atomic action listener to un-register
	 */
	public void removeAtomicActionListener(AtomicActionListener aal) {
		this.atomicActionListeners.remove(aal);
	}
	
	/**
	 * Listener observing atomic actions happening on an Image Markup document
	 * displayed in a markup panel.
	 * 
	 * @author sautter
	 */
	public static interface AtomicActionListener {
		
		/**
		 * Receive notification that an atomic action is starting. Both the
		 * Image Markup Tool and the target annotation can be null, and will be
		 * for Selection Actions. In fact, the target annotation only ever is
		 * not null when an Image Markup Tool is run on an annotation.
		 * @param id the unique ID of the started action
		 * @param label the label of the action
		 * @param imt the Image Markup Tool performing the action
		 * @param annot the annotation being processed
		 * @param pm the progress monitor observing on the action (if any)
		 */
		public abstract void atomicActionStarted(long id, String label, ImageMarkupTool imt, ImAnnotation annot, ProgressMonitor pm);
		
		/**
		 * Receive notification that the running atomic action is finishing.
		 * This method is intended for implementors to trigger any follow-on
		 * activities, still within the running atomic action, e.g. updating
		 * derived data stored on the document proper.
		 * @param id the unique ID of the finishing action
		 * @param pm the progress monitor observing on the action (if any)
		 */
		public abstract void atomicActionFinishing(long id, ProgressMonitor pm);
		
		/**
		 * Receive notification that the running atomic action is finished. Any
		 * activities triggered by client code on this notification does not
		 * fall under the running atomic action any more.
		 * @param id the unique ID of the finished action
		 * @param pm the progress monitor observing on the action (if any)
		 */
		public abstract void atomicActionFinished(long id, ProgressMonitor pm);
	}
	
	/**
	 * Test whether or not an atomic action is running on the markup panel.
	 * This method returns true right from a call to either one of
	 * <code>beginAtomicAction()</code> and <code>startAtomicAction()</code>
	 * up to a call to either one of <code>endAtomicAction()</code> and
	 * <code>finishAtomicAction()</code>.
	 * @return true if an atomic action is running, false otherwise
	 */
	public boolean isAtomicActionRunning() {
		return (this.atomicActionId != 0);
	}
	
	/**
	 * Get the ID of the atomic action currently running on the markup panel.
	 * This method returns a non-zero value right from a call to either one of
	 * <code>beginAtomicAction()</code> and <code>startAtomicAction()</code>
	 * up to a call to either one of <code>endAtomicAction()</code> and
	 * <code>finishAtomicAction()</code>.
	 * @return the ID of the atomic action currently running
	 */
	public long getAtomicActionId() {
		return this.atomicActionId;
	}
	
	/**
	 * Test whether or not a running atomic action involves an Image Markup
	 * Tool. This method returns true right from a call to 
	 * <code>beginAtomicAction()</code> with a non-null <code>imt</code>
	 * argument up to a call to either one of <code>endAtomicAction()</code>
	 * and <code>finishAtomicAction()</code>.
	 * @return true if an atomic action is running and involves an Image Markup
	 *            Tool
	 */
	public boolean isImageMarkupToolRunning() {
		return (this.atomicActionImt != null);
	}
	
	/**
	 * Produce a progress monitor for observing some activity on the image
	 * markup document open in this editor panel. Implementations that do not
	 * support pausing/resuming and aborting can return a plain progress
	 * monitor, ignoring the two boolean arguments; ones willing to support
	 * pausing/resuming and aborting have to return a controlling progress
	 * monitor. If the returned object is a dialog, it has to be positioned and
	 * sized as desired, but need not be opened by implementations, as the code
	 * calling this method takes care of opening and closing. This default
	 * implementation returns a basic progress monitor that simply writes all
	 * output to the system console.
	 * @param title the title for the progress monitor
	 * @param text the explanation text for the progress monitor
	 * @param supportPauseResume support pausing the monitored process?
	 * @param supportAbort support aborting the monitored process?
	 * @return a progress monitor
	 */
	public ProgressMonitor getProgressMonitor(String title, String text, boolean supportPauseResume, boolean supportAbort) {
		return ProgressMonitor.dummy;
	}
	
	/* (non-Javadoc)
	 * @see javax.swing.JComponent#paint(java.awt.Graphics)
	 */
	public void paint(Graphics graphics) {
		super.paint(graphics);
		
		//	paint trans page word connections
		if (this.areTextStreamsPainted()) {
			Color preTpwcColor = graphics.getColor();
			for (Iterator wcit = this.transPageWordConnections.iterator(); wcit.hasNext();) {
				TransPageWordConnection tpwc = ((TransPageWordConnection) wcit.next());
				
				//	get connector line sequence (zoomed)
				Point[] cls = tpwc.getConnectorLineSequence();
				
				//	anything to draw?
				if (cls == null)
					continue;
				
				//	paint connector line sequence (zoomed)
				graphics.setColor(getTextStreamTypeColor(tpwc.fromWord.getTextStreamType(), true));
				for (int p = 0; p < (cls.length-1); p++) {
					graphics.drawLine(cls[p].x, cls[p].y, cls[p+1].x, cls[p+1].y);
					if (cls[p].x == cls[p+1].x)
						graphics.drawLine((cls[p].x + 1), cls[p].y, (cls[p+1].x + 1), cls[p+1].y);
					else if (cls[p].y == cls[p+1].y)
						graphics.drawLine(cls[p].x, (cls[p].y + 1), cls[p+1].x, (cls[p+1].y + 1));
				}
			}
			graphics.setColor(preTpwcColor);
		}
	}
	
	private class TransPageWordConnection {
		ImWord fromWord;
		ImWord toWord;
		private String hashString = null;
		private int hashCode = 0;
		private Point[] connectorLineSequence = null;
		TransPageWordConnection(ImWord fromWord, ImWord toWord) {
			this.fromWord = fromWord;
			this.toWord = toWord;
		}
		public int hashCode() {
			if (this.hashCode == 0)
				this.hashCode = this.toString().hashCode();
			return this.hashCode;
		}
		public boolean equals(Object obj) {
			return ((obj instanceof TransPageWordConnection) && this.toString().equals(obj.toString()));
		}
		public String toString() {
			if (this.hashString == null)
				this.hashString = (this.fromWord.bounds.toString() + "-" + this.toWord.bounds.toString());
			return this.hashString;
		}
		Point[] getConnectorLineSequence() {
			if (!isPageVisible(this.fromWord.pageId) || !isPageVisible(this.toWord.pageId))
				return null;
			if (this.connectorLineSequence != null)
				return this.connectorLineSequence;
			
			//	translate bounding boxes to document panel coordinate space
			ImPageMarkupPanel fromIpmp = pagePanels[this.fromWord.pageId];
			Point fromIpmpLocation = fromIpmp.getLocation();
			BoundingBox from = new BoundingBox(
					(((this.fromWord.bounds.left * renderingDpi) / fromIpmp.pageImageDpi) + fromIpmpLocation.x + fromIpmp.getLeftOffset()),
					(((this.fromWord.bounds.right * renderingDpi) / fromIpmp.pageImageDpi) + fromIpmpLocation.x + fromIpmp.getLeftOffset()),
					(((this.fromWord.bounds.top * renderingDpi) / fromIpmp.pageImageDpi) + fromIpmpLocation.y + fromIpmp.getTopOffset()),
					(((this.fromWord.bounds.bottom * renderingDpi) / fromIpmp.pageImageDpi) + fromIpmpLocation.y + fromIpmp.getTopOffset())
				);
			ImPageMarkupPanel toIpmp = pagePanels[this.toWord.pageId];
			Point toIpmpLocation = toIpmp.getLocation();
			BoundingBox to = new BoundingBox(
					(((this.toWord.bounds.left * renderingDpi) / toIpmp.pageImageDpi) + toIpmpLocation.x + toIpmp.getLeftOffset()),
					(((this.toWord.bounds.right * renderingDpi) / toIpmp.pageImageDpi) + toIpmpLocation.x + toIpmp.getLeftOffset()),
					(((this.toWord.bounds.top * renderingDpi) / toIpmp.pageImageDpi) + toIpmpLocation.y + toIpmp.getTopOffset()),
					(((this.toWord.bounds.bottom * renderingDpi) / toIpmp.pageImageDpi) + toIpmpLocation.y + toIpmp.getTopOffset())
				);
			
			//	collect connector angles
			LinkedList cls = new LinkedList();
			
			//	boxes adjacent in reading order, use simple horizontal line
			if ((from.right <= to.left) && (from.top < to.bottom) && (from.bottom > to.top)) {
				cls.add(new Point(from.right, ((from.top + from.bottom + to.top + to.bottom) / 4)));
				cls.add(new Point(to.left, ((from.top + from.bottom + to.top + to.bottom) / 4)));
			}
			
			//	use angled connection
			else {
				
				//	add starting point
				cls.add(new Point(from.right, ((from.top + from.bottom) / 2)));
				
				//	successor below predecessor (next page below)
				if (from.bottom <= to.top) {
					
					//	successor to lower left of predecessor (next page below)
					if (from.right > to.left) {
						int outswing = Math.min(((to.top - from.bottom) / 2), (renderingDpi / 6));
						cls.add(new Point((from.right + outswing), ((from.top + from.bottom) / 2)));
						cls.add(new Point((from.right + outswing), ((from.bottom + to.top) / 2)));
						cls.add(new Point((to.left - outswing), ((from.bottom + to.top) / 2)));
						cls.add(new Point((to.left - outswing), ((to.top + to.bottom) / 2)));
					}
					
					//	successor to lower right of predecessor (next page below)
					else {
						int middle = ((from.right + to.left) / 2);
						cls.add(new Point(middle, ((from.top + from.bottom) / 2)));
						cls.add(new Point(middle, ((to.top + to.bottom) / 2)));
					}
				}
				
				//	successor to right of predecessor, but at different height (next page to the right)
				else if (from.right < to.left) {
					int fromPageRight = (fromIpmpLocation.x + fromIpmp.getWidth());
					int toPageLeft = toIpmpLocation.x;
					cls.add(new Point(((fromPageRight + toPageLeft) / 2), ((from.top + from.bottom) / 2)));
					cls.add(new Point(((fromPageRight + toPageLeft) / 2), ((to.top + to.bottom) / 2)));
				}
				
				//	add end point
				cls.add(new Point(to.left, ((to.top + to.bottom) / 2)));
			}
			
			//	finally ...
			this.connectorLineSequence = ((Point[]) cls.toArray(new Point[cls.size()]));
			return this.connectorLineSequence;
		}
	}
	private HashSet transPageWordConnections = new HashSet();
	
	/* (non-Javadoc)
	 * @see java.awt.Container#validate()
	 */
	public void validate() {
		if (this.transPageWordConnections != null) // need to catch this during construction
			this.transPageWordConnections.clear();
		if (this.pagePanels != null)
			for (int p = 0; p < this.pagePanels.length; p++) {
				if (this.pagePanels[p] != null)
					this.pagePanels[p].validate();
			}
		for (Iterator hpbit = this.hiddenPageBanners.iterator(); hpbit.hasNext();)
			((HiddenPageBanner) hpbit.next()).validate();
		if (this.idvc != null)
			this.idvc.updateControls();
		super.validate();
	}
	
	/**
	 * Set the component to notify when the two-click action changes, usually
	 * the component responsible for displaying the respective message.
	 * @param tcam the two-click action messenger to notify
	 */
	public void setTwoClickActionMessenger(TwoClickActionMessenger tcam) {
		this.twoClickActionMessenger = tcam;
	}
	
	/**
	 * A component to notify when a two-click action changes.
	 * 
	 * @author sautter
	 */
	public static interface TwoClickActionMessenger {
		/**
		 * Receive notification of a change to the pending two-click action
		 * @param tcsa the new pending two-click action
		 */
		public abstract void twoClickActionChanged(TwoClickSelectionAction tcsa);
	}
	
	/* TODO ImDocumentMarkupPanel:
- enable selection action providers to specify likelihood of action to be used:
  - add isLikely() method to selection actions ...
  - ... and show ones that return false from that method only on "More"
  ==> facilitates providing more actions ...
  ==> ... without cluttering context menu
	 */
	
	/**
	 * A tool to apply to an Image Markup document displayed in an instance of
	 * this class, to perform changes as a visitor.
	 * 
	 * @author sautter
	 */
	public static interface ImageMarkupTool {
		
		/**
		 * Get a nice name for the tool, to use in a user interface.
		 * @return the label of the tool
		 */
		public abstract String getLabel();
		
		/**
		 * Get an explanation text for what the tool does, to use in a user
		 * interface.
		 * @return an explanation text for the tool
		 */
		public abstract String getTooltip();
		
		/**
		 * Get a help text explaining in detail what the tool does, to use in a
		 * user interface.
		 * @return a help text for the tool
		 */
		public abstract String getHelpText();
		
		/**
		 * Process an Image Markup document or an annotation on that document. The
		 * argument document is never null, but the argument annotation can be. If
		 * the annotation is null, the whole document is to be processed; otherwise,
		 * the annotation is to be processed, with the document providing context
		 * information. The argument markup panel provides access to the surrounding
		 * user interface, if any.
		 * @param doc the document to process
		 * @param annot the annotation to process
		 * @param idmp the document markup panel displaying the document, if any
		 * @param pm a progress monitor observing processing progress
		 */
		public abstract void process(ImDocument doc, ImAnnotation annot, ImDocumentMarkupPanel idmp, ProgressMonitor pm);
		
		//	TODO add isApplicableTo(ImDocumentMarkupPanel) method to indicate whether or not the IMT can work on a given document
		//	TODO use this in GGI UI to gray out items in Edit and Tools menus if document of wrong type selected (e.g. font editing on scanned document, or OCR adjustment on born-digital one)
	}
	
	/**
	 * Retrieve the tools for editing page images. This default implementation
	 * returns an empty array; sub classes willing to provide page image
	 * editing functionality have to overwrite this method to return an array
	 * of tools.
	 * @return an array of image editing tools
	 */
	protected ImImageEditTool[] getImageEditTools() {
		return new ImImageEditTool[0];
	}
	
	/**
	 * Open a page for editing the image and words. This method takes care of
	 * making the edit an atomic operation.
	 * @param pageId the ID of the page to edit
	 */
	public boolean editPage(int pageId) {
		return this.editPage(pageId, null);
	}
	
	/**
	 * Open a page for editing the image and words. This method takes care of
	 * making the edit an atomic operation.
	 * @param pageId the ID of the page to edit
	 * @param excerptBox a bounding box defining the part of the page image to
	 *            edit
	 */
	public boolean editPage(int pageId, BoundingBox excerptBox) {
		//	TODO track references and see if page IDs used as arguments
		if ((pageId < 0) || (this.pageVisible.length <= pageId) || !this.pageVisible[pageId] || (this.pagePanels[pageId] == null))
			return false;
		return this.editPage(this.pagePanels[pageId], excerptBox);
	}
	
	private boolean editPage(ImPageMarkupPanel ipmp, BoundingBox excerptBox) {
		
		//	TODO observe excerpt bounding box, maybe simply by creating a sub image and using edge parameters
		
		//	TODO update image edit tools that handle words accordingly to observe page image edges
		
		//	get words
		ImWord[] pageWords = ipmp.page.getWords();
		
		//	create editor panel
		ImImageEditorPanel iiep = new ImImageEditorPanel(ipmp.page.getPageImage(), this.getImageEditTools(), ipmp.page, this.getLayoutObjectColor(WORD_ANNOTATION_TYPE, true));
		
		//	create editor dialog
		final JDialog ped = DialogFactory.produceDialog("Edit Page Image & Words", true);
		ped.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		
		//	add buttons
		final boolean[] cancelled = {false};
		JButton ok = new JButton("OK");
		ok.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				ped.dispose();
			}
		});
		JButton cancel = new JButton("Cancel");
		cancel.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				cancelled[0] = true;
				ped.dispose();
			}
		});
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER), true);
		buttons.add(ok);
		buttons.add(cancel);
		
		//	assemble dialog content
		ped.getContentPane().setLayout(new BorderLayout());
		ped.getContentPane().add(iiep, BorderLayout.CENTER);
		ped.getContentPane().add(buttons, BorderLayout.SOUTH);
		
		//	position dialog and open it
		ped.setSize(ped.getOwner().getSize());
		ped.setLocationRelativeTo(ped.getOwner());
		ped.setVisible(true);
		
		//	cancelled or not, let's preserve that word color
		this.setLayoutObjectColor(WORD_ANNOTATION_TYPE, iiep.getWordBoxColor());
		
		//	cancelled
		if (cancelled[0])
			return false;
		
		//	start atomic action (unless done in calling code)
		boolean handleAtomicAction = !this.isAtomicActionRunning();
		if (handleAtomicAction)
			this.beginAtomicAction("Edit Image & Words in Page " + (ipmp.page.getAttribute(PAGE_NUMBER_ATTRIBUTE, ("" + (ipmp.page.pageId + 1)))));
		
		//	write through word edits
		HashMap pageWordsByID = new HashMap();
		for (int w = 0; w < pageWords.length; w++)
			pageWordsByID.put(pageWords[w].getLocalID(), pageWords[w]);
		ImWord[] ePageWords = iiep.getWords();
		for (int w = 0; w < ePageWords.length; w++) {
			ImWord pageWord = ((ImWord) pageWordsByID.remove(ePageWords[w].getLocalID()));
			if (pageWord == null) {
				System.out.println("Adding word '" + ePageWords[w].getString() + "' at " + ePageWords[w].bounds.toString());
				ipmp.page.addWord(ePageWords[w]);
			}
			else {
				System.out.println("Retaining word '" + ePageWords[w].getString() + "' at " + ePageWords[w].bounds.toString());
				AttributeUtils.copyAttributes(ePageWords[w], pageWord, AttributeUtils.ADD_ATTRIBUTE_COPY_MODE);
				ePageWords[w] = null;
			}
		}
		for (Iterator wit = pageWordsByID.keySet().iterator(); wit.hasNext();) {
			ImWord imw = ((ImWord) pageWordsByID.get(wit.next()));
			ImWord pWord = imw.getPreviousWord();
			ImWord nWord = imw.getNextWord();
			if ((pWord != null) && (nWord != null))
				pWord.setNextWord(nWord);
			else {
				imw.setPreviousWord(null);
				imw.setNextWord(null);
			}
			System.out.println("Removing word '" + imw.getString() + "' at " + imw.bounds.toString());
			ipmp.page.removeWord(imw, true);
		}
		
		//	complete text stream integration
		for (int w = 0; w < ePageWords.length; w++) {
			if (ePageWords[w] == null)
				continue;
			ImWord prevWord = ((ImWord) ePageWords[w].removeAttribute(ImWord.PREVIOUS_WORD_ATTRIBUTE + "Temp"));
			if ((prevWord != null) && (prevWord.getPage() != null))
				ePageWords[w].setPreviousWord(prevWord);
			ImWord nextWord = ((ImWord) ePageWords[w].removeAttribute(ImWord.NEXT_WORD_ATTRIBUTE + "Temp"));
			if ((nextWord != null) && (nextWord.getPage() != null))
				ePageWords[w].setNextWord(nextWord);
		}
		
		//	remove regions that are empty now, and shrink others
		ImRegion[] regions = ipmp.page.getRegions();
		for (int r = 0; r < regions.length; r++) {
			
			//	leave image and graphics regions alone
			if (ImRegion.IMAGE_TYPE.equals(regions[r].getType()) || ImRegion.GRAPHICS_TYPE.equals(regions[r].getType()))
				continue;
			
			//	test if region contains image or graphics
			boolean containsImageOrGraphics = false;
			for (int cr = (r+1); cr < regions.length; cr++)
				if (ImRegion.IMAGE_TYPE.equals(regions[r].getType()) || ImRegion.GRAPHICS_TYPE.equals(regions[r].getType())) {
					containsImageOrGraphics = true;
					continue;
				}
			if (containsImageOrGraphics)
				continue;
			
			//	shrink other regions to words
			ImWord[] regionWords = regions[r].getWords();
			if (regionWords.length == 0)
				ipmp.page.removeRegion(regions[r]);
			else {
				int rLeft = regions[r].bounds.right;
				int rRight = regions[r].bounds.left;
				int rTop = regions[r].bounds.bottom;
				int rBottom = regions[r].bounds.top;
				for (int c = 0; c < regionWords.length; c++) {
					rLeft = Math.min(rLeft, regionWords[c].bounds.left);
					rRight = Math.max(rRight, regionWords[c].bounds.right);
					rTop = Math.min(rTop, regionWords[c].bounds.top);
					rBottom = Math.max(rBottom, regionWords[c].bounds.bottom);
				}
				if ((rLeft < rRight) && (rTop < rBottom) && ((regions[r].bounds.left < rLeft) || (rRight < regions[r].bounds.right) || (regions[r].bounds.top < rTop) || (rBottom < regions[r].bounds.bottom))) {
					BoundingBox rBox = new BoundingBox(rLeft, rRight, rTop, rBottom);
					ImRegion region = new ImRegion(ipmp.page, rBox, regions[r].getType());
					region.copyAttributes(regions[r]);
					ipmp.page.removeRegion(regions[r]);
				}
			}
		}
		
		//	replace page image
		if (iiep.isPageImageDirty())
			ipmp.page.setImage(iiep.getPageImage());
		
		//	finish atomic action (if we started it here)
		if (handleAtomicAction)
			this.endAtomicAction();
		
		//	finally ...
		return true;
	}
	
	/**
	 * Edit the attributes of some Image Markup object belonging to the
	 * document being edited in this panel. This method takes care of making
	 * the attribute changes an atomic operation.
	 * @param attributed the object whose attributes to edit
	 * @param type the type of object whose attribute to edit
	 * @param value the textual value of the object whose attribute to edit
	 */
	public void editAttributes(Attributed attributed, String type, String value) {
		Attributed[] context;
		if (this.isAtomicActionRunning())
			context = null; // no lateral navigation if part of some larger atomic action started outside
		else if (attributed instanceof ImWord)
			context = this.document.getPage(((ImWord) attributed).pageId).getWords();
		else if (attributed instanceof ImAnnotation)
			context = this.document.getAnnotations(((ImAnnotation) attributed).getType());
		else if (attributed instanceof ImRegion)
			context = this.document.getPage(((ImRegion) attributed).pageId).getRegions(((ImRegion) attributed).getType());
		else context = null;
		
		while (attributed != null) {
			if (attributed instanceof ImWord) {
				type = ((ImWord) attributed).getType();
				value = (((ImWord) attributed).getString() + " at " + ((ImWord) attributed).bounds.toString() + " on page " + (((ImWord) attributed).pageId + 1));
			}
			else if (attributed instanceof ImAnnotation) {
				type = ((ImAnnotation) attributed).getType();
//				value = getAnnotationValue((ImAnnotation) attributed);
//				value = ImUtils.getString(((ImAnnotation) attributed).getFirstWord(), ((ImAnnotation) attributed).getLastWord(), 40, true);
				value = this.getAttributeEditorAnnotationValue((ImAnnotation) attributed);
			}
			else if (attributed instanceof ImRegion) {
				type = ((ImRegion) attributed).getType();
				value = (((ImRegion) attributed).getType() + " at " + ((ImRegion) attributed).bounds.toString() + " on page " + (((ImRegion) attributed).pageId + 1));
			}
			attributed = this.editAttributes(attributed, context, type, value);
		}
	}
	
	/**
	 * Create the string to represent an annotation in an attribute editing
	 * dialog. This default implementation restricts the string to roughly 40
	 * character at most, taken from the start and the end of the annotation.
	 * Subclasses are welcome to overwrite it with a different behavior.
	 * @param annot the annotation to create the display string for
	 * @return the display string for the argument annotation
	 */
	protected String getAttributeEditorAnnotationValue(ImAnnotation annot) {
		return ImUtils.getString(annot.getFirstWord(), annot.getLastWord(), 40, true);
	}
//	
//	private static String getAnnotationValue(ImAnnotation annot) {
//		
//		//	count out annotation length
//		int annotChars = 0;
//		for (ImWord imw = annot.getFirstWord(); imw != null; imw = imw.getNextWord()) {
//			annotChars += imw.getString().length();
//			if (imw == annot.getLastWord())
//				break;
//		}
//		
//		//	this one's short enough
//		if (annotChars <= 40)
//			return ImUtils.getString(annot.getFirstWord(), annot.getLastWord(), true);
//		
//		//	get end of head
//		ImWord headEnd = annot.getFirstWord();
//		int headChars = 0;
//		for (; headEnd != null; headEnd = headEnd.getNextWord()) {
//			headChars += headEnd.getString().length();
//			if (headChars >= 20)
//				break;
//			if (headEnd == annot.getLastWord())
//				break;
//		}
//		
//		//	get start of tail
//		ImWord tailStart = annot.getLastWord();
//		int tailChars = 0;
//		for (; tailStart != null; tailStart = tailStart.getPreviousWord()) {
//			tailChars += tailStart.getString().length();
//			if (tailChars >= 20)
//				break;
//			if (tailStart == annot.getFirstWord())
//				break;
//			if (tailStart == headEnd)
//				break;
//		}
//		
//		//	met in the middle, use whole string
//		if ((headEnd == tailStart) || (headEnd.getNextWord() == tailStart) || (headEnd.getNextWord() == tailStart.getPreviousWord()))
//			return ImUtils.getString(annot.getFirstWord(), annot.getLastWord(), true);
//		
//		//	give head and tail only if annotation too long
//		else return (ImUtils.getString(annot.getFirstWord(), headEnd, true) + " ... " + ImUtils.getString(tailStart, annot.getLastWord(), true));
//	}
	
	private Attributed editAttributes(Attributed attributed, Attributed[] context, final String type, String value) {
		final AttributeEditor aePanel = new AttributeEditor(attributed, type, value, context);
		final JDialog aeDialog = DialogFactory.produceDialog("Edit Attributes", true);
		final Attributed[] nextToOpen = {null};
		final boolean handleAtomicAction = !this.isAtomicActionRunning();
		
		JButton commit = new JButton("OK");
		commit.setBorder(BorderFactory.createRaisedBevelBorder());
		commit.setPreferredSize(new Dimension(80, 21));
		commit.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				if (handleAtomicAction)
					beginAtomicAction("Edit " + type + " Attributes");
				aePanel.writeChanges();
				attributeEditorDialogSize = aeDialog.getSize();
				attributeEditorDialogLocation = aeDialog.getLocation(attributeEditorDialogLocation);
				aeDialog.dispose();
				if (handleAtomicAction)
					endAtomicAction();
			}
		});
		JButton cancel = new JButton("Cancel");
		cancel.setBorder(BorderFactory.createRaisedBevelBorder());
		cancel.setPreferredSize(new Dimension(80, 21));
		cancel.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				attributeEditorDialogSize = aeDialog.getSize();
				attributeEditorDialogLocation = aeDialog.getLocation(attributeEditorDialogLocation);
				aeDialog.dispose();
			}
		});
		
		JButton previous = null;
		JButton next = null;
		if (context != null) {
			Attributed previousAttributed = null;
			Attributed nextAttributed = null;
			if (attributed instanceof ImWord) {
				previousAttributed = ((ImWord) attributed).getPreviousWord();
				nextAttributed = ((ImWord) attributed).getNextWord();
			}
			else if (attributed instanceof ImAnnotation) {
				for (int c = 0; c < context.length; c++)
					if (context[c] == attributed) {
						previousAttributed = ((c == 0) ? null: context[c-1]);
						nextAttributed = (((c+1) == context.length) ? null: context[c+1]);
						break;
					}
			}
			else if (attributed instanceof ImRegion) {
				Arrays.sort(context, ImUtils.topDownOrder);
				for (int c = 0; c < context.length; c++)
					if (context[c] == attributed) {
						previousAttributed = ((c == 0) ? null: context[c-1]);
						nextAttributed = (((c+1) == context.length) ? null: context[c+1]);
						break;
					}
			}
			
			if (previousAttributed != null) {
				final Attributed fPreviousAttributed = previousAttributed;
				previous = new JButton("Previous");
				previous.setBorder(BorderFactory.createRaisedBevelBorder());
				previous.setPreferredSize(new Dimension(80, 21));
				previous.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent ae) {
						if (handleAtomicAction)
							beginAtomicAction("Edit " + type + " Attributes");
						aePanel.writeChanges();
						attributeEditorDialogSize = aeDialog.getSize();
						attributeEditorDialogLocation = aeDialog.getLocation(attributeEditorDialogLocation);
						aeDialog.dispose();
						if (handleAtomicAction)
							endAtomicAction();
						nextToOpen[0] = fPreviousAttributed;
					}
				});
			}
			
			if (nextAttributed != null) {
				final Attributed fNextAttributed = nextAttributed;
				next = new JButton("Next");
				next.setBorder(BorderFactory.createRaisedBevelBorder());
				next.setPreferredSize(new Dimension(80, 21));
				next.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent ae) {
						if (handleAtomicAction)
							beginAtomicAction("Edit " + type + " Attributes");
						aePanel.writeChanges();
						attributeEditorDialogSize = aeDialog.getSize();
						attributeEditorDialogLocation = aeDialog.getLocation(attributeEditorDialogLocation);
						aeDialog.dispose();
						if (handleAtomicAction)
							endAtomicAction();
						nextToOpen[0] = fNextAttributed;
					}
				});
			}
		}
		
		JPanel aeButtons = new JPanel(new FlowLayout(FlowLayout.CENTER));
		if (previous != null)
			aeButtons.add(previous);
		aeButtons.add(commit);
		aeButtons.add(cancel);
		if (next != null)
			aeButtons.add(next);
		
		aeDialog.getContentPane().setLayout(new BorderLayout());
		aeDialog.getContentPane().add(aePanel, BorderLayout.CENTER);
		aeDialog.getContentPane().add(aeButtons, BorderLayout.SOUTH);
		
		aeDialog.setResizable(true);
		aeDialog.setSize(attributeEditorDialogSize);
		if (attributeEditorDialogLocation == null)
			aeDialog.setLocationRelativeTo(DialogFactory.getTopWindow());
		else aeDialog.setLocation(attributeEditorDialogLocation);
		aeDialog.setVisible(true);
		
		return nextToOpen[0];
	}
	private static Dimension attributeEditorDialogSize = new Dimension(400, 300);
	private static Point attributeEditorDialogLocation = null;
	
	//	FOR TEST PURPOSES ONLY !!!
	public static void main(String[] args) throws Exception {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e) {}
		
		final File baseFolder = new File("E:/Testdaten/PdfExtract/");
		String pdfName;
		pdfName = "dikow_2012.pdf";
		pdfName = "3868.pdf";
//		pdfName = "MEZ_909-919.pdf";
		
		ImDocument imDoc = ImDocumentIO.loadDocument(new FileInputStream(new File(baseFolder, (pdfName + ".imf"))));
//		ImPage[] imPages = imDoc.getPages();
//		System.out.println("Document loaded, got " + imPages.length + " pages");
//		for (int p = 0; p < imPages.length; p++) {
//			ImWord[] imWords = imPages[p].getWords();
//			System.out.println(" - got " + imWords.length + " words in gape " + p);
//			PageImage pi = PageImage.getPageImage(imDoc.docId, imPages[p].pageId);
//			System.out.println(" - got page image, size is " + pi.image.getWidth() + "x" + pi.image.getHeight());
//			
//			if (p == 4) {
//				new ImAnnotation(imDoc, imWords[0], imWords[5], "heading");
//				new ImAnnotation(imDoc, imWords[6], "pageNumber");
//				for (ImWord imw = imWords[0]; imw != null; imw = imw.getNextWord()) {
//					if ("Abdomen".equals(imw.getString())) {
//						new ImAnnotation(imDoc, imw, imw.getNextWord().getNextWord(), "inLineHeading");
//						new ImAnnotation(imDoc, imw, "inLineHeadWord");
//						new ImAnnotation(imDoc, imw.getPreviousWord(), imw, "crossLineHeadWord");
//						ImWord pew;
//						for (pew = imw; pew != null; pew = pew.getNextWord()) {
//							if (pew.getNextRelation() == ImWord.NEXT_RELATION_PARAGRAPH_END)
//								break;
//						}
//						new ImAnnotation(imDoc, imw, pew, "paragraph");
//					}
//				}
//			}
//		}
		
		JFrame f = new JFrame();
		
		ImDocumentMarkupPanel p = new ImDocumentMarkupPanel(imDoc, 0, 4);
//		p.setTextStreamsPainted(true);
		p.setRenderingDpi(150);
//		p.setRegionsPainted(BLOCK_ANNOTATION_TYPE, true);
//		p.setRegionsPainted(PARAGRAPH_TYPE, true);
		p.setSideBySidePages(2);
		p.setTextStreamsPainted(true);
		JScrollPane pbox = new JScrollPane();
		pbox.getVerticalScrollBar().setUnitIncrement(50);
		pbox.getVerticalScrollBar().setBlockIncrement(50);
		pbox.setViewport(new IdmpViewport(p));
		f.setSize(Math.min((p.getPreferredSize().width + p.getControlPanel().getPreferredSize().width), 1500), 1000);
		
		f.getContentPane().setLayout(new BorderLayout());
		f.getContentPane().add(pbox, BorderLayout.CENTER);
		f.getContentPane().add(p.getControlPanel(), BorderLayout.EAST);
		f.setLocationRelativeTo(null);
		f.setVisible(true);
	}
	
	private static class IdmpViewport extends JViewport implements TwoClickActionMessenger {
		private static Color halfTransparentRed = new Color(Color.red.getRed(), Color.red.getGreen(), Color.red.getBlue(), 128);
		private ImDocumentMarkupPanel idmp;
		private String tcaMessage = null;
		IdmpViewport(ImDocumentMarkupPanel idmp) {
			this.idmp = idmp;
			this.idmp.setTwoClickActionMessenger(this);
			this.setView(this.idmp);
			this.setOpaque(false);
		}
		public void twoClickActionChanged(TwoClickSelectionAction tcsa) {
			this.tcaMessage = ((tcsa == null) ? null : tcsa.getActiveLabel());
			this.validate();
			this.repaint();
		}
		public void paint(Graphics g) {
			super.paint(g);
			if (this.tcaMessage == null)
				return;
			Font f = new Font("SansSerif", Font.PLAIN, 20);
			g.setFont(f);
			TextLayout wtl = new TextLayout(this.tcaMessage, f, ((Graphics2D) g).getFontRenderContext());
			g.setColor(halfTransparentRed);
			g.fillRect(0, 0, this.getViewRect().width, ((int) Math.ceil(wtl.getBounds().getHeight() + (wtl.getDescent() * 3))));
			g.setColor(Color.white);
			((Graphics2D) g).drawString(this.tcaMessage, ((this.getViewRect().width - wtl.getAdvance()) / 2), ((int) Math.ceil(wtl.getBounds().getHeight() + wtl.getDescent())));
		}
	}
	
	private class ImPageMarkupPanelLayout implements LayoutManager {
		private ImPageMarkupPanel parent;
		ImPageMarkupPanelLayout(ImPageMarkupPanel parent) {
			this.parent = parent;
		}
		public void addLayoutComponent(String name, Component comp) {}
		public void removeLayoutComponent(Component comp) {}
		public Dimension preferredLayoutSize(Container parent) {
			return this.parent.getPreferredSize();
//			Dimension pSize = this.parent.getPreferredSize();
//			Rectangle oBounds = this.computeRawOverlayBounds();
//			if (oBounds == null)
//				return pSize;
//			if (oBounds.x < 0)
//				pSize.width -= oBounds.x;
//			if (pSize.width < (oBounds.x + oBounds.width))
//				pSize.width = (oBounds.x + oBounds.width);
//			if (oBounds.y < 0)
//				pSize.height -= oBounds.y;
//			if (pSize.height < (oBounds.y + oBounds.height))
//				pSize.height = (oBounds.y + oBounds.height);
//			return pSize;
		}
		public Dimension minimumLayoutSize(Container parent) {
			return this.parent.getPreferredSize();
//			return this.preferredLayoutSize(parent);
		}
		public void layoutContainer(Container target) {
			if (this.parent.overlay == null)
				return;
			synchronized (target.getTreeLock()) {
				Insets oi = this.parent.overlay.getInsets();
				System.out.println("Overlay insets are " + oi.toString());
				int iLeft = oi.left;
				int iRight = oi.right;
				int iTop = oi.top;
				int iBottom = oi.bottom;
				float ha = this.parent.overlay.getHorizontalAnchor();
				if ((ha != 0.5f) && ((iLeft + iRight) != 0)) {
					ha = Math.max(0, Math.min(ha, 1.0f));
					int lt = Math.round((1 - ha) * iLeft);
					int rt = Math.round(ha * iRight);
					iLeft -= lt;
					iRight -= rt;
					iLeft += rt;
					iRight += lt;
				}
				float va = this.parent.overlay.getVerticalAnchor();
				if ((va != 0.5f) && ((iTop + iBottom) != 0)) {
					va = Math.max(0, Math.min(va, 1.0f));
					int tt = Math.round((1 - va) * iTop);
					int bt = Math.round(va * iBottom);
					iTop -= tt;
					iBottom -= bt;
					iTop += bt;
					iBottom += tt;
				}
//				this.parent.overlay.setBounds(
//						(this.parent.getLeftOffset() - iLeft + ((this.parent.overlay.onPageLocation.x * renderingDpi) / this.parent.page.getImageDPI())),
//						(this.parent.getTopOffset() - iTop + ((this.parent.overlay.onPageLocation.y * renderingDpi) / this.parent.page.getImageDPI())),
//						(iLeft + ((this.parent.overlay.onPageSize.width * renderingDpi) / this.parent.page.getImageDPI()) + iRight),
//						(iTop + ((this.parent.overlay.onPageSize.height * renderingDpi) / this.parent.page.getImageDPI()) + iBottom)
//					);
				int oLeft = (this.parent.getLeftOffset() - iLeft + ((this.parent.overlay.onPageLocation.x * renderingDpi) / this.parent.page.getImageDPI()));
				int oTop = (this.parent.getTopOffset() - iTop + ((this.parent.overlay.onPageLocation.y * renderingDpi) / this.parent.page.getImageDPI()));
				int oWidth = (iLeft + ((this.parent.overlay.onPageSize.width * renderingDpi) / this.parent.page.getImageDPI()) + iRight);
				int oHeight = (iTop + ((this.parent.overlay.onPageSize.height * renderingDpi) / this.parent.page.getImageDPI()) + iBottom);
				Dimension pSize = this.parent.getPreferredSize();
				if (pSize.width < oWidth)
					oWidth = pSize.width;
				if (oLeft < 0)
					oLeft = 0;
				if (pSize.width < (oLeft + oWidth))
					oLeft = (pSize.width - oWidth);
				if (pSize.height < oHeight)
					oHeight = pSize.height;
				if (oTop < 0)
					oTop = 0;
				if (pSize.height < (oTop + oHeight))
					oTop = (pSize.height - oHeight);
				this.parent.overlay.setBounds(oLeft, oTop, oWidth, oHeight);
//				Rectangle oBounds = this.computeRawOverlayBounds();
//				if (oBounds.x < 0)
//					oBounds.x = 0;
//				if (oBounds.y < 0)
//					oBounds.y = 0;
//				this.parent.overlay.setBounds(oBounds);
			}
		}
//		private Rectangle computeRawOverlayBounds() {
//			if (this.parent.overlay == null)
//				return null;
//			Insets oi = this.parent.overlay.getInsets();
//			System.out.println("Overlay insets are " + oi.toString());
//			int iLeft = oi.left;
//			int iRight = oi.right;
//			int iTop = oi.top;
//			int iBottom = oi.bottom;
//			float ha = this.parent.overlay.getHorizontalAnchor();
//			if ((ha != 0.5f) && ((iLeft + iRight) != 0)) {
//				ha = Math.max(0, Math.min(ha, 1.0f));
//				int lt = Math.round((1 - ha) * iLeft);
//				int rt = Math.round(ha * iRight);
//				iLeft -= lt;
//				iRight -= rt;
//				iLeft += rt;
//				iRight += lt;
//			}
//			float va = this.parent.overlay.getVerticalAnchor();
//			if ((va != 0.5f) && ((iTop + iBottom) != 0)) {
//				va = Math.max(0, Math.min(va, 1.0f));
//				int tt = Math.round((1 - va) * iTop);
//				int bt = Math.round(va * iBottom);
//				iTop -= tt;
//				iBottom -= bt;
//				iTop += bt;
//				iBottom += tt;
//			}
//			return new Rectangle(
//					(this.parent.getLeftOffset() - iLeft + ((this.parent.overlay.onPageLocation.x * renderingDpi) / this.parent.page.getImageDPI())),
//					(this.parent.getTopOffset() - iTop + ((this.parent.overlay.onPageLocation.y * renderingDpi) / this.parent.page.getImageDPI())),
//					(iLeft + ((this.parent.overlay.onPageSize.width * renderingDpi) / this.parent.page.getImageDPI()) + iRight),
//					(iTop + ((this.parent.overlay.onPageSize.height * renderingDpi) / this.parent.page.getImageDPI()) + iBottom)
//				);
//		}
	}
	
	private class ImPageMarkupPanel extends JPanel {
		
		/** the page displayed in this panel */
		final ImPage page;
		
		private int pageImageDpi = -1;
		
		private int pageWidth;
		private int pageHeight;
		private int pageMarginLeft;
		private int pageMarginTop;
		
		long docModCount = 0; // changes to the document proper (all we need is the listener)
		
		DisplayOverlay overlay = null;
		
		ImPageMarkupPanel(ImPage page, int pageWidth, int pageHeight) {
			super(new FlowLayout(), true);
			this.page = page;
			this.pageWidth = pageWidth;
			this.pageHeight = pageHeight;
			this.pageMarginLeft = ((this.pageWidth - this.page.bounds.getWidth()) / 2);
//			this.pageMarginTop = ((this.pageHeight - this.page.bounds.getHeight()) / 2);
			this.pageMarginTop = (((this.pageHeight - this.page.bounds.getHeight()) * 2) / 5); // use 40/60 split between top & bottom, as bottom margin often wider
			this.pageImageDpi = this.page.getImageDPI();
//			this.addComponentListener(new ComponentAdapter() {
//				public void componentShown(ComponentEvent ce) {
//					//	hold on to page image with strong reference as long as we're visible (we'll need it for rendering during that time)
//					if (scaledPageImageWeak != null) {
//						scaledPageImage = ((BufferedImage) scaledPageImageWeak.get());
//						System.out.println("ImPageMarkupPanel: page image reference switched to strong");
//					}
//				}
//				public void componentHidden(ComponentEvent ce) {
//					//	reduce hold on page image to weak reference when we're becoming invisible (helps GC determine which page images to clean up first when in a pinch)
//					scaledPageImage = null;
//					System.out.println("ImPageMarkupPanel: page image reference switched to weak");
//				}
//			});
			Border border = BorderFactory.createLineBorder(Color.DARK_GRAY, 1);
			border = BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 1, Color.GRAY), border);
			border = BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 1, Color.LIGHT_GRAY), border);
			this.setBorder(border);
			this.setOpaque(false); // for better refresh behavior (we're painting everything ourselves below)
			System.out.println("Got page image resolution from page image: " + this.pageImageDpi);
			this.setLayout(new ImPageMarkupPanelLayout(this));
		}
		
		ImDocumentMarkupPanel getParentPanel() {
			return ImDocumentMarkupPanel.this;
		}
		
		int getLeftOffset() {
			return (fixPageMargin + Math.round(((float) (this.pageMarginLeft * renderingDpi)) / this.pageImageDpi));
		}
		
		int getTopOffset() {
			return (fixPageMargin + Math.round(((float) (this.pageMarginTop * renderingDpi)) / this.pageImageDpi));
		}
		
		private BufferedImage getScaledPageImage() {
//			if ((this.scaledPageImage == null) && (this.scaledPageImageWeak != null))
//				this.scaledPageImage = ((BufferedImage) this.scaledPageImageWeak.get());
			if ((this.scaledPageImage == null) || (this.scaledPageImageDpi != renderingDpi)) {
				PageImage pi = this.page.getImage();
				BufferedImage bi;
				
				//	no need for scaling, we're showing at original resolution
				if (pi.currentDpi == renderingDpi)
					bi = pi.image;
				
				//	scale original page image via on-board facilities
				else {
					int sbiWidth = ((int) Math.ceil(((float) (pi.image.getWidth() * renderingDpi)) / pi.currentDpi));
					int sbiHeight = ((int) Math.ceil(((float) (pi.image.getHeight() * renderingDpi)) / pi.currentDpi));
					BufferedImage sbi = new BufferedImage(sbiWidth, sbiHeight, BufferedImage.TYPE_INT_ARGB);
					Graphics2D sbiGr = sbi.createGraphics();
					sbiGr.setColor(Color.WHITE);
					sbiGr.fillRect(0, 0, sbiWidth, sbiHeight);
					if (renderingDpi < pi.currentDpi)
						sbiGr.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
					else sbiGr.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
					sbiGr.drawImage(pi.image, 0, 0, sbiWidth, sbiHeight, null);
					bi = sbi;
				}
				
				//	discretize (possibly scaled) page image
				BufferedImage pbi = new BufferedImage(bi.getWidth(), bi.getHeight(), BufferedImage.TYPE_BYTE_INDEXED, pageImageColorModel);
				WritableRaster pwr = pbi.getRaster();
				int rgb, r, g, b;
				int[] c = new int[1];
				for (int x = 0; x < bi.getWidth(); x++)
					for (int y = 0; y < bi.getHeight(); y++) {
						rgb = bi.getRGB(x, y);
						r = ((rgb & 0x00ff0000) >> 16);
						g = ((rgb & 0x0000ff00) >> 8);
						b = ((rgb & 0x000000ff) >> 0);
						//	based on "C = 0.2126 R + 0.7152 G + 0.0722 B" from http://en.wikipedia.org/wiki/Grayscale#Converting_color_to_grayscale
						c[0] = (((21 * r) + (72 * g) + (7 * b)) / 100);
						if (c[0] > 252)
							c[0] = 255;
						pwr.setPixel(x, y, c);
					}
				
				//	activate scaled image
//				this.scaledPageImageWeak = new WeakReference(pbi);
				this.scaledPageImage = pbi;
				this.scaledPageImageDpi = renderingDpi;
				
				//	make sure to clean up (this way, excess memory usage is restricted to one page at a time)
				pi = null;
				bi = null;
				System.gc();
			}
			return this.scaledPageImage;
		}
//		private WeakReference scaledPageImageWeak = null; // ==> component listener doesn't seem to work properly, so no point in using this ...
		private BufferedImage scaledPageImage = null;
		private int scaledPageImageDpi = -1;
		/*
TODO Re-consider using weak references for page images in IM document markup panels
- might help in low-memory situations, if at cost of possibly reloading page image later
- maybe add GC probe and (obviously weak) instance list to IM document markup panel ...
- ... and move page images to weak references only if not used in 2-3 between-QC intervals
  ==> might in fact do similar thing with page image replacement before-values in GGI UNDO history ...
  ==> ... moving said images to disk based cache on weakening reference
		 */
		
		/* (non-Javadoc)
		 * @see javax.swing.JComponent#getPreferredSize()
		 */
		public Dimension getPreferredSize() {
			if (this.pageImageDpi == -1)
				return super.getPreferredSize();
			return new Dimension((Math.round(((float) (this.pageWidth * renderingDpi)) / this.pageImageDpi) + (fixPageMargin * 2)), (Math.round(((float) (this.pageHeight * renderingDpi)) / this.pageImageDpi) + (fixPageMargin * 2)));
		}
		
		private TextStringImage[] getTextStringImagesOCR() {
			if (this.textStringImages != null)
				return this.textStringImages;
			
			//	get page image and compute zoom
			float zoom = (((float) renderingDpi) / this.pageImageDpi);
			int roundAdd = (this.pageImageDpi / 2);
			
			//	paint words
			ArrayList tsiList = new ArrayList();
			int estimatedWordFontSize = 1;
			ImRegion line = null;
			ImWord[] tshs = this.page.getTextStreamHeads();
			for (int h = 0; h < tshs.length; h++) {
				
				//	paint current text stream
				for (ImWord imw = tshs[h]; (imw != null) && (imw.pageId == this.page.pageId); imw = imw.getNextWord()) {
					
					//	check if we are still in line
					if ((line != null) && !line.bounds.includes(imw.bounds, true))
						line = null;
					
					//	find current line
					if (line == null) {
						ImRegion[] regions = this.page.getRegionsIncluding(imw.bounds, true);
						for (int r = 0; r < regions.length; r++)
							if (LINE_ANNOTATION_TYPE.equals(regions[r].getType())) {
								line = regions[r];
								break;
							}
					}
					
					//	create image
					int tsiLeft = (((imw.bounds.left * renderingDpi) + roundAdd) / this.pageImageDpi);
					int tsiTop = (((imw.bounds.top * renderingDpi) + roundAdd) / this.pageImageDpi);
					int tsiWidth = Math.max(((((imw.bounds.right - imw.bounds.left) * renderingDpi) + roundAdd) / this.pageImageDpi), 1);
					int tsiHeight = Math.max(((((imw.bounds.bottom - imw.bounds.top) * renderingDpi) + roundAdd) / this.pageImageDpi), 1);
					TextStringImage tsi = new TextStringImage(tsiLeft, tsiTop, tsiWidth, tsiHeight);
					tsiList.add(tsi);
					Graphics2D tsig = tsi.createGraphics();
					tsig.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
					
					//	anything to render for this one?
					String imwString = imw.getString();
					if ((imwString == null) || (imwString.trim().length() == 0))
						continue;
					
					//	initialize font
					tsig.setColor(ocrTextStringForeground);
					int fontStyle = Font.PLAIN;
					if (imw.hasAttribute(BOLD_ATTRIBUTE))
						fontStyle = (fontStyle | Font.BOLD);
					if (imw.hasAttribute(ITALICS_ATTRIBUTE))
						fontStyle = (fontStyle | Font.ITALIC);
					int fontSize;
					Font rf;
					
					//	get word baseline
//					int imwBaseline = ((documentBornDigital || (line == null)) ? -1 : Integer.parseInt((String) line.getAttribute(BASELINE_ATTRIBUTE, "-1")));
					int imwBaseline = -1;
					if (!documentBornDigital) {
						if ((imwBaseline == -1) && imw.hasAttribute(BASELINE_ATTRIBUTE))
//							imwBaseline = Integer.parseInt((String) imw.getAttribute(BASELINE_ATTRIBUTE, "-1"));
							imwBaseline = imw.getBaseline();
						if ((imwBaseline == -1) && (line != null) && line.hasAttribute(BASELINE_ATTRIBUTE))
							imwBaseline = Integer.parseInt((String) line.getAttribute(BASELINE_ATTRIBUTE, "-1"));
					}
					if (imwBaseline < imw.centerY)
						imwBaseline = -1;
					
					//	test if word string has descent
					boolean imwHasDescent;
					
					//	adjust font size
					if (imw.hasAttribute(FONT_SIZE_ATTRIBUTE) && documentBornDigital) {
						fontSize = imw.getFontSize();
//						rf = new Font("Serif", fontStyle, Math.round(((float) (fontSize * this.pageImageDpi)) / 72));
						rf = getTextStringFont(fontStyle, true, Math.round(((float) (fontSize * this.pageImageDpi)) / 72));
						imwHasDescent = true; // doesn't really matter, as it's queried only in disjunction with born-digital property anyway
					}
					else if (this.isFlatString(imwString)) {
						fontSize = estimatedWordFontSize;
//						rf = new Font("Serif", fontStyle, Math.round(((float) (fontSize * this.pageImageDpi)) / 72));
						rf = getTextStringFont(fontStyle, true, Math.round(((float) (fontSize * this.pageImageDpi)) / 72));
						imwHasDescent = false;
					}
					else {
						fontSize = estimatedWordFontSize;
//						rf = new Font("Serif", fontStyle, Math.round(((float) (fontSize * this.pageImageDpi)) / 72));
						rf = getTextStringFont(fontStyle, true, Math.round(((float) (fontSize * this.pageImageDpi)) / 72));
						String imwFsString = imwString;
						if (!this.isAscendingString(imwFsString) && (line != null) && ((imw.bounds.getHeight() * 10) > (line.bounds.getHeight() * 9)))
							imwFsString = ("d" + imwFsString + "b"); // we have a full (> 90%) line height bounding box, make sure we measure with an ascender
						TextLayout wtl = new TextLayout(imwFsString, rf, tsig.getFontRenderContext());
						imwHasDescent = (this.isDecendingString(imwFsString, imw.hasAttribute(ITALICS_ATTRIBUTE)) || ((Math.abs(wtl.getBounds().getY()) * 10) < (Math.abs(wtl.getBounds().getHeight()) * 9)));
//						System.out.println("Adjusting font size for '" + imw.getString() + "' starting from " + fontSize + ", initial bounds are " + wtl.getBounds());
						while ((wtl.getBounds().getHeight() < (((imwHasDescent || (imwBaseline < 1)) ? imw.bounds.bottom : (imwBaseline + 1)) - imw.bounds.top)) || ((0 < imwBaseline) && (Math.abs(wtl.getBounds().getY()) < ((imwBaseline + 1) - imw.bounds.top)))) {
							fontSize++;
//							rf = new Font("Serif", fontStyle, Math.round(((float) (fontSize * this.pageImageDpi)) / 72));
							rf = getTextStringFont(fontStyle, true, Math.round(((float) (fontSize * this.pageImageDpi)) / 72));
							wtl = new TextLayout(imwFsString, rf, tsig.getFontRenderContext());
//							System.out.println(" - increased bounds at " + fontSize + " are " + wtl.getBounds());
						}
						while (((((imwHasDescent || (imwBaseline < 1)) ? imw.bounds.bottom : (imwBaseline + 1)) - imw.bounds.top) < wtl.getBounds().getHeight()) || ((0 < imwBaseline) && (((imwBaseline + 1) - imw.bounds.top) < Math.abs(wtl.getBounds().getY()))))  {
							fontSize--;
//							rf = new Font("Serif", fontStyle, Math.round(((float) (fontSize * this.pageImageDpi)) / 72));
							rf = getTextStringFont(fontStyle, true, Math.round(((float) (fontSize * this.pageImageDpi)) / 72));
							wtl = new TextLayout(imwFsString, rf, tsig.getFontRenderContext());
//							System.out.println(" - decreased bounds at " + fontSize + " are " + wtl.getBounds());
						}
						estimatedWordFontSize = fontSize;
					}
					
					//	adjust word size and vertical position
					tsig.setFont(rf);
					LineMetrics wlm = rf.getLineMetrics(imwString, tsig.getFontRenderContext());
					TextLayout wtl = new TextLayout(imwString, rf, tsig.getFontRenderContext());
					double hScale = (((double) imw.bounds.getWidth()) / wtl.getBounds().getWidth());
//					System.out.println("Printing '" + imw.getString() + "' in " + imw.bounds.toString() + ", font size is " + fontSize + ", hShift is " + -wtl.getBounds().getMinX() + ", hScale is " + hScale + ", baseline is " + imwBaseline + ", descent is " + imwHasDescent + ", born-digital is " + documentBornDigital);
					
					//	draw word
					tsig.translate(-wtl.getBounds().getMinX(), 0);
					if (Math.abs(hScale - 1) > 0.001) // cut this a little bit of slack
						tsig.scale(hScale, 1);
					tsig.scale(zoom, zoom);
					tsig.drawString(imwString, 0, (
								(imwBaseline < 1)
								?
								//	no explicit baseline given, need to estimate
								(
									(imw.bounds.bottom - imw.bounds.top) - (
										(documentBornDigital || imwHasDescent)
										?
										Math.round(wlm.getDescent())
										:
										0
									)
								)
								:
								//	we have an explicit baseline
								(imwBaseline - imw.bounds.top + 1)
							)
						);
					
					//	finally ...
					tsig.dispose();
				}
			}
			
			//	finally ...
			this.textStringImageDocModCount = this.docModCount;
			this.textStringImageTextStringPercentageModCount = textStringPercentageModCount;
			this.textStringImageRenderingDpiModCount = renderingDpiModCount;
			this.textStringImages = ((TextStringImage[]) tsiList.toArray(new TextStringImage[tsiList.size()]));
			return this.textStringImages;
		}
		
		private TextStringImage[] getTextStringImagesBD(BufferedImage spi) {
			if (this.textStringImages != null)
				return this.textStringImages;
			
			//	get page image and compute zoom
			int roundAdd = (this.pageImageDpi / 2);
			
			//	paint words
			ArrayList tsiList = new ArrayList();
			ImWord[] tshs = this.page.getTextStreamHeads();
			for (int h = 0; h < tshs.length; h++) {
				
				//	paint current text stream
				for (ImWord imw = tshs[h]; (imw != null) && (imw.pageId == this.page.pageId); imw = imw.getNextWord()) {
					
					//	anything to render for this one?
					String imwString = imw.getString();
					if ((imwString == null) || (imwString.trim().length() == 0))
						continue;
					
					//	create image
					int tsiLeft = (((imw.bounds.left * renderingDpi) + roundAdd) / this.pageImageDpi);
					int tsiTop = (((imw.bounds.top * renderingDpi) + roundAdd) / this.pageImageDpi);
					int tsiWidth = Math.max(((((imw.bounds.right - imw.bounds.left) * renderingDpi) + roundAdd) / this.pageImageDpi), 1);
					int tsiHeight = Math.max(((((imw.bounds.bottom - imw.bounds.top) * renderingDpi) + roundAdd) / this.pageImageDpi), 1);
					TextStringImage tsi = new TextStringImage(tsiLeft, tsiTop, tsiWidth, tsiHeight, bdBlackTextStringColorModel);
					tsiList.add(tsi);
					for (int c = 0; c < tsiWidth; c++) {
						if ((tsiLeft + c) < 0)
							continue;
						if (spi.getWidth() <= (tsiLeft + c))
							break;
						for (int r = 0; r < tsiHeight; r++) {
							if ((tsiTop + r) < 0)
								continue;
							if (spi.getHeight() <= (tsiTop + r))
								break;
							int rgb = spi.getRGB((tsiLeft + c), (tsiTop + r));
							if ((rgb & 0xFF) < 32)
								tsi.setRGB(c, r, bdBlackTextStringForeground.getRGB());
						}
					}
				}
			}
			
			//	finally ...
			this.textStringImageDocModCount = this.docModCount;
			this.textStringImageTextStringPercentageModCount = textStringPercentageModCount;
			this.textStringImageRenderingDpiModCount = renderingDpiModCount;
			this.textStringImages = ((TextStringImage[]) tsiList.toArray(new TextStringImage[tsiList.size()]));
			return this.textStringImages;
		}
		private TextStringImage[] textStringImages = null;
		private long textStringImageDocModCount = 0;
		private long textStringImageTextStringPercentageModCount = 0;
		private long textStringImageRenderingDpiModCount = 0;
		
		private class TextStringImage extends BufferedImage {
			final int left;
			final int top;
			TextStringImage(int left, int top, int width, int height) {
				this(left, top, width, height, ocrTextStringColorModel);
			}
			TextStringImage(int left, int top, int width, int height, IndexColorModel colorModel) {
				super(width, height, BufferedImage.TYPE_BYTE_BINARY, colorModel);
				this.left = left;
				this.top = top;
			}
		}
		
		private boolean isFlatString(String str) {
			for (int c = 0; c < str.length(); c++) {
				if (".,:;�_-~*+'�`\"\u00AD\u2010\u2011\u2012\u2013\u2014\u2015\u2212".indexOf(str.charAt(c)) == -1)
					return false;
			}
			return true;
		}
		
		private boolean isAscendingString(String str) {
			for (int c = 0; c < str.length(); c++) {
				if ("acegmnopqrsuvwxyz.,:;_-~+=\u00AD\u2010\u2011\u2012\u2013\u2014\u2015\u2212".indexOf(str.charAt(c)) == -1)
					return true;
			}
			return false;
		}
		
		private boolean isDecendingString(String str, boolean italics) {
			for (int c = 0; c < str.length(); c++) {
				char bch = StringUtils.getBaseChar(str.charAt(c));
				if ("gjpqy".indexOf(bch) != -1)
					return true;
				else if (italics && (bch == 'f'))
					return true;
			}
			return true;
		}
		
		private BackgroundObject[] getBackgroundObjects() {
			if (this.backgroundObjects != null) {
				if (this.backgroundObjectRenderingDpiModCount < renderingDpiModCount) {
					for (int o = 0; o < this.backgroundObjects.length; o++)
						this.backgroundObjects[o].checkZoomAndPosition();
					this.backgroundObjectRenderingDpiModCount = renderingDpiModCount;
				}
				return this.backgroundObjects;
			}
			
			//	collect background objects
			ArrayList boList = new ArrayList();
			
			//	get annotations and sort annotations
			ImAnnotation[] annots = this.page.getDocument().getAnnotations(this.page.pageId);
			Arrays.sort(annots, new Comparator() {
				public int compare(Object obj1, Object obj2) {
					ImAnnotation a1 = ((ImAnnotation) obj1);
					ImAnnotation a2 = ((ImAnnotation) obj2);
					int c = ImUtils.textStreamOrder.compare(a1.getFirstWord(), a2.getFirstWord());
					return ((c == 0) ? ImUtils.textStreamOrder.compare(a2.getLastWord(), a1.getLastWord()) : c);
				}
			});
			
			//	paint highlights for (activated) annotations
			for (int a = 0; a < annots.length; a++) {
				if (!areAnnotationsPainted(annots[a].getType()))
					continue;
				
				//	get anchor words
				ImWord fw = annots[a].getFirstWord();
				ImWord lw = annots[a].getLastWord();
				
				//	create transparent color
//				Color annotColor = getAnnotationColor(annots[a].getType(), true);
//				Color annotHighlightColor = new Color(annotColor.getRed(), annotColor.getGreen(), annotColor.getBlue(), 64);
				Color annotHighlightColor = getAnnotationHighlightColor(annots[a].getType(), true);
				
				//	paint word highlights
				for (ImWord imw = fw; imw != null; imw = ((imw == lw) ? null : imw.getNextWord())) {
					if (imw.pageId < this.page.pageId)
						continue;
					if (imw.pageId > this.page.pageId)
						break;
					
					if ((imw == fw) || (imw.getPreviousWord() == null) || (imw.getPreviousWord().pageId != imw.pageId) || (imw.getPreviousWord().bounds.right > imw.bounds.left) || (imw.getPreviousWord().bounds.top > imw.bounds.bottom) || (imw.getPreviousWord().bounds.bottom < imw.bounds.top))
						boList.add(new AnnotHighlight(imw.bounds.left, imw.bounds.right, imw.bounds.top, imw.bounds.bottom, annotHighlightColor));
					else boList.add(new AnnotHighlight(imw.getPreviousWord().bounds.right, imw.bounds.right, imw.bounds.top, imw.bounds.bottom, annotHighlightColor));
				}
			}
			
			//	get words
			this.textStreamHeads = this.page.getTextStreamHeads();
			this.textStreamTails = this.page.getTextStreamTails();
			
			//	paint words
			for (int h = 0; h < this.textStreamHeads.length; h++) {
				
				//	get text stream color
				Color textStreamColor = getTextStreamTypeColor(this.textStreamHeads[h].getTextStreamType(), true);
				
				//	paint current text stream
				for (ImWord imw = this.textStreamHeads[h]; (imw != null) && (imw.pageId == this.page.pageId); imw = imw.getNextWord()) {
					int flags = 0;
					
					//	check whether or not to paint left and right line of word box
					if ((imw.getPreviousWord() == null) || ((imw.getPreviousWord().getNextRelation() != ImWord.NEXT_RELATION_HYPHENATED) && (imw.getPreviousWord().getNextRelation() != ImWord.NEXT_RELATION_CONTINUE)))
						flags |= 0x02;
					if ((imw.getNextWord() == null) || ((imw.getNextRelation() != ImWord.NEXT_RELATION_HYPHENATED) && (imw.getNextRelation() != ImWord.NEXT_RELATION_CONTINUE)))
						flags |= 0x01;
					
					//	don't draw word relation markers for artifacts and deleted words
					if (!ImWord.TEXT_STREAM_TYPE_DELETED.equals(imw.getTextStreamType()) && !ImWord.TEXT_STREAM_TYPE_ARTIFACT.equals(imw.getTextStreamType())) {
						
						//	visualize paragraph start
						if ((imw.getPreviousWord() == null) || (imw.getPreviousWord().getNextRelation() == ImWord.NEXT_RELATION_PARAGRAPH_END))
							flags |= 0x08;
						
						//	visualize paragraph end
						if ((imw.getNextWord() == null) || (imw.getNextRelation() == ImWord.NEXT_RELATION_PARAGRAPH_END))
							flags |= 0x04;
					}
					
					//	paint word box
					boList.add(new WordRectangle(imw.bounds.left, imw.bounds.right, imw.bounds.top, imw.bounds.bottom, textStreamColor, ((byte) flags)));
					
					//	no text stream connectors to paint
					if (!areTextStreamsPainted())
						continue;
					
					//	no predecessor on same page to connect to
					if ((imw.getPreviousWord() == null) || (imw.getPreviousWord().pageId != imw.pageId))
						continue;
					
					//	compute connector line sequence (zoom independent)
					Point[] cls = this.getConnectorLineSequence(imw.getPreviousWord().bounds, imw.bounds);
					boList.add(new WordConnector(textStreamColor, cls));
				}
			}
			
			/* paint (activated) regions, outdenting on nesting so lines don't obfuscate one another:
			 * - collect _painted_ regions
			 * - compute outdent for each (exploiting that regions are sorted by decreasing size)
			 *   - initialize outdent for each region to -1
			 *   - for each region, test if it contains other regions, and assign outdent 0 to the ones that do not
			 *   - for each remaining region, test if it contains other regions with unknown outdent, and assign outdent (largest-contained-outdent + 1) to the ones that do not
			 *   - repeat the latter until all regions have an outdent assigned
			 * ==> far better visualization
			 * */
			ImRegion[] regions = this.page.getRegions();
			ArrayList paintedRegions = new ArrayList();
			for (int r = 0; r < regions.length; r++) {
				if (areRegionsPainted(regions[r].getType()))
					paintedRegions.add(regions[r]);
			}
			if (paintedRegions.size() < regions.length)
				regions = ((ImRegion[]) paintedRegions.toArray(new ImRegion[paintedRegions.size()]));
			int[] regionOutdents = new int[regions.length];
			Arrays.fill(regionOutdents, -1);
			for (boolean remaining = true; remaining;) {
				remaining = false;
				for (int r = 0; r < regions.length; r++) {
					if (regionOutdents[r] != -1)
						continue;
					int regionOutdent = 0;
					for (int cr = (r+1); cr < regions.length; cr++) {
						if (!regions[r].bounds.includes(regions[cr].bounds, false))
							continue;
						if (regionOutdents[cr] == -1) {
							regionOutdent = -1;
							break;
						}
						else regionOutdent = Math.max(regionOutdent, (regionOutdents[cr] + 1));
					}
					if (regionOutdent == -1)
						remaining = true;
					else regionOutdents[r] = regionOutdent;
				}
			}
			for (int r = 0; r < regionOutdents.length; r++)
				regionOutdents[r] *= 2;
			for (int r = 0; r < regions.length; r++) {
				Color layoutObjectColor = getLayoutObjectColor(regions[r].getType(), true);
				if (ImRegion.IMAGE_TYPE.equals(regions[r].getType()) || ImRegion.GRAPHICS_TYPE.equals(regions[r].getType()))
					boList.add(new RegionRectangle(regions[r].bounds.left, regions[r].bounds.right, regions[r].bounds.top, regions[r].bounds.bottom, layoutObjectColor, regionOutdents[r]));
				else boList.add(new RegionRectangle(regions[r].bounds.left - 2, regions[r].bounds.right, regions[r].bounds.top - 2, regions[r].bounds.bottom, layoutObjectColor, regionOutdents[r]));
			}
			
			//	count annotation starts and ends
			CountingSet annotStartEndCounts = new CountingSet();
			for (int a = 0; a < annots.length; a++) {
				if (!areAnnotationsPainted(annots[a].getType()))
					continue;
				annotStartEndCounts.add("S" + annots[a].getFirstWord().getLocalID());
				annotStartEndCounts.add("E" + annots[a].getLastWord().getLocalID());
			}
			
			//	paint starts and ends of (activated) annotations
			for (int a = 0; a < annots.length; a++) {
				if (!areAnnotationsPainted(annots[a].getType()))
					continue;
				
				//	create transparent color
				Color annotColor = getAnnotationColor(annots[a].getType(), true);
				
				//	get anchor words
				ImWord fw = annots[a].getFirstWord();
				ImWord lw = annots[a].getLastWord();
				
				//	paint opaque kind of square bracket before first and after last word
				if (fw.pageId == this.page.pageId) {
					int out = annotStartEndCounts.getCount("S" + fw.getLocalID());
					annotStartEndCounts.remove("S" + fw.getLocalID());
					if ((fw.getPreviousWord() == null) || (fw.getPreviousWord().getNextRelation() == ImWord.NEXT_RELATION_PARAGRAPH_END))
						out++; // make sure to not obfuscate paragraph start thickening
					boList.add(new AnnotStart(fw.bounds.left, fw.bounds.right, fw.bounds.top, fw.bounds.bottom, annotColor, out));
				}
				if (lw.pageId == this.page.pageId) {
					int out = annotStartEndCounts.getCount("E" + lw.getLocalID());
					annotStartEndCounts.remove("E" + lw.getLocalID());
					if ((lw.getNextWord() == null) || (lw.getNextRelation() == ImWord.NEXT_RELATION_PARAGRAPH_END))
						out++; // make sure to not obfuscate paragraph end thickening
					boList.add(new AnnotEnd(lw.bounds.left, lw.bounds.right, lw.bounds.top, lw.bounds.bottom, annotColor, out));
				}
			}
			
			//	finally ...
			this.backgroundObjectDocModCount = this.docModCount;
			this.backgroundObjectHighlightModCount = highlightModCount;
			this.backgroundObjectRenderingDpiModCount = renderingDpiModCount;
			this.backgroundObjects = ((BackgroundObject[]) boList.toArray(new BackgroundObject[boList.size()]));
			for (int o = 0; o < this.backgroundObjects.length; o++)
				this.backgroundObjects[o].checkZoomAndPosition();
			return this.backgroundObjects;
		}
		private BackgroundObject[] backgroundObjects = null;
		private ImWord[] textStreamHeads = null;
		private ImWord[] textStreamTails = null;
		private long backgroundObjectDocModCount = 0;
		private long backgroundObjectHighlightModCount = 0;
		private long backgroundObjectRenderingDpiModCount = 0;
		
		private abstract class BackgroundObject {
			final Color color;
			int zDpi;
			BackgroundObject(Color color) {
				this.color = color;
			}
			abstract void checkZoomAndPosition();
			final short zoom(int i, int offset) {
				return ((short) ((((i * renderingDpi) + (pageImageDpi / 2)) / pageImageDpi) + offset));
			}
			abstract void paint(Graphics gr);
		}
		
		private abstract class BoxBasedBackgroundObject extends BackgroundObject {
			final short left;
			final short right;
			final short top;
			final short bottom;
			final boolean isOutline;
			short zLeft;
			short zRight;
			short zTop;
			short zBottom;
			BoxBasedBackgroundObject(int left, int right, int top, int bottom, Color color, boolean isOutline) {
				super(color);
				this.left = ((short) left);
				this.right = ((short) right);
				this.top = ((short) top);
				this.bottom = ((short) bottom);
				this.isOutline = isOutline;
			}
			void checkZoomAndPosition() {
				if (this.zDpi == renderingDpi)
					return;
				this.zLeft = this.zoom(this.left, (getLeftOffset() - (this.isOutline ? 1 : 0)));
				this.zRight = this.zoom(this.right, (getLeftOffset() - (this.isOutline ? 0 : 1)));
				this.zTop = this.zoom(this.top, (getTopOffset() - (this.isOutline ? 1 : 0)));
				this.zBottom = this.zoom(this.bottom, (getTopOffset() - (this.isOutline ? 0 : 1)));
				this.zDpi = renderingDpi;
			}
		}
		
		private class WordRectangle extends BoxBasedBackgroundObject {
			final byte flags;
			WordRectangle(int left, int right, int top, int bottom, Color color, byte flags) {
				super(left, right, top, bottom, color, false /* drag in right and bottom */);
				this.flags = flags;
			}
			void paint(Graphics gr) {
				gr.setColor(this.color);
				if ((this.flags & 0x02) != 0) {
					gr.drawLine(this.zLeft, this.zTop, this.zLeft, this.zBottom);
					if ((this.flags & 0x08) != 0) {
						
						//	thicken left word edge
						gr.drawLine((this.zLeft - 1), (this.zTop - 1), (this.zLeft - 1), this.zBottom);
						gr.drawLine((this.zLeft - 2), (this.zTop - 2), (this.zLeft - 2), this.zBottom);
						
						//	thicken top word edge
						gr.drawLine((this.zLeft - 1), (this.zTop - 1), this.zRight, (this.zTop - 1));
						gr.drawLine((this.zLeft - 2), (this.zTop - 2), this.zRight, (this.zTop - 2));
					}
				}
				if ((this.flags & 0x01) != 0) {
					gr.drawLine(this.zRight, this.zTop, this.zRight, this.zBottom);
					if ((this.flags & 0x04) != 0) {
						
						//	thicken right word edge
						gr.drawLine((this.zRight + 1), this.zTop, (this.zRight + 1), (this.zBottom + 1));
						gr.drawLine((this.zRight + 2), this.zTop, (this.zRight + 2), (this.zBottom + 2));
						
						//	thicken bottom word edge
						gr.drawLine(this.zLeft, (this.zBottom + 1), (this.zRight + 1), (this.zBottom + 1));
						gr.drawLine(this.zLeft, (this.zBottom + 2), (this.zRight + 2), (this.zBottom + 2));
					}
				}
				gr.drawLine(this.zLeft, this.zTop, this.zRight, this.zTop);
				gr.drawLine(this.zLeft, this.zBottom, this.zRight, this.zBottom);
			}
		}
		
		private class WordConnector extends BackgroundObject {
			final Point[] points;
			Point[] zPoints;
			WordConnector(Color color, Point[] points) {
				super(color);
				this.points = points;
			}
			void checkZoomAndPosition() {
				if (this.zDpi == renderingDpi)
					return;
				this.zPoints = new Point[this.points.length];
				for (int p = 0; p < this.points.length; p++)
					this.zPoints[p] = new Point(this.zoom(this.points[p].x, getLeftOffset()), this.zoom(this.points[p].y, getTopOffset()));
				this.zDpi = renderingDpi;
			}
			void paint(Graphics gr) {
				gr.setColor(this.color);
				for (int p = 1; p < this.zPoints.length; p++)
					gr.drawLine(this.zPoints[p-1].x, this.zPoints[p-1].y, this.zPoints[p].x, this.zPoints[p].y);
			}
		}
		
		private class RegionRectangle extends BoxBasedBackgroundObject {
			final int out;
			RegionRectangle(int left, int right, int top, int bottom, Color color, int out) {
				super(left, right, top, bottom, color, true /* push out left and top */);
				this.out = out;
			}
			void paint(Graphics gr) {
				gr.setColor(this.color);
				gr.drawLine((this.zLeft - this.out), (this.zTop - this.out), (this.zLeft - this.out), (this.zBottom + this.out));
				gr.drawLine((this.zRight + this.out), (this.zTop - this.out), (this.zRight + this.out), (this.zBottom + this.out));
				gr.drawLine((this.zLeft - this.out), (this.zTop - this.out), (this.zRight + this.out), (this.zTop - this.out));
				gr.drawLine((this.zLeft - this.out), (this.zBottom + this.out), (this.zRight + this.out), (this.zBottom + this.out));
			}
		}
		
		private class AnnotHighlight extends BoxBasedBackgroundObject {
			AnnotHighlight(int left, int right, int top, int bottom, Color color) {
				super(left, right, top, bottom, color, false /* drag in right and bottom */);
			}
			void paint(Graphics gr) {
				gr.setColor(this.color);
				gr.fillRect(this.zLeft, this.zTop, (this.zRight - this.zLeft), (this.zBottom - this.zTop));
			}
		}
		
		private class AnnotStart extends BoxBasedBackgroundObject {
			final int out;
			AnnotStart(int left, int right, int top, int bottom, Color color, int out) {
				super(left, right, top, bottom, color, false /* drag in right and bottom */);
				this.out = out;
			}
			void paint(Graphics gr) {
				gr.setColor(this.color);
				for (int t = 0; t < annotationHighlightMargin; t++)
					gr.drawLine((this.zLeft - (this.out * annotationHighlightMargin) + t), (this.zTop - this.out), (this.zLeft - (this.out * annotationHighlightMargin) + t), (this.zBottom + this.out));
				gr.drawLine((this.zLeft - (this.out * annotationHighlightMargin)), (this.zTop - this.out), (this.zLeft + annotationHighlightMargin), (this.zTop - this.out));
				gr.drawLine((this.zLeft - (this.out * annotationHighlightMargin)), (this.zBottom + this.out), (this.zLeft + annotationHighlightMargin), (this.zBottom + this.out));
			}
		}
		
		private class AnnotEnd extends BoxBasedBackgroundObject {
			final int out;
			AnnotEnd(int left, int right, int top, int bottom, Color color, int out) {
				super(left, right, top, bottom, color, false /* drag in right and bottom */);
				this.out = out;
			}
			void paint(Graphics gr) {
				gr.setColor(this.color);
				for (int t = 0; t < annotationHighlightMargin; t++)
					gr.drawLine((this.zRight + (this.out * annotationHighlightMargin) - t), (this.zTop - this.out), (this.zRight + (this.out * annotationHighlightMargin) - t), (this.zBottom + this.out));
				gr.drawLine((this.zRight + (this.out * annotationHighlightMargin)), (this.zTop - this.out), (this.zRight - annotationHighlightMargin), (this.zTop - this.out));
				gr.drawLine((this.zRight + (this.out * annotationHighlightMargin)), (this.zBottom + this.out), (this.zRight - annotationHighlightMargin), (this.zBottom + this.out));
			}
		}
		
		private DisplayExtensionGraphics[] getDisplayExtensionGraphics() {
			if (this.displayExtensionGraphics == null) {
				this.displayExtensionGraphicsDocModCount = this.docModCount;
				this.displayExtensionGraphicsModCount = displayExtensionModCount;
				this.displayExtensionGraphicsHighlightModCount = highlightModCount;
				this.displayExtensionGraphics = ImDocumentMarkupPanel.this.getDisplayExtensionGraphics(this.page);
			}
			return this.displayExtensionGraphics;
		}
		private DisplayExtensionGraphics[] displayExtensionGraphics = null;
		private long displayExtensionGraphicsDocModCount = 0;
		private long displayExtensionGraphicsHighlightModCount = 0;
		private long displayExtensionGraphicsModCount = 0;
		
		/* (non-Javadoc)
		 * @see java.awt.Container#validate()
		 */
		public void validate() {
			if ((this.backgroundObjectDocModCount != this.docModCount) || (this.backgroundObjectHighlightModCount != highlightModCount)) {
				this.backgroundObjects = null;
				this.textStreamHeads = null;
				this.textStreamTails = null;
			}
			if ((this.displayExtensionGraphicsDocModCount != this.docModCount) || (this.displayExtensionGraphicsModCount != displayExtensionModCount) || (this.displayExtensionGraphicsHighlightModCount != highlightModCount))
				this.displayExtensionGraphics = null;
			if ((this.textStringImageDocModCount != this.docModCount) || (this.textStringImageTextStringPercentageModCount != textStringPercentageModCount) || (this.textStringImageRenderingDpiModCount != renderingDpiModCount))
				this.textStringImages = null;
			if (this.overlay != null)
				this.overlay.validate(); // need to do this explicitly TODO WHY ???
			super.validate();
		}
		
		/* (non-Javadoc)
		 * @see javax.swing.JComponent#paint(java.awt.Graphics)
		 */
		public void paint(Graphics graphics) {
			super.paint(graphics);
			
			//	paint background
			Color preBackgroundColor = graphics.getColor();
			graphics.setColor(Color.white);
			graphics.fillRect(0, 0, this.getWidth(), this.getHeight());
			graphics.setColor(preBackgroundColor);
			
			//	add border above background
			this.paintBorder(graphics);
			
			//	get page image and compute zoom
			float zoom = (((float) renderingDpi) / this.pageImageDpi);
			
			//	get display extension graphics
			DisplayExtensionGraphics[] degs = this.getDisplayExtensionGraphics();
			
			//	draw below-image outlines and fillings of text extension objects
			if (graphics instanceof Graphics2D)
				this.paintDisplayExtensionGraphics(((Graphics2D) graphics), zoom, degs, DisplayExtensionGraphics.ORDER_BEFORE_PAGE_IMAGE);
			
			//	paint main page image underneath markup highlights to keep latter showing for text on non-white background
			BufferedImage spi = this.getScaledPageImage();
			graphics.drawImage(spi, this.getLeftOffset(), this.getTopOffset(), spi.getWidth(), spi.getHeight(), this);
			
			//	highlight word selection (if any)
			Color preSelectionColor = graphics.getColor();
			if (selectionStartWord != null) {
				ImWord sew = ((selectionEndWord == null) ? selectionStartWord : selectionEndWord);
				graphics.setColor(selectionHighlightColor);
				if (selectionStartWord.getTextStreamId().equals(sew.getTextStreamId())) {
//					ImWord fw;
//					ImWord lw;
//					if (selectionStartWord.pageId == sew.pageId) {
//						fw = ((selectionStartWord.getTextStreamPos() <= sew.getTextStreamPos()) ? selectionStartWord : sew);
//						lw = ((selectionStartWord.getTextStreamPos() <= sew.getTextStreamPos()) ? sew : selectionStartWord);
//					}
//					else if (selectionStartWord.pageId < sew.pageId) {
//						fw = selectionStartWord;
//						lw = sew;
//					}
//					else {
//						fw = sew;
//						lw = selectionStartWord;
//					}
					ImWord fw = ((selectionStartWord.getTextStreamPos() <= sew.getTextStreamPos()) ? selectionStartWord : sew);
					ImWord lw = ((selectionStartWord.getTextStreamPos() <= sew.getTextStreamPos()) ? sew : selectionStartWord);
					for (ImWord imw = fw; imw != null; imw = ((imw == lw) ? null : imw.getNextWord())) {
						if (imw.pageId < this.page.pageId)
							continue;
						if (imw.pageId != this.page.pageId)
							break;
						BoundingBox imwHighlight;
						if ((imw == fw) || (imw.getPreviousWord() == null) || (imw.getPreviousWord().bounds.right > imw.bounds.left) || (imw.getPreviousWord().bounds.top > imw.bounds.bottom) || (imw.getPreviousWord().bounds.bottom < imw.bounds.top))
							imwHighlight = new BoundingBox(imw.bounds.left - annotationHighlightMargin, imw.bounds.right + annotationHighlightMargin, imw.bounds.top - annotationHighlightMargin, imw.bounds.bottom + annotationHighlightMargin);
						else imwHighlight = new BoundingBox(imw.getPreviousWord().bounds.right + annotationHighlightMargin, imw.bounds.right + annotationHighlightMargin, imw.bounds.top - annotationHighlightMargin, imw.bounds.bottom + annotationHighlightMargin);
						this.fillBox(graphics, imwHighlight, zoom, this.getLeftOffset(), this.getTopOffset());
					}
				}
				else {
					if (selectionStartWord.pageId == this.page.pageId) {
						BoundingBox imwHighlight = new BoundingBox(selectionStartWord.bounds.left - annotationHighlightMargin, selectionStartWord.bounds.right + annotationHighlightMargin, selectionStartWord.bounds.top - annotationHighlightMargin, selectionStartWord.bounds.bottom + annotationHighlightMargin);
						this.fillBox(graphics, imwHighlight, zoom, this.getLeftOffset(), this.getTopOffset());
					}
					if (sew.pageId == this.page.pageId) {
						BoundingBox imwHighlight = new BoundingBox(sew.bounds.left - annotationHighlightMargin, sew.bounds.right + annotationHighlightMargin, sew.bounds.top - annotationHighlightMargin, sew.bounds.bottom + annotationHighlightMargin);
						this.fillBox(graphics, imwHighlight, zoom, this.getLeftOffset(), this.getTopOffset());
					}
				}
			}
//			if ((pendingTwoClickAction != null) && (pendingTwoClickAction.getFirstWord().pageId == this.page.pageId)) {
//				ImWord tcaStartWord = pendingTwoClickAction.getFirstWord();
//				graphics.setColor(new Color((255 - ((255 - selectionHighlightColor.getRed()) / 2)), (255 - ((255 - selectionHighlightColor.getGreen()) / 2)), (255 - ((255 - selectionHighlightColor.getBlue()) / 2)))); // cut distance to white in half for each component color
//				BoundingBox imwHighlight = new BoundingBox(tcaStartWord.bounds.left - annotationHighlightMargin, tcaStartWord.bounds.right + annotationHighlightMargin, tcaStartWord.bounds.top - annotationHighlightMargin, tcaStartWord.bounds.bottom + annotationHighlightMargin);
//				this.fillBox(graphics, imwHighlight, zoom, this.getLeftOffset(), this.getTopOffset());
//			}
			if ((pendingTwoClickAction != null) && (pendingTwoClickAction.getFirstRegion().pageId == this.page.pageId)) {
				ImRegion tcaStartRegion = pendingTwoClickAction.getFirstRegion();
				graphics.setColor(new Color((255 - ((255 - selectionHighlightColor.getRed()) / 2)), (255 - ((255 - selectionHighlightColor.getGreen()) / 2)), (255 - ((255 - selectionHighlightColor.getBlue()) / 2)))); // cut distance to white in half for each component color
				BoundingBox tcaStartHighlight = new BoundingBox(tcaStartRegion.bounds.left - annotationHighlightMargin, tcaStartRegion.bounds.right + annotationHighlightMargin, tcaStartRegion.bounds.top - annotationHighlightMargin, tcaStartRegion.bounds.bottom + annotationHighlightMargin);
				this.fillBox(graphics, tcaStartHighlight, zoom, this.getLeftOffset(), this.getTopOffset());
			}
			graphics.setColor(preSelectionColor);
			
			//	draw below-object outlines and fillings of text extension objects
			if (graphics instanceof Graphics2D) {
//				AffineTransform preDegTransform = ((Graphics2D) graphics).getTransform();
//				((Graphics2D) graphics).translate(this.getLeftOffset(), this.getTopOffset());
//				((Graphics2D) graphics).scale(zoom, zoom);
//				for (int g = 0; g < degs.length; g++) {
//					if (!degs[g].isActive())
//						continue;
//					if (!degs[g].fillOverText())
//						degs[g].fill((Graphics2D) graphics);
//					if (!degs[g].outlineOverText())
//						degs[g].outline((Graphics2D) graphics);
//				}
//				((Graphics2D) graphics).setTransform(preDegTransform);
				this.paintDisplayExtensionGraphics(((Graphics2D) graphics), zoom, degs, DisplayExtensionGraphics.ORDER_BEFORE_PAGE_OBJECTS);
			}
			
			//	paint background / highlight objects
			BackgroundObject[] bos = this.getBackgroundObjects();
			for (int o = 0; o < bos.length; o++)
				bos[o].paint(graphics);
			
			//	connect text stream heads to predecessors (text stream heads and tails are present after getting background objects)
			if (areTextStreamsPainted()) {
				for (int h = 0; h < this.textStreamHeads.length; h++) {
					if (this.textStreamHeads[h].getPreviousWord() != null)
						transPageWordConnections.add(new TransPageWordConnection(this.textStreamHeads[h].getPreviousWord(), this.textStreamHeads[h]));
				}
				for (int t = 0; t < this.textStreamTails.length; t++) {
					if (this.textStreamTails[t].getNextWord() != null)
						transPageWordConnections.add(new TransPageWordConnection(this.textStreamTails[t], this.textStreamTails[t].getNextWord()));
				}
			}
			
			//	draw below-text outlines and fillings of text extension objects
			if (graphics instanceof Graphics2D)
				this.paintDisplayExtensionGraphics(((Graphics2D) graphics), zoom, degs, DisplayExtensionGraphics.ORDER_BEFORE_PAGE_TEXT);
			
			//	draw text strings on top of markup if activated
			if (areTextStringsPainted()) {
				TextStringImage[] tsis = this.getTextStringImagesOCR();
				for (int i = 0; i < tsis.length; i++)
					graphics.drawImage(tsis[i], (this.getLeftOffset() + tsis[i].left), (this.getTopOffset() + tsis[i].top), tsis[i].getWidth(), tsis[i].getHeight(), this);
			}
			
			//	draw text strings from page image on top of markup
			else if (documentBornDigital) {
				TextStringImage[] tsis = this.getTextStringImagesBD(spi);
				for (int i = 0; i < tsis.length; i++)
					graphics.drawImage(tsis[i], (this.getLeftOffset() + tsis[i].left), (this.getTopOffset() + tsis[i].top), tsis[i].getWidth(), tsis[i].getHeight(), this);
			}
			
			//	draw above-text outlines and fillings of text extension objects
			if (graphics instanceof Graphics2D) {
//				AffineTransform preDegTransform = ((Graphics2D) graphics).getTransform();
//				((Graphics2D) graphics).translate(this.getLeftOffset(), this.getTopOffset());
//				((Graphics2D) graphics).scale(zoom, zoom);
//				for (int g = 0; g < degs.length; g++) {
//					if (!degs[g].isActive())
//						continue;
//					if (degs[g].fillOverText())
//						degs[g].fill((Graphics2D) graphics);
//					if (degs[g].outlineOverText())
//						degs[g].outline((Graphics2D) graphics);
//				}
//				((Graphics2D) graphics).setTransform(preDegTransform);
				this.paintDisplayExtensionGraphics(((Graphics2D) graphics), zoom, degs, DisplayExtensionGraphics.ORDER_AFTER_PAGE_TEXT);
			}
			
			
			//	draw box selection on top of everything (if any)
			if ((pointSelectionPage == this) && (selectionStartPoint != null) && (selectionEndPoint != null)) {
				preSelectionColor = graphics.getColor();
				graphics.setColor(selectionBoxColor);
				BoundingBox selection;
				if (graphics instanceof Graphics2D) {
					Stroke preSelectionStroke = ((Graphics2D) graphics).getStroke();
					((Graphics2D) graphics).setStroke(selectionBoxStroke);
					selection = new BoundingBox(
							Math.min(selectionStartPoint.x, selectionEndPoint.x),
							Math.max(selectionStartPoint.x, selectionEndPoint.x),
							Math.min(selectionStartPoint.y, selectionEndPoint.y),
							Math.max(selectionStartPoint.y, selectionEndPoint.y)
						);
					if ((selection.left < selection.right) && (selection.top < selection.bottom))
						this.paintBox(graphics, selection, zoom, this.getLeftOffset(), this.getTopOffset());
					((Graphics2D) graphics).setStroke(preSelectionStroke);
				}
				else for (int i = 0; i < selectionBoxThickness; i++) {
					selection = new BoundingBox(
							Math.min(selectionStartPoint.x, selectionEndPoint.x)+i,
							Math.max(selectionStartPoint.x, selectionEndPoint.x)-i,
							Math.min(selectionStartPoint.y, selectionEndPoint.y)+i,
							Math.max(selectionStartPoint.y, selectionEndPoint.y)-i
						);
					if ((selection.left < selection.right) && (selection.top < selection.bottom))
						this.paintBox(graphics, selection, zoom, this.getLeftOffset(), this.getTopOffset());
					else break;
				}
				graphics.setColor(preSelectionColor);
			}
			
			//	re-draw any overlay (our only child)
			if (this.overlay != null)
				this.paintChildren(this.getComponentGraphics(graphics).create());
		}
		private void paintBox(Graphics graphics, BoundingBox box, float zoom, int leftOffset, int topOffset) {
			graphics.drawRect((leftOffset + Math.round(zoom * box.left)), (topOffset + Math.round(zoom * box.top)), Math.round(zoom * (box.right - box.left - 1)), Math.round(zoom * (box.bottom - box.top - 1)));
		}
//		private void paintBox(Graphics graphics, BoundingBox box, float zoom, int leftOffset, int topOffset) {
//			this.paintBox(graphics, box, zoom, leftOffset, topOffset, true, true);
//		}
//		private void paintBox(Graphics graphics, BoundingBox box, float zoom, int leftOffset, int topOffset, boolean paintLeft, boolean paintRight) {
//			if (paintLeft && paintRight)
//				graphics.drawRect((leftOffset + Math.round(zoom * box.left)), (topOffset + Math.round(zoom * box.top)), Math.round(zoom * (box.right - box.left - 1)), Math.round(zoom * (box.bottom - box.top - 1)));
//			else {
//				graphics.drawLine((leftOffset + Math.round(zoom * box.left)), (topOffset + Math.round(zoom * box.top)), (leftOffset + Math.round(zoom * (box.right-1))), (topOffset + Math.round(zoom * box.top)));
//				graphics.drawLine((leftOffset + Math.round(zoom * box.left)), (topOffset + Math.round(zoom * (box.bottom-1))), (leftOffset + Math.round(zoom * (box.right-1))), (topOffset + Math.round(zoom * (box.bottom-1))));
//				if (paintLeft)
//					graphics.drawLine((leftOffset + Math.round(zoom * box.left)), (topOffset + Math.round(zoom * box.top)), (leftOffset + Math.round(zoom * box.left)), (topOffset + Math.round(zoom * (box.bottom-1))));
//				if (paintRight)
//					graphics.drawLine((leftOffset + Math.round(zoom * (box.right-1))), (topOffset + Math.round(zoom * box.top)), (leftOffset + Math.round(zoom * (box.right-1))), (topOffset + Math.round(zoom * (box.bottom-1))));
//			}
//		}
		private void fillBox(Graphics graphics, BoundingBox box, float zoom, int leftOffset, int topOffset) {
			graphics.fillRect((leftOffset + Math.round(zoom * box.left)), (topOffset + Math.round(zoom * box.top)), Math.round((zoom * (box.right - box.left)) - 1), Math.round((zoom * (box.bottom - box.top)) - 1));
		}
		private void paintDisplayExtensionGraphics(Graphics2D graphics, float zoom, DisplayExtensionGraphics[] degs, byte dueOrderPosition) {
			
			//	draw below-text outlines and fillings of text extension objects
			AffineTransform preDegTransform = graphics.getTransform();
			graphics.translate(this.getLeftOffset(), this.getTopOffset());
			graphics.scale(zoom, zoom);
			for (int g = 0; g < degs.length; g++) {
				if (!degs[g].isActive())
					continue;
				if (degs[g].getFillOrderPosition() == dueOrderPosition)
					degs[g].fill(graphics);
				if (degs[g].getOutlineOrderPosition() == dueOrderPosition)
					degs[g].outline(graphics);
			}
			graphics.setTransform(preDegTransform);
		}
		
		private Point[] getConnectorLineSequence(BoundingBox from, BoundingBox to) {
			LinkedList cls = new LinkedList();
			
			//	rightward connection
			if (from.right <= to.left) {
				
				//	boxes adjacent in reading order, use simple horizontal line
				if ((from.top < to.bottom) && (from.bottom > to.top)) {
					cls.add(new Point(from.right, ((from.top + from.bottom + to.top + to.bottom) / 4)));
					cls.add(new Point(to.left, ((from.top + from.bottom + to.top + to.bottom) / 4)));
				}
				
				//	successor to upper right of predecessor (column break)
				else if (to.bottom <= from.top) {
					ImRegion[] fromRegions = this.page.getRegionsIncluding(from, false);
					ImRegion fromRegion = null;
					for (int r = 0; r < fromRegions.length; r++) {
						if (ImRegion.REGION_ANNOTATION_TYPE.equals(fromRegions[r].getType()))
							continue;
						if (!fromRegions[r].bounds.includes(to, false)) {
							fromRegion = fromRegions[r];
							break;
						}
					}
					int fromRight = ((fromRegion == null) ? from.right : fromRegion.bounds.right);
					ImRegion[] toRegions = this.page.getRegionsIncluding(to, false);
					ImRegion toRegion = null;
					for (int r = 0; r < toRegions.length; r++) {
						if (ImRegion.REGION_ANNOTATION_TYPE.equals(toRegions[r].getType()))
							continue;
						if (!toRegions[r].bounds.includes(from, false)) {
							toRegion = toRegions[r];
							break;
						}
					}
					int toLeft = ((toRegion == null) ? to.left : toRegion.bounds.left);
					cls.add(new Point(from.right, ((from.top + from.bottom) / 2)));
					cls.add(new Point(((fromRight + toLeft) / 2), ((from.top + from.bottom) / 2)));
					cls.add(new Point(((fromRight + toLeft) / 2), ((to.top + to.bottom) / 2)));
					cls.add(new Point(to.left, ((to.top + to.bottom) / 2)));
				}
				
				//	successor to lower right of predecessor (block break)
				else {
					cls.add(new Point(from.right, ((from.top + from.bottom) / 2)));
					cls.add(new Point(((from.right + to.left) / 2), ((from.top + from.bottom) / 2)));
					cls.add(new Point(((from.right + to.left) / 2), ((to.top + to.bottom) / 2)));
					cls.add(new Point(to.left, ((to.top + to.bottom) / 2)));
				}
			}
			
			//	successor to left of predecessor, can only be lower (line break)
			else if ((from.right > to.left) && (from.bottom <= to.top)) {
				int outswing = Math.min(((to.top - from.bottom) / 2), (renderingDpi / 6));
				cls.add(new Point(from.right, ((from.top + from.bottom) / 2)));
				cls.add(new Point((from.right + outswing), ((from.top + from.bottom) / 2)));
				cls.add(new Point((from.right + outswing), ((from.bottom + to.top) / 2)));
				cls.add(new Point((to.left - outswing), ((from.bottom + to.top) / 2)));
				cls.add(new Point((to.left - outswing), ((to.top + to.bottom) / 2)));
				cls.add(new Point(to.left, ((to.top + to.bottom) / 2)));
			}
			
			//	finally ...
			return ((Point[]) cls.toArray(new Point[cls.size()]));
		}
	}
	
	/**
	 * Open a dialog allowing to configure the display.
	 */
	public void showControlPanel() {
		final JDialog d = DialogFactory.produceDialog("Configure Display", true);
		d.getContentPane().setLayout(new BorderLayout());
		
		JButton c = new JButton("Close");
		c.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				d.dispose();
			}
		});
		
		d.getContentPane().add(this.getControlPanel(), BorderLayout.CENTER);
		d.getContentPane().add(c, BorderLayout.SOUTH);
		d.setSize(300, 600);
		d.setLocationRelativeTo(this);
		d.setVisible(true);
	}
	
	/**
	 * Refresh any display control panel retrieved from the
	 * <code>getControlPanel()</code> method. Client code that starts an atomic
	 * action before modifying the document should call this method after the
	 * atomic action has been finished.
	 */
	public void validateControlPanel() {
		if (this.idvc == null)
			return;
		if (this.validIdvcDocModCount == this.idvcDocModCount)
			return;
		this.idvc.updateControls();
		this.validIdvcDocModCount = this.idvcDocModCount;
	}
	
	/**
	 * Retrieve a control panel allowing to configure the display.
	 * @return the control panel
	 */
	public ImDocumentViewControl getControlPanel() {
		if (this.idvc == null)
			this.idvc = new ImDocumentViewControl(this);
		else this.idvc.updateControls();
		return this.idvc;
	}
	private ImDocumentViewControl idvc = null;
	private boolean immediatelyUpdateIdvc = true;
	private int idvcDocModCount = 0;
	private int validIdvcDocModCount = 0;
	
	/**
	 * Configuration widget for image viewer panel.
	 * 
	 * @author sautter
	 */
	public class ImDocumentViewControl extends JPanel {
		final ImDocumentMarkupPanel idmp;
		private JPanel controlPanel = new JPanel(new GridBagLayout(), true);
		private JScrollPane controlPanelBox;
		final WordControl wordControl;
		private JLabel regionLabel = new JLabel("Regions, Blocks, etc.", JLabel.CENTER);
		private JButton showRegionsButton = new JButton("Show All");
		private JButton hideRegionsButton = new JButton("Hide All");
		private JPanel regionButtons = new JPanel(new GridLayout(1, 2), true);
		final TreeMap regionControls = new TreeMap(String.CASE_INSENSITIVE_ORDER);
		private JLabel annotLabel = new JLabel("Annotations", JLabel.CENTER);
		private JButton showAnnotsButton = new JButton("Show All");
		private JButton hideAnnotsButton = new JButton("Hide All");
		private JPanel annotButtons = new JPanel(new GridLayout(1, 2), true);
		final TreeMap annotControls = new TreeMap(String.CASE_INSENSITIVE_ORDER);
		
		ImDocumentViewControl(ImDocumentMarkupPanel idmp) {
			super(new BorderLayout(), true);
			this.idmp = idmp;
			this.add(new JLabel("Display Control", JLabel.CENTER), BorderLayout.NORTH);
			this.controlPanelBox = new JScrollPane(this.controlPanel);
			this.controlPanelBox.getVerticalScrollBar().setUnitIncrement(33);
			this.controlPanelBox.getVerticalScrollBar().setBlockIncrement(100);
			this.controlPanelBox.addComponentListener(new ComponentAdapter() {
				public void componentResized(ComponentEvent ce) {
					adjustScrollBar();
				}
			});
			this.add(this.controlPanelBox, BorderLayout.CENTER);
			this.wordControl = new WordControl();
			
			this.showRegionsButton.setBorder(BorderFactory.createEtchedBorder());
			this.showRegionsButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent ae) {
					boolean allRegionTypesVisible = true;
					for (Iterator cit = regionControls.keySet().iterator(); cit.hasNext();)
						if (!paintedLayoutObjectTypes.contains(cit.next())) {
							allRegionTypesVisible = false;
							break;
						}
					if (allRegionTypesVisible)
						return;
					for (Iterator cit = regionControls.keySet().iterator(); cit.hasNext();) {
						RegionControl rc = ((RegionControl) regionControls.get(cit.next()));
						if (cit.hasNext()) {
							paintedLayoutObjectTypes.add(rc.type); // prevent selecting checkbox from triggering update avalanche
							rc.paint.setSelected(true); // select checkbox
						}
						else {
							paintedLayoutObjectTypes.remove(rc.type); // make sure setting last type painted triggers update avalanche
							setRegionsPainted(rc.type, true, true); // trigger update avalanche
						}
					}
				}
			});
			this.hideRegionsButton.setBorder(BorderFactory.createEtchedBorder());
			this.hideRegionsButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent ae) {
					boolean allRegionTypesHidden = true;
					for (Iterator cit = regionControls.keySet().iterator(); cit.hasNext();)
						if (paintedLayoutObjectTypes.contains(cit.next())) {
							allRegionTypesHidden = false;
							break;
						}
					if (allRegionTypesHidden)
						return;
					for (Iterator cit = regionControls.keySet().iterator(); cit.hasNext();) {
						RegionControl rc = ((RegionControl) regionControls.get(cit.next()));
						if (cit.hasNext()) {
							paintedLayoutObjectTypes.remove(rc.type); // prevent un-selecting checkbox from triggering update avalanche
							rc.paint.setSelected(false); // un-select checkbox
						}
						else {
							paintedLayoutObjectTypes.add(rc.type); // make sure setting last type non-painted triggers update avalanche
							setRegionsPainted(rc.type, false, true); // trigger update avalanche
						}
					}
				}
			});
			this.regionButtons.add(this.showRegionsButton);
			this.regionButtons.add(this.hideRegionsButton);
			this.showAnnotsButton.setBorder(BorderFactory.createEtchedBorder());
			this.showAnnotsButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent ae) {
					boolean allAnnotTypesVisible = true;
					for (Iterator cit = annotControls.keySet().iterator(); cit.hasNext();)
						if (!paintedAnnotationTypes.contains(cit.next())) {
							allAnnotTypesVisible = false;
							break;
						}
					if (allAnnotTypesVisible)
						return;
					for (Iterator cit = annotControls.keySet().iterator(); cit.hasNext();) {
						AnnotControl ac = ((AnnotControl) annotControls.get(cit.next()));
						if (cit.hasNext()) {
							paintedAnnotationTypes.add(ac.type); // prevent selecting checkbox from triggering update avalanche
							ac.paint.setSelected(true); // select checkbox
						}
						else {
							paintedAnnotationTypes.remove(ac.type); // make sure setting last type painted triggers update avalanche
							setAnnotationsPainted(ac.type, true, true); // trigger update avalanche
						}
					}
				}
			});
			this.hideAnnotsButton.setBorder(BorderFactory.createEtchedBorder());
			this.hideAnnotsButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent ae) {
					boolean allAnnotTypesHidden = true;
					for (Iterator cit = annotControls.keySet().iterator(); cit.hasNext();)
						if (paintedAnnotationTypes.contains(cit.next())) {
							allAnnotTypesHidden = false;
							break;
						}
					if (allAnnotTypesHidden)
						return;
					for (Iterator cit = annotControls.keySet().iterator(); cit.hasNext();) {
						AnnotControl ac = ((AnnotControl) annotControls.get(cit.next()));
						if (cit.hasNext()) {
							paintedAnnotationTypes.remove(ac.type); // prevent un-selecting checkbox from triggering update avalanche
							ac.paint.setSelected(false); // un-select checkbox
						}
						else {
							paintedAnnotationTypes.add(ac.type); // make sure setting last type non-painted triggers update avalanche
							setAnnotationsPainted(ac.type, false, true); // trigger update avalanche
						}
					}
				}
			});
			this.annotButtons.add(this.showAnnotsButton);
			this.annotButtons.add(this.hideAnnotsButton);
			
			this.updateControls();
		}
		
		synchronized void updateControls() {
			HashMap tempControls = new HashMap();
			
			tempControls.clear();
			tempControls.putAll(this.regionControls);
			this.regionControls.clear();
			TreeSet regionTypes = new TreeSet();
			for (int p = 0; p < pagePanels.length; p++) {
				if (isPageVisible(p) && (pagePanels[p] != null))
					regionTypes.addAll(Arrays.asList(pagePanels[p].page.getRegionTypes()));
			}
			for (Iterator rtit = regionTypes.iterator(); rtit.hasNext();) {
				String rt = ((String) rtit.next());
				RegionControl rc = ((RegionControl) tempControls.get(rt));
				if (rc == null)
					rc = new RegionControl(rt);
				this.regionControls.put(rc.type, rc);
			}
			
			tempControls.clear();
			tempControls.putAll(this.annotControls);
			this.annotControls.clear();
			String[] annotTypes = this.idmp.document.getAnnotationTypes();
			for (int a = 0; a < annotTypes.length; a++) {
				AnnotControl ac = ((AnnotControl) tempControls.get(annotTypes[a]));
				if (ac == null)
					ac = new AnnotControl(annotTypes[a]);
				this.annotControls.put(ac.type, ac);
			}
			
			this.layoutControls();
		}
		
		void layoutControls() {
			this.controlPanel.removeAll();
			
			GridBagConstraints gbc = new GridBagConstraints();
			gbc.gridheight = 1;
			gbc.weighty = 0;
			gbc.gridy = 0;
			gbc.insets.left = 4;
			gbc.insets.right = 4;
			gbc.insets.top = 2;
			gbc.insets.bottom = 2;
			gbc.fill = GridBagConstraints.BOTH;
			
			gbc.gridwidth = 1;
			gbc.gridx = 0;
			gbc.weightx = 0;
			this.controlPanel.add(this.wordControl.textStreamsPainted, gbc.clone());
			gbc.gridx = 1;
			gbc.weightx = 1;
			this.controlPanel.add(this.wordControl.label, gbc.clone());
			gbc.gridy++;
			
//			if (!idmp.documentBornDigital) {
			boolean showTextStringPercentag = ((this.idmp.documentTextOrigin == TEXT_ORIGIN_OCR) || (this.idmp.documentTextOrigin == TEXT_ORIGIN_DIGITIZED_OCR));
			if (showTextStringPercentag) {
				gbc.gridwidth = 2;
				gbc.gridx = 0;
				gbc.weightx = 1;
				this.controlPanel.add(this.wordControl.textStringPercentage, gbc.clone());
				gbc.gridy++;
			}
			
			gbc.gridwidth = 2;
			gbc.gridx = 0;
			gbc.weightx = 1;
			this.controlPanel.add(this.regionLabel, gbc.clone());
			gbc.gridy++;
			this.showRegionsButton.setEnabled(this.regionControls.size() != 0);
			this.hideRegionsButton.setEnabled(this.regionControls.size() != 0);
			this.controlPanel.add(this.regionButtons, gbc.clone());
			gbc.gridy++;
			for (Iterator cit = this.regionControls.keySet().iterator(); cit.hasNext();) {
				RegionControl rc = ((RegionControl) this.regionControls.get(cit.next()));
				gbc.gridwidth = 1;
				gbc.gridx = 0;
				gbc.weightx = 0;
				this.controlPanel.add(rc.paint, gbc.clone());
				gbc.gridx = 1;
				gbc.weightx = 1;
				this.controlPanel.add(rc.label, gbc.clone());
				gbc.gridy++;
			}
			
			gbc.gridwidth = 2;
			gbc.gridx = 0;
			gbc.weightx = 1;
			this.controlPanel.add(this.annotLabel, gbc.clone());
			gbc.gridy++;
			this.showAnnotsButton.setEnabled(this.annotControls.size() != 0);
			this.hideAnnotsButton.setEnabled(this.annotControls.size() != 0);
			this.controlPanel.add(this.annotButtons, gbc.clone());
			gbc.gridy++;
			for (Iterator cit = this.annotControls.keySet().iterator(); cit.hasNext();) {
				AnnotControl ac = ((AnnotControl) this.annotControls.get(cit.next()));
				gbc.gridwidth = 1;
				gbc.gridx = 0;
				gbc.weightx = 0;
				this.controlPanel.add(ac.paint, gbc.clone());
				gbc.gridx = 1;
				gbc.weightx = 1;
				this.controlPanel.add(ac.label, gbc.clone());
				gbc.gridy++;
			}
			
			gbc.gridwidth = 2;
			gbc.gridx = 0;
			gbc.weightx = 1;
			gbc.weighty = 1;
			this.controlPanel.add(new JPanel(), gbc.clone());
			
			this.adjustScrollBar();
			
			this.validate();
			this.repaint();
		}
		
		void adjustScrollBar() {
			Dimension cpSize = this.controlPanelBox.getViewport().getView().getSize();
			Dimension cpViewSize = this.controlPanelBox.getViewport().getExtentSize();
			if (cpSize.height <= cpViewSize.height)
				this.controlPanelBox.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
			else {
				this.controlPanelBox.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
				this.controlPanelBox.getVerticalScrollBar().setUnitIncrement(cpViewSize.height / 10);
				this.controlPanelBox.getVerticalScrollBar().setBlockIncrement(cpViewSize.height / 3);
			}
		}
		
		abstract class TypeControl {
			String type;
			Color color;
			JCheckBox paint = new JCheckBox();
			JButton label = new JButton() {
				public void paint(Graphics gr) {
					Color preColor = gr.getColor();
					
					//	paint background (need to be non-opaque and then draw background ourselves to facilitate mixing colors)
					Dimension size = this.getSize();
					gr.setColor(Color.WHITE);
					gr.fillRect(0, 0, size.width, size.height);
					gr.setColor(this.getBackground());
					gr.fillRect(0, 0, size.width, size.height);
					
					//	paint text
					gr.setColor(preColor);
					super.paint(gr);
				}
				public void setBackground(Color bg) {
//					super.setBackground(new Color(bg.getRed(), bg.getGreen(), bg.getBlue(), 0x40));
					super.setBackground(new Color(bg.getRed(), bg.getGreen(), bg.getBlue(), idmp.getAnnotationHighlightAlpha()));
				}
			};
			TypeControl(String type, boolean paint, Color color) {
				this.type = type;
				this.color = color;
				this.label.setText(type);
//				this.label.setOpaque(true);
//				this.label.setBorder(BorderFactory.createLineBorder(this.label.getBackground(), 2));
				this.label.setBorder(BorderFactory.createLineBorder(this.color, 2));
				this.label.setBackground(this.color);
				this.label.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent ae) {
						Color color = JColorChooser.showDialog(TypeControl.this.label, TypeControl.this.type, TypeControl.this.color);
						if (color != null) {
							TypeControl.this.color = color;
							TypeControl.this.label.setBackground(color);
							TypeControl.this.label.setBorder(BorderFactory.createLineBorder(color, 2));
							TypeControl.this.colorChanged(color);
						}
					}
				});
				this.paint.setSelected(paint);
				this.paint.setHorizontalAlignment(JCheckBox.CENTER);
				this.paint.addItemListener(new ItemListener() {
					public void itemStateChanged(ItemEvent ie) {
						TypeControl.this.paintChanged(TypeControl.this.paint.isSelected());
					}
				});
			}
			void setColor(Color color) {
				this.color = color;
				this.label.setBackground(color);
				this.label.setBorder(BorderFactory.createLineBorder(this.color, 2));
			}
			abstract void paintChanged(boolean paint);
			abstract void colorChanged(Color color);
		}
		class WordControl {
			JCheckBox textStreamsPainted = new JCheckBox();
			JButton label = new JButton() {
				public void paint(Graphics gr) {
					Color preColor = gr.getColor();
					
					//	paint background (need to be non-opaque and then draw background ourselves to facilitate mixing colors)
					Dimension size = this.getSize();
					gr.setColor(Color.WHITE);
					gr.fillRect(0, 0, size.width, size.height);
					gr.setColor(this.getBackground());
					gr.fillRect(0, 0, size.width, size.height);
					
					//	paint text
					gr.setColor(preColor);
					super.paint(gr);
				}
				public void setBackground(Color bg) {
//					super.setBackground(new Color(bg.getRed(), bg.getGreen(), bg.getBlue(), 0x40));
					super.setBackground(new Color(bg.getRed(), bg.getGreen(), bg.getBlue(), idmp.getAnnotationHighlightAlpha()));
				}
			};
			JSlider textStringPercentage = new JSlider(0, 100);
			WordControl() {
				this.label.setText(WORD_ANNOTATION_TYPE);
//				this.label.setOpaque(true);
//				this.label.setBorder(BorderFactory.createLineBorder(this.label.getBackground(), 2));
//				this.label.setBackground(idmp.getLayoutObjectColor(WORD_ANNOTATION_TYPE, true));
				Color wordColor = idmp.getLayoutObjectColor(WORD_ANNOTATION_TYPE, true);
				this.label.setBackground(wordColor);
				this.label.setBorder(BorderFactory.createLineBorder(wordColor, 2));
				this.label.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent ae) {
						editColors();
					}
				});
				this.label.setToolTipText("Edit colors of words and text streams");
				
				this.textStreamsPainted.setSelected(idmp.areTextStreamsPainted());
				this.textStreamsPainted.setHorizontalAlignment(JCheckBox.CENTER);
				this.textStreamsPainted.addItemListener(new ItemListener() {
					public void itemStateChanged(ItemEvent ie) {
						idmp.setTextStreamsPainted(textStreamsPainted.isSelected(), false);
					}
				});
				this.textStreamsPainted.setToolTipText("Display logical text streams?");
				
				this.textStringPercentage.setMajorTickSpacing(10);
				this.textStringPercentage.setMinorTickSpacing(10);
				this.textStringPercentage.setPaintTicks(true);
				this.textStringPercentage.setSnapToTicks(true);
				this.textStringPercentage.setPreferredSize(new Dimension(100, this.textStringPercentage.getPreferredSize().height));
				this.textStringPercentage.setToolTipText("Percentage weight of OCR text on top of page image");
				this.textStringPercentage.setValue(idmp.getTextStringPercentage());
				this.textStringPercentage.addChangeListener(new ChangeListener() {
					public void stateChanged(ChangeEvent ce) {
						if (!textStringPercentage.getValueIsAdjusting())
							idmp.setTextStringPercentage(textStringPercentage.getValue(), false);
					}
				});
			}
			void setColor(Color color) {
				this.label.setBackground(color);
				this.label.setBorder(BorderFactory.createLineBorder(color, 2));
			}
			void editColors() {
				final JDialog ecd = DialogFactory.produceDialog("Edit Word and Text Stream Colors", true);
				
				//	create color choosers tabs
				JTabbedPane colorTabs = new JTabbedPane();
				colorTabs.setTabPlacement(JTabbedPane.LEFT);
				
				//	add color chooser for words proper
				final JColorChooser wordColor = new JColorChooser(idmp.getLayoutObjectColor(WORD_ANNOTATION_TYPE, true));
				colorTabs.addTab(WORD_ANNOTATION_TYPE, wordColor);
				
				//	add color choosers for text stream types
				final String[] textStreamTypes = idmp.getTextStreamTypes();
				final JColorChooser[] textStreamTypeColors = new JColorChooser[textStreamTypes.length];
				for (int t = 0; t < textStreamTypes.length; t++) {
					if ("".equals(textStreamTypes[t]))
						continue;
					textStreamTypeColors[t] = new JColorChooser(idmp.getTextStreamTypeColor(textStreamTypes[t], true));
					colorTabs.addTab(textStreamTypes[t], textStreamTypeColors[t]);
				}
				
				//	initialize buttons
				JButton commitButton = new JButton("OK");
				commitButton.setBorder(BorderFactory.createRaisedBevelBorder());
				commitButton.setPreferredSize(new Dimension(100, 21));
				commitButton.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent ae) {
						label.setBackground(wordColor.getColor());
						idmp.setLayoutObjectColor(WORD_ANNOTATION_TYPE, wordColor.getColor());
						for (int t = 0; t < textStreamTypes.length; t++) {
							if (!"".equals(textStreamTypes[t]))
								idmp.setTextStreamTypeColor(textStreamTypes[t], textStreamTypeColors[t].getColor());
						}
						ecd.dispose();
					}
				});
				JButton cancelButton = new JButton("Cancel");
				cancelButton.setBorder(BorderFactory.createRaisedBevelBorder());
				cancelButton.setPreferredSize(new Dimension(100, 21));
				cancelButton.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent ae) {
						ecd.dispose();
					}
				});
				
				//	assemble button panel
				JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
				buttonPanel.add(commitButton);
				buttonPanel.add(cancelButton);
				
				//	put the whole stuff together
				ecd.getContentPane().add(colorTabs, BorderLayout.CENTER);
				ecd.getContentPane().add(buttonPanel, BorderLayout.SOUTH);
				
				//	configure and show dialog
				ecd.setSize(550, 300);
				ecd.setLocationRelativeTo(this.label);
				ecd.setResizable(true);
				ecd.setVisible(true);
			}
		}
		class RegionControl extends TypeControl {
			RegionControl(String type) {
				super(type, idmp.areRegionsPainted(type), idmp.getLayoutObjectColor(type, true));
				this.paint.setToolTipText("Display regions of type '" + type + "'?");
			}
			void paintChanged(boolean paint) {
				idmp.setRegionsPainted(this.type, paint, false);
			}
			void colorChanged(Color color) {
				idmp.setLayoutObjectColor(this.type, color);
			}
		}
		class AnnotControl extends TypeControl {
			AnnotControl(String type) {
				super(type, idmp.areAnnotationsPainted(type), idmp.getAnnotationColor(type, true));
				this.paint.setToolTipText("Display annotations of type '" + type + "'?");
			}
			void paintChanged(boolean paint) {
				idmp.setAnnotationsPainted(this.type, paint, false);
			}
			void colorChanged(Color color) {
				idmp.setAnnotationColor(this.type, color);
			}
		}
	}
	
	/**
	 * Implementation of a visual extension to display as an overlay of
	 * document pages. This facilitates custom additions to the rendering of
	 * document pages, e.g. to highlight content of special interest in a
	 * specific situation.
	 * 
	 * @author sautter
	 */
	public static interface DisplayExtension {
		
		/**
		 * Indicate whether or not the shapes belonging to this display
		 * extension should be rendered. This facilitates switching display
		 * extensions on or off as needed instead of adding or removing them.
		 * @return true if the shapes belonging to this display extension
		 *        should be rendered, false otherwise
		 */
		public abstract boolean isActive();
		
		/**
		 * Obtain the shapes to display in a specific page of a document in a
		 * specific markup panel. The shapes will only show if this display
		 * extension is active. Scaling of the shapes has to be to the image
		 * resolution of the argument page; scaling to the actual display
		 * resolution is done by the rendering facilities.
		 * @param page the page to obtain the shapes for
		 * @param idmp the markup panel to display the extensions in
		 * @return an array holding the shapes to display
		 */
		public abstract DisplayExtensionGraphics[] getExtensionGraphics(ImPage page, ImDocumentMarkupPanel idmp);
	}
	
	/**
	 * Wrapper for a Java2D shapes, adding bindling functionality and control
	 * facilities. This enables handling semantically related groups of shapes
	 * to share one outline color and stroking style, one fill color, as well
	 * as one activation/deactivation status.
	 * 
	 * @author sautter
	 */
	public static abstract class DisplayExtensionGraphics {
		
		/** ordering constant indicating outlining or filling of a display extension graphics should happen before rendering the background page image (which is translucent in white areas) */
		public static final byte ORDER_BEFORE_PAGE_IMAGE = 0;
		
		/** ordering constant indicating outlining or filling of a display extension graphics should happen right before rendering  objects like bounding boxes */
		public static final byte ORDER_BEFORE_PAGE_OBJECTS = 1;
		
		/** ordering constant indicating outlining or filling of a display extension graphics should happen before rendering the page text */
		public static final byte ORDER_BEFORE_PAGE_TEXT = 2;
		
		/** ordering constant indicating outlining or filling of a display extension graphics should happen after rendering the page text */
		public static final byte ORDER_AFTER_PAGE_TEXT = 3;
		
		private static final Stroke DEFAULT_STROKE = new BasicStroke(1, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_BEVEL);
		
		/** the parent display extension */
		protected final DisplayExtension parent;
		
		/** the markup panel the shape is rendered in */
		protected final ImDocumentMarkupPanel idmp;
		
		/** the page to render the shape in */
		protected final ImPage page;
		
		/** the actual shapes to render */
		protected final Shape[] shapes;
		
		/** the color to draw the outline of the wrapped shape in (null deactivates outline drawing) */
		protected final Color lineColor;
		
		/** the stroke to draw the outline of the wrapped shape with (null uses a solid line) */
		protected final Stroke lineStroke;
		
		/** the color to fill the wrapped shape in (null deactivates filling) */
		protected final Color fillColor;
		
		/** Constructor only taking filling parameters
		 * @param parent the parent display extension
		 * @param idmp the markup panel the shape is rendered in
		 * @param page the page to render the shape in
		 * @param shapes the actual shape to render
		 * @param fillColor the color to fill the wrapped shape in (null deactivates filling)
		 */
		public DisplayExtensionGraphics(DisplayExtension parent, ImDocumentMarkupPanel idmp, ImPage page, Shape[] shapes, Color fillColor) {
			this(parent, idmp, page, shapes, null, null, fillColor);
		}
		
		/** Constructor only taking outline drawing parameters
		 * @param parent the parent display extension
		 * @param idmp the markup panel the shape is rendered in
		 * @param page the page to render the shape in
		 * @param shapes the actual shape to render
		 * @param lineColor the color to draw the outline of the wrapped shape in (null deactivates outline drawing)
		 * @param lineStroke the stroke to draw the outline of the wrapped shape with (null uses a solid line with width 1)
		 */
		public DisplayExtensionGraphics(DisplayExtension parent, ImDocumentMarkupPanel idmp, ImPage page, Shape[] shapes, Color lineColor, Stroke lineStroke) {
			this(parent, idmp, page, shapes, lineColor, lineStroke, null);
		}
		
		/** Constructor with all arguments
		 * @param parent the parent display extension
		 * @param idmp the markup panel the shape is rendered in
		 * @param page the page to render the shape in
		 * @param shapes the actual shape to render
		 * @param lineColor the color to draw the outline of the wrapped shape in (null deactivates outline drawing)
		 * @param lineStroke the stroke to draw the outline of the wrapped shape with (null uses a solid line with width 1)
		 * @param fillColor the color to fill the wrapped shape in (null deactivates filling)
		 */
		public DisplayExtensionGraphics(DisplayExtension parent, ImDocumentMarkupPanel idmp, ImPage page, Shape[] shapes, Color lineColor, Stroke lineStroke, Color fillColor) {
			this.parent = parent;
			this.idmp = idmp;
			this.page = page;
			this.shapes = shapes;
			//	TODO throw exception if shapes are null or empty (length 0)
			this.lineColor = lineColor;
			this.lineStroke = lineStroke;
			this.fillColor = fillColor;
			//	TODO throw exception if both line and fill color are null
		}
		
		/**
		 * Indicate whether or not the wrapped shapes should be rendered. This
		 * facilitates switching individual shapes on or off as needed instead
		 * of adding or removing them, e.g. based upon display control settings.
		 * @return true if the shapes should be rendered, false otherwise
		 */
		public abstract boolean isActive();
		
		/**
		 * Indicate when the shapes belonging to this display extension
		 * graphics should have their filling (if any) rendered. This default
		 * implementation indicates rendering right before the page background
		 * objects like bounding boxes, etc., so that the latter and the
		 * document text should be on top of any filling. Sub classes may
		 * overwrite this method as needed, but should make sure to not
		 * completely obfuscate the document text, e.g. by using a transparent
		 * fill color.
		 * @return the order position of the fill operation
		 */
		public byte getFillOrderPosition() {
			return ORDER_BEFORE_PAGE_OBJECTS;
		}
		
		/**
		 * Indicate when the shapes belonging to this display extension 
		 * graphics should have their outlines (if any) rendered. This default
		 * implementation indicates rendering after the document text, so that
		 * the outlines are rendered on top of any document text. Sub classes
		 * may overwrite this method as needed, but should make sure to not
		 * have the document text completely obfuscate the outline, e.g. by
		 * using a very bright line color or broad line stroke.
		 * @return the order position of the outline operation
		 */
		public byte getOutlineOrderPosition() {
			return ORDER_AFTER_PAGE_TEXT;
		}
		
		/**
		 * Render the outlines of the shapes making up this display extension
		 * graphics.
		 * @param graphics the Java2D graphics to use for rendering
		 */
		public void outline(Graphics2D graphics) {
			if (this.lineColor == null)
				return;
			Color preColor = graphics.getColor();
			graphics.setColor(this.lineColor);
			Stroke preStroke = graphics.getStroke();
			graphics.setStroke((this.lineStroke == null) ? DEFAULT_STROKE : this.lineStroke);
			for (int s = 0; s < this.shapes.length; s++)
				graphics.draw(this.shapes[s]);
			graphics.setColor(preColor);
			graphics.setStroke(preStroke);
		}
		
		/**
		 * Fill the shapes making up this display extension graphics.
		 * @param graphics the Java2D graphics to use for rendering
		 */
		public void fill(Graphics2D graphics) {
			if (this.fillColor == null)
				return;
			Color preColor = graphics.getColor();
			graphics.setColor(this.fillColor);
			for (int s = 0; s < this.shapes.length; s++)
				graphics.fill(this.shapes[s]);
			graphics.setColor(preColor);
		}
	}
	
	/**
	 * Display Overlays are panels that can be added to a page to provide a
	 * user interface for additional functionality. They are similar to dialogs
	 * in a sense, but do not block their parent window, and they scroll and
	 * zoom with the markup panel proper. A display overlay can be attached to
	 * exactly one page at a time, and there can be at most one active display
	 * overlay at any time, as adding a new one will remove any overlay that
	 * was previously added. A click in the markup panel outside a display
	 * overlay will close the latter.
	 * 
	 * @author sautter
	 */
	public static abstract class DisplayOverlay extends JPanel {
		Point onPageLocation = new Point(); // the location on the parent page, relative to the page image in its original DPI resolution
		Dimension onPageSize = new Dimension(); // the size in the parent page, in its original page image DPI resolution
		ImDocumentMarkupPanel parentPanel;
		ImPageMarkupPanel parentPage;
		
		/** Constructor
		 */
		protected DisplayOverlay() {
			super(true);
		}
		
		/** Constructor
		 * @param layout the layout manager to use
		 */
		protected DisplayOverlay(LayoutManager layout) {
			super(layout, true);
		}
		
		/**
		 * Take action when the overlay is closed. Sub classes are welcome to
		 * overwrite this method to perform some terminal write-through
		 * operations to any displaying data, depending on the argument
		 * cancellation indicator. Actual closing happens in the code calling
		 * this method.
		 * @param isCancel is closing the result of a cancellation?
		 */
		protected void overlayClosing(boolean isCancel) {}
		
		/**
		 * Indicate whether or not clsing the display overlay in reaction to a
		 * click somewhere else in the markup panel should be treated as a
		 * concellation. The return value of this method becomes the argument
		 * of the subsequent call to <code>close()</code>, and from there loops
		 * through to <code>overlayClosing()</code>. This default
		 * implementation returns false, sub classes are welcome to overwrite
		 * it as needed.
		 * @return true if a click outside the overlay should be treated as a
		 *        cancellation
		 */
		protected boolean cancelOnOutsideClick() {
			return false;
		}
		
		/**
		 * Close the overlay, i.e., remove it from the markup panel.
		 * @param isCancel is the closing operation a cancellation?
		 */
		public final void close(boolean isCancel) {
			this.overlayClosing(isCancel);
			if (this.parentPanel != null)
				this.parentPanel.setDisplayOverlay(null, -1);
			else this.setParentPage(null);
		}
		
		void setParentPage(ImPageMarkupPanel pp) {
			
			//	no actual changes
			if ((this.parentPage != null) && (this.parentPage == pp) && (this.parentPage.overlay == this)) {
				this.parentPage.revalidate();
				this.parentPage.repaint();
				return;
			}
			
			//	clean old parent page
			if (this.parentPage != null) {
				if (this.parentPage.overlay != null)
					this.parentPage.remove(this.parentPage.overlay);
				this.parentPage.overlay = null;
				this.parentPage.revalidate();
				this.parentPage.repaint();
			}
			
			//	remember parent page
			this.parentPage = pp;
			this.parentPanel = ((this.parentPage == null) ? null : this.parentPage.getParentPanel());
			
			//	make ourselves show in new parent page
			if (this.parentPage != null) {
				this.parentPage.overlay = this;
				this.parentPage.add(this);
				this.update(this.parentPanel.renderingDpi);
				this.parentPage.revalidate();
				this.parentPage.repaint();
			}
		}
		
		/**
		 * Retrieve the ID of the page the overlay is displayed in. If the
		 * overlay is not attached to a page, this method returns -1.
		 * @return the ID of the page the overlay is displayed in
		 */
		public int getPageId() {
			return ((this.parentPage == null) ? -1 : this.parentPage.page.pageId);
		}
		
		/**
		 * Retrieve the location on the parent page, relative to the page image
		 * in its original DPI resolution. The returned point instance should
		 * not be modified; use the <code>adjustSizeAndPosition()</code> method
		 * for this purpose, which also makes sure the changes show.
		 * @return the location on the parent page
		 */
		public Point getOnPageLocation() {
			return this.onPageLocation;
		}
		
		/**
		 * Retrieve the size in the parent page, in its original page image DPI
		 * resolution. The returned dimension instance should not be modified;
		 * use the <code>adjustSizeAndPosition()</code> method for this
		 * purpose, which also makes sure the changes show.
		 * @return the size in the parent page
		 */
		public Dimension getOnPageSize() {
			return this.onPageSize;
		}
		
		/**
		 * Adjust the size and position of the display overlay. The arguments
		 * to this method are interpreted in the original resolution page image
		 * coordinates; zooming and positioning in the actual display happens
		 * automatically. The attached parent page will automatically repaint
		 * to make the adjustments show.
		 * @param x the left edge
		 * @param y the top edge
		 * @param w the width
		 * @param h the height
		 */
		protected void adjustSizeAndPosition(int x, int y, int w, int h) {
			this.onPageLocation.setLocation(x, y);
			this.onPageSize.setSize(w, h);
			if (this.parentPage != null) {
				this.parentPage.revalidate();
				this.parentPage.repaint();
			}
		}
		
		/**
		 * Update the display overlay to the current rendering resolution of a
		 * newly set parent page. This default implementation does nothing, sub
		 * classes are welcome to overwrite it as needed.
		 * @param renderingDpi the current rendering resolution
		 */
		protected void update(int renderingDpi) {}
		
		/**
		 * Retrieve the current rendering resolution of the panel the display
		 * overlay is attached to, e.g. for adjusting child components. If the
		 * display overlay is currently not attached to a parent panel, this
		 * method returns -1.
		 * @return the current rendering DPI
		 */
		protected int getCurrentRenderingDpi() {
			return ((this.parentPanel == null) ? -1 : this.parentPanel.renderingDpi);
		}
		
		/**
		 * Indicates the horizontal anchor in the parent page, i.e., how to
		 * distribute additional width beyond the indicated in-page size, like
		 * width added by borders. A return value of 1 indicates to expand only
		 * to the left, 0 indicates only rightward expansion, and any value in
		 * between indicates relative distribution of the expansion to left and
		 * right. This default implementation returns 0.5, indicating equal
		 * expansion to both sides; sub classes are welcome to overwrite it as
		 * needed.
		 * @return the horizontal anchor
		 */
		protected float getHorizontalAnchor() {
			return 0.5f;
		}
		
		/**
		 * Indicates the vertical anchor in the parent page, i.e., how to
		 * distribute additional height beyond the indicated in-page size, like
		 * height added by borders. A return value of 1 indicates to expand
		 * only upward, 0 indicates only downward expansion, and any value in
		 * between indicates relative distribution of the expansion to both top
		 * and bottim. This default implementation returns 0.5, indicating
		 * equal expansion upwards and downwards; sub classes are welcome to
		 * overwrite it as needed.
		 * @return the vertical anchor
		 */
		protected float getVerticalAnchor() {
			return 0.5f;
		}
		
		/* TODO Centralize addition of non-zooming (border) content to overlay size:
- overwrite getInsets() method of overlay class proper ...
- ... to fetch content based additional "insets" from getNonZoomingContentInsets() mounting point ...
- ... and centrally combine those with insets for borders, etc.
- also add getNonZoomingContentSize() mounting point to centrally enforce minimum width and height (e.g. width of OCR editing toolbar) ...
- ... and use aggregate insets to also compensate for any deficiencies in that department
==> simplifies accommodating toolbar in upcoming OCR image editor, etc.
		 */
		
		/**
		 * Convert a bounding box in the original DPI resolution of the backing
		 * page image into representing on-screen size at the current rendering
		 * resolution.
		 * @param bb the bounding box to convert
		 * @return the onverted bounding box
		 */
		protected BoundingBox zoom(BoundingBox bb) {
			if ((this.parentPage == null) || (this.parentPanel == null))
				return bb;
			int rDpi = this.parentPanel.renderingDpi;
			int piDpi = this.parentPage.page.getImageDPI();
			if (rDpi == piDpi)
				return bb;
			return new BoundingBox(
					Math.round(((float) (bb.left * rDpi)) / piDpi),
					Math.round(((float) (bb.right * rDpi)) / piDpi),
					Math.round(((float) (bb.top * rDpi)) / piDpi),
					Math.round(((float) (bb.bottom * rDpi)) / piDpi)
				);
		}
	}
	
	/**
	 * Implementation of an action to perform for a box or word selection. Sub
	 * classes have to implement the <code>performAction()</code> method. They
	 * can further overwrite the <code>getMenuItem()</code> method, e.g. to
	 * provide a sub menu instead of a single menu item. In the latter case,
	 * the <code>performAction()</code> method should be implemented to do
	 * nothing, putting the functionality into the sub menu content.
	 * 
	 * @author sautter
	 */
	public static abstract class SelectionAction {
		
		/** including this constant action in an array of actions causes a separator to be added to the context menu */
		public static final SelectionAction SEPARATOR = new SelectionAction("SEPARATOR") {
			public boolean performAction(ImDocumentMarkupPanel invoker) { return false; }
		};
		
		/** the name of the selection action, identifying what the action does */
		public final String name;
		
		/** the label string representing the selection action in the context menu */
		public final String label;
		
		/** the tooltip explaining the selection action in the context menu */
		public final String tooltip;
		
		/** Constructor
		 * @param name the name of the selection action
		 */
		private SelectionAction(String name) {
			this(name, name, null);
		}
		
		/** Constructor
		 * @param name the name of the selection action
		 * @param label the label string to show in the context menu
		 */
		public SelectionAction(String name, String label) {
			this(name, label, null);
		}
		
		/** Constructor
		 * @param name the name of the selection action
		 * @param label the label string to show in the context menu
		 * @param tooltip the tooltip text for the context menu
		 */
		public SelectionAction(String name, String label, String tooltip) {
			this.name = name;
			this.label = label;
			this.tooltip = tooltip;
		}
		
		/**
		 * Indicate whether or not this selection action is an atomic action in
		 * itself. If this method returns true (and this default implementation
		 * does), the default menu item will encapsulate any call to the
		 * <code>performAction()</code> method in an atomic action, using the
		 * action label as the label for the atomic action. Sub classes can
		 * overwrite this method to change this behavior.
		 * @return true if <code>performAction()</code> is to be atomic
		 */
		protected boolean isAtomicAction() {
			return true;
		}
		
		/**
		 * Perform the action.
		 * @param invoker the component the parent menu shows on
		 * @return true if the document was changed by the method, false otherwise
		 */
		public abstract boolean performAction(ImDocumentMarkupPanel invoker);
		
		/**
		 * Retrieve a menu item to represent the action in the context menu.
		 * This default implementation returns a <code>JMenuItem</code> with
		 * the label and tooltip handed to the constructor, and an action
		 * listener calling the performAction() method. If the latter returns
		 * true, the argument invoker is repainted. Sub classes may overwrite
		 * this method to provide a better suited representation of themselves.
		 * As this method also handles atomicity of the changes performed in
		 * the argument document, however, sub classes overwriting this method
		 * have to take care of the latter as well.
		 * @param invoker the component the parent menu shows on
		 * @return a menu item to represent the action in the context menu
		 */
		public JMenuItem getMenuItem(final ImDocumentMarkupPanel invoker) {
			JMenuItem mi = new JMenuItem(this.label);
			if (this.tooltip != null)
				mi.setToolTipText(this.tooltip);
			mi.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent ae) {
					boolean isAtomicAction = isAtomicAction();
					try {
						if (isAtomicAction)
							invoker.beginAtomicAction(label);
						boolean changed = performAction(invoker);
						if (changed) {
							invoker.validate();
							invoker.repaint();
						}
					}
					finally {
						if (isAtomicAction)
							invoker.endAtomicAction();
					}
				}
			});
			return mi;
		}
		
		JMenuItem getContextMenuItem(ImDocumentMarkupPanel invoker) {
			if (this.contextMenuItem == null)
				this.contextMenuItem = invoker.getContextMenuItemFor(this);
			return this.contextMenuItem;
		}
		JMenuItem contextMenuItem = null;
	}
	
	/**
	 * Create a context menu item for a selection action. This default implementation
	 * simply calls <code>getMenuItem()</code> on the argument selection action. Sub
	 * classes are welcome to overwrite this method to add furether implementation
	 * specific operations.
	 * @param action the selection action to obtain the mnu item for
	 * @return the menu item for the argument selection action
	 */
	protected JMenuItem getContextMenuItemFor(SelectionAction action) {
		return action.getMenuItem(this);
	}
	
	/**
	 * Selection action to execute straight away for a plain click on an Image
	 * Markup layout object, i.e., without intermediate display of a context
	 * menu. Click actions provide a means of injecting default behavior on
	 * plain clicks, i.e., selections that do not involve mouse movement in
	 * between pressing and releasing the mouse button. If multiple click
	 * actions are available for a single click, they are consulted in order of
	 * descending priority, stopping soon as the first returns true from it
	 * <code>handleClick()</code> method. No further selection action come to
	 * bear after that. If no click action handles a click, normal selection
	 * actions will a context menu as usual.
	 * 
	 * @author sautter
	 */
	public static abstract class ClickSelectionAction extends SelectionAction implements Comparable {
		
		/** Constructor
		 * @param name the name of the selection action
		 * @param label the label string to show in the context menu
		 */
		public ClickSelectionAction(String name, String label) {
			super(name, label);
		}
		
		/** Constructor
		 * @param name the name of the selection action
		 * @param label the label string to show in the context menu
		 * @param tooltip the tooltip text for the context menu
		 */
		public ClickSelectionAction(String name, String label, String tooltip) {
			super(name, label, tooltip);
		}
		
		public boolean performAction(ImDocumentMarkupPanel invoker) {
			return false;
		}
		
		/**
		 * Indicate the priority of the click action, on a 0-10 scale. In the
		 * presence of multiple actions for a single click, their
		 * <code>handleClick()</code> methods are consulted in descending
		 * priority order until the first one returns true.
		 * @return the priority of the click action
		 */
		public abstract int getPriority();
		
		/**
		 * Actually handle the click. Since the return value of this method is
		 * used for controlling behavior, implementations have to handle any
		 * atomic actions internally
		 * @param invoker the component the click happened in
		 * @return true if the click was handled
		 */
		public abstract boolean handleClick(ImDocumentMarkupPanel invoker);
		
		/* (non-Javadoc)
		 * @see java.lang.Comparable#compareTo(java.lang.Object)
		 */
		public int compareTo(Object obj) {
			return (((ClickSelectionAction) obj).getPriority() - this.getPriority());
		}
	}
	
	/**
	 * Selection action that works with two clicks rather than one, usually
	 * with intermediate scrolling. If a two-click action is selected in the
	 * context menu after a click, it remains active until completed by a click
	 * on a second word, or cancelled by a new selection or a click outside any
	 * word.
	 * 
	 * @author sautter
	 */
	public static abstract class TwoClickSelectionAction extends SelectionAction {
		
		/** Constructor
		 * @param name the name of the selection action
		 * @param label the label string to show in the context menu
		 */
		public TwoClickSelectionAction(String name, String label) {
			super(name, label);
		}
		
		/** Constructor
		 * @param name the name of the selection action
		 * @param label the label string to show in the context menu
		 * @param tooltip the tooltip text for the context menu
		 */
		public TwoClickSelectionAction(String name, String label, String tooltip) {
			super(name, label, tooltip);
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.im.util.ImDocumentViewerPanel.SelectionAction#performAction()
		 */
		public final boolean performAction(ImDocumentMarkupPanel invoker) {
			invoker.pendingTwoClickAction = this;
			if (invoker.twoClickActionMessenger != null)
				invoker.twoClickActionMessenger.twoClickActionChanged(this);
			return false;
		}
//		
//		/**
//		 * Sub classes overwriting this method to return something else but a
//		 * single menu item have to make sure to call the <code>performAction()</code>
//		 * method, as the latter registers the two-click action as pending
//		 * @see de.uka.ipd.idaho.im.util.ImPageMarkupPanel.SelectionAction#getMenuItem(ImDocumentMarkupPanel)
//		 */
//		public JMenuItem getMenuItem(ImDocumentMarkupPanel invoker) {
//			return super.getMenuItem(invoker);
//		}
//		
//		/**
//		 * Retrieve the word this two-click action was created upon, aka the
//		 * first word to be combined with a second word on the second click.
//		 * @return the word this two-click action was created upon
//		 */
//		public abstract ImWord getFirstWord();
		
		/**
		 * Retrieve the region this two-click action was created upon, aka the
		 * first region to be combined with a second region on the second click.
		 * @return the region this two-click action was created upon
		 */
		public abstract ImRegion getFirstRegion();
		
		/**
		 * Perform the action. The argument word is the word the second click
		 * occurred on.
		 * @param secondWord the second word
		 * @return true if the document was changed by the method, false otherwise
		 */
		public abstract boolean performAction(ImWord secondWord);
		
		/**
		 * Perform the action. The argument page is the page the second click
		 * occurred on, the argument point representing the location of the
		 * click (in unscaled page coordinates).
		 * @param secondPage the second page
		 * @param secondPoint the second point
		 * @return true if the document was changed by the method, false otherwise
		 */
		public abstract boolean performAction(ImPage secondPage, Point secondPoint);
		
		/**
		 * Retrieve a label to display when the action is active, awaiting the
		 * second click
		 * @return the active label
		 */
		public abstract String getActiveLabel();
	}
}