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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.TreeMap;

import de.uka.ipd.idaho.easyIO.util.RandomByteSource;
import de.uka.ipd.idaho.gamta.Attributed;
import de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed;

/**
 * A font in an Image Markup document that originates from a born-digital
 * source, e.g. a born-digital PDF document.
 * 
 * @author sautter
 */
public class ImFont extends AbstractAttributed implements ImObject {
	
	/** the name of the attribute holding the font name */
	public static final String NAME_ATTRIBUTE = "name";
	
	/** the name of the attribute holding the font style */
	public static final String STYLE_ATTRIBUTE = "style";
	
	/** the name of the attribute holding character IDs */
	public static final String CHARACTER_ID_ATTRIBUTE = "charId";
	
	/** the name of the attribute holding character strings */
	public static final String CHARACTER_STRING_ATTRIBUTE = "charString";
	
	/** the name of the attribute holding character images */
	public static final String CHARACTER_IMAGE_ATTRIBUTE = "charImage";
	
	/** the name of the attribute holding character paths */
	public static final String CHARACTER_PATH_ATTRIBUTE = "charPath";
	
	/** the name of the attribute indicating if the font is serif or sans-serif */
	public static final String SERIF_ATTRIBUTE = "serif";
	
	/** the name of the attribute indicating if the font is monospaced */
	public static final String MONOSPACED_ATTRIBUTE = "monospaced";
	
	/** the character face type indicating a sans-serif (gothic) font */
	public static final byte CHAR_FACE_TYPE_SANS_SERIF = ((byte) 'G');
	
	/** the character face type indicating a serif (roman) font */
	public static final byte CHAR_FACE_TYPE_SERIF = ((byte) 'S');
	
	/** the character face type indicating a monospaced font */
	public static final byte CHAR_FACE_TYPE_MONOSPACED = ((byte) 'M');
	
	/** the name of the attribute to a word storing the font specific char codes the word was composed from */
	public static final String CHARACTER_CODE_STRING_ATTRIBUTE = "fontCharCodes";
	
	/** the name of the attribute indicating the number of HEX digits in the char code of an individual character */
	public static final String CHARACTER_CODE_LENGTH_ATTRIBUTE = "charCodeLength";
	
	/** the name of the attribute indicating that the individual characters in the font vary in their face type and style (e.g. a mix of bold and normal characters) */
	public static final String MIXED_STYLE_ATTRIBUTE = "mixedStyle";
	
	/** the name of the attribute indicating that the font represents vectorized pieces of text extracted from a scan (e.g. DjVu) rather than carefully designed gylphs */
	public static final String VECTORIZED_ATTRIBUTE = "vectorized";
	
	/*
TODO Representing DjVu fonts in IMF (FontEditorProvider):
- moves assignment of properties from overall font to individual chars in font editor
==> should also help with bunch of oddjob fonts mixing chars of various styles
	 */
	
	
	private ImDocument doc;
	
	/** the name of the font */
	public final String name;
	
	private String luid = null;
	private String uuid = null;
	
	private boolean bold;
	private boolean italics;
	private byte charFaceType;
	
	private boolean mixedStyle = false;
	private int charCodeLength = 2; // the default, hard coded thus far
	
	private TreeMap characters = new TreeMap();
	
	/** Constructor
	 * @param doc the Image Markup document the font belongs to
	 * @param name the font name
	 */
	public ImFont(ImDocument doc, String name) {
		this(doc, name, false, false, true, false);
	}
	
	/** Constructor
	 * @param doc the Image Markup document the font belongs to
	 * @param name the font name
	 * @param bold is the font bold?
	 * @param italics is the font in italics?
	 * @param serif is the font serif or sans-serif?
	 */
	public ImFont(ImDocument doc, String name, boolean bold, boolean italics, boolean serif) {
		this(doc, name, bold, italics, serif, false);
	}
	
	/** Constructor
	 * @param doc the Image Markup document the font belongs to
	 * @param name the font name
	 * @param bold is the font bold?
	 * @param italics is the font in italics?
	 * @param serif is the font serif or sans-serif?
	 * @param monospaced is the font monospaced or not?
	 */
	public ImFont(ImDocument doc, String name, boolean bold, boolean italics, boolean serif, boolean monospaced) {
		this(doc, name, bold, italics, ((monospaced ? CHAR_FACE_TYPE_MONOSPACED : (serif ? CHAR_FACE_TYPE_SERIF : CHAR_FACE_TYPE_SANS_SERIF))));
	}
	
