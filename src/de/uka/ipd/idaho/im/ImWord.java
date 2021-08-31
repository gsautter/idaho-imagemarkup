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

import java.awt.ComponentOrientation;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.util.Arrays;
import java.util.Comparator;

import de.uka.ipd.idaho.gamta.Gamta;
import de.uka.ipd.idaho.gamta.util.constants.LiteratureConstants;
import de.uka.ipd.idaho.gamta.util.imaging.BoundingBox;
import de.uka.ipd.idaho.gamta.util.imaging.PageImage;
import de.uka.ipd.idaho.im.util.ImObjectTransformer;
import de.uka.ipd.idaho.im.util.ImObjectTransformer.AttributeTransformer;

/**
 * A single word in an image markup document, i.e., a bounding box enclosing a
 * word on the underlying page image, supplemented by its string value, layout
 * information, and the ID of the page it belongs to. The string value need not
 * necessarily be a single word in a linguistic sense, as it might (a) include
 * adjacent punctuation or (b) be either part of a hyphenated word or a word
 * torn apart due to extremely wide character spacing. The next relation
 * property reflects the latter cases. In addition, a word holds pointers to its
 * semantic predecessor and successor, i.e., the previous and next word in
 * reading order, independent of the layout represented by the underlying image.
 * 
 * @author sautter
 */
public class ImWord extends ImRegion implements ImAnnotation {
	
	private static final Comparator TOP_LEFT_ORDER = new Comparator() {
		public int compare(Object o1, Object o2) {
			ImWord w1 = ((ImWord) o1);
			ImWord w2 = ((ImWord) o2);
			
			//	quick check
			if (w1 == w2)
				return 0;
			
			//	compare spacially
			if (w1.centerX < w2.centerX)
				return -1;
			if (w2.centerX < w1.centerX)
				return 1;
			if (w1.centerY < w2.centerY)
				return -1;
			if (w2.centerY < w1.centerY)
				return 1;
			
			//	compare IDs as a last resort
			return w1.getLocalID().compareTo(w2.getLocalID());
		}
	};
	private static final Comparator TOP_RIGHT_ORDER = new Comparator() {
		public int compare(Object o1, Object o2) {
			ImWord w1 = ((ImWord) o1);
			ImWord w2 = ((ImWord) o2);
			
			//	quick check
			if (w1 == w2)
				return 0;
			
			//	compare spacially
			if (w1.centerX < w2.centerX)
				return 1;
			if (w2.centerX < w1.centerX)
				return -1;
			if (w1.centerY < w2.centerY)
				return -1;
			if (w2.centerY < w1.centerY)
				return 1;
			
			//	compare IDs as a last resort
			return w1.getLocalID().compareTo(w2.getLocalID());
		}
	};
//	private static final Comparator BOTTOM_LEFT_ORDER = new Comparator() {
//		public int compare(Object o1, Object o2) {
//			ImWord w1 = ((ImWord) o1);
//			ImWord w2 = ((ImWord) o2);
//			
//			//	check non-overlapping cases first
//			if (w1.bounds.bottom <= w2.bounds.top)
//				return 1;
//			if (w2.bounds.bottom <= w1.bounds.top)
//				return -1;
//			if (w1.bounds.right <= w2.bounds.left)
//				return -1;
//			if (w2.bounds.right <= w1.bounds.left)
//				return 1;
//			
//			//	now, we have overlap (more likely within lines than between)
//			if (w1.centerY <= w2.bounds.top)
//				return 1;
//			if (w2.centerY <= w1.bounds.top)
//				return -1;
//			if (w1.centerX <= w2.bounds.left)
//				return -1;
//			if (w2.centerX <= w1.bounds.left)
//				return 1;
//			
//			//	now, either center lies in the other box (massive overlap, not peripheral)
//			return ((w1.centerY == w2.centerY) ? (w1.centerX - w2.centerX) : (w2.centerY - w1.centerY));
//		}
//	};
//	private static final Comparator BOTTOM_RIGHT_ORDER = new Comparator() {
//		public int compare(Object o1, Object o2) {
//			ImWord w1 = ((ImWord) o1);
//			ImWord w2 = ((ImWord) o2);
//			
//			//	check non-overlapping cases first
//			if (w1.bounds.bottom <= w2.bounds.top)
//				return 1;
//			if (w2.bounds.bottom <= w1.bounds.top)
//				return -1;
//			if (w1.bounds.right <= w2.bounds.left)
//				return 1;
//			if (w2.bounds.right <= w1.bounds.left)
//				return -1;
//			
//			//	now, we have overlap (more likely within lines than between)
//			if (w1.centerY <= w2.bounds.top)
//				return 1;
//			if (w2.centerY <= w1.bounds.top)
//				return -1;
//			if (w1.centerX <= w2.bounds.left)
//				return 1;
//			if (w2.centerX <= w1.bounds.left)
//				return -1;
//			
//			//	now, either center lies in the other box (massive overlap, not peripheral)
//			return ((w1.centerY == w2.centerY) ? (w2.centerX - w1.centerX) : (w2.centerY - w1.centerY));
//		}
//	};
//	private static final Comparator LEFT_TOP_ORDER = new Comparator() {
//		public int compare(Object o1, Object o2) {
//			ImWord w1 = ((ImWord) o1);
//			ImWord w2 = ((ImWord) o2);
//			
//			//	check non-overlapping cases first
//			if (w1.bounds.right <= w2.bounds.left)
//				return -1;
//			if (w2.bounds.right <= w1.bounds.left)
//				return 1;
//			if (w1.bounds.bottom <= w2.bounds.top)
//				return -1;
//			if (w2.bounds.bottom <= w1.bounds.top)
//				return 1;
//			
//			//	now, we have overlap (more likely within lines than between)
//			if (w1.centerX <= w2.bounds.left)
//				return -1;
//			if (w2.centerX <= w1.bounds.left)
//				return 1;
//			if (w1.centerY <= w2.bounds.top)
//				return -1;
//			if (w2.centerY <= w1.bounds.top)
//				return 1;
//			
//			//	now, either center lies in the other box (massive overlap, not peripheral)
//			return ((w1.centerX == w2.centerX) ? (w1.centerY - w2.centerY) : (w1.centerX - w2.centerX));
//		}
//	};
//	private static final Comparator RIGHT_TOP_ORDER = new Comparator() {
//		public int compare(Object o1, Object o2) {
//			ImWord w1 = ((ImWord) o1);
//			ImWord w2 = ((ImWord) o2);
//			
//			//	check non-overlapping cases first
//			if (w1.bounds.right <= w2.bounds.left)
//				return 1;
//			if (w2.bounds.right <= w1.bounds.left)
//				return -1;
//			if (w1.bounds.bottom <= w2.bounds.top)
//				return -1;
//			if (w2.bounds.bottom <= w1.bounds.top)
//				return 1;
//			
//			//	now, we have overlap (more likely within lines than between)
//			if (w1.centerX <= w2.bounds.left)
//				return 1;
//			if (w2.centerX <= w1.bounds.left)
//				return -1;
//			if (w1.centerY <= w2.bounds.top)
//				return -1;
//			if (w2.centerY <= w1.bounds.top)
//				return 1;
//			
//			//	now, either center lies in the other box (massive overlap, not peripheral)
//			return ((w1.centerX == w2.centerX) ? (w2.centerY - w1.centerY) : (w1.centerX - w2.centerX));
//		}
//	};
//	private static final Comparator LEFT_BOTTOM_ORDER = new Comparator() {
//		public int compare(Object o1, Object o2) {
//			ImWord w1 = ((ImWord) o1);
//			ImWord w2 = ((ImWord) o2);
//			
//			//	check non-overlapping cases first
//			if (w1.bounds.right <= w2.bounds.left)
//				return -1;
//			if (w2.bounds.right <= w1.bounds.left)
//				return 1;
//			if (w1.bounds.bottom <= w2.bounds.top)
//				return 1;
//			if (w2.bounds.bottom <= w1.bounds.top)
//				return -1;
//			
//			//	now, we have overlap (more likely within lines than between)
//			if (w1.centerX <= w2.bounds.left)
//				return -1;
//			if (w2.centerX <= w1.bounds.left)
//				return 1;
//			if (w1.centerY <= w2.bounds.top)
//				return 1;
//			if (w2.centerY <= w1.bounds.top)
//				return -1;
//			
//			//	now, either center lies in the other box (massive overlap, not peripheral)
//			return ((w1.centerX == w2.centerX) ? (w1.centerY - w2.centerY) : (w2.centerX - w1.centerX));
//		}
//	};
//	private static final Comparator RIGHT_BOTTOM_ORDER = new Comparator() {
//		public int compare(Object o1, Object o2) {
//			ImWord w1 = ((ImWord) o1);
//			ImWord w2 = ((ImWord) o2);
//			
//			//	check non-overlapping cases first
//			if (w1.bounds.right <= w2.bounds.left)
//				return 1;
//			if (w2.bounds.right <= w1.bounds.left)
//				return -1;
//			if (w1.bounds.bottom <= w2.bounds.top)
//				return 1;
//			if (w2.bounds.bottom <= w1.bounds.top)
//				return -1;
//			
//			//	now, we have overlap (more likely within lines than between)
//			if (w1.centerX <= w2.bounds.left)
//				return 1;
//			if (w2.centerX <= w1.bounds.left)
//				return -1;
//			if (w1.centerY <= w2.bounds.top)
//				return 1;
//			if (w2.centerY <= w1.bounds.top)
//				return -1;
//			
//			//	now, either center lies in the other box (massive overlap, not peripheral)
//			return ((w1.centerX == w2.centerX) ? (w2.centerY - w1.centerY) : (w2.centerX - w1.centerX));
//		}
//	};
	
