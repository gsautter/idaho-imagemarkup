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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import de.uka.ipd.idaho.gamta.Attributed;
import de.uka.ipd.idaho.gamta.util.DocumentStyle;
import de.uka.ipd.idaho.gamta.util.imaging.BoundingBox;
import de.uka.ipd.idaho.im.ImDocument;
import de.uka.ipd.idaho.im.ImPage;
import de.uka.ipd.idaho.im.ImWord;
import de.uka.ipd.idaho.stringUtils.StringUtils;

/**
 * Instances of this class describe the style of a particular type or class of
 * Image Markup documents, e.g. articles from a specific journal, by means of
 * any kind of parameters. This can range from the font styles used for
 * headings of individual levels to the format of named entities like dates to
 * font styles and position information of bibliographic attributes in the
 * article header to the styles of bibliographic references, and so forth.<br>
 * Parameters that represent positional information or measurements (e.g. the
 * location of page numbers, or the width of a column margin) naturally depend
 * on page image resolution. It is recommended to provide such parameter values
 * for a resolution of 72 DPI, the default typographical Point unit.<br>
 * This class simply wraps a generic <code>DocumentStyle</code> to add Image
 * Markup specific functionality like scaling and <code>BoundingBox</code>es
 * as possible parameter values.
 * 
 * @author sautter
 */
public class ImDocumentStyle extends DocumentStyle {
	
	/**
	 * Obtain the style parameter list for a given document, represented as a
	 * generic <code>Attributed</code> object. This method first checks if the 
	 * argument document already has a <code>DocumentStyle</code> attached to
	 * if in its 'docStyle' attribute, and if so, returns it. Otherwise, this 
	 * method delegates to the registered providers. If none are registered, or
	 * none has a style parameter list for the argument document, this method
	 * returns an empty <code>DocumentStyle</code> object, but never
	 * <code>null</code>. In any case, this method attempts to store the
	 * returned <code>DocumentStyle</code> in the 'docStyle' attribute for
	 * easier access later on.
	 * @param doc the document to obtain the style for
	 * @return the style parameter list for the argument document
	 */
	public static ImDocumentStyle getStyleFor(Attributed doc) {
		if (doc == null)
			return null;
		Object idso = doc.getAttribute(DOCUMENT_STYLE_ATTRIBUTE);
		if (idso instanceof ImDocumentStyle)
			return ((ImDocumentStyle) idso);
		DocumentStyle ds = DocumentStyle.getStyleFor(doc);
		if (ds == null)
			return null;
		ImDocumentStyle ids = wrapDocumentStyle(ds);
		if (ids != ds) try {
			doc.setAttribute(DOCUMENT_STYLE_ATTRIBUTE, ids);
		} catch (Throwable t) { /* catch any exception thrown from immutable documents, etc. */ }
		return ids;
	}
	
	static ImDocumentStyle wrapDocumentStyle(DocumentStyle docStyle) {
		if (docStyle instanceof ImDocumentStyle)
			return ((ImDocumentStyle) docStyle);
		if (docStyle.getData() == null)
			return new ImDocumentStyle(docStyle);
		return new ImDocumentStyle(docStyle.getData());
	}
	
	/**
	 * Anchors checking for the presence of specifically styled words in the
	 * first page(s) of an Image Markup document. This class is intended to be
	 * used in <code>Provider</code> implementations to help match style
	 * parameter lists to documents.
	 * 
	 * @author sautter
	 */
	public static class PageFeatureAnchor extends Anchor {
		
		/** the name of the property holding area the target page feature lies in, namely 'area' */
		public static final String TARGET_AREA_PROPERTY = "area";
		
		/** the name of the property holding minimum font size used in the target page feature, namely 'minFontSize' */
		public static final String TARGET_MINIMUM_FONT_SIZE_PROPERTY = "minFontSize";
		
		/** the name of the property holding maximum font size used in the target page feature, namely 'maxFontSize' */
		public static final String TARGET_MAXIMUM_FONT_SIZE_PROPERTY = "maxFontSize";
		
		/** the name of the property holding font size used in the target page feature, namely 'fontSize' (sets both minimum and maximum font size) */
		public static final String TARGET_FONT_SIZE_PROPERTY = "fontSize";
		
		/** the name of the property indicating whether or not the target page feature is in bold face, namely 'isBold' */
		public static final String TARGET_IS_BOLD_PROPERTY = "isBold";
		
		/** the name of the property indicating whether or not the target page feature is in italics, namely 'isItalics' */
		public static final String TARGET_IS_ITALICS_PROPERTY = "isItalics";
		
		/** the name of the property indicating whether or not the target page feature is in all-caps, namely 'isAllCaps' */
		public static final String TARGET_IS_ALL_CAPS_PROPERTY = "isAllCaps";
		
		/** the name of the property holding the pattern to test the extracted page feature against, namely 'pattern' */
		public static final String TARGET_PATTERN_PROPERTY = "pattern";
		
