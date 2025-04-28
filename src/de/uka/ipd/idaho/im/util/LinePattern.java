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

import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import de.uka.ipd.idaho.easyIO.streams.PeekReader;
import de.uka.ipd.idaho.gamta.Gamta;
import de.uka.ipd.idaho.im.ImPage;
import de.uka.ipd.idaho.im.ImRegion;
import de.uka.ipd.idaho.im.ImWord;
import de.uka.ipd.idaho.stringUtils.StringUtils;

/**
 * Utility class for matching individual lines in an Image Markup document,
 * considering font size, text orientation, and emphases alongside a regular
 * expression pattern matching against the normalized line text.
 * 
 * @author sautter
 */
public class LinePattern {
	
	/** the text orientation for the parent paragraphs of matching lines; left
	 * (L), right (R), centered (C), or justified (J), concatenated for
	 * alternatives; null indicates a wildcard; denote as
	 * <code>{PO:&lt;textOrientations&gt;}</code> */
	public final String paragraphOrientation;
	
	/** the emphasizing font properties required on the start of matching lines;
	 * bold (B), italics (I), or all-caps (C), concatenated to require multiple;
	 * null indicates a wildcard; denote as
	 * <code>{SFP:&lt;startFontProperties&gt;}</code> */
	public final String startFontProperties;
	
	/** the emphasizing font properties required on the whole of matching lines;
	 * bold (B), italics (I), or all-caps (C), concatenated to require multiple;
	 * null indicates a wildcard; denote as
	 * <code>{FP:&lt;fontProperties&gt;}</code> */
	public final String fontProperties;
	
	/** the minimum (main) font of matching lines, with some tolerance for
	 * smaller characters (to accommodate superscripts and subscripts); denote
	 * as <code>{FS:&lt;minFontSize&gt;-&lt;maxFontSize&gt;}</code> together
	 * with maximum font size*/
	public final int minFontSize;
	
	/** the maximum font of matching lines, matched strictly on all characters
	 * that extend above the x-height; denote as
	 * <code>{FS:&lt;minFontSize&gt;-&lt;maxFontSize&gt;}</code> together with
	 * minimum font size*/
	public final int maxFontSize;
	
	/** the regular expression pattern to match (normalized) line text against;
	 * null indicates a wildcard */
	public final Pattern pattern;
	
	/** map with custom additional parameters, to be used for extensions by sub
	 * classes */
	private Properties parameters = new Properties();
	
	private static final boolean DEBUG_MATCH = false;
	
	/** Constructor
	 * @param pattern the regular expression pattern to use
	 * @param parameters a Properties object holding the other match parameters
	 * @throws IllegalArgumentException if a match parameter is invalid
	 */
	protected LinePattern(String pattern, Properties parameters) throws IllegalArgumentException {
		this.pattern = ((pattern == null) ? null : Pattern.compile(pattern));
		this.parameters.putAll(parameters);
		
		String po = this.parameters.getProperty("PO");
		if (po != null)
			po = po.trim();
		if ((po != null) && (po.replaceAll("[LRCJ]", "").length() != 0))
			throw new IllegalArgumentException("Invalid text orientation '" + po + "', use 'L', 'R', 'C', and 'J' only");
		this.paragraphOrientation = po;
		
		String sfp = this.parameters.getProperty("SFP");
		if (sfp != null)
			sfp = sfp.trim();
		if ((sfp != null) && (sfp.replaceAll("[BIC]", "").length() != 0))
			throw new IllegalArgumentException("Invalid start font emphasis properties '" + sfp + "', use 'B', 'I', and 'C' only");
		this.startFontProperties = sfp;
		
		String fp = this.parameters.getProperty("FP");
		if (fp != null)
			fp = fp.trim();
		if ((fp != null) && (fp.replaceAll("[BIC]", "").length() != 0))
			throw new IllegalArgumentException("Invalid font emphasis properties '" + fp + "', use 'B', 'I', and 'C' only");
		this.fontProperties = fp;
		
		String fs = this.parameters.getProperty("FS");
		if (fs != null)
			fs = fs.trim();
		if (fs == null) {
			this.minFontSize = 0;
			this.maxFontSize = 72;
		}
		else if (fs.indexOf('-') == -1) {
			this.minFontSize = Integer.parseInt(fs);
			this.maxFontSize = this.minFontSize;
		}
		else {
			int fsSplit = fs.indexOf('-');
			this.minFontSize = Integer.parseInt(fs.substring(0, fsSplit).trim());
			this.maxFontSize = Integer.parseInt(fs.substring(fsSplit + "-".length()).trim());
		}
		if ((this.minFontSize < 0) || (this.maxFontSize < this.minFontSize))
			throw new IllegalArgumentException("Invalid font size '" + fs + "', specify as '<min>-<max>'");
	}
	