	static Comparator getComparator(ComponentOrientation orientation) {
		if (orientation == ComponentOrientation.LEFT_TO_RIGHT)
			return TOP_LEFT_ORDER;
		if (orientation == ComponentOrientation.RIGHT_TO_LEFT)
			return TOP_RIGHT_ORDER;
		throw new RuntimeException("Ordering not yet supported by JVM, see source of java.awt.ComponentOrientation for details");
	}
	
	/** the value indicating the next image word is a separate word
	 * linguistically, i.e., should be appended with a separating space on
	 * concatenation (the default) */
	public static final char NEXT_RELATION_SEPARATE = 'S';

	/** the value indicating the next image word belongs to the same word
	 * linguistically, i.e., should be appended without a separating space on
	 * concatenation */
	public static final char NEXT_RELATION_CONTINUE = 'C';

	/** the value indicating the next image word belongs to the same word
	 * linguistically and is hyphenated, i.e., should appended without a
	 * separating space on concatenation, dropping any dash at the end of this
	 * word's string value */
	public static final char NEXT_RELATION_HYPHENATED = 'H';
	
	/** the value indicating the next image word is a separate word
	 * linguistically and starts a new paragraph, i.e., should be appended with
	 * a separating line break on concatenation */
	public static final char NEXT_RELATION_PARAGRAPH_END = 'P';
	
	/** the text stream type indicating a text stream belongs to the document
	 * main text */
	public static final String TEXT_STREAM_TYPE_MAIN_TEXT = "mainText";
	
	/** the text stream type indicating a text stream is a footnote */
	public static final String TEXT_STREAM_TYPE_FOOTNOTE = LiteratureConstants.FOOTNOTE_TYPE;
	
	/** the text stream type indicating a text stream is a caption */
	public static final String TEXT_STREAM_TYPE_CAPTION = LiteratureConstants.CAPTION_TYPE;
	
	/** the text stream type indicating a text stream is a table */
	public static final String TEXT_STREAM_TYPE_TABLE = "table";
	
	/** the text stream type indicating a text stream is a note associated with a table */
	public static final String TEXT_STREAM_TYPE_TABLE_NOTE = "tableNote";
	
	/** the text stream type indicating a text stream is a label in a bitmap figure or vector graphics */
	public static final String TEXT_STREAM_TYPE_LABEL = "label";
	
	/** the text stream type indicating a text stream is a page title (i.e., a
	 * page header or footer) */
	public static final String TEXT_STREAM_TYPE_PAGE_TITLE = LiteratureConstants.PAGE_TITLE_TYPE;
	
	/** the text stream type indicating a text stream actually is an image,
	 * figure, or diagram that OCR erroneously recognized words in, or some
	 * other stain or mark on a page for which this happened */
	public static final String TEXT_STREAM_TYPE_ARTIFACT = "artifact";
	
	/** the text stream type indicating a text stream has been deleted */
	public static final String TEXT_STREAM_TYPE_DELETED = "deleted";
	
	/** the name to use to retrieve the previous word as a virtual attribute
	 * via the generic <code>getAttribute()</code> methods */
	public static final String PREVIOUS_WORD_ATTRIBUTE = "prevWord";
	
	/** the name to use to retrieve the next word as a virtual attribute via
	 * the generic <code>getAttribute()</code> methods */
	public static final String NEXT_WORD_ATTRIBUTE = "nextWord";
	
	/** the name to use to retrieve the previous word relation as a virtual
	 * attribute via the generic <code>getAttribute()</code> methods */
	public static final String PREVIOUS_RELATION_ATTRIBUTE = "prevRelation";
	
	/** the name to use to retrieve the next word relation as a virtual
	 * attribute via the generic <code>getAttribute()</code> methods */
	public static final String NEXT_RELATION_ATTRIBUTE = "nextRelation";
	
	/** the name to use to retrieve and set the words text stream type as a
	 * virtual attribute via the generic <code>getAttribute()</code> and
	 * <code>setAttribute()</code> methods */
	public static final String TEXT_STREAM_TYPE_ATTRIBUTE = "textStreamType";
	