		/** the name of the property holding the number of pages to check after the first one, namely 'maxPageId' */
		public static final String MAXIMUM_PAGES_AFTER_FIRST_PROPERTY = "maxPageId";
		
		private BoundingBox area;
		private int minFontSize;
		private int maxFontSize;
		private boolean isBold;
		private boolean isItalics;
		private boolean isAllCaps;
		private Pattern pattern;
		private int maxPageId;
		
		/**
		 * @param name the name of the anchor
		 * @param area the area the target page feature lies in
		 * @param minFontSize the minimum font size of the target page feature
		 * @param maxFontSize the maximum font size of the target page feature
		 * @param isBold are the words of the target page feature in bold?
		 * @param isItalics are the words of the target page feature in italics?
		 * @param isAllCaps are the words of the target page feature in all-caps?
		 * @param pattern a pattern matching the target page feature
		 * @param maxPageId the maximum number of pages to test after the first
		 */
		public PageFeatureAnchor(String name, BoundingBox area, int minFontSize, int maxFontSize, boolean isBold, boolean isItalics, boolean isAllCaps, String pattern, int maxPageId) {
			this(name, area, minFontSize, maxFontSize, isBold, isItalics, isAllCaps, Pattern.compile(pattern), maxPageId);
		}
		
		/**
		 * @param name the name of the anchor
		 * @param isRequired is the anchor essential for a document style?
		 * @param area the area the target page feature lies in
		 * @param minFontSize the minimum font size of the target page feature
		 * @param maxFontSize the maximum font size of the target page feature
		 * @param isBold are the words of the target page feature in bold?
		 * @param isItalics are the words of the target page feature in italics?
		 * @param isAllCaps are the words of the target page feature in all-caps?
		 * @param pattern a pattern matching the target page feature
		 * @param maxPageId the maximum number of pages to test after the first
		 */
		public PageFeatureAnchor(String name, boolean isRequired, BoundingBox area, int minFontSize, int maxFontSize, boolean isBold, boolean isItalics, boolean isAllCaps, String pattern, int maxPageId) {
			this(name, isRequired, area, minFontSize, maxFontSize, isBold, isItalics, isAllCaps, Pattern.compile(pattern), maxPageId);
		}
		
		/**
		 * @param name the name of the anchor
		 * @param area the area the target page feature lies in
		 * @param minFontSize the minimum font size of the target page feature
		 * @param maxFontSize the maximum font size of the target page feature
		 * @param isBold are the words of the target page feature in bold?
		 * @param isItalics are the words of the target page feature in italics?
		 * @param isAllCaps are the words of the target page feature in all-caps?
		 * @param pattern a pattern matching the target page feature
		 * @param maxPageId the maximum number of pages to test after the first
		 */
		public PageFeatureAnchor(String name, BoundingBox area, int minFontSize, int maxFontSize, boolean isBold, boolean isItalics, boolean isAllCaps, Pattern pattern, int maxPageId) {
			this(name, false, area, minFontSize, maxFontSize, isBold, isItalics, isAllCaps, pattern, maxPageId);
		}
		
