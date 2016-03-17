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

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Centralized static loader for free fonts. Each class using the built-in free
 * fonts 'FreeSerif', 'FreeSans', and 'FreeMono' should call the
 * <code>loadFreeFonts()</code> method of this class, best from a static
 * initializer block. Calling this method has an effect only the first time,
 * any subsequent invocations are ignored.
 * 
 * @author sautter
 */
public class ImFontUtils {
	
	/**
	 * Load free fonts the built-in free fonts 'FreeSerif', 'FreeSans', and
	 * 'FreeMono'. Calling this method has an effect only the first time, any
	 * subsequent invocations are ignored.
	 */
	public synchronized static void loadFreeFonts() {
		String ifuClassResName = ImFontUtils.class.getName().replaceAll("\\.", "/");
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		
		loadFreeFont("FreeSerif", ifuClassResName, ge);
		loadFreeFont("FreeSerifItalic", ifuClassResName, ge);
		loadFreeFont("FreeSerifBold", ifuClassResName, ge);
		loadFreeFont("FreeSerifBoldItalic", ifuClassResName, ge);
		
		loadFreeFont("FreeSans", ifuClassResName, ge);
		loadFreeFont("FreeSansOblique", ifuClassResName, ge);
		loadFreeFont("FreeSansBold", ifuClassResName, ge);
		loadFreeFont("FreeSansBoldOblique", ifuClassResName, ge);
		
		loadFreeFont("FreeMono", ifuClassResName, ge);
		loadFreeFont("FreeMonoOblique", ifuClassResName, ge);
		loadFreeFont("FreeMonoBold", ifuClassResName, ge);
		loadFreeFont("FreeMonoBoldOblique", ifuClassResName, ge);
	}
	
	private static Set loadedFreeFontNames = Collections.synchronizedSet(new HashSet());
	private static void loadFreeFont(String fontName, String ifuClassResName, GraphicsEnvironment ge) {
		if (!loadedFreeFontNames.add(fontName))
			return;
		String ttfResName = (ifuClassResName.substring(0, ifuClassResName.lastIndexOf('/')) + "/ttfRes/" + fontName + ".ttf");
		InputStream fontIn = ImFontUtils.class.getClassLoader().getResourceAsStream(ttfResName);
		if (fontIn == null)
			System.err.println("Could not load font '" + fontName + "': " + ttfResName + " not found.");
		else try {
			Font font = Font.createFont(Font.TRUETYPE_FONT, fontIn);
			fontIn.close();
			ge.registerFont(font);
			System.err.println("Font '" + fontName + "' loaded successfully.");
		}
		catch (FontFormatException ffe) {
			System.err.println("Could not load font '" + fontName + "': " + ffe.getMessage());
			ffe.printStackTrace(System.err);
		}
		catch (IOException ioe) {
			System.err.println("Could not load font '" + fontName + "': " + ioe.getMessage());
			ioe.printStackTrace(System.err);
		}
	}
}