	/**
	 * @param paragraphOrientation
	 * @param startFontProperties
	 * @param fontProperties
	 * @param minFontSize
	 * @param maxFontSize
	 * @param pattern
	 * @throws IllegalArgumentException if a match parameter is invalid
	 */
	public LinePattern(String po, String sfp, String fp, int minFs, int maxFs, String pattern) throws IllegalArgumentException {
		if (po != null)
			po = po.trim();
		if ((po != null) && (po.replaceAll("[LRCJ]", "").length() != 0))
			throw new IllegalArgumentException("Invalid text orientation '" + po + "', use 'L', 'R', 'C', and 'J' only");
		if (po != null)
			this.parameters.setProperty("PO", po);
		this.paragraphOrientation = po;
		
		if (sfp != null)
			sfp = sfp.trim();
		if ((sfp != null) && (sfp.replaceAll("[BIC]", "").length() != 0))
			throw new IllegalArgumentException("Invalid start font emphasis properties '" + sfp + "', use 'B', 'I', and 'C' only");
		if (sfp != null)
			this.parameters.setProperty("SFP", sfp);
		this.startFontProperties = sfp;
		
		if (fp != null)
			fp = fp.trim();
		if ((fp != null) && (fp.replaceAll("[BIC]", "").length() != 0))
			throw new IllegalArgumentException("Invalid font emphasis properties '" + fp + "', use 'B', 'I', and 'C' only");
		if (fp != null)
			this.parameters.setProperty("FP", fp);
		this.fontProperties = fp;
		
		if ((minFs < 0) || (maxFs < minFs))
			throw new IllegalArgumentException("Invalid font size range " + minFs + "-" + maxFs + ", specify as <min> and <max>");
		if ((minFs <= 0) && (maxFs >= 72)) {}
		else if (minFs == maxFs)
			this.parameters.setProperty("FS", ("" + minFs));
		else this.parameters.setProperty("FS", (minFs + "-" + maxFs));
		this.minFontSize = minFs;
		this.maxFontSize = maxFs;
		
		this.pattern = ((pattern == null) ? null : Pattern.compile(pattern));
	}
	
	/**
	 * Retrieve a parameter from the encapsulated properties.
	 * @param name the name of the parameter
	 * @return the parameter value
	 */
	public String getParameter(String name) {
		return this.parameters.getProperty(name);
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		if (this.toString == null) {
			StringBuffer ts = new StringBuffer();
			//	TODOnot maybe order properties alphabetically to in-line sub class extensions
			//	==> and the DON'T, as with this implementation sub classes can pre-pend their own properties
			if (this.paragraphOrientation != null)
				ts.append("{PO:" + this.paragraphOrientation + "}");
			if (this.startFontProperties != null)
				ts.append("{SFP:" + this.startFontProperties + "}");
			if (this.fontProperties != null)
				ts.append("{FP:" + this.fontProperties + "}");
			if ((this.minFontSize == 0) && (this.maxFontSize == 72)) {}
			else if (this.minFontSize == this.maxFontSize)
				ts.append("{FS:" + this.minFontSize + "}");
			else ts.append("{FS:" + this.minFontSize + "-" + this.maxFontSize + "}");
			if (this.pattern != null)
				ts.append(this.pattern.toString());
			this.toString = ts.toString();
		}
		return this.toString;
	}
	private String toString = null;
	
	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode() {
		return this.toString().hashCode();
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object obj) {
		return ((obj instanceof LinePattern) && this.toString().equals(obj.toString()));
	}
	
	/**
	 * Extract all matching lines from a given page in an Image Markup document.
	 * @param page the page whose lines to test
	 * @return an array holding the matching lines
	 */
	public ImRegion[] getMatches(ImPage page) {
		return this.getMatches(page, (DEBUG_MATCH ? System.out : null));
	}
	
