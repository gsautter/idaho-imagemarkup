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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Iterator;
import java.util.TreeMap;

import de.uka.ipd.idaho.gamta.Attributed;

/**
 * A font in an Image Markup document that originates from a born-digital
 * source, e.g. a born-digital PDF document.
 * 
 * @author sautter
 */
public class ImFont implements ImObject {
	
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
	
	/** the name of the attribute indicating if the font is serif or sans-serif */
	public static final String SERIF_ATTRIBUTE = "serif";
	
	/** the name of the attribute to a word storing the font specific char codes the word was composed from */
	public static final String CHARACTER_CODE_STRING_ATTRIBUTE = "fontCharCodes";
	
	private ImDocument doc;
	
	/** the name of the font */
	public final String name;
	
	private boolean bold;
	private boolean italics;
	private boolean serif;
	
	private TreeMap characters = new TreeMap();
	
	/** Constructor
	 * @param doc the Image Markup document the font belongs to
	 * @param name the font name
	 */
	public ImFont(ImDocument doc, String name) {
		this(doc, name, false, false, true);
	}
	
	/** Constructor
	 * @param doc the Image Markup document the font belongs to
	 * @param name the font name
	 * @param bold is the font bold?
	 * @param italics is the font in italics?
	 * @param serif is the font serif or sans-serif?
	 */
	public ImFont(ImDocument doc, String name, boolean bold, boolean italics, boolean serif) {
		this.doc = doc;
		this.name = name;
		this.bold = bold;
		this.italics = italics;
		this.serif = serif;
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
		else if (name.startsWith(CHARACTER_STRING_ATTRIBUTE + "-") && (value instanceof String)) {
			int charId = Integer.parseInt(name.substring((CHARACTER_STRING_ATTRIBUTE + "-").length()));
			String oCharStr = this.getString(charId);
			this.addCharacter(charId, ((String) value), ((BufferedImage) null));
			return oCharStr;
		}
		else if (name.startsWith(CHARACTER_IMAGE_ATTRIBUTE + "-") && (value instanceof BufferedImage)) {
			int charId = Integer.parseInt(name.substring((CHARACTER_IMAGE_ATTRIBUTE + "-").length()));
			BufferedImage oCharImage = this.getImage(charId);
			this.addCharacter(charId, null, ((BufferedImage) value));
			return oCharImage;
		}
		else return null;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.gamta.Attributed#copyAttributes(de.uka.ipd.idaho.gamta.Attributed)
	 */
	public void copyAttributes(Attributed source) { /* we're not doing this here, as attributes are only to allow for generic undo management */ }
	
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
		else if (name.startsWith(CHARACTER_STRING_ATTRIBUTE + "-")) {
			int charId = Integer.parseInt(name.substring((CHARACTER_STRING_ATTRIBUTE + "-").length()));
			return this.getString(charId);
		}
		else if (name.startsWith(CHARACTER_IMAGE_ATTRIBUTE + "-")) {
			int charId = Integer.parseInt(name.substring((CHARACTER_IMAGE_ATTRIBUTE + "-").length()));
			return this.getImage(charId);
		}
		else return def;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.gamta.Attributed#hasAttribute(java.lang.String)
	 */
	public boolean hasAttribute(String name) {
		return (BOLD_ATTRIBUTE.equals(name) || ITALICS_ATTRIBUTE.equals(name) || SERIF_ATTRIBUTE.equals(name));
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.gamta.Attributed#getAttributeNames()
	 */
	public String[] getAttributeNames() {
		String[] ans = {BOLD_ATTRIBUTE, ITALICS_ATTRIBUTE, SERIF_ATTRIBUTE};
		return ans;
	}
	
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
		else return null;
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
	 * @see de.uka.ipd.idaho.im.ImObject#getDocument()
	 */
	public ImDocument getDocument() {
		return this.doc;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImObject#getDocumentProperty(java.lang.String)
	 */
	public String getDocumentProperty(String propertyName) {
		return this.doc.getDocumentProperty(propertyName);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImObject#getDocumentProperty(java.lang.String, java.lang.String)
	 */
	public String getDocumentProperty(String propertyName, String defaultValue) {
		return this.doc.getDocumentProperty(propertyName, defaultValue);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImObject#getDocumentPropertyNames()
	 */
	public String[] getDocumentPropertyNames() {
		return this.doc.getDocumentPropertyNames();
	}
	
	/**
	 * Test whether or not the font is bold.
	 * @return true if the font is bold, false otherwise
	 */
	public boolean isBold() {
		return this.bold;
	}
	
	/**
	 * Mark the font as bold or non-bold.
	 * @param bold is the font bold?
	 */
	public void setBold(boolean bold) {
		if (this.bold == bold)
			return;
		this.bold = bold;
		this.doc.notifyAttributeChanged(this, BOLD_ATTRIBUTE, (this.bold ? null : "true"));
	}
	
	/**
	 * Test whether or not the font is in italics.
	 * @return true if the font is in italics, false otherwise
	 */
	public boolean isItalics() {
		return this.italics;
	}
	
	/**
	 * Mark the font as italics or non-italics.
	 * @param italics is the font in italics?
	 */
	public void setItalics(boolean italics) {
		if (this.italics == italics)
			return;
		this.italics = italics;
		this.doc.notifyAttributeChanged(this, ITALICS_ATTRIBUTE, (this.italics ? null : "true"));
	}
	
	/**
	 * Test whether the font is serif or sans-serif.
	 * @return true if the font is serif, false otherwise
	 */
	public boolean isSerif() {
		return this.serif;
	}
	
	/**
	 * Mark the font as serif or sans-serif.
	 * @param serif is the font serif?
	 */
	public void setSerif(boolean serif) {
		if (this.serif == serif)
			return;
		this.serif = serif;
		this.doc.notifyAttributeChanged(this, SERIF_ATTRIBUTE, (this.serif ? null : "true"));
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
		return this.addCharacter(charId, charStr, ((BufferedImage) null));
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
	 * @param charImageHex the character image as extracted from the source
	 * @return true if the font was modified, false otherwise
	 */
	public boolean addCharacter(int charId, String charStr, String charImageHex) {
		return this.addCharacter(charId, charStr, decodeCharacterImage(charImageHex));
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
		ImCharacter chr = ((ImCharacter) this.characters.get(new Integer(charId)));
		if (chr == null) {
			chr = new ImCharacter(charId, charStr, charImage);
			this.characters.put(new Integer(chr.id), chr);
			return true;
		}
		boolean modified = false;
		if ((charStr != null) && !charStr.equals(chr.str)) {
			modified = true;
			String oCharStr = chr.str;
			chr.str = charStr;
			this.doc.notifyAttributeChanged(this, (CHARACTER_STRING_ATTRIBUTE + "-" + charId), oCharStr);
		}
		if ((charImage != null) && !characterImagesEqual(charImage, chr.image)) {
			modified = true;
			BufferedImage oCharImage = chr.image;
			chr.image = charImage;
			this.doc.notifyAttributeChanged(this, (CHARACTER_IMAGE_ATTRIBUTE + "-" + charId), oCharImage);
		}
		return modified;
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
		int id;
		String str;
		BufferedImage image;
		ImCharacter(int id, String str, BufferedImage image) {
			this.id = id;
			this.str = str;
			this.image = image;
		}
	}
	
	private static final int whiteRgb = Color.WHITE.getRGB();
	private static final int blackRgb = Color.BLACK.getRGB();
	
	private static BufferedImage decodeCharacterImage(String charImageHex) {
		if (charImageHex == null)
			return null;
		int charImageWidth = (charImageHex.length() / 8);
		if (charImageWidth == 0)
			return null;
		
		BufferedImage charImage = new BufferedImage(charImageWidth, 32, BufferedImage.TYPE_BYTE_BINARY);
		int x = 0;
		int y = 0;
		int hex;
		int hexMask;
		for (int h = 0; h < charImageHex.length(); h++) {
			hex = Integer.parseInt(charImageHex.substring(h, (h+1)), 16);
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
				if (charImage.getRGB(x, y) != whiteRgb)
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
		if ((charImage == null) || (charImage.getHeight() == 32))
			return charImage;
		BufferedImage sCharImage = new BufferedImage(Math.max(1, ((charImage.getWidth() * 32) / charImage.getHeight())), 32, charImage.getType());
		Graphics2D sCiGr = sCharImage.createGraphics();
		sCiGr.setColor(Color.WHITE);
		sCiGr.fillRect(0, 0, sCharImage.getWidth(), sCharImage.getHeight());
		sCiGr.scale((32.0 / charImage.getHeight()), (32.0 / charImage.getHeight()));
		sCiGr.drawImage(charImage, 0, 0, null);
		sCiGr.dispose();
//		JOptionPane.showMessageDialog(null, new JLabel(new ImageIcon(charImage)), ("Scaled " + charImage.getWidth() + "x" + charImage.getHeight() + " to " + sCharImage.getWidth() + "x" + sCharImage.getHeight()), JOptionPane.PLAIN_MESSAGE, new ImageIcon(sCharImage));
		return sCharImage;
	}
}