		/**
		 * @param name the name of the anchor
		 * @param isRequired is the anchor essential for a document style?
		 * @param area the area the target page feature lies in
		 * @param minFontSize the minimum font size of the target page feature
		 * @param maxFontSize the maximum font size of the target page feature
		 * @param isBold are the words of the target page feature in bold?
		 * @param isItalics are the words of the target page feature in italics?
		 * @param isAllCaps are the words of the target page feature in all-caps?
		 * @param pattern a pattern matching the target page feature
		 * @param maxPageId the maximum number of pages to test after the first
		 */
		public PageFeatureAnchor(String name, boolean isRequired, BoundingBox area, int minFontSize, int maxFontSize, boolean isBold, boolean isItalics, boolean isAllCaps, Pattern pattern, int maxPageId) {
			super(name, isRequired);
			this.area = area;
			this.minFontSize = minFontSize;
			this.maxFontSize = maxFontSize;
			this.isBold = isBold;
			this.isItalics = isItalics;
			this.isAllCaps = isAllCaps;
			this.pattern = pattern;
			this.maxPageId = maxPageId;
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.gamta.util.DocumentStyle.Anchor#matches(de.uka.ipd.idaho.gamta.Attributed)
		 */
		public boolean matches(Attributed doc) {
			if (doc instanceof ImDocument) {
				ImDocument imDoc = ((ImDocument) doc);
				for (int p = 0; p <= Math.min(this.maxPageId, (imDoc.getPageCount() - 1)); p++) {
					ImPage page = imDoc.getPage(imDoc.getFirstPageId() + p);
					if (matches(page, this.area, this.minFontSize, this.maxFontSize, this.isBold, this.isItalics, this.isAllCaps, this.pattern, null))
						return true;
				}
			}
			return false;
		}
		
		/**
		 * Match the attributes of a page feature anchor to a given page. If
		 * the <code>matchLog</code> argument is not null, this method fills it
		 * with details about the matching process; this is intended to provide
		 * a testing facility for client code maintaining page feature anchors.
		 * @param page the page to test
		 * @param area the area the target page feature lies in
		 * @param minFontSize the minimum font size of the target page feature
		 * @param maxFontSize the maximum font size of the target page feature
		 * @param isBold are the words of the target page feature in bold?
		 * @param isItalics are the words of the target page feature in italics?
		 * @param isAllCaps are the words of the target page feature in all-caps?
		 * @param pattern a pattern matching the target page feature
		 * @param matchLog a list to collect detail messages about the matching process
		 * @return true if the argument page contains the feature described by
		 *        the other arguments
		 */
		public static boolean matches(ImPage page, BoundingBox area, int minFontSize, int maxFontSize, boolean isBold, boolean isItalics, boolean isAllCaps, String pattern, List matchLog) {
			return matches(page, area, minFontSize, maxFontSize, isBold, isItalics, isAllCaps, Pattern.compile(pattern), matchLog);
		}
		
		/**
		 * Match the attributes of a page feature anchor to a given page. If
		 * the <code>matchLog</code> argument is not null, this method fills it
		 * with details about the matching process; this is intended to provide
		 * a testing facility for client code maintaining page feature anchors.
		 * @param page the page to test
		 * @param area the area the target page feature lies in
		 * @param minFontSize the minimum font size of the target page feature
		 * @param maxFontSize the maximum font size of the target page feature
		 * @param isBold are the words of the target page feature in bold?
		 * @param isItalics are the words of the target page feature in italics?
		 * @param isAllCaps are the words of the target page feature in all-caps?
		 * @param pattern a pattern matching the target page feature
		 * @param matchLog a list to collect detail messages about the matching process
		 * @return true if the argument page contains the feature described by
		 *        the other arguments
		 */
		public static boolean matches(ImPage page, BoundingBox area, int minFontSize, int maxFontSize, boolean isBold, boolean isItalics, boolean isAllCaps, Pattern pattern, List matchLog) {
			
			//	get page and scale bounding box
			area = scaleBox(area, 72, page.getImageDPI());
			if (matchLog != null)
				matchLog.add(" - area scaled to " + page.getImageDPI() + " DPI: " + area.toString());
			
			//	get words in area
			ImWord[] words = page.getWordsInside(area);
			if (words.length == 0) {
				if (matchLog != null)
					matchLog.add(" ==> no words found in area, mismatch");
				return false;
			}
			if (matchLog != null)
				matchLog.add(" - found " + words.length + " words in area");
			
			//	filter words by font properties
			ArrayList wordList = new ArrayList();
			if (matchLog != null) {
				matchLog.add(" - applying font property filter:");
				if (isBold)
					matchLog.add("   - bold");
				if (isItalics)
					matchLog.add("   - italics");
				if (isAllCaps)
					matchLog.add("   - all-caps");
				matchLog.add("   - font size " + minFontSize + ((maxFontSize == minFontSize) ? "" : ("-" + maxFontSize)));
			}
			for (int w = 0; w < words.length; w++) {
				if (isBold && !words[w].hasAttribute(ImWord.BOLD_ATTRIBUTE))
					continue;
				if (isItalics && !words[w].hasAttribute(ImWord.ITALICS_ATTRIBUTE))
					continue;
				if (isAllCaps && !words[w].getString().equals(words[w].getString().toUpperCase()))
					continue;
				try {
					int wfs = words[w].getFontSize();
					if (wfs < minFontSize)
						continue;
					if (maxFontSize < wfs)
						continue;
				} catch (NumberFormatException nfe) {}
				wordList.add(words[w]);
			}
			if (wordList.isEmpty()) {
				if (matchLog != null)
					matchLog.add(" ==> no words left after font property filter, mismatch");
				return false;
			}
			else if (wordList.size() < words.length)
				words = ((ImWord[]) wordList.toArray(new ImWord[wordList.size()]));
			if (matchLog != null)
				matchLog.add(" - " + words.length + " words left after font property filter");
			
			//	sort words and create normalized string
			ImUtils.sortLeftRightTopDown(words);
			StringBuffer wordStr = new StringBuffer();
			for (int w = 0; w < words.length; w++)
				wordStr.append(normalizeString(words[w].getString()));
			if (matchLog != null)
				matchLog.add(" - word string is '" + wordStr.toString() + "'");
			
			//	test against pattern
			boolean match = pattern.matcher(wordStr).matches();
			if (matchLog != null) {
				matchLog.add(" - matching against pattern '" + pattern.toString() + "'");
				matchLog.add(" ==> " + (match ? "match" : "mismatch"));
			}
			
			//	finally ...
			return match;
		}
		
		private static String normalizeString(String string) {
			StringBuffer nString = new StringBuffer();
			for (int c = 0; c < string.length(); c++) {
				char ch = string.charAt(c);
				if ((ch < 33) || (ch == 160))
					nString.append(" "); // turn all control characters into spaces, along with non-breaking space
				else if (ch < 127)
					nString.append(ch); // no need to normalize basic ASCII characters
				else if ("-\u00AD\u2010\u2011\u2012\u2013\u2014\u2015\u2212".indexOf(ch) != -1)
					nString.append("-"); // normalize dashes right here
				else nString.append(StringUtils.getNormalForm(ch));
			}
			return nString.toString();
		}
		
		static void installFactory(/* need method call to trigger static initializers */) {
			Anchor.addFactory(new Factory() {
				public Anchor getAnchor(String name, DocumentStyle docStyle) {
					ImDocumentStyle imDocStyle = wrapDocumentStyle(docStyle);
					BoundingBox ta = imDocStyle.getBoxProperty(TARGET_AREA_PROPERTY, null);
					if (ta == null)
						return null;
					Pattern tp = imDocStyle.getPatternProperty(TARGET_PATTERN_PROPERTY, null);
					if (tp == null)
						return null;
					int minFs = imDocStyle.getIntProperty(TARGET_MINIMUM_FONT_SIZE_PROPERTY, imDocStyle.getIntProperty(TARGET_FONT_SIZE_PROPERTY, 0));
					int maxFs = imDocStyle.getIntProperty(TARGET_MAXIMUM_FONT_SIZE_PROPERTY, imDocStyle.getIntProperty(TARGET_FONT_SIZE_PROPERTY, 72));
					boolean eb = imDocStyle.getBooleanProperty(TARGET_IS_BOLD_PROPERTY, false);
					boolean ei = imDocStyle.getBooleanProperty(TARGET_IS_ITALICS_PROPERTY, false);
					boolean eac = imDocStyle.getBooleanProperty(TARGET_IS_ALL_CAPS_PROPERTY, false);
					int mpId = imDocStyle.getIntProperty(MAXIMUM_PAGES_AFTER_FIRST_PROPERTY, 0);
					return new PageFeatureAnchor(name, docStyle.getBooleanProperty(IS_REQUIRED_PROPERTY, false), ta, minFs, maxFs, eb, ei, eac, tp, mpId);
				}
			});
		}
		
		/** parameter group description for page feature anchors, for use in a UI */
		public static final ParameterGroupDescription PARAMETER_GROUP_DESCRIPTION;
		static {
			PARAMETER_GROUP_DESCRIPTION = new ParameterGroupDescription("pageFeatureAnchor");
			PARAMETER_GROUP_DESCRIPTION.setLabel("Page Feature Anchors");
			PARAMETER_GROUP_DESCRIPTION.setDescription("Page feature anchors extract words from a specific area in a page, filter them by font size and other font properties, and match if the words are present and match the given pattern.");
			
			ParameterDescription taPd = new ParameterDescription("pageFeatureAnchor." + TARGET_AREA_PROPERTY);
			taPd.setLabel("Target Area");
			taPd.setDescription("The area of a page to seek the target feature in");
			taPd.setRequired();
			PARAMETER_GROUP_DESCRIPTION.setParameterDescription(TARGET_AREA_PROPERTY, taPd);
			ParameterDescription tpPd = new ParameterDescription("pageFeatureAnchor." + TARGET_PATTERN_PROPERTY);
			tpPd.setLabel("Target Pattern");
			tpPd.setDescription("The pattern matching the words of the target page feature");
			tpPd.setRequired();
			PARAMETER_GROUP_DESCRIPTION.setParameterDescription(TARGET_PATTERN_PROPERTY, tpPd);
			
			PARAMETER_GROUP_DESCRIPTION.setParamLabel(TARGET_IS_BOLD_PROPERTY, "Is the Page Feature in Bold?");
			PARAMETER_GROUP_DESCRIPTION.setParamDescription(TARGET_IS_BOLD_PROPERTY, "Is the target page feature in bold?");
			PARAMETER_GROUP_DESCRIPTION.setParamLabel(TARGET_IS_ITALICS_PROPERTY, "Is the Page Feature in Italics?");
			PARAMETER_GROUP_DESCRIPTION.setParamDescription(TARGET_IS_ITALICS_PROPERTY, "Is the target page feature in italics?");
			PARAMETER_GROUP_DESCRIPTION.setParamLabel(TARGET_IS_ALL_CAPS_PROPERTY, "Is the Page Feature in All-Caps?");
			PARAMETER_GROUP_DESCRIPTION.setParamDescription(TARGET_IS_ALL_CAPS_PROPERTY, "Is the target page feature in all-caps?");
			
			PARAMETER_GROUP_DESCRIPTION.setParamLabel(TARGET_MINIMUM_FONT_SIZE_PROPERTY, "Minimum Font Size");
			PARAMETER_GROUP_DESCRIPTION.setParamDescription(TARGET_MINIMUM_FONT_SIZE_PROPERTY, "The minimum font size used in the target page feature (preferably use exact font size where applicable)");
			PARAMETER_GROUP_DESCRIPTION.setParamLabel(TARGET_MAXIMUM_FONT_SIZE_PROPERTY, "Maximum Font Size");
			PARAMETER_GROUP_DESCRIPTION.setParamDescription(TARGET_MAXIMUM_FONT_SIZE_PROPERTY, "The maximum font size used in the target page feature (preferably use exact font size where applicable)");
			PARAMETER_GROUP_DESCRIPTION.setParamLabel(TARGET_FONT_SIZE_PROPERTY, "Font Size");
			PARAMETER_GROUP_DESCRIPTION.setParamDescription(TARGET_FONT_SIZE_PROPERTY, "The exact font size used in the target page feature (preferable over using minimum and maximum where applicable)");
			
			PARAMETER_GROUP_DESCRIPTION.setParamLabel(IS_REQUIRED_PROPERTY, "Is the Anchor Required to Match for an Overall Match?");
			PARAMETER_GROUP_DESCRIPTION.setParamDescription(IS_REQUIRED_PROPERTY, "A mismatch on a required anchor fails the overall match of a document style regardless how many other anchors do match a given document.");
			
			mapParameterValueClass(TARGET_AREA_PROPERTY, BoundingBox.class);
			mapParameterValueClass(TARGET_MINIMUM_FONT_SIZE_PROPERTY, Integer.class);
			mapParameterValueClass(TARGET_MAXIMUM_FONT_SIZE_PROPERTY, Integer.class);
			mapParameterValueClass(TARGET_FONT_SIZE_PROPERTY, Integer.class);
			mapParameterValueClass(TARGET_IS_BOLD_PROPERTY, Boolean.class);
			mapParameterValueClass(TARGET_IS_ITALICS_PROPERTY, Boolean.class);
			mapParameterValueClass(TARGET_IS_ALL_CAPS_PROPERTY, Boolean.class);
			mapParameterValueClass(TARGET_PATTERN_PROPERTY, Pattern.class);
			mapParameterValueClass(MAXIMUM_PAGES_AFTER_FIRST_PROPERTY, Integer.class);
			mapParameterValueClass(IS_REQUIRED_PROPERTY, Boolean.class);
		}
	}
	