	/**
	 * Extract all matching lines from a given page in an Image Markup document.
	 * @param page the page whose lines to test
	 * @param log a print stream to write detail matching information to
	 * @return an array holding the matching lines
	 */
	public ImRegion[] getMatches(ImPage page, PrintStream log) {
		
		//	work paragraph by paragraph
		ArrayList matchLines = new ArrayList();
		ImRegion[] paragraphs = page.getRegions(ImRegion.PARAGRAPH_TYPE);
		Arrays.sort(paragraphs, ImUtils.topDownOrder);
		for (int p = 0; p < paragraphs.length; p++) {
			if (log != null) log.println(" - checking paragraph at " + paragraphs[p].bounds);
			
			//	check paragraph orientation first (no need to even get lines on mismatch)
			if (!this.matchesParagraphOrientation((String) paragraphs[p].getAttribute(ImRegion.TEXT_ORIENTATION_ATTRIBUTE))) {
				if (log != null) log.println(" ==> text orientation mis-match");
				continue;
			}
			else if (log != null) log.println(" - text orientation match");
			
			//	get matching lines
//			ImRegion[] pMatchLines = this.getMatches(paragraphs[p].getRegions(ImRegion.LINE_ANNOTATION_TYPE, true), null /* no need to check paragraph time and again */, log);
//			matchLines.addAll(Arrays.asList(pMatchLines));
			this.addMatches(paragraphs[p].getRegions(ImRegion.LINE_ANNOTATION_TYPE, true), matchLines, log);
		}
		
		//	finally ...
		return ((ImRegion[]) matchLines.toArray(new ImRegion[matchLines.size()]));
	}
	
	/**
	 * Extract all matching lines from a given paragraph in an Image Markup
	 * document.
	 * @param paragraph the paragraph whose lines to test
	 * @return an array holding the matching lines
	 */
	public ImRegion[] getMatches(ImRegion paragraph) {
		return this.getMatches(paragraph, (DEBUG_MATCH ? System.out : null));
	}
	
	/**
	 * Extract all matching lines from a given paragraph in an Image Markup
	 * document.
	 * @param paragraph the paragraph whose lines to test
	 * @param log a print stream to write detail matching information to
	 * @return an array holding the matching lines
	 */
	public ImRegion[] getMatches(ImRegion paragraph, PrintStream log) {
		
		//	check paragraph orientation first (no need to even get lines on mismatch)
		if (!this.matchesParagraphOrientation((String) paragraph.getAttribute(ImRegion.TEXT_ORIENTATION_ATTRIBUTE))) {
			if (log != null) log.println(" ==> text orientation mis-match");
			return new ImRegion[0];
		}
		else if (log != null) log.println(" - text orientation match");
		
		//	get matching lines
		return this.getMatches(paragraph.getRegions(ImRegion.LINE_ANNOTATION_TYPE, true), null /* no need to check paragraph again */, log);
	}
	
	/**
	 * Extract all matching lines from a given array, in the context of their
	 * parent paragraph. If the argument paragraph is null, paragraph
	 * orientation will be ignored; the same applies if the paragraph comes
	 * without the 'orientation' attribute.
	 * @param lines the lines to test
	 * @param paragraph the parent paragraph of the lines
	 * @return an array holding the matching lines
	 */
	public ImRegion[] getMatches(ImRegion[] lines, ImRegion paragraph) {
		return this.getMatches(lines, paragraph, (DEBUG_MATCH ? System.out : null));
	}
	
	/**
	 * Extract all matching lines from a given array, in the context of their
	 * parent paragraph. If the argument paragraph is null, paragraph
	 * orientation will be ignored; the same applies if the paragraph comes
	 * without the 'orientation' attribute.
	 * @param lines the lines to test
	 * @param paragraph the parent paragraph of the lines
	 * @param log a print stream to write detail matching information to
	 * @return an array holding the matching lines
	 */
	public ImRegion[] getMatches(ImRegion[] lines, ImRegion paragraph, PrintStream log) {
		
		//	check paragraph orientation first (no need to get words on mismatch)
//		if ((paragraph != null) && !this.matchesParagraphOrientation((String) paragraph.getAttribute(ImRegion.TEXT_ORIENTATION_ATTRIBUTE)))
//			return new ImRegion[0];
		if (paragraph != null) {
			if (!this.matchesParagraphOrientation((String) paragraph.getAttribute(ImRegion.TEXT_ORIENTATION_ATTRIBUTE))) {
				if (log != null) log.println(" ==> text orientation mis-match");
				return new ImRegion[0];
			}
			else if (log != null) log.println(" - text orientation match");
		}
		
		//	filter lines
		ArrayList matchLines = new ArrayList();
//		Arrays.sort(lines, ImUtils.topDownOrder);
//		for (int l = 0; l < lines.length; l++) {
//			if (this.matches(lines[l], null /* no need to check paragraph time and again */, log))
//				matchLines.add(lines[l]);
//		}
		this.addMatches(lines, matchLines, log);
		return ((ImRegion[]) matchLines.toArray(new ImRegion[matchLines.size()]));
	}
	private void addMatches(ImRegion[] lines, ArrayList matchLines, PrintStream log) {
		Arrays.sort(lines, ImUtils.topDownOrder);
		for (int l = 0; l < lines.length; l++) {
			if (log != null) {
				ImWord[] lineWords = lines[l].getWords();
				Arrays.sort(lineWords, ImUtils.textStreamOrder);
				log.println("   - checking line at " + lines[l].pageId + "." + lines[l].bounds + ": " + ImUtils.getString(lineWords, true));
			}
			if (this.matches(lines[l], null /* no need to check paragraph time and again */, log))
				matchLines.add(lines[l]);
		}
	}
	
