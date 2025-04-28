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
package de.uka.ipd.idaho.im.pdf;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.QuadCurve2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.swing.JOptionPane;

import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.util.Library;

import de.uka.ipd.idaho.easyIO.streams.CharSequenceReader;
import de.uka.ipd.idaho.gamta.Gamta;
import de.uka.ipd.idaho.gamta.util.CountingSet;
import de.uka.ipd.idaho.gamta.util.ProgressMonitor;
import de.uka.ipd.idaho.im.pdf.PdfCharDecoder.CharImage;
import de.uka.ipd.idaho.im.pdf.PdfCharDecoder.CharImageMatch;
import de.uka.ipd.idaho.im.pdf.PdfCharDecoder.CharMatchResult;
import de.uka.ipd.idaho.im.pdf.PdfCharDecoder.CharMetrics;
import de.uka.ipd.idaho.im.pdf.PdfCharDecoder.ScoredCharSignature;
import de.uka.ipd.idaho.im.pdf.PdfCharDecoder.UnicodeBlock;
import de.uka.ipd.idaho.im.pdf.PdfFont.CharDecoder;
import de.uka.ipd.idaho.im.pdf.PdfFont.CharNeighbor;
import de.uka.ipd.idaho.im.pdf.PdfFont.CharUsageStats;
import de.uka.ipd.idaho.im.pdf.PdfParser.PInlineImage;
import de.uka.ipd.idaho.im.pdf.PdfParser.PRotate;
import de.uka.ipd.idaho.im.pdf.PdfParser.PStream;
import de.uka.ipd.idaho.im.pdf.PdfParser.PString;
import de.uka.ipd.idaho.im.pdf.PdfParser.PTag;
import de.uka.ipd.idaho.im.pdf.PdfUtils.PdfByteInputStream;
import de.uka.ipd.idaho.im.pdf.PsParser.PsProcedure;
import de.uka.ipd.idaho.im.pdf.PsParser.PsString;
import de.uka.ipd.idaho.im.util.ImFontUtils;
import de.uka.ipd.idaho.im.utilities.ImageDisplayDialog;
import de.uka.ipd.idaho.stringUtils.StringUtils;

/**
 * Utility class for decoding embedded fonts.
 * 
 * @author sautter
 */
public class PdfFontDecoder {
	
	/**
	 * A font decoder charset is a means to restrict the range of Unicode
	 * characters the font decoder considers when decoding the glyphs contained
	 * in a font. Using a restrictive charset can vastly speed up decoding of
	 * glyphs because there are fewer characters to consider, and also prevent
	 * errors because of reduced ambiguity. On the other hand, restricting the
	 * charset too much can also incur some glyphs to be mis-decoded if the
	 * character they actually represent is not contained in the charset.
	 * <br>Regardless of whether or not the charset used for decoding a font
	 * contains them, the decoder will consider any character mapped to by a
	 * font's Unicode mapping, provided the latter is available.
	 * 
	 * @author sautter
	 */
	public static abstract class FontDecoderCharset {
		
		/** the name of the charset */
		public final String name;
		
		private boolean verifyUnicodeMapped = true;
		private boolean decodeUnicodeMapped = true;
		private boolean cleanUnicodeMapping = true;
		
		/** Constructor
		 * @param name the name of the charset
		 */
		protected FontDecoderCharset(String name) {
			this(name, true, true);
		}
		
		/** Constructor
		 * @param name the name of the charset
		 * @param vum verify characters that come with a Unicode mapping?
		 * @param dum decode characters that come with a Unicode mapping?
		 */
		protected FontDecoderCharset(String name, boolean vum, boolean dum) {
			this(name, vum, dum, true);
		}
		
		/** Constructor
		 * @param name the name of the charset
		 * @param vum verify characters that come with a Unicode mapping?
		 * @param dum decode characters that come with a Unicode mapping?
		 * @param cum clean Unicode mapping (of duplicates, etc.)?
		 */
		FontDecoderCharset(String name, boolean vum, boolean dum, boolean cum) {
			this.name = name;
			this.verifyUnicodeMapped = vum;
			this.decodeUnicodeMapped = dum;
			this.cleanUnicodeMapping = cum;
		}
		
		/**
		 * Test if the charset contains a specific character. If this method
		 * returns <code>false</code>, the font decoder will not consider the
		 * argument character for decoding glyphs.
		 * @param ch the character to test
		 * @return true if the argument character is to be considered in glyph
		 *        decoding, false otherwise
		 */
		public abstract boolean containsChar(char ch);
		
		/**
		 * Check whether or not to graphically verify characters that come with
		 * a Unicode mapping. If verification is active, characters whose
		 * Unicode mapping fails to verify will be fully decoded even if full
		 * decoding is inactive. Refraining from doing so can save some
		 * decoding effort, while still generating valid decodings of fonts
		 * and individual characters that come without a mapping. However, it
		 * will not identify and correct any erroneous of malicious Unicode
		 * mappings.
		 * @return true if Unicode mapped characters should be verified
		 */
		public boolean verifyUnicodeMapped() {
			return this.verifyUnicodeMapped;
		}
		
		/**
		 * Check whether or not to fully decode characters that come with a
		 * Unicode mapping. Refraining from doing so can save considerable
		 * decoding effort, while still generating valid decodings of fonts
		 * and individual characters that come without a mapping.
		 * @return true if each and every character should be decoded
		 */
		public boolean decodeUnicodeMapped() {
			return this.decodeUnicodeMapped;
		}
		
		/**
		 * Check whether or no to clean the overall Unicode mapping, i.e.,
		 * resolve conflicts of multiple characters mapping to the same Unicode
		 * point.
		 * @return true if the Unicode mapping should be cleaned
		 */
		public boolean cleanUnicodeMapping() {
			return this.cleanUnicodeMapping;
		}
		
		/**
		 * Combine two charsets to form a union. The name of the returned
		 * charset is the names of the two argument charsets concatenated, with
		 * a '+' in between. As lookups in the returned charset will always
		 * consult the first argument first, it is beneficial to specify the
		 * larger or more general charset as the first argument.
		 * @param fdc1 the first charset to union
		 * @param fdc2 the second charset to union
		 * @return a charset representing the union of the two argument ones
		 */
		public static FontDecoderCharset union(final FontDecoderCharset fdc1, final FontDecoderCharset fdc2) {
			if (fdc1 == fdc2)
				return fdc1;
			else if (fdc1 == VERIFY_MAPPED)
				return new FontDecoderCharset(fdc2.name, true, false) {
					public boolean containsChar(char ch) {
						return fdc2.containsChar(ch);
					}
				};
			else if (fdc2 == VERIFY_MAPPED)
				return new FontDecoderCharset(fdc1.name, true, false) {
					public boolean containsChar(char ch) {
						return fdc1.containsChar(ch);
					}
				};
			else if (fdc1 == DECODE_UNMAPPED)
				return new FontDecoderCharset(fdc2.name, false, false) {
					public boolean containsChar(char ch) {
						return fdc2.containsChar(ch);
					}
				};
			else if (fdc2 == DECODE_UNMAPPED)
				return new FontDecoderCharset(fdc1.name, false, false) {
					public boolean containsChar(char ch) {
						return fdc1.containsChar(ch);
					}
				};
			else if (fdc1 == UNICODE)
				return fdc1;
			else if (fdc2 == UNICODE)
				return fdc2;
			else if ((fdc1 == NO_DECODING) || (fdc1 == RENDER_ONLY) || (fdc1 == VECTORIZED_OCR_DECODING))
				return fdc2;
			else if ((fdc2 == NO_DECODING) || (fdc2 == RENDER_ONLY) || (fdc2 == VECTORIZED_OCR_DECODING))
				return fdc1;
			FontDecoderCharset fdcUnion = new FontDecoderCharset((fdc1.name + '+' + fdc2.name), (fdc1.verifyUnicodeMapped || fdc2.verifyUnicodeMapped), (fdc1.decodeUnicodeMapped || fdc2.decodeUnicodeMapped)) {
				public boolean containsChar(char ch) {
					return (fdc1.containsChar(ch) || fdc2.containsChar(ch));
				}
			};
			return fdcUnion;
		}
		
		/**
		 * Combine two charsets to form an intersection. The name of the
		 * returned charset is the names of the two argument charsets
		 * concatenated, with an '&' in between. As lookups in the returned
		 * charset will always consult the first argument first, it is
		 * beneficial to specify the smaller or more restrictive charset as the
		 * first argument.
		 * @param fdc1 the first charset to intersect
		 * @param fdc2 the second charset to intersect
		 * @return a charset representing the intersection of the two argument
		 *        ones
		 */
		public static FontDecoderCharset intersect(final FontDecoderCharset fdc1, final FontDecoderCharset fdc2) {
			if (fdc1 == fdc2)
				return fdc1;
			if ((fdc1 == VERIFY_MAPPED) || (fdc2 == VERIFY_MAPPED))
				union(fdc1, fdc2); // need to do union, as 'verify' marker does not contain any chars
			else if ((fdc1 == DECODE_UNMAPPED) || (fdc2 == DECODE_UNMAPPED))
				union(fdc1, fdc2); // need to do union, as 'unmapped' marker does not contain any chars
			else if (fdc1 == UNICODE)
				return fdc2;
			else if (fdc2 == UNICODE)
				return fdc1;
			else if ((fdc1 == NO_DECODING) || (fdc1 == RENDER_ONLY))
				return fdc1;
			else if ((fdc2 == NO_DECODING) || (fdc2 == RENDER_ONLY))
				return fdc2;
			return new FontDecoderCharset(fdc1.name + '&' + fdc2.name) {
				public boolean containsChar(char ch) {
					return (fdc1.containsChar(ch) && fdc2.containsChar(ch));
				}
			};
		}
	}
	
	/** font decoder charset containing all of Unicode */
	public static final FontDecoderCharset UNICODE = new FontDecoderCharset("Unicode") {
		public boolean containsChar(char ch) {
			return true;
		}
	};
	
	/** font decoder charset containing various symbols, namely the characters from the Mathematical Unicode blocks as well as those from Currency Symbols, Letterlike Symbols, Misc Technical, and Misc Symbols */
	public static final FontDecoderCharset SYMBOLS = new FontDecoderCharset("Symbols") {
		public boolean containsChar(char ch) {
			return (MATH.containsChar(ch) || ((ch >= 0x20A0) && (ch <= 0x20CF)) || ((ch >= 0x2100) && (ch <= 0x214F)) || ((ch >= 0x2300) && (ch <= 0x23FF)) || ((ch >= 0x2600) && (ch <= 0x26FF)));
		}
	};
	
	/** font decoder charset containing mathematical symbols, namely the characters from the Mathematical Operators and the various Mathematical Symbols Unicode blocks */
	public static final FontDecoderCharset MATH = new FontDecoderCharset("Math") {
		public boolean containsChar(char ch) {
			return (((ch >= 0x2200) && (ch <= 0x22FF)) || ((ch >= 0x27C0) && (ch <= 0x27EF)) || ((ch >= 0x2980) && (ch <= 0x29FF)) || ((ch >= 0x2A00) && (ch <= 0x2AFF)));
		}
	};
	
	/** font decoder charset containing Latin characters only, namely the characters from the Basic Latin and the various Latin Extended Unicode blocks, as well as Combining Diacritic Marks */
	public static final FontDecoderCharset LATIN_FULL = new FontDecoderCharset("LatinFull") {
		public boolean containsChar(char ch) {
			return ((ch <= 0x024F) || ((ch >= 0x0300) && (ch <= 0x036F)) || ((ch >= 0x2C60) && (ch <= 0x2C7F)) || ((ch >= 0x1E00) && (ch <= 0x1EFF)));
		}
	};
	
	/** font decoder charset containing Latin characters only, namely the characters from the Basic Latin and Latin-1 Supplement Unicode blocks */
	public static final FontDecoderCharset LATIN = new FontDecoderCharset("Latin") {
		public boolean containsChar(char ch) {
			return (ch <= 0xFF);
		}
	};
	
	/** font decoder charset containing Basic Latin characters only, i.e., the characters from the Basic Latin Unicode block */
	public static final FontDecoderCharset LATIN_BASIC = new FontDecoderCharset("LatinBasic") {
		public boolean containsChar(char ch) {
			return (ch < 0x7F);
		}
	};
	
	/** font decoder charset indicating that the glyphs of a font should be verified even if they come with a Unicode mapping (union with any other charset to use) */
	public static final FontDecoderCharset VERIFY_MAPPED = new FontDecoderCharset("VerifyMapped", true, false) {
		public boolean containsChar(char ch) {
			return false;
		}
	};
	
	/** font decoder charset indicating that the glyphs of a font should be decoded only if they come without a Unicode mapping (union with any other charset to use) */
	public static final FontDecoderCharset DECODE_UNMAPPED = new FontDecoderCharset("DecodeUnmapped", false, false) {
		public boolean containsChar(char ch) {
			return false;
		}
	};
	
	/** font decoder charset indicating that the glyphs of a font should be rendered for later correction, but not decoded */
	public static final FontDecoderCharset RENDER_ONLY = new FontDecoderCharset("RenderOnly", false, false) {
		public boolean containsChar(char ch) {
			return false;
		}
	};
	
	/** font decoder charset indicating that the glyphs of a font should be rendered for later correction, but not decoded, and that multiple glyphs might map to same Unicode point */
	public static final FontDecoderCharset VECTORIZED_OCR_DECODING = new FontDecoderCharset("VectorizedOCR", false, false, false) {
		public boolean containsChar(char ch) {
			return false;
		}
	};
	
	/** font decoder charset indicating that the glyphs of a font should be neither rendered nor decoded */
	public static final FontDecoderCharset NO_DECODING = new FontDecoderCharset("NoDecoding", false, false) {
		public boolean containsChar(char ch) {
			return false;
		}
	};
	
	/**
	 * Set based implementation of a custom charset.
	 * 
	 * @author sautter
	 */
	public static final class CustomFontDecoderCharset extends FontDecoderCharset {
		private HashSet chars = new HashSet();
		private FontDecoderCharset baseCharset;
		
		/** Constructor
		 * @param name the name for the charset
		 */
		public CustomFontDecoderCharset(String name) {
			super(name);
			this.baseCharset = null;
		}
		
		/** Constructor
		 * @param name the name for the charset
		 * @param baseCharset a base charset to extend upon
		 */
		public CustomFontDecoderCharset(String name, FontDecoderCharset baseCharset) {
			super(((baseCharset == null) ? "" : (baseCharset.name + '+')) + name);
			this.baseCharset = baseCharset;
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.im.pdf.PdfFontDecoder.FontDecoderCharset#containsChar(char)
		 */
		public boolean containsChar(char ch) {
			return (((this.baseCharset != null) && this.baseCharset.containsChar(ch)) || (this.chars.contains(new Character(ch))));
		}
		
		/**
		 * Add a single character to the charset
		 * @param ch the character to add
		 */
		public void addChar(char ch) {
			this.chars.add(new Character(ch));
		}
		
		/**
		 * Add a single character to the charset. The argument integer has to
		 * be in the valid range, i.e., <code>0 &lt; ch &lt; 65536</code> has
		 * to hold.
		 * @param ch the character to add
		 */
		public void addChar(int ch) {
			this.chars.add(new Character((char) ch));
		}
		
		/**
		 * Add a range of characters to the charset. The argument integers have
		 * to be in the valid range, i.e., <code>0 &lt; minCh &lt;= maxCh &lt;
		 * 65536</code> has to hold.
		 * @param minCh the first character to add (inclusive)
		 * @param maxCh the last character to add (inclusive)
		 */
		public void addChars(int minCh, int maxCh) {
			for (int ch = minCh; ch <= maxCh; ch++)
				this.chars.add(new Character((char) ch));
		}
		
		/**
		 * Add the characters from some charset to this charset
		 * @param ch the character to add
		 */
		public void addChars(FontDecoderCharset fdc) {
			if (fdc == this)
				return;
			if (this.baseCharset == null)
				this.baseCharset = fdc;
			else this.baseCharset = union(this.baseCharset, fdc);
		}
		
		/**
		 * Read a custom charset from the character data provided by a Reader.
		 * For further explanation on how this method interprets the data
		 * provided by the argument Reader, please refer to the three-argument
		 * version of this method.
		 * @param name the name for the custom charset
		 * @param in the stream providing the data for the chatset
		 * @return a charset comprising the characters specified by the
		 *        argument character stream
		 * @throws IOException
		 */
		public static CustomFontDecoderCharset readCharSet(String name, Reader in) throws IOException {
			return readCharSet(name, null, in);
		}
		
		/**
		 * Read a custom charset from the character data provided by a Reader.
		 * That data is interpreted line-wise, each line representing a single
		 * character in hexadecimal representation, or a range of thus
		 * represented characters if separated by a dash. A line staring with a
		 * double slash is ignored as a comment. A line staring with '#' is
		 * interpreted (case insensitively) as the ISO name of a Unicode block,
		 * and all characters in that block are included. A line staring with
		 * '@' is interpreted (case insensitively) as the name of one of the
		 * constant charsets available in <code>PdfFontDecoder</code>, or as
		 * the name of a charset hosted by a registered custom provider, with
		 * the latter taking precedence.
		 * @param name the name for the custom charset
		 * @param baseCharset a base charset to extend upon
		 * @param in the stream providing the data for the chatset
		 * @return a charset comprising the characters specified by the
		 *        argument character stream
		 * @throws IOException
		 */
		public static CustomFontDecoderCharset readCharSet(String name, FontDecoderCharset baseCharset, Reader in) throws IOException {
			CustomFontDecoderCharset cfdc = new CustomFontDecoderCharset(name, baseCharset);
			
			//	read charset elements, line by line
			BufferedReader br = ((in instanceof BufferedReader) ? ((BufferedReader) in) : new BufferedReader(in));
			for (String csLine; (csLine = br.readLine()) != null;) {
				csLine = csLine.trim();
				if (csLine.startsWith("//"))
					continue; // ignore comments
				if (csLine.indexOf("//") != -1)
					csLine = csLine.substring(0, csLine.indexOf("//")).trim(); // truncate tailing comments
				if (csLine.length() == 0)
					continue;
				
				//	single hex character
				if (csLine.matches("(0x)?[0-9A-Fa-f]{1,4}"))
					cfdc.addChar(Integer.parseInt(trimHexPrefix(csLine), 16));
				
				//	hex character range
				else if (csLine.matches("(0x)?[0-9A-Fa-f]{1,4}\\s*\\-\\s*(0x)?[0-9A-Fa-f]{1,4}")) {
					String[] csLineParts = csLine.split("\\s*\\-\\s*");
					int minCh = Integer.parseInt(trimHexPrefix(csLineParts[0]), 16);
					int maxCh = Integer.parseInt(trimHexPrefix(csLineParts[1]), 16);
					cfdc.addChars(minCh, maxCh);
				}
				
				//	named Unicode block
				else if (csLine.startsWith("#")) {
					UnicodeBlock ucb = PdfCharDecoder.getUnicodeBlock(csLine.substring("#".length()).trim());
					if (ucb != null)
						cfdc.addChars(ucb.minChar, ucb.maxChar);
					else System.out.println("Unknown Unicode block in custom charset: " + csLine);
				}
				
				//	named charset
				else if (csLine.startsWith("@")) {
					FontDecoderCharset fdc = getNamedCharset(csLine.substring("@".length()).trim());
					if (fdc != null)
						cfdc.addChars(fdc);
					else System.out.println("Unknown referenced charset in custom charset: " + csLine);
				}
				
				//	something strange ...
				else System.out.println("Strange line in custom charset: " + csLine);
			}
			
			//	finally ...
			return cfdc;
		}
		
		private static String trimHexPrefix(String str) {
			return (str.startsWith("0x") ? str.substring("0x".length()) : str);
		}
		
		private static FontDecoderCharset getNamedCharset(String name) {
			
			//	check custom provided character sets first
			for (Iterator cpit = charsetProviders.iterator(); cpit.hasNext();) {
				FontDecoderCharsetProvider cp = ((FontDecoderCharsetProvider) cpit.next());
				Reader cr = cp.getNamedCharset(name);
				if (cr != null) try {
					if ((cr instanceof StringReader) || (cr instanceof CharSequenceReader))
						return readCharSet(name, cr); // no use buffering in-memory data
					StringBuffer csb = new StringBuffer();
					char[] buffer = new char[1024];
					for (int r = 0; (r = cr.read(buffer, 0, buffer.length)) != -1;)
						csb.append(buffer, 0, r);
					cr.close();
					return readCharSet(name, new CharSequenceReader(csb));
				}
				catch (IOException ioe) {
					ioe.printStackTrace(System.out);
				}
			}
			
			//	check built-in character sets TODO extend this
			if ("LatinFull".equalsIgnoreCase(name))
				return LATIN_FULL;
			else if ("Latin".equalsIgnoreCase(name))
				return LATIN;
			else if ("LatinBasic".equalsIgnoreCase(name))
				return LATIN_BASIC;
			else if ("Math".equalsIgnoreCase(name))
				return MATH;
			else if ("Symbols".equalsIgnoreCase(name))
				return SYMBOLS;
			
			//	nothing found
			return null;
		}
		
		/**
		 * Provider of custom font decoder character sets, i.e., sets of Unicode
		 * points to consider in graphics driven character decoding.<br/>
		 * The syntax of such a custom character set definition is line based,
		 * with each line specifying a (range of) Unicode point(s) in one of the
		 * following ways (all other lines will be ignored, as will everything
		 * between a double slash (<code>//</code>) and the subsequent line
		 * break):
		 * <br/>- <code>0x&lt;HHHH&gt;</code>, with <code>&lt;HHHH&gt;</code>
		 *        representing any HEX code (digits <code>0-F</code>) between
		 *        (inclusive) <code>0000</code> and <code>FFFF</code>: that
		 *        very Unicode point (ignored if not printable)
		 * <br/>- <code>0x&lt;HHHH&gt;-0x&lt;IIII&gt;</code>, with 
		 *        <code>&lt;HHHH&gt;</code> and <code>&lt;IIII&gt;</code> 
		 *        representing any HEX code (digits <code>0-F</code>) between
		 *        (inclusive) <code>0000</code> and <code>FFFF</code>: a range
		 *        of the above
		 * <br/>- <code>#&lt;UcBlockName&gt;</code>, with <code>&lt;UcBlockName&gt;</code>
		 *        representing the name of a Unicode block, e.g. "Basic Latin"
		 *        or "Latin Extended-B" (mind the spaces); for a full list see
		 *        http://en.wikipedia.org/wiki/Unicode_block
		 * <br/>- <code>@&lt;CharSetName&gt;</code>, with <code>&lt;CharSetName&gt;</code>
		 *        representing the name of a named character set, to be resolved
		 *        via an implementation of this very interface; this facilitates
		 *        recursive composition of character sets
		 * 
		 * @author sautter
		 */
		public static interface FontDecoderCharsetProvider {
			
			/**
			 * Retrieve by name a Reader providing the data of a custom font
			 * decoder character set. The syntax of the data available from the
			 * returned Reader has to follow the above specification. If an
			 * implementation of this interface is not able to resolve a given
			 * character set name, it should simply return null rather than
			 * throwing an exception.
			 * @param name the name of the sought character set
			 * @return a Reader providing the data for the character set with
			 *            the argument name
			 */
			public abstract Reader getNamedCharset(String name);
			
			/**
			 * Retrieve the available character set names.
			 * @return an array the available character set names
			 */
			public abstract String[] getCharsetNames();
		}
		
		/**
		 * Retrieve the names of the character sets made available by the
		 * currently registered providers.
		 * @return an array holding the character set names
		 */
		public static String[] getProviderCharsetNames() {
			TreeSet pcnSet = new TreeSet(String.CASE_INSENSITIVE_ORDER);
			for (Iterator cpit = charsetProviders.iterator(); cpit.hasNext();) {
				FontDecoderCharsetProvider cp = ((FontDecoderCharsetProvider) cpit.next());
				String[] pcns = cp.getCharsetNames();
				if (pcns != null)
					pcnSet.addAll(Arrays.asList(pcns));
			}
			return ((String[]) pcnSet.toArray(new String[pcnSet.size()]));
		}
		
		/**
		 * Register a character set provider to help decoding fonts.
		 * @param fdcp the character set provider to register
		 */
		public static void addCharsetProvider(FontDecoderCharsetProvider fdcp) {
			if (fdcp != null)
				charsetProviders.add(fdcp);
		}
		
		/**
		 * Unregister a character set provider.
		 * @param fdcp the character set provider to remove
		 */
		public static void removeCharsetProvider(FontDecoderCharsetProvider fdcp) {
			charsetProviders.remove(fdcp);
		}
		
		private static LinkedHashSet charsetProviders = new LinkedHashSet();
	}
	
	private static abstract class OpReceiver {
//		int iSideBearing = 0;
//		int iUpBearing = 0;
		float iWidth = 0;
		float iHeight = 0;
		int rWidth = 0;
		int x = 0;
		int y = 0;
		int segCount = 0; // overall number of segments
		int subSegCount = 0; // number of segments since last call to moveTo()
		int sx = 0;
		int sy = 0;
		abstract void moveTo(int dx, int dy);
		abstract void moveToAbs(int x, int y);
		abstract void lineTo(int dx, int dy);
		abstract void lineToAbs(int x, int y);
		abstract void curveTo(int dx1, int dy1, int dx2, int dy2);
		abstract void curveToAbs(int x1, int y1, int x2, int y2);
		abstract void curveTo(int dx1, int dy1, int dx2, int dy2, int dx3, int dy3);
		abstract void curveToAbs(int x1, int y1, int x2, int y2, int x3, int y3);
		abstract void closePath();
		abstract void closePath(int dx, int dy, boolean explicit);
	}
	
	private static class OpTracker extends OpReceiver {
		int minX = 0;
		int minY = 0;
		int minPaintX = Integer.MAX_VALUE;
		int minPaintY = Integer.MAX_VALUE;
		int maxX = 0;
		int maxY = 0;
		boolean isMultiPath = false;
		Path2D.Float path = new Path2D.Float(Path2D.WIND_NON_ZERO);
		StringBuffer ops = new StringBuffer("Ops:");
		final boolean debug;
		OpTracker(boolean debug) {
			this.debug = debug;
		}
		void moveTo(int dx, int dy) {
			if (this.segCount != 0)
				this.isMultiPath = true;
			if (this.subSegCount != 0)
				this.path.closePath();
			this.subSegCount = 0;
			this.path.moveTo((this.x + dx), (this.y + dy));
			this.doSegTo(dx, dy, false);
			this.ops.append(" M" + dx + "/" + dy);
			if (this.debug) {
				System.out.println("Move to " + dx + "/" + dy + ":");
				System.out.println(" at " + this.x + "/" + this.y);
				System.out.println(" " + this.minX + " < X < " + this.maxX);
				System.out.println(" " + this.minY + " < Y < " + this.maxY);
			}
		}
		void moveToAbs(int x, int y) {
			if (this.segCount != 0)
				this.isMultiPath = true;
			if (this.subSegCount != 0)
				this.path.closePath();
			this.subSegCount = 0;
			this.path.moveTo(x, y);
			this.x = x;
			this.minX = Math.min(this.minX, this.x);
			this.maxX = Math.max(this.maxX, this.x);
			this.y = y;
			this.minY = Math.min(this.minY, this.y);
			this.maxY = Math.max(this.maxY, this.y);
			this.ops.append(" MA" + x + "/" + y);
			if (this.debug) {
				System.out.println("Move abs to " + x + "/" + y + ":");
				System.out.println(" at " + this.x + "/" + this.y);
				System.out.println(" " + this.minX + " < X < " + this.maxX);
				System.out.println(" " + this.minY + " < Y < " + this.maxY);
			}
		}
		void lineTo(int dx, int dy) {
			if (this.subSegCount == 0) {
				this.sx = this.x;
				this.sy = this.y;
			}
			this.path.lineTo((this.x + dx), (this.y + dy));
			this.doSegTo(dx, dy, true);
			this.segCount++;
			this.subSegCount++;
			this.ops.append(" L" + dx + "/" + dy);
		}
		void lineToAbs(int x, int y) {
			if (this.subSegCount == 0) {
				this.sx = this.x;
				this.sy = this.y;
			}
			this.path.lineTo(x, y);
			this.doSegToAbs(x, y, true);
			this.segCount++;
			this.subSegCount++;
			this.ops.append(" LA" + x + "/" + y);
		}
		private void doSegTo(int dx, int dy, boolean painted) {
			if (painted) {
				this.minPaintX = Math.min(this.minPaintX, this.x);
				this.minPaintY = Math.min(this.minPaintY, this.y);
			}
			this.x += dx;
			this.minX = Math.min(this.minX, this.x);
			this.maxX = Math.max(this.maxX, this.x);
			this.y += dy;
			this.minY = Math.min(this.minY, this.y);
			this.maxY = Math.max(this.maxY, this.y);
			if (painted) {
				this.minPaintX = Math.min(this.minPaintX, this.x);
				this.minPaintY = Math.min(this.minPaintY, this.y);
			}
			if (this.debug) {
				System.out.println("Line to " + dx + "/" + dy + ":");
				System.out.println(" at " + this.x + "/" + this.y);
				System.out.println(" " + this.minX + " < X < " + this.maxX);
				System.out.println(" " + this.minY + " < Y < " + this.maxY);
			}
		}
		private void doSegToAbs(int x, int y, boolean painted) {
			if (painted) {
				this.minPaintX = Math.min(this.minPaintX, this.x);
				this.minPaintY = Math.min(this.minPaintY, this.y);
			}
			this.x = x;
			this.minX = Math.min(this.minX, this.x);
			this.maxX = Math.max(this.maxX, this.x);
			this.y = y;
			this.minY = Math.min(this.minY, this.y);
			this.maxY = Math.max(this.maxY, this.y);
			if (painted) {
				this.minPaintX = Math.min(this.minPaintX, this.x);
				this.minPaintY = Math.min(this.minPaintY, this.y);
			}
			if (this.debug) {
				System.out.println("Line to abs " + x + "/" + y + ":");
				System.out.println(" at " + this.x + "/" + this.y);
				System.out.println(" " + this.minX + " < X < " + this.maxX);
				System.out.println(" " + this.minY + " < Y < " + this.maxY);
			}
		}
		private static final int curveSteps = 16;
		void curveTo(int dx1, int dy1, int dx2, int dy2) {
			if (this.subSegCount == 0) {
				this.sx = this.x;
				this.sy = this.y;
			}
			this.path.quadTo((this.x + dx1), (this.y + dy1), (this.x + dx1 + dx2), (this.y + dy1 + dy2));
			/* simply line to control point if lying inside rectagle spanned
			 * open by current position and target point, which is basically
			 * the case if both deltas have the same direction (signum) in both
			 * dimensions */
			if ((((dx1 <= 0) && (dx2 <= 0)) || ((dx1 >= 0) && (dx2 >= 0))) && (((dy1 <= 0) && (dy2 <= 0)) || ((dy1 >= 0) && (dy2 >= 0)))) {
				this.doSegTo(dx1, dy1, true);
				this.doSegTo(dx2, dy2, true);
			}
			/* otherwise, compute vertex of parabola and use that, as including
			 * control point proper in size estimation incurs catastrophic size
			 * over-estimation on tight turns close to 180° whose control point
			 * is either way up or way down (formula see below) */
			else {
				/* run along curve and find extremes:
				 * - should be fast using integer arithmetics by projecting [0,1] on [0,16]
				 * - assume starting point at 0/0 and relative movement from there
				 * - rounding errors not an issue, as formula yields absolute offset from starting point (absolute coordinates, really, but those are offsets from 0/0)
				 * - courtesy https://pomax.github.io/bezierinfo/ :
x(t) = p0x * (1-t)*(1-t) + p1x * (1-t)*t + p2x * t*t
y(t) = p0y * (1-t)*(1-t) + p1y * (1-t)*t + p2y * t*t

=== expand [0,1] to [0,16] and divide out again
x(t) = (p0x * (16-t)*(16-t) + p1x * (16-t)*t + p2x * t*t) / 16*16
y(t) = (p0y * (16-t)*(16-t) + p1y * (16-t)*t + p2y * t*t) / 16*16

=== consider p0 always is 0/0 in relative movement
x(t) = (p1x * (16-t)*t + p2x * t*t) / 16*16
y(t) = (p1y * (16-t)*t + p2y * t*t) / 16*16
				 */
				
				//	assuming p0 to be at 0/0, as we're after relative positions anyway
//				int p0x = 0;
//				int p0y = 0;
				int p1x = dx1;
				int p1y = dy1;
				int p2x = (dx1 + dx2);
				int p2y = (dy1 + dy2);
				if (this.debug) System.out.println("Curving to " + dx1 + "/" + dy1 + "(" + p1x + "/" + p1y + ") and " + dx2 + "/" + dy2 + "(" + p2x + "/" + p2y + ")");
				
//				int lpx = p0x;
//				int lpy = p0y;
				int lpx = 0;
				int lpy = 0;
				for (int t = 1; t <= curveSteps; t++) {
					int r = (curveSteps - t);
//					int px = (((p0x * r*r) + (2 * p1x * r*t) + (p2x * t*t)) / (curveSteps * curveSteps));
//					int py = (((p0y * r*r) + (2 * p1y * r*t) + (p2y * t*t)) / (curveSteps * curveSteps));
					int px = (((2 * p1x * r*t) + (p2x * t*t)) / (curveSteps * curveSteps));
					int py = (((2 * p1y * r*t) + (p2y * t*t)) / (curveSteps * curveSteps));
					this.doSegTo((px - lpx), (py - lpy), true);
					if (this.debug) System.out.println(" - step " + t + " to " + (px - lpx) + "/" + (py - lpy) + "(" + px + "/" + py + ")");
					lpx = px;
					lpy = py;
				}
			}
			this.segCount++;
			this.subSegCount++;
			this.ops.append(" C" + dx1 + "/" + dy1 + "," + dx2 + "/" + dy2);
		}
		void curveToAbs(int x1, int y1, int x2, int y2) {
			if (this.subSegCount == 0) {
				this.sx = this.x;
				this.sy = this.y;
			}
			this.path.quadTo(x1, y1, x2, y2);
			/* run along curve and find extremes:
			 * - should be fast using integer arithmetics by projecting [0,1] on [0,16]
			 * - assume starting point at 0/0 and relative movement from there
			 * - rounding errors not an issue, as formula yields absolute offset from starting point (absolute coordinates, really, but those are offsets from 0/0)
			 * - courtesy https://pomax.github.io/bezierinfo/ :
x(t) = p0x * (1-t)*(1-t) + p1x * (1-t)*t + p2x * t*t
y(t) = p0y * (1-t)*(1-t) + p1y * (1-t)*t + p2y * t*t

=== expand [0,1] to [0,16] and divide out again
x(t) = (p0x * (16-t)*(16-t) + p1x * (16-t)*t + p2x * t*t) / 16*16
y(t) = (p0y * (16-t)*(16-t) + p1y * (16-t)*t + p2y * t*t) / 16*16

=== consider p0 always is 0/0 in relative movement
x(t) = (p1x * (16-t)*t + p2x * t*t) / 16*16
y(t) = (p1y * (16-t)*t + p2y * t*t) / 16*16
			 */
			
			//	assuming p0 to be at 0/0, as we're after relative positions anyway
			int p0x = this.x;
			int p0y = this.y;
//			int p1x = dx1;
//			int p1y = dy1;
//			int p2x = (dx1 + dx2);
//			int p2y = (dy1 + dy2);
			if (this.debug) System.out.println("Curving to abs " + x1 + "/" + y1 + " and " + x2 + "/" + y2);
			
//			int lpx = p0x;
//			int lpy = p0y;
//			int lpx = 0;
//			int lpy = 0;
			for (int t = 1; t <= curveSteps; t++) {
				int r = (curveSteps - t);
				int px = (((p0x * r*r) + (2 * x1 * r*t) + (x2 * t*t)) / (curveSteps * curveSteps));
				int py = (((p0y * r*r) + (2 * y1 * r*t) + (y2 * t*t)) / (curveSteps * curveSteps));
//				int px = (((2 * p1x * r*t) + (p2x * t*t)) / (curveSteps * curveSteps));
//				int py = (((2 * p1y * r*t) + (p2y * t*t)) / (curveSteps * curveSteps));
				this.doSegToAbs(px, py, true);
				if (this.debug) System.out.println(" - step abs " + t + " to " + px + "/" + py);
//				lpx = px;
//				lpy = py;
			}
			this.segCount++;
			this.subSegCount++;
			this.ops.append(" CA" + x1 + "/" + y1 + "," + x2 + "/" + y2);
		}
		void curveTo(int dx1, int dy1, int dx2, int dy2, int dx3, int dy3) {
			this.path.curveTo((this.x + dx1), (this.y + dy1), (this.x + dx1 + dx2), (this.y + dy1 + dy2), (this.x + dx1 + dx2 + dx3), (this.y + dy1 + dy2 + dy3));
			
			/* run along curve and find extremes:
			 * - should be fast using integer arithmetics by projecting [0,1] on [0,16]
			 * - assume starting point at 0/0 and relative movement from there
			 * - rounding errors not an issue, as formula yields absolute offset from starting point (absolute coordinates, really, but those are offsets from 0/0)
			 * - courtesy https://pomax.github.io/bezierinfo/ :
x(t) = p0x * (1-t)*(1-t)*(1-t) + 3 * p1x * (1-t)*(1-t)*t + 3 * p2x * (1-t)*t*t + p3x * t*t*t
y(t) = p0y * (1-t)*(1-t)*(1-t) + 3 * p1y * (1-t)*(1-t)*t + 3 * p2y * (1-t)*t*t + p3x * t*t*t

=== expand [0,1] to [0,16] and divide out again
x(t) = p0x * (16-t)*(16-t)*(16-t) + 3 * p1x * (16-t)*(16-t)*t + 3 * p2x * (16-t)*t*t + p3x * t*t*t / 16*16*16
y(t) = p0y * (16-t)*(16-t)*(16-t) + 3 * p1y * (16-t)*(16-t)*t + 3 * p2y * (16-t)*t*t + p3y * t*t*t / 16*16*16

=== consider p0 always is 0/0 in relative movement
x(t) = 3 * p1x * (16-t)*(16-t)*t + 3 * p2x * (16-t)*t*t + p3x * t*t*t / 16*16*16
y(t) = 3 * p1y * (16-t)*(16-t)*t + 3 * p2y * (16-t)*t*t + p3y * t*t*t / 16*16*16
			 */
			
			//	assuming p0 to be at 0/0, as we're after relative positions anyway
//			int p0x = 0;
//			int p0y = 0;
			int p1x = dx1;
			int p1y = dy1;
			int p2x = (dx1 + dx2);
			int p2y = (dy1 + dy2);
			int p3x = (dx1 + dx2 + dx3);
			int p3y = (dy1 + dy2 + dy3);
			if (this.debug) System.out.println("Curving to " + dx1 + "/" + dy1 + "(" + p1x + "/" + p1y + "), " + dx2 + "/" + dy2 + "(" + p2x + "/" + p2y + "), and " + dx3 + "/" + dy3 + "(" + p3x + "/" + p3y + ")");
			
//			int lpx = p0x;
//			int lpy = p0y;
			int lpx = 0;
			int lpy = 0;
			for (int t = 1; t <= curveSteps; t++) {
				int r = (curveSteps - t);
//				int px = (((p0x * r*r*r) + (3 * p1x * r*r*t) + (3 * p2x * r*t*t) + (p3x * t*t*t)) / (curveSteps * curveSteps * curveSteps));
//				int py = (((p0y * r*r*r) + (3 * p1y * r*r*t) + (3 * p2y * r*t*t) + (p3y * t*t*t)) / (curveSteps * curveSteps * curveSteps));
				int px = (((3 * p1x * r*r*t) + (3 * p2x * r*t*t) + (p3x * t*t*t)) / (curveSteps * curveSteps * curveSteps));
				int py = (((3 * p1y * r*r*t) + (3 * p2y * r*t*t) + (p3y * t*t*t)) / (curveSteps * curveSteps * curveSteps));
				this.doSegTo((px - lpx), (py - lpy), true);
				if (this.debug) System.out.println(" - step " + t + " to " + (px - lpx) + "/" + (py - lpy) + "(" + px + "/" + py + ")");
				lpx = px;
				lpy = py;
			}
			this.segCount++;
			this.subSegCount++;
			this.ops.append(" C" + dx1 + "/" + dy1 + "," + dx2 + "/" + dy2 + "," + dx3 + "/" + dy3);
		}
		void curveToAbs(int x1, int y1, int x2, int y2, int x3, int y3) {
			this.path.curveTo(x1, y1, x2, y2, x3, y3);
			
			/* run along curve and find extremes:
			 * - should be fast using integer arithmetics by projecting [0,1] on [0,16]
			 * - assume starting point at 0/0 and relative movement from there
			 * - rounding errors not an issue, as formula yields absolute offset from starting point (absolute coordinates, really, but those are offsets from 0/0)
			 * - courtesy https://pomax.github.io/bezierinfo/ :
x(t) = p0x * (1-t)*(1-t)*(1-t) + 3 * p1x * (1-t)*(1-t)*t + 3 * p2x * (1-t)*t*t + p3x * t*t*t
y(t) = p0y * (1-t)*(1-t)*(1-t) + 3 * p1y * (1-t)*(1-t)*t + 3 * p2y * (1-t)*t*t + p3x * t*t*t

=== expand [0,1] to [0,16] and divide out again
x(t) = p0x * (16-t)*(16-t)*(16-t) + 3 * p1x * (16-t)*(16-t)*t + 3 * p2x * (16-t)*t*t + p3x * t*t*t / 16*16*16
y(t) = p0y * (16-t)*(16-t)*(16-t) + 3 * p1y * (16-t)*(16-t)*t + 3 * p2y * (16-t)*t*t + p3y * t*t*t / 16*16*16

=== consider p0 always is 0/0 in relative movement
x(t) = 3 * p1x * (16-t)*(16-t)*t + 3 * p2x * (16-t)*t*t + p3x * t*t*t / 16*16*16
y(t) = 3 * p1y * (16-t)*(16-t)*t + 3 * p2y * (16-t)*t*t + p3y * t*t*t / 16*16*16
			 */
			
			//	assuming p0 to be at 0/0, as we're after relative positions anyway
			int p0x = this.x;
			int p0y = this.y;
//			int p1x = dx1;
//			int p1y = dy1;
//			int p2x = (dx1 + dx2);
//			int p2y = (dy1 + dy2);
//			int p3x = (dx1 + dx2 + dx3);
//			int p3y = (dy1 + dy2 + dy3);
			if (this.debug) System.out.println("Curving to " + x1 + "/" + y1 + ", " + x2 + "/" + y2 + ", and " + x3 + "/" + y3);
			
//			int lpx = p0x;
//			int lpy = p0y;
//			int lpx = 0;
//			int lpy = 0;
			for (int t = 1; t <= curveSteps; t++) {
				int r = (curveSteps - t);
				int px = (((p0x * r*r*r) + (3 * x1 * r*r*t) + (3 * x2 * r*t*t) + (x3 * t*t*t)) / (curveSteps * curveSteps * curveSteps));
				int py = (((p0y * r*r*r) + (3 * y1 * r*r*t) + (3 * y2 * r*t*t) + (y3 * t*t*t)) / (curveSteps * curveSteps * curveSteps));
//				int px = (((3 * p1x * r*r*t) + (3 * p2x * r*t*t) + (p3x * t*t*t)) / (curveSteps * curveSteps * curveSteps));
//				int py = (((3 * p1y * r*r*t) + (3 * p2y * r*t*t) + (p3y * t*t*t)) / (curveSteps * curveSteps * curveSteps));
				this.doSegToAbs(px, py, true);
				if (this.debug) System.out.println(" - step abs " + t + " to " + px + "/" + py);
//				lpx = px;
//				lpy = py;
			}
			this.segCount++;
			this.subSegCount++;
			this.ops.append(" CA" + x1 + "/" + y1 + "," + x2 + "/" + y2 + "," + x3 + "/" + y3);
		}
		void closePath() {
			if (this.subSegCount != 0)
				this.path.closePath();
			this.subSegCount = 0;
			this.ops.append(" O");
		}
		void closePath(int dx, int dy, boolean explicit) /* used only for TrueType */ {
			this.path.quadTo((this.x + dx), (this.y + dy), this.sx, this.sy);
			this.path.closePath();
			if (explicit)
				this.doSegTo(dx, dy, true);
			this.subSegCount = 0;
			this.ops.append(" O" + dx + "/" + dy);
		}
	}
	
	private static class OpGraphics extends OpReceiver {
		int minX;
		int minY;
		int height;
		BufferedImage img;
		Graphics2D gr;
//		Path2D.Float path = new Path2D.Float(Path2D.WIND_NON_ZERO);
		float scale = 1.0f;
		OpGraphics(int minX, int minY, int height, float scale, BufferedImage img) {
			this.minX = minX;
			this.minY = minY;
			this.height = height;
			this.scale = scale;
			this.setImage(img);
			this.gr.setColor(Color.WHITE);
			this.gr.fillRect(0, 0, img.getWidth(), img.getHeight());
			this.gr.setColor(Color.BLACK);
		}
		void setImage(BufferedImage img) {
			this.img = img;
			this.gr = this.img.createGraphics();
		}
		void lineTo(int dx, int dy) {
			if (this.subSegCount == 0) {
				this.sx = this.x;
				this.sy = this.y;
			}
			this.gr.drawLine(
					Math.round((this.scale * (this.x - this.minX)) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.height - (this.y - this.minY))) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.x - this.minX + dx)) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.height - (this.y - this.minY + dy))) + glyphOutlineFillSafetyEdge)
				);
//			this.path.lineTo((this.x + dx), (this.y + dy));
			this.x += dx;
			this.y += dy;
			this.subSegCount++;
		}
		void lineToAbs(int x, int y) {
			if (this.subSegCount == 0) {
				this.sx = this.x;
				this.sy = this.y;
			}
			this.gr.drawLine(
					Math.round((this.scale * (this.x - this.minX)) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.height - (this.y - this.minY))) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (x - this.minX)) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.height - (y - this.minY))) + glyphOutlineFillSafetyEdge)
				);
//			this.path.lineTo((this.x + dx), (this.y + dy));
			this.x = x;
			this.y = y;
			this.subSegCount++;
		}
		void curveTo(int dx1, int dy1, int dx2, int dy2) {
			if (this.subSegCount == 0) {
				this.sx = this.x;
				this.sy = this.y;
			}
			QuadCurve2D.Float qc = new QuadCurve2D.Float();
			qc.setCurve(
					Math.round((this.scale * (this.x - this.minX)) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.height - (this.y - this.minY))) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.x - this.minX + dx1)) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.height - (this.y - this.minY + dy1))) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.x - this.minX + dx1 + dx2)) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.height - (this.y - this.minY + dy1 + dy2))) + glyphOutlineFillSafetyEdge)
				);
			this.gr.draw(qc);
//			this.path.quadTo((this.x + dx1), (this.y + dy1), (this.x + dx1 + dy2), (this.y + dy1 + dy2));
			this.x += (dx1 + dx2);
			this.y += (dy1 + dy2);
			this.subSegCount++;
		}
		void curveToAbs(int x1, int y1, int x2, int y2) {
			if (this.subSegCount == 0) {
				this.sx = this.x;
				this.sy = this.y;
			}
			QuadCurve2D.Float qc = new QuadCurve2D.Float();
			qc.setCurve(
					Math.round((this.scale * (this.x - this.minX)) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.height - (this.y - this.minY))) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (x1 - this.minX)) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.height - (y1 - this.minY))) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (x2 - this.minX)) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.height - (y2 - this.minY))) + glyphOutlineFillSafetyEdge)
				);
			this.gr.draw(qc);
//			this.path.quadTo((this.x + dx1), (this.y + dy1), (this.x + dx1 + dy2), (this.y + dy1 + dy2));
			this.x = x2;
			this.y = y2;
			this.subSegCount++;
		}
		void curveTo(int dx1, int dy1, int dx2, int dy2, int dx3, int dy3) {
			if (this.subSegCount == 0) {
				this.sx = this.x;
				this.sy = this.y;
			}
			CubicCurve2D.Float cc = new CubicCurve2D.Float();
			cc.setCurve(
					Math.round((this.scale * (this.x - this.minX)) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.height - (this.y - this.minY))) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.x - this.minX + dx1)) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.height - (this.y - this.minY + dy1))) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.x - this.minX + dx1 + dx2)) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.height - (this.y - this.minY + dy1 + dy2))) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.x - this.minX + dx1 + dx2 + dx3)) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.height - (this.y - this.minY + dy1 + dy2 + dy3))) + glyphOutlineFillSafetyEdge)
				);
			this.gr.draw(cc);
//			this.path.curveTo((this.x + dx1), (this.y + dy1), (this.x + dx1 + dx2), (this.y + dy1 + dy2), (this.x + dx1 + dx2 + dx3), (this.y + dy1 + dy2 + dy3));
			this.x += (dx1 + dx2 + dx3);
			this.y += (dy1 + dy2 + dy3);
			this.subSegCount++;
		}
		void curveToAbs(int x1, int y1, int x2, int y2, int x3, int y3) {
			if (this.subSegCount == 0) {
				this.sx = this.x;
				this.sy = this.y;
			}
			CubicCurve2D.Float cc = new CubicCurve2D.Float();
			cc.setCurve(
					Math.round((this.scale * (this.x - this.minX)) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.height - (this.y - this.minY))) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (x1 - this.minX)) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.height - (y1 - this.minY))) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (x2 - this.minX)) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.height - (y2 - this.minY))) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (x3 - this.minX)) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.height - (y3 - this.minY))) + glyphOutlineFillSafetyEdge)
				);
			this.gr.draw(cc);
//			this.path.curveTo((this.x + dx1), (this.y + dy1), (this.x + dx1 + dx2), (this.y + dy1 + dy2), (this.x + dx1 + dx2 + dx3), (this.y + dy1 + dy2 + dy3));
			this.x = x3;
			this.y = y3;
			this.subSegCount++;
		}
		void moveTo(int dx, int dy) {
			if (this.subSegCount != 0)
				this.closePath();
			this.subSegCount = 0;
//			this.path.moveTo((this.x + dx), (this.y + dy));
			this.x += dx;
			this.y += dy;
		}
		void moveToAbs(int x, int y) {
			if (this.subSegCount != 0)
				this.closePath();
			this.subSegCount = 0;
//			this.path.moveTo(x, y);
			this.x = x;
			this.y = y;
		}
		void closePath() {
			this.gr.drawLine(
					Math.round((this.scale * (this.x - this.minX)) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.height - (this.y - this.minY))) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.sx - this.minX)) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.height - (this.sy - this.minY))) + glyphOutlineFillSafetyEdge)
				);
//			this.path.closePath();
			this.subSegCount = 0;
		}
		void closePath(int dx, int dy, boolean explicit) {
			QuadCurve2D.Float qc = new QuadCurve2D.Float();
			qc.setCurve(
					Math.round((this.scale * (this.x - this.minX)) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.height - (this.y - this.minY))) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.x - this.minX + dx)) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.height - (this.y - this.minY + dy))) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.sx - this.minX)) + glyphOutlineFillSafetyEdge),
					Math.round((this.scale * (this.height - (this.sy - this.minY))) + glyphOutlineFillSafetyEdge)
				);
			this.gr.draw(qc);
//			this.path.quadTo((this.x + dx), (this.y + dy), this.sx, this.sy);
//			this.path.closePath();
			if (explicit) {
				this.x += dx;
				this.y += dy;
			}
			this.subSegCount = 0;
		}
	}
	
	private static final boolean DEBUG_TYPE1_LOADING = false;
	
	static void readFontType1(byte[] data, Map dataParams, Map fd, PdfFont pFont, CharDecoder pCharDecoder, FontDecoderCharset charSet, ProgressMonitor pm) {
		pm.setInfo("   - reading meta data");
		if (DEBUG_TYPE1_LOADING) {
			System.out.println("     - got " + data.length + " bytes of data");
			System.out.println("     - data parameters are " + dataParams);
//			System.out.println("     - start of data proper:");
//			System.out.println(new String(data, 0, Math.min(data.length, 8192)));
//			System.out.println(new String(data));
		}
		
		//	read meta data up to 'eexec' plus subsequent single space char (Length1 parameter in stream dictionary)
		int length1 = ((Number) dataParams.get("Length1")).intValue();
		byte[] headData = new byte[length1];
		System.arraycopy(data, 0, headData, 0, headData.length);
		if (DEBUG_TYPE1_LOADING) {
//			System.out.println("     - header data:");
//			System.out.println(new String(headData));
		}
		
		//	get encrypted portion of data (Length2 parameter in stream dictionary)
		int length2 = ((Number) dataParams.get("Length2")).intValue();
		byte[] mainData = new byte[length2];
		System.arraycopy(data, length1, mainData, 0, mainData.length);
		if (DEBUG_TYPE1_LOADING) {
//			System.out.println("     - main data (encrypted):");
//			System.out.println(new String(mainData));
		}
		
		//	decrypt data (according to the spec, this portion always starts with 'dup', but never with a number, so chunk any leading numbers ...)
		mainData = decryptType1Data(mainData, 55665);
		String mainDataStr = new String(mainData);
		if (!mainDataStr.startsWith("dup")) {
			int mainDataStart = mainDataStr.indexOf("dup");
			if ((mainDataStart < 1) || (mainDataStart > 8)) {}
//			else if (!mainDataStr.substring(0, mainDataStart).matches("[0-9]+")) {}
			else {
				byte[] cMainData = new byte[mainData.length - mainDataStart];
				System.arraycopy(mainData, mainDataStart, cMainData, 0, cMainData.length);
				mainData = cMainData;
				mainDataStr = new String(mainData);
			}
		}
		if (DEBUG_TYPE1_LOADING) {
//			System.out.println("     - main data (plain):");
//			System.out.println(mainDataStr);
		}
		
		//	execute font program to get data
		String fontName = null;
//		Hashtable fontData = new Hashtable();
		Map fontData = new HashMap();
		try {
//			Hashtable psState = new Hashtable();
			Map psState = new HashMap();
			LinkedList psStack = new LinkedList();
			PsParser.executePs(headData, psState, psStack);
			if (DEBUG_TYPE1_LOADING) {
				System.out.println("     - PostScript state after head is " + psState);
				System.out.println("     - PostScript stack after head is " + psStack);
			}
			PsParser.executePs(mainData, psState, psStack);
			if (DEBUG_TYPE1_LOADING) {
				System.out.println("     - PostScript state after main is " + psState);
				System.out.println("     - PostScript stack after main is " + psStack);
			}
			for (Iterator sit = psState.keySet().iterator(); sit.hasNext();) {
				Object fontKey = sit.next();
				fontName = fontKey.toString();
				fontData = ((Map) psState.get(fontKey));
			}
		}
		catch (IOException ioe) {
			ioe.printStackTrace();
		}
		
		//	do we have font data to work with?
		if ((fontName == null) || (fontData == null))
			return;
		if (DEBUG_TYPE1_LOADING) {
			System.out.println("     - font name is " + fontName);
			System.out.println("     - font data is " + fontData);
		}
		
		//	get font matrix to apply custom scaling
		float fmScaleX = 1; // default according to spec
		float fmScaleY = 1; // default according to spec
		List fontMatrix = ((List) fontData.get("FontMatrix"));
		if (fontMatrix != null) {
			fmScaleX = (((Number) ((List) fontMatrix).get(0)).floatValue() * 1000);
			fmScaleY = (((Number) ((List) fontMatrix).get(3)).floatValue() * 1000);
			if (DEBUG_TYPE1_LOADING) System.out.println("     - scaling adjusted to " + fmScaleX + "/" + fmScaleY + " (from " + fontMatrix + ")");
		}
		
		//	get private dictionary and obfuscation byte count
		Map privateDict = ((Map) fontData.get("Private"));
		if (DEBUG_TYPE1_LOADING) System.out.println("     - private dictionaty is " + privateDict);
		int n = 4;
		if (privateDict.containsKey("lenIV"))
			n = ((Number) privateDict.get("lenIV")).intValue();
		
		//	extract char string subroutines
		List charStringSubrs = ((List) privateDict.get("Subrs"));
		if (charStringSubrs == null)
			charStringSubrs = new ArrayList();
		if (DEBUG_TYPE1_LOADING) System.out.println("     - char string subroutines are " + charStringSubrs);
		
		//	decrypt char string subroutines
		for (int s = 0; s < charStringSubrs.size(); s++) {
			PsString css = ((PsString) charStringSubrs.get(s));
			if (DEBUG_TYPE1_LOADING) System.out.println("Decrypting char string sub routine " + css);
			if (css == null) {
				if (DEBUG_TYPE1_LOADING) System.out.println(" ==> skipped");
				continue;
			}
			byte[] decryptedCssBytes = decryptType1Data(css.bytes, 4330);
			byte[] plainCssBytes = new byte[decryptedCssBytes.length - n];
			System.arraycopy(decryptedCssBytes, n, plainCssBytes, 0, plainCssBytes.length);
//			charStringSubrs.set(s, new PsString(plainCssBytes));
			css = new PsString(plainCssBytes);
			charStringSubrs.set(s, new PsString(plainCssBytes));
			if (DEBUG_TYPE1_LOADING) System.out.println(" ==> " + css + ": " + new String(plainCssBytes));
		}
//		System.out.println("     - decrypted char string subroutines are " + charStringSubrs);
		
		//	extract other subroutines
		List otherSubrs = ((List) privateDict.get("OtherSubrs"));
		if (DEBUG_TYPE1_LOADING) System.out.println("     - other subroutines are " + otherSubrs);
		
		//	extract char strings
		Map charStrings = ((Map) fontData.get("CharStrings"));
//		System.out.println("     - char strings are " + charStrings);
		
		//	read encoding, and decrypt and parse char strings
		List encoding = ((List) fontData.get("Encoding"));
		char[] chars = new char[encoding.size()];
		Arrays.fill(chars, ((char) 0));
		Integer[] charCodes = new Integer[encoding.size()];
		OpType1[][] charStringOps = new OpType1[encoding.size()][];
		int nextControlSubstituteChar = 0x1D00; // start of Phonetic Extensions letters 1D00-1D7F (tokenize together with Latin, Greek, and Cyrillic)
		for (int c = 0; c < encoding.size(); c++) {
			String charName = encoding.get(c).toString();
			if (".notdef".equals(charName)) {
				chars[c] = ((char) 0);
				charCodes[c] = new Integer(0);
				charStringOps[c] = null;
				continue;
			}
			
			int chc = pFont.getCharCode(charName);
			charCodes[c] = new Integer((chc == 0) ? c : chc);
			
			//	TODO TEST FAPESP.5.101-112.pdf (char names are 'G<charHex>')
			chars[c] = StringUtils.getCharForName(charName);
			if (chars[c] == 0) {
				Integer chi = new Integer(c);
				
				//	try UC mapping first
				if (pFont.ucMappings.containsKey(chi)) {
					Object pFontChar = pFont.ucMappings.get(chi);
					if (pFontChar instanceof Character)
						chars[c] = ((Character) pFontChar).charValue();
					else if (pFontChar instanceof String)
						chars[c] = ((String) pFontChar).charAt(0);
					if (DEBUG_TYPE1_LOADING) System.out.println("     - defaulting null resolved char " + c + " (" + charName + ") to Unicode mapped '" + chars[c] + "' (" + ((int) chars[c]) + " / " + StringUtils.getCharName(chars[c]) + ")");
				}
				
				//	try font specific encoding second
				if (chars[c] == 0) {
					Character pFontChar = PdfFont.getChar(new Integer(c), "AdobeStandardEncoding", DEBUG_TYPE1_LOADING);
					if (pFontChar != null) {
						chars[c] = pFontChar.charValue();
						if (DEBUG_TYPE1_LOADING) System.out.println("     - defaulting null resolved char " + c + " (" + charName + ") to font encoding '" + chars[c] + "' (" + ((int) chars[c]) + " / " + StringUtils.getCharName(chars[c]) + ")");
					}
				}
				if (chars[c] != 0) {}
				
				//	substitute control characters with printable ones ... might still map to paintable glyph !!!
				//	==> need to substitute 1-31 (31 chars), 127, and 128-159 (32 chars) = 64 chars to substitute while avoiding risk of conflict
				//	==> might use part of Phonetic Extensions letters 1D00-1D7F (very low probability of actual glyphs being used in text)
				else if (false 
					|| ((0x01 <= c) && (c <= 0x1F))
					|| (c == 0x7E)
					|| ((0x81 <= c) && (c <= 0x9F))
					|| (c == 0xA0) // non-breaking space
					) {
					chars[c] = ((char) nextControlSubstituteChar++);
					if (DEBUG_TYPE1_LOADING) System.out.println("     - defaulting null resolved char " + c + " (" + charName + ") to control character substiture '" + chars[c] + "' (" + ((int) chars[c]) + " / " + StringUtils.getCharName(chars[c]) + ")");
				}
				
				//	ASCII default anything else
				else {
					chars[c] = ((char) c);
					if (DEBUG_TYPE1_LOADING) System.out.println("     - defaulting null resolved char " + c + " (" + charName + ") to ASCII '" + chars[c] + "' (" + ((int) chars[c]) + " / " + StringUtils.getCharName(chars[c]) + ")");
				}
			}
			
			//	we have a valid PostFix name, use it (with whatever code the font myght call it by)
			else if (!pFont.ucMappings.containsKey(charCodes[c]) || (charCodes[c].intValue() != c))
				pFont.mapUnicode(charCodes[c], ("" + chars[c]), false);
			
			//	what did we end up with?
			if (DEBUG_TYPE1_LOADING) System.out.println("     - mapped " + c + " to '" + chars[c] + "' (" + ((int) chars[c]) + " / " + charName + ")");
			
			//	decrypt char string
			PsString cs = ((PsString) charStrings.get(charName));
			if (cs == null) {
				System.out.println("Char string " + c + " (" + chars[c] + ") not found");
				continue;
			}
			byte[] decryptedCsBytes = decryptType1Data(cs.bytes, 4330);
			byte[] plainCsBytes = new byte[decryptedCsBytes.length - n];
			System.arraycopy(decryptedCsBytes, n, plainCsBytes, 0, plainCsBytes.length);
			PsString pcs = new PsString(plainCsBytes);
			charStrings.put(charName, pcs);
//			System.out.println("     - decrypted char string " + c + " (" + chars[c] + ") to " + cs);
//			System.out.println("       " + Arrays.toString(plainCsBytes));
			
			//	parse char string
			try {
				if (DEBUG_TYPE1_LOADING) System.out.println("Reading char string " + c + " (" + chars[c] + ") " + cs);
				ArrayList csOps = new ArrayList();
//				readFontType1CharString(plainCsBytes, charName, charStringSubrs, otherSubrs, csOps, "");
				readFontType1CharString(pcs.bytes, charName, charStringSubrs, otherSubrs, csOps, "");
				charStringOps[c] = ((OpType1[]) csOps.toArray(new OpType1[csOps.size()]));
			}
			catch (Exception e) {
				System.out.println("Error reading char string " + c + " (" + chars[c] + ") " + cs + ": " + e.getMessage());
				System.out.println("  raw char string is: " + new String(cs.bytes));
				System.out.println("                      " + Arrays.toString(cs.bytes));
				System.out.println("  decrypted char string is: " + new String(pcs.bytes));
				System.out.println("                            " + Arrays.toString(pcs.bytes));
				e.printStackTrace(System.out);
				throw new RuntimeException(e);
			}
		}
		
		//	store char codes for use below
		Integer[] rawCharCodes = new Integer[encoding.size()];
		String[] charNames = new String[encoding.size()];
		Object[] glyphKeys = new Object[encoding.size()];
		for (int c = 0; c < encoding.size(); c++) {
			String charName = encoding.get(c).toString();
			if (".notdef".equals(charName))
				continue;
			rawCharCodes[c] = new Integer(c);
			charNames[c] = charName;
			glyphKeys[c] = charName;
		}
		
		//	measure characters
		pm.setInfo("   - measuring characters");
		int maxDescent = 0;
		int maxCapHeight = 0;
//		OpTrackerType1[] otrs = new OpTrackerType1[encoding.size()];
		OpTracker[] otrs = new OpTracker[encoding.size()];
		HashMap renderingSequencesToCharIndices = new HashMap();
		HashMap charIndicesToSynonyms = new HashMap();
		BufferedImage allGlyphs = new BufferedImage(1500, 1500, BufferedImage.TYPE_BYTE_BINARY);
		Graphics2D agGr = allGlyphs.createGraphics();
		agGr.setColor(Color.WHITE);
		agGr.fillRect(0, 0, allGlyphs.getWidth(), allGlyphs.getHeight());
		agGr.setColor(Color.BLACK);
		agGr.translate(100, 900);
		agGr.scale(0.3, -0.3); // 300 x 300 for 1000 x 1000 PDF font space (font size 300)
		agGr.setStroke(new BasicStroke(5));
		for (int c = 0; c < encoding.size(); c++) {
			if (chars[c] == 0)
				continue;
			if (charStringOps[c] == null)
				continue;
			
			CharImage chi = ((CharImage) pCharDecoder.plainCharCodesToImages.get(new Integer(c)));
//			CharImage chi = ((CharImage) pCharDecoder.glyphKeysToImages.get(glyphKeys[c]));
			if (chi != null) {
				pm.setInfo("     - skipping previously rendered char " + c + " with code " + charCodes[c] + " (" + encoding.get(c) + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ")");
				Float iCharWidth = ((Float) pCharDecoder.plainCharCodesToWidths.get(new Integer(c)));
//				Float iCharWidth = ((Float) pCharDecoder.glyphKeysToWidths.get(glyphKeys[c]));
				if ((iCharWidth != null) && (!pFont.ccWidths.containsKey(charCodes[c]) || (charCodes[c].intValue() != c)))
					pFont.setCharWidth(charCodes[c], iCharWidth.floatValue());
				continue;
			}
			
			//	dry run char string operations to measure extent
			pm.setInfo("     - measuring char " + c + " with code " + charCodes[c] + " (" + encoding.get(c) + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ")");
			if (DEBUG_TYPE1_LOADING) System.out.println("Measuring char " + c + " (" + encoding.get(c) + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ")");
			if (DEBUG_TYPE1_LOADING) System.out.println(" ==> font glyph key maps to " + pFont.getCharCodeByGlyphKey(encoding.get(c)));
			try {
				//	render vector glyph
//				otrs[c] = new OpTrackerType1();
				otrs[c] = new OpTracker(DEBUG_TYPE1_RENEDRING);
				runFontType1Ops(charStringOps[c], otrs[c], false, false, null, -1, -1, null, null); // we don't know if multi-path or not so far ...
				
				//	index glyph rendering sequence to catch identical characters
				String renderingSequence = otrs[c].ops.toString();
				if (DEBUG_TYPE1_LOADING) {
					System.out.println(" ==> " + otrs[c].minX + "-" + otrs[c].maxX + " x " + otrs[c].minY + "-" + otrs[c].maxY);
					System.out.println(" ==> " + renderingSequence);
				}
				if ("Ops:".equals(renderingSequence)) { /* no synonymizing spaces, tend to differ in width */ }
				else if (renderingSequencesToCharIndices.containsKey(renderingSequence))
					charIndicesToSynonyms.put(new Integer(c), renderingSequencesToCharIndices.get(renderingSequence));
//				else if (pFont.usesCharCode(charCodes[c]))
				else if (pCharDecoder.usesChar(charCodes[c], null))
//				else if (pCharDecoder.usesGlyph(glyphKeys[c]))
					renderingSequencesToCharIndices.put(renderingSequence, new Integer(c));
			}
			catch (RuntimeException re) {
				pm.setInfo("     ==> removed for invalid PostScript");
				if (DEBUG_TYPE1_LOADING) {
					System.out.println("==> Removed for invalid PostScript");
					re.printStackTrace(System.out);
				}
				otrs[c] = null;
				charStringOps[c] = null;
				continue;
			}
			
			//	update maximums (if glyph is in sane bounds, the usual being -250 to 750)
//			if ((-1000 < otrs[c].minY) && (otrs[c].maxY < 2500)) {
			if ((-1000 < (otrs[c].minY * fmScaleY)) && ((otrs[c].maxY * fmScaleY) < 2500)) {
				maxDescent = Math.min(maxDescent, otrs[c].minY);
				maxCapHeight = Math.max(maxCapHeight, otrs[c].maxY);
			}
			agGr.draw(otrs[c].path);
			
			//	store character width if set
			if (otrs[c].iWidth > 0) {
//				pFont.setCharWidth(charCodes[c], otrs[c].iWidth);
				if (!pFont.ccWidths.containsKey(charCodes[c]) || (charCodes[c].intValue() != c))
					pFont.setCharWidth(charCodes[c], (otrs[c].iWidth * fmScaleX));
				pCharDecoder.plainCharCodesToWidths.put(new Integer(c), new Float(otrs[c].iWidth * fmScaleX));
//				pCharDecoder.glyphKeysToWidths.put(glyphKeys[c], new Float(otrs[c].iWidth * fmScaleX));
				if (DEBUG_TYPE1_LOADING) System.out.println("   ==> width set to " + (otrs[c].iWidth * fmScaleX));
			}
		}
		agGr.dispose();
		if (DEBUG_TYPE1_LOADING) System.out.println("Max descent is " + maxDescent + ", max cap height is " + maxCapHeight);
		
		//	check rendered glyph path dimensions
		int white = Color.WHITE.getRGB();
		int minGlyphX = Integer.MAX_VALUE;
		int maxGlyphX = 0;
		int minGlyphY = Integer.MAX_VALUE;
		int maxGlyphY = 0;
		for (int x = 0; x < allGlyphs.getWidth(); x++) {
			for (int y = 0; y < allGlyphs.getHeight(); y++) {
				if (allGlyphs.getRGB(x, y) == white)
					continue;
				minGlyphX = Math.min(minGlyphX, x);
				maxGlyphX = Math.max(maxGlyphX, x);
				minGlyphY = Math.min(minGlyphY, y);
				maxGlyphY = Math.max(maxGlyphY, y);
			}
		}
		
		//	scale back to font space, inverting back Y coordinate for comparison with font indicated measurements
		int fMinGlyphX = (((minGlyphX - 100) * 10) / 3);
		int fMaxGlyphX = (((maxGlyphX - 100) * 10) / 3);
		int fMinGlyphY = (((maxGlyphY - 900) * 10) / -3);
		int fMaxGlyphY = (((minGlyphY - 900) * 10) / -3);
		if (DEBUG_TYPE1_LOADING) {
			System.out.println("Glyph extent is " + minGlyphX + "-" + maxGlyphX + " by " + minGlyphY + "-" + maxGlyphY);
			System.out.println(" ==> " + fMinGlyphX + "-" + fMaxGlyphX + " by " + fMinGlyphY + "-" + fMaxGlyphY);
		}
		
		//	compare measured glyph extents to font indicated ascent and descent
		int bbTop = ((pFont.bBox == null) ? 0 : pFont.bBox.top);
		float ascentRatio = (((float) fMaxGlyphY) / Math.max(Math.round(pFont.ascent * 1000), bbTop));
		int bbBottom = ((pFont.bBox == null) ? 0 : pFont.bBox.bottom);
		float descentRatio = (((float) fMinGlyphY) / Math.min(Math.round(pFont.descent * 1000), bbBottom));
		int bbHeight = ((pFont.bBox == null) ? 0 : -pFont.bBox.getHeight());
		float heightRatio = (((float) (fMaxGlyphY - fMinGlyphY)) / Math.max(Math.round((pFont.ascent - pFont.descent) * 1000), bbHeight));
		if (DEBUG_TYPE1_LOADING) {
			System.out.println("Font indicated height is " + Math.round(pFont.descent * 1000) + "-" + Math.round(pFont.ascent * 1000) + ", bounding box is " + pFont.bBox);
			System.out.println(" ==> height ratio is " + heightRatio + ", ascent ratio is " + ascentRatio + ", descent ratio is " + descentRatio);
		}
		
		//	scale down glyphs if significantly taller than indicated ascent, and adjust descent as well, as font sizes go up for compensation
		BufferedImage sAllGlyphs = null;
		if ((1.5 < ascentRatio) && (charSet == VECTORIZED_OCR_DECODING)) {
			pFont.scaleDownFactor = Math.round(ascentRatio);
			System.out.println(" ==> scaling down glyphs by factor " + pFont.scaleDownFactor);
			pFont.descent = Math.max(pFont.descent, (((float) fMinGlyphY) / (pFont.scaleDownFactor * 1000))); // descent is negative number ...
			System.out.println(" ==> descent adjusted to " + Math.round(pFont.descent * 1000));
			AffineTransform at = AffineTransform.getScaleInstance((1.0 / pFont.scaleDownFactor), (1.0 / pFont.scaleDownFactor));
			sAllGlyphs = new BufferedImage(1500, 1500, BufferedImage.TYPE_BYTE_BINARY);
			Graphics2D sagGr = sAllGlyphs.createGraphics();
			sagGr.setColor(Color.WHITE);
			sagGr.fillRect(0, 0, sAllGlyphs.getWidth(), sAllGlyphs.getHeight());
			sagGr.setColor(Color.BLACK);
			sagGr.translate(100, 900);
			sagGr.scale(0.3, -0.3); // 300 x 300 for 1000 x 1000 PDF font space (font size 300)
			sagGr.setStroke(new BasicStroke(5));
			for (int c = 0; c < otrs.length; c++) {
				if (otrs[c] == null)
					continue;
				otrs[c].path.transform(at);
				sagGr.draw(otrs[c].path);
			}
			sagGr.dispose();
		}
		
		//	make sure to accommodate ascent and descent (keeps glyphs of dash-only fonts in vertical position, and in proportion)
//		maxCapHeight = Math.max(maxCapHeight, Math.round(pFont.ascent * 1000));
//		maxDescent = Math.min(maxDescent, Math.round(pFont.descent * 1000));
		maxCapHeight = Math.max(maxCapHeight, Math.max(Math.round(pFont.ascent * 1000), bbTop));
		maxDescent = Math.min(maxDescent, Math.min(Math.min(Math.round(pFont.descent * 1000), bbBottom), Math.round((maxCapHeight * pFont.descent) / pFont.ascent)));
		
		//	set up rendering
		int maxRenderSize = 300;
		float scale = 1.0f;
		if ((maxCapHeight - maxDescent) > maxRenderSize)
			scale = (((float) maxRenderSize) / (maxCapHeight - maxDescent));
		if (DEBUG_TYPE1_LOADING) System.out.println(" ==> scaledown factor is " + scale);
		
		//	generate images and match against named char
		ImageDisplayDialog fidd = ((DEBUG_TYPE1_RENEDRING || DEBUG_TYPE1_LOADING) ? new ImageDisplayDialog("Font " + pFont.name) : null);
		pm.setInfo("   - decoding characters");
		CharImage[] charImages = new CharImage[encoding.size()];
		for (int c = 0; c < encoding.size(); c++) {
			if (chars[c] == 0)
				continue;
			if (charStringOps[c] == null)
				continue;
			String chn = encoding.get(c).toString();
			
			CharImage chi = ((CharImage) pCharDecoder.plainCharCodesToImages.get(new Integer(c)));
//			CharImage chi = ((CharImage) pCharDecoder.glyphKeysToImages.get(glyphKeys[c]));
			if (chi != null) {
				pm.setInfo("     - reusing previously rendered char " + c + " (" + chn + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ")");
				charImages[c] = chi;
				pFont.setCharImage(c, chn, chi.img, chi.path);
//				pFont.setCharImage(glyphKeys[c], chi.img, chi.path);
				continue;
			}
			
//			if (!DEBUG_TYPE1_RENDRING && !pFont.usesCharCode(new Integer(c)) && (chn != null) && !pFont.usedCharNames.contains(chn)) {
			if (!DEBUG_TYPE1_RENEDRING && !pCharDecoder.usesChar(new Integer(c), chn)) {
//			if (!DEBUG_TYPE1_RENDRING && !pCharDecoder.usesGlyph(glyphKeys[c])) {
				pm.setInfo("     - ignoring unused char " + c + " (" + chn + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ")");
				continue;
			}
			pm.setInfo("     - decoding char " + c + " (" + chn + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ")");
			if (DEBUG_TYPE1_LOADING) System.out.println("Decoding char " + c + " (" + chn + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ")");
			
//			int chw = ((otrs[c].rWidth == 0) ? dWidth : (nWidth + otrs[c].rWidth));
//			if (DEBUG_TYPE1_LOADING) System.out.println(" - char width is " + chw);
//			if (DEBUG_TYPE1_LOADING) System.out.println(" - stroke count is " + otrs[c].mCount);
			if (DEBUG_TYPE1_LOADING) System.out.println(" - stroke count is " + otrs[c].segCount);
			
			//	check if char rendering possible (and in sane bounds)
			if ((otrs[c].maxX <= otrs[c].minX) || (otrs[c].maxY <= otrs[c].minY)) {
				if (DEBUG_TYPE1_LOADING) System.out.println(" ==> unsensible char dimensions: " + otrs[c].minX + "-" + otrs[c].maxX + "x" + otrs[c].minY + "-" + otrs[c].maxY);
				continue;
			}
//			if ((2000 < (otrs[c].maxX - otrs[c].minX)) || (2500 < (otrs[c].maxY - otrs[c].minY)))
			if ((2000 < ((otrs[c].maxX - otrs[c].minX) * fmScaleX )) || (2500 < ((otrs[c].maxY - otrs[c].minY) * fmScaleY))) {
				if (DEBUG_TYPE1_LOADING) System.out.println(" ==> insane char dimensions: " + otrs[c].minX + "-" + otrs[c].maxX + "x" + otrs[c].minY + "-" + otrs[c].maxY);
				continue;
			}
			
			//	render char
			int mx = 8;
			int my = ((mx * (maxCapHeight - maxDescent)) / (otrs[c].maxX - otrs[c].minX));
//			OpGraphicsType1 ogr = new OpGraphicsType1(
			OpGraphics ogr = new OpGraphics(
					otrs[c].minX,
					maxDescent,
//					otrs[c].minY,
					(maxCapHeight - maxDescent + (my / 2)),
					scale,
					new BufferedImage(
							Math.round((scale * (otrs[c].maxX - otrs[c].minX + mx)) + (2 * glyphOutlineFillSafetyEdge)),
							Math.round((scale * (maxCapHeight - maxDescent + my)) + (2 * glyphOutlineFillSafetyEdge)),
							BufferedImage.TYPE_INT_RGB)
					);
			runFontType1Ops(charStringOps[c], ogr, otrs[c].isMultiPath, false, fidd, charCodes[c], c, chn, pFont.getUnicode(charCodes[c]));
			if (DEBUG_TYPE1_LOADING) System.out.println(" - image rendered, size is " + ogr.img.getWidth() + "x" + ogr.img.getHeight() + ", OpGraphics height is " + ogr.height);
			charImages[c] = new CharImage(ogr.img, Math.round(((float) (maxCapHeight * ogr.img.getHeight())) / (maxCapHeight - maxDescent)), otrs[c].path, (otrs[c].maxX - otrs[c].minX));
			if (DEBUG_TYPE1_LOADING) System.out.println(" - char image wrapped, baseline is " + charImages[c].baseline);
			
			//	store char image
			pFont.setCharImage(charCodes[c].intValue(), chn, ogr.img, otrs[c].path);
//			pFont.setCharImage(glyphKeys[c], ogr.img, otrs[c].path);
			pCharDecoder.plainCharCodesToImages.put(new Integer(c), charImages[c]);
//			pCharDecoder.glyphKeysToImages.put(glyphKeys[c], charImages[c]);
//			String chcUc = pFont.getUnicode(charCodes[c].intValue());
//			char chnUc = ((chn == null) ? ((char) 0) : StringUtils.getCharForName(chn));
//			if ((chcUc == null) && (chnUc != 0))
//				chcUc = ("" + chnUc);
//			if (chcUc != null)
//				pCharDecoder.plainCharCodesToUnicode.put(new Integer(c), chcUc);
			
			//	check for char code synonymies
			Integer ci = new Integer(c);
			if (charIndicesToSynonyms.containsKey(ci))
				pFont.setCharCodeSynonym(charCodes[c], charCodes[((Integer) charIndicesToSynonyms.get(ci)).intValue()]);
		}
		if ((fidd != null) && (fidd.getImageCount() != 0)) {
			CountingSet charSizes = pFont.getCharSizes();
			if (charSizes != null) {
				System.out.println("   - font used in sizes " + charSizes);
				int kCharHeight = (maxCapHeight - maxDescent);
				CountingSet charHeights = new CountingSet(new TreeMap());
				float charHeightSum = 0;
				for (Iterator csit = charSizes.iterator(); csit.hasNext();) {
					Float cs = ((Float) csit.next());
					float charHeight = ((kCharHeight * cs.floatValue()) / 1000);
					charHeights.add(new Float(charHeight), charSizes.getCount(cs));
					charHeightSum += (charHeight * charSizes.getCount(cs));
				}
				System.out.println("   - char heights (at 72 DPI) " + charHeights);
				System.out.println("   - average char height (at 72 DPI) " + (charHeightSum / charSizes.size()));
			}
			
			agGr = allGlyphs.createGraphics();
			agGr.setColor(Color.BLACK);
			agGr.drawLine(0, 900, 1000, 900);
			agGr.drawLine(0, 600, 1000, 600);
			agGr.drawLine(100, 0, 100, 1500);
			agGr.dispose();
			fidd.addImage(allGlyphs, "All Paths");
			
			if (sAllGlyphs != null) {
				Graphics2D sagGr = sAllGlyphs.createGraphics();
				sagGr.setColor(Color.BLACK);
				sagGr.drawLine(0, 900, 1000, 900);
				sagGr.drawLine(0, 600, 1000, 600);
				sagGr.drawLine(100, 0, 100, 1500);
				sagGr.dispose();
				fidd.addImage(sAllGlyphs, "All Paths (scaled)");
			}
			
			fidd.setLocationRelativeTo(null);
			fidd.setSize(600, 400);
			fidd.setVisible(true);
		}
		
		//	decode chars
		decodeChars(pFont, pCharDecoder, chars, charCodes, charNames, glyphKeys, charImages/*, rawCharCodes*/, -1, maxDescent, charSet, pm, DEBUG_TYPE1_LOADING);
	}
	
	private static byte[] decryptType1Data(byte[] encrypted, int r) {
		byte[] plain = new byte[encrypted.length];
		int c1 = 52845;
		int c2 = 22719;
		for (int b = 0; b < encrypted.length; b++) {
			int e = encrypted[b];
			if (e < 0)
				e += 256;
			int p = ((e ^ (r >> 8)) & 255);
			r = ((((e + r) * c1) + c2) & 65535);
			if (p > 127)
				p -= 256;
			plain[b] = ((byte) p);
		}
		return plain;
	}
	
//	private static int readFontType1CharString(byte[] data, String name, Vector subrs, Vector otherSubrs, ArrayList content, String indent) {
	private static int readFontType1CharString(byte[] data, String name, List subrs, List otherSubrs, ArrayList content, String indent) {
		return readFontType1CharString(data, name, false, subrs, otherSubrs, new LinkedList(), content, new LinkedList[1], indent);
	}
	
//	private static int readFontType1CharString(byte[] data, String name, boolean inSubr, Vector subrs, Vector otherSubrs, LinkedList stack, ArrayList content, String indent) {
	private static int readFontType1CharString(byte[] data, String name, boolean inSubr, List subrs, List otherSubrs, LinkedList stack, ArrayList content, LinkedList[] flexArgs, String indent) {
		int i = 0;
		LinkedList psStack = new LinkedList();
		//LinkedList flexArgs = null;
//		System.out.println("Reading char string of " + data.length + " bytes");
		while (i < data.length)  {
			
			//	read value
			int bs = convertUnsigned(data[i++]);
//			System.out.println(indent + " - first byte is " + bs + " (" + data[i-1] + ")");
			int op = Integer.MIN_VALUE;
			int iVal = Integer.MIN_VALUE;
			double dVal = Double.NaN;
			
			if ((0 <= bs) && (bs <= 11)) {
				op = bs;
			}
			else if (bs == 12) {
				if (i >= data.length)
					break;
				op = (1000 + convertUnsigned(data[i++]));
			}
			else if ((13 <= bs) && (bs <= 31)) {
				op = bs;
			}
			else if ((32 <= bs) && (bs <= 246)) {
				iVal = (bs - 139);
			}
			else if ((247 <= bs) && (bs <= 250)) {
				if (i >= data.length)
					break;
				int b1 = bs;
				int b2 = convertUnsigned(data[i++]);
				iVal = ((b1 - 247) * 256) + b2 + 108;
			}
			else if ((251 <= bs) && (bs <= 254)) {
				if (i >= data.length)
					break;
				int b1 = bs;
				int b2 = convertUnsigned(data[i++]);
				iVal = -((b1 - 251) * 256) - b2 - 108;
			}
			else if (bs == 255) {
				if ((i+4) >= data.length)
					break;
//				int ib1 = convertUnsigned(data[i++]);
//				int ib2 = convertUnsigned(data[i++]);
//				int iv = ((ib1 * 256) + ib2);
//				int fb1 = convertUnsigned(data[i++]);
//				int fb2 = convertUnsigned(data[i++]);
//				int fv = ((fb1 * 256) + fb2);
//				dVal = ((iv << 16) + fv);
//				dVal /= 65536;
				iVal = 0;
				iVal <<= 8;
				iVal |= convertUnsigned(data[i++]);
				iVal <<= 8;
				iVal |= convertUnsigned(data[i++]);
				iVal <<= 8;
				iVal |= convertUnsigned(data[i++]);
				iVal <<= 8;
				iVal |= convertUnsigned(data[i++]);
			}
			
			if (op != Integer.MIN_VALUE) {
//				System.out.println(indent + "Op is " + type1GlyphProgOpResolver.get(Integer.valueOf(op)) + " (" + op + ")");
//				System.out.println(indent + "  stack is " + stack);
				
				//	hstem, vstem (no need for collecting them here, as Type1 doesn't have flexible-length hint masks)
				if ((op == 1) || (op == 3)) {
//					System.out.println(indent + " --> got " + (stack.size() / 2) + " new hints from " + stack.size() + " args, op is " + op);
					stack.clear();
				}
				
				//	hstem3 & vstem3 (no need for collecting them here, as Type1 doesn't have flexible-length hint masks)
				else if ((op == 1002) || (op == 1001)) {
//					System.out.println(indent + " --> got " + (stack.size() / 2) + " new hints from " + stack.size() + " args, op is " + op);
					stack.clear();
				}
				
				String opStr = ((String) type1GlyphProgOpResolver.get(new Integer(op)));
//				System.out.println(indent + " --> read operator " + op + " (" + opStr + ")");
//				System.out.println(indent + "     stack is " + stack);
				
				//	callsubr (roll out sub routines right here)
				if (op == 10) {
					int subrNr = ((Number) stack.removeLast()).intValue();
//					while (stack.size() != 0)
//						System.out.print(" " + ((Number) stack.removeFirst()));
//					System.out.println(indent + " --> inlining call to subroutine " + subrNr + ":");
					if (subrNr == 0) {
						if (DEBUG_TYPE1_LOADING) {
							System.out.println(indent + "     FLEX sequence finishes recording with call to subroutine 0");
							System.out.println(indent + "     - finish stack is " + stack);
							System.out.println(indent + "     - recorded args are " + flexArgs[0]);
						}
					
						//	create 1 rlineto (5) command flattening out the whole flex (aggregating all even and all odd commands into the two taken by rlineto)
						//	(we could also use two rrcurveto (8) commands, which might be more precise in theory, but is unlikely to make any difference in glyph decoding in practice)
						int rx = 0;
						int ry = 0;
						while ((flexArgs[0] != null) && (flexArgs[0].size() != 0)) {
							rx += ((Number) flexArgs[0].removeFirst()).intValue();
							ry += ((Number) flexArgs[0].removeFirst()).intValue();
						}
						if (DEBUG_TYPE1_LOADING) System.out.println(indent + "     - resulting relative movement is " + rx + "/" + ry);
						Number[] rLineToArgs = {new Integer(rx), new Integer(ry)};
						content.add(new OpType1(5, "rlineto", rLineToArgs, true, indent));
						
						//	ignore 3 subroutine 0 args
						Number absEndY = ((Number) stack.removeLast());
						Number absEndX = ((Number) stack.removeLast());
						Number flexThreshold = ((Number) stack.removeLast());
//						Number[] flexOpArgs = {flexThreshold, absEndX, absEndY};
//						content.add(new OpType1(55, "flexabslineto", flexOpArgs, true));
						
						//	we're done with this one
						flexArgs[0] = null;
						continue;
					}
					else if (subrNr == 1) {
						if (DEBUG_TYPE1_LOADING) System.out.println(indent + "     FLEX sequence starts recording with call to subroutine 1");
						flexArgs[0] = new LinkedList();
						continue;
					}
					else if (subrNr == 2) {
						if (DEBUG_TYPE1_LOADING) System.out.println(indent + "     FLEX call to subroutine 2 ignored");
						continue;
					}
					PsString subr = ((PsString) subrs.get(subrNr));
//					System.out.println(indent + "    subroutine is '" + new String(subr.bytes) + "'");
//					System.out.println(indent + "    call stack is " + stack);
					LinkedList preCallStack = new LinkedList(stack);
					try {
						readFontType1CharString(subr.bytes, name, true, subrs, otherSubrs, stack, content, flexArgs, (indent + "  "));
//						System.out.println(indent + "    return stack is " + psStack);
					}
					catch (RuntimeException re) {
						System.out.println("Error reading char string subroutine " + subrNr + " in '" + name + "': " + re.getMessage());
						System.out.println("  subr char string is: " + new String(subr.bytes));
						System.out.println("                       " + Arrays.toString(subr.bytes));
						System.out.println("  pre-call stack was " + preCallStack);
						re.printStackTrace(System.out);
						throw new RuntimeException(re);
					}
				}
				
				//	callothersubr (run PostScript computation or whatever)
				else if (op == 1016) {
					int subrNr = ((Number) stack.removeLast()).intValue();
					int n = ((Number) stack.removeLast()).intValue();
					for (int a = 0; a < n; a++)
						psStack.addLast(stack.removeLast());
//					System.out.println(indent + " --> inlining call to other subroutine " + subrNr + ":");
					PsProcedure subr = ((PsProcedure) otherSubrs.get(subrNr));
//					System.out.println(indent + "    subroutine is '" + new String(subr.bytes) + "'");
//					System.out.println(indent + "    call stack is " + psStack);
					LinkedList preCallPsStack = new LinkedList(psStack);
					try {
//						PsParser.executePs(subr.bytes, new Hashtable(), psStack, (DEBUG_TYPE1_LOADING ? (indent + "  ") : null));
						PsParser.executePs(subr.bytes, new HashMap(), psStack, (DEBUG_TYPE1_LOADING ? (indent + "  ") : null));
//						System.out.println(indent + "    return stack is " + psStack);
					}
					catch (IOException ioe) {
						System.out.println("Error executing PostScript sub routine '" + new String(subr.bytes) + "': " + ioe.getMessage());
						ioe.printStackTrace(System.out);
					}
					catch (RuntimeException re) {
						System.out.println("Error reading other subroutine " + subrNr + " in '" + name + "': " + re.getMessage());
						System.out.println("  subr char string is: " + new String(subr.bytes));
						System.out.println("                       " + Arrays.toString(subr.bytes));
						System.out.println("  pre-call PostScript stack was " + preCallPsStack);
						re.printStackTrace(System.out);
						throw new RuntimeException(re);
					}
				}
				
				//	div (divide last two numbers on stack and put the result on the stack)
				else if (op == 1012) {
					double denom = ((Number) stack.removeLast()).doubleValue();
					double nom = ((Number) stack.removeLast()).doubleValue();
					stack.addLast(new Double(nom / denom));
//					System.out.println(indent + "     stack extended to " + stack);
				}
				
				//	pop (take result from PostScript computation and put it on operand stack)
				else if (op == 1017) {
					while ((psStack.size() > 1) && !(psStack.getLast() instanceof Number)) {
//						System.out.println(indent + "     dropping non-number pop " + psStack.getLast());
						psStack.removeLast();
					}
//					System.out.println(indent + "     popping " + psStack.getLast());
					stack.addLast(psStack.removeLast());
//					System.out.println(indent + "     stack extended to " + stack);
				}
				
				//	return (end of subroutine)
				else if (op == 11) {
//					while (stack.size() != 0)
//						System.out.print(" " + ((Number) stack.removeFirst()));
					if (inSubr)
						break;
//					System.out.println();
//					System.out.println(indent + " --> ignored");
				}
				
				//	rmoveto within flex (cannot record actual op until flex sequence complete)
				else if ((op == 21) && (flexArgs[0] != null)) {
					flexArgs[0].addAll(stack);
					if (DEBUG_TYPE1_LOADING) System.out.println(indent + "     FLEX args rmoveto extended by " + stack + " to " + flexArgs[0]);
					stack.clear();
				}
				
				//	hmoveto within flex (cannot record actual op until flex sequence complete)
				else if ((op == 22) && (flexArgs[0] != null)) {
					flexArgs[0].addAll(stack);
					flexArgs[0].add(new Integer(0));
					if (DEBUG_TYPE1_LOADING) System.out.println(indent + "     FLEX args hmoveto extended by " + stack + " and 0 to " + flexArgs[0]);
					stack.clear();
				}
				
				//	vmoveto within flex (cannot record actual op until flex sequence complete)
				else if ((op == 4) && (flexArgs[0] != null)) {
					flexArgs[0].add(new Integer(0));
					flexArgs[0].addAll(stack);
					if (DEBUG_TYPE1_LOADING) System.out.println(indent + "     FLEX args vmoveto extended by 0 and " + stack + " to " + flexArgs[0]);
					stack.clear();
				}
				
				//	store other operator (actual rendering operators)
				else {
					content.add(new OpType1(op, opStr, ((Number[]) stack.toArray(new Number[stack.size()])), true, indent));
					if (DEBUG_TYPE1_LOADING) System.out.println(indent + "     Stored for rendering " + opStr + " (" + op + "): " + stack);
					stack.clear();
//					while (stack.size() != 0)
//						System.out.print(" " + ((Number) stack.removeFirst()));
//					System.out.println();
//					System.out.println(indent + " --> stored for rendering");
					if ((op == 14) && !inSubr)
						break; // endchar, we're done here
				}
			}
			else if (iVal != Integer.MIN_VALUE) {
//				System.out.println(indent + " --> read int " + iVal);
				stack.addLast(new Integer(iVal));
			}
			else if (!Double.isNaN(dVal)) {
//				System.out.println(indent + " --> read double " + dVal);
				stack.addLast(new Double(dVal));
			}
			else {
				System.out.println(indent + "Invalid op is " + op);
				System.out.println(indent + "  stack is " + stack);
			}
		}
		
		return i;
	}
	
	private static HashMap type1GlyphProgOpResolver = new HashMap();
	static {
		type1GlyphProgOpResolver.put(new Integer(1), "hstem");
	
		type1GlyphProgOpResolver.put(new Integer(3), "vstem");
		type1GlyphProgOpResolver.put(new Integer(4), "vmoveto");
		type1GlyphProgOpResolver.put(new Integer(5), "rlineto");
		type1GlyphProgOpResolver.put(new Integer(6), "hlineto");
		type1GlyphProgOpResolver.put(new Integer(7), "vlineto");
		type1GlyphProgOpResolver.put(new Integer(8), "rrcurveto");
		type1GlyphProgOpResolver.put(new Integer(9), "closepath");
		type1GlyphProgOpResolver.put(new Integer(10), "callsubr");
		type1GlyphProgOpResolver.put(new Integer(11), "return");

		type1GlyphProgOpResolver.put(new Integer(13), "hsbw");
		type1GlyphProgOpResolver.put(new Integer(14), "endchar");
	
		type1GlyphProgOpResolver.put(new Integer(21), "rmoveto");
		type1GlyphProgOpResolver.put(new Integer(22), "hmoveto");
	
		type1GlyphProgOpResolver.put(new Integer(30), "vhcurveto");
		type1GlyphProgOpResolver.put(new Integer(31), "hvcurveto");
	
		type1GlyphProgOpResolver.put(new Integer(1000), "dotsection");
		type1GlyphProgOpResolver.put(new Integer(1001), "vstem3");
		type1GlyphProgOpResolver.put(new Integer(1002), "hstem3");
		type1GlyphProgOpResolver.put(new Integer(1006), "seac");
		type1GlyphProgOpResolver.put(new Integer(1007), "sbw");
		type1GlyphProgOpResolver.put(new Integer(1012), "div");
		type1GlyphProgOpResolver.put(new Integer(1016), "callothersubr");
		type1GlyphProgOpResolver.put(new Integer(1017), "pop");
		type1GlyphProgOpResolver.put(new Integer(1033), "setcurrentpoint");
	}
	
	private static final boolean DEBUG_TYPE1C_LOADING = false;
	
	/** Read a font in Type1C format, in SID mode or CID mode.
	 * @param data the raw bytes
	 * @param fd the font descriptor hash table
	 * @param pFont the font
	 * @param resolvedCodesOrCidMap mapping of resolved char codes in SID mode, mapping of CIDs in CID mode
	 * @param unresolvedCodes mapping of unresolved char codes (SID mode only, null in CID mode)
	 * @param pm a progress monitor to observe font decoding
	 */
	static void readFontType1C(byte[] data, Map fd, PdfFont pFont, CharDecoder pCharDecoder, HashMap resolvedCodesOrCidMap, HashMap unresolvedCodes, FontDecoderCharset charSet, ProgressMonitor pm) {
		pm.setInfo("   - reading meta data");
		int i = 0;
		
		//	read header
//		if (DEBUG_TYPE1C_LOADING) System.out.println(new String(data));
		int major = convertUnsigned(data[i++]);
		int minor = convertUnsigned(data[i++]);
		if (DEBUG_TYPE1C_LOADING) System.out.println("Version is " + major + "." + minor);
		int hdrSize = convertUnsigned(data[i++]);
		if (DEBUG_TYPE1C_LOADING) System.out.println("Header size is " + hdrSize);
		int offSize = convertUnsigned(data[i++]);
		if (DEBUG_TYPE1C_LOADING) System.out.println("Offset size is " + offSize);
		
		//	read base data
		i = readFontType1cIndex(data, i, "name", null, null, null, null, null);
		if (DEBUG_TYPE1C_LOADING) System.out.println("GOT TO " + i + " of " + data.length + " bytes");
		HashMap topDict = new HashMap() {
			public Object put(Object key, Object value) {
				if (DEBUG_TYPE1C_LOADING) {
					System.out.print("TopDict: " + key + " set to " + value);
					if (value instanceof Number[]) {
						Number[] nums = ((Number[]) value);
						for (int n = 0; n < nums.length; n++)
							System.out.print(" " + nums[n]);
					}
					System.out.println();
				}
				return super.put(key, value);
			}
		};
		i = readFontType1cIndex(data, i, "TopDICT", null, null, topDict, type1cTopDictOpResolver, null);
		if (DEBUG_TYPE1C_LOADING) System.out.println("GOT TO " + i + " of " + data.length + " bytes");
		
		//	read string index
		ArrayList sidIndex = new ArrayList() {
			public boolean add(Object o) {
				if (DEBUG_TYPE1C_LOADING) System.out.println("StringIndex: " + this.size() + " (SID " + (this.size() + sidResolver.size()) + ") set to " + o);
				return super.add(o);
			}
		};
		i = readFontType1cIndex(data, i, "String", null, null, null, null, sidIndex);
		if (DEBUG_TYPE1C_LOADING) System.out.println("GOT TO " + i + " of " + data.length + " bytes");
		
		//	read global subroutines (if any)
		ArrayList gSubrIndex = new ArrayList() {
			public boolean add(Object e) {
				if (DEBUG_TYPE1C_LOADING) System.out.println("GlobalSubrIndex: " + this.size() + " set to " + ((e instanceof byte[]) ? Arrays.toString((byte[]) e) : e));
				return super.add(e);
			}
			public Object get(int index) {
				int bias;
				if (this.size() < 1240)
					bias = 107;
				else if (this.size() < 33900)
					bias = 1131;
				else bias = 32768;
				if ((0 <= (index + bias)) && ((index + bias) < this.size()))
					return super.get(index + bias);
				else return null;
			}
		};
		i = readFontType1cIndex(data, i, "GlobalSubr", null, null, new HashMap(), type1cGlyphProgOpResolver, gSubrIndex);
		if (DEBUG_TYPE1C_LOADING) System.out.println("GOT TO " + i + " of " + data.length + " bytes");
		
		//	read encoding
		HashMap eDict = new HashMap() {
			public Object put(Object key, Object value) {
				if (DEBUG_TYPE1C_LOADING) {
					System.out.print("EncodingDict: " + key + " set to " + value);
					if (value instanceof Number[]) {
						Number[] nums = ((Number[]) value);
						for (int n = 0; n < nums.length; n++)
							System.out.print(" " + nums[n]);
					}
					System.out.println();
				}
				return super.put(key, value);
			}
		};
		int eEnd = 0;
		if (topDict.containsKey("Encoding")) {
			Number[] eos = ((Number[]) topDict.get("Encoding"));
			if (eos.length != 0) {
				int eso = eos[0].intValue();
				eEnd = readFontType1cEncoding(data, eso, eDict);
			}
		}
		
		//	read FD array in CID mode
		ArrayList fdArray = new ArrayList() {
			public boolean add(Object e) {
				if (e instanceof byte[]) {
					byte[] fDictData = ((byte[]) e);
					HashMap fDict = new HashMap() {
						public Object put(Object key, Object value) {
							if (DEBUG_TYPE1C_LOADING) {
								System.out.print("FDict: " + key + " set to " + value);
								if (value instanceof Number[]) {
									Number[] nums = ((Number[]) value);
									for (int n = 0; n < nums.length; n++)
										System.out.print(" " + nums[n]);
								}
								System.out.println();
							}
							return super.put(key, value);
						}
					};
					readFontType1cDict(fDictData, 0, fDictData.length, ("FDict[" + this.size() + "]"), null, null, fDict, type1cTopDictOpResolver, null);
					e = fDict;
				}
				if (DEBUG_TYPE1C_LOADING) System.out.println("FDArray: " + this.size() + " set to " + ((e instanceof byte[]) ? Arrays.toString((byte[]) e) : e));
				return super.add(e);
			}
		};
		int fdaEnd = 0;
		if ((unresolvedCodes == null) && topDict.containsKey("FDArray")) {
			Number[] fdaos = ((Number[]) topDict.get("FDArray"));
			if (fdaos.length != 0) {
				int fdao = fdaos[0].intValue();
				fdaEnd = readFontType1cIndex(data, fdao, "FDArray", null, null, new HashMap(), type1cGlyphProgOpResolver, fdArray);
			}
		}
		
		//	read private dictionary and subroutines in SID mode
		HashMap pDict = new HashMap() {
			public Object put(Object key, Object value) {
				if (DEBUG_TYPE1C_LOADING) {
					System.out.print("PrivateDict: " + key + " set to " + value);
					if (value instanceof Number[]) {
						Number[] nums = ((Number[]) value);
						for (int n = 0; n < nums.length; n++)
							System.out.print(" " + nums[n]);
					}
					System.out.println();
				}
				return super.put(key, value);
			}
		};
		ArrayList subrIndex = new ArrayList() {
			public boolean add(Object e) {
				if (DEBUG_TYPE1C_LOADING) System.out.println("SubrIndex: " + this.size() + " set to " + ((e instanceof byte[]) ? Arrays.toString((byte[]) e) : e));
				return super.add(e);
			}
			public Object get(int index) {
				int bias;
				if (this.size() < 1240)
					bias = 107;
				else if (this.size() < 33900)
					bias = 1131;
				else bias = 32768;
				if ((0 <= (index + bias)) && ((index + bias) < this.size()))
					return super.get(index + bias);
				else return null;
			}
		};
		int pEnd = 0;
		if (topDict.containsKey("Private")) {
			Number[] pos = ((Number[]) topDict.get("Private"));
			if (pos.length == 2) try {
				int ps = pos[0].intValue();
				int po = pos[1].intValue();
				ArrayList pDictContent = new ArrayList() {
					public boolean add(Object e) {
						if (DEBUG_TYPE1C_LOADING) System.out.println("PrivateIndex: " + this.size() + " set to " + ((e instanceof byte[]) ? Arrays.toString((byte[]) e) : e));
						return super.add(e);
					}
				};
				pEnd = readFontType1cDict(data, po, (po + ps), "Private", null, null, pDict, type1cPrivateOpResolver, pDictContent);
				
				//	read char string subroutines
				Number[] sros = ((Number[]) pDict.get("Subrs"));
				if ((sros != null) && (sros.length != 0)) {
					int sro = sros[sros.length-1].intValue();
//					subrIndex = new ArrayList() {
//						public Object get(int index) {
//							int bias;
//							if (this.size() < 1240)
//								bias = 107;
//							else if (this.size() < 33900)
//								bias = 1131;
//							else bias = 32768;
//							if ((0 <= (index + bias)) && ((index + bias) < this.size()))
//								return super.get(index + bias);
//							else return null;
//						}
//					};
					pEnd = readFontType1cIndex(data, (po + sro), "Subrs[0]", null, null, new HashMap(), type1cGlyphProgOpResolver, subrIndex);
					if (DEBUG_TYPE1C_LOADING) System.out.println("Got " + subrIndex.size() + " char string subroutines");
				}
			}
			catch (RuntimeException re) {
				System.out.println("Error reading private dictionary: " + re.getMessage());
				re.printStackTrace(System.out);
			}
		}
		
		//	read private dictionaries and subroutines in CID mode
		//	TODO make this work for multiple entries ...
		//	TODO ... especially in rendering
		else if ((unresolvedCodes == null) && (fdArray.size() == 1) && (fdArray.get(0) instanceof HashMap)) {
			HashMap fDict = ((HashMap) fdArray.get(0));
			if (fDict.containsKey("Private")) {
				Number[] pos = ((Number[]) fDict.get("Private"));
				if (pos.length == 2) try {
					int ps = pos[0].intValue();
					int po = pos[1].intValue();
					ArrayList pDictContent = new ArrayList() {
						public boolean add(Object e) {
							if (DEBUG_TYPE1C_LOADING) System.out.println("PrivateIndex: " + this.size() + " set to " + ((e instanceof byte[]) ? Arrays.toString((byte[]) e) : e));
							return super.add(e);
						}
					};
					pEnd = readFontType1cDict(data, po, (po + ps), "Private[0]", null, null, pDict, type1cPrivateOpResolver, pDictContent);
					
					//	read char string subroutines
					Number[] sros = ((Number[]) pDict.get("Subrs"));
					if ((sros != null) && (sros.length != 0)) {
						int sro = sros[sros.length-1].intValue();
//						subrIndex = new ArrayList() {
//							public Object get(int index) {
//								int bias;
//								if (this.size() < 1240)
//									bias = 107;
//								else if (this.size() < 33900)
//									bias = 1131;
//								else bias = 32768;
//								if ((0 <= (index + bias)) && ((index + bias) < this.size()))
//									return super.get(index + bias);
//								else return null;
//							}
//						};
						pEnd = readFontType1cIndex(data, (po + sro), "Subrs[0]", null, null, new HashMap(), type1cGlyphProgOpResolver, subrIndex);
						if (DEBUG_TYPE1C_LOADING) System.out.println("Got " + subrIndex.size() + " char string subroutines");
					}
				}
				catch (RuntimeException re) {
					System.out.println("Error reading private dictionary: " + re.getMessage());
					re.printStackTrace(System.out);
				}
			}
		}
		
		//	read FD selector in CID mode
		ArrayList fdSelect = new ArrayList() {
			public void add(int i, Object e) {
				if (DEBUG_TYPE1C_LOADING) System.out.println("FDSelect: " + this.size() + " set to " + ((e instanceof byte[]) ? Arrays.toString((byte[]) e) : e));
				super.add(i, e);
			}
		};
		if ((unresolvedCodes == null) && topDict.containsKey("FDSelect")) {
			Number[] fdsos = ((Number[]) topDict.get("FDSelect"));
			if (fdsos.length != 0) {
				int fdso = fdsos[0].intValue();
				int format = data[fdso++];
				if (DEBUG_TYPE1C_LOADING) System.out.println("FDSelect: format is " + format);
				if (format == 0) {
					Number[] ccs = ((Number[]) topDict.get("CIDCount"));
					int charCount = (((ccs != null) && (ccs.length != 0)) ? ccs[0].intValue() : 0);
					for (int c = 0; c < Math.min(charCount, fdArray.size()); c++)
						fdSelect.add(c, fdArray.get(convertUnsigned(data[fdso++])));
				}
				if (format == 3) {
					int ranges = ((convertUnsigned(data[fdso++]) << 8) + convertUnsigned(data[fdso++]));
					if (DEBUG_TYPE1C_LOADING) System.out.println("FDSelect: ranges is " + ranges);
					for (int r = 0; r < ranges; r++) {
						int rFirst = ((convertUnsigned(data[fdso++]) << 8) + convertUnsigned(data[fdso++]));
						int rFdIndex = convertUnsigned(data[fdso++]);
						int rNext = ((convertUnsigned(data[fdso]) << 8) + convertUnsigned(data[fdso + 1]));
						if (DEBUG_TYPE1C_LOADING) System.out.println("FDSelect range " + r + " is [" + rFirst + "," + rNext + ") = " + rFdIndex);
						for (int c = rFirst; c < rNext; c++)
							fdSelect.add(c, fdArray.get(rFdIndex));
					}
				}
			}
		}
		
		//	read char rendering data
		HashMap csDict = new HashMap() {
			public Object put(Object key, Object value) {
				if (DEBUG_TYPE1C_LOADING) {
					System.out.print("CharStringIndexDict: " + key + " set to " + value);
					if (value instanceof Number[]) {
						Number[] nums = ((Number[]) value);
						for (int n = 0; n < nums.length; n++)
							System.out.print(" " + nums[n]);
					}
					System.out.println();
				}
				return super.put(key, value);
			}
		};
		ArrayList csIndexContent = new ArrayList() {
			public boolean add(Object e) {
				if (DEBUG_TYPE1C_LOADING) {
					System.out.println("CharStringIndexContent: " + this.size() + " set to " + e);
//					if (value instanceof Number[]) {
//						Number[] nums = ((Number[]) value);
//						for (int n = 0; n < nums.length; n++)
//							System.out.print(" " + nums[n]);
//					}
//					System.out.println();
				}
				return super.add(e);
			}
		};
		int csEnd = 0;
		if (topDict.containsKey("CharStrings")) {
			Number[] csos = ((Number[]) topDict.get("CharStrings"));
			if (csos.length != 0) {
				int cso = csos[0].intValue();
				csEnd = readFontType1cIndex(data, cso, "CharStrings", subrIndex, gSubrIndex, csDict, type1cGlyphProgOpResolver, csIndexContent);
			}
		}
		ArrayList csContent = new ArrayList();
		int cEnd = 0;
//		if (csDict.containsKey("Count")) {
//			Number[] csos = {new Integer(0)};
//			if (topDict.containsKey("Charset"))
//				csos = ((Number[]) topDict.get("Charset"));
//			Number[] cnts = ((Number[]) csDict.get("Count"));
//			if ((csos.length * cnts.length) != 0) {
//				int cso = csos[0].intValue();
//				int cnt = cnts[0].intValue();
//				cEnd = readFontType1cCharset(data, cso, cnt, csContent);
//			}
//		}
		if (topDict.containsKey("Charset")) {
			Number[] csos = ((Number[]) topDict.get("Charset"));
			int cso = csos[0].intValue();
			if (cso == 1) {
				csContent.addAll(type1cExpertCharSet.keySet());
				if (DEBUG_TYPE1C_LOADING) System.out.println("Using built-in Expert char set");
			}
			else if (cso == 2) {
				csContent.addAll(type1cExpertSubsetCharSet.keySet());
				if (DEBUG_TYPE1C_LOADING) System.out.println("Using built-in ExpertSubset char set");
			}
			else if (csDict.containsKey("Count")) {
				Number[] cnts = ((Number[]) csDict.get("Count"));
				int cnt = cnts[0].intValue();
				cEnd = readFontType1cCharset(data, cso, cnt, csContent);
			}
		}
		else {
			csContent.addAll(type1cIsoAdobeCharSet.keySet());
			if (DEBUG_TYPE1C_LOADING) System.out.println("Defaulted to ISOAdobe char set");
		}
		
		i = Math.max(Math.max(Math.max(i, pEnd), Math.max(csEnd, cEnd)), fdaEnd);
		if (DEBUG_TYPE1C_LOADING) {
			System.out.println("GOT TO " + i + " of " + data.length + " bytes");
			System.out.println("Got " + csContent.size() + " char IDs, " + csIndexContent.size() + " char progs");
		}
		if (csContent.isEmpty() || (((Integer) csContent.get(0)).intValue() != 0))
			csContent.add(0, new Integer(0));
		
		//	get default width
		int dWidth = -1;
		if (pDict.containsKey("defaultWidthX")) {
			Number[] dws = ((Number[]) pDict.get("defaultWidthX"));
			if (dws.length != 0)
				dWidth = dws[0].intValue();
		}
		int nWidth = -1;
		if (pDict.containsKey("nominalWidthX")) {
			Number[] nws = ((Number[]) pDict.get("nominalWidthX"));
			if (nws.length != 0)
				nWidth = nws[0].intValue();
		}
		if (nWidth == -1)
			nWidth = dWidth;
		else if (dWidth == -1)
			dWidth = nWidth;
		if (DEBUG_TYPE1C_LOADING) System.out.println("Default char width is " + dWidth + ", nominal width is " + nWidth);
		
		//	TODO get font box
		
		//	get font flags
		//	TODO maybe use these guys for font style assessment (==> might also decrease effort in verification matches)
		int flags = 0; // using -1 for 'undefined' would set all the flags, so we need to use 0 here
		boolean isMonospaced = false;
		boolean isSerif = false;
		boolean isSymbolic = false;
		boolean isScript = false;
		boolean useIsoAdobeCharSet = false;
		boolean isItalics = false;
		boolean isAllCaps = false;
		boolean isSmallCaps = false;
		boolean isForcedBold = false;
		if (pFont.descriptor.containsKey("Flags")) {
			flags = ((Number) pFont.descriptor.get("Flags")).intValue();
			if (DEBUG_TYPE1C_LOADING) System.out.println("Flags are " + flags + " (" + Integer.toString(flags, 16) + "/" + Integer.toString(flags, 2) + ")");
			isMonospaced = ((flags & PdfFont.FLAG_FixedPitch) != 0);
			isSerif = ((flags & PdfFont.FLAG_Serif) != 0);
			isSymbolic = ((flags & PdfFont.FLAG_Symbolic) != 0);
			isScript = ((flags & PdfFont.FLAG_Script) != 0);
			useIsoAdobeCharSet = ((flags & PdfFont.FLAG_Nonsymbolic) != 0);
			isItalics = ((flags & PdfFont.FLAG_Italic) != 0);
			isAllCaps = ((flags & PdfFont.FLAG_AllCaps) != 0);
			isSmallCaps = ((flags & PdfFont.FLAG_SmallCaps) != 0);
			isForcedBold = ((flags & PdfFont.FLAG_ForceBold) != 0);
		}
		
		//	collect character codes
		char[] chars = new char[Math.min(csContent.size(), csIndexContent.size())];
		Arrays.fill(chars, ((char) 0));
		Integer[] charCodes = new Integer[Math.min(csContent.size(), csIndexContent.size())];
		Integer[] rawCharCodes = new Integer[Math.min(csContent.size(), csIndexContent.size())];
		String[] charNames = new String[Math.min(csContent.size(), csIndexContent.size())];
		Object[] glyphKeys = new Object[Math.min(csContent.size(), csIndexContent.size())];
		if (DEBUG_TYPE1C_LOADING) {
			System.out.println("Loading character codes and names in font " + pFont.name + " (" + pFont + "):");
			System.out.println(" - cs content is " + csContent);
//			System.out.println(" - cs index content is " + csIndexContent); // no use, contains rendering OP arrays, and those work
			System.out.println(" - resolved codes is " + resolvedCodesOrCidMap);
			System.out.println(" - unresolved codes is " + unresolvedCodes);
			System.out.println(" - CharSet is " + pFont.descriptor.get("CharSet"));
			System.out.println(" - TopDict Charset is " + topDict.get("Charset"));
			System.out.println(" - Encoding is " + pFont.descriptor.get("Encoding"));
			System.out.println(" - EncodingScheme is " + pFont.descriptor.get("EncodingScheme"));
			System.out.println(" - CharacterSet is " + pFont.descriptor.get("CharacterSet"));
			System.out.println(" - Font-Encoding is " + pFont.data.get("Encoding"));
			System.out.println(" - BaseFont is " + pFont.data.get("BaseFont"));
		}
		for (int c = 0; c < Math.min(csContent.size(), csIndexContent.size()); c++) {
			if (DEBUG_TYPE1C_LOADING) System.out.println(" - resolving char " + c);
			
			//	CID font mode
			if (unresolvedCodes == null) {
//				Integer chc = ((Integer) resolvedCodesOrCidMap.get(new Integer(c)));
				Integer chc = ((resolvedCodesOrCidMap == null) ? ((Integer) csContent.get(c)) : ((Integer) resolvedCodesOrCidMap.get(new Integer(c))));
				if (chc == null)
					continue;
				if (DEBUG_TYPE1C_LOADING) System.out.println("   - found CID char code " + chc + " in " + ((resolvedCodesOrCidMap == null) ? "index" : "map"));
//				chars[c] = ((char) chc.intValue());
				String ucStr = null; // TODO put UC mapping in CID Map instead !!!
				if (pFont.ucMappings.containsKey(chc)) /* use unicode mapping if available */ {
					ucStr = pFont.getUnicode(chc.intValue());
					if ((ucStr != null) && (ucStr.length() != 1))
						ucStr = null;
				}
				chars[c] = ((ucStr == null) ? ((char) chc.intValue()) : ucStr.charAt(0));
				if (DEBUG_TYPE1C_LOADING) System.out.println("   - found char '" + chars[c] + "' (" + Integer.toString(((int) chars[c]), 16) + ")");
				charNames[c] = StringUtils.getCharName(chars[c]);
				if (DEBUG_TYPE1C_LOADING) System.out.println("   - char name is " + charNames[c] + " (from PostScript mapping)");
//				charCodes[c] = new Integer((int) chars[c]);
				charCodes[c] = chc;
				rawCharCodes[c] = charCodes[c];
				glyphKeys[c] = chc;
			}
			
			//	Type 1c font mode
			else {
				Integer sid = ((Integer) csContent.get(c));
				if (sid.intValue() == 0)
					continue;
				if ((0 < DEBUG_TYPE1C_TARGET_SID) && (sid.intValue() != DEBUG_TYPE1C_TARGET_SID))
					continue;
				if (DEBUG_TYPE1C_LOADING) System.out.println(" - found SID " + sid + " at " + c);
				
				String chnSource = null;
				if (charNames[c] == null) {
					charNames[c] = ((String) sidResolver.get(sid));
					chnSource = "default SID";
				}
				if ((charNames[c] == null) && (sid.intValue() >= sidResolver.size()) && ((sid.intValue() - sidResolver.size()) < sidIndex.size())) {
					charNames[c] = ((String) sidIndex.get(sid.intValue() - sidResolver.size()));
					chnSource = "custom SID";
				}
				if ((charNames[c] == null) && pFont.diffNameMappings.containsKey(new Integer(c))) {
					charNames[c] = ((String) pFont.diffNameMappings.get(new Integer(c)));
					chnSource = "diff mapping";
				}
				if (DEBUG_TYPE1C_LOADING) System.out.println("   char name is " + charNames[c] + " (from " + chnSource + ")");
				
				chars[c] = StringUtils.getCharForName(charNames[c]);
				if ((chars[c] == 0) && (charNames[c] != null)) {
					if (charNames[c].indexOf('.') != -1)
						chars[c] = StringUtils.getCharForName(charNames[c].substring(0, charNames[c].indexOf('.')));
					else if (charNames[c].indexOf('_') != -1)
						chars[c] = StringUtils.getCharForName(charNames[c].replaceAll("_", ""));
				}
				if (DEBUG_TYPE1C_LOADING) {
					System.out.println("   char is " + ((int) chars[c]));
					System.out.println("   embedded char encoding is " + eDict.get(new Integer(c)));
				}
				
				//	use external encoding over embedded encoding, overwrites acoording to spec
				charCodes[c] = null;
				String charCodeSource = null;
				if (charCodes[c] == null) {
					charCodes[c] = ((Integer) unresolvedCodes.get(charNames[c]));
					if (charCodes[c] != null)
						charCodeSource = "unresolved codes (non-PostFix named Encoding/Differences)";
				}
				if (charCodes[c] == null) {
					charCodes[c] = ((Integer) resolvedCodesOrCidMap.get(charNames[c]));
					if (charCodes[c] != null)
						charCodeSource = "resolved codes (PostFix named Encoding/Differences)";
				}
				if (charCodes[c] == null) {
					charCodes[c] = ((Integer) eDict.get(new Integer(c)));
					if (charCodes[c] != null)
						charCodeSource = "embedded encoding";
				}
				if (charCodes[c] == null) {
					charCodes[c] = new Integer((int) StringUtils.getCharForName(charNames[c]));
					if (charCodes[c].intValue() < 1)
						continue;
					charCodeSource = "PostFix char name";
				}
				rawCharCodes[c] = sid;
				glyphKeys[c] = charNames[c];
				if (DEBUG_TYPE1C_LOADING) {
					System.out.println("   char code is " + charCodes[c] + " from " + charCodeSource);
					System.out.println(" ==> font glyph key maps to " + pFont.getCharCodeByGlyphKey(charNames[c]));
				}
			}
		}
		
		//	measure characters
		pm.setInfo("   - measuring characters");
		int maxDescent = 0;
		int maxCapHeight = 0;
//		OpTrackerType1[] otrs = new OpTrackerType1[Math.min(csIndexContent.size(), csContent.size())];
		OpTracker[] otrs = new OpTracker[Math.min(csIndexContent.size(), csContent.size())];
		HashMap renderingSequencesToCharIndices = new HashMap();
		HashMap charIndicesToSynonyms = new HashMap();
		BufferedImage allGlyphs = new BufferedImage(1500, 1500, BufferedImage.TYPE_BYTE_BINARY);
		Graphics2D agGr = allGlyphs.createGraphics();
		agGr.setColor(Color.WHITE);
		agGr.fillRect(0, 0, allGlyphs.getWidth(), allGlyphs.getHeight());
		agGr.setColor(Color.BLACK);
		agGr.translate(100, 900);
		agGr.scale(0.3, -0.3); // 300 x 300 for 1000 x 1000 PDF font space (font size 300)
		agGr.setStroke(new BasicStroke(5));
		for (int c = 0; c < Math.min(csIndexContent.size(), csContent.size()); c++) {
			if (DEBUG_TYPE1C_LOADING) System.out.println(pFont.name + ", char " + c);
			OpType1[] cs = ((OpType1[]) csIndexContent.get(c));
//			otrs[c] = new OpTrackerType1();
			otrs[c] = new OpTracker(DEBUG_TYPE1_RENEDRING && (DEBUG_TYPE1C_TARGET_SID == ((Integer) csContent.get(c)).intValue()));
			
			//	CID font mode
			if (unresolvedCodes == null) {
//				Integer chi = ((Integer) resolvedCodesOrCidMap.get(new Integer(c)));
				Integer chc = ((resolvedCodesOrCidMap == null) ? ((Integer) csContent.get(c)) : ((Integer) resolvedCodesOrCidMap.get(new Integer(c))));
				if (chc == null)
					continue;
//				char ch = ((char) chi.intValue());
				char ch = chars[c];
//				String chn = StringUtils.getCharName(ch);
				String chn = charNames[c];
				CharImage chi = ((CharImage) pCharDecoder.plainCharCodesToImages.get(charCodes[c]));
//				CharImage chi = ((CharImage) pCharDecoder.glyphKeysToImages.get(glyphKeys[c]));
				if (chi != null) {
					pm.setInfo("     - skipping previously rendered char " + c + " (" + chn + "/'" + ch + "'/'" + StringUtils.getNormalForm(ch) + "'/" + ((int) ch) + ")");
					continue;
				}
				pm.setInfo("     - measuring char " + c + " (" + chn + "/'" + ch + "'/'" + StringUtils.getNormalForm(ch) + "'/" + ((int) ch) + ")");
				if (DEBUG_TYPE1C_LOADING) System.out.println("Measuring char " + c + " (" + chn + "/'" + ch + "'/'" + StringUtils.getNormalForm(ch) + "'/" + ((int) ch) + ")");
			}
			
			//	Type 1 font mode
			else {
				Integer sid = ((Integer) csContent.get(c));
				if ((0 < DEBUG_TYPE1C_TARGET_SID) && (sid.intValue() != DEBUG_TYPE1C_TARGET_SID))
					continue;
//				String chn = ((String) sidResolver.get(sid));
//				if ((chn == null) && (sid.intValue() >= sidResolver.size()) && ((sid.intValue() - sidResolver.size()) < sidIndex.size()))
//					chn = ((String) sidIndex.get(sid.intValue() - sidResolver.size()));
//				if ((chn == null) && pFont.diffNameMappings.containsKey(new Integer(c)))
//					chn = ((String) pFont.diffNameMappings.get(new Integer(c)));
				String chn = charNames[c];
//				char ch = StringUtils.getCharForName(chn);
				char ch = chars[c];
				CharImage chi = ((CharImage) pCharDecoder.plainCharCodesToImages.get(sid));
//				CharImage chi = ((CharImage) pCharDecoder.glyphKeysToImages.get(glyphKeys[c]));
				if (chi != null) {
					pm.setInfo("     - skipping previously rendered char " + c + " with SID " + sid + " (" + chn + "/'" + ch + "'/'" + StringUtils.getNormalForm(ch) + "'/" + ((int) ch) + ")");
					continue;
				}
				pm.setInfo("     - measuring char " + c + " with SID " + sid + " (" + chn + "/'" + ch + "'/'" + StringUtils.getNormalForm(ch) + "'/" + ((int) ch) + ")");
				if (DEBUG_TYPE1C_LOADING) System.out.println("Measuring char " + c + ", SID is " + sid + " (" + chn + "/'" + ch + "'/'" + StringUtils.getNormalForm(ch) + "'/" + ((int) ch) + ")");
			}
			
			//	render and measure glyph
			runFontType1Ops(cs, otrs[c], false, false, null, -1, -1, null, null); // we don't know if multi-path or not so far ...
			if (DEBUG_TYPE1C_LOADING) System.out.println("Descent is " + otrs[c].minY + ", height is " + otrs[c].maxY);
			
			//	update maximums (if glyph is in sane bounds, the usual being -250 to 750)
			//	TODO use font bounding box here ('FontBBox' in TopDict, as [left,bottom,right, top])
			if ((-1000 < otrs[c].minY) && (otrs[c].maxY < 2500)) {
				maxDescent = Math.min(maxDescent, otrs[c].minY);
				maxCapHeight = Math.max(maxCapHeight, otrs[c].maxY);
			}
			agGr.draw(otrs[c].path);
			
			//	index glyph rendering sequence to catch identical characters
			String renderingSequence = otrs[c].ops.toString();
			if (DEBUG_TYPE1C_LOADING) System.out.println(" ==> " + renderingSequence);
			if ("Ops:".equals(renderingSequence)) { /* no synonymizing spaces, tend to differ in width */ }
			else if (renderingSequencesToCharIndices.containsKey(renderingSequence))
				charIndicesToSynonyms.put(new Integer(c), renderingSequencesToCharIndices.get(renderingSequence));
//			else if ((charCodes[c] != null) && pFont.usesCharCode(charCodes[c]))
			else if ((charCodes[c] != null) && pCharDecoder.usesChar(charCodes[c], null))
//			else if (pCharDecoder.usesGlyph(glyphKeys[c]))
				renderingSequencesToCharIndices.put(renderingSequence, new Integer(c));
		}
		agGr.dispose();
		if (DEBUG_TYPE1C_LOADING) System.out.println("Max descent is " + maxDescent + ", max cap height is " + maxCapHeight);
		
		//	check rendered glyph path dimensions
		int white = Color.WHITE.getRGB();
		int minGlyphX = Integer.MAX_VALUE;
		int maxGlyphX = 0;
		int minGlyphY = Integer.MAX_VALUE;
		int maxGlyphY = 0;
		for (int x = 0; x < allGlyphs.getWidth(); x++) {
			for (int y = 0; y < allGlyphs.getHeight(); y++) {
				if (allGlyphs.getRGB(x, y) == white)
					continue;
				minGlyphX = Math.min(minGlyphX, x);
				maxGlyphX = Math.max(maxGlyphX, x);
				minGlyphY = Math.min(minGlyphY, y);
				maxGlyphY = Math.max(maxGlyphY, y);
			}
		}
		
		//	scale back to font space, inverting back Y coordinate for comparison with font indicated measurements
		int fMinGlyphX = (((minGlyphX - 100) * 10) / 3);
		int fMaxGlyphX = (((maxGlyphX - 100) * 10) / 3);
		int fMinGlyphY = (((maxGlyphY - 900) * 10) / -3);
		int fMaxGlyphY = (((minGlyphY - 900) * 10) / -3);
		if (DEBUG_TYPE1C_LOADING) {
			System.out.println("Glyph extent is " + minGlyphX + "-" + maxGlyphX + " by " + minGlyphY + "-" + maxGlyphY);
			System.out.println(" ==> " + fMinGlyphX + "-" + fMaxGlyphX + " by " + fMinGlyphY + "-" + fMaxGlyphY);
		}
		
		//	compare measured glyph extents to font indicated ascent and descent
		int bbTop = ((pFont.bBox == null) ? 0 : pFont.bBox.top);
		float ascentRatio = (((float) fMaxGlyphY) / Math.max(Math.round(pFont.ascent * 1000), bbTop));
		int bbBottom = ((pFont.bBox == null) ? 0 : pFont.bBox.bottom);
		float descentRatio = (((float) fMinGlyphY) / Math.min(Math.round(pFont.descent * 1000), bbBottom));
		int bbHeight = ((pFont.bBox == null) ? 0 : -pFont.bBox.getHeight());
		float heightRatio = (((float) (fMaxGlyphY - fMinGlyphY)) / Math.max(Math.round((pFont.ascent - pFont.descent) * 1000), bbHeight));
		if (DEBUG_TYPE1C_LOADING) {
			System.out.println("Font indicated height is " + Math.round(pFont.descent * 1000) + "-" + Math.round(pFont.ascent * 1000) + ", bounding box is " + pFont.bBox);
			System.out.println(" ==> height ratio is " + heightRatio + ", ascent ratio is " + ascentRatio + ", descent ratio is " + descentRatio);
		}
		
		//	scale down glyphs if significantly taller than indicated ascent, and adjust descent as well, as font sizes go up for compensation
		BufferedImage sAllGlyphs = null;
		if ((1.5 < ascentRatio) && (charSet == VECTORIZED_OCR_DECODING)) {
			pFont.scaleDownFactor = Math.round(ascentRatio);
			System.out.println(" ==> scaling down glyphs by factor " + pFont.scaleDownFactor);
			pFont.descent = Math.max(pFont.descent, (((float) fMinGlyphY) / (pFont.scaleDownFactor * 1000))); // descent is negative number ...
			System.out.println(" ==> descent adjusted to " + Math.round(pFont.descent * 1000));
			AffineTransform at = AffineTransform.getScaleInstance((1.0 / pFont.scaleDownFactor), (1.0 / pFont.scaleDownFactor));
			sAllGlyphs = new BufferedImage(1500, 1500, BufferedImage.TYPE_BYTE_BINARY);
			Graphics2D sagGr = sAllGlyphs.createGraphics();
			sagGr.setColor(Color.WHITE);
			sagGr.fillRect(0, 0, sAllGlyphs.getWidth(), sAllGlyphs.getHeight());
			sagGr.setColor(Color.BLACK);
			sagGr.translate(100, 900);
			sagGr.scale(0.3, -0.3); // 300 x 300 for 1000 x 1000 PDF font space (font size 300)
			sagGr.setStroke(new BasicStroke(5));
			for (int c = 0; c < otrs.length; c++) {
				if (otrs[c] == null)
					continue;
				otrs[c].path.transform(at);
				sagGr.draw(otrs[c].path);
			}
			sagGr.dispose();
		}
		
		//	make sure to accommodate ascent and descent (keeps glyphs of dash-only fonts in vertical position, and in proportion)
//		maxCapHeight = Math.max(maxCapHeight, Math.round(pFont.ascent * 1000));
//		maxDescent = Math.min(maxDescent, Math.round(pFont.descent * 1000));
		maxCapHeight = Math.max(maxCapHeight, Math.max(Math.round(pFont.ascent * 1000), bbTop));
		maxDescent = Math.min(maxDescent, Math.min(Math.min(Math.round(pFont.descent * 1000), bbBottom), Math.round((maxCapHeight * pFont.descent) / pFont.ascent)));
		
		//	set up rendering
		int maxRenderSize = 300;
		float scale = 1.0f;
		if ((maxCapHeight - maxDescent) > maxRenderSize)
			scale = (((float) maxRenderSize) / (maxCapHeight - maxDescent));
		if (DEBUG_TYPE1C_LOADING) System.out.println(" ==> scaledown factor is " + scale);
		
		//	generate images and match against named char
		pm.setInfo("   - decoding characters");
		CharImage[] charImages = new CharImage[Math.min(csContent.size(), csIndexContent.size())];
		ImageDisplayDialog fidd = ((DEBUG_TYPE1_RENEDRING || DEBUG_TYPE1C_LOADING) ? new ImageDisplayDialog("Font " + pFont.name) : null);
		for (int c = 0; c < Math.min(csContent.size(), csIndexContent.size()); c++) {
			Integer sid;
			
			//	CID font mode
			if (unresolvedCodes == null) {
				sid = new Integer(-1);
				CharImage chi = ((CharImage) pCharDecoder.plainCharCodesToImages.get(charCodes[c]));
//				CharImage chi = ((CharImage) pCharDecoder.glyphKeysToImages.get(glyphKeys[c]));
				if (chi != null) {
					pm.setInfo("     - reusing previously rendered char " + c + " with code " + charCodes[c] + " (" + charNames[c] + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ")");
					charImages[c] = chi;
					pFont.setCharImage(charCodes[c].intValue(), charNames[c], chi.img, chi.path);
//					pFont.setCharImage(glyphKeys[c], chi.img, chi.path);
					continue;
				}
			}
			
			//	Type 1c font mode
			else {
				sid = ((Integer) csContent.get(c));
				if (sid.intValue() == 0)
					continue;
				if ((0 < DEBUG_TYPE1C_TARGET_SID) && (sid.intValue() != DEBUG_TYPE1C_TARGET_SID))
					continue;
//				if (!DEBUG_TYPE1_RENDRING && !pFont.usesCharCode(new Integer(c)) && !pFont.usesCharCode(new Integer((int) chars[c])) && (charNames[c] != null) && !pFont.usedCharNames.contains(charNames[c])) {
				if (!DEBUG_TYPE1_RENEDRING && !pCharDecoder.usesChar(new Integer(c), charNames[c]) && !pCharDecoder.usesChar(new Integer((int) chars[c]), charNames[c])) {
//				if (!DEBUG_TYPE1_RENDRING && !pCharDecoder.usesGlyph(glyphKeys[c])) {
					pm.setInfo("     - ignoring unused char " + c + " with SID " + sid + " (" + charNames[c] + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ")");
					continue;
				}
				CharImage chi = ((CharImage) pCharDecoder.plainCharCodesToImages.get(sid));
//				CharImage chi = ((CharImage) pCharDecoder.glyphKeysToImages.get(glyphKeys[c]));
				if (chi != null) {
					pm.setInfo("     - reusing previously rendered char " + c + " with SID " + sid + " (" + charNames[c] + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ")");
					charImages[c] = chi;
					Integer chc = ((Integer) resolvedCodesOrCidMap.get(charNames[c]));
					if (chc == null)
						chc = ((Integer) unresolvedCodes.get(charNames[c]));
					pFont.setCharImage(((chc == null) ? ((int) chars[c]) : chc.intValue()), charNames[c], chi.img, chi.path);
//					pFont.setCharImage(glyphKeys[c], chi.img, chi.path);
					continue;
				}
				pm.setInfo("     - decoding char " + c + " with SID " + sid + " (" + charNames[c] + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ")");
				if (DEBUG_TYPE1C_LOADING) System.out.println("Decoding char " + c + ", SID is " + sid + " (" + charNames[c] + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ")");
			}
			int chw = ((otrs[c].rWidth == 0) ? dWidth : (nWidth + otrs[c].rWidth));
			if (DEBUG_TYPE1C_LOADING) System.out.println(" - char width is " + chw);
//			if (DEBUG_TYPE1C_LOADING) System.out.println(" - stroke count is " + otrs[c].mCount);
			if (DEBUG_TYPE1C_LOADING) System.out.println(" - stroke count is " + otrs[c].segCount);
			
			//	check if char rendering possible (and in sane bounds)
			if ((otrs[c].maxX <= otrs[c].minX) || (otrs[c].maxY <= otrs[c].minY)) {
				if (DEBUG_TYPE1C_LOADING) System.out.println(" ==> unsensible char dimensions: " + otrs[c].minX + "-" + otrs[c].maxX + "x" + otrs[c].minY + "-" + otrs[c].maxY);
				continue;
			}
			if ((2000 < (otrs[c].maxX - otrs[c].minX)) || (2000 < (otrs[c].maxY - otrs[c].minY))) {
				if (DEBUG_TYPE1C_LOADING) System.out.println(" ==> insane char dimensions: " + otrs[c].minX + "-" + otrs[c].maxX + "x" + otrs[c].minY + "-" + otrs[c].maxY);
				if (DEBUG_TYPE1C_LOADING) System.out.println(" - actual char dimensions: " + otrs[c].minPaintX + "-" + otrs[c].maxX + "x" + otrs[c].minPaintY + "-" + otrs[c].maxY);
				CharUsageStats cus = pFont.getCharUsageStats(charCodes[c]);
				if (cus == null)
					System.out.println("   - no predecessors or successors");
				else {
					System.out.println("   - " + cus.predecessors.elementCount() + " distinct predecessors in " + cus.predecessors.size() + " occurrences");
					System.out.println("   - " + cus.successors.elementCount() + " distinct successors in " + cus.successors.size() + " occurrences");
					System.out.println("   - used in sizes " + pFont.getCharUsageSizes(charCodes[c]));
					System.out.println("   - font used in sizes " + pFont.getCharSizes());
				}
//				continue;
			}
			
			//	render char
			OpType1[] cs = ((OpType1[]) csIndexContent.get(c));
			int mx = 8;
			int my = ((mx * (maxCapHeight - maxDescent)) / (otrs[c].maxX - otrs[c].minX));
			if (DEBUG_TYPE1C_LOADING) System.out.println(" - image size is " + Math.round((scale * (otrs[c].maxX - otrs[c].minX + mx)) + (2 * glyphOutlineFillSafetyEdge)) + "x" + Math.round((scale * (maxCapHeight - maxDescent + my)) + (2 * glyphOutlineFillSafetyEdge)));
//			OpGraphicsType1 ogr = new OpGraphicsType1(
			OpGraphics ogr = new OpGraphics(
					otrs[c].minX,
					maxDescent,
					(maxCapHeight - maxDescent + (my / 2)),
					scale,
					new BufferedImage(
							Math.round((scale * (otrs[c].maxX - otrs[c].minX + mx)) + (2 * glyphOutlineFillSafetyEdge)),
							Math.round((scale * (maxCapHeight - maxDescent + my)) + (2 * glyphOutlineFillSafetyEdge)),
							BufferedImage.TYPE_INT_RGB)
					);
			runFontType1Ops(cs, ogr, otrs[c].isMultiPath, (0 < DEBUG_TYPE1C_TARGET_SID), fidd, charCodes[c], sid.intValue(), charNames[c], pFont.getUnicode(charCodes[c]));
			if (DEBUG_TYPE1C_LOADING) System.out.println(" - image rendered, size is " + ogr.img.getWidth() + "x" + ogr.img.getHeight() + ", OpGraphics height is " + ogr.height);
			charImages[c] = new CharImage(ogr.img, Math.round(((float) (maxCapHeight * ogr.img.getHeight())) / (maxCapHeight - maxDescent)), otrs[c].path, (otrs[c].maxX - otrs[c].minX));
			if (DEBUG_TYPE1C_LOADING) System.out.println(" - char image wrapped, baseline is " + charImages[c].baseline);
			
			//	CID font mode
			if (unresolvedCodes == null) {
//				pFont.setCharImage(((int) chars[c]), charNames[c], ogr.img);
				pFont.setCharImage(charCodes[c].intValue(), charNames[c], ogr.img, otrs[c].path);
//				pFont.setCharImage(glyphKeys[c], ogr.img, otrs[c].path);
				pCharDecoder.plainCharCodesToImages.put(charCodes[c], charImages[c]);
//				pCharDecoder.glyphKeysToImages.put(glyphKeys[c], charImages[c]);
//				String chcUc = pFont.getUnicode(charCodes[c].intValue());
//				char chnUc = ((charNames[c] == null) ? ((char) 0) : StringUtils.getCharForName(charNames[c]));
//				if ((chcUc == null) && (chnUc != 0))
//					chcUc = ("" + chnUc);
//				if (chcUc != null)
//					pCharDecoder.plainCharCodesToUnicode.put(charCodes[c], chcUc);
			}
			
			//	Type 1c font mode
			else {
				Integer chc = ((Integer) resolvedCodesOrCidMap.get(charNames[c]));
				if (chc == null)
					chc = ((Integer) unresolvedCodes.get(charNames[c]));
				pFont.setCharImage(((chc == null) ? ((int) chars[c]) : chc.intValue()), charNames[c], ogr.img, otrs[c].path);
//				pFont.setCharImage(glyphKeys[c], ogr.img, otrs[c].path);
				pCharDecoder.plainCharCodesToImages.put(sid, charImages[c]);
//				pCharDecoder.glyphKeysToImages.put(glyphKeys[c], charImages[c]);
//				String chcUc = pFont.getUnicode((chc == null) ? ((int) chars[c]) : chc.intValue());
//				char chnUc = ((charNames[c] == null) ? ((char) 0) : StringUtils.getCharForName(charNames[c]));
//				if ((chcUc == null) && (chnUc != 0))
//					chcUc = ("" + chnUc);
//				if (chcUc != null)
//					pCharDecoder.plainCharCodesToUnicode.put(sid, chcUc);
//				pFont.setCharImage(((chc == null) ? (c + 31) : chc.intValue()), charNames[c], ogr.img);
//				pFont.setCharImage(((chc == null) ? (c + ((c < (127-31) ? 31 : (31 + 34)))) : chc.intValue()), charNames[c], ogr.img);
//				pFont.setCharImage(((chc == null) ? (c + firstCharCode - firstSid) : chc.intValue()), charNames[c], ogr.img);
				//	TODO_NEVER try chars[c] + <minChar> - 1 if chc is null
				//	TODO_NEVER take <minChar> from csContent
				//	TODO if charset missing, observe font descriptor flags, and use Adobe Default Charset if flag 5 (32) set
				//	TODO lookup char names in Adobe Default Charset if char code (chc) is null (plainly by index (c))
				//	TODO ==> Type1CFontFormat.pdf, page 45
				//	TODO deactivate all the debug flags afterwards
			}
		}
		if ((fidd != null) && (fidd.getImageCount() != 0)) {
			CountingSet charSizes = pFont.getCharSizes();
			if (charSizes != null) {
				System.out.println("   - font used in sizes " + charSizes);
				int kCharHeight = (maxCapHeight - maxDescent);
				CountingSet charHeights = new CountingSet(new TreeMap());
				float charHeightSum = 0;
				for (Iterator csit = charSizes.iterator(); csit.hasNext();) {
					Float cs = ((Float) csit.next());
					float charHeight = ((kCharHeight * cs.floatValue()) / 1000);
					charHeights.add(new Float(charHeight), charSizes.getCount(cs));
					charHeightSum += (charHeight * charSizes.getCount(cs));
				}
				System.out.println("   - char heights (at 72 DPI) " + charHeights);
				System.out.println("   - average char height (at 72 DPI) " + (charHeightSum / charSizes.size()));
			}
			
			agGr = allGlyphs.createGraphics();
			agGr.setColor(Color.BLACK);
			agGr.drawLine(0, 900, 1000, 900);
			agGr.drawLine(0, 600, 1000, 600);
			agGr.drawLine(100, 0, 100, 1500);
			agGr.dispose();
			fidd.addImage(allGlyphs, "All Paths");
			
			if (sAllGlyphs != null) {
				Graphics2D sagGr = sAllGlyphs.createGraphics();
				sagGr.setColor(Color.BLACK);
				sagGr.drawLine(0, 900, 1000, 900);
				sagGr.drawLine(0, 600, 1000, 600);
				sagGr.drawLine(100, 0, 100, 1500);
				sagGr.dispose();
				fidd.addImage(sAllGlyphs, "All Paths (scaled)");
			}
			
			fidd.setLocationRelativeTo(null);
			fidd.setSize(600, 400);
			fidd.setVisible(true);
		}
		
		//	check for char code synonymies
		for (int c = 0; c < Math.min(csContent.size(), csIndexContent.size()); c++) {
			Integer ci = new Integer(c);
			if (charIndicesToSynonyms.containsKey(ci) && (charCodes[c] != null))
				pFont.setCharCodeSynonym(charCodes[c], charCodes[((Integer) charIndicesToSynonyms.get(ci)).intValue()]);
		}
		
		//	decode characters
		decodeChars(pFont, pCharDecoder, chars, charCodes, charNames, glyphKeys, charImages/*, rawCharCodes*/, dWidth, maxDescent, charSet, pm, DEBUG_TYPE1C_LOADING);
	}
	
	private static final boolean DEBUG_TYPE1_RENEDRING = false;
	private static final int DEBUG_TYPE1C_TARGET_SID = -1;
	
	private static void runFontType1Ops(OpType1[] ops, OpReceiver opr, boolean isMultiPath, boolean show, ImageDisplayDialog fidd, int cc, int sid, String cn, String cuc) {
		ImageDisplayDialog idd = (("EnterTargetCharNameHere".equals(cn) && (opr instanceof OpGraphics)) ? new ImageDisplayDialog("Rendering " + cc + " (" + cn + ")") : null);
		if (idd != null)
			idd.setSize((((OpGraphics) opr).img.getWidth() + 200), (((OpGraphics) opr).img.getHeight() + 100));
		boolean emptyOp = false;
		
		for (int o = 0; o < ops.length; o++) {
			int op = ops[o].op;
//			System.out.print("Executing " + ops[o].name);
//			for (int a = 0; a < ops[o].args.length; a++)
//				System.out.print(" " + ops[o].args[a].intValue());
//			System.out.println();
			
			int a = 0;
			int skipped = 0;
			
			while (op != -1) {
/*
	glyphProgOpResolver.put(new Integer(1012), "div");
	glyphProgOpResolver.put(new Integer(1017), "pop");
 */				
				//	hstem, vstem, hstemhm, vstemhm, hstem3, or vstem3
				if ((op == 1) || (op == 3) || (op == 18) || (op == 23) || (op == 1002) || (op == 1001)) {
					if (DEBUG_TYPE1_RENEDRING)
						printOp(op, "<hints>", skipped, a, ops[o].args);
					if ((o == 0) && ((ops[o].args.length % 2) == 1))
						opr.rWidth += ops[o].args[a++].intValue();
					if (DEBUG_TYPE1_RENEDRING)
						System.out.println(" ==> " + opr.x + "/" + opr.y);
					op = -1;
				}
				
				//	dotsection |- dotsection (12 0) |-
				//	brackets an outline section for the dots in letters such as 'i', 'j', and '!'. This is a hint command that indicates that a section of a charstring should be understood as describing such a feature, Chapter 6: CharStrings Dictionary 53 rather than as part of the main outline.
				else if (op == 1000) {
					if (DEBUG_TYPE1_RENEDRING)
						printOp(op, "<hints>", skipped, a, ops[o].args);
					if (DEBUG_TYPE1_RENEDRING)
						System.out.println(" ==> " + opr.x + "/" + opr.y);
					op = -1;
				}
				
				//	hsbw |- sbx wx hsbw (13) |-
				//	sets the left sidebearing point at (sbx, 0) and sets the character width vector to (wx, 0) in character space. This command also sets the current point to (sbx, 0), but does not place the point in the character path.
				else if (op == 13) {
					if (DEBUG_TYPE1_RENEDRING)
						printOp(op, "hsbw", skipped, a, ops[o].args);
					if (ops[o].fixedArgs && ((a+2) <= ops[o].args.length)) {
						a = (ops[a].args.length - 2);
						skipped = a;
						if (DEBUG_TYPE1_RENEDRING)
							System.out.println(" - skipped to parameter " + a);
					}
					if ((a + 2) <= ops[o].args.length) {
//						opr.iSideBearing = ops[o].args[a++].intValue();
//						opr.moveToAbs(opr.iSideBearing, 0);
						opr.moveToAbs(ops[o].args[a++].intValue(), 0);
						opr.iWidth = ops[o].args[a++].intValue();
					}
					if (DEBUG_TYPE1_RENEDRING)
						System.out.println(" ==> " + opr.x + "/" + opr.y);
					op = -1;
				}
				
				//	sbw |- sbx sby wx wy sbw (12 7) |-
				//	sets the left sidebearing point to (sbx, sby) and sets the character width vector to (wx, wy) in character space. This command also sets the current point to (sbx, sby), but does not place the point in the character path.
				else if (op == 1007) {
					if (DEBUG_TYPE1_RENEDRING)
						printOp(op, "sbw", skipped, a, ops[o].args);
					if (ops[o].fixedArgs && ((a+4) <= ops[o].args.length)) {
						a = (ops[o].args.length - 4);
						skipped = a;
						if (DEBUG_TYPE1_RENEDRING)
							System.out.println(" - skipped to parameter " + a);
					}
					if ((a + 4) <= ops[o].args.length) {
//						opr.iSideBearing = ops[o].args[a++].intValue();
//						opr.iUpBearing = ops[o].args[a++].intValue();
//						opr.moveToAbs(opr.iSideBearing, opr.iUpBearing);
						opr.moveToAbs(ops[o].args[a++].intValue(), ops[o].args[a++].intValue());
						opr.iWidth = ops[o].args[a++].intValue();
						opr.iHeight = ops[o].args[a++].intValue();
					}
					if (DEBUG_TYPE1_RENEDRING)
						System.out.println(" ==> " + opr.x + "/" + opr.y);
					op = -1;
				}
				
				//	TODO seac |- asb adx ady bchar achar seac (12 6) |-
				//	for standard encoding accented character, makes an accented character from two other characters in its font program. The asb argument is the x component of the left sidebearing of the accent; this value must be the same as the sidebearing value given in the hsbw or sbw command in the accent’s own charstring. The origin of the accent is placed at (adx, ady) relative to the origin of the base character. The bchar argument is the character code of the base character, and the achar argument is the character code of the accent character.
				else if (op == 1006) {
					if (DEBUG_TYPE1_RENEDRING)
						printOp(op, "seac", skipped, a, ops[o].args);
					if (DEBUG_TYPE1_RENEDRING)
						System.out.println(" ==> " + opr.x + "/" + opr.y);
					op = -1;
				}
				
				//	rmoveto |- dx1 dy1 rmoveto (21) |-
				//	moves the current point to a position at the relative coordinates (dx1, dy1).
				else if (op == 21) {
					if (DEBUG_TYPE1_RENEDRING)
						printOp(op, "rmoveto", skipped, a, ops[o].args);
					if (ops[o].args.length < 2)
						emptyOp = true;
					if (ops[o].fixedArgs && ((a+2) <= ops[o].args.length)) {
						a = (ops[o].args.length - 2);
						skipped = a;
						if (DEBUG_TYPE1_RENEDRING)
							System.out.println(" - skipped to parameter " + a);
					}
					else if ((o == 0) && ((a+2) < ops[o].args.length))
						opr.rWidth += ops[o].args[a++].intValue();
					else if ((a+2) < ops[o].args.length)
						a = (ops[o].args.length - 2);
					if ((a + 2) <= ops[o].args.length)
						opr.moveTo(ops[o].args[a++].intValue(), ops[o].args[a++].intValue());
					if (DEBUG_TYPE1_RENEDRING)
						System.out.println(" ==> " + opr.x + "/" + opr.y);
					op = -1;
				}
				
				//Note 4 The first stack-clearing operator, which must be one of 
				// hstem,
				// hstemhm,
				// vstem,
				// vstemhm,
				// cntrmask,
				// hintmask,
				// - hmoveto,
				// - vmoveto,
				// - rmoveto, or
				// - endchar,
				//takes an additional argument — the width (as described earlier), which may be expressed as zero or one numeric argument.
				
				//	hmoveto |- dx1 hmoveto (22) |-
				//	moves the current point dx1 units in the horizontal direction. See Note 4.
				else if (op == 22) {
					if (DEBUG_TYPE1_RENEDRING)
						printOp(op, "hmoveto", skipped, a, ops[o].args);
					if (ops[o].args.length < 1)
						emptyOp = true;
					if (ops[o].fixedArgs && ((a+1) <= ops[o].args.length)) {
						a = (ops[o].args.length - 1);
						skipped = a;
						if (DEBUG_TYPE1_RENEDRING)
							System.out.println(" - skipped to parameter " + a);
					}
					else if ((o == 0) && ((a+1) < ops[o].args.length))
						opr.rWidth += ops[o].args[a++].intValue(); 
					if ((a + 1) <= ops[o].args.length)
						opr.moveTo(ops[o].args[a++].intValue(), 0);
					if (DEBUG_TYPE1_RENEDRING)
						System.out.println(" ==> " + opr.x + "/" + opr.y);
					op = -1;
				}
				
				//	TODO setcurrentpoint |- x y setcurrentpoint (12 33) |-
				//	sets the current point in the Type 1 font format BuildChar to (x, y) in absolute character space coordinates without performing a charstring moveto command.
				else if (op == 1033) {
					if (DEBUG_TYPE1_RENEDRING)
						printOp(op, "setcurrentpoint", skipped, a, ops[o].args);
					if (DEBUG_TYPE1_RENEDRING)
						System.out.println(" ==> " + opr.x + "/" + opr.y);
					op = -1;
				}
				
				//	vmoveto |- dy1 vmoveto (4) |-
				//	moves the current point dy1 units in the vertical direction. See Note 4.
				else if (op == 4) {
					if (DEBUG_TYPE1_RENEDRING)
						printOp(op, "vmoveto", skipped, a, ops[o].args);
					if (ops[o].args.length < 1)
						emptyOp = true;
					if (ops[o].fixedArgs && ((a+1) <= ops[o].args.length)) {
						a = (ops[o].args.length - 1);
						skipped = a;
						if (DEBUG_TYPE1_RENEDRING)
							System.out.println(" - skipped to parameter " + a);
					}
					else if ((o == 0) && ((a+1) < ops[o].args.length))
						opr.rWidth += ops[o].args[a++].intValue(); 
					if ((a + 1) <= ops[o].args.length)
						opr.moveTo(0, ops[o].args[a++].intValue());
					if (DEBUG_TYPE1_RENEDRING)
						System.out.println(" ==> " + opr.x + "/" + opr.y);
					op = -1;
				}
				
				//	rlineto |- {dxa dya}+ rlineto (5) |-
				//	appends a line from the current point to a position at the relative coordinates dxa, dya. Additional rlineto operations are performed for all subsequent argument pairs. The number of lines is determined from the number of arguments on the stack.
				else if (op == 5) {
					if (DEBUG_TYPE1_RENEDRING)
						printOp(op, "rlineto", skipped, a, ops[o].args);
					if (((ops[o].args.length - a) % 2) != 0)
						a++;
					if (ops[o].args.length < 2)
						emptyOp = true;
					if (ops[o].fixedArgs && ((a+2) <= ops[o].args.length)) {
						a = (ops[o].args.length - 2);
						skipped = a;
						if (DEBUG_TYPE1_RENEDRING)
							System.out.println(" - skipped to parameter " + a);
					}
					if ((a+2) <= ops[o].args.length) {
						opr.lineTo(ops[o].args[a++].intValue(), ops[o].args[a++].intValue());
						if (DEBUG_TYPE1_RENEDRING)
							System.out.println(" ==> " + opr.x + "/" + opr.y);
					}
					else op = -1;
				}
				
				//	flexabslineto |- treshold axa aya flexabslineto (55) |- (converted from Type1 FLEX absolute end point)
				else if (op == 55) {
					if (DEBUG_TYPE1_RENEDRING)
						printOp(op, "flexabslineto", skipped, a, ops[o].args);
					if (((ops[o].args.length - a) % 2) != 0)
						a++;
					if (ops[o].args.length < 2)
						emptyOp = true;
					if (ops[o].fixedArgs && ((a+2) <= ops[o].args.length)) {
						a = (ops[o].args.length - 2);
						skipped = a;
						if (DEBUG_TYPE1_RENEDRING)
							System.out.println(" - skipped to parameter " + a);
					}
					if ((a+2) <= ops[o].args.length) {
						int rx = (opr.x - ops[o].args[a++].intValue());
						int ry = (opr.y - ops[o].args[a++].intValue());
						if (DEBUG_TYPE1_RENEDRING)
							System.out.println(" - result is rlineto(5): " + rx + " " + ry);
						opr.lineTo(rx, ry);
						if (DEBUG_TYPE1_RENEDRING)
							System.out.println(" ==> " + opr.x + "/" + opr.y);
//						opr.lineTo(ops[o].args[a++].intValue(), ops[o].args[a++].intValue());
					}
					op = -1;
				}
				
				//	hlineto |- dx1 {dya dxb}* hlineto (6) |- OR |- {dxa dyb}+ hlineto (6) |-
				//	appends a horizontal line of length dx1 to the current point.
				//	With an odd number of arguments, subsequent argument pairs are interpreted as alternating values of dy and dx, for which additional lineto operators draw alternating vertical and horizontal lines.
				//	With an even number of arguments, the arguments are interpreted as alternating horizontal and vertical lines. The number of lines is determined from the number of arguments on the stack.
				else if (op == 6) {
					if (DEBUG_TYPE1_RENEDRING)
						printOp(op, "hlineto", skipped, a, ops[o].args);
					if (ops[o].args.length < 1)
						emptyOp = true;
					if (ops[o].fixedArgs && ((a+1) <= ops[o].args.length)) {
						a = (ops[o].args.length - 1);
						skipped = a;
						if (DEBUG_TYPE1_RENEDRING)
							System.out.println(" - skipped to parameter " + a);
					}
					if ((a+1) <= ops[o].args.length) {
						opr.lineTo(ops[o].args[a++].intValue(), 0);
						if (DEBUG_TYPE1_RENEDRING)
							System.out.println(" ==> " + opr.x + "/" + opr.y);
						op = 7;
					}
					else op = -1;
				}
				
				//	vlineto |- dy1 {dxa dyb}* vlineto (7) |- OR |- {dya dxb}+ vlineto (7) |-
				//	appends a vertical line of length dy1 to the current point.
				//	With an odd number of arguments, subsequent argument pairs are interpreted as alternating values of dx and dy, for which additional lineto operators draw alternating horizontal and vertical lines.
				//	With an even number of arguments, the arguments are interpreted as alternating vertical and horizontal lines. The number of lines is determined from the number of arguments on the stack.
				else if (op == 7) {
					if (DEBUG_TYPE1_RENEDRING)
						printOp(op, "vlineto", skipped, a, ops[o].args);
					if (ops[o].args.length < 1)
						emptyOp = true;
					if (ops[o].fixedArgs && ((a+1) <= ops[o].args.length)) {
						a = (ops[o].args.length - 1);
						skipped = a;
						if (DEBUG_TYPE1_RENEDRING)
							System.out.println(" - skipped to parameter " + a);
					}
					if ((a+1) <= ops[o].args.length) {
						opr.lineTo(0, ops[o].args[a++].intValue());
						if (DEBUG_TYPE1_RENEDRING)
							System.out.println(" ==> " + opr.x + "/" + opr.y);
						op = 6;
					}
					else op = -1;
				}
				
				//	rrcurveto |- {dxa dya dxb dyb dxc dyc}+ rrcurveto (8) |-
				//	appends a Bézier curve, defined by dxa...dyc, to the current point. For each subsequent set of six arguments, an additional curve is appended to the current point. The number of curve segments is determined from the number of arguments on the number stack and is limited only by the size of the number stack.
				else if (op == 8) {
					if (DEBUG_TYPE1_RENEDRING)
						printOp(op, "rrcurveto", skipped, a, ops[o].args);
					
					//	skip superfluous arguments
					if (ops[o].fixedArgs && ((a+6) <= ops[o].args.length)) {
						a = (ops[o].args.length - 6);
						skipped = a;
						if (DEBUG_TYPE1_RENEDRING)
							System.out.println(" - skipped to parameter " + a);
					}
					else if (a == skipped) {
						while ((a < ops[o].args.length) && (((ops[o].args.length - a) % 6) != 0))
							a++;
						if (DEBUG_TYPE1_RENEDRING)
							System.out.println(" - skipped to parameter " + a);
					}
					if (ops[o].args.length < 6)
						emptyOp = true;
					
					//	execute op
					if ((a+6) <= ops[o].args.length) {
						opr.curveTo(ops[o].args[a++].intValue(), ops[o].args[a++].intValue(), ops[o].args[a++].intValue(), ops[o].args[a++].intValue(), ops[o].args[a++].intValue(), ops[o].args[a++].intValue());
						if (DEBUG_TYPE1_RENEDRING)
							System.out.println(" ==> " + opr.x + "/" + opr.y);
					}
					else op = -1;
				}
				
				//	hhcurveto |- dy1? {dxa dxb dyb dxc}+ hhcurveto (27) |-
				//	appends one or more Bézier curves, as described by the dxa...dxc set of arguments, to the current point. For each curve, if there are 4 arguments, the curve starts and ends horizontal. The first curve need not start horizontal (the odd argument case). Note the argument order for the odd argument case.
				else if (op == 27) {
					if (DEBUG_TYPE1_RENEDRING)
						printOp(op, "hhcurveto", skipped, a, ops[o].args);
					
					//	skip superfluous arguments
					if (a == skipped) {
						while ((a < ops[o].args.length) && (((ops[o].args.length - a) % 4) > 1)) {
							a++;
							skipped++;
						}
						if (DEBUG_TYPE1_RENEDRING)
							System.out.println(" - skipped to parameter " + a);
					}
					if (ops[o].args.length < 4)
						emptyOp = true;
					
					//	execute op
					if ((a+4) <= ops[o].args.length) {
						int dx1;
						int dy1;
						int dx2;
						int dy2;
						int dx3;
						int dy3;
						if ((a == skipped) && (((ops[o].args.length - skipped) % 4) == 1))
							dy1 = ops[o].args[a++].intValue();
						else dy1 = 0;
						dx1 = ops[o].args[a++].intValue();
						dx2 = ops[o].args[a++].intValue();
						dy2 = ops[o].args[a++].intValue();
						dx3 = ops[o].args[a++].intValue();
						dy3 = 0;
						opr.curveTo(dx1, dy1, dx2, dy2, dx3, dy3);
						if (DEBUG_TYPE1_RENEDRING)
							System.out.println(" ==> " + opr.x + "/" + opr.y);
					}
					else op = -1;
				}
				
				//	hvcurveto |- dx1 dx2 dy2 dy3 {dya dxb dyb dxc dxd dxe dye dyf}* dxf? hvcurveto (31) |- OR |- {dxa dxb dyb dyc dyd dxe dye dxf}+ dyf? hvcurveto (31) |-
				//	appends one or more Bézier curves to the current point. The tangent for the first Bézier must be horizontal, and the second must be vertical (except as noted below). If there is a multiple of four arguments, the curve starts horizontal and ends vertical. Note that the curves alternate between start horizontal, end vertical, and start vertical, and end horizontal. The last curve (the odd argument case) need not end horizontal/vertical.
				else if (op == 31) {
					if (DEBUG_TYPE1_RENEDRING)
						printOp(op, "hvcurveto", skipped, a, ops[o].args);
					
					//	skip superfluous arguments
					if (ops[o].fixedArgs && ((a+4) <= ops[o].args.length)) {
						a = (ops[o].args.length - 4);
						skipped = a;
						if (DEBUG_TYPE1_RENEDRING)
							System.out.println(" - skipped to parameter " + a);
					}
					else if (a == skipped) {
						while ((a < ops[o].args.length) && (((ops[o].args.length - a) % 4) > 1)) {
							a++;
							skipped++;
						}
						if (DEBUG_TYPE1_RENEDRING)
							System.out.println(" - skipped to parameter " + a);
					}
					if (ops[o].args.length < 4)
						emptyOp = true;
					
					//	hvcurveto |- dx1 dx2 dy2 dy3 {dya dxb dyb dxc dxd dxe dye dyf}* dxf? hvcurveto (31) |-
					if (((ops[o].args.length - skipped) % 8) >= 4) {
						if (a == skipped) {
							int dx1;
							int dy1;
							int dx2;
							int dy2;
							int dx3;
							int dy3;
							dx1 = ops[o].args[a++].intValue();
							dy1 = 0;
							dx2 = ops[o].args[a++].intValue();
							dy2 = ops[o].args[a++].intValue();
							if ((a+2) == ops[o].args.length) {
								dy3 = ops[o].args[a++].intValue();
								dx3 = ops[o].args[a++].intValue();
							}
							else {
								dy3 = ops[o].args[a++].intValue();
								dx3 = 0;
							}
							opr.curveTo(dx1, dy1, dx2, dy2, dx3, dy3);
							if (DEBUG_TYPE1_RENEDRING)
								System.out.println(" ==> " + opr.x + "/" + opr.y);
						}
						else if ((a+8) <= ops[o].args.length) {
							int dx1;
							int dy1;
							int dx2;
							int dy2;
							int dx3;
							int dy3;
							dx1 = 0;
							dy1 = ops[o].args[a++].intValue();
							dx2 = ops[o].args[a++].intValue();
							dy2 = ops[o].args[a++].intValue();
							dx3 = ops[o].args[a++].intValue();
							dy3 = 0;
							opr.curveTo(dx1, dy1, dx2, dy2, dx3, dy3);
							dx1 = ops[o].args[a++].intValue();
							dy1 = 0;
							dx2 = ops[o].args[a++].intValue();
							dy2 = ops[o].args[a++].intValue();
							if ((a+2) == ops[o].args.length) {
								dy3 = ops[o].args[a++].intValue();
								dx3 = ops[o].args[a++].intValue();
							}
							else {
								dy3 = ops[o].args[a++].intValue();
								dx3 = 0;
							}
							opr.curveTo(dx1, dy1, dx2, dy2, dx3, dy3);
							if (DEBUG_TYPE1_RENEDRING)
								System.out.println(" ==> " + opr.x + "/" + opr.y);
						}
						else op = -1;
					}
					
					//	hvcurveto |- {dxa dxb dyb dyc dyd dxe dye dxf}+ dyf? hvcurveto (31) |-
					else {
						if ((a+8) <= ops[o].args.length) {
							int dx1;
							int dy1;
							int dx2;
							int dy2;
							int dx3;
							int dy3;
							dx1 = ops[o].args[a++].intValue();
							dy1 = 0;
							dx2 = ops[o].args[a++].intValue();
							dy2 = ops[o].args[a++].intValue();
							dx3 = 0;
							dy3 = ops[o].args[a++].intValue();
							opr.curveTo(dx1, dy1, dx2, dy2, dx3, dy3);
							dx1 = 0;
							dy1 = ops[o].args[a++].intValue();
							dx2 = ops[o].args[a++].intValue();
							dy2 = ops[o].args[a++].intValue();
							dx3 = ops[o].args[a++].intValue();
							if ((a+1) == ops[o].args.length)
								dy3 = ops[o].args[a++].intValue();
							else dy3 = 0;
							opr.curveTo(dx1, dy1, dx2, dy2, dx3, dy3);
							if (DEBUG_TYPE1_RENEDRING)
								System.out.println(" ==> " + opr.x + "/" + opr.y);
						}
						else op = -1;
					}
				}
				
				//	rcurveline |- {dxa dya dxb dyb dxc dyc}+ dxd dyd rcurveline (24) |-
				//	is equivalent to one rrcurveto for each set of six arguments dxa...dyc, followed by exactly one rlineto using the dxd, dyd arguments. The number of curves is determined from the count on the argument stack.
				else if (op == 24) {
					if (DEBUG_TYPE1_RENEDRING)
						printOp(op, "rcurveline", skipped, a, ops[o].args);
					
					//	skip superfluous arguments
					if (a == skipped) {
						while ((a < ops[o].args.length) && (((ops[o].args.length - a) % 6) != 2))
							a++;
						if (DEBUG_TYPE1_RENEDRING)
							System.out.println(" - skipped to parameter " + a);
					}
					if (ops[o].args.length < 8)
						emptyOp = true;
					
					//	execute op
					while ((a+8) <= ops[o].args.length) {
						opr.curveTo(ops[o].args[a++].intValue(), ops[o].args[a++].intValue(), ops[o].args[a++].intValue(), ops[o].args[a++].intValue(), ops[o].args[a++].intValue(), ops[o].args[a++].intValue());
						if (DEBUG_TYPE1_RENEDRING)
							System.out.println(" ==> " + opr.x + "/" + opr.y);
					}
					if ((a+2) <= ops[o].args.length) {
						opr.lineTo(ops[o].args[a++].intValue(), ops[o].args[a++].intValue());
						if (DEBUG_TYPE1_RENEDRING)
							System.out.println(" ==> " + opr.x + "/" + opr.y);
					}
					op = -1;
				}
				
				//	rlinecurve |- {dxa dya}+ dxb dyb dxc dyc dxd dyd rlinecurve (25) |-
				//	is equivalent to one rlineto for each pair of arguments beyond the six arguments dxb...dyd needed for the one rrcurveto command. The number of lines is determined from the count of items on the argument stack.
				else if (op == 25) {
					if (DEBUG_TYPE1_RENEDRING)
						printOp(op, "rlinecurve", skipped, a, ops[o].args);
					
					//	skip superfluous arguments
					if (a == skipped) {
						while ((a < ops[o].args.length) && (((ops[o].args.length - a) % 2) != 0))
							a++;
						if (DEBUG_TYPE1_RENEDRING)
							System.out.println(" - skipped to parameter " + a);
					}
					if (ops[o].args.length < 8)
						emptyOp = true;
					
					//	execute op
					while ((a+8) <= ops[o].args.length) {
						opr.lineTo(ops[o].args[a++].intValue(), ops[o].args[a++].intValue());
						if (DEBUG_TYPE1_RENEDRING)
							System.out.println(" ==> " + opr.x + "/" + opr.y);
					}
					if ((a+6) <= ops[o].args.length) {
						opr.curveTo(ops[o].args[a++].intValue(), ops[o].args[a++].intValue(), ops[o].args[a++].intValue(), ops[o].args[a++].intValue(), ops[o].args[a++].intValue(), ops[o].args[a++].intValue());
						if (DEBUG_TYPE1_RENEDRING)
							System.out.println(" ==> " + opr.x + "/" + opr.y);
					}
					op = -1;
				}
				
				//	vhcurveto |- dy1 dx2 dy2 dx3 {dxa dxb dyb dyc dyd dxe dye dxf}* dyf? vhcurveto (30) |- OR |- {dya dxb dyb dxc dxd dxe dye dyf}+ dxf? vhcurveto (30) |-
				//	appends one or more Bézier curves to the current point, where the first tangent is vertical and the second tangent is horizontal. This command is the complement of hvcurveto; see the description of hvcurveto for more information.
				else if (op == 30) {
					if (DEBUG_TYPE1_RENEDRING)
						printOp(op, "vhcurveto", skipped, a, ops[o].args);
					
					//	skip superfluous arguments
					if (ops[o].fixedArgs && ((a+4) <= ops[o].args.length)) {
						a = (ops[o].args.length - 4);
						skipped = a;
						if (DEBUG_TYPE1_RENEDRING)
							System.out.println(" - skipped to parameter " + a);
					}
					else if (a == skipped) {
						while ((a < ops[o].args.length) && (((ops[o].args.length - a) % 4) > 1)) {
							a++;
							skipped++;
						}
						if (DEBUG_TYPE1_RENEDRING)
							System.out.println(" - skipped to parameter " + a);
					}
					if (ops[o].args.length < 4)
						emptyOp = true;
					
					//	vhcurveto |- dy1 dx2 dy2 dx3 {dxa dxb dyb dyc dyd dxe dye dxf}* dyf? vhcurveto (30) |-
					if (((ops[o].args.length - skipped) % 8) >= 4) {
						if (a == skipped) {
							int dx1;
							int dy1;
							int dx2;
							int dy2;
							int dx3;
							int dy3;
							dx1 = 0;
							dy1 = ops[o].args[a++].intValue();
							dx2 = ops[o].args[a++].intValue();
							dy2 = ops[o].args[a++].intValue();
							dx3 = ops[o].args[a++].intValue();
							if ((a+1) == ops[o].args.length)
								dy3 = ops[o].args[a++].intValue();
							else dy3 = 0;
							opr.curveTo(dx1, dy1, dx2, dy2, dx3, dy3);
							if (DEBUG_TYPE1_RENEDRING)
								System.out.println(" ==> " + opr.x + "/" + opr.y);
						}
						else if ((a+8) <= ops[o].args.length) {
							int dx1;
							int dy1;
							int dx2;
							int dy2;
							int dx3;
							int dy3;
							dx1 = ops[o].args[a++].intValue();
							dy1 = 0;
							dx2 = ops[o].args[a++].intValue();
							dy2 = ops[o].args[a++].intValue();
							dx3 = 0;
							dy3 = ops[o].args[a++].intValue();
							opr.curveTo(dx1, dy1, dx2, dy2, dx3, dy3);
							dx1 = 0;
							dy1 = ops[o].args[a++].intValue();
							dx2 = ops[o].args[a++].intValue();
							dy2 = ops[o].args[a++].intValue();
							dx3 = ops[o].args[a++].intValue();
							if ((a+1) == ops[o].args.length)
								dy3 = ops[o].args[a++].intValue();
							else dy3 = 0;
							opr.curveTo(dx1, dy1, dx2, dy2, dx3, dy3);
							if (DEBUG_TYPE1_RENEDRING)
								System.out.println(" ==> " + opr.x + "/" + opr.y);
						}
						else op = -1;
					}
					
					//	vhcurveto |- {dya dxb dyb dxc dxd dxe dye dyf}+ dxf? vhcurveto (30) |-
					else {
						if ((a+8) <= ops[o].args.length) {
							int dx1;
							int dy1;
							int dx2;
							int dy2;
							int dx3;
							int dy3;
							dx1 = 0;
							dy1 = ops[o].args[a++].intValue();
							dx2 = ops[o].args[a++].intValue();
							dy2 = ops[o].args[a++].intValue();
							dx3 = ops[o].args[a++].intValue();
							dy3 = 0;
							opr.curveTo(dx1, dy1, dx2, dy2, dx3, dy3);
							dx1 = ops[o].args[a++].intValue();
							dy1 = 0;
							dx2 = ops[o].args[a++].intValue();
							dy2 = ops[o].args[a++].intValue();
							if ((a+2) == ops[o].args.length) {
								dy3 = ops[o].args[a++].intValue();
								dx3 = ops[o].args[a++].intValue();
							}
							else {
								dy3 = ops[o].args[a++].intValue();
								dx3 = 0;
							}
							opr.curveTo(dx1, dy1, dx2, dy2, dx3, dy3);
							if (DEBUG_TYPE1_RENEDRING)
								System.out.println(" ==> " + opr.x + "/" + opr.y);
						}
						else op = -1;
					}
				}
				
				//	vvcurveto |- dx1? {dya dxb dyb dyc}+ vvcurveto (26) |-
				//	appends one or more curves to the current point. If the argument count is a multiple of four, the curve starts and ends vertical. If the argument count is odd, the first curve does not begin with a vertical tangent.
				else if (op == 26) {
					if (DEBUG_TYPE1_RENEDRING)
						printOp(op, "vvcurveto", skipped, a, ops[o].args);
					
					//	skip superfluous arguments
					if (a == skipped) {
						while ((a < ops[o].args.length) && (((ops[o].args.length - a) % 4) > 1)) {
							a++;
							skipped++;
						}
						if (DEBUG_TYPE1_RENEDRING)
							System.out.println(" - skipped to parameter " + a);
					}
					if (ops[o].args.length < 4)
						emptyOp = true;
					
					//	execute op
					if ((a+4) <= ops[o].args.length) {
						int dx1;
						int dy1;
						int dx2;
						int dy2;
						int dx3;
						int dy3;
						if ((a == skipped) && (((ops[o].args.length - skipped) % 4) == 1))
							dx1 = ops[o].args[a++].intValue();
						else dx1 = 0;
						dy1 = ops[o].args[a++].intValue();
						dx2 = ops[o].args[a++].intValue();
						dy2 = ops[o].args[a++].intValue();
						dx3 = 0;
						dy3 = ops[o].args[a++].intValue();
						opr.curveTo(dx1, dy1, dx2, dy2, dx3, dy3);
						if (DEBUG_TYPE1_RENEDRING)
							System.out.println(" ==> " + opr.x + "/" + opr.y);
					}
					else op = -1;
				}
				
				//flex |- dx1 dy1 dx2 dy2 dx3 dy3 dx4 dy4 dx5 dy5 dx6 dy6 fd flex (12 35) |-
				//causes two Bézier curves, as described by the arguments (as shown in Figure 2 below), to be rendered as a straight line when the flex depth is less than fd /100 device pixels, and as curved lines when the flex depth is greater than or equal to fd/100 device pixels. The flex depth for a horizontal curve, as shown in Figure 2, is the distance from the join point to the line connecting the start and end points on the curve. If the curve is not exactly horizontal or vertical, it must be determined whether the curve is more horizontal or vertical by the method described in the flex1 description, below, and as illustrated in Figure 3.
				//Note 5 In cases where some of the points have the same x or y coordinate as other points in the curves, arguments may be omitted by using one of the following forms of the flex operator, hflex, hflex1, or flex1.
				else if (op == 1035) {
					if (DEBUG_TYPE1_RENEDRING)
						printOp(op, "flex", skipped, a, ops[o].args);
					
					if (ops[o].args.length < 13)
						emptyOp = true;
					
					//	execute op
					if ((a+12) <= ops[o].args.length) {
						int dx1;
						int dy1;
						int dx2;
						int dy2;
						int dx3;
						int dy3;
						dx1 = ops[o].args[a++].intValue();
						dy1 = ops[o].args[a++].intValue();
						dx2 = ops[o].args[a++].intValue();
						dy2 = ops[o].args[a++].intValue();
						dx3 = ops[o].args[a++].intValue();
						dy3 = ops[o].args[a++].intValue();
						opr.curveTo(dx1, dy1, dx2, dy2, dx3, dy3);
						dx1 = ops[o].args[a++].intValue();
						dy1 = ops[o].args[a++].intValue();
						dx2 = ops[o].args[a++].intValue();
						dy2 = ops[o].args[a++].intValue();
						dx3 = ops[o].args[a++].intValue();
						dy3 = ops[o].args[a++].intValue();
						opr.curveTo(dx1, dy1, dx2, dy2, dx3, dy3);
						if (DEBUG_TYPE1_RENEDRING)
							System.out.println(" ==> " + opr.x + "/" + opr.y);
					}
					else op = -1;
				}
				
				//hflex |- dx1 dx2 dy2 dx3 dx4 dx5 dx6 hflex (12 34) |-
				//causes the two curves described by the arguments dx1...dx6 to be rendered as a straight line when the flex depth is less than 0.5 (that is, fd is 50) device pixels, and as curved lines when the flex depth is greater than or equal to 0.5 device pixels. hflex is used when the following are all true:
				//a) the starting and ending points, first and last control points have the same y value.
				//b) the joining point and the neighbor control points have the same y value.
				//c) the flex depth is 50.
				else if (op == 1034) {
					if (DEBUG_TYPE1_RENEDRING)
						printOp(op, "hflex", skipped, a, ops[o].args);
					
					if (ops[o].args.length < 7)
						emptyOp = true;
					
					//	execute op
					if ((a+7) <= ops[o].args.length) {
						int dx1;
						int dy1;
						int dx2;
						int dy2;
						int dx3;
						int dy3;
						dx1 = ops[o].args[a++].intValue();
						dy1 = 0;
						dx2 = ops[o].args[a++].intValue();
						dy2 = ops[o].args[a++].intValue();
						dx3 = ops[o].args[a++].intValue();
						dy3 = 0;
						opr.curveTo(dx1, dy1, dx2, dy2, dx3, dy3);
						dx1 = ops[o].args[a++].intValue();
						dy1 = 0;
						dx2 = ops[o].args[a++].intValue();
						dy2 = 0;
						dx3 = ops[o].args[a++].intValue();
						dy3 = 0;
						opr.curveTo(dx1, dy1, dx2, dy2, dx3, dy3);
						if (DEBUG_TYPE1_RENEDRING)
							System.out.println(" ==> " + opr.x + "/" + opr.y);
					}
					else op = -1;
				}
				
				//hflex1 |- dx1 dy1 dx2 dy2 dx3 dx4 dx5 dy5 dx6 hflex1 (12 36) |-
				//causes the two curves described by the arguments to be rendered as a straight line when the flex depth is less than 0.5 device pixels, and as curved lines when the flex depth is greater than or equal to 0.5 device pixels. hflex1 is used if the conditions for hflex are not met but all of the following are true:
				//a) the starting and ending points have the same y value,
				//b) the joining point and the neighbor control points have the same y value.
				else if (op == 1036) {
					if (DEBUG_TYPE1_RENEDRING)
						printOp(op, "hflex1", skipped, a, ops[o].args);
					
					if (ops[o].args.length < 9)
						emptyOp = true;
					
					//	execute op
					if ((a+9) <= ops[o].args.length) {
						int dx1;
						int dy1;
						int dx2;
						int dy2;
						int dx3;
						int dy3;
						dx1 = ops[o].args[a++].intValue();
						dy1 = ops[o].args[a++].intValue();
						dx2 = ops[o].args[a++].intValue();
						dy2 = ops[o].args[a++].intValue();
						dx3 = ops[o].args[a++].intValue();
						dy3 = 0;
						opr.curveTo(dx1, dy1, dx2, dy2, dx3, dy3);
						dx1 = ops[o].args[a++].intValue();
						dy1 = 0;
						dx2 = ops[o].args[a++].intValue();
						dy2 = ops[o].args[a++].intValue();
						dx3 = ops[o].args[a++].intValue();
						dy3 = 0;
						opr.curveTo(dx1, dy1, dx2, dy2, dx3, dy3);
						if (DEBUG_TYPE1_RENEDRING)
							System.out.println(" ==> " + opr.x + "/" + opr.y);
					}
					else op = -1;
				}
				
				//flex1 |- dx1 dy1 dx2 dy2 dx3 dy3 dx4 dy4 dx5 dy5 d6 flex1 (12 37) |-
				//causes the two curves described by the arguments to be rendered as a straight line when the flex depth is less than 0.5 device pixels, and as curved lines when the flex depth is greater than or equal to 0.5 device pixels.
				//The d6 argument will be either a dx or dy value, depending on the curve (see Figure 3). To determine the correct value, compute the distance from the starting point (x, y), the first point of the first curve, to the last flex control point (dx5, dy5) by summing all the arguments except d6; call this (dx, dy). If abs(dx) > abs(dy), then the last point’s x-value is given by d6, and its y-value is equal to y. Otherwise, the last point’s x-value is equal to x and its y-value is given by d6.
				//flex1 is used if the conditions for hflex and hflex1 are notmet but all of the following are true:
				//a) the starting and ending points have the same x or y value,
				//b) the flex depth is 50.
				else if (op == 1037) {
					if (DEBUG_TYPE1_RENEDRING)
						printOp(op, "flex1", skipped, a, ops[o].args);
					
					if (ops[o].args.length < 11)
						emptyOp = true;
					
					//	execute op
					if ((a+11) <= ops[o].args.length) {
						int sx = opr.x;
						int sy = opr.y;
						int dx1;
						int dy1;
						int dx2;
						int dy2;
						int dx3;
						int dy3;
						dx1 = ops[o].args[a++].intValue();
						dy1 = ops[o].args[a++].intValue();
						dx2 = ops[o].args[a++].intValue();
						dy2 = ops[o].args[a++].intValue();
						dx3 = ops[o].args[a++].intValue();
						dy3 = ops[o].args[a++].intValue();
						opr.curveTo(dx1, dy1, dx2, dy2, dx3, dy3);
						dx1 = ops[o].args[a++].intValue();
						dy1 = ops[o].args[a++].intValue();
						dx2 = ops[o].args[a++].intValue();
						dy2 = ops[o].args[a++].intValue();
						int dx = (opr.x + dx1 + dx2 - sx);
						int dy = (opr.y + dy1 + dy2 - sy);
						if (Math.abs(dx) > Math.abs(dy)) {
							dx3 = ops[o].args[a++].intValue();
							dy3 = 0;
						}
						else {
							dx3 = 0;
							dy3 = ops[o].args[a++].intValue();
						}
						opr.curveTo(dx1, dy1, dx2, dy2, dx3, dy3);
						if (DEBUG_TYPE1_RENEDRING)
							System.out.println(" ==> " + opr.x + "/" + opr.y);
					}
					else op = -1;
				}
				
				//	endchar – endchar (14) |–
				//	finishes a charstring outline definition, and must be the last operator in a character’s outline.
				else if (op == 14) {
					if (DEBUG_TYPE1_RENEDRING)
						printOp(op, "endchar", skipped, a, ops[o].args);
					if ((o == 0) && (ops[o].args.length > 0))
						opr.rWidth += ops[o].args[a++].intValue(); 
					opr.closePath();
					if (DEBUG_TYPE1_RENEDRING)
						System.out.println(" ==> " + opr.x + "/" + opr.y);
					op = -1;
				}
				
				//	hintmask
				else if (op == 19) {
					if (DEBUG_TYPE1_RENEDRING)
						printOp(op, "hintmask", skipped, a, ops[o].args);
					if ((o == 0) && (ops[o].args.length > 1))
						opr.rWidth += ops[o].args[a++].intValue();
					if (DEBUG_TYPE1_RENEDRING)
						System.out.println(" ==> " + opr.x + "/" + opr.y);
					op = -1;
				}
				
				//	cntrmask
				else if (op == 20) {
					if (DEBUG_TYPE1_RENEDRING)
						printOp(op, "cntrmask", skipped, a, ops[o].args);
					if ((o == 0) && (ops[o].args.length > 1))
						opr.rWidth += ops[o].args[a++].intValue();
					if (DEBUG_TYPE1_RENEDRING)
						System.out.println(" ==> " + opr.x + "/" + opr.y);
					op = -1;
				}
				
				//	closepath |- closepath (9) |-
				//	closepath closes a subpath. Adobe strongly recommends that all character subpaths end with a closepath command, otherwise when an outline is stroked (by setting PaintType equal to 2) you may get unexpected behavior where lines join. Note that, unlike the closepath command in the PostScript language, this command does not reposition the current point. Any subsequent rmoveto must be relative to the current point in force before the Type 1 font format closepath command was given.
				else if (op == 9) {
					if (DEBUG_TYPE1_RENEDRING)
						printOp(op, "closepath", skipped, a, ops[o].args);
					opr.closePath();
					if (DEBUG_TYPE1_RENEDRING)
						System.out.println(" ==> " + opr.x + "/" + opr.y);
					op = -1;
				}
				
				//Note 6 The charstring itself may end with a call(g)subr; the subroutine must then end with an endchar operator.
				//Note 7 A character that does not have a path (e.g. a space character) may consist of an endchar operator preceded only by a width value. Although the width must be specified in the font, it may be specified as the defaultWidthX in the CFF data, in which case it should not be specified in the charstring. Also, it may appear in the charstring as the difference from nominalWidthX. Thus the smallest legal charstring consists of a single endchar operator.
				//Note 8 endchar also has a deprecated function; see Appendix C, “Comaptibility and Deprecated Operators.”
				else {
					if (DEBUG_TYPE1_RENEDRING)
						printOp(op, null, skipped, a, ops[o].args);
					op = -1;
				}
			}
			
//			if (DEBUG_TYPE1_RENDRING && (opr instanceof OpGraphicsType1))
			if (DEBUG_TYPE1_RENEDRING && (opr instanceof OpGraphics))
				System.out.println(" ==> dot at (" + opr.x + "/" + opr.y + ")");
			
//			if (DEBUG_TYPE1_RENDRING && (show || (idd != null) /*|| emptyOp*/ || (idd != null)) && (opr instanceof OpGraphicsType1)) {
			if (DEBUG_TYPE1_RENEDRING && (show || (idd != null) /*|| emptyOp*/ || (idd != null)) && (opr instanceof OpGraphics)) {
//				BufferedImage dImg = new BufferedImage(((OpGraphicsType1) opr).img.getWidth(), ((OpGraphicsType1) opr).img.getHeight(), BufferedImage.TYPE_INT_RGB);
				BufferedImage dImg = new BufferedImage(((OpGraphics) opr).img.getWidth(), ((OpGraphics) opr).img.getHeight(), BufferedImage.TYPE_INT_RGB);
				Graphics g = dImg.getGraphics();
//				g.drawImage(((OpGraphicsType1) opr).img, 0, 0, dImg.getWidth(), dImg.getHeight(), null);
				g.drawImage(((OpGraphics) opr).img, 0, 0, dImg.getWidth(), dImg.getHeight(), null);
				g.setColor(Color.RED);
				g.fillRect(opr.x-2, opr.y-2, 5, 5);
				if (idd == null) {
					idd = new ImageDisplayDialog("Rendering Progress");
					idd.setSize((dImg.getWidth() + 200), (dImg.getHeight() + 100));
				}
				idd.addImage(dImg, ("After " + ops[o].op));
				idd.setLocationRelativeTo(null);
				idd.setVisible(true);
			}
		}
		
		//	only tracking, we're done here
		if (opr instanceof OpTracker)
			return;
		
		//	fill outline
		fillGlyphOutline(((OpGraphics) opr).img, ((OpGraphics) opr).scale, show);
		
		//	scale down image
		int maxHeight = 100;
		((OpGraphics) opr).setImage(scaleImage(((OpGraphics) opr).img, maxHeight));
		
		//	display result for rendering tests
		if (DEBUG_TYPE1_RENEDRING && (show/* || emptyOp*/ || (idd != null))) {
			if (idd != null) {
//				idd.addImage(((OpGraphicsType1) opr).img, "Result");
				idd.addImage(((OpGraphics) opr).img, "Result");
				idd.setLocationRelativeTo(null);
				idd.setVisible(true);
			}
			if (fidd != null) {
//				fidd.addImage(((OpGraphicsType1) opr).img, ("" + sid));
				fidd.addImage(((OpGraphics) opr).img, ("" + sid));
				if (idd == null) {
					fidd.setLocationRelativeTo(null);
					fidd.setVisible(true);
				}
			}
		}
		else if (fidd != null)
			fidd.addImage(((OpGraphics) opr).img, (cc + ": " + sid + " (" + cn + ", '" + cuc + "')"));
	}
	
	private static final void printOp(int op, String opName, int skipped, int a, Number[] args) {
		System.out.print("Executing " + ((opName == null) ? ("" + op + ":") : (opName + " (" + op + "):")));
		for (int i = 0; i < Math.min(skipped, args.length); i++)
			System.out.print(" [" + args[i].intValue() + "]");
		for (int i = skipped; i < Math.min(a, args.length); i++)
			System.out.print(" (" + args[i].intValue() + ")");
		for (int i = a; i < args.length; i++)
			System.out.print(" " + args[i].intValue());
		System.out.println();
	}
	
	private static final int glyphOutlineFillSafetyEdge = 3;
	
	private static void fillGlyphOutline(BufferedImage img, float scale, boolean show) {
		ImageDisplayDialog idd = null;
		
		//	fill outline
		int blackRgb = Color.BLACK.getRGB();
		int whiteRgb = Color.WHITE.getRGB();
		int outsideRgb = Color.GRAY.getRGB();
		int insideRgb = Color.GRAY.darker().getRGB();
		HashSet insideRgbs = new HashSet();
		HashSet outsideRgbs = new HashSet();
		
		//	fill outside
		fill(img, 1, 1, whiteRgb, outsideRgb);
		insideRgbs.add(new Integer(insideRgb));
		insideRgbs.add(new Integer(blackRgb));
		outsideRgbs.add(new Integer(outsideRgb));
		
		//	fill characters outside-in
		outsideRgb = (new Color(outsideRgb)).brighter().getRGB();
		int seekWidth = Math.max(1, (Math.round(scale * 10)));
		boolean gotWhite = true;
		boolean outmostWhiteIsInside = true;
		while (gotWhite) {
			
			if (show) {
				BufferedImage dImg = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
				Graphics g = dImg.getGraphics();
				g.drawImage(img, 0, 0, dImg.getWidth(), dImg.getHeight(), null);
				g.setColor(Color.RED);
				if (idd == null) {
					idd = new ImageDisplayDialog("Rendering Progress");
					idd.setSize((dImg.getWidth() + 200), (dImg.getHeight() + 100));
				}
				idd.addImage(dImg, ("Filling"));
				idd.setLocationRelativeTo(null);
				idd.setVisible(true);
			}
			
			gotWhite = false;
			int fillRgb;
			if (outmostWhiteIsInside) {
				fillRgb = insideRgb;
				insideRgbs.add(new Integer(fillRgb));
			}
			else {
				fillRgb = outsideRgb;
				outsideRgbs.add(new Integer(fillRgb));
			}
//			System.out.println("Fill RGB is " + fillRgb);
			for (int c = seekWidth; c < img.getWidth(); c += seekWidth) {
//				System.out.println("Investigating column " + c);
				int r = 0;
				while ((r < img.getHeight()) && (img.getRGB(c, r) != whiteRgb) && (img.getRGB(c, r) != fillRgb)) {
//					if ((r % 10) == 0) System.out.println(" - ignoring pixel with RGB " + img.getRGB(c, r));
					r++;
				}
//				System.out.println(" - found interesting pixel at row " + r);
				if (r >= img.getHeight()) {
//					System.out.println(" --> bottom of column");
					continue;
				}
//				System.out.println(" - RGB is " + img.getRGB(c, r));
				if (img.getRGB(c, r) == fillRgb) {
//					System.out.println(" --> filled before");
					continue;
				}
				
//				System.out.println(" --> filling at " + c + "/" + r + " with " + fillRgb);
				fill(img, c, r, whiteRgb, fillRgb);
				gotWhite = true;
			}
			
			for (int c = (img.getWidth() - seekWidth); c > 0; c -= seekWidth) {
//				System.out.println("Investigating column " + c);
				int r = (img.getHeight() - 1);
				while ((r >= 0) && (img.getRGB(c, r) != whiteRgb) && (img.getRGB(c, r) != fillRgb)) {
//					if ((r % 10) == 0) System.out.println(" - ignoring pixel with RGB " + img.getRGB(c, r));
					r--;
				}
//				System.out.println(" - found interesting pixel at row " + r);
				if (r < 0) {
//					System.out.println(" --> bottom of column");
					continue;
				}
//				System.out.println(" - RGB is " + img.getRGB(c, r));
				if (img.getRGB(c, r) == fillRgb) {
//					System.out.println(" --> filled before");
					continue;
				}
				
//				System.out.println(" --> filling at " + c + "/" + r + " with " + fillRgb);
				fill(img, c, r, whiteRgb, fillRgb);
				gotWhite = true;
			}
			
			for (int r = seekWidth; r < img.getHeight(); r += seekWidth) {
//				System.out.println("Investigating row " + r);
				int c = 0;
				while ((c < img.getWidth()) && (img.getRGB(c, r) != whiteRgb) && (img.getRGB(c, r) != fillRgb)) {
//					if ((c % 10) == 0) System.out.println(" - ignoring pixel with RGB " + img.getRGB(c, r));
					c++;
				}
//				System.out.println(" - found interesting pixel at column " + r);
				if (c >= img.getWidth()) {
//					System.out.println(" --> right end of row");
					continue;
				}
//				System.out.println(" - RGB is " + img.getRGB(c, r));
				if (img.getRGB(c, r) == fillRgb) {
//					System.out.println(" --> filled before");
					continue;
				}
				
//				System.out.println(" --> filling at " + c + "/" + r + " with " + fillRgb);
				fill(img, c, r, whiteRgb, fillRgb);
				gotWhite = true;
			}
			
			for (int r = (img.getHeight() - seekWidth); r >= 0; r -= seekWidth) {
//				System.out.println("Investigating row " + r);
				int c = (img.getWidth() - 1);
				while ((c >= 0) && (img.getRGB(c, r) != whiteRgb) && (img.getRGB(c, r) != fillRgb)) {
//					if ((c % 10) == 0) System.out.println(" - ignoring pixel with RGB " + img.getRGB(c, r));
					c--;
				}
//				System.out.println(" - found interesting pixel at column " + r);
				if (c < 0) {
//					System.out.println(" --> right end of row");
					continue;
				}
//				System.out.println(" - RGB is " + img.getRGB(c, r));
				if (img.getRGB(c, r) == fillRgb) {
//					System.out.println(" --> filled before");
					continue;
				}
				
//				System.out.println(" --> filling at " + c + "/" + r + " with " + fillRgb);
				fill(img, c, r, whiteRgb, fillRgb);
				gotWhite = true;
			}
			
			if (outmostWhiteIsInside) {
				outmostWhiteIsInside = false;
				insideRgb = (new Color(insideRgb)).darker().getRGB();
//				System.out.println("Inside RGB set to " + insideRgb);
			}
			else {
				outmostWhiteIsInside = true;
				outsideRgb = (new Color(outsideRgb)).brighter().getRGB();
//				System.out.println("Outside RGB set to " + outsideRgb);
			}
		}
		
		//	make it black and white, finally
		for (int c = 0; c < img.getWidth(); c++) {
			for (int r = 0; r < img.getHeight(); r++) {
				int rgb = img.getRGB(c, r);
				if (insideRgbs.contains(new Integer(rgb)))
					img.setRGB(c, r, blackRgb);
				else img.setRGB(c, r, whiteRgb);
			}
		}
	}
	
	private static void fill(BufferedImage img, int x, int y, int toFillRgb, int fillRgb) {
		if ((x < 0) || (x >= img.getWidth()))
			return;
		if ((y < 0) || (y >= img.getHeight()))
			return;
		int rgb = img.getRGB(x, y);
		if (rgb != toFillRgb)
			return;
		img.setRGB(x, y, fillRgb);
//		System.out.println("Filling from " + x + "/" + y + ", boundary is " + boundaryRgb + ", fill is " + fillRgb + ", set is " + img.getRGB(x, y));
		int xlm = x;
		for (int xl = (x-1); xl >= 0; xl--) {
			rgb = img.getRGB(xl, y);
			if (rgb != toFillRgb)
				break;
			img.setRGB(xl, y, fillRgb);
			xlm = xl;
		}
		int xrm = x;
		for (int xr = (x+1); xr < img.getWidth(); xr++) {
			rgb = img.getRGB(xr, y);
			if (rgb != toFillRgb)
				break;
			img.setRGB(xr, y, fillRgb);
			xrm = xr;
		}
		for (int xe = xlm; xe <= xrm; xe++) {
			fill(img, xe, (y - 1), toFillRgb, fillRgb);
			fill(img, xe, (y + 1), toFillRgb, fillRgb);
		}
	}
	
	private static BufferedImage scaleImage(BufferedImage img, int maxHeight) {
//		TODO reactivate this once we have better tracking to splines
//		if (img.getHeight() > maxHeight) {
//			BufferedImage sImg = new BufferedImage(((maxHeight * img.getWidth()) / img.getHeight()), maxHeight, img.getType());
//			sImg.getGraphics().drawImage(img, 0, 0, sImg.getWidth(), sImg.getHeight(), null);
//			return sImg;
//		}
//		else
			return img;
	}
	
	private static int readFontType1cEncoding(byte[] data, int start, HashMap eDict) {
		if (DEBUG_TYPE1C_LOADING) System.out.println("Reading encoding:");
		int i = start;
		int fmt = convertUnsigned(data[i++]);
		if (DEBUG_TYPE1C_LOADING) System.out.println(" - format is " + fmt);
		if (fmt == 0) {
			int nCodes = convertUnsigned(data[i++]);
			if (DEBUG_TYPE1C_LOADING) System.out.println(" - (F0) expecting " + nCodes + " codes");
			for (int c = 1; c <= nCodes; c++) {
				int code = convertUnsigned(data[i++]);
				if (DEBUG_TYPE1C_LOADING) System.out.println(" - (F0) got char " + c + ": " + code);
				eDict.put(new Integer(c), new Integer(code));
			}
		}
		else if (fmt == 1) {
			int nRanges = convertUnsigned(data[i++]);
			if (DEBUG_TYPE1C_LOADING) System.out.println(" - (F1) expecting " + nRanges + " ranges");
			for (int r = 0; r < nRanges; r++) {
				int rStart = convertUnsigned(data[i++]);
				int rSize = convertUnsigned(data[i++]);
				for (int ro = 0; ro < rSize; ro++) {
					int code = (rStart + ro);
					if (DEBUG_TYPE1C_LOADING) System.out.println("   - (F1) got" + ((ro == 0) ? "" : " next") + " char " + (eDict.size()+1) + ": " + code);
					eDict.put(new Integer(eDict.size()+1), new Integer(code));
				}
			}
		}
		return i;
	}
	
	private static int readFontType1cCharset(byte[] data, int start, int charCount, ArrayList content) {
		if (DEBUG_TYPE1C_LOADING) System.out.println("Reading char set:");
		int i = start;
		int fmt = convertUnsigned(data[i++]);
		if (DEBUG_TYPE1C_LOADING) System.out.println(" - format is " + fmt);
		if (fmt == 0) {
			if (content != null)
				content.add(new Integer(0));
			for (int c = 1; c < charCount; c++) {
				int sid = ((convertUnsigned(data[i++]) * 256) + convertUnsigned(data[i++]));
				if (content != null)
					content.add(new Integer(sid));
				if (DEBUG_TYPE1C_LOADING) System.out.println(" - (F0) got char " + c + ": " + sid);
			}
		}
		else if (fmt == 1) {
			int toCome = charCount;
			while (toCome > 0) {
				int sid = ((convertUnsigned(data[i++]) * 256) + convertUnsigned(data[i++]));
				toCome--;
				if (content != null)
					content.add(new Integer(sid));
				if (DEBUG_TYPE1C_LOADING) System.out.println(" - (F1) got char " + content.size() + ": " + sid);
				int toComeInRange = convertUnsigned(data[i++]);
				if (DEBUG_TYPE1C_LOADING) System.out.println("   - " + toComeInRange + " more in range, " + toCome + " in total:");
				for (int c = 0; (c < toComeInRange) && (toCome > 0); c++) {
					sid++;
					toCome--;
					if (content != null)
						content.add(new Integer(sid));
					if (DEBUG_TYPE1C_LOADING) System.out.println("   - (F1) got next char " + content.size() + ": " + sid);
				}
			}
		}
		else if (fmt == 2) {
			int toCome = charCount;
			while (toCome > 0) {
				int sid = ((convertUnsigned(data[i++]) * 256) + convertUnsigned(data[i++]));
				toCome--;
				if (content != null)
					content.add(new Integer(sid));
				if (DEBUG_TYPE1C_LOADING) System.out.println(" - (F2) got char " + content.size() + ": " + sid);
				int toComeInRange = ((convertUnsigned(data[i++]) * 256) + convertUnsigned(data[i++]));
				if (DEBUG_TYPE1C_LOADING) System.out.println("   - " + toComeInRange + " more in range, " + toCome + " in total:");
				for (int c = 0; (c < toComeInRange) && (toCome > 0); c++) {
					sid++;
					toCome--;
					if (content != null)
						content.add(new Integer(sid));
					if (DEBUG_TYPE1C_LOADING) System.out.println("   - (F2) got char " + content.size() + ": " + sid);
				}
			}
		}
		return i;
	}
	
	private static int readFontType1cIndex(byte[] data, int start, String name, ArrayList subrIndex, ArrayList gSubrIndex, HashMap dictEntries, HashMap dictOpResolver, ArrayList content) {
		int i = start;
		if (DEBUG_TYPE1C_LOADING) System.out.println("Doing " + name + " index:");
		int count = (256 * convertUnsigned(data[i++]) + convertUnsigned(data[i++]));
		if (DEBUG_TYPE1C_LOADING) System.out.println(" - count is " + count);
		if (dictEntries != null) {
			Number[] cnt = {new Integer(count)};
			dictEntries.put("Count", cnt);
		}
		if (count == 0)
			return i;
		int offSize = convertUnsigned(data[i++]);
		if (DEBUG_TYPE1C_LOADING) System.out.println(" - offset size is " + offSize);
		int[] offsets = new int[count+1];
		for (int c = 0; c <= count; c++) {
			offsets[c] = 0;
			for (int b = 0; b < offSize; b++)
				offsets[c] = ((offsets[c] * 256) + convertUnsigned(data[i++]));
			if (DEBUG_TYPE1C_LOADING) System.out.println(" - offset[" + c + "] is " + offsets[c]);
		}
		for (int c = 0; c < count; c++) {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			for (int b = offsets[c]; b < offsets[c+1]; b++)
				baos.write(convertUnsigned(data[i++]));
			if ((dictOpResolver == type1cGlyphProgOpResolver) && (gSubrIndex == null) && (content != null)) {
				content.add(baos.toByteArray());
				if (dictEntries != null)
					dictEntries.put(Integer.valueOf(c), baos.toByteArray());
			}
			else if ((dictOpResolver != null) && (dictEntries != null)) {
				ArrayList dictContent = ((content == null) ? null : new ArrayList());
				readFontType1cDict(baos.toByteArray(), 0, baos.size(), (name + "-entry[" + c + "]"), subrIndex, gSubrIndex, dictEntries, dictOpResolver, dictContent);
				if (content != null)
					content.add((OpType1[]) dictContent.toArray(new OpType1[dictContent.size()]));
			}
			else if (content != null)
				content.add(new String(baos.toByteArray()));
			else if (DEBUG_TYPE1C_LOADING) System.out.println(" - entry[" + c + "]: " + new String(baos.toByteArray()));
		}
		return i;
	}
	
	private static int readFontType1cDict(byte[] data, int start, int end, String name, ArrayList subrIndex, ArrayList gSubrIndex, HashMap dictEntries, HashMap opResolver, ArrayList content) {
		return readFontType1cDict(data, start, end, name, subrIndex, gSubrIndex, new LinkedList(), false, "", new HashSet(), new HashMap(), dictEntries, opResolver, content);
	}
	
	private static int readFontType1cDict(byte[] data, int start, int end, String name, ArrayList subrIndex, ArrayList gSubrIndex, LinkedList stack, boolean inSubr, String indent, HashSet hints, HashMap storage, HashMap dictEntries, HashMap opResolver, ArrayList content) {
//		System.out.println("Doing " + name + " dict (from " + start + " of " + end + "):");
//		System.out.println("                        " + Arrays.toString(Arrays.copyOfRange(data, start, end)));
		int i = start;
		while (i < Math.min(end, data.length))  {
			
			//	read value
			int bs = convertUnsigned(data[i++]);
//			System.out.println(" - first byte is " + bs + " (" + data[i-1] + ") at " + (i-1) + " of " + data.length);
			int op = Integer.MIN_VALUE;
			int iVal = Integer.MIN_VALUE;
			double dVal = Double.NEGATIVE_INFINITY;
			
			if ((0 <= bs) && (bs <= 11)) {
				if (opResolver.containsKey(Integer.valueOf(bs)))
					op = bs;
//				else iVal = bs;
			}
			else if (bs == 12) {
				if (i >= data.length)
					break;
				bs = (1000 + convertUnsigned(data[i++]));
				if (opResolver.containsKey(Integer.valueOf(bs)))
					op = bs;
			}
			else if ((13 <= bs) && (bs <= 18)) {
				if (opResolver.containsKey(Integer.valueOf(bs)))
					op = bs;
//				else iVal = bs;
			}
			else if (bs == 19) {
				if (opResolver.containsKey(Integer.valueOf(bs)))
					op = bs;
//				else iVal = bs;
			}
			else if (bs == 20) {
				if (opResolver.containsKey(Integer.valueOf(bs)))
					op = bs;
//				else iVal = bs;
			}
			else if ((21 <= bs) && (bs <= 27)) {
				if (opResolver.containsKey(Integer.valueOf(bs)))
					op = bs;
//				else iVal = bs;
			}
			else if (bs == 28) {
				if ((i + 1) >= data.length)
					break;
				iVal = 0;
				for (int b = 0; b < 2; b++) {
					iVal <<= 8;
					iVal += convertUnsigned(data[i++]);
				}
				if (iVal > 32768)
					iVal -= 65536;
			}
			else if ((opResolver == type1cGlyphProgOpResolver) && (bs == 29)) {
				if (opResolver.containsKey(Integer.valueOf(bs)))
					op = 29;
			}
			else if (bs == 29) {
				if ((i + 4) >= data.length)
					break;
				iVal = 0;
				for (int b = 0; b < 4; b++) {
					iVal <<= 8;
					iVal += convertUnsigned(data[i++]);
				}
			}
			else if ((opResolver == type1cTopDictOpResolver) && (bs == 30)) {
				if (i >= data.length)
					break;
				StringBuffer val = new StringBuffer();
				while (true) {
					int b = convertUnsigned(data[i++]);
					int hq = (b & 240) >>> 4;
					int lq = (b & 15);
					
					if (hq < 10)
						val.append("" + hq);
					else if (hq == 10)
						val.append(".");
					else if (hq == 11)
						val.append("E");
					else if (hq == 12)
						val.append("E-");
					else if (hq == 13)
						val.append("");
					else if (hq == 14)
						val.append("-");
					else if (hq == 15)
						break;
					
					if (lq < 10)
						val.append("" + lq);
					else if (lq == 10)
						val.append(".");
					else if (lq == 11)
						val.append("E");
					else if (lq == 12)
						val.append("E-");
					else if (lq == 13)
						val.append("");
					else if (lq == 14)
						val.append("-");
					else if (lq == 15)
						break;
				}
				dVal = Double.parseDouble(val.toString());
			}
			else if (bs == 30) {
				if (opResolver.containsKey(Integer.valueOf(bs)))
					op = bs;
			}
			else if (bs == 31) {
				if (opResolver.containsKey(Integer.valueOf(bs)))
					op = bs;
			}
			else if ((32 <= bs) && (bs <= 246)) {
				iVal = (bs - 139);
			}
			else if ((247 <= bs) && (bs <= 250)) {
				if (i >= data.length)
					break;
				int b1 = bs;
				int b2 = convertUnsigned(data[i++]);
				iVal = ((b1 - 247) * 256) + b2 + 108;
			}
			else if ((251 <= bs) && (bs <= 254)) {
				if (i >= data.length)
					break;
				int b1 = bs;
				int b2 = convertUnsigned(data[i++]);
				iVal = -((b1 - 251) * 256) - b2 - 108;
			}
			else if (bs == 255) {
				if ((i + 4) >= data.length)
					break;
				int ib1 = convertUnsigned(data[i++]);
				int ib2 = convertUnsigned(data[i++]);
				int iv = ((ib1 * 256) + ib2);
				int fb1 = convertUnsigned(data[i++]);
				int fb2 = convertUnsigned(data[i++]);
				int fv = ((fb1 * 256) + fb2);
				dVal = ((iv << 16) + fv);
				dVal /= 65536;
			}
			
			if (op != Integer.MIN_VALUE) {
				String opStr = ((String) opResolver.get(Integer.valueOf(op)));
//				System.out.println(" --> read operator " + op + " (" + opStr + ")");
//				System.out.println("     " + stack);
				
				//	catch glyph rendering operators
				if ((content != null) && (opResolver == type1cGlyphProgOpResolver)) {
					
					//	catch hint and mask operators, as the latter take a few _subsequent_ bytes depending on the number of arguments existing for the former
					if ((op == 19) || (op == 20)) {
						
						//	if last op is hstemhm and we have something on the stack, it's an implicit vstemhm (observe plain hstem as well for good measure)
						if ((content.size() != 0) && (2 <= stack.size())) {
							OpType1 pervOp = ((OpType1) content.get(content.size()-1));
							if ((content.size() != 0) && ((pervOp.op == 1) || (pervOp.op == 18))) {
								content.add(new OpType1(23, "vstemhm", ((Number[]) stack.toArray(new Number[stack.size()])), false, indent));
								if ((stack.size() % 2) != 0)
									stack.removeFirst(); // width is dealt with later
								int x = ((Number) stack.removeFirst()).intValue();
								int dx = ((Number) stack.removeFirst()).intValue();
								hints.add("V " + x + "-" + (x + dx));
								while (2 <= stack.size()) {
									x = (x + dx + ((Number) stack.removeFirst()).intValue());
									dx = ((Number) stack.removeFirst()).intValue();
									hints.add("V " + x + "-" + (x + dx));
								}
							}
						}
						
						//	read mask bytes
						int h = 0;
						int hintmask = 0;
						while (h < ((hints.size() + 7) / 8)) {
							hintmask <<= 8;
							hintmask += convertUnsigned(data[i++]);
							h++;
						}
						stack.addLast(new Integer(hintmask));
						content.add(new OpType1(op, opStr, ((Number[]) stack.toArray(new Number[stack.size()])), false, indent));
						stack.clear();
//						System.out.println("Skipped " + h + " hint mask bytes: " + hintmask + " (" + Integer.toString(hintmask, 16).toUpperCase() + ", for " + hints.size() + " hints: " + hints.toString() + ")");
					}
					
					//	hstem & hstemhm (hints are number pairs !!, hstem3 doesn't seem to exist in Type1C, but we catch it anyway)
					else if ((op == 1) || (op == 18) || (op == 1002)) {
						content.add(new OpType1(op, opStr, ((Number[]) stack.toArray(new Number[stack.size()])), false, indent));
						try {
							if ((stack.size() % 2) != 0)
								stack.removeFirst(); // width is dealt with later
							int y = ((Number) stack.removeFirst()).intValue();
							int dy = ((Number) stack.removeFirst()).intValue();
							hints.add("H " + y + "-" + (y + dy));
							while (stack.size() != 0) {
								y = (y + dy + ((Number) stack.removeFirst()).intValue());
								dy = ((Number) stack.removeFirst()).intValue();
								hints.add("H " + y + "-" + (y + dy));
							}
						}
						catch (RuntimeException re) {
							System.out.println(indent + "Ignoring ill-parameterized hstem hint: " + re.getMessage());
							re.printStackTrace(System.out);
							stack.clear();
						}
					}
					
					//	vstem & vstemhm (hints are number pairs !!, vstem3 doesn't seem to exist in Type1C, but we catch it anyway)
					else if ((op == 3) || (op == 23) || (op == 1001)) {
						content.add(new OpType1(op, opStr, ((Number[]) stack.toArray(new Number[stack.size()])), false, indent));
						try {
							if ((stack.size() % 2) != 0)
								stack.removeFirst(); // width is dealt with later
							int x = ((Number) stack.removeFirst()).intValue();
							int dx = ((Number) stack.removeFirst()).intValue();
							hints.add("V " + x + "-" + (x + dx));
							while (stack.size() != 0) {
								x = (x + dx + ((Number) stack.removeFirst()).intValue());
								dx = ((Number) stack.removeFirst()).intValue();
								hints.add("V " + x + "-" + (x + dx));
							}
						}
						catch (RuntimeException re) {
							System.out.println(indent + "Ignoring ill-parameterized vstem hint: " + re.getMessage());
							re.printStackTrace(System.out);
							stack.clear();
						}
					}
					
					//	TODO execute arithmetics right in place
					//	abs: num abs (12 9) num2
					//	returns the absolute value of num.
					else if (op == 1009) {
						Number num = ((Number) stack.removeLast());
						if (num instanceof Integer)
							stack.addLast(Integer.valueOf(Math.abs(num.intValue())));
						else stack.addLast(Double.valueOf(Math.abs(num.doubleValue())));
					}
					
					//	add: num1 num2 add (12 10) sum
					//	returns the sum of the two numbers num1 and num2.
					else if (op == 1010) {
						Number num2 = ((Number) stack.removeLast());
						Number num1 = ((Number) stack.removeLast());
						if ((num1 instanceof Integer) && (num2 instanceof Integer))
							stack.addLast(Integer.valueOf(num1.intValue() + num2.intValue()));
						else stack.addLast(Double.valueOf(num1.doubleValue() + num2.doubleValue()));
					}
					
					//	sub: num1 num2 sub (12 11) difference
					//	returns the result of subtracting num2 from num1.
					else if (op == 1011) {
						Number sub = ((Number) stack.removeLast());
						Number min = ((Number) stack.removeLast());
						if ((min instanceof Integer) && (sub instanceof Integer))
							stack.addLast(Integer.valueOf(min.intValue() - sub.intValue()));
						else stack.addLast(Double.valueOf(min.doubleValue() - sub.doubleValue()));
					}
					
					//	div: num1 num2 div (12 12) quotient
					//	returns the quotient of num1 divided by num2. The result is undefined if overflow occurs and is zero for underflow.
					else if (op == 1012) {
						double denom = ((Number) stack.removeLast()).doubleValue();
						double nom = ((Number) stack.removeLast()).doubleValue();
						stack.addLast(Double.valueOf(nom / denom));
					}
					
					//	neg: num neg (12 14) num2
					//	returns the negative of num.
					else if (op == 1014) {
						Number num = ((Number) stack.removeLast());
						if (num instanceof Integer)
							stack.addLast(Integer.valueOf(-num.intValue()));
						else stack.addLast(Double.valueOf(-num.doubleValue()));
					}
					
					//	random: random (12 23) num2
					//	returns a pseudo random number num2 in the range (0,1], that is, greater than zero and less than or equal to one.
					else if (op == 1023) {
						stack.addLast(Double.valueOf(1 - Math.random())); // need to invert interval boundaries
					}
					
					//	mul: num1 num2 mul (12 24) product
					//	returns the product of num1 and num2. If overflow occurs, the result is undefined, and zero is returned for underflow.
					else if (op == 1024) {
						Number num2 = ((Number) stack.removeLast());
						Number num1 = ((Number) stack.removeLast());
						if ((num1 instanceof Integer) && (num2 instanceof Integer))
							stack.addLast(Integer.valueOf(num1.intValue() * num2.intValue()));
						else stack.addLast(Double.valueOf(num1.doubleValue() * num2.doubleValue()));
					}
					
					//	sqrt: num sqrt (12 26) num2
					//	returns the square root of num. If num is negative, the result is undefined.
					else if (op == 1026) {
						double num = ((Number) stack.removeLast()).doubleValue();
						stack.addLast(Double.valueOf((num < 0) ? 0 : Math.sqrt(num)));
					}
					
					//	drop: num drop (12 18)
					//	removes the top element num from the Type 2 argument stack.
					else if (op == 1018) {
						stack.removeLast();
					}
					
					//	exch: num1 num2 exch (12 28) num2 num1
					//	exchanges the top two elements on the argument stack.
					else if (op == 1028) {
						Number num2 = ((Number) stack.removeLast());
						Number num1 = ((Number) stack.removeLast());
						stack.addLast(num2);
						stack.addLast(num1);
					}
					
					//	index: numX ... num0 i index (12 29) numX ... num0 numi
					//	retrieves the element i from the top of the argument stack and pushes a copy of that element onto that stack. If i is negative, the top element is copied. If i is greater than X, the operation is undefined.
					else if (op == 1029) {
						int si = ((Number) stack.removeLast()).intValue();
						if (si < 0)
							stack.addLast(stack.getLast());
						else stack.addLast(stack.get((stack.size() - 1) /* index of last element */ - si));
					}
					
					//	roll: num(N–1) ... num0 N J roll (12 30) num((J–1) mod N) ... num0
					//	num(N–1) ... num(J mod N) performs a circular shift of the elements num(N–1) ... num0 on the argument stack by the amount J. Positive J indicates upward motion of the stack; negative J indicates downward motion.
					//	The value N must be a non-negative integer, otherwise the operation is undefined.
					else if (op == 1030) {
						int j = ((Number) stack.removeLast()).intValue();
						int n = ((Number) stack.removeLast()).intValue();
						if ((j != 0) /* save the hassle of zero rolls */ && (n > 0) /* make sure we got something to roll */) {
							LinkedList rStack = new LinkedList();
							while (rStack.size() < n)
								rStack.addFirst(stack.removeLast());
							if (j < 0) {
								for (; j < 0; j++)
									rStack.addFirst(rStack.removeLast()); // move top of stack down
							}
							else if (j > 0) {
								for (; j > 0; j--)
									rStack.addLast(rStack.removeFirst()); // move down in stack up
							}
							while (rStack.size() != 0)
								stack.addLast(rStack.removeFirst());
						}
					}
					
					//	dup: any dup (12 27) any any
					//	duplicates the top element on the argument stack.
					else if (op == 1027) {
						stack.addLast(stack.getLast());
					}
					
					//	and: num1 num2 and (12 3) 1_or_0
					//	puts a 1 on the stack if num1 and num2 are both non-zero, and puts a 0 on the stack if either argument is zero.
					else if (op == 1003) {
						Number num2 = ((Number) stack.removeLast());
						Number num1 = ((Number) stack.removeLast());
						stack.addLast(Integer.valueOf(((num1.intValue() != 0) && (num2.intValue() != 0)) ? 1 : 0));
					}
					
					//	or: num1 num2 or (12 4) 1_or_0
					//	puts a 1 on the stack if either num1 or num2 are non-zero, and puts a 0 on the stack if both arguments are zero.
					else if (op == 1004) {
						Number num2 = ((Number) stack.removeLast());
						Number num1 = ((Number) stack.removeLast());
						stack.addLast(Integer.valueOf(((num1.intValue() != 0) || (num2.intValue() != 0)) ? 1 : 0));
					}
					
					//	not: num1 not (12 5) 1_or_0
					//	returns a 0 if num1 is non-zero; returns a 1 if num1 is zero.
					else if (op == 1005) {
						Number num1 = ((Number) stack.removeLast());
						stack.addLast(Integer.valueOf((num1.intValue() == 0) ? 1 : 0));
					}
					
					//	eq: num1 num2 eq (12 15) 1_or_0
					//	puts a 1 on the stack if num1 equals num2, otherwise a 0 (zero) is put on the stack.
					else if (op == 1015) {
						Number num2 = ((Number) stack.removeLast());
						Number num1 = ((Number) stack.removeLast());
						stack.addLast(Integer.valueOf(num1.equals(num2) ? 1 : 0));
					}
					
					//	ifelse: s1 s2 v1 v2 ifelse (12 22) s1_or_s2
					//	leaves the value s1 on the stack if v1 <= v2, or leaves s2 on the stack if v1 > v2. The value of s1 and s2 is usually the biased number of a subroutine; see section 2.3.
					else if (op == 1022) {
						double v2 = ((Number) stack.removeLast()).doubleValue();
						double v1 = ((Number) stack.removeLast()).doubleValue();
						Number s2 = ((Number) stack.removeLast());
						Number s1 = ((Number) stack.removeLast());
						stack.addLast((v1 <= v2) ? s1 : s2);
					}
					
					//	put: val i put (12 20)
					//	stores val into the transient array at the location given by i.
					else if (op == 1012) {
						Integer si = Integer.valueOf(((Number) stack.removeLast()).intValue());
						Number sVal = ((Number) stack.removeLast());
						storage.put(si, sVal);
					}
					
					//	get: i get (12 21) val
					//	retrieves the value stored in the transient array at the location given by i and pushes the value onto the argument stack. If get is executed prior to put for i during execution of the current charstring, the value returned is undefined.
					else if (op == 1021) {
						Integer si = Integer.valueOf(((Number) stack.removeLast()).intValue());
						Number sVal = ((Number) storage.get(si));
						stack.addLast((sVal == null) ? Integer.valueOf(0) : sVal);
					}
					
					//	inline subroutine calls right here
					else if ((op == 10) && (subrIndex != null)) {
						int subrNr = ((Number) stack.removeLast()).intValue();
						byte[] subrData = null;
						try {
							subrData = ((byte[]) subrIndex.get(subrNr));
							if (DEBUG_TYPE1C_LOADING && (subrData == null)) System.out.println(indent + "Ignoring attempt to call invalid subroutine " + subrNr);
						}
						catch (RuntimeException re) {
							re.printStackTrace(System.out);
							if (DEBUG_TYPE1C_LOADING) System.out.println(indent + "Ignoring attempt to call invalid subroutine " + subrNr);
//							subrData = ((byte[]) gSubrIndex.get(subrNr));
//							if (DEBUG_TYPE1C_LOADING && (subrData == null)) System.out.println("Ignoring attempt to call invalid subroutine " + subrNr);
						}
						if (subrData != null) {
//							LinkedList preCallStack = new LinkedList(stack);
							//	TODO catch errors like in Type1
							if (DEBUG_TYPE1C_LOADING) {
								System.out.println(indent + "Calling subroutine " + subrNr + ": " + Arrays.toString(subrData));
								System.out.println(indent + "  stack is         " + stack);
							}
							readFontType1cDict(subrData, 0, subrData.length, (name + "->" + subrNr), subrIndex, gSubrIndex, stack, true, (indent + "  "), hints, storage, dictEntries, opResolver, content);
							if (DEBUG_TYPE1C_LOADING) System.out.println(indent + "  result stack is " + stack);
							if ((stack.size() != 0) && "ENDCHAR".equals(stack.getLast()))
								break;
						}
					}
					
					//	inline global subroutine calls right here
					else if ((op == 29) && (gSubrIndex != null)) {
						int gSubrNr = ((Number) stack.removeLast()).intValue();
						byte[] gSubrData = ((byte[]) gSubrIndex.get(gSubrNr));
						if (gSubrData == null) {
							if (DEBUG_TYPE1C_LOADING) System.out.println(indent + "Ignoring attempt to call invalid global subroutine " + gSubrNr);
						}
						else {
//							LinkedList preCallStack = new LinkedList(stack);
							//	TODO catch errors like in Type1
							if (DEBUG_TYPE1C_LOADING) {
								System.out.println(indent + "Calling global subroutine " + gSubrNr + ": " + Arrays.toString(gSubrData));
								System.out.println(indent + "  stack is                " + stack);
							}
							readFontType1cDict(gSubrData, 0, gSubrData.length, (name + "->" + gSubrNr), subrIndex, gSubrIndex, stack, true, (indent + "  "), hints, storage, dictEntries, opResolver, content);
							if (DEBUG_TYPE1C_LOADING) System.out.println(indent + "  result stack is " + stack);
							if ((stack.size() != 0) && "ENDCHAR".equals(stack.getLast()))
								break;
						}
					}
					
					//	observe return in subroutine
					else if (op == 11) {
						if (inSubr)
							break;
					}
					
					//	observe endchar (implicitly performing closepath if not done right before)
					else if (op == 14) {
						stack.clear();
						stack.addLast("ENDCHAR"); // add mark to propagate endchar from subroutine
						if ((content.size() == 0) || (((OpType1) content.get(content.size()-1)).op != 9))
							content.add(new OpType1(9, "closepath", new Number[0], false, indent));
						break;
					}
					
					//	store rendering operators (they are all stack clearing)
					else {
						content.add(new OpType1(op, opStr, ((Number[]) stack.toArray(new Number[stack.size()])), false, indent));
						stack.clear();
					}
					
					//	no further action required
					continue;
				}
				
				//	catch private dictionary operators (we need them subroutines ...)
				else if (opResolver == type1cPrivateOpResolver) {
					
					//	operators that take stack clearing deltas
					if ((op == 6) || (op == 7) || (op == 8) || (op == 9) || (op == 1012) || (op == 1013)) {
//						content.add(new OpType1(op, opStr, ((Number[]) stack.toArray(new Number[stack.size()]))));
						if (opStr != null)
							dictEntries.put(opStr, ((Number[]) stack.toArray(new Number[stack.size()])));
						stack.clear();
					}
					
					//	operators that take a single argument (number or boolean)
					else if ((op == 1009) || (op == 1010) || (op == 1011) || (op == 10) || (op == 11) || (op == 1014) || (op == 1017) || (op == 1018) || (op == 1019) || (op == 20) || (op == 21)) {
						Number[] arg = {(Number) stack.removeLast()};
//						content.add(new OpType1(op, opStr, arg));
						if (opStr != null)
							dictEntries.put(opStr, arg);
					}
					
					//	subroutine declarations
					else if (op == 19) {
						Number[] arg = {(Number) stack.removeLast()};
//						content.add(new OpType1(op, opStr, arg));
						if (opStr != null)
							dictEntries.put(opStr, arg);
					}
					
					//	no further action required
					continue;
				}
				
				if (opStr != null)
					dictEntries.put(opStr, ((Number[]) stack.toArray(new Number[stack.size()])));
				if (content != null)
					content.add(new OpType1(op, opStr, ((Number[]) stack.toArray(new Number[stack.size()])), false, indent));
//				while (stack.size() != 0)
//					System.out.print(" " + ((Number) stack.removeFirst()));
//				if (DEBUG_TYPE1C_LOADING) System.out.println();
				stack.clear();
			}
			else if (iVal != Integer.MIN_VALUE) {
//				System.out.println(" --> read int " + iVal);
				stack.addLast(new Integer(iVal));
			}
			else if (dVal != Double.NEGATIVE_INFINITY) {
//				System.out.println(" --> read double " + dVal);
				stack.addLast(new Double(dVal));
			}
			else {
//				System.out.println(" --> read invalid byte at " + (i-1) + ": " + bs);
//				System.out.println("     " + stack);
			}
		}
		
		return i;
	}
	
	private static class OpType1 {
		final int op;
		final String name;
		final Number[] args;
		final boolean fixedArgs;
		OpType1(int op, String name, Number[] args, boolean fixedArgs, String indent) {
			this.op = op;
			this.name = name;
			this.args = args;
			this.fixedArgs = fixedArgs;
			if (DEBUG_TYPE1_RENEDRING)
				System.out.println(indent + "OpType1-" + op + ": '" + name + "' " + Arrays.toString(args));
		}
	}
	
	private static HashMap type1cTopDictOpResolver = new HashMap();
	static {
		type1cTopDictOpResolver.put(new Integer(0), "Version");
		type1cTopDictOpResolver.put(new Integer(1), "Notice");
		type1cTopDictOpResolver.put(new Integer(2), "FullName");
		type1cTopDictOpResolver.put(new Integer(3), "FamilyName");
		type1cTopDictOpResolver.put(new Integer(4), "Weight");
		type1cTopDictOpResolver.put(new Integer(5), "FontBBox");
		type1cTopDictOpResolver.put(new Integer(13), "UniqueID");
		type1cTopDictOpResolver.put(new Integer(14), "XUID");
		type1cTopDictOpResolver.put(new Integer(15), "Charset");
		type1cTopDictOpResolver.put(new Integer(16), "Encoding");
		type1cTopDictOpResolver.put(new Integer(17), "CharStrings");
		type1cTopDictOpResolver.put(new Integer(18), "Private");
		
		type1cTopDictOpResolver.put(new Integer(1000), "Copyright");
		type1cTopDictOpResolver.put(new Integer(1001), "IsFixedPitch");
		type1cTopDictOpResolver.put(new Integer(1002), "ItalicAngle");
		type1cTopDictOpResolver.put(new Integer(1003), "UnderlinePosition");
		type1cTopDictOpResolver.put(new Integer(1004), "UnderlineThickness");
		type1cTopDictOpResolver.put(new Integer(1005), "PaintType");
		type1cTopDictOpResolver.put(new Integer(1006), "CharstringType");
		type1cTopDictOpResolver.put(new Integer(1007), "FontMatrix");
		type1cTopDictOpResolver.put(new Integer(1008), "StrokeWidth");
		type1cTopDictOpResolver.put(new Integer(1020), "SyntheticBase");
		type1cTopDictOpResolver.put(new Integer(1021), "PostScript");
		type1cTopDictOpResolver.put(new Integer(1022), "BaseFontName");
		type1cTopDictOpResolver.put(new Integer(1023), "BaseFondBlend");
		
		type1cTopDictOpResolver.put(new Integer(1030), "ROS");
		type1cTopDictOpResolver.put(new Integer(1031), "CIDFontVersion");
		type1cTopDictOpResolver.put(new Integer(1032), "CIDFontRevision");
		type1cTopDictOpResolver.put(new Integer(1033), "CIDFontType");
		type1cTopDictOpResolver.put(new Integer(1034), "CIDCount");
		type1cTopDictOpResolver.put(new Integer(1035), "UIDBase");
		type1cTopDictOpResolver.put(new Integer(1036), "FDArray");
		type1cTopDictOpResolver.put(new Integer(1037), "FDSelect");
		type1cTopDictOpResolver.put(new Integer(1038), "FontName");
	}
	
	/*
ROS12 30//SID SID number–, Registry Ordering Supplement
CIDFontVersion12 31//number0
CIDFontRevision12 32//number0
CIDFontType12 33//number0
CIDCount12 34//number8720
UIDBase12 35//number–
FDArray12 36//number–, Font DICT (FD) INDEX offset (0)
FDSelect12 37//number–, FDSelect offset (0)
FontName12 38//SID–, FD FontName
	 */
	
	private static HashMap type1cPrivateOpResolver = new HashMap();
	static {
		type1cPrivateOpResolver.put(new Integer(6), "BlueValues");
		type1cPrivateOpResolver.put(new Integer(7), "OtherBlues");
		type1cPrivateOpResolver.put(new Integer(8), "FamilyBlues");
		type1cPrivateOpResolver.put(new Integer(9), "FamilyOtherBlues");
		type1cPrivateOpResolver.put(new Integer(1009), "BlueScale");
		type1cPrivateOpResolver.put(new Integer(1010), "BlueSchift");
		type1cPrivateOpResolver.put(new Integer(1011), "BlueFuzz");
		type1cPrivateOpResolver.put(new Integer(10), "StdHW");
		type1cPrivateOpResolver.put(new Integer(11), "StdVW");
		type1cPrivateOpResolver.put(new Integer(1012), "StemSnapH");
		type1cPrivateOpResolver.put(new Integer(1013), "StemSnapV");
		type1cPrivateOpResolver.put(new Integer(1014), "ForceBold");
		type1cPrivateOpResolver.put(new Integer(1017), "LanguageGroup");
		type1cPrivateOpResolver.put(new Integer(1018), "ExpansionFactor");
		type1cPrivateOpResolver.put(new Integer(1019), "InitialRandomSeed");
		type1cPrivateOpResolver.put(new Integer(19), "Subrs");
		type1cPrivateOpResolver.put(new Integer(20), "defaultWidthX");
		type1cPrivateOpResolver.put(new Integer(21), "nominalWidthX");
	}
	
	private static HashMap type1cGlyphProgOpResolver = new HashMap();
	static {
		type1cGlyphProgOpResolver.put(new Integer(1), "hstem");
		
		type1cGlyphProgOpResolver.put(new Integer(3), "vstem");
		type1cGlyphProgOpResolver.put(new Integer(4), "vmoveto");
		type1cGlyphProgOpResolver.put(new Integer(5), "rlineto");
		type1cGlyphProgOpResolver.put(new Integer(6), "hlineto");
		type1cGlyphProgOpResolver.put(new Integer(7), "vlineto");
		type1cGlyphProgOpResolver.put(new Integer(8), "rrcurveto");
		type1cGlyphProgOpResolver.put(new Integer(9), "closepath"); // deprecated, but might still be used
		type1cGlyphProgOpResolver.put(new Integer(10), "callsubr");
		type1cGlyphProgOpResolver.put(new Integer(11), "return");
	
		type1cGlyphProgOpResolver.put(new Integer(14), "endchar");
		
		type1cGlyphProgOpResolver.put(new Integer(18), "hstemhm");
		type1cGlyphProgOpResolver.put(new Integer(19), "hintmask");
		type1cGlyphProgOpResolver.put(new Integer(20), "cntrmask");
		type1cGlyphProgOpResolver.put(new Integer(21), "rmoveto");
		type1cGlyphProgOpResolver.put(new Integer(22), "hmoveto");
		type1cGlyphProgOpResolver.put(new Integer(23), "vstemhm");
		type1cGlyphProgOpResolver.put(new Integer(24), "rcurveline");
		type1cGlyphProgOpResolver.put(new Integer(25), "rlinecurve");
		type1cGlyphProgOpResolver.put(new Integer(26), "vvcurveto");
		type1cGlyphProgOpResolver.put(new Integer(27), "hhcurveto");
		
		type1cGlyphProgOpResolver.put(new Integer(29), "callsubr");
		type1cGlyphProgOpResolver.put(new Integer(30), "vhcurveto");
		type1cGlyphProgOpResolver.put(new Integer(31), "hvcurveto");
		
		type1cGlyphProgOpResolver.put(new Integer(1000), "dotsection"); // deprecated, but might still be used
		
		type1cGlyphProgOpResolver.put(new Integer(1003), "and");
		type1cGlyphProgOpResolver.put(new Integer(1004), "or");
		type1cGlyphProgOpResolver.put(new Integer(1005), "not");
		
		type1cGlyphProgOpResolver.put(new Integer(1009), "abs");
		type1cGlyphProgOpResolver.put(new Integer(1010), "add");
		type1cGlyphProgOpResolver.put(new Integer(1011), "sub");
		type1cGlyphProgOpResolver.put(new Integer(1012), "div");
		type1cGlyphProgOpResolver.put(new Integer(1014), "neg");
		type1cGlyphProgOpResolver.put(new Integer(1015), "eq");
		
		type1cGlyphProgOpResolver.put(new Integer(1018), "drop");
		
		type1cGlyphProgOpResolver.put(new Integer(1020), "put");
		type1cGlyphProgOpResolver.put(new Integer(1021), "get");
		type1cGlyphProgOpResolver.put(new Integer(1022), "ifelse");
		type1cGlyphProgOpResolver.put(new Integer(1023), "random");
		type1cGlyphProgOpResolver.put(new Integer(1024), "mul");
		
		type1cGlyphProgOpResolver.put(new Integer(1026), "sqrt");
		type1cGlyphProgOpResolver.put(new Integer(1027), "dup");
		type1cGlyphProgOpResolver.put(new Integer(1028), "exch");
		type1cGlyphProgOpResolver.put(new Integer(1029), "index");
		type1cGlyphProgOpResolver.put(new Integer(1030), "roll");
		
		type1cGlyphProgOpResolver.put(new Integer(1034), "hflex");
		type1cGlyphProgOpResolver.put(new Integer(1035), "flex");
		type1cGlyphProgOpResolver.put(new Integer(1036), "hflex1");
		type1cGlyphProgOpResolver.put(new Integer(1037), "flex1");
	}
	
	private static final boolean DEBUG_TRUE_TYPE_LOADING = false;
	
	static void readFontTrueType(byte[] data, PdfFont pFont, CharDecoder pCharDecoder, boolean dataFromBaseFont, final HashMap glyphIdsToCids, FontDecoderCharset charSet, ProgressMonitor pm) {
		if (DEBUG_TRUE_TYPE_LOADING) {
			System.out.println("Decoding TrueType font " + pFont.name);
			System.out.println(" - font data is " + pFont.data);
			System.out.println(" - font descriptor is " + pFont.descriptor);
			System.out.println(" - used chars are " + Arrays.toString(pFont.getUsedCharCodes()));
			System.out.println(" - unicode mapping is " + pFont.ucMappings.toString());
		}
		
		//	read basic data
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("Got font program type 2:");
		int ffByteOffset = 0;
		int scalerType = readUnsigned(data, ffByteOffset, (ffByteOffset+4));
		ffByteOffset += 4;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - scaler type is " + scalerType);
		int numTables = readUnsigned(data, ffByteOffset, (ffByteOffset+2));
		ffByteOffset += 2;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - num tables is " + numTables);
		int searchRange = readUnsigned(data, ffByteOffset, (ffByteOffset+2));
		ffByteOffset += 2;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - search range is " + searchRange);
		int entrySelector = readUnsigned(data, ffByteOffset, (ffByteOffset+2));
		ffByteOffset += 2;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - entry selector is " + entrySelector);
		int rangeShift = readUnsigned(data, ffByteOffset, (ffByteOffset+2));
		ffByteOffset += 2;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - range shift is " + rangeShift);
		
		//	read tables, and index them by name
		HashMap tables = new HashMap();
		int tableEnd = -1;
		for (int t = 0; t < numTables; t++) {
			String tag = "";
			for (int i = 0; i < 4; i++) {
				tag += ((char) readUnsigned(data, ffByteOffset, (ffByteOffset+1)));
				ffByteOffset++;
			}
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - table " + tag + ":");
			int checkSum = readUnsigned(data, ffByteOffset, (ffByteOffset+4));
			ffByteOffset += 4;
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - checksum is " + checkSum);
			int offset = readUnsigned(data, ffByteOffset, (ffByteOffset+4));
			ffByteOffset += 4;
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - offset is " + offset);
			int length = readUnsigned(data, ffByteOffset, (ffByteOffset+4));
			ffByteOffset += 4;
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - length is " + length);
			tableEnd = offset + length;
			byte[] tableData = new byte[length];
			System.arraycopy(data, offset, tableData, 0, length);
			tables.put(tag.trim(), tableData);
			while ((tableEnd % 4) != 0)
				tableEnd++;
			
		}
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - table end is " + tableEnd);
		if (tableEnd != -1)
			ffByteOffset = tableEnd;
		
		if (ffByteOffset < data.length) {
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - " + (data.length - ffByteOffset) + " bytes remaining after tables");
//			System.out.println(" - bytes are " + new String(ffBytes, ffByteOffset, (ffBytes.length-ffByteOffset)));
//			for (int b = ffByteOffset; b < Math.min(data.length, (ffByteOffset + 1024)); b++) {
//				int bt = convertUnsigned(data[b]);
//				System.out.println(" - " + bt + " (" + ((char) bt) + ")");
//			}
		}
		else if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - no bytes remaining after tables");
		
		//	get 'head', 'cmap', 'loca', and 'glyf' tables
		byte[] headBytes = ((byte[]) tables.get("head"));
		byte[] cmapBytes = ((byte[]) tables.get("cmap"));
		byte[] locaBytes = ((byte[]) tables.get("loca"));
		byte[] glyfBytes = ((byte[]) tables.get("glyf"));
		if ((headBytes == null) || ((cmapBytes == null) && !dataFromBaseFont) || (locaBytes == null) || (glyfBytes == null))
			return;
		
		//	get parameters from 'head' table (http://developer.apple.com/fonts/TrueType-Reference-Manual/RM06/Chap6head.html)
		int headByteOffset = 0;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - reading 'head' table of " + headBytes.length + " bytes");
		int hVersion = readUnsigned(headBytes, headByteOffset, (headByteOffset+4));
		headByteOffset += 4;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - version is " + (((double) hVersion) / 0x00010000));
		int hFontVersion = readUnsigned(headBytes, headByteOffset, (headByteOffset+4));
		headByteOffset += 4;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - font version is " + (((double) hFontVersion) / 0x00010000));
		int hCheckSumAdjustment = readUnsigned(headBytes, headByteOffset, (headByteOffset+4));
		headByteOffset += 4;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - check sum adjustment is " + hCheckSumAdjustment);
		int hMagicNumber = readUnsigned(headBytes, headByteOffset, (headByteOffset+4));
		headByteOffset += 4;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - magic number is " + hMagicNumber);
		int hFlags = readUnsigned(headBytes, headByteOffset, (headByteOffset+2));
		headByteOffset += 2;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - flags are " + Integer.toString(hFlags, 2));
		int hUnitsPerEm = readUnsigned(headBytes, headByteOffset, (headByteOffset+2));
		headByteOffset += 2;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - units per em is " + hUnitsPerEm);
		long hCreated = readUnsigned(headBytes, headByteOffset, (headByteOffset+4));
		headByteOffset += 4;
		hCreated <<= 32;
		hCreated |= readUnsigned(headBytes, headByteOffset, (headByteOffset+4));
		headByteOffset += 4;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - created is " + hCreated);
		long hModified = readUnsigned(headBytes, headByteOffset, (headByteOffset+4));
		headByteOffset += 4;
		hModified <<= 32;
		hModified |= readUnsigned(headBytes, headByteOffset, (headByteOffset+4));
		headByteOffset += 4;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - modified is " + hModified);
		int hMinX = readUnsigned(headBytes, headByteOffset, (headByteOffset+2));
		headByteOffset += 2;
		if (hMinX > 32768)
			hMinX -= 65536;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - min X is " + hMinX);
		int hMinY = readUnsigned(headBytes, headByteOffset, (headByteOffset+2));
		headByteOffset += 2;
		if (hMinY > 32768)
			hMinY -= 65536;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - min Y is " + hMinY);
		int hMaxX = readUnsigned(headBytes, headByteOffset, (headByteOffset+2));
		headByteOffset += 2;
		if (hMaxX > 32768)
			hMaxX -= 65536;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - max X is " + hMaxX);
		int hMaxY = readUnsigned(headBytes, headByteOffset, (headByteOffset+2));
		headByteOffset += 2;
		if (hMaxY > 32768)
			hMaxY -= 65536;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - max Y is " + hMaxY);
		int hMacStyle = readUnsigned(headBytes, headByteOffset, (headByteOffset+2));
		headByteOffset += 2;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - mac style is " + hMacStyle);
		int hLowestRecPPEM = readUnsigned(headBytes, headByteOffset, (headByteOffset+2));
		headByteOffset += 2;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - lowest pixel size is " + hLowestRecPPEM);
		int hFontDirectionHint = readUnsigned(headBytes, headByteOffset, (headByteOffset+2));
		headByteOffset += 2;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - font direction hint is " + hFontDirectionHint);
		int hIndexToLocFormat = readUnsigned(headBytes, headByteOffset, (headByteOffset+2));
		headByteOffset += 2;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - loca format is " + hIndexToLocFormat); // 0 for short offsets (number of byte pairs), 1 for long (number of bytes)
		int hGlyphDataFormat = readUnsigned(headBytes, headByteOffset, (headByteOffset+2));
		headByteOffset += 2;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - glyph data format is " + hGlyphDataFormat);
//		
//		//	TODO_once_required try and read 'post' table as well (contains PostScript char names, which might help decoding to Unicode)
//		byte[] postBytes = ((byte[]) tables.get("post"));
//		if (postBytes != null) {
//			if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - reading 'post' table of " + postBytes.length + " bytes");
//			int postByteOffset = 0;
//			int pVersion = readUnsigned(postBytes, postByteOffset, (postByteOffset+4));
//			postByteOffset += 4;
//			if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - version is " + Integer.toString(pVersion, 16).toUpperCase());
//			int iAngle = readUnsigned(postBytes, postByteOffset, (postByteOffset+4));
//			postByteOffset += 4;
//			if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - italics angle is " + (((double) iAngle) / 0x00010000));
//			int uPos = readUnsigned(postBytes, postByteOffset, (postByteOffset+2));
//			postByteOffset += 2;
//			if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - underline position is " + uPos);
//			int uThick = readUnsigned(postBytes, postByteOffset, (postByteOffset+2));
//			postByteOffset += 2;
//			if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - underline thickness is " + uThick);
//			int fPitch = readUnsigned(postBytes, postByteOffset, (postByteOffset+4));
//			postByteOffset += 4;
//			if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - fixed pitch is " + fPitch);
//			int minMem42 = readUnsigned(postBytes, postByteOffset, (postByteOffset+4));
//			postByteOffset += 4;
//			int maxMem42 = readUnsigned(postBytes, postByteOffset, (postByteOffset+4));
//			postByteOffset += 4;
//			if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - mode 42 memory is " + minMem42 + "-" + maxMem42);
//			int minMem1 = readUnsigned(postBytes, postByteOffset, (postByteOffset+4));
//			postByteOffset += 4;
//			int maxMem1 = readUnsigned(postBytes, postByteOffset, (postByteOffset+4));
//			postByteOffset += 4;
//			if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - mode 1 memory is " + minMem1 + "-" + maxMem1);
//		}
		
		//	map glyph IDs to CIDs
		HashMap cidsByGlyphIndex;
		
		//	default CID mappings in base font mode
		if (dataFromBaseFont) {
			cidsByGlyphIndex = new HashMap() {
				public Object get(Object key) {
					Object value = ((glyphIdsToCids == null) ? null : glyphIdsToCids.get(key));
//					return ((value == null) ? key : value);
					if (value == null) {
						if (DEBUG_TRUE_TYPE_LOADING) System.out.println("Mapped glyph index " + key + " to itself as CID");
						return key;
					}
					else {
						if (DEBUG_TRUE_TYPE_LOADING) System.out.println("Mapped glyph index " + key + " to CID " + value);
						return value;
					}
				}
			};
		}
		
		//	read CID mappings from 'cmap' table (http://developer.apple.com/fonts/TrueType-Reference-Manual/RM06/Chap6cmap.html)
		else {
			cidsByGlyphIndex = new HashMap() {
				private HashSet values = new HashSet(); // need this for fast reverse lookup, to prevent same CID mapped to twice
				public Object put(Object key, Object value) {
					this.values.add(value);
					if (DEBUG_TRUE_TYPE_LOADING) System.out.println("Mapping glyph index " + key + " to CID " + value);
					return super.put(key, value);
				}
				public void clear() {
					super.clear();
					this.values.clear();
				}
				public boolean containsValue(Object value) {
					return this.values.contains(value);
				}
			};
			int cmapPlatformId = -1;
			int cmapPlatformSpecId = -1;
			int cmapByteOffset = 0;
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - reading 'cmap' table of " + cmapBytes.length + " bytes");
			int mVersion = readUnsigned(cmapBytes, cmapByteOffset, (cmapByteOffset+2));
			cmapByteOffset += 2;
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - version is " + mVersion);
			int mSubTableCount = readUnsigned(cmapBytes, cmapByteOffset, (cmapByteOffset+2));
			cmapByteOffset += 2;
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - number of sub tables is " + mSubTableCount);
			for (int t = 0; t < mSubTableCount; t++) {
				if (DEBUG_TRUE_TYPE_LOADING) System.out.println("     - table " + t);
				int stPlatformId = readUnsigned(cmapBytes, cmapByteOffset, (cmapByteOffset+2));
				cmapByteOffset += 2;
				if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - platform ID is " + stPlatformId);
				int stPlatformSpecId = readUnsigned(cmapBytes, cmapByteOffset, (cmapByteOffset+2));
				cmapByteOffset += 2;
				if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - platform specific ID is " + stPlatformSpecId);
				int stOffset = readUnsigned(cmapBytes, cmapByteOffset, (cmapByteOffset+4));
				cmapByteOffset += 4;
//				
//				//	find map with highest precedence using platform ID and platform specific ID (table 'Platform Identifiers' in http://developer.apple.com/fonts/TrueType-Reference-Manual/RM06/Chap6cmap.html)
//				boolean stHasHigherPrecedence = false;
//				if (cmapPlatformId == -1)
//					stHasHigherPrecedence = true;
//				else if (cmapPlatformId == 0)
//					stHasHigherPrecedence = ((stPlatformId == 0) && (stPlatformSpecId > cmapPlatformSpecId));
//				else if (stPlatformId == 0)
//					stHasHigherPrecedence = true;
//				else if (cmapPlatformId == 3) {
//					if (stPlatformId != 3)
//						stHasHigherPrecedence = false;
//					else if (cmapPlatformSpecId == 10)
//						stHasHigherPrecedence = false;
//					else if (stPlatformSpecId == 10)
//						stHasHigherPrecedence = true;
//					else if (cmapPlatformSpecId == 1)
//						stHasHigherPrecedence = false;
//					else if (stPlatformSpecId == 1)
//						stHasHigherPrecedence = true;
//					else if (cmapPlatformSpecId == 0)
//						stHasHigherPrecedence = false;
//					else if (stPlatformSpecId == 0)
//						stHasHigherPrecedence = true;
//				}
//				else if (stPlatformId == 3)
//					stHasHigherPrecedence = true;
//				
//				//	clear mappings if current table has higher precedence
//				if (stHasHigherPrecedence) {
//					cmapPlatformId = stPlatformId;
//					cmapPlatformSpecId = stPlatformSpecId;
//					cidsByGlyphIndex.clear();
//				}
				
				if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - offset is " + stOffset);
				int stByteOffset = stOffset;
				int stFormat = readUnsigned(cmapBytes, stByteOffset, (stByteOffset+2));
				stByteOffset += 2;
				if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - format is " + stFormat);
				if ((stFormat == 8) || (stFormat == 10) || (stFormat == 12) || (stFormat == 13))
					stByteOffset += 2;
				int stLength;
				int stLanguage;
				if ((stFormat == 8) || (stFormat == 10) || (stFormat == 12) || (stFormat == 13) || (stFormat == 14)) {
					stLength = readUnsigned(cmapBytes, stByteOffset, (stByteOffset+4));
					stByteOffset += 4;
					if (stFormat == 14)
						stLanguage = -1;
					else {
						stLanguage = readUnsigned(cmapBytes, stByteOffset, (stByteOffset+4));
						stByteOffset += 4;
					}
				}
				else {
					stLength = readUnsigned(cmapBytes, stByteOffset, (stByteOffset+2));
					stByteOffset += 2;
					stLanguage = readUnsigned(cmapBytes, stByteOffset, (stByteOffset+2));
					stByteOffset += 2;
				}
				if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - length is " + stLength);
				if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - language is " + stLanguage);
				HashMap stCidsByGlyphIndex = new HashMap();
				if (stFormat == 0) {
					for (int c = 0; c < 256; c++) {
						int stGlyphIndex = readUnsigned(cmapBytes, stByteOffset, (stByteOffset+1));
						stByteOffset += 1;
						if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - handling CID " + c);
						if (stGlyphIndex == 0) {
							if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       --> undefined, not mapped to " + stGlyphIndex);
							continue;
						}
						
						Integer gi = new Integer(stGlyphIndex);
//						if (cidsByGlyphIndex.containsKey(gi)) {
						if (stCidsByGlyphIndex.containsKey(gi)) {
							if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       --> duplicate, not mapped to " + stGlyphIndex);
							continue;
						}
						
						Integer cid = new Integer(c);
//						if (!pFont.usesCharCode(cid)) {
						if (!pCharDecoder.usesChar(cid, null)) {
//						if (!pCharDecoder.usesGlyph(cid)) {
							if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - unused CID " + c + " not mapped to " + stGlyphIndex);
							continue;
						}
//						if (cidsByGlyphIndex.containsValue(cid)) {
						if (stCidsByGlyphIndex.containsValue(cid)) {
							if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       --> duplicate value, not mapped to " + stGlyphIndex);
							continue;
						}
						
						if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       --> mapped to " + stGlyphIndex);
//						cidsByGlyphIndex.put(gi, cid);
						stCidsByGlyphIndex.put(gi, cid);
					}
				}
				else if (stFormat == 2) {
					//	TODO implement this once example becomes available
				}
				else if (stFormat == 4) {
					int stSegCountX2 = readUnsigned(cmapBytes, stByteOffset, (stByteOffset+2));
					stByteOffset += 2;
					int stSegCount = (stSegCountX2 / 2); 
					if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - segment count is " + stSegCount);
					int stSearchRange = readUnsigned(cmapBytes, stByteOffset, (stByteOffset+2));
					stByteOffset += 2;
					if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - search range is " + stSearchRange);
					int stEntrySelector = readUnsigned(cmapBytes, stByteOffset, (stByteOffset+2));
					stByteOffset += 2;
					if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - entry selector is " + stEntrySelector);
					int stRangeShift = readUnsigned(cmapBytes, stByteOffset, (stByteOffset+2));
					stByteOffset += 2;
					if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - range shift is " + stRangeShift);
					int[] stSegEnds = new int[stSegCount];
					for (int s = 0; s < stSegCount; s++) {
						stSegEnds[s] = readUnsigned(cmapBytes, stByteOffset, (stByteOffset+2));
						stByteOffset += 2;
					}
					if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - segment ends are " + Arrays.toString(stSegEnds));
					int stReservedPad = readUnsigned(cmapBytes, stByteOffset, (stByteOffset+2));
					stByteOffset += 2;
					if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - reserved pad is " + stReservedPad);
					int[] stSegStarts = new int[stSegCount];
					for (int s = 0; s < stSegCount; s++) {
						stSegStarts[s] = readUnsigned(cmapBytes, stByteOffset, (stByteOffset+2));
						stByteOffset += 2;
					}
					if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - segment starts are " + Arrays.toString(stSegStarts));
					int[] stIdDeltas = new int[stSegCount];
					for (int s = 0; s < stSegCount; s++) {
						stIdDeltas[s] = readUnsigned(cmapBytes, stByteOffset, (stByteOffset+2));
						stByteOffset += 2;
					}
					if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - ID deltas are " + Arrays.toString(stIdDeltas));
					int[] stIdRangeOffsets = new int[stSegCount];
					for (int s = 0; s < stSegCount; s++) {
						stIdRangeOffsets[s] = readUnsigned(cmapBytes, stByteOffset, (stByteOffset+2));
						stByteOffset += 2;
					}
					if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - ID range offsets are " + Arrays.toString(stIdRangeOffsets));
					for (int s = 0; s < stSegCount; s++) {
						if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - reading segment " + s);
						for (int c = stSegStarts[s]; c <= stSegEnds[s]; c++) {
							if (DEBUG_TRUE_TYPE_LOADING) System.out.println("         - checking CID " + c);
							Integer gi;
							Integer cid = new Integer(c);
							if (c >= 256) {
								Character ucCh = new Character((char) c);
								Integer feCid = PdfFont.getCharCode(ucCh, pFont.encoding, DEBUG_TRUE_TYPE_LOADING);
								if (feCid != null) {
									if (DEBUG_TRUE_TYPE_LOADING) System.out.println("           - font encoding specific " + pFont.encoding + " CID is " + feCid);
									cid = feCid;
								}
							}
							
							if (cid.intValue() == 65535) {
								if (DEBUG_TRUE_TYPE_LOADING) System.out.println("           ==> non-char ignoring");
								continue;
							}
							if (!pCharDecoder.usesChar(cid, null)) {
//							if (!pCharDecoder.usesGlyph(cid)) {
								if (DEBUG_TRUE_TYPE_LOADING) System.out.println("           ==> not used, skipped");
								continue;
							}
							
							if (stIdRangeOffsets[s] == 0) {
//								int stGlyphIndex = c + stIdDeltas[s];
//								if (stGlyphIndex > 65535)
//									stGlyphIndex -= 65536;
								int stGlyphIndex = ((c + stIdDeltas[s]) & 0xFFFF);
								if (DEBUG_TRUE_TYPE_LOADING) System.out.println("           - GI is " + stGlyphIndex);
								gi = new Integer(stGlyphIndex);
//								if (cidsByGlyphIndex.containsKey(gi)) {
								if (stCidsByGlyphIndex.containsKey(gi)) {
									if (DEBUG_TRUE_TYPE_LOADING) System.out.println("           ==> already mapped");
									continue;
								}
//								if (cidsByGlyphIndex.containsValue(cid)) {
								if (stCidsByGlyphIndex.containsValue(cid)) {
									if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       ==> value already mapped");
									continue;
								}
								if (DEBUG_TRUE_TYPE_LOADING) System.out.println("           ==> CID " + c + "/" + cid.intValue() + " mapped (1) to GI " + gi);
//								cidsByGlyphIndex.put(gi, cid);
								stCidsByGlyphIndex.put(gi, cid);
							}
							else {
								//	glyphIndex = *( &idRangeOffset[i] + idRangeOffset[i] / 2 + (c - startCode[i]) )
								//	glyphIndexAddress = idRangeOffset[i] + 2 * (c - startCode[i]) + (Ptr) &idRangeOffset[i]
								int stGlyphIndexAddress = stIdRangeOffsets[s] + (2 * (c - stSegStarts[s])) + (stByteOffset - (2 * (stSegCount - s)));
								int stGlyphIndex = readUnsigned(cmapBytes, stGlyphIndexAddress, (stGlyphIndexAddress+2));
								if (stGlyphIndex == 0)
									continue;
								stGlyphIndex += stIdDeltas[s];
								if (stGlyphIndex > 65535)
									stGlyphIndex -= 65536;
								if (DEBUG_TRUE_TYPE_LOADING) System.out.println("           - GI is " + stGlyphIndex);
								gi = new Integer(stGlyphIndex);
//								if (cidsByGlyphIndex.containsKey(gi)) {
								if (stCidsByGlyphIndex.containsKey(gi)) {
									if (DEBUG_TRUE_TYPE_LOADING) System.out.println("           ==> already mapped");
									continue;
								}
//								if (cidsByGlyphIndex.containsValue(cid)) {
								if (stCidsByGlyphIndex.containsValue(cid)) {
									if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       ==> value already mapped");
									continue;
								}
//								cidsByGlyphIndex.put(gi, cid);
								if (DEBUG_TRUE_TYPE_LOADING) System.out.println("           ==> CID " + c + "/" + cid.intValue() + " mapped (2) to GI " + gi);
								stCidsByGlyphIndex.put(gi, cid);
							}
						}
					}
				}
				else if (stFormat == 6) {
					//	TODO implement this once example becomes available
				}
				else if (stFormat == 8) {
					//	TODO implement this once example becomes available
				}
				else if (stFormat == 10) {
					//	TODO implement this once example becomes available
				}
				else if (stFormat == 12) {
					//	TODO implement this once example becomes available
				}
				else if (stFormat == 13) {
					//	TODO implement this once example becomes available
				}
				else if (stFormat == 14) {
					//	TODO implement this once example becomes available
				}
				
				//	found anything useful?
				if (stCidsByGlyphIndex.isEmpty())
					continue;
				
				//	find map with highest precedence using platform ID and platform specific ID (table 'Platform Identifiers' in http://developer.apple.com/fonts/TrueType-Reference-Manual/RM06/Chap6cmap.html)
				boolean stHasHigherPrecedence = false;
				if (cmapPlatformId == -1)
					stHasHigherPrecedence = true;
				else if (cmapPlatformId == 0)
					stHasHigherPrecedence = ((stPlatformId == 0) && (stPlatformSpecId > cmapPlatformSpecId));
				else if (stPlatformId == 0)
					stHasHigherPrecedence = true;
				else if (cmapPlatformId == 3) {
					if (stPlatformId != 3)
						stHasHigherPrecedence = false;
					else if (cmapPlatformSpecId == 10)
						stHasHigherPrecedence = false;
					else if (stPlatformSpecId == 10)
						stHasHigherPrecedence = true;
					else if (cmapPlatformSpecId == 1)
						stHasHigherPrecedence = false;
					else if (stPlatformSpecId == 1)
						stHasHigherPrecedence = true;
					else if (cmapPlatformSpecId == 0)
						stHasHigherPrecedence = false;
					else if (stPlatformSpecId == 0)
						stHasHigherPrecedence = true;
				}
				else if (stPlatformId == 3)
					stHasHigherPrecedence = true;
				
				//	clear mappings if current table has higher precedence
				if (stHasHigherPrecedence) {
					cmapPlatformId = stPlatformId;
					cmapPlatformSpecId = stPlatformSpecId;
					//cidsByGlyphIndex.clear();
					cidsByGlyphIndex.putAll(stCidsByGlyphIndex);
				}
				else for (Iterator giit = stCidsByGlyphIndex.keySet().iterator(); giit.hasNext();) {
					Integer gi = ((Integer) giit.next());
					if (cidsByGlyphIndex.containsKey(gi))
						continue;
					Integer cid = ((Integer) stCidsByGlyphIndex.get(gi));
					if (cidsByGlyphIndex.containsValue(cid))
						continue;
					cidsByGlyphIndex.put(gi, cid);
				}
			}
		}
		
		//	get locations from 'loca' table and read glyph data (http://developer.apple.com/fonts/TrueType-Reference-Manual/RM06/Chap6loca.html)
		HashMap glyphsByIndex = new HashMap();
		ArrayList glyphData = new ArrayList();
		Object fontFileEncoding = pFont.descriptor.get("Encoding");
		if (hIndexToLocFormat == 1) {
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - reading 'loca' table of " + locaBytes.length + " bytes in 4-byte mode");
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - font file encoding is " + fontFileEncoding + ", font encoding is " + pFont.encoding);
			int glyphIndex = 0;
			int glyphStart = 0;
			for (int o = 4; o < locaBytes.length; o += 4) {
				int glyphEnd = readUnsigned(locaBytes, o, (o+4));
				if (glyphStart < glyphEnd) {
					Integer gi = new Integer(glyphIndex);
//					Integer cid = (dataFromBaseFont ? gi : ((Integer) cidsByGlyphIndex.get(gi)));
					Integer cid = ((Integer) cidsByGlyphIndex.get(gi));
					if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - got glyph " + glyphIndex + " from " + glyphStart + " to " + glyphEnd + ", CID is " + cid);
					//	read glyph from 'glyf' table (http://developer.apple.com/fonts/TrueType-Reference-Manual/RM06/Chap6glyf.html)
					GlyphDataTrueType glyph = readGlyphTrueType(glyphStart, glyphEnd, glyfBytes, ((cid == null) ? -1 : cid.intValue()), glyphIndex);
					glyphsByIndex.put(gi, glyph);
					if (glyph.cid != -1)
						glyphData.add(glyph);
				}
				glyphIndex++;
				glyphStart = glyphEnd;
			}
		}
		else {
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - reading 'loca' table of " + locaBytes.length + " bytes in 2-byte mode");
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - font file encoding is " + fontFileEncoding + ", font encoding is " + pFont.encoding);
			int glyphIndex = 0;
			int glyphStart = 0;
			for (int o = 2; o < locaBytes.length; o += 2) {
				int glyphEnd = readUnsigned(locaBytes, o, (o+2));
				glyphEnd *= 2;
				if (glyphStart < glyphEnd) {
					Integer gi = new Integer(glyphIndex);
//					Integer cid = (dataFromBaseFont ? gi : ((Integer) cidsByGlyphIndex.get(gi)));
					Integer cid = ((Integer) cidsByGlyphIndex.get(gi));
					if (DEBUG_TRUE_TYPE_LOADING) System.out.println("   - got glyph " + glyphIndex + " from " + glyphStart + " to " + glyphEnd + ", CID is " + cid);
					//	read glyph from 'glyf' table (http://developer.apple.com/fonts/TrueType-Reference-Manual/RM06/Chap6glyf.html)
					GlyphDataTrueType glyph = readGlyphTrueType(glyphStart, glyphEnd, glyfBytes, ((cid == null) ? -1 : cid.intValue()), glyphIndex);
					glyphsByIndex.put(gi, glyph);
					if (glyph.cid != -1)
						glyphData.add(glyph);
				}
				glyphIndex++;
				glyphStart = glyphEnd;
			}
		}
		
		//	anything to work on?
		if (glyphData.isEmpty())
			return;
		
		//	tray up glyph keys
		Object[] glyphKeys = new Object[glyphData.size()];
		for (int c = 0; c < glyphData.size(); c++) {
			GlyphDataTrueType glyph = ((GlyphDataTrueType) glyphData.get(c));
			glyphKeys[c] = new Integer(glyph.cid);
		}
		
		//	measure characters
		pm.setInfo("   - measuring characters");
		int maxDescent = 0;
		int maxCapHeight = 0;
//		OpTrackerTrueType[] otrs = new OpTrackerTrueType[glyphData.size()];
		OpTracker[] otrs = new OpTracker[glyphData.size()];
		HashMap renderingSequencesToCharIndices = new HashMap();
		HashMap charIndicesToSynonyms = new HashMap();
		BufferedImage allGlyphs = new BufferedImage(1500, 1500, BufferedImage.TYPE_BYTE_BINARY);
		Graphics2D agGr = allGlyphs.createGraphics();
		agGr.setColor(Color.WHITE);
		agGr.fillRect(0, 0, allGlyphs.getWidth(), allGlyphs.getHeight());
		agGr.setColor(Color.BLACK);
		agGr.translate(100, 900);
		agGr.scale(0.3, -0.3); // 300 x 300 for 1000 x 1000 PDF font space (font size 300)
		agGr.setStroke(new BasicStroke(5));
		for (int c = 0; c < glyphData.size(); c++) {
			GlyphDataTrueType glyph = ((GlyphDataTrueType) glyphData.get(c));
			
			CharImage chi = ((CharImage) pCharDecoder.plainCharCodesToImages.get(new Integer(glyph.gid)));
//			CharImage chi = ((CharImage) pCharDecoder.glyphKeysToImages.get(glyphKeys[c]));
			if (chi != null) {
				pm.setInfo("     - skipping previously rendered char " + c + " with CID " + glyph.cid + " (" + pFont.getUnicode(glyph.cid) + ")");
				continue;
			}
			
			//	render glyph
//			otrs[c] = new OpTrackerTrueType();
			otrs[c] = new OpTracker(DEBUG_TRUE_TYPE_RENDERING && (DEBUG_TRUE_TYPE_TARGET_CID == glyph.cid));
			pm.setInfo("     - measuring char " + c + " with CID " + glyph.cid + " (" + pFont.getUnicode(glyph.cid) + ")");
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" ==> font glyph key maps to " + pFont.getCharCodeByGlyphKey(new Integer(glyph.cid)));
			if (glyph instanceof SimpleGlyphDataTrueType)
				runFontTrueTypeOps(((SimpleGlyphDataTrueType) glyph), false, otrs[c], false);
			else runFontTrueTypeOps(((CompoundGlyphDataTrueType) glyph), glyphsByIndex, otrs[c], false);
			if (DEBUG_TRUE_TYPE_LOADING) {
				System.out.println(" ==> " + otrs[c].ops);
				System.out.println("     --> " + otrs[c].minX + " < X < " + otrs[c].maxX);
				System.out.println("     --> " + otrs[c].minY + " < Y < " + otrs[c].maxY);
			}
			maxDescent = Math.min(maxDescent, otrs[c].minY);
			maxCapHeight = Math.max(maxCapHeight, otrs[c].maxY);
			agGr.draw(otrs[c].path);
			
			//	index glyph rendering sequence to catch identical characters
			String renderingSequence = otrs[c].ops.toString();
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" ==> " + renderingSequence);
			if ("Ops:".equals(renderingSequence)) { /* no synonymizing spaces, tend to differ in width */ }
			else if (renderingSequencesToCharIndices.containsKey(renderingSequence))
				charIndicesToSynonyms.put(new Integer(c), renderingSequencesToCharIndices.get(renderingSequence));
//			else if (pFont.usesCharCode(new Integer(c)))
			else if (pCharDecoder.usesChar(new Integer(c), null))
//			else if (pCharDecoder.usesGlyph(glyphKeys[c]))
				renderingSequencesToCharIndices.put(renderingSequence, new Integer(c));
		}
		agGr.dispose();
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("Max descent is " + maxDescent + ", max cap height is " + maxCapHeight);
		
		//	check rendered glyph path dimensions
		int white = Color.WHITE.getRGB(); // TODO make this a constant !!!
		int minGlyphX = Integer.MAX_VALUE;
		int maxGlyphX = 0;
		int minGlyphY = Integer.MAX_VALUE;
		int maxGlyphY = 0;
		for (int x = 0; x < allGlyphs.getWidth(); x++) {
			for (int y = 0; y < allGlyphs.getHeight(); y++) {
				if (allGlyphs.getRGB(x, y) == white)
					continue;
				minGlyphX = Math.min(minGlyphX, x);
				maxGlyphX = Math.max(maxGlyphX, x);
				minGlyphY = Math.min(minGlyphY, y);
				maxGlyphY = Math.max(maxGlyphY, y);
			}
		}
		
		//	scale back to font space, inverting back Y coordinate for comparison with font indicated measurements
		int fMinGlyphX = (((minGlyphX - 100) * 10) / 3);
		int fMaxGlyphX = (((maxGlyphX - 100) * 10) / 3);
		int fMinGlyphY = (((maxGlyphY - 900) * 10) / -3);
		int fMaxGlyphY = (((minGlyphY - 900) * 10) / -3);
		if (DEBUG_TRUE_TYPE_LOADING) {
			System.out.println("Glyph extent is " + minGlyphX + "-" + maxGlyphX + " by " + minGlyphY + "-" + maxGlyphY);
			System.out.println(" ==> " + fMinGlyphX + "-" + fMaxGlyphX + " by " + fMinGlyphY + "-" + fMaxGlyphY);
		}
		
		//	compare measured glyph extents to font indicated ascent and descent
		int bbTop = ((pFont.bBox == null) ? 0 : pFont.bBox.top);
		float ascentRatio = (((float) fMaxGlyphY) / Math.max(Math.round(pFont.ascent * 1000), bbTop));
		int bbBottom = ((pFont.bBox == null) ? 0 : pFont.bBox.bottom);
		float descentRatio = (((float) fMinGlyphY) / Math.min(Math.round(pFont.descent * 1000), bbBottom));
		int bbHeight = ((pFont.bBox == null) ? 0 : -pFont.bBox.getHeight());
		float heightRatio = (((float) (fMaxGlyphY - fMinGlyphY)) / Math.max(Math.round((pFont.ascent - pFont.descent) * 1000), bbHeight));
		if (DEBUG_TRUE_TYPE_LOADING) {
			System.out.println("Font indicated height is " + Math.round(pFont.descent * 1000) + "-" + Math.round(pFont.ascent * 1000) + ", bounding box is " + pFont.bBox);
			System.out.println(" ==> height ratio is " + heightRatio + ", ascent ratio is " + ascentRatio + ", descent ratio is " + descentRatio);
		}
		
		//	scale down glyphs if significantly taller than indicated ascent, and adjust descent as well, as font sizes go up for compensation
		BufferedImage sAllGlyphs = null;
		if ((1.5 < ascentRatio) && (charSet == VECTORIZED_OCR_DECODING)) {
			pFont.scaleDownFactor = Math.round(ascentRatio);
			System.out.println(" ==> scaling down glyphs by factor " + pFont.scaleDownFactor);
			pFont.descent = Math.max(pFont.descent, (((float) fMinGlyphY) / (pFont.scaleDownFactor * 1000))); // descent is negative number ...
			System.out.println(" ==> descent adjusted to " + Math.round(pFont.descent * 1000));
			AffineTransform at = AffineTransform.getScaleInstance((1.0 / pFont.scaleDownFactor), (1.0 / pFont.scaleDownFactor));
			sAllGlyphs = new BufferedImage(1500, 1500, BufferedImage.TYPE_BYTE_BINARY);
			Graphics2D sagGr = sAllGlyphs.createGraphics();
			sagGr.setColor(Color.WHITE);
			sagGr.fillRect(0, 0, sAllGlyphs.getWidth(), sAllGlyphs.getHeight());
			sagGr.setColor(Color.BLACK);
			sagGr.translate(100, 900);
			sagGr.scale(0.3, -0.3); // 300 x 300 for 1000 x 1000 PDF font space (font size 300)
			sagGr.setStroke(new BasicStroke(5));
			for (int c = 0; c < otrs.length; c++) {
				if (otrs[c] == null)
					continue;
				otrs[c].path.transform(at);
				sagGr.draw(otrs[c].path);
			}
			sagGr.dispose();
		}
		
		//	make sure to accommodate ascent and descent (keeps glyphs of dash-only fonts in vertical position, and in proportion)
//		maxCapHeight = Math.max(maxCapHeight, Math.round(pFont.ascent * 1000));
//		maxDescent = Math.min(maxDescent, Math.round(pFont.descent * 1000));
		maxCapHeight = Math.max(maxCapHeight, Math.max(Math.round(pFont.ascent * 1000), bbTop));
		maxDescent = Math.min(maxDescent, Math.min(Math.min(Math.round(pFont.descent * 1000), bbBottom), Math.round((maxCapHeight * pFont.descent) / pFont.ascent)));
		
		//	set up rendering
		int maxRenderSize = 300;
		float scale = 1.0f;
		if ((maxCapHeight - maxDescent) > maxRenderSize)
			scale = (((float) maxRenderSize) / (maxCapHeight - maxDescent));
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" ==> scaledown factor is " + scale);
		
		char[] chars = new char[glyphData.size()];
		Arrays.fill(chars, ((char) 0));
//		HashSet unresolvedCIDs = new HashSet() {
//			public boolean add(Object e) {
//				System.out.println("Marked as unresolved CID: " + e);
//				return super.add(e);
//			}
//		};
		
		//	generate images and match against named char
		pm.setInfo("   - decoding characters");
		CharImage[] charImages = new CharImage[glyphData.size()];
		ImageDisplayDialog fidd = ((DEBUG_TRUE_TYPE_RENDERING || DEBUG_TRUE_TYPE_LOADING) ? new ImageDisplayDialog("Font " + pFont.name) : null);
		for (int c = 0; c < glyphData.size(); c++) {
			GlyphDataTrueType glyph = ((GlyphDataTrueType) glyphData.get(c));
			String chn = null;
			String ucCh = pFont.getUnicode(glyph.cid);
			if ((ucCh != null) && (ucCh.length() > 0)) {
				chars[c] = ucCh.charAt(0);
				chn = StringUtils.getCharName(chars[c]);
			}
			
			CharImage chi = ((CharImage) pCharDecoder.plainCharCodesToImages.get(new Integer(glyph.gid)));
//			CharImage chi = ((CharImage) pCharDecoder.glyphKeysToImages.get(glyphKeys[c]));
			if (chi != null) {
				pm.setInfo("     - reusing previously rendered char " + c + " with CID " + glyph.cid + " (" + ucCh + ", " + chn + ")");
				charImages[c] = chi;
				pFont.setCharImage(glyph.cid, ((chn == null) ? ("ch-" + glyph.cid) : chn), chi.img, chi.path);
//				pFont.setCharImage(glyphKeys[c], chi.img, chi.path);
				continue;
			}
			
//			if (!DEBUG_TRUE_TYPE_RENDERING && !pFont.usedChars.contains(new Integer(glyph.cid))) {
//			if (!DEBUG_TRUE_TYPE_RENDERING && !pFont.usesCharCode(new Integer(glyph.cid))) {
			if (!DEBUG_TRUE_TYPE_RENDERING && !pCharDecoder.usesChar(new Integer(glyph.cid), null)) {
//			if (!DEBUG_TRUE_TYPE_RENDERING && !pCharDecoder.usesGlyph(glyphKeys[c])) {
				pm.setInfo("     - ignoring unused char " + c + " with CID " + glyph.cid + " (" + ucCh + ", " + chn + ")");
				continue;
			}
//			if (chn == null)
//				unresolvedCIDs.add(new Integer(glyph.cid));
			pm.setInfo("     - decoding char " + c + " with CID " + glyph.cid + " (" + ucCh + ", " + chn + ")");
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println("Decoding char " + c + ", CID " + glyph.cid + " (" + ucCh + ", " + chn + ")");
//			String chs = ("" + chars[c]);
//			int chw = ((otrs[c].rWidth == Integer.MIN_VALUE) ? dWidth : (nWidth + otrs[c].rWidth));
//			int chw = ((otrs[c].rWidth == 0) ? dWidth : (nWidth + otrs[c].rWidth));
//			if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - char width is " + chw);
//			if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - stroke count is " + otrs[c].mCount);
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - stroke count is " + otrs[c].segCount);
//			if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - " + otrs[c].id + ": " + otrs[c].minX + " < X < " + otrs[c].maxX);
			
			//	check if char rendering possible
			if ((otrs[c].maxX <= otrs[c].minX) || (otrs[c].maxY <= otrs[c].minY)) {
				if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" ==> unsensible char dimensions: " + otrs[c].minX + "-" + otrs[c].maxX + "x" + otrs[c].minY + "-" + otrs[c].maxY);
				continue;
			}
			
			//	render char
			int mx = 8;
			int my = ((mx * (maxCapHeight - maxDescent)) / (otrs[c].maxX - otrs[c].minX));
//			OpGraphicsTrueType ogr = new OpGraphicsTrueType(
			OpGraphics ogr = new OpGraphics(
					otrs[c].minX,
					maxDescent,
					(maxCapHeight - maxDescent + (my / 2)),
					scale,
					new BufferedImage(
							Math.round((scale * (otrs[c].maxX - otrs[c].minX + mx)) + (2 * glyphOutlineFillSafetyEdge)),
							Math.round((scale * (maxCapHeight - maxDescent + my)) + (2 * glyphOutlineFillSafetyEdge)),
							BufferedImage.TYPE_INT_RGB)
					);
			if (glyph instanceof SimpleGlyphDataTrueType)
				runFontTrueTypeOps(((SimpleGlyphDataTrueType) glyph), false, ogr, (glyph.cid == DEBUG_TRUE_TYPE_TARGET_CID));
			else runFontTrueTypeOps(((CompoundGlyphDataTrueType) glyph), glyphsByIndex, ogr, (glyph.cid == DEBUG_TRUE_TYPE_TARGET_CID));
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - image rendered, size is " + ogr.img.getWidth() + "x" + ogr.img.getHeight() + ", OpGraphics height is " + ogr.height);
			charImages[c] = new CharImage(ogr.img, Math.round(((float) (maxCapHeight * ogr.img.getHeight())) / (maxCapHeight - maxDescent)), otrs[c].path, (otrs[c].maxX - otrs[c].minX));
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println(" - char image wrapped, baseline is " + charImages[c].baseline);
			if (fidd != null)
				fidd.addImage(ogr.img, (glyph.cid + " (" + glyph.gid + "): " + ucCh + " (" + chn + ")"));
//			
//			//	no use going on without a char name
//			if (chn == null) {
//				unresolvedCIDs.add(new Integer(glyph.cid));
//				continue;
//			}
//			
//			//	store char image
//			pFont.setCharImage(glyph.cid, chn, ogr.img);
			
			//	store char image (substitute name if none exists)
			pFont.setCharImage(glyph.cid, ((chn == null) ? ("ch-" + glyph.cid) : chn), ogr.img, otrs[c].path);
//			pFont.setCharImage(glyphKeys[c], ogr.img, otrs[c].path);
			pCharDecoder.plainCharCodesToImages.put(new Integer(glyph.gid), charImages[c]);
//			pCharDecoder.glyphKeysToImages.put(new Integer(glyph.cid), charImages[c]);
//			String cidUc = pFont.getUnicode(glyph.cid);
//			char chnUc = ((chn == null) ? ((char) 0) : StringUtils.getCharForName(chn));
//			if ((cidUc == null) && (chnUc != 0))
//				cidUc = ("" + chnUc);
//			if (cidUc != null)
//				pCharDecoder.plainCharCodesToUnicode.put(new Integer(glyph.gid), cidUc);
		}
//		if ((fidd != null) && (fidd.getImageCount() != 0)) {
//			fidd.setLocationRelativeTo(null);
//			fidd.setSize(600, 400);
//			fidd.setVisible(true);
//		}
		if ((fidd != null) && (fidd.getImageCount() != 0)) {
			CountingSet charSizes = pFont.getCharSizes();
			if (charSizes != null) {
				System.out.println("   - font used in sizes " + charSizes);
				int kCharHeight = (maxCapHeight - maxDescent);
				CountingSet charHeights = new CountingSet(new TreeMap());
				float charHeightSum = 0;
				for (Iterator csit = charSizes.iterator(); csit.hasNext();) {
					Float cs = ((Float) csit.next());
					float charHeight = ((kCharHeight * cs.floatValue()) / 1000);
					charHeights.add(new Float(charHeight), charSizes.getCount(cs));
					charHeightSum += (charHeight * charSizes.getCount(cs));
				}
				System.out.println("   - char heights (at 72 DPI) " + charHeights);
				System.out.println("   - average char height (at 72 DPI) " + (charHeightSum / charSizes.size()));
			}
			
			agGr = allGlyphs.createGraphics();
			agGr.setColor(Color.BLACK);
			agGr.drawLine(0, 900, 1000, 900);
			agGr.drawLine(0, 600, 1000, 600);
			agGr.drawLine(100, 0, 100, 1500);
			agGr.dispose();
			fidd.addImage(allGlyphs, "All Paths");
			
			if (sAllGlyphs != null) {
				Graphics2D sagGr = sAllGlyphs.createGraphics();
				sagGr.setColor(Color.BLACK);
				sagGr.drawLine(0, 900, 1000, 900);
				sagGr.drawLine(0, 600, 1000, 600);
				sagGr.drawLine(100, 0, 100, 1500);
				sagGr.dispose();
				fidd.addImage(sAllGlyphs, "All Paths (scaled)");
			}
			
			fidd.setLocationRelativeTo(null);
			fidd.setSize(600, 400);
			fidd.setVisible(true);
		}
		
		//	create char codes and names for visual glyph decoding
		Integer[] charCodes = new Integer[glyphData.size()];
		Integer[] rawCharCodes = new Integer[glyphData.size()];
		String[] charNames = new String[glyphData.size()];
		for (int c = 0; c < glyphData.size(); c++) {
			GlyphDataTrueType glyph = ((GlyphDataTrueType) glyphData.get(c));
			charCodes[c] = new Integer(glyph.cid);
			rawCharCodes[c] = new Integer(glyph.gid);
			String ucCh = pFont.getUnicode(charCodes[c]);
			if ((ucCh != null) && (ucCh.length() > 0)) {
				chars[c] = ucCh.charAt(0);
				charNames[c] = StringUtils.getCharName(chars[c]);
			}
			else charNames[c] = null;
			glyphKeys[c] = new Integer(glyph.cid);
			
			//	check for char code synonymies
			Integer ci = new Integer(c);
			if (charIndicesToSynonyms.containsKey(ci))
				pFont.setCharCodeSynonym(charCodes[c], charCodes[((Integer) charIndicesToSynonyms.get(ci)).intValue()]);
		}
		decodeChars(pFont, pCharDecoder, chars, charCodes, charNames, glyphKeys, charImages/*, rawCharCodes*/, -1, maxDescent, charSet, pm, DEBUG_TRUE_TYPE_LOADING);
	}
	
	private static GlyphDataTrueType readGlyphTrueType(int start, int end, byte[] glyfBytes, int cid, int glyphIndex) {
		int glyfByteOffset = start;
		int numberOfContours = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+2));
		glyfByteOffset += 2;
		if (numberOfContours > 0x00008000)
			numberOfContours -= 0x00010000;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("     - number of contours is " + numberOfContours);
		int hMinX = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+2));
		glyfByteOffset += 2;
		if (hMinX > 0x00008000)
			hMinX -= 0x00010000;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("     - min X is " + hMinX);
		int hMinY = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+2));
		glyfByteOffset += 2;
		if (hMinY > 0x00008000)
			hMinY -= 0x00010000;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("     - min Y is " + hMinY);
		int hMaxX = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+2));
		glyfByteOffset += 2;
		if (hMaxX > 0x00008000)
			hMaxX -= 0x00010000;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("     - max X is " + hMaxX);
		int hMaxY = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+2));
		glyfByteOffset += 2;
		if (hMaxY > 0x00008000)
			hMaxY -= 0x00010000;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("     - max Y is " + hMaxY);
		if (numberOfContours < 0)
			return readCompoundGlyphTrueType(glyfByteOffset, end, glyfBytes, cid, glyphIndex, -numberOfContours);
		else return readSimpleGlyphTrueType(glyfByteOffset, end, glyfBytes, cid, glyphIndex, numberOfContours);
	}
	
	private static SimpleGlyphDataTrueType readSimpleGlyphTrueType(int start, int end, byte[] glyfBytes, int cid, int glyphIndex, int numberOfContours) {
		int glyfByteOffset = start;
		int[] contourEnds = new int[numberOfContours];
		int numPoints = 0;
		for (int n = 0; n < numberOfContours; n++) {
			contourEnds[n] = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+2));
			glyfByteOffset += 2;
			numPoints = (contourEnds[n]+1);
		}
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("     - contour ends are " + Arrays.toString(contourEnds));
		int instructionLength = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+2));
		glyfByteOffset += 2;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("     - instruction length is " + instructionLength);
		if (DEBUG_TRUE_TYPE_LOADING) {
			StringBuffer instructions = new StringBuffer();
			for (int i = 0; i < instructionLength; i++) {
				int iByte = convertUnsigned(glyfBytes[glyfByteOffset++]);
				System.out.print(" " + Integer.toString(iByte, 16));
				instructions.append((char) iByte);
			}
			System.out.println();
			System.out.println(instructions);
		}
		else glyfByteOffset += instructionLength;
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("     - instructions skipped");
		
		int[] pointFlags = new int[numPoints];
		for (int p = 0; p < numPoints;) {
			int flags = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+1));
			glyfByteOffset += 1;
			if ((flags & 8) == 0)
				pointFlags[p++] = flags;
			else {
				pointFlags[p++] = (flags ^ 8);
				int repeat = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+1));
				glyfByteOffset += 1;
				while (repeat-- != 0)
					pointFlags[p++] = (flags ^ 8);
			}
		}
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("     - flags are " + Arrays.toString(pointFlags));
		
		int[] pointXs = new int[numPoints];
		for (int p = 0; p < numPoints; p++) {
			if ((pointFlags[p] & 2) == 0) {
				if ((pointFlags[p] & 16) == 0) {
					pointXs[p] = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+2));
					glyfByteOffset += 2;
					if (pointXs[p] > 32768)
						pointXs[p] -= 65536;
				}
				else pointXs[p] = 0;
			}
			else {
				pointXs[p] = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+1));
				glyfByteOffset += 1;
				if ((pointFlags[p] & 16) == 0)
					pointXs[p] = -pointXs[p];
			}
		}
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("     - point Xs are " + Arrays.toString(pointXs));
		int[] pointYs = new int[numPoints];
		for (int p = 0; p < numPoints; p++) {
			if ((pointFlags[p] & 4) == 0) {
				if ((pointFlags[p] & 32) == 0) {
					pointYs[p] = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+2));
					glyfByteOffset += 2;
					if (pointYs[p] > 32768)
						pointYs[p] -= 65536;
				}
				else pointYs[p] = 0;
			}
			else {
				pointYs[p] = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+1));
				glyfByteOffset += 1;
				if ((pointFlags[p] & 32) == 0)
					pointYs[p] = -pointYs[p];
			}
		}
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("     - point Ys are " + Arrays.toString(pointYs));
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("     - got to " + glyfByteOffset + ", end is " + end);
		if (DEBUG_TRUE_TYPE_LOADING && ((glyfByteOffset + 1) < end)) {
//			StringBuffer remainder = new StringBuffer();
			System.out.print("     - remainder is");
			while (glyfByteOffset < end) {
				int rByte = convertUnsigned(glyfBytes[glyfByteOffset++]);
//				remainder.append((char) rByte);
				System.out.print(" " + Integer.toString(rByte, 16));
			}
			System.out.println();
//			System.out.println(remainder);
		}
		
		return new SimpleGlyphDataTrueType(cid, glyphIndex, pointFlags, pointXs, pointYs, contourEnds);
	}
	
	private static CompoundGlyphDataTrueType readCompoundGlyphTrueType(int start, int end, byte[] glyfBytes, int cid, int glyphIndex, int numberOfContours) {
		int glyfByteOffset = start;
		ArrayList components = new ArrayList(2);
		while (true) {
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println("     - reading component " + components.size());
			int flags = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+2));
			glyfByteOffset += 2;
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - flags are " + Integer.toString(flags, 2));
			int glyfIndex = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+2));
			glyfByteOffset += 2;
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - glyph index is " + glyfIndex);
			int arg1;
			int arg2;
			if ((flags & 1) == 0) {
				arg1 = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+1));
				glyfByteOffset += 1;
				if (arg1 > 127)
					arg1 -= 256;
				arg2 = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+1));
				glyfByteOffset += 1;
				if (arg2 > 127)
					arg2 -= 256;
			}
			else {
				arg1 = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+2));
				glyfByteOffset += 2;
				if (arg1 > 32768)
					arg1 -= 65536;
				arg2 = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+2));
				glyfByteOffset += 2;
				if (arg2 > 32768)
					arg2 -= 65536;
			}
			if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - arguments are " + arg1 + " and " + arg2);
			
			int[] opts;
			if ((flags & 128) != 0) {
				opts = new int[4];
				opts[0] = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+2));
				glyfByteOffset += 2;
				opts[1] = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+2));
				glyfByteOffset += 2;
				opts[2] = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+2));
				glyfByteOffset += 2;
				opts[3] = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+2));
				glyfByteOffset += 2;
				if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - options are " + Arrays.toString(opts));
			}
			else if ((flags & 64) != 0) {
				opts = new int[2];
				opts[0] = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+2));
				glyfByteOffset += 2;
				opts[1] = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+2));
				glyfByteOffset += 2;
				if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - options are " + Arrays.toString(opts));
			}
			else if ((flags & 8) != 0) {
				opts = new int[1];
				opts[0] = readUnsigned(glyfBytes, glyfByteOffset, (glyfByteOffset+2));
				glyfByteOffset += 2;
				if (DEBUG_TRUE_TYPE_LOADING) System.out.println("       - options are " + Arrays.toString(opts));
			}
			else opts = new int[0];
			
			components.add(new CompoundGlyphDataTrueType.Component(flags, glyfIndex, arg1, arg2, opts));
			
			if ((flags & 32) == 0)
				break;
		}
		if (DEBUG_TRUE_TYPE_LOADING) System.out.println("     - got to " + glyfByteOffset + ", end is " + end);
		if (DEBUG_TRUE_TYPE_LOADING && ((glyfByteOffset + 1) < end)) {
//			StringBuffer remainder = new StringBuffer();
			System.out.print("     - remainder is");
			while (glyfByteOffset < end) {
				int rByte = convertUnsigned(glyfBytes[glyfByteOffset++]);
//				remainder.append((char) rByte);
				System.out.print(" " + Integer.toString(rByte, 16));
			}
			System.out.println();
//			System.out.println(remainder);
		}
		
		return new CompoundGlyphDataTrueType(cid, glyphIndex, ((CompoundGlyphDataTrueType.Component[]) components.toArray(new CompoundGlyphDataTrueType.Component[components.size()])));
	}
	
	private static final boolean DEBUG_TRUE_TYPE_RENDERING = false;
	private static final int DEBUG_TRUE_TYPE_TARGET_CID = -1;
	
	private static void runFontTrueTypeOps(CompoundGlyphDataTrueType cGlyph, HashMap glyphsByCid, OpReceiver opr, boolean show) {
		ImageDisplayDialog idd = null;
		
		//	render components
		for (int c = 0; c < cGlyph.components.length; c++) {
			GlyphDataTrueType glyph = ((GlyphDataTrueType) glyphsByCid.get(new Integer(cGlyph.components[c].glyphIndex)));
			if (glyph == null)
				continue; // can happen with space ...
			
			//	TODO adjust transform if options are present (once we have an example)
			
			//	adjust position
			if ((cGlyph.components[c].flags & 2) == 0) {
				/* If [flag 1] not set, args are points:
			     * - 1st arg contains the index of matching point in compound being constructed
			     * - 2nd arg contains index of matching point in component
			     * ==> might have to collect points in this case */
				GlyphDataTrueType pGlyph = ((c == 0) ? null : ((GlyphDataTrueType) glyphsByCid.get(new Integer(cGlyph.components[c-1].glyphIndex))));
				//	TODO what if compound glyph has more than two components !?!
				int dx = 0;
				int dy = 0;
				if (pGlyph != null)
					for (int p = 0; p <= cGlyph.components[c].arg1; p++) {
						dx += ((SimpleGlyphDataTrueType) pGlyph).pointXs[p];
						dy += ((SimpleGlyphDataTrueType) pGlyph).pointYs[p];
					}
				for (int p = 0; p <= cGlyph.components[c].arg2; p++) {
					dx -= ((SimpleGlyphDataTrueType) glyph).pointXs[p];
					dy -= ((SimpleGlyphDataTrueType) glyph).pointYs[p];
				}
				opr.moveToAbs(dx, dy);
			}
			
			//	If [flag 1] set, the arguments are xy values;
			else opr.moveToAbs(cGlyph.components[c].arg1, cGlyph.components[c].arg2);
			
			//	do actual rendering
			if (glyph instanceof SimpleGlyphDataTrueType)
				runFontTrueTypeOps(((SimpleGlyphDataTrueType) glyph), true, opr, show);
			else runFontTrueTypeOps(((CompoundGlyphDataTrueType) glyph), glyphsByCid, opr, show);
			
			//	TODO reset transform if options are present
			
//			if ((opr instanceof OpGraphicsTrueType) && show) {
			if ((opr instanceof OpGraphics) && show) {
//				BufferedImage dImg = new BufferedImage(((OpGraphicsTrueType) opr).img.getWidth(), ((OpGraphicsTrueType) opr).img.getHeight(), BufferedImage.TYPE_INT_RGB);
				BufferedImage dImg = new BufferedImage(((OpGraphics) opr).img.getWidth(), ((OpGraphics) opr).img.getHeight(), BufferedImage.TYPE_INT_RGB);
				Graphics g = dImg.getGraphics();
//				g.drawImage(((OpGraphicsTrueType) opr).img, 0, 0, dImg.getWidth(), dImg.getHeight(), null);
				g.drawImage(((OpGraphics) opr).img, 0, 0, dImg.getWidth(), dImg.getHeight(), null);
				g.setColor(Color.RED);
				if (idd == null) {
					idd = new ImageDisplayDialog("Rendering Progress");
					idd.setSize((dImg.getWidth() + 200), (dImg.getHeight() + 100));
				}
				idd.addImage(dImg, ("Component " + c));
				idd.setLocationRelativeTo(null);
				idd.setVisible(true);
			}
		}
		
		//	only tracking, we're done here
		if (opr instanceof OpTracker)
			return;
		
		//	fill outline
		fillGlyphOutline(((OpGraphics) opr).img, ((OpGraphics) opr).scale, show);
		
		//	scale down image
		int maxHeight = 100;
		((OpGraphics) opr).setImage(scaleImage(((OpGraphics) opr).img, maxHeight));
	}
	
	private static void runFontTrueTypeOps(SimpleGlyphDataTrueType sGlyph, boolean isComponent, OpReceiver opr, boolean show) {
		ImageDisplayDialog idd = null;
		
		//	run points
		int pointIndex = 0;
		String op;
		String opShow;
		for (int c = 0; c < sGlyph.contourEnds.length; c++) {
			boolean lastWasInterpolated = false;
			boolean lastWasContourStart = true;
			int firstPointAbsX = Integer.MAX_VALUE;
			int firstPointAbsY = Integer.MAX_VALUE;
			opr.moveTo(sGlyph.pointXs[pointIndex], sGlyph.pointYs[pointIndex]);
			if ((sGlyph.pointFlags[pointIndex] & 1) == 0) {
				firstPointAbsX = opr.x;
				firstPointAbsY = opr.y;
			}
			pointIndex += 1;
			if (sGlyph.contourEnds[c] == (pointIndex-1))
				continue; // single-point contour, move on to next one without painting anything
			while (pointIndex < sGlyph.pointFlags.length) {
				if ((sGlyph.pointFlags[pointIndex] & 1) == 0) {
					int dx1;
					int dy1;
					if (((sGlyph.pointFlags[pointIndex-1] & 1) == 0) && lastWasContourStart) {
						opr.moveTo((sGlyph.pointXs[pointIndex] / 2), (sGlyph.pointYs[pointIndex] / 2));
						dx1 = (sGlyph.pointXs[pointIndex] / 2);
						dy1 = (sGlyph.pointYs[pointIndex] / 2);
					}
					else {
						dx1 = (sGlyph.pointXs[pointIndex] / (lastWasInterpolated ? 2 : 1));
						dy1 = (sGlyph.pointYs[pointIndex] / (lastWasInterpolated ? 2 : 1));
					}
					pointIndex += 1;
					if (sGlyph.contourEnds[c] == (pointIndex-1)) {
						opr.closePath(dx1, dy1, true);
						op = "closePath0";
						opShow = (op + "(" + dx1 + "/" + dy1 + ")");
						if (!show)
							break;
					}
					else if ((sGlyph.pointFlags[pointIndex] & 1) == 0) {
						opr.curveTo(dx1, dy1, (sGlyph.pointXs[pointIndex] / 2), (sGlyph.pointYs[pointIndex] / 2));
						op = "curveTo0";
						opShow = (op + "(" + dx1 + "/" + dy1 + "," + (sGlyph.pointXs[pointIndex] / 2) + "/" + (sGlyph.pointYs[pointIndex] / 2) + ")");
						lastWasInterpolated = true;
					}
					else {
						opr.curveTo(dx1, dy1, sGlyph.pointXs[pointIndex], sGlyph.pointYs[pointIndex]);
						op = "curveTo1";
						opShow = (op + "(" + dx1 + "/" + dy1 + "," + sGlyph.pointXs[pointIndex] + "/" + sGlyph.pointYs[pointIndex] + ")");
						pointIndex += 1;
						lastWasInterpolated = false;
					}
				}
				else {
					if (((sGlyph.pointFlags[pointIndex-1] & 1) == 0) && lastWasContourStart) {
						opr.moveTo(sGlyph.pointXs[pointIndex], sGlyph.pointYs[pointIndex]);
						op = "moveTo";
						opShow = (op + "(" + sGlyph.pointXs[pointIndex] + "/" + sGlyph.pointYs[pointIndex] + ")");
						pointIndex += 1;
						lastWasInterpolated = false;
					}
					else {
						opr.lineTo((sGlyph.pointXs[pointIndex] / (lastWasInterpolated ? 2 : 1)), (sGlyph.pointYs[pointIndex] / (lastWasInterpolated ? 2 : 1)));
						op = "lineTo";
						opShow = (op + "(" + (sGlyph.pointXs[pointIndex] / (lastWasInterpolated ? 2 : 1)) + "/" + (sGlyph.pointYs[pointIndex] / (lastWasInterpolated ? 2 : 1)) + ")");
						pointIndex += 1;
						lastWasInterpolated = false;
					}				}
				if (sGlyph.contourEnds[c] == (pointIndex-1)) {
					if ((firstPointAbsX < Integer.MAX_VALUE) && (firstPointAbsY < Integer.MAX_VALUE)) {
						int dx1 = (firstPointAbsX - opr.x);
						int dy1 = (firstPointAbsY - opr.y);
						opr.closePath(dx1, dy1, false);
					}
					else opr.closePath();
					op = "closePath1";
					opShow = op;
					if (!show)
						break;
				}
				
				//	show step by step
				if (show) {
					BufferedImage img = ((OpGraphics) opr).img;
					BufferedImage dImg = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
					Graphics g = dImg.getGraphics();
					g.drawImage(img, 0, 0, dImg.getWidth(), dImg.getHeight(), null);
					g.setColor(Color.RED);
					if (idd == null) {
						idd = new ImageDisplayDialog("Rendering Progress");
						idd.setSize((dImg.getWidth() + 200), (dImg.getHeight() + 100));
					}
					idd.addImage(dImg, ("Drawing (" + opShow + ")"));
					idd.setLocationRelativeTo(null);
					idd.setVisible(true);
				}
				
				lastWasContourStart = false;
				if ("closePath0".equals(op) || "closePath1".equals(op))
					break;
			}
		}
		
		if (idd != null) {
			idd.setLocationRelativeTo(null);
			idd.setVisible(true);
		}
		
		//	only tracking, we're done here
		if ((opr instanceof OpTracker) || isComponent)
			return;
		
		//	fill outline
		fillGlyphOutline(((OpGraphics) opr).img, ((OpGraphics) opr).scale, show);
		
		//	scale down image
		int maxHeight = 100;
		((OpGraphics) opr).setImage(scaleImage(((OpGraphics) opr).img, maxHeight));
	}
	
	private static class GlyphDataTrueType {
		final int cid;
		final int gid;
		char ch;
		GlyphDataTrueType(int cid, int gid) {
			this.cid = cid;
			this.gid = gid;
		}
	}
	
	private static class SimpleGlyphDataTrueType extends GlyphDataTrueType {
		int[] pointFlags;
		int[] pointXs;
		int[] pointYs;
		int[] contourEnds;
		SimpleGlyphDataTrueType(int cid, int gid, int[] pointFlags, int[] pointXs, int[] pointYs, int[] contourEnds) {
			super(cid, gid);
			this.pointFlags = pointFlags;
			this.pointXs = pointXs;
			this.pointYs = pointYs;
			this.contourEnds = contourEnds;
		}
	}
	
	private static class CompoundGlyphDataTrueType extends GlyphDataTrueType {
		Component[] components;
		CompoundGlyphDataTrueType(int cid, int gid, Component[] components) {
			super(cid, gid);
			this.components = components;
		}
		static class Component {
			int flags;
			int glyphIndex;
			int arg1;
			int arg2;
			int[] opts;
			Component(int flags, int glyphIndex, int arg1, int arg2, int[] opts) {
				this.flags = flags;
				this.glyphIndex = glyphIndex;
				this.arg1 = arg1;
				this.arg2 = arg2;
				this.opts = opts;
			}
		}
	}
	
	private static final boolean DEBUG_TYPE3_LOADING = false;
	private static final boolean DEBUG_TYPE3_RENDERING = false;
	private static final String DEBUG_TYPE3_TARGET_NAME = null;
	
	static void readFontType3(PdfFont pFont, CharDecoder pCharDecoder, HashMap diffEncodings, HashMap diffEncodingNames, Map charProcs, float fmScaleX, float fmScaleY, FontDecoderCharset charSet, Library library, Map objects, ProgressMonitor pm) {
		pm.setInfo(" - decoding characters from char programs");
		if (DEBUG_TYPE3_LOADING) System.out.println("Got char procs: " + charProcs);
		
		HashMap cache = ((diffEncodingNames.size() < 10) ? null : new HashMap());
		if (DEBUG_TYPE3_LOADING) {
			System.out.println("Differences are: " + diffEncodings);
			System.out.println("Difference names are: " + diffEncodingNames);
		}
		
		//	set up char codes and names in array (names map to glyphs, not codes)
		Integer[] charCodes = new Integer[diffEncodingNames.size()];
		int cci = 0;
		for (Iterator eit = diffEncodingNames.keySet().iterator(); eit.hasNext();)
			charCodes[cci++] = ((Integer) eit.next());
		Arrays.sort(charCodes);
		String[] charNames = new String[charCodes.length];
		Object[] glyphKeys = new Object[charCodes.length];
		GlyphDataType3[] glyphData = new GlyphDataType3[charCodes.length];
		HashMap charNamesToGlyphData = new HashMap();
		for (int c = 0; c < charCodes.length; c++) {
			String charName = diffEncodingNames.get(charCodes[c]).toString();
			charNames[c] = charName;
			glyphKeys[c] = charName;
			Object charProcObj = PdfParser.dereference(charProcs.get(charName), objects);
			if (charProcObj instanceof PStream) {
				pm.setInfo("   - decoding char " + charName);
				if (DEBUG_TYPE3_LOADING) System.out.println("  Got char proc for '" + charName + "': " + ((PStream) charProcObj).params);
				glyphData[c] = ((GlyphDataType3) charNamesToGlyphData.get(charNames[c]));
				if (glyphData[c] == null) try {
					glyphData[c] = getGlyphDataType3(pFont, ((PStream) charProcObj), objects);
					if (glyphData[c] != null)
						charNamesToGlyphData.put(charNames[c], glyphData[c]);
				}
				catch(IOException ioe) {
					System.out.println("Error loading font '" + pFont.name + "': " + ioe.getMessage());
					ioe.printStackTrace(System.out);
				}
			}
			else if (DEBUG_TYPE3_LOADING) System.out.println(" --> strange char proc for '" + charName + "': " + charProcObj);
		}
		
		//	measure characters
		pm.setInfo("   - measuring characters");
		int maxDescent = 0;
		int maxCapHeight = 0;
		OpTracker[] otrs = new OpTracker[charCodes.length];
		BufferedImage allGlyphs = new BufferedImage(1500, 1500, BufferedImage.TYPE_BYTE_BINARY);
		Graphics2D agGr = allGlyphs.createGraphics();
		agGr.setColor(Color.WHITE);
		agGr.fillRect(0, 0, allGlyphs.getWidth(), allGlyphs.getHeight());
		agGr.setColor(Color.BLACK);
		agGr.translate(100, 900);
		agGr.scale((0.3 * fmScaleX), (-0.3 * fmScaleY)); // 300 x 300 for 1000 x 1000 PDF font space (font size 300)
		agGr.setStroke(new BasicStroke(5 * Math.max(Math.abs(1 / fmScaleX), Math.abs(1 / fmScaleY))));
		HashMap charNamesToOpTrackers = new HashMap();
		for (int c = 0; c < charCodes.length; c++) {
			CharImage chi = ((CharImage) pCharDecoder.plainCharCodesToImages.get(charNames[c]));
			if (chi != null) {
				pm.setInfo("     - skipping previously rendered char " + c + " with name " + charNames[c] + " (" + pFont.getUnicode(charCodes[c].intValue()) + ")");
				continue;
			}
			if (glyphData[c] == null) {
				pm.setInfo("     - procedure unavailable for char " + c + " with name " + charNames[c] + " (" + pFont.getUnicode(charCodes[c].intValue()) + ")");
				continue;
			}
			otrs[c] = ((OpTracker) charNamesToOpTrackers.get(charNames[c]));
			if (otrs[c] != null) {
				pm.setInfo("     - skipping previously measured char " + c + " with name " + charNames[c] + " (" + pFont.getUnicode(charCodes[c].intValue()) + ")");
				continue;
			}
			
			//	render glyph
			otrs[c] = new OpTracker(charNames[c].equals(DEBUG_TYPE3_TARGET_NAME));
			charNamesToOpTrackers.put(charNames[c], otrs[c]);
			pm.setInfo("     - measuring char " + c + " with name " + charNames[c] + " (" + pFont.getUnicode(charCodes[c].intValue()) + ")");
//			if (DEBUG_TYPE3_LOADING) System.out.println(" ==> font glyph key maps to " + pFont.getCharCodeByGlyphKey(new Integer(glyph.cid)));
			int minX = Math.round(glyphData[c].lowerLeftX * fmScaleX);
			int maxX = Math.round(glyphData[c].upperRightX * fmScaleX);
			int minY = Math.round(glyphData[c].upperRightY * -fmScaleY); // bounding box corners come in PDF coordinates, with Y increasing upwards
			int maxY = Math.round(glyphData[c].lowerLeftY * -fmScaleY); // bounding box corners come in PDF coordinates, with Y increasing upwards
			if (glyphData[c].imageData == null)
				glyphData[c].writePathTo(otrs[c], fmScaleX, fmScaleY, charNames[c].equals(DEBUG_TYPE3_TARGET_NAME));
			else glyphData[c].drawImageTo(otrs[c], fmScaleX, fmScaleY, charNames[c].equals(DEBUG_TYPE3_TARGET_NAME), null, null);
			if (DEBUG_TYPE3_LOADING) {
				System.out.println("     --> " + minX + " < X < " + maxX);
				System.out.println("     --> " + minY + " < Y < " + maxY);
				System.out.println("     --> path is " + otrs[c].ops);
			}
			maxDescent = Math.min(maxDescent, minY);
			maxCapHeight = Math.max(maxCapHeight, maxY);
			if (glyphData[c].imageData == null)
				glyphData[c].drawPath(agGr, false);
			else glyphData[c].drawImage(agGr);
		}
		agGr.dispose();
		if (DEBUG_TYPE3_LOADING) System.out.println("Max descent is " + maxDescent + ", max cap height is " + maxCapHeight);
		
		//	check rendered glyph path dimensions
		//	TODO check if this is even sensible here !!!
		int white = Color.WHITE.getRGB(); // TODO make this a constant !!!
		int minGlyphX = Integer.MAX_VALUE;
		int maxGlyphX = 0;
		int minGlyphY = Integer.MAX_VALUE;
		int maxGlyphY = 0;
		for (int x = 0; x < allGlyphs.getWidth(); x++) {
			for (int y = 0; y < allGlyphs.getHeight(); y++) {
				if (allGlyphs.getRGB(x, y) == white)
					continue;
				minGlyphX = Math.min(minGlyphX, x);
				maxGlyphX = Math.max(maxGlyphX, x);
				minGlyphY = Math.min(minGlyphY, y);
				maxGlyphY = Math.max(maxGlyphY, y);
			}
		}
		
		//	scale back to font space, inverting back Y coordinate for comparison with font indicated measurements
		int fMinGlyphX = (((minGlyphX - 100) * 10) / 3);
		int fMaxGlyphX = (((maxGlyphX - 100) * 10) / 3);
		int fMinGlyphY = (((maxGlyphY - 900) * 10) / -3);
		int fMaxGlyphY = (((minGlyphY - 900) * 10) / -3);
		if (DEBUG_TYPE3_LOADING) {
			System.out.println("Glyph extent is " + minGlyphX + "-" + maxGlyphX + " by " + minGlyphY + "-" + maxGlyphY);
			System.out.println(" ==> " + fMinGlyphX + "-" + fMaxGlyphX + " by " + fMinGlyphY + "-" + fMaxGlyphY);
		}
		
		//	compare measured glyph extents to font indicated ascent and descent
		int bbTop = ((pFont.bBox == null) ? 0 : pFont.bBox.top);
		float ascentRatio = (((float) fMaxGlyphY) / Math.max(Math.round(pFont.ascent * 1000), bbTop));
		int bbBottom = ((pFont.bBox == null) ? 0 : pFont.bBox.bottom);
		float descentRatio = (((float) fMinGlyphY) / Math.min(Math.round(pFont.descent * 1000), bbBottom));
		int bbHeight = ((pFont.bBox == null) ? 0 : -pFont.bBox.getHeight());
		float heightRatio = (((float) (fMaxGlyphY - fMinGlyphY)) / Math.max(Math.round((pFont.ascent - pFont.descent) * 1000), bbHeight));
		if (DEBUG_TYPE3_LOADING) {
			System.out.println("Font indicated height is " + Math.round(pFont.descent * 1000) + "-" + Math.round(pFont.ascent * 1000) + ", bounding box is " + pFont.bBox);
			System.out.println(" ==> height ratio is " + heightRatio + ", ascent ratio is " + ascentRatio + ", descent ratio is " + descentRatio);
		}
		
		//	scale down glyphs if significantly taller than indicated ascent, and adjust descent as well, as font sizes go up for compensation
		BufferedImage sAllGlyphs = null;
		if ((1.5 < ascentRatio) && (charSet == VECTORIZED_OCR_DECODING)) {
			pFont.scaleDownFactor = Math.round(ascentRatio);
			System.out.println(" ==> scaling down glyphs by factor " + pFont.scaleDownFactor);
			pFont.descent = Math.max(pFont.descent, (((float) fMinGlyphY) / (pFont.scaleDownFactor * 1000))); // descent is negative number ...
			System.out.println(" ==> descent adjusted to " + Math.round(pFont.descent * 1000));
			AffineTransform at = AffineTransform.getScaleInstance((1.0 / pFont.scaleDownFactor), (1.0 / pFont.scaleDownFactor));
			sAllGlyphs = new BufferedImage(1500, 1500, BufferedImage.TYPE_BYTE_BINARY);
			//	TODO implement this for command rendered paths as well !!!
			Graphics2D sagGr = sAllGlyphs.createGraphics();
			sagGr.setColor(Color.WHITE);
			sagGr.fillRect(0, 0, sAllGlyphs.getWidth(), sAllGlyphs.getHeight());
			sagGr.setColor(Color.BLACK);
			sagGr.translate(100, 900);
			agGr.scale((0.3 * fmScaleX), (-0.3 * fmScaleY)); // 300 x 300 for 1000 x 1000 PDF font space (font size 300)
			sagGr.setStroke(new BasicStroke(5));
			for (int c = 0; c < otrs.length; c++) {
				if (otrs[c] == null)
					continue;
				otrs[c].path.transform(at);
				sagGr.draw(otrs[c].path);
			}
			sagGr.dispose();
		}
		
		//	make sure to accommodate ascent and descent (keeps glyphs of dash-only fonts in vertical position, and in proportion)
//		maxCapHeight = Math.max(maxCapHeight, Math.round(pFont.ascent * 1000));
//		maxDescent = Math.min(maxDescent, Math.round(pFont.descent * 1000));
		maxCapHeight = Math.max(maxCapHeight, Math.max(Math.round(pFont.ascent * 1000), bbTop));
		maxDescent = Math.min(maxDescent, Math.min(Math.min(Math.round(pFont.descent * 1000), bbBottom), Math.round((maxCapHeight * pFont.descent) / pFont.ascent)));
		
		//	set up rendering
		int maxRenderSize = 300;
		float scale = 1.0f;
		if ((maxCapHeight - maxDescent) > maxRenderSize)
			scale = (((float) maxRenderSize) / (maxCapHeight - maxDescent));
		if (DEBUG_TYPE3_LOADING) System.out.println(" ==> scaledown factor is " + scale);
		
		//	tray up chars
		char[] chars = new char[charCodes.length];
		Arrays.fill(chars, ((char) 0));
		
		//	generate images and match against named char
		pm.setInfo("   - decoding characters");
		CharImage[] charImages = new CharImage[charCodes.length];
		ImageDisplayDialog fidd = ((DEBUG_TYPE3_LOADING) ? new ImageDisplayDialog("Font " + pFont.name) : null);
		for (int c = 0; c < charCodes.length; c++) {
			String ucCh = pFont.getUnicode(charCodes[c].intValue());
			if ((ucCh != null) && (ucCh.length() > 0))
				chars[c] = ucCh.charAt(0);
			
			CharImage chi = ((CharImage) pCharDecoder.plainCharCodesToImages.get(charNames[c]));
			if (chi != null) {
				pm.setInfo("     - reusing previously rendered char " + c + " with name " + charNames[c] + " (" + ucCh + ")");
				charImages[c] = chi;
				pFont.setCharImage(charCodes[c].intValue(), charNames[c], chi.img, chi.path);
				continue;
			}
			if (!DEBUG_TYPE3_LOADING && !pCharDecoder.usesChar(charCodes[c], charNames[c])) {
				pm.setInfo("     - ignoring unused char " + c + " with name " + charNames[c] + " (" + ucCh + ")");
				continue;
			}
			pm.setInfo("     - decoding char " + c + " with name " + charNames[c] + " (" + ucCh + ")");
			if (DEBUG_TYPE3_LOADING) System.out.println("Decoding char " + c + ", name is " + charNames[c] + " (" + ucCh + ")");
			if (DEBUG_TYPE3_LOADING) System.out.println(" - stroke count is " + otrs[c].segCount);
			
			//	check if char rendering possible
			if ((otrs[c].maxX <= otrs[c].minX) || (otrs[c].maxY <= otrs[c].minY)) {
				if (DEBUG_TYPE3_LOADING) System.out.println(" ==> unsensible char dimensions: " + otrs[c].minX + "-" + otrs[c].maxX + "x" + otrs[c].minY + "-" + otrs[c].maxY);
				continue;
			}
			
			//	render char
			int mx = 8;
			int my = ((mx * (maxCapHeight - maxDescent)) / (otrs[c].maxX - otrs[c].minX));
			OpGraphics ogr = new OpGraphics(
					otrs[c].minX,
					maxDescent,
					(maxCapHeight - maxDescent + (my / 2)),
					scale,
					new BufferedImage(
							Math.round((scale * (otrs[c].maxX - otrs[c].minX + mx)) + (2 * glyphOutlineFillSafetyEdge)),
							Math.round((scale * (maxCapHeight - maxDescent + my)) + (2 * glyphOutlineFillSafetyEdge)),
							BufferedImage.TYPE_INT_RGB)
					);
			if (glyphData[c].imageData == null)
				glyphData[c].writePathTo(ogr, fmScaleX, fmScaleY, charNames[c].equals(DEBUG_TYPE3_TARGET_NAME));
			else glyphData[c].drawImageTo(ogr, fmScaleX, fmScaleY, charNames[c].equals(DEBUG_TYPE3_TARGET_NAME), library, objects);
			if (DEBUG_TYPE3_LOADING) System.out.println(" - image rendered, size is " + ogr.img.getWidth() + "x" + ogr.img.getHeight() + ", OpGraphics height is " + ogr.height);
			charImages[c] = new CharImage(ogr.img, Math.round(((float) (maxCapHeight * ogr.img.getHeight())) / (maxCapHeight - maxDescent)), otrs[c].path, (otrs[c].maxX - otrs[c].minX));
			if (DEBUG_TYPE3_LOADING) System.out.println(" - char image wrapped, baseline is " + charImages[c].baseline);
			if (fidd != null)
				fidd.addImage(ogr.img, (charCodes[c] + " : " + ucCh + " (" + charNames[c] + ")"));
			
			//	store char image
			if (glyphData[c].imageData == null)
				pFont.setCharImage(charCodes[c], charNames[c], ogr.img, otrs[c].path);
			else pFont.setCharImage(charCodes[c], charNames[c], ogr.img, null);
			pCharDecoder.plainCharCodesToImages.put(charNames[c], charImages[c]);
		}
		if ((fidd != null) && (fidd.getImageCount() != 0)) {
			CountingSet charSizes = pFont.getCharSizes();
			if (charSizes != null) {
				System.out.println("   - font used in sizes " + charSizes);
				int kCharHeight = (maxCapHeight - maxDescent);
				CountingSet charHeights = new CountingSet(new TreeMap());
				float charHeightSum = 0;
				for (Iterator csit = charSizes.iterator(); csit.hasNext();) {
					Float cs = ((Float) csit.next());
					float charHeight = ((kCharHeight * cs.floatValue()) / 1000);
					charHeights.add(new Float(charHeight), charSizes.getCount(cs));
					charHeightSum += (charHeight * charSizes.getCount(cs));
				}
				System.out.println("   - char heights (at 72 DPI) " + charHeights);
				System.out.println("   - average char height (at 72 DPI) " + (charHeightSum / charSizes.size()));
			}
			
			agGr = allGlyphs.createGraphics();
			agGr.setColor(Color.BLACK);
			agGr.drawLine(0, 900, 1000, 900);
			agGr.drawLine(0, 600, 1000, 600);
			agGr.drawLine(100, 0, 100, 1500);
			agGr.dispose();
			fidd.addImage(allGlyphs, "All Paths");
			
			if (sAllGlyphs != null) {
				Graphics2D sagGr = sAllGlyphs.createGraphics();
				sagGr.setColor(Color.BLACK);
				sagGr.drawLine(0, 900, 1000, 900);
				sagGr.drawLine(0, 600, 1000, 600);
				sagGr.drawLine(100, 0, 100, 1500);
				sagGr.dispose();
				fidd.addImage(sAllGlyphs, "All Paths (scaled)");
			}
			
			fidd.setLocationRelativeTo(null);
			fidd.setSize(600, 400);
			fidd.setVisible(true);
		}
		
		//	decode rendered chars
		decodeChars(pFont, pCharDecoder, chars, charCodes, charNames, glyphKeys, charImages/*, rawCharCodes*/, -1, maxDescent, charSet, pm, DEBUG_TYPE3_LOADING);
	}
	
	private static class GlyphDataType3 {
		final byte[] imageData;
		final GlyphRenderingCommandType3[] renderData;
		final int width;
		final int height;
		final int lowerLeftX;
		final int lowerLeftY;
		final int upperRightX;
		final int upperRightY;
		GlyphDataType3(GlyphRenderingCommandType3[] renderData, int width, int height) {
			this(null, renderData, width, height);
		}
		GlyphDataType3(byte[] imageData, int width, int height) {
			this(imageData, null, width, height);
		}
		private GlyphDataType3(byte[] imageData, GlyphRenderingCommandType3[] renderData, int width, int height) {
			this.imageData = imageData;
			this.renderData = renderData;
			this.width = width;
			this.height = height;
			this.lowerLeftX = 0;
			this.lowerLeftY = 0;
			this.upperRightX = this.width;
			this.upperRightY = this.height;
		}
		GlyphDataType3(GlyphRenderingCommandType3[] renderData, int width, int height, int lowerLeftX, int lowerLeftY, int upperRightX, int upperRightY) {
			this(null, renderData, width, height, lowerLeftX, lowerLeftY, upperRightX, upperRightY);
		}
		GlyphDataType3(byte[] imageData, int width, int height, int lowerLeftX, int lowerLeftY, int upperRightX, int upperRightY) {
			this(imageData, null, width, height, lowerLeftX, lowerLeftY, upperRightX, upperRightY);
		}
		private GlyphDataType3(byte[] imageData, GlyphRenderingCommandType3[] renderData, int width, int height, int lowerLeftX, int lowerLeftY, int upperRightX, int upperRightY) {
			this.imageData = imageData;
			this.renderData = renderData;
			this.width = width;
			this.height = height;
			this.lowerLeftX = lowerLeftX;
			this.lowerLeftY = lowerLeftY;
			this.upperRightX = upperRightX;
			this.upperRightY = upperRightY;
		}
		void drawPath(Graphics2D gr, boolean fill) {
			Path2D path = new Path2D.Float();
			for (int c = 0; c < this.renderData.length; c++) {
				if ("m".equals(this.renderData[c].command))
					path.moveTo(this.renderData[c].params[0], this.renderData[c].params[1]);
				else if ("l".equals(this.renderData[c].command))
					path.lineTo(this.renderData[c].params[0], this.renderData[c].params[1]);
				else if ("c".equals(this.renderData[c].command))
					path.curveTo(this.renderData[c].params[0], this.renderData[c].params[1], this.renderData[c].params[2], this.renderData[c].params[3], this.renderData[c].params[4], this.renderData[c].params[5]);
				else if ("v".equals(this.renderData[c].command)) {
					Point2D xy1 = path.getCurrentPoint();
					path.curveTo(xy1.getX(), xy1.getY(), this.renderData[c].params[0], this.renderData[c].params[1], this.renderData[c].params[2], this.renderData[c].params[3]);
				}
				else if ("y".equals(this.renderData[c].command))
					path.curveTo(this.renderData[c].params[0], this.renderData[c].params[1], this.renderData[c].params[2], this.renderData[c].params[3], this.renderData[c].params[2], this.renderData[c].params[3]);
				else if ("re".equals(this.renderData[c].command))
					path.append(new Rectangle2D.Float(this.renderData[c].params[0], this.renderData[c].params[1], this.renderData[c].params[2], this.renderData[c].params[3]), false);
				else if ("h".equals(this.renderData[c].command))
					path.closePath();
				else if ("f".equals(this.renderData[c].command)) {
					if (fill)
						gr.fill(path);
					else gr.draw(path);
				}
			}
		}
		void drawImage(Graphics2D gr) {
			gr.drawRect(
					this.lowerLeftX,
					this.lowerLeftY,
					(this.upperRightX - this.lowerLeftX),
					(this.upperRightY - this.lowerLeftY)
				);
		}
		void writePathTo(OpReceiver or, float scaleX, float scaleY, boolean show) {
			ImageDisplayDialog idd = null;
			for (int c = 0; c < this.renderData.length; c++) {
				int[] params = new int[this.renderData[c].params.length];
				for (int p = 0; p < params.length; p++) {
					if ((p & 0x00000001) == 0)
						params[p] = Math.round(this.renderData[c].params[p] * scaleX);
					else params[p] = Math.round(this.renderData[c].params[p] * scaleY);
				}
				if (DEBUG_TYPE3_RENDERING)
					System.out.println("     - " + this.renderData[c].command + " " + Arrays.toString(params));
				if ("m".equals(this.renderData[c].command))
					or.moveToAbs(params[0], params[1]);
				else if ("l".equals(this.renderData[c].command))
					or.lineToAbs(params[0], params[1]);
				else if ("c".equals(this.renderData[c].command))
					or.curveToAbs(params[0], params[1], params[2], params[3], params[4], params[5]);
				else if ("v".equals(this.renderData[c].command))
					or.curveToAbs(or.x, or.y, params[0], params[1], params[2], params[3]);
				else if ("y".equals(this.renderData[c].command))
					or.curveToAbs(params[0], params[1], params[2], params[3], params[2], params[3]);
				else if ("re".equals(this.renderData[c].command)) {
					or.moveToAbs(params[0], params[1]);
					or.lineToAbs((params[0] + params[2]), params[1]);
					or.lineToAbs((params[0] + params[2]), (params[1] + params[3]));
					or.lineToAbs(params[0], (params[1] + params[3]));
					or.closePath();
				}
				else if ("h".equals(this.renderData[c].command)) {
					or.closePath();
				}
				else if ("f".equals(this.renderData[c].command)) {
					if (or instanceof OpGraphics)
						fillGlyphOutline(((OpGraphics) or).img, ((OpGraphics) or).scale, show);
					//	TODO better simply let Java2D fill path, rendering might not be as clean (e.g. with intersecting areas)
//					else gr.draw(path);
				}
				
				//	show step by step
				if (show && (or instanceof OpGraphics)) {
					BufferedImage img = ((OpGraphics) or).img;
					BufferedImage dImg = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
					Graphics g = dImg.getGraphics();
					g.drawImage(img, 0, 0, dImg.getWidth(), dImg.getHeight(), null);
					g.setColor(Color.RED);
					if (idd == null) {
						idd = new ImageDisplayDialog("Rendering Progress");
						idd.setSize((dImg.getWidth() + 200), (dImg.getHeight() + 100));
					}
					idd.addImage(dImg, ("Drawing (" + this.renderData[c].command + " " + Arrays.toString(params) + ")"));
					idd.setLocationRelativeTo(null);
					idd.setVisible(true);
				}
			}
			
			if (idd != null) {
				idd.setLocationRelativeTo(null);
				idd.setVisible(true);
			}
		}
		void drawImageTo(OpReceiver or, float scaleX, float scaleY, boolean show, Library library, Map objects) {
			if (or instanceof OpTracker) {
				Map ips = this.getImageParams();
				if (ips == null)
					return;
				int llx = Math.round(this.lowerLeftX * scaleX);
				int lly = Math.round(this.lowerLeftY * scaleY);
				int urx = Math.round(this.upperRightX * scaleX);
				int ury = Math.round(this.upperRightY * scaleY);
				or.moveToAbs(llx, lly);
				or.lineToAbs(llx, ury);
				or.lineToAbs(urx, ury);
				or.lineToAbs(urx, lly);
				or.closePath();
			}
			else if (or instanceof OpGraphics) {
				BufferedImage ibi = this.getImage(library, objects);
				if (ibi == null)
					return;
				int llx = (Math.round(this.lowerLeftX * scaleX * ((OpGraphics) or).scale) + glyphOutlineFillSafetyEdge);
				int lly = (Math.round(this.lowerLeftY * scaleY * ((OpGraphics) or).scale) + glyphOutlineFillSafetyEdge);
				int urx = (Math.round(this.upperRightX * scaleX * ((OpGraphics) or).scale) + glyphOutlineFillSafetyEdge);
				int ury = (Math.round(this.upperRightY * scaleY * ((OpGraphics) or).scale) + glyphOutlineFillSafetyEdge);
				Graphics2D gr = ((OpGraphics) or).gr;
				AffineTransform at = gr.getTransform();
				gr.scale(1, -1); // need to render in Java direction, not PDF direction ...
				gr.translate(0, (ury - lly)); // ... but still stay on baseline
				gr.drawImage(ibi, llx, lly, (urx - llx), (ury - lly), null);
				gr.setTransform(at);
			}
		}
		private Map imageParams = null;
		private byte[] imageBytes = null;
		private BufferedImage image = null; // decoded on demand
		private BufferedImage getImage(Library library, Map objects) {
			if (this.imageData == null)
				return null;
			if (this.image == null) try {
				this.getImageParams();
				
				//	check if we're decoding an image mask
				Object isImageMaskObj = this.imageParams.get("ImageMask");
				boolean isImageMask = ((isImageMaskObj != null) && Boolean.parseBoolean(isImageMaskObj.toString()));
				
				//	get decoding filter (need it below)
				Object filterObj = PdfParser.getObject(this.imageParams, "Filter", objects);
//				if (filterObj instanceof List) {
//					List filters = ((List) filterObj);
//					for (int f = 0; f < filters.size(); f++) {
//						if ((f+1) == filters.size())
//							filterObj = filters.get(f);
//						else if (filters.get(f) != null)
//							data = this.decodeImageData(data, params, filters.get(f).toString(), pdfPage.getLibrary(), objects);
//					}
//				}
				if (filterObj instanceof Reference)
					filterObj = objects.get(((Reference) filterObj).getObjectNumber() + " " + ((Reference) filterObj).getGenerationNumber());
				String filter = ((filterObj == null) ? null : filterObj.toString());
				if (DEBUG_TYPE3_LOADING)
					System.out.println("Glyph bitmap decode filter is " + filter);
				
				//	get primary color space
				Object colorSpaceObj = PdfParser.getObject(this.imageParams, "ColorSpace", objects);
				if (DEBUG_TYPE3_LOADING)
					System.out.println("Glyph bitmap color space is " + colorSpaceObj);
				
				//	decode image
//				this.image = PdfUtils.decodeOther(this.imageBytes, this.imageParams, filter, colorSpaceObj, library, resources, objects, false, false, isImageMask, false);
				this.image = PdfUtils.decodeBitmap(this.imageBytes, this.imageParams, filter, colorSpaceObj, library, objects, false, isImageMask, false);
				if (DEBUG_TYPE3_LOADING)
					System.out.println("Glyph bitmap image is " + this.image);
			}
			catch (IOException ioe) {
				System.out.println("Error decoding glyph bitmap image: " + ioe.getMessage());
				ioe.printStackTrace(System.out);
			}
			return this.image;
		}
		private Map getImageParams() {
			if (this.imageData == null)
				return null;
			if (this.imageParams == null) try {
				PdfByteInputStream iImageData = new PdfByteInputStream(this.imageData);
				this.imageParams = PdfParser.cropInlineImageParams(iImageData);
				if (DEBUG_TYPE3_LOADING)
					System.out.println("Glyph bitmap params are " + this.imageParams);
				ByteArrayOutputStream imageBytes = new ByteArrayOutputStream();
				byte[] buffer = new byte[1024];
				for (int r; (r = iImageData.read(buffer, 0, buffer.length)) != -1;)
					imageBytes.write(buffer, 0, r);
				this.imageBytes = imageBytes.toByteArray();
				if (DEBUG_TYPE3_LOADING)
					System.out.println("Glyph bitmap data is " + Arrays.toString(this.imageBytes));
			}
			catch (IOException ioe) {
				System.out.println("Error parsing inline image data: " + ioe.getMessage());
				ioe.printStackTrace(System.out);
			}
			return this.imageParams;
		}
	}
	
	private static class GlyphRenderingCommandType3 {
		final String command;
		final int[] params;
		GlyphRenderingCommandType3(String command, int[] params) {
			this.command = command;
			this.params = params;
		}
	}
	
	private static GlyphDataType3 getGlyphDataType3(PdfFont pFont, PStream charProc, Map objects) throws IOException {
		//	TODO_ideally: PdfParser.getPageContent(entries, content, rotate, resources, objects, tokenizer, fontCharSet, wordsAreOcr, pm);
		byte[] charProcBytes;
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			PdfParser.decode(charProc.params.get("Filter"), charProc.bytes, charProc.params, baos, objects);
			charProcBytes = baos.toByteArray();
		}
		catch (IOException ioe) {
			System.out.println("Could not decode char prog:");
			ioe.printStackTrace(System.out);
			return null;
		}
		if (DEBUG_TYPE3_LOADING) {
			System.out.write(charProcBytes);
			System.out.println();
		}
		PdfByteInputStream pis = new PdfByteInputStream(charProcBytes);
		LinkedList stack = new LinkedList();
		PInlineImage charImageData = null;
		ArrayList charRenderData = new ArrayList();
		int imgMinY = -1;
		int imgMaxY = -1;
		int width = -1;
		int height = -1;
		int lowerLeftX = -1;
		int lowerLeftY = -1;
		int upperRightX = -1;
		int upperRightY = -1;
		for (Object obj; (obj = PdfParser.cropNext(pis, true, false)) != null;) {
			if (obj instanceof PInlineImage) {
				if (DEBUG_TYPE3_LOADING) System.out.println("Inline Image: " + ((PInlineImage) obj).tag + " [" + ((PInlineImage) obj).data.length + "]");
				charImageData = ((PInlineImage) obj);
				break;
			}
			else if (obj instanceof PTag) {
				if (DEBUG_TYPE3_LOADING) System.out.println("Content tag: " + ((PTag) obj).tag + " on " + stack);
				PTag tag = ((PTag) obj);
				if ("d1".equals(tag.tag)) {
					if (DEBUG_TYPE3_LOADING) System.out.println("Char image dimensions (d1): " + stack);
					if (stack.size() >= 6) {
						upperRightY = ((Number) stack.removeLast()).intValue();
						upperRightX = ((Number) stack.removeLast()).intValue();
						lowerLeftY = ((Number) stack.removeLast()).intValue();
						lowerLeftX = ((Number) stack.removeLast()).intValue();
						height = ((Number) stack.removeLast()).intValue();
						width = ((Number) stack.removeLast()).intValue();
					}
				}
				else if ("d0".equals(tag.tag)) {
					if (DEBUG_TYPE3_LOADING) System.out.println("Char image dimensions (d0): " + stack);
					if (stack.size() >= 2) {
						height = ((Number) stack.removeLast()).intValue();
						width = ((Number) stack.removeLast()).intValue();
					}
				}
				else if ("cm".equals(tag.tag)) {
					if (DEBUG_TYPE3_LOADING) System.out.println("Glyph transformation matrix: " + stack);
					if (stack.size() >= 6) {
						float[][] gtm = new float[3][3];
						gtm[2][2] = 1;
						gtm[1][2] = ((Number) stack.removeLast()).floatValue();
						gtm[0][2] = ((Number) stack.removeLast()).floatValue();
						gtm[2][1] = 0;
						gtm[1][1] = ((Number) stack.removeLast()).floatValue();
						gtm[0][1] = ((Number) stack.removeLast()).floatValue();
						gtm[2][0] = 0;
						gtm[1][0] = ((Number) stack.removeLast()).floatValue();
						gtm[0][0] = ((Number) stack.removeLast()).floatValue();
						float[] translate = PdfParser.transform(0, 0, 1, gtm, PRotate.identity, "");
						if (DEBUG_TYPE3_LOADING) System.out.println(" - glyph rendering origin " + Arrays.toString(translate));
						float[] scaleRotate1 = PdfParser.transform(1, 0, 0, gtm, PRotate.identity, "");
						if (DEBUG_TYPE3_LOADING) System.out.println(" - glyph rotation and scaling 1 " + Arrays.toString(scaleRotate1));
						float[] scaleRotate2 = PdfParser.transform(0, 1, 0, gtm, PRotate.identity, "");
						if (DEBUG_TYPE3_LOADING) System.out.println(" - glyph rotation and scaling 2 " + Arrays.toString(scaleRotate2));
						//	if image is upside-down (scaleRotate2[1] < 0), invert min and max Y
						if (scaleRotate2[1] < 0) {
							int minY = Math.min(-imgMinY, -imgMaxY);
							int maxY = Math.max(-imgMinY, -imgMaxY);
							imgMinY = minY;
							imgMaxY = maxY;
						}
					}
				}
				else if ("m".equals(tag.tag)) {
					if (stack.size() >= 2) {
						int[] params = new int[2];
						for (int p = params.length; p != 0; p--)
							params[p-1] = ((Number) stack.removeLast()).intValue();
						charRenderData.add(new GlyphRenderingCommandType3(tag.tag, params));
					}
					else if (DEBUG_TYPE3_LOADING) System.out.println(" ==> stack too small, need 2 parameters");
				}
				else if ("l".equals(tag.tag)) {
					if (stack.size() >= 2) {
						int[] params = new int[2];
						for (int p = params.length; p != 0; p--)
							params[p-1] = ((Number) stack.removeLast()).intValue();
						charRenderData.add(new GlyphRenderingCommandType3(tag.tag, params));
					}
					else if (DEBUG_TYPE3_LOADING) System.out.println(" ==> stack too small, need 2 parameters");
				}
				else if ("c".equals(tag.tag)) {
					if (stack.size() >= 6) {
						int[] params = new int[6];
						for (int p = params.length; p != 0; p--)
							params[p-1] = ((Number) stack.removeLast()).intValue();
						charRenderData.add(new GlyphRenderingCommandType3(tag.tag, params));
					}
					else if (DEBUG_TYPE3_LOADING) System.out.println(" ==> stack too small, need 6 parameters");
				}
				else if ("v".equals(tag.tag)) {
					if (stack.size() >= 4) {
						int[] params = new int[4];
						for (int p = params.length; p != 2; p--)
							params[p-1] = ((Number) stack.removeLast()).intValue();
						charRenderData.add(new GlyphRenderingCommandType3(tag.tag, params));
					}
					else if (DEBUG_TYPE3_LOADING) System.out.println(" ==> stack too small, need 4 parameters");
				}
				else if ("y".equals(tag.tag)) {
					if (stack.size() >= 4) {
						int[] params = new int[4];
						for (int p = params.length; p != 2; p--)
							params[p-1] = ((Number) stack.removeLast()).intValue();
						charRenderData.add(new GlyphRenderingCommandType3(tag.tag, params));
					}
					else if (DEBUG_TYPE3_LOADING) System.out.println(" ==> stack too small, need 4 parameters");
				}
				else if ("re".equals(tag.tag)) {
					if (stack.size() >= 4) {
						int[] params = new int[4];
						for (int p = params.length; p != 2; p--)
							params[p-1] = ((Number) stack.removeLast()).intValue();
						charRenderData.add(new GlyphRenderingCommandType3(tag.tag, params));
					}
					else if (DEBUG_TYPE3_LOADING) System.out.println(" ==> stack too small, need 4 parameters");
				}
				else if ("h".equals(tag.tag))
					charRenderData.add(new GlyphRenderingCommandType3(tag.tag, new int[0]));
				else if ("f".equals(tag.tag))
					charRenderData.add(new GlyphRenderingCommandType3(tag.tag, new int[0]));
				else if (DEBUG_TYPE3_LOADING) System.out.println(" ==> ignored");
			}
			else {
				if (DEBUG_TYPE3_LOADING) {
					System.out.println(obj.getClass().getName() + ": " + obj.toString());
					if (obj instanceof PString) {
						PString str = ((PString) obj);
						System.out.println(" --> HEX 2: " + str.isHex2 + ", HEX 4: " + str.isHex4);
						System.out.println(" --> " + Arrays.toString(str.bytes));
						if (str.isHex2) {
							System.out.print(" -->");
							for (int b = 0; (b + 1) < str.bytes.length; b += 2)
								System.out.print(" " + ((char) str.bytes[b]) + ((char) str.bytes[b+1]));
							System.out.println();
						}
						else if (str.isHex4) {
							System.out.print(" -->");
							for (int b = 0; (b + 3) < str.bytes.length; b += 4)
								System.out.print(" " + ((char) str.bytes[b]) + ((char) str.bytes[b+1]) + ((char) str.bytes[b+2]) + ((char) str.bytes[b+3]));
							System.out.println();
						}
					}
				}
				stack.addLast(obj);
			}
		}
		
		if ((width == -1) || (height == -1))
			return null;
		else if (charImageData == null) {
			if ((lowerLeftX == -1) && (lowerLeftY == -1) && (upperRightX == -1) && (upperRightY == -1))
				return new GlyphDataType3(((GlyphRenderingCommandType3[]) charRenderData.toArray(new GlyphRenderingCommandType3[charRenderData.size()])), width, height);
			else return new GlyphDataType3(((GlyphRenderingCommandType3[]) charRenderData.toArray(new GlyphRenderingCommandType3[charRenderData.size()])), width, height, lowerLeftX, lowerLeftY, upperRightX, upperRightY);
		}
		else {
			if ((lowerLeftX == -1) && (lowerLeftY == -1) && (upperRightX == -1) && (upperRightY == -1))
				return new GlyphDataType3(charImageData.data, width, height);
			else return new GlyphDataType3(charImageData.data, width, height, lowerLeftX, lowerLeftY, upperRightX, upperRightY);
		}
	}
	
	private static final boolean DEBUG_SHOW_VERIFICATION_CHAR_MATCHES = false;
	private static final boolean DEBUG_SHOW_SMALL_CAPS_CHAR_MATCHES = false;
	
	private static void decodeChars(PdfFont pFont, CharDecoder pCharDecoder, char[] chars, Integer[] charCodes, String[] charNames, Object[] glyphKeys, CharImage[] charImages/*, Integer[] rawCharCodes*/, int dWidth, float maxDescent, FontDecoderCharset charSet, ProgressMonitor pm, boolean debug) {
		
		//	do nothing with fonts representing vectorized OCR (e.g. DjVu)
		if (charSet == VECTORIZED_OCR_DECODING)
			return;
		
		//	generate comparison fonts
		Font serifFont = getSerifFont();
		Font[] serifFonts = new Font[4];
		Font sansFont = getSansSerifFont();
		Font[] sansFonts = new Font[4];
		Font monoFont = getMonospaceFont();
		Font[] monoFonts = new Font[4];
		for (int s = Font.PLAIN; s <= (Font.BOLD | Font.ITALIC); s++) {
			serifFonts[s] = serifFont.deriveFont(s);
			sansFonts[s] = sansFont.deriveFont(s);
			monoFonts[s] = monoFont.deriveFont(s);
		}
		
		/* If some character Unicode maps to a character sequence that is NOT
		 * a PostScript name (e.g. non-Unicode custom ligature 'Th'), null out
		 * char image to leave as is (we won't be able to render the apropriate
		 * comparison char anyway ...)
		 * Also, null out duplicate char images (identical glyhs, caught above)
		 * in rendering routines */
		HashSet charImageSet = new HashSet();
		for (int c = 0; c < chars.length; c++) {
			if (charImages[c] == null)
				continue;
			
			//	check if char code has a synonym
			if (pFont.charCodeSynonyms.containsKey(charCodes[c])) {
				pm.setInfo("     - Exempting char " + c + " (" + charCodes[c] + "/" + charNames[c] + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ") from decoding for synonymy");
				if (debug) System.out.println("Exempting char " + c + " (" + charCodes[c] + "/" + charNames[c] + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ") from decoding for synonymy");
				charImages[c] = null; // that prevents all further analysis (no data loss, though, as image is stored in font before we ever get here)
			}
			
			//	check if char image is synonym
			if (!charImageSet.add(charImages[c])) {
				pm.setInfo("     - Exempting char " + c + " (" + charCodes[c] + "/" + charNames[c] + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ") from decoding for image synonymy");
				if (debug) System.out.println("Exempting char " + c + " (" + charCodes[c] + "/" + charNames[c] + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ") from decoding for image synonymy");
				charImages[c] = null; // that prevents all further analysis (no data loss, though, as image is stored in font before we ever get here)
			}
			
			//	get Unicode mapping
			String ucStr = ((String) pFont.ucMappings.get(charCodes[c]));
			
			//	not Unicode mapped at all
			if (ucStr == null)
				continue;
			
			//	mapped to single character, should work just fine
			if (ucStr.length() < 2)
				continue;
			
			//	test if valid PostScript char name
			char ucCh = StringUtils.getCharForName(ucStr);
			if (ucCh != 0)
				continue;
			
			//	check if we have a known/frequent custom ligature, and if so, just accept it
			if (customLigatures.contains(ucStr)) {
				pm.setInfo("     - Exempting char " + c + " (" + charCodes[c] + "/" + charNames[c] + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ") from decoding for lack of a Unicode point for mapped character sequence '" + ucStr + "'");
				if (debug) System.out.println("Exempting char " + c + " (" + charCodes[c] + "/" + charNames[c] + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ") from decoding for lack of a Unicode point for mapped character sequence '" + ucStr + "'");
				charImages[c] = null; // that prevents all further analysis (no data loss, though, as image is stored in font before we ever get here)
			}
		}
		
		//	set up matching
		CharImageMatch[][] serifStyleCims = new CharImageMatch[chars.length][4];
		int[] serifCharCounts = {0, 0, 0, 0};
		int[] serifCharCountsNs = {0, 0, 0, 0};
		double[] serifStyleSimMins = {1, 1, 1, 1};
		double[] serifStyleSimMinsNs = {1, 1, 1, 1};
		double[] serifStyleSimSums = {0, 0, 0, 0};
		double[] serifStyleSimSumsNs = {0, 0, 0, 0};
		
		CharImageMatch[][] sansStyleCims = new CharImageMatch[chars.length][4];
		int[] sansCharCounts = {0, 0, 0, 0};
		int[] sansCharCountsNs = {0, 0, 0, 0};
		double[] sansStyleSimMins = {1, 1, 1, 1};
		double[] sansStyleSimMinsNs = {1, 1, 1, 1};
		double[] sansStyleSimSums = {0, 0, 0, 0};
		double[] sansStyleSimSumsNs = {0, 0, 0, 0};
		
		CharImageMatch[][] monoStyleCims = new CharImageMatch[chars.length][4];
		int[] monoCharCounts = {0, 0, 0, 0};
		int[] monoCharCountsNs = {0, 0, 0, 0};
		double[] monoStyleSimMins = {1, 1, 1, 1};
		double[] monoStyleSimMinsNs = {1, 1, 1, 1};
		double[] monoStyleSimSums = {0, 0, 0, 0};
		double[] monoStyleSimSumsNs = {0, 0, 0, 0};
		
		HashMap matchCharChache = new HashMap();
		
		//	collect potential small-caps, and best matches for small-caps assessment
		HashSet smallCapsChars = new HashSet();
		CharImageMatch[] bestCims = new CharImageMatch[chars.length];
		CharImageMatch[] badBestCims = new CharImageMatch[chars.length];
		float[] bestSims = new float[chars.length];
		Arrays.fill(bestSims, 0);
		float[] bestSimsNs = new float[chars.length];
		Arrays.fill(bestSimsNs, 0);
		
		//	match rendered glyphs against their mapped characters
		for (int c = 0; c < chars.length; c++) {
			if (charImages[c] == null)
				continue;
			
			//	get basic data
			Integer chc = charCodes[c];
			Integer encChc = PdfFont.getCharCode(new Character((char) chc.intValue()), pFont.encoding, debug);
			if (encChc == null)
				encChc = chc;
			String chn = charNames[c];
			pm.setInfo("     - Checking char " + c + " (" + chc + "/" + chn + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ")");
			if (debug) System.out.println("Checking char " + c + " (" + chc + "=>" + encChc + "/" + chn + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ")");
			
			//	some characters just vary too much in their glyphs to be good indicators for overall match quality
			boolean ignoreForMin = ((minIgnoreChars.indexOf(chars[c]) != -1) || (chars[c] != StringUtils.getBaseChar(chars[c])));
			
			//	try named char match first (render known chars to fill whole image)
			CharMatchResult oMatchResult = PdfCharDecoder.matchChar(charImages[c], chars[c], true, serifFonts, sansFonts, monoFonts, matchCharChache, true, false);
			if (debug) System.out.println(" - average similarity is " + oMatchResult.getAverageSimilarity());
			
			//	also try font default encoding (quite a good few fonts ARE faithful, ad sometimes only UC mapping is obfuscated ...)
			char encChar = pFont.getChar(encChc.intValue());
			CharMatchResult encMatchResult = null;
			if (encChar != chars[c]) {
				/* TODO Compare encoding based mapping to existing Unicode mapping in PDF font decoder:
				 * - replace existing Unicode mapping if encoding provided mapping better match
				 *   ==> overcomes obfuscation based on Unicode mapping alone
				 * - create missing Unicode mapping from encoding provided mapping if latter decent match
				 * - also, try and enable rendering multiple characters for Unicode mapping verification matches
				 *   ==> facilitates verifying that don't have Unicode point (like custom "Th" ligature) 
				 */
				if (debug) System.out.println(" - testing non-Unicode mapping '" + encChar + "' (" + ((int) encChar) + ")");
				encMatchResult = PdfCharDecoder.matchChar(charImages[c], encChar, (encChar != Character.toUpperCase(encChar)), serifFonts, sansFonts, monoFonts, matchCharChache, true, false);
				if (debug) System.out.println(" - average similarity is " + encMatchResult.getAverageSimilarity());
				
				//	we have two match results, compare them
				if (oMatchResult.rendered && encMatchResult.rendered) {
					if (debug) System.out.println(" - comparing original and Unicode mapped matches");
					int originalBetter = 0;
					int encMappingBetter = 0;
					for (int s = Font.PLAIN; s <= (Font.BOLD | Font.ITALIC); s++) {
						if ((oMatchResult.serifStyleCims[s] != null) && (encMatchResult.serifStyleCims[s] != null)) {
							if (oMatchResult.serifStyleCims[s].sim < encMatchResult.serifStyleCims[s].sim) {
								encMappingBetter++;
								oMatchResult.serifStyleCims[s] = encMatchResult.serifStyleCims[s];
							}
							else originalBetter++;
						}
						else if (encMatchResult.serifStyleCims[s] != null) {
							encMappingBetter++;
							oMatchResult.serifStyleCims[s] = encMatchResult.serifStyleCims[s];
						}
						else if (oMatchResult.serifStyleCims[s] != null)
							originalBetter++;
						
						if ((oMatchResult.sansStyleCims[s] != null) && (encMatchResult.sansStyleCims[s] != null)) {
							if (oMatchResult.sansStyleCims[s].sim < encMatchResult.sansStyleCims[s].sim) {
								encMappingBetter++;
								oMatchResult.sansStyleCims[s] = encMatchResult.sansStyleCims[s];
							}
							else originalBetter++;
						}
						else if (encMatchResult.sansStyleCims[s] != null) {
							encMappingBetter++;
							oMatchResult.sansStyleCims[s] = encMatchResult.sansStyleCims[s];
						}
						else if (oMatchResult.sansStyleCims[s] != null)
							originalBetter++;
						
						if ((oMatchResult.monoStyleCims[s] != null) && (encMatchResult.monoStyleCims[s] != null)) {
							if (oMatchResult.monoStyleCims[s].sim < encMatchResult.monoStyleCims[s].sim) {
								encMappingBetter++;
								oMatchResult.monoStyleCims[s] = encMatchResult.monoStyleCims[s];
							}
							else originalBetter++;
						}
						else if (encMatchResult.monoStyleCims[s] != null) {
							encMappingBetter++;
							oMatchResult.monoStyleCims[s] = encMatchResult.monoStyleCims[s];
						}
						else if (oMatchResult.monoStyleCims[s] != null)
							originalBetter++;
					}
					
					if (originalBetter > encMappingBetter) {
//						oMatchResult = encMatchResult;
						if (debug) System.out.println(" --> found original char to be better match (" + originalBetter + " vs. " + encMappingBetter + "), removing mapping");
					}
					else if (debug) System.out.println(" --> found encoding mapped char to be better match (" + encMappingBetter + " vs. " + originalBetter + ")");
				}
				
				//	fall back to encoding mapped char if original one didn't render at all
				else if (encMatchResult.rendered) {
					if (debug) System.out.println(" - falling back to encoding mapped char");
					oMatchResult = encMatchResult;
				}
				
				//	Unicode mapped char didn't render for some other reason
				else if (debug) System.out.println(" - retaining original char");
			}
			
			//	if ToUnicode mapping exists, verify it, and use whatever fits better (not in CID font mode)
			CharMatchResult ucMatchResult = null;
			if (pFont.ucMappings.containsKey(chc)) {
				String ucStr = ((String) pFont.ucMappings.get(chc));
				if (debug) System.out.println(" - got Unicode mapping '" + ucStr + "'" + ((ucStr == null) ? "" : (" (" + ((int) ucStr.charAt(0)) + ")")));
				
				//	get ligature chars (names consist of their component letters)
				if ((ucStr != null) && (ucStr.length() > 1)) {
					char lUcCh = StringUtils.getCharForName(ucStr);
					if (lUcCh > 0) {
						if (debug) System.out.println(" - unified Unicode mapping '" + ucStr + "' to '" + lUcCh + "'");
						ucStr = ("" + lUcCh);
					}
				}
				
				//	render mapped Unicode char
				if ((ucStr != null) && (ucStr.length() == 1) && (ucStr.charAt(0) != chars[c])) {
					char ucChar = ucStr.charAt(0);
					if (debug) System.out.println(" - testing Unicode mapping '" + ucChar + "' (" + ((int) ucStr.charAt(0)) + ")");
					ucMatchResult = PdfCharDecoder.matchChar(charImages[c], ucChar, (ucChar != Character.toUpperCase(ucChar)), serifFonts, sansFonts, monoFonts, matchCharChache, true, false);
					if (debug) System.out.println(" - average similarity is " + ucMatchResult.getAverageSimilarity());
					
					//	we have two match results, compare them
					if (oMatchResult.rendered && ucMatchResult.rendered) {
						if (debug) System.out.println(" - comparing original and Unicode mapped matches");
						int originalBetter = 0;
						int ucMappingBetter = 0;
						for (int s = Font.PLAIN; s <= (Font.BOLD | Font.ITALIC); s++) {
							if ((oMatchResult.serifStyleCims[s] != null) && (ucMatchResult.serifStyleCims[s] != null)) {
								if (oMatchResult.serifStyleCims[s].sim < ucMatchResult.serifStyleCims[s].sim) {
									ucMappingBetter++;
									oMatchResult.serifStyleCims[s] = ucMatchResult.serifStyleCims[s];
								}
								else originalBetter++;
							}
							else if (ucMatchResult.serifStyleCims[s] != null) {
								ucMappingBetter++;
								oMatchResult.serifStyleCims[s] = ucMatchResult.serifStyleCims[s];
							}
							else if (oMatchResult.serifStyleCims[s] != null)
								originalBetter++;
//							
//							if ((oMatchResult.serifStyleCims[s] != null) && (ucMatchResult.serifStyleCims[s] != null) && "f".equals(ucStr)) {
//								PdfCharDecoder.displayCharMatch(oMatchResult.serifStyleCims[s], "Original");
//								PdfCharDecoder.displayCharMatch(ucMatchResult.serifStyleCims[s], "UC");
//							}
							
							if ((oMatchResult.sansStyleCims[s] != null) && (ucMatchResult.sansStyleCims[s] != null)) {
								if (oMatchResult.sansStyleCims[s].sim < ucMatchResult.sansStyleCims[s].sim) {
									ucMappingBetter++;
									oMatchResult.sansStyleCims[s] = ucMatchResult.sansStyleCims[s];
								}
								else originalBetter++;
							}
							else if (ucMatchResult.sansStyleCims[s] != null) {
								ucMappingBetter++;
								oMatchResult.sansStyleCims[s] = ucMatchResult.sansStyleCims[s];
							}
							else if (oMatchResult.sansStyleCims[s] != null)
								originalBetter++;
							
							if ((oMatchResult.monoStyleCims[s] != null) && (ucMatchResult.monoStyleCims[s] != null)) {
								if (oMatchResult.monoStyleCims[s].sim < ucMatchResult.monoStyleCims[s].sim) {
									ucMappingBetter++;
									oMatchResult.monoStyleCims[s] = ucMatchResult.monoStyleCims[s];
								}
								else originalBetter++;
							}
							else if (ucMatchResult.monoStyleCims[s] != null) {
								ucMappingBetter++;
								oMatchResult.monoStyleCims[s] = ucMatchResult.monoStyleCims[s];
							}
							else if (oMatchResult.monoStyleCims[s] != null)
								originalBetter++;
						}
						
						if (originalBetter > ucMappingBetter) {
							pFont.ucMappings.remove(chc);
							if (debug) System.out.println(" --> found original char to be better match (" + originalBetter + " vs. " + ucMappingBetter + "), removing mapping");
						}
						else if (debug) System.out.println(" --> found mapped char to be better match (" + ucMappingBetter + " vs. " + originalBetter + ")");
					}
					
					//	fall back to Unicode mapped char if original one didn't render at all
					else if (ucMatchResult.rendered) {
						if (debug) System.out.println(" - falling back to Unicode mapped matches");
						oMatchResult = ucMatchResult;
					}
					
					//	Unicode mapped to exotic char we couldn't render, just retain mapping
					else if ("U".equals(PdfCharDecoder.getCharClass(ucChar))) {
						if (debug) System.out.println(" - retaining unverifiable Unicode mapping");
						bestCims[c] = UNICODE_MAPPING_UNRENDERABLE;
						continue;
					}
					
					//	Unicode mapped char didn't render for some other reason
					else if (debug) System.out.println(" - retaining unverifiable Unicode mapping");
				}
			}
			
			//	evaluate match result
			if (oMatchResult.rendered) {
				for (int s = Font.PLAIN; s <= (Font.BOLD | Font.ITALIC); s++) {
					serifStyleCims[c][s] = oMatchResult.serifStyleCims[s];
					if (serifStyleCims[c][s] == null) {
						serifStyleSimMins[s] = 0;
						continue;
					}
					serifCharCounts[s]++;
					serifStyleSimSums[s] += serifStyleCims[c][s].sim;
					if (bestSims[c] < serifStyleCims[c][s].sim) {
						bestSims[c] = serifStyleCims[c][s].sim;
						bestCims[c] = serifStyleCims[c][s];
						if (DEBUG_SHOW_VERIFICATION_CHAR_MATCHES) {
							System.out.println("Char code " + charCodes[c] + " at " + c + " with name " + charNames[c] + ", UC mapped to " + pFont.getUnicode(charCodes[c]));
							int choice = PdfCharDecoder.displayCharMatch(serifStyleCims[c][s], "Verification char match (serif)");
							if (choice == JOptionPane.CANCEL_OPTION)
								throw new RuntimeException("GOTCHA");
						}
					}
//					if (DEBUG_SHOW_VERIFICATION_CHAR_MATCHES) {
//						System.out.println("Char code " + charCodes[c] + " at " + c + " with name " + charNames[c] + ", UC mapped to " + pFont.getUnicode(charCodes[c]));
//						int choice = PdfCharDecoder.displayCharMatch(serifStyleCims[c][s], "Verification char match (serif)");
//						if (choice == JOptionPane.CANCEL_OPTION)
//							throw new RuntimeException("GOTCHA");
//					}
					if (!ignoreForMin)
						serifStyleSimMins[s] = Math.min(serifStyleCims[c][s].sim, serifStyleSimMins[s]);
					if (((s & Font.ITALIC) == 0) || (skewChars.indexOf(chars[c]) == -1)) {
						serifCharCountsNs[s]++;
						serifStyleSimSumsNs[s] += serifStyleCims[c][s].sim;
						bestSimsNs[c] = Math.max(bestSimsNs[c], serifStyleCims[c][s].sim);
						if (!ignoreForMin)
							serifStyleSimMinsNs[s] = Math.min(serifStyleCims[c][s].sim, serifStyleSimMinsNs[s]);
					}
				}
				for (int s = Font.PLAIN; s <= (Font.BOLD | Font.ITALIC); s++) {
					sansStyleCims[c][s] = oMatchResult.sansStyleCims[s];
					if (sansStyleCims[c][s] == null) {
						sansStyleSimMins[s] = 0;
						continue;
					}
					sansCharCounts[s]++;
					sansStyleSimSums[s] += sansStyleCims[c][s].sim;
					if (bestSims[c] < sansStyleCims[c][s].sim) {
						bestSims[c] = sansStyleCims[c][s].sim;
						bestCims[c] = sansStyleCims[c][s];
						if (DEBUG_SHOW_VERIFICATION_CHAR_MATCHES) {
							System.out.println("Char code " + charCodes[c] + " at " + c + " with name " + charNames[c] + ", UC mapped to " + pFont.getUnicode(charCodes[c]));
							int choice = PdfCharDecoder.displayCharMatch(sansStyleCims[c][s], "Verification char match (sans serif)");
							if (choice == JOptionPane.CANCEL_OPTION)
								throw new RuntimeException("GOTCHA");
						}
					}
//					if (DEBUG_SHOW_VERIFICATION_CHAR_MATCHES) {
//						System.out.println("Char code " + charCodes[c] + " at " + c + " with name " + charNames[c] + ", UC mapped to " + pFont.getUnicode(charCodes[c]));
//						int choice = PdfCharDecoder.displayCharMatch(sansStyleCims[c][s], "Verification char match (sans serif)");
//						if (choice == JOptionPane.CANCEL_OPTION)
//							throw new RuntimeException("GOTCHA");
//					}
					if (!ignoreForMin)
						sansStyleSimMins[s] = Math.min(sansStyleCims[c][s].sim, sansStyleSimMins[s]);
					if (((s & Font.ITALIC) == 0) || (skewChars.indexOf(chars[c]) == -1)) {
						sansCharCountsNs[s]++;
						sansStyleSimSumsNs[s] += sansStyleCims[c][s].sim;
						bestSimsNs[c] = Math.max(bestSimsNs[c], sansStyleCims[c][s].sim);
						if (!ignoreForMin)
							sansStyleSimMinsNs[s] = Math.min(sansStyleCims[c][s].sim, sansStyleSimMinsNs[s]);
					}
				}
				for (int s = Font.PLAIN; s <= (Font.BOLD | Font.ITALIC); s++) {
					monoStyleCims[c][s] = oMatchResult.monoStyleCims[s];
					if (monoStyleCims[c][s] == null) {
						monoStyleSimMins[s] = 0;
						continue;
					}
					monoCharCounts[s]++;
					monoStyleSimSums[s] += monoStyleCims[c][s].sim;
					if (bestSims[c] < monoStyleCims[c][s].sim) {
						bestSims[c] = monoStyleCims[c][s].sim;
						bestCims[c] = monoStyleCims[c][s];
						if (DEBUG_SHOW_VERIFICATION_CHAR_MATCHES) {
							System.out.println("Char code " + charCodes[c] + " at " + c + " with name " + charNames[c] + ", UC mapped to " + pFont.getUnicode(charCodes[c]));
							int choice = PdfCharDecoder.displayCharMatch(monoStyleCims[c][s], "Verification char match (monospaced)");
							if (choice == JOptionPane.CANCEL_OPTION)
								throw new RuntimeException("GOTCHA");
						}
					}
//					if (DEBUG_SHOW_VERIFICATION_CHAR_MATCHES) {
//						System.out.println("Char code " + charCodes[c] + " at " + c + " with name " + charNames[c] + ", UC mapped to " + pFont.getUnicode(charCodes[c]));
//						int choice = PdfCharDecoder.displayCharMatch(sansStyleCims[c][s], "Verification char match (sans serif)");
//						if (choice == JOptionPane.CANCEL_OPTION)
//							throw new RuntimeException("GOTCHA");
//					}
					if (!ignoreForMin)
						monoStyleSimMins[s] = Math.min(monoStyleCims[c][s].sim, monoStyleSimMins[s]);
					if (((s & Font.ITALIC) == 0) || (skewChars.indexOf(chars[c]) == -1)) {
						monoCharCountsNs[s]++;
						monoStyleSimSumsNs[s] += monoStyleCims[c][s].sim;
						bestSimsNs[c] = Math.max(bestSimsNs[c], monoStyleCims[c][s].sim);
						if (!ignoreForMin)
							monoStyleSimMinsNs[s] = Math.min(monoStyleCims[c][s].sim, monoStyleSimMinsNs[s]);
					}
				}
				
				//	what do we have?
				if (debug) System.out.println(" --> best similarity is " + bestSims[c] + " / " + bestSimsNs[c] + " for " + ((bestCims[c] == null) ? "null" : ("" + bestCims[c].match.ch)));
			}
			
			//	failed to render char at all, little we can do
			else if (debug) System.out.println(" --> could not render char " + chars[c] + " (" + ((int) chars[c]) + ")");
			
			//	we might want to revisit this one
			if (bestSims[c] < 0.5) {
				if (DEBUG_SHOW_VERIFICATION_CHAR_MATCHES && (bestCims[c] != null))
					PdfCharDecoder.displayCharMatch(bestCims[c], "Bad verification match");
				if (charSet.verifyUnicodeMapped()) {
					badBestCims[c] = bestCims[c]; // hold on to whatever we have (might still turn out better than best OCR match ...)
					bestCims[c] = null;
					if (debug) System.out.println(" --> marked for revisiting");
				}
				else if (debug) System.out.println(" --> retained despite bad verification match");
				continue;
			}
			
			//	remember small-caps match (whichever way)
			if (bestCims[c].match.ch != chars[c]) {
				if (DEBUG_SHOW_VERIFICATION_CHAR_MATCHES && (bestCims[c] != null))
					PdfCharDecoder.displayCharMatch(bestCims[c], "Small-caps match");
				smallCapsChars.add(new Integer(c));
				if (debug) System.out.println(" --> small-caps match");
			}
			else if (isSmallCapsMatch(chars[c], ((String) pFont.ucMappings.get(chc)))) {
				if (DEBUG_SHOW_VERIFICATION_CHAR_MATCHES && (bestCims[c] != null))
					PdfCharDecoder.displayCharMatch(bestCims[c], "Small-caps match");
				smallCapsChars.add(new Integer(c));
				if (debug) System.out.println(" --> lower case mapped capital");
			}
		}
		
		//	compute minmum and average similarity
		double simMin = 1;
		double simMinNs = 1;
		double simSum = 0;
		double simSumNs = 0;
		int charCount = 0;
		int charCountNs = 0;
		for (int c = 0; c < chars.length; c++) {
			if (charImages[c] == null)
				continue;
			if (bestCims[c] == null)
				continue;
			
			//	count for average
			if (bestSims[c] > 0) {
				charCount++;
				simSum += bestSims[c];
			}
			if (bestSimsNs[c] > 0) {
				charCountNs++;
				simSumNs += bestSimsNs[c];
			}
			
			//	some characters just vary too much in their glyphs to be good indicators for overall match quality
			boolean ignoreForMin = ((minIgnoreChars.indexOf(chars[c]) != -1) || (chars[c] != StringUtils.getBaseChar(chars[c])));
			
			//	count for minimum
			if (!ignoreForMin) {
				if (bestSims[c] > 0)
					simMin = Math.min(simMin, bestSims[c]);
				if (bestSimsNs[c] > 0)
					simMinNs = Math.min(simMinNs, bestSimsNs[c]);
			}
		}
		
		//	use maximum of all fonts and styles when computing min and average similarity
		if (debug) System.out.println(" - min similarity is " + simMin + " all / " + simMinNs + " non-skewed");
		double sim = ((charCount == 0) ? 0 : (simSum / charCount));
		double simNs = ((charCountNs == 0) ? 0 : (simSumNs / charCountNs));
		if (debug) System.out.println(" - average similarity is " + sim + " (" + charCount + ") all / " + simNs + " (" + charCountNs + ") non-skewed");
		
		//	mark chars for revisiting if similarity way below font average (more than twice as far from 1 than average), but only with OCR enabled
		if ((charSet != NO_DECODING) && (charSet != VECTORIZED_OCR_DECODING) && (charSet != RENDER_ONLY)) {
			double minAcceptSim = (sim + sim - 1);
			if (debug) System.out.println(" - minimum acceptance similarity is " + minAcceptSim);
			for (int c = 0; c < chars.length; c++) {
				if (charImages[c] == null)
					continue;
				if (bestCims[c] == null)
					continue;
				
				//	retain mapping if char has Unicode mapping and we're only decoding unmapped codes
				if (!charSet.decodeUnicodeMapped() && pFont.ucMappings.containsKey(charCodes[c]))
					continue;
				
				//	mark char for revisiting if similarity too far below average
				if (bestCims[c].sim < minAcceptSim) {
					if (debug) System.out.println("   --> marked char " + c + " (" + charCodes[c] + "/" + charNames[c] + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ") for revistiting at similarity " + bestCims[c].sim);
					badBestCims[c] = bestCims[c]; // hold on to whatever we have (might still turn out better than best OCR match ...)
					bestCims[c] = null;
				}
			}
		}
		
		//	re-compute minmum and average similarity
		simMin = 1;
		simMinNs = 1;
		simSum = 0;
		simSumNs = 0;
		charCount = 0;
		charCountNs = 0;
		for (int c = 0; c < chars.length; c++) {
			if (charImages[c] == null)
				continue;
			if (bestCims[c] == null)
				continue;
			
			//	count for average
			if (bestSims[c] > 0) {
				charCount++;
				simSum += bestSims[c];
			}
			if (bestSimsNs[c] > 0) {
				charCountNs++;
				simSumNs += bestSimsNs[c];
			}
			
			//	some characters just vary too much in their glyphs to be good indicators for overall match quality
			boolean ignoreForMin = ((minIgnoreChars.indexOf(chars[c]) != -1) || (chars[c] != StringUtils.getBaseChar(chars[c])));
			
			//	count for minimum
			if (!ignoreForMin) {
				if (bestSims[c] > 0)
					simMin = Math.min(simMin, bestSims[c]);
				if (bestSimsNs[c] > 0)
					simMinNs = Math.min(simMinNs, bestSimsNs[c]);
			}
		}
		
		//	use maximum of all fonts and styles when computing min and average similarity
		if (debug) System.out.println(" - recomputed min similarity is " + simMin + " all / " + simMinNs + " non-skewed");
		sim = ((charCount == 0) ? 0 : (simSum / charCount));
		simNs = ((charCountNs == 0) ? 0 : (simSumNs / charCountNs));
		if (debug) System.out.println(" - recomputed average similarity is " + sim + " (" + charCount + ") all / " + simNs + " (" + charCountNs + ") non-skewed");
		
		
		//	check if font has implicit spaces
		checkImplicitSpaces(pFont, charImages, chars, charCodes, charNames, glyphKeys, debug);
		
		
		//	do we have a match?
		if (((simMin > 0.5) && (sim > 0.6)) || ((simMin > 0.375) && (sim > 0.7)) || ((simMin > 0.25) && (sim > 0.8))) {
			if (debug) System.out.println(" ==> match");
//			
//			//	try to select font style if pool sufficiently large
//			int bestStyle = -1;
//			boolean serif = true;
//			if (Math.max(serifStyleSimSums.length, sansStyleSimSums.length) >= 2) {
//				double bestStyleSim = 0;
//				for (int s = Font.PLAIN; s <= (Font.BOLD | Font.ITALIC); s++) {
//					if (debug) {
//						System.out.println(" - checking style " + s);
//						System.out.println("   - min similarity is " + serifStyleSimMins[s] + "/" + sansStyleSimMins[s] + " all / " + serifStyleSimMinsNs[s] + "/" + sansStyleSimMinsNs[s] + " non-skewed");
//					}
//					double serifStyleSim = ((serifCharCounts[s] == 0) ? 0 : (serifStyleSimSums[s] / serifCharCounts[s]));
//					double serifStyleSimNs = ((serifCharCountsNs[s] == 0) ? 0 : (serifStyleSimSumsNs[s] / serifCharCountsNs[s]));
//					double sansStyleSim = ((sansCharCounts[s] == 0) ? 0 : (sansStyleSimSums[s] / sansCharCounts[s]));
//					double sansStyleSimNs = ((sansCharCountsNs[s] == 0) ? 0 : (sansStyleSimSumsNs[s] / sansCharCountsNs[s]));
//					if (debug) System.out.println("   - average similarity is " + serifStyleSim + "/" + sansStyleSim + " all / " + serifStyleSimNs + "/" + sansStyleSimNs + " non-skewed");
//					if ((((s & Font.ITALIC) == 0) ? Math.max(serifStyleSim, sansStyleSim) : Math.max(serifStyleSimNs, sansStyleSimNs)) > bestStyleSim) {
//						if (debug) System.out.println(" ==> new best match style");
//						bestStyleSim = (((s & Font.ITALIC) == 0) ? Math.max(serifStyleSim, sansStyleSim) : Math.max(serifStyleSimNs, sansStyleSimNs));
//						bestStyle = s;
//						serif = (((s & Font.ITALIC) == 0) ? (serifStyleSim >= sansStyleSim) : (serifStyleSimNs >= sansStyleSimNs));
//						if (DEBUG_TYPE1C_LOADING) System.out.println(" ==> serif is " + serif);
//					}
//				}
//			}
//			
//			//	style not found, use plain
//			if (bestStyle == -1)
//				bestStyle = 0;
//			
//			//	set base font according to style
//			pFont.bold = ((Font.BOLD & bestStyle) != 0);
//			pFont.italics = ((Font.ITALIC & bestStyle) != 0);
//			pFont.serif = serif;
//			if ((pFont.baseFont == null) || (pFont.bold != pFont.baseFont.bold) || (pFont.italics != pFont.baseFont.italic)) {
//				String bfn = "Times-";
//				if (pFont.bold && pFont.italics)
//					bfn += "BoldItalic";
//				else if (pFont.bold)
//					bfn += "Bold";
//				else if (pFont.italics)
//					bfn += "Italic";
//				else bfn += "Roman";
//				pFont.setBaseFont(bfn, false);
//			}
			if (debug) System.out.println(" ==> font decoded");
			
			//	store character widths
			if (dWidth != -1)
				pFont.mCharWidth = dWidth;
			
			//	check for descent
			pFont.hasDescent = ((maxDescent < -150) || (pFont.descent < -0.150));
			
			//	measure xHeight and capHeight, vertical scaling and relative vertical shift, and relative changes to ascender and descender
			CharMatchFontMetrics cmfm = computeFontMetrics(bestCims);
			
			//	rectify small-caps based on above measurements
			//	TODO test with 'o' in font 'PAPCAD+AdvOT6504bca5.I' of 'Carbayo_et_al-2013-Zoologica_Scripta.pdf'
			//	TODO test with 'l' in font 'PAPCBD+AdvPSMP10' of 'Carbayo_et_al-2013-Zoologica_Scripta.pdf'
			//	TODO test with 't'/'T' in font 'OXCEMO+TimesNewRomanPSMT-SC700' of 'laciny_et_al_2015_z_arbeitsgem_osterr_entomol_new_diacamma.pdf'
			HashSet smallCapsCharsAccepted = new HashSet();
			HashSet smallCapsCharsRefused = new HashSet();
			for (int c = 0; c < chars.length; c++) {
				if (charImages[c] == null)
					continue;
				if (bestCims[c] == null)
					continue;
				if (bestCims[c] == UNICODE_MAPPING_UNRENDERABLE)
					continue;
				if (!smallCapsChars.contains(new Integer(c)))
					continue;
				if (!isSmallCapsMatch(chars[c], ((String) pFont.ucMappings.get(charCodes[c])))) {
					if (chars[c] == Character.toUpperCase(chars[c]))
						continue;
					if (chars[c] == bestCims[c].match.ch)
						continue;
				}
				
				//	postpone to neighborhood analysis for letters whose glyphs do not differ much in both size and vertical position
				if ("fiFI".indexOf(chars[c]) != -1)
					continue;
				
				boolean lowerCaseOK = true;
				if (debug) System.out.println(" - double-checking potential small-caps char (size & position) " + chars[c]);
				
				//	get basic data
				Integer chc = charCodes[c];
				
				//	check mapped Unicode in addition to char, in case we have a ligature
				String uc = pFont.getUnicode(chc);
				if ((uc != null) && (uc.length() > 1)) {
					if (debug) System.out.println("   ==> Unicode maps to " + uc + ", should be OK in lower case");
					smallCapsChars.remove(new Integer(c));
					continue;
				}
				
				//	simply correct letters whose glyphs are not scaled versions of one another
				if ("cfijopsuvwxyzCFIJOPSUVWXYZ".indexOf(chars[c]) == -1) // this is the letters for which distinguishing case is hard
					lowerCaseOK = false;
				
				//	fall back to ascender and descender shifts if scaling and vertical shift not available
				else if (Float.isNaN(cmfm.uaScaleLogY) && Float.isNaN(cmfm.uaRelVerticalCenterShift)) {
					
					//	use relative changes to ascender and descender, comparing match result figures to respective font averages
					float ascenderShiftDist = Math.abs(bestCims[c].relAscenderShift - cmfm.uaRelCapHeightAscenderShift);
					float descenderShiftDist = Math.abs(bestCims[c].relDescenderShift - cmfm.uaRelDescenderShift);
					if (debug) System.out.println("   - relative ascender shift in match is " + ascenderShiftDist + " off average, relative descender shift inmatch is " + descenderShiftDist + " off average");
					
					//	this one looks OK
					if ((ascenderShiftDist <= 0.15) && (descenderShiftDist <= 0.03))
						lowerCaseOK = false;
				}
				
				//	use scaling and vertical shift (way more precise) on char match results for upper and lower case
				else {
					
					//	render match result in upper and lower case
					CharMatchResult lcMatchResult = PdfCharDecoder.matchChar(charImages[c], Character.toLowerCase(chars[c]), false, serifFonts, sansFonts, monoFonts, matchCharChache, true, false);
					CharMatchResult ucMatchResult = PdfCharDecoder.matchChar(charImages[c], Character.toUpperCase(chars[c]), false, serifFonts, sansFonts, monoFonts, matchCharChache, true, false);
					
					//	compute shifts (average over styles)
					float lcRelVerticalCenterShiftSum = 0;
					int lcRelVerticalCenterShiftCount = 0;
					float ucRelVerticalCenterShiftSum = 0;
					int ucRelVerticalCenterShiftCount = 0;
					float lcScaleLogYSum = 0;
					int lcScaleLogYCount = 0;
					float ucScaleLogYSum = 0;
					int ucScaleLogYCount = 0;
					for (int s = Font.PLAIN; s <= (Font.BOLD | Font.ITALIC); s++) {
						if (lcMatchResult.serifStyleCims[s] != null) {
							lcRelVerticalCenterShiftSum += lcMatchResult.serifStyleCims[s].vCenterShift;
							lcRelVerticalCenterShiftCount++;
							lcScaleLogYSum += lcMatchResult.serifStyleCims[s].scaleLogY;
							lcScaleLogYCount++;
						}
						if (ucMatchResult.serifStyleCims[s] != null) {
							ucRelVerticalCenterShiftSum += ucMatchResult.serifStyleCims[s].vCenterShift;
							ucRelVerticalCenterShiftCount++;
							ucScaleLogYSum += ucMatchResult.serifStyleCims[s].scaleLogY;
							ucScaleLogYCount++;
						}
						if (lcMatchResult.sansStyleCims[s] != null) {
							lcRelVerticalCenterShiftSum += lcMatchResult.sansStyleCims[s].vCenterShift;
							lcRelVerticalCenterShiftCount++;
							lcScaleLogYSum += lcMatchResult.sansStyleCims[s].scaleLogY;
							lcScaleLogYCount++;
						}
						if (ucMatchResult.sansStyleCims[s] != null) {
							ucRelVerticalCenterShiftSum += ucMatchResult.sansStyleCims[s].vCenterShift;
							ucRelVerticalCenterShiftCount++;
							ucScaleLogYSum += ucMatchResult.sansStyleCims[s].scaleLogY;
							ucScaleLogYCount++;
						}
						if (lcMatchResult.monoStyleCims[s] != null) {
							lcRelVerticalCenterShiftSum += lcMatchResult.monoStyleCims[s].vCenterShift;
							lcRelVerticalCenterShiftCount++;
							lcScaleLogYSum += lcMatchResult.monoStyleCims[s].scaleLogY;
							lcScaleLogYCount++;
						}
						if (ucMatchResult.monoStyleCims[s] != null) {
							ucRelVerticalCenterShiftSum += ucMatchResult.monoStyleCims[s].vCenterShift;
							ucRelVerticalCenterShiftCount++;
							ucScaleLogYSum += ucMatchResult.monoStyleCims[s].scaleLogY;
							ucScaleLogYCount++;
						}
					}
					float lcRelVerticalCenterShift = ((lcRelVerticalCenterShiftCount == 0) ? Float.NaN : (lcRelVerticalCenterShiftSum / lcRelVerticalCenterShiftCount));
					float ucRelVerticalCenterShift = ((ucRelVerticalCenterShiftCount == 0) ? Float.NaN : (ucRelVerticalCenterShiftSum / ucRelVerticalCenterShiftCount));
					float lcScaleLogY = ((lcScaleLogYCount == 0) ? Float.NaN : (lcScaleLogYSum / lcScaleLogYCount));
					float ucScaleLogY = ((ucScaleLogYCount == 0) ? Float.NaN : (ucScaleLogYSum / ucScaleLogYCount));
					if (debug) System.out.println("   - measurements: vertical center shifts are LC: " + lcRelVerticalCenterShift + " / UC: " + ucRelVerticalCenterShift + ", Y scale log LC: " + lcScaleLogY + " / UC: " + ucScaleLogY);
					
					//	compare shifts
					float lcRelVerticalCenterShiftDiff = Math.abs(cmfm.uaRelVerticalCenterShift - lcRelVerticalCenterShift);
					float ucRelVerticalCenterShiftDiff = Math.abs(cmfm.uaRelVerticalCenterShift - ucRelVerticalCenterShift);
					float lcScaleLogYDiff = Math.abs(cmfm.uaScaleLogY - lcScaleLogY);
					float ucScaleLogYDiff = Math.abs(cmfm.uaScaleLogY - ucScaleLogY);
					float lcDiff = 0;
					float ucDiff = 0;
					if (!Float.isNaN(cmfm.uaRelVerticalCenterShift)) {
						lcDiff += lcRelVerticalCenterShiftDiff;
						ucDiff += ucRelVerticalCenterShiftDiff;
					}
					if (!Float.isNaN(cmfm.uaScaleLogY)) {
						lcDiff += lcScaleLogYDiff;
						ucDiff += ucScaleLogYDiff;
					}
					if (debug) System.out.println("   - differences: LC: " + lcDiff + " (" + lcRelVerticalCenterShiftDiff + " + " + lcScaleLogYDiff + "), UC: " + ucDiff + " (" + ucRelVerticalCenterShift + " + " + ucScaleLogY + ")");
					
					//	this one looks OK
					if (lcDiff >= ucDiff)
						lowerCaseOK = false;
				}
				
				//	this one looks OK by the means at hand
				if (lowerCaseOK) {
					smallCapsCharsRefused.add(new Integer(c));
					pFont.mapUnicode(chc, ("" + Character.toLowerCase(chars[c])), false);
//					pFont.mapUnicode(glyphKeys[c], ("" + Character.toLowerCase(chars[c])));
					pFont.setCharImage(chc.intValue(), StringUtils.getCharName(Character.toLowerCase(chars[c])), charImages[c].img, charImages[c].path);
//					pFont.setCharImage(glyphKeys[c], charImages[c].img, charImages[c].path);
					if (debug) System.out.println("   ==> looks OK in lower case");
					if (DEBUG_SHOW_SMALL_CAPS_CHAR_MATCHES) PdfCharDecoder.displayCharMatch(bestCims[c], "Small-caps match refused");
				}
				
				//	do actual correction
				else {
					smallCapsCharsAccepted.add(new Integer(c));
					pFont.mapUnicode(chc, ("" + Character.toUpperCase(chars[c])), false);
//					pFont.mapUnicode(glyphKeys[c], ("" + Character.toUpperCase(chars[c])));
					pFont.setCharImage(chc.intValue(), StringUtils.getCharName(Character.toUpperCase(chars[c])), charImages[c].img, charImages[c].path);
//					pFont.setCharImage(glyphKeys[c], charImages[c].img, charImages[c].path);
					if (debug) System.out.println("   ==> small-caps corrected " + chc + " to " + Character.toUpperCase(chars[c]));
					if (DEBUG_SHOW_SMALL_CAPS_CHAR_MATCHES) PdfCharDecoder.displayCharMatch(bestCims[c], "Small-caps match accepted");
				}
				
				//	mark as handled either way
				smallCapsChars.remove(new Integer(c));
			}
			
			//	re-consider the refused small-caps matches if there are many corrections
			if (debug) System.out.println(" - corrected " + smallCapsCharsAccepted.size() + " small-caps, refused " + smallCapsCharsRefused.size());
			if (smallCapsCharsAccepted.size() > smallCapsCharsRefused.size()) {
				smallCapsChars.addAll(smallCapsCharsRefused);
				smallCapsCharsRefused.clear();
				if (debug) System.out.println("   ==> re-considering refused small-caps due to many corrections");
				
				//	TODO if we've had a good few small-caps corrections, include their neighbors in analysis (even though they might actually have lower case best optical match)
			}
			
			//	run neighborhood analysis for remaining potential small-caps
			for (int c = 0; c < chars.length; c++) {
				if (charImages[c] == null)
					continue;
				if (bestCims[c] == null)
					continue;
				if (bestCims[c] == UNICODE_MAPPING_UNRENDERABLE)
					continue;
				if (!smallCapsChars.contains(new Integer(c)))
					continue;
				if (!isSmallCapsMatch(chars[c], ((String) pFont.ucMappings.get(charCodes[c])))) {
					if (chars[c] == Character.toUpperCase(chars[c]))
						continue;
					if (chars[c] == bestCims[c].match.ch)
						continue;
				}
				
				boolean lowerCaseOK = true;
				if (debug) System.out.println(" - double-checking potential small-caps char (neighborhood) " + chars[c]);
				
				//	get basic data
				Integer chc = charCodes[c];
				
				//	get usage stats
				CharUsageStats cus = pFont.getCharUsageStats(chc);
				if (cus == null) {
					if (debug) System.out.println("   ==> usage stats not found");
					continue;
				}
				int lcScore = 0;
				int ucScore = 0;
				if (debug) System.out.println(" - doing neighborhood analysis for potential small-caps char " + chars[c] + " in font " + pFont.name);
				
				//	count lower case predecessors as indicators for lower case, and upper case predecessors as weak indicatos for small-caps
				for (Iterator pit = cus.predecessors.iterator(); pit.hasNext();) {
					Integer pChc = ((Integer) pit.next());
					String pch = pFont.getUnicode(pChc.intValue());
					if (pch == null)
						continue;
					if (pch.toLowerCase().equals(pch.toUpperCase())) { /* don't count neighbors that don't have a case*/ }
					else if (pch.equals(pch.toUpperCase()))
						ucScore += 1;
					else lcScore += 2;
				}
				if (debug) System.out.println(" - after predecessors, lower case score is " + lcScore + ", small-caps score is " + ucScore);
				
				//	count upper case successors as indicators for small-caps, and lower case successors as weak indicators for lower case
				for (Iterator sit = cus.successors.iterator(); sit.hasNext();) {
					Integer sChc = ((Integer) sit.next());
					String sch = pFont.getUnicode(sChc.intValue());
					if (sch == null)
						continue;
					if (sch.toLowerCase().equals(sch.toUpperCase())) { /* don't count neighbors that don't have a case*/ }
					else if (sch.equals(sch.toUpperCase()))
						ucScore += 2;
					else lcScore += 1;
				}
				if (debug) System.out.println(" - after successors, lower case score is " + lcScore + ", small-caps score is " + ucScore);
				
				//	check scores
				lowerCaseOK = (lcScore > ucScore);
				
				//	this one looks OK by the means at hand
				if (lowerCaseOK) {
					smallCapsCharsRefused.add(new Integer(c));
					pFont.mapUnicode(chc, ("" + Character.toLowerCase(chars[c])), false);
//					pFont.mapUnicode(glyphKeys[c], ("" + Character.toLowerCase(chars[c])));
					pFont.setCharImage(chc.intValue(), StringUtils.getCharName(Character.toLowerCase(chars[c])), charImages[c].img, charImages[c].path);
//					pFont.setCharImage(glyphKeys[c], charImages[c].img, charImages[c].path);
					if (debug) System.out.println("   ==> looks OK in lower case");
					if (DEBUG_SHOW_SMALL_CAPS_CHAR_MATCHES) PdfCharDecoder.displayCharMatch(bestCims[c], "Small-caps match refused");
				}
				
				//	do actual correction
				else {
					smallCapsCharsAccepted.add(new Integer(c));
					pFont.mapUnicode(chc, ("" + Character.toUpperCase(chars[c])), false);
//					pFont.mapUnicode(glyphKeys[c], ("" + Character.toUpperCase(chars[c])));
					pFont.setCharImage(chc.intValue(), StringUtils.getCharName(Character.toUpperCase(chars[c])), charImages[c].img, charImages[c].path);
//					pFont.setCharImage(glyphKeys[c], charImages[c].img, charImages[c].path);
					if (debug) System.out.println("   ==> small-caps corrected " + chc + " to " + Character.toUpperCase(chars[c]));
					if (DEBUG_SHOW_SMALL_CAPS_CHAR_MATCHES) PdfCharDecoder.displayCharMatch(bestCims[c], "Small-caps match accepted");
				}
				
				//	mark as handled either way
				smallCapsChars.remove(new Integer(c));
			}
			
			//	collect small-caps neighbors (we might even have an actual small-cap Unicode-mapped _and_ decoded to lower case)
			HashSet smallCapsNeighborCharCodes = new HashSet();
			for (Iterator scait = smallCapsCharsAccepted.iterator(); scait.hasNext();) {
				Integer scai = ((Integer) scait.next());
				Integer scaChc = charCodes[scai.intValue()];
				CharUsageStats scaCus = pFont.getCharUsageStats(scaChc);
				if (scaCus == null)
					continue;
				for (Iterator pit = scaCus.predecessors.iterator(); pit.hasNext();)
					smallCapsNeighborCharCodes.add(pit.next());
				for (Iterator sit = scaCus.successors.iterator(); sit.hasNext();)
					smallCapsNeighborCharCodes.add(sit.next());
			}
			
			//	map small-caps neighbors to character indices for handling
			for (int c = 0; c < chars.length; c++) {
				if (charImages[c] == null)
					continue;
				if (bestCims[c] == null)
					continue;
				if (bestCims[c] == UNICODE_MAPPING_UNRENDERABLE)
					continue;
				if (smallCapsNeighborCharCodes.contains(charCodes[c]))
					smallCapsChars.add(new Integer(c));
			}
			smallCapsChars.removeAll(smallCapsCharsAccepted);
			
			//	run neighborhood analysis for neighbots of accepted small-caps
			for (int c = 0; c < chars.length; c++) {
				if (charImages[c] == null)
					continue;
				if (bestCims[c] == null)
					continue;
				if (bestCims[c] == UNICODE_MAPPING_UNRENDERABLE)
					continue;
				if (!smallCapsChars.contains(new Integer(c)))
					continue;
				if (chars[c] == Character.toUpperCase(chars[c]))
					continue;
				
				boolean lowerCaseOK = true;
				if (debug) System.out.println(" - double-checking potential small-caps char (as neighbor) " + chars[c]);
				
				//	get basic data
				Integer chc = charCodes[c];
				
				//	get usage stats
				CharUsageStats cus = pFont.getCharUsageStats(chc);
				if (cus == null) {
					if (debug) System.out.println("   ==> usage stats not found");
					continue;
				}
				int lcScore = 0;
				int ucScore = 0;
				if (debug) System.out.println(" - doing neighborhood analysis for potential small-caps char " + chars[c] + " in font " + pFont.name);
				
				//	count lower case predecessors as indicators for lower case, and upper case predecessors as weak indicatos for small-caps
				for (Iterator pit = cus.predecessors.iterator(); pit.hasNext();) {
					Integer pChc = ((Integer) pit.next());
					String pch = pFont.getUnicode(pChc.intValue());
					if (pch == null)
						continue;
					if (pch.equals(pch.toUpperCase()))
						ucScore += 1;
					else lcScore += 2;
				}
				if (debug) System.out.println(" - after predecessors, lower case score is " + lcScore + ", small-caps score is " + ucScore);
				
				//	count upper case successors as indicators for small-caps, and lower case successors as weak indicators for lower case
				for (Iterator sit = cus.successors.iterator(); sit.hasNext();) {
					Integer sChc = ((Integer) sit.next());
					String sch = pFont.getUnicode(sChc.intValue());
					if (sch == null)
						continue;
					if (sch.equals(sch.toUpperCase()))
						ucScore += 2;
					else lcScore += 1;
				}
				if (debug) System.out.println(" - after successors, lower case score is " + lcScore + ", small-caps score is " + ucScore);
				
				//	check scores
				lowerCaseOK = (lcScore > ucScore);
				
				//	this one looks OK by the means at hand
				if (lowerCaseOK) {
					smallCapsCharsRefused.add(new Integer(c));
					pFont.mapUnicode(chc, ("" + Character.toLowerCase(chars[c])), false);
//					pFont.mapUnicode(glyphKeys[c], ("" + Character.toLowerCase(chars[c])));
					pFont.setCharImage(chc.intValue(), StringUtils.getCharName(Character.toLowerCase(chars[c])), charImages[c].img, charImages[c].path);
//					pFont.setCharImage(glyphKeys[c], charImages[c].img, charImages[c].path);
					if (debug) System.out.println("   ==> looks OK in lower case");
					if (DEBUG_SHOW_SMALL_CAPS_CHAR_MATCHES) PdfCharDecoder.displayCharMatch(bestCims[c], "Small-caps match refused");
				}
				
				//	do actual correction
				else {
					smallCapsCharsAccepted.add(new Integer(c));
					pFont.mapUnicode(chc, ("" + Character.toUpperCase(chars[c])), false);
//					pFont.mapUnicode(glyphKeys[c], ("" + Character.toUpperCase(chars[c])));
					pFont.setCharImage(chc.intValue(), StringUtils.getCharName(Character.toUpperCase(chars[c])), charImages[c].img, charImages[c].path);
//					pFont.setCharImage(glyphKeys[c], charImages[c].img, charImages[c].path);
					if (debug) System.out.println("   ==> small-caps corrected " + chc + " to " + Character.toUpperCase(chars[c]));
					if (DEBUG_SHOW_SMALL_CAPS_CHAR_MATCHES) PdfCharDecoder.displayCharMatch(bestCims[c], "Small-caps match accepted");
				}
				
				//	mark as handled either way
				smallCapsChars.remove(new Integer(c));
			}
		}
		
		//	reset chars with known names to mark them for re-evaluation (required with fonts that use arbitrary char names)
		else {
			if (debug) System.out.println(" ==> mis-match");
			
			//	cancel out what we have only with OCR enabled
			if ((charSet != NO_DECODING) && (charSet != RENDER_ONLY))
				for (int c = 0; c < bestCims.length; c++) {
					if (!charSet.decodeUnicodeMapped() && pFont.ucMappings.containsKey(charCodes[c]))
						continue;
					if (bestCims[c] != UNICODE_MAPPING_UNRENDERABLE)
						bestCims[c] = null;
				}
		}
		
		//	anything left to decode?
		int unresolvedCharCount = 0;
		for (int c = 0; c < chars.length; c++) {
			if ((charImages[c] != null) && (bestCims[c] == null))
				unresolvedCharCount++;
		}
		
		//	use OCR to decode glyphs that are lacking mapped characters as well as those whose mapped characters do not match sufficiently well
		if (FORCE_OCR_DECODING || ((charSet != NO_DECODING) && (charSet != RENDER_ONLY) && (unresolvedCharCount != 0)))
			ocrDecodeChars(pFont, chars, charImages, bestCims, badBestCims, charSet, maxDescent, charCodes, charNames, glyphKeys, serifFonts, sansFonts, monoFonts, pm, debug);
		
		//	count out font style only now
		pm.setInfo("   - detecting font style");
		int styleCharCount = 0;
		int serifCount = 0;
		int monoCount = 0;
		int boldCount = 0;
		int italicsCount = 0;
		for (int c = 0; c < chars.length; c++) {
			if (bestCims[c] == null)
				continue;
			if (bestCims[c] == UNICODE_MAPPING_UNRENDERABLE)
				continue;
			styleCharCount++;
			if ((bestCims[c].match.fontStyle & Font.BOLD) != 0)
				boldCount++;
			if ((bestCims[c].match.fontStyle & Font.ITALIC) != 0)
				italicsCount++;
			if ((bestCims[c].match.fontStyle & PdfCharDecoder.SERIF) != 0)
				serifCount++;
			if ((bestCims[c].match.fontStyle & PdfCharDecoder.MONOSPACED) != 0)
				monoCount++;
		}
		
		//	set style if we have enough character matches
		if (styleCharCount != 0) {
			pFont.bold = ((boldCount * 2) > styleCharCount);
			pFont.italics = ((italicsCount * 2) > styleCharCount);
			pFont.serif = ((serifCount * 2) > styleCharCount);
			pFont.monospaced = ((monoCount * 2) > styleCharCount);
			if (debug) System.out.println("Font style vote between " + styleCharCount + " chars: " + boldCount + " bold, " + italicsCount + " italics, " + serifCount + " serif, " + monoCount + " monospaced");
		}
		
		//	correct base font if required TODO consider sans-serif and monspaced fonts as well
		if ((pFont.baseFont == null) || (pFont.bold != pFont.baseFont.bold) || (pFont.italics != pFont.baseFont.italic)) {
			String bfn = "Times-";
			if (pFont.bold && pFont.italics)
				bfn += "BoldItalic";
			else if (pFont.bold)
				bfn += "Bold";
			else if (pFont.italics)
				bfn += "Italic";
			else bfn += "Roman";
			pFont.setBaseFont(bfn, false);
		}
	}
	
	private static final CharImageMatch UNICODE_MAPPING_UNRENDERABLE = new CharImageMatch(null, null, 0, 0, 0, false);
	
	private static boolean isSmallCapsMatch(char ch, String ucMapping) {
		if (ucMapping == null)
			return false;
		else if (ucMapping.length() != 1)
			return false;
		else if (ucMapping.charAt(0) == ch)
			return false;
		else return (ch == Character.toUpperCase(ucMapping.charAt(0)));
	}
	
	private static void checkImplicitSpaces(PdfFont pFont, CharImage[] charImages, char[] chars, Integer[] charCodes, String[] charNames, Object[] glyphKeys, boolean debug) {
		
		//	check average word length (will be well below 2 for implicit space fonts)
		float avgFontWordLength = pFont.getAverageWordLength();
		if (debug) System.out.println(" - average font word length is " + avgFontWordLength);
		if (avgFontWordLength > 2) {
			if (debug) System.out.println(" ==> based on word length, char widths appear to make sense");
			return;
		}
		
		//	measure maximum char height and relation to nominal font box
		int maxCharHeight = 0;
		for (int c = 0; c < charImages.length; c++) {
			if (charImages[c] != null)
				maxCharHeight = Math.max(maxCharHeight, charImages[c].box.getHeight());
		}
		float charHeightRel = (maxCharHeight / (pFont.ascent - pFont.descent));
		if (debug) System.out.println(" - maximum height is " + maxCharHeight + " for " + (pFont.ascent - pFont.descent) + ", relation is " + charHeightRel);
		
		//	compare nominal to measured char widths
		float ncCharWidthRelSum = 0;
		int ncCharWidthRelCount = 0;
		for (int c = 0; c < charImages.length; c++) {
			if (charImages[c] == null)
				continue;
			
			//	get basic data
			String chn = charNames[c];
			Integer chc = charCodes[c];
			
			//	get nominal width and compare to actual width
//			float nCharWidth = pFont.getCharWidth(new Character((char) chc.intValue()));
			float nCharWidth = pFont.getCharWidth((char) chc.intValue());
			float nCharWidthRel = ((charImages[c].box.getWidth() * 1000) / nCharWidth);
//			if (debug) System.out.println(" - nominal width of char " + c + " (SID " + sid + ", " + chn + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ") is " + nCharWidth);
			if (debug) System.out.println(" - nominal width of char " + c + " (" + chn + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ") is " + nCharWidth);
			if (debug) System.out.println(" - actual width is " + charImages[c].box.getWidth() + ", relation is " + nCharWidthRel);
			float cCharWidth = ((nCharWidth * nCharWidthRel) / charHeightRel);
			float ncCharWidthRel = (nCharWidth / cCharWidth);
			if (debug) System.out.println(" --> computed width is " + cCharWidth + ", relation is " + ncCharWidthRel);
			if (nCharWidth == 0)
				continue;
			ncCharWidthRelSum += ncCharWidthRel;
			ncCharWidthRelCount++;
		}
		float avgNcCharWidthRel = ((ncCharWidthRelCount == 0) ? 0 : (ncCharWidthRelSum / ncCharWidthRelCount));
		if (debug) System.out.println(" --> average nominal char width to computed char width relation is " + avgNcCharWidthRel + " from " + ncCharWidthRelCount + " chars");
		
		//	this font seams to indicate sincere char widths
		//	TODObelow verify threshold, might be safer off with 2
//		if (avgNcCharWidthRel < 1.5) {
		if (avgNcCharWidthRel < 2) {
			if (debug) System.out.println(" ==> char widths appear to make sense");
			return;
		}
		
		//	nominal char width way larger than measured char width, we have implicit spaces
		if (debug) System.out.println(" ==> char widths appear to imply spaces");
		pFont.hasImplicitSpaces = true;
		pFont.monospaced = false; // TODO actually this _might_ still hold, but what are the odds ?!?
		
		//	add measured char widths
		for (int c = 0; c < charImages.length; c++) {
			if (charImages[c] == null)
				continue;
			
			//	get basic data
			Integer chc = charCodes[c];
			
			//	store actual width TODO measure these things on rendered glyph paths instead !!!
			float nCharWidth = pFont.getCharWidth(new Character((char) chc.intValue()));
			float nCharWidthRel = ((charImages[c].box.getWidth() * 1000) / nCharWidth);
			float cCharWidth = ((nCharWidth * nCharWidthRel) / charHeightRel);
			pFont.setMeasuredCharWidth(chc, cCharWidth);
//			pFont.setMeasuredCharWidth(glyphKeys[c], cCharWidth);
		}
	}
	
	private static final boolean FORCE_OCR_DECODING = false;
	
	private static void ocrDecodeChars(PdfFont pFont, char[] chars, CharImage[] charImages, CharImageMatch[] bestCims, CharImageMatch[] badBestCims, FontDecoderCharset charSet, float maxDescent, Integer[] charCodes, String[] charNames, Object[] glyphKeys, Font[] serifFonts, Font[] sansFonts, Font[] monoFonts, ProgressMonitor pm, boolean debug) {
//		if (!"PBBHDA+AdvOTd5f4e5b7.B".equals(pFont.name))
//			return;
//		if (!"PAPBPP+AdvOT7668bbdf".equals(pFont.name))
//			return;
//		if (!"BJGECJ+TT7Ao00".equals(pFont.name))
//			return;
//		
		
		/* TODO to test OCR backed decoding of letters:
		 * - switch off early finish detection above ...
		 * - ... and null out any char matches
		 */
		
		//	TODO somehow handle small-caps occurring in font alongside normal upper and lower case characters (upper case in duplicates)
		//	TODO TEST true type font with added small-caps in page 17 (bibliography author names) of londt_2014d.pdf
		
		//	union argument charset with whatever characters are Unicode mapped to in argument font
		final HashSet unicodeMappedChars = new HashSet();
		for (Iterator ccit = pFont.ucMappings.keySet().iterator(); ccit.hasNext();) {
			Object ccObj = ccit.next();
			Object ucChObj = pFont.ucMappings.get(ccObj);
			if (ucChObj != null) {
				String ucCh = ucChObj.toString();
				if (ucCh.length() == 1)
					unicodeMappedChars.add(new Character(ucCh.charAt(0)));
				else if (ucCh.length() != 0) {
					char cCh = StringUtils.getCharForName(ucCh);
					if (cCh != 0) // catches ligatures, first and foremost
						unicodeMappedChars.add(new Character(cCh));
				}
			}
		}
		charSet = FontDecoderCharset.union(charSet, new FontDecoderCharset("FontUnicodeMapping") {
			public boolean containsChar(char ch) {
				return unicodeMappedChars.contains(new Character(ch));
			}
		});
		
		//	cache character images to speed up matters
		pm.setInfo("   - OCR decoding remaining characters");
		final float[] cacheHitRate = {0};
		HashMap cache = new HashMap() {
			int lookups = 0;
			int hits = 0;
			public Object get(Object key) {
				this.lookups ++;
				Object value = super.get(key);
				if (value != null)
					this.hits++;
				cacheHitRate[0] = (((float) this.hits) / this.lookups);
				return value;
			}
		};
		
		//	get top matches matches for remaining characters, and collect char codes and names
		CharImageMatch[][] topCims = new CharImageMatch[chars.length][];
		boolean[] isOcrDecoded = new boolean[chars.length];
		for (int c = 0; c < chars.length; c++) {
			if (debug) System.out.println("Assessing char " + c + " (" + charCodes[c] + "/" + charNames[c] + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ")");
			if (charImages[c] == null) {
				if (debug) System.out.println(" ==> not rendered (1)");
				continue;
			}
			
			//	we have a Unicode mpping we cannot verify
			if (bestCims[c] == UNICODE_MAPPING_UNRENDERABLE) {
				isOcrDecoded[c] = false;
				if (debug) System.out.println(" ==> not rendered (2)");
				continue;
			}
			
			//	we already have a match for this one (automatically catches existing Unicode mappings if forced by charset to retained above)
			if (bestCims[c] != null) {
				topCims[c] = new CharImageMatch[1];
				topCims[c][0] = bestCims[c];
				isOcrDecoded[c] = false;
				if (debug) System.out.println(" ==> reliably mapped to '" + bestCims[c].match.ch + "'");
				continue;
			}
			
			//	perform match (keep matches twice to allow for ressurrection of eliminated ones)
			pm.setInfo("     - Getting OCR matches for char " + c + " (" + charCodes[c] + "/" + charNames[c] + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ")");
			if (debug) System.out.println("Getting OCR matches for char " + c + " (" + charCodes[c] + "/" + charNames[c] + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ")");
			topCims[c] = getCharsForImage(chars[c], charImages[c], charSet, serifFonts, sansFonts, monoFonts, cache, false);
			
			//	check OCR matches against retained verification match ... and use latter if better
			if (badBestCims[c] == null)
				isOcrDecoded[c] = true;
			else if ((topCims[c] == null) || (topCims[c].length == 0)) {
				if (debug) System.out.println(" ==> falling back to shunned verification match '" + badBestCims[c].match.ch + "'");
				topCims[c] = new CharImageMatch[1];
				topCims[c][0] = badBestCims[c];
				isOcrDecoded[c] = false;
			}
			else if (topCims[c][0].sim < badBestCims[c].sim) {
				if (debug) System.out.println(" ==> reverting to shunned verification match '" + badBestCims[c].match.ch + "' at similarity " + badBestCims[c].sim + " over best OCR match '" + topCims[c][0].match.ch + "' at similarity " + topCims[c][0].sim);
				topCims[c] = new CharImageMatch[1];
				topCims[c][0] = badBestCims[c];
				isOcrDecoded[c] = false;
			}
			else isOcrDecoded[c] = true; // TODO could be we need to (re-)assess this only after elimination process below
		}
		
		//	handle unambiguous matches
		HashSet assignedChars = new HashSet();
		for (int c = 0; c < chars.length; c++) {
			if (topCims[c] == null)
				continue;
			
			//	unambiguous match
			if (topCims[c].length == 1) {
				bestCims[c] = topCims[c][0];
				assignedChars.add(new Integer((int) topCims[c][0].match.ch));
				pm.setInfo("     - Unambiguous match for char " + c + " (" + charNames[c] + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + "): '" + topCims[c][0].match.ch + "'");
				if (debug) System.out.println("Unambiguous match for char " + c + " (" + charNames[c] + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + "): '" + topCims[c][0].match.ch + "'");
			}
		}
		
		//	eliminate unambiguously assigned matches from other characters
		for (boolean cimEliminated = true; cimEliminated;) {
			cimEliminated = false;
			for (int c = 0; c < chars.length; c++) {
				if (topCims[c] == null)
					continue;
				if (topCims[c].length == 0)
					continue;
				
				//	this one has been handled above
				if (bestCims[c] != null)
					continue;
				
				//	eliminate matches unambiguously assigned otherwise
				ArrayList topCimList = new ArrayList();
				for (int m = 0; m < topCims[c].length; m++) {
					if (!assignedChars.contains(new Integer((int) topCims[c][m].match.ch)))
						topCimList.add(topCims[c][m]);
				}
				
				//	store what is left
				if (topCimList.size() < topCims[c].length) {
					topCims[c] = ((CharImageMatch[]) topCimList.toArray(new CharImageMatch[topCimList.size()]));
					cimEliminated = true;
				}
				
				//	we have a new unambiguous match
				if (topCims[c].length == 1) {
					bestCims[c] = topCims[c][0];
					assignedChars.add(new Integer((int) topCims[c][0].match.ch));
					pm.setInfo("     - Unambiguous match for char " + c + " (" + charNames[c] + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + "): '" + topCims[c][0].match.ch + "'");
					if (debug) System.out.println("Unambiguous match for char " + c + " (" + charNames[c] + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + "): '" + topCims[c][0].match.ch + "'");
					continue;
				}
				
				//	check if named or unicode mapped match among top matches (small caps will still be caught below)
				//	TODO make damn sure this approach doesn't resurrect small-caps problems
				/* TODOne if we have a potential small-caps trouble maker:
				 * - retain _both_ cases,
				 * - leave bestCim blank, 
				 * - and let selector algorithm below do the rest */
				CharImageMatch namedCim = null;
//				
//				//	accept unicode mapped char if among top matches (small caps will still be caught below)
//				if ((namedCim == null) && pFont.ucMappings.containsKey(charCodes[c])) {
//					String ucStr = ((String) pFont.ucMappings.get(charCodes[c]));
//					for (int m = 0; m < topCims[c].length; m++) {
//						String cimStr = ("" + topCims[c][m].match.ch);
//						if (ucStr.equals(cimStr)) {
//							namedCim = topCims[c][m];
//							pm.setInfo("     - Unicode mapping backed match for char " + c + " (" + charNames[c] + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + "): '" + topCims[c][m].match.ch + "'");
//							if (debug) System.out.println("Unicode mapping backed match for char " + c + " (" + charNames[c] + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + "): '" + topCims[c][m].match.ch + "'");
//							break;
//						}
//						String nCimStr = StringUtils.getNormalForm(topCims[c][m].match.ch);
//						if ((nCimStr.length() > 1) && ucStr.equals(nCimStr)) {
//							namedCim = topCims[c][m];
//							pm.setInfo("     - Unicode mapping backed match for char " + c + " (" + charNames[c] + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + "): '" + topCims[c][m].match.ch + "'");
//							if (debug) System.out.println("Unicode mapping backed match for char " + c + " (" + charNames[c] + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + "): '" + topCims[c][m].match.ch + "'");
//							break;
//						}
//					}
//				}
				
				//	eliminate matches unambiguously assigned otherwise
				topCimList.clear();
				for (int m = 0; m < topCims[c].length; m++) {
					if (topCims[c][m].match.ch == chars[c])
						topCimList.add(topCims[c][m]);
					else if (("cijopsuvwxyzCIJOPSUVWXYZ".indexOf(chars[c]) != -1) && (Character.toLowerCase(topCims[c][m].match.ch) == Character.toLowerCase(chars[c])))
						topCimList.add(topCims[c][m]);
				}
				
				//	any matches?
				if (topCimList.size() == 1) {
					namedCim = ((CharImageMatch) topCimList.get(0));
					pm.setInfo("     - Mapping backed match for char " + c + " (" + charNames[c] + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + "): '" + namedCim.match.ch + "'");
					if (debug) System.out.println("Mapping backed match for char " + c + " (" + charNames[c] + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + "): '" + namedCim.match.ch + "'");
					cimEliminated = true;
				}
				else if ((topCimList.size() != 0) && (topCimList.size() < topCims[c].length)) {
					topCims[c] = ((CharImageMatch[]) topCimList.toArray(new CharImageMatch[topCimList.size()]));
					cimEliminated = true;
				}
				
				//	any named matches?
				if (namedCim != null) {
					bestCims[c] = namedCim;
					assignedChars.add(new Integer((int) namedCim.match.ch));
					topCims[c] = new CharImageMatch[1];
					topCims[c][0] = bestCims[c];
					cimEliminated = true;
					continue;
				}
			}
		}
		
		//	compute average of measurements (we need them here for gap cutoff)
		CharMatchFontMetrics cmfm = computeFontMetrics(bestCims);
		if (debug) System.out.println("Averages: vertical center shift is " + cmfm.uaRelVerticalCenterShift + ", scale log " + cmfm.uaScaleLogX + "/" + cmfm.uaScaleLogY + ", x-height ascender shift is " + cmfm.uaRelXHeightAscenderShift + ", cap height ascender shift is " + cmfm.uaRelCapHeightAscenderShift + ", descender shift is " + cmfm.uaRelDescenderShift);
		
		/* cut off at any gap of at least 1 percentage point whose high end is
		 * less than half from 100% than low end; request scaling and vertical
		 * shift within 10% of font average for additional security */
		for (int c = 0; c < chars.length; c++) {
			if (topCims[c] == null)
				continue;
			if (topCims[c].length == 0)
				continue;
			
			//	this one has been handled above
			if (bestCims[c] != null)
				continue;
			
			//	update status
			pm.setInfo("     - Assessing OCR matches for char " + c + " (" + charNames[c] + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ")");
			if (debug) System.out.println("Assessing OCR matches for char " + c + " (" + charNames[c] + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ")");
			
			//	find similarity gap in matches and do eimination
			ArrayList topCimList = new ArrayList();
			topCimList.add(topCims[c][0]);
			CharImageMatch lastCim = topCims[c][0];
			for (int m = 1; m < topCims[c].length; m++) {
				
				//	too small a gap between these two (in absolute or relative terms)
				if ((lastCim.sim - topCims[c][m].sim) <= 0.01) {
					topCimList.add(topCims[c][m]);
					lastCim = topCims[c][m];
					continue;
				}
				else if (((1 - lastCim.sim) * 2) >= (1 - topCims[c][m].sim)) {
					topCimList.add(topCims[c][m]);
					lastCim = topCims[c][m];
					continue;
				}
				
				//	reward glyph size scaling in range of average of unambiguous matches (cut somewhat more slack on horizontal scaling, as narrow characters vary more in that department)
				if (!Float.isNaN(cmfm.uaScaleLogX) && (Math.abs(cmfm.uaScaleLogX - lastCim.scaleLogX) > 0.2)) {
					topCimList.add(topCims[c][m]);
					lastCim = topCims[c][m];
					continue;
				}
				else if (!Float.isNaN(cmfm.uaScaleLogY) && (Math.abs(cmfm.uaScaleLogY - lastCim.scaleLogY) > 0.1)) {
					topCimList.add(topCims[c][m]);
					lastCim = topCims[c][m];
					continue;
				}
				else if (!Float.isNaN(cmfm.uaRelVerticalCenterShift) && (Math.abs(cmfm.uaRelVerticalCenterShift - lastCim.vCenterShift) > 0.1)) {
					topCimList.add(topCims[c][m]);
					lastCim = topCims[c][m];
					continue;
				}
				
				//	gut off from here
				if (debug) {
					System.out.println(" - similarity gap cut matches after " + lastCim.match.ch + " (" + ((int) lastCim.match.ch) + ") at " + lastCim.sim + ", shift " + lastCim.vCenterShift + ", scale " + lastCim.scaleLogX + "/" + lastCim.scaleLogY + ", " + PdfCharDecoder.getCharClass(lastCim.match.ch));
					System.out.println(" - first match after gap is " + topCims[c][m].match.ch + " (" + ((int) topCims[c][m].match.ch) + ") at " + topCims[c][m].sim + ", shift " + topCims[c][m].vCenterShift + ", scale " + topCims[c][m].scaleLogX + "/" + topCims[c][m].scaleLogY + ", " + PdfCharDecoder.getCharClass(topCims[c][m].match.ch));
				}
				break;
			}
			
			//	remove matches that are way out of proportion (scale log diff above 1 (factor 2)) or place (vertical shift diff above 1/4)
			for (int m = 0; m < topCimList.size(); m++) {
				CharImageMatch cim = ((CharImageMatch) topCimList.get(m));
				if (!Float.isNaN(cmfm.uaScaleLogX) && (Math.abs(cmfm.uaScaleLogX - cim.scaleLogX) > 1)) {
					topCimList.remove(m--);
					if (debug) System.out.println(" - removed match (x-scale diff) " + cim.match.ch + " (" + ((int) cim.match.ch) + ") at " + cim.sim + ", shift " + cim.vCenterShift + ", scale " + cim.scaleLogX + "/" + cim.scaleLogY + ", " + PdfCharDecoder.getCharClass(cim.match.ch));
					continue;
				}
				if (!Float.isNaN(cmfm.uaScaleLogY) && (Math.abs(cmfm.uaScaleLogY - cim.scaleLogY) > 1)) {
					topCimList.remove(m--);
					if (debug) System.out.println(" - removed match (y-scale diff) " + cim.match.ch + " (" + ((int) cim.match.ch) + ") at " + cim.sim + ", shift " + cim.vCenterShift + ", scale " + cim.scaleLogX + "/" + cim.scaleLogY + ", " + PdfCharDecoder.getCharClass(cim.match.ch));
					continue;
				}
				if (!Float.isNaN(cmfm.uaRelVerticalCenterShift) && (Math.abs(cmfm.uaRelVerticalCenterShift - cim.vCenterShift) > 0.25)) {
					topCimList.remove(m--);
					if (debug) System.out.println(" - removed match (vertical shift) " + cim.match.ch + " (" + ((int) cim.match.ch) + ") at " + cim.sim + ", shift " + cim.vCenterShift + ", scale " + cim.scaleLogX + "/" + cim.scaleLogY + ", " + PdfCharDecoder.getCharClass(cim.match.ch));
					continue;
				}
			}
			
			//	anything eliminated?
			if (topCimList.size() == topCims[c].length)
				continue;
			if (debug) System.out.println(" ==> retained " + topCimList.size() + " matches out of " + topCims[c].length);
			
			//	store what is left
			topCims[c] = ((CharImageMatch[]) topCimList.toArray(new CharImageMatch[topCimList.size()]));
		}
		
		//	eliminate unambiguously assigned matches from other characters
		for (boolean cimEliminated = true; cimEliminated;) {
			cimEliminated = false;
			for (int c = 0; c < chars.length; c++) {
				if (topCims[c] == null)
					continue;
				if (topCims[c].length == 0)
					continue;
				
				//	this one has been handled above
				if (bestCims[c] != null)
					continue;
				
				//	eliminate matches unambiguously assigned otherwise
				ArrayList topCimList = new ArrayList();
				for (int m = 0; m < topCims[c].length; m++) {
					if (!assignedChars.contains(new Integer((int) topCims[c][m].match.ch)))
						topCimList.add(topCims[c][m]);
				}
				
				//	store what is left
				if (topCimList.size() < topCims[c].length) {
					topCims[c] = ((CharImageMatch[]) topCimList.toArray(new CharImageMatch[topCimList.size()]));
					cimEliminated = true;
				}
				
				//	we have a new unambiguous match
				if (topCims[c].length == 1) {
					bestCims[c] = topCims[c][0];
					assignedChars.add(new Integer((int) topCims[c][0].match.ch));
					pm.setInfo("     - Unambiguous match for char " + c + " (" + charNames[c] + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + "): '" + topCims[c][0].match.ch + "'");
					if (debug) System.out.println("Unambiguous match for char " + c + " (" + charNames[c] + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + "): '" + topCims[c][0].match.ch + "'");
				}
			}
		}
		
		//	index matches by char codes, and also tray up all matches 
		CharImageMatch[][] allTopCims = new CharImageMatch[chars.length][];
		HashMap topCimsByCharCodes = new HashMap();
		for (int c = 0; c < chars.length; c++)
			if (topCims[c] != null) {
				allTopCims[c] = topCims[c];
				topCimsByCharCodes.put(charCodes[c], topCims[c]);
			}
		
		//	figure out best matches (iteratively)
		ScoredCharImageMatch[][] scoredCims = new ScoredCharImageMatch[topCims.length][];
		int idleCsrCount = 0;
		for (int csr = 1;; csr += Math.max((csr / 2), 1)) {
			if (debug) System.out.println("Starting char scoring round " + csr + ", idle since " + idleCsrCount);
			idleCsrCount++;
			
			//	handle unambiguous matches
			int aCharCount = 0;
			for (int c = 0; c < chars.length; c++) {
				if (topCims[c] == null)
					continue;
				
				//	we're only after the unambiguous ones here
				if (topCims[c].length != 1) {
					aCharCount++;
					continue;
				}
				
				//	store match
				bestCims[c] = topCims[c][0];
				assignedChars.add(new Integer((int) topCims[c][0].match.ch));
			}
			
			//	anything left to work on?
			if (aCharCount == 0)
				break;
			
			//	re-compute measurements
			cmfm = computeFontMetrics(bestCims);
			if (debug) System.out.println("Averages: vertical center shift is " + cmfm.uaRelVerticalCenterShift + ", scale log " + cmfm.uaScaleLogX + "/" + cmfm.uaScaleLogY + ", x-height ascender shift is " + cmfm.uaRelXHeightAscenderShift + ", cap height ascender shift is " + cmfm.uaRelCapHeightAscenderShift + ", descender shift is " + cmfm.uaRelDescenderShift);
			
			//	inspect all remaining chars
			for (int c = 0; c < chars.length; c++) {
				if (topCims[c] == null)
					continue;
				if (topCims[c].length == 0)
					continue;
				
				//	this one has been handled above
				if (bestCims[c] != null)
					continue;
				
				//	update status
				pm.setInfo("     - Scoring matches of char " + c + " (" + charNames[c] + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ")");
				if (debug) System.out.println("Scoring matches of char " + c + " (" + charNames[c] + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ")");
				
				//	initialize scoring
				float[] topCimScores = new float[topCims[c].length];
				Arrays.fill(topCimScores, 0);
				scoredCims[c] = new ScoredCharImageMatch[topCims[c].length];
				
				//	score punctuation marks by scaleLogXDiff, even without usage stats (should help distinguish dashes !!!)
				for (int m = 0; m < topCims[c].length; m++) {
					String charClass = PdfCharDecoder.getCharClass(topCims[c][m].match.ch);
					if (!"P".equals(charClass))
						continue;
					
					//	reward glyph size scaling in range of average of unambiguous matches
					float scaleLogXDiff = 1;
					if (!Float.isNaN(cmfm.uaScaleLogX)) {
						scaleLogXDiff = Math.abs(cmfm.uaScaleLogX - topCims[c][m].scaleLogX);
						scaleLogXDiff = Math.max(scaleLogXDiff, 0.2f);
					}
					
					//	also factor in vertical shift (measure difference in relative vertical position of char box, and multiply score by (1 - difference))
					float relVerticalCenterShiftDiff = 0;
					if (!Float.isNaN(cmfm.uaRelVerticalCenterShift))
						relVerticalCenterShiftDiff = Math.abs(cmfm.uaRelVerticalCenterShift - topCims[c][m].vCenterShift);
					
					/* do scoring, and make sure to not give all too big an
					 * advantage to punctuation marks like square brackets over
					 * letters like 'I' and 'l' - dividing by maximum scaling
					 * bonification should do the trick - as this is mostly
					 * meant to give better matching hyphens an edge over worse
					 * matching ones */
					float cimPairScore = (topCims[c][m].sim * topCims[c][m].sim * 0.2f /* 0.2f*/);
					cimPairScore /= scaleLogXDiff; // reward glyph size scaling in range of average of unambiguous matches
					cimPairScore *= (1 - relVerticalCenterShiftDiff); // reward glyph size shifting in range of average of unambiguous matches
					topCimScores[m] += (cimPairScore / topCims[c].length);
				}
				
				//	get character usage stats
				CharUsageStats cus = pFont.getCharUsageStats(charCodes[c]);
				if (cus == null) {
					if (debug) {
						System.out.println("Checking environment of char " + charCodes[c] + ", top matches are:");
						for (int m = 0; m < topCims[c].length; m++)
							System.out.println(" - " + topCims[c][m].match.ch + " (" + ((int) topCims[c][m].match.ch) + ") at " + topCims[c][m].sim + ", " + PdfCharDecoder.getCharClass(topCims[c][m].match.ch));
						System.out.println(" ==> usage stats not found");
					}
					
					//	put matches on trays for easier handling
					if (debug) System.out.println("Measurement scored matches of char " + charCodes[c] + " after round " + csr + ":");
					for (int m = 0; m < topCims[c].length; m++) {
						scoredCims[c][m] = new ScoredCharImageMatch(topCims[c][m], topCimScores[m], charCodes[c], charNames[c]);
						if (debug) System.out.println(" - " + topCims[c][m].match.ch + " (" + ((int) topCims[c][m].match.ch) + ") at " + topCims[c][m].sim + ", shift " + topCims[c][m].vCenterShift + ", scale " + topCims[c][m].scaleLogX + "/" + topCims[c][m].scaleLogY + ", " + PdfCharDecoder.getCharClass(topCims[c][m].match.ch) + " ==> " + topCimScores[m]);
					}
					continue;
				}
				
				/* use glyph (char code) neighborhood analysis to find most likely letter:
				 * - compare writing (Latin vs. Greek vs. whatever) system across neighboring chars ...
				 * - ... and penalize changes
				 * - stop at punctuation marks, though (alpha-<something>) ...
				 * - ... and treat letters and digits separately
				 */
				if (debug) {
					System.out.println("Checking environment of char " + charCodes[c] + " (" + cus.usageCount + " times), top matches are:");
					for (int m = 0; m < topCims[c].length; m++)
						System.out.println(" - " + topCims[c][m].match.ch + " (" + ((int) topCims[c][m].match.ch) + ") at " + topCims[c][m].sim + ", " + PdfCharDecoder.getCharClass(topCims[c][m].match.ch) + "." + PdfCharDecoder.getPunctuationClass(topCims[c][m].match.ch));
				}
				
				//	reward absence or presence of predecessors and successors
				for (int m = 0; m < topCims[c].length; m++) {
					
					//	reward absence of predecessors for upper case letters (without scale log diff factor, though, as lower case letters can also start words)
					boolean upperCase = ((Character.toUpperCase(topCims[c][m].match.ch) == topCims[c][m].match.ch) && (Character.toLowerCase(topCims[c][m].match.ch) != Character.toUpperCase(topCims[c][m].match.ch)));
					if (upperCase) {
						topCimScores[m] += ((topCims[c][m].sim * topCims[c][m].sim * (cus.usageCount - cus.predecessors.size())) / topCims[c].length);
						if (debug) System.out.println(" - " + topCims[c][m].match.ch + " (" + ((int) topCims[c][m].match.ch) + ") at " + topCims[c][m].sim + " scored for absence of predecessors in " + (cus.usageCount - cus.predecessors.size()) + " cases ==> " + topCimScores[m]);
					}
					
					//	reward absence of predecessors and successors, depending on punctuation mark class
					String charClass = PdfCharDecoder.getCharClass(topCims[c][m].match.ch);
					if ("P".equals(charClass)) {
						if (Gamta.spaceBefore("" + topCims[c][m].match.ch)) {
							topCimScores[m] += (((cus.usageCount - cus.predecessors.size() - cus.predecessorChars.size()) * topCims[c][m].sim * topCims[c][m].sim) / topCims[c].length);
							if (debug) System.out.println(" - " + topCims[c][m].match.ch + " (" + ((int) topCims[c][m].match.ch) + ") at " + topCims[c][m].sim + " scored for absence of predecessors in " + (cus.usageCount - cus.predecessors.size()) + " cases ==> " + topCimScores[m]);
						}
//						else topCimScores[m] += (cus.predecessors.size() * topCims[c][m].sim * topCims[c][m].sim);
						if (Gamta.spaceAfter("" + topCims[c][m].match.ch)) {
							topCimScores[m] += (((cus.usageCount - cus.successors.size() - cus.successorChars.size()) * topCims[c][m].sim * topCims[c][m].sim) / topCims[c].length);
							if (debug) System.out.println(" - " + topCims[c][m].match.ch + " (" + ((int) topCims[c][m].match.ch) + ") at " + topCims[c][m].sim + " scored for absence of successors in " + (cus.usageCount - cus.successors.size()) + " cases ==> " + topCimScores[m]);
						}
//						else topCimScores[m] += (cus.successors.size() * topCims[c][m].sim * topCims[c][m].sim);
					}
				}
				
				/* TODO give special treatment to brackets:
				 * - opening:
				 *   - reward lack of (non-punctuation) predecessor
				 *   - penalize lack of successor
				 * - closing:
				 *   - reward lack of (non-punctuation) successor
				 *   - penalize lack of predecessor
				 */
				//	TODO test with square brackets in font 'PAPBPP+AdvOT7668bbdf' of 'Carbayo_et_al-2013-Zoologica_Scripta.pdf'
				
				//	reward likely combinations with predecessors
				for (Iterator pit = cus.predecessors.iterator(); pit.hasNext();) {
					Integer pChc = ((Integer) pit.next());
					if (debug) System.out.println(" - checking predecessor " + pChc + " (" + cus.predecessors.getCount(pChc) + " times)");
					if (pChc.equals(charCodes[c])) {
						if (debug) System.out.println(" - waiving self-comparison");
						continue;
					}
					CharImageMatch[] pCims = ((CharImageMatch[]) topCimsByCharCodes.get(pChc));
					if (pCims == null) {
						if (debug) System.out.println(" - top matches not found");
						continue;
					}
					if (debug) {
						System.out.println(" - top matches are:");
						for (int p = 0; p < pCims.length; p++)
							System.out.println("   - " + pCims[p].match.ch + " (" + ((int) pCims[p].match.ch) + ") at " +  pCims[p].sim + ", " + PdfCharDecoder.getCharClass(pCims[p].match.ch) + "." + PdfCharDecoder.getPunctuationClass(pCims[p].match.ch));
					}
					for (int m = 0; m < topCims[c].length; m++) {
						String charClass = PdfCharDecoder.getCharClass(topCims[c][m].match.ch);
						String charSubClass = ("P".equals(charClass) ? PdfCharDecoder.getPunctuationClass(topCims[c][m].match.ch) : null);
						
						//	reward glyph size scaling in range of average of unambiguous matches
						float scaleLogXDiff = 1;
						if (!Float.isNaN(cmfm.uaScaleLogX)) {
							scaleLogXDiff = Math.abs(cmfm.uaScaleLogX - topCims[c][m].scaleLogX);
							scaleLogXDiff = Math.max(scaleLogXDiff, 0.2f);
						}
						float scaleLogYDiff = 1;
						if (!Float.isNaN(cmfm.uaScaleLogY)) {
							scaleLogYDiff = Math.abs(cmfm.uaScaleLogY - topCims[c][m].scaleLogY);
							scaleLogYDiff = Math.max(scaleLogYDiff, 0.2f);
						}
						
						//	also factor in vertical shift (measure difference in relative vertical position of char box, and multiply score by (1 - difference))
						float relVerticalCenterShiftDiff = 0;
						if (!Float.isNaN(cmfm.uaRelVerticalCenterShift))
							relVerticalCenterShiftDiff = Math.abs(cmfm.uaRelVerticalCenterShift - topCims[c][m].vCenterShift);
						
//						if (debug) System.out.println("     - scale log diffs are " + scaleLogXDiff + "/" + scaleLogYDiff + ", center shift diff is " + relVerticalCenterShiftDiff);
						
						//	reward pairing up with matching character class
						boolean upperCase = ((Character.toUpperCase(topCims[c][m].match.ch) == topCims[c][m].match.ch) && (Character.toLowerCase(topCims[c][m].match.ch) != Character.toUpperCase(topCims[c][m].match.ch)));
						for (int p = 0; p < pCims.length; p++) {
							if (topCims[c][m].match.ch == pCims[p].match.ch)
								continue;
							String pCharClass = PdfCharDecoder.getCharClass(pCims[p].match.ch);
							String pCharSubClass = ("P".equals(pCharClass) ? PdfCharDecoder.getPunctuationClass(pCims[p].match.ch) : null);
							boolean pUpperCase = ((Character.toUpperCase(pCims[p].match.ch) == pCims[p].match.ch) && (Character.toLowerCase(pCims[p].match.ch) != Character.toUpperCase(pCims[p].match.ch)));
							
							//	compute score
							float cimPairScore = (getCharPairScore(pCims[p].match.ch, pCharClass, pCharSubClass, pCims[p].sim, topCims[c][m].match.ch, charClass, charSubClass, topCims[c][m].sim, false) * cus.predecessors.getCount(pChc));
							if (upperCase && !pUpperCase)
								cimPairScore /= 2; // penalize capital letter following lower case letter, as this is unlikely
							else if ((pCims.length == 1) && !"P".equals(charClass) && charClass.equals(pCharClass))
								cimPairScore *= 2; // reward unambiguous neighbors, safe for punctuation marks
							cimPairScore /= scaleLogXDiff; // reward glyph size scaling in range of average of unambiguous matches
							cimPairScore /= scaleLogYDiff; // reward glyph size scaling in range of average of unambiguous matches
							cimPairScore *= (1 - relVerticalCenterShiftDiff); // reward glyph size shifting in range of average of unambiguous matches
							topCimScores[m] += (cimPairScore / pCims.length);
//							if (debug) System.out.println("     - " + topCims[c][m].match.ch + " (" + ((int) topCims[c][m].match.ch) + ") at " + topCims[c][m].sim + ", " + PdfCharDecoder.getCharClass(topCims[c][m].match.ch) + "." + PdfCharDecoder.getPunctuationClass(topCims[c][m].match.ch) + " + " + pCims[p].match.ch + " (" + ((int) pCims[p].match.ch) + ") at " + pCims[p].sim + ", " + PdfCharDecoder.getCharClass(pCims[p].match.ch) + "." + PdfCharDecoder.getPunctuationClass(pCims[p].match.ch) + " ==> " + (cimPairScore / pCims.length));
						}
					}
				}
				
				//	reward likely combinations with successors
				for (Iterator sit = cus.successors.iterator(); sit.hasNext();) {
					Integer sChc = ((Integer) sit.next());
					if (debug) System.out.println(" - checking succecessor " + sChc + " (" + cus.successors.getCount(sChc) + " times)");
					if (sChc.equals(charCodes[c])) {
						if (debug) System.out.println(" - waiving self-comparison");
						continue;
					}
					CharImageMatch[] sCims = ((CharImageMatch[]) topCimsByCharCodes.get(sChc));
					if (sCims == null) {
						if (debug) System.out.println(" - top matches not found");
						continue;
					}
					if (debug) {
						System.out.println(" - top matches are:");
						for (int s = 0; s < sCims.length; s++)
							System.out.println("   - " + sCims[s].match.ch + " (" + ((int) sCims[s].match.ch) + ") at " +  sCims[s].sim + ", " + PdfCharDecoder.getCharClass(sCims[s].match.ch) + "." + PdfCharDecoder.getPunctuationClass(sCims[s].match.ch));
					}
					for (int m = 0; m < topCims[c].length; m++) {
						String charClass = PdfCharDecoder.getCharClass(topCims[c][m].match.ch);
						String charSubClass = ("P".equals(charClass) ? PdfCharDecoder.getPunctuationClass(topCims[c][m].match.ch) : null);
						
						//	reward glyph size scaling in range of average of unambiguous matches
						float scaleLogXDiff = 1;
						if (!Float.isNaN(cmfm.uaScaleLogX)) {
							scaleLogXDiff = Math.abs(cmfm.uaScaleLogX - topCims[c][m].scaleLogX);
							scaleLogXDiff = Math.max(scaleLogXDiff, 0.2f);
						}
						float scaleLogYDiff = 1;
						if (!Float.isNaN(cmfm.uaScaleLogY)) {
							scaleLogYDiff = Math.abs(cmfm.uaScaleLogY - topCims[c][m].scaleLogY);
							scaleLogYDiff = Math.max(scaleLogYDiff, 0.2f);
						}
						
						//	also factor in vertical shift (measure difference in relative vertical position of char box, and multiply score by (1 - difference))
						float relVerticalCenterShiftDiff = 0;
						if (!Float.isNaN(cmfm.uaRelVerticalCenterShift))
							relVerticalCenterShiftDiff = Math.abs(cmfm.uaRelVerticalCenterShift - topCims[c][m].vCenterShift);
						
//						if (debug) System.out.println("     - scale log diffs are " + scaleLogXDiff + "/" + scaleLogYDiff + ", center shift diff is " + relVerticalCenterShiftDiff);
						
						//	reward pairing up with matching character class
						boolean upperCase = ((Character.toUpperCase(topCims[c][m].match.ch) == topCims[c][m].match.ch) && (Character.toLowerCase(topCims[c][m].match.ch) != Character.toUpperCase(topCims[c][m].match.ch)));
						for (int s = 0; s < sCims.length; s++) {
							if (topCims[c][m].match.ch == sCims[s].match.ch)
								continue;
							String sCharClass = PdfCharDecoder.getCharClass(sCims[s].match.ch);
							String sCharSubClass = ("P".equals(sCharClass) ? PdfCharDecoder.getPunctuationClass(sCims[s].match.ch) : null);
							boolean sUpperCase = ((Character.toUpperCase(sCims[s].match.ch) == sCims[s].match.ch) && (Character.toLowerCase(sCims[s].match.ch) != Character.toUpperCase(sCims[s].match.ch)));
							
							//	compute score
							float cimPairScore = (getCharPairScore(topCims[c][m].match.ch, charClass, charSubClass, topCims[c][m].sim, sCims[s].match.ch, sCharClass, sCharSubClass, sCims[s].sim, false) * cus.successors.getCount(sChc));
							if (!upperCase && sUpperCase)
								cimPairScore /= 2; // penalize capital letter following lower case letter, as this is unlikely
							else if ((sCims.length == 1) && !"P".equals(charClass) && charClass.equals(sCharClass))
								cimPairScore *= 2; // reward unambiguous neighbors, safe for punctuation marks
							cimPairScore /= scaleLogXDiff; // reward glyph size scaling in range of average of unambiguous matches
							cimPairScore /= scaleLogYDiff; // reward glyph size scaling in range of average of unambiguous matches
							cimPairScore *= (1 - relVerticalCenterShiftDiff); // reward glyph size shifting in range of average of unambiguous matches
							topCimScores[m] += (cimPairScore / sCims.length);
//							if (debug) System.out.println("     - " + topCims[c][m].match.ch + " (" + ((int) topCims[c][m].match.ch) + ") at " + topCims[c][m].sim + ", " + PdfCharDecoder.getCharClass(topCims[c][m].match.ch) + "." + PdfCharDecoder.getPunctuationClass(topCims[c][m].match.ch) + " + " + sCims[s].match.ch + " (" + ((int) sCims[s].match.ch) + ") at " + sCims[s].sim + ", " + PdfCharDecoder.getCharClass(sCims[s].match.ch) + "." + PdfCharDecoder.getPunctuationClass(sCims[s].match.ch) + " ==> " + (cimPairScore / sCims.length));
						}
					}
				}
				
				//	use predecessor chars
				for (Iterator pit = cus.predecessorChars.iterator(); pit.hasNext();) {
					CharNeighbor pCn = ((CharNeighbor) pit.next());
					if (debug) System.out.println(" - checking predecessor char " + pCn + " (" + cus.predecessorChars.getCount(pCn) + " times)");
					if (pCn.equals(charCodes[c], pFont)) {
						if (debug) System.out.println(" - waiving self-comparison");
						continue;
					}
					
					//	same font, use char code and matches (happend in fonts with implicit spaces)
					if (pCn.font == pFont) {
						CharImageMatch[] pCims = ((CharImageMatch[]) topCimsByCharCodes.get(new Integer(pCn.charByte)));
						if (pCims == null) {
							if (debug) System.out.println(" - top matches not found");
							continue;
						}
						if (debug) {
							System.out.println(" - top matches are:");
							for (int p = 0; p < pCims.length; p++)
								System.out.println("   - " + pCims[p].match.ch + " (" + ((int) pCims[p].match.ch) + ") at " +  pCims[p].sim + ", " + PdfCharDecoder.getCharClass(pCims[p].match.ch) + "." + PdfCharDecoder.getPunctuationClass(pCims[p].match.ch));
						}
						for (int m = 0; m < topCims[c].length; m++) {
							String charClass = PdfCharDecoder.getCharClass(topCims[c][m].match.ch);
							String charSubClass = ("P".equals(charClass) ? PdfCharDecoder.getPunctuationClass(topCims[c][m].match.ch) : null);
							
							//	reward glyph size scaling in range of average of unambiguous matches
							float scaleLogXDiff = 1;
							if (!Float.isNaN(cmfm.uaScaleLogX)) {
								scaleLogXDiff = Math.abs(cmfm.uaScaleLogX - topCims[c][m].scaleLogX);
								scaleLogXDiff = Math.max(scaleLogXDiff, 0.2f);
							}
							float scaleLogYDiff = 1;
							if (!Float.isNaN(cmfm.uaScaleLogY)) {
								scaleLogYDiff = Math.abs(cmfm.uaScaleLogY - topCims[c][m].scaleLogY);
								scaleLogYDiff = Math.max(scaleLogYDiff, 0.2f);
							}
							
							//	also factor in vertical shift (measure difference in relative vertical position of char box, and multiply score by (1 - difference))
							float relVerticalCenterShiftDiff = 0;
							if (!Float.isNaN(cmfm.uaRelVerticalCenterShift))
								relVerticalCenterShiftDiff = Math.abs(cmfm.uaRelVerticalCenterShift - topCims[c][m].vCenterShift);
							
//							if (debug) System.out.println("     - scale log diffs are " + scaleLogXDiff + "/" + scaleLogYDiff + ", center shift diff is " + relVerticalCenterShiftDiff);
							
							//	reward pairing up with matching character class
							boolean upperCase = ((Character.toUpperCase(topCims[c][m].match.ch) == topCims[c][m].match.ch) && (Character.toLowerCase(topCims[c][m].match.ch) != Character.toUpperCase(topCims[c][m].match.ch)));
							for (int p = 0; p < pCims.length; p++) {
								if (topCims[c][m].match.ch == pCims[p].match.ch)
									continue;
								String pCharClass = PdfCharDecoder.getCharClass(pCims[p].match.ch);
								String pCharSubClass = ("P".equals(pCharClass) ? PdfCharDecoder.getPunctuationClass(pCims[p].match.ch) : null);
								boolean pUpperCase = ((Character.toUpperCase(pCims[p].match.ch) == pCims[p].match.ch) && (Character.toLowerCase(pCims[p].match.ch) != Character.toUpperCase(pCims[p].match.ch)));
								
								//	compute score
								float cimPairScore = (getCharPairScore(pCims[p].match.ch, pCharClass, pCharSubClass, pCims[p].sim, topCims[c][m].match.ch, charClass, charSubClass, topCims[c][m].sim, false) * cus.predecessorChars.getCount(pCn));
								if (upperCase && !pUpperCase)
									cimPairScore /= 2; // penalize capital letter following lower case letter, as this is unlikely
								else if ((pCims.length == 1) && !"P".equals(charClass) && charClass.equals(pCharClass))
									cimPairScore *= 2; // reward unambiguous neighbors, safe for punctuation marks
								cimPairScore /= scaleLogXDiff; // reward glyph size scaling in range of average of unambiguous matches
								cimPairScore /= scaleLogYDiff; // reward glyph size scaling in range of average of unambiguous matches
								cimPairScore *= (1 - relVerticalCenterShiftDiff); // reward glyph size shifting in range of average of unambiguous matches
								topCimScores[m] += (cimPairScore / pCims.length);
//								if (debug) System.out.println("     - " + topCims[c][m].match.ch + " (" + ((int) topCims[c][m].match.ch) + ") at " + topCims[c][m].sim + ", " + PdfCharDecoder.getCharClass(topCims[c][m].match.ch) + "." + PdfCharDecoder.getPunctuationClass(topCims[c][m].match.ch) + " + " + pCims[p].match.ch + " (" + ((int) pCims[p].match.ch) + ") at " + pCims[p].sim + ", " + PdfCharDecoder.getCharClass(pCims[p].match.ch) + "." + PdfCharDecoder.getPunctuationClass(pCims[p].match.ch) + " ==> " + (cimPairScore / pCims.length));
							}
						}
					}
					
					//	other font, use it if decoded yet
					else if (pCn.font.isDecoded()) {
						char pCh = pCn.getChar();
						if (pCh == 0)
							continue;
						for (int m = 0; m < topCims[c].length; m++) {
							String charClass = PdfCharDecoder.getCharClass(topCims[c][m].match.ch);
							String charSubClass = ("P".equals(charClass) ? PdfCharDecoder.getPunctuationClass(topCims[c][m].match.ch) : null);
							
							//	reward glyph size scaling in range of average of unambiguous matches
							float scaleLogXDiff = 1;
							if (!Float.isNaN(cmfm.uaScaleLogX)) {
								scaleLogXDiff = Math.abs(cmfm.uaScaleLogX - topCims[c][m].scaleLogX);
								scaleLogXDiff = Math.max(scaleLogXDiff, 0.2f);
							}
							float scaleLogYDiff = 1;
							if (!Float.isNaN(cmfm.uaScaleLogY)) {
								scaleLogYDiff = Math.abs(cmfm.uaScaleLogY - topCims[c][m].scaleLogY);
								scaleLogYDiff = Math.max(scaleLogYDiff, 0.2f);
							}
							
							//	also factor in vertical shift (measure difference in relative vertical position of char box, and multiply score by (1 - difference))
							float relVerticalCenterShiftDiff = 0;
							if (!Float.isNaN(cmfm.uaRelVerticalCenterShift))
								relVerticalCenterShiftDiff = Math.abs(cmfm.uaRelVerticalCenterShift - topCims[c][m].vCenterShift);
							
//							if (debug) System.out.println("     - scale log diffs are " + scaleLogXDiff + "/" + scaleLogYDiff + ", center shift diff is " + relVerticalCenterShiftDiff);
							
							//	reward pairing up with matching character class
							boolean upperCase = ((Character.toUpperCase(topCims[c][m].match.ch) == topCims[c][m].match.ch) && (Character.toLowerCase(topCims[c][m].match.ch) != Character.toUpperCase(topCims[c][m].match.ch)));
							String pCharClass = PdfCharDecoder.getCharClass(pCh);
							String pCharSubClass = ("P".equals(pCharClass) ? PdfCharDecoder.getPunctuationClass(pCh) : null);
							boolean pUpperCase = ((Character.toUpperCase(pCh) == pCh) && (Character.toLowerCase(pCh) != Character.toUpperCase(pCh)));
							
							//	compute score
							float cimPairScore = (getCharPairScore(pCh, pCharClass, pCharSubClass, 1.0f, topCims[c][m].match.ch, charClass, charSubClass, topCims[c][m].sim, false) * cus.predecessorChars.getCount(pCn));
							if (upperCase && !pUpperCase)
								cimPairScore /= 2; // penalize capital letter following lower case letter, as this is unlikely
							else if (!"P".equals(charClass) && charClass.equals(pCharClass))
								cimPairScore *= 2; // reward unambiguous neighbors, safe for punctuation marks
							cimPairScore /= scaleLogXDiff; // reward glyph size scaling in range of average of unambiguous matches
							cimPairScore /= scaleLogYDiff; // reward glyph size scaling in range of average of unambiguous matches
							cimPairScore *= (1 - relVerticalCenterShiftDiff); // reward glyph size shifting in range of average of unambiguous matches
							topCimScores[m] += cimPairScore;
//							if (debug) System.out.println("     - " + topCims[c][m].match.ch + " (" + ((int) topCims[c][m].match.ch) + ") at " + topCims[c][m].sim + ", " + PdfCharDecoder.getCharClass(topCims[c][m].match.ch) + "." + PdfCharDecoder.getPunctuationClass(topCims[c][m].match.ch) + " + " + pCims[p].match.ch + " (" + ((int) pCims[p].match.ch) + ") at " + pCims[p].sim + ", " + PdfCharDecoder.getCharClass(pCims[p].match.ch) + "." + PdfCharDecoder.getPunctuationClass(pCims[p].match.ch) + " ==> " + (cimPairScore / pCims.length));
						}
					}
				}
				
				//	use successor chars
				for (Iterator sit = cus.successorChars.iterator(); sit.hasNext();) {
					CharNeighbor sCn = ((CharNeighbor) sit.next());
					if (debug) System.out.println(" - checking successor char " + sCn + " (" + cus.successorChars.getCount(sCn) + " times)");
					if (sCn.equals(charCodes[c], pFont)) {
						if (debug) System.out.println(" - waiving self-comparison");
						continue;
					}
					
					//	same font, use char code and matches (happend in fonts with implicit spaces)
					if (sCn.font == pFont) {
						CharImageMatch[] sCims = ((CharImageMatch[]) topCimsByCharCodes.get(new Integer(sCn.charByte)));
						if (sCims == null) {
							if (debug) System.out.println(" - top matches not found");
							continue;
						}
						if (debug) {
							System.out.println(" - top matches are:");
							for (int p = 0; p < sCims.length; p++)
								System.out.println("   - " + sCims[p].match.ch + " (" + ((int) sCims[p].match.ch) + ") at " +  sCims[p].sim + ", " + PdfCharDecoder.getCharClass(sCims[p].match.ch) + "." + PdfCharDecoder.getPunctuationClass(sCims[p].match.ch));
						}
						for (int m = 0; m < topCims[c].length; m++) {
							String charClass = PdfCharDecoder.getCharClass(topCims[c][m].match.ch);
							String charSubClass = ("P".equals(charClass) ? PdfCharDecoder.getPunctuationClass(topCims[c][m].match.ch) : null);
							
							//	reward glyph size scaling in range of average of unambiguous matches
							float scaleLogXDiff = 1;
							if (!Float.isNaN(cmfm.uaScaleLogX)) {
								scaleLogXDiff = Math.abs(cmfm.uaScaleLogX - topCims[c][m].scaleLogX);
								scaleLogXDiff = Math.max(scaleLogXDiff, 0.2f);
							}
							float scaleLogYDiff = 1;
							if (!Float.isNaN(cmfm.uaScaleLogY)) {
								scaleLogYDiff = Math.abs(cmfm.uaScaleLogY - topCims[c][m].scaleLogY);
								scaleLogYDiff = Math.max(scaleLogYDiff, 0.2f);
							}
							
							//	also factor in vertical shift (measure difference in relative vertical position of char box, and multiply score by (1 - difference))
							float relVerticalCenterShiftDiff = 0;
							if (!Float.isNaN(cmfm.uaRelVerticalCenterShift))
								relVerticalCenterShiftDiff = Math.abs(cmfm.uaRelVerticalCenterShift - topCims[c][m].vCenterShift);
							
//							if (debug) System.out.println("     - scale log diffs are " + scaleLogXDiff + "/" + scaleLogYDiff + ", center shift diff is " + relVerticalCenterShiftDiff);
							
							//	reward pairing up with matching character class
							boolean upperCase = ((Character.toUpperCase(topCims[c][m].match.ch) == topCims[c][m].match.ch) && (Character.toLowerCase(topCims[c][m].match.ch) != Character.toUpperCase(topCims[c][m].match.ch)));
							for (int p = 0; p < sCims.length; p++) {
								if (topCims[c][m].match.ch == sCims[p].match.ch)
									continue;
								String sCharClass = PdfCharDecoder.getCharClass(sCims[p].match.ch);
								String sCharSubClass = ("P".equals(sCharClass) ? PdfCharDecoder.getPunctuationClass(sCims[p].match.ch) : null);
								boolean sUpperCase = ((Character.toUpperCase(sCims[p].match.ch) == sCims[p].match.ch) && (Character.toLowerCase(sCims[p].match.ch) != Character.toUpperCase(sCims[p].match.ch)));
								
								//	compute score
								float cimPairScore = (getCharPairScore(topCims[c][m].match.ch, charClass, charSubClass, topCims[c][m].sim, sCims[p].match.ch, sCharClass, sCharSubClass, sCims[p].sim, false) * cus.successorChars.getCount(sCn));
								if (!upperCase && sUpperCase)
									cimPairScore /= 2; // penalize capital letter following lower case letter, as this is unlikely
								else if ((sCims.length == 1) && !"P".equals(charClass) && charClass.equals(sCharClass))
									cimPairScore *= 2; // reward unambiguous neighbors, safe for punctuation marks
								cimPairScore /= scaleLogXDiff; // reward glyph size scaling in range of average of unambiguous matches
								cimPairScore /= scaleLogYDiff; // reward glyph size scaling in range of average of unambiguous matches
								cimPairScore *= (1 - relVerticalCenterShiftDiff); // reward glyph size shifting in range of average of unambiguous matches
								topCimScores[m] += (cimPairScore / sCims.length);
//								if (debug) System.out.println("     - " + topCims[c][m].match.ch + " (" + ((int) topCims[c][m].match.ch) + ") at " + topCims[c][m].sim + ", " + PdfCharDecoder.getCharClass(topCims[c][m].match.ch) + "." + PdfCharDecoder.getPunctuationClass(topCims[c][m].match.ch) + " + " + pCims[p].match.ch + " (" + ((int) pCims[p].match.ch) + ") at " + pCims[p].sim + ", " + PdfCharDecoder.getCharClass(pCims[p].match.ch) + "." + PdfCharDecoder.getPunctuationClass(pCims[p].match.ch) + " ==> " + (cimPairScore / pCims.length));
							}
						}
					}
					
					//	other font, use it if decoded yet
					else if (sCn.font.isDecoded()) {
						char sCh = sCn.getChar();
						if (sCh == 0)
							continue;
						for (int m = 0; m < topCims[c].length; m++) {
							String charClass = PdfCharDecoder.getCharClass(topCims[c][m].match.ch);
							String charSubClass = ("P".equals(charClass) ? PdfCharDecoder.getPunctuationClass(topCims[c][m].match.ch) : null);
							
							//	reward glyph size scaling in range of average of unambiguous matches
							float scaleLogXDiff = 1;
							if (!Float.isNaN(cmfm.uaScaleLogX)) {
								scaleLogXDiff = Math.abs(cmfm.uaScaleLogX - topCims[c][m].scaleLogX);
								scaleLogXDiff = Math.max(scaleLogXDiff, 0.2f);
							}
							float scaleLogYDiff = 1;
							if (!Float.isNaN(cmfm.uaScaleLogY)) {
								scaleLogYDiff = Math.abs(cmfm.uaScaleLogY - topCims[c][m].scaleLogY);
								scaleLogYDiff = Math.max(scaleLogYDiff, 0.2f);
							}
							
							//	also factor in vertical shift (measure difference in relative vertical position of char box, and multiply score by (1 - difference))
							float relVerticalCenterShiftDiff = 0;
							if (!Float.isNaN(cmfm.uaRelVerticalCenterShift))
								relVerticalCenterShiftDiff = Math.abs(cmfm.uaRelVerticalCenterShift - topCims[c][m].vCenterShift);
							
//							if (debug) System.out.println("     - scale log diffs are " + scaleLogXDiff + "/" + scaleLogYDiff + ", center shift diff is " + relVerticalCenterShiftDiff);
							
							//	reward pairing up with matching character class
							boolean upperCase = ((Character.toUpperCase(topCims[c][m].match.ch) == topCims[c][m].match.ch) && (Character.toLowerCase(topCims[c][m].match.ch) != Character.toUpperCase(topCims[c][m].match.ch)));
							String sCharClass = PdfCharDecoder.getCharClass(sCh);
							String sCharSubClass = ("P".equals(sCharClass) ? PdfCharDecoder.getPunctuationClass(sCh) : null);
							boolean sUpperCase = ((Character.toUpperCase(sCh) == sCh) && (Character.toLowerCase(sCh) != Character.toUpperCase(sCh)));
							
							//	compute score
							float cimPairScore = (getCharPairScore(topCims[c][m].match.ch, charClass, charSubClass, topCims[c][m].sim, sCh, sCharClass, sCharSubClass, 1.0f, false) * cus.successorChars.getCount(sCn));
							if (upperCase && !sUpperCase)
								cimPairScore /= 2; // penalize capital letter following lower case letter, as this is unlikely
							else if (!"P".equals(charClass) && charClass.equals(sCharClass))
								cimPairScore *= 2; // reward unambiguous neighbors, safe for punctuation marks
							cimPairScore /= scaleLogXDiff; // reward glyph size scaling in range of average of unambiguous matches
							cimPairScore /= scaleLogYDiff; // reward glyph size scaling in range of average of unambiguous matches
							cimPairScore *= (1 - relVerticalCenterShiftDiff); // reward glyph size shifting in range of average of unambiguous matches
							topCimScores[m] += cimPairScore;
//							if (debug) System.out.println("     - " + topCims[c][m].match.ch + " (" + ((int) topCims[c][m].match.ch) + ") at " + topCims[c][m].sim + ", " + PdfCharDecoder.getCharClass(topCims[c][m].match.ch) + "." + PdfCharDecoder.getPunctuationClass(topCims[c][m].match.ch) + " + " + pCims[p].match.ch + " (" + ((int) pCims[p].match.ch) + ") at " + pCims[p].sim + ", " + PdfCharDecoder.getCharClass(pCims[p].match.ch) + "." + PdfCharDecoder.getPunctuationClass(pCims[p].match.ch) + " ==> " + (cimPairScore / pCims.length));
						}
					}
				}
				
				//	put matches on trays for easier handling
				if (debug) System.out.println("Environment scored matches of char " + charCodes[c] + ((cus == null) ? "" : (" (" + cus.usageCount + " times)")) + " after round " + csr + ":");
				for (int m = 0; m < topCims[c].length; m++) {
					scoredCims[c][m] = new ScoredCharImageMatch(topCims[c][m], topCimScores[m], charCodes[c], charNames[c]);
					if (debug) System.out.println(" - " + topCims[c][m].match.ch + " (" + ((int) topCims[c][m].match.ch) + ") at " + topCims[c][m].sim + ", shift " + topCims[c][m].vCenterShift + ", scale " + topCims[c][m].scaleLogX + "/" + topCims[c][m].scaleLogY + ", " + PdfCharDecoder.getCharClass(topCims[c][m].match.ch) + " ==> " + topCimScores[m]);
				}
			}
			
			/* Eliminate matches with low scores, as well as ones unambiguously
			 * assigned otherwise, and do so in separate loop to not affect
			 * upcoming matches of same scoring round (ordering effects !!!).
			 * 
			 * We need to do this iteratively, as new unambiguous assignments
			 * towards end of list might well incur further eliminations further
			 * up the list.
			 */
			for (boolean cimEliminated = true; cimEliminated;) {
				cimEliminated = false;
				for (int c = 0; c < chars.length; c++) {
					if (topCims[c] == null)
						continue;
					if (topCims[c].length == 0)
						continue;
					
					//	this one has been handled before
					if (bestCims[c] != null)
						continue;
					
					//	we couldn't score this one
					if (scoredCims[c] == null)
						continue;
					if (scoredCims[c].length == 0)
						continue;
					
					//	update status
					pm.setInfo("     - Assessing " + scoredCims[c].length + " matches of char " + c + " (" + charNames[c] + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ")");
					if (debug) System.out.println("Assessing " + scoredCims[c].length + " matches of char " + c + " (" + charNames[c] + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ")");
					
					/* Eliminate chars uniquely assigned to other glyphs; this
					 * might incur trouble with mixed-face fonts, in which
					 * characters repeat, but those are trouble on many other
					 * levels as well anyway. */
					for (int m = 0; m < scoredCims[c].length; m++)
						if ((scoredCims[c][m] != null) && assignedChars.contains(new Integer((int) scoredCims[c][m].cim.match.ch))) {
							scoredCims[c][m] = null;
							cimEliminated = true;
							idleCsrCount = 0;
						}
					
					//	find maximum similarity (for dot-less elimination, and for retaining top 1% of most similar matches in first round)
					ScoredCharImageMatch bestSimCim = null;
					for (int m = 0; m < scoredCims[c].length; m++) {
						if ((scoredCims[c][m] != null) && ((bestSimCim == null) || (scoredCims[c][m].cim.sim > bestSimCim.cim.sim)))
							bestSimCim = scoredCims[c][m];
					}
					
					//	eliminate dot-less i and j if _anything_ is more similar
					for (int m = 0; m < scoredCims[c].length; m++) {
						if (scoredCims[c][m] == null)
							continue; // eliminated before in some way
						if ((scoredCims[c][m].cim.match.ch != '\u0131') && (scoredCims[c][m].cim.match.ch != '\u0131'))
							continue;
						if (scoredCims[c][m].cim.sim < bestSimCim.cim.sim) {
							if (debug) System.out.println("Eliminating " + scoredCims[c][m].cim.match.ch + " (" + ((int) scoredCims[c][m].cim.match.ch) + ", " + scoredCims[c][m].cim.sim + ", score " + scoredCims[c][m].score + ") as dot-less character less similar than " + bestSimCim.cim.match.ch + " (" + ((int) bestSimCim.cim.match.ch) + ", " + bestSimCim.cim.sim + ", score " + bestSimCim.score + ")");
							scoredCims[c][m] = null;
							cimEliminated = true;
							idleCsrCount = 0;
						}
					}
					
					//	eliminate diacritics matching glyph worse than respective base character
					//	TODO maybe start doing this only in second round, or second round of elimination, or both
					for (int m = 0; m < scoredCims[c].length; m++) {
						if (scoredCims[c][m] == null)
							continue; // eliminated before in some way
						if (scoredCims[c][m].cim.match.ch != StringUtils.getBaseChar(scoredCims[c][m].cim.match.ch))
							continue; // not a base character
						for (int cm = 0; cm < scoredCims[c].length; cm++) {
							if (cm == c)
								continue; // avoid self comparison
							if (scoredCims[c][cm] == null)
								continue; // eliminated before in some way
							if (StringUtils.getBaseChar(scoredCims[c][cm].cim.match.ch) != scoredCims[c][m].cim.match.ch)
								continue; // not a diacritic derived from current base character
							
							//	eliminate diacritic matching glyph worse than own base character
							if (scoredCims[c][cm].cim.sim < scoredCims[c][m].cim.sim) {
								if (debug) System.out.println("Eliminating " + scoredCims[c][cm].cim.match.ch + " (" + ((int) scoredCims[c][cm].cim.match.ch) + ", " + scoredCims[c][cm].cim.sim + ", score " + scoredCims[c][cm].score + ") as less similar diacritic of " + scoredCims[c][m].cim.match.ch + " (" + ((int) scoredCims[c][m].cim.match.ch) + ", " + scoredCims[c][m].cim.sim + ", score " + scoredCims[c][m].score + ")");
								scoredCims[c][cm] = null;
								cimEliminated = true;
								idleCsrCount = 0;
							}
						}
					}
					
					//	find maximum score
					float bestCimScore = -1;
					for (int m = 0; m < scoredCims[c].length; m++) {
						if (scoredCims[c][m] != null)
							bestCimScore = Math.max(bestCimScore, scoredCims[c][m].score);
					}
					if (debug) System.out.println("Best score is " + bestCimScore);
					
					/* all remaining matches eliminated due to other
					 * unambiguous assignments, bring back previously
					 * eliminated ones (only once, though, to prevent endless
					 * recovery and the loops it incurs) */
					if (bestCimScore < 0) {
						if (allTopCims[c] != null) {
							ArrayList topCimList = new ArrayList();
							for (int m = 0; m < allTopCims[c].length; m++) {
								if (!assignedChars.contains(new Integer((int) allTopCims[c][m].match.ch)))
									topCimList.add(allTopCims[c][m]);
							}
							topCims[c] = ((CharImageMatch[]) topCimList.toArray(new CharImageMatch[topCimList.size()]));
							allTopCims[c] = null;
							if (debug) System.out.println(" ==> brought back " + topCims[c].length + " eliminated matches");
						}
						else {
							topCims[c] = null; // eliminate top CIMs to prevent coming back to them time and again
							if (debug) System.out.println(" ==> no matches left to work with");
						}
						scoredCims[c] = null; // null out scored matches to defer char to next round of scoring
						continue; // skit for current round of elimination
					}
					
					/* eliminate all matches scored less than best one, upping
					 * the bar each round of scoring, 1/2, 2/3, 3/4, 4/5, etc. */
					ArrayList scoredCimList = new ArrayList();
					if (debug) System.out.println("Applying score cutoff with best score " + bestCimScore);
					for (int m = 0; m < scoredCims[c].length; m++) {
						if (scoredCims[c][m] == null)
							continue; // eliminated before in some way
						
						//	retain match in range of top score
						if ((scoredCims[c][m].score * (csr + 1)) >= (bestCimScore * csr)) {
							scoredCimList.add(scoredCims[c][m]);
							if (debug) System.out.println(" - retained (score) " + scoredCims[c][m].cim.match.ch + " (" + ((int) scoredCims[c][m].cim.match.ch) + ") at sim " + scoredCims[c][m].cim.sim + ", score " + scoredCims[c][m].score);
						}
						
						//	_always_ retaining most similar match(es) in first round (gives highly ambiguous neighbors opportunity to sort out)
						else if ((csr == 1) && ((scoredCims[c][m].cim.sim * 100) > (bestSimCim.cim.sim * 99))) {
							scoredCimList.add(scoredCims[c][m]);
							if (debug) System.out.println(" - retained (sim) " + scoredCims[c][m].cim.match.ch + " (" + ((int) scoredCims[c][m].cim.match.ch) + ") at sim " + scoredCims[c][m].cim.sim + ", score " + scoredCims[c][m].score);
						}
						
						//	we're done with this one
						else if (debug) System.out.println(" - eliminated " + scoredCims[c][m].cim.match.ch + " (" + ((int) scoredCims[c][m].cim.match.ch) + ") at sim " + scoredCims[c][m].cim.sim + ", score " + scoredCims[c][m].score);
					}
					if (debug) System.out.println(" - got " + scoredCimList.size() + " matches above threshold");
					
					//	update arrays and index
					if (scoredCimList.size() < scoredCims[c].length) {
						scoredCims[c] = ((ScoredCharImageMatch[]) scoredCimList.toArray(new ScoredCharImageMatch[scoredCimList.size()]));
						topCims[c] = new CharImageMatch[scoredCims[c].length];
						for (int m = 0; m < scoredCims[c].length; m++)
							topCims[c][m] = scoredCims[c][m].cim;
						topCimsByCharCodes.put(scoredCims[c][0].chc, topCims[c]);
						cimEliminated = true;
						idleCsrCount = 0;
						
						//	show scores
						pm.setInfo("       ==> potential matches reduced to " + scoredCims[c].length);
						if (debug) {
							System.out.println(" - matches of char " + scoredCims[c][0].chc + " reduced to:");
							for (int m = 0; m < scoredCims[c].length; m++)
								System.out.println("   - " + scoredCims[c][m].cim.match.ch + " (" + ((int) scoredCims[c][m].cim.match.ch) + ") at " + scoredCims[c][m].cim.sim + ", shift " + scoredCims[c][m].cim.vCenterShift + ", scale " + scoredCims[c][m].cim.scaleLogX + "/" + scoredCims[c][m].cim.scaleLogY + ", " + PdfCharDecoder.getCharClass(scoredCims[c][m].cim.match.ch) + " ==> " + scoredCims[c][m].score);
						}
					}
					
					//	do we have a new unambiguous match?
					if (topCims[c].length == 1) {
						bestCims[c] = topCims[c][0];
						assignedChars.add(new Integer((int) topCims[c][0].match.ch));
						pm.setInfo("       ==> unambiguously matched to '" + topCims[c][0].match.ch + "'");
						if (debug) System.out.println(" ==> got unambiguous match " + topCims[c][0].match.ch);
					}
				}
			}
			
			/* only stop after no new assignments have been made for several
			 * rounds, as eliminating more strictly in next round might well
			 * yield new unambiguous assignments ... */
			if (idleCsrCount >= 3)
				break;
		}
		
		/* TODO  also use bigram frequencies to disambiguate letters, should help distinguish 'h' from 'b' in italics:
		 * - should help sort out remaining ambiguities
		 * ==> compile bigram lists for multiple languages from http://storage.googleapis.com/books/ngrams/books/datasetsv2.html
		 */
		
		//	handle unambiguous matches, and compute average of measurements
		cmfm = computeFontMetrics(bestCims);
		if (debug) System.out.println("Averages: vertical center shift is " + cmfm.uaRelVerticalCenterShift + ", scale log " + cmfm.uaScaleLogX + "/" + cmfm.uaScaleLogY + ", x-height ascender shift is " + cmfm.uaRelXHeightAscenderShift + ", cap height ascender shift is " + cmfm.uaRelCapHeightAscenderShift + ", descender shift is " + cmfm.uaRelDescenderShift);
		
		//	count out classes of decoded characters
		CountingSet charClassCounts = new CountingSet(new HashMap());
		for (int c = 0; c < chars.length; c++) {
			if (bestCims[c] == null)
				continue;
			if (bestCims[c] == UNICODE_MAPPING_UNRENDERABLE)
				continue;
			charClassCounts.add(PdfCharDecoder.getCharClass(bestCims[c].match.ch));
		}
		
		//	resort to matches with highest similarity
		for (int c = 0; c < chars.length; c++) {
			
			//	no matches for this one
			if (topCims[c] == null)
				continue;
			if (topCims[c].length == 0)
				continue;
			
			//	this one has been handled before
			if (bestCims[c] != null)
				continue;
			
			//	update status
			pm.setInfo("     - Decoding char " + c + " (" + charNames[c] + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ")");
			if (debug) System.out.println("Decoding char " + c + " (" + charNames[c] + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ")");
			
			//	find highest similarity, also factoring in scaling and vertical shift
			float bestSim = 0;
			scoredCims[c] = new ScoredCharImageMatch[topCims[c].length];
			for (int m = 0; m < topCims[c].length; m++) {
				if (topCims[c][m] == null)
					continue;
				
				//	reward glyph size scaling in range of average of unambiguous matches
				float scaleLogXDiff = 1;
				if (!Float.isNaN(cmfm.uaScaleLogX)) {
					scaleLogXDiff = Math.abs(cmfm.uaScaleLogX - topCims[c][m].scaleLogX);
					scaleLogXDiff = Math.max(scaleLogXDiff, 0.2f);
				}
				float scaleLogYDiff = 1;
				if (!Float.isNaN(cmfm.uaScaleLogY)) {
					scaleLogYDiff = Math.abs(cmfm.uaScaleLogY - topCims[c][m].scaleLogY);
					scaleLogYDiff = Math.max(scaleLogYDiff, 0.2f);
				}
				
				//	also factor in vertical shift (measure difference in relative vertical position of char box, and multiply score by (1 - difference))
				float relVerticalCenterShiftDiff = 0;
				if (!Float.isNaN(cmfm.uaRelVerticalCenterShift))
					relVerticalCenterShiftDiff = Math.abs(cmfm.uaRelVerticalCenterShift - topCims[c][m].vCenterShift);
				
				//	compute overall similarity, including scaling and vertical shift
				float sim = topCims[c][m].sim;
				sim /= scaleLogXDiff;
				sim /= scaleLogYDiff;
				sim *= (1 - relVerticalCenterShiftDiff);
				bestSim = Math.max(bestSim, sim);
				
				/* score highest similarity match(es) by character class frequency;
				 * should help tell apart characters (a) whose glyphs are shared
				 * between Latin and Greek, and (b) which don't have any letters
				 * for neighbors at all (e.g. only occur as initials) */
				String charClass = PdfCharDecoder.getCharClass(topCims[c][m].match.ch);
				scoredCims[c][m] = new ScoredCharImageMatch(topCims[c][m], (sim * Math.max(1, charClassCounts.getCount(charClass))), charCodes[c], charNames[c]);
				if (debug) System.out.println(" - score for match " + topCims[c][m].match.ch + " (" + ((int) topCims[c][m].match.ch) + ") at " + topCims[c][m].sim + " is " + scoredCims[c][m].score);
			}
			
			//	select best scoring match
			ScoredCharImageMatch bestCim = null;
			for (int m = 0; m < scoredCims[c].length; m++) {
				if (scoredCims[c][m] == null)
					continue;
				//	first match, or match better suiting general language of document
				if ((bestCim == null) || (bestCim.score < scoredCims[c][m].score))
					bestCim = scoredCims[c][m];
				//	hard-prefer Latin over Greek, as there are fonts that consist _exclusively_ of ambiguous glyphs, e.g. 'ZOOTAXA' ...
				else if ((bestCim.score == scoredCims[c][m].score) && "L".equals(PdfCharDecoder.getCharClass(scoredCims[c][m].cim.match.ch)))
					bestCim = scoredCims[c][m];
			}
			if (bestCim != null) {
				bestCims[c] = bestCim.cim;
				pm.setInfo("       ==> similarity matched to '" + bestCims[c].match.ch + "' (" + ((int) bestCims[c].match.ch) + "/0x" + Integer.toString(((int) bestCims[c].match.ch), 16).toUpperCase() + ")");
				if (debug) System.out.println(" ==> got most similar match " + bestCims[c].match.ch);
			}
			else {
				pm.setInfo("       ==> no match found");
				if (debug) System.out.println(" ==> got no match");
			}
		}
		
		//	map remaining characters (no need for case distinction here, above scoring does it all)
		for (int c = 0; c < chars.length; c++) {
			
			//	don't try to match space or unused chars
			if (charImages[c] == null)
				continue;
			
			//	we don't have a match for this one, or one that we couldn't render
			if (bestCims[c] == null)
				continue;
			
			//	this one has been handled in Unicode mapping verification
			if (!isOcrDecoded[c])
				continue;
			
			//	update status
			pm.setInfo("     - Decoding char " + c + " (" + charNames[c] + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ")");
			if (debug) System.out.println("Decoding char " + c + " (" + charNames[c] + "/'" + chars[c] + "'/'" + StringUtils.getNormalForm(chars[c]) + "'/" + ((int) chars[c]) + ")");
			
			//	is our best (remaining) match beter than earlier verification match?
			if ((badBestCims[c] != null) && (bestCims[c].sim < badBestCims[c].sim)) {
				pm.setInfo("       ==> char reverted to verification match '" + badBestCims[c].match.ch + "' (" + StringUtils.getCharName(badBestCims[c].match.ch) + ", '" + StringUtils.getNormalForm(badBestCims[c].match.ch) + "') at " + badBestCims[c].sim + ", cache hit rate at " + cacheHitRate[0]);
				if (debug) System.out.println(" ==> char reverted to verification match '" + badBestCims[c].match.ch + "' at similarity " + badBestCims[c].sim + " over assigned OCR match '" + bestCims[c].match.ch + "' at similarity " + topCims[c][0].sim);
			}
			
			//	do we have a reliable match?
			else if (bestCims[c].sim > 0.8) {
				pm.setInfo("       ==> char decoded (1) to '" + bestCims[c].match.ch + "' (" + StringUtils.getCharName(bestCims[c].match.ch) + ", '" + StringUtils.getNormalForm(bestCims[c].match.ch) + "') at " + bestCims[c].sim + ", cache hit rate at " + cacheHitRate[0]);
				if (debug) System.out.println(" ==> char decoded (1) to '" + bestCims[c].match.ch + "' (" + StringUtils.getCharName(bestCims[c].match.ch) + ", '" + StringUtils.getNormalForm(bestCims[c].match.ch) + "') at " + bestCims[c].sim + ", cache hit rate at " + cacheHitRate[0]);
				
				//	correct char mapping specified in Unicode mapping
				pFont.mapUnicode(charCodes[c], ("" + bestCims[c].match.ch), false);
				if (debug) System.out.println(" ==> mapped (1) " + charCodes[c] + " to " + bestCims[c].match.ch);
			}
			
			//	use whatever we got
			else {
				pm.setInfo("       ==> char decoded (2) to '" + bestCims[c].match.ch + "' (" + StringUtils.getCharName(bestCims[c].match.ch) + ", '" + StringUtils.getNormalForm(bestCims[c].match.ch) + "') at " + bestCims[c].sim + ", cache hit rate at " + cacheHitRate[0]);
				if (debug) System.out.println(" ==> char decoded (2) to '" + bestCims[c].match.ch + "' (" + StringUtils.getCharName(bestCims[c].match.ch) + ", '" + StringUtils.getNormalForm(bestCims[c].match.ch) + "') at " + bestCims[c].sim + ", cache hit rate at " + cacheHitRate[0]);
				
				//	correct char mapping specified in Unicode mapping
				pFont.mapUnicode(charCodes[c], ("" + bestCims[c].match.ch), false);
				if (debug) System.out.println(" ==> mapped (2) " + charCodes[c] + " to " + bestCims[c].match.ch);
			}
		}
		
		//	check for descent
		pFont.hasDescent = ((maxDescent < -150) || (pFont.descent < -0.150));
	}
	
	private static class ScoredCharImageMatch {
		final CharImageMatch cim;
		final float score;
		final Integer chc;
		final String chn;
		ScoredCharImageMatch(CharImageMatch cim, float score, Integer chc, String chn) {
			this.cim = cim;
			this.score = score;
			this.chc = chc;
			this.chn = chn;
		}
	}
	
	private static float getCharPairScore(char char1, String charClass1, String charSubClass1, float charSim1, char char2, String charClass2, String charSubClass2, float charSim2, boolean debug) {
		
		//	assess whether to score
		float scoreFactor = 0;
		
		/* Add to score if chars have same class (change of
		 * script in mid word should be pretty unlikely without
		 * intermediate hyphen or the like). Factor in both
		 * similarities and pair frequency, and normalize by
		 * number of matches for neighbor (boosts scores from
		 * unambiguous neighbors) */
		if (!"P".equals(charClass1) && charClass1.equals(charClass2)) {
			scoreFactor = 1;
			if (debug) System.out.println("       ==> same class");
		}
		
		//	reward special punctuation pairs
		else if ("P".equals(charClass1) && "P".equals(charClass2) && PdfCharDecoder.isSpecialPunctuationPair(char1, char2)) {
			scoreFactor = 33; // TODO optimize this (there might be editing errors putting l right after colon)
			if (debug) System.out.println("       ==> special pair");
		}
		
		//	reward digits adjacent to in-number punctuation (deactivated, as a comma could follow a letter just the same)
//		else if ("D".equals(charClass1) && "P".equals(charClass2) && ((Gamta.IN_NUMBER_PUNCTUATION.indexOf(char2) != -1) || "d".equals(charSubClass2)))
//			scoreMatch = true;
		else if ("D".equals(charClass1) && "P".equals(charClass2) && "d".equals(charSubClass2)) {
			scoreFactor = 1;
			if (debug) System.out.println("       ==> digit + digit punctuation");
		}
		
		//	reward in-number punctuation adjacent to digits
		else if ("P".equals(charClass1) && "D".equals(charClass2) && ((Gamta.IN_NUMBER_PUNCTUATION.indexOf(char1) != -1) || "d".equals(charSubClass1))) {
			scoreFactor = 1;
			if (debug) System.out.println("       ==> digit/number punctuation + digit");
		}
		
		//	reward letters adjacent to in-word punctuation (deactivated, as a hyphen could follow a digit just the same)
//		else if (("LGC".indexOf(charClass1) != -1) && "P".equals(charClass2) && ((Gamta.IN_WORD_PUNCTUATION.indexOf(char2) != -1) || "l".equals(charSubClass2)))
//			scoreMatch = true;
		else if (("LGC".indexOf(charClass1) != -1) && "P".equals(charClass2) && "l".equals(charSubClass2)) {
			scoreFactor = 1;
			if (debug) System.out.println("       ==> letter + letter punctuation");
		}
		
		//	reward in-word punctuation adjacent to letters
		else if ("P".equals(charClass1) && ("LGC".indexOf(charClass2) != -1) && ((Gamta.IN_WORD_PUNCTUATION.indexOf(char1) != -1) || "l".equals(charSubClass1))) {
			scoreFactor = 1;
			if (debug) System.out.println("       ==> letter/word punctuation + letter");
		}
		
		//	reward letter immediately following opening quotation mark
		else if ("P".equals(charClass1) && ("LGC".indexOf(charClass2) != -1) && "q".equals(charSubClass1)) {
			scoreFactor = 1;
			if (debug) System.out.println("       ==> opening quoter + letter");
		}
		
		else if (debug) System.out.println("       ==> no match");
		
		//	compute score
		return (scoreFactor * charSim2 * charSim1);
	}
	
	private static CharMatchFontMetrics computeFontMetrics(CharImageMatch[] bestCims) {
		float xHeightSum = 0;
		int xHeightCount = 0;
		float capHeightSum = 0;
		int capHeightCount = 0;
		float uaRelVerticalCenterShiftSum = 0;
		int uaRelVerticalCenterShiftCount = 0;
		float uaScaleLogXSum = 0;
		int uaScaleLogXCount = 0;
		float uaScaleLogYSum = 0;
		int uaScaleLogYCount = 0;
		float uaRelXHeightAscenderShiftSum = 0;
		int uaRelXHeightAscenderShiftCount = 0;
		float uaRelCapHeightAscenderShiftSum = 0;
		int uaRelCapHeightAscenderShiftCount = 0;
		float uaRelDescenderShiftSum = 0;
		int uaRelDescenderShiftCount = 0;
		for (int c = 0; c < bestCims.length; c++) {
			if (bestCims[c] == null)
				continue;
			if (bestCims[c] == UNICODE_MAPPING_UNRENDERABLE)
				continue;
			
			//	don't count punctuation marks whose relative height and position varies too much across fonts
			if ("P".equals(PdfCharDecoder.getCharClass(bestCims[c].match.ch))) {
				if ("%&/;:()[]{}\\?!#".indexOf(bestCims[c].match.ch) == -1)
					continue;
			}
			
			//	take measurements of avearge x-height and cap height
			if ("aemnru".indexOf(bestCims[c].match.ch) != -1) {
				xHeightSum += bestCims[c].charImage.box.getHeight();
				xHeightCount++;
			}
			else if ("ABDEFGHIKLMNPRTUYbdfhkl0123456789".indexOf(bestCims[c].match.ch) != -1) {
				capHeightSum += bestCims[c].charImage.box.getHeight();
				capHeightCount++;
			}
			
			//	record match properties
			uaRelVerticalCenterShiftSum += bestCims[c].vCenterShift;
			uaRelVerticalCenterShiftCount++;
			uaScaleLogXSum += bestCims[c].scaleLogX;
			uaScaleLogXCount++;
			uaScaleLogYSum += bestCims[c].scaleLogY;
			uaScaleLogYCount++;
			
			//	take measurements of x-height, cap height, and relative ascender and descender shift as well
			if ("aegmnqruy".indexOf(bestCims[c].match.ch) != -1) {
				uaRelXHeightAscenderShiftSum += bestCims[c].relAscenderShift;
				uaRelXHeightAscenderShiftCount++;
			}
			else if ("ABDEFGHIKLMNPQRTUYbdfhkl0123456789".indexOf(bestCims[c].match.ch) != -1) {
				uaRelCapHeightAscenderShiftSum += bestCims[c].relAscenderShift;
				uaRelCapHeightAscenderShiftCount++;
			}
			if ("gjqy".indexOf(bestCims[c].match.ch) != -1) {
				uaRelDescenderShiftSum += bestCims[c].relDescenderShift;
				uaRelDescenderShiftCount++;
			}
		}
		return new CharMatchFontMetrics(
			((xHeightCount == 0) ? 0 : (xHeightSum / xHeightCount)),
			((capHeightCount == 0) ? 0 : (capHeightSum / capHeightCount)),
			((uaRelVerticalCenterShiftCount == 0) ? Float.NaN : (uaRelVerticalCenterShiftSum / uaRelVerticalCenterShiftCount)),
			((uaScaleLogXCount == 0) ? Float.NaN : (uaScaleLogXSum / uaScaleLogXCount)),
			((uaScaleLogYCount == 0) ? Float.NaN : (uaScaleLogYSum / uaScaleLogYCount)),
			((uaRelXHeightAscenderShiftCount == 0) ? Float.NaN : (uaRelXHeightAscenderShiftSum / uaRelXHeightAscenderShiftCount)),
			((uaRelCapHeightAscenderShiftCount == 0) ? Float.NaN : (uaRelCapHeightAscenderShiftSum / uaRelCapHeightAscenderShiftCount)),
			((uaRelDescenderShiftCount == 0) ? Float.NaN : (uaRelDescenderShiftSum / uaRelDescenderShiftCount))
		);
	}
	
	private static class CharMatchFontMetrics {
		final float uaXHeight;
		final float uaCapHeight;
		final float uaRelVerticalCenterShift;
		final float uaScaleLogX;
		final float uaScaleLogY;
		final float uaRelXHeightAscenderShift;
		final float uaRelCapHeightAscenderShift;
		final float uaRelDescenderShift;
		CharMatchFontMetrics(float uaXHeight, float uaCapHeight, float uaRelVerticalCenterShift, float uaScaleLogX, float uaScaleLogY, float uaRelXHeightAscenderShift, float uaRelCapHeightAscenderShift, float uaRelDescenderShift) {
			this.uaXHeight = uaXHeight;
			this.uaCapHeight = uaCapHeight;
			this.uaRelVerticalCenterShift = uaRelVerticalCenterShift;
			this.uaScaleLogX = uaScaleLogX;
			this.uaScaleLogY = uaScaleLogY;
			this.uaRelXHeightAscenderShift = uaRelXHeightAscenderShift;
			this.uaRelCapHeightAscenderShift = uaRelCapHeightAscenderShift;
			this.uaRelDescenderShift = uaRelDescenderShift;
		}
	}
	
	private static HashMap sidResolver = new HashMap();
	static {
		sidResolver.put(new Integer(0), ".notdef");
		sidResolver.put(new Integer(1), "space");
		sidResolver.put(new Integer(2), "exclam");
		sidResolver.put(new Integer(3), "quotedbl");
		sidResolver.put(new Integer(4), "numbersign");
		sidResolver.put(new Integer(5), "dollar");
		sidResolver.put(new Integer(6), "percent");
		sidResolver.put(new Integer(7), "ampersand");
		sidResolver.put(new Integer(8), "quoteright");
		sidResolver.put(new Integer(9), "parenleft");
		sidResolver.put(new Integer(10), "parenright");
		sidResolver.put(new Integer(11), "asterisk");
		sidResolver.put(new Integer(12), "plus");
		sidResolver.put(new Integer(13), "comma");
		sidResolver.put(new Integer(14), "hyphen");
		sidResolver.put(new Integer(15), "period");
		sidResolver.put(new Integer(16), "slash");
		sidResolver.put(new Integer(17), "zero");
		sidResolver.put(new Integer(18), "one");
		sidResolver.put(new Integer(19), "two");
		sidResolver.put(new Integer(20), "three");
		sidResolver.put(new Integer(21), "four");
		sidResolver.put(new Integer(22), "five");
		sidResolver.put(new Integer(23), "six");
		sidResolver.put(new Integer(24), "seven");
		sidResolver.put(new Integer(25), "eight");
		sidResolver.put(new Integer(26), "nine");
		sidResolver.put(new Integer(27), "colon");
		sidResolver.put(new Integer(28), "semicolon");
		sidResolver.put(new Integer(29), "less");
		sidResolver.put(new Integer(30), "equal");
		sidResolver.put(new Integer(31), "greater");
		sidResolver.put(new Integer(32), "question");
		sidResolver.put(new Integer(33), "at");
		sidResolver.put(new Integer(34), "A");
		sidResolver.put(new Integer(35), "B");
		sidResolver.put(new Integer(36), "C");
		sidResolver.put(new Integer(37), "D");
		sidResolver.put(new Integer(38), "E");
		sidResolver.put(new Integer(39), "F");
		sidResolver.put(new Integer(40), "G");
		sidResolver.put(new Integer(41), "H");
		sidResolver.put(new Integer(42), "I");
		sidResolver.put(new Integer(43), "J");
		sidResolver.put(new Integer(44), "K");
		sidResolver.put(new Integer(45), "L");
		sidResolver.put(new Integer(46), "M");
		sidResolver.put(new Integer(47), "N");
		sidResolver.put(new Integer(48), "O");
		sidResolver.put(new Integer(49), "P");
		sidResolver.put(new Integer(50), "Q");
		sidResolver.put(new Integer(51), "R");
		sidResolver.put(new Integer(52), "S");
		sidResolver.put(new Integer(53), "T");
		sidResolver.put(new Integer(54), "U");
		sidResolver.put(new Integer(55), "V");
		sidResolver.put(new Integer(56), "W");
		sidResolver.put(new Integer(57), "X");
		sidResolver.put(new Integer(58), "Y");
		sidResolver.put(new Integer(59), "Z");
		sidResolver.put(new Integer(60), "bracketleft");
		sidResolver.put(new Integer(61), "backslash");
		sidResolver.put(new Integer(62), "bracketright");
		sidResolver.put(new Integer(63), "asciicircum");
		sidResolver.put(new Integer(64), "underscore");
		sidResolver.put(new Integer(65), "quoteleft");
		sidResolver.put(new Integer(66), "a");
		sidResolver.put(new Integer(67), "b");
		sidResolver.put(new Integer(68), "c");
		sidResolver.put(new Integer(69), "d");
		sidResolver.put(new Integer(70), "e");
		sidResolver.put(new Integer(71), "f");
		sidResolver.put(new Integer(72), "g");
		sidResolver.put(new Integer(73), "h");
		sidResolver.put(new Integer(74), "i");
		sidResolver.put(new Integer(75), "j");
		sidResolver.put(new Integer(76), "k");
		sidResolver.put(new Integer(77), "l");
		sidResolver.put(new Integer(78), "m");
		sidResolver.put(new Integer(79), "n");
		sidResolver.put(new Integer(80), "o");
		sidResolver.put(new Integer(81), "p");
		sidResolver.put(new Integer(82), "q");
		sidResolver.put(new Integer(83), "r");
		sidResolver.put(new Integer(84), "s");
		sidResolver.put(new Integer(85), "t");
		sidResolver.put(new Integer(86), "u");
		sidResolver.put(new Integer(87), "v");
		sidResolver.put(new Integer(88), "w");
		sidResolver.put(new Integer(89), "x");
		sidResolver.put(new Integer(90), "y");
		sidResolver.put(new Integer(91), "z");
		sidResolver.put(new Integer(92), "braceleft");
		sidResolver.put(new Integer(93), "bar");
		sidResolver.put(new Integer(94), "braceright");
		sidResolver.put(new Integer(95), "asciitilde");
		sidResolver.put(new Integer(96), "exclamdown");
		sidResolver.put(new Integer(97), "cent");
		sidResolver.put(new Integer(98), "sterling");
		sidResolver.put(new Integer(99), "fraction");
		sidResolver.put(new Integer(100), "yen");
		sidResolver.put(new Integer(101), "florin");
		sidResolver.put(new Integer(102), "section");
		sidResolver.put(new Integer(103), "currency");
		sidResolver.put(new Integer(104), "quotesingle");
		sidResolver.put(new Integer(105), "quotedblleft");
		sidResolver.put(new Integer(106), "guillemotleft");
		sidResolver.put(new Integer(107), "guilsinglleft");
		sidResolver.put(new Integer(108), "guilsinglright");
		sidResolver.put(new Integer(109), "fi");
		sidResolver.put(new Integer(110), "fl");
		sidResolver.put(new Integer(111), "endash");
		sidResolver.put(new Integer(112), "dagger");
		sidResolver.put(new Integer(113), "daggerdbl");
		sidResolver.put(new Integer(114), "periodcentered");
		sidResolver.put(new Integer(115), "paragraph");
		sidResolver.put(new Integer(116), "bullet");
		sidResolver.put(new Integer(117), "quotesinglbase");
		sidResolver.put(new Integer(118), "quotedblbase");
		sidResolver.put(new Integer(119), "quotedblright");
		sidResolver.put(new Integer(120), "guillemotright");
		sidResolver.put(new Integer(121), "ellipsis");
		sidResolver.put(new Integer(122), "perthousand");
		sidResolver.put(new Integer(123), "questiondown");
		sidResolver.put(new Integer(124), "grave");
		sidResolver.put(new Integer(125), "acute");
		sidResolver.put(new Integer(126), "circumflex");
		sidResolver.put(new Integer(127), "tilde");
		sidResolver.put(new Integer(128), "macron");
		sidResolver.put(new Integer(129), "breve");
		sidResolver.put(new Integer(130), "dotaccent");
		sidResolver.put(new Integer(131), "dieresis");
		sidResolver.put(new Integer(132), "ring");
		sidResolver.put(new Integer(133), "cedilla");
		sidResolver.put(new Integer(134), "hungarumlaut");
		sidResolver.put(new Integer(135), "ogonek");
		sidResolver.put(new Integer(136), "caron");
		sidResolver.put(new Integer(137), "emdash");
		sidResolver.put(new Integer(138), "AE");
		sidResolver.put(new Integer(139), "ordfeminine");
		sidResolver.put(new Integer(140), "Lslash");
		sidResolver.put(new Integer(141), "Oslash");
		sidResolver.put(new Integer(142), "OE");
		sidResolver.put(new Integer(143), "ordmasculine");
		sidResolver.put(new Integer(144), "ae");
		sidResolver.put(new Integer(145), "dotlessi");
		sidResolver.put(new Integer(146), "lslash");
		sidResolver.put(new Integer(147), "oslash");
		sidResolver.put(new Integer(148), "oe");
		sidResolver.put(new Integer(149), "germandbls");
		sidResolver.put(new Integer(150), "onesuperior");
		sidResolver.put(new Integer(151), "logicalnot");
		sidResolver.put(new Integer(152), "mu");
		sidResolver.put(new Integer(153), "trademark");
		sidResolver.put(new Integer(154), "Eth");
		sidResolver.put(new Integer(155), "onehalf");
		sidResolver.put(new Integer(156), "plusminus");
		sidResolver.put(new Integer(157), "Thorn");
		sidResolver.put(new Integer(158), "onequarter");
		sidResolver.put(new Integer(159), "divide");
		sidResolver.put(new Integer(160), "brokenbar");
		sidResolver.put(new Integer(161), "degree");
		sidResolver.put(new Integer(162), "thorn");
		sidResolver.put(new Integer(163), "threequarters");
		sidResolver.put(new Integer(164), "twosuperior");
		sidResolver.put(new Integer(165), "registered");
		sidResolver.put(new Integer(166), "minus");
		sidResolver.put(new Integer(167), "eth");
		sidResolver.put(new Integer(168), "multiply");
		sidResolver.put(new Integer(169), "threesuperior");
		sidResolver.put(new Integer(170), "copyright");
		sidResolver.put(new Integer(171), "Aacute");
		sidResolver.put(new Integer(172), "Acircumflex");
		sidResolver.put(new Integer(173), "Adieresis");
		sidResolver.put(new Integer(174), "Agrave");
		sidResolver.put(new Integer(175), "Aring");
		sidResolver.put(new Integer(176), "Atilde");
		sidResolver.put(new Integer(177), "Ccedilla");
		sidResolver.put(new Integer(178), "Eacute");
		sidResolver.put(new Integer(179), "Ecircumflex");
		sidResolver.put(new Integer(180), "Edieresis");
		sidResolver.put(new Integer(181), "Egrave");
		sidResolver.put(new Integer(182), "Iacute");
		sidResolver.put(new Integer(183), "Icircumflex");
		sidResolver.put(new Integer(184), "Idieresis");
		sidResolver.put(new Integer(185), "Igrave");
		sidResolver.put(new Integer(186), "Ntilde");
		sidResolver.put(new Integer(187), "Oacute");
		sidResolver.put(new Integer(188), "Ocircumflex");
		sidResolver.put(new Integer(189), "Odieresis");
		sidResolver.put(new Integer(190), "Ograve");
		sidResolver.put(new Integer(191), "Otilde");
		sidResolver.put(new Integer(192), "Scaron");
		sidResolver.put(new Integer(193), "Uacute");
		sidResolver.put(new Integer(194), "Ucircumflex");
		sidResolver.put(new Integer(195), "Udieresis");
		sidResolver.put(new Integer(196), "Ugrave");
		sidResolver.put(new Integer(197), "Yacute");
		sidResolver.put(new Integer(198), "Ydieresis");
		sidResolver.put(new Integer(199), "Zcaron");
		sidResolver.put(new Integer(200), "aacute");
		sidResolver.put(new Integer(201), "acircumflex");
		sidResolver.put(new Integer(202), "adieresis");
		sidResolver.put(new Integer(203), "agrave");
		sidResolver.put(new Integer(204), "aring");
		sidResolver.put(new Integer(205), "atilde");
		sidResolver.put(new Integer(206), "ccedilla");
		sidResolver.put(new Integer(207), "eacute");
		sidResolver.put(new Integer(208), "ecircumflex");
		sidResolver.put(new Integer(209), "edieresis");
		sidResolver.put(new Integer(210), "egrave");
		sidResolver.put(new Integer(211), "iacute");
		sidResolver.put(new Integer(212), "icircumflex");
		sidResolver.put(new Integer(213), "idieresis");
		sidResolver.put(new Integer(214), "igrave");
		sidResolver.put(new Integer(215), "ntilde");
		sidResolver.put(new Integer(216), "oacute");
		sidResolver.put(new Integer(217), "ocircumflex");
		sidResolver.put(new Integer(218), "odieresis");
		sidResolver.put(new Integer(219), "ograve");
		sidResolver.put(new Integer(220), "otilde");
		sidResolver.put(new Integer(221), "scaron");
		sidResolver.put(new Integer(222), "uacute");
		sidResolver.put(new Integer(223), "ucircumflex");
		sidResolver.put(new Integer(224), "udieresis");
		sidResolver.put(new Integer(225), "ugrave");
		sidResolver.put(new Integer(226), "yacute");
		sidResolver.put(new Integer(227), "ydieresis");
		sidResolver.put(new Integer(228), "zcaron");
		sidResolver.put(new Integer(229), "exclamsmall");
		sidResolver.put(new Integer(230), "Hungarumlautsmall");
		sidResolver.put(new Integer(231), "dollaroldstyle");
		sidResolver.put(new Integer(232), "dollarsuperior");
		sidResolver.put(new Integer(233), "ampersandsmall");
		sidResolver.put(new Integer(234), "Acutesmall");
		sidResolver.put(new Integer(235), "parenleftsuperior");
		sidResolver.put(new Integer(236), "parenrightsuperior");
		sidResolver.put(new Integer(237), "twodotenleader");
		sidResolver.put(new Integer(238), "onedotenleader");
		sidResolver.put(new Integer(239), "zerooldstyle");
		sidResolver.put(new Integer(240), "oneoldstyle");
		sidResolver.put(new Integer(241), "twooldstyle");
		sidResolver.put(new Integer(242), "threeoldstyle");
		sidResolver.put(new Integer(243), "fouroldstyle");
		sidResolver.put(new Integer(244), "fiveoldstyle");
		sidResolver.put(new Integer(245), "sixoldstyle");
		sidResolver.put(new Integer(246), "sevenoldstyle");
		sidResolver.put(new Integer(247), "eightoldstyle");
		sidResolver.put(new Integer(248), "nineoldstyle");
		sidResolver.put(new Integer(249), "commasuperior");
		sidResolver.put(new Integer(250), "threequartersemdash");
		sidResolver.put(new Integer(251), "periodsuperior");
		sidResolver.put(new Integer(252), "questionsmall");
		sidResolver.put(new Integer(253), "asuperior");
		sidResolver.put(new Integer(254), "bsuperior");
		sidResolver.put(new Integer(255), "centsuperior");
		sidResolver.put(new Integer(256), "dsuperior");
		sidResolver.put(new Integer(257), "esuperior");
		sidResolver.put(new Integer(258), "isuperior");
		sidResolver.put(new Integer(259), "lsuperior");
		sidResolver.put(new Integer(260), "msuperior");
		sidResolver.put(new Integer(261), "nsuperior");
		sidResolver.put(new Integer(262), "osuperior");
		sidResolver.put(new Integer(263), "rsuperior");
		sidResolver.put(new Integer(264), "ssuperior");
		sidResolver.put(new Integer(265), "tsuperior");
		sidResolver.put(new Integer(266), "ff");
		sidResolver.put(new Integer(267), "ffi");
		sidResolver.put(new Integer(268), "ffl");
		sidResolver.put(new Integer(269), "parenleftinferior");
		sidResolver.put(new Integer(270), "parenrightinferior");
		sidResolver.put(new Integer(271), "Circumflexsmall");
		sidResolver.put(new Integer(272), "hyphensuperior");
		sidResolver.put(new Integer(273), "Gravesmall");
		sidResolver.put(new Integer(274), "Asmall");
		sidResolver.put(new Integer(275), "Bsmall");
		sidResolver.put(new Integer(276), "Csmall");
		sidResolver.put(new Integer(277), "Dsmall");
		sidResolver.put(new Integer(278), "Esmall");
		sidResolver.put(new Integer(279), "Fsmall");
		sidResolver.put(new Integer(280), "Gsmall");
		sidResolver.put(new Integer(281), "Hsmall");
		sidResolver.put(new Integer(282), "Ismall");
		sidResolver.put(new Integer(283), "Jsmall");
		sidResolver.put(new Integer(284), "Ksmall");
		sidResolver.put(new Integer(285), "Lsmall");
		sidResolver.put(new Integer(286), "Msmall");
		sidResolver.put(new Integer(287), "Nsmall");
		sidResolver.put(new Integer(288), "Osmall");
		sidResolver.put(new Integer(289), "Psmall");
		sidResolver.put(new Integer(290), "Qsmall");
		sidResolver.put(new Integer(291), "Rsmall");
		sidResolver.put(new Integer(292), "Ssmall");
		sidResolver.put(new Integer(293), "Tsmall");
		sidResolver.put(new Integer(294), "Usmall");
		sidResolver.put(new Integer(295), "Vsmall");
		sidResolver.put(new Integer(296), "Wsmall");
		sidResolver.put(new Integer(297), "Xsmall");
		sidResolver.put(new Integer(298), "Ysmall");
		sidResolver.put(new Integer(299), "Zsmall");
		sidResolver.put(new Integer(300), "colonmonetary");
		sidResolver.put(new Integer(301), "onefitted");
		sidResolver.put(new Integer(302), "rupiah");
		sidResolver.put(new Integer(303), "Tildesmall");
		sidResolver.put(new Integer(304), "exclamdownsmall");
		sidResolver.put(new Integer(305), "centoldstyle");
		sidResolver.put(new Integer(306), "Lslashsmall");
		sidResolver.put(new Integer(307), "Scaronsmall");
		sidResolver.put(new Integer(308), "Zcaronsmall");
		sidResolver.put(new Integer(309), "Dieresissmall");
		sidResolver.put(new Integer(310), "Brevesmall");
		sidResolver.put(new Integer(311), "Caronsmall");
		sidResolver.put(new Integer(312), "Dotaccentsmall");
		sidResolver.put(new Integer(313), "Macronsmall");
		sidResolver.put(new Integer(314), "figuredash");
		sidResolver.put(new Integer(315), "hypheninferior");
		sidResolver.put(new Integer(316), "Ogoneksmall");
		sidResolver.put(new Integer(317), "Ringsmall");
		sidResolver.put(new Integer(318), "Cedillasmall");
		sidResolver.put(new Integer(319), "questiondownsmall");
		sidResolver.put(new Integer(320), "oneeighth");
		sidResolver.put(new Integer(321), "threeeighths");
		sidResolver.put(new Integer(322), "fiveeighths");
		sidResolver.put(new Integer(323), "seveneighths");
		sidResolver.put(new Integer(324), "onethird");
		sidResolver.put(new Integer(325), "twothirds");
		sidResolver.put(new Integer(326), "zerosuperior");
		sidResolver.put(new Integer(327), "foursuperior");
		sidResolver.put(new Integer(328), "fivesuperior");
		sidResolver.put(new Integer(329), "sixsuperior");
		sidResolver.put(new Integer(330), "sevensuperior");
		sidResolver.put(new Integer(331), "eightsuperior");
		sidResolver.put(new Integer(332), "ninesuperior");
		sidResolver.put(new Integer(333), "zeroinferior");
		sidResolver.put(new Integer(334), "oneinferior");
		sidResolver.put(new Integer(335), "twoinferior");
		sidResolver.put(new Integer(336), "threeinferior");
		sidResolver.put(new Integer(337), "fourinferior");
		sidResolver.put(new Integer(338), "fiveinferior");
		sidResolver.put(new Integer(339), "sixinferior");
		sidResolver.put(new Integer(340), "seveninferior");
		sidResolver.put(new Integer(341), "eightinferior");
		sidResolver.put(new Integer(342), "nineinferior");
		sidResolver.put(new Integer(343), "centinferior");
		sidResolver.put(new Integer(344), "dollarinferior");
		sidResolver.put(new Integer(345), "periodinferior");
		sidResolver.put(new Integer(346), "commainferior");
		sidResolver.put(new Integer(347), "Agravesmall");
		sidResolver.put(new Integer(348), "Aacutesmall");
		sidResolver.put(new Integer(349), "Acircumflexsmall");
		sidResolver.put(new Integer(350), "Atildesmall");
		sidResolver.put(new Integer(351), "Adieresissmall");
		sidResolver.put(new Integer(352), "Aringsmall");
		sidResolver.put(new Integer(353), "AEsmall");
		sidResolver.put(new Integer(354), "Ccedillasmall");
		sidResolver.put(new Integer(355), "Egravesmall");
		sidResolver.put(new Integer(356), "Eacutesmall");
		sidResolver.put(new Integer(357), "Ecircumflexsmall");
		sidResolver.put(new Integer(358), "Edieresissmall");
		sidResolver.put(new Integer(359), "Igravesmall");
		sidResolver.put(new Integer(360), "Iacutesmall");
		sidResolver.put(new Integer(361), "Icircumflexsmall");
		sidResolver.put(new Integer(362), "Idieresissmall");
		sidResolver.put(new Integer(363), "Ethsmall");
		sidResolver.put(new Integer(364), "Ntildesmall");
		sidResolver.put(new Integer(365), "Ogravesmall");
		sidResolver.put(new Integer(366), "Oacutesmall");
		sidResolver.put(new Integer(367), "Ocircumflexsmall");
		sidResolver.put(new Integer(368), "Otildesmall");
		sidResolver.put(new Integer(369), "Odieresissmall");
		sidResolver.put(new Integer(370), "OEsmall");
		sidResolver.put(new Integer(371), "Oslashsmall");
		sidResolver.put(new Integer(372), "Ugravesmall");
		sidResolver.put(new Integer(373), "Uacutesmall");
		sidResolver.put(new Integer(374), "Ucircumflexsmall");
		sidResolver.put(new Integer(375), "Udieresissmall");
		sidResolver.put(new Integer(376), "Yacutesmall");
		sidResolver.put(new Integer(377), "Thornsmall");
		sidResolver.put(new Integer(378), "Ydieresissmall");
		sidResolver.put(new Integer(379), "001.000");
		sidResolver.put(new Integer(380), "001.001");
		sidResolver.put(new Integer(381), "001.002");
		sidResolver.put(new Integer(382), "001.003");
		sidResolver.put(new Integer(383), "Black");
		sidResolver.put(new Integer(384), "Bold");
		sidResolver.put(new Integer(385), "Book");
		sidResolver.put(new Integer(386), "Light");
		sidResolver.put(new Integer(387), "Medium");
		sidResolver.put(new Integer(388), "Regular");
		sidResolver.put(new Integer(389), "Roman");
		sidResolver.put(new Integer(390), "Semibold");
	}
	
	private static final int readUnsigned(byte[] bytes, int start, int end) {
		end = Math.min(end, bytes.length); // there _might_ be cases where this matters ...
		int ui = convertUnsigned(bytes[start++]);
		while (start < end) {
			ui <<= 8;
			ui += convertUnsigned(bytes[start++]);
		}
		return ui;
	}
	private static final int convertUnsigned(byte b) {
		return (((int) b) & 0x000000FF);
	}
	
//	private static CharImageMatch getCharForImage(CharImage charImage, Font[] serifFonts, Font[] sansFonts, HashMap cache, boolean debug) {
	private static CharImageMatch[] getCharsForImage(char ch, CharImage charImage, FontDecoderCharset charSet, Font[] serifFonts, Font[] sansFonts, Font[] monoFonts, HashMap cache, boolean debug) {
//		if (Character.toLowerCase(ch) != 'c')
//			return new CharImageMatch[0];
		
		//	wrap and measure char
		CharMetrics chMetrics = PdfCharDecoder.getCharMetrics(charImage.img, 1);
		
		//	compute additional metrics
		float chBoxHeightPercent = (((float) charImage.box.getHeight()) / charImage.img.getHeight());
		float chAboveBaselinePercent = Float.NaN;
		float chBelowBaselinePercent = Float.NaN;
		if (charImage.baseline >= 0) {
			if (charImage.box.getBottomRow() < charImage.baseline) {
				chAboveBaselinePercent = 1;
				chBelowBaselinePercent = 0;
			}
			else if (charImage.box.getTopRow() >= charImage.baseline) {
				chAboveBaselinePercent = 0;
				chBelowBaselinePercent = 1;
			}
			else {
				chAboveBaselinePercent = (((float) (charImage.baseline - charImage.box.getTopRow())) / charImage.box.getHeight());
				chBelowBaselinePercent = (((float) (charImage.box.getBottomRow() - charImage.baseline)) / charImage.box.getHeight());
			}
		}
		
		//	set up statistics
		CharImageMatch bestCim = null;
		BestCimList bestCims = new BestCimList(); // we have to use a list, as some characters share the same glyphs, which is catastrophic in a TreeSet
		
		//	get ranked list of probable matches
		SortedSet matchChars = PdfCharDecoder.getScoredCharSignatures(chMetrics, -1, charSet, true, ((char) 0), null);
		
		//	evaluate probable matches
		float bestCsSim = 0;
		float bestSim = 0;
//		boolean seenCh = (ch != ':');
		for (Iterator mcit = matchChars.iterator(); mcit.hasNext();) {
			ScoredCharSignature scs = ((ScoredCharSignature) mcit.next());
			if (scs.difference > 500)
				break;
//			if ((scs.difference > 500) && seenCh)
//				break;
//			if (scs.cs.ch == ch)
//				seenCh = true;
			if ("U".equals(PdfCharDecoder.getCharClass(scs.cs.ch)))
				continue;
//			System.out.println(" testing '" + scs.cs.ch + "' (" + ((int) scs.cs.ch) + "), signature difference is " + scs.difference);
			CharMatchResult cmr = PdfCharDecoder.matchChar(charImage, scs.cs.ch, false, serifFonts, sansFonts, monoFonts, cache, false, debug);
			if (!cmr.rendered)
				continue;
			
			CharImageMatch cim = null;
			String cimStyle = null;
			for (int s = 0; s < cmr.serifStyleCims.length; s++) {
				if (cmr.serifStyleCims[s] == null)
					continue;
				if (!checkMatchBaseline(charImage, chBoxHeightPercent, chAboveBaselinePercent, chBelowBaselinePercent, cmr.serifStyleCims[s].match))
					continue;
//				
//				if (Character.toLowerCase(ch) == '–') {
//					System.out.println("   ==> match '" + cmr.serifStyleCims[s].match.ch + "' (" + ((int) cmr.serifStyleCims[s].match.ch) + ", " + StringUtils.getCharName((char) cmr.serifStyleCims[s].match.ch) + ") in " + "Serif-" + s + ", similarity is " + cmr.serifStyleCims[s].sim);
//					System.out.println("    - signature difference is " + scs.difference);
//					System.out.println("    - scale logs are " + cmr.serifStyleCims[s].scaleLogX + "/" + cmr.serifStyleCims[s].scaleLogY);
//					System.out.println("    - vertical center shift is " + cmr.serifStyleCims[s].vCenterShift);
////					System.out.println("    - histogram similarities are " + cmr.serifStyleCims[s].xHistSim + "/" + cmr.serifStyleCims[s].yHistSim);
////					System.out.println("    - char histogram peaks are  " + charImage.xHistogramPeaks25 + "/" + charImage.xHistogramPeaks33 + "/" + charImage.xHistogramPeaks50 + "/" + charImage.xHistogramPeaks67 + "/" + charImage.xHistogramPeaks75 + " and " + charImage.yHistogramPeaks25 + "/" + charImage.yHistogramPeaks33 + "/" + charImage.yHistogramPeaks50 + "/" + charImage.yHistogramPeaks67 + "/" + charImage.yHistogramPeaks75);
////					System.out.println("    - match histogram peaks are " + cmr.serifStyleCims[s].match.xHistogramPeaks25 + "/" + cmr.serifStyleCims[s].match.xHistogramPeaks33 + "/" + cmr.serifStyleCims[s].match.xHistogramPeaks50 + "/" + cmr.serifStyleCims[s].match.xHistogramPeaks67 + "/" + cmr.serifStyleCims[s].match.xHistogramPeaks75 + " and " + cmr.serifStyleCims[s].match.yHistogramPeaks25 + "/" + cmr.serifStyleCims[s].match.yHistogramPeaks33 + "/" + cmr.serifStyleCims[s].match.yHistogramPeaks50 + "/" + cmr.serifStyleCims[s].match.yHistogramPeaks67 + "/" + cmr.serifStyleCims[s].match.yHistogramPeaks75);
//					if (PdfCharDecoder.displayCharMatch(cmr.serifStyleCims[s], "Char match") != JOptionPane.OK_OPTION)
//						ch = ' ';
//				}
				
				if ((cim == null) || (cim.sim < cmr.serifStyleCims[s].sim)) {
					cim = cmr.serifStyleCims[s];
					cimStyle = ("Serif-" + s);
				}
			}
			for (int s = 0; s < cmr.sansStyleCims.length; s++) {
				if (cmr.sansStyleCims[s] == null)
					continue;
				if (!checkMatchBaseline(charImage, chBoxHeightPercent, chAboveBaselinePercent, chBelowBaselinePercent, cmr.sansStyleCims[s].match))
					continue;
//				
//				if (Character.toLowerCase(ch) == '–') {
//					System.out.println("   ==> match '" + cmr.sansStyleCims[s].match.ch + "' (" + ((int) cmr.sansStyleCims[s].match.ch) + ", " + StringUtils.getCharName((char) cmr.sansStyleCims[s].match.ch) + ") in " + "Sans-" + s + ", similarity is " + cmr.sansStyleCims[s].sim);
//					System.out.println("    - signature difference is " + scs.difference);
//					System.out.println("    - scale logs are " + cmr.sansStyleCims[s].scaleLogX + "/" + cmr.sansStyleCims[s].scaleLogY);
//					System.out.println("    - vertical center shift is " + cmr.sansStyleCims[s].vCenterShift);
////					System.out.println("    - histogram similarities are " + cmr.sansStyleCims[s].xHistSim + "/" + cmr.sansStyleCims[s].yHistSim);
////					System.out.println("    - char histogram peaks are  " + charImage.xHistogramPeaks25 + "/" + charImage.xHistogramPeaks33 + "/" + charImage.xHistogramPeaks50 + "/" + charImage.xHistogramPeaks67 + "/" + charImage.xHistogramPeaks75 + " and " + charImage.yHistogramPeaks25 + "/" + charImage.yHistogramPeaks33 + "/" + charImage.yHistogramPeaks50 + "/" + charImage.yHistogramPeaks67 + "/" + charImage.yHistogramPeaks75);
////					System.out.println("    - match histogram peaks are " + cmr.sansStyleCims[s].match.xHistogramPeaks25 + "/" + cmr.sansStyleCims[s].match.xHistogramPeaks33 + "/" + cmr.sansStyleCims[s].match.xHistogramPeaks50 + "/" + cmr.sansStyleCims[s].match.xHistogramPeaks67 + "/" + cmr.sansStyleCims[s].match.xHistogramPeaks75 + " and " + cmr.sansStyleCims[s].match.yHistogramPeaks25 + "/" + cmr.sansStyleCims[s].match.yHistogramPeaks33 + "/" + cmr.sansStyleCims[s].match.yHistogramPeaks50 + "/" + cmr.sansStyleCims[s].match.yHistogramPeaks67 + "/" + cmr.sansStyleCims[s].match.yHistogramPeaks75);
//					if (PdfCharDecoder.displayCharMatch(cmr.sansStyleCims[s], "Char match") != JOptionPane.OK_OPTION)
//						ch = ' ';
//				}
				
				if ((cim == null) || (cim.sim < cmr.sansStyleCims[s].sim)) {
					cim = cmr.sansStyleCims[s];
					cimStyle = ("Sans-" + s);
				}
			}
			for (int s = 0; s < cmr.monoStyleCims.length; s++) {
				if (cmr.monoStyleCims[s] == null)
					continue;
				if (!checkMatchBaseline(charImage, chBoxHeightPercent, chAboveBaselinePercent, chBelowBaselinePercent, cmr.monoStyleCims[s].match))
					continue;
//				
//				if (Character.toLowerCase(ch) == '–') {
//					System.out.println("   ==> match '" + cmr.sansStyleCims[s].match.ch + "' (" + ((int) cmr.sansStyleCims[s].match.ch) + ", " + StringUtils.getCharName((char) cmr.sansStyleCims[s].match.ch) + ") in " + "Sans-" + s + ", similarity is " + cmr.sansStyleCims[s].sim);
//					System.out.println("    - signature difference is " + scs.difference);
//					System.out.println("    - scale logs are " + cmr.sansStyleCims[s].scaleLogX + "/" + cmr.sansStyleCims[s].scaleLogY);
//					System.out.println("    - vertical center shift is " + cmr.sansStyleCims[s].vCenterShift);
////					System.out.println("    - histogram similarities are " + cmr.sansStyleCims[s].xHistSim + "/" + cmr.sansStyleCims[s].yHistSim);
////					System.out.println("    - char histogram peaks are  " + charImage.xHistogramPeaks25 + "/" + charImage.xHistogramPeaks33 + "/" + charImage.xHistogramPeaks50 + "/" + charImage.xHistogramPeaks67 + "/" + charImage.xHistogramPeaks75 + " and " + charImage.yHistogramPeaks25 + "/" + charImage.yHistogramPeaks33 + "/" + charImage.yHistogramPeaks50 + "/" + charImage.yHistogramPeaks67 + "/" + charImage.yHistogramPeaks75);
////					System.out.println("    - match histogram peaks are " + cmr.sansStyleCims[s].match.xHistogramPeaks25 + "/" + cmr.sansStyleCims[s].match.xHistogramPeaks33 + "/" + cmr.sansStyleCims[s].match.xHistogramPeaks50 + "/" + cmr.sansStyleCims[s].match.xHistogramPeaks67 + "/" + cmr.sansStyleCims[s].match.xHistogramPeaks75 + " and " + cmr.sansStyleCims[s].match.yHistogramPeaks25 + "/" + cmr.sansStyleCims[s].match.yHistogramPeaks33 + "/" + cmr.sansStyleCims[s].match.yHistogramPeaks50 + "/" + cmr.sansStyleCims[s].match.yHistogramPeaks67 + "/" + cmr.sansStyleCims[s].match.yHistogramPeaks75);
//					if (PdfCharDecoder.displayCharMatch(cmr.sansStyleCims[s], "Char match") != JOptionPane.OK_OPTION)
//						ch = ' ';
//				}
				
				if ((cim == null) || (cim.sim < cmr.monoStyleCims[s].sim)) {
					cim = cmr.monoStyleCims[s];
					cimStyle = ("Mono-" + s);
				}
			}
			
			if (cim == null) {
//				System.out.println("   --> could not render image");
				continue;
			}
//			System.out.println("   --> similarity is " + cim.sim + " in " + cimStyle);
			
			//	collect char image matches
			bestCims.add(cim);
//			
//			if (Character.toLowerCase(ch) == '–') {
//				System.out.println("   ==> match '" + scs.cs.ch + "' (" + ((int) scs.cs.ch) + ", " + StringUtils.getCharName((char) scs.cs.ch) + ") in " + cimStyle + ", similarity is " + cim.sim);
//				System.out.println("    - scale logs are " + cim.scaleLogX + "/" + cim.scaleLogY);
////				System.out.println("    - histogram similarities are " + cim.xHistSim + "/" + cim.yHistSim);
////				System.out.println("    - char histogram peaks are  " + charImage.xHistogramPeaks25 + "/" + charImage.xHistogramPeaks33 + "/" + charImage.xHistogramPeaks50 + "/" + charImage.xHistogramPeaks67 + "/" + charImage.xHistogramPeaks75 + " and " + charImage.yHistogramPeaks25 + "/" + charImage.yHistogramPeaks33 + "/" + charImage.yHistogramPeaks50 + "/" + charImage.yHistogramPeaks67 + "/" + charImage.yHistogramPeaks75);
////				System.out.println("    - match histogram peaks are " + cim.match.xHistogramPeaks25 + "/" + cim.match.xHistogramPeaks33 + "/" + cim.match.xHistogramPeaks50 + "/" + cim.match.xHistogramPeaks67 + "/" + cim.match.xHistogramPeaks75 + " and " + cim.match.yHistogramPeaks25 + "/" + cim.match.yHistogramPeaks33 + "/" + cim.match.yHistogramPeaks50 + "/" + cim.match.yHistogramPeaks67 + "/" + cim.match.yHistogramPeaks75);
//				if (PdfCharDecoder.displayCharMatch(cim, "Char match") != JOptionPane.OK_OPTION)
//					ch = ' ';
//			}
			
			//	do we have a new best match?
			if ((bestCim == null) || ((cim.sim - (scs.difference / 25)) > (bestSim - (bestCsSim / 25)))) {
				System.out.println("   ==> new best match '" + scs.cs.ch + "' (" + ((int) scs.cs.ch) + ", " + StringUtils.getCharName((char) scs.cs.ch) + ") in " + cimStyle + ", similarity is " + cim.sim);
				System.out.println("    - signature difference is " + scs.difference);
				System.out.println("    - scale logs are " + cim.scaleLogX + "/" + cim.scaleLogY);
				System.out.println("    - vertical center shift is " + cim.vCenterShift);
//				System.out.println("    - histogram similarities are " + cim.xHistSim + "/" + cim.yHistSim);
//				System.out.println("    - char histogram peaks are  " + charImage.xHistogramPeaks25 + "/" + charImage.xHistogramPeaks33 + "/" + charImage.xHistogramPeaks50 + "/" + charImage.xHistogramPeaks67 + "/" + charImage.xHistogramPeaks75 + " and " + charImage.yHistogramPeaks25 + "/" + charImage.yHistogramPeaks33 + "/" + charImage.yHistogramPeaks50 + "/" + charImage.yHistogramPeaks67 + "/" + charImage.yHistogramPeaks75);
//				System.out.println("    - match histogram peaks are " + cim.match.xHistogramPeaks25 + "/" + cim.match.xHistogramPeaks33 + "/" + cim.match.xHistogramPeaks50 + "/" + cim.match.xHistogramPeaks67 + "/" + cim.match.xHistogramPeaks75 + " and " + cim.match.yHistogramPeaks25 + "/" + cim.match.yHistogramPeaks33 + "/" + cim.match.yHistogramPeaks50 + "/" + cim.match.yHistogramPeaks67 + "/" + cim.match.yHistogramPeaks75);
				if (bestCim != null) {
					System.out.println("    - improvement is " + (cim.sim - bestCim.sim));
					System.out.println("    - sig diff factor is " + (scs.difference / bestCsSim));
					System.out.println("    - sig diff malus is " + (scs.difference / (bestCsSim * 100)));
					if ((bestCim.sim + (scs.difference / (bestCsSim * 100))) > cim.sim) {
						System.out.println("    ==> rejected for signature difference");
//						if (debug || FORCE_OCR_DECODING)
//							PdfCharDecoder.displayCharMatch(cim, "New best match rejected for signature difference");
						continue;
					}
				}
//				if (debug || FORCE_OCR_DECODING)
//					PdfCharDecoder.displayCharMatch(cim, "New best match");
				bestCim = cim;
				bestCsSim = scs.difference;
				bestSim = cim.sim;
				
				//	truncate collected matches based on new threshold
				bestCims.truncate();
			}
		}
		
		//	truncate collected matches one last time
		bestCims.truncate();
		System.out.println(" got " + bestCims.size() + " matches in the top 10%:");
		for (int c = 0; c < bestCims.size(); c++) {
			CharImageMatch cim = ((CharImageMatch) bestCims.get(c));
			System.out.println("   - " + cim.match.ch + " (" + ((int) cim.match.ch) + ") at " + cim.sim + ", shift " + cim.vCenterShift + ", scale " + cim.scaleLogX + "/" + cim.scaleLogY + ", " + PdfCharDecoder.getCharClass(cim.match.ch));
		}
		
		//	finally ...
//		return bestCim;
		return ((CharImageMatch[]) bestCims.toArray(new CharImageMatch[bestCims.size()]));
	}
	
	private static boolean checkMatchBaseline(CharImage charImage, float chBoxHeightPercent, float chAboveBaselinePercent, float chBelowBaselinePercent, CharImage match) {
		if (charImage.baseline < 0)
			return true;
		if (match.baseline < 0)
			return true;
		float mChBoxHeightPercent = (((float) match.box.getHeight()) / match.img.getHeight());
		if ((chBoxHeightPercent < 0.2) && (mChBoxHeightPercent < 0.2))
			return true;
		float mChAboveBaselinePercent;
		float mChBelowBaselinePercent;
		if (match.box.getBottomRow() < match.baseline) {
			mChAboveBaselinePercent = 1;
			mChBelowBaselinePercent = 0;
		}
		else if (match.box.getTopRow() >= match.baseline) {
			mChAboveBaselinePercent = 0;
			mChBelowBaselinePercent = 1;
		}
		else {
			mChAboveBaselinePercent = (((float) (match.baseline - match.box.getTopRow())) / match.box.getHeight());
			mChBelowBaselinePercent = (((float) (match.box.getBottomRow() - match.baseline)) / match.box.getHeight());
		}
		if (Math.abs(chAboveBaselinePercent - mChAboveBaselinePercent) > 0.5)
			return false;
		if (Math.abs(chBelowBaselinePercent - mChBelowBaselinePercent) > 0.5)
			return false;
		return true;
	}
	
	private static class BestCimList extends ArrayList {
		void truncate() {
			if (this.isEmpty())
				return;
			Collections.sort(this, charImageMatchOrder);
			CharImageMatch bestCim = ((CharImageMatch) this.get(0));
			while (this.size() > 1) {
				CharImageMatch worstBestCim = ((CharImageMatch) this.get(this.size() - 1));
				if ((worstBestCim.sim * 10) < (bestCim.sim * 9)) // TODO optimize threshold over time
					this.remove(this.size() - 1);
				else break;
			}
		}
	}
	
	private static final Comparator charImageMatchOrder = new Comparator() {
		public int compare(Object obj1, Object obj2) {
			CharImageMatch cim1 = ((CharImageMatch) obj1);
			CharImageMatch cim2 = ((CharImageMatch) obj2);
			return Float.compare(cim2.sim, cim1.sim);
		}
	};
	
	private static final Map type1cIsoAdobeCharSet = Collections.synchronizedMap(new LinkedHashMap());
	static {
		type1cIsoAdobeCharSet.put(new Integer(0), ".notdef");
		type1cIsoAdobeCharSet.put(new Integer(1), "space");
		type1cIsoAdobeCharSet.put(new Integer(2), "exclam");
		type1cIsoAdobeCharSet.put(new Integer(3), "quotedbl");
		type1cIsoAdobeCharSet.put(new Integer(4), "numbersign");
		type1cIsoAdobeCharSet.put(new Integer(5), "dollar");
		type1cIsoAdobeCharSet.put(new Integer(6), "percent");
		type1cIsoAdobeCharSet.put(new Integer(7), "ampersand");
		type1cIsoAdobeCharSet.put(new Integer(8), "quoteright");
		type1cIsoAdobeCharSet.put(new Integer(9), "parenleft");
		type1cIsoAdobeCharSet.put(new Integer(10), "parenright");
		type1cIsoAdobeCharSet.put(new Integer(11), "asterisk");
		type1cIsoAdobeCharSet.put(new Integer(12), "plus");
		type1cIsoAdobeCharSet.put(new Integer(13), "comma");
		type1cIsoAdobeCharSet.put(new Integer(14), "hyphen");
		type1cIsoAdobeCharSet.put(new Integer(15), "period");
		type1cIsoAdobeCharSet.put(new Integer(16), "slash");
		type1cIsoAdobeCharSet.put(new Integer(17), "zero");
		type1cIsoAdobeCharSet.put(new Integer(18), "one");
		type1cIsoAdobeCharSet.put(new Integer(19), "two");
		type1cIsoAdobeCharSet.put(new Integer(20), "three");
		type1cIsoAdobeCharSet.put(new Integer(21), "four");
		type1cIsoAdobeCharSet.put(new Integer(22), "five");
		type1cIsoAdobeCharSet.put(new Integer(23), "six");
		type1cIsoAdobeCharSet.put(new Integer(24), "seven");
		type1cIsoAdobeCharSet.put(new Integer(25), "eight");
		type1cIsoAdobeCharSet.put(new Integer(26), "nine");
		type1cIsoAdobeCharSet.put(new Integer(27), "colon");
		type1cIsoAdobeCharSet.put(new Integer(28), "semicolon");
		type1cIsoAdobeCharSet.put(new Integer(29), "less");
		type1cIsoAdobeCharSet.put(new Integer(30), "equal");
		type1cIsoAdobeCharSet.put(new Integer(31), "greater");
		type1cIsoAdobeCharSet.put(new Integer(32), "question");
		type1cIsoAdobeCharSet.put(new Integer(33), "at");
		type1cIsoAdobeCharSet.put(new Integer(34), "A");
		type1cIsoAdobeCharSet.put(new Integer(35), "B");
		type1cIsoAdobeCharSet.put(new Integer(36), "C");
		type1cIsoAdobeCharSet.put(new Integer(37), "D");
		type1cIsoAdobeCharSet.put(new Integer(38), "E");
		type1cIsoAdobeCharSet.put(new Integer(39), "F");
		type1cIsoAdobeCharSet.put(new Integer(40), "G");
		type1cIsoAdobeCharSet.put(new Integer(41), "H");
		type1cIsoAdobeCharSet.put(new Integer(42), "I");
		type1cIsoAdobeCharSet.put(new Integer(43), "J");
		type1cIsoAdobeCharSet.put(new Integer(44), "K");
		type1cIsoAdobeCharSet.put(new Integer(45), "L");
		type1cIsoAdobeCharSet.put(new Integer(46), "M");
		type1cIsoAdobeCharSet.put(new Integer(47), "N");
		type1cIsoAdobeCharSet.put(new Integer(48), "O");
		type1cIsoAdobeCharSet.put(new Integer(49), "P");
		type1cIsoAdobeCharSet.put(new Integer(50), "Q");
		type1cIsoAdobeCharSet.put(new Integer(51), "R");
		type1cIsoAdobeCharSet.put(new Integer(52), "S");
		type1cIsoAdobeCharSet.put(new Integer(53), "T");
		type1cIsoAdobeCharSet.put(new Integer(54), "U");
		type1cIsoAdobeCharSet.put(new Integer(55), "V");
		type1cIsoAdobeCharSet.put(new Integer(56), "W");
		type1cIsoAdobeCharSet.put(new Integer(57), "X");
		type1cIsoAdobeCharSet.put(new Integer(58), "Y");
		type1cIsoAdobeCharSet.put(new Integer(59), "Z");
		type1cIsoAdobeCharSet.put(new Integer(60), "bracketleft");
		type1cIsoAdobeCharSet.put(new Integer(61), "backslash");
		type1cIsoAdobeCharSet.put(new Integer(62), "bracketright");
		type1cIsoAdobeCharSet.put(new Integer(63), "asciicircum");
		type1cIsoAdobeCharSet.put(new Integer(64), "underscore");
		type1cIsoAdobeCharSet.put(new Integer(65), "quoteleft");
		type1cIsoAdobeCharSet.put(new Integer(66), "a");
		type1cIsoAdobeCharSet.put(new Integer(67), "b");
		type1cIsoAdobeCharSet.put(new Integer(68), "c");
		type1cIsoAdobeCharSet.put(new Integer(69), "d");
		type1cIsoAdobeCharSet.put(new Integer(70), "e");
		type1cIsoAdobeCharSet.put(new Integer(71), "f");
		type1cIsoAdobeCharSet.put(new Integer(72), "g");
		type1cIsoAdobeCharSet.put(new Integer(73), "h");
		type1cIsoAdobeCharSet.put(new Integer(74), "i");
		type1cIsoAdobeCharSet.put(new Integer(75), "j");
		type1cIsoAdobeCharSet.put(new Integer(76), "k");
		type1cIsoAdobeCharSet.put(new Integer(77), "l");
		type1cIsoAdobeCharSet.put(new Integer(78), "m");
		type1cIsoAdobeCharSet.put(new Integer(79), "n");
		type1cIsoAdobeCharSet.put(new Integer(80), "o");
		type1cIsoAdobeCharSet.put(new Integer(81), "p");
		type1cIsoAdobeCharSet.put(new Integer(82), "q");
		type1cIsoAdobeCharSet.put(new Integer(83), "r");
		type1cIsoAdobeCharSet.put(new Integer(84), "s");
		type1cIsoAdobeCharSet.put(new Integer(85), "t");
		type1cIsoAdobeCharSet.put(new Integer(86), "u");
		type1cIsoAdobeCharSet.put(new Integer(87), "v");
		type1cIsoAdobeCharSet.put(new Integer(88), "w");
		type1cIsoAdobeCharSet.put(new Integer(89), "x");
		type1cIsoAdobeCharSet.put(new Integer(90), "y");
		type1cIsoAdobeCharSet.put(new Integer(91), "z");
		type1cIsoAdobeCharSet.put(new Integer(92), "braceleft");
		type1cIsoAdobeCharSet.put(new Integer(93), "bar");
		type1cIsoAdobeCharSet.put(new Integer(94), "braceright");
		type1cIsoAdobeCharSet.put(new Integer(95), "asciitilde");
		type1cIsoAdobeCharSet.put(new Integer(96), "exclamdown");
		type1cIsoAdobeCharSet.put(new Integer(97), "cent");
		type1cIsoAdobeCharSet.put(new Integer(98), "sterling");
		type1cIsoAdobeCharSet.put(new Integer(99), "fraction");
		type1cIsoAdobeCharSet.put(new Integer(100), "yen");
		type1cIsoAdobeCharSet.put(new Integer(101), "florin");
		type1cIsoAdobeCharSet.put(new Integer(102), "section");
		type1cIsoAdobeCharSet.put(new Integer(103), "currency");
		type1cIsoAdobeCharSet.put(new Integer(104), "quotesingle");
		type1cIsoAdobeCharSet.put(new Integer(105), "quotedblleft");
		type1cIsoAdobeCharSet.put(new Integer(106), "guillemotleft");
		type1cIsoAdobeCharSet.put(new Integer(107), "guilsinglleft");
		type1cIsoAdobeCharSet.put(new Integer(108), "guilsinglright");
		type1cIsoAdobeCharSet.put(new Integer(109), "fi");
		type1cIsoAdobeCharSet.put(new Integer(110), "fl");
		type1cIsoAdobeCharSet.put(new Integer(111), "endash");
		type1cIsoAdobeCharSet.put(new Integer(112), "dagger");
		type1cIsoAdobeCharSet.put(new Integer(113), "daggerdbl");
		type1cIsoAdobeCharSet.put(new Integer(114), "periodcentered");
		type1cIsoAdobeCharSet.put(new Integer(115), "paragraph");
		type1cIsoAdobeCharSet.put(new Integer(116), "bullet");
		type1cIsoAdobeCharSet.put(new Integer(117), "quotesinglbase");
		type1cIsoAdobeCharSet.put(new Integer(118), "quotedblbase");
		type1cIsoAdobeCharSet.put(new Integer(119), "quotedblright");
		type1cIsoAdobeCharSet.put(new Integer(120), "guillemotright");
		type1cIsoAdobeCharSet.put(new Integer(121), "ellipsis");
		type1cIsoAdobeCharSet.put(new Integer(122), "perthousand");
		type1cIsoAdobeCharSet.put(new Integer(123), "questiondown");
		type1cIsoAdobeCharSet.put(new Integer(124), "grave");
		type1cIsoAdobeCharSet.put(new Integer(125), "acute");
		type1cIsoAdobeCharSet.put(new Integer(126), "circumflex");
		type1cIsoAdobeCharSet.put(new Integer(127), "tilde");
		type1cIsoAdobeCharSet.put(new Integer(128), "macron");
		type1cIsoAdobeCharSet.put(new Integer(129), "breve");
		type1cIsoAdobeCharSet.put(new Integer(130), "dotaccent");
		type1cIsoAdobeCharSet.put(new Integer(131), "dieresis");
		type1cIsoAdobeCharSet.put(new Integer(132), "ring");
		type1cIsoAdobeCharSet.put(new Integer(133), "cedilla");
		type1cIsoAdobeCharSet.put(new Integer(134), "hungarumlaut");
		type1cIsoAdobeCharSet.put(new Integer(135), "ogonek");
		type1cIsoAdobeCharSet.put(new Integer(136), "caron");
		type1cIsoAdobeCharSet.put(new Integer(137), "emdash");
		type1cIsoAdobeCharSet.put(new Integer(138), "AE");
		type1cIsoAdobeCharSet.put(new Integer(139), "ordfeminine");
		type1cIsoAdobeCharSet.put(new Integer(140), "Lslash");
		type1cIsoAdobeCharSet.put(new Integer(141), "Oslash");
		type1cIsoAdobeCharSet.put(new Integer(142), "OE");
		type1cIsoAdobeCharSet.put(new Integer(143), "ordmasculine");
		type1cIsoAdobeCharSet.put(new Integer(144), "ae");
		type1cIsoAdobeCharSet.put(new Integer(145), "dotlessi");
		type1cIsoAdobeCharSet.put(new Integer(146), "lslash");
		type1cIsoAdobeCharSet.put(new Integer(147), "oslash");
		type1cIsoAdobeCharSet.put(new Integer(148), "oe");
		type1cIsoAdobeCharSet.put(new Integer(149), "germandbls");
		type1cIsoAdobeCharSet.put(new Integer(150), "onesuperior");
		type1cIsoAdobeCharSet.put(new Integer(151), "logicalnot");
		type1cIsoAdobeCharSet.put(new Integer(152), "mu");
		type1cIsoAdobeCharSet.put(new Integer(153), "trademark");
		type1cIsoAdobeCharSet.put(new Integer(154), "Eth");
		type1cIsoAdobeCharSet.put(new Integer(155), "onehalf");
		type1cIsoAdobeCharSet.put(new Integer(156), "plusminus");
		type1cIsoAdobeCharSet.put(new Integer(157), "Thorn");
		type1cIsoAdobeCharSet.put(new Integer(158), "onequarter");
		type1cIsoAdobeCharSet.put(new Integer(159), "divide");
		type1cIsoAdobeCharSet.put(new Integer(160), "brokenbar");
		type1cIsoAdobeCharSet.put(new Integer(161), "degree");
		type1cIsoAdobeCharSet.put(new Integer(162), "thorn");
		type1cIsoAdobeCharSet.put(new Integer(163), "threequarters");
		type1cIsoAdobeCharSet.put(new Integer(164), "twosuperior");
		type1cIsoAdobeCharSet.put(new Integer(165), "registered");
		type1cIsoAdobeCharSet.put(new Integer(166), "minus");
		type1cIsoAdobeCharSet.put(new Integer(167), "eth");
		type1cIsoAdobeCharSet.put(new Integer(168), "multiply");
		type1cIsoAdobeCharSet.put(new Integer(169), "threesuperior");
		type1cIsoAdobeCharSet.put(new Integer(170), "copyright");
		type1cIsoAdobeCharSet.put(new Integer(171), "Aacute");
		type1cIsoAdobeCharSet.put(new Integer(172), "Acircumflex");
		type1cIsoAdobeCharSet.put(new Integer(173), "Adieresis");
		type1cIsoAdobeCharSet.put(new Integer(174), "Agrave");
		type1cIsoAdobeCharSet.put(new Integer(175), "Aring");
		type1cIsoAdobeCharSet.put(new Integer(176), "Atilde");
		type1cIsoAdobeCharSet.put(new Integer(177), "Ccedilla");
		type1cIsoAdobeCharSet.put(new Integer(178), "Eacute");
		type1cIsoAdobeCharSet.put(new Integer(179), "Ecircumflex");
		type1cIsoAdobeCharSet.put(new Integer(180), "Edieresis");
		type1cIsoAdobeCharSet.put(new Integer(181), "Egrave");
		type1cIsoAdobeCharSet.put(new Integer(182), "Iacute");
		type1cIsoAdobeCharSet.put(new Integer(183), "Icircumflex");
		type1cIsoAdobeCharSet.put(new Integer(184), "Idieresis");
		type1cIsoAdobeCharSet.put(new Integer(185), "Igrave");
		type1cIsoAdobeCharSet.put(new Integer(186), "Ntilde");
		type1cIsoAdobeCharSet.put(new Integer(187), "Oacute");
		type1cIsoAdobeCharSet.put(new Integer(188), "Ocircumflex");
		type1cIsoAdobeCharSet.put(new Integer(189), "Odieresis");
		type1cIsoAdobeCharSet.put(new Integer(190), "Ograve");
		type1cIsoAdobeCharSet.put(new Integer(191), "Otilde");
		type1cIsoAdobeCharSet.put(new Integer(192), "Scaron");
		type1cIsoAdobeCharSet.put(new Integer(193), "Uacute");
		type1cIsoAdobeCharSet.put(new Integer(194), "Ucircumflex");
		type1cIsoAdobeCharSet.put(new Integer(195), "Udieresis");
		type1cIsoAdobeCharSet.put(new Integer(196), "Ugrave");
		type1cIsoAdobeCharSet.put(new Integer(197), "Yacute");
		type1cIsoAdobeCharSet.put(new Integer(198), "Ydieresis");
		type1cIsoAdobeCharSet.put(new Integer(199), "Zcaron");
		type1cIsoAdobeCharSet.put(new Integer(200), "aacute");
		type1cIsoAdobeCharSet.put(new Integer(201), "acircumflex");
		type1cIsoAdobeCharSet.put(new Integer(202), "adieresis");
		type1cIsoAdobeCharSet.put(new Integer(203), "agrave");
		type1cIsoAdobeCharSet.put(new Integer(204), "aring");
		type1cIsoAdobeCharSet.put(new Integer(205), "atilde");
		type1cIsoAdobeCharSet.put(new Integer(206), "ccedilla");
		type1cIsoAdobeCharSet.put(new Integer(207), "eacute");
		type1cIsoAdobeCharSet.put(new Integer(208), "ecircumflex");
		type1cIsoAdobeCharSet.put(new Integer(209), "edieresis");
		type1cIsoAdobeCharSet.put(new Integer(210), "egrave");
		type1cIsoAdobeCharSet.put(new Integer(211), "iacute");
		type1cIsoAdobeCharSet.put(new Integer(212), "icircumflex");
		type1cIsoAdobeCharSet.put(new Integer(213), "idieresis");
		type1cIsoAdobeCharSet.put(new Integer(214), "igrave");
		type1cIsoAdobeCharSet.put(new Integer(215), "ntilde");
		type1cIsoAdobeCharSet.put(new Integer(216), "oacute");
		type1cIsoAdobeCharSet.put(new Integer(217), "ocircumflex");
		type1cIsoAdobeCharSet.put(new Integer(218), "odieresis");
		type1cIsoAdobeCharSet.put(new Integer(219), "ograve");
		type1cIsoAdobeCharSet.put(new Integer(220), "otilde");
		type1cIsoAdobeCharSet.put(new Integer(221), "scaron");
		type1cIsoAdobeCharSet.put(new Integer(222), "uacute");
		type1cIsoAdobeCharSet.put(new Integer(223), "ucircumflex");
		type1cIsoAdobeCharSet.put(new Integer(224), "udieresis");
		type1cIsoAdobeCharSet.put(new Integer(225), "ugrave");
		type1cIsoAdobeCharSet.put(new Integer(226), "yacute");
		type1cIsoAdobeCharSet.put(new Integer(227), "ydieresis");
		type1cIsoAdobeCharSet.put(new Integer(228), "zcaron");
	}
	
	private static final Map type1cExpertCharSet = Collections.synchronizedMap(new LinkedHashMap());
	static {
		type1cExpertCharSet.put(new Integer(0), ".notdef");
		type1cExpertCharSet.put(new Integer(1), "space");
		type1cExpertCharSet.put(new Integer(229), "exclamsmall");
		type1cExpertCharSet.put(new Integer(230), "Hungarumlautsmall");
		type1cExpertCharSet.put(new Integer(231), "dollaroldstyle");
		type1cExpertCharSet.put(new Integer(232), "dollarsuperior");
		type1cExpertCharSet.put(new Integer(233), "ampersandsmall");
		type1cExpertCharSet.put(new Integer(234), "Acutesmall");
		type1cExpertCharSet.put(new Integer(235), "parenleftsuperior");
		type1cExpertCharSet.put(new Integer(236), "parenrightsuperior");
		type1cExpertCharSet.put(new Integer(237), "twodotenleader");
		type1cExpertCharSet.put(new Integer(238), "onedotenleader");
		type1cExpertCharSet.put(new Integer(13), "comma");
		type1cExpertCharSet.put(new Integer(14), "hyphen");
		type1cExpertCharSet.put(new Integer(15), "period");
		type1cExpertCharSet.put(new Integer(99), "fraction");
		type1cExpertCharSet.put(new Integer(239), "zerooldstyle");
		type1cExpertCharSet.put(new Integer(240), "oneoldstyle");
		type1cExpertCharSet.put(new Integer(241), "twooldstyle");
		type1cExpertCharSet.put(new Integer(242), "threeoldstyle");
		type1cExpertCharSet.put(new Integer(243), "fouroldstyle");
		type1cExpertCharSet.put(new Integer(244), "fiveoldstyle");
		type1cExpertCharSet.put(new Integer(245), "sixoldstyle");
		type1cExpertCharSet.put(new Integer(246), "sevenoldstyle");
		type1cExpertCharSet.put(new Integer(247), "eightoldstyle");
		type1cExpertCharSet.put(new Integer(248), "nineoldstyle");
		type1cExpertCharSet.put(new Integer(27), "colon");
		type1cExpertCharSet.put(new Integer(28), "semicolon");
		type1cExpertCharSet.put(new Integer(249), "commasuperior");
		type1cExpertCharSet.put(new Integer(250), "threequartersemdash");
		type1cExpertCharSet.put(new Integer(251), "periodsuperior");
		type1cExpertCharSet.put(new Integer(252), "questionsmall");
		type1cExpertCharSet.put(new Integer(253), "asuperior");
		type1cExpertCharSet.put(new Integer(254), "bsuperior");
		type1cExpertCharSet.put(new Integer(255), "centsuperior");
		type1cExpertCharSet.put(new Integer(256), "dsuperior");
		type1cExpertCharSet.put(new Integer(257), "esuperior");
		type1cExpertCharSet.put(new Integer(258), "isuperior");
		type1cExpertCharSet.put(new Integer(259), "lsuperior");
		type1cExpertCharSet.put(new Integer(260), "msuperior");
		type1cExpertCharSet.put(new Integer(261), "nsuperior");
		type1cExpertCharSet.put(new Integer(262), "osuperior");
		type1cExpertCharSet.put(new Integer(263), "rsuperior");
		type1cExpertCharSet.put(new Integer(264), "ssuperior");
		type1cExpertCharSet.put(new Integer(265), "tsuperior");
		type1cExpertCharSet.put(new Integer(266), "ff");
		type1cExpertCharSet.put(new Integer(109), "fi");
		type1cExpertCharSet.put(new Integer(110), "fl");
		type1cExpertCharSet.put(new Integer(267), "ffi");
		type1cExpertCharSet.put(new Integer(268), "ffl");
		type1cExpertCharSet.put(new Integer(269), "parenleftinferior");
		type1cExpertCharSet.put(new Integer(270), "parenrightinferior");
		type1cExpertCharSet.put(new Integer(271), "Circumflexsmall");
		type1cExpertCharSet.put(new Integer(272), "hyphensuperior");
		type1cExpertCharSet.put(new Integer(273), "Gravesmall");
		type1cExpertCharSet.put(new Integer(274), "Asmall");
		type1cExpertCharSet.put(new Integer(275), "Bsmall");
		type1cExpertCharSet.put(new Integer(276), "Csmall");
		type1cExpertCharSet.put(new Integer(277), "Dsmall");
		type1cExpertCharSet.put(new Integer(278), "Esmall");
		type1cExpertCharSet.put(new Integer(279), "Fsmall");
		type1cExpertCharSet.put(new Integer(280), "Gsmall");
		type1cExpertCharSet.put(new Integer(281), "Hsmall");
		type1cExpertCharSet.put(new Integer(282), "Ismall");
		type1cExpertCharSet.put(new Integer(283), "Jsmall");
		type1cExpertCharSet.put(new Integer(284), "Ksmall");
		type1cExpertCharSet.put(new Integer(285), "Lsmall");
		type1cExpertCharSet.put(new Integer(286), "Msmall");
		type1cExpertCharSet.put(new Integer(287), "Nsmall");
		type1cExpertCharSet.put(new Integer(288), "Osmall");
		type1cExpertCharSet.put(new Integer(289), "Psmall");
		type1cExpertCharSet.put(new Integer(290), "Qsmall");
		type1cExpertCharSet.put(new Integer(291), "Rsmall");
		type1cExpertCharSet.put(new Integer(292), "Ssmall");
		type1cExpertCharSet.put(new Integer(293), "Tsmall");
		type1cExpertCharSet.put(new Integer(294), "Usmall");
		type1cExpertCharSet.put(new Integer(295), "Vsmall");
		type1cExpertCharSet.put(new Integer(296), "Wsmall");
		type1cExpertCharSet.put(new Integer(297), "Xsmall");
		type1cExpertCharSet.put(new Integer(298), "Ysmall");
		type1cExpertCharSet.put(new Integer(299), "Zsmall");
		type1cExpertCharSet.put(new Integer(300), "colonmonetary");
		type1cExpertCharSet.put(new Integer(301), "onefitted");
		type1cExpertCharSet.put(new Integer(302), "rupiah");
		type1cExpertCharSet.put(new Integer(303), "Tildesmall");
		type1cExpertCharSet.put(new Integer(304), "exclamdownsmall");
		type1cExpertCharSet.put(new Integer(305), "centoldstyle");
		type1cExpertCharSet.put(new Integer(306), "Lslashsmall");
		type1cExpertCharSet.put(new Integer(307), "Scaronsmall");
		type1cExpertCharSet.put(new Integer(308), "Zcaronsmall");
		type1cExpertCharSet.put(new Integer(309), "Dieresissmall");
		type1cExpertCharSet.put(new Integer(310), "Brevesmall");
		type1cExpertCharSet.put(new Integer(311), "Caronsmall");
		type1cExpertCharSet.put(new Integer(312), "Dotaccentsmall");
		type1cExpertCharSet.put(new Integer(313), "Macronsmall");
		type1cExpertCharSet.put(new Integer(314), "figuredash");
		type1cExpertCharSet.put(new Integer(315), "hypheninferior");
		type1cExpertCharSet.put(new Integer(316), "Ogoneksmall");
		type1cExpertCharSet.put(new Integer(317), "Ringsmall");
		type1cExpertCharSet.put(new Integer(318), "Cedillasmall");
		type1cExpertCharSet.put(new Integer(158), "onequarter");
		type1cExpertCharSet.put(new Integer(155), "onehalf");
		type1cExpertCharSet.put(new Integer(163), "threequarters");
		type1cExpertCharSet.put(new Integer(319), "questiondownsmall");
		type1cExpertCharSet.put(new Integer(320), "oneeighth");
		type1cExpertCharSet.put(new Integer(321), "threeeighths");
		type1cExpertCharSet.put(new Integer(322), "fiveeighths");
		type1cExpertCharSet.put(new Integer(323), "seveneighths");
		type1cExpertCharSet.put(new Integer(324), "onethird");
		type1cExpertCharSet.put(new Integer(325), "twothirds");
		type1cExpertCharSet.put(new Integer(326), "zerosuperior");
		type1cExpertCharSet.put(new Integer(150), "onesuperior");
		type1cExpertCharSet.put(new Integer(164), "twosuperior");
		type1cExpertCharSet.put(new Integer(169), "threesuperior");
		type1cExpertCharSet.put(new Integer(327), "foursuperior");
		type1cExpertCharSet.put(new Integer(328), "fivesuperior");
		type1cExpertCharSet.put(new Integer(329), "sixsuperior");
		type1cExpertCharSet.put(new Integer(330), "sevensuperior");
		type1cExpertCharSet.put(new Integer(331), "eightsuperior");
		type1cExpertCharSet.put(new Integer(332), "ninesuperior");
		type1cExpertCharSet.put(new Integer(333), "zeroinferior");
		type1cExpertCharSet.put(new Integer(334), "oneinferior");
		type1cExpertCharSet.put(new Integer(335), "twoinferior");
		type1cExpertCharSet.put(new Integer(336), "threeinferior");
		type1cExpertCharSet.put(new Integer(337), "fourinferior");
		type1cExpertCharSet.put(new Integer(338), "fiveinferior");
		type1cExpertCharSet.put(new Integer(339), "sixinferior");
		type1cExpertCharSet.put(new Integer(340), "seveninferior");
		type1cExpertCharSet.put(new Integer(341), "eightinferior");
		type1cExpertCharSet.put(new Integer(342), "nineinferior");
		type1cExpertCharSet.put(new Integer(343), "centinferior");
		type1cExpertCharSet.put(new Integer(344), "dollarinferior");
		type1cExpertCharSet.put(new Integer(345), "periodinferior");
		type1cExpertCharSet.put(new Integer(346), "commainferior");
		type1cExpertCharSet.put(new Integer(347), "Agravesmall");
		type1cExpertCharSet.put(new Integer(348), "Aacutesmall");
		type1cExpertCharSet.put(new Integer(349), "Acircumflexsmall");
		type1cExpertCharSet.put(new Integer(350), "Atildesmall");
		type1cExpertCharSet.put(new Integer(351), "Adieresissmall");
		type1cExpertCharSet.put(new Integer(352), "Aringsmall");
		type1cExpertCharSet.put(new Integer(353), "AEsmall");
		type1cExpertCharSet.put(new Integer(354), "Ccedillasmall");
		type1cExpertCharSet.put(new Integer(355), "Egravesmall");
		type1cExpertCharSet.put(new Integer(356), "Eacutesmall");
		type1cExpertCharSet.put(new Integer(357), "Ecircumflexsmall");
		type1cExpertCharSet.put(new Integer(358), "Edieresissmall");
		type1cExpertCharSet.put(new Integer(359), "Igravesmall");
		type1cExpertCharSet.put(new Integer(360), "Iacutesmall");
		type1cExpertCharSet.put(new Integer(361), "Icircumflexsmall");
		type1cExpertCharSet.put(new Integer(362), "Idieresissmall");
		type1cExpertCharSet.put(new Integer(363), "Ethsmall");
		type1cExpertCharSet.put(new Integer(364), "Ntildesmall");
		type1cExpertCharSet.put(new Integer(365), "Ogravesmall");
		type1cExpertCharSet.put(new Integer(366), "Oacutesmall");
		type1cExpertCharSet.put(new Integer(367), "Ocircumflexsmall");
		type1cExpertCharSet.put(new Integer(368), "Otildesmall");
		type1cExpertCharSet.put(new Integer(369), "Odieresissmall");
		type1cExpertCharSet.put(new Integer(370), "OEsmall");
		type1cExpertCharSet.put(new Integer(371), "Oslashsmall");
		type1cExpertCharSet.put(new Integer(372), "Ugravesmall");
		type1cExpertCharSet.put(new Integer(373), "Uacutesmall");
		type1cExpertCharSet.put(new Integer(374), "Ucircumflexsmall");
		type1cExpertCharSet.put(new Integer(375), "Udieresissmall");
		type1cExpertCharSet.put(new Integer(376), "Yacutesmall");
		type1cExpertCharSet.put(new Integer(377), "Thornsmall");
		type1cExpertCharSet.put(new Integer(378), "Ydieresissmall");
	}
	
	private static final Map type1cExpertSubsetCharSet = Collections.synchronizedMap(new LinkedHashMap());
	static {
		type1cExpertSubsetCharSet.put(new Integer(0), ".notdef");
		type1cExpertSubsetCharSet.put(new Integer(1), "space");
		type1cExpertSubsetCharSet.put(new Integer(231), "dollaroldstyle");
		type1cExpertSubsetCharSet.put(new Integer(232), "dollarsuperior");
		type1cExpertSubsetCharSet.put(new Integer(235), "parenleftsuperior");
		type1cExpertSubsetCharSet.put(new Integer(236), "parenrightsuperior");
		type1cExpertSubsetCharSet.put(new Integer(237), "twodotenleader");
		type1cExpertSubsetCharSet.put(new Integer(238), "onedotenleader");
		type1cExpertSubsetCharSet.put(new Integer(13), "comma");
		type1cExpertSubsetCharSet.put(new Integer(14), "hyphen");
		type1cExpertSubsetCharSet.put(new Integer(15), "period");
		type1cExpertSubsetCharSet.put(new Integer(99), "fraction");
		type1cExpertSubsetCharSet.put(new Integer(239), "zerooldstyle");
		type1cExpertSubsetCharSet.put(new Integer(240), "oneoldstyle");
		type1cExpertSubsetCharSet.put(new Integer(241), "twooldstyle");
		type1cExpertSubsetCharSet.put(new Integer(242), "threeoldstyle");
		type1cExpertSubsetCharSet.put(new Integer(243), "fouroldstyle");
		type1cExpertSubsetCharSet.put(new Integer(244), "fiveoldstyle");
		type1cExpertSubsetCharSet.put(new Integer(245), "sixoldstyle");
		type1cExpertSubsetCharSet.put(new Integer(246), "sevenoldstyle");
		type1cExpertSubsetCharSet.put(new Integer(247), "eightoldstyle");
		type1cExpertSubsetCharSet.put(new Integer(248), "nineoldstyle");
		type1cExpertSubsetCharSet.put(new Integer(27), "colon");
		type1cExpertSubsetCharSet.put(new Integer(28), "semicolon");
		type1cExpertSubsetCharSet.put(new Integer(249), "commasuperior");
		type1cExpertSubsetCharSet.put(new Integer(250), "threequartersemdash");
		type1cExpertSubsetCharSet.put(new Integer(251), "periodsuperior");
		type1cExpertSubsetCharSet.put(new Integer(253), "asuperior");
		type1cExpertSubsetCharSet.put(new Integer(254), "bsuperior");
		type1cExpertSubsetCharSet.put(new Integer(255), "centsuperior");
		type1cExpertSubsetCharSet.put(new Integer(256), "dsuperior");
		type1cExpertSubsetCharSet.put(new Integer(257), "esuperior");
		type1cExpertSubsetCharSet.put(new Integer(258), "isuperior");
		type1cExpertSubsetCharSet.put(new Integer(259), "lsuperior");
		type1cExpertSubsetCharSet.put(new Integer(260), "msuperior");
		type1cExpertSubsetCharSet.put(new Integer(261), "nsuperior");
		type1cExpertSubsetCharSet.put(new Integer(262), "osuperior");
		type1cExpertSubsetCharSet.put(new Integer(263), "rsuperior");
		type1cExpertSubsetCharSet.put(new Integer(264), "ssuperior");
		type1cExpertSubsetCharSet.put(new Integer(265), "tsuperior");
		type1cExpertSubsetCharSet.put(new Integer(266), "ff");
		type1cExpertSubsetCharSet.put(new Integer(109), "fi");
		type1cExpertSubsetCharSet.put(new Integer(110), "fl");
		type1cExpertSubsetCharSet.put(new Integer(267), "ffi");
		type1cExpertSubsetCharSet.put(new Integer(268), "ffl");
		type1cExpertSubsetCharSet.put(new Integer(269), "parenleftinferior");
		type1cExpertSubsetCharSet.put(new Integer(270), "parenrightinferior");
		type1cExpertSubsetCharSet.put(new Integer(272), "hyphensuperior");
		type1cExpertSubsetCharSet.put(new Integer(300), "colonmonetary");
		type1cExpertSubsetCharSet.put(new Integer(301), "onefitted");
		type1cExpertSubsetCharSet.put(new Integer(302), "rupiah");
		type1cExpertSubsetCharSet.put(new Integer(305), "centoldstyle");
		type1cExpertSubsetCharSet.put(new Integer(314), "figuredash");
		type1cExpertSubsetCharSet.put(new Integer(315), "hypheninferior");
		type1cExpertSubsetCharSet.put(new Integer(158), "onequarter");
		type1cExpertSubsetCharSet.put(new Integer(155), "onehalf");
		type1cExpertSubsetCharSet.put(new Integer(163), "threequarters");
		type1cExpertSubsetCharSet.put(new Integer(320), "oneeighth");
		type1cExpertSubsetCharSet.put(new Integer(321), "threeeighths");
		type1cExpertSubsetCharSet.put(new Integer(322), "fiveeighths");
		type1cExpertSubsetCharSet.put(new Integer(323), "seveneighths");
		type1cExpertSubsetCharSet.put(new Integer(324), "onethird");
		type1cExpertSubsetCharSet.put(new Integer(325), "twothirds");
		type1cExpertSubsetCharSet.put(new Integer(326), "zerosuperior");
		type1cExpertSubsetCharSet.put(new Integer(150), "onesuperior");
		type1cExpertSubsetCharSet.put(new Integer(164), "twosuperior");
		type1cExpertSubsetCharSet.put(new Integer(169), "threesuperior");
		type1cExpertSubsetCharSet.put(new Integer(327), "foursuperior");
		type1cExpertSubsetCharSet.put(new Integer(328), "fivesuperior");
		type1cExpertSubsetCharSet.put(new Integer(329), "sixsuperior");
		type1cExpertSubsetCharSet.put(new Integer(330), "sevensuperior");
		type1cExpertSubsetCharSet.put(new Integer(331), "eightsuperior");
		type1cExpertSubsetCharSet.put(new Integer(332), "ninesuperior");
		type1cExpertSubsetCharSet.put(new Integer(333), "zeroinferior");
		type1cExpertSubsetCharSet.put(new Integer(334), "oneinferior");
		type1cExpertSubsetCharSet.put(new Integer(335), "twoinferior");
		type1cExpertSubsetCharSet.put(new Integer(336), "threeinferior");
		type1cExpertSubsetCharSet.put(new Integer(337), "fourinferior");
		type1cExpertSubsetCharSet.put(new Integer(338), "fiveinferior");
		type1cExpertSubsetCharSet.put(new Integer(339), "sixinferior");
		type1cExpertSubsetCharSet.put(new Integer(340), "seveninferior");
		type1cExpertSubsetCharSet.put(new Integer(341), "eightinferior");
		type1cExpertSubsetCharSet.put(new Integer(342), "nineinferior");
		type1cExpertSubsetCharSet.put(new Integer(343), "centinferior");
		type1cExpertSubsetCharSet.put(new Integer(344), "dollarinferior");
		type1cExpertSubsetCharSet.put(new Integer(345), "periodinferior");
		type1cExpertSubsetCharSet.put(new Integer(346), "commainferior");
	}
	
	private static final String skewChars = "AIJSVWXYfgsvwxy7()[]{}/\\!%&"; // chars that can cause trouble in italic fonts due to varying angle
	private static final String diacriticMarkerChars = (""
			+ StringUtils.getCharForName("acute")
			+ StringUtils.getCharForName("grave")
			+ StringUtils.getCharForName("breve")
			+ StringUtils.getCharForName("circumflex")
			+ StringUtils.getCharForName("cedilla")
			+ StringUtils.getCharForName("dieresis")
			+ StringUtils.getCharForName("macron")
			+ StringUtils.getCharForName("caron")
			+ StringUtils.getCharForName("ogonek")
			+ StringUtils.getCharForName("ring")
		);
	private static final String fractionChars = (""
			+ StringUtils.getCharForName("percent")
			+ StringUtils.getCharForName("onehalf")
			+ StringUtils.getCharForName("onethird")
			+ StringUtils.getCharForName("twothirds")
			+ StringUtils.getCharForName("onequarter")
			+ StringUtils.getCharForName("threequarters")
			+ StringUtils.getCharForName("oneeighth")
			+ StringUtils.getCharForName("threeeighths")
			+ StringUtils.getCharForName("fiveeighths")
			+ StringUtils.getCharForName("seveneighths")
		);
	private static final String bracketChars = (""
//			+ StringUtils.getCharForName("parenleft")
//			+ StringUtils.getCharForName("parenright")
//			+ StringUtils.getCharForName("bracketleft")
//			+ StringUtils.getCharForName("bracketright")
//			+ StringUtils.getCharForName("braceleft")
//			+ StringUtils.getCharForName("braceright")
		);
	
	/* 
	 * Multi-character Unicode mappings of somewhat frequent custom ligatures
	 * that lack a single character representation in Unicode and are not valid
	 * PostScript character names, either.
	 */
	private static final Set customLigatures = Collections.synchronizedSet(new HashSet());
	static {
		customLigatures.add("Th");
	}
	
	/*
	 * chars (mostly actually char modifiers, safe for a few punctuation marks,
	 * and ligatures) that vary too widely across fonts to prevent a font match;
	 * we have to exclude both upper and lower case V and W as well, as they
	 * vary a lot in their angle (both upper and lower case) or are round (lower
	 * case) in some italics font faces and thus won't match comparison; in
	 * addition, capital A also varies too much in italic angle, and the
	 * descending tails of capital J and Q and lower case Y and Z exhibit
	 * extreme variability as well,
	 */
	private static final String minIgnoreChars = ("%@?*&/" + diacriticMarkerChars + fractionChars + bracketChars + '\uFB00' + '\uFB01' + '\uFB02' + '\uFB03' + '\uFB04' + '0' + 'A' + 'J' + 'O' + 'Q' + 'V' + 'W' + 'k' + 'v' + 'w' + 'y' + 'z' + '\u00F8' + '\u00BC' + '\u00BD' + '\u2153'); 
//	private static final int highestPossiblePunctuationMark = 9842; // corresponds to 2672 in Unicode, the end of the Misc Symbols range (we need this for 'male' and 'female' ...)
	
	static Font getSerifFont() {
		String osName = System.getProperty("os.name");
		String fontName = (USE_FREE_FONTS ? "FreeSerif" : "Serif");
//		if (osName.matches("Win.*"))
//			fontName = "Times New Roman";
//		else if (osName.matches(".*Linux.*") || osName.matches("Mac.*")) {
//			String[] fontNames = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
//			for (int f = 0; f < fontNames.length; f++)
//				if (fontNames[f].toLowerCase().startsWith("times")) {
//					fontName = fontNames[f];
//					break;
//				}
//		}
		return Font.decode(fontName);
	}
	
	static Font getSansSerifFont() {
		String osName = System.getProperty("os.name");
		String fontName = (USE_FREE_FONTS ? "FreeSans" : "SansSerif");
//		if (osName.matches("Win.*"))
//			fontName = "Arial Unicode MS";
//		else if (osName.matches(".*Linux.*") || osName.matches("Mac.*")) {
//			String[] fontNames = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
//			for (int f = 0; f < fontNames.length; f++)
//				if (fontNames[f].toLowerCase().startsWith("arial")) {
//					fontName = fontNames[f];
//					break;
//				}
//		}
		return Font.decode(fontName);
	}
	
	static Font getMonospaceFont() {
		String osName = System.getProperty("os.name");
		String fontName = (USE_FREE_FONTS ? "FreeMono" : "Monospaced");
//		if (osName.matches("Win.*"))
//			fontName = "Arial Unicode MS";
//		else if (osName.matches(".*Linux.*") || osName.matches("Mac.*")) {
//			String[] fontNames = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
//			for (int f = 0; f < fontNames.length; f++)
//				if (fontNames[f].toLowerCase().startsWith("arial")) {
//					fontName = fontNames[f];
//					break;
//				}
//		}
		return Font.decode(fontName);
	}
	
	//	font preference switch, mainly for testing Liberation Fonts against default system fonts
	private static final boolean USE_FREE_FONTS = true;
	
	/* make sure we have the fonts we need */
	static {
		if (USE_FREE_FONTS)
			ImFontUtils.loadFreeFonts();
	}
}