	static {
		ImObjectTransformer.addGlobalAttributeTransformer(new AttributeTransformer() {
			public boolean canTransformAttribute(String name) {
				return BASELINE_ATTRIBUTE.equals(name);
			}
			public Object transformAttributeValue(ImObject object, String name, Object value, ImObjectTransformer transformer) {
				if (value == null)
					return null;
				int baseline;
				try {
					baseline = Integer.parseInt(value.toString());
				}
				catch (NumberFormatException nfe) {
					return null;
				}
				Object textDirection = object.getAttribute(TEXT_DIRECTION_ATTRIBUTE, TEXT_DIRECTION_LEFT_RIGHT);
				if (TEXT_DIRECTION_BOTTOM_UP.equals(textDirection) || TEXT_DIRECTION_TOP_DOWN.equals(textDirection))
					baseline = (baseline - transformer.fromBounds.left);
				else baseline = (baseline - transformer.fromBounds.top);
				textDirection = transformTextDirection(value, transformer);
				if (TEXT_DIRECTION_BOTTOM_UP.equals(textDirection) || TEXT_DIRECTION_TOP_DOWN.equals(textDirection))
					baseline = (baseline + transformer.toBounds.left);
				else baseline = (baseline + transformer.toBounds.top);
				return ("" + baseline);
			}
		});
	}
	
	/** the horizontal center of the word's bounding box, to speed up inclusion tests */
	public final int centerX;
	
	/** the vertical center of the word's bounding box, to speed up inclusion tests */
	public final int centerY;
	
	private String string;
	private String fontName = null;
	private int fontSize = -1;
	private String fontCharCodes = null;
	
	private static final int TEXT_DIR_LEFT_RIGHT = 0x00000000;
	private static final int TEXT_DIR_TOP_DOWN = 0x00000001;
	private static final int TEXT_DIR_BOTTOM_UP = 0x00000002;
	private static final int TEXT_DIR_RIGHT_LEFT_UPSIDE_DOWN = 0x00000003;
	private static final int TEXT_DIR_MASK = 0x00000003;
	private static final String[] TEXT_DIRS = {
		null, //TEXT_DIRECTION_LEFT_RIGHT, ==> default, not store explicitly in attributes, and code built that way
		TEXT_DIRECTION_TOP_DOWN,
		TEXT_DIRECTION_BOTTOM_UP,
		TEXT_DIRECTION_RIGHT_LEFT_UPSIDE_DOWN,
	};
	private static final int FONT_PROP_BOLD = 0x00000004;
	private static final int FONT_PROP_ITALICS = 0x00000008;
	private int flags = 0; // could use byte, but due to memory alignment, we'd be using 4 bytes anyway
	
	private ImWord prevWord = null;
	private ImWord nextWord = null;
	private char nextRelation = NEXT_RELATION_SEPARATE;
	
	private String textStreamId; // local ID of first word in text stream
//	private int textStreamPos = 0; // position within text stream in page
	private int textStreamPos = 0; // position within text stream, absolute
	private String textStreamType = TEXT_STREAM_TYPE_MAIN_TEXT; // type of the text stream the word belongs to
	
	/** Constructor (automatically adds the word to the argument page; if this
	 * is undesired for whatever reason, one of the constructors taking a
	 * document and a page ID as arguments has to be used instead)
	 * @param page the page the word lies on
	 * @param bounds the bounding box enclosing the word on the underlying page image
	 */
	public ImWord(ImPage page, BoundingBox bounds) {
		this(page, bounds, "");
	}
	
	/** Constructor (automatically adds the word to the argument page; if this
	 * is undesired for whatever reason, one of the constructors taking a
	 * document and a page ID as arguments has to be used instead)
	 * @param page the page the word lies on
	 * @param bounds the bounding box enclosing the word on the underlying page image
	 * @param string the string value the word
	 */
	public ImWord(ImPage page, BoundingBox bounds, String string) {
		this(page.getDocument(), page.pageId, bounds, string);
		page.addWord(this);
	}
	
	/** Constructor
	 * @param doc the document the word belongs to
	 * @param pageId the ID of the page the word lies in
	 * @param bounds the bounding box enclosing the word on the underlying page image
	 */
	public ImWord(ImDocument doc, int pageId, BoundingBox bounds) {
		this(doc, pageId, bounds, "");
	}
	