	/**
	 * Match a line against the pattern, in context of its parent paragraph. If
	 * the argument paragraph is null, paragraph orientation will be ignored;
	 * the same applies if the paragraph comes without the 'orientation'
	 * attribute.
	 * @param line the line to match
	 * @param paragraph the parent paragraph of the line
	 * @return true on a match
	 */
	public boolean matches(ImRegion line) {
		return this.matches(line, (DEBUG_MATCH ? System.out : null));
	}
	
	/**
	 * Match a line against the pattern, in context of its parent paragraph. If
	 * the argument paragraph is null, paragraph orientation will be ignored;
	 * the same applies if the paragraph comes without the 'orientation'
	 * attribute.
	 * @param line the line to match
	 * @param paragraph the parent paragraph of the line
	 * @param log a print stream to write detail matching information to
	 * @return true on a match
	 */
	public boolean matches(ImRegion line, PrintStream log) {
		
		//	find parent paragraph
		ImPage page = line.getPage();
		if (page == null)
			page = line.getDocument().getPage(line.pageId);
		ImRegion paragraph;
		if (page == null)
			paragraph = null;
		else {
			ImRegion[] paragraphs = page.getRegionsIncluding(ImRegion.PARAGRAPH_TYPE, line.bounds, true);
			paragraph = ((paragraphs.length == 0) ? null : paragraphs[0]);
		}
		
		//	match in paragraph context
		return this.matches(line, paragraph, log);
	}
	
	/**
	 * Match a line against the pattern, in context of its parent paragraph. If
	 * the argument paragraph is null, paragraph orientation will be ignored;
	 * the same applies if the paragraph comes without the 'orientation'
	 * attribute.
	 * @param line the line to match
	 * @param paragraph the parent paragraph of the line
	 * @return true on a match
	 */
	public boolean matches(ImRegion line, ImRegion paragraph) {
		return this.matches(line, paragraph, (DEBUG_MATCH ? System.out : null));
	}
	
	/**
	 * Match a line against the pattern, in context of its parent paragraph. If
	 * the argument paragraph is null, paragraph orientation will be ignored;
	 * the same applies if the paragraph comes without the 'orientation'
	 * attribute.
	 * @param line the line to match
	 * @param paragraph the parent paragraph of the line
	 * @param log a print stream to write detail matching information to
	 * @return true on a match
	 */
	public boolean matches(ImRegion line, ImRegion paragraph, PrintStream log) {
		
		//	check paragraph orientation first (no need to get words on mismatch)
//		if ((paragraph != null) && !this.matchesParagraphOrientation((String) paragraph.getAttribute(ImRegion.TEXT_ORIENTATION_ATTRIBUTE)))
//			return false;
		if (paragraph != null) {
			if (!this.matchesParagraphOrientation((String) paragraph.getAttribute(ImRegion.TEXT_ORIENTATION_ATTRIBUTE))) {
				if (log != null) log.println(" ==> text orientation mis-match");
				return false;
			}
			else if (log != null) log.println(" - text orientation match");
		}
		
		//	match line words
		ImWord[] lineWords = line.getWords();
		Arrays.sort(lineWords, ImUtils.textStreamOrder);
//		return (true
//			&& this.matchesStartFontProperties(lineWords, log)
//			&& this.matchesFontProperties(lineWords, log)
//			&& this.matchesFontSize(lineWords, log)
//			&& this.matchesString(lineWords, log)
//		);
		if (true
			&& this.matchesStartFontProperties(lineWords, log)
			&& this.matchesFontProperties(lineWords, log)
			&& this.matchesFontSize(lineWords, log)
			&& this.matchesString(lineWords, log)
		) {
			if (log != null) log.println("   ==> match");
			return true;
		}
		else return false;
	}
	