	/** the default resolution of 72 DPI, i.e., the default typographical Point */
	public static int DEFAULT_DPI = 72;
	
	private DocumentStyle docStyle;
	
	/** Constructor for root object
	 * @param data the document style data object to wrap
	 */
	public ImDocumentStyle(Data data) {
		super(data);
	}
	
	/** Constructor for wrapper object, using data of argument document style,
	 * or argument document style proper (only used for anchors)
	 * @param docStyle the document style to wrap
	 */
	ImDocumentStyle(DocumentStyle docStyle) {
		super(docStyle.getData());
		if (this.data == null)
			this.docStyle = docStyle;
	}
	
	/** Constructor for subsets
	 * @param parent the parent document style to query with the argument prefix
	 * @param prefix the prefix to query the parent with
	 */
	public ImDocumentStyle(DocumentStyle parent, String prefix) {
		super(parent, prefix);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.gamta.util.DocumentStyle#getSubset(java.lang.String)
	 */
	public DocumentStyle getSubset(String prefix) {
		return this.getImSubset(prefix);
	}
	
	/**
	 * Obtain a sublist of this document style parameter list, comprising all
	 * parameters whose name starts with the argument prefix plus a dot. Only
	 * the name part after the prefix needs to be specified to retrieve the
	 * parameters from the sublist. This is useful for eliminating the need to
	 * specify full parameter names in client code.
	 * @param prefix the prefix for the sublist
	 * @return a sublist with the argument prefix
	 */
	public ImDocumentStyle getImSubset(String prefix) {
		return (((prefix == null) || (prefix.trim().length() == 0)) ? this : new ImDocumentStyle(this, prefix.trim()));
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.gamta.util.DocumentStyle#getPropertyData(java.lang.String)
	 */
	public String getPropertyData(String key) {
		String data = super.getPropertyData(key);
		if (data != null)
			return data;
		//	emulate Image Markup specific fallback for maximum page ID used in page feature anchor
		if ((Anchor.ANCHOR_PREFIX + "." + PageFeatureAnchor.MAXIMUM_PAGES_AFTER_FIRST_PROPERTY).equals(key)) { /* prevent endless loop */ }
		else if (key.startsWith(Anchor.ANCHOR_PREFIX + ".") && key.endsWith("." + PageFeatureAnchor.MAXIMUM_PAGES_AFTER_FIRST_PROPERTY)) {
			data = this.getPropertyData(Anchor.ANCHOR_PREFIX + "." + PageFeatureAnchor.MAXIMUM_PAGES_AFTER_FIRST_PROPERTY);
			if (data == null)
				data = "0";
		}
		return data;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.gamta.util.DocumentStyle#getPropertyData(java.lang.String, java.lang.Class)
	 */
	public String getPropertyData(String key, Class valueClass) {
		return ((this.docStyle == null) ? super.getPropertyData(key, valueClass) : this.docStyle.getPropertyData(key, valueClass));
	}
	
	/**
	 * Retrieve an integer property. If the argument key is not mapped to any
	 * value, or if its mapped value fails to parse into an integer, this
	 * method returns the argument default. If the argument key does map to a
	 * valid integer value, the latter is scaled to the argument DPI number,
	 * assuming a base resolution of 72 DPI. The default value argument is
	 * never scaled.
	 * @param key the hashtable key
	 * @param defVal a default value
	 * @param dpi the DPI to scale to, assuming the value to be in 72 DPI
	 * @return the value in this property list with the specified key value.
	 * @see java.util.Properties#getProperty(java.lang.String, java.lang.String)
	 */
	public int getIntProperty(String key, int defVal, int dpi) {
		int val = this.getIntProperty(key, Integer.MIN_VALUE);
		if (val == Integer.MIN_VALUE)
			return defVal;
		if ((dpi < 1) || (dpi == DEFAULT_DPI))
			return val;
		return scaleInt(val, DEFAULT_DPI, dpi);
	}
	
	/**
	 * Retrieve a float (floating point) property. If the argument key is not
	 * mapped to any value, or if its mapped value fails to parse into a float,
	 * this method returns the argument default. If the argument key does map
	 * to a valid float value, the latter is scaled to the argument DPI number,
	 * assuming a base resolution of 72 DPI. The default value argument is
	 * never scaled.
	 * @param key the hashtable key
	 * @param defVal a default value
	 * @param dpi the DPI to scale to, assuming the value to be in 72 DPI
	 * @return the value in this property list with the specified key value.
	 * @see java.util.Properties#getProperty(java.lang.String, java.lang.String)
	 */
	public float getFloatProperty(String key, float defVal, int dpi) {
		float val = this.getFloatProperty(key, Float.NEGATIVE_INFINITY);
		if (val == Float.NEGATIVE_INFINITY)
			return defVal;
		if ((dpi < 1) || (dpi == DEFAULT_DPI))
			return val;
		return scaleFloat(val, DEFAULT_DPI, dpi);
	}
	
	/**
	 * Retrieve a double (floating point) property. If the argument key is not
	 * mapped to any value, or if its mapped value fails to parse into a double,
	 * this method returns the argument default. If the argument key does map
	 * to a valid double value, the latter is scaled to the argument DPI number,
	 * assuming a base resolution of 72 DPI. The default value argument is
	 * never scaled.
	 * @param key the hashtable key
	 * @param defVal a default value
	 * @param dpi the DPI to scale to, assuming the value to be in 72 DPI
	 * @return the value in this property list with the specified key value.
	 * @see java.util.Properties#getProperty(java.lang.String, java.lang.String)
	 */
	public double getDoubleProperty(String key, double defVal, int dpi) {
		double val = this.getDoubleProperty(key, Double.NEGATIVE_INFINITY);
		if (val == Double.NEGATIVE_INFINITY)
			return defVal;
		if ((dpi < 1) || (dpi == DEFAULT_DPI))
			return val;
		return scaleDouble(val, DEFAULT_DPI, dpi);
	}
	
	/**
	 * Retrieve a bounding box property. If the argument key is not mapped to
	 * any value, or if its mapped value fails to parse into a bounding box,
	 * this method returns the argument default.
	 * @param key the hashtable key
	 * @param defVal a default value
	 * @return the value in this property list with the specified key value.
	 * @see java.util.Properties#getProperty(java.lang.String, java.lang.String)
	 */
	public BoundingBox getBoxProperty(String key, BoundingBox defVal) {
		return this.getBoxProperty(key, defVal, DEFAULT_DPI);
	}
	
	/**
	 * Retrieve a bounding box property. If the argument key is not mapped to
	 * any value, or if its mapped value fails to parse into a bounding box,
	 * this method returns the argument default. If the argument key does map
	 * to a valid bounding box value, the latter is scaled to the argument DPI
	 * number, assuming a base resolution of 72 DPI. The default value argument
	 * is never scaled.
	 * @param key the hashtable key
	 * @param defVal a default value
	 * @param dpi the DPI to scale to, assuming the value to be in 72 DPI
	 * @return the value in this property list with the specified key value.
	 * @see java.util.Properties#getProperty(java.lang.String, java.lang.String)
	 */
	public BoundingBox getBoxProperty(String key, BoundingBox defVal, int dpi) {
		String valStr = this.getPropertyData(key, BoundingBox.class);
		if ((valStr != null) && (valStr.trim().length() != 0)) try {
			BoundingBox val = BoundingBox.parse(valStr);
			if ((dpi < 1) || (dpi == DEFAULT_DPI))
				return val;
			else return scaleBox(val, DEFAULT_DPI, dpi);
		} catch (IllegalArgumentException iae) {}
		return defVal;
	}
	
	/**
	 * Retrieve an integer list property. If the argument key is not mapped to
	 * any value, or if its mapped value fails to parse into an integer list,
	 * this method returns the argument default. If the argument key does map
	 * to a valid integer list value, the latter is scaled to the argument DPI
	 * number, assuming a base resolution of 72 DPI. The default value argument
	 * is never scaled.
	 * @param key the hashtable key
	 * @param defVal a default value
	 * @param dpi the DPI to scale to, assuming the value to be in 72 DPI
	 * @return the value in this property list with the specified key value.
	 * @see java.util.Properties#getProperty(java.lang.String, java.lang.String)
	 */
	public int[] getIntListProperty(String key, int[] defVal, int dpi) {
		int[] vals = this.getIntListProperty(key, null);
		if (vals == null)
			return defVal;
		if ((0 < dpi) && (dpi != DEFAULT_DPI)) {
			for (int v = 0; v < vals.length; v++)
				vals[v] = scaleInt(vals[v], DEFAULT_DPI, dpi);
		}
		return vals;
	}
	
	/**
	 * Retrieve a float (floating point) list property. If the argument key is
	 * not mapped to any value, or if its mapped value fails to parse into a
	 * float list, this method returns the argument default. If the argument
	 * key does map to a valid float list value, the latter is scaled to the
	 * argument DPI number, assuming a base resolution of 72 DPI. The default
	 * value argument is never scaled.
	 * @param key the hashtable key
	 * @param defVal a default value
	 * @param dpi the DPI to scale to, assuming the value to be in 72 DPI
	 * @return the value in this property list with the specified key value.
	 * @see java.util.Properties#getProperty(java.lang.String, java.lang.String)
	 */
	public float[] getFloatListProperty(String key, float[] defVal, int dpi) {
		float[] vals = this.getFloatListProperty(key, null);
		if (vals == null)
			return defVal;
		if ((0 < dpi) && (dpi != DEFAULT_DPI)) {
			for (int v = 0; v < vals.length; v++)
				vals[v] = scaleFloat(vals[v], DEFAULT_DPI, dpi);
		}
		return vals;
	}
	
	/**
	 * Retrieve a double (floating point) list property. If the argument key is
	 * not mapped to any value, or if its mapped value fails to parse into a
	 * double list, this method returns the argument default. If the argument
	 * key does map to a valid double list value, the latter is scaled to the
	 * argument DPI number, assuming a base resolution of 72 DPI. The default
	 * value argument is never scaled.
	 * @param key the hashtable key
	 * @param defVal a default value
	 * @param dpi the DPI to scale to, assuming the value to be in 72 DPI
	 * @return the value in this property list with the specified key value.
	 * @see java.util.Properties#getProperty(java.lang.String, java.lang.String)
	 */
	public double[] getDoubleListProperty(String key, double[] defVal, int dpi) {
		double[] vals = this.getDoubleListProperty(key, null);
		if (vals == null)
			return defVal;
		if ((0 < dpi) && (dpi != DEFAULT_DPI)) {
			for (int v = 0; v < vals.length; v++)
				vals[v] = scaleDouble(vals[v], DEFAULT_DPI, dpi);
		}
		return vals;
	}
	
	/**
	 * Retrieve a bounding box list property. If the argument key is not mapped
	 * to any value, or if its mapped value fails to parse into a bounding box
	 * list, this method returns the argument default.
	 * @param key the hashtable key
	 * @param defVal a default value
	 * @return the value in this property list with the specified key value.
	 * @see java.util.Properties#getProperty(java.lang.String, java.lang.String)
	 */
	public BoundingBox[] getBoxListProperty(String key, BoundingBox[] defVal) {
		return this.getBoxListProperty(key, defVal, DEFAULT_DPI);
	}
	
	/**
	 * Retrieve a bounding box list property. If the argument key is not mapped
	 * to any value, or if its mapped value fails to parse into a bounding box
	 * list, this method returns the argument default. If the argument key does
	 * map to a valid bounding box list value, the latter is scaled to the
	 * argument DPI number, assuming a base resolution of 72 DPI. The default
	 * value argument is never scaled.
	 * @param key the hashtable key
	 * @param defVal a default value
	 * @param dpi the DPI to scale to, assuming the value to be in 72 DPI
	 * @return the value in this property list with the specified key value.
	 * @see java.util.Properties#getProperty(java.lang.String, java.lang.String)
	 */
	public BoundingBox[] getBoxListProperty(String key, BoundingBox[] defVal, int dpi) {
		String valStr = this.getPropertyData(key, boxListClass);
		if ((valStr == null) || (valStr.trim().length() == 0))
			return defVal;
		String[] valStrs = valStr.split("[^0-9\\,\\[\\]]+");
		BoundingBox[] vals = new BoundingBox[valStrs.length];
		try {
			for (int v = 0; v < valStrs.length; v++) {
				vals[v] = BoundingBox.parse(valStrs[v]);
				if ((0 < dpi) && (dpi != DEFAULT_DPI))
					vals[v] = scaleBox(vals[v], DEFAULT_DPI, dpi);
			}
			return vals;
		} catch (IllegalArgumentException iae) {}
		return defVal;
	}
	
	/**
	 * Scale an integer value.
	 * @param val the integer to scale 
	 * @param cDpi the current DPI to scale from
	 * @param tDpi the target DPI to scale to
	 * @return the scaled integer value
	 */
	public static final int scaleInt(int val, int cDpi, int tDpi) {
		return (((val * tDpi) + (cDpi / 2)) / cDpi);
	}
	
	/**
	 * Scale an integer value.
	 * @param val the integer to scale 
	 * @param cDpi the current DPI to scale from
	 * @param tDpi the target DPI to scale to
	 * @param mode either of R(ound), F(loor), and C(eiling)
	 * @return the scaled integer value
	 */
	public static final int scaleInt(int val, int cDpi, int tDpi, char mode) {
		int moduloAmortizer;
		if (mode == 'F')
			moduloAmortizer = 0;
		else if (mode == 'C')
			moduloAmortizer = (cDpi - 1);
		else moduloAmortizer = (cDpi / 2);
		return (((val * tDpi) + moduloAmortizer) / cDpi);
	}
	
	/**
	 * Scale a float value.
	 * @param val the float to scale 
	 * @param cDpi the current DPI to scale from
	 * @param tDpi the target DPI to scale to
	 * @return the scaled float value
	 */
	public static final float scaleFloat(float val, int cDpi, int tDpi) {
		return ((val * tDpi) / cDpi);
	}
	
	/**
	 * Scale a double value.
	 * @param val the integer to scale 
	 * @param cDpi the current DPI to scale from
	 * @param tDpi the target DPI to scale to
	 * @return the scaled double value
	 */
	public static final double scaleDouble(double val, int cDpi, int tDpi) {
		return ((val * tDpi) / cDpi);
	}
	
	/**
	 * Scale an integer value.
	 * @param val the integer to scale 
	 * @param cDpi the current DPI to scale from
	 * @param tDpi the target DPI to scale to
	 * @return the scaled integer
	 */
	public static final BoundingBox scaleBox(BoundingBox val, int cDpi, int tDpi) {
		return new BoundingBox(
			scaleInt(val.left, cDpi, tDpi),
			scaleInt(val.right, cDpi, tDpi),
			scaleInt(val.top, cDpi, tDpi),
			scaleInt(val.bottom, cDpi, tDpi)
		);
	}
	
	/**
	 * Scale an integer value.
	 * @param val the integer to scale 
	 * @param cDpi the current DPI to scale from
	 * @param tDpi the target DPI to scale to
	 * @param mode either of R(ound), I(nward), and O(utward)
	 * @return the scaled integer
	 */
	public static final BoundingBox scaleBox(BoundingBox val, int cDpi, int tDpi, char mode) {
		if (mode == 'I')
			return new BoundingBox(
					scaleInt(val.left, cDpi, tDpi, 'C'),
					scaleInt(val.right, cDpi, tDpi, 'F'),
					scaleInt(val.top, cDpi, tDpi, 'C'),
					scaleInt(val.bottom, cDpi, tDpi, 'F')
				);
		else if (mode == 'O')
			return new BoundingBox(
					scaleInt(val.left, cDpi, tDpi, 'F'),
					scaleInt(val.right, cDpi, tDpi, 'C'),
					scaleInt(val.top, cDpi, tDpi, 'F'),
					scaleInt(val.bottom, cDpi, tDpi, 'C')
				);
		else return new BoundingBox(
			scaleInt(val.left, cDpi, tDpi),
			scaleInt(val.right, cDpi, tDpi),
			scaleInt(val.top, cDpi, tDpi),
			scaleInt(val.bottom, cDpi, tDpi)
		);
	}
	
	private static final Class boxListClass;
	static {
		try {
			boxListClass = Class.forName("[L" + BoundingBox.class.getName() + ";");
			mapListElementClass(boxListClass, BoundingBox.class);
		}
		catch (ClassNotFoundException cnfe) {
			throw new RuntimeException(cnfe);
		}
	}
	
	static {
		PageFeatureAnchor.installFactory();
	}
//	
//	/**
//	 * @param args
//	 */
//	public static void main(String[] args) throws Exception {
//		String[] strs = {"", ""};
//		System.out.println(strs.getClass().getName());
//		int[] ints = {1, 2};
//		System.out.println(ints.getClass().getName());
//		float[] floats = {0.1f, 0.2f};
//		System.out.println(floats.getClass().getName());
//		double[] doubles = {0.1, 0.2};
//		System.out.println(doubles.getClass().getName());
//		boolean[] booleans = {true, false};
//		System.out.println(booleans.getClass().getName());
//		BoundingBox[] boxes = {};
//		System.out.println(boxes.getClass().getName());
//		
//		Class.forName("[Ljava.lang.String;");
//		Class.forName("[I");
//		Class.forName("[F");
//		Class.forName("[D");
//		Class.forName("[Z");
//		Class.forName("[Lde.uka.ipd.idaho.gamta.util.imaging.BoundingBox;");
//	}
}