	/** Constructor
	 * @param doc the Image Markup document the font belongs to
	 * @param name the font name
	 * @param bold is the font bold?
	 * @param italics is the font in italics?
	 * @param charForm the char type (serif, sans-serif, or monospaced)
	 */
	public ImFont(ImDocument doc, String name, boolean bold, boolean italics, byte charForm) {
		this.doc = doc;
		this.name = name;
		this.bold = bold;
		this.italics = italics;
		this.charFaceType = charForm;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.gamta.Attributed#getAttribute(java.lang.String)
	 */
	public Object getAttribute(String name) {
		return this.getAttribute(name, null);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.gamta.Attributed#getAttribute(java.lang.String, java.lang.Object)
	 */
	public Object getAttribute(String name, Object def) {
		if (BOLD_ATTRIBUTE.equals(name))
			return new Boolean(this.isBold());
		else if (ITALICS_ATTRIBUTE.equals(name))
			return new Boolean(this.isItalics());
		else if (SERIF_ATTRIBUTE.equals(name))
			return new Boolean(this.isSerif());
		else if (MONOSPACED_ATTRIBUTE.equals(name))
			return new Boolean(this.isMonospaced());
		else if (CHARACTER_CODE_LENGTH_ATTRIBUTE.equals(name))
			return new Integer(this.charCodeLength);
		else if (MIXED_STYLE_ATTRIBUTE.equals(name))
			return new Boolean(this.isMixedStyle());
		else if (name.startsWith(CHARACTER_STRING_ATTRIBUTE + "-")) {
			int charId = Integer.parseInt(name.substring((CHARACTER_STRING_ATTRIBUTE + "-").length()));
			return this.getString(charId);
		}
		else if (name.startsWith(CHARACTER_IMAGE_ATTRIBUTE + "-")) {
			int charId = Integer.parseInt(name.substring((CHARACTER_IMAGE_ATTRIBUTE + "-").length()));
			return this.getImage(charId);
		}
		else if (name.startsWith(CHARACTER_PATH_ATTRIBUTE + "-")) {
			int charId = Integer.parseInt(name.substring((CHARACTER_PATH_ATTRIBUTE + "-").length()));
			return this.getPath(charId);
		}
		else if (name.startsWith(BOLD_ATTRIBUTE + "-")) {
			int charId = Integer.parseInt(name.substring((BOLD_ATTRIBUTE + "-").length()));
			return new Boolean(this.isBold(charId));
		}
		else if (name.startsWith(ITALICS_ATTRIBUTE + "-")) {
			int charId = Integer.parseInt(name.substring((ITALICS_ATTRIBUTE + "-").length()));
			return new Boolean(this.isItalics(charId));
		}
		else if (name.startsWith(SERIF_ATTRIBUTE + "-")) {
			int charId = Integer.parseInt(name.substring((SERIF_ATTRIBUTE + "-").length()));
			return new Boolean(this.isSerif(charId));
		}
		else if (name.startsWith(MONOSPACED_ATTRIBUTE + "-")) {
			int charId = Integer.parseInt(name.substring((MONOSPACED_ATTRIBUTE + "-").length()));
			return new Boolean(this.isMonospaced(charId));
		}
		else return super.getAttribute(name, def);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.gamta.Attributed#hasAttribute(java.lang.String)
	 */
	public boolean hasAttribute(String name) {
		if (BOLD_ATTRIBUTE.equals(name) && this.bold)
			return true;
		else if (ITALICS_ATTRIBUTE.equals(name) && this.italics)
			return true;
		else if (SERIF_ATTRIBUTE.equals(name) && this.isSerif())
			return true;
		else if (MONOSPACED_ATTRIBUTE.equals(name) && this.isMonospaced())
			return true;
		else if (CHARACTER_CODE_LENGTH_ATTRIBUTE.equals(name))
			return (this.charCodeLength != 2);
		else if (MIXED_STYLE_ATTRIBUTE.equals(name) && this.mixedStyle)
			return true;
		else if (name.startsWith(BOLD_ATTRIBUTE + "-")) {
			int charId = Integer.parseInt(name.substring((BOLD_ATTRIBUTE + "-").length()));
			return this.isBold(charId);
		}
		else if (name.startsWith(ITALICS_ATTRIBUTE + "-")) {
			int charId = Integer.parseInt(name.substring((ITALICS_ATTRIBUTE + "-").length()));
			return this.isItalics(charId);
		}
		else if (name.startsWith(SERIF_ATTRIBUTE + "-")) {
			int charId = Integer.parseInt(name.substring((SERIF_ATTRIBUTE + "-").length()));
			return this.isSerif(charId);
		}
		else if (name.startsWith(MONOSPACED_ATTRIBUTE + "-")) {
			int charId = Integer.parseInt(name.substring((MONOSPACED_ATTRIBUTE + "-").length()));
			return this.isMonospaced(charId);
		}
		else return super.hasAttribute(name);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.gamta.defaultImplementation.AbstractAttributed#getAttributeNames()
	 */
	public String[] getAttributeNames() {
		String[] ans = super.getAttributeNames();
		if (this.charCodeLength == 2)
			return ans;
		String[] cclAns = new String[ans.length + 1];
		System.arraycopy(ans, 0, cclAns, 0, ans.length);
		cclAns[ans.length] = CHARACTER_CODE_LENGTH_ATTRIBUTE;
		Arrays.sort(cclAns);
		return cclAns;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.gamta.Attributed#setAttribute(java.lang.String)
	 */
	public void setAttribute(String name) {
		if (BOLD_ATTRIBUTE.equals(name))
			this.setBold(true);
		else if (ITALICS_ATTRIBUTE.equals(name))
			this.setItalics(true);
		else if (SERIF_ATTRIBUTE.equals(name))
			this.setSerif(true);
		else if (MONOSPACED_ATTRIBUTE.equals(name))
			this.setMonospaced(true);
		else if (MIXED_STYLE_ATTRIBUTE.equals(name))
			this.setMixedStyle(true);
		else super.setAttribute(name);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.gamta.Attributed#setAttribute(java.lang.String, java.lang.Object)
	 */
	public Object setAttribute(String name, Object value) {
		if (BOLD_ATTRIBUTE.equals(name)) {
			boolean wasBold = this.isBold();
			this.setBold(value != null);
			return new Boolean(wasBold);
		}
		else if (ITALICS_ATTRIBUTE.equals(name)) {
			boolean wasItalics = this.isItalics();
			this.setItalics(value != null);
			return new Boolean(wasItalics);
		}
		else if (SERIF_ATTRIBUTE.equals(name)) {
			boolean wasSerif = this.isSerif();
			this.setSerif(value != null);
			return new Boolean(wasSerif);
		}
		else if (MONOSPACED_ATTRIBUTE.equals(name)) {
			boolean wasMonospaced = this.isMonospaced();
			this.setMonospaced(value != null);
			return new Boolean(wasMonospaced);
		}
		else if (CHARACTER_CODE_LENGTH_ATTRIBUTE.equals(name)) {
			if (value == null)
				return this.removeAttribute(CHARACTER_CODE_LENGTH_ATTRIBUTE);
			int oldCcl = this.charCodeLength;
			this.setCharCodeLength((value instanceof Number) ? ((Number) value).intValue() : Integer.parseInt(value.toString()));
			return new Integer(oldCcl);
		}
		else if (MIXED_STYLE_ATTRIBUTE.equals(name)) {
			boolean wasMixedStyle = this.isMixedStyle();
			this.setMixedStyle(value != null);
			return new Boolean(wasMixedStyle);
		}
		else if (name.startsWith(CHARACTER_STRING_ATTRIBUTE + "-") && (value instanceof String)) {
			int charId = Integer.parseInt(name.substring((CHARACTER_STRING_ATTRIBUTE + "-").length()));
			String oCharStr = this.getString(charId);
			this.addCharacter(charId, ((String) value), ((BufferedImage) null), null);
			return oCharStr;
		}
		else if (name.startsWith(CHARACTER_IMAGE_ATTRIBUTE + "-") && (value instanceof BufferedImage)) {
			int charId = Integer.parseInt(name.substring((CHARACTER_IMAGE_ATTRIBUTE + "-").length()));
			BufferedImage oCharImage = this.getImage(charId);
			this.addCharacter(charId, null, ((BufferedImage) value), null);
			return oCharImage;
		}
		else if (name.startsWith(CHARACTER_PATH_ATTRIBUTE + "-") && (value instanceof Path2D.Float)) {
			int charId = Integer.parseInt(name.substring((CHARACTER_PATH_ATTRIBUTE + "-").length()));
			Path2D.Float oCharPath = this.getPath(charId);
			this.addCharacter(charId, null, ((BufferedImage) null), ((Path2D.Float) value));
			return oCharPath;
		}
		else if (name.startsWith(BOLD_ATTRIBUTE + "-")) {
			int charId = Integer.parseInt(name.substring((BOLD_ATTRIBUTE + "-").length()));
			boolean wasBold = this.isBold(charId);
			this.setBold((value != null), charId);
			return new Boolean(wasBold);
		}
		else if (name.startsWith(ITALICS_ATTRIBUTE + "-")) {
			int charId = Integer.parseInt(name.substring((ITALICS_ATTRIBUTE + "-").length()));
			boolean wasItalics = this.isItalics(charId);
			this.setItalics((value != null), charId);
			return new Boolean(wasItalics);
		}
		else if (name.startsWith(SERIF_ATTRIBUTE + "-")) {
			int charId = Integer.parseInt(name.substring((SERIF_ATTRIBUTE + "-").length()));
			boolean wasSerif = this.isSerif(charId);
			this.setSerif((value != null), charId);
			return new Boolean(wasSerif);
		}
		else if (name.startsWith(MONOSPACED_ATTRIBUTE + "-")) {
			int charId = Integer.parseInt(name.substring((MONOSPACED_ATTRIBUTE + "-").length()));
			boolean wasMonospaced = this.isMonospaced(charId);
			this.setMonospaced((value != null), charId);
			return new Boolean(wasMonospaced);
		}
		else {
			Object oldValue = super.setAttribute(name, value);
			if (this.doc != null)
				this.doc.notifyAttributeChanged(this, name, oldValue);
			return oldValue;
		}
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.gamta.Attributed#copyAttributes(de.uka.ipd.idaho.gamta.Attributed)
	 */
	public void copyAttributes(Attributed source) { /* we're not doing this here, as attributes are only to allow for generic undo management */ }
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.gamta.Attributed#removeAttribute(java.lang.String)
	 */
	public Object removeAttribute(String name) {
		if (BOLD_ATTRIBUTE.equals(name)) {
			boolean wasBold = this.isBold();
			this.setBold(false);
			return new Boolean(wasBold);
		}
		else if (ITALICS_ATTRIBUTE.equals(name)) {
			boolean wasItalics = this.isItalics();
			this.setItalics(false);
			return new Boolean(wasItalics);
		}
		else if (SERIF_ATTRIBUTE.equals(name)) {
			boolean wasSerif = this.isSerif();
			this.setSerif(false);
			return new Boolean(wasSerif);
		}
		else if (MONOSPACED_ATTRIBUTE.equals(name)) {
			boolean wasMonospaced = this.isMonospaced();
			this.setMonospaced(false);
			return new Boolean(wasMonospaced);
		}
		else if (CHARACTER_CODE_LENGTH_ATTRIBUTE.equals(name)) {
			int oldCcl = this.charCodeLength;
			this.setCharCodeLength(2);
			return new Integer(oldCcl);
		}
		else if (MIXED_STYLE_ATTRIBUTE.equals(name)) {
			boolean wasMixedStyle = this.isMixedStyle();
			this.setMixedStyle(false);
			return new Boolean(wasMixedStyle);
		}
		else if (name.startsWith(BOLD_ATTRIBUTE + "-")) {
			int charId = Integer.parseInt(name.substring((BOLD_ATTRIBUTE + "-").length()));
			boolean wasBold = this.isBold(charId);
			this.setBold(false, charId);
			return new Boolean(wasBold);
		}
		else if (name.startsWith(ITALICS_ATTRIBUTE + "-")) {
			int charId = Integer.parseInt(name.substring((ITALICS_ATTRIBUTE + "-").length()));
			boolean wasItalics = this.isItalics(charId);
			this.setItalics(false, charId);
			return new Boolean(wasItalics);
		}
		else if (name.startsWith(SERIF_ATTRIBUTE + "-")) {
			int charId = Integer.parseInt(name.substring((SERIF_ATTRIBUTE + "-").length()));
			boolean wasSerif = this.isSerif(charId);
			this.setSerif(false, charId);
			return new Boolean(wasSerif);
		}
		else if (name.startsWith(MONOSPACED_ATTRIBUTE + "-")) {
			int charId = Integer.parseInt(name.substring((MONOSPACED_ATTRIBUTE + "-").length()));
			boolean wasMonospaced = this.isMonospaced(charId);
			this.setMonospaced(false, charId);
			return new Boolean(wasMonospaced);
		}
		else {
			Object oldValue = super.removeAttribute(name);
			if (this.doc != null)
				this.doc.notifyAttributeChanged(this, name, oldValue);
			return oldValue;
		}
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.gamta.Attributed#clearAttributes()
	 */
	public void clearAttributes() { /* we're not doing this here, as attributes are only to allow for generic undo management */ }
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImObject#getType()
	 */
	public String getType() {
		return "font";
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImObject#setType(java.lang.String)
	 */
	public void setType(String type) {}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImObject#getLocalID()
	 */
	public String getLocalID() {
		return this.name;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImObject#getLocalUID()
	 */
	public String getLocalUID() {
		if (this.luid == null) {
			String name = this.name;
			while (name.length() < 10)
				name = (name + name);
			this.luid = (UuidHelper.asHex(this.name.hashCode(), 4) +
					UuidHelper.asHex(-1, 2) +
					String.valueOf(RandomByteSource.getHexCode(name.substring(0, 4).getBytes())) +
					String.valueOf(RandomByteSource.getHexCode(name.substring(name.length() - 6).getBytes())) // PDF fonts tend to have these suffixes of 6 random letters
				);
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
		return ((this.doc == null) ? null : this.doc.getDocumentProperty(propertyName, defaultValue));
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImObject#getDocumentPropertyNames()
	 */
	public String[] getDocumentPropertyNames() {
		return ((this.doc == null) ? null : this.doc.getDocumentPropertyNames());
	}
	
	/**
	 * Test whether or not the font is mixed-style, i.e., individual characters
	 * can differ in face type or style.
	 * @return true if the font is mixed-style
	 */
	public boolean isMixedStyle() {
		return this.mixedStyle;
	}
	
	/**
	 * Set the font to mixed-style or uniform style. In a mixed-style font,
	 * face type and style can be set for individual characters. This is mainly
	 * to help accommodate respective fonts that occasionally occur in digital
	 * source documents, but also for fonts whose characters are vectorized
	 * parts of page images (e.g. DjVu).
	 * @param mixedStyle should the font accommodate mixed characters styles?
	 */
	public void setMixedStyle(boolean mixedStyle) {
		if (mixedStyle == this.mixedStyle)
			return;
		if (mixedStyle) {
			
			//	switch to mixed mode before transfer of global properties to glyphs (would cause errors on UNDO otherwise)
			this.mixedStyle = true;
			if (this.doc != null)
				this.doc.notifyAttributeChanged(this, MIXED_STYLE_ATTRIBUTE, null);
			
			//	write global properties to glyphs now that we're open
			ArrayList chars = new ArrayList(this.characters.values());
			for (int c = 0; c < chars.size(); c++) {
				ImCharacter chr = ((ImCharacter) chars.get(c));
				this.setBold(this.bold, chr);
				this.setItalics(this.italics, chr);
				if (this.charFaceType == CHAR_FACE_TYPE_SERIF)
					this.setSerif(true, chr);
				else if (this.charFaceType == CHAR_FACE_TYPE_MONOSPACED)
					this.setMonospaced(true, chr);
				else {
					this.setSerif(false, chr);
					this.setMonospaced(false, chr);
				}
			}
		}
		else {
			
			//	write glyph properties to global ones while we're still in voting mode
			this.setBold(this.isBold()); // looks funny, but writes majority vote to global property setter
			this.setItalics(this.isItalics()); // looks funny, but writes majority vote to global property setter
			if (this.isSerif()) // looks funny, but writes majority vote to global property setter
				this.setSerif(true);
			else if (this.isMonospaced()) // looks funny, but writes majority vote to global property setter
				this.setMonospaced(true);
			else {
				this.setSerif(false);
				this.setMonospaced(false);
			}
			
			//	switch to uniform mode after transfer of glyph majority vote to global properties (would cause errors on UNDO otherwise)
			this.mixedStyle = false;
			if (this.doc != null)
				this.doc.notifyAttributeChanged(this, MIXED_STYLE_ATTRIBUTE, "true");
		}
	}
	
	/**
	 * Test whether or not the font is bold. In a mixed-style font, this method
	 * returns true if there are more bold characters than others.
	 * @return true if the font is bold, false otherwise
	 */
	public boolean isBold() {
		if (this.mixedStyle) {
			int boldCount = 0;
			ArrayList chrs = new ArrayList(this.characters.values());
			for (int c = 0; c < chrs.size(); c++) {
				if (((ImCharacter) chrs.get(c)).isBold())
					boldCount++;
			}
			return (chrs.size() < (boldCount * 2));
		}
		else return this.bold;
	}
	
	/**
	 * Test whether or not the character with a given ID is bold. If the font
	 * is mixed-style and contains no character with the argument ID, this
	 * method returns false.
	 * @param charId the font-local ID of the character to test
	 * @return true if the character with the argument ID is bold
	 */
	public boolean isBold(int charId) {
		if (this.mixedStyle) {
			ImCharacter chr = ((ImCharacter) this.characters.get(new Integer(charId)));
			return ((chr != null) && chr.isBold());
		}
		else return this.bold;
	}
	
	/**
	 * Mark the font as bold or non-bold.
	 * @param bold is the font bold?
	 */
	public void setBold(boolean bold) {
		if (this.bold == bold)
			return;
		this.bold = bold;
//		//	ONLY EVER UPDATE GLYPHS WHEN SWITCHING TO MIXED MODE
//		ArrayList chrs = new ArrayList(this.characters.values());
//		for (int c = 0; c < chrs.size(); c++)
//			((ImCharacter) chrs.get(c)).setBold(bold);
//		//	ONLY EVER UPDATE GLYPHS WHEN SWITCHING TO MIXED MODE
		if (this.doc != null)
			this.doc.notifyAttributeChanged(this, BOLD_ATTRIBUTE, (this.bold ? null : "true"));
	}
	
	/**
	 * Mark an individual character in a mixed-style font as bold or non-bold.
	 * If the font is not a mixed-style font, this method throws an
	 * <code>IllegalStateException</code>. If no character exists for the
	 * argument ID, this method does nothing.
	 * @param charId the font-local ID of the character to modify
	 * @param bold is the character with the argument ID bold?
	 */
	public void setBold(boolean bold, int charId) {
		if (!this.mixedStyle)
			throw new IllegalStateException("Can only set style of individual characters in mixed mode");
		ImCharacter chr = ((ImCharacter) this.characters.get(new Integer(charId)));
		if (chr != null)
			this.setBold(bold, chr);
//		if (chr == null)
//			return;
//		if (chr.isBold() == bold)
//			return;
//		chr.setBold(bold);
//		if (this.doc != null)
//			this.doc.notifyAttributeChanged(this, (BOLD_ATTRIBUTE + "-" + charId), (chr.isBold() ? null : "true"));
	}
	private void setBold(boolean bold, ImCharacter chr) {
		if (chr.isBold() == bold)
			return;
		chr.setBold(bold);
		if (this.doc != null)
			this.doc.notifyAttributeChanged(this, (BOLD_ATTRIBUTE + "-" + chr.id), (chr.isBold() ? null : "true"));
	}
	
	/**
	 * Test whether or not the font is in italics. In a mixed-style font, this
	 * method returns true if there are more characters in italics than are not.
	 * @return true if the font is in italics, false otherwise
	 */
	public boolean isItalics() {
		if (this.mixedStyle) {
			int italicsCount = 0;
			ArrayList chrs = new ArrayList(this.characters.values());
			for (int c = 0; c < chrs.size(); c++) {
				if (((ImCharacter) chrs.get(c)).isItalics())
					italicsCount++;
			}
			return (chrs.size() < (italicsCount * 2));
		}
		else return this.italics;
	}
	
	/**
	 * Test whether or not the character with a given ID is in italics. If the
	 * font is mixed-style and contains no character with the argument ID, this
	 * method returns false.
	 * @param charId the font-local ID of the character to test
	 * @return true if the character with the argument ID is in italics
	 */
	public boolean isItalics(int charId) {
		if (this.mixedStyle) {
			ImCharacter chr = ((ImCharacter) this.characters.get(new Integer(charId)));
			return ((chr != null) && chr.isItalics());
		}
		else return this.italics;
	}
	
	/**
	 * Mark the font as italics or non-italics.
	 * @param italics is the font in italics?
	 */
	public void setItalics(boolean italics) {
		if (this.italics == italics)
			return;
		this.italics = italics;
//		//	ONLY EVER UPDATE GLYPHS WHEN SWITCHING TO MIXED MODE
//		ArrayList chrs = new ArrayList(this.characters.values());
//		for (int c = 0; c < chrs.size(); c++)
//			((ImCharacter) chrs.get(c)).setItalics(italics);
//		//	ONLY EVER UPDATE GLYPHS WHEN SWITCHING TO MIXED MODE
		if (this.doc != null)
			this.doc.notifyAttributeChanged(this, ITALICS_ATTRIBUTE, (this.italics ? null : "true"));
	}
	
	/**
	 * Mark an individual character in a mixed-style font as italics or
	 * non-italics. If the font is not a mixed-style font, this method
	 * throws an <code>IllegalStateException</code>. If no character
	 * exists for the argument ID, this method does nothing.
	 * @param charId the font-local ID of the character to modify
	 * @param italics is the character with the argument ID in italics?
	 */
	public void setItalics(boolean italics, int charId) {
		if (!this.mixedStyle)
			throw new IllegalStateException("Can only set style of individual characters in mixed mode");
		ImCharacter chr = ((ImCharacter) this.characters.get(new Integer(charId)));
		if (chr != null)
			this.setItalics(italics, chr);
//		if (chr == null)
//			return;
//		if (chr.isItalics() == italics)
//			return;
//		chr.setItalics(italics);
//		if (this.doc != null)
//			this.doc.notifyAttributeChanged(this, (ITALICS_ATTRIBUTE + "-" + charId), (chr.isItalics() ? null : "true"));
	}
	private void setItalics(boolean italics, ImCharacter chr) {
		if (chr.isItalics() == italics)
			return;
		chr.setItalics(italics);
		if (this.doc != null)
			this.doc.notifyAttributeChanged(this, (ITALICS_ATTRIBUTE + "-" + chr.id), (chr.isItalics() ? null : "true"));
	}
	
	/**
	 * Test whether or not the font is in a serif face.
	 * @return true if the font is in a serif face
	 */
	public boolean isSerif() {
		if (this.mixedStyle) {
			int sansCount = 0;
			int serifCount = 0;
			int monoCount = 0;
			ArrayList chrs = new ArrayList(this.characters.values());
			for (int c = 0; c < chrs.size(); c++) {
				if (((ImCharacter) chrs.get(c)).isSerif())
					serifCount++;
				else if (((ImCharacter) chrs.get(c)).isMonospaced())
					monoCount++;
				else sansCount++;
			}
			return ((sansCount < serifCount) && (monoCount < serifCount));
		}
		else return (this.charFaceType == CHAR_FACE_TYPE_SERIF);
	}
	
	/**
	 * Test whether or not the character with a given ID is in a serif face. If
	 * the font is mixed-style and contains no character with the argument ID,
	 * this method returns false.
	 * @param charId the font-local ID of the character to test
	 * @return true if the character with the argument ID is in a serif face
	 */
	public boolean isSerif(int charId) {
		if (this.mixedStyle) {
			ImCharacter chr = ((ImCharacter) this.characters.get(new Integer(charId)));
			return ((chr != null) && chr.isSerif());
		}
		else return (this.charFaceType == CHAR_FACE_TYPE_SERIF);
	}
	
	/**
	 * Mark the font as being in a serif or sans-serif face. This property
	 * overwrites the monospaced property if set to <code>true</code>.
	 * @param serif is the font in a serif face?
	 */
	public void setSerif(boolean serif) {
		if (serif == this.isSerif())
			return;
//		byte cf;
		if (serif) {
			if (this.isMonospaced() && (this.doc != null))
				this.doc.notifyAttributeChanged(this, MONOSPACED_ATTRIBUTE, "true");
			this.charFaceType = CHAR_FACE_TYPE_SERIF;
//			cf = ImCharacter.serif;
			if (this.doc != null)
				this.doc.notifyAttributeChanged(this, SERIF_ATTRIBUTE, null);
		}
		else {
			this.charFaceType = CHAR_FACE_TYPE_SANS_SERIF;
//			cf = ImCharacter.sansSerif;
			if (this.doc != null)
				this.doc.notifyAttributeChanged(this, SERIF_ATTRIBUTE, "true");
		}
//		//	ONLY EVER UPDATE GLYPHS WHEN SWITCHING TO MIXED MODE
//		ArrayList chrs = new ArrayList(this.characters.values());
//		for (int c = 0; c < chrs.size(); c++)
//			((ImCharacter) chrs.get(c)).setCharForm(cf);
//		//	ONLY EVER UPDATE GLYPHS WHEN SWITCHING TO MIXED MODE
	}
	
	/**
	 * Mark an individual character in a mixed-style font as serif or
	 * sans-serif. If the font is not a mixed-style font, this method
	 * throws an <code>IllegalStateException</code>. If no character
	 * exists for the argument ID, this method does nothing.
	 * @param charId the font-local ID of the character to modify
	 * @param serif is the character with the argument ID in a serif face?
	 */
	public void setSerif(boolean serif, int charId) {
		if (!this.mixedStyle)
			throw new IllegalStateException("Can only set style of individual characters in mixed mode");
		ImCharacter chr = ((ImCharacter) this.characters.get(new Integer(charId)));
		if (chr != null)
			this.setSerif(serif, chr);
//		if (chr == null)
//			return;
//		if (chr.isSerif() == serif)
//			return;
//		if (serif) {
//			if (chr.isMonospaced() && (this.doc != null))
//				this.doc.notifyAttributeChanged(this, (MONOSPACED_ATTRIBUTE + "-" + charId), "true");
//			chr.setCharForm(ImCharacter.serif);
//			if (this.doc != null)
//				this.doc.notifyAttributeChanged(this, (SERIF_ATTRIBUTE + "-" + charId), null);
//		}
//		else {
//			chr.setCharForm(ImCharacter.sansSerif);
//			if (this.doc != null)
//				this.doc.notifyAttributeChanged(this, (SERIF_ATTRIBUTE + "-" + charId), "true");
//		}
	}
	private void setSerif(boolean serif, ImCharacter chr) {
		if (chr.isSerif() == serif)
			return;
		if (serif) {
			if (chr.isMonospaced() && (this.doc != null))
				this.doc.notifyAttributeChanged(this, (MONOSPACED_ATTRIBUTE + "-" + chr.id), "true");
			chr.setCharForm(ImCharacter.serif);
			if (this.doc != null)
				this.doc.notifyAttributeChanged(this, (SERIF_ATTRIBUTE + "-" + chr.id), null);
		}
		else {
			chr.setCharForm(ImCharacter.sansSerif);
			if (this.doc != null)
				this.doc.notifyAttributeChanged(this, (SERIF_ATTRIBUTE + "-" + chr.id), "true");
		}
	}
	
	/**
	 * Test whether or not the font is in a monospaced face.
	 * @return true if the font is in a monospaced face
	 */
	public boolean isMonospaced() {
		if (this.mixedStyle) {
			int sansCount = 0;
			int serifCount = 0;
			int monoCount = 0;
			ArrayList chrs = new ArrayList(this.characters.values());
			for (int c = 0; c < chrs.size(); c++) {
				if (((ImCharacter) chrs.get(c)).isSerif())
					serifCount++;
				else if (((ImCharacter) chrs.get(c)).isMonospaced())
					monoCount++;
				else sansCount++;
			}
			return ((sansCount < monoCount) && (serifCount < monoCount));
		}
		else return (this.charFaceType == CHAR_FACE_TYPE_MONOSPACED);
	}
	
	/**
	 * Test whether or not the character with a given ID is in a monospaced
	 * face. If the font is mixed-style and contains no character with the
	 * argument ID, this method returns false.
	 * @param charId the font-local ID of the character to test
	 * @return true if the character with the argument ID is in a monospaced face
	 */
	public boolean isMonospaced(int charId) {
		if (this.mixedStyle) {
			ImCharacter chr = ((ImCharacter) this.characters.get(new Integer(charId)));
			return ((chr != null) && chr.isMonospaced());
		}
		else return (this.charFaceType == CHAR_FACE_TYPE_MONOSPACED);
	}
	
	/**
	 * Mark the font as being or not being in a monospaced face. This property
	 * overwrites the serif property if set to <code>true</code>.
	 * @param monospaced is the font monospaced?
	 */
	public void setMonospaced(boolean monospaced) {
		if (monospaced == this.isMonospaced())
			return;
//		byte cf;
		if (monospaced) {
			if (this.isSerif() && (this.doc != null))
				this.doc.notifyAttributeChanged(this, SERIF_ATTRIBUTE, "true");
			this.charFaceType = CHAR_FACE_TYPE_MONOSPACED;
//			cf = ImCharacter.monospaced;
			if (this.doc != null)
				this.doc.notifyAttributeChanged(this, MONOSPACED_ATTRIBUTE, null);
		}
		else {
			this.charFaceType = CHAR_FACE_TYPE_SANS_SERIF;
//			cf = ImCharacter.sansSerif;
			if (this.doc != null)
				this.doc.notifyAttributeChanged(this, MONOSPACED_ATTRIBUTE, "true");
		}
//		//	ONLY EVER UPDATE GLYPHS WHEN SWITCHING TO MIXED MODE
//		ArrayList chrs = new ArrayList(this.characters.values());
//		for (int c = 0; c < chrs.size(); c++)
//			((ImCharacter) chrs.get(c)).setCharForm(cf);
//		//	ONLY EVER UPDATE GLYPHS WHEN SWITCHING TO MIXED MODE
	}
	
	/**
	 * Mark an individual character in a mixed-style font as being or not being
	 * in a monospaced face sans-serif. If the font is not a mixed-style font,
	 * this method throws an <code>IllegalStateException</code>. If no character
	 * exists for the argument ID, this method does nothing.
	 * @param charId the font-local ID of the character to modify
	 * @param monospaced is the character with the argument ID in a monospaced face?
	 */
	public void setMonospaced(boolean monospaced, int charId) {
		if (!this.mixedStyle)
			throw new IllegalStateException("Can only set style of individual characters in mixed mode");
		ImCharacter chr = ((ImCharacter) this.characters.get(new Integer(charId)));
		if (chr != null)
			this.setMonospaced(monospaced, chr);
//		if (chr == null)
//			return;
//		if (chr.isMonospaced() == monospaced)
//			return;
//		if (monospaced) {
//			if (chr.isSerif() && (this.doc != null))
//				this.doc.notifyAttributeChanged(this, (SERIF_ATTRIBUTE + "-" + charId), "true");
//			chr.setCharForm(ImCharacter.monospaced);
//			if (this.doc != null)
//				this.doc.notifyAttributeChanged(this, (MONOSPACED_ATTRIBUTE + "-" + charId), null);
//		}
//		else {
//			chr.setCharForm(ImCharacter.sansSerif);
//			if (this.doc != null)
//				this.doc.notifyAttributeChanged(this, (MONOSPACED_ATTRIBUTE + "-" + charId), "true");
//		}
	}
	private void setMonospaced(boolean monospaced, ImCharacter chr) {
		if (chr.isMonospaced() == monospaced)
			return;
		if (monospaced) {
			if (chr.isSerif() && (this.doc != null))
				this.doc.notifyAttributeChanged(this, (SERIF_ATTRIBUTE + "-" + chr.id), "true");
			chr.setCharForm(ImCharacter.monospaced);
			if (this.doc != null)
				this.doc.notifyAttributeChanged(this, (MONOSPACED_ATTRIBUTE + "-" + chr.id), null);
		}
		else {
			chr.setCharForm(ImCharacter.sansSerif);
			if (this.doc != null)
				this.doc.notifyAttributeChanged(this, (MONOSPACED_ATTRIBUTE + "-" + chr.id), "true");
		}
	}
	
	/**
	 * Retrieve the number of HEX digits making up the code for an individual
	 * character in a word composed of chars from this font. This property
	 * defaults to 2, but can be as high as 4 in fonts that comprise a large
	 * number of characters.
	 * @return the char code length (in HEX digits)
	 */
	public int getCharCodeLength() {
		return this.charCodeLength;
	}
	
	/**
	 * Set the number of HEX digits to use for the code of an individual char
	 * in this font. The minimum allowed value is 2, but this property may be
	 * as high as 4 if the font comprises a large number of characters. This
	 * property should hardly ever be modified outside code creating instances
	 * of this class.
	 * @param ccl the char code length (in HEX digits) to set
	 */
	public void setCharCodeLength(int ccl) {
		if (ccl == this.charCodeLength)
			return;
		if (ccl < 2)
			throw new IllegalArgumentException("The minimum number of HEX digits per character code is 2");
		if (4 < ccl)
			throw new IllegalArgumentException("The maximum number of HEX digits per character code is 4");
		int oldCcl = this.charCodeLength;
		this.charCodeLength = ccl;
		if (this.doc != null)
			this.doc.notifyAttributeChanged(this, CHARACTER_CODE_LENGTH_ATTRIBUTE, ("" + oldCcl));
	}
	
	/**
	 * Retrieve the number of characters in the font.
	 * @return the number of characters
	 */
	public int getCharacterCount() {
		return this.characters.size();
	}
	
	/**
	 * Add a character to the font. The character string is supposed to hold a
	 * Unicode representation of the character, preferably one consisting of
	 * individual letters, digits, symbols, or punctuation marks, rather than
	 * ligature characters or the like.
	 * The returned boolean indicates if the font was modified, i.e., if a
	 * character was added or modified as a result of the call to this method.
	 * @param charId the font-local ID of the character
	 * @param charStr the Unicode representation of the character
	 * @return true if the font was modified, false otherwise
	 */
	public boolean addCharacter(int charId, String charStr) {
		return this.addCharacter(charId, charStr, ((BufferedImage) null), null);
	}
	
	/**
	 * Add a character to the font. The character string is supposed to hold a
	 * Unicode representation of the character, preferably one consisting of
	 * individual letters, digits, symbols, or punctuation marks, rather than
	 * ligature characters or the like. The argument character image is the
	 * glyph corresponding to the character in the source document, if any is
	 * explicitly given there; it allows for users to double-check and modify
	 * the transcription of glyphs to Unicode characters. The character image
	 * string is parsed as a hex encoding of a 32 pixel high black-and-white
	 * bitmap.
	 * The returned boolean indicates if the font was modified, i.e., if a
	 * character was added or modified as a result of the call to this method.
	 * @param charId the font-local ID of the character
	 * @param charStr the Unicode representation of the character
	 * @param charImageHex the HEX representation of the character image
	 * @return true if the font was modified, false otherwise
	 */
	public boolean addCharacter(int charId, String charStr, String charImageHex) {
		return this.addCharacter(charId, charStr, decodeCharacterImage(charImageHex, 32), null);
	}
	
	/**
	 * Add a character to the font. The character string is supposed to hold a
	 * Unicode representation of the character, preferably one consisting of
	 * individual letters, digits, symbols, or punctuation marks, rather than
	 * ligature characters or the like. The argument character image is the
	 * glyph corresponding to the character in the source document, if any is
	 * explicitly given there; it allows for users to double-check and modify
	 * the transcription of glyphs to Unicode characters. The character image
	 * string is parsed as a hex encoding of a black-and-white bitmap with the
	 * argument height.
	 * The returned boolean indicates if the font was modified, i.e., if a
	 * character was added or modified as a result of the call to this method.
	 * @param charId the font-local ID of the character
	 * @param charStr the Unicode representation of the character
	 * @param charImageHex the HEX representation of the character image
	 * @param charImageHeight the height of the character image
	 * @return true if the font was modified, false otherwise
	 */
	public boolean addCharacter(int charId, String charStr, String charImageHex, int charImageHeight) {
		return this.addCharacter(charId, charStr, decodeCharacterImage(charImageHex, charImageHeight), null);
	}
	
	/**
	 * Add a character to the font. The character string is supposed to hold a
	 * Unicode representation of the character, preferably one consisting of
	 * individual letters, digits, symbols, or punctuation marks, rather than
	 * ligature characters or the like. The argument character image is the
	 * glyph corresponding to the character in the source document, if any is
	 * explicitly given there; it allows for users to double-check and modify
	 * the transcription of glyphs to Unicode characters. The character image
	 * should be a black-and-white bitmap, 32 pixels high, with width allowed
	 * to adjust proportionally in accordance to the glyph dimensions.
	 * The returned boolean indicates if the font was modified, i.e., if a
	 * character was added or modified as a result of the call to this method.
	 * @param charId the font-local ID of the character
	 * @param charStr the Unicode representation of the character
	 * @param charImage the character image as extracted from the source
	 * @return true if the font was modified, false otherwise
	 */
	public boolean addCharacter(int charId, String charStr, BufferedImage charImage) {
		return this.addCharacter(charId, charStr, charImage, null);
	}
	
	/**
	 * Add a character to the font. The character string is supposed to hold a
	 * Unicode representation of the character, preferably one consisting of
	 * individual letters, digits, symbols, or punctuation marks, rather than
	 * ligature characters or the like. The argument character image is the
	 * glyph corresponding to the character in the source document, if any is
	 * explicitly given there; it allows for users to double-check and modify
	 * the transcription of glyphs to Unicode characters. The character image
	 * string is parsed as a hex encoding of a 32 pixel high black-and-white
	 * bitmap.
	 * The returned boolean indicates if the font was modified, i.e., if a
	 * character was added or modified as a result of the call to this method.
	 * @param charId the font-local ID of the character
	 * @param charStr the Unicode representation of the character
	 * @param charImageHex the HEX representation of the character image
	 * @param charPathString the string representation of the character
	 *            vector path
	 * @return true if the font was modified, false otherwise
	 */
	public boolean addCharacter(int charId, String charStr, String charImageHex, String charPathString) {
		return this.addCharacter(charId, charStr, decodeCharacterImage(charImageHex, 32), decodeCharacterPath(charPathString));
	}
	
	/**
	 * Add a character to the font. The character string is supposed to hold a
	 * Unicode representation of the character, preferably one consisting of
	 * individual letters, digits, symbols, or punctuation marks, rather than
	 * ligature characters or the like. The argument character image is the
	 * glyph corresponding to the character in the source document, if any is
	 * explicitly given there; it allows for users to double-check and modify
	 * the transcription of glyphs to Unicode characters. The character image
	 * string is parsed as a hex encoding of a black-and-white bitmap with the
	 * argument height.
	 * The returned boolean indicates if the font was modified, i.e., if a
	 * character was added or modified as a result of the call to this method.
	 * @param charId the font-local ID of the character
	 * @param charStr the Unicode representation of the character
	 * @param charImageHex the HEX representation of the character image
	 * @param charImageHeight the height of the character image
	 * @param charPathString the string representation of the character
	 *            vector path
	 * @return true if the font was modified, false otherwise
	 */
	public boolean addCharacter(int charId, String charStr, String charImageHex, int charImageHeight, String charPathString) {
		return this.addCharacter(charId, charStr, decodeCharacterImage(charImageHex, charImageHeight), decodeCharacterPath(charPathString));
	}
	
	/**
	 * Add a character to the font. The character string is supposed to hold a
	 * Unicode representation of the character, preferably one consisting of
	 * individual letters, digits, symbols, or punctuation marks, rather than
	 * ligature characters or the like. The argument character image is the
	 * glyph corresponding to the character in the source document, if any is
	 * explicitly given there; it allows for users to double-check and modify
	 * the transcription of glyphs to Unicode characters. The character image
	 * should be a black-and-white bitmap, 32 pixels high, with width allowed
	 * to adjust proportionally in accordance to the glyph dimensions.
	 * The returned boolean indicates if the font was modified, i.e., if a
	 * character was added or modified as a result of the call to this method.
	 * @param charId the font-local ID of the character
	 * @param charStr the Unicode representation of the character
	 * @param charImage the character image as extracted from the source
	 * @param charPath the the character vector path as extracted from the
	 *            source
	 * @return true if the font was modified, false otherwise
	 */
	public boolean addCharacter(int charId, String charStr, BufferedImage charImage, Path2D.Float charPath) {
		if ((charStr == null) && (charImage == null)) // path is optional, image is not
			return false;
		ImCharacter chr = ((ImCharacter) this.characters.get(new Integer(charId)));
		if (chr == null) {
			chr = new ImCharacter(charId, charStr, charImage, charPath);
			if (!this.mixedStyle) {
				chr.setBold(this.bold);
				chr.setItalics(this.italics);
				if (this.charFaceType == CHAR_FACE_TYPE_SERIF)
					chr.setCharForm(ImCharacter.serif);
				else if (this.charFaceType == CHAR_FACE_TYPE_MONOSPACED)
					chr.setCharForm(ImCharacter.monospaced);
			}
			this.characters.put(new Integer(chr.id), chr);
			return true;
		}
		boolean modified = false;
		if ((charStr != null) && !charStr.equals(chr.str)) {
			modified = true;
			String oCharStr = chr.str;
			chr.str = charStr;
			if (this.doc != null)
				this.doc.notifyAttributeChanged(this, (CHARACTER_STRING_ATTRIBUTE + "-" + charId), oCharStr);
		}
		if ((charImage != null) && !characterImagesEqual(charImage, chr.image)) {
			modified = true;
			BufferedImage oCharImage = chr.image;
			chr.image = charImage;
			if (this.doc != null)
				this.doc.notifyAttributeChanged(this, (CHARACTER_IMAGE_ATTRIBUTE + "-" + charId), oCharImage);
		}
		if ((charPath != null) && !characterPathsEqual(charPath, chr.path)) {
			modified = true;
			Path2D.Float oCharPath = chr.path;
			chr.path = charPath;
			if (this.doc != null)
				this.doc.notifyAttributeChanged(this, (CHARACTER_PATH_ATTRIBUTE + "-" + charId), oCharPath);
		}
		return modified;
	}
	
	/**
	 * Add a character to the font. The returned boolean indicates if the font
	 * was modified, i.e., if a character with the argument ID was present
	 * before the call to this method.
	 * @param charId the font-local ID of the character to remove
	 * @return true if the font was modified, false otherwise
	 */
	public boolean removeCharacter(int charId) {
		ImCharacter chr = ((ImCharacter) this.characters.remove(new Integer(charId)));
		if (chr == null)
			return false;
		if ((chr.image != null) && (this.doc != null))
			this.doc.notifyAttributeChanged(this, (CHARACTER_IMAGE_ATTRIBUTE + "-" + charId), chr.image);
		if ((chr.path != null) && (this.doc != null))
			this.doc.notifyAttributeChanged(this, (CHARACTER_PATH_ATTRIBUTE + "-" + charId), chr.path);
		if ((chr.str != null) && (this.doc != null))
			this.doc.notifyAttributeChanged(this, (CHARACTER_STRING_ATTRIBUTE + "-" + charId), chr.str);
		return true;
	}
	
	/**
	 * Retrieve the Unicode string representing a character.
	 * @param charId the font-local ID of the character
	 * @return the Unicode string of the character with the argument ID
	 */
	public String getString(int charId) {
		ImCharacter chr = ((ImCharacter) this.characters.get(new Integer(charId)));
		return ((chr == null) ? null : chr.str);
	}
	
	/**
	 * Retrieve the glyph image for a character.
	 * @param charId the font-local ID of the character
	 * @return the glyph image of the character with the argument ID
	 */
	public BufferedImage getImage(int charId) {
		ImCharacter chr = ((ImCharacter) this.characters.get(new Integer(charId)));
		return ((chr == null) ? null : chr.image);
	}
	
	/**
	 * Retrieve the hex representation of the glyph image for a character.
	 * @param charId the font-local ID of the character
	 * @return the hex representation glyph image of the character with the
	 *            argument ID
	 */
	public String getImageHex(int charId) {
		ImCharacter chr = ((ImCharacter) this.characters.get(new Integer(charId)));
		return ((chr == null) ? null : encodeCharacterImage(chr.image));
	}
	
	/**
	 * Retrieve the glyph vector path for a character.
	 * @param charId the font-local ID of the character
	 * @return the glyph vector path of the character with the argument ID
	 */
	public Path2D.Float getPath(int charId) {
		ImCharacter chr = ((ImCharacter) this.characters.get(new Integer(charId)));
		return ((chr == null) ? null : chr.path);
	}
	
	/**
	 * Retrieve the string representation of the glyph vector path for a
	 * character.
	 * @param charId the font-local ID of the character
	 * @return the hex representation glyph vector path of the character with
	 *            the argument ID
	 */
	public String getPathString(int charId) {
		ImCharacter chr = ((ImCharacter) this.characters.get(new Integer(charId)));
		return ((chr == null) ? null : encodeCharacterPath(chr.path));
	}
	
	/**
	 * Retrieve the IDs of all characters belonging to the font.
	 * @return an array holding the character IDs
	 */
	public int[] getCharacterIDs() {
		int[] charIDs = new int[this.characters.size()];
		int charIdIndex = 0;
		for (Iterator cidit = this.characters.keySet().iterator(); cidit.hasNext();)
			charIDs[charIdIndex++] = ((Integer) cidit.next()).intValue();
		return charIDs;
	}
	
	private static class ImCharacter {
		static final byte bold = 0x01;
		static final byte italics = 0x02;
		static final byte sansSerif = 0x00;
		static final byte serif = 0x04;
		static final byte monospaced = 0x08;
		static final byte sansSerifMask = ~(serif | monospaced); // bit-wise complement of serif and monospaced, to erase both at once
		final int id;
		String str;
		byte style;
		BufferedImage image;
		Path2D.Float path;
		ImCharacter(int id, String str, BufferedImage image, Path2D.Float path) {
			this.id = id;
			this.str = str;
			this.image = image;
			this.path = path;
		}
		void setBold(boolean b) {
			if (b)
				this.style |= bold;
			else this.style &= ~bold;
		}
		boolean isBold() {
			return ((this.style & bold) != 0);
		}
		void setItalics(boolean i) {
			if (i)
				this.style |= italics;
			else this.style &= ~italics;
		}
		boolean isItalics() {
			return ((this.style & italics) != 0);
		}
		void setCharForm(byte cf) {
			this.style &= sansSerifMask; // erase previous char face type
			this.style |= cf; // set either one bit
		}
		boolean isSans() {
			return ((this.style & (serif | monospaced)) == 0);
		}
		boolean isSerif() {
			return ((this.style & serif) != 0);
		}
		boolean isMonospaced() {
			return ((this.style & monospaced) != 0);
		}
	}
	
	private static final int whiteRgb = Color.WHITE.getRGB();
	private static final int blackRgb = Color.BLACK.getRGB();
	
	private static BufferedImage decodeCharacterImage(String charImageHex, int height) {
		if (charImageHex == null)
			return null;
		int charImageSize = (charImageHex.length() * 4 /* number of pixels per HEX digit */);
		int charImageWidth = (charImageSize / height);
		if (charImageWidth == 0)
			return null;
		
		BufferedImage charImage = new BufferedImage(charImageWidth, height, BufferedImage.TYPE_BYTE_BINARY);
		int x = 0;
		int y = 0;
		int hex;
		int hexMask;
		for (int h = 0; h < charImageHex.length(); h++) {
			try {
				hex = Integer.parseInt(charImageHex.substring(h, (h+1)), 16);
			}
			catch (NumberFormatException nfe) {
				return null; // whatever input we got here ...
			}
			hexMask = 8;
			while (hexMask != 0) {
				charImage.setRGB(x++, y, (((hex & hexMask) == 0) ? whiteRgb : blackRgb));
				hexMask >>= 1;
				if (x == charImageWidth) {
					x = 0;
					y++;
				}
			}
		}
		
		return charImage;
	}
	
	private static String encodeCharacterImage(BufferedImage charImage) {
		if (charImage == null)
			return null;
		
		int hex = 0;
		int hexBits = 0;
		StringBuffer charImageHex = new StringBuffer();
		for (int y = 0; y < charImage.getHeight(); y++)
			for (int x = 0; x < charImage.getWidth(); x++) {
				if (charImage.getRGB(x, y) != whiteRgb) // TODO make this image type dependent !!!
					hex += 1;
				if (hexBits == 3) {
					charImageHex.append(Integer.toString(hex, 16).toUpperCase());
					hex = 0;
					hexBits = 0;
				}
				else {
					hex <<= 1;
					hexBits++;
				}
			}
		
		return charImageHex.toString();
	}
	
	private static boolean characterImagesEqual(BufferedImage ci1, BufferedImage ci2) {
		if (ci1 == ci2)
			return true;
		if ((ci1 == null) || (ci2 == null))
			return false;
		if ((ci1.getWidth() != ci2.getWidth()) || (ci1.getHeight() != ci2.getHeight()))
			return false;
		for (int x = 0; x < ci1.getWidth(); x++)
			for (int y = 0; y < ci1.getHeight(); y++) {
				if (ci1.getRGB(x, y) != ci2.getRGB(x, y))
					return false;
			}
		return true;
	}
	
	/**
	 * Scale a character image to 32 pixels high and proportional width. If the
	 * argument image already is 32 pixels in height, it is simply returned. In
	 * any case, the type of the returned image is the same as that of the
	 * argument image.
	 * @param charImage the character image to scale
	 * @return the scaled character image
	 */
	public static BufferedImage scaleCharImage(BufferedImage charImage) {
		return scaleCharImage(charImage, 32);
	}
	
	/**
	 * Scale a character image to a specific number of pixels high and
	 * proportional width. If the argument image already has the argument
	 * height, it is simply returned. In any case, the type of the returned
	 * image is the same as that of the argument image.
	 * @param charImage the character image to scale
	 * @param height the height to scale to
	 * @return the scaled character image
	 */
	public static BufferedImage scaleCharImage(BufferedImage charImage, int height) {
		return scaleCharImage(charImage, height, true);
	}
	
	private static BufferedImage scaleCharImage(BufferedImage charImage, int height, boolean allowStepping) {
		if ((charImage == null) || (charImage.getHeight() == height))
			return charImage;
		
		/* TODOne Prevent thin lines in char images from disappearing altogether on scale-down:
		 * - round down on thick side on size reduction ...
		 * - ... always going black if third (or even fourth) of to-aggregate pixels are set
		 * - scale rendered char images to closest multiple of target size ...
		 * - ... and then scale down to height 64 or 32 in single go
		 * ==> makes smaller char images somewhat heavier ...
		 * ==> ... but makes sure actual char remains discernable from reduced image
		 */
//		if (allowStepping) /* scaling to height 128 or 64 before going to actual target height seems to benefit survival of thin lines */ {
//			if (false) {}
//			else if ((charImage.getHeight() > 128) && (height < 128)/* && (height != 96)*/ && (height != 64))
//				charImage = scaleCharImage(charImage, 128, false);
//			else if (charImage.getHeight() == 128) { /* no stepping down to 64 from 128, deteriorates image */ }
////			else if ((charImage.getHeight() > 96) && (height < 96) && (height != 64))
////				charImage = scaleCharImage(charImage, 96, false);
////			else if (charImage.getHeight() == 96) { /* no stepping down to 64 from 96, deteriorates image */ }
//			else if ((charImage.getHeight() > 64) && (height < 64))
//				charImage = scaleCharImage(charImage, 64, false);
//		}
		
		/* TODOne Turns out, stepped scale-down alone also deteriorates char images ...
		 * ... but differently than direct scale-down
		 * ==> combine scale-down methods ...
		 * ==> ... and or-combine results
		 * 
		 * ==> Seems to do the trick, erring on thick side for very thin strokes just as desired
		 */
		BufferedImage i128charImage = null;
		BufferedImage i64charImage = null;
		if (allowStepping) /* scaling to height 128 or 64 before going to actual target height seems to benefit survival of other thin lines than direct way ==> combine paths */ {
			if ((charImage.getHeight() > 128) && (height < 128) && (height != 64)) {
				i128charImage = scaleCharImage(charImage, 128, false);
				i128charImage = scaleCharImage(i128charImage, height, false);
			}
			if ((charImage.getHeight() > 64) && (height < 64)) {
				i64charImage = scaleCharImage(charImage, 64, false);
				i64charImage = scaleCharImage(i64charImage, height, false);
			}
		}
		
		//	perform normal scale-down
		BufferedImage sCharImage = new BufferedImage(Math.max(1, ((charImage.getWidth() * height) / charImage.getHeight())), height, charImage.getType());
//		BufferedImage sCharImage = new BufferedImage(Math.max(1, ((charImage.getWidth() * height) / charImage.getHeight())), height, BufferedImage.TYPE_BYTE_GRAY); ==> GRAYSCALE DOESN'T SEEM TO MAKE A DIFFERENCE
		Graphics2D sCiGr = sCharImage.createGraphics();
//		sCiGr.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); ==> ANTI-ALIASING DOESN'T SEEM TO MAKE A DIFFERENCE
		sCiGr.setColor(Color.WHITE);
		sCiGr.fillRect(0, 0, sCharImage.getWidth(), sCharImage.getHeight());
		sCiGr.scale((((double) height) / charImage.getHeight()), (((double) height) / charImage.getHeight()));
		sCiGr.drawImage(charImage, 0, 0, null);
		sCiGr.dispose();
		
		//	combine with result of stepped scale-down via 128 pixels of height ...
		if (i128charImage != null) {
			for (int x = 0; x < sCharImage.getWidth(); x++)
				for (int y = 0; y < sCharImage.getHeight(); y++) {
					if (sCharImage.getRGB(x, y) != whiteRgb)
						continue; // already set
					if (i128charImage.getRGB(x, y) != whiteRgb)
						sCharImage.setRGB(x, y, i128charImage.getRGB(x, y));
//						sCharImage.setRGB(x, y, Color.RED.getRGB());
				}
		}
		
		//	... as well as result of stepped scale-down via 64 pixels of height ...
		if (i64charImage != null) {
			for (int x = 0; x < sCharImage.getWidth(); x++)
				for (int y = 0; y < sCharImage.getHeight(); y++) {
					if (sCharImage.getRGB(x, y) != whiteRgb)
						continue; // already set
					if (i64charImage.getRGB(x, y) != whiteRgb)
						sCharImage.setRGB(x, y, i64charImage.getRGB(x, y));
//						sCharImage.setRGB(x, y, Color.GREEN.getRGB());
				}
		}
//		JOptionPane.showMessageDialog(null, new JLabel(new ImageIcon(charImage)), ("Scaled " + charImage.getWidth() + "x" + charImage.getHeight() + " to " + sCharImage.getWidth() + "x" + sCharImage.getHeight()), JOptionPane.PLAIN_MESSAGE, new ImageIcon(sCharImage));
		return sCharImage;
	}
	
	private static Path2D.Float decodeCharacterPath(String charPathString) {
		if (charPathString == null)
			return null;
		String[] segments = charPathString.split("\\s");
		if (segments.length < 2) // 'segment' 0 merely indicates winding rule
			return null;
		Path2D.Float charPath = new Path2D.Float("E".equals(segments[0]) ? Path2D.WIND_EVEN_ODD : Path2D.WIND_NON_ZERO);
		int subSegCount = 0;
		for (int s = 1 /* 'segment' 0 indicates winding rule */; s < segments.length; s++) {
			String[] params = segments[s].split("\\/");
			if ("M".equals(params[0])) {
				if (subSegCount != 0)
					charPath.closePath();
				charPath.moveTo(Float.parseFloat(params[1]), Float.parseFloat(params[2]));
				subSegCount = 0;
			}
			else if ("L".equals(params[0])) {
				charPath.lineTo(Float.parseFloat(params[1]), Float.parseFloat(params[2]));
				subSegCount++;
			}
			else if ("Q".equals(params[0])) {
				charPath.quadTo(Float.parseFloat(params[1]), Float.parseFloat(params[2]), Float.parseFloat(params[3]), Float.parseFloat(params[4]));
				subSegCount++;
			}
			else if ("C".equals(params[0])) {
				charPath.curveTo(Float.parseFloat(params[1]), Float.parseFloat(params[2]), Float.parseFloat(params[3]), Float.parseFloat(params[4]), Float.parseFloat(params[5]), Float.parseFloat(params[6]));
				subSegCount++;
			}
			else if ("O".equals(params[0])) {
				if (subSegCount != 0)
					charPath.closePath();
				subSegCount = 0;
			}
		}
		if (subSegCount != 0)
			charPath.closePath();
		return charPath;
	}
	
	private static String encodeCharacterPath(Path2D.Float charPath) {
		if (charPath == null)
			return null;
		PathIterator pit = charPath.getPathIterator(null);
		if (pit.isDone()) // no use storing empty paths
			return null;
		StringBuffer charPathString = new StringBuffer((charPath.getWindingRule() == Path2D.WIND_EVEN_ODD) ? "E" /* _E_ven-odd */ : "Z" /* non-_Z_ero */);
		float[] params = new float[6];
		while (!pit.isDone()) {
			int op = pit.currentSegment(params);
			if (op == PathIterator.SEG_MOVETO) {
				charPathString.append(" M");
				appendParams(charPathString, params, 2);
			}
			else if (op == PathIterator.SEG_LINETO) {
				charPathString.append(" L");
				appendParams(charPathString, params, 2);
			}
			else if (op == PathIterator.SEG_QUADTO) {
				charPathString.append(" Q");
				appendParams(charPathString, params, 4);
			}
			else if (op == PathIterator.SEG_CUBICTO) {
				charPathString.append(" C");
				appendParams(charPathString, params, 6);
			}
			else if (op == PathIterator.SEG_CLOSE)
				charPathString.append(" O");
			pit.next();
		}
		return charPathString.toString();
	}
	private static void appendParams(StringBuffer charPathString, float[] params, int count) {
		for (int p = 0; p < count; p++)
			charPathString.append("/" + Math.round(params[p]));
	}
	
	private static boolean characterPathsEqual(Path2D.Float cp1, Path2D.Float cp2) {
		if (cp1 == cp2)
			return true;
		if ((cp1 == null) || (cp2 == null))
			return false;
		if (cp1.getWindingRule() != cp2.getWindingRule())
			return false;
		PathIterator pit1 = cp1.getPathIterator(null);
		float[] params1 = new float[6];
		PathIterator pit2 = cp2.getPathIterator(null);
		float[] params2 = new float[6];
		while (!pit1.isDone() && !pit2.isDone()) {
			int op1 = pit1.currentSegment(params1);
			int op2 = pit2.currentSegment(params2);
			if (op1 != op2)
				return false;
			else if ((op1 == PathIterator.SEG_MOVETO) && paramsDifferent(params1, params2, 2))
				return false;
			else if ((op1 == PathIterator.SEG_LINETO) && paramsDifferent(params1, params2, 2))
				return false;
			else if ((op1 == PathIterator.SEG_QUADTO) && paramsDifferent(params1, params2, 4))
				return false;
			else if ((op1 == PathIterator.SEG_CUBICTO) && paramsDifferent(params1, params2, 6))
				return false;
			pit1.next();
			pit2.next();
		}
		return (pit1.isDone() == pit2.isDone());
	}
	private static boolean paramsDifferent(float[] params1, float[] params2, int count) {
		for (int p = 0; p < count; p++) {
			if (Math.round(params1[p]) != Math.round(params2[p]))
				return true;
		}
		return false;
	}
//	
//	public static void main(String[] args) throws Exception {
//		//ItalicsLowerZ.retainDiagonalStroke.png
//		BufferedImage bi = ImageIO.read(new File("E:/Testdaten/PdfExtract/Fonts/ItalicsLowerZ.retainDiagonalStroke.png"));
//		ImageDisplayDialog idd = new ImageDisplayDialog("ItalicsLowerZ.retainDiagonalStroke.png");
//		idd.addImage(bi, "Original (Type " + bi.getType() + ")");
//		BufferedImage s32Bi = scaleCharImage(bi, 32);
//		idd.addImage(s32Bi, "Scale 32 (Type " + s32Bi.getType() + ")");
//		BufferedImage s64Bi = scaleCharImage(bi, 64);
//		idd.addImage(s64Bi, "Scale 64 (Type " + s64Bi.getType() + ")");
//		BufferedImage s64s32Bi = scaleCharImage(s64Bi, 32);
//		idd.addImage(s64s32Bi, "Scale 64-32 (Type " + s64s32Bi.getType() + ")");
////		BufferedImage s96Bi = scaleCharImage(bi, 96);
////		idd.addImage(s96Bi, "Scale 96 (Type " + s96Bi.getType() + ")");
////		BufferedImage s96s32Bi = scaleCharImage(s96Bi, 32);
////		idd.addImage(s96s32Bi, "Scale 96-32 (Type " + s96s32Bi.getType() + ")");
//		BufferedImage s128Bi = scaleCharImage(bi, 128);
//		idd.addImage(s128Bi, "Scale 128 (Type " + s128Bi.getType() + ")");
//		BufferedImage s128s32Bi = scaleCharImage(s128Bi, 32);
//		idd.addImage(s128s32Bi, "Scale 128-32 (Type " + s128s32Bi.getType() + ")");
//		BufferedImage s128s64Bi = scaleCharImage(s128Bi, 64);
//		idd.addImage(s128s64Bi, "Scale 128-64 (Type " + s128s64Bi.getType() + ")");
//		BufferedImage s128s64s32Bi = scaleCharImage(s128s64Bi, 32);
//		idd.addImage(s128s64s32Bi, "Scale 128-64-32 (Type " + s128s64s32Bi.getType() + ")");
//		BufferedImage s256Bi = scaleCharImage(bi, 256);
//		idd.addImage(s256Bi, "Scale 256 (Type " + s256Bi.getType() + ")");
//		BufferedImage s256s32Bi = scaleCharImage(s256Bi, 32);
//		idd.addImage(s256s32Bi, "Scale 256-32 (Type " + s256s32Bi.getType() + ")");
//		BufferedImage s256s64Bi = scaleCharImage(s256Bi, 64);
//		idd.addImage(s256s64Bi, "Scale 256-64 (Type " + s256s64Bi.getType() + ")");
//		BufferedImage s256s64s32Bi = scaleCharImage(s256s64Bi, 32);
//		idd.addImage(s256s64s32Bi, "Scale 256-64-32 (Type " + s256s64s32Bi.getType() + ")");
//		BufferedImage s256s128Bi = scaleCharImage(s256Bi, 128);
//		idd.addImage(s256s128Bi, "Scale 256-128 (Type " + s256s128Bi.getType() + ")");
//		BufferedImage s256s128s32Bi = scaleCharImage(s256s128Bi, 32);
//		idd.addImage(s256s128s32Bi, "Scale 256-128-32 (Type " + s256s128s32Bi.getType() + ")");
//		
//		idd.setSize(400, 400);
//		idd.setLocationRelativeTo(null);
//		idd.setVisible(true);
//	}
}