	/**
	 * Check if a paragraph orientation matches the pattern.
	 * @param po the paragraph orientation to test
	 * @return true on a match
	 */
	public boolean matchesParagraphOrientation(String po) {
		if (this.paragraphOrientation == null)
			return true; // wildcard match
		else if (po == null)
			return true; // be graceful
		else if (ImRegion.TEXT_ORIENTATION_JUSTIFIED.equals(po))
			return (this.paragraphOrientation.indexOf('J') != -1);
		else if (ImRegion.TEXT_ORIENTATION_LEFT.equals(po))
			return (this.paragraphOrientation.indexOf('L') != -1);
		else if (ImRegion.TEXT_ORIENTATION_RIGHT.equals(po))
			return (this.paragraphOrientation.indexOf('R') != -1);
		else if (ImRegion.TEXT_ORIENTATION_CENTERED.equals(po))
			return (this.paragraphOrientation.indexOf('C') != -1);
		else return ((po.length() == 1) && (this.paragraphOrientation.indexOf(po) != -1)); // also matching single-letter indicators proper
	}
	
	/**
	 * Check if the font properties of the starting words in a line match the
	 * pattern.
	 * @param lineWords words to test
	 * @return true on a match
	 */
	public boolean matchesStartFontProperties(ImWord[] lineWords) {
		return this.matchesStartFontProperties(lineWords, (DEBUG_MATCH ? System.out : null));
	}
	
	/**
	 * Check if the font properties of the starting words in a line match the
	 * pattern.
	 * @param lineWords words to test
	 * @param log a print stream to write detail matching information to
	 * @return true on a match
	 */
	public boolean matchesStartFontProperties(ImWord[] lineWords, PrintStream log) {
//		return matchesFontProperties(lineWords, this.fontProperties, true, log);
		if (this.startFontProperties == null)
			return true; // wildcard match
		else if (matchesFontProperties(lineWords, this.startFontProperties, true, log)) {
			if (log != null) log.println("   - start font properties match");
			return true;
		}
		else {
			if (log != null) log.println("   ==> start font properties mis-match");
			return false;
		}
	}
	
	/**
	 * Check if the font properties of the words in a line match the pattern.
	 * @param lineWords words to test
	 * @return true on a match
	 */
	public boolean matchesFontProperties(ImWord[] lineWords) {
		return this.matchesFontProperties(lineWords, (DEBUG_MATCH ? System.out : null));
	}
	
	/**
	 * Check if the font properties of the words in a line match the pattern.
	 * @param lineWords words to test
	 * @param log a print stream to write detail matching information to
	 * @return true on a match
	 */
	public boolean matchesFontProperties(ImWord[] lineWords, PrintStream log) {
//		return matchesFontProperties(lineWords, this.fontProperties, false, log);
		if (this.fontProperties == null)
			return true; // wildcard match
		else if (matchesFontProperties(lineWords, this.fontProperties, true, log)) {
			if (log != null) log.println("   - font properties match");
			return true;
		}
		else {
			if (log != null) log.println("   ==> font properties mis-match");
			return false;
		}
	}
	
	private static boolean matchesFontProperties(ImWord[] lineWords, String fps, boolean startOnly, PrintStream log) {
		if (fps == null)
			return true; // wildcard match
		
		//	check font properties
		boolean bold = (fps.indexOf('B') != -1);
		boolean italics = (fps.indexOf('I') != -1);
		boolean allCaps = (fps.indexOf('C') != -1);
		for (int w = 0; w < lineWords.length; w++) {
			String lineWordString = lineWords[w].getString();
			if (bold && !lineWords[w].hasAttribute(ImWord.BOLD_ATTRIBUTE)) {
				if (penalizeNonBold(lineWordString)) {
					if (log != null) log.println("   ==> not bold at '" + lineWordString + "'");
					return false;
				}
				else if (log != null) log.println("   - not bold at '" + lineWordString + "', but tolerated");
			}
			if (italics && !lineWords[w].hasAttribute(ImWord.ITALICS_ATTRIBUTE)) {
				if (penalizeNonItalics(lineWordString)) {
					if (log != null) log.println("   ==> not in italics at '" + lineWordString + "'");
					return false;
				}
				else if (log != null) log.println("   - not in italics at '" + lineWordString + "', but tolerated");
			}
			if (lineWordString == null)
				continue;
			if (!Gamta.isWord(lineWordString))
				continue; // all-caps only makes sense on actual words ...
			if (allCaps && !lineWordString.equals(lineWordString.toUpperCase())) {
				if (log != null) log.println("   ==> not all-caps at '" + lineWordString + "'");
				return false;
			}
			if (startOnly)
				break; // reaching first actual word is enough for start match
		}
		
		//	no counter indications found
		return true;
	}
	
	/* string of punctuation marks whose font size can differ a little from the
	 * surrounding text due to sloppy layout or even adjustment */
	private static final String fontSizeVariablePunctuationMarks = (",.;:^°\"'=+\u00B1" + StringUtils.DASHES);
	