	/** Constructor
	 * @param doc the document the word belongs to
	 * @param pageId the ID of the page the word lies in
	 * @param bounds the bounding box enclosing the word on the underlying page image
	 * @param string the string value the word
	 */
	public ImWord(ImDocument doc, int pageId, BoundingBox bounds, String string) {
		super(doc, pageId, bounds, WORD_ANNOTATION_TYPE);
		this.centerX = ((this.bounds.left + this.bounds.right) / 2);
		this.centerY = ((this.bounds.top + this.bounds.bottom) / 2);
		this.string = string;
		this.textStreamId = this.getLocalID();
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImAnnotation#getFirstWord()
	 */
	public ImWord getFirstWord() {
		return this;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImAnnotation#setFirstWord(de.uka.ipd.idaho.im.ImWord)
	 */
	public void setFirstWord(ImWord firstWord) {}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImAnnotation#getLastWord()
	 */
	public ImWord getLastWord() {
		return this;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImAnnotation#setLastWord(de.uka.ipd.idaho.im.ImWord)
	 */
	public void setLastWord(ImWord lastWord) {}
	
	/**
	 * Retrieve a document local ID for the layout object. This is helpful for
	 * indexing, storage, etc. The ID takes the form
	 * <code>&lt;pageId&gt;.&lt;bounds&gt;</code>'
	 * @return the document local ID
	 */
	public String getLocalID() {
		if (this.localId == null)
			this.localId = (this.pageId + "." + this.bounds.toString());
		return this.localId;
	}
	private String localId = null;
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImLayoutObject#getType()
	 */
	public String getType() {
		return WORD_ANNOTATION_TYPE;
	}
	
	/**
	 * This implementation does not have any effect, so its corresponding getter
	 * always returns 'word'.
	 * @see de.uka.ipd.idaho.im.ImObject#setType(java.lang.String)
	 */
	public void setType(String type) {}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImLayoutObject#getBounds()
	 */
	public BoundingBox getBounds() {
		return this.bounds;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImRegion#getWords()
	 */
	public ImWord[] getWords() {
		ImWord[] imws = {this};
		return imws;
	}
	
	/**
	 * Retrieve the string value of the image word, e.g. the result of an OCR
	 * pass over the underlying page image or a user-corrected version of the
	 * latter.
	 * @return the string value of the image word
	 */
	public String getString() {
		return this.string;
	}
	
	/**
	 * Alter the string value of the image word, e.g. to correct an OCR error.
	 * @param string the new string value
	 */
	public void setString(String string) {
		String oldString = this.string;
		this.string = string;
		this.notifyAttributeChanged(STRING_ATTRIBUTE, true, this.string, oldString);
	}
	
	/**
	 * Retrieves the font name of the word (if known), otherwise returns null.
	 * @return the font name
	 */
	public String getFontName() {
		return this.fontName;
	}
	
	/**
	 * Set the font name of the word. This method should rarely be used outside
	 * of generating or loading an image markup document.
	 * @param fontName the font name to set
	 */
	public void setFontName(String fontName) {
		String oldFontName = this.fontName;
		this.fontName = fontName;
		this.notifyAttributeChanged(FONT_NAME_ATTRIBUTE, true, this.fontName, oldFontName);
	}
	
	/**
	 * Retrieves the font size of the word (if known), otherwise returns -1.
	 * @return the font size
	 */
	public int getFontSize() {
		return this.fontSize;
	}
	
	/**
	 * Set the font size of the word. This method should rarely be used outside
	 * of generating or loading an image markup document.
	 * @param fontSize the font size to set
	 */
	public void setFontSize(int fontSize) {
		if (fontSize == this.fontSize)
			return;
		int oldFontSize = this.fontSize;
		this.fontSize = fontSize;
		this.notifyAttributeChanged(FONT_SIZE_ATTRIBUTE, false, Integer.toString(this.fontSize), Integer.toString(oldFontSize));
	}
	private void setFontSize(String fontSize) {
		int fs = -1;
		if (fontSize != null) try {
			fs = Integer.parseInt(fontSize.trim());
		} catch (NumberFormatException nfe) {}
		this.setFontSize(fs);
	}
	
	/**
	 * Retrieves the font char codes of the word (if known), otherwise returns
	 * null. This string holds, in HEX representation, the font based codes of
	 * the individual characters the word string consists of, usually two, but
	 * in rare cases three HEX digits per character. The number of digits can
	 * be retrieved from the font the word is written in.
	 * @return the font char codes
	 */
	public String getFontCharCodes() {
		return this.fontCharCodes;
	}
	
	/**
	 * Set the font char codes of the word. This method should rarely be used
	 * outside of generating or loading an image markup document.
	 * @param fontCharCodes the font char codes to set
	 */
	public void setFontCharCodes(String fontCharCodes) {
		String oldFontCharCodes = this.fontCharCodes;
		this.fontCharCodes = fontCharCodes;
		this.notifyAttributeChanged(ImFont.CHARACTER_CODE_STRING_ATTRIBUTE, true, this.fontCharCodes, oldFontCharCodes);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImLayoutObject#getImage()
	 */
	public PageImage getImage() {
		PageImage pi = this.getPageImage();
		return pi.getSubImage(this.bounds, true);
	}
	
	/**
	 * Retrieve the previous image word. This might be null, e.g. if the word is
	 * the first in a logical text stream.
	 * @return the previous image word
	 */
	public ImWord getPreviousWord() {
		return this.prevWord;
	}
	
	/**
	 * Set the pointer to the previous image word. This also sets the next word
	 * of the argument word to this word, and the next word of the old previous
	 * word to null.
	 * @param prevWord the new previous image word
	 */
	public void setPreviousWord(ImWord prevWord) {
		if (prevWord == this.prevWord)
			return;
		if (prevWord == this)
			throw new IllegalArgumentException("Cannot set predecessor of '" + this.getString() + "' (page " + this.pageId + ") to self.");
		else if ((prevWord != null) && (prevWord.textStreamId.equals(this.textStreamId)) && (this.pageId == prevWord.pageId) && (this.textStreamPos < prevWord.textStreamPos))
			throw new IllegalArgumentException("Cannot set predecessor of '" + this.getString() + "' (page " + this.pageId + " at " + this.bounds + ") to successor '" + prevWord.getString() + "' (page " + prevWord.pageId + " at " + prevWord.bounds + ").");
		final ImWord oldPrev = this.prevWord;
		final String oldType = ((oldPrev != null) ? null : this.textStreamType);
		final ImWord prevOldNext = ((prevWord == null) ? null : prevWord.nextWord);
		this.prevWord = prevWord;
		invalidatePageTextStreamEnds(this);
		if (this.prevWord != null) {
			this.prevWord.nextWord = this;
			invalidatePageTextStreamEnds(this.prevWord);
		}
		if (prevOldNext != null) {
			prevOldNext.prevWord = null;
			invalidatePageTextStreamEnds(prevOldNext);
		}
		if (oldPrev != null) {
			oldPrev.nextWord = null;
			invalidatePageTextStreamEnds(oldPrev);
		}
		
		if (this.prevWord == null) {
			this.textStreamId = this.getLocalID();
			this.textStreamPos = 0;
		}
		else {
			this.textStreamId = this.prevWord.textStreamId;
			this.textStreamPos = (this.prevWord.textStreamPos + 1);
			this.textStreamType = this.prevWord.textStreamType;
		}
		
		propagateTextStreamProperties(this.nextWord, this, (this.textStreamPos + 1), this.textStreamId, this.textStreamType);
		
		if (prevOldNext != null) {
			prevOldNext.textStreamId = prevOldNext.getLocalID();
			prevOldNext.textStreamPos = 0;
			propagateTextStreamProperties(prevOldNext.nextWord, prevOldNext, (prevOldNext.textStreamPos + 1), prevOldNext.textStreamId, prevOldNext.textStreamType);
			for (ImWord imw = prevOldNext.nextWord; imw != null; imw = imw.nextWord) {
				if (imw == prevOldNext)
					break; // cycle breaker safety
				imw.textStreamPos = (imw.prevWord.textStreamPos + 1);
				imw.textStreamId = prevOldNext.textStreamId;
				imw.textStreamType = prevOldNext.textStreamType;
			}
		}
		
		//	TODO centralize attribute notification
//		if (this.getPage() != null) {
//			if ((oldType != null) && !oldType.equals(this.textStreamType))
//				this.getDocument().notifyAttributeChanged(this, TEXT_STREAM_TYPE_ATTRIBUTE, oldType);
			this.notifyAttributeChanged(TEXT_STREAM_TYPE_ATTRIBUTE, true, this.textStreamType, oldType);
			
//			this.getDocument().notifyAttributeChanged(this, PREVIOUS_WORD_ATTRIBUTE, oldPrev);
			this.notifyAttributeChanged(PREVIOUS_WORD_ATTRIBUTE, false, this.prevWord, oldPrev);
			
			if (prevOldNext != null)
//				this.getDocument().notifyAttributeChanged(prevOldNext, PREVIOUS_WORD_ATTRIBUTE, this.prevWord);
				prevOldNext.notifyAttributeChanged(PREVIOUS_WORD_ATTRIBUTE, true, prevOldNext.prevWord, this.prevWord);
//		}
	}
	
	/**
	 * Retrieve the next image word. This might be null, e.g. if the word is
	 * the last in a logical text stream.
	 * @return the next image word
	 */
	public ImWord getNextWord() {
		return this.nextWord;
	}
	
	/**
	 * Set the pointer to the next image word. This also sets the previous word
	 * of the argument word to this word, and the previous word of the old next
	 * word to null.
	 * @param nextWord the new next image word
	 */
	public void setNextWord(ImWord nextWord) {
		if (nextWord == this.nextWord)
			return;
		if (nextWord == this)
			throw new IllegalArgumentException("Cannot set successor of '" + this.getString() + "' (page " + this.pageId + ") to self.");
		else if ((nextWord != null) && (nextWord.textStreamId.equals(this.textStreamId)) && (this.pageId == nextWord.pageId) && (nextWord.textStreamPos < this.textStreamPos))
			throw new IllegalArgumentException("Cannot set successor of '" + this.getString() + "' (page " + this.pageId + " at " + this.bounds + ") to predecessor '" + nextWord.getString() + "' (page " + nextWord.pageId + " at " + nextWord.bounds + ").");
		final ImWord oldNext = this.nextWord;
		final ImWord nextOldPrev = ((nextWord == null) ? null : nextWord.prevWord);
		final String nextOldType = (((nextWord == null) || (nextOldPrev != null)) ? null : nextWord.getTextStreamType());
		this.nextWord = nextWord;
		invalidatePageTextStreamEnds(this);
		if (this.nextWord != null) {
			this.nextWord.prevWord = this;
			invalidatePageTextStreamEnds(this.nextWord);
		}
		if (oldNext != null) {
			oldNext.prevWord = null;
			invalidatePageTextStreamEnds(oldNext);
		}
		if (nextOldPrev != null) {
			nextOldPrev.nextWord = null;
			invalidatePageTextStreamEnds(nextOldPrev);
		}
		if (this.nextWord != null)
			propagateTextStreamProperties(this.nextWord, this, (this.textStreamPos + 1), this.textStreamId, this.textStreamType);
		
		if (oldNext != null) {
			oldNext.textStreamId = oldNext.getLocalID();
			oldNext.textStreamPos = 0;
			propagateTextStreamProperties(oldNext.nextWord, oldNext, (oldNext.textStreamPos + 1), oldNext.textStreamId, oldNext.textStreamType);
		}
		
		//	TODO centralize attribute notification
//		if (this.getPage() != null) {
//			if ((nextOldType != null) && !nextOldType.equals(this.textStreamType))
//				this.getDocument().notifyAttributeChanged(nextWord, TEXT_STREAM_TYPE_ATTRIBUTE, nextOldType);
			if (nextWord != null)
				nextWord.notifyAttributeChanged(TEXT_STREAM_TYPE_ATTRIBUTE, true, nextWord.textStreamType, nextOldType);
			
//			this.getDocument().notifyAttributeChanged(this, NEXT_WORD_ATTRIBUTE, oldNext);
			this.notifyAttributeChanged(NEXT_WORD_ATTRIBUTE, false, this.nextWord, oldNext);
			
			if (nextOldPrev != null)
//				this.getDocument().notifyAttributeChanged(nextOldPrev, NEXT_WORD_ATTRIBUTE, this.nextWord);
				nextOldPrev.notifyAttributeChanged(NEXT_WORD_ATTRIBUTE, true, nextOldPrev.nextWord, this.nextWord);
//		}
	}
	
	private static void propagateTextStreamProperties(ImWord fromWord, ImWord stopWord, int textStreamPos, String textStreamId, String textStreamType) {
		for (ImWord imw = fromWord; imw != null; imw = imw.nextWord) {
			if (imw == stopWord)
				break; // cycle breaker safety
			imw.textStreamPos = textStreamPos++;
			imw.textStreamId = textStreamId;
			imw.textStreamType = textStreamType;
		}
	}
	
	private static void invalidatePageTextStreamEnds(ImWord word) {
		if (word.getPage() != null)
			word.getPage().invalidateTextStreamEnds();
	}
	
	/**
	 * Retrieve the relation of this image word's string value to the one of its
	 * predecessor. This method always returns one of the four respective
	 * constants defined by this class. If the next word is null, it always
	 * returns <code>NEXT_RELATION_PARAGRAPH_END</code>.
	 * @return the relation to the previous word
	 */
	public char getPreviousRelation() {
		return ((this.prevWord == null) ? NEXT_RELATION_PARAGRAPH_END : this.prevWord.getNextRelation());
	}
	
	/**
	 * Retrieve the relation of this image word's string value to the one of its
	 * successor. This method always returns one of the four respective
	 * constants defined by this class. If the next word is null, it always
	 * returns <code>NEXT_RELATION_PARAGRAPH_END</code>.
	 * @return the relation to the next word
	 */
	public char getNextRelation() {
		return ((this.nextWord == null) ? NEXT_RELATION_PARAGRAPH_END : this.nextRelation);
	}
	
	/**
	 * Set the relation of this word's string value to the one of its successor.
	 * If the argument is none of the four respective constants, it sets the
	 * relation to the default <code>NEXT_RELATION_SEPARATE</code>. While the
	 * next word is null, this method has an effect on the inner state of this
	 * object, but does not reflect in the corresponding getter. This somewhat
	 * odd behavior is to simplify loading and manipulation, namely not to cause
	 * order related manipulation of logical text stream to actually reset any
	 * variables.
	 * @param nextRelation the new relation to the next word
	 */
	public void setNextRelation(char nextRelation) {
		if (nextRelation == this.nextRelation)
			return;
		char oldNextRelation = this.nextRelation;
		if ((NEXT_RELATION_CONTINUE == nextRelation) || (NEXT_RELATION_HYPHENATED == nextRelation) || (NEXT_RELATION_PARAGRAPH_END == nextRelation))
			this.nextRelation = nextRelation;
		else this.nextRelation = NEXT_RELATION_SEPARATE;
		this.notifyAttributeChanged(NEXT_RELATION_ATTRIBUTE, false, Character.toString(this.nextRelation), Character.toString(oldNextRelation));
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#getAttributeNames()
	 */
	public String[] getAttributeNames() {
		String[] ans = super.getAttributeNames();
//		if (this.fontSize != -1) {
//			String[] eAns = new String[ans.length + 1];
//			System.arraycopy(ans, 0, eAns, 0, ans.length);
//			eAns[ans.length] = FONT_SIZE_ATTRIBUTE;
//			Arrays.sort(eAns);
//			ans = eAns;
//		}
		int addAns = 0;
		if (this.fontName != null)
			addAns++;
		if (this.fontSize != -1)
			addAns++;
		if (this.fontCharCodes != null)
			addAns++;
		if ((this.flags & TEXT_DIR_MASK) != 0)
			addAns++;
		if ((this.flags & FONT_PROP_BOLD) != 0)
			addAns++;
		if ((this.flags & FONT_PROP_ITALICS) != 0)
			addAns++;
		if (addAns != 0) {
			String[] eAns = new String[ans.length + addAns];
			System.arraycopy(ans, 0, eAns, 0, ans.length);
			addAns = ans.length;
			if (this.fontName != null)
				eAns[addAns++] = FONT_NAME_ATTRIBUTE;
			if (this.fontSize != -1)
				eAns[addAns++] = FONT_SIZE_ATTRIBUTE;
			if (this.fontCharCodes != null)
				eAns[addAns++] = ImFont.CHARACTER_CODE_STRING_ATTRIBUTE;
			if ((this.flags & TEXT_DIR_MASK) != 0)
				eAns[addAns++] = TEXT_DIRECTION_ATTRIBUTE;
			if ((this.flags & FONT_PROP_BOLD) != 0)
				eAns[addAns++] = BOLD_ATTRIBUTE;
			if ((this.flags & FONT_PROP_ITALICS) != 0)
				eAns[addAns++] = ITALICS_ATTRIBUTE;
			Arrays.sort(eAns);
			ans = eAns;
		}
		return ans;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#getAttribute(java.lang.String)
	 */
	public Object getAttribute(String name) {
		return this.getAttribute(name, null);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#getAttribute(java.lang.String, java.lang.Object)
	 */
	public Object getAttribute(String name, Object def) {
		if (STRING_ATTRIBUTE.equals(name))
			return ((this.string == null) ? def : this.string);
		else if (BOUNDING_BOX_ATTRIBUTE.equals(name))
			return this.bounds;
		else if (PREVIOUS_WORD_ATTRIBUTE.equals(name))
			return ((this.prevWord == null) ? def : this.prevWord);
		else if (NEXT_WORD_ATTRIBUTE.equals(name))
			return ((this.nextWord == null) ? def : this.nextWord);
		else if (NEXT_RELATION_ATTRIBUTE.equals(name))
			return ("" + this.getNextRelation());
		else if (PREVIOUS_RELATION_ATTRIBUTE.equals(name))
			return ("" + this.getPreviousRelation());
		else if (TEXT_STREAM_TYPE_ATTRIBUTE.equals(name))
			return this.textStreamType;
		else if (FONT_NAME_ATTRIBUTE.equals(name))
			return ((this.fontName == null) ? def : this.fontName);
		else if (FONT_SIZE_ATTRIBUTE.equals(name))
			return ((this.fontSize == -1) ? def : ("" + this.fontSize));
		else if (ImFont.CHARACTER_CODE_STRING_ATTRIBUTE.equals(name))
			return ((this.fontCharCodes == null) ? def : this.fontCharCodes);
		else if (TEXT_DIRECTION_ATTRIBUTE.equals(name)) {
			int textDir = (this.flags & TEXT_DIR_MASK);
			return ((textDir == TEXT_DIR_LEFT_RIGHT) ? def : TEXT_DIRS[textDir]);
		}
		else if (BOLD_ATTRIBUTE.equals(name))
			return (((this.flags & FONT_PROP_BOLD) == 0) ? def : "true");
		else if (ITALICS_ATTRIBUTE.equals(name))
			return (((this.flags & FONT_PROP_ITALICS) == 0) ? def : "true");
		else return super.getAttribute(name, def);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#hasAttribute(java.lang.String)
	 */
	public boolean hasAttribute(String name) {
		if (STRING_ATTRIBUTE.equals(name))
			return (this.string != null);
		else if (BOUNDING_BOX_ATTRIBUTE.equals(name))
			return true;
		else if (PREVIOUS_WORD_ATTRIBUTE.equals(name))
			return (this.prevWord != null);
		else if (NEXT_WORD_ATTRIBUTE.equals(name))
			return (this.nextWord != null);
		else if (PREVIOUS_RELATION_ATTRIBUTE.equals(name))
			return true;
		else if (NEXT_RELATION_ATTRIBUTE.equals(name))
			return true;
		else if (TEXT_STREAM_TYPE_ATTRIBUTE.equals(name))
			return true;
		else if (FONT_NAME_ATTRIBUTE.equals(name))
			return (this.fontName != null);
		else if (FONT_SIZE_ATTRIBUTE.equals(name))
			return (this.fontSize != -1);
		else if (ImFont.CHARACTER_CODE_STRING_ATTRIBUTE.equals(name))
			return (this.fontCharCodes != null);
		else if (TEXT_DIRECTION_ATTRIBUTE.equals(name))
			return ((this.flags & TEXT_DIR_MASK) != TEXT_DIR_LEFT_RIGHT);
		else if (BOLD_ATTRIBUTE.equals(name))
			return ((this.flags & FONT_PROP_BOLD) != 0);
		else if (ITALICS_ATTRIBUTE.equals(name))
			return ((this.flags & FONT_PROP_ITALICS) != 0);
		else return super.hasAttribute(name);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#setAttribute(java.lang.String, java.lang.Object)
	 */
	public Object setAttribute(String name, Object value) {
		if (STRING_ATTRIBUTE.equals(name)) {
			String oldString = this.getString();
			this.setString((value == null) ? null : value.toString());
			return oldString;
		}
		else if (BOUNDING_BOX_ATTRIBUTE.equals(name))
			return this.bounds;
		else if (TEXT_STREAM_TYPE_ATTRIBUTE.equals(name)) {
			if (value == null)
				return this.getTextStreamType();
			else return this.setTextStreamType(value.toString());
		}
		else if (NEXT_RELATION_ATTRIBUTE.equals(name)) {
			String oldNextRelation = ("" + this.getNextRelation());
			if ((value != null) && (value.toString().length() == 1))
				this.setNextRelation(value.toString().charAt(0));
			return oldNextRelation;
		}
		else if (PREVIOUS_RELATION_ATTRIBUTE.equals(name)) {
			String oldPrevRelation = ("" + this.getPreviousRelation());
			if ((value != null) && (value.toString().length() == 1) && (this.prevWord != null))
				this.prevWord.setNextRelation(value.toString().charAt(0));
			return oldPrevRelation;
		}
		else if (PREVIOUS_WORD_ATTRIBUTE.equals(name) && ((value == null) || (value instanceof ImWord))) {
			ImWord oldPrev = this.getPreviousWord();
			this.setPreviousWord((ImWord) value);
			return oldPrev;
		}
		else if (PREVIOUS_WORD_ATTRIBUTE.equals(name))
			return this.prevWord;
		else if (NEXT_WORD_ATTRIBUTE.equals(name) && ((value == null) || (value instanceof ImWord))) {
			ImWord oldNext = this.getNextWord();
			this.setNextWord((ImWord) value);
			return oldNext;
		}
		else if (NEXT_WORD_ATTRIBUTE.equals(name))
			return this.nextWord;
		else if (FONT_NAME_ATTRIBUTE.equals(name)) {
			String oldFontName = this.fontName;
			this.setFontName((value == null) ? null : value.toString());
			return oldFontName;
		}
		else if (FONT_SIZE_ATTRIBUTE.equals(name)) {
			String oldFontSize = ((this.fontSize == -1) ? null : ("" + this.fontSize));
			this.setFontSize((value == null) ? null : value.toString());
			return oldFontSize;
		}
		else if (ImFont.CHARACTER_CODE_STRING_ATTRIBUTE.equals(name)) {
			String oldFontCharCodes = this.fontCharCodes;
			this.setFontCharCodes((value == null) ? null : value.toString());
			return oldFontCharCodes;
		}
		else if (TEXT_DIRECTION_ATTRIBUTE.equals(name)) {
			String oldTextDirection = TEXT_DIRS[this.flags & TEXT_DIR_MASK];
			this.flags &= ~TEXT_DIR_MASK; // erase text direction using bitwise inverse of mask
			if (TEXT_DIRECTION_TOP_DOWN.equals(value))
				this.flags |= TEXT_DIR_TOP_DOWN;
			else if (TEXT_DIRECTION_BOTTOM_UP.equals(value))
				this.flags |= TEXT_DIR_BOTTOM_UP;
			else if (TEXT_DIRECTION_RIGHT_LEFT_UPSIDE_DOWN.equals(value))
				this.flags |= TEXT_DIR_RIGHT_LEFT_UPSIDE_DOWN;
			else this.flags |= TEXT_DIR_LEFT_RIGHT; // for completeness ... this thing is 0
			this.notifyAttributeChanged(TEXT_DIRECTION_ATTRIBUTE, true, TEXT_DIRS[this.flags & TEXT_DIR_MASK], oldTextDirection);
			return oldTextDirection;
		}
		else if (BOLD_ATTRIBUTE.equals(name)) {
			String oldBold = Boolean.toString((this.flags & FONT_PROP_BOLD) != 0);
			if ((value == null) || "false".equalsIgnoreCase(value.toString()))
				this.flags &= ~FONT_PROP_BOLD; // erase bold flag using bitwise inverse
			else this.flags |= FONT_PROP_BOLD;
			this.notifyAttributeChanged(BOLD_ATTRIBUTE, true, Boolean.toString((this.flags & FONT_PROP_BOLD) != 0), oldBold);
			return oldBold;
		}
		else if (ITALICS_ATTRIBUTE.equals(name)) {
			String oldItalics = Boolean.toString((this.flags & FONT_PROP_ITALICS) != 0);
			if ((value == null) || "false".equalsIgnoreCase(value.toString()))
				this.flags &= ~FONT_PROP_ITALICS; // erase bold flag using bitwise inverse
			else this.flags |= FONT_PROP_ITALICS;
			this.notifyAttributeChanged(ITALICS_ATTRIBUTE, true, Boolean.toString((this.flags & FONT_PROP_ITALICS) != 0), oldItalics);
			return oldItalics;
		}
		else return super.setAttribute(name, value);
	}
	
	private void notifyAttributeChanged(String name, boolean checkForChange, Object newValue, Object oldValue) {
		if (this.getPage() == null)
			return; // we're detached
		if (checkForChange && ((oldValue == null) ? (newValue == null) : oldValue.equals(newValue)))
			return; // no actual change happened
		this.getDocument().notifyAttributeChanged(this, name, oldValue);
	}
	
	/**
	 * Retrieve the head of the logical text the word belongs to. If the
	 * argument boolean is <code>true</code>, this method returns the first
	 * word of the text stream in the page. Otherwise, it seeks and returns the
	 * global head of the text stream, which may be many pages up the document.
	 * @param inPage find the text stream head in the page or globally?
	 * @return the text stream head
	 */
	public ImWord getTextStreamHead(boolean inPage) {
		ImWord imw = this;
		while (imw.prevWord != null) {
			if (inPage && (imw.pageId != imw.prevWord.pageId))
				break;
			else imw = imw.prevWord;
		}
		return imw;
	}
	
	/**
	 * Retrieve the ID of the logical text stream this word belongs to. This ID
	 * is the local ID of the first word in the text stream, not necessarily on
	 * the same page.
	 * @return the text stream ID
	 */
	public String getTextStreamId() {
		return this.textStreamId;
	}
	
//	/**
//	 * Retrieve the position of the word within the logical text stream it
//	 * belongs to, within this page. For each logical text stream, the
//	 * position restarts at 0 after a page break.
//	 * @return the text stream position
//	 */
	/**
	 * Retrieve the position of the word within the logical text stream it
	 * belongs to. The position is absolute, increasing through the entire text
	 * stream.
	 * @return the text stream position
	 */
	public int getTextStreamPos() {
		return this.textStreamPos;
	}
	
	/**
	 * Retrieve the type of logical text stream this word belongs to.
	 * @return the text stream type
	 */
	public String getTextStreamType() {
		return this.textStreamType;
	}
	
	/**
	 * Set the type of the logical text stream this word belongs to. This
	 * method propagates the update throughout the whole logical text stream. 
	 * @param textStreamType the text stream type to set
	 * @return the old text stream type
	 */
	public String setTextStreamType(String textStreamType) {
		String oldTextStreamType = this.textStreamType;
		this.textStreamType = textStreamType;
		
		if (!oldTextStreamType.equals(textStreamType)) {
			for (ImWord imw = this.prevWord; imw != null; imw = imw.prevWord)
				imw.textStreamType = textStreamType;
			for (ImWord imw = this.nextWord; imw != null; imw = imw.nextWord)
				imw.textStreamType = textStreamType;
			this.notifyAttributeChanged(TEXT_STREAM_TYPE_ATTRIBUTE, false, this.textStreamType, oldTextStreamType);
		}
		
		return oldTextStreamType;
	}
	
	/**
	 * Retrieve the font of the word. This method only returns a non-null value
	 * if the word belongs to an Image Markup document created from a
	 * born-digital source document. It is a shorthand for
	 * <code>getDocument().getFont((String) getAttribute(FONT_NAME_ATTRIBUTE))</code>.
	 * @return the font of the word
	 */
	public ImFont getFont() {
		return this.getDocument().getFont((String) this.getAttribute(FONT_NAME_ATTRIBUTE));
	}
	
	/**
	 * This implementation returns the hash code of the concatenation of the
	 * document ID, page ID, and bounding box, the latter in its string
	 * representation, in compliance with below implementation of
	 * <code>equals()</code>.
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode() {
		if (this.hash == 0)
			this.hash = this.getEqualsString().hashCode();
		return this.hash;
	}
	private int hash = 0;
	
	/**
	 * This implementation returns true if (a) the argument is an instance of
	 * this class and (b) its document ID, page ID, and bounding box equal the
	 * ones of this instance.
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object obj) {
		return ((obj instanceof ImWord) && this.getEqualsString().equals(((ImWord) obj).getEqualsString()));
	}
	private String getEqualsString() {
		if (this.equalsString == null)
			this.equalsString = (this.getDocument().docId + "." + this.pageId + "." + this.bounds.toString());
		return this.equalsString;
	}
	private String equalsString = null;
	
	/**
	 * This implementation returns the string value of the image word, ready for
	 * concatenation. In particular, this means the following: If the relation
	 * to the next word is <code>NEXT_RELATION_CONTINUE</code> or the next word
	 * is null, the string value is returned as is. If the relation to the next
	 * word is <code>NEXT_RELATION_HYPHENATED</code>, any terminal dashes are
	 * truncated from the end of the string value. Finally, if the relation to
	 * the next word is <code>NEXT_RELATION_SEPARATE</code> or
	 * <code>NEXT_RELATION_PARAGRAPH_END</code>, a space is appended to the
	 * string value; this is unless the string value ends with an opening
	 * bracket, or the one of the next word starts with a closing bracket or a
	 * punctuation mark that does not require the insertion of a space. If the
	 * string value of the image word is null, this method returns the empty
	 * string.
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		if (this.string == null)
			return "";
		if ((NEXT_RELATION_CONTINUE == this.nextRelation) || (this.nextWord == null) || ((this.nextWord.string != null) && !Gamta.insertSpace(this.string, this.nextWord.string)))
			return this.string;
		if ((NEXT_RELATION_SEPARATE == this.nextRelation) || (NEXT_RELATION_PARAGRAPH_END == this.nextRelation))
			return (this.string + " ");
		String str = this.string;
		while ((str.length() != 0) && ("-­——".indexOf(str.charAt(str.length()-1)) != -1))
			str = str.substring(0, (str.length()-1));
		return str;
	}
	
	/**
	 * Render a word via a <code>Graphics2D</code> object. The latter argument
	 * has to be translated to the intended rendering position (bottom left
	 * corner of word bounding box) before calling this method, and the intended
	 * rendering color has to be set. However, no scaling is required, as this
	 * method simply scales the argument word's font size (assumed to be the
	 * standard 72 DPI) to the argument output DPI number. The rendering font
	 * is derived from the argument font, based on the font style attributes of
	 * the argument word.
	 * @param word the word to render
	 * @param font the font to derive the rendering font from
	 * @param renderingDpi the rendering resolution
	 * @param zoomToBounds adjust rendering position and font size to completely
	 *            fill the bounding box of the argument word?
	 * @param renderer the graphics object to use for rendering
	 */
	public static void render(ImWord word, Font font, int renderingDpi, boolean zoomToBounds, Graphics2D renderer) {
		int wordDpi = word.getPage().getImageDPI();
		BoundingBox renderingBounds = word.bounds.scale(((float) renderingDpi) / wordDpi);
		
		//	prepare font
		Font preFont = renderer.getFont();
		int wFontSize = -1;
		try {
			wFontSize = Integer.parseInt((String) word.getAttribute(ImWord.FONT_SIZE_ATTRIBUTE, "-1"));
		} catch (NumberFormatException nfe) {}
		if (wFontSize < 1)
			wFontSize = 10;
		int fontSize = Math.round(((float) (wFontSize * renderingDpi)) / 72); // need to scale font size, as we're not scaling renderer proper
		int fontStyle = Font.PLAIN;
		if (word.hasAttribute(ImWord.BOLD_ATTRIBUTE))
			fontStyle = (fontStyle | Font.BOLD);
		if (word.hasAttribute(ImWord.ITALICS_ATTRIBUTE))
			fontStyle = (fontStyle | Font.ITALIC);
		Font renderingFont = font.deriveFont(fontStyle, fontSize);
		
		//	get text orientation
		Object to = word.getAttribute(ImWord.TEXT_DIRECTION_ATTRIBUTE, ImWord.TEXT_DIRECTION_LEFT_RIGHT);
		
		//	adjust font size and vertical position
		int renderingWidth = ((TEXT_DIRECTION_BOTTOM_UP.equals(to) || TEXT_DIRECTION_TOP_DOWN.equals(to)) ? renderingBounds.getHeight() : renderingBounds.getWidth());
		FontRenderContext fontRenderContext = new FontRenderContext(renderer.getTransform(), true, true);
		LineMetrics lineMetrics = renderingFont.getLineMetrics(word.getString(), fontRenderContext);
		TextLayout textLayout = new TextLayout(word.getString(), renderingFont, fontRenderContext);
		if (zoomToBounds) {
			while (textLayout.getBounds().getWidth() < renderingWidth) {
				fontSize++;
				renderingFont = font.deriveFont(fontStyle, fontSize);
				lineMetrics = renderingFont.getLineMetrics(word.getString(), fontRenderContext);
				textLayout = new TextLayout(word.getString(), renderingFont, fontRenderContext);
			}
			while (renderingWidth < textLayout.getBounds().getWidth())  {
				fontSize--;
				renderingFont = font.deriveFont(fontStyle, fontSize);
				lineMetrics = renderingFont.getLineMetrics(word.getString(), fontRenderContext);
				textLayout = new TextLayout(word.getString(), renderingFont, fontRenderContext);
			}
		}
		renderer.setFont(renderingFont);
		
		//	adjust word size and position
		AffineTransform preAt = renderer.getTransform();
		float leftShift = ((float) -textLayout.getBounds().getMinX());
		double hScale = 1;
		
		//	rotate and scale word as required
		if (TEXT_DIRECTION_BOTTOM_UP.equals(to)) {
			renderer.rotate((-Math.PI / 2), (((float) renderingBounds.getWidth()) / 2), -(((float) renderingBounds.getWidth()) / 2));
			if (word.hasAttribute(ImWord.ITALICS_ATTRIBUTE))
				hScale = (((double) renderingBounds.getHeight()) / textLayout.getBounds().getWidth());
			else {
				hScale = (((double) renderingBounds.getHeight()) / textLayout.getAdvance());
				leftShift = 0;
			}
			renderer.scale(1, hScale);
		}
		else if (TEXT_DIRECTION_TOP_DOWN.equals(to)) {
			renderer.rotate((Math.PI / 2), (((float) renderingBounds.getHeight()) / 2), -(((float) renderingBounds.getHeight()) / 2));
			if (word.hasAttribute(ImWord.ITALICS_ATTRIBUTE))
				hScale = (((double) renderingBounds.getHeight()) / textLayout.getBounds().getWidth());
			else {
				hScale = (((double) renderingBounds.getHeight()) / textLayout.getAdvance());
				leftShift = 0;
			}
			renderer.scale(1, hScale);
		}
		else {
			if (word.hasAttribute(ImWord.ITALICS_ATTRIBUTE))
				hScale = (((double) renderingBounds.getWidth()) / textLayout.getBounds().getWidth());
			else {
				hScale = (((double) renderingBounds.getWidth()) / textLayout.getAdvance());
				leftShift = 0;
			}
			renderer.scale(hScale, 1);
		}
		
		//	render word
		try {
			renderer.drawGlyphVector(renderingFont.createGlyphVector(new FontRenderContext(preAt, true, true), word.getString()), leftShift, -Math.round((zoomToBounds && !hasDescender(word)) ? 1 : lineMetrics.getDescent()));
		}
		catch (InternalError ie) {
			ie.printStackTrace(System.out);
		}
		
		//	reset renderer
		renderer.setTransform(preAt);
		renderer.setFont(preFont);
	}
	
	private static boolean hasDescender(ImWord word) {
		String str = word.getString();
		for (int c = 0; c < str.length(); c++) {
			char ch = str.charAt(c);
			if ("gjpqy".indexOf(ch) != -1)
				return true;
			else if (("f".indexOf(ch) != -1) && word.hasAttribute(ImWord.ITALICS_ATTRIBUTE))
				return true;
		}
		return false;
	}
//	
//	public static void main(String[] args) throws Exception {
//		ImDocument doc = new ImDocument("test");
//		ImPage page = new ImPage(doc, 0, new BoundingBox(0, 400, 0, 600)) {
//			public int getImageDPI() {
//				return 192;
//			}
//		};
//		ImWord word = new ImWord(page, new BoundingBox(100, 200, 100, 130), "acewxz");
////		ImWord word = new ImWord(page, new BoundingBox(100, 200, 100, 130), "bdhkl");
////		ImWord word = new ImWord(page, new BoundingBox(100, 200, 100, 130), "bqdp");
//		word.setAttribute(ImWord.FONT_SIZE_ATTRIBUTE, "12");
////		word.setAttribute(ImWord.BOLD_ATTRIBUTE);
////		word.setAttribute(ImWord.ITALICS_ATTRIBUTE);
//		BufferedImage bi = new BufferedImage(200, 60, BufferedImage.TYPE_INT_ARGB);
//		Graphics2D gr = bi.createGraphics();
//		gr.setColor(Color.WHITE);
//		gr.fillRect(0, 0, bi.getWidth(), bi.getHeight());
//		gr.setColor(Color.BLACK);
//		render(word, new Font("Serif", Font.PLAIN, 1), 384, false, gr);
//		ImageDisplayDialog idd = new ImageDisplayDialog("the word");
//		idd.addImage(bi, word.getString());
//		idd.setSize(400, 400);
//		idd.setLocationRelativeTo(null);
//		idd.setVisible(true);
//	}
}