	/* string of punctuation marks whose italicisation can differ from the
	 * surrounding text due to sloppy layout or even adjustment */
	private static final String fontStyleVariablePunctuationMarks = (",.°'-\u00B1" + StringUtils.DASHES);
	
	private static boolean penalizeNonBold(String lineWordString) {
		if (lineWordString == null)
			return false;
		if (lineWordString.length() > 1)
			return true;
		return (fontSizeVariablePunctuationMarks.indexOf(lineWordString.charAt(0)) == -1);
	}
	
	private static boolean penalizeNonItalics(String lineWordString) {
		if (lineWordString == null)
			return false;
		if (lineWordString.length() > 1)
			return true;
		return (fontStyleVariablePunctuationMarks.indexOf(lineWordString.charAt(0)) == -1);
	}
	
	/**
	 * Check if the font sizes of the words in a line match the pattern.
	 * @param lineWords words to test
	 * @return true on a match
	 */
	public boolean matchesFontSize(ImWord[] lineWords) {
		return this.matchesFontSize(lineWords, (DEBUG_MATCH ? System.out : null));
	}
	
	/**
	 * Check if the font sizes of the words in a line match the pattern.
	 * @param lineWords words to test
	 * @param log a print stream to write detail matching information to
	 * @return true on a match
	 */
	public boolean matchesFontSize(ImWord[] lineWords, PrintStream log) {
		if ((this.minFontSize == 0) && (this.maxFontSize == 72))
			return true; // wildcard match
		
		//	collect word font sizes (matching as well as below-minimum), and count absence of font size as well
		int matchFontSizeWordCount = 0;
		int matchFontSizeWordCharCount = 0;
		int lowFontSizeWordCount = 0;
		int lowFontSizeWordCharCount = 0;
		int noFontSizeWordCount = 0;
		int noFontSizeWordCharCount = 0;
		for (int w = 0; w < lineWords.length; w++) try {
			int wfs = lineWords[w].getFontSize();
			
			//	no font size at all
			if (wfs == -1) {
				noFontSizeWordCount++;
				noFontSizeWordCharCount += lineWords[w].getString().length();
			}
			
			//	this one's good (with some upward tolerance for certain punctuation marks)
			else if (isFontSizeMatch(lineWords[w], wfs, this.minFontSize, this.maxFontSize, log)) {
				matchFontSizeWordCount++;
				matchFontSizeWordCharCount += lineWords[w].getString().length();
			}
			
			//	this one's too large, we're not having that
			else if (this.maxFontSize < wfs) {
				if (log != null) log.println("   ==> too large words (font size " + wfs + ")");
				return false;
			}
			
			//	this one's too small, allow this to a certain degree (super- and subscripts)
			else if (wfs < this.minFontSize) {
				lowFontSizeWordCount++;
				lowFontSizeWordCharCount += lineWords[w].getString().length();
			}
		}
		catch (NumberFormatException nfe) {
			noFontSizeWordCount++;
			noFontSizeWordCharCount += lineWords[w].getString().length();
		}
		
		//	declare mismatch if more than one third of words or characters have no font size at all
		//	TODO verify thresholds, might be too lenient
		if ((noFontSizeWordCount * 3) > matchFontSizeWordCount) {
			if (log != null) log.println("   ==> too many words without font size (" + noFontSizeWordCount + " against " + matchFontSizeWordCount + " with)");
			return false;
		}
		if ((noFontSizeWordCharCount * 3) > matchFontSizeWordCharCount) {
			if (log != null) log.println("   ==> too many characters without font size (" + noFontSizeWordCharCount + " against " + matchFontSizeWordCharCount + " with)");
			return false;
		}
		
		//	declare mismatch if more than one fifth of words or one tenth of characters have too small font size
		//	TODO verify thresholds, might be too lenient
		if ((lowFontSizeWordCount * 5) > matchFontSizeWordCount) {
			if (log != null) log.println("   ==> too many words below minimum font size (" + lowFontSizeWordCount + " against " + matchFontSizeWordCount + " above)");
			return false;
		}
		if ((lowFontSizeWordCharCount * 10) > matchFontSizeWordCharCount) {
			if (log != null) log.println("   ==> too many characters below minimum font size (" + lowFontSizeWordCharCount + " against " + matchFontSizeWordCharCount + " above)");
			return false;
		}
		
		//	no red flags on this one ...
		if (log != null) log.println("   - font size match");
		return true;
	}
	
	private static boolean isFontSizeMatch(ImWord word, int wordFontSize, int minFontSize, int maxFontSize, PrintStream log) {
		if (wordFontSize < 0)
			return true;
		int fontSizeTolerance = (((word.getString().length() > 1) || (fontSizeVariablePunctuationMarks.indexOf(word.getString()) == -1)) ? 0 : 1);
		if ((wordFontSize + fontSizeTolerance) < minFontSize) {
			boolean isPossibleSuperOrSubScript = false;
			if ((word.getString().length() == 1) && Character.isLetterOrDigit(word.getString().charAt(0)))
				isPossibleSuperOrSubScript = true; // index letter or digit
			else if (" st nd rd th ".indexOf(word.getString()) != -1)
				isPossibleSuperOrSubScript = true; // English ordinal number suffixes (all other Latin based languages use single letter ones or none at all)
			if (isPossibleSuperOrSubScript) {
				if (log != null) log.println("   ==> font smaller than " + minFontSize + " at " + wordFontSize + " tolerated for " + word.getString() + " as potential super- or subscript");
			}
			else {
				if (log != null) log.println("   ==> font smaller than " + minFontSize + " at " + wordFontSize + " for " + word.getString());
				return false;
			}
		}
		if (maxFontSize < (wordFontSize - fontSizeTolerance)) {
			if (log != null) log.println("   ==> font larger than " + maxFontSize + " at " + wordFontSize + " for " + word.getString());
			return false;
		}
		return true;
	}
	
	/**
	 * Check if the text represented by the words in a line matches the pattern.
	 * @param lineWords words to test
	 * @return true on a match
	 */
	public boolean matchesString(ImWord[] lineWords) {
		return this.matchesString(lineWords, (DEBUG_MATCH ? System.out : null));
	}
	
	/**
	 * Check if the text represented by the words in a line matches the pattern.
	 * @param lineWords words to test
	 * @param log a print stream to write detail matching information to
	 * @return true on a match
	 */
	public boolean matchesString(ImWord[] lineWords, PrintStream log) {
		if (this.pattern == null)
			return true; // wildcard match
		return this.matchesString(ImUtils.getString(lineWords, true), log);
	}
	
	/**
	 * Check if the text represented by the words in a line matches the pattern.
	 * @param lineWords words to test
	 * @return true on a match
	 */
	public boolean matchesString(String lineString) {
		return this.matchesString(lineString, (DEBUG_MATCH ? System.out : null));
	}
	
	/**
	 * Check if the text represented by the words in a line matches the pattern.
	 * @param lineWords words to test
	 * @param log a print stream to write detail matching information to
	 * @return true on a match
	 */
	public boolean matchesString(String lineString, PrintStream log) {
//		if (this.pattern == null)
//			return true; // wildcard match
//		lineString = StringUtils.normalizeString(lineString);
//		return this.pattern.matcher(lineString).matches();
		if (this.pattern == null)
			return true; // wildcard match
		else if (this.pattern.matcher(StringUtils.normalizeString(lineString)).matches()) {
			if (log != null)
				log.println("   - pattern match");
			return true;
		}
		else {
			if (log != null)
				log.println("   ==> pattern mis-match on '" + lineString + "'");
			return false;
		}
	}
	
	/**
	 * Parse a line pattern from its string representation.
	 * @param pattern the pattern string to parse
	 * @return the parsed line pattern
	 * @throws PatternSyntaxException if the argument pattern string is invalid
	 */
	public static LinePattern parsePattern(String pattern) throws PatternSyntaxException {
		try {
			LinePatternReader lpr = new LinePatternReader(new StringReader(pattern), pattern.length());
			lpr.skipSpace();
			Properties parameters = new Properties();
			StringBuffer psb = new StringBuffer();
			int parameterEnd = 0;
			while (lpr.peek() != -1) {
				if (lpr.peek() == '{') {
					lpr.read(); // consume opening curly bracket
					cropParameter(pattern, lpr, parameters); // read parameter
				}
				else {
					parameterEnd = lpr.read;
					while (lpr.peek() != -1)
						psb.append((char) lpr.read()); // read actual pattern
					break;
				}
			}
			String ps = psb.toString().trim();
			try {
				return new LinePattern(((ps.length() == 0) ? null : ps), parameters);
			}
			catch (PatternSyntaxException pse) {
				throw new PatternSyntaxException(pse.getDescription(), pattern, (parameterEnd + pse.getIndex()));
			}
		}
		catch (IOException ioe) {
			//	cannot happen with string reader, but Java don't know
			return null;
		}
	}
	
	private static void cropParameter(String pattern, LinePatternReader lpr, Properties parameters) throws IOException, PatternSyntaxException {
		
		//	read parameter name
		lpr.skipSpace();
		StringBuffer pn = new StringBuffer();
		while ((lpr.peek() != -1) && (lpr.peek() != ':') && (lpr.peek() > 32))
			pn.append((char) lpr.read());
		if (pn.length() == 0)
			throw new PatternSyntaxException("Expected parameter name before ':'", pattern, lpr.read);
		lpr.skipSpace();
		if (lpr.peek() != ':')
			throw new PatternSyntaxException("Expected ':' after parameter name", pattern, lpr.read);
		lpr.read(); // consume colon
		
		//	read parameter value
		lpr.skipSpace();
		StringBuffer pv = new StringBuffer();
		while ((lpr.peek() != -1) && (lpr.peek() != '}'))
			pv.append((char) lpr.read());
		if (pv.length() == 0)
			throw new PatternSyntaxException("Expected parameter value before '}'", pattern, lpr.read);
		if (lpr.peek() != '}')
			throw new PatternSyntaxException("Expected '}' after parameter value", pattern, lpr.read);
		lpr.read(); // consume closing curly bracket
		lpr.skipSpace();
		
		//	store parameter value
		parameters.setProperty(pn.toString(), pv.toString());
	}
	
	private static class LinePatternReader extends PeekReader {
		int read;
		LinePatternReader(Reader in, int lookahead) throws IOException {
			super(in, lookahead);
		}
		public int read() throws IOException {
			int r = super.read();
			if (r != -1)
				this.read++;
			return r;
		}
		public int read(char[] cbuf, int off, int len) throws IOException {
			int r = super.read(cbuf, off, len);
			if (r != -1)
				this.read += r;
			return r;
		}
	}
	
	
	/* TODO
Create LinePattern static inner class in PageAnalysis
- maybe also add find() method ...
- ... and also use this for captions and footnotes (unify latter two in any case)
- maybe also use for headings (if most likely without the regular expression in most cases)
  ==> denotes more compactly (see below) ...
  ==> ... but renders visualization of individual aspects (font size, etc.) harder
    ==> only use under the hood, but not in style template editor

In heading style inference:
- collect line patterns for all main text lines in document ...
- ... augmented by space above and below
==> also provide equals() and hashCode() methods (most likely based on serialization) to support use in CountingSet
  ==> cache serialization internally to prevent frequent recomputation
  ==> make damn sure to serialize "flags" in well-defined way (most likely in alphabetical order) in toString() method
- provide subsumes() method (at least in observed heading styles) ...
  - inclusion of font size ranges
  - inclusion of start and full-line font properties (fewer properties required)
  - string equality of any regular expression pattern
- ... and use that for aggregation
==> should produce pretty nice set of line patterns
- also check for multi-line matches, and aggregate above and below space for those ...
- ... reducing match count for patterns by any additional lines
- assess sequence of line styles in reading order, as well as distances:
  - higher ranking headings precede lower ranking ones
  - downward steps in heading hierarchy often smaller in distance than upward ones
- higher level headings come with more (average) space around them
- higher level headings more prominent than lower level ones (font size, emphasis)
- also use numbering if present:
  - hierarchical numbering (1, 1.1, 1.2, 2, 2.1, 2.2, etc.) pretty distinctive
  - fewer repetitions in numbers indicate higher level with non-hierarchical numbering
  ==> also collect starting numbers
- lower level headings more frequent than higher level ones
  - be careful about lowest level, though, might occur only very rarely
    ==> also consider distribution over document
==> maybe score all possible hierarchical orders of all potential heading styles
  - goes by factorial, yes, incurring risk of runaway ...
  - ... but then, there should not be that many styles ...
  - ... and scoring should be fast enough to handle the combinations based upon order-
  - maybe limit number of heading levels to 4 or 5
- maybe also learn key words frequent at higher or lower levels ...
-  ... and pre-seed top level with "Bibliography", "Literature Cited", etc.
  ==> should be possible to populate from current IMF corpus	 */
	
	
	//	TEST ONLY
	public static void main(String[] args) throws Exception {
		String p;
//		p = "[A-Z][a-z]+.*";
//		p = "{PO:C}[A-Z][a-z]+.*";
//		p = "{PO:B}[A-Z][a-z]+.*"; // getting expected exception
//		p = "{PO:}[A-Z][a-z]+.*"; // getting expected exception
//		p = "{PO:C}{FS:9-10}[A-Z][a-z]+.*";
//		p = "{PO:C}{FS:10}[A-Z][a-z]+.*";
//		p = "{FS:10}{PO:C}[A-Z][a-z]+.*";
		p = "{FS:10}{PO:C}{FP:BC}";
		
		System.out.println(p);
		LinePattern lp = LinePattern.parsePattern(p);
		System.out.println(lp.toString());
	}
}